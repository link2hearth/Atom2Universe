# Toybox Racers — catalogue de décors procéduraux

Créé le 5 septembre 2026. Tous les modèles sont originaux, construits en Kotlin à
partir de primitives colorées. Aucun modèle, texture, logo ou son externe téléchargé.

**Total actuel : 49 modèles réutilisables**, en plus du mobilier de la chambre.
Filtrage par pièce : `DecorCatalog.forRoom(DecorRoom.BATHROOM)` (également KITCHEN,
LIVING_ROOM, GARAGE, OFFICE et OUTDOOR). Ce filtrage inclut toutes les collections.

## Chambre actuelle

Le décor installé dans la chambre comprend désormais : porte fermée à panneaux,
deux fenêtres avec ciel illustré et rideaux, bureau avec tiroirs et tabouret,
lampe champignon, pot à crayons, cahier ouvert, livres, bibliothèque avec bacs,
deux étagères murales garnies, lit bas avec couverture et oreiller, parquet dessiné.
Les murs ont été rehaussés à 32 unités pour accueillir les fenêtres et le mobilier.

L'ours assis possède un bassin posé au sol, deux pattes avancées avec coussinets,
deux bras, un ventre clair, des oreilles bicolores, des reflets dans les yeux et
un nœud lavande. Son encombrement reste dans le volume du jouet existant.
Le train est déplacé vers `(25, -62)` pour dégager le bureau.

## Catalogue pour les futures pièces

Les 25 modèles de la première collection ci-dessous sont disponibles via `DecorCatalog`. Ils ne sont pas
automatiquement placés dans la chambre actuelle. Ce sont des éléments prêts à
composer pour les futures pistes, pas encore des circuits cuisine/salon/garage.

| Pièce | Identifiant stable | Modèle |
|---|---|---|
| Cuisine | `kitchen.fridge` | Réfrigérateur rétro, deux portes, poignées et magnets |
| Cuisine | `kitchen.oven` | Four avec vitre, commandes et quatre plaques |
| Cuisine | `kitchen.sink` | Meuble évier, portes et robinet |
| Cuisine | `kitchen.counter` | Meuble bas à trois tiroirs |
| Cuisine | `kitchen.island` | Îlot central avec plateau débordant |
| Cuisine | `kitchen.toaster` | Grille-pain arrondi avec deux tartines |
| Cuisine | `kitchen.kettle` | Bouilloire avec anse et bec |
| Cuisine | `kitchen.fruit_bowl` | Corbeille de fruits pastel |
| Salon | `living.sofa` | Canapé trois places, coussins et accoudoirs |
| Salon | `living.armchair` | Fauteuil menthe avec coussin |
| Salon | `living.coffee_table` | Table basse avec tablette inférieure |
| Salle à manger | `living.dining_table` | Table avec chemin de table et quatre assiettes |
| Salle à manger | `living.dining_chair` | Chaise avec dossier et assise rembourrée |
| Salon | `living.tv_cabinet` | Meuble TV avec écran illustré original |
| Salon | `living.plant` | Plante à feuilles arrondies en pot |
| Salon | `living.floor_lamp` | Lampadaire à abat-jour crème |
| Toutes | `living.books` | Pile de quatre livres |
| Garage | `garage.workbench` | Établi, panneau à outils et étau |
| Garage | `garage.tool_chest` | Servante à quatre tiroirs sur roulettes |
| Garage | `garage.storage_rack` | Rayonnage à quatre niveaux et boîtes |
| Garage | `garage.tires` | Trois roues empilées avec moyeux stylisés |
| Garage | `garage.toolbox` | Boîte à outils avec poignée et fermoirs |
| Garage | `garage.cone` | Plot de signalisation rose et crème |
| Garage | `garage.crate` | Caisse renforcée en bois clair |
| Garage | `garage.barrier` | Barrière de chantier miniature |

### Collection 02 — 24 modèles supplémentaires

