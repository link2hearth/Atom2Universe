"""Audited BodyParts3D ocular surfaces and external ears; no invented globes.

The original meshes include a few nearly degenerate fragments in the opposite
orbit. Only the four explicitly audited files are cleaned, with strict guards.
Transparent materials are illustrative and do not simulate optical refraction.
"""
import hashlib

# Identity | original source term | French label | family | category | OBJ elements
_TERMS = '''
FMA58271|right sclera|Sclère droite|sclera|eyeballs|FJ1368
FMA58272|left sclera|Sclère gauche|sclera|eyeballs|FJ1317
FMA58239|right cornea|Cornée droite|cornea|eyeballs|FJ1340
FMA58240|left cornea|Cornée gauche|cornea|eyeballs|FJ1289
FMA58236|right iris|Iris droit|iris|eyeballs|FJ1348
FMA58237|left iris|Iris gauche|iris|eyeballs|FJ1297
FMA58242|right lens|Cristallin droit|lens|eyeballs|FJ1356
FMA58243|left lens|Cristallin gauche|lens|eyeballs|FJ1305
FMA58299|right choroid|Choroïde droite|choroid|eyeballs|FJ1336,FJ1337
FMA58300|left choroid|Choroïde gauche|choroid|eyeballs|FJ1285,FJ1286
FMA58607|optic part of right retina|Partie optique de la rétine droite|retina|eyeballs|FJ1367
FMA58608|optic part of left retina|Partie optique de la rétine gauche|retina|eyeballs|FJ1316
FMA58483|right corona ciliaris|Couronne ciliaire droite|ciliary_crown|eyeballs|FJ1338
FMA58484|left corona ciliaris|Couronne ciliaire gauche|ciliary_crown|eyeballs|FJ1287
FMA58839|suspensory ligament of right lens|Zonule ciliaire droite|zonule|eyeballs|FJ1371
FMA58840|suspensory ligament of left lens|Zonule ciliaire gauche|zonule|eyeballs|FJ1320
FMA58828|right vitreous body|Corps vitré droit|vitreous|eyeballs|FJ1382
FMA58829|left vitreous body|Corps vitré gauche|vitreous|eyeballs|FJ1331
FMA58081|anterior chamber of right eyeball|Chambre antérieure de l’œil droit|anterior_chamber|eyeballs|FJ1332
FMA58082|anterior chamber of left eyeball|Chambre antérieure de l’œil gauche|anterior_chamber|eyeballs|FJ1282
FMA59102|right lacrimal gland|Glande lacrymale droite|lacrimal_gland|lacrimal|FJ1350
FMA59103|left lacrimal gland|Glande lacrymale gauche|lacrimal_gland|lacrimal|FJ1299
FMA59545|right lacrimal sac|Sac lacrymal droit|lacrimal_sac|lacrimal|FJ1360
FMA59546|left lacrimal sac|Sac lacrymal gauche|lacrimal_sac|lacrimal|FJ1309
FMA59555|right nasolacrimal duct|Conduit lacrymonasal droit|nasolacrimal|lacrimal|FJ1353
FMA59556|left nasolacrimal duct|Conduit lacrymonasal gauche|nasolacrimal|lacrimal|FJ1302
FMA59582|right lacrimal canaliculus|Canalicules lacrymaux droits|lacrimal_canaliculi|lacrimal|FJ1349
FMA59583|left lacrimal canaliculus|Canalicules lacrymaux gauches|lacrimal_canaliculi|lacrimal|FJ1298
FMA59541|right lacrimal lake|Lac lacrymal droit|lacrimal_lake|lacrimal|FJ1352
FMA59542|left lacrimal lake|Lac lacrymal gauche|lacrimal_lake|lacrimal|FJ1301
FMA59091|tarsal plate of right upper eyelid|Tarse palpébral supérieur droit|tarsal_plate|ocular_support|FJ1375
FMA59092|tarsal plate of left upper eyelid|Tarse palpébral supérieur gauche|tarsal_plate|ocular_support|FJ1324
FMA59089|tarsal plate of right lower eyelid|Tarse palpébral inférieur droit|tarsal_plate|ocular_support|FJ1379
FMA59090|tarsal plate of left lower eyelid|Tarse palpébral inférieur gauche|tarsal_plate|ocular_support|FJ1328
FMA49144|check ligament of right lateral rectus|Aileron du droit latéral droit|check_ligament|ocular_support|FJ1334
FMA49145|check ligament of left lateral rectus|Aileron du droit latéral gauche|check_ligament|ocular_support|FJ1284
FMA49147|check ligament of right medial rectus|Aileron du droit médial droit|check_ligament|ocular_support|FJ1335
FMA49148|check ligament of left medial rectus|Aileron du droit médial gauche|check_ligament|ocular_support|FJ1292
FMA49067|trochlea of right superior oblique|Trochlée de l’oblique supérieur droit|trochlea|ocular_support|FJ1380
FMA49068|trochlea of left superior oblique|Trochlée de l’oblique supérieur gauche|trochlea|ocular_support|FJ1329
FMA54159|tendon of right levator palpebrae superioris|Tendon du releveur de la paupière supérieure droite|levator_tendon|ocular_support|FJ1343
FMA52781|external ear|Pavillons des oreilles|external_ear|external_ears|FJ2811
'''
TERMS = {r.split('|')[0]: tuple(r.split('|')[1:]) for r in _TERMS.strip().splitlines()}
BY_NAME = {v[0]: v[1:] for v in TERMS.values()}

