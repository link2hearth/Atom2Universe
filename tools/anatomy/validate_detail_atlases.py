"""Check independently sourced atlases, packaged meshes and Android resources."""
from collections import Counter
import json
import hashlib
import xml.etree.ElementTree as ET
import numpy as np
from validate_atlas import ROOT, ASSETS, read_glb


def check_source_geometry(atlas, records, gltf, accessor):
    """Independently compare final buffers with the original source surfaces."""
    source = ROOT / 'app/build/anatomy-source'
    expected = {}
    registration = json.loads((ROOT / 'tools/anatomy' / (atlas + '_registration.json')).read_text(encoding='utf8'))
    if atlas == 'female':
        from female_content import read_source  # Parser only; no provider build/selection logic.
        raw_gltf, raw_accessor = read_source(source / 'female/HRA-female.glb')
        translation = np.array(registration['translation'])
        used = set()
        for record in records:
            if record.get('reconstructed'):
                assert record['source'] == 'atom2universe-reconstruction'
                continue
            assert record['source'] == 'hra-female-v1.5'
            vertices, faces, offset = [], [], 0
            for identity in record['sourceOntology']:
                index = identity['sourceNode']
                assert index not in used
                used.add(index)
                node = raw_gltf['nodes'][index]
                assert node['name'] == identity['sourceObject']
                for p in raw_gltf['meshes'][node['mesh']]['primitives']:
                    v = raw_accessor(p['attributes']['POSITION']).astype(np.float64)
                    f = raw_accessor(p['indices']).reshape(-1, 3)
                    vertices.append((v + translation).astype(np.float32))
                    faces.append(f + offset)
                    offset += len(v)
            expected[record['id']] = np.concatenate(vertices), np.concatenate(faces)
        assert len(expected) == 169 and len(used) == 398
    else:
        from ear_content import read_ply
        rotation = np.array(registration['displayRotation'])
        assert np.allclose(rotation.T @ rotation, np.eye(3)) and np.linalg.det(rotation) > .999999
        center = np.array(registration['displayCenterMm'])
        for record in records:
            filename = record['sourceObject']
            path = source / 'ear/ZETA' / filename
            assert hashlib.sha256(path.read_bytes()).hexdigest() == registration['files'][filename]['sha256']
            if filename == 'Bone.ply':
                path = path.with_name('Bone_context.npz')
                assert hashlib.sha256(path.read_bytes()).hexdigest() == registration['context']['cacheSha256']
                with np.load(path, allow_pickle=False) as data:
                    v, f = data['vertices'], data['faces']
            else:
                v, f = read_ply(path)
            expected[record['id']] = ((v - center) @ rotation / 1000.).astype(np.float32), f
    for node in gltf['nodes']:
        if node['name'] not in expected:
            assert atlas == 'female' and node['name'].startswith('A2U_FEMALE_')
            continue
        p = gltf['meshes'][node['mesh']]['primitives'][0]
        v, f = expected[node['name']]
        assert np.array_equal(accessor(p['attributes']['POSITION']), v), node['name']
        assert np.array_equal(accessor(p['indices']).reshape(-1, 3), f), node['name']
    return len(expected)


def check_reconstructions(records, gltf, accessor, provenance):
    selected = {r['id']: r for r in records if r.get('reconstructed')}
    if not selected:
        return 0
    registration_file = ROOT / 'tools/anatomy/female_supplement_registration.json'
    registration = json.loads(registration_file.read_text(encoding='utf8'))
    report = provenance['reconstructedSupplement']
    assert report['registrationSha256'] == hashlib.sha256(registration_file.read_bytes()).hexdigest()
    assert len(selected) == report['renderedStructures']
    expected = {'clitoral_glans', 'clitoral_body', 'crus_left', 'crus_right', 'bulb_left', 'bulb_right',
                'labium_majus_left', 'labium_majus_right', 'labium_minus_left', 'labium_minus_right', 'female_urethra'}
    assert {r['sourceObject'] for r in selected.values()} >= expected
    triangles = 0
    for node in gltf['nodes']:
        if node['name'] not in selected:
            continue
        record = selected[node['name']]
        primitive = gltf['meshes'][node['mesh']]['primitives'][0]
        v = accessor(primitive['attributes']['POSITION']).astype(np.float64)
        f = accessor(primitive['indices']).reshape(-1, 3).astype(np.int64)
        edges = np.concatenate([f[:, [0, 1]], f[:, [1, 2]], f[:, [2, 0]]])
        unique, inv, counts = np.unique(np.sort(edges, axis=1), axis=0, return_inverse=True, return_counts=True)
        assert (counts == 2).all(), record['id']
        direction = np.bincount(inv, weights=np.where(edges[:, 0] < edges[:, 1], 1, -1))
        assert (direction == 0).all(), record['id']
        t = v[f]
        normals = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0])
        assert (np.linalg.norm(normals, axis=1) > 1e-12).all()
        assert np.einsum('ij,ij->i', t[:, 0], normals).sum() > 0
        # The urethra has a through lumen; the other teaching surfaces are closed solids.
        assert len(v) - len(unique) + len(f) == (0 if record['sourceObject'] == 'female_urethra' else 2)
        assert max(np.ptp(v, axis=0)) < .15
        if record['sourceObject'].endswith(('_left', '_right')):
            assert (v[:, 0].mean() > registration['midlineX']) == record['sourceObject'].endswith('_left')
        if record['sourceObject'] == 'female_urethra':
            for key in ('bladderNeckCentre', 'urethralMeatus'):
                anchor = np.array(registration['anchors'][key]) + registration['translation']
                assert np.linalg.norm(v - anchor, axis=1).min() < .007, key
        triangles += len(f)
    # The reconstruction report records the imported source. Mobile derivatives
    # keep the topology checks above but deliberately contain fewer triangles.
    if 'mobileOptimization' not in provenance:
        assert triangles == report['triangles']
    return len(selected)


