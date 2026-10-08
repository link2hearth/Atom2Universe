"""Muscular subset of BodyParts3D 4.0, with bilingual educational nomenclature.

The source distinguishes organs, heads and zones: all three classes are required.
No absent muscle is fabricated. Short descriptions are original family-level
summaries; they do not assert individual origins, insertions or innervation.
"""
import re

# English source term | French name | primary display region | educational family
_TERMS = """
abductor digiti minimi of foot|Abducteur du petit orteil|feet|foot
abductor digiti minimi of hand|Abducteur du petit doigt|hands|hand
abductor hallucis|Abducteur de l’hallux|feet|foot
abductor pollicis brevis|Court abducteur du pouce|hands|thumb
abductor pollicis longus|Long abducteur du pouce|upper|thumb
adductor brevis|Court adducteur|lower|adductor
adductor longus|Long adducteur|lower|adductor
adductor magnus|Grand adducteur|lower|adductor
adductor minimus|Petit adducteur|lower|adductor
adductor hallucis|Adducteur de l’hallux|feet|foot
adductor pollicis|Adducteur du pouce|hands|thumb
anconeus|Anconé|upper|elbow_extension
aryepiglotticus|Ary-épiglottique|neck|larynx
biceps brachii|Biceps brachial|upper|biceps
biceps femoris|Biceps fémoral|lower|hamstring
brachialis|Brachial|upper|elbow_flexion
brachioradialis|Brachio-radial|upper|elbow_flexion
cervical rotator|Rotateurs cervicaux|spine|back
coccygeus|Coccygien|pelvis|pelvic_floor
coracobrachialis|Coraco-brachial|upper|shoulder
cricothyroid|Crico-thyroïdien|neck|larynx
deltoid|Deltoïde|upper|shoulder
diaphragm|Diaphragme|thorax|diaphragm
digastric|Digastrique|neck|suprahyoid
extensor carpi radialis brevis|Court extenseur radial du carpe|upper|wrist_extension
extensor carpi radialis longus|Long extenseur radial du carpe|upper|wrist_extension
extensor carpi ulnaris|Extenseur ulnaire du carpe|upper|wrist_extension
extensor digiti minimi|Extenseur du petit doigt|upper|finger_extension
extensor digitorum|Extenseur des doigts|upper|finger_extension
extensor digitorum longus|Long extenseur des orteils|lower|toe_extension
extensor hallucis brevis|Court extenseur de l’hallux|feet|toe_extension
extensor hallucis longus|Long extenseur de l’hallux|lower|toe_extension
extensor indicis|Extenseur de l’index|upper|finger_extension
extensor pollicis brevis|Court extenseur du pouce|upper|thumb
extensor pollicis longus|Long extenseur du pouce|upper|thumb
external oblique|Oblique externe de l’abdomen|abdomen|abdominal
external anal sphincter|Sphincter externe de l’anus|pelvis|sphincter
external intercostal muscle|Intercostaux externes|thorax|intercostal
internal intercostal muscle|Intercostaux internes|thorax|intercostal
innermost intercostal muscle|Intercostaux intimes|thorax|intercostal
fibularis brevis|Court fibulaire|lower|fibular
fibularis longus|Long fibulaire|lower|fibular
fibularis tertius|Troisième fibulaire|lower|fibular
flexor accessorius|Carré plantaire|feet|foot
flexor carpi radialis|Fléchisseur radial du carpe|upper|wrist_flexion
flexor carpi ulnaris|Fléchisseur ulnaire du carpe|upper|wrist_flexion
flexor digiti minimi brevis of foot|Court fléchisseur du petit orteil|feet|foot
flexor digiti minimi brevis of hand|Court fléchisseur du petit doigt|hands|hand
flexor digitorum brevis|Court fléchisseur des orteils|feet|toe_flexion
flexor digitorum longus|Long fléchisseur des orteils|lower|toe_flexion
flexor digitorum profundus|Fléchisseur profond des doigts|upper|finger_flexion
flexor digitorum superficialis|Fléchisseur superficiel des doigts|upper|finger_flexion
flexor hallucis brevis|Court fléchisseur de l’hallux|feet|toe_flexion
flexor hallucis longus|Long fléchisseur de l’hallux|lower|toe_flexion
flexor pollicis brevis|Court fléchisseur du pouce|hands|thumb
flexor pollicis longus|Long fléchisseur du pouce|upper|thumb
gastrocnemius|Gastrocnémien|lower|calf
gemellus inferior|Jumeau inférieur|pelvis|hip_rotation
gemellus superior|Jumeau supérieur|pelvis|hip_rotation
genioglossus|Génio-glosse|skull|tongue
geniohyoid|Génio-hyoïdien|neck|suprahyoid
gluteus maximus|Grand glutéal (grand fessier)|pelvis|gluteal
gluteus medius|Moyen glutéal (moyen fessier)|pelvis|gluteal
gluteus minimus|Petit glutéal (petit fessier)|pelvis|gluteal
gracilis|Gracile|lower|adductor
hyoglossus|Hyo-glosse|skull|tongue
iliacus|Iliaque|pelvis|hip_flexion
iliococcygeus|Ilio-coccygien|pelvis|pelvic_floor
iliocostalis cervicis|Ilio-costal du cou|spine|back
iliocostalis lumborum|Ilio-costal des lombes|spine|back
iliocostalis thoracis|Ilio-costal du thorax|spine|back
inferior oblique|Oblique inférieur de l’œil|skull|eye
inferior pharyngeal constrictor|Constricteur inférieur du pharynx|neck|pharynx
inferior rectus|Droit inférieur de l’œil|skull|eye
infraspinatus muscle|Infra-épineux|upper|rotator_cuff
interspinalis thoracis|Interépineux thoraciques|spine|back
lateral crico-arytenoid|Crico-aryténoïdien latéral|neck|larynx
lateral lumbar intertransversarius|Intertransversaires lombaires latéraux|spine|back
lateral rectus|Droit latéral de l’œil|skull|eye
levator palpebrae superioris|Releveur de la paupière supérieure|skull|eyelid
levator scapulae|Élévateur de la scapula|upper|scapula
levator veli palatini|Élévateur du voile du palais|skull|palate
longissimus capitis|Longissimus de la tête|spine|back
longissimus cervicis|Longissimus du cou|spine|back
longissimus thoracis|Longissimus du thorax|spine|back
longus capitis|Long de la tête|neck|neck
longus colli|Long du cou|neck|neck
lumbar rotator|Rotateurs lombaires|spine|back
medial lumbar intertransversarius|Intertransversaires lombaires médiaux|spine|back
medial rectus|Droit médial de l’œil|skull|eye
middle pharyngeal constrictor|Constricteur moyen du pharynx|neck|pharynx
mylohyoid|Mylo-hyoïdien|neck|suprahyoid
oblique arytenoid|Aryténoïdien oblique|neck|larynx
obliquus capitis inferior|Oblique inférieur de la tête|neck|neck
obliquus capitis superior|Oblique supérieur de la tête|neck|neck
obturator externus|Obturateur externe|pelvis|hip_rotation
obturator internus|Obturateur interne|pelvis|hip_rotation
omohyoid|Omo-hyoïdien|neck|infrahyoid
opponens digiti minimi of foot|Opposant du petit orteil|feet|foot
opponens digiti minimi of hand|Opposant du petit doigt|hands|hand
opponens pollicis|Opposant du pouce|hands|thumb
palatopharyngeus|Palato-pharyngien|neck|pharynx
palmaris longus|Long palmaire|upper|wrist_flexion
pectineus|Pectiné|lower|adductor
pectoralis major|Grand pectoral|upper|shoulder
pectoralis minor|Petit pectoral|upper|scapula
piriformis|Piriforme|pelvis|hip_rotation
plantaris|Plantaire|lower|calf
platysma|Platysma|neck|platysma
popliteus|Poplité|lower|popliteus
posterior crico-arytenoid|Crico-aryténoïdien postérieur|neck|larynx
pronator quadratus|Carré pronateur|upper|pronation
pronator teres|Rond pronateur|upper|pronation
psoas major|Grand psoas|pelvis|hip_flexion
pubococcygeus|Pubo-coccygien|pelvis|pelvic_floor
puborectalis|Pubo-rectal|pelvis|pelvic_floor
quadratus femoris|Carré fémoral|pelvis|hip_rotation
rectus capitis anterior|Droit antérieur de la tête|neck|neck
rectus capitis lateralis|Droit latéral de la tête|neck|neck
rectus capitis posterior major|Grand droit postérieur de la tête|neck|neck
rectus capitis posterior minor|Petit droit postérieur de la tête|neck|neck
rectus femoris|Droit fémoral|lower|quadriceps
rhomboid major|Grand rhomboïde|upper|scapula
rhomboid minor|Petit rhomboïde|upper|scapula
salpingopharyngeus|Salpingo-pharyngien|neck|pharynx
sartorius|Sartorius (couturier)|lower|sartorius
scalenus anterior|Scalène antérieur|neck|scalene
scalenus medius|Scalène moyen|neck|scalene
scalenus posterior|Scalène postérieur|neck|scalene
semimembranosus|Semi-membraneux|lower|hamstring
semispinalis capitis|Semi-épineux de la tête|spine|back
semispinalis cervicis|Semi-épineux du cou|spine|back
semispinalis thoracis|Semi-épineux du thorax|spine|back
semitendinosus|Semi-tendineux|lower|hamstring
serratus anterior|Dentelé antérieur|upper|scapula
serratus posterior inferior|Dentelé postérieur inférieur|thorax|thorax
serratus posterior superior|Dentelé postérieur supérieur|thorax|thorax
soleus|Soléaire|lower|calf
spinalis|Épineux|spine|back
spinalis thoracis|Épineux du thorax|spine|back
splenius capitis|Splénius de la tête|neck|neck
splenius cervicis|Splénius du cou|neck|neck
sternocleidomastoid|Sterno-cléido-mastoïdien|neck|neck
sternohyoid|Sterno-hyoïdien|neck|infrahyoid
sternothyroid|Sterno-thyroïdien|neck|infrahyoid
stylohyoid|Stylo-hyoïdien|neck|suprahyoid
stylopharyngeus|Stylo-pharyngien|neck|pharynx
subclavius|Subclavier|upper|scapula
subscapularis|Subscapulaire|upper|rotator_cuff
superior oblique|Oblique supérieur de l’œil|skull|eye
superior pharyngeal constrictor|Constricteur supérieur du pharynx|neck|pharynx
superior rectus|Droit supérieur de l’œil|skull|eye
supinator|Supinateur|upper|supination
supraspinatus|Supra-épineux|upper|rotator_cuff
tensor fasciae latae|Tenseur du fascia lata|lower|tensor
tensor veli palatini|Tenseur du voile du palais|skull|palate
teres major|Grand rond|upper|shoulder
teres minor|Petit rond|upper|rotator_cuff
thoracic rotator|Rotateurs thoraciques|spine|back
thyro-arytenoid|Thyro-aryténoïdien|neck|larynx
thyrohyoid|Thyro-hyoïdien|neck|infrahyoid
tibialis anterior|Tibial antérieur|lower|tibialis_anterior
tibialis posterior|Tibial postérieur|lower|tibialis_posterior
transverse arytenoid|Aryténoïdien transverse|neck|larynx
trapezius|Trapèze|upper|scapula
transversus thoracis|Transverse du thorax|thorax|thorax
triceps brachii|Triceps brachial|upper|elbow_extension
uvular muscle|Muscle de la luette|skull|palate
vastus intermedius|Vaste intermédiaire|lower|quadriceps
vastus lateralis|Vaste latéral|lower|quadriceps
vastus medialis|Vaste médial|lower|quadriceps
vocalis|Vocal|neck|larynx
"""
TERMS = {row.split('|')[0]: tuple(row.split('|')[1:]) for row in _TERMS.strip().splitlines()}

