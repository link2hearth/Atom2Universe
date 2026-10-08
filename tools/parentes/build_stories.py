"""Original EN/FR illustrated narratives, with source-checked lineage milestones.

No photograph, borrowed figure, fossil reconstruction or inferred trait dataset.
Illustrations are native schematic drawings. Every fork uses the source-tree LCA.
"""
import json
from pathlib import Path
from data_paths import SOURCE
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'app/src/main/assets/parentes'
tree = json.loads((SOURCE / 'tree.json').read_text(encoding='utf8'))
nodes = {n['id']: n for n in tree['nodes']}
names = {n['scientific']: n['id'] for n in nodes.values() if n['scientific']}
strings = {}
chapters = {}


def text(key, en, fr):
    key = 'pt_story_' + key
    strings[key] = (en, fr)
    return key


def chapter(key, group, art, title, body, caption, source):
    chapters[key] = dict(node=names[group], art=art,
        title=text(key + '_title', *title), body=text(key + '_body', *body),
        caption=text(key + '_caption', *caption), sources=source.split())


chapter('cell', 'cellular organisms', 'cell',
    ('An inheritance older than animals', 'Un héritage plus ancien que les animaux'),
    ('Before feathers, leaves or hands, there were cells. A membrane separates an inside from an outside; genetic information is copied and passed on. These shared mechanisms point to a very ancient common history. LUCA names the last common ancestor of present-day cellular life, not the first living thing.\n\nThe bacterium on the side branch is a living cousin, not a portrait of LUCA. The root follows this atlas’s source; deep relationships also involve gene transfers, and eukaryotes originated within the archaeal lineage.',
     'Avant les plumes, les feuilles ou les mains, il y a des cellules. Une membrane sépare un intérieur d’un extérieur ; une information génétique se copie et se transmet. Ces mécanismes partagés témoignent d’une histoire commune très ancienne. LUCA désigne le dernier ancêtre commun du vivant cellulaire actuel, pas le premier être vivant.\n\nLa bactérie sur la branche latérale est une cousine actuelle, pas un portrait de LUCA. La racine suit la source de cet atlas ; les parentés profondes impliquent aussi des transferts de gènes, et les eucaryotes ont une origine au sein des archées.'),
    ('Membrane and genetic material: a schematic cell, not a reconstruction of LUCA.',
     'Membrane et matériel génétique : une cellule schématique, pas une reconstitution de LUCA.'), 'microbes asgard')

chapter('eukaryote', 'Eukaryota', 'eukaryote',
    ('A cell with an internal history', 'Une cellule avec une histoire intérieure'),
    ('A nucleus encloses most of the DNA. Mitochondria help supply usable energy; their ancestry traces to bacteria that became integrated into host cells. Over generations, genes and functions were redistributed between the partners.\n\nThis inheritance is shared by animals, fungi and plants. Following one of their branches does not erase the others. The order of all the early steps is not known, and a simple branching tree cannot show the entire history of endosymbiosis.',
     'Un noyau abrite la majeure partie de l’ADN. Les mitochondries contribuent à fournir de l’énergie utilisable ; leur ascendance remonte à des bactéries intégrées dans des cellules hôtes. Au fil des générations, gènes et fonctions se sont redistribués entre les partenaires.\n\nCet héritage appartient aux animaux, aux champignons et aux plantes. Suivre une de ces branches ne fait pas disparaître les autres. L’ordre de toutes les étapes anciennes reste incertain, et un simple arbre ne peut pas montrer toute l’histoire de l’endosymbiose.'),
    ('The central nucleus and the smaller mitochondria are distinct compartments.',
     'Le noyau central et les petites mitochondries sont des compartiments distincts.'), 'eukaryotes')

