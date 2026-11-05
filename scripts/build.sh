#!/usr/bin/env bash
# Build APK without changing Android settings or accepting SDK licenses.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Prefer an installed JDK 17, then an explicit compatible JAVA_HOME.
if [[ -x "$HOME/.local/share/jdks/temurin-17/bin/java" ]]; then
  export JAVA_HOME="$HOME/.local/share/jdks/temurin-17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  echo 'ERROR: JDK 17 потрібен для Android Gradle Plugin 8.7.3.' >&2
  exit 1
fi
MAJOR=$(java -version 2>&1 | head -n1 | sed -nE 's/.*version "([0-9]+).*/\1/p')
if [[ "$MAJOR" != 17 && "$MAJOR" != 21 ]]; then
  echo "ERROR: Gradle 8.9 needs compatible Java 17 or 21; detected ${MAJOR:-unknown}." >&2
  echo 'Set JAVA_HOME to JDK 17 (e.g. ~/.local/share/jdks/temurin-17).' >&2
  exit 1
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [[ ! -f "$SDK/platforms/android-35/android.jar" ]]; then
  echo "ERROR: SDK Platform android-35 not found under $SDK" >&2
  echo 'Install it using Android Studio or sdkmanager; see README.' >&2
  exit 1
fi
if [[ ! -d "$SDK/build-tools/35.0.0" ]]; then
  echo "ERROR: Android Build-Tools 35.0.0 missing in $SDK" >&2
  exit 1
fi
printf 'sdk.dir=%s\n' "$SDK" > local.properties
export ANDROID_HOME="$SDK"
echo "Java: $(java -version 2>&1 | head -n1)"
echo "SDK: $SDK"
./gradlew --no-daemon assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
echo "APK: $APK"
ls -lh "$APK"
