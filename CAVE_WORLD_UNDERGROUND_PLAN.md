# Cave World — biomes souterrains

Statut : première implémentation des six biomes intégrée. Validation visuelle et
mesure des performances sur appareil encore à effectuer par le propriétaire.

## Réalisation intégrée

- Sélection 3D par climat, humidité, profondeur et bruit ; palettes exposées,
  minerais préservés, passages souterrains conservés.
- Concrétions, racines avec ramifications, champignons géants, cristaux
  décoratifs, formations de glace et de basalte. Portée bornée, jusqu'à huit
  blocs de hauteur ; les silhouettes restent volontairement voxel.
- Petits bassins d'eau et de lave à fond et bordure fermés, placés seulement
  lorsque tout leur support existe.
- Quinze blocs (2600–2614) avec textures originales générées dans l'atlas et
  noms anglais/français. Cristaux décoratifs distincts des minerais précieux.
- Lumières : ambre dans le calcaire, mousse verte aux racines, champignons et
  pierres violettes dans les forêts fongiques, cyan dans les géodes, bleu dans
  la glace, orange dans le basalte. La pierre verte sert aussi de pointe rare
  aux racines. Intensités 6–9 sur 15, sources espacées et limite globale de
  32 sources dynamiques inchangée.
- Éclairage coloré sur roche et eau, lumière naturelle stable, scintillement
  conservé pour les torches et la lave.
- Contrôles du générateur adaptés pour distinguer volume brut et décor,
  vérifier la répétabilité avec réutilisation des buffers et préserver les
  minerais. Ces contrôles ne sont pas exécutés dans cette session conformément
  à la politique du dépôt ; la vérification autorisée est compileDebugKotlin.

Les grandes salles utilisent les formations ci-dessus. Les draperies complexes,
arches et mises en scène de cathédrales complètes restent des enrichissements
possibles ; elles ne sont pas des structures dédiées dans cette première version.

## Correction de performance après essai sur tablette

Observation ADB du 14 septembre : collections mémoire fréquentes pendant
l'exploration, puis OutOfMemoryError à 23:42:59 avec un heap de 256 Mo saturé.
La pile termine dans le cache des hauteurs utilisé par la lumière du ciel ; elle
ne suffit pas, seule, à attribuer toute la mémoire retenue à ce cache.

Corrections :

- Cache des hauteurs borné à 4096 colonnes par worker, en tableaux primitifs.
  Mélange des clés X/Z pour éviter les collisions systématiques de Long.hashCode.
- Réutilisation des hauteurs, du champ de grotte et de l'air déjà générés avant
  la décoration ; climat mis en cache par colonne.
- Au plus huit générations et deux constructions de mesh en cours/en attente.
  La production de meshes ralentit à 8 Mo de vertices en attente d'upload ; les
  deux travaux déjà démarrés peuvent dépasser temporairement ce seuil.
- Frein sur les nouvelles générations quand les meshes sont en retard.
- Buffers de maillage réutilisés et suppression d'une copie complète intermédiaire.
- Index des lumières par chunk/version : pas de rescannage pour une simple
  mise à jour de skylight, pas de parcours de toutes les lumières à chaque upload.
- Filtrage des lumières par volume de chunk et par rayon avant calcul du shader.
  Scintillement calculé une fois par source côté CPU, plutôt que par pixel.
- Logs CavePerf toutes les cinq secondes en debug : FPS, heap, travaux en cours,
  meshes en attente, vertices en attente d'upload et nombre de lumières.

Ne pas annoncer de gain mesuré avant un nouvel essai sur appareil avec cette
compilation. Comparer exploration continue puis arrêt trente secondes, en
surveillant CavePerf et les logs GC. Aucun APK ni installation par Codex.

### Deuxième essai : chunks invisibles et chargement directionnel

