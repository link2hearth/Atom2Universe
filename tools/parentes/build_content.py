"""Build Android EN/FR resources and original, source-linked editorial annotations.
Run after import_catalogue.py. Scientific names below resolve against imported IDs;
missing names fail the build rather than being assigned invented relationships.
"""
import json
from pathlib import Path
from data_paths import SOURCE
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/parentes'
tree = json.loads((SOURCE / 'tree.json').read_text(encoding='utf-8'))
names = {n['scientific']: n['id'] for n in tree['nodes'] if n['scientific']}
strings = json.loads((ROOT / 'app/build/parentes-import/labels.json').read_text(encoding='utf-8'))

def text(key, en, fr):
    strings[key] = {'en': en, 'fr': fr}
    return key

sources = [
    ('wikidata', 'Wikidata contributors (2026-10-05). Wikidata.', 'https://www.wikidata.org/wiki/Wikidata:Licensing', 'CC0 1.0', 'https://creativecommons.org/publicdomain/zero/1.0/'),
    ('asgard', 'Eme L., Tamarit D., Caceres E. F. et al. (2023). Inference and reconstruction of the heimdallarchaeial ancestry of eukaryotes. Nature 618:992–999.', 'https://doi.org/10.1038/s41586-023-06186-2', 'CC BY 4.0', 'https://creativecommons.org/licenses/by/4.0/'),
    ('opentree', 'Open Tree of Life — opentree16.1 (2025-12-20), OTT 3.7draft3; Hinchliff et al. (2015), doi:10.1073/pnas.1423041112', 'https://files.opentreeoflife.org/synthesis/opentree16.1/opentree16.1/', 'CC0; prior terms may apply', 'https://tree.opentreeoflife.org/opentree/about/licenses'),
    ('flight', 'Cao T. & Jin J.-P. (2020). Evolution of Flight Muscle Contractility and Energetic Efficiency. Frontiers in Physiology 11:1038.', 'https://doi.org/10.3389/fphys.2020.01038', 'CC BY 4.0', 'https://creativecommons.org/licenses/by/4.0/'),
    ('hair', 'Sun X., Zhang Z., Sun Y., Li J., Xu S. & Yang G. (2017). Comparative genomics analyses of alpha-keratins reveal insights into evolutionary adaptation of marine mammals. Frontiers in Zoology 14:41.', 'https://doi.org/10.1186/s12983-017-0225-x', 'CC BY 4.0', 'https://creativecommons.org/licenses/by/4.0/'),
    ('plastids', 'Oborník M. (2019). Endosymbiotic Evolution of Algae, Secondary Heterotrophy and Parasitism. Biomolecules 9:266.', 'https://doi.org/10.3390/biom9070266', 'CC BY 4.0', 'https://creativecommons.org/licenses/by/4.0/'),
]
for key, chapter in [('microbes','22-1-prokaryotic-diversity'),('eukaryotes','23-1-eukaryotic-origins'),('fungi','24-1-characteristics-of-fungi'),('plants','25-1-early-plant-life'),('animals','27-1-features-of-the-animal-kingdom'),('invertebrates','28-introduction'),('vertebrates','29-introduction')]:
    sources.append((key, 'Clark M. A., Douglas M. & Choi J. (2018). Biology 2e. OpenStax, Rice University.', 'https://openstax.org/books/biology-2e/pages/'+chapter, 'CC BY-NC-SA 4.0', 'https://creativecommons.org/licenses/by-nc-sa/4.0/'))

groups = []
def group(scientific, key, en, fr, body_en, body_fr, source):
    key = text('pt_group_'+key, en, fr)
    text(key+'_body', body_en, body_fr)
    groups.append({'id':names[scientific], 'key':key, 'sources':['opentree',source]})

group('cellular organisms','life','Cellular life','Vivant cellulaire',
    'This is a teaching selection, not a census of biodiversity. LUCA means the last universal common ancestor of living cellular organisms, not necessarily the first life. This root is a source-tree node, not an identified fossil.',
    'Ceci est une sélection pédagogique, pas un inventaire de la biodiversité. LUCA désigne le dernier ancêtre commun universel du vivant cellulaire actuel, pas nécessairement la première vie. Cette racine est un nœud de l’arbre source, pas un fossile identifié.', 'microbes')
