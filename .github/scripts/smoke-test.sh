#!/usr/bin/env bash
# Installs the CI-built APKs on a running emulator, exercises the main screens,
# captures screenshots and fails if the app crashed.
# Usage: smoke-test.sh <apk-dir> <output-dir>
set -uo pipefail

APK_DIR="$1"
OUT="$2"
PKG=io.github.duddmeister.weather
mkdir -p "$OUT"

adb_() { timeout 60 adb "$@"; }

shot() {
  sleep "${2:-2}"
  if adb_ shell screencap -p /sdcard/shot.png && adb_ pull /sdcard/shot.png "$OUT/$1.png" > /dev/null; then
    echo "Captured $1"
  else
    echo "::warning::Screenshot $1 failed"
  fi
}

open_screen() {
  adb_ shell am start -n "$PKG/.MainActivity" -a "$PKG.action.$1" > /dev/null
}

swipe_up() {
  adb_ shell input swipe 540 1900 540 600 500
}

adb_ wait-for-device
adb_ shell settings put secure location_mode 3 > /dev/null 2>&1 || true
adb_ shell cmd location set-location-enabled true > /dev/null 2>&1 || true
for _ in 1 2 3; do adb_ emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 1; done

adb_ install -r -g "$APK_DIR/release/app-release.apk"
adb_ logcat -c

adb_ shell am start -W -n "$PKG/.MainActivity"
for _ in 1 2 3; do adb_ emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 2; done

echo "Waiting for the first forecast…"
for _ in $(seq 1 60); do
  if adb_ logcat -d | grep -q "Nimbus/WeatherRepository: Updated"; then
    echo "Forecast loaded."
    break
  fi
  sleep 2
done
shot 01-home 4

for i in 2 3 4 5 6; do
  swipe_up
  shot "0$i-scroll"
done

open_screen OPEN_SETTINGS
shot 07-settings 3
swipe_up
shot 08-settings-more 2

open_screen OPEN_PLACES
shot 09-places 3
adb_ shell input text "Paris"
shot 10-search 5
adb_ shell input keyevent KEYCODE_BACK
adb_ shell input keyevent KEYCODE_BACK
sleep 1

# Widget layouts rendered through Glance by the debug build's preview screen.
if [ -f "$APK_DIR/debug/app-debug.apk" ]; then
  adb_ install -r -g "$APK_DIR/debug/app-debug.apk"
  adb_ shell am start -W -n "$PKG.debug/$PKG.WidgetPreviewActivity"
  shot 11-widgets 6
  swipe_up
  shot 12-widgets-more 2
fi

adb_ logcat -d > "$OUT/logcat.txt"
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
