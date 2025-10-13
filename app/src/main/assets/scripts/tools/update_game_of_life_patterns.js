#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..', '..');
const patternsPath = path.join(repoRoot, 'resources', 'patterns', 'game-of-life.json');
const enPath = path.join(repoRoot, 'scripts', 'i18n', 'en.json');
const frPath = path.join(repoRoot, 'scripts', 'i18n', 'fr.json');

function asciiToCells(asciiRows) {
  const cells = [];
  asciiRows.forEach((row, y) => {
    row.split('').forEach((char, x) => {
      if (char === 'O') {
        cells.push([y, x]);
      }
    });
  });
  return cells;
}

function normalizeCells(cells) {
  if (cells.length === 0) {
    return [];
  }
  let minX = Infinity;
  let minY = Infinity;
  cells.forEach(([y, x]) => {
    if (x < minX) minX = x;
    if (y < minY) minY = y;
  });
  return cells.map(([y, x]) => [y - minY, x - minX]).sort((a, b) => (a[0] - b[0]) || (a[1] - b[1]));
}

function buildGridFromCells(cells) {
  if (cells.length === 0) {
    return ['.'];
  }
  let maxX = 0;
  let maxY = 0;
  cells.forEach(([y, x]) => {
    if (x > maxX) maxX = x;
    if (y > maxY) maxY = y;
  });
  const grid = Array.from({ length: maxY + 1 }, () => Array.from({ length: maxX + 1 }, () => '.'));
  cells.forEach(([y, x]) => {
    grid[y][x] = 'O';
  });
  return grid.map(row => row.join(''));
}
const baseShapes = {
  block: asciiToCells([
    'OO',
    'OO'
  ]),
  beehive: asciiToCells([
    '.OO.',
    'O..O',
    '.OO.'
  ]),
  loaf: asciiToCells([
    '.OO.',
    'O..O',
    '.O.O',
    '..O.'
  ]),
  boat: asciiToCells([
    'OO.',
    'O.O',
    '.O.'
  ]),
  tub: asciiToCells([
    '.O.',
    'O.O',
    '.O.'
  ]),
  ship: asciiToCells([
    'OO.',
    'O.O',
    '.OO'
  ]),
  longBoat: asciiToCells([
    'OO..',
    'O.O.',
    '.O.O',
    '..O.'
  ])
};

const baseVariants = {
  spurBoat: asciiToCells([
    '..OO',
    '...O',
    'OOO.',
    'O...'
  ]),
  cornerBoat: asciiToCells([
    '...O',
    '.OOO',
    'O...',
    'OO..'
  ]),
  boatTie: asciiToCells([
    'OO..',
    'O.O.',
    '.O.O',
    '..O.'
  ]),
  cappedBoat: asciiToCells([
    '.OO.',
    'O..O',
    'O.O.',
    '.O..'
  ]),
  mirroredHook: asciiToCells([
    '..OO',
    '.O.O',
    'O.O.',
    '.O..'
  ]),
  fishhook: asciiToCells([
    '..OO',
    '.O.O',
    '.O..',
    'OO..'
  ]),
  tailBoat: asciiToCells([
    '..OO',
    '..O.',
    'O.O.',
    'OO..'
  ]),
  offsetBoat: asciiToCells([
    '..O.',
    '.O.O',
    'O.O.',
    'OO..'
  ]),
  twistedBoat: asciiToCells([
    '.O..',
    'O.O.',
    'O..O',
    '.OO.'
  ]),
  diagonalLongBoat: asciiToCells([
    '..O.',
    '.O.O',
    'O..O',
    '.OO.'
  ]),
  shiftedBoat: asciiToCells([
    'OO..',
    'O.O.',
    '..O.',
    '..OO'
  ]),
  skewedLongBoat: asciiToCells([
    'OO..',
    '.O..',
    '.O.O',
    '..OO'
  ]),
  layeredBoat: asciiToCells([
    '.O..',
    'O.O.',
    '.O.O',
    '..OO'
  ])
};

