"""Audit new BP surfaces independently against the archived OBJ triangle positions.

Checks the pre-existing atlas byte-for-byte and the identity/side/geometry of the
new layers. Run after build_skeleton.py; no Android package is built.
"""
import collections
import hashlib
import json
from pathlib import Path
import sys
import zipfile
import numpy as np
sys.dont_write_bytecode = True
from validate_atlas import read_glb, source_atlas_assets
from fbx_geometry import meshes

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/build/anatomy-source'
ASSETS = source_atlas_assets()


def surfaces(path):
    gltf, accessor = read_glb(path)
    return {node['name']: (accessor((p := gltf['meshes'][node['mesh']]['primitives'][0])['attributes']['POSITION']),
                           accessor(p['indices']).reshape(-1, 3), accessor(p['attributes']['NORMAL']),
                           gltf['materials'][p['material']]) for node in gltf['nodes']}


def read_obj_mesh(archive, path):
    v, f = [], []
    for line in archive.read(path).decode('utf-8-sig').splitlines():
        row = line.split()
        if not row:
            continue
        if row[0] == 'v':
            x, y, z = map(float, row[1:4])
            v.append((x / 1000, z / 1000, -y / 1000))
        elif row[0] == 'f':
            polygon = [int(s.split('/')[0]) for s in row[1:]]
            polygon = [s - 1 if s > 0 else len(v) + s for s in polygon]
            f += [(polygon[0], polygon[i], polygon[i + 1]) for i in range(1, len(polygon) - 1)]
    return np.asarray(v, dtype=np.float64), np.asarray(f, dtype=np.int32)


def read_obj(archive, path):
    vertices, faces = read_obj_mesh(archive, path)
    return vertices.astype(np.float32)[faces]


def check_skin(archive, path, actual, indices):
    """Guard the explicitly documented skin exception without rerunning its fitter."""
    original, source_indices = read_obj_mesh(archive, path)
    source32 = original.astype(np.float32)
    assert original.shape == actual.shape == (102467, 3)
    assert np.array_equal(indices, source_indices)
    delta = actual.astype(np.float64) - source32
    length = np.linalg.norm(delta, axis=1)
    changed = np.any(actual != source32, axis=1)
    assert changed.sum() == 2301
    # Pin the outer-sheet selection from the independent intersection review.
    # This also guards every unchanged vertex, including the entire inner wall.
    moved_indices = np.flatnonzero(changed).astype('<i4')
    assert hashlib.sha256(moved_indices.tobytes()).hexdigest() == (
        '28f1cc11ac2bf28553d1ed3d4ff88c12bfe8ea768ad16a1f38c421a124ee36b3')
    assert length.max() < .012
    # All remaining source skin vertices, including the internal sheet, stay exact.
    moved = original[changed]
    lateral = np.abs(moved[:, 0]) > .1
    neck = moved[:, 1] > 1.25
    medial = ~(lateral | neck)
    assert (lateral.sum(), medial.sum(), neck.sum()) == (924, 959, 418)
    lower = np.array([[-.169, .429, .018], [-.081, .121, -.001], [-.061, 1.320, .090]])
    upper = np.array([[.168, .959, .129], [.072, .514, .127], [.063, 1.438, .160]])
    for i, mask in enumerate((lateral, medial, neck)):
        assert (moved[mask] >= lower[i]).all() and (moved[mask] <= upper[i]).all()
    shift = delta[changed]
    assert (shift[lateral, 0] * moved[lateral, 0] >= 0).all()
    assert (shift[medial, 0] * moved[medial, 0] <= 0).all()
    assert (shift[neck, 2] >= 0).all()
    assert length[changed][~medial].max() < .008
    triangles = original[source_indices]
    old_normals = np.cross(triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0])
    triangles = actual[indices].astype(np.float64)
    new_normals = np.cross(triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0])
    valid = np.linalg.norm(old_normals, axis=1) > 1e-15
    assert (np.einsum('ij,ij->i', old_normals[valid], new_normals[valid]) > 0).all()
    report = json.loads((ASSETS / 'provenance.json').read_text())['skin']['skinAccommodation']
    assert report['changedVertices'] == changed.sum()
    assert hashlib.sha256(original.astype('<f8').tobytes()).hexdigest() == report['sourceVerticesSha256']
    assert hashlib.sha256(source_indices.astype('<i4').tobytes()).hexdigest() == report['sourceIndicesSha256']
    assert abs(length.max() * 1000 - report['maximumDisplacementMm']) < .001
    return {'modifiedVertices': int(changed.sum()), 'maximumDisplacementMm': float(length.max() * 1000)}


