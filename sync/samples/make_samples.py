#!/usr/bin/env python3
"""Writes the sync format v1 sample files and expected.json.

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

The samples are committed; this script only exists so they can be regenerated consistently.
Run from anywhere:  python3 sync/samples/make_samples.py

Valid samples cover every sky type the phone's scene code knows (clear, partly cloudy, overcast,
fog, drizzle, rain, snow, thunder), day and night, both hemispheres, polar night, a minimal and a
largest snapshot. Each invalid sample has exactly ONE defect and the reason both checkers
(the Kotlin decoder and scripts/check-samples.py) must report.
Values are realistic but illustrative; the Moon values are plausible, not astronomically exact.
"""
import copy
import random
import json
import pathlib
from datetime import datetime, timedelta, timezone

HERE = pathlib.Path(__file__).resolve().parent
TOL_MS = 10 * 60_000          # Limits.FUTURE_TOLERANCE_MS


def ms(dt):
    return int(dt.timestamp() * 1000)


def local_str(dt):
    return dt.strftime("%Y-%m-%dT%H:%M")


PLACES = {
    "auckland": dict(name="Auckland", latitude=-36.848, longitude=174.763, timezone="Pacific/Auckland",
                     utcOffsetSeconds=46800, country="New Zealand", admin1="Auckland"),
    "wellington": dict(name="Wellington", latitude=-41.289, longitude=174.777, timezone="Pacific/Auckland",
                       utcOffsetSeconds=46800, country="New Zealand", admin1="Wellington"),
    "brisbane": dict(name="Brisbane", latitude=-27.468, longitude=153.028, timezone="Australia/Brisbane",
                     utcOffsetSeconds=36000, country="Australia", admin1="Queensland"),
    "oslo": dict(name="Oslo", latitude=59.913, longitude=10.739, timezone="Europe/Oslo",
                 utcOffsetSeconds=3600, country="Norway", admin1="Oslo"),
    "tromso": dict(name="Tromsø", latitude=69.649, longitude=18.956, timezone="Europe/Oslo",
                   utcOffsetSeconds=3600, country="Norway", admin1="Troms"),
}


def snapshot(place="auckland", local_start=datetime(2026, 11, 28, 10, 0), code=0, is_day=True,
             temp=19.0, hours=48, days=7, hourly_wind=False, warnings=(), seq=12,
             sun=True, moon_up=None, cloud=10.0, precip=0.0):
    p = copy.deepcopy(PLACES[place])
    off = timedelta(seconds=p["utcOffsetSeconds"])
    fetched_utc = (local_start - off).replace(tzinfo=timezone.utc)
    fetched = ms(fetched_utc)
    hourly = []
    for i in range(hours):
        t = local_start + timedelta(hours=i)
        h = dict(time=local_str(t), temperature=round(temp + 3 * ((i % 24) - 12) / 12, 1),
                 precipitationProbability=float(min(100, precip * 20)), precipitation=precip,
                 weatherCode=code)
        if hourly_wind:
            h["wind"] = dict(speed=float(12 + i % 10), gusts=float(20 + i % 15), direction=float((200 + i * 5) % 360))
        hourly.append(h)
    daily = []
    for d in range(days):
        day = local_start.date() + timedelta(days=d)
        entry = dict(date=day.isoformat(), weatherCode=code, tempMax=temp + 4, tempMin=temp - 6,
                     precipitationSum=precip * 6, precipitationProbabilityMax=float(min(100, precip * 25)),
                     windSpeedMax=28.0, gustMax=45.0, uvIndexMax=9.5 if is_day else 0.0)
        if sun:
            entry["sunrise"] = f"{day.isoformat()}T05:58"
            entry["sunset"] = f"{day.isoformat()}T20:21"
        daily.append(entry)
    s = {
        "kind": "rbsb.snapshot",
        "schema": 1,
        "provider": "Open-Meteo",
        "fetchedAt": fetched,
        "sentAt": fetched + 30_000,
        "sequence": seq,
        "sourceId": "3f2b9c1e-6d4a-4b7e-9a51-0c8d2e7f4a10",
        "place": p,
        "current": dict(time=local_str(local_start), isDay=is_day, temperature=temp,
                        apparentTemperature=temp - 1.0, humidity=72.0, pressure=1016.4, cloudCover=cloud,
                        precipitation=precip, weatherCode=code,
                        wind=dict(speed=14.0, gusts=26.0, direction=225.0)),
        "hourly": hourly,
        "daily": daily,
        "warnings": list(warnings),
        "sun": dict(rise=fetched - 4 * 3600_000, set=fetched + 10 * 3600_000) if sun else {},
        "moon": dict(at=fetched, phase=152.0, phaseName="Waxing Gibbous", illumination=0.94,
                     brightLimb=268.5, tilt=-31.2, rise=fetched + 6 * 3600_000, set=fetched - 5 * 3600_000,
                     alwaysUp=False, alwaysDown=False),
    }
    if moon_up is not None:
        s["moon"].update(alwaysUp=moon_up, alwaysDown=not moon_up)
        s["moon"].pop("rise")
        s["moon"].pop("set")
    return s


