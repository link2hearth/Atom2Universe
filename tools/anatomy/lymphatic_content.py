"""Named lymph-node groups and palatine tonsils from Z-Anatomy.

Joined annotations (.j), the generic demonstration node, section planes,
source spleen and thymus are deliberately excluded. Each selected object keeps
its original shape through a uniform-scale rigid regional registration.
"""
import hashlib
import json
from pathlib import Path
import re
import numpy as np
from fbx_geometry import meshes

SOURCE_URL = 'https://raw.githubusercontent.com/LluisV/Z-Anatomy/PC-Version/Resources/Models/FBX/LymphoidOrgans100.fbx'
SOURCE_SHA256 = '310ff82f502f4f3a79e85f99ddc2009bba9514338119af9edff87b20cf1b3609'
LICENSE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/'
REGISTRATION = Path(__file__).with_name('lymphatic_registration.json')

# Exact FBX base name (apart from optional parentheses) | French | display region
_TERMS = '''
Anterior axillary nodes|Ganglions axillaires antérieurs|upper
Anterior inferior jugular nodes|Ganglions jugulaires inférieurs antérieurs|neck
Anterior tibial node|Ganglion tibial antérieur|lower
Apical axillary nodes|Ganglions axillaires apicaux|upper
Appendicular nodes|Ganglions appendiculaires|abdomen
Brachial nodes|Ganglions brachiaux|upper
Brachiocephalic nodes|Ganglions brachio-céphaliques|thorax
Bucinator node|Ganglion buccinateur|skull
Central axillary nodes|Ganglions axillaires centraux|upper
Central superior mesenteric nodes|Ganglions mésentériques supérieurs centraux|abdomen
Coeliac nodes|Ganglions cœliaques|abdomen
Cubital nodes|Ganglions du coude|upper
Cystic node|Ganglion cystique|abdomen
Deep popliteal nodes|Ganglions poplités profonds|lower
Fibular node|Ganglion fibulaire|lower
Ileocolic nodes|Ganglions iléo-coliques|abdomen
Inferior deep lateral cervical nodes|Ganglions cervicaux latéraux profonds inférieurs|neck
Inferior diaphragmatic nodes|Ganglions diaphragmatiques inférieurs|abdomen
Inferior epigastric nodes|Ganglions épigastriques inférieurs|abdomen
Inferior gluteal nodes|Ganglions glutéaux inférieurs|pelvis
Inferior pancreatic nodes|Ganglions pancréatiques inférieurs|abdomen
Inferior superficial inguinal nodes|Ganglions inguinaux superficiels inférieurs|lower
Inferior tracheobronchial nodes|Ganglions trachéo-bronchiques inférieurs|thorax
Infra-auricular nodes|Ganglions infra-auriculaires|skull
Infraclavicular nodes|Ganglions infraclaviculaires|upper
Intercostal nodes|Ganglions intercostaux|thorax
Intermediate common iliac nodes|Ganglions iliaques communs intermédiaires|pelvis
Intermediate deep inguinal node|Ganglion inguinal profond intermédiaire|lower
Intermediate external iliac nodes|Ganglions iliaques externes intermédiaires|pelvis
Intermediate lacunar node|Ganglion lacunaire intermédiaire|pelvis
Intermediate lumbar nodes|Ganglions lombaires intermédiaires|abdomen
Interpectoral nodes|Ganglions interpectoraux|upper
Intraglandular parotid nodes|Ganglions parotidiens intraglandulaires|skull
Intrapulmonary nodes|Ganglions intrapulmonaires|thorax
Jugulodigastric node|Ganglion jugulo-digastrique|neck
Juxta-intestinal mesenteric nodes|Ganglions mésentériques juxta-intestinaux|abdomen
Juxta-oesophageal nodes|Ganglions juxta-œsophagiens|thorax
Lateral aortic nodes|Ganglions latéro-aortiques|abdomen
Lateral axillary nodes|Ganglions axillaires latéraux|upper
Lateral caval nodes|Ganglions latéro-caves|abdomen
Lateral common iliac nodes|Ganglions iliaques communs latéraux|pelvis
Lateral lacunar node|Ganglion lacunaire latéral|pelvis
Lateral pericardial nodes|Ganglions péricardiques latéraux|thorax
Lateral sacral nodes|Ganglions sacraux latéraux|pelvis
Lateral superior jugular node|Ganglion jugulaire supérieur latéral|neck
Lateral vesical nodes|Ganglions vésicaux latéraux|pelvis
Left colic nodes|Ganglions coliques gauches|abdomen
Mandibular node|Ganglion mandibulaire|skull
Mastoid nodes|Ganglions mastoïdiens|skull
Medial common iliac nodes|Ganglions iliaques communs médiaux|pelvis
Medial external iliac nodes|Ganglions iliaques externes médiaux|pelvis
Medial lacunar node|Ganglion lacunaire médial|pelvis
Median sacral nodes|Ganglions sacraux médians|pelvis
Middle colic nodes|Ganglions coliques moyens|abdomen
Nasolabial node|Ganglion naso-labial|skull
Node of arch of azygos vein|Ganglion de la crosse de la veine azygos|thorax
Node of ligamentum arteriosum|Ganglion du ligament artériel|thorax
Obturator nodes|Ganglions obturateurs|pelvis
Occipital nodes|Ganglions occipitaux|skull
Palatine tonsil|Tonsille palatine|skull
Paracolic superior mesenteric nodes|Ganglions mésentériques supérieurs paracoliques|abdomen
Pararectal nodes|Ganglions pararectaux|pelvis
Parasternal nodes|Ganglions parasternaux|thorax
Paratracheal cervical nodes|Ganglions paratrachéaux cervicaux|neck
Paratracheal thoracic nodes|Ganglions paratrachéaux thoraciques|thorax
Posterior axillary nodes|Ganglions axillaires postérieurs|upper
Posterior tibial node|Ganglion tibial postérieur|lower
Postvesical nodes|Ganglions postvésicaux|pelvis
Pre-aortic nodes|Ganglions préaortiques|abdomen
Pre-auricular nodes|Ganglions préauriculaires|skull
Precaecal nodes|Ganglions précæcaux|abdomen
Precaval nodes|Ganglions précaves|abdomen
Prepericardial nodes|Ganglions prépéricardiques|thorax
Pretracheal nodes|Ganglions prétrachéaux|neck
Prevertebral nodes|Ganglions prévertébraux|thorax
Prevesical nodes|Ganglions prévésicaux|pelvis
Proximal deep inguinal node|Ganglion inguinal profond proximal|lower
Retro-aortic nodes|Ganglions rétro-aortiques|abdomen
Retrocaecal nodes|Ganglions rétrocæcaux|abdomen
Retrocaval nodes|Ganglions rétrocaves|abdomen
Retropharyngeal nodes|Ganglions rétropharyngiens|neck
Retropyloric nodes|Ganglions rétropyloriques|abdomen
Right colic nodes|Ganglions coliques droits|abdomen
Right gastric nodes|Ganglions gastriques droits|abdomen
Right gastro-omental nodes|Ganglions gastro-omentaux droits|abdomen
Sigmoid nodes|Ganglions sigmoïdiens|abdomen
Splenic nodes|Ganglions spléniques|abdomen
Subaortic nodes|Ganglions subaortiques|thorax
Submandibular nodes|Ganglions submandibulaires|skull
Submental nodes|Ganglions submentaux|skull
Subpyloric nodes|Ganglions subpyloriques|abdomen
Superficial anterior cervical nodes|Ganglions cervicaux antérieurs superficiels|neck
Superficial lateral cervical nodes|Ganglions cervicaux latéraux superficiels|neck
Superficial parotid nodes|Ganglions parotidiens superficiels|skull
Superficial popliteal nodes|Ganglions poplités superficiels|lower
Superior diaphragmatic nodes|Ganglions diaphragmatiques supérieurs|thorax
Superior gluteal nodes|Ganglions glutéaux supérieurs|pelvis
Superior pancreatic nodes|Ganglions pancréatiques supérieurs|abdomen
Superior pancreaticoduodenal nodes|Ganglions pancréatico-duodénaux supérieurs|abdomen
Superior tracheobronchial nodes|Ganglions trachéo-bronchiques supérieurs|thorax
Superolateral superficial inguinal nodes|Ganglions inguinaux superficiels supéro-latéraux|lower
Superomedial superficial inguinal nodes|Ganglions inguinaux superficiels supéro-médiaux|lower
Supraclavicular nodes|Ganglions supraclaviculaires|neck
Suprapyloric node|Ganglion suprapylorique|abdomen
Supratrochlear nodes|Ganglions supratrochléaires|upper
Thyroid nodes|Ganglions thyroïdiens|neck
'''
TERMS = {row.split('|')[0]: tuple(row.split('|')[1:]) for row in _TERMS.strip().splitlines()}

