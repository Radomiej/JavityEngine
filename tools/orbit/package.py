#!/usr/bin/env python3
"""Package an existing Javity/TeaVM web distribution; never execute game code."""
import argparse,hashlib,json,os,pathlib,re,shutil,tempfile,zipfile
from html import escape
from html.parser import HTMLParser
from urllib.parse import urlsplit
ROOT=pathlib.Path(__file__).resolve().parents[2]
MiB=1024*1024

def valid_path(name):
 return bool(re.fullmatch(r'([a-zA-Z0-9._@ -]+/)*[a-zA-Z0-9._@ -]+',name)) and not any(p in ('.','..') for p in name.split('/'))

def origins(values):
 if not values:raise ValueError('Explicit launcher origins are required')
 for value in values:
  u=urlsplit(value)
  if u.scheme not in ('https','http','tauri','capacitor') or not u.hostname or '*' in value or u.username or u.password or u.path or u.query or u.fragment or any(c.isspace() for c in value):raise ValueError('Invalid launcher origin')
 return values

class ExternalScripts(HTMLParser):
 def __init__(self,prefix):
  super().__init__(convert_charrefs=False);self.prefix=prefix;self.html=[];self.files={};self.script=None;self.injected=False;self.events=[]
 def handle_decl(self,v):self.html.append('<!'+v+'>')
 def handle_comment(self,v):self.handle_data('<!--'+v+'-->')
 def handle_entityref(self,v):self.handle_data('&'+v+';')
 def handle_charref(self,v):self.handle_data('&#'+v+';')
 def handle_starttag(self,tag,attrs):
  clean=[]
  for key,value in attrs:
   if key.lower().startswith('on'):
    if tag!='body' or key not in ('oncontextmenu','onload'):raise ValueError('Unsupported inline handler: '+tag+'.'+key)
    target='window' if key=='onload' else 'document'
    self.events.append(f'{target}.addEventListener({json.dumps(key[2:])},function(event){{if((function(event){{{value}}}).call(this,event)===false)event.preventDefault();}});')
   else:clean.append((key,value))
  if tag=='base':raise ValueError('A base URL is not supported by ORBIT CSP')
  if tag=='script' and not any(k=='src' for k,v in clean):
   if dict(clean).get('type','text/javascript') not in ('module','text/javascript','application/javascript'):raise ValueError('Unsupported inline script type')
   self.script=[clean,[]];return
  self.html.append('<'+tag+''.join(' '+k+(('="'+escape(v,quote=True)+'"') if v is not None else '') for k,v in clean)+'>')
  if tag=='head':
   self.injected=True
   for path in ('orbit-config.js','orbit-sdk.global.js','bridge.js'):
    self.html.append(f'<script src="{self.prefix}{path}"></script>')
 def handle_endtag(self,tag):
  if tag=='script' and self.script is not None:
   attrs,parts=self.script;name=f'inline-{len(self.files):03d}.js';self.files[name]=''.join(parts)
   self.html.append('<script src="'+self.prefix+name+'"'+''.join(' '+k+(('="'+escape(v,quote=True)+'"') if v is not None else '') for k,v in attrs)+'></script>');self.script=None;return
  if tag=='body' and self.events:
   self.files['events.js']='\n'.join(self.events);self.html.append(f'<script src="{self.prefix}events.js"></script>')
  self.html.append('</'+tag+'>')
 def handle_data(self,data):
  if self.script is not None:self.script[1].append(data)
  else:self.html.append(data)

