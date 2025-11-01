(() => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-stars-war-root]');
  if (!root) {
    return;
  }

  const elements = {
    canvas: document.getElementById('starsWarCanvas'),
    overlay: document.getElementById('starsWarOverlay'),
    overlayTitle: document.getElementById('starsWarOverlayTitle'),
    overlayMessage: document.getElementById('starsWarOverlayMessage'),
    overlayButton: document.getElementById('starsWarOverlayButton'),
    scoreValue: document.getElementById('starsWarScoreValue'),
    timeValue: document.getElementById('starsWarTimeValue'),
    waveValue: document.getElementById('starsWarWaveValue'),
    difficultyValue: document.getElementById('starsWarDifficultyValue'),
    restartButton: document.getElementById('starsWarRestartButton'),
    powerupList: document.getElementById('starsWarPowerupList'),
    livesContainer: document.getElementById('starsWarLives'),
    screenReaderStatus: document.getElementById('starsWarStatus')
  };

  if (!elements.canvas) {
    return;
  }

  const CANVAS_WIDTH = 480;
  const CANVAS_HEIGHT = 720;
  const PLAYER_MAX_HP = 3;
  const PLAYER_DAMAGE_COOLDOWN = 0.7;
  const PLAYER_AREA_TOP_RATIO = 0.35;
  const BASE_SPAWN_INTERVAL = 0.8;
  const MIN_SPAWN_INTERVAL = 0.35;
  const ENEMY_BULLET_SIZE = { width: 6, height: 18 };
  const PLAYER_BULLET_SIZE = { width: 8, height: 18 };
  const DROP_SIZE = 20;
  const DROP_BASE_SPEED = 120;
  const MAGNET_PULL_SPEED = 240;
  const MAGNET_RADIUS = 120;

  const PLAYER_BASE_STATS = Object.freeze({
    speed: 220,
    cooldown: 0.18,
    bulletSpeed: 360,
    multi: 1
  });

  const PLAYER_MILESTONES = Object.freeze([
    { time: 120, apply(player) { player.baseMulti = Math.max(player.baseMulti, 2); } },
    { time: 240, apply(player) { player.baseCooldown *= 0.85; } },
    { time: 360, apply(player) { player.baseBulletSpeed *= 1.15; } },
    { time: 480, apply(player) { player.baseMulti = Math.max(player.baseMulti, 3); } },
    { time: 600, apply(player) { player.baseSpeed *= 1.1; } }
  ]);

  const POWERUP_DEFS = Object.freeze({
    multi_shot: { duration: 15, type: 'timed' },
    rapid_fire: { duration: 15, type: 'timed' },
    magnet: { duration: 10, type: 'timed' },
    enemy_slow: { duration: 6, type: 'timed' },
    shield: { duration: Infinity, type: 'shield' },
    heart: { duration: 0, type: 'heal' }
  });

  const POWERUP_VISUALS = Object.freeze({
    multi_shot: {
      letter: 'M',
      base: '#37c9ff',
      accent: '#aee7ff',
      cabin: '#0b1836',
      text: '#04101f',
      glow: 'rgba(55, 201, 255, 0.55)'
    },
    rapid_fire: {
      letter: 'F',
      base: '#ff5a5a',
      accent: '#ffd37a',
      cabin: '#360b0b',
      text: '#320808',
      glow: 'rgba(255, 90, 90, 0.6)'
    },
    magnet: {
      letter: 'A',
      base: '#52e0a1',
      accent: '#c3ffe0',
      cabin: '#08311f',
      text: '#042616',
      glow: 'rgba(82, 224, 161, 0.6)'
    },
    enemy_slow: {
      letter: 'L',
      base: '#9185ff',
      accent: '#d5ceff',
      cabin: '#1b1740',
      text: '#120d2e',
      glow: 'rgba(145, 133, 255, 0.6)'
    },
    shield: {
      letter: 'B',
      base: '#4bb3ff',
      accent: '#c6e7ff',
      cabin: '#0b1a2e',
      text: '#082037',
      glow: 'rgba(75, 179, 255, 0.6)'
    },
    heart: {
      letter: 'V',
      base: '#ff6b81',
      accent: '#ffc2d1',
      cabin: '#401018',
      text: '#3a0811',
      glow: 'rgba(255, 107, 129, 0.6)'
    },
    default: {
      letter: '?',
      base: '#8be9fd',
      accent: '#dff9ff',
      cabin: '#0b1d2d',
      text: '#06212e',
      glow: 'rgba(139, 233, 253, 0.45)'
    }
  });

  const ENEMY_TYPES = Object.freeze({
    drone: {
      id: 'drone',
      baseHp: 1,
      baseSpeed: 60,
      score: 30,
      canShoot: false
    },
    fast: {
      id: 'fast',
      baseHp: 1,
      baseSpeed: 90,
      score: 40,
      canShoot: false
    },
    gunner: {
      id: 'gunner',
      baseHp: 2,
      baseSpeed: 55,
      score: 70,
      canShoot: true,
      fireRate: 1.8,
      bulletSpeed: 180
    },
    tank: {
      id: 'tank',
      baseHp: 4,
      baseSpeed: 45,
      score: 110,
      canShoot: false
    },
    kamikaze: {
      id: 'kamikaze',
      baseHp: 1,
      baseSpeed: 120,
      score: 90,
      canShoot: false
    },
    sniper: {
      id: 'sniper',
      baseHp: 2,
      baseSpeed: 50,
      score: 120,
      canShoot: true,
      fireRate: 2.8,
      bulletSpeed: 260
    },
    carrier: {
      id: 'carrier',
      baseHp: 2,
      baseSpeed: 65,
      score: 50,
      canShoot: false
    },
    miniboss: {
      id: 'miniboss',
      baseHp: 12,
      baseSpeed: 40,
      score: 400,
      canShoot: true,
      fireRate: 1.2,
      bulletSpeed: 200
    }
  });

  const FORMATIONS = Object.freeze(['LINE', 'V', 'COLUMN', 'ARC', 'SWARM']);
  const PATHS = Object.freeze(['SIN', 'CIRCLE', 'SWOOP', 'LINE', 'ARC']);
  const AUTOSAVE_KEY = 'starsWar';
  let autosaveTimerId = null;

  function rngMulberry32(a) {
    return function rng() {
      let t = a += 0x6D2B79F5;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function strToSeed(str) {
    if (typeof str !== 'string' || !str.length) {
      return 0;
    }
    let h = 1779033703 ^ str.length;
    for (let i = 0; i < str.length; i += 1) {
      h = Math.imul(h ^ str.charCodeAt(i), 3432918353);
      h = (h << 13) | (h >>> 19);
    }
    return h >>> 0;
  }

  const PALETTES = [
    ['#0b0e17', '#4de0ff', '#c3f0ff', '#ffffff'],
    ['#0b0e17', '#ff5a5a', '#ffd37a', '#ffffff'],
    ['#0b0e17', '#9dff6b', '#6be6ff', '#ffffff']
  ];

  function makeShipCanvas(seedStr, w = 24, h = 24, scale = 2, paletteIdx = 0, density = 0.55) {
    const rnd = rngMulberry32(strToSeed(seedStr));
    const pal = PALETTES[paletteIdx % PALETTES.length];
    const cols = Math.floor(w / scale);
    const rows = Math.floor(h / scale);
    const half = Math.ceil(cols / 2);

    const bits = Array.from({ length: rows }, () => Array(half).fill(0));
    for (let y = 0; y < rows; y += 1) {
      const rowDensity = density * (0.85 + rnd() * 0.3);
      for (let x = 0; x < half; x += 1) {
        const nx = x / half;
        const ny = y / rows;
        let bias = 0;
        if (ny < 0.2) bias += 0.08;
        if (ny > 0.75) bias -= 0.05;
        if (nx < 0.2) bias += 0.05;
        const p = rowDensity + bias - Math.abs(nx - 0.05) * 0.1;
        bits[y][x] = rnd() < p ? 1 : 0;
      }
    }
    bits[0][0] = 1;
    bits[1][0] = 1;

    const canvas = document.createElement('canvas');
    canvas.width = cols * scale;
    canvas.height = rows * scale;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = pal[0];
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    const body = pal[1];
    const accent = pal[2];
    const light = pal[3];

    for (let y = 0; y < rows; y += 1) {
      for (let x = 0; x < cols; x += 1) {
        const sx = x < half ? x : cols - 1 - x;
        if (!bits[y][sx]) {
          continue;
        }
        const c = x === Math.floor(cols / 2) ? light : (x % 3 === 0 ? accent : body);
        ctx.fillStyle = c;
        ctx.fillRect(x * scale, y * scale, scale, scale);
      }
    }

    ctx.fillStyle = light;
    ctx.fillRect(Math.floor(cols / 2) * scale, 2 * scale, scale, scale);
    return canvas;
  }

  function generateFleet(seed = 'stars-war-fleet') {
    const names = ['player', 'drone', 'fast', 'gunner', 'tank', 'kamikaze', 'sniper', 'carrier', 'miniboss'];
    const fleet = {};
    for (let i = 0; i < names.length; i += 1) {
      const size = names[i] === 'miniboss' ? 40 : names[i] === 'tank' ? 28 : 24;
      const scale = 2;
      fleet[names[i]] = makeShipCanvas(`${seed}-${names[i]}`, size, size, scale, i % PALETTES.length, 0.52 + 0.03 * i);
    }
    return fleet;
  }

  const fleet = generateFleet('stars-war');

  function clamp(value, min, max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  function formatTime(seconds) {
    const total = Math.max(0, Math.floor(seconds));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  function formatNumber(value) {
    try {
      if (typeof Intl !== 'undefined' && Intl.NumberFormat) {
        return new Intl.NumberFormat().format(Math.floor(value));
      }
    } catch (error) {
      // Ignore formatter failures.
    }
    return Math.floor(value).toString();
  }

  function createContext(canvas) {
    const context = canvas.getContext('2d');
    const pixelRatio = Math.max(1, Math.floor(window.devicePixelRatio || 1));
    canvas.width = CANVAS_WIDTH * pixelRatio;
    canvas.height = CANVAS_HEIGHT * pixelRatio;
    canvas.style.width = '100%';
    canvas.style.height = '100%';
    context.scale(pixelRatio, pixelRatio);
    return context;
  }

  const context = createContext(elements.canvas);

  const inputState = {
    left: false,
    right: false,
    up: false,
    down: false,
    pointerActive: false,
    pointerX: CANVAS_WIDTH / 2,
    pointerY: CANVAS_HEIGHT * 0.8
  };

  const state = {
    rngSeed: '',
    rng: rngMulberry32(1),
    running: false,
    paused: false,
    lastTimestamp: 0,
    elapsed: 0,
    difficulty: 1,
    wave: 0,
    spawnQueue: [],
    spawnTimer: 0,
    spawnInterval: BASE_SPAWN_INTERVAL,
    maxOnScreen: 10 + Math.floor(3 * 1),
    enemies: [],
    enemyBullets: [],
    playerBullets: [],
    drops: [],
    effects: [],
    score: 0,
    heartPitySeconds: 0,
    nextDifficultyTick: 60,
    nextSpawnDensityPush: { at12: false, at14: false },
    activeMilestones: new Set(),
    gameOver: false,
    overlayMode: 'ready',
    powerups: {},
    timeSinceLastUpdate: 0,
    records: {
      bestScore: 0,
      bestTime: 0,
      bestWave: 0,
      bestDifficulty: 1
    },
    lastSeed: ''
  };

  const player = {
    x: CANVAS_WIDTH / 2,
    y: CANVAS_HEIGHT * 0.8,
    width: 32,
    height: 32,
    velocityX: 0,
    velocityY: 0,
    hp: PLAYER_MAX_HP,
    shieldCharges: 0,
    baseSpeed: PLAYER_BASE_STATS.speed,
    baseCooldown: PLAYER_BASE_STATS.cooldown,
    baseBulletSpeed: PLAYER_BASE_STATS.bulletSpeed,
    baseMulti: PLAYER_BASE_STATS.multi,
    fireTimer: 0,
    tempMultiBonus: 0,
    rapidFireFactor: 1,
    magnetActive: false,
    lastDamageTime: -Infinity
  };

  const KEY_BINDINGS = Object.freeze({
    ArrowLeft: 'left',
    ArrowRight: 'right',
    ArrowUp: 'up',
    ArrowDown: 'down',
    q: 'left',
    Q: 'left',
    s: 'down',
    S: 'down',
    d: 'right',
    D: 'right',
    z: 'up',
    Z: 'up',
    w: 'up',
    W: 'up'
  });

  const POWERUP_LABEL_KEYS = Object.freeze({
    multi_shot: 'index.sections.starsWar.powerups.multi',
    rapid_fire: 'index.sections.starsWar.powerups.rapid',
    magnet: 'index.sections.starsWar.powerups.magnet',
    enemy_slow: 'index.sections.starsWar.powerups.slow',
    shield: 'index.sections.starsWar.powerups.shield'
  });

  const translate = (() => {
    const translator = typeof window !== 'undefined' && typeof window.t === 'function'
      ? window.t.bind(window)
      : null;
    return (key, params) => {
      if (translator) {
        try {
          return translator(key, params);
        } catch (error) {
          return key;
        }
      }
      return key;
    };
  })();

  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const api = window.ArcadeAutosave;
    if (!api || typeof api.get !== 'function' || typeof api.set !== 'function') {
      return null;
    }
    return api;
  }

  function sanitizeAutosaveData(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const toNumber = value => {
      const numeric = Number(value);
      return Number.isFinite(numeric) && numeric >= 0 ? numeric : 0;
    };
    const lastSeed = typeof raw.lastSeed === 'string'
      ? raw.lastSeed.trim().slice(0, 32)
      : '';
    return {
      lastSeed,
      bestScore: toNumber(raw.bestScore ?? raw.score ?? raw.highScore),
      bestTime: toNumber(raw.bestTime ?? raw.bestTimeSeconds ?? raw.time),
      bestWave: Math.floor(toNumber(raw.bestWave ?? raw.wave)),
      bestDifficulty: Number.isFinite(Number(raw.bestDifficulty ?? raw.difficulty))
        ? Math.max(0, Number(raw.bestDifficulty ?? raw.difficulty))
        : 0
    };
  }

  function serializeAutosaveData() {
    const safeSeed = (state.rngSeed || state.lastSeed || '').toString().slice(0, 32);
    const normalizeNumber = value => (Number.isFinite(value) && value >= 0 ? value : 0);
    const clampDifficulty = value => {
      if (!Number.isFinite(value)) {
        return 0;
      }
      return Math.max(0, value);
    };
    return {
      version: 1,
      lastSeed: safeSeed,
      bestScore: Math.floor(normalizeNumber(state.records.bestScore)),
      bestTime: normalizeNumber(state.records.bestTime),
      bestWave: Math.floor(normalizeNumber(state.records.bestWave)),
      bestDifficulty: clampDifficulty(state.records.bestDifficulty)
    };
  }

  function persistAutosave() {
    const api = getAutosaveApi();
    if (!api) {
      return;
    }
    try {
      api.set(AUTOSAVE_KEY, serializeAutosaveData());
    } catch (error) {
      // Ignore autosave persistence errors
    }
  }

  function scheduleAutosave() {
    if (typeof window === 'undefined') {
      return;
    }
    if (autosaveTimerId != null) {
      window.clearTimeout(autosaveTimerId);
    }
    autosaveTimerId = window.setTimeout(() => {
      autosaveTimerId = null;
      persistAutosave();
    }, 200);
  }

  function flushAutosave() {
    if (typeof window !== 'undefined' && autosaveTimerId != null) {
      window.clearTimeout(autosaveTimerId);
      autosaveTimerId = null;
    }
    persistAutosave();
  }

  function loadAutosave() {
    const api = getAutosaveApi();
    if (!api) {
      return;
    }
    let raw = null;
    try {
      raw = api.get(AUTOSAVE_KEY);
    } catch (error) {
      return;
    }
    const data = sanitizeAutosaveData(raw);
    if (!data) {
      return;
    }
    state.records.bestScore = data.bestScore;
    state.records.bestTime = data.bestTime;
    state.records.bestWave = Math.max(0, data.bestWave);
    state.records.bestDifficulty = data.bestDifficulty > 0 ? data.bestDifficulty : 1;
    state.lastSeed = data.lastSeed;
  }

  function announceStatus(key, params) {
    if (!elements.screenReaderStatus) {
      return;
    }
    const message = translate(key, params);
    elements.screenReaderStatus.textContent = message;
  }

  function resetPlayer() {
    player.x = CANVAS_WIDTH / 2;
    player.y = CANVAS_HEIGHT * 0.8;
    player.velocityX = 0;
    player.velocityY = 0;
    player.hp = PLAYER_MAX_HP;
    player.shieldCharges = 0;
    player.baseSpeed = PLAYER_BASE_STATS.speed;
    player.baseCooldown = PLAYER_BASE_STATS.cooldown;
    player.baseBulletSpeed = PLAYER_BASE_STATS.bulletSpeed;
    player.baseMulti = PLAYER_BASE_STATS.multi;
    player.fireTimer = 0;
    player.tempMultiBonus = 0;
    player.rapidFireFactor = 1;
    player.magnetActive = false;
    player.lastDamageTime = -Infinity;
  }

  function clearState() {
    state.enemies.length = 0;
    state.enemyBullets.length = 0;
    state.playerBullets.length = 0;
    state.drops.length = 0;
    state.effects.length = 0;
    state.spawnQueue.length = 0;
    state.powerups = {};
  }

  function resetGame() {
    state.score = 0;
    state.elapsed = 0;
    state.difficulty = 1;
    state.wave = 0;
    state.spawnInterval = BASE_SPAWN_INTERVAL;
    state.maxOnScreen = 10 + Math.floor(3 * state.difficulty);
    state.spawnTimer = 0;
    state.heartPitySeconds = 0;
    state.nextDifficultyTick = 60;
    state.nextSpawnDensityPush = { at12: false, at14: false };
    state.activeMilestones.clear();
    state.gameOver = false;
    state.overlayMode = 'ready';
    state.lastTimestamp = 0;
    state.timeSinceLastUpdate = 0;
    clearState();
    resetPlayer();
    updateUi(true);
  }

  function randomSeedString() {
    const alphabet = '0123456789abcdefghijklmnopqrstuvwxyz';
    let result = '';
    for (let i = 0; i < 8; i += 1) {
      const index = Math.floor(Math.random() * alphabet.length);
      result += alphabet[index];
    }
    return result;
  }

  function applySeed(seedString) {
    const trimmed = typeof seedString === 'string' ? seedString.trim() : '';
    const seed = trimmed ? strToSeed(trimmed) : Math.floor(Math.random() * 0xffffffff);
    state.rngSeed = trimmed || seed.toString(16);
    state.rng = rngMulberry32(seed);
    state.lastSeed = state.rngSeed;
    scheduleAutosave();
  }

  function getRandomFloat() {
    return state.rng();
  }

  function getRandomInt(minInclusive, maxExclusive) {
    return Math.floor(getRandomFloat() * (maxExclusive - minInclusive)) + minInclusive;
  }

  function pickRandom(array) {
    if (!array.length) {
      return null;
    }
    return array[getRandomInt(0, array.length)];
  }

  function scheduleNextWave() {
    state.wave += 1;
    const spawnPlan = generateWave();
    state.spawnQueue.push(...spawnPlan);
    state.spawnTimer = 0;
  }

  function getAllowedEnemies(difficulty) {
    const allowed = [ENEMY_TYPES.drone, ENEMY_TYPES.fast];
    if (difficulty >= 2) {
      allowed.push(ENEMY_TYPES.gunner);
    }
    if (difficulty >= 3) {
      allowed.push(ENEMY_TYPES.tank);
    }
    if (difficulty >= 4) {
      allowed.push(ENEMY_TYPES.kamikaze);
    }
    if (difficulty >= 5) {
      allowed.push(ENEMY_TYPES.sniper, ENEMY_TYPES.carrier);
    }
    return allowed;
  }

  function generateWave() {
    const D = state.difficulty;
    const baseCount = 8;
    const count = baseCount + Math.floor(2 * D);
    const formation = pickRandom(FORMATIONS);
    const path = pickRandom(PATHS);
    const allowed = getAllowedEnemies(D);
    const waveEntries = [];
    const laneCount = Math.max(3, Math.min(7, Math.floor(3 + D)));
    for (let i = 0; i < count; i += 1) {
      const type = pickRandom(allowed);
      waveEntries.push(createSpawnEntry(type, formation, path, laneCount, i, count));
    }
    if (state.wave % 5 === 0) {
      const miniboss = createSpawnEntry(ENEMY_TYPES.miniboss, 'COLUMN', 'LINE', laneCount, Math.floor(count / 2), count);
      miniboss.delay += 2;
      waveEntries.push(miniboss);
    }
    waveEntries.sort((a, b) => a.delay - b.delay);
    return waveEntries;
  }

  function createSpawnEntry(type, formation, path, laneCount, index, total) {
    const delay = 0.45 + index * 0.28 + getRandomFloat() * 0.2;
    let lane = index % laneCount;
    let offset = 0;
    switch (formation) {
      case 'V':
        lane = index % laneCount;
        offset = (lane - (laneCount - 1) / 2) * 32;
        break;
      case 'COLUMN':
        lane = Math.floor(laneCount / 2);
        offset = (index % 4) * 40;
        break;
      case 'ARC':
        lane = index % laneCount;
        offset = Math.sin((index / total) * Math.PI) * 80;
        break;
      case 'SWARM':
        lane = getRandomInt(0, laneCount);
        offset = (getRandomFloat() - 0.5) * 120;
        break;
      case 'LINE':
      default:
        lane = index % laneCount;
        offset = 0;
        break;
    }
    return {
      type,
      formation,
      path,
      delay,
      lane,
      offset
    };
  }

  function spawnEnemy(entry) {
    const D = state.difficulty;
    const enemyWidth = entry.type.id === 'miniboss' ? 60 : entry.type.id === 'tank' ? 48 : 40;
    const enemyHeight = enemyWidth;
    const laneWidth = CANVAS_WIDTH / Math.max(4, 6);
    const baseX = laneWidth * (entry.lane + 0.5);
    const spawnX = clamp(baseX + entry.offset, enemyWidth / 2, CANVAS_WIDTH - enemyWidth / 2);
    const spawnY = -enemyHeight - getRandomFloat() * 60;
    const hp = entry.type.id === 'miniboss'
      ? ENEMY_TYPES.miniboss.baseHp + Math.floor(3 * D)
      : entry.type.baseHp + Math.floor(0.6 * D);
    const speed = entry.type.id === 'miniboss'
      ? ENEMY_TYPES.miniboss.baseSpeed * (1 + 0.03 * D)
      : entry.type.baseSpeed * (1 + 0.09 * D);
    const enemy = {
      id: entry.type.id,
      type: entry.type,
      x: spawnX,
      y: spawnY,
      width: enemyWidth,
      height: enemyHeight,
      hp,
      maxHp: hp,
      speed,
      path: entry.path,
      formation: entry.formation,
      age: 0,
      fireTimer: entry.type.canShoot ? (entry.type.fireRate / (1 + 0.06 * D)) * getRandomFloat() : 0,
      lockedTargetX: null,
      remove: false,
      waveIndex: state.wave
    };
    state.enemies.push(enemy);
  }

  function getPlayerBounds() {
    return {
      left: player.x - player.width / 2,
      right: player.x + player.width / 2,
      top: player.y - player.height / 2,
      bottom: player.y + player.height / 2
    };
  }

  function intersects(a, b) {
    return !(
      a.right < b.left
      || a.left > b.right
      || a.bottom < b.top
      || a.top > b.bottom
    );
  }

  function addPlayerBullet(x, y, offsetIndex, total) {
    const spread = 18;
    const offset = (offsetIndex - (total - 1) / 2) * spread;
    state.playerBullets.push({
      x: x + offset,
      y,
      width: PLAYER_BULLET_SIZE.width,
      height: PLAYER_BULLET_SIZE.height,
      speed: player.baseBulletSpeed,
      remove: false
    });
  }

  function firePlayerWeapons(delta) {
    player.fireTimer -= delta;
    if (player.fireTimer > 0) {
      return;
    }
    const baseCooldown = player.baseCooldown;
    const effectiveCooldown = Math.max(0.05, baseCooldown / player.rapidFireFactor);
    player.fireTimer = effectiveCooldown;
    const totalShots = Math.min(3, player.baseMulti + player.tempMultiBonus);
    for (let i = 0; i < totalShots; i += 1) {
      addPlayerBullet(player.x, player.y - player.height / 2 - 6, i, totalShots);
    }
  }

  function spawnEnemyBullet(enemy, angle, speedMultiplier = 1) {
    const vx = Math.cos(angle);
    const vy = Math.sin(angle);
    const slowFactor = isPowerupActive('enemy_slow') ? 0.75 : 1;
    state.enemyBullets.push({
      x: enemy.x,
      y: enemy.y,
      width: ENEMY_BULLET_SIZE.width,
      height: ENEMY_BULLET_SIZE.height,
      velocityX: vx * enemy.type.bulletSpeed * speedMultiplier * slowFactor,
      velocityY: vy * enemy.type.bulletSpeed * speedMultiplier * slowFactor,
      remove: false
    });
  }

  function aimAtPlayer(enemy) {
    const dx = player.x - enemy.x;
    const dy = player.y - enemy.y;
    const angle = Math.atan2(dy, dx);
    spawnEnemyBullet(enemy, angle, 1);
  }

  function fireEnemyWeapons(enemy, delta) {
    if (!enemy.type.canShoot) {
      return;
    }
    const baseRate = enemy.type.fireRate / (1 + 0.06 * state.difficulty);
    enemy.fireTimer -= delta;
    if (enemy.fireTimer > 0) {
      return;
    }
    enemy.fireTimer = baseRate;
    if (enemy.id === 'miniboss') {
      const bulletCount = 8;
      for (let i = 0; i < bulletCount; i += 1) {
        const angle = (Math.PI * 2 * i) / bulletCount;
        spawnEnemyBullet(enemy, angle, 1);
      }
    } else {
      aimAtPlayer(enemy);
    }
  }

  function moveEnemy(enemy, delta) {
    const slowFactor = isPowerupActive('enemy_slow') ? 0.75 : 1;
    const speed = enemy.speed * delta * slowFactor;
    enemy.age += delta;
    let nextX = enemy.x;
    let nextY = enemy.y;
    switch (enemy.path) {
      case 'SIN': {
        const amplitude = 60;
        const frequency = 2;
        nextX = enemy.x + Math.sin(enemy.age * frequency) * amplitude * delta;
        nextY = enemy.y + speed;
        break;
      }
      case 'CIRCLE': {
        const radius = 40 + Math.sin(enemy.age * 0.5) * 12;
        const angle = enemy.age * 2;
        nextX = enemy.x + Math.cos(angle) * radius * delta;
        nextY = enemy.y + speed * 0.75;
        break;
      }
      case 'SWOOP': {
        const direction = enemy.x < CANVAS_WIDTH / 2 ? 1 : -1;
        nextX = enemy.x + direction * speed * 0.35;
        nextY = enemy.y + speed * (enemy.age > 1.6 ? 1.6 : 0.9);
        break;
      }
      case 'ARC': {
        const t = Math.min(1, enemy.age / 3);
        const curve = Math.sin(t * Math.PI) * 90;
        nextX = enemy.x + curve * delta * (enemy.waveIndex % 2 === 0 ? 1 : -1);
        nextY = enemy.y + speed;
        break;
      }
      case 'LINE':
      default:
        nextX = enemy.x;
        nextY = enemy.y + speed;
        break;
    }

    if (enemy.id === 'kamikaze') {
      if (enemy.lockedTargetX == null && enemy.y > CANVAS_HEIGHT * 0.25) {
        enemy.lockedTargetX = player.x;
      }
      const targetX = enemy.lockedTargetX != null ? enemy.lockedTargetX : player.x;
      nextX += (targetX - enemy.x) * delta * 1.5;
      nextY += speed * 0.6;
    }

    enemy.x = clamp(nextX, enemy.width / 2, CANVAS_WIDTH - enemy.width / 2);
    enemy.y = nextY;
  }

  function isPowerupActive(id) {
    const entry = state.powerups[id];
    if (!entry) {
      return false;
    }
    if (entry.expiresAt === Infinity) {
      return true;
    }
    return entry.expiresAt > state.elapsed;
  }

  function updatePowerups(delta) {
    const now = state.elapsed;
    Object.keys(state.powerups).forEach(id => {
      const entry = state.powerups[id];
      if (!entry) {
        return;
      }
      if (entry.expiresAt !== Infinity && entry.expiresAt <= now) {
        delete state.powerups[id];
        if (id === 'multi_shot') {
          player.tempMultiBonus = 0;
        } else if (id === 'rapid_fire') {
          player.rapidFireFactor = 1;
        } else if (id === 'magnet') {
          player.magnetActive = false;
        }
      }
    });
  }

  function applyPowerup(id) {
    const def = POWERUP_DEFS[id];
    if (!def) {
      return;
    }
    if (id === 'heart') {
      if (player.hp < PLAYER_MAX_HP) {
        player.hp = Math.min(PLAYER_MAX_HP, player.hp + 1);
        state.heartPitySeconds = 0;
        announceStatus('index.sections.starsWar.status.heart');
      }
      return;
    }
    const expiresAt = def.duration === Infinity ? Infinity : state.elapsed + def.duration;
    state.powerups[id] = {
      id,
      expiresAt
    };
    if (id === 'multi_shot') {
      player.tempMultiBonus = 2;
    } else if (id === 'rapid_fire') {
      player.rapidFireFactor = 1.7;
    } else if (id === 'shield') {
      player.shieldCharges += 1;
    } else if (id === 'magnet') {
      player.magnetActive = true;
    }
    announceStatus('index.sections.starsWar.status.powerup', { powerup: translate(POWERUP_LABEL_KEYS[id] || id) });
  }

  function getDropChanceForEnemy(enemy) {
    return enemy.id === 'carrier' ? 0.8 : 0.08;
  }

  function rollDrop(enemy) {
    const chance = getDropChanceForEnemy(enemy);
    if (getRandomFloat() >= chance) {
      return;
    }
    const heartChance = player.hp < PLAYER_MAX_HP
      ? Math.min(0.02 + Math.floor(state.heartPitySeconds / 20) * 0.01, 0.08)
      : 0;
    const roll = getRandomFloat();
    if (heartChance > 0 && roll < heartChance) {
      spawnDrop(enemy.x, enemy.y, 'heart');
      state.heartPitySeconds = 0;
      return;
    }
    const candidates = ['multi_shot', 'rapid_fire', 'shield', 'magnet', 'enemy_slow'];
    const id = pickRandom(candidates);
    spawnDrop(enemy.x, enemy.y, id);
  }

  function spawnDrop(x, y, id) {
    state.drops.push({
      id,
      x,
      y,
      vy: DROP_BASE_SPEED,
      remove: false
    });
  }

  function addExplosion(x, y, radius) {
    state.effects.push({
      x,
      y,
      radius,
      age: 0,
      duration: 0.45
    });
  }

  function damagePlayer() {
    const timeSinceLastHit = state.elapsed - player.lastDamageTime;
    if (timeSinceLastHit < PLAYER_DAMAGE_COOLDOWN) {
      return;
    }
    if (player.shieldCharges > 0) {
      player.shieldCharges -= 1;
      delete state.powerups.shield;
      announceStatus('index.sections.starsWar.status.shield');
      player.lastDamageTime = state.elapsed;
      return;
    }
    if (player.hp <= 0) {
      return;
    }
    player.hp -= 1;
    player.lastDamageTime = state.elapsed;
    announceStatus('index.sections.starsWar.status.damage', { hp: player.hp });
    if (player.hp <= 0) {
      handleGameOver();
    }
  }

  function damageEnemy(enemy, damage = 1) {
    enemy.hp -= damage;
    if (enemy.hp <= 0) {
      enemy.remove = true;
      state.score += enemy.type.score;
      rollDrop(enemy);
      addExplosion(enemy.x, enemy.y, enemy.id === 'miniboss' ? 80 : 45);
    }
  }

  function updateDrops(delta) {
    const playerBounds = getPlayerBounds();
    state.drops.forEach(drop => {
      if (!Number.isFinite(drop.vy) || drop.vy < DROP_BASE_SPEED) {
        drop.vy = DROP_BASE_SPEED;
      }
      if (player.magnetActive) {
        const dx = player.x - drop.x;
        const dy = player.y - drop.y;
        const distance = Math.hypot(dx, dy);
        if (distance < MAGNET_RADIUS && distance > 1) {
          const speed = MAGNET_PULL_SPEED * delta;
          drop.x += (dx / distance) * speed;
          drop.y += (dy / distance) * speed;
        } else {
          drop.y += drop.vy * delta;
        }
      } else {
        drop.y += drop.vy * delta;
      }
      const dropBounds = {
        left: drop.x - DROP_SIZE / 2,
        right: drop.x + DROP_SIZE / 2,
        top: drop.y - DROP_SIZE / 2,
        bottom: drop.y + DROP_SIZE / 2
      };
      if (intersects(playerBounds, dropBounds)) {
        applyPowerup(drop.id);
        drop.remove = true;
      }
      if (drop.y > CANVAS_HEIGHT + DROP_SIZE) {
        drop.remove = true;
      }
    });
    state.drops = state.drops.filter(drop => !drop.remove);
  }

  function updatePlayer(delta) {
    const speed = player.baseSpeed * delta;
    const areaTop = CANVAS_HEIGHT * (1 - PLAYER_AREA_TOP_RATIO);
    let moveX = 0;
    let moveY = 0;
    if (inputState.left) moveX -= 1;
    if (inputState.right) moveX += 1;
    if (inputState.up) moveY -= 1;
    if (inputState.down) moveY += 1;

    if (inputState.pointerActive) {
      const dx = inputState.pointerX - player.x;
      const dy = inputState.pointerY - player.y;
      const dist = Math.hypot(dx, dy);
      if (dist > 4) {
        moveX += (dx / dist) * Math.min(1, dist / 60);
        moveY += (dy / dist) * Math.min(1, dist / 60);
      }
    }

    if (moveX !== 0 || moveY !== 0) {
      const length = Math.hypot(moveX, moveY) || 1;
      player.x += (moveX / length) * speed;
      player.y += (moveY / length) * speed;
    }

    player.x = clamp(player.x, player.width / 2, CANVAS_WIDTH - player.width / 2);
    player.y = clamp(player.y, areaTop, CANVAS_HEIGHT - player.height / 2);
  }

  function updatePlayerBullets(delta) {
    state.playerBullets.forEach(bullet => {
      bullet.y -= player.baseBulletSpeed * delta;
      if (bullet.y < -bullet.height) {
        bullet.remove = true;
      }
    });
  }

  function updateEnemyBullets(delta) {
    const playerBounds = getPlayerBounds();
    state.enemyBullets.forEach(bullet => {
      bullet.x += bullet.velocityX * delta;
      bullet.y += bullet.velocityY * delta;
      const bulletBounds = {
        left: bullet.x - bullet.width / 2,
        right: bullet.x + bullet.width / 2,
        top: bullet.y - bullet.height / 2,
        bottom: bullet.y + bullet.height / 2
      };
      if (intersects(playerBounds, bulletBounds)) {
        bullet.remove = true;
        damagePlayer();
      }
      if (bullet.y > CANVAS_HEIGHT + 60 || bullet.y < -60 || bullet.x < -60 || bullet.x > CANVAS_WIDTH + 60) {
        bullet.remove = true;
      }
    });
    state.enemyBullets = state.enemyBullets.filter(bullet => !bullet.remove);
  }

  function updateEnemies(delta) {
    const playerBounds = getPlayerBounds();
    state.enemies.forEach(enemy => {
      moveEnemy(enemy, delta);
      fireEnemyWeapons(enemy, delta);
      const enemyBounds = {
        left: enemy.x - enemy.width / 2,
        right: enemy.x + enemy.width / 2,
        top: enemy.y - enemy.height / 2,
        bottom: enemy.y + enemy.height / 2
      };
      if (enemyBounds.top > CANVAS_HEIGHT + enemy.height) {
        enemy.remove = true;
      }
      if (intersects(playerBounds, enemyBounds)) {
        enemy.remove = true;
        addExplosion(enemy.x, enemy.y, 40);
        damagePlayer();
      }
    });
  }

  function resolveBulletHits() {
    state.playerBullets.forEach(bullet => {
      if (bullet.remove) {
        return;
      }
      const bounds = {
        left: bullet.x - bullet.width / 2,
        right: bullet.x + bullet.width / 2,
        top: bullet.y - bullet.height / 2,
        bottom: bullet.y + bullet.height / 2
      };
      for (let i = 0; i < state.enemies.length; i += 1) {
        const enemy = state.enemies[i];
        if (enemy.remove) {
          continue;
        }
        const enemyBounds = {
          left: enemy.x - enemy.width / 2,
          right: enemy.x + enemy.width / 2,
          top: enemy.y - enemy.height / 2,
          bottom: enemy.y + enemy.height / 2
        };
        if (intersects(bounds, enemyBounds)) {
          bullet.remove = true;
          damageEnemy(enemy);
          break;
        }
      }
    });
    state.playerBullets = state.playerBullets.filter(bullet => !bullet.remove);
    state.enemies = state.enemies.filter(enemy => !enemy.remove);
  }

  function updateEffects(delta) {
    state.effects.forEach(effect => {
      effect.age += delta;
      if (effect.age >= effect.duration) {
        effect.remove = true;
      }
    });
    state.effects = state.effects.filter(effect => !effect.remove);
  }

  function maybeSpawnEnemies(delta) {
    if (!state.spawnQueue.length) {
      return;
    }
    state.spawnTimer -= delta;
    if (state.spawnTimer > 0) {
      return;
    }
    const activeCount = state.enemies.length;
    if (activeCount >= state.maxOnScreen) {
      state.spawnTimer = 0.1;
      return;
    }
    const next = state.spawnQueue.shift();
    if (next) {
      spawnEnemy(next);
    }
    state.spawnTimer = Math.max(MIN_SPAWN_INTERVAL, state.spawnInterval * (0.8 + getRandomFloat() * 0.4));
  }

  function updateDifficulty(delta) {
    const previousDifficulty = state.difficulty;
    while (state.elapsed >= state.nextDifficultyTick) {
      state.difficulty = Math.min(8, state.difficulty + 0.4);
      state.nextDifficultyTick += 60;
    }
    if (state.wave > 0 && !state.spawnQueue.length && !state.enemies.length) {
      state.difficulty = Math.min(8, state.difficulty + 0.25);
      scheduleNextWave();
    }
    if (previousDifficulty !== state.difficulty) {
      state.maxOnScreen = 10 + Math.floor(3 * state.difficulty);
    }
    if (!state.nextSpawnDensityPush.at12 && state.elapsed >= 12 * 60) {
      state.spawnInterval = Math.max(MIN_SPAWN_INTERVAL, state.spawnInterval * 0.7);
      state.nextSpawnDensityPush.at12 = true;
    }
    if (!state.nextSpawnDensityPush.at14 && state.elapsed >= 14 * 60) {
      state.maxOnScreen += 6;
      state.nextSpawnDensityPush.at14 = true;
    }
  }

  function applyMilestones() {
    for (const milestone of PLAYER_MILESTONES) {
      if (state.elapsed >= milestone.time && !state.activeMilestones.has(milestone.time)) {
        milestone.apply(player);
        state.activeMilestones.add(milestone.time);
      }
    }
  }

  function updateHeartPity(delta) {
    if (player.hp < PLAYER_MAX_HP) {
      state.heartPitySeconds += delta;
    }
  }

  function updateUi() {
    if (elements.scoreValue) {
      elements.scoreValue.textContent = formatNumber(state.score);
    }
    if (elements.timeValue) {
      elements.timeValue.textContent = formatTime(state.elapsed);
    }
    if (elements.waveValue) {
      elements.waveValue.textContent = state.wave.toString();
    }
    if (elements.difficultyValue) {
      elements.difficultyValue.textContent = state.difficulty.toFixed(1);
    }
    if (elements.livesContainer) {
      const fragments = document.createDocumentFragment();
      for (let i = 0; i < PLAYER_MAX_HP; i += 1) {
        const span = document.createElement('span');
        span.className = 'stars-war__life';
        span.setAttribute('aria-hidden', 'true');
        if (i < player.hp) {
          span.classList.add('stars-war__life--full');
        }
        fragments.appendChild(span);
      }
      if (player.shieldCharges > 0) {
        const shield = document.createElement('span');
        shield.className = 'stars-war__life stars-war__life--shield';
        shield.setAttribute('aria-hidden', 'true');
        shield.textContent = player.shieldCharges > 1 ? `×${player.shieldCharges}` : '';
        fragments.appendChild(shield);
      }
      elements.livesContainer.innerHTML = '';
      elements.livesContainer.appendChild(fragments);
    }
    if (elements.powerupList) {
      elements.powerupList.innerHTML = '';
      Object.keys(state.powerups)
        .filter(id => id !== 'shield')
        .forEach(id => {
          const entry = state.powerups[id];
          if (!entry) {
            return;
          }
          const li = document.createElement('li');
          li.className = 'stars-war__powerup-item';
          const label = document.createElement('span');
          label.className = 'stars-war__powerup-label';
          label.textContent = translate(POWERUP_LABEL_KEYS[id] || id);
          li.appendChild(label);
          if (entry.expiresAt !== Infinity) {
            const remaining = Math.max(0, entry.expiresAt - state.elapsed);
            const duration = POWERUP_DEFS[id]?.duration || 1;
            const progress = 1 - remaining / duration;
            const bar = document.createElement('span');
            bar.className = 'stars-war__powerup-progress';
            bar.style.setProperty('--progress', Math.min(1, Math.max(0, progress)).toString());
            li.appendChild(bar);
          }
          elements.powerupList.appendChild(li);
        });
      if (player.shieldCharges > 0) {
        const li = document.createElement('li');
        li.className = 'stars-war__powerup-item';
        const label = document.createElement('span');
        label.className = 'stars-war__powerup-label';
        label.textContent = translate(POWERUP_LABEL_KEYS.shield);
        const count = document.createElement('span');
        count.className = 'stars-war__powerup-count';
        count.textContent = `×${player.shieldCharges}`;
        li.appendChild(label);
        li.appendChild(count);
        elements.powerupList.appendChild(li);
      }
    }
  }

  function renderBackground(ctx) {
    const gradient = ctx.createLinearGradient(0, 0, 0, CANVAS_HEIGHT);
    gradient.addColorStop(0, '#050912');
    gradient.addColorStop(1, '#0b1836');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
  }

  function drawPlayer(ctx) {
    const sprite = fleet.player;
    if (sprite) {
      ctx.drawImage(sprite, player.x - sprite.width / 2, player.y - sprite.height / 2);
    } else {
      ctx.fillStyle = '#6be6ff';
      ctx.fillRect(player.x - player.width / 2, player.y - player.height / 2, player.width, player.height);
    }
  }

  function drawEnemies(ctx) {
    state.enemies.forEach(enemy => {
      const sprite = fleet[enemy.id];
      if (sprite) {
        ctx.drawImage(sprite, enemy.x - sprite.width / 2, enemy.y - sprite.height / 2);
      } else {
        ctx.fillStyle = '#ff5a5a';
        ctx.fillRect(enemy.x - enemy.width / 2, enemy.y - enemy.height / 2, enemy.width, enemy.height);
      }
      if (enemy.maxHp > enemy.hp) {
        const ratio = Math.max(0, enemy.hp / enemy.maxHp);
        const barWidth = enemy.width;
        const barHeight = 4;
        ctx.fillStyle = 'rgba(0,0,0,0.35)';
        ctx.fillRect(enemy.x - barWidth / 2, enemy.y - enemy.height / 2 - 10, barWidth, barHeight);
        ctx.fillStyle = '#67e8f9';
        ctx.fillRect(enemy.x - barWidth / 2, enemy.y - enemy.height / 2 - 10, barWidth * ratio, barHeight);
      }
    });
  }

  function drawBullets(ctx) {
    ctx.fillStyle = '#c3f0ff';
    state.playerBullets.forEach(bullet => {
      ctx.fillRect(
        bullet.x - bullet.width / 2,
        bullet.y - bullet.height / 2,
        bullet.width,
        bullet.height
      );
    });
    ctx.fillStyle = '#ff8a8a';
    state.enemyBullets.forEach(bullet => {
      ctx.fillRect(
        bullet.x - bullet.width / 2,
        bullet.y - bullet.height / 2,
        bullet.width,
        bullet.height
      );
    });
  }

  function drawDrops(ctx) {
    state.drops.forEach(drop => {
      const style = POWERUP_VISUALS[drop.id] || POWERUP_VISUALS.default;
      const half = DROP_SIZE / 2;
      const nose = half;
      ctx.save();
      ctx.translate(drop.x, drop.y);

      ctx.shadowColor = style.glow;
      ctx.shadowBlur = 8;
      ctx.beginPath();
      ctx.moveTo(0, -nose);
      ctx.lineTo(half * 0.78, -half * 0.1);
      ctx.lineTo(half * 0.6, half * 0.65);
      ctx.lineTo(0, half * 0.95);
      ctx.lineTo(-half * 0.6, half * 0.65);
      ctx.lineTo(-half * 0.78, -half * 0.1);
      ctx.closePath();
      ctx.fillStyle = style.base;
      ctx.fill();

      ctx.shadowBlur = 0;
      ctx.strokeStyle = 'rgba(5, 9, 18, 0.65)';
      ctx.lineWidth = 2;
      ctx.stroke();

      ctx.fillStyle = style.accent;
      ctx.fillRect(-half * 0.7, -half * 0.05, half * 0.25, half * 0.6);
      ctx.fillRect(half * 0.45, -half * 0.05, half * 0.25, half * 0.6);
      ctx.fillRect(-half * 0.22, -half * 0.35, half * 0.44, half * 0.72);

      ctx.fillStyle = style.cabin;
      ctx.beginPath();
      ctx.ellipse(0, -half * 0.2, half * 0.2, half * 0.28, 0, 0, Math.PI * 2);
      ctx.fill();

      const textSize = Math.floor(DROP_SIZE * 0.48);
      ctx.font = `700 ${textSize}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillStyle = style.text;
      const textY = half * 0.35;
      ctx.fillText(style.letter, 0, textY);
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = 'rgba(5, 9, 18, 0.6)';
      ctx.strokeText(style.letter, 0, textY);

      ctx.restore();
    });
  }

  function drawEffects(ctx) {
    state.effects.forEach(effect => {
      const t = Math.min(1, effect.age / effect.duration);
      const radius = effect.radius * (0.8 + 0.6 * t);
      const alpha = 1 - t;
      ctx.beginPath();
      ctx.strokeStyle = `rgba(255, 180, 120, ${alpha})`;
      ctx.lineWidth = 4 * (1 - t);
      ctx.arc(effect.x, effect.y, radius, 0, Math.PI * 2);
      ctx.stroke();
      ctx.closePath();
    });
  }

  function render() {
    renderBackground(context);
    drawEffects(context);
    drawDrops(context);
    drawEnemies(context);
    drawBullets(context);
    drawPlayer(context);
  }

  function update(delta) {
    state.elapsed += delta;
    state.timeSinceLastUpdate += delta;
    applyMilestones();
    updateHeartPity(delta);
    updatePowerups(delta);
    maybeSpawnEnemies(delta);
    updateEnemies(delta);
    updatePlayer(delta);
    firePlayerWeapons(delta);
    updatePlayerBullets(delta);
    updateEnemyBullets(delta);
    updateDrops(delta);
    resolveBulletHits();
    updateEffects(delta);
    updateDifficulty(delta);
    updateUi();
    render();
  }

  function updateRecords() {
    let updated = false;
    if (state.score > state.records.bestScore) {
      state.records.bestScore = state.score;
      updated = true;
    }
    if (state.elapsed > state.records.bestTime) {
      state.records.bestTime = state.elapsed;
      updated = true;
    }
    if (state.wave > state.records.bestWave) {
      state.records.bestWave = state.wave;
      updated = true;
    }
    if (state.difficulty > state.records.bestDifficulty) {
      state.records.bestDifficulty = state.difficulty;
      updated = true;
    }
    if (updated) {
      scheduleAutosave();
    }
  }

  function handleGameOver() {
    state.gameOver = true;
    state.running = false;
    updateRecords();
    state.overlayMode = 'gameover';
    showOverlay(
      translate('index.sections.starsWar.overlay.gameOver.title'),
      translate('index.sections.starsWar.overlay.gameOver.message', {
        time: formatTime(state.elapsed),
        score: formatNumber(state.score)
      }),
      translate('index.sections.starsWar.overlay.retry')
    );
  }

  function showOverlay(title, message, primaryLabel, secondaryLabel) {
    if (!elements.overlay || !elements.overlayTitle || !elements.overlayMessage || !elements.overlayButton) {
      return;
    }
    elements.overlayTitle.textContent = title || '';
    elements.overlayMessage.textContent = message || '';
    elements.overlayButton.textContent = primaryLabel || '';
    elements.overlayButton.hidden = !primaryLabel;
    elements.overlayButton.disabled = !primaryLabel;
    elements.overlay.hidden = false;
    elements.overlay.setAttribute('aria-hidden', 'false');
  }

  function hideOverlay() {
    if (!elements.overlay) {
      return;
    }
    elements.overlay.hidden = true;
    elements.overlay.setAttribute('aria-hidden', 'true');
  }

  function startRun() {
    if (state.running) {
      return;
    }
    state.running = true;
    state.gameOver = false;
    state.overlayMode = 'running';
    const now = typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Date.now();
    state.lastTimestamp = now;
    hideOverlay();
    requestAnimationFrame(loop);
  }

  function restartRun(options = {}) {
    const { preserveSeed = false } = options;
    state.running = false;
    hideOverlay();
    flushAutosave();
    const seedValue = preserveSeed && state.rngSeed ? state.rngSeed : randomSeedString();
    applySeed(seedValue);
    resetGame();
    scheduleNextWave();
    startRun();
  }

  function loop(timestamp) {
    if (!state.running) {
      return;
    }
    if (!state.lastTimestamp) {
      state.lastTimestamp = timestamp;
    }
    const delta = Math.min(0.1, (timestamp - state.lastTimestamp) / 1000);
    state.lastTimestamp = timestamp;
    update(delta);
    if (state.running) {
      requestAnimationFrame(loop);
    }
  }

  function handleKeyDown(event) {
    const action = KEY_BINDINGS[event.key];
    if (action) {
      inputState[action] = true;
    }
    if (event.code === 'Space' && state.gameOver) {
      restartRun({ preserveSeed: true });
      event.preventDefault();
    }
  }

  function handleKeyUp(event) {
    const action = KEY_BINDINGS[event.key];
    if (action) {
      inputState[action] = false;
    }
  }

  function toCanvasCoordinates(event) {
    const rect = elements.canvas.getBoundingClientRect();
    const scaleX = CANVAS_WIDTH / rect.width;
    const scaleY = CANVAS_HEIGHT / rect.height;
    return {
      x: (event.clientX - rect.left) * scaleX,
      y: (event.clientY - rect.top) * scaleY
    };
  }

  function handlePointerDown(event) {
    event.preventDefault();
    const coords = toCanvasCoordinates(event);
    inputState.pointerActive = true;
    inputState.pointerX = coords.x;
    inputState.pointerY = coords.y;
  }

  function handlePointerMove(event) {
    if (!inputState.pointerActive) {
      return;
    }
    const coords = toCanvasCoordinates(event);
    inputState.pointerX = coords.x;
    inputState.pointerY = coords.y;
  }

  function handlePointerUp() {
    inputState.pointerActive = false;
  }

  function attachInputListeners() {
    if (typeof document !== 'undefined') {
      document.addEventListener('keydown', handleKeyDown);
      document.addEventListener('keyup', handleKeyUp);
    }
    elements.canvas.addEventListener('pointerdown', handlePointerDown);
    elements.canvas.addEventListener('pointermove', handlePointerMove);
    elements.canvas.addEventListener('pointerup', handlePointerUp);
    elements.canvas.addEventListener('pointerleave', handlePointerUp);
  }

  function detachInputListeners() {
    if (typeof document !== 'undefined') {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('keyup', handleKeyUp);
    }
    elements.canvas.removeEventListener('pointerdown', handlePointerDown);
    elements.canvas.removeEventListener('pointermove', handlePointerMove);
    elements.canvas.removeEventListener('pointerup', handlePointerUp);
    elements.canvas.removeEventListener('pointerleave', handlePointerUp);
  }

  function init() {
    attachInputListeners();
    loadAutosave();
    if (elements.overlayButton) {
      elements.overlayButton.addEventListener('click', () => {
        if (state.gameOver) {
          restartRun({ preserveSeed: true });
        }
      });
    }
    if (elements.restartButton) {
      elements.restartButton.addEventListener('click', () => {
        restartRun({ preserveSeed: false });
      });
    }
    const initialSeed = state.lastSeed || randomSeedString();
    applySeed(initialSeed);
    resetGame();
    scheduleNextWave();
    const pageElement = typeof document !== 'undefined'
      ? document.getElementById('starsWar')
      : null;
    if (pageElement && !pageElement.hasAttribute('hidden')) {
      startRun();
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', flushAutosave);
      window.addEventListener('pagehide', flushAutosave);
    }
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden') {
          flushAutosave();
        }
      });
    }
  }

  function onEnter() {
    if (!state.running && !state.gameOver) {
      startRun();
    }
  }

  function onLeave() {
    state.running = false;
    if (state.overlayMode !== 'gameover') {
      hideOverlay();
    }
    flushAutosave();
  }

  init();

  window.starsWarArcade = {
    onEnter,
    onLeave,
    restart: restartRun
  };
})();
