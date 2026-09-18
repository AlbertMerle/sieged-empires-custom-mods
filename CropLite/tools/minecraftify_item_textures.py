#!/usr/bin/env python3
"""Give CropLite item sprites a more Minecraft-like outline + edge shading.

Edits source PNGs under textures/ that map to item/ (not block-only sheets).
Re-run tools/generate_assets.py afterward to copy into assets.

Usage:
  python3 tools/minecraftify_item_textures.py
  python3 tools/minecraftify_item_textures.py --dry-run
"""

from __future__ import annotations

import argparse
import os
import shutil
from collections import Counter

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURE_SRC = os.path.join(ROOT, "textures")
BACKUP_DIR = os.path.join(ROOT, "textures", "_item_backup_pre_minecraftify")

# Source filenames that install as item textures (from generate_assets.TEXTURE_MAP).
ITEM_SOURCES = [
    "hay.png",
    "tomato.png",
    "tomatoseed.png",
    "pepper.png",
    "pepperseed.png",
    "eggplant.png",
    "eggplantseed.png",
    "cucumber.png",
    "cucumberseed.png",
    "sweetpotato.png",
    "bakedsweetpotato.png",
    "oats.png",
    "beans.png",
    "rice.png",
    "basil.png",
    "basilseed.png",
    "cantelopeslice.png",
    "peach.png",
    "lemon.png",
    "lemonade.png",
    "oatmeal.png",
    "rice_and_beans.png",
    "coffee_bean.png",
    "roasted_coffee_bean.png",
    "bottle_of_coffee.png",
    "garlic.png",
    "banana.png",
    "cactus_flesh.png",
    # Shared item+block (inventory icons): still item art
    "peach_sapling.png",
    "lemon_sapling.png",
    "banana_sapling.png",
]

NEIGH4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
NEIGH8 = NEIGH4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))


def clamp(v: int) -> int:
    return max(0, min(255, int(round(v))))


def lerp_channel(c: int, t: float) -> int:
    """t>1 lighten toward white, t<1 darken toward black."""
    if t >= 1.0:
        return clamp(c + (255 - c) * (t - 1.0))
    return clamp(c * t)


def scale_rgb(rgb: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    r, g, b = rgb
    return (lerp_channel(r, t), lerp_channel(g, t), lerp_channel(b, t))


def mix_rgb(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))  # type: ignore[return-value]


def brightness(rgb: tuple[int, int, int]) -> float:
    return (rgb[0] + rgb[1] + rgb[2]) / 3.0


def opaque(px, x, y, w, h, thresh=128) -> bool:
    if not (0 <= x < w and 0 <= y < h):
        return False
    return px[x, y][3] >= thresh


def harden_alpha(im: Image.Image) -> Image.Image:
    """Minecraft items are fully opaque or fully transparent pixels."""
    out = im.convert("RGBA")
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                px[x, y] = (0, 0, 0, 0)
            elif a < 128:
                px[x, y] = (0, 0, 0, 0)
            else:
                px[x, y] = (r, g, b, 255)
    return out


def unique_opaque_colors(im: Image.Image) -> int:
    px = im.load()
    w, h = im.size
    colors = set()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a >= 128:
                colors.add((r, g, b))
    return len(colors)


def local_base_color(px, x, y, w, h) -> tuple[int, int, int]:
    """Average of nearby opaque RGB (biased to self)."""
    samples: list[tuple[int, int, int]] = []
    for dx, dy in ((0, 0),) + NEIGH8:
        nx, ny = x + dx, y + dy
        if opaque(px, nx, ny, w, h):
            r, g, b, _ = px[nx, ny]
            samples.append((r, g, b))
            if dx == 0 and dy == 0:
                samples.append((r, g, b))
                samples.append((r, g, b))
    if not samples:
        return (40, 40, 40)
    n = len(samples)
    return (
        sum(s[0] for s in samples) // n,
        sum(s[1] for s in samples) // n,
        sum(s[2] for s in samples) // n,
    )


def is_edge_pixel(px, x, y, w, h) -> bool:
    if not opaque(px, x, y, w, h):
        return False
    for dx, dy in NEIGH4:
        if not opaque(px, x + dx, y + dy, w, h):
            return True
    return False


def outline_color(base: tuple[int, int, int]) -> tuple[int, int, int]:
    """Dark outline: always darker than the body, with a hint of hue.

    Never lighten — bright rims read as glow, not Minecraft outlines.
    """
    br = brightness(base)
    # Light items (rice, garlic): strong dark rim.
    if br > 180:
        return mix_rgb(scale_rgb(base, 0.22), (18, 16, 12), 0.4)
    # Mid tones.
    if br > 70:
        return mix_rgb(scale_rgb(base, 0.32), (12, 10, 8), 0.3)
    # Already-dark bodies: push toward near-black, keep a little chroma.
    return mix_rgb(scale_rgb(base, 0.45), (8, 6, 6), 0.55)


def has_interior_neighbor(px, x, y, w, h) -> bool:
    """True if this edge pixel backs onto a non-edge (thick) body pixel."""
    for dx, dy in NEIGH4:
        nx, ny = x + dx, y + dy
        if opaque(px, nx, ny, w, h) and not is_edge_pixel(px, nx, ny, w, h):
            return True
    return False


