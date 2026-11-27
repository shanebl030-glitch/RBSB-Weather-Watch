# Test report

RBSB-Weather for Galaxy Watch 0.1.0-dev. Created By Shane BlackbuRn

Results are split into **tested** (run and passed), **inferred** (follows from what was tested, not run directly) and **not yet tested**.

# Stage 1: sync format v1 (in review)

## Review fix: time-zone validation (`c9d7128`)

`place.timezone` was documented as an IANA name, but both checkers only checked its characters. Now:
- **Kotlin** checks it against `java.time.ZoneId.getAvailableZoneIds()`.
- **Python** checks it against `zoneinfo`, reading only the pinned `tzdata` package.
- Both exclude the same names, listed in `sync/contract/timezone-rules.json`.

Runs at `c9d7128` (push and pull request), all passed: Build and test `46081029446`, `46081039895`; Wear OS emulators `46081029436`, `46081039905`; Signing gate 1 `46081029444`, `46081040002`. The documentation commit after it was checked again by the same workflows (see the pull request).

Actual output (identical in both runs, apart from the JDK build):

```
  sync.TimeZoneTest: 7 run, 0 failed, 0 errors
    TIMEZONES (Java 17.0.21.1, tzdb 2026f): 591 names accepted      [second run: Java 17.0.21, tzdb 2026e, 591]
  sync.phone072.Phone072MappingTest: 7 run, 0 failed, 0 errors
TEST TOTALS (sync): 57 run, 57 passed, 0 failed, 0 errors, 0 skipped
TEST TOTALS: 60 run, 60 passed, 0 failed, 0 errors, 0 skipped
SAMPLES UP TO DATE: regenerating changes nothing
TIMEZONES (Python, tzdata 2026f): 591 names accepted
TIMEZONES MATCH: Java and Python accept the same 591 names
SAMPLES OK: 77 samples and 34 time-zone cases, every result matches the expected one
```

What the new tests cover:
- **Accepted:** 17 real zones, including `Pacific/Auckland`, `UTC`, `Etc/GMT+12`, and the old alias `Europe/Kiev`.
- **Rejected as `BAD_VALUE`** (14): `Not/ARealZone`, `auto`, `pacific/auckland`, `UTC+13`, `Z`, `SystemV/PST8`, `EST`, and others.
- **Rejected as `BAD_FORMAT`** (3): `+13:00`, a space, a trailing newline.
- **Beyond `ZoneId.of()`:** offset forms that `ZoneId.of()` itself accepts are rejected.
- **Exclusion list:** identical in the code and the contract file.
- **Phone side:** `encode()` refuses unknown zones, and a 0.7.2 forecast with `auto` is refused.

All earlier Stage 1 tests still pass unchanged (49 → 57 sync tests: +7 time-zone, +1 mapping). The schema's validation keywords are unchanged; only description text was added. The snapshot format is unchanged.

Code tested: `8856fb2` on `claude/stage-1-sync-format` (28 Nov 2026). Runs: Build and test `46078538186`, Signing gate 1 `46078538128`, Wear OS emulators `46078538171`; all passed. The documentation commit after it was checked again by the same workflows (see the pull request).

## Tested (GitHub Actions)

Actual CI output (run `46078538186`):

```
  AboutInfoTest: 3 run, 0 failed, 0 errors
TEST TOTALS (wear): 3 run, 3 passed, 0 failed, 0 errors, 0 skipped
  sync.CompatibilityTest: 7 run, 0 failed, 0 errors
  sync.FormatAndHelpersTest: 6 run, 0 failed, 0 errors
  sync.OrderingTest: 12 run, 0 failed, 0 errors
  sync.PayloadSafetyTest: 8 run, 0 failed, 0 errors
    SIZE largest snapshot: 9872 bytes compressed (target 20000, Data Layer limit about 100000)
    SIZE typical snapshot (48 h, 7 days, 3 warnings): 1294 bytes compressed
  sync.SamplesTest: 6 run, 0 failed, 0 errors
  sync.SchemaMatchesCodeTest: 4 run, 0 failed, 0 errors
    SCHEMA MATCH refresh: 12 checks
    SCHEMA MATCH settings: 32 checks
    SCHEMA MATCH snapshot: 284 checks
  sync.phone072.Phone072MappingTest: 6 run, 0 failed, 0 errors
    MAPPING 0.7.2 -> v1: 48 hours, 7 days, 3 warnings, 1497 bytes compressed
TEST TOTALS (sync): 49 run, 49 passed, 0 failed, 0 errors, 0 skipped
TEST TOTALS: 52 run, 52 passed, 0 failed, 0 errors, 0 skipped
SAMPLES UP TO DATE: regenerating changes nothing
SAMPLES OK: 74 samples, every result matches expected.json (schema + extra rules, independent of the Kotlin decoder)
LINT: 0 errors, 4 warnings
64-BIT OK: wear-debug.apk / wear-release-unsigned.apk
DEBUG OK: package ...rbsbweather.dev, minSdk 33, targetSdk 36, shared debug key
RELEASE OK: package ...rbsbweather, unsigned
PERMISSIONS OK (debug and release)
```

