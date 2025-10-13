# Pistes d'optimisation pour Atom2Univers

Ce document propose plusieurs améliorations concrètes pour alléger le chargement initial du jeu et réduire le coût des mises à jour d'interface pendant les parties. Chaque piste est accompagnée d'un résumé du comportement actuel et de suggestions de mise en œuvre.

## 1. Charger les modules arcade à la demande

**Constat actuel :** tous les mini-jeux arcade (Particules, Wave, Balance, Math, Sudoku, etc.) sont chargés dès l'ouverture de la page, alors qu'un joueur n'en ouvrira généralement qu'un seul pendant une session. L'`index.html` inclut près d'une quinzaine de scripts synchrones en fin de page, ce qui allonge le temps de parsing et bloque le thread principal.

**Plan d'implémentation (étape en cours) :**
- [ ] **Audit des imports existants** : lister dans `index.html` et `scripts/app.js` tous les `<script>` synchrones pour identifier les modules à convertir.
- [ ] **Conversion d'`app.js` en module ES** : ajouter `type="module"`, vérifier les exports/imports existants et préparer les chargements dynamiques.
- [ ] **Création du manifest des mini-jeux** : définir une structure de données `{ id, importPath, init }` exportée par `app.js` afin de centraliser les `import()`.
- [ ] **Chargement différé dans le hub arcade** : remplacer les instanciations directes par des appels `await import(manifest[id].importPath)` lorsque le joueur choisit un mini-jeu.
- [ ] **Fallback pour navigateurs anciens** : documenter la génération d'un bundle unique via l'outil de build actuel (Android) et l'inclure uniquement si la détection de modules échoue.

**Journal de progression**
- 2024-05-09 — Démarrage de l'étape 1 : définition du plan détaillé ci-dessus pour guider les commits successifs.

**Impact attendu :** réduction du temps de chargement initial et du pic mémoire, puisque seules les ressources nécessaires sont téléchargées et évaluées.

## 2. Mémoriser l'état d'affordabilité des achats

**Constat actuel :** la fonction `updateShopAffordability` reconstruit les textes, états ARIA et classes CSS de chaque bouton d'achat à chaque mise à jour, même si aucune valeur n'a changé. Le recalcul touche potentiellement des dizaines de lignes (titre, description, prix, niveau, attributs ARIA) pour chaque entrée du magasin.

**Proposition :**
- Conserver pour chaque `shopRow` une structure légère (ex : `lastRender`) qui stocke le niveau, le prix formaté, l'état `isReady`, le libellé ARIA, etc.
- Lors des boucles `SHOP_PURCHASE_AMOUNTS.forEach`, comparer les valeurs actuelles aux précédentes et n'écrire dans le DOM que si une différence est détectée.
- Factoriser la construction des libellés (titre, description) pour éviter les appels répétés à `translateOrDefault` si les textes ne changent pas.

**Impact attendu :** moins d'opérations sur le DOM et réduction des reflows lors des ticks du jeu, ce qui aide les appareils mobiles et le mode "eco".

## 3. Couper les animations lourdes en mode performance réduite

**Constat actuel :** la génération du champ d'étoiles (`initStarfield`) crée des dizaines d'éléments animés via CSS quelle que soit la configuration. Sur mobile, ces animations se poursuivent même lorsque le joueur active le mode `eco` ou qu'un `prefers-reduced-motion` est détecté.

**Proposition :**
- Ajouter un observateur du mode de performance (et de la media query `prefers-reduced-motion`) pour désactiver dynamiquement les animations décoratives.
- Concrètement : ne pas appeler `initStarfield` ou vider le conteneur lorsque `performanceModeState.id === 'eco'` ou qu'une préférence de mouvement réduit est active.
- Optionnel : appliquer une classe `is-low-motion` sur `<body>` pour que le CSS masque également d'autres effets (particules critiques, surbrillance du bouton d'atome, etc.).

**Impact attendu :** baisse de l'utilisation CPU/GPU pendant les longues sessions et meilleure accessibilité pour les joueurs sensibles aux animations.

## 4. Planifier les tâches secondaires en arrière-plan

**Constat actuel :** plusieurs mises à jour non critiques (rafraîchissement des listes de trophées, génération du tableau périodique) sont déclenchées immédiatement lors du chargement, en concurrence avec le rendu initial.

**Proposition :**
- Encapsuler ces travaux dans `requestIdleCallback` (avec fallback `setTimeout`) pour laisser le navigateur dessiner l'interface principale avant d'initialiser les sections lourdes.
- Prioriser les éléments interactifs (bouton d'atome, compteur) et repousser le reste si l'`IdleDeadline` reste court.

**Impact attendu :** sensation de démarrage plus fluide, surtout sur appareils à faible cœur.

---

Ces pistes sont compatibles entre elles et peuvent être implémentées progressivement. Elles n'affectent pas la logique de progression du jeu, mais visent à optimiser la réactivité ressentie par les joueurs.
