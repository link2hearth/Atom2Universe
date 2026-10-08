"""BodyParts3D cartilages and discs, in the original skeletal coordinate system.

FJ3211 has only a generic disk label. Its T12-L1 position is checked against
adjacent discs/bones and uses a local identity instead of an invented FMA ID.
"""
import re

CLASSES = ('FMA55107', 'FMA7538')
ORDINALS = ('first', 'second', 'third', 'fourth', 'fifth', 'sixth', 'seventh',
            'eighth', 'ninth', 'tenth', 'eleventh', 'twelfth')
TERMS = {
    'cricoid cartilage': ('Cartilage cricoïde', 'cricoid', 'neck', 'Cricoid cartilage', 'Cartilage cricoïde'),
    'thyroid cartilage': ('Cartilage thyroïde', 'thyroid', 'neck', 'Thyroid cartilage', 'Cartilage thyroïde'),
    'arytenoid cartilage': ('Cartilage aryténoïde', 'arytenoid', 'neck', 'Arytenoid cartilage', 'Cartilage aryténoïde'),
    'corniculate cartilage': ('Cartilage corniculé', 'laryngeal_small', 'neck', 'Corniculate cartilage', 'Cartilage corniculé'),
    'cuneiform cartilage': ('Cartilage cunéiforme', 'laryngeal_small', 'neck', 'Cuneiform cartilage', 'Cartilage cunéiforme'),
    'septal nasal cartilage': ('Cartilage du septum nasal', 'nasal', 'skull', 'Nasal septum', 'Septum nasal'),
    'major alar cartilage': ('Grand cartilage alaire', 'nasal', 'skull', 'Major alar cartilage', 'Cartilage grand alaire'),
    'lateral nasal cartilage': ('Cartilage nasal latéral', 'nasal', 'skull', 'Lateral nasal cartilage', 'Cartilage latéral du nez'),
}


def entries(concepts):
    source_files = {r['element file id'] for key in CLASSES for r in concepts[key]}
    grouped = {r['element file id'] for r in concepts['FMA9615']}
    assert grouped == {'FJ2440', 'FJ2769'}
    singles = {}
    for key, rows in concepts.items():
        if len(rows) == 1 and rows[0]['element file id'] in source_files:
            element = rows[0]['element file id']
            assert element not in singles, (element, key)
            singles[element] = rows[0]
    assert source_files - singles.keys() == grouped | {'FJ3211'}
    result = [(singles[f]['concept id'], singles[f]['name'], [f], False, 0)
              for f in sorted(source_files - grouped - {'FJ3211'})]
    result += [('FMA9615', 'cricoid cartilage', sorted(grouped), False, 0),
               ('BP3D_FJ3211', 'intervertebral disk T12-L1', ['FJ3211'], False, 0)]
    consumed = [f for _, _, files, _, _ in result for f in files]
    assert len(consumed) == len(set(consumed)) == len(source_files) == 51
    assert len(result) == 50
    return result


def disc_level(source):
    if source == 'intervertebral disk of axis':
        return 'C2', 'C3'
    if source == 'intervertebral disk T12-L1':
        return 'T12', 'L1'
    match = re.fullmatch(r'intervertebral disk of (\w+) (cervical|thoracic|lumbar) vertebra', source)
    if not match:
        return None
    n = ORDINALS.index(match[1]) + 1
    prefix = {'cervical': 'C', 'thoracic': 'T', 'lumbar': 'L'}[match[2]]
    following = {'C7': 'T1', 'T12': 'L1', 'L5': 'S1'}.get(prefix + str(n), prefix + str(n + 1))
    return prefix + str(n), following


def describe(source):
    level = disc_level(source)
    if level:
        segment = '–'.join(level)
        return (f'Intervertebral disc {segment}', f'Disque intervertébral {segment}',
                'cartilage_disc', 'spine', 'Intervertebral disc', 'Disque intervertébral')
    side = 'gauche' if source.startswith('left ') else 'droit' if source.startswith('right ') else None
    base = re.sub(r'^(left|right) ', '', source)
    match = re.fullmatch(r'(\w+) costal cartilage', base)
    if match:
        number = ORDINALS.index(match[1]) + 1
        french = f'Cartilage costal {number}'
        kind, region, wiki_en, wiki_fr = 'costal', 'thorax', 'Costal cartilage', 'Cartilage costal'
    else:
        french, kind, region, wiki_en, wiki_fr = TERMS[base]
    french += (' · côté ' + side) if side else ''
    return source[0].upper() + source[1:], french, 'cartilage_' + kind, region, wiki_en, wiki_fr


