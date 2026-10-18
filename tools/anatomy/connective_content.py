"""Identified connective surfaces from BodyParts3D; no synthetic attachments."""

# FMA | exact source name | French label | source elements | region | family
_TERMS = '''
FMA44249|right long plantar ligament|Ligament plantaire long droit|FJ1424|feet|plantar
FMA44250|left long plantar ligament|Ligament plantaire long gauche|FJ1424M|feet|plantar
FMA72309|right stylohyoid ligament|Ligament stylo-hyoïdien droit|FJ2764|neck|stylohyoid
FMA72311|left stylohyoid ligament|Ligament stylo-hyoïdien gauche|FJ2763|neck|stylohyoid
FMA55138|median thyrohyoid ligament|Ligament thyro-hyoïdien médian|FJ2790|neck|laryngeal
FMA55140|right lateral thyrohyoid ligament|Ligament thyro-hyoïdien latéral droit|FJ2797|neck|laryngeal
FMA55141|left lateral thyrohyoid ligament|Ligament thyro-hyoïdien latéral gauche|FJ2779|neck|laryngeal
FMA55227|hyo-epiglottic ligament|Ligament hyo-épiglottique|FJ2771|neck|laryngeal
FMA55230|thyro-epiglottic ligament|Ligament thyro-épiglottique|FJ2807|neck|laryngeal
FMA55237|median cricothyroid ligament|Ligament crico-thyroïdien médian|FJ2789|neck|laryngeal
FMA55245|right vocal ligament|Ligament vocal droit|FJ2805|neck|vocal
FMA55246|left vocal ligament|Ligament vocal gauche|FJ2787|neck|vocal
FMA258847|right calcaneal tendon|Tendon calcanéen droit (tendon d’Achille)|FJ1405|lower|achilles
FMA264844|left calcaneal tendon|Tendon calcanéen gauche (tendon d’Achille)|FJ1405M|lower|achilles
FMA58776|right iliotibial tract|Tractus ilio-tibial droit|FJ1423|lower|iliotibial
FMA58777|left iliotibial tract|Tractus ilio-tibial gauche|FJ1423M|lower|iliotibial
FMA40120|flexor retinaculum of right wrist|Rétinaculum des fléchisseurs du poignet droit|FJ1471|hands|retinaculum
FMA40121|flexor retinaculum of left wrist|Rétinaculum des fléchisseurs du poignet gauche|FJ1471M|hands|retinaculum
FMA55077|pharyngeal raphe|Raphé pharyngien|FJ2749|neck|raphe
FMA55619|right pterygomandibular raphe|Raphé ptérygo-mandibulaire droit|FJ2756|skull|raphe
FMA55620|left pterygomandibular raphe|Raphé ptérygo-mandibulaire gauche|FJ2744|skull|raphe
FMA14643|mesentery of small intestine|Mésentère de l’intestin grêle|FJ3396|abdomen|mesentery
FMA16549|mesoappendix|Mésoappendice|FJ3397|abdomen|mesentery
FMA14647|transverse mesocolon|Mésocôlon transverse|FJ3398|abdomen|mesentery
'''
TERMS = {r[0]: r[1:] for line in _TERMS.strip().splitlines() if (r := line.split('|'))}
BY_NAME = {r[0]: r[1:] for r in TERMS.values()}
groups = {
    'connective_head_neck': ('Head and neck', 'Tête et cou'),
    'connective_hand': ('Wrist retinacula', 'Rétinaculums des poignets'),
    'connective_lower': ('Lower limbs and feet', 'Membres inférieurs et pieds'),
    'connective_peritoneum': ('Mesenteries', 'Mésos péritonéaux'),
}
WIKI = {
    'plantar': ('Long plantar ligament', 'Ligament plantaire long'),
    'stylohyoid': ('Stylohyoid ligament', 'Ligament stylo-hyoïdien'),
    'laryngeal': ('Larynx', 'Larynx'), 'vocal': ('Vocal ligament', 'Ligament vocal'),
    'achilles': ('Achilles tendon', 'Tendon calcanéen'),
    'iliotibial': ('Iliotibial tract', 'Tractus ilio-tibial'),
    'retinaculum': ('Flexor retinaculum of the hand', 'Rétinaculum des fléchisseurs'),
    'raphe': ('Raphe', 'Raphé'), 'mesentery': ('Mesentery', 'Mésentère'),
}
_SUMMARIES = {
    'plantar': ('Strong ligament on the sole connecting the calcaneus to the cuboid and metatarsal bases. It supports the longitudinal arch and forms part of the fibularis longus tendon tunnel.', 'Ligament résistant de la plante reliant le calcanéus au cuboïde et aux bases métatarsiennes. Il soutient la voûte longitudinale et participe au tunnel du tendon du long fibulaire.'),
    'stylohyoid': ('Fibrous connection from the styloid process of the temporal bone to the lesser horn of the hyoid. It belongs to the suspension apparatus of the hyoid.', 'Lien fibreux du processus styloïde de l’os temporal à la petite corne de l’os hyoïde. Il participe à l’appareil de suspension de l’hyoïde.'),
    'laryngeal': ('Fibrous attachment connecting elements of the larynx or linking them to the hyoid. These connections help support the laryngeal framework while allowing movement during swallowing and phonation.', 'Attache fibreuse reliant des éléments du larynx ou les reliant à l’hyoïde. Ces liens soutiennent la charpente laryngée tout en permettant les mouvements de déglutition et de phonation.'),
    'vocal': ('Elastic band within the vocal fold, extending from the thyroid cartilage to the vocal process of the arytenoid. Changes in its tension contribute to pitch control.', 'Bande élastique dans le pli vocal, tendue du cartilage thyroïde au processus vocal de l’aryténoïde. Les variations de sa tension participent au contrôle de la hauteur de la voix.'),
    'achilles': ('Common distal tendon of gastrocnemius and soleus, inserting on the calcaneus. It transmits the force used in ankle plantar flexion, including walking and rising onto the toes.', 'Tendon distal commun des gastrocnémiens et du soléaire, inséré sur le calcanéus. Il transmet la force de flexion plantaire de la cheville, notamment pendant la marche et la montée sur la pointe des pieds.'),
    'iliotibial': ('Thickened lateral part of the fascia lata receiving fibres from tensor fasciae latae and gluteus maximus. Its distal attachment on the tibia contributes to lateral hip and knee stability.', 'Épaississement latéral du fascia lata recevant des fibres du tenseur du fascia lata et du grand fessier. Son attache distale au tibia participe à la stabilité latérale de la hanche et du genou.'),
    'retinaculum': ('Fibrous band spanning the carpal arch on the palmar side of the wrist. It forms the roof of the carpal tunnel containing the median nerve and finger flexor tendons.', 'Bande fibreuse fermant l’arc carpien du côté palmaire du poignet. Elle forme le toit du canal carpien, qui contient le nerf médian et les tendons fléchisseurs des doigts.'),
    'raphe': ('Fibrous line of attachment shared by adjacent muscles. Pharyngeal and pterygomandibular raphes provide attachment for muscles involved in swallowing and movements of the oral cavity.', 'Ligne fibreuse d’attache commune à des muscles voisins. Les raphés pharyngien et ptérygo-mandibulaire donnent insertion à des muscles impliqués dans la déglutition et les mouvements de la cavité buccale.'),
    'mesentery': ('Double layer of peritoneum suspending part of the digestive tract and carrying its vessels, nerves and lymphatics. The source shows a surface envelope, without microscopic layers or all of its contents.', 'Double feuillet de péritoine suspendant une partie du tube digestif et portant ses vaisseaux, nerfs et lymphatiques. La source représente une enveloppe de surface, sans ses couches microscopiques ni la totalité de son contenu.'),
}
summaries = {'connective_' + k: v for k, v in _SUMMARIES.items()}
references = [
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/4-3-connective-tissue-supports-and-protects',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/11-6-appendicular-muscles-of-the-pelvic-girdle-and-lower-limbs',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/23-1-overview-of-the-digestive-system',
]