groups = {
    'eyeballs': ('Eyeballs', 'Globes oculaires'),
    'lacrimal': ('Lacrimal apparatus', 'Appareil lacrymal'),
    'ocular_support': ('Eyelid and orbital support', 'Soutien palpébral et orbitaire'),
    'external_ears': ('External ears', 'Oreilles externes'),
}

WIKI = {
    'sclera': ('Sclera', 'Sclère'), 'cornea': ('Cornea', 'Cornée'),
    'iris': ('Iris (anatomy)', 'Iris (anatomie)'), 'lens': ('Lens (anatomy)', 'Cristallin'),
    'choroid': ('Choroid', 'Choroïde'), 'retina': ('Retina', 'Rétine'),
    'ciliary_crown': ('Ciliary body', 'Corps ciliaire'), 'zonule': ('Ciliary zonule', 'Zonule ciliaire'),
    'vitreous': ('Vitreous body', 'Corps vitré'),
    'anterior_chamber': ('Anterior chamber of eyeball', 'Chambre antérieure de l’œil'),
    'lacrimal_gland': ('Lacrimal gland', 'Glande lacrymale'),
    'lacrimal_sac': ('Lacrimal sac', 'Sac lacrymal'),
    'nasolacrimal': ('Nasolacrimal duct', 'Conduit lacrymonasal'),
    'lacrimal_canaliculi': ('Lacrimal canaliculi', 'Canalicule lacrymal'),
    'lacrimal_lake': ('Lacrimal lake', 'Appareil lacrymal'),
    'tarsal_plate': ('Tarsus (eyelids)', 'Tarse palpébral'),
    'check_ligament': ('Orbit (anatomy)', 'Orbite (anatomie)'),
    'trochlea': ('Trochlea of superior oblique', 'Muscle oblique supérieur du bulbe de l’œil'),
    'levator_tendon': ('Levator palpebrae superioris muscle', 'Muscle releveur de la paupière supérieure'),
    'external_ear': ('Auricle (anatomy)', 'Pavillon de l’oreille'),
}

