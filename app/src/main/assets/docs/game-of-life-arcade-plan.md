# Plan d'intégration : Jeu de la vie de Conway dans l'Arcade Hub

## 1. Objectifs principaux
- Ajouter une nouvelle page "Jeu de la vie" accessible depuis le hub Arcade existant.
- Fournir une expérience utilisateur fluide avec contrôle complet de la simulation (lecture, pause, avance rapide, retour arrière).
- Offrir une surface de jeu théoriquement infinie en ne simulant que la zone visible et son voisinage immédiat.
- Mettre à disposition un catalogue de figures célèbres et un générateur aléatoire configurables.
- S'appuyer sur des optimisations modernes pour maintenir des performances élevées, même avec de grandes grilles visibles.

## 2. Parcours utilisateur et navigation
1. **Accès via l'Arcade Hub**
   - Ajouter une vignette "Jeu de la vie" dans l'interface Arcade avec visuel et court descriptif.
   - Lien interne ouvrant la nouvelle page dans la même application (pas de nouvelle fenêtre).
2. **Page du mini-jeu**
   - Layout en deux zones :
     - A gauche : surface de jeu avec barre flottante de contrôle.
     - A droite : panneau latéral repliable pour options, figures, randomisation et aide.
   - Prévoir un bouton de retour vers le hub.

## 3. Architecture fichiers & configuration
- **Fichiers dédiés** :
  - `scripts/arcade/game-of-life.js` pour la logique principale.
  - `styles/arcade/game-of-life.css` pour le style spécifique.
  - `config/arcade/game-of-life.json` pour les paramètres ajustables (vitesse par défaut, densité aléatoire, limites de zoom, etc.).
- **Routage/Intégration** :
  - Étendre le module de navigation de l'Arcade pour enregistrer la nouvelle page.
  - Ajouter les clés i18n correspondantes dans les fichiers de langue (titres, labels de boutons, descriptions).
- **Assets** :
  - Créer un visuel statique (PNG/SVG) pour la vignette.

## 4. Conception UI/UX
- **Surface de jeu** :
  - Canvas HTML5 ou `<div>` à base de WebGL/Canvas 2D pour rendu performant.
  - Grille affichée avec options : activer/désactiver la grille, régler la taille des cellules via zoom.
- **Contrôles principaux** (barre flottante en overlay) :
  - Boutons : Lecture/Pause (toggle), Avance rapide (x2/x4), Retour arrière (undo step), Pas-à-pas (step), Réinitialisation.
  - Curseur de vitesse continue (slider) lié à la config.
  - Indicateurs : génération courante, population.
- **Interactions directes** :
  - Clique gauche : basculer état d'une cellule.
  - Drag pour dessiner/effacer.
  - Sélection rectangulaire pour copier/coller et placer des motifs.
  - Zoom/dézoom : molette + ctrl (ou pinch sur tactile), avec recentrage autour du pointeur.
  - Panoramique : maintenir espace + drag ou clic milieu.
- **Panneau latéral** :
  - Onglet "Figures" : liste des motifs (Planeur, Pulsar, Fusée, etc.) avec aperçu, placement via clic.
  - Onglet "Random" : config surface (largeur, hauteur, densité), bouton générer et options pour ajouter ou remplacer.
  - Onglet "Aide" : rappel commandes, liens ressources.
  - Historique des états (pile) avec miniatures facultatives.

## 5. Moteur de simulation
- Représentation centrée sur la zone visible :
  - Maintenir un offset `worldOrigin` et une matrice dynamique pour la portion affichée.
  - Calculer les cellules actives dans un rayon tampon (1 cellule) autour de l'écran pour anticiper l'évolution.
- Modèle de données :
  - Utiliser une structure `Set`/`Map` de cellules vivantes indexées par clés "x:y" ou un `Map` bidimensionnel.
  - Pour le rendu, convertir en buffer 2D correspondant à la fenêtre de vision.
