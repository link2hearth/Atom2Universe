"""Explicit organ surfaces from BodyParts3D, with original bilingual summaries.

PART-OF collections also contain vessels, cavities and overlapping variants.
Only the documented surface subset is used, never the entire collection blindly.
"""
import hashlib

# Identity | source name | French label | region | family | display system
_TERMS = '''
FMA7088|heart|Cœur|thorax|heart|cardiovascular
FMA7197|liver|Foie|abdomen|liver|digestive
FMA7131|esophagus|Œsophage|thorax|esophagus|digestive
FMA7148|stomach|Estomac|abdomen|stomach|digestive
FMA7206|duodenum|Duodénum|abdomen|duodenum|digestive
FMA7207|jejunum|Jéjunum|abdomen|jejunum|digestive
FMA7208|ileum|Iléon|abdomen|ileum|digestive
FMA14541|cecum|Cæcum|abdomen|cecum|digestive
FMA14542|appendix|Appendice vermiforme|abdomen|appendix|digestive
FMA14545|ascending colon|Côlon ascendant|abdomen|colon|digestive
FMA14546|transverse colon|Côlon transverse|abdomen|colon|digestive
FMA14547|descending colon|Côlon descendant|abdomen|colon|digestive
FMA14544|rectum|Rectum|pelvis|rectum|digestive
FMA7198|pancreas|Pancréas|abdomen|pancreas|digestive
FMA7202|gallbladder|Vésicule biliaire|abdomen|gallbladder|digestive
FMA7196|spleen|Rate|abdomen|spleen|lymphatic
FMA7204|right kidney|Rein droit|abdomen|kidney|urinary
FMA7205|left kidney|Rein gauche|abdomen|kidney|urinary
FMA15571|right ureter|Uretère droit|abdomen|ureter|urinary
FMA15572|left ureter|Uretère gauche|abdomen|ureter|urinary
FMA15900|urinary bladder|Vessie|pelvis|bladder|urinary
FMA19667|urethra|Urètre masculin|pelvis|urethra|urinary
FMA15629|right adrenal gland|Glande surrénale droite|abdomen|adrenal|endocrine
FMA15630|left adrenal gland|Glande surrénale gauche|abdomen|adrenal|endocrine
FMA9607|thymus|Thymus|thorax|thymus|lymphatic
FMA7394|trachea|Trachée|neck|trachea|respiratory
FMA7395|right main bronchus|Bronche principale droite|thorax|bronchus|respiratory
FMA7396|left main bronchus|Bronche principale gauche|thorax|bronchus|respiratory
FMA54640|tongue|Langue|skull|tongue|digestive
FMA59802|right submandibular gland|Glande submandibulaire droite|skull|salivary|digestive
FMA59803|left submandibular gland|Glande submandibulaire gauche|skull|salivary|digestive
FMA59804|right sublingual gland|Glande sublinguale droite|skull|salivary|digestive
FMA59805|left sublingual gland|Glande sublinguale gauche|skull|salivary|digestive
FMA13889|pituitary gland|Hypophyse|skull|pituitary|endocrine
FMA9600|prostate|Prostate|pelvis|prostate|reproductive
FMA7211|right testis|Testicule droit|pelvis|testis|reproductive
FMA7212|left testis|Testicule gauche|pelvis|testis|reproductive
FMA18256|right epididymis|Épididyme droit|pelvis|epididymis|reproductive
FMA18257|left epididymis|Épididyme gauche|pelvis|epididymis|reproductive
FMA19387|right seminal vesicle|Vésicule séminale droite|pelvis|seminal|reproductive
FMA19388|left seminal vesicle|Vésicule séminale gauche|pelvis|seminal|reproductive
FMA19618|corpus cavernosum of penis|Corps caverneux du pénis|pelvis|cavernosum|reproductive
FMA19617|corpus spongiosum of penis|Corps spongieux du pénis|pelvis|spongiosum|reproductive
FMA18247|glans penis|Gland du pénis|pelvis|glans|reproductive
'''
TERMS = {row.split('|')[0]: tuple(row.split('|')[1:]) for row in _TERMS.strip().splitlines()}
BY_NAME = {v[0]: v[1:] for v in TERMS.values()}
WIKI = {
    'heart': ('Heart', 'Cœur'), 'liver': ('Liver', 'Foie'), 'esophagus': ('Esophagus', 'Œsophage'),
    'stomach': ('Stomach', 'Estomac'), 'duodenum': ('Duodenum', 'Duodénum'),
    'jejunum': ('Jejunum', 'Jéjunum'), 'ileum': ('Ileum', 'Iléon'), 'cecum': ('Cecum', 'Cæcum'),
    'appendix': ('Appendix (anatomy)', 'Appendice iléo-cæcal'), 'colon': ('Colon (anatomy)', 'Côlon'),
    'rectum': ('Rectum', 'Rectum'), 'pancreas': ('Pancreas', 'Pancréas'),
    'gallbladder': ('Gallbladder', 'Vésicule biliaire'), 'spleen': ('Spleen', 'Rate'),
    'kidney': ('Kidney', 'Rein'), 'ureter': ('Ureter', 'Uretère'), 'bladder': ('Urinary bladder', 'Vessie'),
    'urethra': ('Urethra', 'Urètre'), 'adrenal': ('Adrenal gland', 'Glande surrénale'),
    'thymus': ('Thymus', 'Thymus'), 'trachea': ('Trachea', 'Trachée'), 'bronchus': ('Bronchus', 'Bronche'),
    'tongue': ('Tongue', 'Langue (anatomie)'), 'salivary': ('Salivary gland', 'Glande salivaire'),
    'pituitary': ('Pituitary gland', 'Hypophyse'), 'prostate': ('Prostate', 'Prostate'),
    'testis': ('Testicle', 'Testicule'), 'epididymis': ('Epididymis', 'Épididyme'),
    'seminal': ('Seminal vesicle', 'Vésicule séminale'),
    'cavernosum': ('Corpus cavernosum penis', 'Corps caverneux'),
    'spongiosum': ('Corpus spongiosum', 'Corps spongieux'),
    'glans': ('Glans penis', 'Gland du pénis'),
}

