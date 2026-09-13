# Cave World : mode Assaut

Un FPS tactique **solo** façon Counter-Strike, joué contre des bots, **à l'intérieur de Cave World**.
Au lieu de la génération procédurale, le monde vient d'une **carte voxel préparée**.

On réutilise tout ce qui existe déjà : rendu OpenGL, caméra FPS, contrôles tactiles et manette,
physique du joueur (`PhysicsNode`), armes à feu (`RangedProfile` : chargeur, rechargement,
dispersion, tir auto, plombs), projectiles balistiques, modèles humanoïdes en boîtes (`MobModel`).
Il reste à ajouter : les cartes et l'IA ennemie.

Style visé : « Counter-Strike en blocs » (esprit *Block Strike* / *Pixel Gun 3D*), pas de réalisme.

---

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
  - **Idée notée : du brouillard** pour fondre les bords de la carte dans le lointain
    (demande de toucher aux shaders du monde, à traiter à part).
