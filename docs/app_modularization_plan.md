# Plan de modularisation de `scripts/app.js`

## Contexte actuel
`scripts/app.js` concentre l'essentiel de la logique du jeu : initialisation des données, gestion du gameplay, mises à jour de l'UI, intégration des mini-jeux et services transverses (musique, sauvegarde, notifications, etc.). Avec plus de 11 000 lignes, il est difficile à faire évoluer, tester et relire.

## Cartographie détaillée *(Étape 1)*

### Zones fonctionnelles observées

- **Initialisation & configuration runtime** : lecture d'`APP_DATA`, normalisation des paramètres de sauvegarde et injection des options de configuration (ex. `initializeApp`, `startApp`, `initUiScaleOption`, `normalizeOfflineBonusConfig`).
- **Gestion d'état du clicker principal** : structures de données `gameState`, `elements`, `upgrades`, boucles d'update (`loop`, `recalcProduction`, `updateUI`), calculs de production active/passive, application des bonus temporaires.
- **Systèmes secondaires** : frénésie, trophées, quêtes quotidiennes, météorites, calculs statistiques, préservation des ressources lors des resets et gestion des récompenses offline.
- **UI & interactions** : binding des boutons, panneaux d'options, animations, rendu des listes (shop, collections, quêtes), manipulation directe du DOM via l'objet `elements` et un ensemble conséquent de fonctions de rendu.
- **Mini-jeux intégrés** : Sudoku, Démineur, Piano/MIDI, Chess, etc. Leur orchestration (déblocage, récompenses, synchronisation avec les bonus) est toujours pilotée par `app.js`.
- **Services transverses** : musique (`musicPlayer`), sonorisation (`playClickSound`), notifications/toasts, timers, import/export de sauvegarde, intégrations réseau et exposition d'utilitaires dans `globalThis`.

### Synthèse par bloc de code

