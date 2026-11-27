#!/usr/bin/env python3
# Signing gate 1: compares, on ONE device, three independent views of the probe's signing identity:
#   system   - what Android records (`dumpsys package`, signatures listed by hash code)
#   app      - what the installed app sees through PackageManager.signingInfo (logged by the probe)
#   expected - what apksigner read from the APK file before it was installed (SHA-256)
# Fails unless all three agree on the current signer and the full signing history.
# Usage: identity_compare.py SYSTEM.json APP.json EXPECTED.json OUT.json
# Created By Shane BlackbuRn
import json, sys

system, app, expected = (json.load(open(p)) for p in sys.argv[1:4])
out_path = sys.argv[4]
fail = []
if "error" in app:
    fail.append(f"the app could not read its signing info: {app['error']}")
else:
    sys_current = system["current"]
    sys_history = [h.split()[0] for h in system["history"]]
    print(f"System (dumpsys):  current {sys_current}, history {sys_history}")
    print(f"App (PackageManager): current {app['current_hash']}, history {app['history_hash']}")
    print(f"App SHA-256:       current {[c[:8] for c in app['current_sha256']]}, history {[c[:8] for c in app['history_sha256']]}")
    print(f"APK file (apksigner) SHA-256: history {[c[:8] for c in expected['certs']]}")
    if app["current_hash"] != sys_current:
        fail.append("app-reported current signer differs from the system's")
    if app["history_hash"] != sys_history:
        fail.append("app-reported signing history differs from the system's")
    if app["current_sha256"] != [expected["certs"][-1]]:
        fail.append("app-reported current signer SHA-256 differs from the APK file's newest certificate")
    if app["history_sha256"] != expected["certs"]:
        fail.append("app-reported history SHA-256 differs from the APK file's lineage")
    if len(sys_history) != 2:
        fail.append(f"expected a two-key history, system reports {len(sys_history)}")
if fail:
    sys.exit("IDENTITY MISMATCH: " + "; ".join(fail))
print("DEVICE IDENTITY OK: system, app and APK file agree on the current signer and the history")
json.dump({"system_current": system["current"], "system_history": system["history"],
           "current_sha256": app["current_sha256"], "history_sha256": app["history_sha256"]},
          open(out_path, "w"), sort_keys=True)
