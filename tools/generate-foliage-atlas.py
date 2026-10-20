#!/usr/bin/env python3
"""Original CC0 cutout foliage, v1. Rebuild with Python 3 + Pillow; no source images.
1024² RGBA: grass left/leaves right, four variants top to bottom. Transparent
texels receive a four-pixel RGB bleed so linear filtering cannot add black halos.
"""
from pathlib import Path
import math
import random
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "javity-core/src/main/resources/foliage/leaves-and-grass.png"
S = 3
atlas = Image.new("RGBA", (1024, 1024))


def bezier(points, steps=30):
    result = []
    for i in range(steps + 1):
        t = i / steps
        q = 1 - t
        result.append(
            (
                q * q * points[0][0] + 2 * q * t * points[1][0] + t * t * points[2][0],
                q * q * points[0][1] + 2 * q * t * points[1][1] + t * t * points[2][1],
            )
        )
    return result


def leaf(d, x, y, length, angle, width, color):
    dx, dy = math.cos(angle), math.sin(angle)
    px, py = -dy, dx
    tip = (x + dx * length, y + dy * length)
    a = bezier(
        [
            (x, y),
            (x + dx * length * 0.5 + px * width, y + dy * length * 0.5 + py * width),
            tip,
        ]
    )
    b = bezier(
        [
            tip,
            (x + dx * length * 0.5 - px * width, y + dy * length * 0.5 - py * width),
            (x, y),
        ]
    )
    d.polygon(a + b, fill=color)
    vein = tuple(min(255, c + 25) for c in color[:3]) + (255,)
    d.line([(x, y), tip], fill=vein, width=2 * S)


for row in range(4):
    rng = random.Random(8100 + row)
    for col in range(2):
        tile = Image.new("RGBA", (512 * S, 256 * S))
        d = ImageDraw.Draw(tile)
        if col == 0:
            # Narrow curved blades overlap at the root but leave generous open sky.
            for i in range(9):
                root = (256 + rng.uniform(-105, 105)) * S
                h = rng.uniform(112, 219) * S
                lean = rng.uniform(-100, 100) * S
                tip = (max(28 * S, min(484 * S, root + lean)), 242 * S - h)
                w = rng.uniform(6, 14) * S
                outline = bezier(
                    [
                        (root - w, 244 * S),
                        (root - w + lean * 0.28, 244 * S - h * 0.62),
                        tip,
                    ]
                ) + bezier(
                    [
                        tip,
                        (root + w + lean * 0.55, 244 * S - h * 0.53),
                        (root + w, 244 * S),
                    ]
                )
                mask = Image.new("L", tile.size)
                ImageDraw.Draw(mask).polygon(outline, fill=255)
                gradient = Image.new("RGBA", tile.size)
                gd = ImageDraw.Draw(gradient)
                base = (35 + row * 4, 76 + row * 5, 31)
                light = (160 - row * 10, 188 - row * 4, 75 + row * 8)
                for y in range(256 * S):
                    t = max(0, min(1, (244 * S - y) / h))
                    color = tuple(
                        round(base[k] * (1 - t) + light[k] * t) for k in range(3)
                    ) + (255,)
                    gd.line((0, y, 512 * S, y), fill=color)
                tile.alpha_composite(
                    Image.composite(gradient, Image.new("RGBA", tile.size), mask)
                )
                d = ImageDraw.Draw(tile)
                d.line(
                    bezier(
                        [(root, 241 * S), (root + lean * 0.4, 244 * S - h * 0.6), tip]
                    ),
                    fill=(129, 161, 65, 255),
                    width=S,
                )
        else:
            # A forked twig with distinct pointed leaves, real holes and warm buds.
            for branch in range(3):
                origin = (256 * S, 240 * S)
                end = (
                    (135 + branch * 120 + rng.uniform(-18, 18)) * S,
                    (35 + abs(branch - 1) * 29) * S,
                )
                d.line([origin, end], fill=(91, 98, 39, 255), width=4 * S)
                for j in range(1, 6):
                    t = j / 6
                    x = origin[0] + (end[0] - origin[0]) * t
                    y = origin[1] + (end[1] - origin[1]) * t
                    for side in (-1, 1):
                        angle = -math.pi / 2 + side * rng.uniform(0.65, 1.3)
                        leaf(
                            d,
                            x,
                            y,
                            rng.uniform(38, 68) * S,
                            angle,
                            rng.uniform(13, 22) * S,
                            (
                                57 + row * 8 + rng.randrange(22),
                                112 + row * 8 + rng.randrange(28),
                                38 + row * 5,
                                255,
                            ),
                        )
                leaf(d, *end, 41 * S, -math.pi / 2, 16 * S, (133, 169, 66, 255))
        tile = tile.resize((512, 256), Image.Resampling.LANCZOS)
        # RGB dilation, alpha unchanged: invisible texels are safe under filtering.
        alpha = tile.getchannel("A")
        source = tile.convert("RGB")
        rgb = Image.new("RGB", tile.size)
        rgb.paste(source, mask=alpha.point(lambda a: 255 if a > 128 else 0))
        for _ in range(4):
            rgb = rgb.filter(ImageFilter.MaxFilter(3))
        rgb.paste(source, mask=alpha.point(lambda a: 255 if a > 0 else 0))
        rgb.putalpha(alpha)
        atlas.paste(rgb, (col * 512, row * 256))
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
atlas.save(OUTPUT, optimize=True)
print(OUTPUT)
