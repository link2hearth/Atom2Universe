"""Named Z-Anatomy head muscles missing from the BodyParts3D 4.0 subset.

Source meshes remain separate, including the bilateral portions of circular and
midline muscles. ZAN identifiers are local mesh identities, never invented FMA IDs.
"""
import hashlib
import re
import numpy as np
from fbx_geometry import meshes

SOURCE_URL = 'https://raw.githubusercontent.com/LluisV/Z-Anatomy/PC-Version/Resources/Models/FBX/MuscularSystem100.fbx'
SOURCE_SHA256 = '4c19df534d5d84aabbce08604306aa0485b43e8a2483c72a95b569e1dfea2279'
LICENSE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/'

# One similarity transform for the whole addition: centimetres to metres, then
# uniform scale/rotation/translation. No per-muscle warping or invented anatomy.
# Reproduce with align_z_anatomy.py. Skull and mandible differ between sources;
# residual nearest-vertex distances are recorded, not a claim of medical accuracy.
ROW_MATRIX = np.array([
    [1.0245816323653203, .0002651241834706599, .0016166015587164326],
    [-.00028549711435165863, 1.0245013718821046, .012925268852140443],
    [-.001613128274503395, -.012925702792101803, 1.0245001362109807],
])
OFFSET = np.array([-.0001746841848163929, -.1104264390017613, .06748972709379279])

# Exact FBX name | French label | educational family | Wikipedia search EN | FR
_TERMS = """
Frontalis muscle|Frontal|scalp|Frontalis muscle|Muscle frontal
Occipitalis muscle|Occipital|scalp|Occipitalis muscle|Muscle occipital
Temporoparietalis muscle|Temporo-pariétal|scalp|Temporoparietalis muscle|Muscle temporo-pariétal
Nasalis muscle|Nasal|nasal|Nasalis muscle|Muscle nasal
Orbital part of orbicularis oculi|Orbiculaire de l’œil · portion orbitaire|eye_closure|Orbicularis oculi muscle|Muscle orbiculaire de l’œil
Palpebral part of orbicularis oculi|Orbiculaire de l’œil · portion palpébrale|eye_closure|Orbicularis oculi muscle|Muscle orbiculaire de l’œil
Orbicularis oris muscle|Orbiculaire de la bouche|lip_closure|Orbicularis oris muscle|Muscle orbiculaire de la bouche
Zygomaticus major muscle|Grand zygomatique|zygomatic_major|Zygomaticus major muscle|Muscle grand zygomatique
Zygomaticus minor muscle|Petit zygomatique|zygomatic_minor|Zygomaticus minor muscle|Muscle petit zygomatique
Corrugator supercilii|Corrugateur du sourcil|brow|Corrugator supercilii muscle|Muscle corrugateur du sourcil
Depressor anguli oris|Abaisseur de l’angle de la bouche|lip_depression|Depressor anguli oris muscle|Muscle abaisseur de l’angle de la bouche
Depressor labii inferioris|Abaisseur de la lèvre inférieure|lip_depression|Depressor labii inferioris muscle|Muscle abaisseur de la lèvre inférieure
Levator anguli oris|Élévateur de l’angle de la bouche|lip_elevation|Levator anguli oris muscle|Muscle élévateur de l’angle de la bouche
Procerus muscle|Procérus|brow|Procerus muscle|Muscle procérus
Risorius muscle|Risorius|risorius|Risorius muscle|Muscle risorius
Levator labii superioris|Élévateur de la lèvre supérieure|lip_elevation|Levator labii superioris muscle|Muscle élévateur de la lèvre supérieure
Depressor septi nasi|Abaisseur du septum nasal|nasal|Depressor septi nasi muscle|Muscle abaisseur du septum nasal
Mentalis muscle|Mentonnier|mentalis|Mentalis muscle|Muscle mentonnier
Levator nasolabialis|Élévateur de la lèvre supérieure et de l’aile du nez|nasal|Levator labii superioris alaeque nasi muscle|Muscle élévateur de la lèvre supérieure et de l’aile du nez
Bucinator|Buccinateur|buccinator|Buccinator muscle|Muscle buccinateur
Superficial part of masseter|Masséter · portion superficielle|masseter|Masseter muscle|Muscle masséter
Deep part of masseter|Masséter · portion profonde|masseter|Masseter muscle|Muscle masséter
Medial pterygoid muscle|Ptérygoïdien médial|pterygoid|Medial pterygoid muscle|Muscle ptérygoïdien médial
Inferior head of lateral pterygoid muscle|Ptérygoïdien latéral · chef inférieur|pterygoid|Lateral pterygoid muscle|Muscle ptérygoïdien latéral
Superior head of lateral pterygoid muscle|Ptérygoïdien latéral · chef supérieur|pterygoid|Lateral pterygoid muscle|Muscle ptérygoïdien latéral
Temporalis muscle|Temporal|temporalis|Temporalis muscle|Muscle temporal
"""
TERMS = {r.split('|')[0]: tuple(r.split('|')[1:]) for r in _TERMS.strip().splitlines()}

