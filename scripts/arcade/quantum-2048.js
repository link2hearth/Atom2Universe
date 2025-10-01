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
      spawnWeights: spawnWeights.length ? spawnWeights : DEFAULT_CONFIG.spawnWeights
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
        statusElement,
        restartButton,
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
      this.statusElement = statusElement || null;
      this.restartButton = restartButton || null;
      this.overlayElement = overlayElement || null;
      this.overlayMessageElement = overlayMessageElement || null;
      this.overlayButtonElement = overlayButtonElement || null;

      this.size = this.normalizeSize(this.config.defaultGridSize);
      this.target = this.normalizeTarget(this.config.recommendedTargetBySize[this.size] ?? this.config.targetValues[0]);
      this.board = [];
      this.score = 0;
      this.moves = 0;
      this.bestTile = 0;
      this.hasWon = false;
      this.gameOver = false;
      this.active = false;

      this.handleKeydown = this.handleKeydown.bind(this);
      this.handleSizeChange = this.handleSizeChange.bind(this);
      this.handleTargetChange = this.handleTargetChange.bind(this);
      this.handleRestart = this.handleRestart.bind(this);
      this.handleOverlayAction = this.handleOverlayAction.bind(this);
      this.handleResize = this.handleResize.bind(this);
      this.resizeFrame = null;

      this.setupControls();
      this.startNewGame({ announce: false, randomize: this.config.randomizeGames !== false });
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
      if (this.restartButton) {
        this.restartButton.addEventListener('click', this.handleRestart);
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
        option.textContent = translate(
          'scripts.arcade.quantum2048.sizeOption',
          `${size}×${size}`,
          { size }
        );
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
        let label = translate('scripts.arcade.quantum2048.targetOption', formatted, { value: formatted });
        if (recommended && value === recommended) {
          const recommendedLabel = translate('scripts.arcade.quantum2048.targetRecommended', 'Recommandé');
          label = `${label} · ${recommendedLabel}`;
        }
        option.textContent = label;
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

    handleRestart() {
      this.startNewGame({
        size: this.size,
        target: this.target,
        randomize: this.config.randomizeGames !== false
      });
    }

    handleOverlayAction() {
      this.startNewGame({
        size: this.size,
        target: this.target,
        randomize: this.config.randomizeGames !== false
      });
    }

    startNewGame({ size = this.size, target = this.target, announce = true, randomize = false } = {}) {
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
      if (announce !== false) {
        this.setStatus('ready');
      }
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
      if (Number.isFinite(boardSize) && boardSize > 0) {
        this.boardElement.style.setProperty('--quantum2048-board-size', `${boardSize}px`);
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
    }

    setStatus(state) {
      if (!this.statusElement) {
        return;
      }
      this.statusElement.classList.remove('quantum2048-status--success', 'quantum2048-status--warning');
      let message = '';
      const targetValue = formatInteger(this.target);
      switch (state) {
        case 'victory':
          message = translate('index.sections.quantum2048.status.victory', 'Objectif atteint ! Continuez votre ascension.');
          this.statusElement.classList.add('quantum2048-status--success');
          break;
        case 'defeat':
          message = translate('index.sections.quantum2048.status.defeat', 'Plus aucun mouvement possible. Relancez une partie.');
          this.statusElement.classList.add('quantum2048-status--warning');
          break;
        default:
          message = translate(
            'index.sections.quantum2048.status.ready',
            `Fusionnez les tuiles pour atteindre ${targetValue}.`,
            { target: targetValue }
          );
          break;
      }
      this.statusElement.textContent = message;
    }

    handleVictory() {
      this.hasWon = true;
      this.setStatus('victory');
    }

    handleDefeat() {
      this.gameOver = true;
      this.setStatus('defeat');
      this.showOverlay(
        translate(
          'scripts.arcade.quantum2048.overlay.defeat',
          'Plus de mouvements ! Score : {score}',
          { score: formatInteger(this.score) }
        )
      );
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

    onEnter() {
      if (this.active) {
        return;
      }
      this.active = true;
      document.addEventListener('keydown', this.handleKeydown);
      if (typeof window !== 'undefined') {
        window.addEventListener('resize', this.handleResize);
        this.handleResize();
      }
      this.setStatus(this.gameOver ? 'defeat' : this.hasWon ? 'victory' : 'ready');
    }

    onLeave() {
      if (!this.active) {
        return;
      }
      this.active = false;
      document.removeEventListener('keydown', this.handleKeydown);
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.handleResize);
        if (this.resizeFrame) {
          window.cancelAnimationFrame(this.resizeFrame);
          this.resizeFrame = null;
        }
      }
    }
  }

  window.Quantum2048Game = Quantum2048Game;
})();