chapter('opistho', 'Opisthokonta', 'fungus',
    ('Our unexpected fungal relatives', 'Une parenté inattendue avec les champignons'),
    ('Animals and fungi share a common branch more recently than either does with green plants. Their ways of feeding later took different forms: fungi absorb nutrients, while animals generally ingest food.\n\nThe yeast illustrates a fungal branch that continues alongside the one we follow. It is not an animal ancestor. A mushroom’s rooted appearance is therefore a poor guide to its kinship: relationships are reconstructed from many characters, including molecular evidence.',
     'Animaux et champignons partagent une branche commune plus récente que celle qui les relie aux plantes vertes. Leurs modes d’alimentation ont ensuite pris des formes différentes : les champignons absorbent les nutriments, tandis que les animaux ingèrent généralement leur nourriture.\n\nLa levure illustre une branche fongique qui continue à côté de celle que nous suivons. Ce n’est pas un ancêtre des animaux. L’aspect fixé d’un champignon renseigne donc mal sur sa parenté : celle-ci se reconstruit avec de nombreux caractères, notamment moléculaires.'),
    ('A fungal filament and its branching growth; many fungi, including yeast, have other forms.',
     'Un filament fongique qui se ramifie ; d’autres champignons, dont les levures, ont d’autres formes.'), 'fungi')

chapter('animal', 'Metazoa', 'colony',
    ('Cells build a shared body', 'Des cellules construisent un corps commun'),
    ('In the animal lineage, cells cooperate within a multicellular body. They communicate, attach to one another and specialize during development. Feeding, sensing and movement can be distributed among different tissues.\n\nHydra belongs to the cnidarians. The route to our destination instead passes through bilaterians, whose body organization has a left and a right side. Hydra is not an unfinished version of that body: both branches have their own continuing history.',
     'Dans la lignée animale, les cellules coopèrent au sein d’un corps pluricellulaire. Elles communiquent, s’attachent les unes aux autres et se spécialisent pendant le développement. Se nourrir, percevoir et bouger peuvent mobiliser des tissus différents.\n\nL’hydre appartient aux cnidaires. Le chemin vers notre destination passe, lui, par les bilatériens, dont l’organisation corporelle distingue un côté gauche et un côté droit. L’hydre n’est pas une version inachevée de ce corps : les deux branches poursuivent leur propre histoire.'),
    ('A schematic group of cooperating cells, with different cells highlighted in turn.',
     'Un ensemble schématique de cellules coopérantes, mises en évidence tour à tour.'), 'animals')

chapter('chordate', 'Chordata', 'fish',
    ('An axis inside the body', 'Un axe à l’intérieur du corps'),
    ('Chordates share a supporting rod, the notochord, and a dorsal nerve cord at some stage of development. Vertebrates elaborate an internal skeleton around this body plan. Our lineage’s distant aquatic history remains legible in anatomy and embryos.\n\nRay-finned fishes, represented by the zebrafish, branch away from the lineage containing lobe-finned fishes and tetrapods. We did not descend from a modern zebrafish: both lineages descend from older populations.',
     'Les chordés partagent une tige de soutien, la chorde, et un cordon nerveux dorsal à un stade de leur développement. Les vertébrés développent un squelette interne autour de cette organisation. L’histoire aquatique lointaine de notre lignée reste lisible dans l’anatomie et les embryons.\n\nLes poissons à nageoires rayonnées, représentés par le poisson-zèbre, divergent de la lignée qui comprend les poissons à nageoires charnues et les tétrapodes. Nous ne descendons pas d’un poisson-zèbre actuel : les deux lignées proviennent de populations plus anciennes.'),
    ('A fish-shaped schematic highlights the longitudinal supporting axis.',
     'Un schéma de forme pisciforme met en évidence l’axe de soutien longitudinal.'), 'chordates')

chapter('tetrapod', 'Tetrapoda', 'limbs',
    ('Four limbs, many possible lives', 'Quatre membres, des vies très différentes'),
    ('The tetrapod lineage inherited limbs with digits from aquatic ancestors. The transition involved changes over many generations, not a fish suddenly deciding to walk. The same broad limb organization can later be reshaped into an arm, a wing or a flipper.\n\nFrogs belong to the amphibian branch. We continue toward the amniotes. A present-day frog is a relative on another branch, not a compulsory stage in the ancestry of every land vertebrate.',
     'La lignée des tétrapodes a hérité de membres munis de doigts depuis des ancêtres aquatiques. La transition implique des changements sur de nombreuses générations, pas un poisson décidant soudain de marcher. Une même organisation générale peut ensuite devenir un bras, une aile ou une nageoire.\n\nLes grenouilles appartiennent à la branche des amphibiens. Nous continuons vers les amniotes. Une grenouille actuelle est une parente sur une autre branche, pas une étape obligatoire de l’ascendance de chaque vertébré terrestre.'),
    ('One proximal bone, two distal bones, then digits: a simplified limb plan.',
     'Un os proximal, deux os plus distaux, puis des doigts : un plan de membre simplifié.'), 'amphibians')

