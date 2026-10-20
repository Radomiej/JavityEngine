#!/usr/bin/env python3
"""Original CC0 articulated block humanoid, authored for exact-path avatar examples.
Run from any directory. No downloaded art, textures, rig or animation data.
"""
import json
import math
from pathlib import Path
import struct

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/org/javity/gltf/fixtures/avatar.glb'
blob = bytearray()
views, accessors, meshes = [], [], []


def accessor(values, fmt, component, kind, count, **extra):
    while len(blob) % 4:
        blob.append(0)
    offset = len(blob)
    data = struct.pack('<' + str(len(values)) + fmt, *values)
    blob.extend(data)
    views.append(dict(buffer=0, byteOffset=offset, byteLength=len(data)))
    accessors.append(dict(bufferView=len(views)-1, componentType=component,
                          type=kind, count=count, **extra))
    return len(accessors)-1


def box(center, size, material, joint=None):
    x, y, z = center
    a, b, c = [v/2 for v in size]
    positions, normals, indices = [], [], []
    # Each face has its own vertices and authored flat normals, CCW viewed outside.
    faces = [((0,0,1), [(-a,-b,c),(a,-b,c),(a,b,c),(-a,b,c)]),
             ((0,0,-1), [(a,-b,-c),(-a,-b,-c),(-a,b,-c),(a,b,-c)]),
             ((1,0,0), [(a,-b,c),(a,-b,-c),(a,b,-c),(a,b,c)]),
             ((-1,0,0), [(-a,-b,-c),(-a,-b,c),(-a,b,c),(-a,b,-c)]),
             ((0,1,0), [(-a,b,c),(a,b,c),(a,b,-c),(-a,b,-c)]),
             ((0,-1,0), [(-a,-b,-c),(a,-b,-c),(a,-b,c),(-a,-b,c)])]
    for normal, vertices in faces:
        start = len(positions)//3
        for px, py, pz in vertices:
            positions += [x+px, y+py, z+pz]
            normals += normal
        indices += [start, start+1, start+2, start, start+2, start+3]
    attrs = dict(POSITION=accessor(positions,'f',5126,'VEC3',24,
                                  min=[x-a,y-b,z-c], max=[x+a,y+b,z+c]),
                 NORMAL=accessor(normals,'f',5126,'VEC3',24))
    if joint is not None:
        attrs['JOINTS_0'] = accessor([joint,0,0,0]*24,'B',5121,'VEC4',24)
        attrs['WEIGHTS_0'] = accessor([1,0,0,0]*24,'f',5126,'VEC4',24)
    meshes.append(dict(primitives=[dict(attributes=attrs,
        indices=accessor(indices,'H',5123,'SCALAR',36), material=material)]))
    return len(meshes)-1


# Stable globally unique names are deliberately explicit, never inferred as humanoid anatomy.
nodes = [dict(name='Avatar',children=[1]),
         dict(name='Hips',translation=[0,.9,0],children=[2,7,8]),
         dict(name='Spine',translation=[0,.4,0],children=[3,4,5]),
         dict(name='HeadBone',translation=[0,.5,0]),
         dict(name='LeftArmBone',translation=[-.48,.25,0]),
         dict(name='RightArmBone',translation=[.48,.25,0],children=[6]),
         dict(name='RightHand',translation=[0,-.55,0],children=[9]),
         dict(name='LeftLegBone',translation=[-.19,-.45,0]),
         dict(name='RightLegBone',translation=[.19,-.45,0]),
         dict(name='Tool',translation=[0,-.08,.14],mesh=box((0,0,.12),(.16,.16,.55),4))]
joints = list(range(1,9))
world_positions = [(0,.9,0),(0,1.3,0),(0,1.8,0),(-.48,1.55,0),(.48,1.55,0),
                   (.48,1,0),(-.19,.45,0),(.19,.45,0)]
inverse = []
for x,y,z in world_positions:
    inverse += [1,0,0,0, 0,1,0,0, 0,0,1,0, -x,-y,-z,1]
skin = dict(joints=joints,skeleton=1,
            inverseBindMatrices=accessor(inverse,'f',5126,'MAT4',len(joints)))
for name, center, size, material, joint in [
    ('Torso',(0,1.25,0),(.68,.7,.36),0,1),
    ('Head',(0,1.84,0),(.44,.43,.4),1,2),
    ('LeftArm',(-.48,1.26,0),(.23,.68,.28),2,3),
    ('RightArm',(.48,1.26,0),(.23,.68,.28),2,4),
    ('LeftLeg',(-.19,.45,0),(.28,.8,.31),3,6),
    ('RightLeg',(.19,.45,0),(.28,.8,.31),3,7),
    ('Visor',(0,1.89,.211),(.31,.10,.04),4,2)]:
    nodes[0]['children'].append(len(nodes))
    nodes.append(dict(name=name,mesh=box(center,size,material,joint),skin=0))
times = accessor([0,.5,1],'f',5126,'SCALAR',3,min=[0],max=[1])
rotations=[]
for angle in [0, -65, 0]:
    rotations += [math.sin(math.radians(angle)/2),0,0,math.cos(math.radians(angle)/2)]
output = accessor(rotations,'f',5126,'VEC4',3)
materials=[]
for name,color in [('jacket',[.12,.42,.43,1]),('face',[.9,.67,.41,1]),
                   ('sleeves',[.2,.63,.6,1]),('trousers',[.12,.2,.28,1]),
                   ('tool-and-visor',[.95,.57,.15,1])]:
    materials.append(dict(name=name,pbrMetallicRoughness=dict(baseColorFactor=color,
        metallicFactor=.05,roughnessFactor=.72)))
root=dict(asset=dict(version='2.0',generator='Javity original CC0 block humanoid'),
          scene=0,scenes=[dict(nodes=[0])],nodes=nodes,meshes=meshes,skins=[skin],
          materials=materials,buffers=[dict(byteLength=len(blob))],bufferViews=views,
          accessors=accessors,animations=[dict(name='tool-salute',
              samplers=[dict(input=times,output=output,interpolation='LINEAR')],
              channels=[dict(sampler=0,target=dict(node=5,path='rotation'))])])
raw=json.dumps(root,separators=(',',':')).encode()
raw += b' '*((-len(raw))%4)
blob += b'\0'*((-len(blob))%4)
chunks=struct.pack('<II',len(raw),0x4e4f534a)+raw+struct.pack('<II',len(blob),0x004e4942)+blob
OUT.write_bytes(struct.pack('<III',0x46546c67,2,12+len(chunks))+chunks)
print(f'{OUT.name}: {len(chunks)+12} bytes; 8-joint humanoid, 8 parts, tool-salute clip')
