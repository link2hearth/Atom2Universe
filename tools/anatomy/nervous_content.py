"""Explicit nervous structures; source identities are never inferred from parents.

BodyParts3D PART-OF is incomplete and sometimes lists a cavity as its containing
organ. In particular FJ1737 is the central canal, NOT the spinal cord itself.
IS-A supplies the exact identities used below. No parent mesh is added on top
of the same child surfaces. Labels and short educational notes are original.
"""
import hashlib
import nervous_external_content

# Source names are checked against the metadata, not mechanically translated.
# id | exact source name | French label | educational family | navigation group
_TERMS = '''
FMA72670|left angular gyrus|Gyrus angulaire gauche|association_cortex|cerebral
FMA72669|right angular gyrus|Gyrus angulaire droit|association_cortex|cerebral
FMA72718|left cingulate gyrus|Gyrus cingulaire gauche|limbic_cortex|cerebral
FMA72717|right cingulate gyrus|Gyrus cingulaire droit|limbic_cortex|cerebral
FMA72658|left inferior frontal gyrus|Gyrus frontal inférieur gauche|frontal_cortex|cerebral
FMA72657|right inferior frontal gyrus|Gyrus frontal inférieur droit|frontal_cortex|cerebral
FMA72656|left middle frontal gyrus|Gyrus frontal moyen gauche|frontal_cortex|cerebral
FMA72655|right middle frontal gyrus|Gyrus frontal moyen droit|frontal_cortex|cerebral
FMA72654|left superior frontal gyrus|Gyrus frontal supérieur gauche|frontal_cortex|cerebral
FMA72653|right superior frontal gyrus|Gyrus frontal supérieur droit|frontal_cortex|cerebral
FMA256194|orbital gyrus|Gyri orbitaires|frontal_cortex|cerebral
FMA72688|left inferior temporal gyrus|Gyrus temporal inférieur gauche|temporal_cortex|cerebral
FMA72687|right inferior temporal gyrus|Gyrus temporal inférieur droit|temporal_cortex|cerebral
FMA72686|left middle temporal gyrus|Gyrus temporal moyen gauche|temporal_cortex|cerebral
FMA72685|right middle temporal gyrus|Gyrus temporal moyen droit|temporal_cortex|cerebral
FMA72801|anterior part of left superior temporal gyrus|Gyrus temporal supérieur gauche · partie antérieure|temporal_cortex|cerebral
FMA72800|anterior part of right superior temporal gyrus|Gyrus temporal supérieur droit · partie antérieure|temporal_cortex|cerebral
FMA72805|posterior part of left superior temporal gyrus|Gyrus temporal supérieur gauche · partie postérieure|temporal_cortex|cerebral
FMA72804|posterior part of right superior temporal gyrus|Gyrus temporal supérieur droit · partie postérieure|temporal_cortex|cerebral
FMA72978|left insula|Insula gauche|insula|cerebral
FMA72977|right insula|Insula droite|insula|cerebral
FMA72690|left fusiform gyrus|Gyrus fusiforme gauche|association_cortex|cerebral
FMA72689|right fusiform gyrus|Gyrus fusiforme droit|association_cortex|cerebral
FMA72706|left parahippocampal gyrus|Gyrus parahippocampique gauche|limbic_cortex|cerebral
FMA72705|right parahippocampal gyrus|Gyrus parahippocampique droit|limbic_cortex|cerebral
FMA72976|left occipital lobe|Lobe occipital gauche|occipital_cortex|cerebral
FMA72975|right occipital lobe|Lobe occipital droit|occipital_cortex|cerebral
FMA72666|left postcentral gyrus|Gyrus postcentral gauche|somatosensory_cortex|cerebral
FMA72665|right postcentral gyrus|Gyrus postcentral droit|somatosensory_cortex|cerebral
FMA72662|left precentral gyrus|Gyrus précentral gauche|motor_cortex|cerebral
FMA72661|right precentral gyrus|Gyrus précentral droit|motor_cortex|cerebral
FMA72672|left superior parietal lobule|Lobule pariétal supérieur gauche|association_cortex|cerebral
FMA72671|right superior parietal lobule|Lobule pariétal supérieur droit|association_cortex|cerebral
FMA72668|left supramarginal gyrus|Gyrus supramarginal gauche|association_cortex|cerebral
FMA72667|right supramarginal gyrus|Gyrus supramarginal droit|association_cortex|cerebral
FMA72907|left internal capsule|Capsule interne gauche|projection_fibres|deep_brain
FMA72906|right internal capsule|Capsule interne droite|projection_fibres|deep_brain
FMA72833|left amygdala|Amygdale gauche|amygdala|deep_brain
FMA72832|right amygdala|Amygdale droite|amygdala|deep_brain
FMA72827|left caudate nucleus|Noyau caudé gauche|basal_nuclei|deep_brain
FMA72826|right caudate nucleus|Noyau caudé droit|basal_nuclei|deep_brain
FMA72831|left globus pallidus|Globus pallidus gauche|basal_nuclei|deep_brain
FMA72830|right globus pallidus|Globus pallidus droit|basal_nuclei|deep_brain
FMA72829|left putamen|Putamen gauche|basal_nuclei|deep_brain
FMA72828|right putamen|Putamen droit|basal_nuclei|deep_brain
FMA72714|left hippocampus|Hippocampe gauche|hippocampus|deep_brain
FMA72713|right hippocampus|Hippocampe droit|hippocampus|deep_brain
FMA258716|left thalamus|Thalamus gauche|thalamus|deep_brain
FMA258714|right thalamus|Thalamus droit|thalamus|deep_brain
FMA62008|hypothalamus|Hypothalamus|hypothalamus|deep_brain
FMA74877|mammillary body|Corps mamillaires|limbic_nuclei|deep_brain
FMA62327|tuber cinereum|Tuber cinereum|hypothalamus|deep_brain
FMA62032|habenula|Habénula|limbic_nuclei|deep_brain
FMA62033|pineal body|Glande pinéale|pineal|deep_brain
FMA61961|anterior commissure|Commissure antérieure|commissural_fibres|deep_brain
FMA61970|commissure of fornix of forebrain|Commissure du fornix|commissural_fibres|deep_brain
FMA86464|corpus callosum|Corps calleux|commissural_fibres|deep_brain
FMA62072|posterior commissure|Commissure postérieure|commissural_fibres|deep_brain
FMA72925|left fornix of forebrain|Fornix gauche|limbic_fibres|deep_brain
FMA72924|right fornix of forebrain|Fornix droit|limbic_fibres|deep_brain
FMA73414|left stria medullaris of thalamus|Strie médullaire du thalamus gauche|limbic_fibres|deep_brain
FMA73413|right stria medullaris of thalamus|Strie médullaire du thalamus droit|limbic_fibres|deep_brain
FMA61974|stria terminalis|Stries terminales|limbic_fibres|deep_brain
FMA61975|lamina terminalis|Lame terminale|deep_support|deep_brain
FMA61842|septum of telencephalon|Septum du télencéphale|deep_support|deep_brain
FMA62045|optic chiasm|Chiasma optique|optic_pathway|deep_brain
FMA67936|left optic tract|Tractus optique gauche|optic_pathway|deep_brain
FMA62382|right optic tract|Tractus optique droit|optic_pathway|deep_brain
FMA73304|left lateral geniculate body|Corps géniculé latéral gauche|optic_pathway|deep_brain
FMA73303|right lateral geniculate body|Corps géniculé latéral droit|optic_pathway|deep_brain
FMA73309|right medial geniculate body|Corps géniculé médial droit|auditory_pathway|deep_brain
FMA73310|left medial geniculate body|Corps géniculé médial gauche|auditory_pathway|deep_brain
FMA61993|midbrain|Mésencéphale|midbrain|brainstem
FMA67943|pons|Pont|pons|brainstem
FMA62004|medulla oblongata|Moelle allongée|medulla|brainstem
FMA62394|peduncle of midbrain|Pédoncules cérébraux|projection_fibres|brainstem
FMA73423|left superior colliculus|Colliculus supérieur gauche|superior_colliculus|brainstem
FMA73422|right superior colliculus|Colliculus supérieur droit|superior_colliculus|brainstem
FMA73435|left inferior colliculus|Colliculus inférieur gauche|auditory_pathway|brainstem
FMA73434|right inferior colliculus|Colliculus inférieur droit|auditory_pathway|brainstem
FMA72417|brachium of superior colliculus|Bras des colliculus supérieurs|optic_pathway|brainstem
FMA73464|brachium of left inferior colliculus|Bras du colliculus inférieur gauche|auditory_pathway|brainstem
FMA73463|brachium of right inferior colliculus|Bras du colliculus inférieur droit|auditory_pathway|brainstem
FMA67944|cerebellum|Cervelet|cerebellum|cerebellum
FMA83966|tentorium cerebelli|Tente du cervelet|meninges|meninges
FMA78454|third ventricle|Troisième ventricule · cavité|csf|csf
FMA78469|fourth ventricle|Quatrième ventricule · cavité|csf|csf
FMA78450|left lateral ventricle|Ventricule latéral gauche · cavité|csf|csf
FMA78449|right lateral ventricle|Ventricule latéral droit · cavité|csf|csf
FMA78467|cerebral aqueduct|Aqueduc du mésencéphale · cavité|csf|csf
FMA78497|central canal of spinal cord|Canal central de la moelle · cavité|central_canal|csf
FMA75351|interventricular foramen|Foramens interventriculaires · cavités|csf|csf
FMA61934|choroid plexus of cerebral hemisphere|Plexus choroïdes des hémisphères cérébraux|choroid_plexus|csf
FMA50878|left optic nerve|Nerf optique gauche · II|optic_nerve|cranial_nerves
FMA50875|right optic nerve|Nerf optique droit · II|optic_nerve|cranial_nerves
FMA50882|left trochlear nerve|Nerf trochléaire gauche · IV|trochlear|cranial_nerves
FMA50881|right trochlear nerve|Nerf trochléaire droit · IV|trochlear|cranial_nerves
FMA52575|superior branch of left oculomotor nerve|Branche supérieure du nerf oculomoteur gauche|oculomotor|cranial_nerves
FMA52574|superior branch of right oculomotor nerve|Branche supérieure du nerf oculomoteur droit|oculomotor|cranial_nerves
FMA52577|inferior branch of left oculomotor nerve|Branche inférieure du nerf oculomoteur gauche|oculomotor|cranial_nerves
FMA52576|inferior branch of right oculomotor nerve|Branche inférieure du nerf oculomoteur droit|oculomotor|cranial_nerves
FMA52623|left ophthalmic nerve|Nerf ophtalmique gauche · V1|ophthalmic|cranial_nerves
FMA52622|right ophthalmic nerve|Nerf ophtalmique droit · V1|ophthalmic|cranial_nerves
FMA52640|left frontal nerve|Nerf frontal gauche|ophthalmic|cranial_nerves
FMA52639|right frontal nerve|Nerf frontal droit|ophthalmic|cranial_nerves
FMA52630|left lacrimal nerve|Nerf lacrymal gauche|ophthalmic|cranial_nerves
FMA52629|right lacrimal nerve|Nerf lacrymal droit|ophthalmic|cranial_nerves
FMA52670|left nasociliary nerve|Nerf naso-ciliaire gauche|ophthalmic|cranial_nerves
FMA52669|right nasociliary nerve|Nerf naso-ciliaire droit|ophthalmic|cranial_nerves
FMA52657|left supra-orbital nerve|Nerf supra-orbitaire gauche|ophthalmic|cranial_nerves
FMA52656|right supra-orbital nerve|Nerf supra-orbitaire droit|ophthalmic|cranial_nerves
FMA52644|left supratrochlear nerve|Nerf supra-trochléaire gauche|ophthalmic|cranial_nerves
FMA52643|right supratrochlear nerve|Nerf supra-trochléaire droit|ophthalmic|cranial_nerves
FMA52699|left infratrochlear nerve|Nerf infra-trochléaire gauche|ophthalmic|cranial_nerves
FMA52698|right infratrochlear nerve|Nerf infra-trochléaire droit|ophthalmic|cranial_nerves
FMA52677|left anterior ethmoidal nerve|Nerf ethmoïdal antérieur gauche|ophthalmic|cranial_nerves
FMA52676|right anterior ethmoidal nerve|Nerf ethmoïdal antérieur droit|ophthalmic|cranial_nerves
FMA52716|left posterior ethmoidal nerve|Nerf ethmoïdal postérieur gauche|ophthalmic|cranial_nerves
FMA52715|right posterior ethmoidal nerve|Nerf ethmoïdal postérieur droit|ophthalmic|cranial_nerves
FMA82735|left long ciliary nerve|Nerfs ciliaires longs gauches|ophthalmic|cranial_nerves
FMA82734|right long ciliary nerve|Nerfs ciliaires longs droits|ophthalmic|cranial_nerves
FMA52674|communicating branch of left nasociliary nerve with left ciliary ganglion|Branche communicante naso-ciliaire gauche|ciliary_connections|cranial_nerves
FMA52673|communicating branch of right nasociliary nerve with right ciliary ganglion|Branche communicante naso-ciliaire droite|ciliary_connections|cranial_nerves
FMA53550|left ciliary ganglion|Ganglion ciliaire gauche|ciliary_ganglion|cranial_nerves
FMA53549|right ciliary ganglion|Ganglion ciliaire droit|ciliary_ganglion|cranial_nerves
FMA7041|short ciliary nerve|Nerfs ciliaires courts|ciliary_connections|cranial_nerves
'''
TERMS = {row.split('|')[0]: tuple(row.split('|')[1:]) for row in _TERMS.strip().splitlines()}
BY_NAME = {v[0]: v[1:] for v in TERMS.values()}

