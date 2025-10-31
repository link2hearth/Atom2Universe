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
    howLink: document.getElementById('pipeTapHowLink')
  };

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
    [DIRECTIONS.NORTH]: '│',
    [DIRECTIONS.SOUTH]: '│',
    [DIRECTIONS.NORTH | DIRECTIONS.SOUTH]: '│',
    [DIRECTIONS.EAST]: '─',
    [DIRECTIONS.WEST]: '─',
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
    rewardClaimed: false,
    lastAnalysis: null
  };

  const DIRECTION_ORDER = Object.freeze([
    { bit: DIRECTIONS.NORTH, dx: 0, dy: -1, opp: DIRECTIONS.SOUTH },
    { bit: DIRECTIONS.EAST, dx: 1, dy: 0, opp: DIRECTIONS.WEST },
    { bit: DIRECTIONS.SOUTH, dx: 0, dy: 1, opp: DIRECTIONS.NORTH },
    { bit: DIRECTIONS.WEST, dx: -1, dy: 0, opp: DIRECTIONS.EAST }
  ]);

  function indexFor(x, y, size) {
    return y * size + x;
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

  function evaluateConnections(grid) {
    const size = grid.length;
    const adjacency = new Map();
    const satisfied = new Set();
    const leakIds = new Set();
    const pipeIds = new Set();

    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const mask = grid[y][x];
        if (mask === 0) {
          continue;
        }
        const id = indexFor(x, y, size);
        pipeIds.add(id);
        adjacency.set(id, []);

        const neighbors = neighborsOf(x, y, size);
        const neighborByDir = new Map(neighbors.map(neighbor => [neighbor.dir, neighbor]));

        let hasConnection = false;
        let hasLeak = false;

        DIRECTION_ORDER.forEach(direction => {
          const neighbor = neighborByDir.get(direction.bit);
          const neighborMask = neighbor ? grid[neighbor.y][neighbor.x] : 0;
          const outgoing = (mask & direction.bit) !== 0;
          const incoming = neighbor ? (neighborMask & direction.opp) !== 0 : false;

          if (outgoing && incoming) {
            hasConnection = true;
            adjacency.get(id).push(indexFor(neighbor.x, neighbor.y, size));
          } else if (outgoing || incoming) {
            hasLeak = true;
          }
        });

        if (hasLeak) {
          leakIds.add(id);
        }

        if (hasConnection && !hasLeak) {
          satisfied.add(id);
        }
      }
    }

    const components = [];
    const visited = new Set();

    satisfied.forEach(id => {
      if (visited.has(id)) {
        return;
      }
      const component = new Set();
      const queue = [id];
      visited.add(id);
      component.add(id);

      while (queue.length) {
        const current = queue.shift();
        const neighbors = adjacency.get(current) || [];
        neighbors.forEach(nextId => {
          if (!satisfied.has(nextId) || visited.has(nextId)) {
            return;
          }
          visited.add(nextId);
          component.add(nextId);
          queue.push(nextId);
        });
      }

      components.push(component);
    });

    return {
      satisfied,
      leakIds,
      components,
      pipeCount: pipeIds.size
    };
  }

  function isSolved(grid, analysis) {
    const result = analysis || evaluateConnections(grid);
    if (!result || result.pipeCount === 0) {
      return false;
    }
    if (result.leakIds.size > 0) {
      return false;
    }
    if (result.satisfied.size !== result.pipeCount) {
      return false;
    }
    if (result.components.length !== 1) {
      return false;
    }
    return result.components[0]?.size === result.pipeCount;
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
  }

  function paintConnectedTiles() {
    if (!state.tiles.length) {
      return null;
    }
    const analysis = evaluateConnections(state.grid);
    state.lastAnalysis = analysis;
    for (let y = 0; y < state.tiles.length; y += 1) {
      for (let x = 0; x < state.tiles[y].length; x += 1) {
        const tile = state.tiles[y][x];
        if (!tile) {
          continue;
        }
        const id = indexFor(x, y, state.size);
        if (state.grid[y][x] !== 0 && analysis.satisfied.has(id)) {
          tile.classList.add('pipetap__tile--connected');
        } else {
          tile.classList.remove('pipetap__tile--connected');
        }
        if (analysis.leakIds.has(id)) {
          tile.classList.add('pipetap__tile--leaking');
        } else {
          tile.classList.remove('pipetap__tile--leaking');
        }
      }
    }
    return analysis;
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
    const analysis = paintConnectedTiles();

    if (isSolved(state.grid, analysis)) {
      state.solved = true;
      pauseTimer();
      awardCompletionTickets();
      updateWinMessage();
      showWin();
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

    state.seed = seed;
    state.size = size;
    state.rng = seeded(seed);
    state.grid = generateGrid(size, state.rng);
    state.source = findSource(state.grid);
    state.moves = 0;
    state.elapsedSeconds = 0;
    state.solved = false;
    state.rewardClaimed = false;
    state.lastAnalysis = null;
    hideWin();
    resetShareLabel();

    renderBoard();
    updateStats();
    pauseTimer();
    state.elapsedSeconds = 0;
    resumeTimer();
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

  function handleHowRequest(event) {
    event.preventDefault();
    const message = translate(
      'index.sections.pipeTap.how.message',
      'Faites pivoter chaque tuile pour relier toutes les conduites à partir de la source entourée. Les puzzles sont générés à partir d’un arbre couvrant puis mélangés, ils sont donc toujours solvables.'
    );
    window.alert(message);
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
    elements.sizeSelect?.addEventListener('change', handleNewGridRequest);
    elements.seedInput?.addEventListener('pointerdown', focusSeedInputForTouch);
    elements.seedInput?.addEventListener('touchstart', focusSeedInputForTouch, { passive: true });
  }

  function detachTimer() {
    pauseTimer();
  }

  function init() {
    attachEventListeners();
    const initialSize = normalizeSize(elements.sizeSelect?.value);
    startNewGame(elements.seedInput?.value || '', initialSize);
  }

  init();

  window.pipeTapArcade = {
    onEnter() {
      resumeTimer();
    },
    onLeave() {
      detachTimer();
    }
  };
})();