PARTS = {
    'abdominal part': 'portion abdominale', 'acromial part': 'portion acromiale',
    'clavicular part': 'portion claviculaire', 'spinal part': 'portion spinale',
    'sternocostal part': 'portion sterno-costale', 'transverse part': 'portion transverse',
    'ascending part': 'portion ascendante', 'descending part': 'portion descendante',
    'straight part': 'portion droite', 'oblique part': 'portion oblique',
    'superior oblique part': 'portion oblique supérieure', 'inferior oblique part': 'portion oblique inférieure',
    'vertical intermediate part': 'portion verticale intermédiaire',
    'humeral head': 'chef huméral', 'ulnar head': 'chef ulnaire', 'long head': 'chef long',
    'short head': 'chef court', 'medial head': 'chef médial', 'lateral head': 'chef latéral',
    'oblique head': 'chef oblique', 'transverse head': 'chef transverse', 'superficial head': 'chef superficiel',
}

# The smallest unambiguous concepts for meshes that have no singleton FMA label.
# Spinalis includes two already labelled thoracic parts; consume them only once.
GROUPS = ('FMA46444', 'FMA21930', 'FMA9756', 'FMA9757', 'FMA9758', 'FMA45859',
          'FMA45855', 'FMA45857', 'FMA38507', 'FMA38508', 'FMA38470', 'FMA38471',
          'FMA23083', 'FMA77179', 'FMA22850', 'FMA22851', 'FMA46292', 'FMA46293',
          'FMA46589', 'FMA46590')

