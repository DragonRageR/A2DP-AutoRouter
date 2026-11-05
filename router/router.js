'use strict';
/*
 * A2DP AutoRouter v8 (experimental). For Frida Gadget script interaction.
 * Only package com.google.android.apps.tachyon and com.spotify.music.
 * Changes AAudio OUTPUT builders, never input, PCM, AudioPolicy, Telecom or focus.
 * The device ID comes from a root-maintained, read-only small status file.
 */
(function () {
  const BASE = '/data/local/tmp/re.zyg.fri';
  const TAG = 'A2DPRouter';
  const MEET = 'com.google.android.apps.tachyon';
  const SPOTIFY = 'com.spotify.music';
  const builders = new Map();
  const streams = new Map();
  let proc = '';
  let count = 0;
  let installedAt = null;
  let logger = null;
  try {
    const liblog = Process.findModuleByName('liblog.so') || Module.load('liblog.so');
    const logAddr = liblog.findExportByName('__android_log_write');
    if (logAddr) logger = new NativeFunction(logAddr, 'int', ['int', 'pointer', 'pointer']);
  } catch (_) {}
  function say(event, extra) {
    const line = TAG + ' ' + JSON.stringify(Object.assign({seq: ++count, pid: Process.id, app: proc, event}, extra || {}));
    if (logger) {
      try { logger(4, Memory.allocUtf8String(TAG), Memory.allocUtf8String(line)); return; } catch (_) {}
    }
    console.log(line);
  }
  function key(p) { return p ? p.toString() : 'null'; }
  function int32(v) { try { return v.toInt32(); } catch (_) { return null; } }
  function loadJSON(filename) {
    try { return JSON.parse(File.readAllText(BASE + '/' + filename)); }
    catch (_) { return null; }
  }
  function livePolicy() {
    const rules = loadJSON('audio_router_rules.json');
    const state = loadJSON('audio_router_status.json');
    if (!rules || !rules.enabled || !rules.apps || !state) return null;
    const rule = rules.apps[proc];
    if (!rule || !rule.enabled) return null;
    // IDs change after device reconnection; don't use a stale or ambiguous ID.
    if (!Number.isSafeInteger(state.a2dp_id) || state.a2dp_id <= 0 || state.a2dp_count !== 1) return null;
    if (rules.require_hfp_off !== false && state.sco_present !== false) return null;
    if (!Number.isFinite(state.updated) || Math.abs(Date.now() / 1000 - state.updated) > 35) return null;
    return {rule, deviceId: state.a2dp_id};
  }
  function install(module) {
    if (installedAt === key(module.base)) return;
    installedAt = key(module.base);
    const exp = n => { try { return module.findExportByName(n); } catch (_) { return null; } };
    const fn = (n, rt, a) => { const p = exp(n); return p ? new NativeFunction(p, rt, a) : null; };
    const setUsage = fn('AAudioStreamBuilder_setUsage', 'void', ['pointer', 'int']);
    const setDevice = fn('AAudioStreamBuilder_setDeviceId', 'void', ['pointer', 'int']);
    const getDevice = fn('AAudioStream_getDeviceId', 'int', ['pointer']);
    const hooked = [];
    function dev(p) {
      try { return getDevice && p && !p.isNull() ? getDevice(p) : null; } catch (_) { return null; }
    }
    function hook(name, enter, leave) {
      const p = exp(name);
      if (!p) { say('missing_symbol', {symbol: name}); return; }
      try {
        Interceptor.attach(p, {
          onEnter(args) {
            try { if (enter) enter.call(this, args); } catch (e) { say('callback_error', {symbol:name, error:String(e)}); }
          },
          onLeave(ret) {
            try { if (leave) leave.call(this, ret); } catch (e) { say('callback_error', {symbol:name, error:String(e)}); }
          }
        });
        hooked.push(name);
      } catch (e) { say('hook_failed', {symbol:name, error:String(e)}); }
    }
    function put(builder, field, value) {
      const id = key(builder);
      const spec = builders.get(id) || {};
      spec[field] = value;
      builders.set(id, spec);
    }
    hook('AAudio_createStreamBuilder', function (args) { this.out = args[0]; }, function (ret) {
      if (int32(ret) !== 0 || !this.out || this.out.isNull()) return;
      try { const b = this.out.readPointer(); if (!b.isNull()) builders.set(key(b), {}); } catch (_) {}
    });
    for (const [symbol, field] of [
      ['AAudioStreamBuilder_setDirection','direction'],
      ['AAudioStreamBuilder_setUsage','usage'],
      ['AAudioStreamBuilder_setDeviceId','deviceId']
    ]) hook(symbol, function(args) { put(args[0], field, int32(args[1])); });
    hook('AAudioStreamBuilder_openStream', function(args) {
      this.builder = args[0];
      this.out = args[1];
      const original = Object.assign({}, builders.get(key(args[0])) || {});
      this.spec = original;
      this.pinned = false;
      const policy = livePolicy();
      const dir = original.direction === undefined ? 0 : original.direction;
      const usage = original.usage === undefined ? 1 : original.usage;
      if (!policy || dir !== 0 || !setDevice) return;
      // Meet: only its VOICE_COMMUNICATION output. Spotify: only MEDIA output.
      if (proc === MEET && usage === 2 && setUsage) {
        try {
          setUsage(args[0], 1);
          setDevice(args[0], policy.deviceId);
          this.pinned = true;
          this.spec = Object.assign({}, original, {usage:1, deviceId:policy.deviceId});
        } catch (e) { say('pin_failed', {error:String(e)}); }
      } else if (proc === SPOTIFY && usage === 1) {
        try {
          setDevice(args[0], policy.deviceId);
          this.pinned = true;
          this.spec = Object.assign({}, original, {deviceId:policy.deviceId});
        } catch (e) { say('pin_failed', {error:String(e)}); }
      }
      if (this.pinned) say('pin_requested', {deviceId:policy.deviceId, origUsage:usage, newUsage: this.spec.usage ?? usage});
    }, function(ret) {
      const result = int32(ret);
      if (result !== 0 || !this.out || this.out.isNull()) {
        if (this.pinned) say('open_failed', {result});
        return;
      }
      let stream;
      try { stream = this.out.readPointer(); } catch (_) { return; }
      if (stream && !stream.isNull()) {
        streams.set(key(stream), {pinned: this.pinned, requestedId: this.spec.deviceId || 0});
        if (this.pinned) say('open', {result, actualDeviceId:dev(stream), requestedId:this.spec.deviceId});
      }
    });
    hook('AAudioStream_requestStart', function(args) { this.s = args[0]; }, function(ret) {
      const spec = streams.get(key(this.s));
      if (spec && spec.pinned) say('start', {result:int32(ret), actualDeviceId:dev(this.s), requestedId:spec.requestedId});
    });
    hook('AAudioStream_close', function(args) { streams.delete(key(args[0])); });
    say('hooks_ready', {symbols:hooked, note:'Rules apply to newly opened AAudio output streams only.'});
  }
  function start() {
    try {
      // /proc/self/cmdline is NUL-delimited binary, not a UTF-8 text file.
      const raw = new Uint8Array(File.readAllBytes('/proc/self/cmdline'));
      const zero = raw.indexOf(0);
      const end = zero < 0 ? raw.length : zero;
      if (end < 1 || end > 128) throw new Error('Unexpected cmdline length: ' + end);
      proc = '';
      for (let i = 0; i < end; i++) proc += String.fromCharCode(raw[i]);
    }
    catch (e) { say('cmdline_error', {error:String(e)}); return; }
    if (proc !== MEET && proc !== SPOTIFY) return;
    say('ready', {note:'Standalone Frida Gadget script: no laptop attachment required.'});
    try {
      Process.attachModuleObserver({
        onAdded(mod) {
          if (mod.name === 'libaaudio.so') {
            try { install(mod); } catch (e) { say('install_error', {error:String(e)}); }
          }
        }
      });
    } catch(e) { say('module_observer_error', {error:String(e)}); }
  }
  rpc.exports = {init(stage, parameters) { start(); }};
})();
