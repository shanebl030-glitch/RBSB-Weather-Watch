# Test report: Stage 0

RBSB-Weather for Galaxy Watch 0.1.0-dev. Created By Shane BlackbuRn

Code tested: `6cb6fdc` on `claude/stage-0-foundation` (27–28 Nov 2026). Later commits change documentation only. Runs: Build and test `45998461300` and `45998467459`, Signing gate 1 kit `45998461152`, Wear OS emulators `45998461144`, `45998467423` and `45998497079` (three full runs of all five emulators).

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
| Signing gate 1 kit | Phone copy: v3 signer key B with a two-key lineage A → B, same `apksigner` options as Android 0.7.2. Watch copy: key B only, no lineage. Current signers match. Throwaway keys deleted after the run |

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
4. On fresh emulators the system sometimes returned to the watch face after launch. The test now keeps the screen on and confirms the app is in front.
5. On the production-signed Wear OS 6/7 images, adb sometimes stayed unauthorised (3 of 15 jobs before the fix). Cause: the emulator could boot before adb created its key. Fixed by creating the key and starting adb first; 0 of 30 jobs after the fix.
6. Two emulator runs failed on Maven Central download errors while building, before any emulator started: five parallel jobs each downloaded every dependency. The APK is now built once and shared by the five emulator jobs.

## Inferred

- The "Wear OS 7" emulator is a **beta** image (API 37.2 beta 1). A stable Wear OS 7 image was not available in the SDK repository on 27 Nov 2026. Results on it are indicative only.
- The rotary test only confirms the system accepted a rotary event without a crash. With four short lines there is nothing to scroll yet, so scrolling behaviour is untested until real content exists.

## Not yet tested

- **Signing gate 1 pairing test:** needs Shane to pair a phone emulator and a watch emulator in Android Studio and follow [docs/SIGNING_GATE_1.md](docs/SIGNING_GATE_1.md). **Stage 0 is not complete until this result is in.**
- Anything on real Samsung hardware (stage 9).
- Always-on behaviour, battery and performance (later stages; the shell has nothing to measure).
