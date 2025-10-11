(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/game-of-life.json';
  const PATTERNS_PATH = 'resources/patterns/game-of-life.json';
  const RANDOM_SEED_STORAGE_KEY = 'atom2univers.arcade.gameOfLife.seed';

  const DEFAULT_CONFIG = Object.freeze({
    simulation: Object.freeze({
      tickMs: 200,
      fastForwardMultiplier: 4,
      historyLimit: 240
    }),
    viewport: Object.freeze({
      baseCellSize: 22,
      minCellSize: 6,
      maxCellSize: 64,
      gridFadeStart: 12,
      gridFadeEnd: 28,
      paddingCells: 2
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

    return Object.freeze({ simulation, viewport, random });
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
      this.menu = menu;
      this.menuToggle = menuToggle;
      this.menuHeader = menuHeader;

      this.config = DEFAULT_CONFIG;
      this.patterns = [];
      this.selectedPatternId = null;

      this.dpr = window.devicePixelRatio || 1;
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
        lastCellKey: null
      };
      this.panState = {
        active: false,
        lastX: 0,
        lastY: 0
      };

      this.spacePressed = false;
      this.pointerId = null;
      this.randomSeed = loadSeed();
      this.resizeObserver = null;
      this.frameId = null;
      this.needsRedraw = true;
      this.canvasRect = { width: 0, height: 0 };
      this.boundHandleWindowResize = null;

      this.boundHandleFrame = this.handleFrame.bind(this);
      this.boundHandleVisibility = this.handleVisibilityChange.bind(this);
      this.boundHandleMenuPointerMove = this.handleMenuPointerMove.bind(this);
      this.boundHandleMenuPointerUp = this.handleMenuPointerUp.bind(this);

      this.menuDragState = {
        active: false,
        pointerId: null,
        offsetX: 0,
        offsetY: 0
      };

      this.stepCandidates = new Map();
      this.stepCellBuffer = { x: 0, y: 0 };
      this.renderCellBuffer = { x: 0, y: 0 };
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
      this.patterns = rawPatterns
        .map(pattern => this.normalizePattern(pattern))
        .filter(Boolean);

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
      this.updateUI();
      this.observeResize();
      this.state.active = false;
      this.needsRedraw = true;
      this.render();
      this.frameId = requestAnimationFrame(this.boundHandleFrame);
      document.addEventListener('visibilitychange', this.boundHandleVisibility);
    }

    destroy() {
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
    }

    onEnter() {
      this.state.active = true;
      this.state.accumulator = 0;
      this.state.lastFrameTime = performance.now();
      if (!this.frameId) {
        this.frameId = requestAnimationFrame(this.boundHandleFrame);
      }
    }

    onLeave() {
      this.state.active = false;
      this.pause();
    }

    setupUI() {
      if (this.speedSlider) {
        this.speedSlider.value = String(Math.round(this.state.speedMs));
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
          const value = clamp(Number(this.speedSlider.value) || this.state.speedMs, 30, 2000);
          this.setSpeed(value);
        });
      }
      if (this.zoomSlider) {
        this.zoomSlider.addEventListener('input', () => {
          const value = clamp(Number(this.zoomSlider.value) || this.viewport.cellSize, this.viewport.minCellSize, this.viewport.maxCellSize);
          this.setZoom(value);
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
      }

      if (this.canvas) {
        this.canvas.addEventListener('pointerdown', event => this.handlePointerDown(event));
        this.canvas.addEventListener('pointermove', event => this.handlePointerMove(event));
        window.addEventListener('pointerup', event => this.handlePointerUp(event));
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

      this.frameId = requestAnimationFrame(this.boundHandleFrame);
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

      if (!this.patterns.length) {
        this.patternSelect.disabled = true;
        this.patternSelect.value = '';
        this.selectedPatternId = null;
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
      this.patterns.forEach(pattern => {
        const option = document.createElement('option');
        option.value = pattern.id;
        const fallbackName = pattern.id.replace(/([A-Z])/g, ' $1').trim();
        if (pattern.nameKey) {
          option.dataset.i18n = pattern.nameKey;
        }
        option.textContent = translate(pattern.nameKey, fallbackName);
        if (pattern.id === this.selectedPatternId) {
          option.selected = true;
          hasSelectedPattern = true;
        }
        this.patternSelect.append(option);
      });

      if (!hasSelectedPattern) {
        this.selectedPatternId = null;
        this.patternSelect.value = '';
      }

      this.updatePatternDescription();
    }

    updatePatternDescription() {
      if (!this.patternDescription) {
        return;
      }
      if (!this.selectedPatternId) {
        this.patternDescription.hidden = true;
        this.patternDescription.textContent = '';
        delete this.patternDescription.dataset.i18n;
        return;
      }
      const pattern = this.patterns.find(entry => entry.id === this.selectedPatternId);
      if (!pattern) {
        this.patternDescription.hidden = true;
        this.patternDescription.textContent = '';
        delete this.patternDescription.dataset.i18n;
        return;
      }
      const description = translate(pattern.descriptionKey, '');
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
      if (this.zoomSlider) {
        this.zoomSlider.value = String(Math.round(this.viewport.cellSize));
      }
      this.needsRedraw = true;
    }

    play() {
      this.state.running = true;
      this.updateUI();
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
      this.pause();
      this.updateUI();
    }

    setSpeed(value) {
      this.state.speedMs = clamp(value, 30, 2000);
      if (this.speedSlider) {
        this.speedSlider.value = String(Math.round(this.state.speedMs));
      }
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
      const dpr = window.devicePixelRatio || 1;
      this.canvas.width = Math.round(safeWidth * dpr);
      this.canvas.height = Math.round(safeHeight * dpr);
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.needsRedraw = true;
    }

    toggleMenu(forceState) {
      if (!this.menu) {
        return;
      }
      const isExpanded = this.menu.dataset.expanded !== 'false';
      const nextState = typeof forceState === 'boolean' ? forceState : !isExpanded;
      this.menu.dataset.expanded = nextState ? 'true' : 'false';
      this.updateMenuToggleLabel();
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

    handlePointerDown(event) {
      if (!this.canvas || event.pointerType === 'touch') {
        this.canvas.setPointerCapture?.(event.pointerId);
      }
      this.pointerId = event.pointerId;
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
    }

    handlePointerMove(event) {
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
      this.needsRedraw = true;
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
      const pattern = this.patterns.find(entry => entry.id === this.selectedPatternId);
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
      const width = clamp(Number(this.randomWidthInput?.value) || this.config.random.defaultWidth, 5, this.config.random.maxWidth);
      const height = clamp(Number(this.randomHeightInput?.value) || this.config.random.defaultHeight, 5, this.config.random.maxHeight);
      const densityPercent = clamp(Number(this.randomDensitySlider?.value) || Math.round(this.config.random.defaultDensity * 100), 0, 100);
      const density = densityPercent / 100;
      const area = Math.max(1, Math.round(width * height * density));
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

    getSimulationBounds() {
      const visible = this.getVisibleBounds();
      const padding = Number.isFinite(this.viewport.paddingCells) ? this.viewport.paddingCells : 2;
      return {
        minX: visible.minX - padding,
        maxX: visible.maxX + padding,
        minY: visible.minY - padding,
        maxY: visible.maxY + padding
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
      ctx.fillStyle = 'rgba(120, 200, 255, 0.85)';
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

      this.needsRedraw = false;
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
      menu,
      menuToggle,
      menuHeader
    });

    instance.init();
    window.gameOfLifeArcade = instance;
  });

  window.GameOfLifeArcade = GameOfLifeArcade;
})();