group('Bacteria','bacteria','Bacteria','Bactéries','These cells have no nucleus. Their ways of obtaining energy are very diverse. Horizontal gene transfer means a single tree cannot tell the history of every gene.','Ces cellules n’ont pas de noyau. Leurs façons d’obtenir de l’énergie sont très diverses. Les transferts horizontaux de gènes empêchent un arbre unique de raconter l’histoire de tous les gènes.','microbes')
group('Archaea','archaea','Archaea','Archées','Archaea have no nucleus but differ from bacteria in important cellular features. They also live in ordinary environments, not only extreme ones. The deep divisions shown here follow the source and are not a settled three-domain phylogeny.','Les archées n’ont pas de noyau mais diffèrent des bactéries par des caractères cellulaires importants. Elles vivent aussi dans des milieux ordinaires. Les divisions profondes suivent la source : elles ne représentent pas une phylogénie à trois domaines définitivement établie.','microbes')
group('Eukaryota','eukaryotes','Eukaryotes','Eucaryotes','Their cells have a nucleus in at least part of the life cycle. Animals, fungi, plants and many unicellular organisms belong here. Endosymbioses are essential to their history.','Leurs cellules possèdent un noyau au moins durant une partie du cycle de vie. Animaux, champignons, plantes et de nombreux unicellulaires en font partie. Les endosymbioses sont essentielles à leur histoire.','eukaryotes')
group('Opisthokonta','opisthokonts','Opisthokonts','Opisthocontes','Animals and fungi share a branch with several unicellular lineages. A mushroom is not a plant: resemblance and ways of life do not define ancestry.','Animaux et champignons partagent une branche avec plusieurs lignées unicellulaires. Un champignon n’est pas une plante : la ressemblance et le mode de vie ne définissent pas la parenté.','eukaryotes')
group('Metazoa','animals','Animals','Animaux','Multicellular organisms that obtain organic matter from other organisms. Most animal diversity is outside the vertebrates. The selected tips do not represent the relative sizes of the groups.','Organismes pluricellulaires qui obtiennent leur matière organique d’autres organismes. L’essentiel de la diversité animale se trouve hors des vertébrés. Les espèces choisies ne représentent pas la taille relative des groupes.','animals')
group('Bilateria','bilaterians','Bilaterians','Bilatériens','This lineage includes insects, molluscs and vertebrates. Bilateral organization can change during development: adult starfish do not resemble their bilaterally organized larvae.','Cette lignée comprend insectes, mollusques et vertébrés. L’organisation bilatérale peut se transformer durant le développement : les étoiles de mer adultes ne ressemblent pas à leurs larves bilatérales.','invertebrates')
group('Arthropoda','arthropods','Arthropods','Arthropodes','Jointed appendages and an external skeleton characterize this diverse group. Growth involves moulting. Insects are only one of its branches.','Des appendices articulés et un squelette externe caractérisent ce groupe varié. La croissance implique des mues. Les insectes ne constituent qu’une de ses branches.','invertebrates')
group('Insecta','insects','Insects','Insectes','The adult body typically has three pairs of legs. Wings occur in many lineages, but some insects are wingless; life stage and sex can matter when describing flight.','Le corps adulte possède typiquement trois paires de pattes. Beaucoup de lignées ont des ailes, mais certains insectes en sont dépourvus ; le stade de vie et le sexe comptent pour décrire le vol.','invertebrates')
group('Mollusca','molluscs','Molluscs','Mollusques','A mantle is part of their shared body organization. Shells can be external, internal or reduced: an octopus and a mussel still belong to the same broad lineage.','Le manteau fait partie de leur organisation commune. La coquille peut être externe, interne ou réduite : un poulpe et une moule appartiennent néanmoins à la même grande lignée.','invertebrates')
group('Cnidaria','cnidarians','Cnidarians','Cnidaires','Stinging cells help these animals capture prey or defend themselves. Polyps and jellyfish illustrate different body forms within this lineage.','Des cellules urticantes aident ces animaux à capturer leurs proies ou à se défendre. Polypes et méduses illustrent des formes différentes au sein de cette lignée.','invertebrates')
group('Echinodermata','echinoderms','Echinoderms','Échinodermes','These marine animals include starfish and sea urchins. Their adult radial organization does not place them outside the bilaterians.','Ces animaux marins comprennent étoiles de mer et oursins. Leur organisation radiaire adulte ne les place pas hors des bilatériens.','invertebrates')
group('Tetrapoda','tetrapods','Tetrapods','Tétrapodes','This lineage inherited a four-limbed body plan. Limbs have been transformed or lost in some descendants: membership is about ancestry, not a count of visible legs.','Cette lignée a hérité d’un plan corporel à quatre membres. Ceux-ci ont été transformés ou perdus chez certains descendants : la parenté ne dépend pas du nombre de pattes visibles.','vertebrates')
group('Amniota','amniotes','Amniotes','Amniotes','Embryonic membranes, including the amnion, are part of this lineage’s inheritance. Mammals and reptiles, including birds, are represented here.','Les membranes embryonnaires, dont l’amnios, font partie de l’héritage de cette lignée. Mammifères et reptiles, oiseaux compris, sont représentés ici.','vertebrates')
group('Mammalia','mammals','Mammals','Mammifères','Milk production and hair are characteristic of the mammalian lineage. Hair can be strongly reduced. Bats, whales and platypuses show how different descendants can become.','La production de lait et les poils caractérisent la lignée des mammifères. Les poils peuvent être fortement réduits. Chauves-souris, baleines et ornithorynques montrent combien les descendants peuvent différer.','hair')
group('Aves','birds','Birds','Oiseaux','Feathers are inherited within this lineage, including in birds that do not fly. Their presence does not by itself establish an ability to fly.','Les plumes sont héritées au sein de cette lignée, y compris chez les oiseaux qui ne volent pas. Leur présence ne suffit donc pas à établir une capacité de vol.','vertebrates')
group('Chiroptera','bats','Bats','Chauves-souris','These mammals fly actively with forelimbs supporting a skin membrane. Their flight evolved independently of bird flight; the forelimbs themselves have a deeper shared history.','Ces mammifères volent activement avec des membres antérieurs portant une membrane de peau. Leur vol a évolué indépendamment de celui des oiseaux ; les membres eux-mêmes ont une histoire commune plus ancienne.','flight')
group('Fungi','fungi','Fungi','Champignons','Fungi absorb nutrients from their surroundings. Many grow as filaments, while yeasts illustrate unicellular forms. They are evolutionarily closer to animals than to green plants.','Les champignons absorbent les nutriments de leur milieu. Beaucoup forment des filaments ; les levures illustrent des formes unicellulaires. Ils sont évolutivement plus proches des animaux que des plantes vertes.','fungi')
group('Ascomycota','ascomycetes','Ascomycetes','Ascomycètes','Sexual spores form in structures called asci. This large group includes the baker’s yeast and the red bread mould selected in this atlas.','Les spores sexuelles se forment dans des structures nommées asques. Ce grand groupe comprend la levure de boulanger et la moisissure rouge du pain choisies dans cet atlas.','fungi')
group('Basidiomycota','basidiomycetes','Basidiomycetes','Basidiomycètes','Sexual spores are borne on basidia. Many familiar mushrooms belong here, but their visible fruiting bodies are only part of the organism.','Les spores sexuelles sont portées par des basides. Beaucoup de champignons familiers en font partie, mais leur fructification visible n’est qu’une partie de l’organisme.','fungi')
group('Chloroplastida','green','Green plants and algae','Plantes et algues vertes','This lineage includes green algae and land plants. Their plastids have a history of endosymbiosis; the host tree alone cannot depict that history.','Cette lignée comprend algues vertes et plantes terrestres. Leurs plastes ont une histoire d’endosymbiose ; l’arbre des hôtes ne suffit pas à la représenter.','plastids')
group('Embryophyta','land','Land plants','Plantes terrestres','The embryo is retained and nourished by parental tissues. The lineage includes plants that later returned to aquatic habitats.','L’embryon est retenu et nourri par les tissus parentaux. La lignée comprend aussi des plantes retournées secondairement dans les milieux aquatiques.','plants')
group('Tracheophyta','vascular','Vascular plants','Plantes vasculaires','Specialized conducting tissues transport water and other substances. Ferns and seed plants belong to this branch.','Des tissus conducteurs spécialisés transportent l’eau et d’autres substances. Fougères et plantes à graines appartiennent à cette branche.','plants')
group('Spermatophyta','seeds','Seed plants','Plantes à graines','Seeds protect an embryo and provide reserves. A pine and an oak share this inheritance even though only the oak produces flowers and fruits.','Les graines protègent un embryon et fournissent des réserves. Un pin et un chêne partagent cet héritage, même si seul le chêne produit des fleurs et des fruits.','plants')
group('Cyanobacteria','cyanobacteria','Cyanobacteria','Cyanobactéries','These bacteria perform oxygen-producing photosynthesis. Plastids trace their origins to cyanobacterial endosymbionts; no species shown here is claimed to be that ancestor.','Ces bactéries réalisent une photosynthèse qui produit du dioxygène. Les plastes proviennent d’endosymbiotes cyanobactériens ; aucune espèce affichée ici n’est présentée comme cet ancêtre.','plastids')

