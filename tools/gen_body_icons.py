#!/usr/bin/env python3
"""Generates app/src/main/res/drawable/ic_body_*.xml from tools/body_polygons.json.

Each icon is a square crop of one body view: every muscle polygon drawn faint, the group's muscles solid.
Polygons derive from react-body-highlighter / react-native-body-highlighter, see THIRD_PARTY_NOTICES.md.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "tools" / "body_polygons.json"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable"

FAINT_ALPHA = 0.28
FRONT_CROP = (150, 230, 700)

# name: (view, solid muscles, (x, y, size), muscles left out entirely)
ICONS = {
    "chest": ("anterior", ["CHEST"], FRONT_CROP, []),
    "back": ("posterior", ["UPPER_BACK", "LOWER_BACK", "TRAPEZIUS"], (70, 200, 860), []),
    "shoulders": ("anterior", ["FRONT_DELTOIDS"], FRONT_CROP, []),
    "rear_delts": ("posterior", ["BACK_DELTOIDS"], FRONT_CROP, []),
    "biceps": ("anterior", ["BICEPS"], FRONT_CROP, []),
    "triceps": ("posterior", ["TRICEPS"], FRONT_CROP, []),
    "legs": ("anterior", ["QUADRICEPS", "CALVES", "ABDUCTORS"], (-20, 930, 1040), ["FOREARM"]),
    "core": ("anterior", ["ABS", "OBLIQUES"], (150, 420, 700), []),
    "other": ("anterior", [], FRONT_CROP, []),
}


def num(v):
    return str(int(v)) if float(v).is_integer() else repr(float(v))


def path_data(polygons):
    parts = []
    for flat in polygons:
        pts = [f"{num(flat[i])},{num(flat[i + 1])}" for i in range(0, len(flat), 2)]
        parts.append("M" + " L".join(pts) + " Z")
    return " ".join(parts)


def path_xml(polygons, alpha):
    a = "" if alpha == 1 else f'\n            android:fillAlpha="{alpha}"'
    return (f'        <path\n            android:fillColor="#FF000000"{a}\n'
            f'            android:pathData="{path_data(polygons)}" />\n')


def icon_xml(view, solid, crop, skip, data):
    x, y, size = crop
    faint, strong = [], []
    for muscle, polygons in data[view].items():
        if muscle in skip:
            continue
        (strong if muscle in solid else faint).extend(polygons)
    body = path_xml(faint, FAINT_ALPHA) + (path_xml(strong, 1) if strong else "")
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n    android:height="24dp"\n'
        f'    android:viewportWidth="{size}"\n    android:viewportHeight="{size}">\n'
        f'    <group android:translateX="{-x}" android:translateY="{-y}">\n'
        f'{body}'
        '    </group>\n</vector>\n'
    )


def main():
    data = json.loads(DATA.read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (view, solid, crop, skip) in ICONS.items():
        for m in solid + skip:
            assert m in data[view], f"{name}: no {m} in {view}"
        (OUT / f"ic_body_{name}.xml").write_text(icon_xml(view, solid, crop, skip, data))
        print(f"wrote ic_body_{name}.xml")


if __name__ == "__main__":
    main()
