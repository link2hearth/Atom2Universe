(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/the-line.json';
  const DEFAULT_CONFIG = Object.freeze({
    maxGenerationAttempts: 100,
    difficulties: Object.freeze({
      easy: Object.freeze({
        gridSizes: Object.freeze([[5, 5], [5, 6], [6, 6]]),
        holeRange: Object.freeze({ min: 2, max: 5 }),
        minTurns: 6,
        multiPairs: Object.freeze({ min: 2, max: 3 })
      }),
      medium: Object.freeze({
        gridSizes: Object.freeze([[7, 6], [7, 7], [8, 6], [8, 7]]),
        holeRange: Object.freeze({ min: 5, max: 12 }),
        minTurns: 10,
        multiPairs: Object.freeze({ min: 2, max: 4 })
      }),
      hard: Object.freeze({
        gridSizes: Object.freeze([[8, 8], [9, 7], [9, 8], [9, 9]]),
        holeRange: Object.freeze({ min: 10, max: 20 }),
        minTurns: 14,
        multiPairs: Object.freeze({ min: 3, max: 5 })
      })
    })
  });

  const SINGLE_PATH_COLOR = Object.freeze({ id: 'single', value: '#7ad3ff' });
  const ENDPOINT_TYPES = Object.freeze({
    START: 'start',
    FINISH: 'finish'
  });
  const ENDPOINT_LABEL_CONFIG = Object.freeze({
    [ENDPOINT_TYPES.START]: Object.freeze({
      symbolKey: 'index.sections.theLine.endpointLabels.start.symbol',
      ariaKey: 'index.sections.theLine.endpointLabels.start.aria',
      fallbackSymbol: 'S',
      fallbackAria: 'Start tile'
    }),
    [ENDPOINT_TYPES.FINISH]: Object.freeze({
      symbolKey: 'index.sections.theLine.endpointLabels.finish.symbol',
      ariaKey: 'index.sections.theLine.endpointLabels.finish.aria',
      fallbackSymbol: 'F',
      fallbackAria: 'Finish tile'
    })
  });
  const COLOR_PALETTE = Object.freeze([
    Object.freeze({ id: 'amber', value: '#f7b731' }),
    Object.freeze({ id: 'azure', value: '#34d1ff' }),
    Object.freeze({ id: 'orchid', value: '#a162f7' }),
    Object.freeze({ id: 'coral', value: '#ff6b6b' }),
    Object.freeze({ id: 'emerald', value: '#7bd88f' }),
    Object.freeze({ id: 'rose', value: '#ff8ad6' }),
    Object.freeze({ id: 'citrus', value: '#ffc371' })
  ]);

  const COMPLETION_REWARD = Object.freeze({ chance: 0.5, gachaTickets: 1 });
  const AUTOSAVE_GAME_ID = 'theLine';
  const AUTOSAVE_VERSION = 1;
  const AUTOSAVE_DEBOUNCE_MS = 200;

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

  let autosaveTimer = null;
  let autosaveSuppressed = false;

  function sanitizeCoord(coord, width, height) {
    if (!coord || !Number.isFinite(coord.x) || !Number.isFinite(coord.y)) {
      return null;
    }
    let x = Math.round(coord.x);
    let y = Math.round(coord.y);
    if (Number.isFinite(width)) {
      x = Math.max(0, Math.min(width - 1, x));
    }
    if (Number.isFinite(height)) {
      y = Math.max(0, Math.min(height - 1, y));
    }
    if (x < 0 || y < 0) {
      return null;
    }
    return { x, y };
  }

  function serializeLevelCounters(counters) {
    const result = {
      single: { easy: 1, medium: 1, hard: 1 },
      multi: { easy: 1, medium: 1, hard: 1 }
    };
    if (!counters || typeof counters !== 'object') {
      return result;
    }
    ['single', 'multi'].forEach(group => {
      const source = counters[group] && typeof counters[group] === 'object' ? counters[group] : {};
      ['easy', 'medium', 'hard'].forEach(difficulty => {
        const value = clampInteger(source[difficulty], 1, 9999, result[group][difficulty]);
        result[group][difficulty] = Math.max(1, value || 1);
      });
    });
    return result;
  }

  function serializePuzzle(puzzle) {
    if (!puzzle || typeof puzzle !== 'object') {
      return null;
    }
    const width = clampInteger(puzzle.width, 3, 64, null);
    const height = clampInteger(puzzle.height, 3, 64, null);
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return null;
    }
    const totalCells = width * height;
    const blocked = new Set();
    const blockedSource = puzzle.blockedIndices instanceof Set
      ? Array.from(puzzle.blockedIndices)
      : Array.isArray(puzzle.blockedIndices)
        ? puzzle.blockedIndices
        : [];
    blockedSource.forEach(value => {
      const index = clampInteger(value, 0, totalCells - 1, null);
      if (Number.isFinite(index)) {
        blocked.add(index);
      }
    });
    const path = [];
    const pathSource = Array.isArray(puzzle.path) ? puzzle.path : [];
    pathSource.forEach(coord => {
      const sanitized = sanitizeCoord(coord, width, height);
      if (sanitized) {
        path.push(sanitized);
      }
    });
    if (!path.length) {
      return null;
    }
    const payload = {
      mode: puzzle.mode === 'multi' ? 'multi' : 'single',
      width,
      height,
      blocked: Array.from(blocked),
      path
    };
    if (payload.mode === 'multi') {
      const segments = [];
      if (Array.isArray(puzzle.segments)) {
        puzzle.segments.forEach(segment => {
          if (!segment || typeof segment !== 'object') {
            return;
          }
          const colorId = typeof segment.colorId === 'string' ? segment.colorId : null;
          if (!colorId) {
            return;
          }
          const cells = [];
          if (Array.isArray(segment.cells)) {
            segment.cells.forEach(coord => {
              const sanitized = sanitizeCoord(coord, width, height);
              if (sanitized) {
                cells.push(sanitized);
              }
            });
          }
          if (!cells.length) {
            return;
          }
          const start = sanitizeCoord(segment.start, width, height) || cells[0];
          const end = sanitizeCoord(segment.end, width, height) || cells[cells.length - 1];
          segments.push({
            colorId,
            colorValue: typeof segment.colorValue === 'string' ? segment.colorValue : '',
            cells,
            start,
            end
          });
        });
      }
      payload.segments = segments;
    } else {
      payload.endpoints = {
        start: sanitizeCoord(puzzle.endpoints && puzzle.endpoints.start, width, height) || path[0],
        end: sanitizeCoord(puzzle.endpoints && puzzle.endpoints.end, width, height) || path[path.length - 1]
      };
    }
    return payload;
  }

  function serializePaths() {
    const result = [];
    if (!state.paths || typeof state.paths.forEach !== 'function') {
      return result;
    }
    const width = state.board ? state.board.width : state.currentPuzzle ? state.currentPuzzle.width : null;
    const height = state.board ? state.board.height : state.currentPuzzle ? state.currentPuzzle.height : null;
    state.paths.forEach(pathState => {
      if (!pathState || typeof pathState.colorId !== 'string') {
        return;
      }
      const sequence = [];
      if (Array.isArray(pathState.sequence)) {
        pathState.sequence.forEach(cell => {
          if (!cell) {
            return;
          }
          const sanitized = sanitizeCoord({ x: cell.x, y: cell.y }, width, height);
          if (sanitized) {
            sequence.push(sanitized);
          }
        });
      }
      result.push({
        colorId: pathState.colorId,
        complete: Boolean(pathState.complete),
        sequence
      });
    });
    return result;
  }

  function buildAutosavePayload() {
    return {
      version: AUTOSAVE_VERSION,
      mode: state.mode === 'multi' ? 'multi' : 'single',
      difficulty: ['easy', 'medium', 'hard'].includes(state.difficulty) ? state.difficulty : 'easy',
      levelCounters: serializeLevelCounters(state.levelCounters),
      puzzle: serializePuzzle(state.currentPuzzle),
      paths: serializePaths(),
      cellsRemaining: clampInteger(state.cellsRemaining, 0, 10000, 0)
    };
  }

  function persistAutosaveNow() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    try {
      autosave.set(AUTOSAVE_GAME_ID, buildAutosavePayload());
    } catch (error) {
      // Ignore autosave persistence errors
    }
  }

  function scheduleAutosave() {
    if (autosaveSuppressed || typeof window === 'undefined') {
      return;
    }
    if (autosaveTimer != null) {
      window.clearTimeout(autosaveTimer);
    }
    autosaveTimer = window.setTimeout(() => {
      autosaveTimer = null;
      persistAutosaveNow();
    }, AUTOSAVE_DEBOUNCE_MS);
  }

  function flushAutosave() {
    if (typeof window !== 'undefined' && autosaveTimer != null) {
      window.clearTimeout(autosaveTimer);
      autosaveTimer = null;
    }
    persistAutosaveNow();
  }

  function withAutosaveSuppressed(callback) {
    autosaveSuppressed = true;
    try {
      callback();
    } finally {
      autosaveSuppressed = false;
    }
  }

  function normalizeLevelCountersPayload(payload) {
    return serializeLevelCounters(payload);
  }

  function rebuildPuzzleFromPayload(puzzlePayload, mode) {
    if (!puzzlePayload || typeof puzzlePayload !== 'object') {
      return null;
    }
    const width = clampInteger(puzzlePayload.width, 3, 64, null);
    const height = clampInteger(puzzlePayload.height, 3, 64, null);
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return null;
    }
    const totalCells = width * height;
    const blocked = new Set();
    const blockedSource = Array.isArray(puzzlePayload.blocked) ? puzzlePayload.blocked : [];
    blockedSource.forEach(value => {
      const index = clampInteger(value, 0, totalCells - 1, null);
      if (Number.isFinite(index)) {
        blocked.add(index);
      }
    });
    const path = [];
    const pathSource = Array.isArray(puzzlePayload.path) ? puzzlePayload.path : [];
    pathSource.forEach(coord => {
      const sanitized = sanitizeCoord(coord, width, height);
      if (sanitized) {
        path.push(sanitized);
      }
    });
    if (!path.length) {
      return null;
    }
    if (mode === 'multi') {
      const segments = [];
      const segmentsSource = Array.isArray(puzzlePayload.segments) ? puzzlePayload.segments : [];
      segmentsSource.forEach(segment => {
        if (!segment || typeof segment !== 'object') {
          return;
        }
        const colorId = typeof segment.colorId === 'string' ? segment.colorId : null;
        if (!colorId) {
          return;
        }
        const cells = [];
        if (Array.isArray(segment.cells)) {
          segment.cells.forEach(coord => {
            const sanitized = sanitizeCoord(coord, width, height);
            if (sanitized) {
              cells.push(sanitized);
            }
          });
        }
        if (!cells.length) {
          return;
        }
        const start = sanitizeCoord(segment.start, width, height) || cells[0];
        const end = sanitizeCoord(segment.end, width, height) || cells[cells.length - 1];
        segments.push({
          colorId,
          colorValue: typeof segment.colorValue === 'string' ? segment.colorValue : '',
          cells,
          start,
          end
        });
      });
      if (!segments.length) {
        return null;
      }
      return {
        mode: 'multi',
        width,
        height,
        blockedIndices: blocked,
        path,
        segments
      };
    }
    const endpoints = {
      start: sanitizeCoord(puzzlePayload.endpoints && puzzlePayload.endpoints.start, width, height) || path[0],
      end: sanitizeCoord(puzzlePayload.endpoints && puzzlePayload.endpoints.end, width, height) || path[path.length - 1]
    };
    return {
      mode: 'single',
      width,
      height,
      blockedIndices: blocked,
      path,
      endpoints
    };
  }

  function applySavedPaths(entries, puzzle) {
    if (!Array.isArray(entries)) {
      return;
    }
    const occupied = new Set();
    entries.forEach(entry => {
      if (!entry || typeof entry.colorId !== 'string') {
        return;
      }
      const pathState = state.paths.get(entry.colorId);
      if (!pathState) {
        return;
      }
      pathState.sequence = [];
      if (Array.isArray(entry.sequence)) {
        entry.sequence.forEach(coord => {
          const sanitized = sanitizeCoord(coord, puzzle.width, puzzle.height);
          if (!sanitized) {
            return;
          }
          const cell = getCellAt(sanitized.x, sanitized.y);
          if (!cell || cell.blocked) {
            return;
          }
          pathState.sequence.push(cell);
          occupyCellIfNeeded(cell, pathState);
          occupied.add(`${cell.x},${cell.y}`);
        });
      }
      pathState.complete = Boolean(entry.complete) && pathState.sequence.length > 0;
    });
    const totalCells = puzzle.width * puzzle.height;
    const blockedCount = puzzle.blockedIndices instanceof Set ? puzzle.blockedIndices.size : 0;
    const available = Math.max(0, totalCells - blockedCount);
    state.cellsRemaining = Math.max(0, available - occupied.size);
    updateRemainingValue();
  }

  function restoreFromAutosave() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return false;
    }
    let payload = null;
    try {
      payload = autosave.get(AUTOSAVE_GAME_ID);
    } catch (error) {
      return false;
    }
    if (!payload || typeof payload !== 'object') {
      return false;
    }
    if (Number(payload.version) !== AUTOSAVE_VERSION) {
      return false;
    }
    const mode = payload.mode === 'multi' ? 'multi' : 'single';
    const difficulty = ['easy', 'medium', 'hard'].includes(payload.difficulty)
      ? payload.difficulty
      : 'easy';
    const counters = normalizeLevelCountersPayload(payload.levelCounters);
    const puzzle = rebuildPuzzleFromPayload(payload.puzzle, mode);
    if (!puzzle) {
      return false;
    }
    const paths = Array.isArray(payload.paths) ? payload.paths : [];

    withAutosaveSuppressed(() => {
      state.mode = mode;
      state.difficulty = difficulty;
      state.levelCounters = counters;
      updateModeButtons();
      updateDifficultyButtons();
      updateLevelDisplay();
      clearMessageTimeout();
      state.messageTimeout = null;
      state.lastMessage = null;
      state.currentPuzzle = puzzle;
      renderPuzzle(puzzle);
      applySavedPaths(paths, puzzle);
      const hintKey = mode === 'multi'
        ? 'index.sections.theLine.messages.multi'
        : 'index.sections.theLine.messages.single';
      const fallback = mode === 'multi'
        ? 'Reliez chaque paire de couleurs sans croiser les chemins.'
        : 'Tracez un parcours continu qui visite chaque case.';
      setMessage(hintKey, fallback, { width: puzzle.width, height: puzzle.height });
    });

    persistAutosaveNow();
    return true;
  }

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
    const restored = restoreFromAutosave();
    if (!restored) {
      prepareNewPuzzle();
    }
    loadRemoteConfig();

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('beforeunload', () => {
        try {
          flushAutosave();
        } catch (error) {
          // Ignore autosave flush errors during unload
        }
      });
    }

    if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
      document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
          flushAutosave();
        }
      });
    }
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

  function formatIntegerLocalized(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    const integer = Math.trunc(numeric);
    try {
      return integer.toLocaleString();
    } catch (error) {
      return String(integer);
    }
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

  function clampInteger(value, min, max, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    const clamped = clampNumber(Math.round(numeric), min, max, Math.round(numeric));
    if (!Number.isFinite(clamped)) {
      return fallback;
    }
    const rounded = Math.round(clamped);
    if (typeof min === 'number' && rounded < min) {
      return min;
    }
    if (typeof max === 'number' && rounded > max) {
      return max;
    }
    return rounded;
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
      500,
      base.maxGenerationAttempts
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
      refreshEndpointLabels();
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
    scheduleAutosave();
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
    const targetHoles = holeMin === holeMax ? holeMin : randomInt(holeMin, holeMax);
    const minTurns = Math.max(2, difficultyConfig.minTurns || 0);
    const maxAttempts = clampNumber(config.maxGenerationAttempts, 20, 200, 50);

    let bestResult = null;
    let bestTurns = -1;

    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const result = generatePuzzleWithHoles(width, height, targetHoles);
      if (!result || !result.path || result.path.length < 2) {
        continue;
      }
      const turns = countTurns(result.path, width);
      if (mode === 'single' && turns < minTurns) {
        if (turns > bestTurns) {
          bestResult = result;
          bestTurns = turns;
        }
        continue;
      }
      return buildPuzzleFromPath(mode, width, height, result.blocked, result.path, difficultyConfig);
    }

    if (bestResult) {
      return buildPuzzleFromPath(mode, width, height, bestResult.blocked, bestResult.path, difficultyConfig);
    }

    const simplePath = createSimpleHamiltonianPath(width, height);
    if (simplePath && simplePath.length >= 2) {
      return buildPuzzleFromPath(mode, width, height, new Set(), simplePath, difficultyConfig);
    }
    return null;
  }

  function generatePuzzleWithHoles(width, height, targetHoles) {
    const totalCells = width * height;
    if (targetHoles <= 0) {
      const path = createSimpleHamiltonianPath(width, height);
      return path ? { path: path, blocked: new Set() } : null;
    }

    for (let holeCount = targetHoles; holeCount >= 1; holeCount -= 1) {
      const blocked = generateValidBlockedSet(width, height, holeCount);
      if (blocked.size === 0) {
        continue;
      }

      const path = findHamiltonianPathIterative(width, height, blocked);
      if (path && path.length >= 2) {
        return { path: path, blocked: blocked };
      }
    }

    return null;
  }

  function generateValidBlockedSet(width, height, targetCount) {
    const totalCells = width * height;
    const blocked = new Set();
    const maxCount = Math.min(targetCount, Math.floor(totalCells * 0.4));

    if (maxCount <= 0) {
      return blocked;
    }

    const corners = [0, width - 1, (height - 1) * width, totalCells - 1];
    const edges = [];
    const interior = [];

    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const index = y * width + x;
        const isCorner = (x === 0 || x === width - 1) && (y === 0 || y === height - 1);
        const isEdge = !isCorner && (x === 0 || x === width - 1 || y === 0 || y === height - 1);

        if (isCorner) {
          continue;
        } else if (isEdge) {
          edges.push(index);
        } else {
          interior.push(index);
        }
      }
    }

    shuffle(interior);
    shuffle(edges);

    const candidates = interior.concat(edges);

    for (let i = 0; i < candidates.length && blocked.size < maxCount; i += 1) {
      const candidate = candidates[i];
      blocked.add(candidate);

      if (!isGraphConnected(width, height, blocked)) {
        blocked.delete(candidate);
      }
    }

    return blocked;
  }

  function isGraphConnected(width, height, blocked) {
    const totalCells = width * height;
    const available = [];
    for (let i = 0; i < totalCells; i += 1) {
      if (!blocked.has(i)) {
        available.push(i);
      }
    }

    if (available.length <= 1) {
      return available.length === 1;
    }

    const visited = new Set();
    const queue = [available[0]];
    visited.add(available[0]);

    while (queue.length > 0) {
      const current = queue.shift();
      const x = current % width;
      const y = Math.floor(current / width);

      const neighbors = [];
      if (x > 0) neighbors.push(current - 1);
      if (x < width - 1) neighbors.push(current + 1);
      if (y > 0) neighbors.push(current - width);
      if (y < height - 1) neighbors.push(current + width);

      for (let i = 0; i < neighbors.length; i += 1) {
        const neighbor = neighbors[i];
        if (!blocked.has(neighbor) && !visited.has(neighbor)) {
          visited.add(neighbor);
          queue.push(neighbor);
        }
      }
    }

    return visited.size === available.length;
  }

  function findHamiltonianPathIterative(width, height, blocked) {
    const totalCells = width * height;
    const accessibleCount = totalCells - blocked.size;

    if (accessibleCount <= 0) {
      return null;
    }
    if (accessibleCount === 1) {
      for (let i = 0; i < totalCells; i += 1) {
        if (!blocked.has(i)) {
          return [i];
        }
      }
      return null;
    }

    const neighborCache = [];
    for (let index = 0; index < totalCells; index += 1) {
      if (blocked.has(index)) {
        neighborCache[index] = [];
        continue;
      }
      const x = index % width;
      const y = Math.floor(index / width);
      const neighbors = [];
      if (x > 0 && !blocked.has(index - 1)) neighbors.push(index - 1);
      if (x < width - 1 && !blocked.has(index + 1)) neighbors.push(index + 1);
      if (y > 0 && !blocked.has(index - width)) neighbors.push(index - width);
      if (y < height - 1 && !blocked.has(index + width)) neighbors.push(index + width);
      neighborCache[index] = neighbors;
    }

    const startCandidates = [];
    for (let i = 0; i < totalCells; i += 1) {
      if (!blocked.has(i)) {
        startCandidates.push(i);
      }
    }
    shuffle(startCandidates);

    const maxStartTries = Math.min(startCandidates.length, 6);
    const maxIterations = Math.min(50000, accessibleCount * 500);

    for (let s = 0; s < maxStartTries; s += 1) {
      const start = startCandidates[s];
      const result = searchPathIterative(start, accessibleCount, neighborCache, maxIterations);
      if (result) {
        return result;
      }
    }

    return null;
  }

  function searchPathIterative(start, targetLength, neighborCache, maxIterations) {
    const path = [start];
    const visited = new Set([start]);
    const choiceStack = [0];
    let iterations = 0;

    while (path.length > 0 && iterations < maxIterations) {
      iterations += 1;

      if (path.length === targetLength) {
        return path.slice();
      }

      const current = path[path.length - 1];
      const neighbors = neighborCache[current];

      const candidates = [];
      for (let i = 0; i < neighbors.length; i += 1) {
        if (!visited.has(neighbors[i])) {
          const n = neighbors[i];
          let degree = 0;
          const nNeighbors = neighborCache[n];
          for (let j = 0; j < nNeighbors.length; j += 1) {
            if (!visited.has(nNeighbors[j])) {
              degree += 1;
            }
          }
          candidates.push({ index: n, degree: degree });
        }
      }

      candidates.sort(function(a, b) { return a.degree - b.degree; });

      const choiceIndex = choiceStack[choiceStack.length - 1];

      if (choiceIndex < candidates.length) {
        const next = candidates[choiceIndex].index;
        choiceStack[choiceStack.length - 1] = choiceIndex + 1;

        path.push(next);
        visited.add(next);
        choiceStack.push(0);
      } else {
        const removed = path.pop();
        visited.delete(removed);
        choiceStack.pop();
      }
    }

    return null;
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

  function createSimpleHamiltonianPath(width, height) {
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return null;
    }
    const path = [];
    for (let y = 0; y < height; y += 1) {
      if (y % 2 === 0) {
        for (let x = 0; x < width; x += 1) {
          path.push(y * width + x);
        }
      } else {
        for (let x = width - 1; x >= 0; x -= 1) {
          path.push(y * width + x);
        }
      }
    }
    return path;
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

  function computeGenerationLimitMs(width, height, mode, difficulty) {
    const totalCells = width * height;
    let base = totalCells <= 36 ? 520 : totalCells <= 56 ? 820 : 1150;
    if (mode === 'multi') {
      base += 140;
    }
    if (difficulty === 'hard') {
      base += 220;
    }
    return base;
  }

  function createTimeLimiter(limitMs) {
    const limit = Number.isFinite(limitMs) && limitMs > 0 ? limitMs : 0;
    const getNow = typeof performance !== 'undefined'
      && performance
      && typeof performance.now === 'function'
        ? () => performance.now()
        : () => Date.now();
    const start = getNow();
    let timeoutReached = false;
    return {
      timedOut() {
        if (timeoutReached) {
          return true;
        }
        if (!limit) {
          return false;
        }
        timeoutReached = getNow() - start >= limit;
        return timeoutReached;
      },
      forceTimeout() {
        timeoutReached = true;
      },
      get hasTimedOut() {
        return timeoutReached;
      }
    };
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
    board.dataset.lineDifficulty = state.difficulty;

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
          endpointType: null,
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
        markEndpoint(startCell, color, ENDPOINT_TYPES.START);
        markEndpoint(endCell, color, ENDPOINT_TYPES.FINISH);
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
      markEndpoint(startCell, SINGLE_PATH_COLOR, ENDPOINT_TYPES.START);
      markEndpoint(endCell, SINGLE_PATH_COLOR, ENDPOINT_TYPES.FINISH);
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

  function markEndpoint(cell, color, type) {
    if (!cell) {
      return;
    }
    cell.endpointColor = color.id;
    cell.endpointColorValue = color.value;
    cell.endpointType = type === ENDPOINT_TYPES.START || type === ENDPOINT_TYPES.FINISH
      ? type
      : null;
    cell.element.classList.add('the-line__cell--endpoint');
    cell.element.classList.toggle('the-line__cell--endpoint-start', cell.endpointType === ENDPOINT_TYPES.START);
    cell.element.classList.toggle('the-line__cell--endpoint-finish', cell.endpointType === ENDPOINT_TYPES.FINISH);
    if (cell.endpointType) {
      cell.element.dataset.lineEndpoint = cell.endpointType;
    } else {
      delete cell.element.dataset.lineEndpoint;
    }
    updateEndpointLabel(cell);
    cell.element.dataset.lineColor = color.id;
    cell.element.style.setProperty('--line-color-value', color.value);
  }

  function updateEndpointLabel(cell) {
    if (!cell || !cell.element) {
      return;
    }
    const type = cell.endpointType;
    const config = type ? ENDPOINT_LABEL_CONFIG[type] : null;
    if (!config) {
      delete cell.element.dataset.lineEndpointLabel;
      cell.element.removeAttribute('aria-label');
      return;
    }
    const symbol = translate(config.symbolKey, config.fallbackSymbol);
    const trimmedSymbol = typeof symbol === 'string' ? symbol.trim() : '';
    if (trimmedSymbol) {
      cell.element.dataset.lineEndpointLabel = trimmedSymbol;
    } else {
      delete cell.element.dataset.lineEndpointLabel;
    }
    const ariaText = translate(config.ariaKey, config.fallbackAria);
    const trimmedAria = typeof ariaText === 'string' ? ariaText.trim() : '';
    if (trimmedAria) {
      const finalLabel = trimmedSymbol ? `${trimmedAria} (${trimmedSymbol})` : trimmedAria;
      cell.element.setAttribute('aria-label', finalLabel);
    } else if (trimmedSymbol) {
      cell.element.setAttribute('aria-label', trimmedSymbol);
    } else {
      cell.element.removeAttribute('aria-label');
    }
  }

  function refreshEndpointLabels() {
    if (!state.paths || typeof state.paths.forEach !== 'function') {
      return;
    }
    state.paths.forEach(pathState => {
      if (!pathState || !Array.isArray(pathState.endpoints)) {
        return;
      }
      pathState.endpoints.forEach(cell => {
        if (!cell) {
          return;
        }
        updateEndpointLabel(cell);
      });
    });
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
    scheduleAutosave();
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
    scheduleAutosave();
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

  function getCellElementFromEvent(event) {
    if (!event) {
      return null;
    }
    const directTarget = event.target && typeof event.target.closest === 'function'
      ? event.target.closest('.the-line__cell')
      : null;
    if (directTarget) {
      return directTarget;
    }
    if (typeof document === 'undefined' || typeof document.elementFromPoint !== 'function') {
      return null;
    }
    const fallbackTarget = document.elementFromPoint(event.clientX, event.clientY);
    if (fallbackTarget && typeof fallbackTarget.closest === 'function') {
      return fallbackTarget.closest('.the-line__cell');
    }
    return null;
  }

  function handlePointerDown(event) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    const target = getCellElementFromEvent(event);
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
    const target = getCellElementFromEvent(event);
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
    maybeAwardCompletionReward();
    counters[state.difficulty] = currentLevel + 1;
    updateLevelDisplay();
    scheduleAutosave();
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

  function maybeAwardCompletionReward() {
    if (!COMPLETION_REWARD || Math.random() >= COMPLETION_REWARD.chance) {
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
      const tickets = Math.max(1, COMPLETION_REWARD.gachaTickets);
      gained = awardGacha(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('The Line: unable to grant gacha tickets', error);
      gained = 0;
    }
    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = gained > 1 ? 's' : '';
      showToast(translate(
        'scripts.arcade.theLine.rewards.gachaWin',
        'Ticket gacha obtenu !',
        { count: formatIntegerLocalized(gained), suffix }
      ));
    }
  }

})();