# Illustrative tissue colours, shared by import and runtime through the catalogue.
COLORS = {
    'heart': [.65, .22, .24], 'liver': [.55, .27, .22], 'gallbladder': [.38, .56, .29],
    'spleen': [.48, .25, .43], 'kidney': [.63, .29, .29], 'pancreas': [.87, .69, .45],
    'lung': [.83, .58, .61], 'thyroid': [.71, .36, .32],
    'trachea': [.72, .69, .62], 'bronchus': [.72, .69, .62],
    'adrenal': [.84, .68, .38], 'pituitary': [.77, .47, .55], 'thymus': [.77, .57, .59],
    'salivary': [.85, .68, .50], 'tongue': [.72, .35, .42],
    'stomach': [.83, .51, .48], 'duodenum': [.84, .62, .51], 'jejunum': [.86, .65, .55],
    'ileum': [.82, .59, .49], 'colon': [.77, .58, .48], 'cecum': [.77, .58, .48],
    'appendix': [.77, .58, .48], 'rectum': [.77, .58, .48],
    'esophagus': [.78, .53, .49], 'ureter': [.84, .73, .61], 'bladder': [.85, .67, .58],
    'urethra': [.80, .59, .56], 'prostate': [.69, .50, .54], 'testis': [.85, .70, .60],
    'epididymis': [.81, .57, .49], 'seminal': [.78, .64, .46],
    'cavernosum': [.62, .35, .45], 'spongiosum': [.79, .48, .53], 'glans': [.86, .58, .59],
}


