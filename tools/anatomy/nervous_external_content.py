"""Z-Anatomy nerve surfaces absent from the direct BodyParts3D selection.

Exact mesh names are explicitly allowlisted. Empty organizational .j objects,
brain/white-matter reference models, eye and ear tissues are not imported.
"""
import hashlib
import json
import re
from pathlib import Path
import numpy as np
from fbx_geometry import meshes
from align_nervous import warp

SOURCE_URL = 'https://raw.githubusercontent.com/LluisV/Z-Anatomy/PC-Version/Resources/Models/FBX/NervousSystem100.fbx'
SOURCE_SHA256 = '3ea1aad64956cad27348a27b8fb50494b7cc307c6bc77bee0810ec2a67dff2b1'
LICENSE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/'
REGISTRATION = Path(__file__).with_name('nervous_registration.json')

# Exact bilateral base | French label | family | category | region.
# Side markers are expanded only for these hand-checked names, not arbitrary
# automatic translations. Unmarked right-side exceptions are listed below.
_PAIRED = '''
Olfactory nerve (I)|Nerf olfactif · I|olfactory|cranial_nerves|skull
Trigeminal nerve (V)|Nerf trijumeau · V|trigeminal|cranial_nerves|skull
Sensory root of trigeminal nerve|Racine sensitive du trijumeau|trigeminal|cranial_nerves|skull
Motor root of trigeminal nerve|Racine motrice du trijumeau|trigeminal|cranial_nerves|skull
Inferior alveolar nerve|Nerf alvéolaire inférieur|trigeminal|cranial_nerves|skull
Mental nerve|Nerf mentonnier|trigeminal|cranial_nerves|skull
Nerve to mylohyoid muscle|Nerf mylo-hyoïdien|trigeminal|cranial_nerves|skull
Buccal nerve|Nerf buccal|trigeminal|cranial_nerves|skull
Posterior division of mandibular nerve|Division postérieure du nerf mandibulaire|trigeminal|cranial_nerves|skull
Anterior division of mandibular nerve|Division antérieure du nerf mandibulaire|trigeminal|cranial_nerves|skull
Lingual nerve|Nerf lingual|trigeminal|cranial_nerves|skull
Maxillary nerve|Nerf maxillaire · V2|trigeminal|cranial_nerves|skull
Meningeal branch of maxillary nerve|Branche méningée du nerf maxillaire|trigeminal|cranial_nerves|skull
Chorda tympani|Corde du tympan|facial|cranial_nerves|skull
Facial nerve (VII)|Nerf facial · VII|facial|cranial_nerves|skull
Vestibulocochlear nerve (VIII)|Nerf vestibulocochléaire · VIII|vestibulocochlear|cranial_nerves|skull
Glossopharyngeal nerve (IX)|Nerf glossopharyngien · IX|glossopharyngeal|cranial_nerves|skull
Vagus nerve (X)|Nerf vague · X|vagus|cranial_nerves|neck
Accessory nerve (XI)|Nerf accessoire · XI|accessory|cranial_nerves|neck
Hypoglossal nerve (XII)|Nerf hypoglosse · XII|hypoglossal|cranial_nerves|skull
Abducens nerve (VI)|Nerf abducens · VI|abducens|cranial_nerves|skull
Superior trunk of brachial plexus|Tronc supérieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Anterior division of superior trunk of brachial plexus|Division antérieure du tronc supérieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Posterior division of superior trunk of brachial plexus|Division postérieure du tronc supérieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Middle trunk of brachial plexus|Tronc moyen du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Anterior division of middle trunk of brachial plexus|Division antérieure du tronc moyen du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Posterior division of middle trunk of brachial plexus|Division postérieure du tronc moyen du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Inferior trunk of brachial plexus|Tronc inférieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Anterior division of inferior trunk of brachial plexus|Division antérieure du tronc inférieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Posterior division of inferior trunk of brachial plexus|Division postérieure du tronc inférieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Roots of brachial plexus|Racines du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Posterior cord of brachial plexus|Faisceau postérieur du plexus brachial|brachial_plexus|upper_limb_nerves|upper
Dorsal scapular nerve|Nerf dorsal de la scapula|upper|upper_limb_nerves|upper
Long thoracic nerve|Nerf thoracique long|upper|upper_limb_nerves|thorax
Suprascapular nerve|Nerf supra-scapulaire|upper|upper_limb_nerves|upper
Subclavian nerve|Nerf du subclavier|upper|upper_limb_nerves|upper
Axillary nerve|Nerf axillaire|upper|upper_limb_nerves|upper
Lateral pectoral nerve|Nerf pectoral latéral|upper|upper_limb_nerves|thorax
Medial pectoral nerve|Nerf pectoral médial|upper|upper_limb_nerves|thorax
Superior subscapular nerve|Nerf subscapulaire supérieur|upper|upper_limb_nerves|upper
Inferior subscapular nerve|Nerf subscapulaire inférieur|upper|upper_limb_nerves|upper
Thoracodorsal nerve|Nerf thoraco-dorsal|upper|upper_limb_nerves|thorax
Musculocutaneous nerve|Nerf musculocutané|upper|upper_limb_nerves|upper
Lateral antebrachial cutaneous nerve|Nerf cutané latéral de l’avant-bras|cutaneous|upper_limb_nerves|upper
Radial nerve|Nerf radial|upper|upper_limb_nerves|upper
Deep branch of radial nerve|Branche profonde du nerf radial|upper|upper_limb_nerves|upper
Posterior interosseous nerve of forearm|Nerf interosseux postérieur de l’avant-bras|upper|upper_limb_nerves|upper
Superficial branch of radial nerve|Branche superficielle du nerf radial|cutaneous|upper_limb_nerves|upper
Dorsal digital branches of radial nerve|Branches digitales dorsales du nerf radial|cutaneous|upper_limb_nerves|hands
Posterior antebrachial cutaneous nerve|Nerf cutané postérieur de l’avant-bras|cutaneous|upper_limb_nerves|upper
Inferior lateral brachial cutaneous nerve|Nerf cutané latéral inférieur du bras|cutaneous|upper_limb_nerves|upper
Muscular branches of radial nerve|Branches musculaires du nerf radial|upper|upper_limb_nerves|upper
Superior lateral brachial cutaneous nerve|Nerf cutané latéral supérieur du bras|cutaneous|upper_limb_nerves|upper
Muscular branches of axillary nerve|Branches musculaires du nerf axillaire|upper|upper_limb_nerves|upper
Anterior branch of medial antebrachial cutaneous nerve|Branche antérieure du nerf cutané médial de l’avant-bras|cutaneous|upper_limb_nerves|upper
Posterior branch of medial antebrachial cutaneous nerve|Branche postérieure du nerf cutané médial de l’avant-bras|cutaneous|upper_limb_nerves|upper
Medial antebrachial cutaneous nerve|Nerf cutané médial de l’avant-bras|cutaneous|upper_limb_nerves|upper
Ulnar nerve|Nerf ulnaire|upper|upper_limb_nerves|upper
Dorsal digital branches of ulnar nerve|Branches digitales dorsales du nerf ulnaire|cutaneous|upper_limb_nerves|hands
Dorsal branch of ulnar nerve|Branche dorsale du nerf ulnaire|cutaneous|upper_limb_nerves|hands
Proper palmar digital branches of ulnar nerve|Branches digitales palmaires propres du nerf ulnaire|cutaneous|upper_limb_nerves|hands
Common palmar digital branches of ulnar nerve|Branches digitales palmaires communes du nerf ulnaire|cutaneous|upper_limb_nerves|hands
Superficial branch of ulnar nerve|Branche superficielle du nerf ulnaire|upper|upper_limb_nerves|hands
Palmar branch of ulnar nerve|Branche palmaire du nerf ulnaire|cutaneous|upper_limb_nerves|hands
Deep branch of ulnar nerve|Branche profonde du nerf ulnaire|upper|upper_limb_nerves|hands
Muscular branches of ulnar nerve|Branches musculaires du nerf ulnaire|upper|upper_limb_nerves|upper
Medial brachial cutaneous nerve|Nerf cutané médial du bras|cutaneous|upper_limb_nerves|upper
Median nerve|Nerf médian|upper|upper_limb_nerves|upper
Proper palmar digital branches of median nerve|Branches digitales palmaires propres du nerf médian|cutaneous|upper_limb_nerves|hands
Common palmar digital branches of median nerve|Branches digitales palmaires communes du nerf médian|cutaneous|upper_limb_nerves|hands
(Communicating branch of median nerve with ulnar nerve)|Branche communicante entre nerfs médian et ulnaire|upper|upper_limb_nerves|hands
Anterior interosseous nerve of forearm|Nerf interosseux antérieur de l’avant-bras|upper|upper_limb_nerves|upper
Palmar branch of median nerve|Branche palmaire du nerf médian|cutaneous|upper_limb_nerves|hands
Muscular branches of median nerve|Branches musculaires du nerf médian|upper|upper_limb_nerves|upper
Intercostal nerves|Nerfs intercostaux|intercostal|trunk_nerves|thorax
Iliohypogastric nerve|Nerf ilio-hypogastrique|trunk|trunk_nerves|abdomen
Ilio-inguinal nerve|Nerf ilio-inguinal|trunk|trunk_nerves|abdomen
Infrapatellar branch of saphenous nerve|Branche infrapatellaire du nerf saphène|cutaneous|lower_limb_nerves|lower
Saphenous nerve|Nerf saphène|cutaneous|lower_limb_nerves|lower
Medial crural cutaneous branches of saphenous nerve|Branches cutanées médiales de la jambe du nerf saphène|cutaneous|lower_limb_nerves|lower
Sural nerve|Nerf sural|cutaneous|lower_limb_nerves|lower
Sural communicating branch of common fibular nerve|Branche communicante surale du nerf fibulaire commun|cutaneous|lower_limb_nerves|lower
Medial sural cutaneous nerve|Nerf cutané sural médial|cutaneous|lower_limb_nerves|lower
Anterior cutaneous branches of femoral nerve|Branches cutanées antérieures du nerf fémoral|cutaneous|lower_limb_nerves|lower
Femoral nerve|Nerf fémoral|lower|lower_limb_nerves|lower
Lateral femoral cutaneous nerve|Nerf cutané latéral de la cuisse|cutaneous|lower_limb_nerves|lower
Obturator nerve|Nerf obturateur|lower|lower_limb_nerves|pelvis
Posterior branch of obturator nerve|Branche postérieure du nerf obturateur|lower|lower_limb_nerves|lower
Anterior branch of obturator nerve|Branche antérieure du nerf obturateur|lower|lower_limb_nerves|lower
Genitofemoral nerve|Nerf génitofémoral|genitofemoral|trunk_nerves|pelvis
Femoral branch of genitofemoral nerve|Branche fémorale du nerf génitofémoral|genitofemoral|trunk_nerves|pelvis
Genital branch of genitofemoral nerve|Branche génitale du nerf génitofémoral|genitofemoral|trunk_nerves|pelvis
Superficial fibular nerve|Nerf fibulaire superficiel|lower|lower_limb_nerves|lower
Dorsal digital branches of superficial fibular nerve|Branches digitales dorsales du nerf fibulaire superficiel|cutaneous|lower_limb_nerves|feet
Medial dorsal cutaneous nerve of foot|Nerf cutané dorsal médial du pied|cutaneous|lower_limb_nerves|feet
Intermediate dorsal cutaneous nerve of foot|Nerf cutané dorsal intermédiaire du pied|cutaneous|lower_limb_nerves|feet
Deep fibular nerve|Nerf fibulaire profond|lower|lower_limb_nerves|lower
Dorsal digital branches of deep fibular nerve|Branches digitales dorsales du nerf fibulaire profond|cutaneous|lower_limb_nerves|feet
Muscular branches of deep fibular nerve|Branches musculaires du nerf fibulaire profond|lower|lower_limb_nerves|feet
Sciatic nerve|Nerf sciatique|lower|lower_limb_nerves|lower
Tibial nerve|Nerf tibial|lower|lower_limb_nerves|lower
Proper plantar digital branches of medial plantar nerve|Branches digitales plantaires propres du nerf plantaire médial|cutaneous|lower_limb_nerves|feet
Common plantar digital branches of lateral plantar nerve|Branches digitales plantaires communes du nerf plantaire latéral|cutaneous|lower_limb_nerves|feet
Lateral plantar nerve|Nerf plantaire latéral|lower|lower_limb_nerves|feet
Medial plantar nerve|Nerf plantaire médial|lower|lower_limb_nerves|feet
Common fibular nerve|Nerf fibulaire commun|lower|lower_limb_nerves|lower
Proper plantar digital branches of lateral plantar nerve|Branches digitales plantaires propres du nerf plantaire latéral|cutaneous|lower_limb_nerves|feet
Common plantar digital branches of medial plantar nerve|Branches digitales plantaires communes du nerf plantaire médial|cutaneous|lower_limb_nerves|feet
Superior gluteal nerve|Nerf glutéal supérieur|lower|lower_limb_nerves|pelvis
Nerve to piriformis muscle|Nerf du piriforme|lower|lower_limb_nerves|pelvis
Nerve to quadratus femoris muscle|Nerf du carré fémoral|lower|lower_limb_nerves|pelvis
Posterior femoral cutaneous nerve|Nerf cutané postérieur de la cuisse|cutaneous|lower_limb_nerves|lower
Anterior root of posterior femoral cutaneous nerve|Racine antérieure du nerf cutané postérieur de la cuisse|cutaneous|lower_limb_nerves|pelvis
Posterior root of posterior femoral cutaneous nerve|Racine postérieure du nerf cutané postérieur de la cuisse|cutaneous|lower_limb_nerves|pelvis
Pudendal nerve|Nerf pudendal|pudendal|trunk_nerves|pelvis
Sympathetic nerves|Nerfs sympathiques|autonomic|autonomic|thorax
Ganglia of sympathetic trunk|Ganglions du tronc sympathique|autonomic|autonomic|thorax
Sympathetic trunk|Tronc sympathique|autonomic|autonomic|thorax
Spinal ganglion|Ganglions spinaux|spinal_roots|spinal_cord|spine
Posterior root of spinal nerve|Racines postérieures des nerfs spinaux|spinal_roots|spinal_cord|spine
Anterior root of spinal nerve|Racines antérieures des nerfs spinaux|spinal_roots|spinal_cord|spine
'''
TERMS = {}
for row in _PAIRED.strip().splitlines():
    base, french, family, category, region = row.split('|')
    for suffix, en_side, fr_side in [('l', 'Left', 'gauche'), ('r', 'Right', 'droit')]:
        name = base + '.' + suffix
        if name == 'Common plantar digital branches of medial plantar nerve.r':
            name = base  # Source mesh lacks its right-side suffix; coordinates checked.
        TERMS[name] = (f'{en_side} {base[0].lower() + base[1:]}', f'{french} · côté {fr_side}', family, category, region)