chapter('amniote', 'Amniota', 'egg',
    ('Protecting the developing embryo', 'Protéger l’embryon en développement'),
    ('The amnion encloses the embryo in a fluid-filled environment. Along with other embryonic membranes, it is a shared inheritance of mammals and reptiles, including birds. It can function inside a laid egg or inside the mother.\n\nHere the mammalian and reptilian branches separate in our story. An eggshell alone does not define this relationship: humans also develop an amnion. The illustration is an egg cutaway, not the universal shape of an amniote embryo.',
     'L’amnios entoure l’embryon d’un milieu rempli de liquide. Avec d’autres membranes embryonnaires, il constitue un héritage partagé par les mammifères et les reptiles, oiseaux compris. Il peut fonctionner dans un œuf pondu ou à l’intérieur de la mère.\n\nLes branches mammalienne et reptilienne se séparent ici dans notre récit. La coquille ne définit pas à elle seule cette parenté : les humains développent aussi un amnios. L’illustration montre une coupe d’œuf, pas la forme universelle d’un embryon d’amniote.'),
    ('Inside the egg, a highlighted membrane surrounds the embryo and its fluid environment.',
     'Dans l’œuf, une membrane mise en évidence entoure l’embryon et son milieu liquide.'), 'reptiles')

chapter('mammal', 'Mammalia', 'mammal',
    ('Milk, hair and transformed bones', 'Du lait, des poils et des os transformés'),
    ('Milk feeds young mammals. Hair can insulate the body or act as a sensory structure, even though it is reduced in some lineages. The mammalian middle ear also includes bones whose evolutionary history is linked to the jaw.\n\nThe platypus lays eggs but produces milk: it is a mammal too. Its monotreme branch diverged before the common ancestor of marsupials and placental mammals. Our route continues among placental mammals toward primates.',
     'Le lait nourrit les jeunes mammifères. Les poils peuvent isoler le corps ou servir à percevoir l’environnement, même s’ils sont réduits dans certaines lignées. L’oreille moyenne des mammifères comprend aussi des os dont l’histoire évolutive est liée à la mâchoire.\n\nL’ornithorynque pond des œufs mais produit du lait : c’est bien un mammifère. Sa branche, celle des monotrèmes, a divergé avant l’ancêtre commun des marsupiaux et des placentaires. Notre chemin continue parmi les placentaires vers les primates.'),
    ('A schematic fur-bearing mammal; hair is highlighted along its back.',
     'Un mammifère schématique portant une fourrure ; les poils sont soulignés sur son dos.'), 'mammals')

chapter('primate', 'Primates', 'hand',
    ('Grasping, looking, moving', 'Saisir, regarder, se déplacer'),
    ('Primates inherited features associated with life in trees: grasping extremities, mobile joints and forward-facing eyes with overlapping fields of view. These features vary and have been modified in different branches.\n\nThe galago represents a strepsirrhine lineage. Our route continues among haplorhines, then apes. We remain primates when we walk on the ground: evolution transforms inherited structures without removing descendants from their ancestral groups.',
     'Les primates ont hérité de caractères associés à la vie arboricole : des extrémités préhensiles, des articulations mobiles et des yeux orientés vers l’avant aux champs visuels superposés. Ces caractères varient et se sont modifiés dans les différentes branches.\n\nLe galago représente une lignée de strepsirrhiniens. Notre chemin continue parmi les haplorhiniens, puis les grands singes. Nous restons des primates lorsque nous marchons au sol : l’évolution transforme les structures héritées sans retirer les descendants de leurs groupes ancestraux.'),
    ('A simplified grasping hand, with the thumb separated from the other digits.',
     'Une main préhensile simplifiée, avec le pouce écarté des autres doigts.'), 'primates')

