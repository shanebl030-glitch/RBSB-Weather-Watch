#!/usr/bin/env python3
"""Independent check of the sync format v1 samples (Stage 1).

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

A second implementation, written separately from the Kotlin decoder: it validates every sample in
sync/samples against the JSON Schemas in sync/contract plus the rules JSON Schema cannot express,
and checks each result against sync/samples/expected.json. The Kotlin tests check the same files
against the same expected.json, so if this script and the Kotlin decoder both pass, they agree.

Usage: python3 scripts/check-samples.py [--java-zones FILE] [repo-root]
Needs the jsonschema and tzdata packages (pip install jsonschema tzdata).

Time zones: place.timezone must be a real IANA name in Python's zoneinfo database, read only from
the tzdata package (the same full IANA list on every machine), minus the names listed in
sync/contract/timezone-rules.json. With --java-zones (written by the Kotlin tests), the full lists
of names accepted by Java and by Python are compared and must be identical.
"""
import argparse
import json
import pathlib
import re
import sys
import zoneinfo
from datetime import date, datetime
from decimal import Decimal

from jsonschema import Draft202012Validator
from jsonschema.exceptions import best_match

ARGS = argparse.ArgumentParser()
ARGS.add_argument("--java-zones", help="file of time-zone names the Kotlin tests accepted, one per line")
ARGS.add_argument("root", nargs="?", default=str(pathlib.Path(__file__).resolve().parent.parent))
OPTS = ARGS.parse_args()
ROOT = pathlib.Path(OPTS.root)
SAMPLES = ROOT / "sync" / "samples"
CONTRACT = ROOT / "sync" / "contract"

# Full matches only: Python's "$" would also accept a trailing newline, which the Kotlin checks reject.
LOCAL_TIME = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?")
SOURCE_ID = re.compile(r"[A-Za-z0-9-]{8,64}")
TIMEZONE_CHARS = re.compile(r"[A-Za-z0-9_+\-/]{1,64}")


def accepted_time_zones():
    try:
        import tzdata  # noqa: F401  (the IANA database as a Python package)
    except ImportError:
        sys.exit("check-samples.py needs the tzdata package: pip install tzdata")
    zoneinfo.reset_tzpath([])                    # ignore the system's files; use only the tzdata package
    rules = json.loads((CONTRACT / "timezone-rules.json").read_text(encoding="utf-8"))
    names = zoneinfo.available_timezones()
    return {z for z in names
            if z not in rules["excluded"] and not any(z.startswith(p) for p in rules["excludedPrefixes"])}


ZONES = accepted_time_zones()

KEYWORD_REASON = {
    "required": "MISSING_FIELD",
    "type": "WRONG_TYPE",
    "minimum": "OUT_OF_RANGE", "maximum": "OUT_OF_RANGE",
    "exclusiveMinimum": "OUT_OF_RANGE", "exclusiveMaximum": "OUT_OF_RANGE",
    "minLength": "BAD_LENGTH", "maxLength": "BAD_LENGTH",
    "maxItems": "TOO_MANY",
    "pattern": "BAD_FORMAT", "anyOf": "BAD_FORMAT",
    "enum": "BAD_VALUE",
}


class Reject(Exception):
    def __init__(self, reason, where):
        super().__init__(f"{reason} at {where}")
        self.reason = reason


def no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise Reject("BAD_JSON", "duplicate key")
    return dict(pairs)


def schema_reason(err):
    if err.validator == "const":
        last = err.path[-1] if err.path else ""
        return {"kind": "WRONG_KIND", "schema": "UNSUPPORTED_SCHEMA"}.get(last, "BAD_VALUE")
    return KEYWORD_REASON.get(err.validator, "UNEXPECTED:" + str(err.validator))


def real_local_time(s, where):
    if not LOCAL_TIME.fullmatch(s):
        raise Reject("BAD_FORMAT", where)
    try:
        datetime.fromisoformat(s)
    except ValueError:
        raise Reject("BAD_FORMAT", where)


def three_decimals(v):
    # Uses the shortest decimal form of the number (a different method from the Kotlin check).
    return Decimal(repr(float(v))).as_tuple().exponent >= -3


def snapshot_rules(s, now, tol):
    if not SOURCE_ID.fullmatch(s["sourceId"]):
        raise Reject("BAD_FORMAT", "sourceId")
    p = s["place"]
    if not TIMEZONE_CHARS.fullmatch(p["timezone"]):
        raise Reject("BAD_FORMAT", "place.timezone")
    if p["timezone"] not in ZONES:
        raise Reject("BAD_VALUE", "place.timezone")
    for k in ("latitude", "longitude"):
        if not three_decimals(p[k]):
            raise Reject("UNROUNDED_COORDINATES", f"place.{k}")
    real_local_time(s["current"]["time"], "current.time")
    hours = [h["time"] for h in s["hourly"]]
    for i, t in enumerate(hours):
        real_local_time(t, f"hourly[{i}].time")
    for i, d in enumerate(s["daily"]):
        try:
            date.fromisoformat(d["date"])
        except ValueError:
            raise Reject("BAD_FORMAT", f"daily[{i}].date")
        for k in ("sunrise", "sunset"):
            if d.get(k) is not None:
                real_local_time(d[k], f"daily[{i}].{k}")
        if d.get("tempMax") is not None and d.get("tempMin") is not None and d["tempMax"] < d["tempMin"]:
            raise Reject("INCONSISTENT", f"daily[{i}].tempMax")
    if any(b <= a for a, b in zip(hours, hours[1:])):
        raise Reject("INCONSISTENT", "hourly order")
    days = [d["date"] for d in s["daily"]]
    if any(b <= a for a, b in zip(days, days[1:])):
        raise Reject("INCONSISTENT", "daily order")
    if s["sentAt"] < s["fetchedAt"]:
        raise Reject("INCONSISTENT", "sentAt")
    for i, w in enumerate(s["warnings"]):
        if w.get("start") is not None and w.get("end") is not None and w["end"] < w["start"]:
            raise Reject("INCONSISTENT", f"warnings[{i}].end")
    m = s["moon"]
    if m.get("alwaysUp") and m.get("alwaysDown"):
        raise Reject("INCONSISTENT", "moon")
    for k in ("fetchedAt", "sentAt"):
        if s[k] > now + tol:
            raise Reject("FUTURE_DATED", k)


