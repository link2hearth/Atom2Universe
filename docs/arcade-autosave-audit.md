# Arcade Autosave Audit

This audit uses the helper script [`tools/checkArcadeAutosave.js`](../tools/checkArcadeAutosave.js) to verify that each arcade mini-game references the shared `ArcadeAutosave` API. The following table reflects the current state of autosave integration.

| Game | Script | Autosave Integration |
| ---- | ------ | -------------------- |
| Particules | `app/src/main/assets/scripts/arcade/particules.js` | ✅
| Math | `app/src/main/assets/scripts/arcade/math.js` | ✅
| Game of Life | `app/src/main/assets/scripts/arcade/game-of-life.js` | ✅
| Sudoku | `app/src/main/assets/scripts/arcade/sudoku.js` | ✅
| Minesweeper | `app/src/main/assets/scripts/arcade/minesweeper.js` | ✅
| Solitaire | `app/src/main/assets/scripts/arcade/solitaire.js` | ✅
| Échecs | `app/src/main/assets/scripts/arcade/echecs.js` | ✅
| Othello | `app/src/main/assets/scripts/arcade/othello.js` | ✅
| The Line | `app/src/main/assets/scripts/arcade/the-line.js` | ✅
| Lights Out | `app/src/main/assets/scripts/arcade/lights-out.js` | ✅
| PipeTap | `app/src/main/assets/scripts/arcade/pipetap.js` | ✅
| Sokoban | `app/src/main/assets/scripts/arcade/sokoban.js` | ✅
| Roulette | `app/src/main/assets/scripts/arcade/roulette.js` | ❌
| Blackjack | `app/src/main/assets/scripts/arcade/blackjack.js` | ❌
| Pachinko | `app/src/main/assets/scripts/arcade/pachinko.js` | ❌
| Dice | `app/src/main/assets/scripts/arcade/dice.js` | ✅
| Hold’em | `app/src/main/assets/scripts/arcade/holdem.js` | ✅
| Wave | `app/src/main/assets/scripts/arcade/wave.js` | ✅
| Quantum 2048 | `app/src/main/assets/scripts/arcade/quantum-2048.js` | ✅
| Balance | `app/src/main/assets/scripts/arcade/balance.js` | ❌
| Bigger | `app/src/main/assets/scripts/arcade/bigger.js` | ✅
| Métaux Match-3 | `app/src/main/assets/scripts/arcade/metaux-match3.js` | ✅

The audit identifies games where the autosave integration still needs to be implemented (`❌`).
