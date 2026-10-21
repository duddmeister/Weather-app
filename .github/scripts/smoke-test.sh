#!/usr/bin/env bash
# Installs the CI-built APKs on a running emulator, walks through the main screens and fails if the app
# crashed. The release build is checked as users get it; the debug build repeats the walk and saves
# screenshots through its SnapshotProvider (the emulator's own screencap stalls under software rendering).
# Usage: smoke-test.sh <apk-dir> <output-dir>
set -uo pipefail

APK_DIR="$1"
OUT="$2"
PKG=io.github.duddmeister.weather
DEBUG_PKG=$PKG.debug
mkdir -p "$OUT"

adb_() { timeout 60 adb "$@"; }

update_status=0
widget_status=0

# Collects logs, checks for crashes, writes thumbnails and exits.
finish() {
  local status=$1
  cat "$OUT"/logcat-*.txt > "$OUT/logcat.txt" 2>/dev/null
  grep -E "Nimbus/|AndroidRuntime|FATAL EXCEPTION|ANR in" "$OUT/logcat.txt" > "$OUT/app-log.txt" || true
  echo "----- App log -----"
  tail -n 80 "$OUT/app-log.txt"

  if grep -E "FATAL EXCEPTION" -A 40 "$OUT/logcat.txt" | grep -q -E "Process: $PKG"; then
    echo "::error::The app crashed during the smoke test"
    grep -E "FATAL EXCEPTION" -A 40 "$OUT/logcat.txt"
    status=1
  fi
  if grep -q -E "ANR in $PKG" "$OUT/logcat.txt"; then
    echo "::error::The app stopped responding (ANR)"
    grep -E "ANR in $PKG" -A 20 "$OUT/logcat.txt"
    status=1
  fi

  # Small JPEG copies for quick review.
  python3 - "$OUT" <<'PY' || true
import os, sys
from PIL import Image
out = sys.argv[1]
os.makedirs(os.path.join(out, "thumbs"), exist_ok=True)
for name in sorted(os.listdir(out)):
    if name.endswith(".png"):
        image = Image.open(os.path.join(out, name)).convert("RGB")
        image.thumbnail((432, 960))
        image.save(os.path.join(out, "thumbs", name[:-4] + ".jpg"), quality=70)
PY
  exit "$status"
}

# Stops early, keeping what was captured, if the emulator itself has died.
check_emulator() {
  if ! timeout 10 adb get-state > /dev/null 2>&1; then
    echo "::error::The emulator stopped responding ($1)"
    adb_ logcat -d > "$OUT/logcat-last.txt" 2>/dev/null
    finish 1
  fi
  echo "Emulator alive: $1"
}

# launch <package> [action]
launch() {
  if [ -n "${2:-}" ]; then
    adb_ shell am start -W -n "$1/$PKG.MainActivity" -a "$PKG.action.$2" > /dev/null
  else
    adb_ shell am start -W -n "$1/$PKG.MainActivity" > /dev/null
  fi
}

swipe_up() {
  adb_ shell input swipe 540 1900 540 600 500
}

# wait_for_forecast <package>: waits until that app logs its first successful fetch.
wait_for_forecast() {
  local pid
  pid=$(adb_ shell pidof -s "$1" | tr -d '\r')
  echo "Waiting for the first forecast…"
  for _ in $(seq 1 60); do
    if adb_ logcat -d ${pid:+--pid="$pid"} | grep -q "Nimbus/WeatherRepository: Updated"; then
      echo "Forecast loaded."
      return 0
    fi
    sleep 2
  done
  echo "::warning::No forecast was loaded within two minutes"
}

