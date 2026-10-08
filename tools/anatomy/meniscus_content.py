"""Four source menisci, rigidly registered to the existing knees.

Local ZAN identities preserve the exact source objects and their laterality.
No articular cartilage, ligament, or missing anatomy is synthesized.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
from fbx_geometry import meshes

SOURCE_URL = 'https://raw.githubusercontent.com/LluisV/Z-Anatomy/PC-Version/Resources/Models/FBX/Joints100.fbx'
SOURCE_SHA256 = 'f4ba7a910cdaef99e31530f368628780d9f06b5d77853f8b721f47f67137e823'
REGISTRATION = Path(__file__).with_name('meniscus_registration.json')
TERMS = {'Medial meniscus': ('Ménisque médial du genou', 'medial'),
         'Lateral meniscus': ('Ménisque latéral du genou', 'lateral')}


def entries():
    return [('ZAN_' + base.lower().replace(' ', '_') + '_' + side,
             base + '.' + side, [base + '.' + side], False, 0)
            for base in TERMS for side in ('l', 'r')]


def describe(source_name):
    base, side = source_name.rsplit('.', 1)
    french, family = TERMS[base]
    english = ('Left ' if side == 'l' else 'Right ') + base.lower()
    french += ' · côté ' + ('gauche' if side == 'l' else 'droit')
    return english, french, 'cartilage_meniscus_' + family, 'lower', base, 'Ménisque (anatomie)'


summaries = {
    'cartilage_meniscus_medial': (
        'C-shaped fibrocartilage on the inner part of the tibial plateau, between the tibia and femur. It helps distribute loads and improves joint congruence. Its peripheral attachments to the capsule and medial collateral ligament make it less mobile than the lateral meniscus. These attachments are not modelled here.',
        'Fibrocartilage en croissant sur la partie interne du plateau tibial, entre le tibia et le fémur. Il contribue à répartir les charges et à adapter les surfaces articulaires. Ses attaches périphériques à la capsule et au ligament collatéral tibial le rendent moins mobile que le ménisque latéral. Ces attaches ne sont pas représentées ici.'),
    'cartilage_meniscus_lateral': (
        'Curved fibrocartilage on the outer part of the tibial plateau, between the tibia and femur. It helps distribute loads and accommodate the femoral condyle during knee movement. It is generally more mobile than the medial meniscus. The atlas shows its outer shape; fibres and vascular zones are not represented.',
        'Fibrocartilage incurvé sur la partie externe du plateau tibial, entre le tibia et le fémur. Il contribue à répartir les charges et à accompagner le condyle fémoral pendant les mouvements du genou. Il est généralement plus mobile que le ménisque médial. Le modèle montre sa forme externe ; les fibres et zones vascularisées ne sont pas représentées.'),
}


def load(source):
    path = source / 'Z-Anatomy-Joints100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SOURCE_SHA256, 'Unexpected joint source version'
    registration = json.loads(REGISTRATION.read_text())['structures']
    names = [name for _, name, _, _, _ in entries()]
    result = meshes(path, names)
    assert result.keys() == set(names) == registration.keys()
    for name, (vertices, faces) in result.items():
        fit = registration[name]
        matrix, offset = np.array(fit['matrix']), np.array(fit['offset'])
        scales = np.linalg.svd(matrix, compute_uv=False)
        assert np.ptp(scales) < 1e-10 and .90 < scales.mean() < 1.05
        assert np.linalg.det(matrix) > 0
        aligned = vertices / 100 @ matrix + offset
        assert .34 < aligned[:, 1].min() < aligned[:, 1].max() < .40, name
        assert (aligned[:, 0].mean() > 0) == name.endswith('.l'), name
        result[name] = aligned.tolist(), faces.tolist()
    return result


def provenance():
    return {
        'source': SOURCE_URL, 'sourceSha256': SOURCE_SHA256,
        'license': 'https://creativecommons.org/licenses/by-sa/4.0/',
        'structures': 4, 'sourceObjects': [name for _, name, _, _, _ in entries()],
        'modifications': 'Static FBX world transforms including mirrored instances; centimetres to metres; uniform scale and rigid registration to each proximal tibia, then small rigid contact adjustments per meniscus; original source vertices and triangulated topology retained, with no simplification or non-rigid deformation; smooth normals and independent materials.',
        'registration': json.loads(REGISTRATION.read_text()),
        'limitations': 'Different source poses and reduced bone surface detail leave small placement discrepancies. Surface checks describe mesh geometry only, not clinical accuracy. Meniscus attachments, internal fibres, vascular zones and articular cartilage are not represented.',
    }
