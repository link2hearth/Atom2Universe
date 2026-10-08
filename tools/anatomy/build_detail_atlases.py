"""Package the independently sourced female body and local ear for the native atlas.

Provider build(source) returns ([(record, vertices_in_metres, triangle_indices)],
provenance). This writer does not register, simplify or otherwise reshape meshes.
Run from the repository root; outputs stay below Git's individual file limit.
Final mobile packaging after source builds: node tools/anatomy/optimize_mobile.mjs --write
(install offline tool dependencies first with npm ci --prefix tools/anatomy).
"""
import argparse
import gzip
import hashlib
import importlib
import json
from pathlib import Path
import re
import struct
import sys
from xml.sax.saxutils import escape
import numpy as np

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/build/anatomy-source'
RES = ROOT / 'app/src/main/res'
ASSETS = ROOT / 'app/src/main/assets/science/biology'
LAYERS = {'skeleton', 'cartilage', 'muscles', 'organs', 'senses', 'nervous', 'vascular', 'lymphatic', 'connective', 'skin'}


def key(value):
    return re.sub('[^a-z0-9_]', '_', value.lower())


def xml_string(value):
    return escape(value).replace("'", "\\'").replace('"', '\\"')


def build(atlas):
    provider = importlib.import_module(atlas + '_content')
    items, provenance = provider.build(SOURCE)
    if atlas == 'female':
        supplement = importlib.import_module('female_supplement_content')
        additions, report = supplement.build(SOURCE, items)
        items += additions
        provenance['reconstructedSupplement'] = report
        provenance['modifications'] += (' Separate original educational reconstructions supplement the missing external genital '
            'and urethral structures; these are labelled as reconstructions and are not HRA segmentations. '
            'The retained HRA surface coordinates and triangles are unchanged.')
    assert items and provenance['license']
    output = ASSETS / atlas
    output.mkdir(exist_ok=True)
    binary = bytearray()
    gltf = {'asset': {'version': '2.0', 'generator': 'Atom2Universe detailed anatomy importer',
                     'copyright': provenance.get('attribution', 'See provenance.json: CC BY 4.0')},
            'scene': 0, 'scenes': [{'nodes': []}], 'nodes': [], 'meshes': [], 'materials': [],
            'accessors': [], 'bufferViews': []}
    strings = {'en': {}, 'fr': {}}
    catalog, hidden, triangles = [], [], 0

    def accessor(values, component, shape, target, bounds=False):
        binary.extend(b'\0' * (-len(binary) % 4))
        data = np.asarray(values, dtype={5126: '<f4', 5125: '<u4', 5123: '<u2'}[component])
        view = len(gltf['bufferViews'])
        gltf['bufferViews'].append({'buffer': 0, 'byteOffset': len(binary), 'byteLength': data.nbytes, 'target': target})
        binary.extend(data.tobytes())
        record = {'bufferView': view, 'componentType': component, 'count': len(data), 'type': shape}
        if bounds:
            record.update(min=data.min(0).tolist(), max=data.max(0).tolist())
        gltf['accessors'].append(record)
        return len(gltf['accessors']) - 1

    for source_record, vv, ff in items:
        source_record = dict(source_record)
        v, f = np.asarray(vv, dtype=np.float64), np.asarray(ff, dtype=np.int64)
        assert v.ndim == f.ndim == 2 and v.shape[1] == f.shape[1] == 3
        assert len(v) > 0 and len(f) > 0 and np.isfinite(v).all()
        assert f.min() >= 0 and f.max() < len(v)
        identity = source_record['id']
        layer = source_record['layer']
        assert layer in LAYERS
        color = source_record.get('color', [.8, .7, .6])
        opacity = source_record.get('opacity', 1.)
        assert len(color) == 3 and all(0 <= c <= 1 for c in color) and 0 < opacity <= 1
        family = 'detail_' + atlas + '_' + key(identity)
        category = atlas + '_' + key(source_record.get('category', layer))
        name = 'bio_name_' + family
        for language, suffix in [('en', 'En'), ('fr', 'Fr')]:
            strings[language][name] = source_record['name' + suffix]
            strings[language]['bio_summary_' + family] = source_record['summary' + suffix]
            group = source_record['group' + suffix]
            group_key = 'bio_group_' + category
            assert group_key not in strings[language] or strings[language][group_key] == group
            strings[language][group_key] = group
        record = {k: value for k, value in source_record.items()
                  if k not in {'nameEn', 'nameFr', 'summaryEn', 'summaryFr', 'groupEn', 'groupFr', 'hiddenByDefault'}}
        record.update(name=name, kind=family, category=category, mesh=True,
                      standard=source_record.get('standard', False), boneCount=source_record.get('boneCount', 0),
                      elements=source_record.get('elements', [source_record.get('sourceObject', identity)]),
                      color=color, opacity=opacity)
        record.setdefault('sourceName', source_record['nameEn'].lower())
        record.setdefault('region', 'skull' if atlas == 'ear' else 'body')
        assert (layer == 'organs') == ('organSystem' in record), identity
        if source_record.get('hiddenByDefault', False):
            hidden.append(identity)

        n = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
        normals = np.column_stack([np.bincount(f.ravel(), weights=np.repeat(n[:, axis], 3), minlength=len(v))
                                   for axis in range(3)])
        lengths = np.linalg.norm(normals, axis=1)
        normals /= np.maximum(lengths[:, None], 1e-30)
        normals[lengths < 1e-20] = [0, 1, 0]
        position = accessor(v, 5126, 'VEC3', 34962, True)
        normal = accessor(normals, 5126, 'VEC3', 34962)
        indices = accessor(f.reshape(-1), 5123 if len(v) <= 65535 else 5125, 'SCALAR', 34963)
        index = len(gltf['meshes'])
        material = {'name': identity, 'pbrMetallicRoughness': {'baseColorFactor': [*color, opacity],
                    'metallicFactor': 0., 'roughnessFactor': .65}, 'doubleSided': True}
        if opacity < 1:
            material['alphaMode'] = 'BLEND'
        gltf['materials'].append(material)
        gltf['meshes'].append({'name': identity, 'primitives': [{'attributes': {'POSITION': position, 'NORMAL': normal},
                                                            'indices': indices, 'material': index}]})
        gltf['nodes'].append({'name': identity, 'mesh': index})
        gltf['scenes'][0]['nodes'].append(index)
        record['min'] = gltf['accessors'][position]['min']
        record['max'] = gltf['accessors'][position]['max']
        catalog.append(record)
        triangles += len(f)

    assert len({s['id'] for s in catalog}) == len(catalog)
    gltf['buffers'] = [{'byteLength': len(binary)}]
    payload = json.dumps(gltf, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    payload += b' ' * (-len(payload) % 4)
    binary.extend(b'\0' * (-len(binary) % 4))
    model = (struct.pack('<III', 0x46546C67, 2, 28 + len(payload) + len(binary)) +
             struct.pack('<II', len(payload), 0x4E4F534A) + payload +
             struct.pack('<II', len(binary), 0x004E4942) + binary)
    parts = []
    for start in range(0, len(model), 48 * 1024 * 1024):
        name = f'atlas-{len(parts):03d}.part.gzip'  # .gz would be altered by Android asset merging.
        data = gzip.compress(model[start:start + 48 * 1024 * 1024], compresslevel=9, mtime=0)
        (output / name).write_bytes(data)
        parts.append({'file': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    layers = list(dict.fromkeys(s['layer'] for s in catalog))
    defaults = provenance.get('defaultLayers', [layer for layer in layers if layer != 'skin'])
    assert set(defaults) <= set(layers) and defaults
    (output / 'catalog.json').write_text(json.dumps({'version': 8, 'atlas': atlas,
        'modelParts': [p['file'] for p in parts], 'modelBytes': len(model), 'defaultLayers': defaults,
        'hiddenByDefault': hidden, 'structures': catalog}, ensure_ascii=False, indent=2), encoding='utf8')
    provenance.update(structures=len(catalog), triangles=triangles,
        packaging={'format': 'GLB split into independent gzip members; concatenate decompressed data in modelParts order',
                   'modelBytes': len(model), 'modelSha256': hashlib.sha256(model).hexdigest(), 'parts': parts})
    (output / 'provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf8')
    for language, directory in [('en', 'values'), ('fr', 'values-fr')]:
        content = '\n'.join(f'    <string name="{k}">{xml_string(v)}</string>' for k, v in strings[language].items())
        (RES / directory / ('strings_biology_' + atlas + '_catalog.xml')).write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/anatomy/build_detail_atlases.py -->\n<resources>\n' +
            content + '\n</resources>\n', encoding='utf8')
    for path in output.glob('atlas-???.part.gzip'):
        if path.name not in {p['file'] for p in parts} and re.fullmatch(r'atlas-\d{3}\.part\.gzip', path.name):
            path.unlink()
    print(json.dumps({'atlas': atlas, 'structures': len(catalog), 'triangles': triangles,
                      'bytes': sum(p['bytes'] for p in parts), 'defaultLayers': defaults, 'hidden': hidden}, indent=2))
    if atlas == 'female':
        from external_visibility import apply as add_external_visibility
        add_external_visibility(atlas)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('atlas', choices=['female', 'ear'], nargs='+')
    for atlas in parser.parse_args().atlas:
        build(atlas)