The lint warnings are the same "newer library version available" notices as Stage 0. Stage 0's fifth warning (a newer Gradle version) depends on what lint finds online and did not appear in this run.

| Acceptance criterion (approved Stage 1 proposal) | Result |
|---|---|
| 1. Every valid sample decodes and survives JSON and compressed round trips | Pass: 17 valid snapshots, 3 settings, 2 refresh (18 snapshots after the time-zone fix) |
| 2. Every invalid sample is rejected with its expected reason, never a crash | Pass: 42 invalid snapshots (44 after the time-zone fix) (every rejection reason that applies to text), 7 settings, 3 refresh. Bytes-level reasons (too large, corrupt) are in PayloadSafetyTest |
| 3. Unknown fields ignored; missing optional fields accepted | Pass: samples plus CompatibilityTest |
| 4. Largest snapshot under 20 KB compressed | Pass: 9,872 bytes |
| 5. Ordering: newer accepted; older, duplicate and future-dated handled as defined | Pass: 12 tests, including phone reinstall (3 cases) and the exact 10-minute boundary |
| 6. 0.7.2 mapping: real 0.7.2 data maps to a valid snapshot | Pass: 6 tests (168 h trimmed to 48, unrounded coordinates, missing values, long warning shortened, own limit, `fetchedAt = 0` refused) |
| 7. The Python check agrees with the Kotlin decoder on every sample | Pass: both match `expected.json` for all 74 samples (77 plus 34 time-zone cases after the fix, and the full Java and Python time-zone lists match). The schema-against-code test checks 328 rules field by field |
| 8. Existing baseline still passes | Pass: wear tests 3/3, lint 0 errors, 64-bit, permissions, signing gate 1 (`DEVICE IDENTITY OK` on phone and watch, `GATE 1 IDENTITY: PASS`), emulators 5/5 `RESULT: PASS` |
| 9. "Created By Shane BlackbuRn" in new code and docs | Present in every new source file, script, schema, sample generator and document |

Safety cases tested and rejected without a crash:
- a payload over 100 KB;
- a 5 MB zip bomb;
- empty, truncated, random and non-UTF-8 bytes;
- JSON that isn't one object;
- 200,000-deep nesting;
- the wrong payload kind in each codec.

`encode()` refused 9 kinds of bad snapshot.

The Python check was itself shown to fail, with exit code 1, on a wrong expectation, a broken valid sample and an unlisted file (local run on a throwaway copy).

## Inferred, not run directly

- The phone (0.8.0, if approved) can use an exact copy of `sync/src/main`: it depends only on Kotlin, `java.util.zip`, `java.time` and `org.json`, all of which Android provides.

## Not yet tested

- **Android's time-zone database.** On the watch, `ZoneId` uses Android's own database. It should accept the same names, but that is only verified when the Stage 3 receiver runs on the emulators. A zone newer than a watch's last time-zone update would be rejected by that watch.
- **Android's built-in `org.json`.** The decoder ran on the JVM with the Maven copy. They behave the same for what this format uses, but this is only verified when the Stage 3 receiver runs on the emulators.
- **Real Data Layer transfer.** Nothing is sent yet: the watch side is Stage 3; the live test is Stage 8 (signing gate 2, real paired devices).
- **Real-world provider data.** The 0.7.2 mapping test uses realistic data built in the test, not a live fetch.

# Stage 0: project foundation (merged 28 Nov 2026, `cb8e6d7`)

Latest code tested: `c569a89` on `claude/stage-0-foundation` (28 Nov 2026). Runs at `c569a89`: Build and test `46071621725` and `46071628042`; Signing gate 1 (automated identity check) `46071621888` and `46071628007`; Wear OS emulators `46071621793` and `46071628094`. The documentation commit after it was checked again by the same workflows (see the pull request). The emulator reliability figures below are from `6cb6fdc` (runs `45998461144`, `45998467423`, `45998497079`); the watch app itself has not changed since.

After the merge, all three workflows passed on `main` at the merge commit `cb8e6d7`: Build and test `46074926798` (on push), Wear OS emulators `46075622689` and Signing gate 1 `46075624120` (run by hand on `main`).

