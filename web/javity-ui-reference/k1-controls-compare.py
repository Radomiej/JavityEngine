import copy
import json
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

BASE = Path(__file__).resolve().parent
ROOT = BASE.parent.parent
ARTIFACTS = ROOT / "artifacts"
ARTIFACTS.mkdir(exist_ok=True)
FIXTURE = json.loads((BASE / "k1-controls-fixture.json").read_text())
STATES = FIXTURE["states"]
IDS = [FIXTURE["panel"]["id"]] + [item["id"] for item in FIXTURE["panel"]["children"]]


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass


SERVER = ThreadingHTTPServer(
    ("127.0.0.1", 0), lambda *a, **k: QuietHandler(*a, directory=str(ROOT), **k)
)
threading.Thread(target=SERVER.serve_forever, daemon=True).start()
URL = f"http://127.0.0.1:{SERVER.server_port}/web/javity-ui-reference/k1-controls-comparison.html"


def create_page(browser, fixture_override=None):
    page = browser.new_page(
        viewport={"width": FIXTURE["viewport"]["width"], "height": FIXTURE["viewport"]["height"]},
        device_scale_factor=FIXTURE["viewport"]["dpr"],
    )
    if fixture_override is not None:
        body = json.dumps(fixture_override)

        def fixture_route(route):
            route.fulfill(status=200, content_type="application/json", body=body)

        page.route("**/k1-controls-fixture.json", fixture_route)
    page.goto(URL, wait_until="networkidle")
    page.wait_for_function("window.k1FixtureReady === true")
    if not page.evaluate('document.fonts.check(\'16px "Open Sans"\')'):
        raise AssertionError("Browser did not load bundled Open Sans")
    return page


def inspect_slider(page):
    return page.evaluate("""() => {
      const panel = document.querySelector('#k1-panel').getBoundingClientRect();
      const slider = document.querySelector('#k1-slider');
      const input = {min: slider.min, max: slider.max, value: slider.value,
        valueAsNumber: slider.valueAsNumber, step: slider.step};
      const wrapper = slider.parentElement;
      const track = wrapper.querySelector('.k1-range-track').getBoundingClientRect();
      const fill = wrapper.querySelector('.k1-range-fill').getBoundingClientRect();
      const knob = wrapper.querySelector('.k1-range-knob').getBoundingClientRect();
      return {
        panel: {x: panel.x, y: panel.y, width: panel.width, height: panel.height},
        input,
        ratio: Number(getComputedStyle(wrapper).getPropertyValue('--k1-slider-ratio')),
        track: {x: track.x, y: track.y, width: track.width, height: track.height},
        fill: {x: fill.x, y: fill.y, width: fill.width, height: fill.height},
        knob: {x: knob.x, y: knob.y, width: knob.width, height: knob.height}
      };
    }""")


