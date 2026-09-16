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

**État : première passe intégrée le 16/09/2026** (voir le journal en fin de document).
Faits : garnison déployée par escouades de 4 à 6, tableau partagé (la radio), une seule escouade
engagée à la fois avec relève, regroupement avant l'assaut, postes de tir répartis tout autour.

Restent ouverts :
- Contournement par un A* où les cases **visibles depuis la cible** coûtent très cher. Aujourd'hui
  l'encerclement vient des postes assignés, pas du chemin choisi pour les rejoindre.
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

### Combat individuel des soldats — 16/09/2026

- **Anticipation fondée sur une estimation retardée** de la course du joueur (moyenne glissante,
  constante 0,35 s), et non plus sur 85 % de sa vitesse réelle. Une course régulière est donc
  correctement devancée ; un demi-tour prend le soldat à contre-pied pendant ~0,15 s, puis son
  estimation se corrige. L'estimation repart de zéro dès qu'il repère le joueur.
- **Visée dégradée quand il se déplace** (`aimMoveSelfPenaltyDeg`) : ses pas de côté lui coûtaient
  jusqu'ici zéro précision.
- **Tirs en rafales** de 3 à 5 balles puis une pause de 0,5 à 1,1 s, au lieu d'une cadence régulière :
  cela ouvre des fenêtres pour riposter, se soigner ou changer de couverture.
- **Balles qui frôlent** : `AssaultMode.noticeNearMisses` projette la trajectoire des balles du
  joueur (une balle à 120 blocs/s franchit six blocs entre deux images, une comparaison de
  positions ne verrait jamais rien). À moins d'1,6 bloc du torse, le soldat est gêné : sa visée se
  dégrade 1,5 s, et **s'il est déjà blessé**, cela suffit à le décider à plonger à couvert.
  En pleine forme, il tient sa position : la peur seule ne le fait pas fuir.
