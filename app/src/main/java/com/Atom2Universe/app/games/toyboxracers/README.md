# Toybox Racers : pièces, carrefour et maison continue

Objectif : explorer une maison de jouets continue en voiture, traverser ses couloirs
et participer à une course dans chaque pièce. La seed doit reproduire la maison.

## État actuel

- Huit pièces indépendantes : chambre, cuisine, salon, salle à manger, bureau,
  salle de bains, buanderie et garage. Chaque pièce accepte les onze circuits
  à une seule pièce (le mode Maison, ci-dessous, est séparé).
- Deux longues boucles « meubles », « Tour des meubles » (~894 unités) et
  « Grande expédition » (~1008 unités), utilisent un meuble traversable dessus
  et dessous, des rampes en bois et des portions libres guidées par des chevrons.
  Le mobilier et le plancher sont les vrais supports physiques.
- « Grand carrefour » (`CROSSROADS_SHOWCASE`) généralise le croisement du grand
  huit à plusieurs occurrences : deux sauts à des hauteurs distinctes (`GradeCrossing`,
  table `CircuitCrossings`) et un pont fixe sans aucun vide, plus un tunnel
  décoratif traversable (`DecorStructures`). `PrototypeTrack.crossingAt()`
  remplace l'ancien couple `jumpStartDistance/jumpEndDistance` câblé pour un
  seul croisement ; ces deux champs restent en compatibilité (premier croisement).
- **Mode Maison** (`HOUSE_GROUND_FLOOR`, bouton dédié « Maison », hors sélecteur
  pièce/circuit habituel) : un étage complet et continu, quatre pièces fixes
  (chambre, bureau, salon, cuisine) reliées par un couloir central est-ouest,
  sans recharger de scène en changeant de pièce. `HouseGeometry` applique enfin
  les poses préparées par `HouseRoom` (translation + symétrie de rotation) au
  mur, au mobilier et aux jouets. Chaque porte (position historique sud-ouest de
  `RoomDecor`) devient un vrai passage : le mur correspondant est simplement
  absent de la liste de `RoomBox`, à la fois pour le rendu et pour la collision.
  Un parcours signature (dans `OrganicCircuits.trails[HOUSE_GROUND_FLOOR]`, en
  coordonnées monde) part du lit, traverse le tapis entre les jouets, franchit
  le couloir, grimpe sur la chaise puis le bureau du bureau, saute vers le sol,
  monte sur la commode puis l'étagère-escabeau (`DecorHouseFurniture`), saute sur
  l'armoire et referme la boucle par le couloir.
- Le bouton de pièce ouvre la liste des huit pièces et de leurs circuits (hors
  Maison). Les scènes à une pièce restent séparées : en choisir une recharge la
  scène locale et son départ.
- Le bouton « Seed de la maison » de cette liste permet de saisir un entier signé
  sur 64 bits. Valider rétablit toutes les affectations produites par cette seed
  — cette seed ne pilote que les huit pièces indépendantes, pas le mode Maison
  (ses quatre pièces sont fixes : le parcours signature a besoin de savoir
  précisément où se trouve chaque meuble).
- Le choix manuel de circuit est mémorisé par pièce, indépendamment du plan généré.
  Les records restent attachés à la pièce, au circuit et à la difficulté.

## Séparation des responsabilités

- `HousePlan` produit le plan logique déterministe des huit pièces indépendantes :
  pièces mélangées, circuits répartis sans répétition, deux rangées de quatre
  pièces le long du couloir central (`HouseRoom.centerX/centerZ/quarterTurns`).
  La version 1 conserve son catalogue de huit pistes afin de reproduire les
  seeds enregistrées.
- `HouseGeometry` consomme ces mêmes poses pour le mode Maison à quatre pièces
  fixes : `wallBoxes()`/`furnitureBoxes()` pour le mur affiché **et** collisionnable
  (toujours la même liste, jamais deux chemins séparés — c'est ce qui évite de
  reproduire le bug de téléportation déjà rencontré au croisement du grand huit),
  `furnitureDecorations()`/`toys()` pour le mobilier et les jouets transformés.
- `RoomThemes` décrit couleurs, sols et meubles des pièces autres que chambre et
  cuisine. `RaceLayouts` conserve les implantations historiques de chambre et
  cuisine, et bascule vers `HouseGeometry` pour `HOUSE_GROUND_FLOOR`.
- `PrototypeTrack` décrit le circuit dans les coordonnées locales de sa pièce
  pour les onze circuits à une seule pièce ; le mode Maison réutilise la même
  classe avec des coordonnées de monde plus étendues (`ArcadeCar` ne suit jamais
  la spline, donc rien à généraliser côté conduite).
- `OrganicCircuits` sépare la trajectoire de course de la surface : dalle de rampe,
  plancher ou mobilier. Ne pas ajouter de dalle invisible aux portions ouvertes.
  Le parcours signature de la maison y vit comme une entrée de plus.
- `CircuitCrossings`/`GradeCrossing` généralisent la notion de croisement à N
  occurrences par circuit ; `CrossroadsCircuit` est le premier circuit à en
  utiliser plusieurs.
- Les menus utilisent `Theme.Toybox.Dialog` pour garantir leur contraste, sans
  hériter des couleurs incohérentes des dialogues du thème global.

Les réceptions sur une pente sont calculées relativement aux deux hauteurs de
route (avant/après le déplacement), même si la voiture monte encore. Les contacts
doux ne prélèvent plus systématiquement de vitesse. Les tests de régression sont
dans `PrototypeTrackTest` ; la politique du dépôt limite ici la vérification
exécutée à `compileDebugKotlin`, sans APK ni exécution des tests Gradle (les
tests JVM `testDebugUnitTest` restent possibles en développement local).

## Connu : un test pré-existant en échec, sans lien avec ce travail

`elevatedRoadBlocksTheCarFromBelow` échouait déjà avant ces ajouts (vérifié par
un `git stash`) : un cas de collision par le dessous sur le pont du grand huit à
creuser séparément. Les nouveaux tests de croisements multiples et de la maison
passent tous.

## Prochaine étape

Plusieurs étages verticaux réels (au-delà du simple rez-de-chaussée actuel),
la maison complète à huit pièces simultanées, des portes qui s'ouvrent/se
ferment dynamiquement, et une généralisation d'`ArcadeCar` via une interface
`DrivableSurface` si un futur étage 2 l'exige. Affiner les marges du parcours
signature (chaise/bureau/commode/étagère/armoire) par playtest sur device,
comme la marge de 2,33 unités de `TOYBOX_DECORS.md` l'a été en son temps.

La génération actuelle est la version 1 (`house_seed_v1`, `house_circuit_v1_*`).
Versionner les sauvegardes si les listes, leur ordre ou l'algorithme changent,
afin de ne pas modifier silencieusement une maison enregistrée.
