import hashlib
import json
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

BASE = Path(__file__).resolve().parent
ROOT = BASE.parent.parent
server = ThreadingHTTPServer(("127.0.0.1", 0), lambda *a, **k: SimpleHTTPRequestHandler(*a, directory=str(ROOT), **k))
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f"http://127.0.0.1:{server.server_port}/web/javity-ui-reference/index.html"
font_path = ROOT / "javity-ui/src/main/resources/org/javity/ui/fonts/OpenSans-Regular.ttf"
font_sha256 = hashlib.sha256(font_path.read_bytes()).hexdigest()
font_url_path = "/javity-ui/src/main/resources/org/javity/ui/fonts/OpenSans-Regular.ttf"
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, executable_path="/usr/bin/chromium", args=["--no-sandbox", "--disable-features=OverlayScrollbar"])
    page = browser.new_page(viewport={"width": 1100, "height": 720}, device_scale_factor=1)
    font_responses = []
    page.on("response", lambda response: font_responses.append({"url": response.url, "status": response.status}) if response.url.endswith(font_url_path) else None)
    page.goto(url, wait_until="networkidle")
    page.evaluate("document.fonts.ready")
    page.wait_for_selector("#classic-card")
    font_diagnostic = page.evaluate("""() => {
      const faces=[];
      const visit=(rules,sheetUrl)=>{for(const rule of rules){
        if(rule instanceof CSSFontFaceRule){
          const src=rule.style.getPropertyValue('src');
          const source=src.match(/url\\((.*?)\\)/);
          faces.push({family:rule.style.getPropertyValue('font-family').replace(/[\"']/g,''),weight:rule.style.getPropertyValue('font-weight'),style:rule.style.getPropertyValue('font-style'),src,resolvedSrc:source?new URL(source[1].replace(/[\"']/g,''),sheetUrl).href:null});
        } else if(rule.cssRules) visit(rule.cssRules,sheetUrl);
      }};
      for(const sheet of document.styleSheets){try{visit(sheet.cssRules,sheet.href||location.href)}catch(_){}}
      const face=[...document.fonts].find(f=>f.family.replace(/[\"']/g,'')==='Open Sans'&&f.weight==='400'&&f.style==='normal');
      const computed=getComputedStyle(document.querySelector('#card-title'));
      return {faces,loadedFace:face?{family:face.family,weight:face.weight,style:face.style,status:face.status}:null,computedFamily:computed.fontFamily,computedWeight:computed.fontWeight};
    }""")
    expected_font_url = f"{url.rsplit('/web/', 1)[0]}{font_url_path}"
    font_checks = {
        "declaredBundledFace": any(face["family"].strip().lower() == "open sans" and face["weight"].strip() == "400" and face["style"].strip() == "normal" and face["resolvedSrc"] == expected_font_url for face in font_diagnostic["faces"]),
        "faceLoaded": bool(font_diagnostic["loadedFace"] and font_diagnostic["loadedFace"]["status"] == "loaded"),
        "fontResponseOk": any(response["url"] == expected_font_url and response["status"] == 200 for response in font_responses),
        "computedOpenSans": font_diagnostic["computedFamily"].split(",")[0].strip().strip("\"'").lower() == "open sans" and font_diagnostic["computedWeight"] == "400",
    }
    font_diagnostic.update({"browserVersion": browser.version, "expectedFontUrl": expected_font_url, "fontFileSha256": font_sha256, "fontResponses": font_responses, "checks": font_checks})
    print("UI reference browser/font: " + json.dumps(font_diagnostic, sort_keys=True), flush=True)
    if not all(font_checks.values()):
        raise AssertionError("The bundled Open Sans 400 face must load and be applied to the browser reference")
    page.locator("#scroll-sample").hover(position={"x": 418, "y": 36})
    page.locator("#scroll-sample").evaluate("e => e.scrollTop = 0")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-reference.png"))
    metrics = page.evaluate("""() => {
      const keys=['screen','nested','nested-header','header-left','header-right','header-label','nested-action','nested-content','flex-left','left-text','flex-right','right-text','controls','classic-button','classic-input','classic-progress','classic-card','card-title','card-body','typography','type-inherited','type-xl','type-2xl','scroll-sample'];
      const round=n=>Math.round(n*100)/100;
      const el=id=>{const e=document.getElementById(id),s=getComputedStyle(e),r=e.getBoundingClientRect();return {rect:{x:round(r.x),y:round(r.y),width:round(r.width),height:round(r.height)},fontSize:s.fontSize,lineHeight:s.lineHeight,color:s.color,backgroundColor:s.backgroundColor,borderColor:s.borderColor,borderWidth:s.borderWidth,borderRadius:s.borderRadius,padding:{top:s.paddingTop,right:s.paddingRight,bottom:s.paddingBottom,left:s.paddingLeft},gap:s.gap,flexDirection:s.flexDirection}};
      const scroll=document.querySelector('#scroll-sample');
      const sr=scroll.getBoundingClientRect();
      const thumb=document.querySelector('#scroll-thumb').getBoundingClientRect();
      return {viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio},els:Object.fromEntries(keys.map(k=>[k,el(k)])),scrollbar:{clientWidth:scroll.clientWidth,offsetWidth:scroll.offsetWidth,scrollWidth:scroll.scrollWidth,scrollHeight:scroll.scrollHeight,scrollTop:scroll.scrollTop,rect:{x:round(sr.x),y:round(sr.y),width:round(sr.width),height:round(sr.height)},thumb:{x:round(thumb.x),y:round(thumb.y),width:round(thumb.width),height:round(thumb.height)}}};
    }""")
    metrics["browserFont"] = font_diagnostic
    metrics["states"] = {"inputBorderPoint": page.locator("#classic-input").evaluate("e => {const r=e.getBoundingClientRect(); return [Math.round(r.x),Math.round(r.y+r.height/2)]}")}
    page.locator("#classic-button").hover()
    metrics["states"]["buttonHover"] = page.locator("#classic-button").evaluate("e => getComputedStyle(e).backgroundColor")
    metrics["states"]["buttonHoverPoint"] = page.locator("#classic-button").evaluate("e => {const r=e.getBoundingClientRect(); return [Math.round(r.x+8),Math.round(r.y+8)]}")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-hover.png"))
    page.locator("#classic-input").hover()
    metrics["states"]["inputHoverBorder"] = page.locator("#classic-input").evaluate("e => getComputedStyle(e).borderColor")
    metrics["states"]["inputHoverPoint"] = page.locator("#classic-input").evaluate("e => {const r=e.getBoundingClientRect(); return [Math.round(r.x),Math.round(r.y+r.height/2)]}")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-input-hover.png"))
    page.locator("#classic-input").focus()
    metrics["states"]["inputFocusBorder"] = page.locator("#classic-input").evaluate("e => getComputedStyle(e).borderColor")
    metrics["states"]["inputFocusPoint"] = page.locator("#classic-input").evaluate("e => {const r=e.getBoundingClientRect(); return [Math.round(r.x),Math.round(r.y+r.height/2)]}")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-focus.png"))
    page.mouse.move(1000, 700)
    page.locator("#classic-input").evaluate("e => e.disabled = true")
    metrics["states"]["inputDisabledPoint"] = page.locator("#classic-input").evaluate("e => {const r=e.getBoundingClientRect(); return [Math.round(r.x),Math.round(r.y+r.height/2)]}")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-input-disabled.png"))
    page.locator("#classic-button").evaluate("e => e.disabled = true")
    metrics["states"]["buttonDisabled"] = page.locator("#classic-button").evaluate("e => getComputedStyle(e).backgroundColor")
    metrics["states"]["buttonDisabledOpacity"] = page.locator("#classic-button").evaluate("e => getComputedStyle(e).opacity")
    metrics["states"]["buttonDisabledPoint"] = page.locator("#classic-button").evaluate("e => {const r=e.getBoundingClientRect(); return [Math.round(r.x+8),Math.round(r.y+8)]}")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-disabled.png"))
    page.locator("#scroll-sample").evaluate("e => e.scrollTop = 40")
    page.wait_for_timeout(100)
    metrics["scrollbar"]["scrollTopMoved"] = page.locator("#scroll-sample").evaluate("e => e.scrollTop")
    metrics["scrollbar"]["thumbMoved"] = page.locator("#scroll-thumb").evaluate("e => {const r=e.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}}")
    page.screenshot(path=str(BASE / "tailwind-4.3.3-scroll-moved.png"))
    browser.close()