- **Blessé, il reste plus longtemps à couvert** avant de ressortir (jusqu'à 2,5 × la durée normale).
- Tests : le test de visée sur cible mobile, **rouge et jamais exécuté depuis le 14/09**, passe
  maintenant. Trois cas ajoutés : devancer une course régulière mais rater un demi-tour, rafales
  entrecoupées de pauses, balle qui frôle un soldat blessé. 74 tests Cave World, seul
  `world.TreeShapeTest` (forme des arbres, sans rapport avec l'IA) reste rouge — il l'était déjà.
- À valider en jeu ; la coordination entre soldats reste la phase 5.

### Les soldats entendent enfin les pas — 16/09/2026

Retour de l'utilisateur : les tirs alertaient bien les soldats proches, mais **les pas étaient
totalement ignorés** (on pouvait courir dans le dos d'un soldat sans qu'il réagisse). Seul
`onPlayerFired` faisait du bruit.

- `Soldier.hearNoise(x, eyeY, z, range)` généralise `hearShot` : même mémoire, même demi-tour vers
  la source, mais une portée qui dépend du bruit. `hearShot` n'en est plus qu'un cas particulier.
- `AssaultMode.emitFootsteps` s'abonne à **l'événement `GameEvent.Footstep` déjà publié pour le son**
  des pas. Les soldats entendent donc exactement ce que le joueur entend : même cadence, et rien du
  tout quand il est accroupi ou dans l'eau, puisque le renderer n'y publie aucun pas.
- Portées : marcher ≈ 11 blocs, courir ≈ 18, modulées par la surface (pierre 1,15 ; bois 1,05 ;
  terre et herbe 0,85). Un tir porte à 80 blocs (portée ramenée de 160 à 80 après essai en jeu :
  elle alertait trop large), et 48 dans la tour, valeur jugée bonne telle quelle.
- L'origine du bruit est brouillée d'un bloc : le soldat vient inspecter la zone, il ne pointe pas
  le joueur au bloc près.
- Tests ajoutés : des pas proches alertent et font se retourner, des pas lointains non, et un tir
  s'entend là où des pas ne portent pas. 76 tests Cave World, seul `world.TreeShapeTest` reste rouge.

### Les escouades et le talkie-walkie — 16/09/2026

Demande de l'utilisateur : des escouades de quatre à six hommes, **une seule sur le joueur à la
fois**, les autres qui tiennent leur secteur et prennent le relais quand elle tombe. Le joueur doit
rester sous pression sans être submergé — et surtout pas servi en file indienne.

**`ai/SquadCommand.kt` (Kotlin pur, `SquadCommandTest` : 8 cas).** L'état-major du camp adverse.

- **Ce qu'il sait** : la position approximative du joueur, en permanence, floutée de 3 blocs et
  rafraîchie toutes les 2 secondes. C'est la seule concession assumée à la règle « l'IA ne triche
  pas » : sans elle, soixante hommes dans une tour ne retrouveraient jamais un joueur mobile.
- **Ce qu'il ne fait pas** : voir à la place de ses soldats. L'interface `SquadMember` n'expose
  que trois ordres — rejoindre une case, écouter une annonce, tenir un secteur. Un soldat ne tire
  toujours que sur ce que ses propres yeux trouvent.
- **Cycle d'une escouade** : `HOLD` (réserve, secteur de 14 blocs, regard tourné vers la direction
  annoncée) → `RALLY` (regroupement à 22 blocs du joueur, **hors de sa vue**) → `ASSAULT` (postes
  de tir à 9–15 blocs, répartis à 0°, ±70°, ±130° et 180° de l'axe d'arrivée).
- **Anti-file indienne** : c'est le regroupement qui fait le travail. Personne n'approche seul ;
  l'assaut ne part que lorsque les deux tiers de l'escouade sont en place (ou au bout de 25 s, ou
  tout de suite si elle est repérée — attendre bien rangée sous le feu n'aurait aucun sens).
- **Relève** : lancée quand l'escouade engagée est anéantie (5 s de répit) ou qu'il ne lui reste
  qu'un homme (7 s). Le rescapé n'est pas rappelé, il finit son assaut. À distance comparable, on
  choisit l'escouade qui arrive **par un autre azimut** que la précédente : pas trois vagues dans
  le même couloir.

**`ai/Soldier.kt`** gagne trois consignes, et rien d'autre : `order(case, strict)`, `radioContact`
et `leashTo(secteur)`.

- Le **secteur** borne la patrouille, la recherche, *et le tir* : un homme en réserve qui aperçoit
  le joueur à l'autre bout de la carte ne le mitraille pas et ne quitte pas son poste (marge de
  8 blocs). L'escouade engagée reçoit un rayon infini : elle, elle a le droit d'aller le chercher.
- Un ordre **strict** (regroupement) passe avant ce qu'il a seulement *entendu* — sinon une
  fusillade à l'autre bout vide le point de ralliement et on retombe dans la file indienne. Ce
  qu'il **voit** reste toujours prioritaire : un ordre ne l'empêche jamais de se défendre.
- En poste sans rien avoir vu, il balaie du regard la direction annoncée au lieu de tourner sur
  lui-même : c'est là qu'on voit une garnison prévenue.

**`ai/SquadSpawn.kt` + déploiements.** Les cartes font désormais apparaître les escouades
**groupées**, ce qui leur donne un secteur et un côté d'où arriver :
- tour : 12 escouades de 5 (60 hommes, inchangé), une par pièce, deux par niveau ;
- pavillons : 2 escouades de 4 (8 hommes, inchangé), une par parcelle ;
- fonderies et cartes importées : **3 escouades de 4 au lieu de 3 soldats isolés**, semées au sol
  dans la moitié adverse, séparées de 26 blocs. Manche portée de 3 à 5 minutes en conséquence.

**Deux pièges rencontrés en chemin :**
- une escouade qui replanifie s'arrêtait net à chaque calcul de trajet (`pathTo` figeait le soldat
  en attendant la file A*). Elle poursuit maintenant son trajet en cours et bascule à l'arrivée du
  nouveau ; le seuil de replanification est passé à 10 blocs, bien au-dessus du flou des annonces.
- les réserves ne regardaient jamais dans la bonne direction : sans ordre ni mémoire, elles sont
  en `PATROL`, pas en `SEARCH`, et le balayage n'avait été branché que sur la recherche.

