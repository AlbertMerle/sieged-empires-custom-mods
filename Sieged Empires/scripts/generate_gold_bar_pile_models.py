#!/usr/bin/env python3
"""Generate gold_bar_pile block models — full-size bars side-by-side along X only."""

import json
from copy import deepcopy
from pathlib import Path

MAX_COUNT = 15
MAX_LAYERS = 5
BARS_PER_LAYER = 3
BAR_WIDTH = 5.0
BAR_HEIGHT = 3.0
GAP = 1.0
LAYER_Y = [float(i * BAR_HEIGHT) for i in range(MAX_LAYERS)]  # 0,3,6,9,12 → 15px total
CENTERED_X_MIN = 5.5  # left edge of a single centered bar after rotation

# -90° Y rotation remaps cube face names
FACE_ROT_NEG90_Y = {
    "east": "north",
    "north": "west",
    "west": "south",
    "south": "east",
    "up": "up",
    "down": "down",
}

BAR_TEMPLATES = [
    {
        "name": "bar_base",
        "from": [0, 0, 5.5],
        "to": [16, 2, 10.5],
        "rotation": {"angle": 0, "axis": "y", "origin": [8, 0, 8]},
        "faces": {
            "north": {"uv": [5, 1, 1, 1.5], "texture": "#0"},
            "east": {"uv": [7.25, 1, 6, 1.5], "texture": "#0"},
            "south": {"uv": [8.25, 1, 12.25, 1.5], "texture": "#0"},
            "west": {"uv": [13.25, 1, 14.5, 1.5], "texture": "#0"},
            "up": {"uv": [1, 2.5, 5, 3.75], "texture": "#0"},
            "down": {"uv": [6, 2.5, 10, 3.75], "texture": "#0"},
        },
    },
    {
        "name": "bar_mid",
        "from": [0.5, 1.5, 6],
        "to": [15.5, 2.5, 10],
        "rotation": {"angle": 0, "axis": "y", "origin": [8, 0, 8]},
        "faces": {
            "north": {"uv": [14.75, 2.5, 11, 2.75], "texture": "#0"},
            "east": {"uv": [2, 4.75, 1, 5], "texture": "#0"},
            "south": {"uv": [3, 4.75, 6.75, 5], "texture": "#0"},
            "west": {"uv": [7.75, 4.75, 8.75, 5], "texture": "#0"},
            "up": {"uv": [9.75, 4.75, 13.5, 5.75], "texture": "#0"},
            "down": {"uv": [1, 6.75, 4.75, 7.75], "texture": "#0"},
        },
    },
    {
        "name": "bar_top",
        "from": [1, 1, 6.5],
        "to": [15, 3, 9.5],
        "rotation": {"angle": 0, "axis": "y", "origin": [8, -2, 8]},
        "faces": {
            "north": {"uv": [9.25, 6.75, 5.75, 7.25], "texture": "#0"},
            "east": {"uv": [11, 6.75, 10.25, 7.25], "texture": "#0"},
            "south": {"uv": [12, 6.75, 15.5, 7.25], "texture": "#0"},
            "west": {"uv": [1, 8.75, 1.75, 9.25], "texture": "#0"},
            "up": {"uv": [2.75, 8.75, 6.25, 9.5], "texture": "#0"},
            "down": {"uv": [7.25, 8.75, 10.75, 9.5], "texture": "#0"},
        },
    },
]


def rot_y_neg90(x: float, y: float, z: float, ox: float, oy: float, oz: float) -> tuple[float, float, float]:
    rx, rz = x - ox, z - oz
    return ox + rz, y, oz - rx


def row_x_starts(layer_count: int) -> list[float]:
    if layer_count == 1:
        return [(16.0 - BAR_WIDTH) / 2.0]
    if layer_count == 2:
        total = 2 * BAR_WIDTH + GAP
        start = (16.0 - total) / 2.0
        return [start, start + BAR_WIDTH + GAP]
    total = 3 * BAR_WIDTH
    start = (16.0 - total) / 2.0
    return [start, start + BAR_WIDTH, start + 2 * BAR_WIDTH]


def bake_element(template: dict, y_layer: float, x_shift: float, bar_index: int) -> dict:
    rot_origin = template["rotation"]["origin"]
    ox, oy, oz = rot_origin[0], rot_origin[1] + y_layer, rot_origin[2]

    from_c = template["from"]
    to_c = template["to"]

    corners = []
    for x in (from_c[0], to_c[0]):
        for y in (from_c[1], to_c[1]):
            for z in (from_c[2], to_c[2]):
                rx, ry, rz = rot_y_neg90(x, y + y_layer, z, ox, oy, oz)
                corners.append((rx + x_shift, ry, rz))

    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    zs = [c[2] for c in corners]

    faces = {}
    for face_name, face_data in template["faces"].items():
        faces[FACE_ROT_NEG90_Y[face_name]] = deepcopy(face_data)

    return {
        "name": f"{template['name']}_{bar_index}",
        "from": [min(xs), min(ys), min(zs)],
        "to": [max(xs), max(ys), max(zs)],
        "faces": faces,
    }


def make_bar_elements(x_start: float, y_layer: float, bar_index: int) -> list[dict]:
    x_shift = x_start - CENTERED_X_MIN
    return [bake_element(template, y_layer, x_shift, bar_index) for template in BAR_TEMPLATES]


def build_pile_model(count: int) -> dict:
    elements = []
    remaining = count
    bar_index = 0
    for layer in range(MAX_LAYERS):
        if remaining <= 0:
            break
        layer_count = min(BARS_PER_LAYER, remaining)
        remaining -= layer_count
        y_layer = LAYER_Y[layer]
        for x_start in row_x_starts(layer_count):
            elements.extend(make_bar_elements(x_start, y_layer, bar_index))
            bar_index += 1
    return {
        "texture_size": [64, 64],
        "textures": {
            "0": "siegedempires:block/gold_bar",
            "particle": "minecraft:block/gold_block",
        },
        "elements": elements,
    }


def write_blockstates(out_dir: Path) -> None:
    variants = {}
    for count in range(1, MAX_COUNT + 1):
        model = f"siegedempires:block/gold_bar_pile_{count}"
        variants[f"count={count},rotated=false"] = {"model": model}
        variants[f"count={count},rotated=true"] = {"model": model, "y": 90}
    path = out_dir / "blockstates/gold_bar_pile.json"
    path.write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path.name}")


def main() -> None:
    assets = Path(__file__).resolve().parents[1] / "src/main/resources/assets/siegedempires"
    model_dir = assets / "models/block"
    model_dir.mkdir(parents=True, exist_ok=True)
    for count in range(1, MAX_COUNT + 1):
        model = build_pile_model(count)
        path = model_dir / f"gold_bar_pile_{count}.json"
        path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {path.name} ({len(model['elements'])} elements)")
    write_blockstates(assets)


if __name__ == "__main__":
    main()
