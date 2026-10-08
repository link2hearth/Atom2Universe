"""Reproduce the shared face registration; write measurements only under app/build.

Download the skeletal FBX from the same Z-Anatomy folder as the muscular FBX.
Alignment uses named bone pairs; held-out parietal bones check the transform.
It does not assert clinical accuracy or edit the source geometry.
"""
import sys, json, zipfile, hashlib
from pathlib import Path
import numpy as np
sys.dont_write_bytecode = True
sys.path.insert(0, 'tools/anatomy')
from fbx_geometry import meshes

source = Path('app/build/anatomy-source')
catalog = json.load(open('app/src/main/assets/science/biology/catalog.json'))['structures']
by_name = {r['sourceName']: r for r in catalog}
pairs = {'Frontal bone':'frontal bone', 'Maxilla.r':'right maxilla', 'Maxilla.l':'left maxilla',
         'Zygomatic bone.r':'right zygomatic bone', 'Zygomatic bone.l':'left zygomatic bone',
         'Mandible':'mandible', 'Parietal bone.r':'right parietal bone', 'Parietal bone.l':'left parietal bone'}
fbx = meshes(source / 'Z-Anatomy-SkeletalSystem100.fbx', pairs, positions_only=True)
assert hashlib.sha256((source / 'Z-Anatomy-SkeletalSystem100.fbx').read_bytes()).hexdigest() == '294a649765cd060a62a4095da52b9c8ef2d97769aa447e196448aa5f7d596dea'
archive = zipfile.ZipFile(source / 'bodyparts.zip')
paths = {Path(n).stem:n for n in archive.namelist() if n.endswith('.obj')}
arrays = {}
for a,b in pairs.items():
    vertices=[]
    for el in by_name[b]['elements']:
        for line in archive.read(paths[el]).decode('utf-8-sig').splitlines():
            if line.startswith('v '):
                x,y,z=map(float,line.split()[1:4]);vertices.append((x/1000,z/1000,-y/1000))
    target = np.unique(np.array(vertices),axis=0)
    initial = np.unique(fbx[a][0]/100,axis=0)
    arrays[a] = initial[::max(1,len(initial)//900)],target

def nearest(a,b):
    indices=[];sq=(b*b).sum(1)
    for batch in np.array_split(a,max(1,(len(a)+127)//128)):
        d=(batch*batch).sum(1)[:,None]+sq[None,:]-2*batch@b.T
        indices.extend(d.argmin(1))
    indices=np.array(indices)
    return b[indices],np.linalg.norm(a-b[indices],axis=1)

def similarity(a,b):
    ma,mb=a.mean(0),b.mean(0)
    aa,bb=a-ma,b-mb
    u,s,vt=np.linalg.svd(aa.T@bb)
    d=np.eye(3);d[2,2]=np.linalg.det(u@vt)
    rotation=u@d@vt
    scale=(s*np.diag(d)).sum()/(aa*aa).sum()
    return rotation*scale,mb-ma@rotation*scale

matrix=np.eye(3)*1.024
offset=np.array([-.00066,-.11,.0882])
for iteration in range(35):
    aa=[];bb=[]
    for name,(a,b) in arrays.items():
        if name.startswith('Parietal'):continue
        match,dist=nearest(a@matrix+offset,b)
        keep=dist<=np.quantile(dist,.9)
        aa.append(a[keep]);bb.append(match[keep])
    matrix,offset=similarity(np.concatenate(aa),np.concatenate(bb))
    if iteration%10==0:print(iteration,matrix,offset,flush=True)
report={}
for name,(a,b) in arrays.items():
    _,dist=nearest(a@matrix+offset,b)
    report[name]={'medianMm':float(np.median(dist)*1000),'p95Mm':float(np.quantile(dist,.95)*1000)}
result={'rowMatrix':matrix.tolist(),'offset':offset.tolist(),'checks':report}
(source/'face_alignment.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