# Colours distinguish tissues; the fluid and optical surfaces remain selectable.
# Dark retinal/choroidal colours keep the iris aperture visually dark without
# adding a fictional pupil disc. Transparency is not a physical optical model.
COLORS = {
    'sclera': [.94, .93, .88], 'cornea': [.78, .89, .96],
    'iris': [.30, .21, .12], 'lens': [.80, .88, .91],
    'choroid': [.13, .055, .04], 'retina': [.14, .035, .025],
    'ciliary_crown': [.43, .19, .15], 'zonule': [.83, .84, .72],
    'vitreous': [.76, .87, .93], 'anterior_chamber': [.74, .86, .95],
    'lacrimal_gland': [.83, .58, .50], 'lacrimal_sac': [.75, .53, .46],
    'nasolacrimal': [.81, .66, .53], 'lacrimal_canaliculi': [.79, .58, .50],
    'lacrimal_lake': [.68, .82, .89], 'tarsal_plate': [.83, .68, .61],
    'check_ligament': [.87, .85, .71], 'trochlea': [.80, .80, .66],
    'levator_tendon': [.87, .85, .72], 'external_ear': [.83, .64, .54],
}
OPACITY = {'cornea': .045, 'lens': .035, 'vitreous': .0125,
           'anterior_chamber': .0125, 'lacrimal_lake': .20}


def entries(concepts, partof):
    result = []
    for identity, (name, _, _, _, files) in TERMS.items():
        source = concepts if identity in concepts else partof
        rows = source[identity]
        expected = files.split(',')
        assert {r['name'] for r in rows} == {name}, identity
        assert {r['element file id'] for r in rows} == set(expected), identity
        result.append((identity, name, expected, False, 0))
    elements = [f for _, _, files, _, _ in result for f in files]
    assert len(elements) == len(set(elements)) == 44
    assert len(result) == 42
    return result


def describe(name):
    french, family, _, _ = BY_NAME[name]
    english = 'External ears (auricles)' if family == 'external_ear' else name[0].upper() + name[1:]
    return english, french, 'sense_' + family, 'skull', *WIKI[family]


def attributes(name):
    _, family, category, _ = BY_NAME[name]
    result = {'category': category, 'color': COLORS[family]}
    if family in OPACITY:
        result['opacity'] = OPACITY[family]
    return result


