"""Eight Z-Anatomy trunk muscles absent from the BodyParts3D subset.

The posterior serratus and oblique muscles already present are not duplicated.
All additions use one skeletal registration without per-muscle deformation.
Run this file to reproduce registration diagnostics under app/build, not assets.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
sys.dont_write_bytecode = True
import numpy as np
from fbx_geometry import meshes

SOURCE_URL = 'https://raw.githubusercontent.com/LluisV/Z-Anatomy/PC-Version/Resources/Models/FBX/MuscularSystem100.fbx'
SOURCE_SHA256 = '4c19df534d5d84aabbce08604306aa0485b43e8a2483c72a95b569e1dfea2279'
SKELETAL_SHA256 = '294a649765cd060a62a4095da52b9c8ef2d97769aa447e196448aa5f7d596dea'
LICENSE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/'
REGISTRATION = Path(__file__).with_name('trunk_muscle_registration.json')

# Source object | French label | family | region | Wikipedia EN | FR
_TERMS = '''
Rectus abdominis muscle|Droit de l’abdomen|trunk_rectus|abdomen|Rectus abdominis muscle|Muscle droit de l’abdomen
Transversus abdominis muscle|Transverse de l’abdomen|trunk_transversus|abdomen|Transverse abdominal muscle|Muscle transverse de l’abdomen
Quadratus lumborum muscle|Carré des lombes|trunk_quadratus|abdomen|Quadratus lumborum muscle|Muscle carré des lombes
Pyramidalis muscle|Pyramidal de l’abdomen|trunk_pyramidalis|abdomen|Pyramidalis muscle|Muscle pyramidal de l’abdomen
'''
TERMS = {r.split('|')[0]: tuple(r.split('|')[1:]) for r in _TERMS.strip().splitlines()}
groups = {
    'trunk_rectus': ('Rectus abdominis', 'Droits de l’abdomen'),
    'trunk_transversus': ('Transversus abdominis', 'Transverses de l’abdomen'),
    'trunk_quadratus': ('Quadratus lumborum', 'Carrés des lombes'),
    'trunk_pyramidalis': ('Pyramidalis', 'Pyramidaux de l’abdomen'),
}
summaries = {
    'trunk_rectus': ('Paired muscle of the anterior abdominal wall extending from the pubis towards the lower thorax. It contributes to trunk flexion and abdominal compression.', 'Muscle pair de la paroi abdominale antérieure, s’étendant du pubis vers le thorax inférieur. Il participe à la flexion du tronc et à la compression abdominale.'),
    'trunk_transversus': ('Deep, mostly transverse muscle layer of the anterolateral abdominal wall. It compresses the abdominal contents and contributes to control of the trunk.', 'Couche musculaire profonde, principalement transversale, de la paroi abdominale antérolatérale. Elle comprime le contenu abdominal et participe au contrôle du tronc.'),
    'trunk_quadratus': ('Posterior abdominal wall muscle connecting the iliac crest, lumbar transverse processes and twelfth rib. Its fibres have variable arrangements; its contribution to lumbar movement is modest relative to the main spinal muscles.', 'Muscle de la paroi abdominale postérieure reliant la crête iliaque, les processus transverses lombaires et la douzième côte. Ses faisceaux sont variables ; sa contribution aux mouvements lombaires est modeste par rapport aux principaux muscles du rachis.'),
    'trunk_pyramidalis': ('Small triangular muscle in front of the lower rectus abdominis. Its presence varies between people; it is generally associated with tension of the linea alba, although its precise function remains uncertain.', 'Petit muscle triangulaire devant la partie inférieure du droit de l’abdomen. Sa présence varie selon les personnes ; il est généralement associé à la tension de la ligne blanche, mais sa fonction précise reste incertaine.'),
}
references = [
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/11-4-axial-muscles-of-the-abdominal-wall-and-thorax',
    'https://pubmed.ncbi.nlm.nih.gov/18441751/',
    'https://pubmed.ncbi.nlm.nih.gov/19159363/',
]


def entries(concepts=None, partof=None):
    return [('ZAN_' + re.sub('[^a-z0-9]+', '_', name.lower()).strip('_'), name, [name], False, 0)
            for base in TERMS for side in ('l', 'r') for name in [base + '.' + side]]


def describe(name):
    base, side = name.rsplit('.', 1)
    french, family, region, wiki_en, wiki_fr = TERMS[base]
    assert side in ('l', 'r')
    english = ('Left ' if side == 'l' else 'Right ') + base[0].lower() + base[1:]
    french += ' · côté ' + ('gauche' if side == 'l' else 'droit')
    return english, french, family, region, wiki_en, wiki_fr


def attributes(name):
    assert name.rsplit('.', 1)[0] in TERMS
    return {'color': [.62, .16, .14]}


def load(source):
    path = source / 'Z-Anatomy-MuscularSystem100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SOURCE_SHA256
    registration = json.loads(REGISTRATION.read_text(encoding='utf8'))
    matrix, offset = np.array(registration['matrix']), np.array(registration['offset'])
    singular = np.linalg.svd(matrix, compute_uv=False)
    assert np.ptp(singular) < 1e-10 and np.linalg.det(matrix) > 0
    result = meshes(path, [name for _, name, _, _, _ in entries()])
    for name, (v, f) in result.items():
        aligned = v / 100 @ matrix + offset
        assert .77 < aligned[:, 1].min() < aligned[:, 1].max() < 1.22, name
        assert (aligned[:, 0].mean() > 0) == name.endswith('.l'), name
        assert np.isfinite(aligned).all() and f.min() >= 0 and f.max() < len(v)
        result[name] = (aligned.tolist(), f.tolist())
    return result


def provenance(source, selected_entries=None):
    registration = json.loads(REGISTRATION.read_text(encoding='utf8'))
    return {
        'source': SOURCE_URL, 'sourceSha256': SOURCE_SHA256, 'license': LICENSE_URL,
        'licenseDeclaration': 'https://github.com/Z-Anatomy/Models-of-human-anatomy/blob/master/License.txt',
        'structures': len(entries()), 'sourceObjects': [name for _, name, _, _, _ in entries()],
        'modifications': 'Selected paired rectus abdominis, transversus abdominis, quadratus lumborum and pyramidalis muscles. Static hierarchy including mirrored instances baked, centimetres converted to metres, one shared uniform-scale/rotation/translation fitted to BodyParts3D bones, normals recomputed. Complete source topology preserved; no per-muscle warp or duplication of the existing oblique and posterior serratus muscles.',
        'alignment': registration,
        'limitations': 'Approximate registration between different source anatomies. Median point-to-bone-surface errors range from 1.31 to 3.34 mm; 95th-percentile errors reach 13.75 mm at the twelfth ribs and 11.84 mm at the pelvis. Attachments therefore remain approximate and are not a validated clinical reconstruction. No contraction or muscle-fibre simulation.',
        'educationalReferences': references,
    }


def reproduce_registration(source, geometry):
    """Deterministic diagnostic fit and independent point-to-triangle checks."""
    from mesh_surface import closest, similarity
    assert hashlib.sha256((source / 'Z-Anatomy-SkeletalSystem100.fbx').read_bytes()).hexdigest() == SKELETAL_SHA256
    pairs = {f'Vertebra L{i}': f'{word} lumbar vertebra'
             for i, word in enumerate(('first', 'second', 'third', 'fourth', 'fifth'), 1)}
    pairs.update({f'Hip bone.{suffix}': f'{side} hip bone'
                  for side, suffix in [('left', 'l'), ('right', 'r')]})
    pairs.update({f'{word.title()} rib.{suffix}': f'{side} {word} rib'
                  for word in ('sixth', 'eighth', 'tenth', 'twelfth')
                  for side, suffix in [('left', 'l'), ('right', 'r')]})
    z = meshes(source / 'Z-Anatomy-SkeletalSystem100.fbx', list(pairs), positions_only=True)
    held_out = {'Vertebra L2', 'Vertebra L4', 'Eighth rib.l', 'Eighth rib.r'}
    all_points = {name: np.unique(np.round(z[name][0] / 100, 9), axis=0) for name in pairs}
    points = {}
    for name, v in all_points.items():
        rng = np.random.default_rng(53)
        points[name] = v[rng.choice(len(v), min(400, len(v)), False)]
    def nearest(a, b):
        indices = []
        for p in np.array_split(a, max(1, (len(a) + 63) // 64)):
            distances = (p*p).sum(1)[:, None] + (b*b).sum(1)[None, :] - 2*p@b.T
            indices.extend(distances.argmin(1))
        q = b[indices]
        return q, np.linalg.norm(a-q, axis=1)
    result = json.loads(REGISTRATION.read_text(encoding='utf8'))
    matrix, offset = np.array(result['initialMatrix']), np.array(result['initialOffset'])
    for _ in range(60):
        aa, bb, weights = [], [], []
        for name, v in points.items():
            if name in held_out:
                continue
            q, distances = nearest(v @ matrix + offset, geometry[pairs[name]][0])
            keep = distances <= np.quantile(distances, .9)
            aa.append(v[keep]); bb.append(q[keep]); weights.extend([1/keep.sum()]*keep.sum())
        matrix, offset = similarity(np.concatenate(aa), np.concatenate(bb), weights)
    result.update(matrix=matrix.tolist(), offset=offset.tolist(),
                  scale=float(np.linalg.svd(matrix, compute_uv=False).mean()))
    for name, v in points.items():
        distances = nearest(v @ matrix + offset, geometry[pairs[name]][0])[1] * 1000
        result['checks'][name] = {'medianMm': float(np.median(distances)),
                                 'p95Mm': float(np.quantile(distances, .95)), 'heldOut': name in held_out}
        original = all_points[name]; rng = np.random.default_rng(83)
        check = original[rng.choice(len(original), min(200, len(original)), False)] @ matrix + offset
        target, faces = geometry[pairs[name]]
        distances = closest(check, target[faces])[1] * 1000
        result['surfaceChecks'][name] = {'medianMm': float(np.median(distances)),
            'p95Mm': float(np.quantile(distances, .95)), 'maximumMm': float(distances.max()), 'samples': len(check)}
    return result


if __name__ == '__main__':
    from validate_atlas import read_glb
    root = Path(__file__).resolve().parents[2]
    source = root / 'app/build/anatomy-source'
    assets = root / 'app/src/main/assets/science/biology'
    catalog = json.loads((assets / 'catalog.json').read_text(encoding='utf8'))['structures']
    by_id = {r['id']: r for r in catalog}
    gltf, accessor = read_glb(assets / 'atlas.glb')
    geometry = {}
    for node in gltf['nodes']:
        p = gltf['meshes'][node['mesh']]['primitives'][0]
        geometry[by_id[node['name']]['sourceName']] = (accessor(p['attributes']['POSITION']).astype(float),
                                                     accessor(p['indices']).reshape(-1, 3))
    output = source / 'sensory/trunk_registration_reproduced.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(reproduce_registration(source, geometry), indent=2), encoding='utf8')
    print(output)
