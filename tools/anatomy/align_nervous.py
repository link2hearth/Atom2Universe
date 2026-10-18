"""Reproduce continuous bone-guided registration of Z-Anatomy nerve surfaces.

Writes diagnostics under app/build, never edits the atlas or pinned transform.
A shared smooth blend of regional similarities keeps coincident branch points
together. Residuals measure nearest bone vertices, not clinical accuracy.
"""
import sys
sys.dont_write_bytecode = True
import json, hashlib, zipfile
from pathlib import Path
import numpy as np
from fbx_geometry import meshes
from mesh_surface import similarity

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/build/anatomy-source'
SKELETAL_SHA256 = '294a649765cd060a62a4095da52b9c8ef2d97769aa447e196448aa5f7d596dea'


def nearest(points, vertices):
    indices = []
    vv = (vertices * vertices).sum(1)
    for batch in np.array_split(points, max(1, (len(points) + 95) // 96)):
        d = (batch * batch).sum(1)[:, None] + vv[None, :] - 2 * batch @ vertices.T
        indices.extend(d.argmin(1))
    match = vertices[indices]
    return match, np.linalg.norm(match - points, axis=1)


def warp(points, registration):
    """Continuous, bone-guided displacement; all branches use the same field."""
    matrix, offset = np.array(registration['matrix']), np.array(registration['offset'])
    q = np.asarray(points) @ matrix + offset
    if 'regional' in registration:
        result = []
        regions = registration['regional']
        controls = [np.array(r['anchors']) for r in regions]
        matrices = np.array([r['matrix'] for r in regions])
        offsets = np.array([r['offset'] for r in regions])
        for batch in np.array_split(np.asarray(points), max(1, (len(points) + 1023) // 1024)):
            # Squared distance to representative skeletal points in each region.
            distances = np.stack([((batch[:, None] - c) ** 2).sum(2).min(1) for c in controls], 1)
            weights = np.exp(-(distances - distances.min(1)[:, None]) / (2 * registration['blendWidthMetres'] ** 2))
            weights /= weights.sum(1)[:, None]
            transformed = np.einsum('ni,kij->nkj', batch, matrices) + offsets
            result.append((transformed * weights[:, :, None]).sum(1))
        return np.concatenate(result)
    return q


def main():
    path = SOURCE / 'Z-Anatomy-SkeletalSystem100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SKELETAL_SHA256
    pairs = {'Occipital bone': 'occipital bone', 'Frontal bone': 'frontal bone',
             'Vertebra C3': 'third cervical vertebra', 'Vertebra C7': 'seventh cervical vertebra',
             'Vertebra T6': 'sixth thoracic vertebra', 'Vertebra T12': 'twelfth thoracic vertebra',
             'Vertebra L3': 'third lumbar vertebra', 'Sacrum': 'sacrum'}
    for bone in ['Clavicle', 'Humerus', 'Radius', 'Ulna', 'Femur', 'Tibia', 'Fibula']:
        for side, suffix in [('left', 'l'), ('right', 'r')]:
            pairs[f'{bone}.{suffix}'] = f'{side} {bone.lower()}'
    global_names = set(pairs)
    for bone in ['Hip bone', 'Talus', 'Calcaneus'] + [n + ' ' + b + ' bone' for n in ['First', 'Third', 'Fifth'] for b in ['metacarpal', 'metatarsal']]:
        for side, suffix in [('left', 'l'), ('right', 'r')]:
            pairs[f'{bone}.{suffix}'] = f'{side} {bone.lower()}'
    fb = meshes(path, list(pairs), positions_only=True)
    catalog = json.loads((ROOT / 'app/src/main/assets/science/biology/catalog.json').read_text())['structures']
    records = {r['sourceName']: r for r in catalog}
    archive = zipfile.ZipFile(SOURCE / 'bodyparts.zip')
    paths = {Path(p).stem: p for p in archive.namelist() if p.endswith('.obj')}
    source, target = [], []
    for name, bp in pairs.items():
        v = np.unique(np.round(fb[name][0] / 100, 9), axis=0)
        rng = np.random.default_rng(84)
        source.append(v[rng.choice(len(v), min(len(v), 220), False)])
        vv = []
        for element in records[bp]['elements']:
            for line in archive.read(paths[element]).decode('utf-8-sig').splitlines():
                if line.startswith('v '):
                    x, y, z = map(float, line.split()[1:4])
                    vv.append((x / 1000, z / 1000, -y / 1000))
        target.append(np.unique(vv, axis=0))
    matrix, offset = similarity(np.array([v.mean(0) for v in source]),
                                np.array([v.mean(0) for v in target]))
    for iteration in range(50):
        aa, bb, ww = [], [], []
        for name, v, t in zip(pairs, source, target):
            if name not in global_names:
                continue
            q, d = nearest(v @ matrix + offset, t)
            take = d <= np.quantile(d, .90)
            aa.append(v[take]); bb.append(q[take]); ww.extend([1 / sum(take)] * sum(take))
        matrix, offset = similarity(np.concatenate(aa), np.concatenate(bb), ww)
        if iteration % 10 == 0: print('iteration', iteration, flush=True)
    checks = {}
    for name, v, t in zip(pairs, source, target):
        _, d = nearest(v @ matrix + offset, t)
        checks[name] = dict(zip(('medianMm', 'p95Mm'), (np.quantile(d, [.5, .95]) * 1000).tolist()))
    report = {'matrix': matrix.tolist(), 'offset': offset.tolist(), 'boneReferenceSha256': SKELETAL_SHA256,
              'method': 'Equal-weight 22-bone, 90%-trimmed nearest-vertex ICP; one similarity for the whole nerve network; 220 deterministic samples per bone, seed 84, 50 iterations.',
              'checks': checks}
    (SOURCE / 'nervous/global_alignment.json').write_text(json.dumps(report, indent=2))
    local = {}
    for name, v, t in zip(pairs, source, target):
        # Fit the entire named bone before deriving a small set of displacement
        # landmarks; no nerve is fitted to a guessed path or collision target.
        mm, oo = matrix.copy(), offset.copy()
        for _ in range(45):
            match, dist = nearest(v @ mm + oo, t)
            keep = dist <= np.quantile(dist, .90)
            mm, oo = similarity(v[keep], match[keep])
        _, d = nearest(v @ mm + oo, t)
        local[name] = {'matrix': mm.tolist(), 'offset': oo.tolist(),
                       'medianMm': float(np.median(d) * 1000), 'p95Mm': float(np.quantile(d, .95) * 1000)}
    (SOURCE / 'nervous/local_bone_alignment.json').write_text(json.dumps(local, indent=2))
    region_names = {'head': ['Occipital bone', 'Frontal bone'],
                    'cervical': ['Vertebra C3', 'Vertebra C7'],
                    'thoracic': ['Vertebra T6', 'Vertebra T12'],
                    'lumbar': ['Vertebra L3'], 'pelvis': ['Sacrum', 'Hip bone.l', 'Hip bone.r']}
    for side in ['l', 'r']:
        for region, names in [('arm', ['Humerus', 'Clavicle']), ('forearm', ['Radius', 'Ulna']),
                              ('hand', ['First metacarpal bone', 'Third metacarpal bone', 'Fifth metacarpal bone']),
                              ('thigh', ['Femur']), ('shin', ['Tibia', 'Fibula']),
                              ('foot', ['Talus', 'Calcaneus', 'First metatarsal bone', 'Third metatarsal bone', 'Fifth metatarsal bone'])]:
            region_names[region + '.' + side] = [name + '.' + side for name in names]
    by_name = {n: (v, t) for n, v, t in zip(pairs, source, target)}
    regions = []
    for region, names in region_names.items():
        mm = matrix.copy()
        oo = np.mean([by_name[n][1].mean(0) - by_name[n][0].mean(0) @ mm for n in names], 0)
        for _ in range(60):
            aa, bb, ww = [], [], []
            for name in names:
                v, t = by_name[name]
                match, dist = nearest(v @ mm + oo, t)
                keep = dist <= np.quantile(dist, .90)
                aa.append(v[keep]); bb.append(match[keep]); ww.extend([1 / sum(keep)] * sum(keep))
            mm, oo = similarity(np.concatenate(aa), np.concatenate(bb), ww)
        anchors = []
        for name in names:
            v = by_name[name][0]
            # A short representative curve through each bone rather than a
            # single centroid, so long shafts do not leave large unweighted gaps.
            axis = np.linalg.svd(v - v.mean(0), full_matrices=False)[2][0]
            val = v @ axis
            for lo, hi in [(0, .2), (.2, .4), (.4, .6), (.6, .8), (.8, 1)]:
                vv = v[(val >= np.quantile(val, lo)) & (val <= np.quantile(val, hi))]
                anchors.append(vv.mean(0).tolist())
        regions.append({'name': region, 'bones': names, 'matrix': mm.tolist(), 'offset': oo.tolist(), 'anchors': anchors})
    candidate = {k: report[k] for k in ['matrix', 'offset', 'boneReferenceSha256']}
    candidate.update(regional=regions, blendWidthMetres=.06, referenceBoneCount=len(pairs),
                     method='Continuous blend of 17 regional similarity registrations, each fitted to explicitly paired skeletal surfaces. Gaussian distance to five shaft landmarks per reference bone weights the same coordinate field for every nerve vertex. No neural branches are added or moved independently.')
    checks = {}
    for name, v, t in zip(pairs, source, target):
        _, d = nearest(warp(v, candidate), t)
        checks[name] = dict(zip(('medianMm', 'p95Mm'), (np.quantile(d, [.5, .95]) * 1000).tolist()))
    candidate['checks'] = checks
    (SOURCE / 'nervous/regional_alignment.json').write_text(json.dumps(candidate, indent=2))
    print('Regional checks', json.dumps(checks, indent=2), flush=True)


if __name__ == '__main__':
    main()