| Pièce | Identifiant stable | Modèle |
|---|---|---|
| Bureau | `office.pc_tower` | Tour PC avec ventilateurs de façade et aérations |
| Bureau | `office.computer` | Moniteur, clavier détaillé et souris |
| Bureau | `office.exercise_bike` | Vélo d'appartement, selle, guidon, console et pédales |
| Bureau | `office.dresser` | Commode pastel à trois tiroirs |
| Cuisine | `kitchen.microwave` | Micro-ondes avec vitre, poignée et commandes |
| Salle de bains | `bathroom.mirror` | Miroir encadré, surface stylisée sans reflet dynamique |
| Salle de bains | `bathroom.shower` | Douche ouverte avec receveur, parois et pommeau |
| Salle de bains | `bathroom.vanity` | Meuble lavabo avec robinet |
| Salle de bains | `bathroom.towel_rack` | Porte-serviettes sur pieds avec serviette pliée |
| Salle de bains | `bathroom.bathtub` | Baignoire creuse avec robinet et tablette |
| Salle de bains | `bathroom.toilet` | Toilettes avec réservoir, abattant et bouton |
| Salle de bains | `bathroom.toilet_paper` | Support sur pied, rouleau et feuille déroulée |
| Salle de bains | `bathroom.washer` | Lave-linge à hublot |
| Salle de bains | `bathroom.laundry_basket` | Panier rempli et linge débordant |
| Extérieur | `outdoor.house` | Maison fermée, quatre façades, toit à deux pentes, cheminée |
| Extérieur | `outdoor.porch` | Porche indépendant avec marches et auvent |
| Extérieur | `outdoor.garage_door` | Porte de garage fermée, panneaux et hublots |
| Extérieur | `outdoor.fence` | Section de clôture à lattes pointues |
| Extérieur | `outdoor.tree` | Arbre fruitier facetté |
| Extérieur | `outdoor.hedge` | Section de haie |
| Extérieur | `outdoor.bench` | Banc de jardin à lattes |
| Extérieur | `outdoor.mailbox` | Boîte aux lettres sur poteau |
| Extérieur | `outdoor.family_car` | Voiture familiale statique sans marque |
| Extérieur | `outdoor.planter` | Jardinière fleurie |

Les accessoires sont indépendants et peuvent être combinés : PC sur un bureau,
miroir au-dessus du lavabo, clôtures répétées, porche contre la façade. La maison
est un décor extérieur fermé ; elle ne contient pas les pièces jouables intérieures.
Cette collection prépare le futur circuit autour de la maison sans créer ce circuit.
Le vélo et la voiture sont des décors statiques, sans animation ou nouvelle conduite.

### Miroir : modèle et futur rendu

