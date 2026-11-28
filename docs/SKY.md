# The animated sky (Stage 2)

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

The sky is the app's first screen. It draws the full live sky on the watch from a weather snapshot: wind-driven cloud layers, rain and splashes, drizzle, snow, fog, lightning, the Sun's arc, the Moon in its real phase and tilt, stars, and day/night fades. Until Stage 3 connects the phone, it shows the Stage 1 sample data, always labelled **SAMPLE DATA**.

## Where the code comes from

The scene code is copied from RBSB-Weather for Android v0.7.2 (commit `cdf422d`), so the phone and watch draw the same sky for the same weather. Each copied file says where it came from and what the watch changed. The Android repository is unchanged.

| Watch file | From the phone app | Watch changes |
|---|---|---|
| `scene/SceneEngine.kt` | `ui/scene/SceneEngine.kt` | Quality levels scale the numbers of clouds, drops, flakes, stars and splashes |
| `scene/SceneDraw.kt` | `ui/scene/SceneDraw.kt` | A layer under the Sun and Moon (for the readability shade); one cloud sprite set in memory (40, was 96); half-resolution clouds at Light |
| `scene/SceneModel.kt` | `ui/scene/SceneModel.kt` | Reads the Stage 1 `Snapshot`; Moon as in decision 1 (below); no forecast playback |
| `scene/MoonRender.kt` | `ui/scene/MoonRender.kt` | 512 px textures; androidx.core KTX bitmap calls (same behaviour) |
| `scene/SafeBrush.kt`, `data/Moon.kt`, `data/WeatherCodes.kt` | same names | Package name only |
| `scene/Quality.kt`, `sky/*` | new | Quality levels, frame timing, the sky screen, sample data, rotary input |

## The screen

The screen shows the place, a large temperature, the conditions and today's high and low over the sky.

- **Readability:** a soft dark centre sits under the Sun, Moon and clouds, so it never dulls the Sun.
- **Sample label:** the yellow SAMPLE DATA label is at the bottom, with the sample's number and name. Names are shortened on screens under 230 dp wide, such as 432 px watches.
- **Switching samples:** tap the screen, or turn the bezel or crown. Rotary events reach the screen through Compose's rotary handling, and the activity passes on any the screen didn't take.
- **About:** swipe sideways to the About page.
- **The sample's clock:** each sample's sky starts at the sample's own moment and runs on in real time, so a night sample shows night.
- **Cross-fades:** switching samples cross-fades. The screen opens directly on the sample's own sky, without fading from daytime blue.

## The Moon (approved decision 1, option C)

- **Phase and illumination** come from the snapshot. The phase angle the renderer needs is worked out from the lit fraction.
- **Position and tilt** are calculated live on the watch with the copied Moon code: up or down, altitude, place on its arc, the parallactic angle and the bright-limb direction. So the sky stays right as the data ages.
- **Rise and set** come from the snapshot when they span the moment shown; otherwise they're calculated.
- **The Moon texture** is NASA's (512 px). The credit is on the About page: *Moon imagery: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO LROC WAC and LOLA data)*.

**Checked:** in the clear-night sample (23:00 in Auckland) the Moon is 13° below the horizon, so none is drawn. In the fog sample it is 14° up, and in the polar-night sample 9° up. The desktop app's `moon.py`, which the watch's Moon code ports line by line, gives the same results, and a test keeps them.

## Animation and quality levels (decision 3)

The sky animates only while its page is on screen and the app is in front. It stops in the background and when the watch dims; the always-on still sky is Stage 4. It aims for 30 fps and paces itself to every other screen refresh.

**Detail levels.** Detail changes; effects never do. Every effect runs at every level, with at least 12 drops, flakes or stars wherever they appear.

| Level | Clouds | Drops, flakes, stars | Cloud sprites | Splashes at once |
|---|---|---|---|---|
| High (the phone's detail) | 100% | 100% | full resolution | 60 |
| Standard | 75% | 65% | full resolution | 36 |
| Light | 60% | 40% | half resolution | 20 |

**How the level is chosen** (`QualityController`). The starting thresholds are adjustable in `QualityConfig` and are to be tuned at Stage 7.

- **Measuring:** the first window is 3 seconds, then every 10 seconds.
- **Slow frames:** a frame is slow when its work (moving and drawing) takes over 33 ms, or when it arrives more than 1.5 frame-times late.
- **Step down** one level when more than 10% of a window's frames are slow.
- **Step up** one level only after 30 seconds in a row of windows whose 90th-percentile work is under 20 ms. A slower window restarts the wait, so the level doesn't flip back and forth.
- **Power-saving mode:** one level lower and at most 20 fps.
- **Never by device:** the level comes from measurement, never from the chip or model.

**Recorded measurements, for Stage 7.** Every window is recorded:
- written to logcat with the tag `RBSBSky` (`SKY FRAME WINDOW …`);
- saved in the app's private storage as `sky-frame-timing.csv` (never more than the last 500 windows: once it is full, each new window replaces the oldest; excluded from backup like all app data);
- the latest shown on the About page, for example *Sky: High · 30.0 fps · work p90 1.2 ms · slow 0%*.

The columns are: start time, seconds, frames, fps, work p50/p90/max in ms, slow share, level, power saving, decision, and the next level.

**On the CI emulators** the sky runs at about 30 fps with work around 1–8 ms per frame, and stays on High. Emulators use the computer's graphics, so this says nothing about real watches. The real numbers come at Stage 7.

`dumpsys gfxinfo` reports most frames as "janky" on the emulators. That's expected: it judges every frame against a 60 Hz deadline, and the sky deliberately draws at 30 fps. The app's own measurements are the ones to use.

## Memory

- **Cloud sprites:** one set is kept (the cache holds at most 40: about 18 clouds at 498 px, plus a set fading out).
- **Moon textures:** 512 px, about 147 KB in the APK; the phone app ships 2048 px, 1.8 MB.
- **Particles:** particle and splash counts are fixed for a scene, and a test runs 10 simulated minutes at every level to confirm they don't grow.

## Tests

**Unit tests (JVM):**
- **Ported from the phone app:** the scene tests (desktop `test_scene.py` checks), the Moon tests (against PyEphem reference values) and the safe-drawing test.
- **New:**
  - quality levels and their thresholds;
  - the frame-timing file;
  - the conditions each sample gives;
  - the Moon below or above the horizon;
  - sample loading, and the text over the sky.

**Render tests (Robolectric 4.18.1, SDK 36, test-only):**
- every weather code, day and night, at every quality level;
- day and night;
- the Sun and Moon arcs;
- the NASA Moon picture from the 512 px textures;
- the Sun staying bright under the readability shade;
- the screen opening without a fade from day;
- the one-set sprite cache;
- pictures of 10 samples at 432, 480 and 498 px, saved to `wear/build/screens`.

Robolectric needs Java 21 to run SDK 36, so CI runs the wear unit tests on Java 21. The app is still compiled for Java 17.

**Emulator tests (5 configurations):**
- the sample label;
- tap and rotary switching;
- 10 sample screenshots;
- the About credit;
- frames drawn while shown and stopped in the background;
- frame-timing windows;
- 6-second recordings of three samples.
