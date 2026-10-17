#!/usr/bin/env python3
"""Runs the actual gdx-teavm-generated Javity webapp, not a replacement JS renderer."""
import hashlib, json, threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright
from PIL import Image

root = Path(__file__).resolve().parents[1]
web = root/'build/dist/wasm'
index = next(web.rglob('index.html'), None)
if index is None: raise SystemExit('Build first: ./gradlew -p experiments/javity-wasm gdx_teavm_web_wasm_build')
out = root/'evidence'; out.mkdir(exist_ok=True)
server = ThreadingHTTPServer(('127.0.0.1',0), partial(SimpleHTTPRequestHandler,directory=str(web)))
thread = threading.Thread(target=server.serve_forever,daemon=True); thread.start()
errors = []; messages = []; failed_requests = []
result = {'scope':'Javity GdxRenderer + Game/Component/ModelRenderer on gdx-teavm Wasm GC', 'success':False}
try:
 with sync_playwright() as pw:
  browser = pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,
      args=['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
  page = browser.new_page(viewport={'width':1100,'height':800})
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.on('console',lambda e:messages.append({'type':e.type,'text':e.text}))
  page.on('requestfailed',lambda r:failed_requests.append({'url':r.url,'failure':r.failure}))
  try:
   response = page.goto(f'http://127.0.0.1:{server.server_port}/{index.relative_to(web).as_posix()}',wait_until='domcontentloaded')
   page.wait_for_function('globalThis.javityWasm?.rendered > 10',timeout=45000)
   first = page.evaluate('javityWasm')
   assert first['renderer']=='org.javity.render.GdxRenderer' and first['webgl2'] and first['glError']==0,first
   canvas=page.locator('#canvas');canvas.click()
   page.keyboard.down('d')
   page.wait_for_function('javityWasm.x > 1',timeout=10000)
   page.keyboard.up('d');page.wait_for_timeout(250)
   moved=page.evaluate('javityWasm')
   assert moved['x']>first['x']+1 and moved['frame']>first['frame'],moved
   page.screenshot(path=str(out/'javity-wasm.png'),full_page=True)
   pixels=Image.open(out/'javity-wasm.png').convert('RGB')
   color_count=len(pixels.resize((120,90)).getcolors(10800) or [])
   assert color_count>30,('empty/flat framebuffer',color_count)
   page.set_viewport_size({'width':760,'height':620})
   page.wait_for_function('javityWasm.width < 900 && javityWasm.height < 700')
   resized=page.evaluate('javityWasm');assert resized['glError']==0,resized
   page.wait_for_timeout(150);stable=page.evaluate('javityWasm')
   assert abs(stable['x']-moved['x'])<.3,'keyup did not release GDX input'
   context=page.evaluate("""() => {const gl=document.querySelector('#canvas').getContext('webgl2'); return {version:gl.getParameter(gl.VERSION),renderer:gl.getParameter(gl.RENDERER),shadingLanguage:gl.getParameter(gl.SHADING_LANGUAGE_VERSION)}}""")
   page.evaluate('globalThis.javityWasmStop=true')
   page.wait_for_function('javityWasm.disposed === true')
   disposed=page.evaluate('javityWasm');assert disposed['frame']==-1 and disposed['glError']==0,disposed
   assert not errors,errors
   assert not [m for m in messages if m['type']=='error'],messages
   assert not failed_requests,failed_requests
   result.update(success=True,browser=browser.version,rendering='ANGLE/SwiftShader software WebGL2 in headless Chromium',httpStatus=response.status,initial=first,afterInput=moved,resized=resized,disposed=disposed,gl=context,framebufferColorCount=color_count)
  except Exception as error:
   page.screenshot(path=str(out/'failure.png'),full_page=True)
   result['error']=str(error)
   raise
  finally:
   result.update(pageErrors=errors,console=messages,failedRequests=failed_requests)
   (out/('runtime.json' if result['success'] else 'failure.json')).write_text(json.dumps(result,indent=2)+'\n')
   browser.close()
 print(json.dumps(result,indent=2))
finally:
 server.shutdown();server.server_close();thread.join(timeout=2)
