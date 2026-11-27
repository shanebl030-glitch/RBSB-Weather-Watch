#!/usr/bin/env python3
# 64-bit check for every APK given (approved plan, section 3). Created By Shane BlackbuRn
#
# Google Play (from 15 Sep 2026): Wear OS apps must support 64-bit devices, and any native
# code must also ship a 64-bit version. This check fails if an APK contains native libraries
# for a 32-bit ABI without the same libraries for its 64-bit counterpart.
import sys
import zipfile
from collections import defaultdict

PAIRS = {"armeabi-v7a": "arm64-v8a", "armeabi": "arm64-v8a", "x86": "x86_64"}


def check(path: str) -> bool:
    libs = defaultdict(set)
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            parts = name.split("/")
            if len(parts) == 3 and parts[0] == "lib" and parts[2].endswith(".so"):
                libs[parts[1]].add(parts[2])
    if not libs:
        print(f"64-BIT OK: {path}: no native code (pure Kotlin/Java runs on 32- and 64-bit alike)")
        return True
    ok = True
    for abi, names in sorted(libs.items()):
        print(f"  {abi}: {len(names)} libraries")
        partner = PAIRS.get(abi)
        if partner is not None:
            missing = names - libs.get(partner, set())
            if missing:
                ok = False
                print(f"  MISSING 64-bit ({partner}) versions of: {', '.join(sorted(missing))}")
    if "arm64-v8a" not in libs:
        ok = False
        print("  MISSING arm64-v8a (the ABI of Galaxy Watch processors)")
    print(("64-BIT OK: " if ok else "64-BIT FAIL: ") + path)
    return ok


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: check-64bit.py APK [APK...]")
    results = [check(p) for p in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
