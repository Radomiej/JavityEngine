#!/usr/bin/env python3
"""Generate the original CC0 fixtures; no downloaded artwork. Run from repository root."""
import json, struct, zlib, base64
from pathlib import Path
OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/org/javity/gltf/fixtures'

def png(rgba):
    def chunk(kind, body):
        return struct.pack('>I',len(body))+kind+body+struct.pack('>I',zlib.crc32(kind+body)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0'+bytes(rgba)))+chunk(b'IEND',b'')

def build(skinned):
    blob=bytearray(); views=[]; accessors=[]
    def view(data):
        while len(blob)%4: blob.append(0)
        offset=len(blob); blob.extend(data)
        views.append(dict(buffer=0,byteOffset=offset,byteLength=len(data)))
        return len(views)-1
    def acc(values,fmt,ctype,kind,count,**extra):
        accessors.append(dict(bufferView=view(struct.pack('<'+fmt,*values)),componentType=ctype,type=kind,count=count,**extra))
        return len(accessors)-1
    positions=acc([-.5,-.5,0,.5,-.5,0,.5,.5,0,-.5,.5,0],'12f',5126,'VEC3',4,min=[-.5,-.5,0],max=[.5,.5,0])
    normals=acc([0,0,1]*4,'12f',5126,'VEC3',4)
    uv=acc([0,0,1,0,1,1,0,1],'8f',5126,'VEC2',4)
    indices=acc([0,1,2,0,2,3],'6H',5123,'SCALAR',6)
    attrs={'POSITION':positions,'NORMAL':normals,'TEXCOORD_0':uv}
    nodes=[dict(name='mesh',mesh=0)]
    root=dict(asset=dict(version='2.0',generator='Javity CC0 fixture generator'),scene=0,scenes=[dict(nodes=[0])],nodes=nodes,
              meshes=[dict(primitives=[dict(attributes=attrs,indices=indices,material=0)])])
    if skinned:
        attrs['JOINTS_0']=acc([1,0,0,0]*4,'16B',5121,'VEC4',4)
        attrs['WEIGHTS_0']=acc([1,0,0,0]*4,'16f',5126,'VEC4',4)
        identity=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
        inverse=acc(identity*2,'32f',5126,'MAT4',2)
        nodes[0]['skin']=0
        nodes.extend([dict(name='root',children=[2]),dict(name='joint')])
        root['scenes'][0]['nodes'].append(1)
        root['skins']=[dict(joints=[1,2],inverseBindMatrices=inverse,skeleton=1)]
        times=acc([0,1],'2f',5126,'SCALAR',2,min=[0],max=[1])
        output=acc([0,0,0,0,.7,0],'6f',5126,'VEC3',2)
        root['animations']=[dict(name='lift',samplers=[dict(input=times,output=output,interpolation='LINEAR')],channels=[dict(sampler=0,target=dict(node=2,path='translation'))])]
    images=[]
    for pixel in [[220,50,25,255],[128,128,255,255],[255,180,32,255],[160,160,160,255],[20,8,4,255]]:
        images.append(dict(bufferView=view(png(pixel)),mimeType='image/png'))
    root['images']=images; root['textures']=[dict(source=i) for i in range(5)]
    root['materials']=[dict(name='complete-pbr',pbrMetallicRoughness=dict(baseColorFactor=[1,1,1,1],baseColorTexture=dict(index=0),metallicFactor=.2,roughnessFactor=.65,metallicRoughnessTexture=dict(index=2)),normalTexture=dict(index=1),occlusionTexture=dict(index=3),emissiveTexture=dict(index=4),emissiveFactor=[.1,.1,.1],doubleSided=True)]
    root['buffers']=[dict(byteLength=len(blob))];root['bufferViews']=views;root['accessors']=accessors
    raw=json.dumps(root,separators=(',',':')).encode();raw+=b' '*((-len(raw))%4)
    blob+=b'\0'*((-len(blob))%4)
    chunks=struct.pack('<II',len(raw),0x4e4f534a)+raw+struct.pack('<II',len(blob),0x004e4942)+blob
    (OUT/('skinned.glb' if skinned else 'static.glb')).write_bytes(struct.pack('<III',0x46546c67,2,12+len(chunks))+chunks)

    if not skinned:
        invalid = json.loads(raw)
        primitive = dict(invalid['meshes'][0]['primitives'][0])
        primitive['attributes'] = dict(primitive['attributes'])
        primitive['attributes']['NORMAL'] = uv  # fails after the first primitive allocated a mesh
        invalid['meshes'][0]['primitives'].append(primitive)
        bad = json.dumps(invalid,separators=(',',':')).encode();bad+=b' '*((-len(bad))%4)
        chunks=struct.pack('<II',len(bad),0x4e4f534a)+bad+struct.pack('<II',len(blob),0x004e4942)+blob
        (OUT/'invalid-after-mesh.glb').write_bytes(struct.pack('<III',0x46546c67,2,12+len(chunks))+chunks)
        root['buffers'][0]['byteLength']=len(blob)
        root['buffers'][0]['uri']='data:application/octet-stream;base64,'+base64.b64encode(blob).decode()
        (OUT/'static.gltf').write_text(json.dumps(root,separators=(',',':'))+'\n')

OUT.mkdir(parents=True,exist_ok=True)
build(False);build(True)
