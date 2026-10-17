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
FIXTURE = json.loads((BASE / "k2-wrap-fixture.json").read_text())


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass


SERVER = ThreadingHTTPServer(
    ("127.0.0.1", 0), lambda *args, **kwargs: QuietHandler(*args, directory=str(ROOT), **kwargs)
)
threading.Thread(target=SERVER.serve_forever, daemon=True).start()
URL = f"http://127.0.0.1:{SERVER.server_port}/web/javity-ui-reference/k2-wrap.html"
ROUND = lambda value: round(float(value), 2)
browser_metrics = {
    "viewport": FIXTURE["viewport"],
    "font": FIXTURE["font"],
    "fontLoaded": True,
    "widths": {state: {} for state in ("narrow", "wide")},
}

try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            executable_path="/usr/bin/chromium",
            args=["--no-sandbox", "--disable-features=OverlayScrollbar"],
        )
        page = browser.new_page(
            viewport={"width": FIXTURE["viewport"]["width"], "height": FIXTURE["viewport"]["height"]},
            device_scale_factor=FIXTURE["viewport"]["dpr"],
        )
        page.goto(URL, wait_until="networkidle")
        page.wait_for_function("document.querySelector('#fixture')?.dataset.ready === 'true'")
        page.wait_for_function("document.fonts.check('16px \"Open Sans\"')")
        for state in ("narrow", "wide"):
            page.evaluate("state => window.setK2State(state)", state)
            page.wait_for_timeout(50)
            page.screenshot(path=str(ARTIFACTS / f"k2-wrap-html-{state}.png"))
            browser_metrics["widths"][state] = page.evaluate(
                r"""({paragraphs, state}) => {
                  const round=n=>Math.round(n*100)/100;
                  const result={};
                  for(const p of paragraphs){
                    const e=document.getElementById(p.id), r=e.getBoundingClientRect();
                    const range=document.createRange(); range.selectNodeContents(e);
                    const rects=[...range.getClientRects()].filter(v=>v.width>0&&v.height>0);
                    const lines=[...new Set(rects.map(v=>Math.round(v.y*100)/100))];
                    const lineTexts=[];
                    for(const token of p.text.matchAll(/\S+/g)){
                      const range=document.createRange();
                      range.setStart(e.firstChild,token.index);range.setEnd(e.firstChild,token.index+token[0].length);
                      const box=range.getBoundingClientRect();
                      const y=Math.round(box.y*100)/100;
                      let line=lineTexts.find(value=>value.y===y);
                      if(!line){line={y,text:''};lineTexts.push(line);}
                      line.text+=(line.text?' ':'')+token[0];
                    }
                    const style=getComputedStyle(e);
                    const minX=rects.length?Math.min(...rects.map(v=>v.x)):0;
                    const minY=rects.length?Math.min(...rects.map(v=>v.y)):0;
                    result[p.id]={x:round(r.x),y:round(r.y),width:round(r.width),height:round(r.height),
                      authoredWidth:p[state],
                      contentWidth:p[state]-18,
                      lineHeight:parseFloat(style.lineHeight),lineCount:lines.length,lineTexts:lineTexts.map(v=>v.text),
                      rangeInkBounds:rects.length?{x:round(minX),y:round(minY),width:round(Math.max(...rects.map(v=>v.right))-minX),height:round(Math.max(...rects.map(v=>v.bottom))-minY)}:null,
                      backgroundColor:style.backgroundColor};
                  }
                  return result;
                }""",
                {"paragraphs": FIXTURE["paragraphs"], "state": state},
            )
        browser.close()
finally:
    SERVER.shutdown()

for state in ("narrow", "wide"):
    image = Image.open(ARTIFACTS / f"k2-wrap-html-{state}.png").convert("RGB")
    for ident, metric in browser_metrics["widths"][state].items():
        x, y, width, height = (round(metric[k]) for k in ("x", "y", "width", "height"))
        points = []
        for py in range(max(0, y), min(image.height, y + height)):
            for px in range(max(0, x), min(image.width, x + width + 80)):
                red, green, blue = image.getpixel((px, py))
                if max(red, green, blue) < 150:
                    points.append((px, py))
        metric["inkBounds"] = (
            None
            if not points
            else {
                "x": min(p[0] for p in points),
                "y": min(p[1] for p in points),
                "width": max(p[0] for p in points) - min(p[0] for p in points) + 1,
                "height": max(p[1] for p in points) - min(p[1] for p in points) + 1,
            }
        )
        metric["horizontalOverflowPx"] = (
            0
            if metric["inkBounds"] is None
            else round(max(0, metric["inkBounds"]["x"] + metric["inkBounds"]["width"] - (x + width)), 2)
        )
        metric["backgroundSample"] = list(image.getpixel((x + width - 5, y + 5)))

