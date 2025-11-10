(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-pipetap-root]');
  if (!root) {
    return;
  }

  const elements = {
    seedInput: document.getElementById('pipeTapSeedInput'),
    sizeSelect: document.getElementById('pipeTapSizeSelect'),
    newButton: document.getElementById('pipeTapNewButton'),
    board: document.getElementById('pipeTapBoard'),
    seedValue: document.getElementById('pipeTapSeedValue'),
    movesValue: document.getElementById('pipeTapMovesValue'),
    timeValue: document.getElementById('pipeTapTimeValue'),
    win: document.getElementById('pipeTapWin'),
    winMessage: document.getElementById('pipeTapWinMessage'),
    againButton: document.getElementById('pipeTapAgainButton'),
    nextButton: document.getElementById('pipeTapNextButton'),
    shareButton: document.getElementById('pipeTapShareButton'),
    howLink: document.getElementById('pipeTapHowLink'),
    howDialog: document.getElementById('pipeTapHowDialog'),
    howDialogTitle: document.getElementById('pipeTapHowDialogTitle'),
    howDialogMessage: document.getElementById('pipeTapHowDialogMessage'),
    howDialogClose: document.getElementById('pipeTapHowDialogClose')
  };

  const AUTOSAVE_GAME_ID = 'pipeTap';
  const AUTOSAVE_VERSION = 1;
  const AUTOSAVE_DEBOUNCE_MS = 200;

  const ALLOWED_SIZES = Object.freeze([4, 5, 6, 7, 8]);
  const DEFAULT_SIZE = 5;
  const COMPLETION_REWARD_BY_SIZE = Object.freeze({
    4: 1,
    5: 1,
    6: 2,
    7: 3,
    8: 4
  });

  const DIRECTIONS = Object.freeze({
    NORTH: 1,
    EAST: 2,
    SOUTH: 4,
    WEST: 8
  });

  const GLYPH = Object.freeze({
    0: '·',
    [DIRECTIONS.NORTH]: '╷',
    [DIRECTIONS.SOUTH]: '╵',
    [DIRECTIONS.NORTH | DIRECTIONS.SOUTH]: '│',
    [DIRECTIONS.EAST]: '╴',
    [DIRECTIONS.WEST]: '╶',
    [DIRECTIONS.EAST | DIRECTIONS.WEST]: '─',
    [DIRECTIONS.NORTH | DIRECTIONS.EAST]: '└',
    [DIRECTIONS.EAST | DIRECTIONS.SOUTH]: '┌',
    [DIRECTIONS.SOUTH | DIRECTIONS.WEST]: '┐',
    [DIRECTIONS.WEST | DIRECTIONS.NORTH]: '┘',
    [DIRECTIONS.NORTH | DIRECTIONS.EAST | DIRECTIONS.SOUTH]: '├',
    [DIRECTIONS.EAST | DIRECTIONS.SOUTH | DIRECTIONS.WEST]: '┬',
    [DIRECTIONS.SOUTH | DIRECTIONS.WEST | DIRECTIONS.NORTH]: '┤',
    [DIRECTIONS.WEST | DIRECTIONS.NORTH | DIRECTIONS.EAST]: '┴',
    [DIRECTIONS.NORTH | DIRECTIONS.EAST | DIRECTIONS.SOUTH | DIRECTIONS.WEST]: '┼'
  });

  const state = {
    rng: null,
    grid: [],
    tiles: [],
    size: DEFAULT_SIZE,
    seed: '',
    moves: 0,
    source: { x: 0, y: 0 },
    solved: false,
    timerId: null,
    timerAnchor: 0,
    elapsedSeconds: 0,
    shareResetTimer: null,
    rewardClaimed: false
  };

  let autosaveTimer = null;
  let autosaveSuppressed = false;
  let howDialogLastTrigger = null;
  let boardResizeObserver = null;
  let pendingBoardSizingFrame = null;

  function parsePixelValue(value) {
    if (typeof value !== 'string') {
      return 0;
    }
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function updateBoardSizing() {
    if (!elements.board || typeof window === 'undefined') {
      return;
    }
    const columns = Math.max(1, Number(state.size) || 1);
    const container = elements.board.parentElement instanceof HTMLElement
      ? elements.board.parentElement
      : elements.board;
    const containerWidth = container.getBoundingClientRect().width;
    if (!Number.isFinite(containerWidth) || containerWidth <= 0) {
      return;
    }
    const targetWidth = containerWidth * 0.95;
    const computedStyle = window.getComputedStyle(elements.board);
    const paddingLeft = parsePixelValue(computedStyle.paddingLeft);
    const paddingRight = parsePixelValue(computedStyle.paddingRight);
    const borderLeft = parsePixelValue(computedStyle.borderLeftWidth);
    const borderRight = parsePixelValue(computedStyle.borderRightWidth);
    const gapValue = parsePixelValue(
      computedStyle.columnGap || computedStyle.gridColumnGap || computedStyle.gap
    );
    const outerSpacing = paddingLeft + paddingRight + borderLeft + borderRight;
    const gapTotal = gapValue * Math.max(0, columns - 1);
    const availableContentWidth = targetWidth - outerSpacing;
    let tileSize = (availableContentWidth - gapTotal) / columns;
    if (!Number.isFinite(tileSize) || tileSize <= 0) {
      tileSize = availableContentWidth > 0 ? availableContentWidth / columns : 0;
    }
    if (tileSize <= 0) {
      const fallback = parsePixelValue(
        elements.board.style.getPropertyValue('--pipetap-tile-size')
      );
      tileSize = fallback > 0 ? fallback : 32;
    }
    const maxWidth = Math.max(targetWidth, 0);
    elements.board.style.setProperty('--pipetap-tile-size', `${tileSize}px`);
    elements.board.style.maxWidth = `${maxWidth}px`;
  }

  function scheduleBoardSizingUpdate() {
    if (typeof window === 'undefined') {
      updateBoardSizing();
      return;
    }
    if (pendingBoardSizingFrame != null) {
      return;
    }
    const run = () => {
      pendingBoardSizingFrame = null;
      updateBoardSizing();
    };
    if (typeof window.requestAnimationFrame === 'function') {
      pendingBoardSizingFrame = window.requestAnimationFrame(run);
    } else {
      pendingBoardSizingFrame = window.setTimeout(run, 16);
    }
  }

  function initBoardResizeObserver() {
    if (!elements.board || typeof window === 'undefined') {
      return;
    }
    if (typeof window.ResizeObserver !== 'function') {
      return;
    }
    const target = elements.board.parentElement instanceof HTMLElement
      ? elements.board.parentElement
      : elements.board;
    if (!target) {
      return;
    }
    if (boardResizeObserver) {
      try {
        boardResizeObserver.disconnect();
      } catch (error) {
        // Ignore observer disconnect errors.
      }
    }
    boardResizeObserver = new window.ResizeObserver(() => {
      scheduleBoardSizingUpdate();
    });
    boardResizeObserver.observe(target);
  }

  function initBoardViewportListeners() {
    if (typeof window === 'undefined') {
      return;
    }
    const handleViewportResize = () => {
      scheduleBoardSizingUpdate();
    };
    window.addEventListener('resize', handleViewportResize, { passive: true });
    window.addEventListener('orientationchange', handleViewportResize);
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', handleViewportResize);
      window.visualViewport.addEventListener('scroll', handleViewportResize);
    }
  }

  const HOW_DIALOG_FALLBACK_TITLE = 'Comment ça marche ?';
  const HOW_DIALOG_FALLBACK_MESSAGE = 'Faites pivoter chaque tuile pour relier toutes les conduites à partir de la source entourée. La grille est générée depuis un arbre couvrant puis chaque tuile est mélangée, le puzzle reste donc toujours solvable.';
  const HOW_DIALOG_FALLBACK_CLOSE = 'Fermer';

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

  function sanitizeMask(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return 0;
    }
    const mask = Math.floor(numeric) & (
      DIRECTIONS.NORTH
      | DIRECTIONS.EAST
      | DIRECTIONS.SOUTH
      | DIRECTIONS.WEST
    );
    return mask;
  }

  function buildAutosavePayload() {
    const size = normalizeSize(state.size);
    const grid = [];
    for (let y = 0; y < size; y += 1) {
      const row = [];
      for (let x = 0; x < size; x += 1) {
        const mask = state.grid?.[y]?.[x];
        row.push(sanitizeMask(mask));
      }
      grid.push(row);
    }
    const sourceX = Number.isFinite(state.source?.x) ? Math.max(0, Math.min(size - 1, Math.floor(state.source.x))) : 0;
    const sourceY = Number.isFinite(state.source?.y) ? Math.max(0, Math.min(size - 1, Math.floor(state.source.y))) : 0;
    return {
      version: AUTOSAVE_VERSION,
      seed: typeof state.seed === 'string' ? state.seed : '',
      size,
      grid,
      source: { x: sourceX, y: sourceY },
      moves: Math.max(0, Math.floor(Number(state.moves) || 0)),
      elapsedSeconds: Math.max(0, Math.floor(Number(state.elapsedSeconds) || 0)),
      solved: Boolean(state.solved),
      rewardClaimed: Boolean(state.rewardClaimed)
    };
  }

  function persistAutosaveNow() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    const payload = buildAutosavePayload();
    try {
      autosave.set(AUTOSAVE_GAME_ID, payload);
    } catch (error) {
      // Ignore autosave persistence errors.
    }
  }

  function scheduleAutosave() {
    if (autosaveSuppressed) {
      return;
    }
    if (typeof window === 'undefined') {
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

    const size = normalizeSize(payload.size);
    const gridPayload = Array.isArray(payload.grid) ? payload.grid : [];
    const grid = Array.from({ length: size }, (_, y) => {
      const rowSource = Array.isArray(gridPayload[y]) ? gridPayload[y] : [];
      return Array.from({ length: size }, (_, x) => sanitizeMask(rowSource[x]));
    });
    let nonZeroTiles = 0;
    for (let y = 0; y < grid.length; y += 1) {
      for (let x = 0; x < grid[y].length; x += 1) {
        if (grid[y][x] !== 0) {
          nonZeroTiles += 1;
        }
      }
    }
    if (nonZeroTiles === 0) {
      return false;
    }

    const seed = typeof payload.seed === 'string' ? payload.seed : '';
    const moves = Math.max(0, Math.floor(Number(payload.moves) || 0));
    const elapsedSeconds = Math.max(0, Math.floor(Number(payload.elapsedSeconds) || 0));
    const solved = Boolean(payload.solved);
    const rewardClaimed = Boolean(payload.rewardClaimed);
    const sourceX = Number.isFinite(payload.source?.x) ? Math.floor(payload.source.x) : null;
    const sourceY = Number.isFinite(payload.source?.y) ? Math.floor(payload.source.y) : null;
    const safeSource = sourceX != null && sourceY != null && sourceX >= 0 && sourceY >= 0
      && sourceX < size && sourceY < size
      ? { x: sourceX, y: sourceY }
      : findSource(grid);

    withAutosaveSuppressed(() => {
      pauseTimer();
      state.seed = seed;
      state.size = size;
      state.rng = seeded(seed || Date.now().toString(36));
      state.grid = grid;
      state.source = safeSource;
      state.moves = moves;
      state.elapsedSeconds = elapsedSeconds;
      state.solved = solved;
      state.rewardClaimed = rewardClaimed;
      state.shareResetTimer = null;
      if (elements.seedInput) {
        elements.seedInput.value = seed;
      }
      if (elements.sizeSelect) {
        elements.sizeSelect.value = String(size);
      }
      resetShareLabel();
      renderBoard();
      updateStats();
      if (solved) {
        updateWinMessage();
        showWin();
      } else {
        hideWin();
      }
    });

    if (solved) {
      pauseTimer();
    } else {
      resumeTimer();
    }

    scheduleAutosave();
    return true;
  }

  function translate(key, fallback, params) {
    const translator = typeof window !== 'undefined'
      && window.i18n
      && typeof window.i18n.t === 'function'
        ? window.i18n.t.bind(window.i18n)
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
        console.warn('PipeTap translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      if (!params || typeof params !== 'object') {
        return fallback;
      }
      let resolved = fallback;
      Object.keys(params).forEach(paramKey => {
        const value = params[paramKey];
        resolved = resolved.replace(new RegExp(`{{\\s*${paramKey}\\s*}}`, 'g'), String(value));
      });
      return resolved;
    }
    return key;
  }

  function xmur3(str) {
    let h = 1779033703 ^ str.length;
    for (let i = 0; i < str.length; i += 1) {
      h = Math.imul(h ^ str.charCodeAt(i), 3432918353);
      h = (h << 13) | (h >>> 19);
    }
    return function hash() {
      h = Math.imul(h ^ (h >>> 16), 2246822507);
      h = Math.imul(h ^ (h >>> 13), 3266489909);
      return (h ^ (h >>> 16)) >>> 0;
    };
  }

  function mulberry32(seed) {
    return function random() {
      let t = seed += 0x6D2B79F5;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function seeded(seedString) {
    const hash = xmur3(seedString)();
    return mulberry32(hash);
  }

  function normalizeSize(value) {
    const numeric = Number.parseInt(value, 10);
    if (Number.isFinite(numeric) && ALLOWED_SIZES.includes(numeric)) {
      return numeric;
    }
    return DEFAULT_SIZE;
  }

  function formatTime(seconds) {
    const safe = Number.isFinite(seconds) && seconds >= 0 ? Math.floor(seconds) : 0;
    const minutes = Math.floor(safe / 60);
    const secs = safe % 60;
    return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  function formatIntegerLocalized(value) {
    const numeric = Number.isFinite(Number(value)) ? Math.floor(Number(value)) : 0;
    const safe = numeric >= 0 ? numeric : 0;
    try {
      return safe.toLocaleString();
    } catch (error) {
      return String(safe);
    }
  }

  function rotCW(mask) {
    let value = 0;
    if (mask & DIRECTIONS.NORTH) value |= DIRECTIONS.EAST;
    if (mask & DIRECTIONS.EAST) value |= DIRECTIONS.SOUTH;
    if (mask & DIRECTIONS.SOUTH) value |= DIRECTIONS.WEST;
    if (mask & DIRECTIONS.WEST) value |= DIRECTIONS.NORTH;
    return value;
  }

  function neighborsOf(x, y, size) {
    return [
      { x, y: y - 1, dir: DIRECTIONS.NORTH, opp: DIRECTIONS.SOUTH },
      { x: x + 1, y, dir: DIRECTIONS.EAST, opp: DIRECTIONS.WEST },
      { x, y: y + 1, dir: DIRECTIONS.SOUTH, opp: DIRECTIONS.NORTH },
      { x: x - 1, y, dir: DIRECTIONS.WEST, opp: DIRECTIONS.EAST }
    ].filter(neighbor => (
      neighbor.x >= 0
      && neighbor.y >= 0
      && neighbor.x < size
      && neighbor.y < size
    ));
  }

  function generateGrid(size, rng) {
    const visited = Array.from({ length: size }, () => Array(size).fill(false));
    const startX = Math.floor(rng() * size);
    const startY = Math.floor(rng() * size);
    const stack = [[startX, startY]];
    const edges = [];
    visited[startY][startX] = true;

    while (stack.length) {
      const [cx, cy] = stack[stack.length - 1];
      const neighbors = neighborsOf(cx, cy, size);
      for (let i = neighbors.length - 1; i > 0; i -= 1) {
        const j = Math.floor(rng() * (i + 1));
        const tmp = neighbors[i];
        neighbors[i] = neighbors[j];
        neighbors[j] = tmp;
      }
      const next = neighbors.find(option => !visited[option.y][option.x]);
      if (!next) {
        stack.pop();
        continue;
      }
      edges.push({ x: cx, y: cy, dir: next.dir });
      edges.push({ x: next.x, y: next.y, dir: next.opp });
      visited[next.y][next.x] = true;
      stack.push([next.x, next.y]);
    }

    const grid = Array.from({ length: size }, () => Array(size).fill(0));
    edges.forEach(edge => {
      grid[edge.y][edge.x] |= edge.dir;
    });

    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const mask = grid[y][x];
        if (mask === 0) {
          continue;
        }
        const rotations = Math.floor(rng() * 4);
        for (let r = 0; r < rotations; r += 1) {
          grid[y][x] = rotCW(grid[y][x]);
        }
      }
    }

    return grid;
  }

  function findSource(grid) {
    const size = grid.length;
    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        if (grid[y][x] !== 0) {
          return { x, y };
        }
      }
    }
    return { x: 0, y: 0 };
  }

  function isSolved(grid, source) {
    const size = grid.length;
    const totalPipeTiles = grid.flat().reduce((count, mask) => (mask !== 0 ? count + 1 : count), 0);
    if (totalPipeTiles === 0) {
      return false;
    }
    const visited = new Set();
    const queue = [{ x: source.x, y: source.y }];
    visited.add(source.y * size + source.x);

    while (queue.length) {
      const current = queue.shift();
      const mask = grid[current.y][current.x];
      if (mask === 0) {
        continue;
      }
      const neighbors = neighborsOf(current.x, current.y, size);
      for (const neighbor of neighbors) {
        if (!(mask & neighbor.dir)) {
          continue;
        }
        const neighborMask = grid[neighbor.y][neighbor.x];
        if (!(neighborMask & neighbor.opp)) {
          return false;
        }
        const id = neighbor.y * size + neighbor.x;
        if (!visited.has(id) && neighborMask !== 0) {
          visited.add(id);
          queue.push({ x: neighbor.x, y: neighbor.y });
        }
      }
    }

    return visited.size === totalPipeTiles;
  }

  function computeReachableTiles(grid, source) {
    const size = grid.length;
    const visited = new Set();
    const queue = [{ x: source.x, y: source.y }];
    visited.add(source.y * size + source.x);

    while (queue.length) {
      const current = queue.shift();
      const mask = grid[current.y][current.x];
      if (mask === 0) {
        continue;
      }
      const neighbors = neighborsOf(current.x, current.y, size);
      neighbors.forEach(neighbor => {
        if (!(mask & neighbor.dir)) {
          return;
        }
        const neighborMask = grid[neighbor.y][neighbor.x];
        if (!(neighborMask & neighbor.opp)) {
          return;
        }
        const id = neighbor.y * size + neighbor.x;
        if (!visited.has(id)) {
          visited.add(id);
          if (neighborMask !== 0) {
            queue.push({ x: neighbor.x, y: neighbor.y });
          }
        }
      });
    }

    return visited;
  }

  function updateStats() {
    if (elements.seedValue) {
      elements.seedValue.textContent = state.seed || '—';
    }
    if (elements.movesValue) {
      elements.movesValue.textContent = String(state.moves);
    }
    if (elements.timeValue) {
      elements.timeValue.textContent = formatTime(state.elapsedSeconds);
    }
  }

  function resumeTimer() {
    if (state.solved) {
      return;
    }
    if (state.timerId != null) {
      return;
    }
    state.timerAnchor = Date.now() - state.elapsedSeconds * 1000;
    state.timerId = window.setInterval(() => {
      const seconds = Math.floor((Date.now() - state.timerAnchor) / 1000);
      if (Number.isFinite(seconds) && seconds !== state.elapsedSeconds) {
        state.elapsedSeconds = seconds;
        updateStats();
        scheduleAutosave();
      }
    }, 250);
    updateStats();
  }

  function pauseTimer() {
    if (state.timerId != null) {
      window.clearInterval(state.timerId);
      state.timerId = null;
      state.elapsedSeconds = Math.floor((Date.now() - state.timerAnchor) / 1000);
      updateStats();
    }
  }

  function resetShareLabel() {
    if (state.shareResetTimer != null) {
      window.clearTimeout(state.shareResetTimer);
      state.shareResetTimer = null;
    }
    if (elements.shareButton) {
      elements.shareButton.textContent = translate(
        'index.sections.pipeTap.win.share',
        'Copier seed'
      );
    }
  }

  function updateWinMessage() {
    if (!elements.winMessage) {
      return;
    }
    const message = translate(
      'index.sections.pipeTap.win.message',
      'Bravo ! Résolu en {{time}} avec {{moves}} rotations.',
      {
        time: formatTime(state.elapsedSeconds),
        moves: state.moves
      }
    );
    elements.winMessage.textContent = message;
  }

  function hideWin() {
    if (elements.win) {
      elements.win.hidden = true;
    }
  }

  function showWin() {
    if (!elements.win) {
      return;
    }
    elements.win.hidden = false;
    try {
      elements.win.animate(
        [
          { transform: 'translateY(10px)', opacity: 0 },
          { transform: 'translateY(0)', opacity: 1 }
        ],
        { duration: 200, easing: 'ease-out' }
      );
    } catch (error) {
      // Animation optional.
    }
  }

  function updateBoardAria() {
    if (!elements.board) {
      return;
    }
    elements.board.style.gridTemplateColumns = `repeat(${state.size}, var(--pipetap-tile-size))`;
    elements.board.setAttribute('aria-rowcount', String(state.size));
    elements.board.setAttribute('aria-colcount', String(state.size));
    scheduleBoardSizingUpdate();
  }

  function updateTileButton(button, mask) {
    if (!button) {
      return;
    }
    const glyph = GLYPH[mask] || GLYPH[0];
    button.textContent = glyph;
  }

  function buildTileButton(x, y, mask) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'pipetap__tile';
    button.dataset.x = String(x);
    button.dataset.y = String(y);
    button.setAttribute('role', 'gridcell');
    button.setAttribute('aria-rowindex', String(y + 1));
    button.setAttribute('aria-colindex', String(x + 1));
    const label = translate(
      'index.sections.pipeTap.tile.aria',
      'Tuile {{row}}, colonne {{column}}',
      { row: y + 1, column: x + 1 }
    );
    button.setAttribute('aria-label', label);
    updateTileButton(button, mask);
    button.addEventListener('click', handleTileClick, { passive: true });
    return button;
  }

  function renderBoard() {
    if (!elements.board) {
      return;
    }
    elements.board.innerHTML = '';
    state.tiles = Array.from({ length: state.size }, () => Array(state.size).fill(null));
    updateBoardAria();

    for (let y = 0; y < state.size; y += 1) {
      for (let x = 0; x < state.size; x += 1) {
        const button = buildTileButton(x, y, state.grid[y][x]);
        if (x === state.source.x && y === state.source.y) {
          button.classList.add('pipetap__tile--source');
        }
        elements.board.appendChild(button);
        state.tiles[y][x] = button;
      }
    }

    paintConnectedTiles();
    updateBoardSizing();
  }

  function paintConnectedTiles() {
    if (!state.tiles.length) {
      return;
    }
    const connected = computeReachableTiles(state.grid, state.source);
    for (let y = 0; y < state.tiles.length; y += 1) {
      for (let x = 0; x < state.tiles[y].length; x += 1) {
        const tile = state.tiles[y][x];
        if (!tile) {
          continue;
        }
        const id = y * state.size + x;
        if (connected.has(id) && state.grid[y][x] !== 0) {
          tile.classList.add('pipetap__tile--connected');
        } else {
          tile.classList.remove('pipetap__tile--connected');
        }
      }
    }
  }

  function handleTileClick(event) {
    if (state.solved) {
      return;
    }
    const button = event.currentTarget;
    const x = Number.parseInt(button.dataset.x, 10);
    const y = Number.parseInt(button.dataset.y, 10);
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      return;
    }
    const mask = state.grid[y][x];
    if (mask === 0) {
      return;
    }
    state.grid[y][x] = rotCW(mask);
    updateTileButton(button, state.grid[y][x]);
    state.moves += 1;
    updateStats();
    paintConnectedTiles();
    scheduleAutosave();

    if (isSolved(state.grid, state.source)) {
      state.solved = true;
      pauseTimer();
      awardCompletionTickets();
      updateWinMessage();
      showWin();
      flushAutosave();
    }
  }

  function getCompletionReward(size) {
    const reward = COMPLETION_REWARD_BY_SIZE[size];
    if (!Number.isFinite(reward) || reward <= 0) {
      return 0;
    }
    return Math.floor(reward);
  }

  function awardCompletionTickets() {
    if (state.rewardClaimed) {
      return;
    }
    const tickets = getCompletionReward(state.size);
    if (tickets <= 0) {
      state.rewardClaimed = true;
      return;
    }
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      state.rewardClaimed = true;
      return;
    }
    let gained = 0;
    try {
      gained = awardGacha(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('PipeTap: unable to grant gacha tickets', error);
      gained = 0;
    }
    state.rewardClaimed = true;
    scheduleAutosave();
    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = gained > 1 ? 's' : '';
      const message = translate(
        'scripts.arcade.pipeTap.rewardToast',
        'PipeTap : +{count} ticket{suffix} gacha !',
        {
          count: formatIntegerLocalized(gained),
          suffix
        }
      );
      showToast(message);
    }
  }

  function startNewGame(seedValue, requestedSize) {
    const size = normalizeSize(requestedSize);
    const trimmedSeed = typeof seedValue === 'string' ? seedValue.trim() : '';
    const seed = trimmedSeed ? trimmedSeed : Date.now().toString(36);
    withAutosaveSuppressed(() => {
      pauseTimer();
      state.seed = seed;
      state.size = size;
      state.rng = seeded(seed);
      state.grid = generateGrid(size, state.rng);
      state.source = findSource(state.grid);
      state.moves = 0;
      state.elapsedSeconds = 0;
      state.solved = false;
      state.rewardClaimed = false;
      state.shareResetTimer = null;
      hideWin();
      resetShareLabel();
      renderBoard();
      updateStats();
    });
    resumeTimer();
    scheduleAutosave();
  }

  function handleNewGridRequest() {
    const size = normalizeSize(elements.sizeSelect?.value);
    startNewGame(elements.seedInput?.value || '', size);
  }

  function handleNextSizeRequest() {
    if (!elements.sizeSelect) {
      return;
    }
    const currentSize = normalizeSize(elements.sizeSelect.value);
    const currentIndex = ALLOWED_SIZES.indexOf(currentSize);
    const nextIndex = Math.min(ALLOWED_SIZES.length - 1, currentIndex + 1);
    const nextSize = ALLOWED_SIZES[nextIndex];
    elements.sizeSelect.value = String(nextSize);
    if (elements.seedInput) {
      elements.seedInput.value = '';
    }
    startNewGame('', nextSize);
  }

  async function handleShareRequest() {
    if (!elements.shareButton) {
      return;
    }
    const seed = state.seed || '';
    if (!seed) {
      return;
    }
    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        await navigator.clipboard.writeText(seed);
      } else {
        throw new Error('Clipboard API unavailable');
      }
      elements.shareButton.textContent = translate(
        'index.sections.pipeTap.share.success',
        'Copié !'
      );
      state.shareResetTimer = window.setTimeout(() => {
        resetShareLabel();
      }, 1000);
    } catch (error) {
      const fallback = translate(
        'index.sections.pipeTap.share.fallback',
        'Seed : {{seed}}',
        { seed }
      );
      window.alert(fallback);
    }
  }

  function updateHowDialogTexts() {
    if (!elements.howDialog) {
      return;
    }
    if (elements.howDialogTitle) {
      elements.howDialogTitle.textContent = translate(
        'index.sections.pipeTap.how.title',
        HOW_DIALOG_FALLBACK_TITLE
      );
    }
    if (elements.howDialogMessage) {
      elements.howDialogMessage.textContent = translate(
        'index.sections.pipeTap.how.message',
        HOW_DIALOG_FALLBACK_MESSAGE
      );
    }
    if (elements.howDialogClose) {
      elements.howDialogClose.textContent = translate(
        'index.sections.pipeTap.how.close',
        HOW_DIALOG_FALLBACK_CLOSE
      );
    }
  }

  function closeHowDialog() {
    if (!elements.howDialog || elements.howDialog.hidden !== false) {
      return;
    }
    elements.howDialog.hidden = true;
    elements.howDialog.setAttribute('aria-hidden', 'true');
    const trigger = howDialogLastTrigger;
    howDialogLastTrigger = null;
    if (trigger && typeof trigger.focus === 'function') {
      window.requestAnimationFrame(() => {
        try {
          trigger.focus({ preventScroll: true });
        } catch (error) {
          trigger.focus();
        }
      });
    }
  }

  function openHowDialog(triggerElement) {
    if (!elements.howDialog || !elements.howDialogMessage || !elements.howDialogClose) {
      const fallbackMessage = translate(
        'index.sections.pipeTap.how.message',
        HOW_DIALOG_FALLBACK_MESSAGE
      );
      window.alert(fallbackMessage);
      return;
    }
    updateHowDialogTexts();
    elements.howDialog.hidden = false;
    elements.howDialog.setAttribute('aria-hidden', 'false');
    const activeElement = triggerElement && typeof triggerElement.focus === 'function'
      ? triggerElement
      : typeof document !== 'undefined' && document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    howDialogLastTrigger = activeElement;
    window.requestAnimationFrame(() => {
      if (elements.howDialogClose && typeof elements.howDialogClose.focus === 'function') {
        try {
          elements.howDialogClose.focus({ preventScroll: true });
        } catch (error) {
          elements.howDialogClose.focus();
        }
      } else if (typeof elements.howDialog.focus === 'function') {
        elements.howDialog.focus();
      }
    });
  }

  function handleHowRequest(event) {
    event.preventDefault();
    const triggerElement = event && event.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : null;
    openHowDialog(triggerElement);
  }

  function handleHowDialogClose(event) {
    event.preventDefault();
    closeHowDialog();
  }

  function handleHowDialogBackdrop(event) {
    if (event.target === elements.howDialog) {
      closeHowDialog();
    }
  }

  function handleHowDialogKeyDown(event) {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeHowDialog();
      return;
    }
    if (event.key === 'Tab' && elements.howDialogClose) {
      event.preventDefault();
      elements.howDialogClose.focus();
    }
  }

  function focusSeedInputForTouch(event) {
    if (!elements.seedInput) {
      return;
    }
    if (event?.type === 'touchstart' && typeof window !== 'undefined' && 'PointerEvent' in window) {
      return;
    }
    const pointerType = event && typeof event === 'object'
      ? event.pointerType || (event.type === 'touchstart' ? 'touch' : null)
      : null;
    if (pointerType && pointerType.toLowerCase() === 'mouse') {
      return;
    }
    window.requestAnimationFrame(() => {
      try {
        if (typeof elements.seedInput.focus === 'function') {
          elements.seedInput.focus({ preventScroll: true });
        }
      } catch (error) {
        if (typeof elements.seedInput.focus === 'function') {
          elements.seedInput.focus();
        }
      }
      if (typeof elements.seedInput.select === 'function') {
        elements.seedInput.select();
      }
    });
  }

  function attachEventListeners() {
    elements.newButton?.addEventListener('click', handleNewGridRequest);
    elements.againButton?.addEventListener('click', handleNewGridRequest);
    elements.nextButton?.addEventListener('click', handleNextSizeRequest);
    elements.shareButton?.addEventListener('click', handleShareRequest);
    elements.howLink?.addEventListener('click', handleHowRequest);
    elements.howDialogClose?.addEventListener('click', handleHowDialogClose);
    elements.howDialog?.addEventListener('click', handleHowDialogBackdrop);
    elements.howDialog?.addEventListener('keydown', handleHowDialogKeyDown);
    elements.sizeSelect?.addEventListener('change', handleNewGridRequest);
    elements.seedInput?.addEventListener('pointerdown', focusSeedInputForTouch);
    elements.seedInput?.addEventListener('touchstart', focusSeedInputForTouch, { passive: true });
  }

  function detachTimer() {
    pauseTimer();
  }

  function init() {
    attachEventListeners();
    initBoardViewportListeners();
    initBoardResizeObserver();
    const restored = restoreFromAutosave();
    if (!restored) {
      const initialSize = normalizeSize(elements.sizeSelect?.value);
      startNewGame(elements.seedInput?.value || '', initialSize);
    }
    scheduleBoardSizingUpdate();
  }

  init();

  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('beforeunload', () => {
      try {
        flushAutosave();
      } catch (error) {
        // Ignore flush errors during unload.
      }
    });
  }

  if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        try {
          flushAutosave();
        } catch (error) {
          // Ignore visibility flush errors.
        }
      }
    });
  }

  window.pipeTapArcade = {
    onEnter() {
      resumeTimer();
      scheduleBoardSizingUpdate();
    },
    onLeave() {
      detachTimer();
      flushAutosave();
    }
  };
})();
