#!/usr/bin/env python3
"""Safe ADB installer and localhost GUI for a ZygiskFrida-backed Frida Gadget auto-router.
No remote network server, root audio-policy changes, or app data capture.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import uuid
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import urllib.parse

HERE = Path(__file__).resolve().parent
BASE = '/data/local/tmp/re.zyg.fri'
CONF = BASE + '/config.json'
GADGETCONF = BASE + '/libgadget.config.so'
SCRIPT = BASE + '/libgadget.script.so'
RULES = BASE + '/audio_router_rules.json'
STATUS = BASE + '/audio_router_status.json'
MARK = BASE + '/audio_router_enabled'
WATCHER = '/data/adb/service.d/98_a2dp_autorouter_v8.sh'
APP_NAMES = ('com.google.android.apps.tachyon', 'com.spotify.music')
BACKUP = HERE / 'original_configuration_backup'


def run(args, check=True, text=True):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=text)
    if check and result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {args!r}\n{result.stderr}")
    return result


def root(cmd, check=True):
    return run(['adb', 'shell', 'su', '-c', cmd], check=check)


def q(path): return shlex.quote(path)


def exists(path):
    return root('test -f ' + q(path), check=False).returncode == 0


def read(path):
    if not exists(path): return None
    r = run(['adb', 'exec-out', 'su', '-c', 'cat ' + q(path)], text=False)
    return bytes(r.stdout)


def upload(path, data, perms='0644'):
    with tempfile.NamedTemporaryFile(delete=False, prefix='a2dp_router_') as f:
        f.write(data)
        tmp = Path(f.name)
    remote_tmp = '/data/local/tmp/a2dp_router.stage.' + uuid.uuid4().hex
    try:
        run(['adb', 'push', str(tmp), remote_tmp])
        # Android Magisk may permit chmod on the shell-owned staging file but
        # deny chmod on the destination. Set permissions before the copy.
        run(['adb', 'shell', 'chmod', perms, remote_tmp])
        root('cp ' + q(remote_tmp) + ' ' + q(path))
        actual_mode = root('stat -c %a ' + q(path)).stdout.strip()
        expected_mode = format(int(perms, 8), 'o')
        if actual_mode != expected_mode:
            raise RuntimeError(f'Permissions for {path}: got {actual_mode}, expected {expected_mode}')
    finally:
        run(['adb', 'shell', 'rm', '-f', remote_tmp], check=False)
        tmp.unlink(missing_ok=True)


def backup(path, name):
    data = read(path)
    BACKUP.mkdir(mode=0o700, exist_ok=True)
    (BACKUP / (name + '.present')).write_text('1' if data is not None else '0')
    if data is not None:
        (BACKUP / name).write_bytes(data)
    return data


def json_on_phone(path):
    blob = read(path)
    return json.loads(blob) if blob else None


def ensure_ready(require_gadget=False):
    if run(['adb', 'get-state'], check=False).returncode:
        raise RuntimeError('ADB not connected/authorized.')
    if root('id -u', check=False).stdout.strip() != '0':
        raise RuntimeError('su access was not granted. Check Magisk Superuser for shell.')
    if require_gadget and not exists(BASE + '/libgadget.so'):
        raise RuntimeError('ZygiskFrida gadget not found at ' + BASE + '/libgadget.so. Install ZygiskFrida first and reboot. Do not confuse it with frida-server.')


def install():
    ensure_ready(require_gadget=True)
    if exists(MARK):
        print('Already installed. Use: python3 controller.py web')
        return
    if BACKUP.exists():
        raise RuntimeError('Previous backup folder exists. Resolve previous installation before reinstalling.')
    original_cfg = json_on_phone(CONF) or {}
    if original_cfg and not isinstance(original_cfg.get('targets', []), list):
        raise RuntimeError('Unrecognized ZygiskFrida config; refusing to overwrite.')
    targets = list(original_cfg.get('targets', []))
    collisions = [t.get('app_name') for t in targets if isinstance(t, dict) and t.get('app_name') in APP_NAMES]
    if collisions:
        raise RuntimeError('Existing ZygiskFrida targets already configured: ' + ', '.join(collisions) + '. Refusing to override them. Adjust manually or use a clean config.')
    if exists(GADGETCONF):
        raise RuntimeError('Existing libgadget.config.so (different Gadget interaction) found. Refusing to overwrite it. Make a manual backup and review compatibility first.')
    if any(exists(path) for path in (SCRIPT, WATCHER, RULES, STATUS, MARK)):
        raise RuntimeError('AutoRouter file name collision or partial installation. Refusing to overwrite.')
    backup(CONF, 'config.json')
    backup(GADGETCONF, 'libgadget.config.so')
    for app in APP_NAMES:
        targets.append({
            'app_name':app,'enabled':True,'start_up_delay_ms':0,
            'injected_libraries':[{'path':BASE+'/libgadget.so'}],
            'child_gating':{'enabled':False, 'mode':'freeze','injected_libraries':[]}
        })
    updated = dict(original_cfg)
    updated['targets'] = targets
    # Only files checked absent above are managed by this installation.
    # If setup fails, restore the exact original ZygiskFrida config and
    # remove only the files created during this attempt.
    original_cfg_bytes = read(CONF)
    created = (MARK, WATCHER, SCRIPT, GADGETCONF, RULES, STATUS)
    try:
        upload(SCRIPT, (HERE/'libgadget.script.so').read_bytes())
        upload(GADGETCONF, (HERE/'libgadget.config.so').read_bytes())
        upload(RULES, (HERE/'audio_router_rules.json').read_bytes())
        root('mkdir -p /data/adb/service.d')
        upload(WATCHER, (HERE/'audio_router_watcher.sh').read_bytes(), perms='0755')
        upload(CONF, (json.dumps(updated, indent=2, ensure_ascii=False)+'\n').encode())
        # The marker is created with the same checked upload path; no chmod
        # directly on this directory (fails on some rooted Android builds).
        upload(MARK, b'', perms='0644')
        if not exists(MARK):
            raise RuntimeError('Install marker missing after upload.')
    except Exception:
        root('rm -f ' + ' '.join(q(x) for x in created), check=False)
        if original_cfg_bytes is None:
            root('rm -f ' + q(CONF), check=False)
        else:
            upload(CONF, original_cfg_bytes)
        print('INSTALL FAILED. Partial files removed; backup on laptop retained.')
        raise
    root('nohup /system/bin/sh ' + q(WATCHER) + ' </dev/null >/dev/null 2>&1 &', check=False)
    print('\nINSTALLED. Restart both apps so ZygiskFrida can inject the Gadget.')
    print('Web UI: python3 controller.py web')
    print('Log: adb logcat -s A2DPRouter')
    print('Do not run legacy Frida v5/v7 sessions simultaneously.')
    print('Audio rules apply only to NEW output streams.')

def set_enabled(enabled, app=None):
    ensure_ready()
    config = json_on_phone(RULES)
    if not config: raise RuntimeError('Router rules not installed.')
    if app is None:
        config['enabled'] = bool(enabled)
    else:
        if app not in APP_NAMES: raise RuntimeError('Unknown app')
        config['apps'][app]['enabled'] = bool(enabled)
    upload(RULES, (json.dumps(config, indent=2)+'\n').encode())
    print(('ENABLED' if enabled else 'DISABLED') + (' all router rules' if app is None else ' ' + app))
    print('Only NEW output streams get new routing. Rejoin Meet / restart audio playback.')


def uninstall():
    ensure_ready()
    if not exists(MARK):
        print('Router marker absent; no changes made.')
        return
    # Fail closed first; remove all rules before touching ZygiskFrida configuration.
    set_enabled(False)
    cfg = json_on_phone(CONF) or {}
    if not isinstance(cfg.get('targets', []), list):
        raise RuntimeError('ZygiskFrida config malformed; router disabled but files not removed.')
    targets = [t for t in cfg.get('targets', []) if not (isinstance(t, dict) and t.get('app_name') in APP_NAMES)]
    cfg['targets'] = targets
    upload(CONF, (json.dumps(cfg, indent=2, ensure_ascii=False)+'\n').encode())
    root('rm -f ' + ' '.join(q(x) for x in [MARK, WATCHER, SCRIPT, GADGETCONF, RULES, STATUS]))
    print('AutoRouter removed from ZygiskFrida targets, service.d and gadget script.')
    print('You must force-stop and reopen both apps (or reboot) to unload existing injected code.')
    print('Laptop backups preserved under:', BACKUP)
    if BACKUP.exists():
        renamed = HERE / ('original_configuration_backup_uninstalled_' + time.strftime('%Y%m%d_%H%M%S'))
        BACKUP.rename(renamed)
        print('Backup archived at:', renamed)


def get_status():
    cfg = json_on_phone(RULES) or {}
    stat = json_on_phone(STATUS) or {}
    return {'installed':exists(MARK), 'rules':cfg, 'device':stat}


HTML = '''<!DOCTYPE html><html lang="uk"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>A2DP AutoRouter v8</title>
<style>body{background:#101820;color:#ecf2f7;font-family:system-ui,sans-serif;max-width:720px;margin:40px auto;padding:0 18px}h1{font-size:1.8em}.panel{background:#1b2835;border:1px solid #374d60;border-radius:15px;padding:18px;margin:15px 0}label{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 0;font-size:1.05em}input{height:23px;width:23px;accent-color:#64d2a2}p,small{color:#b7c7d4}button{border:1px solid #68859b;background:#31465a;color:#fff;padding:9px 14px;border-radius:9px;font-size:1em;cursor:pointer}.row{display:flex;justify-content:space-between;align-items:center}code{color:#aee1cf}#msg{min-height:1.4em}</style></head><body><h1>🎧 A2DP AutoRouter v8</h1><p>Експериментальне керування правилами Android через USB/ADB. Доступ лише з цього ноутбука (<code>127.0.0.1</code>).</p>
<div class="panel"><label>Увімкнути маршрутизатор <input id="master" type="checkbox"></label><hr><label>Google Meet → Sony A2DP <input id="meet" type="checkbox"></label><label>Spotify → Sony A2DP <input id="spotify" type="checkbox"></label><small>Для Meet: USAGE_VOICE_COMMUNICATION → MEDIA. Для Spotify: MEDIA без змін. Мікрофон не перенаправляється.</small></div>
<div class="panel"><div class="row"><strong>Підключений пристрій</strong><button onclick="refresh()">Оновити</button></div><p id="device">Зчитування...</p><p id="notice"></p></div><p id="msg"></p><small>Правила застосовуються при створенні НОВОГО AAudio-потоку. Якщо щось не так, вимкни головний перемикач і перезапусти програми. HFP/«Телефонні виклики» повинні бути вимкнені.</small>
<script>
async function req(path,method='GET',obj){let r=await fetch(path,{method,headers:{'Content-Type':'application/json'},body:obj?JSON.stringify(obj):undefined});let v=await r.json();if(!r.ok)throw Error(v.error||'Помилка');return v}
async function refresh(){try{let v=await req('/api/status');let r=v.rules||{},a=r.apps||{},d=v.device||{};master.checked=!!r.enabled;meet.checked=!!a['com.google.android.apps.tachyon']?.enabled;spotify.checked=!!a['com.spotify.music']?.enabled;device.textContent=d.a2dp_id?'A2DP port ID: '+d.a2dp_id:'A2DP не визначено';notice.textContent=d.sco_present?'⚠️ HFP/SCO активний: правила автоматично не застосуються.':'HFP/SCO неактивний. ID оновлюється автоматично.';msg.textContent=''}catch(e){msg.textContent=e.message}}
async function change(){try{let vals={enabled:master.checked,meet:meet.checked,spotify:spotify.checked};await req('/api/config','POST',vals);msg.textContent='Збережено. Новий маршрут діятиме після відкриття наступного аудіопотоку.'}catch(e){msg.textContent=e.message}}
for(let id of ['master','meet','spotify'])document.getElementById(id).addEventListener('change',change);refresh();</script></body></html>'''


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args): return
    def send_json(self, obj, code=200):
        data = json.dumps(obj, ensure_ascii=False).encode('utf8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def do_GET(self):
        try:
            if self.path == '/':
                data = HTML.encode('utf8')
                self.send_response(200); self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
            elif self.path == '/api/status': self.send_json(get_status())
            else: self.send_error(404)
        except Exception as e: self.send_json({'error':str(e)}, 500)
    def do_POST(self):
        if self.path != '/api/config': return self.send_error(404)
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if size <= 0 or size > 2048: return self.send_json({'error':'Invalid input size'},400)
            obj = json.loads(self.rfile.read(size))
            if set(obj) != {'enabled','meet','spotify'} or any(not isinstance(x,bool) for x in obj.values()):
                return self.send_json({'error':'Invalid settings'},400)
            config = json_on_phone(RULES)
            if not config: return self.send_json({'error':'Not installed'},409)
            config['enabled']=obj['enabled']
            config['apps'][APP_NAMES[0]]['enabled']=obj['meet']
            config['apps'][APP_NAMES[1]]['enabled']=obj['spotify']
            upload(RULES, (json.dumps(config,indent=2)+'\n').encode())
            self.send_json({'ok':True})
        except Exception as e: self.send_json({'error':str(e)},500)


def main():
    ap = argparse.ArgumentParser(description='A2DP Router v8: ZygiskFrida autorouter installer/GUI')
    ap.add_argument('action', choices=['install','web','status','enable','disable','uninstall'])
    args = ap.parse_args()
    if args.action == 'install': install()
    elif args.action == 'enable': set_enabled(True)
    elif args.action == 'disable': set_enabled(False)
    elif args.action == 'uninstall': uninstall()
    elif args.action == 'status': print(json.dumps(get_status(), ensure_ascii=False, indent=2))
    elif args.action == 'web':
        server = ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
        print('Open browser: http://127.0.0.1:8765 (Ctrl+C to stop GUI; router keeps running on phone)')
        server.serve_forever()

if __name__ == '__main__':
    try: main()
    except (RuntimeError, ValueError, OSError) as e:
        raise SystemExit('ERROR: '+str(e))
