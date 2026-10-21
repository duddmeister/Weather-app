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

wait_for_forecast() {
  echo "Waiting for the first forecast…"
  for _ in $(seq 1 60); do
    if adb_ logcat -d | grep -q "Nimbus/WeatherRepository: Updated"; then
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
  wait_for_forecast

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

adb_ wait-for-device
adb_ shell settings put secure location_mode 3 > /dev/null 2>&1 || true
adb_ shell cmd location set-location-enabled true > /dev/null 2>&1 || true
for _ in 1 2 3; do adb_ emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 1; done

echo "::group::Release build"
adb_ install -r -g "$APK_DIR/release/app-release.apk"
walk "$PKG"
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
adb_ logcat -d > "$OUT/logcat-debug.txt"
echo "::endgroup::"

cat "$OUT"/logcat-*.txt > "$OUT/logcat.txt"
grep -E "Nimbus/|AndroidRuntime|FATAL EXCEPTION|ANR in" "$OUT/logcat.txt" > "$OUT/app-log.txt" || true
echo "----- App log -----"
tail -n 80 "$OUT/app-log.txt"

status=0
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
if ! grep -q "Nimbus/WeatherRepository: Updated" "$OUT/logcat-release.txt"; then
  echo "::error::The release build never loaded a forecast"
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

exit $status
