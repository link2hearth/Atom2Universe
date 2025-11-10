#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..');
const arcadeScriptsDir = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'scripts', 'arcade');

const games = [
  { id: 'particules', label: 'Particules', script: 'particules.js' },
  { id: 'math', label: 'Math', script: 'math.js' },
  { id: 'gameOfLife', label: 'Game of Life', script: 'game-of-life.js' },
  { id: 'sudoku', label: 'Sudoku', script: 'sudoku.js' },
  { id: 'minesweeper', label: 'Boom!', script: 'minesweeper.js' },
  { id: 'solitaire', label: 'Solitaire', script: 'solitaire.js' },
  { id: 'echecs', label: 'Échecs', script: 'echecs.js' },
  { id: 'othello', label: 'Reversi', script: 'othello.js' },
  { id: 'theLine', label: 'The Line', script: 'the-line.js' },
  { id: 'lightsOut', label: 'All Off', script: 'lights-out.js' },
  { id: 'pipeTap', label: 'PipeTap', script: 'pipetap.js' },
  { id: 'sokoban', label: 'Sokoban', script: 'sokoban.js' },
  { id: 'roulette', label: 'Roulette', script: 'roulette.js' },
  { id: 'blackjack', label: 'Blackjack', script: 'blackjack.js' },
  { id: 'pachinko', label: 'Pachinko', script: 'pachinko.js' },
  { id: 'dice', label: 'Dice', script: 'dice.js' },
  { id: 'holdem', label: 'Hold’em', script: 'holdem.js' },
  { id: 'wave', label: 'Wave', script: 'wave.js' },
  { id: 'quantum2048', label: 'Quantum 2048', script: 'quantum-2048.js' },
  { id: 'balance', label: 'Balance', script: 'balance.js' },
  { id: 'bigger', label: 'Bigger', script: 'bigger.js' },
  { id: 'metaux', label: 'Métaux Match-3', script: 'metaux-match3.js' }
];

const results = games.map(game => {
  const scriptPath = path.join(arcadeScriptsDir, game.script);
  let content = '';
  try {
    content = fs.readFileSync(scriptPath, 'utf8');
  } catch (error) {
    return {
      ...game,
      status: 'missing-script',
      details: `Unable to read ${game.script}: ${error.message}`
    };
  }
  const hasAutosave = content.includes('ArcadeAutosave');
  return {
    ...game,
    status: hasAutosave ? 'has-autosave' : 'missing-autosave',
    scriptPath: path.relative(repoRoot, scriptPath)
  };
});

const missing = results.filter(result => result.status !== 'has-autosave');
const ok = results.filter(result => result.status === 'has-autosave');

const pad = (value, length) => {
  const stringValue = String(value);
  if (stringValue.length >= length) {
    return stringValue;
  }
  return stringValue + ' '.repeat(length - stringValue.length);
};

console.log('Arcade autosave audit');
console.log('======================\n');
console.log('Games with autosave integration:');
ok.forEach(entry => {
  console.log(`  - ${entry.label} (${entry.scriptPath})`);
});

console.log('\nGames missing autosave integration:');
if (missing.length === 0) {
  console.log('  All arcade games reference ArcadeAutosave.');
} else {
  missing.forEach(entry => {
    console.log(`  - ${entry.label} (${entry.scriptPath})`);
  });
}

process.exitCode = missing.length === 0 ? 0 : 1;
