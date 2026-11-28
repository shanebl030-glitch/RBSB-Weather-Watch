#!/usr/bin/env bash
# Install and launch RBSB-Weather Watch on a running Wear OS emulator and record what happened
# (Stage 3): the sky with its sample label, every section of the first screen, every detail
# screen, the data-age and no-data states, bezel and touch scrolling, swipe back, the animation
# (running while the sky shows, stopped when scrolled away and in the background), recordings,
# and a crash check. Screens are opened with the debug build's launch options (DebugOptions).
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
note "Rotary input configured: $ROTARY"

adb logcat -c || true
if ! adb install -r -g "$APK" > "$OUT/install.txt" 2>&1; then
  cat "$OUT/install.txt"; note "INSTALL: FAIL"; exit 1
fi
note "INSTALL: OK"

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

dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; }
found() { dump; grep -q "$1" "$OUT/ui.xml"; }
launch() { adb shell am start -S -W -n "$PKG/$ACT" "$@" > "$OUT/start.txt" 2>&1; sleep 6; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
SIZE=$(adb shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}

# --- the sky (debug build, no phone data: a labelled sample) ---
if found "SAMPLE DATA"; then note "SKY: sample-data label visible"; else note "SKY: sample-data label NOT found"; fi
adb logcat -c || true
adb shell input tap $((W / 2)) $((W * 3 / 5)); sleep 2
shot "after-tap"
adb logcat -d -s RBSBInput:I | grep -o "SKY TAP.*\|SCREEN .*" | tail -3 | sed 's/^/  APP /' | tee -a "$R"
if found "SAMPLE DATA · 2/"; then note "SKY: tap switched to the next sample"; else note "SKY: tap did NOT switch sample"; fi

for S in clear-day partly-cloudy-day rain-night-warnings thunder-warning clear-night snow-northern fog; do
  launch --es sample "$S"; sleep 2; shot "sky-$S"
  if found "SAMPLE DATA"; then note "SKY $S: shown"; else note "SKY $S: label NOT found"; fi
done

# --- every section of the first screen ---
for SEC in details hourly daily wind warnings moon about; do
  S=clear-day; [ "$SEC" = warnings ] && S=rain-night-warnings; [ "$SEC" = wind ] && S=hourly-wind-mixed
  launch --es sample "$S" --es scrollTo "$SEC"; sleep 2; shot "home-$SEC"
  note "SECTION $SEC: shown ($S)"
done
launch --es sample clear-day --es scrollTo about
if found "Created By Shane BlackbuRn"; then note "ABOUT: credit visible"; else
  adb shell input swipe $((W / 2)) $((W * 3 / 4)) $((W / 2)) $((W / 4)) 300; sleep 2
  if found "Created By Shane BlackbuRn"; then note "ABOUT: credit visible after scrolling"; else note "ABOUT: credit NOT found"; fi
fi

# --- the detail screens ---
for RT in hours day/0 moon moonFull; do
  launch --es sample clear-day --es route "$RT"; sleep 3; shot "screen-${RT//\//-}"; note "SCREEN $RT: shown"
done
launch --es sample rain-night-warnings --es route warning/0; sleep 2; shot "screen-warning-0"; note "SCREEN warning/0: shown"

# --- data age and no-data states ---
for A in 80 300 1200 4400; do
  launch --es sample clear-day --el ageMinutes "$A"; sleep 2; shot "state-age-$A"; note "STATE age $A min: shown"
done
launch --es sample rain-night-warnings --el ageMinutes 240 --es scrollTo warnings; sleep 2; shot "state-warnings-stale"
for P in waiting noapp nophone; do
  launch --es phone "$P"; shot "state-$P"
  if found "Waiting for your phone\|Phone app update needed\|Phone not connected"; then note "STATE $P: shown"; else note "STATE $P: text NOT found"; fi
done

# --- back from a detail screen: a swipe right, and the watch's Back button ---
launch --es sample clear-day --es route day/0; sleep 2
adb logcat -c || true
adb shell input swipe $((W / 4)) $((W / 2)) $((W * 15 / 16)) $((W / 2)) 400; sleep 3
shot "after-swipe-back"
adb logcat -d -s RBSBInput:I | grep -o "SCREEN .*" | tail -3 | sed 's/^/  APP /' | tee -a "$R"
if found "SAMPLE DATA"; then note "SWIPE BACK: returned to the first screen"
elif front; then note "SWIPE BACK: did NOT return (app still open: $(grep -o 'text="[^"]\+"' "$OUT/ui.xml" | head -3 | tr '\n' ' '))"
else note "SWIPE BACK: did NOT return (the app closed instead)"; fi
launch --es sample clear-day --es route day/0; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 3
if found "SAMPLE DATA"; then note "BACK BUTTON: returned to the first screen"; else note "BACK BUTTON: did NOT return"; fi

# --- touch scrolling: a swipe up from the sky shows the next section ---
launch --es sample clear-day
adb shell input swipe $((W / 2)) $((W * 4 / 5)) $((W / 2)) $((W / 5)) 400; sleep 2
if found 'text="Now"\|Feels like\|Humidity'; then note "TOUCH SCROLL: moved to the Now section"; else note "TOUCH SCROLL: did NOT move"; fi

# --- bezel or crown scrolling ---
if [ "$ROTARY" = "yes" ]; then
  launch --es sample clear-day
  rot() { adb shell input rotaryencoder scroll --axis SCROLL,"$1" > "$OUT/rotary.txt" 2>&1 \
          && ! grep -qi "error\|usage\|exception" "$OUT/rotary.txt" \
          || adb shell input rotaryencoder scroll "$1" > "$OUT/rotary.txt" 2>&1; }
  adb logcat -c || true
  if rot 3; then
    sleep 1; rot 3; sleep 2
    note "ROTARY: input tool replied: $(tr -d '\r' < "$OUT/rotary.txt" | head -c 160 | tr '\n' ' ')"
    adb logcat -d -s RBSBInput:I | grep -o "ROTARY .*" | tail -6 | sed 's/^/  APP /' | tee -a "$R"
    shot "after-rotary"
    if found 'text="Now"\|Feels like\|Humidity\|Hourly'; then note "ROTARY: the list scrolled"; else note "ROTARY: the list did NOT scroll"; fi
  else
    note "ROTARY: the input tool refused the rotary event ($(head -c 200 "$OUT/rotary.txt"))"
  fi
fi

# --- the animation: drawing while the sky shows, stopped when scrolled away and in the background ---
frames() { adb shell dumpsys gfxinfo "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 "Total frames rendered" | grep -o '[0-9]*$'; }
launch --es sample thunder-warning
adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1
sleep 10
F1=$(frames); sleep 5; F2=$(frames)
note "ANIMATION: frames rendered while shown: ${F1:-?} after 10 s, ${F2:-?} after 15 s"
if [ -n "$F1" ] && [ -n "$F2" ] && [ "$F2" -gt "$F1" ]; then note "ANIMATION: running while shown"; else note "ANIMATION: NOT running while shown"; fi
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1
grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" "$OUT/gfxinfo.txt" | head -8 | sed 's/^/  GFXINFO /' | tee -a "$R"
# About has nothing animated (the forecast rows' icons do animate, like the phone's), so any
# frames there would be the sky drawing while off screen. One or two may come from the clock.
launch --es sample thunder-warning --es scrollTo about; sleep 4
S1=$(frames); sleep 6; S2=$(frames)
note "ANIMATION: scrolled away from the sky (About): ${S1:-?} then ${S2:-?} frames"
if [ $(( ${S2:-0} - ${S1:-0} )) -le 2 ]; then note "ANIMATION: stopped when scrolled away"; else note "ANIMATION: still drawing when scrolled away"; fi
launch --es sample thunder-warning --es scrollTo daily; sleep 4
D1=$(frames); sleep 6; D2=$(frames)
note "ANIMATION: forecast icons on the daily list: $(( ${D2:-0} - ${D1:-0} )) frames in 6 s (about 12 a second while icons are on screen)"
launch --es sample thunder-warning
adb shell input keyevent KEYCODE_HOME; sleep 4
B1=$(frames); sleep 6; B2=$(frames)
note "ANIMATION: in the background: ${B1:-?} then ${B2:-?} frames"
if [ "${B1:-0}" = "${B2:-0}" ]; then note "ANIMATION: stopped in the background"; else note "ANIMATION: still drawing in the background"; fi
adb logcat -d -s RBSBSky:I 2>/dev/null | grep "SKY FRAME WINDOW" | sed 's/.*SKY FRAME WINDOW /  SKY FRAME WINDOW /' | tail -12 | tee -a "$R"

# --- recordings: the sky, and scrolling down the first screen ---
launch --es sample rain-night-warnings; sleep 2
adb shell screenrecord --time-limit 6 /sdcard/rec.mp4 > /dev/null 2>&1 && adb pull /sdcard/rec.mp4 "$OUT/rec-sky.mp4" > /dev/null 2>&1 \
  && note "RECORDING sky: $(stat -c %s "$OUT/rec-sky.mp4" 2>/dev/null) bytes" || note "RECORDING sky: screenrecord not available"
adb shell rm -f /sdcard/rec.mp4
launch --es sample rain-night-warnings; sleep 2
( adb shell screenrecord --time-limit 9 /sdcard/rec.mp4 > /dev/null 2>&1 ) &
REC=$!
sleep 1
for i in 1 2 3 4 5 6 7; do adb shell input swipe $((W / 2)) $((W * 3 / 4)) $((W / 2)) $((W / 3)) 350; sleep 1; done
wait $REC
adb pull /sdcard/rec.mp4 "$OUT/rec-scroll.mp4" > /dev/null 2>&1 && note "RECORDING scroll: $(stat -c %s "$OUT/rec-scroll.mp4" 2>/dev/null) bytes" || note "RECORDING scroll: not available"
adb shell rm -f /sdcard/rec.mp4

sleep 2
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  note "CRASH: YES"
fi
if grep -q "ANR in $PKG" "$OUT/logcat.txt"; then note "ANR: YES"; fi
launch
if [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then note "PROCESS: running"; else note "PROCESS: not running"; fi

grep -q "CRASH: YES\|ANR: YES\|NOT found\|PROCESS: not running\|app NOT in front\|did NOT\|NOT running while shown\|still drawing" "$R" && { note "RESULT: FAIL"; exit 1; }
note "RESULT: PASS"
