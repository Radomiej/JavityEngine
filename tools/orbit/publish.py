#!/usr/bin/env python3
"""Publish a .nekko archive; never place account credentials in game assets."""
import argparse,json,os,pathlib,stat
from urllib.parse import urlsplit
from urllib.request import Request,build_opener,HTTPRedirectHandler,ProxyHandler
from urllib.error import HTTPError,URLError
class NoRedirect(HTTPRedirectHandler):
 def redirect_request(self,*args):return None

def publish(package,api_url,token_file,allow_http_local=False):
 package=pathlib.Path(package);token_file=pathlib.Path(token_file);url=urlsplit(api_url)
 local=url.hostname in ('localhost','127.0.0.1','::1') or (url.hostname or '').endswith('.localhost')
 if (url.scheme!='https' and not (allow_http_local and local and url.scheme=='http')) or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ('','/'):
  raise ValueError('ORBIT_API_URL must be an HTTPS origin (local HTTP requires explicit opt-in)')
 if package.is_symlink() or not package.is_file() or not 0<package.stat().st_size<=100*1024*1024:raise ValueError('Invalid package file/size')
 if token_file.is_symlink() or not token_file.is_file() or token_file.stat().st_size>4096:raise ValueError('Invalid token file')
 token=token_file.read_text().strip()
 if not token or any(c.isspace() for c in token):raise ValueError('Invalid publishing credential')
 handlers=[NoRedirect()]
 if allow_http_local and local:handlers.append(ProxyHandler({}))
 opener=build_opener(*handlers)
 try:
  with package.open('rb') as body:
   request=Request(api_url.rstrip('/')+'/v1/admin/packages',data=body,method='POST',headers={'Authorization':'Bearer '+token,'Content-Type':'application/zip','Content-Length':str(package.stat().st_size)})
   with opener.open(request,timeout=120) as response:
    result=response.read(65537)
    if len(result)>65536:raise RuntimeError('Oversized API response')
    result=json.loads(result)
    if not isinstance(result.get('gameId'),str) or not isinstance(result.get('version'),str) or result.get('active') is not True:raise RuntimeError('Unexpected publication response')
    return result
 except HTTPError as error:raise RuntimeError(f'ORBIT publication failed: HTTP {error.code}; redirects are not followed') from None
 except URLError:raise RuntimeError('ORBIT publication connection failed') from None
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--package',type=pathlib.Path,required=True);parser.add_argument('--api-url',default=os.environ.get('ORBIT_API_URL'));parser.add_argument('--token-file',type=pathlib.Path,default=os.environ.get('ORBIT_TOKEN_FILE'));parser.add_argument('--allow-http-local',action='store_true');args=parser.parse_args()
 if not args.api_url or not args.token_file:parser.error('Set ORBIT_API_URL and ORBIT_TOKEN_FILE')
 try:
  result=publish(args.package,args.api_url,args.token_file,args.allow_http_local);print('Published '+result['gameId']+'@'+result['version'])
 except (ValueError,OSError,RuntimeError) as error:raise SystemExit(str(error))
