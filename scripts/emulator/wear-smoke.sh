#!/usr/bin/env bash
# Install and launch RBSB-Weather Watch on a running Wear OS emulator, then record what happened:
# the sky screen (sample label, tap and rotary switch samples), a sky screenshot for each of a set
# of samples, the About page (credit), crash check.
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

found() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; grep -q "$1" "$OUT/ui.xml"; }
launch() { adb shell am start -S -W -n "$PKG/$ACT" "$@" > "$OUT/start.txt" 2>&1; sleep 7; }

# --- the sky screen (first page) ---
if found "SAMPLE DATA"; then note "SKY SCREEN: sample-data label visible"; else note "SKY SCREEN: sample-data label NOT found"; fi
if found "1/[0-9]* · "; then note "SKY SCREEN: first sample shown ($(grep -o '1/[0-9]* · [a-z-]*' "$OUT/ui.xml" | head -1))"; fi
# A tap moves to the next sample.
SIZE=$(adb shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}
adb shell input tap $((W / 2)) $((W / 2)); sleep 2
if found "2/[0-9]* · "; then note "SKY SCREEN: tap switched to the next sample"; else note "SKY SCREEN: tap did NOT switch sample"; fi

# --- a sky screenshot for each of these samples (launched directly by name) ---
for S in clear-day partly-cloudy-day overcast drizzle rain-night-warnings thunder-warning fog snow-northern clear-night polar-night; do
  launch --es sample "$S"
  sleep 3                                  # clouds and the Moon picture are made in the background
  adb exec-out screencap -p > "$OUT/sky-$S.png"
  if found "SAMPLE DATA"; then note "SKY $S: shown"; else note "SKY $S: label NOT found"; fi
done
cp "$OUT/sky-clear-day.png" "$OUT/about.png" 2>/dev/null || true   # kept for older log steps

# --- the About page (second page) ---
launch --ei page 1
adb exec-out screencap -p > "$OUT/about-page.png"
if found "Created By Shane BlackbuRn"; then
  note "ABOUT SCREEN: credit visible"
else
  adb shell input rotaryencoder scroll 2 > /dev/null 2>&1 || adb shell input swipe 200 380 200 120 300
  sleep 2
  if found "Created By Shane BlackbuRn"; then note "ABOUT SCREEN: credit visible after scrolling"; else note "ABOUT SCREEN: credit NOT found"; fi
fi
if found "RBSB-Weather Watch"; then note "ABOUT SCREEN: title visible"; else note "ABOUT SCREEN: title not on first screen"; fi
launch                                       # back to the sky for the rotary check

sampleline() { found "SAMPLE DATA"; grep -o '[0-9]*/[0-9]* · [a-z +-]*' "$OUT/ui.xml" | head -1; }
if [ "$ROTARY" = "yes" ]; then
  BEFORE=$(sampleline)
  if adb shell input rotaryencoder scroll 3 > "$OUT/rotary.txt" 2>&1; then
    sleep 2; adb exec-out screencap -p > "$OUT/after-rotary.png"
    AFTER=$(sampleline)
    note "ROTARY: scroll event sent (before: $BEFORE; after: $AFTER)"
    if [ -n "$AFTER" ] && [ "$BEFORE" != "$AFTER" ]; then note "ROTARY: sample switched"; else note "ROTARY: sample did NOT switch"; fi
  else
    note "ROTARY: the input tool refused the rotary event ($(head -c 200 "$OUT/rotary.txt"))"
  fi
fi

# --- the animation: frames are drawn while the sky is shown, and stop in the background ---
launch --es sample thunder-warning
frames() { adb shell dumpsys gfxinfo "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 "Total frames rendered" | grep -o '[0-9]*$'; }
adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1
sleep 10
F1=$(frames); sleep 5; F2=$(frames)
note "ANIMATION: frames rendered while shown: ${F1:-?} after 10 s, ${F2:-?} after 15 s"
if [ -n "$F1" ] && [ -n "$F2" ] && [ "$F2" -gt "$F1" ]; then note "ANIMATION: running while shown"; else note "ANIMATION: NOT running while shown"; fi
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1
grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" "$OUT/gfxinfo.txt" | head -8 | sed 's/^/  GFXINFO /' | tee -a "$R"
adb shell input keyevent KEYCODE_HOME; sleep 4
B1=$(frames); sleep 6; B2=$(frames)
note "ANIMATION: in the background: ${B1:-?} then ${B2:-?} frames"
if [ "${B1:-0}" = "${B2:-0}" ]; then note "ANIMATION: stopped in the background"; else note "ANIMATION: still drawing in the background"; fi
adb logcat -d -s RBSBSky:I 2>/dev/null | grep "SKY FRAME WINDOW" | sed 's/.*SKY FRAME WINDOW /  SKY FRAME WINDOW /' | tee -a "$R"

# --- short recordings for review: 3 samples, 6 s each ---
for S in thunder-warning rain-night-warnings snow-northern; do
  launch --es sample "$S"
  sleep 2
  adb shell screenrecord --time-limit 6 /sdcard/rec.mp4 > /dev/null 2>&1 && adb pull /sdcard/rec.mp4 "$OUT/rec-$S.mp4" > /dev/null 2>&1 \
    && note "RECORDING $S: $(stat -c %s "$OUT/rec-$S.mp4" 2>/dev/null) bytes" || note "RECORDING $S: screenrecord not available"
  adb shell rm -f /sdcard/rec.mp4
done
sleep 2
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  note "CRASH: YES"
fi
if [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then note "PROCESS: running"; else note "PROCESS: not running"; fi

grep -q "CRASH: YES\|credit NOT found\|PROCESS: not running\|app NOT in front\|label NOT found\|did NOT switch\|NOT running while shown\|still drawing in the background" "$R" && { note "RESULT: FAIL"; exit 1; }
note "RESULT: PASS"