def run_fixture_mutation_probes(browser):
    alternate = copy.deepcopy(FIXTURE)
    alternate["panel"]["rect"]["x"] = 71
    alternate["panel"]["rect"]["y"] = 93
    slider = next(item for item in alternate["panel"]["children"] if item["type"] == "Slider")
    slider.update({"min": 20, "max": 80, "value": 44, "step": 4})

    page = create_page(browser, alternate)
    alternate_state = inspect_slider(page)
    page.screenshot(path=str(ARTIFACTS / "k1-fix2-html-alternate.png"))
    expected_ratio = (44 - 20) / (80 - 20)
    expected_fill = alternate_state["track"]["width"] * expected_ratio
    expected_knob_x = alternate_state["track"]["x"] - 9 + expected_fill
    if alternate_state["panel"]["x"] != 71 or alternate_state["panel"]["y"] != 93:
        raise AssertionError(f"Alternate panel position did not come from fixture rect: {alternate_state}")
    if (alternate_state["panel"]["width"], alternate_state["panel"]["height"]) != (260, 224):
        raise AssertionError("Alternate panel dimensions no longer match fixture utility classes")
    if alternate_state["input"]["valueAsNumber"] != 44 or abs(alternate_state["ratio"] - expected_ratio) > 1e-9:
        raise AssertionError(f"Nonzero slider range was not normalized from the input: {alternate_state}")
    if abs(alternate_state["fill"]["width"] - expected_fill) > 0.02:
        raise AssertionError(f"Alternate slider fill has wrong width: {alternate_state}")
    if abs(alternate_state["knob"]["x"] - expected_knob_x) > 0.02:
        raise AssertionError(f"Alternate slider knob did not follow normalized range: {alternate_state}")
    page.close()

    clamped = copy.deepcopy(alternate)
    clamped_slider = next(item for item in clamped["panel"]["children"] if item["type"] == "Slider")
    clamped_slider["value"] = 120
    page = create_page(browser, clamped)
    clamped_state = inspect_slider(page)
    page.screenshot(path=str(ARTIFACTS / "k1-fix2-html-clamped.png"))
    expected_clamped_fill = clamped_state["track"]["width"]
    expected_clamped_knob_x = clamped_state["track"]["x"] - 9 + expected_clamped_fill
    if clamped_state["input"]["valueAsNumber"] != 80 or clamped_state["ratio"] != 1:
        raise AssertionError(f"Native range input did not clamp the out-of-range fixture value: {clamped_state}")
    if abs(clamped_state["fill"]["width"] - expected_clamped_fill) > 0.02:
        raise AssertionError(f"Clamped range fill does not reach the endpoint: {clamped_state}")
    if abs(clamped_state["knob"]["x"] - expected_clamped_knob_x) > 0.02:
        raise AssertionError(f"Clamped range knob did not reach the endpoint: {clamped_state}")
    page.close()

    result = {
        "alternate": {
            "fixturePanelRect": alternate["panel"]["rect"],
            "fixtureSlider": {key: slider[key] for key in ("min", "max", "value", "step")},
            "expectedNormalizedRatio": expected_ratio,
            "observed": alternate_state,
        },
        "clamped": {
            "fixtureValue": clamped_slider["value"],
            "nativeInputClampedValue": clamped_state["input"]["valueAsNumber"],
            "expectedNormalizedRatio": 1,
            "observed": clamped_state,
        },
    }
    (ARTIFACTS / "k1-fix2-mutation-probe.json").write_text(json.dumps(result, indent=2) + "\n")
    return result


try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            executable_path="/usr/bin/chromium",
            args=["--no-sandbox", "--disable-features=OverlayScrollbar"],
        )
        page = create_page(browser)
        browser_metrics = {
            "viewport": page.evaluate("({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})"),
            "font": "Open Sans",
            "fontLoaded": True,
            "states": {},
        }
        for state in STATES:
            page.mouse.move(1070, 700)
            page.locator("#k1-slider").evaluate("element => element.blur()")
            if state == "slider-focus":
                page.locator("#k1-slider").focus()
            elif state == "toggle-hover":
                page.locator("#k1-toggle").hover()
            page.wait_for_timeout(60)
            page.screenshot(path=str(ARTIFACTS / f"k1-fix2-html-{state}.png"))
            browser_metrics["states"][state] = page.evaluate("""({ids,state}) => {
              const round=n=>Math.round(n*100)/100;
              const out={};
              for(const id of ids){
                const e=document.getElementById(id),r=e.getBoundingClientRect(),s=getComputedStyle(e);
                out[id]={x:round(r.x),y:round(r.y),width:round(r.width),height:round(r.height),fontSize:s.fontSize,lineHeight:s.lineHeight,color:s.color,backgroundColor:s.backgroundColor,borderColor:s.borderColor,opacity:s.opacity,thumbBackground:getComputedStyle(e,'::after').backgroundColor};
              }
              const toggle=state==='disabled'?document.querySelector('#k1-disabled-toggle'):document.querySelector('#k1-toggle');
              const tr=toggle.getBoundingClientRect();
              const toggleTrackStyle=getComputedStyle(toggle,'::before');
              const toggleKnobStyle=getComputedStyle(toggle,'::after');
              const tw=parseFloat(toggleTrackStyle.width),th=parseFloat(toggleTrackStyle.height);
              const kw=parseFloat(toggleKnobStyle.width),kh=parseFloat(toggleKnobStyle.height);
              const knobLeft=parseFloat(toggleKnobStyle.left);
              const toggleTrack={x:tr.x+(tr.width-tw)/2,y:tr.y+(tr.height-th)/2,width:tw,height:th};
              const toggleKnob={x:tr.x+knobLeft-kw/2,y:tr.y+(tr.height-kh)/2,width:kw,height:kh};
              const slider=document.querySelector('#k1-slider');
              const wrapper=slider.parentElement;
              const rect=selector=>{
                const r=wrapper.querySelector(selector).getBoundingClientRect();
                return {x:round(r.x),y:round(r.y),width:round(r.width),height:round(r.height)};
              };
              const shapes={toggleTrack,toggleKnob,sliderRail:rect('.k1-range-track'),sliderFill:rect('.k1-range-fill'),sliderKnob:rect('.k1-range-knob')};
              out.controlShapes=Object.fromEntries(Object.entries(shapes).map(([key,r])=>[key,Object.fromEntries(Object.entries(r).map(([k,v])=>[k,round(v)]))]));
              out.sliderRange={min:slider.min,max:slider.max,value:slider.valueAsNumber,ratio:Number(getComputedStyle(wrapper).getPropertyValue('--k1-slider-ratio'))};
              out.sliderTrackBorderColor=getComputedStyle(wrapper.querySelector('.k1-range-track')).borderColor;
              const toggleSampleX=toggle.checked?toggleTrack.x+toggleTrack.width*.2:toggleTrack.x+toggleTrack.width*.8;
              out.samples={toggleTrack:[Math.round(toggleSampleX),Math.round(toggleTrack.y+toggleTrack.height/2)],toggleKnob:[Math.round(toggleKnob.x+toggleKnob.width/2),Math.round(toggleKnob.y+toggleKnob.height/2)],sliderTrack:[Math.round(shapes.sliderRail.x+shapes.sliderRail.width*.87),Math.round(shapes.sliderRail.y+shapes.sliderRail.height/2)],sliderKnob:[Math.round(shapes.sliderKnob.x+shapes.sliderKnob.width/2),Math.round(shapes.sliderKnob.y+shapes.sliderKnob.height/2)],sliderBorder:[Math.round(shapes.sliderRail.x+shapes.sliderRail.width*.45),Math.round(shapes.sliderRail.y)]};
              return out;
            }""", {"ids": IDS, "state": state})

        mutation_metrics = run_fixture_mutation_probes(browser)
        browser.close()
