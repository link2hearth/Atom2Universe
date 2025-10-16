# Plan de migration vers une page HTML dédiée aux jeux tactiles

## Vue d'ensemble
Ce document décrit la stratégie pour isoler les mini-jeux sensibles aux interactions tactiles (Particules, Métaux, Balance, Game of Life) dans une page dédiée afin d'éliminer les conflits de gestuelle présents sur la page principale.

## Étapes prévues
1. **Cartographier la situation actuelle**
   - Confirmer que les sections Particules, Métaux, Balance et Game of Life vivent toutes dans `index.html`, avec une navigation centralisée et un chargement commun de tous les scripts arcade en bas de page.
   - Identifier les zones de friction tactile actuelles : `app.js` ajoute des écouteurs globaux `touchstart/touchend` sur `document` et `window`, qui s’appliquent à tout le DOM et peuvent interférer entre mini-jeux.
   - Noter les comportements propres à chaque jeu : Particules gère ses pointeurs sur le `<canvas>`, Métaux attache des événements pointer aux tuiles, Balance capture les pointeurs au niveau du document pendant un drag & drop, et Game of Life écoute également les pointeurs sur `window` pour le pinch/drag.
2. **Créer une nouvelle page hôte “arcade-touch.html”**
   - Partir d’un squelette minimal : reprendre l’en-tête `<head>` d’`index.html` (métadonnées, feuilles de style, i18n) et ne conserver dans le `<body>` que les sections nécessaires (Particules, Métaux, Balance, Game of Life) ainsi que les overlays/structures strictement requises par ces jeux.
   - Charger uniquement les scripts indispensables (config, i18n, ressources partagées, et les quatre mini-jeux) afin de limiter les effets de bord et le temps de parsing.
   - Ajouter un nouvel orchestrateur `scripts/arcade-touch.js` (ou équivalent) qui assure l’initialisation des modules déplacés sans importer `scripts/app.js` pour éviter de réactiver les écouteurs globaux problématiques.
3. **Isoler la gestion des interactions tactiles**
   - Déporter la logique de suivi tactile globale d’`app.js` dans une classe ou un module réutilisable que l’on peut instancier séparément sur la nouvelle page, en limitant sa portée aux conteneurs présents dans “arcade-touch.html”.
   - Vérifier pour chaque jeu si des événements restent attachés au `window` et, si besoin, les encapsuler derrière un gestionnaire optionnel activé uniquement dans le nouveau contexte (ex. injection d’un `eventTarget` spécifique au lieu de `window`).
   - Profiter de la duplication pour introduire une configuration dédiée (par ex. `config/touch-mode.js`) qui activera ou non les comportements de lock du scroll selon la page.
4. **Adapter les scripts des mini-jeux**
   - Vérifier que chaque module peut détecter dynamiquement sa présence dans le DOM (sélecteurs basés sur `document.getElementById`) et prévoir un mécanisme d’init/dispose pour ne pas dépendre d’un orchestrateur global comme `ensureBalanceGame()` dans `app.js`.
   - Pour Balance et Métaux, s’assurer que les captures de pointeurs et `pointermove` globaux peuvent être relâchés proprement lors des changements de page ou d’onglet dans le nouvel environnement.
   - Pour Game of Life, encapsuler les écoutes sur `window` (pointer/keyboard/resize) dans des méthodes exportées pour pouvoir les réinitialiser quand l’utilisateur quitte la page dédiée.
5. **Mettre en place la cohabitation des deux pages**
   - Ajouter, dans `index.html`, un bouton ou un lien menant vers “arcade-touch.html” pour les plateformes concernées (en respectant l’i18n existant).
   - Documenter dans le code (commentaires ou README) la différence de périmètre entre les deux pages afin d’éviter de futures régressions.
6. **Plan de tests et de mise en production**
   - Tester séparément “arcade-touch.html” sur mobile/tablette : drag, pinch, scroll pour chacun des quatre jeux, et comparer avec l’ancienne page.
   - Vérifier que l’index historique continue de fonctionner (aucune régression dans le routage `showPage()` ni dans les autres mini-jeux toujours chargés sur la page principale).
   - Prévoir un plan de rollback (suppression du lien de redirection) en cas de problème.

## Progrès de l'étape 1 – Cartographie