def sent_rule(s, now, tol):
    if s["sentAt"] > now + tol:
        raise Reject("FUTURE_DATED", "sentAt")


def check(path, validator, rules, now, tol, text=None):
    """Returns None for an accepted sample, else the rejection reason."""
    try:
        try:
            obj = json.loads(text if text is not None else path.read_text(encoding="utf-8"), object_pairs_hook=no_duplicates)
        except json.JSONDecodeError:
            raise Reject("BAD_JSON", "parse")
        if not isinstance(obj, dict):
            raise Reject("BAD_JSON", "not an object")
        err = best_match(validator.iter_errors(obj))
        if err is not None:
            raise Reject(schema_reason(err), "/".join(str(x) for x in err.path) or "(root)")
        rules(obj, now, tol)
        return None
    except Reject as r:
        return r.reason


def tzdata_version():
    import tzdata
    return getattr(tzdata, "IANA_VERSION", "?")


def main():
    expected = json.loads((SAMPLES / "expected.json").read_text(encoding="utf-8"))
    now, tol = expected["now"], expected["toleranceMs"]
    kinds = {
        "snapshot": (CONTRACT / "snapshot-v1.schema.json", snapshot_rules),
        "settings": (CONTRACT / "settings-v1.schema.json", sent_rule),
        "refresh": (CONTRACT / "refresh-v1.schema.json", sent_rule),
    }
    failures, total = [], 0
    for kind, (schema_path, rules) in kinds.items():
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema)
        listed = set(expected[kind]["valid"]) | set(expected[kind]["invalid"])
        on_disk = {str(f.relative_to(SAMPLES)) for f in (SAMPLES / kind).rglob("*") if f.is_file()}
        for extra in sorted(on_disk - listed):
            failures.append(f"{extra}: on disk but not in expected.json")
        for missing in sorted(listed - on_disk):
            failures.append(f"{missing}: in expected.json but missing")
        for rel in expected[kind]["valid"]:
            total += 1
            got = check(SAMPLES / rel, validator, rules, now, tol)
            print(f"{'OK  ' if got is None else 'FAIL'} {rel}: {'accepted' if got is None else 'rejected ' + got} (expected accepted)")
            if got is not None:
                failures.append(f"{rel}: rejected {got}, expected accepted")
        for rel, want in expected[kind]["invalid"].items():
            total += 1
            got = check(SAMPLES / rel, validator, rules, now, tol)
            ok = got == want
            print(f"{'OK  ' if ok else 'FAIL'} {rel}: {'accepted' if got is None else 'rejected ' + got} (expected {want})")
            if not ok:
                failures.append(f"{rel}: got {got}, expected {want}")
    # Time-zone cases both checkers must agree on, each tried as place.timezone in the clear-day sample.
    tz_cases = json.loads((SAMPLES / "timezones.json").read_text(encoding="utf-8"))
    snap_validator = Draft202012Validator(json.loads((CONTRACT / "snapshot-v1.schema.json").read_text(encoding="utf-8")))
    base = json.loads((SAMPLES / "snapshot/valid/clear-day.json").read_text(encoding="utf-8"))
    cases = [(z, None) for z in tz_cases["accept"]] + list(tz_cases["reject"].items())
    for zone, want in cases:
        total += 1
        base["place"]["timezone"] = zone
        got = check(None, snap_validator, snapshot_rules, now, tol, text=json.dumps(base))
        ok = got == want
        print(f"{'OK  ' if ok else 'FAIL'} timezone {zone!r}: {'accepted' if got is None else 'rejected ' + got} (expected {want or 'accepted'})")
        if not ok:
            failures.append(f"timezone {zone!r}: got {got}, expected {want or 'accepted'}")
    print(f"TIMEZONES (Python, tzdata {tzdata_version()}): {len(ZONES)} names accepted")

    if OPTS.java_zones:
        java = set(pathlib.Path(OPTS.java_zones).read_text(encoding="utf-8").split())
        only_java, only_python = sorted(java - ZONES), sorted(ZONES - java)
        if only_java or only_python:
            failures.append(f"time-zone lists differ: only Java accepts {only_java}; only Python accepts {only_python}")
        else:
            print(f"TIMEZONES MATCH: Java and Python accept the same {len(java)} names")

    if failures:
        print(f"\nSAMPLES FAIL: {len(failures)} problem(s)")
        for f in failures:
            print("  " + f)
        sys.exit(1)
    print(f"\nSAMPLES OK: {total - len(cases)} samples and {len(cases)} time-zone cases, every result matches the expected one "
          "(schema + extra rules, independent of the Kotlin decoder)")


if __name__ == "__main__":
    main()
