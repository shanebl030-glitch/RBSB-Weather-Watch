# Sync format v1

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

What RBSB-Weather for Android sends the watch over the Wear OS Data Layer, and how the watch checks it. Stage 1 of Development Plan v1.1 defines it; nothing sends or receives it yet:

- The watch's receiver is Stage 3.
- The phone side is part of the Android 0.8.0 proposal (Stage 6), which needs Shane's approval before any phone code.

| Where | What |
|---|---|
| `sync/src/main/` | The shared module: plain Kotlin, **no Android dependencies**. The phone app can take an exact copy. |
| `sync/contract/*.schema.json` | Language-neutral definition (JSON Schema 2020-12) |
| `sync/samples/` | 74 sample files and `expected.json` (the result each must give) |
| `scripts/check-samples.py` | Independent Python check of the samples |

## Channels

| Path or name | Type | Direction | Payload |
|---|---|---|---|
| `/rbsb/v1/snapshot` | Data item | Phone → watch | Weather snapshot |
| `/rbsb/v1/settings` | Data item | Phone → watch | Six display settings |
| `/rbsb/v1/refresh` | Message | Watch → phone | "Please fetch now" |
| `rbsb_phone`, `rbsb_watch` | Capability | Both | The other app is installed |
| `rbsb_phone_v1`, `rbsb_watch_v1` | Capability | Both | It speaks protocol version 1 |

Any path not starting with `/rbsb/` is ignored.

## Encoding

- **Format:** UTF-8 JSON, gzip-compressed. Every payload starts with `"kind"` and `"schema": 1`.
- **Size limits:**
  - At most 100,000 bytes compressed (the Data Layer limit is about 100 KB).
  - At most 262,144 bytes once decompressed, so a "zip bomb" is stopped.
  - The largest possible snapshot is about 10 KB compressed, under the plan's 20 KB target.
- **Units:** values are in the phone app's metric base units: °C, km/h, mm, hPa and %. The watch converts them for display using the settings item.
- **Times:**
  - Forecast times are **local times at the place** (`2026-11-28T14:00`, optional `:ss`), as the phone app keeps them.
  - Every other time is **unix milliseconds** (UTC).
- **Missing values:** for optional values, absent and `null` mean the same. A missing weather value is null, never invented.
- **Unknown fields** are ignored, so later additions don't break older watches.
- **Duplicate keys** are not allowed.

## Snapshot (`kind: "rbsb.snapshot"`)

**Top level**

| Field | Required | Notes |
|---|---|---|
| `provider` | yes | e.g. `Open-Meteo`. 1 to 40 characters. |
| `fetchedAt` | yes | When the phone fetched the weather. Data age is measured from this. |
| `sentAt` | yes | When sent. Never earlier than `fetchedAt`. |
| `sequence` | yes | Counts up from 1 for each snapshot one phone install publishes. |
| `sourceId` | yes | Random per phone install, 8 to 64 letters, digits or `-` |
| `place` | yes | See below |
| `current` | yes | See below |
| `hourly` | yes | Up to 48 hours from the current hour, in order |
| `daily` | yes | Up to 10 days from today, in order |
| `warnings` | yes | Up to 20, see below |
| `sun` | yes | `rise`, `set`: today's times, or null |
| `moon` | yes | See below |

**`place`**
- Required:
  - `name` (1–100 characters);
  - `latitude` and `longitude`, **rounded to 3 decimals** (about 100 m);
  - `timezone` (IANA name);
  - `utcOffsetSeconds` (±64,800).
- Optional: `country`, `admin1`.

**`current`**
- Required: `time` and `isDay`.
- Optional: `temperature`, `apparentTemperature`, `humidity`, `pressure` (sea level), `cloudCover`, `precipitation`, `weatherCode` (WMO, 0–99) and `wind` (`speed`, `gusts`, `direction` = where it blows from).

**`hourly[]`**
- Required: `time`.
- Optional: `temperature`, `precipitationProbability`, `precipitation`, `weatherCode` and **`wind`**.
- **Hourly wind is optional in v1** (decision 3). RBSB-Weather for Android 0.7.2 doesn't fetch it. When an hour has no wind, the watch uses the current wind (`Snapshot.windAt`).

