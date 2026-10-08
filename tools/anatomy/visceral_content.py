"""Z-Anatomy lung lobes and thyroid missing from the BodyParts3D subset.

The source kidney/inner-ear models with additional NC attributions are excluded.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
from fbx_geometry import meshes
from organ_content import COLORS

SOURCE_URL = 'https://raw.githubusercontent.com/LluisV/Z-Anatomy/PC-Version/Resources/Models/FBX/VisceralSystem100.fbx'
SOURCE_SHA256 = '22b301c93327929c9a14ee582d1848b550a950376e221a6d1078c7256bb83d0e'
REGISTRATION = Path(__file__).with_name('visceral_registration.json')
# Exact object name | French label | educational family | region | display system
TERMS = {
    'Superior lobe of right lung': ('Lobe supérieur du poumon droit', 'lung', 'thorax', 'respiratory'),
    'Middle lobe of right lung': ('Lobe moyen du poumon droit', 'lung', 'thorax', 'respiratory'),
    'Inferior lobe of right lung': ('Lobe inférieur du poumon droit', 'lung', 'thorax', 'respiratory'),
    'Superior lobe of left lung': ('Lobe supérieur du poumon gauche', 'lung', 'thorax', 'respiratory'),
    'Inferior lobe of left lung': ('Lobe inférieur du poumon gauche', 'lung', 'thorax', 'respiratory'),
    'Thyroid gland': ('Glande thyroïde', 'thyroid', 'neck', 'endocrine'),
}


def entries():
    return [('ZAN_' + name.lower().replace(' ', '_'), name, [name], False, 0) for name in TERMS]


def describe(name):
    french, family, region, _ = TERMS[name]
    wiki = ('Lung', 'Poumon') if family == 'lung' else ('Thyroid', 'Thyroïde')
    return name, french, 'organ_' + family, region, *wiki


def attributes(name):
    _, family, _, system = TERMS[name]
    return {'organSystem': system, 'color': COLORS[family]}


summaries = {
    'organ_lung': (
        'One lobe of a lung. The right lung has three lobes and the left has two. Gas exchange occurs in microscopic alveoli, which are not individually represented. Each lobe can be hidden to inspect deeper structures.',
        'Un lobe pulmonaire. Le poumon droit comporte trois lobes et le gauche deux. Les échanges gazeux ont lieu dans les alvéoles microscopiques, qui ne sont pas représentées individuellement. Chaque lobe peut être masqué pour examiner les structures profondes.'),
    'organ_thyroid': (
        'Endocrine gland at the front of the lower neck, with two lobes joined by an isthmus. Its hormones help regulate metabolism and development. It is distinct from the thyroid cartilage of the larynx.',
        'Glande endocrine située à l’avant de la partie basse du cou, formée de deux lobes reliés par un isthme. Ses hormones contribuent à réguler le métabolisme et le développement. Elle est distincte du cartilage thyroïde du larynx.'),
}


def load(source):
    path = source / 'Z-Anatomy-VisceralSystem100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SOURCE_SHA256
    registration = json.loads(REGISTRATION.read_text())
    matrix, offset = np.array(registration['matrix']), np.array(registration['offset'])
    scales = np.linalg.svd(matrix, compute_uv=False)
    assert np.ptp(scales) < 1e-10 and .9 < scales.mean() < 1.1 and np.linalg.det(matrix) > 0
    result = meshes(path, list(TERMS))
    assert result.keys() == TERMS.keys()
    for name, (vertices, faces) in result.items():
        v = vertices / 100 @ matrix + offset
        if 'left lung' in name:
            adjustment = registration['leftLungAdjustment']
            rotation = np.array(adjustment['matrix'])
            assert np.allclose(rotation @ rotation.T, np.eye(3), atol=1e-10) and np.linalg.det(rotation) > 0
            v = v @ rotation + adjustment['offset']
        assert 1.0 < v[:, 1].min() < v[:, 1].max() < 1.5, name
        if 'lung' in name:
            assert (v[:, 0].mean() > 0) == ('left' in name), name
        result[name] = v.tolist(), faces.tolist()
    return result


def provenance():
    return {'source': SOURCE_URL, 'sourceSha256': SOURCE_SHA256,
            'license': 'https://creativecommons.org/licenses/by-sa/4.0/',
            'structures': len(TERMS), 'sourceObjects': list(TERMS),
            'modifications': 'Six static meshes only; FBX hierarchy and instance transforms; centimetres to metres; one shared thoracic similarity registration, then a joint rigid adjustment of both left lung lobes to reduce heart contact errors. No non-rigid deformation; smooth normals and illustrative colours. Source topology retained without simplification.',
            'registration': json.loads(REGISTRATION.read_text()),
            'limitations': 'Cross-source alignment is approximate; source poses, lung inflation and surface detail differ. This is an educational surface atlas, not a clinically validated model. Alveoli and fine tissue structure are not modelled.',
            'excludedSourceAssets': 'All other VisceralSystem meshes, including the kidneys with a separate CC BY-NC attribution, are excluded. Both kidneys in the atlas come from BodyParts3D 4.0 CC BY 4.0.'}