def journey(key, en, fr, steps):
    key = text('pt_'+key,en,fr)
    result=[]
    for i,(e,f,targets,refs) in enumerate(steps):
        result.append({'text':text(key+'_'+str(i),e,f),'targets':[names[n] for n in targets],'sources':refs})
    return {'key':key,'steps':result}

heritages = [
journey('hair','Hair: inheritance and reduction','Poils : héritage et réduction',[
 ('Hair belongs to the mammalian inheritance. Highlighting the mammal branch gives context; it does not date the origin of hair or place its first appearance at this exact node.','Les poils appartiennent à l’héritage des mammifères. La branche mise en évidence donne le contexte ; elle ne date pas leur origine et ne place pas leur première apparition sur ce nœud précis.',['Mammalia'],['hair']),
 ('A seal and a dolphin are both mammals. The seal retains fur; dolphins have greatly reduced hair. An inherited character can be transformed without erasing ancestry.','Phoque et dauphin sont deux mammifères. Le phoque garde un pelage ; les dauphins ont des poils fortement réduits. Un caractère hérité peut être transformé sans effacer la parenté.',['Phoca vitulina','Tursiops truncatus'],['hair']),
 ('Hair and active flight are different characters. A bat is part of the mammal lineage just as a cat is. The highlighted paths show their relationship, not a reconstructed history of every hair-related gene.','Poils et vol actif sont des caractères différents. Une chauve-souris appartient à la lignée des mammifères comme un chat. Les chemins montrent leur parenté, pas une reconstitution de chaque gène lié aux poils.',['Myotis myotis','Felis catus'],['hair','flight'])]),
journey('flight','Active flight: independent histories','Vol actif : histoires indépendantes',[
 ('Here active flight means generating lift and thrust with muscle-powered wing beats, not simply gliding. The paths connect a bird and a bat; their common ancestor is not inferred to have flown.','Ici, le vol actif consiste à produire portance et propulsion par des battements d’ailes musculaires, et non à simplement planer. Les chemins relient un oiseau et une chauve-souris ; ils ne signifient pas que leur ancêtre commun volait.',['Columba livia','Myotis myotis'],['flight']),
 ('Insect flight is another independent history. A bee’s wing and a bird’s wing serve a similar function but are not the same inherited anatomical structure. The connecting path is not an “origin of flight” branch.','Le vol des insectes a une autre histoire indépendante. L’aile d’une abeille et celle d’un oiseau ont une fonction semblable, mais ne sont pas la même structure anatomique héritée. Le chemin de parenté n’est pas une branche « d’origine du vol ».',['Apis mellifera','Columba livia'],['flight']),
 ('Not all birds fly. An ostrich retains feathers and modified forelimbs. Loss of aerial flight does not remove an organism from its ancestral lineage. This is a documented example, not an automatic inference from our small sample.','Tous les oiseaux ne volent pas. Une autruche garde des plumes et des membres antérieurs modifiés. La perte du vol aérien ne fait pas sortir une espèce de sa lignée. Cet exemple est documenté, pas déduit automatiquement de notre petit échantillon.',['Struthio camelus','Columba livia'],['flight','vertebrates'])]),
journey('photosynthesis','Photosynthesis: a history of partnerships','Photosynthèse : histoire de partenariats',[
 ('This trail concerns oxygen-producing photosynthesis. A cyanobacterium and a green alga are distant on the host tree, yet plastids originated from cyanobacterial endosymbionts. The relationship paths do not draw this transfer between lineages.','Ce parcours concerne la photosynthèse productrice de dioxygène. Une cyanobactérie et une algue verte sont éloignées dans l’arbre des hôtes, mais les plastes proviennent d’endosymbiotes cyanobactériens. Les chemins de parenté ne dessinent pas ce transfert entre lignées.',['Nostoc commune','Chlamydomonas reinhardtii'],['plastids']),
 ('Green algae and land plants share an inherited plastid history. Endosymbiosis involved integration of a partner cell and transfers of genes; it was not simply the appearance of a green surface.','Algues vertes et plantes terrestres partagent une histoire héritée de leurs plastes. L’endosymbiose implique l’intégration d’une cellule partenaire et des transferts de gènes, pas la simple apparition d’une surface verte.',['Chlamydomonas reinhardtii','Arabidopsis thaliana'],['plastids']),
 ('Euglena and diatoms illustrate more complex plastid histories involving eukaryotic partners: green-algal ancestry for euglenid plastids, red-algal ancestry for diatom plastids. A single host tree is insufficient, and the number and order of some ancient events remain debated.','Euglènes et diatomées illustrent des histoires plus complexes impliquant des partenaires eucaryotes : ascendance d’algue verte pour les plastes des euglènes, d’algue rouge pour ceux des diatomées. Un arbre des hôtes ne suffit pas ; le nombre et l’ordre de certains événements anciens restent débattus.',['Euglena gracilis','Thalassiosira pseudonana'],['plastids'])])]

