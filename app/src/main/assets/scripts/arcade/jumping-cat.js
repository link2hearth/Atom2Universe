(() => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-jumping-cat-root]');
  if (!root) {
    return;
  }

  const elements = {
    page: document.getElementById('jumpingCat'),
    canvas: document.getElementById('jumpingCatCanvas'),
    header: root.querySelector('.jumping-cat__header'),
    overlay: document.getElementById('jumpingCatOverlay'),
    overlayTitle: document.getElementById('jumpingCatOverlayTitle'),
    overlayMessage: document.getElementById('jumpingCatOverlayMessage'),
    overlayAction: document.getElementById('jumpingCatOverlayAction'),
    overlayDismiss: document.getElementById('jumpingCatOverlayDismiss'),
    overlayBestScore: document.getElementById('jumpingCatOverlayBestScoreValue'),
    overlayBestTime: document.getElementById('jumpingCatOverlayBestTimeValue'),
    scoreValue: document.getElementById('jumpingCatScoreValue'),
    bestScoreValue: document.getElementById('jumpingCatBestScoreValue'),
    timeValue: document.getElementById('jumpingCatTimeValue'),
    bestTimeValue: document.getElementById('jumpingCatBestTimeValue'),
    status: document.getElementById('jumpingCatStatus'),
    actionButton: document.getElementById('jumpingCatStart')
  };

  const GAME_ID = 'jumpingCat';
  const CONFIG_PATH = 'config/arcade/jumping-cat.json';
  const CAT_SPRITE_PATH = 'Assets/sprites/Chat.png';
  const PYLON_SPRITE_PATH = 'Assets/sprites/Pylones.png';
  const BIRD_SPRITE_PATH = 'Assets/sprites/Bird.png';
  const BACKGROUND_SPRITE_PATH = 'Assets/sprites/FondChat.png';

  const DEFAULT_CONFIG = Object.freeze({
    gravity: 2400,
    jumpImpulse: 760,
    maxFallSpeed: 1800,
    forwardSpeed: 190,
    obstacleSpacing: { min: 1.6, max: 2.4 },
    pylonGap: { min: 135, max: 170 },
    safetyCorridor: 120,
    birdInterval: { min: 3, max: 5.5 },
    birdSpeed: { min: 150, max: 230 },
    birdWobble: 26,
    groundHeight: 60,
    backgroundScrollSpeed: 110,
    rewards: {
      gacha: {
        survivalIntervalSeconds: 45,
        ticketAmount: 1
      }
    }
  });

  const CAT_FRAME_WIDTH = 51;
  const CAT_FRAME_HEIGHT = 34;
  const CANVAS_WIDTH = 420;
  const CANVAS_HEIGHT = 560;
  const PYLON_SLICES = [
    { sx: 0, sw: 114 },
    { sx: 115, sw: 118 },
    { sx: 233, sw: 42 },
    { sx: 275, sw: 44 },
    { sx: 319, sw: 40 }
  ];
  const PYLON_HEIGHT = 78;
  const BIRD_FRAMES = [
    { sx: 0, sy: 0, sw: 128, sh: 128, size: 72 },
    { sx: 0, sy: 128, sw: 128, sh: 128, size: 72 },
    { sx: 128, sy: 0, sw: 128, sh: 128, size: 72 },
    { sx: 128, sy: 128, sw: 128, sh: 128, size: 72 }
  ];
  const BIRD_ANIMATIONS = [
    [BIRD_FRAMES[0], BIRD_FRAMES[1]],
    [BIRD_FRAMES[2], BIRD_FRAMES[3]]
  ];

  const state = {
    config: DEFAULT_CONFIG,
    running: false,
    started: false,
    cat: { x: 120, y: 260, vx: 0, vy: 0, frame: 0, frameTimer: 0 },
    obstacles: [],
    birds: [],
    nextObstacle: 1.5,
    nextBird: 4,
    lastTimestamp: null,
    score: 0,
    bestScore: 0,
    elapsed: 0,
    bestTime: 0,
    lastOutcome: 'ready',
    floorY: CANVAS_HEIGHT - DEFAULT_CONFIG.groundHeight,
    autosaveLoaded: false,
    backgroundOffset: 0,
    nextGachaReward: DEFAULT_CONFIG.rewards.gacha.survivalIntervalSeconds
  };

  let ctx = null;
  let catFrames = [];
  let pylonSprite = null;
  let birdSprite = null;
  let backgroundSprite = null;
  let backgroundWidth = CANVAS_WIDTH;
  let backgroundHeight = CANVAS_HEIGHT;
  let backgroundScale = 1;
  let animationHandle = null;

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window.t === 'function') {
      const value = window.t(key, params);
      if (value != null) {
        return value;
      }
    }
    return fallback;
  }

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function randomInRange(min, max) {
    const a = Number(min);
    const b = Number(max);
    if (!Number.isFinite(a) || !Number.isFinite(b)) {
      return 0;
    }
    const low = Math.min(a, b);
    const high = Math.max(a, b);
    return low + Math.random() * (high - low);
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
    if (typeof window.atom2universSaveGame === 'function') {
      window.atom2universSaveGame();
      return;
    }
    if (typeof window.saveGame === 'function') {
      window.saveGame();
    }
  }

  function setStatus(messageKey, fallback) {
    const text = translate(messageKey, fallback);
    if (elements.status) {
      elements.status.textContent = text;
    }
  }

  function getSurvivalRewardSettings() {
    const rewards = state.config && typeof state.config === 'object' ? state.config.rewards : null;
    const gacha = rewards && typeof rewards === 'object' && typeof rewards.gacha === 'object'
      ? rewards.gacha
      : null;
    const interval = Number(gacha && gacha.survivalIntervalSeconds);
    const amount = Number(gacha && gacha.ticketAmount);
    const validInterval = Number.isFinite(interval) && interval > 0 ? interval : null;
    const validAmount = Number.isFinite(amount) && amount > 0 ? Math.floor(amount) : null;
    if (!validInterval || !validAmount) {
      return null;
    }
    return { interval: validInterval, amount: validAmount };
  }

  function updateHud() {
    if (elements.scoreValue) {
      elements.scoreValue.textContent = Math.floor(state.score);
    }
    if (elements.timeValue) {
      elements.timeValue.textContent = `${state.elapsed.toFixed(1)}s`;
    }
    if (elements.bestScoreValue) {
      elements.bestScoreValue.textContent = state.bestScore > 0 ? state.bestScore : '—';
    }
    if (elements.overlayBestScore) {
      elements.overlayBestScore.textContent = state.bestScore > 0 ? state.bestScore : '—';
    }
    if (elements.bestTimeValue) {
      elements.bestTimeValue.textContent = state.bestTime > 0 ? `${state.bestTime.toFixed(1)}s` : '—';
    }
    if (elements.overlayBestTime) {
      elements.overlayBestTime.textContent = state.bestTime > 0 ? `${state.bestTime.toFixed(1)}s` : '—';
    }
  }

  function loadImage(path) {
    return new Promise(resolve => {
      const image = new Image();
      image.src = path;
      image.addEventListener('load', () => resolve(image));
      image.addEventListener('error', () => resolve(null));
    });
  }

  function configureBackground(image) {
    if (!image) {
      backgroundSprite = null;
      backgroundWidth = CANVAS_WIDTH;
      backgroundHeight = CANVAS_HEIGHT;
      backgroundScale = 1;
      return;
    }
    backgroundSprite = image;
    backgroundScale = CANVAS_HEIGHT / image.height;
    backgroundWidth = image.width * backgroundScale;
    backgroundHeight = CANVAS_HEIGHT;
  }

  function sliceCatFrames(sheet) {
    if (!sheet) {
      return [];
    }
    const frames = [];
    for (let i = 0; i < 2; i += 1) {
      const canvas = document.createElement('canvas');
      canvas.width = CAT_FRAME_WIDTH;
      canvas.height = CAT_FRAME_HEIGHT;
      const context = canvas.getContext('2d');
      context.drawImage(sheet, i * CAT_FRAME_WIDTH, 0, CAT_FRAME_WIDTH, CAT_FRAME_HEIGHT, 0, 0, CAT_FRAME_WIDTH, CAT_FRAME_HEIGHT);
      frames.push(canvas);
    }
    return frames;
  }

  function normalizeConfig(raw) {
    if (!raw || typeof raw !== 'object') {
      return DEFAULT_CONFIG;
    }
    const obstacleSpacing = raw.obstacleSpacing && typeof raw.obstacleSpacing === 'object' ? raw.obstacleSpacing : {};
    const pylonGap = raw.pylonGap && typeof raw.pylonGap === 'object' ? raw.pylonGap : {};
    const birdInterval = raw.birdInterval && typeof raw.birdInterval === 'object' ? raw.birdInterval : {};
    const birdSpeed = raw.birdSpeed && typeof raw.birdSpeed === 'object' ? raw.birdSpeed : {};
    const rewards = raw.rewards && typeof raw.rewards === 'object' ? raw.rewards : {};
    const gachaRewards = rewards.gacha && typeof rewards.gacha === 'object' ? rewards.gacha : {};
    const survivalInterval = clamp(
      Number(gachaRewards.survivalIntervalSeconds) || DEFAULT_CONFIG.rewards.gacha.survivalIntervalSeconds,
      5,
      600
    );
    const survivalTicketAmount = Math.max(0, Math.floor(Number(gachaRewards.ticketAmount)
      || DEFAULT_CONFIG.rewards.gacha.ticketAmount));
    return Object.freeze({
      gravity: clamp(Number(raw.gravity) || DEFAULT_CONFIG.gravity, 600, 5000),
      jumpImpulse: clamp(Number(raw.jumpImpulse) || DEFAULT_CONFIG.jumpImpulse, 320, 1600),
      maxFallSpeed: clamp(Number(raw.maxFallSpeed) || DEFAULT_CONFIG.maxFallSpeed, 800, 3200),
      forwardSpeed: clamp(Number(raw.forwardSpeed) || DEFAULT_CONFIG.forwardSpeed, 80, 400),
      obstacleSpacing: {
        min: clamp(Number(obstacleSpacing.min) || DEFAULT_CONFIG.obstacleSpacing.min, 0.8, 4),
        max: clamp(Number(obstacleSpacing.max) || DEFAULT_CONFIG.obstacleSpacing.max, 1, 5)
      },
      pylonGap: {
        min: clamp(Number(pylonGap.min) || DEFAULT_CONFIG.pylonGap.min, 80, 220),
        max: clamp(Number(pylonGap.max) || DEFAULT_CONFIG.pylonGap.max, 100, 260)
      },
      safetyCorridor: clamp(Number(raw.safetyCorridor) || DEFAULT_CONFIG.safetyCorridor, 80, 240),
      birdInterval: {
        min: clamp(Number(birdInterval.min) || DEFAULT_CONFIG.birdInterval.min, 1.5, 8),
        max: clamp(Number(birdInterval.max) || DEFAULT_CONFIG.birdInterval.max, 2, 10)
      },
      birdSpeed: {
        min: clamp(Number(birdSpeed.min) || DEFAULT_CONFIG.birdSpeed.min, 60, 360),
        max: clamp(Number(birdSpeed.max) || DEFAULT_CONFIG.birdSpeed.max, 120, 420)
      },
      birdWobble: clamp(Number(raw.birdWobble) || DEFAULT_CONFIG.birdWobble, 0, 80),
      groundHeight: clamp(Number(raw.groundHeight) || DEFAULT_CONFIG.groundHeight, 48, 180),
      backgroundScrollSpeed: clamp(Number(raw.backgroundScrollSpeed) || DEFAULT_CONFIG.backgroundScrollSpeed, 20, 240),
      rewards: {
        gacha: {
          survivalIntervalSeconds: survivalInterval,
          ticketAmount: survivalTicketAmount
        }
      }
    });
  }

  async function loadConfig() {
    try {
      const response = await fetch(CONFIG_PATH, { cache: 'no-store' });
      if (!response.ok) {
        return DEFAULT_CONFIG;
      }
      const json = await response.json();
      return normalizeConfig(json);
    } catch (error) {
      return DEFAULT_CONFIG;
    }
  }

  function readProgressFromGlobal() {
    const globalState = getGlobalGameState();
    if (!globalState || !globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
      return null;
    }
    const entries = globalState.arcadeProgress.entries && typeof globalState.arcadeProgress.entries === 'object'
      ? globalState.arcadeProgress.entries
      : globalState.arcadeProgress;
    const entry = entries && entries[GAME_ID];
    if (entry && typeof entry === 'object') {
      const state = entry.state && typeof entry.state === 'object' ? entry.state : entry;
      return {
        bestScore: Number(state.bestScore) || 0,
        bestTime: Number(state.bestTime) || 0
      };
    }
    return null;
  }

  function persistProgress(progress) {
    const globalState = getGlobalGameState();
    if (globalState) {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
        globalState.arcadeProgress.entries = {};
      }
      globalState.arcadeProgress.entries[GAME_ID] = {
        state: {
          bestScore: progress.bestScore,
          bestTime: progress.bestTime
        },
        updatedAt: Date.now()
      };
    }

    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.set === 'function') {
      try {
        window.ArcadeAutosave.set(GAME_ID, { bestScore: progress.bestScore, bestTime: progress.bestTime });
      } catch (error) {
        // Ignore autosave failures
      }
    }

    requestSave();
    window.dispatchEvent(new Event('arcadeAutosaveSync'));
  }

  function restoreProgress() {
    const saved = readProgressFromGlobal();
    if (saved) {
      state.bestScore = saved.bestScore;
      state.bestTime = saved.bestTime;
      updateHud();
    }
    state.autosaveLoaded = true;
  }

  function resetRun() {
    state.running = false;
    state.started = false;
    state.cat.x = CANVAS_WIDTH * 0.25;
    state.cat.y = CANVAS_HEIGHT * 0.5;
    state.cat.vx = 0;
    state.cat.vy = 0;
    state.cat.frame = 0;
    state.cat.frameTimer = 0;
    state.obstacles = [];
    state.birds = [];
    state.score = 0;
    state.elapsed = 0;
    state.backgroundOffset = 0;
    const survivalRewards = getSurvivalRewardSettings();
    state.nextGachaReward = survivalRewards ? survivalRewards.interval : Infinity;
    state.nextObstacle = randomInRange(state.config.obstacleSpacing.min, state.config.obstacleSpacing.max);
    state.nextBird = randomInRange(state.config.birdInterval.min, state.config.birdInterval.max);
    state.lastTimestamp = null;
    state.floorY = CANVAS_HEIGHT - state.config.groundHeight;
    state.lastOutcome = 'ready';
    updateHud();
    showOverlay('index.sections.jumpingCat.overlay.readyTitle', 'Jumping Cat',
      'index.sections.jumpingCat.overlay.ready', 'Touchez ou appuyez sur espace pour commencer');
  }

  function startRun() {
    if (state.running) {
      return;
    }
    hideOverlay();
    state.running = true;
    state.started = true;
    state.lastOutcome = 'running';
    state.lastTimestamp = performance.now();
    setStatus('index.sections.jumpingCat.status.running', 'Partie en cours');
    loop();
  }

  function stopRun(messageKey, fallback) {
    state.running = false;
    state.started = false;
    state.lastOutcome = 'gameOver';
    setStatus('index.sections.jumpingCat.status.gameOver', 'Partie terminée');
    const newBestScore = Math.max(state.bestScore, Math.floor(state.score));
    const newBestTime = Math.max(state.bestTime, Number(state.elapsed.toFixed(1)));
    const hasNewRecord = newBestScore > state.bestScore || newBestTime > state.bestTime;
    state.bestScore = newBestScore;
    state.bestTime = newBestTime;
    updateHud();
    persistProgress({ bestScore: state.bestScore, bestTime: state.bestTime });
    const titleKey = hasNewRecord
      ? 'index.sections.jumpingCat.overlay.recordTitle'
      : 'index.sections.jumpingCat.overlay.gameOverTitle';
    const descriptionKey = messageKey || 'index.sections.jumpingCat.overlay.defaultMessage';
    showOverlay(titleKey, hasNewRecord ? 'Nouveau record' : 'Game over', descriptionKey, fallback);
  }

  function showOverlay(titleKey, titleFallback, messageKey, messageFallback) {
    if (!elements.overlay || !elements.overlayTitle || !elements.overlayMessage) {
      return;
    }
    elements.overlayTitle.textContent = translate(titleKey, titleFallback);
    elements.overlayMessage.textContent = translate(messageKey, messageFallback);
    elements.overlay.hidden = false;
    elements.overlay.removeAttribute('aria-hidden');
    if (elements.overlayAction) {
      elements.overlayAction.textContent = translate(
        'index.sections.jumpingCat.overlay.retry',
        'Rejouer'
      );
    }
    if (elements.overlayDismiss) {
      elements.overlayDismiss.textContent = translate(
        'index.sections.jumpingCat.overlay.dismiss',
        'Fermer'
      );
    }
  }

  function hideOverlay() {
    if (!elements.overlay) {
      return;
    }
    elements.overlay.hidden = true;
    elements.overlay.setAttribute('aria-hidden', 'true');
  }

  function spawnObstacle() {
    const gap = randomInRange(state.config.pylonGap.min, state.config.pylonGap.max);
    const maxPylonHeight = state.floorY - gap - state.config.safetyCorridor;
    const minHeight = Math.max(70, maxPylonHeight * 0.35);
    const targetHeight = randomInRange(minHeight, Math.max(minHeight + 20, maxPylonHeight * 0.95));
    const height = clamp(targetHeight, minHeight, maxPylonHeight);
    const slice = PYLON_SLICES[Math.floor(Math.random() * PYLON_SLICES.length)];
    state.obstacles.push({
      x: CANVAS_WIDTH + 30,
      width: slice.sw,
      height,
      sprite: slice,
      passed: false
    });
    state.nextObstacle = randomInRange(state.config.obstacleSpacing.min, state.config.obstacleSpacing.max);
  }

  function spawnBird() {
    const frames = BIRD_ANIMATIONS[Math.floor(Math.random() * BIRD_ANIMATIONS.length)];
    const sprite = frames[0];
    const bandHeight = 180;
    const minY = 24 + sprite.size * 0.5;
    const maxY = Math.max(minY, bandHeight - sprite.size * 0.5);
    const y = clamp(randomInRange(minY, maxY), minY, maxY);
    const speed = randomInRange(state.config.birdSpeed.min, state.config.birdSpeed.max);
    state.birds.push({
      x: CANVAS_WIDTH + sprite.size,
      y,
      size: sprite.size,
      frames,
      frameIndex: 0,
      animationTimer: 0,
      vx: -speed,
      wobblePhase: Math.random() * Math.PI * 2,
      passed: false
    });
    state.nextBird = randomInRange(state.config.birdInterval.min, state.config.birdInterval.max);
  }

  function applyInput() {
    state.cat.vy = -state.config.jumpImpulse;
    state.cat.frame = (state.cat.frame + 1) % 2;
    state.cat.frameTimer = 0;
    if (!state.running) {
      startRun();
    }
  }

  function handlePointer(event) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    applyInput();
  }

  function shouldIgnoreGlobalPointer(event) {
    if (!event || !event.target) {
      return false;
    }
    const target = event.target;
    if (elements.header && target.closest('.jumping-cat__header')) {
      return true;
    }
    if (target.closest('button, a, [role="button"], input, select, textarea, label')) {
      return true;
    }
    return false;
  }

  function handleGlobalPointer(event) {
    if (shouldIgnoreGlobalPointer(event)) {
      return;
    }
    handlePointer(event);
  }

  function bindInputs() {
    if (elements.canvas) {
      elements.canvas.addEventListener('pointerdown', handlePointer);
    }
    if (elements.page) {
      elements.page.addEventListener('pointerdown', handleGlobalPointer);
    }
    window.addEventListener('keydown', event => {
      if (event.code === 'Space') {
        event.preventDefault();
        applyInput();
      }
    });
  }

  function maybeAwardSurvivalTickets() {
    const settings = getSurvivalRewardSettings();
    if (!settings) {
      return;
    }
    if (!Number.isFinite(state.nextGachaReward) || state.nextGachaReward <= 0) {
      state.nextGachaReward = settings.interval;
    }
    if (state.elapsed < state.nextGachaReward) {
      return;
    }

    let milestones = 0;
    while (state.elapsed >= state.nextGachaReward) {
      milestones += 1;
      state.nextGachaReward += settings.interval;
    }

    const ticketsToGrant = milestones * settings.amount;
    if (ticketsToGrant <= 0) {
      return;
    }
    const award = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof award !== 'function') {
      return;
    }
    let granted = 0;
    try {
      granted = award(ticketsToGrant, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Jumping Cat: unable to grant survival gacha tickets', error);
      granted = 0;
    }
    if (!Number.isFinite(granted) || granted <= 0 || typeof showToast !== 'function') {
      return;
    }
    const suffix = granted > 1 ? 's' : '';
    const message = translate(
      'scripts.app.arcade.jumpingCat.rewardToast',
      `Bonus Jumping Cat ! +${granted} ticket${suffix} gacha.`,
      { count: granted, suffix }
    );
    showToast(message);
  }

  function updatePhysics(deltaSeconds) {
    state.cat.vy += state.config.gravity * deltaSeconds;
    state.cat.vy = clamp(state.cat.vy, -state.config.jumpImpulse * 1.25, state.config.maxFallSpeed);
    state.cat.y += state.cat.vy * deltaSeconds;
    state.cat.frameTimer += deltaSeconds;
    if (state.cat.frameTimer > 0.18) {
      state.cat.frame = (state.cat.frame + 1) % catFrames.length;
      state.cat.frameTimer = 0;
    }
    if (state.cat.y > state.floorY - CAT_FRAME_HEIGHT / 2) {
      state.cat.y = state.floorY - CAT_FRAME_HEIGHT / 2;
      stopRun('index.sections.jumpingCat.overlay.floor', 'Le chat a touché le sol.');
    }
    if (state.cat.y < CAT_FRAME_HEIGHT / 2) {
      state.cat.y = CAT_FRAME_HEIGHT / 2;
    }

    state.nextObstacle -= deltaSeconds;
    if (state.nextObstacle <= 0) {
      spawnObstacle();
    }
    state.nextBird -= deltaSeconds;
    if (state.nextBird <= 0) {
      spawnBird();
    }

    const scroll = state.config.forwardSpeed * deltaSeconds;
    state.obstacles.forEach(obstacle => {
      obstacle.x -= scroll;
      if (!obstacle.passed && obstacle.x + obstacle.width < state.cat.x) {
        obstacle.passed = true;
        state.score += 1;
      }
    });
    state.birds.forEach(bird => {
      bird.x += bird.vx * deltaSeconds;
      const wobble = Math.sin(bird.wobblePhase + state.elapsed * 2) * state.config.birdWobble;
      bird.y = clamp(bird.y + wobble * deltaSeconds, bird.size * 0.5, 200);
      bird.animationTimer += deltaSeconds;
      if (bird.animationTimer >= 0.5) {
        bird.frameIndex = bird.frames && bird.frames.length ? (bird.frameIndex + 1) % bird.frames.length : 0;
        bird.animationTimer = 0;
      }
    });
    state.obstacles = state.obstacles.filter(obstacle => obstacle.x + obstacle.width > -40);
    state.birds = state.birds.filter(bird => bird.x + bird.size > -60);

    state.backgroundOffset = (state.backgroundOffset + state.config.backgroundScrollSpeed * deltaSeconds) % backgroundWidth;

    state.elapsed += deltaSeconds;
    maybeAwardSurvivalTickets();
  }

  function intersectsRect(ax, ay, aw, ah, bx, by, bw, bh) {
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
  }

  function checkCollisions() {
    const catBox = {
      x: state.cat.x - CAT_FRAME_WIDTH * 0.35,
      y: state.cat.y - CAT_FRAME_HEIGHT * 0.35,
      width: CAT_FRAME_WIDTH * 0.7,
      height: CAT_FRAME_HEIGHT * 0.7
    };
    for (const obstacle of state.obstacles) {
      const obsBox = {
        x: obstacle.x,
        y: state.floorY - obstacle.height,
        width: obstacle.width,
        height: obstacle.height
      };
      if (intersectsRect(catBox.x, catBox.y, catBox.width, catBox.height, obsBox.x, obsBox.y, obsBox.width, obsBox.height)) {
        stopRun('index.sections.jumpingCat.overlay.pylon', 'Le chat a heurté un pylône.');
        return;
      }
    }

    const birdBand = state.config.safetyCorridor + state.config.pylonGap.min;
    const pylonTop = state.obstacles.length ? Math.min(...state.obstacles.map(o => state.floorY - o.height)) : state.floorY;
    const minSafeHeight = Math.min(pylonTop - state.config.safetyCorridor, state.floorY - birdBand);

    for (const bird of state.birds) {
      const size = bird.size * 0.7;
      const birdBox = {
        x: bird.x - size * 0.5,
        y: bird.y - size * 0.5,
        width: size,
        height: size
      };
      if (birdBox.y + birdBox.height > minSafeHeight) {
        birdBox.y = minSafeHeight - birdBox.height;
      }
      if (intersectsRect(catBox.x, catBox.y, catBox.width, catBox.height, birdBox.x, birdBox.y, birdBox.width, birdBox.height)) {
        stopRun('index.sections.jumpingCat.overlay.bird', 'Un oiseau a percuté le chat.');
        return;
      }
    }
  }

  function drawBackground() {
    if (!backgroundSprite) {
      ctx.fillStyle = '#0b0f1a';
      ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
      const horizon = state.floorY;
      const gradient = ctx.createLinearGradient(0, horizon - 120, 0, CANVAS_HEIGHT);
      gradient.addColorStop(0, 'rgba(64, 118, 255, 0.08)');
      gradient.addColorStop(1, 'rgba(255, 171, 88, 0.18)');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, horizon - 120, CANVAS_WIDTH, 160);
      ctx.fillStyle = '#0e111c';
      ctx.fillRect(0, horizon, CANVAS_WIDTH, CANVAS_HEIGHT - horizon);
      return;
    }

    const offset = state.backgroundOffset % backgroundWidth;
    for (let x = -offset; x < CANVAS_WIDTH + backgroundWidth; x += backgroundWidth) {
      ctx.drawImage(backgroundSprite, 0, 0, backgroundSprite.width, backgroundSprite.height, x, 0, backgroundWidth, backgroundHeight);
    }
  }

  function drawObstacles() {
    if (!pylonSprite) {
      ctx.fillStyle = '#f3c26b';
      state.obstacles.forEach(obstacle => {
        ctx.fillRect(obstacle.x, state.floorY - obstacle.height, obstacle.width, obstacle.height);
      });
      return;
    }
    state.obstacles.forEach(obstacle => {
      ctx.drawImage(
        pylonSprite,
        obstacle.sprite.sx,
        0,
        obstacle.sprite.sw,
        PYLON_HEIGHT,
        obstacle.x,
        state.floorY - obstacle.height,
        obstacle.sprite.sw,
        obstacle.height
      );
    });
  }

  function drawBirds() {
    if (!birdSprite) {
      ctx.fillStyle = '#ff6b6b';
      state.birds.forEach(bird => {
        ctx.beginPath();
        ctx.arc(bird.x, bird.y, bird.size * 0.35, 0, Math.PI * 2);
        ctx.fill();
      });
      return;
    }
    state.birds.forEach(bird => {
      const frame = bird.frames && bird.frames.length ? bird.frames[bird.frameIndex] : null;
      const sprite = frame || bird.sprite;
      if (!sprite) {
        return;
      }
      ctx.drawImage(
        birdSprite,
        sprite.sx,
        sprite.sy,
        sprite.sw,
        sprite.sh,
        bird.x - sprite.size * 0.5,
        bird.y - sprite.size * 0.5,
        sprite.size,
        sprite.size
      );
    });
  }

  function drawCat() {
    const frame = catFrames[state.cat.frame] || null;
    const x = state.cat.x - CAT_FRAME_WIDTH * 0.5;
    const y = state.cat.y - CAT_FRAME_HEIGHT * 0.5;
    if (!frame) {
      ctx.fillStyle = '#ffd166';
      ctx.fillRect(x, y, CAT_FRAME_WIDTH, CAT_FRAME_HEIGHT);
      return;
    }
    ctx.drawImage(frame, x, y, CAT_FRAME_WIDTH, CAT_FRAME_HEIGHT);
  }

  function render() {
    if (!ctx) {
      return;
    }
    drawBackground();
    drawObstacles();
    drawBirds();
    drawCat();
  }

  function loop(timestamp) {
    if (!state.running) {
      return;
    }
    const now = typeof timestamp === 'number' ? timestamp : performance.now();
    if (state.lastTimestamp == null) {
      state.lastTimestamp = now;
    }
    const delta = Math.min(0.05, Math.max(0.001, (now - state.lastTimestamp) / 1000));
    state.lastTimestamp = now;
    updatePhysics(delta);
    checkCollisions();
    updateHud();
    render();
    animationHandle = requestAnimationFrame(loop);
  }

  async function init() {
    if (!elements.canvas) {
      return;
    }
    ctx = elements.canvas.getContext('2d');
    elements.canvas.width = CANVAS_WIDTH;
    elements.canvas.height = CANVAS_HEIGHT;
    state.config = await loadConfig();
    state.floorY = CANVAS_HEIGHT - state.config.groundHeight;

    const [catSheet, pylons, birds, background] = await Promise.all([
      loadImage(CAT_SPRITE_PATH),
      loadImage(PYLON_SPRITE_PATH),
      loadImage(BIRD_SPRITE_PATH),
      loadImage(BACKGROUND_SPRITE_PATH)
    ]);
    catFrames = sliceCatFrames(catSheet);
    pylonSprite = pylons;
    birdSprite = birds;
    configureBackground(background);
    restoreProgress();
    resetRun();
    updateHud();
    render();
    bindInputs();

    if (elements.overlayAction) {
      elements.overlayAction.addEventListener('click', () => {
        hideOverlay();
        resetRun();
        startRun();
      });
    }
    if (elements.overlayDismiss) {
      elements.overlayDismiss.addEventListener('click', () => {
        hideOverlay();
      });
    }
    if (elements.actionButton) {
      elements.actionButton.addEventListener('click', () => {
        hideOverlay();
        resetRun();
        startRun();
      });
    }
  }

  function onEnter() {
    if (!state.autosaveLoaded) {
      restoreProgress();
    }
    if (state.running) {
      setStatus('index.sections.jumpingCat.status.running', 'Partie en cours');
    } else if (state.lastOutcome === 'gameOver' && elements.overlay && elements.overlay.hidden === false) {
      setStatus('index.sections.jumpingCat.status.gameOver', 'Partie terminée');
    } else {
      setStatus('index.sections.jumpingCat.status.ready', 'Prêt à bondir');
    }
    if (!animationHandle) {
      render();
    }
  }

  function onLeave() {
    state.running = false;
    if (animationHandle) {
      cancelAnimationFrame(animationHandle);
      animationHandle = null;
    }
  }

  init();

  window.jumpingCatArcade = {
    onEnter,
    onLeave,
    restart: resetRun,
    pause: onLeave,
    resume: startRun
  };
})();
