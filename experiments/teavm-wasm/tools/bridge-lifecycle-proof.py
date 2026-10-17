#!/usr/bin/env python3
"""Run lifecycle regression scenarios against real Yoga and Ammo in Chromium."""
import json
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
server = ThreadingHTTPServer(("127.0.0.1", 0), partial(SimpleHTTPRequestHandler, directory=str(ROOT)))
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
errors = []
try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, executable_path="/usr/bin/chromium", args=["--no-sandbox"])
        page = browser.new_page()
        page.on("pageerror", lambda error: errors.append(str(error)))
        response = page.goto(f"http://127.0.0.1:{server.server_port}/web/bridge-lifecycle-tests.html", wait_until="load")
        page.wait_for_function("globalThis.bridgeLifecycleResult !== undefined", timeout=30000)
        result = page.evaluate("globalThis.bridgeLifecycleResult")
        assert response and response.status == 200, response.status if response else None
        assert result["success"], json.dumps(result, indent=2)
        assert not errors, errors
        print(json.dumps({"success": True, "browser": browser.version, "result": result}, indent=2))
        browser.close()
finally:
    server.shutdown()
    server.server_close()
    thread.join(timeout=2)
