# Cave World : mode Assaut

Un FPS tactique **solo** façon Counter-Strike, joué contre des bots, **à l'intérieur de Cave World**.
Au lieu de la génération procédurale, le monde vient d'une **carte voxel préparée**.

On réutilise tout ce qui existe déjà : rendu OpenGL, caméra FPS, contrôles tactiles et manette,
physique du joueur (`PhysicsNode`), armes à feu (`RangedProfile` : chargeur, rechargement,
dispersion, tir auto, plombs), projectiles balistiques, modèles humanoïdes en boîtes (`MobModel`).
Il reste à ajouter : les cartes et l'IA ennemie.

Style visé : « Counter-Strike en blocs » (esprit *Block Strike* / *Pixel Gun 3D*), pas de réalisme.

---

## Décors 3D de l’Expo (premier lot)

L’Expo démarre désormais dans la cour de mobilier, derrière le jardin de cultures
(point local 113, 5, 119). Le passage central rejoint le jardin puis les autres galeries.
Le lot comprend un bureau avec écran, clavier, souris et tour de PC, une chaise,
un canapé, un fauteuil, une table basse, une étagère et une voiture familiale.

Un deuxième lot ajoute 24 objets : cuisine (réfrigérateur, four, évier, plan de travail,
grille-pain, bouilloire, table et deux chaises), salon (télévision, lampadaire, livres,
plante), atelier (établi, servante, boîte à outils, pneus, caisse, deux cônes) et extérieur
(banc, jardinière, boîte aux lettres, haie). Les petits accessoires reposent sur leurs
meubles, et les allées existantes restent dégagées. Ces modèles viennent directement
du catalogue Toybox, sans copie de leur géométrie.

La chambre ouverte près de l’allée centrale (x 100–110, z 118–131) ajoute un lit double,
deux chevets, une lampe de chevet, des livres, une armoire et une commode sur un parquet.
Le lit et les chevets sont définis dans `DecorBedroom.kt`, avec les primitives communes.
`DecorGarden.kt` remplace les anciens modèles partagés de jardinière et de haie : bac à
lattes, terre en retrait, tiges courbes, pétales et feuilles pliées, silhouette de buisson
irrégulière. Les feuilles, tiges et pétales fins sont visuels ; les volumes principaux restent solides.

- `world/CaveDecor.kt` décrit les objets à taille libre et réutilise les modèles Kotlin
  de Toybox Racers. Le bureau simple est construit avec les mêmes primitives.
- `render/CaveDecorRenderer.kt` regroupe le lot en un mesh statique et applique
  l’origine flottante, l’éclairage ambiant, le brouillard souterrain et le mode gris de Cave World.
  Les lampes locales et les ombres portées des objets ne sont pas encore prises en charge.
- Les collisions sont des boîtes par pièce solide, indexées par cellule ; elles restent
  approximatives pour les parties arrondies. Les tirs testent aussi leur segment de déplacement
  pour ne pas traverser un plateau fin. La navigation des soldats réserve les cellules occupées,
  tandis que leur visibilité teste les boîtes des pièces.
- Le format `.a2map` v2 ajoute les identifiants, positions, échelles et rotations des objets
  après les suites de blocs. La lecture des cartes v1 reste prise en charge.
- Les objets sont fixes : pas encore de placement depuis l’inventaire, de destruction ou de recette.
  Ils ne sont pas générés dans le monde infini. L’Expo reste une visite paisible.

## Pourquoi le voxel simplifie l'IA

Dans un FPS classique, il faut une bibliothèque comme Recast pour savoir où les bots peuvent marcher.
Ici, **la carte est déjà une grille** :

- **Navigation** : une case est praticable si le bloc dessous est plein et les deux dessus sont vides.
  Un A* en Kotlin suffit, calculé une fois au chargement (la carte ne change pas).
- **Ligne de vue** : un rayon qui avance case par case (`raycastBlock` existe déjà).
- **Couverture** : une case praticable collée à un mur de 2 blocs de haut.

---

## Le plan en 7 phases

Chaque phase se termine par quelque chose de testable sur la tablette.

### Phase 0 : faire de la place (aucun changement visible)

- `WorldSource` : interface « d'où viennent les blocs d'un chunk neuf ». Sans source, `World` garde
  sa génération procédurale actuelle ; plus tard, `MapSource`.
- `GameMode` : interface « quelles sont les règles ». `SurvivalMode` reçoit ce qui est propre à la
  survie : XP et compétences, butin, pierres de garde, restauration de la progression, monstres.
  `CaveRenderer` se contente d'appeler le mode.
- **Test :** la survie doit se jouer exactement comme avant.