chapter('human', 'Homo sapiens', 'human',
    ('One surviving twig: us', 'Un rameau actuel : nous'),
    ('Our species emerged in Africa within an already branching human history. Habitual bipedalism, changes in teeth and brains, and cultural innovations did not all appear together. Fossils record a mosaic of features rather than a ladder of increasingly complete humans.\n\nChimpanzees and bonobos are our closest living relatives. Neither is our ancestor. This atlas follows living species, so extinct human relatives are absent from its tips. The final branch is the end of this story, not the goal of evolution.',
     'Notre espèce est apparue en Afrique dans une histoire humaine déjà ramifiée. La bipédie habituelle, les changements dentaires et cérébraux et les innovations culturelles ne sont pas tous apparus ensemble. Les fossiles montrent une mosaïque de caractères plutôt qu’une échelle d’humains de plus en plus achevés.\n\nChimpanzés et bonobos sont nos plus proches parents actuels. Aucun n’est notre ancêtre. Cet atlas suit les espèces vivantes : les parentes humaines éteintes sont donc absentes de ses feuilles. Cette branche termine le récit, pas l’évolution.'),
    ('A modern human silhouette in motion, not a procession of supposed ancestors.',
     'Une silhouette humaine actuelle en mouvement, pas une procession d’ancêtres supposés.'), 'human_origins')

chapter('sauropsid', 'Sauropsida', 'feather',
    ('A dinosaur inheritance', 'Un héritage de dinosaures'),
    ('Birds belong within the reptilian lineage and descend from theropod dinosaurs. Feathers existed in dinosaurs that did not perform powered flight; insulation and display are among their possible functions. Flight later combined modified forelimbs, feathers and many other anatomical changes.\n\nThe crocodile branch is shown as a living relative. A crocodile is not a bird ancestor. Fossils establish the dinosaur connection even where the selected living-species tree hides many intermediate branches.',
     'Les oiseaux appartiennent à la lignée reptilienne et descendent de dinosaures théropodes. Des plumes existaient chez des dinosaures qui ne pratiquaient pas le vol battu ; isolation et parade figurent parmi leurs fonctions possibles. Le vol a ensuite associé membres antérieurs modifiés, plumes et de nombreux autres changements anatomiques.\n\nLa branche des crocodiles représente ici des parents actuels. Un crocodile n’est pas un ancêtre d’oiseau. Les fossiles établissent le lien dinosaurien même lorsque l’arbre d’espèces actuelles masque de nombreuses branches intermédiaires.'),
    ('A feather with a central shaft and branching barbs; a feather alone does not imply flight.',
     'Une plume avec un axe central et des barbes ramifiées ; une plume seule n’implique pas le vol.'), 'birds')

chapter('bird', 'Aves', 'bird',
    ('Wings do not dictate a single destiny', 'Les ailes ne dictent pas un seul destin'),
    ('A bird’s wing is a modified tetrapod forelimb. Its bones share a deeper history with our arms, while the flight surface is built largely from feathers. Bat flight evolved independently, using a skin membrane supported by elongated fingers.\n\nThe ostrich retains feathers and forelimbs but does not fly. Its branch continues alongside the many other bird lineages. Our route enters the group containing pigeons: shared ancestry is more reliable than a simple test of whether an animal can fly.',
     'L’aile d’un oiseau est un membre antérieur de tétrapode modifié. Ses os partagent une histoire profonde avec ceux de nos bras, tandis que la surface de vol est surtout formée de plumes. Le vol des chauves-souris a évolué indépendamment, avec une membrane de peau soutenue par des doigts allongés.\n\nL’autruche conserve plumes et membres antérieurs mais ne vole pas. Sa branche continue aux côtés des autres lignées d’oiseaux. Notre chemin entre dans le groupe qui contient les pigeons : l’ascendance est plus fiable qu’un simple test de capacité à voler.'),
    ('The wing unfolds around the shoulder; its flight feathers extend the forelimb.',
     'L’aile se déploie autour de l’épaule ; les rémiges prolongent le membre antérieur.'), 'flight')