# snap <name> [settle seconds]: saves the debug app's visible screen to $OUT/<name>.png.
snap() {
  sleep "${2:-2}"
  local result
  result=$(adb_ shell am broadcast -p "$DEBUG_PKG" -a "$DEBUG_PKG.SNAPSHOT" --es name "$1" 2>&1)
  if echo "$result" | grep -q 'data="saved"' &&
    adb_ exec-out run-as "$DEBUG_PKG" cat "files/snapshots/$1.png" > "$OUT/$1.png" && [ -s "$OUT/$1.png" ]; then
    echo "Captured $1"
  else
    rm -f "$OUT/$1.png"
    echo "::warning::Snapshot $1 failed: $(echo "$result" | tail -n 1)"
  fi
}

# walk <package> [snapshots]: opens each screen in turn, optionally capturing it.
walk() {
  local package="$1" capture="${2:-}"
  adb_ logcat -c
  launch "$package"
  for _ in 1 2 3; do adb_ emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 2; done
  wait_for_forecast "$package"
  check_emulator "forecast loaded in $package"

  if [ -n "$capture" ]; then
    snap 01-home 4
    for i in 2 3 4 5 6; do
      swipe_up
      snap "0$i-scroll"
    done
  else
    sleep 4
    for _ in 1 2 3; do swipe_up; sleep 1; done
  fi

  check_emulator "home screen of $package"
  launch "$package" OPEN_SETTINGS
  if [ -n "$capture" ]; then snap 07-settings 3; swipe_up; snap 08-settings-more; else sleep 3; fi

  launch "$package" OPEN_PLACES
  if [ -n "$capture" ]; then snap 09-places 3; else sleep 3; fi
  adb_ shell input text "Paris"
  if [ -n "$capture" ]; then snap 10-search 5; else sleep 5; fi
  adb_ shell input keyevent KEYCODE_BACK
  adb_ shell input keyevent KEYCODE_BACK
  sleep 1
}

# CI builds are numbered below every release, so the installed release build should update itself to
# the latest GitHub release the same way a phone does: "Update now" → download → verify → install.
update_to_latest_release() {
  local repo="${GITHUB_REPOSITORY:-}" auth=() release tag apk_url apksigner have="" want
  [ -n "$repo" ] || { echo "No repository context; skipping."; return 0; }
  [ -n "${GH_TOKEN:-}" ] && auth=(-H "Authorization: Bearer $GH_TOKEN")
  if ! release=$(curl -fsS "${auth[@]}" "https://api.github.com/repos/$repo/releases/latest"); then
    echo "No published release yet; skipping."
    return 0
  fi
  tag=$(python3 -c 'import json, sys; print(json.load(sys.stdin)["tag_name"])' <<< "$release")
  apk_url=$(python3 -c 'import json, sys; print(next((a["browser_download_url"] for a in json.load(sys.stdin)["assets"] if a["name"].endswith(".apk")), ""))' <<< "$release")
  [ -n "$apk_url" ] || { echo "::error::Release $tag has no APK attached"; return 1; }

  # Android only installs an update signed with the same key, so skip if releases use a different one.
  apksigner=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n 1)
  curl -fsSL -o "$RUNNER_TEMP/latest.apk" "$apk_url"
  cert() { "$apksigner" verify --print-certs "$1" | grep -m 1 "SHA-256"; }
  if [ "$(cert "$RUNNER_TEMP/latest.apk")" != "$(cert "$APK_DIR/release/app-release.apk")" ]; then
    echo "::notice::$tag is signed with a different key than CI builds; skipping the update test."
    return 0
  fi

  if [ "$(adb_ shell getprop ro.build.version.sdk | tr -d '\r')" -lt 31 ]; then
    echo "::notice::Prompt-free self-updates need Android 12+; skipping the update test on this emulator."
    return 0
  fi

  want="${tag#v}"
  adb_ shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow
  adb_ logcat -c
  adb_ shell am start -n "$PKG/$PKG.MainActivity" -a "$PKG.action.INSTALL_UPDATE" > /dev/null
  for _ in $(seq 1 60); do
    have=$(adb_ shell dumpsys package "$PKG" | grep -m 1 -o "versionName=[^ ]*" | cut -d= -f2 | tr -d '\r')
    [ "$have" = "$want" ] && break
    sleep 3
  done
  sleep 5 # Let the updated app handle MY_PACKAGE_REPLACED before the logs are checked.
  adb_ logcat -d > "$OUT/logcat-update.txt"
  grep -E "Nimbus/UpdateManager|Nimbus/ApkInstaller" "$OUT/logcat-update.txt" || true
  if [ "$have" = "$want" ]; then
    echo "Updated in place from the CI build to $want."
  elif grep -q -E "HTTP (403|429) for https://api.github.com" "$OUT/logcat-update.txt" "$OUT/logcat-release.txt"; then
    echo "::warning::GitHub rate-limited the emulator's update check; the update test was inconclusive."
  else
    echo "::error::The in-app update to $tag didn't install (installed version: ${have:-unknown})"
    return 1
  fi
}

