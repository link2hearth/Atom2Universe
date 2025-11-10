(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/lights-out.json';
  const GAME_ID = 'lightsOut';
  const SAVE_VERSION = 1;
  const AUTOSAVE_DEBOUNCE_MS = 150;
  const HINT_FLASH_DURATION_MS = 2400;
  const DEFAULT_CONFIG = Object.freeze({
    autoAdvanceDelayMs: 1800,
    difficulties: Object.freeze({
      easy: Object.freeze({
        gridSizes: Object.freeze([5]),
        shuffleMoves: Object.freeze({ min: 8, max: 14 }),
        gachaTickets: 1
      }),
      medium: Object.freeze({
        gridSizes: Object.freeze([6]),
        shuffleMoves: Object.freeze({ min: 14, max: 24 }),
        gachaTickets: 2
      }),
      hard: Object.freeze({
        gridSizes: Object.freeze([7, 8]),
        shuffleMoves: Object.freeze({ min: 22, max: 36 }),
        gachaTickets: 3
      })
    })
  });

  const state = {
    config: DEFAULT_CONFIG,
    difficulty: 'easy',
    levelCounters: { easy: 1, medium: 1, hard: 1 },
    board: [],
    size: 5,
    isBusy: false,
    messageTimeout: null,
    autoAdvanceTimeout: null,
    autoAdvanceAt: null,
    elements: null,
    lastMessage: null,
    languageListenerAttached: false,
    languageChangeHandler: null,
    autosaveTimer: null,
    hintTimeout: null,
    hintActiveCell: null
  };

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

  function deepClone(value) {
    if (value == null) {
      return value;
    }
    try {
      return JSON.parse(JSON.stringify(value));
    } catch (error) {
      return null;
    }
  }

  function sanitizeLevelCounters(source) {
    const counters = { easy: 1, medium: 1, hard: 1 };
    if (!source || typeof source !== 'object') {
      return counters;
    }
    ['easy', 'medium', 'hard'].forEach(key => {
      counters[key] = clampInteger(source[key], 1, 9999, counters[key]);
    });
    return counters;
  }

  function sanitizeMessage(message) {
    if (!message || typeof message !== 'object') {
      return null;
    }
    const key = typeof message.key === 'string' ? message.key : null;
    const fallback = typeof message.fallback === 'string' ? message.fallback : '';
    const params = message.params && typeof message.params === 'object'
      ? deepClone(message.params)
      : null;
    if (!key && !fallback) {
      return null;
    }
    return {
      key,
      fallback,
      params: params && typeof params === 'object' ? params : null
    };
  }

  function cloneBoard(board, size) {
    if (!Array.isArray(board) || board.length !== size) {
      return null;
    }
    const cloned = new Array(size);
    for (let row = 0; row < size; row += 1) {
      const sourceRow = board[row];
      if (!Array.isArray(sourceRow) || sourceRow.length !== size) {
        return null;
      }
      const clonedRow = new Array(size);
      for (let col = 0; col < size; col += 1) {
        clonedRow[col] = Boolean(sourceRow[col]);
      }
      cloned[row] = clonedRow;
    }
    return cloned;
  }

  function buildSavePayload() {
    if (!Array.isArray(state.board) || state.board.length === 0) {
      return null;
    }
    const size = clampInteger(state.size, 3, 12, null);
    if (!Number.isFinite(size)) {
      return null;
    }
    const boardClone = cloneBoard(state.board, size);
    if (!boardClone) {
      return null;
    }
    const lastMessage = state.lastMessage && typeof state.lastMessage === 'object'
      ? {
          key: typeof state.lastMessage.key === 'string' ? state.lastMessage.key : null,
          fallback: typeof state.lastMessage.fallback === 'string' ? state.lastMessage.fallback : '',
          params: state.lastMessage.params && typeof state.lastMessage.params === 'object'
            ? deepClone(state.lastMessage.params)
            : null
        }
      : null;
    const difficulty = ['easy', 'medium', 'hard'].includes(state.difficulty)
      ? state.difficulty
      : 'easy';
    return {
      version: SAVE_VERSION,
      difficulty,
      levelCounters: sanitizeLevelCounters(state.levelCounters),
      size,
      board: boardClone,
      isBusy: Boolean(state.isBusy),
      autoAdvanceAt: Number.isFinite(state.autoAdvanceAt) ? state.autoAdvanceAt : null,
      lastMessage
    };
  }

  function persistAutosaveNow() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    const payload = buildSavePayload();
    if (!payload) {
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

  function scheduleAutosave() {
    if (typeof window === 'undefined') {
      return;
    }
    if (state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
    }
    state.autosaveTimer = window.setTimeout(() => {
      state.autosaveTimer = null;
      persistAutosaveNow();
    }, AUTOSAVE_DEBOUNCE_MS);
  }

  function flushAutosave() {
    if (typeof window === 'undefined') {
      return;
    }
    if (state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
      state.autosaveTimer = null;
    }
    persistAutosaveNow();
  }

  function normalizeSavedState(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const difficulty = ['easy', 'medium', 'hard'].includes(raw.difficulty)
      ? raw.difficulty
      : 'easy';
    const size = clampInteger(raw.size, 3, 12, null);
    if (!Number.isFinite(size)) {
      return null;
    }
    const board = cloneBoard(raw.board, size);
    if (!board) {
      return null;
    }
    const levelCounters = sanitizeLevelCounters(raw.levelCounters);
    const message = sanitizeMessage(raw.lastMessage);
    const storedAutoAdvanceAt = Number(raw.autoAdvanceAt);
    const autoAdvanceAt = Number.isFinite(storedAutoAdvanceAt) ? storedAutoAdvanceAt : null;
    const isBusy = raw.isBusy === true && isBoardCleared(board) && autoAdvanceAt != null;
    return {
      difficulty,
      size,
      board,
      levelCounters,
      lastMessage: message,
      isBusy,
      autoAdvanceAt: isBusy ? autoAdvanceAt : null
    };
  }

  function restoreStateFromAutosave() {
    const autosave = getAutosaveApi();
    if (!autosave || typeof autosave.get !== 'function') {
      return false;
    }
    let rawState = null;
    try {
      rawState = autosave.get(GAME_ID);
    } catch (error) {
      return false;
    }
    const saved = normalizeSavedState(rawState);
    if (!saved) {
      return false;
    }
    state.difficulty = saved.difficulty;
    state.size = saved.size;
    state.board = saved.board;
    state.levelCounters = saved.levelCounters;
    state.lastMessage = saved.lastMessage;
    state.isBusy = saved.isBusy;
    state.autoAdvanceAt = saved.autoAdvanceAt;
    return true;
  }

  function resumeAutoAdvanceIfNeeded() {
    if (!state.isBusy) {
      return;
    }
    const target = Number(state.autoAdvanceAt);
    if (!Number.isFinite(target)) {
      state.isBusy = false;
      state.autoAdvanceAt = null;
      scheduleAutosave();
      prepareNewPuzzle();
      return;
    }
    const now = Date.now();
    const remaining = target - now;
    if (remaining <= 0) {
      state.isBusy = false;
      state.autoAdvanceAt = null;
      scheduleAutosave();
      prepareNewPuzzle();
      return;
    }
    state.autoAdvanceTimeout = window.setTimeout(() => {
      state.autoAdvanceTimeout = null;
      state.autoAdvanceAt = null;
      state.isBusy = false;
      prepareNewPuzzle();
    }, remaining);
  }

  onReady(() => {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    state.config = normalizeConfig(DEFAULT_CONFIG);
    const restored = restoreStateFromAutosave();
    updateDifficultyButtons();
    setupButtons();
    setupLanguageChangeListener();
    if (restored) {
      renderBoard();
      refreshBoardLabels();
      updateLevelDisplay(true);
      if (state.lastMessage) {
        setMessage(state.lastMessage.key, state.lastMessage.fallback, state.lastMessage.params, true);
      } else {
        setMessage(
          'index.sections.lightsOut.messages.intro',
          'Éteignez toutes les lumières. Chaque clic inverse la case visée et ses voisines orthogonales.'
        );
      }
      resumeAutoAdvanceIfNeeded();
      scheduleAutosave();
    } else {
      prepareNewPuzzle();
    }
    loadRemoteConfig();
  });

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function getElements() {
    const section = document.getElementById('lightsOut');
    if (!section) {
      return null;
    }
    const board = document.getElementById('lightsOutBoard');
    if (!board) {
      return null;
    }
    return {
      section,
      board,
      message: document.getElementById('lightsOutMessage'),
      difficultyButtons: Array.from(section.querySelectorAll('[data-lights-difficulty]')),
      newGridButton: document.getElementById('lightsOutNewButton'),
      helpButton: document.getElementById('lightsOutHelpButton')
    };
  }

  function setupButtons() {
    const { difficultyButtons, newGridButton, helpButton } = state.elements;
    difficultyButtons.forEach(button => {
      button.addEventListener('click', () => {
        const value = button.dataset.lightsDifficulty;
        setDifficulty(value);
      });
    });
    if (newGridButton) {
      newGridButton.addEventListener('click', () => {
        clearTimeouts();
        prepareNewPuzzle();
      });
    }
    if (helpButton) {
      helpButton.addEventListener('click', handleHelpRequest);
    }
  }

  function setupLanguageChangeListener() {
    if (state.languageListenerAttached) {
      return;
    }
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
      return;
    }
    const handler = () => {
      updateLevelDisplay(true);
      refreshBoardLabels();
      if (state.lastMessage) {
        setMessage(state.lastMessage.key, state.lastMessage.fallback, state.lastMessage.params, true);
      }
    };
    state.languageChangeHandler = handler;
    window.addEventListener('i18n:languagechange', handler);
    state.languageListenerAttached = true;
  }

  function clearHint() {
    if (state.hintTimeout != null && typeof window !== 'undefined') {
      window.clearTimeout(state.hintTimeout);
      state.hintTimeout = null;
    }
    if (state.hintActiveCell && state.hintActiveCell.classList) {
      state.hintActiveCell.classList.remove('lights-out__cell--hint');
    }
    state.hintActiveCell = null;
  }

  function clearTimeouts() {
    if (state.messageTimeout != null) {
      window.clearTimeout(state.messageTimeout);
      state.messageTimeout = null;
    }
    if (state.autoAdvanceTimeout != null) {
      window.clearTimeout(state.autoAdvanceTimeout);
      state.autoAdvanceTimeout = null;
    }
    state.autoAdvanceAt = null;
    clearHint();
  }

  function normalizeConfig(source) {
    const base = DEFAULT_CONFIG;
    if (!source || typeof source !== 'object') {
      return base;
    }
    const autoAdvanceDelayMs = clampInteger(
      source.autoAdvanceDelayMs,
      600,
      6000,
      base.autoAdvanceDelayMs
    );
    const difficulties = ['easy', 'medium', 'hard'].reduce((acc, key) => {
      acc[key] = normalizeDifficultyConfig(source.difficulties?.[key], base.difficulties[key]);
      return acc;
    }, {});
    return {
      autoAdvanceDelayMs,
      difficulties
    };
  }

  function normalizeDifficultyConfig(source, fallback) {
    const base = fallback || DEFAULT_CONFIG.difficulties.easy;
    const gridSizes = Array.isArray(source?.gridSizes) && source.gridSizes.length
      ? source.gridSizes
        .map(size => clampInteger(size, 3, 12, null))
        .filter(size => size != null)
      : [...base.gridSizes];
    const shuffleSource = source?.shuffleMoves;
    const shuffleMin = clampInteger(shuffleSource?.min, 1, 200, base.shuffleMoves.min);
    const shuffleMax = clampInteger(shuffleSource?.max, shuffleMin, 400, Math.max(base.shuffleMoves.max, shuffleMin));
    const gachaTickets = clampInteger(source?.gachaTickets, 0, 99, base.gachaTickets);
    return {
      gridSizes: gridSizes.length ? gridSizes : [...base.gridSizes],
      shuffleMoves: { min: shuffleMin, max: shuffleMax },
      gachaTickets
    };
  }

  function clampInteger(value, min, max, fallback) {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) {
      const clamped = Math.min(Math.max(Math.floor(numeric), min), max);
      return clamped;
    }
    return fallback;
  }

  function loadRemoteConfig() {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          state.config = normalizeConfig(data);
          updateLevelDisplay(true);
        }
      })
      .catch(error => {
        console.warn('All Off config load error', error);
      });
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
    clearTimeouts();
    prepareNewPuzzle();
  }

  function updateDifficultyButtons() {
    const { difficultyButtons } = state.elements;
    difficultyButtons.forEach(button => {
      const value = button.dataset.lightsDifficulty;
      const isActive = value === state.difficulty;
      button.classList.toggle('lights-out__toggle--active', isActive);
      button.setAttribute('aria-pressed', String(isActive));
    });
  }

  function prepareNewPuzzle() {
    clearHint();
    const config = getDifficultyConfig(state.difficulty);
    const size = pickGridSize(config);
    state.isBusy = false;
    state.autoAdvanceAt = null;
    state.size = size;
    state.board = createEmptyBoard(size);
    shuffleBoard(state.board, config.shuffleMoves);
    if (isBoardCleared(state.board)) {
      toggleAt(state.board, size, Math.floor(size / 2), Math.floor(size / 2));
    }
    renderBoard();
    refreshBoardLabels();
    updateLevelDisplay();
    setMessage(
      'index.sections.lightsOut.messages.intro',
      'Éteignez toutes les lumières. Chaque clic inverse la case visée et ses voisines orthogonales.'
    );
    scheduleAutosave();
  }

  function getDifficultyConfig(difficulty) {
    const config = state.config?.difficulties?.[difficulty];
    if (config && typeof config === 'object') {
      return config;
    }
    return DEFAULT_CONFIG.difficulties[difficulty] || DEFAULT_CONFIG.difficulties.easy;
  }

  function pickGridSize(config) {
    const sizes = Array.isArray(config.gridSizes) && config.gridSizes.length
      ? config.gridSizes
      : DEFAULT_CONFIG.difficulties.easy.gridSizes;
    const index = Math.floor(Math.random() * sizes.length);
    const size = clampInteger(sizes[index], 3, 12, sizes[0] || 5);
    return size;
  }

  function createEmptyBoard(size) {
    const board = new Array(size);
    for (let row = 0; row < size; row += 1) {
      board[row] = new Array(size).fill(false);
    }
    return board;
  }

  function shuffleBoard(board, shuffleMoves) {
    const size = board.length;
    const minMoves = clampInteger(shuffleMoves?.min, 1, 400, size * 2);
    const maxMoves = clampInteger(shuffleMoves?.max, minMoves, 600, minMoves * 2);
    const moveCount = randomInt(minMoves, maxMoves);
    for (let move = 0; move < moveCount; move += 1) {
      const row = Math.floor(Math.random() * size);
      const col = Math.floor(Math.random() * size);
      toggleAt(board, size, row, col);
    }
  }

  function toggleAt(board, size, row, col) {
    const positions = [
      [row, col],
      [row - 1, col],
      [row + 1, col],
      [row, col - 1],
      [row, col + 1]
    ];
    positions.forEach(([r, c]) => {
      if (r >= 0 && r < size && c >= 0 && c < size) {
        board[r][c] = !board[r][c];
      }
    });
  }

  function renderBoard() {
    clearHint();
    const { board: boardElement } = state.elements;
    const size = state.size;
    boardElement.innerHTML = '';
    boardElement.style.setProperty('--lights-out-size', String(size));
    boardElement.setAttribute('aria-rowcount', String(size));
    boardElement.setAttribute('aria-colcount', String(size));
    for (let row = 0; row < size; row += 1) {
      for (let col = 0; col < size; col += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'lights-out__cell';
        button.dataset.row = String(row);
        button.dataset.column = String(col);
        button.setAttribute('aria-pressed', 'false');
        button.addEventListener('click', () => {
          handleCellInteraction(row, col);
        });
        button.addEventListener('keydown', event => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            handleCellInteraction(row, col);
          }
        });
        boardElement.appendChild(button);
      }
    }
    updateCells();
  }

  function getCellElement(row, col) {
    const { board: boardElement } = state.elements;
    if (!boardElement) {
      return null;
    }
    if (!Number.isInteger(row) || !Number.isInteger(col)) {
      return null;
    }
    if (row < 0 || col < 0 || row >= state.size || col >= state.size) {
      return null;
    }
    const index = row * state.size + col;
    return boardElement.children[index] || null;
  }

  function applyHintHighlight(row, col) {
    const cell = getCellElement(row, col);
    if (!cell) {
      return;
    }
    clearHint();
    cell.classList.add('lights-out__cell--hint');
    state.hintActiveCell = cell;
    if (typeof window !== 'undefined') {
      state.hintTimeout = window.setTimeout(() => {
        if (state.hintActiveCell === cell) {
          cell.classList.remove('lights-out__cell--hint');
          state.hintActiveCell = null;
        }
        state.hintTimeout = null;
      }, HINT_FLASH_DURATION_MS);
    }
  }

  function handleHelpRequest() {
    if (state.isBusy) {
      return;
    }
    if (isBoardCleared(state.board)) {
      clearHint();
      setMessage(
        'index.sections.lightsOut.messages.hintSolved',
        'La grille est déjà éteinte : aucun indice nécessaire !'
      );
      return;
    }
    const hint = findHintMove(state.board, state.size);
    if (!hint) {
      clearHint();
      setMessage(
        'index.sections.lightsOut.messages.hintUnavailable',
        'Impossible de calculer un indice pour cette grille pour le moment.'
      );
      return;
    }
    applyHintHighlight(hint.row, hint.col);
    setMessage(
      'index.sections.lightsOut.messages.hint',
      'Indice : cliquez sur la case ligne {row}, colonne {column}.',
      { row: hint.row + 1, column: hint.col + 1 }
    );
  }

  function findHintMove(board, size) {
    if (!Array.isArray(board) || !Number.isInteger(size) || size <= 0) {
      return null;
    }
    const solution = solveLightsOut(board, size);
    if (!solution) {
      return null;
    }
    for (let index = 0; index < solution.length; index += 1) {
      if (solution[index] === 1) {
        return {
          row: Math.floor(index / size),
          col: index % size
        };
      }
    }
    return null;
  }

  function solveLightsOut(board, size) {
    const matrix = buildAugmentedMatrix(board, size);
    if (!matrix) {
      return null;
    }
    const total = size * size;
    const pivotColumns = new Array(total).fill(-1);
    let pivotRow = 0;
    for (let col = 0; col < total && pivotRow < total; col += 1) {
      let candidate = pivotRow;
      while (candidate < total && matrix[candidate][col] !== 1) {
        candidate += 1;
      }
      if (candidate >= total) {
        continue;
      }
      if (candidate !== pivotRow) {
        const tmp = matrix[pivotRow];
        matrix[pivotRow] = matrix[candidate];
        matrix[candidate] = tmp;
      }
      pivotColumns[pivotRow] = col;
      for (let row = 0; row < total; row += 1) {
        if (row !== pivotRow && matrix[row][col] === 1) {
          const source = matrix[pivotRow];
          const target = matrix[row];
          for (let k = col; k <= total; k += 1) {
            target[k] ^= source[k];
          }
        }
      }
      pivotRow += 1;
    }
    for (let row = 0; row < total; row += 1) {
      let hasCoefficient = false;
      for (let col = 0; col < total; col += 1) {
        if (matrix[row][col] === 1) {
          hasCoefficient = true;
          break;
        }
      }
      if (!hasCoefficient && matrix[row][total] === 1) {
        return null;
      }
    }
    const solution = new Array(total).fill(0);
    for (let row = pivotRow - 1; row >= 0; row -= 1) {
      const pivotCol = pivotColumns[row];
      if (pivotCol < 0) {
        continue;
      }
      let value = matrix[row][total];
      for (let col = pivotCol + 1; col < total; col += 1) {
        if (matrix[row][col] === 1 && solution[col] === 1) {
          value ^= 1;
        }
      }
      solution[pivotCol] = value;
    }
    return solution;
  }

  function buildAugmentedMatrix(board, size) {
    if (!Array.isArray(board) || board.length !== size) {
      return null;
    }
    const total = size * size;
    const matrix = new Array(total);
    const indexFromCoord = (row, col) => row * size + col;
    for (let row = 0; row < size; row += 1) {
      const boardRow = board[row];
      if (!Array.isArray(boardRow) || boardRow.length !== size) {
        return null;
      }
      for (let col = 0; col < size; col += 1) {
        const matrixRow = new Uint8Array(total + 1);
        const equationIndex = indexFromCoord(row, col);
        const positions = [
          [row, col],
          [row - 1, col],
          [row + 1, col],
          [row, col - 1],
          [row, col + 1]
        ];
        for (let i = 0; i < positions.length; i += 1) {
          const [r, c] = positions[i];
          if (r >= 0 && r < size && c >= 0 && c < size) {
            const variableIndex = indexFromCoord(r, c);
            matrixRow[variableIndex] = 1;
          }
        }
        matrixRow[total] = boardRow[col] ? 1 : 0;
        matrix[equationIndex] = matrixRow;
      }
    }
    return matrix;
  }

  function handleCellInteraction(row, col) {
    if (state.isBusy) {
      return;
    }
    clearTimeouts();
    toggleAt(state.board, state.size, row, col);
    updateCells();
    refreshBoardLabels();
    if (isBoardCleared(state.board)) {
      handleWin();
    } else {
      scheduleAutosave();
    }
  }

  function updateCells() {
    const { board: boardElement } = state.elements;
    const cells = boardElement ? boardElement.children : [];
    let index = 0;
    for (let row = 0; row < state.size; row += 1) {
      for (let col = 0; col < state.size; col += 1) {
        const cell = cells[index];
        if (!cell) {
          index += 1;
          continue;
        }
        const isOn = Boolean(state.board[row][col]);
        cell.classList.toggle('lights-out__cell--on', isOn);
        cell.setAttribute('aria-pressed', String(isOn));
        cell.setAttribute('data-state', isOn ? 'on' : 'off');
        index += 1;
      }
    }
  }

  function refreshBoardLabels() {
    const { board: boardElement } = state.elements;
    if (!boardElement) {
      return;
    }
    const cells = boardElement.children;
    let index = 0;
    for (let row = 0; row < state.size; row += 1) {
      for (let col = 0; col < state.size; col += 1) {
        const cell = cells[index];
        if (cell) {
          const isOn = Boolean(state.board[row][col]);
          const label = translate(
            'index.sections.lightsOut.cell.label',
            'Ligne {row}, colonne {column} : {state}',
            {
              row: row + 1,
              column: col + 1,
              state: translate(
                isOn
                  ? 'index.sections.lightsOut.cell.states.on'
                  : 'index.sections.lightsOut.cell.states.off',
                isOn ? 'allumée' : 'éteinte'
              )
            }
          );
          cell.setAttribute('aria-label', label);
          const title = translate(
            isOn
              ? 'index.sections.lightsOut.cell.states.on'
              : 'index.sections.lightsOut.cell.states.off',
            isOn ? 'Lumière allumée' : 'Lumière éteinte'
          );
          cell.title = title;
        }
        index += 1;
      }
    }
    const boardLabel = translate(
      'index.sections.lightsOut.board',
      'Grille All Off'
    );
    boardElement.setAttribute('aria-label', boardLabel);
  }

  function isBoardCleared(board) {
    for (let row = 0; row < board.length; row += 1) {
      for (let col = 0; col < board[row].length; col += 1) {
        if (board[row][col]) {
          return false;
        }
      }
    }
    return true;
  }

  function handleWin() {
    clearHint();
    state.isBusy = true;
    const counters = state.levelCounters;
    const currentLevel = counters[state.difficulty] || 1;
    setMessage(
      'index.sections.lightsOut.messages.victory',
      `Grille réussie ! Niveau ${currentLevel} terminé.`,
      { level: currentLevel }
    );
    awardDifficultyTickets();
    counters[state.difficulty] = currentLevel + 1;
    updateLevelDisplay();
    const delay = clampInteger(state.config.autoAdvanceDelayMs, 600, 6000, DEFAULT_CONFIG.autoAdvanceDelayMs);
    state.autoAdvanceAt = Date.now() + delay;
    state.autoAdvanceTimeout = window.setTimeout(() => {
      state.autoAdvanceTimeout = null;
      state.autoAdvanceAt = null;
      state.isBusy = false;
      prepareNewPuzzle();
    }, delay);
    scheduleAutosave();
  }

  function awardDifficultyTickets() {
    const config = getDifficultyConfig(state.difficulty);
    const tickets = clampInteger(config.gachaTickets, 0, 99, 0);
    if (tickets <= 0) {
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
      console.warn('All Off: unable to grant gacha tickets', error);
      gained = 0;
    }
    if (Number.isFinite(gained) && gained > 0 && typeof showToast === 'function') {
      const suffix = gained > 1 ? 's' : '';
      const formattedCount = formatIntegerLocalized(gained);
      showToast(translate(
        'scripts.arcade.lightsOut.rewards.gacha',
        'Ticket{suffix} gacha obtenu{suffix} : {formattedCount}',
        { count: gained, formattedCount, suffix }
      ));
    }
  }

  function setMessage(key, fallback, params, skipCache) {
    const { message } = state.elements;
    if (!message) {
      return;
    }
    const text = translate(key, fallback, params);
    message.textContent = text;
    if (!skipCache) {
      state.lastMessage = {
        key: typeof key === 'string' ? key : null,
        fallback: typeof fallback === 'string' ? fallback : '',
        params: params && typeof params === 'object' ? deepClone(params) : null
      };
      scheduleAutosave();
    }
  }

  function updateLevelDisplay(skipMessageUpdate) {
    if (skipMessageUpdate !== true && state.lastMessage) {
      setMessage(state.lastMessage.key, state.lastMessage.fallback, state.lastMessage.params, true);
    }
  }

  function randomInt(min, max) {
    const range = Math.max(0, max - min);
    return min + Math.floor(Math.random() * (range + 1));
  }

  function formatTemplate(template, params) {
    if (typeof template !== 'string') {
      return '';
    }
    if (!template || !params || typeof params !== 'object') {
      return template;
    }
    return template.replace(/\{\s*([^{}\s]+)\s*\}/g, (match, key) => {
      if (Object.prototype.hasOwnProperty.call(params, key)) {
        const value = params[key];
        return value == null ? '' : String(value);
      }
      return match;
    });
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return typeof fallback === 'string' ? formatTemplate(fallback, params) : '';
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
        console.warn('All Off translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return formatTemplate(fallback, params);
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
      console.warn('All Off number formatting error', error);
    }
    return String(Math.floor(Math.abs(numeric)));
  }

  const api = {
    onEnter() {
      refreshBoardLabels();
    },
    onLeave() {
      flushAutosave();
    }
  };

  window.lightsOutArcade = api;
})();