def warning(fetched, wid, level, title, official=True, hours=12, summary="", area="", source="MetService", hazard="rain"):
    return dict(id=wid, level=level, title=title, official=official, area=area,
                start=fetched - 3600_000, end=fetched + hours * 3600_000, summary=summary,
                source=source if official else "RBSB-Weather", hazard=hazard)


def build():
    valid = {}
    valid["clear-day"] = snapshot()
    valid["clear-night"] = snapshot(local_start=datetime(2026, 11, 28, 23, 0), is_day=False, temp=14.0, cloud=0.0)
    valid["partly-cloudy-day"] = snapshot(code=2, cloud=45.0)
    valid["overcast"] = snapshot(code=3, cloud=100.0, temp=16.5)
    valid["fog"] = snapshot(place="wellington", local_start=datetime(2026, 11, 28, 7, 0), code=45, cloud=100.0, temp=11.0)
    valid["drizzle"] = snapshot(place="wellington", code=53, cloud=95.0, precip=0.4, temp=13.0)
    rain = snapshot(local_start=datetime(2026, 11, 28, 22, 0), code=65, is_day=False, cloud=100.0, precip=6.5, temp=15.0)
    f = rain["fetchedAt"]
    rain["warnings"] = [
        warning(f, "metservice-2026-11-28-heavy-rain-auckland", "orange", "Orange Heavy Rain Warning",
                summary="Expect 80 to 110 mm of rain about the ranges, with peak rates of 20 to 30 mm/h.",
                area="Auckland and Great Barrier Island"),
        warning(f, "metservice-2026-11-28-wind-auckland", "yellow", "Strong Wind Watch", hours=8,
                summary="Southwest winds may approach severe gale in exposed places.", area="Auckland", hazard="wind"),
        warning(f, "limit-rain-50mm", "limit", "My limit: rain over 50 mm", official=False,
                summary="Forecast rain today: 64 mm.", area="Auckland"),
    ]
    valid["rain-night-warnings"] = rain
    valid["snow-northern"] = snapshot(place="oslo", local_start=datetime(2026, 11, 28, 13, 0), code=73, cloud=100.0,
                                      precip=1.2, temp=-3.5)
    thunder = snapshot(place="brisbane", local_start=datetime(2026, 11, 28, 16, 0), code=95, cloud=90.0, precip=12.0, temp=29.0)
    thunder["warnings"] = [warning(thunder["fetchedAt"], "bom-qld-severe-thunderstorm-1", "red",
                                   "Red Severe Thunderstorm Warning", hours=3, source="Bureau of Meteorology",
                                   summary="Very dangerous thunderstorms with giant hail and destructive winds.",
                                   area="Brisbane", hazard="thunderstorm")]
    valid["thunder-warning"] = thunder
    valid["polar-night"] = snapshot(place="tromso", local_start=datetime(2026, 11, 28, 12, 0), code=71, is_day=False,
                                    sun=False, moon_up=True, cloud=80.0, precip=0.3, temp=-6.0)
    valid["hourly-wind-mixed"] = snapshot(code=2, hourly_wind=True)
    for i, h in enumerate(valid["hourly-wind-mixed"]["hourly"]):
        if i % 3 == 1:
            h.pop("wind")                          # absent: falls back to current wind
        elif i % 3 == 2:
            h["wind"] = None                       # null: same
    nulls = snapshot(code=None)
    for k in ["temperature", "apparentTemperature", "humidity", "pressure", "cloudCover", "precipitation", "weatherCode"]:
        nulls["current"][k] = None
    nulls["current"]["wind"] = None
    for h in nulls["hourly"]:
        for k in ["temperature", "precipitationProbability", "precipitation", "weatherCode"]:
            h[k] = None
    for d in nulls["daily"]:
        for k in ["weatherCode", "tempMax", "tempMin", "precipitationSum", "precipitationProbabilityMax",
                  "windSpeedMax", "gustMax", "uvIndexMax", "sunrise", "sunset"]:
            d[k] = None
    nulls["sun"] = dict(rise=None, set=None)
    nulls["moon"]["rise"] = None
    nulls["moon"]["set"] = None
    valid["explicit-nulls"] = nulls

    base = snapshot()
    minimal = {k: base[k] for k in ["kind", "schema", "provider", "fetchedAt", "sentAt", "sequence", "sourceId"]}
    minimal["place"] = {k: base["place"][k] for k in ["name", "latitude", "longitude", "timezone", "utcOffsetSeconds"]}
    minimal["current"] = dict(time=base["current"]["time"], isDay=True)
    minimal["hourly"] = []
    minimal["daily"] = []
    minimal["warnings"] = []
    minimal["sun"] = {}
    minimal["moon"] = {k: base["moon"][k] for k in ["at", "phase", "phaseName", "illumination", "brightLimb", "tilt"]}
    valid["minimal"] = minimal

    largest = snapshot(code=63, hours=48, days=10, hourly_wind=True, precip=2.5, cloud=100.0)
    # Varied text at every maximum length, so compression has no easy repetition to exploit.
    rnd = random.Random(20261128)

    def text(n):
        words = ["rain", "heavy", "wind", "gusts", "coast", "ranges", "expect", "flooding", "slips", "travel",
                 "conditions", "possible", "northwest", "severe", "gale", "exposed", "places", "thunderstorms",
                 "hail", "localised", "downpours", "surface", "flooding", "roads", "rivers", "rising", "quickly"]
        out = ""
        while len(out) < n:
            out += rnd.choice(words) + rnd.choice([" ", " ", " ", ", ", ". "]) + (str(rnd.randint(10, 99)) + " " if rnd.random() < 0.15 else "")
        return out[:n - 1].strip().ljust(n - 1, "x") + "."

    largest["place"].update(name=text(100), country=text(100), admin1=text(100))
    fl = largest["fetchedAt"]
    largest["warnings"] = [
        warning(fl, f"{i:03d}-" + "".join(rnd.choice("abcdefghijklmnopqrstuvwxyz0123456789-") for _ in range(196)),
                ["red", "orange", "yellow", "watch", "minor", "limit"][i % 6],
                text(120), official=(i % 6 != 5), summary=text(500), area=text(200),
                source=text(60), hazard=text(32))
        for i in range(20)
    ]
    for h in largest["hourly"]:
        h["temperature"] = round(rnd.uniform(8, 24), 1)
        h["precipitation"] = round(rnd.uniform(0, 9), 1)
        h["precipitationProbability"] = float(rnd.randint(0, 100))
        h["wind"] = dict(speed=round(rnd.uniform(5, 60), 1), gusts=round(rnd.uniform(60, 110), 1),
                         direction=float(rnd.randint(0, 359)))
    valid["largest"] = largest

    seconds = snapshot()
    seconds["current"]["time"] += ":00"
    for h in seconds["hourly"]:
        h["time"] += ":00"
    valid["times-with-seconds"] = seconds

    utc = snapshot()
    utc["place"]["timezone"] = "UTC"
    utc["place"]["utcOffsetSeconds"] = 0
    valid["utc-timezone"] = utc

    unknown = snapshot()
    unknown["futureFeature"] = {"anything": [1, 2, 3]}
    unknown["place"]["elevation"] = 196
    unknown["current"]["visibility"] = 24000
    unknown["hourly"][0]["dewPoint"] = 11.2
    unknown["daily"][0]["daylightSeconds"] = 51900
    unknown["moon"]["distanceKm"] = 384400
    unknown["sun"]["solarNoon"] = unknown["fetchedAt"]
    valid["unknown-fields"] = unknown

    now = max(s["sentAt"] for s in valid.values()) + 5 * 60_000

    soon = snapshot()
    soon["fetchedAt"] = now + TOL_MS
    soon["sentAt"] = now + TOL_MS
    soon["moon"]["at"] = soon["fetchedAt"]
    valid["future-within-tolerance"] = soon

    # ------------------------------------------------------------------ invalid: one defect each
    invalid = {}

    def bad(name, reason, mutate):
        s = snapshot()
        mutate(s)
        invalid[name] = (s, reason)

    bad("wrong-kind", "WRONG_KIND", lambda s: s.update(kind="rbsb.settings"))
    bad("schema-2", "UNSUPPORTED_SCHEMA", lambda s: s.update(schema=2))
    bad("missing-fetchedAt", "MISSING_FIELD", lambda s: s.pop("fetchedAt"))
    bad("missing-place-name", "MISSING_FIELD", lambda s: s["place"].pop("name"))
    bad("missing-moon", "MISSING_FIELD", lambda s: s.pop("moon"))
    bad("null-provider", "WRONG_TYPE", lambda s: s.update(provider=None))
    bad("temperature-as-text", "WRONG_TYPE", lambda s: s["current"].update(temperature="19"))
    bad("hourly-not-a-list", "WRONG_TYPE", lambda s: s.update(hourly={}))
    bad("fractional-weather-code", "WRONG_TYPE", lambda s: s["current"].update(weatherCode=61.5))
    bad("is-day-as-text", "WRONG_TYPE", lambda s: s["current"].update(isDay="true"))
    bad("unrounded-coordinates", "UNROUNDED_COORDINATES", lambda s: s["place"].update(latitude=-36.8485))
    bad("latitude-out-of-range", "OUT_OF_RANGE", lambda s: s["place"].update(latitude=91.0))
    bad("temperature-out-of-range", "OUT_OF_RANGE", lambda s: s["current"].update(temperature=61.0))
    bad("humidity-out-of-range", "OUT_OF_RANGE", lambda s: s["current"].update(humidity=101.0))
    bad("pressure-out-of-range", "OUT_OF_RANGE", lambda s: s["current"].update(pressure=800.0))
    bad("weather-code-out-of-range", "OUT_OF_RANGE", lambda s: s["hourly"][5].update(weatherCode=100))
    bad("wind-direction-out-of-range", "OUT_OF_RANGE", lambda s: s["current"]["wind"].update(direction=361.0))
    bad("sequence-zero", "OUT_OF_RANGE", lambda s: s.update(sequence=0))
    bad("fetched-before-2020", "OUT_OF_RANGE", lambda s: s.update(fetchedAt=1_500_000_000_000, sentAt=1_500_000_030_000))
    bad("moon-illumination-out-of-range", "OUT_OF_RANGE", lambda s: s["moon"].update(illumination=1.2))
    bad("bad-time-format", "BAD_FORMAT", lambda s: s["hourly"][3].update(time="2026-11-28 13:00"))
    bad("impossible-date", "BAD_FORMAT", lambda s: s["daily"][1].update(date="2026-02-30"))
    bad("bad-source-id", "BAD_FORMAT", lambda s: s.update(sourceId="abc"))
    bad("bad-timezone", "BAD_FORMAT", lambda s: s["place"].update(timezone="Pacific/Auck land"))
    bad("unknown-timezone", "BAD_VALUE", lambda s: s["place"].update(timezone="Not/ARealZone"))
    # Android 0.7.2 can hold "auto" (My location with MET Norway); it is not an IANA name.
    bad("auto-timezone", "BAD_VALUE", lambda s: s["place"].update(timezone="auto"))
    bad("too-many-hours", "TOO_MANY", lambda s: s["hourly"].append(
        dict(s["hourly"][-1], time=local_str(datetime(2026, 11, 30, 10, 0)))))
    bad("too-many-days", "TOO_MANY", lambda s: s["daily"].extend(
        [dict(s["daily"][-1], date=(datetime(2026, 12, 5) + timedelta(days=i)).date().isoformat()) for i in range(4)]))
    bad("too-many-warnings", "TOO_MANY", lambda s: s.update(warnings=[
        warning(s["fetchedAt"], f"w{i}", "minor", "Minor warning") for i in range(21)]))
    bad("bad-warning-level", "BAD_VALUE", lambda s: s.update(warnings=[
        warning(s["fetchedAt"], "w1", "purple", "Purple warning")]))
    bad("empty-place-name", "BAD_LENGTH", lambda s: s["place"].update(name=""))
    bad("place-name-too-long", "BAD_LENGTH", lambda s: s["place"].update(name="N" * 101))
    bad("warning-title-too-long", "BAD_LENGTH", lambda s: s.update(warnings=[
        warning(s["fetchedAt"], "w1", "orange", "T" * 121)]))
    bad("sent-before-fetched", "INCONSISTENT", lambda s: s.update(sentAt=s["fetchedAt"] - 1))
    bad("hours-out-of-order", "INCONSISTENT", lambda s: s["hourly"].__setitem__(
        slice(2, 4), [s["hourly"][3], s["hourly"][2]]))
    bad("days-repeated", "INCONSISTENT", lambda s: s["daily"][2].update(date=s["daily"][1]["date"]))
    bad("temp-max-below-min", "INCONSISTENT", lambda s: s["daily"][0].update(tempMax=5.0, tempMin=10.0))
    bad("warning-ends-before-start", "INCONSISTENT", lambda s: s.update(warnings=[
        dict(warning(s["fetchedAt"], "w1", "orange", "Orange Wind Warning"), end=s["fetchedAt"] - 7200_000)]))
    bad("moon-always-up-and-down", "INCONSISTENT", lambda s: s["moon"].update(alwaysUp=True, alwaysDown=True))
    bad("future-dated", "FUTURE_DATED", lambda s: s.update(fetchedAt=now + TOL_MS + 1000, sentAt=now + TOL_MS + 1000))
    bad("sent-in-future", "FUTURE_DATED", lambda s: s.update(sentAt=now + TOL_MS + 1))

    raw_invalid = {
        "truncated-json.txt": ('{"kind": "rbsb.snapshot", "schema": 1, "provider": "Open-Me', "BAD_JSON"),
        "not-an-object.txt": ('[1, 2, 3]', "BAD_JSON"),
        "text-after-object.txt": ('{"kind": "rbsb.snapshot"} trailing', "BAD_JSON"),
    }

    # ------------------------------------------------------------------ settings and refresh
    settings_valid = {
        "default": dict(kind="rbsb.settings", schema=1, sentAt=now - 60_000, temperatureUnit="C", windUnit="KMH",
                        precipitationUnit="MM", clock="H24", animations=True, minWarningLevel="watch"),
        "imperial-12h-no-animation": dict(kind="rbsb.settings", schema=1, sentAt=now - 60_000, temperatureUnit="F",
                                          windUnit="MPH", precipitationUnit="IN", clock="H12", animations=False,
                                          minWarningLevel="all"),
    }
    settings_valid["unknown-fields"] = dict(settings_valid["default"], windUnit="KN", minWarningLevel="red",
                                            futureSetting="ignored")
    settings_invalid = {}

    def bad_settings(name, reason, mutate):
        s = copy.deepcopy(settings_valid["default"])
        mutate(s)
        settings_invalid[name] = (s, reason)

    bad_settings("wrong-kind", "WRONG_KIND", lambda s: s.update(kind="rbsb.snapshot"))
    bad_settings("schema-2", "UNSUPPORTED_SCHEMA", lambda s: s.update(schema=2))
    bad_settings("missing-clock", "MISSING_FIELD", lambda s: s.pop("clock"))
    bad_settings("bad-temperature-unit", "BAD_VALUE", lambda s: s.update(temperatureUnit="K"))
    bad_settings("bad-min-warning-level", "BAD_VALUE", lambda s: s.update(minWarningLevel="severe"))
    bad_settings("animations-as-text", "WRONG_TYPE", lambda s: s.update(animations="yes"))
    bad_settings("future-dated", "FUTURE_DATED", lambda s: s.update(sentAt=now + TOL_MS + 1))

    refresh_valid = {
        "with-cached": dict(kind="rbsb.refresh", schema=1, sentAt=now, haveFetchedAt=valid["clear-day"]["fetchedAt"]),
        "nothing-cached": dict(kind="rbsb.refresh", schema=1, sentAt=now),
    }
    refresh_invalid = {
        "missing-sentAt": (dict(kind="rbsb.refresh", schema=1), "MISSING_FIELD"),
        "wrong-kind": (dict(kind="rbsb.snapshot", schema=1, sentAt=now), "WRONG_KIND"),
        "have-fetched-as-text": (dict(kind="rbsb.refresh", schema=1, sentAt=now, haveFetchedAt="yesterday"), "WRONG_TYPE"),
    }

    return now, valid, invalid, raw_invalid, settings_valid, settings_invalid, refresh_valid, refresh_invalid


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    now, valid, invalid, raw_invalid, sv, si, rv, ri = build()
    for sub in ["snapshot", "settings", "refresh"]:
        for f in (HERE / sub).rglob("*"):
            if f.is_file():
                f.unlink()
    expected = {"comment": "Created By Shane BlackbuRn. Generated by make_samples.py; 'now' is the clock both checkers use.",
                "now": now, "toleranceMs": TOL_MS}
    for kind, v, i in [("snapshot", valid, invalid), ("settings", sv, si), ("refresh", rv, ri)]:
        for name, obj in v.items():
            write(HERE / kind / "valid" / f"{name}.json", obj)
        for name, (obj, _) in i.items():
            write(HERE / kind / "invalid" / f"{name}.json", obj)
        expected[kind] = {
            "valid": sorted(f"{kind}/valid/{n}.json" for n in v),
            "invalid": {f"{kind}/invalid/{n}.json": r for n, (_, r) in sorted(i.items())},
        }
    for name, (text, reason) in raw_invalid.items():
        p = HERE / "snapshot" / "invalid" / name
        p.write_text(text + "\n", encoding="utf-8")
        expected["snapshot"]["invalid"][f"snapshot/invalid/{name}"] = reason
    expected["snapshot"]["invalid"] = dict(sorted(expected["snapshot"]["invalid"].items()))
    write(HERE / "expected.json", expected)
    total = sum(len(expected[k]["valid"]) + len(expected[k]["invalid"]) for k in ["snapshot", "settings", "refresh"])
    print(f"wrote {total} samples; now = {now}")


if __name__ == "__main__":
    main()