# The source contains both bilateral objects and intentionally unpaired groups.
# These names are pinned, so future FBX additions never import themselves silently.
_NAMES = '''
(Anterior tibial node).l
(Anterior tibial node).r
(Fibular node).l
(Fibular node).r
(Intermediate deep inguinal node).l
(Intermediate deep inguinal node).r
(Intermediate lacunar node).l
(Intermediate lacunar node).r
(Lateral lacunar node).l
(Lateral lacunar node).r
(Medial lacunar node).l
(Medial lacunar node).r
(Posterior tibial node).l
(Posterior tibial node).r
(Proximal deep inguinal node).l
(Proximal deep inguinal node).r
(Retropyloric nodes)
(Subpyloric nodes)
(Suprapyloric node)
Anterior axillary nodes.l
Anterior axillary nodes.r
Anterior inferior jugular nodes.l
Anterior inferior jugular nodes.r
Apical axillary nodes.l
Apical axillary nodes.r
Appendicular nodes
Brachial nodes.l
Brachial nodes.r
Brachiocephalic nodes
Bucinator node.l
Bucinator node.r
Central axillary nodes.l
Central axillary nodes.r
Central superior mesenteric nodes
Coeliac nodes
Cubital nodes.l
Cubital nodes.r
Cystic node
Deep popliteal nodes.l
Deep popliteal nodes.r
Ileocolic nodes
Inferior deep lateral cervical nodes.l
Inferior deep lateral cervical nodes.r
Inferior diaphragmatic nodes
Inferior epigastric nodes.l
Inferior epigastric nodes.r
Inferior gluteal nodes.l
Inferior gluteal nodes.r
Inferior pancreatic nodes
Inferior superficial inguinal nodes.l
Inferior superficial inguinal nodes.r
Inferior tracheobronchial nodes
Infra-auricular nodes.l
Infra-auricular nodes.r
Infraclavicular nodes.l
Infraclavicular nodes.r
Intercostal nodes.l
Intercostal nodes.r
Intermediate common iliac nodes
Intermediate external iliac nodes.l
Intermediate external iliac nodes.r
Intermediate lumbar nodes
Interpectoral nodes.l
Interpectoral nodes.r
Intraglandular parotid nodes.l
Intraglandular parotid nodes.r
Intrapulmonary nodes
Jugulodigastric node.l
Jugulodigastric node.r
Juxta-intestinal mesenteric nodes
Juxta-oesophageal nodes
Lateral aortic nodes
Lateral axillary nodes.l
Lateral axillary nodes.r
Lateral caval nodes
Lateral common iliac nodes
Lateral pericardial nodes
Lateral sacral nodes.l
Lateral sacral nodes.r
Lateral superior jugular node.l
Lateral superior jugular node.r
Lateral vesical nodes.l
Lateral vesical nodes.r
Left colic nodes
Mandibular node.l
Mandibular node.r
Mastoid nodes.l
Mastoid nodes.r
Medial common iliac nodes
Medial external iliac nodes.l
Medial external iliac nodes.r
Median sacral nodes
Middle colic nodes
Nasolabial node.l
Nasolabial node.r
Node of arch of azygos vein
Node of ligamentum arteriosum
Obturator nodes.l
Obturator nodes.r
Occipital nodes.l
Occipital nodes.r
Palatine tonsil.l
Palatine tonsil.r
Paracolic superior mesenteric nodes
Pararectal nodes
Parasternal nodes.l
Parasternal nodes.r
Paratracheal cervical nodes
Paratracheal thoracic nodes
Posterior axillary nodes.l
Posterior axillary nodes.r
Postvesical nodes
Pre-aortic nodes
Pre-auricular nodes.l
Pre-auricular nodes.r
Precaecal nodes
Precaval nodes
Prepericardial nodes
Pretracheal nodes.l
Pretracheal nodes.r
Prevertebral nodes
Prevesical nodes
Retro-aortic nodes
Retrocaecal nodes
Retrocaval nodes
Retropharyngeal nodes.l
Retropharyngeal nodes.r
Right colic nodes
Right gastric nodes
Right gastro-omental nodes
Sigmoid nodes
Splenic nodes
Subaortic nodes
Submandibular nodes.l
Submandibular nodes.r
Submental nodes.l
Submental nodes.r
Superficial anterior cervical nodes
Superficial lateral cervical nodes.l
Superficial lateral cervical nodes.r
Superficial parotid nodes.l
Superficial parotid nodes.r
Superficial popliteal nodes.l
Superficial popliteal nodes.r
Superior diaphragmatic nodes
Superior gluteal nodes.l
Superior gluteal nodes.r
Superior pancreatic nodes
Superior pancreaticoduodenal nodes
Superior tracheobronchial nodes
Superolateral superficial inguinal nodes.l
Superolateral superficial inguinal nodes.r
Superomedial superficial inguinal nodes.l
Superomedial superficial inguinal nodes.r
Supraclavicular nodes.l
Supraclavicular nodes.r
Supratrochlear nodes.l
Supratrochlear nodes.r
Thyroid nodes.l
Thyroid nodes.r
'''.strip().splitlines()

