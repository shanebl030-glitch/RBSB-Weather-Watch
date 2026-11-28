#!/usr/bin/env python3
"""Round-screen fit check (Stage 3 acceptance criterion A8).

Reads a uiautomator dump of a watch screen and checks that every visible text element lies
inside the round display: all four corners of its box within the circle (2 px tolerance).
Used on screens that do not scroll mid-content (the sky in every data state, the no-data
screens), where nothing should ever be cut by the round edge.

Usage: round_fit.py UI_XML SCREEN_WIDTH_PX NAME
Prints one line: "ROUND FIT NAME: OK (n texts)" or "ROUND FIT NAME: NOT inside: ...".
Created By Shane BlackbuRn
"""
import re
import sys
import xml.etree.ElementTree as ET


def main(path, width, name):
    w = int(width)
    r = w / 2.0
    try:
        root = ET.parse(path).getroot()
    except Exception as e:  # an unreadable dump is reported, not hidden
        print(f"ROUND FIT {name}: NOT checked (dump unreadable: {e})")
        return
    bad, n = [], 0
    for node in root.iter("node"):
        text = (node.get("text") or "").strip()
        if not text:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds") or "")
        if not m:
            continue
        x0, y0, x1, y1 = map(int, m.groups())
        if x1 <= x0 or y1 <= y0:
            continue
        n += 1
        worst = max(((x - r) ** 2 + (y - r) ** 2) ** 0.5 for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)))
        if worst > r + 2:
            bad.append(f"'{text[:28]}' [{x0},{y0}][{x1},{y1}] {worst - r:.0f}px out")
    if bad:
        print(f"ROUND FIT {name}: NOT inside: " + "; ".join(bad))
    else:
        print(f"ROUND FIT {name}: OK ({n} texts)")


if __name__ == "__main__":
    main(*sys.argv[1:4])
