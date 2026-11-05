#!/system/bin/sh
# Magisk service.d helper: read-only dumpsys, writes tiny current-device snapshot.
# Only while our marker exists; never changes bluetooth routes or system audio policy.
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/data/adb/magisk:$PATH
BASE=/data/local/tmp/re.zyg.fri
MARKER="$BASE/audio_router_enabled"
PIDFILE="$BASE/audio_router_watcher.pid"
[ -e "$MARKER" ] || exit 0
# Avoid duplicate daemons when setup is retried.
if [ -r "$PIDFILE" ]; then
  previous="$(cat "$PIDFILE" 2>/dev/null)"
  case "$previous" in
    *[!0-9]*|'') ;;
    *) if [ "$previous" != "$$" ] && kill -0 "$previous" 2>/dev/null; then
         if grep -a -q '98_a2dp_autorouter_v8.sh' "/proc/$previous/cmdline" 2>/dev/null; then exit 0; fi
       fi ;;
  esac
fi
echo $$ > "$PIDFILE"
trap 'rm -f "$PIDFILE"' EXIT
while [ -e "$MARKER" ]; do
  POLICY="$(dumpsys media.audio_policy 2>/dev/null)"
  RESULT="$(printf '%s\n' "$POLICY" | awk '
    /Available output devices/ {inside=1; next}
    /Available input devices/ {inside=0}
    inside && /"BT A2DP Out"/ && /AUDIO_DEVICE_OUT_BLUETOOTH_A2DP/ {
      s=$0; sub(/^.*Port ID: /, "", s); sub(/;.*/, "", s);
      if (s ~ /^[0-9]+$/) {n++; id=s}
    }
    inside && /AUDIO_DEVICE_OUT_BLUETOOTH_SCO/ {sco=1}
    END {if (n!=1) id=0; printf("%d %d %d", id+0, n+0, sco+0)}
  ')"
  set -- $RESULT
  ID=${1:-0}
  COUNT=${2:-0}
  SCO=${3:-1}
  case "$ID:$COUNT:$SCO" in *[!0-9:]*|'') ID=0; COUNT=0; SCO=1 ;; esac
  if [ "$SCO" = 0 ]; then SCO_JSON=false; else SCO_JSON=true; fi
  NOW=$(date +%s)
  TMP="$BASE/.audio_router_status.$$"
  printf '{"a2dp_id":%s,"a2dp_count":%s,"sco_present":%s,"updated":%s}\n' \
    "$ID" "$COUNT" "$SCO_JSON" "$NOW" > "$TMP"
  chmod 644 "$TMP" 2>/dev/null
  mv -f "$TMP" "$BASE/audio_router_status.json"
  if pidof com.spotify.music >/dev/null 2>&1 || pidof com.google.android.apps.tachyon >/dev/null 2>&1; then
    sleep 4
  else
    sleep 15
  fi
done