Capture coordonnée du 14/15 septembre : environ 29 FPS, 60–75 meshes souvent
en attente, génération fréquemment suspendue par le seuil de retard. Les traces
ne comptaient pas encore les meshes rejetés pour changement de lumière.

Corrections identifiées dans le code :

- Le bonus directionnel pouvait placer un chunk à huit chunks devant le joueur
  avant son voisin immédiat derrière lui. La distance 3D est désormais toujours
  prioritaire ; le regard départage seulement les distances égales.
- La géométrie générée est proposée immédiatement au maillage. Un changement
  de lumière voisin déclenche une retouche après affichage, au lieu de jeter
  le premier mesh et de laisser un trou. Un chunk remplacé ou modifié conserve
  ses contrôles d'identité/version.
- Priorité aux premiers affichages. Les retouches lumineuses ne bloquent plus
  la génération via le seuil de meshes invisibles en attente.
- Préparation initiale limitée aux couches du corps et du sol, avec conservation
  de la couronne horizontale de sécurité ; autres couches chargées en fond.
- Suppression des recherches répétées de définition des blocs pleins lors de
  la construction des faces. Propriétés de blocs partiels dans une table dédiée.
- CavePerf ajoute startupBegin/startupReady, waitingFirst, firstUploads et
  provisional pour permettre la comparaison sur la prochaine compilation.

Contrôles de priorité spatiale et de changement de propriétaire ajoutés au
contrôle Java existant, à exécuter selon la politique du dépôt. Nouvelle mesure
sur tablette requise ; la capture doit commencer avant le lancement du niveau.

## Diagnostic

NaturalTerrain génère les volumes et les minerais, puis appelle CozyLandscape,
qui décore uniquement la proximité de la surface. Les anciens JSON cave et
gigacave sont chargés mais leur génération dans World est contournée pour les
terrains de version 3 et plus. Il faut intégrer les biomes au nouveau relief,
sans réintroduire les anciennes couches souterraines périodiques.

## Direction

Chaque biome doit se reconnaître par ses matériaux, ses silhouettes et ses
points lumineux. Les petits tunnels annoncent le biome ; les grandes salles
en développent les décors. Garder des portions de roche nue et des espaces
ouverts pour que les découvertes conservent leur valeur.

| Biome | Implantation | Décor courant | Grande salle remarquable |
| --- | --- | --- | --- |
| Grottes calcaires | Fréquentes, toutes profondeurs | Calcaire crème, gravier, concrétions au sol et au plafond | Colonnes irrégulières et draperies minérales en gradins |
| Jardins des racines | Humides, proches de la surface | Mousse, terre humide, fougères près des ouvertures, racines suspendues | Enchevêtrement de racines et îlots de végétation autour d'une cuvette |
| Forêts fongiques | Humides, profondeur intermédiaire | Mycélium, petits champignons en groupes, quelques pousses lumineuses | Champignons géants de hauteurs variées, clairières entre les pieds |
| Géodes cristallines | Rares, intermédiaires et profondes | Quartz, petites grappes de cristaux dans les renfoncements | Géode ouverte avec cristaux sur plusieurs faces et centre dégagé |
| Cathédrales de glace | Sous les régions froides | Glace bleue, givre, glace fissurée et concrétions | Piliers translucides, grands pendants et passage central |
| Profondeurs basaltiques | Profondes, plus fréquentes sous les régions volcaniques | Basalte, éboulis sombres, fissures incandescentes dispersées | Colonnes massives et cuvette de lave fermée, contournable |

## Blocs et textures

Réutiliser calcaire, quartz, basalte, mousse, glace bleue, glace fissurée,
champignons et fougères existants. Vérifier leurs textures en jeu avant de les
remplacer. Les noms d'anciens biomes ne prouvent pas l'existence de leurs blocs.

Ajouter les familles manquantes :