TERMS.update({
    'White matter of spinal cord': ('Spinal cord · white matter', 'Moelle spinale · substance blanche', 'spinal_white', 'spinal_cord', 'spine'),
    'Anterior horn of spinal cord': ('Spinal cord · anterior horns', 'Moelle spinale · cornes antérieures', 'spinal_gray', 'spinal_cord', 'spine'),
    'Posterior horn of spinal cord': ('Spinal cord · posterior horns', 'Moelle spinale · cornes postérieures', 'spinal_gray', 'spinal_cord', 'spine'),
    'Cauda equina': ('Cauda equina', 'Queue de cheval', 'cauda', 'spinal_cord', 'spine'),
    'Falx cerebri': ('Falx cerebri', 'Faux du cerveau', 'falx', 'meninges', 'skull'),
})
groups = {
    'spinal_cord': ('Spinal cord and roots', 'Moelle spinale et racines'),
    'upper_limb_nerves': ('Upper limb nerves', 'Nerfs du membre supérieur'),
    'lower_limb_nerves': ('Lower limb nerves', 'Nerfs du membre inférieur'),
    'trunk_nerves': ('Trunk and pelvic nerves', 'Nerfs du tronc et du pelvis'),
    'autonomic': ('Autonomic nerves', 'Nerfs autonomes'),
}
_SUMMARIES = {
    'olfactory': ('Cranial nerve I conveys olfactory information from the nasal region. The source includes the proximal olfactory structures as a surface model.', 'Le nerf crânien I transmet les informations olfactives de la région nasale. La source inclut les structures olfactives proximales dans un modèle de surface.'),
    'trigeminal': ('Component of cranial nerve V, the principal sensory nerve of the face. Its mandibular division also carries motor fibres to muscles of mastication.', 'Composant du nerf crânien V, principal nerf sensitif de la face. Sa division mandibulaire transporte aussi des fibres motrices vers les muscles masticateurs.'),
    'facial': ('Component of cranial nerve VII, involved in facial movement, taste and parasympathetic functions according to the branch.', 'Composant du nerf crânien VII, participant aux mouvements faciaux, au goût et à des fonctions parasympathiques selon la branche.'),
    'vestibulocochlear': ('Cranial nerve VIII conveys auditory and balance information from the inner ear. Fine sensory endings are not included.', 'Le nerf crânien VIII transmet les informations auditives et d’équilibre de l’oreille interne. Les terminaisons sensorielles fines ne sont pas incluses.'),
    'glossopharyngeal': ('Cranial nerve IX has sensory, motor and parasympathetic components serving the pharynx, posterior tongue and related structures.', 'Le nerf crânien IX comporte des composantes sensitives, motrices et parasympathiques desservant le pharynx, la langue postérieure et des structures associées.'),
    'vagus': ('Cranial nerve X connects the brainstem with structures of the neck, thorax and abdomen. The two source meshes have unequal distal coverage.', 'Le nerf crânien X relie le tronc cérébral à des structures du cou, du thorax et de l’abdomen. Les deux maillages sources n’ont pas la même étendue distale.'),
    'accessory': ('Cranial nerve XI contributes to motor supply of sternocleidomastoid and trapezius.', 'Le nerf crânien XI participe à l’innervation motrice du sternocléidomastoïdien et du trapèze.'),
    'hypoglossal': ('Cranial nerve XII supplies most tongue muscles and contributes to tongue movement.', 'Le nerf crânien XII innerve la plupart des muscles de la langue et contribue à ses mouvements.'),
    'abducens': ('Cranial nerve VI supplies the lateral rectus muscle, which turns the eye outward.', 'Le nerf crânien VI innerve le muscle droit latéral, qui tourne l’œil vers l’extérieur.'),
    'brachial_plexus': ('Part of the nerve network supplying the upper limb, formed mainly from the anterior rami C5 to T1.', 'Partie du réseau nerveux du membre supérieur, formé principalement par les rameaux antérieurs de C5 à T1.'),
    'upper': ('Named peripheral nerve or branch of the upper limb. Motor, sensory and autonomic fibres vary by branch; microscopic terminals are not resolved.', 'Nerf périphérique ou branche du membre supérieur. Les fibres motrices, sensitives et autonomes varient selon la branche ; les terminaisons microscopiques ne sont pas représentées.'),
    'lower': ('Named peripheral nerve or branch of the lower limb, carrying signals between lumbosacral networks and its peripheral territory.', 'Nerf périphérique ou branche du membre inférieur, transmettant des signaux entre les réseaux lombo-sacrés et son territoire périphérique.'),
    'cutaneous': ('Cutaneous nerve or branch carrying sensory information from part of the skin. The surface model shows its course, not exact sensory territory boundaries.', 'Nerf cutané ou branche transmettant des informations sensitives d’une partie de la peau. Le modèle montre son trajet, pas les limites exactes de son territoire sensitif.'),
    'intercostal': ('Thoracic nerve branches following the intercostal spaces and supplying structures of the trunk wall. Multiple levels are grouped per side.', 'Branches nerveuses thoraciques suivant les espaces intercostaux et desservant la paroi du tronc. Plusieurs niveaux sont regroupés par côté.'),
    'trunk': ('Lumbar plexus branch supplying parts of the lower abdominal wall and neighbouring regions.', 'Branche du plexus lombaire desservant des parties de la paroi abdominale inférieure et des régions voisines.'),
    'genitofemoral': ('Lumbar plexus nerve dividing into genital and femoral branches, serving portions of the groin and upper thigh.', 'Nerf du plexus lombaire se divisant en branches génitale et fémorale, desservant une partie de l’aine et de la cuisse supérieure.'),
    'pudendal': ('Sacral plexus nerve providing somatic sensory and motor supply to the perineum. It is distinct from the pelvic autonomic nerves.', 'Nerf du plexus sacré assurant une innervation somatique sensitive et motrice du périnée. Il est distinct des nerfs autonomes pelviens.'),
    'autonomic': ('Component of the sympathetic pathway alongside the spine. The imported surfaces represent selected trunks, ganglia and connections, not the entire autonomic network.', 'Composant de la voie sympathique longeant la colonne. Les surfaces importées représentent certains troncs, ganglions et connexions, pas tout le réseau autonome.'),
    'spinal_roots': ('Roots connect the spinal cord with spinal nerves; posterior roots carry sensory input and anterior roots motor output. Sensory cell bodies lie in spinal ganglia. Several levels are grouped.', 'Les racines relient la moelle aux nerfs spinaux ; les racines postérieures conduisent les informations sensitives et les antérieures la sortie motrice. Les corps cellulaires sensitifs siègent dans les ganglions spinaux. Plusieurs niveaux sont regroupés.'),
    'spinal_white': ('Outer spinal white matter carries ascending and descending pathways. This surface surrounds separately selectable grey horns; individual tracts are not modelled as physiological connections.', 'La substance blanche périphérique de la moelle conduit des voies ascendantes et descendantes. Cette surface entoure des cornes grises sélectionnables séparément ; les faisceaux ne sont pas modélisés comme connexions physiologiques individuelles.'),
    'spinal_gray': ('Longitudinal representation of a pair of spinal grey horns. Posterior horns process sensory input; anterior horns include motor neurons. Segment-specific cellular detail is not shown.', 'Représentation longitudinale d’une paire de cornes grises médullaires. Les cornes postérieures traitent les informations sensitives ; les antérieures contiennent des motoneurones. Les détails cellulaires propres à chaque segment ne sont pas représentés.'),
    'cauda': ('Bundle of lumbar, sacral and coccygeal nerve roots descending below the end of the spinal cord.', 'Faisceau de racines nerveuses lombaires, sacrées et coccygiennes descendant sous l’extrémité de la moelle spinale.'),
    'falx': ('Midline fold of dura mater between the cerebral hemispheres. It supports and separates them without being part of the brain tissue.', 'Repli médian de dure-mère entre les hémisphères cérébraux. Il les soutient et les sépare sans appartenir au tissu cérébral.'),
}
summaries = {'nervous_z_' + k: v for k, v in _SUMMARIES.items()}


