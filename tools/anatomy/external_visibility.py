"""Add reversible display variants; never change the anatomical source meshes."""
import copy
import gzip
import hashlib
import json
from pathlib import Path
import struct
import numpy as np

ASSETS = Path(__file__).resolve().parents[2] / 'app/src/main/assets/science/biology'
PREFIX = '__external_hidden_'
# Explicit anatomical identities, independent of language and broad pelvic groups.
MALE_EXTERNAL = {
    'FMA7211', 'FMA7212', 'FMA18256', 'FMA18257',  # testes and epididymides
    'FMA19618', 'FMA19617', 'FMA18247',  # penis
    'FMA21354', 'FMA20818', 'FMA20819', 'FMA21384', 'FMA21385', 'FMA21386',  # penile vessels
}
FEMALE_EXTERNAL = {'A2U_FEMALE_' + name for name in (
    'CLITORAL_GLANS', 'CLITORAL_BODY', 'CRUS_LEFT', 'CRUS_RIGHT', 'BULB_LEFT', 'BULB_RIGHT',
    'LABIUM_MAJUS_LEFT', 'LABIUM_MAJUS_RIGHT', 'LABIUM_MINUS_LEFT', 'LABIUM_MINUS_RIGHT')}


def apply(atlas):
    from reproductive_surface import build
    directory = ASSETS / ('female' if atlas == 'female' else '')
    assert atlas in {'male', 'female'}
    catalog = json.loads((directory / 'catalog.json').read_text(encoding='utf8'))
    provenance = json.loads((directory / 'provenance.json').read_text(encoding='utf8'))
    if 'mobileOptimization' in provenance:
        raise ValueError('Rebuild the source atlas before editing display variants, then rerun optimize_mobile.mjs --write')
    ids = {s['id'] for s in catalog['structures']}
    external = MALE_EXTERNAL if atlas == 'male' else FEMALE_EXTERNAL
    assert external <= ids
    raw = b''.join(gzip.decompress((directory / part).read_bytes()) for part in catalog['modelParts'])
    size = struct.unpack_from('<I', raw, 12)[0]
    gltf = json.loads(raw[20:20 + size])
    binary = bytearray(raw[28 + size:])
    arrays = ('nodes', 'meshes', 'materials', 'accessors', 'bufferViews')
    # A standalone rerun replaces only our appended display data.
    previous = provenance.get('externalVisibility', {}).get('base')
    if previous:
        for name in arrays:
            gltf[name] = gltf[name][:previous[name]]
        binary = binary[:previous['binaryBytes']]
        gltf['scenes'][gltf['scene']]['nodes'] = list(range(previous['nodes']))
    base = {name: len(gltf[name]) for name in arrays}
    base['binaryBytes'] = len(binary)

    def read(index):
        a = gltf['accessors'][index]
        view = gltf['bufferViews'][a['bufferView']]
        width = 3 if a['type'] == 'VEC3' else 1
        result = np.frombuffer(binary, dtype={5126: '<f4', 5125: '<u4', 5123: '<u2'}[a['componentType']],
            count=a['count'] * width, offset=view.get('byteOffset', 0) + a.get('byteOffset', 0)).copy()
        return result.reshape(-1, width) if width > 1 else result

    primitives = {n['name']: gltf['meshes'][n['mesh']]['primitives'][0] for n in gltf['nodes']}
    meshes = {identity: (read(p['attributes']['POSITION']), read(p['indices']).reshape(-1, 3))
              for identity, p in primitives.items()}
    variants, report = build(atlas, meshes)
    assert not external.intersection(variants)
    assert set(variants) <= ids

    def add(values, component, kind, target):
        binary.extend(b'\0' * (-len(binary) % 4))
        data = np.asarray(values, dtype='<f4' if component == 5126 else '<u4')
        index = len(gltf['bufferViews'])
        gltf['bufferViews'].append({'buffer': 0, 'byteOffset': len(binary), 'byteLength': data.nbytes, 'target': target})
        binary.extend(data.tobytes())
        row = {'bufferView': index, 'componentType': component, 'count': len(data), 'type': kind}
        if kind == 'VEC3':
            row.update(min=data.min(0).tolist(), max=data.max(0).tolist())
        gltf['accessors'].append(row)
        return len(gltf['accessors']) - 1

    mapping, hashes, bounds = {}, {}, {}
    for identity, (vertices, faces) in sorted(variants.items()):
        v, f = np.asarray(vertices, dtype='<f4'), np.asarray(faces, dtype='<u4')
        assert v.shape[1] == f.shape[1] == 3 and len(f) and f.max() < len(v)
        assert np.isfinite(v).all()
        triangle_normals = np.cross(v[f[:, 1]].astype(float) - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
        normals = np.column_stack([np.bincount(f.ravel(), weights=np.repeat(triangle_normals[:, i], 3), minlength=len(v))
                                   for i in range(3)])
        length = np.linalg.norm(normals, axis=1)
        normals /= np.maximum(length[:, None], 1e-30)
        normals[length < 1e-20] = [0, 1, 0]
        primitive = {'attributes': {'POSITION': add(v, 5126, 'VEC3', 34962),
                                    'NORMAL': add(normals, 5126, 'VEC3', 34962)},
                     'indices': add(f.ravel(), 5125, 'SCALAR', 34963), 'material': len(gltf['materials'])}
        gltf['materials'].append(copy.deepcopy(gltf['materials'][primitives[identity]['material']]))
        name = PREFIX + identity
        gltf['scenes'][gltf['scene']]['nodes'].append(len(gltf['nodes']))
        gltf['nodes'].append({'name': name, 'mesh': len(gltf['meshes'])})
        gltf['meshes'].append({'name': name, 'primitives': [primitive]})
        mapping[name] = identity
        bounds[identity] = {'min': v.min(0).tolist(), 'max': v.max(0).tolist()}
        hashes[name] = {'verticesSha256': hashlib.sha256(v.tobytes()).hexdigest(),
                        'indicesSha256': hashlib.sha256(f.tobytes()).hexdigest(), 'triangles': len(f)}

    if variants or previous:
        binary.extend(b'\0' * (-len(binary) % 4))
        gltf['buffers'] = [{'byteLength': len(binary)}]
        payload = json.dumps(gltf, ensure_ascii=False, separators=(',', ':')).encode('utf8')
        payload += b' ' * (-len(payload) % 4)
        raw = (struct.pack('<III', 0x46546C67, 2, 28 + len(payload) + len(binary)) +
               struct.pack('<II', len(payload), 0x4E4F534A) + payload +
               struct.pack('<II', len(binary), 0x004E4942) + binary)
        parts = []
        for start in range(0, len(raw), 48 * 1024 * 1024):
            name = f'atlas-{len(parts):03d}.part.gzip'
            data = gzip.compress(raw[start:start + 48 * 1024 * 1024], compresslevel=9, mtime=0)
            (directory / name).write_bytes(data)
            parts.append({'file': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
        catalog.update(modelParts=[p['file'] for p in parts], modelBytes=len(raw))
        provenance['packaging'].update(modelBytes=len(raw), modelSha256=hashlib.sha256(raw).hexdigest(), parts=parts)
    catalog.update(externalGenitalIds=sorted(external), externalHiddenVariants=mapping, externalHiddenBounds=bounds)
    provenance['externalVisibility'] = {'defaultVisible': False, 'scope': 'External genitalia only; internal reproductive organs retained',
        'hiddenStructures': sorted(external), 'base': base, 'variants': hashes, 'surfaceAdaptation': report,
        'generatorSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'providerSha256': hashlib.sha256(Path(__file__).with_name('reproductive_surface.py').read_bytes()).hexdigest()}
    (directory / 'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding='utf8')
    (directory / 'provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps({'atlas': atlas, 'externalHiddenByDefault': len(external), 'displayVariants': mapping,
                      'modelBytes': len(raw)}, indent=2))


def validate(catalog, gltf, accessor, provenance):
    """Check the additional display objects without relaxing anatomical checks."""
    mapping = catalog.get('externalHiddenVariants', {})
    external = set(catalog.get('externalGenitalIds', []))
    records = {s['id']: s for s in catalog['structures']}
    assert set(mapping).isdisjoint(records) and set(mapping.values()) <= records.keys()
    assert external <= records.keys() and external.isdisjoint(mapping.values())
    assert set(catalog.get('externalHiddenBounds', {})) == set(mapping.values())
    expected = {'male': MALE_EXTERNAL, 'female': FEMALE_EXTERNAL, 'ear': set()}[catalog.get('atlas', 'male')]
    assert external == expected
    assert set(mapping.values()) == ({'FMA7163', 'FMA19667', 'FMA14759', 'FMA14760', 'FMA14341', 'FMA14345'}
        if catalog.get('atlas', 'male') == 'male' else set())
    # These organs must not be hidden by the external-only setting.
    assert not external.intersection({'FMA9600', 'FMA19387', 'FMA19388', 'FMA15900', 'FMA19667',
        'HRAF_473', 'HRAF_479', 'HRAF_431', 'HRAF_469', 'HRAF_470', 'A2U_FEMALE_FEMALE_URETHRA'})
    by_name = {n['name']: n for n in gltf['nodes']}
    assert gltf['scenes'][gltf['scene']]['nodes'] == list(range(len(gltf['nodes'])))
    for name, original in mapping.items():
        assert name == PREFIX + original and name in by_name
        p = gltf['meshes'][by_name[name]['mesh']]['primitives'][0]
        source = gltf['meshes'][by_name[original]['mesh']]['primitives'][0]
        v, n, f = accessor(p['attributes']['POSITION']), accessor(p['attributes']['NORMAL']), accessor(p['indices'])
        assert len(f) and len(f) % 3 == 0 and f.max() < len(v)
        assert np.isfinite(v).all() and np.allclose(np.linalg.norm(n, axis=1), 1., atol=1e-5)
        assert gltf['materials'][p['material']] == gltf['materials'][source['material']]
        bounds = catalog['externalHiddenBounds'][original]
        assert np.array_equal(v.min(0), bounds['min']) and np.array_equal(v.max(0), bounds['max'])
        report = provenance['externalVisibility']['variants'][name]
        assert hashlib.sha256(v.astype('<f4').tobytes()).hexdigest() == report['verticesSha256']
        assert hashlib.sha256(f.astype('<u4').tobytes()).hexdigest() == report['indicesSha256']
        assert len(f) == report['triangles'] * 3
    return len(mapping)


if __name__ == '__main__':
    import sys
    for atlas in sys.argv[1:] or ('male', 'female'):
        apply(atlas)