## Stage 0 results

| Check | Result |
|---|---|
| Unit tests (baseline suite) | 3 run, 3 passed, 0 failed |
| Lint | 0 errors. 5 warnings remain: newer Gradle/library versions available (kept in step with the Android app's versions for now) |
| Debug and release builds (compileSdk 36) | Built |
| 64-bit check | Pass: no native code in either APK |
| Debug APK | Package `io.github.shanebl030glitch.rbsbweather.dev`, versionCode 1000100, minSdk 33, targetSdk 36, signed with the shared debug key `a9618b32…4a36e87c` |
| Release APK | Package `io.github.shanebl030glitch.rbsbweather`, **unsigned** (as required) |
| Custom permissions | Debug and release each define only AndroidX's `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (allowed); nothing else |

Actual CI output lines (run `46071621725`):

```
TEST TOTALS: 3 run, 3 passed, 0 failed, 0 errors, 0 skipped
LINT: 0 errors, 5 warnings
64-BIT OK: wear/build/outputs/apk/debug/wear-debug.apk
64-BIT OK: wear/build/outputs/apk/release/wear-release-unsigned.apk
DEBUG OK: package ...rbsbweather.dev, minSdk 33, targetSdk 36, shared debug key
RELEASE OK: package ...rbsbweather, unsigned (signed later only by the protected release workflow)
PERMISSIONS OK
```

### Signing gate 1: automated signing identity and history verification — **PASS** (both runs at `c569a89`)

| Check | Result |
|---|---|
| Published 0.7.2 APK | SHA-256 matches `66bc5d44…74611`. History: test key `a9618b32…` → production key `02f6fc5f…` |
| 0.7.2 old-key flags | installed-data **yes**, permission **yes**, shared UID **no**, rollback **no**, auth **no** (read by `apksigner` in CI and independently by a local parser) |
| 0.7.2 lineage reuse | Extracted from the APK and read back with the same certificates and flags |
| Lineage extractor | Output byte-identical to the lineage file `apksigner` creates |
| Probe phone copy | Throwaway A → B, same `apksigner` options as 0.7.2 |
| Probe watch copy | Throwaway key B only (key A deleted first) + extracted lineage, v3 only |
| Copies compared | Same current signer, identical two-key history, old-key flags identical to 0.7.2 |
| Comparison self-test | Passes when system, app and APK file agree; fails on each of 5 kinds of disagreement |
| Phone emulator (API 34) | System (`dumpsys`), app (`PackageManager`) and APK file (`apksigner`) **agree**: current signer and two-key history `[old flags 5, new flags 17]` |
| Wear OS 6 emulator (API 36) | System, app and APK file **agree** on the same signer and history |
| Phone against watch | System current signer, system history with flags, app current SHA-256 and app history SHA-256 all **SAME** |

Actual output (run `46071621888`; throwaway keys, so the values change every run):

```
SELF-TEST OK: the comparison passes on agreement and fails on all 5 kinds of disagreement
EXTRACTOR OK: the lineage extracted from the phone APK is byte-identical to apksigner's
SAME FLAGS AS 0.7.2 for the old key: {'auth': False, 'installed_data': True, 'permission': True, 'rollback': False, 'shared_uid': False}
KIT OK: same current signer and same signing history on both copies
Phone:  DEVICE IDENTITY OK: system, app and APK file agree on the current signer and the history
Watch:  DEVICE IDENTITY OK: system, app and APK file agree on the current signer and the history
SAME: system current signer: phone ['b44ed500'] / watch ['b44ed500']
SAME: system signing history and flags: phone ['233077f flags: 5', 'b44ed500 flags: 17'] / watch [same]
SAME: app-reported current signer SHA-256: cfeeb0f0… / cfeeb0f0…
SAME: app-reported history SHA-256: [9e093ce1…, cfeeb0f0…] / [9e093ce1…, cfeeb0f0…]
GATE 1 IDENTITY: PASS. On each device the system, the app and the APK file agree;
```

One run (`46071628007`) first failed on a Maven Central download limit (HTTP 429) while building the probe, before any check ran; the re-run of that job passed every check.

Flags 5 = installed-data + permission for the old key, as in 0.7.2. Flags 17 is how Android records the current signer.

Emulator tests at `c569a89`: 10 of 10 passed (two runs), each printing `RESULT: PASS`.

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

## Gate 1 and gate 2

- **Gate 1 (tested above):** automated signing identity and history verification on emulators, with no pairing and no real key.
- **Gate 2 (stage 8, not yet tested):** live Data Layer communication on real paired devices.

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
