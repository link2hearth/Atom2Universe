# Atelier des biomes

Accès : Cave World → Assaut → **Atelier des biomes · visite libre**.
Carte intégrée, disponible sans export ni téléchargement. La visite est sans
soldats, manches ou chronomètre, à midi fixe. Les blocs ne sont pas modifiables.
Une chute dans le vide ramène au départ. La carte de combat existante est conservée.

![Plan calculé depuis les blocs de la carte](atelier-biomes.png)

Le monde mesure 72 × 98 blocs au sol et 32 blocs de haut. Il comporte six
vignettes de 16 × 16 cases (256 cases de surface chacune), reliées par des allées :

- Montagne en gradins, sommet enneigé, glace, éboulis et coupe de minerais.
- Désert, petite dune, grès, sable rouge et cactus.
- Plage, graviers, argile et bassin côtier.
- Forêt, arbres ordinaires et bouleau, sol forestier, mousse et champignons.
- Marais, boue, argile, roseaux et pierre moussue.
- Relief volcanique en basalte avec bassin de lave contenu.

Deux fours et deux établis sont présentés au bord du chemin, avec orientations
différentes. Le four possède désormais des côtés à évents et un dessus dédié ;
l'établi possède des côtés avec tiroir et tablette, et un dessous en bois encadré.

La galerie expose automatiquement chaque entrée du registre, triée par identifiant :
166 blocs actuellement, directement posés au sol sans socle, y compris les marqueurs et liquides. Les liquides sont
placés dans des récipients en verre. Le bandeau affiche la zone, puis le nom de
l'échantillon proche dans la galerie. Ce nom dépend de la position, pas de la visée.
La longueur de la carte augmente automatiquement si le registre s'agrandit.
Les arbres utilisent les teintes prévues pour ces décors ; la galerie est neutre.

## Matériaux ajoutés

| Identifiant | Bloc |
|---|---|
| 2300 | Pierre brute / cobblestone |
| 2301 | Pierre brute moussue |
| 2302 | Grès |
| 2303 | Boue |
| 2304 | Argile |
| 2305 | Sol forestier |
| 2306 | Mousse |
| 2307 | Basalte |

Ils sont disponibles dans le registre et l'inventaire créatif, avec noms anglais
et français. Ils ne sont ajoutés à aucune distribution de biomes de survie.
Le comportement de collecte de la pierre existante ne change pas.

![Nouveaux matériaux et faces](materiaux.png)

## Vérifications

- `compileDebugKotlin` réussi ; aucun APK construit ou installé.
- Les 166 identifiants du registre sont présents dans la carte générée.
- Aucun identifiant de bloc inconnu ; spawn dégagé avec sol, pieds et tête libres.
- Tous les liquides ont un support et des côtés fermés.
- Aller-retour sérialisation A2Map vérifié, blocs et orientations conservés.
- Plan produit depuis le générateur Kotlin compilé, avec les identifiants et
  couleurs des JSON réels. Ce plan ne représente pas l'éclairage 3D du jeu.
- 172 recettes de textures utilisées, 225 couches GPU de 32 × 32, variantes comprises.

Le parcours, les collisions et le rendu final restent à vérifier sur appareil.
Les scènes sont définies dans `ShowcaseMap.kt`, les règles de visite dans
`ShowcaseMode.kt`. Les aperçus proviennent de ces données et des recettes Kotlin,
sans créer un second générateur de carte.
