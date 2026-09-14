# Génération naturelle — version 3

Les nouveaux mondes utilisent la version 3. Les sauvegardes existantes conservent
leur version de génération : changer leur terrain déplacerait les constructions
et ferait apparaître des raccords entre régions explorées et nouvelles régions.
Créer un monde pour essayer cette génération. Le jardin d’essai Assaut reste une
carte finie indépendante.

## Une surface continue

Il n’existe plus de succession de bandes de grottes et de surfaces souterraines,
ni d’îles célestes. Une hauteur de terrain est calculée pour chaque colonne X/Z ;
au-dessus, uniquement l’eau jusqu’au niveau marin 74, puis le ciel et la végétation.
Les cavités sont creusées dans le terrain, y compris sous les hautes montagnes.
La profondeur ne provoque aucun redémarrage périodique du relief.

Le relief privilégie les plaines et les forêts peu accidentées. Les collines
utilisent des ondulations larges ; les pics sont réservés à des zones plus rares.
Le mélange des amplitudes s’étend sur une grille de 192 blocs pour allonger les
transitions vers les massifs plutôt que produire des pointes rapprochées.

Le climat, la continentalité et le caractère montagneux sélectionnent et mélangent
22 profils de biomes. Les paramètres propres à cette version sont dans
`app/src/main/assets/caves/natural_generation.json` :

- `base` : décalage du relief par rapport à la mer ;
- `amplitude` : puissance des crêtes et des collines, en blocs ;
- `temperature`, `humidity` : climat préféré, entre 0 et 1 ;
- `rarity` : pénalité de sélection pour les biomes plus rares.

La végétation réutilise les définitions des biomes de surface. Les anciennes
valeurs `height_base`, `height_amplitude`, `height_blocks` et les dossiers
`underground`/`gigacave` ne commandent plus le relief des nouveaux mondes.
Les anciennes structures avec plateformes aplaties ne sont pas générées en v3.

Les réglages actuels produisent des massifs pouvant atteindre environ 1 000 blocs. Le scan du
rendu lointain couvre jusqu’à 4 095 blocs ; ce plafond technique du scan ne crée
aucune couche solide. Les paramètres restent à garder dans cette enveloppe si
on les augmente. Le monde n’utilise pas une pile de surfaces jusqu’à cette limite.

## Grottes et ressources

Deux champs de bruit combinés forment les galeries sinueuses ; un autre champ
forme les grandes salles. Une déformation du bruit évite des motifs trop réguliers.
Le champ est interpolé sur une grille mondiale de 4 blocs, identique aux raccords
de chunks, y compris dans les coordonnées négatives. Les entrées sont localisées,
et une épaisseur de roche sous les mers limite les inondations initiales.

Les roches varient en volumes 3D : pierre, granite, calcaire, quartz et basalte.
Les minerais forment des petits dépôts déterministes ; les ressources précieuses
demandent davantage de profondeur sous la surface locale, et non une altitude
absolue. Aucun plancher horizontal n’est imposé aux grandes salles.

| Milieu | Ressources de surface |
|---|---|
| Berges et fonds peu profonds | Argile, sable, gravier, boue |
| Forêts | Humus, mousse, bois et végétation |
| Marais | Boue, mousse, roseaux au contact de l’eau |
| Régions volcaniques | Basalte |
| Déserts rouges | Sable rouge et terre cuite |
| Régions froides et sommets | Neige, terre enneigée, glace sur l’eau froide |

Les recettes d’argile, de briques, de variantes moussues et de basalte deviennent
accessibles par exploration. La glace reste fragile à la récolte : les outils de
récupération et le regel renouvelable restent à ajouter avec les outils/climat.

## Vérifications

`NaturalTerrainCheck.java` utilise les classes Kotlin compilées : diversité sur
trois graines, ressources naturelles, apparition sur terrain sec, ciel vide aux
anciennes altitudes d’îles, déterminisme et comparaison de chaque voxel avec le
champ de grottes autour des anciennes limites de couches. Il produit aussi une
coupe 2D du terrain. `compileDebugKotlin` vérifie l’intégration Android.

Le rendu et les performances sur téléphone restent à apprécier en jeu ; aucun
APK n’est construit ou installé par ces vérifications.
