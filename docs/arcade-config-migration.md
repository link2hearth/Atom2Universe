# Migration des configurations arcade

Ce document recense les mini-jeux d'arcade dont les paramètres sont encore définis dans `config/config.js` et qui peuvent être migrés vers un fichier JSON dédié situé dans `config/arcade/`.
L'objectif de cette migration est d'alléger `config/config.js` tout en conservant un accès simple aux réglages via un JSON spécifique à chaque jeu.

## Jeux à traiter

- [x] Blackjack – configuration déportée dans `config/arcade/blackjack.json`
- [x] Roulette
- [x] Pachinko
- [x] Hold'em
- [x] Dice
- [ ] Échecs
- [x] Balance
- [x] Math
- [ ] Solitaire
- [x] Particules
- [ ] Sudoku
- [ ] Quantum 2048
- [ ] Démineur
