# Session transcript

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

A summary of decisions and work, newest last.

## 27 Nov 2026: planning

- Shane asked for a Galaxy Watch version of RBSB-Weather that keeps the animated weather experience. No coding until a plan was approved.
- Claude inspected the desktop and Android repos. The Android scene code (SceneEngine, SceneDraw, MoonRender) is pure Compose drawing and can be reused on Wear OS.
- Key finding: Wear OS watch faces must use the Watch Face Format (no code), so the live engine runs in the watch app; the watch face plays pre-rendered loops chosen through a complication. *(Superseded 28 Nov 2026: there is no watch face.)*
- Decisions, over four revisions: RBSB-Weather for Android is the only weather source (Wear OS Data Layer); Galaxy Watch6 generation onwards by capability; Watch FE unsupported but not blocked; minSdk 33, targetSdk 36 (fallback 35); WFF version 1 and a separate watch face key *(both superseded 28 Nov 2026)*; matching `.dev` debug suffixes; one synced location; test devices chosen at stage 9.
- Shane approved **Development Plan v1.0** and asked for Stage 0 only.

## 27–28 Nov 2026: Stage 0

- Shane created the public repository `RBSB-Weather-Watch`. Claude made the initial commit on `main` (`ef212e2`, stub README only) and the branch `claude/stage-0-foundation`.
- Signing gate 1 cannot pair emulators on GitHub's runners (pairing needs the Wear OS companion app and a Google sign-in). Shane chose to run the pairing test himself on his PC with Android Studio emulators.
- Built on the branch: the Gradle project and `wear` shell (About screen only), shared debug signing with the `.dev` package, CI (tests, lint, 64-bit, signing and package checks), the Wear OS emulator set, the signing gate 1 kit and checklist, and the docs.
- CI runs found four harness problems, all fixed: a wrong `minSdk` check, Wear OS 6/7 images using the `android-wear-signed` tag, two different AVD folders, and the app sometimes not staying in front on a fresh emulator. One adb authorisation flake passed on re-run. Lint warnings for backup rules and the icon folder were fixed.
- A further harness fix: on the production-signed Wear OS 6/7 images adb could stay unauthorised; adb's key is now created before the emulator boots.
- Two runs hit Maven Central download errors before any emulator started; the five emulator jobs now share one APK build.
- Final results at `6cb6fdc`: all workflows green; 15 of 15 emulator tests passed over three full runs, with no re-runs (Wear OS 4, 5, 6 ×2 and a Wear OS 7 beta image). Pull request #1 opened for Shane's review. The signing gate 1 pairing test is waiting for Shane.
- Results are in TEST_REPORT.md. Neither existing RBSB repository, Android 0.7.2 or the 0.8.0 proposal was touched.

## 28 Nov 2026: scope clarified (plan v1.1)

- Shane clarified that RBSB-Weather for Galaxy Watch is **strictly a standalone weather app**: not a watch face, not a clock, and never a replacement for Samsung's watch face, which stays untouched.
- Plan v1.1 removes the Watch Face Format project, its separate package and signing key, the watch-face animation loops and the watch-face stages (12 stages became 10). Kept: the Data Layer link, the live animated sky, weather screens, forecasts, warnings, Moon, caching and staleness handling.
- The Tile is kept as an optional, strictly weather-related glance card that never replaces or interferes with Samsung's watch face or clock. Complications on Samsung's faces may be considered separately later; they are not in the plan.
- Shane approved plan v1.1. Stage 0 needed documentation changes only (no code referenced a watch face); they were made in one commit on `claude/stage-0-foundation`. Signing gate 1 still needs Shane's PC test.

## 28 Nov 2026: signing gate 1 becomes automatic; watch signing decision

- Shane paused the manual gate 1 test: Android Studio's Wear OS pairing could not find the watch emulator.
- Investigation: Google documents that the Data Layer needs matching package names and signatures, but not how Play services compares signatures when one app has a key-rotation history. Pairing cannot be automated (it needs the companion app and a Google sign-in).
- **Decisions (Shane):** gate 1 becomes an automated signing identity check; **Option A**: the watch app uses the same signing history as Android 0.7.2. Live Data Layer testing moves to gate 2; real Galaxy Watch testing remains required.
- Verified from the published 0.7.2 APK (checksum matched): old test key `a9618b32…` → production key `02f6fc5f…`; old-key flags installed-data and permission only (no rollback, shared UID or auth). The one custom permission 0.7.2 defines is AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Built and passed (two runs): the 0.7.2 reference check, a probe kit (phone A → B; watch key B only + extracted public lineage, v3 only, with key A deleted first), and identical signer and history reported by a phone emulator and a Wear OS 6 emulator. CI now refuses any custom permission other than AndroidX's own. The production key was never used.

## 28 Nov 2026: gate 1 compares the app's view with the system's

