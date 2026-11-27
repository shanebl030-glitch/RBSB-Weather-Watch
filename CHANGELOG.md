# Changelog

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

Version codes use their own range: 1_000_000 + major × 10_000 + minor × 100 + patch.

## 0.1.0-dev (Stage 0, in review)

Project foundation only. No weather features.

- Scope clarified (plan v1.1): a standalone weather app, not a watch face. Documentation updated; watch-face key and references removed. No code changes were needed.

- Gradle project with a `wear` module: package `io.github.shanebl030glitch.rbsbweather`, minSdk 33 (Wear OS 4), targetSdk 36, versionCode 1000100.
- Minimal watch app shell: one About screen with the title, version and "Created By Shane BlackbuRn".
- Development builds signed with the shared public debug key and installed as `...rbsbweather.dev`. Release builds are unsigned.
- CI: unit tests, lint, 64-bit check, signing and package checks, debug APK with SHA256SUMS.
- Wear OS emulator set: Wear OS 4, 5, 6 and the newest available image, at 432 to 498 px, with and without rotary input.
- Signing gate 1 is an **automated signing identity check** (replaces the manual pairing checklist): verifies the published 0.7.2 signing history and flags; signs a throwaway probe for phone (A → B) and watch (key B + extracted public lineage, v3 only); confirms phone and Wear OS 6 emulators report the same signer and history.
- Watch signing decision (Option A): same signing history and flags as Android 0.7.2.
- CI: the app may define no custom permission except AndroidX's own.
- Backup and device transfer exclude all app data (the weather cache will come back from the phone).
- Docs: README, SIGNING, SDK levels, signing gate 1 checklist, user guide, transcript, test report with emulator screenshots.
