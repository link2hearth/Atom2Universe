(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/link.json';
  const GAME_ID = 'link';
  const MAX_BOARD_SIZE = 24;
  const MAX_CELL_COUNT = MAX_BOARD_SIZE * MAX_BOARD_SIZE;
  const DEFAULT_CONFIG = Object.freeze({
    defaultDifficulty: 'medium',
    difficulties: Object.freeze({
      easy: Object.freeze({
        size: Object.freeze({ min: 4, max: 6 }),
        plusCells: Object.freeze({ min: 0, max: 2 }),
        twinPairs: Object.freeze({ min: 0, max: 2 }),
        scrambleMoves: Object.freeze({ min: 10, max: 15 })
      }),
      medium: Object.freeze({
        size: Object.freeze({ min: 7, max: 9 }),
        plusCells: Object.freeze({ min: 2, max: 5 }),
        twinPairs: Object.freeze({ min: 1, max: 3 }),
        scrambleMoves: Object.freeze({ min: 15, max: 25 })
      }),
      hard: Object.freeze({
        size: Object.freeze({ min: 10, max: 12 }),
        plusCells: Object.freeze({ min: 5, max: 10 }),
        twinPairs: Object.freeze({ min: 3, max: 5 }),
        scrambleMoves: Object.freeze({ min: 30, max: 50 })
      })
    })
  });

  const PAIR_COLORS = Object.freeze([
    '#8f3fff',
    '#34c759',
    '#111111',
    '#ff2d20',
    '#ff9500',
    '#ffcc00',
    '#ff6f61',
    '#c51162',
    '#6d28d9',
    '#2ba84a',
    '#fa4eab',
    '#ff4f00'
  ]);

  const MAX_HISTORY_ENTRIES = 200;
  const MAX_CREATION_VISITS = 7;
  const GENERATION_MODES = Object.freeze(['base', 'plus', 'random']);
  const DEFAULT_GENERATION_MODE = 'plus';
  const LINK_LENGTH_OPTIONS = Object.freeze([2, 3, 4]);
  const DEFAULT_LINK_LENGTH = 3;
  const SELECTION_FINALIZE_DELAY = 500;
  const BASE_L_SHAPE = Object.freeze([
    { row: 0, col: 0 },
    { row: 1, col: 0 },
    { row: 2, col: 0 },
    { row: 2, col: 1 }
  ]);
  const BASE_ZIGZAG_SHAPE = Object.freeze([
    { row: 0, col: 0 },
    { row: 0, col: 1 },
    { row: 1, col: 1 },
    { row: 1, col: 2 }
  ]);
  const L_SHAPE_VARIANT_LIST = createShapeVariantList(BASE_L_SHAPE);
  const ZIGZAG_SHAPE_VARIANT_LIST = createShapeVariantList(BASE_ZIGZAG_SHAPE);
  const L_SHAPE_VARIANTS = createShapeVariantSet(BASE_L_SHAPE);
  const ZIGZAG_SHAPE_VARIANTS = createShapeVariantSet(BASE_ZIGZAG_SHAPE);

  const numberFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;

  const state = {
    config: DEFAULT_CONFIG,
    difficulty: DEFAULT_CONFIG.defaultDifficulty,
    generationMode: DEFAULT_GENERATION_MODE,
    linkLength: DEFAULT_LINK_LENGTH,
    size: 0,
    board: [],
    cellElements: [],
    pairs: new Map(),
    initialBoard: null,
    moves: 0,
    history: [],
    elements: null,
    interaction: {
      path: [],
      pointerId: null,
      mode: null,
      finalizeTimeoutId: null,
      isActive: false,
      captureElement: null
    },
    initialized: false,
    isVictory: false,
    messageData: {
      key: 'index.sections.link.messages.intro',
      fallback:
        `Reliez exactement ${DEFAULT_LINK_LENGTH} cases adjacentes orthogonalement pour modifier leurs valeurs et celles de leurs jumelles. Mettez toutes les cases normales à 0 et les cases +1 à 10.`,
      params: { count: formatNumber(DEFAULT_LINK_LENGTH) }
    },
    languageHandlerAttached: false,
    languageHandler: null
  };

  function clampNumber(value, min, max, fallback) {
    const numeric = typeof value === 'number' ? value : Number.parseFloat(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    return Math.min(Math.max(numeric, min), max);
  }

  function clampInteger(value, min, max, fallback) {
    const numeric = typeof value === 'number' ? value : Number.parseInt(value, 10);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    const clamped = Math.min(Math.max(numeric, min), max);
    return Math.round(clamped);
  }

  function clampRange(rawRange, fallbackRange, min, max) {
    const source = rawRange && typeof rawRange === 'object' ? rawRange : null;
    const fallback = fallbackRange || { min, max };
    const resolvedMin = clampInteger(source ? source.min : undefined, min, max, fallback.min);
    const resolvedMax = clampInteger(source ? source.max : undefined, resolvedMin, max, Math.max(resolvedMin, fallback.max));
    return { min: resolvedMin, max: resolvedMax };
  }

  function normalizeShapeKey(points) {
    if (!Array.isArray(points) || points.length === 0) {
      return '';
    }
    let minRow = Infinity;
    let minCol = Infinity;
    points.forEach(point => {
      if (!point) {
        return;
      }
      if (point.row < minRow) {
        minRow = point.row;
      }
      if (point.col < minCol) {
        minCol = point.col;
      }
    });
    return points
      .map(point => ({ row: point.row - minRow, col: point.col - minCol }))
      .sort((a, b) => (a.row - b.row) || (a.col - b.col))
      .map(point => `${point.row},${point.col}`)
      .join('|');
  }

  function normalizeShapePoints(points) {
    if (!Array.isArray(points)) {
      return [];
    }
    let minRow = Infinity;
    let minCol = Infinity;
    points.forEach(point => {
      if (!point) {
        return;
      }
      if (point.row < minRow) {
        minRow = point.row;
      }
      if (point.col < minCol) {
        minCol = point.col;
      }
    });
    return points.map(point => ({ row: point.row - minRow, col: point.col - minCol }));
  }

  function createShapeVariantList(basePoints) {
    if (!Array.isArray(basePoints)) {
      return [];
    }
    const transforms = [
      point => ({ row: point.row, col: point.col }),
      point => ({ row: point.col, col: -point.row }),
      point => ({ row: -point.row, col: -point.col }),
      point => ({ row: -point.col, col: point.row })
    ];
    const variants = [];
    const seenKeys = new Set();
    transforms.forEach(transform => {
      const rotated = basePoints.map(transform);
      const rotatedKey = normalizeShapeKey(rotated);
      if (!seenKeys.has(rotatedKey)) {
        seenKeys.add(rotatedKey);
        variants.push(normalizeShapePoints(rotated));
      }
      const mirrored = rotated.map(point => ({ row: point.row, col: -point.col }));
      const mirroredKey = normalizeShapeKey(mirrored);
      if (!seenKeys.has(mirroredKey)) {
        seenKeys.add(mirroredKey);
        variants.push(normalizeShapePoints(mirrored));
      }
    });
    return variants;
  }

  function createShapeVariantSet(basePoints) {
    const variants = createShapeVariantList(basePoints);
    return new Set(variants.map(variant => normalizeShapeKey(variant)));
  }

  function resolveDifficultyConfig(rawDifficulty, fallbackDifficulty) {
    const fallback = fallbackDifficulty || DEFAULT_CONFIG.difficulties[DEFAULT_CONFIG.defaultDifficulty];
    return {
      size: clampRange(rawDifficulty?.size, fallback.size, 3, MAX_BOARD_SIZE),
      plusCells: clampRange(rawDifficulty?.plusCells, fallback.plusCells, 0, MAX_CELL_COUNT),
      twinPairs: clampRange(
        rawDifficulty?.twinPairs,
        fallback.twinPairs,
        0,
        Math.floor(MAX_CELL_COUNT / 2)
      ),
      scrambleMoves: clampRange(rawDifficulty?.scrambleMoves, fallback.scrambleMoves, 0, 999)
    };
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return fallback;
    }
    let translator = null;
    if (window.i18n && typeof window.i18n.t === 'function') {
      translator = window.i18n.t.bind(window.i18n);
    } else if (typeof window.t === 'function') {
      translator = window.t.bind(window);
    }
    if (!translator) {
      return fallback;
    }
    try {
      const result = translator(key, params);
      if (typeof result === 'string') {
        const trimmed = result.trim();
        if (trimmed && trimmed !== key) {
          return trimmed;
        }
      }
    } catch (error) {
      console.warn('Link translation error for key', key, error);
    }
    return fallback;
  }

  function formatNumber(value) {
    if (!Number.isFinite(value)) {
      return '0';
    }
    return numberFormatter ? numberFormatter.format(value) : String(Math.round(value));
  }

  function cloneBoard(board) {
    if (!Array.isArray(board)) {
      return null;
    }
    return board.map(row =>
      row.map(cell => ({
        type: cell.type,
        value: cell.value,
        pairId: cell.pairId,
        pairColor: cell.pairColor || null
      }))
    );
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

  function resolveConfig(rawConfig) {
    if (!rawConfig || typeof rawConfig !== 'object') {
      return DEFAULT_CONFIG;
    }
    const fallback = DEFAULT_CONFIG;
    const rawDifficulties = rawConfig.difficulties && typeof rawConfig.difficulties === 'object'
      ? rawConfig.difficulties
      : {};
    const resolvedDifficulties = {};
    Object.keys(fallback.difficulties).forEach(key => {
      resolvedDifficulties[key] = resolveDifficultyConfig(rawDifficulties[key], fallback.difficulties[key]);
    });
    const defaultDifficulty = typeof rawConfig.defaultDifficulty === 'string'
      && Object.prototype.hasOwnProperty.call(resolvedDifficulties, rawConfig.defaultDifficulty)
      ? rawConfig.defaultDifficulty
      : fallback.defaultDifficulty;
    return {
      defaultDifficulty,
      difficulties: resolvedDifficulties
    };
  }

  function getDifficultyConfig(key) {
    const available = state.config && state.config.difficulties ? state.config.difficulties : null;
    if (available && Object.prototype.hasOwnProperty.call(available, key)) {
      return available[key];
    }
    const fallbackKey = state.config?.defaultDifficulty || DEFAULT_CONFIG.defaultDifficulty;
    return (available && available[fallbackKey]) || DEFAULT_CONFIG.difficulties[fallbackKey];
  }

  function getDifficultyLabel(key) {
    const fallbackLabels = { easy: 'Easy', medium: 'Medium', hard: 'Hard' };
    const fallback = fallbackLabels[key] || key;
    return translate(`index.sections.link.difficulty.${key}`, fallback);
  }

  function syncDifficultySelect() {
    if (!state.elements || !state.elements.difficultySelect) {
      return;
    }
    const select = state.elements.difficultySelect;
    if (typeof select.value !== 'string' || select.value !== state.difficulty) {
      select.value = state.difficulty;
    }
  }

  function syncGenerationSelect() {
    if (!state.elements || !state.elements.generationSelect) {
      return;
    }
    const select = state.elements.generationSelect;
    if (typeof select.value !== 'string' || select.value !== state.generationMode) {
      select.value = state.generationMode;
    }
  }

  function getRequiredLinkLength() {
    return LINK_LENGTH_OPTIONS.includes(state.linkLength)
      ? state.linkLength
      : DEFAULT_LINK_LENGTH;
  }

  function syncLinkLengthSelect() {
    if (!state.elements || !state.elements.linkLengthSelect) {
      return;
    }
    const select = state.elements.linkLengthSelect;
    const value = String(getRequiredLinkLength());
    if (select.value !== value) {
      select.value = value;
    }
  }

  function loadRemoteConfig() {
    if (typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          state.config = resolveConfig(data);
          if (!state.config.difficulties || !state.config.difficulties[state.difficulty]) {
            state.difficulty = state.config.defaultDifficulty;
            syncDifficultySelect();
            if (state.initialized) {
              generateLevel();
            }
          } else {
            syncDifficultySelect();
          }
        }
      })
      .catch(error => {
        console.warn('Link config load error', error);
      });
  }

  function getElements() {
    const section = document.getElementById('link');
    if (!section) {
      return null;
    }
    return {
      section,
      root: section.querySelector('.link'),
      board: document.getElementById('linkBoard'),
      movesValue: document.getElementById('linkMovesValue'),
      message: document.getElementById('linkMessage'),
      difficultySelect: document.getElementById('linkDifficultySelect'),
      generationSelect: document.getElementById('linkGenerationSelect'),
      linkLengthSelect: document.getElementById('linkLengthSelect'),
      undo: document.getElementById('linkUndoButton'),
      restart: document.getElementById('linkRestartButton'),
      newLevel: document.getElementById('linkNewLevelButton')
    };
  }

  function setMessage(key, fallback, params) {
    if (!state.elements || !state.elements.message) {
      return;
    }
    state.messageData = { key, fallback, params: params || null };
    const text = translate(key, fallback, params);
    state.elements.message.textContent = text;
  }

  function refreshMessage() {
    if (!state.messageData) {
      return;
    }
    const { key, fallback, params } = state.messageData;
    setMessage(key, fallback, params);
  }

  function attachLanguageListener() {
    if (state.languageHandlerAttached) {
      return;
    }
    const handler = () => {
      updateAllCells();
      refreshMessage();
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandlerAttached = true;
    state.languageHandler = handler;
  }

  function clearFinalizeTimer() {
    if (state.interaction.finalizeTimeoutId != null) {
      clearTimeout(state.interaction.finalizeTimeoutId);
      state.interaction.finalizeTimeoutId = null;
    }
  }

  function resetInteraction() {
    clearFinalizeTimer();
    if (state.interaction.captureElement) {
      const element = state.interaction.captureElement;
      const pointerId = state.interaction.pointerId;
      if (
        element instanceof Element
        && typeof element.releasePointerCapture === 'function'
        && Number.isFinite(pointerId)
        && element.hasPointerCapture?.(pointerId)
      ) {
        element.releasePointerCapture(pointerId);
      }
    }
    const path = state.interaction.path;
    if (Array.isArray(path)) {
      path.forEach(cell => {
        const element = getCellElement(cell.row, cell.col);
        if (element) {
          element.dataset.state = '';
        }
      });
    }
    state.interaction.path = [];
    state.interaction.pointerId = null;
    state.interaction.mode = null;
    state.interaction.isActive = false;
    state.interaction.captureElement = null;
  }

  function getCellElement(row, col) {
    if (!Array.isArray(state.cellElements)) {
      return null;
    }
    const rowElements = state.cellElements[row];
    if (!Array.isArray(rowElements)) {
      return null;
    }
    return rowElements[col] || null;
  }

  function getCellData(row, col) {
    if (!Array.isArray(state.board)) {
      return null;
    }
    const rowData = state.board[row];
    if (!Array.isArray(rowData)) {
      return null;
    }
    return rowData[col] || null;
  }

  function formatCellLabel(row, col, cell) {
    const valueText = formatNumber(cell.value);
    const target = cell.type === 'plus' ? 10 : 0;
    const params = {
      row: row + 1,
      column: col + 1,
      value: valueText,
      target,
      type: cell.type === 'plus' ? 'plus' : 'normal'
    };
    const fallback = `Ligne ${params.row}, colonne ${params.column} : ${valueText} (objectif ${target})`;
    return translate('index.sections.link.cell.label', fallback, params);
  }

  function computeCellBackground(cell) {
    const ratio = clampNumber(cell.value / 10, 0, 1, 0);
    const startLight = 82;
    const endLight = 34;
    const light = startLight - (startLight - endLight) * ratio;
    return `hsl(210, 68%, ${Math.round(light)}%)`;
  }

  function updateCell(row, col) {
    const cell = getCellData(row, col);
    const element = getCellElement(row, col);
    if (!cell || !element) {
      return;
    }
    const formattedValue = formatNumber(cell.value);
    element.dataset.value = formattedValue;
    element.dataset.type = cell.type;
    const valueElement = element.querySelector('.link__cell-value');
    if (valueElement) {
      valueElement.textContent = formattedValue;
    }
    if (cell.pairId != null) {
      element.dataset.hasPair = 'true';
      element.style.setProperty('--link-pair-color', cell.pairColor || 'transparent');
    } else {
      element.dataset.hasPair = 'false';
      element.style.setProperty('--link-pair-color', 'transparent');
    }
    const background = computeCellBackground(cell);
    element.style.setProperty('--link-cell-background', background);
    element.dataset.state = '';
    element.setAttribute('aria-label', formatCellLabel(row, col, cell));
  }

  function updateAllCells() {
    if (!Array.isArray(state.board)) {
      return;
    }
    for (let row = 0; row < state.board.length; row += 1) {
      const rowData = state.board[row];
      if (!Array.isArray(rowData)) {
        continue;
      }
      for (let col = 0; col < rowData.length; col += 1) {
        updateCell(row, col);
      }
    }
  }

  function updateMovesDisplay() {
    if (!state.elements || !state.elements.movesValue) {
      return;
    }
    state.elements.movesValue.textContent = formatNumber(state.moves);
  }

  function updateButtonsState() {
    if (!state.elements) {
      return;
    }
    const disableUndo = state.history.length === 0;
    state.elements.undo?.toggleAttribute('disabled', disableUndo);
    state.elements.restart?.toggleAttribute('disabled', false);
    state.elements.newLevel?.toggleAttribute('disabled', false);
  }

  function setVictoryState(isVictory) {
    state.isVictory = Boolean(isVictory);
    if (state.elements && state.elements.root) {
      state.elements.root.classList.toggle('link--victory', state.isVictory);
    }
  }

  function recordHistoryEntry(changes) {
    if (!Array.isArray(state.history)) {
      state.history = [];
    }
    state.history.push({ changes });
    if (state.history.length > MAX_HISTORY_ENTRIES) {
      state.history.shift();
    }
  }

  function areCoordsConnected(coords) {
    if (!Array.isArray(coords) || coords.length === 0) {
      return false;
    }
    const visited = new Set();
    const queue = [coords[0]];
    visited.add(`${coords[0].row}:${coords[0].col}`);
    while (queue.length > 0) {
      const current = queue.shift();
      coords.forEach(coord => {
        const key = `${coord.row}:${coord.col}`;
        if (visited.has(key)) {
          return;
        }
        const distance = Math.abs(coord.row - current.row) + Math.abs(coord.col - current.col);
        if (distance === 1) {
          visited.add(key);
          queue.push(coord);
        }
      });
    }
    return visited.size === coords.length;
  }

  function isLineShape(coords) {
    if (!Array.isArray(coords) || coords.length < 2) {
      return false;
    }
    const rows = coords.map(coord => coord.row);
    const cols = coords.map(coord => coord.col);
    const uniqueRows = new Set(rows).size;
    const uniqueCols = new Set(cols).size;
    if (uniqueRows === 1) {
      const sortedCols = cols.slice().sort((a, b) => a - b);
      for (let i = 1; i < sortedCols.length; i += 1) {
        if (sortedCols[i] !== sortedCols[i - 1] + 1) {
          return false;
        }
      }
      return true;
    }
    if (uniqueCols === 1) {
      const sortedRows = rows.slice().sort((a, b) => a - b);
      for (let i = 1; i < sortedRows.length; i += 1) {
        if (sortedRows[i] !== sortedRows[i - 1] + 1) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  function isCornerShape(coords) {
    if (!Array.isArray(coords) || coords.length !== 3) {
      return false;
    }
    const rowValues = Array.from(new Set(coords.map(coord => coord.row)));
    const colValues = Array.from(new Set(coords.map(coord => coord.col)));
    if (rowValues.length !== 2 || colValues.length !== 2) {
      return false;
    }
    const present = new Set(coords.map(coord => `${coord.row}:${coord.col}`));
    let missing = 0;
    for (let i = 0; i < rowValues.length; i += 1) {
      for (let j = 0; j < colValues.length; j += 1) {
        if (!present.has(`${rowValues[i]}:${colValues[j]}`)) {
          missing += 1;
        }
      }
    }
    return missing === 1;
  }

  function isSquareShape(coords) {
    if (!Array.isArray(coords) || coords.length !== 4) {
      return false;
    }
    const rowValues = Array.from(new Set(coords.map(coord => coord.row)));
    const colValues = Array.from(new Set(coords.map(coord => coord.col)));
    if (rowValues.length !== 2 || colValues.length !== 2) {
      return false;
    }
    const present = new Set(coords.map(coord => `${coord.row}:${coord.col}`));
    for (let i = 0; i < rowValues.length; i += 1) {
      for (let j = 0; j < colValues.length; j += 1) {
        if (!present.has(`${rowValues[i]}:${colValues[j]}`)) {
          return false;
        }
      }
    }
    return true;
  }

  function isPatternValid(path) {
    const requiredLength = getRequiredLinkLength();
    if (!Array.isArray(path) || path.length !== requiredLength) {
      return false;
    }
    const uniqueKeys = new Set();
    const coords = [];
    for (let index = 0; index < path.length; index += 1) {
      const cell = path[index];
      if (!cell) {
        return false;
      }
      const row = Number.isFinite(cell.row) ? cell.row : Number.parseInt(cell.row, 10);
      const col = Number.isFinite(cell.col) ? cell.col : Number.parseInt(cell.col, 10);
      if (!Number.isFinite(row) || !Number.isFinite(col)) {
        return false;
      }
      const normalizedRow = Math.round(row);
      const normalizedCol = Math.round(col);
      const key = `${normalizedRow}:${normalizedCol}`;
      if (uniqueKeys.has(key)) {
        return false;
      }
      uniqueKeys.add(key);
      coords.push({ row: normalizedRow, col: normalizedCol });
    }
    if (!areCoordsConnected(coords)) {
      return false;
    }
    if (requiredLength === 2) {
      return isLineShape(coords);
    }
    if (requiredLength === 3) {
      return isLineShape(coords) || isCornerShape(coords);
    }
    if (requiredLength === 4) {
      if (isLineShape(coords) || isSquareShape(coords)) {
        return true;
      }
      const shapeKey = normalizeShapeKey(coords);
      if (ZIGZAG_SHAPE_VARIANTS.has(shapeKey)) {
        return true;
      }
      if (L_SHAPE_VARIANTS.has(shapeKey)) {
        return true;
      }
      return false;
    }
    return false;
  }

  function applyCellEffect(cell) {
    if (!cell) {
      return;
    }
    if (cell.type === 'plus') {
      if (cell.value < 10) {
        cell.value += 1;
      } else {
        cell.value = 9;
      }
      return;
    }
    if (cell.value > 0) {
      cell.value -= 1;
    } else {
      cell.value = 1;
    }
  }

  function applyCreationCellEffect(cell) {
    if (!cell) {
      return;
    }
    if (cell.type === 'plus') {
      if (cell.value > 0) {
        cell.value -= 1;
      } else {
        cell.value = 1;
      }
      return;
    }
    if (cell.value < 10) {
      cell.value += 1;
    } else {
      cell.value = 9;
    }
  }

  function getPairPartner(row, col, pairId) {
    if (!state.pairs || !state.pairs.has(pairId)) {
      return null;
    }
    const members = state.pairs.get(pairId);
    if (!Array.isArray(members)) {
      return null;
    }
    for (let i = 0; i < members.length; i += 1) {
      const member = members[i];
      if (member.row === row && member.col === col) {
        continue;
      }
      return member;
    }
    return null;
  }

  function keyForCell(row, col) {
    return `${row}:${col}`;
  }

  function collectAffectedCells(path) {
    if (!Array.isArray(path) || path.length === 0) {
      return [];
    }
    const affected = new Map();
    const selectedKeys = new Set(path.map(cell => keyForCell(cell.row, cell.col)));
    const triggeredPairs = new Set();
    path.forEach(cell => {
      const cellData = getCellData(cell.row, cell.col);
      if (!cellData) {
        return;
      }
      const key = keyForCell(cell.row, cell.col);
      if (!affected.has(key)) {
        affected.set(key, { row: cell.row, col: cell.col });
      }
      if (cellData.pairId == null) {
        return;
      }
      const partner = getPairPartner(cell.row, cell.col, cellData.pairId);
      if (!partner) {
        return;
      }
      const partnerKey = keyForCell(partner.row, partner.col);
      if (selectedKeys.has(partnerKey) || triggeredPairs.has(cellData.pairId)) {
        return;
      }
      triggeredPairs.add(cellData.pairId);
      if (!affected.has(partnerKey)) {
        affected.set(partnerKey, { row: partner.row, col: partner.col });
      }
    });
    return Array.from(affected.values());
  }

  function applyPattern(path, options = {}) {
    const requiredLength = getRequiredLinkLength();
    if (!Array.isArray(path) || path.length !== requiredLength) {
      return false;
    }
    const record = options.recordHistory !== false;
    const skipRender = options.skipRender === true;
    const effectFn = typeof options.effect === 'function' ? options.effect : applyCellEffect;
    const affected = new Map();
    const selectedKeys = new Set(path.map(cell => keyForCell(cell.row, cell.col)));
    const triggeredPairs = new Set();
    path.forEach(cell => {
      const cellData = getCellData(cell.row, cell.col);
      if (!cellData) {
        return;
      }
      const key = keyForCell(cell.row, cell.col);
      if (!affected.has(key)) {
        affected.set(key, {
          row: cell.row,
          col: cell.col,
          previous: cellData.value,
          next: null
        });
      }
      effectFn(cellData);
      affected.get(key).next = cellData.value;
    });
    path.forEach(cell => {
      const cellData = getCellData(cell.row, cell.col);
      if (!cellData || cellData.pairId == null) {
        return;
      }
      const partner = getPairPartner(cell.row, cell.col, cellData.pairId);
      if (!partner) {
        return;
      }
      const partnerKey = keyForCell(partner.row, partner.col);
      if (selectedKeys.has(partnerKey)) {
        return;
      }
      if (triggeredPairs.has(cellData.pairId)) {
        return;
      }
      triggeredPairs.add(cellData.pairId);
      const partnerData = getCellData(partner.row, partner.col);
      if (!partnerData) {
        return;
      }
      if (!affected.has(partnerKey)) {
        affected.set(partnerKey, {
          row: partner.row,
          col: partner.col,
          previous: partnerData.value,
          next: null
        });
      }
      effectFn(partnerData);
      affected.get(partnerKey).next = partnerData.value;
    });
    if (!skipRender) {
      affected.forEach(change => {
        updateCell(change.row, change.col);
      });
    }
    if (record) {
      recordHistoryEntry(Array.from(affected.values()));
    }
    return affected.size > 0;
  }

  function countRemainingCells() {
    if (!Array.isArray(state.board)) {
      return { normal: 0, plus: 0 };
    }
    let normal = 0;
    let plus = 0;
    for (let row = 0; row < state.board.length; row += 1) {
      const rowData = state.board[row];
      if (!Array.isArray(rowData)) {
        continue;
      }
      for (let col = 0; col < rowData.length; col += 1) {
        const cell = rowData[col];
        if (!cell) {
          continue;
        }
        if (cell.type === 'plus') {
          if (cell.value !== 10) {
            plus += 1;
          }
        } else if (cell.value !== 0) {
          normal += 1;
        }
      }
    }
    return { normal, plus };
  }

  function checkVictory() {
    const remaining = countRemainingCells();
    return remaining.normal === 0 && remaining.plus === 0;
  }

  function handleMove(path) {
    const applied = applyPattern(path);
    if (!applied) {
      return;
    }
    state.moves += 1;
    updateMovesDisplay();
    updateButtonsState();
    const victory = checkVictory();
    if (victory) {
      setVictoryState(true);
      setMessage(
        'index.sections.link.messages.victory',
        `Bravo ! Puzzle résolu en ${formatNumber(state.moves)} coups.`,
        { moves: formatNumber(state.moves) }
      );
      return;
    }
    setVictoryState(false);
    const remaining = countRemainingCells();
    setMessage(
      'index.sections.link.messages.progress',
      `Encore ${formatNumber(remaining.normal)} cases normales et ${formatNumber(remaining.plus)} cases +1 à ajuster.`,
      {
        normal: formatNumber(remaining.normal),
        plus: formatNumber(remaining.plus)
      }
    );
  }

  function flashInvalidSelection(path) {
    if (!Array.isArray(path)) {
      return;
    }
    path.forEach(cell => {
      const element = getCellElement(cell.row, cell.col);
      if (element) {
        element.dataset.state = 'invalid';
        setTimeout(() => {
          if (element.dataset.state === 'invalid') {
            element.dataset.state = '';
          }
        }, 260);
      }
    });
  }

  function scheduleFinalizeSelection() {
    const requiredLength = getRequiredLinkLength();
    if (!Array.isArray(state.interaction.path) || state.interaction.path.length !== requiredLength) {
      return;
    }
    clearFinalizeTimer();
    state.interaction.finalizeTimeoutId = setTimeout(() => {
      state.interaction.finalizeTimeoutId = null;
      finalizeSelection();
    }, SELECTION_FINALIZE_DELAY);
  }

  function finalizeSelection() {
    const currentPath = state.interaction.path.slice();
    resetInteraction();
    const requiredLength = getRequiredLinkLength();
    if (currentPath.length !== requiredLength) {
      return;
    }
    if (!isPatternValid(currentPath)) {
      flashInvalidSelection(currentPath);
      setMessage(
        'index.sections.link.messages.invalidPattern',
        'Motif invalide pour ce nombre de cases.',
        { count: formatNumber(requiredLength) }
      );
      return;
    }
    handleMove(currentPath);
  }

  function startSelection(row, col, mode, pointerId) {
    const cell = getCellData(row, col);
    const element = getCellElement(row, col);
    if (!cell || !element) {
      return;
    }
    resetInteraction();
    state.interaction.mode = mode;
    state.interaction.pointerId = pointerId || null;
    state.interaction.path = [{ row, col }];
    state.interaction.isActive = mode === 'pointer';
    element.dataset.state = 'selected';
  }

  function isOrthogonalNeighbor(a, b) {
    return Math.abs(a.row - b.row) + Math.abs(a.col - b.col) === 1;
  }

  function processPointerCell(row, col) {
    if (tryBacktrackSelection(row, col)) {
      return;
    }
    const added = addCellToSelection(row, col);
    if (!added) {
      return;
    }
    if (state.interaction.path.length === getRequiredLinkLength()) {
      scheduleFinalizeSelection();
    }
  }

  function tryBacktrackSelection(row, col) {
    if (!Array.isArray(state.interaction.path) || state.interaction.path.length < 2) {
      return false;
    }
    const previous = state.interaction.path[state.interaction.path.length - 2];
    if (previous.row === row && previous.col === col) {
      const removed = state.interaction.path.pop();
      if (removed) {
        const removedElement = getCellElement(removed.row, removed.col);
        if (removedElement) {
          removedElement.dataset.state = '';
        }
      }
      clearFinalizeTimer();
      return true;
    }
    return false;
  }

  function addCellToSelection(row, col) {
    if (!Array.isArray(state.interaction.path)) {
      state.interaction.path = [];
    }
    const limit = getRequiredLinkLength();
    if (state.interaction.path.length >= limit) {
      return false;
    }
    const exists = state.interaction.path.some(cell => cell.row === row && cell.col === col);
    if (exists) {
      return false;
    }
    if (
      state.interaction.path.length > 0
      && !state.interaction.path.some(existing => isOrthogonalNeighbor(existing, { row, col }))
    ) {
      return false;
    }
    state.interaction.path.push({ row, col });
    const element = getCellElement(row, col);
    if (element) {
      element.dataset.state = 'selected';
    }
    return true;
  }

  function handlePointerDown(event) {
    if (state.isVictory) {
      return;
    }
    const element = event.currentTarget;
    const row = Number.parseInt(element.dataset.row, 10);
    const col = Number.parseInt(element.dataset.col, 10);
    if (!Number.isFinite(row) || !Number.isFinite(col)) {
      return;
    }
    if (typeof event.preventDefault === 'function') {
      event.preventDefault();
    }
    startSelection(row, col, 'pointer', event.pointerId);
    if (
      element instanceof Element
      && typeof element.setPointerCapture === 'function'
      && Number.isFinite(event.pointerId)
    ) {
      try {
        element.setPointerCapture(event.pointerId);
        state.interaction.captureElement = element;
      } catch (captureError) {
        state.interaction.captureElement = null;
      }
    } else {
      state.interaction.captureElement = null;
    }
  }

  function handlePointerEnter(event) {
    if (state.interaction.mode !== 'pointer') {
      return;
    }
    if (state.interaction.pointerId !== event.pointerId) {
      return;
    }
    if (!state.interaction.isActive) {
      return;
    }
    const element = event.currentTarget;
    const row = Number.parseInt(element.dataset.row, 10);
    const col = Number.parseInt(element.dataset.col, 10);
    if (!Number.isFinite(row) || !Number.isFinite(col)) {
      return;
    }
    processPointerCell(row, col);
  }

  function resolvePointerMoveTarget(event) {
    const currentTarget = event.currentTarget instanceof Element ? event.currentTarget : null;
    const rawTarget = event.target instanceof Element ? event.target : null;
    const hasCapture = Boolean(
      currentTarget
      && typeof currentTarget.hasPointerCapture === 'function'
      && Number.isFinite(event.pointerId)
      && currentTarget.hasPointerCapture(event.pointerId)
    );
    if (!hasCapture) {
      const element = rawTarget?.closest?.('.link__cell');
      if (element) {
        return element;
      }
    }
    if (typeof document.elementFromPoint === 'function') {
      const fromPoint = document.elementFromPoint(event.clientX, event.clientY);
      if (fromPoint instanceof Element) {
        return fromPoint.closest('.link__cell');
      }
    }
    return null;
  }

  function handlePointerMove(event) {
    if (state.interaction.mode !== 'pointer') {
      return;
    }
    if (state.interaction.pointerId !== event.pointerId) {
      return;
    }
    if (!state.interaction.isActive) {
      return;
    }
    const element = resolvePointerMoveTarget(event);
    if (!element) {
      return;
    }
    const row = Number.parseInt(element.dataset.row, 10);
    const col = Number.parseInt(element.dataset.col, 10);
    if (!Number.isFinite(row) || !Number.isFinite(col)) {
      return;
    }
    processPointerCell(row, col);
  }

  function handlePointerUp(event) {
    if (state.interaction.mode !== 'pointer') {
      return;
    }
    if (state.interaction.pointerId !== event.pointerId) {
      return;
    }
    if (state.interaction.captureElement) {
      const captureElement = state.interaction.captureElement;
      if (
        captureElement instanceof Element
        && typeof captureElement.releasePointerCapture === 'function'
        && Number.isFinite(event.pointerId)
        && captureElement.hasPointerCapture?.(event.pointerId)
      ) {
        captureElement.releasePointerCapture(event.pointerId);
      }
      state.interaction.captureElement = null;
    }
    state.interaction.isActive = false;
    if (state.interaction.path.length === getRequiredLinkLength()) {
      if (!state.interaction.finalizeTimeoutId) {
        scheduleFinalizeSelection();
      }
      return;
    }
    resetInteraction();
  }

  function handlePointerCancel(event) {
    if (state.interaction.mode !== 'pointer') {
      return;
    }
    if (state.interaction.pointerId !== event.pointerId) {
      return;
    }
    if (state.interaction.captureElement) {
      const captureElement = state.interaction.captureElement;
      if (
        captureElement instanceof Element
        && typeof captureElement.releasePointerCapture === 'function'
        && Number.isFinite(event.pointerId)
        && captureElement.hasPointerCapture?.(event.pointerId)
      ) {
        captureElement.releasePointerCapture(event.pointerId);
      }
      state.interaction.captureElement = null;
    }
    state.interaction.isActive = false;
    resetInteraction();
  }

  function handleCellClick(event) {
    if (state.isVictory) {
      return;
    }
    if (state.interaction.mode === 'pointer') {
      return;
    }
    const element = event.currentTarget;
    const row = Number.parseInt(element.dataset.row, 10);
    const col = Number.parseInt(element.dataset.col, 10);
    if (!Number.isFinite(row) || !Number.isFinite(col)) {
      return;
    }
    if (state.interaction.mode !== 'click' || state.interaction.path.length === 0) {
      startSelection(row, col, 'click', null);
      return;
    }
    const added = addCellToSelection(row, col);
    if (!added) {
      return;
    }
    if (state.interaction.path.length === getRequiredLinkLength()) {
      scheduleFinalizeSelection();
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      resetInteraction();
    }
  }

  function applyHistoryChange(changes, direction) {
    if (!Array.isArray(changes)) {
      return;
    }
    changes.forEach(change => {
      const cell = getCellData(change.row, change.col);
      if (!cell) {
        return;
      }
      cell.value = direction === 'undo' ? change.previous : change.next;
      updateCell(change.row, change.col);
    });
  }

  function undoLastMove() {
    if (!state.history.length) {
      setMessage(
        'index.sections.link.messages.nothingToUndo',
        'Aucun coup à annuler.'
      );
      return;
    }
    const entry = state.history.pop();
    applyHistoryChange(entry.changes, 'undo');
    state.moves = Math.max(0, state.moves - 1);
    updateMovesDisplay();
    updateButtonsState();
    setVictoryState(false);
    setMessage(
      'index.sections.link.messages.undo',
      'Dernier coup annulé.'
    );
  }

  function restartLevel() {
    if (!state.initialBoard) {
      return;
    }
    state.board = cloneBoard(state.initialBoard) || [];
    state.history = [];
    state.moves = 0;
    setVictoryState(false);
    updateAllCells();
    updateMovesDisplay();
    updateButtonsState();
    setMessage(
      'index.sections.link.messages.restart',
      'Puzzle réinitialisé.'
    );
  }

  function clearBoardElements() {
    if (state.elements && state.elements.board) {
      state.elements.board.innerHTML = '';
    }
    state.cellElements = [];
  }

  function buildBoardElements() {
    if (!state.elements || !state.elements.board) {
      return;
    }
    clearBoardElements();
    const size = state.board.length;
    state.elements.board.style.setProperty('--link-size', String(size));
    const fragment = document.createDocumentFragment();
    state.cellElements = new Array(size);
    for (let row = 0; row < size; row += 1) {
      const rowElements = new Array(size);
      for (let col = 0; col < size; col += 1) {
        const cellButton = document.createElement('button');
        cellButton.type = 'button';
        cellButton.className = 'link__cell';
        cellButton.dataset.row = String(row);
        cellButton.dataset.col = String(col);
        cellButton.dataset.value = '0';
        cellButton.dataset.type = 'normal';
        cellButton.setAttribute('aria-label', `Ligne ${row + 1}, colonne ${col + 1}`);
        const content = document.createElement('span');
        content.className = 'link__cell-content';
        content.setAttribute('aria-hidden', 'true');
        const plusSpan = document.createElement('span');
        plusSpan.className = 'link__cell-plus';
        plusSpan.textContent = '+';
        const valueSpan = document.createElement('span');
        valueSpan.className = 'link__cell-value';
        valueSpan.textContent = '0';
        content.appendChild(plusSpan);
        content.appendChild(valueSpan);
        cellButton.appendChild(content);
        cellButton.addEventListener('pointerdown', handlePointerDown);
        cellButton.addEventListener('pointerenter', handlePointerEnter);
        cellButton.addEventListener('pointermove', handlePointerMove);
        cellButton.addEventListener('pointerup', handlePointerUp);
        cellButton.addEventListener('pointercancel', handlePointerCancel);
        cellButton.addEventListener('click', handleCellClick);
        fragment.appendChild(cellButton);
        rowElements[col] = cellButton;
      }
      state.cellElements[row] = rowElements;
    }
    state.elements.board.appendChild(fragment);
    updateAllCells();
  }

  function generatePairs(size, totalCells, pairCount) {
    const indices = shuffle(Array.from({ length: totalCells }, (_, index) => index));
    const pairs = new Map();
    let pointer = 0;
    for (let id = 0; id < pairCount; id += 1) {
      while (pointer < indices.length && pairs.has(indices[pointer])) {
        pointer += 1;
      }
      if (pointer >= indices.length - 1) {
        break;
      }
      const firstIndex = indices[pointer];
      pointer += 1;
      while (pointer < indices.length && pairs.has(indices[pointer])) {
        pointer += 1;
      }
      if (pointer >= indices.length) {
        break;
      }
      const secondIndex = indices[pointer];
      pointer += 1;
      const firstRow = Math.floor(firstIndex / size);
      const firstCol = firstIndex % size;
      const secondRow = Math.floor(secondIndex / size);
      const secondCol = secondIndex % size;
      const pairColor = PAIR_COLORS[id % PAIR_COLORS.length];
      pairs.set(firstIndex, { id, color: pairColor, cells: [firstRow, firstCol] });
      pairs.set(secondIndex, { id, color: pairColor, cells: [secondRow, secondCol] });
    }
    const result = new Map();
    pairs.forEach((value, index) => {
      const coords = {
        row: value.cells[0],
        col: value.cells[1],
        pairColor: value.color,
        pairId: value.id
      };
      const existing = result.get(value.id) || [];
      existing.push({ row: coords.row, col: coords.col });
      result.set(value.id, existing);
    });
    state.pairs = result;
    return pairs;
  }

  function buildInitialBoard(size, difficultyConfig) {
    const totalCells = size * size;
    const fallbackDifficulty =
      DEFAULT_CONFIG.difficulties[state.difficulty]
      || DEFAULT_CONFIG.difficulties[DEFAULT_CONFIG.defaultDifficulty];
    const plusRange = difficultyConfig.plusCells || fallbackDifficulty.plusCells;
    const plusMin = Math.min(plusRange.min, totalCells);
    const plusMax = Math.min(Math.max(plusRange.max, plusMin), totalCells);
    const plusTarget = Math.floor(Math.random() * (plusMax - plusMin + 1)) + plusMin;
    const indices = shuffle(Array.from({ length: totalCells }, (_, index) => index));
    const plusSet = new Set(indices.slice(0, plusTarget));
    const twinRange = difficultyConfig.twinPairs || fallbackDifficulty.twinPairs;
    const maxPairsAllowed = Math.floor(totalCells / 2);
    const minPairs = Math.min(twinRange.min, maxPairsAllowed);
    const maxPairs = Math.min(Math.max(twinRange.max, minPairs), maxPairsAllowed);
    const pairCount = Math.floor(Math.random() * (maxPairs - minPairs + 1)) + minPairs;
    const pairAssignments = generatePairs(size, totalCells, pairCount);
    const board = new Array(size);
    const pairMap = new Map();
    for (let row = 0; row < size; row += 1) {
      const rowCells = new Array(size);
      for (let col = 0; col < size; col += 1) {
        const index = row * size + col;
        const pairInfo = pairAssignments.get(index) || null;
        const type = plusSet.has(index) ? 'plus' : 'normal';
        const value = type === 'plus' ? 10 : 0;
        rowCells[col] = {
          type,
          value,
          pairId: pairInfo ? pairInfo.id : null,
          pairColor: pairInfo ? pairInfo.color : null
        };
        if (pairInfo) {
          const existing = pairMap.get(pairInfo.id) || [];
          existing.push({ row, col });
          pairMap.set(pairInfo.id, existing);
        }
      }
      board[row] = rowCells;
    }
    state.pairs = pairMap;
    return board;
  }

  function randomLinePattern(size, length) {
    const segments = Math.max(2, Math.round(length));
    if (!Number.isFinite(size) || size < segments) {
      return null;
    }
    const horizontal = Math.random() < 0.5;
    if (horizontal) {
      const row = Math.floor(Math.random() * size);
      const maxStart = size - segments;
      const startCol = Math.floor(Math.random() * (maxStart + 1));
      return Array.from({ length: segments }, (_, index) => ({ row, col: startCol + index }));
    }
    const col = Math.floor(Math.random() * size);
    const maxStart = size - segments;
    const startRow = Math.floor(Math.random() * (maxStart + 1));
    return Array.from({ length: segments }, (_, index) => ({ row: startRow + index, col }));
  }

  function randomCornerPattern(size) {
    if (!Number.isFinite(size) || size < 2) {
      return null;
    }
    for (let attempts = 0; attempts < 40; attempts += 1) {
      const pivotRow = Math.floor(Math.random() * size);
      const pivotCol = Math.floor(Math.random() * size);
      const orientations = shuffle([
        { dr1: -1, dc1: 0, dr2: 0, dc2: 1 },
        { dr1: -1, dc1: 0, dr2: 0, dc2: -1 },
        { dr1: 1, dc1: 0, dr2: 0, dc2: 1 },
        { dr1: 1, dc1: 0, dr2: 0, dc2: -1 }
      ]);
      for (let i = 0; i < orientations.length; i += 1) {
        const orient = orientations[i];
        const firstRow = pivotRow + orient.dr1;
        const firstCol = pivotCol + orient.dc1;
        const secondRow = pivotRow + orient.dr2;
        const secondCol = pivotCol + orient.dc2;
        if (
          firstRow >= 0
          && firstRow < size
          && firstCol >= 0
          && firstCol < size
          && secondRow >= 0
          && secondRow < size
          && secondCol >= 0
          && secondCol < size
        ) {
          return [
            { row: pivotRow, col: pivotCol },
            { row: firstRow, col: firstCol },
            { row: secondRow, col: secondCol }
          ];
        }
      }
    }
    return randomLinePattern(size, 3);
  }

  function randomSquarePattern(size) {
    if (!Number.isFinite(size) || size < 2) {
      return null;
    }
    const maxStart = size - 2;
    const row = Math.floor(Math.random() * (maxStart + 1));
    const col = Math.floor(Math.random() * (maxStart + 1));
    return [
      { row, col },
      { row, col: col + 1 },
      { row: row + 1, col: col + 1 },
      { row: row + 1, col }
    ];
  }

  function randomVariantPattern(size, variants) {
    if (!Array.isArray(variants) || variants.length === 0) {
      return null;
    }
    const candidates = shuffle(variants.slice());
    for (let index = 0; index < candidates.length; index += 1) {
      const variant = candidates[index];
      const maxRow = variant.reduce((max, point) => Math.max(max, point.row), 0);
      const maxCol = variant.reduce((max, point) => Math.max(max, point.col), 0);
      if (!Number.isFinite(size) || size <= maxRow || size <= maxCol) {
        continue;
      }
      const rowLimit = size - maxRow - 1;
      const colLimit = size - maxCol - 1;
      if (rowLimit < 0 || colLimit < 0) {
        continue;
      }
      const baseRow = Math.floor(Math.random() * (rowLimit + 1));
      const baseCol = Math.floor(Math.random() * (colLimit + 1));
      return variant.map(point => ({ row: baseRow + point.row, col: baseCol + point.col }));
    }
    return null;
  }

  function randomZigZagPattern(size) {
    return randomVariantPattern(size, ZIGZAG_SHAPE_VARIANT_LIST);
  }

  function randomLongLPattern(size) {
    return randomVariantPattern(size, L_SHAPE_VARIANT_LIST);
  }

  function randomPatternForLength(size, length) {
    const segments = Math.round(length);
    if (segments === 2) {
      return randomLinePattern(size, segments);
    }
    if (segments === 3) {
      const generators = shuffle([
        () => randomLinePattern(size, segments),
        () => randomCornerPattern(size)
      ]);
      for (let i = 0; i < generators.length; i += 1) {
        const pattern = generators[i]();
        if (Array.isArray(pattern) && pattern.length === segments) {
          return pattern;
        }
      }
      return null;
    }
    if (segments === 4) {
      const generators = shuffle([
        () => randomLinePattern(size, segments),
        () => randomSquarePattern(size),
        () => randomZigZagPattern(size),
        () => randomLongLPattern(size)
      ]);
      for (let i = 0; i < generators.length; i += 1) {
        const pattern = generators[i]();
        if (Array.isArray(pattern) && pattern.length === segments) {
          return pattern;
        }
      }
      return null;
    }
    return null;
  }

  function scrambleBoardBase(board, moveCount) {
    const size = board.length;
    const linkLength = getRequiredLinkLength();
    let appliedMoves = 0;
    let attempts = 0;
    const maxAttempts = moveCount * 6 + 30;
    while (appliedMoves < moveCount && attempts < maxAttempts) {
      attempts += 1;
      const pattern = randomPatternForLength(size, linkLength);
      if (!Array.isArray(pattern) || pattern.length !== linkLength) {
        continue;
      }
      state.board = board;
      const applied = applyPattern(pattern, { recordHistory: false, skipRender: true });
      if (!applied) {
        continue;
      }
      appliedMoves += 1;
    }
  }

  function scrambleBoardPlus(board, moveCount) {
    const size = board.length;
    const linkLength = getRequiredLinkLength();
    const visitCounts = Array.from({ length: size }, () => new Array(size).fill(0));
    let appliedMoves = 0;
    let attempts = 0;
    const maxAttempts = moveCount * 8 + 50;
    while (appliedMoves < moveCount && attempts < maxAttempts) {
      attempts += 1;
      const pattern = randomPatternForLength(size, linkLength);
      if (!Array.isArray(pattern) || pattern.length !== linkLength) {
        continue;
      }
      state.board = board;
      const affectedCells = collectAffectedCells(pattern);
      if (!affectedCells.length) {
        continue;
      }
      if (affectedCells.some(cell => visitCounts[cell.row][cell.col] >= MAX_CREATION_VISITS)) {
        continue;
      }
      const applied = applyPattern(pattern, {
        recordHistory: false,
        skipRender: true,
        effect: applyCreationCellEffect
      });
      if (!applied) {
        continue;
      }
      affectedCells.forEach(cell => {
        visitCounts[cell.row][cell.col] += 1;
      });
      appliedMoves += 1;
    }
  }

  function scrambleBoardRandom(board, moveCount) {
    const size = board.length;
    const linkLength = getRequiredLinkLength();
    const visitCounts = Array.from({ length: size }, () => new Array(size).fill(0));
    let appliedMoves = 0;
    let attempts = 0;
    const maxAttempts = moveCount * 8 + 50;
    while (appliedMoves < moveCount && attempts < maxAttempts) {
      attempts += 1;
      const pattern = randomPatternForLength(size, linkLength);
      if (!Array.isArray(pattern) || pattern.length !== linkLength) {
        continue;
      }
      state.board = board;
      const usePlus = Math.random() < 0.5;
      if (usePlus) {
        const affectedCells = collectAffectedCells(pattern);
        if (!affectedCells.length) {
          continue;
        }
        if (affectedCells.some(cell => visitCounts[cell.row][cell.col] >= MAX_CREATION_VISITS)) {
          continue;
        }
        const applied = applyPattern(pattern, {
          recordHistory: false,
          skipRender: true,
          effect: applyCreationCellEffect
        });
        if (!applied) {
          continue;
        }
        affectedCells.forEach(cell => {
          visitCounts[cell.row][cell.col] += 1;
        });
        appliedMoves += 1;
      } else {
        const applied = applyPattern(pattern, { recordHistory: false, skipRender: true });
        if (!applied) {
          continue;
        }
        appliedMoves += 1;
      }
    }
    if (appliedMoves < moveCount) {
      scrambleBoardBase(board, moveCount - appliedMoves);
    }
  }

  function scrambleBoard(board, moveCount, mode) {
    if (mode === 'base') {
      scrambleBoardBase(board, moveCount);
      return;
    }
    if (mode === 'plus') {
      scrambleBoardPlus(board, moveCount);
      return;
    }
    scrambleBoardRandom(board, moveCount);
  }

  function generateLevel() {
    const difficultyConfig = getDifficultyConfig(state.difficulty);
    const fallbackDifficulty =
      DEFAULT_CONFIG.difficulties[state.difficulty]
      || DEFAULT_CONFIG.difficulties[DEFAULT_CONFIG.defaultDifficulty];
    syncGenerationSelect();
    const sizeRange = difficultyConfig.size || fallbackDifficulty.size;
    const sizeMin = clampInteger(sizeRange.min, 3, MAX_BOARD_SIZE, fallbackDifficulty.size.min);
    const sizeMax = clampInteger(
      sizeRange.max,
      sizeMin,
      MAX_BOARD_SIZE,
      Math.max(sizeMin, fallbackDifficulty.size.max)
    );
    const size = Math.floor(Math.random() * (sizeMax - sizeMin + 1)) + sizeMin;
    const board = buildInitialBoard(size, difficultyConfig);
    const scrambleRange = difficultyConfig.scrambleMoves || fallbackDifficulty.scrambleMoves;
    const scrambleMin = clampInteger(scrambleRange.min, 0, 999, fallbackDifficulty.scrambleMoves.min);
    const scrambleMax = clampInteger(
      scrambleRange.max,
      scrambleMin,
      999,
      Math.max(scrambleMin, fallbackDifficulty.scrambleMoves.max)
    );
    const scrambleCount = Math.floor(Math.random() * (scrambleMax - scrambleMin + 1)) + scrambleMin;
    scrambleBoard(board, scrambleCount, state.generationMode);
    state.board = board;
    state.initialBoard = cloneBoard(board);
    state.size = size;
    state.moves = 0;
    state.history = [];
    setVictoryState(false);
    updateMovesDisplay();
    updateButtonsState();
    const difficultyLabel = getDifficultyLabel(state.difficulty);
    const linkLength = getRequiredLinkLength();
    setMessage(
      'index.sections.link.messages.newLevel',
      `Nouveau niveau ${difficultyLabel} généré. Reliez ${formatNumber(linkLength)} cases pour ajuster les valeurs !`,
      {
        difficulty: difficultyLabel,
        count: formatNumber(linkLength)
      }
    );
    buildBoardElements();
    syncDifficultySelect();
    syncLinkLengthSelect();
  }

  function setGenerationMode(mode, options) {
    const normalized = typeof mode === 'string' ? mode.toLowerCase() : '';
    if (!GENERATION_MODES.includes(normalized)) {
      return;
    }
    const shouldRegenerate = !options || options.regenerate !== false;
    if (state.generationMode === normalized) {
      if (shouldRegenerate) {
        resetInteraction();
        generateLevel();
      }
      return;
    }
    state.generationMode = normalized;
    syncGenerationSelect();
    if (shouldRegenerate) {
      resetInteraction();
      generateLevel();
    }
  }

  function setLinkLength(value, options) {
    const fallback = getRequiredLinkLength();
    const numeric = typeof value === 'number' ? value : Number.parseInt(value, 10);
    const resolved = clampInteger(
      numeric,
      LINK_LENGTH_OPTIONS[0],
      LINK_LENGTH_OPTIONS[LINK_LENGTH_OPTIONS.length - 1],
      fallback
    );
    if (!LINK_LENGTH_OPTIONS.includes(resolved)) {
      return;
    }
    const shouldRegenerate = !options || options.regenerate !== false;
    if (state.linkLength === resolved) {
      if (shouldRegenerate) {
        resetInteraction();
        generateLevel();
      }
      return;
    }
    state.linkLength = resolved;
    syncLinkLengthSelect();
    if (shouldRegenerate) {
      resetInteraction();
      generateLevel();
    }
  }

  function setDifficulty(key, options) {
    if (!state.config || !state.config.difficulties) {
      return;
    }
    const normalized = typeof key === 'string' ? key.toLowerCase() : '';
    if (!Object.prototype.hasOwnProperty.call(state.config.difficulties, normalized)) {
      return;
    }
    const shouldRegenerate = !options || options.regenerate !== false;
    if (state.difficulty === normalized) {
      if (shouldRegenerate) {
        resetInteraction();
        generateLevel();
      }
      return;
    }
    state.difficulty = normalized;
    syncDifficultySelect();
    if (shouldRegenerate) {
      resetInteraction();
      generateLevel();
    }
  }

  function handleGenerationChange(event) {
    const select = event && event.target ? event.target : null;
    if (!select || typeof select.value !== 'string') {
      return;
    }
    setGenerationMode(select.value, { regenerate: true });
  }

  function handleLinkLengthChange(event) {
    const select = event && event.target ? event.target : null;
    if (!select || typeof select.value !== 'string') {
      return;
    }
    setLinkLength(select.value, { regenerate: true });
  }

  function handleDifficultyChange(event) {
    const select = event && event.target ? event.target : null;
    if (!select || typeof select.value !== 'string') {
      return;
    }
    setDifficulty(select.value, { regenerate: true });
  }

  function handleUndoClick() {
    resetInteraction();
    undoLastMove();
  }

  function handleRestartClick() {
    resetInteraction();
    restartLevel();
  }

  function handleNewLevelClick() {
    resetInteraction();
    generateLevel();
  }

  function initialize() {
    if (state.initialized) {
      return;
    }
    state.elements = getElements();
    if (!state.elements) {
      return;
    }
    state.initialized = true;
    attachLanguageListener();
    state.elements.generationSelect?.addEventListener('change', handleGenerationChange);
    state.elements.linkLengthSelect?.addEventListener('change', handleLinkLengthChange);
    state.elements.difficultySelect?.addEventListener('change', handleDifficultyChange);
    state.elements.undo?.addEventListener('click', handleUndoClick);
    state.elements.restart?.addEventListener('click', handleRestartClick);
    state.elements.newLevel?.addEventListener('click', handleNewLevelClick);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerCancel);
    window.addEventListener('keydown', handleKeyDown);
    syncDifficultySelect();
    syncGenerationSelect();
    syncLinkLengthSelect();
    loadRemoteConfig();
    generateLevel();
  }

  const api = {
    onEnter() {
      initialize();
      refreshMessage();
    },
    onLeave() {
      resetInteraction();
    }
  };

  window.linkArcade = api;
})();