function placeComponents(def) {
  const cells = [];
  def.components.forEach(component => {
    const base = baseShapes[component.shape];
    if (!base) {
      throw new Error(`Unknown base shape: ${component.shape}`);
    }
    base.forEach(([y, x]) => {
      cells.push([y + component.y, x + component.x]);
    });
  });
  return normalizeCells(cells);
}

function rotateAscii(asciiRows) {
  const height = asciiRows.length;
  const width = asciiRows[0].length;
  const rotated = [];
  for (let x = 0; x < width; x++) {
    let row = '';
    for (let y = height - 1; y >= 0; y--) {
      row += asciiRows[y][x];
    }
    rotated.push(row);
  }
  return rotated;
}

const gliderAscii = [
  '.O.',
  '..O',
  'OOO'
];

function createGliderOrientation(name, rotations) {
  let ascii = gliderAscii;
  for (let i = 0; i < rotations; i++) {
    ascii = rotateAscii(ascii);
  }
  return { name, ascii };
}

const lwssAscii = [
  '.O..O',
  'O....',
  'O...O',
  'OOOO.'
];

function createLwssOrientation(name, rotations) {
  let ascii = lwssAscii;
  for (let i = 0; i < rotations; i++) {
    ascii = rotateAscii(ascii);
  }
  return { name, ascii };
}
const patternDefinitions = [
  {
    id: 'block',
    nameEn: 'Block',
    nameFr: 'Bloc',
    descriptionEn: 'A 2×2 still life that stays solid forever.',
    descriptionFr: 'Un motif stable 2×2 qui reste figé pour toujours.',
    ascii: buildGridFromCells(baseShapes.block)
  },
  {
    id: 'beehive',
    nameEn: 'Beehive',
    nameFr: 'Ruche',
    descriptionEn: 'Six cells forming a honeycomb-like still life.',
    descriptionFr: 'Six cellules qui dessinent une alvéole immobile.',
    ascii: buildGridFromCells(baseShapes.beehive)
  },
  {
    id: 'loaf',
    nameEn: 'Loaf',
    nameFr: 'Pain de sucre',
    descriptionEn: 'A compact still life shaped like a loaf of bread.',
    descriptionFr: 'Un motif compact rappelant la forme d’un pain de sucre.',
    ascii: buildGridFromCells(baseShapes.loaf)
  },
  {
    id: 'boat',
    nameEn: 'Boat',
    nameFr: 'Bateau',
    descriptionEn: 'A five-cell still life shaped like a tiny boat.',
    descriptionFr: 'Un petit bateau de cinq cellules qui reste stable.',
    ascii: buildGridFromCells(baseShapes.boat)
  },
  {
    id: 'tub',
    nameEn: 'Tub',
    nameFr: 'Baignoire',
    descriptionEn: 'Four cells forming a rounded tub-shaped still life.',
    descriptionFr: 'Quatre cellules qui dessinent une petite baignoire stable.',
    ascii: buildGridFromCells(baseShapes.tub)
  },
  {
    id: 'ship',
    nameEn: 'Ship',
    nameFr: 'Navire',
    descriptionEn: 'A six-cell still life shaped like a small ship.',
    descriptionFr: 'Un navire immobile composé de six cellules.',
    ascii: buildGridFromCells(baseShapes.ship)
  },
  {
    id: 'longBoat',
    nameEn: 'Long boat',
    nameFr: 'Bateau long',
    descriptionEn: 'A stretched boat still life with an extended stern.',
    descriptionFr: 'Un bateau allongé dont la poupe s’étire sur une case supplémentaire.',
    ascii: buildGridFromCells(baseShapes.longBoat)
  },
  {
    id: 'spurBoat',
    nameEn: 'Spur boat',
    nameFr: 'Bateau éperon',
    descriptionEn: 'A seven-cell hook-shaped still life often used as an eater.',
    descriptionFr: 'Un motif à sept cellules en forme de crochet, souvent utilisé comme absorbeur.',
    ascii: buildGridFromCells(baseVariants.spurBoat)
  },
  {
    id: 'cornerBoat',
    nameEn: 'Corner boat',
    nameFr: 'Bateau d’angle',
    descriptionEn: 'A hook-shaped boat variant that hugs a corner.',
    descriptionFr: 'Une variante du bateau en crochet qui épouse un angle.',
    ascii: buildGridFromCells(baseVariants.cornerBoat)
  },
  {
    id: 'boatTie',
    nameEn: 'Boat tie',
    nameFr: 'Nœud de bateaux',
    descriptionEn: 'Two boats fused corner to corner into a stable still life.',
    descriptionFr: 'Deux bateaux soudés par un coin forment ce motif stable.',
    ascii: buildGridFromCells(baseVariants.boatTie)
  },
  {
    id: 'cappedBoat',
    nameEn: 'Capped boat',
    nameFr: 'Bateau coiffé',
    descriptionEn: 'A boat capped with an extra block that keeps it stable.',
    descriptionFr: 'Un bateau coiffé d’un bloc supplémentaire qui le stabilise.',
    ascii: buildGridFromCells(baseVariants.cappedBoat)
  },
  {
    id: 'mirroredHook',
    nameEn: 'Mirrored hook',
    nameFr: 'Crochet miroir',
    descriptionEn: 'A mirrored hook still life that often appears in eater catalogs.',
    descriptionFr: 'Un crochet symétrique que l’on retrouve souvent parmi les absorbeurs.',
    ascii: buildGridFromCells(baseVariants.mirroredHook)
  },
  {
    id: 'fishhook',
    nameEn: 'Fishhook',
    nameFr: 'Hameçon',
    descriptionEn: 'A compact hook-shaped still life reminiscent of a fishhook.',
    descriptionFr: 'Un petit crochet en forme d’hameçon, stable et utile pour détourner les signaux.',
    ascii: buildGridFromCells(baseVariants.fishhook)
  },
  {
    id: 'tailBoat',
    nameEn: 'Tail boat',
    nameFr: 'Bateau à queue',
    descriptionEn: 'A boat variant sporting a tail that can catch gliders.',
    descriptionFr: 'Une variante de bateau dotée d’une queue capable d’attraper des planeurs.',
    ascii: buildGridFromCells(baseVariants.tailBoat)
  },
  {
    id: 'offsetBoat',
    nameEn: 'Offset boat',
    nameFr: 'Bateau décalé',
    descriptionEn: 'A diagonally offset boat still life with seven cells.',
    descriptionFr: 'Un bateau de sept cellules dont la coque est décalée en diagonale.',
    ascii: buildGridFromCells(baseVariants.offsetBoat)
  },
  {
    id: 'twistedBoat',
    nameEn: 'Twisted boat',
    nameFr: 'Bateau torsadé',
    descriptionEn: 'A twisted boat variant where the bow folds inward.',
    descriptionFr: 'Une variante torsadée dont la proue se replie vers l’intérieur.',
    ascii: buildGridFromCells(baseVariants.twistedBoat)
  },
  {
    id: 'diagonalLongBoat',
    nameEn: 'Diagonal long boat',
    nameFr: 'Bateau long diagonal',
    descriptionEn: 'A long boat rotated along the diagonal, useful as an eater.',
    descriptionFr: 'Un bateau long aligné sur la diagonale, très utile comme absorbeur.',
    ascii: buildGridFromCells(baseVariants.diagonalLongBoat)
  },
  {
    id: 'shiftedBoat',
    nameEn: 'Shifted boat',
    nameFr: 'Bateau décalé',
    descriptionEn: 'A skewed boat still life with an offset stern.',
    descriptionFr: 'Un bateau penché dont la poupe est décalée.',
    ascii: buildGridFromCells(baseVariants.shiftedBoat)
  },
  {
    id: 'skewedLongBoat',
    nameEn: 'Skewed long boat',
    nameFr: 'Bateau long penché',
    descriptionEn: 'A long boat stretched along a diagonal axis.',
    descriptionFr: 'Un bateau long étiré sur un axe diagonal.',
    ascii: buildGridFromCells(baseVariants.skewedLongBoat)
  },
  {
    id: 'layeredBoat',
    nameEn: 'Layered boat',
    nameFr: 'Bateau superposé',
    descriptionEn: 'A layered boat variant with a double-thick stern.',
    descriptionFr: 'Une variante superposée dont la poupe est doublée.',
    ascii: buildGridFromCells(baseVariants.layeredBoat)
  },
  {
    id: 'blockPairHorizontal',
    nameEn: 'Block pair (horizontal)',
    nameFr: 'Paire de blocs (horizontale)',
    descriptionEn: 'Two separated blocks aligned horizontally.',
    descriptionFr: 'Deux blocs séparés alignés horizontalement.',
    components: [
      { shape: 'block', x: 0, y: 0 },
      { shape: 'block', x: 4, y: 0 }
    ]
  },
  {
    id: 'blockPairVertical',
    nameEn: 'Block pair (vertical)',
    nameFr: 'Paire de blocs (verticale)',
    descriptionEn: 'Two separated blocks stacked vertically.',
    descriptionFr: 'Deux blocs séparés empilés verticalement.',
    components: [
      { shape: 'block', x: 0, y: 0 },
      { shape: 'block', x: 0, y: 3 }
    ]
  },
  {
    id: 'blockSquare',
    nameEn: 'Block square',
    nameFr: 'Carré de blocs',
    descriptionEn: 'Four blocks forming the corners of a hollow square.',
    descriptionFr: 'Quatre blocs disposés aux coins d’un carré creux.',
    components: [
      { shape: 'block', x: 0, y: 0 },
      { shape: 'block', x: 4, y: 0 },
      { shape: 'block', x: 0, y: 4 },
      { shape: 'block', x: 4, y: 4 }
    ]
  },
  {
    id: 'blockRing',
    nameEn: 'Block ring',
    nameFr: 'Anneau de blocs',
    descriptionEn: 'A wide ring made of four distant blocks.',
    descriptionFr: 'Un large anneau composé de quatre blocs espacés.',
    components: [
      { shape: 'block', x: 0, y: 0 },
      { shape: 'block', x: 6, y: 0 },
      { shape: 'block', x: 0, y: 6 },
      { shape: 'block', x: 6, y: 6 }
    ]
  },
  {
    id: 'beehivePairHorizontal',
    nameEn: 'Beehive pair',
    nameFr: 'Paire de ruches',
    descriptionEn: 'Two beehives side by side with a one-cell gap.',
    descriptionFr: 'Deux ruches côte à côte séparées par une case.',
    components: [
      { shape: 'beehive', x: 0, y: 0 },
      { shape: 'beehive', x: 6, y: 0 }
    ]
  },
  {
    id: 'loafPairDiagonal',
    nameEn: 'Diagonal loaf pair',
    nameFr: 'Paire de pains diagonale',
    descriptionEn: 'Two loaves arranged on a diagonal to leave breathing room.',
    descriptionFr: 'Deux pains de sucre disposés en diagonale pour éviter toute interaction.',
    components: [
      { shape: 'loaf', x: 0, y: 0 },
      { shape: 'loaf', x: 6, y: 4 }
    ]
  },
  {
    id: 'boatFleet',
    nameEn: 'Boat fleet',
    nameFr: 'Flotte de bateaux',
    descriptionEn: 'Three boats aligned to form a tiny fleet.',
    descriptionFr: 'Trois bateaux alignés qui forment une petite flotte.',
    components: [
      { shape: 'boat', x: 0, y: 0 },
      { shape: 'boat', x: 5, y: 0 },
      { shape: 'boat', x: 10, y: 0 }
    ]
  },
  {
    id: 'loafAndBoat',
    nameEn: 'Loaf and boat',
    nameFr: 'Pain et bateau',
    descriptionEn: 'A loaf guarding a nearby boat.',
    descriptionFr: 'Un pain de sucre qui veille sur un bateau voisin.',
    components: [
      { shape: 'loaf', x: 0, y: 0 },
      { shape: 'boat', x: 6, y: 1 }
    ]
  },
  {
    id: 'blockAndBeehive',
    nameEn: 'Block and beehive',
    nameFr: 'Bloc et ruche',
    descriptionEn: 'A block paired with a beehive for a simple garden.',
    descriptionFr: 'Un bloc associé à une ruche pour un petit jardin immobile.',
    components: [
      { shape: 'block', x: 0, y: 0 },
      { shape: 'beehive', x: 5, y: 0 }
    ]
  },
  {
    id: 'tubPair',
    nameEn: 'Tub pair',
    nameFr: 'Paire de baignoires',
    descriptionEn: 'Two tubs separated diagonally.',
    descriptionFr: 'Deux baignoires séparées en diagonale.',
    components: [
      { shape: 'tub', x: 0, y: 0 },
      { shape: 'tub', x: 4, y: 4 }
    ]
  },
  {
    id: 'shipPair',
    nameEn: 'Ship pair',
    nameFr: 'Paire de navires',
    descriptionEn: 'Two ships docked with enough room to stay still.',
    descriptionFr: 'Deux navires amarrés avec assez d’espace pour rester stables.',
    components: [
      { shape: 'ship', x: 0, y: 0 },
      { shape: 'ship', x: 5, y: 0 }
    ]
  },
  {
    id: 'tubRing',
    nameEn: 'Tub ring',
    nameFr: 'Anneau de baignoires',
    descriptionEn: 'Four tubs arranged in a loose ring.',
    descriptionFr: 'Quatre baignoires disposées en anneau.',
    components: [
      { shape: 'tub', x: 0, y: 0 },
      { shape: 'tub', x: 4, y: 0 },
      { shape: 'tub', x: 0, y: 4 },
      { shape: 'tub', x: 4, y: 4 }
    ]
  },
  {
    id: 'stillLifeGarden',
    nameEn: 'Still-life garden',
    nameFr: 'Jardin immobile',
    descriptionEn: 'A small garden of a block, tub, and boat living in harmony.',
    descriptionFr: 'Un petit jardin composé d’un bloc, d’une baignoire et d’un bateau.',
    components: [
      { shape: 'block', x: 0, y: 0 },
      { shape: 'tub', x: 5, y: 0 },
      { shape: 'boat', x: 3, y: 4 }
    ]
  }
];
patternDefinitions.push(
  {
    id: 'blinker',
    nameEn: 'Blinker',
    nameFr: 'Clignotant',
    descriptionEn: 'The classic period-2 oscillator of three cells.',
    descriptionFr: 'L’oscillateur de période 2 le plus célèbre, composé de trois cellules.',
    ascii: [
      '.O.',
      '.O.',
      '.O.'
    ]
  },
  {
    id: 'blinkerPairHorizontal',
    nameEn: 'Blinker pair',
    nameFr: 'Paire de clignotants',
    descriptionEn: 'Two synchronized blinkers separated by a gap.',
    descriptionFr: 'Deux clignotants synchronisés séparés par un espace.',
    ascii: [
      '.O...O.',
      '.O...O.',
      '.O...O.'
    ]
  },
  {
    id: 'blinkerCross',
    nameEn: 'Blinker cross',
    nameFr: 'Croix de clignotants',
    descriptionEn: 'Four blinkers forming a cross of pulsing light.',
    descriptionFr: 'Quatre clignotants formant une croix lumineuse.',
    ascii: [
      '..O..',
      '..O..',
      'OOOOO',
      '..O..',
      '..O..'
    ]
  },
  {
    id: 'toad',
    nameEn: 'Toad',
    nameFr: 'Crapaud',
    descriptionEn: 'A six-cell period-2 oscillator shaped like a toad.',
    descriptionFr: 'Un oscillateur de période 2 en forme de crapaud.',
    ascii: [
      '.OOO',
      'OOO.'
    ]
  },
  {
    id: 'toadPair',
    nameEn: 'Toad pair',
    nameFr: 'Paire de crapauds',
    descriptionEn: 'Two toads oscillating in parallel.',
    descriptionFr: 'Deux crapauds qui oscillent en parallèle.',
    ascii: [
      '.OOO...OOO',
      'OOO...OOO'
    ]
  },
  {
    id: 'beacon',
    nameEn: 'Beacon',
    nameFr: 'Balise',
    descriptionEn: 'Two blocks flashing against each other every other tick.',
    descriptionFr: 'Deux blocs qui s’allument en alternance toutes les deux générations.',
    ascii: [
      'OO..',
      'OO..',
      '..OO',
      '..OO'
    ]
  },
  {
    id: 'beaconPair',
    nameEn: 'Beacon pair',
    nameFr: 'Paire de balises',
    descriptionEn: 'Two beacons blinking in sync for a rhythmic signal.',
    descriptionFr: 'Deux balises clignotant en phase pour créer un signal rythmique.',
    ascii: [
      'OO....OO',
      'OO....OO',
      '..OO..OO',
      '..OO..OO'
    ]
  },
  {
    id: 'rPentomino',
    nameEn: 'R-pentomino',
    nameFr: 'Pentomino R',
    descriptionEn: 'A famous methuselah that erupts into chaotic life.',
    descriptionFr: 'Un méthusalem célèbre qui déclenche une évolution chaotique.',
    ascii: [
      '.OO',
      'OO.',
      '.O.'
    ]
  },
  {
    id: 'acorn',
    nameEn: 'Acorn',
    nameFr: 'Gland',
    descriptionEn: 'A seven-cell methuselah that takes generations to settle.',
    descriptionFr: 'Un méthusalem de sept cellules qui met de nombreuses générations à se stabiliser.',
    ascii: [
      '.O.....',
      '...O...',
      'OO..OOO'
    ]
  },
  {
    id: 'diehard',
    nameEn: 'Diehard',
    nameFr: 'Dur à cuire',
    descriptionEn: 'A sparse pattern that lives for 130 generations before vanishing.',
    descriptionFr: 'Un motif rare qui survit 130 générations avant de disparaître.',
    ascii: [
      '......O.',
      'OO......',
      '.O..OOO.'
    ]
  },
  {
    id: 'piHeptomino',
    nameEn: 'Pi-heptomino',
    nameFr: 'Heptomino Pi',
    descriptionEn: 'Seven cells arranged like the Greek letter π.',
    descriptionFr: 'Sept cellules disposées comme la lettre grecque π.',
    ascii: [
      '.OOO.',
      '.O.O.',
      '.O.O.'
    ]
  }
);
const gliderPatterns = [
  createGliderOrientation('gliderNE', 1),
  createGliderOrientation('gliderNW', 2),
  createGliderOrientation('gliderSW', 3)
];

