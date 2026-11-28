# RBSB-Weather for Galaxy Watch: user guide

Created By Shane BlackbuRn

**Version 0.1.0-dev (Stage 2).** A development build: the animated sky works, but it shows sample weather, not your phone's, until Stage 3.

## The sky (first screen)

Opening the app shows the live animated sky with the place, temperature, conditions and today's high and low.

- **SAMPLE DATA:** the yellow label means the weather shown is a built-in sample, not real weather. The line under it shows which sample, such as "5/18 · rain + warnings".
- **Switch samples:** tap the screen, or turn the bezel or crown.
- **About:** swipe sideways to reach the About page and back.
- **Saving battery:** the sky moves only while you're looking at it. It stops when you leave the app or the watch dims, and leaving the app returns to your normal Samsung watch face.
- **Detail adjusts by itself:** if the watch can't keep up, the sky uses less detail (fewer drops, simpler clouds) rather than dropping any effect. In power-saving mode it also moves a little less smoothly.

RBSB-Weather for Galaxy Watch is a **weather app, not a watch face**. It never replaces or changes Samsung's watch face or clock.

## About page

The About page shows the version, "Created By Shane BlackbuRn", the NASA credit for the Moon pictures, and, once the sky has run for a few seconds, its latest performance, for example "Sky: High · 30.0 fps". That line helps when testing on a real watch.

## Supported watches (planned)

Galaxy Watch6 / Watch6 Classic and every newer Galaxy Watch generation, decided by Wear OS capabilities rather than model numbers. Galaxy Watch FE is unsupported but not blocked.

## Installing a development build

Development builds are only for testing. They install as a separate app (`...rbsbweather.dev`) and do not replace a future release.

1. On the watch: **Settings → About watch → Software information**, tap **Software version** until developer mode turns on.
2. **Settings → Developer options**: turn on **ADB debugging** and **Debug over Wi-Fi**. Note the IP address and port shown.
3. On your computer: `adb connect <ip>:<port>` (newer watches may ask you to pair first with **Wireless debugging → Pair new device** and `adb pair <ip>:<port>`).
4. `adb install rbsb-weather-watch_0.1.0-dev_debug.apk`
5. Check the file first: `sha256sum -c SHA256SUMS`.

To remove it: `adb uninstall io.github.shanebl030glitch.rbsbweather.dev`.