chapter('pigeon', 'Columba livia', 'pigeon',
    ('An ancient history on a city roof', 'Une histoire ancienne sur un toit'),
    ('The rock pigeon nests naturally on cliffs. Buildings offer comparable ledges, and feral populations descend from domesticated birds. A familiar city bird therefore carries both a deep evolutionary history and a much more recent history with humans.\n\nIts branch and the duck’s meet within the bird tree. A duck did not become a pigeon. Look back through the chapters: feathers, embryonic membranes and the cellular machinery are nested inheritances, still present in this ordinary bird.',
     'Le pigeon biset niche naturellement sur les falaises. Les bâtiments offrent des rebords comparables, et les populations férales descendent d’oiseaux domestiqués. Un oiseau urbain familier porte donc à la fois une histoire évolutive profonde et une histoire beaucoup plus récente avec les humains.\n\nSa branche et celle du canard se rejoignent dans l’arbre des oiseaux. Un canard n’est pas devenu un pigeon. Remontez les chapitres : plumes, membranes embryonnaires et machinerie cellulaire sont des héritages emboîtés, toujours présents chez cet oiseau ordinaire.'),
    ('A stylized rock pigeon with a grey body, darker wing bars and a coloured neck.',
     'Un pigeon biset stylisé, au corps gris, aux barres alaires sombres et au cou coloré.'), 'cornell_pigeon')

chapter('green', 'Chloroplastida', 'plastid',
    ('A partnership that captures light', 'Un partenariat qui capte la lumière'),
    ('The plastids of green algae and land plants trace their origin to a cyanobacterial endosymbiont. The partner became integrated into the host cell; photosynthesis uses light to help build organic matter. Plants also retain mitochondria and carry out respiration.\n\nThe green alga represents another descendant of this history. The oak lineage continues among streptophytes. It did not descend from the particular alga shown here, and a host-cell tree alone cannot depict the ancient partnership that produced plastids.',
     'Les plastes des algues vertes et des plantes terrestres remontent à un endosymbiote cyanobactérien. Ce partenaire s’est intégré dans la cellule hôte ; la photosynthèse utilise la lumière pour contribuer à fabriquer de la matière organique. Les plantes conservent aussi des mitochondries et respirent.\n\nL’algue verte représente une autre descendante de cette histoire. La lignée du chêne continue parmi les streptophytes. Elle ne descend pas de l’algue précise montrée ici, et l’arbre des cellules hôtes ne peut pas représenter à lui seul le partenariat ancien à l’origine des plastes.'),
    ('A plant cell with chloroplasts highlighted around its central compartment.',
     'Une cellule végétale dont les chloroplastes sont mis en évidence autour du compartiment central.'), 'plastids eukaryotes')

chapter('land', 'Embryophyta', 'moss',
    ('Keeping an embryo on land', 'Conserver un embryon sur la terre ferme'),
    ('Land plants retain and nourish an embryo in parental tissues. Living away from continuous immersion creates problems of drying, support and reproduction; different lineages solve them in different ways. This is a branching history, not a march toward trees.\n\nThe liverwort represents a non-vascular land-plant lineage. The route to the oak continues toward vascular plants. Modern liverworts have their own evolution; they are not surviving copies of the first land plants.',
     'Les plantes terrestres retiennent et nourrissent un embryon dans des tissus parentaux. Vivre sans immersion permanente pose des problèmes de dessèchement, de soutien et de reproduction ; les différentes lignées y répondent de façons diverses. C’est une histoire ramifiée, pas une marche vers les arbres.\n\nL’hépatique représente une lignée de plantes terrestres non vasculaires. Le chemin vers le chêne continue vers les plantes vasculaires. Les hépatiques actuelles ont leur propre évolution ; elles ne sont pas des copies préservées des premières plantes terrestres.'),
    ('A low, branching thallus represents a liverwort, an example of a living land plant.',
     'Un thalle bas et ramifié représente une hépatique, exemple de plante terrestre actuelle.'), 'plants')

chapter('vascular', 'Tracheophyta', 'vessels',
    ('Moving water through the body', 'Faire circuler l’eau dans le corps'),
    ('Vascular plants have specialized conducting tissues. Xylem transports water and minerals; phloem distributes sugars and other organic compounds. Strengthened tissues also help support growth above the ground.\n\nFerns share this inheritance with seed plants. The bracken branch illustrates a lineage reproducing through spores without making seeds. We continue toward seed plants. A fern is therefore neither a young tree nor a failed flowering plant.',
     'Les plantes vasculaires possèdent des tissus conducteurs spécialisés. Le xylème transporte l’eau et les éléments minéraux ; le phloème distribue des sucres et d’autres composés organiques. Des tissus renforcés contribuent aussi à soutenir la croissance au-dessus du sol.\n\nLes fougères partagent cet héritage avec les plantes à graines. La fougère aigle illustre une lignée qui se reproduit par spores sans fabriquer de graines. Nous continuons vers les plantes à graines. Une fougère n’est donc ni un jeune arbre ni une plante à fleurs inachevée.'),
    ('A stem cutaway highlights conducting strands and upward water movement.',
     'Une coupe de tige souligne les faisceaux conducteurs et la circulation ascendante de l’eau.'), 'vascular')

