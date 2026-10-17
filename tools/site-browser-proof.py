#!/usr/bin/env python3
"""Check the static showcase in Chromium; requires Python Playwright and Chromium."""
import argparse
from contextlib import contextmanager
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading
import traceback

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "/JavityEngine/"


class SiteHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        if not self.path.startswith(PREFIX):
            self.send_error(404)
            return
        self.path = self.path.removeprefix(PREFIX[:-1])
        super().do_GET()


@contextmanager
def serve_site():
    handler = partial(SiteHandler, directory=str(ROOT / "site"))
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}{PREFIX}"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def check_page(page, url, output):
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.on("console", lambda message: errors.append(message.text) if message.type == "error" else None)
    page.emulate_media(reduced_motion="reduce")
    response = page.goto(url, wait_until="networkidle")
    assert response.status == 200
    assert page.title().startswith("JavityEngine")
    assert page.locator('.example-tab[tabindex="0"]').count() == 1
    for image in page.locator("img").all():
        image.scroll_into_view_if_needed()
        page.wait_for_function("image => image.complete && image.naturalWidth > 0", arg=image.element_handle())
    assert page.locator(".hero-visual img").evaluate(
        'x => Math.abs(x.clientWidth / x.clientHeight - 16 / 9) < 0.02 && getComputedStyle(x).objectFit === "cover"'
    )
    for name, command in [("voxels", "--voxels"), ("shapes", "--shapes"),
                          ("physics", "--physics3d"), ("map", "--gorzow"), ("relay", "--mm")]:
        page.locator("#tab-" + name).click()
        assert command in page.locator("#example-command").inner_text(), f"wrong example command: {name}"
        page.locator("#example-image").evaluate("image => image.decode()")
    page.locator("#tab-voxels").focus()
    page.keyboard.press("ArrowDown")
    assert page.locator("#tab-shapes").get_attribute("aria-selected") == "true"
    page.keyboard.press("End")
    assert page.locator("#tab-relay").get_attribute("aria-selected") == "true"
    page.evaluate("window.scrollTo(0, 0)")
    page.screenshot(path=str(output / "desktop.png"), full_page=True)
    page.screenshot(path=str(output / "desktop-top.png"))
    viewports = []
    for width in [360, 390, 768, 1440]:
        page.set_viewport_size({"width": width, "height": 900})
        dimensions = page.evaluate("({viewport: innerWidth, scroll: document.documentElement.scrollWidth})")
        assert dimensions["scroll"] <= dimensions["viewport"], dimensions
        viewports.append(dimensions)
    page.set_viewport_size({"width": 390, "height": 844})
    page.evaluate("window.scrollTo(0, 0)")
    page.locator(".menu-toggle").click()
    assert page.locator(".menu-toggle").get_attribute("aria-expanded") == "true"
    page.locator('#site-nav a[href="#examples"]').click()
    assert page.locator(".menu-toggle").get_attribute("aria-expanded") == "false"
    page.evaluate("window.scrollTo(0, 0)")
    page.screenshot(path=str(output / "mobile.png"), full_page=True)
    page.screenshot(path=str(output / "mobile-top.png"))
    assert not errors, errors
    return {"desktop": True, "tabsAndKeyboard": True, "viewports": viewports, "errors": errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser", default="/usr/bin/chromium")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/site")
    parser.add_argument("--disable-scripts", action="store_true", help="Negative control: disable page JavaScript; must fail.")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    result = {"success": False, "scope": "Static engine showcase", "negativeControl": args.disable_scripts}
    try:
        with serve_site() as url, sync_playwright() as playwright:
            browser = playwright.chromium.launch(
                executable_path=args.browser, headless=True, args=["--no-sandbox", "--disable-dev-shm-usage"]
            )
            try:
                page = browser.new_page(viewport={"width": 1440, "height": 1000}, device_scale_factor=1,
                                        java_script_enabled=not args.disable_scripts)
                result["browser"] = browser.version
                result.update(check_page(page, url, args.output))
                result["success"] = True
            finally:
                browser.close()
    except Exception as error:
        result["error"] = repr(error)
        result["traceback"] = traceback.format_exc()
    finally:
        (args.output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