groups = {
    'nodes_head': ('Head and neck', 'Tête et cou'),
    'nodes_thorax': ('Thorax', 'Thorax'),
    'nodes_abdomen': ('Abdomen', 'Abdomen'),
    'nodes_pelvis': ('Pelvis', 'Pelvis'),
    'nodes_upper': ('Upper limbs', 'Membres supérieurs'),
    'nodes_lower': ('Lower limbs', 'Membres inférieurs'),
    'tonsils': ('Palatine tonsils', 'Tonsilles palatines'),
}
summaries = {
    'lymphatic_node': (
        'Lymphoid tissue that filters lymph and supports immune responses. A named source object may contain several lymph nodes. This layer preserves those groups; collecting vessels and ducts are absent from this source file.',
        'Tissu lymphoïde filtrant la lymphe et participant aux réponses immunitaires. Un objet source nommé peut contenir plusieurs ganglions lymphatiques. Cette couche conserve ces groupes ; les vaisseaux collecteurs et conduits sont absents du fichier source.'),
    'lymphatic_tonsil': (
        'Lymphoid tissue at the side of the oropharynx, contributing to immune surveillance near the entrance to the digestive and respiratory tracts. Other tonsillar regions are not separately modelled in this subset.',
        'Tissu lymphoïde situé sur le côté de l’oropharynx, contribuant à la surveillance immunitaire à l’entrée des voies digestive et respiratoire. Les autres territoires tonsillaires ne sont pas individualisés dans ce sous-ensemble.'),
}
references = [
    'https://github.com/LluisV/Z-Anatomy/blob/PC-Version/README.md',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/21-1-anatomy-of-the-lymphatic-and-immune-systems',
]


