#!/usr/bin/env python3
"""Serve and optionally capture the authored town with upstream MapLibre GL JS.

Install the pinned browser renderer first with:
    npm --prefix web/maplibre-reference ci
Then run from the repository root:
    python3 tools/maplibre-browser-reference.py
or capture the default day-style reference:
    python3 tools/maplibre-browser-reference.py --capture
"""

from __future__ import annotations

import argparse
import json
import mimetypes
from pathlib import Path
import sqlite3
import sys
from threading import Thread
from urllib.request import urlopen
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "javity-desktop/src/main/resources/org/javity/demo/maplibre-town"
WEB = ROOT / "web/maplibre-reference"


def database_path() -> Path:
    sources = read_manifest()["sources"]
    if len(sources) != 1:
        raise ValueError("Reference capture currently requires exactly one MBTiles source")
    return PACKAGE / next(iter(sources.values()))


def read_manifest() -> dict:
    return json.loads((PACKAGE / "map.json").read_text(encoding="utf-8"))


def tilejson() -> dict:
    """Build a standards-shaped XYZ TileJSON view over MBTiles' TMS rows."""
    with sqlite3.connect(f"file:{database_path()}?mode=ro", uri=True) as db:
        metadata = dict(db.execute("SELECT name, value FROM metadata"))
    extra = json.loads(metadata.get("json", "{}"))
    result = {
        "tilejson": "3.0.0",
        "name": metadata.get("name", "Javity Authored Town"),
        "scheme": "xyz",
        "tiles": ["/tiles/{z}/{x}/{y}.pbf"],
        "minzoom": int(metadata.get("minzoom", 0)),
        "maxzoom": int(metadata.get("maxzoom", 22)),
        "bounds": [float(v) for v in metadata["bounds"].split(",")],
        "center": [float(v) for v in metadata["center"].split(",")[:2]],
        "vector_layers": extra.get("vector_layers", []),
    }
    return result


def prepared_style(style_name: str, origin: str) -> dict:
    manifest = read_manifest()
    if style_name not in manifest["styles"]:
        raise ValueError(f"Unknown style: {style_name}")
    style_file = PACKAGE / manifest["styles"][style_name]
    style = json.loads(style_file.read_text(encoding="utf-8"))
    for source_name, source in style.get("sources", {}).items():
        source_url = source.get("url", "")
        prefix = "mbtiles://@"
        if source_url.startswith(prefix):
            package_name = source_url[len(prefix):]
            relative = manifest.get("sources", {}).get(package_name)
            if not relative:
                raise ValueError(f"Style refers to unmapped MBTiles source {package_name!r}")
            if not (PACKAGE / relative).is_file():
                raise FileNotFoundError(PACKAGE / relative)
            source["url"] = "/tiles.json"
        elif source.get("tiles"):
            raise ValueError(f"Reference fixture requires local mbtiles:// sources ({source_name})")
    if style.get("glyphs"):
        style["glyphs"] = f"{origin}/glyphs/{{fontstack}}/{{range}}.pbf"
    if style.get("sprite"):
        style["sprite"] = f"{origin}/sprites/town"
    return style