tours = [
journey('tour_fork','Read a fork','Lire une bifurcation',[
 ('Follow the two paths towards their meeting point. That node is their most recent common ancestor in this source tree, even if it has no familiar name. Neither living species is the ancestor of the other.','Suivez les deux chemins jusqu’à leur rencontre. Ce nœud est leur plus récent ancêtre commun dans cet arbre source, même sans nom familier. Aucune des deux espèces actuelles n’est l’ancêtre de l’autre.',['Apis mellifera','Bombus terrestris'],['opentree']),
 ('Branch lengths here are layout choices, not time intervals. Several outgoing branches can preserve an unresolved relationship; the selection can also hide branches of species absent from the catalogue.','La longueur des branches est un choix de dessin, pas une durée. Plusieurs branches peuvent conserver une relation non résolue ; la sélection peut aussi masquer les branches d’espèces absentes du catalogue.',['Insecta'],['opentree'])]),
journey('tour_life','Across cellular life','Parcourir le vivant',[
 ('Start with a selection of cellular organisms. Counts reflect our editorial selection, not the true abundance or diversity of each group. No branch is a higher stage of evolution.','Partez d’une sélection d’organismes cellulaires. Les effectifs reflètent notre choix éditorial, pas l’abondance ou la diversité réelle de chaque groupe. Aucune branche n’est un stade supérieur de l’évolution.',['cellular organisms'],['opentree']),
 ('A bacterium and an archaeon can both lack a nucleus without belonging to the same lineage. Deep relationships and horizontal transfers require more than this simplified view.','Une bactérie et une archée peuvent toutes deux manquer de noyau sans appartenir à la même lignée. Les parentés profondes et les transferts horizontaux demandent plus que cette vue simplifiée.',['Escherichia coli','Halobacterium salinarum'],['microbes']),
 ('Fungi are eukaryotes too. Compare a yeast with a plant, then explore their branches. Being fixed in place or microscopic does not define a family.','Les champignons sont aussi des eucaryotes. Comparez une levure à une plante, puis explorez leurs branches. Être fixé sur place ou microscopique ne définit pas une famille.',['Saccharomyces cerevisiae','Arabidopsis thaliana'],['fungi','eukaryotes'])]),
journey('tour_mammals','Mammals in many forms','Mammifères sous toutes leurs formes',[
 ('Open the mammal branch. These representatives share an ancestry, despite striking differences in shape, habitat and reproduction.','Ouvrez la branche des mammifères. Ces représentants partagent une ascendance malgré leurs différences de forme, d’habitat et de reproduction.',['Mammalia'],['vertebrates']),
 ('The platypus lays eggs and the mouse does not. Both are mammals: no single everyday resemblance can replace the history recorded in multiple characters and molecular data.','L’ornithorynque pond des œufs, la souris non. Tous deux sont des mammifères : une ressemblance du quotidien ne remplace pas l’histoire étudiée à partir de multiples caractères et de données moléculaires.',['Ornithorhynchus anatinus','Mus musculus'],['vertebrates'])]),
journey('tour_similarity','A misleading resemblance','Une ressemblance trompeuse',[
 ('A dolphin and a shark both swim with streamlined bodies. This way of life does not make them close relatives. Explore the two paths, then look at a dolphin’s relationship with another mammal.','Dauphin et requin nagent avec un corps fuselé. Ce mode de vie ne fait pas d’eux de proches parents. Explorez les deux chemins, puis la parenté du dauphin avec un autre mammifère.',['Tursiops truncatus','Carcharodon carcharias'],['opentree','vertebrates']),
 ('The dolphin shares a more recent common ancestor with the hippopotamus in this tree. Aquatic life is a way of life, not a single anatomical character with one origin across all animals.','Dans cet arbre, le dauphin partage un ancêtre commun plus récent avec l’hippopotame. La vie aquatique est un mode de vie, pas un caractère anatomique unique apparu une seule fois chez tous les animaux.',['Tursiops truncatus','Hippopotamus amphibius'],['opentree','hair'])])]