def _parts(name):
    assert name in _NAMES
    suffix = name[-2:] if name.endswith(('.l', '.r')) else ''
    base = (name[:-2] if suffix else name).strip('()')
    return base, suffix


def entries(concepts=None, partof=None):
    return [('ZAN_LYMPH_' + re.sub('[^a-z0-9]+', '_', name.lower()).strip('_'), name, [name], False, 0)
            for name in _NAMES]


def describe(name):
    base, suffix = _parts(name)
    french, region = TERMS[base]
    label = base.replace('Bucinator', 'Buccinator')
    if suffix:
        label = ('Left ' if suffix == '.l' else 'Right ') + label.lower()
        french += ' (gauche)' if suffix == '.l' else ' (droite)'
    tonsil = base == 'Palatine tonsil'
    return label, french, 'lymphatic_tonsil' if tonsil else 'lymphatic_node', region, \
        ('Palatine tonsil' if tonsil else 'Lymph node'), ('Tonsille palatine' if tonsil else 'Ganglion lymphatique')


def attributes(name):
    base, _ = _parts(name)
    region = TERMS[base][1]
    category = 'tonsils' if base == 'Palatine tonsil' else 'nodes_' + ('head' if region in ('skull', 'neck') else region)
    return {'category': category, 'color': [.38, .64, .27] if category != 'tonsils' else [.65, .36, .40]}


