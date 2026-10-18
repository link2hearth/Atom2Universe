"""Curated native HRA female atlas, independent of the BodyParts3D male atlas.

No anatomical surface is generated, warped, reflected or borrowed from the male
body. Surface patches belonging to one organ are joined for selection only.
Exact upstream node names and raw crosswalk identities remain in provenance.
"""
import csv
import hashlib
import json
import struct
from pathlib import Path

import numpy as np

SOURCE_URL = 'https://cdn.humanatlas.io/digital-objects/ref-organ/united-female/v1.5/assets/3d-vh-f-united.glb'
SOURCE_SHA256 = '472567a56896b9b7890508da6501fbf858e56aaa30745365f7a71ade782b529c'
CROSSWALK_SHA256 = '8b67f6d66873f2458c68471c5c086ea4405aa6682337612795defced9785a437'
REGISTRATION = Path(__file__).with_name('female_registration.json')
references = [
    'https://humanatlas.io/3d-reference-library',
    'https://3d.nih.gov/entries/3DPX-020992',
    'https://doi.org/10.48539/HBM352.BTSQ.586',
    'https://training.seer.cancer.gov/anatomy/reproductive/female/',
    'https://training.seer.cancer.gov/anatomy/reproductive/female/ovaries.html',
    'https://training.seer.cancer.gov/anatomy/reproductive/female/tract.html',
    'https://training.seer.cancer.gov/anatomy/reproductive/female/glands.html',
    'https://www.cancer.gov/types/breast/what-is-breast-cancer',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/8-3-the-pelvic-girdle-and-pelvis',
]

