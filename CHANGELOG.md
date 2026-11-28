# Changelog

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

Version codes use their own range: 1_000_000 + major × 10_000 + minor × 100 + patch.

## 0.1.0-dev (Stage 3: the weather app, in progress on `claude/stage-3-weather-app`)

Approved 28 Nov 2026 with decisions D1 to D10. The version is unchanged. Not merged.

- **First screen (D1):** one vertical scroll from the animated sky through Now, Hourly (6 hours, then all 48), Daily (tap a day), Wind, Warnings (tap a warning), Moon (tap for the Moon screen, tap again for full screen, D8) and About. Bezel, crown and touch all scroll; swipe right or Back returns.
- **Old data is never shown as current:** freshness states (45 min, 3 h, 12 h, end of forecast, D4), "Forecast for 15:00" and "Last known" labels, the age always shown; readings the forecast lacks are left out. The sky follows the place's real local time and, with old data, this hour's forecast.
- **Moon (D7 to D10):** 1024 px NASA textures; decoded textures released after scaling; phase, illumination and position calculated live with the shared Moon code, the phone's values as cross-check and fallback. Ported from the verification branch with its tests; that branch is not merged.
- **Data core:** a checked, atomically written cache; units and 12/24 h from the phone's settings; times in the place's zone with a label when the watch differs (D3); the refresh rule (once per 10 min, data over 45 min old).
- **Sample data (D2):** debug builds only, labelled SAMPLE DATA; release APKs contain none.
- **Fixed during Stage 3, found by the emulator tests:**
  - the Moon screen would have crashed on Wear OS 4 (an API 34 call);
  - open screens did not update after their first draw;
  - the first Back-button press did nothing on Wear OS 4.
- **Tests:** 176 unit and render tests (Moon parity, data age, rows, screen text, cache with every Stage 1 sample); on every emulator, every screen and data state is screenshotted, plus round-screen fit, bezel, touch, back, animation stopping and the on-device Moon test.
- **Libraries (D6):** play-services-wearable 20.0.1, Wear Compose navigation; no new permissions; the debug-only test activity is kept unexported.

## 0.1.0-dev (Stage 2: animated sky, merged 28 Nov 2026, PR #3)

The animated sky, from sample data. The version is unchanged. See `docs/SKY.md`.

