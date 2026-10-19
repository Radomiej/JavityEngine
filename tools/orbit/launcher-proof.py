#!/usr/bin/env python3
"""Real ORBIT UI + published Javity WASM integration. Requires an owned test account."""
import argparse,json,pathlib,urllib.parse
from playwright.sync_api import sync_playwright
from PIL import Image
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--chromium',default='/usr/bin/chromium');p.add_argument('--launcher-url',required=True);p.add_argument('--api-url',help='API base before /v1; defaults to launcher /api');p.add_argument('--session-file',type=pathlib.Path,required=True);p.add_argument('--game-id',default='javity-wasm-demo');p.add_argument('--evidence-dir',type=pathlib.Path,default=pathlib.Path('artifacts/orbit'));args=p.parse_args()
launcher=args.launcher_url.rstrip('/');api=(args.api_url or launcher+'/api').rstrip('/');token=args.session_file.read_text().strip();host=urllib.parse.urlsplit(api)
if not token or any(c.isspace() for c in token):raise SystemExit('Invalid session file')
args.evidence_dir.mkdir(parents=True,exist_ok=True)
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path=args.chromium,headless=True,args=['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
 try:
  context=browser.new_context(viewport={'width':1440,'height':1000})
  context.add_cookies([{'name':'orbit_session','value':token,'domain':host.hostname,'path':'/','httpOnly':True,'secure':host.scheme=='https','sameSite':'Lax'}])
  page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(launcher);page.get_by_role('button',name='Moja biblioteka',exact=True).click()
  def open_game():
   page.locator('[data-game-id="'+args.game_id+'"]').get_by_role('button',name='Zagraj w',exact=False).click()
   frame=page.locator('iframe').element_handle().content_frame()
   frame.wait_for_function("globalThis.javityWasm?.rendered>15 && globalThis.JavityOrbit?.state.javaPlayer && !document.querySelector('#orbit-panel button').disabled",timeout=60000)
   return frame
  frame=open_game();state=frame.evaluate('({scene:javityWasm,player:JavityOrbit.state.player,javaPlayer:JavityOrbit.state.javaPlayer,revision:JavityOrbit.state.revision})')
  assert state['player']['id']==state['javaPlayer']['id'] and state['scene']['webgl2'] and state['scene']['glError']==0
  before=state['scene']['x'];forward='d' if before<8 else 'a';backward='a' if forward=='d' else 'd'
  frame.locator('#canvas').click();page.keyboard.down(forward)
  try:frame.wait_for_function('x=>Math.abs(javityWasm.x-x)>.8',arg=before)
  finally:page.keyboard.up(forward)
  frame.get_by_role('button',name='Zapisz w chmurze').click();revision=state['revision']+1
  frame.wait_for_function('r=>JavityOrbit.state.revision===r',arg=revision);saved=frame.evaluate('javityWasm.x')
  frame.locator('#canvas').click();page.keyboard.down(backward)
  try:frame.wait_for_function('x=>Math.abs(javityWasm.x-x)>.8',arg=saved)
  finally:page.keyboard.up(backward)
  frame.get_by_role('button',name='Wczytaj zapis').click();frame.wait_for_function('x=>Math.abs(javityWasm.x-x)<.01',arg=saved)
  page.get_by_role('button',name='Biblioteka',exact=True).click();frame=open_game();frame.wait_for_function('x=>Math.abs(javityWasm.x-x)<.01',arg=saved)
  response=context.request.get(api+'/v1/games/'+args.game_id+'/saves/progress');value=response.json()
  assert response.status==200 and value['revision']==revision and abs(value['data']['x']-saved)<.01
  page.screenshot(path=str(args.evidence_dir/'launcher.png'),full_page=True)
  frame.locator('#spike-label').evaluate("e=>e.style.visibility='hidden'");frame.locator('#orbit-panel').evaluate("e=>e.style.visibility='hidden'")
  canvas=args.evidence_dir/'canvas.png';frame.locator('#canvas').screenshot(path=str(canvas));pixels=Image.open(canvas).convert('RGB')
  orange=green=0
  for r,g,b in pixels.getdata():
   orange+=r>130 and g>40 and b<100 and r>g*1.2
   green+=g>70 and g>r*1.5 and b>r*1.5
  assert orange>50 and green>50,(orange,green)
  assert not errors,errors
  evidence={'success':True,'gameId':args.game_id,'javaProfile':True,'saveRevision':revision,'savedX':saved,'reopenedX':frame.evaluate('javityWasm.x'),'webgl2':True,'glError':0,'orangePixels':orange,'greenPixels':green,'browser':'Chromium/SwiftShader','errors':errors}
  (args.evidence_dir/'verification.json').write_text(json.dumps(evidence,indent=2)+'\n');print('ORBIT UI + Java profile + Wasm rendering + save/load + reopen: PASS')
 finally:browser.close()