gliderPatterns.forEach(({ name, ascii }) => {
  patternDefinitions.push({
    id: name,
    nameEn:
      name === 'gliderNE'
        ? 'Glider (north-east)'
        : name === 'gliderNW'
        ? 'Glider (north-west)'
        : 'Glider (south-west)',
    nameFr:
      name === 'gliderNE'
        ? 'Planeur (nord-est)'
        : name === 'gliderNW'
        ? 'Planeur (nord-ouest)'
        : 'Planeur (sud-ouest)',
    descriptionEn: 'A glider rotated to travel in a different diagonal direction.',
    descriptionFr: 'Un planeur tourné pour voyager sur une autre diagonale.',
    ascii
  });
});

const lwssPatterns = [
  createLwssOrientation('lightweightSpaceshipNorth', 1),
  createLwssOrientation('lightweightSpaceshipWest', 2),
  createLwssOrientation('lightweightSpaceshipSouth', 3)
];

lwssPatterns.forEach(({ name, ascii }) => {
  patternDefinitions.push({
    id: name,
    nameEn:
      name === 'lightweightSpaceshipNorth'
        ? 'Lightweight spaceship (north)'
        : name === 'lightweightSpaceshipWest'
        ? 'Lightweight spaceship (west)'
        : 'Lightweight spaceship (south)',
    nameFr:
      name === 'lightweightSpaceshipNorth'
        ? 'Vaisseau léger (nord)'
        : name === 'lightweightSpaceshipWest'
        ? 'Vaisseau léger (ouest)'
        : 'Vaisseau léger (sud)',
    descriptionEn: 'The lightweight spaceship rotated to travel along another axis.',
    descriptionFr: 'Le vaisseau léger pivoté pour voyager selon un autre axe.',
    ascii
  });
});
function definitionToPattern(def) {
  let cells;
  if (def.ascii) {
    cells = asciiToCells(def.ascii);
  } else if (def.components) {
    cells = placeComponents(def);
  } else {
    throw new Error(`Pattern ${def.id} is missing ascii or components definition.`);
  }
  const normalized = normalizeCells(cells);
  const grid = buildGridFromCells(normalized);
  const height = grid.length;
  const width = grid[0].length;
  const anchor = {
    x: Math.floor((width - 1) / 2),
    y: Math.floor((height - 1) / 2)
  };
  return {
    id: def.id,
    nameKey: `index.sections.gameOfLife.patterns.${def.id}`,
    descriptionKey: `index.sections.gameOfLife.patterns.${def.id}Description`,
    anchor,
    cells: normalized
  };
}

