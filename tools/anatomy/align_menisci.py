"""Reproduce knee registration; writes a candidate under app/build/anatomy-source.

Requires the pinned Z-Anatomy SkeletalSystem100.fbx and Joints100.fbx exports,
numpy, and the existing atlas GLB. Review geometry before replacing the checked-in
meniscus_registration.json. The existing bones are never changed.
"""
import hashlib
import json
import sys
from pathlib import Path
import numpy as np
sys.dont_write_bytecode = True
from fbx_geometry import meshes
from mesh_surface import closest, signed_distance, similarity
from validate_atlas import read_glb
import meniscus_content

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/build/anatomy-source'
ASSETS = ROOT / 'app/src/main/assets/science/biology'
SKELETAL_SHA256 = '294a649765cd060a62a4095da52b9c8ef2d97769aa447e196448aa5f7d596dea'


def atlas_bones():
    gltf, accessor = read_glb(ASSETS / 'atlas.glb')
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf-8'))['structures']
    names = {s['id']: s['sourceName'] for s in catalog}
    targets = {}
    for node in gltf['nodes']:
        name = names[node['name']]
        if name in {side + ' ' + bone for side in ('left', 'right') for bone in ('tibia', 'femur')}:
            p = gltf['meshes'][node['mesh']]['primitives'][0]
            targets[name] = (accessor(p['attributes']['POSITION']).astype(float),
                             accessor(p['indices']).reshape(-1, 3))
    return targets


def fit_tibia(vertices, target, seed):
    vertices = vertices / 100
    vertices = vertices[vertices[:, 1] > vertices[:, 1].max() - .04]
    w, f = target
    triangles = w[f]
    triangles = triangles[triangles[:, :, 1].max(1) > w[:, 1].max() - .06]
    matrix, offset = np.array(seed['matrix']), np.array(seed['offset'])
    for _ in range(100):
        matched, distances = closest(vertices @ matrix + offset, triangles)
        keep = distances < np.quantile(distances, .98)
        m, o = similarity(vertices[keep], matched[keep])
        change = np.max(abs(vertices @ m + o - (vertices @ matrix + offset)))
        matrix, offset = m, o
        if change < 1e-7:
            break
    _, distance = closest(vertices @ matrix + offset, triangles)
    return {'matrix': matrix.tolist(), 'offset': offset.tolist(),
            'surfaceResidualMm': dict(zip(('median', 'p95', 'max'), (np.quantile(distance, [.5, .95, 1]) * 1000).tolist()))}


def refine_contact(vertices, faces, bones, fit):
    matrix, offset = np.array(fit['matrix']), np.array(fit['offset'])
    v = vertices / 100 @ matrix + offset
    original = np.concatenate([v, v[faces].mean(1)])
    rotation, shift = np.eye(3), np.zeros(3)
    for _ in range(50):
        points = original @ rotation + shift
        sources, destinations = [original], [original]
        weights = [np.full(len(points), .004)]
        for w, f in bones:
            nearest, distance = signed_distance(points, w[f])
            overlap = distance < -.0002
            delta = (nearest[overlap] - points[overlap]) * (1 - .0002 / np.maximum(abs(distance[overlap, None]), 1e-10))
            sources.append(original[overlap])
            destinations.append(points[overlap] + delta)
            weights.append(np.ones(sum(overlap)))
        r, s = similarity(np.concatenate(sources), np.concatenate(destinations), np.concatenate(weights), scale=False)
        change = np.max(abs(original @ r + s - points))
        rotation, shift = r, s
        if change < 1e-6:
            break
    checks = {}
    for bone, (w, f) in zip(('tibia', 'femur'), bones):
        _, distance = signed_distance(original @ rotation + shift, w[f])
        checks[bone] = dict(zip(('minimum', 'p05', 'median', 'maximum'),
                               (np.quantile(distance, [0, .05, .5, 1]) * 1000).tolist()))
    center = original.mean(0)
    return {'matrix': (matrix @ rotation).tolist(), 'offset': (offset @ rotation + shift).tolist(),
            'correctionAngleDegrees': float(np.rad2deg(np.arccos(np.clip((np.trace(rotation) - 1) / 2, -1, 1)))),
            'centroidCorrectionMm': ((center @ rotation + shift - center) * 1000).tolist(),
            'sampledBoneClearanceMm': checks}


def main():
    report = json.loads(meniscus_content.REGISTRATION.read_text())
    for file, digest in [('Z-Anatomy-SkeletalSystem100.fbx', SKELETAL_SHA256),
                         ('Z-Anatomy-Joints100.fbx', meniscus_content.SOURCE_SHA256)]:
        assert hashlib.sha256((SOURCE / file).read_bytes()).hexdigest() == digest
    targets = atlas_bones()
    tibias = meshes(SOURCE / 'Z-Anatomy-SkeletalSystem100.fbx', ['Tibia.l', 'Tibia.r'], positions_only=True)
    menisci = meshes(SOURCE / 'Z-Anatomy-Joints100.fbx', [name for _, name, _, _, _ in meniscus_content.entries()])
    for side, suffix in [('left', 'l'), ('right', 'r')]:
        fit = fit_tibia(tibias['Tibia.' + suffix][0], targets[side + ' tibia'], report['initialSeeds'][suffix])
        report['tibialRegistration'][suffix] = fit
        print(side, 'tibia', fit['surfaceResidualMm'], flush=True)
        for base in meniscus_content.TERMS:
            name = base + '.' + suffix
            v, f = menisci[name]
            result = refine_contact(v, f, [targets[side + ' ' + b] for b in ('tibia', 'femur')], fit)
            report['structures'][name] = result
            print(name, result['sampledBoneClearanceMm'], flush=True)
    output = SOURCE / 'meniscus_registration_candidate.json'
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(output)


if __name__ == '__main__':
    main()