La face du miroir est un aplat clair séparé du cadre. Aucun reflet de la scène n'est
calculé actuellement. OpenGL ES permet de rendre dans une texture via un framebuffer
([spécification Khronos](https://registry.khronos.org/OpenGL/specs/es/2.0/es_cm_spec_2.0.pdf)).
Une prochaine étape pourra rendre la pièce depuis une caméra réfléchie, masquer
le miroir dans cette passe et appliquer la texture sur sa face. Il faudra traiter
le plan de coupe, le sens des faces et le coût GPU sur téléphone. Ce travail de
rendu reste distinct de la création du modèle et n'est pas inclus dans cette collection.

## Placement et collisions

- Origine locale au centre de la base ; Y monte ; la façade regarde vers +Z.
- Dimensions en unités du jeu ; l'échelle 1 suit le mobilier de la chambre.
- `DecorPlacement` accepte position XYZ, `quarterTurns` et échelle uniforme positive.
  Les rotations de 0, 90, 180 et 270 degrés gardent les boîtes physiques alignées
  avec les modèles. Les normales du rendu tournent avec les objets.
- `model.bounds` décrit l'encombrement total pour organiser un futur éditeur.
- `placement.solids` contient une boîte par partie solide, pas une boîte autour de
  tout le meuble : on peut passer sous une table ou entre les pieds d'une chaise.
- Les volumes des parties rondes sont des approximations rectangulaires. Les petits
  détails purement visuels (étiquettes, magnets, reflets, poignées fines) n'ajoutent
  pas tous une collision individuelle. Pas de portes ou de tiroirs animés à ce stade.
- Les toits à deux pentes utilisent une nouvelle primitive triangulaire ; leur
  collision est encore une boîte englobante. Ils sont prévus pour le décor extérieur,
  pas encore comme des pentes praticables. Douche et baignoire ont des parois séparées.
- Tous les objets sont statiques. Les dessus portent une voiture qui arrive depuis
  le haut ; les dessous bloquent une montée ; quitter un plateau provoque une chute.
- Les parties sont regroupées au chargement dans un mesh statique, sans rendu
  séparé pour chaque livre, poignée ou assiette. Les catalogues sont créés à la demande.

Exemple pour composer un petit ensemble, à utiliser dans le futur plan de piste :

```kotlin
val furniture = listOf(
    DecorPlacement(DecorCatalog["kitchen.counter"], x = 0f, y = 0f, z = 0f),
    DecorPlacement(DecorCatalog["kitchen.toaster"], x = -2f, y = 9.6f, z = 0f),
    DecorPlacement(DecorCatalog["kitchen.fridge"], x = 12f, y = 0f, z = 0f, quarterTurns = 1)
)
// Pour ajouter des objets à un terrain : rendu et collisions utilisent la même liste.
val track = PrototypeTrack(decorations = furniture)
// Pour un aperçu / une scène indépendante : mesh sans décor de chambre.
val mesh = DecorMeshFactory.build(furniture)
```

Cet exemple ne choisit pas des emplacements libres sur le huit actuel : toute
composition destinée au jeu doit être placée selon le tracé de sa future pièce.

## Inventaire de provenance

| Ensemble | Source | Méthode | Date |
|---|---|---|---|
| Mobilier et ouvertures de chambre | `track/RoomDecor.kt`, `render/PrototypeMeshes.kt` | Boîtes, cylindres, ellipsoïdes, palette pastel définie par code | 2026-09-05 |
| Ours assis | `render/PrototypeMeshes.kt`, `addTeddy` | Ellipsoïdes facettés et détails géométriques | 2026-09-05 |
| Cuisine, salon, garage | `models/DecorCatalog.kt` | 25 assemblages originaux, réalisés par Codex pour ce projet | 2026-09-05 |
| Bureau, salle de bains, extérieur et micro-ondes | `models/DecorExpansion.kt` | 24 assemblages originaux, réalisés par Codex pour ce projet | 2026-09-05 |
| Primitives, placement et palette | `models/DecorModel.kt`, `render/DecorMeshFactory.kt` | Génération Kotlin sans ressource externe | 2026-09-05 |

## Validation à poursuivre sur téléphone

`compileDebugKotlin` réussit. Les maillages issus des classes Kotlin compilées ont
été exportés pour produire et inspecter une planche d'aperçu hors Android. Le décor
de chambre contient 10 432 triangles ; chaque modèle du catalogue compte entre
84 et 1 404 triangles pour la première collection. La seconde collection a aussi
été exportée et inspectée visuellement, sans lancer l'application. Le mobilier de
chambre conserve au moins 2,33 unités de marge
horizontale par rapport au bord de la piste (avant rayon de collision de la voiture).

Contrôler la silhouette de l'ours, la lisibilité des fenêtres depuis la voiture,
le passage entre les pieds du bureau, les collisions au bord du lit, les
performances de la chambre enrichie et l'absence de gêne pendant une course.
Les 49 modèles du catalogue devront aussi être essayés lors de leur placement sur
de vraies pistes. Aucun APK n'est généré ou installé par Codex.