Pourquoi d'abord : `CaveRenderer.kt` fait plus de 3 200 lignes et `onDrawFrame` gère tout à chaque
image. Des `if (modeAssaut)` éparpillés le rendraient ingérable.

### Phase 1 : marcher dans une carte

- Format `.a2map` binaire compressé (suites de blocs identiques regroupées) + points d'apparition
  des deux camps. `StructureCapture` écrit aujourd'hui une ligne JSON par bloc : trop lourd.
- Création de la carte **en créatif, dans le jeu**, puis export. Limite : on ne capture que ce qui
  est chargé (~128 blocs autour du joueur), largement assez pour une arène.
- `MapSource` : lit la carte depuis `assets/caves/maps/`, vide hors carte, mur invisible au bord.
  Pas de LOD lointain, pas de sauvegarde des modifications.
- Bouton « Assaut » dans le menu de Cave World.
- **Test :** se promener dans sa carte avec ses armes.

### Phase 2 : la boucle de match

- `MatchMode` : manches, chrono, score, réapparition, équipement fixe (ni inventaire, ni XP).
- Les projectiles savent **qui** a tiré, et peuvent toucher le joueur (aujourd'hui, seulement les monstres).
- Zone de tête : la hitbox actuelle est un cylindre, à découper en tête / corps.
- HUD du match (chaînes EN + FR).
- Cibles immobiles pour régler le tir.
- **Test :** une partie complète contre des cibles.

### Phase 3 : les yeux et les jambes de l'IA

Kotlin pur, sans Android, **avec tests automatiques** :

- `NavGrid` : cases praticables + liaisons (plat, monter 1 bloc, descendre jusqu'à 3).
- A* avec tableaux réutilisés (pas de déchets mémoire à chaque image).
- `LineOfSight` : rayon case par case.
- `CoverPoints` : cases à couvert et direction dont elles protègent.
- Vue de debug (option dev) : cases et chemin choisi.
- **Test :** un bot rejoint le point touché en contournant les murs.

### Phase 4 : le soldat

**État : jouable, compléments tactiques en cours de validation.** Perception, mémoire, tirs,
PV et manches sont intégrés. La suite du 14/09 ajoute les décisions par scores, le repli
après blessure, les déplacements latéraux et le rechargement après arrivée à un abri accessible.
Les collisions de volume contre le décor, entre soldats et avec le joueur sont désormais intégrées,
ainsi que les modèles d'armes du joueur et leurs profils de cadence/chargeur/portée. Ces ajouts
restent à valider sur tablette. La coordination entre soldats relève de la phase 5.

`SoldierManager` séparé de `EnemyManager` (les monstres ont saignement, poison, gel : inutile ici).
On extrait la collision partagée (`move()`) et on réutilise le rendu des modèles.

- **Perception** : champ de vision ~110° + ligne de vue ; ouïe (un tir s'entend dans un rayon).
- **Mémoire** : dernière position connue + heure, oubliée au bout d'un moment.
- **Décision par scores** (utility AI) : patrouiller, engager, se couvrir, recharger à l'abri,
  aller vérifier, battre en retraite.
- **Visée humaine** : temps de réaction, erreur qui diminue en gardant la visée et repart si la
  cible bouge vite, recul. Les bots tirent avec **les mêmes armes** que le joueur.
- **Difficulté** = ces paramètres, jamais de triche.
- **Test :** un duel contre un bot qui se cache et recharge.

### Phase 5 : l'escouade

- Tableau partagé par camp : « l'ennemi a été vu là ».
- Rôles : un bot cloue le joueur sur place pendant qu'un autre contourne.
- Contournement : un A* où les cases **visibles depuis la cible** coûtent très cher. Les bots
  trouvent seuls le chemin par derrière, sans script.
- Annonces (« Contact ! », « Je recharge ! ») en bulles ou sons : l'astuce de F.E.A.R., qui fait
  paraître l'IA bien plus maligne qu'elle ne l'est.
- Mode en équipe avec bots alliés.

### Phase 6 : contenu et méta

- 2 ou 3 cartes, plusieurs modes (match à mort, équipes, bombe à désamorcer).
- Récompenses en neutrinos via `NeutrinoRewards.kt` (et `GAMES_NEUTRINOS.md`).
- Visée au gyroscope.

---

## Décisions ouvertes

1. **Premier mode** : seul contre 4 à 6 bots (conseillé pour commencer) ou directement en équipes ?
2. **Murs destructibles** (grenade qui perce un mur) : très « voxel », mais la grille de navigation
   devra se recalculer localement. À garder pour après la phase 5.
3. **Look des soldats** : boîtes façon Minecraft comme les monstres actuels, ou plus détaillés ?

## Journal

- **14/09/2026 — collisions et armes de la phase 4 :**
  - Volume debout de rayon 0,30 et hauteur 1,80, vérifié par petits pas contre le décor et les corps.
    Montée préalable des marches et descente lorsque le rebord est dégagé. Le joueur est lui aussi
    bloqué par les soldats via un callback optionnel de sa physique, actif seulement dans Assaut.
  - A* exclut les cases occupées ; nouvelle recherche après 0,7 s sans déplacement. Si un passage
    reste occupé et aucun détour n'existe, le soldat attend. Pas de poussée entre personnages.
  - Un pistolet, une SMG et un fusil à levier par groupe. Modèles issus de `HeldEquipmentMesh`,
    géométrie conservée en cache par phase de recul/rechargement, même rendu groupé que les corps.
  - Vitesse, portée, cadence, chargeur et durée de rechargement issus de `RangedProfile` du joueur.
    Les dégâts restent équilibrés pour le solo : 8 / 3 / 16. La dispersion humaine reste propre à l'IA.
    Hors portée de son arme, le soldat se rapproche au lieu de gaspiller des tirs.
  - Cinq cas de régression de collision ajoutés, non exécutés (compileDebugKotlin uniquement).
    Vérifier sur tablette les croisements, marches, passages étroits et le placement visuel des armes.

- **14/09/2026 — suite de la phase 4 :**
  - `SoldierDecision` compare les scores de patrouille, recherche, engagement et repli selon
    perception, mémoire, santé et coups récents. Un repli est mené à terme avec attente à couvert
    et délai avant une nouvelle fuite ; la faible santé seule ne provoque pas une fuite permanente.
  - Recherche bornée d'abris : huit candidats proches, chemin A* vérifié avant de s'engager.
    En l'absence d'abri accessible, riposte ou rechargement sur place. Le temps de rechargement
    commence à l'arrivée ; un trajet trop long est interrompu après quatre secondes.
  - En combat, déplacement latéral court toutes les trois à quatre secondes si une ligne de tir
    et un chemin court existent. Nouvelle vérification du rayon depuis l'arme après déplacement.
  - Les soldats morts sont ignorés avant leur mise à jour : plus de dernier tir après un impact mortel.
  - Un nouveau chemin revient au centre de la case de départ pour éviter de couper un angle
    lorsqu'un déplacement est interrompu. Cela ne remplace pas encore des collisions physiques.
  - Cas de régression ajoutés pour décisions, repli, absence d'abri et déplacement latéral.
    Non exécutés : le dépôt autorise seulement `compileDebugKotlin`. À valider sur tablette.

- **13/09/2026** : plan établi. Phase 0, première passe :
  - `world/WorldSource.kt` : l'interface existe, `World` l'accepte (paramètre `source`, null par
    défaut = procédural). Aucune implémentation encore : `MapSource` arrive en phase 1.
  - `mode/GameMode.kt` + `mode/SurvivalMode.kt` : sortis de `CaveRenderer` : XP et compétences
    (endurance, saut, chute), récompenses des monstres, butin, pierres de garde restaurées,
    restauration de la progression, point d'apparition et mise à jour des monstres.
  - Restent dans le renderer, car communs : caméra, inventaire et barres, munitions plantées,
    recul quand le joueur est touché, placement du joueur, rendu des monstres.
  - **Encore à trier** (à traiter quand le mode Assaut en aura besoin, en phase 1) :
    XP de vitesse dans `updateWalk`, pierres de garde posées/minées, minage et pose de blocs,
    ticks d'eau et de chute de blocs, cache LOD, sauvegarde dans `CaveActivity`.
  - Compile. Vérifié en jeu par l'utilisateur : la survie se comporte comme avant. Commité.
- **13/09/2026** : phase 1.
  - `world/A2Map.kt` : format `.a2map` (suites de blocs identiques + GZIP) et capture d'une zone.
    Tests : `A2MapTest` (relecture identique, taille des aplats, placement dans les chunks, ciel).
  - Balises `spawn_marker_a` / `spawn_marker_b` (ids 8005 / 8006) : posées en créatif, elles
    deviennent des points d'apparition à l'export et laissent de l'air à leur place.
  - Export : bouton 🗺 dans l'outil de capture (📐) → `Documents/cave_world/maps/`.
  - `world/MapSource.kt` : pose la carte à y = 64, vide autour, ciel ouvert calculé par colonne.
  - `mode/AssaultMode.kt` : kit d'armes à distance, retour au point d'apparition si on tombe.
  - Bouton « Assaut » dans le menu de Cave World → liste des cartes (assets + Documents).
  - Demandes de l'utilisateur : **aucune construction ni destruction** dans le shooter (boutons
    masqués + `allowsWorldEdits = false`), **pas de LOD** pour une carte (~100 × 100).
  - Retour de l'utilisateur : l'export depuis l'outil de capture **ne marche pas** chez lui
    (cause non recherchée). Remplacé pour l'instant par une **arène d'essai intégrée**
    (`world/BuiltinMaps.kt`) : sol d'herbe 100 × 100, murs de pierre de 5 blocs, camp A dans un
    coin, camp B dans le coin opposé. Toujours en tête de la liste « Assaut ».
  - Arène testée en jeu par l'utilisateur : ça marche. Phase 1 commitée.
  - **Carte chargée en entier** : `WorldSource.chunkBounds()` donne la zone finie ; `World` en
    charge tous les chunks (49 pour l'arène) sans distance de vue et n'en décharge aucun.
- **13/09/2026** : phase 2, la boucle de match.
  - Consignes de l'utilisateur : **toutes les armes restent disponibles**, **munitions illimitées**
    (la réserve n'est pas entamée ; le chargeur et le rechargement restent, à rediscuter si besoin).
  - `mode/AssaultMatch.kt` : manches, chrono (60 s), score (100 par cible, +50 à la tête, +10 par
    seconde restante si la manche est gagnée), pause de 4 s entre les manches. Tests : `AssaultMatchTest`.
  - Mannequins d'entraînement (modèle `dummy`, 100 PV) : 8 par manche, posés au hasard au niveau du
    sol, loin du point d'apparition, tournés vers lui. Ce sont des `Enemy` jamais mis à jour : immobiles.
  - Tir à la tête : au-dessus de 74 % de la hauteur du corps (`MobModels.HEAD_START`), dégâts × 2 en Assaut.
  - Heure figée à midi, bouton jour/nuit masqué. Panneau de manche en haut de l'écran, « Tir à la tête ! ».
  - Reporté à la phase 4 : les balles qui touchent le joueur (rien ne lui tire dessus pour l'instant).
  - Testée en jeu par l'utilisateur : ça marche. Commitée.
- **13/09/2026** : phase 3, les yeux et les jambes de l'IA (nouveau dossier `caves/ai/`, Kotlin pur).
  - `NavGrid` : cases praticables (sol dessous, 2 blocs libres), liaisons vers les 8 voisines (plat,
    marche d'un bloc, descente jusqu'à 3 blocs, diagonales qui ne rasent pas les coins), couverture
    par case (mur de 2 blocs = totale, muret d'1 bloc = à moitié).
  - `PathFinder` : A* sans allocation par recherche. `LineOfSight` : rayon bloc par bloc.
    `PathFollower` : glisse d'une case à l'autre. `SolidGrid` évite d'emballer les entiers.
  - Tests : `NavigationTest` (10). Deux tests étaient faux au premier essai (colonne mal comptée,
    œil placé au niveau du muret) ; le code, lui, était juste.
  - Arène d'essai : murets, caisses et pilier central, recopiés en miroir dans les quatre quarts.
  - Démonstration : un mannequin **coureur bleu** suit le joueur en contournant les obstacles et
    s'arrête quand il le voit à moins de 8 blocs. Bouton 🧭 : son chemin tracé au sol en cyan.
  - Testée en jeu par l'utilisateur : ça marche. Commitée. Remarque : **impossible de semer le
    coureur**, il marche toujours vers le joueur. Normal pour la démo : il connaît en permanence la
    position du joueur (il triche). **À corriger en phase 4** : un soldat ne va que là où il a vu ou
    entendu le joueur pour la dernière fois, et finit par oublier. On doit pouvoir le semer.
- **13/09/2026** : phase 4, le soldat.
  - `ai/Soldier.kt` (Kotlin pur, `SoldierTest` : 6 tests). **Il ne triche plus** : il ne connaît la
    position du joueur que s'il le voit (champ de vision 110°, portée 45, ligne de vue), l'entend
    tirer (35 blocs) ou se fait toucher. Il retient la dernière position connue et l'oublie après
    8 s sans nouvelle information : on peut le semer (retour de l'utilisateur en phase 3).
  - États par priorité : RELOAD (abri le plus proche hors de vue du joueur, 2,2 s) > ENGAGE
    (s'arrête, temps de réaction 0,35 à 0,6 s, visée de 7° qui se resserre jusqu'à 1,2° et se
    dérègle si le joueur court en travers) > SEARCH (va vérifier, puis regarde autour) > PATROL.
  - Manche : 3 soldats qui apparaissent près du coin adverse, 3 minutes. Mannequins et coureur retirés.
  - Joueur : 100 PV, balles de soldat à 8 dégâts (champ `Projectile.fromEnemy`, pas de tir ami).
    Mort = manche perdue (`RoundEnd.DIED`), soigné et replacé à la manche suivante.
  - Sons : alerte quand un soldat repère le joueur, touché, mort, et nouveau son de tir ennemi.
  - Modèle `soldier` (treillis, gilet, casque, fusil). Bouton 🧭 : chemins de tous les soldats.
  - **Crash au premier kill** (signalé par l'utilisateur) : la mort d'un soldat publiait `MobDied`,
    que `LootNode` écoute pour le butin de la survie ; il cherchait `assault_soldier` dans
    `MobRegistry` et levait une exception. Corrigé avec un événement dédié `SoldierDown`.
- **14/09/2026** : réglage après essais des soldats : réaction trop courte portée, visée trop imprécise.
  - Vue normale 65 blocs, vue après alerte et audition des tirs 160 blocs ; un bruit fait regarder
    vers son origine, mais les murs bloquent toujours la vue et la mémoire expire toujours après 8 s.
  - Visée initiale 2°, minimum 0,18°, convergence 3°/s ; la course ajoute un écart borné au lieu
    d'accumuler une pénalité à chaque image. Délai de réaction et dégâts inchangés, visée au torse.
  - Balles à 120 blocs/s, portée 160 ; anticipation partielle du déplacement visible, réglage de
    vitesse partagé entre cerveau et projectiles. Réserves illimitées et rechargement conservés.
  - Cas de régression ajoutés : riposte lointaine, occultation par un mur, précision sur cible mobile.
    Tests non exécutés (politique du dépôt : compileDebugKotlin uniquement). Équilibrage à valider en jeu.
  - **Idée notée : du brouillard** pour fondre les bords de la carte dans le lointain
    (demande de toucher aux shaders du monde, à traiter à part).

### Refonte du terrain intégré — Quartier des fonderies

- Remplace le terrain de test par un quartier original de **140 × 120 × 16 blocs**.
  Chargement intégral : 72 chunks, contre 49 auparavant.
- Camps ouest/est séparés, cours de déploiement protégées, repères bleus/rouges.
  Le mode reste le combat solo contre les soldats existants ; cette refonte ne crée pas de bots alliés.
- Quatre bâtiments principaux : grande salle, petits bureaux, étage, fenêtres ouvertes,
  escalier intérieur avec trémie et second accès extérieur. Quatre annexes traversantes.
- Trois axes principaux et contournements périphériques avec chicanes, caisses cerclées,
  comptoirs bas et couvertures debout. Voie centrale deux blocs plus bas ; pont accessible
  et passage inférieur de trois blocs libres. Les blocs pleins restent compatibles avec NavGrid.
- Géométrie est/ouest identique hors chicanes et parapets centraux ; matériaux différents
  pour se repérer. Aucun nouveau bloc ni texture supplémentaire nécessaire.
- Déploiement des ennemis de la carte intégrée limité au sol dans la cour est :
  les toits ne sont plus des candidats. Les cartes importées conservent leur tirage existant.
- Assertions de dimensions, couverture des chunks et chemins vers les étages mises à jour.
  Validation autorisée : compileDebugKotlin ; essai visuel et jouabilité sur appareil à faire.

### Finitions architecturales du quartier

- Marches remplacées par les escaliers en briques grises existants (2406), orientés
  selon la montée et réfléchis correctement entre les camps. Supports pleins conservés.
  Les métadonnées sont désormais écrites dans la carte pour conserver ces orientations.
- Dalles grises (2506) pour les linteaux de meurtrières, banquettes et auvents minces.
  Embrasures allongées au RDC et à l'étage, postes bas dans les ruelles et fentes dans
  les chicanes périphériques. Le soubassement reste plein et les extrémités offrent un abri.
- Une banquette de demi-bloc relève les pieds : yeux accroupis et canon passent dans la
  fente, tandis que la tête debout est masquée par le linteau. Ce décalage est nécessaire
  car le canon est placé 0,05 bloc sous les yeux.
- Collision des projectiles et visibilité des soldats respectent maintenant les volumes
  réels des dalles/escaliers. Navigation des soldats conservatrice par voxel inchangée.
- Tests ajoutés pour les orientations après export et les hauteurs de tir/visibilité.
  Non exécutés conformément à la politique du dépôt ; compileDebugKotlin réussi.