# These four source concepts are classified as generic organ zones, not muscle zones.
EXTRA_CONCEPTS = ('FMA33581', 'FMA33583', 'FMA33586', 'FMA33587')

# Two pairs in isa_element_parts.txt disagree with the actual geometry's side.
# Source X > 0 is anatomical left (also verified against femora and the other muscles).
# Swap element associations, never geometry or FMA concept names; record this in provenance.
LATERALITY_CORRECTIONS = {
    'FMA37388': ['FJ1469'], 'FMA37389': ['FJ1469M'],
    'FMA46633': ['FJ2754'], 'FMA46634': ['FJ2742'],
}


def entries(concepts):
    source_files = {r['element file id'] for key in ('FMA5022', 'FMA85453', 'FMA10474', *EXTRA_CONCEPTS) for r in concepts[key]}
    grouped_files = {r['element file id'] for key in GROUPS for r in concepts[key]}
    singles = {}
    for rows in concepts.values():
        if len(rows) == 1:
            row = rows[0]
            file = row['element file id']
            if file in singles:
                # The source lists both a generic and a lateralized label for three neck parts.
                assert file in ('FJ1600', 'FJ1557', 'FJ1601') or file not in source_files, file
            singles[file] = row
    result = [(singles[f]['concept id'], singles[f]['name'], [f], False, 0) for f in sorted(source_files - grouped_files)]
    result += [(key, concepts[key][0]['name'], [r['element file id'] for r in concepts[key]], False, 0) for key in GROUPS]
    result = [(key, name, LATERALITY_CORRECTIONS.get(key, files), standard, count)
              for key, name, files, standard, count in result]
    consumed = [f for _, _, files, _, _ in result for f in files]
    assert len(consumed) == len(set(consumed)) == len(source_files) == 407
    assert set(consumed) == source_files
    return result


