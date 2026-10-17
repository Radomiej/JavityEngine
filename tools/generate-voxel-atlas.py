#!/usr/bin/env python3
"""Generate Javity's original, deterministic 16px voxel atlas (stdlib only)."""

from __future__ import annotations

import json
import random
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "javity-desktop/src/main/resources/org/javity/demo/voxels"
ATLAS_PATH = OUT_DIR / "atlas.png"
METADATA_PATH = OUT_DIR / "atlas.json"
LICENSE_PATH = OUT_DIR / "LICENSE.txt"
PREVIEW_PATH = ROOT / "docs/voxel-atlas.png"

TILE = 16
GUTTER = 2
STRIDE = TILE + 2 * GUTTER
COLUMNS = 4
ROWS = 2
WIDTH = STRIDE * COLUMNS
HEIGHT = STRIDE * ROWS
SEED = 0x4A415649

TILE_NAMES = (
    "grass_top",
    "grass_side",
    "dirt",
    "stone",
    "wood_side",
    "wood_top",
    "sand",
    "leaves",
)

Color = tuple[int, int, int, int]
PixelTile = list[list[Color]]


def _noise(rng: random.Random, palette: tuple[Color, ...], width: int, height: int) -> PixelTile:
    return [[rng.choice(palette) for _ in range(width)] for _ in range(height)]


def _grass_top(rng: random.Random) -> PixelTile:
    palette = ((70, 126, 48, 255), (82, 143, 53, 255), (94, 153, 59, 255), (62, 116, 45, 255))
    tile = _noise(rng, palette, TILE, TILE)
    # Short paired blades and occasional warm soil pixels make a readable top.
    for _ in range(24):
        x, y = rng.randrange(TILE), rng.randrange(TILE)
        color = rng.choice(((116, 165, 67, 255), (53, 105, 42, 255), (143, 160, 64, 255)))
        tile[y][x] = color
        if rng.random() < 0.55 and x + 1 < TILE:
            tile[y][x + 1] = color
    return tile


def _dirt(rng: random.Random) -> PixelTile:
    palette = ((116, 76, 46, 255), (128, 84, 49, 255), (139, 94, 56, 255), (105, 68, 42, 255))
    tile = _noise(rng, palette, TILE, TILE)
    for _ in range(22):
        x, y = rng.randrange(TILE), rng.randrange(TILE)
        tile[y][x] = rng.choice(((164, 116, 68, 255), (85, 57, 38, 255), (151, 99, 58, 255)))
    return tile


def _grass_side(rng: random.Random, dirt: PixelTile) -> PixelTile:
    tile = [row[:] for row in dirt]
    # A broken 4px green cap; the lower 11px exactly match the dirt tile.
    for y in range(5):
        for x in range(TILE):
            if y < 3 or rng.random() < 0.90:
                tile[y][x] = rng.choice(((72, 132, 49, 255), (85, 145, 55, 255), (97, 153, 59, 255)))
    for x in (2, 8, 13):
        tile[4][x] = (58, 111, 44, 255)
    return tile


def _stone(rng: random.Random) -> PixelTile:
    palette = ((91, 101, 105, 255), (106, 116, 119, 255), (119, 128, 130, 255), (82, 92, 97, 255))
    tile = _noise(rng, palette, TILE, TILE)
    # L-shaped mineral flecks and seams add a cut-stone rhythm without white noise.
    for _ in range(7):
        x, y = rng.randrange(1, TILE - 2), rng.randrange(1, TILE - 2)
        shade = rng.choice(((139, 145, 143, 255), (68, 79, 83, 255), (128, 135, 133, 255)))
        tile[y][x] = shade
        tile[y][x + 1] = shade
        if rng.random() < 0.7:
            tile[y + 1][x] = shade
    return tile


