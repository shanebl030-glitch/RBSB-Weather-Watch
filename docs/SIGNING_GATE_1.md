# Signing gate 1: checklist (Stage 0)

Created By Shane BlackbuRn

**Question:** will Google Play services treat a key-rotated phone app and a watch app signed with the new key only as "the same app", so they can use the Wear OS Data Layer?

The real apps will be signed like this:

- RBSB-Weather Android release: rotated from the old public test key to the production key.
- RBSB watch app: the production key only.

This test copies that structure with **throwaway keys** and a **throwaway probe app** (`io.github.shanebl030glitch.signingprobe`). It never touches RBSB-Weather, Android 0.7.2 or any real key. Each run of the *Signing gate 1 kit* workflow makes new keys, signs three copies of the probe, checks them, and deletes the keys.

| File in the kit | Install on | Signed with |
|---|---|---|
| `probe-phone-rotated.apk` | Phone emulator | Key A rotated to key B (same `apksigner` options as Android 0.7.2) |
| `probe-watch-newkey-only.apk` | Watch emulator | Key B only (the approved plan) |
| `probe-watch-lineage-fallback.apk` | Watch emulator, **only if the main test fails** | Key A rotated to key B |

Expected time: 20 to 30 minutes, most of it emulator downloads the first time.

## 1. Get the kit

1. Open the repository's **Actions** tab → **Signing gate 1 kit** → the latest green run on `claude/stage-0-foundation`.
2. Download the **signing-gate-1-kit** artifact and unzip it.
3. Check the files are intact: in that folder run `sha256sum -c SHA256SUMS` (Linux). Every line should say `OK`.

## 2. Create two emulators in Android Studio

In **Device Manager → Create Virtual Device**:

1. **Phone:** Pixel 8 (any recent phone), system image **API 35, "Google Play"** (the Play Store logo must show; needed to install the Wear OS companion app).
2. **Watch:** **Wear OS Large Round**, system image **API 36 (Wear OS 6)**.

## 3. Pair them

1. Start both emulators.
2. In Device Manager, on the **phone**, open the ⋮ menu → **Pair Wearable** → choose the watch → follow the steps.
3. The phone may ask you to sign in to the Play Store and install the **Wear OS** app. A spare Google account is fine.
4. Pairing is done when the Wear OS app on the phone shows the watch as **Connected**.

## 4. Install the probe

In a terminal:

```
adb devices                     # note the two serials, e.g. emulator-5554 (phone) and emulator-5556 (watch)
adb -s <phone-serial> install probe-phone-rotated.apk
adb -s <watch-serial> install probe-watch-newkey-only.apk
```

## 5. Run the test

1. Open **Signing probe** on both the phone and the watch.
2. Check the signing lines at the top:
    - Phone: **Rotation history** shows two fingerprints (A → B).
    - Watch: **Rotation history: none**.
    - The phone's **Current signer** equals the watch's.
3. Tap **Run test** on the watch, then on the phone (both screens must stay open).
4. Both should show **GATE 1: PASS** (a reply received and a data item received).

## 6. Results

- **PASS on both:** gate 1 passes. Screenshot both screens (or copy the text) and send them to Claude.
- **GATE 1: NO CONNECTED DEVICE:** a pairing problem, not a signing one. Re-check step 3.
- **Connected, but PARTIAL or WAITING after 30 seconds:** possible signature mismatch. Run the fallback:
    1. `adb -s <watch-serial> uninstall io.github.shanebl030glitch.signingprobe`
    2. `adb -s <watch-serial> install probe-watch-lineage-fallback.apk`
    3. Repeat step 5 and send both results (main test and fallback).

## 7. Clean up (optional)

```
adb -s <phone-serial> uninstall io.github.shanebl030glitch.signingprobe
adb -s <watch-serial> uninstall io.github.shanebl030glitch.signingprobe
```

The emulators can be deleted in Device Manager.

## What this proves, and what it doesn't

- It proves Play services' "same app" check with the same rotation structure, on Google's emulator images.
- It does **not** use the real production key. Signing gate 2 (stage 8) repeats the check with the real release candidates before anything is published.
- It does not test a real Samsung phone or watch. That is covered at stage 7.