def describe(source):
    side = 'gauche' if re.search(r'\bleft\b', source) else 'droit' if re.search(r'\bright\b', source) else None
    base = re.sub(r'\b(left|right)\b ?', '', source).strip()
    part = None
    for prefix in PARTS:
        if base.startswith(prefix + ' of '):
            part, base = PARTS[prefix], base[len(prefix) + 4:]
            break
    match = re.fullmatch(r'(first|second|third|fourth) (lumbrical|plantar interosseous) of foot', base)
    if match:
        number = ('first', 'second', 'third', 'fourth').index(match[1]) + 1
        fr = ('Lombrical' if match[2] == 'lumbrical' else 'Interosseux plantaire') + f' {number} du pied'
        region, kind = 'feet', 'foot'
        wiki_en, wiki_fr = ('Lumbricals of the foot', 'Muscles lombricaux du pied') if match[2] == 'lumbrical' else ('Plantar interossei muscles', 'Muscles interosseux plantaires')
    else:
        fr, region, kind = TERMS[base]  # Untranslated nomenclature must fail the build.
        wiki_en = base if base.endswith('muscle') else base + ' muscle'
        wiki_fr = 'Muscle ' + fr.split(' (')[0].lower()
        overrides = {
            'diaphragm': ('Thoracic diaphragm', 'Diaphragme (organe)'),
            'external intercostal muscle': ('External intercostal muscles', 'Muscles intercostaux externes'),
            'internal intercostal muscle': ('Internal intercostal muscles', 'Muscles intercostaux internes'),
            'innermost intercostal muscle': ('Innermost intercostal muscles', 'Muscles intercostaux intimes'),
            'spinalis': ('Spinalis', 'Muscle épineux'),
            'uvular muscle': ('Musculus uvulae', 'Muscle de la luette'),
        }
        wiki_en, wiki_fr = overrides.get(base, (wiki_en, wiki_fr))
    # Separate side and part with a separator: no ambiguous grammatical attachment to a digit.
    french = fr + ((' · côté ' + side) if side else '') + ((' · ' + part) if part else '')
    return source[0].upper() + source[1:], french, 'muscle_' + kind, region, wiki_en, wiki_fr


