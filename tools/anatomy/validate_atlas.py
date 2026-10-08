"""Validate generated glTF/catalogue/resource consistency without building an APK."""
import json
import gzip
import hashlib
import subprocess
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/science/biology'


def source_atlas_assets(atlas='male'):
    """Select hash-pinned imported surfaces for audits that require exact source geometry.

    These historical audits describe the import/registration step. Mobile output
    has separate checks in validate_mobile.py and optimize_mobile.mjs --validate.
    """
    directory = ASSETS / (atlas if atlas != 'male' else '')
    provenance = json.loads((directory / 'provenance.json').read_text(encoding='utf8'))
    mobile = provenance.get('mobileOptimization')
    if not mobile:
        return directory
    original = ROOT / 'app/build/anatomy-source/mobile' / atlas / 'original'
    if not (original / 'provenance.json').exists():
        raise ValueError('Exact source audit requires rebuilding the source atlas before mobile optimization; '
                         'use validate_mobile.py to check the packaged derivative')
    source = json.loads((original / 'provenance.json').read_text(encoding='utf8'))
    assert source['packaging']['modelSha256'] == mobile['sourceModelSha256']
    print('Source-fidelity audit of the imported original:', original)
    return original


def read_glb(path):
    if path.is_file():
        raw = path.read_bytes()
    else:
        assert path.name == 'atlas.glb', path
        catalog = json.loads((path.parent / 'catalog.json').read_text(encoding='utf8'))
        # Android strips .gz and decompresses such assets, breaking runtime reads.
        assert catalog['modelParts'] == [f'atlas-{i:03d}.part.gzip' for i in range(len(catalog['modelParts']))]
        raw = b''.join(gzip.decompress((path.parent / name).read_bytes()) for name in catalog['modelParts'])
        assert len(raw) == catalog['modelBytes']
        packaging = json.loads((path.parent / 'provenance.json').read_text())['packaging']
        assert hashlib.sha256(raw).hexdigest() == packaging['modelSha256']
        assert [p['file'] for p in packaging['parts']] == catalog['modelParts']
        for part in packaging['parts']:
            data = (path.parent / part['file']).read_bytes()
            assert len(data) == part['bytes'] < 50 * 1024 * 1024
            assert hashlib.sha256(data).hexdigest() == part['sha256']
    magic, version, length = struct.unpack_from('<III', raw)
    assert magic == 0x46546C67 and version == 2 and length == len(raw)
    size, kind = struct.unpack_from('<II', raw, 12)
    assert kind == 0x4E4F534A
    gltf = json.loads(raw[20:20 + size])
    if 'KHR_draco_mesh_compression' in gltf.get('extensionsUsed', []):
        # Decode once into the ignored build directory; all existing inspection tools
        # then read ordinary float/index buffers, with the packaged hashes checked above.
        cache = ROOT / 'app/build/anatomy-source/mobile/decoded-cache'
        cache.mkdir(parents=True, exist_ok=True)
        digest = hashlib.sha256(raw).hexdigest()
        packed, decoded = cache / (digest + '.draco.glb'), cache / (digest + '.glb')
        if not decoded.exists():
            packed.write_bytes(raw)
            subprocess.run(['node', str(ROOT / 'tools/anatomy/optimize_mobile.mjs'),
                            '--decode', str(packed), str(decoded)], check=True)
        return read_glb(decoded)
    binary_size, binary_kind = struct.unpack_from('<II', raw, 20 + size)
    assert binary_kind == 0x004E4942
    binary = memoryview(raw)[28 + size:]
    assert binary_size == len(binary)

    def accessor(index):
        a = gltf['accessors'][index]
        view = gltf['bufferViews'][a['bufferView']]
        width = {'SCALAR': 1, 'VEC3': 3}[a['type']]
        dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2'}[a['componentType']]
        data = np.frombuffer(binary, dtype=dtype, count=a['count'] * width,
                             offset=view.get('byteOffset', 0) + a.get('byteOffset', 0))
        return data.reshape((-1, width)) if width > 1 else data

    return gltf, accessor