_SUMMARIES = {
    'scalp': ('Superficial scalp muscle, involved in movements of the scalp and facial expression.', 'Muscle superficiel du cuir chevelu, participant à ses mouvements et à l’expression du visage.'),
    'nasal': ('Facial muscle acting around the nose, involved in movements of the nostrils or upper lip.', 'Muscle de la mimique situé autour du nez, participant aux mouvements des narines ou de la lèvre supérieure.'),
    'eye_closure': ('Part of the circular muscle around the eye, which closes the eyelids.', 'Portion du muscle circulaire entourant l’œil, qui ferme les paupières.'),
    'lip_closure': ('Muscle surrounding the mouth opening. It closes and shapes the lips during speech and facial expression.', 'Muscle entourant l’ouverture de la bouche. Il ferme et façonne les lèvres pendant la parole et la mimique.'),
    'zygomatic_major': ('Pulls the corner of the mouth upward and outward, contributing to a smile.', 'Attire la commissure des lèvres vers le haut et l’extérieur, participant au sourire.'),
    'zygomatic_minor': ('Raises the upper lip, contributing to facial expression.', 'Élève la lèvre supérieure et participe à la mimique du visage.'),
    'brow': ('Facial muscle in the brow region, contributing to frowning and movement of the skin between the eyebrows.', 'Muscle de la région sourcilière, participant au froncement et aux mouvements de la peau entre les sourcils.'),
    'lip_depression': ('Lowers part of the lower lip or the corner of the mouth during facial expression.', 'Abaisse une partie de la lèvre inférieure ou la commissure de la bouche pendant la mimique.'),
    'lip_elevation': ('Raises the upper lip or corner of the mouth during facial expression.', 'Élève la lèvre supérieure ou la commissure de la bouche pendant la mimique.'),
    'risorius': ('Draws the corner of the mouth sideways.', 'Attire la commissure de la bouche latéralement.'),
    'mentalis': ('Raises the skin of the chin and helps protrude the lower lip.', 'Soulève la peau du menton et contribue à avancer la lèvre inférieure.'),
    'buccinator': ('Compresses the cheek against the teeth and helps keep food between the chewing surfaces.', 'Comprime la joue contre les dents et aide à maintenir les aliments entre les surfaces de mastication.'),
    'masseter': ('A chewing muscle that elevates the mandible to close the jaw. Its superficial and deep portions are separately selectable.', 'Muscle masticateur qui élève la mandibule pour fermer la mâchoire. Ses portions superficielle et profonde sont sélectionnables séparément.'),
    'pterygoid': ('Deep chewing muscle participating in coordinated movements of the mandible.', 'Muscle masticateur profond participant aux mouvements coordonnés de la mandibule.'),
    'temporalis': ('Fan-shaped chewing muscle at the temple, helping elevate and retract the mandible.', 'Muscle masticateur en éventail au niveau de la tempe, contribuant à élever et à reculer la mandibule.'),
}
summaries = {'muscle_' + k: v for k, v in _SUMMARIES.items()}


def identity(name):
    return 'ZAN_' + re.sub('[^a-z0-9]+', '_', name.lower()).strip('_')


def entries():
    return [(identity(name + '.' + side), name + '.' + side, [name + '.' + side], False, 0)
            for name in TERMS for side in ('l', 'r')]


def describe(source_name):
    base, side = source_name.rsplit('.', 1)
    french, family, wiki_en, wiki_fr = TERMS[base]
    english = ('Left ' if side == 'l' else 'Right ') + ('buccinator' if base == 'Bucinator' else base[0].lower() + base[1:])
    french += ' · côté ' + ('gauche' if side == 'l' else 'droit')
    return english, french, 'muscle_' + family, 'skull', wiki_en, wiki_fr


def load(source):
    path = source / 'Z-Anatomy-MuscularSystem100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SOURCE_SHA256, 'Unexpected Z-Anatomy source version'
    names = [name for _, name, _, _, _ in entries()]
    result = meshes(path, names)
    assert result.keys() == set(names)
    for name, (vertices, faces) in result.items():
        aligned = vertices / 100 @ ROW_MATRIX + OFFSET
        # All additions must remain at the head and retain anatomical laterality.
        assert aligned[:, 1].min() > 1.40 and aligned[:, 1].max() < 1.67, name
        assert (aligned[:, 0].mean() > 0) == name.endswith('.l'), name
        result[name] = (aligned.tolist(), faces.tolist())
    return result


def provenance(source):
    return {
        'source': SOURCE_URL, 'sourceSha256': SOURCE_SHA256, 'license': LICENSE_URL,
        'licenseDeclaration': 'https://github.com/Z-Anatomy/Models-of-human-anatomy/blob/master/License.txt',
        'structures': len(entries()), 'sourceObjects': [n for _, n, _, _, _ in entries()],
        'modifications': 'Selected facial and chewing muscles; static FBX hierarchy baked including mirrored instances; centimetres to metres; shared similarity registration to BodyParts3D skull; normals recomputed; per-structure materials. Original facial mesh detail retained.',
        'alignment': {'rowMatrix': ROW_MATRIX.tolist(), 'offsetMetres': OFFSET.tolist(),
                      'referenceSourceSha256': '294a649765cd060a62a4095da52b9c8ef2d97769aa447e196448aa5f7d596dea',
                      'method': 'Trimmed nearest-vertex similarity registration on frontal bone, paired maxillae, paired zygomatic bones and mandible; parietal bones held out for checking. Sources differ; this is not medical validation.',
                      'medianResidualRangeMm': [.50, 3.08], 'maxP95ResidualMm': 6.01},
    }