server.shutdown()
image = Image.open(BASE / "tailwind-4.3.3-reference.png").convert("RGB")
metrics["states"]["inputBorderPixel"] = list(image.getpixel(tuple(metrics["states"]["inputBorderPoint"])))
for state, point_name in (("hover", "buttonHoverPoint"), ("input-hover", "inputHoverPoint"), ("focus", "inputFocusPoint"), ("disabled", "buttonDisabledPoint"), ("input-disabled", "inputDisabledPoint")):
    state_image = Image.open(BASE / f"tailwind-4.3.3-{state}.png").convert("RGB")
    metrics["states"][state + "Pixel"] = list(state_image.getpixel(tuple(metrics["states"][point_name])))
for filename, top in (("tailwind-4.3.3-reference.png", 0), ("tailwind-4.3.3-scroll-moved.png", 40)):
    scroll_image = Image.open(BASE / filename).convert("RGB")
    box = metrics["scrollbar"]["thumb"] if top == 0 else metrics["scrollbar"].get("thumbMoved")
    if box:
        pixels = [(x,y) for y in range(round(box["y"]), round(box["y"]+box["height"])) for x in range(round(box["x"]), round(box["x"]+box["width"])) if scroll_image.getpixel((x,y)) == (144,161,185)]
        metrics["scrollbar"]["thumbPixels" if top == 0 else "thumbMovedPixels"] = len(pixels)
        metrics["scrollbar"]["thumbPixelBounds" if top == 0 else "thumbMovedPixelBounds"] = {"x":min(p[0] for p in pixels),"y":min(p[1] for p in pixels),"width":max(p[0] for p in pixels)-min(p[0] for p in pixels)+1,"height":max(p[1] for p in pixels)-min(p[1] for p in pixels)+1} if pixels else None