summaries = {
    'cartilage_disc': (
        'Fibrocartilaginous structure between adjacent vertebral bodies. It distributes loads and allows small movements. The atlas shows the outer surface only; the annulus fibrosus and nucleus pulposus are not separately modelled.',
        'Structure fibrocartilagineuse entre deux corps vertébraux voisins. Elle répartit les charges et permet de petits mouvements. Le modèle montre sa surface externe ; l’anneau fibreux et le noyau pulpeux ne sont pas représentés séparément.'),
    'cartilage_costal': (
        'Cartilaginous extension of a rib, contributing to the flexibility of the front of the rib cage during breathing. This layer contains the source structures labelled for ribs 1 to 7 on each side; cartilages of ribs 8 to 10 are not separately available.',
        'Prolongement cartilagineux d’une côte, participant à la souplesse de l’avant de la cage thoracique pendant la respiration. Cette couche contient les structures sources nommées pour les côtes 1 à 7 de chaque côté ; les cartilages des côtes 8 à 10 ne sont pas disponibles séparément.'),
    'cartilage_nasal': (
        'Supports the flexible part of the external nose or the front of the nasal septum. Its shape helps define the nasal passages.',
        'Soutient la partie souple du nez externe ou l’avant du septum nasal. Sa forme contribue à délimiter les passages nasaux.'),
    'cartilage_cricoid': (
        'Ring-shaped cartilage below the thyroid cartilage, supporting the larynx above the trachea. It has an anterior arch and a broader posterior lamina.',
        'Cartilage en anneau situé sous le cartilage thyroïde, soutenant le larynx au-dessus de la trachée. Il possède un arc antérieur et une lame postérieure plus large.'),
    'cartilage_thyroid': (
        'Large protective cartilage at the front and sides of the larynx. It provides attachment for laryngeal structures and contributes to the framework around the vocal folds.',
        'Grand cartilage protecteur à l’avant et sur les côtés du larynx. Il sert d’attache à des structures laryngées et participe à l’armature entourant les plis vocaux.'),
    'cartilage_arytenoid': (
        'Paired laryngeal cartilage on the upper part of the cricoid. Its movements help adjust the position of the vocal folds.',
        'Cartilage pair du larynx situé sur la partie supérieure du cricoïde. Ses mouvements contribuent à régler la position des plis vocaux.'),
    'cartilage_laryngeal_small': (
        'Small paired cartilage supporting soft tissues around the entrance to the larynx.',
        'Petit cartilage pair soutenant les tissus souples autour de l’entrée du larynx.'),
}


def provenance():
    return {
        'source': 'BodyParts3D 4.0', 'conceptClasses': list(CLASSES),
        'structures': 50, 'sourceElements': 51,
        'groups': {'intervertebralDiscs': 23, 'costalStructures': 14, 'laryngealCartilages': 8, 'nasalCartilages': 5},
        'modifications': 'Original skeletal coordinates and full source mesh detail preserved; OBJ to GLB; smooth normals; independent materials. Identical cricoid meshes FJ2440 and FJ2769 deduplicated under FMA9615.',
        'localIdentifications': {'BP3D_FJ3211': 'Source labels FJ3211 only as an intervertebral disk (FMA10446 class); T12-L1 level identified from spatial order between the labelled T11-T12 and L1-L2 discs and the T12/L1 vertebral bodies. No specific FMA identifier is asserted.'},
        'limitations': 'Not a complete cartilage or joint model. Costal source meshes are labelled for ribs 1 to 7 per side; no separate cartilages for ribs 8 to 10. Joint-surface cartilage, auricular cartilage, epiglottis and ligaments are not included. Four menisci from Z-Anatomy are documented separately in the menisci section.',
    }
