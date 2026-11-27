# RBSB-Weather-Watch

RBSB-Weather for Samsung Galaxy Watch (Wear OS), fed by RBSB-Weather for Android.

**A standalone weather app, not a watch face.** It does not replace or change Samsung's watch face or clock.

Created By Shane BlackbuRn

## Status

**Version 0.1.0-dev, Stage 0 (project foundation).** The app is a shell with an About screen only. There are no weather features yet.

Work follows the approved plan, **RBSB-Weather for Galaxy Watch: Development Plan v1.1** (approved 28 Nov 2026):
https://claude.ai/artifact/PRGaNrySRq7hgTMNBheuzs

## What the finished app will be

- **RBSB-Weather for Android is the only weather source.** The phone sends weather to the watch over the Wear OS Data Layer. The watch never fetches weather and needs no API keys.
- **The animated sky is the central feature.** The watch app draws the full live sky locally at 30 fps, adjusting detail automatically. When the screen dims while the app is open, it shows a still, low-power sky.
- **Weather screens:** now, forecast, wind, warnings and Moon, plus an optional Tile (a static weather glance card that opens the app).
- **Not a watch face or clock.** There is no custom watch face. Leaving the app returns to Samsung's own watch face as normal.
- **Supported watches:** Galaxy Watch6 / Watch6 Classic and every newer generation, decided by Wear OS capabilities rather than model numbers. Galaxy Watch FE is unsupported but not blocked.

## Project layout

| Path | What it is |
|---|---|
| `wear/` | The Wear OS app (package `io.github.shanebl030glitch.rbsbweather`; debug builds `...rbsbweather.dev`) |
| `tools/signing-probe/` | Stage 0 test tool for signing gate 1. Never released; its own package |
| `scripts/` | 64-bit check and emulator scripts |
| `keystore/rbsb-test.keystore` | The shared, **public** debug key. Development builds only |
| `docs/` | Signing, SDK levels, signing gate 1 checklist, user guide |

## Building

Builds run in GitHub Actions (JDK 17, Android SDK 36):

- **Build and test:** unit tests, lint, debug and release APKs, 64-bit check, signing and package checks. The debug APK and its SHA256SUMS are an artifact.
- **Wear OS emulators:** installs and launches the app on Wear OS 4, 5, 6 and the newest available image at Galaxy Watch screen sizes, with and without rotary input.
- **Signing gate 1 kit:** throwaway-key probe APKs and the checklist in `docs/SIGNING_GATE_1.md`.

Locally: `./gradlew :wear:testDebugUnitTest :wear:assembleDebug`.

## Documents

- [CHANGELOG](CHANGELOG.md), [TRANSCRIPT](TRANSCRIPT.md), [TEST_REPORT](TEST_REPORT.md)
- [User guide](docs/USER_GUIDE.md), [Signing](docs/SIGNING.md), [SDK levels](docs/SDK_LEVELS.md), [Signing gate 1 checklist](docs/SIGNING_GATE_1.md)

## Related projects (not modified by this repository)

- RBSB-Weather (desktop): https://github.com/shanebl030-glitch/RBSB-Weather
- RBSB-Weather for Android: https://github.com/shanebl030-glitch/RBSB-Weather-Android
