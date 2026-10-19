import importlib.util,json,pathlib,tempfile,threading,unittest
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('orbit_publish',ROOT/'publish.py');module=importlib.util.module_from_spec(spec)
try:spec.loader.exec_module(module)
except FileNotFoundError:module=None
class Publish(unittest.TestCase):
 def test_requires_https_and_never_follows_redirect_with_token(self):
  self.assertIsNotNone(module,'Publisher is missing')
  hit=[]
  class Handler(BaseHTTPRequestHandler):
   def log_message(self,*a):pass
   def do_POST(self):
    hit.append(self.path);self.rfile.read(int(self.headers['Content-Length']));self.send_response(307);self.send_header('Location','/stolen');self.end_headers()
  server=ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
  try:
   with tempfile.TemporaryDirectory() as d:
    package=pathlib.Path(d)/'test.nekko';package.write_bytes(b'PKtest');token=pathlib.Path(d)/'secret';token.write_text('fake-publishing-token')
    url=f'http://127.0.0.1:{server.server_port}'
    with self.assertRaises(ValueError):module.publish(package,url,token)
    with self.assertRaises(RuntimeError):module.publish(package,url,token,allow_http_local=True)
    self.assertEqual(hit,['/v1/admin/packages'])
  finally:server.shutdown();server.server_close()
if __name__=='__main__':unittest.main(verbosity=2)
