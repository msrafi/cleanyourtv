#!/usr/bin/env bash
# Build home-info from this checkout and install it on the TV, then reopen Home.
#
# The city and the holiday calendars are read from
# home-info/app/src/main/res/values/config.xml. Reminder lines are edited from a
# phone on the same Wi-Fi, at http://<tv ip>:8787, and that list survives this install.
# Edit the config file for the city or the calendars, then run:
#
#   TV_HOST=192.168.1.50 tools/push-home.sh
#
# Exit status: 0 the app is installed and Home came back. 1 the build or the install
# failed after the TV was reached. 2 the command was refused before anything was sent.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 tools/push-home.sh}"

PY=$ROOT/tools/venv/bin/python
[ -x "$PY" ] || PY=$ROOT/tools/venv/Scripts/python.exe
if [ ! -x "$PY" ]; then
  echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2
  exit 2
fi

if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17
  elif [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  fi
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JDK 17 was not found. Install it, or set JAVA_HOME, then run this again." >&2
  exit 2
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ -z "${ANDROID_HOME:-}" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" \
      /opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools; do
    if [ -d "$d" ]; then ANDROID_HOME=$d; break; fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "The Android SDK was not found. Install platform 34, or set ANDROID_HOME." >&2
  exit 2
fi
export ANDROID_HOME

cd "$ROOT/home-info"
if [ ! -f local.properties ]; then
  echo "sdk.dir=$(printf '%s' "$ANDROID_HOME" | tr '\\' '/')" > local.properties
fi
./gradlew --no-daemon assembleRelease -q --console=plain

APK=$ROOT/home-info/app/build/outputs/apk/release/app-release.apk
if [ ! -f "$APK" ]; then
  echo "the build finished without an APK." >&2
  exit 1
fi

cd "$ROOT"
"$PY" -I "$ROOT/tools/b64push.py" "$APK" /data/local/tmp/homeinfo.apk
"$PY" -I "$ROOT/tools/tv.py" 'pm install -r /data/local/tmp/homeinfo.apk; rm -f /data/local/tmp/homeinfo.apk'

# Replacing the app does not refresh the words on screen until Home asks again. Reopen Home,
# and put the Home-key helper back if this set was using it. force-stop clears that setting.
"$PY" -I "$ROOT/tools/tv.py" 's=$(settings get secure enabled_accessibility_services); a=$(settings get secure accessibility_enabled); am force-stop com.gathin.tvhome; case "$s" in *com.gathin.tvhome*) settings put secure enabled_accessibility_services ""; settings put secure enabled_accessibility_services "$s"; settings put secure accessibility_enabled "$a" ;; esac; am start -W -n com.gathin.tvhome/.MainActivity'
echo "Installed. Home is open."
