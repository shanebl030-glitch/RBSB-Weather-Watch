#!/usr/bin/env python3
"""Round-screen fit check (Stage 3 acceptance criterion A8).

Reads a uiautomator dump of a watch screen and checks that every visible text element lies
inside the round display (all four corners of its box within the circle, 2 px tolerance), and
that no two texts overlap each other (more than 2 px both ways). The overlap check was added
after a two-line "No update since Sat 10:00" label ran into the place name on 432 px.
Tappable elements (clickable="true", such as the warnings chip) are left out of the overlap
test: uiautomator reports their enlarged 48 dp touch area, not the drawn label, so they would
clash with the text below even when the screenshot shows a clear gap. They are still checked
against the round edge, and the screenshots are reviewed by eye.
Used on screens that do not scroll mid-content (the sky in every data state, the no-data
screens), where nothing should ever be cut by the round edge.

Usage: round_fit.py UI_XML SCREEN_WIDTH_PX NAME
Prints one line: "ROUND FIT NAME: OK (n texts)", or "ROUND FIT NAME: NOT inside: ..." and/or
"ROUND FIT NAME: NOT apart: ..." (texts overlapping).
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
    bad, n, boxes = [], 0, []
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
        if node.get("clickable") != "true":
            boxes.append((text, x0, y0, x1, y1))
        worst = max(((x - r) ** 2 + (y - r) ** 2) ** 0.5 for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)))
        if worst > r + 2:
            bad.append(f"'{text[:28]}' [{x0},{y0}][{x1},{y1}] {worst - r:.0f}px out")
    clash = overlaps(boxes)
    if bad:
        print(f"ROUND FIT {name}: NOT inside: " + "; ".join(bad))
    if clash:
        print(f"ROUND FIT {name}: NOT apart: " + "; ".join(clash))
    if not bad and not clash:
        print(f"ROUND FIT {name}: OK ({n} texts)")


def overlaps(boxes, tol=2):
    """Pairs of different texts whose boxes overlap by more than tol px in both directions.
    A box that holds another with the same text (a merged parent) is not a clash."""
    out = []
    for i, (ta, a0, b0, a1, b1) in enumerate(boxes):
        for tb, c0, d0, c1, d1 in boxes[i + 1:]:
            ox = min(a1, c1) - max(a0, c0)
            oy = min(b1, d1) - max(b0, d0)
            if ox <= tol or oy <= tol:
                continue
            if ta == tb or ta in tb or tb in ta:
                continue
            out.append(f"'{ta[:20]}' / '{tb[:20]}' {ox}x{oy}px")
    return out


if __name__ == "__main__":
    main(*sys.argv[1:4])