- Shane noticed the probe app's own report of its signing identity was printed but not checked. He asked for it to be compared automatically, without weakening the existing checks.
- The probe now logs its identity (`PackageManager`) as JSON. On each emulator, CI fails unless the system (`dumpsys`), the app and the APK file (`apksigner`) agree on the current signer and the full history; a final job then requires the phone and watch to match on all of them. A self-test proves the comparison fails on five kinds of disagreement. No existing check was removed.
- Passed in both runs at `c569a89` (one run needed a re-run after a Maven Central 429 before any check ran). The full Stage 0 checks were run again on the documentation commit. Gate 1 = automated signing identity and history verification; gate 2 = live Data Layer on real paired devices (stage 8).

## 28 Nov 2026: Stage 0 merged

- Shane approved Stage 0. PR #1 was merged into `main` (merge commit `cb8e6d7`). All three workflows passed on `main`: Build and test on the merge, and Wear OS emulators and Signing gate 1 run by hand (they only run automatically on work branches and pull requests).

## 28 Nov 2026: Stage 1, sync format v1

- Claude reported the Stage 1 scope from plan v1.1 and six decisions. Shane decided:
  1. Watch-side tests plus an independent Python schema check; Android 0.8.0 stays separate and needs approval.
  2. Moon phase, illumination, tilt and rise/set stay in the snapshot, as the plan specifies.
  3. Hourly wind optional, falling back to the current wind.
  4. fetchedAt first, sequence as tie-breaker; future-date tolerance documented and tested; phone reinstalls handled.
  5. All six settings in v1.
  6. Warnings as the phone shows them after its filters: user limits included, muted ones excluded.
- Built on `claude/stage-1-sync-format`:
  - the `sync` module (no Android dependencies);
  - JSON Schemas and 74 sample files;
  - 49 tests, including a schema-against-code check and a mapping test using test-only copies of the Android 0.7.2 data classes;
  - `scripts/check-samples.py`;
  - `docs/SYNC_FORMAT.md`.
- Reinstalls are handled with a per-install `sourceId`: a reinstalled phone's fresh weather wins on fetchedAt, and a re-send of its cached weather wins because the id changed. The future tolerance is 10 minutes.
- Maven Central is blocked from Claude's workspace, so the Kotlin code was built and tested only in GitHub Actions. All three workflows passed on the first run (`8856fb2`). The Python check was run locally as well.
- Version stays 0.1.0-dev. No screen changes. RBSB-Weather-Android (only read) and WeatherDesk were not touched. No production key; nothing published.

## 28 Nov 2026: Stage 1 review fix, time-zone validation

- Shane's review found that `place.timezone` was documented as an IANA name, but both checkers only checked its characters. He asked for a narrow fix with no format change.
- **Kotlin** now checks it against `java.time.ZoneId`'s available IDs. **Python** checks it against `zoneinfo`, reading only the pinned `tzdata` package, because Debian's system copy omits the old alias names.
- Comparing the two databases showed non-zone and Java-unsupported names on each side. Both now exclude the same list (`sync/contract/timezone-rules.json`), and CI compares the full accepted lists: 591 names each, identical.
- Unknown names are `BAD_VALUE`; wrong characters stay `BAD_FORMAT`. The Python check also now uses full pattern matches, as Kotlin does.
- **Finding for the phone (0.8.0):** Android 0.7.2 stores `auto` for My location, and with MET Norway it reaches `Forecast.timezone`. The phone must send a real zone name; a test documents that `auto` is refused. Nothing in the phone app was changed.
- Build and test passed at `c9d7128`: 60 of 60 tests, time zones match. Version 0.1.0-dev; no Stage 2; nothing published.

## 28 Nov 2026: Stage 2 approved; milestone 1 (design review)