ui = {
'title':('Kinships — the tree of life','Parentés — l’arbre du vivant'),
'hub':('Explore living lineages, compare species and follow biological inheritances.','Explorez les lignées du vivant, comparez les espèces et suivez leurs héritages.'),
'back':('Back','Retour'),'overview':('Overview','Vue générale'),'parent':('Parent group','Groupe parent'),
'search':('Search','Rechercher'),'search_hint':('French, English or scientific name','Nom français, anglais ou scientifique'),
'list':('Accessible list','Liste accessible'),'compare':('Compare','Comparer'),'heritages':('Follow an inheritance','Suivre un héritage'),
'tours':('Guided trails','Parcours guidés'),'sources':('Sources and method','Sources et méthode'),
'loading':('Loading the offline atlas…','Chargement de l’atlas hors ligne…'),
'error':('The offline catalogue could not be loaded. Please reopen the module.','Le catalogue hors ligne n’a pas pu être chargé. Veuillez rouvrir le module.'),
'unnamed':('Unnamed ancestor','Ancêtre sans nom'),
'selection':('Teaching selection · no time scale','Sélection pédagogique · sans échelle de temps'),
'hint':('Drag to move, pinch to zoom. Touch a node to read its card. Open a group to explore further.','Glissez pour déplacer, pincez pour zoomer. Touchez un nœud pour lire sa fiche. Ouvrez un groupe pour poursuivre.'),
'open':('Explore this group','Explorer ce groupe'),'details':('Read more','En savoir plus'),'less':('Show less','Réduire'),
'count':('%1$d species in this selection','%1$d espèces dans cette sélection'),
'members':('Representatives in this catalogue','Représentants dans ce catalogue'),
'position':('Position: %1$s','Position : %1$s'),
'species_body':('A present-day species in our selection. Explore its position within %1$s. Its neighbours on screen are selected representatives, not a complete inventory of its relatives.','Une espèce actuelle de notre sélection. Explorez sa position au sein de %1$s. Ses voisines à l’écran sont des représentants choisis, pas un inventaire complet de ses parentes.'),
'node_body':('This source node connects the lineages shown below. Its identity is retained even when intermediate nodes are hidden for readability. A named taxon is not automatically the exact ancestor of two selected species.','Ce nœud de la source relie les lignées présentées. Son identité est conservée même lorsque des étapes intermédiaires sont masquées pour la lisibilité. Un taxon nommé n’est pas automatiquement l’ancêtre exact de deux espèces choisies.'),
'node_id':('Source node: %1$s','Nœud source : %1$s'),
'support_study':('Source annotation: supported by at least one study. This is not a numerical confidence score.','Annotation de la source : appui d’au moins une étude. Ce n’est pas un score numérique de confiance.'),
'support_taxonomy':('Source annotation: taxonomic placement only, or no direct study support reported for this node.','Annotation de la source : placement taxonomique seul, ou absence d’appui direct d’étude signalé pour ce nœud.'),
'conflicts':('The source also reports conflicting study relationships here.','La source signale aussi des relations contradictoires dans les études à cet endroit.'),
'polytomy':('Several branches leave this node. Their order is not resolved further in this selection; it does not imply simultaneous divergences.','Plusieurs branches partent de ce nœud. Leur ordre n’est pas davantage résolu dans cette sélection ; cela ne signifie pas des divergences simultanées.'),
'a':('A · %1$s','A · %1$s'),'b':('B · %1$s','B · %1$s'),
'choose_a':('Choose species A','Choisir l’espèce A'),'choose_b':('Choose species B','Choisir l’espèce B'),
'choose':('Choose a species','Choisir une espèce'),'none':('No matches','Aucun résultat'),
'comparison':('Most recent common ancestor: %1$s. A uses a solid line and circles; B uses a dashed line and squares. Neither living species is the ancestor of the other.','Plus récent ancêtre commun : %1$s. A suit un trait plein et des cercles ; B un trait discontinu et des carrés. Aucune espèce actuelle n’est l’ancêtre de l’autre.'),
'same':('You selected the same species twice. Its node is the result; choose a different species to explore a divergence.','Vous avez choisi deux fois la même espèce. Son nœud est le résultat ; choisissez une autre espèce pour explorer une divergence.'),
'comparison_limits':('The result is the meeting point in this source tree, not a genetic similarity percentage or an identified ancestral species. Hidden nodes remain in the calculation.','Le résultat est le point de rencontre dans cet arbre source, pas un pourcentage de ressemblance génétique ni une espèce ancestrale identifiée. Les nœuds masqués restent dans le calcul.'),
'path_a':('A · full source path','A · chemin source complet'),'path_b':('B · full source path','B · chemin source complet'),
'path_item':('%1$s · %2$s','%1$s · %2$s'),
'step':('Step %1$d of %2$d','Étape %1$d sur %2$d'),'next':('Next','Suivant'),'previous':('Previous','Précédent'),
'exit_trail':('Leave trail','Quitter le parcours'),
'trail_note':('Highlighted paths locate the examples. They do not automatically reconstruct character origins.','Les chemins situent les exemples. Ils ne reconstruisent pas automatiquement l’origine des caractères.'),
'method':('The atlas works offline. External reference links need a browser and a connection. The catalogue preserves every imported lineage node from Open Tree; the map only hides intermediate nodes. The source combines phylogenetic studies with taxonomy, and unresolved forks are not artificially resolved. Branch lengths, order around the tree and the number of examples are editorial choices, not time, evolutionary progress or real diversity. Deep divisions follow this version of the source: in particular, its separate Bacteria, Archaea and Eukaryota branches should not be read as a settled three-domain evolutionary hypothesis. Many analyses place eukaryotes within Archaea. Horizontal gene transfer and endosymbioses cannot be represented fully by this host tree.\n\nThe original selection has editorial common names; additional names are Wikidata labels, with the scientific name used when no common label is available. Educational group articles are original EN/FR text. Wikidata licensing was consulted but no Wikidata data, images or Wikipedia text are imported. No data from the inspiration website are used. No fossil dates, predicted traits or genetic proximity percentages are supplied. The illustrated lineage stories use selected milestones on source-tree paths. Their character explanations are sourced editorial text, not automatic inferences from the selected species. Original schematic drawings illustrate structures rather than identified ancestors. Chapter references are available here, under illustrated stories.',
'L’atlas fonctionne hors ligne. Les liens externes demandent un navigateur et une connexion. Le catalogue conserve tous les nœuds des lignées importées depuis Open Tree ; la carte masque seulement des étapes intermédiaires. La source combine études phylogénétiques et taxonomie ; les embranchements non résolus ne sont pas artificiellement précisés. Longueurs, ordre autour de l’arbre et nombre d’exemples sont des choix éditoriaux, pas des durées, un progrès évolutif ou la diversité réelle. Les divisions profondes suivent cette version de la source : ses branches distinctes Bactéries, Archées et Eucaryotes ne constituent notamment pas une hypothèse évolutive à trois domaines définitivement établie. De nombreuses analyses placent les eucaryotes au sein des archées. Transferts horizontaux et endosymbioses ne peuvent pas être entièrement représentés par cet arbre des hôtes.\n\nLes noms de la sélection initiale sont éditoriaux ; les ajouts utilisent les libellés de Wikidata, ou le nom scientifique lorsqu’aucun nom courant n’est disponible. Les fiches pédagogiques de groupes sont rédigées en français et en anglais. La licence de Wikidata a été consultée, mais aucune donnée Wikidata, image ou texte Wikipédia n’est importé. Aucune donnée du site d’inspiration n’est utilisée. Aucune date fossile, valeur de caractère prédite ou proximité génétique chiffrée n’est fournie. Les récits illustrés suivent des jalons choisis sur les chemins de l’arbre source. Les explications des caractères sont des textes éditoriaux sourcés, pas des déductions automatiques à partir des espèces choisies. Les schémas originaux illustrent des structures et non des ancêtres identifiés. Les références par chapitre figurent ici, dans les récits illustrés.'),
'version':('Open Tree: %1$s · OTT: %2$s\nExtracted: %3$s\n%4$d selected species · %5$d group articles · %6$d source nodes','Open Tree : %1$s · OTT : %2$s\nExtraction : %3$s\n%4$d espèces choisies · %5$d fiches de groupes · %6$d nœuds sources'),
'license_note':('Open Tree publishes data under CC0 where prior terms do not limit reuse. This package contains a reduced synthetic topology and source identifiers, not the original articles, figures or study datasets. The source version and node support identifiers are retained for audit. Article licences apply to the cited articles only. Trail explanations are original paraphrases; no supplementary trait dataset is imported.','Open Tree publie ses données sous CC0 lorsque des conditions antérieures ne limitent pas leur réutilisation. Ce paquet contient une topologie synthétique réduite et des identifiants de provenance, pas les articles, figures ou jeux de données originaux des études. La version et les identifiants d’appui des nœuds sont conservés pour vérification. Les licences des articles s’appliquent aux seuls articles cités. Les explications des parcours sont des reformulations originales ; aucun jeu de caractères supplémentaire n’est importé.'),
'license':('Licence: %1$s','Licence : %1$s'),
'cc0_scope':('CC0 — see scope and prior terms','CC0 — voir le périmètre et les conditions antérieures'),
'no_browser':('No application can open this reference.','Aucune application ne peut ouvrir cette référence.'),
'close':('Close','Fermer'),'fit':('Fit tree','Cadrer l’arbre'),
'node_action':('%1$s · open card','%1$s · ouvrir la fiche'),
'list_intro':('The same visible branches, as buttons. Each card gives access to the parent group and representatives. Search includes the full catalogue.','Les mêmes branches visibles, sous forme de boutons. Chaque fiche donne accès au groupe parent et aux représentants. La recherche couvre tout le catalogue.'),
'branch_format':('%1$s → %2$s','%1$s → %2$s'),
'study_link':('Study %1$s','Étude %1$s'),
'taxon_sources':('Taxonomic identifiers: %1$s','Identifiants taxonomiques : %1$s'),
'separator':(' › ',' › '),
'references':('References','Références'),
}
for key,(en,fr) in ui.items(): text('pt_'+key,en,fr)
text('pt_catalogue', 'Species (%1$d)', 'Espèces (%1$d)')
text('pt_explore_tab', 'Explore', 'Explorer')
text('pt_short_title', 'Kinships', 'Parentés')
text('pt_tree_subtitle', 'The tree of life', 'L’arbre du vivant')
text('pt_search_tree', 'Find a species or group', 'Rechercher une espèce, un groupe')
text('pt_trails_tab', 'Trails', 'Parcours')
text('pt_active_mode', 'Selected mode', 'Mode sélectionné')
text('pt_zoom_in', 'Zoom in', 'Agrandir')
text('pt_zoom_out', 'Zoom out', 'Réduire')
text('pt_tip_count', '%1$d species', '%1$d espèces')
text('pt_map_hint', 'Zoom to read species · touch a group', 'Zoomez pour lire les espèces · touchez un groupe')
text('pt_comparison_hint', 'A: solid line · B: dashed line · no time scale', 'A : trait plein · B : pointillés · sans échelle de temps')
text('pt_common_summary', 'Follow the branches to their common ancestor.', 'Suivez les branches jusqu’à leur ancêtre commun.')
text('pt_trail_step', '%1$s · %2$d/%3$d', '%1$s · %2$d/%3$d')
text('pt_common_node', 'Their common ancestor', 'Leur ancêtre commun')
text('pt_junction', 'Branching point', 'Point de divergence')
text('pt_about_title', 'Kinships — sources and credits', 'Parentés — sources et crédits')
text('pt_about_body', 'Open Tree of Life: tree and taxonomy. Wikidata: short descriptions and search aliases. Scientific references, versions and licences for the atlas and its trails.', 'Open Tree of Life : arbre et taxonomie. Wikidata : descriptions courtes et synonymes de recherche. Références scientifiques, versions et licences de l’atlas et de ses parcours.')
text('pt_species_references', 'References by species or group', 'Références par espèce ou groupe')

