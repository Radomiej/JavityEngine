#!/usr/bin/env python3
"""Package an already-built JAR and supplied Windows x64 JRE; no runtime downloads."""
import argparse
import hashlib
import struct
import zipfile
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--runtime',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
jar=root/'games/gatekeepers/build/libs/Straznicy-Bram.jar'
args.output.mkdir(parents=True,exist_ok=True)
prefix='Straznicy-Bram-Windows-x64/'
destination=args.output/'Straznicy-Bram-Windows-x64.zip'
with zipfile.ZipFile(args.runtime) as runtime:
    java=next(n for n in runtime.namelist() if n.endswith('/bin/java.exe'))
    data=runtime.read(java)
    offset=struct.unpack_from('<I',data,0x3c)[0]
    assert data[:2]==b'MZ' and data[offset:offset+4]==b'PE\0\0'
    assert struct.unpack_from('<H',data,offset+4)[0]==0x8664,'Runtime must be Windows x64'
    runtime_root=java[:-len('bin/java.exe')]
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as bundle:
        bundle.write(jar,prefix+'Straznicy-Bram.jar')
        script=(root/'games/gatekeepers/windows/Start.bat').read_text().replace('\r\n','\n').replace('\n','\r\n')
        bundle.writestr(prefix+'Start.bat',script.encode('ascii'))
        bundle.write(root/'docs/gatekeepers-windows.txt',prefix+'CZYTAJ.txt')
        for filename in ['LICENSE','THIRD_PARTY.md']:bundle.write(root/filename,prefix+filename)
        for entry in runtime.infolist():
            if entry.is_dir() or not entry.filename.startswith(runtime_root):continue
            name=entry.filename[len(runtime_root):]
            assert '..' not in Path(name).parts and not name.startswith(('/', '\\'))
            bundle.writestr(prefix+'runtime/'+name,runtime.read(entry))
        bundle.writestr(prefix+'BUILD.txt','Javity base: 95b951c249d59d2c7285701e6e2610933c71b75d\nPvE version: 0.2.0\nJAR SHA256: '+hashlib.sha256(jar.read_bytes()).hexdigest()+'\nRuntime SHA256: '+hashlib.sha256(args.runtime.read_bytes()).hexdigest()+'\n')
print(destination)
with zipfile.ZipFile(destination) as bundle:
    assert bundle.testzip() is None
    assert prefix+'runtime/bin/java.exe' in bundle.namelist()
    assert prefix+'runtime/bin/server/jvm.dll' in bundle.namelist()
print('PASS: Windows runtime, JVM, launcher and archive integrity')
