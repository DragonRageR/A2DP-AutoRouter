#!/bin/sh
# Bootstrap official Gradle 8.9 wrapper JAR from Gradle's GitHub tag v8.9.0.
# SHA-256 verified against https://gradle.org/release-checksums/.
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$BASE/gradle/wrapper/gradle-wrapper.jar"
EXPECTED='498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17'
if [ ! -f "$JAR" ]; then
  echo 'Downloading the official Gradle 8.9 wrapper JAR from GitHub...' >&2
  TEMP="$JAR.download.$$"
  trap 'rm -f "$TEMP"' EXIT HUP INT TERM
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error --output "$TEMP" \
      'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar'
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$TEMP" 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar'
  else
    echo 'Need curl or wget to retrieve the official Gradle wrapper.' >&2
    exit 1
  fi
  ACTUAL=$(sha256sum "$TEMP" | cut -d ' ' -f 1)
  if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo 'Gradle wrapper JAR checksum mismatch; refusing to execute.' >&2
    exit 1
  fi
  mv "$TEMP" "$JAR"
  trap - EXIT HUP INT TERM
fi
if [ "$(sha256sum "$JAR" | cut -d ' ' -f 1)" != "$EXPECTED" ]; then
  echo 'Gradle wrapper JAR checksum mismatch; refusing to execute.' >&2
  exit 1
fi
exec java -cp "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
