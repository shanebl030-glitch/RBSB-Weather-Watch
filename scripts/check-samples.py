#!/usr/bin/env python3
"""Independent check of the sync format v1 samples (Stage 1).

RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn

A second implementation, written separately from the Kotlin decoder: it validates every sample in
sync/samples against the JSON Schemas in sync/contract plus the rules JSON Schema cannot express,
and checks each result against sync/samples/expected.json. The Kotlin tests check the same files
against the same expected.json, so if this script and the Kotlin decoder both pass, they agree.

Usage: python3 scripts/check-samples.py [repo-root]
Needs the jsonschema package (pip install jsonschema).
"""
import json
import pathlib
import re
import sys
from datetime import date, datetime
from decimal import Decimal

from jsonschema import Draft202012Validator
from jsonschema.exceptions import best_match

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent)
SAMPLES = ROOT / "sync" / "samples"
CONTRACT = ROOT / "sync" / "contract"

LOCAL_TIME = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$")

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
    if not LOCAL_TIME.match(s):
        raise Reject("BAD_FORMAT", where)
    try:
        datetime.fromisoformat(s)
    except ValueError:
        raise Reject("BAD_FORMAT", where)


def three_decimals(v):
    # Uses the shortest decimal form of the number (a different method from the Kotlin check).
    return Decimal(repr(float(v))).as_tuple().exponent >= -3


def snapshot_rules(s, now, tol):
    p = s["place"]
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


def check(path, validator, rules, now, tol):
    """Returns None for an accepted sample, else the rejection reason."""
    try:
        try:
            obj = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=no_duplicates)
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
    if failures:
        print(f"\nSAMPLES FAIL: {len(failures)} problem(s)")
        for f in failures:
            print("  " + f)
        sys.exit(1)
    print(f"\nSAMPLES OK: {total} samples, every result matches expected.json (schema + extra rules, independent of the Kotlin decoder)")


if __name__ == "__main__":
    main()