# Common family notes deliberately avoid claiming a complete clinical atlas.
_SUMMARIES = {
    'eye': ('An extraocular muscle: coordinated contractions steer the eyeball within the orbit.', 'Muscle oculomoteur : ses contractions coordonnées avec les autres muscles orientent le globe dans l’orbite.'),
    'eyelid': ('Raises the upper eyelid to open the eye.', 'Élève la paupière supérieure pour ouvrir l’œil.'),
    'tongue': ('An extrinsic tongue muscle, contributing to positioning the tongue for speech and swallowing.', 'Muscle extrinsèque de la langue, participant à son positionnement pour la parole et la déglutition.'),
    'palate': ('Part of the soft palate musculature, which changes its position and shape during swallowing and speech.', 'Appartient aux muscles du voile du palais, qui en modifient la position et la forme lors de la déglutition et de la parole.'),
    'neck': ('A neck muscle involved in positioning or stabilizing the head and cervical spine.', 'Muscle du cou participant au positionnement ou à la stabilisation de la tête et du rachis cervical.'),
    'suprahyoid': ('Located above the hyoid; this group participates in hyoid movement, swallowing and opening the jaw.', 'Situé au-dessus de l’os hyoïde ; ce groupe participe à ses mouvements, à la déglutition et à l’ouverture de la mâchoire.'),
    'infrahyoid': ('An infrahyoid muscle, helping position the hyoid or larynx during swallowing and vocal activity.', 'Muscle infra-hyoïdien contribuant au positionnement de l’os hyoïde ou du larynx lors de la déglutition et de la phonation.'),
    'larynx': ('An intrinsic laryngeal muscle. This group controls the vocal folds and the opening of the larynx.', 'Muscle intrinsèque du larynx. Ce groupe contrôle les plis vocaux et l’ouverture du larynx.'),
    'pharynx': ('A pharyngeal muscle involved in the coordinated passage of a swallowed bolus.', 'Muscle du pharynx participant au passage coordonné du bol alimentaire lors de la déglutition.'),
    'platysma': ('A thin superficial neck muscle that tenses the skin and contributes to lower-face movements.', 'Muscle superficiel mince du cou qui tend la peau et participe aux mouvements de la partie inférieure du visage.'),
    'scalene': ('A lateral neck muscle connecting cervical vertebrae to upper ribs; contributes to neck movement and accessory inspiration.', 'Muscle latéral du cou reliant les vertèbres cervicales aux premières côtes ; participe aux mouvements du cou et à l’inspiration accessoire.'),
    'back': ('Part of the deep back musculature, which supports posture and controls movements between vertebrae.', 'Appartient à la musculature profonde du dos, qui soutient la posture et contrôle les mouvements entre les vertèbres.'),
    'diaphragm': ('A muscular partition between thorax and abdomen. Its contraction lowers its dome and increases thoracic volume during inspiration.', 'Cloison musculaire entre thorax et abdomen. Sa contraction abaisse sa coupole et augmente le volume thoracique pendant l’inspiration.'),
    'intercostal': ('Muscles occupying the spaces between ribs. Their layers stabilize the intercostal spaces and participate in breathing.', 'Muscles occupant les espaces entre les côtes. Leurs différentes couches stabilisent ces espaces et participent à la respiration.'),
    'thorax': ('Muscle attached to the rib cage. Its shape and relations can be examined by hiding the overlying structures.', 'Muscle attaché à la cage thoracique. Sa forme et ses rapports peuvent être examinés en masquant les structures qui le recouvrent.'),
    'abdominal': ('A broad abdominal wall muscle, contributing to trunk movement and compression of the abdominal contents.', 'Muscle large de la paroi abdominale, participant aux mouvements du tronc et à la compression du contenu abdominal.'),
    'scapula': ('A shoulder-girdle muscle, helping position or stabilize the scapula or clavicle during upper-limb movement.', 'Muscle de la ceinture scapulaire, contribuant au positionnement ou à la stabilisation de la scapula ou de la clavicule lors des mouvements du membre supérieur.'),
    'shoulder': ('A muscle acting at the shoulder, helping move the humerus relative to the shoulder girdle.', 'Muscle agissant sur l’épaule et participant aux mouvements de l’humérus par rapport à la ceinture scapulaire.'),
    'rotator_cuff': ('One of the rotator cuff muscles; helps keep the humeral head centred against the glenoid during shoulder movement.', 'Un des muscles de la coiffe des rotateurs ; contribue au centrage de la tête humérale face à la glène pendant les mouvements de l’épaule.'),
    'biceps': ('An anterior arm muscle contributing to elbow flexion and forearm supination. Its two heads join distally.', 'Muscle antérieur du bras participant à la flexion du coude et à la supination de l’avant-bras. Ses deux chefs se rejoignent distalement.'),
    'elbow_flexion': ('An elbow flexor, bringing the forearm toward the arm.', 'Fléchisseur du coude, rapprochant l’avant-bras du bras.'),
    'elbow_extension': ('Contributes to extending the elbow and stabilizing the joint.', 'Participe à l’extension du coude et à la stabilisation de l’articulation.'),
    'pronation': ('Turns the forearm into pronation, with the radius crossing in front of the ulna.', 'Tourne l’avant-bras en pronation, le radius croisant en avant de l’ulna.'),
    'supination': ('Turns the forearm into supination, bringing the radius and ulna toward a parallel position.', 'Tourne l’avant-bras en supination, rapprochant le radius et l’ulna d’une position parallèle.'),
    'wrist_flexion': ('A forearm muscle contributing to wrist flexion or tension of the palmar tissues.', 'Muscle de l’avant-bras participant à la flexion du poignet ou à la mise en tension des tissus palmaires.'),
    'wrist_extension': ('An extensor of the wrist, also contributing to lateral balance of the hand.', 'Extenseur du poignet, participant aussi à l’équilibre latéral de la main.'),
    'finger_flexion': ('A long finger flexor. Its tendons transmit force from the forearm to the fingers.', 'Fléchisseur long des doigts. Ses tendons transmettent la force de l’avant-bras aux doigts.'),
    'finger_extension': ('A finger extensor whose tendons continue from the forearm toward the hand.', 'Extenseur des doigts dont les tendons se prolongent de l’avant-bras vers la main.'),
    'thumb': ('A muscle controlling the thumb. Thumb movements combine flexion, extension, abduction, adduction and opposition across several joints.', 'Muscle contrôlant le pouce. Ses mouvements combinent flexion, extension, abduction, adduction et opposition au niveau de plusieurs articulations.'),
    'hand': ('An intrinsic hand muscle, contributing to the fine positioning of the fingers during grip.', 'Muscle intrinsèque de la main, participant au positionnement fin des doigts pendant la préhension.'),
    'pelvic_floor': ('A pelvic floor muscle, contributing to support of the pelvic contents and continence.', 'Muscle du plancher pelvien, participant au soutien du contenu pelvien et à la continence.'),
    'sphincter': ('A striated muscle surrounding the anal canal, contributing to voluntary anal continence.', 'Muscle strié entourant le canal anal, participant à la continence anale volontaire.'),
    'hip_rotation': ('A deep hip muscle, contributing to external rotation and stabilization of the femoral head.', 'Muscle profond de la hanche, participant à la rotation latérale et à la stabilisation de la tête fémorale.'),
    'hip_flexion': ('Part of the iliopsoas group, which flexes the hip and contributes to lumbopelvic stability.', 'Appartient au groupe ilio-psoas, qui fléchit la hanche et contribue à la stabilité lombo-pelvienne.'),
    'gluteal': ('A gluteal muscle. This group moves the hip and stabilizes the pelvis, notably during walking and single-leg stance.', 'Muscle glutéal. Ce groupe mobilise la hanche et stabilise le bassin, notamment pendant la marche et l’appui sur une seule jambe.'),
    'adductor': ('A muscle of the medial thigh, contributing to drawing the thigh toward the body midline.', 'Muscle de la région médiale de la cuisse, contribuant à rapprocher la cuisse de la ligne médiane.'),
    'hamstring': ('A posterior thigh muscle contributing to knee flexion. Most parts of this group also cross the hip.', 'Muscle de la région postérieure de la cuisse participant à la flexion du genou. La plupart des parties de ce groupe traversent aussi la hanche.'),
    'quadriceps': ('Part of the quadriceps femoris, the main extensor of the knee. Rectus femoris also crosses the hip.', 'Appartient au quadriceps fémoral, principal extenseur du genou. Le droit fémoral traverse également la hanche.'),
    'sartorius': ('A long superficial thigh muscle crossing the hip and knee, contributing to hip flexion and knee flexion.', 'Long muscle superficiel de la cuisse traversant la hanche et le genou, participant à la flexion de ces deux articulations.'),
    'tensor': ('Tenses the fascia lata through the iliotibial tract and contributes to stabilization of the hip and knee.', 'Tend le fascia lata par l’intermédiaire du tractus ilio-tibial et contribue à la stabilisation de la hanche et du genou.'),
    'calf': ('A posterior leg muscle contributing to plantar flexion, which raises the heel or points the foot downward.', 'Muscle postérieur de la jambe participant à la flexion plantaire, qui soulève le talon ou abaisse la pointe du pied.'),
    'popliteus': ('A small posterior knee muscle that helps unlock the knee at the start of flexion.', 'Petit muscle postérieur du genou contribuant au déverrouillage de l’articulation au début de la flexion.'),
    'fibular': ('A lateral or anterior leg muscle contributing to eversion of the foot.', 'Muscle latéral ou antérieur de la jambe participant à l’éversion du pied.'),
    'tibialis_anterior': ('Dorsiflexes and inverts the foot; helps control lowering of the foot after heel contact.', 'Effectue la flexion dorsale et l’inversion du pied ; participe au contrôle de son abaissement après le contact du talon.'),
    'tibialis_posterior': ('Contributes to inversion and plantar flexion of the foot and supports the medial longitudinal arch.', 'Participe à l’inversion et à la flexion plantaire du pied et soutient la voûte longitudinale médiale.'),
    'toe_extension': ('An extensor of the toes or hallux, helping raise the digits of the foot.', 'Extenseur des orteils ou de l’hallux, participant au relèvement des doigts du pied.'),
    'toe_flexion': ('A flexor of the toes or hallux, contributing to support and propulsion through the foot.', 'Fléchisseur des orteils ou de l’hallux, participant à l’appui et à la propulsion du pied.'),
    'foot': ('An intrinsic foot muscle, contributing to toe positioning and support of the foot during loading.', 'Muscle intrinsèque du pied, participant au positionnement des orteils et au soutien du pied pendant l’appui.'),
}
summaries = {'muscle_' + key: value for key, value in _SUMMARIES.items()}
