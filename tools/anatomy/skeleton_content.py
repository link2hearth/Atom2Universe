"""Curated bilingual nomenclature and short, original educational descriptions.

FMA identities come from BodyParts3D. Reference links lead to the corresponding
Wikipedia topic; no Wikipedia article text or images are redistributed.
"""
import re

# source term: French term, grammatical gender, region, English/French article titles
TERMS = {
    "frontal bone": ("Os frontal", "m", "skull", "Frontal bone", "Os frontal"),
    "parietal bone": ("Os pariétal", "m", "skull", "Parietal bone", "Os pariétal"),
    "temporal bone": ("Os temporal", "m", "skull", "Temporal bone", "Os temporal"),
    "occipital bone": ("Os occipital", "m", "skull", "Occipital bone", "Os occipital"),
    "sphenoid bone": ("Os sphénoïde", "m", "skull", "Sphenoid bone", "Os sphénoïde"),
    "ethmoid": ("Os ethmoïde", "m", "skull", "Ethmoid bone", "Os ethmoïde"),
    "maxilla": ("Maxillaire", "m", "skull", "Maxilla", "Maxillaire"),
    "mandible": ("Mandibule", "f", "skull", "Mandible", "Mandibule"),
    "zygomatic bone": ("Os zygomatique", "m", "skull", "Zygomatic bone", "Os zygomatique"),
    "nasal bone": ("Os nasal", "m", "skull", "Nasal bone", "Os nasal"),
    "lacrimal bone": ("Os lacrymal", "m", "skull", "Lacrimal bone", "Os lacrymal"),
    "palatine bone": ("Os palatin", "m", "skull", "Palatine bone", "Os palatin"),
    "inferior nasal concha": ("Cornet nasal inférieur", "m", "skull", "Inferior nasal concha", "Cornet nasal inférieur"),
    "vomer": ("Vomer", "m", "skull", "Vomer", "Vomer"),
    "hyoid bone": ("Os hyoïde", "m", "neck", "Hyoid bone", "Os hyoïde"),
    "malleus": ("Marteau", "m", "skull", "Malleus", "Marteau (os)"),
    "incus": ("Enclume", "f", "skull", "Incus", "Enclume (os)"),
    "stapes": ("Étrier", "m", "skull", "Stapes", "Étrier (os)"),
    "atlas": ("Atlas · C1", "m", "spine", "Atlas (anatomy)", "Atlas (os)"),
    "axis": ("Axis · C2", "m", "spine", "Axis (anatomy)", "Axis (os)"),
    "sacrum": ("Sacrum", "m", "spine", "Sacrum", "Sacrum"),
    "coccyx": ("Coccyx", "m", "spine", "Coccyx", "Coccyx"),
    "sternum": ("Sternum", "m", "thorax", "Sternum", "Sternum"),
    "clavicle": ("Clavicule", "f", "upper", "Clavicle", "Clavicule"),
    "scapula": ("Scapula (omoplate)", "f", "upper", "Scapula", "Scapula"),
    "humerus": ("Humérus", "m", "upper", "Humerus", "Humérus"),
    "radius": ("Radius", "m", "upper", "Radius (bone)", "Radius (os)"),
    "ulna": ("Ulna (cubitus)", "m", "upper", "Ulna", "Ulna"),
    "scaphoid": ("Scaphoïde", "m", "hands", "Scaphoid bone", "Os scaphoïde"),
    "lunate": ("Lunatum (semi-lunaire)", "m", "hands", "Lunate bone", "Os lunatum"),
    "triquetral": ("Triquetrum (pyramidal)", "m", "hands", "Triquetral bone", "Os triquetrum"),
    "pisiform": ("Pisiforme", "m", "hands", "Pisiform bone", "Os pisiforme"),
    "trapezium": ("Trapèze", "m", "hands", "Trapezium (bone)", "Os trapèze"),
    "trapezoid": ("Trapézoïde", "m", "hands", "Trapezoid bone", "Os trapézoïde"),
    "capitate": ("Capitatum (grand os)", "m", "hands", "Capitate bone", "Os capitatum"),
    "hamate": ("Hamatum (os crochu)", "m", "hands", "Hamate bone", "Os hamatum"),
    "hip bone": ("Os coxal", "m", "pelvis", "Hip bone", "Os coxal"),
    "femur": ("Fémur", "m", "lower", "Femur", "Fémur"),
    "patella": ("Patella (rotule)", "f", "lower", "Patella", "Patella"),
    "tibia": ("Tibia", "m", "lower", "Tibia", "Tibia"),
    "fibula": ("Fibula (péroné)", "f", "lower", "Fibula", "Fibula"),
    "talus": ("Talus (astragale)", "m", "feet", "Talus bone", "Talus (os)"),
    "calcaneus": ("Calcanéus", "m", "feet", "Calcaneus", "Calcanéus"),
    "cuboid bone": ("Cuboïde", "m", "feet", "Cuboid bone", "Os cuboïde"),
    "medial cuneiform bone": ("Cunéiforme médial", "m", "feet", "Cuneiform bones", "Os cunéiformes"),
    "intermediate cuneiform bone": ("Cunéiforme intermédiaire", "m", "feet", "Cuneiform bones", "Os cunéiformes"),
    "lateral cuneiform bone": ("Cunéiforme latéral", "m", "feet", "Cuneiform bones", "Os cunéiformes"),
}