adb_ wait-for-device
if [ "${IDLE_SECONDS:-0}" -gt 0 ]; then
  echo "Idling ${IDLE_SECONDS}s before installing anything…"
  sleep "$IDLE_SECONDS"
  check_emulator "idle after boot"
fi
adb_ shell settings put secure location_mode 3 > /dev/null 2>&1 || true
adb_ shell cmd location set-location-enabled true > /dev/null 2>&1 || true
for _ in 1 2 3; do adb_ emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 1; done

echo "::group::Release build"
adb_ install -r -g "$APK_DIR/release/app-release.apk"
walk "$PKG"
check_emulator "release build walk"
adb_ logcat -d > "$OUT/logcat-release.txt"
echo "::endgroup::"

echo "::group::Debug build with screenshots"
adb_ install -r -g "$APK_DIR/debug/app-debug.apk"
walk "$DEBUG_PKG" capture
# Widget layouts rendered through Glance with sample data.
adb_ shell am start -W -n "$DEBUG_PKG/$PKG.WidgetPreviewActivity" > /dev/null
snap 11-widgets 6
swipe_up
snap 12-widgets-more
check_emulator "debug build walk"
adb_ logcat -d > "$OUT/logcat-debug.txt"
echo "::endgroup::"

# The real widgets, hosted like a launcher would, showing live data; then their refresh button is pressed.
echo "::group::Live widgets and the refresh button"
widget_status=0
adb_ shell appwidget grantbind --package "$DEBUG_PKG" --user 0
adb_ logcat -c
adb_ shell am start -W -n "$DEBUG_PKG/$PKG.WidgetHostActivity" > /dev/null
snap 13-live-widgets 8
adb_ logcat -c
adb_ shell am start -n "$DEBUG_PKG/$PKG.WidgetHostActivity" -a "$PKG.debug.PRESS_REFRESH" > /dev/null
snap 14-live-widgets-refreshing 1
snap 15-live-widgets-refreshed 10
check_emulator "live widgets"
adb_ logcat -d > "$OUT/logcat-widgets.txt"
grep -E "Nimbus/(WidgetHost|WeatherRepository|WeatherRefresher|WidgetUpdater)" "$OUT/logcat-widgets.txt" || true
if ! grep -q "Nimbus/WidgetHost: Pressed refresh" "$OUT/logcat-widgets.txt"; then
  echo "::error::Couldn't find the widgets' refresh button"
  widget_status=1
elif ! grep -q "Nimbus/WeatherRepository: Updated" "$OUT/logcat-widgets.txt"; then
  echo "::error::Pressing the widget refresh button didn't fetch new weather"
  widget_status=1
fi
echo "::endgroup::"

echo "::group::In-app update to the latest release"
update_to_latest_release
update_status=$?
echo "::endgroup::"

status=$((update_status || widget_status))
if ! grep -q "Nimbus/WeatherRepository: Updated" "$OUT/logcat-release.txt"; then
  echo "::error::The release build never loaded a forecast"
  status=1
fi
finish "$status"
