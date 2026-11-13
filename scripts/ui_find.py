"""Prints "x y", the center of the first on-screen node whose text or content description is exactly
the given string, from a `uiautomator dump` file. Exits with status 1 when there is no such node.

Usage: python3 ui_find.py ui.xml "Quit Fastwise"
"""
import re
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    path, wanted = sys.argv[1], sys.argv[2]
    for node in ET.parse(path).getroot().iter("node"):
        if wanted not in (node.get("text"), node.get("content-desc")):
            continue
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds", "")))
        if x2 > x1 and y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
