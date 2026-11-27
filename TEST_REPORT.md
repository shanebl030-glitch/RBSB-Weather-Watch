# Test report: Stage 0

RBSB-Weather for Galaxy Watch 0.1.0-dev. Created By Shane BlackbuRn

Latest code tested: `d791e80` on `claude/stage-0-foundation` (28 Nov 2026). Runs at `d791e80`: Build and test `46069091372` and `46069096984`; Signing gate 1 (automated identity check) `46069091337` and `46069096974`; Wear OS emulators `46069091266` and `46069096990`. The emulator reliability figures below are from `6cb6fdc` (runs `45998461144`, `45998467423`, `45998497079`); the watch app itself has not changed since.

Results are split into **tested** (run and passed), **inferred** (follows from what was tested, not run directly) and **not yet tested**.

## Tested (GitHub Actions)

| Check | Result |
|---|---|
| Unit tests (baseline suite) | 3 run, 3 passed, 0 failed |
| Lint | 0 errors. 5 warnings remain: newer Gradle/library versions available (kept in step with the Android app's versions for now) |
| Debug and release builds (compileSdk 36) | Built |
| 64-bit check | Pass: no native code in either APK |
| Debug APK | Package `io.github.shanebl030glitch.rbsbweather.dev`, versionCode 1000100, minSdk 33, targetSdk 36, signed with the shared debug key `a9618b32…4a36e87c` |
| Release APK | Package `io.github.shanebl030glitch.rbsbweather`, **unsigned** (as required) |
| Custom permissions | Debug and release each define only AndroidX's `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (allowed); nothing else |

### Signing gate 1: automated signing identity check — **PASS** (both runs at `d791e80`)

| Check | Result |
|---|---|
| Published 0.7.2 APK | SHA-256 matches `66bc5d44…74611`. History: test key `a9618b32…` → production key `02f6fc5f…` |
| 0.7.2 old-key flags | installed-data **yes**, permission **yes**, shared UID **no**, rollback **no**, auth **no** (read by `apksigner` in CI and independently by a local parser) |
| 0.7.2 lineage reuse | Extracted from the APK and read back with the same certificates and flags |
| Lineage extractor | Output byte-identical to the lineage file `apksigner` creates |
| Probe phone copy | Throwaway A → B, same `apksigner` options as 0.7.2 |
| Probe watch copy | Throwaway key B only (key A deleted first) + extracted lineage, v3 only |
| Copies compared | Same current signer, identical two-key history, old-key flags identical to 0.7.2 |
| Phone emulator (API 34) | Android records current signer + history `[old flags 5, new flags 17]`; the app sees A → B |
| Wear OS 6 emulator (API 36) | **Same** current signer and history as the phone; the app sees A → B |

Flags 5 = installed-data + permission for the old key, as in 0.7.2. Flags 17 is how Android records the current signer.

Emulator tests at `d791e80`: 10 of 10 passed (two runs).

### Wear OS emulators (x86_64, 64-bit images)

**15 of 15 emulator tests passed** at `6cb6fdc` (five configurations, three runs each), with no re-runs.


| Emulator | Image used | Install | App in front | About screen | Rotary | Crash |
|---|---|---|---|---|---|---|
| Wear OS 4, 432 px (un-updated Watch6) | API 33 `android-wear` | OK | Yes | Title and credit visible | Not configured | None |
| Wear OS 5, 480 px, rotary (Watch7/Ultra at launch) | API 34 `android-wear` | OK | Yes | Visible | Event accepted | None |
| Wear OS 6, 438 px (Watch8) | API 36.1 `android-wear-signed` | OK | Yes | Visible | Not configured | None |
| Wear OS 6, 480 px, rotary (Watch6 Classic) | API 36.1 `android-wear-signed` | OK | Yes | Visible | Event accepted | None |
| Newest image, 498 px (Ultra2) | **API 37.2 beta 1** `android-wear-signed` (Android 17) | OK | Yes | Visible | Not configured | None |

Screenshots: [docs/stage0/emulator-about-screens.png](docs/stage0/emulator-about-screens.png). On 432 px and 438 px screens the longer lines wrap, as expected.

## Problems found and fixed during Stage 0

1. CI checked `sdkVersion` instead of `minSdkVersion` (test script bug).
2. Wear OS 6 and 7 emulator images use the tag `android-wear-signed`, not `android-wear`. The workflow now finds any Wear OS tag.
3. `avdmanager` and the emulator used different AVD folders. Now one folder is set for both.
4. On fresh emulators the system sometimes returned to its built-in watch face after launch (the emulator's own face, not part of this project). The test now keeps the screen on and confirms the app is in front.
5. On the production-signed Wear OS 6/7 images, adb sometimes stayed unauthorised (3 of 15 jobs before the fix). Cause: the emulator could boot before adb created its key. Fixed by creating the key and starting adb first; 0 of 30 jobs after the fix.
6. Two emulator runs failed on Maven Central download errors while building, before any emulator started: five parallel jobs each downloaded every dependency. The APK is now built once and shared by the five emulator jobs.

## Inferred

- The "Wear OS 7" emulator is a **beta** image (API 37.2 beta 1). A stable Wear OS 7 image was not available in the SDK repository on 27 Nov 2026. Results on it are indicative only.
- The rotary test only confirms the system accepted a rotary event without a crash. With four short lines there is nothing to scroll yet, so scrolling behaviour is untested until real content exists.

## Not yet tested

- **Live Data Layer communication** between the release-signed phone and watch apps: moved to **signing gate 2** (stage 8, real devices). The manual emulator pairing test was retired on 28 Nov 2026 (see [docs/SIGNING_GATE_1.md](docs/SIGNING_GATE_1.md)).
- How Google Play services compares signatures for rotated keys is not documented. Gate 1 makes both apps present **identical** signing information, so any comparison sees the same thing; gate 2 confirms it live.
- Anything on real Samsung hardware (stage 9).
- Always-on behaviour, battery and performance (later stages; the shell has nothing to measure).

## Gate 1 rework (28 Nov 2026): problems found and fixed

1. `apksigner` aligns its capability lines (`capability    :`); the new lineage check did not allow for the spaces.
2. The new permission check read the package name from the wrong field of `aapt2`'s output.
3. `apksigner` cannot write a lineage file straight from an APK; a small extractor was added and proved byte-identical in CI.
4. Signing the watch copy with key B only failed while v1/v2 signatures were enabled (they need the old key). The watch copy is signed v3 only, which every supported watch verifies.
