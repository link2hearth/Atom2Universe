(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/the-line.json';
  const DEFAULT_CONFIG = Object.freeze({
    maxGenerationAttempts: 200,
    holeRetryLimit: 24,
    difficulties: Object.freeze({
      easy: Object.freeze({
        gridSizes: Object.freeze([[5, 5], [5, 6], [6, 6]]),
        holeRange: Object.freeze({ min: 0, max: 2 }),
        minTurns: 6,
        multiPairs: Object.freeze({ min: 2, max: 3 })
      }),
      medium: Object.freeze({
        gridSizes: Object.freeze([[7, 6], [7, 7], [8, 6], [8, 7]]),
        holeRange: Object.freeze({ min: 2, max: 6 }),
        minTurns: 12,
        multiPairs: Object.freeze({ min: 3, max: 4 })
      }),
      hard: Object.freeze({
        gridSizes: Object.freeze([[8, 8], [9, 7], [9, 8], [9, 9]]),
        holeRange: Object.freeze({ min: 1, max: 5 }),
        minTurns: 18,
        multiPairs: Object.freeze({ min: 4, max: 5 })
      })
    })
  });

  const SINGLE_PATH_COLOR = Object.freeze({ id: 'single', value: '#7ad3ff' });
  const COLOR_PALETTE = Object.freeze([
    Object.freeze({ id: 'amber', value: '#f7b731' }),
    Object.freeze({ id: 'azure', value: '#34d1ff' }),
    Object.freeze({ id: 'orchid', value: '#a162f7' }),
    Object.freeze({ id: 'coral', value: '#ff6b6b' }),
    Object.freeze({ id: 'emerald', value: '#7bd88f' }),
    Object.freeze({ id: 'rose', value: '#ff8ad6' }),
    Object.freeze({ id: 'citrus', value: '#ffc371' })
  ]);

  const state = {
    config: null,
    mode: 'single',
    difficulty: 'easy',
    levelCounters: {
      single: { easy: 1, medium: 1, hard: 1 },
      multi: { easy: 1, medium: 1, hard: 1 }
    },
    board: null,
    paths: new Map(),
    activePath: null,
    cellsRemaining: 0,
    messageTimeout: null,
    currentPuzzle: null,
    elements: null,
    lastMessage: null,
    languageListenerAttached: false,
    languageChangeHandler: null
  };

  onReady(() => {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    updateModeButtons();
    updateDifficultyButtons();
    updateLevelDisplay();
    setupButtons();
    setupBoardEvents();
    setupLanguageChangeListener();
    state.config = normalizeConfig(DEFAULT_CONFIG, null);
    prepareNewPuzzle();
    loadRemoteConfig();
  });

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function translate(key, fallback, params) {
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
        console.warn('The Line translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return fallback;
    }
    return key;
  }

  function clampNumber(value, min, max, fallback) {
    if (Number.isFinite(value)) {
      let numeric = value;
      if (typeof min === 'number') {
        numeric = Math.max(min, numeric);
      }
      if (typeof max === 'number') {
        numeric = Math.min(max, numeric);
      }
      return numeric;
    }
    return fallback;
  }

  function randomInt(min, max) {
    const low = Math.ceil(min);
    const high = Math.floor(max);
    return Math.floor(Math.random() * (high - low + 1)) + low;
  }

  function shuffle(array) {
    for (let i = array.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = array[i];
      array[i] = array[j];
      array[j] = temp;
    }
    return array;
  }

  function normalizeConfig(config, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : DEFAULT_CONFIG;
    const source = config && typeof config === 'object' ? config : {};
    const maxGenerationAttempts = clampNumber(
      source.maxGenerationAttempts,
      20,
      1200,
      base.maxGenerationAttempts
    );
    const holeRetryLimit = clampNumber(
      source.holeRetryLimit,
      4,
      200,
      base.holeRetryLimit
    );
    const difficulties = {};
    ['easy', 'medium', 'hard'].forEach(key => {
      difficulties[key] = normalizeDifficultyConfig(
        source.difficulties && source.difficulties[key],
        base.difficulties[key]
      );
    });
    return {
      maxGenerationAttempts,
      holeRetryLimit,
      difficulties
    };
  }

  function normalizeDifficultyConfig(config, fallback) {
    const base = fallback && typeof fallback === 'object'
      ? fallback
      : DEFAULT_CONFIG.difficulties.easy;
    const source = config && typeof config === 'object' ? config : {};

    const gridSizesSource = Array.isArray(source.gridSizes) ? source.gridSizes : base.gridSizes;
    const gridSizes = [];
    gridSizesSource.forEach(entry => {
      if (!Array.isArray(entry) || entry.length !== 2) {
        return;
      }
      const width = Math.max(3, Math.floor(entry[0]));
      const height = Math.max(3, Math.floor(entry[1]));
      if (!Number.isFinite(width) || !Number.isFinite(height)) {
        return;
      }
      gridSizes.push([width, height]);
    });
    if (!gridSizes.length) {
      gridSizes.push([5, 5]);
    }

    const holeRangeSource = source.holeRange && typeof source.holeRange === 'object'
      ? source.holeRange
      : base.holeRange;
    const minHole = Number.isFinite(holeRangeSource?.min)
      ? Math.max(0, Math.floor(holeRangeSource.min))
      : Math.max(0, Math.floor(base.holeRange?.min || 0));
    const maxHole = Number.isFinite(holeRangeSource?.max)
      ? Math.max(minHole, Math.floor(holeRangeSource.max))
      : Math.max(minHole, Math.floor(base.holeRange?.max || minHole));

    const minTurns = Number.isFinite(source.minTurns)
      ? Math.max(0, Math.floor(source.minTurns))
      : Math.max(0, Math.floor(base.minTurns || 0));

    const multiPairsSource = source.multiPairs && typeof source.multiPairs === 'object'
      ? source.multiPairs
      : base.multiPairs;
    const multiMin = Number.isFinite(multiPairsSource?.min)
      ? Math.max(2, Math.floor(multiPairsSource.min))
      : Math.max(2, Math.floor(base.multiPairs?.min || 2));
    const multiMax = Number.isFinite(multiPairsSource?.max)
      ? Math.max(multiMin, Math.floor(multiPairsSource.max))
      : Math.max(multiMin, Math.floor(base.multiPairs?.max || multiMin));

    return {
      gridSizes,
      holeRange: { min: minHole, max: maxHole },
      minTurns,
      multiPairs: { min: multiMin, max: multiMax }
    };
  }

  function loadRemoteConfig() {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          state.config = normalizeConfig(data, state.config || DEFAULT_CONFIG);
        }
      })
      .catch(error => {
        console.warn('The Line config load error', error);
      });
  }

  function getElements() {
    const section = document.getElementById('theLine');
    if (!section) {
      return null;
    }
    return {
      section,
      board: document.getElementById('theLineBoard'),
      message: document.getElementById('theLineMessage'),
      reset: document.getElementById('theLineResetButton'),
      level: document.getElementById('theLineLevelValue'),
      remaining: document.getElementById('theLineRemainingValue'),
      modeButtons: Array.from(section.querySelectorAll('[data-line-mode]')),
      difficultyButtons: Array.from(section.querySelectorAll('[data-line-difficulty]'))
    };
  }

  function setupButtons() {
    const elements = state.elements;
    if (!elements) {
      return;
    }
    elements.modeButtons.forEach(button => {
      button.addEventListener('click', () => {
        const mode = button.dataset.lineMode;
        setMode(mode);
      });
    });
    elements.difficultyButtons.forEach(button => {
      button.addEventListener('click', () => {
        const difficulty = button.dataset.lineDifficulty;
        setDifficulty(difficulty);
      });
    });
    if (elements.reset) {
      elements.reset.addEventListener('click', () => {
        clearMessageTimeout();
        prepareNewPuzzle();
      });
    }
  }

  function setupBoardEvents() {
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    const board = elements.board;
    board.addEventListener('pointerdown', handlePointerDown);
    board.addEventListener('pointermove', handlePointerMove);
    board.addEventListener('pointerup', handlePointerUp);
    board.addEventListener('pointercancel', handlePointerUp);
  }

  function setupLanguageChangeListener() {
    if (state.languageListenerAttached) {
      return;
    }
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
      return;
    }
    const handler = () => {
      const message = state.lastMessage;
      if (!message) {
        return;
      }
      setMessage(message.key, message.fallback, message.params, true);
    };
    state.languageChangeHandler = handler;
    window.addEventListener('i18n:languagechange', handler);
    state.languageListenerAttached = true;
  }

  function setMode(mode) {
    if (mode !== 'single' && mode !== 'multi') {
      return;
    }
    if (state.mode === mode) {
      return;
    }
    state.mode = mode;
    updateModeButtons();
    updateLevelDisplay();
    clearMessageTimeout();
    prepareNewPuzzle();
  }

  function setDifficulty(difficulty) {
    if (!['easy', 'medium', 'hard'].includes(difficulty)) {
      return;
    }
    if (state.difficulty === difficulty) {
      return;
    }
    state.difficulty = difficulty;
    updateDifficultyButtons();
    updateLevelDisplay();
    clearMessageTimeout();
    prepareNewPuzzle();
  }

  function updateModeButtons() {
    const elements = state.elements;
    if (!elements) {
      return;
    }
    elements.modeButtons.forEach(button => {
      const mode = button.dataset.lineMode;
      const isActive = mode === state.mode;
      button.classList.toggle('the-line__toggle--active', isActive);
      button.setAttribute('aria-pressed', String(isActive));
    });
  }

  function updateDifficultyButtons() {
    const elements = state.elements;
    if (!elements) {
      return;
    }
    elements.difficultyButtons.forEach(button => {
      const difficulty = button.dataset.lineDifficulty;
      const isActive = difficulty === state.difficulty;
      button.classList.toggle('the-line__toggle--active', isActive);
      button.setAttribute('aria-pressed', String(isActive));
    });
  }

  function updateLevelDisplay() {
    const elements = state.elements;
    if (!elements || !elements.level) {
      return;
    }
    const counters = state.levelCounters[state.mode] || state.levelCounters.single;
    const level = counters[state.difficulty] || 1;
    elements.level.textContent = String(level);
  }

  function updateRemainingValue() {
    const elements = state.elements;
    if (!elements || !elements.remaining) {
      return;
    }
    elements.remaining.textContent = String(Math.max(0, state.cellsRemaining));
  }

  function clearMessageTimeout() {
    if (state.messageTimeout) {
      window.clearTimeout(state.messageTimeout);
      state.messageTimeout = null;
    }
  }

  function setMessage(key, fallback, params, isRefresh = false) {
    const elements = state.elements;
    if (!elements || !elements.message) {
      return;
    }
    const normalizedKey = typeof key === 'string' && key.trim() ? key.trim() : '';
    if (normalizedKey) {
      elements.message.setAttribute('data-i18n', normalizedKey);
    } else if (!isRefresh) {
      elements.message.removeAttribute('data-i18n');
    }
    const text = normalizedKey
      ? translate(normalizedKey, fallback, params)
      : typeof fallback === 'string'
        ? fallback
        : '';
    elements.message.textContent = text;
    if (!isRefresh) {
      const storedParams = params && typeof params === 'object'
        ? Object.assign({}, params)
        : null;
      state.lastMessage = {
        key: normalizedKey || null,
        fallback: typeof fallback === 'string' ? fallback : '',
        params: storedParams
      };
    }
  }

  function prepareNewPuzzle() {
    cancelActivePath();
    const config = state.config || normalizeConfig(DEFAULT_CONFIG, null);
    const puzzle = generatePuzzle(state.mode, state.difficulty, config);
    if (!puzzle) {
      setMessage(
        'index.sections.theLine.messages.error',
        'Impossible de générer une nouvelle grille. Réessayez.'
      );
      return;
    }
    state.currentPuzzle = puzzle;
    renderPuzzle(puzzle);
    const hintKey = state.mode === 'multi'
      ? 'index.sections.theLine.messages.multi'
      : 'index.sections.theLine.messages.single';
    const fallback = state.mode === 'multi'
      ? 'Reliez chaque paire de couleurs sans croiser les chemins.'
      : 'Tracez un parcours continu qui visite chaque case.';
    setMessage(hintKey, fallback, { width: puzzle.width, height: puzzle.height });
  }
  function generatePuzzle(mode, difficulty, config) {
    const difficultyConfig = config.difficulties[difficulty] || config.difficulties.easy;
    const sizes = Array.isArray(difficultyConfig.gridSizes)
      ? difficultyConfig.gridSizes
      : [[5, 5]];
    const [width, height] = sizes[randomInt(0, sizes.length - 1)];
    const totalCells = width * height;
    const holeRange = difficultyConfig.holeRange || { min: 0, max: 0 };
    let holeMin = clampNumber(holeRange.min, 0, totalCells - 2, 0);
    let holeMax = clampNumber(holeRange.max, holeMin, totalCells - 2, holeMin);
    if (mode === 'multi') {
      holeMax = Math.min(holeMax, Math.max(holeMin, 3));
    }
    const maxAttempts = clampNumber(config.maxGenerationAttempts, 20, 2000, 200);
    const holeRetryLimit = Math.max(1, Math.floor(config.holeRetryLimit || 16));

    let attempt = 0;
    let holeAttempts = 0;
    let currentHoleMax = holeMax;
    let bestCandidate = null;

    while (attempt < maxAttempts) {
      attempt += 1;
      holeAttempts += 1;
      const holeCount = holeMin === currentHoleMax
        ? holeMin
        : randomInt(holeMin, currentHoleMax);
      const blocked = generateBlockedSet(width, height, holeCount);
      const path = findHamiltonianPath(width, height, blocked);
      if (!path) {
        if (holeAttempts >= holeRetryLimit && currentHoleMax > holeMin) {
          currentHoleMax = Math.max(holeMin, currentHoleMax - 1);
          holeAttempts = 0;
        }
        continue;
      }
      const turns = countTurns(path, width);
      if (mode === 'single' && turns < Math.max(2, difficultyConfig.minTurns || 0)) {
        if (!bestCandidate || turns > bestCandidate.turns) {
          bestCandidate = { path, blocked, turns };
        }
        continue;
      }
      return buildPuzzleFromPath(mode, width, height, blocked, path, difficultyConfig);
    }

    if (bestCandidate) {
      return buildPuzzleFromPath(
        mode,
        width,
        height,
        bestCandidate.blocked,
        bestCandidate.path,
        difficultyConfig
      );
    }

    const fallbackPath = findHamiltonianPath(width, height, new Set());
    if (fallbackPath) {
      return buildPuzzleFromPath(mode, width, height, new Set(), fallbackPath, difficultyConfig);
    }
    return null;
  }

  function generateBlockedSet(width, height, count) {
    const total = width * height;
    const limit = Math.max(0, Math.min(count, total - 2));
    if (limit <= 0) {
      return new Set();
    }
    const blocked = new Set();
    while (blocked.size < limit) {
      const index = randomInt(0, total - 1);
      blocked.add(index);
      if (blocked.size >= limit) {
        break;
      }
    }
    return blocked;
  }

  function findHamiltonianPath(width, height, blockedIndices) {
    const totalCells = width * height;
    const totalAccessible = totalCells - blockedIndices.size;
    if (totalAccessible <= 0) {
      return null;
    }

    const neighborCache = new Array(totalCells);
    const accessible = [];
    for (let index = 0; index < totalCells; index += 1) {
      if (blockedIndices.has(index)) {
        neighborCache[index] = [];
        continue;
      }
      const coord = indexToCoord(index, width);
      const neighbors = [];
      if (coord.x > 0) {
        const left = index - 1;
        if (!blockedIndices.has(left)) {
          neighbors.push(left);
        }
      }
      if (coord.x < width - 1) {
        const right = index + 1;
        if (!blockedIndices.has(right)) {
          neighbors.push(right);
        }
      }
      if (coord.y > 0) {
        const up = index - width;
        if (!blockedIndices.has(up)) {
          neighbors.push(up);
        }
      }
      if (coord.y < height - 1) {
        const down = index + width;
        if (!blockedIndices.has(down)) {
          neighbors.push(down);
        }
      }
      neighborCache[index] = neighbors;
      accessible.push(index);
    }

    if (!accessible.length) {
      return null;
    }

    const visited = new Uint8Array(totalCells);
    blockedIndices.forEach(index => {
      visited[index] = 1;
    });
    const path = new Array(totalAccessible);
    const startCandidates = shuffle(accessible.slice());

    for (let i = 0; i < startCandidates.length; i += 1) {
      const start = startCandidates[i];
      path[0] = start;
      visited[start] = 1;
      if (search(1, start)) {
        return path.slice();
      }
      visited[start] = 0;
    }
    return null;

    function search(step, current) {
      if (step >= totalAccessible) {
        return true;
      }
      const neighbors = neighborCache[current];
      if (!neighbors || !neighbors.length) {
        return false;
      }
      const ordered = orderNeighbors(neighbors);
      for (let j = 0; j < ordered.length; j += 1) {
        const next = ordered[j];
        if (visited[next]) {
          continue;
        }
        visited[next] = 1;
        path[step] = next;
        if (search(step + 1, next)) {
          return true;
        }
        visited[next] = 0;
      }
      return false;
    }

    function orderNeighbors(list) {
      const candidates = [];
      for (let k = 0; k < list.length; k += 1) {
        const neighbor = list[k];
        if (visited[neighbor]) {
          continue;
        }
        const neighbors = neighborCache[neighbor];
        let degree = 0;
        for (let d = 0; d < neighbors.length; d += 1) {
          if (!visited[neighbors[d]]) {
            degree += 1;
          }
        }
        candidates.push({ index: neighbor, degree });
      }
      shuffle(candidates);
      candidates.sort((a, b) => a.degree - b.degree);
      return candidates.map(candidate => candidate.index);
    }
  }

  function countTurns(path, width) {
    if (!Array.isArray(path) || path.length < 3) {
      return 0;
    }
    let turns = 0;
    let previousDirection = null;
    for (let i = 1; i < path.length; i += 1) {
      const prevCoord = indexToCoord(path[i - 1], width);
      const currentCoord = indexToCoord(path[i], width);
      const dx = currentCoord.x - prevCoord.x;
      const dy = currentCoord.y - prevCoord.y;
      const direction = `${dx},${dy}`;
      if (previousDirection && direction !== previousDirection) {
        turns += 1;
      }
      previousDirection = direction;
    }
    return turns;
  }

  function buildPuzzleFromPath(mode, width, height, blockedIndices, pathIndices, difficultyConfig) {
    const pathCoords = pathIndices.map(index => indexToCoord(index, width));
    if (mode === 'multi') {
      const segments = createColorSegments(pathCoords, difficultyConfig);
      return {
        mode,
        width,
        height,
        blockedIndices,
        path: pathCoords,
        segments
      };
    }
    return {
      mode,
      width,
      height,
      blockedIndices,
      path: pathCoords,
      endpoints: {
        start: pathCoords[0],
        end: pathCoords[pathCoords.length - 1]
      }
    };
  }

  function createColorSegments(pathCoords, difficultyConfig) {
    const total = pathCoords.length;
    const configPairs = difficultyConfig.multiPairs || { min: 2, max: 3 };
    const maxPossible = Math.max(2, Math.floor(total / 4));
    const minPairs = Math.min(Math.max(2, configPairs.min || 2), maxPossible);
    const maxPairs = Math.min(Math.max(minPairs, configPairs.max || minPairs), maxPossible);
    const pairCount = minPairs === maxPairs ? minPairs : randomInt(minPairs, maxPairs);
    const segments = splitPathSegments(pathCoords, pairCount);
    return segments.map((segment, index) => {
      const color = COLOR_PALETTE[index % COLOR_PALETTE.length];
      return {
        colorId: color.id,
        colorValue: color.value,
        cells: segment,
        start: segment[0],
        end: segment[segment.length - 1]
      };
    });
  }

  function splitPathSegments(pathCoords, segmentCount) {
    const segments = [];
    const total = pathCoords.length;
    let remaining = total;
    let cursor = 0;
    for (let i = 0; i < segmentCount; i += 1) {
      const segmentsLeft = segmentCount - i;
      const minRemaining = 2 * (segmentsLeft - 1);
      let minLength = 2;
      let maxLength = remaining - minRemaining;
      if (i === segmentCount - 1 || maxLength < minLength) {
        maxLength = remaining;
      } else {
        maxLength = Math.max(minLength, maxLength);
      }
      const length = i === segmentCount - 1
        ? remaining
        : randomInt(minLength, maxLength);
      const segment = pathCoords.slice(cursor, cursor + length);
      segments.push(segment);
      cursor += length;
      remaining -= length;
    }
    return segments;
  }

  function indexToCoord(index, width) {
    const x = index % width;
    const y = Math.floor(index / width);
    return { x, y };
  }
  function renderPuzzle(puzzle) {
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    cancelActivePath();
    state.paths.clear();
    state.cellsRemaining = 0;

    const board = elements.board;
    board.innerHTML = '';
    board.style.setProperty('--the-line-columns', String(puzzle.width));
    board.setAttribute('data-line-columns', String(puzzle.width));

    const fragment = document.createDocumentFragment();
    const cells = [];
    for (let y = 0; y < puzzle.height; y += 1) {
      const row = [];
      for (let x = 0; x < puzzle.width; x += 1) {
        const cellElement = document.createElement('div');
        cellElement.className = 'the-line__cell';
        cellElement.dataset.x = String(x);
        cellElement.dataset.y = String(y);
        const index = y * puzzle.width + x;
        const blocked = puzzle.blockedIndices.has(index);
        if (blocked) {
          cellElement.classList.add('the-line__cell--blocked');
          cellElement.setAttribute('data-blocked', 'true');
        } else {
          state.cellsRemaining += 1;
        }
        fragment.appendChild(cellElement);
        row.push({
          x,
          y,
          index,
          blocked,
          element: cellElement,
          endpointColor: null,
          endpointColorValue: null,
          occupantColor: null
        });
      }
      cells.push(row);
    }
    board.appendChild(fragment);
    state.board = {
      width: puzzle.width,
      height: puzzle.height,
      cells,
      blocked: puzzle.blockedIndices
    };
    state.paths.clear();

    if (puzzle.mode === 'multi' && Array.isArray(puzzle.segments) && puzzle.segments.length) {
      puzzle.segments.forEach((segment, index) => {
        const startCell = getCellAt(segment.start.x, segment.start.y);
        const endCell = getCellAt(segment.end.x, segment.end.y);
        const color = COLOR_PALETTE[index % COLOR_PALETTE.length];
        markEndpoint(startCell, color);
        markEndpoint(endCell, color);
        state.paths.set(color.id, {
          colorId: color.id,
          colorValue: color.value,
          endpoints: [startCell, endCell],
          sequence: [],
          complete: false
        });
      });
    } else {
      const startCoord = puzzle.path[0];
      const endCoord = puzzle.path[puzzle.path.length - 1];
      const startCell = getCellAt(startCoord.x, startCoord.y);
      const endCell = getCellAt(endCoord.x, endCoord.y);
      markEndpoint(startCell, SINGLE_PATH_COLOR);
      markEndpoint(endCell, SINGLE_PATH_COLOR);
      state.paths.set(SINGLE_PATH_COLOR.id, {
        colorId: SINGLE_PATH_COLOR.id,
        colorValue: SINGLE_PATH_COLOR.value,
        endpoints: [startCell, endCell],
        sequence: [],
        complete: false
      });
    }

    updateRemainingValue();
  }

  function markEndpoint(cell, color) {
    if (!cell) {
      return;
    }
    cell.endpointColor = color.id;
    cell.endpointColorValue = color.value;
    cell.element.classList.add('the-line__cell--endpoint');
    cell.element.dataset.lineColor = color.id;
    cell.element.style.setProperty('--line-color-value', color.value);
  }

  function getCellAt(x, y) {
    if (!state.board) {
      return null;
    }
    if (y < 0 || y >= state.board.height || x < 0 || x >= state.board.width) {
      return null;
    }
    return state.board.cells[y][x];
  }

  function cancelActivePath() {
    const active = state.activePath;
    if (!active) {
      return;
    }
    const board = state.elements && state.elements.board;
    if (board && typeof board.releasePointerCapture === 'function') {
      try {
        board.releasePointerCapture(active.pointerId);
      } catch (error) {
        /* ignore */
      }
    }
    if (active.lastCell) {
      active.lastCell.element.classList.remove('the-line__cell--active');
    }
    state.activePath = null;
  }

  function occupyCellIfNeeded(cell, pathState) {
    if (!cell || cell.blocked) {
      return;
    }
    if (cell.occupantColor === pathState.colorId) {
      return;
    }
    if (cell.occupantColor) {
      clearCellOccupant(cell);
    }
    cell.occupantColor = pathState.colorId;
    cell.element.classList.add('the-line__cell--filled');
    cell.element.dataset.lineColor = pathState.colorId;
    cell.element.style.setProperty('--line-color-value', pathState.colorValue);
    state.cellsRemaining = Math.max(0, state.cellsRemaining - 1);
    updateRemainingValue();
  }

  function clearCellOccupant(cell) {
    if (!cell || !cell.occupantColor) {
      return;
    }
    cell.occupantColor = null;
    cell.element.classList.remove('the-line__cell--filled');
    state.cellsRemaining += 1;
    if (cell.endpointColor) {
      cell.element.dataset.lineColor = cell.endpointColor;
      cell.element.style.setProperty('--line-color-value', cell.endpointColorValue || '');
    } else {
      cell.element.removeAttribute('data-line-color');
      cell.element.style.removeProperty('--line-color-value');
    }
    updateRemainingValue();
  }

  function clearPath(pathState) {
    if (!pathState || !Array.isArray(pathState.sequence)) {
      return;
    }
    for (let i = 0; i < pathState.sequence.length; i += 1) {
      const cell = pathState.sequence[i];
      clearCellOccupant(cell);
    }
    pathState.sequence = [];
    pathState.complete = false;
  }

  function resolvePathEntry(cell) {
    if (!cell || !state.currentPuzzle) {
      return null;
    }
    if (state.mode === 'single') {
      const pathState = state.paths.get(SINGLE_PATH_COLOR.id);
      if (!pathState) {
        return null;
      }
      const startCell = pathState.endpoints[0];
      const lastCell = pathState.sequence[pathState.sequence.length - 1] || null;
      if (!pathState.sequence.length) {
        return cell === startCell ? { pathState, reset: true } : null;
      }
      if (pathState.complete) {
        return cell === startCell ? { pathState, reset: true } : null;
      }
      if (cell === lastCell) {
        return { pathState, reset: false };
      }
      if (cell === startCell) {
        return { pathState, reset: true };
      }
      return null;
    }
    const colorId = cell.endpointColor;
    if (!colorId) {
      return null;
    }
    const pathState = state.paths.get(colorId);
    if (!pathState) {
      return null;
    }
    return { pathState, reset: true };
  }

  function startActivePath(cell, entry, pointerId) {
    cancelActivePath();
    const pathState = entry.pathState;
    if (entry.reset) {
      clearPath(pathState);
    }
    if (!pathState.sequence.length) {
      pathState.sequence.push(cell);
      occupyCellIfNeeded(cell, pathState);
    }
    cell.element.classList.add('the-line__cell--active');
    state.activePath = {
      pointerId,
      pathState,
      lastCell: cell
    };
  }

  function areAdjacent(a, b) {
    if (!a || !b) {
      return false;
    }
    const dx = Math.abs(a.x - b.x);
    const dy = Math.abs(a.y - b.y);
    return (dx === 1 && dy === 0) || (dx === 0 && dy === 1);
  }

  function handlePointerDown(event) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    const target = event.target.closest('.the-line__cell');
    if (!target || target.getAttribute('data-blocked') === 'true') {
      return;
    }
    const x = Number.parseInt(target.dataset.x, 10);
    const y = Number.parseInt(target.dataset.y, 10);
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      return;
    }
    const cell = getCellAt(x, y);
    const entry = resolvePathEntry(cell);
    if (!entry) {
      return;
    }
    event.preventDefault();
    clearMessageTimeout();
    if (typeof elements.board.setPointerCapture === 'function') {
      try {
        elements.board.setPointerCapture(event.pointerId);
      } catch (error) {
        /* ignore */
      }
    }
    startActivePath(cell, entry, event.pointerId);
  }

  function handlePointerMove(event) {
    const active = state.activePath;
    if (!active || active.pointerId !== event.pointerId) {
      return;
    }
    const target = event.target.closest('.the-line__cell');
    if (!target || target.getAttribute('data-blocked') === 'true') {
      return;
    }
    const x = Number.parseInt(target.dataset.x, 10);
    const y = Number.parseInt(target.dataset.y, 10);
    const cell = getCellAt(x, y);
    if (!cell) {
      return;
    }
    const pathState = active.pathState;
    const lastCell = active.lastCell || pathState.sequence[pathState.sequence.length - 1];
    if (!lastCell || cell === lastCell) {
      return;
    }
    if (!areAdjacent(cell, lastCell)) {
      return;
    }
    if (cell.occupantColor && cell.occupantColor !== pathState.colorId) {
      return;
    }
    const existingIndex = pathState.sequence.indexOf(cell);
    if (existingIndex >= 0) {
      if (existingIndex === pathState.sequence.length - 2) {
        const removed = pathState.sequence.pop();
        if (removed && removed !== cell) {
          clearCellOccupant(removed);
        }
        lastCell.element.classList.remove('the-line__cell--active');
        cell.element.classList.add('the-line__cell--active');
        active.lastCell = cell;
      }
      return;
    }
    if (cell.endpointColor && cell.endpointColor !== pathState.colorId) {
      return;
    }
    pathState.sequence.push(cell);
    occupyCellIfNeeded(cell, pathState);
    if (active.lastCell) {
      active.lastCell.element.classList.remove('the-line__cell--active');
    }
    cell.element.classList.add('the-line__cell--active');
    active.lastCell = cell;

    if (pathState.endpoints && pathState.endpoints.length === 2) {
      const startCell = pathState.sequence[0];
      const targetEndpoint = pathState.endpoints[0] === startCell
        ? pathState.endpoints[1]
        : pathState.endpoints[0];
      if (cell === targetEndpoint) {
        if (state.mode === 'single') {
          if (state.cellsRemaining === 0) {
            pathState.complete = true;
            finalizeActivePath(event.pointerId, true);
          }
        } else {
          pathState.complete = true;
          finalizeActivePath(event.pointerId, true);
        }
      }
    }
  }

  function handlePointerUp(event) {
    const active = state.activePath;
    if (!active || active.pointerId !== event.pointerId) {
      return;
    }
    finalizeActivePath(event.pointerId, false);
  }

  function finalizeActivePath(pointerId, triggeredCompletion) {
    const active = state.activePath;
    if (!active || active.pointerId !== pointerId) {
      cancelActivePath();
      return;
    }
    const board = state.elements && state.elements.board;
    if (board && typeof board.releasePointerCapture === 'function') {
      try {
        board.releasePointerCapture(pointerId);
      } catch (error) {
        /* ignore */
      }
    }
    if (active.lastCell) {
      active.lastCell.element.classList.remove('the-line__cell--active');
    }
    state.activePath = null;

    const pathState = active.pathState;
    if (!triggeredCompletion) {
      if (state.mode === 'multi') {
        clearPath(pathState);
        setMessage(
          'index.sections.theLine.messages.incompletePath',
          'Reliez la paire jusqu’à l’autre extrémité pour valider la couleur.'
        );
      }
      return;
    }

    if (state.mode === 'single') {
      if (pathState.complete) {
        checkForCompletion();
      }
      return;
    }

    checkForCompletion();
  }

  function checkForCompletion() {
    if (!state.currentPuzzle) {
      return;
    }
    if (state.mode === 'single') {
      const pathState = state.paths.get(SINGLE_PATH_COLOR.id);
      if (!pathState || !pathState.complete) {
        return;
      }
      if (state.cellsRemaining > 0) {
        return;
      }
      handlePuzzleCompleted();
      return;
    }
    for (const pathState of state.paths.values()) {
      if (!pathState.complete) {
        return;
      }
    }
    if (state.cellsRemaining > 0) {
      return;
    }
    handlePuzzleCompleted();
  }

  function handlePuzzleCompleted() {
    clearMessageTimeout();
    const counters = state.levelCounters[state.mode] || state.levelCounters.single;
    const currentLevel = counters[state.difficulty] || 1;
    setMessage(
      'index.sections.theLine.messages.completed',
      `Niveau ${currentLevel} terminé !`,
      { level: currentLevel }
    );
    counters[state.difficulty] = currentLevel + 1;
    updateLevelDisplay();
    state.messageTimeout = window.setTimeout(() => {
      setMessage(
        'index.sections.theLine.messages.autoNext',
        'Nouveau parcours généré !',
        { level: counters[state.difficulty] }
      );
      state.messageTimeout = null;
      prepareNewPuzzle();
    }, 2200);
  }

})();