# Original short teaching notes; no reference text or illustrations are copied.
# Each entry is EN summary, FR summary, EN Wikipedia title, FR Wikipedia title.
NOTES = {
    'skin': ('Skin covers the body and contributes to protection, sensation and temperature regulation. This surface represents the female reference body.', 'La peau recouvre le corps et participe à sa protection, à la sensibilité et à la régulation thermique. Cette surface représente le corps féminin de référence.', 'Human_skin', 'Peau_humaine'),
    'breast_fat': ('Adipose tissue surrounds the glandular parts of the breast and contributes to its shape. Breast volume alone does not measure milk production.', 'Le tissu adipeux entoure les parties glandulaires du sein et contribue à sa forme. Le volume mammaire ne mesure pas à lui seul la production de lait.', 'Breast', 'Sein'),
    'breast_gland': ('The mammary gland is arranged in lobes containing milk-secreting units. Its activity changes with hormonal state and lactation.', 'La glande mammaire est organisée en lobes contenant des unités sécrétrices de lait. Son activité varie avec l’état hormonal et la lactation.', 'Mammary_gland', 'Glande_mammaire'),
    'breast_duct': ('Lactiferous ducts carry milk from the gland towards openings in the nipple. The source includes separately segmented duct expansions.', 'Les conduits lactifères acheminent le lait de la glande vers les orifices du mamelon. La source distingue aussi des dilatations des conduits.', 'Lactiferous_duct', 'Canal_galactophore'),
    'breast_surface': ('The nipple and surrounding areola form the central surface of the breast. Mammary ducts open at the nipple; areolar glands contribute surface secretions.', 'Le mamelon et l’aréole qui l’entoure forment la partie centrale de la surface du sein. Les conduits mammaires débouchent au mamelon ; les glandes aréolaires produisent des sécrétions de surface.', 'Nipple', 'Mamelon'),
    'breast_ligament': ('Connective tissue bands support the breast between its superficial tissues and deeper attachments.', 'Des bandes de tissu conjonctif soutiennent le sein entre ses tissus superficiels et ses attaches profondes.', 'Cooper%27s_ligaments', 'Sein'),
    'ovary': ('The ovary contains developing oocytes and produces reproductive hormones. It lies beside the uterus, close to the free end of the uterine tube.', 'L’ovaire contient les ovocytes en développement et produit des hormones de la reproduction. Il se situe à côté de l’utérus, près de l’extrémité libre de la trompe utérine.', 'Ovary', 'Ovaire'),
    'tube': ('The uterine tube links the uterine cavity to the peritoneal cavity near the ovary. Its fimbriae and ciliated lining assist oocyte transport.', 'La trompe utérine relie la cavité utérine à la cavité péritonéale près de l’ovaire. Ses franges et son revêtement cilié participent au transport de l’ovocyte.', 'Fallopian_tube', 'Trompe_utérine'),
    'uterus': ('The uterus is a muscular pelvic organ between the bladder and rectum. Its cavity communicates with the uterine tubes and the cervical canal.', 'L’utérus est un organe musculaire pelvien situé entre la vessie et le rectum. Sa cavité communique avec les trompes utérines et le canal cervical.', 'Uterus', 'Utérus'),
    'cervix': ('The cervix forms the lower part of the uterus and projects into the vagina. Its canal connects the uterine cavity with the vagina.', 'Le col forme la partie inférieure de l’utérus et fait saillie dans le vagin. Son canal relie la cavité utérine au vagin.', 'Cervix', 'Utérus'),
    'vagina': ('The vagina is a distensible muscular canal extending from the cervix towards the exterior. It is posterior to the lower urinary tract and anterior to the rectum.', 'Le vagin est un canal musculaire extensible allant du col utérin vers l’extérieur. Il se situe en arrière des voies urinaires basses et en avant du rectum.', 'Vagina', 'Vagin'),
    'uterine_support': ('Pelvic connective tissues and peritoneal folds maintain the relations of the reproductive organs. These named structures have different attachments; they are not all equivalent load-bearing ligaments.', 'Les tissus conjonctifs pelviens et les replis péritonéaux maintiennent les rapports des organes génitaux. Ces structures ont des attaches différentes et ne constituent pas toutes des ligaments porteurs équivalents.', 'Broad_ligament_of_the_uterus', 'Utérus'),
    'pelvis': ('The hip bones, sacrum and coccyx enclose the bony pelvis. Its form supports the trunk and defines the space around the pelvic organs.', 'Les os coxaux, le sacrum et le coccyx délimitent le bassin osseux. Sa forme soutient le tronc et définit l’espace entourant les organes pelviens.', 'Pelvis', 'Bassin_osseux'),
    'vertebra': ('Vertebrae support the axial body and surround the vertebral canal. Their shapes and articulations vary along the cervical, thoracic and lumbar regions.', 'Les vertèbres soutiennent l’axe du corps et entourent le canal vertébral. Leurs formes et articulations varient entre les régions cervicale, thoracique et lombaire.', 'Vertebral_column', 'Colonne_vertébrale'),
    'lumbar': ('The source contains six meshes labelled lumbar vertebrae, retained together here in their native positions. The usual adult count is five; this source labelling alone does not establish a diagnosis or vertebral numbering for a patient.', 'La source contient six maillages nommés vertèbres lombaires, conservés ensemble dans leur position native. Le nombre habituel chez l’adulte est cinq ; cet étiquetage ne suffit pas à poser un diagnostic ni à numéroter les vertèbres d’une personne.', 'Lumbar_vertebrae', 'Vertèbre_lombaire'),
    'lower_bone': ('The femur, tibia, fibula and patella form the bony framework around the thigh, knee and leg. Their surfaces provide joint contacts and attachments for soft tissues.', 'Le fémur, le tibia, la fibula et la patella constituent l’armature osseuse de la cuisse, du genou et de la jambe. Leurs surfaces portent des articulations et des attaches de tissus mous.', 'Lower_limb', 'Membre_inférieur_humain'),
    'knee_ligament': ('The ligaments around the knee guide motion and limit excessive displacement between its bones. The cruciate ligaments lie inside the joint capsule.', 'Les ligaments du genou guident les mouvements et limitent les déplacements excessifs entre ses os. Les ligaments croisés se trouvent à l’intérieur de la capsule articulaire.', 'Knee', 'Genou'),
    'meniscus': ('The medial and lateral menisci are fibrocartilaginous structures between femur and tibia. They improve joint congruence and distribute load; the source groups both menisci of each knee.', 'Les ménisques médial et latéral sont des structures fibrocartilagineuses situées entre fémur et tibia. Ils améliorent la congruence articulaire et répartissent les charges ; la source regroupe les deux ménisques de chaque genou.', 'Meniscus_(anatomy)', 'Ménisque_(anatomie)'),
    'cartilage': ('Articular cartilage covers contacting joint surfaces and contributes to smooth movement under load.', 'Le cartilage articulaire recouvre les surfaces de contact de l’articulation et facilite leur mouvement sous charge.', 'Articular_cartilage', 'Cartilage_articulaire'),
    'quadriceps': ('The rectus femoris is one component of the quadriceps and acts across the hip and knee. The quadriceps tendon transmits force to the patella.', 'Le droit fémoral est une composante du quadriceps et agit sur la hanche et le genou. Le tendon quadricipital transmet la force à la patella.', 'Rectus_femoris_muscle', 'Muscle_droit_fémoral'),
    'kidney': ('The kidney filters blood and adjusts water and solute excretion. Its cortex and medulla surround a collecting system that drains towards the ureter.', 'Le rein filtre le sang et ajuste l’excrétion d’eau et de solutés. Son cortex et sa médulla entourent un système collecteur qui se draine vers l’uretère.', 'Kidney', 'Rein'),
    'ureter': ('The ureter conveys urine from the renal pelvis to the bladder. It is distinct from the urethra, which carries urine out of the bladder.', 'L’uretère conduit l’urine du pelvis rénal à la vessie. Il est distinct de l’urètre, qui évacue l’urine hors de la vessie.', 'Ureter', 'Uretère'),
    'bladder': ('The bladder stores urine in front of the reproductive organs. Its muscular wall contracts during emptying.', 'La vessie stocke l’urine en avant des organes génitaux internes. Sa paroi musculaire se contracte lors de la vidange.', 'Urinary_bladder', 'Vessie'),
    'intestine': ('The small intestine supports digestion and nutrient absorption. The large intestine reabsorbs water and conveys intestinal contents towards the rectum.', 'L’intestin grêle participe à la digestion et à l’absorption des nutriments. Le gros intestin réabsorbe de l’eau et conduit le contenu intestinal vers le rectum.', 'Intestine', 'Intestin'),
    'rectum': ('The rectum is the terminal pelvic part of the large intestine. In the female pelvis it lies behind the vagina and uterus.', 'Le rectum est la partie pelvienne terminale du gros intestin. Dans le bassin féminin, il se situe derrière le vagin et l’utérus.', 'Rectum', 'Rectum'),
    'liver': ('The liver processes absorbed nutrients and produces bile. Its vessels and bile ducts meet at the hepatic hilum.', 'Le foie transforme les nutriments absorbés et produit la bile. Ses vaisseaux et ses voies biliaires se rejoignent au hile hépatique.', 'Liver', 'Foie'),
    'bile': ('The gallbladder stores bile; the biliary passages deliver it towards the duodenum. Bile assists the handling of dietary fats.', 'La vésicule biliaire stocke la bile ; les voies biliaires la conduisent vers le duodénum. La bile participe au traitement des graisses alimentaires.', 'Biliary_tract', 'Voies_biliaires'),
    'pancreas': ('The pancreas secretes digestive enzymes into the intestine and hormones into the blood. Its head lies next to the duodenum.', 'Le pancréas sécrète des enzymes digestives dans l’intestin et des hormones dans le sang. Sa tête est située au voisinage du duodénum.', 'Pancreas', 'Pancréas'),
    'heart': ('The heart propels blood through pulmonary and systemic circulation. The atria receive blood; the ventricles eject it, with valves helping maintain forward flow.', 'Le cœur propulse le sang dans les circulations pulmonaire et générale. Les atriums reçoivent le sang ; les ventricules l’éjectent, avec des valves qui favorisent un flux à sens unique.', 'Heart', 'Cœur'),
    'lung': ('The lungs contain branching airways and gas-exchange tissue. The right lung has three lobes and the left lung two.', 'Les poumons contiennent des voies aériennes ramifiées et des tissus d’échanges gazeux. Le poumon droit possède trois lobes et le gauche deux.', 'Lung', 'Poumon'),
    'airway': ('The trachea and bronchi conduct air towards the lungs. Cartilage helps keep these larger airways open.', 'La trachée et les bronches conduisent l’air vers les poumons. Le cartilage contribue à maintenir ces grandes voies aériennes ouvertes.', 'Trachea', 'Trachée'),
    'spleen': ('The spleen filters blood and contributes to immune surveillance. It lies in the upper left abdomen.', 'La rate filtre le sang et participe à la surveillance immunitaire. Elle se situe dans la partie supérieure gauche de l’abdomen.', 'Spleen', 'Rate'),
    'thymus': ('The thymus supports T-lymphocyte development. Its size and tissue composition change with age.', 'Le thymus participe au développement des lymphocytes T. Sa taille et sa composition tissulaire évoluent avec l’âge.', 'Thymus', 'Thymus'),
    'artery': ('Arteries carry blood away from the heart towards organs. These meshes preserve the named arterial branches available in the reference model.', 'Les artères conduisent le sang depuis le cœur vers les organes. Ces maillages conservent les branches artérielles nommées présentes dans le modèle de référence.', 'Artery', 'Artère'),
    'vein': ('Veins return blood towards the heart. Their branching pattern and connections vary between individuals.', 'Les veines ramènent le sang vers le cœur. Leur ramification et leurs connexions varient entre les individus.', 'Vein', 'Veine'),
    'eye': ('The eye combines transparent optical tissues with a light-sensitive retina and supporting coats. The globe components remain separately selectable in this model.', 'L’œil associe des tissus optiques transparents à une rétine sensible à la lumière et à des enveloppes de soutien. Les composantes du globe restent sélectionnables séparément dans ce modèle.', 'Human_eye', 'Œil_humain'),
    'optic': ('The optic nerve conveys retinal signals towards the brain. Part of the fibres cross at the optic chiasm.', 'Le nerf optique transmet les signaux rétiniens vers le cerveau. Une partie des fibres se croisent au chiasma optique.', 'Optic_nerve', 'Nerf_optique'),
}


