# RBSB-Weather for Galaxy Watch: user guide

Created By Shane BlackbuRn

**Version 0.1.0-dev (Stage 0).** This is a development shell, not a usable weather app yet.

## What it does now

Opening the app shows an About screen: the app name, its version and "Created By Shane BlackbuRn". There is no weather, sync, watch face, Tile or complication yet. Those arrive in later stages of the approved plan.

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
