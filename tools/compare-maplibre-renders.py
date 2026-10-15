#!/usr/bin/env python3
"""Compare paired raw MapLibre Native and GL JS map captures.

Run from the repository root after capturing both styles:
    python3 tools/compare-maplibre-renders.py

Uses RGB screenshots at their original pixel dimensions. A foreground pixel is
one whose largest channel differs from that image's upper-left background by
more than the reported RGB tolerance (3); this threshold is for mask stability,
not a pass/fail acceptance threshold.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUTS = {
    "day": {
        "native": ROOT / "docs/demo-maplibre-map.png",
        "gljs": ROOT / "docs/demo-maplibre-reference.png",
    },
    "night": {
        "native": ROOT / "docs/demo-maplibre-night-map.png",
        "gljs": ROOT / "docs/demo-maplibre-reference-night.png",
    },
}
PROBES = {"northwest_lake": (325, 280)}
PIXEL_TOLERANCE = 3


def image_data(path: Path) -> tuple[Image.Image, np.ndarray, dict]:
    if not path.is_file():
        raise FileNotFoundError(path)
    raw = path.read_bytes()
    image = Image.open(path).convert("RGB")
    array = np.asarray(image, dtype=np.int16)
    metadata = {
        "path": str(path.relative_to(ROOT) if path.is_relative_to(ROOT) else path),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "width": image.width,
        "height": image.height,
        "channels_compared": "RGB (alpha ignored)",
    }
    return image, array, metadata


def bbox(mask: np.ndarray) -> list[int] | None:
    ys, xs = np.nonzero(mask)
    if not len(xs):
        return None
    return [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())]


def metrics(native: np.ndarray, gljs: np.ndarray) -> dict:
    if native.shape != gljs.shape:
        raise ValueError(f"Capture dimensions differ: Native {native.shape} vs GL JS {gljs.shape}")
    diff = np.abs(native - gljs)
    pixels = int(native.shape[0] * native.shape[1])
    channel_count = pixels * 3
    tolerance_mask = np.max(diff, axis=2) <= PIXEL_TOLERANCE
    significant_mask = ~tolerance_mask

    native_bg = native[0, 0]
    gljs_bg = gljs[0, 0]
    native_fg = np.max(np.abs(native - native_bg), axis=2) > PIXEL_TOLERANCE
    gljs_fg = np.max(np.abs(gljs - gljs_bg), axis=2) > PIXEL_TOLERANCE
    intersection = int(np.count_nonzero(native_fg & gljs_fg))
    union = int(np.count_nonzero(native_fg | gljs_fg))

    probes = {}
    height, width = native.shape[:2]
    for name, (x, y) in PROBES.items():
        if x >= width or y >= height:
            continue
        probes[name] = {
            "pixel_xy": [x, y],
            "native_rgb": [int(c) for c in native[y, x]],
            "gljs_rgb": [int(c) for c in gljs[y, x]],
            "absolute_channel_difference": [int(c) for c in diff[y, x]],
        }

    mae = diff.mean(axis=(0, 1))
    rmse = np.sqrt(np.square(diff, dtype=np.int32).mean(axis=(0, 1)))
    return {
        "dimensions": {"width": width, "height": height, "pixels": pixels},
        "pixel_comparison": {
            "channel_mae_rgb": [round(float(v), 6) for v in mae],
            "channel_rmse_rgb": [round(float(v), 6) for v in rmse],
            "overall_channel_mae": round(float(diff.sum() / channel_count), 6),
            "overall_channel_rmse": round(float(np.sqrt(np.square(diff, dtype=np.int32).mean())), 6),
            "exact_channel_fraction": round(float(np.count_nonzero(diff == 0) / channel_count), 8),
            "exact_pixel_fraction": round(float(np.count_nonzero(np.max(diff, axis=2) == 0) / pixels), 8),
            "pixel_fraction_with_all_rgb_channels_within_tolerance": round(float(tolerance_mask.mean()), 8),
            "tolerance_rgb_per_channel": PIXEL_TOLERANCE,
            "significant_mismatch_pixel_count": int(np.count_nonzero(significant_mask)),
        },
        "foreground_relative_to_each_image_background": {
            "background_rgb": {
                "native": [int(c) for c in native_bg],
                "gljs": [int(c) for c in gljs_bg],
            },
            "mask_rule": f"max RGB channel distance from own top-left background > {PIXEL_TOLERANCE}",
            "native_foreground_pixels": int(np.count_nonzero(native_fg)),
            "gljs_foreground_pixels": int(np.count_nonzero(gljs_fg)),
            "intersection_pixels": intersection,
            "union_pixels": union,
            "intersection_over_union": round(intersection / union, 8) if union else 1.0,
            "native_bbox_xyxy": bbox(native_fg),
            "gljs_bbox_xyxy": bbox(gljs_fg),
        },
        "probes": probes,
    }


def make_side_by_side(panels: dict[str, dict[str, Image.Image]], output: Path) -> None:
    sample = panels["day"]["native"]
    width, height = sample.size
    label_height = 34
    output_image = Image.new("RGB", (width * 2, (height + label_height) * 2), (244, 246, 248))
    draw = ImageDraw.Draw(output_image)
    font = ImageFont.load_default()
    for row, style in enumerate(("day", "night")):
        for col, renderer in enumerate(("native", "gljs")):
            x = col * width
            y = row * (height + label_height)
            label = f"{style.upper()}  |  {'MapLibre Native FFI 0.202610.0' if renderer == 'native' else 'MapLibre GL JS 6.14.0'}"
            draw.text((x + 12, y + 10), label, fill=(30, 38, 46), font=font)
            output_image.paste(panels[style][renderer], (x, y + label_height))
    output.parent.mkdir(parents=True, exist_ok=True)
    output_image.save(output, format="PNG", optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-day", type=Path, default=DEFAULT_INPUTS["day"]["native"])
    parser.add_argument("--gljs-day", type=Path, default=DEFAULT_INPUTS["day"]["gljs"])
    parser.add_argument("--native-night", type=Path, default=DEFAULT_INPUTS["night"]["native"])
    parser.add_argument("--gljs-night", type=Path, default=DEFAULT_INPUTS["night"]["gljs"])
    parser.add_argument("--json", type=Path, default=ROOT / "docs/maplibre-render-comparison.json")
    parser.add_argument("--image", type=Path, default=ROOT / "docs/maplibre-comparison.png")
    args = parser.parse_args()

    source_paths = {
        "day": {"native": args.native_day, "gljs": args.gljs_day},
        "night": {"native": args.native_night, "gljs": args.gljs_night},
    }
    loaded: dict[str, dict[str, tuple[Image.Image, np.ndarray, dict]]] = {}
    panel_images: dict[str, dict[str, Image.Image]] = {}
    report = {
        "comparison": "MapLibre Native raw map captures vs MapLibre GL JS raw map captures",
        "renderers": {
            "native": "MapLibre Native FFI 0.202610.0 / OpenGL EGL",
            "gljs": "MapLibre GL JS 6.14.0 / Chromium WebGL (SwiftShader)",
        },
        "method": {
            "comparison_space": "8-bit sRGB RGB screenshot values",
            "alpha": "ignored; inputs converted to RGB",
            "tolerance": "all three RGB channel absolute differences <= 3",
            "significant_mismatch": "a pixel with any RGB channel difference > 3",
            "foreground": "each image compared with its own upper-left background pixel; max RGB distance > 3",
            "interpretation": "Descriptive renderer comparison; metrics are not pass/fail thresholds.",
            "limitations": "Native and GL JS use different rasterizers, antialiasing, and GPU paths; small edge and text differences are expected.",
        },
        "styles": {},
    }

    for style, renderers in source_paths.items():
        loaded[style] = {}
        panel_images[style] = {}
        for renderer, path in renderers.items():
            img, array, metadata = image_data(path)
            loaded[style][renderer] = (img, array, metadata)
            panel_images[style][renderer] = img
        native_array = loaded[style]["native"][1]
        gljs_array = loaded[style]["gljs"][1]
        if native_array.shape != gljs_array.shape:
            raise ValueError(f"{style} capture dimensions differ")
        report["styles"][style] = {
            "inputs": {renderer: loaded[style][renderer][2] for renderer in ("native", "gljs")},
            **metrics(native_array, gljs_array),
        }

    make_side_by_side(panel_images, args.image)
    report["artifacts"] = {
        "side_by_side_png": str(args.image.relative_to(ROOT) if args.image.is_relative_to(ROOT) else args.image),
        "comparison_json": str(args.json.relative_to(ROOT) if args.json.is_relative_to(ROOT) else args.json),
    }
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
