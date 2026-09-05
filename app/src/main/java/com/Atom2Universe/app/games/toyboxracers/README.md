# Toybox Racers : pièces et maison

Objectif : explorer une maison de jouets continue en voiture, traverser ses couloirs
et participer à une course dans chaque pièce. La seed doit reproduire la maison.

## État actuel

- Huit pièces : chambre, cuisine, salon, salle à manger, bureau, salle de bains,
  buanderie et garage. Chaque pièce accepte les dix circuits.
- Deux longues boucles supplémentaires, « Tour des meubles » (environ 894 unités)
  et « Grande expédition » (environ 1 008 unités), utilisent un meuble traversable
  dessus et dessous, des rampes en bois et des portions libres guidées par des
  chevrons. Le mobilier et le plancher sont les vrais supports physiques.
- Le bouton de pièce ouvre la liste des pièces et de leurs circuits. Les scènes
  restent séparées : choisir une pièce recharge la scène locale et son départ.
- Le bouton « Seed de la maison » de cette liste permet de saisir un entier signé
  sur 64 bits. Valider rétablit toutes les affectations produites par cette seed.
- Le choix manuel de circuit est mémorisé par pièce, indépendamment du plan généré.
  Les records restent attachés à la pièce, au circuit et à la difficulté.

## Séparation des responsabilités

- `HousePlan` produit le plan logique déterministe : huit pièces mélangées, huit
  circuits répartis sans répétition, deux rangées de quatre pièces le long du
  futur couloir central. Les poses mondiales sont préparées mais pas encore
  appliquées au rendu ni aux collisions.
  La version 1 conserve son catalogue de huit pistes afin de reproduire les
  seeds enregistrées ; les deux parcours organiques se choisissent dans le menu.
- `RoomThemes` décrit couleurs, sols et meubles des nouvelles pièces.
  `RaceLayouts` conserve les implantations historiques de chambre et cuisine.
- Le mobilier rendu et ses collisions proviennent des mêmes placements et boîtes.
  La zone de la porte au sud-ouest reste libre ; les portes sont encore fermées.
- `PrototypeTrack` décrit le circuit dans les coordonnées locales de sa pièce.
- `OrganicCircuits` sépare la trajectoire de course de la surface : dalle de rampe,
  plancher ou mobilier. Ne pas ajouter de dalle invisible aux portions ouvertes.
- Les menus utilisent `Theme.Toybox.Dialog` pour garantir leur contraste, sans
  hériter des couleurs incohérentes des dialogues du thème global.

Les réceptions sur une pente sont calculées relativement aux deux hauteurs de
route (avant/après le déplacement), même si la voiture monte encore. Les contacts
doux ne prélèvent plus systématiquement de vitesse. Les tests de régression sont
dans `PrototypeTrackTest` ; la politique du dépôt limite ici la vérification
exécutée à `compileDebugKotlin`, sans APK ni exécution des tests Gradle.

## Prochaine étape : continuité de la maison

Utiliser les poses de `HouseRoom` pour transformer ensemble route, décor et
collisions. Construire le couloir central et ouvrir les portes correspondantes
dans la géométrie **et** dans la collision des murs. Conserver la position et la
vitesse de la voiture lors du franchissement ; la progression de course reste
locale au circuit. Ne pas remplacer les couloirs par des téléportations de menu.

La génération actuelle est la version 1 (`house_seed_v1`, `house_circuit_v1_*`).
Versionner les sauvegardes si les listes, leur ordre ou l'algorithme changent,
afin de ne pas modifier silencieusement une maison enregistrée.