class Handler(BaseHTTPRequestHandler):
    server_version = "JavityMapLibreReference/1.0"

    def log_message(self, fmt: str, *args: object) -> None:
        # Keep ordinary tile traffic quiet; failures remain useful on the console.
        if args and str(args[1]).startswith("5"):
            super().log_message(fmt, *args)

    def send_bytes(self, body: bytes, content_type: str, *, content_encoding: str | None = None) -> None:
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        if content_encoding:
            self.send_header("Content-Encoding", content_encoding)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_json(self, value: object) -> None:
        self.send_bytes(json.dumps(value, separators=(",", ":")).encode(), "application/json; charset=utf-8")

    def do_HEAD(self) -> None:
        self.do_GET()

    def do_GET(self) -> None:
        path = unquote(urlsplit(self.path).path)
        if path == "/" or path == "/index.html":
            return self.send_bytes((WEB / "index.html").read_bytes(), "text/html; charset=utf-8")
        if path in ("/reference.js", "/reference.mjs"):
            return self.send_bytes((WEB / "reference.js").read_bytes(), "text/javascript; charset=utf-8")
        if path == "/reference.css":
            return self.send_bytes((WEB / "reference.css").read_bytes(), "text/css; charset=utf-8")
        if path == "/map.json":
            return self.send_json(read_manifest())
        if path.startswith("/style/"):
            try:
                origin = f"http://{self.headers.get('Host', '127.0.0.1')}"
                return self.send_json(prepared_style(path.removeprefix("/style/"), origin))
            except (KeyError, ValueError, OSError, json.JSONDecodeError) as error:
                return self.send_error(404, str(error))
        if path == "/tiles.json":
            return self.send_json(tilejson())
        if path.startswith("/tiles/"):
            return self.serve_tile(path)
        if path.startswith("/glyphs/"):
            return self.serve_package_file(path, "/glyphs/", "application/x-protobuf")
        if path.startswith("/sprites/"):
            return self.serve_package_file(path, "/sprites/", None)
        if path.startswith("/vendor/"):
            rel = path.removeprefix("/vendor/")
            if rel != "maplibre-gl.css" and not (rel.startswith("maplibre-gl") and rel.endswith(".mjs")):
                return self.send_error(404)
            file_path = WEB / "node_modules/maplibre-gl/dist" / rel
            if not file_path.is_file():
                return self.send_error(503, "MapLibre GL JS missing; run npm --prefix web/maplibre-reference ci")
            content_type = "text/javascript; charset=utf-8" if rel.endswith((".js", ".mjs")) else "text/css; charset=utf-8"
            return self.send_bytes(file_path.read_bytes(), content_type)
        self.send_error(404)

    def serve_tile(self, path: str) -> None:
        try:
            z, x, xyz_y = (int(part) for part in path.removeprefix("/tiles/").removesuffix(".pbf").split("/"))
            if z < 0 or z > 30 or x < 0 or x >= 1 << z or xyz_y < 0 or xyz_y >= 1 << z:
                raise ValueError("tile coordinate out of range")
        except ValueError:
            return self.send_error(400, "Expected /tiles/{z}/{x}/{y}.pbf with valid XYZ coordinates")
        tms_y = (1 << z) - 1 - xyz_y
        try:
            with sqlite3.connect(f"file:{database_path()}?mode=ro", uri=True) as db:
                row = db.execute(
                    "SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                    (z, x, tms_y),
                ).fetchone()
        except sqlite3.Error as error:
            return self.send_error(500, str(error))
        if row is None:
            return self.send_error(404, "tile is not present in the authored MBTiles fixture")
        payload = bytes(row[0])
        compressed = payload.startswith(b"\x1f\x8b")
        # MBTiles vector tile payloads in this fixture are gzip-compressed MVT.
        # Advertise compression and bytes faithfully to MapLibre's browser fetch.
        self.send_bytes(payload, "application/x-protobuf", content_encoding="gzip" if compressed else None)

    def serve_package_file(self, path: str, prefix: str, fallback_type: str | None) -> None:
        rel = Path(path.removeprefix(prefix))
        if rel.is_absolute() or ".." in rel.parts:
            return self.send_error(400)
        resource_root = (PACKAGE / prefix.strip("/")).resolve()
        file_path = (resource_root / rel).resolve()
        if resource_root not in file_path.parents or not file_path.is_file():
            return self.send_error(404)
        content_type = fallback_type or mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
        self.send_bytes(file_path.read_bytes(), content_type)