def pack(source,output,metadata,launcher_origins):
 source=pathlib.Path(source).resolve();output=pathlib.Path(output).resolve();launcher_origins=origins(launcher_origins)
 if source==output or source in output.parents:raise ValueError('Output must be outside the input distribution')
 m=dict(metadata)
 if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}',str(m.get('id',''))) or not re.fullmatch(r'[a-zA-Z0-9._-]{1,64}',str(m.get('version',''))):raise ValueError('Invalid game id/version')
 if not valid_path(m.get('entrypoint','')):raise ValueError('Invalid entrypoint')
 if not (source/m['entrypoint']).is_file():raise ValueError('Missing entrypoint')
 if not source.is_dir():raise ValueError('Missing distribution')
 with tempfile.TemporaryDirectory(prefix='javity-orbit-') as tmp:
  stage=pathlib.Path(tmp);total=0;count=0;has_wasm=False
  for file in sorted(source.rglob('*')):
   name=file.relative_to(source).as_posix()
   if file.is_symlink() or not valid_path(name) or name=='nekko.json' or name=='__orbit' or name.startswith('__orbit/'):raise ValueError('Invalid/reserved path or symlink')
   if file.is_dir():continue
   size=file.stat().st_size;total+=size;count+=1
   if not file.is_file() or size>50*MiB or total>240*MiB or count>9900:raise ValueError('Distribution limits exceeded')
   if file.suffix=='.wasm':
    with file.open('rb') as f:
     if f.read(8)!=b'\0asm\1\0\0\0':raise ValueError('Invalid WebAssembly binary')
    has_wasm=True
   target=stage/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target)
  if not has_wasm:raise ValueError('Distribution contains no WebAssembly module')
  support=stage/'__orbit';support.mkdir()
  sdk=ROOT/'web/orbit/vendor/orbit-sdk.global.js';provenance=json.loads((sdk.parent/'provenance.json').read_text())
  if hashlib.sha256(sdk.read_bytes()).hexdigest()!=provenance['sha256']:raise ValueError('SDK checksum mismatch')
  shutil.copyfile(sdk,support/sdk.name);shutil.copyfile(ROOT/'web/orbit/bridge.js',support/'bridge.js')
  (support/'orbit-config.js').write_text('globalThis.JAVITY_ORBIT_CONFIG='+json.dumps({'launcherOrigins':launcher_origins},ensure_ascii=True)+';\n')
  entry=stage/m['entrypoint'];prefix=os.path.relpath(support,entry.parent).replace(os.sep,'/')+'/'
  parser=ExternalScripts(prefix);parser.feed(entry.read_text());parser.close()
  if not parser.injected or parser.script is not None:raise ValueError('Unsupported or incomplete HTML document')
  entry.write_text(''.join(parser.html))
  for name,code in parser.files.items():(support/name).write_text(code)
  if m.get('thumbnail')=='cover.svg' and not (stage/'cover.svg').exists():
   (stage/'cover.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360"><rect width="640" height="360" fill="#132538"/><text x="40" y="180" fill="#6fe0bd" font-family="sans-serif" font-size="42">Javity · WebAssembly</text></svg>')
  if not valid_path(m.get('thumbnail','')) or not (stage/m['thumbnail']).is_file():raise ValueError('Missing thumbnail')
  m.update(formatVersion=1,runtime='web',files=[])
  for file in sorted(stage.rglob('*')):
   if file.is_file():
    data=file.read_bytes();m['files'].append({'path':file.relative_to(stage).as_posix(),'size':len(data),'sha256':hashlib.sha256(data).hexdigest()})
  manifest=json.dumps(m,ensure_ascii=True,indent=2).encode()
  if len(manifest)>MiB or sum(f['size'] for f in m['files'])+len(manifest)>250*MiB or len(m['files'])+1>10000:raise ValueError('Package/manifest limits exceeded')
  output.parent.mkdir(parents=True,exist_ok=True);candidate=output.with_name(output.name+'.tmp')
  try:
   with zipfile.ZipFile(candidate,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
    for f in m['files']:archive.write(stage/f['path'],f['path'])
    archive.writestr('nekko.json',manifest)
   if candidate.stat().st_size>100*MiB:raise ValueError('Compressed package exceeds100MiB')
   candidate.replace(output)
  finally:candidate.unlink(missing_ok=True)
 return m

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=pathlib.Path,required=True);parser.add_argument('--output',type=pathlib.Path,required=True);parser.add_argument('--metadata',type=pathlib.Path,required=True);parser.add_argument('--launcher-origins',required=True)
 args=parser.parse_args()
 try:
  m=pack(args.source,args.output,json.loads(args.metadata.read_text()),args.launcher_origins.split(','));print(f"Packaged {m['id']}@{m['version']}: {len(m['files'])} files")
 except (ValueError,OSError) as error:raise SystemExit(str(error))
