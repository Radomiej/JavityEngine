import hashlib,importlib.util,json,pathlib,tempfile,unittest,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('orbit_package',ROOT/'package.py')
module=importlib.util.module_from_spec(spec)
try:spec.loader.exec_module(module)
except FileNotFoundError:module=None
class Package(unittest.TestCase):
 def fixture(self,base):
  source=pathlib.Path(base)/'web';source.mkdir();(source/'webapp').mkdir()
  (source/'webapp/index.html').write_text('<!doctype html><html><head><script src="app.wasm-runtime.js"></script><script>window.boot=true;</script></head><body oncontextmenu="return false"><canvas id="canvas"></canvas></body></html>')
  (source/'webapp/app.wasm').write_bytes(b'\0asm\1\0\0\0');(source/'webapp/app.wasm-runtime.js').write_text('window.runtime=true;')
  metadata={'id':'javity-test','version':'1.0.0','name':'Javity Test','description':'test','genre':'Test','entrypoint':'webapp/index.html','thumbnail':'cover.svg','orientations':['landscape'],'achievements':[]}
  return source,metadata,pathlib.Path(base)/'game.nekko'
 def test_packages_complete_runtime_hashes_and_external_scripts(self):
  self.assertIsNotNone(module,'ORBIT packager is missing')
  with tempfile.TemporaryDirectory() as d:
   src,meta,out=self.fixture(d);module.pack(src,out,meta,['https://play.example.com'])
   with zipfile.ZipFile(out) as z:
    m=json.loads(z.read('nekko.json'));self.assertEqual((m['runtime'],m['formatVersion']),('web',1))
    self.assertEqual(z.read('webapp/app.wasm'),b'\0asm\1\0\0\0')
    html=z.read('webapp/index.html').decode();self.assertNotIn('<script>window.boot',html);self.assertNotIn('oncontextmenu=',html);self.assertIn('orbit-sdk.global.js',html)
    self.assertTrue(any(z.read(f).find(b'window.boot=true;')>=0 for f in z.namelist() if f.endswith('.js')))
    for f in m['files']:self.assertEqual(hashlib.sha256(z.read(f['path'])).hexdigest(),f['sha256']);self.assertEqual(len(z.read(f['path'])),f['size'])
    self.assertFalse(any('token' in p.lower() or p.startswith('/') for p in z.namelist()))
 def test_rejects_wildcard_origin_and_symlink(self):
  self.assertIsNotNone(module,'ORBIT packager is missing')
  with tempfile.TemporaryDirectory() as d:
   src,meta,out=self.fixture(d)
   with self.assertRaises(ValueError):module.pack(src,out,meta,['*'])
   (src/'evil').symlink_to('/etc/passwd')
   with self.assertRaises(ValueError):module.pack(src,out,meta,['https://play.example.com'])
 def test_rejects_missing_wasm(self):
  self.assertIsNotNone(module,'ORBIT packager is missing')
  with tempfile.TemporaryDirectory() as d:
   src,meta,out=self.fixture(d);(src/'webapp/app.wasm').unlink()
   with self.assertRaises(ValueError):module.pack(src,out,meta,['https://play.example.com'])
 def test_rejects_unknown_inline_handler(self):
  with tempfile.TemporaryDirectory() as d:
   src,meta,out=self.fixture(d)
   (src/'webapp/index.html').write_text('<html><head></head><body onclick="alert(1)"></body></html>')
   with self.assertRaises(ValueError):module.pack(src,out,meta,['https://play.example.com'])
if __name__=='__main__':unittest.main(verbosity=2)