def definitions():
    """Hand-curated source node sets, never inferred from unreviewed names."""
    rows = []

    def add(ids, en, fr, note, layer, region, group, color, system=None, opacity=None):
        rows.append(dict(nodes=[ids] if isinstance(ids, int) else list(ids), en=en, fr=fr,
                         note=note, layer=layer, region=region, group=group, color=color,
                         system=system, opacity=opacity))

    bone = [.80, .74, .62]
    pink = [.72, .34, .38]
    ligament = [.73, .72, .55]
    add(2, 'Skin of the female body', 'Peau du corps féminin', 'skin', 'skin', 'body', 'female_skin', [.76, .55, .42])
    for side, fr_side, ids in [('Left', 'gauche', [5, 6, 7, 8, 9, 10, 11, 12]), ('Right', 'droit', [14, 15, 16, 17, 20, 19, 18, 21])]:
        terms = [('breast adipose tissue', 'Tissu adipeux du sein', 'breast_fat', [.88, .75, .39]),
                 ('areolar tubercles', 'Tubercules aréolaires du sein', 'breast_surface', [.56, .30, .25]),
                 ('nipple', 'Mamelon', 'breast_surface', [.58, .27, .25]),
                 ('areola', 'Aréole du sein', 'breast_surface', [.61, .34, .27]),
                 ('mammary lobes', 'Lobes mammaires du sein', 'breast_gland', [.82, .56, .57]),
                 ('main lactiferous ducts', 'Conduits lactifères du sein', 'breast_duct', [.90, .75, .59]),
                 ('lactiferous sinuses', 'Sinus lactifères du sein', 'breast_duct', [.80, .66, .46]),
                 ('suspensory ligaments of breast', 'Ligaments suspenseurs du sein', 'breast_ligament', ligament)]
        for i, (en, fr, note, color) in zip(ids, terms):
            layer = 'connective' if note == 'breast_ligament' else 'organs'
            add(i, f'{side} {en}', f'{fr} {fr_side}', note, layer, 'thorax', 'female_breast', color,
                'reproductive' if layer == 'organs' else None)
    add([473, 474, 475, 476, 477, 478], 'Body and fundus of uterus', 'Corps et fond de l’utérus', 'uterus', 'organs', 'pelvis', 'female_reproductive', [.63, .23, .28], 'reproductive')
    add([479, 480, 481], 'Uterine cervix', 'Col de l’utérus', 'cervix', 'organs', 'pelvis', 'female_reproductive', [.67, .30, .36], 'reproductive')
    add([431, 432], 'Vagina and cervical junction', 'Vagin et jonction cervicale', 'vagina', 'organs', 'pelvis', 'female_reproductive', pink, 'reproductive')
    for side, fr_side, ovary, tube in [('Left', 'gauche', 469, [440, 441, 442, 443]), ('Right', 'droite', 470, [435, 436, 437, 438])]:
        add(ovary, f'{side} ovary', f'Ovaire {"gauche" if side == "Left" else "droit"}', 'ovary', 'organs', 'pelvis', 'female_reproductive', [.86, .76, .57], 'reproductive')
        add(tube, f'{side} uterine tube', f'Trompe utérine {fr_side}', 'tube', 'organs', 'pelvis', 'female_reproductive', [.77, .46, .47], 'reproductive')
    supports = '''
450|Right uterosacral ligament|Ligament utéro-sacré droit
451|Left uterosacral ligament|Ligament utéro-sacré gauche
453|Right cardinal ligament|Ligament cardinal droit
454|Left cardinal ligament|Ligament cardinal gauche
456|Right suspensory ligament of ovary|Ligament suspenseur de l’ovaire droit
457|Left suspensory ligament of ovary|Ligament suspenseur de l’ovaire gauche
459|Left ovarian ligament|Ligament propre de l’ovaire gauche
460|Right ovarian ligament|Ligament propre de l’ovaire droit
461|Broad ligament of uterus|Ligament large de l’utérus
463|Left mesosalpinx|Mésosalpinx gauche
464|Right mesosalpinx|Mésosalpinx droit
466|Right mesovarium|Mésovarium droit
467|Left mesovarium|Mésovarium gauche
'''
    for line in supports.strip().splitlines():
        i, en, fr = line.split('|')
        add(int(i), en, fr, 'uterine_support', 'connective', 'pelvis', 'female_pelvic_support', ligament)
    # The source reverses the side names/crosswalk for this pair: node 447 is
    # wholly on the subject's left and 448 wholly on the right. Retain the pair
    # with a bilateral label and preserve the erroneous raw mapping as evidence.
    add([447, 448], 'Round ligaments of uterus', 'Ligaments ronds de l’utérus', 'uterine_support', 'connective', 'pelvis', 'female_pelvic_support', ligament)
    for ids, en, fr in [([967, 971, 975, 977, 982, 985], 'Left hip bone', 'Os coxal gauche'),
                        ([968, 970, 974, 978, 981, 984], 'Right hip bone', 'Os coxal droit'),
                        ([963], 'Sacrum', 'Sacrum'), ([964], 'Coccyx', 'Coccyx')]:
        add(ids, en, fr, 'pelvis', 'skeleton', 'pelvis', 'female_pelvis', bone)
    add(range(1048, 1054), 'Lumbar region of the source model', 'Région lombaire du modèle source', 'lumbar', 'skeleton', 'spine', 'female_spine', bone)
    for start, count, en, fr, prefix, region in [(1054, 12, 'Thoracic vertebra', 'Vertèbre thoracique', 'T', 'spine'), (1066, 7, 'Cervical vertebra', 'Vertèbre cervicale', 'C', 'neck')]:
        for index in range(count):
            add(start + index, f'{en} {prefix}{index+1}', f'{fr} {prefix}{index+1}', 'vertebra', 'skeleton', region, 'female_spine', bone)
    # The distal femoral mesh is divided into labelled surface patches upstream.
    # Removing those patches would create holes; keep them within the femur.
    for ids, en, fr in [([998, 999, 1000, *range(1002, 1014)], 'Right femur', 'Fémur droit'),
                        ([1028, *range(1029, 1037), *range(1038, 1044)], 'Left femur', 'Fémur gauche'),
                        ([1014], 'Right tibia', 'Tibia droit'), ([1045], 'Left tibia', 'Tibia gauche'),
                        ([1015], 'Right fibula', 'Fibula droite'), ([1046], 'Left fibula', 'Fibula gauche'),
                        ([1016], 'Right patella', 'Patella droite'), ([1044], 'Left patella', 'Patella gauche')]:
        add(ids, en, fr, 'lower_bone', 'skeleton', 'lower', 'female_lower_bones', bone)
    for ids, en, fr in [([996], 'Right knee menisci', 'Ménisques du genou droit'), ([1026], 'Left knee menisci', 'Ménisques du genou gauche')]:
        add(ids, en, fr, 'meniscus', 'cartilage', 'lower', 'female_knee', [.59, .77, .78])
    for i, side, fr in [(1001, 'Right', 'droit'), (1037, 'Left', 'gauche')]:
        add(i, f'{side} femoral articular cartilage', f'Cartilage articulaire fémoral {fr}', 'cartilage', 'cartilage', 'lower', 'female_knee', [.70, .85, .85])
    for side, fr_side, ids in [('Right', 'droit', [989, 990, 991, 992, 993, 994]), ('Left', 'gauche', [1022, 1023, 1024, 1019, 1020, 1021])]:
        names = [('tibial collateral ligament', 'Ligament collatéral tibial'), ('anterior cruciate ligament', 'Ligament croisé antérieur'), ('posterior cruciate ligament', 'Ligament croisé postérieur'), ('anterolateral knee ligament', 'Ligament antérolatéral du genou'), ('fibular collateral ligament', 'Ligament collatéral fibulaire'), ('patellar ligament', 'Ligament patellaire')]
        for i, (en, fr) in zip(ids, names):
            add(i, f'{side} {en}', f'{fr} {fr_side}', 'knee_ligament', 'connective', 'lower', 'female_knee_ligaments', ligament)
    for i, en, fr, layer in [(415, 'Left rectus femoris', 'Muscle droit fémoral gauche', 'muscles'), (418, 'Right rectus femoris', 'Muscle droit fémoral droit', 'muscles'), (416, 'Left quadriceps tendon', 'Tendon quadricipital gauche', 'connective'), (419, 'Right quadriceps tendon', 'Tendon quadricipital droit', 'connective')]:
        add(i, en, fr, 'quadriceps', layer, 'lower', 'female_quadriceps', pink if layer == 'muscles' else ligament)
    # Kidney internal parts remain separate from their capsule and collecting
    # passages, making the native internal anatomy inspectable without doubling
    # a parent exterior over another complete kidney mesh.
    for side, fr_side, capsule, parenchyma, collecting, ureter in [('Left', 'gauche', 565, [*range(569, 580), *range(581, 592), 593, 594], [*range(647, 657), *range(658, 663)], 663), ('Right', 'droit', 596, [599, 600, *range(603, 613), *range(614, 624)], [*range(628, 638), *range(639, 643)], 643)]:
        add(capsule, f'{side} renal capsule', f'Capsule du rein {fr_side}', 'kidney', 'organs', 'abdomen', 'female_urinary', [.57, .28, .23], 'urinary')
        add(parenchyma, f'{side} renal parenchyma', f'Parenchyme du rein {fr_side}', 'kidney', 'organs', 'abdomen', 'female_urinary', [.67, .30, .26], 'urinary')
        add(collecting, f'{side} renal calyces and pelvis', f'Calices et pelvis rénal {fr_side}', 'kidney', 'organs', 'abdomen', 'female_urinary', [.89, .73, .59], 'urinary')
        add(ureter, f'{side} ureter', f'Uretère {fr_side}', 'ureter', 'organs', 'abdomen', 'female_urinary', [.79, .68, .48], 'urinary')
    add(range(665, 669), 'Urinary bladder', 'Vessie', 'bladder', 'organs', 'pelvis', 'female_urinary', [.71, .61, .47], 'urinary')
    digestive = [(487, 'Hepatic flexure of colon', 'Angle colique droit'), (488, 'Transverse colon', 'Côlon transverse'), (489, 'Ascending colon', 'Côlon ascendant'), (490, 'Vermiform appendix', 'Appendice vermiforme'), (491, 'Descending colon', 'Côlon descendant'), ([492, 493], 'Caecum and ileocecal valve', 'Cæcum et valve iléo-cæcale'), (494, 'Rectum', 'Rectum'), (495, 'Sigmoid colon', 'Côlon sigmoïde'), (496, 'Splenic flexure of colon', 'Angle colique gauche'), ([499, 500, 501, 502, 503, 504], 'Duodenum', 'Duodénum'), (505, 'Jejunum', 'Jéjunum'), ([506, 507], 'Ileum', 'Iléon')]
    for ids, en, fr in digestive:
        add(ids, en, fr, 'rectum' if ids == 494 else 'intestine', 'organs', 'pelvis' if ids in (494, 495) else 'abdomen', 'female_digestive', [.75, .47, .34], 'digestive')
    add([*range(521, 531), 534, 535, 537, 538, 539, 541, 542, 543, 544, 545], 'Liver', 'Foie', 'liver', 'organs', 'abdomen', 'female_digestive', [.48, .24, .21], 'digestive')
    add([554, 555, 556, 557, 558], 'Pancreas', 'Pancréas', 'pancreas', 'organs', 'abdomen', 'female_digestive', [.79, .63, .41], 'digestive')
    add(560, 'Gallbladder', 'Vésicule biliaire', 'bile', 'organs', 'abdomen', 'female_digestive', [.37, .53, .30], 'digestive')
    add([510, 511, 512, 514, 516, 519], 'Biliary passages', 'Voies biliaires', 'bile', 'organs', 'abdomen', 'female_digestive', [.56, .67, .33], 'digestive')
    add([515, 517], 'Pancreatic ducts', 'Conduits pancréatiques', 'pancreas', 'organs', 'abdomen', 'female_digestive', [.84, .72, .51], 'digestive')
    heart = [(675, 'Interventricular septum', 'Septum interventriculaire'), (676, 'Left atrium', 'Atrium gauche'), (677, 'Left ventricle', 'Ventricule gauche'), (678, 'Right atrium', 'Atrium droit'), (679, 'Right ventricle', 'Ventricule droit'), ([681, 682, 683, 684, 685], 'Cardiac papillary muscles', 'Muscles papillaires du cœur'), (687, 'Aortic valve', 'Valve aortique'), (688, 'Pulmonary valve', 'Valve pulmonaire'), (689, 'Mitral valve', 'Valve mitrale'), (690, 'Tricuspid valve', 'Valve tricuspide')]
    for ids, en, fr in heart:
        add(ids, en, fr, 'heart', 'organs', 'thorax', 'female_heart', [.62, .27, .30], 'cardiovascular')
    for ids, en, fr in [([851, *range(853, 859)], 'Left upper lung lobe', 'Lobe supérieur du poumon gauche'), (range(860, 866), 'Left lower lung lobe', 'Lobe inférieur du poumon gauche'), ([867, *range(869, 875)], 'Right lower lung lobe', 'Lobe inférieur du poumon droit'), (range(876, 879), 'Right middle lung lobe', 'Lobe moyen du poumon droit'), (range(880, 884), 'Right upper lung lobe', 'Lobe supérieur du poumon droit')]:
        add(ids, en, fr, 'lung', 'organs', 'thorax', 'female_lungs', [.70, .49, .57], 'respiratory')
    add([897, 899], 'Trachea', 'Trachée', 'airway', 'organs', 'neck', 'female_airways', [.70, .64, .58], 'respiratory')
    add([904, 911, 912, *range(914, 924)], 'Left bronchial tree', 'Arbre bronchique gauche', 'airway', 'organs', 'thorax', 'female_airways', [.73, .66, .57], 'respiratory')
    add([902, 929, 930, 931, *range(933, 943)], 'Right bronchial tree', 'Arbre bronchique droit', 'airway', 'organs', 'thorax', 'female_airways', [.73, .66, .57], 'respiratory')
    add(range(945, 950), 'Spleen', 'Rate', 'spleen', 'organs', 'abdomen', 'female_lymphoid', [.49, .28, .42], 'lymphatic')
    add([951, 952], 'Thymus', 'Thymus', 'thymus', 'organs', 'thorax', 'female_lymphoid', [.68, .42, .52], 'lymphatic')
    # Arteries/veins with reviewed identities, leaving unreviewed source
    # crosswalk mistakes untouched and outside this selection.
    vessels = [(694, 'Left uterine artery', 'Artère utérine gauche', 'artery', 'pelvis'), (695, 'Right uterine artery', 'Artère utérine droite', 'artery', 'pelvis'), (697, 'Left uterine vein', 'Veine utérine gauche', 'vein', 'pelvis'), (698, 'Right uterine vein', 'Veine utérine droite', 'vein', 'pelvis'), (723, 'Right renal artery', 'Artère rénale droite', 'artery', 'abdomen'), (724, 'Left renal artery', 'Artère rénale gauche', 'artery', 'abdomen'), (727, 'Left renal vein', 'Veine rénale gauche', 'vein', 'abdomen'), (728, 'Right renal vein', 'Veine rénale droite', 'vein', 'abdomen'), (816, 'Superior vena cava', 'Veine cave supérieure', 'vein', 'thorax'), ([818, 819], 'Inferior vena cava', 'Veine cave inférieure', 'vein', 'abdomen'), ([835, 836, 838, 839], 'Aorta', 'Aorte', 'artery', 'thorax'), (831, 'Left pulmonary artery', 'Artère pulmonaire gauche', 'artery', 'thorax'), (832, 'Pulmonary trunk', 'Tronc pulmonaire', 'artery', 'thorax'), (833, 'Right pulmonary artery', 'Artère pulmonaire droite', 'artery', 'thorax')]
    for ids, en, fr, note, region in vessels:
        add(ids, en, fr, note, 'vascular', region, 'female_arteries' if note == 'artery' else 'female_veins', [.76, .24, .22] if note == 'artery' else [.27, .44, .74])
    # Optical coats, iris and lens supply genuine globes inside the skin.
    for side, fr_side, ids in [('Right', 'droit', [25, 32, 33, 39, 42, 45, 47, 48]), ('Left', 'gauche', [50, 73, 72, 66, 62, 60, 58, 57])]:
        for i, (en, fr, color, opacity) in zip(ids, [('retina', 'Rétine de l’œil', [.72, .39, .31], None), ('sclera', 'Sclère de l’œil', [.87, .86, .79], None), ('choroid', 'Choroïde de l’œil', [.34, .22, .19], None), ('lens', 'Cristallin de l’œil', [.72, .87, .88], .48), ('ciliary body', 'Corps ciliaire de l’œil', [.38, .28, .21], None), ('iris', 'Iris de l’œil', [.37, .44, .37], None), ('cornea', 'Cornée de l’œil', [.70, .87, .90], .20), ('vitreous body', 'Corps vitré de l’œil', [.73, .85, .85], .12)]):
            add(i, f'{side} {en}', f'{fr} {fr_side}', 'eye', 'senses', 'skull', 'female_eyes', color, opacity=opacity)
    for i, en, fr in [(77, 'Right optic nerve', 'Nerf optique droit'), (80, 'Left optic nerve', 'Nerf optique gauche'), (82, 'Optic chiasm', 'Chiasma optique')]:
        add(i, en, fr, 'optic', 'nervous', 'skull', 'female_optic', [.84, .78, .43])
    return rows