| Bloc principal | Fonctions / objets notables | Cible envisagée |
| --- | --- | --- |
| Chargement des paramètres & constantes (lignes ~1-950) | Normalisation des thèmes, stockage des préférences (langue, sons, polices) | `scripts/core/config/` (fonctions pures) + `scripts/services/storage.js` |
| Gestion des sauvegardes & stats (lignes ~950-2100) | `loadGame`, `saveGame`, parseurs `parseStats`, `createInitial...` | `scripts/core/state/` (structures + parsing) et `scripts/services/persistence.js` |
| Calculs de production & bonus (lignes ~2100-3600) | `gameState`, `recalcProduction`, gestion des multiplicateurs | `scripts/core/production/` |
| Options & préférences UI (lignes ~3120-4080) | `initUiScaleOption`, `initTextFontOption`, toggles audio/critique | `scripts/ui/options/` + `scripts/services/preferences.js` |
| Services audio (lignes ~4030-4700) | IIFE `musicPlayer`, gestion du volume, écoute d'événements utilisateur | `scripts/services/audio/music-player.js` |
| Rendu UI & navigation (lignes ~4700-7600) | `renderShop`, `renderGoals`, `showToast`, binding des boutons de navigation | `scripts/ui/` (un fichier par onglet) |
| Gestion du clicker & frénésie (lignes ~7600-9300) | `handleManualAtomClick`, `startFrenzy`, `updateFrenzyStatus` | `scripts/core/clicker/` + orchestrateur UI |
| Mini-jeux & intégration arcade (lignes ~9300-11000) | Déblocage des mini-jeux, distribution des récompenses, gestion des tickets | `scripts/minigames/` (coordination) + réutilisation de `scripts/arcade/` |
| Bootstrapping (lignes ~11000-11700) | `startApp`, `initializeApp`, écouteur `DOMContentLoaded` | Nouveau `scripts/main.js` (point d'entrée) |

### Structure cible validée *(Étape 1)*

```
scripts/
  main.js                     # Point d'entrée ESM orchestrant l'initialisation.
  core/
    config/
      themes.js
      options.js
      offline-bonus.js
    state/
      index.js               # Construction de `gameState` et fonctions de reset.
      persistence.js         # `loadGame`, `saveGame`, normalisation des sauvegardes.
    production/
      index.js               # Calculs APC/APS, multiplicateurs, frenzy.
    clicker/
      manual-clicks.js
      frenzy.js
    progression/
      trophies.js
      goals.js
  services/
    storage.js               # Accès localStorage, clés, helpers de persistance.
    audio/
      music-player.js
      sound-effects.js
    scheduler.js             # Timers, animation frame loop, gestion offline.
    notifications.js         # Toasts et messages utilisateurs.
    i18n.js                  # Passerelle vers `globalThis.i18n`.
  ui/
    layout.js                # Navigation générale, visibilité des pages.
    options/
      index.js
    shop/
      index.js
    fusion/
      index.js
    collections/
      index.js
    stats/
      index.js
  minigames/
    index.js                 # Orchestration des mini-jeux et récompenses.
    arcade/
      (réexport depuis `scripts/arcade/`)
```

Cette structure réutilise le dossier `scripts/arcade/` existant pour les implémentations métier des mini-jeux, tout en déléguant à `minigames/index.js` la synchronisation avec le cœur du clicker (tickets, bonus, trophées).

### Points d'entrée et dépendances partagées *(Étape 1)*

- **État global** : `gameState`, exposé via `window.atom2universGameState`, regroupe ressources, bonus et statistiques. Nécessite une API claire pour lecture/écriture depuis l'UI et les mini-jeux.
- **Données de configuration** : `APP_DATA`, `GLOBAL_CONFIG`, `LayeredNumber`. À centraliser dans `core/config` et reconsidérer l'accès direct depuis les mini-jeux.
- **Objets DOM** : l'objet `elements` rassemble >150 références `document.getElementById/querySelector`. Il faudra les encapsuler par section d'UI pour éviter le couplage direct.
- **Services audio** : `musicPlayer` (IIFE retournant un objet), `playClickSound`, `applyCritAtomVisualsDisabled`. Doivent migrer dans `services/audio` avec une API unique.
- **I18n** : multiples appels à `globalThis.i18n`, `globalThis.t` et écouteurs `i18n:languagechange`. Un module `services/i18n.js` exposera `t`, `formatNumber`, `onLanguageChanged`.
- **Timers & boucles** : `requestAnimationFrame(loop)`, `setInterval` pour autosaves et spawn de tickets. À encapsuler dans `services/scheduler` pour limiter les effets de bord.
- **Interop arcade** : fonctions comme `handleArcadeGameReward`, `triggerArcadeTicket`, `startSudokuBonusTimer` utilisent à la fois `gameState`, `elements` et `APP_DATA`. Elles seront orchestrées depuis `minigames/index.js`.
- **Expositions globales** : `handleManualAtomClick`, `isManualClickContextActive`, `getActiveTicketLayerElement` sont attachées à `globalThis`. À regrouper dans un module qui contrôle explicitement ce qui reste global.

### Points d'attention identifiés *(Étape 1)*

1. **Accès direct au DOM** : la plupart des fonctions manipulent directement `elements.<...>`. Il faudra introduire des fonctions d'UI qui reçoivent les dépendances (ex. `renderShop({ state, i18n })`) pour réduire l'usage d'objets globaux.
2. **Mutations partagées de `gameState`** : plusieurs sous-systèmes modifient les mêmes propriétés (ex. frénésie, bonus offline, mini-jeux) sans médiation. Prévoir un module `core/state` responsable des setters et d'un bus d'événements léger pour notifier l'UI.
3. **Gestion des préférences** : la lecture/écriture dans `localStorage` est dispersée. Centraliser les clés et ajouter des helpers pour simplifier les tests.
4. **Synchronisation i18n** : `app.js` enregistre des écouteurs `i18n:languagechange` partout. Un service dédié devra relayer l'événement aux modules intéressés afin d'éviter les fuites de listeners.
5. **Orchestration audio** : `musicPlayer` et les sons de clic partagent des responsabilités avec les options UI. L'extraction devra veiller à ne pas casser la restauration des préférences (`musicEnabled`, `musicVolume`).
6. **Interop mini-jeux** : les récompenses utilisent `APP_DATA.arcadeRewards` et les timers de bonus offline. Lors de l'extraction, s'assurer que les promesses/résultats des mini-jeux passent par une API unique (ex. `minigames.award('sudoku', result)`).
7. **Compatibilité progressive** : pendant l'étape 2, `app.js` devra continuer d'exposer les mêmes fonctions globales afin de ne pas casser `index.html`. Prévoir une phase de pont (wrapper) lorsque `main.js` sera introduit.

## Découpage proposé (3 étapes)

### Étape 1 — Cartographier et préparer les frontières de modules ✅ *(terminée)*
- [x] Documenter les grandes zones fonctionnelles du fichier (cf. section « Cartographie détaillée »).
- [x] Dégager une structure cible de dossiers pour accueillir les modules (`scripts/core`, `scripts/ui`, `scripts/minigames`, `scripts/services`).
- [x] Identifier les points d'entrée partagés (ex. objet `game`, bus d'événements, helpers utilitaires) pour anticiper leurs futures exports.

