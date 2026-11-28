#!/usr/bin/env python3
"""Manifest and size report for APKs (Moon verification branch: dependency and permission check).

Usage: manifest_report.py AAPT2 label=path.apk [label=path.apk ...] [--diff base_label:other_label ...]

For each APK prints: package and versions, requested permissions, permissions it defines,
components (activities, services, receivers, providers) with whether each is exported, the
meta-data names, and the file size. --diff prints what the second APK adds or removes compared
with the first. Read-only: it never changes an APK. Created By Shane BlackbuRn
"""
import os
import re
import subprocess
import sys

ANDROID = "http://schemas.android.com/apk/res/android:"


def xmltree(aapt, apk):
    out = subprocess.run([aapt, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                         capture_output=True, text=True, check=True).stdout
    root = {"tag": "#root", "attrs": {}, "kids": []}
    stack = [(-1, root)]
    for line in out.splitlines():
        m = re.match(r"^(\s*)E: (\S+)", line)
        if m:
            depth = len(m.group(1))
            while stack and stack[-1][0] >= depth:
                stack.pop()
            el = {"tag": m.group(2), "attrs": {}, "kids": []}
            stack[-1][1]["kids"].append(el)
            stack.append((depth, el))
            continue
        m = re.match(r"^\s*A: (\S+?)(?:\(0x[0-9a-f]+\))?=(.*)$", line)
        if m and len(stack) > 1:
            name = m.group(1).replace(ANDROID, "android:")
            val = m.group(2)
            val = re.sub(r" \(Raw: .*\)$", "", val)
            if val.startswith('"') and val.endswith('"'):
                val = val[1:-1]
            elif "(type 0x12)" in val:              # boolean
                val = "true" if val.endswith("0xffffffff") else "false"
            stack[-1][1]["attrs"][name] = val
    return root


def walk(el):
    yield el
    for k in el["kids"]:
        yield from walk(k)


def summary(aapt, apk):
    root = xmltree(aapt, apk)
    man = next(e for e in walk(root) if e["tag"] == "manifest")
    s = {
        "package": man["attrs"].get("package", "?"),
        "versionCode": man["attrs"].get("android:versionCode", "?"),
        "versionName": man["attrs"].get("android:versionName", "?"),
        "uses": sorted({e["attrs"].get("android:name", "?") for e in walk(man) if e["tag"] in ("uses-permission", "uses-permission-sdk-23")}),
        "defines": sorted({e["attrs"].get("android:name", "?") for e in walk(man) if e["tag"] == "permission"}),
        "components": [],
        "meta": sorted({e["attrs"].get("android:name", "?") for e in walk(man) if e["tag"] == "meta-data"}),
        "size": os.path.getsize(apk),
    }
    for e in walk(man):
        if e["tag"] in ("activity", "activity-alias", "service", "receiver", "provider"):
            has_filter = any(k["tag"] == "intent-filter" for k in e["kids"])
            exp = e["attrs"].get("android:exported")
            exported = exp == "true" if exp is not None else has_filter
            perm = e["attrs"].get("android:permission", "")
            s["components"].append((e["tag"], e["attrs"].get("android:name", "?"), exported, perm))
    s["components"].sort(key=lambda c: (c[0], c[1]))
    return s


def show(label, s):
    print(f"== {label}: {s['package']} versionCode {s['versionCode']} versionName {s['versionName']}, {s['size']:,} bytes")
    print(f"   requested permissions ({len(s['uses'])}): " + (", ".join(s["uses"]) or "none"))
    print(f"   defined permissions ({len(s['defines'])}): " + (", ".join(s["defines"]) or "none"))
    exp = [c for c in s["components"] if c[2]]
    print(f"   components: {len(s['components'])}, exported: {len(exp)}")
    for c in s["components"]:
        print(f"     {'EXPORTED ' if c[2] else 'internal '} {c[0]:<9} {c[1]}" + (f"  (needs {c[3]})" if c[3] else ""))
    print(f"   meta-data ({len(s['meta'])}): " + (", ".join(s["meta"]) or "none"))


def diff(a_label, a, b_label, b):
    print(f"== DIFF {a_label} -> {b_label}")
    def setdiff(name, x, y):
        add = sorted(set(y) - set(x)); rem = sorted(set(x) - set(y))
        print(f"   {name}: added {add or 'nothing'}; removed {rem or 'nothing'}")
    setdiff("requested permissions", a["uses"], b["uses"])
    setdiff("defined permissions", a["defines"], b["defines"])
    setdiff("exported components", [f"{c[0]} {c[1]}" for c in a["components"] if c[2]], [f"{c[0]} {c[1]}" for c in b["components"] if c[2]])
    setdiff("all components", [f"{c[0]} {c[1]}" for c in a["components"]], [f"{c[0]} {c[1]}" for c in b["components"]])
    setdiff("meta-data", a["meta"], b["meta"])
    print(f"   size: {a['size']:,} -> {b['size']:,} bytes ({b['size'] - a['size']:+,})")


def main(argv):
    aapt = argv[1]
    apks, diffs = {}, []
    it = iter(argv[2:])
    for arg in it:
        if arg == "--diff":
            diffs.append(next(it).split(":"))
        else:
            label, path = arg.split("=", 1)
            apks[label] = summary(aapt, path)
            show(label, apks[label])
    for a, b in diffs:
        diff(a, apks[a], b, apks[b])


if __name__ == "__main__":
    main(sys.argv)