groups = {
    'cerebral': ('Cerebral cortex', 'Cortex cérébral'),
    'deep_brain': ('Deep brain structures', 'Structures cérébrales profondes'),
    'brainstem': ('Brainstem', 'Tronc cérébral'),
    'cerebellum': ('Cerebellum', 'Cervelet'),
    'cranial_nerves': ('Cranial nerves', 'Nerfs crâniens'),
    'meninges': ('Meninges', 'Méninges'),
    'csf': ('CSF spaces and choroid plexuses', 'Cavités du LCS et plexus choroïdes'),
}
COLORS = {
    'cerebral': [.80, .61, .60], 'deep_brain': [.68, .48, .60],
    'brainstem': [.85, .71, .58], 'cerebellum': [.77, .58, .58],
    'cranial_nerves': [.96, .85, .36], 'meninges': [.78, .81, .80],
    'csf': [.35, .69, .90],
}

_SUMMARIES = {
    'association_cortex': ('Cortical association region integrating information across distributed networks. Its visible surface does not delimit a single isolated function.', 'Région corticale associative intégrant des informations dans des réseaux distribués. Sa surface visible ne délimite pas une fonction isolée.'),
    'frontal_cortex': ('Frontal cortical region contributing to planning and behavioural control; functions depend on its connections with other regions.', 'Région corticale frontale participant à la planification et au contrôle du comportement ; ses fonctions dépendent de ses connexions avec les autres régions.'),
    'temporal_cortex': ('Temporal cortical region involved in sensory processing and association. Auditory, visual and memory networks occupy distinct but connected regions.', 'Région corticale temporale participant au traitement et à l’association sensoriels. Les réseaux auditifs, visuels et mnésiques occupent des régions distinctes mais connectées.'),
    'limbic_cortex': ('Medial cortical region connected with networks for memory, emotion and behaviour.', 'Région corticale médiale reliée aux réseaux de la mémoire, des émotions et du comportement.'),
    'insula': ('Cortex concealed within the lateral sulcus, associated with taste and processing of bodily signals.', 'Cortex enfoui dans le sillon latéral, associé notamment au goût et au traitement des signaux corporels.'),
    'occipital_cortex': ('Posterior cerebral lobe containing visual cortex. Visual perception also recruits networks beyond this lobe.', 'Lobe cérébral postérieur contenant le cortex visuel. La perception visuelle mobilise également des réseaux au-delà de ce lobe.'),
    'somatosensory_cortex': ('The postcentral gyrus contains primary somatosensory cortex, receiving information such as touch and body position.', 'Le gyrus postcentral contient le cortex somatosensoriel primaire, recevant notamment les informations tactiles et de position du corps.'),
    'motor_cortex': ('The precentral gyrus contains primary motor cortex, contributing to commands for voluntary movement.', 'Le gyrus précentral contient le cortex moteur primaire, contribuant aux commandes des mouvements volontaires.'),
    'projection_fibres': ('Bundle carrying signals between cerebral cortex and deeper nervous structures. Individual axons are not resolved.', 'Faisceau transmettant des signaux entre le cortex cérébral et des structures nerveuses profondes. Les axones individuels ne sont pas représentés.'),
    'commissural_fibres': ('White-matter connection crossing the midline to link structures on the two sides.', 'Connexion de substance blanche franchissant la ligne médiane pour relier des structures des deux côtés.'),
    'limbic_fibres': ('White-matter pathway connecting structures involved in memory and emotional processing.', 'Voie de substance blanche reliant des structures participant à la mémoire et au traitement émotionnel.'),
    'basal_nuclei': ('Deep cerebral nucleus participating in circuits that regulate movement selection and other aspects of behaviour.', 'Noyau cérébral profond participant aux circuits qui régulent la sélection des mouvements et d’autres aspects du comportement.'),
    'hippocampus': ('Medial temporal structure important for forming memories and spatial representation.', 'Structure temporale médiale importante pour la formation des souvenirs et la représentation spatiale.'),
    'amygdala': ('Group of deep temporal nuclei participating in emotional processing and learning.', 'Ensemble de noyaux temporaux profonds participant au traitement émotionnel et à l’apprentissage.'),
    'thalamus': ('Diencephalic relay linking cortical, sensory and motor networks. Its many nuclei are grouped in this surface.', 'Relais diencéphalique reliant les réseaux corticaux, sensoriels et moteurs. Ses nombreux noyaux sont regroupés dans cette surface.'),
    'hypothalamus': ('Diencephalic region coordinating autonomic and endocrine regulation and contributing to homeostasis.', 'Région diencéphalique coordonnant les régulations autonome et endocrine et contribuant à l’homéostasie.'),
    'limbic_nuclei': ('Diencephalic structure connected with circuits for memory or emotional and motivational processing.', 'Structure diencéphalique reliée aux circuits de la mémoire ou des traitements émotionnels et motivationnels.'),
    'pineal': ('Small endocrine structure of the epithalamus secreting melatonin and contributing to circadian timing.', 'Petite structure endocrine de l’épithalamus sécrétant la mélatonine et contribuant à la régulation circadienne.'),
    'deep_support': ('Midline forebrain structure bordering the ventricular region. The surface model does not resolve its microscopic organization.', 'Structure médiane du cerveau antérieur bordant la région ventriculaire. Le modèle de surface ne représente pas son organisation microscopique.'),
    'optic_pathway': ('Structure of the visual pathway beyond the retina. At the optic chiasm, nasal retinal fibres cross the midline.', 'Structure de la voie visuelle après la rétine. Au chiasma optique, les fibres des hémirétines nasales croisent la ligne médiane.'),
    'auditory_pathway': ('Relay or connecting bundle of the central auditory pathway.', 'Relais ou faisceau de connexion de la voie auditive centrale.'),
    'midbrain': ('Upper brainstem region traversed by sensory and motor pathways and involved in orienting responses.', 'Partie supérieure du tronc cérébral traversée par des voies sensitives et motrices et participant aux réponses d’orientation.'),
    'pons': ('Brainstem region connecting with the cerebellum and containing pathways and nuclei involved in cranial and vital functions.', 'Région du tronc cérébral reliée au cervelet et contenant des voies et noyaux participant aux fonctions crâniennes et vitales.'),
    'medulla': ('Brainstem region continuous with the spinal cord, containing ascending and descending pathways and centres involved in vital regulation.', 'Région du tronc cérébral se prolongeant par la moelle spinale, contenant des voies ascendantes et descendantes et des centres de régulation vitale.'),
    'superior_colliculus': ('Midbrain structure participating in the orientation of gaze and attention toward sensory events.', 'Structure du mésencéphale participant à l’orientation du regard et de l’attention vers des événements sensoriels.'),
    'cerebellum': ('Coordinates movements and motor learning by comparing motor commands with sensory feedback. Both sides are grouped in this source selection.', 'Coordonne les mouvements et l’apprentissage moteur en comparant les commandes motrices au retour sensoriel. Les deux côtés sont regroupés dans cette sélection source.'),
    'meninges': ('Fold of dura mater supporting and separating nervous structures. It is a protective membrane, not neural tissue.', 'Repli de dure-mère soutenant et séparant des structures nerveuses. Il s’agit d’une membrane protectrice, pas de tissu nerveux.'),
    'csf': ('Representation of a cavity or passage containing cerebrospinal fluid. The coloured surface outlines a space; it is not a solid organ.', 'Représentation d’une cavité ou d’un passage contenant le liquide cérébrospinal. La surface colorée délimite un espace ; ce n’est pas un organe plein.'),
    'central_canal': ('Narrow fluid-filled canal within the spinal cord. This source surface represents the canal only, not the surrounding spinal tissue.', 'Fin canal contenant du liquide au sein de la moelle spinale. Cette surface source représente uniquement le canal, pas le tissu médullaire qui l’entoure.'),
    'choroid_plexus': ('Specialized tissue within the ventricles that contributes to cerebrospinal fluid production. The source groups the two cerebral plexuses.', 'Tissu spécialisé intraventriculaire contribuant à la production du liquide cérébrospinal. La source regroupe les deux plexus cérébraux.'),
    'optic_nerve': ('Cranial nerve II carries retinal output toward the optic chiasm. Orbital and intracranial source portions are grouped per side.', 'Le nerf crânien II transmet les signaux rétiniens vers le chiasma optique. Les portions orbitaires et intracrâniennes sources sont regroupées par côté.'),
    'trochlear': ('Cranial nerve IV supplies the superior oblique eye muscle.', 'Le nerf crânien IV innerve le muscle oblique supérieur de l’œil.'),
    'oculomotor': ('Branch of cranial nerve III, which supplies most extraocular muscles and carries parasympathetic fibres toward the ciliary ganglion.', 'Branche du nerf crânien III, qui innerve la plupart des muscles oculomoteurs et transporte des fibres parasympathiques vers le ganglion ciliaire.'),
    'ophthalmic': ('Sensory branch belonging to the ophthalmic division of the trigeminal nerve. Its territory depends on the selected branch.', 'Branche sensitive appartenant à la division ophtalmique du nerf trijumeau. Son territoire dépend de la branche sélectionnée.'),
    'ciliary_connections': ('Fine nerve connection of the ciliary network. Short ciliary nerves convey autonomic and sensory fibres between the ganglion and the eye.', 'Fine connexion nerveuse du réseau ciliaire. Les nerfs ciliaires courts conduisent des fibres autonomes et sensitives entre le ganglion et l’œil.'),
    'ciliary_ganglion': ('Parasympathetic relay in the orbit for pupillary constriction and accommodation. Sensory and sympathetic fibres also pass through the region.', 'Relais parasympathique orbitaire pour la constriction pupillaire et l’accommodation. Des fibres sensitives et sympathiques traversent aussi cette région.'),
}
summaries = {'nervous_' + k: v for k, v in _SUMMARIES.items()}
summaries.update(nervous_external_content.summaries)
groups.update(nervous_external_content.groups)