(ARTIFACTS / "k2-wrap-browser-metrics.json").write_text(json.dumps(browser_metrics, indent=2) + "\n")
native_path = ARTIFACTS / "k2-wrap-native-metrics.json"
if not native_path.exists():
    raise SystemExit(f"Missing real native capture metrics: {native_path}")
native = json.loads(native_path.read_text())
comparison = {"viewport": FIXTURE["viewport"], "font": FIXTURE["font"], "states": {}}
violations = []
for state in ("narrow", "wide"):
    pairs = {}
    for item in FIXTURE["paragraphs"]:
        ident = item["id"]
        browser = browser_metrics["widths"][state][ident]
        native_item = native["states"][state][ident]
        pairs[ident] = {
            "nearThreshold": bool(item.get("nearThreshold", False)),
            "fontSize": item["fontSize"],
            "geometryDeltaPx": {
                key: round(abs(float(browser[key]) - float(native_item[key])), 2)
                for key in ("x", "y", "width", "height")
            },
            "contentWidthDeltaPx": round(abs(browser["contentWidth"] - native_item["contentWidth"]), 2),
            "lineCountBrowser": browser["lineCount"],
            "lineCountNative": native_item["lineCount"],
            "lineTextsBrowser": browser["lineTexts"],
            "lineTextsNative": native_item["lineTexts"],
            "lineBoxBrowserPx": browser["lineHeight"],
            "lineBoxNativePx": native_item["lineHeight"],
            "inkBrowser": browser["inkBounds"],
            "inkNative": native_item["inkBounds"],
            "overflowBrowserPx": browser["horizontalOverflowPx"],
            "overflowNativePx": native_item["horizontalOverflowPx"],
            "backgroundSampleBrowser": browser["backgroundSample"],
            "backgroundSampleNative": native_item["backgroundSample"],
        }
        pair = pairs[ident]
        near = pair["nearThreshold"]
        for key, delta in pair["geometryDeltaPx"].items():
            if key == "height" and near and state == "narrow":
                continue
            if delta > 1:
                violations.append(f"{state}/{ident}: {key} geometry delta {delta}px")
        if pair["contentWidthDeltaPx"] > 1:
            violations.append(f"{state}/{ident}: content width delta {pair['contentWidthDeltaPx']}px")
        line_delta = abs(pair["lineCountBrowser"] - pair["lineCountNative"])
        if line_delta > 1 and not (near and state == "narrow"):
            violations.append(f"{state}/{ident}: unexpected line count {pair['lineCountBrowser']}/{pair['lineCountNative']}")
        if pair["lineTextsBrowser"] != pair["lineTextsNative"] and not (near and state == "narrow"):
            violations.append(
                f"{state}/{ident}: word line breaks differ: "
                f"browser={pair['lineTextsBrowser']} native={pair['lineTextsNative']}"
            )
        if abs(pair["lineBoxBrowserPx"] - pair["lineBoxNativePx"]) > 1:
            violations.append(f"{state}/{ident}: line box metrics differ")
        browser_ink, native_ink = pair["inkBrowser"], pair["inkNative"]
        if browser_ink is None or native_ink is None:
            violations.append(f"{state}/{ident}: missing rasterized text ink")
        else:
            if abs(browser_ink["y"] - native_ink["y"]) > 1:
                violations.append(f"{state}/{ident}: ink top differs by {abs(browser_ink['y']-native_ink['y'])}px")
            if abs(browser_ink["height"] - native_ink["height"]) > 1 and not (near and state == "narrow"):
                violations.append(f"{state}/{ident}: ink height differs by {abs(browser_ink['height']-native_ink['height'])}px")
            ink_width_delta = abs(browser_ink["width"] - native_ink["width"])
            pair["inkWidthDeltaPx"] = ink_width_delta
            pair["inkWidthRelativeError"] = round(ink_width_delta / max(1, browser_ink["width"]), 4)
            pair["inkWidthDiagnosticOnly"] = pair["fontSize"] == 24
            if ink_width_delta > 6 and not (near and state == "narrow") and not pair["inkWidthDiagnosticOnly"]:
                violations.append(
                    f"{state}/{ident}: ink width differs by {ink_width_delta}px (K2 paragraph gate 6px)"
                )
        if abs(pair["overflowBrowserPx"] - pair["overflowNativePx"]) > 3:
            violations.append(f"{state}/{ident}: overflow differs by more than 3px")
        color_delta = max(
            abs(a - b)
            for a, b in zip(pair["backgroundSampleBrowser"], pair["backgroundSampleNative"])
        )
        pair["backgroundSampleMaxDelta"] = color_delta
        if color_delta > 1:
            violations.append(f"{state}/{ident}: background opacity/color sample differs by {color_delta}")
    comparison["states"][state] = pairs
(ARTIFACTS / "k2-wrap-comparison.json").write_text(json.dumps(comparison, indent=2) + "\n")
print(json.dumps(comparison, indent=2))
if violations:
    raise SystemExit("K2 parity gates failed:\n- " + "\n- ".join(violations))
