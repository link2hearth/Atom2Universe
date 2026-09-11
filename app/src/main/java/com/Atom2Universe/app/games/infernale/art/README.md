# Infernale — bibliothèque graphique des 126 pièces

Le dessin est natif Android Canvas : aucun fichier JSON ou SVG à charger.
Les sprites sont dessinés sur une grille transparente de 64 × 64 pixels puis
agrandis sans interpolation. Palette pastel, contours ardoise, deux niveaux
d'ombrage. Il s'agit d'une première direction artistique, pas du jeu complet.

## Contenu

- `InfernalePartArt` : interface Canvas publique inchangée. Les 23 familles restent
  une classification ; chaque identifiant possède désormais son propre dessin.
- `InfernalePixels` : raster Kotlin partagé avec l'outil d'export, sans dépendance
  Android. Aucun dessin de substitution : une pièce inconnue dans le raster
  provoque une erreur, détectée par la vérification de tout le catalogue.
- `InfernaleMechanicalSprites`, `InfernaleFluidSprites`, `InfernaleDeviceSprites` :
  126 branches de dessin spécifiques, avec primitives communes (boulon, roue,
  ressort, manomètre). Les pièces proches ont des détails fonctionnels différents :
  denture interne, roulements à billes, manetons, raccords, ressort de rappel,
  filtre, électrodes, levier ou champ de détection.
- `InfernaleArtPreviewView` : planche native animée des 126 pièces, textes français
  et anglais. Vue de développement, pas encore reliée au hub ou au manifeste.
  Seules les cellules qui croisent la zone visible sont dessinées.
- Le catalogue et le moteur physique restent indépendants de cette bibliothèque.

## Utilisation dans la future vue du jeu

Créer un renderer par vue et réutiliser les rectangles et les états lorsque
possible. Appeler depuis le thread UI :

```kotlin
val art = InfernalePartArt()
val bounds = RectF(0f, 0f, 192f, 192f)
// Dans onDraw : angle réel du mécanisme, en radians.
art.draw(canvas, "transmission.spur_gear", bounds,
    InfernalePartArt.State(angle = shaftAngle))
```

`travel` règle la course du piston / compression du ressort ; `level` règle le
réservoir / la jauge / la batterie ; `active` règle bouton et voyant ; `phase`
fait défiler le convoyeur. Aucune horloge n'est utilisée par le renderer : la
pause du jeu conserve les dessins exactement dans leur état.

77 pièces réagissent à au moins un état ; les éléments passifs (poutre, boulon,
tuyau, etc.) ne bougent pas artificiellement.
Pour les deux protections `safety.fuse` et `safety.rupture_disk`, `active` représente
le déclenchement de la protection (rupture). Le futur jeu doit conserver cet état
si la rupture est irréversible. Le renderer est une vue, pas une simulation.

Les coordonnées vont vers le bas. Appliquer la position et la rotation du corps
avec `canvas.save()`, `translate()`, `rotate()` et `restore()` autour du dessin.
L'angle interne est distinct de la rotation du corps : ne pas appliquer deux fois
la même rotation à une roue. Un rectangle carré de taille multiple de 64 produit
les pixels les plus réguliers. L'alpha permet l'estompage des couches.

Pour afficher la planche dans une activité de développement :

```kotlin
setContentView(android.widget.ScrollView(this).apply {
    addView(InfernaleArtPreviewView(this@YourActivity))
})
```

## Suite nécessaire pour le jeu

Les silhouettes actuelles servent d'icônes et de prototypes animés. Elles sont
centrées dans un carré : elles ne représentent pas encore la géométrie physique
paramétrable du catalogue. Le rendu des poutres de longueur libre, des cordes
entre deux ancrages, des ports et des dents réellement engrenées devra suivre
les dimensions et connexions de la future scène. Le tampon réutilisé évite une
allocation de bitmap par pièce, mais le dessin est recalculé à chaque appel :
prévoir un cache d'icônes statiques si un grand catalogue est affiché.

La planche native doit encore être vérifiée visuellement sur Android. Elle ne
simule aucun mécanisme : son horloge montre seulement les possibilités d'animation.

## Voir et vérifier les dessins sans APK

Depuis la racine du dépôt :

```powershell
./tools/infernale/export-art.ps1
```

L'outil utilise uniquement le JBR et les dépendances Kotlin déjà en cache. Il
compile le raster de production pour la JVM, exporte trois planches PNG et une
galerie HTML animée dans `build/infernale-art/`. Ouvrir `index.html` avec son
fichier voisin `animation.png` : filtre par nom ou identifiant, pause/reprise,
respect de la préférence de réduction des animations. Aucun serveur nécessaire.

La vérification parcourt **les 126 identifiants du catalogue réel**, à 16 étapes :
aucun sprite vide, aucune paire identique en couleur ou en niveaux de gris,
rendu déterministe, entrées NaN/infinies supportées. Les planches sont issues
exactement des pixels transmis au Bitmap Android, sans réinterprétation graphique.
Les poses animées sont aussi exportées dans `mouvements.png` pour inspection.
Les PNG ont été inspectés ; le comportement de la galerie HTML et l'intégration
sur appareil restent à vérifier (l'ouverture du fichier local a été bloquée
par la politique du navigateur de l'agent).
