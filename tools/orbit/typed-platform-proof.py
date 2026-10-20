#!/usr/bin/env python3
"""Exercise packaged Java/Wasm platform calls against a synthetic MessagePort host, not a live API."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import zipfile

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
HOST_SCRIPT = r"""
const host = window.testHost = {save:{data:{kind:'javity-demo-position-v1',x:2,z:1},revision:5}, calls:[], conflicts:0};
window.addEventListener('message', event => {
  const frame = document.querySelector('iframe');
  if(event.source !== frame.contentWindow || event.origin !== new URL(frame.src).origin || event.data?.type !== 'orbit:ready' || event.data.v !== 1) return;
  const channel = new MessageChannel();
  channel.port1.onmessage = ({data}) => {
    if(data?.v !== 1 || typeof data.id !== 'string') return;
    host.calls.push(data.method);
    let result, error;
    if(data.method === 'player.get') result = {id:'fixture-player',nickname:'Test Player'};
    else if(data.method === 'saves.get') result = structuredClone(host.save);
    else if(data.method === 'saves.put') {
      if(data.params.expectedRevision !== host.save.revision) {host.conflicts++; error={code:'SAVE_CONFLICT',message:'fixture conflict'};}
      else {host.save={data:structuredClone(data.params.data),revision:host.save.revision+1}; result=structuredClone(host.save);}
    } else error={code:'UNSUPPORTED',message:'Not implemented by synthetic host'};
    channel.port1.postMessage({v:1,id:data.id,...(error?{error}:{result})});
  };
  channel.port1.start();
  event.source.postMessage({type:'orbit:connect',v:1},event.origin,[channel.port2]);
});
"""


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


class GameHandler(QuietHandler):
    def end_headers(self):
        self.send_header("Content-Security-Policy", self.server.game_policy)
        super().end_headers()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--package",
        type=Path,
        default=ROOT / "experiments/javity-wasm/build/orbit/javity-wasm.nekko",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=5173,
        help="Must match a launcher origin baked into the package",
    )
    parser.add_argument("--browser", default="/usr/bin/chromium")
    parser.add_argument(
        "--disable-draw",
        action="store_true",
        help="Negative control: suppress GL draw calls; proof must fail",
    )
    parser.add_argument(
        "--evidence-dir", type=Path, default=ROOT / "artifacts/typed-platform"
    )
    args = parser.parse_args()
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    result = {
        "success": False,
        "syntheticHost": True,
        "negativeControl": args.disable_draw,
        "scope": "Actual packaged Java/Wasm + ORBIT SDK/MessagePort; no live account/API",
    }
    servers = []
    started_servers = []
    try:
        spec = importlib.util.spec_from_file_location(
            "wasm_proof", ROOT / "experiments/javity-wasm/tools/chromium-proof.py"
        )
        proof = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(proof)
        with tempfile.TemporaryDirectory(prefix="javity-platform-proof-") as temporary:
            base = Path(temporary)
            game = base / "game"
            game.mkdir()
            with zipfile.ZipFile(args.package) as archive:
                entries = archive.infolist()
                if (
                    len(entries) > 10000
                    or sum(item.file_size for item in entries) > 250 * 1024 * 1024
                ):
                    raise ValueError("Package limits exceeded")
                for item in entries:
                    path = Path(item.filename)
                    if (
                        path.is_absolute()
                        or ".." in path.parts
                        or "\\" in item.filename
                    ):
                        raise ValueError("Unsafe archive path")
                archive.extractall(game)
            launcher_origin = f"http://localhost:{args.port}"
            games = ThreadingHTTPServer(
                ("127.0.0.1", 0), partial(GameHandler, directory=str(game))
            )
            games.game_policy = f"default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; base-uri 'none'; form-action 'none'; frame-ancestors {launcher_origin}"
            servers.append(games)
            (base / "host.js").write_text(HOST_SCRIPT)
            (base / "index.html").write_text(
                f'<!doctype html><title>Synthetic ORBIT host</title><script src="host.js"></script><iframe sandbox="allow-scripts allow-same-origin" width="1100" height="800" src="http://127.0.0.1:{games.server_port}/index.html"></iframe>'
            )
            launcher = ThreadingHTTPServer(
                ("127.0.0.1", args.port), partial(QuietHandler, directory=str(base))
            )
            servers.append(launcher)
            for server in servers:
                threading.Thread(target=server.serve_forever, daemon=True).start()
                started_servers.append(server)
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(
                    executable_path=args.browser,
                    headless=True,
                    args=[
                        "--no-sandbox",
                        "--use-gl=angle",
                        "--use-angle=swiftshader",
                        "--enable-unsafe-swiftshader",
                    ],
                )
                try:
                    page = browser.new_page(viewport={"width": 1200, "height": 900})
                    if args.disable_draw:
                        page.add_init_script(proof.DISABLE_DRAWS)
                    errors = []
                    page.on("pageerror", lambda error: errors.append(str(error)))
                    page.goto(launcher_origin)
                    frame = page.locator("iframe").element_handle().content_frame()
                    frame.wait_for_function(
                        "globalThis.javityWasm?.rendered > 10 && JavityOrbit.state.javaPlayer?.id === 'fixture-player' && Math.abs(javityWasm.x-2)<.01",
                        timeout=60000,
                    )
                    frame.locator("#canvas").click()
                    page.keyboard.down("d")
                    try:
                        frame.wait_for_function("javityWasm.x > 3")
                    finally:
                        page.keyboard.up("d")
                    for revision in (6, 8):
                        page.keyboard.down("p")
                        try:
                            page.wait_for_function(
                                "revision => testHost.save.revision === revision",
                                arg=revision,
                            )
                            frame.wait_for_function(
                                "revision => JavityOrbit.state.revision === revision",
                                arg=revision,
                            )
                        finally:
                            page.keyboard.up("p")
                        if revision == 6:
                            frame.get_by_role("button", name="Zapisz w chmurze").click()
                            page.wait_for_function("testHost.save.revision === 7")
                            frame.wait_for_function("JavityOrbit.state.revision === 7")
                    assert page.evaluate("testHost.conflicts") == 0
                    assert frame.evaluate("javityWasm.glError") == 0
                    frame.locator("#orbit-panel").evaluate(
                        "element => {element.style.visibility='hidden';}"
                    )
                    pixels = proof.check_framebuffer(
                        page=frame,
                        canvas=frame.locator("#canvas"),
                        output=args.evidence_dir,
                    )
                    page.evaluate("testHost.save.data.x = 1e40")
                    page.locator("iframe").evaluate(
                        "frame => {frame.src=frame.src+'?invalid-save=1';}"
                    )
                    frame = page.locator("iframe").element_handle().content_frame()
                    frame.wait_for_function(
                        "globalThis.javityWasm?.rendered > 10 && globalThis.javityWasmPlatformStatus === 'save format unsupported'",
                        timeout=60000,
                    )
                    assert abs(frame.evaluate("javityWasm.x")) < 0.01
                    frame.evaluate("globalThis.javityWasmStop = true")
                    frame.wait_for_function(
                        "javityWasm.disposed && JavityOrbit.state.mode === 'closed'"
                    )
                    assert not errors, errors
                    result.update(
                        success=True,
                        browser=browser.version,
                        javaProfile=True,
                        typedSave=True,
                        sharedPanelRevision=True,
                        finalRevision=page.evaluate("testHost.save.revision"),
                        invalidSaveRejected=True,
                        dispose=True,
                        framebuffer=pixels,
                        errors=errors,
                    )
                    print(
                        "Packaged typed Java/Wasm platform + synthetic MessagePort host: PASS"
                    )
                finally:
                    browser.close()
    except Exception as failure:
        result["error"] = repr(failure)
        raise
    finally:
        for server in reversed(servers):
            if server in started_servers:
                server.shutdown()
            server.server_close()
        (args.evidence_dir / "verification.json").write_text(
            json.dumps(result, indent=2) + "\n"
        )


if __name__ == "__main__":
    main()