chapter('seed', 'Spermatophyta', 'seed',
    ('An embryo travels with provisions', 'Un embryon voyage avec des réserves'),
    ('A seed packages an embryo with protection and stored resources. Pollen carries the male gametophyte and contributes to reproduction without requiring a film of external water for swimming sperm in most seed plants. These innovations opened new possibilities for dispersal and survival.\n\nPines and oaks both make seeds. The pine represents a gymnosperm branch; the oak route continues into flowering plants. Seeds appeared before flowers, so the two must not be treated as the same innovation.',
     'Une graine réunit un embryon, une protection et des réserves. Le pollen transporte le gamétophyte mâle et permet, chez la plupart des plantes à graines, une reproduction sans film d’eau externe pour des spermatozoïdes nageurs. Ces innovations ouvrent d’autres possibilités de dispersion et de survie.\n\nPins et chênes fabriquent tous deux des graines. Le pin représente une branche de gymnospermes ; le chemin du chêne continue vers les plantes à fleurs. Les graines ont précédé les fleurs : ce sont deux innovations distinctes.'),
    ('A seed cutaway: coat, reserves and a curled embryo beginning to extend.',
     'Une graine en coupe : enveloppe, réserves et embryon courbé qui commence à s’allonger.'), 'seeds')

chapter('flower', 'Magnoliopsida', 'flower',
    ('Flowers do not have to be showy', 'Une fleur n’a pas besoin d’être voyante'),
    ('In flowering plants, ovules are enclosed in carpels; after fertilization, seeds develop and the ovary contributes to the fruit. Flowers organize reproduction, but large coloured petals and insect visitors are only some of the possibilities.\n\nMaize and oak share this flowering-plant ancestry, then follow different branches. Both use wind pollination. The oak’s small flowers and its acorn remind us that a flowering plant need not resemble a garden blossom.',
     'Chez les plantes à fleurs, les ovules sont enfermés dans des carpelles ; après fécondation, les graines se développent et l’ovaire contribue au fruit. Les fleurs organisent la reproduction, mais grands pétales colorés et insectes visiteurs ne sont que certaines des possibilités.\n\nMaïs et chêne partagent cette ascendance, puis suivent des branches différentes. Tous deux utilisent le vent pour la pollinisation. Les petites fleurs du chêne et son gland rappellent qu’une plante à fleurs ne ressemble pas forcément à une fleur de jardin.'),
    ('A schematic flower exposes its central reproductive structures; this is not an oak flower.',
     'Une fleur schématique expose ses structures reproductrices centrales ; ce n’est pas une fleur de chêne.'), 'flowers oak')

chapter('oak', 'Quercus robur', 'oak',
    ('A tree inside the tree of life', 'Un arbre dans l’arbre du vivant'),
    ('The English oak develops lobed leaves, inconspicuous flowers and acorns held in cups. Its woody trunk contains tissues produced through repeated growth. Each acorn can carry the story into a new generation if conditions allow it to germinate.\n\nThe birch is a relative within the same order, not an oak ancestor. From cells to plastids, embryos, vessels and seeds, the oak combines inherited features. Other branches combine and modify their own inheritance: none is a rung below this tree.',
     'Le chêne pédonculé développe des feuilles lobées, des fleurs discrètes et des glands portés dans des cupules. Son tronc ligneux contient des tissus produits au fil de la croissance. Chaque gland peut prolonger l’histoire dans une nouvelle génération si les conditions permettent sa germination.\n\nLe bouleau est un parent du même ordre, pas un ancêtre du chêne. Des cellules aux plastes, aux embryons, aux vaisseaux et aux graines, le chêne combine des héritages. Les autres branches combinent et transforment les leurs : aucune n’est un échelon inférieur à cet arbre.'),
    ('A stylized oak grows branches and a leafy crown; an acorn appears beside it.',
     'Un chêne stylisé déploie ses branches et son feuillage ; un gland apparaît à ses côtés.'), 'oak')


