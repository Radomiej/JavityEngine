#!/usr/bin/env python3
"""Optional Chromium QA: pip install playwright; use /usr/bin/chromium.
Run from any directory. Captures are disposable artifacts/site/ output.
"""
from pathlib import Path
import functools,http.server,threading,json
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];out=root/'artifacts/site';out.mkdir(parents=True,exist_ok=True)
class Quiet(http.server.SimpleHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_GET(self):
  if not self.path.startswith('/JavityEngine/'):
   self.send_error(404);return
  self.path=self.path.removeprefix('/JavityEngine')
  super().do_GET()
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Quiet,directory=str(root/'site')))
threading.Thread(target=server.serve_forever,daemon=True).start()
try:
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  page=browser.new_page(viewport={'width':1440,'height':1000},device_scale_factor=1)
  errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text) if m.type=='error' else None)
  page.goto(f'http://127.0.0.1:{server.server_port}/JavityEngine/',wait_until='networkidle')
  assert page.title().startswith('JavityEngine')
  assert page.locator('.example-tab[tabindex="0"]').count()==1
  page.evaluate('window.scrollTo(0,document.body.scrollHeight)');page.wait_for_timeout(300)
  assert page.locator('img').evaluate_all('(xs)=>xs.every(x=>x.complete && x.naturalWidth>0)')
  assert page.locator('.hero-visual img').evaluate('(x)=>Math.abs(x.clientWidth/x.clientHeight-16/9)<0.02 && getComputedStyle(x).objectFit==="cover"')
  for name,command in [('voxels','--voxels'),('shapes','--shapes'),('physics','--physics3d'),('map','--gorzow'),('relay','--mm')]:
   page.locator('#tab-'+name).click();assert command in page.locator('#example-command').inner_text()
  page.locator('#tab-voxels').focus();page.keyboard.press('ArrowDown');assert page.locator('#tab-shapes').get_attribute('aria-selected')=='true'
  page.keyboard.press('End');assert page.locator('#tab-relay').get_attribute('aria-selected')=='true'
  page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(out/'desktop.png'),full_page=True)
  page.screenshot(path=str(out/'desktop-top.png'))
  result={'desktop':True,'tabsAndKeyboard':True,'viewports':[]}
  for w in [360,390,768,1440]:
   page.set_viewport_size({'width':w,'height':900});page.wait_for_timeout(150)
   dims=page.evaluate('({viewport:innerWidth,scroll:document.documentElement.scrollWidth})');assert dims['scroll']<=dims['viewport'],dims
   result['viewports'].append(dims)
  page.set_viewport_size({'width':390,'height':844});page.evaluate('window.scrollTo(0,0)')
  page.locator('.menu-toggle').click();assert page.locator('.menu-toggle').get_attribute('aria-expanded')=='true'
  page.locator('#site-nav a[href="#examples"]').click();assert page.locator('.menu-toggle').get_attribute('aria-expanded')=='false'
  page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(out/'mobile.png'),full_page=True)
  page.screenshot(path=str(out/'mobile-top.png'))
  assert not errors,errors
  result.update(success=True,browser=browser.version,errors=errors)
  (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
  browser.close()
finally:server.shutdown();server.server_close()