def capture(server: ThreadingHTTPServer, args: argparse.Namespace) -> None:
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as error:
        raise RuntimeError("Capture requires the Python Playwright package and its Chromium browser") from error

    bound_host = server.server_address[0]
    origin_host = f"[{bound_host}]" if ":" in bound_host else bound_host
    origin = f"http://{origin_host}:{server.server_port}"
    with sqlite3.connect(f"file:{database_path()}?mode=ro", uri=True) as db:
        z, x, tms_y, original_tile = db.execute(
            "SELECT zoom_level,tile_column,tile_row,tile_data FROM tiles ORDER BY zoom_level,tile_column,tile_row LIMIT 1"
        ).fetchone()
    xyz_y = (1 << z) - 1 - tms_y
    with urlopen(f"{origin}/tiles/{z}/{x}/{xyz_y}.pbf", timeout=5) as response:
        served_tile = response.read()
        tile_encoding = response.headers.get("Content-Encoding")
    if served_tile != bytes(original_tile) or not served_tile.startswith(b"\x1f\x8b") or tile_encoding != "gzip":
        raise RuntimeError(f"XYZ-to-TMS/gzip tile validation failed for z{z}/{x}/{xyz_y}")
    requests: list[str] = []
    page_errors: list[str] = []
    console_errors: list[str] = []
    with sync_playwright() as playwright:
        chromium = args.chromium or "/usr/bin/chromium"
        browser = playwright.chromium.launch(
            executable_path=chromium,
            headless=True,
            args=["--no-sandbox", "--no-proxy-server", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"],
        )
        page = browser.new_page(viewport={"width": args.width, "height": args.height}, device_scale_factor=1)

        def track_request(request) -> None:
            requests.append(request.url)

        page.on("request", track_request)
        page.on("pageerror", lambda error: page_errors.append(str(error)))
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.goto(f"{origin}/?style={args.style}", wait_until="domcontentloaded", timeout=args.timeout * 1000)
        page.wait_for_function("window.__referenceReady !== undefined", timeout=args.timeout * 1000)
        page.wait_for_timeout(250)
        state = page.evaluate("({ready: window.__referenceReady, errors: window.__mapErrors})")
        external = [url for url in requests if not url.startswith(origin + "/")]
        tile_count = sum("/tiles/" in url and url.endswith(".pbf") for url in requests)
        glyph_count = sum("/glyphs/" in url for url in requests)
        sprite_count = sum("/sprites/" in url for url in requests)
        if not (tile_count and glyph_count and (sprite_count or not prepared_style(args.style, origin).get("sprite"))):
            browser.close()
            raise RuntimeError(json.dumps({"tileRequests": tile_count, "glyphRequests": glyph_count, "spriteRequests": sprite_count}, indent=2))
        if state["errors"] or page_errors or external:
            browser.close()
            raise RuntimeError(json.dumps({"mapErrors": state["errors"], "pageErrors": page_errors, "externalRequests": external}, indent=2))
        destination = Path(args.screenshot) if args.screenshot else ROOT / "docs/demo-maplibre-reference.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        page.screenshot(path=str(destination), animations="disabled")
        browser.close()
    print(json.dumps({
        "version": state["ready"]["version"],
        "style": state["ready"]["style"],
        "camera": state["ready"]["camera"],
        "viewport": [args.width, args.height],
        "tileRequests": tile_count,
        "glyphRequests": glyph_count,
        "spriteRequests": sprite_count,
        "xyzToTmsValidation": {"requested": [z, x, xyz_y], "storedTmsRow": tms_y, "contentEncoding": tile_encoding},
        "externalRequests": [],
        "screenshot": str(destination),
    }, indent=2))


def main() -> int:
    global PACKAGE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", action="store_true", help="render and save a headless browser screenshot, then exit")
    parser.add_argument("--style", choices=("day", "night"), default="day")
    parser.add_argument("--screenshot", help="screenshot path (default: docs/demo-maplibre-reference.png)")
    parser.add_argument("--host", default="127.0.0.1", help="bind address; loopback only by default")
    parser.add_argument("--port", type=int, default=0, help="HTTP port (0 selects an available ephemeral port)")
    parser.add_argument("--width", type=int, default=1100)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument("--timeout", type=int, default=45)
    parser.add_argument("--chromium", help="Chromium executable (default: /usr/bin/chromium)")
    parser.add_argument("--package", type=Path, default=PACKAGE, help="local single-source offline package directory")
    args = parser.parse_args()
    PACKAGE = args.package.resolve()
    if args.host not in ("127.0.0.1", "localhost", "::1"):
        parser.error("this local fixture server only binds to loopback")
    if not (WEB / "node_modules/maplibre-gl/dist/maplibre-gl.mjs").is_file():
        parser.error("MapLibre GL JS is not installed; run npm --prefix web/maplibre-reference ci")
    if not PACKAGE.is_dir():
        parser.error(f"authored MapLibre package is missing: {PACKAGE}")
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"MapLibre GL JS reference serving at http://{args.host}:{server.server_port}/ (Ctrl-C to stop)", flush=True)
    if args.capture:
        service = Thread(target=server.serve_forever, daemon=True)
        service.start()
        try:
            capture(server, args)
        finally:
            server.shutdown()
            server.server_close()
    else:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"maplibre-browser-reference: {error}", file=sys.stderr)
        raise SystemExit(1)
