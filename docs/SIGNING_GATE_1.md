# Signing gate 1: automated signing identity check (Stage 0)

Created By Shane BlackbuRn

**No manual steps.** Gate 1 runs automatically in the **Signing gate 1 (automated identity check)** workflow on every push and pull request. The earlier manual emulator-pairing test was retired on 28 Nov 2026: pairing needs the Wear OS companion app and a Google sign-in, and could not be made reliable. **Live Data Layer communication moves to signing gate 2** (stage 8, real devices).

## The decision it checks (Option A, approved 28 Nov 2026)

The watch app carries **the same signing history as RBSB-Weather Android 0.7.2**: the old public test key rotated to the production key, with the same capability flags. Both apps then present identical signing information to Google Play services, whichever way Play services compares signatures for the Wear OS Data Layer (Google does not document that comparison for rotated keys).

## What the workflow proves

Everything except the 0.7.2 reference uses **throwaway keys** made fresh in each run and deleted. Key A plays the old test key, key B plays the production key. **The production key is never used.**

1. **0.7.2 reference.** Downloads the published Android 0.7.2 APK (public release asset, read only), checks its SHA-256 (`66bc5d44…74611`), reads its signing history with `apksigner`, and checks the old key's flags:

    | Key in the history | Certificate SHA-256 | installed-data | permission | shared UID | rollback | auth |
    |---|---|---|---|---|---|---|
    | Old: public test key | `a9618b32…4a36e87c` | yes | yes | **no** | **no** | **no** |
    | New: production key (current signer) | `02f6fc5f…5dc7a2a60` | yes | yes | yes | no | yes |

    It also extracts that public lineage from the APK and reads it back, to show the real watch release can reuse it.

2. **Probe kit.** The throwaway probe app (`io.github.shanebl030glitch.signingprobe`, never released) is signed twice:
    - `probe-phone.apk`: A rotated to B with exactly the options in Android 0.7.2's `release.yml`.
    - `probe-watch.apk`: **key B only**, plus the lineage extracted from the phone APK. Key A's private key is deleted before this step. The lineage extractor is proved exact: its output is byte-identical to the lineage file `apksigner` created.
    - The watch copy is signed **v3 only**. Every supported watch (Wear OS 4+, API 33+) verifies only the v3 signature, which carries the history; the older v1/v2 signatures would need the old key's private key.

    The workflow checks both copies have the same current signer, the same two-key history, and old-key flags identical to 0.7.2's.

3. **Identity on real Android systems, without pairing.** Installs `probe-phone.apk` on a phone emulator (API 34) and `probe-watch.apk` on a Wear OS 6 watch emulator (API 36), separately. On **each** device it collects three independent views of the signing identity and **fails unless all three agree** on the current signer and the full history:
    - **System:** what Android records for the installed package (`dumpsys package`, which lists signatures by their `Signature.hashCode()` and gives each history entry's capability flags).
    - **App:** what the installed app sees through `PackageManager.signingInfo`. The probe logs this as JSON (full SHA-256 and the same hash codes dumpsys uses), and CI reads it from logcat.
    - **APK file:** what `apksigner` read from the APK before it was installed (full SHA-256).

4. **Phone against watch.** A final job compares the two devices: the system's current signer, the system's history with flags, and the app-reported SHA-256 values of the current signer and the history must all be identical. It prints `GATE 1 IDENTITY: PASS` only then; any difference fails the workflow.

5. **Self-test.** Before using it, the workflow proves the per-device comparison passes when everything agrees and fails on each of five kinds of disagreement (wrong current signer, wrong history, wrong SHA-256 of either, or an app that cannot read its signing info).

## What this means for the real watch release (a later stage)

The watch release will be signed with the **production key only**, plus the **public lineage extracted from the published 0.7.2 APK**, v3 only. It never needs the old test key's private key, and the history and flags are identical to 0.7.2's by construction.

## What the old key can and cannot do (same as 0.7.2)

- **Cannot** roll back or replace the production-signed app: rollback is off, so an APK signed only with the old test key cannot install over it.
- **Cannot** share a user ID with the app or keep auth access.
- **Can** hand over installed data: an app first installed with the old key could be updated to the production-signed app, keeping its data. This is how 0.6.x phone installs kept their data. On the watch there are no test-key installs of the release package (development builds use the separate `.dev` package).
- **Can** be granted the app's signature permissions. The only custom permission the app defines is AndroidX's `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (the same one 0.7.2 defines), and CI fails if any other appears. On API 33 and later, which covers every supported watch, AndroidX uses the system's own "not exported" receiver flag instead of that permission.

## Gate 1 and gate 2

- **Gate 1 (this check):** automated signing identity and history verification. It proves the phone and watch apps are signed so that Android on both devices records the same signer and history, as the app itself and the APK file confirm.
- **Gate 2 (stage 8):** live Data Layer communication between the real release-signed apps on real paired devices.

## What gate 1 does not prove

- A live Data Layer exchange between the two apps. That is **signing gate 2** (stage 8), with the real release-signed phone and watch apps on real devices.
- Anything on real Samsung hardware. **Real Galaxy Watch testing remains required** (stage 7).

## Where to find the results

Actions → **Signing gate 1 (automated identity check)** → the latest run on `claude/stage-0-foundation`. Each **Identity on …** job prints the three views and `DEVICE IDENTITY OK`; the **Same identity on phone and watch** job prints each comparison (`SAME` or `DIFFERENT`) and the final result. The `signing-gate-1-kit` artifact holds the two probe APKs, `CERTS.txt` and `SHA256SUMS` for reference.