def _wood_side(rng: random.Random) -> PixelTile:
    tile: PixelTile = []
    bands = (111, 76, 43, 132, 90, 50, 103, 70, 40, 145, 99, 53, 117, 79, 45, 94)
    for y in range(TILE):
        row: list[Color] = []
        for x in range(TILE):
            shift = ((y // 4) % 2) if (x // 4) % 2 else 0
            value = bands[(x + shift) % len(bands)] + rng.randrange(-5, 6)
            row.append((value, int(value * 0.68), int(value * 0.39), 255))
        tile.append(row)
    for x in (3, 11):
        for y in range(TILE):
            if y % 5 != 0:
                tile[y][x] = (76, 53, 34, 255)
    for y in (4, 12):
        tile[y][6] = (184, 129, 69, 255)
    return tile


def _wood_top(rng: random.Random) -> PixelTile:
    tile: PixelTile = []
    center_x, center_y = 7, 8
    for y in range(TILE):
        row: list[Color] = []
        for x in range(TILE):
            # Manhattan rings stay crisp and legible at native size.
            ring = max(abs(x - center_x), abs(y - center_y))
            if ring in (3, 6):
                base = (93, 60, 35)
            elif ring in (2, 5):
                base = (183, 128, 70)
            elif ring in (1, 4, 7):
                base = (139, 91, 49)
            else:
                base = (213, 165, 95)
            jitter = rng.randrange(-4, 5)
            row.append((base[0] + jitter, base[1] + jitter, base[2] + jitter, 255))
        tile.append(row)
    # Small off-center heart mark gives the cut face a distinct focal point.
    tile[7][7] = (240, 194, 119, 255)
    tile[8][7] = (228, 178, 103, 255)
    return tile


def _sand(rng: random.Random) -> PixelTile:
    palette = ((194, 169, 112, 255), (211, 187, 128, 255), (222, 199, 139, 255), (182, 157, 103, 255))
    tile = _noise(rng, palette, TILE, TILE)
    for _ in range(20):
        x, y = rng.randrange(TILE), rng.randrange(TILE)
        tile[y][x] = rng.choice(((234, 213, 153, 255), (164, 143, 98, 255), (201, 177, 119, 255)))
    return tile


def _leaves(rng: random.Random) -> PixelTile:
    palette = ((43, 92, 45, 255), (54, 111, 51, 255), (68, 126, 57, 255), (78, 137, 63, 255))
    tile = _noise(rng, palette, TILE, TILE)
    # Compact overlapping clusters with bright tips; every texel remains opaque.
    for _ in range(17):
        cx, cy = rng.randrange(1, TILE - 1), rng.randrange(1, TILE - 1)
        tone = rng.choice(((91, 151, 67, 255), (35, 79, 40, 255), (105, 157, 73, 255)))
        for dy, dxs in ((-1, (0,)), (0, (-1, 0, 1)), (1, (0,))):
            for dx in dxs:
                if 0 <= cx + dx < TILE and 0 <= cy + dy < TILE:
                    tile[cy + dy][cx + dx] = tone
    return tile


def _tiles() -> list[PixelTile]:
    rng = random.Random(SEED)
    grass_top = _grass_top(rng)
    dirt = _dirt(rng)
    return [
        grass_top,
        _grass_side(rng, dirt),
        dirt,
        _stone(rng),
        _wood_side(rng),
        _wood_top(rng),
        _sand(rng),
        _leaves(rng),
    ]


def _put_tile(atlas: PixelTile, tile: PixelTile, cell_x: int, cell_y: int) -> None:
    left = cell_x * STRIDE + GUTTER
    top = cell_y * STRIDE + GUTTER
    for py in range(-GUTTER, TILE + GUTTER):
        source_y = min(TILE - 1, max(0, py))
        for px in range(-GUTTER, TILE + GUTTER):
            source_x = min(TILE - 1, max(0, px))
            atlas[top + py][left + px] = tile[source_y][source_x]


def _png_bytes(pixels: PixelTile) -> bytes:
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + bytes(channel for px in row for channel in px) for row in pixels)

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, level=9))
        + chunk(b"IEND", b"")
    )


def _metadata() -> dict[str, object]:
    tiles: list[dict[str, object]] = []
    for index, name in enumerate(TILE_NAMES):
        column, row = index % COLUMNS, index // COLUMNS
        cell_x, cell_y = column * STRIDE, row * STRIDE
        x0, y0 = cell_x + GUTTER, cell_y + GUTTER
        x1, y1 = x0 + TILE, y0 + TILE
        tiles.append(
            {
                "id": index,
                "name": name,
                "column": column,
                "row": row,
                "cell": {"x": cell_x, "y": cell_y, "width": STRIDE, "height": STRIDE},
                "imageRect": {"x": x0, "y": y0, "width": TILE, "height": TILE},
                "uv": {
                    "u0": (x0 + 0.5) / WIDTH,
                    "v0": (y0 + 0.5) / HEIGHT,
                    "u1": (x1 - 0.5) / WIDTH,
                    "v1": (y1 - 0.5) / HEIGHT,
                },
            }
        )
    return {
        "format": "javity-voxel-atlas-v1",
        "image": "atlas.png",
        "width": WIDTH,
        "height": HEIGHT,
        "pixelFormat": "RGBA8",
        "origin": "top-left image pixels",
        "tileSize": TILE,
        "gutter": GUTTER,
        "cellStride": STRIDE,
        "columns": COLUMNS,
        "rows": ROWS,
        "filter": "nearest",
        "mipmaps": False,
        "alpha": "all texels are fully opaque",
        "uvConvention": "u increases right; image-top maps to low v; face top samples v0 and bottom v1; bounds inset half a texel into tile content",
        "seed": f"0x{SEED:08x}",
        "license": "LICENSE.txt",
        "tiles": tiles,
    }


def _write_preview(atlas: PixelTile, scale: int = 8) -> None:
    preview = [[(0, 0, 0, 255) for _ in range(WIDTH * scale)] for _ in range(HEIGHT * scale)]
    for y, row in enumerate(atlas):
        for x, color in enumerate(row):
            for dy in range(scale):
                yy = y * scale + dy
                for dx in range(scale):
                    preview[yy][x * scale + dx] = color
    preview_png = _png_bytes(preview)
    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    PREVIEW_PATH.write_bytes(preview_png)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    atlas: PixelTile = [[(0, 0, 0, 255) for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for index, tile in enumerate(_tiles()):
        _put_tile(atlas, tile, index % COLUMNS, index // COLUMNS)
    ATLAS_PATH.write_bytes(_png_bytes(atlas))
    METADATA_PATH.write_text(json.dumps(_metadata(), indent=2, sort_keys=True) + "\n", encoding="utf-8")
    LICENSE_PATH.write_text(
        "Javity voxel atlas — original project artwork\n"
        "Copyright (c) 2026 Javity contributors.\n\n"
        "This atlas and its deterministic generator were authored for JavityEngine.\n"
        "Permission is granted to use, copy, modify, and distribute these project-authored\n"
        "artwork files with JavityEngine and projects built with it, including commercially.\n"
        "No third-party artwork is included. This note does not grant rights to the Javity\n"
        "name or other project trademarks. The files are provided without warranty.\n",
        encoding="utf-8",
    )
    _write_preview(atlas)
    print(f"generated {ATLAS_PATH.relative_to(ROOT)} ({WIDTH}x{HEIGHT}) and {PREVIEW_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
