(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-star-bridges-root]');
  if (!root) {
    return;
  }

  const elements = {
    seedInput: document.getElementById('starBridgesSeedInput'),
    sizeSelect: document.getElementById('starBridgesSizeSelect'),
    newButton: document.getElementById('starBridgesNewButton'),
    board: document.getElementById('starBridgesBoard'),
    boardMessage: document.getElementById('starBridgesBoardMessage'),
    seedValue: document.getElementById('starBridgesSeedValue'),
    bridgesValue: document.getElementById('starBridgesBridgesValue'),
    timeValue: document.getElementById('starBridgesTimeValue'),
    win: document.getElementById('starBridgesWin'),
    winMessage: document.getElementById('starBridgesWinMessage'),
    againButton: document.getElementById('starBridgesAgainButton'),
    nextButton: document.getElementById('starBridgesNextButton'),
    shareButton: document.getElementById('starBridgesShareButton'),
    howLink: document.getElementById('starBridgesHowLink')
  };

  const AUTOSAVE_GAME_ID = 'starBridges';
  const AUTOSAVE_VERSION = 2;
  const AUTOSAVE_DEBOUNCE_MS = 200;

  const ALLOWED_SIZES = Object.freeze([6, 7, 8]);
  const DEFAULT_SIZE = 6;
  const COMPLETION_REWARD_BY_SIZE = Object.freeze({
    6: Object.freeze({ min: 2, max: 4 }),
    7: Object.freeze({ min: 3, max: 6 }),
    8: Object.freeze({ min: 4, max: 8 })
  });

  const DIRECTIONS = Object.freeze({
    NORTH: 'north',
    NORTH_EAST: 'northEast',
    EAST: 'east',
    SOUTH_EAST: 'southEast',
    SOUTH: 'south',
    SOUTH_WEST: 'southWest',
    WEST: 'west',
    NORTH_WEST: 'northWest'
  });

  const state = {
    rng: null,
    size: DEFAULT_SIZE,
    seed: '',
    seedEntry: '',
    seedWasRandom: true,
    nodes: [],
    solution: new Map(),
    bridges: new Map(),
    bridgeTotals: new Map(),
    totalRequired: 0,
    moves: 0,
    solved: false,
    timerId: null,
    timerAnchor: 0,
    elapsedSeconds: 0,
    selectedNodeId: null,
    shareResetTimer: null,
    rewardClaimed: false
  };

  let autosaveTimer = null;
  let autosaveSuppressed = false;

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

  function sanitizeBridgeCount(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return 0;
    }
    return 1;
  }

  function buildAutosavePayload() {
    const bridges = {};
    state.bridges.forEach((count, key) => {
      if (count > 0) {
        bridges[key] = count;
      }
    });
    return {
      version: AUTOSAVE_VERSION,
      seed: typeof state.seed === 'string' ? state.seed : '',
      seedEntry: typeof state.seedEntry === 'string' ? state.seedEntry : '',
      seedWasRandom: Boolean(state.seedWasRandom),
      size: normalizeSize(state.size),
      bridges,
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

    const seed = typeof payload.seed === 'string' ? payload.seed : '';
    const size = normalizeSize(payload.size);
    if (!seed) {
      return false;
    }
    const restored = generatePuzzleWithSeed(seed, size);
    if (!restored) {
      return false;
    }

    const bridgesPayload = payload.bridges && typeof payload.bridges === 'object'
      ? payload.bridges
      : {};
    const validEdges = new Set(restored.edges.map(edge => edge.key));
    const bridges = new Map();
    Object.keys(bridgesPayload).forEach(key => {
      const count = sanitizeBridgeCount(bridgesPayload[key]);
      if (!validEdges.has(key)) {
        return;
      }
      if (count > 0) {
        bridges.set(key, count);
      }
    });
    const moves = Math.max(0, Math.floor(Number(payload.moves) || 0));
    const elapsedSeconds = Math.max(0, Math.floor(Number(payload.elapsedSeconds) || 0));
    const solved = Boolean(payload.solved);
    const rewardClaimed = Boolean(payload.rewardClaimed);
    const seedEntry = typeof payload.seedEntry === 'string' ? payload.seedEntry : '';
    const seedWasRandom = typeof payload.seedWasRandom === 'boolean'
      ? payload.seedWasRandom
      : seedEntry.trim().length === 0;

    withAutosaveSuppressed(() => {
      pauseTimer();
      applyPuzzle(restored);
      state.seed = seed;
      state.seedEntry = seedEntry;
      state.seedWasRandom = seedWasRandom;
      state.size = size;
      state.moves = moves;
      state.elapsedSeconds = elapsedSeconds;
      state.solved = solved;
      state.rewardClaimed = rewardClaimed;
      state.bridges = bridges;
      recomputeBridgeTotals();
      if (elements.seedInput) {
        elements.seedInput.value = seedEntry;
      }
      if (elements.sizeSelect) {
        elements.sizeSelect.value = String(size);
      }
      renderBoard();
      updateStats();
      updateBoardMessage();
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
        console.warn('Star Bridges translation error for', key, error);
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

  function shuffle(array, rng) {
    for (let i = array.length - 1; i > 0; i -= 1) {
      const j = Math.floor(rng() * (i + 1));
      const tmp = array[i];
      array[i] = array[j];
      array[j] = tmp;
    }
    return array;
  }

  function makeEdgeKey(a, b) {
    return a < b ? `${a}-${b}` : `${b}-${a}`;
  }

  function edgesCross(a, b) {
    if (!a || !b) {
      return false;
    }
    if (a.key === b.key) {
      return false;
    }
    if (a.from === b.from || a.from === b.to || a.to === b.from || a.to === b.to) {
      return false;
    }

    const ax1 = a.x1;
    const ay1 = a.y1;
    const ax2 = a.x2;
    const ay2 = a.y2;
    const bx1 = b.x1;
    const by1 = b.y1;
    const bx2 = b.x2;
    const by2 = b.y2;

    const EPSILON = 1e-9;

    function orientation(px, py, qx, qy, rx, ry) {
      const value = (qx - px) * (ry - py) - (qy - py) * (rx - px);
      if (Math.abs(value) <= EPSILON) {
        return 0;
      }
      return value > 0 ? 1 : -1;
    }

    function onSegment(px, py, qx, qy, rx, ry) {
      return rx <= Math.max(px, qx) + EPSILON
        && rx + EPSILON >= Math.min(px, qx)
        && ry <= Math.max(py, qy) + EPSILON
        && ry + EPSILON >= Math.min(py, qy);
    }

    const o1 = orientation(ax1, ay1, ax2, ay2, bx1, by1);
    const o2 = orientation(ax1, ay1, ax2, ay2, bx2, by2);
    const o3 = orientation(bx1, by1, bx2, by2, ax1, ay1);
    const o4 = orientation(bx1, by1, bx2, by2, ax2, ay2);

    if (o1 === 0 && onSegment(ax1, ay1, ax2, ay2, bx1, by1)) {
      return true;
    }
    if (o2 === 0 && onSegment(ax1, ay1, ax2, ay2, bx2, by2)) {
      return true;
    }
    if (o3 === 0 && onSegment(bx1, by1, bx2, by2, ax1, ay1)) {
      return true;
    }
    if (o4 === 0 && onSegment(bx1, by1, bx2, by2, ax2, ay2)) {
      return true;
    }

    return o1 !== o2 && o3 !== o4;
  }

  function addBridgeCount(nodeCounts, nodeId, direction, amount) {
    if (!nodeCounts.has(nodeId)) {
      nodeCounts.set(nodeId, {
        total: 0,
        north: 0,
        northEast: 0,
        east: 0,
        southEast: 0,
        south: 0,
        southWest: 0,
        west: 0,
        northWest: 0
      });
    }
    const entry = nodeCounts.get(nodeId);
    entry.total += amount;
    if (entry[direction] == null) {
      entry[direction] = 0;
    }
    entry[direction] += amount;
  }

  function generateLayout(size, rng) {
    const nodes = [];
    const used = new Set();
    const rowCounts = Array.from({ length: size }, () => 0);
    const colCounts = Array.from({ length: size }, () => 0);

    function addNode(x, y) {
      const key = `${x},${y}`;
      if (used.has(key)) {
        return false;
      }
      used.add(key);
      const id = nodes.length;
      nodes.push({ id, x, y, required: 0 });
      rowCounts[y] += 1;
      colCounts[x] += 1;
      return true;
    }

    const baseColumns = Array.from({ length: size }, (_, i) => i);
    for (let y = 0; y < size; y += 1) {
      const shuffled = shuffle(baseColumns.slice(), rng);
      let placed = 0;
      for (let i = 0; i < shuffled.length && placed < 2; i += 1) {
        const x = shuffled[i];
        if (addNode(x, y)) {
          placed += 1;
        }
      }
      if (placed < 2) {
        for (let x = 0; x < size && placed < 2; x += 1) {
          if (addNode(x, y)) {
            placed += 1;
          }
        }
      }
    }

    const minNodes = Math.max(nodes.length, Math.floor(size * 2.5));
    const maxNodes = Math.min(size * size - size, Math.floor(size * 3.2));
    const target = Math.max(minNodes, Math.min(maxNodes, nodes.length + Math.floor(rng() * size)));

    const allPositions = [];
    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        allPositions.push({ x, y });
      }
    }
    shuffle(allPositions, rng);
    for (let i = 0; i < allPositions.length && nodes.length < target; i += 1) {
      const { x, y } = allPositions[i];
      if (addNode(x, y)) {
        // added
      }
    }

    for (let x = 0; x < size; x += 1) {
      if (colCounts[x] > 0) {
        continue;
      }
      const rowOrder = shuffle(Array.from({ length: size }, (_, i) => i), rng);
      for (let i = 0; i < rowOrder.length; i += 1) {
        const y = rowOrder[i];
        if (addNode(x, y)) {
          break;
        }
      }
    }

    return nodes;
  }

  function buildEdges(nodes, size) {
    const grid = Array.from({ length: size }, () => Array(size).fill(null));
    nodes.forEach(node => {
      grid[node.y][node.x] = node;
    });

    const directions = [
      { dx: -1, dy: 0 },
      { dx: 1, dy: 0 },
      { dx: 0, dy: -1 },
      { dx: 0, dy: 1 },
      { dx: -1, dy: -1 },
      { dx: 1, dy: -1 },
      { dx: 1, dy: 1 },
      { dx: -1, dy: 1 }
    ];

    function orientationFromDelta(dx, dy) {
      if (dy === 0) {
        return 'horizontal';
      }
      if (dx === 0) {
        return 'vertical';
      }
      return dx === dy ? 'diagonal-down' : 'diagonal-up';
    }

    const edges = [];

    nodes.forEach(node => {
      const { x, y } = node;
      directions.forEach(({ dx, dy }) => {
        let nx = x + dx;
        let ny = y + dy;
        while (nx >= 0 && nx < size && ny >= 0 && ny < size) {
          const neighbor = grid[ny][nx];
          if (neighbor) {
            const key = makeEdgeKey(node.id, neighbor.id);
            const fromX = node.x + 0.5;
            const fromY = node.y + 0.5;
            const toX = neighbor.x + 0.5;
            const toY = neighbor.y + 0.5;
            const angle = Math.atan2(toY - fromY, toX - fromX);
            const length = Math.hypot(toX - fromX, toY - fromY);
            edges.push({
              from: node.id,
              to: neighbor.id,
              orientation: orientationFromDelta(dx, dy),
              key,
              x1: fromX,
              y1: fromY,
              x2: toX,
              y2: toY,
              cx: (fromX + toX) / 2,
              cy: (fromY + toY) / 2,
              angle,
              length,
              distance: Math.hypot(neighbor.x - node.x, neighbor.y - node.y)
            });
            break;
          }
          nx += dx;
          ny += dy;
        }
      });
    });

    const unique = new Map();
    edges.forEach(edge => {
      if (!unique.has(edge.key) || unique.get(edge.key).distance > edge.distance) {
        unique.set(edge.key, edge);
      }
    });
    return Array.from(unique.values());
  }

  function buildSpanningTree(nodes, edges, rng) {
    const shuffled = shuffle(edges.slice(), rng);
    const parent = nodes.map((_, index) => index);

    function find(index) {
      if (parent[index] !== index) {
        parent[index] = find(parent[index]);
      }
      return parent[index];
    }

    function union(a, b) {
      const rootA = find(a);
      const rootB = find(b);
      if (rootA === rootB) {
        return false;
      }
      parent[rootB] = rootA;
      return true;
    }

    const selected = [];
    shuffled.forEach(edge => {
      if (selected.length >= nodes.length - 1) {
        return;
      }
      const crosses = selected.some(existing => edgesCross(existing, edge));
      if (crosses) {
        return;
      }
      if (union(edge.from, edge.to)) {
        selected.push(edge);
      }
    });

    if (selected.length !== Math.max(0, nodes.length - 1)) {
      return null;
    }

    return selected;
  }

  function directionBetween(nodes, edge, sourceId) {
    const from = nodes[sourceId];
    const otherId = edge.from === sourceId ? edge.to : edge.from;
    const other = nodes[otherId];
    if (!from || !other) {
      return DIRECTIONS.EAST;
    }
    const dx = other.x - from.x;
    const dy = other.y - from.y;
    if (dx === 0 && dy < 0) {
      return DIRECTIONS.NORTH;
    }
    if (dx === 0 && dy > 0) {
      return DIRECTIONS.SOUTH;
    }
    if (dy === 0 && dx > 0) {
      return DIRECTIONS.EAST;
    }
    if (dy === 0 && dx < 0) {
      return DIRECTIONS.WEST;
    }
    if (dx > 0 && dy < 0) {
      return DIRECTIONS.NORTH_EAST;
    }
    if (dx > 0 && dy > 0) {
      return DIRECTIONS.SOUTH_EAST;
    }
    if (dx < 0 && dy > 0) {
      return DIRECTIONS.SOUTH_WEST;
    }
    return DIRECTIONS.NORTH_WEST;
  }

  function generatePuzzle(size, rng) {
    for (let attempt = 0; attempt < 20; attempt += 1) {
      const nodes = generateLayout(size, rng);
      const edges = buildEdges(nodes, size);
      const edgesByKey = new Map(edges.map(edge => [edge.key, edge]));
      if (!edges.length) {
        continue;
      }
      const tree = buildSpanningTree(nodes, edges, rng);
      if (!tree) {
        continue;
      }
      const nodeCounts = new Map();
      const activeEdges = new Map();
      tree.forEach(edge => {
        activeEdges.set(edge.key, 1);
        const dirFrom = directionBetween(nodes, edge, edge.from);
        const dirTo = directionBetween(nodes, edge, edge.to);
        addBridgeCount(nodeCounts, edge.from, dirFrom, 1);
        addBridgeCount(nodeCounts, edge.to, dirTo, 1);
      });

      const extras = shuffle(edges.filter(edge => !activeEdges.has(edge.key)), rng);
      extras.forEach(edge => {
        if (rng() > 0.4) {
          return;
        }
        const dirFrom = directionBetween(nodes, edge, edge.from);
        const dirTo = directionBetween(nodes, edge, edge.to);
        const crosses = Array.from(activeEdges.keys()).some(key => {
          if (key === edge.key) {
            return false;
          }
          const existing = edgesByKey.get(key);
          return existing ? edgesCross(existing, edge) : false;
        });
        if (crosses) {
          return;
        }
        activeEdges.set(edge.key, 1);
        addBridgeCount(nodeCounts, edge.from, dirFrom, 1);
        addBridgeCount(nodeCounts, edge.to, dirTo, 1);
      });

      nodes.forEach(node => {
        const entry = nodeCounts.get(node.id);
        node.required = entry ? entry.total : 0;
      });

      if (nodes.some(node => node.required <= 0)) {
        continue;
      }

      return { nodes, edges, solution: activeEdges };
    }
    return null;
  }

  function generatePuzzleWithSeed(seed, size) {
    const rng = seeded(seed);
    const puzzle = generatePuzzle(size, rng);
    if (!puzzle) {
      return null;
    }
    puzzle.seed = seed;
    puzzle.size = size;
    puzzle.rng = rng;
    return puzzle;
  }

  function recomputeBridgeTotals() {
    state.bridgeTotals = new Map();
    state.nodes.forEach(node => {
      state.bridgeTotals.set(node.id, {
        total: 0,
        north: 0,
        northEast: 0,
        east: 0,
        southEast: 0,
        south: 0,
        southWest: 0,
        west: 0,
        northWest: 0
      });
    });
    state.bridges.forEach((count, key) => {
      if (count <= 0) {
        return;
      }
      const parts = key.split('-');
      const a = Number.parseInt(parts[0], 10);
      const b = Number.parseInt(parts[1], 10);
      if (!Number.isFinite(a) || !Number.isFinite(b)) {
        return;
      }
      const edge = state.edgesByKey?.get(key);
      if (!edge) {
        return;
      }
      const dirA = directionBetween(state.nodes, edge, a);
      const dirB = directionBetween(state.nodes, edge, b);
      const entryA = state.bridgeTotals.get(a);
      const entryB = state.bridgeTotals.get(b);
      if (entryA) {
        entryA.total += count;
        entryA[dirA] += count;
      }
      if (entryB) {
        entryB.total += count;
        entryB[dirB] += count;
      }
    });
  }

  function computeTotalRequired(solution) {
    let total = 0;
    solution.forEach(count => {
      total += count;
    });
    return total;
  }

  function applyPuzzle(puzzle) {
    state.nodes = puzzle.nodes.map(node => ({ ...node }));
    state.edges = puzzle.edges;
    state.edgesByKey = new Map(puzzle.edges.map(edge => [edge.key, edge]));
    state.solution = new Map(puzzle.solution);
    state.totalRequired = computeTotalRequired(state.solution);
  }

  function renderBoard() {
    if (!elements.board) {
      return;
    }
    elements.board.innerHTML = '';
    elements.board.style.setProperty('--star-bridges-size', String(state.size));
    const totalNodes = state.nodes.length;
    const fragment = document.createDocumentFragment();

    const size = state.size;
    state.bridges.forEach((playerCount, key) => {
      if (playerCount <= 0) {
        return;
      }
      const edge = state.edgesByKey.get(key);
      if (!edge) {
        return;
      }
      const bridge = document.createElement('div');
      bridge.className = 'starbridges__bridge';
      bridge.dataset.key = key;
      bridge.dataset.orientation = edge.orientation;
      const left = (edge.cx / size) * 100;
      const top = (edge.cy / size) * 100;
      const length = (edge.length / size) * 100;
      bridge.style.left = `${left}%`;
      bridge.style.top = `${top}%`;
      bridge.style.width = `${length}%`;
      bridge.style.height = 'var(--starbridges-bridge-thickness)';
      bridge.style.transform = `translate(-50%, -50%) rotate(${edge.angle}rad)`;
      fragment.appendChild(bridge);
    });

    state.nodes.forEach(node => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'starbridges__node';
      button.dataset.id = String(node.id);
      button.textContent = String(node.required);
      button.style.setProperty('--node-x', String(node.x + 0.5));
      button.style.setProperty('--node-y', String(node.y + 0.5));
      const totals = state.bridgeTotals.get(node.id) || { total: 0 };
      if (totals.total === node.required) {
        button.classList.add('starbridges__node--satisfied');
      } else if (totals.total > node.required) {
        button.classList.add('starbridges__node--overloaded');
      }
      if (state.selectedNodeId === node.id) {
        button.classList.add('starbridges__node--selected');
      }
      const aria = translate(
        'index.sections.starBridges.node.aria',
        'Étoile ligne {{row}}, colonne {{column}} : {{current}}/{{required}} ponts',
        {
          row: node.y + 1,
          column: node.x + 1,
          current: totals.total,
          required: node.required
        }
      );
      button.setAttribute('aria-label', aria);
      button.addEventListener('click', handleNodeClick);
      fragment.appendChild(button);
    });

    elements.board.appendChild(fragment);
  }

  function updateStats() {
    if (elements.seedValue) {
      elements.seedValue.textContent = state.seed || '—';
    }
    if (elements.bridgesValue) {
      const placed = Array.from(state.bridges.values()).reduce((sum, count) => sum + count, 0);
      elements.bridgesValue.textContent = `${formatIntegerLocalized(placed)} / ${formatIntegerLocalized(state.totalRequired)}`;
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
        'index.sections.starBridges.win.share',
        'Copier seed'
      );
    }
  }

  function updateWinMessage() {
    if (!elements.winMessage) {
      return;
    }
    const placed = Array.from(state.bridges.values()).reduce((sum, count) => sum + count, 0);
    const message = translate(
      'index.sections.starBridges.win.message',
      'Constellation complétée en {{time}} avec {{moves}} actions ({{bridges}} ponts).',
      {
        time: formatTime(state.elapsedSeconds),
        moves: state.moves,
        bridges: formatIntegerLocalized(placed)
      }
    );
    elements.winMessage.textContent = message;
  }

  function showWin() {
    if (elements.win) {
      elements.win.hidden = false;
    }
  }

  function hideWin() {
    if (elements.win) {
      elements.win.hidden = true;
    }
  }

  function updateBoardMessage() {
    if (!elements.boardMessage) {
      return;
    }
    if (state.solved) {
      elements.boardMessage.textContent = translate(
        'index.sections.starBridges.messages.completed',
        'Constellation complétée !'
      );
      return;
    }
    if (state.selectedNodeId != null) {
      elements.boardMessage.textContent = translate(
        'index.sections.starBridges.messages.selectTarget',
        'Sélectionnez une étoile alignée ou diagonale pour prolonger le pont.'
      );
      return;
    }
    elements.boardMessage.textContent = translate(
      'index.sections.starBridges.messages.selectSource',
      'Touchez une étoile pour commencer un pont.'
    );
  }

  function bridgeWouldCross(edge, nextCount) {
    if (!edge || nextCount <= 0) {
      return false;
    }
    const edges = Array.from(state.bridges.entries())
      .filter(([key, count]) => count > 0 && key !== edge.key)
      .map(([key]) => state.edgesByKey.get(key))
      .filter(Boolean);
    return edges.some(existing => edgesCross(existing, edge));
  }

  function canAddBridge(edge) {
    if (!edge) {
      return false;
    }
    const current = state.bridges.get(edge.key) || 0;
    if (current >= 1) {
      return false;
    }
    if (bridgeWouldCross(edge, 1)) {
      return false;
    }
    return true;
  }

  function toggleBridge(aId, bId) {
    const key = makeEdgeKey(aId, bId);
    const edge = state.edgesByKey.get(key);
    if (!edge) {
      return;
    }
    const current = state.bridges.get(key) || 0;
    if (current >= 1) {
      state.bridges.delete(key);
    } else {
      if (!canAddBridge(edge)) {
        return;
      }
      state.bridges.set(key, 1);
    }
    state.moves += 1;
    recomputeBridgeTotals();
    renderBoard();
    updateStats();
    updateBoardMessage();
    scheduleAutosave();

    if (isSolved()) {
      state.solved = true;
      pauseTimer();
      awardCompletionTickets();
      updateBoardMessage();
      updateWinMessage();
      showWin();
      flushAutosave();
    }
  }

  function handleNodeClick(event) {
    if (state.solved) {
      return;
    }
    const button = event.currentTarget;
    const id = Number.parseInt(button.dataset.id, 10);
    if (!Number.isFinite(id)) {
      return;
    }
    if (state.selectedNodeId == null) {
      state.selectedNodeId = id;
      renderBoard();
      updateBoardMessage();
      return;
    }
    if (state.selectedNodeId === id) {
      state.selectedNodeId = null;
      renderBoard();
      updateBoardMessage();
      return;
    }
    const key = makeEdgeKey(state.selectedNodeId, id);
    if (!state.edgesByKey?.has(key)) {
      state.selectedNodeId = id;
      renderBoard();
      updateBoardMessage();
      return;
    }
    state.selectedNodeId = null;
    toggleBridge(Number.parseInt(key.split('-')[0], 10), Number.parseInt(key.split('-')[1], 10));
  }

  function isSolved() {
    if (!state.nodes.length) {
      return false;
    }
    if (!state.bridges.size) {
      return false;
    }
    const totals = new Map();
    state.nodes.forEach(node => {
      totals.set(node.id, 0);
    });
    let valid = true;
    state.bridges.forEach((count, key) => {
      if (!valid) {
        return;
      }
      const numeric = Math.max(0, Math.floor(Number(count) || 0));
      if (numeric <= 0) {
        valid = false;
        return;
      }
      if (!state.edgesByKey?.has(key)) {
        valid = false;
        return;
      }
      const [a, b] = key.split('-').map(part => Number.parseInt(part, 10));
      if (!Number.isFinite(a) || !Number.isFinite(b)) {
        valid = false;
        return;
      }
      totals.set(a, (totals.get(a) || 0) + numeric);
      totals.set(b, (totals.get(b) || 0) + numeric);
    });
    if (!valid) {
      return false;
    }
    for (let i = 0; i < state.nodes.length; i += 1) {
      const node = state.nodes[i];
      if ((totals.get(node.id) || 0) !== node.required) {
        return false;
      }
    }
    return isConnected();
  }

  function isConnected() {
    if (!state.nodes.length) {
      return false;
    }
    const adjacency = new Map();
    state.nodes.forEach(node => {
      adjacency.set(node.id, []);
    });
    state.bridges.forEach((count, key) => {
      if (count <= 0) {
        return;
      }
      const [a, b] = key.split('-').map(part => Number.parseInt(part, 10));
      adjacency.get(a).push(b);
      adjacency.get(b).push(a);
    });
    const visited = new Set();
    const stack = [state.nodes[0].id];
    while (stack.length) {
      const current = stack.pop();
      if (visited.has(current)) {
        continue;
      }
      visited.add(current);
      const neighbors = adjacency.get(current) || [];
      neighbors.forEach(neighbor => {
        if (!visited.has(neighbor)) {
          stack.push(neighbor);
        }
      });
    }
    return visited.size === state.nodes.length;
  }

  function getCompletionReward(size) {
    const range = COMPLETION_REWARD_BY_SIZE[size];
    if (!range || typeof range !== 'object') {
      return 0;
    }
    const min = Math.max(0, Math.floor(Number(range.min) || 0));
    const max = Math.max(min, Math.floor(Number(range.max) || 0));
    if (max <= 0) {
      return 0;
    }
    if (max === min) {
      return max;
    }
    const roll = Math.random();
    const span = max - min + 1;
    return min + Math.floor(roll * span);
  }

  function awardCompletionTickets() {
    if (state.rewardClaimed) {
      return;
    }
    if (!state.seedWasRandom) {
      state.rewardClaimed = true;
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
      console.warn('Star Bridges: unable to grant gacha tickets', error);
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
        'scripts.arcade.starBridges.rewardToast',
        'Ponts Stellaires : +{count} ticket{suffix} gacha !',
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
    const isRandomSeed = !trimmedSeed;
    const seed = isRandomSeed ? Date.now().toString(36) : trimmedSeed;
    const puzzle = generatePuzzleWithSeed(seed, size);
    if (!puzzle) {
      window.alert(
        translate(
          'index.sections.starBridges.messages.error',
          'Impossible de générer une constellation. Réessayez.'
        )
      );
      return;
    }
    withAutosaveSuppressed(() => {
      pauseTimer();
      applyPuzzle(puzzle);
      state.seed = seed;
      state.seedEntry = trimmedSeed;
      state.seedWasRandom = isRandomSeed;
      state.size = size;
      state.moves = 0;
      state.elapsedSeconds = 0;
      state.solved = false;
      state.rewardClaimed = false;
      state.bridges = new Map();
      state.bridgeTotals = new Map();
      state.selectedNodeId = null;
      state.shareResetTimer = null;
      if (elements.seedInput) {
        elements.seedInput.value = trimmedSeed;
      }
      if (elements.sizeSelect) {
        elements.sizeSelect.value = String(size);
      }
      hideWin();
      resetShareLabel();
      renderBoard();
      updateStats();
      updateBoardMessage();
    });
    resumeTimer();
    scheduleAutosave();
  }

  function handleNewPuzzleRequest() {
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
        'index.sections.starBridges.share.success',
        'Copié !'
      );
      state.shareResetTimer = window.setTimeout(() => {
        resetShareLabel();
      }, 1000);
    } catch (error) {
      const fallback = translate(
        'index.sections.starBridges.share.fallback',
        'Seed : {{seed}}',
        { seed }
      );
      window.alert(fallback);
    }
  }

  function handleHowRequest(event) {
    event.preventDefault();
    const message = translate(
      'index.sections.starBridges.how.message',
      'Reliez toutes les étoiles avec des ponts horizontaux ou verticaux. Les nombres indiquent combien de ponts doivent partir de chaque étoile. Les ponts ne peuvent ni se croiser ni dépasser deux par direction.'
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
    elements.newButton?.addEventListener('click', handleNewPuzzleRequest);
    elements.againButton?.addEventListener('click', handleNewPuzzleRequest);
    elements.nextButton?.addEventListener('click', handleNextSizeRequest);
    elements.shareButton?.addEventListener('click', handleShareRequest);
    elements.howLink?.addEventListener('click', handleHowRequest);
    elements.sizeSelect?.addEventListener('change', handleNewPuzzleRequest);
    elements.seedInput?.addEventListener('pointerdown', focusSeedInputForTouch);
    elements.seedInput?.addEventListener('touchstart', focusSeedInputForTouch, { passive: true });
  }

  function detachTimer() {
    pauseTimer();
  }

  function init() {
    attachEventListeners();
    const restored = restoreFromAutosave();
    if (!restored) {
      const initialSize = normalizeSize(elements.sizeSelect?.value);
      startNewGame(elements.seedInput?.value || '', initialSize);
    }
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

  window.starBridgesArcade = {
    onEnter() {
      resumeTimer();
      updateBoardMessage();
    },
    onLeave() {
      detachTimer();
      flushAutosave();
    }
  };
})();
