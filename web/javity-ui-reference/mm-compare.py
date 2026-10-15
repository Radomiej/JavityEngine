#!/usr/bin/env python3
import json
import socket
import subprocess
import time
from pathlib import Path

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "web/javity-ui-reference"
ARTIFACTS = ROOT / "artifacts/mm-systems"
FIXTURE = json.loads((REFERENCE / "mm-fixture.json").read_text())
STATES = tuple(FIXTURE["states"])
NATIVE_PNG = {
    "idle": "01-idle.png", "hit": "03-hit-health-trail.png", "settings": "05-settings.png",
    "loading": "06-loading-cancel.png", "arena": "07-arena.png",
}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def walk(node, parent=None, index=0):
    yield node, parent, index
    for child_index, child in enumerate(node.get("children", [])):
        yield from walk(child, node, child_index)


def all_text(node):
    if node["type"] == "Text":
        return True
    children = node.get("children", [])
    return bool(children) and all(all_text(child) for child in children)


def bbox_color(image, rect, target, tolerance=4):
    x0, y0 = (round(rect[key]) for key in ("x", "y"))
    x1 = round(rect["x"] + rect["width"])
    y1 = round(rect["y"] + rect["height"])
    points = []
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            px = image.getpixel((x, y))[:3]
            if max(abs(px[i] - target[i]) for i in range(3)) <= tolerance:
                points.append((x, y))
    if not points:
        return None
    return {"x": min(p[0] for p in points), "y": min(p[1] for p in points),
            "width": max(p[0] for p in points) - min(p[0] for p in points) + 1,
            "height": max(p[1] for p in points) - min(p[1] for p in points) + 1}


def ink_bbox(image, rect, target):
    return bbox_color(image, rect, target, tolerance=110)


def pixel(image, point):
    return list(image.getpixel((int(point[0]), int(point[1])))[:3])


def color_span(image, rect, expected, tolerance=4):
    """Measure an actual progress segment from the screenshot center scanline."""
    y = int(float(rect["y"]) + float(rect["height"]) / 2)
    x0 = max(0, int(float(rect["x"])))
    x1 = min(image.width, int(np.ceil(float(rect["x"]) + float(rect["width"]))))
    matches = [
        x for x in range(x0, x1)
        if max(abs(image.getpixel((x, y))[i] - expected[i]) for i in range(3)) <= tolerance
    ]
    if not matches:
        return None
    return {"start": min(matches), "endExclusive": max(matches) + 1, "y": y}


def compare_box(a, b):
    return {key: round(abs(float(a[key]) - float(b[key])), 2) for key in ("x", "y", "width", "height")}


def control_shapes(toggle_actor, slider_actor, value, checked):
    x, y, width, height = (float(toggle_actor[key]) for key in ("x", "y", "width", "height"))
    track_height = min(height * .62, 24)
    track_width = min(width, track_height * 1.75)
    tx, ty = x + (width - track_width) / 2, y + (height - track_height) / 2
    toggle_knob = max(2, track_height - 4)
    toggle = {
        "toggleTrack": {"x": tx, "y": ty, "width": track_width, "height": track_height},
        "toggleKnob": {"x": tx + (track_width - toggle_knob - 2 if checked else 2),
                       "y": ty + 2, "width": toggle_knob, "height": toggle_knob},
    }
    x, y, width, height = (float(slider_actor[key]) for key in ("x", "y", "width", "height"))
    rail_height = min(6, max(3, height * .22))
    slider_knob = min(18, max(10, height * .72))
    usable = max(0, width - slider_knob)
    slider_y = y + height / 2
    ratio = max(0, min(1, float(value)))
    toggle.update({
        "sliderTrack": {"x": x + slider_knob / 2, "y": slider_y - rail_height / 2,
                        "width": usable, "height": rail_height},
        "sliderFill": {"x": x + slider_knob / 2, "y": slider_y - rail_height / 2,
                       "width": usable * ratio, "height": rail_height},
        "sliderKnob": {"x": x + usable * ratio, "y": slider_y - slider_knob / 2,
                       "width": slider_knob, "height": slider_knob},
    })
    return toggle


