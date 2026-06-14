# Jeux du Game Hub — Récompenses en neutrinos

> Inventaire basé sur `GamesActivity.getDefaultTiles()` et les appels à
> `NeutrinoRepository` dans chaque jeu. Les neutrinos sont la monnaie partagée
> (`neutrino_prefs`) dépensée notamment dans le Clicker.
>
> **Source unique de vérité** : tous les montants vivent dans
> `crypto/clicker/NeutrinoRewards.kt`. Les jeux y lisent leur récompense et la
> fenêtre d'info du shop du clicker (icône ⓘ sur la ligne « Neutrinos ») génère
> son résumé via `NeutrinoRewards.summary()`. Modifier une valeur dans ce fichier
> met à jour le jeu **et** l'affichage, sans toucher au moindre string.

## ✅ Jeux qui rapportent des neutrinos

| Jeu | Conditions et récompenses |
|-----|---------------------------|
| **Chess** (échecs) | Victoire du joueur contre l'IA. **TRAINING : 10**, **STANDARD : 20**, **EXPERT : 50**. Niveaux sans IA : 0. |
| **Draughts** (dames) | Victoire du joueur contre l'IA. **TRAINING : 10**, **STANDARD : 20**, **EXPERT : 50**. Niveaux sans IA : 0. |
| **Othello** | **15** par victoire, **mode Solo uniquement** (victoire du joueur humain = blanc, contre l'IA). Mode Duo : aucune récompense (anti-farm). |
| **Solitaire** | **20** à chaque partie gagnée (montant fixe). |
| **Memory** | À chaque partie gagnée, selon la difficulté : **EASY : +1**, **NORMAL : +2**, **MEDIUM : +3**, **PRO : +4**, **HARD : +5**. |
| **Blackjack** | Jeu de mise (la balance neutrino *est* la cave). Gain net par main : **WIN = mise**, **Blackjack (WIN_BJ) = mise × 1,5**, **LOSE = −mise**, PUSH = 0. Mode « gratuit » (mise 0) : aucun gain/perte. |
| **Roulette** | Jeu de mise (la balance neutrino *est* la cave). Mises possibles : 1, 2, 5, 10, 25. **Gain = mise × multiplicateur** des symboles alignés ; perte de la mise sinon. |
| **2048** | **20** lorsque la cible est atteinte (victoire). |
| **Sudoku** | À la résolution, selon la difficulté : **EASY : 5**, **MEDIUM : 10**, **HARD : 20**. |
| **ColorStack** | À la victoire, selon la difficulté : **EASY : 1**, **MEDIUM : 3**, **HARD : 7**. |
| **PipeTap** | À la victoire, selon la difficulté `(ordinal + 1) × 2` : **EASY : 2**, **MEDIUM : 4**, **HARD : 6**, **EXPERT : 8**, **MASTER : 10**. |
| **Circles** | À la victoire, selon la difficulté : **EASY : 1**, **MEDIUM : 2**, **HARD : 3** (ou **4** si ≥ 6 anneaux). |
| **StarBridges** | À la victoire (grille aléatoire uniquement), selon la taille : **6×6 : 5**, **7×7 : 10**, **autres : 15**. |
| **The Line** | À chaque niveau complété, selon la difficulté : **EASY : 1**, **MEDIUM : 2**, **HARD : 3**. |
| **Minesweeper** | À la victoire, selon la difficulté `(ordinal + 1) × 5` : **EASY : 5**, **NORMAL : 10**, **MEDIUM : 15**, **HARD : 20**. |
| **Link** | À la victoire : **base difficulté** (EASY 2 / MEDIUM 4 / HARD 6) **× multiplicateur jumelles** (FEW ×1 / NORMAL ×2 / MANY ×3). Ex. : EASY+FEW = 2, HARD+MANY = 18. |
| **Hex Runner** | **1 neutrino par tranche de 15 secondes** de jeu (récompensé en fin de partie). |
| **Match 3** | **1 neutrino par tranche de 15 secondes** de jeu (récompensé en fin de partie). |
| **Motocross** | **1 neutrino par tranche de 500 m** parcourus (récompensé au game over, distance de la course en cours). |
| **Escape the Labyrinth** | À la sortie atteinte, selon la difficulté : **EASY : 2**, **MEDIUM : 5**, **HARD : 10**. **×2 si parcours parfait** (tours ≤ optimal). |

### Cas particulier — Clicker
Le **Clicker** (`MainClickerActivity`, première tuile du hub) n'attribue pas de
récompense « par victoire » : c'est la **source principale** des neutrinos. On en
obtient en convertissant des tokens d'éléments (1:1 via `buyElementsToNeutrinos`)
et on les y dépense (échanges APC↔APS, etc.). Il est donc lié aux neutrinos, mais
selon une mécanique d'économie continue, pas un palier de récompense.

## ❌ Jeux qui ne rapportent pas de neutrinos

- **Quiz**
- **Survivor**
- **Roguelike**
- **Particules** (casse-briques)
- **Stars War** (shmup)
- **Flappy Cat**
- **Reflex**
- **Wave Surf**
- **Caves** (Cave World)
- **Bigger** (Suika)
- **Clicker Stats** (écran de statistiques, pas un jeu)