def entries(concepts, partof):
    def files(key, source=concepts):
        return sorted({r['element file id'] for r in source[key]})
    groups = {
        'FMA7088': files('FMA13883') + files('FMA13884'),
        'FMA7197': ['FJ2816'] + [f'FJ{i}' for i in range(2818, 2825)],
        'FMA7207': files('FMA16980'),
        'FMA7208': files('FMA14963'),  # Excludes FJ2599, selected once as cecum.
        'FMA9607': files('FMA71193'),
    }
    assert set(groups['FMA7088']) == {'FJ2428', 'FJ2438', 'FJ2439'}
    assert set(groups['FMA7197']) < set(files('FMA7197', partof))
    result = []
    for identity, (name, *_) in TERMS.items():
        elements = groups.get(identity)
        if elements is None:
            source = concepts if identity in concepts else partof
            elements = files(identity, source)
        result.append((identity, name, elements, False, 0))
    elements = [f for _, _, ff, _, _ in result for f in ff]
    assert len(elements) == len(set(elements))
    assert len(result) == 44
    return result


def describe(name):
    french, region, family, _ = BY_NAME[name]
    return name[0].upper() + name[1:], french, 'organ_' + family, region, *WIKI[family]


def attributes(name):
    _, _, family, system = BY_NAME[name]
    return {'organSystem': system, 'color': COLORS[family]}