def main():
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf-8'))['structures']
    by_id = {s['id']: s for s in catalog}
    assert len(by_id) == len(catalog)
    assert sum(s['boneCount'] for s in catalog if s['standard']) == 206
    assert sum(s['boneCount'] for s in catalog if s['standard'] and s['mesh']) == 199
    assert sum(s['layer'] == 'muscles' for s in catalog) == 442
    organs = [s for s in catalog if s['layer'] == 'organs']
    assert len(organs) == 50
    assert all(not s['standard'] and s['boneCount'] == 0 and s['mesh'] for s in organs)
    systems = {'cardiovascular', 'respiratory', 'digestive', 'urinary', 'endocrine', 'lymphatic', 'reproductive'}
    assert {s['organSystem'] for s in organs} == systems
    assert all(('organSystem' in s) == (s['layer'] == 'organs') for s in catalog)
    added_layers = {'senses', 'nervous', 'vascular', 'lymphatic', 'connective', 'skin'}
    assert {s['layer'] for s in catalog} == {'skeleton', 'cartilage', 'muscles', 'organs'} | added_layers
    for s in catalog:
        if s['layer'] in added_layers:
            assert s['category'] and s['mesh'] and not s['standard'] and s['boneCount'] == 0, s['id']
    visceral = [s for s in organs if s.get('source') == 'z-anatomy']
    assert {s['sourceObject'] for s in visceral} == {
        'Superior lobe of right lung', 'Middle lobe of right lung', 'Inferior lobe of right lung',
        'Superior lobe of left lung', 'Inferior lobe of left lung', 'Thyroid gland'}
    assert all(s['organSystem'] == ('endocrine' if s['kind'] == 'organ_thyroid' else 'respiratory') for s in visceral)
    assert set(by_id['FMA7088']['elements']) == {'FJ2428', 'FJ2438', 'FJ2439'}
    assert set(by_id['FMA7197']['elements']) == {'FJ2816', *(f'FJ{i}' for i in range(2818, 2825))}
    assert by_id['FMA7198']['elements'] == ['FJ1895']
    assert len(by_id['FMA7207']['elements']) == 23 and len(by_id['FMA7208']['elements']) == 31
    assert by_id['FMA14541']['elements'] == ['FJ2599']
    assert not {'FJ2409', 'FJ2629'} & {f for s in organs for f in s['elements']}
    assert {by_id[key]['elements'][0] for key in ('FMA19618', 'FMA19617', 'FMA18247')} == {'FJ3132', 'FJ3133', 'FJ3134'}
    assert all(by_id[key]['organSystem'] == 'reproductive' for key in ('FMA19618', 'FMA19617', 'FMA18247'))
    cartilages = [s for s in catalog if s['layer'] == 'cartilage']
    assert len(cartilages) == 54
    assert all(not s['standard'] and s['boneCount'] == 0 and s['mesh'] for s in cartilages)
    assert {region: sum(s['region'] == region for s in cartilages)
            for region in ('skull', 'neck', 'spine', 'thorax', 'lower')} == {'skull': 5, 'neck': 8, 'spine': 23, 'thorax': 14, 'lower': 4}
    # An independently enumerated sequence catches absent, duplicate or shifted
    # disc labels, including the source's unnamed T12-L1 mesh.
    spine = [f'C{i}' for i in range(2, 8)] + [f'T{i}' for i in range(1, 13)] + [f'L{i}' for i in range(1, 6)] + ['S1']
    expected_levels = list(zip(spine, spine[1:]))
    discs = sorted((s for s in cartilages if 'discLevel' in s),
                   key=lambda s: -(s['min'][1] + s['max'][1]) / 2)
    assert [tuple(s['discLevel']) for s in discs] == expected_levels
    assert by_id['BP3D_FJ3211']['elements'] == ['FJ3211']
    assert set(by_id['FMA9615']['elements']) == {'FJ2440', 'FJ2769'}
    additions = [s for s in catalog if s.get('source') == 'z-anatomy' and s['layer'] == 'muscles' and s['region'] == 'skull']
    trunk = [s for s in catalog if s['kind'].startswith('trunk_')]
    assert len(trunk) == 8 and all(s['source'] == 'z-anatomy' and s['region'] == 'abdomen' for s in trunk)
    assert len(additions) == 52
    assert all(s['region'] == 'skull' and s['mesh'] and s['layer'] == 'muscles' for s in additions)
    menisci = [s for s in cartilages if s.get('source') == 'z-anatomy']
    assert {s['id'] for s in menisci} == {'ZAN_' + kind + '_meniscus_' + side
                                        for kind in ('medial', 'lateral') for side in ('l', 'r')}
    assert {s['sourceObject'] for s in menisci} == {kind + ' meniscus.' + side
                                                 for kind in ('Medial', 'Lateral') for side in ('l', 'r')}
    assert all(s['region'] == 'lower' and s['kind'].startswith('cartilage_meniscus_') for s in menisci)
    for side in ('l', 'r'):
        medial, lateral = [by_id['ZAN_' + kind + '_meniscus_' + side] for kind in ('medial', 'lateral')]
        assert abs(sum(medial[k][0] for k in ('min', 'max'))) < abs(sum(lateral[k][0] for k in ('min', 'max')))
        assert all(.34 < s['min'][1] < s['max'][1] < .40 for s in (medial, lateral))
    for name in ('zygomaticus_major_muscle', 'zygomaticus_minor_muscle'):
        assert all('ZAN_' + name + '_' + side in by_id for side in ('l', 'r'))
    assert all(s['boneCount'] == 0 and not s['standard'] for s in catalog if s['layer'] == 'muscles')
    assert sum(s['region'] == 'teeth' for s in catalog) == 28
    element_ids = [f for s in catalog for f in s['elements']]
    assert len(element_ids) == len(set(element_ids)), 'An element is assigned to several structures'
    resources = {}
    for lang in ('values', 'values-fr'):
        resources[lang] = {}
        for path in (ROOT / 'app/src/main/res' / lang).glob('strings_biology*.xml'):
            for s in ET.parse(path).getroot():
                assert s.attrib['name'] not in resources[lang], s.attrib['name']
                resources[lang][s.attrib['name']] = s.text
        for s in catalog:
            assert resources[lang].get(s['name']), (lang, s['id'])
            assert resources[lang].get('bio_summary_' + s['kind']), (lang, s['kind'])
            if s['layer'] == 'muscles':
                assert resources[lang].get('bio_group_' + s['kind']), (lang, s['kind'])
            if s['layer'] in added_layers:
                assert resources[lang].get('bio_group_' + s['category']), (lang, s['category'])
        for family in ('disc', 'costal', 'nasal', 'laryngeal', 'menisci'):
            assert resources[lang].get('bio_group_cartilage_' + family), (lang, family)
    assert resources['values'].keys() == resources['values-fr'].keys()
    gltf, accessor = read_glb(ASSETS / 'atlas.glb')
    from external_visibility import validate as validate_visibility
    document = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf8'))
    provenance = json.loads((ASSETS / 'provenance.json').read_text(encoding='utf8'))
    variant_count = validate_visibility(document, gltf, accessor, provenance)
    assert set(n['name'] for n in gltf['nodes']) == ({s['id'] for s in catalog if s['mesh']} |
                                                  set(document.get('externalHiddenVariants', {})))
    assert len(gltf['nodes']) == len(gltf['meshes']) == len(gltf['materials']) == sum(s['mesh'] for s in catalog) + variant_count
    triangles, lateral_anomalies = 0, []
    for node in gltf['nodes']:
        if node['name'] in document.get('externalHiddenVariants', {}):
            continue
        s = by_id[node['name']]
        primitive = gltf['meshes'][node['mesh']]['primitives'][0]
        p = accessor(primitive['attributes']['POSITION'])
        normals = accessor(primitive['attributes']['NORMAL'])
        indices = accessor(primitive['indices'])
        assert np.isfinite(p).all() and np.isfinite(normals).all()
        assert p.shape == normals.shape and len(indices) % 3 == 0 and int(indices.max()) < len(p)
        assert np.allclose(np.linalg.norm(normals, axis=1), 1., atol=1e-5)
        assert np.allclose(p.min(axis=0), s['min'], atol=1e-6)
        assert np.allclose(p.max(axis=0), s['max'], atol=1e-6)
        assert primitive['material'] == node['mesh']  # Independent selection highlight.
        if 'color' in s:
            color = np.array(s['color'])
            assert color.shape == (3,) and np.isfinite(color).all() and ((0 <= color) & (color <= 1)).all()
            material = gltf['materials'][primitive['material']]['pbrMetallicRoughness']
            alpha = s.get('opacity', 1.)
            assert 0 < alpha <= 1
            assert np.allclose(material['baseColorFactor'], [*color, alpha])
            assert (gltf['materials'][primitive['material']].get('alphaMode') == 'BLEND') == (alpha < 1)
        centre_x = float((p[:, 0].min() + p[:, 0].max()) / 2)
        if s.get('sourceObject') in {'Vagus nerve (X).l', 'Vagus nerve (X).r'}:
            # The side names the proximal nerve, not its visceral branches that
            # may cross the midline. Check the first 4 cm below its upper end.
            proximal = p[p[:, 1] > p[:, 1].max() - .04]
            centre_x = float(proximal[:, 0].mean())
            assert (centre_x > 0) == s['sourceObject'].endswith('.l')
        visceral_sides = {
            'FMA50039',  # Right coronary tree wraps around the left-shifted heart.
            'FMA15415', 'FMA71710',  # Left hepatic/portal branches within the liver.
            'FMA14781', 'FMA15397',  # Right gastro-omental vessels curve along the stomach.
            'FMA4716',  # Right marginal cardiac vein, right relative to the heart.
        }
        if s['id'] not in visceral_sides and (('left' in s['sourceName'].split() and centre_x < -.005) or ('right' in s['sourceName'].split() and centre_x > .005)):
            lateral_anomalies.append((s['id'], s['sourceName'], centre_x))
        triangles += len(indices) // 3
    provenance = json.loads((ASSETS / 'provenance.json').read_text())
    assert triangles == provenance['triangles']
    assert provenance['facialMuscles']['structures'] == len(additions)
    assert provenance['cartilage']['structures'] + provenance['menisci']['structures'] == len(cartilages)
    assert provenance['organs']['structures'] == 44
    assert provenance['visceralAdditions']['structures'] == len(visceral) == 6
    assert provenance['combinedAtlasLicense'] == 'https://creativecommons.org/licenses/by-sa/4.0/'
    assert not lateral_anomalies, lateral_anomalies
    for layer in added_layers:
        assert provenance[layer]['structures'] == sum(s['layer'] == layer for s in catalog), layer
    report = {'structures': len(catalog), 'meshes': sum(s['mesh'] for s in catalog), 'displayVariants': variant_count, 'muscularStructures': 442,
              'addedHeadStructures': len(additions),
              'cartilagesAndDiscs': len(cartilages),
              'menisci': len(menisci),
              'organStructures': len(organs),
              'layers': {layer: sum(s['layer'] == layer for s in catalog) for layer in sorted({s['layer'] for s in catalog})},
              'organSystems': {system: sum(s['organSystem'] == system for s in organs) for system in sorted(systems)},
              'triangles': triangles, 'bytes': sum(p['bytes'] for p in provenance['packaging']['parts']),
              'uncompressedBytes': provenance['packaging']['modelBytes'],
              'bilingualResources': len(resources['values']), 'lateralAnomaliesToReview': lateral_anomalies}
    output = ROOT / 'app/build/anatomy-source/validation.json'
    output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