def triangle_keys(triangles):
    # Triangle order and exact vertex welding can change, winding must not.
    # Canonicalise cyclic rotations, never the reversed orientation.
    result = []
    for triangle in triangles:
        rotations = [np.roll(triangle, i, axis=0).tobytes() for i in range(3)]
        result.append(min(rotations))
    return collections.Counter(result)


def check_external(catalog, current):
    """Compare final buffers to source topology and the audited pinned fits.

    This does not call the providers' load functions. Pose quality itself is
    measured separately in each registration report, not inferred from equality.
    """
    counts = {}
    for layer, basename in [('muscles', 'trunk_muscle'), ('nervous', 'nervous'), ('lymphatic', 'lymphatic')]:
        registration = json.loads((ROOT / 'tools/anatomy' / (basename + '_registration.json')).read_text())
        selected = [s for s in catalog if s['layer'] == layer and s.get('source') == 'z-anatomy'
                    and (layer != 'muscles' or s['kind'].startswith('trunk_'))]
        source_name = {'muscles': 'MuscularSystem', 'nervous': 'NervousSystem', 'lymphatic': 'LymphoidOrgans'}[layer]
        source = SOURCE / ('Z-Anatomy-' + source_name + '100.fbx')
        imported = meshes(source, [s['sourceObject'] for s in selected])
        for item in selected:
            points, faces = imported[item['sourceObject']]
            points = points / 100
            if layer == 'nervous':
                regions = registration['regional']
                aligned = []
                for start in range(0, len(points), 768):
                    batch = points[start:start + 768]
                    distances = np.column_stack([
                        ((batch[:, None] - np.asarray(r['anchors'])) ** 2).sum(2).min(1)
                        for r in regions])
                    weights = np.exp(-(distances - distances.min(1)[:, None]) /
                                     (2 * registration['blendWidthMetres'] ** 2))
                    weights /= weights.sum(1)[:, None]
                    aligned.append(sum((batch @ np.asarray(r['matrix']) + r['offset']) * weights[:, i, None]
                                       for i, r in enumerate(regions)))
                expected = np.concatenate(aligned).astype(np.float32)
            else:
                fit = (registration['transforms'][registration['objects'][item['sourceObject']]]
                       if layer == 'lymphatic' else registration)
                matrix = np.asarray(fit['matrix'])
                singular = np.linalg.svd(matrix, compute_uv=False)
                assert np.ptp(singular) < 1e-9 and np.linalg.det(matrix) > 0
                expected = (points @ matrix + fit['offset']).astype(np.float32)
            actual, indices, _, _ = current[item['id']]
            assert np.array_equal(faces, indices), item['id']
            assert actual.shape == expected.shape, item['id']
            assert np.max(np.abs(actual - expected)) < 2e-7, item['id']
        counts[layer] = len(selected)
    assert counts == {'muscles': 8, 'nervous': 247, 'lymphatic': 160}, counts
    return counts


