const ARCADE_GAME_IDS = Object.freeze([
  'particules',
  'metaux',
  'wave',
  'starsWar',
  'jumpingCat',
  'reflex',
  'quantum2048',
  'bigger',
  'math',
  'theLine',
  'lightsOut',
  'link',
  'starBridges',
  'pipeTap',
  'colorStack',
  'motocross',
  'hex',
  'twins',
  'sokoban',
  'taquin',
  'balance',
  'sudoku',
  'minesweeper',
  'solitaire',
  'holdem',
  'blackjack',
  'echecs'
]);

function createInitialArcadeProgress() {
  const entries = {};
  ARCADE_GAME_IDS.forEach(id => {
    entries[id] = null;
  });
  return {
    version: 1,
    entries
  };
}
