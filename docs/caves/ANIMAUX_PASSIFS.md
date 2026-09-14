# Animaux passifs — adultes, jeunes et robes

Les quatre espèces utilisent des modèles voxel pastel natifs mis en cache.

| Espèce | Robes | Jeune |
| --- | --- | --- |
| Mouton | Blanc, gris clair, gris ardoise, noir (laine uniquement neutre) | Agneau |
| Bovin | Fond vanille, taches noires, brunes ou rousses sur flancs et dos | Veau sans cornes |
| Poulet | Ivoire, fauve, charbon | Poussin à duvet jaune, doré ou gris, sans crête |
| Cochon | Rose, rose sable, gris perle | Porcelet |

Les jeunes ont un corps et des pattes raccourcis, une tête proportionnellement
plus grosse et une collision réduite. La tête descend et s’incline au broutage ;
les oiseaux picorent plus rapidement et agitent leurs ailes ; les cochons reniflent
le sol avec leur groin et animent leur queue en boucle. Aucune modification du sol.

## Monde

- Groupes de trois au maximum, déterminés par graine et cellule de 32 blocs,
  avec deux adultes et un jeune lorsque les trois emplacements conviennent.
  Le jeune rejoint un adulte proche de son espèce s’il s’éloigne de plus de 3,5 blocs.
- Moutons : plaines, forêts, bouleaux et taïga ; vaches : plaines, forêts et bouleaux.
- Poulets : plaines, forêts, bouleaux et lisières de jungle ; cochons : plaines,
  forêts, bouleaux et forêts sombres.
- Apparition sur herbe, humus ou mousse de surface, dans les chunks chargés.
- Promenade lente, pauses et éloignement quand le joueur approche à moins de 2,5 blocs.
- Vérification du volume et des appuis : obstacles, eau et descentes de plus
  d’un bloc refusés ; séparation entre animaux. Marches d’un bloc franchies.
- Population indépendante des monstres, également présente en créatif. Aucun
  ciblage de combat, dégât, butin ou gain d’XP animal dans cette première version.
- Au maximum 32 animaux actifs à proximité ; les autres restent mémorisés.

## Sauvegarde et visualisation

Le champ optionnel `passiveAnimals` conserve les cellules déjà peuplées, les espèces,
positions, orientations, pauses, statut jeune (`young`) et robe (`coat`). Sans ces
deux derniers champs, les animaux restent adultes, moutons blancs et bovins bruns.
Les nouvelles espèces et les jeunes apparaissent dans les cellules non encore
peuplées : aucun remplacement des animaux déjà sauvegardés. Les animaux éloignés restent stockés,
sans simulation hors écran. Un instantané immuable est publié toutes les deux secondes
sur le thread GL, puis enregistré par le mécanisme habituel du monde.

Dans Assaut → terrain de visualisation, la galerie expose les 26 combinaisons
espèce/âge/robe, chacune dans trois poses : référence, alimentation et marche.
La taille de la carte suit le nombre de présentoirs. Les légendes EN/FR indiquent
le nom du jeune ou de l’adulte, sa robe et son animation.

## Suite et contrôles sur appareil

Les jeunes ne grandissent pas encore automatiquement. Les sons, la reproduction,
la croissance, la tonte, le lait et les ressources alimentaires restent
à relier aux prochaines étapes outils/agriculture. Pas de recettes sans usage ajouté.
Le support supprimé sous un animal demande encore une physique de chute dédiée ;
le déplacement actuel suit les surfaces praticables.

À vérifier sur appareil : silhouettes et pivots dans les trois poses de la galerie,
rencontre des quatre espèces en surface, suivi des adultes par les petits,
conservation des robes et des âges au rechargement, arrêt devant eau/mur/précipice, éloignement
à l’approche, puis sauvegarde/rechargement et retour après un voyage lointain.
Compilation autorisée : `compileDebugKotlin` seulement, sans APK ni installation.