def main():
    current = surfaces(ASSETS / 'atlas.glb')
    baseline = SOURCE / 'atlas-before-systems.glb'
    preserved = 0
    if baseline.exists():
        for identity, old in surfaces(baseline).items():
            new = current[identity]
            assert all(np.array_equal(a, b) for a, b in zip(old[:3], new[:3])), identity
            assert old[3] == new[3], identity
            preserved += 1
        assert preserved == 767
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf8'))['structures']
    layers = {'senses', 'nervous', 'vascular', 'lymphatic', 'connective', 'skin'}
    assert hashlib.sha256((SOURCE / 'bodyparts.zip').read_bytes()).hexdigest() == '40665852c49f218326590e204db91064a1ecfc3c6f8cbd7bbbcaac62c7cd409e'
    removed = {'FJ1340': 18, 'FJ1368': 4, 'FJ1337': 10, 'FJ1371': 4}
    checked = collections.Counter()
    skin_check = None
    with zipfile.ZipFile(SOURCE / 'bodyparts.zip') as archive:
        paths = {Path(n).stem: n for n in archive.namelist() if n.endswith('.obj')}
        for item in catalog:
            if item['layer'] not in layers or item.get('source') == 'z-anatomy':
                continue
            if item['id'] == 'FMA7163':
                skin_check = check_skin(archive, paths['FJ2810'], *current[item['id']][:2])
                checked[item['layer']] += 1
                continue
            expected, seen = [], set()
            for element in item['elements']:
                t = read_obj(archive, paths[element])
                if element in removed:
                    other_side = (t[:, :, 0] > 0).all(1)
                    assert int(other_side.sum()) == removed[element]
                    t = t[~other_side]
                signature = hashlib.sha256(t.tobytes()).hexdigest()
                if signature not in seen:
                    expected.append(t)
                seen.add(signature)
            expected = np.concatenate(expected)
            v, f, _, _ = current[item['id']]
            actual = v[f]
            assert len(actual) == len(expected), (item['id'], len(actual), len(expected))
            assert np.array_equal(actual, expected) or triangle_keys(actual) == triangle_keys(expected), item['id']
            checked[item['layer']] += 1
    by_id = {s['id']: s for s in catalog}
    assert skin_check is not None
    print('Documented external skin adjustment:', json.dumps(skin_check))
    assert by_id['FMA7163']['elements'] == ['FJ2810']
    vascular = [s for s in catalog if s['layer'] == 'vascular']
    assert len(vascular) == 396
    elements = [e for s in vascular for e in s['elements']]
    assert len(elements) == len(set(elements)) == 1014
    assert by_id['FMA4843']['elements'] == ['FJ3493']
    assert by_id['FMA4950']['elements'] == ['FJ3589']
    excluded = {'FJ1846', 'FJ1853', 'FJ2025', 'FJ2011', 'FJ1928', 'FJ1844',
                'FJ2034', 'FJ2038', 'FJ2046', 'FJ3615', 'FJ3530', 'FJ1931',
                'FJ1932', 'FJ2013', 'FJ2386', 'FJ2394'}
    assert not excluded.intersection(elements)
    for side in ('left', 'right'):
        item = by_id['BP3D_' + side + '_epigastric_venous_portion']
        assert item['kind'] == 'vascular_uncertain_epigastric'
    for s in catalog:
        if 'FJ1737' in s['elements']:
            assert 'canal' in s['sourceName'] and s['category'] == 'csf', 'Central canal is not spinal-cord tissue'
    for side in ('left', 'right'):
        eye = [s for s in catalog if s['layer'] == 'senses' and s['sourceName'] == side + ' sclera']
        assert len(eye) == 1
        item = eye[0]
        assert (item['min'][0] > 0) if side == 'left' else (item['max'][0] < 0)
    assert dict(checked) == {'senses': 42, 'nervous': 126, 'vascular': 396, 'connective': 24, 'skin': 3}, checked
    external = check_external(catalog, current)
    result = {'preservedPreviousMeshes': preserved, 'verifiedSourceSurfaces': dict(checked),
              'verifiedExternalTopologyAndPose': external,
              'removedOppositeOrbitTriangles': sum(removed.values())}
    (SOURCE / 'systems-validation.json').write_text(json.dumps(result, indent=2), encoding='utf8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