_SUMMARIES = {
    'heart': ('Muscular pump connecting pulmonary and systemic circulation. This selection groups the atrial and ventricular walls; valves, coronary vessels and chamber contents are not included.', 'Pompe musculaire reliant les circulations pulmonaire et générale. Cette sélection regroupe les parois atriales et ventriculaires ; les valves, vaisseaux coronaires et contenus des cavités ne sont pas inclus.'),
    'liver': ('Large abdominal gland involved in metabolism, storage and bile production. The source parenchymal parts are grouped into one selectable organ; internal vessels and bile ducts are omitted.', 'Grande glande abdominale intervenant dans le métabolisme, le stockage et la production de bile. Les parties parenchymateuses sources sont regroupées en un organe sélectionnable ; les vaisseaux et canaux biliaires internes sont omis.'),
    'esophagus': ('Muscular tube carrying swallowed food from the pharynx to the stomach. It crosses the thorax and passes through the diaphragm.', 'Conduit musculaire acheminant les aliments du pharynx vers l’estomac. Il traverse le thorax et franchit le diaphragme.'),
    'stomach': ('Expandable digestive organ below the diaphragm. It stores and mixes food with gastric secretions before gradually passing it to the duodenum. Only its external surface is shown.', 'Organe digestif extensible sous le diaphragme. Il stocke et brasse les aliments avec les sécrétions gastriques, puis les transmet progressivement au duodénum. Seule sa surface externe est représentée.'),
    'duodenum': ('First part of the small intestine, receiving gastric contents, bile and pancreatic secretions.', 'Première partie de l’intestin grêle, recevant le contenu gastrique, la bile et les sécrétions pancréatiques.'),
    'jejunum': ('Small-intestinal loops important for nutrient absorption, between the duodenum and ileum.', 'Anses de l’intestin grêle participant à l’absorption des nutriments, entre le duodénum et l’iléon.'),
    'ileum': ('Final part of the small intestine, continuing absorption before the ileocecal junction.', 'Dernière partie de l’intestin grêle, poursuivant l’absorption avant la jonction iléo-cæcale.'),
    'cecum': ('Pouch at the beginning of the large intestine. The source groups it with the ileocecal junction.', 'Poche initiale du gros intestin. La source la regroupe avec la jonction iléo-cæcale.'),
    'appendix': ('Narrow blind-ended structure attached to the cecum, containing lymphoid tissue.', 'Structure étroite en cul-de-sac rattachée au cæcum, contenant du tissu lymphoïde.'),
    'colon': ('Part of the large intestine involved in water absorption and stool formation.', 'Partie du gros intestin participant à l’absorption de l’eau et à la formation des selles.'),
    'rectum': ('Pelvic part of the digestive tract that temporarily stores stool before defecation.', 'Partie pelvienne du tube digestif stockant temporairement les selles avant la défécation.'),
    'pancreas': ('Gland behind the stomach. It supplies digestive secretions to the small intestine and releases hormones including insulin and glucagon into the blood.', 'Glande située derrière l’estomac. Elle fournit des sécrétions digestives à l’intestin grêle et libère dans le sang des hormones, notamment l’insuline et le glucagon.'),
    'gallbladder': ('Stores and concentrates bile produced by the liver, then releases it into the digestive tract. It does not produce bile.', 'Stocke et concentre la bile produite par le foie, puis la libère dans le tube digestif. Elle ne produit pas la bile.'),
    'spleen': ('Lymphoid organ in the upper left abdomen. It filters blood, contributes to immune responses and removes ageing blood cells.', 'Organe lymphoïde de la partie supérieure gauche de l’abdomen. Il filtre le sang, participe aux réponses immunitaires et élimine des cellules sanguines vieillissantes.'),
    'kidney': ('Retroperitoneal organ that forms urine and helps regulate water, electrolytes and acid–base balance. This mesh shows the outer shape; nephrons and collecting structures are not represented.', 'Organe rétropéritonéal formant l’urine et participant à la régulation de l’eau, des électrolytes et de l’équilibre acido-basique. Ce maillage montre la forme externe ; les néphrons et structures collectrices ne sont pas représentés.'),
    'ureter': ('Muscular conduit carrying urine from the kidney to the bladder. It extends through the abdomen into the pelvis.', 'Conduit musculaire acheminant l’urine du rein vers la vessie. Il traverse l’abdomen puis le pelvis.'),
    'bladder': ('Expandable muscular reservoir storing urine before emptying through the urethra. Its shape varies with filling.', 'Réservoir musculaire extensible stockant l’urine avant son évacuation par l’urètre. Sa forme varie avec son remplissage.'),
    'urethra': ('Passage carrying urine from the bladder to the exterior. In this male model it also serves as the final route for semen.', 'Conduit acheminant l’urine de la vessie vers l’extérieur. Dans ce modèle masculin, il constitue également la voie terminale du sperme.'),
    'adrenal': ('Endocrine gland above a kidney. Its cortex and medulla produce different hormones involved in stress responses and physiological regulation; these internal regions are not separately modelled.', 'Glande endocrine située au-dessus d’un rein. Son cortex et sa médullaire produisent différentes hormones participant aux réponses au stress et aux régulations physiologiques ; ces régions internes ne sont pas modélisées séparément.'),
    'thymus': ('Lymphoid organ behind the sternum, where T lymphocytes mature. It is larger in childhood and undergoes substantial fatty replacement with age.', 'Organe lymphoïde situé derrière le sternum, où les lymphocytes T mûrissent. Plus volumineux pendant l’enfance, il subit un important remplacement graisseux avec l’âge.'),
    'trachea': ('Airway connecting the larynx to the main bronchi. Its cartilaginous support helps maintain an open passage for air.', 'Voie aérienne reliant le larynx aux bronches principales. Son support cartilagineux contribue à maintenir le passage de l’air ouvert.'),
    'bronchus': ('Main airway entering a lung and dividing into smaller bronchi. This selection shows the principal source conduit, not the complete branching tree.', 'Voie aérienne principale entrant dans un poumon et se divisant en bronches plus petites. Cette sélection montre le conduit principal de la source, pas tout l’arbre bronchique.'),
    'tongue': ('Muscular organ contributing to food handling, swallowing, speech and taste. Its external surface is shown; the surrounding tongue muscles belong to the muscular layer.', 'Organe musculaire participant à la manipulation des aliments, à la déglutition, à la parole et au goût. Sa surface externe est représentée ; les muscles de la langue figurent dans la couche musculaire.'),
    'salivary': ('Gland producing saliva, which moistens the mouth and food and contributes to digestion and oral protection.', 'Glande produisant la salive, qui humidifie la bouche et les aliments et contribue à la digestion et à la protection buccale.'),
    'pituitary': ('Endocrine gland at the base of the brain, linked to the hypothalamus. Its hormones participate in growth, reproduction and regulation of other endocrine glands.', 'Glande endocrine à la base du cerveau, liée à l’hypothalamus. Ses hormones participent à la croissance, à la reproduction et à la régulation d’autres glandes endocrines.'),
    'prostate': ('Gland below the bladder surrounding the beginning of the male urethra. Its secretions contribute to seminal fluid.', 'Glande sous la vessie entourant le début de l’urètre masculin. Ses sécrétions contribuent au liquide séminal.'),
    'testis': ('Gonad in the scrotum producing sperm and hormones, including testosterone. Internal seminiferous tubules are not modelled.', 'Gonade située dans le scrotum, produisant les spermatozoïdes et des hormones, dont la testostérone. Les tubes séminifères internes ne sont pas modélisés.'),
    'epididymis': ('Coiled duct associated with the testis, involved in sperm maturation and storage.', 'Conduit enroulé associé au testicule, participant à la maturation et au stockage des spermatozoïdes.'),
    'seminal': ('Paired gland behind the bladder whose secretion contributes to seminal fluid.', 'Glande paire derrière la vessie dont la sécrétion contribue au liquide séminal.'),
    'cavernosum': ('Erectile tissue contributing to penile rigidity as it fills with blood. The two corpora cavernosa are grouped in this source surface. This is not a separately dissected skeletal muscle; microscopic smooth muscle is not resolved.', 'Tissu érectile contribuant à la rigidité du pénis lorsqu’il se remplit de sang. Les deux corps caverneux sont regroupés dans cette surface source. Il ne s’agit pas d’un muscle strié individualisé ; les muscles lisses microscopiques ne sont pas représentés.'),
    'spongiosum': ('Erectile tissue surrounding the spongy urethra and continuing distally into the glans, which is separately selectable here. The bulbospongiosus muscle covering its root is absent from the imported muscle sources.', 'Tissu érectile entourant l’urètre spongieux et se prolongeant à son extrémité par le gland, sélectionnable séparément ici. Le muscle bulbo-spongieux qui recouvre sa racine est absent des sources musculaires importées.'),
    'glans': ('Distal expansion of the corpus spongiosum. The external urethral opening is at its tip. This surface does not include the foreskin, fine nerves or microscopic tissue structure.', 'Dilatation terminale du corps spongieux. L’orifice externe de l’urètre se situe à son extrémité. Cette surface ne comprend pas le prépuce, les nerfs fins ni la structure microscopique des tissus.'),
}
summaries = {'organ_' + k: v for k, v in _SUMMARIES.items()}