function sortObjectKeys(obj) {
  const sorted = {};
  Object.keys(obj)
    .sort((a, b) => a.localeCompare(b))
    .forEach(key => {
      sorted[key] = obj[key];
    });
  return sorted;
}

function updateFiles() {
  const patternFile = JSON.parse(fs.readFileSync(patternsPath, 'utf8'));
  const existingIds = new Set(patternFile.patterns.map(pattern => pattern.id));
  const additions = [];

  patternDefinitions.forEach(def => {
    if (existingIds.has(def.id)) {
      return;
    }
    additions.push(definitionToPattern(def));
  });

  if (additions.length === 0) {
    console.log('No new patterns to add.');
  } else {
    const combined = patternFile.patterns.concat(additions);
    combined.sort((a, b) => a.id.localeCompare(b.id));
    patternFile.patterns = combined;
    fs.writeFileSync(patternsPath, JSON.stringify(patternFile, null, 2) + '\n');
    console.log(`Added ${additions.length} patterns to ${patternsPath}.`);
  }

  const enData = JSON.parse(fs.readFileSync(enPath, 'utf8'));
  const frData = JSON.parse(fs.readFileSync(frPath, 'utf8'));

  const enPatterns = enData?.index?.sections?.gameOfLife?.patterns;
  const frPatterns = frData?.index?.sections?.gameOfLife?.patterns;
  if (!enPatterns || !frPatterns) {
    throw new Error('Unable to locate pattern translations in i18n files.');
  }

  let addedTranslations = 0;
  patternDefinitions.forEach(def => {
    const nameKey = def.id;
    const descriptionKey = `${def.id}Description`;
    if (!Object.prototype.hasOwnProperty.call(enPatterns, nameKey)) {
      enPatterns[nameKey] = def.nameEn;
      addedTranslations++;
    }
    if (!Object.prototype.hasOwnProperty.call(enPatterns, descriptionKey)) {
      enPatterns[descriptionKey] = def.descriptionEn;
      addedTranslations++;
    }
    if (!Object.prototype.hasOwnProperty.call(frPatterns, nameKey)) {
      frPatterns[nameKey] = def.nameFr;
      addedTranslations++;
    }
    if (!Object.prototype.hasOwnProperty.call(frPatterns, descriptionKey)) {
      frPatterns[descriptionKey] = def.descriptionFr;
      addedTranslations++;
    }
  });

  enData.index.sections.gameOfLife.patterns = sortObjectKeys(enPatterns);
  frData.index.sections.gameOfLife.patterns = sortObjectKeys(frPatterns);

  fs.writeFileSync(enPath, JSON.stringify(enData, null, 2) + '\n');
  fs.writeFileSync(frPath, JSON.stringify(frData, null, 2) + '\n');

  console.log(`Updated translation files with ${addedTranslations} new entries.`);
}

updateFiles();
