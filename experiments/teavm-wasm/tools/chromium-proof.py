#!/usr/bin/env python3
import json
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "evidence"
errors = []
console_errors = []
server = ThreadingHTTPServer(("127.0.0.1", 0), partial(SimpleHTTPRequestHandler, directory=str(ROOT)))
server_thread = threading.Thread(target=server.serve_forever, daemon=True)
server_thread.start()
try:
  with sync_playwright() as p:
      browser = p.chromium.launch(headless=True, executable_path="/usr/bin/chromium", args=["--no-sandbox"])
      page = browser.new_page(viewport={"width": 900, "height": 700})
      page.on("pageerror", lambda error: errors.append(str(error)))
      page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
      response = page.goto(f"http://127.0.0.1:{server.server_port}/web/index.html", wait_until="networkidle")
      page.wait_for_function("globalThis.spikeEvidence !== undefined", timeout=15000)
      result = page.evaluate("globalThis.spikeEvidence")
      result["pageStatus"] = response.status if response else None
      result["browser"] = "Chromium via /usr/bin/chromium"
      result["pageErrors"] = errors
      result["consoleErrors"] = console_errors
      assert result["frame"] == 60, result
      assert result["state"] == 15, result
      assert 0 < result["ballYAfter60Ticks"] < 5, result
      assert result["tickCallbacks"] == 60, result
      assert result["events"][0] == "java:create" and "yoga:0,0,80,40" in result["events"], result
      assert result["events"][-1] == "bridge:dispose", result
      assert result["ammoFrees"] == 13 and "gravity:destroy:ok" in result["events"], result
      assert not errors and not console_errors, result
      page.screenshot(path=str(EVIDENCE / "chromium-proof.png"), full_page=True)
      browser.close()
  (EVIDENCE / "runtime.json").write_text(json.dumps(result, indent=2) + "\n")
  print(json.dumps(result, indent=2))
finally:
  server.shutdown()
  server.server_close()
  server_thread.join(timeout=2)