_SUMMARIES = {
    'sclera': ('Tough outer coat forming the white of the eye. It encloses most of the globe and provides attachments for the eye muscles.', 'Enveloppe résistante formant le blanc de l’œil. Elle entoure l’essentiel du globe et reçoit les insertions des muscles oculaires.'),
    'cornea': ('Transparent anterior surface of the eye. Its curvature contributes to focusing incoming light onto the retina.', 'Surface antérieure transparente de l’œil. Sa courbure contribue à focaliser la lumière reçue sur la rétine.'),
    'iris': ('Pigmented diaphragm surrounding the pupil. Its muscles vary the size of this opening and regulate the amount of light entering the eye.', 'Diaphragme pigmenté entourant la pupille. Ses muscles modifient la taille de cette ouverture et régulent la quantité de lumière entrant dans l’œil.'),
    'lens': ('Transparent lens behind the iris. Changes in its curvature contribute to accommodation, allowing the eye to focus at different distances.', 'Lentille transparente derrière l’iris. Les changements de sa courbure participent à l’accommodation, permettant la mise au point à différentes distances.'),
    'choroid': ('Vascular, pigmented coat between the sclera and retina. It supplies the outer retina and absorbs stray light.', 'Enveloppe vascularisée et pigmentée entre la sclère et la rétine. Elle nourrit la rétine externe et absorbe la lumière parasite.'),
    'retina': ('Light-sensitive neural lining at the back of the eye. Photoreceptors initiate signals that pass through retinal circuits and then the optic nerve. Its microscopic cell layers are not individually modelled.', 'Tunique nerveuse sensible à la lumière au fond de l’œil. Les photorécepteurs déclenchent des signaux transmis par les circuits rétiniens puis le nerf optique. Ses couches cellulaires microscopiques ne sont pas individualisées.'),
    'ciliary_crown': ('Folded anterior region of the ciliary body containing the ciliary processes, associated with aqueous-humour production and the attachment of zonular fibres. The source does not model the whole ciliary body separately.', 'Région antérieure plissée du corps ciliaire portant les procès ciliaires, associés à la production d’humeur aqueuse et à l’attache des fibres zonulaires. La source ne modélise pas séparément tout le corps ciliaire.'),
    'zonule': ('Fine suspensory fibres connecting the ciliary region to the lens capsule. They transmit tension involved in accommodation.', 'Fines fibres suspensives reliant la région ciliaire à la capsule du cristallin. Elles transmettent les tensions intervenant dans l’accommodation.'),
    'vitreous': ('Transparent gel occupying the cavity behind the lens and in front of the retina. This mesh marks its volume, not a solid tissue wall.', 'Gel transparent occupant la cavité derrière le cristallin et devant la rétine. Ce maillage délimite son volume, pas une paroi tissulaire solide.'),
    'anterior_chamber': ('Space between the cornea and iris containing aqueous humour. The source represents the cavity volume, not an additional membrane.', 'Espace entre la cornée et l’iris contenant l’humeur aqueuse. La source représente le volume de la cavité, pas une membrane supplémentaire.'),
    'lacrimal_gland': ('Gland in the upper outer orbit producing the aqueous component of tears. Tears wet and protect the eye surface.', 'Glande située en haut et en dehors de l’orbite, produisant la composante aqueuse des larmes. Celles-ci humidifient et protègent la surface oculaire.'),
    'lacrimal_sac': ('Collecting part of the tear-drainage pathway near the inner corner of the eye. It receives the canaliculi and continues into the nasolacrimal duct.', 'Partie collectrice de la voie lacrymale près de l’angle interne de l’œil. Elle reçoit les canalicules et se poursuit par le conduit lacrymonasal.'),
    'nasolacrimal': ('Drainage conduit carrying tears from the lacrimal sac towards the nasal cavity.', 'Conduit évacuant les larmes du sac lacrymal vers la cavité nasale.'),
    'lacrimal_canaliculi': ('Small drainage channels connecting the lacrimal puncta of the eyelids to the lacrimal sac. The source groups the upper and lower branches on each side.', 'Petits canaux de drainage reliant les points lacrymaux des paupières au sac lacrymal. La source regroupe les branches supérieure et inférieure de chaque côté.'),
    'lacrimal_lake': ('Small collection of tears at the inner corner of the eye, near the openings of the drainage pathway. This is a fluid space, not a gland.', 'Petite collection de larmes à l’angle interne de l’œil, près des orifices des voies de drainage. Il s’agit d’un espace liquidien, pas d’une glande.'),
    'tarsal_plate': ('Dense connective-tissue support within an eyelid. It gives the lid its shape; it is neither bone nor cartilage.', 'Armature de tissu conjonctif dense à l’intérieur d’une paupière. Elle lui donne sa forme ; ce n’est ni un os ni un cartilage.'),
    'check_ligament': ('Fibrous attachment linking a rectus muscle sheath to the orbital wall and contributing to support of the eye.', 'Attache fibreuse reliant la gaine d’un muscle droit à la paroi orbitaire et participant au soutien de l’œil.'),
    'trochlea': ('Fibrous pulley in the upper inner orbit that redirects the tendon of the superior oblique muscle.', 'Poulie fibreuse dans la partie supérieure et interne de l’orbite, déviant le tendon du muscle oblique supérieur.'),
    'levator_tendon': ('Tendinous expansion transmitting the pull of the levator muscle to the upper eyelid. Only the right-side source mesh is available in this selection.', 'Expansion tendineuse transmettant la traction du muscle releveur à la paupière supérieure. Seul le maillage source droit est disponible dans cette sélection.'),
    'external_ear': ('The auricles collect sound towards the external auditory canals. Both sides share one source mesh here. The eardrums, middle-ear ossicles and inner-ear structures are not included in this surface.', 'Les pavillons recueillent les sons vers les conduits auditifs externes. Les deux côtés partagent ici un maillage source. Les tympans, osselets et structures de l’oreille interne ne font pas partie de cette surface.'),
}
summaries = {'sense_' + k: v for k, v in _SUMMARIES.items()}