def main():
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    subprocess.run(["npm", "run", "css"], cwd=REFERENCE, check=True, stdout=subprocess.DEVNULL)
    port = free_port()
    server = subprocess.Popen(["python3", "-m", "http.server", str(port), "--bind", "127.0.0.1"], cwd=ROOT,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        time.sleep(.3)
        browser_states = {}
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(executable_path="/usr/bin/chromium", headless=True,
                                                 args=["--no-sandbox", "--disable-gpu"])
            page = browser.new_page(viewport={"width": FIXTURE["viewport"]["width"],
                                              "height": FIXTURE["viewport"]["height"]},
                                    device_scale_factor=FIXTURE["viewport"]["dpr"])
            for state in STATES:
                page.goto(f"http://127.0.0.1:{port}/web/javity-ui-reference/mm-reference.html?state={state}",
                          wait_until="networkidle")
                page.wait_for_function("window.mmReady === true")
                page.screenshot(path=str(ARTIFACTS / f"mm-browser-{state}.png"))
                browser_states[state] = page.evaluate("window.mmMetrics()")
            browser.close()
        (ARTIFACTS / "mm-browser-geometry.json").write_text(
            json.dumps({"states": browser_states}, indent=2) + "\n")

        native_path = ARTIFACTS / "native-geometry.json"
        require(native_path.is_file(), f"Missing native geometry evidence: {native_path}")
        native = json.loads(native_path.read_text())
        smoke_path = ARTIFACTS / "native-smoke.json"
        require(smoke_path.is_file(), f"Missing native smoke evidence: {smoke_path}")
        smoke = json.loads(smoke_path.read_text())
        require(smoke.get("status") == "PASS", "Native smoke evidence is not PASS")
        expected_ids = {node["key"] for node, _, _ in walk(FIXTURE["tree"])}
        require(set(browser_states) == set(STATES), "Browser capture states differ from fixture states")
        require(set(native.get("states", {})) == set(STATES), "Native capture states differ from fixture states")
        require(browser_states["idle"]["viewport"] == FIXTURE["viewport"], "Browser viewport/DPR differs from fixture")
        require(native.get("viewport") == FIXTURE["viewport"], "Native viewport/DPR differs from fixture")
        require(smoke.get("font") == FIXTURE["font"], "Native smoke used a different declared font")
        for state in STATES:
            browser_elements = browser_states[state].get("elements", {})
            native_elements = native["states"][state].get("elements", {})
            require(set(browser_elements) == expected_ids,
                    f"{state}: browser element IDs differ from complete fixture: missing={sorted(expected_ids-set(browser_elements))}")
            require(set(native_elements) == expected_ids,
                    f"{state}: native element IDs differ from complete fixture: missing={sorted(expected_ids-set(native_elements))}")
            require(browser_states[state]["viewport"] == FIXTURE["viewport"], f"{state}: browser viewport changed")
            require(browser_states[state]["openSansLoaded"], f"{state}: bundled Open Sans did not load")
            for node, _, _ in walk(FIXTURE["tree"]):
                if node["type"] != "Text":
                    continue
                key = node["key"]
                require(browser_elements[key].get("text") == native_elements[key].get("text"),
                        f"{state}.{key}: rendered text differs native={native_elements[key].get('text')!r} browser={browser_elements[key].get('text')!r}")
                require("Open Sans" in browser_elements[key].get("font", ""), f"{state}.{key}: text is not rendered in Open Sans")

        geometry = {}
        text_geometry = {}
        glyphs = {}
        colors = {}
        controls = {}
        progress_visuals = {}
        image_diagnostics = {}
        for state in STATES:
            browser_elements = browser_states[state]["elements"]
            native_elements = native["states"][state]["elements"]
            geometry[state] = {}
            text_geometry[state] = {}
            for node, parent, index in walk(FIXTURE["tree"]):
                key = node["key"]
                delta = compare_box(browser_elements[key]["rect"], native_elements[key]["rect"])
                if node["type"] == "Text":
                    text_geometry[state][key] = delta
                    require(delta["y"] <= 1 and delta["height"] <= 1,
                            f"{state}.{key}: text line box vertical geometry differs: {delta}")
                    right_aligned = parent is not None and "justify-between" in parent.get("classes", "") and index == len(parent.get("children", [])) - 1
                    if right_aligned:
                        be = browser_elements[key]["rect"]
                        ne = native_elements[key]["rect"]
                        anchor_delta = abs((be["x"] + be["width"]) - (ne["x"] + ne["width"]))
                        text_geometry[state][key]["anchor"] = "right"
                    else:
                        anchor_delta = delta["x"]
                        text_geometry[state][key]["anchor"] = "left"
                    text_geometry[state][key]["anchorErrorPx"] = round(anchor_delta, 2)
                    require(anchor_delta <= 1,
                            f"{state}.{key}: aligned text anchor differs by {anchor_delta:.2f}px")
                elif node["type"] == "View" and all_text(node):
                    text_geometry[state][key] = {**delta, "naturalTextContainer": True}
                else:
                    geometry[state][key] = delta
                    require(max(delta.values()) <= 1,
                            f"{state}.{key}: fixed actor geometry differs by {max(delta.values()):.2f}px: {delta}")

            browser_image = Image.open(ARTIFACTS / f"mm-browser-{state}.png").convert("RGB")
            native_image = Image.open(ARTIFACTS / NATIVE_PNG[state]).convert("RGB")
            require(browser_image.size == native_image.size == (FIXTURE["viewport"]["width"], FIXTURE["viewport"]["height"]),
                    f"{state}: screenshot pixel dimensions differ from authored viewport")

            state_progress = {}
            for node, _, _ in walk(FIXTURE["tree"]):
                if node["type"] != "Progress":
                    continue
                key = node["key"]
                target = float(FIXTURE["states"][state][node["props"]["valueSignal"]])
                checkpoint = FIXTURE["states"][state].get("progress", {}).get(key, {})
                displayed = float(checkpoint.get("displayed", target))
                trail = float(checkpoint.get("trail", displayed))
                native_element = native_elements[key]
                browser_element = browser_elements[key]
                actual = native_element.get("progress")
                require(actual is not None, f"{state}.{key}: native progress values are missing")
                for name, expected in (("target", target), ("displayed", displayed), ("trail", trail)):
                    require(abs(float(actual.get(name, float("nan"))) - expected) <= .002,
                            f"{state}.{key}: native {name} differs from authored checkpoint: expected={expected}, actual={actual.get(name)}")
                browser_parts = browser_element.get("progress")
                require(browser_parts is not None, f"{state}.{key}: browser fill/trail geometry is missing")
                rect = native_element["rect"]
                expected_fill_end = float(rect["x"]) + float(rect["width"]) * displayed
                expected_trail_end = float(rect["x"]) + float(rect["width"]) * trail
                browser_fill = browser_parts["displayed"]
                browser_trail = browser_parts["trail"]
                require(abs(float(browser_fill["width"]) - float(rect["width"]) * displayed) <= .1
                        and abs(float(browser_trail["width"]) - float(rect["width"]) * trail) <= .1,
                        f"{state}.{key}: browser fill/trail widths do not follow the authored checkpoint")
                fill_native = color_span(native_image, rect, (0, 211, 243))
                fill_browser = color_span(browser_image, rect, (0, 211, 243))
                require(fill_native is not None and fill_browser is not None,
                        f"{state}.{key}: cyan progress fill is missing from a real screenshot")
                fill_delta = abs(fill_native["endExclusive"] - fill_browser["endExclusive"])
                expected_fill_pixel = int(np.floor(expected_fill_end + .5))
                fill_checkpoint_error = max(abs(fill_native["endExclusive"] - expected_fill_pixel),
                                            abs(fill_browser["endExclusive"] - expected_fill_pixel))
                require(fill_delta <= 1 and fill_checkpoint_error <= 1,
                        f"{state}.{key}: actual cyan fill endpoints differ: native={fill_native}, browser={fill_browser}, expectedEnd={expected_fill_end:.2f}")
                visual = {"target": target, "displayed": displayed, "trail": trail,
                          "nativeFill": fill_native, "browserFill": fill_browser,
                          "fillEndDeltaPx": fill_delta}
                if trail > displayed + .002:
                    trail_color = (255, 185, 0) if target < displayed else (0, 188, 125)
                    trail_native = color_span(native_image, rect, trail_color)
                    trail_browser = color_span(browser_image, rect, trail_color)
                    require(trail_native is not None and trail_browser is not None,
                            f"{state}.{key}: expected colored progress trail is missing")
                    trail_delta = abs(trail_native["endExclusive"] - trail_browser["endExclusive"])
                    expected_trail_pixel = int(np.floor(expected_trail_end + .5))
                    trail_checkpoint_error = max(abs(trail_native["endExclusive"] - expected_trail_pixel),
                                                 abs(trail_browser["endExclusive"] - expected_trail_pixel))
                    sample_x = (expected_fill_end + expected_trail_end) / 2
                    sample_y = float(rect["y"]) + float(rect["height"]) / 2
                    native_color = pixel(native_image, (sample_x, sample_y))
                    browser_color = pixel(browser_image, (sample_x, sample_y))
                    require(trail_delta <= 1 and trail_checkpoint_error <= 1,
                            f"{state}.{key}: actual trail endpoints differ: native={trail_native}, browser={trail_browser}, expectedEnd={expected_trail_end:.2f}")
                    require(max(abs(native_color[i] - trail_color[i]) for i in range(3)) <= 2
                            and max(abs(browser_color[i] - trail_color[i]) for i in range(3)) <= 2,
                            f"{state}.{key}: native/browser trail tint mismatch: expected={trail_color}, native={native_color}, browser={browser_color}")
                    visual.update({"nativeTrail": trail_native, "browserTrail": trail_browser,
                                   "trailEndDeltaPx": trail_delta, "trailColor": trail_color,
                                   "nativeTrailSample": native_color, "browserTrailSample": browser_color})
                state_progress[key] = visual
            progress_visuals[state] = state_progress

            state_glyphs = {}
            for key in ("game-title", "drone-state", "mute-label"):
                box = browser_elements[key]["rect"]
                color = browser_elements[key].get("colorRgb")
                require(color is not None and len(color) >= 3, f"{state}.{key}: browser text color is unavailable")
                ng = ink_bbox(native_image, native_elements[key]["rect"], color)
                bg = ink_bbox(browser_image, box, color)
                require(ng is not None and bg is not None, f"{state}.{key}: glyph ink missing native={ng} browser={bg}")
                item = {"native": ng, "browser": bg,
                        "verticalPx": abs(ng["y"] - bg["y"]), "heightPx": abs(ng["height"] - bg["height"]),
                        "widthPx": abs(ng["width"] - bg["width"])}
                state_glyphs[key] = item
                require(item["verticalPx"] <= 1 and item["heightPx"] <= 1 and item["widthPx"] <= 4,
                        f"{state}.{key}: selected short-glyph raster bounds differ: {item}")
            glyphs[state] = state_glyphs

            probes = {}
            samples = {
                "page": ((5, 5), (7, 17, 28)),
                "scenePanel": ((25, 25), (12, 29, 44)),
                "targetInset": ((50, 125), (16, 40, 58)),
            }
            if state == "idle":
                samples.update({"fireButton": ((45, 260), (8, 126, 131)),
                                "healthFill": ((100, 232), (0, 211, 243)),
                                "toggleOffTrack": ((322, 527), (107, 120, 140)),
                                "sliderRail": ((260, 594), (87, 102, 125))})
            elif state == "hit":
                samples["healthTrack"] = ((300, 232), (38, 46, 56))
            elif state == "settings":
                samples.update({"toggleOnTrack": ((300, 527), (31, 140, 237)),
                                "sliderFill": ((60, 594), (0, 211, 243)),
                                "sliderRail": ((200, 594), (87, 102, 125))})
            for name, (point, expected) in samples.items():
                bp, npix = pixel(browser_image, point), pixel(native_image, point)
                channel_delta = max(abs(bp[i] - npix[i]) for i in range(3))
                require(max(abs(npix[i] - expected[i]) for i in range(3)) <= 2,
                        f"{state}.{name}: native interior sample no longer matches expected skin color {expected}, got {npix}")
                require(max(abs(bp[i] - expected[i]) for i in range(3)) <= 2,
                        f"{state}.{name}: browser interior sample differs from expected skin color {expected}, got {bp}")
                require(channel_delta <= 2, f"{state}.{name}: pixel channel difference {channel_delta}px: native={npix} browser={bp}")
                probes[name] = {"point": point, "native": npix, "browser": bp, "expected": expected,
                                "maxChannelDelta": channel_delta}
            colors[state] = probes

            if state in ("idle", "settings"):
                native_shapes = control_shapes(native_elements["mute-toggle"]["rect"],
                                               native_elements["volume-slider"]["rect"],
                                               FIXTURE["states"][state]["volume"], FIXTURE["states"][state]["muted"])
                browser_shapes = browser_states[state]["controlShapes"]
                for name, native_shape in native_shapes.items():
                    browser_shape = browser_shapes[name]
                    delta = compare_box(native_shape, browser_shape)
                    require(max(delta.values()) <= 1, f"{state}.{name}: control shape geometry differs: {delta}")
                    controls.setdefault(state, {})[name] = {"nativeFormula": native_shape,
                                                             "browserComputed": browser_shape,
                                                             "deltaPx": delta}
                # Retain screenshot-derived edge masks as raster diagnostics; Scene2D SDF and CSS edge coverage differ.
                for name, ident, color in (("toggleTrack", "mute-toggle", (31, 140, 237) if state == "settings" else (107, 120, 140)),
                                           ("toggleKnob", "mute-toggle", (255, 255, 255)),
                                           ("sliderKnob", "volume-slider", (255, 255, 255))):
                    native_mask = bbox_color(native_image, native_elements[ident]["rect"], color)
                    browser_mask = bbox_color(browser_image, browser_elements[ident]["rect"], color)
                    require(native_mask is not None and browser_mask is not None,
                            f"{state}.{name}: rendered pixel mask missing native={native_mask} browser={browser_mask}")
                    mask_delta = compare_box(native_mask, browser_mask)
                    require(max(mask_delta.values()) <= 4,
                            f"{state}.{name}: screenshot-derived shape mask exceeds measured 4px antialias allowance: {mask_delta}")
                    controls[state][name]["rasterPixelBounds"] = {"native": native_mask, "browser": browser_mask,
                        "deltaPx": mask_delta}
                knob = browser_shapes["sliderKnob"]
                corners = [(knob["x"] + 1, knob["y"] + 1),
                           (knob["x"] + knob["width"] - 2, knob["y"] + 1),
                           (knob["x"] + 1, knob["y"] + knob["height"] - 2),
                           (knob["x"] + knob["width"] - 2, knob["y"] + knob["height"] - 2)]
                corner_evidence = {}
                for renderer_name, image in (("native", native_image), ("browser", browser_image)):
                    values = [pixel(image, point) for point in corners]
                    center = pixel(image, (knob["x"] + knob["width"] / 2, knob["y"] + knob["height"] / 2))
                    require(all(max(px) < 250 for px in values),
                            f"{state}.sliderKnob: {renderer_name} corners look square instead of circular: {values}")
                    require(min(center) >= 245, f"{state}.sliderKnob: {renderer_name} center is not white: {center}")
                    corner_evidence[renderer_name] = {"corners": values, "center": center}
                controls[state]["sliderKnobCornerGlyphGate"] = corner_evidence

            img_rect = browser_elements["pilot-image"]["rect"]
            x, y, width, height = (round(img_rect[n]) for n in ("x", "y", "width", "height"))
            b_crop = np.asarray(browser_image.crop((x + 2, y + 2, x + width - 2, y + height - 2)), dtype=np.int16)
            n_crop = np.asarray(native_image.crop((x + 2, y + 2, x + width - 2, y + height - 2)), dtype=np.int16)
            image_mean = float(np.abs(b_crop - n_crop).mean())
            image_diagnostics[state] = round(image_mean, 3)
            require(image_mean <= 4, f"{state}.pilot-image: cropped screenshot RGB mean error exceeds 4 ({image_mean:.3f})")

        # Natural text widths are reported separately because BitmapFont rounds glyph advances to integers,
        # while Chromium uses subpixel Open Sans advances; line boxes, anchors, and selected glyph ink are gated above.
        width_diagnostics = {}
        for state in STATES:
            for node, _, _ in walk(FIXTURE["tree"]):
                if node["type"] == "Text":
                    key = node["key"]
                    b = browser_states[state]["elements"][key]["rect"]["width"]
                    n = native["states"][state]["elements"][key]["rect"]["width"]
                    width_diagnostics[f"{state}.{key}"] = round(abs(b - n), 2)
        raster = {}
        for state in STATES:
            b = np.asarray(Image.open(ARTIFACTS / f"mm-browser-{state}.png").convert("RGB").crop((0, 0, 370, 660)), dtype=np.int16)
            n = np.asarray(Image.open(ARTIFACTS / NATIVE_PNG[state]).convert("RGB").crop((0, 0, 370, 660)), dtype=np.int16)
            raster[state] = round(float(np.abs(b - n).mean()), 3)

        report = {
            "status": "PASS", "viewport": FIXTURE["viewport"], "font": FIXTURE["font"],
            "states": list(STATES), "expectedElementIds": sorted(expected_ids),
            "thresholds": {"fixedActorGeometryPx": 1, "textLineBoxVerticalPx": 1,
                           "textAnchorPx": 1, "selectedGlyphVerticalAndHeightPx": 1,
                           "selectedGlyphWidthPx": 4, "flatColorChannel": 2,
                           "controlShapeBoundsPx": 1, "pilotCropRGBMean": 4},
            "fixedActorGeometryDeltaPx": geometry, "textLineBoxesAndAnchors": text_geometry,
            "selectedGlyphInkBounds": glyphs, "flatColorProbes": colors,
            "progressFillAndTrailPixels": progress_visuals,
            "nativeControlShapeBounds": controls, "pilotImageCropRgbMeanDifference": image_diagnostics,
            "textNaturalWidthDeltaPxDiagnostic": width_diagnostics,
            "sidebarRgbMeanAbsoluteDifferenceDiagnostic": raster,
            "rasterNote": "Whole-sidebar RGB is diagnostic only; acceptance uses fixed actor geometry, text anchors/line boxes, selected glyph ink, flat-color probes, control shape masks, and matched atlas crop pixels."
        }
        (ARTIFACTS / "mm-comparison.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps({"status": report["status"], "viewport": report["viewport"],
                          "maxFixedActorGeometryPx": max(max(d.values()) for state in geometry.values() for d in state.values()),
                          "maxTextWidthDiagnosticPx": max(width_diagnostics.values()),
                          "glyphs": glyphs, "colors": colors, "controls": controls,
                          "pilotImageCropRgbMeanDifference": image_diagnostics}, indent=2))
    finally:
        server.terminate()
        server.wait(timeout=5)


if __name__ == "__main__":
    main()