# Each entry is a short original description, not an extract from a third-party article.
summaries = {
    "frontal_bone": ("Forms the forehead and part of the roof of the eye sockets. It contributes to the protective wall around the brain.", "Forme le front et une partie du toit des orbites. Il participe à la paroi osseuse qui protège le cerveau."),
    "parietal_bone": ("Paired bone forming much of the roof and sides of the cranial vault. The two parietal bones meet at the sagittal suture.", "Os pair formant une grande partie de la voûte du crâne. Les deux pariétaux se rejoignent à la suture sagittale."),
    "temporal_bone": ("Forms part of the side and base of the skull. It houses the structures of hearing and balance and articulates with the mandible.", "Participe au côté et à la base du crâne. Il abrite les structures de l’audition et de l’équilibre et s’articule avec la mandibule."),
    "occipital_bone": ("Forms the back and part of the base of the skull. Its large opening, the foramen magnum, connects the cranial cavity to the vertebral canal.", "Forme l’arrière et une partie de la base du crâne. Le foramen magnum relie la cavité crânienne au canal vertébral."),
    "sphenoid_bone": ("Central bone of the skull base, contributing to the orbits. Its sella turcica contains the pituitary fossa.", "Os central de la base du crâne, participant aux orbites. Sa selle turcique comprend la fosse hypophysaire."),
    "ethmoid": ("Located between the orbits, it contributes to the nasal cavity and nasal septum. Its cribriform plate is crossed by olfactory nerve fibres.", "Situé entre les orbites, il participe aux fosses nasales et à leur cloison. Sa lame criblée laisse passer les fibres des nerfs olfactifs."),
    "maxilla": ("Paired bone of the upper jaw. It carries the upper teeth and contributes to the hard palate and the floor of the orbit.", "Os pair de la mâchoire supérieure. Il porte les dents supérieures et participe au palais osseux et au plancher de l’orbite."),
    "mandible": ("Bone of the lower jaw, carrying the lower teeth. Its joints with the temporal bones allow opening and movement of the jaw.", "Os de la mâchoire inférieure, portant les dents inférieures. Ses articulations avec les os temporaux permettent les mouvements de la mâchoire."),
    "zygomatic_bone": ("Forms the prominence of the cheek and part of the orbit. It also contributes to the zygomatic arch.", "Forme le relief de la pommette et une partie de l’orbite. Il participe également à l’arcade zygomatique."),
    "nasal_bone": ("One of the two small bones forming the bony bridge of the nose. Much of the projecting nose is supported by cartilage.", "L’un des deux petits os formant la partie osseuse du dos du nez. Une grande partie du nez saillant est soutenue par du cartilage."),
    "lacrimal_bone": ("Small bone on the medial wall of the orbit, associated with the passage that drains tears towards the nasal cavity.", "Petit os de la paroi médiale de l’orbite, associé au passage qui conduit les larmes vers les fosses nasales."),
    "palatine_bone": ("Contributes to the rear of the hard palate and to the walls of the nasal cavity and orbit.", "Participe à l’arrière du palais osseux et aux parois des fosses nasales et de l’orbite."),
    "inferior_nasal_concha": ("Curved bone projecting into the nasal cavity. Its mucosal covering helps condition inhaled air. It is a separate bone from the ethmoid.", "Lame osseuse recourbée dans la fosse nasale. La muqueuse qui la recouvre contribue au traitement de l’air inspiré. C’est un os indépendant de l’ethmoïde."),
    "vomer": ("Thin midline bone forming part of the nasal septum, which separates the right and left nasal cavities.", "Os mince médian constituant une partie de la cloison séparant les fosses nasales droite et gauche."),
    "hyoid_bone": ("Located in the neck below the mandible. Suspended by muscles and ligaments, it supports attachments involved in tongue movement and swallowing.", "Situé dans le cou, sous la mandibule. Suspendu par des muscles et des ligaments, il sert d’ancrage lors des mouvements de la langue et de la déglutition."),
    "malleus": ("Middle-ear ossicle attached to the eardrum. It transmits vibration to the incus. Its individual 3D mesh is absent from this dataset.", "Osselet de l’oreille moyenne lié au tympan. Il transmet les vibrations à l’enclume. Son modèle 3D individuel est absent de ce jeu de données."),
    "incus": ("Middle-ear ossicle between the malleus and stapes, forming part of the sound-transmission chain. Its individual 3D mesh is absent from this dataset.", "Osselet de l’oreille moyenne placé entre le marteau et l’étrier, dans la chaîne de transmission du son. Son modèle 3D individuel est absent de ce jeu de données."),
    "stapes": ("Middle-ear ossicle whose footplate transmits vibration at the oval window of the inner ear. Its individual 3D mesh is absent from this dataset.", "Osselet de l’oreille moyenne dont la base transmet les vibrations à la fenêtre ovale de l’oreille interne. Son modèle 3D individuel est absent de ce jeu de données."),
    "atlas": ("First cervical vertebra, supporting the skull. Its ring-like shape differs from a typical vertebra; it articulates with the occipital bone and the axis.", "Première vertèbre cervicale, supportant le crâne. Sa forme en anneau diffère d’une vertèbre typique ; elle s’articule avec l’occipital et l’axis."),
    "axis": ("Second cervical vertebra. Its upward projection, the dens, forms a pivot involved in rotation of the atlas and head.", "Deuxième vertèbre cervicale. Sa dent, dirigée vers le haut, forme un pivot participant à la rotation de l’atlas et de la tête."),
    "cervical": ("One of the seven vertebrae of the neck. The cervical spine supports the head, allows movement and surrounds the upper vertebral canal.", "L’une des sept vertèbres du cou. Le rachis cervical soutient la tête, permet sa mobilité et entoure la partie haute du canal vertébral."),
    "thoracic": ("One of the twelve thoracic vertebrae. These vertebrae articulate with the ribs and form the spinal part of the thoracic cage.", "L’une des douze vertèbres thoraciques. Ces vertèbres s’articulent avec les côtes et forment la partie vertébrale de la cage thoracique."),
    "lumbar": ("One of the five lumbar vertebrae in the lower back. Their large bodies bear substantial load above the sacrum.", "L’une des cinq vertèbres lombaires du bas du dos. Leurs corps volumineux supportent une charge importante au-dessus du sacrum."),
    "sacrum": ("Bone formed by fusion of five sacral vertebrae. It links the vertebral column to the hip bones through the sacroiliac joints.", "Os issu de la fusion de cinq vertèbres sacrées. Il relie la colonne aux os coxaux par les articulations sacro-iliaques."),
    "coccyx": ("Terminal part of the vertebral column, below the sacrum, formed from a variable number of small vertebrae. No separately identified mesh is supplied in this dataset.", "Extrémité de la colonne sous le sacrum, issue d’un nombre variable de petites vertèbres. Ce jeu de données ne fournit pas de modèle identifié séparément."),
    "sternum": ("Midline bone at the front of the chest. It comprises the manubrium, body and xiphoid process. Here the three source meshes are selected together as one adult bone.", "Os médian à l’avant du thorax. Il comprend le manubrium, le corps et le processus xiphoïde. Les trois modèles sources sont ici regroupés en un os adulte."),
    "rib_true": ("A rib from pairs 1–7. Its costal cartilage attaches directly to the sternum. Costal cartilage is not shown in this bone layer.", "Côte appartenant aux paires 1 à 7. Son cartilage costal rejoint directement le sternum. Les cartilages costaux ne sont pas affichés dans cette couche osseuse."),
    "rib_false": ("A rib from pairs 8–10. Its cartilage joins the cartilage above rather than attaching directly to the sternum. The cartilage is not shown here.", "Côte appartenant aux paires 8 à 10. Son cartilage rejoint celui de la côte supérieure au lieu de se fixer directement au sternum. Le cartilage n’est pas affiché ici."),
    "rib_floating": ("A rib from pairs 11–12. Its anterior end has no attachment to the sternum; posteriorly it articulates with the thoracic spine.", "Côte appartenant aux paires 11 et 12. Son extrémité antérieure n’est pas reliée au sternum ; en arrière, elle s’articule avec le rachis thoracique."),
    "clavicle": ("Links the sternum to the scapula. It helps hold the shoulder away from the thorax and transmits forces from the upper limb.", "Relie le sternum à la scapula. Elle contribue à maintenir l’épaule à distance du thorax et transmet les efforts du membre supérieur."),
    "scapula": ("Flat bone at the back of the thorax. Its glenoid cavity receives the head of the humerus; its surfaces provide many muscle attachments.", "Os plat à l’arrière du thorax. Sa cavité glénoïdale reçoit la tête de l’humérus ; ses surfaces offrent de nombreuses insertions musculaires."),
    "humerus": ("Long bone of the arm between shoulder and elbow. It articulates with the scapula above and with the radius and ulna below.", "Os long du bras, entre épaule et coude. Il s’articule avec la scapula en haut, puis avec le radius et l’ulna en bas."),
    "radius": ("Forearm bone on the thumb side in anatomical position. It participates in the wrist joint and rotates around the ulna during pronation and supination.", "Os de l’avant-bras situé du côté du pouce en position anatomique. Il participe au poignet et tourne autour de l’ulna lors de la pronation et de la supination."),
    "ulna": ("Forearm bone on the little-finger side in anatomical position. Its olecranon forms the bony point of the elbow.", "Os de l’avant-bras du côté du petit doigt en position anatomique. Son olécrâne forme la pointe osseuse du coude."),
    "carpal": ("One of the eight carpal bones of each wrist. Together they form two rows between the forearm and the metacarpals.", "L’un des huit os du carpe de chaque poignet. Ils forment deux rangées entre l’avant-bras et les métacarpiens."),
    "scaphoid": ("Carpal bone on the thumb side of the proximal row. It articulates with the radius and connects the two carpal rows through its articulations.", "Os du carpe du côté du pouce, dans la rangée proximale. Il s’articule avec le radius et relie les deux rangées du carpe par ses articulations."),
    "lunate": ("Central bone of the proximal carpal row, between the scaphoid and triquetral. It articulates with the radius above and the capitate below.", "Os central de la rangée proximale du carpe, entre scaphoïde et triquetrum. Il s’articule avec le radius en haut et le capitatum en bas."),
    "triquetral": ("Carpal bone on the little-finger side of the proximal row. The pisiform lies on its palmar surface.", "Os du côté du petit doigt dans la rangée proximale du carpe. Le pisiforme se place sur sa face palmaire."),
    "pisiform": ("Small sesamoid carpal bone within the tendon of flexor carpi ulnaris. It lies in front of the triquetral and is included in the conventional 206 bones.", "Petit os sésamoïde du carpe situé dans le tendon du fléchisseur ulnaire du carpe. Il se place en avant du triquetrum et fait partie des 206 os conventionnels."),
    "trapezium": ("Carpal bone at the base of the thumb. Its saddle-shaped joint with the first metacarpal contributes to the mobility of the thumb.", "Os du carpe à la base du pouce. Son articulation en selle avec le premier métacarpien contribue à la mobilité du pouce."),
    "trapezoid": ("Small bone of the distal carpal row, between the trapezium and capitate. It lies mainly beneath the base of the second metacarpal.", "Petit os de la rangée distale du carpe, entre trapèze et capitatum. Il se place principalement sous la base du deuxième métacarpien."),
    "capitate": ("Largest carpal bone, situated near the centre of the wrist. It belongs to the distal row and aligns mainly with the third metacarpal.", "Plus volumineux os du carpe, situé près du centre du poignet. Il appartient à la rangée distale et s’aligne principalement avec le troisième métacarpien."),
    "hamate": ("Bone on the little-finger side of the distal carpal row. Its palmar hook provides an attachment for the flexor retinaculum.", "Os du côté du petit doigt dans la rangée distale du carpe. Son crochet palmaire donne une insertion au rétinaculum des fléchisseurs."),
    "metacarpal": ("One of the five bones of the palm. Numbering runs from I at the thumb to V at the little finger. It lies between the carpus and a proximal phalanx.", "L’un des cinq os de la paume. La numérotation va de I au pouce à V au petit doigt. Il se place entre le carpe et une phalange proximale."),
    "finger_phalanx": ("A finger bone. Each hand has fourteen phalanges: two in the thumb and three in each other finger. Proximal, middle and distal indicate increasing distance from the palm.", "Os d’un doigt. Chaque main possède quatorze phalanges : deux au pouce et trois aux autres doigts. Proximale, moyenne et distale indiquent l’éloignement croissant de la paume."),
    "hip_bone": ("Pelvic bone formed by fusion of the ilium, ischium and pubis. Its acetabulum receives the femoral head; posteriorly it meets the sacrum.", "Os du bassin formé par la fusion de l’ilium, de l’ischium et du pubis. Son acétabulum reçoit la tête du fémur ; en arrière, il rejoint le sacrum."),
    "femur": ("Long bone of the thigh between hip and knee. Its head articulates with the acetabulum; its lower end articulates with the tibia and patella.", "Os long de la cuisse, entre hanche et genou. Sa tête s’articule avec l’acétabulum ; son extrémité inférieure avec le tibia et la patella."),
    "patella": ("Sesamoid bone within the quadriceps tendon at the front of the knee. It contributes to the mechanical action of the knee extensor apparatus.", "Os sésamoïde situé dans le tendon du quadriceps, à l’avant du genou. Elle contribue à l’action mécanique de l’appareil extenseur du genou."),
    "tibia": ("Medial bone of the leg and its main weight-bearing bone. It articulates with the femur at the knee and the talus at the ankle.", "Os médial de la jambe, assurant l’essentiel de la transmission du poids. Il s’articule avec le fémur au genou et avec le talus à la cheville."),
    "fibula": ("Slender bone on the lateral side of the leg. Its lower end forms the lateral malleolus and contributes to ankle stability.", "Os fin du côté latéral de la jambe. Son extrémité inférieure forme la malléole latérale et participe à la stabilité de la cheville."),
    "talus": ("Tarsal bone between the leg and the rest of the foot. It receives load from the tibia and articulates with the calcaneus and navicular.", "Os du tarse entre la jambe et le reste du pied. Il reçoit la charge du tibia et s’articule avec le calcanéus et le naviculaire."),
    "calcaneus": ("Heel bone, beneath the talus. Its posterior surface receives the calcaneal (Achilles) tendon.", "Os du talon, sous le talus. Sa face postérieure reçoit le tendon calcanéen, également appelé tendon d’Achille."),
    "tarsal": ("One of the seven tarsal bones of each foot. The tarsus links the ankle region to the metatarsals and contributes to the arches of the foot.", "L’un des sept os du tarse de chaque pied. Le tarse relie la région de la cheville aux métatarsiens et participe aux voûtes du pied."),
    "navicular": ("Medial tarsal bone between the talus and the three cuneiform bones. It contributes to the medial longitudinal arch of the foot.", "Os médial du tarse, entre le talus et les trois cunéiformes. Il participe à la voûte longitudinale médiale du pied."),
    "cuboid_bone": ("Lateral tarsal bone between the calcaneus and the fourth and fifth metatarsals. It contributes to the lateral column of the foot.", "Os latéral du tarse, entre le calcanéus et les quatrième et cinquième métatarsiens. Il participe à la colonne latérale du pied."),
    "medial_cuneiform_bone": ("Largest of the three cuneiforms. It lies between the navicular and the first metatarsal, on the big-toe side of the foot.", "Le plus volumineux des trois cunéiformes. Il se place entre le naviculaire et le premier métatarsien, du côté du gros orteil."),
    "intermediate_cuneiform_bone": ("Middle and smallest cuneiform, between the medial and lateral cuneiforms. It lies behind the base of the second metatarsal.", "Cunéiforme central, le plus petit des trois, entre les cunéiformes médial et latéral. Il se place derrière la base du deuxième métatarsien."),
    "lateral_cuneiform_bone": ("Lateral member of the three cuneiform bones. It lies between the navicular and the third metatarsal, next to the cuboid.", "Le plus latéral des trois cunéiformes. Il se place entre le naviculaire et le troisième métatarsien, à côté du cuboïde."),
    "metatarsal": ("One of the five metatarsal bones. Numbering runs from I at the big toe to V at the little toe. It lies between the tarsus and a proximal phalanx.", "L’un des cinq métatarsiens. La numérotation va de I au gros orteil à V au petit orteil. Il se place entre le tarse et une phalange proximale."),
    "toe_phalanx": ("A toe bone. Each foot has fourteen phalanges: two in the big toe and three in each other toe. They form the articulated skeleton of the toes.", "Os d’un orteil. Chaque pied possède quatorze phalanges : deux au gros orteil et trois aux autres orteils. Elles constituent leur squelette articulé."),
    "sesamoid": ("Two sesamoid bones beneath the head of the first metatarsal, shown as one selectable group. These additional sesamoids are outside the conventional 206-bone count.", "Deux os sésamoïdes sous la tête du premier métatarsien, réunis en un groupe sélectionnable. Ces sésamoïdes supplémentaires ne sont pas inclus dans le décompte conventionnel de 206 os."),
    "incisor": ("Permanent front tooth with an edge adapted to cutting food. Teeth contain enamel and dentine; they are not bones and are counted separately in this atlas.", "Dent permanente antérieure présentant un bord adapté à la coupe des aliments. Les dents contiennent de l’émail et de la dentine ; ce ne sont pas des os et elles sont comptées séparément."),
    "canine": ("Permanent tooth between the incisors and premolars, with a pointed crown. Teeth are shown separately from the skeletal bone count.", "Dent permanente placée entre incisives et prémolaires, à couronne pointue. Les dents sont présentées séparément du décompte des os."),
    "premolar": ("Permanent tooth between the canine and molars, contributing to crushing food. There are two premolars per quadrant in the usual adult dentition.", "Dent permanente située entre canine et molaires, participant à l’écrasement des aliments. La denture adulte habituelle comprend deux prémolaires par quadrant."),
    "molar": ("Posterior tooth with a broad chewing surface. This dataset includes the first and second permanent molars; wisdom teeth are not supplied.", "Dent postérieure à large surface masticatoire. Ce jeu de données comprend les premières et deuxièmes molaires permanentes ; les dents de sagesse ne sont pas fournies."),
}

