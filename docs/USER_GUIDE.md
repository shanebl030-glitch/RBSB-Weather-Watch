# RBSB-Weather for Galaxy Watch: user guide

Created By Shane BlackbuRn

**Version 0.1.0-dev (Stage 3, in progress).** A development build. The weather screens work, but until RBSB-Weather for Android 0.8.0 sends real weather, development builds show built-in sample weather, always labelled **SAMPLE DATA**.

RBSB-Weather for Galaxy Watch is a **weather app, not a watch face**. It never replaces or changes Samsung's watch face or clock.

## Getting around

- **One scroll from the sky down:** the app opens on the live animated sky. Turn the bezel or crown, or swipe up, to scroll down through Now, Hourly, Daily, Wind, Warnings, Moon and About.
- **Details:** tap a day, a warning, "All 48 hours" or the Moon. Tap the Moon picture on the Moon screen to see it full screen.
- **Back:** swipe right or press the watch's Back button. From the sky, this leaves the app and returns to your normal Samsung watch face.
- **Warnings chip:** when warnings are active, a coloured chip on the sky shows how many; tap it to go to them.

## The sky

The place, temperature and conditions over the live animated sky, with today's high and low and how old the weather is.

- **The data's age is always shown** ("Updated 12 min ago").
- **Old weather is never shown as current:**
  - 45 minutes to 3 hours old: only the age line changes.
  - 3 to 12 hours old: an **Old data** chip, and "now" comes from the forecast for this hour ("Forecast for 15:00"). Readings the forecast doesn't include (humidity, pressure) are left out rather than shown out of date.
  - Over 12 hours old: **No update since …**, with the temperature dimmed. The forecast is still used.
  - These chips show the time of the last update if it was today, or just the day if it was earlier (the age line below gives the hours).
  - Past the end of the forecast: **No current data**, with the last known conditions labelled as such.
- **Saving battery:** the sky moves only while you can see it. It stops when you scroll down, leave the app or the watch dims.
- **Detail adjusts by itself:** if the watch can't keep up, the sky uses less detail (fewer drops, simpler clouds) rather than dropping any effect.

## The Moon

The Moon is the same NASA lunar imagery as RBSB-Weather Desktop and Android, lit for its real phase and turned as it looks from your place. Its phase and position are calculated on the watch for the moment shown, with the same calculation as Desktop and Android, so it stays right even when the weather data is old.

## Times

Times are shown in the place's own time. When your watch is set to a different time zone, a short label is added (for example "14:05 NZDT", or "UTC+13" where the watch has no short name for the zone).

## Sample data (development builds only)

- With no phone data yet, development builds show the Stage 1 samples, labelled **SAMPLE DATA · 1/18** on the sky.
- Tap the sky to move to the next sample. About shows which sample it is.
- Release builds never contain or show sample weather.

## About

About shows the version, the place, the weather provider and when the weather was fetched, the phone's status (once the phone link is ready), "Created By Shane BlackbuRn", the NASA credit for the Moon imagery, and the sky's latest performance (for example "Sky: High · 30.0 fps"), which helps when testing on a real watch.

## Supported watches (planned)

Galaxy Watch6 / Watch6 Classic and every newer Galaxy Watch generation, decided by Wear OS capabilities rather than model numbers. Galaxy Watch FE is unsupported but not blocked.

## Installing a development build

Development builds are only for testing. They install as a separate app (`...rbsbweather.dev`) and do not replace a future release. Full step-by-step instructions for Linux Mint come with the private beta at the end of Stage 3.

1. On the watch: **Settings → About watch → Software information**, tap **Software version** until developer mode turns on.
2. **Settings → Developer options**: turn on **ADB debugging** and **Wireless debugging**.
3. On your computer: pair with **Wireless debugging → Pair new device** and `adb pair <ip>:<port>`, then `adb connect <ip>:<port>`.
4. Check the file: `sha256sum -c SHA256SUMS`, then `adb install rbsb-weather-watch_0.1.0-dev_debug.apk`.

To remove it: `adb uninstall io.github.shanebl030glitch.rbsbweather.dev`.
