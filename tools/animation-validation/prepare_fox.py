"""Offline Fox-only fixture conversion and independent glTF skinning oracle, not a runtime loader."""
from pathlib import Path
import json, hashlib, numpy as np
from scipy.spatial.transform import Rotation, Slerp
BASE = Path(__file__).resolve().parent / 'fox'
g = json.loads((BASE/'Fox.gltf').read_text())
blob = (BASE/'Fox.bin').read_bytes()
def acc(i):
 a=g['accessors'][i];v=g['bufferViews'][a['bufferView']]
 assert 'sparse' not in a and not a.get('normalized',False)
 dtype={5126:'<f4',5123:'<u2',5121:'u1',5125:'<u4'}[a['componentType']]
 n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
 size=np.dtype(dtype).itemsize
 return np.ndarray((a['count'],n),dtype,blob,offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',n*size),size)).copy()
def trs(n):
 m=np.eye(4);m[:3,:3]=Rotation.from_quat(n.get('rotation',[0,0,0,1])).as_matrix() @ np.diag(n.get('scale',[1,1,1]));m[:3,3]=n.get('translation',[0,0,0]);return m
def split(m):
 scale=np.linalg.norm(m[:3,:3],axis=0);r=m[:3,:3]/scale
 if np.linalg.det(r)<0:scale[0]*=-1;r[:,0]*=-1
 result={'translation':m[:3,3].tolist(),'rotation':Rotation.from_matrix(r).as_quat().tolist(),'scale':scale.tolist()}
 assert np.max(np.abs(trs(result)-m))<.001
 return result
p=g['meshes'][0]['primitives'][0]; assert len(g['meshes'])==1 and len(g['skins'])==1 and p.get('mode',4)==4
assert set(p['attributes'])=={'POSITION','TEXCOORD_0','JOINTS_0','WEIGHTS_0'}
pos=acc(p['attributes']['POSITION']);uv=acc(p['attributes']['TEXCOORD_0']);joints=acc(p['attributes']['JOINTS_0']);weights=acc(p['attributes']['WEIGHTS_0'])
assert np.max(np.abs(weights.sum(axis=1)-1))<1e-5
indices=acc(p['indices']).reshape(-1).astype(int) if 'indices' in p else np.arange(len(pos))
assert indices.max()<65536
normals=np.zeros_like(pos)
for tri in indices.reshape(-1,3):
 a,b,c=pos[tri];normal=np.cross(b-a,c-a)
 normals[tri]+=normal
length=np.linalg.norm(normals,axis=1); assert length.min()>0
normals/=length[:,None]
verts=np.concatenate([pos,normals,uv,*[np.column_stack([joints[:,k],weights[:,k]]) for k in range(4)]],axis=1)
skin=g['skins'][0];ibm=acc(skin['inverseBindMatrices']).reshape((-1,4,4)).transpose(0,2,1)
bones=[{'node':g['nodes'][n]['name'],**split(np.linalg.inv(ibm[k]))} for k,n in enumerate(skin['joints'])]
def node(i):
 n=g['nodes'][i];r={'id':n['name']}
 assert 'matrix' not in n
 for key in ['translation','rotation','scale']:
  if key in n:r[key]=n[key]
 if 'children' in n:r['children']=[node(c) for c in n['children']]
 if 'mesh' in n:r['parts']=[{'meshpartid':'fox-mesh','materialid':'fox-material','bones':bones}]
 return r
animations=[]
for a in g['animations']:
 channels={}
 for c in a['channels']:
  sampler=a['samplers'][c['sampler']];assert sampler.get('interpolation','LINEAR')=='LINEAR'
  path=c['target']['path']; assert path in ['translation','rotation','scale']
  frames=[{'keytime':float(t[0])*1000,'value':v.tolist()} for t,v in zip(acc(sampler['input']),acc(sampler['output']))]
  channels.setdefault(c['target']['node'],{})[{'scale':'scaling'}.get(path,path)]=frames
 animations.append({'id':a['name'],'bones':[{'boneId':g['nodes'][i]['name'],**ch} for i,ch in channels.items()]})
model={'version':[0,1],'id':'licensed-fox-fixture','meshes':[{'attributes':['POSITION','NORMAL','TEXCOORD0',*[f'BLENDWEIGHT{k}' for k in range(4)]],'vertices':verts.ravel().tolist(),'parts':[{'id':'fox-mesh','type':'TRIANGLES','indices':indices.tolist()}]}],'materials':[{'id':'fox-material','diffuse':[1,1,1,1],'textures':[{'id':'fox-texture','filename':'Texture.png','type':'DIFFUSE'}]}],'nodes':[node(i) for i in g['scenes'][g.get('scene',0)]['nodes']],'animations':animations}
(BASE/'Fox.g3dj').write_text(json.dumps(model,separators=(',',':'),allow_nan=False))
# Oracle samples the ORIGINAL glTF channels, not the converted G3DJ or Javity code.
def oracle(name,time):
 nodes=[dict(n) for n in g['nodes']]
 anim=next(a for a in g['animations'] if a['name']==name)
 for c in anim['channels']:
  s=anim['samplers'][c['sampler']];times=acc(s['input']).ravel().astype(float);values=acc(s['output']).astype(float);t=float(np.clip(time,times[0],times[-1]));path=c['target']['path']
  value=Slerp(times,Rotation.from_quat(values))([t]).as_quat()[0] if path=='rotation' else np.array([np.interp(t,times,values[:,k]) for k in range(values.shape[1])])
  nodes[c['target']['node']][path]=value
 globals={}
 def visit(i,parent):
  globals[i]=parent@trs(nodes[i])
  for ch in nodes[i].get('children',[]):visit(ch,globals[i])
 for i in g['scenes'][g.get('scene',0)]['nodes']:visit(i,np.eye(4))
 matrices=np.array([globals[n]@ibm[k] for k,n in enumerate(skin['joints'])])
 out=np.zeros((len(pos),3));homogeneous=np.column_stack([pos,np.ones(len(pos))])
 for k in range(4):out+=np.einsum('nij,nj->ni',matrices[joints[:,k]],homogeneous)[:,:3]*weights[:,k,None]
 return {'vertices':out.ravel().tolist(),'poses':{n['name']:{k:np.asarray(n.get(k,default)).tolist() for k,default in [('translation',[0,0,0]),('rotation',[0,0,0,1]),('scale',[1,1,1])]} for n in nodes}}
samples=[{'clip':name,'time':time,**oracle(name,time)} for name in ['Survey','Walk','Run'] for time in [0,.25,.5]]
for a in g['animations']:
 duration=max(float(acc(s['input'])[-1,0]) for s in a['samplers'])
 samples.append({'clip':a['name'],'time':duration*.25,**oracle(a['name'],duration*.25)})
(BASE/'oracle.json').write_text(json.dumps({'samples':samples},separators=(',',':')))
(BASE/'hashes.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in BASE.iterdir() if p.name not in ['hashes.json']},indent=2)+'\n')
print('Prepared Fox: vertices',len(pos),'joints',len(bones),'clips',[a['id'] for a in animations],'oracle samples',len(samples))