- Gestion du temps :
  - Tick basé sur `requestAnimationFrame` couplé à un accumulateur de temps pour respecter la vitesse choisie.
  - Prévoir un mode "avance rapide" en calculant plusieurs générations par frame.
- Retour arrière :
  - Stocker un historique limité (pile circulaire configurable) des états compressés (diffs ou bitsets) pour supporter undo/rewind.

## 6. Optimisations clés
- **Calcul** :
  - Utiliser l'algorithme "hashlife" simplifié ou `Sparse QuadTree` si nécessaire, mais prioriser une approche sparse Set + voisinage pour la zone visible.
  - Pré-calculer les voisins actifs en utilisant un compteur par cellule (Map cellule -> nombre de voisins vivants) mis à jour incrémentalement.
  - Ne recalculer que les cellules à proximité de cellules vivantes (liste de candidats).
- **Rendu** :
  - Batch draw via Canvas 2D `fillRect` groupé ou WebGL instancing pour gros zoom out.
  - Double buffering : calculer l'image dans un `OffscreenCanvas` puis copier.
  - Ajuster la densité de la grille selon le niveau de zoom pour limiter les lignes.
- **Mémoire** :
  - Compresser l'historique via RLE ou bit arrays.
  - Nettoyer automatiquement les zones totalement mortes hors champ.
- **Responsiveness** :
  - Utiliser Web Workers pour le calcul lorsque la zone visible est très dense.
  - Détecter inactivité pour réduire la fréquence d'update.

## 7. Catalogue de figures
- Stocker les motifs dans un fichier JSON (`resources/patterns/game-of-life.json`) avec métadonnées (nom, description, dimensions, offsets, pattern en notation RLE standard).
- Lorsqu'un motif est sélectionné :
  - Afficher un aperçu dans le panneau.
  - Permettre le placement par clic (prévisualisation fantôme avant validation).
  - Historiser le placement pour permettre l'annulation.

## 8. Randomisation
- Options configurables : largeur, hauteur, densité (0-100%), seed optionnelle.
- Boutons :
  - "Remplacer" (vide la zone visible puis remplit aléatoirement la zone définie).
  - "Ajouter" (superpose la génération aléatoire aux cellules existantes).
- Implémentation :
  - Utiliser un générateur pseudo-aléatoire déterministe pour permettre la reproductibilité (seed).
  - Supporter la génération sur des zones décalées (offset configurable).

## 9. Accessibilité & i18n
- Tous les boutons avec libellés + attribut `aria-label`.
- Navigation clavier : focus visuel sur les contrôles, activation via touches (espace pour pause, flèches pour step).
- Textes regroupés dans le système i18n (clés `arcade.game_of_life.*`).

## 10. Tests & validation
- **Unitaires** : tester la logique de calcul de génération, la gestion de l'historique et du random.
- **Intégration** : vérifier l'ajout et le placement des motifs, les contrôles de lecture/rewind.
- **Performance** : scénarios stress (grande densité, zoom out max) avec mesure FPS.
- **UX** : tests manuels sur desktop + mobile/tablette.

## 11. Roadmap de mise en œuvre (itérative)
1. Préparation : config, fichiers vides, entrée Arcade, i18n basique.
2. Moteur minimal : affichage canvas, placement manuel, itération simple sur zone visible.
3. Contrôles de simulation (lecture/pause, step, vitesse) + zoom/pan.
4. Optimisations de calcul et rendu.
5. Catalogue de figures + placement.
6. Randomisation configurable.
7. Retour arrière/avance rapide et historique.
8. Ajustements UX, accessibilité, tests et documentation utilisateur.

## 12. Livrables complémentaires
- Documentation utilisateur (guide rapide dans `docs/` ou intégrée dans le panneau aide).
- Tutoriel interactif optionnel pour présenter les commandes principales.
- Script de benchmark optionnel (mode développeur) pour visualiser les performances selon la densité.

