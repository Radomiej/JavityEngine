#!/usr/bin/env python3
"""Verify the real gdx-teavm app, with DOM-free pixels and a negative GL control."""
import argparse
import json
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
DISABLE_DRAWS = """
for (const name of ['drawArrays', 'drawElements', 'drawArraysInstanced', 'drawElementsInstanced']) {
  WebGL2RenderingContext.prototype[name] = function () {};
}
"""


def check_framebuffer(page, canvas, output):
    # An element screenshot can still include overlapping DOM. Hide the label explicitly.
    label = page.locator('#spike-label')
    previous_visibility = label.evaluate("element => element.style.visibility")
    try:
        label.evaluate("element => { element.style.visibility = 'hidden'; }")
        canvas.screenshot(path=str(output / 'framebuffer.png'))
    finally:
        label.evaluate('(element, value) => { element.style.visibility = value; }', previous_visibility)
    pixels = Image.open(output / 'framebuffer.png').convert('RGB')
    colors = pixels.resize((120, 90)).getcolors(10800) or []
    orange = green = 0
    data = iter(pixels.tobytes())
    for r, g, b in zip(data, data, data):
        orange += r > 130 and g > 40 and b < 100 and r > g * 1.2
        green += g > 70 and g > r * 1.5 and b > r * 1.5
    assert len(colors) > 30, ('empty/flat canvas framebuffer', len(colors))
    assert orange > 50 and green > 50, ('missing expected sphere/cube pixels', orange, green)
    return {'colors': len(colors), 'orangePixels': orange, 'greenPixels': green, 'domLabelExcluded': True}


def verify(page, output, url):
    response = page.goto(url, wait_until='domcontentloaded')
    assert response and response.status == 200, 'generated webapp did not load'
    page.wait_for_function('globalThis.javityWasm?.rendered > 10', timeout=45000)
    first = page.evaluate('javityWasm')
    assert first['renderer'] == 'org.javity.render.GdxRenderer' and first['webgl2'] and first['glError'] == 0, first
    canvas = page.locator('#canvas')
    canvas.click()
    page.keyboard.down('d')
    try:
        page.wait_for_function('javityWasm.x > 1', timeout=10000)
    finally:
        page.keyboard.up('d')
    page.wait_for_timeout(250)
    moved = page.evaluate('javityWasm')
    assert moved['x'] > first['x'] + 1 and moved['frame'] > first['frame'], moved
    framebuffer = check_framebuffer(page, canvas, output)
    page.screenshot(path=str(output / 'javity-wasm.png'), full_page=True)
    page.set_viewport_size({'width': 760, 'height': 620})
    page.wait_for_function('javityWasm.width < 900 && javityWasm.height < 700')
    resized = page.evaluate('javityWasm')
    assert resized['glError'] == 0, resized
    page.wait_for_timeout(150)
    stable = page.evaluate('javityWasm')
    assert abs(stable['x'] - moved['x']) < .3, 'keyup did not release GDX input'
    context = page.evaluate("""() => {
      const gl = document.querySelector('#canvas').getContext('webgl2');
      return {version: gl.getParameter(gl.VERSION), renderer: gl.getParameter(gl.RENDERER),
              shadingLanguage: gl.getParameter(gl.SHADING_LANGUAGE_VERSION)};
    }""")
    page.evaluate('globalThis.javityWasmStop = true')
    page.wait_for_function('javityWasm.disposed === true')
    disposed = page.evaluate('javityWasm')
    assert disposed['frame'] == -1 and disposed['glError'] == 0, disposed
    page.wait_for_timeout(150)
    assert page.evaluate('javityWasm.rendered') == disposed['rendered'], 'scene rendering continued after dispose'
    return {'httpStatus': response.status, 'initial': first, 'afterInput': moved,
            'resized': resized, 'disposed': disposed, 'gl': context, 'framebuffer': framebuffer}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disable-draw', action='store_true', help='Negative control: disable GL draws; must fail.')
    parser.add_argument('--evidence-dir', type=Path, default=ROOT / 'evidence')
    args = parser.parse_args()
    web = ROOT / 'build/dist/wasm'
    index = web / 'webapp/index.html'
    if not index.is_file():
        raise SystemExit('Build first: ./gradlew -p experiments/javity-wasm gdx_teavm_web_wasm_build')
    output = args.evidence_dir
    output.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(SimpleHTTPRequestHandler, directory=str(web)))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    errors, messages, failed_requests = [], [], []
    result = {'scope': 'Actual Javity GdxRenderer on gdx-teavm Wasm GC', 'success': False,
              'negativeControl': args.disable_draw}
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(executable_path='/usr/bin/chromium', headless=True,
                args=['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
            try:
                page = browser.new_page(viewport={'width': 1100, 'height': 800})
                if args.disable_draw:
                    page.add_init_script(DISABLE_DRAWS)
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.on('console', lambda message: messages.append({'type': message.type, 'text': message.text}))
                page.on('requestfailed', lambda request: failed_requests.append({'url': request.url, 'failure': request.failure}))
                try:
                    result.update(verify(page, output, f'http://127.0.0.1:{server.server_port}/webapp/index.html'))
                    assert not errors, errors
                    assert not [message for message in messages if message['type'] == 'error'], messages
                    assert not failed_requests, failed_requests
                    result.update(success=True, browser=browser.version,
                                  rendering='ANGLE/SwiftShader software WebGL2 in headless Chromium')
                except Exception as error:
                    result['error'] = str(error)
                    try:
                        page.screenshot(path=str(output / 'failure.png'), full_page=True)
                    except Exception:
                        pass  # Preserve the original failure if the page itself has closed.
                    raise
                finally:
                    result.update(pageErrors=errors, console=messages, failedRequests=failed_requests)
                    # A failed run replaces stale PASS evidence rather than leaving it as latest.
                    (output / 'runtime.json').write_text(json.dumps(result, indent=2) + '\n')
            finally:
                browser.close()
        print(json.dumps(result, indent=2))
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


if __name__ == '__main__':
    main()
