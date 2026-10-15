import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ARTIFACTS = ROOT / "artifacts"
browser_metrics = json.loads((ARTIFACTS / "k4-image-browser-metrics.json").read_text())
native_metrics = json.loads((ARTIFACTS / "k4-image-native-metrics.json").read_text())


def require(condition, message):
    if not condition:
        raise AssertionError(message)


rect_errors = {}
for state in ("initial", "resized"):
    browser_images = browser_metrics[state]["images"]
    native_images = native_metrics[state]
    require(set(browser_images) == set(native_images), f"{state}: fixture image keys differ")
    for key, browser_image in browser_images.items():
        actual = native_images[key]
        expected_rect = browser_image["rect"]
        native_rect = actual["rect"]
        deltas = {
            name: round(abs(expected_rect[name] - native_rect[name]), 2)
            for name in ("x", "y", "width", "height")
        }
        rect_errors[f"{state}.{key}"] = deltas
        require(max(deltas.values()) <= 1, f"{state}.{key}: browser/native box differs: {deltas}")
        require(browser_image["fit"] in ("contain", "cover", "fill"), f"{key}: unexpected CSS fit")
        expected_fit = "stretch" if browser_image["fit"] == "fill" else browser_image["fit"]
        require(actual["fit"] == expected_fit, f"{key}: object-fit mapping differs")


pixel_means = {}
screenshots = {}
for state, suffix in (("initial", ""), ("resized", "-resized")):
    browser = Image.open(ARTIFACTS / f"k4-image-browser{suffix}.png").convert("RGB")
    native = Image.open(ARTIFACTS / f"k4-image-native{suffix}.png").convert("RGB")
    state_means = {}
    for key, item in browser_metrics[state]["images"].items():
        box = item["rect"]
        x, y, width, height = (round(box[name]) for name in ("x", "y", "width", "height"))
        browser_crop = np.asarray(browser.crop((x + 2, y + 2, x + width - 2, y + height - 2)), dtype=np.int16)
        native_crop = np.asarray(native.crop((x + 2, y + 2, x + width - 2, y + height - 2)), dtype=np.int16)
        mean = float(np.abs(browser_crop - native_crop).mean())
        state_means[key] = round(mean, 3)
        require(mean <= 3, f"{state}.{key}: RGB pixel mean difference exceeds 3 ({mean:.3f})")
    pixel_means[state] = state_means
    screenshots[state] = (browser, native)


def pixel(image, key, local_x, local_y):
    rect = browser_metrics["initial"]["images"][key]["rect"]
    return np.asarray(image.getpixel((round(rect["x"] + local_x), round(rect["y"] + local_y))), dtype=np.int16)


sample_differences = {}
browser, native = screenshots["initial"]
for key, local_x, local_y, label in (
    ("alpha-avatar", 48, 48, "opacity-colored-center"),
    ("alpha-avatar", 20, 20, "opacity-image-interior"),
    ("contain-padded", 50, 8, "asymmetric-padding-fill"),
    ("contain-padded", 1, 1, "rounded-padded-corner"),
    ("contain-padded", 0, 40, "single-border"),
    ("empty-content", 16, 16, "zero-content-background"),
):
    difference = int(np.abs(pixel(browser, key, local_x, local_y) - pixel(native, key, local_x, local_y)).max())
    sample_differences[label] = difference
    require(difference <= 8, f"{label}: browser/native sampled color differs by {difference}")

result = {
    "geometryMaxPixels": max(max(delta.values()) for delta in rect_errors.values()),
    "geometryDeltas": rect_errors,
    "rgbMeanDifferenceByImage": pixel_means,
    "sampleMaxChannelDifference": sample_differences,
    "alphaComparison": "RGB channels compared after whole-element compositing; framebuffer alpha is backend-specific",
}
(ARTIFACTS / "k4-image-comparison.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
