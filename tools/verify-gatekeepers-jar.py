#!/usr/bin/env python3
"""Inspect the shipped executable artifact, including Windows x64 PE headers and Yoga SPI."""
import argparse
import struct
import zipfile
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('jar',type=Path)
args=parser.parse_args()
with zipfile.ZipFile(args.jar) as jar:
    names=set(jar.namelist())
    manifest=jar.read('META-INF/MANIFEST.MF').decode()
    assert 'Main-Class: org.javity.gatekeepers.GatekeepersLauncher' in manifest
    assert 'Enable-Native-Access: ALL-UNNAMED' in manifest
    assert 'Multi-Release: true' in manifest
    assert 'org/javity/ui/fonts/OpenSans-Regular.ttf' in names
    assert b'LwjglYogaBackendProvider' in jar.read('META-INF/services/org.javity.ui.yoga.YogaBackendProvider')
    required=['gdx64.dll','gdx-freetype64.dll',
      'windows/x64/org/lwjgl/lwjgl.dll',
      'windows/x64/org/lwjgl/glfw/glfw.dll',
      'windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll',
      'windows/x64/org/lwjgl/jemalloc/jemalloc.dll',
      'windows/x64/org/lwjgl/stb/lwjgl_stb.dll',
      'windows/x64/org/lwjgl/yoga/lwjgl_yoga.dll']
    for name in required:
        data=jar.read(name)
        assert data[:2]==b'MZ',name
        offset=struct.unpack_from('<I',data,0x3c)[0]
        assert data[offset:offset+4]==b'PE\0\0',name
        assert struct.unpack_from('<H',data,offset+4)[0]==0x8664,name
        print('PASS: Windows x64 native',name)
    assert any(n.startswith('META-INF/licenses/') for n in names)
    assert jar.testzip() is None
    print('PASS: executable manifest, font, Yoga provider, licenses and ZIP integrity')