- Concrétion calcaire : texture stratifiée ; formations voxel effilées assemblées.
- Racines : bloc orientable pour les grosses racines et décor pendant distinct.
- Mycélium : dessus organique, côtés terreux avec filaments.
- Pied et chapeau de champignon géant : textures dessus/dessous/côtés distinctes.
- Champignon luminescent : petite pousse à émission lumineuse modérée.
- Cristal décoratif : amas distinct du minerai récoltable ; variantes colorées limitées.
- Givre : roche givrée pour les transitions entre pierre et glace.
- Basalte fissuré lumineux : texture sombre avec quelques fissures chaudes.

Respecter la résolution et le style de l'atlas existant, vérifier les raccords,
la transparence des sprites, les faces dessous et la lisibilité dans l'obscurité.
Les concrétions et gros cristaux commencent en assemblages de blocs ; une vraie
géométrie pointue nécessiterait un travail supplémentaire sur le maillage.
Définir identifiant libre, collision, support, dureté, récupération, inventaire et
noms anglais/français pour chaque nouveau bloc. Les décors lumineux ne doivent
pas devenir des gisements illimités de minerais précieux.

## Génération

1. Créer un sélecteur de biomes en coordonnées mondiales 3D : profondeur sous
   le sol, humidité et température locales, champs de bruit lents. Viser des
   régions de 100 à 250 blocs, à ajuster visuellement, et des transitions de
   15 à 30 blocs. Pas de découpage par chunk ni d'étages horizontaux fixes.
2. Appliquer les palettes aux surfaces exposées et à une faible épaisseur de
   roche. Conserver les filons existants dans la masse rocheuse.
3. Ajouter une passe dédiée UndergroundDecor après le creusement : détecter
   sols, plafonds et parois via le même terrain analytique que le générateur.
   Ne pas dépendre des chunks déjà chargés pour choisir un décor.
4. Distribuer les petites formations par groupes, avec variations de taille
   et de densité. Ne pas couvrir uniformément toutes les surfaces.
5. Ancrer les grandes formations sur une grille mondiale espacée, décalée par
   la graine. Vérifier le volume disponible dans le terrain non décoré et
   reconstruire uniquement leur intersection avec chaque chunk. Donner une
   portée maximale à chaque formation pour borner le coût de génération.
6. Préserver les passages : décor discret dans les tunnels étroits, grandes
   structures uniquement dans les salles suffisamment hautes, espacement entre
   obstacles. Les racines et pendants doivent avoir un support réel.
7. Introduire les liquides après les décors secs. Placer seulement des cuvettes
   bornées dont le fond et les bords sont fermés ; ne pas remplir selon une
   altitude globale. Si la fermeture ne peut pas être prouvée, omettre le bassin.

## Ordre de réalisation

1. Infrastructure commune + calcaire et géodes : valider matériaux, jonctions,
   structures traversant les chunks et densité visuelle.
2. Racines et champignons : ajouter la végétation, les nouveaux blocs organiques
   et les repères lumineux.
3. Glace et basalte : compléter les six biomes et leurs grandes formations.
4. Bassins fermés, réglage des raretés, ressources et performances.

## Validation

- compileDebugKotlin uniquement pour la compilation, aucun APK ni installation.
- Préparer les contrôles de déterminisme, coordonnées négatives et frontières
  de chunks, avec génération dans des ordres différents ; leur exécution reste
  soumise à la politique de tests du dépôt.
- Contrôler supports, absence d'objets tronqués, absence de blocs inconnus,
  raccords de textures et préservation du fond marin.
- En jeu, observer plusieurs graines et chaque biome : circulation du joueur,
  visibilité à la torche, contraste des lumières, grandes salles et transitions.
- Comparer le coût du streaming et le nombre de faces aux grottes sans décor.
  Réduire les formations et sources lumineuses si les chargements ralentissent.

## Périmètre

Cette étape concerne le terrain et ses décors. Les ennemis spécifiques,
quêtes, ruines à butin et nouvelles recettes pourront venir ensuite. Pas de
suppression automatique des sauvegardes ni de migration de compatibilité.
