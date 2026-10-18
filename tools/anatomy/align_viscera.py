"""Reproduce approximate lung/thyroid registration; write a candidate in build/.

Requires the pinned Z-Anatomy skeletal and visceral FBX files and the atlas GLB.
Review the candidate before replacing visceral_registration.json. No existing
BodyParts3D geometry is modified. Residuals are diagnostic, not clinical accuracy.
"""
import hashlib
import json
import sys
from pathlib import Path
import numpy as np
sys.dont_write_bytecode = True
from fbx_geometry import meshes
from mesh_surface import closest, similarity
from validate_atlas import read_glb
import visceral_content

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/build/anatomy-source'
ASSETS = ROOT / 'app/src/main/assets/science/biology'
SKELETAL_SHA256 = '294a649765cd060a62a4095da52b9c8ef2d97769aa447e196448aa5f7d596dea'


def nearest(points, vertices):
    indices = []
    for batch in np.array_split(points, max(1, (len(points) + 127) // 128)):
        indices.extend((((batch[:, None] - vertices) ** 2).sum(2)).argmin(1))
    matched = vertices[indices]
    return matched, np.linalg.norm(matched - points, axis=1)


def inside_z(points, triangles):
    """Ray parity diagnostic for these closed lung surfaces (not arbitrary meshes)."""
    a, b, c = triangles.transpose(1, 0, 2)
    u, v = b - a, c - a
    determinant = u[:, 0] * v[:, 1] - u[:, 1] * v[:, 0]
    safe = np.where(abs(determinant) > 1e-14, determinant, 1)
    result = []
    for p in np.array_split(points, max(1, (len(points) + 79) // 80)):
        q = p[:, None, :] - a
        bb = (q[:, :, 0] * v[:, 1] - q[:, :, 1] * v[:, 0]) / safe
        cc = (u[:, 0] * q[:, :, 1] - u[:, 1] * q[:, :, 0]) / safe
        hit = (bb >= 0) & (cc >= 0) & (bb + cc < 1) & (abs(determinant) > 1e-14)
        z = a[:, 2] + bb * u[:, 2] + cc * v[:, 2]
        result.extend(((hit & (z > p[:, None, 2])).sum(1) % 2) == 1)
    return np.array(result)


def fit_thorax(geometry):
    pairs = {f'{name.title()} rib.{suffix}': f'{side} {name} rib'
             for name in ('first', 'fourth', 'eighth', 'twelfth')
             for side, suffix in [('left', 'l'), ('right', 'r')]}
    pairs.update({'Vertebra T1': 'first thoracic vertebra', 'Vertebra T6': 'sixth thoracic vertebra',
                  'Vertebra T12': 'twelfth thoracic vertebra'})
    source = meshes(SOURCE / 'Z-Anatomy-SkeletalSystem100.fbx', list(pairs), positions_only=True)
    points, targets = [], []
    for name, target in pairs.items():
        vertices = np.unique(np.round(source[name][0] / 100, 9), axis=0)
        rng = np.random.default_rng(5)
        points.append(vertices[rng.choice(len(vertices), min(len(vertices), 500), replace=False)])
        targets.append(geometry[target][0])
    matrix = np.eye(3)
    offset = np.mean([w.mean(0) - v.mean(0) for v, w in zip(points, targets)], 0)
    for _ in range(80):
        pp, qq, ww = [], [], []
        for v, w in zip(points, targets):
            q, d = nearest(v @ matrix + offset, w)
            take = d <= np.quantile(d, .9)
            pp.append(v[take]); qq.append(q[take]); ww.extend([1 / sum(take)] * sum(take))
        matrix, offset = similarity(np.concatenate(pp), np.concatenate(qq), ww)
    checks = {}
    for name, v, w in zip(pairs, points, targets):
        _, d = nearest(v @ matrix + offset, w)
        checks[name] = dict(zip(('medianMm', 'p95Mm'), (np.quantile(d, [.5, .95]) * 1000).tolist()))
    return {'matrix': matrix.tolist(), 'offset': offset.tolist(), 'checks': checks}


def refine_left_lung(geometry, registration):
    names = ['Superior lobe of left lung', 'Inferior lobe of left lung']
    source = meshes(SOURCE / 'Z-Anatomy-VisceralSystem100.fbx', names)
    # Reconstruct from the source, never from an already registered atlas lung.
    origin = np.concatenate([source[name][0] for name in names]) / 100
    origin = origin @ np.array(registration['matrix']) + registration['offset']
    faces = np.concatenate([source[names[0]][1], source[names[1]][1] + len(source[names[0]][0])])
    rng = np.random.default_rng(33)
    anchors = origin[rng.choice(len(origin), 600, False)]
    heart, hf = geometry['heart']
    heart = np.concatenate([heart, heart[hf].mean(1)])
    heart = heart[rng.choice(len(heart), 2400, False)]
    ribs = np.concatenate([v for name, (v, _) in geometry.items()
                           if name.startswith('left ') and name.endswith(' rib')])
    ribs = ribs[rng.choice(len(ribs), 1800, False)]
    matrix, offset = np.eye(3), np.zeros(3)
    for step in range(35):
        triangles = (origin @ matrix + offset)[faces]
        aa, bb, weights = [anchors], [anchors], [np.full(len(anchors), .02)]
        for points in (heart, ribs):
            p = points[inside_z(points, triangles)]
            if not len(p):
                continue
            q, d = closest(p, triangles)
            take = d > .0003
            p, q, d = p[take], q[take], d[take]
            aa.append((q - offset) @ np.linalg.inv(matrix))
            bb.append(q + (p - q) * (1 - .0003 / d[:, None]))
            weights.append(np.ones(len(q)))
        m, o = similarity(np.concatenate(aa), np.concatenate(bb), np.concatenate(weights), scale=False)
        change = np.max(abs(origin @ m + o - (origin @ matrix + offset)))
        matrix, offset = m, o
        if step % 5 == 0:
            print('Left lung iteration', step, 'change (mm)', change * 1000, flush=True)
        if change < .00001:
            break
    checks = {}
    triangles = (origin @ matrix + offset)[faces]
    for label, points in [('heart', heart), ('ribs', ribs)]:
        p = points[inside_z(points, triangles)]
        distance = closest(p, triangles)[1] if len(p) else np.zeros(1)
        checks[label] = {'inside': len(p), 'maxPenetrationMm': float(distance.max() * 1000), 'samples': len(points)}
    center = origin.mean(0)
    return {'matrix': matrix.tolist(), 'offset': offset.tolist(),
            'scale': float(np.linalg.svd(matrix, compute_uv=False).mean()),
            'centroidDisplacementMm': ((center @ matrix + offset - center) * 1000).tolist(),
            'checks': checks}


def main():
    assert hashlib.sha256((SOURCE / 'Z-Anatomy-SkeletalSystem100.fbx').read_bytes()).hexdigest() == SKELETAL_SHA256
    assert hashlib.sha256((SOURCE / 'Z-Anatomy-VisceralSystem100.fbx').read_bytes()).hexdigest() == visceral_content.SOURCE_SHA256
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf-8'))['structures']
    by_id = {s['id']: s for s in catalog}
    gltf, accessor = read_glb(ASSETS / 'atlas.glb')
    geometry = {}
    for node in gltf['nodes']:
        p = gltf['meshes'][node['mesh']]['primitives'][0]
        geometry[by_id[node['name']]['sourceName']] = (accessor(p['attributes']['POSITION']).astype(float),
                                                     accessor(p['indices']).reshape(-1, 3))
    registration = fit_thorax(geometry)
    registration['leftLungAdjustment'] = refine_left_lung(geometry, registration)
    registration['skeletalSourceSha256'] = SKELETAL_SHA256
    registration['method'] = 'Equal-bone-weighted, 90%-trimmed nearest-vertex ICP on eight ribs and T1/T6/T12; one thoracic similarity. Left lobes adjusted together by a rigid transform against sampled heart/rib contacts, with original-pose anchors. No non-rigid deformation.'
    registration['limitations'] = 'Nearest-vertex residuals and sampled contact depths do not establish anatomical accuracy. Different source poses and lung inflation leave residual intersections; no claim of clinical validation.'
    output = SOURCE / 'visceral_registration_candidate.json'
    output.write_text(json.dumps(registration, indent=2) + '\n', encoding='utf-8')
    print(output)


if __name__ == '__main__':
    main()