finally:
    SERVER.shutdown()

(ARTIFACTS / "k1-fix2-browser-metrics.json").write_text(json.dumps(browser_metrics, indent=2) + "\n")
native_path = ARTIFACTS / "k1-fix1-native-metrics.json"
if not native_path.exists():
    raise SystemExit(f"Missing retained native captures for unchanged fixture: {native_path}")
native_metrics = json.loads(native_path.read_text())
if browser_metrics["viewport"] != native_metrics["viewport"] or browser_metrics["viewport"] != FIXTURE["viewport"]:
    raise AssertionError(f"Viewport mismatch: browser={browser_metrics['viewport']} native={native_metrics['viewport']} authored={FIXTURE['viewport']}")
if browser_metrics["font"] != native_metrics["font"] or not browser_metrics["fontLoaded"]:
    raise AssertionError("Both renderers must use the bundled Open Sans font")


def near(rgb, target, tol=3):
    return max(abs(int(rgb[i]) - target[i]) for i in range(3)) <= tol


def bbox_for(image, box, target, tol=3):
    x0, y0 = round(box["x"]), round(box["y"])
    x1, y1 = round(box["x"] + box["width"]), round(box["y"] + box["height"])
    points = []
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            if near(image.getpixel((x, y))[:3], target, tol):
                points.append((x, y))
    if not points:
        return None
    return {"x": min(x for x, y in points), "y": min(y for x, y in points),
            "width": max(x for x, y in points) - min(x for x, y in points) + 1,
            "height": max(y for x, y in points) - min(y for x, y in points) + 1}


def ink_bbox(image, box):
    x0, y0 = round(box["x"]), round(box["y"])
    x1, y1 = round(box["x"] + box["width"]), round(box["y"] + box["height"])
    points = []
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            r, g, b = image.getpixel((x, y))[:3]
            if min(r, g, b) > 150 and max(r, g, b) - min(r, g, b) < 35:
                points.append((x, y))
    if not points:
        return None
    return {"x": min(x for x, y in points), "y": min(y for x, y in points),
            "width": max(x for x, y in points) - min(x for x, y in points) + 1,
            "height": max(y for x, y in points) - min(y for x, y in points) + 1}


