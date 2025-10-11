(() => {
  const DEFAULT_BOARD_PHYSICS = {
    gravity: 9.81,
    boardMassKg: 12,
    cubeMassPerWeight: 1.4,
    lengthMeters: 2.4,
    pivotFriction: 14,
    stiffness: 960,
    simulationStepMs: 16,
    simulationDurationMs: 1800,
    maxAngularVelocity: 5,
    settleThresholdVelocity: 0.0025,
    settleThresholdTorque: 0.05
  };

  const DEFAULT_BOARD_CONFIG = {
    widthPx: 620,
    surfaceHeightPx: 12,
    pivotHeightPx: 110,
    tolerance: 0.026,
    maxTiltDegrees: 18,
    maxOffsetForTilt: 0.32,
    testDurationMs: 900,
    settleDurationMs: 600,
    physics: { ...DEFAULT_BOARD_PHYSICS }
  };

  const DEFAULT_CUBE_RULES = {
    countPerSet: 5,
    widthRatio: 0.18,
    inventoryWidthPx: { min: 60, max: 120 },
    weightRange: { min: 1, max: 20 },
    stackOffsetMultiplier: 0.72,
    stackGroupingThreshold: 0.08,
    randomizeWeights: true
  };

  const DEFAULT_CUBE_SETS = [
    {
      id: 'default',
      labelKey: 'scripts.arcade.balance.sets.default',
      cubeCount: DEFAULT_CUBE_RULES.countPerSet
    }
  ];

  const DEFAULT_REWARD_RULES = {
    perfectBalance: {
      maxAttempts: 2,
      ticketAmount: 1
    }
  };

  const DEFAULT_DIFFICULTY_CONFIG = {
    defaultMode: 'easy',
    modes: [
      {
        id: 'easy',
        labelKey: 'scripts.arcade.balance.difficulty.easy',
        labelFallback: 'Easy mode',
        descriptionKey: 'scripts.arcade.balance.difficulty.easyDescription',
        descriptionFallback: 'Generous tolerance to get familiar with balancing.',
        toleranceMultiplier: 1
      },
      {
        id: 'hard',
        labelKey: 'scripts.arcade.balance.difficulty.hard',
        labelFallback: 'Hard mode',
        descriptionKey: 'scripts.arcade.balance.difficulty.hardDescription',
        descriptionFallback: 'Reduced tolerance for precise, demanding balance tests.',
        toleranceMultiplier: 0.45
      }
    ]
  };

  function clamp(value, min, max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  function toRadians(degrees) {
    return (degrees * Math.PI) / 180;
  }

  function toDegrees(radians) {
    return (radians * 180) / Math.PI;
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
      settleDurationMs: Number(rawBoard?.settleDurationMs) || DEFAULT_BOARD_CONFIG.settleDurationMs,
      physics: normalizeBoardPhysics(rawBoard?.physics)
    };

    const cubeSets = Array.isArray(globalConfig?.cubeSets) && globalConfig.cubeSets.length
      ? globalConfig.cubeSets
      : DEFAULT_CUBE_SETS;
    const defaultSetId = typeof globalConfig?.defaultSetId === 'string'
      ? globalConfig.defaultSetId
      : cubeSets[0]?.id || 'default';

    const rawCubeRules = globalConfig?.cubeRules;
    const normalizeInventoryWidth = value => {
      const numeric = Number(value);
      if (!Number.isFinite(numeric)) {
        return null;
      }
      return clamp(numeric, 36, 220);
    };
    const weightMin = Math.floor(Number(rawCubeRules?.weightRange?.min));
    const weightMax = Math.floor(Number(rawCubeRules?.weightRange?.max));
    let resolvedMin = Number.isFinite(weightMin) ? weightMin : DEFAULT_CUBE_RULES.weightRange.min;
    let resolvedMax = Number.isFinite(weightMax) ? weightMax : DEFAULT_CUBE_RULES.weightRange.max;
    if (resolvedMax < resolvedMin) {
      const temp = resolvedMax;
      resolvedMax = resolvedMin;
      resolvedMin = temp;
    }
    const cubeRules = {
      countPerSet: clamp(
        Math.floor(Number(rawCubeRules?.countPerSet)) || DEFAULT_CUBE_RULES.countPerSet,
        1,
        12
      ),
      widthRatio: normalizeWidthRatio(rawCubeRules?.widthRatio ?? DEFAULT_CUBE_RULES.widthRatio),
      inventoryWidthPx: {
        min: normalizeInventoryWidth(rawCubeRules?.inventoryWidthPx?.min) ?? DEFAULT_CUBE_RULES.inventoryWidthPx.min,
        max: normalizeInventoryWidth(rawCubeRules?.inventoryWidthPx?.max) ?? DEFAULT_CUBE_RULES.inventoryWidthPx.max
      },
      weightRange: {
        min: clamp(resolvedMin, -120, 120),
        max: clamp(resolvedMax, -120, 120)
      },
      stackOffsetMultiplier: Number(rawCubeRules?.stackOffsetMultiplier) > 0
        ? clamp(Number(rawCubeRules.stackOffsetMultiplier), 0.2, 1.6)
        : DEFAULT_CUBE_RULES.stackOffsetMultiplier,
      stackGroupingThreshold: clamp(
        Number(rawCubeRules?.stackGroupingThreshold) || DEFAULT_CUBE_RULES.stackGroupingThreshold,
        0.02,
        0.32
      ),
      randomizeWeights: rawCubeRules?.randomizeWeights ?? DEFAULT_CUBE_RULES.randomizeWeights
    };

    if (cubeRules.inventoryWidthPx.max < cubeRules.inventoryWidthPx.min) {
      const { min, max } = cubeRules.inventoryWidthPx;
      cubeRules.inventoryWidthPx.min = max;
      cubeRules.inventoryWidthPx.max = min;
    }

    const rawRewards = globalConfig?.rewards;
    const perfectBalanceConfig = rawRewards?.perfectBalance || {};
    const rawMaxAttempts = Math.floor(Number(perfectBalanceConfig.maxAttempts));
    const rawTicketAmount = Math.floor(Number(perfectBalanceConfig.ticketAmount));
    const rewards = {
      perfectBalance: {
        maxAttempts: Number.isFinite(rawMaxAttempts) && rawMaxAttempts > 0
          ? clamp(rawMaxAttempts, 1, 10)
          : DEFAULT_REWARD_RULES.perfectBalance.maxAttempts,
        ticketAmount: Number.isFinite(rawTicketAmount)
          ? clamp(rawTicketAmount, 0, 10)
          : DEFAULT_REWARD_RULES.perfectBalance.ticketAmount
      }
    };

    const difficulty = normalizeDifficulty(globalConfig?.difficulty);

    return { board, cubeSets, defaultSetId, cubeRules, rewards, difficulty };
  }

  function normalizeWidthRatio(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return DEFAULT_CUBE_RULES.widthRatio;
    }
    return clamp(numeric, 0.08, 0.32);
  }

  function normalizeBoardPhysics(rawPhysics) {
    const defaults = DEFAULT_BOARD_PHYSICS;
    const normalized = { ...defaults };
    if (!rawPhysics || typeof rawPhysics !== 'object') {
      return { ...normalized };
    }
    const clampOr = (value, fallback, min, max) => {
      const numeric = Number(value);
      if (!Number.isFinite(numeric)) {
        return fallback;
      }
      return clamp(numeric, min, max);
    };

    normalized.gravity = clampOr(rawPhysics.gravity, defaults.gravity, 1, 50);
    normalized.boardMassKg = clampOr(rawPhysics.boardMassKg, defaults.boardMassKg, 0.5, 200);
    normalized.cubeMassPerWeight = clampOr(
      rawPhysics.cubeMassPerWeight,
      defaults.cubeMassPerWeight,
      0.05,
      15
    );
    normalized.lengthMeters = clampOr(rawPhysics.lengthMeters, defaults.lengthMeters, 0.5, 12);
    normalized.pivotFriction = clampOr(rawPhysics.pivotFriction, defaults.pivotFriction, 0.01, 400);
    normalized.stiffness = clampOr(rawPhysics.stiffness, defaults.stiffness, 10, 6000);
    const step = clampOr(rawPhysics.simulationStepMs, defaults.simulationStepMs, 4, 120);
    normalized.simulationStepMs = Math.floor(step);
    const duration = clampOr(
      rawPhysics.simulationDurationMs,
      defaults.simulationDurationMs,
      normalized.simulationStepMs,
      6000
    );
    normalized.simulationDurationMs = Math.floor(duration);
    normalized.maxAngularVelocity = clampOr(
      rawPhysics.maxAngularVelocity,
      defaults.maxAngularVelocity,
      0.1,
      30
    );
    normalized.settleThresholdVelocity = clampOr(
      rawPhysics.settleThresholdVelocity,
      defaults.settleThresholdVelocity,
      0.0001,
      0.5
    );
    normalized.settleThresholdTorque = clampOr(
      rawPhysics.settleThresholdTorque,
      defaults.settleThresholdTorque,
      0.0001,
      10
    );

    return { ...normalized };
  }

  function getDefaultDifficultyMode(id) {
    if (typeof id !== 'string') {
      return null;
    }
    return DEFAULT_DIFFICULTY_CONFIG.modes.find(mode => mode.id === id) || null;
  }

  function normalizeDifficulty(rawDifficulty) {
    const fallbackModes = DEFAULT_DIFFICULTY_CONFIG.modes;
    const rawModes = Array.isArray(rawDifficulty?.modes) && rawDifficulty.modes.length
      ? rawDifficulty.modes
      : fallbackModes;
    const seen = new Set();
    const normalizedModes = [];
    rawModes.forEach((mode, index) => {
      const modeId = typeof mode?.id === 'string' ? mode.id : null;
      const fallback = getDefaultDifficultyMode(modeId) || fallbackModes[index] || fallbackModes[0];
      const id = modeId || fallback?.id || `mode-${index + 1}`;
      if (seen.has(id)) {
        return;
      }
      seen.add(id);
      const toleranceMultiplierRaw = Number(mode?.toleranceMultiplier);
      const toleranceMultiplier = Number.isFinite(toleranceMultiplierRaw) && toleranceMultiplierRaw > 0
        ? clamp(toleranceMultiplierRaw, 0.05, 5)
        : clamp(Number(fallback?.toleranceMultiplier) || 1, 0.05, 5);
      const labelKey = typeof mode?.labelKey === 'string'
        ? mode.labelKey
        : fallback?.labelKey;
      const descriptionKey = typeof mode?.descriptionKey === 'string'
        ? mode.descriptionKey
        : fallback?.descriptionKey;
      const labelFallback = typeof mode?.labelFallback === 'string'
        ? mode.labelFallback
        : typeof fallback?.labelFallback === 'string'
          ? fallback.labelFallback
          : `Mode ${id}`;
      const descriptionFallback = typeof mode?.descriptionFallback === 'string'
        ? mode.descriptionFallback
        : typeof fallback?.descriptionFallback === 'string'
          ? fallback.descriptionFallback
          : '';
      normalizedModes.push({
        id,
        labelKey,
        descriptionKey,
        toleranceMultiplier,
        labelFallback,
        descriptionFallback
      });
    });

    if (!normalizedModes.length) {
      return { ...DEFAULT_DIFFICULTY_CONFIG };
    }

    const desiredDefault = typeof rawDifficulty?.defaultMode === 'string'
      ? rawDifficulty.defaultMode
      : DEFAULT_DIFFICULTY_CONFIG.defaultMode;
    const defaultMode = normalizedModes.some(mode => mode.id === desiredDefault)
      ? desiredDefault
      : normalizedModes[0].id;

    return { defaultMode, modes: normalizedModes };
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
      ready: 'Disposez les blocs puis appuyez sur la zone de résultat pour lancer le test.',
      needBlocks: 'Ajoutez au moins un bloc sur la planche avant de tester.',
      needAllBlocks: 'Placez tous les blocs sur la planche avant de tester.',
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
      this.statusButton = options.statusButton || document.getElementById('balanceStatusButton');
      this.statusElement = options.statusElement || document.getElementById('balanceStatus');
      this.difficultySelect = options.difficultySelect || document.getElementById('balanceDifficultySelect');
      this.difficultyDescriptionElement = options.difficultyDescription || document.getElementById('balanceDifficultyDescription');
      this.resetButton = options.resetButton || document.getElementById('balanceResetButton');
      this.testButton =
        options.testButton || this.statusButton || document.getElementById('balanceTestButton');
      this.dragLayer = options.dragLayer || document.getElementById('balanceDragLayer');

      this.difficultyConfig = config.difficulty || { ...DEFAULT_DIFFICULTY_CONFIG };
      this.baseTolerance = Number(config.board?.tolerance) || DEFAULT_BOARD_CONFIG.tolerance;
      this.currentTolerance = this.baseTolerance;
      this.activeDifficultyId = null;

      this.cubes = new Map();
      this.activeSet = null;
      this.dragState = null;
      this.currentBoardWidth = 0;
      this.pendingSettleTimeout = null;
      this.isTesting = false;
      this.testsSinceReset = 0;
      this.successRewardClaimed = false;

      this.handlePointerDown = this.handlePointerDown.bind(this);
      this.handlePointerMove = this.handlePointerMove.bind(this);
      this.handlePointerUp = this.handlePointerUp.bind(this);
      this.handlePointerCancel = this.handlePointerCancel.bind(this);
      this.handleResize = this.handleResize.bind(this);
      this.handleTest = this.handleTest.bind(this);
      this.handleReset = this.handleReset.bind(this);
      this.handleDoubleClick = this.handleDoubleClick.bind(this);
      this.handleKeyDown = this.handleKeyDown.bind(this);
      this.handleDifficultyChange = this.handleDifficultyChange.bind(this);
      this.handleLanguageChange = this.handleLanguageChange.bind(this);

      this.initializeDimensions();
      this.bindEvents();
      this.initializeDifficultyControls();
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

    initializeDifficultyControls() {
      const hasModes = Array.isArray(this.difficultyConfig?.modes) && this.difficultyConfig.modes.length;
      if (!hasModes) {
        this.difficultyConfig = { ...DEFAULT_DIFFICULTY_CONFIG };
      }
      const modes = this.difficultyConfig.modes || DEFAULT_DIFFICULTY_CONFIG.modes;
      if (this.difficultySelect) {
        this.difficultySelect.innerHTML = '';
        modes.forEach(mode => {
          const option = document.createElement('option');
          option.value = mode.id;
          option.textContent = translate(mode.labelKey, mode.labelFallback || mode.id);
          this.difficultySelect.appendChild(option);
        });
        this.difficultySelect.addEventListener('change', this.handleDifficultyChange);
      }
      const defaultModeId = this.difficultyConfig.defaultMode || modes[0]?.id;
      this.setDifficulty(defaultModeId, { skipStatusReset: true });
    }

    getDifficultyMode(modeId) {
      const modes = this.difficultyConfig?.modes;
      if (!Array.isArray(modes) || !modes.length) {
        return DEFAULT_DIFFICULTY_CONFIG.modes[0];
      }
      if (modeId) {
        const found = modes.find(mode => mode.id === modeId);
        if (found) {
          return found;
        }
      }
      return modes[0];
    }

    computeToleranceForMode(mode) {
      const base = Number(this.baseTolerance) > 0 ? this.baseTolerance : DEFAULT_BOARD_CONFIG.tolerance;
      const multiplier = Number(mode?.toleranceMultiplier);
      const effectiveMultiplier = Number.isFinite(multiplier) && multiplier > 0 ? multiplier : 1;
      const tolerance = base * effectiveMultiplier;
      return clamp(tolerance, 0.002, 0.12);
    }

    setDifficulty(modeId, { skipStatusReset = false } = {}) {
      const mode = this.getDifficultyMode(modeId);
      if (!mode) {
        return;
      }
      this.activeDifficultyId = mode.id;
      this.currentTolerance = this.computeToleranceForMode(mode);
      this.config.board.tolerance = this.currentTolerance;
      if (this.difficultySelect && this.difficultySelect.value !== mode.id) {
        this.difficultySelect.value = mode.id;
      }
      this.updateDifficultyDescription(mode);
      if (!skipStatusReset) {
        this.updateStatus('ready');
      }
    }

    updateDifficultyDescription(mode) {
      if (!this.difficultyDescriptionElement) {
        return;
      }
      const activeMode = mode || this.getDifficultyMode(this.activeDifficultyId);
      const description = activeMode
        ? translate(activeMode.descriptionKey, activeMode.descriptionFallback || '')
        : '';
      this.difficultyDescriptionElement.textContent = description || '';
      this.difficultyDescriptionElement.hidden = !description;
    }

    getCurrentTolerance() {
      const tolerance = Number(this.currentTolerance);
      if (Number.isFinite(tolerance) && tolerance > 0) {
        return tolerance;
      }
      const configTolerance = Number(this.config.board?.tolerance);
      if (Number.isFinite(configTolerance) && configTolerance > 0) {
        return configTolerance;
      }
      return DEFAULT_BOARD_CONFIG.tolerance;
    }

    refreshDifficultyTexts() {
      const modes = this.difficultyConfig?.modes;
      if (this.difficultySelect && Array.isArray(modes) && modes.length) {
        const previousValue = this.difficultySelect.value;
        const options = Array.from(this.difficultySelect.options || []);
        modes.forEach(mode => {
          const option = options.find(opt => opt.value === mode.id);
          if (option) {
            option.textContent = translate(mode.labelKey, mode.labelFallback || mode.id);
          }
        });
        if (previousValue) {
          this.difficultySelect.value = previousValue;
        }
      }
      this.updateDifficultyDescription();
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
      if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
        globalThis.addEventListener('i18n:languagechange', this.handleLanguageChange);
      }
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
      if (this.difficultySelect) {
        this.difficultySelect.removeEventListener('change', this.handleDifficultyChange);
      }
      window.removeEventListener('resize', this.handleResize);
      if (typeof globalThis !== 'undefined' && typeof globalThis.removeEventListener === 'function') {
        globalThis.removeEventListener('i18n:languagechange', this.handleLanguageChange);
      }
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

    handleDifficultyChange(event) {
      const modeId = event?.target?.value;
      this.setDifficulty(modeId, { skipStatusReset: true });
      this.reset({ keepSet: true });
      this.updateStatus('ready');
    }

    handleLanguageChange() {
      this.refreshDifficultyTexts();
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
      cube.style.top = '';
      this.surfaceElement.appendChild(cube);
      this.updateCubeWidth(cube, state.widthRatio);
      this.recalculateStacking();
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
      state.stackIndex = 0;
      this.clearCubeStackStyles(cube);
      this.inventoryElement.appendChild(cube);
      this.updateCubeWidth(cube, state.widthRatio);
      this.applyRotation(0, this.config.board.settleDurationMs);
      this.clearTiltState();
      this.updateInventoryAccessibility();
      this.recalculateStacking();
    }

    updateCubeWidths() {
      this.cubes.forEach((state, cube) => {
        this.updateCubeWidth(cube, state.widthRatio);
      });
      this.recalculateStacking();
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
          const minWidth = this.config.cubeRules.inventoryWidthPx.min;
          const maxWidth = this.config.cubeRules.inventoryWidthPx.max;
          cube.style.width = `${clamp(widthPx, minWidth, maxWidth)}px`;
        }
      }
    }

    reset({ keepSet = false } = {}) {
      this.clearSettleTimeout();
      this.applyRotation(0, this.config.board.settleDurationMs);
      this.clearTiltState();
      this.isTesting = false;
      this.testsSinceReset = 0;
      this.successRewardClaimed = false;
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
      const cubes = this.getCubeDefinitionsForSet(set);
      cubes.forEach((def, index) => {
        const weight = this.resolveCubeWeight(def, index);
        const cube = this.createCube({ ...def, weight }, index);
        if (!cube) {
          return;
        }
        const widthRatio = normalizeWidthRatio(this.config.cubeRules.widthRatio);
        this.cubes.set(cube, {
          definition: def,
          widthRatio,
          weight,
          location: 'inventory',
          position: null,
          stackIndex: 0
        });
        this.updateCubeWidth(cube, widthRatio);
        this.inventoryElement?.appendChild(cube);
      });
      this.updateInventoryAccessibility();
      this.updateTestButtonState();
    }

    getCubeDefinitionsForSet(set) {
      if (!set) {
        return [];
      }
      if (Array.isArray(set.cubes) && set.cubes.length) {
        return set.cubes;
      }
      const count = Number.isFinite(set.cubeCount)
        ? clamp(Math.floor(set.cubeCount), 1, 20)
        : this.config.cubeRules.countPerSet;
      return Array.from({ length: count }, (_, index) => ({
        id: `${set.id || 'cube'}-${index + 1}`
      }));
    }

    resolveCubeWeight(definition, index) {
      const existingWeights = new Set();
      this.cubes.forEach(state => {
        if (state && typeof state.weight === 'number') {
          existingWeights.add(state.weight);
        }
      });
      const { min, max } = this.config.cubeRules.weightRange;
      const fallback = clamp(
        Number(definition?.weight) || min + index,
        min,
        max
      );
      if (!this.config.cubeRules.randomizeWeights) {
        return this.ensureUniqueWeight(fallback, existingWeights, min, max);
      }
      const randomWeight = this.generateRandomWeight(existingWeights, min, max);
      return randomWeight ?? this.ensureUniqueWeight(fallback, existingWeights, min, max);
    }

    ensureUniqueWeight(weight, existingWeights, min, max) {
      let candidate = weight;
      const span = Math.max(max - min + 1, 1);
      if (!existingWeights.has(candidate)) {
        return candidate;
      }
      for (let offset = 1; offset < span; offset += 1) {
        const next = weight + offset;
        if (next <= max && !existingWeights.has(next)) {
          return next;
        }
        const previous = weight - offset;
        if (previous >= min && !existingWeights.has(previous)) {
          return previous;
        }
      }
      return clamp(weight, min, max);
    }

    generateRandomWeight(existingWeights, min, max) {
      if (max < min) {
        return null;
      }
      const span = max - min + 1;
      if (existingWeights.size >= span) {
        return null;
      }
      let attempts = 0;
      while (attempts < span * 2) {
        const candidate = Math.floor(Math.random() * span) + min;
        if (!existingWeights.has(candidate)) {
          return candidate;
        }
        attempts += 1;
      }
      return null;
    }

    recalculateStacking() {
      const entries = [];
      this.cubes.forEach((state, cube) => {
        if (!state) {
          return;
        }
        if (state.location === 'board' && typeof state.position === 'number') {
          entries.push({ state, cube });
        } else {
          state.stackIndex = 0;
          this.clearCubeStackStyles(cube);
        }
      });
      if (!entries.length) {
        return;
      }
      const threshold = this.config.cubeRules.stackGroupingThreshold;
      entries.sort((a, b) => a.state.position - b.state.position);
      let currentGroup = [];
      let currentCenter = null;
      const groups = [];
      entries.forEach(entry => {
        if (!currentGroup.length) {
          currentGroup.push(entry);
          currentCenter = entry.state.position;
          groups.push(currentGroup);
          return;
        }
        const distance = Math.abs(entry.state.position - currentCenter);
        if (distance <= threshold) {
          currentGroup.push(entry);
          currentCenter = (currentCenter * (currentGroup.length - 1) + entry.state.position) / currentGroup.length;
        } else {
          currentGroup = [entry];
          currentCenter = entry.state.position;
          groups.push(currentGroup);
        }
      });
      groups.forEach(group => {
        group.forEach((entry, index) => {
          entry.state.stackIndex = index;
          this.applyStackStyles(entry.cube, entry.state);
        });
      });
    }

    applyStackStyles(cube, state) {
      if (!cube || !state) {
        return;
      }
      const stackIndex = Number(state.stackIndex) || 0;
      const baseHeight = cube.offsetHeight || (this.currentBoardWidth * state.widthRatio * 0.45);
      const offset = baseHeight * this.config.cubeRules.stackOffsetMultiplier * stackIndex;
      cube.style.setProperty('--cube-stack-offset', `${offset}px`);
      cube.style.setProperty('--cube-stack-level', String(stackIndex));
    }

    clearCubeStackStyles(cube) {
      if (!cube) {
        return;
      }
      cube.style.removeProperty('--cube-stack-offset');
      cube.style.removeProperty('--cube-stack-level');
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
      this.updateTestButtonState();
    }

    runTest() {
      const placed = this.getPlacedCubes();
      if (!placed.length) {
        this.updateStatus('needBlocks');
        this.applyRotation(0, this.config.board.settleDurationMs);
        this.updateTestButtonState();
        return;
      }
      const totalCubes = this.cubes.size;
      if (totalCubes === 0 || placed.length < totalCubes) {
        this.updateStatus('needAllBlocks');
        this.applyRotation(0, this.config.board.settleDurationMs);
        this.updateTestButtonState();
        return;
      }
      this.isTesting = true;
      this.testsSinceReset += 1;
      this.updateTestButtonState();
      const result = this.evaluateBoard(placed);
      const offsetDisplay = formatNumber(Math.abs(result.centerOffset) * 100, 1);
      let statusKey = 'success';
      const tolerance = this.getCurrentTolerance();
      if (Math.abs(result.centerOffset) > tolerance) {
        statusKey = result.centerOffset < 0 ? 'leanLeft' : 'leanRight';
      }
      this.updateStatus(statusKey, { offset: offsetDisplay });
      this.applyRotation(result.rotation, this.config.board.testDurationMs);
      this.applyTiltState(statusKey);
      if (statusKey === 'success') {
        this.maybeAwardPerfectReward();
      }
      this.clearSettleTimeout();
      this.pendingSettleTimeout = window.setTimeout(() => {
        this.applyRotation(0, this.config.board.settleDurationMs);
        this.clearTiltState();
        this.pendingSettleTimeout = null;
        this.isTesting = false;
        this.updateTestButtonState();
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
      const rotation = this.simulateBoardRotation(cubes, {
        centerOffset,
        totalWeight,
        totalTorque
      });
      return { centerOffset, rotation, totalWeight, totalTorque };
    }

    legacyRotation(centerOffset) {
      const maxOffset = this.config.board.maxOffsetForTilt || DEFAULT_BOARD_CONFIG.maxOffsetForTilt;
      const normalized = clamp(centerOffset / maxOffset, -1, 1);
      const maxTilt = this.config.board.maxTiltDegrees || DEFAULT_BOARD_CONFIG.maxTiltDegrees;
      return normalized * maxTilt;
    }

    simulateBoardRotation(cubes, summary) {
      const fallback = this.legacyRotation(summary?.centerOffset || 0);
      const boardConfig = this.config?.board || {};
      const physics = boardConfig.physics;
      if (!physics) {
        return fallback;
      }
      const stepMs = Number(physics.simulationStepMs) || DEFAULT_BOARD_PHYSICS.simulationStepMs;
      const durationMs = Number(physics.simulationDurationMs) || DEFAULT_BOARD_PHYSICS.simulationDurationMs;
      const maxDegrees = boardConfig.maxTiltDegrees || DEFAULT_BOARD_CONFIG.maxTiltDegrees;
      const maxAngle = toRadians(maxDegrees);
      const length = Number(physics.lengthMeters) || DEFAULT_BOARD_PHYSICS.lengthMeters;
      const gravity = Number(physics.gravity) || DEFAULT_BOARD_PHYSICS.gravity;
      const boardMass = Math.max(Number(physics.boardMassKg) || DEFAULT_BOARD_PHYSICS.boardMassKg, 0.01);
      const cubeMassPerWeight = Math.max(
        Number(physics.cubeMassPerWeight) || DEFAULT_BOARD_PHYSICS.cubeMassPerWeight,
        0.01
      );
      const pivotFriction = Number(physics.pivotFriction) || DEFAULT_BOARD_PHYSICS.pivotFriction;
      const stiffness = Number(physics.stiffness) || DEFAULT_BOARD_PHYSICS.stiffness;
      const maxAngularVelocity = Math.max(
        Number(physics.maxAngularVelocity) || DEFAULT_BOARD_PHYSICS.maxAngularVelocity,
        0.1
      );
      const settleVelocity = Number(physics.settleThresholdVelocity) || DEFAULT_BOARD_PHYSICS.settleThresholdVelocity;
      const settleTorque = Number(physics.settleThresholdTorque) || DEFAULT_BOARD_PHYSICS.settleThresholdTorque;

      if (!Number.isFinite(length) || length <= 0 || !Number.isFinite(stepMs) || stepMs <= 0) {
        return fallback;
      }

      const dt = stepMs / 1000;
      const iterations = Math.max(Math.floor(durationMs / stepMs), 1);
      const baseInertia = (boardMass * (length ** 2)) / 12;
      let angle = clamp(toRadians(fallback), -maxAngle, maxAngle);
      let angularVelocity = 0;

      for (let i = 0; i < iterations; i += 1) {
        let torque = 0;
        let inertia = baseInertia;
        cubes.forEach(cube => {
          const weight = Number(cube.weight) || 0;
          if (!weight) {
            return;
          }
          const mass = weight * cubeMassPerWeight;
          const ratio = clamp(Number(cube.position) || 0, -0.5, 0.5);
          const distance = ratio * length;
          const leverArm = distance * Math.cos(angle);
          torque += mass * gravity * leverArm;
          inertia += mass * (distance ** 2);
        });

        const restoringTorque = -stiffness * angle;
        const dampingTorque = -pivotFriction * angularVelocity;
        const netTorque = torque + restoringTorque + dampingTorque;
        const angularAcceleration = netTorque / Math.max(inertia, 0.0001);
        angularVelocity += angularAcceleration * dt;
        angularVelocity = clamp(angularVelocity, -maxAngularVelocity, maxAngularVelocity);
        angle += angularVelocity * dt;

        if (angle > maxAngle) {
          angle = maxAngle;
          angularVelocity = 0;
        } else if (angle < -maxAngle) {
          angle = -maxAngle;
          angularVelocity = 0;
        }

        if (Math.abs(angularVelocity) < settleVelocity && Math.abs(netTorque) < settleTorque) {
          break;
        }
      }

      const rotationDegrees = clamp(toDegrees(angle), -maxDegrees, maxDegrees);
      return Number.isFinite(rotationDegrees) ? rotationDegrees : fallback;
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
      const statusVisualElement = this.statusButton || this.statusElement;
      statusVisualElement.classList.remove(
        'balance-status--success',
        'balance-status--warning',
        'balance-status--error'
      );
      if (key === 'success') {
        statusVisualElement.classList.add('balance-status--success');
      } else if (key === 'leanLeft' || key === 'leanRight') {
        statusVisualElement.classList.add('balance-status--warning');
      } else if (key === 'needBlocks' || key === 'needAllBlocks') {
        statusVisualElement.classList.add('balance-status--error');
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
      this.updateTestButtonState();
    }

    onLeave() {
      this.clearSettleTimeout();
      this.applyRotation(0, this.config.board.settleDurationMs);
      this.clearTiltState();
      this.isTesting = false;
      this.updateTestButtonState();
    }

    areAllCubesPlaced() {
      if (!this.cubes || this.cubes.size === 0) {
        return false;
      }
      let placed = 0;
      this.cubes.forEach(state => {
        if (state.location === 'board') {
          placed += 1;
        }
      });
      return placed === this.cubes.size;
    }

    updateTestButtonState() {
      if (!this.testButton) {
        return;
      }
      const allPlaced = this.areAllCubesPlaced();
      const disabled = this.isTesting || !allPlaced;
      this.testButton.disabled = disabled;
      if (disabled) {
        this.testButton.setAttribute('aria-disabled', 'true');
      } else {
        this.testButton.removeAttribute('aria-disabled');
      }
    }

    maybeAwardPerfectReward() {
      if (this.successRewardClaimed) {
        return;
      }
      const rules = this.config?.rewards?.perfectBalance;
      if (!rules) {
        return;
      }
      const maxAttempts = Number(rules.maxAttempts) || 0;
      if (maxAttempts > 0 && this.testsSinceReset > maxAttempts) {
        return;
      }
      if (!this.areAllCubesPlaced()) {
        return;
      }
      const amount = Number(rules.ticketAmount) || 0;
      if (amount <= 0 || typeof gainBonusParticulesTickets !== 'function') {
        return;
      }
      const gained = gainBonusParticulesTickets(amount);
      if (gained <= 0) {
        return;
      }
      this.successRewardClaimed = true;
      if (typeof showToast === 'function') {
        const messageKey = gained === 1
          ? 'scripts.arcade.balance.toast.perfectReward.single'
          : 'scripts.arcade.balance.toast.perfectReward.multiple';
        const fallback = gained === 1
          ? 'Équilibre parfait ! Ticket Mach3 gagné.'
          : 'Équilibre parfait ! {count} tickets Mach3 gagnés.';
        const message = translate(messageKey, fallback, { count: formatNumber(gained) });
        showToast(message);
      }
    }
  }

  window.BalanceGame = BalanceGame;
})();
