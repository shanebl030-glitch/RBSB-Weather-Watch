#!/usr/bin/env python3
# Extracts the signing identity Android reports for an installed package from
# `adb shell dumpsys package <pkg>`: the current signer(s) and the signing history (past
# signatures with their capability flags). Prints JSON. Created By Shane BlackbuRn
import json, re, sys

text = open(sys.argv[1]).read()
m = re.search(r"signatures=PackageSignatures\{[^}]*\}", text)
if not m:
    sys.exit("No PackageSignatures entry found in dumpsys output")
entry = m.group(0)
cur = re.search(r"signatures:\[([^\]]*)\]", entry)
past = re.search(r"past signatures:\[([^\]]*)\]", entry)
ver = re.search(r"version:(\d+)", entry)
out = {
    "raw": entry,
    "scheme_version": int(ver.group(1)) if ver else None,
    "current": [x.strip() for x in cur.group(1).split(",") if x.strip()] if cur else [],
    "history": [x.strip() for x in past.group(1).split(",") if x.strip()] if past else [],
}
print(json.dumps(out, sort_keys=True))
