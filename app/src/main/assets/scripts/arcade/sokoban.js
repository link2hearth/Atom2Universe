(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/sokoban.json';
  const GAME_ID = 'sokoban';
  const SAVE_VERSION = 1;
  const AUTOSAVE_DELAY_MS = 200;
  const BLOCKED_STATUS_RESET_MS = 1600;
  const COMPLETION_HIGHLIGHT_DURATION_MS = 450;
  const AUTO_ADVANCE_DELAY_MS = 520;
  const COMPLETION_GACHA_REWARD = Object.freeze({ chance: 0.25, tickets: 1 });

  const DEFAULT_LEVELS = Object.freeze([]);

  const DEFAULT_CONFIG = Object.freeze({
    shuffleMoves: Object.freeze({ min: 48, max: 160 }),
    generator: Object.freeze({
      width: Object.freeze({ min: 6, max: 12 }),
      height: Object.freeze({ min: 6, max: 12 }),
      boxes: Object.freeze({ min: 1, max: 4 }),
      wallDensity: Object.freeze({ min: 0.05, max: 0.2 }),
      attempts: 24
    }),
    map: Object.freeze([]),
    targets: Object.freeze([]),
    playerStart: Object.freeze([0, 0]),
    levels: DEFAULT_LEVELS
  });

  const KEY_DIRECTIONS = Object.freeze({
    ArrowUp: { row: -1, col: 0 },
    ArrowDown: { row: 1, col: 0 },
    ArrowLeft: { row: 0, col: -1 },
    ArrowRight: { row: 0, col: 1 },
    z: { row: -1, col: 0 },
    Z: { row: -1, col: 0 },
    w: { row: -1, col: 0 },
    W: { row: -1, col: 0 },
    s: { row: 1, col: 0 },
    S: { row: 1, col: 0 },
    q: { row: 0, col: -1 },
    Q: { row: 0, col: -1 },
    a: { row: 0, col: -1 },
    A: { row: 0, col: -1 },
    d: { row: 0, col: 1 },
    D: { row: 0, col: 1 }
  });

  const DIRECTIONS = Object.freeze([
    { row: -1, col: 0 },
    { row: 1, col: 0 },
    { row: 0, col: -1 },
    { row: 0, col: 1 }
  ]);

  function decodeKey(key) {
    if (typeof key !== 'string') {
      return { row: 0, col: 0 };
    }
    const [rowString, colString] = key.split(',');
    const row = Number(rowString);
    const col = Number(colString);
    return {
      row: Number.isFinite(row) ? row : 0,
      col: Number.isFinite(col) ? col : 0
    };
  }

  const state = {
    config: DEFAULT_CONFIG,
    configSignature: computeConfigSignature(DEFAULT_CONFIG),
    width: DEFAULT_CONFIG.map[0]?.length || 0,
    height: DEFAULT_CONFIG.map.length,
    tiles: [],
    targetKeys: new Set(),
    boxes: new Set(),
    levelDefinitions: [],
    levelIndex: 0,
    currentLevelLayout: null,
    player: { row: 0, col: 0 },
    playerStart: { row: 0, col: 0 },
    moveCount: 0,
    pushCount: 0,
    level: 1,
    solved: false,
    ready: false,
    active: false,
    pointer: null,
    statusTimeout: null,
    autosaveTimer: null,
    celebrationTimer: null,
    autoAdvanceTimer: null,
    baseStatus: null,
    initialSnapshot: null,
    currentMetrics: null,
    cellElements: [],
    elements: null,
    languageHandler: null
  };

  function computeMapSignature(map) {
    if (!Array.isArray(map)) {
      return 'default';
    }
    return map.map(row => String(row)).join('|');
  }

  function computeConfigSignature(config) {
    if (!config || typeof config !== 'object') {
      return 'default';
    }
    if (Array.isArray(config.levels) && config.levels.length > 0) {
      const parts = config.levels.map(level => {
        if (!level || typeof level !== 'object') {
          return '';
        }
        const mapSource = Array.isArray(level.map)
          ? level.map
          : Array.isArray(level.layout)
            ? level.layout
            : [];
        const mapSignature = computeMapSignature(mapSource);
        const targetSignature = Array.isArray(level.targets)
          ? level.targets
              .map(entry => (Array.isArray(entry) && entry.length >= 2 ? `${entry[0]},${entry[1]}` : ''))
              .filter(Boolean)
              .sort()
              .join(';')
          : '';
        const boxSignature = Array.isArray(level.boxes)
          ? level.boxes
              .map(entry => (Array.isArray(entry) && entry.length >= 2 ? `${entry[0]},${entry[1]}` : ''))
              .filter(Boolean)
              .sort()
              .join(';')
          : '';
        const playerSignature = Array.isArray(level.playerStart)
          ? `${clampInt(level.playerStart[0], -9999, 9999, 0)},${clampInt(level.playerStart[1], -9999, 9999, 0)}`
          : '0,0';
        const nameSignature = typeof level.name === 'string' ? level.name : '';
        return `${mapSignature}#${targetSignature}#${boxSignature}#${playerSignature}#${nameSignature}`;
      });
      return `levels:${parts.join('||')}`;
    }
    if (config.generator && typeof config.generator === 'object') {
      const generator = config.generator;
      const widthMin = clampInt(generator?.width?.min, 3, 9999, 0);
      const widthMax = clampInt(generator?.width?.max, widthMin, 9999, widthMin);
      const heightMin = clampInt(generator?.height?.min, 3, 9999, 0);
      const heightMax = clampInt(generator?.height?.max, heightMin, 9999, heightMin);
      const boxesMin = clampInt(generator?.boxes?.min, 1, 9999, 1);
      const boxesMax = clampInt(generator?.boxes?.max, boxesMin, 9999, boxesMin);
      const densityMin = clampNumber(generator?.wallDensity?.min, 0, 1, 0);
      const densityMax = clampNumber(generator?.wallDensity?.max, densityMin, 1, densityMin);
      const attempts = clampInt(generator?.attempts, 1, 9999, 0);
      const shuffleMin = clampInt(config?.shuffleMoves?.min, 1, 9999, 0);
      const shuffleMax = clampInt(config?.shuffleMoves?.max, shuffleMin, 9999, shuffleMin);
      return `generator:${widthMin}-${widthMax}x${heightMin}-${heightMax}|boxes:${boxesMin}-${boxesMax}|density:${densityMin.toFixed(3)}-${densityMax.toFixed(3)}|attempts:${attempts}|shuffle:${shuffleMin}-${shuffleMax}`;
    }
    if (Array.isArray(config.map)) {
      return computeMapSignature(config.map);
    }
    return 'default';
  }

  function translateText(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return typeof fallback === 'string' ? fallback : '';
    }
    const translator = typeof window !== 'undefined'
      && window.i18n
      && typeof window.i18n.t === 'function'
        ? window.i18n.t
        : typeof window !== 'undefined' && typeof window.t === 'function'
          ? window.t
          : null;
    if (translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string' && result.trim()) {
          return result;
        }
      } catch (error) {
        console.warn('Sokoban translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return fallback;
    }
    return key;
  }

  function formatIntegerLocalized(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    try {
      const formatter = typeof Intl !== 'undefined' && Intl.NumberFormat
        ? new Intl.NumberFormat()
        : null;
      if (formatter) {
        return formatter.format(Math.floor(Math.abs(numeric)));
      }
    } catch (error) {
      console.warn('Sokoban number formatting error', error);
    }
    return String(Math.floor(Math.abs(numeric)));
  }

  function clampInt(value, min, max, fallback) {
    if (typeof clampInteger === 'function') {
      return clampInteger(value, min, max, fallback);
    }
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    const clamped = Math.min(Math.max(Math.floor(numeric), min), max);
    return Number.isFinite(clamped) ? clamped : fallback;
  }

  function clampNumber(value, min, max, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    const minimum = Number(min);
    const maximum = Number(max);
    if (!Number.isFinite(minimum) || !Number.isFinite(maximum) || minimum > maximum) {
      return fallback;
    }
    const clamped = Math.min(Math.max(numeric, minimum), maximum);
    return Number.isFinite(clamped) ? clamped : fallback;
  }

  function randomInt(min, max) {
    const minValue = Math.ceil(Number(min));
    const maxValue = Math.floor(Number(max));
    if (!Number.isFinite(minValue) || !Number.isFinite(maxValue)) {
      return 0;
    }
    if (maxValue < minValue) {
      return minValue;
    }
    return Math.floor(Math.random() * (maxValue - minValue + 1)) + minValue;
  }

  function randomFloat(min, max) {
    const minValue = Number(min);
    const maxValue = Number(max);
    if (!Number.isFinite(minValue) || !Number.isFinite(maxValue) || maxValue <= minValue) {
      return minValue;
    }
    return Math.random() * (maxValue - minValue) + minValue;
  }

  function shuffleArray(array) {
    if (!Array.isArray(array)) {
      return;
    }
    for (let i = array.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = array[i];
      array[i] = array[j];
      array[j] = tmp;
    }
  }

  function isGridWall(grid, row, col) {
    if (!Array.isArray(grid) || grid.length <= 0) {
      return true;
    }
    const height = grid.length;
    const width = grid[0]?.length || 0;
    if (row < 0 || row >= height || col < 0 || col >= width) {
      return true;
    }
    return grid[row][col] === '#';
  }

  function gridCellHasPushSpace(grid, row, col) {
    if (isGridWall(grid, row, col)) {
      return false;
    }
    for (const direction of DIRECTIONS) {
      const behindRow = row - direction.row;
      const behindCol = col - direction.col;
      const aheadRow = row + direction.row;
      const aheadCol = col + direction.col;
      if (!isGridWall(grid, behindRow, behindCol) && !isGridWall(grid, aheadRow, aheadCol)) {
        return true;
      }
    }
    return false;
  }

  function isGridDeadCell(grid, row, col) {
    if (isGridWall(grid, row, col)) {
      return true;
    }
    const up = isGridWall(grid, row - 1, col);
    const down = isGridWall(grid, row + 1, col);
    const left = isGridWall(grid, row, col - 1);
    const right = isGridWall(grid, row, col + 1);
    return (up && left) || (up && right) || (down && left) || (down && right);
  }

  function normalizeCoordinate(value, maxIndex) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return null;
    }
    const integer = Math.floor(numeric);
    if (!Number.isFinite(integer)) {
      return null;
    }
    const maxCandidate = Number(maxIndex);
    if (!Number.isFinite(maxCandidate)) {
      return null;
    }
    const clamped = Math.min(Math.max(integer, 0), maxCandidate);
    return Number.isFinite(clamped) ? clamped : null;
  }

  function gridHasSingleRegion(grid, expectedOpenCells) {
    if (!Array.isArray(grid) || grid.length <= 0 || expectedOpenCells <= 0) {
      return false;
    }
    const height = grid.length;
    const width = grid[0]?.length || 0;
    if (width <= 0) {
      return false;
    }
    let startRow = -1;
    let startCol = -1;
    for (let row = 0; row < height; row += 1) {
      for (let col = 0; col < width; col += 1) {
        if (grid[row][col] !== '#') {
          startRow = row;
          startCol = col;
          break;
        }
      }
      if (startRow >= 0) {
        break;
      }
    }
    if (startRow < 0 || startCol < 0) {
      return false;
    }
    const visited = new Array(height);
    for (let row = 0; row < height; row += 1) {
      visited[row] = new Array(width).fill(false);
    }
    const stack = [[startRow, startCol]];
    visited[startRow][startCol] = true;
    let visitedCount = 0;
    while (stack.length) {
      const [row, col] = stack.pop();
      visitedCount += 1;
      for (const direction of DIRECTIONS) {
        const nextRow = row + direction.row;
        const nextCol = col + direction.col;
        if (nextRow < 0 || nextRow >= height || nextCol < 0 || nextCol >= width) {
          continue;
        }
        if (grid[nextRow][nextCol] === '#') {
          continue;
        }
        if (visited[nextRow][nextCol]) {
          continue;
        }
        visited[nextRow][nextCol] = true;
        stack.push([nextRow, nextCol]);
      }
    }
    return visitedCount === expectedOpenCells;
  }

  function buildConnectedGrid(width, height, wallDensityRange) {
    const safeWidth = clampInt(width, 3, 24, 6);
    const safeHeight = clampInt(height, 3, 24, 6);
    const grid = new Array(safeHeight);
    for (let row = 0; row < safeHeight; row += 1) {
      const rowCells = new Array(safeWidth);
      for (let col = 0; col < safeWidth; col += 1) {
        rowCells[col] = '.';
      }
      grid[row] = rowCells;
    }
    const interiorCells = [];
    for (let row = 1; row < safeHeight - 1; row += 1) {
      for (let col = 1; col < safeWidth - 1; col += 1) {
        interiorCells.push([row, col]);
      }
    }
    shuffleArray(interiorCells);

    const minDensity = clampNumber(wallDensityRange?.min, 0, 0.45, DEFAULT_CONFIG.generator.wallDensity.min);
    const maxDensity = clampNumber(wallDensityRange?.max, minDensity, 0.45, Math.max(minDensity, DEFAULT_CONFIG.generator.wallDensity.max));
    const density = randomFloat(minDensity, maxDensity);
    const totalCells = safeWidth * safeHeight;
    const maxWalls = Math.min(interiorCells.length, Math.floor(totalCells * Math.min(Math.max(density, 0), 0.45)));
    let openCells = totalCells;
    let placed = 0;
    const minimumOpenCells = Math.max(safeWidth + safeHeight, 6);

    for (let index = 0; index < interiorCells.length && placed < maxWalls; index += 1) {
      const [row, col] = interiorCells[index];
      if (openCells - 1 < minimumOpenCells) {
        break;
      }
      grid[row][col] = '#';
      const newOpenCells = openCells - 1;
      if (gridHasSingleRegion(grid, newOpenCells)) {
        openCells = newOpenCells;
        placed += 1;
      } else {
        grid[row][col] = '.';
      }
    }

    if (openCells < minimumOpenCells) {
      return null;
    }

    return grid;
  }

  function generateProceduralLayout(generator) {
    if (!generator || typeof generator !== 'object') {
      return null;
    }
    const widthMin = clampInt(generator?.width?.min, 3, 24, DEFAULT_CONFIG.generator.width.min);
    const widthMax = clampInt(generator?.width?.max, widthMin, 24, DEFAULT_CONFIG.generator.width.max);
    const heightMin = clampInt(generator?.height?.min, 3, 24, DEFAULT_CONFIG.generator.height.min);
    const heightMax = clampInt(generator?.height?.max, heightMin, 24, DEFAULT_CONFIG.generator.height.max);
    const boxesMin = clampInt(generator?.boxes?.min, 1, 8, DEFAULT_CONFIG.generator.boxes.min);
    const boxesMax = clampInt(generator?.boxes?.max, boxesMin, 12, DEFAULT_CONFIG.generator.boxes.max);

    const width = randomInt(widthMin, widthMax);
    const height = randomInt(heightMin, heightMax);
    const grid = buildConnectedGrid(width, height, generator?.wallDensity);
    if (!grid) {
      return null;
    }

    const floorCells = [];
    for (let row = 0; row < height; row += 1) {
      for (let col = 0; col < width; col += 1) {
        if (grid[row][col] !== '#') {
          floorCells.push([row, col]);
        }
      }
    }
    if (floorCells.length <= 2) {
      return null;
    }

    const maxBoxes = Math.min(boxesMax, floorCells.length - 1);
    const minBoxes = Math.min(boxesMin, maxBoxes);
    if (maxBoxes <= 0 || minBoxes <= 0 || maxBoxes < minBoxes) {
      return null;
    }

    const boxCount = randomInt(minBoxes, maxBoxes);
    if (boxCount <= 0 || floorCells.length <= boxCount) {
      return null;
    }

    const candidateTargets = floorCells.filter(cell => gridCellHasPushSpace(grid, cell[0], cell[1]));
    let targetPool = candidateTargets.length >= boxCount
      ? candidateTargets.slice()
      : floorCells.filter(cell => !isGridDeadCell(grid, cell[0], cell[1]));
    if (targetPool.length < boxCount) {
      targetPool = floorCells.slice();
    }
    shuffleArray(targetPool);
    const usedKeys = new Set();
    const targets = [];
    for (let index = 0; index < targetPool.length && targets.length < boxCount; index += 1) {
      const [row, col] = targetPool[index];
      const key = `${row},${col}`;
      if (usedKeys.has(key)) {
        continue;
      }
      usedKeys.add(key);
      targets.push([row, col]);
    }
    if (targets.length !== boxCount) {
      return null;
    }

    const playerCandidates = floorCells.filter(cell => {
      const key = `${cell[0]},${cell[1]}`;
      if (usedKeys.has(key)) {
        return false;
      }
      for (const direction of DIRECTIONS) {
        const nextRow = cell[0] + direction.row;
        const nextCol = cell[1] + direction.col;
        if (!isGridWall(grid, nextRow, nextCol)) {
          return true;
        }
      }
      return false;
    });

    if (!playerCandidates.length) {
      return null;
    }

    const playerCell = chooseRandomEntry(playerCandidates) || playerCandidates[0];

    const mapRows = grid.map(row => row.join(''));
    return {
      map: mapRows,
      targets,
      playerStart: { row: playerCell[0], col: playerCell[1] }
    };
  }

  function sanitizeGeneratorConfig(rawGenerator) {
    const defaultGenerator = DEFAULT_CONFIG.generator;
    const source = rawGenerator && typeof rawGenerator === 'object' ? rawGenerator : {};
    const widthMin = clampInt(source?.width?.min, 3, 24, defaultGenerator.width.min);
    const widthMax = clampInt(source?.width?.max, widthMin, 24, defaultGenerator.width.max);
    const heightMin = clampInt(source?.height?.min, 3, 24, defaultGenerator.height.min);
    const heightMax = clampInt(source?.height?.max, heightMin, 24, defaultGenerator.height.max);
    const boxesMin = clampInt(source?.boxes?.min, 1, 8, defaultGenerator.boxes.min);
    const boxesMax = clampInt(source?.boxes?.max, boxesMin, 12, defaultGenerator.boxes.max);
    const densityMin = clampNumber(source?.wallDensity?.min, 0, 0.45, defaultGenerator.wallDensity.min);
    const densityMax = clampNumber(source?.wallDensity?.max, densityMin, 0.45, Math.max(densityMin, defaultGenerator.wallDensity.max));
    const attempts = clampInt(source?.attempts, 3, 200, defaultGenerator.attempts);
    return {
      width: { min: widthMin, max: widthMax },
      height: { min: heightMin, max: heightMax },
      boxes: { min: boxesMin, max: boxesMax },
      wallDensity: { min: densityMin, max: densityMax },
      attempts
    };
  }

  function keyFor(row, col) {
    return `${row},${col}`;
  }

  function isInside(row, col) {
    return row >= 0 && row < state.height && col >= 0 && col < state.width;
  }

  function isWall(row, col) {
    if (!isInside(row, col)) {
      return true;
    }
    return Boolean(state.tiles[row][col]?.wall);
  }

  function isWalkable(row, col) {
    return isInside(row, col) && !isWall(row, col);
  }

  function computeReachableCells(startRow, startCol, blockedKeys) {
    if (!isWalkable(startRow, startCol)) {
      return new Set();
    }
    const visited = new Set();
    const queue = [];
    queue.push([startRow, startCol]);
    for (let index = 0; index < queue.length; index += 1) {
      const [row, col] = queue[index];
      const key = keyFor(row, col);
      if (visited.has(key)) {
        continue;
      }
      visited.add(key);
      for (const direction of DIRECTIONS) {
        const nextRow = row + direction.row;
        const nextCol = col + direction.col;
        const nextKey = keyFor(nextRow, nextCol);
        if (!isWalkable(nextRow, nextCol)) {
          continue;
        }
        if (blockedKeys && blockedKeys.has(nextKey)) {
          continue;
        }
        if (!visited.has(nextKey)) {
          queue.push([nextRow, nextCol]);
        }
      }
    }
    return visited;
  }

  function computeReachableWithDistances(startRow, startCol, blockedKeys) {
    const distances = new Map();
    if (!isWalkable(startRow, startCol)) {
      return distances;
    }
    const queue = [];
    queue.push([startRow, startCol]);
    distances.set(keyFor(startRow, startCol), 0);
    for (let index = 0; index < queue.length; index += 1) {
      const [row, col] = queue[index];
      const currentDistance = distances.get(keyFor(row, col)) || 0;
      for (const direction of DIRECTIONS) {
        const nextRow = row + direction.row;
        const nextCol = col + direction.col;
        const nextKey = keyFor(nextRow, nextCol);
        if (distances.has(nextKey)) {
          continue;
        }
        if (!isWalkable(nextRow, nextCol)) {
          continue;
        }
        if (blockedKeys && blockedKeys.has(nextKey)) {
          continue;
        }
        distances.set(nextKey, currentDistance + 1);
        queue.push([nextRow, nextCol]);
      }
    }
    return distances;
  }

  function manhattanDistance(rowA, colA, rowB, colB) {
    return Math.abs(rowA - rowB) + Math.abs(colA - colB);
  }

  function decodeKeyToTuple(key) {
    const { row, col } = decodeKey(key);
    return [row, col];
  }

  function countBoxesOnTargets(boxesSet, targetSet) {
    let count = 0;
    for (const key of boxesSet) {
      if (targetSet.has(key)) {
        count += 1;
      }
    }
    return count;
  }

  function computeMinimalMatchingDistance(boxesArray, targetsArray) {
    if (!boxesArray.length || !targetsArray.length) {
      return 0;
    }
    const memo = new Map();
    const targetCount = targetsArray.length;

    function helper(index, usedMask) {
      if (index >= boxesArray.length) {
        return 0;
      }
      const memoKey = `${index}:${usedMask}`;
      if (memo.has(memoKey)) {
        return memo.get(memoKey);
      }
      let best = Infinity;
      for (let targetIndex = 0; targetIndex < targetCount; targetIndex += 1) {
        if (usedMask & (1 << targetIndex)) {
          continue;
        }
        const [boxRow, boxCol] = boxesArray[index];
        const [goalRow, goalCol] = targetsArray[targetIndex];
        const distance = manhattanDistance(boxRow, boxCol, goalRow, goalCol);
        const candidate = distance + helper(index + 1, usedMask | (1 << targetIndex));
        if (candidate < best) {
          best = candidate;
        }
      }
      memo.set(memoKey, best);
      return best;
    }

    return helper(0, 0);
  }

  function computeAverageBoxGoalDistance(boxesSet, targetSet) {
    const boxesArray = Array.from(boxesSet, decodeKeyToTuple);
    const targetsArray = Array.from(targetSet, decodeKeyToTuple);
    if (!boxesArray.length || !targetsArray.length) {
      return 0;
    }
    const totalDistance = computeMinimalMatchingDistance(boxesArray, targetsArray);
    return totalDistance / boxesArray.length;
  }

  function encodeBoxes(boxesSet) {
    return Array.from(boxesSet).sort().join('|');
  }

  function buildRowTargetPresence() {
    const rowTargets = new Array(state.height).fill(false);
    const columnTargets = new Array(state.width).fill(false);
    for (const key of state.targetKeys) {
      const { row, col } = decodeKey(key);
      if (row >= 0 && row < rowTargets.length) {
        rowTargets[row] = true;
      }
      if (col >= 0 && col < columnTargets.length) {
        columnTargets[col] = true;
      }
    }
    return { rowTargets, columnTargets };
  }

  function isCornerCell(row, col) {
    const up = isWall(row - 1, col);
    const down = isWall(row + 1, col);
    const left = isWall(row, col - 1);
    const right = isWall(row, col + 1);
    if ((up && left) || (up && right) || (down && left) || (down && right)) {
      return true;
    }
    return false;
  }

  function isStaticDeadlockForBox(row, col, boxesSet, targetSet, caches) {
    const key = keyFor(row, col);
    if (targetSet.has(key)) {
      return false;
    }
    if (isCornerCell(row, col)) {
      return true;
    }
    const up = isWall(row - 1, col);
    const down = isWall(row + 1, col);
    const left = isWall(row, col - 1);
    const right = isWall(row, col + 1);
    if ((up || down) && !caches.rowTargets[row]) {
      return true;
    }
    if ((left || right) && !caches.columnTargets[col]) {
      return true;
    }
    return false;
  }

  function hasPairDeadlock(boxesSet, targetSet) {
    const boxesArray = Array.from(boxesSet, decodeKeyToTuple);
    for (let i = 0; i < boxesArray.length; i += 1) {
      const [rowA, colA] = boxesArray[i];
      const keyA = keyFor(rowA, colA);
      const targetA = targetSet.has(keyA);
      for (let j = i + 1; j < boxesArray.length; j += 1) {
        const [rowB, colB] = boxesArray[j];
        if (Math.abs(rowA - rowB) + Math.abs(colA - colB) !== 1) {
          continue;
        }
        const keyB = keyFor(rowB, colB);
        const targetB = targetSet.has(keyB);
        if (targetA || targetB) {
          continue;
        }
        if (rowA === rowB) {
          const aboveWall = isWall(rowA - 1, colA) && isWall(rowB - 1, colB);
          const belowWall = isWall(rowA + 1, colA) && isWall(rowB + 1, colB);
          if (aboveWall || belowWall) {
            return true;
          }
        } else if (colA === colB) {
          const leftWall = isWall(rowA, colA - 1) && isWall(rowB, colB - 1);
          const rightWall = isWall(rowA, colA + 1) && isWall(rowB, colB + 1);
          if (leftWall || rightWall) {
            return true;
          }
        }
      }
    }
    return false;
  }

  function containsStaticDeadlock(boxesSet, targetSet, caches) {
    if (!boxesSet || boxesSet.size === 0) {
      return false;
    }
    const lookupCaches = caches || buildRowTargetPresence();
    for (const key of boxesSet) {
      const { row, col } = decodeKey(key);
      if (isStaticDeadlockForBox(row, col, boxesSet, targetSet, lookupCaches)) {
        return true;
      }
    }
    if (hasPairDeadlock(boxesSet, targetSet)) {
      return true;
    }
    return false;
  }

  function legalInverseActions(player, boxesSet, caches) {
    const actions = [];
    const blocked = boxesSet;
    for (const direction of DIRECTIONS) {
      const nextRow = player.row + direction.row;
      const nextCol = player.col + direction.col;
      const nextKey = keyFor(nextRow, nextCol);
      if (isWalkable(nextRow, nextCol) && !blocked.has(nextKey)) {
        actions.push({ type: 'move', row: nextRow, col: nextCol });
      }
      const pullRow = player.row + direction.row;
      const pullCol = player.col + direction.col;
      const pullKey = keyFor(pullRow, pullCol);
      const retreatRow = player.row - direction.row;
      const retreatCol = player.col - direction.col;
      const retreatKey = keyFor(retreatRow, retreatCol);
      if (!isWalkable(retreatRow, retreatCol)) {
        continue;
      }
      if (boxesSet.has(retreatKey)) {
        continue;
      }
      if (!boxesSet.has(pullKey)) {
        continue;
      }
      if (!isWalkable(pullRow, pullCol)) {
        continue;
      }
      const newBoxKey = keyFor(player.row, player.col);
      if (boxesSet.has(newBoxKey)) {
        continue;
      }
      const tentativeBoxes = new Set(boxesSet);
      tentativeBoxes.delete(pullKey);
      tentativeBoxes.add(newBoxKey);
      if (containsStaticDeadlock(tentativeBoxes, state.targetKeys, caches)) {
        continue;
      }
      actions.push({
        type: 'pull',
        boxFrom: pullKey,
        boxTo: newBoxKey,
        playerRow: retreatRow,
        playerCol: retreatCol
      });
    }
    return actions;
  }

  function applyInverseAction(player, boxesSet, action) {
    if (!action) {
      return { player, boxes: new Set(boxesSet) };
    }
    if (action.type === 'move') {
      return {
        player: { row: action.row, col: action.col },
        boxes: new Set(boxesSet)
      };
    }
    if (action.type === 'pull') {
      const updatedBoxes = new Set(boxesSet);
      updatedBoxes.delete(action.boxFrom);
      updatedBoxes.add(action.boxTo);
      return {
        player: { row: action.playerRow, col: action.playerCol },
        boxes: updatedBoxes
      };
    }
    return { player, boxes: new Set(boxesSet) };
  }

  function solveForwardMinPushes(startPlayer, startBoxes, targetSet, options = {}) {
    if (!startPlayer || !startBoxes || !targetSet || targetSet.size === 0) {
      return null;
    }
    const timeLimitMs = clampNumber(options?.timeLimitMs, 200, 8000, 2000);
    const startTime = Date.now();
    const targetCount = targetSet.size;
    const targetArray = Array.from(targetSet, decodeKeyToTuple);
    const caches = buildRowTargetPresence();

    const startBoxesKey = encodeBoxes(startBoxes);
    const startState = {
      playerRow: startPlayer.row,
      playerCol: startPlayer.col,
      boxes: new Set(startBoxes),
      boxesKey: startBoxesKey,
      pushes: 0,
      steps: 0,
      heuristic: computeMinimalMatchingDistance(Array.from(startBoxes, decodeKeyToTuple), targetArray)
    };

    const open = [startState];
    const visited = new Map();
    let expandedStates = 0;
    let generatedTransitions = 0;

    while (open.length > 0) {
      if (Date.now() - startTime > timeLimitMs) {
        return null;
      }
      let bestIndex = 0;
      for (let index = 1; index < open.length; index += 1) {
        if (open[index].pushes + open[index].heuristic < open[bestIndex].pushes + open[bestIndex].heuristic) {
          bestIndex = index;
        }
      }
      const current = open.splice(bestIndex, 1)[0];
      const playerKey = `${current.playerRow},${current.playerCol}`;
      const visitKey = `${current.boxesKey}@${playerKey}`;
      const bestPushes = visited.get(visitKey);
      if (bestPushes != null && bestPushes <= current.pushes) {
        continue;
      }
      visited.set(visitKey, current.pushes);

      if (countBoxesOnTargets(current.boxes, targetSet) === targetCount) {
        return {
          pushes: current.pushes,
          steps: current.steps,
          expandedStates,
          generatedTransitions
        };
      }

      expandedStates += 1;

      const blocked = new Set(current.boxes);
      const distances = computeReachableWithDistances(current.playerRow, current.playerCol, blocked);

      for (const boxKey of current.boxes) {
        const { row: boxRow, col: boxCol } = decodeKey(boxKey);
        for (const direction of DIRECTIONS) {
          const targetRow = boxRow + direction.row;
          const targetCol = boxCol + direction.col;
          const targetKey = keyFor(targetRow, targetCol);
          if (!isWalkable(targetRow, targetCol)) {
            continue;
          }
          if (blocked.has(targetKey)) {
            continue;
          }
          const pushRow = boxRow - direction.row;
          const pushCol = boxCol - direction.col;
          const pushKey = keyFor(pushRow, pushCol);
          const distanceToPush = distances.get(pushKey);
          if (distanceToPush == null) {
            continue;
          }
          const newBoxes = new Set(current.boxes);
          newBoxes.delete(boxKey);
          newBoxes.add(targetKey);
          if (containsStaticDeadlock(newBoxes, targetSet, caches)) {
            continue;
          }
          generatedTransitions += 1;
          const newPlayerRow = boxRow;
          const newPlayerCol = boxCol;
          const newSteps = current.steps + distanceToPush + 1;
          const newPushes = current.pushes + 1;
          const newBoxesKey = encodeBoxes(newBoxes);
          const heuristic = computeMinimalMatchingDistance(Array.from(newBoxes, decodeKeyToTuple), targetArray);
          open.push({
            playerRow: newPlayerRow,
            playerCol: newPlayerCol,
            boxes: newBoxes,
            boxesKey: newBoxesKey,
            pushes: newPushes,
            steps: newSteps,
            heuristic
          });
        }
      }
    }

    return null;
  }

  function computeSolverMetrics(result) {
    if (!result) {
      return null;
    }
    const { pushes, steps, expandedStates, generatedTransitions } = result;
    const branching = expandedStates > 0 ? generatedTransitions / expandedStates : 0;
    return {
      minPushes: pushes,
      pathLength: steps,
      exploredStates: expandedStates,
      branching
    };
  }

  function fitsDifficulty(metrics, difficultyHint, totalBoxes) {
    if (!metrics) {
      return false;
    }
    const flooredHint = Math.floor(difficultyHint);
    const cappedHint = Number.isFinite(flooredHint) ? clampInt(flooredHint, 0, 120, flooredHint) : 0;
    const baseline = Math.max(4, cappedHint, totalBoxes * 3);
    const minPushTarget = Math.max(4, Math.floor(baseline * 0.3));
    const maxPushTarget = Math.max(minPushTarget + 4, Math.floor(baseline * 1.8));
    if (metrics.minPushes < minPushTarget) {
      return false;
    }
    if (metrics.minPushes > maxPushTarget) {
      return false;
    }
    if (metrics.pathLength < metrics.minPushes * 2) {
      return false;
    }
    return true;
  }

  function chooseRandomEntry(array) {
    if (!Array.isArray(array) || array.length === 0) {
      return null;
    }
    const index = randomInt(0, array.length - 1);
    return array[index];
  }

  function sanitizeLevels(rawLevels) {
    if (!Array.isArray(rawLevels)) {
      return [];
    }
    const sanitized = [];
    rawLevels.forEach(entry => {
      const layoutSource = Array.isArray(entry?.layout)
        ? entry.layout
        : Array.isArray(entry?.map)
          ? entry.map
          : null;
      if (!layoutSource) {
        return;
      }
      const rows = layoutSource
        .map(row => (typeof row === 'string' ? row.replace(/\r/g, '') : ''))
        .filter(row => row.length > 0);
      if (!rows.length) {
        return;
      }
      const width = rows.reduce((acc, row) => Math.max(acc, row.length), 0);
      if (width < 3 || rows.length < 3) {
        return;
      }
      const normalizedRows = new Array(rows.length);
      const targetSet = new Set();
      const targets = [];
      const boxSet = new Set();
      const boxes = [];
      const walkableCandidates = [];
      let player = null;

      for (let row = 0; row < rows.length; row += 1) {
        const sourceRow = rows[row];
        let normalizedRow = '';
        for (let col = 0; col < width; col += 1) {
          const char = sourceRow[col] ?? '#';
          let walkable = false;
          let markTarget = false;
          let markBox = false;
          let markPlayer = false;
          switch (char) {
            case '#':
              break;
            case '.':
            case 'o':
            case 'O':
            case 'T':
            case 't':
              walkable = true;
              markTarget = true;
              break;
            case '+':
              walkable = true;
              markTarget = true;
              markPlayer = true;
              break;
            case '*':
            case 'X':
            case 'x':
              walkable = true;
              markTarget = true;
              markBox = true;
              break;
            case '$':
            case 'B':
            case 'b':
              walkable = true;
              markBox = true;
              break;
            case '@':
            case 'P':
            case 'p':
              walkable = true;
              markPlayer = true;
              break;
            case ' ':
            case '-':
            case '_':
              walkable = true;
              break;
            default:
              if (typeof char === 'string' && char.trim() === '') {
                walkable = true;
              } else {
                walkable = true;
              }
          }
          if (walkable) {
            normalizedRow += '.';
            walkableCandidates.push([row, col]);
            if (markTarget) {
              const targetKey = keyFor(row, col);
              if (!targetSet.has(targetKey)) {
                targetSet.add(targetKey);
                targets.push([row, col]);
              }
            }
            if (markBox) {
              const boxKey = keyFor(row, col);
              if (!boxSet.has(boxKey)) {
                boxSet.add(boxKey);
                boxes.push([row, col]);
              }
            }
            if (markPlayer && !player) {
              player = { row, col };
            }
          } else {
            normalizedRow += '#';
          }
        }
        normalizedRows[row] = normalizedRow;
      }

      if (!player) {
        for (const [row, col] of walkableCandidates) {
          const key = keyFor(row, col);
          if (!boxSet.has(key)) {
            player = { row, col };
            break;
          }
        }
      }

      if (!player || !targets.length || !boxes.length || boxes.length !== targets.length) {
        return;
      }

      sanitized.push({
        name: typeof entry?.name === 'string' && entry.name.trim() ? entry.name.trim() : null,
        map: normalizedRows,
        targets,
        boxes,
        playerStart: [player.row, player.col]
      });
    });
    return sanitized;
  }

  function sanitizeConfig(rawConfig) {
    const fallbackLevels = sanitizeLevels(DEFAULT_CONFIG.levels);
    const fallbackGenerator = sanitizeGeneratorConfig(DEFAULT_CONFIG.generator);
    if (!rawConfig || typeof rawConfig !== 'object') {
      return {
        shuffleMoves: { ...DEFAULT_CONFIG.shuffleMoves },
        generator: fallbackGenerator,
        map: Array.from(DEFAULT_CONFIG.map),
        targets: Array.from(DEFAULT_CONFIG.targets),
        playerStart: Array.from(DEFAULT_CONFIG.playerStart),
        levels: fallbackLevels
      };
    }

    const hasLevelsProperty = Object.prototype.hasOwnProperty.call(rawConfig, 'levels');
    const rawLevels = hasLevelsProperty ? rawConfig.levels : DEFAULT_CONFIG.levels;
    let normalizedLevels = sanitizeLevels(rawLevels);
    if (!normalizedLevels.length) {
      normalizedLevels = fallbackLevels;
    }

    const generator = sanitizeGeneratorConfig(rawConfig.generator);

    const mapSource = Array.isArray(rawConfig.map) ? rawConfig.map : null;
    let normalizedMap = Array.isArray(mapSource)
      ? mapSource
          .map(row => (typeof row === 'string' ? row : ''))
          .filter(row => row.length > 0)
      : null;

    const width = normalizedMap && normalizedMap.length ? normalizedMap[0].length : 0;
    const mapValid = normalizedMap
      && normalizedMap.length >= 3
      && normalizedMap.every(row => row.length === width && width >= 3);

    if (!mapValid) {
      normalizedMap = Array.from(DEFAULT_CONFIG.map);
    }

    const extractedTargets = [];
    normalizedMap = normalizedMap.map((row, rowIndex) => {
      let resultRow = row;
      for (let col = 0; col < row.length; col += 1) {
        if (row[col] === 'T') {
          extractedTargets.push([rowIndex, col]);
          resultRow = `${resultRow.slice(0, col)}.${resultRow.slice(col + 1)}`;
        }
      }
      return resultRow;
    });

    let targets = [];
    if (Array.isArray(rawConfig.targets) && rawConfig.targets.length) {
      targets = rawConfig.targets
        .map(entry => (Array.isArray(entry) && entry.length >= 2 ? [Number(entry[0]), Number(entry[1])] : null))
        .filter(entry => Number.isInteger(entry?.[0]) && Number.isInteger(entry?.[1]));
    }
    if (!targets.length) {
      targets = extractedTargets;
    }
    if (!targets.length) {
      targets = Array.from(DEFAULT_CONFIG.targets);
    }

    let shuffleMin = clampInt(rawConfig?.shuffleMoves?.min, 8, 800, DEFAULT_CONFIG.shuffleMoves.min);
    let shuffleMax = clampInt(rawConfig?.shuffleMoves?.max, shuffleMin, 1200, DEFAULT_CONFIG.shuffleMoves.max);
    if (shuffleMax < shuffleMin) {
      shuffleMax = shuffleMin;
    }

    const playerCandidate = Array.isArray(rawConfig.playerStart) && rawConfig.playerStart.length >= 2
      ? [Number(rawConfig.playerStart[0]), Number(rawConfig.playerStart[1])]
      : Array.from(DEFAULT_CONFIG.playerStart);

    return {
      shuffleMoves: { min: shuffleMin, max: shuffleMax },
      generator,
      map: normalizedMap,
      targets,
      playerStart: playerCandidate,
      levels: normalizedLevels
    };
  }

  function applyDynamicLayout(definition) {
    if (!definition || !Array.isArray(definition.map) || !definition.map.length) {
      return null;
    }
    const rows = definition.map
      .map(row => (typeof row === 'string' ? row.replace(/\r/g, '') : ''))
      .filter(row => row.length > 0);
    const height = rows.length;
    const width = rows[0]?.length || 0;
    if (height <= 0 || width <= 0) {
      return null;
    }
    if (!rows.every(row => row.length === width)) {
      return null;
    }

    state.height = height;
    state.width = width;
    state.tiles = new Array(height);
    state.targetKeys = new Set();
    state.currentMetrics = null;

    for (let row = 0; row < height; row += 1) {
      const rowTiles = new Array(width);
      const rowString = rows[row];
      for (let col = 0; col < width; col += 1) {
        const wall = rowString[col] === '#';
        rowTiles[col] = { wall, target: false };
      }
      state.tiles[row] = rowTiles;
    }

    const sanitizedTargets = [];
    const targetSource = Array.isArray(definition.targets) ? definition.targets : [];
    targetSource.forEach(target => {
      const rawRow = Array.isArray(target) ? target[0] : target?.row;
      const rawCol = Array.isArray(target) ? target[1] : target?.col;
      const row = normalizeCoordinate(rawRow, height - 1);
      const col = normalizeCoordinate(rawCol, width - 1);
      if (row == null || col == null) {
        return;
      }
      if (state.tiles[row][col].wall) {
        return;
      }
      const key = keyFor(row, col);
      if (state.targetKeys.has(key)) {
        return;
      }
      state.tiles[row][col].target = true;
      state.targetKeys.add(key);
      sanitizedTargets.push([row, col]);
    });

    const rawPlayer = definition.playerStart;
    let startRow = null;
    let startCol = null;
    if (Array.isArray(rawPlayer)) {
      startRow = normalizeCoordinate(rawPlayer[0], height - 1);
      startCol = normalizeCoordinate(rawPlayer[1], width - 1);
    } else if (rawPlayer && typeof rawPlayer === 'object') {
      const candidateRow = rawPlayer.row ?? rawPlayer[0];
      const candidateCol = rawPlayer.col ?? rawPlayer[1];
      startRow = normalizeCoordinate(candidateRow, height - 1);
      startCol = normalizeCoordinate(candidateCol, width - 1);
    }

    if (startRow == null || startCol == null || !isWalkable(startRow, startCol)) {
      if (sanitizedTargets.length > 0) {
        [startRow, startCol] = sanitizedTargets[0];
      } else {
        let fallback = null;
        outer:
        for (let row = 0; row < height; row += 1) {
          for (let col = 0; col < width; col += 1) {
            if (!state.tiles[row][col].wall) {
              fallback = [row, col];
              break outer;
            }
          }
        }
        if (fallback) {
          [startRow, startCol] = fallback;
        } else {
          startRow = 0;
          startCol = 0;
        }
      }
    }

    state.playerStart = { row: startRow, col: startCol };
    state.player = { row: startRow, col: startCol };
    state.boxes = new Set();

    return {
      map: rows,
      targets: sanitizedTargets,
      playerStart: { row: startRow, col: startCol }
    };
  }

  function loadLevel(levelIndex) {
    if (!Array.isArray(state.levelDefinitions) || !state.levelDefinitions.length) {
      return false;
    }
    const maxIndex = state.levelDefinitions.length - 1;
    const clampedIndex = clampInt(levelIndex, 0, maxIndex, 0);
    const level = state.levelDefinitions[clampedIndex];
    if (!level) {
      return false;
    }

    state.levelIndex = clampedIndex;
    state.level = clampInt(clampedIndex + 1, 1, 9999, clampedIndex + 1);
    state.currentLevelLayout = null;
    state.height = level.map.length;
    state.width = level.map[0]?.length || 0;
    state.tiles = new Array(state.height);
    state.targetKeys = new Set();

    for (let row = 0; row < state.height; row += 1) {
      const rowTiles = new Array(state.width);
      const rowString = level.map[row] || '';
      for (let col = 0; col < state.width; col += 1) {
        const wall = rowString[col] === '#';
        rowTiles[col] = { wall, target: false };
      }
      state.tiles[row] = rowTiles;
    }

    level.targets.forEach(target => {
      if (!Array.isArray(target) || target.length < 2) {
        return;
      }
      const row = clampInt(target[0], 0, state.height - 1, 0);
      const col = clampInt(target[1], 0, state.width - 1, 0);
      if (!isWall(row, col)) {
        state.tiles[row][col].target = true;
        state.targetKeys.add(keyFor(row, col));
      }
    });

    const startRow = clampInt(level.playerStart?.[0], 0, state.height - 1, 0);
    const startCol = clampInt(level.playerStart?.[1], 0, state.width - 1, 0);
    if (isWalkable(startRow, startCol)) {
      state.playerStart = { row: startRow, col: startCol };
    } else if (state.targetKeys.size > 0) {
      const [firstTarget] = Array.from(state.targetKeys);
      const [row, col] = firstTarget.split(',').map(Number);
      state.playerStart = { row, col };
    } else {
      let fallback = null;
      for (let row = 0; row < state.height; row += 1) {
        for (let col = 0; col < state.width; col += 1) {
          if (isWalkable(row, col)) {
            fallback = { row, col };
            break;
          }
        }
        if (fallback) {
          break;
        }
      }
      state.playerStart = fallback || { row: 0, col: 0 };
    }

    state.player = { row: state.playerStart.row, col: state.playerStart.col };
    state.boxes = new Set();
    level.boxes.forEach(box => {
      if (!Array.isArray(box) || box.length < 2) {
        return;
      }
      const row = clampInt(box[0], 0, state.height - 1, 0);
      const col = clampInt(box[1], 0, state.width - 1, 0);
      if (isWalkable(row, col)) {
        state.boxes.add(keyFor(row, col));
      }
    });

    state.moveCount = 0;
    state.pushCount = 0;
    state.ready = false;
    state.solved = false;
    state.initialSnapshot = null;
    return true;
  }

  function applyConfig(config) {
    const normalized = sanitizeConfig(config);
    state.config = normalized;
    state.configSignature = computeConfigSignature(normalized);
    state.levelDefinitions = Array.isArray(normalized.levels) ? normalized.levels : [];
    state.currentLevelLayout = null;
    clearCompletionEffects();

    if (state.levelDefinitions.length > 0) {
      loadLevel(0);
      return;
    }

    state.levelDefinitions = [];
    state.levelIndex = 0;
    const staticLayout = applyDynamicLayout({
      map: normalized.map,
      targets: normalized.targets,
      playerStart: normalized.playerStart
    });
    if (!staticLayout) {
      state.height = 0;
      state.width = 0;
      state.tiles = [];
      state.targetKeys = new Set();
      state.playerStart = { row: 0, col: 0 };
      state.player = { row: 0, col: 0 };
      state.boxes = new Set();
    }
    state.currentLevelLayout = null;
    state.moveCount = 0;
    state.pushCount = 0;
    state.ready = false;
    state.solved = false;
    state.initialSnapshot = null;
  }

  function initElements() {
    const board = document.getElementById('sokobanBoard');
    if (!board) {
      return false;
    }
    const restart = document.getElementById('sokobanRestartButton');

    state.elements = {
      board,
      status: null,
      moves: null,
      pushes: null,
      level: null,
      restart
    };

    board.setAttribute('role', 'grid');
    board.setAttribute('aria-live', 'polite');

    if (restart) {
      restart.addEventListener('click', () => {
        prepareNewPuzzle({ randomizeLevel: true, force: true });
      });
    }

    board.addEventListener('keydown', handleBoardKeydown);
    board.addEventListener('pointerdown', handlePointerDown);
    board.addEventListener('pointermove', handlePointerMove);
    board.addEventListener('pointerup', handlePointerUp);
    board.addEventListener('pointercancel', handlePointerCancel);
    board.addEventListener('pointerleave', handlePointerCancel);

    window.addEventListener('keydown', handleGlobalKeydown, { passive: false });

    return true;
  }

  function buildBoardCells() {
    const board = state.elements?.board;
    if (!board) {
      return;
    }
    board.innerHTML = '';
    board.style.setProperty('--sokoban-width', String(state.width));
    board.setAttribute('aria-rowcount', String(state.height));
    board.setAttribute('aria-colcount', String(state.width));

    state.cellElements = new Array(state.height);
    for (let row = 0; row < state.height; row += 1) {
      const rowElements = new Array(state.width);
      for (let col = 0; col < state.width; col += 1) {
        const cell = document.createElement('div');
        cell.className = 'sokoban__cell';
        cell.setAttribute('role', 'gridcell');
        cell.dataset.row = String(row);
        cell.dataset.col = String(col);
        rowElements[col] = cell;
        board.appendChild(cell);
      }
      state.cellElements[row] = rowElements;
    }
  }

  function clearCompletionEffects() {
    if (state.celebrationTimer != null) {
      window.clearTimeout(state.celebrationTimer);
      state.celebrationTimer = null;
    }
    if (state.autoAdvanceTimer != null) {
      window.clearTimeout(state.autoAdvanceTimer);
      state.autoAdvanceTimer = null;
    }
    const board = state.elements?.board;
    if (board) {
      board.classList.remove('sokoban__board--celebrate');
    }
  }

  function takeSnapshot() {
    return {
      player: { row: state.player.row, col: state.player.col },
      boxes: Array.from(state.boxes).map(entry => {
        const [row, col] = entry.split(',').map(Number);
        return [row, col];
      })
    };
  }

  function applySnapshot(snapshot) {
    if (!snapshot || typeof snapshot !== 'object') {
      return false;
    }
    const boxes = new Set();
    if (Array.isArray(snapshot.boxes)) {
      for (const entry of snapshot.boxes) {
        if (!Array.isArray(entry) || entry.length < 2) {
          continue;
        }
        const row = Number(entry[0]);
        const col = Number(entry[1]);
        if (!Number.isInteger(row) || !Number.isInteger(col) || isWall(row, col)) {
          continue;
        }
        boxes.add(keyFor(row, col));
      }
    }
    if (boxes.size !== state.targetKeys.size) {
      return false;
    }
    const playerRow = Number(snapshot.player?.row);
    const playerCol = Number(snapshot.player?.col);
    if (!Number.isInteger(playerRow) || !Number.isInteger(playerCol) || !isWalkable(playerRow, playerCol)) {
      return false;
    }
    state.boxes = boxes;
    state.player = { row: playerRow, col: playerCol };
    return true;
  }

  function scheduleAutosave() {
    if (state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
      state.autosaveTimer = null;
    }
    state.autosaveTimer = window.setTimeout(() => {
      state.autosaveTimer = null;
      persistAutosaveNow();
    }, AUTOSAVE_DELAY_MS);
  }

  function flushAutosave() {
    if (state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
      state.autosaveTimer = null;
    }
    persistAutosaveNow();
  }

  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const autosave = window.ArcadeAutosave;
    if (!autosave || typeof autosave !== 'object') {
      return null;
    }
    if (typeof autosave.get !== 'function' || typeof autosave.set !== 'function') {
      return null;
    }
    return autosave;
  }

  function persistAutosaveNow() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    const payload = buildSavePayload();
    if (payload == null) {
      if (typeof autosave.clear === 'function') {
        try {
          autosave.clear(GAME_ID);
        } catch (error) {
          // Ignore autosave clearance errors
        }
      }
      return;
    }
    try {
      autosave.set(GAME_ID, payload);
    } catch (error) {
      // Ignore autosave persistence errors
    }
  }

  function readAutosave() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return null;
    }
    try {
      return autosave.get(GAME_ID);
    } catch (error) {
      return null;
    }
  }

  function buildSavePayload() {
    if (!state.initialSnapshot) {
      return null;
    }
    const boxes = Array.from(state.boxes).map(entry => {
      const [row, col] = entry.split(',').map(Number);
      return [row, col];
    });
    const initialBoxes = Array.from(state.initialSnapshot.boxes || []);
    const payload = {
      version: SAVE_VERSION,
      configSignature: state.configSignature,
      level: clampInt(state.level, 1, 9999, 1),
      moveCount: clampInt(state.moveCount, 0, 999999, 0),
      pushCount: clampInt(state.pushCount, 0, 999999, 0),
      solved: Boolean(state.solved),
      player: { row: state.player.row, col: state.player.col },
      boxes,
      initial: {
        player: state.initialSnapshot.player,
        boxes: initialBoxes
      }
    };
    if (state.currentLevelLayout) {
      payload.procedural = {
        map: Array.isArray(state.currentLevelLayout.map)
          ? state.currentLevelLayout.map.map(row => String(row))
          : [],
        targets: Array.isArray(state.currentLevelLayout.targets)
          ? state.currentLevelLayout.targets.map(coords => [coords[0], coords[1]])
          : [],
        playerStart: [
          state.currentLevelLayout.playerStart?.row ?? state.playerStart.row,
          state.currentLevelLayout.playerStart?.col ?? state.playerStart.col
        ]
      };
    }
    return payload;
  }

  function restoreFromSave(payload) {
    if (!payload || typeof payload !== 'object') {
      return false;
    }
    if (payload.version !== SAVE_VERSION) {
      return false;
    }
    if (payload.configSignature !== state.configSignature) {
      return false;
    }
    let fallbackSnapshot = null;
    if (state.levelDefinitions.length > 0) {
      const totalLevels = state.levelDefinitions.length;
      const targetIndex = clampInt(payload.level - 1, 0, totalLevels - 1, 0);
      const loaded = loadLevel(targetIndex);
      if (!loaded) {
        return false;
      }
      buildBoardCells();
      fallbackSnapshot = takeSnapshot();
    } else if (payload.procedural && state.config?.generator) {
      const layout = {
        map: Array.isArray(payload.procedural.map) ? payload.procedural.map : [],
        targets: Array.isArray(payload.procedural.targets) ? payload.procedural.targets : [],
        playerStart: payload.procedural.playerStart
      };
      const appliedLayout = applyDynamicLayout(layout);
      if (!appliedLayout) {
        return false;
      }
      state.currentLevelLayout = {
        map: appliedLayout.map.slice(),
        targets: appliedLayout.targets.map(coords => [coords[0], coords[1]]),
        playerStart: { row: appliedLayout.playerStart.row, col: appliedLayout.playerStart.col }
      };
      buildBoardCells();
      fallbackSnapshot = takeSnapshot();
    } else if (state.config?.generator) {
      return false;
    } else {
      buildBoardCells();
      fallbackSnapshot = takeSnapshot();
    }

    const applied = applySnapshot({
      player: payload.player,
      boxes: payload.boxes
    });
    if (!applied) {
      return false;
    }
    state.moveCount = clampInt(payload.moveCount, 0, 999999, 0);
    state.pushCount = clampInt(payload.pushCount, 0, 999999, 0);
    state.level = clampInt(payload.level, 1, 9999, 1);
    const solvedFromSave = Boolean(payload.solved) && checkSolved();
    state.solved = solvedFromSave;
    state.ready = !state.solved;
    if (state.solved) {
      setStatus(
        'scripts.arcade.sokoban.status.completed',
        'Niveau terminé !',
        {
          level: formatIntegerLocalized(state.level),
          moves: formatIntegerLocalized(state.moveCount),
          pushes: formatIntegerLocalized(state.pushCount)
        },
        { rememberBase: true }
      );
    } else {
      setStatus(
        'scripts.arcade.sokoban.status.ready',
        'Niveau prêt.',
        null,
        { rememberBase: true }
      );
    }
    if (payload.initial) {
      const validInitial = applySnapshot(payload.initial);
      if (validInitial) {
        state.initialSnapshot = takeSnapshot();
        applySnapshot({
          player: payload.player,
          boxes: payload.boxes
        });
      } else if (fallbackSnapshot) {
        state.initialSnapshot = fallbackSnapshot;
        applySnapshot({
          player: payload.player,
          boxes: payload.boxes
        });
      } else {
        state.initialSnapshot = takeSnapshot();
      }
    } else if (fallbackSnapshot) {
      state.initialSnapshot = fallbackSnapshot;
    } else {
      state.initialSnapshot = takeSnapshot();
    }
    if (solvedFromSave) {
      handleLevelCompleted({ skipRewards: true, skipCelebration: true });
    }
    return true;
  }

  function updateHud() {
    if (state.elements?.moves) {
      state.elements.moves.textContent = formatIntegerLocalized(state.moveCount);
    }
    if (state.elements?.pushes) {
      state.elements.pushes.textContent = formatIntegerLocalized(state.pushCount);
    }
    if (state.elements?.level) {
      state.elements.level.textContent = formatIntegerLocalized(state.level);
    }
  }

  function setStatus(key, fallback, params, options = {}) {
    const statusElement = state.elements?.status;
    if (!statusElement) {
      return;
    }
    const message = translateText(key, fallback, params);
    statusElement.textContent = message;
    if (options.rememberBase) {
      state.baseStatus = { key, fallback, params };
    }
    if (state.statusTimeout != null) {
      window.clearTimeout(state.statusTimeout);
      state.statusTimeout = null;
    }
    if (options.transient === true && state.baseStatus) {
      state.statusTimeout = window.setTimeout(() => {
        state.statusTimeout = null;
        const base = state.baseStatus;
        setStatus(base.key, base.fallback, base.params);
      }, BLOCKED_STATUS_RESET_MS);
    }
  }

  function refreshBaseStatus() {
    if (state.baseStatus) {
      setStatus(state.baseStatus.key, state.baseStatus.fallback, state.baseStatus.params);
    }
  }

  function renderBoard() {
    const board = state.elements?.board;
    if (!board || !Array.isArray(state.cellElements) || !state.cellElements.length) {
      return;
    }
    for (let row = 0; row < state.height; row += 1) {
      for (let col = 0; col < state.width; col += 1) {
        const cell = state.cellElements[row]?.[col];
        if (!cell) {
          continue;
        }
        const tile = state.tiles[row][col];
        const key = keyFor(row, col);
        const hasBox = state.boxes.has(key);
        const isPlayer = state.player.row === row && state.player.col === col;
        let className = 'sokoban__cell';
        if (tile.wall) {
          className += ' sokoban__cell--wall';
        } else {
          if (tile.target) {
            className += ' sokoban__cell--target';
          }
          if (hasBox) {
            className += ' sokoban__cell--box';
          }
          if (isPlayer) {
            className += ' sokoban__cell--player';
          }
        }
        cell.className = className;
        const baseKey = tile.wall
          ? 'index.sections.sokoban.cell.wall'
          : tile.target
            ? 'index.sections.sokoban.cell.target'
            : 'index.sections.sokoban.cell.floor';
        const occupantKey = isPlayer
          ? 'index.sections.sokoban.occupant.player'
          : hasBox
            ? 'index.sections.sokoban.occupant.box'
            : null;
        const baseText = translateText(baseKey, tile.wall ? 'Mur' : tile.target ? 'Cible' : 'Sol');
        if (occupantKey) {
          const occupantText = translateText(occupantKey, isPlayer ? 'Manutentionnaire' : 'Caisse');
          const combined = translateText(
            'scripts.arcade.sokoban.cell.combined',
            '{occupant} sur {base}',
            { occupant: occupantText, base: baseText }
          );
          cell.setAttribute('aria-label', combined);
        } else {
          cell.setAttribute('aria-label', baseText);
        }
      }
    }
  }

  function triggerBoardCelebration() {
    const board = state.elements?.board;
    if (!board) {
      return;
    }
    board.classList.remove('sokoban__board--celebrate');
    void board.offsetWidth;
    board.classList.add('sokoban__board--celebrate');
    if (state.celebrationTimer != null) {
      window.clearTimeout(state.celebrationTimer);
      state.celebrationTimer = null;
    }
    state.celebrationTimer = window.setTimeout(() => {
      state.celebrationTimer = null;
      board.classList.remove('sokoban__board--celebrate');
    }, COMPLETION_HIGHLIGHT_DURATION_MS);
  }

  function maybeAwardCompletionReward() {
    const tickets = Math.max(0, Math.floor(Number(COMPLETION_GACHA_REWARD.tickets) || 0));
    const chance = Number(COMPLETION_GACHA_REWARD.chance);
    if (!Number.isFinite(tickets) || tickets <= 0) {
      return;
    }
    if (!Number.isFinite(chance) || chance <= 0) {
      return;
    }
    if (Math.random() >= chance) {
      return;
    }
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      return;
    }
    let gained = 0;
    try {
      gained = awardGacha(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Sokoban: unable to grant gacha tickets', error);
      gained = 0;
    }
    if (!Number.isFinite(gained) || gained <= 0 || typeof showToast !== 'function') {
      return;
    }
    try {
      const message = translateText(
        'scripts.arcade.sokoban.rewards.gachaWin',
        'Ticket gacha obtenu ! (+{count})',
        { count: formatIntegerLocalized(gained) }
      );
      showToast(message);
    } catch (error) {
      console.warn('Sokoban: unable to display gacha reward toast', error);
    }
  }

  function scheduleAutoAdvance() {
    if (state.autoAdvanceTimer != null) {
      window.clearTimeout(state.autoAdvanceTimer);
      state.autoAdvanceTimer = null;
    }
    state.autoAdvanceTimer = window.setTimeout(() => {
      state.autoAdvanceTimer = null;
      prepareNewPuzzle({ randomizeLevel: true, force: true });
    }, AUTO_ADVANCE_DELAY_MS);
  }

  function handleLevelCompleted(options = {}) {
    const { skipRewards = false, skipCelebration = false } = options;
    state.ready = false;
    if (!skipCelebration) {
      triggerBoardCelebration();
    }
    if (!skipRewards) {
      maybeAwardCompletionReward();
    }
    if (state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
      state.autosaveTimer = null;
    }
    state.initialSnapshot = null;
    flushAutosave();
    scheduleAutoAdvance();
  }

  function checkSolved() {
    if (state.boxes.size !== state.targetKeys.size) {
      return false;
    }
    for (const key of state.targetKeys) {
      if (!state.boxes.has(key)) {
        return false;
      }
    }
    return true;
  }

  function attemptMove(deltaRow, deltaCol, options = {}) {
    const {
      countMove = true,
      announceBlocked = true,
      allowWhenSolved = false,
      skipAutosave = false,
      skipStatus = false,
      force = false,
      skipCompletion = false
    } = options;

    if (!allowWhenSolved && state.solved) {
      return false;
    }
    if (!state.ready && !force) {
      return false;
    }
    const nextRow = state.player.row + deltaRow;
    const nextCol = state.player.col + deltaCol;
    if (!isWalkable(nextRow, nextCol)) {
      if (announceBlocked && !skipStatus) {
        setStatus(
          'scripts.arcade.sokoban.status.blocked',
          'La caisse est bloquée dans cette direction.',
          null,
          { transient: true }
        );
      }
      return false;
    }
    const nextKey = keyFor(nextRow, nextCol);
    const hasBox = state.boxes.has(nextKey);
    if (hasBox) {
      const boxRow = nextRow + deltaRow;
      const boxCol = nextCol + deltaCol;
      if (!isWalkable(boxRow, boxCol)) {
        if (announceBlocked && !skipStatus) {
          setStatus(
            'scripts.arcade.sokoban.status.blocked',
            'La caisse est bloquée dans cette direction.',
            null,
            { transient: true }
          );
        }
        return false;
      }
      const boxKey = keyFor(boxRow, boxCol);
      if (state.boxes.has(boxKey)) {
        if (announceBlocked && !skipStatus) {
          setStatus(
            'scripts.arcade.sokoban.status.blocked',
            'La caisse est bloquée dans cette direction.',
            null,
            { transient: true }
          );
        }
        return false;
      }
      state.boxes.delete(nextKey);
      state.boxes.add(boxKey);
      if (countMove) {
        state.pushCount += 1;
      }
    }

    state.player = { row: nextRow, col: nextCol };
    if (countMove) {
      state.moveCount += 1;
    }

    const solvedNow = checkSolved();
    state.solved = solvedNow;
    if (solvedNow && !skipStatus) {
      setStatus(
        'scripts.arcade.sokoban.status.completed',
        'Niveau terminé !',
        {
          level: formatIntegerLocalized(state.level),
          moves: formatIntegerLocalized(state.moveCount),
          pushes: formatIntegerLocalized(state.pushCount)
        },
        { rememberBase: true }
      );
    } else if (!skipStatus && countMove) {
      setStatus(
        'scripts.arcade.sokoban.status.ready',
        'Niveau prêt.',
        null,
        { rememberBase: true }
      );
    }

    renderBoard();
    updateHud();
    if (solvedNow) {
      if (skipCompletion) {
        return true;
      }
      handleLevelCompleted();
    } else if (!skipAutosave) {
      scheduleAutosave();
    }
    return true;
  }

  function shuffleFromSolvedState() {
    if (state.targetKeys.size <= 0) {
      return false;
    }

    const requestedSteps = clampInt(
      randomInt(state.config.shuffleMoves.min, state.config.shuffleMoves.max),
      12,
      2400,
      120
    );

    const caches = buildRowTargetPresence();
    const totalBoxes = state.targetKeys.size;
    const pullBias = 0.7;

    let player = { row: state.playerStart.row, col: state.playerStart.col };
    let boxes = new Set(state.targetKeys);

    const initialReachable = computeReachableCells(player.row, player.col, boxes);
    if (!initialReachable.has(keyFor(player.row, player.col))) {
      const alternatives = Array.from(initialReachable).filter(entry => !boxes.has(entry));
      const randomKey = chooseRandomEntry(alternatives);
      if (!randomKey) {
        return false;
      }
      const coords = decodeKey(randomKey);
      player = { row: coords.row, col: coords.col };
    }

    let pulls = 0;
    let stagnation = 0;
    const maxStagnation = requestedSteps * 18;

    for (let step = 0; step < requestedSteps && stagnation < maxStagnation; step += 1) {
      const actions = legalInverseActions(player, boxes, caches);
      if (!actions.length) {
        stagnation += 1;
        const reachable = computeReachableCells(player.row, player.col, boxes);
        const alternatives = Array.from(reachable).filter(entry => !boxes.has(entry));
        const randomKey = chooseRandomEntry(alternatives);
        if (randomKey) {
          const coords = decodeKey(randomKey);
          player = { row: coords.row, col: coords.col };
        }
        continue;
      }

      const pullActions = actions.filter(action => action.type === 'pull');
      let chosen = null;
      if (pullActions.length && (Math.random() < pullBias || pullActions.length === actions.length)) {
        chosen = chooseRandomEntry(pullActions);
      } else {
        chosen = chooseRandomEntry(actions);
      }

      if (!chosen) {
        stagnation += 1;
        continue;
      }

      const result = applyInverseAction(player, boxes, chosen);
      player = result.player;
      boxes = result.boxes;
      stagnation = 0;
      if (chosen.type === 'pull') {
        pulls += 1;
      }
    }

    const requiredPulls = Math.max(4, Math.floor(requestedSteps * 0.4));
    if (pulls < requiredPulls) {
      return false;
    }

    if (containsStaticDeadlock(boxes, state.targetKeys, caches)) {
      return false;
    }

    const boxesOnTargets = countBoxesOnTargets(boxes, state.targetKeys);
    if (boxesOnTargets / totalBoxes > 0.5) {
      return false;
    }

    const averageDistance = computeAverageBoxGoalDistance(boxes, state.targetKeys);
    if (!Number.isFinite(averageDistance) || averageDistance < 3) {
      return false;
    }

    if (!isWalkable(player.row, player.col) || boxes.has(keyFor(player.row, player.col))) {
      const reachable = computeReachableCells(player.row, player.col, boxes);
      const alternatives = Array.from(reachable).filter(entry => !boxes.has(entry));
      const randomKey = chooseRandomEntry(alternatives);
      if (!randomKey) {
        return false;
      }
      const coords = decodeKey(randomKey);
      player = { row: coords.row, col: coords.col };
    }

    const solverResult = solveForwardMinPushes(player, boxes, state.targetKeys, { timeLimitMs: 2500 });
    const metrics = computeSolverMetrics(solverResult);
    if (!metrics) {
      return false;
    }
    const difficultyHint = Math.max(pulls, Math.floor(requestedSteps * 0.25));
    if (!fitsDifficulty(metrics, difficultyHint, totalBoxes)) {
      return false;
    }

    state.player = { row: player.row, col: player.col };
    state.boxes = boxes;
    state.currentMetrics = metrics;

    return true;
  }

  function resetToInitialState() {
    if (!state.initialSnapshot) {
      return;
    }
    const applied = applySnapshot(state.initialSnapshot);
    if (!applied) {
      return;
    }
    clearCompletionEffects();
    state.moveCount = 0;
    state.pushCount = 0;
    state.solved = false;
    state.ready = true;
    setStatus(
      'scripts.arcade.sokoban.status.ready',
      'Niveau prêt.',
      null,
      { rememberBase: true }
    );
    renderBoard();
    updateHud();
    scheduleAutosave();
  }

  function selectRandomLevelIndex(excludeIndex) {
    const total = Array.isArray(state.levelDefinitions) ? state.levelDefinitions.length : 0;
    if (total <= 0) {
      return 0;
    }
    const sanitizedExclude = Number.isInteger(excludeIndex) ? excludeIndex : -1;
    let index = Math.floor(Math.random() * total);
    if (total > 1 && sanitizedExclude >= 0 && sanitizedExclude < total) {
      let guard = 0;
      while (index === sanitizedExclude && guard < 6) {
        index = Math.floor(Math.random() * total);
        guard += 1;
      }
      if (index === sanitizedExclude) {
        index = (sanitizedExclude + 1) % total;
      }
    }
    return index;
  }

  function prepareProceduralLevel(options = {}) {
    const { randomizeLevel = false } = options;
    const generator = state.config?.generator;
    if (!generator) {
      return false;
    }
    const attempts = clampInt(generator.attempts, 3, 200, DEFAULT_CONFIG.generator.attempts);
    const baseLevel = clampInt(state.level, 1, 9999, 1);

    for (let attempt = 0; attempt < attempts; attempt += 1) {
      const layout = generateProceduralLayout(generator);
      if (!layout) {
        continue;
      }
      const applied = applyDynamicLayout(layout);
      if (!applied) {
        continue;
      }

      state.currentLevelLayout = {
        map: applied.map.slice(),
        targets: applied.targets.map(target => [target[0], target[1]]),
        playerStart: { row: applied.playerStart.row, col: applied.playerStart.col }
      };

      const shuffled = shuffleFromSolvedState();
      if (!shuffled || checkSolved()) {
        continue;
      }

      const nextLevel = randomizeLevel
        ? clampInt(baseLevel + 1, 1, 9999, baseLevel + 1)
        : baseLevel;
      state.level = nextLevel;

      buildBoardCells();
      state.initialSnapshot = takeSnapshot();
      state.moveCount = 0;
      state.pushCount = 0;
      state.solved = false;
      state.ready = true;

      setStatus(
        'scripts.arcade.sokoban.status.ready',
        'Niveau prêt.',
        null,
        { rememberBase: true }
      );
      renderBoard();
      updateHud();
      scheduleAutosave();
      return true;
    }

    state.ready = false;
    state.currentLevelLayout = null;
    setStatus(
      'scripts.arcade.sokoban.status.generationFailed',
      'Impossible de générer un niveau. Réessayez.',
      null,
      { rememberBase: true }
    );
    return false;
  }

  function prepareNewPuzzle(options = {}) {
    const { randomizeLevel = false, force = false } = options;
    if (!force && !state.ready && !state.solved) {
      // avoid interrupting initialization
      return;
    }
    clearCompletionEffects();
    state.initialSnapshot = null;
    if (state.levelDefinitions.length > 0) {
      const totalLevels = state.levelDefinitions.length;
      if (totalLevels <= 0) {
        return;
      }
      let nextIndex = state.levelIndex;
      if (randomizeLevel || !Number.isInteger(nextIndex) || nextIndex < 0 || nextIndex >= totalLevels) {
        nextIndex = selectRandomLevelIndex(state.levelIndex);
      }
      const loaded = loadLevel(nextIndex);
      if (!loaded) {
        return;
      }
      state.currentLevelLayout = null;
      buildBoardCells();
      state.initialSnapshot = takeSnapshot();
      state.moveCount = 0;
      state.pushCount = 0;
      state.solved = false;
      state.ready = true;
      setStatus(
        'scripts.arcade.sokoban.status.ready',
        'Niveau prêt.',
        null,
        { rememberBase: true }
      );
      renderBoard();
      updateHud();
      scheduleAutosave();
      return;
    }

    if (state.config?.generator) {
      prepareProceduralLevel({ randomizeLevel });
      return;
    }

    if (state.height <= 0 || state.width <= 0 || !state.tiles.length) {
      return;
    }

    if (randomizeLevel) {
      state.level = clampInt(state.level + 1, 1, 9999, 1);
    }
    const shuffled = shuffleFromSolvedState();
    if (!shuffled || checkSolved()) {
      state.ready = false;
      setStatus(
        'scripts.arcade.sokoban.status.generationFailed',
        'Impossible de générer un niveau. Réessayez.',
        null,
        { rememberBase: true }
      );
      return;
    }
    state.initialSnapshot = takeSnapshot();
    state.moveCount = 0;
    state.pushCount = 0;
    state.solved = false;
    state.ready = true;
    setStatus(
      'scripts.arcade.sokoban.status.ready',
      'Niveau prêt.',
      null,
      { rememberBase: true }
    );
    renderBoard();
    updateHud();
    scheduleAutosave();
  }

  function handleBoardKeydown(event) {
    if (!state.active) {
      return;
    }
    const direction = KEY_DIRECTIONS[event.key];
    if (!direction) {
      return;
    }
    event.preventDefault();
    attemptMove(direction.row, direction.col);
  }

  function handleGlobalKeydown(event) {
    if (!state.active) {
      return;
    }
    if (event.defaultPrevented) {
      return;
    }
    const direction = KEY_DIRECTIONS[event.key];
    if (!direction) {
      return;
    }
    event.preventDefault();
    attemptMove(direction.row, direction.col);
  }

  function handlePointerDown(event) {
    if (!state.active) {
      return;
    }
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    state.pointer = {
      id: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      lastX: event.clientX,
      lastY: event.clientY
    };
    if (event.currentTarget && typeof event.currentTarget.setPointerCapture === 'function') {
      try {
        event.currentTarget.setPointerCapture(event.pointerId);
      } catch (error) {
        // Ignore pointer capture errors
      }
    }
  }

  function handlePointerMove(event) {
    if (!state.pointer || event.pointerId !== state.pointer.id) {
      return;
    }
    state.pointer.lastX = event.clientX;
    state.pointer.lastY = event.clientY;
  }

  function handlePointerUp(event) {
    if (!state.pointer || event.pointerId !== state.pointer.id) {
      return;
    }
    const dx = event.clientX - state.pointer.startX;
    const dy = event.clientY - state.pointer.startY;
    if (event.currentTarget && typeof event.currentTarget.releasePointerCapture === 'function') {
      try {
        event.currentTarget.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore release errors
      }
    }
    const threshold = 24;
    const absX = Math.abs(dx);
    const absY = Math.abs(dy);
    if (absX >= threshold || absY >= threshold) {
      if (absX > absY) {
        attemptMove(0, dx > 0 ? 1 : -1);
      } else {
        attemptMove(dy > 0 ? 1 : -1, 0);
      }
    }
    state.pointer = null;
  }

  function handlePointerCancel(event) {
    if (!state.pointer || event.pointerId !== state.pointer.id) {
      return;
    }
    if (event.currentTarget && typeof event.currentTarget.releasePointerCapture === 'function') {
      try {
        event.currentTarget.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore
      }
    }
    state.pointer = null;
  }

  function attachLanguageListener() {
    if (state.languageHandler) {
      return;
    }
    const handler = () => {
      renderBoard();
      refreshBaseStatus();
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandler = handler;
  }

  function detachLanguageListener() {
    if (!state.languageHandler) {
      return;
    }
    window.removeEventListener('i18n:languagechange', state.languageHandler);
    state.languageHandler = null;
  }

  function initializeGame() {
    if (!initElements()) {
      return;
    }
    applyConfig(state.config);
    attachLanguageListener();
    const saved = readAutosave();
    if (saved && restoreFromSave(saved)) {
      renderBoard();
      updateHud();
      refreshBaseStatus();
    } else {
      prepareNewPuzzle({ randomizeLevel: true, force: true });
    }
  }

  function loadConfigAndInit() {
    if (typeof fetch !== 'function') {
      initializeGame();
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response && response.ok ? response.json() : null))
      .then(data => {
        if (data) {
          applyConfig(data);
        } else {
          applyConfig(DEFAULT_CONFIG);
        }
        initializeGame();
      })
      .catch(() => {
        applyConfig(DEFAULT_CONFIG);
        initializeGame();
      });
  }

  const api = {
    onEnter() {
      state.active = true;
      if (state.elements?.board) {
        try {
          state.elements.board.focus({ preventScroll: true });
        } catch (error) {
          state.elements.board.focus();
        }
      }
      renderBoard();
      refreshBaseStatus();
    },
    onLeave() {
      state.active = false;
      if (state.pointer) {
        state.pointer = null;
      }
      flushAutosave();
    }
  };

  window.sokobanArcade = api;

  loadConfigAndInit();

  window.addEventListener('beforeunload', () => {
    flushAutosave();
  });
  window.addEventListener('pagehide', () => {
    flushAutosave();
  });
})();