if metrics["scrollbar"].get("thumbMoved", {}).get("y", 0) <= metrics["scrollbar"]["thumb"]["y"]:
    raise AssertionError("The HTML scrollbar thumb must be visible and move with scrollTop")
for key, item in metrics["els"].items():
    box = item["rect"]
    if box["width"] > 0 and box["height"] > 0:
        item["pixelCenter"] = image.getpixel((round(box["x"] + box["width"] / 2), round(box["y"] + box["height"] / 2)))
for key in ("header-label", "left-text", "right-text", "card-title", "card-body", "type-inherited", "type-xl", "type-2xl"):
    item = metrics["els"].get(key)
    if item is None:
        continue
    box = item["rect"]
    crop = image.crop((round(box["x"]), round(box["y"]), round(box["x"] + box["width"]), round(box["y"] + box["height"])))
    points = [(x, y) for y in range(crop.height) for x in range(crop.width) if max(crop.getpixel((x, y))) < 180]
    item["glyphPixels"] = {"x": box["x"] + min(p[0] for p in points), "y": box["y"] + min(p[1] for p in points), "width": max(p[0] for p in points) - min(p[0] for p in points) + 1, "height": max(p[1] for p in points) - min(p[1] for p in points) + 1} if points else None
(BASE / "reference-metrics.json").write_text(json.dumps(metrics, indent=2))
print(json.dumps(metrics, indent=2))