- Shane merged Stage 1 (PR #2, `fd00950`) and approved the Stage 2 scope with conditions:
  1. Moon option C.
  2. Sample data in debug and release builds until Stage 3, clearly labelled, switchable by tap or rotary.
  3. The proposed quality thresholds, adjustable, with measurements recorded for Stage 7.
  4. Forecast icons left for Stage 3.
  5. A 512 px Moon texture with the NASA credit.
  6. Test-only Robolectric, with SDK 36 compatibility verified in CI first.
  7. The sky as the first screen, About on the second page.
- He asked to see the watch design early: a basic sky screen with sample data and emulator screenshots (432, 480 and 498 px) for review before the full effects and animation work.
- Built milestone 1 on `claude/stage-2-animated-sky`: the copied scene code adapted to the Stage 1 snapshot, the sky and About pages, bundled samples, 512 px Moon textures, the emulator screenshots, and unit tests for sample loading, the text over the sky and the snapshot-to-sky conditions (including the Moon, option C).
- Note: the CI emulators capture the full square framebuffer; Galaxy Watch screens are round. The layout keeps content inside the circle, and review images have a circular mask applied afterwards, labelled as such.

## 28 Nov 2026: Stage 2 completed (in review)

- Shane approved the milestone 1 design and asked for five fixes: a bright Sun, rotary switching, confirming the missing Moon, shorter sample names at 432 px, and the lint warnings. Then the animation, quality levels, frame timing and remaining tests.
- **Sun:** the readability shade now sits under the Sun, Moon and clouds.
- **Rotary:** the emulator test used an old command form with no scroll amount. With `--axis SCROLL,3` the app switches samples, and the logs show the event arriving through Compose. An activity fallback was added too.
- **Moon:** confirmed below the horizon at 23:00 in Auckland (13° below, the same as the desktop `moon.py`).
- **Names:** shortened below 230 dp.
- **Lint:** the UseKtx warnings are fixed.
- **Built:** 30 fps animation only while shown; High/Standard/Light levels chosen from measured frame times with the approved thresholds (adjustable); frame windows logged and stored for Stage 7; one cloud sprite set; ported scene, Moon and safe-drawing tests; new tests; Robolectric 4.18.1 render tests on SDK 36.
- **Unexpected problem, reported to Shane before continuing:** Robolectric needs Java 21 for SDK 36. CI now runs the wear unit tests on Java 21; the app is still built for Java 17.
- **Found by a test:** the screen opened with a 0.8-second fade from daytime blue; it now opens on the sample's own sky.
- **Milestone media sent to Shane:** a before/after of the Sun, lightning stills, and 6-second recordings at 432, 480 and 498 px.
- **Results at `0796ed4`:** 132/132 tests, lint 0 errors, 5/5 emulators PASS (rotary included), Signing gate 1 PASS. Version 0.1.0-dev. Android and WeatherDesk untouched; no production key; nothing published.

## 28 Nov 2026: Stage 2 review corrections

- Shane reviewed PR #3 and asked for two corrections before final approval.
- **TEST_REPORT.md:** the Stage 2 results now name the final code commit (`d8911ef`) with its pull-request and push run IDs, and the emulator measurements come from that pull-request run. `40d7bb5` (before the fix) is recorded as also passing. A commit can't contain its own ID, so the report's update is a documentation-only commit after `d8911ef`, checked by the same workflows.
- **SkyStats:** the frame-timing file was documented as keeping the last 500 windows but trimmed only above 600. It now never holds more than 500; the test checks this after every append. SKY.md and the CHANGELOG agree.
- **Results at `d8911ef`:** all six runs passed (132/132 tests, lint 0 errors, 5/5 emulators PASS with rotary, Signing gate 1 PASS).

## 28 Nov 2026: frame-window measurements clarified

- Shane spotted that TEST_REPORT.md said all 82 emulator windows were at High while also recording 3 step-downs, and that SKY.md said the emulators stayed on High.
- **Checked run `46091229456` window by window:**
  - 64 windows are the first 3 seconds after a launch and 18 are 10-second windows.
  - The emulator test restarts the app for every screenshot and recording, so each launch starts at High.
  - All 3 step-downs happened in a first 3-second window; the next window was always a new launch, about 14–15 seconds later.
  - So every window started at High, and no completed window ran at Standard.
- TEST_REPORT.md and SKY.md now say exactly that. The quality-control behaviour is unchanged.
- **For Stage 7:** all three step-downs came in the first seconds after launch, while the app also makes its cloud sprites and Moon picture.

## 28 Nov 2026: Stage 3 approved and started

- Shane approved the final Stage 3 proposal and decisions D1 to D10, then authorised the Stage 3 branch. His conditions:
  - keep the verified Moon, and port its tested changes without merging the verification branch;
  - provide emulator screenshots at each visual milestone, and pause for his review once the main screens work;
  - smooth scrolling and readable layouts on every round size;
  - never present missing or stale weather as current;
  - separate verified results from those awaiting a real watch;
  - version 0.1.0-dev; Android and Desktop untouched; no production signing, publishing or new permissions; no merge without his final approval.
- Claude updated the development plan: Stage 2 merged, Stage 3 in progress.
- Shane added a milestone at the end of Stage 3: a private beta APK (`.dev`, development-signed, sample data clearly labelled), with Linux Mint install and removal steps and a real-watch checklist. Claude added it to the development plan. It is not to be rushed.
- **Work so far** (branch `claude/stage-3-weather-app`):
  - the Moon changes ported, D10 live Moon, the data core, all main screens and detail screens, and the no-data screens;
  - the emulator tests now screenshot every screen and state on all five emulators and check round-screen fit, taps, bezel, touch scrolling, back, animation and the on-device Moon.
- **Problems the emulator runs found, and their fixes:**
  - The sky filled only two thirds of the screen. Fixed.
  - Words over the sky collided with the time on 432 px. They now sit in three bands sized from the screen.
  - An API 34 call would have crashed the Moon screen on Wear OS 4. Lint caught it, and it's fixed.
  - Open screens kept the data they were first drawn with, so new data or a new sample never appeared. Fixed.
  - The first Back-button press did nothing on Wear OS 4. Fixed.
  - Three test mistakes were corrected: the bezel was turned the wrong way, slow injected swipes didn't count as a swipe, and the scroll-away check counted the forecast icons' own animation. Created By Shane BlackbuRn
