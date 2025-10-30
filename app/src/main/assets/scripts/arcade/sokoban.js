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

  const DEFAULT_LEVELS = Object.freeze([
    Object.freeze({
      name: 'Training Dock',
      layout: Object.freeze([
        '.....',
        '.@  .',
        '. $ .',
        '. . .',
        '.....'
      ])
    }),
    Object.freeze({
      name: 'Double Shift',
      layout: Object.freeze([
        '......',
        '. .  .',
        '. $$ .',
        '.  .@.',
        '......'
      ])
    }),
    Object.freeze({
      name: 'Crossing Lanes',
      layout: Object.freeze([
        '.......',
        '. . . .',
        '. $$@ .',
        '.     .',
        '.......'
      ])
    }),
    Object.freeze({
      name: 'Storage Split',
      layout: Object.freeze([
        '.......',
        '.  .  .',
        '. $$# .',
        '.  .@ .',
        '.......'
      ])
    }),
    Object.freeze({
      name: 'Warehouse Loop',
      layout: Object.freeze([
        '........',
        '.  .  @.',
        '. $$ $ .',
        '.  . . .',
        '........'
      ])
    }),
    Object.freeze({
      name: 'Orbital Depot',
      layout: Object.freeze([
        '........',
        '. @    .',
        '. $$#  .',
        '.  .$. .',
        '.   .  .',
        '........'
      ])
    }),
    Object.freeze({
      name: 'Tight Corners',
      layout: Object.freeze([
        '........',
        '.  .  ..',
        '. $$@  .',
        '.  # $ .',
        '.  . . .',
        '........'
      ])
    }),
    Object.freeze({
      name: 'Central Support',
      layout: Object.freeze([
        '........',
        '.  . . .',
        '. $$#$ .',
        '. @  $ .',
        '.  ##  .',
        '.  . . .',
        '........'
      ])
    })
  ]);

  const DEFAULT_CONFIG = Object.freeze({
    shuffleMoves: Object.freeze({ min: 48, max: 160 }),
    map: Object.freeze([
      '.........',
      '.........',
      '...#.#...',
      '.........',
      '..#...#..',
      '.........',
      '...#.#...',
      '.........',
      '.........'
    ]),
    targets: Object.freeze([
      [2, 4],
      [4, 3],
      [4, 5],
      [6, 4]
    ]),
    playerStart: Object.freeze([4, 4]),
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

  const state = {
    config: DEFAULT_CONFIG,
    configSignature: computeConfigSignature(DEFAULT_CONFIG),
    width: DEFAULT_CONFIG.map[0].length,
    height: DEFAULT_CONFIG.map.length,
    tiles: [],
    targetKeys: new Set(),
    boxes: new Set(),
    levelDefinitions: [],
    levelIndex: 0,
    player: { row: 4, col: 4 },
    playerStart: { row: 4, col: 4 },
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
    if (!rawConfig || typeof rawConfig !== 'object') {
      return {
        shuffleMoves: { ...DEFAULT_CONFIG.shuffleMoves },
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
      map: normalizedMap,
      targets,
      playerStart: playerCandidate,
      levels: normalizedLevels
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
    clearCompletionEffects();

    if (state.levelDefinitions.length > 0) {
      loadLevel(0);
      return;
    }

    state.levelDefinitions = [];
    state.levelIndex = 0;
    state.height = normalized.map.length;
    state.width = normalized.map[0]?.length || 0;
    state.tiles = new Array(state.height);
    state.targetKeys = new Set();

    for (let row = 0; row < state.height; row += 1) {
      const rowTiles = new Array(state.width);
      const rowString = normalized.map[row];
      for (let col = 0; col < state.width; col += 1) {
        const char = rowString[col];
        const wall = char === '#';
        rowTiles[col] = { wall, target: false };
      }
      state.tiles[row] = rowTiles;
    }

    normalized.targets.forEach(target => {
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

    const candidateRow = clampInt(normalized.playerStart?.[0], 0, state.height - 1, 0);
    const candidateCol = clampInt(normalized.playerStart?.[1], 0, state.width - 1, 0);
    if (isWalkable(candidateRow, candidateCol)) {
      state.playerStart = { row: candidateRow, col: candidateCol };
    } else if (state.targetKeys.size > 0) {
      const [firstTarget] = Array.from(state.targetKeys);
      const [row, col] = firstTarget.split(',').map(Number);
      state.playerStart = { row, col };
    } else {
      outer:
      for (let row = 0; row < state.height; row += 1) {
        for (let col = 0; col < state.width; col += 1) {
          if (isWalkable(row, col)) {
            state.playerStart = { row, col };
            break outer;
          }
        }
      }
    }

    state.player = { row: state.playerStart.row, col: state.playerStart.col };
    state.boxes = new Set();
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
    return {
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
      force = false
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
      handleLevelCompleted();
    } else if (!skipAutosave) {
      scheduleAutosave();
    }
    return true;
  }

  function performRandomShuffleMove() {
    const directions = [...DIRECTIONS];
    for (let i = directions.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = directions[i];
      directions[i] = directions[j];
      directions[j] = tmp;
    }
    for (const direction of directions) {
      const moved = attemptMove(direction.row, direction.col, {
        countMove: false,
        announceBlocked: false,
        allowWhenSolved: true,
        skipAutosave: true,
        skipStatus: true,
        force: true
      });
      if (moved) {
        return true;
      }
    }
    return false;
  }

  function shuffleFromSolvedState() {
    const iterations = clampInt(
      Math.floor(Math.random() * (state.config.shuffleMoves.max - state.config.shuffleMoves.min + 1))
        + state.config.shuffleMoves.min,
      8,
      2000,
      64
    );
    state.player = { row: state.playerStart.row, col: state.playerStart.col };
    state.boxes = new Set(state.targetKeys);
    let moves = 0;
    let attempts = 0;
    while (moves < iterations && attempts < iterations * 10) {
      const moved = performRandomShuffleMove();
      attempts += 1;
      if (moved) {
        moves += 1;
      }
    }
    if (checkSolved()) {
      let guard = 0;
      while (checkSolved() && guard < 16) {
        performRandomShuffleMove();
        guard += 1;
      }
    }
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

  function prepareNewPuzzle(options = {}) {
    const { randomizeLevel = false, force = false } = options;
    if (!force && !state.ready && !state.solved) {
      // avoid interrupting initialization
      return;
    }
    clearCompletionEffects();
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

    if (randomizeLevel) {
      state.level = clampInt(state.level + 1, 1, 9999, 1);
    }
    shuffleFromSolvedState();
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
    buildBoardCells();
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