def entries():
    return [('ZAN_nervous_' + re.sub(r'[^a-z0-9]+', '_', name.lower()).strip('_'), name, [name], False, 0) for name in TERMS]


def describe(name):
    english, french, family, _, region = TERMS[name]
    return english, french, 'nervous_z_' + family, region, english, french.split(' · ')[0]


def attributes(name):
    _, _, family, category, _ = TERMS[name]
    color = [.95, .83, .34]
    if family == 'spinal_white': color = [.88, .83, .70]
    elif family == 'spinal_gray': color = [.66, .54, .57]
    elif family == 'falx': color = [.78, .81, .80]
    elif family == 'autonomic': color = [.87, .65, .32]
    return {'category': category, 'color': color}


def load(source):
    path = source / 'Z-Anatomy-NervousSystem100.fbx'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == SOURCE_SHA256
    registration = json.loads(REGISTRATION.read_text())
    result = meshes(path, list(TERMS))
    assert result.keys() == TERMS.keys()
    for name, (vertices, faces) in result.items():
        v = warp(vertices / 100, registration)
        assert np.isfinite(v).all() and -.25 < v[:, 1].min() < v[:, 1].max() < 1.8, name
        side = TERMS[name][0].split()[0]
        if side in ('Left', 'Right'):
            # Distal vagal branches can cross the midline; side names refer to
            # the nerve's origin, not the centroid of its visceral branches.
            check = v[v[:, 1] > v[:, 1].max() - .04] if name.startswith('Vagus nerve') else v
            assert (check[:, 0].mean() > 0) == (side == 'Left'), name
        result[name] = (v.tolist(), faces.tolist())
    return result


