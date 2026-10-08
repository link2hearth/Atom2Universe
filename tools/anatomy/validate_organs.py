"""Verify imported organ surfaces against their pinned sources, without an APK.

Optionally pass --baseline PATH to ensure that all previously shipped meshes,
normals, topology and materials remain byte-for-byte unchanged.
"""
import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path
import numpy as np
sys.dont_write_bytecode = True
from fbx_geometry import meshes
from validate_atlas import read_glb, source_atlas_assets
from align_viscera import inside_z
from mesh_surface import closest
import visceral_content

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/build/anatomy-source'
ASSETS = source_atlas_assets()


def geometry(path):
    gltf, accessor = read_glb(path)
    result = {}
    for node in gltf['nodes']:
        p = gltf['meshes'][node['mesh']]['primitives'][0]
        result[node['name']] = (accessor(p['attributes']['POSITION']), accessor(p['indices']).reshape(-1, 3),
                                accessor(p['attributes']['NORMAL']), gltf['materials'][p['material']])
    return result


def bodyparts_surface(archive, paths, elements):
    vertices, faces = [], []
    for element in elements:
        offset = len(vertices)
        for line in archive.read(paths[element]).decode('utf-8-sig').splitlines():
            row = line.split()
            if not row:
                continue
            if row[0] == 'v':
                xyz = np.array(row[1:4], dtype=float)
                vertices.append(xyz[[0, 2, 1]] * [.001, .001, -.001])
            elif row[0] == 'f':
                polygon = [int(token.split('/')[0]) for token in row[1:]]
                polygon = [i + offset - 1 if i > 0 else len(vertices) + i for i in polygon]
                faces.extend((polygon[0], polygon[i], polygon[i + 1]) for i in range(1, len(polygon) - 1))
    return np.array(vertices, dtype=np.float32), np.array(faces)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path)
    args = parser.parse_args()
    current = geometry(ASSETS / 'atlas.glb')
    preserved = 0
    if args.baseline:
        for identity, old in geometry(args.baseline).items():
            new = current[identity]
            assert all(np.array_equal(a, b) for a, b in zip(old[:3], new[:3])), identity
            assert old[3] == new[3], identity
            preserved += 1
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf-8'))['structures']
    organs = [s for s in catalog if s['layer'] == 'organs']
    archive_path = SOURCE / 'bodyparts.zip'
    assert hashlib.sha256(archive_path.read_bytes()).hexdigest() == '40665852c49f218326590e204db91064a1ecfc3c6f8cbd7bbbcaac62c7cd409e'
    with zipfile.ZipFile(archive_path) as archive:
        paths = {Path(name).stem: name for name in archive.namelist() if name.endswith('.obj')}
        for s in organs:
            if s.get('source') == 'z-anatomy':
                continue
            expected, indices = bodyparts_surface(archive, paths, s['elements'])
            actual, faces, _, _ = current[s['id']]
            assert np.array_equal(expected, actual) and np.array_equal(indices, faces), s['id']
    source_path = SOURCE / 'Z-Anatomy-VisceralSystem100.fbx'
    assert hashlib.sha256(source_path.read_bytes()).hexdigest() == visceral_content.SOURCE_SHA256
    source = meshes(source_path, list(visceral_content.TERMS))
    reg = json.loads(visceral_content.REGISTRATION.read_text())
    matrix, offset = np.array(reg['matrix']), np.array(reg['offset'])
    scale = np.linalg.svd(matrix, compute_uv=False)
    assert np.ptp(scale) < 1e-10 and np.linalg.det(matrix) > 0
    left = reg['leftLungAdjustment']
    rotation = np.array(left['matrix'])
    assert np.allclose(rotation @ rotation.T, np.eye(3), atol=1e-10) and np.linalg.det(rotation) > 0
    before, after = [], []
    for s in organs:
        if s.get('source') != 'z-anatomy':
            continue
        vertices, indices = source[s['sourceObject']]
        v = vertices / 100 @ matrix + offset
        actual, faces, _, _ = current[s['id']]
        assert np.array_equal(indices, faces), s['id']
        if 'left lung' in s['sourceObject']:
            before.extend(v[indices])
            v = v @ rotation + left['offset']
            after.extend(actual[faces])
        assert np.allclose(v, actual, atol=1e-7, rtol=0), s['id']
    # Interior barycentric points were not used by the fitting (vertices and
    # centroids). Report residual overlap honestly, rather than calling it zero.
    heart, faces, _, _ = current['FMA7088']
    triangles = heart[faces].astype(float)
    samples = np.einsum('ijk,j->ik', triangles, [.23, .31, .46])
    rng = np.random.default_rng(2026)
    samples = samples[rng.choice(len(samples), 1500, False)]
    contacts = {}
    for label, lung in [('before', before), ('after', after)]:
        lung = np.array(lung)
        p = samples[inside_z(samples, lung)]
        distance = closest(p, lung)[1] if len(p) else np.zeros(1)
        contacts[label] = {'insideSamples': len(p), 'maxDepthMm': float(distance.max() * 1000), 'sampleCount': len(samples)}
    assert contacts['after']['maxDepthMm'] < contacts['before']['maxDepthMm'], contacts
    report = {'verifiedOriginalBodyPartsSurfaces': 44, 'verifiedZAnatomySurfaces': 6,
              'unchangedBaselineMeshes': preserved, 'leftLungHeartResidualContacts': contacts,
              'note': 'Surface preservation and approximate placement checks; residual intersections remain. Not clinical validation.'}
    (SOURCE / 'organ_validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