### Structure HTML actuelle
- **Sections confirmées** : dans `app/src/main/assets/index.html`, les `section` des jeux ciblés vivent sous `#pageContainer` avec `data-page-group="arcade"`. Les identifiants sont `#arcade` (Particules), `#gameOfLife`, `#balance` et `#metaux`. Chaque bloc contient ses contrôles dédiés (`#arcadeOverlay`, `#gameOfLifeMenu`, `#balanceDragLayer`, `#metauxBoard`, etc.) qui devront être transférés tels quels dans la nouvelle page.
- **Chargement des scripts** : la section `<script>` située en bas du fichier (lignes ~3550) charge tous les modules arcade (`scripts/arcade/*.js`) y compris les quatre jeux concernés, de pair avec des modules voisins (Wave, Math, Quantum 2048, etc.). Cela confirme la dépendance vis-à-vis de l'orchestrateur global `scripts/app.js` et de son état partagé.
- **Navigation/activation** : `scripts/app.js` pilote l’affichage via `showPage(pageId)` qui active la section demandée et instancie certains jeux par `ensureBalanceGame()` ou `ensureWaveGame()`. Les sections restent présentes dans le DOM, donc leurs écouteurs restent actifs même lorsqu’elles sont masquées.

### Écouteurs globaux et scroll
- `scripts/app.js` attache au démarrage :
  - `document.addEventListener('touchstart'/'touchend'/'touchcancel', …)` pour remplir `registerActiveTouches()` et verrouiller ou libérer le scroll.
  - `window.addEventListener('touchstart'/'touchend'/'touchcancel', …)` et, si disponible, `window.addEventListener('pointerdown'/'pointerup'/'pointercancel', …)` afin de suivre tous les contacts tactiles (`activePointerTouchIds`).
  - `applyBodyScrollBehavior()` impose `touch-action: none` et `overscroll-behavior: none` sur `<body>` tant qu’un contact tactile est actif, avant de rétablir `touch-scroll-force` ou le comportement par défaut.
  - Des écouteurs supplémentaires sur `window` (`focus`, `atom2univers:scroll-reset`) recalculent le comportement de scroll actif.
- Ce dispositif agit au niveau du document entier, ce qui provoque des conflits dès qu’un mini-jeu tente de gérer ses propres drags, pinch ou scroll.

### Cartographie par mini-jeu
- **Particules (`scripts/arcade/particules.js`)**
  - Le `<canvas id="arcadeGameCanvas">` reçoit `pointerdown`/`pointermove`/`pointerup` et utilise `setPointerCapture`. Le module écoute également `window.addEventListener('resize', …)` et `window.addEventListener('i18n:languagechange', …)` pour recalculer le rendu.
  - L’instance conserve un état global (`globalThis.GAME_CONFIG.arcade.particules`) partagé avec `app.js` et attend que le canvas existe déjà dans le DOM principal.
- **Métaux Match-3 (`scripts/arcade/metaux-match3.js`)**
  - `initializeBoard()` ajoute `pointerdown`/`pointermove`/`pointerup`/`pointercancel`/`contextmenu` sur chaque tuile et capture le pointeur durant un drag (`event.target.setPointerCapture`).
  - Le module observe `window.addEventListener('resize', this.boundOrientationChange)` et `window.matchMedia('(orientation: portrait)')` pour adapter la grille ; aucune écoute directe sur `document`, mais l'ensemble reste actif tant que les tuiles sont montées dans la page principale.
- **Balance (`scripts/arcade/balance.js`)**
  - `pageElement.addEventListener('pointerdown', this.handlePointerDown)` déclenche les drags. Dès qu’un cube est saisi (`startDrag()`), le jeu attache `document.addEventListener('pointermove'/'pointerup'/'pointercancel', …)` pour suivre le curseur partout, relâchés via `stopPointerListeners()`.
  - Surveille également `window.addEventListener('resize', …)` et `globalThis.addEventListener('i18n:languagechange', …)` pour recalculer le plateau. Ces écouteurs globaux sont ceux qui interfèrent le plus avec la logique tactile centralisée.
- **Game of Life (`scripts/arcade/game-of-life.js`)**
  - Le canvas principal écoute `pointerdown`/`pointermove`, mais le module complète avec `window.addEventListener('pointerup'/'pointercancel', event => this.handlePointerUp(event))` pour gérer les sorties de canvas.
  - Le menu latéral draggable (`#gameOfLifeMenuHeader`) ajoute aussi des écouteurs sur `window` pour suivre le déplacement, et le jeu écoute `document.addEventListener('visibilitychange', …)` pour suspendre la simulation hors focus.
  - D’autres contrôles (`input`, `click`, `keydown`) restent actifs même lorsque la section est cachée, car la page principale ne détruit jamais l’instance.

