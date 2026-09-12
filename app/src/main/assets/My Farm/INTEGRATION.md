# Ma Ferme — décor pixel art validé

`ma-ferme-printemps.html` contient la référence visuelle animée validée par le propriétaire. Le dessin est autonome (Canvas 2D JavaScript, sans image ni dépendance externe). Ouvrir le fichier dans un navigateur pour observer le décor, le vent et le portail. Les contrôles utilisent des classes de présentation de l'aperçu Codex ; hors Codex, ils conservent leur apparence native et leur fonctionnement.

Ce fichier est un prototype de design, pas une implémentation Android. L'intégration reste à réaliser en Kotlin avec Canvas Android, sans WebView.

## Design à conserver

- Palette vive et colorée, verts printaniers, bois doré, fleurs pastel.
- Herbe et chemins texturés, sans raccords de tuiles visibles.
- Vent commun : objet `vent`, direction gauche vers droite, vitesse 20 pixels logiques/s, longueur d'onde 320 pixels, intensité 1,7. Cycle de 16 secondes. Toutes les plantes suivent le même champ spatial, sans phase aléatoire individuelle.
- Buissons : base et ombre fixes ; déformation croissante vers le sommet. Le prototype met leur dessin en cache puis décale les lignes selon leur hauteur.
- Poteaux aux sommets légèrement arrondis en pixels.
- Portail avec rotation progressive, épaisseur de bois, tranche ombrée et dessus éclairé. Sa diagonale est projetée comme une planche, sans trait d'épaisseur constante à l'écran.
- Jonction des chemins calculée comme une union adoucie ; la branche se termine à l'intérieur du chemin principal pour éviter un dépassement.
- Variantes stables de buissons, cailloux et touffes, pixels nets sans lissage.

## Périmètre de l'intégration future

Remplacer les usages du sheet `generated/garden/garden_environment_v1.png` par du dessin Kotlin et revoir les chemins de la ferme principale. Conserver les visuels des cultures, animaux et bâtiments. Ne pas supprimer les PNG sans demande explicite.

Points de départ : `FarmSprites.kt` (grass/environment), `FarmScenery.kt` (chemins et décor), `FarmWorldView.kt` (clôtures, portail et autres usages environment), dans `app/src/main/java/com/Atom2Universe/app/games/farm/`. Rechercher tous les usages avant remplacement, y compris ceux d'autres régions.

La petite parcelle de l'aperçu sert à valider le style : conserver la disposition et les règles du vrai jeu. Le portail actuel dépend de `state.parcelHasIdleGround(index)` ; animer les changements de cet état, sans reprendre le bouton de démonstration.

Pour Android : mettre en cache le décor fixe, dessiner les éléments visibles, partager une horloge de vent, éviter les allocations par image et respecter le cycle de vie de la vue. Les performances sur appareil restent à vérifier ; le prototype navigateur ne constitue pas une mesure Android.

Respecter AGENTS.md : textes éventuels en ressources anglais/français ; seule compilation `compileDebugKotlin` autorisée, pas de génération ni d'installation d'APK.

## État du portage

Herbe, chemins, clôture et portail sont maintenant dessinés en Kotlin natif (plus aucun usage de `garden_environment_v1.png` pour ces éléments) :

- `FarmSprites.grass()` : plus de tuiles-variantes façon sprite sheet (l'ancien "3 pelouses + 1 fleurie sur 12" était un reliquat du PNG). Comme dans `ma-ferme-printemps.html`, la couleur vient d'une onde continue évaluée en coordonnées MONDE (`sin(x/31+y/48)+sin(y/22-x/67)+bruit`) : deux tuiles voisines prolongent la même vague, rien ne se répète et rien ne peut créer de raccord. Chaque tuile 80×80 reste mise en cache (impossible de repeindre tout le monde à chaque image), juste échantillonnée à sa vraie position. Les touffes d'herbe et les fleurs isolées sont posées au cas par cas sur chaque tuile (deux tirages indépendants), jamais comme un bloc entier "spécial".
- `FarmScenery.ground()` : les chemins ne sont plus dessinés par des traits concentriques. Le réseau entier est peint une fois dans un bitmap de cache (à demi-résolution) à partir de la distance au chemin le plus proche - bord ondulé, tramage vert qui se fond dans l'herbe, sable tacheté avec cailloux - puis ce bitmap est simplement replaqué à chaque image (`canvas.drawBitmap`, un seul appel). La distance est calculée en semant chaque échantillon de chemin sur son propre voisinage plutôt qu'en scannant tout le monde par pixel, pour rester rapide au premier affichage.
- `FarmScenery.fenceRail()` / `fencePost()` / `gate()` remplacent les sprites de clôture et de portail ; le portail anime sa rotation en continu (`FarmWorldView.updateGateOpening`) au lieu de basculer entre deux images fixes.
- `FarmScenery.bush()` / `rock()` remplacent les sprites de buissons et rochers décoratifs (`FarmScenery.objects()`, et le buisson du bonus quotidien dans `FarmWorldView`). Le buisson est mis en cache par variante (silhouette 80×80, 6 variantes) puis replaqué par bandes horizontales décalées pour le vent - base et ombre fixes, inclinaison croissante vers le sommet, comme `paintBush()`/`bush()` dans le prototype. Le rocher est statique (pas de vent), avec facettes ombrées et touffe de mousse optionnelle.
- La densité d'herbe/fleurs a été revue à la hausse : l'essentiel (speckle fin, petites brindilles statiques, fleurs éparses) est peint une fois dans la tuile mise en cache (coût nul une fois en cache) ; seules 2-3 touffes par tuile restent animées en direct par le vent, pour garder le coût par image raisonnable.

Restent sur `garden_environment_v1.png` sans changement : les variantes de débris (`FarmWorldView`, `sprites.environment(3,0/2,3/3,3)` - rocher/mauvaises herbes du mini-jeu de déblaiement, distinct des rochers/buissons décoratifs). Les cultures (`ma-ferme-cultures*.html`) n'ont pas été touchées, voir `CULTURES.md`.
