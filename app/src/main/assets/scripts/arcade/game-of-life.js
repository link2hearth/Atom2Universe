(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/game-of-life.json';
  const PATTERNS_PATH = 'resources/patterns/game-of-life.json';
  const RANDOM_SEED_STORAGE_KEY = 'atom2univers.arcade.gameOfLife.seed';
  const CUSTOM_PATTERNS_STORAGE_KEY = 'atom2univers.arcade.gameOfLife.customPatterns';
  const CUSTOM_PATTERN_COUNTER_STORAGE_KEY = 'atom2univers.arcade.gameOfLife.customPatternCounter';
  const GAME_ID = 'gameOfLife';
  const AUTOSAVE_VERSION = 1;
  const AUTOSAVE_DEBOUNCE_MS = 200;

  const DEFAULT_CELL_COLOR = 'rgba(255, 255, 255, 0.92)';

  const DEFAULT_CONFIG = Object.freeze({
    simulation: Object.freeze({
      tickMs: 400,
      fastForwardMultiplier: 4,
      historyLimit: 240,
      maxOffscreenDistance: 160
    }),
    viewport: Object.freeze({
      baseCellSize: 22,
      minCellSize: 6,
      maxCellSize: 64,
      gridFadeStart: 12,
      gridFadeEnd: 28,
      paddingCells: 2
    }),
    selection: Object.freeze({
      maxWidth: 200,
      maxHeight: 200,
      maxStored: 25
    }),
    random: Object.freeze({
      defaultWidth: 40,
      defaultHeight: 24,
      defaultDensity: 0.25,
      maxWidth: 200,
      maxHeight: 200
    })
  });

  const FALLBACK_PATTERNS = Object.freeze([
    Object.freeze({
      id: 'glider',
      nameKey: 'index.sections.gameOfLife.patterns.glider',
      descriptionKey: 'index.sections.gameOfLife.patterns.gliderDescription',
      anchor: Object.freeze({ x: 1, y: 1 }),
      cells: Object.freeze([[0, 1], [1, 2], [2, 0], [2, 1], [2, 2]])
    }),
    Object.freeze({
      id: 'lwss',
      nameKey: 'index.sections.gameOfLife.patterns.lightweightSpaceship',
      descriptionKey: 'index.sections.gameOfLife.patterns.lightweightSpaceshipDescription',
      anchor: Object.freeze({ x: 2, y: 1 }),
      cells: Object.freeze([
        [0, 1], [0, 4],
        [1, 0],
        [2, 0], [2, 4],
        [3, 0], [3, 1], [3, 2], [3, 3]
      ])
    }),
    Object.freeze({
      id: 'pulsar',
      nameKey: 'index.sections.gameOfLife.patterns.pulsar',
      descriptionKey: 'index.sections.gameOfLife.patterns.pulsarDescription',
      anchor: Object.freeze({ x: 6, y: 6 }),
      cells: Object.freeze([
        [2, 4], [2, 5], [2, 6], [2, 10], [2, 11], [2, 12],
        [4, 2], [5, 2], [6, 2], [4, 7], [5, 7], [6, 7], [4, 9], [5, 9], [6, 9], [4, 14], [5, 14], [6, 14],
        [7, 4], [7, 5], [7, 6], [7, 10], [7, 11], [7, 12],
        [9, 4], [9, 5], [9, 6], [9, 10], [9, 11], [9, 12],
        [10, 2], [11, 2], [12, 2], [10, 7], [11, 7], [12, 7], [10, 9], [11, 9], [12, 9], [10, 14], [11, 14], [12, 14],
        [14, 4], [14, 5], [14, 6], [14, 10], [14, 11], [14, 12]
      ])
    })
  ]);

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window.translateOrDefault === 'function') {
      return window.translateOrDefault(key, fallback, params);
    }
    return fallback;
  }

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function clampInteger(value, min, max, fallback) {
    const numeric = Math.round(Number(value));
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    return clamp(numeric, min, max);
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

  function toNumber(value, fallback) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : fallback;
  }

  function mergeConfig(base, override) {
    const simulation = Object.assign({}, base.simulation);
    if (override?.simulation) {
      const source = override.simulation;
      if (Number.isFinite(source.tickMs) && source.tickMs > 10) {
        simulation.tickMs = source.tickMs;
      }
      if (Number.isFinite(source.fastForwardMultiplier) && source.fastForwardMultiplier >= 1) {
        simulation.fastForwardMultiplier = source.fastForwardMultiplier;
      }
      if (Number.isFinite(source.historyLimit) && source.historyLimit >= 10) {
        simulation.historyLimit = source.historyLimit;
      }
      if (Number.isFinite(source.maxOffscreenDistance) && source.maxOffscreenDistance >= 0) {
        simulation.maxOffscreenDistance = source.maxOffscreenDistance;
      }
    }

    const viewport = Object.assign({}, base.viewport);
    if (override?.viewport) {
      const source = override.viewport;
      if (Number.isFinite(source.baseCellSize) && source.baseCellSize > 2) {
        viewport.baseCellSize = source.baseCellSize;
      }
      if (Number.isFinite(source.minCellSize) && source.minCellSize > 1) {
        viewport.minCellSize = source.minCellSize;
      }
      if (Number.isFinite(source.maxCellSize) && source.maxCellSize >= viewport.minCellSize) {
        viewport.maxCellSize = source.maxCellSize;
      }
      if (Number.isFinite(source.gridFadeStart) && source.gridFadeStart > 0) {
        viewport.gridFadeStart = source.gridFadeStart;
      }
      if (Number.isFinite(source.gridFadeEnd) && source.gridFadeEnd >= viewport.gridFadeStart) {
        viewport.gridFadeEnd = source.gridFadeEnd;
      }
      if (Number.isFinite(source.paddingCells) && source.paddingCells >= 0) {
        viewport.paddingCells = source.paddingCells;
      }
    }

    const selection = Object.assign({}, base.selection);
    if (override?.selection) {
      const source = override.selection;
      if (Number.isFinite(source.maxWidth) && source.maxWidth >= 1) {
        selection.maxWidth = source.maxWidth;
      }
      if (Number.isFinite(source.maxHeight) && source.maxHeight >= 1) {
        selection.maxHeight = source.maxHeight;
      }
      if (Number.isFinite(source.maxStored) && source.maxStored >= 0) {
        selection.maxStored = Math.floor(source.maxStored);
      }
    }

    const random = Object.assign({}, base.random);
    if (override?.random) {
      const source = override.random;
      if (Number.isFinite(source.defaultWidth) && source.defaultWidth > 0) {
        random.defaultWidth = source.defaultWidth;
      }
      if (Number.isFinite(source.defaultHeight) && source.defaultHeight > 0) {
        random.defaultHeight = source.defaultHeight;
      }
      if (Number.isFinite(source.defaultDensity) && source.defaultDensity >= 0 && source.defaultDensity <= 1) {
        random.defaultDensity = source.defaultDensity;
      }
      if (Number.isFinite(source.maxWidth) && source.maxWidth >= random.defaultWidth) {
        random.maxWidth = source.maxWidth;
      }
      if (Number.isFinite(source.maxHeight) && source.maxHeight >= random.defaultHeight) {
        random.maxHeight = source.maxHeight;
      }
    }

    return Object.freeze({ simulation, viewport, selection, random });
  }

  async function fetchJson(path) {
    try {
      const response = await fetch(path, { cache: 'no-store' });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return await response.json();
    } catch (error) {
      return null;
    }
  }

  function serializeCell(x, y) {
    return `${x},${y}`;
  }

  function deserializeCell(key, target) {
    const output = target || { x: 0, y: 0 };
    const commaIndex = typeof key === 'string' ? key.indexOf(',') : -1;
    if (commaIndex === -1) {
      output.x = 0;
      output.y = 0;
      return output;
    }
    output.x = Number.parseInt(key.slice(0, commaIndex), 10) || 0;
    output.y = Number.parseInt(key.slice(commaIndex + 1), 10) || 0;
    return output;
  }

  const NEIGHBOR_OFFSETS = Object.freeze([
    [-1, -1], [-1, 0], [-1, 1],
    [0, -1], /* skip (0,0) */ [0, 1],
    [1, -1], [1, 0], [1, 1]
  ]);

  function computeNextGenerationFromCells(cells) {
    const current = new Set();
    const neighborCounts = new Map();
    cells.forEach(([x, y]) => {
      const key = serializeCell(x, y);
      if (current.has(key)) {
        return;
      }
      current.add(key);
      NEIGHBOR_OFFSETS.forEach(([dx, dy]) => {
        const neighborKey = serializeCell(x + dx, y + dy);
        neighborCounts.set(neighborKey, (neighborCounts.get(neighborKey) || 0) + 1);
      });
    });

    const next = new Set();
    neighborCounts.forEach((count, key) => {
      const alive = current.has(key);
      if ((alive && (count === 2 || count === 3)) || (!alive && count === 3)) {
        next.add(key);
      }
    });
    return { current, next };
  }

  function isStillLifePattern(cells) {
    if (!cells.length) {
      return false;
    }
    const { current, next } = computeNextGenerationFromCells(cells);
    if (current.size !== next.size) {
      return false;
    }
    for (const key of current) {
      if (!next.has(key)) {
        return false;
      }
    }
    return true;
  }

  function loadSeed() {
    try {
      const raw = window.localStorage?.getItem(RANDOM_SEED_STORAGE_KEY);
      if (!raw) {
        return Math.floor(Math.random() * 0xffffffff);
      }
      const parsed = Number.parseInt(raw, 10);
      if (Number.isFinite(parsed) && parsed >= 0) {
        return parsed >>> 0;
      }
    } catch (error) {
      // ignore storage errors
    }
    return Math.floor(Math.random() * 0xffffffff);
  }

  function saveSeed(seed) {
    try {
      window.localStorage?.setItem(RANDOM_SEED_STORAGE_KEY, String(seed >>> 0));
    } catch (error) {
      // ignore storage errors
    }
  }

  function createMulberry32(seed) {
    let state = seed >>> 0;
    return function rng() {
      state += 0x6d2b79f5;
      let t = state;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function advanceSeed(seed) {
    return (seed + 0x9e3779b9) >>> 0;
  }

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  class GameOfLifeArcade {
    constructor({
      root,
      canvas,
      playPauseButton,
      stepButton,
      rewindButton,
      fastForwardButton,
      clearButton,
      speedSlider,
      zoomSlider,
      generationValue,
      populationValue,
      randomWidthInput,
      randomHeightInput,
      randomDensitySlider,
      randomDensityValue,
      randomReplaceButton,
      randomAddButton,
      patternSelect,
      patternDescription,
      patternEmpty,
      selectionButton,
      selectionPanel,
      selectionForm,
      selectionNameInput,
      selectionCancelButton,
      menu,
      menuToggle,
      menuHeader
    }) {
      this.root = root;
      this.canvas = canvas;
      this.ctx = canvas?.getContext('2d') || null;
      this.playPauseButton = playPauseButton;
      this.stepButton = stepButton;
      this.rewindButton = rewindButton;
      this.fastForwardButton = fastForwardButton;
      this.clearButton = clearButton;
      this.speedSlider = speedSlider;
      this.zoomSlider = zoomSlider;
      this.generationValue = generationValue;
      this.populationValue = populationValue;
      this.randomWidthInput = randomWidthInput;
      this.randomHeightInput = randomHeightInput;
      this.randomDensitySlider = randomDensitySlider;
      this.randomDensityValue = randomDensityValue;
      this.randomReplaceButton = randomReplaceButton;
      this.randomAddButton = randomAddButton;
      this.patternSelect = patternSelect;
      this.patternDescription = patternDescription;
      this.patternEmpty = patternEmpty;
      this.selectionButton = selectionButton;
      this.selectionPanel = selectionPanel;
      this.selectionForm = selectionForm;
      this.selectionNameInput = selectionNameInput;
      this.selectionCancelButton = selectionCancelButton;
      this.menu = menu;
      this.menuToggle = menuToggle;
      this.menuHeader = menuHeader;

      this.autosaveTimer = null;
      this.autosaveSuppressed = false;

      this.config = DEFAULT_CONFIG;
      this.builtinPatterns = [];
      this.customPatterns = [];
      this.selectedPatternId = null;
      this.customPatternCounter = 1;

      const rawDpr = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
        ? window.devicePixelRatio
        : 1;
      const configuredMaxDpr = typeof globalThis !== 'undefined' && typeof globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO === 'number'
        ? Math.max(1, globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO)
        : 1;
      this.dpr = Math.min(rawDpr, configuredMaxDpr);
      this.viewport = {
        originX: -20,
        originY: -15,
        cellSize: DEFAULT_CONFIG.viewport.baseCellSize,
        minCellSize: DEFAULT_CONFIG.viewport.minCellSize,
        maxCellSize: DEFAULT_CONFIG.viewport.maxCellSize,
        gridFadeStart: DEFAULT_CONFIG.viewport.gridFadeStart,
        gridFadeEnd: DEFAULT_CONFIG.viewport.gridFadeEnd,
        paddingCells: DEFAULT_CONFIG.viewport.paddingCells
      };

      this.state = {
        aliveCells: new Set(),
        generation: 0,
        running: false,
        fastForward: false,
        history: [],
        speedMs: DEFAULT_CONFIG.simulation.tickMs,
        accumulator: 0,
        lastFrameTime: 0,
        active: false
      };

      this.dragState = {
        active: false,
        mode: 'toggle',
        lastCellKey: null,
        initialCellKey: null,
        initialCellAlive: false
      };
      this.panState = {
        active: false,
        lastX: 0,
        lastY: 0
      };

      this.touchState = {
        pointers: new Map(),
        isGesture: false,
        initialDistance: 0,
        initialCellSize: this.viewport.cellSize,
        lastMidpoint: null
      };

      this.spacePressed = false;
      this.pointerId = null;
      this.randomSeed = loadSeed();
      this.resizeObserver = null;
      this.frameId = null;
      this.boundHandleFrame = this.handleFrame.bind(this);
      this.boundHandleVisibility = this.handleVisibilityChange.bind(this);
      this.boundHandleMenuPointerMove = this.handleMenuPointerMove.bind(this);
      this.boundHandleMenuPointerUp = this.handleMenuPointerUp.bind(this);
      this.themeObserver = null;
      this.needsRedraw = false;
      this.canvasRect = { width: 0, height: 0 };
      this.cellColor = DEFAULT_CELL_COLOR;
      this.boundHandleWindowResize = null;

      this.selectionState = {
        enabled: false,
        active: false,
        pointerId: null,
        startCell: null,
        currentCell: null,
        message: null
      };

      this.pendingSelection = null;

      this.speedSliderRange = { min: 50, max: 600 };

      this.requestRedraw();

      this.menuDragState = {
        active: false,
        pointerId: null,
        offsetX: 0,
        offsetY: 0
      };

      this.stepCandidates = new Map();
      this.stepCellBuffer = { x: 0, y: 0 };
      this.renderCellBuffer = { x: 0, y: 0 };
      this.boundsCellBuffer = { x: 0, y: 0 };
      this.selectionCellBuffer = { x: 0, y: 0 };

      this.observeThemeChanges();
    }

    ensureAnimationLoop() {
      if (!this.frameId) {
        this.frameId = requestAnimationFrame(this.boundHandleFrame);
      }
    }

    requestRedraw() {
      this.needsRedraw = true;
      this.ensureAnimationLoop();
    }

    async init() {
      const [configJson, patternsJson] = await Promise.all([
        fetchJson(CONFIG_PATH),
        fetchJson(PATTERNS_PATH)
      ]);

      this.config = mergeConfig(DEFAULT_CONFIG, configJson || {});
      const rawPatterns = Array.isArray(patternsJson?.patterns)
        ? patternsJson.patterns
        : FALLBACK_PATTERNS;
      this.builtinPatterns = rawPatterns
        .map(pattern => this.normalizePattern(pattern))
        .filter(Boolean)
        .map(pattern => Object.freeze(Object.assign({
          origin: 'builtin',
          nameFallback: pattern.id.replace(/([A-Z])/g, ' $1').trim(),
          nameParams: null,
          descriptionParams: null,
          descriptionFallback: '',
          customName: null,
          created: null
        }, pattern)));
      this.restoreCustomPatterns();

      this.viewport.cellSize = clamp(
        this.config.viewport.baseCellSize,
        this.config.viewport.minCellSize,
        this.config.viewport.maxCellSize
      );
      this.viewport.minCellSize = this.config.viewport.minCellSize;
      this.viewport.maxCellSize = this.config.viewport.maxCellSize;
      this.viewport.gridFadeStart = this.config.viewport.gridFadeStart;
      this.viewport.gridFadeEnd = this.config.viewport.gridFadeEnd;
      this.viewport.paddingCells = this.config.viewport.paddingCells;

      this.state.speedMs = clamp(
        this.config.simulation.tickMs,
        50,
        2000
      );

      this.setupUI();
      this.attachEvents();
      this.renderPatterns();
      this.updateRandomInputs();
      this.restoreFromAutosave();
      this.updateUI();
      this.observeResize();
      this.state.active = false;
      this.requestRedraw();
      this.render();
      this.ensureAnimationLoop();
      document.addEventListener('visibilitychange', this.boundHandleVisibility);
    }

    buildAutosavePayload() {
      const aliveCells = [];
      const cellBuffer = this.autosaveCellBuffer || { x: 0, y: 0 };
      this.autosaveCellBuffer = cellBuffer;
      if (this.state.aliveCells && typeof this.state.aliveCells.forEach === 'function') {
        this.state.aliveCells.forEach(key => {
          const cell = deserializeCell(key, cellBuffer);
          const x = Number.isFinite(cell.x) ? Math.round(cell.x) : null;
          const y = Number.isFinite(cell.y) ? Math.round(cell.y) : null;
          if (x == null || y == null) {
            return;
          }
          aliveCells.push([x, y]);
        });
      }

      const viewport = {
        originX: Number.isFinite(this.viewport.originX) ? Number(this.viewport.originX) : 0,
        originY: Number.isFinite(this.viewport.originY) ? Number(this.viewport.originY) : 0,
        cellSize: clamp(
          Number.isFinite(this.viewport.cellSize) ? Number(this.viewport.cellSize) : DEFAULT_CONFIG.viewport.baseCellSize,
          this.viewport.minCellSize,
          this.viewport.maxCellSize
        )
      };

      const payload = {
        version: AUTOSAVE_VERSION,
        generation: clampInteger(this.state.generation, 0, 1000000, 0),
        running: Boolean(this.state.running),
        fastForward: Boolean(this.state.fastForward),
        speedMs: clamp(Number(this.state.speedMs) || DEFAULT_CONFIG.simulation.tickMs, 30, 2000),
        selectionEnabled: Boolean(this.selectionState.enabled),
        selectedPatternId: typeof this.selectedPatternId === 'string' ? this.selectedPatternId : null,
        viewport,
        aliveCells
      };

      if (this.menu) {
        const expanded = this.menu.dataset.expanded !== 'false';
        const left = Number.parseFloat(this.menu.style.left);
        const top = Number.parseFloat(this.menu.style.top);
        const menuState = { expanded };
        if (Number.isFinite(left) && Number.isFinite(top)) {
          menuState.left = left;
          menuState.top = top;
        }
        payload.menu = menuState;
      }

      return payload;
    }

    persistAutosaveNow() {
      const autosave = getAutosaveApi();
      if (!autosave) {
        return;
      }
      const payload = this.buildAutosavePayload();
      if (!payload) {
        if (typeof autosave.clear === 'function') {
          try {
            autosave.clear(GAME_ID);
          } catch (error) {
            // ignore autosave clearance errors
          }
        } else if (typeof autosave.set === 'function') {
          try {
            autosave.set(GAME_ID, null);
          } catch (error) {
            // ignore autosave persistence errors
          }
        }
        return;
      }
      try {
        autosave.set(GAME_ID, payload);
      } catch (error) {
        // ignore autosave persistence errors
      }
    }

    scheduleAutosave() {
      if (this.autosaveSuppressed) {
        return;
      }
      if (typeof window === 'undefined') {
        return;
      }
      if (this.autosaveTimer != null) {
        window.clearTimeout(this.autosaveTimer);
      }
      this.autosaveTimer = window.setTimeout(() => {
        this.autosaveTimer = null;
        this.persistAutosaveNow();
      }, AUTOSAVE_DEBOUNCE_MS);
    }

    flushAutosave() {
      if (typeof window !== 'undefined' && this.autosaveTimer != null) {
        window.clearTimeout(this.autosaveTimer);
        this.autosaveTimer = null;
      }
      this.persistAutosaveNow();
    }

    restoreFromAutosave() {
      const autosave = getAutosaveApi();
      if (!autosave) {
        return;
      }
      let payload = null;
      try {
        payload = autosave.get(GAME_ID);
      } catch (error) {
        return;
      }
      if (!payload || typeof payload !== 'object') {
        return;
      }
      const version = Number(payload.version) || 0;
      if (version !== AUTOSAVE_VERSION) {
        return;
      }

      this.autosaveSuppressed = true;
      try {
        const cells = Array.isArray(payload.aliveCells) ? payload.aliveCells : [];
        const nextCells = new Set();
        const cellBuffer = this.autosaveCellBuffer || { x: 0, y: 0 };
        this.autosaveCellBuffer = cellBuffer;
        cells.forEach(entry => {
          if (!entry) {
            return;
          }
          let rawX = null;
          let rawY = null;
          if (Array.isArray(entry)) {
            rawX = entry[0];
            rawY = entry[1];
          } else if (typeof entry === 'object') {
            rawX = entry.x;
            rawY = entry.y;
          }
          const x = Number(rawX);
          const y = Number(rawY);
          if (!Number.isFinite(x) || !Number.isFinite(y)) {
            return;
          }
          nextCells.add(serializeCell(Math.round(x), Math.round(y)));
        });
        this.state.aliveCells = nextCells;
        this.state.generation = clampInteger(payload.generation, 0, 1000000, 0);
        this.state.running = Boolean(payload.running);
        this.state.fastForward = Boolean(payload.fastForward);
        this.state.history = [];
        this.state.accumulator = 0;
        if (typeof performance === 'object' && typeof performance.now === 'function') {
          this.state.lastFrameTime = performance.now();
        } else {
          this.state.lastFrameTime = 0;
        }

        const speedCandidate = Number(payload.speedMs);
        if (Number.isFinite(speedCandidate)) {
          this.setSpeed(speedCandidate);
        }

        const viewport = payload.viewport && typeof payload.viewport === 'object' ? payload.viewport : null;
        if (viewport) {
          const originX = Number(viewport.originX);
          const originY = Number(viewport.originY);
          const cellSize = Number(viewport.cellSize);
          if (Number.isFinite(originX)) {
            this.viewport.originX = originX;
          }
          if (Number.isFinite(originY)) {
            this.viewport.originY = originY;
          }
          if (Number.isFinite(cellSize)) {
            const clampedSize = clamp(cellSize, this.viewport.minCellSize, this.viewport.maxCellSize);
            this.viewport.cellSize = clampedSize;
            if (this.zoomSlider) {
              this.zoomSlider.value = String(Math.round(clampedSize));
            }
          }
        }

        const selectionEnabled = Boolean(payload.selectionEnabled);
        this.setSelectionEnabled(selectionEnabled);

        const selectedPatternId = typeof payload.selectedPatternId === 'string' ? payload.selectedPatternId : null;
        this.selectedPatternId = selectedPatternId;
        if (this.patternSelect) {
          this.patternSelect.value = selectedPatternId || '';
        }

        const menuState = payload.menu && typeof payload.menu === 'object' ? payload.menu : null;
        if (this.menu && menuState) {
          const expanded = menuState.expanded !== false;
          this.menu.dataset.expanded = expanded ? 'true' : 'false';
          const left = Number(menuState.left);
          const top = Number(menuState.top);
          if (Number.isFinite(left) && Number.isFinite(top)) {
            this.menu.style.left = `${left}px`;
            this.menu.style.top = `${top}px`;
            this.menu.style.right = 'auto';
            this.menu.style.bottom = 'auto';
            this.menu.style.transform = 'none';
          }
        }

        this.updatePatternDescription();
        this.updateMenuToggleLabel();
        this.updateUI();
        this.requestRedraw();
      } finally {
        this.autosaveSuppressed = false;
      }
      this.scheduleAutosave();
    }

    destroy() {
      this.flushAutosave();
      this.state.active = false;
      if (this.frameId) {
        cancelAnimationFrame(this.frameId);
        this.frameId = null;
      }
      if (this.resizeObserver) {
        this.resizeObserver.disconnect();
        this.resizeObserver = null;
      }
      if (this.boundHandleWindowResize) {
        window.removeEventListener('resize', this.boundHandleWindowResize);
        this.boundHandleWindowResize = null;
      }
      document.removeEventListener('visibilitychange', this.boundHandleVisibility);
      this.resetInputStates();
      this.requestScrollUnlock();
      if (this.themeObserver && typeof this.themeObserver.disconnect === 'function') {
        this.themeObserver.disconnect();
      }
      this.themeObserver = null;
    }

    onEnter() {
      this.state.active = true;
      this.state.accumulator = 0;
      this.state.lastFrameTime = performance.now();
      this.ensureAnimationLoop();
    }

    onLeave() {
      this.state.active = false;
      this.pause();
      this.setSelectionEnabled(false);
      this.resetInputStates();
      this.requestScrollUnlock();
      this.flushAutosave();
    }

    resetInputStates() {
      this.pointerId = null;
      this.panState.active = false;
      this.panState.lastX = 0;
      this.panState.lastY = 0;
      this.dragState.active = false;
      this.dragState.lastCellKey = null;
      this.dragState.initialCellKey = null;
      this.dragState.initialCellAlive = false;
      this.touchState.isGesture = false;
      this.touchState.initialDistance = 0;
      this.touchState.initialCellSize = this.viewport.cellSize;
      this.touchState.lastMidpoint = null;
      if (this.touchState.pointers && typeof this.touchState.pointers.clear === 'function') {
        this.touchState.pointers.clear();
      }
    }

    requestScrollUnlock() {
      if (typeof window === 'undefined') {
        return;
      }
      let eventDispatched = false;
      if (typeof window.dispatchEvent === 'function') {
        let event = null;
        if (typeof window.CustomEvent === 'function') {
          event = new window.CustomEvent('atom2univers:scroll-reset');
        } else if (typeof window.Event === 'function') {
          event = new window.Event('atom2univers:scroll-reset');
        }
        if (event) {
          try {
            window.dispatchEvent(event);
            eventDispatched = true;
          } catch (error) {
            eventDispatched = false;
          }
        }
      }

      if (typeof globalThis !== 'undefined' && typeof globalThis.forceUnlockScrollSafe === 'function') {
        globalThis.forceUnlockScrollSafe();
        return;
      }

      if (!eventDispatched) {
        const body = typeof document !== 'undefined' ? document.body : null;
        if (body) {
          body.style.removeProperty('touch-action');
          body.style.removeProperty('overscroll-behavior');
          body.classList.remove('touch-scroll-lock');
          body.classList.remove('touch-scroll-force');
        }
      }
    }

    setupUI() {
      if (this.speedSlider) {
        const rawMin = toNumber(this.speedSlider.min, this.speedSliderRange.min);
        const rawMax = toNumber(this.speedSlider.max, this.speedSliderRange.max);
        if (Number.isFinite(rawMin) && Number.isFinite(rawMax)) {
          this.speedSliderRange.min = Math.min(rawMin, rawMax);
          this.speedSliderRange.max = Math.max(rawMin, rawMax);
        }
        this.speedSlider.value = this.speedToSliderValue(this.state.speedMs);
      }
      if (this.zoomSlider) {
        this.zoomSlider.min = String(this.viewport.minCellSize);
        this.zoomSlider.max = String(this.viewport.maxCellSize);
        this.zoomSlider.value = String(Math.round(this.viewport.cellSize));
      }
      if (this.randomDensitySlider) {
        this.randomDensitySlider.value = String(Math.round(this.config.random.defaultDensity * 100));
        if (this.randomDensityValue) {
          this.randomDensityValue.textContent = `${Math.round(this.config.random.defaultDensity * 100)}%`;
        }
      }
      if (this.randomWidthInput) {
        this.randomWidthInput.value = String(Math.round(this.config.random.defaultWidth));
        this.randomWidthInput.max = String(Math.round(this.config.random.maxWidth));
      }
      if (this.randomHeightInput) {
        this.randomHeightInput.value = String(Math.round(this.config.random.defaultHeight));
        this.randomHeightInput.max = String(Math.round(this.config.random.maxHeight));
      }
      this.resetSelectionForm();
      this.updateMenuToggleLabel();
    }

    attachEvents() {
      if (this.playPauseButton) {
        this.playPauseButton.addEventListener('click', () => {
          if (this.state.running) {
            this.pause();
          } else {
            this.play();
          }
        });
      }
      if (this.stepButton) {
        this.stepButton.addEventListener('click', () => {
          this.step();
        });
      }
      if (this.rewindButton) {
        this.rewindButton.addEventListener('click', () => {
          this.rewind();
        });
      }
      if (this.fastForwardButton) {
        this.fastForwardButton.addEventListener('click', () => {
          this.toggleFastForward();
        });
      }
      if (this.clearButton) {
        this.clearButton.addEventListener('click', () => {
          this.reset();
        });
      }
      if (this.speedSlider) {
        this.speedSlider.addEventListener('input', () => {
          const value = this.sliderValueToSpeed(this.speedSlider.value);
          this.setSpeed(value);
        });
      }
      if (this.zoomSlider) {
        this.zoomSlider.addEventListener('input', () => {
          const value = clamp(Number(this.zoomSlider.value) || this.viewport.cellSize, this.viewport.minCellSize, this.viewport.maxCellSize);
          this.setZoom(value);
        });
      }
      if (this.selectionButton) {
        this.selectionButton.addEventListener('click', () => {
          const nextState = !this.selectionState.enabled;
          this.setSelectionEnabled(nextState);
          this.updateUI();
        });
      }
      if (this.selectionForm) {
        this.selectionForm.addEventListener('submit', event => {
          event.preventDefault();
          this.handleSelectionFormSubmit();
        });
      }
      if (this.selectionCancelButton) {
        this.selectionCancelButton.addEventListener('click', () => {
          this.cancelPendingSelection(false);
        });
      }
      if (this.randomDensitySlider) {
        this.randomDensitySlider.addEventListener('input', () => {
          const value = clamp(Number(this.randomDensitySlider.value) || 0, 0, 100);
          if (this.randomDensityValue) {
            this.randomDensityValue.textContent = `${value}%`;
          }
        });
      }
      if (this.randomReplaceButton) {
        this.randomReplaceButton.addEventListener('click', () => {
          this.generateRandomPattern('replace');
        });
      }
      if (this.randomAddButton) {
        this.randomAddButton.addEventListener('click', () => {
          this.generateRandomPattern('add');
        });
      }
      if (this.patternSelect) {
        this.patternSelect.addEventListener('change', () => {
          const value = this.patternSelect.value;
          this.selectedPatternId = value || null;
          this.updatePatternDescription();
          this.scheduleAutosave();
        });
      }

      if (this.menuToggle) {
        this.menuToggle.addEventListener('click', () => {
          this.toggleMenu();
        });
      }

      if (this.menuHeader) {
        this.menuHeader.addEventListener('pointerdown', event => {
          this.startMenuDrag(event);
        });
        this.menuHeader.addEventListener('pointermove', this.boundHandleMenuPointerMove);
        this.menuHeader.addEventListener('pointerup', this.boundHandleMenuPointerUp);
        this.menuHeader.addEventListener('pointercancel', this.boundHandleMenuPointerUp);
        this.menuHeader.addEventListener('lostpointercapture', this.boundHandleMenuPointerUp);
      }

      if (this.canvas) {
        this.canvas.addEventListener('pointerdown', event => this.handlePointerDown(event));
        this.canvas.addEventListener('pointermove', event => this.handlePointerMove(event));
        window.addEventListener('pointerup', event => this.handlePointerUp(event));
        window.addEventListener('pointercancel', event => this.handlePointerUp(event));
        this.canvas.addEventListener('lostpointercapture', event => this.handlePointerUp(event));
        this.canvas.addEventListener('wheel', event => this.handleWheel(event), { passive: false });
      }

      window.addEventListener('keydown', event => this.handleKeyDown(event));
      window.addEventListener('keyup', event => this.handleKeyUp(event));
    }

    observeResize() {
      if (!this.canvas) {
        return;
      }
      const update = () => {
        this.updateCanvasSize(window.innerWidth, window.innerHeight);
        this.clampMenuPosition();
      };
      this.boundHandleWindowResize = update;
      window.addEventListener('resize', this.boundHandleWindowResize);
      update();
    }

    handleVisibilityChange() {
      if (document.hidden) {
        this.pause();
        this.flushAutosave();
      }
    }

    handleFrame(timestamp) {
      if (!this.state.active) {
        this.frameId = null;
        return;
      }
      if (!this.state.lastFrameTime) {
        this.state.lastFrameTime = timestamp;
      }
      const delta = timestamp - this.state.lastFrameTime;
      this.state.lastFrameTime = timestamp;

      if (this.state.running) {
        const multiplier = this.state.fastForward
          ? Math.max(1, this.config.simulation.fastForwardMultiplier)
          : 1;
        const targetInterval = Math.max(16, this.state.speedMs / multiplier);
        this.state.accumulator += delta;
        let iterations = 0;
        while (this.state.accumulator >= targetInterval && iterations < 12) {
          this.state.accumulator -= targetInterval;
          this.stepInternal();
          iterations += 1;
        }
      } else {
        this.state.accumulator = 0;
      }

      if (this.needsRedraw) {
        this.render();
      }

      if (this.state.running || this.needsRedraw) {
        this.frameId = requestAnimationFrame(this.boundHandleFrame);
      } else {
        this.frameId = null;
      }
    }

    normalizePattern(pattern) {
      if (!pattern || typeof pattern !== 'object') {
        return null;
      }
      const id = typeof pattern.id === 'string' && pattern.id.trim()
        ? pattern.id.trim()
        : null;
      if (!id) {
        return null;
      }
      const anchorX = Number(pattern.anchor?.x) || 0;
      const anchorY = Number(pattern.anchor?.y) || 0;
      const rawCells = Array.isArray(pattern.cells) ? pattern.cells : [];
      const uniqueKeys = new Set();
      const normalizedCells = [];
      rawCells.forEach(cell => {
        if (!Array.isArray(cell) || cell.length < 2) {
          return;
        }
        const x = Number(cell[0]) || 0;
        const y = Number(cell[1]) || 0;
        const key = serializeCell(x, y);
        if (uniqueKeys.has(key)) {
          return;
        }
        uniqueKeys.add(key);
        normalizedCells.push([x, y]);
      });
      if (!normalizedCells.length || isStillLifePattern(normalizedCells)) {
        return null;
      }
      const absoluteCells = normalizedCells.map(([x, y]) => Object.freeze([x, y]));
      const relativeCells = normalizedCells.map(([x, y]) => Object.freeze([x - anchorX, y - anchorY]));
      return {
        id,
        nameKey: typeof pattern.nameKey === 'string' ? pattern.nameKey : null,
        descriptionKey: typeof pattern.descriptionKey === 'string' ? pattern.descriptionKey : null,
        anchor: Object.freeze({ x: anchorX, y: anchorY }),
        cells: Object.freeze(absoluteCells),
        relativeCells: Object.freeze(relativeCells)
      };
    }

    restoreCustomPatterns() {
      const selectionConfig = this.config.selection || DEFAULT_CONFIG.selection;
      const rawMaxWidth = Number(selectionConfig.maxWidth ?? DEFAULT_CONFIG.selection.maxWidth);
      const rawMaxHeight = Number(selectionConfig.maxHeight ?? DEFAULT_CONFIG.selection.maxHeight);
      const maxWidth = Number.isFinite(rawMaxWidth) && rawMaxWidth > 0
        ? Math.floor(rawMaxWidth)
        : 0;
      const maxHeight = Number.isFinite(rawMaxHeight) && rawMaxHeight > 0
        ? Math.floor(rawMaxHeight)
        : 0;
      const maxStored = Math.max(0, Math.floor(selectionConfig.maxStored ?? DEFAULT_CONFIG.selection.maxStored));

      let parsedList = [];
      try {
        const raw = window.localStorage?.getItem(CUSTOM_PATTERNS_STORAGE_KEY);
        if (raw) {
          const json = JSON.parse(raw);
          if (Array.isArray(json)) {
            parsedList = json;
          }
        }
      } catch (error) {
        parsedList = [];
      }

      const patterns = [];
      parsedList.forEach(entry => {
        const normalized = this.normalizeStoredCustomPattern(entry, maxWidth, maxHeight);
        if (normalized) {
          patterns.push(normalized);
        }
      });

      patterns.sort((a, b) => (b.created || 0) - (a.created || 0));
      if (maxStored > 0 && patterns.length > maxStored) {
        patterns.length = maxStored;
      }
      this.customPatterns = patterns;

      let counter = this.readStoredCustomPatternCounter();
      const maxAutoIndex = patterns.reduce((highest, pattern) => Math.max(highest, pattern.autoIndex || 0), 0);
      if (!Number.isFinite(counter) || counter <= maxAutoIndex) {
        counter = maxAutoIndex + 1;
      }
      this.customPatternCounter = Math.max(1, counter);
    }

    readStoredCustomPatternCounter() {
      try {
        const raw = window.localStorage?.getItem(CUSTOM_PATTERN_COUNTER_STORAGE_KEY);
        if (!raw) {
          return 1;
        }
        const parsed = Number.parseInt(raw, 10);
        if (Number.isFinite(parsed) && parsed > 0) {
          return parsed;
        }
      } catch (error) {
        // ignore storage errors
      }
      return 1;
    }

    persistCustomPatternCounter() {
      try {
        const value = Math.max(1, Math.floor(this.customPatternCounter || 1));
        window.localStorage?.setItem(CUSTOM_PATTERN_COUNTER_STORAGE_KEY, String(value));
      } catch (error) {
        // ignore storage errors
      }
    }

    normalizeStoredCustomPattern(entry, maxWidth, maxHeight) {
      if (!entry || typeof entry !== 'object') {
        return null;
      }
      const id = typeof entry.id === 'string' && entry.id.trim() ? entry.id.trim() : null;
      if (!id) {
        return null;
      }
      const customName = typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : null;
      const autoIndex = Number.isFinite(entry.autoIndex) && entry.autoIndex > 0
        ? Math.floor(entry.autoIndex)
        : null;
      const rawCells = Array.isArray(entry.cells) ? entry.cells : [];
      const unique = new Set();
      const normalizedCells = [];
      rawCells.forEach(cell => {
        if (!Array.isArray(cell) || cell.length < 2) {
          return;
        }
        const x = Math.max(0, Math.floor(Number(cell[0]) || 0));
        const y = Math.max(0, Math.floor(Number(cell[1]) || 0));
        const key = serializeCell(x, y);
        if (unique.has(key)) {
          return;
        }
        unique.add(key);
        normalizedCells.push([x, y]);
      });
      if (!normalizedCells.length) {
        return null;
      }
      let maxX = 0;
      let maxY = 0;
      normalizedCells.forEach(([x, y]) => {
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      });
      const width = maxX + 1;
      const height = maxY + 1;
      if ((maxWidth && width > maxWidth) || (maxHeight && height > maxHeight)) {
        return null;
      }
      const created = Number.isFinite(entry.created) ? entry.created : Date.now();
      const relativeCells = Object.freeze(normalizedCells.map(([x, y]) => Object.freeze([x, y])));
      const descriptionParams = { width, height, cells: relativeCells.length };
      return Object.freeze({
        id,
        origin: 'custom',
        nameKey: autoIndex ? 'index.sections.gameOfLife.patterns.customNameTemplate' : null,
        nameParams: autoIndex ? { index: autoIndex } : null,
        nameFallback: autoIndex ? `Pattern #${autoIndex}` : (customName || id),
        customName,
        descriptionKey: 'index.sections.gameOfLife.patterns.customDescription',
        descriptionParams,
        descriptionFallback: `${width} × ${height} – ${relativeCells.length} cells`,
        anchor: Object.freeze({ x: 0, y: 0 }),
        cells: relativeCells,
        relativeCells,
        autoIndex,
        created,
        meta: Object.freeze({ width, height, population: relativeCells.length })
      });
    }

    persistCustomPatterns() {
      const selectionConfig = this.config.selection || DEFAULT_CONFIG.selection;
      const maxStored = Math.max(0, Math.floor(selectionConfig.maxStored ?? DEFAULT_CONFIG.selection.maxStored));
      const limit = maxStored > 0 ? Math.min(maxStored, this.customPatterns.length) : this.customPatterns.length;
      const serialized = [];
      for (let i = 0; i < limit; i += 1) {
        const pattern = this.customPatterns[i];
        if (!pattern) {
          continue;
        }
        const cells = Array.isArray(pattern.relativeCells)
          ? pattern.relativeCells.map(([x, y]) => [x, y])
          : [];
        serialized.push({
          id: pattern.id,
          name: pattern.customName || null,
          autoIndex: pattern.autoIndex || null,
          cells,
          width: pattern.meta?.width || 0,
          height: pattern.meta?.height || 0,
          created: Number.isFinite(pattern.created) ? pattern.created : Date.now()
        });
      }
      try {
        window.localStorage?.setItem(CUSTOM_PATTERNS_STORAGE_KEY, JSON.stringify(serialized));
      } catch (error) {
        // ignore storage errors
      }
    }

    getAllPatterns() {
      return this.builtinPatterns.concat(this.customPatterns);
    }

    getPatternById(id) {
      if (!id) {
        return null;
      }
      const all = this.getAllPatterns();
      return all.find(pattern => pattern.id === id) || null;
    }

    getPatternDisplayName(pattern) {
      if (!pattern) {
        return '';
      }
      if (pattern.customName) {
        return pattern.customName;
      }
      if (pattern.nameKey) {
        return translate(pattern.nameKey, pattern.nameFallback || pattern.id, pattern.nameParams);
      }
      return pattern.nameFallback || pattern.id;
    }

    renderPatterns() {
      if (!this.patternSelect) {
        return;
      }
      this.patternSelect.innerHTML = '';
      const placeholderOption = document.createElement('option');
      placeholderOption.value = '';
      placeholderOption.dataset.i18n = 'index.sections.gameOfLife.patterns.none';
      placeholderOption.textContent = translate(
        'index.sections.gameOfLife.patterns.none',
        'No pattern'
      );
      this.patternSelect.append(placeholderOption);

      const totalPatterns = this.builtinPatterns.length + this.customPatterns.length;
      if (!totalPatterns) {
        this.patternSelect.disabled = true;
        this.patternSelect.value = '';
        if (this.selectedPatternId !== null) {
          this.selectedPatternId = null;
          this.scheduleAutosave();
        } else {
          this.selectedPatternId = null;
        }
        if (this.patternEmpty) {
          this.patternEmpty.hidden = false;
        }
        this.updatePatternDescription();
        return;
      }

      this.patternSelect.disabled = false;
      if (this.patternEmpty) {
        this.patternEmpty.hidden = true;
      }

      let hasSelectedPattern = false;
      const appendOption = (pattern, parent) => {
        if (!pattern) {
          return;
        }
        const option = document.createElement('option');
        option.value = pattern.id;
        const label = this.getPatternDisplayName(pattern);
        if (!pattern.customName && pattern.nameKey) {
          option.dataset.i18n = pattern.nameKey;
          option.textContent = translate(pattern.nameKey, pattern.nameFallback || label, pattern.nameParams);
        } else {
          delete option.dataset.i18n;
          option.textContent = label;
        }
        if (pattern.id === this.selectedPatternId) {
          option.selected = true;
          hasSelectedPattern = true;
        }
        parent.append(option);
      };

      this.builtinPatterns.forEach(pattern => appendOption(pattern, this.patternSelect));

      if (this.customPatterns.length) {
        const group = document.createElement('optgroup');
        const labelKey = 'index.sections.gameOfLife.patterns.customGroup';
        group.label = translate(labelKey, 'Personal patterns');
        group.dataset.i18n = labelKey;
        this.customPatterns.forEach(pattern => appendOption(pattern, group));
        this.patternSelect.append(group);
      }

      if (!hasSelectedPattern) {
        if (this.selectedPatternId !== null) {
          this.selectedPatternId = null;
          this.scheduleAutosave();
        } else {
          this.selectedPatternId = null;
        }
        this.patternSelect.value = '';
      }

      this.updatePatternDescription();
    }

    updatePatternDescription() {
      if (!this.patternDescription) {
        return;
      }
      if (this.selectionState.message) {
        const { key, fallback, params } = this.selectionState.message;
        this.patternDescription.hidden = false;
        if (key) {
          this.patternDescription.dataset.i18n = key;
          this.patternDescription.textContent = translate(key, fallback || '', params);
        } else {
          delete this.patternDescription.dataset.i18n;
          this.patternDescription.textContent = fallback || '';
        }
        this.selectionState.message = null;
        return;
      }
      if (!this.selectedPatternId) {
        this.patternDescription.hidden = true;
        this.patternDescription.textContent = '';
        delete this.patternDescription.dataset.i18n;
        return;
      }
      const pattern = this.getPatternById(this.selectedPatternId);
      if (!pattern) {
        this.patternDescription.hidden = true;
        this.patternDescription.textContent = '';
        delete this.patternDescription.dataset.i18n;
        return;
      }
      const fallback = pattern.descriptionFallback || '';
      const description = pattern.descriptionKey
        ? translate(pattern.descriptionKey, fallback, pattern.descriptionParams)
        : fallback;
      if (!description) {
        this.patternDescription.hidden = true;
        this.patternDescription.textContent = '';
        delete this.patternDescription.dataset.i18n;
        return;
      }
      this.patternDescription.hidden = false;
      if (pattern.descriptionKey) {
        this.patternDescription.dataset.i18n = pattern.descriptionKey;
      } else {
        delete this.patternDescription.dataset.i18n;
      }
      this.patternDescription.textContent = description;
    }

    updateRandomInputs() {
      if (this.randomWidthInput) {
        this.randomWidthInput.value = String(Math.round(
          clamp(this.config.random.defaultWidth, 5, this.config.random.maxWidth)
        ));
      }
      if (this.randomHeightInput) {
        this.randomHeightInput.value = String(Math.round(
          clamp(this.config.random.defaultHeight, 5, this.config.random.maxHeight)
        ));
      }
      if (this.randomDensitySlider) {
        const densityPercent = Math.round(clamp(this.config.random.defaultDensity, 0, 1) * 100);
        this.randomDensitySlider.value = String(densityPercent);
        if (this.randomDensityValue) {
          this.randomDensityValue.textContent = `${densityPercent}%`;
        }
      }
    }

    updateUI() {
      if (this.playPauseButton) {
        this.playPauseButton.textContent = this.state.running
          ? translate('index.sections.gameOfLife.controls.pause', 'Pause')
          : translate('index.sections.gameOfLife.controls.play', 'Play');
        this.playPauseButton.setAttribute('aria-pressed', this.state.running ? 'true' : 'false');
        this.playPauseButton.dataset.state = this.state.running ? 'play' : 'pause';
      }
      if (this.fastForwardButton) {
        this.fastForwardButton.setAttribute('aria-pressed', this.state.fastForward ? 'true' : 'false');
      }
      if (this.generationValue) {
        this.generationValue.textContent = String(this.state.generation);
      }
      if (this.populationValue) {
        this.populationValue.textContent = String(this.state.aliveCells.size);
      }
      if (this.speedSlider) {
        this.speedSlider.value = this.speedToSliderValue(this.state.speedMs);
      }
      if (this.zoomSlider) {
        this.zoomSlider.value = String(Math.round(this.viewport.cellSize));
      }
      if (this.selectionButton) {
        this.selectionButton.disabled = this.state.running;
        const pressed = this.selectionState.enabled && !this.state.running;
        this.selectionButton.setAttribute('aria-pressed', pressed ? 'true' : 'false');
      }
      this.requestRedraw();
      this.scheduleAutosave();
    }

    play() {
      this.state.running = true;
      this.setSelectionEnabled(false);
      this.updateUI();
      this.ensureAnimationLoop();
    }

    pause() {
      this.state.running = false;
      this.updateUI();
    }

    toggleFastForward() {
      this.state.fastForward = !this.state.fastForward;
      if (this.fastForwardButton) {
        this.fastForwardButton.setAttribute('aria-pressed', this.state.fastForward ? 'true' : 'false');
      }
      this.scheduleAutosave();
    }

    step() {
      this.pause();
      this.stepInternal();
    }

    stepInternal() {
      const nextState = new Set();
      const candidates = this.stepCandidates;
      candidates.clear();
      const bounds = this.getSimulationBounds();
      const { minX, maxX, minY, maxY } = bounds;
      const aliveCells = this.state.aliveCells;
      const cellBuffer = this.stepCellBuffer;

      for (const key of aliveCells) {
        const cell = deserializeCell(key, cellBuffer);
        const x = cell.x;
        const y = cell.y;
        if (x < minX || x > maxX || y < minY || y > maxY) {
          continue;
        }
        for (let i = 0; i < NEIGHBOR_OFFSETS.length; i += 1) {
          const offset = NEIGHBOR_OFFSETS[i];
          const nx = x + offset[0];
          const ny = y + offset[1];
          if (nx < minX || nx > maxX || ny < minY || ny > maxY) {
            continue;
          }
          const neighborKey = serializeCell(nx, ny);
          candidates.set(neighborKey, (candidates.get(neighborKey) || 0) + 1);
        }
      }

      for (const [key, count] of candidates) {
        const alive = aliveCells.has(key);
        if ((alive && (count === 2 || count === 3)) || (!alive && count === 3)) {
          nextState.add(key);
        }
      }

      this.pushHistory();
      this.state.aliveCells = nextState;
      this.state.generation += 1;
      this.updateUI();
      candidates.clear();
    }

    pushHistory() {
      const history = this.state.history;
      history.push({
        alive: new Set(this.state.aliveCells),
        generation: this.state.generation
      });
      const limit = Math.max(1, Number(this.config.simulation.historyLimit) || DEFAULT_CONFIG.simulation.historyLimit);
      while (history.length > limit) {
        history.shift();
      }
    }

    rewind() {
      if (!this.state.history.length) {
        return;
      }
      const snapshot = this.state.history.pop();
      this.state.aliveCells = snapshot.alive;
      this.state.generation = snapshot.generation;
      this.pause();
      this.updateUI();
    }

    reset() {
      this.state.aliveCells.clear();
      this.state.history = [];
      this.state.generation = 0;
      this.cancelSelection();
      this.selectionState.message = null;
      this.pause();
      this.updateUI();
    }

    getSpeedSliderRange() {
      const min = Number.isFinite(this.speedSliderRange?.min)
        ? this.speedSliderRange.min
        : 50;
      const max = Number.isFinite(this.speedSliderRange?.max)
        ? this.speedSliderRange.max
        : 600;
      if (min > max) {
        return { min: max, max: min };
      }
      return { min, max };
    }

    speedToSliderValue(speed) {
      const { min, max } = this.getSpeedSliderRange();
      const clampedSpeed = clamp(Number(speed) || this.state.speedMs, 30, 2000);
      const mirrored = max + min - clamp(clampedSpeed, min, max);
      const sliderValue = clamp(mirrored, min, max);
      return String(Math.round(sliderValue));
    }

    sliderValueToSpeed(value) {
      const { min, max } = this.getSpeedSliderRange();
      const numeric = clamp(Number(value) || min, min, max);
      const mirrored = max + min - numeric;
      return clamp(mirrored, 30, 2000);
    }

    setSpeed(value) {
      this.state.speedMs = clamp(value, 30, 2000);
      if (this.speedSlider) {
        this.speedSlider.value = this.speedToSliderValue(this.state.speedMs);
      }
      this.scheduleAutosave();
    }

    setZoom(newCellSize, anchorCell) {
      const clamped = clamp(newCellSize, this.viewport.minCellSize, this.viewport.maxCellSize);
      const previousSize = this.viewport.cellSize;
      if (Math.abs(previousSize - clamped) < 0.01) {
        return;
      }
      const canvasWidth = this.canvasRect.width || 1;
      const canvasHeight = this.canvasRect.height || 1;
      let anchor = anchorCell;
      if (!anchor) {
        anchor = {
          x: this.viewport.originX + (canvasWidth / previousSize) / 2,
          y: this.viewport.originY + (canvasHeight / previousSize) / 2
        };
      }
      const offsetX = (anchor.x - this.viewport.originX);
      const offsetY = (anchor.y - this.viewport.originY);
      const ratio = previousSize / clamped;
      this.viewport.originX = anchor.x - offsetX * ratio;
      this.viewport.originY = anchor.y - offsetY * ratio;
      this.viewport.cellSize = clamped;
      this.updateUI();
    }

    updateCanvasSize(width, height) {
      if (!this.canvas || !this.ctx) {
        return;
      }
      const safeWidth = Math.max(1, width);
      const safeHeight = Math.max(1, height);
      this.canvasRect = { width: safeWidth, height: safeHeight };
      const rawDpr = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
        ? window.devicePixelRatio
        : 1;
      const configuredMaxDpr = typeof globalThis !== 'undefined' && typeof globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO === 'number'
        ? Math.max(1, globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO)
        : 1;
      const dpr = Math.min(rawDpr, configuredMaxDpr);
      this.dpr = dpr;
      this.canvas.width = Math.round(safeWidth * dpr);
      this.canvas.height = Math.round(safeHeight * dpr);
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.requestRedraw();
    }

    toggleMenu(forceState) {
      if (!this.menu) {
        return;
      }
      const isExpanded = this.menu.dataset.expanded !== 'false';
      const nextState = typeof forceState === 'boolean' ? forceState : !isExpanded;
      this.menu.dataset.expanded = nextState ? 'true' : 'false';
      this.updateMenuToggleLabel();
      this.scheduleAutosave();
    }

    updateMenuToggleLabel() {
      if (!this.menuToggle) {
        return;
      }
      const expanded = this.menu?.dataset.expanded !== 'false';
      const key = expanded
        ? 'index.sections.gameOfLife.menu.collapse'
        : 'index.sections.gameOfLife.menu.expand';
      const fallback = expanded ? 'Réduire le panneau' : 'Afficher le panneau';
      this.menuToggle.dataset.i18n = key;
      this.menuToggle.textContent = translate(key, fallback);
      this.menuToggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
    }

    startMenuDrag(event) {
      if (!this.menu || event.button !== 0) {
        return;
      }
      if (this.menuToggle && (event.target === this.menuToggle || this.menuToggle.contains(event.target))) {
        return;
      }
      const rect = this.menu.getBoundingClientRect();
      this.menuDragState.active = true;
      this.menuDragState.pointerId = event.pointerId;
      this.menuDragState.offsetX = event.clientX - rect.left;
      this.menuDragState.offsetY = event.clientY - rect.top;
      this.menu.style.left = `${rect.left}px`;
      this.menu.style.top = `${rect.top}px`;
      this.menu.style.right = 'auto';
      this.menu.style.bottom = 'auto';
      this.menu.style.transform = 'none';
      this.menuHeader?.setPointerCapture?.(event.pointerId);
      event.preventDefault();
    }

    handleMenuPointerMove(event) {
      if (!this.menuDragState.active || event.pointerId !== this.menuDragState.pointerId || !this.menu) {
        return;
      }
      const newLeft = clamp(
        event.clientX - this.menuDragState.offsetX,
        8,
        Math.max(8, window.innerWidth - this.menu.offsetWidth - 8)
      );
      const newTop = clamp(
        event.clientY - this.menuDragState.offsetY,
        8,
        Math.max(8, window.innerHeight - this.menu.offsetHeight - 8)
      );
      this.menu.style.left = `${newLeft}px`;
      this.menu.style.top = `${newTop}px`;
    }

    handleMenuPointerUp(event) {
      if (!this.menuDragState.active || event.pointerId !== this.menuDragState.pointerId) {
        return;
      }
      this.menuDragState.active = false;
      this.menuDragState.pointerId = null;
      this.menuHeader?.releasePointerCapture?.(event.pointerId);
      this.clampMenuPosition();
      this.scheduleAutosave();
    }

    clampMenuPosition() {
      if (!this.menu || !this.menu.style.left) {
        return;
      }
      const currentLeft = Number.parseFloat(this.menu.style.left);
      const currentTop = Number.parseFloat(this.menu.style.top);
      if (!Number.isFinite(currentLeft) || !Number.isFinite(currentTop)) {
        return;
      }
      const maxLeft = Math.max(8, window.innerWidth - this.menu.offsetWidth - 8);
      const maxTop = Math.max(8, window.innerHeight - this.menu.offsetHeight - 8);
      const clampedLeft = clamp(currentLeft, 8, maxLeft);
      const clampedTop = clamp(currentTop, 8, maxTop);
      this.menu.style.left = `${clampedLeft}px`;
      this.menu.style.top = `${clampedTop}px`;
    }

    setSelectionEnabled(enabled) {
      const allowSelection = Boolean(enabled) && !this.state.running;
      if (!allowSelection && this.selectionState.enabled) {
        this.cancelSelection();
      }
      this.selectionState.enabled = allowSelection;
      if (!allowSelection) {
        this.selectionState.message = null;
        this.cancelPendingSelection(true);
      }
      if (this.selectionButton) {
        this.selectionButton.disabled = this.state.running;
        this.selectionButton.setAttribute('aria-pressed', allowSelection ? 'true' : 'false');
      }
      this.requestRedraw();
      this.updatePatternDescription();
      this.scheduleAutosave();
    }

    cancelSelection() {
      if (this.selectionState.active) {
        this.requestRedraw();
      }
      this.selectionState.active = false;
      this.selectionState.pointerId = null;
      this.selectionState.startCell = null;
      this.selectionState.currentCell = null;
      this.cancelPendingSelection(true);
    }

    startSelection(cell, pointerId) {
      if (!cell) {
        return;
      }
      this.selectionState.active = true;
      this.selectionState.pointerId = pointerId;
      this.selectionState.startCell = this.selectionState.startCell || { x: 0, y: 0 };
      this.selectionState.startCell.x = cell.x;
      this.selectionState.startCell.y = cell.y;
      this.selectionState.currentCell = this.selectionState.currentCell || { x: 0, y: 0 };
      this.selectionState.currentCell.x = cell.x;
      this.selectionState.currentCell.y = cell.y;
      this.selectionState.message = null;
      this.pointerId = pointerId;
      this.requestRedraw();
    }

    updateSelection(cell) {
      if (!this.selectionState.active || !cell) {
        return;
      }
      this.selectionState.currentCell = this.selectionState.currentCell || { x: 0, y: 0 };
      this.selectionState.currentCell.x = cell.x;
      this.selectionState.currentCell.y = cell.y;
      this.requestRedraw();
    }

    finishSelection() {
      if (!this.selectionState.active) {
        return;
      }
      const startCell = this.selectionState.startCell
        ? { x: this.selectionState.startCell.x, y: this.selectionState.startCell.y }
        : null;
      const endCell = this.selectionState.currentCell
        ? { x: this.selectionState.currentCell.x, y: this.selectionState.currentCell.y }
        : startCell;
      this.selectionState.active = false;
      this.selectionState.pointerId = null;
      this.selectionState.startCell = null;
      this.selectionState.currentCell = null;
      this.requestRedraw();
      if (startCell && endCell) {
        this.saveSelectionAsPattern(startCell, endCell);
      }
    }

    saveSelectionAsPattern(startCell, endCell) {
      if (!startCell || !endCell) {
        return;
      }
      const minX = Math.min(startCell.x, endCell.x);
      const maxX = Math.max(startCell.x, endCell.x);
      const minY = Math.min(startCell.y, endCell.y);
      const maxY = Math.max(startCell.y, endCell.y);
      const width = maxX - minX + 1;
      const height = maxY - minY + 1;
      const selectionConfig = this.config.selection || DEFAULT_CONFIG.selection;
      const rawMaxWidth = Number(selectionConfig.maxWidth ?? DEFAULT_CONFIG.selection.maxWidth);
      const rawMaxHeight = Number(selectionConfig.maxHeight ?? DEFAULT_CONFIG.selection.maxHeight);
      const maxWidth = Number.isFinite(rawMaxWidth) && rawMaxWidth > 0 ? Math.floor(rawMaxWidth) : 0;
      const maxHeight = Number.isFinite(rawMaxHeight) && rawMaxHeight > 0 ? Math.floor(rawMaxHeight) : 0;
      if ((maxWidth && width > maxWidth) || (maxHeight && height > maxHeight)) {
        const displayWidth = maxWidth || '∞';
        const displayHeight = maxHeight || '∞';
        this.selectionState.message = {
          key: 'index.sections.gameOfLife.patterns.selectionTooLarge',
          fallback: `Selection is limited to ${displayWidth}×${displayHeight} cells.`,
          params: { maxWidth: displayWidth, maxHeight: displayHeight }
        };
        this.updatePatternDescription();
        return;
      }
      const aliveCells = this.state.aliveCells;
      const buffer = this.selectionCellBuffer;
      const relativeCells = [];
      const unique = new Set();
      for (const key of aliveCells) {
        const cell = deserializeCell(key, buffer);
        if (cell.x < minX || cell.x > maxX || cell.y < minY || cell.y > maxY) {
          continue;
        }
        const rx = cell.x - minX;
        const ry = cell.y - minY;
        const relKey = serializeCell(rx, ry);
        if (unique.has(relKey)) {
          continue;
        }
        unique.add(relKey);
        relativeCells.push([rx, ry]);
      }
      if (!relativeCells.length) {
        this.selectionState.message = {
          key: 'index.sections.gameOfLife.patterns.selectionEmpty',
          fallback: 'The selected area does not contain any living cells.'
        };
        if (this.selectedPatternId !== null) {
          this.selectedPatternId = null;
          this.scheduleAutosave();
        } else {
          this.selectedPatternId = null;
        }
        this.updatePatternDescription();
        return;
      }
      this.prepareSelectionSave({
        bounds: { width, height },
        relativeCells
      });
    }

    resetSelectionForm() {
      if (this.selectionPanel) {
        this.selectionPanel.hidden = true;
      }
      if (this.selectionNameInput) {
        this.selectionNameInput.value = '';
        this.selectionNameInput.placeholder = '';
      }
    }

    prepareSelectionSave(data) {
      if (!data || !data.relativeCells || !data.relativeCells.length) {
        return;
      }
      const bounds = data.bounds || { width: 0, height: 0 };
      this.pendingSelection = {
        bounds: {
          width: Math.max(1, Math.floor(bounds.width || 0)),
          height: Math.max(1, Math.floor(bounds.height || 0))
        },
        relativeCells: data.relativeCells.map(([x, y]) => [x, y])
      };
      if (!this.selectionPanel || !this.selectionForm || !this.selectionNameInput) {
        this.finalizeSelectionSave('');
        return;
      }
      const nextIndex = Math.max(1, this.customPatternCounter);
      const automaticName = translate(
        'index.sections.gameOfLife.patterns.customNameTemplate',
        `Pattern #${nextIndex}`,
        { index: nextIndex }
      );
      const placeholder = translate(
        'index.sections.gameOfLife.patterns.selection.namePlaceholder',
        `Automatic name: ${automaticName}`,
        { name: automaticName }
      );
      this.selectionNameInput.value = '';
      this.selectionNameInput.placeholder = placeholder;
      this.selectionPanel.hidden = false;
      if (typeof this.selectionNameInput.focus === 'function') {
        window.requestAnimationFrame(() => {
          try {
            this.selectionNameInput.focus({ preventScroll: true });
          } catch (error) {
            this.selectionNameInput.focus();
          }
        });
      }
    }

    handleSelectionFormSubmit() {
      if (!this.pendingSelection) {
        this.resetSelectionForm();
        return;
      }
      const name = typeof this.selectionNameInput?.value === 'string'
        ? this.selectionNameInput.value.trim()
        : '';
      this.finalizeSelectionSave(name);
    }

    finalizeSelectionSave(name) {
      const data = this.pendingSelection;
      this.pendingSelection = null;
      this.resetSelectionForm();
      if (!data) {
        return;
      }
      const pattern = this.createCustomPattern(data.bounds, data.relativeCells, name);
      if (!pattern) {
        return;
      }
      this.customPatterns.unshift(pattern);
      const selectionConfig = this.config.selection || DEFAULT_CONFIG.selection;
      const maxStored = Math.max(0, Math.floor(selectionConfig.maxStored ?? DEFAULT_CONFIG.selection.maxStored));
      if (maxStored > 0 && this.customPatterns.length > maxStored) {
        this.customPatterns.length = maxStored;
      }
      this.persistCustomPatterns();
      const displayName = this.getPatternDisplayName(pattern) || pattern.id;
      this.selectionState.message = {
        key: 'index.sections.gameOfLife.patterns.selection.saved',
        fallback: `"${displayName}" was added to your patterns.`,
        params: { name: displayName }
      };
      this.selectedPatternId = pattern.id;
      this.renderPatterns();
      this.scheduleAutosave();
    }

    cancelPendingSelection(silent) {
      const hadPending = Boolean(this.pendingSelection);
      this.pendingSelection = null;
      this.resetSelectionForm();
      if (!silent && hadPending) {
        this.selectionState.message = {
          key: 'index.sections.gameOfLife.patterns.selection.cancelled',
          fallback: 'Pattern save cancelled.'
        };
        this.updatePatternDescription();
      }
    }

    createCustomPattern(bounds, relativeCells, customName) {
      if (!bounds || !relativeCells || !relativeCells.length) {
        return null;
      }
      const trimmedName = typeof customName === 'string' ? customName.trim() : '';
      let finalName = trimmedName || '';
      let autoIndex = null;
      if (!finalName) {
        autoIndex = this.customPatternCounter;
      }
      this.customPatternCounter = Math.max(1, this.customPatternCounter + 1);
      this.persistCustomPatternCounter();
      const id = `custom-${Date.now().toString(36)}-${Math.floor(Math.random() * 0xffffff).toString(16)}`;
      const created = Date.now();
      const frozenCells = Object.freeze(relativeCells.map(([x, y]) => Object.freeze([x, y])));
      const width = Math.max(1, Math.floor(bounds.width));
      const height = Math.max(1, Math.floor(bounds.height));
      const cellCount = frozenCells.length;
      const descriptionParams = { width, height, cells: cellCount };
      const fallbackName = autoIndex ? `Pattern #${autoIndex}` : id;
      const displayName = finalName || fallbackName;
      return Object.freeze({
        id,
        origin: 'custom',
        nameKey: autoIndex ? 'index.sections.gameOfLife.patterns.customNameTemplate' : null,
        nameParams: autoIndex ? { index: autoIndex } : null,
        nameFallback: displayName,
        customName: finalName || null,
        descriptionKey: 'index.sections.gameOfLife.patterns.customDescription',
        descriptionParams,
        descriptionFallback: `${width} × ${height} – ${cellCount} cells`,
        anchor: Object.freeze({ x: 0, y: 0 }),
        cells: frozenCells,
        relativeCells: frozenCells,
        autoIndex,
        created,
        meta: Object.freeze({ width, height, population: cellCount })
      });
    }

    startTouchGesture() {
      if (!this.canvas) {
        return;
      }
      const pointers = Array.from(this.touchState.pointers.values());
      if (pointers.length < 2) {
        return;
      }
      const [first, second] = pointers;
      const midpoint = {
        x: (first.x + second.x) / 2,
        y: (first.y + second.y) / 2
      };
      const distance = Math.hypot(second.x - first.x, second.y - first.y) || 1;
      this.touchState.isGesture = true;
      this.touchState.initialDistance = distance;
      this.touchState.initialCellSize = this.viewport.cellSize;
      this.touchState.lastMidpoint = midpoint;
      if (this.dragState.active && this.dragState.initialCellKey) {
        const originalCell = deserializeCell(
          this.dragState.initialCellKey,
          this.stepCellBuffer
        );
        if (originalCell) {
          this.applyCellToggle(
            originalCell.x,
            originalCell.y,
            this.dragState.initialCellAlive
          );
        }
      }
      if (this.selectionState.active) {
        this.cancelSelection();
      }
      this.panState.active = true;
      this.panState.lastX = midpoint.x;
      this.panState.lastY = midpoint.y;
      this.dragState.active = false;
      this.dragState.lastCellKey = null;
      this.dragState.initialCellKey = null;
      this.dragState.initialCellAlive = false;
    }

    handleTouchGestureMove() {
      if (!this.canvas) {
        return;
      }
      const pointers = Array.from(this.touchState.pointers.values());
      if (pointers.length < 2) {
        return;
      }
      const [first, second] = pointers;
      const midpoint = {
        x: (first.x + second.x) / 2,
        y: (first.y + second.y) / 2
      };
      const lastMidpoint = this.touchState.lastMidpoint || midpoint;
      const dx = midpoint.x - lastMidpoint.x;
      const dy = midpoint.y - lastMidpoint.y;
      const rect = this.canvas.getBoundingClientRect();
      const anchor = {
        x: this.viewport.originX + (midpoint.x - rect.left) / this.viewport.cellSize,
        y: this.viewport.originY + (midpoint.y - rect.top) / this.viewport.cellSize
      };
      if (dx !== 0 || dy !== 0) {
        this.panByPixels(dx, dy);
        this.panState.lastX = midpoint.x;
        this.panState.lastY = midpoint.y;
        this.touchState.lastMidpoint = midpoint;
      }
      const distance = Math.hypot(second.x - first.x, second.y - first.y);
      if (!distance || !this.touchState.initialDistance) {
        return;
      }
      const ratio = distance / this.touchState.initialDistance;
      const newSize = clamp(
        this.touchState.initialCellSize * ratio,
        this.viewport.minCellSize,
        this.viewport.maxCellSize
      );
      this.setZoom(newSize, anchor);
    }

    handlePointerDown(event) {
      if (!this.canvas) {
        return;
      }
      if (event.pointerType === 'touch') {
        this.canvas.setPointerCapture?.(event.pointerId);
        this.touchState.pointers.set(event.pointerId, {
          x: event.clientX,
          y: event.clientY
        });
        if (this.touchState.pointers.size >= 2) {
          this.startTouchGesture();
          event.preventDefault();
          return;
        }
        this.pointerId = event.pointerId;
      } else {
        this.pointerId = event.pointerId;
      }
      const isPanButton = event.button === 1 || event.button === 2;
      const wantsPan = this.spacePressed || isPanButton;
      if (wantsPan) {
        this.panState.active = true;
        this.panState.lastX = event.clientX;
        this.panState.lastY = event.clientY;
        event.preventDefault();
        return;
      }
      const cell = this.eventToCell(event);
      if (!cell) {
        return;
      }
      const isPrimaryButton = event.button === 0 || typeof event.button === 'undefined';
      if (this.selectionState.enabled && !this.state.running && isPrimaryButton) {
        this.startSelection(cell, event.pointerId);
        return;
      }
      if (this.selectedPatternId) {
        this.placePatternAt(cell.x, cell.y);
        return;
      }
      const key = serializeCell(cell.x, cell.y);
      const alreadyAlive = this.state.aliveCells.has(key);
      this.dragState.active = true;
      this.dragState.mode = alreadyAlive ? 'erase' : 'draw';
      this.applyCellToggle(cell.x, cell.y, this.dragState.mode === 'draw');
      this.dragState.lastCellKey = key;
      this.dragState.initialCellKey = key;
      this.dragState.initialCellAlive = alreadyAlive;
    }

    handlePointerMove(event) {
      if (event.pointerType === 'touch' && this.touchState.pointers.has(event.pointerId)) {
        this.touchState.pointers.set(event.pointerId, {
          x: event.clientX,
          y: event.clientY
        });
        if (this.touchState.isGesture) {
          this.handleTouchGestureMove();
          event.preventDefault();
          return;
        }
      }
      if (this.selectionState.active && event.pointerId === this.selectionState.pointerId) {
        const cell = this.eventToCell(event);
        if (cell) {
          this.updateSelection(cell);
        }
        return;
      }
      if (this.panState.active) {
        const dx = event.clientX - this.panState.lastX;
        const dy = event.clientY - this.panState.lastY;
        this.panState.lastX = event.clientX;
        this.panState.lastY = event.clientY;
        this.panByPixels(dx, dy);
        return;
      }
      if (!this.dragState.active) {
        return;
      }
      const cell = this.eventToCell(event);
      if (!cell) {
        return;
      }
      const key = serializeCell(cell.x, cell.y);
      if (key === this.dragState.lastCellKey) {
        return;
      }
      const shouldDraw = this.dragState.mode === 'draw';
      this.applyCellToggle(cell.x, cell.y, shouldDraw);
      this.dragState.lastCellKey = key;
    }

    handlePointerUp(event) {
      if (event.pointerType === 'touch') {
        this.touchState.pointers.delete(event.pointerId);
        if (this.touchState.pointers.size >= 2) {
          const pointers = Array.from(this.touchState.pointers.values());
          const [first, second] = pointers;
          this.touchState.initialDistance = Math.hypot(
            second.x - first.x,
            second.y - first.y
          ) || this.touchState.initialDistance;
          this.touchState.initialCellSize = this.viewport.cellSize;
          this.touchState.lastMidpoint = {
            x: (first.x + second.x) / 2,
            y: (first.y + second.y) / 2
          };
        } else {
          this.touchState.isGesture = false;
          this.touchState.initialDistance = 0;
          this.touchState.initialCellSize = this.viewport.cellSize;
          this.touchState.lastMidpoint = null;
          this.panState.active = false;
          this.touchState.pointers.clear();
        }
      }
      if (this.selectionState.active && event.pointerId === this.selectionState.pointerId) {
        this.finishSelection();
        this.pointerId = null;
        return;
      }
      if (this.pointerId !== null && event.pointerId !== this.pointerId) {
        return;
      }
      this.pointerId = null;
      if (this.panState.active) {
        this.panState.active = false;
        return;
      }
      this.dragState.active = false;
      this.dragState.lastCellKey = null;
      this.dragState.initialCellKey = null;
      this.dragState.initialCellAlive = false;
    }

    handleWheel(event) {
      if (!event.ctrlKey) {
        const deltaX = event.deltaX;
        const deltaY = event.deltaY;
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
          this.panByPixels(deltaX, 0);
        } else {
          this.panByPixels(0, deltaY);
        }
        event.preventDefault();
        return;
      }
      event.preventDefault();
      const zoomDelta = event.deltaY;
      const zoomFactor = zoomDelta > 0 ? 0.9 : 1.1;
      const newSize = clamp(this.viewport.cellSize * zoomFactor, this.viewport.minCellSize, this.viewport.maxCellSize);
      const cell = this.eventToWorldCoordinate(event);
      this.setZoom(newSize, cell);
    }

    handleKeyDown(event) {
      const tagName = typeof event.target?.tagName === 'string'
        ? event.target.tagName.toUpperCase()
        : '';
      const isFormField = tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT';
      if (event.code === 'Space') {
        this.spacePressed = true;
        if (!isFormField) {
          event.preventDefault();
        }
      }
      if (event.code === 'Escape' && !isFormField) {
        if (this.selectionState.enabled || this.selectionState.active) {
          this.setSelectionEnabled(false);
          this.updateUI();
          return;
        }
      }
      if (isFormField) {
        return;
      }
      if (event.code === 'KeyR') {
        this.reset();
      }
      if (event.code === 'KeyZ' && (event.ctrlKey || event.metaKey || !event.shiftKey)) {
        this.rewind();
        event.preventDefault();
      }
      if (event.code === 'Space') {
        if (this.state.running) {
          this.pause();
        } else {
          this.play();
        }
      }
    }

    handleKeyUp(event) {
      if (event.code === 'Space') {
        this.spacePressed = false;
      }
    }

    eventToCell(event) {
      if (!this.canvas) {
        return null;
      }
      const rect = this.canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left);
      const y = (event.clientY - rect.top);
      const cellX = Math.floor(this.viewport.originX + x / this.viewport.cellSize);
      const cellY = Math.floor(this.viewport.originY + y / this.viewport.cellSize);
      return { x: cellX, y: cellY };
    }

    eventToWorldCoordinate(event) {
      if (!this.canvas) {
        return { x: 0, y: 0 };
      }
      const rect = this.canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left);
      const y = (event.clientY - rect.top);
      return {
        x: this.viewport.originX + x / this.viewport.cellSize,
        y: this.viewport.originY + y / this.viewport.cellSize
      };
    }

    panByPixels(dx, dy) {
      const scale = this.viewport.cellSize || 1;
      this.viewport.originX -= dx / scale;
      this.viewport.originY -= dy / scale;
      this.requestRedraw();
      this.scheduleAutosave();
    }

    applyCellToggle(x, y, alive) {
      const key = serializeCell(x, y);
      if (alive) {
        this.state.aliveCells.add(key);
      } else {
        this.state.aliveCells.delete(key);
      }
      this.updateUI();
    }

    placePatternAt(baseX, baseY) {
      const pattern = this.getPatternById(this.selectedPatternId);
      if (!pattern) {
        return;
      }
      this.pushHistory();
      const relativeCells = pattern.relativeCells
        ? pattern.relativeCells
        : pattern.cells.map(([x, y]) => [x - pattern.anchor.x, y - pattern.anchor.y]);
      relativeCells.forEach(([dx, dy]) => {
        const worldX = baseX + dx;
        const worldY = baseY + dy;
        this.state.aliveCells.add(serializeCell(worldX, worldY));
      });
      this.updateUI();
    }

    generateRandomPattern(mode) {
      const widthInput = clamp(
        Number(this.randomWidthInput?.value) || this.config.random.defaultWidth,
        5,
        this.config.random.maxWidth
      );
      const heightInput = clamp(
        Number(this.randomHeightInput?.value) || this.config.random.defaultHeight,
        5,
        this.config.random.maxHeight
      );
      const width = Math.round(widthInput);
      const height = Math.round(heightInput);
      const densityPercent = clamp(Number(this.randomDensitySlider?.value) || Math.round(this.config.random.defaultDensity * 100), 0, 100);
      const density = densityPercent / 100;
      const totalCells = Math.max(0, width * height);
      const desiredCells = Math.round(totalCells * density);
      const area = Math.max(0, Math.min(totalCells, desiredCells));
      let seed = this.randomSeed;
      const rng = createMulberry32(seed);
      seed = advanceSeed(seed);
      this.randomSeed = seed;
      saveSeed(seed);

      if (mode === 'replace') {
        this.pushHistory();
        this.state.aliveCells.clear();
        this.state.generation = 0;
      } else {
        this.pushHistory();
      }

      const bounds = this.getVisibleBounds();
      const centerX = Math.floor((bounds.minX + bounds.maxX) / 2);
      const centerY = Math.floor((bounds.minY + bounds.maxY) / 2);
      const startX = centerX - Math.floor(width / 2);
      const startY = centerY - Math.floor(height / 2);
      const used = new Set();
      let attempts = 0;
      while (used.size < area && attempts < area * 8) {
        attempts += 1;
        const rx = startX + Math.floor(rng() * width);
        const ry = startY + Math.floor(rng() * height);
        const key = serializeCell(rx, ry);
        if (!used.has(key)) {
          used.add(key);
          this.state.aliveCells.add(key);
        }
      }
      this.updateUI();
    }

    getVisibleBounds() {
      const widthCells = (this.canvasRect.width || 1) / this.viewport.cellSize;
      const heightCells = (this.canvasRect.height || 1) / this.viewport.cellSize;
      const minX = Math.floor(this.viewport.originX);
      const minY = Math.floor(this.viewport.originY);
      const maxX = Math.ceil(this.viewport.originX + widthCells);
      const maxY = Math.ceil(this.viewport.originY + heightCells);
      return { minX, minY, maxX, maxY };
    }

    computeAliveBounds() {
      const alive = this.state.aliveCells;
      if (!alive || !alive.size) {
        return null;
      }
      const buffer = this.boundsCellBuffer;
      let minX = Infinity;
      let maxX = -Infinity;
      let minY = Infinity;
      let maxY = -Infinity;
      for (const key of alive) {
        const cell = deserializeCell(key, buffer);
        if (cell.x < minX) { minX = cell.x; }
        if (cell.x > maxX) { maxX = cell.x; }
        if (cell.y < minY) { minY = cell.y; }
        if (cell.y > maxY) { maxY = cell.y; }
      }
      if (!Number.isFinite(minX) || !Number.isFinite(maxX) || !Number.isFinite(minY) || !Number.isFinite(maxY)) {
        return null;
      }
      return { minX, maxX, minY, maxY };
    }

    getSimulationBounds() {
      const visible = this.getVisibleBounds();
      const padding = Number.isFinite(this.viewport.paddingCells) ? this.viewport.paddingCells : 2;
      let minX = visible.minX - padding;
      let maxX = visible.maxX + padding;
      let minY = visible.minY - padding;
      let maxY = visible.maxY + padding;

      const aliveBounds = this.computeAliveBounds();
      if (aliveBounds) {
        minX = Math.min(minX, aliveBounds.minX - padding);
        maxX = Math.max(maxX, aliveBounds.maxX + padding);
        minY = Math.min(minY, aliveBounds.minY - padding);
        maxY = Math.max(maxY, aliveBounds.maxY + padding);
      }

      const maxDistance = Math.max(0, Number(this.config.simulation?.maxOffscreenDistance ?? DEFAULT_CONFIG.simulation.maxOffscreenDistance) || 0);
      if (maxDistance > 0) {
        const clampMinX = visible.minX - maxDistance;
        const clampMaxX = visible.maxX + maxDistance;
        const clampMinY = visible.minY - maxDistance;
        const clampMaxY = visible.maxY + maxDistance;
        minX = Math.max(minX, clampMinX);
        maxX = Math.min(maxX, clampMaxX);
        minY = Math.max(minY, clampMinY);
        maxY = Math.min(maxY, clampMaxY);
      }

      return {
        minX: Math.floor(minX),
        maxX: Math.ceil(maxX),
        minY: Math.floor(minY),
        maxY: Math.ceil(maxY)
      };
    }

    render() {
      if (!this.canvas || !this.ctx) {
        return;
      }
      const ctx = this.ctx;
      const width = this.canvasRect.width;
      const height = this.canvasRect.height;
      ctx.clearRect(0, 0, width, height);

      const cellSize = this.viewport.cellSize;
      const bounds = this.getVisibleBounds();
      const padding = this.viewport.paddingCells;

      ctx.save();
      const cellFill = typeof this.cellColor === 'string' && this.cellColor.trim()
        ? this.cellColor.trim()
        : DEFAULT_CELL_COLOR;
      ctx.fillStyle = cellFill;
      const aliveCells = this.state.aliveCells;
      const renderCell = this.renderCellBuffer;
      const minDrawX = bounds.minX - padding;
      const maxDrawX = bounds.maxX + padding;
      const minDrawY = bounds.minY - padding;
      const maxDrawY = bounds.maxY + padding;
      for (const key of aliveCells) {
        const cell = deserializeCell(key, renderCell);
        const cellX = cell.x;
        const cellY = cell.y;
        if (cellX < minDrawX || cellX > maxDrawX || cellY < minDrawY || cellY > maxDrawY) {
          continue;
        }
        const px = (cellX - this.viewport.originX) * cellSize;
        const py = (cellY - this.viewport.originY) * cellSize;
        ctx.fillRect(px, py, cellSize, cellSize);
      }
      ctx.restore();

      if (cellSize >= this.viewport.gridFadeStart) {
        const alpha = clamp((this.viewport.gridFadeEnd - cellSize) / Math.max(1, this.viewport.gridFadeEnd - this.viewport.gridFadeStart), 0, 1);
        const lineAlpha = 0.25 + (1 - alpha) * 0.35;
        ctx.save();
        ctx.strokeStyle = `rgba(180, 210, 255, ${lineAlpha.toFixed(3)})`;
        ctx.lineWidth = 1;
        ctx.beginPath();
        for (let x = Math.floor(bounds.minX); x <= Math.ceil(bounds.maxX); x += 1) {
          const px = Math.round((x - this.viewport.originX) * cellSize) + 0.5;
          ctx.moveTo(px, 0);
          ctx.lineTo(px, height);
        }
        for (let y = Math.floor(bounds.minY); y <= Math.ceil(bounds.maxY); y += 1) {
          const py = Math.round((y - this.viewport.originY) * cellSize) + 0.5;
          ctx.moveTo(0, py);
          ctx.lineTo(width, py);
        }
        ctx.stroke();
        ctx.restore();
      }

      if (this.selectionState.active && this.selectionState.startCell && this.selectionState.currentCell) {
        const start = this.selectionState.startCell;
        const current = this.selectionState.currentCell;
        const minX = Math.min(start.x, current.x);
        const minY = Math.min(start.y, current.y);
        const maxX = Math.max(start.x, current.x);
        const maxY = Math.max(start.y, current.y);
        const px = (minX - this.viewport.originX) * cellSize;
        const py = (minY - this.viewport.originY) * cellSize;
        const rectWidth = (maxX - minX + 1) * cellSize;
        const rectHeight = (maxY - minY + 1) * cellSize;
        ctx.save();
        ctx.fillStyle = 'rgba(64, 220, 255, 0.18)';
        ctx.strokeStyle = 'rgba(64, 220, 255, 0.85)';
        ctx.lineWidth = Math.max(1, Math.min(3, cellSize * 0.12));
        const dashLength = Math.max(4, cellSize * 0.6);
        ctx.setLineDash([dashLength, dashLength * 0.6]);
        ctx.fillRect(px, py, rectWidth, rectHeight);
        ctx.strokeRect(px + 0.5, py + 0.5, rectWidth, rectHeight);
        ctx.restore();
      }

      this.needsRedraw = false;
    }

    readCellColor() {
      if (!this.root || typeof window === 'undefined' || typeof window.getComputedStyle !== 'function') {
        return DEFAULT_CELL_COLOR;
      }
      try {
        const styles = window.getComputedStyle(this.root);
        const value = styles.getPropertyValue('--gol-cell-color');
        return value && value.trim() ? value.trim() : DEFAULT_CELL_COLOR;
      } catch (error) {
        return DEFAULT_CELL_COLOR;
      }
    }

    updateCellColor(force = false) {
      const next = this.readCellColor();
      if (!force && next === this.cellColor) {
        return;
      }
      this.cellColor = next;
      this.requestRedraw();
    }

    observeThemeChanges() {
      this.updateCellColor(true);
      if (typeof MutationObserver !== 'function' || typeof document === 'undefined' || !document.body) {
        return;
      }
      if (this.themeObserver && typeof this.themeObserver.disconnect === 'function') {
        this.themeObserver.disconnect();
      }
      this.themeObserver = new MutationObserver(() => {
        this.updateCellColor();
      });
      this.themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class', 'data-theme'] });
    }
  }

  onReady(() => {
    const root = document.getElementById('gameOfLife');
    if (!root) {
      return;
    }
    const canvas = document.getElementById('gameOfLifeCanvas');
    const playPauseButton = document.getElementById('gameOfLifePlayPauseButton');
    const stepButton = document.getElementById('gameOfLifeStepButton');
    const rewindButton = document.getElementById('gameOfLifeRewindButton');
    const fastForwardButton = document.getElementById('gameOfLifeFastForwardButton');
    const clearButton = document.getElementById('gameOfLifeClearButton');
    const speedSlider = document.getElementById('gameOfLifeSpeedSlider');
    const zoomSlider = document.getElementById('gameOfLifeZoomSlider');
    const generationValue = document.getElementById('gameOfLifeGenerationValue');
    const populationValue = document.getElementById('gameOfLifePopulationValue');
    const randomWidthInput = document.getElementById('gameOfLifeRandomWidth');
    const randomHeightInput = document.getElementById('gameOfLifeRandomHeight');
    const randomDensitySlider = document.getElementById('gameOfLifeRandomDensity');
    const randomDensityValue = document.getElementById('gameOfLifeRandomDensityValue');
    const randomReplaceButton = document.getElementById('gameOfLifeRandomReplace');
    const randomAddButton = document.getElementById('gameOfLifeRandomAdd');
    const patternSelect = document.getElementById('gameOfLifePatternSelect');
    const patternDescription = document.getElementById('gameOfLifePatternDescription');
    const patternEmpty = document.getElementById('gameOfLifePatternEmpty');
    const selectionButton = document.getElementById('gameOfLifeSelectButton');
    const selectionPanel = document.getElementById('gameOfLifeSelectionPanel');
    const selectionForm = document.getElementById('gameOfLifeSelectionForm');
    const selectionNameInput = document.getElementById('gameOfLifeSelectionName');
    const selectionCancelButton = document.getElementById('gameOfLifeSelectionCancel');
    const menu = document.getElementById('gameOfLifeMenu');
    const menuToggle = document.getElementById('gameOfLifeMenuToggle');
    const menuHeader = document.getElementById('gameOfLifeMenuHeader');

    const instance = new GameOfLifeArcade({
      root,
      canvas,
      playPauseButton,
      stepButton,
      rewindButton,
      fastForwardButton,
      clearButton,
      speedSlider,
      zoomSlider,
      generationValue,
      populationValue,
      randomWidthInput,
      randomHeightInput,
      randomDensitySlider,
      randomDensityValue,
      randomReplaceButton,
      randomAddButton,
      patternSelect,
      patternDescription,
      patternEmpty,
      selectionButton,
      selectionPanel,
      selectionForm,
      selectionNameInput,
      selectionCancelButton,
      menu,
      menuToggle,
      menuHeader
    });

    instance.init();
    window.gameOfLifeArcade = instance;

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('beforeunload', () => {
        try {
          instance.flushAutosave();
        } catch (error) {
          // ignore autosave flush errors on unload
        }
      });
    }
  });

  window.GameOfLifeArcade = GameOfLifeArcade;
})();
