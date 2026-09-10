# Assets d’élevage — version 1

Créés avec l’outil intégré image_gen. Prompts complets dans `prompts.json`.
Les cinq PNG originaux font chacun 1536 × 1024 pixels et possèdent un canal alpha.
Les couleurs visibles sous les pixels entièrement transparents ne doivent pas être rendues : conserver le canal alpha au chargement.

## Animaux : 24 sprites statiques

Chaque planche comporte deux rangées de variantes de robe/plumage.

| Fichier | Sujets, de gauche à droite | Variantes |
| --- | --- | --- |
| cattle_v1.png | Vache, veau, taureau | Caramel et crème ; noir et blanc |
| sheep_v1.png | Brebis, bélier, agneau | Ivoire ; laine beige et face grise |
| poultry_v1.png | Poule, coq, poussin | Plumage roux ; plumage clair |
| pigs_v1.png | Cochon mâle, truie, cochonnet | Rose ; rose tacheté brun |

## Décors : 6 sprites

`decor_v1.png`, première rangée : auge à foin, abreuvoir extérieur, abri de nuit pour bovins.
Deuxième rangée : abri pour moutons, poulailler avec rampe, abri pour cochons.

## Découpage

Utiliser `atlas.json` : chaque rectangle est donné en pixels sous la forme `[x, y, largeur, hauteur]`.
Les colonnes ne sont pas toutes de largeur identique : le découpage a été adapté aux contours réels pour ne pas couper les animaux et les toitures.
Le point d’ancrage est le bas-centre du rectangle. Préserver les proportions lors du rendu.
Les échelles relatives entre familles et bâtiments seront à régler dans le jeu.

Contrôles effectués : dimensions, présence de transparence et absence de pixels de sujet (alpha > 128) sur les limites de découpage.

Ces assets sont intégrés à la page Élevage : quatre parcelles, achats de reproducteurs, naissances, croissance et vente des adultes. Les sprites restent des poses statiques, sans animation de marche.