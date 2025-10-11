# Corpus de positions de test pour l'IA des noirs

Ce fichier complète l'étape 5 de la feuille de route du mini-jeu d'échecs. Il recense trois positions FEN dans lesquelles l'IA noire
est invitée à démontrer ses nouvelles heuristiques (itération approfondie, MVV-LVA, killer heuristic et gestion des finales).

## Comment utiliser le corpus

1. Ouvrir la console développeur dans le mini-jeu et appeler `window.atom2universChessTests` pour retrouver la liste en mémoire.
2. Charger la FEN de votre choix dans l'outil de test manuel (ou directement dans `setupBoardFromFEN`) puis lancer la recherche IA.
3. Vérifier que le coup retourné par l'IA fait partie de `expectedMoves`. En cas de divergence, noter les logs et ajuster les
   paramètres dans `config/config.js` si nécessaire.

## Détail des positions

| ID | FEN | Coup(s) attendu(s) | Objectif |
| --- | --- | --- | --- |
| `king_centralization` | `8/6p1/3k3p/8/8/3K3P/6P1/8 b - - 0 1` | `Kd5` | Valoriser la centralisation du roi noir dans une finale simplifiée. |
| `capture_passed_pawn` | `8/6p1/8/3k3p/3P4/8/6PP/5K2 b - - 0 1` | `Kxd4` | Prioriser l'élimination immédiate d'un pion passé adverse. |
| `push_outside_passed_pawn` | `8/8/6k1/6P1/7p/6K1/8/8 b - - 0 1` | `h3` | Exploiter un pion passé éloigné pour créer une course à la promotion. |

Ces scénarios sont volontairement courts pour être lancés dans la console sans recharger la page. Ils servent de garde-fous afin de
confirmer que les améliorations de l'étape 5 sont opérationnelles.