def entries(concepts, partof):
    result = []
    for identity, (name, french, files, region, kind) in TERMS.items():
        elements = files.split(',')
        assert set(elements) <= {r['element file id'] for r in concepts[identity]}, (identity, name)
        assert {r['name'] for r in concepts[identity]} == {name}, (identity, name)
        result.append((identity, name, elements, False, 0))
    return result

def describe(name):
    french, files, region, kind = BY_NAME[name]
    return name.capitalize(), french, 'connective_' + kind, region, *WIKI[kind]

def attributes(name):
    french, files, region, kind = BY_NAME[name]
    category = ('connective_peritoneum' if kind == 'mesentery' else
                'connective_head_neck' if region in ('neck', 'skull') else
                'connective_hand' if region == 'hands' else 'connective_lower')
    return {'category': category, 'color': [.87, .85, .71] if kind != 'mesentery' else [.88, .73, .59]}

def provenance(source, entries):
    return {'source': 'BodyParts3D 4.0', 'structures': len(entries),
            'modifications': 'Original source geometry, coordinates and FMA identifiers; illustrative colours and smooth normals.',
            'limitations': 'Selected identifiable connective surfaces, not the complete ligament or fascial system. Ocular supports are in the sensory layer. Ambiguous intermediate tendon FJ1581 and overlapping levator-ani tendinous-arch variants are excluded.'}