GROUPS = {
    'female_skin': ('Skin', 'Peau'), 'female_breast': ('Breasts', 'Seins'),
    'female_reproductive': ('Internal genital organs', 'Organes génitaux internes'),
    'female_pelvic_support': ('Pelvic support tissues', 'Tissus de soutien pelviens'),
    'female_pelvis': ('Pelvic bones', 'Os du bassin'), 'female_spine': ('Vertebral column', 'Colonne vertébrale'),
    'female_lower_bones': ('Lower limb bones', 'Os des membres inférieurs'),
    'female_knee': ('Knee cartilages', 'Cartilages du genou'),
    'female_knee_ligaments': ('Knee ligaments', 'Ligaments du genou'),
    'female_quadriceps': ('Quadriceps region', 'Région quadricipitale'),
    'female_urinary': ('Urinary organs', 'Organes urinaires'),
    'female_digestive': ('Digestive organs', 'Organes digestifs'),
    'female_heart': ('Heart', 'Cœur'), 'female_lungs': ('Lungs', 'Poumons'),
    'female_airways': ('Airways', 'Voies respiratoires'), 'female_lymphoid': ('Lymphoid organs', 'Organes lymphoïdes'),
    'female_arteries': ('Arteries', 'Artères'), 'female_veins': ('Veins', 'Veines'),
    'female_eyes': ('Eyes', 'Yeux'), 'female_optic': ('Optic pathways', 'Voies optiques'),
}


