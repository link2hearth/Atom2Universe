(() => {
  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const ARCADE_CONFIG = GLOBAL_CONFIG && typeof GLOBAL_CONFIG === 'object' && GLOBAL_CONFIG.arcade
    ? GLOBAL_CONFIG.arcade.particules ?? {}
    : {};

  const readObject = (value, fallback = {}) => (value && typeof value === 'object' ? value : fallback);
  const readNumber = (value, fallback, { min, max, round } = {}) => {
    const numeric = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    let result = numeric;
    if (typeof min === 'number') {
      result = Math.max(min, result);
    }
    if (typeof max === 'number') {
      result = Math.min(max, result);
    }
    if (round === 'floor') {
      result = Math.floor(result);
    } else if (round === 'ceil') {
      result = Math.ceil(result);
    } else if (round === 'round') {
      result = Math.round(result);
    }
    return result;
  };
  const readString = (value, fallback) => {
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed) {
        return trimmed;
      }
    }
    return fallback;
  };
  const readArray = (value, fallback) => (Array.isArray(value) && value.length ? value : fallback);

  const cloneParticle = particle => {
    if (!particle || typeof particle !== 'object') {
      return particle;
    }
    const clone = { ...particle };
    if (Array.isArray(clone.colors)) {
      clone.colors = [...clone.colors];
    }
    if (clone.sprite && typeof clone.sprite === 'object') {
      clone.sprite = { ...clone.sprite };
      if (Array.isArray(clone.sprite.columns)) {
        clone.sprite.columns = [...clone.sprite.columns];
      }
    }
    return clone;
  };
  const cloneVisual = visual => {
    if (!visual || typeof visual !== 'object') {
      return visual;
    }
    return {
      symbol: visual.symbol,
      gradient: Array.isArray(visual.gradient) ? [...visual.gradient] : undefined,
      textColor: visual.textColor,
      glow: visual.glow,
      border: visual.border,
      widthMultiplier: visual.widthMultiplier
    };
  };
  const cloneArrayOfObjects = array => (Array.isArray(array) ? array.map(cloneParticle) : []);

  const FALLBACK_POWER_UP_IDS = {
    extend: 'extend',
    multiball: 'multiball',
    laser: 'laser',
    speed: 'speed',
    floor: 'floor'
  };

  const ENERGY_BALL_SPRITE = (() => {
    if (typeof Image === 'undefined') {
      return {
        image: null,
        loaded: false,
        frameWidth: 32,
        frameHeight: 32,
        trailHeight: 128,
        normalTrailOffsetY: 32,
        speedTrailOffsetY: 160,
        colorCount: 6
      };
    }
    const image = new Image();
    image.src = 'Assets/Sprites/energy_ball.png';
    const sprite = {
      image,
      loaded: false,
      frameWidth: 32,
      frameHeight: 32,
      trailHeight: 128,
      normalTrailOffsetY: 32,
      speedTrailOffsetY: 160,
      colorCount: 6
    };
    const markLoaded = () => {
      sprite.loaded = true;
    };
    if (typeof image.decode === 'function') {
      image.decode().then(markLoaded).catch(() => {
        sprite.loaded = image.complete && image.naturalWidth > 0;
      });
    }
    image.addEventListener('load', markLoaded, { once: true });
    image.addEventListener('error', () => {
      sprite.loaded = false;
    }, { once: true });
    if (image.complete && image.naturalWidth > 0) {
      sprite.loaded = true;
    }
    return sprite;
  })();

  const getRandomEnergyBallVariant = () => {
    const count = Math.max(1, ENERGY_BALL_SPRITE.colorCount || 1);
    return Math.floor(Math.random() * count);
  };

  const createSpriteSheet = ({
    src,
    frameWidth,
    frameHeight,
    columns = 1,
    rows = 1
  }) => {
    const normalizedColumns = Math.max(1, Math.floor(columns));
    const normalizedRows = Math.max(1, Math.floor(rows));
    if (typeof Image === 'undefined') {
      return {
        image: null,
        loaded: false,
        frameWidth,
        frameHeight,
        columns: normalizedColumns,
        rows: normalizedRows
      };
    }
    const image = new Image();
    image.src = src;
    const sheet = {
      image,
      loaded: false,
      frameWidth,
      frameHeight,
      columns: normalizedColumns,
      rows: normalizedRows
    };
    const markLoaded = () => {
      sheet.loaded = true;
    };
    if (typeof image.decode === 'function') {
      image.decode().then(markLoaded).catch(() => {
        sheet.loaded = image.complete && image.naturalWidth > 0;
      });
    }
    image.addEventListener('load', markLoaded, { once: true });
    image.addEventListener('error', () => {
      sheet.loaded = false;
    }, { once: true });
    if (image.complete && image.naturalWidth > 0) {
      sheet.loaded = true;
    }
    return sheet;
  };

  const QUARK_SPRITE_SHEET = createSpriteSheet({
    src: 'Assets/Sprites/quarks.png',
    frameWidth: 64,
    frameHeight: 32,
    columns: 6,
    rows: 3
  });

  const PARTICLE_SPRITE_SHEET = createSpriteSheet({
    src: 'Assets/Sprites/particles.png',
    frameWidth: 64,
    frameHeight: 32,
    columns: 6,
    rows: 3
  });

  const BRICK_SPRITE_SHEETS = {
    quarks: QUARK_SPRITE_SHEET,
    particles: PARTICLE_SPRITE_SHEET
  };

  const FALLBACK_SIMPLE_PARTICLES = [
    {
      id: 'quarkUp',
      family: 'quark',
      quarkColor: 'red',
      colors: ['#ff6c7a', '#ff2d55'],
      symbol: 'u',
      symbolColor: '#fff5f8',
      sprite: { sheet: 'quarks', column: 0 }
    },
    {
      id: 'quarkDown',
      family: 'quark',
      quarkColor: 'green',
      colors: ['#7ef37d', '#2bc84a'],
      symbol: 'd',
      symbolColor: '#03210f',
      sprite: { sheet: 'quarks', column: 1 }
    },
    {
      id: 'quarkStrange',
      family: 'quark',
      quarkColor: 'blue',
      colors: ['#7ac3ff', '#2f82ff'],
      symbol: 's',
      symbolColor: '#021639',
      sprite: { sheet: 'quarks', column: 2 }
    },
    {
      id: 'quarkCharm',
      family: 'quark',
      quarkColor: 'red',
      colors: ['#ffb36c', '#ff7b2d'],
      symbol: 'c',
      symbolColor: '#241002',
      sprite: { sheet: 'quarks', column: 3 }
    },
    {
      id: 'quarkTop',
      family: 'quark',
      quarkColor: 'green',
      colors: ['#a78bff', '#6c4dff'],
      symbol: 't',
      symbolColor: '#160835',
      sprite: { sheet: 'quarks', column: 4 }
    },
    {
      id: 'quarkBottom',
      family: 'quark',
      quarkColor: 'blue',
      colors: ['#6ce7ff', '#2ab3ff'],
      symbol: 'b',
      symbolColor: '#03202c',
      sprite: { sheet: 'quarks', column: 5 }
    }
  ];
  const FALLBACK_RESISTANT_PARTICLES = [
    {
      id: 'higgs',
      family: 'boson',
      colors: ['#ffe680', '#f7c948'],
      symbol: 'H⁰',
      symbolColor: '#4d3100',
      minHits: 3,
      maxHits: 3,
      sprite: { sheet: 'particles', column: 0 }
    },
    {
      id: 'bosonW',
      family: 'boson',
      colors: ['#8ec5ff', '#4b92ff'],
      symbol: 'W',
      symbolColor: '#04193a',
      minHits: 2,
      maxHits: 3,
      sprite: { sheet: 'particles', column: 1 }
    },
    {
      id: 'bosonZ',
      family: 'boson',
      colors: ['#ffd291', '#ffb74b'],
      symbol: 'Z⁰',
      symbolColor: '#3a1b00',
      minHits: 2,
      maxHits: 3,
      sprite: { sheet: 'particles', column: 2 }
    },
    {
      id: 'gluon',
      family: 'boson',
      colors: ['#14151c', '#2c2e36'],
      symbol: 'g',
      symbolColor: '#9fa5ff',
      minHits: 3,
      maxHits: 3,
      sprite: { sheet: 'particles', column: 3 }
    },
    {
      id: 'photon',
      family: 'boson',
      colors: ['#ffd447', '#ffb347'],
      symbol: 'γ',
      symbolColor: '#3e2500',
      minHits: 2,
      maxHits: 2,
      sprite: { sheet: 'particles', column: 4 }
    }
  ];
  const FALLBACK_BONUS_PARTICLES = [
    {
      id: 'positron',
      family: 'lepton',
      colors: ['#f6f6ff', '#dcdcf9'],
      symbol: 'e⁺',
      symbolColor: '#312a5c',
      sprite: { sheet: 'particles', column: 5 }
    },
    {
      id: 'tau',
      family: 'lepton',
      colors: ['#b89bff', '#d9c9ff'],
      symbol: 'τ',
      symbolColor: '#1f0b45',
      sprite: { sheet: 'particles', column: 5 }
    },
    {
      id: 'sterileNeutrino',
      family: 'lepton',
      colors: ['#c3c7d4', '#eff1f6'],
      symbol: 'νₛ',
      symbolColor: '#1f2535',
      sprite: { sheet: 'particles', column: 5 }
    }
  ];
  const FALLBACK_GRAVITON_PARTICLE = {
    id: 'graviton',
    family: 'graviton',
    colors: ['#ff6ec7', '#7afcff', '#ffe45e', '#9d4edd'],
    symbol: 'G*',
    symbolColor: '#ffffff'
  };

  const FALLBACK_POWER_UP_VISUALS = {
    default: {
      symbol: 'P',
      gradient: ['#ffffff', '#a6d8ff'],
      textColor: '#041022',
      glow: 'rgba(140, 210, 255, 0.45)',
      border: 'rgba(255, 255, 255, 0.5)',
      widthMultiplier: 1.45
    },
    extend: {
      symbol: 'L',
      gradient: ['#66f4ff', '#2c9cff'],
      textColor: '#041222',
      glow: 'rgba(110, 220, 255, 0.55)',
      border: 'rgba(255, 255, 255, 0.65)',
      widthMultiplier: 1.5
    },
    multiball: {
      symbol: 'M',
      gradient: ['#ffe066', '#ff7b6b'],
      textColor: '#241104',
      glow: 'rgba(255, 160, 110, 0.55)',
      border: 'rgba(255, 255, 255, 0.6)',
      widthMultiplier: 1.6
    },
    laser: {
      symbol: 'T',
      gradient: ['#ff96c7', '#ff4d9a'],
      textColor: '#36001a',
      glow: 'rgba(255, 120, 190, 0.55)',
      border: 'rgba(255, 255, 255, 0.55)',
      widthMultiplier: 1.45
    },
    speed: {
      symbol: 'S',
      gradient: ['#9d7bff', '#4f3bff'],
      textColor: '#1a083a',
      glow: 'rgba(160, 140, 255, 0.52)',
      border: 'rgba(255, 255, 255, 0.55)',
      widthMultiplier: 1.45
    },
    floor: {
      symbol: 'F',
      gradient: ['#6ef7a6', '#1ec37a'],
      textColor: '#052615',
      glow: 'rgba(90, 240, 180, 0.55)',
      border: 'rgba(255, 255, 255, 0.55)',
      widthMultiplier: 1.55
    }
  };

  const FALLBACK_PATTERN_WEIGHTS = [
    { id: 'organic', weight: 0.25 },
    { id: 'singleGap', weight: 0.1 },
    { id: 'multiGap', weight: 0.1 },
    { id: 'singleBrick', weight: 0.01 },
    { id: 'singleLine', weight: 0.1 },
    { id: 'bottomUniform', weight: 0.1 },
    { id: 'uniformLines', weight: 0.1 },
    { id: 'checkerboard', weight: 0.12 },
    { id: 'diagonals', weight: 0.12 }
  ];

  const settings = {};

  settings.ticketReward = readNumber(ARCADE_CONFIG.ticketReward, 1, { min: 0, round: 'floor' });

  const gridConfig = readObject(ARCADE_CONFIG.grid);
  settings.grid = {
    cols: readNumber(gridConfig.columns ?? gridConfig.cols ?? gridConfig.colonnes, 14, { min: 1, round: 'round' }),
    rows: readNumber(gridConfig.rows ?? gridConfig.lignes ?? gridConfig.rowsCount, 6, { min: 1, round: 'round' })
  };

  settings.maxLives = readNumber(ARCADE_CONFIG.maxLives, 3, { min: 1, round: 'round' });

  const geometryConfig = readObject(ARCADE_CONFIG.geometry);
  settings.geometry = {
    paddingX: readNumber(geometryConfig.paddingX, 0.08),
    paddingY: readNumber(geometryConfig.paddingY, 0.04),
    usableHeight: readNumber(geometryConfig.usableHeight, 0.46),
    innerWidthRatio: readNumber(geometryConfig.innerWidthRatio, 0.92),
    innerHeightRatio: readNumber(geometryConfig.innerHeightRatio, 0.68)
  };

  const paddleConfig = readObject(ARCADE_CONFIG.paddle);
  const paddleExtendConfig = readObject(paddleConfig.extend);
  const paddleBounceConfig = readObject(paddleConfig.bounce);
  settings.paddle = {
    baseWidthRatio: readNumber(paddleConfig.baseWidthRatio, 0.18),
    minWidthRatio: readNumber(paddleConfig.minWidthRatio, 0.12),
    heightRatio: readNumber(paddleConfig.heightRatio, 0.025),
    extendMaxWidthRatio: readNumber(paddleExtendConfig.maxWidthRatio, 0.32),
    extendMultiplier: readNumber(paddleExtendConfig.multiplier, 1.6),
    stretchDurationMs: readNumber(paddleConfig.stretchDurationMs, 620, { min: 0 }),
    bounceDurationMs: readNumber(paddleConfig.bounceDurationMs, 260, { min: 0 }),
    bounceIntensityBase: readNumber(paddleBounceConfig.intensityBase, 0.28),
    bounceMaxHeightRatio: readNumber(paddleBounceConfig.maxHeightRatio, 0.7)
  };

  const ballConfig = readObject(ARCADE_CONFIG.ball);
  const ballTrailConfig = readObject(ballConfig.trail);
  const ballGhostConfig = readObject(ballConfig.ghost);
  settings.ball = {
    radiusRatio: readNumber(ballConfig.radiusRatio, 0.015),
    baseSpeedRatio: readNumber(ballConfig.baseSpeedRatio, 1.9),
    speedGrowthRatio: readNumber(ballConfig.speedGrowthRatio, 0.15),
    minSpeedPerMs: readNumber(ballConfig.minSpeedPerMs, 0.25, { min: 0 }),
    speedFactor: readNumber(ballConfig.speedFactor, 0.0006),
    followOffsetRatio: readNumber(ballConfig.followOffsetRatio, 1.6),
    trailMinDistance: readNumber(ballTrailConfig.minDistance, 2, { min: 0 }),
    trailMinDistanceFactor: readNumber(ballTrailConfig.minDistanceFactor, 0.35),
    trailCaptureIntervalMs: readNumber(ballTrailConfig.captureIntervalMs, 18, { min: 0 }),
    trailMaxPoints: readNumber(ballTrailConfig.maxPoints, 12, { min: 1, round: 'round' }),
    trailPruneMs: readNumber(ballTrailConfig.pruneMs, 260, { min: 0 }),
    ghostIntervalMs: readNumber(ballGhostConfig.intervalMs, 68, { min: 0 }),
    ghostBlurFactor: readNumber(ballGhostConfig.blurFactor, 1.4),
    ghostRemoveDelayMs: readNumber(ballGhostConfig.removeDelayMs, 360, { min: 0 })
  };

  const gravitonConfig = readObject(ARCADE_CONFIG.graviton);
  const gravitonSpawnConfig = readObject(gravitonConfig.spawnChance);
  settings.graviton = {
    lifetimeMs: readNumber(gravitonConfig.lifetimeMs, 10000, { min: 0 }),
    spawnChance: {
      base: readNumber(gravitonSpawnConfig.base, 0.15, { min: 0 }),
      perLevel: readNumber(gravitonSpawnConfig.perLevel, 0.05, { min: 0 }),
      min: readNumber(gravitonSpawnConfig.min, 0.15, { min: 0 }),
      max: readNumber(gravitonSpawnConfig.max, 0.5, { min: 0 })
    },
    detectionMessage: readString(gravitonConfig.detectionMessage, 'Graviton détecté !'),
    detectionMessageDurationMs: readNumber(gravitonConfig.detectionMessageDurationMs, 2600, { min: 0 }),
    dissipateMessage: readString(gravitonConfig.dissipateMessage, 'Le graviton s’est dissipé…'),
    dissipateMessageDurationMs: readNumber(gravitonConfig.dissipateMessageDurationMs, 2800, { min: 0 }),
    captureMessage: readString(gravitonConfig.captureMessage, 'Graviton capturé ! Ticket spécial +1'),
    captureMessageDurationMs: readNumber(gravitonConfig.captureMessageDurationMs, 3600, { min: 0 })
  };

  const combosConfig = readObject(ARCADE_CONFIG.combos);
  const combosShockwaveConfig = readObject(combosConfig.shockwave);
  const combosStagePulseConfig = readObject(combosShockwaveConfig.stagePulse);
  settings.combos = {
    requiredColors: readArray(combosConfig.requiredColors, ['red', 'green', 'blue']),
    powerUpRewards: readArray(combosConfig.powerUpRewards, ['laser', 'extend', 'multiball']),
    chainThreshold: readNumber(combosConfig.chainThreshold, 3, { min: 1, round: 'round' }),
    bonusMessagePrefix: readString(combosConfig.bonusMessagePrefix, 'Bonus: '),
    bonusMessageDurationMs: readNumber(combosConfig.bonusMessageDurationMs, 2400, { min: 0 }),
    quarkMessagePrefix: readString(combosConfig.quarkMessagePrefix, 'Combo quark ! '),
    quarkMessageDurationMs: readNumber(combosConfig.quarkMessageDurationMs, 3200, { min: 0 }),
    chainWindowMs: readNumber(combosConfig.chainWindowMs, 520, { min: 0 }),
    shockwave: {
      baseScale: readNumber(combosShockwaveConfig.baseScale, 1.25),
      scalePerChain: readNumber(combosShockwaveConfig.scalePerChain, 0.08),
      maxScaleChains: readNumber(combosShockwaveConfig.maxScaleChains, 6, { min: 1, round: 'round' }),
      baseOpacity: readNumber(combosShockwaveConfig.baseOpacity, 0.78),
      opacityPerChain: readNumber(combosShockwaveConfig.opacityPerChain, 0.03),
      maxOpacity: readNumber(combosShockwaveConfig.maxOpacity, 0.92),
      removeDelayMs: readNumber(combosShockwaveConfig.removeDelayMs, 640, { min: 0 }),
      stagePulse: {
        baseIntensity: readNumber(combosStagePulseConfig.baseIntensity, 1.02),
        intensityPerChain: readNumber(combosStagePulseConfig.intensityPerChain, 0.005),
        maxChains: readNumber(combosStagePulseConfig.maxChains, 5, { min: 1, round: 'round' }),
        durationMs: readNumber(combosStagePulseConfig.durationMs, 420, { min: 0 })
      }
    }
  };

  const lingerBonusConfig = readObject(
    ARCADE_CONFIG.lingerBonus ?? ARCADE_CONFIG.multiballIdleBonus
  );
  settings.lingerBonus = {
    thresholdMs: readNumber(lingerBonusConfig.thresholdMs, 20000, { min: 0 }),
    intervalMs: readNumber(
      lingerBonusConfig.intervalMs ?? lingerBonusConfig.checkIntervalMs,
      1000,
      { min: 1 }
    ),
    chance: readNumber(lingerBonusConfig.chance, 0.1, { min: 0, max: 1 }),
    message: readString(lingerBonusConfig.message, 'Multiballe bonus !'),
    messageDurationMs: readNumber(lingerBonusConfig.messageDurationMs, 2400, { min: 0 })
  };

  const powerUpConfig = readObject(ARCADE_CONFIG.powerUps);
  const powerUpIdsConfig = readObject(powerUpConfig.ids);
  const resolvedPowerUpIds = {
    extend: readString(powerUpIdsConfig.extend, FALLBACK_POWER_UP_IDS.extend),
    multiball: readString(powerUpIdsConfig.multiball, FALLBACK_POWER_UP_IDS.multiball),
    laser: readString(powerUpIdsConfig.laser, FALLBACK_POWER_UP_IDS.laser),
    speed: readString(powerUpIdsConfig.speed, FALLBACK_POWER_UP_IDS.speed),
    floor: readString(powerUpIdsConfig.floor, FALLBACK_POWER_UP_IDS.floor)
  };
  const labelsConfig = readObject(powerUpConfig.labels);
  const visualsConfig = readObject(powerUpConfig.visuals);
  const effectsConfig = readObject(powerUpConfig.effects);
  const pulseConfig = readObject(powerUpConfig.pulseIntensity);
  const floorShieldConfig = readObject(powerUpConfig.floorShield);
  const mapVisual = key => {
    const base = FALLBACK_POWER_UP_VISUALS[key] ?? FALLBACK_POWER_UP_VISUALS.default;
    const source = readObject(visualsConfig[key]);
    return {
      symbol: readString(source.symbol, base.symbol),
      gradient: readArray(source.gradient, base.gradient),
      textColor: readString(source.textColor, base.textColor),
      glow: readString(source.glow, base.glow),
      border: readString(source.border, base.border),
      widthMultiplier: readNumber(source.widthMultiplier, base.widthMultiplier)
    };
  };
  const laserIntervalMs = readNumber(powerUpConfig.laserIntervalMs, 420, { min: 0 });
  settings.powerUps = {
    fallSpeedRatio: readNumber(powerUpConfig.fallSpeedRatio, 0.00042),
    laserSpeedRatio: readNumber(powerUpConfig.laserSpeedRatio, -0.0026),
    laserIntervalMs,
    ids: resolvedPowerUpIds,
    labels: {
      [resolvedPowerUpIds.extend]: readString(labelsConfig.extend, 'Barre allongée'),
      [resolvedPowerUpIds.multiball]: readString(labelsConfig.multiball, 'Multiballe'),
      [resolvedPowerUpIds.laser]: readString(labelsConfig.laser, 'Tir laser'),
      [resolvedPowerUpIds.speed]: readString(labelsConfig.speed, 'Accélération'),
      [resolvedPowerUpIds.floor]: readString(labelsConfig.floor, 'Bouclier inférieur')
    },
    defaultVisual: mapVisual('default'),
    visuals: {
      [resolvedPowerUpIds.extend]: mapVisual('extend'),
      [resolvedPowerUpIds.multiball]: mapVisual('multiball'),
      [resolvedPowerUpIds.laser]: mapVisual('laser'),
      [resolvedPowerUpIds.speed]: mapVisual('speed'),
      [resolvedPowerUpIds.floor]: mapVisual('floor')
    },
    pulseIntensity: {
      [resolvedPowerUpIds.multiball]: readNumber(pulseConfig.multiball, 1.06),
      [resolvedPowerUpIds.extend]: readNumber(pulseConfig.extend, 1.04),
      [resolvedPowerUpIds.floor]: readNumber(pulseConfig.floor, 1.05)
    },
    effects: {
      [resolvedPowerUpIds.extend]: {
        durationMs: readNumber(readObject(effectsConfig.extend).durationMs, 12000, { min: 0 })
      },
      [resolvedPowerUpIds.multiball]: {
        durationMs: readNumber(readObject(effectsConfig.multiball).durationMs, 0, { min: 0 }) || null
      },
      [resolvedPowerUpIds.laser]: {
        durationMs: readNumber(readObject(effectsConfig.laser).durationMs, 9000, { min: 0 }),
        intervalMs: readNumber(readObject(effectsConfig.laser).intervalMs, laserIntervalMs, { min: 0 })
      },
      [resolvedPowerUpIds.speed]: {
        durationMs: readNumber(readObject(effectsConfig.speed).durationMs, 8000, { min: 0 }),
        speedMultiplier: readNumber(readObject(effectsConfig.speed).speedMultiplier, 1.35)
      },
      [resolvedPowerUpIds.floor]: {
        durationMs: readNumber(readObject(effectsConfig.floor).durationMs, 10000, { min: 0 })
      }
    },
    floorShield: {
      heightRatio: readNumber(floorShieldConfig.heightRatio, 0.06),
      minHeightPx: readNumber(floorShieldConfig.minHeightPx, 12, { min: 0 })
    }
  };

  const bricksConfig = readObject(ARCADE_CONFIG.bricks);
  const brickTypesConfig = readObject(bricksConfig.types);
  const scoreConfig = readObject(bricksConfig.scoreValues);
  const bonusDistributionConfig = readObject(bricksConfig.bonusDistribution);
  const organicConfig = readObject(bricksConfig.organic);
  const singleGapConfig = readObject(bricksConfig.singleGap);
  const multiGapConfig = readObject(bricksConfig.multiGap);
  const uniformConfig = readObject(bricksConfig.uniform);
  const checkerboardConfig = readObject(bricksConfig.checkerboard);
  const diagonalsConfig = readObject(bricksConfig.diagonals);
  const brickTypeWeightsConfig = readObject(bricksConfig.brickTypeWeights);
  const brickTypeWeightsBase = readObject(brickTypeWeightsConfig.base);
  const brickTypeLevelFactor = readObject(brickTypeWeightsConfig.levelFactor);
  settings.bricks = {
    types: {
      simple: readString(brickTypesConfig.simple, 'simple'),
      resistant: readString(brickTypesConfig.resistant, 'resistant'),
      bonus: readString(brickTypesConfig.bonus, 'bonus'),
      graviton: readString(brickTypesConfig.graviton, 'graviton')
    },
    scoreValues: {
      simple: readNumber(scoreConfig.simple, 120, { min: 0 }),
      resistant: readNumber(scoreConfig.resistant, 200, { min: 0 }),
      bonus: readNumber(scoreConfig.bonus, 160, { min: 0 }),
      graviton: readNumber(scoreConfig.graviton, 420, { min: 0 })
    },
    bonusDistribution: {
      targetedPatternRatio: readNumber(bonusDistributionConfig.targetedPatternRatio, 0.32),
      otherPatternRatio: readNumber(bonusDistributionConfig.otherPatternRatio, 0.22),
      perLevelIncrement: readNumber(bonusDistributionConfig.perLevelIncrement, 0.01),
      minRatio: readNumber(bonusDistributionConfig.minRatio, 0.18),
      maxRatio: readNumber(bonusDistributionConfig.maxRatio, 0.42)
    },
    patterns: readArray(bricksConfig.patterns, FALLBACK_PATTERN_WEIGHTS),
    organic: {
      baseFillStart: readNumber(organicConfig.baseFillStart, 0.55),
      baseFillGrowth: readNumber(organicConfig.baseFillGrowth, 0.02),
      minFill: readNumber(organicConfig.minFill, 0.55),
      maxFill: readNumber(organicConfig.maxFill, 0.82),
      depthBiasMax: readNumber(organicConfig.depthBiasMax, 0.18),
      variability: readNumber(organicConfig.variability, 0.12),
      minProbability: readNumber(organicConfig.minProbability, 0.35),
      maxProbability: readNumber(organicConfig.maxProbability, 0.92)
    },
    singleGap: {
      removeRowChance: readNumber(singleGapConfig.removeRowChance, 0.5)
    },
    multiGap: {
      removeRowChance: readNumber(multiGapConfig.removeRowChance, 0.75),
      removeColumnChance: readNumber(multiGapConfig.removeColumnChance, 0.65),
      minEmptyRows: readNumber(multiGapConfig.minEmptyRows, 2, { min: 0, round: 'round' }),
      maxEmptyRows: readNumber(multiGapConfig.maxEmptyRows, 3, { min: 0, round: 'round' }),
      minEmptyCols: readNumber(multiGapConfig.minEmptyCols, 2, { min: 0, round: 'round' }),
      maxEmptyCols: readNumber(multiGapConfig.maxEmptyCols, 4, { min: 0, round: 'round' })
    },
    uniform: {
      fullRowChance: readNumber(uniformConfig.fullRowChance, 0.25)
    },
    checkerboard: {
      extraFillChance: readNumber(checkerboardConfig.extraFillChance, 0.45)
    },
    diagonals: {
      extraFillChance: readNumber(diagonalsConfig.extraFillChance, 0.4)
    },
    brickTypeWeights: {
      base: {
        simple: readNumber(brickTypeWeightsBase.simple, 0.6),
        resistant: readNumber(brickTypeWeightsBase.resistant, 0.26),
        bonus: readNumber(brickTypeWeightsBase.bonus, 0.14)
      },
      levelFactor: {
        step: readNumber(brickTypeLevelFactor.step, 0.015),
        simple: readNumber(brickTypeLevelFactor.simple, -0.25),
        resistant: readNumber(brickTypeLevelFactor.resistant, 0.55),
        bonus: readNumber(brickTypeLevelFactor.bonus, 0.2),
        max: readNumber(brickTypeLevelFactor.max, 0.2)
      }
    }
  };

  const particlesConfig = readObject(ARCADE_CONFIG.particles);
  settings.particles = {
    simple: cloneArrayOfObjects(readArray(particlesConfig.simple, FALLBACK_SIMPLE_PARTICLES)),
    resistant: cloneArrayOfObjects(readArray(particlesConfig.resistant, FALLBACK_RESISTANT_PARTICLES)),
    bonus: cloneArrayOfObjects(readArray(particlesConfig.bonus, FALLBACK_BONUS_PARTICLES)),
    graviton: cloneParticle(readObject(particlesConfig.graviton)) || cloneParticle(FALLBACK_GRAVITON_PARTICLE)
  };

  const uiConfig = readObject(ARCADE_CONFIG.ui);
  const uiStartConfig = readObject(uiConfig.start);
  const uiPauseConfig = readObject(uiConfig.pause);
  const uiLifeLostConfig = readObject(uiConfig.lifeLost);
  const uiLevelClearedConfig = readObject(uiConfig.levelCleared);
  const uiGameOverConfig = readObject(uiConfig.gameOver);
  const uiHudConfig = readObject(uiConfig.hud);
  settings.ui = {
    start: {
      message: readString(uiStartConfig.message, 'Touchez ou cliquez la raquette pour guider la particule et détruire les briques quantiques.'),
      buttonLabel: readString(uiStartConfig.buttonLabel, 'Commencer')
    },
    pause: {
      message: readString(uiPauseConfig.message, 'Partie en pause. Touchez la raquette pour continuer.'),
      buttonLabel: readString(uiPauseConfig.buttonLabel, 'Reprendre')
    },
    lifeLost: {
      message: readString(uiLifeLostConfig.message, 'Particule perdue ! Touchez la raquette pour continuer.'),
      buttonLabel: readString(uiLifeLostConfig.buttonLabel, 'Reprendre')
    },
    levelCleared: {
      template: readString(uiLevelClearedConfig.template, 'Niveau {level} terminé !{reward}'),
      buttonLabel: readString(uiLevelClearedConfig.buttonLabel, 'Continuer'),
      rewardTemplate: readString(uiLevelClearedConfig.rewardTemplate, ' {reward} obtenu !'),
      noReward: readString(uiLevelClearedConfig.noReward, ' Aucun ticket cette fois.')
    },
    gameOver: {
      withTickets: readString(uiGameOverConfig.withTickets, 'Partie terminée ! Tickets gagnés : {tickets}{bonus}.'),
      withoutTickets: readString(uiGameOverConfig.withoutTickets, 'Partie terminée ! Aucun ticket gagné cette fois-ci.'),
      buttonLabel: readString(uiGameOverConfig.buttonLabel, 'Rejouer')
    },
    hud: {
      ticketSingular: readString(uiHudConfig.ticketSingular, 'ticket'),
      ticketPlural: readString(uiHudConfig.ticketPlural, 'tickets'),
      bonusTicketSingular: readString(uiHudConfig.bonusTicketSingular, 'ticket Mach3'),
      bonusTicketPlural: readString(uiHudConfig.bonusTicketPlural, 'tickets Mach3')
    }
  };

  const SETTINGS = settings;

  const ARCADE_TICKET_REWARD = SETTINGS.ticketReward;
  const GRID_COLS = SETTINGS.grid.cols;
  const GRID_ROWS = SETTINGS.grid.rows;
  const MAX_LIVES = SETTINGS.maxLives;
  const BRICK_TYPES = Object.freeze({
    SIMPLE: SETTINGS.bricks.types.simple,
    RESISTANT: SETTINGS.bricks.types.resistant,
    BONUS: SETTINGS.bricks.types.bonus,
    GRAVITON: SETTINGS.bricks.types.graviton
  });
  const POWER_UP_IDS = Object.freeze({
    EXTEND: SETTINGS.powerUps.ids.extend,
    MULTIBALL: SETTINGS.powerUps.ids.multiball,
    LASER: SETTINGS.powerUps.ids.laser,
    SPEED: SETTINGS.powerUps.ids.speed,
    FLOOR: SETTINGS.powerUps.ids.floor
  });
  const COMBO_REQUIRED_COLORS = SETTINGS.combos.requiredColors;
  const BRICK_SCORE_VALUE = {
    [BRICK_TYPES.SIMPLE]: SETTINGS.bricks.scoreValues.simple,
    [BRICK_TYPES.RESISTANT]: SETTINGS.bricks.scoreValues.resistant,
    [BRICK_TYPES.BONUS]: SETTINGS.bricks.scoreValues.bonus,
    [BRICK_TYPES.GRAVITON]: SETTINGS.bricks.scoreValues.graviton
  };
  const GRAVITON_LIFETIME_MS = SETTINGS.graviton.lifetimeMs;
  const POWER_UP_FALL_SPEED_RATIO = SETTINGS.powerUps.fallSpeedRatio;
  const LASER_SPEED_RATIO = SETTINGS.powerUps.laserSpeedRatio;
  const LASER_INTERVAL_MS = SETTINGS.powerUps.laserIntervalMs;
  const PADDLE_STRETCH_DURATION_MS = SETTINGS.paddle.stretchDurationMs;
  const PADDLE_BOUNCE_DURATION_MS = SETTINGS.paddle.bounceDurationMs;
  const PADDLE_BOUNCE_INTENSITY_BASE = SETTINGS.paddle.bounceIntensityBase;
  const PADDLE_BOUNCE_MAX_RATIO = SETTINGS.paddle.bounceMaxHeightRatio;
  const PADDLE_EXTEND_MAX_WIDTH_RATIO = SETTINGS.paddle.extendMaxWidthRatio;
  const PADDLE_EXTEND_MULTIPLIER = SETTINGS.paddle.extendMultiplier;
  const BALL_TRAIL_MIN_DISTANCE = SETTINGS.ball.trailMinDistance;
  const BALL_TRAIL_MIN_DISTANCE_FACTOR = SETTINGS.ball.trailMinDistanceFactor;
  const BALL_TRAIL_CAPTURE_INTERVAL_MS = SETTINGS.ball.trailCaptureIntervalMs;
  const BALL_TRAIL_MAX_POINTS = SETTINGS.ball.trailMaxPoints;
  const BALL_TRAIL_PRUNE_MS = SETTINGS.ball.trailPruneMs;
  const BALL_GHOST_INTERVAL_MS = SETTINGS.ball.ghostIntervalMs;
  const BALL_GHOST_BLUR_FACTOR = SETTINGS.ball.ghostBlurFactor;
  const BALL_GHOST_REMOVE_DELAY_MS = SETTINGS.ball.ghostRemoveDelayMs;
  const BALL_SPEED_FACTOR = SETTINGS.ball.speedFactor;
  const BALL_MIN_SPEED_PER_MS = SETTINGS.ball.minSpeedPerMs;
  const BALL_FOLLOW_OFFSET_RATIO = SETTINGS.ball.followOffsetRatio;
  const DEFAULT_POWER_UP_VISUAL = SETTINGS.powerUps.defaultVisual;
  const POWER_UP_VISUALS = SETTINGS.powerUps.visuals;
  const POWER_UP_LABELS = SETTINGS.powerUps.labels;
  const POWER_UP_PULSE_INTENSITY = SETTINGS.powerUps.pulseIntensity;
  const RAW_POWER_UP_EFFECTS = SETTINGS.powerUps.effects;
  const POWER_UP_EFFECTS = {
    [POWER_UP_IDS.EXTEND]: { duration: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.EXTEND]?.durationMs ?? 12000 },
    [POWER_UP_IDS.MULTIBALL]: { duration: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.MULTIBALL]?.durationMs ?? 0 },
    [POWER_UP_IDS.LASER]: {
      duration: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.LASER]?.durationMs ?? 9000,
      interval: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.LASER]?.intervalMs ?? LASER_INTERVAL_MS
    },
    [POWER_UP_IDS.SPEED]: {
      duration: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.SPEED]?.durationMs ?? 8000,
      speedMultiplier: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.SPEED]?.speedMultiplier ?? 1.35
    },
    [POWER_UP_IDS.FLOOR]: { duration: RAW_POWER_UP_EFFECTS[POWER_UP_IDS.FLOOR]?.durationMs ?? 10000 }
  };
  const POWER_UP_SPEED_MULTIPLIER = POWER_UP_EFFECTS[POWER_UP_IDS.SPEED].speedMultiplier ?? 1.35;
  const COMBO_POWER_UPS = SETTINGS.combos.powerUpRewards
    .map(id => Object.values(POWER_UP_IDS).includes(id) ? id : null)
    .filter(Boolean);
  const COMBO_BONUS_PREFIX = SETTINGS.combos.bonusMessagePrefix;
  const COMBO_BONUS_DURATION = SETTINGS.combos.bonusMessageDurationMs;
  const COMBO_QUARK_PREFIX = SETTINGS.combos.quarkMessagePrefix;
  const COMBO_QUARK_DURATION = SETTINGS.combos.quarkMessageDurationMs;
  const COMBO_CHAIN_WINDOW_MS = SETTINGS.combos.chainWindowMs;
  const COMBO_CHAIN_THRESHOLD = SETTINGS.combos.chainThreshold;
  const COMBO_SHOCKWAVE = SETTINGS.combos.shockwave;
  const FLOOR_SHIELD_CONFIG = SETTINGS.powerUps.floorShield;
  const BONUS_DISTRIBUTION = SETTINGS.bricks.bonusDistribution;
  const PATTERN_WEIGHTS = SETTINGS.bricks.patterns;
  const ORGANIC_CONFIG = SETTINGS.bricks.organic;
  const SINGLE_GAP_CONFIG = SETTINGS.bricks.singleGap;
  const MULTI_GAP_CONFIG = SETTINGS.bricks.multiGap;
  const UNIFORM_CONFIG = SETTINGS.bricks.uniform;
  const CHECKERBOARD_CONFIG = SETTINGS.bricks.checkerboard;
  const DIAGONALS_CONFIG = SETTINGS.bricks.diagonals;
  const BRICK_TYPE_WEIGHTS = SETTINGS.bricks.brickTypeWeights;

  const SIMPLE_PARTICLES = SETTINGS.particles.simple;
  const RESISTANT_PARTICLES = SETTINGS.particles.resistant;
  const BONUS_PARTICLES = SETTINGS.particles.bonus;
  const GRAVITON_PARTICLE = SETTINGS.particles.graviton;

  const LINGER_BONUS_CONFIG = SETTINGS.lingerBonus;
  const LEVEL_LINGER_THRESHOLD_MS = LINGER_BONUS_CONFIG.thresholdMs;
  const LEVEL_LINGER_INTERVAL_MS = LINGER_BONUS_CONFIG.intervalMs;
  const LEVEL_LINGER_CHANCE = LINGER_BONUS_CONFIG.chance;
  const LEVEL_LINGER_MESSAGE = LINGER_BONUS_CONFIG.message;
  const LEVEL_LINGER_MESSAGE_DURATION = LINGER_BONUS_CONFIG.messageDurationMs;

  const START_OVERLAY_MESSAGE = SETTINGS.ui.start.message;
  const START_OVERLAY_BUTTON = SETTINGS.ui.start.buttonLabel;
  const PAUSE_OVERLAY_MESSAGE = SETTINGS.ui.pause.message;
  const PAUSE_OVERLAY_BUTTON = SETTINGS.ui.pause.buttonLabel;
  const LIFE_LOST_MESSAGE = SETTINGS.ui.lifeLost.message;
  const LIFE_LOST_BUTTON = SETTINGS.ui.lifeLost.buttonLabel;
  const LEVEL_CLEARED_TEMPLATE = SETTINGS.ui.levelCleared.template;
  const LEVEL_CLEARED_BUTTON = SETTINGS.ui.levelCleared.buttonLabel;
  const LEVEL_CLEARED_REWARD_TEMPLATE = SETTINGS.ui.levelCleared.rewardTemplate;
  const LEVEL_CLEARED_NO_REWARD = SETTINGS.ui.levelCleared.noReward;
  const GAME_OVER_WITH_TICKETS = SETTINGS.ui.gameOver.withTickets;
  const GAME_OVER_WITHOUT_TICKETS = SETTINGS.ui.gameOver.withoutTickets;
  const GAME_OVER_BUTTON = SETTINGS.ui.gameOver.buttonLabel;
  const HUD_TICKET_SINGULAR = SETTINGS.ui.hud.ticketSingular;
  const HUD_TICKET_PLURAL = SETTINGS.ui.hud.ticketPlural;
  const HUD_BONUS_TICKET_SINGULAR = SETTINGS.ui.hud.bonusTicketSingular;
  const HUD_BONUS_TICKET_PLURAL = SETTINGS.ui.hud.bonusTicketPlural;
  const GRAVITON_DETECTION_MESSAGE = SETTINGS.graviton.detectionMessage;
  const GRAVITON_DETECTION_DURATION = SETTINGS.graviton.detectionMessageDurationMs;
  const GRAVITON_DISSIPATE_MESSAGE = SETTINGS.graviton.dissipateMessage;
  const GRAVITON_DISSIPATE_DURATION = SETTINGS.graviton.dissipateMessageDurationMs;
  const GRAVITON_CAPTURE_MESSAGE = SETTINGS.graviton.captureMessage;
  const GRAVITON_CAPTURE_DURATION = SETTINGS.graviton.captureMessageDurationMs;
  const createCubicBezierEasing = (p1x, p1y, p2x, p2y) => {
    const cx = 3 * p1x;
    const bx = 3 * (p2x - p1x) - cx;
    const ax = 1 - cx - bx;
    const cy = 3 * p1y;
    const by = 3 * (p2y - p1y) - cy;
    const ay = 1 - cy - by;
    const sampleCurveX = t => ((ax * t + bx) * t + cx) * t;
    const sampleCurveY = t => ((ay * t + by) * t + cy) * t;
    const sampleDerivativeX = t => (3 * ax * t + 2 * bx) * t + cx;
    const solveCurveX = x => {
      let t2 = x;
      for (let i = 0; i < 8; i += 1) {
        const x2 = sampleCurveX(t2) - x;
        if (Math.abs(x2) < 1e-6) {
          return t2;
        }
        const d2 = sampleDerivativeX(t2);
        if (Math.abs(d2) < 1e-6) {
          break;
        }
        t2 -= x2 / d2;
      }
      let t0 = 0;
      let t1 = 1;
      t2 = x;
      for (let i = 0; i < 8; i += 1) {
        const x2 = sampleCurveX(t2);
        if (Math.abs(x2 - x) < 1e-6) {
          return t2;
        }
        if (x > x2) {
          t0 = t2;
        } else {
          t1 = t2;
        }
        t2 = (t0 + t1) / 2;
      }
      return t2;
    };
    return progress => {
      if (progress <= 0) return 0;
      if (progress >= 1) return 1;
      const parameter = solveCurveX(progress);
      return sampleCurveY(parameter);
    };
  };

  const PADDLE_STRETCH_EASING = createCubicBezierEasing(0.34, 1.56, 0.64, 1);

  const defaultTicketFormatter = value => {
    const numeric = Math.max(0, Math.floor(Number(value) || 0));
    const unit = numeric === 1 ? HUD_TICKET_SINGULAR : HUD_TICKET_PLURAL;
    return `${numeric.toLocaleString('fr-FR')} ${unit}`;
  };

  const defaultBonusTicketFormatter = value => {
    const numeric = Math.max(0, Math.floor(Number(value) || 0));
    const unit = numeric === 1 ? HUD_BONUS_TICKET_SINGULAR : HUD_BONUS_TICKET_PLURAL;
    return `${numeric.toLocaleString('fr-FR')} ${unit}`;
  };

  const randomChoice = list => (Array.isArray(list) && list.length > 0
    ? list[Math.floor(Math.random() * list.length)]
    : null);

  const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

  class ParticulesGame {
    constructor(options = {}) {
      const {
        canvas,
        overlay,
        overlayButton,
        overlayMessage,
        particleLayer,
        levelLabel,
        livesLabel,
        scoreLabel,
        comboLabel,
        onTicketsEarned,
        onSpecialTicket,
        formatTicketLabel,
        formatBonusTicketLabel
      } = options;

      this.canvas = canvas;
      this.overlay = overlay;
      this.overlayButton = overlayButton;
      this.overlayMessage = overlayMessage;
      const hasHTMLElement = typeof HTMLElement !== 'undefined';
      this.particleLayer = hasHTMLElement && particleLayer instanceof HTMLElement ? particleLayer : null;
      this.comboLabel = comboLabel;
      this.stage = hasHTMLElement && canvas instanceof HTMLElement
        ? canvas.closest('.arcade-stage')
        : null;
      [levelLabel, livesLabel, scoreLabel].forEach(label => {
        const container = typeof label?.closest === 'function'
          ? label.closest('.arcade-hud__item')
          : null;
        if (container) {
          container.hidden = true;
          container.setAttribute('aria-hidden', 'true');
        }
      });
      this.levelLabel = null;
      this.livesLabel = null;
      this.scoreLabel = null;
      this.onTicketsEarned = typeof onTicketsEarned === 'function' ? onTicketsEarned : null;
      this.onSpecialTicket = typeof onSpecialTicket === 'function' ? onSpecialTicket : null;
      this.formatTicketLabel = typeof formatTicketLabel === 'function' ? formatTicketLabel : defaultTicketFormatter;
      this.formatBonusTicketLabel = typeof formatBonusTicketLabel === 'function'
        ? formatBonusTicketLabel
        : defaultBonusTicketFormatter;

      if (!this.canvas || !this.canvas.getContext) {
        this.enabled = false;
        return;
      }

      const context = this.canvas.getContext('2d');
      if (!context) {
        this.enabled = false;
        return;
      }

      this.ctx = context;
      this.enabled = true;
      this.gridCols = GRID_COLS;
      this.gridRows = GRID_ROWS;
      this.maxLives = MAX_LIVES;
      this.level = 1;
      this.lives = this.maxLives;
      this.score = 0;
      this.ticketsEarned = 0;
      this.specialTicketsEarned = 0;
      this.pendingLevelAdvance = false;
      this.pointerActive = false;
      this.pendingResume = false;
      this.running = false;
      this.lastTimestamp = 0;
      this.animationFrameId = null;
      this.width = 0;
      this.height = 0;
      this.pixelRatio = 1;
      this.effects = new Map();
      this.bricks = [];
      this.powerUps = [];
      this.lasers = [];
      this.balls = [];
      this.ballIdCounter = 0;
      this.quarkComboColors = new Set();
      this.comboMessage = '';
      this.comboMessageExpiry = 0;
      this.ballSpeedMultiplier = 1;
      this.gravitonLifetimeMs = GRAVITON_LIFETIME_MS;
      this.paddleStretchAnimation = null;
      this.paddleBounceAnimation = null;
      this.comboChainCount = 0;
      this.lastBrickDestroyedAt = 0;
      this.stagePulseTimeout = null;
      this.levelStartedAt = 0;
      this.lastLingerBonusCheckAt = 0;

      this.paddle = {
        baseWidthRatio: SETTINGS.paddle.baseWidthRatio,
        currentWidthRatio: SETTINGS.paddle.baseWidthRatio,
        minWidthRatio: SETTINGS.paddle.minWidthRatio,
        heightRatio: SETTINGS.paddle.heightRatio,
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        xRatio: 0.5
      };

      this.ballSettings = {
        radiusRatio: SETTINGS.ball.radiusRatio,
        baseSpeedRatio: SETTINGS.ball.baseSpeedRatio,
        speedGrowthRatio: SETTINGS.ball.speedGrowthRatio
      };

      this.handleFrame = this.handleFrame.bind(this);
      this.handlePointerDown = this.handlePointerDown.bind(this);
      this.handlePointerMove = this.handlePointerMove.bind(this);
      this.handlePointerUp = this.handlePointerUp.bind(this);
      this.handleOverlayButtonClick = this.handleOverlayButtonClick.bind(this);
      this.handleResize = this.handleResize.bind(this);

      this.canvas.addEventListener('pointerdown', this.handlePointerDown);
      this.canvas.addEventListener('pointermove', this.handlePointerMove);
      this.canvas.addEventListener('pointerup', this.handlePointerUp);
      this.canvas.addEventListener('pointerleave', this.handlePointerUp);
      this.canvas.addEventListener('pointercancel', this.handlePointerUp);

      if (this.overlayButton) {
        this.overlayButton.addEventListener('click', this.handleOverlayButtonClick);
      }

      if (typeof window !== 'undefined') {
        window.addEventListener('resize', this.handleResize);
      }

      this.handleResize();
      this.setupLevel();
      this.showOverlay({
        message:
          (this.overlayMessage?.textContent || '').trim()
          || START_OVERLAY_MESSAGE,
        buttonLabel: START_OVERLAY_BUTTON,
        action: 'start'
      });
    }

    dispose() {
      if (!this.enabled) return;
      this.stopAnimation();
      this.canvas.removeEventListener('pointerdown', this.handlePointerDown);
      this.canvas.removeEventListener('pointermove', this.handlePointerMove);
      this.canvas.removeEventListener('pointerup', this.handlePointerUp);
      this.canvas.removeEventListener('pointerleave', this.handlePointerUp);
      this.canvas.removeEventListener('pointercancel', this.handlePointerUp);
      if (this.overlayButton) {
        this.overlayButton.removeEventListener('click', this.handleOverlayButtonClick);
      }
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.handleResize);
      }
      if (this.stagePulseTimeout) {
        clearTimeout(this.stagePulseTimeout);
        this.stagePulseTimeout = null;
      }
      this.paddleBounceAnimation = null;
      if (this.stage && typeof this.stage.classList?.remove === 'function') {
        this.stage.classList.remove('arcade-stage--pulse');
      }
      this.clearImpactParticles();
      this.enabled = false;
    }

    handleResize() {
      if (!this.enabled) return;
      const rect = this.canvas.getBoundingClientRect();
      const width = Math.max(1, rect.width);
      const height = Math.max(1, rect.height);
      const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
      const targetWidth = Math.round(width * dpr);
      const targetHeight = Math.round(height * dpr);
      if (this.canvas.width !== targetWidth || this.canvas.height !== targetHeight) {
        this.canvas.width = targetWidth;
        this.canvas.height = targetHeight;
      }
      this.width = targetWidth;
      this.height = targetHeight;
      this.pixelRatio = dpr;
      this.updatePaddleSize();
      this.updateBallRadius();
      this.balls.forEach(ball => {
        ball.radius = this.ballRadius;
        if (ball.attachedToPaddle) {
          this.updateBallFollowingPaddle(ball);
        }
      });
      this.updateHud();
      this.render();
    }

    updatePaddleSize() {
      const ratio = Math.max(this.paddle.minWidthRatio, this.paddle.currentWidthRatio);
      this.paddle.width = Math.max(40, ratio * this.width);
      this.paddle.height = Math.max(10, this.paddle.heightRatio * this.height);
      this.paddle.y = this.height - this.paddle.height * 3;
      this.paddle.x = clamp(this.paddle.xRatio * this.width - this.paddle.width / 2, 0, Math.max(0, this.width - this.paddle.width));
    }

    triggerPaddleStretchAnimation(previousWidth, newWidth) {
      const nextWidth = Number.isFinite(newWidth) ? newWidth : 0;
      const priorWidth = Number.isFinite(previousWidth) ? previousWidth : 0;
      if (nextWidth <= 0 || priorWidth <= 0 || Math.abs(nextWidth - priorWidth) < 0.5) {
        return;
      }
      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      const fromScale = clamp(priorWidth / nextWidth, 0, 1.6);
      this.paddleStretchAnimation = {
        start: now,
        duration: PADDLE_STRETCH_DURATION_MS,
        from: fromScale,
        to: 1
      };
      if (nextWidth > priorWidth) {
        const growthRatio = clamp((nextWidth - priorWidth) / Math.max(priorWidth, 1), 0.2, 1.6);
        this.triggerPaddleBounce(0.8 + growthRatio * 0.6);
      }
      this.startAnimation();
    }

    getPaddleStretchScale(renderTimestamp) {
      const state = this.paddleStretchAnimation;
      if (!state) {
        return 1;
      }
      const duration = state.duration > 0 ? state.duration : PADDLE_STRETCH_DURATION_MS;
      const elapsed = Number.isFinite(renderTimestamp) ? renderTimestamp - state.start : 0;
      if (!Number.isFinite(elapsed)) {
        return 1;
      }
      if (elapsed <= 0) {
        return state.from;
      }
      if (elapsed >= duration) {
        this.paddleStretchAnimation = null;
        return state.to;
      }
      const progress = clamp(elapsed / duration, 0, 1);
      const eased = PADDLE_STRETCH_EASING(progress);
      return state.from + (state.to - state.from) * eased;
    }

    triggerPaddleBounce(intensity = 1) {
      const paddleHeight = this.paddle?.height || 0;
      if (paddleHeight <= 0) {
        return;
      }
      const normalizedIntensity = clamp(intensity, 0.1, 2.2);
      const amplitude = clamp(
        paddleHeight * PADDLE_BOUNCE_INTENSITY_BASE * normalizedIntensity,
        0,
        paddleHeight * PADDLE_BOUNCE_MAX_RATIO
      );
      if (amplitude <= 0) {
        return;
      }
      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      const current = this.paddleBounceAnimation;
      const nextAmplitude = Math.max(amplitude, current?.amplitude || 0);
      this.paddleBounceAnimation = {
        start: now,
        duration: PADDLE_BOUNCE_DURATION_MS,
        amplitude: nextAmplitude
      };
      this.startAnimation();
    }

    getPaddleBounceOffset(renderTimestamp) {
      const state = this.paddleBounceAnimation;
      if (!state) {
        return 0;
      }
      const duration = state.duration > 0 ? state.duration : PADDLE_BOUNCE_DURATION_MS;
      const elapsed = Number.isFinite(renderTimestamp) ? renderTimestamp - state.start : 0;
      if (!Number.isFinite(elapsed) || elapsed < 0) {
        return 0;
      }
      if (elapsed >= duration) {
        this.paddleBounceAnimation = null;
        return 0;
      }
      const progress = clamp(elapsed / duration, 0, 1);
      const wave = Math.sin(progress * Math.PI);
      return state.amplitude * wave;
    }

    updateBallRadius() {
      this.ballRadius = Math.max(6, this.ballSettings.radiusRatio * Math.min(this.width, this.height));
    }

    setupLevel() {
      if (!this.enabled) return;
      this.pendingLevelAdvance = false;
      this.levelStartedAt = typeof performance !== 'undefined' ? performance.now() : Date.now();
      this.lastLingerBonusCheckAt = 0;
      this.effects.clear();
      this.powerUps = [];
      this.lasers = [];
      this.balls = [];
      this.clearImpactParticles();
      this.quarkComboColors.clear();
      this.comboMessage = '';
      this.comboMessageExpiry = 0;
      this.ballSpeedMultiplier = 1;
      this.paddleBounceAnimation = null;
      this.lives = this.maxLives;
      this.bricks = this.generateBricks();
      this.resetComboChain();
      this.prepareServe();
      this.updateHud();
      this.render();
    }

    resetComboChain() {
      this.comboChainCount = 0;
      this.lastBrickDestroyedAt = 0;
    }

    clearImpactParticles() {
      if (!this.particleLayer) return;
      while (this.particleLayer.firstChild) {
        this.particleLayer.removeChild(this.particleLayer.firstChild);
      }
    }

    generateBricks() {
      const geometry = this.getGridGeometry();
      const pattern = this.pickLevelPattern();
      const generators = {
        organic: () => this.generateOrganicLayout(geometry),
        singleGap: () => this.generateSingleGapLayout(geometry),
        multiGap: () => this.generateMultiGapLayout(geometry),
        singleBrick: () => this.generateSingleBrickLayout(geometry),
        singleLine: () => this.generateSingleLineLayout(geometry),
        bottomUniform: () => this.generateBottomUniformLayout(geometry),
        uniformLines: () => this.generateUniformLinesLayout(geometry),
        checkerboard: () => this.generateCheckerboardLayout(geometry),
        diagonals: () => this.generateDiagonalLayout(geometry)
      };
      const generator = generators[pattern] || generators.organic;
      const bricks = generator();
      this.ensureBonusDistribution(bricks, pattern);
      return this.maybeAddGraviton(bricks);
    }

    getGridGeometry() {
      const paddingX = SETTINGS.geometry.paddingX;
      const paddingY = SETTINGS.geometry.paddingY;
      const usableWidth = 1 - paddingX * 2;
      const usableHeight = SETTINGS.geometry.usableHeight;
      const brickWidth = usableWidth / this.gridCols;
      const brickHeight = usableHeight / this.gridRows;
      const innerWidthRatio = SETTINGS.geometry.innerWidthRatio;
      const innerHeightRatio = SETTINGS.geometry.innerHeightRatio;
      const horizontalOffset = (1 - innerWidthRatio) / 2;
      const verticalOffset = (1 - innerHeightRatio) / 2;
      return {
        paddingX,
        paddingY,
        brickWidth,
        brickHeight,
        innerWidthRatio,
        innerHeightRatio,
        horizontalOffset,
        verticalOffset
      };
    }

    getCellPosition(row, col, geometry) {
      const {
        paddingX,
        paddingY,
        brickWidth,
        brickHeight,
        innerWidthRatio,
        innerHeightRatio,
        horizontalOffset,
        verticalOffset
      } = geometry;
      return {
        row,
        col,
        relX: paddingX + col * brickWidth + brickWidth * horizontalOffset,
        relY: paddingY + row * brickHeight + brickHeight * verticalOffset,
        relWidth: brickWidth * innerWidthRatio,
        relHeight: brickHeight * innerHeightRatio
      };
    }

    placeBrick(bricks, row, col, geometry, type = null, particle = null) {
      const brickType = type || this.pickBrickType();
      const brickParticle = particle || this.pickParticle(brickType);
      bricks.push(this.createBrick({
        ...this.getCellPosition(row, col, geometry),
        type: brickType,
        particle: brickParticle
      }));
    }

    ensureBonusDistribution(bricks, pattern) {
      if (!Array.isArray(bricks) || bricks.length === 0) {
        return bricks;
      }
      const eligible = bricks.filter(brick => brick.type !== BRICK_TYPES.GRAVITON);
      if (eligible.length === 0) {
        return bricks;
      }
      const existingBonus = eligible.filter(brick => brick.type === BRICK_TYPES.BONUS).length;
      const targetedPatterns = new Set(['singleLine', 'uniformLines', 'diagonals']);
      const patternBoost = targetedPatterns.has(pattern)
        ? BONUS_DISTRIBUTION.targetedPatternRatio
        : BONUS_DISTRIBUTION.otherPatternRatio;
      const desiredRatio = clamp(
        patternBoost + (this.level - 1) * BONUS_DISTRIBUTION.perLevelIncrement,
        BONUS_DISTRIBUTION.minRatio,
        BONUS_DISTRIBUTION.maxRatio
      );
      const desiredCount = Math.max(1, Math.round(eligible.length * desiredRatio));
      if (existingBonus >= desiredCount) {
        return bricks;
      }
      const candidates = eligible
        .filter(brick => brick.type !== BRICK_TYPES.BONUS)
        .sort(() => Math.random() - 0.5);
      const upgradesNeeded = Math.min(desiredCount - existingBonus, candidates.length);
      for (let index = 0; index < upgradesNeeded; index += 1) {
        const target = candidates[index];
        if (!target) continue;
        const bonusBrick = this.createBrick({
          row: target.row,
          col: target.col,
          relX: target.relX,
          relY: target.relY,
          relWidth: target.relWidth,
          relHeight: target.relHeight,
          type: BRICK_TYPES.BONUS,
          particle: this.pickParticle(BRICK_TYPES.BONUS)
        });
        Object.assign(target, bonusBrick);
      }
      return bricks;
    }

    pickLevelPattern() {
      const weights = PATTERN_WEIGHTS;
      const total = weights.reduce((sum, entry) => sum + entry.weight, 0);
      const roll = Math.random() * total;
      let cumulative = 0;
      for (const entry of weights) {
        cumulative += entry.weight;
        if (roll <= cumulative) {
          return entry.id;
        }
      }
      return 'organic';
    }

    generateOrganicLayout(geometry) {
      const bricks = [];
      const baseFill = clamp(
        ORGANIC_CONFIG.baseFillStart + (this.level - 1) * ORGANIC_CONFIG.baseFillGrowth,
        ORGANIC_CONFIG.minFill,
        ORGANIC_CONFIG.maxFill
      );
      for (let row = 0; row < this.gridRows; row += 1) {
        let rowHasBrick = false;
        const candidates = [];
        for (let col = 0; col < this.gridCols; col += 1) {
          const position = this.getCellPosition(row, col, geometry);
          const variability = (Math.random() - 0.5) * ORGANIC_CONFIG.variability;
          const depthBias = clamp(
            (row / Math.max(1, this.gridRows - 1)) * ORGANIC_CONFIG.depthBiasMax,
            0,
            ORGANIC_CONFIG.depthBiasMax
          );
          const fillProbability = clamp(
            baseFill + depthBias + variability,
            ORGANIC_CONFIG.minProbability,
            ORGANIC_CONFIG.maxProbability
          );
          if (Math.random() > fillProbability) {
            candidates.push(position);
            continue;
          }
          const type = this.pickBrickType();
          const particle = this.pickParticle(type);
          bricks.push(this.createBrick({ ...position, type, particle }));
          rowHasBrick = true;
        }
        if (!rowHasBrick && candidates.length > 0) {
          const forced = randomChoice(candidates);
          const type = this.pickBrickType();
          const particle = this.pickParticle(type);
          bricks.push(this.createBrick({ ...forced, type, particle }));
        }
      }
      return bricks;
    }

    generateSingleGapLayout(geometry) {
      const bricks = [];
      const removeRow = Math.random() < SINGLE_GAP_CONFIG.removeRowChance;
      const gapIndex = removeRow
        ? Math.floor(Math.random() * this.gridRows)
        : Math.floor(Math.random() * this.gridCols);
      for (let row = 0; row < this.gridRows; row += 1) {
        for (let col = 0; col < this.gridCols; col += 1) {
          if ((removeRow && row === gapIndex) || (!removeRow && col === gapIndex)) {
            continue;
          }
          this.placeBrick(bricks, row, col, geometry);
        }
      }
      return bricks;
    }

    generateMultiGapLayout(geometry) {
      const bricks = [];
      let removeRows = Math.random() < MULTI_GAP_CONFIG.removeRowChance;
      let removeCols = Math.random() < MULTI_GAP_CONFIG.removeColumnChance;
      if (!removeRows && !removeCols) {
        removeRows = true;
      }
      const emptyRows = new Set();
      const emptyCols = new Set();
      if (removeRows) {
        const maxRows = Math.max(1, Math.min(MULTI_GAP_CONFIG.maxEmptyRows, this.gridRows - 1));
        const minRows = Math.max(1, Math.min(MULTI_GAP_CONFIG.minEmptyRows, maxRows));
        const rangeRows = Math.max(0, maxRows - minRows);
        const emptyRowCount = minRows + (rangeRows > 0 ? Math.floor(Math.random() * (rangeRows + 1)) : 0);
        while (emptyRows.size < emptyRowCount) {
          emptyRows.add(Math.floor(Math.random() * this.gridRows));
        }
      }
      if (removeCols) {
        const maxCols = Math.max(1, Math.min(MULTI_GAP_CONFIG.maxEmptyCols, this.gridCols - 1));
        const minCols = Math.max(1, Math.min(MULTI_GAP_CONFIG.minEmptyCols, maxCols));
        const rangeCols = Math.max(0, maxCols - minCols);
        const emptyColCount = minCols + (rangeCols > 0 ? Math.floor(Math.random() * (rangeCols + 1)) : 0);
        while (emptyCols.size < emptyColCount) {
          emptyCols.add(Math.floor(Math.random() * this.gridCols));
        }
      }
      for (let row = 0; row < this.gridRows; row += 1) {
        if (emptyRows.has(row)) continue;
        for (let col = 0; col < this.gridCols; col += 1) {
          if (emptyCols.has(col)) continue;
          this.placeBrick(bricks, row, col, geometry);
        }
      }
      if (bricks.length === 0) {
        this.placeBrick(bricks, 0, 0, geometry);
      }
      return bricks;
    }

    generateSingleBrickLayout(geometry) {
      const bricks = [];
      const row = Math.floor(Math.random() * this.gridRows);
      const col = Math.floor(Math.random() * this.gridCols);
      const particle = RESISTANT_PARTICLES.find(p => (p.minHits || 1) >= 3) || RESISTANT_PARTICLES[0];
      bricks.push(this.createBrick({
        ...this.getCellPosition(row, col, geometry),
        type: BRICK_TYPES.RESISTANT,
        particle
      }));
      return bricks;
    }

    generateSingleLineLayout(geometry) {
      const bricks = [];
      const horizontal = Math.random() < 0.5;
      if (horizontal) {
        const targetRow = Math.floor(Math.random() * this.gridRows);
        for (let col = 0; col < this.gridCols; col += 1) {
          this.placeBrick(bricks, targetRow, col, geometry, BRICK_TYPES.BONUS);
        }
      } else {
        const targetCol = Math.floor(Math.random() * this.gridCols);
        for (let row = 0; row < this.gridRows; row += 1) {
          this.placeBrick(bricks, row, targetCol, geometry, BRICK_TYPES.BONUS);
        }
      }
      return bricks;
    }

    generateBottomUniformLayout(geometry) {
      const bricks = this.generateOrganicLayout(geometry);
      const bottomRow = this.gridRows - 1;
      const type = this.pickBrickType();
      const particle = this.pickParticle(type);
      for (let col = 0; col < this.gridCols; col += 1) {
        const existingIndex = bricks.findIndex(brick => brick.row === bottomRow && brick.col === col);
        const brick = this.createBrick({
          ...this.getCellPosition(bottomRow, col, geometry),
          type,
          particle
        });
        if (existingIndex >= 0) {
          bricks[existingIndex] = brick;
        } else {
          bricks.push(brick);
        }
      }
      return bricks;
    }

    generateUniformLinesLayout(geometry) {
      const bricks = [];
      const type = BRICK_TYPES.BONUS;
      const uniformRows = Math.random() < 0.5 ? this.pickUniformRows() : [];
      const uniformCols = uniformRows.length > 0 ? [] : this.pickUniformCols();
      if (uniformRows.length === this.gridRows) {
        for (let row = 0; row < this.gridRows; row += 1) {
          for (let col = 0; col < this.gridCols; col += 1) {
            this.placeBrick(bricks, row, col, geometry, type);
          }
        }
        return bricks;
      }
      const fallbackType = this.pickBrickType();
      for (let row = 0; row < this.gridRows; row += 1) {
        for (let col = 0; col < this.gridCols; col += 1) {
          const isUniform = uniformRows.includes(row) || uniformCols.includes(col);
          if (isUniform) {
            this.placeBrick(bricks, row, col, geometry, type);
          } else if (Math.random() < 0.7) {
            this.placeBrick(bricks, row, col, geometry, fallbackType);
          }
        }
      }
      if (bricks.length === 0) {
        for (let col = 0; col < this.gridCols; col += 1) {
          this.placeBrick(bricks, 0, col, geometry, type);
        }
      }
      return bricks;
    }

    pickUniformRows() {
      if (Math.random() < UNIFORM_CONFIG.fullRowChance) {
        return Array.from({ length: this.gridRows }, (_, index) => index);
      }
      const rowCount = Math.max(2, Math.floor(Math.random() * Math.max(2, this.gridRows - 1)) + 1);
      const rows = new Set();
      while (rows.size < rowCount) {
        rows.add(Math.floor(Math.random() * this.gridRows));
      }
      return [...rows];
    }

    pickUniformCols() {
      const colCount = Math.max(2, Math.floor(Math.random() * Math.max(2, this.gridCols - 1)) + 1);
      const cols = new Set();
      while (cols.size < colCount) {
        cols.add(Math.floor(Math.random() * this.gridCols));
      }
      return [...cols];
    }

    generateCheckerboardLayout(geometry) {
      const bricks = [];
      const type = this.pickBrickType();
      const particle = this.pickParticle(type);
      for (let row = 0; row < this.gridRows; row += 1) {
        for (let col = 0; col < this.gridCols; col += 1) {
          if ((row + col) % 2 === 0) {
            this.placeBrick(bricks, row, col, geometry, type, particle);
          } else if (Math.random() < CHECKERBOARD_CONFIG.extraFillChance) {
            this.placeBrick(bricks, row, col, geometry);
          }
        }
      }
      return bricks;
    }

    generateDiagonalLayout(geometry) {
      const bricks = [];
      const mainType = BRICK_TYPES.BONUS;
      for (let row = 0; row < this.gridRows; row += 1) {
        for (let col = 0; col < this.gridCols; col += 1) {
          const onMain = row === col;
          const onSecondary = row + col === this.gridCols - 1;
          if (onMain || onSecondary) {
            this.placeBrick(bricks, row, col, geometry, mainType);
          } else if (Math.random() < DIAGONALS_CONFIG.extraFillChance) {
            this.placeBrick(bricks, row, col, geometry);
          }
        }
      }
      return bricks;
    }

    maybeAddGraviton(bricks) {
      if (bricks.length <= 1) {
        return bricks;
      }
      const spawnConfig = SETTINGS.graviton.spawnChance;
      const gravitonChance = clamp(
        spawnConfig.base + (this.level - 1) * spawnConfig.perLevel,
        spawnConfig.min,
        spawnConfig.max
      );
      if (Math.random() >= gravitonChance) {
        return bricks;
      }
      const candidates = bricks.filter(brick => brick.row >= Math.floor(this.gridRows / 2));
      if (candidates.length === 0) {
        return bricks;
      }
      const target = randomChoice(candidates);
      if (!target) {
        return bricks;
      }
      const graviton = this.createBrick({
        row: target.row,
        col: target.col,
        relX: target.relX,
        relY: target.relY,
        relWidth: target.relWidth,
        relHeight: target.relHeight,
        type: BRICK_TYPES.GRAVITON,
        particle: GRAVITON_PARTICLE
      });
      graviton.hidden = true;
      const index = bricks.findIndex(brick => brick === target);
      if (index >= 0) {
        bricks[index] = graviton;
      }
      return bricks;
    }

    pickBrickType() {
      const factorStep = BRICK_TYPE_WEIGHTS.levelFactor.step;
      const maxFactor = BRICK_TYPE_WEIGHTS.levelFactor.max;
      const levelFactor = clamp((this.level - 1) * factorStep, 0, maxFactor);
      const weights = [
        {
          type: BRICK_TYPES.SIMPLE,
          weight: BRICK_TYPE_WEIGHTS.base.simple + levelFactor * BRICK_TYPE_WEIGHTS.levelFactor.simple
        },
        {
          type: BRICK_TYPES.RESISTANT,
          weight: BRICK_TYPE_WEIGHTS.base.resistant + levelFactor * BRICK_TYPE_WEIGHTS.levelFactor.resistant
        },
        {
          type: BRICK_TYPES.BONUS,
          weight: BRICK_TYPE_WEIGHTS.base.bonus + levelFactor * BRICK_TYPE_WEIGHTS.levelFactor.bonus
        }
      ];
      const total = weights.reduce((sum, entry) => sum + entry.weight, 0);
      const roll = Math.random() * total;
      let cumulative = 0;
      for (const entry of weights) {
        cumulative += entry.weight;
        if (roll <= cumulative) {
          return entry.type;
        }
      }
      return BRICK_TYPES.SIMPLE;
    }

    pickParticle(type) {
      if (type === BRICK_TYPES.RESISTANT) {
        return randomChoice(RESISTANT_PARTICLES);
      }
      if (type === BRICK_TYPES.BONUS) {
        return randomChoice(BONUS_PARTICLES);
      }
      if (type === BRICK_TYPES.GRAVITON) {
        return GRAVITON_PARTICLE;
      }
      return randomChoice(SIMPLE_PARTICLES);
    }

    createBrick({ row, col, relX, relY, relWidth, relHeight, type, particle }) {
      const maxHits = (() => {
        if (type === BRICK_TYPES.RESISTANT) {
          const minHits = Math.max(2, Math.floor(particle?.minHits || 2));
          const max = Math.max(minHits, Math.floor(particle?.maxHits || minHits));
          return Math.floor(Math.random() * (max - minHits + 1)) + minHits;
        }
        return 1;
      })();
      return {
        id: `${type}-${row}-${col}-${Math.random().toString(36).slice(2, 7)}`,
        type,
        particle,
        row,
        col,
        relX,
        relY,
        relWidth,
        relHeight,
        maxHits,
        hitsRemaining: maxHits,
        active: true,
        hidden: type === BRICK_TYPES.GRAVITON,
        revealedAt: 0,
        dissipated: false
      };
    }

    prepareServe() {
      this.resetComboChain();
      this.balls = [this.createBall({ attachToPaddle: true })];
    }

    createBall({ attachToPaddle = false, angle = null } = {}) {
      const radius = this.ballRadius || this.ballSettings.radiusRatio * Math.min(this.width, this.height);
      const paddleCenter = this.paddle.x + this.paddle.width / 2;
      const ball = {
        id: `ball-${this.ballIdCounter += 1}`,
        x: paddleCenter,
        y: this.paddle.y - radius * BALL_FOLLOW_OFFSET_RATIO,
        vx: 0,
        vy: 0,
        radius,
        electricSeed: Math.random() * Math.PI * 2,
        spriteIndex: getRandomEnergyBallVariant(),
        attachedToPaddle: attachToPaddle,
        inPlay: !attachToPaddle,
        trail: [],
        lastGhostTime: 0
      };
      if (!attachToPaddle) {
        const launchAngle = typeof angle === 'number'
          ? angle
          : (-Math.PI / 2) * 0.75 + Math.random() * (Math.PI / 3);
        const speed = this.getBallSpeed();
        ball.vx = Math.cos(launchAngle) * speed;
        ball.vy = Math.sin(launchAngle) * speed;
      }
      return ball;
    }

    getBallSpeed() {
      const base = (this.width + this.height) / (2 * Math.max(this.pixelRatio, 0.5));
      const levelMultiplier = Math.pow(
        1 + this.ballSettings.speedGrowthRatio,
        Math.max(0, this.level - 1)
      );
      const ratio = this.ballSettings.baseSpeedRatio * levelMultiplier;
      const speedPerMillisecond = base * ratio * BALL_SPEED_FACTOR * this.ballSpeedMultiplier;
      return Math.max(BALL_MIN_SPEED_PER_MS, speedPerMillisecond);
    }

    launchBallFromPaddle(ball) {
      if (!ball) return;
      const angle = (-Math.PI / 2) * 0.75 + Math.random() * (Math.PI / 3);
      const speed = this.getBallSpeed();
      ball.vx = Math.cos(angle) * speed;
      ball.vy = Math.sin(angle) * speed;
      ball.attachedToPaddle = false;
      ball.inPlay = true;
    }

    releaseHeldBalls() {
      let launched = false;
      this.balls.forEach(ball => {
        if (ball.attachedToPaddle) {
          this.updateBallFollowingPaddle(ball);
          this.launchBallFromPaddle(ball);
          launched = true;
        }
      });
      if (launched) {
        this.startAnimation();
      }
      return launched;
    }

    updateBallFollowingPaddle(ball) {
      const paddleCenter = this.paddle.x + this.paddle.width / 2;
      ball.x = paddleCenter;
      ball.y = this.paddle.y - ball.radius * BALL_FOLLOW_OFFSET_RATIO;
      ball.vx = 0;
      ball.vy = 0;
      ball.inPlay = false;
      ball.attachedToPaddle = true;
      this.resetBallTrail(ball);
    }

    startAnimation() {
      if (this.running) return;
      this.running = true;
      this.lastTimestamp = typeof performance !== 'undefined' ? performance.now() : Date.now();
      this.animationFrameId = requestAnimationFrame(this.handleFrame);
    }

    stopAnimation() {
      if (!this.running) return;
      this.running = false;
      if (this.animationFrameId != null) {
        cancelAnimationFrame(this.animationFrameId);
        this.animationFrameId = null;
      }
    }

    handleFrame(timestamp) {
      if (!this.running) return;
      const now = typeof timestamp === 'number' ? timestamp : (typeof performance !== 'undefined' ? performance.now() : Date.now());
      const delta = Math.min(32, now - this.lastTimestamp);
      this.lastTimestamp = now;
      this.update(delta, now);
      this.render(now);
      this.animationFrameId = requestAnimationFrame(this.handleFrame);
    }

    update(delta, now) {
      this.updateEffects(now);
      this.updateGravitons(now);
      this.updatePowerUps(delta);
      this.updateLasers(delta);
      this.updateBalls(delta, now);
      this.updateLingerBonus(now);
      this.refreshComboMessage(now);
    }

    updateBalls(delta, now) {
      if (this.balls.length === 0) {
        return;
      }
      let lostBall = false;
      for (let i = this.balls.length - 1; i >= 0; i -= 1) {
        const ball = this.balls[i];
        if (!ball.inPlay) {
          if (ball.attachedToPaddle) {
            this.updateBallFollowingPaddle(ball);
          }
          continue;
        }
        ball.x += ball.vx * delta;
        ball.y += ball.vy * delta;
        this.handleWallCollisions(ball);
        if (!ball.inPlay) {
          lostBall = true;
          this.balls.splice(i, 1);
          continue;
        }
        this.handlePaddleCollision(ball);
        this.handleBrickCollisions(ball);
        this.addBallTrailPoint(ball, now);
        this.pruneBallTrail(ball, now);
      }
      if (lostBall && this.balls.length === 0) {
        this.handleLifeLost();
      }
    }

    updateLingerBonus(now) {
      if (!this.running) {
        return;
      }
      if (!(LEVEL_LINGER_THRESHOLD_MS > 0)) {
        return;
      }
      const start = this.levelStartedAt;
      if (!Number.isFinite(start) || start <= 0) {
        return;
      }
      if (now - start < LEVEL_LINGER_THRESHOLD_MS) {
        return;
      }
      const interval = Math.max(LEVEL_LINGER_INTERVAL_MS, 16);
      const lastCheck = this.lastLingerBonusCheckAt || 0;
      if (lastCheck && now - lastCheck < interval) {
        return;
      }
      this.lastLingerBonusCheckAt = now;
      if (LEVEL_LINGER_CHANCE <= 0) {
        return;
      }
      if (Math.random() < LEVEL_LINGER_CHANCE) {
        this.activatePowerUp(POWER_UP_IDS.MULTIBALL);
        if (LEVEL_LINGER_MESSAGE) {
          const duration = LEVEL_LINGER_MESSAGE_DURATION || COMBO_BONUS_DURATION;
          this.setComboMessage(LEVEL_LINGER_MESSAGE, duration);
        }
      }
    }

    addBallTrailPoint(ball, timestamp) {
      if (!ball || !ball.inPlay) return;
      const now = Number.isFinite(timestamp) && timestamp > 0
        ? timestamp
        : (typeof performance !== 'undefined' ? performance.now() : Date.now());
      if (!Array.isArray(ball.trail)) {
        ball.trail = [];
      }
      const lastEntry = ball.trail[ball.trail.length - 1];
      const minDistance = Math.max(BALL_TRAIL_MIN_DISTANCE, ball.radius * BALL_TRAIL_MIN_DISTANCE_FACTOR);
      if (lastEntry) {
        const dx = lastEntry.x - ball.x;
        const dy = lastEntry.y - ball.y;
        const distanceSquared = dx * dx + dy * dy;
        if (distanceSquared < minDistance * minDistance && now - lastEntry.time < BALL_TRAIL_CAPTURE_INTERVAL_MS) {
          return;
        }
      }
      ball.trail.push({
        x: ball.x,
        y: ball.y,
        time: now,
        vx: ball.vx,
        vy: ball.vy
      });
      const maxPoints = BALL_TRAIL_MAX_POINTS;
      while (ball.trail.length > maxPoints) {
        ball.trail.shift();
      }
      const ghostInterval = BALL_GHOST_INTERVAL_MS;
      if (
        this.particleLayer
        && !ball.attachedToPaddle
        && (typeof ball.lastGhostTime !== 'number' || now - ball.lastGhostTime >= ghostInterval)
      ) {
        this.spawnBallGhost(ball);
        ball.lastGhostTime = now;
      }
    }

    pruneBallTrail(ball, timestamp) {
      if (!ball || !Array.isArray(ball.trail) || ball.trail.length === 0) return;
      const now = Number.isFinite(timestamp) && timestamp > 0
        ? timestamp
        : (typeof performance !== 'undefined' ? performance.now() : Date.now());
      const maxLifetime = BALL_TRAIL_PRUNE_MS;
      while (ball.trail.length > 0 && now - ball.trail[0].time > maxLifetime) {
        ball.trail.shift();
      }
      if (!ball.inPlay && ball.trail.length > 0) {
        ball.trail.length = 0;
      }
    }

    resetBallTrail(ball) {
      if (!ball) return;
      if (Array.isArray(ball.trail)) {
        ball.trail.length = 0;
      }
      ball.lastGhostTime = 0;
    }

    spawnBallGhost(ball) {
      if (!this.particleLayer || !this.canvas || !ball) {
        return;
      }
      const rect = this.canvas.getBoundingClientRect();
      if (!rect.width || !rect.height) {
        return;
      }
      const doc = this.particleLayer.ownerDocument || (typeof document !== 'undefined' ? document : null);
      if (!doc) {
        return;
      }
      const pixelRatio = this.pixelRatio || 1;
      const radius = ball.radius / pixelRatio;
      const ghost = doc.createElement('div');
      if (!ghost) {
        return;
      }
      ghost.className = 'arcade-ball-ghost';
      const left = ball.x / pixelRatio - radius;
      const top = ball.y / pixelRatio - radius;
      ghost.style.left = `${left.toFixed(2)}px`;
      ghost.style.top = `${top.toFixed(2)}px`;
      const size = radius * 2;
      ghost.style.width = `${size.toFixed(2)}px`;
      ghost.style.height = `${size.toFixed(2)}px`;
      const blur = Math.max(6, radius * BALL_GHOST_BLUR_FACTOR);
      ghost.style.setProperty('--arcade-ball-ghost-blur', `${blur.toFixed(2)}px`);
      const removeGhost = () => {
        ghost.removeEventListener('animationend', removeGhost);
        if (ghost.parentNode === this.particleLayer) {
          this.particleLayer.removeChild(ghost);
        }
      };
      ghost.addEventListener('animationend', removeGhost);
      this.particleLayer.appendChild(ghost);
      setTimeout(removeGhost, BALL_GHOST_REMOVE_DELAY_MS);
    }

    updateEffects(now) {
      for (const [effectId, effect] of this.effects.entries()) {
        if (effect.expiresAt && now >= effect.expiresAt) {
          this.endEffect(effectId);
          this.effects.delete(effectId);
        }
      }
    }

    updateGravitons(now) {
      this.bricks.forEach(brick => {
        if (!brick.active || brick.type !== BRICK_TYPES.GRAVITON) {
          return;
        }
        if (brick.hidden) {
          const blocked = this.bricks.some(other => (
            other !== brick
            && other.active
            && other.col === brick.col
            && other.row < brick.row
          ));
          if (!blocked) {
            brick.hidden = false;
            brick.revealedAt = now;
            this.setComboMessage(GRAVITON_DETECTION_MESSAGE, GRAVITON_DETECTION_DURATION);
          }
          return;
        }
        if (!brick.revealedAt) {
          brick.revealedAt = now;
        }
        if (!brick.dissipated && brick.revealedAt && now - brick.revealedAt >= this.gravitonLifetimeMs) {
          brick.active = false;
          brick.dissipated = true;
          this.setComboMessage(GRAVITON_DISSIPATE_MESSAGE, GRAVITON_DISSIPATE_DURATION);
        }
      });
    }

    updatePowerUps(delta) {
      if (this.powerUps.length === 0) return;
      const speed = Math.max(0.2, this.height * POWER_UP_FALL_SPEED_RATIO);
      for (let i = this.powerUps.length - 1; i >= 0; i -= 1) {
        const powerUp = this.powerUps[i];
        powerUp.y += speed * delta;
        if (powerUp.y > this.height + powerUp.size) {
          this.powerUps.splice(i, 1);
          continue;
        }
        if (this.checkPowerUpCatch(powerUp)) {
          this.powerUps.splice(i, 1);
        }
      }
    }

    updateLasers(delta) {
      const effect = this.effects.get(POWER_UP_IDS.LASER);
      if (effect) {
        effect.cooldown = (effect.cooldown || 0) + delta;
        if (effect.cooldown >= (effect.interval || LASER_INTERVAL_MS)) {
          effect.cooldown -= effect.interval || LASER_INTERVAL_MS;
          this.fireLasers();
        }
      }
      const velocity = this.height * LASER_SPEED_RATIO;
      for (let i = this.lasers.length - 1; i >= 0; i -= 1) {
        const laser = this.lasers[i];
        laser.y += velocity * delta;
        if (laser.y + laser.height < 0) {
          this.lasers.splice(i, 1);
          continue;
        }
        if (this.checkLaserCollisions(laser)) {
          this.lasers.splice(i, 1);
        }
      }
    }

    refreshComboMessage(now) {
      if (this.comboMessage && this.comboMessageExpiry && now >= this.comboMessageExpiry) {
        this.comboMessage = '';
        this.comboMessageExpiry = 0;
        if (this.comboLabel) {
          this.comboLabel.textContent = '';
        }
      }
    }

    handleWallCollisions(ball) {
      if (ball.x - ball.radius <= 0) {
        ball.x = ball.radius;
        ball.vx = Math.abs(ball.vx);
      } else if (ball.x + ball.radius >= this.width) {
        ball.x = this.width - ball.radius;
        ball.vx = -Math.abs(ball.vx);
      }
      if (ball.y - ball.radius <= 0) {
        ball.y = ball.radius;
        ball.vy = Math.abs(ball.vy);
      }
      const floorShieldActive = this.effects.has(POWER_UP_IDS.FLOOR);
      if (floorShieldActive && ball.y + ball.radius >= this.height) {
        ball.y = this.height - ball.radius;
        const reboundSpeed = Math.max(Math.abs(ball.vy), this.getBallSpeed());
        ball.vy = -Math.abs(reboundSpeed);
      } else if (ball.y - ball.radius > this.height) {
        ball.inPlay = false;
      }
    }

    handlePaddleCollision(ball) {
      if (
        !ball.inPlay
        || ball.vy <= 0
        || ball.y - ball.radius > this.paddle.y + this.paddle.height
        || ball.y + ball.radius < this.paddle.y
        || ball.x + ball.radius < this.paddle.x
        || ball.x - ball.radius > this.paddle.x + this.paddle.width
      ) {
        return;
      }
      const relative = (ball.x - (this.paddle.x + this.paddle.width / 2)) / (this.paddle.width / 2);
      const clamped = clamp(relative, -1, 1);
      const angle = clamped * (Math.PI / 3);
      const speed = this.getBallSpeed();
      ball.vx = speed * Math.sin(angle);
      ball.vy = -Math.abs(speed * Math.cos(angle));
      ball.y = this.paddle.y - ball.radius - 1;
      const impactIntensity = 0.6 + Math.abs(clamped) * 0.4;
      this.triggerPaddleBounce(impactIntensity);
    }

    handleBrickCollisions(ball) {
      if (!ball.inPlay) return;
      for (const brick of this.bricks) {
        if (!brick.active || (brick.hidden && brick.type === BRICK_TYPES.GRAVITON)) continue;
        const x = brick.relX * this.width;
        const y = brick.relY * this.height;
        const w = brick.relWidth * this.width;
        const h = brick.relHeight * this.height;
        if (
          ball.x + ball.radius < x
          || ball.x - ball.radius > x + w
          || ball.y + ball.radius < y
          || ball.y - ball.radius > y + h
        ) {
          continue;
        }
        const overlapLeft = ball.x + ball.radius - x;
        const overlapRight = x + w - (ball.x - ball.radius);
        const overlapTop = ball.y + ball.radius - y;
        const overlapBottom = y + h - (ball.y - ball.radius);
        const minOverlap = Math.min(overlapLeft, overlapRight, overlapTop, overlapBottom);
        if (minOverlap === overlapLeft) {
          ball.x = x - ball.radius;
          ball.vx = -Math.abs(ball.vx);
        } else if (minOverlap === overlapRight) {
          ball.x = x + w + ball.radius;
          ball.vx = Math.abs(ball.vx);
        } else if (minOverlap === overlapTop) {
          ball.y = y - ball.radius;
          ball.vy = -Math.abs(ball.vy);
        } else {
          ball.y = y + h + ball.radius;
          ball.vy = Math.abs(ball.vy);
        }
        this.hitBrick(brick);
        break;
      }
    }

    hitBrick(brick) {
      brick.hitsRemaining = Math.max(0, brick.hitsRemaining - 1);
      if (brick.hitsRemaining <= 0) {
        brick.active = false;
        this.handleBrickDestroyed(brick);
      } else if (brick.type === BRICK_TYPES.RESISTANT && !brick.particle?.sprite) {
        brick.relHeight *= 0.98;
      }
    }

    handleBrickDestroyed(brick) {
      const scoreGain = BRICK_SCORE_VALUE[brick.type] || 100;
      this.score += scoreGain;
      this.updateHud();
      this.spawnBrickImpactParticles(brick);
      if (brick.type === BRICK_TYPES.BONUS) {
        this.spawnPowerUp(brick);
      }
      if (brick.type === BRICK_TYPES.GRAVITON) {
        this.captureGraviton();
      }
      if (brick.particle?.family === 'quark' && brick.particle?.quarkColor) {
        this.registerQuarkCombo(brick.particle.quarkColor);
      }
      this.registerComboChain(brick);
      const hasRemaining = this.bricks.some(entry => entry.active);
      if (!hasRemaining) {
        this.handleLevelCleared();
      }
    }

    spawnBrickImpactParticles(brick) {
      if (!this.particleLayer || !brick || !this.canvas) {
        return;
      }
      const rect = this.canvas.getBoundingClientRect();
      if (!rect.width || !rect.height) {
        return;
      }
      const doc = this.particleLayer.ownerDocument || (typeof document !== 'undefined' ? document : null);
      if (!doc) {
        return;
      }
      const brickLeft = brick.relX * rect.width;
      const brickTop = brick.relY * rect.height;
      const brickWidth = brick.relWidth * rect.width;
      const brickHeight = brick.relHeight * rect.height;
      const brickCenterX = brickLeft + brickWidth / 2;
      const brickCenterY = brickTop + brickHeight / 2;
      const rawCount = Math.round(18 + Math.random() * 10);
      const baseCount = Math.max(7, Math.round(rawCount * 0.5));
      const maxHorizontalScatter = Math.min(rect.width * 0.2, 180);

      for (let index = 0; index < baseCount; index += 1) {
        const particle = doc.createElement('div');
        if (!particle) {
          break;
        }
        particle.className = 'arcade-particle';
        const size = 3.6 + Math.random() * 2.6;
        const spawnX = brickCenterX + (Math.random() - 0.5) * brickWidth * 0.6;
        const spawnY = brickCenterY + (Math.random() - 0.5) * brickHeight * 0.5;
        particle.style.left = `${(spawnX - size / 2).toFixed(2)}px`;
        particle.style.top = `${(spawnY - size / 2).toFixed(2)}px`;
        particle.style.width = `${size.toFixed(2)}px`;
        particle.style.height = `${size.toFixed(2)}px`;
        particle.style.borderRadius = Math.random() < 0.45 ? '50%' : '1px';
        const hue = Math.floor(Math.random() * 360);
        const hueOffset = (hue + 40 + Math.random() * 60) % 360;
        const angle = Math.floor(Math.random() * 180);
        particle.style.background = `linear-gradient(${angle}deg, hsla(${hue}, 98%, 66%, 0.95), hsla(${hueOffset}, 82%, 58%, 0.6))`;
        const burstRadius = 32 + Math.random() * 72;
        const burstAngle = Math.random() * Math.PI * 2;
        const burstX = clamp(Math.cos(burstAngle) * burstRadius, -maxHorizontalScatter, maxHorizontalScatter);
        const burstY = -Math.abs(Math.sin(burstAngle)) * (burstRadius * 0.82 + Math.random() * 34) - (16 + Math.random() * 28);
        particle.style.setProperty('--arcade-particle-burst-x', `${burstX.toFixed(2)}px`);
        particle.style.setProperty('--arcade-particle-burst-y', `${burstY.toFixed(2)}px`);
        const scaleStart = 0.6 + Math.random() * 0.4;
        const peakScale = scaleStart + 0.3 + Math.random() * 0.25;
        const scaleEnd = 0.35 + Math.random() * 0.25;
        particle.style.setProperty('--arcade-particle-scale-start', scaleStart.toFixed(2));
        particle.style.setProperty('--arcade-particle-scale-peak', peakScale.toFixed(2));
        particle.style.setProperty('--arcade-particle-scale-end', scaleEnd.toFixed(2));
        particle.style.setProperty('--arcade-particle-rotation', `${((Math.random() - 0.5) * 220).toFixed(1)}deg`);
        const duration = 1500;
        particle.style.setProperty('--arcade-particle-duration', `${duration}ms`);
        particle.style.animationDelay = `${Math.random() * 130}ms`;
        const removeParticle = () => {
          particle.removeEventListener('animationend', removeParticle);
          if (particle.parentNode === this.particleLayer) {
            this.particleLayer.removeChild(particle);
          }
        };
        particle.addEventListener('animationend', removeParticle);
        this.particleLayer.appendChild(particle);
        setTimeout(removeParticle, duration + 200);
      }
    }

    spawnPowerUp(brick) {
      const powerUpTypes = [
        POWER_UP_IDS.EXTEND,
        POWER_UP_IDS.MULTIBALL,
        POWER_UP_IDS.LASER,
        POWER_UP_IDS.SPEED,
        POWER_UP_IDS.FLOOR
      ];
      const type = randomChoice(powerUpTypes);
      if (!type) return;
      const visuals = POWER_UP_VISUALS[type] || DEFAULT_POWER_UP_VISUAL;
      const powerUp = {
        id: `pu-${Math.random().toString(36).slice(2, 7)}`,
        type,
        x: brick.relX * this.width + (brick.relWidth * this.width) / 2,
        y: brick.relY * this.height + (brick.relHeight * this.height) / 2,
        size: Math.max(14, this.ballRadius * 1.6),
        symbol: visuals.symbol || DEFAULT_POWER_UP_VISUAL.symbol,
        widthMultiplier: visuals.widthMultiplier || DEFAULT_POWER_UP_VISUAL.widthMultiplier
      };
      this.powerUps.push(powerUp);
      const label = POWER_UP_LABELS[type] || '';
      this.setComboMessage(`${COMBO_BONUS_PREFIX}${label}`, COMBO_BONUS_DURATION);
    }

    checkPowerUpCatch(powerUp) {
      const visuals = POWER_UP_VISUALS[powerUp.type] || DEFAULT_POWER_UP_VISUAL;
      const widthMultiplier = typeof powerUp.widthMultiplier === 'number'
        ? powerUp.widthMultiplier
        : visuals.widthMultiplier || DEFAULT_POWER_UP_VISUAL.widthMultiplier;
      const halfWidth = (powerUp.size * widthMultiplier) / 2;
      const halfHeight = powerUp.size / 2;
      const withinX = powerUp.x + halfWidth >= this.paddle.x && powerUp.x - halfWidth <= this.paddle.x + this.paddle.width;
      const withinY = powerUp.y + halfHeight >= this.paddle.y && powerUp.y - halfHeight <= this.paddle.y + this.paddle.height;
      if (withinX && withinY) {
        this.activatePowerUp(powerUp.type);
        return true;
      }
      return false;
    }

    fireLasers() {
      const leftX = this.paddle.x + this.paddle.width * 0.25;
      const rightX = this.paddle.x + this.paddle.width * 0.75;
      const originY = this.paddle.y;
      const width = Math.max(4, this.ballRadius * 0.4);
      const height = Math.max(14, this.ballRadius * 1.6);
      this.lasers.push({ x: leftX, y: originY, width, height });
      this.lasers.push({ x: rightX, y: originY, width, height });
    }

    checkLaserCollisions(laser) {
      for (const brick of this.bricks) {
        if (!brick.active || (brick.hidden && brick.type === BRICK_TYPES.GRAVITON)) continue;
        const x = brick.relX * this.width;
        const y = brick.relY * this.height;
        const w = brick.relWidth * this.width;
        const h = brick.relHeight * this.height;
        if (
          laser.x + laser.width < x
          || laser.x > x + w
          || laser.y > y + h
          || laser.y + laser.height < y
        ) {
          continue;
        }
        this.hitBrick(brick);
        return true;
      }
      return false;
    }

    captureGraviton() {
      this.setComboMessage(GRAVITON_CAPTURE_MESSAGE, GRAVITON_CAPTURE_DURATION);
      this.specialTicketsEarned += 1;
      if (this.onSpecialTicket) {
        this.onSpecialTicket(1);
      }
    }

    registerQuarkCombo(color) {
      this.quarkComboColors.add(color);
      if (COMBO_REQUIRED_COLORS.every(required => this.quarkComboColors.has(required))) {
        this.quarkComboColors.clear();
        const reward = randomChoice(COMBO_POWER_UPS);
        if (reward) {
          this.activatePowerUp(reward);
          const label = POWER_UP_LABELS[reward] || '';
          this.setComboMessage(`${COMBO_QUARK_PREFIX}${label}`, COMBO_QUARK_DURATION);
        }
      }
    }

    registerComboChain(brick) {
      if (!brick) return;
      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      if (now - this.lastBrickDestroyedAt <= COMBO_CHAIN_WINDOW_MS) {
        this.comboChainCount += 1;
      } else {
        this.comboChainCount = 1;
      }
      this.lastBrickDestroyedAt = now;
      if (this.comboChainCount >= COMBO_CHAIN_THRESHOLD) {
        this.triggerComboShockwave(brick, this.comboChainCount);
      }
    }

    triggerComboShockwave(brick, chainCount = COMBO_CHAIN_THRESHOLD) {
      if (!this.particleLayer) return;
      const doc = this.particleLayer.ownerDocument || (typeof document !== 'undefined' ? document : null);
      if (!doc) return;
      const shockwave = doc.createElement('div');
      if (!shockwave) return;
      shockwave.className = 'arcade-shockwave';
      const centerX = ((brick.relX + brick.relWidth / 2) * 100).toFixed(2);
      const centerY = ((brick.relY + brick.relHeight / 2) * 100).toFixed(2);
      const effectiveChains = Math.min(chainCount, COMBO_SHOCKWAVE.maxScaleChains);
      const originalScale = COMBO_SHOCKWAVE.baseScale + effectiveChains * COMBO_SHOCKWAVE.scalePerChain;
      const scale = 1 + (originalScale - 1) * 0.5;
      const opacity = Math.min(
        COMBO_SHOCKWAVE.baseOpacity + chainCount * COMBO_SHOCKWAVE.opacityPerChain,
        COMBO_SHOCKWAVE.maxOpacity
      ) * 0.5;
      shockwave.style.setProperty('--shockwave-x', `${centerX}%`);
      shockwave.style.setProperty('--shockwave-y', `${centerY}%`);
      shockwave.style.setProperty('--shockwave-scale', scale.toFixed(2));
      shockwave.style.setProperty('--shockwave-opacity', opacity.toFixed(2));
      this.particleLayer.appendChild(shockwave);
      const removeShockwave = () => {
        shockwave.removeEventListener('animationend', removeShockwave);
        if (shockwave.parentNode === this.particleLayer) {
          this.particleLayer.removeChild(shockwave);
        }
      };
      shockwave.addEventListener('animationend', removeShockwave);
      setTimeout(removeShockwave, COMBO_SHOCKWAVE.removeDelayMs);
      const stagePulse = COMBO_SHOCKWAVE.stagePulse;
      const effectivePulseChains = Math.min(chainCount, stagePulse.maxChains);
      const originalIntensity = stagePulse.baseIntensity + effectivePulseChains * stagePulse.intensityPerChain;
      const intensity = 1 + (originalIntensity - 1) * 0.5;
      this.triggerScreenPulse(intensity);
    }

    triggerScreenPulse(intensity = 1.03) {
      if (!this.stage || typeof this.stage.classList?.add !== 'function') return;
      const scale = Math.max(1, intensity);
      this.stage.style.setProperty('--arcade-pulse-scale', scale.toFixed(3));
      this.stage.classList.remove('arcade-stage--pulse');
      if (typeof this.stage.offsetWidth === 'number') {
        void this.stage.offsetWidth;
      }
      this.stage.classList.add('arcade-stage--pulse');
      if (this.stagePulseTimeout) {
        clearTimeout(this.stagePulseTimeout);
      }
      this.stagePulseTimeout = setTimeout(() => {
        if (this.stage) {
          this.stage.classList.remove('arcade-stage--pulse');
        }
      }, COMBO_SHOCKWAVE.stagePulse.durationMs);
    }

    maybePulseForPowerUp(type) {
      const intensity = POWER_UP_PULSE_INTENSITY[type];
      if (intensity && intensity > 1) {
        this.triggerScreenPulse(intensity);
      }
    }

    activatePowerUp(type) {
      if (type === POWER_UP_IDS.MULTIBALL) {
        this.maybePulseForPowerUp(type);
        this.spawnAdditionalBalls();
        return;
      }
      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      const existing = this.effects.get(type);
      const effectConfig = POWER_UP_EFFECTS[type];
      const duration = effectConfig?.duration ?? 0;
      if (existing) {
        existing.expiresAt = now + duration;
        existing.interval = effectConfig?.interval ?? existing.interval;
      } else {
        const newEffect = {
          expiresAt: now + duration,
          interval: effectConfig?.interval ?? LASER_INTERVAL_MS,
          cooldown: 0
        };
        this.effects.set(type, newEffect);
        this.startEffect(type, newEffect);
      }
      this.maybePulseForPowerUp(type);
    }

    startEffect(type, effect) {
      switch (type) {
        case POWER_UP_IDS.EXTEND:
          {
            const previousWidth = this.paddle.width;
            this.paddle.currentWidthRatio = Math.min(
              PADDLE_EXTEND_MAX_WIDTH_RATIO,
              this.paddle.baseWidthRatio * PADDLE_EXTEND_MULTIPLIER
            );
            this.updatePaddleSize();
            this.triggerPaddleStretchAnimation(previousWidth, this.paddle.width);
          }
          break;
        case POWER_UP_IDS.LASER:
          effect.cooldown = 0;
          break;
        case POWER_UP_IDS.SPEED:
          this.ballSpeedMultiplier = POWER_UP_SPEED_MULTIPLIER;
          break;
        case POWER_UP_IDS.FLOOR:
          break;
        default:
          break;
      }
    }

    endEffect(type) {
      switch (type) {
        case POWER_UP_IDS.EXTEND:
          this.paddle.currentWidthRatio = this.paddle.baseWidthRatio;
          this.updatePaddleSize();
          break;
        case POWER_UP_IDS.SPEED:
          this.ballSpeedMultiplier = 1;
          break;
        default:
          break;
      }
    }

    spawnAdditionalBalls() {
      if (this.balls.length === 0) {
        this.prepareServe();
        this.releaseHeldBalls();
        return;
      }
      const existing = this.balls.filter(ball => ball.inPlay);
      if (existing.length === 0) {
        this.releaseHeldBalls();
        return;
      }
      existing.slice(0, 2).forEach(ball => {
        const clone = this.createBall({ attachToPaddle: false });
        clone.x = ball.x;
        clone.y = ball.y;
        const angle = Math.atan2(ball.vy, ball.vx) + (Math.random() - 0.5) * 0.6;
        const speed = this.getBallSpeed();
        clone.vx = Math.cos(angle) * speed;
        clone.vy = Math.sin(angle) * speed;
        clone.inPlay = true;
        this.balls.push(clone);
      });
      this.startAnimation();
    }

    handleLevelCleared() {
      this.stopAnimation();
      this.balls = [];
      this.powerUps = [];
      this.lasers = [];
      const completedLevel = this.level;
      const reward = this.lives === this.maxLives ? ARCADE_TICKET_REWARD : 0;
      if (reward > 0) {
        this.ticketsEarned += reward;
        if (this.onTicketsEarned) {
          this.onTicketsEarned(reward, { level: completedLevel, score: this.score });
        }
      }
      const rewardLabel = reward > 0
        ? LEVEL_CLEARED_REWARD_TEMPLATE.replace('{reward}', this.formatTicketLabel(reward))
        : LEVEL_CLEARED_NO_REWARD;
      const message = LEVEL_CLEARED_TEMPLATE
        .replace('{level}', completedLevel)
        .replace('{reward}', rewardLabel);
      this.pendingLevelAdvance = true;
      this.showOverlay({
        message,
        buttonLabel: LEVEL_CLEARED_BUTTON,
        action: 'next'
      });
    }

    handleLifeLost() {
      this.stopAnimation();
      this.lives = Math.max(0, this.lives - 1);
      this.updateHud();
      if (this.lives > 0) {
        this.prepareServe();
        this.showOverlay({
          message: LIFE_LOST_MESSAGE,
          buttonLabel: LIFE_LOST_BUTTON,
          action: 'resume'
        });
      } else {
        this.gameOver();
      }
    }

    gameOver() {
      this.stopAnimation();
      this.prepareServe();
      const ticketSummary = this.formatTicketLabel(this.ticketsEarned);
      const bonusSummary = this.specialTicketsEarned > 0
        ? ` · ${this.formatBonusTicketLabel(this.specialTicketsEarned)}`
        : '';
      const message = this.ticketsEarned > 0 || this.specialTicketsEarned > 0
        ? GAME_OVER_WITH_TICKETS
            .replace('{tickets}', ticketSummary)
            .replace('{bonus}', bonusSummary)
        : GAME_OVER_WITHOUT_TICKETS;
      this.showOverlay({
        message,
        buttonLabel: GAME_OVER_BUTTON,
        action: 'restart'
      });
    }

    showOverlay(options = {}) {
      if (!this.overlay) return;
      const {
        message = '',
        buttonLabel = 'Continuer',
        action = 'start'
      } = options;
      this.overlay.hidden = false;
      this.overlay.setAttribute('aria-hidden', 'false');
      if (this.overlayMessage) {
        this.overlayMessage.textContent = message;
      }
      if (this.overlayButton) {
        this.overlayButton.textContent = buttonLabel;
      }
      this.overlayAction = action;
    }

    hideOverlay() {
      if (!this.overlay) return;
      this.overlay.hidden = true;
      this.overlay.setAttribute('aria-hidden', 'true');
    }

    isOverlayVisible() {
      return !this.overlay?.hidden;
    }

    startNewGame() {
      this.level = 1;
      this.score = 0;
      this.ticketsEarned = 0;
      this.specialTicketsEarned = 0;
      this.pendingLevelAdvance = false;
      this.setupLevel();
      this.hideOverlay();
      this.releaseHeldBalls();
    }

    resumeFromPause() {
      this.pendingResume = false;
      this.hideOverlay();
      if (!this.releaseHeldBalls()) {
        this.startAnimation();
      }
    }

    startNextLevel() {
      if (this.pendingLevelAdvance) {
        this.level += 1;
      }
      this.pendingLevelAdvance = false;
      this.setupLevel();
      this.hideOverlay();
      this.releaseHeldBalls();
    }

    handlePointerDown(event) {
      if (!this.enabled || this.isOverlayVisible()) return;
      this.pointerActive = true;
      if (this.canvas.setPointerCapture) {
        try {
          this.canvas.setPointerCapture(event.pointerId);
        } catch (error) {
          // ignore pointer capture errors
        }
      }
      this.updatePaddleFromPointer(event);
      this.releaseHeldBalls();
      event.preventDefault();
    }

    handlePointerMove(event) {
      if (!this.enabled || !this.pointerActive) return;
      this.updatePaddleFromPointer(event);
      event.preventDefault();
    }

    handlePointerUp(event) {
      if (!this.enabled) return;
      if (this.canvas.releasePointerCapture) {
        try {
          this.canvas.releasePointerCapture(event.pointerId);
        } catch (error) {
          // ignore pointer capture errors
        }
      }
      this.pointerActive = false;
    }

    updatePaddleFromPointer(event) {
      const rect = this.canvas.getBoundingClientRect();
      if (rect.width === 0) return;
      const ratio = (event.clientX - rect.left) / rect.width;
      const clampedRatio = clamp(ratio, 0, 1);
      const center = clampedRatio * this.width;
      const minX = 0;
      const maxX = Math.max(0, this.width - this.paddle.width);
      this.paddle.x = clamp(center - this.paddle.width / 2, minX, maxX);
      this.paddle.xRatio = (this.paddle.x + this.paddle.width / 2) / this.width;
      this.balls.forEach(ball => {
        if (ball.attachedToPaddle) {
          this.updateBallFollowingPaddle(ball);
        }
      });
    }

    handleOverlayButtonClick() {
      if (!this.enabled) return;
      if (this.overlayAction === 'start') {
        this.startNewGame();
      } else if (this.overlayAction === 'resume') {
        this.resumeFromPause();
      } else if (this.overlayAction === 'next') {
        this.startNextLevel();
      } else if (this.overlayAction === 'restart') {
        this.startNewGame();
      }
    }

    onEnter() {
      if (!this.enabled) return;
      this.handleResize();
      if (this.pendingResume && !this.isOverlayVisible()) {
        this.showOverlay({
          message: PAUSE_OVERLAY_MESSAGE,
          buttonLabel: PAUSE_OVERLAY_BUTTON,
          action: 'resume'
        });
        this.pendingResume = false;
      }
      if (this.balls.some(ball => ball.inPlay) && !this.isOverlayVisible()) {
        this.startAnimation();
      } else {
        this.render();
      }
    }

    onLeave() {
      if (!this.enabled) return;
      if (this.balls.some(ball => ball.inPlay) && !this.isOverlayVisible()) {
        this.pendingResume = true;
        this.prepareServe();
      }
      this.stopAnimation();
    }

    setComboMessage(message, duration = 2500) {
      this.comboMessage = message;
      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      this.comboMessageExpiry = now + duration;
      if (this.comboLabel) {
        this.comboLabel.textContent = message;
      }
    }

    render(now = 0) {
      if (!this.enabled) return;
      const ctx = this.ctx;
      const renderTimestamp = Number.isFinite(now) && now > 0
        ? now
        : (typeof performance !== 'undefined' ? performance.now() : Date.now());
      const time = typeof renderTimestamp === 'number' ? renderTimestamp / 1000 : 0;
      const floorShieldActive = this.effects.has(POWER_UP_IDS.FLOOR);
      this.balls.forEach(ball => this.pruneBallTrail(ball, renderTimestamp));
      ctx.clearRect(0, 0, this.width, this.height);
      const background = ctx.createLinearGradient(0, 0, this.width, this.height);
      background.addColorStop(0, 'rgba(12, 16, 38, 0.85)');
      background.addColorStop(1, 'rgba(4, 6, 18, 0.95)');
      ctx.fillStyle = background;
      ctx.fillRect(0, 0, this.width, this.height);

      const hasRoundRect = typeof ctx.roundRect === 'function';
      const originalImageSmoothing = ctx.imageSmoothingEnabled;
      this.bricks.forEach(brick => {
        if (!brick.active || (brick.hidden && brick.type === BRICK_TYPES.GRAVITON)) return;
        const x = brick.relX * this.width;
        const y = brick.relY * this.height;
        const w = brick.relWidth * this.width;
        const h = brick.relHeight * this.height;
        if (brick.type === BRICK_TYPES.GRAVITON) {
          const gradient = ctx.createLinearGradient(x, y, x + w, y + h);
          brick.particle.colors.forEach((color, index) => {
            gradient.addColorStop(index / (brick.particle.colors.length - 1 || 1), color);
          });
          ctx.fillStyle = gradient;
          const radius = Math.min(16, h * 0.4);
          if (hasRoundRect) {
            ctx.beginPath();
            ctx.roundRect(x, y, w, h, radius);
            ctx.fill();
          } else {
            ctx.fillRect(x, y, w, h);
          }
          return;
        }

        let spriteDrawn = false;
        const spriteInfo = brick.particle?.sprite;
        if (spriteInfo?.sheet) {
          const sheet = BRICK_SPRITE_SHEETS[spriteInfo.sheet];
          const baseColumn = Array.isArray(spriteInfo.columns) && spriteInfo.columns.length > 0
            ? spriteInfo.columns[0]
            : spriteInfo.column;
          if (sheet && sheet.loaded && sheet.image && Number.isFinite(baseColumn)) {
            const frameWidth = sheet.frameWidth;
            const frameHeight = sheet.frameHeight;
            if (frameWidth > 0 && frameHeight > 0) {
              const columnIndex = Math.max(0, Math.min(sheet.columns - 1, Math.floor(baseColumn)));
              let rowIndex = 0;
              if (sheet.rows > 1) {
                const hitsTaken = Math.max(0, brick.maxHits - brick.hitsRemaining);
                rowIndex = Math.max(0, Math.min(sheet.rows - 1, hitsTaken));
              }
              const srcX = columnIndex * frameWidth;
              const srcY = rowIndex * frameHeight;
              ctx.imageSmoothingEnabled = false;
              ctx.drawImage(
                sheet.image,
                srcX,
                srcY,
                frameWidth,
                frameHeight,
                x,
                y,
                w,
                h
              );
              ctx.imageSmoothingEnabled = originalImageSmoothing;
              spriteDrawn = true;
            }
          }
        }

        if (!spriteDrawn) {
          const colors = brick.particle?.colors || ['#6c8cff', '#3b4da6'];
          const gradient = ctx.createLinearGradient(x, y, x, y + h);
          gradient.addColorStop(0, colors[0]);
          gradient.addColorStop(1, colors[1] || colors[0]);
          ctx.fillStyle = gradient;
          const radius = Math.min(16, h * 0.4);
          if (hasRoundRect) {
            ctx.beginPath();
            ctx.roundRect(x, y, w, h, radius);
            ctx.fill();
          } else {
            ctx.fillRect(x, y, w, h);
          }
          if (brick.particle?.symbol) {
            ctx.fillStyle = brick.particle.symbolColor || '#ffffff';
            const symbolScale = typeof brick.particle.symbolScale === 'number'
              ? brick.particle.symbolScale
              : 1;
            const baseFontSize = Math.max(12, h * 0.55);
            ctx.font = `${Math.max(12, baseFontSize * symbolScale)}px 'Orbitron', 'Inter', sans-serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(brick.particle.symbol, x + w / 2, y + h / 2 + h * 0.04);
          }
          if (brick.type === BRICK_TYPES.RESISTANT && brick.maxHits > 1 && brick.hitsRemaining > 0) {
            const ratio = brick.hitsRemaining / brick.maxHits;
            ctx.fillStyle = 'rgba(255, 255, 255, 0.18)';
            ctx.fillRect(x, y + h - h * ratio, w, h * ratio * 0.12);
          }
        }
      });
      ctx.imageSmoothingEnabled = originalImageSmoothing;

      this.powerUps.forEach(powerUp => {
        const visuals = POWER_UP_VISUALS[powerUp.type] || DEFAULT_POWER_UP_VISUAL;
        const widthMultiplier = typeof powerUp.widthMultiplier === 'number'
          ? powerUp.widthMultiplier
          : visuals.widthMultiplier || DEFAULT_POWER_UP_VISUAL.widthMultiplier;
        const height = powerUp.size;
        const width = height * widthMultiplier;
        const left = powerUp.x - width / 2;
        const top = powerUp.y - height / 2;
        const radius = Math.min(18, height * 0.45);
        const colors = visuals.gradient || DEFAULT_POWER_UP_VISUAL.gradient;
        const gradient = ctx.createLinearGradient(left, top, left, top + height);
        gradient.addColorStop(0, colors[0]);
        gradient.addColorStop(1, colors[1] || colors[0]);
        ctx.save();
        ctx.shadowColor = visuals.glow || DEFAULT_POWER_UP_VISUAL.glow;
        ctx.shadowBlur = height * 0.55;
        ctx.fillStyle = gradient;
        if (hasRoundRect) {
          ctx.beginPath();
          ctx.roundRect(left, top, width, height, radius);
          ctx.fill();
        } else {
          ctx.fillRect(left, top, width, height);
        }
        if (visuals.border) {
          ctx.lineWidth = Math.max(1.2, height * 0.12);
          ctx.strokeStyle = visuals.border;
          if (hasRoundRect) {
            ctx.stroke();
          } else {
            ctx.strokeRect(left, top, width, height);
          }
        }
        ctx.shadowBlur = 0;
        ctx.shadowColor = 'transparent';
        ctx.fillStyle = visuals.textColor || DEFAULT_POWER_UP_VISUAL.textColor;
        ctx.font = `${Math.max(14, height * 0.6)}px 'Orbitron', 'Inter', sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const symbol = powerUp.symbol || visuals.symbol || DEFAULT_POWER_UP_VISUAL.symbol;
        ctx.fillText(symbol, powerUp.x, powerUp.y + height * 0.04);
        ctx.restore();
      });

      this.lasers.forEach(laser => {
        const gradient = ctx.createLinearGradient(laser.x, laser.y, laser.x, laser.y - laser.height);
        gradient.addColorStop(0, 'rgba(180, 240, 255, 0.9)');
        gradient.addColorStop(1, 'rgba(120, 200, 255, 0.2)');
        ctx.fillStyle = gradient;
        ctx.fillRect(laser.x - laser.width / 2, laser.y - laser.height, laser.width, laser.height);
      });

      const floorHeight = Math.max(
        FLOOR_SHIELD_CONFIG.minHeightPx,
        this.height * FLOOR_SHIELD_CONFIG.heightRatio
      );
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      if (floorShieldActive) {
        const pulse = 0.55 + 0.25 * Math.sin(time * 6.2);
        const shieldGradient = ctx.createLinearGradient(
          0,
          this.height - floorHeight * 1.8,
          0,
          this.height
        );
        shieldGradient.addColorStop(0, 'rgba(40, 120, 255, 0)');
        shieldGradient.addColorStop(0.45, `rgba(90, 180, 255, ${0.22 + pulse * 0.28})`);
        shieldGradient.addColorStop(1, `rgba(180, 240, 255, ${0.48 + pulse * 0.35})`);
        ctx.fillStyle = shieldGradient;
        ctx.fillRect(0, this.height - floorHeight * 1.8, this.width, floorHeight * 1.8);
        ctx.strokeStyle = `rgba(200, 240, 255, ${0.55 + pulse * 0.4})`;
        ctx.lineWidth = Math.max(2, floorHeight * 0.22);
        ctx.beginPath();
        ctx.moveTo(0, this.height - floorHeight * 0.42);
        ctx.lineTo(this.width, this.height - floorHeight * 0.42);
        ctx.stroke();
      } else {
        const ember = 0.4 + 0.2 * Math.sin(time * 3.4);
        const floorGradient = ctx.createLinearGradient(0, this.height - floorHeight, 0, this.height);
        floorGradient.addColorStop(0, 'rgba(180, 30, 40, 0)');
        floorGradient.addColorStop(1, `rgba(255, 60, 60, ${0.35 + ember * 0.25})`);
        ctx.fillStyle = floorGradient;
        ctx.fillRect(0, this.height - floorHeight, this.width, floorHeight);
        ctx.strokeStyle = `rgba(220, 40, 50, ${0.45 + ember * 0.25})`;
        ctx.lineWidth = Math.max(1.5, floorHeight * 0.18);
        ctx.beginPath();
        ctx.moveTo(0, this.height - floorHeight * 0.18);
        ctx.lineTo(this.width, this.height - floorHeight * 0.18);
        ctx.stroke();
      }
      ctx.restore();

      const paddleCenterX = this.paddle.x + this.paddle.width / 2;
      const paddleScaleX = this.getPaddleStretchScale(renderTimestamp);
      const paddleBounceOffset = this.getPaddleBounceOffset(renderTimestamp);
      ctx.save();
      ctx.translate(paddleCenterX, 0);
      ctx.scale(paddleScaleX, 1);
      ctx.translate(-paddleCenterX, 0);
      if (paddleBounceOffset !== 0) {
        ctx.translate(0, paddleBounceOffset);
      }
      const paddleGradient = ctx.createLinearGradient(
        this.paddle.x,
        this.paddle.y,
        this.paddle.x,
        this.paddle.y + this.paddle.height
      );
      paddleGradient.addColorStop(0, 'rgba(120, 220, 255, 0.95)');
      paddleGradient.addColorStop(1, 'rgba(86, 140, 255, 0.85)');
      ctx.fillStyle = paddleGradient;
      const paddleRadius = Math.min(18, this.paddle.height * 1.2);
      if (hasRoundRect) {
        ctx.beginPath();
        ctx.roundRect(this.paddle.x, this.paddle.y, this.paddle.width, this.paddle.height, paddleRadius);
        ctx.fill();
      } else {
        ctx.fillRect(this.paddle.x, this.paddle.y, this.paddle.width, this.paddle.height);
      }
      ctx.restore();

      const speedTrailActive = this.effects.has(POWER_UP_IDS.SPEED);
      const trailFillColor = speedTrailActive ? '#ffe5cc' : '#ffffff';
      const trailGlowColor = speedTrailActive
        ? { r: 255, g: 120, b: 40 }
        : { r: 150, g: 220, b: 255 };
      const trailBlurBoost = speedTrailActive ? 1.35 : 1;
      const trailRadiusBoost = speedTrailActive ? 1.25 : 1;
      const energySpriteReady = ENERGY_BALL_SPRITE.loaded && ENERGY_BALL_SPRITE.image;

      this.balls.forEach(ball => {
        const electricSeed = typeof ball.electricSeed === 'number' ? ball.electricSeed : 0;
        const pulse = 0.55 + 0.35 * Math.sin(time * 7.1 + electricSeed);
        const trail = Array.isArray(ball.trail) ? ball.trail : [];
        if (trail.length > 0) {
          if (energySpriteReady) {
            const sprite = ENERGY_BALL_SPRITE;
            const variantCount = Math.max(1, sprite.colorCount || 1);
            const colorIndex = typeof ball.spriteIndex === 'number'
              ? Math.max(0, Math.min(variantCount - 1, Math.floor(ball.spriteIndex)))
              : 0;
            const srcX = colorIndex * sprite.frameWidth;
            const srcY = speedTrailActive ? sprite.speedTrailOffsetY : sprite.normalTrailOffsetY;
            const scale = ball.radius / 16;
            const destWidth = sprite.frameWidth * scale;
            const destHeight = sprite.trailHeight * scale;
            ctx.save();
            ctx.globalCompositeOperation = 'lighter';
            trail.forEach(point => {
              if (!point || typeof point.time !== 'number') return;
              const age = renderTimestamp - point.time;
              if (age < 0) return;
              const lifeRatio = clamp(1 - age / 260, 0, 1);
              if (lifeRatio <= 0) return;
              const alphaBase = speedTrailActive ? 0.35 : 0.28;
              const alpha = alphaBase * lifeRatio;
              if (alpha <= 0) return;
              const vx = typeof point.vx === 'number' ? point.vx : ball.vx;
              const vy = typeof point.vy === 'number' ? point.vy : ball.vy;
              let rotation = 0;
              if (vx !== 0 || vy !== 0) {
                rotation = Math.atan2(-vx, -vy);
              }
              ctx.save();
              ctx.translate(point.x, point.y);
              ctx.rotate(rotation);
              ctx.globalAlpha = alpha;
              ctx.drawImage(
                sprite.image,
                srcX,
                srcY,
                sprite.frameWidth,
                sprite.trailHeight,
                -destWidth / 2,
                0,
                destWidth,
                destHeight
              );
              ctx.restore();
            });
            ctx.restore();
          } else {
            ctx.save();
            ctx.globalCompositeOperation = 'lighter';
            ctx.fillStyle = trailFillColor;
            ctx.shadowOffsetX = 0;
            ctx.shadowOffsetY = 0;
            trail.forEach(point => {
              if (!point || typeof point.time !== 'number') return;
              const age = renderTimestamp - point.time;
              if (age < 0) return;
              const lifeRatio = clamp(1 - age / 260, 0, 1);
              if (lifeRatio <= 0) return;
              const alphaBase = speedTrailActive ? 0.12 : 0.08;
              const alphaRange = speedTrailActive ? 0.28 : 0.2;
              const alpha = alphaBase + lifeRatio * alphaRange;
              ctx.globalAlpha = alpha;
              const blur = ball.radius * (1.1 + (1 - lifeRatio) * 0.9 * trailBlurBoost);
              ctx.shadowBlur = blur;
              const glowAlpha = Math.min(1, 0.25 + lifeRatio * (speedTrailActive ? 0.45 : 0.35));
              ctx.shadowColor = `rgba(${trailGlowColor.r}, ${trailGlowColor.g}, ${trailGlowColor.b}, ${glowAlpha})`;
              const radius = ball.radius * (0.85 + lifeRatio * 0.35 * trailRadiusBoost);
              ctx.beginPath();
              ctx.arc(point.x, point.y, radius, 0, Math.PI * 2);
              ctx.fill();
            });
            ctx.restore();
          }
        }
        if (energySpriteReady) {
          const sprite = ENERGY_BALL_SPRITE;
          const variantCount = Math.max(1, sprite.colorCount || 1);
          const colorIndex = typeof ball.spriteIndex === 'number'
            ? Math.max(0, Math.min(variantCount - 1, Math.floor(ball.spriteIndex)))
            : 0;
          const scale = ball.radius / 16;
          const destWidth = sprite.frameWidth * scale;
          const destHeight = sprite.frameHeight * scale;
          const destX = ball.x - destWidth / 2;
          const destY = ball.y - destHeight / 2;
          ctx.save();
          ctx.globalCompositeOperation = 'lighter';
          const glowRadius = ball.radius * (1.25 + pulse * 0.3);
          ctx.globalAlpha = 0.3 + pulse * 0.25;
          ctx.fillStyle = 'rgba(255, 255, 255, 0.65)';
          ctx.beginPath();
          ctx.arc(ball.x, ball.y, glowRadius, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
          ctx.drawImage(
            sprite.image,
            colorIndex * sprite.frameWidth,
            0,
            sprite.frameWidth,
            sprite.frameHeight,
            destX,
            destY,
            destWidth,
            destHeight
          );
        } else {
          const gradient = ctx.createRadialGradient(
            ball.x - ball.radius / 3,
            ball.y - ball.radius / 3,
            ball.radius * 0.2,
            ball.x,
            ball.y,
            ball.radius
          );
          gradient.addColorStop(0, 'rgba(255, 255, 255, 0.95)');
          gradient.addColorStop(1, 'rgba(120, 200, 255, 0.9)');
          ctx.fillStyle = gradient;
          ctx.beginPath();
          ctx.arc(ball.x, ball.y, ball.radius, 0, Math.PI * 2);
          ctx.fill();
          ctx.save();
          ctx.globalCompositeOperation = 'lighter';
          const auraOuterRadius = ball.radius * (1.6 + 0.28 * Math.sin(time * 3.4 + electricSeed * 1.7));
          const auraGradient = ctx.createRadialGradient(
            ball.x,
            ball.y,
            ball.radius * 0.45,
            ball.x,
            ball.y,
            auraOuterRadius
          );
          auraGradient.addColorStop(0, `rgba(150, 220, 255, ${0.18 + pulse * 0.2})`);
          auraGradient.addColorStop(0.8, `rgba(90, 180, 255, ${0.08 + pulse * 0.18})`);
          auraGradient.addColorStop(1, 'rgba(30, 120, 255, 0)');
          ctx.fillStyle = auraGradient;
          ctx.beginPath();
          ctx.arc(ball.x, ball.y, auraOuterRadius, 0, Math.PI * 2);
          ctx.fill();

          const arcCount = 4;
          const arcLineWidth = Math.max(0.8, ball.radius * 0.18);
          ctx.lineWidth = arcLineWidth;
          ctx.lineCap = 'round';
          for (let i = 0; i < arcCount; i += 1) {
            const segmentSeed = electricSeed + i * 2.318;
            const baseAngle = segmentSeed + time * 4.2 + Math.sin(time * 2.1 + segmentSeed) * 0.4;
            const innerRadius = ball.radius * (0.92 + 0.12 * Math.sin(time * 5.3 + segmentSeed * 1.4));
            const outerRadius = ball.radius * (1.45 + 0.3 * Math.sin(time * 3.8 + segmentSeed * 2.2));
            ctx.beginPath();
            ctx.moveTo(
              ball.x + Math.cos(baseAngle) * innerRadius,
              ball.y + Math.sin(baseAngle) * innerRadius
            );
            const jaggedSteps = 3;
            for (let step = 1; step <= jaggedSteps; step += 1) {
              const progress = step / jaggedSteps;
              const noise = Math.sin((time + step) * 6.4 + segmentSeed * (step + 1)) * 0.35;
              const angle = baseAngle + noise * 0.55;
              const radius = innerRadius + (outerRadius - innerRadius) * progress + noise * ball.radius * 0.22;
              ctx.lineTo(ball.x + Math.cos(angle) * radius, ball.y + Math.sin(angle) * radius);
            }
            ctx.strokeStyle = `rgba(170, 240, 255, ${0.18 + pulse * 0.28})`;
            ctx.stroke();
          }

          const ringRadius = ball.radius * (1.18 + 0.08 * Math.sin(time * 4.6 + electricSeed));
          ctx.strokeStyle = `rgba(150, 220, 255, ${0.2 + pulse * 0.3})`;
          ctx.lineWidth = Math.max(1, ball.radius * 0.24);
          ctx.beginPath();
          ctx.arc(ball.x, ball.y, ringRadius, 0, Math.PI * 2);
          ctx.stroke();
          ctx.restore();
        }
      });
    }

    updateHud() {
      if (this.levelLabel) {
        this.levelLabel.textContent = `${this.level}`;
      }
      if (this.livesLabel) {
        this.livesLabel.textContent = `${this.lives}`;
      }
      if (this.scoreLabel) {
        this.scoreLabel.textContent = `${this.score}`;
      }
    }
  }

  window.ParticulesGame = ParticulesGame;
})();
