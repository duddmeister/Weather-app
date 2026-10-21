#!/usr/bin/env bash
# Installs the CI-built APKs on a running emulator, exercises the main screens,
# captures screenshots and fails if the app crashed.
# Usage: smoke-test.sh <apk-dir> <output-dir>
set -uo pipefail

APK_DIR="$1"
OUT="$2"
PKG=io.github.duddmeister.weather
mkdir -p "$OUT"

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "Captured $1"
}

# Taps the first on-screen element whose content description or text matches $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  local bounds
  bounds=$(python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if sys.argv[2] in (node.get("content-desc"), node.get("text")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$bounds" ]; then
    echo "Couldn't find '$1' on screen"
    return 1
  fi
  adb shell input tap $bounds
}

adb wait-for-device
adb shell settings put secure location_mode 3 > /dev/null 2>&1 || true
adb shell cmd location set-location-enabled true > /dev/null 2>&1 || true
for _ in 1 2 3; do adb emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 1; done

adb install -r -g "$APK_DIR/release/app-release.apk"
adb logcat -c

adb shell am start -W -n "$PKG/.MainActivity"
for _ in 1 2 3; do adb emu geo fix -74.0060 40.7128 > /dev/null 2>&1; sleep 2; done

echo "Waiting for the first forecast…"
for _ in $(seq 1 60); do
  if adb logcat -d | grep -q "Nimbus/WeatherRepository: Updated"; then
    echo "Forecast loaded."
    break
  fi
  sleep 2
done
shot 01-home 4

for i in 2 3 4 5 6; do
  adb shell input swipe 540 1900 540 600 500
  shot "0$i-scroll"
done

tap "Settings" && shot 07-settings 2
adb shell input keyevent KEYCODE_BACK
sleep 1
tap "Places" && shot 08-places 2
adb shell input text "Paris"
shot 09-search 4
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
sleep 1

# Widget layouts rendered through Glance by the debug build's preview screen.
if [ -f "$APK_DIR/debug/app-debug.apk" ]; then
  adb install -r -g "$APK_DIR/debug/app-debug.apk"
  adb shell am start -W -n "$PKG.debug/$PKG.WidgetPreviewActivity"
  shot 10-widgets 6
  adb shell input swipe 540 1900 540 600 500
  shot 11-widgets-more 2
fi

adb logcat -d > "$OUT/logcat.txt"
echo "----- App log -----"
grep -E "Nimbus/" "$OUT/logcat.txt" | tail -n 60 || true

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
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "::warning::App process isn't running at the end of the test"
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
