#!/usr/bin/env bash
# Install and launch the RBSB-Weather Watch shell on a running Wear OS emulator, then record
# what happened: screenshot, UI text, crash check, optional rotary scroll.
# Usage: wear-smoke.sh APK OUTDIR LABEL ROTARY(yes|no)
# Created By Shane BlackbuRn
set -u
APK="$1"; OUT="$2"; LABEL="$3"; ROTARY="${4:-no}"
PKG=io.github.shanebl030glitch.rbsbweather.dev
ACT=io.github.shanebl030glitch.rbsbweather.MainActivity
mkdir -p "$OUT"
R="$OUT/result.txt"
: > "$R"
note() { echo "$*" | tee -a "$R"; }

note "Emulator: $LABEL"
note "Android API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')  release: $(adb shell getprop ro.build.version.release | tr -d '\r')"
note "Screen: $(adb shell wm size | tr -d '\r')  density: $(adb shell wm density | tr -d '\r')"
note "ABI: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
note "Rotary input configured: $ROTARY"

adb logcat -c || true
if ! adb install -r -g "$APK" > "$OUT/install.txt" 2>&1; then
  cat "$OUT/install.txt"
  note "INSTALL: FAIL"; exit 1
fi
note "INSTALL: OK"

# Keep the screen awake and in front: a fresh Wear OS emulator can drop back to the watch face
adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 1800000 || true
front() { adb shell dumpsys activity activities 2>/dev/null | grep -E "topResumedActivity|mResumedActivity" | grep -q "$PKG/"; }
for attempt in 1 2 3; do
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  adb shell am start -W -n "$PKG/$ACT" > "$OUT/start.txt" 2>&1
  sleep 8
  if front; then note "LAUNCH: app in front (attempt $attempt)"; break; fi
  note "LAUNCH: app not in front after attempt $attempt; retrying"
  adb shell input keyevent KEYCODE_BACK || true
  sleep 3
done
front || note "LAUNCH: app NOT in front"
adb exec-out screencap -p > "$OUT/about.png"

found() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; grep -q "$1" "$OUT/ui.xml"; }

if found "Created By Shane BlackbuRn"; then
  note "ABOUT SCREEN: credit visible"
else
  # The list may need scrolling on a small screen: try the rotary encoder, then a swipe.
  adb shell input rotaryencoder scroll 2 > /dev/null 2>&1 || adb shell input swipe 200 380 200 120 300
  sleep 2
  if found "Created By Shane BlackbuRn"; then note "ABOUT SCREEN: credit visible after scrolling"; else note "ABOUT SCREEN: credit NOT found"; fi
fi
if found "RBSB-Weather Watch"; then note "ABOUT SCREEN: title visible"; else note "ABOUT SCREEN: title not on first screen"; fi

if [ "$ROTARY" = "yes" ]; then
  if adb shell input rotaryencoder scroll 3 > "$OUT/rotary.txt" 2>&1; then
    sleep 2; adb exec-out screencap -p > "$OUT/after-rotary.png"
    note "ROTARY: scroll event sent"
  else
    note "ROTARY: the input tool refused the rotary event ($(head -c 200 "$OUT/rotary.txt"))"
  fi
fi

sleep 2
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  note "CRASH: YES"
fi
if [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then note "PROCESS: running"; else note "PROCESS: not running"; fi

grep -q "CRASH: YES\|credit NOT found\|PROCESS: not running\|app NOT in front" "$R" && { note "RESULT: FAIL"; exit 1; }
note "RESULT: PASS"