references = [
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/' + page
    for page in ('13-2-the-central-nervous-system', '13-3-circulation-and-the-central-nervous-system',
                 '13-4-the-peripheral-nervous-system', '14-1-sensory-perception',
                 '14-3-motor-responses', '15-1-divisions-of-the-autonomic-nervous-system',
                 '16-3-the-cranial-nerve-exam')
]


def entries(concepts, partof):
    result = []
    for identity, (name, *_) in TERMS.items():
        rows = concepts[identity]
        assert rows and all(r['name'] == name for r in rows), (identity, name, rows)
        files = sorted({r['element file id'] for r in rows})
        result.append((identity, name, files, False, 0))
    files = [f for _, _, ff, _, _ in result for f in ff]
    assert len(files) == len(set(files)), 'Duplicate nervous source geometry'
    assert 'FJ1796' not in files, 'Pituitary already belongs to organs'
    return result + nervous_external_content.entries()


def describe(name):
    if name in nervous_external_content.TERMS:
        return nervous_external_content.describe(name)
    french, family, category = BY_NAME[name]
    english = name[0].upper() + name[1:]
    if family in ('csf', 'central_canal'):
        english += ' · cavity'
    region = 'spine' if family == 'central_canal' else 'skull'
    return english, french, 'nervous_' + family, region, name, french.split(' · ')[0]