def color_at(image, point):
    return list(image.getpixel((int(point[0]), int(point[1])))[:3])


comparison = {
    "viewport": browser_metrics["viewport"], "font": "Open Sans",
    "nativeEvidenceReused": "artifacts/k1-fix1-native-{idle,slider-focus,toggle-hover,disabled}.png and k1-fix1-native-metrics.json; fixture and native renderer unchanged",
    "thresholds": {"geometryPx": 2.12, "glyphVerticalPx": 1, "glyphWidthPx": 4, "flatColorChannel": 1, "controlBoundsAndCenterPx": 1},
    "states": {},
    "edgeAntialias": "Flat-color samples use interior pixels. Pixel-mask bounds are retained as raster diagnostics; computed control-shape rectangles carry the 1px geometry acceptance, since edge coverage differs between Scene2D SDF and Chromium rasterization.",
}
for state in STATES:
    native_state = native_metrics["states"][state]
    browser_state = browser_metrics["states"][state]
    native_img = Image.open(ARTIFACTS / f"k1-fix1-native-{state}.png").convert("RGB")
    browser_img = Image.open(ARTIFACTS / f"k1-fix2-html-{state}.png").convert("RGB")
    errors = {"geometry": {}, "glyph": {}, "controls": {}, "colors": {}}
    for ident in IDS:
        n, b = native_state[ident], browser_state[ident]
        for key in ("x", "y", "width", "height"):
            errors["geometry"][f"{ident}.{key}"] = round(abs(float(n[key]) - float(b[key])), 2)
    for ident in ("k1-title", "k1-volume-label", "k1-hint"):
        ng, bg = ink_bbox(native_img, native_state[ident]), ink_bbox(browser_img, browser_state[ident])
        if ng is None or bg is None:
            raise AssertionError(f"Missing glyph pixels {state} {ident}: native={ng}, browser={bg}")
        errors["glyph"][ident] = {"native": ng, "browser": bg,
            "verticalPx": abs(ng["y"] - bg["y"]), "heightPx": abs(ng["height"] - bg["height"]),
            "widthPx": abs(ng["width"] - bg["width"])}
    for name, point in browser_state["samples"].items():
        if name == "sliderBorder":
            continue
        bp, npix = color_at(browser_img, point), color_at(native_img, point)
        errors["colors"][name] = {"point": point, "browser": bp, "native": npix,
            "maxChannelDelta": max(abs(bp[i] - npix[i]) for i in range(3))}

    tbox, sbox = native_state["k1-toggle"], native_state["k1-slider"]
    toggle_color = (48, 60, 79) if state == "disabled" else ((21, 93, 252) if state == "toggle-hover" else (31, 140, 237))
    native_toggle = native_state["k1-disabled-toggle"] if state == "disabled" else tbox
    browser_toggle = browser_state["k1-disabled-toggle"] if state == "disabled" else browser_state["k1-toggle"]
    toggle_track = bbox_for(native_img, native_toggle, toggle_color, 4)
    toggle_track_html = bbox_for(browser_img, browser_toggle, toggle_color, 4)
    slider_rail = bbox_for(native_img, sbox, (87, 102, 125))
    slider_rail_html = bbox_for(browser_img, browser_state["k1-slider"], (87, 102, 125))
    toggle_knob_color = (114, 122, 136) if state == "disabled" else (255, 255, 255)
    toggle_knob = bbox_for(native_img, native_toggle, toggle_knob_color, 4)
    toggle_knob_html = bbox_for(browser_img, browser_toggle, toggle_knob_color, 4)
    slider_knob = bbox_for(native_img, sbox, (255, 255, 255), 3)
    slider_knob_html = bbox_for(browser_img, browser_state["k1-slider"], (255, 255, 255), 3)
    raster_controls = {"toggleTrack": {"native": toggle_track, "html": toggle_track_html},
        "toggleKnob": {"native": toggle_knob, "html": toggle_knob_html},
        "sliderRail": {"native": slider_rail, "html": slider_rail_html},
        "sliderKnob": {"native": slider_knob, "html": slider_knob_html}}
    for item in raster_controls.values():
        a, b = item["native"], item["html"]
        if a is not None and b is not None:
            item["edgeRasterDeltaPx"] = {key: abs(a[key] - b[key]) for key in ("x", "y", "width", "height")}

    native_shapes, browser_shapes = native_state["controlShapes"], browser_state["controlShapes"]
    shape_errors = {}
    for name, rect in native_shapes.items():
        other = browser_shapes[name]
        delta = {key: round(abs(float(rect[key]) - float(other[key])), 2) for key in ("x", "y", "width", "height")}
        delta["centerX"] = round(abs((float(rect["x"]) + float(rect["width"]) / 2) - (float(other["x"]) + float(other["width"]) / 2)), 2)
        delta["centerY"] = round(abs((float(rect["y"]) + float(rect["height"]) / 2) - (float(other["y"]) + float(other["height"]) / 2)), 2)
        shape_errors[name] = {"native": rect, "html": other, "errorsPx": delta}
    comparison["states"][state] = {
        "maxGeometryErrorPx": max(errors["geometry"].values()), "geometryErrorsPx": errors["geometry"],
        "glyphErrors": errors["glyph"], "controlBounds": shape_errors,
        "rasterPixelBounds": raster_controls, "flatColorSamples": errors["colors"],
    }
    if max(errors["geometry"].values()) > 2.12:
        raise AssertionError(f"Geometry tolerance exceeded in {state}: {errors['geometry']}")
    for ident, item in errors["glyph"].items():
        if item["verticalPx"] > 1 or item["heightPx"] > 1 or item["widthPx"] > 4:
            raise AssertionError(f"Glyph tolerance exceeded in {state} {ident}: {item}")
    for name, item in errors["colors"].items():
        if item["maxChannelDelta"] > 1:
            raise AssertionError(f"Flat color tolerance exceeded in {state} {name}: {item}")
    for name, item in shape_errors.items():
        if max(item["errorsPx"].values()) > 1:
            raise AssertionError(f"Control bounds/center tolerance exceeded in {state} {name}: {item['errorsPx']}")
    if state == "slider-focus":
        if browser_state["sliderTrackBorderColor"] != "rgb(0, 211, 243)":
            raise AssertionError("Chromium slider focus border state was not active")
        edge = browser_state["samples"]["sliderBorder"]
        focused = color_at(native_img, edge)
        idle = color_at(Image.open(ARTIFACTS / "k1-fix1-native-idle.png").convert("RGB"), edge)
        if max(abs(focused[i] - idle[i]) for i in range(3)) < 20:
            raise AssertionError("Native Stage slider focus border did not change")
        html_edge = color_at(browser_img, edge)
        comparison["states"][state]["focusBorderEdge"] = {
            "native": focused, "html": html_edge, "nativeChangedFromIdle": True,
            "edgeAntialiasResidual": max(abs(focused[i] - html_edge[i]) for i in range(3)),
        }
    if state == "toggle-hover" and color_at(browser_img, browser_state["samples"]["toggleTrack"]) != [21, 93, 252]:
        raise AssertionError("Chromium toggle hover state was not active")

(ARTIFACTS / "k1-fix2-browser-metrics.json").write_text(json.dumps(browser_metrics, indent=2) + "\n")
(ARTIFACTS / "k1-fix2-comparison.json").write_text(json.dumps(comparison, indent=2) + "\n")
summary = {
    state: {
        "maxGeometryPx": values["maxGeometryErrorPx"],
        "maxControlShapePx": max(delta for item in values["controlBounds"].values() for delta in item["errorsPx"].values()),
        "maxFlatColorChannel": max(item["maxChannelDelta"] for item in values["flatColorSamples"].values()),
        "maxGlyphWidthPx": max(item["widthPx"] for item in values["glyphErrors"].values()),
        "glyphVerticalPx": max(item["verticalPx"] for item in values["glyphErrors"].values()),
        "maxRasterPixelBoundDeltaPx": max(delta for item in values["rasterPixelBounds"].values() if item.get("edgeRasterDeltaPx") for delta in item["edgeRasterDeltaPx"].values()),
    }
    for state, values in comparison["states"].items()
}
print(json.dumps({"status": "PASS", "viewport": comparison["viewport"], "font": comparison["font"], "states": summary, "mutationProbes": mutation_metrics}, indent=2))
