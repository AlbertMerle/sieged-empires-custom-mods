#!/usr/bin/env python3
"""Convert plant/leaf greens to greyscale and split fruit onto untinted overlays.

Minecraft multiplies tintindex faces by biome foliage color. Fruit on the same
layer would shift (pink peaches → muddy green), so chromatic non-foliage pixels
are moved to a sibling ``*_fruit.png`` overlay rendered without tint.

Run from project root: python3 tools/foliage_biome_tint_textures.py
"""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "textures"
BLOCK = ROOT / "src/main/resources/assets/croplite/textures/block"

# Source filename → installed block texture stem(s) (no .png).
# Overlays are written as ``{stem}_fruit.png`` when fruit pixels exist.
SOURCE_TO_STEMS: dict[str, list[str]] = {
    "peachleaves.png": ["peach_leaves"],
    "lemonleaves.png": ["lemon_leaves"],
    "banana_leaves.png": ["banana_leaves"],
    "allplant_stage1.png": ["crop_stage0"],
    "allplant_stage2.png": ["crop_stage1"],
    "eggplant_tomato_pepper_stage3.png": ["crop_stage2"],
    "eggplant_tomato_pepper_stage4567.png": ["crop_stage3"],
    "tomato_plant_top_bottom_finalstae.png": ["tomato_plant_mature"],
    "tomato_plant_top_bottom_fruiting.png": ["tomato_plant_fruiting"],
    "pepperplant_fruiting.png": ["pepper_plant_fruiting"],
    "eggplant_plant_fruiting.png": ["eggplant_plant_fruiting"],
    "cucumber_cantelope_stage1.png": ["cucumber_stage0"],
    "cucumber_cantelope_stage234.png": ["cucumber_stage1"],
    "cucumber_cantelope_stage56.png": ["cucumber_stage2"],
    "cucumber_cantelope_plant_fullgrown.png": ["cucumber_plant_mature"],
    "cucumber_cantelope_plant_fruiting.png": ["cucumber_plant_fruiting"],
    "cucumber_plant_tilted.png": ["cucumber_plant_tilted"],
    "oats_beans_rice_stage1.png": ["grain_stage0"],
    "oats_beans_rice_stage2.png": ["grain_stage1"],
    "oats_beans_rice_stage34.png": ["grain_stage2"],
    "oats_beans_rice_stage567.png": ["grain_stage3"],
    # Golden mature grain — leave pre-colored (no biome foliage multiply).
    # "oats_rice_plant_finalstage_bottom.png"
    # "oats_plant_top_finalstage.png"
    # "beans_plant_finalstage.png"
    "rice_plant_top_finalstage.png": ["rice_plant_mature_upper"],
    "basil_stage1.png": ["basil_stage0"],
    "basil_stage2.png": ["basil_stage1"],
    "basil_stage3456.png": ["basil_stage2"],
    "basil_finalstage.png": ["basil_stage3"],
    "coffee_grow1.png": ["coffee_stage0"],
    "coffee_grow2.png": ["coffee_stage1"],
    "coffee_grow_final.png": ["coffee_stage2", "coffee_plant_mature"],
    "coffee_grow_fruiting.png": ["coffee_plant_fruiting"],
    "garlic_growth1.png": ["garlic_stage0"],
    "garlic_growth2.png": ["garlic_stage1"],
    "garlic_growth3.png": ["garlic_stage2"],
    "garlic_growth4.png": ["garlic_stage3"],
    # Final garlic is olive plant only (no fruit) — still greyscale, never split.
    "garlic_growth_final.png": ["garlic_stage4"],
    # Golden mature grain — entire sprite is untinted overlay (transparent foliage base).
    "beans_plant_finalstage.png": ["beans_plant_mature"],
    "oats_plant_top_finalstage.png": ["oats_plant_mature_upper"],
    "oats_rice_plant_finalstage_bottom.png": ["oats_rice_plant_mature_lower"],
}

# Never treat chromatic pixels as fruit (whole texture → greyscale foliage).
FOLIAGE_ONLY_SOURCES = {
    "garlic_growth_final.png",
    "garlic_growth1.png",
    "garlic_growth2.png",
    "garlic_growth3.png",
    "garlic_growth4.png",
    "basil_stage1.png",
    "basil_stage2.png",
    "basil_stage3456.png",
    "basil_finalstage.png",
    "eggplant_tomato_pepper_stage3.png",
    "eggplant_tomato_pepper_stage4567.png",
    "tomato_plant_top_bottom_finalstae.png",
    "pepperplant_fruiting.png",
}

# Move every opaque pixel to the fruit overlay (biome tint must not touch these).
GOLDEN_OVERLAY_SOURCES = {
    "beans_plant_finalstage.png",
    "oats_plant_top_finalstage.png",
    "oats_rice_plant_finalstage_bottom.png",
}


def hue_sat_val(r: int, g: int, b: int) -> tuple[float, float, float]:
    mx, mn = max(r, g, b), min(r, g, b)
    v = mx / 255.0
    d = mx - mn
    s = (d / mx) if mx else 0.0
    if d == 0:
        h = 0.0
    elif mx == r:
        h = (60.0 * ((g - b) / d) + 360.0) % 360.0
    elif mx == g:
        h = 60.0 * ((b - r) / d) + 120.0
    else:
        h = 60.0 * ((r - g) / d) + 240.0
    return h, s, v


