# Changelog

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

Version codes use their own range: 1_000_000 + major × 10_000 + minor × 100 + patch.

## 0.1.0-dev (Stage 1: sync format v1, in review)

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
