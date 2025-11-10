# Migration des configurations arcade

Ce document recense les mini-jeux d'arcade dont les paramètres sont encore définis dans `config/config.js` et qui peuvent être migrés vers un fichier JSON dédié situé dans `config/arcade/`.
L'objectif de cette migration est d'alléger `config/config.js` tout en conservant un accès simple aux réglages via un JSON spécifique à chaque jeu.

## Jeux à traiter

- [x] Blackjack – configuration déportée dans `config/arcade/blackjack.json`
- [x] Roulette
- [x] Pachinko
- [x] Hold'em
- [x] Dice
- [x] Échecs
- [x] Balance
- [x] Math
- [x] Solitaire
- [x] Particules
- [x] Sudoku
- [x] Quantum 2048
- [x] Boom! (ancien Démineur)
- [x] Métaux – configuration déportée dans `config/arcade/metaux.json`

## Modules à migrer ensuite

- **Recettes de fusion** – Le tableau `fusions` liste l'intégralité des recettes et des prérequis. Les extraire dans `config/systems/fusions.json` permettrait d'étendre le contenu sans alourdir `config.js`.
- **Bonus de collection** – Les sections `elementBonuses`, `elementFamilies` et `elements` rassemblent de nombreuses données statiques. Les éclater dans des fichiers dédiés (`config/collection/bonuses.json`, `config/collection/families.json`, `config/collection/elements.json`) améliorerait la lisibilité et le suivi des traductions.
- **Étoile à tickets** – Les paramètres `ticketStar` (timings, vitesse, récompenses) restent définis en dur. Un fichier `config/systems/ticket-star.json` clarifierait les ajustements de cadence ou de récompenses.

## Systèmes déjà externalisés

- **Gacha** – Paramètres déplacés dans `config/systems/gacha.json` (poids de rareté, coûts, paliers) avec les visuels listés séparément dans `config/gacha/bonus-images.json`.