**`daily[]`**
- Required: `date`.
- Optional: `weatherCode`, `tempMax`, `tempMin`, `precipitationSum`, `precipitationProbabilityMax`, `windSpeedMax`, `gustMax`, `uvIndexMax`, `sunrise` and `sunset` (local times, or null in polar day or night).

**`warnings[]`**
- What the phone **currently shows after its own filters**: official warnings and the user's own forecast limits, but **not muted ones** (decision 6).
- Required:
  - `id` (≤200 characters);
  - `level`: `red`, `orange`, `yellow`, `watch`, `minor` or `limit`;
  - `title` (≤120 characters, as the phone shows it);
  - `official` (false for the user's own limits).
- Optional: `area` (≤200), `start` and `end` (ms), `summary` (≤500), `source` (≤60), `hazard` (≤32).

**`moon`** (plan v1.1 section 5, kept as specified)
- Required:
  - `at`: when the values below were calculated;
  - `phase`: elongation, 0 = new, 180 = full;
  - `phaseName`;
  - `illumination` (0–1);
  - `brightLimb` (0–360°);
  - `tilt` (−180 to 180°, the angle to turn a north-up Moon picture to match the sky).
- Optional: `rise`, `set`, `alwaysUp`, `alwaysDown`.

### Value ranges

| Values | Range |
|---|---|
| temperature, tempMax, tempMin | −90 to 60 °C |
| apparentTemperature | −100 to 80 °C |
| humidity, cloudCover, precipitation probabilities | 0 to 100 % |
| pressure | 850 to 1100 hPa |
| precipitation (current, hourly) | 0 to 500 mm |
| precipitationSum | 0 to 2000 mm |
| wind speed, windSpeedMax | 0 to 500 km/h |
| gusts, gustMax | 0 to 600 km/h |
| direction | 0 to 360° |
| uvIndexMax | 0 to 25 |
| weatherCode | whole number, 0 to 99 |
| timestamps | 2020-01-01 to 2100-01-01 |

Whole-number fields also accept `12.0`.

### Rules beyond the schema

Both checkers enforce these:
- coordinates have at most 3 decimals;
- hours and days are in order with no repeats;
- `sentAt` is not before `fetchedAt`;
- a warning's `end` is not before its `start`;
- `tempMax` is not below `tempMin`;
- the Moon is not both always up and always down;
- the future-date tolerance (below).

## Settings (`kind: "rbsb.settings"`)

All fields are required: `sentAt` plus the six approved settings (decision 5).

| Field | Values |
|---|---|
| `temperatureUnit` | `C`, `F` |
| `windUnit` | `KMH`, `MS`, `MPH`, `KN` |
| `precipitationUnit` | `MM`, `IN` |
| `clock` | `H24`, `H12` |
| `animations` | true or false |
| `minWarningLevel` | `all`, `watch`, `orange`, `red` (the phone app's own choices) |

## Refresh request (`kind: "rbsb.refresh"`)

`sentAt` (required) and `haveFetchedAt`, the fetchedAt of the snapshot the watch already has (optional). When it is sent and how often is Stage 3: when the app opens with data over 45 minutes old, at most once every 10 minutes.

## Which snapshot wins (decision 4)

`Ordering.compare(incoming, cached)` applies these rules in order:
1. Nothing cached: the incoming snapshot is **newer**.
2. A later `fetchedAt` is **newer**; an earlier one is **older**. Newer weather always wins, whatever the sequence.
3. Same `fetchedAt` but a different `sourceId` is **newer**. The phone app was reinstalled and is re-sending the same weather; the new install wins.
4. Same `fetchedAt` and `sourceId`:
   - a higher `sequence` is **newer** (for example, the warnings changed without a new fetch);
   - the same sequence is a **duplicate**;
   - a lower one is **older**.

**Phone reinstalls:** the sequence restarts at 1, but that never causes a wrong decision. A reinstalled phone's fresh weather wins by rule 2, and a re-send of its cached weather wins by rule 3.

**Future-dated snapshots:** a snapshot whose `fetchedAt` or `sentAt` is more than **10 minutes** ahead of the watch's clock is rejected as `FUTURE_DATED`. Settings and refresh requests use the same rule on `sentAt`.
- Phone and watch both take their time from the network, so they normally agree within seconds.
- Exactly 10 minutes ahead is accepted; one millisecond more is rejected.
- Because such a snapshot is rejected on arrival, it never becomes the cached copy, so it can't block later snapshots.
- Old snapshots aren't rejected for their age. The watch shows them as stale (Stage 3), and ordering decides whether they are used.

## Rejection reasons

Decoding never throws. A bad payload returns `Decoded.Rejected(reason, path, detail)`, and the watch keeps its cached copy.

| Reason | Meaning |
|---|---|
| `TOO_LARGE` | Over the compressed or decompressed size limit |
| `CORRUPT` | Not valid gzip or UTF-8 |
| `BAD_JSON` | Not exactly one JSON object |
| `WRONG_KIND` | A different payload type |
| `UNSUPPORTED_SCHEMA` | Not schema 1 |
| `MISSING_FIELD` | A required field is absent |
| `WRONG_TYPE` | Wrong JSON type, or null where a value is required |
| `OUT_OF_RANGE` | A value outside its range |
| `BAD_LENGTH` | Text too long, or empty where text is required |
| `TOO_MANY` | Too many hours, days or warnings |
| `BAD_FORMAT` | A time, date, id or time zone in the wrong format |
| `BAD_VALUE` | A name outside its list |
| `UNROUNDED_COORDINATES` | Coordinates with more than 3 decimals |
| `INCONSISTENT` | Values that contradict each other |
| `FUTURE_DATED` | Too far ahead of this device's clock |
| `UNEXPECTED` | Should never happen; reported instead of crashing |

On the phone side, `SnapshotCodec.encode()` refuses (with `IllegalArgumentException`) anything the watch would reject, so a phone bug is caught before sending.

## How it is tested

Everything here runs in CI. The tests are in `sync/src/test/` and run with `:sync:test`.

1. **Samples:** every valid sample decodes and survives JSON and compressed round trips. Every invalid sample is rejected with its expected reason. Every file on disk is listed in `expected.json`, and every listed file exists.
2. **Independent check:** `scripts/check-samples.py` checks the same samples against the JSON Schemas plus the extra rules, written separately from the Kotlin. Both must match `expected.json`, so they agree on every sample.
3. **Schema matches code:** a test walks every field of each schema and checks that the Kotlin decoder applies the same rules: required and optional fields, both ends of every range, every length and list limit, every allowed name. More than 250 checks for the snapshot.
4. **Safety:**
   - over-size payloads;
   - a 5 MB zip bomb;
   - truncated, random and non-UTF-8 bytes;
   - JSON that isn't one object;
   - 200,000-deep nesting.
   All are rejected, never a crash.
5. **Compatibility and wind fallback:** unknown fields at every level are ignored; absent and null values are accepted; hours without wind use the current wind.
6. **Ordering:** every rule above, including reinstalls and the exact 10-minute boundary.
7. **0.7.2 mapping:** test-only copies of RBSB-Weather for Android 0.7.2's data classes (tag `v0.7.2`, commit `cdf422d`; the Android repo was only read) are mapped, encoded, sent and decoded unchanged. This covers 168 hours trimmed to 48, unrounded coordinates, missing values, long warnings shortened, and the user's own limits. A forecast with 0.7.2's default `fetchedAt = 0` is refused.
8. **Samples up to date:** CI regenerates them with `make_samples.py` and fails if anything changes.

## Not covered yet

- The decoder is tested on the JVM with the Maven copy of `org.json`. The watch app uses Android's built-in `org.json`, which behaves the same for everything this format uses, but that is only verified once the Stage 3 receiver runs on emulators.
- Nothing is sent over a real Data Layer link yet. That is Stage 3 (watch) and Stage 8 (live, real paired devices, signing gate 2).
- The Moon values in the samples are plausible, not calculated for those dates.
