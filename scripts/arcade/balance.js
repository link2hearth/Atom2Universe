(() => {
  const DEFAULT_BOARD_CONFIG = {
    widthPx: 620,
    surfaceHeightPx: 92,
    pivotHeightPx: 110,
    tolerance: 0.04,
    maxTiltDegrees: 18,
    maxOffsetForTilt: 0.3,
    testDurationMs: 900,
    settleDurationMs: 550
  };

  const DEFAULT_CUBE_SETS = [
    {
      id: 'default',
      labelKey: 'scripts.arcade.balance.sets.default',
      cubes: [
        { id: 'df-1', weight: 2, widthRatio: 0.16 },
        { id: 'df-2', weight: 3, widthRatio: 0.18 },
        { id: 'df-3', weight: 4, widthRatio: 0.2 },
        { id: 'df-4', weight: 1, widthRatio: 0.12 },
        { id: 'df-5', weight: 5, widthRatio: 0.22 }
      ]
    }
  ];

  function clamp(value, min, max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined' && typeof window.translateOrDefault === 'function') {
      return window.translateOrDefault(key, fallback, params);
    }
    return fallback;
  }

  function formatNumber(value, fractionDigits = 0) {
    const numeric = Number(value) || 0;
    const options = {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits
    };
    try {
      return numeric.toLocaleString(undefined, options);
    } catch (error) {
      return numeric.toFixed(fractionDigits);
    }
  }

  function getBalanceConfig() {
    const globalConfig = (typeof GAME_CONFIG !== 'undefined' && GAME_CONFIG && GAME_CONFIG.arcade)
      ? GAME_CONFIG.arcade.balance
      : null;
    const rawBoard = globalConfig?.board;
    const board = {
      widthPx: Number(rawBoard?.widthPx) || DEFAULT_BOARD_CONFIG.widthPx,
      surfaceHeightPx: Number(rawBoard?.surfaceHeightPx) || DEFAULT_BOARD_CONFIG.surfaceHeightPx,
      pivotHeightPx: Number(rawBoard?.pivotHeightPx) || DEFAULT_BOARD_CONFIG.pivotHeightPx,
      tolerance: clamp(Number(rawBoard?.tolerance) || DEFAULT_BOARD_CONFIG.tolerance, 0.005, 0.12),
      maxTiltDegrees: Number(rawBoard?.maxTiltDegrees) || DEFAULT_BOARD_CONFIG.maxTiltDegrees,
      maxOffsetForTilt: clamp(
        Number(rawBoard?.maxOffsetForTilt) || DEFAULT_BOARD_CONFIG.maxOffsetForTilt,
        0.08,
        0.48
      ),
      testDurationMs: Number(rawBoard?.testDurationMs) || DEFAULT_BOARD_CONFIG.testDurationMs,
      settleDurationMs: Number(rawBoard?.settleDurationMs) || DEFAULT_BOARD_CONFIG.settleDurationMs
    };

    const cubeSets = Array.isArray(globalConfig?.cubeSets) && globalConfig.cubeSets.length
      ? globalConfig.cubeSets
      : DEFAULT_CUBE_SETS;
    const defaultSetId = typeof globalConfig?.defaultSetId === 'string'
      ? globalConfig.defaultSetId
      : cubeSets[0]?.id || 'default';

    return { board, cubeSets, defaultSetId };
  }

  function normalizeWidthRatio(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return 0.16;
    }
    return clamp(numeric, 0.08, 0.32);
  }

  function getCubeAriaLabel(weight) {
    return translate(
      'index.sections.balance.cubeAria',
      'Bloc de poids {weight}',
      { weight: formatNumber(weight) }
    );
  }

  function getInventoryEmptyText() {
    return translate(
      'index.sections.balance.inventory.empty',
      'Tous les blocs sont placés sur la planche.'
    );
  }

  function getStatusMessage(key, params) {
    const fallbackMap = {
      ready: 'Disposez les blocs et lancez un test.',
      needBlocks: 'Ajoutez au moins un bloc sur la planche avant de tester.',
      success: 'Équilibre parfait !',
      leanLeft: 'La planche penche à gauche (écart : {offset}).',
      leanRight: 'La planche penche à droite (écart : {offset}).',
      returned: 'Bloc renvoyé dans l’inventaire.'
    };
    const fallback = fallbackMap[key] || fallbackMap.ready;
    return translate(`scripts.arcade.balance.status.${key}`, fallback, params);
  }

  class BalanceGame {
    constructor(options = {}) {
      const config = getBalanceConfig();
      this.config = config;
      this.pageElement = options.pageElement || document.getElementById('balance');
      this.stageElement = options.stageElement || document.getElementById('balanceStage');
      this.boardElement = options.boardElement || document.getElementById('balanceBoard');
      this.surfaceElement = options.surfaceElement || document.getElementById('balanceBoardSurface');
      this.inventoryElement = options.inventoryElement || document.getElementById('balancePieces');
      this.statusElement = options.statusElement || document.getElementById('balanceStatus');
      this.resetButton = options.resetButton || document.getElementById('balanceResetButton');
      this.testButton = options.testButton || document.getElementById('balanceTestButton');
      this.dragLayer = options.dragLayer || document.getElementById('balanceDragLayer');

      this.cubes = new Map();
      this.activeSet = null;
      this.dragState = null;
      this.currentBoardWidth = 0;
      this.pendingSettleTimeout = null;
      this.isTesting = false;

      this.handlePointerDown = this.handlePointerDown.bind(this);
      this.handlePointerMove = this.handlePointerMove.bind(this);
      this.handlePointerUp = this.handlePointerUp.bind(this);
      this.handlePointerCancel = this.handlePointerCancel.bind(this);
      this.handleResize = this.handleResize.bind(this);
      this.handleTest = this.handleTest.bind(this);
      this.handleReset = this.handleReset.bind(this);
      this.handleDoubleClick = this.handleDoubleClick.bind(this);
      this.handleKeyDown = this.handleKeyDown.bind(this);

      this.initializeDimensions();
      this.bindEvents();
      this.reset();
      this.updateStatus('ready');
    }

    initializeDimensions() {
      if (!this.boardElement) {
        return;
      }
      const { board } = this.config;
      this.boardElement.style.setProperty('--balance-board-width', `${board.widthPx}px`);
      this.boardElement.style.setProperty('--balance-board-surface-height', `${board.surfaceHeightPx}px`);
      this.boardElement.style.setProperty('--balance-pivot-height', `${board.pivotHeightPx}px`);
      this.pageElement?.style.setProperty('--balance-settle-duration', `${board.settleDurationMs}ms`);
      this.pageElement?.style.setProperty('--balance-tilt-duration', `${board.testDurationMs}ms`);
      this.measureBoardWidth();
    }

    bindEvents() {
      if (this.pageElement) {
        this.pageElement.addEventListener('pointerdown', this.handlePointerDown);
        this.pageElement.addEventListener('dblclick', this.handleDoubleClick);
        this.pageElement.addEventListener('keydown', this.handleKeyDown);
      }
      if (this.testButton) {
        this.testButton.addEventListener('click', this.handleTest);
      }
      if (this.resetButton) {
        this.resetButton.addEventListener('click', this.handleReset);
      }
      window.addEventListener('resize', this.handleResize);
    }

    dispose() {
      if (this.pageElement) {
        this.pageElement.removeEventListener('pointerdown', this.handlePointerDown);
        this.pageElement.removeEventListener('dblclick', this.handleDoubleClick);
        this.pageElement.removeEventListener('keydown', this.handleKeyDown);
      }
      if (this.testButton) {
        this.testButton.removeEventListener('click', this.handleTest);
      }
      if (this.resetButton) {
        this.resetButton.removeEventListener('click', this.handleReset);
      }
      window.removeEventListener('resize', this.handleResize);
      this.clearSettleTimeout();
    }

    measureBoardWidth() {
      if (!this.surfaceElement) {
        return;
      }
      const rect = this.surfaceElement.getBoundingClientRect();
      if (rect.width > 0) {
        this.currentBoardWidth = rect.width;
        this.updateCubeWidths();
      }
    }

    handleResize() {
      this.measureBoardWidth();
    }

    handleReset() {
      this.reset({ keepSet: false });
    }

    handleTest() {
      if (this.isTesting) {
        return;
      }
      this.runTest();
    }

    handlePointerDown(event) {
      if (!event || event.button !== 0) {
        return;
      }
      const cube = event.target?.closest?.('.balance-cube');
      if (!cube || !this.cubes.has(cube)) {
        return;
      }
      event.preventDefault();
      const pointerId = event.pointerId || 'mouse';
      const rect = cube.getBoundingClientRect();
      const offsetX = event.clientX - rect.left;
      const offsetY = event.clientY - rect.top;
      this.startDrag({ cube, pointerId, offsetX, offsetY, clientX: event.clientX, clientY: event.clientY });
    }

    handlePointerMove(event) {
      if (!this.dragState || event.pointerId !== this.dragState.pointerId) {
        return;
      }
      event.preventDefault();
      this.updateDragPosition(event.clientX, event.clientY);
    }

    handlePointerUp(event) {
      if (!this.dragState || event.pointerId !== this.dragState.pointerId) {
        return;
      }
      event.preventDefault();
      this.finishDrag(event.clientX, event.clientY);
    }

    handlePointerCancel(event) {
      if (!this.dragState || event.pointerId !== this.dragState.pointerId) {
        return;
      }
      this.cancelDrag();
    }

    handleDoubleClick(event) {
      const cube = event.target?.closest?.('.balance-cube');
      if (!cube || !this.cubes.has(cube)) {
        return;
      }
      const state = this.cubes.get(cube);
      if (state.location === 'board') {
        this.returnCubeToInventory(cube);
        this.updateStatus('returned');
      }
    }

    handleKeyDown(event) {
      if (!event) {
        return;
      }
      const cube = event.target?.closest?.('.balance-cube');
      if (!cube || !this.cubes.has(cube)) {
        return;
      }
      const state = this.cubes.get(cube);
      if ((event.key === 'Delete' || event.key === 'Backspace') && state.location === 'board') {
        event.preventDefault();
        this.returnCubeToInventory(cube);
        this.updateStatus('returned');
      }
    }

    startDrag(dragInfo) {
      const { cube, pointerId, offsetX, offsetY, clientX, clientY } = dragInfo;
      const state = this.cubes.get(cube);
      if (!state) {
        return;
      }
      this.cancelDrag();
      this.dragState = {
        cube,
        pointerId,
        offsetX,
        offsetY,
        originalParent: cube.parentElement,
        originalNextSibling: cube.nextSibling,
        from: state.location
      };
      cube.classList.add('balance-cube--dragging');
      if (this.dragLayer) {
        this.dragLayer.appendChild(cube);
      }
      document.addEventListener('pointermove', this.handlePointerMove);
      document.addEventListener('pointerup', this.handlePointerUp);
      document.addEventListener('pointercancel', this.handlePointerCancel);
      if (Number.isFinite(clientX) && Number.isFinite(clientY)) {
        this.updateDragPosition(clientX, clientY);
      }
    }

    updateDragPosition(clientX, clientY) {
      if (!this.dragState || !this.dragLayer) {
        return;
      }
      const layerRect = this.dragLayer.getBoundingClientRect();
      const { cube, offsetX, offsetY } = this.dragState;
      const x = clientX - layerRect.left - offsetX;
      const y = clientY - layerRect.top - offsetY;
      cube.style.left = `${x}px`;
      cube.style.top = `${y}px`;
    }

    finishDrag(clientX, clientY) {
      if (!this.dragState) {
        return;
      }
      const { cube } = this.dragState;
      this.stopPointerListeners();
      const boardRect = this.surfaceElement?.getBoundingClientRect();
      let placed = false;
      if (boardRect && boardRect.width > 0) {
        const state = this.cubes.get(cube);
        const widthRatio = state?.widthRatio || 0.16;
        const cubeWidth = cube.offsetWidth || (boardRect.width * widthRatio);
        const cubeLeft = clientX - this.dragState.offsetX;
        const cubeCenter = cubeLeft + cubeWidth / 2;
        const ratio = (cubeCenter - boardRect.left) / boardRect.width - 0.5;
        const halfSpan = widthRatio / 2;
        const clamped = clamp(ratio, -0.5 + halfSpan, 0.5 - halfSpan);
        const verticalMargin = Math.max(cube.offsetHeight || 0, boardRect.height * 0.25);
        const withinBounds = clientY >= boardRect.top - verticalMargin && clientY <= boardRect.bottom + verticalMargin;
        if (withinBounds) {
          this.placeCubeOnBoard(cube, clamped);
          placed = true;
        }
      }
      if (!placed) {
        this.returnCubeToInventory(cube);
      }
      cube.classList.remove('balance-cube--dragging');
      this.dragState = null;
      this.updateInventoryAccessibility();
    }

    cancelDrag() {
      if (!this.dragState) {
        return;
      }
      const { cube, originalParent, originalNextSibling } = this.dragState;
      cube.classList.remove('balance-cube--dragging');
      if (originalParent) {
        originalParent.insertBefore(cube, originalNextSibling);
      }
      this.dragState = null;
      this.stopPointerListeners();
    }

    stopPointerListeners() {
      document.removeEventListener('pointermove', this.handlePointerMove);
      document.removeEventListener('pointerup', this.handlePointerUp);
      document.removeEventListener('pointercancel', this.handlePointerCancel);
    }

    placeCubeOnBoard(cube, positionRatio) {
      if (!this.surfaceElement || !this.cubes.has(cube)) {
        return;
      }
      const state = this.cubes.get(cube);
      state.location = 'board';
      state.position = positionRatio;
      cube.classList.remove('balance-cube--inventory');
      cube.classList.add('balance-cube--board');
      cube.setAttribute('aria-pressed', 'true');
      cube.style.left = `${(positionRatio + 0.5) * 100}%`;
      this.surfaceElement.appendChild(cube);
      this.updateCubeWidth(cube, state.widthRatio);
    }

    returnCubeToInventory(cube) {
      if (!this.inventoryElement || !this.cubes.has(cube)) {
        return;
      }
      const state = this.cubes.get(cube);
      state.location = 'inventory';
      state.position = null;
      cube.classList.remove('balance-cube--board');
      cube.classList.add('balance-cube--inventory');
      cube.removeAttribute('aria-pressed');
      cube.style.left = '';
      cube.style.top = '';
      this.inventoryElement.appendChild(cube);
      this.updateCubeWidth(cube, state.widthRatio);
      this.applyRotation(0, this.config.board.settleDurationMs);
      this.clearTiltState();
      this.updateInventoryAccessibility();
    }

    updateCubeWidths() {
      this.cubes.forEach((state, cube) => {
        this.updateCubeWidth(cube, state.widthRatio);
      });
    }

    updateCubeWidth(cube, ratio) {
      if (!cube) {
        return;
      }
      const widthRatio = normalizeWidthRatio(ratio);
      cube.style.setProperty('--cube-width-ratio', widthRatio);
      if (this.currentBoardWidth > 0) {
        const widthPx = this.currentBoardWidth * widthRatio;
        if (cube.classList.contains('balance-cube--board')) {
          cube.style.width = `${widthPx}px`;
        } else {
          cube.style.width = `${clamp(widthPx, 60, 120)}px`;
        }
      }
    }

    reset({ keepSet = false } = {}) {
      this.clearSettleTimeout();
      this.applyRotation(0, this.config.board.settleDurationMs);
      this.clearTiltState();
      this.cubes.clear();
      if (this.inventoryElement) {
        this.inventoryElement.innerHTML = '';
      }
      if (this.surfaceElement) {
        this.surfaceElement.innerHTML = '';
      }
      if (!keepSet) {
        this.pickNextSet();
      }
      const set = this.activeSet;
      if (!set) {
        return;
      }
      const cubes = Array.isArray(set.cubes) ? set.cubes : [];
      cubes.forEach((def, index) => {
        const cube = this.createCube(def, index);
        if (!cube) {
          return;
        }
        const widthRatio = normalizeWidthRatio(def.widthRatio);
        this.cubes.set(cube, {
          definition: def,
          widthRatio,
          weight: Number(def.weight) || 1,
          location: 'inventory',
          position: null
        });
        this.updateCubeWidth(cube, widthRatio);
        this.inventoryElement?.appendChild(cube);
      });
      this.updateInventoryAccessibility();
    }

    pickNextSet() {
      const sets = this.config.cubeSets;
      if (!Array.isArray(sets) || !sets.length) {
        this.activeSet = DEFAULT_CUBE_SETS[0];
        return;
      }
      const ids = sets.map(set => set?.id).filter(id => typeof id === 'string');
      let targetId = this.config.defaultSetId;
      if (this.activeSet && ids.length > 1) {
        const currentIndex = ids.indexOf(this.activeSet.id);
        const remaining = ids.filter((_, index) => index !== currentIndex);
        if (remaining.length) {
          targetId = remaining[Math.floor(Math.random() * remaining.length)];
        }
      }
      const found = sets.find(set => set && set.id === targetId) || sets[0];
      this.activeSet = found || DEFAULT_CUBE_SETS[0];
    }

    createCube(definition, index) {
      if (!definition) {
        return null;
      }
      const weight = Number(definition.weight) || 1 + index;
      const cube = document.createElement('div');
      cube.className = 'balance-cube balance-cube--inventory';
      cube.tabIndex = 0;
      cube.dataset.cubeId = typeof definition.id === 'string' ? definition.id : `cube-${index}`;
      cube.dataset.weight = String(weight);
      cube.dataset.index = String(index);
      cube.setAttribute('role', 'button');
      cube.setAttribute('aria-label', getCubeAriaLabel(weight));
      cube.innerHTML = `<span class="balance-cube__weight">${formatNumber(weight)}</span>`;
      return cube;
    }

    updateInventoryAccessibility() {
      if (!this.inventoryElement) {
        return;
      }
      const placedCount = Array.from(this.cubes.values()).filter(state => state.location === 'board').length;
      const total = this.cubes.size;
      const emptyText = getInventoryEmptyText();
      this.inventoryElement.dataset.emptyText = total > 0 && placedCount === total ? emptyText : '';
      const inventoryLabel = translate(
        'index.sections.balance.inventory.status',
        '{available} blocs prêts à être placés.',
        { available: formatNumber(total - placedCount) }
      );
      this.inventoryElement.setAttribute('aria-live', 'polite');
      this.inventoryElement.setAttribute('aria-label', inventoryLabel);
    }

    runTest() {
      const placed = this.getPlacedCubes();
      if (!placed.length) {
        this.updateStatus('needBlocks');
        this.applyRotation(0, this.config.board.settleDurationMs);
        return;
      }
      this.isTesting = true;
      const result = this.evaluateBoard(placed);
      const offsetDisplay = formatNumber(Math.abs(result.centerOffset) * 100, 1);
      let statusKey = 'success';
      if (Math.abs(result.centerOffset) > this.config.board.tolerance) {
        statusKey = result.centerOffset < 0 ? 'leanLeft' : 'leanRight';
      }
      this.updateStatus(statusKey, { offset: offsetDisplay });
      this.applyRotation(result.rotation, this.config.board.testDurationMs);
      this.applyTiltState(statusKey);
      this.clearSettleTimeout();
      this.pendingSettleTimeout = window.setTimeout(() => {
        this.applyRotation(0, this.config.board.settleDurationMs);
        this.clearTiltState();
        this.pendingSettleTimeout = null;
        this.isTesting = false;
      }, this.config.board.testDurationMs + this.config.board.settleDurationMs);
    }

    getPlacedCubes() {
      const placed = [];
      this.cubes.forEach(state => {
        if (state.location === 'board' && typeof state.position === 'number') {
          placed.push(state);
        }
      });
      return placed;
    }

    evaluateBoard(cubes) {
      let totalTorque = 0;
      let totalWeight = 0;
      cubes.forEach(cube => {
        const weight = Number(cube.weight) || 0;
        totalWeight += weight;
        totalTorque += weight * cube.position;
      });
      const centerOffset = totalWeight > 0 ? totalTorque / totalWeight : 0;
      const maxOffset = this.config.board.maxOffsetForTilt || 0.3;
      const normalized = clamp(centerOffset / maxOffset, -1, 1);
      const rotation = normalized * (this.config.board.maxTiltDegrees || 18);
      return { centerOffset, rotation, totalWeight, totalTorque };
    }

    applyRotation(degrees, duration) {
      if (!this.boardElement) {
        return;
      }
      const validDuration = Number.isFinite(duration) && duration >= 0
        ? `${duration}ms`
        : `${this.config.board.testDurationMs}ms`;
      this.boardElement.style.setProperty('--balance-tilt-duration', validDuration);
      this.boardElement.style.setProperty('--balance-rotation', `${degrees}deg`);
    }

    applyTiltState(statusKey) {
      if (!this.stageElement) {
        return;
      }
      this.stageElement.classList.add('balance-stage--testing');
      this.stageElement.classList.remove('balance-stage--settling');
      this.stageElement.classList.remove('balance-stage--tilt-left', 'balance-stage--tilt-right', 'balance-stage--balanced');
      if (statusKey === 'success') {
        this.stageElement.classList.add('balance-stage--balanced');
      } else if (statusKey === 'leanLeft') {
        this.stageElement.classList.add('balance-stage--tilt-left');
      } else if (statusKey === 'leanRight') {
        this.stageElement.classList.add('balance-stage--tilt-right');
      }
      window.requestAnimationFrame(() => {
        this.stageElement?.classList.add('balance-stage--settling');
      });
    }

    clearTiltState() {
      if (!this.stageElement) {
        return;
      }
      this.stageElement.classList.remove(
        'balance-stage--testing',
        'balance-stage--settling',
        'balance-stage--tilt-left',
        'balance-stage--tilt-right',
        'balance-stage--balanced'
      );
    }

    updateStatus(key, params = {}) {
      if (!this.statusElement) {
        return;
      }
      const message = getStatusMessage(key, params);
      this.statusElement.textContent = message;
      this.statusElement.classList.remove('balance-status--success', 'balance-status--warning', 'balance-status--error');
      if (key === 'success') {
        this.statusElement.classList.add('balance-status--success');
      } else if (key === 'leanLeft' || key === 'leanRight') {
        this.statusElement.classList.add('balance-status--warning');
      } else if (key === 'needBlocks') {
        this.statusElement.classList.add('balance-status--error');
      }
    }

    clearSettleTimeout() {
      if (this.pendingSettleTimeout) {
        window.clearTimeout(this.pendingSettleTimeout);
        this.pendingSettleTimeout = null;
      }
    }

    onEnter() {
      this.measureBoardWidth();
      this.updateInventoryAccessibility();
    }

    onLeave() {
      this.clearSettleTimeout();
      this.applyRotation(0, this.config.board.settleDurationMs);
      this.clearTiltState();
      this.isTesting = false;
    }
  }

  window.BalanceGame = BalanceGame;
})();