**Tests** : 86 cas Cave World, tous verts (`SquadCommandTest` : 8 nouveaux, `SoldierTest` : 2
nouveaux sur l'ordre radio et le secteur de tir). `compileDebugKotlin` réussi.

**À valider en jeu** — c'est là que se jouent les réglages : taille d'escouade, rayon des postes
(9–15 blocs, peut-être trop serré à cinq fusils), durées de répit entre deux vagues, et le
comportement des escouades de la tour qui doivent trouver l'escalier pour changer d'étage.

### Rôles dans l'escouade, et le bug du regard fixe — 16/09/2026

Retour de l'utilisateur après essai : « ça marche à peu près, mais des fois les soldats me
regardent sans rien faire », et « il faudrait un éclaireur, un backup qui prend à revers, rendre le
groupe vivant ».

**Le regard fixe était un défaut de conception de la veille.** Le secteur tenu par les réserves
bornait *le tir* (`if (!inSector) return` en plein milieu de `actEngage`) : un homme qui apercevait
le joueur hors de son secteur restait en `ENGAGE`, suivait sa cible du regard, et ne faisait rien
d'autre — indéfiniment. Deux corrections :

- la limite passe du **secteur** à une **portée d'engagement** (`reserveEngageRange`, 32 blocs) et
  surtout elle est évaluée **dans la décision**, pas au milieu de l'action : trop loin pour un duel,
  il bascule en `SEARCH` et se porte en avant dans son secteur pour prendre une position de tir.
  Règle générale à retenir : *une condition qui empêche d'agir doit être lue là où l'on choisit
  l'action, jamais après* — sinon elle produit un état sans comportement.
- **canon masqué** (ses yeux passent, pas son arme : rebord, embrasure, angle de mur) : il se
  décalait pas, il attendait. Au bout de 0,45 s sans ligne de tir, il change de place.
- Plafond de cerveaux mis à jour par image porté de 8 à 12 (l'échéance de 2 ms reste la vraie
  limite) : un soldat non mis à jour garde sa pose et son regard, et paraît lui aussi figé.

**Les rôles** (`Squad.Role`), attribués au rang et **redistribués à chaque plan**, donc les pertes
se comblent d'elles-mêmes :

| Rang | Rôle | Poste | Détour |
|---|---|---|---|
| 1 | `POINT` — éclaireur | axe d'arrivée, 0,65 × rayon | — |
| 2 | `ANCHOR` — base de feu | +40°, 1,4 × rayon | — |
| 3-4 | `FLANK` — contournement | ∓112°, 0,95 × rayon | ∓160°, 1,75 × rayon, hors de vue |
| 5 | `SUPPORT` — soutien | −45°, 1,15 × rayon | — |
| 6 | `FLANK` | 170°, 1 × rayon | 178°, 1,6 × rayon |

- **L'éclaireur ne se regroupe pas** : il part devant dès l'activation, à 0,8 × rayon, chercher le
  contact. C'est lui qu'on voit arriver en premier, et souvent tomber en premier. Tant qu'il n'a
  rien trouvé, le reste du groupe se met en place tranquillement ; **dès qu'il voit le joueur, les
  autres n'ont plus que 8 secondes** (`contactRallySeconds`) pour se placer avant l'assaut.
- **Le contournement passe par un point de passage** large et hors de vue avant de se rabattre sur
  son poste. Sans ce détour, quatre lignes droites vers quatre points restent quatre lignes
  droites et le joueur les voit toutes arriver ; c'est le détour qui se lit comme « il m'a pris à
  revers ». (Ce n'est pas encore l'A* pondéré par la visibilité prévu au plan, mais ça en donne la
  lecture pour un coût nul.)
- **Le « Contact ! » est joué en déplacements, pas en réplique** : dès qu'un homme voit le joueur,
  toute l'escouade se recale sur sa position réelle et **cesse de contourner** — ils sont trouvés,
  la discrétion n'a plus d'objet. Garde-fou de 2 s entre deux plans déclenchés par un contact, le
  joueur passant sans arrêt derrière un mur.
- **Sous le feu** (`shaken`), l'assaut part immédiatement : attendre bien rangé pendant qu'on vous
  tire dessus n'a aucun sens.

**Tests** : 91 cas Cave World, tous verts. Quatre ajoutés (éclaireur en avant, détour puis
rabattement, abandon du détour au contact, redistribution des rôles après la perte de la pointe) ;
trois anciens réécrits, dont un qui cachait un vrai défaut — le rôle d'un mort restait inscrit au
tableau, si bien que « la pointe » était tenue par un cadavre.

**À valider en jeu.** Ce qu'il reste de plus évident pour la vie du groupe : les annonces sonores
(l'astuce de F.E.A.R.), qui demandent des sons et des chaînes EN + FR.

### Pourquoi les escouades ne bougeaient plus — 16/09/2026

Retour de l'utilisateur : « ils bougent très peu et restent statiques en groupe de 2/3. Le premier
groupe que je croise bouge à peu près, puis les groupes suivants c'est de pire en pire. » Deux
causes distinctes, et la seconde explique la progression.

**1. Les réserves étaient des statues.** `actPatrol` tire une case au hasard **dans toute la carte**
(`rng.nextInt(grid.nodeCount)`) puis refuse ce qui sort du secteur. Sur la tour, un secteur de 14
blocs représente une centaine de cases sur des dizaines de milliers : vingt tirages échouaient
pratiquement toujours, et le soldat ne partait en ronde qu'une fois par minute. La laisse ajoutée
la veille avait transformé la patrouille en loterie perdue d'avance. Le tirage se fait maintenant
**dans le disque du secteur** (`randomSectorNode`), et le pas minimal de ronde y tombe à 3 blocs.

*Règle : un filtre posé après un tirage aléatoire n'est pas un filtre, c'est un rejet. Tirer
directement dans l'ensemble voulu.*

**2. La file de calcul de chemins s'engorgeait, et l'engorgement empirait.** Elle est partagée,
servie dans l'ordre d'arrivée, un trajet à la fois. Trois sources la noyaient :
- chaque soldat qui entend un tir passe en recherche et demande un trajet ; le joueur bouge, la
  destination glisse de deux blocs, il en redemande un. Un **seuil de 3 blocs**
  (`worthRepathing`) supprime cette agitation.
- deux soldats coincés l'un contre l'autre relançaient chacun un contournement toutes les 0,7 s,
  indéfiniment. Le délai **double à chaque échec** jusqu'à 4 s.
- rien ne distinguait la ronde d'un réserviste du trajet d'une escouade qui doit traverser la
  carte. Les demandes de l'escouade engagée (laisse infinie) **passent devant**.

Budget de la file porté à 2 ms et 2048 extractions par image. Replanification d'assaut ramenée à
6 s / 8 blocs, la file n'étant plus saturée. Plafond de cerveaux par image : 12.

**Diagnostic sur appareil** : le journal `CavePerf` (debug) affiche maintenant `routeStalled=` (le
nombre de soldats en attente d'un trajet) et `squads=[H5 H5 A3 …]` (posture et effectif debout de
chaque escouade). Si `routeStalled` monte avec la manche, c'est encore la file.

Tests : 91 cas Cave World, tous verts. À revalider en jeu.

### Ce que l'escouade coûtait, et ce qu'elle coûte — 16/09/2026

Retour de l'utilisateur : « ça lag et ça chauffe ». Vérifié : oui, et une partie était de la
dépense pure, sans contrepartie à l'écran.

**La faute de fond : tout tournait à la cadence de l'écran.** La tablette affiche 120 images par
seconde ; la file de chemins consommait donc son budget de 1,5 ms **cent vingt fois par seconde**,
et les cerveaux le leur autant — pour un résultat identique, puisqu'un soldat ne réfléchit au mieux
qu'à 20 Hz. `updateSoldiers` travaille maintenant à **pas fixe de 1/60 s** : sur cet écran, c'est
deux fois moins de calcul pour exactement le même jeu. Rien à interpoler, les corps ne bougeaient
déjà qu'aux mises à jour de leur cerveau.

**Trois autres coupes :**
- `SquadCommand.update` décidait à chaque image. Un état-major n'est pas un réflexe : **six
  décisions par seconde** suffisent, soit vingt fois moins de balayages et de comptages.
- `postNear` sondait jusqu'à **315 positions** par poste (5 rayons × 9 angles × 7 niveaux), chacune
  payant une ligne de vue d'une trentaine de pas de voxels. En intérieur, où la vue est presque
  toujours coupée, ce maximum était atteint à chaque fois, six fois par escouade et par plan.
  Ramené à **45 sondages** (3 × 5 × 3) : la qualité des postes ne change pas, le repli sur le
  premier emplacement trouvé faisait déjà le travail.
- Les comptages d'escouade (`living`, contacts, barycentres) parcouraient des `List` avec un
  itérateur alloué à chaque appel, plusieurs fois par image. Passés en tableaux et boucles
  indexées : plus une allocation dans le chemin chaud.

**Et deux élargissements de la veille rendus :** budget de la file revenu à 1,5 ms / 1024
extractions, plafond de cerveaux revenu à 8 par pas. C'était la **priorité** des demandes qui
réglait l'engorgement des escouades lointaines, pas la taille du budget ; élargir ne faisait que
brûler du processeur à chaque image.

**Ordres de grandeur** (plafonds théoriques, pas des mesures) : le travail d'IA au pire passe
d'environ 480 ms par seconde à environ 210, soit **sous le niveau d'avant les escouades** (420).

**Pour mesurer plutôt que croire**, `adb logcat -s CavePerf` : `aiPeakUs` donne la pointe d'IA par
pas — **état-major compris** depuis cette passe, il ne l'était pas et le journal sous-estimait donc
le coût réel. Si `aiPeakUs` reste petit et que ça chauffe quand même, la cause est ailleurs : voir
les notes sur les allocations GL et les appels de dessin de Cave World.

### La tour : borner l'IA, et savoir d'où vient la chute — 16/09/2026

Mesures de l'utilisateur sur la Tour du crépuscule (6 niveaux, 60 gardes) : **15 images/s de
moyenne seul, sans aucune escouade engagée**, et **moins de 10** quand deux ou trois escouades
l'entendent en début de partie. Son hypothèse : la différence de niveaux.

**Sur les étages, il a raison, mais pas pour l'IA de décision.** Ce qui coûte cher en bâtiment, ce
sont les recherches de chemin **qui échouent** : un point tiré dans un secteur tombe souvent
derrière une cloison, et sans plafond de coût serré A* fouille tout l'étage avant d'abandonner.
Deux gardes-fous posés avant que ça se voie :

- **Rondes plafonnées au secteur** (`leashRadius × 1,8` au lieu de 60) : une ronde ratée explore
  quelques centaines de cases au lieu de plus de dix mille.
- **Rondes espacées quand la radio annonce le joueur à plus de 45 blocs** : 6 à 12 s au lieu de 1
  à 2,5 s. Réparer les statues avait ouvert une trentaine de recherches par seconde sur un graphe
  de dizaines de milliers de cases — pour des hommes que personne ne regarde marcher.
- **Recherche d'abri plafonnée à 160 lignes de vue** (elle en faisait jusqu'à deux mille, et dans
  un bâtiment chaque pas sur un escalier ou une dalle déclenche un test de volume).

**Mais 15 images/s *seul* ne peut pas venir de l'IA** : à ce moment-là, soixante réservistes
réfléchissent une fois par seconde, ce qui est négligeable. Le soupçon porte sur le rendu de la
tour elle-même : 72 chunks chargés en entier, 42 blocs de haut, six étages de géométrie intérieure,
**sans LOD** (choix assumé pour les cartes). Voir les notes sur les allocations GL et les appels de
dessin de Cave World.

**Le test qui tranche, sans une ligne de code** : l'écran de **choix d'arme** en début de manche
n'a aucun soldat (`clearSoldiers()` est appelé sur `WEAPON_CHOICE`) mais affiche toute la tour.
Si les images par seconde y sont les mêmes qu'en pleine manche, la chute ne vient pas de l'IA.

**Et le chiffre qui compte pour la chauffe** est maintenant dans `adb logcat -s CavePerf` :
`aiPerSecUs`, les microsecondes d'IA dépensées par seconde de jeu (1 000 000 = un cœur saturé).
En dessous de ~50 000, l'IA n'est pas en cause.

### Le lag de la tour venait du dessin des soldats, pas de l'IA — 16/09/2026

Observation décisive de l'utilisateur : **le lag dépend de la direction du regard**. Tourné vers
l'intérieur de la tour, ça rame ; tourné vers un mur extérieur, plus du tout ; et ça s'améliore à
mesure que les escouades tombent. Écran de choix d'arme (tour affichée, aucun soldat) : 29-30 fps.
Rien de ce qui dépend du regard ne peut être de l'IA.

**Mesure sur la Lenovo TB320FC**, `simpleperf` pendant une partie réelle, piles d'appels :

| | part du processeur de l'appli |
|---|---|
| `EnemyRenderer.buildBody` (inclusif) | **60 à 67 %** |
| `AssaultMode.update` — toute l'IA, escouades comprises | **0,4 %** |

Fenêtre sans lag (regard vers l'extérieur) : 508 échantillons en 6 s ; vers la tour : ~4 000.

**Deux causes, qui se multiplient :**
1. **Aucune élimination par les murs.** `EnemyRenderer` ne rejetait que ce qui sort du cône de
   vue. Tourné vers la tour, les 60 gardes des six étages étaient dans le cône et chaque corps était
   reconstruit sommet par sommet, envoyé au GPU et dessiné derrière les dalles, à chaque image.
2. **`buildBody` tournait interprété.** Une méthode de 170 lignes à boucles imbriquées que le JIT
   ne compilait pas ; l'essentiel de son temps partait dans `Jit::MaybeDoOnStackReplacement` →
   `RuntimeCallbacks::HaveLocalsChanged`, un crochet de débogage qui fait un `malloc`/`free` sous
   verrou **à chaque tour de boucle**. Il est activé par l'agent qu'Android Studio injecte dans les
   versions de débogage lancées depuis l'IDE (`code_cache/startup_agents/…-agent.so`, vu dans le
   processus). Une version de production ne paierait pas ce crochet — mais la cause 1, si.

**Correctifs :**
- `AssaultMode.updateOcclusion` : deux rayons caméra → soldat (tête, torse), une douzaine de soldats
  par pas d'IA, soit chacun revu toutes les ~80 ms. Seuls les **blocs pleins et opaques** cachent :
  ni vitre, ni escalier, ni dalle, ni meuble — dans le doute on dessine. Un soldat reste affiché
  0,2 s après avoir été vu, et toujours à moins de 6 blocs, pour ne pas surgir en retard à un coin.
  Le renderer ignore les `Enemy.occluded`.
- `buildBody` découpé en petites méthodes (`preparePose`, `limbAngle`, `placeCorner`, `emitPart`,
  `emitWeapon`, `emitSlime`) qui lisent la pose depuis des champs : même géométrie, mais du code
  que le JIT compile.
- Allocations par soldat et par image supprimées : clé texte du cache d'arme, tableau de teinte,
  `Triple` de couleur de label, `toString()` du niveau, `subList`, itérateurs.

**Règle** : sur un bug qui dépend de la direction de la caméra, chercher dans le dessin, pas dans la
simulation. Et avant de juger un chiffre de performance, vérifier si un agent de l'IDE est chargé.

À revalider sur tablette : regard vers la tour en début de manche, et vérifier qu'aucun soldat
n'apparaît en retard au coin d'un couloir ni ne disparaît derrière une vitre.

**Mesure après correctif (même tablette, même déroulé, 16/09/2026 au soir)** : regard vers la tour
et combat rapproché, **29-30 fps** au compteur du jeu (le plafond, identique à l'écran de choix
d'arme), contre moins de 10-15 avant. Processeur de l'appli divisé par ~4 (≈ 750 échantillons par
fenêtre de 5 s contre ≈ 2 750). `buildBody` passe de 60-67 % à 1-2 %, et l'occlusion elle-même
coûte moins de 1 %. Reste à confirmer à l'œil : pas de soldat qui surgit en retard à un angle.

À savoir pour mesurer : l'agent d'Android Studio reste chargé même lancé depuis l'icône, et il ne
faut pas le supprimer — le code à jour est livré par un dossier superposé
(`code_cache/.overlay`) que cet agent charge ; l'APK de base, lui, date de la dernière vraie
installation.

**Retour en jeu : oui, des soldats apparaissaient en retard en sortant d'un angle.** Deux causes :
la cadence (chaque soldat revu toutes les ~80 ms) et la visée (le centre du corps reste caché alors
que l'épaule et le fusil dépassent déjà). Idée de l'utilisateur, retenue : **forcer l'affichage des
soldats qui savent où est le joueur**. Ce sont exactement ceux qui débouchent vers lui, alors que le
coût venait des réserves des autres étages. Sont désormais dessinés à chaque pas, sans rayon : ceux
qui connaissent ou voient le joueur, ceux d'une escouade en regroupement ou à l'assaut, et tout
soldat à moins de 12 blocs (au lieu de 6). Pour les autres, deux rayons de flanc (±0,55 bloc) sont
tirés si la tête et le torse sont cachés.
