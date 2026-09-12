# Cultures — références visuelles

- `ma-ferme-cultures.html` : tomates, carottes, salades. Version corrigée avec feuilles agrandies, contraste renforcé, fond atténué et affichage en résolution ×3.
- `ma-ferme-cultures-lot-2.html` : radis, pommes de terre, choux-fleurs, brocolis. Même style et méthode d'animation.
- `ma-ferme-cultures-lot-3.html` : courgettes, poivrons, aubergines. Quatre variantes par culture, floraison et croissance progressive, fruits striés ou brillants, même vent discret en résolution ×3.

Chaque culture présente quatre variantes déterministes, un curseur de croissance et une lecture de la pousse sur 26 secondes pour la démonstration. Ces durées ne remplacent pas les durées du jeu. Les fichiers sont autonomes, sans PNG ni dépendance externe ; ils peuvent être ouverts dans un navigateur. Hors de l'aperçu Codex, les contrôles conservent une présentation native.

## À conserver lors du portage Kotlin

- Grandes feuilles lisibles, contours verts foncés, reflets clairs, touches pastel.
- Graine stable par plante : silhouettes, tailles et couleurs ne changent pas au déplacement de la caméra.
- Développement distinct des tiges, feuilles, fleurs et fruits, plutôt qu'un simple agrandissement du dessin final.
- Les racines et tubercules restent sous terre ; seules les épaules des radis et carottes émergent.
- Le vent utilise un champ partagé de gauche à droite : vitesse 16 pixels logiques/s, longueur d'onde 320, intensité 1,1, soit un cycle de 20 secondes.
- Les silhouettes sont dessinées sans vent puis mises en cache. L'animation applique une transformation continue de la plante entière, ancrée au sol, à une résolution d'affichage trois fois supérieure. Ne pas revenir à un déplacement indépendant et arrondi des feuilles ou des pixels : ce rendu avait été rejeté comme trop saccadé.
- Le grossissement initial conserve les pixels nets ; la transformation animée utilise un léger filtrage pour adoucir le mouvement.

Le brocoli est une proposition supplémentaire : il n'était pas dans l'énumération FarmCrop consultée lors de la création. Son ajout au jeu reste une décision distincte.

Ces fichiers sont des prototypes de design en Canvas JavaScript, à porter vers Canvas Android en Kotlin, pas à intégrer via WebView. Aucun remplacement des cultures du jeu n'a été effectué ici. Suivre aussi `INTEGRATION.md` et les règles du dépôt.