def bevel_factor(px, x, y, w, h, bevel_strength: float, flatness: int) -> float:
    """Directional light from top-left → highlight / shadow multiplier."""
    tl = sum(
        1
        for dx, dy in ((-1, -1), (0, -1), (-1, 0), (-1, 1), (1, -1))
        if not opaque(px, x + dx, y + dy, w, h)
    )
    br = sum(
        1
        for dx, dy in ((1, 1), (0, 1), (1, 0), (1, -1), (-1, 1))
        if not opaque(px, x + dx, y + dy, w, h)
    )
    near_tl_edge = any(
        is_edge_pixel(px, x + dx, y + dy, w, h)
        for dx, dy in ((-1, 0), (0, -1), (-1, -1))
        if opaque(px, x + dx, y + dy, w, h)
    )
    near_br_edge = any(
        is_edge_pixel(px, x + dx, y + dy, w, h)
        for dx, dy in ((1, 0), (0, 1), (1, 1))
        if opaque(px, x + dx, y + dy, w, h)
    )

    factor = 1.0
    if tl > br:
        factor += bevel_strength * (0.12 + 0.08 * tl)
    elif br > tl:
        factor -= bevel_strength * (0.14 + 0.08 * br)
    if near_tl_edge and not near_br_edge:
        factor += bevel_strength * 0.18
    if near_br_edge and not near_tl_edge:
        factor -= bevel_strength * 0.22

    # Tiny ordered dither so large flat fills aren't one slab
    if flatness <= 8:
        dither = ((x * 3 + y * 5) % 7) - 3
        factor += bevel_strength * 0.035 * dither

    return factor


def minecraftify(im: Image.Image) -> Image.Image:
    src = harden_alpha(im)
    w, h = src.size
    assert w == 16 and h == 16, f"expected 16x16, got {w}x{h}"
    spx = src.load()

    flatness = unique_opaque_colors(src)
    # Flat fills get stronger interior bevel; detailed art gets a lighter touch.
    bevel_strength = 0.45 if flatness <= 12 else (0.24 if flatness <= 40 else 0.12)

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    opx = out.load()

    # Pass 1: shade body. Only crush silhouette pixels to outline when the
    # shape is thick enough to still have an interior (thin strands like rice
    # would otherwise become 100% outline).
    for y in range(h):
        for x in range(w):
            if not opaque(spx, x, y, w, h):
                continue
            r, g, b, _ = spx[x, y]
            base = (r, g, b)
            edge = is_edge_pixel(spx, x, y, w, h)
            factor = bevel_factor(spx, x, y, w, h, bevel_strength, flatness)
            rgb = scale_rgb(base, factor)

            if edge and has_interior_neighbor(spx, x, y, w, h):
                target = outline_color(base)
                blend = 0.65 if brightness(base) < 50 else 0.82
                rgb = mix_rgb(rgb, target, blend)
            elif edge:
                # Thin strand: mild darken so outer rim still reads, keep body color
                rgb = mix_rgb(rgb, outline_color(base), 0.28)

            opx[x, y] = (*rgb, 255)

    # Pass 2: outer 1px outline into transparent neighbors (classic MC rim).
    rim_pixels: dict[tuple[int, int], list[tuple[int, int, int]]] = {}
    for y in range(h):
        for x in range(w):
            if opaque(opx, x, y, w, h):
                continue
            neighbors = []
            for dx, dy in NEIGH4:
                nx, ny = x + dx, y + dy
                if opaque(opx, nx, ny, w, h):
                    # Prefer original body color (pre-rim) for outline hue
                    neighbors.append(local_base_color(opx, nx, ny, w, h))
            if neighbors:
                rim_pixels[(x, y)] = neighbors

    for (x, y), neighbors in rim_pixels.items():
        n = len(neighbors)
        avg = (
            sum(c[0] for c in neighbors) // n,
            sum(c[1] for c in neighbors) // n,
            sum(c[2] for c in neighbors) // n,
        )
        opx[x, y] = (*outline_color(avg), 255)

    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--no-backup", action="store_true")
    args = ap.parse_args()

    if not args.dry_run and not args.no_backup:
        os.makedirs(BACKUP_DIR, exist_ok=True)

    changed = 0
    for name in ITEM_SOURCES:
        path = os.path.join(TEXTURE_SRC, name)
        if not os.path.isfile(path):
            print(f"missing: {name}")
            continue
        im = Image.open(path)
        before_colors = unique_opaque_colors(harden_alpha(im))
        out = minecraftify(im)
        after_colors = unique_opaque_colors(out)

        if args.dry_run:
            print(f"{name:28} colors {before_colors:3d} -> {after_colors:3d}")
            continue

        if not args.no_backup:
            bak = os.path.join(BACKUP_DIR, name)
            if not os.path.isfile(bak):
                shutil.copy2(path, bak)

        out.save(path, optimize=True)
        changed += 1
        print(f"updated {name:28} colors {before_colors:3d} -> {after_colors:3d}")

    if not args.dry_run:
        print(f"\nDone: {changed} item textures. Backup: {BACKUP_DIR}")
        print("Next: python3 tools/generate_assets.py")


if __name__ == "__main__":
    main()
