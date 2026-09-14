# Eau de Cave World — vérifications sur appareil

## Fonctionnement et coût

- Simulation locale : seules les cellules réveillées sont traitées. Maximum de
  128 cellules par tick, avec arrêt entre deux cellules après environ 2 ms.
  Une opération en cours peut dépasser cette durée. Cadence conservée à 4 Hz.
- Les cascades ne prennent plus un flux descendant pour un sol autorisant
  l'étalement horizontal. Les sources restent renouvelables selon la règle
  existante des deux sources voisines : ce modèle ne conserve pas un volume fini.
- Les attentes aux frontières non chargées sont dédupliquées. Les modifications
  invalident aussi les meshes voisins qui partagent un coin de surface.
- Les reconstructions d'eau en arrière-plan sont vérifiées avec leur version
  d'eau avant envoi au GPU, y compris lors d'une reconstruction du terrain.
- Rides et reflets calculés dans la passe d'eau existante, sans texture ni passe
  de réflexion supplémentaire. Les petites rides s'atténuent à distance.
- Tri de transparence par chunk visible avec réutilisation de la liste.
  Les faces à l'intérieur d'un même chunk ne sont pas triées individuellement :
  certaines superpositions peuvent encore produire des différences de mélange.
- L'immersion utilise les hauteurs et les triangles de la surface affichée.
- La surface libre d'une source se situe à 8/9 de la hauteur du bloc. Les cellules
  avec de l'eau au-dessus restent pleines pour raccorder les colonnes.
- La portée horizontale atteint huit cases après la source sur un sol plat.
  Chaque descente renouvelle cette portée sans transformer le flux en source.
  Il n'y a pas de limite de hauteur de chute spécifique à l'eau ; les chunks
  non chargés restent une frontière de simulation, reprise à leur chargement.
- Poser un bloc dans une source ou un flux remplace l'eau et réveille la simulation.
  Un seau plein peut également remplacer un flux par une source.

## Scénarios à essayer

### Courants et résistance

Le joueur, les ennemis et les animaux passifs lisent les différences de niveau de
l'eau autour de leur point d'immersion. La dérive horizontale tend progressivement
vers 1,25 m/s ; l'eau immobile n'impose aucune direction. Une colonne descendante
ajoute une attraction verticale. Le joueur conserve sa nage et les déplacements
volontaires des mobs immergés sont réduits à 45 %. Les collisions sont conservées ;
la poussée seule ne déclenche pas d'escalade automatique du joueur ou des ennemis.
Les ennemis gelés conservent leur immobilisation spéciale.

Vérifier : rester sans bouger dans un courant puis dans un lac ; nager avec et
contre le courant ; se laisser entraîner contre un mur et vers une cascade ;
faire entrer un ennemi et un animal au repos dans le flux ; ressortir de l'eau.
Comparer également à 30 et 60 FPS. Le calcul ne lance aucune recherche de chemin
et réutilise les tampons de lecture. `WaterCurrentPhysicsTest.kt` couvre la dérive
au repos, l'arrêt contre un mur et la sensibilité au pas de temps ; tests ajoutés
mais non exécutés dans le cadre de la vérification autorisée par le dépôt.

### Routage et rendu

Le routage horizontal cherche le premier trou accessible par une recherche en
largeur limitée à quatre pas et à la portée restante du flux. La chute immédiate
est prioritaire ; sinon les chemins les plus courts gagnent, avec partage à égalité.
Sans trou proche, la nappe peut s'étendre dans les directions libres. Il ne s'agit
pas d'une recherche du point le plus bas de toute la grotte. Les tableaux de travail
sont réutilisés et les résultats sont mémorisés pendant un tick. Les chunks absents
ne sont pas considérés comme des trous et ne sont pas chargés par cette recherche.

Pour vérifier ce routage, placer deux trous à des distances différentes : seul le
plus proche doit attirer le flux. Les placer ensuite à égale distance, puis barrer
le chemin direct avec un mur et vérifier le détour. Boucher le trou choisi pour
vérifier le recalcul des branches. Les tests unitaires correspondants sont dans
`WaterFlowRoutingTest.kt` ; ils ne sont pas exécutés par la compilation Kotlin seule.

1. Poser une source sur une plateforme isolée, puis ouvrir un trou : vérifier
   que la chute descend et ne forme pas de nappes suspendues à chaque étage.
2. Retirer cette source avec un seau : attendre le retrait progressif du flux.
   Refaire avec un obstacle ajouté, puis retiré, sous la cascade.
3. Faire couler l'eau à une frontière et à un coin de chunks ; s'éloigner puis
   revenir. Vérifier les raccords et la reprise du flux au chargement.
4. Sauvegarder avec une cascade active et recharger ; vérifier les niveaux.
5. Avancer dans une nappe mince puis immerger la caméra, debout et accroupi :
   le passage sous l'eau doit suivre la surface visible.
6. Longer un lac et tourner la caméra : les rides restent ancrées au monde.
   Regarder une cascade : le motif descend. Vérifier aussi de nuit et sous terre.
7. Observer l'eau près du verre, des plantes et des blocs partiels : elle doit
   conserver ses faces visibles au contact de ces blocs.
8. Comparer les temps de frame avant/après au même endroit et à la même distance
   d'affichage : lac immobile, plusieurs cascades actives, puis eau stabilisée.
9. Sur un sol plat sans obstacle, poser une source : le flux doit atteindre huit
   cases dans les directions cardinales. Prévoir du temps pour la stabilisation.
   Ouvrir un trou à la huitième case : la chute doit descendre et pouvoir s'étaler
   de nouveau sur le palier inférieur. Refaire avec plusieurs paliers successifs,
   puis retirer la source et vérifier que tous les flux finissent par disparaître.
10. Poser des blocs dans une source et dans un flux en visant le fond ou une paroi :
    vérifier le placement et la mise à jour de l'écoulement autour de l'obstacle.

La compilation Kotlin ne valide ni la compilation des shaders sur le pilote
OpenGL du téléphone ni les performances CPU/GPU réelles. Aucun APK n'est construit
ou installé par cette vérification.