def provenance(source, entries):
    return {
        'source': 'BodyParts3D 4.0', 'structures': len(entries),
        'sourceElements': sum(len(files) for _, _, files, _, _ in entries),
        'partOfMetadataSha256': hashlib.sha256((source / 'partof_elements.tsv').read_bytes()).hexdigest(),
        'modifications': 'Original source positions and mesh detail; OBJ to GLB; smooth normals and illustrative tissue colours. Explicit parenchymal/wall subset excludes vascular trees and cavity casts. Intestinal loops and thymic lobes grouped by organ.',
        'grouping': {
            'FMA7088': 'Only ventricular wall FJ2428 and atrial walls FJ2438/FJ2439. FJ2428 is classified as ventricular wall by IS-A although missing from the PART-OF heart listing.',
            'FMA7197': 'Eight liver parts FJ2816 and FJ2818–FJ2824 combined without assigning segment numbers. FJ2409 overlaps the FJ2822 surface and is excluded. Source sector/segment labels are inconsistent and are not exposed as individual segment identifications.',
            'FMA7198': 'Use FJ1895, the source pancreas concept. FJ2629 is an overlapping parenchymal variant and is excluded.',
            'FMA7208': '31 ileal elements from the IS-A zone-of-ileum set; FJ2599 appears separately as cecum/ileocecal junction.',
        },
        'limitations': 'Selected organ surfaces of an adult male, not a complete visceral system. No bulbospongiosus or ischiocavernosus muscle meshes in the imported sources. No separately identified sigmoid colon or parotid glands in this subset. Brain and peripheral nerves, vascular networks, female reproductive organs, histology and moving physiology are not included. Primary display systems and regions are navigation groups, not exclusive physiological classifications.',
    }