ORDINALS = {name: i + 1 for i, name in enumerate(("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth"))}
ROMAN = ["", "I", "II", "III", "IV", "V"]


def describe(name):
    side = "left" if "left" in name else "right" if "right" in name else ""
    fr_side = "gauche" if side == "left" else "droit"
    base = re.sub(r"^(left|right) ", "", name)
    if base in TERMS:
        french, gender, region, wiki_en, wiki_fr = TERMS[base]
        if side:
            french += " " + ("droite" if side == "right" and gender == "f" else fr_side)
        kind = base.replace(" ", "_")
        return name.capitalize(), french, kind, region, wiki_en, wiki_fr
    if "vertebra" in name:
        number = next(v for k, v in ORDINALS.items() if name.startswith(k + " "))
        kind = next(k for k in ("cervical", "thoracic", "lumbar") if k in name)
        code = {"cervical": "C", "thoracic": "T", "lumbar": "L"}[kind] + str(number)
        return f"{name.capitalize()} · {code}", f"Vertèbre {code}", kind, "spine", kind.capitalize() + " vertebrae", "Vertèbre"
    if name.endswith(" rib"):
        number = next(v for k, v in ORDINALS.items() if f" {k} " in name)
        kind = "rib_true" if number <= 7 else "rib_false" if number <= 10 else "rib_floating"
        return name.capitalize(), f"Côte {number} {fr_side if side == 'left' else 'droite'}", kind, "thorax", "Rib", "Côte (anatomie)"
    if "metacarpal" in name or "metatarsal" in name:
        number = next(v for k, v in ORDINALS.items() if f" {k} " in name)
        kind = "metacarpal" if "metacarpal" in name else "metatarsal"
        french = "Métacarpien" if kind == "metacarpal" else "Métatarsien"
        return name.capitalize(), f"{french} {ROMAN[number]} {fr_side}", kind, "hands" if kind == "metacarpal" else "feet", kind.capitalize() + " bones", "Métacarpe" if kind == "metacarpal" else "Métatarse"
    if "phalanx" in name:
        level = name.split()[0]
        french = "Phalange " + {"proximal": "proximale", "middle": "moyenne", "distal": "distale"}[level]
        digits = {"thumb": "du pouce", "index finger": "de l’index", "middle finger": "du majeur", "ring finger": "de l’annulaire", "little finger": "du petit doigt", "big toe": "du gros orteil", "second toe": "du 2e orteil", "third toe": "du 3e orteil", "fourth toe": "du 4e orteil", "little toe": "du petit orteil"}
        digit = next(fr for en, fr in digits.items() if name.endswith(en))
        toe = "toe" in name
        return name.capitalize(), f"{french} {digit} {fr_side}", "toe_phalanx" if toe else "finger_phalanx", "feet" if toe else "hands", "Phalanx bone", "Phalange (anatomie)"
    if "navicular bone" in name:
        return name.capitalize(), f"Os naviculaire {fr_side}", "navicular", "feet", "Navicular bone", "Os naviculaire"
    if "sesamoid bones" in name:
        return name.capitalize(), f"Sésamoïdes du pied {fr_side}", "sesamoid", "feet", "Sesamoid bone", "Os sésamoïde"
    if "tooth" in name:
        kind = next(k for k in ("incisor", "canine", "premolar", "molar") if k in name)
        upper = "upper" in name
        quadrant = (1 if side == "right" else 2) if upper else (4 if side == "right" else 3)
        number = {"incisor": 1 if "central" in name else 2, "canine": 3,
                  "premolar": 4 if "first" in name else 5, "molar": 6 if "first" in name else 7}[kind]
        fdi = quadrant * 10 + number
        french = {"incisor": "Incisive centrale" if number == 1 else "Incisive latérale", "canine": "Canine", "premolar": "Première prémolaire" if number == 4 else "Deuxième prémolaire", "molar": "Première molaire" if number == 6 else "Deuxième molaire"}[kind]
        french += " supérieure" if upper else " inférieure"
        french += " droite" if side == "right" else " gauche"
        english = name.replace("secondary ", "permanent ").capitalize()
        return f"{english} · {fdi}", f"{french} · {fdi}", kind, "teeth", {"incisor": "Incisor", "canine": "Canine tooth", "premolar": "Premolar", "molar": "Molar (tooth)"}[kind], {"incisor": "Incisive", "canine": "Canine (dent)", "premolar": "Prémolaire", "molar": "Molaire (dent)"}[kind]
    raise ValueError("Untranslated structure: " + name)
