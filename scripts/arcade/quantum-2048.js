(function () {
  const GLOBAL_CONFIG =
    typeof window !== 'undefined'
      && window.GLOBAL_CONFIG
      && typeof window.GLOBAL_CONFIG === 'object'
      && window.GLOBAL_CONFIG.arcade
      && typeof window.GLOBAL_CONFIG.arcade === 'object'
      ? window.GLOBAL_CONFIG.arcade.quantum2048
      : null;

  const DEFAULT_CONFIG = {
    gridSizes: [3, 4, 5, 6],
    targetValues: [16, 32, 64, 128, 256, 512, 1024, 2048],
    defaultGridSize: 4,
    recommendedTargetBySize: {
      3: 64,
      4: 256,
      5: 1024,
      6: 2048
    },
    targetPoolsBySize: {
      3: [16, 32, 64],
      4: [64, 128, 256, 512],
      5: [128, 256, 512, 1024],
      6: [128, 256, 512, 1024, 2048]
    },
    randomizeGames: true,
    spawnValues: [2, 4],
    spawnWeights: [0.9, 0.1]
  };

  const DEFAULT_REWARD_CONFIG = {
    gachaTickets: {
      minRange: { min: 1, max: 4 },
      maxRange: { min: 25, max: 50 }
    }
  };

  const UNIVERSE_TRANSITION_DELAY_MS = 850;
  const UNIVERSE_DEFEAT_EXTRA_DELAY_MS = 250;
  const PARALLEL_UNIVERSE_STORAGE_KEY = 'atom2univers.quantum2048.parallelUniverses';

  function readStoredParallelUniverses(defaultValue = 0) {
    if (typeof window === 'undefined' || !window.localStorage) {
      return defaultValue;
    }
    try {
      const raw = window.localStorage.getItem(PARALLEL_UNIVERSE_STORAGE_KEY);
      if (raw == null) {
        return defaultValue;
      }
      const parsed = Number.parseInt(raw, 10);
      return Number.isFinite(parsed) && parsed >= 0 ? parsed : defaultValue;
    } catch (error) {
      return defaultValue;
    }
  }

  function writeStoredParallelUniverses(value) {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    try {
      const normalized = Math.max(0, Number.parseInt(value, 10) || 0);
      window.localStorage.setItem(PARALLEL_UNIVERSE_STORAGE_KEY, String(normalized));
    } catch (error) {
      // Ignore storage errors (e.g., private mode)
    }
  }

  function toUniquePositiveIntegers(list) {
    if (!Array.isArray(list)) {
      return [];
    }
    const unique = new Set();
    list.forEach(value => {
      const parsed = Number.parseInt(value, 10);
      if (Number.isFinite(parsed) && parsed > 0) {
        unique.add(parsed);
      }
    });
    return Array.from(unique).sort((a, b) => a - b);
  }

  function sanitizeTicketRange(range, fallback) {
    const fallbackMin = Math.max(0, Math.floor(Number(fallback?.min) || 0));
    const fallbackMax = Math.max(fallbackMin, Math.floor(Number(fallback?.max) || fallbackMin));
    if (!range || typeof range !== 'object') {
      return { min: fallbackMin, max: fallbackMax };
    }
    const rawMin = Number(range.min ?? range.minimum ?? range.from);
    const rawMax = Number(range.max ?? range.maximum ?? range.to);
    const min = Number.isFinite(rawMin) ? Math.max(0, Math.floor(rawMin)) : fallbackMin;
    const maxCandidate = Number.isFinite(rawMax) ? Math.max(0, Math.floor(rawMax)) : fallbackMax;
    const max = Math.max(min, maxCandidate);
    return { min, max };
  }

  function sanitizeRewardsConfig(rawRewards) {
    const rewards = rawRewards && typeof rawRewards === 'object' ? rawRewards : {};
    const gachaSource = rewards.gachaTickets && typeof rewards.gachaTickets === 'object'
      ? rewards.gachaTickets
      : {};
    const defaults = DEFAULT_REWARD_CONFIG.gachaTickets;
    const minRange = sanitizeTicketRange(gachaSource.minRange, defaults.minRange);
    const maxRange = sanitizeTicketRange(gachaSource.maxRange, defaults.maxRange);
    return {
      gachaTickets: {
        minRange,
        maxRange
      }
    };
  }

  function sanitizeConfig() {
    const config = GLOBAL_CONFIG && typeof GLOBAL_CONFIG === 'object' ? GLOBAL_CONFIG : {};
    const gridSizes = toUniquePositiveIntegers(config.gridSizes);
    const targetValues = toUniquePositiveIntegers(config.targetValues);
    const spawnValues = toUniquePositiveIntegers(config.spawnValues);
    const spawnWeights = Array.isArray(config.spawnWeights)
      ? config.spawnWeights.map(weight => {
          const num = Number(weight);
          return Number.isFinite(num) && num >= 0 ? num : 0;
        })
      : [];

    const baseTargetValues = targetValues.length ? targetValues : DEFAULT_CONFIG.targetValues;
    const targetValueSet = new Set(baseTargetValues);
    const poolSource = config.targetPoolsBySize && typeof config.targetPoolsBySize === 'object'
      ? config.targetPoolsBySize
      : DEFAULT_CONFIG.targetPoolsBySize;
    const targetPoolsBySize = {};
    Object.keys(poolSource).forEach(key => {
      const size = Number.parseInt(key, 10);
      const pool = Array.isArray(poolSource[key]) ? poolSource[key] : [];
      const sanitizedPool = toUniquePositiveIntegers(pool);
      if (!Number.isFinite(size) || size <= 0 || !sanitizedPool.length) {
        return;
      }
      targetPoolsBySize[size] = sanitizedPool;
      sanitizedPool.forEach(value => targetValueSet.add(value));
    });

    if (!Object.keys(targetPoolsBySize).length) {
      Object.keys(DEFAULT_CONFIG.targetPoolsBySize).forEach(key => {
        const size = Number.parseInt(key, 10);
        const pool = DEFAULT_CONFIG.targetPoolsBySize[key];
        if (!Number.isFinite(size) || size <= 0 || !Array.isArray(pool)) {
          return;
        }
        const sanitizedPool = toUniquePositiveIntegers(pool);
        if (!sanitizedPool.length) {
          return;
        }
        targetPoolsBySize[size] = sanitizedPool;
        sanitizedPool.forEach(value => targetValueSet.add(value));
      });
    }

    const recommendedSource = config.recommendedTargetBySize && typeof config.recommendedTargetBySize === 'object'
      ? config.recommendedTargetBySize
      : DEFAULT_CONFIG.recommendedTargetBySize;
    const recommendedTargetBySize = {};
    Object.keys(recommendedSource).forEach(key => {
      const size = Number.parseInt(key, 10);
      const value = Number.parseInt(recommendedSource[key], 10);
      if (Number.isFinite(size) && size > 0 && Number.isFinite(value) && value > 0) {
        recommendedTargetBySize[size] = value;
      }
    });

    const normalizedTargetValues = Array.from(targetValueSet).sort((a, b) => a - b);
    Object.keys(recommendedTargetBySize).forEach(key => {
      const size = Number.parseInt(key, 10);
      const allowedPool = targetPoolsBySize[size] || normalizedTargetValues;
      if (!allowedPool.includes(recommendedTargetBySize[size])) {
        if (allowedPool.length) {
          recommendedTargetBySize[size] = allowedPool[allowedPool.length - 1];
        } else {
          delete recommendedTargetBySize[size];
        }
      }
    });

    return {
      gridSizes: gridSizes.length ? gridSizes : DEFAULT_CONFIG.gridSizes,
      targetValues: normalizedTargetValues,
      defaultGridSize: DEFAULT_CONFIG.gridSizes.includes(config.defaultGridSize)
        ? config.defaultGridSize
        : DEFAULT_CONFIG.defaultGridSize,
      recommendedTargetBySize,
      targetPoolsBySize,
      randomizeGames: config.randomizeGames !== false,
      spawnValues: spawnValues.length ? spawnValues : DEFAULT_CONFIG.spawnValues,
      spawnWeights: spawnWeights.length ? spawnWeights : DEFAULT_CONFIG.spawnWeights,
      rewards: sanitizeRewardsConfig(config.rewards)
    };
  }

  const CONFIG = sanitizeConfig();

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined' && typeof window.translateOrDefault === 'function') {
      return window.translateOrDefault(key, fallback, params);
    }
    return fallback;
  }

  function formatInteger(value) {
    if (typeof formatIntegerLocalized === 'function') {
      return formatIntegerLocalized(value);
    }
    try {
      return Number(value || 0).toLocaleString();
    } catch (error) {
      return String(value ?? 0);
    }
  }

  function normalizeDirection(direction) {
    switch (direction) {
      case 'up':
      case 'down':
      case 'left':
      case 'right':
        return direction;
      default:
        return null;
    }
  }

  function clamp(value, min, max) {
    if (!Number.isFinite(value)) {
      return min;
    }
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  function lerp(start, end, t) {
    const ratio = Number.isFinite(t) ? clamp(t, 0, 1) : 0;
    const a = Number.isFinite(Number(start)) ? Number(start) : 0;
    const b = Number.isFinite(Number(end)) ? Number(end) : 0;
    return a + (b - a) * ratio;
  }

  function randomIntInRange(min, max) {
    const lower = Math.max(0, Math.ceil(Number(min) || 0));
    const upper = Math.max(lower, Math.floor(Number(max) || 0));
    if (upper <= lower) {
      return lower;
    }
    return lower + Math.floor(Math.random() * (upper - lower + 1));
  }

  class Quantum2048Game {
    constructor(options = {}) {
      const {
        boardElement,
        tilesContainer,
        backgroundContainer,
        sizeSelect,
        targetSelect,
        scoreElement,
        bestElement,
        movesElement,
        goalElement,
        parallelUniverseElement,
        overlayElement,
        overlayMessageElement,
        overlayButtonElement
      } = options;

      this.config = CONFIG;
      this.boardElement = boardElement || null;
      this.tilesContainer = tilesContainer || null;
      this.backgroundContainer = backgroundContainer || null;
      this.sizeSelect = sizeSelect || null;
      this.targetSelect = targetSelect || null;
      this.scoreElement = scoreElement || null;
      this.bestElement = bestElement || null;
      this.movesElement = movesElement || null;
      this.goalElement = goalElement || null;
      this.parallelUniverseElement = parallelUniverseElement || null;
      this.overlayElement = overlayElement || null;
      this.overlayMessageElement = overlayMessageElement || null;
      this.overlayButtonElement = overlayButtonElement || null;
      this.rewards = this.config.rewards || DEFAULT_REWARD_CONFIG;

      this.size = this.normalizeSize(this.config.defaultGridSize);
      this.target = this.normalizeTarget(this.config.recommendedTargetBySize[this.size] ?? this.config.targetValues[0]);
      this.board = [];
      this.score = 0;
      this.moves = 0;
      this.bestTile = 0;
      this.hasWon = false;
      this.gameOver = false;
      this.active = false;
      this.parallelUniverseCount = readStoredParallelUniverses(0);
      this.transitionTimeout = null;
      this.autosaveTimer = null;

      this.handleKeydown = this.handleKeydown.bind(this);
      this.handleSizeChange = this.handleSizeChange.bind(this);
      this.handleTargetChange = this.handleTargetChange.bind(this);
      this.handleOverlayAction = this.handleOverlayAction.bind(this);
      this.handleResize = this.handleResize.bind(this);
      this.handleTouchStart = this.handleTouchStart.bind(this);
      this.handleTouchMove = this.handleTouchMove.bind(this);
      this.handleTouchEnd = this.handleTouchEnd.bind(this);
      this.handleTouchCancel = this.handleTouchCancel.bind(this);
      this.touchIdentifier = null;
      this.touchStartX = 0;
      this.touchStartY = 0;
      this.touchLastX = 0;
      this.touchLastY = 0;
      this.touchMoved = false;
      this.touchStartOptions = { passive: true };
      this.touchMoveOptions = { passive: false };
      this.resizeFrame = null;

      this.setupControls();
      this.updateParallelUniverseDisplay();
      const restored = this.restoreAutosavedState();
      if (!restored) {
        this.startNewGame({ randomize: this.config.randomizeGames !== false });
      }
    }

    normalizeSize(value) {
      const parsed = Number.parseInt(value, 10);
      if (!Number.isFinite(parsed)) {
        return this.config.defaultGridSize;
      }
      if (this.config.gridSizes.includes(parsed)) {
        return parsed;
      }
      const closest = this.config.gridSizes.reduce((prev, current) => {
        if (prev == null) {
          return current;
        }
        return Math.abs(current - parsed) < Math.abs(prev - parsed) ? current : prev;
      }, null);
      return closest ?? this.config.defaultGridSize;
    }

    normalizeTarget(target, forSize) {
      const size = Number.isFinite(forSize) ? forSize : this.size;
      const allowedTargets = this.getAllowedTargets(size);
      const parsed = Number.parseInt(target, 10);
      if (Number.isFinite(parsed) && allowedTargets.includes(parsed)) {
        return parsed;
      }
      const recommended = this.getRecommendedTarget(size);
      if (Number.isFinite(recommended)) {
        return recommended;
      }
      return allowedTargets[0] ?? this.config.targetValues[0];
    }

    getAllowedTargets(size) {
      if (!Number.isFinite(size)) {
        return this.config.targetValues.slice();
      }
      const pool = this.config.targetPoolsBySize?.[size];
      if (Array.isArray(pool) && pool.length) {
        return pool.filter(value => this.config.targetValues.includes(value));
      }
      return this.config.targetValues.slice();
    }

    getRecommendedTarget(size) {
      if (!Number.isFinite(size)) {
        return null;
      }
      const allowed = this.getAllowedTargets(size);
      const mapped = this.config.recommendedTargetBySize[size];
      if (Number.isFinite(mapped) && allowed.includes(mapped)) {
        return mapped;
      }
      return allowed.length ? allowed[allowed.length - 1] : null;
    }

    pickRandomSizeAndTarget() {
      const sizes = Array.isArray(this.config.gridSizes) ? this.config.gridSizes : [];
      if (!sizes.length) {
        return { size: this.config.defaultGridSize, target: this.getRecommendedTarget(this.config.defaultGridSize) };
      }
      const size = sizes[Math.floor(Math.random() * sizes.length)];
      const allowedTargets = this.getAllowedTargets(size);
      const targetPool = allowedTargets.length ? allowedTargets : this.config.targetValues;
      const targetIndex = Math.floor(Math.random() * targetPool.length);
      const target = targetPool[targetIndex] ?? this.getRecommendedTarget(size) ?? this.config.targetValues[0];
      return { size, target };
    }

    calculateBoardSize() {
      if (typeof window === 'undefined' || !this.boardElement) {
        return null;
      }
      const viewportWidth = Math.max(window.innerWidth || 0, document.documentElement?.clientWidth || 0);
      const viewportHeight = Math.max(window.innerHeight || 0, document.documentElement?.clientHeight || 0);
      let availableWidth = viewportWidth;
      let availableHeight = viewportHeight;
      const parent = this.boardElement.parentElement;
      if (parent && parent.getBoundingClientRect) {
        const rect = parent.getBoundingClientRect();
        if (Number.isFinite(rect?.width) && rect.width > 0) {
          availableWidth = rect.width;
        }
        if (Number.isFinite(rect?.height) && rect.height > 0) {
          availableHeight = rect.height;
        }
        if (window.getComputedStyle) {
          const styles = window.getComputedStyle(parent);
          const paddingLeft = Number.parseFloat(styles.paddingLeft) || 0;
          const paddingRight = Number.parseFloat(styles.paddingRight) || 0;
          const paddingTop = Number.parseFloat(styles.paddingTop) || 0;
          const paddingBottom = Number.parseFloat(styles.paddingBottom) || 0;
          availableWidth -= paddingLeft + paddingRight;
          availableHeight -= paddingTop + paddingBottom;
        }
      }
      if (!Number.isFinite(availableWidth) || availableWidth <= 0) {
        availableWidth = viewportWidth;
      }
      if (!Number.isFinite(availableHeight) || availableHeight <= 0) {
        availableHeight = viewportHeight;
      }
      availableWidth = Math.max(availableWidth, 0);
      availableHeight = Math.max(availableHeight, 0);

      const candidates = [availableWidth, availableHeight];
      if (Number.isFinite(viewportWidth) && viewportWidth > 0) {
        candidates.push(viewportWidth * 0.92);
      }
      if (Number.isFinite(viewportHeight) && viewportHeight > 0) {
        candidates.push(viewportHeight * 0.92);
      }
      const positiveCandidates = candidates.filter(value => Number.isFinite(value) && value > 0);
      if (!positiveCandidates.length) {
        return null;
      }
      const baseSize = Math.min(...positiveCandidates);
      return Math.max(Math.round(baseSize), 0);
    }

    getSpawnDistribution() {
      const values = this.config.spawnValues;
      const weights = this.config.spawnWeights;
      const pairs = values.map((value, index) => {
        const weight = Number.isFinite(weights[index]) ? weights[index] : 0;
        return { value, weight };
      }).filter(entry => entry.value > 0 && entry.weight >= 0);
      if (!pairs.length) {
        return [{ value: 2, weight: 1 }];
      }
      const total = pairs.reduce((sum, entry) => sum + entry.weight, 0);
      if (total <= 0) {
        return pairs.map(entry => ({ value: entry.value, weight: 1 }));
      }
      return pairs.map(entry => ({ value: entry.value, weight: entry.weight / total }));
    }

    setupControls() {
      if (this.sizeSelect) {
        this.populateSizeOptions();
        this.sizeSelect.addEventListener('change', this.handleSizeChange);
      }
      if (this.targetSelect) {
        this.populateTargetOptions();
        this.targetSelect.addEventListener('change', this.handleTargetChange);
      }
      if (this.overlayButtonElement) {
        this.overlayButtonElement.addEventListener('click', this.handleOverlayAction);
      }
    }

    populateSizeOptions() {
      if (!this.sizeSelect) {
        return;
      }
      this.sizeSelect.innerHTML = '';
      this.config.gridSizes.forEach(size => {
        const option = document.createElement('option');
        option.value = String(size);
        option.textContent = `${size}×${size}`;
        this.sizeSelect.appendChild(option);
      });
      this.sizeSelect.value = String(this.size);
    }

    populateTargetOptions() {
      if (!this.targetSelect) {
        return;
      }
      const recommended = this.getRecommendedTarget(this.size);
      const previousValue = this.targetSelect.value;
      this.targetSelect.innerHTML = '';
      const targetPool = this.getAllowedTargets(this.size);
      const values = targetPool.length ? targetPool : this.config.targetValues;
      values.forEach(value => {
        const option = document.createElement('option');
        option.value = String(value);
        const formatted = formatInteger(value);
        option.textContent = formatted;
        this.targetSelect.appendChild(option);
      });
      if (previousValue && this.config.targetValues.includes(Number.parseInt(previousValue, 10))) {
        this.targetSelect.value = previousValue;
      } else {
        this.targetSelect.value = String(this.target);
      }
    }

    handleSizeChange(event) {
      const value = Number.parseInt(event?.target?.value, 10);
      const normalized = this.normalizeSize(value);
      const recommended = this.getRecommendedTarget(normalized);
      const nextTarget = this.normalizeTarget(recommended ?? this.target, normalized);
      this.startNewGame({ size: normalized, target: nextTarget, randomize: false });
    }

    handleTargetChange(event) {
      const value = Number.parseInt(event?.target?.value, 10);
      const normalized = this.normalizeTarget(value, this.size);
      this.startNewGame({ target: normalized, size: this.size, randomize: false });
    }

    handleOverlayAction() {
      this.startNewGame({
        size: this.size,
        target: this.target,
        randomize: this.config.randomizeGames !== false
      });
    }

    clearTransitionTimeout() {
      if (this.transitionTimeout != null && typeof window !== 'undefined') {
        window.clearTimeout(this.transitionTimeout);
      }
      this.transitionTimeout = null;
    }

    updateParallelUniverseDisplay() {
      if (this.parallelUniverseElement) {
        this.parallelUniverseElement.textContent = formatInteger(this.parallelUniverseCount);
      }
    }

    adjustParallelUniverse(delta = 0) {
      if (!Number.isFinite(delta) || delta === 0) {
        this.updateParallelUniverseDisplay();
        return;
      }
      this.parallelUniverseCount = Math.max(0, this.parallelUniverseCount + delta);
      writeStoredParallelUniverses(this.parallelUniverseCount);
      this.updateParallelUniverseDisplay();
    }

    scheduleUniverseShift(delta, options = {}) {
      const { delay = UNIVERSE_TRANSITION_DELAY_MS } = options;
      this.adjustParallelUniverse(delta);
      const launchNext = () => {
        this.transitionTimeout = null;
        this.startNewGame({ randomize: true });
      };
      if (typeof window === 'undefined' || !Number.isFinite(delay) || delay <= 0) {
        launchNext();
        return;
      }
      this.clearTransitionTimeout();
      this.transitionTimeout = window.setTimeout(launchNext, delay);
    }

    getAutosaveApi() {
      if (typeof window === 'undefined') {
        return null;
      }
      const api = window.ArcadeAutosave;
      if (!api || typeof api.set !== 'function' || typeof api.get !== 'function') {
        return null;
      }
      return api;
    }

    sanitizeSavedState(raw) {
      if (!raw || typeof raw !== 'object') {
        return null;
      }
      const size = this.normalizeSize(raw.size);
      const boardSource = Array.isArray(raw.board) ? raw.board : [];
      if (boardSource.length !== size * size) {
        return null;
      }
      const board = boardSource.map(value => {
        const numeric = Number(value);
        return Number.isFinite(numeric) && numeric > 0 ? numeric : 0;
      });
      const target = this.normalizeTarget(raw.target, size);
      const scoreValue = Number(raw.score);
      const movesValue = Number(raw.moves);
      const bestTileValue = Number(raw.bestTile);
      const hasWon = raw.hasWon === true;
      const gameOver = raw.gameOver === true;
      const bestTileFromBoard = board.reduce((max, value) => {
        const numeric = Number(value);
        return Number.isFinite(numeric) && numeric > max ? numeric : max;
      }, 0);
      const score = Number.isFinite(scoreValue) ? Math.max(0, Math.floor(scoreValue)) : 0;
      const moves = Number.isFinite(movesValue) ? Math.max(0, Math.floor(movesValue)) : 0;
      const bestTile = Math.max(bestTileFromBoard, Number.isFinite(bestTileValue) ? bestTileValue : 0);
      return { size, board, target, score, moves, bestTile, hasWon, gameOver };
    }

    serializeState() {
      const board = Array.isArray(this.board) ? this.board.slice() : [];
      return {
        size: this.size,
        target: this.target,
        board,
        score: Number.isFinite(this.score) ? this.score : 0,
        moves: Number.isFinite(this.moves) ? this.moves : 0,
        bestTile: Number.isFinite(this.bestTile) ? this.bestTile : 0,
        hasWon: this.hasWon === true,
        gameOver: this.gameOver === true
      };
    }

    applyAutosavedState(state) {
      if (!state) {
        return false;
      }
      this.clearTransitionTimeout();
      this.size = state.size;
      this.target = state.target;
      this.board = state.board.slice();
      this.score = state.score;
      this.moves = state.moves;
      this.bestTile = state.bestTile;
      this.hasWon = state.hasWon;
      this.gameOver = state.gameOver;
      if (this.sizeSelect) {
        this.sizeSelect.value = String(this.size);
      }
      if (this.targetSelect) {
        this.populateTargetOptions();
        this.targetSelect.value = String(this.target);
      }
      this.hideOverlay();
      this.updateBoardGeometry(true);
      this.renderTiles();
      this.updateStats();
      return true;
    }

    persistAutosave() {
      const api = this.getAutosaveApi();
      if (!api) {
        return;
      }
      try {
        api.set('quantum2048', this.serializeState());
      } catch (error) {
        // Ignore autosave persistence issues
      }
    }

    scheduleAutosave() {
      if (typeof window === 'undefined') {
        return;
      }
      if (this.autosaveTimer != null) {
        window.clearTimeout(this.autosaveTimer);
      }
      this.autosaveTimer = window.setTimeout(() => {
        this.autosaveTimer = null;
        this.persistAutosave();
      }, 150);
    }

    restoreAutosavedState() {
      const api = this.getAutosaveApi();
      if (!api) {
        return false;
      }
      let saved = null;
      try {
        saved = api.get('quantum2048');
      } catch (error) {
        return false;
      }
      if (!saved) {
        return false;
      }
      const normalized = this.sanitizeSavedState(saved);
      if (!normalized) {
        try {
          api.set('quantum2048', null);
        } catch (error) {
          // Ignore cleanup failures
        }
        return false;
      }
      const applied = this.applyAutosavedState(normalized);
      if (applied) {
        this.scheduleAutosave();
      }
      return applied;
    }

    startNewGame({ size = this.size, target = this.target, randomize = false } = {}) {
      this.clearTransitionTimeout();
      let nextSize = size;
      let nextTarget = target;
      if (randomize) {
        const random = this.pickRandomSizeAndTarget();
        if (Number.isFinite(random?.size) && random.size > 0) {
          nextSize = random.size;
        }
        if (Number.isFinite(random?.target) && random.target > 0) {
          nextTarget = random.target;
        }
      }
      const normalizedSize = this.normalizeSize(nextSize);
      const normalizedTarget = this.normalizeTarget(nextTarget, normalizedSize);
      const sizeChanged = normalizedSize !== this.size;
      this.size = normalizedSize;
      this.target = normalizedTarget;
      this.board = new Array(this.size * this.size).fill(0);
      this.score = 0;
      this.moves = 0;
      this.bestTile = 0;
      this.hasWon = false;
      this.gameOver = false;

      if (this.sizeSelect) {
        this.sizeSelect.value = String(this.size);
      }
      if (this.targetSelect) {
        this.populateTargetOptions();
        this.targetSelect.value = String(this.target);
      }

      this.hideOverlay();
      this.updateBoardGeometry(sizeChanged);
      this.renderTiles();
      const spawned = [];
      const first = this.spawnRandomTile();
      if (first != null) {
        spawned.push(first);
      }
      const second = this.spawnRandomTile();
      if (second != null) {
        spawned.push(second);
      }
      this.renderTiles({ spawned });
      this.updateStats();
      this.scheduleAutosave();
    }

    handleResize() {
      if (typeof window === 'undefined') {
        return;
      }
      if (this.resizeFrame) {
        window.cancelAnimationFrame(this.resizeFrame);
      }
      this.resizeFrame = window.requestAnimationFrame(() => {
        this.resizeFrame = null;
        this.updateBoardGeometry(false);
      });
    }

    updateBoardGeometry(forceRebuild = false) {
      if (!this.boardElement) {
        return;
      }
      const boardSize = this.calculateBoardSize();
      const layoutContainer = this.boardElement.parentElement;
      if (Number.isFinite(boardSize) && boardSize > 0) {
        this.boardElement.style.setProperty('--quantum2048-board-size', `${boardSize}px`);
        if (layoutContainer && layoutContainer.style) {
          layoutContainer.style.setProperty('--quantum2048-layout-width', `${boardSize}px`);
        }
      } else if (layoutContainer && layoutContainer.style) {
        layoutContainer.style.removeProperty('--quantum2048-layout-width');
      }
      const gapRem = this.size >= 6 ? 0.32 : this.size === 5 ? 0.38 : 0.45;
      const fontScale = clamp(4 / (this.size + 0.25), 0.52, 1.2);
      this.boardElement.style.setProperty('--quantum2048-grid-size', String(this.size));
      this.boardElement.style.setProperty('--quantum2048-cell-gap', `${gapRem}rem`);
      this.boardElement.style.setProperty('--quantum2048-font-scale', fontScale.toFixed(2));
      if (!this.backgroundContainer) {
        return;
      }
      if (!forceRebuild && this.backgroundContainer.childElementCount === this.board.length) {
        return;
      }
      this.backgroundContainer.innerHTML = '';
      const totalCells = this.size * this.size;
      for (let index = 0; index < totalCells; index += 1) {
        const cell = document.createElement('div');
        cell.className = 'quantum2048-cell';
        this.backgroundContainer.appendChild(cell);
      }
    }

    spawnRandomTile() {
      const emptyIndices = [];
      for (let index = 0; index < this.board.length; index += 1) {
        if (this.board[index] === 0) {
          emptyIndices.push(index);
        }
      }
      if (!emptyIndices.length) {
        return null;
      }
      const distribution = this.getSpawnDistribution();
      let random = Math.random();
      let selectedValue = distribution[0]?.value ?? 2;
      for (let i = 0; i < distribution.length; i += 1) {
        const entry = distribution[i];
        random -= Number(entry.weight) || 0;
        if (random <= 0) {
          selectedValue = entry.value;
          break;
        }
      }
      const chosenIndex = emptyIndices[Math.floor(Math.random() * emptyIndices.length)];
      this.board[chosenIndex] = selectedValue;
      this.bestTile = Math.max(this.bestTile, selectedValue);
      return chosenIndex;
    }

    renderTiles({ spawned = [], merged = [] } = {}) {
      if (!this.tilesContainer) {
        return;
      }
      const spawnedSet = new Set(spawned);
      const mergedSet = new Set(merged);
      this.tilesContainer.innerHTML = '';
      for (let index = 0; index < this.board.length; index += 1) {
        const value = this.board[index];
        if (!value) {
          continue;
        }
        const row = Math.floor(index / this.size);
        const column = index % this.size;
        const tile = document.createElement('div');
        const valueClass = this.getTileClass(value);
        tile.className = `quantum2048-tile ${valueClass}`;
        if (spawnedSet.has(index)) {
          tile.classList.add('quantum2048-tile--spawned');
        }
        if (mergedSet.has(index)) {
          tile.classList.add('quantum2048-tile--merged');
        }
        tile.style.gridRowStart = String(row + 1);
        tile.style.gridColumnStart = String(column + 1);
        tile.textContent = formatInteger(value);
        tile.setAttribute('data-value', String(value));
        this.tilesContainer.appendChild(tile);
      }
    }

    getTileClass(value) {
      const known = [2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048];
      const closest = known.includes(value) ? value : null;
      if (closest) {
        return `quantum2048-tile--${closest}`;
      }
      return 'quantum2048-tile--super';
    }

    move(directionInput) {
      if (this.gameOver) {
        return false;
      }
      const direction = normalizeDirection(directionInput);
      if (!direction) {
        return false;
      }
      const result = this.applyMove(direction);
      if (!result || !result.moved) {
        return false;
      }
      this.score += result.scoreGain;
      this.moves += 1;
      this.bestTile = Math.max(this.bestTile, result.maxTile);
      const spawnedIndex = this.spawnRandomTile();
      const spawned = [];
      if (spawnedIndex != null) {
        spawned.push(spawnedIndex);
      }
      this.renderTiles({ spawned, merged: Array.from(result.mergedIndices) });
      this.updateStats();
      this.scheduleAutosave();
      if (!this.hasWon && this.bestTile >= this.target) {
        this.handleVictory();
      }
      if (!this.canMove()) {
        this.handleDefeat();
      }
      return true;
    }

    applyMove(direction) {
      const size = this.size;
      if (!Number.isFinite(size) || size <= 0) {
        return null;
      }
      const nextBoard = this.board.slice();
      let moved = false;
      let scoreGain = 0;
      const mergedIndices = new Set();
      let maxTile = 0;

      const processLine = (indices, reverse) => {
        const line = indices.map(index => this.board[index]);
        const working = reverse ? line.slice().reverse() : line.slice();
        const mergedResult = this.mergeLine(working);
        const finalLine = reverse ? mergedResult.line.slice().reverse() : mergedResult.line;
        finalLine.forEach((value, position) => {
          const boardIndex = indices[position];
          if (nextBoard[boardIndex] !== value) {
            moved = moved || value !== this.board[boardIndex];
            nextBoard[boardIndex] = value;
          } else if (value !== line[position]) {
            moved = true;
          }
          maxTile = Math.max(maxTile, value);
        });
        mergedResult.mergedPositions.forEach(pos => {
          const normalizedPosition = reverse ? indices.length - 1 - pos : pos;
          const boardIndex = indices[normalizedPosition];
          mergedIndices.add(boardIndex);
        });
        scoreGain += mergedResult.scoreGain;
      };

      for (let row = 0; row < size; row += 1) {
        const indices = [];
        for (let column = 0; column < size; column += 1) {
          indices.push(row * size + column);
        }
        if (direction === 'left') {
          processLine(indices, false);
        } else if (direction === 'right') {
          processLine(indices, true);
        }
      }

      for (let column = 0; column < size; column += 1) {
        const indices = [];
        for (let row = 0; row < size; row += 1) {
          indices.push(row * size + column);
        }
        if (direction === 'up') {
          processLine(indices, false);
        } else if (direction === 'down') {
          processLine(indices, true);
        }
      }

      const changed = moved || !this.boardsEqual(this.board, nextBoard);
      if (!changed) {
        return { moved: false };
      }
      this.board = nextBoard;
      return { moved: true, scoreGain, mergedIndices, maxTile };
    }

    mergeLine(line) {
      const size = line.length;
      const filtered = line.filter(value => value !== 0);
      const merged = [];
      const mergedPositions = [];
      let scoreGain = 0;
      for (let index = 0; index < filtered.length; index += 1) {
        const current = filtered[index];
        if (index < filtered.length - 1 && filtered[index + 1] === current) {
          const mergedValue = current * 2;
          merged.push(mergedValue);
          scoreGain += mergedValue;
          mergedPositions.push(merged.length - 1);
          index += 1;
        } else {
          merged.push(current);
        }
      }
      while (merged.length < size) {
        merged.push(0);
      }
      return {
        line: merged,
        mergedPositions,
        scoreGain
      };
    }

    boardsEqual(a, b) {
      if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) {
        return false;
      }
      for (let index = 0; index < a.length; index += 1) {
        if (a[index] !== b[index]) {
          return false;
        }
      }
      return true;
    }

    updateStats() {
      if (this.scoreElement) {
        this.scoreElement.textContent = formatInteger(this.score);
      }
      if (this.bestElement) {
        this.bestElement.textContent = formatInteger(this.bestTile);
      }
      if (this.movesElement) {
        this.movesElement.textContent = formatInteger(this.moves);
      }
      if (this.goalElement) {
        this.goalElement.textContent = formatInteger(this.target);
      }
      this.updateParallelUniverseDisplay();
    }

    getVictoryTicketRewardRange(target) {
      const targets = Array.isArray(this.config.targetValues)
        ? this.config.targetValues.slice().sort((a, b) => a - b)
        : [];
      if (!targets.length) {
        return null;
      }
      const rewardConfig = this.rewards?.gachaTickets;
      if (!rewardConfig || !rewardConfig.minRange || !rewardConfig.maxRange) {
        return null;
      }
      const minRange = rewardConfig.minRange;
      const maxRange = rewardConfig.maxRange;
      const minLow = Math.max(0, Math.floor(Number(minRange.min) || 0));
      const minHigh = Math.max(minLow, Math.floor(Number(minRange.max) || minLow));
      const maxLow = Math.max(0, Math.floor(Number(maxRange.min) || 0));
      const maxHigh = Math.max(maxLow, Math.floor(Number(maxRange.max) || maxLow));

      let ratio = targets.length === 1 ? 1 : 0;
      if (targets.length > 1) {
        const index = targets.indexOf(target);
        if (index >= 0) {
          ratio = index / (targets.length - 1);
        } else {
          let insertionIndex = targets.findIndex(value => target < value);
          if (insertionIndex <= 0) {
            ratio = 0;
          } else if (insertionIndex < 0) {
            ratio = 1;
          } else {
            const lowerValue = targets[insertionIndex - 1];
            const upperValue = targets[insertionIndex];
            const span = upperValue - lowerValue;
            const localRatio = span > 0 ? (target - lowerValue) / span : 0;
            ratio = (insertionIndex - 1 + clamp(localRatio, 0, 1)) / (targets.length - 1);
          }
        }
      }

      const safeRatio = clamp(ratio, 0, 1);
      const minTickets = Math.round(lerp(minLow, maxLow, safeRatio));
      const maxTickets = Math.round(lerp(minHigh, maxHigh, safeRatio));
      const lower = Math.max(0, Math.min(minTickets, maxTickets));
      const upper = Math.max(lower, Math.max(minTickets, maxTickets));
      return { min: lower, max: upper };
    }

    grantVictoryRewards() {
      const range = this.getVictoryTicketRewardRange(this.target);
      if (!range) {
        return null;
      }
      const min = Math.max(0, Math.floor(Number(range.min) || 0));
      const max = Math.max(min, Math.floor(Number(range.max) || min));
      if (max <= 0) {
        return null;
      }
      const rewardAmount = randomIntInRange(min, max);
      if (!Number.isFinite(rewardAmount) || rewardAmount <= 0) {
        return null;
      }
      let granted = rewardAmount;
      if (typeof gainGachaTickets === 'function') {
        granted = gainGachaTickets(rewardAmount, { unlockTicketStar: true });
      }
      if (granted > 0) {
        const ticketLabel = typeof formatTicketLabel === 'function'
          ? formatTicketLabel(granted)
          : `${formatInteger(granted)} tickets`;
        const message = translate('scripts.arcade.quantum2048.rewardToast', 'ObjectX reward: {tickets}', {
          tickets: ticketLabel
        });
        if (typeof showToast === 'function' && message) {
          showToast(message);
        }
        if (typeof updateArcadeTicketDisplay === 'function') {
          updateArcadeTicketDisplay();
        }
      }
      return { amount: granted, range };
    }

    handleVictory() {
      if (this.hasWon || this.gameOver) {
        return;
      }
      this.hasWon = true;
      this.gameOver = true;
      this.grantVictoryRewards();
      this.scheduleUniverseShift(1);
      this.scheduleAutosave();
    }

    handleDefeat() {
      if (this.gameOver) {
        return;
      }
      this.gameOver = true;
      this.hideOverlay();
      this.scheduleUniverseShift(-1, { delay: UNIVERSE_TRANSITION_DELAY_MS + UNIVERSE_DEFEAT_EXTRA_DELAY_MS });
      this.scheduleAutosave();
    }

    canMove() {
      if (this.board.some(value => value === 0)) {
        return true;
      }
      const size = this.size;
      for (let row = 0; row < size; row += 1) {
        for (let column = 0; column < size; column += 1) {
          const index = row * size + column;
          const value = this.board[index];
          if (column + 1 < size && this.board[row * size + column + 1] === value) {
            return true;
          }
          if (row + 1 < size && this.board[(row + 1) * size + column] === value) {
            return true;
          }
        }
      }
      return false;
    }

    showOverlay(message) {
      if (!this.overlayElement) {
        return;
      }
      if (this.overlayMessageElement) {
        this.overlayMessageElement.textContent = message;
      }
      this.overlayElement.hidden = false;
      this.overlayElement.removeAttribute('hidden');
    }

    hideOverlay() {
      if (!this.overlayElement) {
        return;
      }
      this.overlayElement.hidden = true;
      this.overlayElement.setAttribute('hidden', 'true');
    }

    isOverlayVisible() {
      return Boolean(this.overlayElement) && this.overlayElement.hidden === false;
    }

    handleKeydown(event) {
      if (!this.active) {
        return;
      }
      const key = event?.key;
      const code = event?.code;
      if (this.isOverlayVisible()) {
        if (key === 'Enter' || key === ' ' || code === 'Space') {
          event.preventDefault();
          this.startNewGame({
            size: this.size,
            target: this.target,
            randomize: this.config.randomizeGames !== false
          });
        }
        return;
      }
      let direction = null;
      switch (code) {
        case 'ArrowUp':
        case 'KeyW':
          direction = 'up';
          break;
        case 'ArrowDown':
        case 'KeyS':
          direction = 'down';
          break;
        case 'ArrowLeft':
        case 'KeyA':
          direction = 'left';
          break;
        case 'ArrowRight':
        case 'KeyD':
          direction = 'right';
          break;
        default:
          if (key === 'z' || key === 'Z') direction = 'up';
          if (key === 's' || key === 'S') direction = 'down';
          if (key === 'q' || key === 'Q') direction = 'left';
          if (key === 'd' || key === 'D') direction = 'right';
          break;
      }
      if (!direction) {
        return;
      }
      event.preventDefault();
      this.move(direction);
    }

    resetTouchTracking() {
      this.touchIdentifier = null;
      this.touchStartX = 0;
      this.touchStartY = 0;
      this.touchLastX = 0;
      this.touchLastY = 0;
      this.touchMoved = false;
    }

    handleTouchStart(event) {
      if (!this.active || this.isOverlayVisible()) {
        return;
      }
      if (!event || !event.touches || event.touches.length === 0) {
        return;
      }
      const touch = event.touches[0];
      if (!touch) {
        return;
      }
      this.touchIdentifier = Number.isFinite(touch.identifier) ? touch.identifier : null;
      this.touchStartX = Number.isFinite(touch.clientX) ? touch.clientX : 0;
      this.touchStartY = Number.isFinite(touch.clientY) ? touch.clientY : 0;
      this.touchLastX = this.touchStartX;
      this.touchLastY = this.touchStartY;
      this.touchMoved = false;
    }

    handleTouchMove(event) {
      if (this.touchIdentifier === null || !event || !event.touches) {
        return;
      }
      const touch = Array.from(event.touches).find(t => t.identifier === this.touchIdentifier)
        || event.touches[0];
      if (!touch) {
        return;
      }
      const currentX = Number.isFinite(touch.clientX) ? touch.clientX : this.touchLastX;
      const currentY = Number.isFinite(touch.clientY) ? touch.clientY : this.touchLastY;
      const deltaX = currentX - this.touchStartX;
      const deltaY = currentY - this.touchStartY;
      if (!this.touchMoved) {
        const threshold = 8;
        if (Math.abs(deltaX) > threshold || Math.abs(deltaY) > threshold) {
          this.touchMoved = true;
        }
      }
      if (this.touchMoved && typeof event.preventDefault === 'function') {
        event.preventDefault();
      }
      this.touchLastX = currentX;
      this.touchLastY = currentY;
    }

    handleTouchEnd(event) {
      if (this.touchIdentifier === null || !event) {
        this.resetTouchTracking();
        return;
      }
      const touchList = event.changedTouches || [];
      const touch = Array.from(touchList).find(t => t.identifier === this.touchIdentifier)
        || touchList[0];
      if (!touch) {
        this.resetTouchTracking();
        return;
      }
      const finalX = Number.isFinite(touch.clientX) ? touch.clientX : this.touchLastX;
      const finalY = Number.isFinite(touch.clientY) ? touch.clientY : this.touchLastY;
      const deltaX = finalX - this.touchStartX;
      const deltaY = finalY - this.touchStartY;
      const absX = Math.abs(deltaX);
      const absY = Math.abs(deltaY);
      const distance = Math.max(absX, absY);
      const minSwipeDistance = 24;
      const direction = distance < minSwipeDistance
        ? null
        : absX > absY
          ? (deltaX > 0 ? 'right' : 'left')
          : (deltaY > 0 ? 'down' : 'up');
      this.resetTouchTracking();
      if (!direction || !this.active || this.isOverlayVisible()) {
        return;
      }
      this.move(direction);
    }

    handleTouchCancel() {
      this.resetTouchTracking();
    }

    onEnter() {
      if (this.active) {
        return;
      }
      this.active = true;
      document.addEventListener('keydown', this.handleKeydown);
      if (this.boardElement) {
        this.boardElement.addEventListener('touchstart', this.handleTouchStart, this.touchStartOptions);
        this.boardElement.addEventListener('touchmove', this.handleTouchMove, this.touchMoveOptions);
        this.boardElement.addEventListener('touchend', this.handleTouchEnd);
        this.boardElement.addEventListener('touchcancel', this.handleTouchCancel);
      }
      if (typeof window !== 'undefined') {
        window.addEventListener('resize', this.handleResize);
        this.handleResize();
      }
    }

    onLeave() {
      if (!this.active) {
        if (typeof window !== 'undefined' && this.autosaveTimer != null) {
          window.clearTimeout(this.autosaveTimer);
          this.autosaveTimer = null;
        }
        this.persistAutosave();
        return;
      }
      this.active = false;
      document.removeEventListener('keydown', this.handleKeydown);
      if (this.boardElement) {
        this.boardElement.removeEventListener('touchstart', this.handleTouchStart, this.touchStartOptions);
        this.boardElement.removeEventListener('touchmove', this.handleTouchMove, this.touchMoveOptions);
        this.boardElement.removeEventListener('touchend', this.handleTouchEnd);
        this.boardElement.removeEventListener('touchcancel', this.handleTouchCancel);
      }
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.handleResize);
        if (this.resizeFrame) {
          window.cancelAnimationFrame(this.resizeFrame);
          this.resizeFrame = null;
        }
      }
      this.resetTouchTracking();
      if (typeof window !== 'undefined' && this.autosaveTimer != null) {
        window.clearTimeout(this.autosaveTimer);
        this.autosaveTimer = null;
      }
      this.persistAutosave();
    }
  }

  window.Quantum2048Game = Quantum2048Game;
})();
