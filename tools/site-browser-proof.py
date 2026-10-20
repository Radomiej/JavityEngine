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
EXAMPLES = {
    "quality": ("./gradlew :javity-desktop:qualityDemo", "docs/quality-showcase.md"),
    "foliage": ("./gradlew :javity-desktop:foliage", "docs/foliage.md"),
    "avatar": ("./gradlew :javity-desktop:importedAvatar", "docs/avatars.md"),
    "voxels": ("./gradlew :javity-desktop:run --args='--voxels'", "docs/voxels.md"),
    "shapes": ("./gradlew :javity-desktop:run --args='--shapes'", "docs/shapes-and-shaders.md"),
    "physics": ("./gradlew :javity-desktop:run --args='--physics3d'", "docs/physics3d.md"),
    "map": ("./gradlew :javity-desktop:run --args='--gorzow'", "docs/gorzow-offline-map.md"),
    "relay": ("./gradlew :javity-desktop:run --args='--mm'", "docs/game-authoring.md"),
}


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


def assert_decoded(image):
    assert image.get_attribute("alt"), "Screenshot needs descriptive alternative text"
    image.evaluate("async image => { await image.decode(); }")
    assert image.evaluate("image => image.complete && image.naturalWidth > 0"), image.get_attribute("src")


def assert_selected(page, tab_id):
    tab = page.locator("#" + tab_id)
    assert tab.get_attribute("aria-selected") == "true", tab_id
    assert tab.get_attribute("tabindex") == "0", tab_id
    assert page.locator('.example-tab[aria-selected="true"]').count() == 1
    assert page.locator('.example-tab[tabindex="0"]').count() == 1
    assert page.locator("#example-panel").get_attribute("aria-labelledby") == tab_id


def check_gallery(page, output, width):
    buttons = page.locator("#gallery .gallery-card button[data-gallery-open]")
    assert buttons.count() >= len(EXAMPLES), "Gallery must retain all eight demo captures"
    dialog = page.locator("#gallery-dialog")
    assert dialog.evaluate("element => element instanceof HTMLDialogElement"), "Use a native modal dialog"
    assert dialog.get_attribute("aria-labelledby"), "Zoom needs an accessible name"
    captures = []
    for index, button in enumerate(buttons.all()):
        thumbnail = button.locator("img")
        assert thumbnail.count() == 1
        assert_decoded(thumbnail)
        source = thumbnail.evaluate("image => image.currentSrc")
        button.click()
        assert dialog.is_visible()
        assert dialog.evaluate("element => element.open && element.matches(':modal')"), "Zoom must be modal"
        assert dialog.evaluate("element => element.contains(document.activeElement)"), "Focus must enter the zoom"
        label = page.locator("#" + dialog.get_attribute("aria-labelledby"))
        assert label.inner_text().strip(), "Zoom label must name the screenshot"
        image = page.locator("#gallery-image")
        assert_decoded(image)
        assert image.evaluate("image => image.currentSrc") == source, "Zoom must match the selected capture"
        assert page.locator("#gallery-caption").inner_text().strip()
        dimensions = dialog.bounding_box()
        assert dimensions and dimensions["x"] >= -1 and dimensions["x"] + dimensions["width"] <= width + 1, dimensions
        assert dimensions["y"] >= -1 and dimensions["y"] + dimensions["height"] <= page.viewport_size["height"] + 1, dimensions
        if index == 0:
            page.screenshot(path=str(output / f"gallery-{width}.png"))
        if index % 2:
            page.locator("#gallery-close").click()
        else:
            page.keyboard.press("Escape")
        page.wait_for_function("() => !document.querySelector('#gallery-dialog').open")
        assert not dialog.is_visible(), "Zoom must close using both Escape and its close button"
        page.wait_for_function("element => element === document.activeElement", arg=button.element_handle())
        assert button.evaluate("element => element === document.activeElement"), "Closing must restore trigger focus"
        captures.append(source.rsplit("/", 1)[-1])
    return {"width": width, "captures": captures, "escapeAndClose": True, "focusReturn": True}


def check_page(page, url, output):
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.on("console", lambda message: errors.append(message.text) if message.type == "error" else None)
    page.emulate_media(reduced_motion="reduce")
    response = page.goto(url, wait_until="networkidle")
    assert response.status == 200
    assert page.title().startswith("JavityEngine")
    tab_ids = page.locator(".example-tab").evaluate_all("tabs => tabs.map(tab => tab.id)")
    assert tab_ids == ["tab-" + name for name in EXAMPLES], f"Missing or reordered demos: {tab_ids}"
    assert page.locator('.example-tab[tabindex="0"]').count() == 1
    assert_selected(page, "tab-quality")
    assert page.locator("#example-command").inner_text().strip() == EXAMPLES["quality"][0]
    for image in page.locator("img").all():
        if not image.is_visible():
            continue  # The initially empty modal image is checked after every gallery selection.
        image.scroll_into_view_if_needed()
        assert_decoded(image)
    assert page.locator(".hero-visual img").evaluate(
        'x => Math.abs(x.clientWidth / x.clientHeight - 16 / 9) < 0.02 && getComputedStyle(x).objectFit === "cover"'
    )
    for name, (command, guide) in EXAMPLES.items():
        page.locator("#tab-" + name).click()
        assert_selected(page, "tab-" + name)
        assert command == page.locator("#example-command").inner_text().strip(), f"wrong example command: {name}"
        assert page.locator("#example-source").get_attribute("href") == "https://github.com/Radomiej/JavityEngine/blob/master/" + guide
        assert_decoded(page.locator("#example-image"))
    page.locator("#" + tab_ids[0]).focus()
    page.keyboard.press("ArrowDown")
    assert_selected(page, tab_ids[1])
    page.keyboard.press("End")
    assert_selected(page, tab_ids[-1])
    page.keyboard.press("ArrowRight")
    assert_selected(page, tab_ids[0])
    page.keyboard.press("ArrowLeft")
    assert_selected(page, tab_ids[-1])
    page.keyboard.press("Home")
    assert_selected(page, tab_ids[0])
    gallery = [check_gallery(page, output, 1440)]
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
    gallery.append(check_gallery(page, output, 390))
    page.evaluate("window.scrollTo(0, 0)")
    page.locator(".menu-toggle").click()
    assert page.locator(".menu-toggle").get_attribute("aria-expanded") == "true"
    page.locator('#site-nav a[href="#examples"]').click()
    assert page.locator(".menu-toggle").get_attribute("aria-expanded") == "false"
    page.evaluate("window.scrollTo(0, 0)")
    page.screenshot(path=str(output / "mobile.png"), full_page=True)
    page.screenshot(path=str(output / "mobile-top.png"))
    assert not errors, errors
    return {"desktop": True, "tabsAndKeyboard": True, "examples": list(EXAMPLES),
            "gallery": gallery, "viewports": viewports, "mobileMenu": True, "errors": errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser", default="/usr/bin/chromium")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/site")
    parser.add_argument("--disable-scripts", action="store_true", help="Negative control: disable page JavaScript; must fail.")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    result = {"success": False, "scope": "Static engine showcase", "negativeControl": args.disable_scripts,
              "browserHarness": "Project Playwright with regular Chromium; not the connected browser extension",
              "browserHarnessReason": "No browser control tool was exposed after tool discovery; AGENTS.md permits the existing Chromium showcase proof"}
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