# These fragments occupy the other orbit, are disconnected from the right-eye
# mesh, and together have less than 0.003 mm² of area in each audited OBJ.
REMOVED_TRIANGLES = {'FJ1340': 18, 'FJ1368': 4, 'FJ1337': 10, 'FJ1371': 4}


def clean_geometry(element, vertices, faces):
    """Remove only pinned opposite-orbit debris; keep all other positions exact."""
    if element not in REMOVED_TRIANGLES:
        return vertices, faces
    import numpy as np
    v = np.asarray(vertices, dtype=np.float64)
    f = np.asarray(faces, dtype=np.int64)
    t = v[f]
    removed = (t[:, :, 0] > 0).all(1)
    assert not ((t[:, :, 0].min(1) < 0) & (t[:, :, 0].max(1) > 0)).any(), element
    assert int(removed.sum()) == REMOVED_TRIANGLES[element], element
    area = np.linalg.norm(np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0]), axis=1) / 2
    assert area[removed].sum() < 3e-9, element  # 0.003 mm², coordinates in metres
    assert area[removed].sum() < area.sum() * 1.1e-6, element
    kept = f[~removed]
    used, remapped = np.unique(kept, return_inverse=True)
    assert (v[used, 0] < 0).all(), element
    return v[used].tolist(), remapped.reshape(-1, 3).tolist()


references = [
    'https://www.nei.nih.gov/eye-health-information/healthy-vision/how-eyes-work',
    'https://www.nei.nih.gov/eye-health-information/healthy-vision/nei-for-kids/about-eye',
    'https://www.nei.nih.gov/eye-health-information/healthy-vision/how-eyes-work/how-tears-work',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/14-1-sensory-perception',
    'https://pmc.ncbi.nlm.nih.gov/articles/PMC6486368/',
    'https://ncbi.nlm.nih.gov/mesh/68007765',
]


def provenance(source, selected_entries):
    return {
        'source': 'BodyParts3D 4.0', 'license': 'CC BY 4.0',
        'structures': len(selected_entries),
        'sourceElements': sum(len(files) for _, _, files, _, _ in selected_entries),
        'isAMetadataSha256': hashlib.sha256((source / 'elements.tsv').read_bytes()).hexdigest(),
        'modifications': 'Original anatomical coordinates, no spheres or replacement geometry. OBJ to GLB with smooth normals, illustrative colours and translucent optical/fluid surfaces. Each choroid combines its two source parts; the external-ear mesh contains both auricles. The pupil remains the existing iris aperture, with no extra mesh. Explicit cleanup of tiny opposite-orbit debris is detailed below.',
        'removedOppositeOrbitTriangles': REMOVED_TRIANGLES,
        'cleanupRule': 'For these four right-eye OBJ files only, remove the asserted number of triangles lying entirely at X > 0. Their area is below 0.003 square millimetres per file and below 0.00011 percent of the mesh surface. Remove the now-unused vertices; all surviving positions and triangle winding are unchanged.',
        'transparency': {family: opacity for family, opacity in OPACITY.items()},
        'limitations': 'Illustrative transparency, not a refractive or physiological eye simulation. No microscopic retinal or corneal layers, moving accommodation, conjunctiva or complete eyelid skin. The anterior chamber and lacrimal lake are cavity/fluid volumes. Only the right levator tendon is supplied. No tympanic membranes, middle-ear ossicle meshes, cochleae or vestibular labyrinths in this source subset. Optic nerves and ocular vessels belong to their respective atlas layers; existing extraocular muscles are not duplicated.',
        'educationalReferences': references,
    }