strings['pt_method']['en'] = strings['pt_method']['en'].replace('Wikidata licensing was consulted but no Wikidata data, images or Wikipedia text are imported.', 'Short structured descriptions and search aliases come from Wikidata (CC0), matched by exact scientific name; ambiguous matches and descriptions requiring review are excluded. Entity revisions are retained. No photographs or Wikipedia text are imported.')
strings['pt_method']['fr'] = strings['pt_method']['fr'].replace('La licence de Wikidata a été consultée, mais aucune donnée Wikidata, image ou texte Wikipédia n’est importé.', 'Des descriptions structurées courtes et des synonymes de recherche proviennent de Wikidata (CC0), reliés par nom scientifique exact ; les correspondances ambiguës et les descriptions nécessitant une vérification sont écartées. Les révisions des entités sont conservées. Aucune photographie ni texte Wikipédia n’est importé.')
text('pt_wikidata_ref','Wikidata: %1$s · revision %2$s','Wikidata : %1$s · révision %2$s')
wiki = json.loads((SOURCE/'wikidata.json').read_text(encoding='utf-8'))
for n in wiki['notes']: text(n['key'],n['en'],n['fr'])
strings['pt_group_life_body']['en'] += ' The three main branches follow the source’s taxonomic overview; current phylogenomic work places eukaryote origins within Archaea. Sources and method: main hub settings, About.'
strings['pt_group_life_body']['fr'] += ' Les trois grandes branches suivent la vue taxonomique de la source ; des travaux phylogénomiques actuels placent l’origine des eucaryotes au sein des archées. Sources et méthode : paramètres du hub principal, rubrique À propos.'
for g in groups:
    if g['id'] in [names['Archaea'], names['Eukaryota'], names['cellular organisms']]:
        g['sources'].append('asgard')

content = {'schema':1,'groups':groups,'heritages':heritages,'tours':tours,
 'sources':[dict(zip(['id','citation','url','license','license_url'],s)) for s in sources]}
(ASSETS/'content.json').write_text(json.dumps(content,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for lang, folder in [('en','values'),('fr','values-fr')]:
    def android(s): return escape(s).replace("'", "\\'").replace('"','\\"').replace('\n','\\n')
    xml='<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
    xml+=''.join(f'    <string name="{key}">{android(value[lang])}</string>\n' for key,value in sorted(strings.items()))
    xml+='</resources>\n'
    (ROOT/f'app/src/main/res/{folder}/strings_parentes.xml').write_text(xml,encoding='utf-8')
print(f'{len(groups)} group articles; {len(heritages)} inheritance trails; {len(tours)} guided trails; {len(strings)} bilingual resources')
