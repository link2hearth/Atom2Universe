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
    pauseButton: document.getElementById('starsWarPauseButton'),
    restartButton: document.getElementById('starsWarRestartButton'),
    difficultyButton: document.getElementById('starsWarDifficultyButton'),
    powerupList: document.getElementById('starsWarPowerupList'),
    livesContainer: document.getElementById('starsWarLives'),
    screenReaderStatus: document.getElementById('starsWarStatus')
  };

  if (!elements.canvas) {
    return;
  }

  const CONFIG_PATH = 'config/arcade/stars-war.json';
  const DEFAULT_CONFIG = Object.freeze({
    maxWaveDurationSeconds: 15,
    enemyBulletCap: Object.freeze({
      base: 10,
      perWave: 2
    }),
    shipHitboxScale: Object.freeze({
      player: 0.5,
      enemy: 0.5
    })
  });

  const DIFFICULTY_MODES = Object.freeze({
    EASY: 'easy',
    HARD: 'hard'
  });

  const EASY_ENEMY_HITBOX_SCALE = 1;
  const EASY_BULLET_CAP_FACTOR = 0.5;
  const GAME_STATE_ARCADE_ID = 'starsWar';
  const ARCADE_AUTOSAVE_STORAGE_KEY = 'atom2univers.arcadeSaves.v1';
  let pendingGameStatePayload = null;
  let gameStateSyncTimerId = null;

  function createEmptyRecordEntry() {
    return {
      bestScore: 0,
      bestTime: 0,
      bestWave: 0,
      bestDifficulty: 1,
      topRuns: []
    };
  }

  function createInitialRecordState() {
    return {
      modes: {
        [DIFFICULTY_MODES.EASY]: createEmptyRecordEntry(),
        [DIFFICULTY_MODES.HARD]: createEmptyRecordEntry()
      },
      lastMode: DIFFICULTY_MODES.HARD
    };
  }

  function toFiniteNumber(value, fallback) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : fallback;
  }

  function clampNumber(value, min, max, fallback) {
    const numeric = toFiniteNumber(value, fallback);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    if (numeric < min) {
      return min;
    }
    if (numeric > max) {
      return max;
    }
    return numeric;
  }

  function resolveConfig(raw) {
    if (!raw || typeof raw !== 'object') {
      return DEFAULT_CONFIG;
    }
    const maxWaveDuration = clampNumber(
      raw.maxWaveDurationSeconds,
      5,
      120,
      DEFAULT_CONFIG.maxWaveDurationSeconds
    );
    const bulletConfig = raw.enemyBulletCap && typeof raw.enemyBulletCap === 'object'
      ? raw.enemyBulletCap
      : {};
    const bulletBase = clampNumber(
      bulletConfig.base,
      0,
      999,
      DEFAULT_CONFIG.enemyBulletCap.base
    );
    const bulletPerWave = clampNumber(
      bulletConfig.perWave,
      0,
      200,
      DEFAULT_CONFIG.enemyBulletCap.perWave
    );
    const rawShipHitboxScale = raw.shipHitboxScale && typeof raw.shipHitboxScale === 'object'
      ? raw.shipHitboxScale
      : {};
    const playerHitboxScale = clampNumber(
      rawShipHitboxScale.player,
      0.1,
      2,
      DEFAULT_CONFIG.shipHitboxScale.player
    );
    const enemyHitboxScale = clampNumber(
      rawShipHitboxScale.enemy,
      0.1,
      2,
      DEFAULT_CONFIG.shipHitboxScale.enemy
    );
    return Object.freeze({
      maxWaveDurationSeconds: Math.max(5, Math.floor(maxWaveDuration)),
      enemyBulletCap: Object.freeze({
        base: Math.max(0, Math.floor(bulletBase)),
        perWave: Math.max(0, Math.floor(bulletPerWave))
      }),
      shipHitboxScale: Object.freeze({
        player: playerHitboxScale,
        enemy: enemyHitboxScale
      })
    });
  }

  const backgroundGradientCache = {
    gradient: null,
    context: null
  };

  let numberFormatterInstance = null;
  let numberFormatterLocale = null;

  function ensureNumberFormatter(locale) {
    if (typeof Intl === 'undefined' || typeof Intl.NumberFormat !== 'function') {
      numberFormatterInstance = null;
      numberFormatterLocale = null;
      return;
    }
    const normalized = typeof locale === 'string' && locale.trim() ? locale.trim() : null;
    if (numberFormatterInstance && numberFormatterLocale === normalized) {
      return;
    }
    try {
      numberFormatterInstance = normalized ? new Intl.NumberFormat([normalized]) : new Intl.NumberFormat();
      numberFormatterLocale = normalized;
    } catch (error) {
      try {
        numberFormatterInstance = new Intl.NumberFormat();
        numberFormatterLocale = null;
      } catch (fallbackError) {
        numberFormatterInstance = null;
        numberFormatterLocale = null;
      }
    }
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
  const INITIAL_WAVE_INTERVAL = 30;
  const MIN_WAVE_INTERVAL = 10;
  const WAVE_ACCELERATION_DURATION = 10 * 60;

  const PLAYER_BASE_STATS = Object.freeze({
    speed: 275,
    cooldown: 0.18,
    bulletSpeed: 360,
    multi: 1
  });

  const SIMULATION_STEP = 1 / 30;
  const MAX_ACCUMULATED_TIME = SIMULATION_STEP * 10;
  const UI_UPDATE_INTERVAL = 0.1;

  const PLAYER_MILESTONES = Object.freeze([
    { time: 120, apply(player) { player.baseMulti = Math.max(player.baseMulti, 2); } },
    { time: 240, apply(player) { player.baseCooldown *= 0.85; } },
    { time: 360, apply(player) { player.baseBulletSpeed *= 1.15; } },
    { time: 480, apply(player) { player.baseMulti = Math.max(player.baseMulti, 3); } },
    { time: 600, apply(player) { player.baseSpeed *= 1.1; } }
  ]);

  const TIME_REWARD_PER_MINUTE = 1;
  const TIME_REWARD_MILESTONES = Object.freeze([
    Object.freeze({ time: 300, bonus: 5 }),
    Object.freeze({ time: 600, bonus: 10 })
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
      glow: 'rgba(64, 218, 255, 0.55)',
      body: {
        highlight: '#6ff0ff',
        base: '#2da7ff',
        shadow: '#0a499f',
        edge: '#031835',
        shine: 'rgba(255, 255, 255, 0.35)'
      },
      bodyAccent: 'rgba(102, 228, 255, 0.4)',
      icon: drawDoubleCannonIcon,
      iconFill: '#f4fbff',
      iconStroke: '#082d5a',
      iconAccent: '#3dd2ff',
      iconScale: 0.72
    },
    rapid_fire: {
      glow: 'rgba(255, 134, 61, 0.6)',
      body: {
        highlight: '#ffb36a',
        base: '#ff7438',
        shadow: '#a52a00',
        edge: '#401101',
        shine: 'rgba(255, 238, 215, 0.4)'
      },
      bodyAccent: 'rgba(255, 214, 150, 0.35)',
      icon: drawRapidFireIcon,
      iconFill: '#fff5e6',
      iconStroke: '#742400',
      iconAccent: '#ff9b48',
      iconScale: 0.78
    },
    magnet: {
      glow: 'rgba(120, 244, 192, 0.55)',
      body: {
        highlight: '#a6ffd8',
        base: '#2fc684',
        shadow: '#0e6140',
        edge: '#04301c',
        shine: 'rgba(220, 255, 240, 0.38)'
      },
      icon: drawMagnetIcon,
      iconFill: '#eafff6',
      iconStroke: '#06402a',
      iconAccent: '#4af1a9',
      iconScale: 0.78
    },
    enemy_slow: {
      glow: 'rgba(150, 130, 255, 0.6)',
      body: {
        highlight: '#d5c8ff',
        base: '#8f82ff',
        shadow: '#3b2aa5',
        edge: '#1b1454',
        shine: 'rgba(240, 232, 255, 0.42)'
      },
      icon: drawSnowflakeIcon,
      iconFill: '#f4f0ff',
      iconStroke: '#352180',
      iconAccent: '#c7bdff',
      iconScale: 0.7
    },
    shield: {
      glow: 'rgba(88, 190, 255, 0.6)',
      body: {
        highlight: '#bfe4ff',
        base: '#4faaf6',
        shadow: '#0b4b9c',
        edge: '#06223f',
        shine: 'rgba(220, 242, 255, 0.45)'
      },
      icon: drawShieldIcon,
      iconFill: 'rgba(194, 232, 255, 0.9)',
      iconStroke: '#0d3a6b',
      iconAccent: 'rgba(115, 198, 255, 0.65)',
      iconScale: 0.76,
      iconOffsetY: -1
    },
    heart: {
      glow: 'rgba(255, 107, 129, 0.6)',
      body: {
        highlight: '#ff9db0',
        base: '#ff5c7a',
        shadow: '#b31230',
        edge: '#4a0c1b',
        shine: 'rgba(255, 220, 230, 0.45)'
      },
      icon: drawHeartIcon,
      iconFill: '#fff2f6',
      iconStroke: '#931e36',
      iconAccent: '#ffccd8',
      iconScale: 0.8
    },
    default: {
      glow: 'rgba(139, 233, 253, 0.45)',
      body: {
        highlight: '#d7f7ff',
        base: '#8be9fd',
        shadow: '#3088b0',
        edge: '#16475e',
        shine: 'rgba(255, 255, 255, 0.35)'
      },
      icon: drawStarIcon,
      iconFill: '#ffffff',
      iconStroke: '#14506a',
      iconScale: 0.75
    }
  });

  function beginCapsulePath(ctx, width, height, radius) {
    const r = Math.min(radius, height / 2, width / 2);
    ctx.beginPath();
    ctx.moveTo(-width / 2 + r, -height / 2);
    ctx.lineTo(width / 2 - r, -height / 2);
    ctx.arc(width / 2 - r, -height / 2 + r, r, -Math.PI / 2, 0);
    ctx.lineTo(width / 2, height / 2 - r);
    ctx.arc(width / 2 - r, height / 2 - r, r, 0, Math.PI / 2);
    ctx.lineTo(-width / 2 + r, height / 2);
    ctx.arc(-width / 2 + r, height / 2 - r, r, Math.PI / 2, Math.PI);
    ctx.lineTo(-width / 2, -height / 2 + r);
    ctx.arc(-width / 2 + r, -height / 2 + r, r, Math.PI, -Math.PI / 2);
    ctx.closePath();
  }

  function drawRoundedRect(ctx, x, y, width, height, radius) {
    const r = Math.min(radius, width / 2, height / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + width - r, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + r);
    ctx.lineTo(x + width, y + height - r);
    ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
    ctx.lineTo(x + r, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  function drawPowerupBadge(ctx, style, size, options = {}) {
    const height = size * 0.95;
    const width = size * 1.12;
    const radius = height / 2;
    const glowStrength = options.forHud ? 6 : 10;

    ctx.save();
    ctx.shadowColor = style.glow;
    ctx.shadowBlur = glowStrength;
    beginCapsulePath(ctx, width, height, radius);
    const gradient = ctx.createLinearGradient(0, -height / 2, 0, height / 2);
    gradient.addColorStop(0, style.body.highlight);
    gradient.addColorStop(0.55, style.body.base);
    gradient.addColorStop(1, style.body.shadow);
    ctx.fillStyle = gradient;
    ctx.fill();

    ctx.shadowBlur = 0;
    ctx.lineWidth = Math.max(1.5, size * 0.08);
    ctx.strokeStyle = style.body.edge;
    beginCapsulePath(ctx, width, height, radius);
    ctx.stroke();

    ctx.save();
    beginCapsulePath(ctx, width, height, radius);
    ctx.clip();
    const shineGradient = ctx.createLinearGradient(-width / 2, -height / 2, width / 2, -height / 2 + height * 0.7);
    shineGradient.addColorStop(0, style.body.shine);
    shineGradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.fillStyle = shineGradient;
    ctx.fillRect(-width / 2, -height / 2, width, height);
    ctx.restore();

    if (style.bodyAccent) {
      ctx.save();
      beginCapsulePath(ctx, width, height, radius);
      ctx.clip();
      ctx.fillStyle = style.bodyAccent;
      const stripeHeight = height * 0.18;
      ctx.globalAlpha = 0.6;
      ctx.fillRect(-width / 2, -stripeHeight / 2, width, stripeHeight);
      ctx.restore();
    }

    if (typeof style.icon === 'function') {
      ctx.save();
      ctx.translate(0, style.iconOffsetY || 0);
      const iconSize = size * (style.iconScale || 0.75);
      style.icon(ctx, iconSize, style);
      ctx.restore();
    }

    ctx.restore();
  }

  function drawHeartIcon(ctx, size, style) {
    const width = size * 0.95;
    const height = size * 0.88;
    const topCurveHeight = height * 0.45;
    ctx.beginPath();
    ctx.moveTo(0, height / 2);
    ctx.bezierCurveTo(width / 2, height / 2, width / 2, -topCurveHeight, 0, -height / 4);
    ctx.bezierCurveTo(-width / 2, -topCurveHeight, -width / 2, height / 2, 0, height / 2);
    ctx.closePath();
    ctx.fillStyle = style.iconFill;
    ctx.fill();
    ctx.lineWidth = Math.max(1, size * 0.08);
    ctx.strokeStyle = style.iconStroke;
    ctx.stroke();
    ctx.save();
    ctx.clip();
    const highlight = ctx.createLinearGradient(-width / 2, -height / 2, width / 2, 0);
    highlight.addColorStop(0, style.iconAccent || 'rgba(255, 255, 255, 0.4)');
    highlight.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.globalAlpha = 0.8;
    ctx.fillStyle = highlight;
    ctx.fillRect(-width / 2, -height / 2, width, height);
    ctx.restore();
  }

  function drawShieldIcon(ctx, size, style) {
    const width = size * 0.95;
    const height = size;
    ctx.beginPath();
    ctx.moveTo(0, -height / 2);
    ctx.quadraticCurveTo(width / 2, -height / 2 + height * 0.2, width / 2, -height * 0.05);
    ctx.quadraticCurveTo(width / 2, height * 0.55, 0, height / 2);
    ctx.quadraticCurveTo(-width / 2, height * 0.55, -width / 2, -height * 0.05);
    ctx.quadraticCurveTo(-width / 2, -height / 2 + height * 0.2, 0, -height / 2);
    ctx.closePath();
    const gradient = ctx.createLinearGradient(0, -height / 2, 0, height / 2);
    gradient.addColorStop(0, style.iconFill);
    gradient.addColorStop(1, style.iconAccent || style.iconFill);
    ctx.fillStyle = gradient;
    ctx.fill();
    ctx.lineWidth = Math.max(1, size * 0.08);
    ctx.strokeStyle = style.iconStroke;
    ctx.stroke();
    ctx.save();
    ctx.clip();
    const shine = ctx.createRadialGradient(0, -height / 3, height * 0.1, 0, -height / 3, height * 0.8);
    shine.addColorStop(0, 'rgba(255, 255, 255, 0.65)');
    shine.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.fillStyle = shine;
    ctx.fillRect(-width / 2, -height / 2, width, height);
    ctx.restore();
  }

  function drawDoubleCannonIcon(ctx, size, style) {
    const barrelWidth = size * 0.24;
    const barrelHeight = size * 0.72;
    const spacing = size * 0.34;
    ctx.fillStyle = style.iconFill;
    ctx.strokeStyle = style.iconStroke;
    ctx.lineWidth = Math.max(1, size * 0.08);
    [-1, 1].forEach(dir => {
      const x = (spacing / 2) * dir - barrelWidth / 2;
      const y = -barrelHeight / 2;
      drawRoundedRect(ctx, x, y, barrelWidth, barrelHeight, barrelWidth / 3);
      ctx.fill();
      drawRoundedRect(ctx, x, y, barrelWidth, barrelHeight, barrelWidth / 3);
      ctx.stroke();
    });
    ctx.fillStyle = style.iconAccent || style.iconFill;
    const bridgeHeight = size * 0.18;
    drawRoundedRect(ctx, -spacing / 2, -bridgeHeight / 2, spacing, bridgeHeight, bridgeHeight / 2);
    ctx.fill();
    drawRoundedRect(ctx, -spacing / 2, -bridgeHeight / 2, spacing, bridgeHeight, bridgeHeight / 2);
    ctx.stroke();
  }

  function drawRapidFireIcon(ctx, size, style) {
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.lineWidth = Math.max(1.2, size * 0.12);
    ctx.strokeStyle = style.iconStroke;
    ctx.fillStyle = style.iconFill;
    const length = size * 0.9;
    const height = size * 0.45;
    ctx.beginPath();
    ctx.moveTo(-length / 2, height / 2);
    ctx.lineTo(length * 0.1, height / 2);
    ctx.lineTo(-length / 10, -height / 2);
    ctx.lineTo(length / 2, 0);
    ctx.lineTo(-length / 10, height / 2);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.lineWidth = Math.max(1, size * 0.08);
    ctx.strokeStyle = style.iconAccent || style.iconStroke;
    ctx.beginPath();
    ctx.moveTo(-length / 2 + size * 0.12, -height * 0.1);
    ctx.lineTo(-length / 2 + size * 0.12, height * 0.5);
    ctx.stroke();
  }

  function drawMagnetIcon(ctx, size, style) {
    const outerWidth = size * 0.9;
    const outerHeight = size;
    const innerWidth = outerWidth * 0.55;
    const innerHeight = outerHeight * 0.65;
    const legHeight = outerHeight * 0.5;
    ctx.lineWidth = Math.max(1, size * 0.08);
    ctx.strokeStyle = style.iconStroke;
    ctx.fillStyle = style.iconFill;
    ctx.beginPath();
    ctx.moveTo(-outerWidth / 2, -legHeight / 2);
    ctx.lineTo(-outerWidth / 2, legHeight / 2);
    ctx.arc(0, legHeight / 2, outerWidth / 2, Math.PI, 0);
    ctx.lineTo(outerWidth / 2, -legHeight / 2);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.save();
    ctx.globalCompositeOperation = 'destination-out';
    ctx.beginPath();
    ctx.moveTo(-innerWidth / 2, -legHeight / 2);
    ctx.lineTo(-innerWidth / 2, innerHeight / 2);
    ctx.arc(0, innerHeight / 2, innerWidth / 2, Math.PI, 0);
    ctx.lineTo(innerWidth / 2, -legHeight / 2);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
    ctx.fillStyle = style.iconAccent || style.iconFill;
    const tipHeight = size * 0.22;
    ctx.beginPath();
    ctx.rect(-outerWidth / 2, -legHeight / 2, outerWidth / 2, tipHeight);
    ctx.rect(0, -legHeight / 2, outerWidth / 2, tipHeight);
    ctx.fill();
    ctx.stroke();
  }

  function drawSnowflakeIcon(ctx, size, style) {
    const radius = size * 0.42;
    ctx.strokeStyle = style.iconStroke;
    ctx.lineWidth = Math.max(1, size * 0.12);
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(-radius, 0);
    ctx.lineTo(radius, 0);
    ctx.moveTo(0, -radius);
    ctx.lineTo(0, radius);
    const diagonal = radius * Math.cos(Math.PI / 6);
    ctx.moveTo(-diagonal, -diagonal);
    ctx.lineTo(diagonal, diagonal);
    ctx.moveTo(-diagonal, diagonal);
    ctx.lineTo(diagonal, -diagonal);
    ctx.stroke();

    ctx.strokeStyle = style.iconAccent || style.iconStroke;
    ctx.lineWidth = Math.max(1, size * 0.06);
    const branch = radius * 0.32;
    [-1, 1].forEach(sign => {
      ctx.beginPath();
      ctx.moveTo(sign * radius, 0);
      ctx.lineTo(sign * (radius - branch), branch);
      ctx.moveTo(sign * radius, 0);
      ctx.lineTo(sign * (radius - branch), -branch);
      ctx.moveTo(0, sign * radius);
      ctx.lineTo(branch, sign * (radius - branch));
      ctx.moveTo(0, sign * radius);
      ctx.lineTo(-branch, sign * (radius - branch));
      ctx.stroke();
    });
  }

  function drawStarIcon(ctx, size, style) {
    const points = 5;
    const outerRadius = size * 0.48;
    const innerRadius = outerRadius * 0.5;
    ctx.beginPath();
    for (let i = 0; i < points * 2; i += 1) {
      const angle = (Math.PI / points) * i;
      const radius = i % 2 === 0 ? outerRadius : innerRadius;
      ctx.lineTo(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }
    ctx.closePath();
    ctx.fillStyle = style.iconFill;
    ctx.fill();
    ctx.lineWidth = Math.max(1, size * 0.08);
    ctx.strokeStyle = style.iconStroke;
    ctx.stroke();
  }

  function createPowerupIconElement(id, size = 28) {
    const canvas = document.createElement('canvas');
    const scale = window.devicePixelRatio || 1;
    const logicalSize = size;
    const actualSize = Math.ceil(logicalSize * scale);
    canvas.width = actualSize;
    canvas.height = actualSize;
    canvas.style.width = `${logicalSize}px`;
    canvas.style.height = `${logicalSize}px`;
    canvas.className = 'stars-war__life-icon';
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.scale(scale, scale);
      ctx.translate(logicalSize / 2, logicalSize / 2);
      drawPowerupBadge(ctx, POWERUP_VISUALS[id] || POWERUP_VISUALS.default, logicalSize * 0.72, { forHud: true });
    }
    canvas.setAttribute('aria-hidden', 'true');
    return canvas;
  }

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

  function clampLaneIndex(lane, laneCount) {
    if (!Number.isFinite(lane)) {
      return 0;
    }
    return Math.max(0, Math.min(laneCount - 1, Math.floor(lane)));
  }

  function makePatternEntry(type, lane, delay, laneCount, options = {}) {
    return {
      type,
      formation: options.formation || 'LINE',
      path: options.path || 'LINE',
      delay,
      lane: clampLaneIndex(lane, laneCount),
      offset: options.offset ?? 0,
      laneCount,
      props: options.props ? { ...options.props } : undefined
    };
  }

  function makeLaneBand(center, spread, laneCount) {
    const lanes = [];
    for (let delta = -spread; delta <= spread; delta += 1) {
      lanes.push(clampLaneIndex(center + delta, laneCount));
    }
    return Array.from(new Set(lanes));
  }

  function planTopSkirmish({ laneCount, startDelay, difficulty, elapsed }) {
    const entries = [];
    const usableTypes = [ENEMY_TYPES.drone, ENEMY_TYPES.fast];
    if (difficulty >= 3) {
      usableTypes.push(ENEMY_TYPES.gunner);
    }
    const count = Math.max(2, Math.min(laneCount, getRandomInt(2, 5)));
    for (let i = 0; i < count; i += 1) {
      const lane = getRandomInt(0, laneCount);
      const type = pickWeightedEnemy(usableTypes, elapsed);
      entries.push(makePatternEntry(type, lane, startDelay + i * 0.18, laneCount, {
        formation: 'SWARM',
        path: 'FIGURE8',
        offset: (getRandomFloat() - 0.5) * 40,
        props: {
          anchorY: CANVAS_HEIGHT * 0.2,
          figure8RadiusX: 60 + getRandomFloat() * 40,
          figure8RadiusY: 26 + getRandomFloat() * 12
        }
      }));
    }
    const nextDelay = entries.length
      ? entries[entries.length - 1].delay + 0.8
      : startDelay + 0.8;
    return { entries, nextDelay };
  }

  const WAVE_PATTERNS = Object.freeze([
    {
      id: 'scout_serpentine',
      minDifficulty: 1,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const spacing = 0.32;
        for (let i = 0; i < 8; i += 1) {
          const step = Math.ceil(i / 2);
          const direction = i % 2 === 0 ? 1 : -1;
          const lane = clampLaneIndex(center + direction * step, laneCount);
          const type = i % 3 === 0 ? ENEMY_TYPES.drone : ENEMY_TYPES.fast;
          entries.push(makePatternEntry(type, lane, startDelay + i * spacing, laneCount, {
            path: 'SIN',
            formation: 'SWARM',
            offset: direction * 12
          }));
        }
        const nextDelay = entries.length ? entries[entries.length - 1].delay + 1 : startDelay + 1;
        return { entries, nextDelay };
      }
    },
    {
      id: 'drone_screen',
      minDifficulty: 1,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const lanes = makeLaneBand(center, Math.min(2, Math.floor(laneCount / 2)), laneCount);
        lanes.forEach(lane => {
          entries.push(makePatternEntry(ENEMY_TYPES.drone, lane, startDelay, laneCount, {
            formation: 'LINE',
            path: 'LINE'
          }));
        });
        const secondDelay = startDelay + 0.4;
        lanes.forEach((lane, index) => {
          const type = index % 2 === 0 ? ENEMY_TYPES.fast : ENEMY_TYPES.drone;
          entries.push(makePatternEntry(type, lane, secondDelay, laneCount, {
            formation: 'LINE',
            path: 'LINE',
            offset: (lane - center) * 8
          }));
        });
        return { entries, nextDelay: secondDelay + 1 };
      }
    },
    {
      id: 'flanking_darts',
      minDifficulty: 2,
      build({ laneCount, startDelay }) {
        const entries = [];
        const spacing = 0.28;
        for (let i = 0; i < 6; i += 1) {
          const fromLeft = i % 2 === 0;
          const lane = fromLeft ? 0 : laneCount - 1;
          entries.push(makePatternEntry(ENEMY_TYPES.fast, lane, startDelay + i * spacing, laneCount, {
            path: 'SWOOP',
            formation: 'V',
            offset: fromLeft ? -40 : 40
          }));
        }
        const centerLane = clampLaneIndex(Math.floor(laneCount / 2), laneCount);
        entries.push(makePatternEntry(ENEMY_TYPES.gunner, centerLane, startDelay + 6 * spacing + 0.4, laneCount, {
          path: 'LINE',
          formation: 'LINE'
        }));
        entries.push(makePatternEntry(ENEMY_TYPES.gunner, clampLaneIndex(centerLane + 1, laneCount), startDelay + 6 * spacing + 0.7, laneCount, {
          path: 'LINE',
          formation: 'LINE'
        }));
        return { entries, nextDelay: startDelay + 6 * spacing + 1.2 };
      }
    },
    {
      id: 'gunner_phalanx',
      minDifficulty: 2,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const lanes = makeLaneBand(center, Math.min(2, Math.floor((laneCount - 1) / 2)), laneCount);
        lanes.forEach((lane, index) => {
          const delay = startDelay + index * 0.2;
          entries.push(makePatternEntry(ENEMY_TYPES.drone, lane, delay, laneCount, {
            formation: 'LINE',
            path: 'LINE'
          }));
          entries.push(makePatternEntry(ENEMY_TYPES.gunner, lane, delay + 0.25, laneCount, {
            formation: 'LINE',
            path: 'LINE',
            offset: 6
          }));
        });
        const lastDelay = lanes.length ? startDelay + (lanes.length - 1) * 0.2 + 0.6 : startDelay + 0.6;
        return { entries, nextDelay: lastDelay + 0.8 };
      }
    },
    {
      id: 'arc_barrage',
      minDifficulty: 2,
      build({ laneCount, startDelay }) {
        const entries = [];
        const total = Math.min(laneCount + 1, 7);
        for (let i = 0; i < total; i += 1) {
          const lane = clampLaneIndex(i, laneCount);
          const type = i % 2 === 0 ? ENEMY_TYPES.fast : ENEMY_TYPES.drone;
          entries.push(makePatternEntry(type, lane, startDelay + i * 0.22, laneCount, {
            formation: 'ARC',
            path: 'ARC',
            offset: (i - (total - 1) / 2) * 20
          }));
        }
        return { entries, nextDelay: startDelay + total * 0.22 + 0.9 };
      }
    },
    {
      id: 'tank_wall',
      minDifficulty: 3,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const lanes = makeLaneBand(center, Math.min(2, Math.floor((laneCount - 1) / 2)), laneCount);
        lanes.forEach(lane => {
          entries.push(makePatternEntry(ENEMY_TYPES.tank, lane, startDelay, laneCount, {
            formation: 'LINE',
            path: 'LINE',
            offset: (lane - center) * 10
          }));
        });
        const supportDelay = startDelay + 0.45;
        lanes.forEach((lane, index) => {
          const type = index % 2 === 0 ? ENEMY_TYPES.gunner : ENEMY_TYPES.fast;
          entries.push(makePatternEntry(type, lane, supportDelay, laneCount, {
            formation: 'LINE',
            path: 'LINE'
          }));
        });
        return { entries, nextDelay: supportDelay + 1.1 };
      }
    },
    {
      id: 'figure_eight_flyby',
      minDifficulty: 3,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const lanes = makeLaneBand(center, Math.min(2, Math.floor((laneCount - 1) / 2)), laneCount);
        lanes.forEach((lane, index) => {
          entries.push(makePatternEntry(ENEMY_TYPES.drone, lane, startDelay + index * 0.18, laneCount, {
            formation: 'LINE',
            path: 'FIGURE8',
            offset: (lane - center) * 14,
            props: {
              anchorY: CANVAS_HEIGHT * 0.18,
              figure8RadiusX: 60 + index * 10,
              figure8RadiusY: 24 + index * 4
            }
          }));
        });
        const tailDelay = startDelay + lanes.length * 0.18 + 0.25;
        entries.push(makePatternEntry(ENEMY_TYPES.fast, clampLaneIndex(center, laneCount), tailDelay, laneCount, {
          formation: 'SWARM',
          path: 'FIGURE8',
          props: {
            anchorY: CANVAS_HEIGHT * 0.18,
            figure8RadiusX: 80,
            figure8RadiusY: 30
          }
        }));
        return { entries, nextDelay: tailDelay + 1.1 };
      }
    },
    {
      id: 'kamikaze_rush',
      minDifficulty: 4,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const spacing = 0.22;
        for (let i = 0; i < 8; i += 1) {
          const step = Math.ceil(i / 2);
          const direction = i % 2 === 0 ? 1 : -1;
          const lane = clampLaneIndex(center + direction * step, laneCount);
          entries.push(makePatternEntry(ENEMY_TYPES.kamikaze, lane, startDelay + i * spacing, laneCount, {
            formation: 'SWARM',
            path: 'SWOOP',
            offset: direction * 24
          }));
        }
        const anchorDelay = startDelay + 8 * spacing + 0.35;
        entries.push(makePatternEntry(ENEMY_TYPES.gunner, center, anchorDelay, laneCount, {
          formation: 'LINE',
          path: 'LINE'
        }));
        return { entries, nextDelay: anchorDelay + 1 };
      }
    },
    {
      id: 'tank_sniper_column',
      minDifficulty: 5,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const lanes = makeLaneBand(center, Math.min(2, Math.floor((laneCount - 1) / 2)), laneCount);
        const spacing = 0.28;
        lanes.forEach((lane, index) => {
          entries.push(makePatternEntry(ENEMY_TYPES.tank, lane, startDelay + index * spacing, laneCount, {
            formation: 'LINE',
            path: 'LINE',
            offset: (lane - center) * 12
          }));
        });
        const sniperDelay = startDelay + lanes.length * spacing + 0.35;
        lanes.forEach((lane, index) => {
          entries.push(makePatternEntry(ENEMY_TYPES.sniper, lane, sniperDelay + index * 0.18, laneCount, {
            formation: 'LINE',
            path: 'LINE',
            offset: (lane - center) * 12
          }));
        });
        return { entries, nextDelay: sniperDelay + lanes.length * 0.18 + 1.1 };
      }
    },
    {
      id: 'twin_commanders',
      minDifficulty: 6,
      build({ laneCount, startDelay }) {
        const entries = [];
        const center = Math.floor(laneCount / 2);
        const leftLane = clampLaneIndex(center - 2, laneCount);
        const rightLane = clampLaneIndex(center + 2, laneCount);
        const bossProps = phase => ({
          anchorY: CANVAS_HEIGHT * PLAYER_AREA_TOP_RATIO,
          swayAmplitude: CANVAS_WIDTH * 0.28,
          swayFrequency: 0.65,
          swayPhase: phase
        });
        entries.push(makePatternEntry(ENEMY_TYPES.miniboss, leftLane, startDelay, laneCount, {
          formation: 'COLUMN',
          path: 'BOSS_SWAY',
          props: bossProps(0)
        }));
        entries.push(makePatternEntry(ENEMY_TYPES.miniboss, rightLane, startDelay, laneCount, {
          formation: 'COLUMN',
          path: 'BOSS_SWAY',
          props: bossProps(Math.PI)
        }));
        const escortDelay = startDelay + 0.6;
        entries.push(makePatternEntry(ENEMY_TYPES.fast, clampLaneIndex(center - 3, laneCount), escortDelay, laneCount, {
          formation: 'V',
          path: 'SIN',
          offset: -26
        }));
        entries.push(makePatternEntry(ENEMY_TYPES.fast, clampLaneIndex(center + 3, laneCount), escortDelay, laneCount, {
          formation: 'V',
          path: 'SIN',
          offset: 26
        }));
        const gunnerDelay = escortDelay + 0.45;
        entries.push(makePatternEntry(ENEMY_TYPES.gunner, clampLaneIndex(center - 1, laneCount), gunnerDelay, laneCount, {
          formation: 'LINE',
          path: 'LINE'
        }));
        entries.push(makePatternEntry(ENEMY_TYPES.gunner, clampLaneIndex(center + 1, laneCount), gunnerDelay, laneCount, {
          formation: 'LINE',
          path: 'LINE'
        }));
        return { entries, nextDelay: gunnerDelay + 1.4 };
      }
    }
  ]);
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

  const SPRITE_SHEET_PATH = 'Assets/sprites/StarsWar.png';
  const SPRITE_DEFINITIONS = Object.freeze({
    drone: { sx: 0, sy: 512, sw: 256, sh: 256, size: 40 },
    tank: { sx: 256, sy: 512, sw: 256, sh: 256, size: 48 },
    carrier: { sx: 512, sy: 512, sw: 256, sh: 256, size: 40 },
    kamikaze: { sx: 768, sy: 512, sw: 256, sh: 256, size: 40 },
    gunner: { sx: 0, sy: 768, sw: 256, sh: 256, size: 40 },
    sniper: { sx: 256, sy: 768, sw: 256, sh: 256, size: 40 },
    fast: { sx: 512, sy: 768, sw: 256, sh: 256, size: 40 },
    player: { sx: 768, sy: 768, sw: 256, sh: 256, size: 32, rotate180: true },
    miniboss: { sx: 512, sy: 0, sw: 512, sh: 512, size: 60 }
  });

  function createSpriteCanvas(width, height) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    return canvas;
  }

  function createFleetSprites() {
    const sprites = {};
    Object.entries(SPRITE_DEFINITIONS).forEach(([key, def]) => {
      const size = Math.max(1, Math.round(def.size));
      sprites[key] = createSpriteCanvas(size, size);
    });

    const image = new Image();
    image.src = SPRITE_SHEET_PATH;
    image.addEventListener('load', () => {
      Object.entries(SPRITE_DEFINITIONS).forEach(([key, def]) => {
        const canvas = sprites[key];
        if (!canvas) {
          return;
        }
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.save();
        if (def.rotate180) {
          ctx.translate(canvas.width / 2, canvas.height / 2);
          ctx.rotate(Math.PI);
          ctx.drawImage(
            image,
            def.sx,
            def.sy,
            def.sw,
            def.sh,
            -canvas.width / 2,
            -canvas.height / 2,
            canvas.width,
            canvas.height
          );
        } else {
          ctx.drawImage(
            image,
            def.sx,
            def.sy,
            def.sw,
            def.sh,
            0,
            0,
            canvas.width,
            canvas.height
          );
        }
        ctx.restore();
      });
    });

    return sprites;
  }

  const fleet = createFleetSprites();

  function clamp(value, min, max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  const lerp = (a, b, t) => a + (b - a) * t;

  function easeInOutQuad(t) {
    if (t <= 0) return 0;
    if (t >= 1) return 1;
    return t < 0.5
      ? 2 * t * t
      : 1 - Math.pow(-2 * t + 2, 2) / 2;
  }

  function cubicBezier(p0, p1, p2, p3, t) {
    const u = 1 - t;
    const tt = t * t;
    const uu = u * u;
    const uuu = uu * u;
    const ttt = tt * t;
    const x = uuu * p0[0]
      + 3 * uu * t * p1[0]
      + 3 * u * tt * p2[0]
      + ttt * p3[0];
    const y = uuu * p0[1]
      + 3 * uu * t * p1[1]
      + 3 * u * tt * p2[1]
      + ttt * p3[1];
    return { x, y };
  }

  function degToRad(degrees) {
    return degrees * Math.PI / 180;
  }

  const PATTERN_IMPLEMENTATIONS = Object.freeze({
    spiral(params, t) {
      const {
        centerX = CANVAS_WIDTH / 2,
        centerY = CANVAS_HEIGHT / 3,
        radiusStart = 0,
        radiusGrow = 0,
        angularSpeed = 1,
        phase = 0
      } = params;
      const radius = radiusStart + radiusGrow * t;
      const angle = angularSpeed * t + phase;
      const x = centerX + radius * Math.cos(angle);
      const y = centerY + radius * Math.sin(angle);
      const rotation = angle + Math.PI / 2;
      return { x, y, rotation };
    },

    sine(params, t) {
      const {
        startX = CANVAS_WIDTH / 2,
        startY = -40,
        amplitude = 80,
        freq = 1,
        speedY = 100
      } = params;
      const angular = 2 * Math.PI * freq;
      const y = startY + speedY * t;
      const x = startX + amplitude * Math.sin(angular * t);
      const slope = angular * amplitude * Math.cos(angular * t);
      const rotation = Math.atan2(speedY, slope);
      return { x, y, rotation };
    },

    bezier(params, t) {
      const {
        p0,
        p1,
        p2,
        p3,
        duration = 2,
        easing = 'easeInOutQuad'
      } = params;
      if (!p0 || !p1 || !p2 || !p3) {
        return { x: 0, y: 0, rotation: 0 };
      }
      const tt = clamp(t / duration, 0, 1);
      const eased = easing === 'easeInOutQuad' ? easeInOutQuad(tt) : tt;
      const pose = cubicBezier(p0, p1, p2, p3, eased);
      const epsilon = 0.001;
      const nextT = clamp(tt + epsilon, 0, 1);
      const poseB = cubicBezier(p0, p1, p2, p3, nextT);
      const rotation = Math.atan2(poseB.y - pose.y, poseB.x - pose.x);
      return { x: pose.x, y: pose.y, rotation };
    },

    waypoints(params, t) {
      const { points, segmentSpeed = 150, easing = 'linear' } = params;
      if (!Array.isArray(points) || points.length < 2) {
        const [fallbackX = 0, fallbackY = 0] = points?.[0] || [];
        return { x: fallbackX, y: fallbackY, rotation: 0 };
      }

      let totalLength = 0;
      const lengths = [];
      for (let i = 0; i < points.length - 1; i += 1) {
        const [x1, y1] = points[i];
        const [x2, y2] = points[i + 1];
        const length = Math.hypot(x2 - x1, y2 - y1);
        lengths.push(length);
        totalLength += length;
      }

      if (totalLength <= 0) {
        const [x = 0, y = 0] = points[0];
        return { x, y, rotation: 0 };
      }

      const travelled = clamp(segmentSpeed * t, 0, totalLength);
      let segmentIndex = 0;
      let accumulated = 0;
      while (segmentIndex < lengths.length && accumulated + lengths[segmentIndex] < travelled) {
        accumulated += lengths[segmentIndex];
        segmentIndex += 1;
      }

      const segmentLength = lengths[segmentIndex] || 1;
      const rawK = segmentLength > 0
        ? (travelled - accumulated) / segmentLength
        : 0;
      const easingFn = easing === 'easeOutQuad'
        ? value => 1 - Math.pow(1 - value, 2)
        : easing === 'easeInOutQuad'
          ? easeInOutQuad
          : value => value;
      const k = easingFn(clamp(rawK, 0, 1));
      const [sx, sy] = points[segmentIndex];
      const [ex, ey] = points[segmentIndex + 1] || points[segmentIndex];
      const x = lerp(sx, ex, k);
      const y = lerp(sy, ey, k);
      const rotation = Math.atan2(ey - sy, ex - sx);
      return { x, y, rotation };
    },

    homing(params, t, state, previousPose) {
      const { speed = 200, maxTurnDegPerSec = 180 } = params;
      const { playerX = CANVAS_WIDTH / 2, playerY = CANVAS_HEIGHT } = state || {};
      const dt = state?.dt || 0.016;
      const from = previousPose || { x: 0, y: -60, rotation: Math.PI / 2 };
      const desired = Math.atan2(playerY - from.y, playerX - from.x);
      const maxTurn = degToRad(maxTurnDegPerSec) * dt;
      let delta = ((desired - from.rotation + Math.PI * 3) % (Math.PI * 2)) - Math.PI;
      if (delta > maxTurn) delta = maxTurn;
      if (delta < -maxTurn) delta = -maxTurn;
      const rotation = from.rotation + delta;
      const velocity = speed * dt;
      const x = from.x + Math.cos(rotation) * velocity;
      const y = from.y + Math.sin(rotation) * velocity;
      return { x, y, rotation };
    },

    idle(params, t) {
      const { x = CANVAS_WIDTH / 2, y = CANVAS_HEIGHT / 3, jitter = 0, jitterFreq = 1 } = params;
      if (!jitter) {
        return { x, y, rotation: 0 };
      }
      const oscillation = 2 * Math.PI * jitterFreq * t;
      return {
        x: x + Math.sin(oscillation) * jitter,
        y: y + Math.cos(oscillation) * jitter,
        rotation: 0
      };
    }
  });

  function positionFromPattern(patternDef, t, state = {}, previousPose = null) {
    if (!patternDef || !patternDef.type) {
      return { x: 0, y: 0, rotation: 0 };
    }
    const handler = PATTERN_IMPLEMENTATIONS[patternDef.type];
    if (!handler) {
      throw new Error(`Unknown pattern type: ${patternDef.type}`);
    }
    return handler(patternDef.params || {}, t, state, previousPose || undefined);
  }

  class PatternController {
    constructor(primaryDef, options = {}) {
      this.primary = primaryDef;
      this.secondary = options.thenDef || null;
      this.switchAt = Number.isFinite(options.thenAt) ? Math.max(0, options.thenAt) : null;
      this.formationOffset = Array.isArray(options.formationOffset) ? options.formationOffset.slice(0, 2) : [0, 0];
      this.activeDef = this.primary;
      this.patternTime = 0;
      this.previousPose = null;
      this.lastPose = null;
      this.totalTime = 0;
    }

    advance(delta, runtimeState) {
      const dt = Math.max(0, delta);
      this.totalTime += dt;
      this.patternTime += dt;
      if (this.secondary && this.activeDef === this.primary && this.switchAt != null && this.patternTime >= this.switchAt) {
        const overflow = this.patternTime - this.switchAt;
        this.activeDef = this.secondary;
        this.patternTime = overflow;
        this.previousPose = this.lastPose;
      }
      const pose = positionFromPattern(this.activeDef, this.patternTime, runtimeState, this.previousPose);
      this.previousPose = pose;
      this.lastPose = pose;
      return this.withOffset(pose);
    }

    snapshot(runtimeState) {
      const pose = positionFromPattern(this.activeDef, this.patternTime, runtimeState, this.previousPose);
      this.previousPose = pose;
      this.lastPose = pose;
      return this.withOffset(pose);
    }

    withOffset(pose) {
      return {
        x: pose.x + this.formationOffset[0],
        y: pose.y + this.formationOffset[1],
        rotation: pose.rotation
      };
    }
  }

  const SCRIPTED_PATTERN_LIBRARY = Object.freeze({
    patterns: Object.freeze([
      Object.freeze({
        name: 'entrance_spiral',
        type: 'spiral',
        params: Object.freeze({
          centerX: CANVAS_WIDTH / 2,
          centerY: CANVAS_HEIGHT * 0.22,
          radiusStart: -50,
          radiusGrow: 30,
          angularSpeed: 2.2,
          phase: 0
        })
      }),
      Object.freeze({
        name: 'sine_sweep',
        type: 'sine',
        params: Object.freeze({
          startX: CANVAS_WIDTH / 2,
          startY: -40,
          amplitude: 90,
          freq: 1.1,
          speedY: 110
        })
      }),
      Object.freeze({
        name: 'bezier_entry',
        type: 'bezier',
        params: Object.freeze({
          p0: [-40, 60],
          p1: [80, -20],
          p2: [280, 140],
          p3: [CANVAS_WIDTH / 2, CANVAS_HEIGHT * 0.24],
          duration: 3,
          easing: 'easeInOutQuad'
        })
      }),
      Object.freeze({
        name: 'waypoints_to_formation',
        type: 'waypoints',
        params: Object.freeze({
          points: Object.freeze([
            [CANVAS_WIDTH - 180, -40],
            [CANVAS_WIDTH - 200, 40],
            [CANVAS_WIDTH - 220, 120],
            [CANVAS_WIDTH - 240, CANVAS_HEIGHT * 0.25]
          ]),
          segmentSpeed: 180,
          easing: 'easeOutQuad'
        })
      }),
      Object.freeze({
        name: 'dive_at_player',
        type: 'homing',
        params: Object.freeze({
          speed: 240,
          maxTurnDegPerSec: 260
        })
      }),
      Object.freeze({
        name: 'idle_formation',
        type: 'idle',
        params: Object.freeze({
          x: CANVAS_WIDTH / 2,
          y: CANVAS_HEIGHT * 0.22,
          jitter: 4,
          jitterFreq: 0.8
        })
      })
    ]),
    waves: Object.freeze([
      Object.freeze({
        name: 'Wave_1_SpiralToFormation',
        enemies: Object.freeze([
          Object.freeze({ pattern: 'entrance_spiral', timeOffset: 0, then: 'idle_formation', thenAt: 2.4, formationOffset: [-60, -40], enemyType: 'drone' }),
          Object.freeze({ pattern: 'entrance_spiral', timeOffset: 0.2, then: 'idle_formation', thenAt: 2.6, formationOffset: [-20, -40], enemyType: 'drone' }),
          Object.freeze({ pattern: 'entrance_spiral', timeOffset: 0.4, then: 'idle_formation', thenAt: 2.8, formationOffset: [20, -40], enemyType: 'fast' }),
          Object.freeze({ pattern: 'entrance_spiral', timeOffset: 0.6, then: 'idle_formation', thenAt: 3, formationOffset: [60, -40], enemyType: 'fast' })
        ])
      }),
      Object.freeze({
        name: 'Wave_2_SineThenDive',
        enemies: Object.freeze([
          Object.freeze({ pattern: 'sine_sweep', timeOffset: 0, then: 'dive_at_player', thenAt: 2.2, enemyType: 'fast' }),
          Object.freeze({ pattern: 'sine_sweep', timeOffset: 0.25, then: 'dive_at_player', thenAt: 2.4, enemyType: 'fast' }),
          Object.freeze({ pattern: 'sine_sweep', timeOffset: 0.5, then: 'dive_at_player', thenAt: 2.6, enemyType: 'kamikaze' })
        ])
      }),
      Object.freeze({
        name: 'Wave_3_BezierEscort',
        enemies: Object.freeze([
          Object.freeze({ pattern: 'bezier_entry', timeOffset: 0, then: 'idle_formation', thenAt: 3.2, formationOffset: [-80, -32], enemyType: 'gunner' }),
          Object.freeze({ pattern: 'bezier_entry', timeOffset: 0.4, then: 'idle_formation', thenAt: 3.4, formationOffset: [0, -24], enemyType: 'drone' }),
          Object.freeze({ pattern: 'bezier_entry', timeOffset: 0.8, then: 'idle_formation', thenAt: 3.6, formationOffset: [80, -32], enemyType: 'gunner' })
        ])
      }),
      Object.freeze({
        name: 'Wave_4_WaypointScreen',
        enemies: Object.freeze([
          Object.freeze({ pattern: 'waypoints_to_formation', timeOffset: 0, then: 'idle_formation', thenAt: 2.6, formationOffset: [-100, -36], enemyType: 'tank' }),
          Object.freeze({ pattern: 'waypoints_to_formation', timeOffset: 0.3, then: 'idle_formation', thenAt: 2.8, formationOffset: [-40, -28], enemyType: 'gunner' }),
          Object.freeze({ pattern: 'waypoints_to_formation', timeOffset: 0.6, then: 'idle_formation', thenAt: 3, formationOffset: [20, -28], enemyType: 'tank' }),
          Object.freeze({ pattern: 'waypoints_to_formation', timeOffset: 0.9, then: 'idle_formation', thenAt: 3.2, formationOffset: [80, -36], enemyType: 'gunner' })
        ])
      })
    ])
  });

  const SCRIPTED_PATTERN_INDEX = new Map();
  const SCRIPTED_WAVE_INDEX = new Map();
  SCRIPTED_PATTERN_LIBRARY.patterns.forEach(pattern => {
    SCRIPTED_PATTERN_INDEX.set(pattern.name, pattern);
  });
  SCRIPTED_PATTERN_LIBRARY.waves.forEach(wave => {
    SCRIPTED_WAVE_INDEX.set(wave.name, wave);
  });

  const SCRIPTED_WAVE_SEQUENCE = Object.freeze([
    'Wave_1_SpiralToFormation',
    'Wave_2_SineThenDive',
    'Wave_3_BezierEscort',
    'Wave_4_WaypointScreen'
  ]);

  function clonePatternParams(params = {}) {
    const clone = {};
    Object.keys(params).forEach(key => {
      const value = params[key];
      if (Array.isArray(value)) {
        clone[key] = value.map(item => (Array.isArray(item) ? item.slice() : item));
      } else if (value && typeof value === 'object') {
        clone[key] = { ...value };
      } else {
        clone[key] = value;
      }
    });
    return clone;
  }

  function clonePatternDef(definition) {
    if (!definition) {
      return null;
    }
    return {
      type: definition.type,
      params: clonePatternParams(definition.params)
    };
  }

  function randomizePatternDef(definition, difficulty = 1) {
    const clone = clonePatternDef(definition);
    if (!clone) {
      return null;
    }
    const params = clone.params || {};
    switch (clone.type) {
      case 'spiral': {
        const baseCenterX = params.centerX != null ? params.centerX : CANVAS_WIDTH / 2;
        const baseCenterY = params.centerY != null ? params.centerY : CANVAS_HEIGHT * 0.22;
        const baseGrow = params.radiusGrow != null ? params.radiusGrow : 30;
        const baseStart = params.radiusStart != null ? params.radiusStart : -60;
        const baseAngular = params.angularSpeed != null ? params.angularSpeed : 2.2;
        params.centerX = clamp(baseCenterX + randomRange(-80, 80), 60, CANVAS_WIDTH - 60);
        params.centerY = clamp(baseCenterY + randomRange(-40, 40), 40, CANVAS_HEIGHT * 0.35);
        params.radiusGrow = clamp(baseGrow * randomRange(0.85, 1.2), 18, 52);
        params.radiusStart = clamp(baseStart + randomRange(-18, 18), -90, -20);
        params.angularSpeed = clamp(baseAngular * randomRange(0.85, 1.15), 1.6, 2.8);
        params.phase = randomRange(0, Math.PI * 2);
        break;
      }
      case 'sine': {
        const baseStartX = params.startX != null ? params.startX : CANVAS_WIDTH / 2;
        const baseAmplitude = params.amplitude != null ? params.amplitude : 80;
        const baseFreq = params.freq != null ? params.freq : 1.1;
        const baseSpeedY = params.speedY != null ? params.speedY : 110;
        params.startX = clamp(baseStartX + randomRange(-140, 140), 40, CANVAS_WIDTH - 40);
        params.startY = -60 + randomRange(-20, 20);
        params.amplitude = clamp(baseAmplitude * randomRange(0.85, 1.2), 50, 130);
        params.freq = clamp(baseFreq * randomRange(0.85, 1.2), 0.8, 1.5);
        params.speedY = clamp(baseSpeedY * randomRange(0.85, 1.2), 80, 160);
        break;
      }
      case 'bezier': {
        const adjustPoint = (point, spreadX, spreadY) => {
          if (!Array.isArray(point)) {
            return [CANVAS_WIDTH / 2, CANVAS_HEIGHT * 0.25];
          }
          const [px, py] = point;
          return [
            clamp(px + randomRange(-spreadX, spreadX), -80, CANVAS_WIDTH + 80),
            clamp(py + randomRange(-spreadY, spreadY), -80, CANVAS_HEIGHT * 0.5)
          ];
        };
        params.p0 = adjustPoint(params.p0, 40, 30);
        params.p1 = adjustPoint(params.p1, 80, 40);
        params.p2 = adjustPoint(params.p2, 80, 50);
        params.p3 = adjustPoint(params.p3, 40, 30);
        params.duration = clamp((params.duration || 3) * randomRange(0.9, 1.15), 2.2, 3.8);
        break;
      }
      case 'waypoints': {
        if (Array.isArray(params.points)) {
          params.points = params.points.map(point => {
            if (!Array.isArray(point)) {
              return [CANVAS_WIDTH / 2, CANVAS_HEIGHT * 0.25];
            }
            const [px, py] = point;
            return [
              clamp(px + randomRange(-60, 60), -120, CANVAS_WIDTH + 120),
              clamp(py + randomRange(-50, 50), -100, CANVAS_HEIGHT * 0.55)
            ];
          });
        }
        params.segmentSpeed = clamp((params.segmentSpeed || 180) * randomRange(0.85, 1.15), 140, 240);
        break;
      }
      case 'homing': {
        params.speed = clamp((params.speed || 220) * randomRange(0.9, 1.1), 170, 280);
        params.maxTurnDegPerSec = clamp((params.maxTurnDegPerSec || 240) * randomRange(0.85, 1.2), 180, 320);
        break;
      }
      case 'idle': {
        const baseX = params.x != null ? params.x : CANVAS_WIDTH / 2;
        const baseY = params.y != null ? params.y : CANVAS_HEIGHT * 0.24;
        const baseJitter = params.jitter != null ? params.jitter : 0;
        const baseFreq = params.jitterFreq != null ? params.jitterFreq : 1;
        params.x = clamp(baseX + randomRange(-60, 60), 80, CANVAS_WIDTH - 80);
        params.y = clamp(baseY + randomRange(-36, 36), 60, CANVAS_HEIGHT * 0.4);
        params.jitter = Math.max(0, baseJitter * randomRange(0.85, 1.3) + randomRange(-1.5, 1.5));
        params.jitterFreq = clamp(baseFreq * randomRange(0.85, 1.2), 0.5, 1.4);
        break;
      }
      default:
        break;
    }
    clone.params = params;
    return clone;
  }

  function estimatePatternFollowTime(patternDef) {
    if (!patternDef) {
      return 2.4;
    }
    switch (patternDef.type) {
      case 'spiral':
        return randomRange(2.2, 3.2);
      case 'sine':
        return randomRange(2, 2.8);
      case 'bezier':
        return (patternDef.params?.duration || 3) + randomRange(0.2, 0.6);
      case 'waypoints':
        return randomRange(2.2, 3.1);
      default:
        return randomRange(2, 3);
    }
  }

  function createDynamicFollowUp(primaryDef, difficulty = 1) {
    if (!primaryDef) {
      return null;
    }
    const roll = getRandomFloat();
    const preferDive = difficulty >= 2 && roll > 0.55;
    const baseFollowTime = estimatePatternFollowTime(primaryDef);
    if (preferDive) {
      const diveBase = SCRIPTED_PATTERN_INDEX.get('dive_at_player');
      if (!diveBase) {
        return null;
      }
      const dive = randomizePatternDef(diveBase, difficulty);
      return {
        def: dive,
        at: Math.max(1.4, baseFollowTime - 0.3 + randomRange(0, 0.6))
      };
    }
    const idleBase = SCRIPTED_PATTERN_INDEX.get('idle_formation');
    if (!idleBase) {
      return null;
    }
    const idle = randomizePatternDef(idleBase, difficulty);
    return {
      def: idle,
      at: Math.max(1.8, baseFollowTime + randomRange(-0.3, 0.4))
    };
  }

  function resolveScriptedEnemyType(id) {
    if (!id) {
      return ENEMY_TYPES.drone;
    }
    return ENEMY_TYPES[id] || ENEMY_TYPES.drone;
  }

  function buildScriptedWavePlan(name, options = {}) {
    const waveDef = SCRIPTED_WAVE_INDEX.get(name);
    if (!waveDef) {
      return [];
    }
    const startDelay = options.startDelay || 0;
    const entries = [];
    waveDef.enemies.forEach(entry => {
      const patternDef = clonePatternDef(SCRIPTED_PATTERN_INDEX.get(entry.pattern));
      if (!patternDef) {
        return;
      }
      const thenPattern = entry.then ? clonePatternDef(SCRIPTED_PATTERN_INDEX.get(entry.then)) : null;
      entries.push({
        scripted: true,
        delay: startDelay + (entry.timeOffset || 0),
        pattern: patternDef,
        thenDef: thenPattern,
        thenAt: entry.thenAt,
        formationOffset: Array.isArray(entry.formationOffset) ? entry.formationOffset.slice(0, 2) : [0, 0],
        enemyType: resolveScriptedEnemyType(entry.enemyType)
      });
    });
    entries.sort((a, b) => a.delay - b.delay);
    return entries;
  }

  function formatTime(seconds) {
    const total = Math.max(0, Math.floor(seconds));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  function formatNumber(value) {
    ensureNumberFormatter(numberFormatterLocale);
    const numeric = Math.floor(Number(value) || 0);
    if (numberFormatterInstance) {
      try {
        return numberFormatterInstance.format(numeric);
      } catch (error) {
        // If formatting fails, fall back to default string conversion below.
      }
    }
    return numeric.toString();
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
    pointerMode: 'absolute',
    pointerX: CANVAS_WIDTH / 2,
    pointerY: CANVAS_HEIGHT * 0.8,
    pointerOriginX: CANVAS_WIDTH / 2,
    pointerOriginY: CANVAS_HEIGHT * 0.8
  };

  const state = {
    rngSeed: '',
    rng: rngMulberry32(1),
    running: false,
    paused: false,
    lastTimestamp: 0,
    accumulator: 0,
    uiAccumulator: 0,
    uiNeedsSync: true,
    elapsed: 0,
    difficulty: 1,
    difficultyMode: DIFFICULTY_MODES.HARD,
    wave: 0,
    spawnQueue: [],
    spawnTimer: 0,
    spawnInterval: BASE_SPAWN_INTERVAL,
    waveTimer: Math.min(INITIAL_WAVE_INTERVAL, DEFAULT_CONFIG.maxWaveDurationSeconds),
    waveElapsed: 0,
    maxOnScreen: 10 + Math.floor(3 * 1),
    pendingWaveAdvance: false,
    enemies: [],
    enemyBullets: [],
    activeEnemyBulletCount: 0,
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
    lastWaveDifficultyBoost: 0,
    records: createInitialRecordState(),
    lastSeed: '',
    config: DEFAULT_CONFIG,
    scriptedSequence: SCRIPTED_WAVE_SEQUENCE.slice()
  };

  const uiState = {
    language: (resolveLanguageCode && (resolveLanguageCode() || '')) || '',
    scoreText: '',
    timeText: '',
    waveText: '',
    difficultyText: '',
    livesKey: '',
    powerupOrder: [],
    powerupEntries: new Map(),
    translationCache: new Map()
  };

  ensureNumberFormatter(uiState.language);

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

  function createPatternRuntimeState(dt = 0) {
    return {
      dt,
      playerX: player.x,
      playerY: player.y,
      screenW: CANVAS_WIDTH,
      screenH: CANVAS_HEIGHT
    };
  }

  function getShipHitboxScale(type) {
    const fallbackConfig = DEFAULT_CONFIG.shipHitboxScale || {};
    const activeConfig = state.config && state.config.shipHitboxScale
      ? state.config.shipHitboxScale
      : fallbackConfig;
    const fallback = type === 'enemy'
      ? fallbackConfig.enemy
      : fallbackConfig.player;
    const rawScale = activeConfig && typeof activeConfig === 'object' ? activeConfig[type] : undefined;
    const scale = clampNumber(rawScale, 0.1, 2, fallback != null ? fallback : 0.5);
    if (state.difficultyMode === DIFFICULTY_MODES.EASY && type === 'enemy') {
      return clampNumber(EASY_ENEMY_HITBOX_SCALE, 0.1, 2, scale);
    }
    return scale;
  }

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
    shield: 'index.sections.starsWar.powerups.shield',
    heart: 'index.sections.starsWar.powerups.heart'
  });

  const LOCAL_FALLBACK_TRANSLATIONS = Object.freeze({
    'index.sections.starsWar.powerups.multi': { en: 'Side cannons', fr: 'Tir latéral' },
    'index.sections.starsWar.powerups.rapid': { en: 'Rapid fire', fr: 'Cadence boostée' },
    'index.sections.starsWar.powerups.magnet': { en: 'Magnet', fr: 'Aimant' },
    'index.sections.starsWar.powerups.slow': { en: 'Enemy slow', fr: 'Ralentissement ennemi' },
    'index.sections.starsWar.powerups.shield': { en: 'Shield', fr: 'Bouclier' },
    'index.sections.starsWar.powerups.heart': { en: 'Extra life', fr: 'Cœur supplémentaire' },
    'index.sections.starsWar.controls.pause': { en: 'Pause', fr: 'Pause' },
    'index.sections.starsWar.controls.resume': { en: 'Resume', fr: 'Reprendre' },
    'index.sections.starsWar.controls.difficulty.easy': { en: 'Easy', fr: 'Easy' },
    'index.sections.starsWar.controls.difficulty.hard': { en: 'Hard', fr: 'Hard' },
    'index.sections.starsWar.status.powerup': { en: 'Power-up equipped: {powerup}', fr: 'Bonus activé : {powerup}' },
    'index.sections.starsWar.status.heart': { en: 'Heart recovered!', fr: 'Cœur récupéré !' },
    'index.sections.starsWar.status.shield': { en: 'Shield absorbed the hit.', fr: 'Bouclier absorbé.' },
    'index.sections.starsWar.status.damage': { en: 'Hit! Hull at {hp} HP.', fr: 'Touché ! PV restants : {hp}' },
    'index.sections.starsWar.status.paused': { en: 'Mission paused.', fr: 'Mission en pause.' },
    'index.sections.starsWar.status.resumed': { en: 'Mission resumed.', fr: 'Mission reprise.' },
    'index.sections.starsWar.overlay.gameOver.title': { en: 'Mission failed', fr: 'Mission échouée' },
    'index.sections.starsWar.overlay.gameOver.message': { en: 'Time: {time} · Score: {score}', fr: 'Durée : {time} · Score : {score}' },
    'index.sections.starsWar.overlay.gameOver.reward': { en: 'Reward: +{count} gacha ticket{suffix}', fr: 'Récompense : +{count} ticket{suffix} gacha' },
    'index.sections.starsWar.overlay.retry': { en: 'Retry', fr: 'Rejouer' },
    'index.sections.starsWar.overlay.pause.title': { en: 'Paused', fr: 'Pause' },
    'index.sections.starsWar.overlay.pause.message': {
      en: 'Game paused. Tap resume to continue the mission.',
      fr: 'Partie en pause. Appuyez sur Reprendre pour continuer la mission.'
    },
    'index.sections.starsWar.overlay.pause.action': { en: 'Resume mission', fr: 'Reprendre la mission' }
  });

  function resolveLanguageCode() {
    if (typeof document !== 'undefined' && document.documentElement && typeof document.documentElement.lang === 'string') {
      const lang = document.documentElement.lang.trim();
      if (lang) {
        return lang.toLowerCase().split('-')[0];
      }
    }
    if (typeof navigator !== 'undefined' && typeof navigator.language === 'string') {
      const lang = navigator.language.trim();
      if (lang) {
        return lang.toLowerCase().split('-')[0];
      }
    }
    return 'en';
  }

  function formatTemplateValue(value) {
    if (value == null) {
      return '';
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value.toString();
    }
    return String(value);
  }

  function applyTemplateParams(template, params) {
    if (typeof template !== 'string' || !params || typeof params !== 'object') {
      return template;
    }
    return template.replace(/\{([^}]+)\}/g, (match, key) => {
      if (!Object.prototype.hasOwnProperty.call(params, key)) {
        return match;
      }
      return formatTemplateValue(params[key]);
    });
  }

  function getFallbackTranslation(key, params) {
    const entry = LOCAL_FALLBACK_TRANSLATIONS[key];
    if (!entry) {
      return null;
    }
    const locale = resolveLanguageCode();
    const normalized = locale ? locale.toLowerCase() : 'en';
    const text = entry[normalized] || entry[normalized.slice(0, 2)] || entry.en || entry.fr;
    if (!text) {
      return null;
    }
    return applyTemplateParams(text, params);
  }

  function resolveTranslator() {
    if (typeof window !== 'undefined') {
      if (window.i18n && typeof window.i18n.t === 'function') {
        return window.i18n.t.bind(window.i18n);
      }
      if (typeof window.t === 'function') {
        return window.t.bind(window);
      }
    }
    if (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function') {
      return globalThis.t.bind(globalThis);
    }
    return null;
  }

  function translate(key, params) {
    if (typeof key !== 'string' || !key) {
      return '';
    }
    const translator = resolveTranslator();
    if (translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string' && result.trim() && result !== key) {
          return result;
        }
      } catch (error) {
        // Ignore translation runtime errors and fall back to local values
      }
    }
    const fallback = getFallbackTranslation(key, params);
    if (typeof fallback === 'string' && fallback.trim()) {
      return fallback;
    }
    return key;
  }

  function safeClone(value) {
    if (value == null) {
      return null;
    }
    try {
      return JSON.parse(JSON.stringify(value));
    } catch (error) {
      return null;
    }
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

  function readArcadeEntryFromGameState() {
    const gameState = getGlobalGameState();
    if (!gameState || !gameState.arcadeProgress || typeof gameState.arcadeProgress !== 'object') {
      return null;
    }
    const entries = gameState.arcadeProgress.entries && typeof gameState.arcadeProgress.entries === 'object'
      ? gameState.arcadeProgress.entries
      : null;
    if (!entries) {
      return null;
    }
    const entry = entries[GAME_STATE_ARCADE_ID];
    if (!entry || typeof entry !== 'object') {
      return null;
    }
    return entry.state && typeof entry.state === 'object' ? entry.state : entry;
  }

  function schedulePendingGameStateSync() {
    if (typeof window === 'undefined') {
      return;
    }
    if (gameStateSyncTimerId != null) {
      window.clearTimeout(gameStateSyncTimerId);
    }
    gameStateSyncTimerId = window.setTimeout(() => {
      gameStateSyncTimerId = null;
      if (pendingGameStatePayload) {
        writeArcadeEntryToGameState(pendingGameStatePayload);
      }
    }, 200);
  }

  function writeArcadeEntryToGameState(payload) {
    const clone = safeClone(payload);
    if (!clone) {
      return;
    }
    const gameState = getGlobalGameState();
    if (!gameState) {
      pendingGameStatePayload = clone;
      schedulePendingGameStateSync();
      return;
    }
    if (!gameState.arcadeProgress || typeof gameState.arcadeProgress !== 'object') {
      gameState.arcadeProgress = { version: 1, entries: {} };
    }
    if (!gameState.arcadeProgress.entries || typeof gameState.arcadeProgress.entries !== 'object') {
      gameState.arcadeProgress.entries = {};
    }
    gameState.arcadeProgress.entries[GAME_STATE_ARCADE_ID] = {
      state: clone,
      updatedAt: Date.now()
    };
    pendingGameStatePayload = null;
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('load', () => {
      if (pendingGameStatePayload) {
        writeArcadeEntryToGameState(pendingGameStatePayload);
      }
    });
  }

  function readArcadeEntryFromNativeSave() {
    if (typeof window === 'undefined' || !window.AndroidSaveBridge || typeof window.AndroidSaveBridge.loadData !== 'function') {
      return null;
    }
    try {
      const raw = window.AndroidSaveBridge.loadData();
      if (!raw) {
        return null;
      }
      const payload = typeof raw === 'string' ? raw : String(raw);
      const parsed = JSON.parse(payload);
      if (!parsed || typeof parsed !== 'object') {
        return null;
      }
      const sources = [];
      if (parsed.clicker && typeof parsed.clicker === 'object') {
        sources.push(parsed.clicker);
      }
      sources.push(parsed);
      if (parsed.meta && typeof parsed.meta === 'object') {
        sources.push(parsed.meta);
      }
      for (let i = 0; i < sources.length; i += 1) {
        const container = sources[i];
        if (!container || typeof container !== 'object') {
          continue;
        }
        const progress = container.arcadeProgress && typeof container.arcadeProgress === 'object'
          ? container.arcadeProgress
          : null;
        if (!progress) {
          continue;
        }
        const entries = progress.entries && typeof progress.entries === 'object'
          ? progress.entries
          : progress;
        if (!entries || typeof entries !== 'object') {
          continue;
        }
        const entry = entries[GAME_STATE_ARCADE_ID];
        if (entry && typeof entry === 'object') {
          const state = entry.state && typeof entry.state === 'object' ? entry.state : entry;
          if (state && typeof state === 'object') {
            return state;
          }
        }
      }
    } catch (error) {
      return null;
    }
    return null;
  }

  function readArcadeEntryFromLegacyStorage() {
    if (typeof window === 'undefined' || !window.localStorage) {
      return null;
    }
    try {
      const raw = window.localStorage.getItem(ARCADE_AUTOSAVE_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') {
        return null;
      }
      const entries = parsed.entries && typeof parsed.entries === 'object' ? parsed.entries : parsed;
      const entry = entries[GAME_STATE_ARCADE_ID];
      if (!entry || typeof entry !== 'object') {
        return null;
      }
      return entry.state && typeof entry.state === 'object' ? entry.state : entry;
    } catch (error) {
      return null;
    }
  }

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

  function readArcadeEntryFromAutosaveApi() {
    const api = getAutosaveApi();
    if (!api) {
      return null;
    }
    try {
      return api.get(AUTOSAVE_KEY);
    } catch (error) {
      return null;
    }
  }

  function compareTopRuns(a, b) {
    if (a.score !== b.score) {
      return b.score - a.score;
    }
    if (a.waves !== b.waves) {
      return b.waves - a.waves;
    }
    return b.duration - a.duration;
  }

  function normalizeTopRunEntry(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const toNumber = value => {
      const numeric = Number(value);
      return Number.isFinite(numeric) && numeric >= 0 ? numeric : 0;
    };
    const score = Math.floor(toNumber(raw.score ?? raw.points ?? raw.bestScore));
    const duration = Math.floor(toNumber(raw.duration ?? raw.time ?? raw.elapsed ?? raw.seconds));
    const waves = Math.floor(toNumber(raw.waves ?? raw.wave ?? raw.maxWave));
    if (score <= 0 && waves <= 0 && duration <= 0) {
      return null;
    }
    return { score, duration, waves };
  }

  function sanitizeTopRunsArray(rawRuns) {
    const list = Array.isArray(rawRuns) ? rawRuns : [];
    const normalized = [];
    const seen = new Set();
    list.forEach(item => {
      const entry = normalizeTopRunEntry(item);
      if (!entry) {
        return;
      }
      const key = `${entry.score}|${entry.waves}|${entry.duration}`;
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
      normalized.push(entry);
    });
    normalized.sort(compareTopRuns);
    return normalized.slice(0, 3);
  }

  function topRunsEqual(a, b) {
    const left = sanitizeTopRunsArray(a);
    const right = sanitizeTopRunsArray(b);
    if (left.length !== right.length) {
      return false;
    }
    for (let i = 0; i < left.length; i += 1) {
      const current = left[i];
      const other = right[i];
      if (!other || current.score !== other.score || current.duration !== other.duration || current.waves !== other.waves) {
        return false;
      }
    }
    return true;
  }

  function sanitizeRecordEntry(raw) {
    const record = createEmptyRecordEntry();
    if (!raw || typeof raw !== 'object') {
      return record;
    }
    const toNumber = value => {
      const numeric = Number(value);
      return Number.isFinite(numeric) && numeric >= 0 ? numeric : 0;
    };
    record.bestScore = Math.max(0, Math.floor(toNumber(raw.bestScore ?? raw.score ?? raw.highScore)));
    record.bestTime = toNumber(raw.bestTime ?? raw.bestTimeSeconds ?? raw.time ?? raw.duration);
    record.bestWave = Math.max(0, Math.floor(toNumber(raw.bestWave ?? raw.wave ?? raw.maxWave)));
    const difficultyCandidate = Number(raw.bestDifficulty ?? raw.difficulty);
    record.bestDifficulty = Number.isFinite(difficultyCandidate) && difficultyCandidate > 0
      ? difficultyCandidate
      : 1;
    const runsSource = Array.isArray(raw.topRuns)
      ? raw.topRuns
      : Array.isArray(raw.bestRuns)
        ? raw.bestRuns
        : Array.isArray(raw.history)
          ? raw.history
          : [];
    record.topRuns = sanitizeTopRunsArray(runsSource);
    if (!record.topRuns.length) {
      const fallback = normalizeTopRunEntry({
        score: record.bestScore,
        duration: record.bestTime,
        waves: record.bestWave
      });
      if (fallback) {
        record.topRuns = [fallback];
      }
    }
    return record;
  }

  function aggregateRecords(records) {
    const aggregate = createEmptyRecordEntry();
    const runs = [];
    records.forEach(entry => {
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const sanitized = sanitizeRecordEntry(entry);
      aggregate.bestScore = Math.max(aggregate.bestScore, sanitized.bestScore);
      aggregate.bestTime = Math.max(aggregate.bestTime, sanitized.bestTime);
      aggregate.bestWave = Math.max(aggregate.bestWave, sanitized.bestWave);
      aggregate.bestDifficulty = Math.max(aggregate.bestDifficulty, sanitized.bestDifficulty);
      if (Array.isArray(sanitized.topRuns)) {
        runs.push(...sanitized.topRuns);
      }
    });
    aggregate.topRuns = sanitizeTopRunsArray(runs);
    if (!aggregate.topRuns.length) {
      const fallback = normalizeTopRunEntry({
        score: aggregate.bestScore,
        duration: aggregate.bestTime,
        waves: aggregate.bestWave
      });
      if (fallback) {
        aggregate.topRuns = [fallback];
      }
    }
    return aggregate;
  }

  function ensureRecordStateInitialized() {
    if (!state.records || typeof state.records !== 'object') {
      state.records = createInitialRecordState();
      return;
    }
    if (!state.records.modes || typeof state.records.modes !== 'object') {
      state.records.modes = {};
    }
    Object.values(DIFFICULTY_MODES).forEach(mode => {
      if (!state.records.modes[mode] || typeof state.records.modes[mode] !== 'object') {
        state.records.modes[mode] = createEmptyRecordEntry();
      } else {
        state.records.modes[mode] = sanitizeRecordEntry(state.records.modes[mode]);
      }
    });
    if (state.records.lastMode !== DIFFICULTY_MODES.EASY && state.records.lastMode !== DIFFICULTY_MODES.HARD) {
      state.records.lastMode = DIFFICULTY_MODES.HARD;
    }
  }

  function getRecordForMode(mode) {
    ensureRecordStateInitialized();
    const normalized = mode === DIFFICULTY_MODES.EASY ? DIFFICULTY_MODES.EASY : DIFFICULTY_MODES.HARD;
    const record = state.records.modes[normalized];
    if (!record || typeof record !== 'object') {
      state.records.modes[normalized] = createEmptyRecordEntry();
      return state.records.modes[normalized];
    }
    return record;
  }

  function getActiveRecord() {
    return getRecordForMode(state.difficultyMode);
  }

  function snapshotRecordForMode(mode) {
    const record = getRecordForMode(mode);
    const snapshot = sanitizeRecordEntry(record);
    const { list } = ensureTopRunsSanitized(snapshot);
    snapshot.topRuns = list;
    return snapshot;
  }

  function ensureTopRunsSanitized(record) {
    const target = record && typeof record === 'object' ? record : createEmptyRecordEntry();
    const stored = Array.isArray(target.topRuns) ? target.topRuns : [];
    const sanitized = sanitizeTopRunsArray(stored);
    let changed = stored.length !== sanitized.length;
    if (!changed) {
      for (let i = 0; i < sanitized.length; i += 1) {
        const original = normalizeTopRunEntry(stored[i]);
        const next = sanitized[i];
        if (!original || original.score !== next.score || original.duration !== next.duration || original.waves !== next.waves) {
          changed = true;
          break;
        }
      }
    }
    target.topRuns = sanitized;
    return { list: sanitized, changed };
  }

  function updateTopRunHistory(record, run) {
    const { list: current, changed: sanitizedChanged } = ensureTopRunsSanitized(record);
    const normalized = normalizeTopRunEntry(run);
    if (!normalized) {
      return sanitizedChanged;
    }
    const merged = sanitizeTopRunsArray([...current, normalized]);
    const changed = sanitizedChanged || !topRunsEqual(current, merged);
    if (changed) {
      record.topRuns = merged;
    }
    return changed;
  }

  function loadStoredRecordPayload() {
    return readArcadeEntryFromGameState()
      || readArcadeEntryFromNativeSave()
      || readArcadeEntryFromAutosaveApi()
      || readArcadeEntryFromLegacyStorage();
  }

  function sanitizeAutosaveData(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const lastSeed = typeof raw.lastSeed === 'string'
      ? raw.lastSeed.trim().slice(0, 32)
      : '';
    const modes = raw.modes && typeof raw.modes === 'object'
      ? raw.modes
      : null;
    const recordsByMode = {};
    if (modes) {
      recordsByMode[DIFFICULTY_MODES.EASY] = sanitizeRecordEntry(modes[DIFFICULTY_MODES.EASY]);
      recordsByMode[DIFFICULTY_MODES.HARD] = sanitizeRecordEntry(modes[DIFFICULTY_MODES.HARD]);
    } else {
      const legacyRecord = sanitizeRecordEntry(raw);
      const legacyMode = raw.lastMode === DIFFICULTY_MODES.EASY ? DIFFICULTY_MODES.EASY : DIFFICULTY_MODES.HARD;
      recordsByMode[legacyMode] = legacyRecord;
      const otherMode = legacyMode === DIFFICULTY_MODES.EASY ? DIFFICULTY_MODES.HARD : DIFFICULTY_MODES.EASY;
      recordsByMode[otherMode] = createEmptyRecordEntry();
    }
    if (!recordsByMode[DIFFICULTY_MODES.EASY]) {
      recordsByMode[DIFFICULTY_MODES.EASY] = createEmptyRecordEntry();
    }
    if (!recordsByMode[DIFFICULTY_MODES.HARD]) {
      recordsByMode[DIFFICULTY_MODES.HARD] = createEmptyRecordEntry();
    }
    const lastMode = raw.lastMode === DIFFICULTY_MODES.EASY
      ? DIFFICULTY_MODES.EASY
      : raw.lastMode === DIFFICULTY_MODES.HARD
        ? DIFFICULTY_MODES.HARD
        : DIFFICULTY_MODES.HARD;
    return {
      lastSeed,
      lastMode,
      recordsByMode
    };
  }

  function serializeAutosaveData() {
    const safeSeed = (state.rngSeed || state.lastSeed || '').toString().slice(0, 32);
    ensureRecordStateInitialized();
    const easySnapshot = snapshotRecordForMode(DIFFICULTY_MODES.EASY);
    const hardSnapshot = snapshotRecordForMode(DIFFICULTY_MODES.HARD);
    const aggregate = aggregateRecords([easySnapshot, hardSnapshot]);
    const lastMode = state.records.lastMode === DIFFICULTY_MODES.EASY
      ? DIFFICULTY_MODES.EASY
      : DIFFICULTY_MODES.HARD;
    return {
      version: 3,
      lastSeed: safeSeed,
      lastMode,
      bestScore: Math.max(0, Math.floor(aggregate.bestScore)),
      bestTime: Math.max(0, aggregate.bestTime),
      bestWave: Math.max(0, Math.floor(aggregate.bestWave)),
      bestDifficulty: Math.max(0, aggregate.bestDifficulty),
      topRuns: sanitizeTopRunsArray(aggregate.topRuns),
      modes: {
        [DIFFICULTY_MODES.EASY]: easySnapshot,
        [DIFFICULTY_MODES.HARD]: hardSnapshot
      }
    };
  }

  function persistAutosave() {
    const payload = serializeAutosaveData();
    writeArcadeEntryToGameState(payload);
    const api = getAutosaveApi();
    if (api) {
      try {
        api.set(AUTOSAVE_KEY, payload);
      } catch (error) {
        // Ignore autosave persistence errors
      }
    }
    if (nativeSaveTimerId != null) {
      window.clearTimeout(nativeSaveTimerId);
    }
    nativeSaveTimerId = window.setTimeout(() => {
      nativeSaveTimerId = null;
      if (typeof window !== 'undefined' && typeof window.atom2universSaveGame === 'function') {
        try {
          window.atom2universSaveGame();
        } catch (error) {
          // Ignore native save errors
        }
      }
    }, NATIVE_SAVE_DEBOUNCE_MS);
  }

  function persistAutosave() {
    const payload = serializeAutosaveData();
    writeArcadeEntryToGameState(payload);
    const api = getAutosaveApi();
    if (api) {
      try {
        api.set(AUTOSAVE_KEY, payload);
      } catch (error) {
        // Ignore autosave persistence errors
      }
    }
    requestNativeSave();
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
    requestNativeSave(true);
  }

  function loadAutosave() {
    const raw = loadStoredRecordPayload();
    const data = sanitizeAutosaveData(raw);
    if (!data) {
      return;
    }
    state.records = createInitialRecordState();
    state.records.modes[DIFFICULTY_MODES.EASY] = sanitizeRecordEntry(data.recordsByMode[DIFFICULTY_MODES.EASY]);
    state.records.modes[DIFFICULTY_MODES.HARD] = sanitizeRecordEntry(data.recordsByMode[DIFFICULTY_MODES.HARD]);
    if (data.lastMode === DIFFICULTY_MODES.EASY || data.lastMode === DIFFICULTY_MODES.HARD) {
      state.records.lastMode = data.lastMode;
      state.difficultyMode = data.lastMode;
    }
    state.lastSeed = data.lastSeed;
    writeArcadeEntryToGameState(serializeAutosaveData());
  }

  function loadConfig() {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (!data || typeof data !== 'object') {
          return;
        }
        state.config = resolveConfig(data);
        state.waveTimer = Math.min(state.waveTimer, getMaxWaveDuration());
      })
      .catch(error => {
        console.warn('Stars War config load error', error);
      });
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
    state.activeEnemyBulletCount = 0;
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
    state.paused = false;
    state.spawnInterval = BASE_SPAWN_INTERVAL;
    state.waveElapsed = 0;
    resetWaveTimer();
    state.maxOnScreen = 10 + Math.floor(3 * state.difficulty);
    state.pendingWaveAdvance = false;
    state.spawnTimer = 0;
    state.heartPitySeconds = 0;
    state.nextDifficultyTick = 60;
    state.nextSpawnDensityPush = { at12: false, at14: false };
    state.activeMilestones.clear();
    state.lastWaveDifficultyBoost = 0;
    state.gameOver = false;
    state.overlayMode = 'ready';
    state.lastTimestamp = 0;
    state.accumulator = 0;
    state.uiAccumulator = 0;
    state.uiNeedsSync = true;
    clearState();
    resetPlayer();
    updateUi(true);
    state.uiNeedsSync = false;
  }

  function setDifficultyMode(mode) {
    const normalized = mode === DIFFICULTY_MODES.EASY ? DIFFICULTY_MODES.EASY : DIFFICULTY_MODES.HARD;
    if (state.difficultyMode === normalized) {
      return false;
    }
    state.difficultyMode = normalized;
    ensureRecordStateInitialized();
    if (state.records.lastMode !== normalized) {
      state.records.lastMode = normalized;
      scheduleAutosave();
    }
    state.uiNeedsSync = true;
    return true;
  }

  function applyDifficultyModeChange() {
    const wasRunning = state.running && !state.gameOver && state.overlayMode === 'running';
    const wasPaused = state.paused && !state.gameOver && state.overlayMode === 'paused';
    const shouldAutoStart = wasRunning || wasPaused;
    state.running = false;
    state.paused = false;
    hideOverlay();
    flushAutosave();
    const seedValue = randomSeedString();
    applySeed(seedValue);
    state.scriptedSequence = createScriptedSequence();
    resetGame();
    scheduleNextWave();
    if (shouldAutoStart) {
      startRun();
    } else {
      promptForNewRun();
    }
  }

  function toggleDifficultyMode() {
    const nextMode = state.difficultyMode === DIFFICULTY_MODES.EASY
      ? DIFFICULTY_MODES.HARD
      : DIFFICULTY_MODES.EASY;
    if (!setDifficultyMode(nextMode)) {
      return;
    }
    applyDifficultyModeChange();
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

  function randomRange(min, max) {
    return min + getRandomFloat() * (max - min);
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

  function getMaxWaveDuration() {
    const config = state.config || DEFAULT_CONFIG;
    const duration = toFiniteNumber(config.maxWaveDurationSeconds, DEFAULT_CONFIG.maxWaveDurationSeconds);
    if (!Number.isFinite(duration) || duration <= 0) {
      return DEFAULT_CONFIG.maxWaveDurationSeconds;
    }
    return Math.max(5, duration);
  }

  function getEnemyBulletCapForWave() {
    const config = state.config && state.config.enemyBulletCap
      ? state.config.enemyBulletCap
      : DEFAULT_CONFIG.enemyBulletCap;
    const base = toFiniteNumber(config.base, DEFAULT_CONFIG.enemyBulletCap.base);
    const perWave = toFiniteNumber(config.perWave, DEFAULT_CONFIG.enemyBulletCap.perWave);
    const safeBase = Math.max(0, Math.floor(Number.isFinite(base) ? base : DEFAULT_CONFIG.enemyBulletCap.base));
    const safeIncrement = Math.max(0, Math.floor(Number.isFinite(perWave) ? perWave : DEFAULT_CONFIG.enemyBulletCap.perWave));
    const waveIndex = Math.max(1, Math.floor(state.wave || 1));
    const cap = safeBase + Math.max(0, waveIndex - 1) * safeIncrement;
    if (state.difficultyMode === DIFFICULTY_MODES.EASY) {
      return Math.max(0, Math.floor(cap * EASY_BULLET_CAP_FACTOR));
    }
    return cap;
  }

  function computeWaveInterval() {
    const progress = Math.min(1, state.elapsed / WAVE_ACCELERATION_DURATION);
    const reduction = (INITIAL_WAVE_INTERVAL - MIN_WAVE_INTERVAL) * progress;
    return INITIAL_WAVE_INTERVAL - reduction;
  }

  function resetWaveTimer() {
    const interval = computeWaveInterval();
    const maxDuration = getMaxWaveDuration();
    state.waveTimer = Math.min(interval, maxDuration);
  }

  function scheduleNextWave() {
    state.wave += 1;
    let spawnPlan = [];
    const scriptedSequence = Array.isArray(state.scriptedSequence) && state.scriptedSequence.length
      ? state.scriptedSequence
      : SCRIPTED_WAVE_SEQUENCE;
    const scriptedName = scriptedSequence[state.wave - 1];
    if (scriptedName) {
      spawnPlan = buildScriptedWavePlan(scriptedName);
    }
    if (!spawnPlan.length && state.wave > SCRIPTED_WAVE_SEQUENCE.length) {
      const dynamicChanceBase = 0.35 + 0.05 * state.difficulty;
      const elapsedBonus = state.elapsed >= 120 ? 0.15 : 0;
      const dynamicChance = Math.min(0.75, dynamicChanceBase + elapsedBonus);
      if (getRandomFloat() < dynamicChance) {
        spawnPlan = buildDynamicPatternWave({
          startDelay: 0,
          difficulty: state.difficulty,
          elapsed: state.elapsed,
          maxOnScreen: state.maxOnScreen
        });
      }
    }
    if (!spawnPlan.length) {
      spawnPlan = generateWave();
    }
    const now = state.elapsed;
    spawnPlan.forEach(entry => {
      const delay = Number.isFinite(entry.delay) ? Math.max(0, entry.delay) : 0;
      entry.spawnAt = now + delay;
    });
    state.spawnQueue.push(...spawnPlan);
    state.spawnQueue.sort((a, b) => (a.spawnAt || now) - (b.spawnAt || now));
    state.waveElapsed = 0;
    resetWaveTimer();
    state.pendingWaveAdvance = false;
  }

  function createScriptedSequence() {
    const sequence = SCRIPTED_WAVE_SEQUENCE.slice();
    for (let i = sequence.length - 1; i > 0; i -= 1) {
      const j = getRandomInt(0, i + 1);
      const tmp = sequence[i];
      sequence[i] = sequence[j];
      sequence[j] = tmp;
    }
    return sequence;
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

  function computeEnemyWeight(type, elapsed) {
    let weight = 1;
    if (!type.canShoot) {
      weight *= 0.55;
    }
    if (type.id === 'sniper') {
      if (elapsed >= 600) {
        weight *= 2.4;
      } else if (elapsed >= 300) {
        weight *= 1.6;
      }
    }
    if (type.id === 'miniboss') {
      if (elapsed >= 600) {
        weight *= 2.6;
      } else if (elapsed >= 300) {
        weight *= 1.8;
      }
    }
    return weight;
  }

  function pickWeightedEnemy(types, elapsed) {
    if (!types.length) {
      throw new Error('Cannot pick enemy from empty list');
    }
    const weights = types.map(type => Math.max(0.01, computeEnemyWeight(type, elapsed)));
    const totalWeight = weights.reduce((sum, weight) => sum + weight, 0);
    let roll = getRandomFloat() * totalWeight;
    for (let i = 0; i < types.length; i += 1) {
      roll -= weights[i];
      if (roll <= 0) {
        return types[i];
      }
    }
    return types[types.length - 1];
  }

  function buildDynamicPatternWave(options = {}) {
    const startDelay = options.startDelay || 0;
    const difficulty = options.difficulty != null ? options.difficulty : state.difficulty;
    const elapsed = options.elapsed != null ? options.elapsed : state.elapsed;
    const maxOnScreen = Math.max(6, options.maxOnScreen || state.maxOnScreen || 12);
    const primaryPatterns = SCRIPTED_PATTERN_LIBRARY.patterns.filter(pattern => pattern.type !== 'idle' && pattern.type !== 'homing');
    if (!primaryPatterns.length) {
      return [];
    }
    const allowedTypes = getAllowedEnemies(difficulty);
    if (!allowedTypes.length) {
      return [];
    }
    const entries = [];
    const entryCap = Math.max(3, Math.min(6, Math.floor(maxOnScreen * 0.6)));
    const target = Math.max(3, Math.min(entryCap, Math.round(3 + difficulty * 0.6 + getRandomFloat() * 2)));
    let delayCursor = startDelay;
    while (entries.length < target) {
      const basePattern = pickRandom(primaryPatterns);
      if (!basePattern) {
        break;
      }
      const pattern = randomizePatternDef(basePattern, difficulty);
      if (!pattern) {
        break;
      }
      const followUp = createDynamicFollowUp(pattern, difficulty);
      const spreadX = Math.min(200, 60 + difficulty * 18);
      const spreadY = Math.min(140, 40 + difficulty * 10);
      const formationOffset = [
        (getRandomFloat() - 0.5) * spreadX,
        (getRandomFloat() - 0.5) * spreadY
      ];
      entries.push({
        scripted: true,
        delay: delayCursor,
        pattern,
        thenDef: followUp ? followUp.def : null,
        thenAt: followUp ? followUp.at : null,
        formationOffset,
        enemyType: pickWeightedEnemy(allowedTypes, elapsed)
      });
      if (entries.length >= entryCap) {
        break;
      }
      if (getRandomFloat() < 0.3 && entries.length < entryCap) {
        const partnerPattern = randomizePatternDef(basePattern, difficulty);
        const partnerFollow = partnerPattern ? createDynamicFollowUp(partnerPattern, difficulty) : null;
        entries.push({
          scripted: true,
          delay: delayCursor + randomRange(0.12, 0.3),
          pattern: partnerPattern || pattern,
          thenDef: partnerFollow ? partnerFollow.def : followUp ? followUp.def : null,
          thenAt: partnerFollow ? partnerFollow.at : followUp ? followUp.at : null,
          formationOffset: [
            -formationOffset[0],
            formationOffset[1] + randomRange(-20, 20)
          ],
          enemyType: pickWeightedEnemy(allowedTypes, elapsed)
        });
      }
      delayCursor += randomRange(0.35, 0.7);
    }
    entries.sort((a, b) => a.delay - b.delay);
    return entries;
  }

  function generateWave() {
    const D = state.difficulty;
    const laneCount = Math.max(4, Math.min(8, Math.floor(4 + D)));
    const formation = pickRandom(FORMATIONS);
    const path = pickRandom(PATHS);
    const allowed = getAllowedEnemies(D);
    const waveEntries = [];
    const isFirstWave = state.wave === 1;
    const isSecondWave = state.wave === 2;
    const elapsed = state.elapsed;
    const entryCap = Math.max(6, Math.floor(state.maxOnScreen * 0.9));
    let baseCount = Math.max(5, Math.floor(5 + 1.05 * D));
    if (isFirstWave) {
      baseCount = 5;
    } else if (isSecondWave) {
      baseCount = Math.max(6, Math.floor(5 + 0.9 * D));
    }
    baseCount = Math.min(baseCount, entryCap);
    for (let i = 0; i < baseCount && waveEntries.length < entryCap; i += 1) {
      const type = pickWeightedEnemy(allowed, elapsed);
      waveEntries.push(createSpawnEntry(type, formation, path, laneCount, i, baseCount));
    }

    let sequenceDelay = waveEntries.length ? waveEntries[waveEntries.length - 1].delay + 0.6 : 0.6;
    const allowSpecialSequences = state.elapsed >= 30 || state.wave >= 3;

    if (allowSpecialSequences && D >= 3 && getRandomFloat() < 0.45 && waveEntries.length < entryCap) {
      const skirmish = planTopSkirmish({ laneCount, startDelay: sequenceDelay, difficulty: D, elapsed });
      if (skirmish.entries.length) {
        waveEntries.push(...skirmish.entries);
        sequenceDelay = skirmish.nextDelay;
        if (waveEntries.length > entryCap) {
          waveEntries.length = entryCap;
        }
      }
    }

    const minibossChance = elapsed >= 600 ? 0.55 : elapsed >= 300 ? 0.38 : 0.25;
    if (allowSpecialSequences && D >= 4 && getRandomFloat() < minibossChance && waveEntries.length < entryCap) {
      const lane = getRandomInt(0, laneCount);
      const bossDelay = Math.max(sequenceDelay, waveEntries.length ? waveEntries[waveEntries.length - 1].delay + 1 : 1);
      const bossEntry = makePatternEntry(ENEMY_TYPES.miniboss, lane, bossDelay, laneCount, {
        formation: 'COLUMN',
        path: 'BOSS_SWAY',
        props: {
          anchorY: CANVAS_HEIGHT * PLAYER_AREA_TOP_RATIO,
          swayAmplitude: CANVAS_WIDTH * 0.24,
          swayFrequency: 0.7,
          swayPhase: getRandomFloat() * Math.PI * 2
        }
      });
      waveEntries.push(bossEntry);
      sequenceDelay = bossDelay + 1.2;
      if (elapsed >= 600 && waveEntries.length < entryCap) {
        const offsetLane = clampLaneIndex(lane + (getRandomFloat() < 0.5 ? 1 : -1), laneCount);
        const extraDelay = sequenceDelay + 1.4;
        const extraBoss = makePatternEntry(ENEMY_TYPES.miniboss, offsetLane, extraDelay, laneCount, {
          formation: 'COLUMN',
          path: 'BOSS_SWAY',
          props: {
            anchorY: CANVAS_HEIGHT * PLAYER_AREA_TOP_RATIO,
            swayAmplitude: CANVAS_WIDTH * 0.28,
            swayFrequency: 0.75,
            swayPhase: getRandomFloat() * Math.PI * 2
          }
        });
        waveEntries.push(extraBoss);
        sequenceDelay = extraDelay + 1.2;
      }
    }

    const availablePatterns = allowSpecialSequences
      ? WAVE_PATTERNS.filter(pattern => D >= pattern.minDifficulty)
      : [];
    if (availablePatterns.length && waveEntries.length < entryCap) {
      const patternRuns = Math.min(availablePatterns.length, 1 + Math.floor(D / 2));
      let startDelay = Math.max(sequenceDelay, waveEntries.length ? waveEntries[waveEntries.length - 1].delay + 0.8 : 0.8);
      for (let i = 0; i < patternRuns; i += 1) {
        const pattern = pickRandom(availablePatterns);
        const plan = pattern.build({
          laneCount,
          startDelay,
          difficulty: D
        });
        if (plan && Array.isArray(plan.entries) && plan.entries.length) {
          waveEntries.push(...plan.entries);
          const lastEntry = plan.entries[plan.entries.length - 1];
          startDelay = plan.nextDelay != null ? plan.nextDelay : lastEntry.delay + 0.9;
          if (waveEntries.length >= entryCap) {
            waveEntries.length = entryCap;
            break;
          }
        }
      }
    }

    if (state.wave % 5 === 0) {
      const center = Math.floor(laneCount / 2);
      const lane = clampLaneIndex(center + getRandomInt(-1, Math.min(2, laneCount - center)), laneCount);
      const minibossDelay = waveEntries.length ? waveEntries[waveEntries.length - 1].delay + 1.2 : 1.2;
      const miniboss = makePatternEntry(ENEMY_TYPES.miniboss, lane, minibossDelay, laneCount, {
        formation: 'COLUMN',
        path: 'BOSS_SWAY',
        props: {
          anchorY: CANVAS_HEIGHT * PLAYER_AREA_TOP_RATIO,
          swayAmplitude: CANVAS_WIDTH * 0.26,
          swayFrequency: 0.6,
          swayPhase: 0
        }
      });
      waveEntries.push(miniboss);
      if (elapsed >= 600) {
        const alternateLane = clampLaneIndex(lane + (getRandomFloat() < 0.5 ? -1 : 1), laneCount);
        const followUpDelay = minibossDelay + 1.6;
        const followUp = makePatternEntry(ENEMY_TYPES.miniboss, alternateLane, followUpDelay, laneCount, {
          formation: 'COLUMN',
          path: 'BOSS_SWAY',
          props: {
            anchorY: CANVAS_HEIGHT * PLAYER_AREA_TOP_RATIO,
            swayAmplitude: CANVAS_WIDTH * 0.3,
            swayFrequency: 0.78,
            swayPhase: Math.PI / 2
          }
        });
        waveEntries.push(followUp);
      }
    }

    if (elapsed >= 300) {
      const sniperUpgradeChance = elapsed >= 600 ? 0.45 : 0.22;
      for (const entry of waveEntries) {
        if (!entry.type.canShoot && entry.type.id !== 'carrier' && getRandomFloat() < sniperUpgradeChance) {
          entry.type = ENEMY_TYPES.sniper;
        }
      }
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
      offset,
      laneCount,
      props: undefined
    };
  }

  function spawnScriptedEnemy(entry) {
    const type = entry.enemyType || ENEMY_TYPES.drone;
    const enemyWidth = type.id === 'miniboss' ? 60 : type.id === 'tank' ? 48 : 40;
    const enemyHeight = enemyWidth;
    const baseHp = type.id === 'miniboss' ? ENEMY_TYPES.miniboss.baseHp : type.baseHp;
    const difficultyBonus = type.id === 'miniboss'
      ? Math.floor(3 * state.difficulty)
      : Math.floor(0.6 * state.difficulty);
    const minuteMultiplier = Math.pow(1.1, state.elapsed / 60);
    const hp = Math.max(1, Math.floor((baseHp + difficultyBonus) * minuteMultiplier));
    const speed = type.id === 'miniboss'
      ? ENEMY_TYPES.miniboss.baseSpeed * (1 + 0.03 * state.difficulty)
      : type.baseSpeed * (1 + 0.09 * state.difficulty);

    const controller = new PatternController(entry.pattern, {
      thenDef: entry.thenDef,
      thenAt: entry.thenAt,
      formationOffset: entry.formationOffset
    });

    const enemy = {
      id: type.id,
      type,
      x: 0,
      y: -enemyHeight,
      width: enemyWidth,
      height: enemyHeight,
      hp,
      maxHp: hp,
      speed,
      path: 'SCRIPTED',
      formation: 'SCRIPTED',
      age: 0,
      fireTimer: type.canShoot ? (type.fireRate / (1 + 0.06 * state.difficulty)) * getRandomFloat() : 0,
      lockedTargetX: null,
      remove: false,
      waveIndex: state.wave,
      motionController: controller,
      rotation: 0
    };

    const pose = controller.snapshot(createPatternRuntimeState(0));
    enemy.x = pose.x;
    enemy.y = pose.y;
    enemy.rotation = pose.rotation;
    enemy.anchorX = enemy.x;
    enemy.anchorReached = false;
    enemy.figure8Timer = 0;
    enemy.swayTimer = 0;

    state.enemies.push(enemy);
  }

  function spawnEnemy(entry) {
    if (entry && entry.scripted) {
      spawnScriptedEnemy(entry);
      return;
    }
    const D = state.difficulty;
    const enemyWidth = entry.type.id === 'miniboss' ? 60 : entry.type.id === 'tank' ? 48 : 40;
    const enemyHeight = enemyWidth;
    const laneSpan = Math.max(1, entry.laneCount || 6);
    const laneWidth = CANVAS_WIDTH / laneSpan;
    const baseX = laneWidth * (entry.lane + 0.5);
    const spawnX = clamp(baseX + entry.offset, enemyWidth / 2, CANVAS_WIDTH - enemyWidth / 2);
    const spawnY = -enemyHeight - getRandomFloat() * 60;
    const baseHp = entry.type.id === 'miniboss'
      ? ENEMY_TYPES.miniboss.baseHp
      : entry.type.baseHp;
    const difficultyBonus = entry.type.id === 'miniboss'
      ? Math.floor(3 * D)
      : Math.floor(0.6 * D);
    const minuteMultiplier = Math.pow(1.1, state.elapsed / 60);
    const hp = Math.max(1, Math.floor((baseHp + difficultyBonus) * minuteMultiplier));
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
    enemy.anchorX = enemy.x;
    if (entry.props) {
      if (entry.props.anchorY != null) {
        enemy.anchorY = entry.props.anchorY;
      }
      if (entry.props.swayAmplitude != null) {
        enemy.swayAmplitude = entry.props.swayAmplitude;
      }
      if (entry.props.swayFrequency != null) {
        enemy.swayFrequency = entry.props.swayFrequency;
      }
      if (entry.props.swayPhase != null) {
        enemy.swayPhase = entry.props.swayPhase;
      }
      if (entry.props.figure8RadiusX != null) {
        enemy.figure8RadiusX = entry.props.figure8RadiusX;
      }
      if (entry.props.figure8RadiusY != null) {
        enemy.figure8RadiusY = entry.props.figure8RadiusY;
      }
    }
    enemy.anchorReached = false;
    enemy.figure8Timer = 0;
    enemy.swayTimer = 0;
    state.enemies.push(enemy);
  }

  function getPlayerBounds() {
    const scale = getShipHitboxScale('player');
    const halfWidth = (player.width * scale) / 2;
    const halfHeight = (player.height * scale) / 2;
    return {
      left: player.x - halfWidth,
      right: player.x + halfWidth,
      top: player.y - halfHeight,
      bottom: player.y + halfHeight
    };
  }

  function getEnemyCollisionBounds(enemy) {
    const scale = getShipHitboxScale('enemy');
    const halfWidth = (enemy.width * scale) / 2;
    const halfHeight = (enemy.height * scale) / 2;
    return {
      left: enemy.x - halfWidth,
      right: enemy.x + halfWidth,
      top: enemy.y - halfHeight,
      bottom: enemy.y + halfHeight
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
    if (state.activeEnemyBulletCount >= getEnemyBulletCapForWave()) {
      return;
    }
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
    state.activeEnemyBulletCount += 1;
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
    const movementDelta = delta * slowFactor;
    enemy.age += delta;

    if (enemy.motionController) {
      const pose = enemy.motionController.advance(movementDelta, createPatternRuntimeState(movementDelta));
      enemy.x = pose.x;
      enemy.y = pose.y;
      enemy.rotation = pose.rotation;
      return;
    }

    const speed = enemy.speed * movementDelta;
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
      case 'FIGURE8': {
        const anchorY = enemy.anchorY != null ? enemy.anchorY : CANVAS_HEIGHT * 0.2;
        const approachSpeed = speed * 0.9;
        if (!enemy.anchorReached) {
          const direction = Math.sign(anchorY - enemy.y) || 1;
          const step = Math.min(Math.abs(anchorY - enemy.y), approachSpeed);
          nextY = enemy.y + direction * step;
          nextX = enemy.x;
          if (Math.abs(anchorY - nextY) <= 1.5) {
            enemy.anchorReached = true;
            enemy.figure8Timer = 0;
            enemy.anchorY = anchorY;
          }
        } else {
          enemy.figure8Timer = (enemy.figure8Timer || 0) + delta;
          const timer = enemy.figure8Timer * 1.6;
          const radiusX = enemy.figure8RadiusX != null ? enemy.figure8RadiusX : 80;
          const radiusY = enemy.figure8RadiusY != null ? enemy.figure8RadiusY : 28;
          nextX = enemy.anchorX + Math.sin(timer) * radiusX;
          nextY = anchorY + Math.sin(timer * 2) * radiusY;
        }
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
      case 'BOSS_SWAY': {
        const anchorY = enemy.anchorY != null ? enemy.anchorY : CANVAS_HEIGHT * PLAYER_AREA_TOP_RATIO;
        if (!enemy.anchorReached) {
          const direction = Math.sign(anchorY - enemy.y) || 1;
          const step = Math.min(Math.abs(anchorY - enemy.y), speed);
          nextY = enemy.y + direction * step;
          nextX = enemy.x;
          if (Math.abs(anchorY - nextY) <= 1.5) {
            enemy.anchorReached = true;
            enemy.swayTimer = 0;
            enemy.anchorY = anchorY;
          }
        } else {
          enemy.swayTimer = (enemy.swayTimer || 0) + delta;
          const amplitude = enemy.swayAmplitude != null ? enemy.swayAmplitude : CANVAS_WIDTH * 0.25;
          const frequency = enemy.swayFrequency != null ? enemy.swayFrequency : 0.65;
          const phase = enemy.swayPhase || 0;
          const theta = enemy.swayTimer * Math.PI * frequency;
          nextX = enemy.anchorX + Math.sin(theta + phase) * amplitude;
          nextY = anchorY + Math.sin((theta + phase) * 0.6) * 18;
        }
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

    const minX = enemy.width / 2;
    const maxX = CANVAS_WIDTH - enemy.width / 2;
    let clampedX = clamp(nextX, minX, maxX);
    if (clampedX === minX || clampedX === maxX) {
      const center = CANVAS_WIDTH / 2;
      const towardCenter = clampedX < center ? 1 : -1;
      clampedX = clamp(clampedX + towardCenter * enemy.speed * delta * 0.6, minX, maxX);
    }
    enemy.x = clampedX;
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

    if (inputState.pointerActive && inputState.pointerMode !== 'relative') {
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

    if (inputState.pointerActive && inputState.pointerMode === 'relative') {
      const dx = inputState.pointerX - inputState.pointerOriginX;
      const dy = inputState.pointerY - inputState.pointerOriginY;
      if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
        player.x += dx;
        player.y += dy;
      }
    }

    player.x = clamp(player.x, player.width / 2, CANVAS_WIDTH - player.width / 2);
    player.y = clamp(player.y, areaTop, CANVAS_HEIGHT - player.height / 2);

    if (inputState.pointerActive && inputState.pointerMode === 'relative') {
      inputState.pointerOriginX = inputState.pointerX;
      inputState.pointerOriginY = inputState.pointerY;
    }
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
    state.activeEnemyBulletCount = state.enemyBullets.length;
  }

  function updateEnemies(delta) {
    const playerBounds = getPlayerBounds();
    state.enemies.forEach(enemy => {
      moveEnemy(enemy, delta);
      fireEnemyWeapons(enemy, delta);
      const collisionBounds = getEnemyCollisionBounds(enemy);
      const enemyVisualTop = enemy.y - enemy.height / 2;
      if (enemyVisualTop > CANVAS_HEIGHT + enemy.height) {
        enemy.remove = true;
      }
      if (intersects(playerBounds, collisionBounds)) {
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
        const enemyBounds = getEnemyCollisionBounds(enemy);
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

  function updateWaveProgress() {
    if (state.wave <= 0) {
      return;
    }
    const easyMode = state.difficultyMode === DIFFICULTY_MODES.EASY;
    const waveCleared = !state.spawnQueue.length && !state.enemies.length;
    if (waveCleared && state.lastWaveDifficultyBoost !== state.wave) {
      state.difficulty = Math.min(8, state.difficulty + 0.25);
      state.lastWaveDifficultyBoost = state.wave;
      state.maxOnScreen = 10 + Math.floor(3 * state.difficulty);
    }
    if (easyMode) {
      if (waveCleared && !state.pendingWaveAdvance) {
        state.pendingWaveAdvance = true;
      }
      return;
    }
    const maxDuration = getMaxWaveDuration();
    if ((waveCleared && state.waveTimer > 0) || state.waveElapsed >= maxDuration) {
      state.waveTimer = Math.min(state.waveTimer, 0);
    }
  }

  function updateWaveTimer(delta) {
    if (state.difficultyMode === DIFFICULTY_MODES.EASY) {
      if (state.pendingWaveAdvance) {
        state.pendingWaveAdvance = false;
        scheduleNextWave();
      }
      return;
    }
    state.waveTimer -= delta;
    while (state.waveTimer <= 0) {
      const overrun = -state.waveTimer;
      scheduleNextWave();
      state.waveTimer -= overrun;
    }
  }

  function maybeSpawnEnemies(delta) {
    if (!state.spawnQueue.length) {
      return;
    }
    const next = state.spawnQueue[0];
    const spawnAt = next.spawnAt != null ? next.spawnAt : state.elapsed;
    if (state.elapsed + 1e-6 < spawnAt) {
      return;
    }
    if (state.enemies.length >= state.maxOnScreen) {
      return;
    }
    state.spawnQueue.shift();
    spawnEnemy(next);
  }

  function updateDifficulty(delta) {
    const previousDifficulty = state.difficulty;
    while (state.elapsed >= state.nextDifficultyTick) {
      state.difficulty = Math.min(8, state.difficulty + 0.4);
      state.nextDifficultyTick += 60;
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

  function arraysShallowEqual(a, b) {
    if (a === b) {
      return true;
    }
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) {
      return false;
    }
    for (let i = 0; i < a.length; i += 1) {
      if (a[i] !== b[i]) {
        return false;
      }
    }
    return true;
  }

  function getPowerupProgress(id, entry) {
    if (!entry || entry.expiresAt === Infinity) {
      return 1;
    }
    const definition = POWERUP_DEFS[id] || {};
    const duration = Number(definition.duration) || 0;
    if (duration <= 0) {
      return 1;
    }
    const remaining = Math.max(0, entry.expiresAt - state.elapsed);
    const progress = 1 - remaining / duration;
    return Math.min(1, Math.max(0, progress));
  }

  function getUiTranslation(key) {
    if (typeof key !== 'string' || !key) {
      return '';
    }
    const languageKey = uiState.language || '';
    const cacheKey = `${languageKey}|${key}`;
    if (uiState.translationCache.has(cacheKey)) {
      return uiState.translationCache.get(cacheKey) || '';
    }
    const value = translate(key);
    uiState.translationCache.set(cacheKey, value);
    return value || '';
  }

  function updatePauseButtonState(force = false) {
    if (!elements.pauseButton) {
      return;
    }
    const button = elements.pauseButton;
    const pauseKey = typeof button.dataset.pauseI18n === 'string'
      ? button.dataset.pauseI18n
      : 'index.sections.starsWar.controls.pause';
    const resumeKey = typeof button.dataset.resumeI18n === 'string'
      ? button.dataset.resumeI18n
      : 'index.sections.starsWar.controls.resume';
    const isPaused = Boolean(state.paused);
    const activeKey = isPaused ? resumeKey : pauseKey;
    if (activeKey) {
      button.dataset.i18n = activeKey;
    } else {
      delete button.dataset.i18n;
    }
    const canInteract = state.overlayMode === 'running' || isPaused;
    const nextPressed = isPaused ? 'true' : 'false';
    if (button.getAttribute('aria-pressed') !== nextPressed) {
      button.setAttribute('aria-pressed', nextPressed);
    }
    if (button.disabled !== !canInteract) {
      button.disabled = !canInteract;
    }
    const currentLang = resolveLanguageCode();
    const previousLang = button.dataset.pauseLang || '';
    const previousKey = button.dataset.pauseActiveKey || '';
    if (force || previousLang !== (currentLang || '') || previousKey !== (activeKey || '')) {
      const label = activeKey ? getUiTranslation(activeKey) : '';
      button.textContent = label || (isPaused ? 'Resume' : 'Pause');
      button.dataset.pauseLang = currentLang || '';
      button.dataset.pauseActiveKey = activeKey || '';
      const ariaLabel = button.textContent || (isPaused ? 'Resume' : 'Pause');
      if (ariaLabel) {
        button.setAttribute('aria-label', ariaLabel);
      }
    }
  }

  function updateDifficultyToggleState(force = false) {
    if (!elements.difficultyButton) {
      return;
    }
    const button = elements.difficultyButton;
    const isEasy = state.difficultyMode === DIFFICULTY_MODES.EASY;
    const pressed = isEasy ? 'true' : 'false';
    if (button.getAttribute('aria-pressed') !== pressed) {
      button.setAttribute('aria-pressed', pressed);
    }
    const translationKey = isEasy
      ? 'index.sections.starsWar.controls.difficulty.easy'
      : 'index.sections.starsWar.controls.difficulty.hard';
    const currentLang = uiState.language || '';
    const previousLang = button.dataset.modeLang || '';
    const previousKey = button.dataset.modeKey || '';
    if (force || previousLang !== currentLang || previousKey !== translationKey) {
      const label = getUiTranslation(translationKey) || (isEasy ? 'Easy mode' : 'Hard mode');
      button.textContent = label;
      button.dataset.modeLang = currentLang;
      button.dataset.modeKey = translationKey;
      if (translationKey) {
        button.dataset.i18n = translationKey;
      } else {
        delete button.dataset.i18n;
      }
      if (label) {
        button.setAttribute('aria-label', label);
      }
    }
  }

  function updateUi(force = false) {
    const currentLang = resolveLanguageCode();
    const normalizedLang = currentLang || '';
    let effectiveForce = force;
    if (uiState.language !== normalizedLang) {
      uiState.language = normalizedLang;
      uiState.powerupOrder = [];
      uiState.powerupEntries.clear();
      uiState.translationCache.clear();
      ensureNumberFormatter(normalizedLang);
      effectiveForce = true;
    }
    updatePauseButtonState(effectiveForce);
    updateDifficultyToggleState(effectiveForce);
    if (elements.scoreValue) {
      const scoreText = formatNumber(state.score);
      if (effectiveForce || uiState.scoreText !== scoreText) {
        elements.scoreValue.textContent = scoreText;
        uiState.scoreText = scoreText;
      }
    }
    if (elements.timeValue) {
      const timeText = formatTime(state.elapsed);
      if (effectiveForce || uiState.timeText !== timeText) {
        elements.timeValue.textContent = timeText;
        uiState.timeText = timeText;
      }
    }
    if (elements.waveValue) {
      const waveText = state.wave.toString();
      if (effectiveForce || uiState.waveText !== waveText) {
        elements.waveValue.textContent = waveText;
        uiState.waveText = waveText;
      }
    }
    if (elements.difficultyValue) {
      const difficultyText = state.difficulty.toFixed(1);
      if (effectiveForce || uiState.difficultyText !== difficultyText) {
        elements.difficultyValue.textContent = difficultyText;
        uiState.difficultyText = difficultyText;
      }
    }
    if (elements.livesContainer) {
      const livesKey = `${player.hp}|${player.shieldCharges}`;
      if (effectiveForce || uiState.livesKey !== livesKey) {
        uiState.livesKey = livesKey;
        const fragments = document.createDocumentFragment();
        for (let i = 0; i < PLAYER_MAX_HP; i += 1) {
          const life = document.createElement('span');
          life.className = 'stars-war__life';
          life.setAttribute('aria-hidden', 'true');
          const icon = createPowerupIconElement('heart', 26);
          if (i < player.hp) {
            life.classList.add('stars-war__life--full');
          } else {
            life.classList.add('stars-war__life--empty');
          }
          life.appendChild(icon);
          fragments.appendChild(life);
        }
        if (player.shieldCharges > 0) {
          const shield = document.createElement('span');
          shield.className = 'stars-war__life stars-war__life--shield';
          shield.setAttribute('aria-hidden', 'true');
          const icon = createPowerupIconElement('shield', 28);
          shield.appendChild(icon);
          if (player.shieldCharges > 1) {
            const count = document.createElement('span');
            count.className = 'stars-war__life-count';
            count.textContent = `×${player.shieldCharges}`;
            shield.appendChild(count);
          }
          fragments.appendChild(shield);
        }
        elements.livesContainer.innerHTML = '';
        elements.livesContainer.appendChild(fragments);
      }
    }
    if (elements.powerupList) {
      const powerupIds = Object.keys(state.powerups)
        .filter(id => id !== 'shield' && state.powerups[id]);
      const order = powerupIds.slice();
      const hasShield = player.shieldCharges > 0;
      if (hasShield) {
        order.push('shield');
      }
      if (effectiveForce || !arraysShallowEqual(uiState.powerupOrder, order)) {
        elements.powerupList.innerHTML = '';
        uiState.powerupOrder = order.slice();
        uiState.powerupEntries.clear();
        powerupIds.forEach(id => {
          const entry = state.powerups[id];
          if (!entry) {
            return;
          }
          const li = document.createElement('li');
          li.className = 'stars-war__powerup-item';
          const label = document.createElement('span');
          label.className = 'stars-war__powerup-label';
          const labelKey = POWERUP_LABEL_KEYS[id];
          if (labelKey) {
            label.dataset.i18n = labelKey;
          } else {
            delete label.dataset.i18n;
          }
          label.textContent = getUiTranslation(labelKey || id);
          li.appendChild(label);
          let progressEl = null;
          let progressValue = 1;
          if (entry.expiresAt !== Infinity) {
            progressEl = document.createElement('span');
            progressEl.className = 'stars-war__powerup-progress';
            progressValue = getPowerupProgress(id, entry);
            progressEl.style.setProperty('--progress', progressValue.toString());
            li.appendChild(progressEl);
          }
          elements.powerupList.appendChild(li);
          uiState.powerupEntries.set(id, {
            element: li,
            label,
            progress: progressEl,
            progressValue
          });
        });
        if (hasShield) {
          const li = document.createElement('li');
          li.className = 'stars-war__powerup-item';
          const label = document.createElement('span');
          label.className = 'stars-war__powerup-label';
          label.dataset.i18n = POWERUP_LABEL_KEYS.shield;
          label.textContent = getUiTranslation(POWERUP_LABEL_KEYS.shield);
          const count = document.createElement('span');
          count.className = 'stars-war__powerup-count';
          const countText = `×${player.shieldCharges}`;
          count.textContent = countText;
          li.appendChild(label);
          li.appendChild(count);
          elements.powerupList.appendChild(li);
          uiState.powerupEntries.set('shield', {
            element: li,
            label,
            count,
            countText
          });
        }
      } else {
        powerupIds.forEach(id => {
          const entry = state.powerups[id];
          const uiEntry = uiState.powerupEntries.get(id);
          if (!uiEntry) {
            return;
          }
          const labelKey = POWERUP_LABEL_KEYS[id];
          if (labelKey) {
            uiEntry.label.dataset.i18n = labelKey;
          } else {
            delete uiEntry.label.dataset.i18n;
          }
          const labelText = getUiTranslation(labelKey || id);
          if (effectiveForce || uiEntry.label.textContent !== labelText) {
            uiEntry.label.textContent = labelText;
          }
          if (uiEntry.progress) {
            const progress = getPowerupProgress(id, entry);
            if (!Number.isFinite(uiEntry.progressValue) || Math.abs(progress - uiEntry.progressValue) > 0.01) {
              uiEntry.progress.style.setProperty('--progress', progress.toString());
              uiEntry.progressValue = progress;
            }
          }
        });
        if (hasShield) {
          const uiEntry = uiState.powerupEntries.get('shield');
          if (uiEntry) {
            const labelText = getUiTranslation(POWERUP_LABEL_KEYS.shield);
            if (effectiveForce || uiEntry.label.textContent !== labelText) {
              uiEntry.label.textContent = labelText;
            }
            const nextCount = `×${player.shieldCharges}`;
            if (uiEntry.countText !== nextCount) {
              uiEntry.countText = nextCount;
              uiEntry.count.textContent = nextCount;
            }
          }
        }
      }
    }
  }

  function renderBackground(ctx) {
    if (!backgroundGradientCache.gradient || backgroundGradientCache.context !== ctx) {
      const gradient = ctx.createLinearGradient(0, 0, 0, CANVAS_HEIGHT);
      gradient.addColorStop(0, '#050912');
      gradient.addColorStop(1, '#0b1836');
      backgroundGradientCache.gradient = gradient;
      backgroundGradientCache.context = ctx;
    }
    ctx.fillStyle = backgroundGradientCache.gradient;
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
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.65)';
    ctx.lineWidth = 1.5;
    state.playerBullets.forEach(bullet => {
      const radius = Math.max(3, Math.min(bullet.width, bullet.height) / 2);
      ctx.beginPath();
      ctx.arc(bullet.x, bullet.y, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    });
    ctx.fillStyle = '#ff8a8a';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)';
    state.enemyBullets.forEach(bullet => {
      const radius = Math.max(3, Math.min(bullet.width, bullet.height) / 2);
      ctx.beginPath();
      ctx.arc(bullet.x, bullet.y, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    });
  }

  function drawDrops(ctx) {
    state.drops.forEach(drop => {
      const style = POWERUP_VISUALS[drop.id] || POWERUP_VISUALS.default;
      ctx.save();
      ctx.translate(drop.x, drop.y);
      drawPowerupBadge(ctx, style, DROP_SIZE);
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
    state.waveElapsed += delta;
    state.uiAccumulator += delta;
    applyMilestones();
    updateHeartPity(delta);
    updateWaveProgress();
    updatePowerups(delta);
    updateWaveTimer(delta);
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
    if (state.uiNeedsSync) {
      updateUi(true);
      state.uiNeedsSync = false;
      state.uiAccumulator = 0;
    } else if (state.uiAccumulator >= UI_UPDATE_INTERVAL) {
      updateUi();
      state.uiAccumulator %= UI_UPDATE_INTERVAL;
    }
  }

  function updateRecords() {
    const record = getActiveRecord();
    let updated = false;
    const score = Math.max(0, Math.floor(Number(state.score) || 0));
    if (score > record.bestScore) {
      record.bestScore = score;
      updated = true;
    }
    const elapsedSeconds = Math.max(0, Math.floor(Number(state.elapsed) || 0));
    if (elapsedSeconds > record.bestTime) {
      record.bestTime = elapsedSeconds;
      updated = true;
    }
    const wave = Math.max(0, Math.floor(Number(state.wave) || 0));
    if (wave > record.bestWave) {
      record.bestWave = wave;
      updated = true;
    }
    if (state.difficulty > record.bestDifficulty) {
      record.bestDifficulty = state.difficulty;
      updated = true;
    }
    if (updateTopRunHistory(record, {
      score,
      duration: elapsedSeconds,
      waves: wave
    })) {
      updated = true;
    }
    if (updated) {
      scheduleAutosave();
    }
  }

  function getTimeRewardTickets(elapsedSeconds) {
    if (!Number.isFinite(elapsedSeconds) || elapsedSeconds <= 0) {
      return 0;
    }
    const totalSeconds = Math.max(0, elapsedSeconds);
    let tickets = Math.floor(totalSeconds / 60) * TIME_REWARD_PER_MINUTE;
    TIME_REWARD_MILESTONES.forEach(milestone => {
      if (totalSeconds >= milestone.time) {
        tickets += milestone.bonus;
      }
    });
    return tickets;
  }

  function grantTimeRewardTickets(amount) {
    const tickets = Math.max(0, Math.floor(Number(amount) || 0));
    if (!tickets) {
      return 0;
    }
    const award = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof award !== 'function') {
      return 0;
    }
    try {
      const granted = award(tickets, { unlockTicketStar: true });
      if (Number.isFinite(granted)) {
        return Math.max(0, Math.floor(granted));
      }
    } catch (error) {
      console.warn('Stars War: unable to grant gacha tickets', error);
    }
    return 0;
  }

  function handleGameOver() {
    state.gameOver = true;
    state.running = false;
    state.paused = false;
    state.uiNeedsSync = true;
    state.uiAccumulator = 0;
    updateRecords();
    state.overlayMode = 'gameover';
    updateUi(true);
    state.uiNeedsSync = false;
    const baseMessage = translate('index.sections.starsWar.overlay.gameOver.message', {
      time: formatTime(state.elapsed),
      score: formatNumber(state.score)
    });
    const rewardTarget = getTimeRewardTickets(state.elapsed);
    const grantedTickets = rewardTarget > 0 ? grantTimeRewardTickets(rewardTarget) : 0;
    let overlayMessage = baseMessage;
    if (grantedTickets > 0) {
      const suffix = grantedTickets > 1 ? 's' : '';
      const formattedCount = formatNumber(grantedTickets);
      const rewardText = translate('index.sections.starsWar.overlay.gameOver.reward', {
        count: formattedCount,
        suffix
      });
      if (rewardText) {
        overlayMessage = `${baseMessage}\n${rewardText}`;
        if (typeof showToast === 'function') {
          showToast(rewardText);
        }
        announceStatus('index.sections.starsWar.overlay.gameOver.reward', {
          count: formattedCount,
          suffix
        });
      }
    }
    showOverlay(
      translate('index.sections.starsWar.overlay.gameOver.title'),
      overlayMessage,
      translate('index.sections.starsWar.overlay.retry'),
      {
        titleKey: 'index.sections.starsWar.overlay.gameOver.title',
        primaryKey: 'index.sections.starsWar.overlay.retry'
      }
    );
    flushAutosave();
  }

  function showOverlay(title, message, primaryLabel, options = {}) {
    if (!elements.overlay || !elements.overlayTitle || !elements.overlayMessage || !elements.overlayButton) {
      return;
    }
    const { titleKey, messageKey, primaryKey } = options;
    if (titleKey) {
      elements.overlayTitle.dataset.i18n = titleKey;
    } else {
      delete elements.overlayTitle.dataset.i18n;
    }
    elements.overlayTitle.textContent = title || '';
    if (messageKey) {
      elements.overlayMessage.dataset.i18n = messageKey;
    } else {
      delete elements.overlayMessage.dataset.i18n;
    }
    elements.overlayMessage.textContent = message || '';
    if (primaryKey) {
      elements.overlayButton.dataset.i18n = primaryKey;
    } else {
      delete elements.overlayButton.dataset.i18n;
    }
    elements.overlayButton.textContent = primaryLabel || '';
    elements.overlayButton.hidden = !primaryLabel;
    elements.overlayButton.disabled = !primaryLabel;
    elements.overlay.hidden = false;
    elements.overlay.setAttribute('aria-hidden', 'false');
  }

  function promptForNewRun() {
    state.running = false;
    state.gameOver = false;
    state.paused = false;
    state.overlayMode = 'ready';
    state.accumulator = 0;
    state.uiAccumulator = 0;
    state.uiNeedsSync = true;
    updatePauseButtonState(true);
    updateUi(true);
    state.uiNeedsSync = false;
    showOverlay(
      translate('index.sections.starsWar.overlay.start.title'),
      translate('index.sections.starsWar.overlay.start.message'),
      translate('index.sections.starsWar.overlay.start.action'),
      {
        titleKey: 'index.sections.starsWar.overlay.start.title',
        messageKey: 'index.sections.starsWar.overlay.start.message',
        primaryKey: 'index.sections.starsWar.overlay.start.action'
      }
    );
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
    state.paused = false;
    state.gameOver = false;
    state.overlayMode = 'running';
    const now = typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Date.now();
    state.lastTimestamp = now;
    state.accumulator = 0;
    state.uiAccumulator = 0;
    state.uiNeedsSync = true;
    hideOverlay();
    updatePauseButtonState(true);
    requestAnimationFrame(loop);
  }

  function restartRun(options = {}) {
    const { preserveSeed = false } = options;
    state.running = false;
    state.paused = false;
    hideOverlay();
    flushAutosave();
    const seedValue = preserveSeed && state.rngSeed ? state.rngSeed : randomSeedString();
    applySeed(seedValue);
    state.scriptedSequence = createScriptedSequence();
    resetGame();
    scheduleNextWave();
    startRun();
  }

  function pauseRun() {
    if (!state.running || state.gameOver || state.overlayMode !== 'running') {
      return;
    }
    state.running = false;
    state.paused = true;
    state.overlayMode = 'paused';
    state.lastTimestamp = 0;
    state.accumulator = 0;
    state.uiAccumulator = 0;
    state.uiNeedsSync = true;
    showOverlay(
      translate('index.sections.starsWar.overlay.pause.title'),
      translate('index.sections.starsWar.overlay.pause.message'),
      translate('index.sections.starsWar.overlay.pause.action'),
      {
        titleKey: 'index.sections.starsWar.overlay.pause.title',
        messageKey: 'index.sections.starsWar.overlay.pause.message',
        primaryKey: 'index.sections.starsWar.overlay.pause.action'
      }
    );
    updatePauseButtonState(true);
    updateUi(true);
    state.uiNeedsSync = false;
    announceStatus('index.sections.starsWar.status.paused');
  }

  function resumeRun() {
    if (!state.paused || state.gameOver) {
      return;
    }
    startRun();
    announceStatus('index.sections.starsWar.status.resumed');
  }

  function loop(timestamp) {
    if (!state.running) {
      return;
    }
    if (!state.lastTimestamp) {
      state.lastTimestamp = timestamp;
    }
    const frameDelta = Math.min(MAX_ACCUMULATED_TIME, (timestamp - state.lastTimestamp) / 1000);
    state.lastTimestamp = timestamp;
    state.accumulator = Math.min(state.accumulator + frameDelta, MAX_ACCUMULATED_TIME);
    while (state.accumulator >= SIMULATION_STEP && state.running) {
      update(SIMULATION_STEP);
      state.accumulator -= SIMULATION_STEP;
    }
    render();
    if (state.running) {
      requestAnimationFrame(loop);
    }
  }

  function handleKeyDown(event) {
    const action = KEY_BINDINGS[event.key];
    if (action) {
      inputState[action] = true;
    }
    if (event.code === 'Space' && (state.gameOver || state.overlayMode === 'ready')) {
      restartRun({ preserveSeed: false });
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
    const pointerType = typeof event.pointerType === 'string' ? event.pointerType : 'mouse';
    inputState.pointerActive = true;
    inputState.pointerMode = pointerType === 'touch' ? 'relative' : 'absolute';
    inputState.pointerOriginX = coords.x;
    inputState.pointerOriginY = coords.y;
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
    if (inputState.pointerMode !== 'relative') {
      inputState.pointerOriginX = coords.x;
      inputState.pointerOriginY = coords.y;
    }
  }

  function handlePointerUp() {
    inputState.pointerActive = false;
    inputState.pointerMode = 'absolute';
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
    loadConfig();
    if (elements.overlayButton) {
      elements.overlayButton.addEventListener('click', () => {
        if (state.overlayMode === 'ready' || state.gameOver) {
          restartRun({ preserveSeed: false });
        } else if (state.overlayMode === 'paused' && state.paused && !state.gameOver) {
          resumeRun();
        }
      });
    }
    if (elements.pauseButton) {
      elements.pauseButton.addEventListener('click', () => {
        if (state.paused) {
          resumeRun();
        } else {
          pauseRun();
        }
      });
    }
    if (elements.restartButton) {
      elements.restartButton.addEventListener('click', () => {
        restartRun({ preserveSeed: false });
      });
    }
    if (elements.difficultyButton) {
      elements.difficultyButton.addEventListener('click', () => {
        toggleDifficultyMode();
      });
    }
    const initialSeed = randomSeedString();
    applySeed(initialSeed);
    state.scriptedSequence = createScriptedSequence();
    resetGame();
    scheduleNextWave();
    promptForNewRun();
    updatePauseButtonState(true);
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
    if (state.overlayMode === 'ready') {
      promptForNewRun();
    } else if (!state.running && !state.gameOver && state.overlayMode === 'running') {
      startRun();
    }
  }

  function onLeave() {
    state.running = false;
    state.paused = false;
    if (state.overlayMode === 'running') {
      hideOverlay();
    }
    updatePauseButtonState(true);
    flushAutosave();
  }

  init();

  window.starsWarArcade = {
    onEnter,
    onLeave,
    restart: restartRun,
    pause: pauseRun,
    resume: resumeRun
  };
})();
