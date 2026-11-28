# RBSB-Weather-Watch

RBSB-Weather for Samsung Galaxy Watch (Wear OS), fed by RBSB-Weather for Android.

**A standalone weather app, not a watch face.** It does not replace or change Samsung's watch face or clock.

Created By Shane BlackbuRn

## Status

**Version 0.1.0-dev, Stage 2 (animated sky, in review).** Stages 0 (foundation) and 1 (sync format v1) are merged. The first screen is the live animated sky, drawn from the Stage 1 sample data and labelled SAMPLE DATA until the phone link arrives in Stage 3. About is on the second page.

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
| `wear/` | The Wear OS app (package `io.github.shanebl030glitch.rbsbweather`; debug builds `...rbsbweather.dev`): the sky (`scene/`, `sky/`; see `docs/SKY.md`) and About |
| `sync/` | Shared sync format v1 (Stage 1): plain Kotlin with no Android dependencies, JSON Schemas (`sync/contract`) and sample files (`sync/samples`). See `docs/SYNC_FORMAT.md` |
| `tools/signing-probe/` | Stage 0 test tool for signing gate 1. Never released; its own package |
| `scripts/` | 64-bit check, emulator scripts, the independent sync sample check (`check-samples.py`) and the signing gate 1 scripts |
| `keystore/rbsb-test.keystore` | The shared, **public** debug key. Development builds only |
| `docs/` | Sync format, signing, SDK levels, signing gate 1, user guide |

## Building

Builds run in GitHub Actions (JDK 17, Android SDK 36):

- **Build and test:** unit tests (wear app and sync module), sync sample checks (up to date, plus an independent Python check against the JSON Schemas), lint, debug and release APKs, 64-bit check, signing and package checks. The debug APK and its SHA256SUMS are an artifact.
- **Wear OS emulators:** installs and launches the app on Wear OS 4, 5, 6 and the newest available image at Galaxy Watch screen sizes, with and without rotary input; checks the sky screen (label, tap and rotary), that it animates while shown and stops in the background, and the About page; records screenshots, frame timing and short recordings.
- **Signing gate 1 (automated identity check):** checks the published Android 0.7.2 signing history, signs a throwaway probe the same way for phone and watch (throwaway keys), and confirms that on a phone emulator and a Wear OS 6 emulator the system, the app and the APK file agree on the signer and history, and that both devices match. Live Data Layer communication is signing gate 2, on real paired devices. See `docs/SIGNING_GATE_1.md`.

Locally: `./gradlew :wear:testDebugUnitTest :sync:test :wear:assembleDebug` and `python3 scripts/check-samples.py` (needs `pip install jsonschema`).

## Documents

- [CHANGELOG](CHANGELOG.md), [TRANSCRIPT](TRANSCRIPT.md), [TEST_REPORT](TEST_REPORT.md)
- [Animated sky](docs/SKY.md), [Sync format v1](docs/SYNC_FORMAT.md), [User guide](docs/USER_GUIDE.md), [Signing](docs/SIGNING.md), [SDK levels](docs/SDK_LEVELS.md), [Signing gate 1](docs/SIGNING_GATE_1.md)

Moon imagery: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO LROC WAC and LOLA data).

## Related projects (not modified by this repository)

- RBSB-Weather (desktop): https://github.com/shanebl030-glitch/RBSB-Weather
- RBSB-Weather for Android: https://github.com/shanebl030-glitch/RBSB-Weather-Android
