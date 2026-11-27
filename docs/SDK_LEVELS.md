# SDK levels

Created By Shane BlackbuRn

From the approved Development Plan v1.0, section 3.

| Setting | Watch app | Meaning |
|---|---|---|
| minSdk | 33 (Wear OS 4) | Oldest Wear OS the app installs on. The Galaxy Watch6 shipped with Wear OS 4. |
| targetSdk | 36 (Wear OS 6 / Android 16) | Which platform behaviours the app opts into. It does not change which watches are supported. |
| compileSdk | 36 | Must be at least targetSdk. |

## targetSdk is not the same as testing on Wear OS 6

- Changes for "all apps" apply on any Wear OS 6 watch whatever the app targets. Only testing on Wear OS 6 (emulators, then real watches) shows how the app behaves there.
- targetSdk only opts the app into the extra changes reserved for apps targeting that level, and only on Wear OS 6 or later. On Wear OS 4 or 5, targeting 36 changes nothing.

## Changes that apply because the app targets API 36

| Change | Relevance |
|---|---|
| Wear OS 6 always-on: the app is treated as always-on (screen dims, app stays visible) | Matches the planned still always-on sky. Ambient handling is implemented explicitly either way. |
| Predictive back on by default; `onBackPressed` not called, back key not dispatched | Must test swipe-to-dismiss and back navigation (Compose for Wear OS handles them). |
| Tile enter/leave events batched | Minor. Tile visits are not tracked. |
| No edge-to-edge opt-out; `elegantTextHeight` ignored | Minor. Checked in screenshots. |
| Granular health permissions | Not used. |
| Large-screen orientation rules; `scheduleAtFixedRate` change | Not relevant / not used. |

## Why 36 rather than 35

Google Play's minimum for Wear OS apps is API 35 (from 31 Aug 2026). API 36 adds no device support. Its practical benefits are modest: the always-on default matches the design, adopting Android 16 behaviour is cheap on new code, and it delays the next forced target increase. **Fallback:** API 35, which still meets Play's minimum and changes no supported devices.

## Version codes

Watch builds use their own range so they can never be confused with the phone app (0.7.2 is versionCode 14):

`versionCode = 1_000_000 + major × 10_000 + minor × 100 + patch` → 0.1.0 is **1000100**.

Debug builds use the package `io.github.shanebl030glitch.rbsbweather.dev` so they install beside a release.