- **Sky first, About second (decision 7):** the sky is the first screen; swipe sideways to About.
- **The sky:** drawn by the scene code copied from RBSB-Weather for Android v0.7.2 (commit `cdf422d`; each file records its source and the watch's changes). The Android repository is unchanged.
  - Effects: wind-driven clouds, rain and splashes, drizzle, snow, fog, lightning, the Sun's arc, the Moon's phase and tilt, stars, day/night fades.
  - It reads the Stage 1 snapshot. The sync format is unchanged.
- **Moon (decision 1, option C):**
  - phase and illumination from the snapshot;
  - live position and tilt calculated on the watch;
  - rise and set from the snapshot when they span the moment shown.
- **Sample data (decision 2):** the Stage 1 valid samples are bundled at build time and labelled SAMPLE DATA. Tap or turn the bezel/crown to switch. Names are shortened on screens under 230 dp (432 px).
- **Animation:** 30 fps, only while the sky is on screen and the app is in front; it stops in the background and when the watch dims.
- **Quality levels (decision 3):**
  - High, Standard and Light change detail only; every effect stays at every level.
  - The level is chosen from measured frame times: the first 3 s, then every 10 s. Step down above 10% slow frames (over 33 ms or late); step up after 30 s with p90 work under 20 ms. Power-saving mode goes one level lower and caps at 20 fps.
  - The thresholds are adjustable (`QualityConfig`).
- **Frame timing:** each 10-second window is logged (`RBSBSky`) and kept in `sky-frame-timing.csv` (never more than the last 500 windows) for Stage 7. The latest is shown on About.
- **Memory:** one cloud sprite set (a cache of 40; the phone uses 96). 512 px Moon textures, about 147 KB (decision 5); NASA credit on About and in the docs.
- **Review fixes:**
  - The Sun stays bright: the readability shade is drawn under the Sun, Moon and clouds.
  - Rotary sample switching is confirmed on emulators. The test was sending its command in an old form; the app also passes on rotary events no view took.
  - The Moon was confirmed below the horizon in the clear-night sample.
  - The UseKtx lint warnings are gone.
- **Fixed in review:** the frame-timing file could grow to 600 windows between clean-ups although documented as 500; it now never holds more than 500, and the test checks this after every append.
- **Fixed during testing:** the screen opened with a 0.8-second fade from daytime blue (seen on night samples); it now opens directly on the sample's own sky.
- **Tests (decision 6):**
  - 132 unit tests (75 wear, 57 sync). The phone's scene, Moon (PyEphem reference) and safe-drawing tests are ported, plus new quality, frame-timing, Moon-horizon and sample tests.
  - Robolectric 4.18.1 render tests on SDK 36, test-only. Robolectric needs Java 21 for SDK 36, so CI runs the wear unit tests on Java 21; the app is still built for Java 17.
- **Emulator test:** sample label, tap and rotary switching, 10 sample screenshots, About credit, animation running while shown and stopped in the background, frame-timing windows, and three 6-second recordings.

## 0.1.0-dev (Stage 1: sync format v1, merged 28 Nov 2026)

Defines what RBSB-Weather for Android will send the watch. No weather features, no screen changes, and nothing is sent or received yet (watch receiver: Stage 3; phone side: Android 0.8.0, which needs approval).

- New `sync` module: plain Kotlin with **no Android dependencies**, so the phone app can later use an exact copy. The wear app depends on it, but no screen uses it yet.
- Sync format v1: weather snapshot (`/rbsb/v1/snapshot`), settings (`/rbsb/v1/settings`), refresh request (`/rbsb/v1/refresh`), capability names, gzip JSON, size limits. The largest snapshot is about 10 KB compressed (target under 20 KB).
- Snapshot contents as in plan v1.1 section 5, including Moon phase, illumination, tilt and rise/set times. Hourly wind is optional, falling back to the current wind. Warnings are what the phone shows after its own filters: the user's limits are included, muted ones are not.
- Six settings: temperature, wind and rain units, 12/24-hour clock, animations on/off, minimum warning level.
- Strict decoding: every payload is untrusted. A bad one is rejected with a reason, never a crash. The phone-side `encode()` refuses anything the watch would reject.
- Ordering: fetchedAt first, then sequence; a per-install `sourceId` handles phone reinstalls. Snapshots more than 10 minutes in the future are rejected.
- JSON Schemas (`sync/contract`), 77 sample files with expected results (`sync/samples`), `docs/SYNC_FORMAT.md`.
- Tests: 49 new unit tests, including a schema-against-code check (328 rule checks) and a mapping test using test-only copies of the Android 0.7.2 data classes (that repository is unchanged). `scripts/check-samples.py` is an independent Python check of the same samples.
- CI runs the sync tests, regenerates the samples to confirm they are up to date, and runs the Python check.
- Review fix: `place.timezone` must now be a real IANA time zone. Kotlin checks it against `java.time.ZoneId`'s database and the Python check against `zoneinfo` with the `tzdata` package, both with the same exclusion list (`sync/contract/timezone-rules.json`). Unknown names such as `Not/ARealZone` or `auto` are rejected as `BAD_VALUE`; wrong characters stay `BAD_FORMAT`. CI compares the full lists of accepted names from both. The Python check also now uses full matches for patterns, as Kotlin does (a trailing newline is no longer accepted). New: `TimeZoneTest` (7 tests), a 0.7.2 `auto` mapping test, 3 samples and 34 shared time-zone cases. The snapshot format is unchanged.

## 0.1.0-dev (Stage 0, merged 28 Nov 2026)

Project foundation only. No weather features.

- Scope clarified (plan v1.1): a standalone weather app, not a watch face. Documentation updated; watch-face key and references removed. No code changes were needed.

- Gradle project with a `wear` module: package `io.github.shanebl030glitch.rbsbweather`, minSdk 33 (Wear OS 4), targetSdk 36, versionCode 1000100.
- Minimal watch app shell: one About screen with the title, version and "Created By Shane BlackbuRn".
- Development builds signed with the shared public debug key and installed as `...rbsbweather.dev`. Release builds are unsigned.
- CI: unit tests, lint, 64-bit check, signing and package checks, debug APK with SHA256SUMS.
- Wear OS emulator set: Wear OS 4, 5, 6 and the newest available image, at 432 to 498 px, with and without rotary input.
- Signing gate 1 is an **automated signing identity check** (replaces the manual pairing checklist): verifies the published 0.7.2 signing history and flags; signs a throwaway probe for phone (A → B) and watch (key B + extracted public lineage, v3 only); on each of a phone emulator and a Wear OS 6 emulator, fails unless the system (`dumpsys`), the app (`PackageManager`, logged by the probe) and the APK file (`apksigner`) agree on the signer and history, then requires both devices to match. A self-test proves the comparison fails on disagreement.
- Watch signing decision (Option A): same signing history and flags as Android 0.7.2.
- CI: the app may define no custom permission except AndroidX's own.
- Backup and device transfer exclude all app data (the weather cache will come back from the phone).
- Docs: README, SIGNING, SDK levels, signing gate 1 checklist, user guide, transcript, test report with emulator screenshots.