def is_fruit_pixel(r: int, g: int, b: int, a: int) -> bool:
    """Chromatic pixels that must stay untinted (fruit / grain heads / pods)."""
    if a < 16:
        return False
    h, s, v = hue_sat_val(r, g, b)
    if s < 0.18:
        return False
    # Bright yellow fruit (banana, lemon) — exclude olive leaf shadows via sat/value.
    if 40.0 <= h <= 72.0 and s >= 0.35 and v >= 0.25:
        return True
    # Red / pink / orange fruit (peach, tomato, coffee cherry).
    if (h <= 40.0 or h >= 330.0) and s >= 0.18:
        return True
    # Saturated purple eggplant.
    if 250.0 <= h <= 320.0 and s >= 0.35 and v >= 0.2:
        return True
    # Tan / gold grain heads mixed into leafy sheets (rice).
    if 25.0 <= h <= 50.0 and s >= 0.35 and v >= 0.45 and r >= 140 and b < g * 0.75:
        return True
    return False


def to_grey(r: int, g: int, b: int) -> int:
    """Luma close to how green foliage reads before biome multiply."""
    return int(round(0.2126 * r + 0.7152 * g + 0.0722 * b))


# Vanilla short_grass / oak_leaves highlights sit around 180–190. Authored CropLite
# greens were darker, so Rec.709 greyscale looked muddy even after biome multiply.
FOLIAGE_LUMA_TARGET_MAX = 190


def normalize_foliage_luma(im: Image.Image, target_max: int = FOLIAGE_LUMA_TARGET_MAX) -> Image.Image:
    """Stretch greyscale foliage so biome tint reads as jungle-bright / forest-dark."""
    im = im.convert("RGBA")
    max_y = 0
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a >= 16:
                max_y = max(max_y, r)
    if max_y <= 0 or max_y >= target_max:
        return im
    scale = target_max / max_y
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a < 16:
                continue
            v = min(255, int(round(r * scale)))
            out.putpixel((x, y), (v, v, v, a))
    return out


def process_rgba(
    im: Image.Image, *, allow_fruit: bool
) -> tuple[Image.Image, Image.Image | None, dict[str, int]]:
    im = im.convert("RGBA")
    foliage = Image.new("RGBA", im.size, (0, 0, 0, 0))
    fruit = Image.new("RGBA", im.size, (0, 0, 0, 0))
    stats = {"foliage": 0, "fruit": 0, "transparent": 0}
    has_fruit = False

    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a < 16:
                stats["transparent"] += 1
                continue
            if allow_fruit and is_fruit_pixel(r, g, b, a):
                fruit.putpixel((x, y), (r, g, b, a))
                has_fruit = True
                stats["fruit"] += 1
            else:
                yv = to_grey(r, g, b)
                foliage.putpixel((x, y), (yv, yv, yv, a))
                stats["foliage"] += 1

    return foliage, (fruit if has_fruit else None), stats


def process_golden_overlay(im: Image.Image) -> tuple[Image.Image, Image.Image, dict[str, int]]:
    """Entire opaque sprite → fruit overlay; foliage layer stays empty."""
    im = im.convert("RGBA")
    foliage = Image.new("RGBA", im.size, (0, 0, 0, 0))
    fruit = Image.new("RGBA", im.size, (0, 0, 0, 0))
    stats = {"foliage": 0, "fruit": 0, "transparent": 0}
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a < 16:
                stats["transparent"] += 1
            else:
                fruit.putpixel((x, y), (r, g, b, a))
                stats["fruit"] += 1
    return foliage, fruit, stats


def save(path: Path, im: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, "PNG")


def main() -> None:
    report: list[str] = []
    for src_name, stems in SOURCE_TO_STEMS.items():
        src = TEXTURES / src_name
        if not src.is_file():
            report.append(f"MISSING {src_name}")
            continue
        allow_fruit = src_name not in FOLIAGE_ONLY_SOURCES
        fruit_src = TEXTURES / src_name.replace(".png", "_fruit.png")

        # Rebuild from base+fruit when re-running after a prior split.
        base = Image.open(src).convert("RGBA")
        if fruit_src.is_file():
            overlay_im = Image.open(fruit_src).convert("RGBA")
            merged = base.copy()
            merged.paste(overlay_im, (0, 0), overlay_im)
            base = merged

        if src_name in GOLDEN_OVERLAY_SOURCES:
            foliage, fruit, stats = process_golden_overlay(base)
        else:
            foliage, fruit, stats = process_rgba(base, allow_fruit=allow_fruit)
            foliage = normalize_foliage_luma(foliage)

        save(src, foliage)
        if fruit is not None and stats["fruit"] > 0:
            save(fruit_src, fruit)
        elif fruit_src.is_file():
            fruit_src.unlink()
            fruit = None

        for stem in stems:
            save(BLOCK / f"{stem}.png", foliage)
            fruit_dst = BLOCK / f"{stem}_fruit.png"
            if fruit is not None:
                save(fruit_dst, fruit)
            elif fruit_dst.is_file():
                fruit_dst.unlink()

        overlay = " +fruit" if fruit is not None else ""
        report.append(
            f"{src_name} → {', '.join(stems)}{overlay} "
            f"(foliage={stats['foliage']} fruit={stats['fruit']})"
        )

    print("foliage biome-tint textures:")
    for line in report:
        print(" ", line)


if __name__ == "__main__":
    main()
