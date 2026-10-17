import json
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = Path(__file__).resolve().parent
ROOT = BASE.parent.parent
ARTIFACTS = ROOT / "artifacts"
fixture = json.loads((BASE / "k4-image-fixture.json").read_text())
server = ThreadingHTTPServer(
    ("127.0.0.1", 0),
    lambda *args, **kwargs: SimpleHTTPRequestHandler(*args, directory=str(ROOT), **kwargs),
)
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f"http://127.0.0.1:{server.server_port}/web/javity-ui-reference/k4-image.html"
try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            executable_path="/usr/bin/chromium",
            args=["--no-sandbox", "--disable-features=OverlayScrollbar"],
        )
        page = browser.new_page(
            viewport={"width": fixture["viewport"]["width"], "height": fixture["viewport"]["height"]},
            device_scale_factor=fixture["viewport"]["dpr"],
        )
        page.goto(url, wait_until="networkidle")
        page.wait_for_function("globalThis.k4Ready === true")
        ARTIFACTS.mkdir(exist_ok=True)
        page.screenshot(path=str(ARTIFACTS / "k4-image-browser.png"))
        initial = page.evaluate("window.k4Metrics()")
        page.evaluate("window.applyK4Resize()")
        page.screenshot(path=str(ARTIFACTS / "k4-image-browser-resized.png"))
        resized = page.evaluate("window.k4Metrics()")
        browser.close()
finally:
    server.shutdown()
    server.server_close()

metrics = {"initial": initial, "resized": resized}
(ARTIFACTS / "k4-image-browser-metrics.json").write_text(json.dumps(metrics, indent=2) + "\n")
for name, data in metrics.items():
    print(name, json.dumps(data, separators=(",", ":")))