def lineage(id):
    result = []
    while id is not None:
        result.append(id); id = nodes[id]['parent']
    return result


def story(key, endpoint, title, subtitle, milestones):
    target = names[endpoint]
    path = lineage(target)
    steps = []
    previous = None
    for chapter_id, cousin in milestones:
        step = dict(chapters[chapter_id])
        assert step['node'] in path, (key, chapter_id, 'not ancestral')
        if previous: assert previous in lineage(step['node'])[1:]
        previous = step['node']
        step['cousin'] = names[cousin]
        assert nodes[step['cousin']]['species'] and step['cousin'] != target
        other_path = set(lineage(step['cousin']))
        step['fork'] = next(n for n in path if n in other_path)
        steps.append(step)
    assert steps[-1]['node'] == target
    return dict(id=key, endpoint=target, title=text(key+'_name', *title),
                subtitle=text(key+'_intro', *subtitle), steps=steps)


common = [('cell','Escherichia coli'), ('eukaryote','Quercus robur'),
          ('opistho','Saccharomyces cerevisiae'), ('animal','Hydra vulgaris'),
          ('chordate','Danio rerio'), ('tetrapod','Rana temporaria')]
stories = [
    story('human', 'Homo sapiens', ('The branch that leads to us', 'La branche qui mène à nous'),
          ('Cells, bodies, hands: follow the nested inheritance of our species.',
           'Cellules, corps, mains : suivez les héritages emboîtés de notre espèce.'),
          common + [('amniote','Columba livia'), ('mammal','Ornithorhynchus anatinus'),
                    ('primate','Galago senegalensis'), ('human','Pan troglodytes')]),
    story('pigeon', 'Columba livia', ('A dinosaur on the roof', 'Un dinosaure sur le toit'),
          ('Follow the pigeon’s lineage: aquatic ancestors, limbs, feathers and flight.',
           'Suivez la lignée du pigeon : ancêtres aquatiques, membres, plumes et vol.'),
          common + [('amniote','Homo sapiens'), ('sauropsid','Crocodylus niloticus'),
                    ('bird','Struthio camelus'), ('pigeon','Anas platyrhynchos')]),
    story('oak', 'Quercus robur', ('The long history of an acorn', 'La longue histoire d’un gland'),
          ('A cellular partnership, life on land and the inheritance within an oak.',
           'Un partenariat cellulaire, la vie à terre et les héritages contenus dans un chêne.'),
          [('cell','Escherichia coli'), ('eukaryote','Saccharomyces cerevisiae'),
           ('green','Chlamydomonas reinhardtii'), ('land','Marchantia polymorpha'),
           ('vascular','Pteridium aquilinum'), ('seed','Pinus sylvestris'),
           ('flower','Zea mays'), ('oak','Betula pendula')])]

sources = []
for id, page in [('chordates','29-1-chordates'), ('amphibians','29-3-amphibians'),
                 ('reptiles','29-4-reptiles'), ('mammals','29-6-mammals'),
                 ('primates','29-7-the-evolution-of-primates'), ('birds','29-5-birds'),
                 ('vascular','25-4-seedless-vascular-plants'), ('seeds','26-1-evolution-of-seed-plants'),
                 ('flowers','26-3-angiosperms')]:
    sources.append(dict(id=id, citation='OpenStax, Biology 2e — '+page,
        url='https://openstax.org/books/biology-2e/pages/'+page,
        license='CC BY-NC-SA 4.0 (reference only; no figure reproduced)',
        license_url='https://creativecommons.org/licenses/by-nc-sa/4.0/'))
for id, citation, url in [
    ('human_origins', 'Smithsonian Human Origins — Homo sapiens', 'https://humanorigins.si.edu/evidence/human-fossils/species/homo-sapiens'),
    ('cornell_pigeon', 'Cornell Lab of Ornithology — Rock Pigeon', 'https://www.allaboutbirds.org/guide/Rock_Pigeon/overview'),
    ('oak', 'Woodland Trust — English oak (Quercus robur)', 'https://www.woodlandtrust.org.uk/trees-woods-and-wildlife/british-trees/a-z-of-british-trees/english-oak/')]:
    sources.append(dict(id=id, citation=citation, url=url, license='Reference only; no text or image reproduced', license_url=url))