### Étape 2 — Extraire le noyau et les services partagés *(en cours)*
- [x] Introduire un point d'entrée explicite (ex. `scripts/main.js`) chargé par `index.html` en `type="module"`.
- [x] Déplacer dans `scripts/core/` la gestion d'état principale (structures, calculs de production, persistance) et fournir des fonctions/objets exportés.
- [x] Isoler dans `scripts/services/` les utilitaires transverses (sons, musique, horloges, sauvegarde) en minimisant les dépendances circulaires. → premier service extrait : `services/storage` pour la persistance locale des préférences et sauvegardes.
- [x] Mettre en place un index de modules pour exposer une API claire à l'UI et aux mini-jeux.

#### Livrables intermédiaires (Étape 2)
- Ajout d'un entrypoint ESM `scripts/main.js` déclenchant `initializeApp` et rendant `index.html` prêt pour les importations modulaires.
- Création du dossier `scripts/services/` et extraction d'un service `storage` centralisant les accès `localStorage` (`readString`, `writeString`, `readJSON`, `writeJSON`, `remove`).
- Ajout du module `scripts/services/audio/music-player.js` pour encapsuler la découverte des pistes, la lecture et la gestion du volume, réutilisé depuis `app.js`.
- Adaptation de `scripts/app.js` pour utiliser ce service et exposition explicite des fonctions `initializeApp`, `startApp`, `loadGame`, `saveGame` et `gameState`.
- Création du module `scripts/core/state/index.js` fournissant l'initialisation du `gameState`, les helpers de statistiques/production et l'API DevKit, réexporté via `scripts/core/index.js`.
- Reroutage des dépendances de `scripts/app.js` vers cette API (`initializeCoreState`, `ensureApsCritState`, helpers d'arcade) en retirant les définitions locales redondantes.

### Étape 3 — Segmenter l'UI et les mini-jeux *(en cours)*
- [x] Extraire l'onglet Options dans `scripts/ui/options/index.js` (échelle UI, polices et préférences associées).
- [ ] Créer un module par onglet/mini-jeu dans `scripts/ui/` ou `scripts/minigames/` avec leurs ressources spécifiques (Shop, Fusion, Arcade, etc.).
- [x] Shop : extraire le rendu de la boutique et ses interactions dans `scripts/ui/shop/index.js`.
- [x] Layout : isoler la navigation principale et le basculement des pages dans `scripts/ui/layout.js`.
- [x] Collections : déplacer le tableau périodique, les fiches éléments/familles et la progression de collection dans `scripts/ui/collections/index.js`.
- [x] Fusion : encapsuler le pilotage du registre DOM des fusions dans `scripts/ui/fusion/index.js` en s'appuyant sur les helpers déjà fournis par `scripts/arcade/gacha.js`.
- [x] Goals : isoler l'affichage des trophées, la mise à jour des paliers et la synchronisation des cartes dans `scripts/ui/goals/index.js`.
- [x] Info : regrouper la visibilité des cartes bonus et du module succès dans `scripts/ui/info/index.js`, tout en conservant le rendu détaillé piloté par `scripts/modules/info.js`.
- [x] Arcade : extraire le portail Gacha, l'affichage des tickets et le mini-jeu Métaux dans `scripts/ui/arcade/index.js`.
- [ ] Faire consommer ces modules l'API `core`/`services` et enregistrer leurs gestionnaires d'événements DOM au montage.
  - [x] Fusion : `initializeFusionUI` délègue aux fonctions existantes de `scripts/arcade/gacha.js` tout en fournissant un contrôleur côté ESM.
  - [x] Goals : `initializeGoalsUI` orchestre la liste des trophées via l'API d'état et les helpers d'internationalisation existants.
  - [x] Info : `initializeInfoUI` contrôle les états de verrouillage, relaie le rendu des panneaux détaillés via l'API historique et expose les garde-fous nécessaires au DevKit.
  - [x] Arcade : `initializeArcadeUI` centralise le hub arcade, relaie les tickets Gacha/Mach3 et conserve les ponts globaux attendus par les scripts legacy.
  - [ ] Réduire progressivement la taille de `app.js` jusqu'à ne conserver qu'un orchestrateur léger, ou remplacer complètement `app.js` par des modules spécialisés.
  - [ ] Ajuster le build (ou ajouter un bundler léger si nécessaire) afin de produire une version concaténée pour la production.

## Livrables de l'étape 1
- ✅ Cartographie détaillée des responsabilités de `app.js` (section « Cartographie détaillée » et tableau de synthèse).
- ✅ Structure cible validée pour la suite de la migration (section « Structure cible »).
- ✅ Liste des points d'attention et dépendances critiques (section « Points d'entrée et dépendances » + « Points d'attention »).

Les étapes suivantes pourront ensuite migrer progressivement le code en respectant cette cartographie.
