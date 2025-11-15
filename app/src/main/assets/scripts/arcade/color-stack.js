(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-color-stack-root]');
  if (!root) {
    return;
  }

  const CONFIG_PATH = 'config/arcade/color-stack.json';
  const AUTOSAVE_GAME_ID = 'colorStack';
  const AUTOSAVE_VERSION = 1;
  const AUTOSAVE_DEBOUNCE_MS = 180;

  const DEFAULT_CONFIG = Object.freeze({
    maxScrambleAttempts: 120,
    preferDifferentColorWeight: 2.5,
    preferSameColorWeight: 1,
    minMovePool: 6,
    extraTopSpace: 1,
    palette: Object.freeze([
      Object.freeze({ id: 'ruby', value: '#ff6b6b' }),
      Object.freeze({ id: 'azure', value: '#4ab3ff' }),
      Object.freeze({ id: 'emerald', value: '#2ecc71' }),
      Object.freeze({ id: 'amber', value: '#f6b93b' }),
      Object.freeze({ id: 'violet', value: '#9b59b6' }),
      Object.freeze({ id: 'rose', value: '#ff9ff3' }),
      Object.freeze({ id: 'teal', value: '#1abc9c' }),
      Object.freeze({ id: 'slate', value: '#95a5a6' })
    ]),
    difficulties: Object.freeze({
      easy: Object.freeze({
        columns: 4,
        capacity: 4,
        filledColumns: Object.freeze([0, 1, 2]),
        emptyColumns: 1,
        scrambleMoves: 48,
        minMulticoloredColumns: 2,
        minDisplacedRatio: 0.5,
        gachaTickets: 1
      }),
      medium: Object.freeze({
        columns: 6,
        capacity: 5,
        filledColumns: Object.freeze([0, 1, 2, 3, 4]),
        emptyColumns: 1,
        scrambleMoves: 72,
        minMulticoloredColumns: 3,
        minDisplacedRatio: 0.6,
        gachaTickets: 2
      }),
      hard: Object.freeze({
        columns: 8,
        capacity: 6,
        filledColumns: Object.freeze([0, 1, 2, 3, 4, 5, 6]),
        emptyColumns: 1,
        scrambleMoves: 96,
        minMulticoloredColumns: 4,
        minDisplacedRatio: 0.65,
        gachaTickets: 3
      })
    })
  });

  const FALLBACK_MESSAGES = Object.freeze({
    selectSource: 'Sélectionnez une colonne contenant un jeton.',
    invalidMove: 'Déplacement impossible vers cette colonne.',
    victory: 'Bravo ! Toutes les piles sont triées.',
    resumed: 'Partie restaurée.'
  });

  const DIFFICULTY_ORDER = Object.freeze(['easy', 'medium', 'hard']);

  const state = {
    config: DEFAULT_CONFIG,
    difficulty: 'easy',
    nextTokenId: 0,
    board: [],
    initialBoard: [],
    history: [],
    solved: false,
    rewardClaimed: false,
    selectedColumn: null,
    autosaveTimer: null,
    autosaveSuppressed: false,
    elements: getElements()
  };

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined' && typeof window.translateOrDefault === 'function') {
      return window.translateOrDefault(key, fallback, params);
    }
    return fallback;
  }

  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const api = window.ArcadeAutosave;
    if (!api || typeof api !== 'object') {
      return null;
    }
    if (typeof api.get !== 'function' || typeof api.set !== 'function') {
      return null;
    }
    return api;
  }

  function clampNumber(value, min, max, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    const bounded = Math.max(min, Math.min(max, numeric));
    if (!Number.isFinite(bounded)) {
      return fallback;
    }
    return bounded;
  }

  function toInteger(value, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    return Math.max(0, Math.floor(numeric));
  }

  function isNonEmptyString(value) {
    return typeof value === 'string' && value.trim().length > 0;
  }

  function normalizePalette(palette, fallbackPalette) {
    const source = Array.isArray(palette) && palette.length ? palette : fallbackPalette;
    const normalized = [];
    source.forEach(entry => {
      const id = isNonEmptyString(entry?.id) ? entry.id.trim() : null;
      const value = isNonEmptyString(entry?.value) ? entry.value.trim() : null;
      if (!id || !value) {
        return;
      }
      normalized.push({ id, value });
    });
    return normalized.length ? normalized : fallbackPalette.slice();
  }

  function normalizeDifficulty(entry, fallback) {
    const base = fallback || DEFAULT_CONFIG.difficulties.easy;
    const columns = Math.max(1, toInteger(entry?.columns, base.columns));
    const capacity = Math.max(1, toInteger(entry?.capacity, base.capacity));
    const emptyColumns = Math.max(0, toInteger(entry?.emptyColumns, base.emptyColumns));
    const scrambleMoves = Math.max(capacity, toInteger(entry?.scrambleMoves, base.scrambleMoves));
    const minMulticolored = Math.max(0, toInteger(entry?.minMulticoloredColumns, base.minMulticoloredColumns || 1));
    const minDisplacedRatio = clampNumber(
      entry?.minDisplacedRatio,
      0,
      1,
      typeof base.minDisplacedRatio === 'number' ? base.minDisplacedRatio : 0.5
    );
    const gachaTickets = Math.max(0, toInteger(entry?.gachaTickets, base.gachaTickets || 0));
    const filledColumns = Array.isArray(entry?.filledColumns) && entry.filledColumns.length
      ? entry.filledColumns.map(index => toInteger(index, 0))
      : Array.from({ length: Math.max(0, columns - emptyColumns) }, (_, idx) => idx);
    return {
      columns,
      capacity,
      emptyColumns,
      scrambleMoves,
      minMulticoloredColumns: minMulticolored,
      minDisplacedRatio,
      filledColumns,
      gachaTickets
    };
  }

  function normalizeConfig(rawConfig, fallback = DEFAULT_CONFIG) {
    if (!rawConfig || typeof rawConfig !== 'object') {
      return fallback;
    }
    const maxScrambleAttempts = Math.max(1, toInteger(rawConfig.maxScrambleAttempts, fallback.maxScrambleAttempts));
    const preferDifferentColorWeight = clampNumber(
      rawConfig.preferDifferentColorWeight,
      0.1,
      10,
      fallback.preferDifferentColorWeight
    );
    const preferSameColorWeight = clampNumber(
      rawConfig.preferSameColorWeight,
      0.1,
      10,
      fallback.preferSameColorWeight
    );
    const minMovePool = Math.max(1, toInteger(rawConfig.minMovePool, fallback.minMovePool));
    const extraTopSpace = Math.max(0, toInteger(rawConfig.extraTopSpace, fallback.extraTopSpace || 0));
    const palette = normalizePalette(rawConfig.palette, fallback.palette);
    const difficulties = {};
    DIFFICULTY_ORDER.forEach(key => {
      difficulties[key] = normalizeDifficulty(rawConfig.difficulties?.[key], fallback.difficulties[key]);
    });
    return {
      maxScrambleAttempts,
      preferDifferentColorWeight,
      preferSameColorWeight,
      minMovePool,
      extraTopSpace,
      palette,
      difficulties
    };
  }

  function getEffectiveCapacity(difficultyConfig) {
    const baseCapacity = Math.max(1, toInteger(difficultyConfig?.capacity, 1));
    const extraSpace = Math.max(0, toInteger(state.config?.extraTopSpace, DEFAULT_CONFIG.extraTopSpace));
    return baseCapacity + extraSpace;
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
        console.warn('Color Stack config load error', error);
      });
  }

  function getElements() {
    return {
      root,
      board: root.querySelector('#colorStackBoard'),
      difficulty: root.querySelector('#colorStackDifficultySelect'),
      newButton: root.querySelector('#colorStackNewButton'),
      restartButton: root.querySelector('#colorStackRestartButton'),
      undoButton: root.querySelector('#colorStackUndoButton'),
      movesValue: root.querySelector('#colorStackMovesValue'),
      completedValue: root.querySelector('#colorStackCompletedValue'),
      message: root.querySelector('#colorStackMessage')
    };
  }

  function createToken(color, originColumn) {
    state.nextTokenId += 1;
    return {
      id: `t${state.nextTokenId}`,
      colorId: color.id,
      colorHex: color.value,
      origin: originColumn
    };
  }

  function cloneToken(token) {
    if (!token) {
      return null;
    }
    return {
      id: token.id,
      colorId: token.colorId,
      colorHex: token.colorHex,
      origin: token.origin
    };
  }

  function cloneBoard(board) {
    return board.map(column => column.map(cloneToken));
  }

  function serializeBoard(board) {
    return board.map(column => column.map(cloneToken));
  }

  function deserializeBoard(serialized) {
    if (!Array.isArray(serialized)) {
      return [];
    }
    return serialized.map(column => {
      if (!Array.isArray(column)) {
        return [];
      }
      return column
        .map(cloneToken)
        .filter(token => token && isNonEmptyString(token.colorId) && isNonEmptyString(token.colorHex));
    });
  }

  function isUniformColumn(column) {
    if (!Array.isArray(column) || column.length === 0) {
      return true;
    }
    const firstColor = column[0].colorId;
    return column.every(token => token.colorId === firstColor);
  }

  function isBoardSolved(board, capacity) {
    return board.every(column => {
      if (!column.length) {
        return true;
      }
      if (!isUniformColumn(column)) {
        return false;
      }
      return column.length === capacity;
    });
  }

  function countEmptyColumns(board) {
    return board.reduce((count, column) => count + (Array.isArray(column) && column.length === 0 ? 1 : 0), 0);
  }

  function countMulticoloredColumns(board) {
    return board.reduce((count, column) => count + (column.length > 1 && !isUniformColumn(column) ? 1 : 0), 0);
  }

  function computeDisplacedRatio(board) {
    let displaced = 0;
    let total = 0;
    board.forEach((column, columnIndex) => {
      column.forEach(token => {
        total += 1;
        if (token.origin !== columnIndex) {
          displaced += 1;
        }
      });
    });
    if (total === 0) {
      return 0;
    }
    return displaced / total;
  }

  function scoreScrambleCandidate(board, difficultyConfig) {
    const ratio = computeDisplacedRatio(board);
    const multicolored = countMulticoloredColumns(board);
    const solvedPenalty = isBoardSolved(board, difficultyConfig.capacity) ? -10 : 0;
    return ratio * 2 + multicolored + solvedPenalty;
  }

  function generateSolvedBoard(difficultyConfig, palette) {
    state.nextTokenId = 0;
    const board = [];
    const filledColumns = Array.isArray(difficultyConfig.filledColumns)
      ? difficultyConfig.filledColumns
      : [];
    const paletteLength = palette.length;
    const requireUniqueColors = paletteLength >= filledColumns.length && paletteLength > 0;
    const usedPaletteIndices = new Set();
    filledColumns.forEach((colorIndex, columnIndex) => {
      let paletteIndex = paletteLength > 0
        ? (Number.isFinite(colorIndex) ? Math.abs(colorIndex) % paletteLength : columnIndex % paletteLength)
        : 0;
      if (requireUniqueColors) {
        let attempts = 0;
        while (usedPaletteIndices.has(paletteIndex) && attempts < paletteLength) {
          paletteIndex = (paletteIndex + 1) % paletteLength;
          attempts += 1;
        }
      }
      if (paletteLength > 0) {
        usedPaletteIndices.add(paletteIndex);
      }
      const color = palette[paletteIndex] || palette[0];
      const column = [];
      for (let slot = 0; slot < difficultyConfig.capacity; slot += 1) {
        column.push(createToken(color, columnIndex));
      }
      board.push(column);
    });
    const requiredColumns = Math.max(
      difficultyConfig.columns,
      board.length + Math.max(0, difficultyConfig.emptyColumns)
    );
    while (board.length < requiredColumns) {
      board.push([]);
    }
    return board;
  }

  function hasMatchingTopPair(column) {
    if (!Array.isArray(column) || column.length < 2) {
      return false;
    }
    const top = column[column.length - 1];
    const second = column[column.length - 2];
    if (!top || !second) {
      return false;
    }
    return top.colorId === second.colorId;
  }

  function wouldCreateLongStack(column, token) {
    if (!token || !token.colorId) {
      return false;
    }
    if (!Array.isArray(column) || column.length === 0) {
      return false;
    }
    const tokenColor = token.colorId;
    const top = column[column.length - 1];
    if (!top || top.colorId !== tokenColor) {
      return false;
    }
    let runLength = 1; // Include the incoming token.
    for (let index = column.length - 1; index >= 0; index -= 1) {
      const entry = column[index];
      if (!entry || entry.colorId !== tokenColor) {
        break;
      }
      runLength += 1;
      if (runLength > 2) {
        return true;
      }
    }
    return false;
  }

  function chooseSourceColumn(board) {
    const candidates = [];
    board.forEach((column, index) => {
      if (hasMatchingTopPair(column)) {
        candidates.push(index);
      }
    });
    if (!candidates.length) {
      return null;
    }
    const randomIndex = Math.floor(Math.random() * candidates.length);
    return candidates[randomIndex];
  }

  function chooseDestinationColumn(board, sourceIndex, token, capacity) {
    if (!token) {
      return null;
    }
    const candidates = [];
    board.forEach((column, index) => {
      if (index === sourceIndex || !Array.isArray(column) || column.length >= capacity) {
        return;
      }
      if (wouldCreateLongStack(column, token)) {
        return;
      }
      candidates.push(index);
    });
    if (!candidates.length) {
      return null;
    }
    const randomIndex = Math.floor(Math.random() * candidates.length);
    return candidates[randomIndex];
  }

  function meetsScrambleDiversity(board, difficultyConfig) {
    if (isBoardSolved(board, difficultyConfig.capacity)) {
      return false;
    }
    if (countMulticoloredColumns(board) < difficultyConfig.minMulticoloredColumns) {
      return false;
    }
    const baseRatio = typeof difficultyConfig.minDisplacedRatio === 'number'
      ? difficultyConfig.minDisplacedRatio
      : 0.5;
    const softCap = 0.3;
    if (computeDisplacedRatio(board) < Math.min(baseRatio, softCap)) {
      return false;
    }
    return true;
  }

  function scrambleBoard(solvedBoard, difficultyConfig) {
    const capacity = getEffectiveCapacity(difficultyConfig);
    const board = cloneBoard(solvedBoard);
    const scrambleTarget = Math.max(1, toInteger(difficultyConfig.scrambleMoves, 1));
    const minMovePool = Math.max(1, state.config.minMovePool);
    const requiredMoves = Math.max(Math.floor(scrambleTarget * 0.4), 3, minMovePool);
    const maxAttempts = scrambleTarget * Math.max(10, board.length * 5);
    let performedMoves = 0;
    let attempts = 0;
    let bestBoard = null;
    let bestScore = -Infinity;

    while (performedMoves < scrambleTarget && attempts < maxAttempts) {
      attempts += 1;
      const sourceIndex = chooseSourceColumn(board);
      if (sourceIndex === null) {
        continue;
      }
      const sourceColumn = board[sourceIndex];
      if (!sourceColumn || sourceColumn.length === 0) {
        continue;
      }
      const movingToken = sourceColumn[sourceColumn.length - 1];
      if (!movingToken) {
        continue;
      }
      const destinationIndex = chooseDestinationColumn(board, sourceIndex, movingToken, capacity);
      if (destinationIndex === null) {
        continue;
      }
      const destinationColumn = board[destinationIndex];
      if (!destinationColumn || destinationColumn.length >= capacity) {
        continue;
      }
      sourceColumn.pop();
      destinationColumn.push(movingToken);
      performedMoves += 1;
      const candidateScore = scoreScrambleCandidate(board, difficultyConfig);
      if (candidateScore > bestScore) {
        bestScore = candidateScore;
        bestBoard = cloneBoard(board);
      }
      sourceColumn.pop();
      destinationColumn.push(movingToken);
      performedMoves += 1;
    }

    const finalBoardValid =
      performedMoves >= requiredMoves && meetsScrambleDiversity(board, difficultyConfig);
    if (finalBoardValid) {
      return board;
    }
    if (bestBoard && meetsScrambleDiversity(bestBoard, difficultyConfig)) {
      return bestBoard;
    }
    return null;
  }

  function generatePuzzle(difficultyKey) {
    const config = state.config.difficulties[difficultyKey] || state.config.difficulties.easy;
    const palette = state.config.palette && state.config.palette.length
      ? state.config.palette
      : DEFAULT_CONFIG.palette;
    const solvedBoard = generateSolvedBoard(config, palette);
    let scrambled = null;
    const maxAttempts = Math.max(1, state.config.maxScrambleAttempts);
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const attemptBoard = scrambleBoard(solvedBoard, config);
      if (attemptBoard) {
        scrambled = attemptBoard;
        break;
      }
    }
    if (!scrambled) {
      scrambled = cloneBoard(solvedBoard);
    }
    return {
      board: scrambled,
      initial: cloneBoard(scrambled),
      capacity: config.capacity,
      goalColumns: solvedBoard.length
    };
  }

  function formatTicketCount(value) {
    if (typeof formatIntegerLocalized === 'function') {
      try {
        return formatIntegerLocalized(value);
      } catch (error) {
        // Ignore formatting errors and fallback below.
      }
    }
    if (Number.isFinite(value)) {
      try {
        return Number(value).toLocaleString();
      } catch (error) {
        // Ignore locale formatting errors.
      }
    }
    return String(value);
  }

  function getGachaTicketsForDifficulty(difficultyKey) {
    const difficultyConfig = state.config?.difficulties?.[difficultyKey]
      || state.config?.difficulties?.easy
      || DEFAULT_CONFIG.difficulties.easy;
    const amount = Number(difficultyConfig?.gachaTickets);
    if (!Number.isFinite(amount) || amount <= 0) {
      return 0;
    }
    return Math.max(0, Math.floor(amount));
  }

  function awardVictoryTickets() {
    if (state.rewardClaimed) {
      return;
    }
    const tickets = getGachaTicketsForDifficulty(state.difficulty);
    if (tickets <= 0) {
      state.rewardClaimed = true;
      scheduleAutosave();
      return;
    }
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      state.rewardClaimed = true;
      scheduleAutosave();
      return;
    }
    let gained = 0;
    try {
      gained = awardGacha(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Color Stack: unable to grant gacha tickets', error);
      gained = 0;
    }
    state.rewardClaimed = true;
    scheduleAutosave();
    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = gained > 1 ? 's' : '';
      const formattedCount = formatTicketCount(gained);
      const message = translate(
        'scripts.arcade.colorStack.rewards.gachaVictory',
        'Color Stack victory! +{count} gacha ticket{suffix}.',
        { count: formattedCount, suffix }
      );
      showToast(message);
    }
  }

  function setMessage(key, fallback, isSuccess = false) {
    const messageElement = state.elements.message;
    if (!messageElement) {
      return;
    }
    const text = key ? translate(key, fallback) : '';
    messageElement.textContent = text;
    messageElement.classList.toggle('color-stack__message--success', Boolean(isSuccess));
  }

  function updateStatus() {
    const movesValue = state.elements.movesValue;
    if (movesValue) {
      movesValue.textContent = String(state.history.length);
    }
    const completedValue = state.elements.completedValue;
    if (completedValue) {
      const capacity = state.config.difficulties[state.difficulty]?.capacity || 1;
      const solvedColumns = state.board.reduce((count, column) => {
        if (!column.length) {
          return count + 1;
        }
        return count + (column.length === capacity && isUniformColumn(column) ? 1 : 0);
      }, 0);
      completedValue.textContent = `${solvedColumns}/${state.board.length}`;
    }
    if (state.elements.undoButton) {
      state.elements.undoButton.disabled = state.history.length === 0;
    }
    if (state.elements.restartButton) {
      const hasInitial = state.initialBoard.some(column => column.length);
      state.elements.restartButton.disabled = !hasInitial;
    }
  }

  function buildColumnLabel(index, column) {
    const count = column.length;
    return translate(
      'index.sections.colorStack.columnLabel',
      `Colonne ${index + 1} · ${count} jeton(s)`,
      { index: index + 1, count }
    );
  }

  function canMoveToken(fromIndex, toIndex) {
    if (!Number.isInteger(fromIndex) || !Number.isInteger(toIndex)) {
      return false;
    }
    if (fromIndex === toIndex) {
      return false;
    }
    const source = state.board[fromIndex];
    const dest = state.board[toIndex];
    if (!source || !dest || source.length === 0) {
      return false;
    }
    const difficultyConfig = state.config.difficulties[state.difficulty] || state.config.difficulties.easy;
    const capacity = getEffectiveCapacity(difficultyConfig);
    if (dest.length >= capacity) {
      return false;
    }
    const movingToken = source[source.length - 1];
    if (!movingToken) {
      return false;
    }
    const destTop = dest[dest.length - 1];
    if (!destTop) {
      return true;
    }
    return destTop.colorId === movingToken.colorId;
  }

  function getValidTargets(fromIndex) {
    const targets = [];
    state.board.forEach((_, index) => {
      if (canMoveToken(fromIndex, index)) {
        targets.push(index);
      }
    });
    return targets;
  }

  function renderBoard() {
    const container = state.elements.board;
    if (!container) {
      return;
    }
    container.innerHTML = '';
    const selection = state.selectedColumn;
    const validTargets = Number.isInteger(selection) ? getValidTargets(selection) : [];
    const targetSet = new Set(validTargets);
    state.board.forEach((column, columnIndex) => {
      const columnButton = document.createElement('button');
      columnButton.type = 'button';
      columnButton.className = 'color-stack__column';
      if (selection === columnIndex) {
        columnButton.classList.add('color-stack__column--selected');
      }
      if (targetSet.has(columnIndex)) {
        columnButton.classList.add('color-stack__column--targetable');
      }
      columnButton.dataset.columnIndex = String(columnIndex);
      columnButton.setAttribute('aria-label', buildColumnLabel(columnIndex, column));
      column.forEach(token => {
        const tokenElement = document.createElement('span');
        tokenElement.className = 'color-stack__token';
        tokenElement.style.setProperty('--token-color', token.colorHex);
        tokenElement.setAttribute('aria-hidden', 'true');
        columnButton.appendChild(tokenElement);
      });
      columnButton.addEventListener('click', () => {
        handleColumnClick(columnIndex);
      });
      container.appendChild(columnButton);
    });
  }

  function scheduleAutosave() {
    if (state.autosaveSuppressed) {
      return;
    }
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

  function persistAutosaveNow() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    const payload = {
      version: AUTOSAVE_VERSION,
      difficulty: state.difficulty,
      nextTokenId: state.nextTokenId,
      board: serializeBoard(state.board),
      initialBoard: serializeBoard(state.initialBoard),
      history: state.history.slice(),
      solved: Boolean(state.solved),
      rewardClaimed: Boolean(state.rewardClaimed),
      selectedColumn: state.selectedColumn
    };
    try {
      autosave.set(AUTOSAVE_GAME_ID, payload);
    } catch (error) {
      // Ignore autosave persistence errors.
    }
  }

  function flushAutosave() {
    if (typeof window !== 'undefined' && state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
      state.autosaveTimer = null;
    }
    persistAutosaveNow();
  }

  function withAutosaveSuppressed(callback) {
    state.autosaveSuppressed = true;
    try {
      callback();
    } finally {
      state.autosaveSuppressed = false;
    }
  }

  function applyAutosave(payload) {
    const difficulty = typeof payload.difficulty === 'string' && DIFFICULTY_ORDER.includes(payload.difficulty)
      ? payload.difficulty
      : 'easy';
    const board = deserializeBoard(payload.board);
    const initialBoard = deserializeBoard(payload.initialBoard);
    if (!board.length) {
      return false;
    }
    state.difficulty = difficulty;
    state.nextTokenId = Math.max(0, toInteger(payload.nextTokenId, 0));
    state.board = board;
    state.initialBoard = initialBoard.length ? initialBoard : cloneBoard(board);
    state.history = Array.isArray(payload.history)
      ? payload.history
          .map(move => (Number.isInteger(move?.from) && Number.isInteger(move?.to)
            ? { from: move.from, to: move.to }
            : null))
          .filter(Boolean)
      : [];
    state.solved = Boolean(payload.solved);
    state.rewardClaimed = Boolean(payload.rewardClaimed);
    state.selectedColumn = Number.isInteger(payload.selectedColumn) ? payload.selectedColumn : null;
    if (state.solved && !state.rewardClaimed) {
      awardVictoryTickets();
    }
    renderBoard();
    updateStatus();
    setMessage('index.sections.colorStack.messages.restored', FALLBACK_MESSAGES.resumed, false);
    if (state.elements.difficulty) {
      state.elements.difficulty.value = state.difficulty;
    }
    return true;
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
    return applyAutosave(payload);
  }

  function resetSelection() {
    state.selectedColumn = null;
  }

  function restartLevel() {
    withAutosaveSuppressed(() => {
      state.board = cloneBoard(state.initialBoard);
      state.history = [];
      state.solved = false;
      state.rewardClaimed = false;
      resetSelection();
      renderBoard();
      updateStatus();
      setMessage('index.sections.colorStack.messages.restart', 'Plateau réinitialisé.', false);
    });
    scheduleAutosave();
  }

  function startNewGame(difficultyKey) {
    const normalizedDifficulty = DIFFICULTY_ORDER.includes(difficultyKey) ? difficultyKey : state.difficulty;
    state.difficulty = normalizedDifficulty;
    const puzzle = generatePuzzle(normalizedDifficulty);
    withAutosaveSuppressed(() => {
      state.board = cloneBoard(puzzle.board);
      state.initialBoard = cloneBoard(puzzle.initial);
      state.history = [];
      state.solved = false;
      state.rewardClaimed = false;
      resetSelection();
      if (state.elements.difficulty) {
        state.elements.difficulty.value = normalizedDifficulty;
      }
      renderBoard();
      updateStatus();
      setMessage('index.sections.colorStack.messages.new', 'Nouveau niveau généré.', false);
    });
    scheduleAutosave();
  }

  function handleColumnClick(columnIndex) {
    if (!Number.isInteger(columnIndex) || columnIndex < 0 || columnIndex >= state.board.length) {
      return;
    }
    if (state.selectedColumn == null) {
      if (state.board[columnIndex] && state.board[columnIndex].length) {
        state.selectedColumn = columnIndex;
        renderBoard();
      } else {
        setMessage('index.sections.colorStack.messages.selectSource', FALLBACK_MESSAGES.selectSource, false);
      }
      return;
    }
    if (state.selectedColumn === columnIndex) {
      resetSelection();
      renderBoard();
      return;
    }
    if (!canMoveToken(state.selectedColumn, columnIndex)) {
      setMessage('index.sections.colorStack.messages.invalidMove', FALLBACK_MESSAGES.invalidMove, false);
      return;
    }
    const source = state.board[state.selectedColumn];
    const dest = state.board[columnIndex];
    const token = source.pop();
    if (!token) {
      return;
    }
    dest.push(token);
    state.history.push({ from: state.selectedColumn, to: columnIndex });
    resetSelection();
    renderBoard();
    updateStatus();
    scheduleAutosave();
    if (isBoardSolved(state.board, state.config.difficulties[state.difficulty].capacity)) {
      state.solved = true;
      awardVictoryTickets();
      setMessage('index.sections.colorStack.messages.victory', FALLBACK_MESSAGES.victory, true);
    } else {
      setMessage('index.sections.colorStack.messages.move', '', false);
    }
  }

  function undoMove() {
    if (!state.history.length) {
      return;
    }
    const lastMove = state.history.pop();
    if (!lastMove || !Number.isInteger(lastMove.from) || !Number.isInteger(lastMove.to)) {
      return;
    }
    const dest = state.board[lastMove.to];
    const source = state.board[lastMove.from];
    if (!dest || !source || dest.length === 0) {
      return;
    }
    const token = dest.pop();
    if (!token) {
      return;
    }
    source.push(token);
    state.solved = isBoardSolved(state.board, state.config.difficulties[state.difficulty].capacity);
    resetSelection();
    renderBoard();
    updateStatus();
    setMessage('index.sections.colorStack.messages.undo', 'Coup annulé.', false);
    scheduleAutosave();
  }

  function bindEvents() {
    const { difficulty, newButton, restartButton, undoButton } = state.elements;
    if (difficulty) {
      difficulty.addEventListener('change', event => {
        const value = event.target?.value;
        startNewGame(value);
      });
    }
    if (newButton) {
      newButton.addEventListener('click', () => {
        startNewGame(state.difficulty);
      });
    }
    if (restartButton) {
      restartButton.addEventListener('click', () => {
        restartLevel();
      });
    }
    if (undoButton) {
      undoButton.addEventListener('click', () => {
        undoMove();
      });
    }
  }

  bindEvents();
  loadRemoteConfig();
  if (!restoreFromAutosave()) {
    startNewGame(state.difficulty);
  }

  window.colorStackArcade = {
    onEnter() {
      renderBoard();
    },
    onLeave() {
      flushAutosave();
    }
  };
})();