def provenance(source, selected):
    return {
        'source': 'Z-Anatomy', 'sourceUrl': SOURCE_URL, 'sourceSha256': SOURCE_SHA256,
        'license': 'CC BY-SA 4.0', 'licenseUrl': LICENSE_URL, 'structures': len(selected),
        'registration': json.loads(REGISTRATION.read_text()),
        'attribution': 'Z-Anatomy / Gauthier Kervyn, based on BodyParts3D / DBCLS; cranial nerves reference: University of Dundee, CAHID, CC BY 4.0 as declared in the upstream model license.',
        'modifications': 'Selected named nerve meshes, static FBX triangulation, centimetres to metres, one continuous bone-guided pose registration, recomputed smooth normals, illustrative colours. No new branches, no independent branch repositioning.',
        'registrationValidation': {'jacobianSamples': 38648, 'minimumJacobianDeterminant': .7891640907587109,
            'sampledLocalScaleRange': [.7484508209579434, 1.2322497152515166],
            'sampledLocalScaleP01P99': [.9039720503465489, 1.0945617770393734],
            'checkedSurfaceJunctions': 6,
            'interpretation': 'Approximate geometry checks, not clinical validation or exact calibre preservation. Topology and common coordinates remain unchanged; the smooth pose adjustment can alter local dimensions.'},
        'limitations': 'Approximate registration between different source bodies, not verified foraminal or surgical anatomy. Multiple spinal levels are grouped, and source distal coverage is incomplete. Z-Anatomy optic/trochlear/ophthalmic nerves and full oculomotor nerves are excluded to avoid overlap with the direct BodyParts3D orbit. Cranial III is represented by its BodyParts3D orbital branches only. The non-triangulable Spinal dura mesh is excluded. Brain, telencephalic white matter, labyrinth/ear and eye models are excluded from this addition.',
    }
