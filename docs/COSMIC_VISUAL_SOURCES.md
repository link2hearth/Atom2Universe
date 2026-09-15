# Représentations astronomiques — 16 septembre 2026

## Ce qui est représenté

L'échelle cosmique compare des **rayons adoptés**, pas des photographies ni un classement
définitif des plus grands objets. Les sphères planétaires utilisent les rayons moyens
déjà présents. Les étoiles ont désormais une photosphère procédurale en lumière visible,
sans utiliser `textures/cosmic/star_surface.jpg`. Le Soleil du système solaire emploie
le même shader. Le soleil orange du gacha reste une illustration de jeu distincte.

Les surfaces ne sont pas des cartes observées : motifs, contraste, orientation et vitesse
d'évolution sont des choix pédagogiques. Les petites cellules solaires sont agrandies pour
rester visibles à l'écran. Les étoiles chaudes ont un contraste discret ; les géantes froides
et supergéantes présentent des structures plus larges. Une graine stable propre à chaque
astre distingue les motifs, sans prétendre décrire sa surface réelle.

## Références et corrections

| Sujet | Source primaire | Application et limites |
|---|---|---|
| Soleil en lumière visible | [NASA, couleurs du Soleil](https://svs.gsfc.nasa.gov/vis/a010000/a013800/a013859/script_31299_00.html), [NASA, Sun Facts](https://science.nasa.gov/sun/facts/) | Soleil presque blanc, granulation illustrative et taches possibles. Pas de texture solaire orange appliquée aux autres étoiles. |
| Structures des supergéantes | [ESO, Antares, 2017](https://www.eso.org/public/news/eso1726/), [Montargès et al., 2017](https://arxiv.org/abs/1705.07829) | Grandes structures et atmosphère complexe documentées. Leur forme dans l'application reste inventée, pas une reconstruction interférométrique. |
| Sirius A | [Davis et al., 2011](https://arxiv.org/abs/1010.3790) | Rayon corrigé de 1,19 à 1,713 R☉ ; incertitude publiée ±0,009 R☉. |
| Arcturus | [Ramírez & Allende Prieto, 2011](https://arxiv.org/abs/1109.4425) | Valeurs conservées : 25,4 ±0,2 R☉ et 4 286 ±30 K. |
| Bételgeuse | [Joyce et al., 2020](https://arxiv.org/abs/2006.09837) | 764 R☉ conservé comme résultat de ce modèle, avec −62/+116 R☉. Pas une valeur indépendante de la méthode ou de la distance. |
| UY Scuti | [Arroyo-Torres et al., 2013](https://arxiv.org/abs/1305.6179) | 1 708 ±192 R☉ et 3 365 ±134 K sont les estimations historiques conservées. Leur date et leur dépendance au modèle sont indiquées ; ce n'est pas une certification de la meilleure estimation actuelle. |
| Uranus et Neptune | [Irwin et al., 2024](https://academic.oup.com/mnras/article/527/4/11521/7511973) | Deux teintes bleu-vert pâle proches ; Neptune un peu plus bleue. Remappage illustratif des textures existantes, pas calibration spectrophotométrique. |
| Trous noirs | [NASA, anatomie](https://science.nasa.gov/universe/black-holes/anatomy/), [EHT, masse et ombre de M87, 2019](https://arxiv.org/abs/1906.11243) | Suppression de l'image générique faussement assimilée à l'horizon. Disque noir schématique de rayon 2GM/c² ; ni disque d'accrétion ni ombre optique. Les rapports de volume sont masqués lorsqu'un trou noir intervient. |

## Limites du catalogue restant

- Stephenson 2-18 : les 2 150 R☉ du catalogue sont conservés comme **scénario incertain**,
  explicitement non validé par cette révision. Ne pas transformer cette valeur en record.
- Les autres rayons, températures et masses historiques du catalogue n'ont pas fait l'objet
  d'une nouvelle revue exhaustive. Les masses de quasars, notamment TON 618, dépendent
  fortement des méthodes d'estimation. La fiche explique cette limite.
- La couleur des autres étoiles conserve l'approximation de corps noir existante ; la couleur
  du Soleil est choisie presque blanche. Ni synthèse spectrale détaillée, ni calibration d'écran,
  ni extinction interstellaire ne sont simulées.
- Les textures des autres planètes restent celles du projet. Leur provenance photographique
  précise et leur balance des couleurs ne sont pas certifiées par cette modification.
- Les trous noirs ont volontairement une géométrie similaire à masse mise à l'échelle :
  leur masse seule ne permet pas d'inventer une apparence réelle différente du disque environnant.

## Vérification

La vérification autorisée est `compileDebugKotlin`. La compilation Kotlin ne compile pas les
shaders GLSL : leur affichage, le contraste et la fluidité doivent être vérifiés sur l'appareil.
La vue Terre–Lune utilise désormais le plasma orangé animé du gacha, partagé via
`SunPlasmaShader` et `SunAnimationState`. Ce Soleil est un repère directionnel stylisé,
pas une vue en couleurs naturelles ni une représentation de sa distance réelle.
`textures/planets/sun.jpg` ne sert plus au rendu.