def attributes(name):
    if name in nervous_external_content.TERMS:
        return nervous_external_content.attributes(name)
    _, family, category = BY_NAME[name]
    return {'category': category, 'color': COLORS[category]}


def provenance(source, selected):
    bp = [entry for entry in selected if entry[0].startswith('FMA')]
    external = [entry for entry in selected if entry[0].startswith('ZAN_')]
    return {
        'source': 'BodyParts3D 4.0 and Z-Anatomy', 'structures': len(selected),
        'bodyPartsStructures': len(bp),
        'sourceElements': sum(len(files) for _, _, files, _, _ in bp),
        'metadataSha256': hashlib.sha256((source / 'elements.tsv').read_bytes()).hexdigest(),
        'modifications': 'Explicit IS-A identities; original source positions and topology; unit/axis conversion, smooth normals and illustrative colours. Bilateral source parts remain grouped when no side-specific FMA identity is supplied.',
        'grouping': 'Brain regions, deep nuclei, tracts and cavities are distinct source selections, with no repeated source element. Optic nerves each combine orbital and intracranial pieces. Superior colliculus brachia FJ1735/FJ1736 have reversed lateral labels in source metadata, so the verified bilateral parent FMA72417 is used without exposing incorrect side labels. Corpus-callosum and named tracts are used instead of a superimposed whole-hemisphere white-matter envelope.',
        'limitations': 'Surface atlas, not histology or tractography. FJ1737 is the central canal of the spinal cord, not spinal cord tissue. BodyParts3D contains selected orbital nerves but not a complete set of cranial or peripheral nerves. Ventricular casts are explicitly labelled as spaces. No cerebral sulcal or cellular detail beyond the supplied surfaces is synthesized.',
        'references': references,
        'externalAdditions': nervous_external_content.provenance(source, external),
    }


def load(source):
    return nervous_external_content.load(source)