def load(source):
    path = source / 'Z-Anatomy-LymphoidOrgans100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SOURCE_SHA256
    registration = json.loads(REGISTRATION.read_text(encoding='utf-8'))
    result = {}
    for name, (vertices, faces) in meshes(path, _NAMES).items():
        fit = registration['transforms'][registration['objects'][name]]
        matrix, offset = np.array(fit['matrix']), np.array(fit['offset'])
        scale_squared = np.trace(matrix.T @ matrix) / 3
        assert np.allclose(matrix.T @ matrix, np.eye(3) * scale_squared, atol=1e-6)
        assert np.linalg.det(matrix) > 0
        vertices = vertices / 100 @ matrix + offset
        assert np.isfinite(vertices).all() and np.isfinite(faces).all()
        assert vertices[:, 1].min() > 0 and vertices[:, 1].max() < 1.8
        _, suffix = _parts(name)
        if suffix:
            assert (vertices[:, 0].mean() > 0) == (suffix == '.l'), name
        result[name] = (vertices.tolist(), faces.tolist())
    assert len(result) == 160
    return result


def provenance(source, selected):
    return {
        'source': 'Z-Anatomy — The libre 3D atlas of anatomy',
        'sourceUrl': SOURCE_URL, 'sha256': SOURCE_SHA256,
        'license': 'CC BY-SA 4.0', 'licenseUrl': LICENSE_URL,
        'attribution': 'Z-Anatomy / Gauthier Kervyn; BodyParts3D / The Database Center for Life Science (CC BY-SA 2.1 Japan); FBX export distributed by Lluís Vinent Juanico.',
        'structures': len(selected), 'lymphNodeGroups': 158, 'palatineTonsils': 2,
        'sourceObjects': list(_NAMES),
        'modifications': 'Static FBX hierarchy baked; centimetres to metres; each named object receives a uniform-scale rigid regional skeletal registration; original vertices and triangles retained without simplification or deformation; normals and illustrative material rebuilt.',
        'registration': json.loads(REGISTRATION.read_text(encoding='utf-8')),
        'excluded': 'Joined annotation meshes (.j), generic demonstration node, section planes, source spleen and thymic lobes. The atlas spleen and thymus already come from BodyParts3D.',
        'limitations': '158 named lymph-node groups, not a claim of 158 individual nodes or a complete lymphatic network. No collecting vessels, thoracic duct, right lymphatic duct or lymph flow is supplied by this FBX. Regional registration between different source bodies is approximate; local tissue contacts are not clinically validated.',
        'references': references,
    }
