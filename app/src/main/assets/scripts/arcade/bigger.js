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
  const PHYSICS_MAX_STEP_MS = 16;
  const PHYSICS_GRAVITY = 1600;
  const PHYSICS_WALL_BOUNCE = 0.55;
  const PHYSICS_FLOOR_BOUNCE = 0.68;
  const PHYSICS_LINEAR_DAMPING = 0.995;
  const PHYSICS_STATIC_FRICTION = 0.8;
  const PHYSICS_DYNAMIC_FRICTION = 0.12;
  const MERGE_OVERLAP_THRESHOLD = 0.22;
  const MERGE_CONTACT_TIME = 0.18;
  const MERGE_RELATIVE_SPEED = 90;
  const DEFEAT_MARGIN = 96;

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
      this.restartButton = opts.restartButton || null;
      this.overlayElement = opts.overlayElement || null;
      this.overlayTitleElement = opts.overlayTitleElement || null;
      this.overlayMessageElement = opts.overlayMessageElement || null;
      this.overlayActionElement = opts.overlayActionElement || null;
      this.overlayDismissElement = opts.overlayDismissElement || null;

      this.state = {
        balls: [],
        queue: [],
        currentValue: null,
        stats: { turns: 0, merges: 0, largest: 0 },
        isGameOver: false,
        gameResult: null,
        victoryAchieved: false
      };

      this.balls = new Map();
      this.ballIdCounter = 1;
      this.cellSize = 56;
      this.boardWidth = 0;
      this.boardHeight = 0;
      this.spawnPositions = [];
      this.hoverColumn = null;
      this.isActive = false;

      this.physics = {
        running: false,
        lastTimestamp: null,
        rafId: null,
        contacts: new Map(),
        pendingMerges: []
      };

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
      this.handleRestartClick = this.handleRestartClick.bind(this);
      this.handleMotionPreferenceChange = this.handleMotionPreferenceChange.bind(this);

      this.prefersReducedMotionQuery = typeof window !== 'undefined' && window.matchMedia
        ? window.matchMedia('(prefers-reduced-motion: reduce)')
        : null;
      this.prefersReducedMotion = this.prefersReducedMotionQuery?.matches || false;
      if (this.prefersReducedMotionQuery && typeof this.prefersReducedMotionQuery.addEventListener === 'function') {
        this.prefersReducedMotionQuery.addEventListener('change', this.handleMotionPreferenceChange);
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
      this.startPhysics();
      if (this.prefersReducedMotion) {
        this.settleBallsInstantly();
        this.persistState();
      }
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

      if (this.restartButton) {
        this.restartButton.addEventListener('click', this.handleRestartClick);
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
      this.startPhysics();
      if (this.prefersReducedMotion) {
        this.settleBallsInstantly();
        this.persistState();
      }
    }

    handleOverlayDismiss() {
      if (this.state.gameResult === 'victory') {
        this.state.gameResult = null;
        this.state.isGameOver = false;
        this.persistState();
      }
      this.hideOverlay();
      if (!this.state.isGameOver) {
        this.startPhysics();
        if (this.prefersReducedMotion) {
          this.settleBallsInstantly();
          this.persistState();
        }
      }
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

    dropInColumn(columnIndex) {
      if (this.state.isGameOver) {
        return;
      }
      if (!Number.isFinite(columnIndex) || columnIndex < 0 || columnIndex >= COLUMN_COUNT) {
        return;
      }

      this.ensureCurrentValue();
      const value = this.state.currentValue;
      if (!VALUE_ORDER.includes(value)) {
        return;
      }

      const spawnX = this.spawnPositions[columnIndex] ?? (columnIndex * this.cellSize + this.cellSize / 2);
      const spawnY = -DEFEAT_MARGIN * 0.5;

      this.state.currentValue = null;
      this.ensureCurrentValue();
      this.state.stats.turns += 1;
      this.updateDropButtonLabels();

      const ball = this.createBall(value, { x: spawnX, y: spawnY });
      ball.element.dataset.state = 'spawn';
      this.applyBallPosition(ball);
      this.refreshSerializedBalls();

      window.requestAnimationFrame(() => {
        if (ball.element) {
          ball.element.dataset.state = '';
        }
      });

      this.state.stats.largest = Math.max(this.state.stats.largest, value);
      this.updateHud();
      if (this.prefersReducedMotion) {
        this.settleBallsInstantly();
        this.persistState();
        return;
      }
      this.persistState();
      this.startPhysics();
    }

    createBall(value, overrides = {}) {
      const tier = getTier(value);
      const element = document.createElement('div');
      element.className = `bigger-ball bigger-ball--tier-${tier}`;
      element.dataset.value = String(value);
      element.setAttribute('aria-hidden', 'true');
      if (this.boardElement) {
        this.boardElement.appendChild(element);
      }
      const multiplier = getDiameterMultiplier(value);
      const diameter = Math.max(28, this.cellSize * multiplier);
      element.style.width = `${diameter}px`;
      element.style.height = `${diameter}px`;
      const radius = diameter / 2;
      const mass = Math.max(1, radius * radius);
      const ball = {
        id: `ball-${this.ballIdCounter++}`,
        value,
        element,
        radius,
        mass,
        invMass: 1 / mass,
        x: Number.isFinite(overrides.x) ? overrides.x : radius,
        y: Number.isFinite(overrides.y) ? overrides.y : radius,
        vx: Number.isFinite(overrides.vx) ? overrides.vx : 0,
        vy: Number.isFinite(overrides.vy) ? overrides.vy : 0,
        overflowTime: 0
      };
      this.balls.set(ball.id, ball);
      return ball;
    }

    updateBallShape(ball) {
      if (!ball || !ball.element) {
        return;
      }
      const multiplier = getDiameterMultiplier(ball.value);
      const diameter = Math.max(28, this.cellSize * multiplier);
      ball.radius = diameter / 2;
      ball.mass = Math.max(1, ball.radius * ball.radius);
      ball.invMass = 1 / ball.mass;
      ball.element.style.width = `${diameter}px`;
      ball.element.style.height = `${diameter}px`;
    }

    applyBallPosition(ball) {
      if (!ball || !ball.element) {
        return;
      }
      const left = (Number.isFinite(ball.x) ? ball.x : 0) - (ball.radius || 0);
      const top = (Number.isFinite(ball.y) ? ball.y : 0) - (ball.radius || 0);
      ball.element.style.left = `${left}px`;
      ball.element.style.top = `${top}px`;
    }

    serializeBall(ball) {
      return {
        id: ball.id,
        value: ball.value,
        x: Number(ball.x) || 0,
        y: Number(ball.y) || 0,
        vx: Number(ball.vx) || 0,
        vy: Number(ball.vy) || 0
      };
    }

    refreshSerializedBalls() {
      this.state.balls = Array.from(this.balls.values()).map(ball => this.serializeBall(ball));
    }

    removeBall(ball) {
      if (!ball) {
        return;
      }
      this.balls.delete(ball.id);
      if (ball.element && ball.element.parentNode) {
        ball.element.remove();
      }
      const keys = Array.from(this.physics.contacts.keys());
      keys.forEach(key => {
        const [left, right] = key.split('|');
        if (left === ball.id || right === ball.id) {
          this.physics.contacts.delete(key);
        }
      });
    }

    queueMerge(ballA, ballB) {
      if (!ballA || !ballB || ballA === ballB) {
        return;
      }
      this.physics.pendingMerges.push([ballA.id, ballB.id]);
    }

    executePendingMerges() {
      if (!this.physics.pendingMerges.length) {
        return;
      }
      const pairs = this.physics.pendingMerges.splice(0, this.physics.pendingMerges.length);
      let merged = false;
      pairs.forEach(([idA, idB]) => {
        const ballA = this.balls.get(idA);
        const ballB = this.balls.get(idB);
        if (!ballA || !ballB || ballA.value !== ballB.value || this.state.isGameOver) {
          return;
        }
        this.mergeBalls(ballA, ballB);
        merged = true;
      });
      if (merged) {
        this.refreshSerializedBalls();
        this.persistState();
      }
    }

    mergeBalls(ballA, ballB) {
      const totalMass = ballA.mass + ballB.mass;
      const x = (ballA.x * ballA.mass + ballB.x * ballB.mass) / totalMass;
      const y = (ballA.y * ballA.mass + ballB.y * ballB.mass) / totalMass;
      const vx = (ballA.vx * ballA.mass + ballB.vx * ballB.mass) / totalMass;
      const vy = (ballA.vy * ballA.mass + ballB.vy * ballB.mass) / totalMass;
      const newValue = Math.min(MAX_VALUE, ballA.value * 2);

      this.removeBall(ballA);
      this.removeBall(ballB);

      const merged = this.createBall(newValue, { x, y, vx, vy });
      merged.element.dataset.state = 'spawn';
      this.applyBallPosition(merged);
      window.requestAnimationFrame(() => {
        if (merged.element) {
          merged.element.dataset.state = '';
        }
      });

      this.state.stats.merges += 1;
      this.state.stats.largest = Math.max(this.state.stats.largest, newValue);
      this.updateHud();
      if (!this.state.gameResult && this.state.stats.largest >= MAX_VALUE && !this.state.victoryAchieved) {
        this.handleVictory();
      }
    }

    handleRestartClick() {
      this.resetGame();
      this.startPhysics();
    }

    handleMotionPreferenceChange(event) {
      if (event && typeof event.matches === 'boolean') {
        this.prefersReducedMotion = event.matches;
      } else if (this.prefersReducedMotionQuery) {
        this.prefersReducedMotion = !!this.prefersReducedMotionQuery.matches;
      }
      if (this.prefersReducedMotion) {
        this.stopPhysics();
        this.settleBallsInstantly();
        this.syncBallPositions();
        this.persistState();
      } else {
        this.startPhysics();
      }
    }

    startPhysics() {
      if (typeof window === 'undefined' || this.prefersReducedMotion) {
        return;
      }
      if (this.physics.running) {
        return;
      }
      if (this.boardElement) {
        this.boardElement.classList.add('bigger-board--physics');
      }
      this.physics.running = true;
      this.physics.lastTimestamp = null;
      const step = timestamp => {
        if (!this.physics.running) {
          return;
        }
        this.stepPhysics(timestamp);
        this.physics.rafId = window.requestAnimationFrame(step);
      };
      this.physics.rafId = window.requestAnimationFrame(step);
    }

    stopPhysics() {
      if (typeof window === 'undefined') {
        return;
      }
      if (this.physics.rafId != null) {
        window.cancelAnimationFrame(this.physics.rafId);
        this.physics.rafId = null;
      }
      this.physics.running = false;
      this.physics.lastTimestamp = null;
      if (this.boardElement) {
        this.boardElement.classList.remove('bigger-board--physics');
      }
    }

    stepPhysics(timestamp) {
      if (this.prefersReducedMotion) {
        this.stopPhysics();
        return;
      }
      if (!this.balls.size) {
        return;
      }
      const previous = this.physics.lastTimestamp ?? timestamp;
      let delta = timestamp - previous;
      if (!Number.isFinite(delta) || delta <= 0) {
        delta = PHYSICS_MAX_STEP_MS;
      }
      delta = Math.min(delta, PHYSICS_MAX_STEP_MS);
      this.physics.lastTimestamp = timestamp;
      const dt = delta / 1000;
      if (dt <= 0) {
        return;
      }
      this.runPhysicsFrame(dt);
    }

    runPhysicsFrame(dt, options = {}) {
      const { syncState = true } = options;
      const width = this.boardWidth || this.boardElement?.clientWidth || this.cellSize * COLUMN_COUNT;
      const height = this.boardHeight || this.cellSize * ROW_COUNT;
      if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
        if (syncState) {
          this.refreshSerializedBalls();
        }
        return;
      }
      const balls = Array.from(this.balls.values());
      const contacts = this.physics.contacts;
      const activeContacts = new Set();

      balls.forEach(ball => {
        if (!Number.isFinite(ball.radius) || ball.radius <= 0) {
          this.updateBallShape(ball);
        }
        ball.vy += PHYSICS_GRAVITY * dt;
        ball.vx *= PHYSICS_LINEAR_DAMPING;
        ball.vy *= PHYSICS_LINEAR_DAMPING;
        ball.x += ball.vx * dt;
        ball.y += ball.vy * dt;

        const minX = ball.radius;
        const maxX = width - ball.radius;
        if (ball.x < minX) {
          ball.x = minX;
          ball.vx = Math.abs(ball.vx) * PHYSICS_WALL_BOUNCE;
        } else if (ball.x > maxX) {
          ball.x = maxX;
          ball.vx = -Math.abs(ball.vx) * PHYSICS_WALL_BOUNCE;
        }

        const maxY = height - ball.radius;
        if (ball.y > maxY) {
          ball.y = maxY;
          if (Math.abs(ball.vy) < 30) {
            ball.vy = 0;
            ball.vx *= 1 - PHYSICS_STATIC_FRICTION;
          } else {
            ball.vy = -ball.vy * PHYSICS_FLOOR_BOUNCE;
            ball.vx *= 1 - PHYSICS_DYNAMIC_FRICTION;
          }
        }

        const minY = -DEFEAT_MARGIN;
        if (ball.y < minY) {
          ball.y = minY;
          ball.vy = Math.max(ball.vy, 0);
        }

        if (ball.y - ball.radius < 0) {
          ball.overflowTime = (ball.overflowTime || 0) + dt;
        } else {
          ball.overflowTime = Math.max(0, (ball.overflowTime || 0) - dt * 0.5);
        }
      });

      for (let i = 0; i < balls.length; i += 1) {
        const a = balls[i];
        for (let j = i + 1; j < balls.length; j += 1) {
          const b = balls[j];
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const distance = Math.hypot(dx, dy) || 0.0001;
          const minDistance = (a.radius || 0) + (b.radius || 0);
          const key = a.id < b.id ? `${a.id}|${b.id}` : `${b.id}|${a.id}`;
          if (distance < minDistance && minDistance > 0) {
            const overlap = minDistance - distance;
            const nx = dx / distance;
            const ny = dy / distance;
            const invMassSum = a.invMass + b.invMass;
            if (invMassSum > 0) {
              const shiftA = overlap * (a.invMass / invMassSum);
              const shiftB = overlap * (b.invMass / invMassSum);
              a.x -= nx * shiftA;
              a.y -= ny * shiftA;
              b.x += nx * shiftB;
              b.y += ny * shiftB;

              const relativeVelocity = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
              const impulse = (-(1 + PHYSICS_FLOOR_BOUNCE) * relativeVelocity) / invMassSum;
              a.vx -= impulse * nx * a.invMass;
              a.vy -= impulse * ny * a.invMass;
              b.vx += impulse * nx * b.invMass;
              b.vy += impulse * ny * b.invMass;

              activeContacts.add(key);
              let contact = contacts.get(key);
              if (!contact) {
                contact = { time: 0 };
                contacts.set(key, contact);
              }
              const relativeSpeed = Math.abs(relativeVelocity);
              if (
                a.value === b.value
                && minDistance > 0
                && overlap / minDistance > MERGE_OVERLAP_THRESHOLD
                && relativeSpeed < MERGE_RELATIVE_SPEED
              ) {
                contact.time += dt;
                if (contact.time >= MERGE_CONTACT_TIME) {
                  this.queueMerge(a, b);
                  contact.time = 0;
                }
              } else {
                contact.time = Math.max(0, contact.time - dt * 0.5);
              }
            }
          }
        }
      }

      contacts.forEach((contact, key) => {
        if (!activeContacts.has(key)) {
          contacts.delete(key);
        }
      });

      this.executePendingMerges();
      balls.forEach(ball => {
        this.applyBallPosition(ball);
      });
      if (syncState) {
        this.refreshSerializedBalls();
      }

      if (!this.state.isGameOver && balls.some(ball => (ball.overflowTime || 0) > 0.75)) {
        this.triggerDefeat();
      }
    }

    settleBallsInstantly(iterations = 48) {
      let steps = Number.isFinite(iterations) ? Math.trunc(iterations) : 0;
      if (steps <= 0) {
        steps = 32;
      }
      steps = Math.min(steps, 160);
      for (let i = 0; i < steps; i += 1) {
        this.runPhysicsFrame(PHYSICS_MAX_STEP_MS / 1000, { syncState: false });
        if (this.state.isGameOver) {
          break;
        }
      }
      this.refreshSerializedBalls();
      this.syncBallPositions();
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
        button.disabled = this.state.isGameOver;
      });
    }

    updateBoardMetrics() {
      if (!this.boardElement) {
        return;
      }
      const width = this.boardElement.clientWidth || this.boardElement.parentElement?.clientWidth || 480;
      const computedCell = width / COLUMN_COUNT;
      const previousCell = this.cellSize || computedCell;
      this.cellSize = Math.max(36, Math.min(82, computedCell));
      this.boardWidth = this.cellSize * COLUMN_COUNT;
      this.boardHeight = this.cellSize * ROW_COUNT;
      this.spawnPositions = Array.from({ length: COLUMN_COUNT }, (_, index) => (index + 0.5) * this.cellSize);
      this.boardElement.style.setProperty('--bigger-cell-size', `${this.cellSize}px`);
      if (this.hoverColumn != null) {
        this.setHoverColumn(this.hoverColumn);
      }

      const scale = previousCell > 0 ? this.cellSize / previousCell : 1;
      this.balls.forEach(ball => {
        this.updateBallShape(ball);
        if (scale !== 1) {
          ball.x *= scale;
          ball.y *= scale;
          ball.vx *= scale;
          ball.vy *= scale;
        }
        this.applyBallPosition(ball);
      });
    }

    syncBallPositions() {
      this.balls.forEach(ball => {
        this.updateBallShape(ball);
        this.applyBallPosition(ball);
      });
    }

    renderBoardFromState() {
      if (!this.boardElement) {
        return;
      }
      this.clearBoard();
      const entries = Array.isArray(this.state.balls) ? this.state.balls : [];
      entries.forEach(entry => {
        if (!VALUE_ORDER.includes(entry.value)) {
          return;
        }
        const ball = this.createBall(entry.value, entry);
        this.updateBallShape(ball);
        ball.x = Number.isFinite(entry.x) ? entry.x : ball.x;
        ball.y = Number.isFinite(entry.y) ? entry.y : ball.y;
        ball.vx = Number.isFinite(entry.vx) ? entry.vx : 0;
        ball.vy = Number.isFinite(entry.vy) ? entry.vy : 0;
        this.applyBallPosition(ball);
      });
      this.refreshSerializedBalls();
    }

    clearBoard() {
      this.balls.forEach(ball => {
        if (ball.element && ball.element.parentNode) {
          ball.element.remove();
        }
      });
      this.balls.clear();
      this.physics.contacts.clear();
      this.physics.pendingMerges.length = 0;
    }

    resetGame(options = {}) {
      const { skipPersist = false } = options;
      this.clearBoard();
      this.state = {
        balls: [],
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
      this.stopPhysics();
      this.showOverlayForResult('victory');
      this.persistState();
    }

    triggerDefeat() {
      if (this.state.isGameOver) {
        return;
      }
      this.state.gameResult = 'defeat';
      this.state.isGameOver = true;
      this.stopPhysics();
      this.showOverlayForResult('defeat');
      this.persistState();
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
      const balls = Array.from(this.balls.values()).map(ball => this.serializeBall(ball));
      const largestBall = balls.reduce((max, entry) => Math.max(max, Number(entry.value) || 0), 0);
      return {
        version: 2,
        balls,
        queue: this.state.queue.slice(0, 12),
        currentValue: VALUE_ORDER.includes(this.state.currentValue) ? this.state.currentValue : null,
        stats: {
          turns: toInteger(this.state.stats.turns),
          merges: toInteger(this.state.stats.merges),
          largest: VALUE_ORDER.includes(this.state.stats.largest)
            ? this.state.stats.largest
            : largestBall
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

      this.clearBoard();
      const baseCell = this.cellSize || 56;
      const normalizedBalls = [];
      if (Array.isArray(raw.balls) && raw.balls.length) {
        raw.balls.forEach(entry => {
          const value = Number(entry.value);
          if (!VALUE_ORDER.includes(value)) {
            return;
          }
          normalizedBalls.push({
            value,
            x: Number.isFinite(entry.x) ? Number(entry.x) : baseCell * 0.5,
            y: Number.isFinite(entry.y) ? Number(entry.y) : baseCell * 0.5,
            vx: Number(entry.vx) || 0,
            vy: Number(entry.vy) || 0
          });
        });
      } else if (Array.isArray(raw.grid)) {
        raw.grid.forEach((rowValues, row) => {
          if (!Array.isArray(rowValues)) {
            return;
          }
          rowValues.forEach((value, col) => {
            const numeric = Number(value);
            if (!VALUE_ORDER.includes(numeric)) {
              return;
            }
            normalizedBalls.push({
              value: numeric,
              x: (col + 0.5) * baseCell,
              y: (row + 0.5) * baseCell,
              vx: 0,
              vy: 0
            });
          });
        });
      }
      this.state.balls = normalizedBalls;

      this.state.queue = clampQueue(raw.queue);
      this.state.currentValue = VALUE_ORDER.includes(raw.currentValue) ? raw.currentValue : null;
      const largestFromBalls = normalizedBalls.reduce((max, entry) => Math.max(max, Number(entry.value) || 0), 0);
      this.state.stats = {
        turns: Math.max(0, toInteger(raw.stats?.turns)),
        merges: Math.max(0, toInteger(raw.stats?.merges)),
        largest: VALUE_ORDER.includes(raw.stats?.largest)
          ? raw.stats.largest
          : largestFromBalls
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
      this.startPhysics();
    }

    onLeave() {
      this.isActive = false;
      this.setHoverColumn(null);
      this.stopPhysics();
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
      if (this.restartButton) {
        this.restartButton.removeEventListener('click', this.handleRestartClick);
      }
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.handleResize);
      }
      if (this.prefersReducedMotionQuery && typeof this.prefersReducedMotionQuery.removeEventListener === 'function') {
        this.prefersReducedMotionQuery.removeEventListener('change', this.handleMotionPreferenceChange);
      }
      if (this.languageUnsubscribe) {
        this.languageUnsubscribe();
      }
      this.stopPhysics();
    }
  }

  window.BiggerGame = BiggerGame;
})();
