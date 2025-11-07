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
    maxScrambleAttempts: 200,
    preferDifferentColorWeight: 2.5,
    preferSameColorWeight: 1,
    minMovePool: 6,
    preparedPoolSize: 1,
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
        colors: 4,
        columns: 6,
        capacity: 5,
        emptyColumns: 2,
        scrambleMoves: Object.freeze({ min: 10, max: 30 }),
        minMulticoloredColumns: 2,
        minDisplacedRatio: 0.45
      }),
      medium: Object.freeze({
        colors: 5,
        columns: 7,
        capacity: 5,
        emptyColumns: 2,
        scrambleMoves: Object.freeze({ min: 40, max: 60 }),
        minMulticoloredColumns: 3,
        minDisplacedRatio: 0.55
      }),
      hard: Object.freeze({
        colors: 6,
        columns: 7,
        capacity: 6,
        emptyColumns: 1,
        scrambleMoves: Object.freeze({ min: 70, max: 100 }),
        minMulticoloredColumns: 4,
        minDisplacedRatio: 0.6
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
    selectedColumn: null,
    autosaveTimer: null,
    autosaveSuppressed: false,
    preparedPuzzles: { easy: [], medium: [], hard: [] },
    preparationTimers: { easy: null, medium: null, hard: null },
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
    const colors = Math.max(1, toInteger(entry?.colors, base.colors));
    const capacity = Math.max(1, toInteger(entry?.capacity, base.capacity));
    const emptyColumns = Math.max(1, toInteger(entry?.emptyColumns, base.emptyColumns));
    const baseRange = base.scrambleMoves || { min: capacity, max: capacity };
    const scrambleMin = Math.max(1, toInteger(entry?.scrambleMoves?.min, baseRange.min));
    const scrambleMax = Math.max(scrambleMin, toInteger(entry?.scrambleMoves?.max, baseRange.max));
    const minMulticolored = Math.max(1, toInteger(entry?.minMulticoloredColumns, base.minMulticoloredColumns || 1));
    const minDisplacedRatio = clampNumber(
      entry?.minDisplacedRatio,
      0,
      1,
      typeof base.minDisplacedRatio === 'number' ? base.minDisplacedRatio : 0.5
    );
    const minimumColumns = colors + Math.max(0, emptyColumns);
    const columns = Math.max(minimumColumns, toInteger(entry?.columns, base.columns || minimumColumns));
    return {
      colors,
      columns,
      capacity,
      emptyColumns,
      scrambleMoves: { min: scrambleMin, max: scrambleMax },
      minMulticoloredColumns: minMulticolored,
      minDisplacedRatio
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
    const preparedPoolSize = Math.max(1, toInteger(rawConfig.preparedPoolSize, fallback.preparedPoolSize || 1));
    const palette = normalizePalette(rawConfig.palette, fallback.palette);
    const difficulties = {};
    DIFFICULTY_ORDER.forEach(key => {
      const normalized = normalizeDifficulty(rawConfig.difficulties?.[key], fallback.difficulties[key]);
      difficulties[key] = Object.freeze({
        colors: normalized.colors,
        columns: normalized.columns,
        capacity: normalized.capacity,
        emptyColumns: normalized.emptyColumns,
        scrambleMoves: Object.freeze({ min: normalized.scrambleMoves.min, max: normalized.scrambleMoves.max }),
        minMulticoloredColumns: normalized.minMulticoloredColumns,
        minDisplacedRatio: normalized.minDisplacedRatio
      });
    });
    return {
      maxScrambleAttempts,
      preferDifferentColorWeight,
      preferSameColorWeight,
      minMovePool,
      preparedPoolSize,
      palette,
      difficulties
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
          resetPreparedPuzzles();
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

  function createToken(color, originColumn, counter) {
    if (counter && typeof counter === 'object') {
      const nextValue = Number.isFinite(counter.value) ? counter.value + 1 : 1;
      counter.value = nextValue;
      return {
        id: `t${counter.value}`,
        colorId: color.id,
        colorHex: color.value,
        origin: originColumn
      };
    }
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

  function chooseWeighted(candidates) {
    if (!Array.isArray(candidates) || candidates.length === 0) {
      return null;
    }
    const totalWeight = candidates.reduce((sum, candidate) => sum + candidate.weight, 0);
    if (totalWeight <= 0) {
      return candidates[Math.floor(Math.random() * candidates.length)];
    }
    const threshold = Math.random() * totalWeight;
    let cumulative = 0;
    for (let index = 0; index < candidates.length; index += 1) {
      cumulative += candidates[index].weight;
      if (threshold <= cumulative) {
        return candidates[index];
      }
    }
    return candidates[candidates.length - 1];
  }

  function generateSolvedBoard(difficultyConfig, palette) {
    const counter = { value: 0 };
    const board = [];
    const colorCount = Math.max(1, Math.min(palette.length, toInteger(difficultyConfig.colors, palette.length)));
    for (let colorIndex = 0; colorIndex < colorCount; colorIndex += 1) {
      const paletteIndex = colorIndex % palette.length;
      const color = palette[paletteIndex] || palette[0];
      const columnIndex = board.length;
      const column = [];
      for (let slot = 0; slot < difficultyConfig.capacity; slot += 1) {
        column.push(createToken(color, columnIndex, counter));
      }
      board.push(column);
    }
    const requiredColumns = Math.max(
      Math.max(colorCount + Math.max(0, difficultyConfig.emptyColumns || 0), board.length),
      Math.max(1, toInteger(difficultyConfig.columns, board.length))
    );
    while (board.length < requiredColumns) {
      board.push([]);
    }
    return { board, nextTokenId: counter.value };
  }

  function collectScrambleCandidates(board, difficultyConfig, lastMove, emptyRequirement) {
    const candidates = [];
    const { capacity } = difficultyConfig;
    const currentEmpty = countEmptyColumns(board);
    board.forEach((sourceColumn, sourceIndex) => {
      if (!Array.isArray(sourceColumn) || sourceColumn.length === 0) {
        return;
      }
      const movingToken = sourceColumn[sourceColumn.length - 1];
      if (!movingToken) {
        return;
      }
      const belowToken = sourceColumn[sourceColumn.length - 2];
      if (belowToken && belowToken.colorId !== movingToken.colorId) {
        return;
      }
      board.forEach((destColumn, destIndex) => {
        if (destIndex === sourceIndex) {
          return;
        }
        if (!Array.isArray(destColumn) || destColumn.length >= capacity) {
          return;
        }
        if (lastMove && lastMove.from === destIndex && lastMove.to === sourceIndex) {
          return;
        }
        const destTop = destColumn[destColumn.length - 1] || null;
        const sameColor = destTop && destTop.colorId === movingToken.colorId;
        const destWasEmpty = destColumn.length === 0;
        const sourceWillBeEmpty = sourceColumn.length === 1;
        const projectedEmpty = currentEmpty + (sourceWillBeEmpty ? 1 : 0) - (destWasEmpty ? 1 : 0);
        if (projectedEmpty < emptyRequirement) {
          return;
        }
        const weight = sameColor
          ? Math.max(0.1, state.config.preferSameColorWeight)
          : Math.max(0.1, state.config.preferDifferentColorWeight);
        candidates.push({ from: sourceIndex, to: destIndex, weight });
      });
    });
    return candidates;
  }

  function performScrambleMove(board, move) {
    const source = board[move.from];
    const dest = board[move.to];
    if (!source || !dest || source.length === 0) {
      return false;
    }
    const token = source.pop();
    if (!token) {
      return false;
    }
    dest.push(token);
    return true;
  }

  function meetsScrambleDiversity(board, difficultyConfig) {
    if (isBoardSolved(board, difficultyConfig.capacity)) {
      return false;
    }
    if (countMulticoloredColumns(board) < difficultyConfig.minMulticoloredColumns) {
      return false;
    }
    if (computeDisplacedRatio(board) < difficultyConfig.minDisplacedRatio) {
      return false;
    }
    return true;
  }

  function scrambleBoard(solvedBoard, difficultyConfig) {
    const attemptBoard = cloneBoard(solvedBoard);
    const range = difficultyConfig.scrambleMoves || { min: difficultyConfig.capacity, max: difficultyConfig.capacity };
    const minMovePool = Math.max(1, state.config.minMovePool);
    const minMoves = Math.max(minMovePool, toInteger(range.min, minMovePool));
    const maxMoves = Math.max(minMoves, toInteger(range.max, minMoves));
    const desiredMoves = minMoves + Math.floor(Math.random() * (maxMoves - minMoves + 1));
    const emptyRequirement = Math.max(1, toInteger(difficultyConfig.emptyColumns, 1));
    const moves = [];
    let satisfied = false;
    let iterations = 0;
    const iterationLimit = Math.max(200, maxMoves * attemptBoard.length * Math.max(1, difficultyConfig.capacity));
    while (moves.length < maxMoves && iterations < iterationLimit) {
      iterations += 1;
      const lastMove = moves.length ? moves[moves.length - 1] : null;
      const candidates = collectScrambleCandidates(attemptBoard, difficultyConfig, lastMove, emptyRequirement);
      if (!candidates.length) {
        break;
      }
      const move = chooseWeighted(candidates);
      if (!move) {
        break;
      }
      if (!performScrambleMove(attemptBoard, move)) {
        break;
      }
      moves.push({ from: move.from, to: move.to });
      if (
        moves.length >= minMoves &&
        moves.length >= desiredMoves &&
        meetsScrambleDiversity(attemptBoard, difficultyConfig)
      ) {
        satisfied = true;
        break;
      }
    }
    if (!satisfied) {
      if (moves.length < minMoves) {
        return null;
      }
      if (!meetsScrambleDiversity(attemptBoard, difficultyConfig)) {
        return null;
      }
    }
    if (moves.length < minMoves || moves.length > maxMoves) {
      return null;
    }
    if (countEmptyColumns(attemptBoard) < emptyRequirement) {
      return null;
    }
    return { board: attemptBoard, moves };
  }

  function canMoveTokenOnBoard(board, capacity, fromIndex, toIndex) {
    if (!Number.isInteger(fromIndex) || !Number.isInteger(toIndex) || fromIndex === toIndex) {
      return false;
    }
    const source = board[fromIndex];
    const dest = board[toIndex];
    if (!Array.isArray(source) || !Array.isArray(dest) || source.length === 0) {
      return false;
    }
    if (dest.length >= capacity) {
      return false;
    }
    const movingToken = source[source.length - 1];
    if (!movingToken) {
      return false;
    }
    const destTop = dest[dest.length - 1];
    return !destTop || destTop.colorId === movingToken.colorId;
  }

  function simulateReverseSolution(scrambledBoard, difficultyConfig, scrambleMoves) {
    const capacity = difficultyConfig.capacity;
    const moves = Array.isArray(scrambleMoves) ? scrambleMoves : [];
    if (!moves.length) {
      return isBoardSolved(scrambledBoard, capacity);
    }
    const workBoard = cloneBoard(scrambledBoard);
    for (let index = moves.length - 1; index >= 0; index -= 1) {
      const move = moves[index];
      if (!move || !Number.isInteger(move.from) || !Number.isInteger(move.to)) {
        return false;
      }
      const fromIndex = move.to;
      const toIndex = move.from;
      if (!canMoveTokenOnBoard(workBoard, capacity, fromIndex, toIndex)) {
        return false;
      }
      const token = workBoard[fromIndex].pop();
      if (!token) {
        return false;
      }
      workBoard[toIndex].push(token);
    }
    return isBoardSolved(workBoard, capacity);
  }

  function createPuzzle(difficultyKey) {
    const config = state.config.difficulties[difficultyKey] || state.config.difficulties.easy;
    const palette = state.config.palette && state.config.palette.length
      ? state.config.palette
      : DEFAULT_CONFIG.palette;
    const attempts = Math.max(1, state.config.maxScrambleAttempts);
    for (let attempt = 0; attempt < attempts; attempt += 1) {
      const solved = generateSolvedBoard(config, palette);
      const scramble = scrambleBoard(solved.board, config);
      if (!scramble) {
        continue;
      }
      if (!simulateReverseSolution(scramble.board, config, scramble.moves)) {
        continue;
      }
      return {
        board: scramble.board,
        initial: cloneBoard(scramble.board),
        capacity: config.capacity,
        nextTokenId: solved.nextTokenId,
        scrambleMoves: scramble.moves.length
      };
    }
    const fallbackSolved = generateSolvedBoard(config, palette);
    const fallbackBoard = cloneBoard(fallbackSolved.board);
    return {
      board: fallbackBoard,
      initial: cloneBoard(fallbackBoard),
      capacity: config.capacity,
      nextTokenId: fallbackSolved.nextTokenId,
      scrambleMoves: 0
    };
  }

  function getPreparedPoolTarget() {
    return Math.max(1, state.config.preparedPoolSize || 1);
  }

  function clearPreparationTimer(difficulty) {
    if (typeof window !== 'undefined' && state.preparationTimers[difficulty] != null) {
      window.clearTimeout(state.preparationTimers[difficulty]);
    }
    state.preparationTimers[difficulty] = null;
  }

  function schedulePuzzlePreparation(difficulty) {
    if (!DIFFICULTY_ORDER.includes(difficulty)) {
      return;
    }
    if (!Array.isArray(state.preparedPuzzles[difficulty])) {
      state.preparedPuzzles[difficulty] = [];
    }
    const target = getPreparedPoolTarget();
    if (state.preparedPuzzles[difficulty].length >= target) {
      return;
    }
    if (state.preparationTimers[difficulty] != null && typeof window !== 'undefined') {
      return;
    }
    if (typeof window === 'undefined') {
      const puzzle = createPuzzle(difficulty);
      if (puzzle) {
        state.preparedPuzzles[difficulty].push(puzzle);
      }
      return;
    }
    state.preparationTimers[difficulty] = window.setTimeout(() => {
      state.preparationTimers[difficulty] = null;
      const puzzle = createPuzzle(difficulty);
      if (puzzle) {
        state.preparedPuzzles[difficulty].push(puzzle);
      }
      if (state.preparedPuzzles[difficulty].length < getPreparedPoolTarget()) {
        schedulePuzzlePreparation(difficulty);
      }
    }, 0);
  }

  function resetPreparedPuzzles() {
    DIFFICULTY_ORDER.forEach(difficulty => {
      clearPreparationTimer(difficulty);
      state.preparedPuzzles[difficulty] = [];
      schedulePuzzlePreparation(difficulty);
    });
  }

  function takePreparedPuzzle(difficultyKey) {
    const normalized = DIFFICULTY_ORDER.includes(difficultyKey) ? difficultyKey : 'easy';
    const pool = state.preparedPuzzles[normalized] || [];
    const puzzle = pool.length ? pool.shift() : createPuzzle(normalized);
    schedulePuzzlePreparation(normalized);
    return puzzle || createPuzzle(normalized);
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
    const capacity = state.config.difficulties[state.difficulty]?.capacity || 1;
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
    state.selectedColumn = Number.isInteger(payload.selectedColumn) ? payload.selectedColumn : null;
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
    const puzzle = takePreparedPuzzle(normalizedDifficulty);
    withAutosaveSuppressed(() => {
      state.nextTokenId = Math.max(0, toInteger(puzzle?.nextTokenId, state.nextTokenId));
      state.board = cloneBoard(puzzle.board);
      state.initialBoard = cloneBoard(puzzle.initial);
      state.history = [];
      state.solved = false;
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
  resetPreparedPuzzles();
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