def read_source(path):
    """Read the pinned GLB with strict scene and accessor checks."""
    raw = path.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == SOURCE_SHA256, 'Unexpected HRA female version'
    magic, version, length = struct.unpack_from('<III', raw)
    assert (magic, version, length) == (0x46546C67, 2, len(raw))
    size, kind = struct.unpack_from('<II', raw, 12)
    assert kind == 0x4E4F534A
    gltf = json.loads(raw[20:20 + size])
    bsize, bkind = struct.unpack_from('<II', raw, 20 + size)
    binary = memoryview(raw)[28 + size:]
    assert bkind == 0x004E4942 and bsize == len(binary)
    assert not gltf.get('skins') and not gltf.get('animations') and not gltf.get('extensionsRequired')
    assert len(gltf['buffers']) == 1 and 'uri' not in gltf['buffers'][0]
    assert not any(any(k in n for k in ('matrix', 'translation', 'rotation', 'scale')) for n in gltf['nodes'])

    def accessor(index):
        a = gltf['accessors'][index]
        assert not a.get('sparse') and not a.get('normalized')
        view = gltf['bufferViews'][a['bufferView']]
        width = {'SCALAR': 1, 'VEC3': 3}[a['type']]
        dtype = np.dtype({5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']])
        offset = view.get('byteOffset', 0) + a.get('byteOffset', 0)
        stride = view.get('byteStride', width * dtype.itemsize)
        assert stride >= width * dtype.itemsize
        assert offset + (a['count'] - 1) * stride + width * dtype.itemsize <= view.get('byteOffset', 0) + view['byteLength']
        return np.ndarray((a['count'], width), dtype=dtype, buffer=binary, offset=offset, strides=(stride, dtype.itemsize)).copy()

    return gltf, accessor


def build(source):
    """Return (records with vertices/faces, attribution), for a separate atlas."""
    folder = Path(source)
    if not (folder / 'HRA-female.glb').is_file():
        folder = folder / 'female'
    crosswalk_data = (folder / 'crosswalk.csv').read_bytes()
    assert hashlib.sha256(crosswalk_data).hexdigest() == CROSSWALK_SHA256
    crosswalk = {r['node_name']: r for r in csv.DictReader(crosswalk_data.decode('utf-8-sig').splitlines())}
    gltf, accessor = read_source(folder / 'HRA-female.glb')
    registration = json.loads(REGISTRATION.read_text(encoding='utf8'))
    assert registration['sourceSha256'] == SOURCE_SHA256
    translation = np.array(registration['translation'])
    used, items = set(), []
    for row in definitions():
        node_ids = row['nodes']
        assert len(node_ids) == len(set(node_ids)) and not used.intersection(node_ids), row['en']
        used.update(node_ids)
        vertices, faces, names, ontology = [], [], [], []
        count = 0
        for index in node_ids:
            node = gltf['nodes'][index]
            name = node['name']
            names.append(name)
            mapped = crosswalk.get(name)
            ontology.append({'sourceNode': index, 'sourceObject': name, 'ontologyId': mapped.get('OntologyID') if mapped else None,
                             'ontologyLabel': mapped.get('label') if mapped else None})
            for primitive in gltf['meshes'][node['mesh']]['primitives']:
                assert primitive.get('mode', 4) == 4
                v = accessor(primitive['attributes']['POSITION']).astype(np.float64)
                f = accessor(primitive['indices']).reshape(-1, 3).astype(np.uint32)
                assert np.isfinite(v).all() and f.max() < len(v)
                vertices.append(v + translation)
                faces.append(f + count)
                count += len(v)
        v, f = np.concatenate(vertices), np.concatenate(faces)
        note = NOTES[row['note']]
        identity = 'HRAF_' + str(node_ids[0])
        record = dict(id=identity, sourceName=names[0], sourceObject=names, source='hra-female-v1.5',
                      elements=['HRAF_NODE_' + str(n) for n in node_ids], sourceOntology=ontology,
                      nameEn=row['en'], nameFr=row['fr'], summaryEn=note[0], summaryFr=note[1],
                      wikiEn=note[2], wikiFr=note[3], layer=row['layer'], region=row['region'],
                      category=row['group'], groupEn=GROUPS[row['group']][0], groupFr=GROUPS[row['group']][1],
                      color=row['color'], standard=False, boneCount=0)
        if row['system']:
            record['organSystem'] = row['system']
        if row['opacity'] is not None:
            record['opacity'] = row['opacity']
        items.append((record, v, f))
    excluded = [{'sourceNode': i, 'sourceObject': n['name']} for i, n in enumerate(gltf['nodes']) if 'mesh' in n and i not in used]
    provenance = dict(source='Human Reference Atlas, united-female v1.5', sourceUrl=SOURCE_URL,
                      sourceSha256=SOURCE_SHA256, crosswalkSha256=CROSSWALK_SHA256,
                      authors=['Kristen Browne', 'Heidi Schlehlein'], publisher='HuBMAP',
                      doi='https://doi.org/10.48539/HBM352.BTSQ.586', year=2023,
                      license='CC BY 4.0', licenseUrl='https://creativecommons.org/licenses/by/4.0/',
                      upstreamDescription='Reference organ assembly created using the Visible Human Female dataset, National Library of Medicine.',
                      registration=registration, educationalReferences=references,
                      modifications='Curated anatomical selection; source patches grouped into selectable organs; one common vertical translation; original triangles retained; educational materials and bilingual labels added. No male geometry, sex morph, remeshing or invented surfaces.',
                      sourceMeshNodes=sum('mesh' in n for n in gltf['nodes']), importedSourceNodes=len(used),
                      sourceLabelIssues=[{'sourceNodes': [447, 448], 'issue': 'The raw left/right names and crosswalk for the round uterine ligaments are reversed relative to their coordinates and uterine/ovarian attachments.', 'handling': 'Kept as one bilateral structure; raw names and ontology IDs preserved without asserting the erroneous laterality.'}],
                      renderedStructures=len(items), triangles=sum(len(f) for _, _, f in items), excludedSourceNodes=excluded,
                      exclusions=['Placenta, amnion and umbilical cord are pregnancy reference models, excluded from the adult baseline.',
                                  'Separate Yao lymph-node specimen is excluded.',
                                  'Allen brain subdivisions and unreviewed small vascular/airway/eye annotations are outside this curated subset.',
                                  'Uterovesical pouch and isolated abdominal tubal ostium reference surfaces are excluded, not presented as solid organs.',
                                  'Skull, ribs, shoulder girdle, upper-limb skeleton, hand/foot bones, most muscles and peripheral nerves are not supplied by this reference organ set.',
                                  'Clitoris, labia, female urethra and pelvic-floor muscles have no separate source meshes. The external perineal skin is smooth and does not depict a detailed vulva.'])
    return items, provenance
