(function () {
  const GAME_ID = 'bigger';
  const LOCAL_STORAGE_KEY = 'atom2univers.arcade.bigger';
  const COLUMN_COUNT = 8;
  const ROW_COUNT = 14;
  const QUEUE_LENGTH = 3;
  const MAX_VALUE = 1024;
  const VALUE_ORDER = [1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024];
  const SPAWN_VALUES = [1, 2, 4, 8, 16];
  const BALL_SIZE_STEPS = [0.68, 0.76, 0.88, 0.98, 1.08, 1.22, 1.36, 1.5, 1.64, 1.78, 1.92];
  const DROP_ANIMATION_MS = 280;
  const MERGE_ANIMATION_MS = 180;

  const toInteger = value => {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return 0;
    }
    return Math.trunc(parsed);
  };

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined') {
      if (typeof window.translateOrDefault === 'function') {
        return window.translateOrDefault(key, fallback, params);
      }
      if (window.i18n && typeof window.i18n.t === 'function') {
        try {
          const result = window.i18n.t(key, params);
          if (result != null) {
            return result;
          }
        } catch (error) {
          // Ignore translation errors and fall back to default string
        }
      }
    }
    return fallback;
  }

  function getGlobalGameState() {
    if (typeof window === 'undefined') {
      return null;
    }
    if (window.atom2universGameState && typeof window.atom2universGameState === 'object') {
      return window.atom2universGameState;
    }
    if (window.gameState && typeof window.gameState === 'object') {
      return window.gameState;
    }
    return null;
  }

  function requestSave() {
    if (typeof window === 'undefined') {
      return;
    }
    if (typeof window.atom2universSaveGame === 'function') {
      window.atom2universSaveGame();
      return;
    }
    if (typeof window.saveGame === 'function') {
      window.saveGame();
    }
  }

  function clampQueue(values) {
    if (!Array.isArray(values)) {
      return [];
    }
    const sanitized = [];
    values.forEach(value => {
      const numeric = Number(value);
      if (VALUE_ORDER.includes(numeric)) {
        sanitized.push(numeric);
      }
    });
    return sanitized.slice(0, 12);
  }

  function createEmptyGrid() {
    return Array.from({ length: ROW_COUNT }, () => Array(COLUMN_COUNT).fill(null));
  }

  function deepClone(value) {
    if (value == null) {
      return value;
    }
    try {
      return JSON.parse(JSON.stringify(value));
    } catch (error) {
      return null;
    }
  }

  function readStoredState() {
    if (typeof window === 'undefined') {
      return null;
    }

    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.get === 'function') {
      const stored = window.ArcadeAutosave.get(GAME_ID);
      if (stored && typeof stored === 'object') {
        return deepClone(stored);
      }
    }

    const globalState = getGlobalGameState();
    if (globalState && globalState.arcadeProgress && typeof globalState.arcadeProgress === 'object') {
      const entries = globalState.arcadeProgress.entries && typeof globalState.arcadeProgress.entries === 'object'
        ? globalState.arcadeProgress.entries
        : globalState.arcadeProgress;
      const entry = entries && entries[GAME_ID];
      if (entry && typeof entry === 'object') {
        const payload = entry.state && typeof entry.state === 'object' ? entry.state : entry;
        if (payload && typeof payload === 'object') {
          return deepClone(payload);
        }
      }
    }

    if (window.localStorage) {
      try {
        const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && typeof parsed === 'object') {
            return parsed;
          }
        }
      } catch (error) {
        // Ignore malformed storage
      }
    }

    return null;
  }

  function writeStoredState(state) {
    if (typeof window === 'undefined') {
      return;
    }
    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.set === 'function') {
      try {
        window.ArcadeAutosave.set(GAME_ID, state);
      } catch (error) {
        // Ignore autosave errors (private mode, quota, ...)
      }
    }
    if (window.localStorage) {
      try {
        window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(state));
      } catch (error) {
        // Ignore storage failures
      }
    }
  }

  function getTier(value) {
    const index = VALUE_ORDER.indexOf(value);
    if (index >= 0) {
      return index;
    }
    const normalized = Number(value);
    if (!Number.isFinite(normalized) || normalized <= 0) {
      return 0;
    }
    const fallback = Math.max(0, Math.log2(normalized));
    return Math.min(VALUE_ORDER.length - 1, Math.round(fallback));
  }

  function getDiameterMultiplier(value) {
    const tier = getTier(value);
    const index = Math.max(0, Math.min(BALL_SIZE_STEPS.length - 1, tier));
    return BALL_SIZE_STEPS[index];
  }

  function nextAnimationFrame() {
    return new Promise(resolve => requestAnimationFrame(resolve));
  }

  class BiggerGame {
    constructor(options) {
      const opts = options || {};
      this.pageElement = opts.pageElement || null;
      this.boardElement = opts.boardElement || null;
      this.dropButtons = Array.from(opts.dropButtons || []);
      this.queueSlots = Array.from(opts.queueSlots || []);
      this.currentValueElement = opts.currentValueElement || null;
      this.largestValueElement = opts.largestValueElement || null;
      this.turnValueElement = opts.turnValueElement || null;
      this.mergeValueElement = opts.mergeValueElement || null;
      this.goalValueElement = opts.goalValueElement || null;
      this.overlayElement = opts.overlayElement || null;
      this.overlayTitleElement = opts.overlayTitleElement || null;
      this.overlayMessageElement = opts.overlayMessageElement || null;
      this.overlayActionElement = opts.overlayActionElement || null;
      this.overlayDismissElement = opts.overlayDismissElement || null;

      this.state = {
        grid: createEmptyGrid(),
        queue: [],
        currentValue: null,
        stats: { turns: 0, merges: 0, largest: 0 },
        isGameOver: false,
        gameResult: null,
        victoryAchieved: false
      };

      this.balls = new Map();
      this.ballIdCounter = 1;
      this.isAnimating = false;
      this.cellSize = 56;
      this.hoverColumn = null;
      this.isActive = false;

      this.highlightElement = document.createElement('div');
      this.highlightElement.className = 'bigger-board__highlight';
      if (this.boardElement) {
        this.boardElement.appendChild(this.highlightElement);
      }

      this.handleDropClick = this.handleDropClick.bind(this);
      this.handleDropEnter = this.handleDropEnter.bind(this);
      this.handleDropLeave = this.handleDropLeave.bind(this);
      this.handleResize = this.handleResize.bind(this);
      this.handleOverlayAction = this.handleOverlayAction.bind(this);
      this.handleOverlayDismiss = this.handleOverlayDismiss.bind(this);

      this.prefersReducedMotionQuery = typeof window !== 'undefined' && window.matchMedia
        ? window.matchMedia('(prefers-reduced-motion: reduce)')
        : null;
      this.prefersReducedMotion = this.prefersReducedMotionQuery?.matches || false;
      if (this.prefersReducedMotionQuery && typeof this.prefersReducedMotionQuery.addEventListener === 'function') {
        this.prefersReducedMotionQuery.addEventListener('change', event => {
          this.prefersReducedMotion = !!event.matches;
        });
      }

      this.languageUnsubscribe = null;
      if (typeof window !== 'undefined' && window.i18n && typeof window.i18n.onLanguageChanged === 'function') {
        this.languageUnsubscribe = window.i18n.onLanguageChanged(() => {
          this.updateDropButtonLabels();
          this.updateOverlayTexts();
        });
      }

      this.bindDomListeners();
      this.ensureBoardAttributes();
      this.updateGoalValue();

      const stored = readStoredState();
      if (stored) {
        this.hydrateState(stored, { skipPersist: true });
      } else {
        this.resetGame({ skipPersist: true });
      }

      this.ensureCurrentValue();
      this.updateBoardMetrics();
      this.renderBoardFromState();
      this.updateHud();
      if (this.state.gameResult) {
        this.showOverlayForResult(this.state.gameResult, { skipPersist: true });
      } else {
        this.hideOverlay();
      }
      this.persistState();
    }

    bindDomListeners() {
      this.dropButtons.forEach((button, index) => {
        button.addEventListener('click', this.handleDropClick);
        button.addEventListener('mouseenter', this.handleDropEnter);
        button.addEventListener('mouseleave', this.handleDropLeave);
        button.addEventListener('focus', this.handleDropEnter);
        button.addEventListener('blur', this.handleDropLeave);
        button.dataset.column = String(index);
      });

      if (this.overlayActionElement) {
        this.overlayActionElement.addEventListener('click', this.handleOverlayAction);
      }
      if (this.overlayDismissElement) {
        this.overlayDismissElement.addEventListener('click', this.handleOverlayDismiss);
      }

      if (typeof window !== 'undefined') {
        window.addEventListener('resize', this.handleResize);
      }
    }

    ensureBoardAttributes() {
      if (!this.boardElement) {
        return;
      }
      this.boardElement.style.setProperty('--bigger-cols', String(COLUMN_COUNT));
      this.boardElement.style.setProperty('--bigger-rows', String(ROW_COUNT));
    }

    updateGoalValue() {
      if (!this.goalValueElement) {
        return;
      }
      this.goalValueElement.textContent = String(MAX_VALUE);
    }

    handleDropClick(event) {
      const button = event.currentTarget;
      if (!button || typeof button.dataset.column === 'undefined') {
        return;
      }
      const column = Number(button.dataset.column);
      this.dropInColumn(column);
    }

    handleDropEnter(event) {
      const column = Number(event.currentTarget?.dataset?.column);
      if (!Number.isFinite(column)) {
        return;
      }
      this.setHoverColumn(column);
    }

    handleDropLeave() {
      this.setHoverColumn(null);
    }

    handleResize() {
      this.updateBoardMetrics();
      this.syncBallPositions();
    }

    handleOverlayAction() {
      this.resetGame();
      this.hideOverlay();
    }

    handleOverlayDismiss() {
      if (this.state.gameResult === 'victory') {
        this.state.gameResult = null;
        this.state.isGameOver = false;
        this.persistState();
      }
      this.hideOverlay();
    }

    setHoverColumn(column) {
      if (!this.highlightElement || !this.boardElement) {
        return;
      }
      if (!Number.isFinite(column) || column < 0 || column >= COLUMN_COUNT) {
        this.hoverColumn = null;
        this.highlightElement.classList.remove('is-visible');
        return;
      }
      this.hoverColumn = column;
      const width = this.boardElement.clientWidth;
      if (width > 0) {
        const cellWidth = width / COLUMN_COUNT;
        this.highlightElement.style.left = `${column * cellWidth}px`;
        this.highlightElement.style.width = `${cellWidth}px`;
      }
      this.highlightElement.classList.add('is-visible');
    }

    ensureCurrentValue() {
      this.fillQueue();
      if (!VALUE_ORDER.includes(this.state.currentValue)) {
        this.state.currentValue = this.state.queue.shift() ?? SPAWN_VALUES[0];
      }
      this.fillQueue();
    }

    fillQueue() {
      while (this.state.queue.length < QUEUE_LENGTH) {
        this.state.queue.push(this.generateRandomValue());
      }
    }

    generateRandomValue() {
      const index = Math.floor(Math.random() * SPAWN_VALUES.length);
      const value = SPAWN_VALUES[index];
      return VALUE_ORDER.includes(value) ? value : SPAWN_VALUES[0];
    }

    async dropInColumn(columnIndex) {
      if (this.isAnimating || this.state.isGameOver) {
        return;
      }
      if (!Number.isFinite(columnIndex) || columnIndex < 0 || columnIndex >= COLUMN_COUNT) {
        return;
      }

      this.ensureCurrentValue();
      const landingRow = this.findLandingRow(columnIndex);
      if (landingRow < 0) {
        this.triggerDefeat();
        return;
      }

      const value = this.state.currentValue;
      this.state.currentValue = null;
      this.ensureCurrentValue();

      this.isAnimating = true;
      this.updateDropButtonLabels();
      this.state.stats.turns += 1;

      const ball = this.createBall(value);
      ball.col = columnIndex;
      this.setBallPosition(ball, -1, columnIndex, { immediate: true });
      ball.element.dataset.state = 'spawn';
      await nextAnimationFrame();
      await this.animateTo(ball, landingRow, columnIndex);
      ball.row = landingRow;
      this.grid[landingRow][columnIndex] = ball;

      this.state.stats.largest = Math.max(this.state.stats.largest, value);
      await this.resolveMerges(ball);
      this.updateHud();

      if (!this.state.gameResult && this.state.stats.largest >= MAX_VALUE && !this.state.victoryAchieved) {
        this.handleVictory();
      }

      this.persistState();
      this.isAnimating = false;
      this.updateDropButtonLabels();
    }

    findLandingRow(columnIndex) {
      for (let row = ROW_COUNT - 1; row >= 0; row -= 1) {
        if (!this.grid[row][columnIndex]) {
          return row;
        }
      }
      return -1;
    }

    async resolveMerges(ball) {
      if (!ball) {
        return;
      }
      let current = ball;
      let chained = false;

      while (current) {
        const cluster = this.collectCluster(current.row, current.col, current.value);
        if (cluster.length < 2) {
          break;
        }

        chained = true;
        this.state.stats.merges += Math.max(1, cluster.length - 1);
        const newValue = Math.min(MAX_VALUE, current.value * 2);
        const anchor = this.selectMergeAnchor(cluster, current);

        await this.animateMerge(cluster);
        this.removeCluster(cluster);
        await this.collapseColumns(cluster.map(cell => cell.col));

        const mergedBall = this.createBall(newValue);
        const landingColumn = Number.isFinite(anchor?.col) ? anchor.col : current.col;
        const landingRow = this.findLandingRow(landingColumn);
        this.setBallPosition(mergedBall, -1, landingColumn, { immediate: true });
        mergedBall.element.dataset.state = 'spawn';
        await nextAnimationFrame();
        await this.animateTo(mergedBall, landingRow, landingColumn);
        mergedBall.row = landingRow;
        mergedBall.col = landingColumn;
        this.grid[landingRow][landingColumn] = mergedBall;

        this.state.stats.largest = Math.max(this.state.stats.largest, newValue);
        current = mergedBall;
      }

      if (chained) {
        this.updateHud();
        if (!this.state.gameResult && this.state.stats.largest >= MAX_VALUE && !this.state.victoryAchieved) {
          this.handleVictory();
        }
      }
    }

    collectCluster(row, col, value) {
      const cluster = [];
      if (!Number.isFinite(row) || !Number.isFinite(col)) {
        return cluster;
      }
      const targetValue = VALUE_ORDER.includes(value) ? value : null;
      if (targetValue == null) {
        return cluster;
      }
      const visited = new Set();
      const queue = [{ row, col }];
      while (queue.length) {
        const current = queue.pop();
        const key = `${current.row}:${current.col}`;
        if (visited.has(key)) {
          continue;
        }
        visited.add(key);
        if (
          current.row < 0
          || current.row >= ROW_COUNT
          || current.col < 0
          || current.col >= COLUMN_COUNT
        ) {
          continue;
        }
        const cell = this.grid[current.row][current.col];
        if (!cell || cell.value !== targetValue) {
          continue;
        }
        cluster.push(cell);
        queue.push({ row: current.row - 1, col: current.col });
        queue.push({ row: current.row + 1, col: current.col });
        queue.push({ row: current.row, col: current.col - 1 });
        queue.push({ row: current.row, col: current.col + 1 });
      }
      return cluster;
    }

    selectMergeAnchor(cluster, fallback) {
      if (!Array.isArray(cluster) || !cluster.length) {
        return fallback || null;
      }
      let anchor = cluster[0];
      cluster.forEach(cell => {
        if (!anchor) {
          anchor = cell;
          return;
        }
        if (cell.row > anchor.row) {
          anchor = cell;
          return;
        }
        if (cell.row === anchor.row && Math.abs(cell.col - fallback.col) < Math.abs(anchor.col - fallback.col)) {
          anchor = cell;
        }
      });
      return anchor;
    }

    async animateMerge(cluster) {
      if (!Array.isArray(cluster) || !cluster.length) {
        return;
      }
      cluster.forEach(cell => {
        if (cell?.element) {
          cell.element.dataset.state = 'merging';
        }
      });
      if (this.prefersReducedMotion) {
        return;
      }
      await new Promise(resolve => setTimeout(resolve, MERGE_ANIMATION_MS));
    }

    removeCluster(cluster) {
      if (!Array.isArray(cluster)) {
        return;
      }
      cluster.forEach(cell => {
        if (!cell) {
          return;
        }
        if (Number.isFinite(cell.row) && Number.isFinite(cell.col)) {
          if (this.grid[cell.row][cell.col] === cell) {
            this.grid[cell.row][cell.col] = null;
          }
        }
        if (cell.element && cell.element.parentNode) {
          cell.element.remove();
        }
        this.balls.delete(cell.id);
      });
    }

    async collapseColumns(columns) {
      const columnSet = new Set(columns);
      const animations = [];
      columnSet.forEach(col => {
        if (!Number.isFinite(col) || col < 0 || col >= COLUMN_COUNT) {
          return;
        }
        const columnBalls = [];
        for (let row = ROW_COUNT - 1; row >= 0; row -= 1) {
          const cell = this.grid[row][col];
          if (cell) {
            columnBalls.push(cell);
            this.grid[row][col] = null;
          }
        }
        let targetRow = ROW_COUNT - 1;
        columnBalls.forEach(ball => {
          this.grid[targetRow][col] = ball;
          if (ball.row !== targetRow) {
            animations.push(this.animateTo(ball, targetRow, col));
            ball.row = targetRow;
            ball.col = col;
          }
          targetRow -= 1;
        });
        for (; targetRow >= 0; targetRow -= 1) {
          this.grid[targetRow][col] = null;
        }
      });
      if (animations.length) {
        await Promise.all(animations);
      }
    }

    createBall(value) {
      const tier = getTier(value);
      const element = document.createElement('div');
      element.className = `bigger-ball bigger-ball--tier-${tier}`;
      element.dataset.value = String(value);
      element.setAttribute('aria-hidden', 'true');
      if (this.boardElement) {
        this.boardElement.appendChild(element);
      }
      const ball = {
        id: `ball-${this.ballIdCounter++}`,
        value,
        row: -1,
        col: -1,
        element
      };
      this.balls.set(ball.id, ball);
      return ball;
    }

    setBallPosition(ball, row, col, options = {}) {
      if (!ball || !ball.element) {
        return;
      }
      const { immediate = false } = options;
      const multiplier = getDiameterMultiplier(ball.value);
      const diameter = Math.max(28, this.cellSize * multiplier);
      const clampedRow = Number.isFinite(row) ? row : ball.row;
      const clampedCol = Number.isFinite(col) ? col : ball.col;
      const left = clampedCol * this.cellSize + (this.cellSize - diameter) / 2;
      const topBase = clampedRow * this.cellSize + (this.cellSize - diameter) / 2;
      const top = clampedRow >= 0 ? topBase : -diameter - this.cellSize * 0.2;

      if (immediate && this.prefersReducedMotion !== false) {
        ball.element.style.transition = 'none';
      }
      ball.element.style.width = `${diameter}px`;
      ball.element.style.height = `${diameter}px`;
      ball.element.style.left = `${left}px`;
      ball.element.style.top = `${top}px`;
      if (immediate && this.prefersReducedMotion !== false) {
        void ball.element.offsetWidth;
        ball.element.style.transition = '';
      }
    }

    animateTo(ball, row, col) {
      if (!ball || !ball.element) {
        return Promise.resolve();
      }
      if (this.prefersReducedMotion) {
        this.setBallPosition(ball, row, col, { immediate: true });
        return Promise.resolve();
      }
      return new Promise(resolve => {
        let resolved = false;
        const cleanup = () => {
          if (resolved) return;
          resolved = true;
          ball.element.removeEventListener('transitionend', onTransitionEnd);
          ball.element.dataset.state = '';
          resolve();
        };
        const onTransitionEnd = event => {
          if (event.target === ball.element && (event.propertyName === 'top' || event.propertyName === 'left')) {
            cleanup();
          }
        };
        ball.element.addEventListener('transitionend', onTransitionEnd);
        this.setBallPosition(ball, row, col);
        ball.element.dataset.state = '';
        setTimeout(cleanup, DROP_ANIMATION_MS + 80);
      });
    }

    updateHud() {
      if (this.currentValueElement) {
        const value = this.state.currentValue;
        this.currentValueElement.textContent = VALUE_ORDER.includes(value) ? String(value) : '—';
      }
      if (this.largestValueElement) {
        this.largestValueElement.textContent = String(this.state.stats.largest || 0);
      }
      if (this.turnValueElement) {
        this.turnValueElement.textContent = String(this.state.stats.turns || 0);
      }
      if (this.mergeValueElement) {
        this.mergeValueElement.textContent = String(this.state.stats.merges || 0);
      }
      this.renderQueue();
      this.updateDropButtonLabels();
    }

    renderQueue() {
      const fallback = translate('index.sections.bigger.queue.empty', '—');
      this.queueSlots.forEach((slot, index) => {
        if (!slot) {
          return;
        }
        const value = this.state.queue[index];
        if (VALUE_ORDER.includes(value)) {
          slot.textContent = String(value);
          slot.dataset.value = String(value);
          const label = translate(
            'index.sections.bigger.queue.slotLabel',
            'Bille à venir numéro {{position}} : {{value}}',
            { position: index + 1, value }
          );
          slot.setAttribute('aria-label', label);
        } else {
          slot.textContent = fallback;
          slot.dataset.value = '';
          slot.removeAttribute('aria-label');
        }
      });
    }

    updateDropButtonLabels() {
      this.dropButtons.forEach((button, index) => {
        const columnNumber = index + 1;
        const label = translate(
          'index.sections.bigger.dropButton',
          'Lâcher la bille dans la colonne {{column}}',
          { column: columnNumber }
        );
        if (label) {
          button.setAttribute('aria-label', label);
          button.setAttribute('title', label);
        }
        button.disabled = this.state.isGameOver || this.isAnimating;
      });
    }

    updateBoardMetrics() {
      if (!this.boardElement) {
        return;
      }
      const width = this.boardElement.clientWidth || this.boardElement.parentElement?.clientWidth || 480;
      const computedCell = width / COLUMN_COUNT;
      this.cellSize = Math.max(36, Math.min(82, computedCell));
      this.boardElement.style.setProperty('--bigger-cell-size', `${this.cellSize}px`);
      if (this.hoverColumn != null) {
        this.setHoverColumn(this.hoverColumn);
      }
    }

    syncBallPositions() {
      this.balls.forEach(ball => {
        this.setBallPosition(ball, ball.row, ball.col, { immediate: true });
      });
    }

    renderBoardFromState() {
      if (!this.boardElement) {
        return;
      }
      this.clearBoard();
      for (let row = 0; row < ROW_COUNT; row += 1) {
        for (let col = 0; col < COLUMN_COUNT; col += 1) {
          const value = this.state.grid[row]?.[col];
          if (!VALUE_ORDER.includes(value)) {
            this.grid[row][col] = null;
            continue;
          }
          const ball = this.createBall(value);
          ball.row = row;
          ball.col = col;
          this.grid[row][col] = ball;
          this.setBallPosition(ball, row, col, { immediate: true });
        }
      }
    }

    clearBoard() {
      this.balls.forEach(ball => {
        if (ball.element && ball.element.parentNode) {
          ball.element.remove();
        }
      });
      this.balls.clear();
      this.grid = createEmptyGrid();
    }

    resetGame(options = {}) {
      const { skipPersist = false } = options;
      this.clearBoard();
      this.state = {
        grid: createEmptyGrid(),
        queue: [],
        currentValue: null,
        stats: { turns: 0, merges: 0, largest: 0 },
        isGameOver: false,
        gameResult: null,
        victoryAchieved: false
      };
      this.fillQueue();
      this.ensureCurrentValue();
      this.renderBoardFromState();
      this.updateHud();
      this.hideOverlay();
      if (!skipPersist) {
        this.persistState();
      }
    }

    handleVictory() {
      if (this.state.victoryAchieved) {
        return;
      }
      this.state.victoryAchieved = true;
      this.state.gameResult = 'victory';
      this.state.isGameOver = true;
      this.showOverlayForResult('victory');
    }

    triggerDefeat() {
      this.state.gameResult = 'defeat';
      this.state.isGameOver = true;
      this.showOverlayForResult('defeat');
    }

    showOverlayForResult(result, options = {}) {
      const { skipPersist = false } = options;
      if (!this.overlayElement) {
        return;
      }
      let title;
      let message;
      if (result === 'victory') {
        title = translate('index.sections.bigger.overlay.victory.title', 'Objectif atteint !');
        message = translate(
          'index.sections.bigger.overlay.victory.message',
          'Vous avez forgé la bille {{value}} en {{turns}} lancers. Continuez pour repousser les limites !',
          { value: MAX_VALUE, turns: this.state.stats.turns }
        );
        if (this.overlayDismissElement) {
          this.overlayDismissElement.hidden = false;
        }
      } else {
        title = translate('index.sections.bigger.overlay.defeat.title', 'Bac saturé');
        message = translate(
          'index.sections.bigger.overlay.defeat.message',
          'Plus aucune colonne n’est disponible. Relancez une partie pour tenter de mieux organiser les fusions.'
        );
        if (this.overlayDismissElement) {
          this.overlayDismissElement.hidden = false;
        }
      }
      if (this.overlayTitleElement) {
        this.overlayTitleElement.textContent = title;
      }
      if (this.overlayMessageElement) {
        this.overlayMessageElement.textContent = message;
      }
      this.overlayElement.classList.add('is-visible');
      this.overlayElement.hidden = false;
      if (!skipPersist) {
        this.persistState();
      }
    }

    updateOverlayTexts() {
      if (!this.state.gameResult || !this.overlayElement || this.overlayElement.hidden) {
        return;
      }
      this.showOverlayForResult(this.state.gameResult, { skipPersist: true });
    }

    hideOverlay() {
      if (!this.overlayElement) {
        return;
      }
      this.overlayElement.classList.remove('is-visible');
      this.overlayElement.hidden = true;
    }

    persistState() {
      const payload = this.serializeState();
      writeStoredState(payload);
      const globalState = getGlobalGameState();
      if (globalState) {
        if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
          globalState.arcadeProgress = { version: 1, entries: {} };
        }
        if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
          globalState.arcadeProgress.entries = {};
        }
        globalState.arcadeProgress.entries[GAME_ID] = {
          state: deepClone(payload),
          updatedAt: Date.now()
        };
      }
      requestSave();
    }

    serializeState() {
      const gridValues = this.grid.map(row => row.map(cell => (cell ? cell.value : 0)));
      return {
        version: 1,
        grid: gridValues,
        queue: this.state.queue.slice(0, 12),
        currentValue: VALUE_ORDER.includes(this.state.currentValue) ? this.state.currentValue : null,
        stats: {
          turns: toInteger(this.state.stats.turns),
          merges: toInteger(this.state.stats.merges),
          largest: VALUE_ORDER.includes(this.state.stats.largest)
            ? this.state.stats.largest
            : Math.max(...gridValues.flat())
        },
        isGameOver: !!this.state.isGameOver,
        gameResult: this.state.gameResult || null,
        victoryAchieved: !!this.state.victoryAchieved
      };
    }

    hydrateState(raw, options = {}) {
      const { skipPersist = false } = options;
      if (!raw || typeof raw !== 'object') {
        this.resetGame({ skipPersist: true });
        if (!skipPersist) {
          this.persistState();
        }
        return;
      }
      const gridSource = Array.isArray(raw.grid) ? raw.grid : [];
      this.state.grid = createEmptyGrid();
      this.clearBoard();

      for (let row = 0; row < ROW_COUNT; row += 1) {
        const rowValues = Array.isArray(gridSource[row]) ? gridSource[row] : [];
        for (let col = 0; col < COLUMN_COUNT; col += 1) {
          const value = Number(rowValues[col]);
          if (!VALUE_ORDER.includes(value)) {
            continue;
          }
          const ball = this.createBall(value);
          ball.row = row;
          ball.col = col;
          this.state.grid[row][col] = value;
          this.grid[row][col] = ball;
          this.setBallPosition(ball, row, col, { immediate: true });
        }
      }

      this.state.queue = clampQueue(raw.queue);
      this.state.currentValue = VALUE_ORDER.includes(raw.currentValue) ? raw.currentValue : null;
      this.state.stats = {
        turns: Math.max(0, toInteger(raw.stats?.turns)),
        merges: Math.max(0, toInteger(raw.stats?.merges)),
        largest: VALUE_ORDER.includes(raw.stats?.largest)
          ? raw.stats.largest
          : Math.max(...this.state.grid.flat().map(Number), 0)
      };
      this.state.isGameOver = raw.isGameOver === true;
      this.state.gameResult = typeof raw.gameResult === 'string' ? raw.gameResult : null;
      this.state.victoryAchieved = raw.victoryAchieved === true;

      this.ensureCurrentValue();
      this.updateHud();
      if (this.state.gameResult) {
        this.showOverlayForResult(this.state.gameResult, { skipPersist: true });
      } else {
        this.hideOverlay();
      }
      if (!skipPersist) {
        this.persistState();
      }
    }

    onEnter() {
      this.isActive = true;
      this.updateBoardMetrics();
      this.syncBallPositions();
      this.updateDropButtonLabels();
      this.updateOverlayTexts();
    }

    onLeave() {
      this.isActive = false;
      this.setHoverColumn(null);
    }

    dispose() {
      this.dropButtons.forEach(button => {
        button.removeEventListener('click', this.handleDropClick);
        button.removeEventListener('mouseenter', this.handleDropEnter);
        button.removeEventListener('mouseleave', this.handleDropLeave);
        button.removeEventListener('focus', this.handleDropEnter);
        button.removeEventListener('blur', this.handleDropLeave);
      });
      if (this.overlayActionElement) {
        this.overlayActionElement.removeEventListener('click', this.handleOverlayAction);
      }
      if (this.overlayDismissElement) {
        this.overlayDismissElement.removeEventListener('click', this.handleOverlayDismiss);
      }
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.handleResize);
      }
      if (this.languageUnsubscribe) {
        this.languageUnsubscribe();
      }
    }
  }

  window.BiggerGame = BiggerGame;
})();