### Points sensibles identifiés
- Les jeux partagent l’état global de `scripts/app.js` (`ensureXGame()`, `document.body.dataset.pageGroup`, classes `touch-scroll-lock`, etc.), qu’il faudra reproduire ou isoler dans la nouvelle page pour conserver le comportement attendu.
- Les écouteurs globaux (notamment ceux de Balance et Game of Life sur `document`/`window`) risquent de déclencher des gestuelles concurrentes avec la logique de verrouillage du scroll. Lors de la migration, il faudra prévoir des cibles d’événements dédiées ou un wrapper d’événements pour limiter leur portée.
- Les ajustements responsives (orientation, resize, i18n) reposent actuellement sur `window`/`document`. La nouvelle page devra réinjecter ces signaux ou fournir des équivalents afin d’éviter les régressions visuelles ou d’accessibilité.

## Progrès de l'étape 2 – Nouvelle page hôte

- Créé `app/src/main/assets/arcade-touch.html` avec un en-tête minimal, une navigation dédiée et les sections complètes des mini-jeux Particules, Game of Life, Balance et Métaux isolées de la page principale.
- Ajouté `app/src/main/assets/scripts/arcade-touch.js` pour initialiser les jeux nécessaires, piloter la navigation et charger la langue par défaut sans dépendre de `scripts/app.js`.
- Conservé les attributs `data-i18n` et l’ordre de chargement des ressources partagées (config, i18n, scripts des mini-jeux) afin de garantir une parité fonctionnelle avant l’isolation des interactions tactiles.

## Progrès de l'étape 3 – Isolation des interactions tactiles

- Introduit `app/src/main/assets/config/touch-mode.js` pour centraliser les paramètres du mode tactile (verrouillage de scroll par défaut et délai de relâche) et les exposer via `globalThis.ATOM2UNIVERS_CONFIG.touchMode`.
- Créé `app/src/main/assets/scripts/modules/touch-interaction-manager.js`, un gestionnaire instanciable qui suit les contacts tactiles/pointer sur la page dédiée, applique les classes `touch-scroll-lock`/`touch-scroll-force` selon l’état et limite les écoutes à la zone `#touchArcadeContainer`.
- Intégré ce gestionnaire dans `scripts/arcade-touch.js` afin d’adapter dynamiquement le comportement de scroll à la section affichée et d’éviter toute dépendance vis-à-vis des écouteurs globaux de `scripts/app.js`.

## Progrès de l'étape 4 – Adaptation des scripts des mini-jeux

- Ajouté un hub tactile dédié dans `arcade-touch.html` avec les cartes des quatre mini-jeux migrés afin de supprimer toute dépendance visuelle aux bannières de la page clicker.
- Intégré un bouton de sortie discret pour chaque section (`touch-arcade__close`) en haut à droite, relié à un retour vers l’arcade principale, conformément à la nouvelle contrainte métier.
- Fait évoluer `scripts/arcade-touch.js` pour instancier les mini-jeux à la demande, appeler leurs méthodes `onEnter`/`onLeave` lors des changements de section et déconnecter proprement leurs écouteurs globaux.
- Ajouté une passerelle d’événement (`atom2univers:game-of-life-ready`) depuis `scripts/arcade/game-of-life.js` pour synchroniser l’initialisation du jeu de la vie dans le contexte autonome.
- Centralisé la navigation (nav, cartes, bouton de sortie) via un seul gestionnaire `data-target` et importé les styles dédiés dans `styles/modules/arcade-touch.css`.

## Progrès de l'étape 5 – Cohabitation des deux pages

- Ajouté, dans `index.html`, un bouton de navigation dédié (`#navTouchArcadeButton`) et une carte d’accueil `arcade-hub-card--touch` pointant vers `arcade-touch.html`, avec les libellés i18n FR/EN pour la nouvelle destination tactile.
- Relié ces déclencheurs à une routine de bascule (`openTouchArcadeHub()` dans `scripts/app.js`) qui force une sauvegarde immédiate, mémorise l’horodatage de départ et redirige vers la page tactile afin de préparer le calcul des APS hors ligne au retour.
- Synchronisé l’état de déverrouillage de l’arcade entre les deux commandes (nav + carte) dans `updateBrandPortalState()` pour qu’elles restent masquées tant que la fonctionnalité “Arcade” n’est pas débloquée.
- Stylisé la carte tactile via `arcade-hub-card--touch` pour la différencier visuellement et signaler la bascule hors de la page principale.

## Contraintes supplémentaires

- La navigation vers la page tactile déclenche désormais systématiquement une sauvegarde et l’horodatage de départ pour garantir le calcul des APS hors ligne à la réouverture du clicker principal.
