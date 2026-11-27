# Signing

Created By Shane BlackbuRn

From the approved Development Plan v1.1, section 8.

## Development builds

- Signed with the **shared debug key** `keystore/rbsb-test.keystore`. It is the same file as RBSB-Weather for Android's test key (SHA-256 `a9618b32…4a36e87c`).
- This key is **public**. It is for development builds only and must never sign a release.
- Debug builds use the package `io.github.shanebl030glitch.rbsbweather.dev`, so they install beside the real app. The phone's matching `.dev` debug builds are part of the Android 0.8.0 proposal and need Shane's approval.
- CI checks every debug build is signed with this key and has the `.dev` package.

## Release builds

- Release builds come out of Gradle **unsigned**. CI fails if a release build is signed.
- The production key will sign the watch app (package `io.github.shanebl030glitch.rbsbweather`, same as the phone app, required by the Wear OS Data Layer).
- **Signing history (Option A, approved 28 Nov 2026):** the watch app carries the **same history as Android 0.7.2**: test key `a9618b32…` rotated to production key `02f6fc5f…`, with the same flags. The release will be signed with the production key plus the public lineage extracted from the published 0.7.2 APK, v3 only, so the old test key's private key is never needed.
- Old-key flags (verified from the published 0.7.2 APK): installed-data **yes**, permission **yes**, shared UID **no**, rollback **no**, auth **no**. The old key cannot roll back or replace the production-signed app.
- The app may define no custom permission except AndroidX's `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; CI enforces this.
- Production credentials will live only in a protected GitHub environment (main branch, required approval) or be used on Shane's own machine. They are never committed and never printed.
- There is no watch face and no watch-face key: this is a standalone weather app (plan v1.1).
- Nothing is signed for release in Stage 0.

## Rotation compatibility gates

1. **Gate 1 (Stage 0): automated signing identity and history verification.** Throwaway keys, same structure. On a phone emulator and a Wear OS 6 emulator (no pairing), the system, the app and the APK file must agree on the signer and history, and the two devices must match. See [SIGNING_GATE_1.md](SIGNING_GATE_1.md).
2. **Gate 2 (stage 8): live Data Layer communication on real paired devices.** release candidates signed with the real production key, checked on Shane's devices before anything is published.

## Never committed

`.gitignore` refuses `*.jks`, `*.p12`, `*.pfx`, `*.bks`, `keystore.properties`, `signing.properties`, `rbsb-release*` and `probe-keys/`.