ui = {
 'library': ('Lineage stories', 'Histoires de lignées'),
 'intro': ('Follow a lineage through the tree, one illustrated chapter at a time. At each fork, meet a relative whose branch continues alongside it.', 'Suivez une lignée dans l’arbre, un chapitre illustré à la fois. À chaque bifurcation, rencontrez un parent dont la branche continue à côté.'),
 'chapters': ('%1$d illustrated chapters', '%1$d chapitres illustrés'),
 'start': ('Begin the story', 'Commencer le récit'),
 'resume': ('Continue · chapter %1$d', 'Reprendre · chapitre %1$d'),
 'chapter': ('Chapter %1$d of %2$d', 'Chapitre %1$d sur %2$d'),
 'choose': ('Choose a chapter', 'Choisir un chapitre'),
 'play': ('Auto-play', 'Lecture auto'),
 'pause': ('Pause', 'Pause'),
 'replay': ('Replay animation', 'Rejouer l’animation'),
 'restart': ('Start again', 'Recommencer'),
 'finish': ('Other stories', 'Autres récits'),
 'branch': ('The fork in the tree', 'La bifurcation dans l’arbre'),
 'toward': ('Lineage followed · %1$s', 'Lignée suivie · %1$s'),
 'cousin': ('Another living branch · %1$s', 'Autre branche actuelle · %1$s'),
 'ancestor': ('Common ancestor in the source tree: %1$s', 'Ancêtre commun dans l’arbre source : %1$s'),
 'explore': ('Explore this fork in the tree', 'Explorer cette bifurcation dans l’arbre'),
 'limits': ('Milestones selected along a real lineage. Intermediate branches are omitted. The drawings illustrate structures, not identified ancestors; their spacing is not a time scale. Living relatives are never stages on the way to another living species.', 'Jalons choisis le long d’une vraie lignée. Des branches intermédiaires sont omises. Les dessins illustrent des structures, pas des ancêtres identifiés ; leur espacement n’est pas une échelle de temps. Les parents actuels ne sont jamais des étapes vers une autre espèce actuelle.'),
 'fork_hint': ('Both branches continue to the present. The highlighted junction is their exact meeting node in this atlas, not a dated fossil.', 'Les deux branches continuent jusqu’au présent. Le point souligné est leur nœud de rencontre exact dans cet atlas, pas un fossile daté.'),
 'credits': ('Illustrated stories — references by chapter', 'Récits illustrés — références par chapitre'),
 'art_credit': ('Original schematic illustrations drawn in the app. Texts are original explanations based on the references below. No external photograph, figure or audio is included.', 'Illustrations schématiques originales dessinées dans l’application. Les textes sont des explications originales fondées sur les références ci-dessous. Aucune photographie, figure ou piste audio externe n’est incluse.'),
 'loading_search': ('Preparing search…', 'Préparation de la recherche…'),
 'loading_map': ('Preparing the tree…', 'Préparation de l’arbre…'),
 'retry': ('Try again', 'Réessayer'),
}
for key, pair in ui.items(): text(key, *pair)

if __name__ == '__main__':
    content = json.loads((DATA/'content.json').read_text(encoding='utf8'))
    source_ids = {s['id'] for s in content['sources'] + sources}
    assert all(set(c['sources']) <= source_ids for c in chapters.values())
    (DATA/'stories.json').write_text(json.dumps(dict(schema=1, stories=stories, sources=sources), ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    for language, folder in enumerate(['values','values-fr']):
        def android(s): return escape(s).replace("'", "\\'").replace('"','\\"').replace('\n','\\n')
        xml = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        xml += ''.join(f'    <string name="{k}">{android(v[language])}</string>\n' for k,v in strings.items())
        (ROOT/f'app/src/main/res/{folder}/strings_parentes_stories.xml').write_text(xml+'</resources>\n', encoding='utf8')
    print(f'{len(stories)} stories, {sum(len(s["steps"]) for s in stories)} chapters; all milestones and forks verified')
