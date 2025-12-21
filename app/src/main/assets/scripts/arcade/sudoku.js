(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const DEFAULT_LEVEL_CLUE_RANGES = Object.freeze({
    facile: Object.freeze({ min: 30, max: 34 }),
    moyen: Object.freeze({ min: 24, max: 28 }),
    difficile: Object.freeze({ min: 18, max: 22 })
  });
  const DEFAULT_DIGIT_SCALE_OPTIONS = Object.freeze([
    Object.freeze({
      value: 1,
      labelKey: 'index.sections.sudoku.toolbar.digitScale.options.x1',
      fallback: 'x1'
    }),
    Object.freeze({
      value: 1.5,
      labelKey: 'index.sections.sudoku.toolbar.digitScale.options.x1_5',
      fallback: 'x1.5'
    }),
    Object.freeze({
      value: 2,
      labelKey: 'index.sections.sudoku.toolbar.digitScale.options.x2',
      fallback: 'x2'
    }),
    Object.freeze({
      value: 3,
      labelKey: 'index.sections.sudoku.toolbar.digitScale.options.x3',
      fallback: 'x3'
    })
  ]);
  const DEFAULT_DIGIT_SCALE = 1;

  function normalizeCompletionReward(raw) {
    if (raw === false) {
      return { enabled: false, settings: null };
    }

    const source = raw && typeof raw === 'object' ? raw : {};
    if (source.enabled === false) {
      return { enabled: false, settings: null };
    }

    const offlineBonus = (() => {
      if (source.offlineBonus && typeof source.offlineBonus === 'object') {
        return source.offlineBonus;
      }
      if (source.levels && typeof source.levels === 'object') {
        return source.levels;
      }
      if (Object.values(source).every(value => value && typeof value === 'object')) {
        return source;
      }
      return null;
    })();

    if (!offlineBonus || Object.keys(offlineBonus).length === 0) {
      return { enabled: false, settings: null };
    }

    const settings = source.offlineBonus ? source : { ...source, offlineBonus };
    return { enabled: true, settings };
  }

  const COMPLETION_REWARD_CONFIG = normalizeCompletionReward(
    GLOBAL_CONFIG
    && GLOBAL_CONFIG.arcade
    && GLOBAL_CONFIG.arcade.sudoku
    && GLOBAL_CONFIG.arcade.sudoku.rewards
    ? GLOBAL_CONFIG.arcade.sudoku.rewards
    : null
  );

  function getNowMs() {
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
      return performance.now();
    }
    return Date.now();
  }

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function getConfiguredClueRange(level) {
    const fallback = DEFAULT_LEVEL_CLUE_RANGES[level] || DEFAULT_LEVEL_CLUE_RANGES.moyen;
    if (!GLOBAL_CONFIG || !GLOBAL_CONFIG.arcade || !GLOBAL_CONFIG.arcade.sudoku) {
      return fallback;
    }
    const ranges = GLOBAL_CONFIG.arcade.sudoku.levelClues;
    if (!ranges || typeof ranges !== 'object') {
      return fallback;
    }
    const entry = ranges[level] || null;
    const min = entry && Number.isFinite(entry.min) ? entry.min : fallback.min;
    const max = entry && Number.isFinite(entry.max) ? entry.max : fallback.max;
    const normalizedMin = Math.max(17, Math.min(min, max));
    const normalizedMax = Math.max(normalizedMin, max);
    return { min: normalizedMin, max: normalizedMax };
  }

  function normalizeDigitScaleOptions(raw) {
    if (!Array.isArray(raw)) {
      return DEFAULT_DIGIT_SCALE_OPTIONS;
    }
    const normalized = raw
      .map(option => {
        if (!option || typeof option !== 'object') {
          return null;
        }
        const value = Number(option.value);
        if (!Number.isFinite(value) || value <= 0) {
          return null;
        }
        const labelKey = typeof option.labelKey === 'string' ? option.labelKey.trim() : '';
        return {
          value,
          labelKey,
          fallback: typeof option.fallback === 'string' && option.fallback.trim()
            ? option.fallback
            : `x${value}`
        };
      })
      .filter(Boolean);
    return normalized.length ? normalized : DEFAULT_DIGIT_SCALE_OPTIONS;
  }

  function getConfiguredDigitScaleOptions() {
    if (!GLOBAL_CONFIG || !GLOBAL_CONFIG.arcade || !GLOBAL_CONFIG.arcade.sudoku) {
      return DEFAULT_DIGIT_SCALE_OPTIONS;
    }
    return normalizeDigitScaleOptions(GLOBAL_CONFIG.arcade.sudoku.digitScaleOptions);
  }

  function getConfiguredDigitScaleDefault(options) {
    const fallbackValue = Array.isArray(options) && options.length
      ? options[0].value
      : DEFAULT_DIGIT_SCALE;
    if (!GLOBAL_CONFIG || !GLOBAL_CONFIG.arcade || !GLOBAL_CONFIG.arcade.sudoku) {
      return fallbackValue;
    }
    const value = Number(GLOBAL_CONFIG.arcade.sudoku.digitScaleDefault);
    if (!Number.isFinite(value) || value <= 0) {
      return fallbackValue;
    }
    const allowedValues = options.map(option => option.value);
    return allowedValues.includes(value) ? value : fallbackValue;
  }

  function pickClueCount(level) {
    const range = getConfiguredClueRange(level);
    if (range.min === range.max) {
      return range.min;
    }
    const span = range.max - range.min + 1;
    return range.min + Math.floor(Math.random() * span);
  }

  function createEmptyBoard() {
    return Array.from({ length: 9 }, () => Array(9).fill(0));
  }

  function isValid(board, row, col, value) {
    for (let j = 0; j < 9; j += 1) {
      if (board[row][j] === value) {
        return false;
      }
    }
    for (let i = 0; i < 9; i += 1) {
      if (board[i][col] === value) {
        return false;
      }
    }
    const boxRow = Math.floor(row / 3) * 3;
    const boxCol = Math.floor(col / 3) * 3;
    for (let i = 0; i < 3; i += 1) {
      for (let j = 0; j < 3; j += 1) {
        if (board[boxRow + i][boxCol + j] === value) {
          return false;
        }
      }
    }
    return true;
  }

  function getCandidates(board, row, col) {
    const candidates = [];
    for (let value = 1; value <= 9; value += 1) {
      if (isValid(board, row, col, value)) {
        candidates.push(value);
      }
    }
    return candidates;
  }

  function selectNextCell(board) {
    let bestRow = -1;
    let bestCol = -1;
    let bestCandidates = null;
    for (let row = 0; row < 9; row += 1) {
      for (let col = 0; col < 9; col += 1) {
        if (board[row][col] !== 0) {
          continue;
        }
        const candidates = getCandidates(board, row, col);
        if (candidates.length === 0) {
          return { row, col, candidates };
        }
        if (!bestCandidates || candidates.length < bestCandidates.length) {
          bestRow = row;
          bestCol = col;
          bestCandidates = candidates;
          if (candidates.length === 1) {
            return { row, col, candidates };
          }
        }
      }
    }
    if (!bestCandidates) {
      return null;
    }
    return { row: bestRow, col: bestCol, candidates: bestCandidates };
  }

  function solveBoard(board) {
    const nextCell = selectNextCell(board);
    if (!nextCell) {
      return true;
    }
    const { row, col, candidates } = nextCell;
    if (!candidates.length) {
      return false;
    }
    const options = shuffled(candidates);
    for (let i = 0; i < options.length; i += 1) {
      board[row][col] = options[i];
      if (solveBoard(board)) {
        return true;
      }
      board[row][col] = 0;
    }
    return false;
  }

  function countSolutions(board, limit = 2) {
    const nextCell = selectNextCell(board);
    if (!nextCell) {
      return 1;
    }
    const { row, col, candidates } = nextCell;
    if (!candidates.length) {
      return 0;
    }
    let count = 0;
    const options = shuffled(candidates);
    for (let i = 0; i < options.length; i += 1) {
      board[row][col] = options[i];
      count += countSolutions(board, limit);
      if (count >= limit) {
        board[row][col] = 0;
        return count;
      }
    }
    board[row][col] = 0;
    return count;
  }

  function shuffled(values) {
    const copy = values.slice();
    for (let i = copy.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = copy[i];
      copy[i] = copy[j];
      copy[j] = tmp;
    }
    return copy;
  }

  function generateFullSolution() {
    const board = createEmptyBoard();
    const digits = [1, 2, 3, 4, 5, 6, 7, 8, 9];

    function fillCell(index) {
      if (index === 81) {
        return true;
      }
      const row = Math.floor(index / 9);
      const col = index % 9;
      const options = shuffled(digits);
      for (let i = 0; i < options.length; i += 1) {
        const value = options[i];
        if (isValid(board, row, col, value)) {
          board[row][col] = value;
          if (fillCell(index + 1)) {
            return true;
          }
          board[row][col] = 0;
        }
      }
      return false;
    }

    fillCell(0);
    return board;
  }

  function cloneBoard(board) {
    return board.map(row => row.slice());
  }

  function makePuzzleFromSolution(solution, targetClues) {
    const puzzle = cloneBoard(solution);
    const positions = shuffled(Array.from({ length: 81 }, (_, index) => index));
    let removals = 81 - targetClues;
    for (let i = 0; i < positions.length && removals > 0; i += 1) {
      const index = positions[i];
      const row = Math.floor(index / 9);
      const col = index % 9;
      const backup = puzzle[row][col];
      if (backup === 0) {
        continue;
      }
      puzzle[row][col] = 0;
      const solutionCount = countSolutions(puzzle, 2);
      if (solutionCount !== 1) {
        puzzle[row][col] = backup;
      } else {
        removals -= 1;
      }
    }
    return puzzle;
  }

  function generateRandomPuzzle(level) {
    const solution = generateFullSolution();
    const clues = pickClueCount(level);
    const puzzle = makePuzzleFromSolution(solution, clues);
    return {
      puzzle,
      solution
    };
  }

  function parseGridToBoard(container) {
    const inputs = container.querySelectorAll('input');
    const board = createEmptyBoard();
    inputs.forEach((input, index) => {
      const row = Math.floor(index / 9);
      const col = index % 9;
      const value = Number.parseInt(input.value, 10);
      board[row][col] = Number.isInteger(value) && value >= 1 && value <= 9 ? value : 0;
    });
    return board;
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key.trim()) {
      return fallback;
    }
    const translator = typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
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
        console.warn('Sudoku translation error for', key, error);
      }
    }
    return fallback;
  }

  function formatStatus(key, fallback, params) {
    return translate(`scripts.arcade.sudoku.status.${key}`, fallback, params);
  }

  function getI18nApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const i18n = window.i18n;
    if (!i18n || typeof i18n.updateTranslations !== 'function') {
      return null;
    }
    return i18n;
  }

  onReady(() => {
    const gridElement = document.getElementById('sudokuGrid');
    if (!gridElement) {
      return;
    }

    const statusElement = document.getElementById('sudokuStatus');
    const levelSelect = document.getElementById('sudokuLevel');
    const digitScaleSelect = document.getElementById('sudokuDigitScale');
    const generateButton = document.getElementById('sudokuGenerate');
    const checkButton = document.getElementById('sudokuCheck');
    const padValidateButton = document.getElementById('sudokuPadValidate');
    const padElement = document.getElementById('sudokuPad');
    const sudokuPage = gridElement.closest('.page--sudoku') || document.getElementById('sudoku');

    if (
      !statusElement ||
      !levelSelect ||
      !digitScaleSelect ||
      !generateButton ||
      !padValidateButton ||
      !padElement ||
      !checkButton
    ) {
      return;
    }

    const padButtons = Array.from(padElement.querySelectorAll('.sudoku-pad__button[data-value]'));
    const conflictToggleButton = document.createElement('button');
    conflictToggleButton.type = 'button';
    conflictToggleButton.className = 'sudoku-status__action sudoku-status__action--conflicts';

    let activeInput = null;
    let selectedPadValue = null;
    let highlightedCell = null;

    let currentFixedMask = createEmptyBoard().map(row => row.map(() => false));
    let solutionBoard = createEmptyBoard();
    let currentLevel = levelSelect.value || 'moyen';
    let showMistakes = currentLevel === 'facile';
    let showConflicts = currentLevel === 'facile';
    let allowMistakeHints = currentLevel === 'facile';
    let manualMistakeReveal = false;
    let lastMistakeSet = new Set();
    let lastMistakeCount = 0;
    let lastConflictSet = new Set();
    let lastConflictCount = 0;
    let lastStatusMessage = '';
    let lastStatusKind = 'info';
    let lastStatusOptionsBuilder = null;
    let lastStatusMessageBuilder = null;
    let lastStatusMeta = {};
    let lastStatusIsMistakeMessage = false;
    let puzzleStartTimestamp = null;
    let puzzleSolved = false;
    let puzzleBoard = createEmptyBoard();
    const digitScaleOptions = getConfiguredDigitScaleOptions();
    const defaultDigitScale = getConfiguredDigitScaleDefault(digitScaleOptions);
    const AUTOSAVE_ID = 'sudoku';
    const AUTOSAVE_DELAY_MS = 160;
    let autosaveTimerId = null;

    function applyDigitScale(value) {
      if (!sudokuPage) {
        return;
      }
      sudokuPage.style.setProperty('--sudoku-digit-scale', String(value));
    }

    function resolveOptionLabel(option) {
      if (!option || !option.labelKey) {
        return option?.fallback ?? '';
      }
      const translated = translate(option.labelKey, option.fallback);
      return translated === option.labelKey ? option.fallback : translated;
    }

    function populateDigitScaleOptions() {
      digitScaleSelect.replaceChildren();
      digitScaleOptions.forEach(option => {
        const optionElement = document.createElement('option');
        optionElement.value = String(option.value);
        if (option.labelKey) {
          optionElement.setAttribute('data-i18n', option.labelKey);
        }
        optionElement.textContent = resolveOptionLabel(option);
        digitScaleSelect.appendChild(optionElement);
      });
      const i18n = getI18nApi();
      if (i18n) {
        i18n.updateTranslations(digitScaleSelect);
      }
    }

    function syncDigitScaleSelection() {
      const selectedValue = Number(digitScaleSelect.value);
      const isValidSelection = digitScaleOptions.some(option => option.value === selectedValue);
      const nextValue = isValidSelection ? selectedValue : defaultDigitScale;
      digitScaleSelect.value = String(nextValue);
      applyDigitScale(nextValue);
    }

    populateDigitScaleOptions();
    syncDigitScaleSelection();
    digitScaleSelect.addEventListener('change', () => {
      syncDigitScaleSelection();
    });

    function getAutosaveApi() {
      if (typeof window === 'undefined') {
        return null;
      }
      const api = window.ArcadeAutosave;
      if (!api || typeof api.set !== 'function' || typeof api.get !== 'function') {
        return null;
      }
      return api;
    }

    function clearAutosaveEntry() {
      const api = getAutosaveApi();
      if (!api) {
        return;
      }
      try {
        api.set(AUTOSAVE_ID, null);
      } catch (error) {
        // Ignore cleanup errors
      }
    }

    function sanitizeBoardData(board) {
      if (!Array.isArray(board) || board.length !== 9) {
        return null;
      }
      const sanitized = [];
      for (let row = 0; row < 9; row += 1) {
        const sourceRow = board[row];
        if (!Array.isArray(sourceRow) || sourceRow.length !== 9) {
          return null;
        }
        const rowValues = [];
        for (let col = 0; col < 9; col += 1) {
          const value = Number(sourceRow[col]);
          if (Number.isFinite(value) && value >= 1 && value <= 9) {
            rowValues.push(Math.floor(value));
          } else {
            rowValues.push(0);
          }
        }
        sanitized.push(rowValues);
      }
      return sanitized;
    }

    function boardHasClues(board) {
      if (!Array.isArray(board)) {
        return false;
      }
      for (let row = 0; row < board.length; row += 1) {
        const rowEntries = board[row];
        if (!Array.isArray(rowEntries)) {
          continue;
        }
        for (let col = 0; col < rowEntries.length; col += 1) {
          if (Number(rowEntries[col]) > 0) {
            return true;
          }
        }
      }
      return false;
    }

    function buildAutosavePayload() {
      const puzzle = sanitizeBoardData(puzzleBoard);
      const solution = sanitizeBoardData(solutionBoard);
      if (!puzzle || !solution || !boardHasClues(puzzle)) {
        return null;
      }
      const currentBoard = sanitizeBoardData(parseGridToBoard(gridElement));
      if (!currentBoard) {
        return null;
      }
      const elapsedSeconds =
        typeof puzzleStartTimestamp === 'number' && Number.isFinite(puzzleStartTimestamp)
          ? Math.max(0, Math.floor((getNowMs() - puzzleStartTimestamp) / 1000))
          : null;
      return {
        level: currentLevel,
        puzzle,
        board: currentBoard,
        solution,
        showMistakes: showMistakes === true,
        showConflicts: showConflicts === true,
        allowMistakeHints: allowMistakeHints === true,
        manualMistakeReveal: manualMistakeReveal === true,
        puzzleSolved: puzzleSolved === true,
        elapsedSeconds,
        updatedAt: Date.now()
      };
    }

    function persistAutosave() {
      const api = getAutosaveApi();
      if (!api) {
        return;
      }
      const payload = buildAutosavePayload();
      try {
        if (payload) {
          api.set(AUTOSAVE_ID, payload);
        } else {
          api.set(AUTOSAVE_ID, null);
        }
      } catch (error) {
        // Ignore persistence issues to avoid disrupting gameplay
      }
    }

    function scheduleAutosave() {
      if (typeof window === 'undefined') {
        return;
      }
      if (autosaveTimerId != null) {
        window.clearTimeout(autosaveTimerId);
      }
      autosaveTimerId = window.setTimeout(() => {
        autosaveTimerId = null;
        persistAutosave();
      }, AUTOSAVE_DELAY_MS);
    }

    function sanitizeSavedState(saved) {
      if (!saved || typeof saved !== 'object') {
        return null;
      }
      const puzzle = sanitizeBoardData(saved.puzzle);
      const board = sanitizeBoardData(saved.board);
      const solution = sanitizeBoardData(saved.solution);
      if (!puzzle || !solution || !board || !boardHasClues(puzzle)) {
        return null;
      }
      const level = typeof saved.level === 'string' && saved.level.trim() ? saved.level : currentLevel;
      const showSavedMistakes = saved.showMistakes === true;
      const showSavedConflicts = saved.showConflicts === true;
      const allowSavedMistakes = saved.allowMistakeHints === true;
      const manualSavedReveal = saved.manualMistakeReveal === true;
      const solved = saved.puzzleSolved === true;
      const elapsed = Number(saved.elapsedSeconds);
      const elapsedSeconds = Number.isFinite(elapsed) && elapsed >= 0 ? elapsed : null;
      return {
        level,
        puzzle,
        board,
        solution,
        showMistakes: showSavedMistakes,
        showConflicts: showSavedConflicts,
        allowMistakeHints: allowSavedMistakes,
        manualMistakeReveal: manualSavedReveal,
        puzzleSolved: solved,
        elapsedSeconds
      };
    }

    function restoreAutosavedState() {
      const api = getAutosaveApi();
      if (!api) {
        return false;
      }
      let saved = null;
      try {
        saved = api.get(AUTOSAVE_ID);
      } catch (error) {
        return false;
      }
      if (!saved) {
        return false;
      }
      const normalized = sanitizeSavedState(saved);
      if (!normalized) {
        clearAutosaveEntry();
        return false;
      }
      if (normalized.level && typeof normalized.level === 'string') {
        currentLevel = normalized.level;
      }
      const availableLevels = Array.from(levelSelect.options).map(option => option.value);
      if (!availableLevels.includes(currentLevel)) {
        currentLevel = levelSelect.value || 'moyen';
      }
      levelSelect.value = currentLevel;
      showMistakes = normalized.showMistakes;
      showConflicts = normalized.showConflicts;
      allowMistakeHints = normalized.allowMistakeHints;
      manualMistakeReveal = normalized.manualMistakeReveal;
      puzzleSolved = normalized.puzzleSolved === true;
      puzzleBoard = cloneBoard(normalized.puzzle);
      solutionBoard = cloneBoard(normalized.solution);
      const fixedMask = puzzleBoard.map(row => row.map(value => value !== 0));
      loadBoardToGrid(normalized.board, fixedMask);
      if (!puzzleSolved && normalized.elapsedSeconds != null) {
        const now = getNowMs();
        const elapsedMs = Math.max(0, normalized.elapsedSeconds) * 1000;
        const startCandidate = now - elapsedMs;
        puzzleStartTimestamp = Number.isFinite(startCandidate) && startCandidate >= 0 ? startCandidate : now;
      } else if (!puzzleSolved) {
        puzzleStartTimestamp = getNowMs();
      } else {
        puzzleStartTimestamp = null;
      }
      refreshMistakeVisibility();
      refreshConflictHighlights();
      updateConflictButtonState();
      updateCheckButtonVisibility();
      if (puzzleSolved) {
        setStatus(formatStatus('solved', 'Solution trouvée ✔︎'), 'ok', null, { isSolved: true });
      } else {
        refreshStatus();
      }
      scheduleAutosave();
      return true;
    }

    function updateConflictButtonState() {
      conflictToggleButton.hidden = true;
      conflictToggleButton.textContent = '';
      conflictToggleButton.removeAttribute('aria-label');
      conflictToggleButton.setAttribute('aria-pressed', 'false');
      conflictToggleButton.disabled = true;
      conflictToggleButton.classList.add('is-disabled');
    }

    conflictToggleButton.addEventListener('click', () => {
      if (currentLevel !== 'facile') {
        return;
      }
      if (lastConflictCount === 0 && !showConflicts) {
        return;
      }
      showConflicts = !showConflicts;
      updateConflictButtonState();
      refreshConflictHighlights();
      refreshStatus();
      scheduleAutosave();
    });

    function refreshStatus() {
      if (lastStatusMeta && lastStatusMeta.isSolved) {
        return;
      }
      if (lastStatusMeta.isConflictMessage && lastConflictCount === 0) {
        if (lastMistakeCount > 0) {
          const messageBuilder = () =>
            formatStatus('mistakes', 'Cases incorrectes : {count}.', { count: lastMistakeCount });
          const severity = currentLevel === 'facile' ? 'error' : 'info';
          setStatus(messageBuilder, severity, buildMistakeStatusOptions, {
            isMistakeMessage: true,
            isConflictMessage: false
          });
        } else {
          setStatus(formatStatus('noError', "Aucune erreur pour l'instant."), 'ok');
        }
        return;
      }
      if (lastStatusIsMistakeMessage && lastMistakeCount === 0) {
        setStatus(formatStatus('noError', "Aucune erreur pour l'instant."), 'ok');
        return;
      }
      if (typeof lastStatusMessageBuilder === 'function') {
        setStatus(lastStatusMessageBuilder, lastStatusKind, lastStatusOptionsBuilder, lastStatusMeta);
        return;
      }
      if (typeof lastStatusMessage !== 'string' || !lastStatusMessage.trim()) {
        setStatus('');
        return;
      }
      setStatus(lastStatusMessage, lastStatusKind, lastStatusOptionsBuilder, lastStatusMeta);
    }

    function setStatus(message, kind = 'info', optionsBuilder = null, meta = null) {
      if (!statusElement) {
        return;
      }
      const resolvedMessage = typeof message === 'function' ? message() : message;
      lastStatusMessageBuilder = typeof message === 'function' ? message : null;
      lastStatusMessage = typeof resolvedMessage === 'string' ? resolvedMessage : '';
      lastStatusKind = kind;
      lastStatusOptionsBuilder = optionsBuilder;
      lastStatusMeta = meta && typeof meta === 'object' ? { ...meta } : {};
      lastStatusIsMistakeMessage = Boolean(lastStatusMeta.isMistakeMessage);

      if (currentLevel !== 'facile') {
        statusElement.hidden = true;
        statusElement.classList.remove('sudoku-status--ok', 'sudoku-status--error');
        statusElement.replaceChildren();
        return;
      }

      const hasMessage = typeof resolvedMessage === 'string' && resolvedMessage.trim().length > 0;
      statusElement.hidden = false;
      statusElement.classList.toggle('sudoku-status--ok', hasMessage && kind === 'ok');
      statusElement.classList.toggle('sudoku-status--error', hasMessage && kind === 'error');
      if (!hasMessage) {
        statusElement.classList.remove('sudoku-status--ok', 'sudoku-status--error');
      }
      statusElement.replaceChildren();

      if (hasMessage) {
        const messageSpan = document.createElement('span');
        messageSpan.className = 'sudoku-status__message';
        messageSpan.textContent = resolvedMessage;
        statusElement.appendChild(messageSpan);
      }

      const options = typeof optionsBuilder === 'function' ? optionsBuilder() : optionsBuilder;
      if (options && options.button) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'sudoku-status__action';
        button.textContent = options.button.label;
        if (options.button.ariaLabel) {
          button.setAttribute('aria-label', options.button.ariaLabel);
        }
        if (typeof options.button.pressed === 'boolean') {
          button.setAttribute('aria-pressed', String(options.button.pressed));
        }
        if (typeof options.button.disabled === 'boolean') {
          button.disabled = options.button.disabled;
          button.classList.toggle('is-disabled', options.button.disabled);
        }
        if (typeof options.button.onClick === 'function') {
          button.addEventListener('click', event => {
            options.button.onClick(event);
          });
        }
        statusElement.appendChild(button);
      }

      updateConflictButtonState();
      if (!conflictToggleButton.hidden) {
        statusElement.appendChild(conflictToggleButton);
      }
    }

    function updateCheckButtonVisibility() {
      const isEasyLevel = currentLevel === 'facile';
      checkButton.hidden = isEasyLevel;
      checkButton.disabled = isEasyLevel;
    }

    function clearSelectionHighlights() {
      highlightedCell = null;
      gridElement.querySelectorAll('.sudoku-cell').forEach(cell => {
        cell.classList.remove(
          'is-highlighted-related',
          'is-highlighted-line',
          'is-highlighted-block',
          'is-highlighted-same',
          'is-highlighted-origin'
        );
      });
    }

    function highlightSelection(cell) {
      if (!cell || !gridElement.contains(cell)) {
        clearSelectionHighlights();
        return;
      }

      const input = cell.querySelector('input');
      const value = input ? input.value.trim() : '';
      clearSelectionHighlights();

      if (!value) {
        return;
      }

      const row = Number(cell.dataset.r);
      const col = Number(cell.dataset.c);
      if (!Number.isInteger(row) || !Number.isInteger(col)) {
        return;
      }

      highlightedCell = cell;
      const blockRow = Math.floor(row / 3);
      const blockCol = Math.floor(col / 3);

      gridElement.querySelectorAll('.sudoku-cell').forEach(otherCell => {
        const otherRow = Number(otherCell.dataset.r);
        const otherCol = Number(otherCell.dataset.c);
        const otherInput = otherCell.querySelector('input');
        const otherValue = otherInput ? otherInput.value.trim() : '';

        if (Number.isInteger(otherRow) && Number.isInteger(otherCol)) {
          const sameRow = otherRow === row;
          const sameCol = otherCol === col;
          const sameBlock = (
            Math.floor(otherRow / 3) === blockRow
            && Math.floor(otherCol / 3) === blockCol
          );

          if (sameRow || sameCol || sameBlock) {
            otherCell.classList.add('is-highlighted-related');
          }

          if (sameRow || sameCol) {
            otherCell.classList.add('is-highlighted-line');
          }

          if (sameBlock) {
            otherCell.classList.add('is-highlighted-block');
          }
        }

        if (otherValue && otherValue === value) {
          otherCell.classList.add('is-highlighted-same');
        }
      });

      cell.classList.add('is-highlighted-origin');
    }

    function refreshSelectionHighlights() {
      if (!highlightedCell) {
        return;
      }
      highlightSelection(highlightedCell);
    }

    function highlightPadSelection() {
      if (highlightedCell) {
        refreshSelectionHighlights();
        return;
      }

      clearSelectionHighlights();

      if (!selectedPadValue) {
        return;
      }

      gridElement.querySelectorAll('.sudoku-cell').forEach(cell => {
        const input = cell.querySelector('input');
        const value = input ? input.value.trim() : '';
        if (value && value === selectedPadValue) {
          cell.classList.add('is-highlighted-same');
        }
      });
    }

    function clearHighlights() {
      gridElement.querySelectorAll('.sudoku-cell').forEach(cell => {
        cell.classList.remove('error', 'ok');
      });
      clearSelectionHighlights();
      lastConflictSet = new Set();
      lastConflictCount = 0;
      highlightPadSelection();
    }

    function refreshMistakeVisibility() {
      const cells = gridElement.querySelectorAll('.sudoku-cell');
      cells.forEach((cell, index) => {
        const isMistake = lastMistakeSet.has(index);
        cell.classList.toggle('mistake', showMistakes && isMistake);
      });
    }

    function refreshConflictHighlights() {
      const cells = gridElement.querySelectorAll('.sudoku-cell');
      cells.forEach((cell, index) => {
        const isConflict = lastConflictSet.has(index);
        cell.classList.toggle('error', showConflicts && isConflict);
      });
    }

    function getMistakePositions(board) {
      if (!Array.isArray(board) || board.length !== 9) {
        return [];
      }
      if (!Array.isArray(solutionBoard) || solutionBoard.length !== 9) {
        return [];
      }
      const mistakes = [];
      for (let row = 0; row < 9; row += 1) {
        for (let col = 0; col < 9; col += 1) {
          if (currentFixedMask[row][col]) {
            continue;
          }
          const value = board[row][col];
          if (!value) {
            continue;
          }
          const expected = solutionBoard[row] ? solutionBoard[row][col] : 0;
          if (expected && expected !== value) {
            mistakes.push({ row, col });
          }
        }
      }
      return mistakes;
    }

    function updateMistakeHighlights(board) {
      const workingBoard = board || parseGridToBoard(gridElement);
      const mistakes = getMistakePositions(workingBoard);
      lastMistakeCount = mistakes.length;
      lastMistakeSet = new Set(mistakes.map(({ row, col }) => row * 9 + col));
      refreshMistakeVisibility();
      return { workingBoard, mistakes };
    }

    function updateConflictState(board, conflicts) {
      const workingBoard = board || parseGridToBoard(gridElement);
      const entries = Array.isArray(conflicts) ? conflicts : validateBoard(workingBoard);
      const indices = new Set();
      entries.forEach(({ row, col }) => {
        if (Number.isInteger(row) && Number.isInteger(col)) {
          indices.add(row * 9 + col);
        }
      });
      lastConflictSet = indices;
      lastConflictCount = indices.size;
      refreshConflictHighlights();
      updateConflictButtonState();
      return entries;
    }

    function isBoardComplete(board) {
      if (!Array.isArray(board) || board.length !== 9) {
        return false;
      }
      for (let row = 0; row < 9; row += 1) {
        const rowEntries = board[row];
        if (!Array.isArray(rowEntries) || rowEntries.length !== 9) {
          return false;
        }
        for (let col = 0; col < 9; col += 1) {
          const value = rowEntries[col];
          if (!Number.isInteger(value) || value < 1 || value > 9) {
            return false;
          }
        }
      }
      return true;
    }

    function boardMatchesSolution(board) {
      if (!Array.isArray(board) || board.length !== 9) {
        return false;
      }
      if (!Array.isArray(solutionBoard) || solutionBoard.length !== 9) {
        return false;
      }
      for (let row = 0; row < 9; row += 1) {
        const currentRow = board[row];
        const targetRow = solutionBoard[row];
        if (!Array.isArray(currentRow) || !Array.isArray(targetRow) || currentRow.length !== 9) {
          return false;
        }
        for (let col = 0; col < 9; col += 1) {
          if (targetRow[col] !== currentRow[col]) {
            return false;
          }
        }
      }
      return true;
    }

    function triggerCompletionReward(elapsedSeconds) {
      if (!COMPLETION_REWARD_CONFIG.enabled || !COMPLETION_REWARD_CONFIG.settings) {
        return;
      }
      const elapsed = Number(elapsedSeconds);
      if (!Number.isFinite(elapsed) || elapsed < 0) {
        return;
      }
      const registrar = typeof window !== 'undefined'
        && typeof window.registerSudokuOfflineBonus === 'function'
        ? window.registerSudokuOfflineBonus
        : null;
      if (!registrar) {
        return;
      }
      try {
        registrar({
          elapsedSeconds: elapsed,
          config: COMPLETION_REWARD_CONFIG.settings,
          difficulty: currentLevel
        });
      } catch (error) {
        console.warn('Sudoku reward registration failed', error);
      }
    }

    function handlePuzzleSolved(elapsedSeconds) {
      puzzleSolved = true;
      setStatus(formatStatus('solved', 'Solution trouvée ✔︎'), 'ok', null, { isSolved: true });
      triggerCompletionReward(elapsedSeconds);
      scheduleAutosave();
    }

    function checkForCompletion(board, conflicts) {
      if (puzzleSolved) {
        return true;
      }
      const hasConflicts = Array.isArray(conflicts) ? conflicts.length > 0 : lastConflictCount > 0;
      if (hasConflicts || lastMistakeCount > 0) {
        return false;
      }
      const workingBoard = board || parseGridToBoard(gridElement);
      if (!isBoardComplete(workingBoard)) {
        return false;
      }
      if (!boardMatchesSolution(workingBoard)) {
        return false;
      }
      const startedAt = typeof puzzleStartTimestamp === 'number' && Number.isFinite(puzzleStartTimestamp)
        ? puzzleStartTimestamp
        : null;
      puzzleStartTimestamp = null;
      const now = getNowMs();
      const elapsedSeconds = startedAt != null ? Math.max(0, (now - startedAt) / 1000) : null;
      handlePuzzleSolved(elapsedSeconds);
      return true;
    }

    function buildMistakeStatusOptions() {
      if (!allowMistakeHints) {
        return null;
      }
      if (!lastMistakeCount) {
        return null;
      }
      return {
        button: {
          label: formatStatus(
            showMistakes ? 'hideMistakes' : 'showMistakes',
            showMistakes ? 'Masquer les cases incorrectes' : 'Afficher les cases incorrectes'
          ),
          pressed: showMistakes,
          onClick: () => {
            showMistakes = !showMistakes;
            refreshMistakeVisibility();
            refreshStatus();
            scheduleAutosave();
          }
        }
      };
    }

    function syncCellValueState(cell, value) {
      if (!cell) {
        return;
      }
      if (value) {
        cell.dataset.value = String(value);
      } else {
        delete cell.dataset.value;
      }
    }

    function loadBoardToGrid(board, fixedMask) {
      gridElement.innerHTML = '';
      clearSelectionHighlights();
      activeInput = null;
      currentFixedMask = fixedMask
        ? fixedMask.map(row => row.slice())
        : createEmptyBoard().map(row => row.map(() => false));

      for (let row = 0; row < 9; row += 1) {
        for (let col = 0; col < 9; col += 1) {
          const cell = document.createElement('div');
          cell.className = 'sudoku-cell';
          cell.dataset.r = String(row);
          cell.dataset.c = String(col);
          cell.setAttribute('role', 'gridcell');
          const input = document.createElement('input');
          const isFixedCell = Boolean(currentFixedMask[row][col]);
          input.inputMode = 'none';
          input.setAttribute('inputmode', 'none');
          input.maxLength = 1;
          input.pattern = '[1-9]';
          input.setAttribute('aria-label', translate('index.sections.sudoku.cellLabel', 'Case de Sudoku'));
          const value = board[row][col];
          if (value) {
            input.value = String(value);
          }
          syncCellValueState(cell, value);
          input.readOnly = true;
          input.dataset.fixed = isFixedCell ? 'true' : 'false';
          if (isFixedCell) {
            cell.classList.add('fixed');
            input.tabIndex = -1;
          } else {
            input.tabIndex = 0;
          }
          input.addEventListener('input', event => {
            const sanitized = event.target.value.replace(/[^1-9]/g, '');
            const normalizedValue = sanitized.slice(-1);
            event.target.value = normalizedValue;
            syncCellValueState(event.target.closest('.sudoku-cell'), normalizedValue);
            const { workingBoard } = updateMistakeHighlights();
            if (currentLevel !== 'facile' && manualMistakeReveal) {
              manualMistakeReveal = false;
              showMistakes = false;
              refreshMistakeVisibility();
            }
            const conflicts = updateConflictState(workingBoard);
            if (!checkForCompletion(workingBoard, conflicts)) {
              refreshStatus();
            }
            scheduleAutosave();
            if (highlightedCell && highlightedCell.contains(event.target)) {
              refreshSelectionHighlights();
            }
          });
          input.addEventListener('focus', () => {
            cell.classList.remove('error', 'ok');
          });
          cell.appendChild(input);
          gridElement.appendChild(cell);
        }
      }

      const { workingBoard } = updateMistakeHighlights(board);
      updateConflictState(workingBoard);
      highlightPadSelection();
    }

    function updatePadSelection() {
      padButtons.forEach(button => {
        button.classList.toggle('is-active', button.dataset.value === selectedPadValue);
      });
    }

    function clearPadSelection() {
      if (selectedPadValue !== null) {
        selectedPadValue = null;
        updatePadSelection();
        highlightPadSelection();
      }
    }

    function clearActiveInput() {
      if (!activeInput) {
        return;
      }
      const input = activeInput;
      activeInput = null;
      if (typeof input.blur === 'function') {
        input.blur();
      }
    }

    function applySelectionToInput(input, selection) {
      if (!input || input.dataset.fixed === 'true' || selection === null) {
        return;
      }
      const finalValue = selection === 'clear' ? '' : selection;
      input.value = finalValue;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function validateBoard(board) {
      const errors = [];
      for (let i = 0; i < 9; i += 1) {
        const seenRow = new Map();
        const seenCol = new Map();
        for (let j = 0; j < 9; j += 1) {
          const rowValue = board[i][j];
          if (rowValue) {
            if (seenRow.has(rowValue)) {
              errors.push({ row: i, col: j });
              const prevCol = seenRow.get(rowValue);
              errors.push({ row: i, col: prevCol });
            } else {
              seenRow.set(rowValue, j);
            }
          }
          const colValue = board[j][i];
          if (colValue) {
            if (seenCol.has(colValue)) {
              errors.push({ row: j, col: i });
              const prevRow = seenCol.get(colValue);
              errors.push({ row: prevRow, col: i });
            } else {
              seenCol.set(colValue, j);
            }
          }
        }
      }
      for (let baseRow = 0; baseRow < 9; baseRow += 3) {
        for (let baseCol = 0; baseCol < 9; baseCol += 3) {
          const seen = new Map();
          for (let i = 0; i < 3; i += 1) {
            for (let j = 0; j < 3; j += 1) {
              const row = baseRow + i;
              const col = baseCol + j;
              const value = board[row][col];
              if (!value) {
                continue;
              }
              if (seen.has(value)) {
                errors.push({ row, col });
                const previous = seen.get(value);
                errors.push(previous);
              } else {
                seen.set(value, { row, col });
              }
            }
          }
        }
      }
      return errors;
    }

    function onValidate() {
      clearHighlights();
      clearPadSelection();
      clearActiveInput();
      const board = parseGridToBoard(gridElement);
      const { mistakes } = updateMistakeHighlights(board);
      const conflicts = updateConflictState(board, validateBoard(board));

      if (!conflicts.length && !mistakes.length) {
        if (!checkForCompletion(board, conflicts)) {
          setStatus(formatStatus('noError', "Aucune erreur pour l'instant."), 'ok');
        }
      } else {
        const hasConflicts = conflicts.length > 0;
        const messageBuilder = () =>
          formatStatus(
            hasConflicts ? 'errors' : 'mistakes',
            hasConflicts ? 'Erreurs détectées : {count}.' : 'Cases incorrectes : {count}.',
            { count: hasConflicts ? lastConflictCount : lastMistakeCount }
          );
        const severity = currentLevel === 'facile' ? 'error' : 'info';
        const optionsBuilder = hasConflicts ? null : buildMistakeStatusOptions;
        setStatus(messageBuilder, severity, optionsBuilder, {
          isMistakeMessage: !hasConflicts,
          isConflictMessage: hasConflicts
        });
      }
    }

    function onGenerate() {
      clearHighlights();
      const level = levelSelect.value || 'moyen';
      const levelLabel = levelSelect.options[levelSelect.selectedIndex]
        ? levelSelect.options[levelSelect.selectedIndex].textContent.trim()
        : level;
      currentLevel = level;
      showMistakes = level === 'facile';
      allowMistakeHints = level === 'facile';
      showConflicts = level === 'facile';
      manualMistakeReveal = false;
      lastMistakeSet = new Set();
      lastMistakeCount = 0;
      lastConflictSet = new Set();
      lastConflictCount = 0;
      updateConflictButtonState();
      updateCheckButtonVisibility();
      setStatus(formatStatus('generating', 'Génération en cours…'));
      const { puzzle, solution } = generateRandomPuzzle(level);
      puzzleBoard = cloneBoard(puzzle);
      solutionBoard = cloneBoard(solution);
      puzzleSolved = false;
      puzzleStartTimestamp = getNowMs();
      const fixedMask = puzzle.map(row => row.map(value => value !== 0));
      loadBoardToGrid(puzzle, fixedMask);
      const clues = puzzle.flat().filter(value => value !== 0).length;
      setStatus(
        formatStatus('generated', 'Grille {level} générée (indices : {clues}).', {
          level: levelLabel,
          clues
        }),
        'ok'
      );
      refreshMistakeVisibility();
      refreshConflictHighlights();
      scheduleAutosave();
    }

    padButtons.forEach(button => {
      button.addEventListener('click', () => {
        const value = button.dataset.value ?? null;
        const isSameSelection = selectedPadValue === value;
        selectedPadValue = isSameSelection ? null : value;
        updatePadSelection();
        highlightPadSelection();
        const appliedValue = isSameSelection ? null : selectedPadValue;
        if (activeInput) {
          activeInput.focus();
          applySelectionToInput(activeInput, appliedValue);
        }
      });
    });

    gridElement.addEventListener('focusin', event => {
      if (event.target instanceof HTMLInputElement && event.target.dataset.fixed !== 'true') {
        activeInput = event.target;
      }
      const cell = event.target.closest('.sudoku-cell');
      if (cell) {
        highlightSelection(cell);
      }
    });

    gridElement.addEventListener('focusout', event => {
      if (activeInput === event.target) {
        const nextFocused = event.relatedTarget;
        const staysInInteractionZone =
          nextFocused instanceof HTMLElement &&
          (gridElement.contains(nextFocused) || padElement.contains(nextFocused));

        if (!staysInInteractionZone) {
          activeInput = null;
          clearSelectionHighlights();
          highlightPadSelection();
        }
      }
    });

    gridElement.addEventListener('click', event => {
      const cell = event.target.closest('.sudoku-cell');
      if (!cell) {
        return;
      }
      const input = cell.querySelector('input');
      highlightSelection(cell);
      if (!input || input.dataset.fixed === 'true') {
        activeInput = null;
        return;
      }
      input.focus();
      activeInput = input;
      applySelectionToInput(input, selectedPadValue);
    });

    document.addEventListener('click', event => {
      if (
        !gridElement.contains(event.target)
        && !padElement.contains(event.target)
      ) {
        clearSelectionHighlights();
        highlightPadSelection();
      }
    });

    checkButton.addEventListener('click', () => {
      if (currentLevel === 'facile') {
        return;
      }
      const board = parseGridToBoard(gridElement);
      const { mistakes } = updateMistakeHighlights(board);
      manualMistakeReveal = mistakes.length > 0;
      showMistakes = manualMistakeReveal;
      refreshMistakeVisibility();
      updateConflictState(board, validateBoard(board));
      refreshStatus();
      scheduleAutosave();
    });

    generateButton.addEventListener('click', onGenerate);
    padValidateButton.addEventListener('click', onValidate);

    const restored = restoreAutosavedState();
    if (!restored) {
      puzzleSolved = false;
      puzzleStartTimestamp = null;
      puzzleBoard = createEmptyBoard();
      solutionBoard = createEmptyBoard();
      loadBoardToGrid(createEmptyBoard());
      updateConflictButtonState();
      updateCheckButtonVisibility();
      setStatus('');
      persistAutosave();
    }
  });
})();
