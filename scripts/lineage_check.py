#!/usr/bin/env python3
# Reads `apksigner lineage --print-certs -v` output and reports each signer's certificate and
# capability flags. Optionally checks the old and new certificates, and that the old key cannot
# roll back, share a user ID or keep auth (the position accepted for Android 0.7.2).
# Created By Shane BlackbuRn
import argparse, json, re, sys

CAPS = {"installed data": "installed_data", "shared uid": "shared_uid", "permission": "permission",
        "rollback": "rollback", "auth": "auth"}

def parse(text):
    signers = []
    for line in text.splitlines():
        m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", line)
        if m:
            signers.append({"cert": m.group(1).replace(":", "").lower(), "flags": {}})
            continue
        m = re.search(r"Has (.+?) capability:\s*(true|false)", line, re.I)
        if m and signers:
            key = CAPS.get(m.group(1).strip().lower(), m.group(1).strip().lower().replace(" ", "_"))
            signers[-1]["flags"][key] = m.group(2).lower() == "true"
    return signers

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file"); ap.add_argument("--expect-old"); ap.add_argument("--expect-new"); ap.add_argument("--json")
    a = ap.parse_args()
    s = parse(open(a.file).read())
    if len(s) != 2:
        sys.exit(f"Expected a two-key history (old -> new), found {len(s)} signers")
    old, new = s
    print(f"Old key {old['cert'][:8]}…{old['cert'][-8:]} capabilities: {old['flags']}")
    print(f"New key {new['cert'][:8]}…{new['cert'][-8:]} capabilities: {new['flags']}")
    bad = []
    if a.expect_old and old["cert"] != a.expect_old.lower(): bad.append("old certificate is not the expected one")
    if a.expect_new and new["cert"] != a.expect_new.lower(): bad.append("new certificate is not the expected one")
    for cap in ("rollback", "shared_uid", "auth"):
        if old["flags"].get(cap) is not False: bad.append(f"old key must not have the {cap} capability")
    if bad:
        sys.exit("LINEAGE FAIL: " + "; ".join(bad))
    print("LINEAGE OK: old key cannot roll back, share a user ID or keep auth access")
    if a.json:
        json.dump({"certs": [old["cert"], new["cert"]], "old_flags": old["flags"]}, open(a.json, "w"), sort_keys=True)

if __name__ == "__main__":
    main()