def validate(atlas):
    directory = ASSETS / atlas
    catalog = json.loads((directory / 'catalog.json').read_text(encoding='utf8'))
    provenance = json.loads((directory / 'provenance.json').read_text(encoding='utf8'))
    records = catalog['structures']
    by_id = {s['id']: s for s in records}
    assert len(by_id) == len(records) == provenance['structures']
    assert catalog['atlas'] == atlas and provenance['license']
    assert set(catalog['defaultLayers']) <= {s['layer'] for s in records}
    assert set(catalog['hiddenByDefault']) <= set(by_id)
    assert any(s['id'] not in catalog['hiddenByDefault'] and s['layer'] in catalog['defaultLayers'] for s in records)
    resources = {}
    for locale in ('values', 'values-fr'):
        strings = {}
        for path in (ROOT / 'app/src/main/res' / locale).glob('strings_biology*.xml'):
            for row in ET.parse(path).getroot():
                assert row.attrib['name'] not in strings, row.attrib['name']
                strings[row.attrib['name']] = row.text
        for s in records:
            for key in (s['name'], 'bio_summary_' + s['kind'], 'bio_group_' + s['category']):
                assert strings.get(key), (locale, s['id'], key)
        resources[locale] = strings
    assert resources['values'].keys() == resources['values-fr'].keys()
    gltf, accessor = read_glb(directory / 'atlas.glb')
    from external_visibility import validate as validate_visibility
    variant_count = validate_visibility(catalog, gltf, accessor, provenance)
    assert len(gltf['nodes']) == len(gltf['meshes']) == len(gltf['materials']) == len(records) + variant_count
    assert {n['name'] for n in gltf['nodes']} == set(by_id) | set(catalog.get('externalHiddenVariants', {}))
    assert gltf['scenes'][gltf['scene']]['nodes'] == list(range(len(gltf['nodes'])))
    triangles = 0
    for node in gltf['nodes']:
        if node['name'] in catalog.get('externalHiddenVariants', {}):
            continue
        s = by_id[node['name']]
        assert s['mesh'] and not s['standard'] and s['boneCount'] == 0
        assert ('organSystem' in s) == (s['layer'] == 'organs')
        assert s['source'] and s['sourceObject'] and s['wikiEn'] and s['wikiFr']
        assert s['region'] in {'body', 'skull', 'neck', 'spine', 'thorax', 'abdomen', 'upper', 'hands', 'pelvis', 'lower', 'feet', 'teeth'}
        assert not {'matrix', 'translation', 'rotation', 'scale'} & node.keys()
        primitive, = gltf['meshes'][node['mesh']]['primitives']
        p = accessor(primitive['attributes']['POSITION'])
        n = accessor(primitive['attributes']['NORMAL'])
        f = accessor(primitive['indices'])
        assert len(p) and len(f) and len(f) % 3 == 0 and f.max() < len(p)
        assert p.shape == n.shape and np.isfinite(p).all() and np.isfinite(n).all()
        assert np.allclose(np.linalg.norm(n, axis=1), 1., atol=1e-5)
        assert np.allclose(p.min(0), s['min'], atol=1e-7) and np.allclose(p.max(0), s['max'], atol=1e-7)
        assert primitive['material'] == node['mesh']
        material = gltf['materials'][primitive['material']]
        assert np.allclose(material['pbrMetallicRoughness']['baseColorFactor'], [*s['color'], s['opacity']])
        assert (material.get('alphaMode') == 'BLEND') == (s['opacity'] < 1.)
        triangles += len(f) // 3
    assert triangles == provenance['triangles']
    bounds = np.array([[s['min'], s['max']] for s in records])
    span = bounds[:, 1].max(0) - bounds[:, 0].min(0)
    assert (.8 < span[1] < 2.) if atlas == 'female' else (.005 < max(span) < .25), span
    mobile = 'mobileOptimization' in provenance
    if mobile:
        from validate_mobile import validate as validate_mobile
        source_preserved = validate_mobile(atlas)
    else:
        source_preserved = check_source_geometry(atlas, records, gltf, accessor)
    reconstructions = check_reconstructions(records, gltf, accessor, provenance)
    print(json.dumps({'atlas': atlas, 'meshes': len(records), 'triangles': triangles,
        'layers': dict(Counter(s['layer'] for s in records)), 'spanMetres': span.tolist(),
        'modelBytes': catalog['modelBytes'],
        ('mobileMeshesVerified' if mobile else 'sourceGeometriesVerified'): source_preserved,
        'reconstructionsChecked': reconstructions, 'result': 'PASS'}, indent=2))


if __name__ == '__main__':
    import sys
    for atlas in sys.argv[1:] or ('female', 'ear'):
        assert atlas in {'female', 'ear'}
        validate(atlas)
