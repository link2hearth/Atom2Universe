(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const GAME_ID = 'escape';
  const CONFIG_PATH = 'config/arcade/escape.json';

  const TILE_TYPES = Object.freeze({
    WALL: 'wall',
    FLOOR: 'floor',
    START: 'start',
    EXIT: 'exit',
    DOOR: 'door',
    KEY: 'key',
    PLATE: 'plate',
    BONUS: 'bonus'
  });

  const DIRECTIONS = Object.freeze({
    NORTH: 'N',
    SOUTH: 'S',
    WEST: 'W',
    EAST: 'E'
  });

  const MOVE_VECTORS = Object.freeze({
    [DIRECTIONS.NORTH]: { row: -1, col: 0 },
    [DIRECTIONS.SOUTH]: { row: 1, col: 0 },
    [DIRECTIONS.WEST]: { row: 0, col: -1 },
    [DIRECTIONS.EAST]: { row: 0, col: 1 }
  });

  const PLAYER_ACTIONS = Object.freeze([
    { id: 'wait', row: 0, col: 0 },
    { id: 'north', row: -1, col: 0 },
    { id: 'south', row: 1, col: 0 },
    { id: 'west', row: 0, col: -1 },
    { id: 'east', row: 0, col: 1 }
  ]);

  const DEFAULT_CONFIG = Object.freeze({
    defaultDifficulty: 'medium',
    seed: Object.freeze({
      randomLength: 8,
      alphabet: 'ABCDEFGHJKMNPQRSTUVWXYZ23456789',
      paramNames: Object.freeze({ seed: 'seed', difficulty: 'd' })
    }),
    dailyChallenge: Object.freeze({
      enabled: true,
      offsetHours: 0,
      format: 'YYYYMMDD'
    }),
    generation: Object.freeze({
      maxRetryCount: 24,
      maxCycleAttempts: 120,
      cycleDensityLimits: Object.freeze({ min: 0.06, max: 0.22 }),
      minMazeSize: 7,
      maxMazeSize: 25
    }),
    validation: Object.freeze({
      maxTurns: 90,
      maxStates: 60000
    }),
    patrol: Object.freeze({
      maxLength: 22,
      maxAttempts: 80,
      minCorridorPadding: 1
    }),
    objects: Object.freeze({
      keyDoor: Object.freeze({
        enabled: true,
        minDistanceFromStart: 3,
        minKeyDoorSeparation: 3
      }),
      pressurePlate: Object.freeze({
        enabled: true,
        timerRange: Object.freeze({ min: 4, max: 6 }),
        pairsPerDifficulty: Object.freeze({
          easy: Object.freeze({ min: 0, max: 1 }),
          medium: Object.freeze({ min: 1, max: 2 }),
          hard: Object.freeze({ min: 2, max: 3 })
        })
      }),
      bonusOrbs: Object.freeze({
        maxCount: 6,
        culDeSacDepth: 2,
        perDifficulty: Object.freeze({
          easy: Object.freeze({ min: 2, max: 4 }),
          medium: Object.freeze({ min: 2, max: 5 }),
          hard: Object.freeze({ min: 3, max: 6 })
        })
      })
    }),
    difficulties: Object.freeze({
      easy: Object.freeze({
        mazeSize: Object.freeze({ min: 10, max: 11 }),
        extraConnections: Object.freeze({ min: 0.08, max: 0.12 }),
        optionalCycles: Object.freeze({ min: 2, max: 4 }),
        visionRange: Object.freeze({ short: 3, long: 3 }),
        patrols: Object.freeze({ min: 1, max: 2 })
      }),
      medium: Object.freeze({
        mazeSize: Object.freeze({ min: 12, max: 13 }),
        extraConnections: Object.freeze({ min: 0.1, max: 0.16 }),
        optionalCycles: Object.freeze({ min: 3, max: 5 }),
        visionRange: Object.freeze({ short: 4, long: 4 }),
        patrols: Object.freeze({ min: 2, max: 3 })
      }),
      hard: Object.freeze({
        mazeSize: Object.freeze({ min: 14, max: 15 }),
        extraConnections: Object.freeze({ min: 0.14, max: 0.2 }),
        optionalCycles: Object.freeze({ min: 4, max: 6 }),
        visionRange: Object.freeze({ short: 4, long: 5 }),
        patrols: Object.freeze({ min: 3, max: 4 })
      })
    })
  });

  const integerFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;
  const percentFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 })
    : null;

  const state = {
    config: DEFAULT_CONFIG,
    elements: null,
    difficulty: DEFAULT_CONFIG.defaultDifficulty,
    seed: '',
    level: null,
    messageData: null,
    languageHandlerAttached: false,
    languageHandler: null,
    ready: false,
    play: null,
    renderState: {
      playerEntity: null,
      guardEntities: [],
      visionTiles: [],
      visionIndicators: []
    },
    tileMap: null,
    entityMap: null,
    visionMap: null,
    inputHandlers: null,
    touchTracking: null,
    touchSkipClick: false
  };

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined' && typeof window.translateOrDefault === 'function') {
      return window.translateOrDefault(key, fallback, params);
    }
    if (typeof fallback === 'string' && fallback && params && typeof params === 'object') {
      return fallback.replace(/\{([^}]+)\}/g, (match, token) => {
        const trimmed = typeof token === 'string' ? token.trim() : '';
        if (trimmed && Object.prototype.hasOwnProperty.call(params, trimmed)) {
          return String(params[trimmed]);
        }
        return match;
      });
    }
    return typeof fallback === 'string' ? fallback : '';
  }

  function formatInteger(value) {
    if (!Number.isFinite(value)) {
      return '0';
    }
    return integerFormatter ? integerFormatter.format(value) : String(Math.trunc(value));
  }

  function formatPercent(ratio) {
    if (!Number.isFinite(ratio)) {
      return '0';
    }
    const value = ratio * 100;
    return percentFormatter ? percentFormatter.format(value) : value.toFixed(1);
  }

  function clampNumber(value, min, max, fallback) {
    const numeric = typeof value === 'number' ? value : Number.parseFloat(value);
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

  function clampInteger(value, min, max, fallback) {
    const numeric = typeof value === 'number' ? value : Number.parseInt(value, 10);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    const clamped = Math.min(Math.max(numeric, min), max);
    return Math.round(clamped);
  }

  function normalizeRange(rawRange, fallbackRange, min, max) {
    const source = rawRange && typeof rawRange === 'object' ? rawRange : null;
    const fallback = fallbackRange && typeof fallbackRange === 'object' ? fallbackRange : { min, max };
    const resolvedMin = clampInteger(source ? source.min : undefined, min, max, clampInteger(fallback.min, min, max, min));
    const resolvedMax = clampInteger(
      source ? source.max : undefined,
      resolvedMin,
      max,
      clampInteger(fallback.max, resolvedMin, max, resolvedMin)
    );
    return { min: resolvedMin, max: resolvedMax };
  }

  function normalizeFloatRange(rawRange, fallbackRange, min, max) {
    const source = rawRange && typeof rawRange === 'object' ? rawRange : null;
    const fallback = fallbackRange && typeof fallbackRange === 'object' ? fallbackRange : { min, max };
    const resolvedMin = clampNumber(source ? source.min : undefined, min, max, clampNumber(fallback.min, min, max, min));
    const resolvedMax = clampNumber(
      source ? source.max : undefined,
      resolvedMin,
      max,
      clampNumber(fallback.max, resolvedMin, max, resolvedMin)
    );
    return { min: resolvedMin, max: resolvedMax };
  }

  function normalizeDifficultyPairCounts(rawPairs, fallbackPairs) {
    const fallback = fallbackPairs && typeof fallbackPairs === 'object' ? fallbackPairs : {};
    const source = rawPairs && typeof rawPairs === 'object' ? rawPairs : {};
    const result = {};
    const keys = new Set([...Object.keys(fallback), ...Object.keys(source)]);
    keys.forEach(key => {
      const fallbackEntry = fallback[key] && typeof fallback[key] === 'object' ? fallback[key] : { min: 0, max: 6 };
      const entry = source[key] && typeof source[key] === 'object' ? source[key] : null;
      result[key] = normalizeRange(entry, fallbackEntry, 0, 6);
    });
    return result;
  }

  function normalizeDifficultyRangeMap(rawRanges, fallbackRanges, min, max) {
    const fallback = fallbackRanges && typeof fallbackRanges === 'object' ? fallbackRanges : {};
    const source = rawRanges && typeof rawRanges === 'object' ? rawRanges : {};
    const result = {};
    const keys = new Set([...Object.keys(fallback), ...Object.keys(source)]);
    keys.forEach(key => {
      const fallbackEntry = fallback[key] && typeof fallback[key] === 'object' ? fallback[key] : { min, max };
      const entry = source[key] && typeof source[key] === 'object' ? source[key] : null;
      result[key] = normalizeRange(entry, fallbackEntry, min, max);
    });
    return result;
  }

  function gcd(a, b) {
    let x = Math.abs(a);
    let y = Math.abs(b);
    while (y) {
      const temp = y;
      y = x % y;
      x = temp;
    }
    return x || 1;
  }

  function lcm(a, b) {
    if (a === 0 || b === 0) {
      return 0;
    }
    return Math.abs(a * b) / gcd(a, b);
  }

  function lcmArray(values) {
    if (!Array.isArray(values) || !values.length) {
      return 1;
    }
    return values.reduce((acc, value) => {
      const numeric = Number.isFinite(value) && value > 0 ? Math.floor(value) : 1;
      return acc ? lcm(acc, numeric || 1) : numeric || 1;
    }, 1);
  }

  function cyrb128(str) {
    let h1 = 1779033703;
    let h2 = 3144134277;
    let h3 = 1013904242;
    let h4 = 2773480762;
    const input = typeof str === 'string' ? str : '';
    for (let i = 0; i < input.length; i += 1) {
      const k = input.charCodeAt(i);
      h1 = Math.imul(h1 ^ k, 597399067);
      h2 = Math.imul(h2 ^ k, 2869860233);
      h3 = Math.imul(h3 ^ k, 951274213);
      h4 = Math.imul(h4 ^ k, 2716044179);
    }
    h1 = Math.imul(h1 ^ (h1 >>> 18), 597399067);
    h2 = Math.imul(h2 ^ (h2 >>> 22), 2869860233);
    h3 = Math.imul(h3 ^ (h3 >>> 17), 951274213);
    h4 = Math.imul(h4 ^ (h4 >>> 19), 2716044179);
    return [(h1 ^ h2 ^ h3 ^ h4) >>> 0, (h2 ^ h1) >>> 0, (h3 ^ h1) >>> 0, (h4 ^ h1) >>> 0];
  }

  function sfc32(a, b, c, d) {
    return function next() {
      a >>>= 0;
      b >>>= 0;
      c >>>= 0;
      d >>>= 0;
      const t = (a + b) | 0;
      a = b ^ (b >>> 9);
      b = (c + (c << 3)) | 0;
      c = (c << 21) | (c >>> 11);
      d = (d + 1) | 0;
      const result = (t + d) | 0;
      return (result >>> 0) / 4294967296;
    };
  }

  function createRng(seed) {
    const [a, b, c, d] = cyrb128(seed);
    return sfc32(a, b, c, d);
  }

  function normalizeConfig(rawConfig) {
    const source = rawConfig && typeof rawConfig === 'object' ? rawConfig : {};
    const defaultSeed = DEFAULT_CONFIG.seed;
    const seedSource = source.seed && typeof source.seed === 'object' ? source.seed : {};
    const seed = {
      randomLength: clampInteger(seedSource.randomLength, 4, 64, defaultSeed.randomLength),
      alphabet: typeof seedSource.alphabet === 'string' && seedSource.alphabet.trim()
        ? seedSource.alphabet.trim()
        : defaultSeed.alphabet,
      paramNames: {
        seed: typeof seedSource.paramNames?.seed === 'string' && seedSource.paramNames.seed.trim()
          ? seedSource.paramNames.seed.trim()
          : defaultSeed.paramNames.seed,
        difficulty: typeof seedSource.paramNames?.difficulty === 'string' && seedSource.paramNames.difficulty.trim()
          ? seedSource.paramNames.difficulty.trim()
          : defaultSeed.paramNames.difficulty
      }
    };
    const generationSource = source.generation && typeof source.generation === 'object' ? source.generation : {};
    const generation = {
      maxRetryCount: clampInteger(generationSource.maxRetryCount, 1, 200, DEFAULT_CONFIG.generation.maxRetryCount),
      maxCycleAttempts: clampInteger(generationSource.maxCycleAttempts, 1, 400, DEFAULT_CONFIG.generation.maxCycleAttempts),
      cycleDensityLimits: normalizeFloatRange(
        generationSource.cycleDensityLimits,
        DEFAULT_CONFIG.generation.cycleDensityLimits,
        0,
        0.45
      ),
      minMazeSize: clampInteger(generationSource.minMazeSize, 5, 99, DEFAULT_CONFIG.generation.minMazeSize),
      maxMazeSize: clampInteger(generationSource.maxMazeSize, 5, 199, DEFAULT_CONFIG.generation.maxMazeSize)
    };
    if (generation.maxMazeSize < generation.minMazeSize) {
      generation.maxMazeSize = generation.minMazeSize;
    }

    const dailySource = source.dailyChallenge && typeof source.dailyChallenge === 'object' ? source.dailyChallenge : {};
    const dailyChallenge = {
      enabled: dailySource.enabled !== false,
      offsetHours: clampNumber(dailySource.offsetHours, -48, 48, DEFAULT_CONFIG.dailyChallenge.offsetHours),
      format: typeof dailySource.format === 'string' && dailySource.format.trim()
        ? dailySource.format.trim()
        : DEFAULT_CONFIG.dailyChallenge.format
    };

    const difficultySource = source.difficulties && typeof source.difficulties === 'object' ? source.difficulties : {};
    const difficulties = {};
    Object.keys(DEFAULT_CONFIG.difficulties).forEach(key => {
      const fallback = DEFAULT_CONFIG.difficulties[key];
      const current = difficultySource[key] && typeof difficultySource[key] === 'object' ? difficultySource[key] : {};
      const mazeSize = normalizeRange(current.mazeSize, fallback.mazeSize, generation.minMazeSize, generation.maxMazeSize);
      const extraConnections = normalizeFloatRange(
        current.extraConnections,
        fallback.extraConnections,
        generation.cycleDensityLimits.min,
        generation.cycleDensityLimits.max
      );
      const optionalCycles = normalizeRange(
        current.optionalCycles,
        fallback.optionalCycles,
        0,
        Math.max(generation.maxMazeSize, fallback.optionalCycles.max)
      );
      const visionRange = {
        short: clampInteger(current.visionRange?.short, 1, 15, fallback.visionRange.short),
        long: clampInteger(current.visionRange?.long, 1, 15, fallback.visionRange.long)
      };
      const patrols = normalizeRange(current.patrols, fallback.patrols, 0, 10);
      difficulties[key] = {
        mazeSize,
        extraConnections,
        optionalCycles,
        visionRange,
        patrols
      };
    });

    const defaultDifficulty = typeof source.defaultDifficulty === 'string' && difficulties[source.defaultDifficulty]
      ? source.defaultDifficulty
      : DEFAULT_CONFIG.defaultDifficulty;

    const patrolSource = source.patrol && typeof source.patrol === 'object' ? source.patrol : {};
    const patrol = {
      maxLength: clampInteger(patrolSource.maxLength, 4, 120, DEFAULT_CONFIG.patrol.maxLength),
      maxAttempts: clampInteger(patrolSource.maxAttempts, 1, 400, DEFAULT_CONFIG.patrol.maxAttempts),
      minCorridorPadding: clampInteger(patrolSource.minCorridorPadding, 0, 5, DEFAULT_CONFIG.patrol.minCorridorPadding)
    };

    const objectsSource = source.objects && typeof source.objects === 'object' ? source.objects : {};
    const bonusSource = objectsSource.bonusOrbs && typeof objectsSource.bonusOrbs === 'object'
      ? objectsSource.bonusOrbs
      : {};
    const bonusMaxCount = clampInteger(
      bonusSource.maxCount,
      0,
      30,
      DEFAULT_CONFIG.objects.bonusOrbs.maxCount
    );
    const objects = {
      keyDoor: {
        enabled: objectsSource.keyDoor?.enabled !== false,
        minDistanceFromStart: clampInteger(
          objectsSource.keyDoor?.minDistanceFromStart,
          0,
          40,
          DEFAULT_CONFIG.objects.keyDoor.minDistanceFromStart
        ),
        minKeyDoorSeparation: clampInteger(
          objectsSource.keyDoor?.minKeyDoorSeparation,
          0,
          40,
          DEFAULT_CONFIG.objects.keyDoor.minKeyDoorSeparation
        )
      },
      pressurePlate: {
        enabled: objectsSource.pressurePlate?.enabled !== false,
        timerRange: normalizeRange(
          objectsSource.pressurePlate?.timerRange,
          DEFAULT_CONFIG.objects.pressurePlate.timerRange,
          1,
          20
        ),
        pairsPerDifficulty: normalizeDifficultyPairCounts(
          objectsSource.pressurePlate?.pairsPerDifficulty,
          DEFAULT_CONFIG.objects.pressurePlate.pairsPerDifficulty
        )
      },
      bonusOrbs: {
        maxCount: bonusMaxCount,
        culDeSacDepth: clampInteger(
          bonusSource.culDeSacDepth,
          0,
          12,
          DEFAULT_CONFIG.objects.bonusOrbs.culDeSacDepth
        ),
        perDifficulty: normalizeDifficultyRangeMap(
          bonusSource.perDifficulty,
          DEFAULT_CONFIG.objects.bonusOrbs.perDifficulty,
          0,
          Math.max(0, bonusMaxCount)
        )
      }
    };

    const validationSource = source.validation && typeof source.validation === 'object' ? source.validation : {};
    const validation = {
      maxTurns: clampInteger(validationSource.maxTurns, 10, 240, DEFAULT_CONFIG.validation.maxTurns),
      maxStates: clampInteger(validationSource.maxStates, 1000, 200000, DEFAULT_CONFIG.validation.maxStates)
    };

    return {
      defaultDifficulty,
      seed,
      dailyChallenge,
      generation,
      patrol,
      validation,
      objects,
      difficulties
    };
  }

  function getElements() {
    const section = document.getElementById(GAME_ID);
    if (!section) {
      return null;
    }
    return {
      section,
      root: section.querySelector('.escape'),
      board: document.getElementById('escapeBoard'),
      message: document.getElementById('escapeMessage'),
      difficultySelect: document.getElementById('escapeDifficultySelect'),
      generateButton: document.getElementById('escapeGenerateButton')
    };
  }

  function setMessage(key, fallback, params, options = {}) {
    if (!state.elements || !state.elements.message) {
      return;
    }
    state.messageData = { key, fallback, params: params || null, warning: !!options.warning };
    const text = translate(key, fallback, params);
    state.elements.message.textContent = text;
    state.elements.message.classList.toggle('escape__message--warning', !!options.warning);
  }

  function refreshMessage() {
    if (!state.messageData) {
      return;
    }
    const { key, fallback, params, warning } = state.messageData;
    setMessage(key, fallback, params, { warning });
  }

  function attachLanguageListener() {
    if (state.languageHandlerAttached) {
      return;
    }
    const handler = () => {
      if (state.play && !state.play.completed && !state.play.caught) {
        updateGameplayStatus();
      } else {
        refreshMessage();
      }
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandlerAttached = true;
    state.languageHandler = handler;
  }

  function removeLanguageListener() {
    if (!state.languageHandlerAttached || !state.languageHandler) {
      return;
    }
    window.removeEventListener('i18n:languagechange', state.languageHandler);
    state.languageHandlerAttached = false;
    state.languageHandler = null;
  }

  function sanitizeSeed(value) {
    const alphabet = (state.config?.seed?.alphabet || DEFAULT_CONFIG.seed.alphabet).toUpperCase();
    const allowed = new Set(alphabet.split(''));
    const raw = typeof value === 'string' ? value.toUpperCase().trim() : '';
    if (!raw) {
      return '';
    }
    let result = '';
    for (let i = 0; i < raw.length; i += 1) {
      const char = raw[i];
      if (allowed.has(char)) {
        result += char;
      }
    }
    return result;
  }

  function generateRandomSeed() {
    const alphabet = (state.config?.seed?.alphabet || DEFAULT_CONFIG.seed.alphabet).toUpperCase();
    const length = clampInteger(state.config?.seed?.randomLength, 4, 64, DEFAULT_CONFIG.seed.randomLength);
    const characters = alphabet.split('');
    if (!characters.length) {
      return 'ESCAPE';
    }
    const buffer = [];
    const cryptoObj = typeof window !== 'undefined' ? window.crypto || window.msCrypto : null;
    if (cryptoObj && typeof cryptoObj.getRandomValues === 'function') {
      const array = new Uint32Array(length);
      cryptoObj.getRandomValues(array);
      for (let i = 0; i < length; i += 1) {
        const index = array[i] % characters.length;
        buffer.push(characters[index]);
      }
    } else {
      for (let i = 0; i < length; i += 1) {
        const index = Math.floor(Math.random() * characters.length);
        buffer.push(characters[index]);
      }
    }
    return buffer.join('');
  }

  function normalizeDifficultyKey(rawDifficulty) {
    const key = typeof rawDifficulty === 'string' ? rawDifficulty.trim().toLowerCase() : '';
    if (key && state.config.difficulties[key]) {
      return key;
    }
    if (state.config.difficulties[state.config.defaultDifficulty]) {
      return state.config.defaultDifficulty;
    }
    const [firstKey] = Object.keys(state.config.difficulties);
    return firstKey || 'medium';
  }

  function parseQueryParameters() {
    if (typeof window === 'undefined' || typeof window.location === 'undefined' || typeof URL === 'undefined') {
      return { seed: '', difficulty: state.config.defaultDifficulty };
    }
    try {
      const url = new URL(window.location.href);
      const paramNames = state.config.seed?.paramNames || DEFAULT_CONFIG.seed.paramNames;
      const seedParam = paramNames.seed || 'seed';
      const difficultyParam = paramNames.difficulty || 'd';
      const rawSeed = url.searchParams.get(seedParam)
        || url.searchParams.get('escapeSeed')
        || '';
      const rawDifficulty = url.searchParams.get(difficultyParam)
        || url.searchParams.get('escapeDifficulty')
        || '';
      return {
        seed: sanitizeSeed(rawSeed),
        difficulty: normalizeDifficultyKey(rawDifficulty)
      };
    } catch (error) {
      console.warn('Escape URL parsing failed', error);
    }
    return {
      seed: '',
      difficulty: normalizeDifficultyKey(state.config.defaultDifficulty)
    };
  }

  function updateUrl(seed, difficulty) {
    if (typeof window === 'undefined' || typeof window.history === 'undefined' || typeof window.location === 'undefined') {
      return;
    }
    try {
      const url = new URL(window.location.href);
      const paramNames = state.config.seed?.paramNames || DEFAULT_CONFIG.seed.paramNames;
      const seedParam = paramNames.seed || 'seed';
      const difficultyParam = paramNames.difficulty || 'd';
      if (seed) {
        url.searchParams.set(seedParam, seed);
        url.searchParams.set('escapeSeed', seed);
      } else {
        url.searchParams.delete(seedParam);
        url.searchParams.delete('escapeSeed');
      }
      if (difficulty) {
        url.searchParams.set(difficultyParam, difficulty);
        url.searchParams.set('escapeDifficulty', difficulty);
      } else {
        url.searchParams.delete(difficultyParam);
        url.searchParams.delete('escapeDifficulty');
      }
      window.history.replaceState({}, '', url.toString());
    } catch (error) {
      console.warn('Escape URL update failed', error);
    }
  }

  function shuffle(array, rng) {
    for (let i = array.length - 1; i > 0; i -= 1) {
      const j = Math.floor(rng() * (i + 1));
      const temp = array[i];
      array[i] = array[j];
      array[j] = temp;
    }
    return array;
  }
  function generatePerfectMaze(width, height, rng) {
    const tileWidth = Math.max(1, width * 2 - 1);
    const tileHeight = Math.max(1, height * 2 - 1);
    const grid = Array.from({ length: tileHeight }, () => Array(tileWidth).fill(TILE_TYPES.WALL));
    const adjacency = Array.from({ length: height }, () => Array.from({ length: width }, () => new Set()));
    const visited = Array.from({ length: height }, () => Array(width).fill(false));
    const startRow = Math.floor(rng() * height);
    const startCol = Math.floor(rng() * width);
    const stack = [{ row: startRow, col: startCol }];
    visited[startRow][startCol] = true;
    grid[startRow * 2][startCol * 2] = TILE_TYPES.FLOOR;

    const directions = [
      { row: -1, col: 0 },
      { row: 1, col: 0 },
      { row: 0, col: -1 },
      { row: 0, col: 1 }
    ];

    while (stack.length > 0) {
      const current = stack[stack.length - 1];
      const neighbors = [];
      for (let i = 0; i < directions.length; i += 1) {
        const dir = directions[i];
        const nextRow = current.row + dir.row;
        const nextCol = current.col + dir.col;
        if (nextRow < 0 || nextRow >= height || nextCol < 0 || nextCol >= width) {
          continue;
        }
        if (!visited[nextRow][nextCol]) {
          neighbors.push({ row: nextRow, col: nextCol });
        }
      }
      if (!neighbors.length) {
        stack.pop();
        continue;
      }
      const next = neighbors[Math.floor(rng() * neighbors.length)];
      const wallRow = current.row + next.row;
      const wallCol = current.col + next.col;
      grid[wallRow][wallCol] = TILE_TYPES.FLOOR;
      grid[next.row * 2][next.col * 2] = TILE_TYPES.FLOOR;
      const currentKey = `${current.row},${current.col}`;
      const nextKey = `${next.row},${next.col}`;
      adjacency[current.row][current.col].add(nextKey);
      adjacency[next.row][next.col].add(currentKey);
      visited[next.row][next.col] = true;
      stack.push(next);
    }

    return { grid, adjacency };
  }

  function getCandidatesForCycles(adjacency, width, height) {
    const candidates = [];
    for (let row = 0; row < height; row += 1) {
      for (let col = 0; col < width; col += 1) {
        const key = `${row},${col}`;
        const neighbors = [
          { row: row + 1, col },
          { row, col: col + 1 }
        ];
        for (let i = 0; i < neighbors.length; i += 1) {
          const neighbor = neighbors[i];
          if (neighbor.row >= height || neighbor.col >= width) {
            continue;
          }
          const neighborKey = `${neighbor.row},${neighbor.col}`;
          if (adjacency[row][col].has(neighborKey)) {
            continue;
          }
          const wallRow = row + neighbor.row;
          const wallCol = col + neighbor.col;
          candidates.push({
            from: key,
            to: neighborKey,
            wallRow,
            wallCol
          });
        }
      }
    }
    return candidates;
  }

  function addCycleConnections(adjacency, grid, width, height, targetCount, rng) {
    if (targetCount <= 0) {
      return 0;
    }
    const candidates = getCandidatesForCycles(adjacency, width, height);
    if (!candidates.length) {
      return 0;
    }
    shuffle(candidates, rng);
    let added = 0;
    for (let i = 0; i < candidates.length && added < targetCount; i += 1) {
      const candidate = candidates[i];
      const [fromRow, fromCol] = candidate.from.split(',').map(Number);
      const [toRow, toCol] = candidate.to.split(',').map(Number);
      const neighborKey = `${toRow},${toCol}`;
      const currentKey = `${fromRow},${fromCol}`;
      if (adjacency[fromRow][fromCol].has(neighborKey)) {
        continue;
      }
      adjacency[fromRow][fromCol].add(neighborKey);
      adjacency[toRow][toCol].add(currentKey);
      grid[candidate.wallRow][candidate.wallCol] = TILE_TYPES.FLOOR;
      added += 1;
    }
    return added;
  }

  function bfsFarthest(startRow, startCol, adjacency, width, height) {
    const distances = Array.from({ length: height }, () => Array(width).fill(-1));
    const queue = [];
    let front = 0;
    distances[startRow][startCol] = 0;
    queue.push({ row: startRow, col: startCol });
    let farthest = { row: startRow, col: startCol, distance: 0 };

    while (front < queue.length) {
      const current = queue[front];
      front += 1;
      const currentDistance = distances[current.row][current.col];
      if (currentDistance > farthest.distance) {
        farthest = { row: current.row, col: current.col, distance: currentDistance };
      }
      const neighbors = adjacency[current.row][current.col];
      neighbors.forEach(key => {
        const [row, col] = key.split(',').map(Number);
        if (distances[row][col] !== -1) {
          return;
        }
        distances[row][col] = currentDistance + 1;
        queue.push({ row, col });
      });
    }

    return { farthest, distances };
  }

  function reconstructPath(start, end, distances, adjacency) {
    const path = [];
    let current = { row: end.row, col: end.col };
    path.push(current);
    while (current.row !== start.row || current.col !== start.col) {
      const neighbors = adjacency[current.row][current.col];
      let found = false;
      neighbors.forEach(key => {
        if (found) {
          return;
        }
        const [row, col] = key.split(',').map(Number);
        if (distances[row][col] === distances[current.row][current.col] - 1) {
          current = { row, col };
          path.push(current);
          found = true;
        }
      });
      if (!found) {
        break;
      }
    }
    path.reverse();
    return path;
  }

  function normalizeCellPosition(cell) {
    if (!cell || typeof cell !== 'object') {
      return { row: 0, col: 0 };
    }
    if (Number.isInteger(cell.row) && Number.isInteger(cell.col)) {
      return { row: cell.row, col: cell.col };
    }
    if (Number.isInteger(cell.cellRow) && Number.isInteger(cell.cellCol)) {
      return { row: cell.cellRow, col: cell.cellCol };
    }
    const parsedRow = Number.parseInt(cell.row, 10);
    const parsedCol = Number.parseInt(cell.col, 10);
    if (Number.isInteger(parsedRow) && Number.isInteger(parsedCol)) {
      return { row: parsedRow, col: parsedCol };
    }
    const parsedCellRow = Number.parseInt(cell.cellRow, 10);
    const parsedCellCol = Number.parseInt(cell.cellCol, 10);
    if (Number.isInteger(parsedCellRow) && Number.isInteger(parsedCellCol)) {
      return { row: parsedCellRow, col: parsedCellCol };
    }
    return { row: 0, col: 0 };
  }

  function computeDistances(adjacency, width, height, start, blockedCells = new Set()) {
    const distances = Array.from({ length: height }, () => Array(width).fill(-1));
    const queue = [];
    let front = 0;
    const startRow = Number.isInteger(start?.row) ? start.row : Number.parseInt(start?.row, 10);
    const startCol = Number.isInteger(start?.col) ? start.col : Number.parseInt(start?.col, 10);
    if (!Number.isInteger(startRow) || !Number.isInteger(startCol)) {
      return distances;
    }
    if (!isInsideCell(startRow, startCol, width, height)) {
      return distances;
    }
    const startKey = getCellKey(startRow, startCol);
    if (blockedCells.has(startKey)) {
      return distances;
    }
    distances[startRow][startCol] = 0;
    queue.push({ row: startRow, col: startCol });
    while (front < queue.length) {
      const current = queue[front];
      front += 1;
      const neighbors = adjacency[current.row][current.col];
      neighbors.forEach(key => {
        if (blockedCells.has(key)) {
          return;
        }
        const { row, col } = parseCellKey(key);
        if (distances[row][col] !== -1) {
          return;
        }
        distances[row][col] = distances[current.row][current.col] + 1;
        queue.push({ row, col });
      });
    }
    return distances;
  }

  function getCellKey(row, col) {
    return `${row},${col}`;
  }

  function parseCellKey(key) {
    if (typeof key !== 'string') {
      return { row: 0, col: 0 };
    }
    const [row, col] = key.split(',').map(Number);
    return { row, col };
  }

  function getTileCoords(row, col) {
    return { tileRow: row * 2, tileCol: col * 2 };
  }

  function getTileKey(row, col) {
    return `${row},${col}`;
  }

  function isInsideCell(row, col, width, height) {
    return row >= 0 && row < height && col >= 0 && col < width;
  }

  function cloneGrid(grid) {
    return grid.map(row => row.slice());
  }

  function directionToVector(direction) {
    const move = MOVE_VECTORS[direction];
    if (!move) {
      return { x: 0, y: 0 };
    }
    return { x: move.col, y: move.row };
  }

  function bresenhamLine(x0, y0, x1, y1) {
    let dx = Math.abs(x1 - x0);
    let dy = -Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1;
    const sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    let currentX = x0;
    let currentY = y0;
    const result = [];
    // We skip the very first cell (the guard tile) when consuming the line.
    while (true) {
      if (!(currentX === x0 && currentY === y0)) {
        result.push({ col: currentX, row: currentY });
      }
      if (currentX === x1 && currentY === y1) {
        break;
      }
      const e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        currentX += sx;
      }
      if (e2 <= dx) {
        err += dx;
        currentY += sy;
      }
    }
    return result;
  }

  function angleBetweenVectors(a, b) {
    const dot = a.x * b.x + a.y * b.y;
    const magA = Math.hypot(a.x, a.y) || 1;
    const magB = Math.hypot(b.x, b.y) || 1;
    const cos = Math.min(Math.max(dot / (magA * magB), -1), 1);
    return Math.acos(cos) * (180 / Math.PI);
  }

  function manhattanDistance(a, b) {
    return Math.abs(a.row - b.row) + Math.abs(a.col - b.col);
  }

  function computeTilePadding(grid) {
    const height = grid.length;
    const width = grid[0]?.length || 0;
    const padding = Array.from({ length: height }, () => Array(width).fill(Number.POSITIVE_INFINITY));
    const queue = [];
    let front = 0;
    for (let row = 0; row < height; row += 1) {
      for (let col = 0; col < width; col += 1) {
        if (grid[row][col] === TILE_TYPES.WALL) {
          padding[row][col] = 0;
          queue.push({ row, col });
        }
      }
    }
    const directions = [
      { row: -1, col: 0 },
      { row: 1, col: 0 },
      { row: 0, col: -1 },
      { row: 0, col: 1 }
    ];
    while (front < queue.length) {
      const current = queue[front];
      front += 1;
      const currentDistance = padding[current.row][current.col];
      for (let i = 0; i < directions.length; i += 1) {
        const dir = directions[i];
        const nextRow = current.row + dir.row;
        const nextCol = current.col + dir.col;
        if (nextRow < 0 || nextRow >= height || nextCol < 0 || nextCol >= width) {
          continue;
        }
        const tentative = currentDistance + 1;
        if (tentative < padding[nextRow][nextCol]) {
          padding[nextRow][nextCol] = tentative;
          queue.push({ row: nextRow, col: nextCol });
        }
      }
    }
    return padding;
  }

  function getCellPadding(paddingMap, row, col) {
    if (!paddingMap || !paddingMap.length) {
      return 0;
    }
    const coords = getTileCoords(row, col);
    const value = paddingMap[coords.tileRow]?.[coords.tileCol];
    if (!Number.isFinite(value)) {
      return 0;
    }
    return value;
  }

  function getNeighborCells(adjacency, row, col) {
    const neighbors = adjacency[row][col];
    return Array.from(neighbors).map(parseCellKey);
  }

  function determineDirection(from, to) {
    if (!from || !to) {
      return DIRECTIONS.NORTH;
    }
    if (to.row < from.row) {
      return DIRECTIONS.NORTH;
    }
    if (to.row > from.row) {
      return DIRECTIONS.SOUTH;
    }
    if (to.col < from.col) {
      return DIRECTIONS.WEST;
    }
    return DIRECTIONS.EAST;
  }
  function createLevel(seed, difficultyKey) {
    const difficulty = state.config.difficulties[difficultyKey] || state.config.difficulties[state.config.defaultDifficulty];
    const maxAttempts = clampInteger(
      state.config.generation?.maxRetryCount,
      1,
      200,
      DEFAULT_CONFIG.generation.maxRetryCount
    );
    let lastError = null;
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const attemptSeed = `${difficultyKey}|${seed}|${attempt}`;
      const rng = createRng(attemptSeed);
      try {
        const level = createLevelAttempt(seed, difficultyKey, difficulty, rng);
        if (level) {
          return level;
        }
      } catch (error) {
        lastError = error;
      }
    }
    if (lastError) {
      throw lastError;
    }
    throw new Error('Unable to generate Escape level');
  }

  function createLevelAttempt(seed, difficultyKey, difficulty, rng) {
    const level = buildBaseMaze(seed, difficultyKey, difficulty, rng);
    const decoration = decorateLevel(level, difficultyKey, rng);
    generateGuards(level, difficultyKey, rng);
    const bonusConfig = state.config.objects?.bonusOrbs;
    if (bonusConfig?.maxCount > 0) {
      placeBonusOrbs(level, bonusConfig, rng, difficultyKey, {
        keyDoorData: decoration?.keyDoorData || null,
        ensurePlateOrb: Boolean(decoration?.keyDoorData?.door?.plateId),
        ensureGuardOrb: true
      });
    }
    prepareRuntime(level);
    let validation = validateLevelLayout(level);
    if (!validation.success && level.guards.length) {
      const guardForbidden = [
        getCellKey(level.start.cellRow, level.start.cellCol),
        getCellKey(level.exit.cellRow, level.exit.cellCol)
      ];
      level.objects.keys.forEach(item => guardForbidden.push(getCellKey(item.cellRow, item.cellCol)));
      level.objects.doors.forEach(item => guardForbidden.push(getCellKey(item.cellRow, item.cellCol)));
      level.objects.plates.forEach(item => guardForbidden.push(getCellKey(item.cellRow, item.cellCol)));
      const difficultyConfig = state.config.difficulties[difficultyKey]
        || state.config.difficulties[state.config.defaultDifficulty];
      const patrolRange = difficultyConfig?.patrols;
      const minimumRequired = Math.max(
        difficultyKey === 'hard' ? 2 : 1,
        Math.max(1, patrolRange?.min ?? 1)
      );
      const fallbackGuards = forceMinimalGuards(level, minimumRequired, rng, { forbiddenCells: guardForbidden });
      if (fallbackGuards.length < minimumRequired) {
        throw new Error('Escape level unsolvable');
      }
      level.guards = fallbackGuards;
      prepareRuntime(level);
      validation = validateLevelLayout(level);
    }
    if (!validation.success) {
      throw new Error('Escape level unsolvable');
    }
    level.validation = validation;
    level.metrics = buildLevelMetrics(level, validation);
    return level;
  }

  function buildBaseMaze(seed, difficultyKey, difficulty, rng) {
    const sizeRange = difficulty.mazeSize;
    const cells = clampInteger(
      Math.floor(sizeRange.min + rng() * (sizeRange.max - sizeRange.min + 1)),
      sizeRange.min,
      sizeRange.max,
      sizeRange.min
    );
    const width = cells;
    const height = cells;
    const perfect = generatePerfectMaze(width, height, rng);
    const connectionsRange = difficulty.extraConnections;
    const ratio = clampNumber(
      connectionsRange.min + rng() * (connectionsRange.max - connectionsRange.min),
      state.config.generation.cycleDensityLimits.min,
      state.config.generation.cycleDensityLimits.max,
      connectionsRange.min
    );
    const desiredCycleCount = Math.round(width * height * ratio);
    const addedCycles = addCycleConnections(
      perfect.adjacency,
      perfect.grid,
      width,
      height,
      desiredCycleCount,
      rng
    );

    const optionalRange = difficulty.optionalCycles;
    if (optionalRange?.max > 0) {
      const optionalCount = clampInteger(
        Math.floor(optionalRange.min + rng() * (optionalRange.max - optionalRange.min + 1)),
        optionalRange.min,
        optionalRange.max,
        optionalRange.min
      );
      if (optionalCount > 0) {
        addCycleConnections(perfect.adjacency, perfect.grid, width, height, optionalCount, rng);
      }
    }

    const { farthest: firstFarthest } = bfsFarthest(0, 0, perfect.adjacency, width, height);
    const { farthest: secondFarthest, distances } = bfsFarthest(
      firstFarthest.row,
      firstFarthest.col,
      perfect.adjacency,
      width,
      height
    );
    const optimalPathCells = reconstructPath(firstFarthest, secondFarthest, distances, perfect.adjacency);

    const startTile = getTileCoords(firstFarthest.row, firstFarthest.col);
    const exitTile = getTileCoords(secondFarthest.row, secondFarthest.col);
    perfect.grid[startTile.tileRow][startTile.tileCol] = TILE_TYPES.START;
    perfect.grid[exitTile.tileRow][exitTile.tileCol] = TILE_TYPES.EXIT;

    const level = {
      seed,
      difficulty: difficultyKey,
      cellWidth: width,
      cellHeight: height,
      tileWidth: Math.max(1, width * 2 - 1),
      tileHeight: Math.max(1, height * 2 - 1),
      grid: perfect.grid,
      adjacency: perfect.adjacency,
      cycles: {
        desired: desiredCycleCount,
        added: addedCycles,
        ratio: width * height > 0 ? addedCycles / (width * height) : 0
      },
      start: {
        cellRow: firstFarthest.row,
        cellCol: firstFarthest.col,
        tileRow: startTile.tileRow,
        tileCol: startTile.tileCol
      },
      exit: {
        cellRow: secondFarthest.row,
        cellCol: secondFarthest.col,
        tileRow: exitTile.tileRow,
        tileCol: exitTile.tileCol
      },
      optimalPath: {
        length: Math.max(optimalPathCells.length - 1, 0),
        cells: optimalPathCells
      },
      objects: {
        keys: [],
        doors: [],
        plates: [],
        bonuses: []
      },
      guards: [],
      validation: null,
      metrics: null,
      runtime: null
    };

    const startCoords = normalizeCellPosition(level.start);
    level.distancesFromStart = computeDistances(level.adjacency, width, height, startCoords);
    return level;
  }

  function decorateLevel(level, difficultyKey, rng) {
    const objectsConfig = state.config.objects || {};
    let keyDoorData = null;
    let pairCount = 0;
    if (objectsConfig.keyDoor?.enabled && difficultyKey !== 'easy') {
      keyDoorData = placeKeyDoor(level, objectsConfig.keyDoor, rng);
    }
    if (
      objectsConfig.pressurePlate?.enabled
      && difficultyKey !== 'easy'
      && keyDoorData?.door
    ) {
      const plate = placePressurePlate(
        level,
        keyDoorData.door,
        objectsConfig.pressurePlate,
        difficultyKey,
        rng,
        keyDoorData.distancesWithoutDoor,
        { forcePlacement: true }
      );
      if (plate) {
        keyDoorData.door.pairIndex = pairCount;
        plate.pairIndex = pairCount;
        pairCount += 1;
      }
    }
    if (objectsConfig.pressurePlate?.enabled && difficultyKey !== 'easy') {
      const created = placeAdditionalPlateDoors(level, objectsConfig.pressurePlate, difficultyKey, rng, {
        existingPairs: pairCount,
        startIndex: 0
      });
      pairCount += created;
    }
    return { keyDoorData };
  }

  function placeKeyDoor(level, keyConfig, rng) {
    const pathCells = level.optimalPath?.cells || [];
    if (pathCells.length < 4) {
      return null;
    }
    const doorIndexMin = Math.max(1, Math.floor(pathCells.length * 0.35));
    const doorIndexMax = Math.max(doorIndexMin, Math.floor(pathCells.length * 0.75));
    const span = Math.max(doorIndexMax - doorIndexMin, 1);
    const selectedIndex = clampInteger(
      doorIndexMin + Math.floor(rng() * (span + 1)),
      1,
      pathCells.length - 2,
      doorIndexMin
    );
    const doorCell = pathCells[selectedIndex];
    const doorCoords = getTileCoords(doorCell.row, doorCell.col);
    level.grid[doorCoords.tileRow][doorCoords.tileCol] = TILE_TYPES.DOOR;
    const doorIndex = level.objects.doors.length;
    const door = {
      id: `door-${doorIndex}`,
      index: doorIndex,
      cellRow: doorCell.row,
      cellCol: doorCell.col,
      tileRow: doorCoords.tileRow,
      tileCol: doorCoords.tileCol,
      keyId: null,
      keyIndex: null,
      plateId: null,
      timerDuration: 0,
      timerIndex: null,
      kind: 'key'
    };
    level.objects.doors.push(door);

    const blocked = new Set([getCellKey(doorCell.row, doorCell.col)]);
    const startCoords = normalizeCellPosition(level.start);
    const distancesWithoutDoor = computeDistances(
      level.adjacency,
      level.cellWidth,
      level.cellHeight,
      startCoords,
      blocked
    );
    const doorDistance = level.distancesFromStart?.[doorCell.row]?.[doorCell.col] ?? Number.POSITIVE_INFINITY;
    const minDistance = Math.max(0, keyConfig.minDistanceFromStart || 0);
    const minSeparation = Math.max(0, keyConfig.minKeyDoorSeparation || 0);
    const pathSet = new Set(pathCells.map(cell => getCellKey(cell.row, cell.col)));

    const candidates = [];
    for (let row = 0; row < level.cellHeight; row += 1) {
      for (let col = 0; col < level.cellWidth; col += 1) {
        const key = getCellKey(row, col);
        if (key === getCellKey(level.start.cellRow, level.start.cellCol)) {
          continue;
        }
        if (key === getCellKey(level.exit.cellRow, level.exit.cellCol)) {
          continue;
        }
        if (key === getCellKey(door.cellRow, door.cellCol)) {
          continue;
        }
        const distance = distancesWithoutDoor[row][col];
        if (distance === -1 || distance < minDistance || distance >= doorDistance) {
          continue;
        }
        if (manhattanDistance({ row, col }, doorCell) < minSeparation) {
          continue;
        }
        const onPath = pathSet.has(key);
        candidates.push({ row, col, distance, onPath });
      }
    }

    if (!candidates.length) {
      level.grid[doorCoords.tileRow][doorCoords.tileCol] = TILE_TYPES.FLOOR;
      if (level.objects.doors.length > doorIndex) {
        level.objects.doors.splice(doorIndex, 1);
      } else {
        level.objects.doors.pop();
      }
      return null;
    }

    candidates.sort((a, b) => {
      if (a.onPath !== b.onPath) {
        return a.onPath ? 1 : -1;
      }
      return b.distance - a.distance;
    });
    const choiceIndex = Math.min(candidates.length - 1, Math.floor(rng() * Math.min(3, candidates.length)));
    const selected = candidates[choiceIndex];
    const keyCoords = getTileCoords(selected.row, selected.col);
    level.grid[keyCoords.tileRow][keyCoords.tileCol] = TILE_TYPES.KEY;
    const keyIndex = level.objects.keys.length;
    const keyObject = {
      id: `key-${keyIndex}`,
      index: keyIndex,
      cellRow: selected.row,
      cellCol: selected.col,
      tileRow: keyCoords.tileRow,
      tileCol: keyCoords.tileCol,
      doorId: door.id
    };
    level.objects.keys.push(keyObject);
    door.keyId = keyObject.id;
    door.keyIndex = keyObject.index;

    return { door, key: keyObject, distancesWithoutDoor };
  }

  function placePressurePlate(level, door, plateConfig, difficultyKey, rng, distancesWithoutDoor, options = {}) {
    if (!door) {
      return null;
    }
    const probability = options.forcePlacement ? 1 : difficultyKey === 'hard' ? 0.85 : 0.6;
    if (rng() > probability) {
      return null;
    }
    const doorKey = getCellKey(door.cellRow, door.cellCol);
    const doorDistance = level.distancesFromStart?.[door.cellRow]?.[door.cellCol] ?? Number.POSITIVE_INFINITY;
    const candidates = [];
    for (let row = 0; row < level.cellHeight; row += 1) {
      for (let col = 0; col < level.cellWidth; col += 1) {
        const key = getCellKey(row, col);
        if (key === getCellKey(level.start.cellRow, level.start.cellCol)) {
          continue;
        }
        if (key === getCellKey(level.exit.cellRow, level.exit.cellCol)) {
          continue;
        }
        if (key === doorKey) {
          continue;
        }
        if (level.objects.keys.some(k => getCellKey(k.cellRow, k.cellCol) === key)) {
          continue;
        }
        if (level.objects.doors.some(existing => getCellKey(existing.cellRow, existing.cellCol) === key)) {
          continue;
        }
        if (level.objects.plates.some(existing => getCellKey(existing.cellRow, existing.cellCol) === key)) {
          continue;
        }
        const distance = distancesWithoutDoor?.[row]?.[col];
        if (typeof distance !== 'number' || distance < 0 || distance >= doorDistance) {
          continue;
        }
        candidates.push({ row, col, distance, delta: Math.abs(doorDistance - distance) });
      }
    }
    if (!candidates.length) {
      return null;
    }
    candidates.sort((a, b) => a.delta - b.delta || b.distance - a.distance);
    const selected = candidates[Math.floor(rng() * Math.min(3, candidates.length))] || candidates[0];
    const timerRange = plateConfig.timerRange || { min: 4, max: 6 };
    const duration = clampInteger(
      Math.floor(timerRange.min + rng() * (timerRange.max - timerRange.min + 1)),
      timerRange.min,
      timerRange.max,
      timerRange.min
    );
    const coords = getTileCoords(selected.row, selected.col);
    level.grid[coords.tileRow][coords.tileCol] = TILE_TYPES.PLATE;
    const plateIndex = level.objects.plates.length;
    const plate = {
      id: `plate-${plateIndex}`,
      index: plateIndex,
      cellRow: selected.row,
      cellCol: selected.col,
      tileRow: coords.tileRow,
      tileCol: coords.tileCol,
      doorId: door.id,
      duration
    };
    level.objects.plates.push(plate);
    door.plateId = plate.id;
    door.timerDuration = duration;
    if (door.kind === 'key') {
      door.kind = 'hybrid';
    } else if (!door.kind || door.kind === 'plate') {
      door.kind = 'plate';
    }
    return plate;
  }

  function placeAdditionalPlateDoors(level, plateConfig, difficultyKey, rng, options = {}) {
    if (!plateConfig || !plateConfig.pairsPerDifficulty) {
      return 0;
    }
    const range = plateConfig.pairsPerDifficulty[difficultyKey];
    if (!range) {
      return 0;
    }
    const existingPairs = Math.max(0, options.existingPairs || 0);
    const startIndex = Math.max(0, options.startIndex || 0);
    const span = Math.max(range.max - range.min, 0);
    const targetTotal = clampInteger(
      Math.floor(range.min + rng() * (span + 1)),
      range.min,
      range.max,
      range.min
    );
    const desiredAdditional = Math.max(0, targetTotal - existingPairs);
    if (desiredAdditional <= 0) {
      return 0;
    }

    const pathCells = Array.isArray(level.optimalPath?.cells) ? level.optimalPath.cells : [];
    if (pathCells.length < 4) {
      return 0;
    }

    const usedDoorCells = new Set(level.objects.doors.map(door => getCellKey(door.cellRow, door.cellCol)));
    const startCoords = normalizeCellPosition(level.start);
    let created = 0;
    let attempts = 0;
    const maxAttempts = Math.max(pathCells.length * 3, 30);

    while (created < desiredAdditional && attempts < maxAttempts) {
      attempts += 1;
      const minIndex = Math.max(1, Math.floor(pathCells.length * 0.2));
      const maxIndex = Math.max(minIndex + 1, Math.floor(pathCells.length * 0.92));
      const candidateIndex = clampInteger(
        Math.floor(minIndex + rng() * (maxIndex - minIndex + 1)),
        minIndex,
        maxIndex,
        minIndex
      );
      const doorCell = pathCells[candidateIndex];
      if (!doorCell) {
        continue;
      }
      const doorKey = getCellKey(doorCell.row, doorCell.col);
      if (
        usedDoorCells.has(doorKey)
        || doorKey === getCellKey(level.start.cellRow, level.start.cellCol)
        || doorKey === getCellKey(level.exit.cellRow, level.exit.cellCol)
      ) {
        continue;
      }
      if (level.objects.keys.some(item => getCellKey(item.cellRow, item.cellCol) === doorKey)) {
        continue;
      }
      if (level.objects.plates.some(item => getCellKey(item.cellRow, item.cellCol) === doorKey)) {
        continue;
      }

      const coords = getTileCoords(doorCell.row, doorCell.col);
      const previousTile = level.grid[coords.tileRow][coords.tileCol];
      level.grid[coords.tileRow][coords.tileCol] = TILE_TYPES.DOOR;
      const doorIndex = level.objects.doors.length;
      const door = {
        id: `door-${doorIndex}`,
        index: doorIndex,
        cellRow: doorCell.row,
        cellCol: doorCell.col,
        tileRow: coords.tileRow,
        tileCol: coords.tileCol,
        keyId: null,
        keyIndex: null,
        plateId: null,
        timerDuration: 0,
        timerIndex: null,
        kind: 'plate'
      };
      level.objects.doors.push(door);
      usedDoorCells.add(doorKey);

      const blocked = new Set([doorKey]);
      const distancesWithoutDoor = computeDistances(
        level.adjacency,
        level.cellWidth,
        level.cellHeight,
        startCoords,
        blocked
      );

      const plate = placePressurePlate(
        level,
        door,
        plateConfig,
        difficultyKey,
        rng,
        distancesWithoutDoor,
        { forcePlacement: true }
      );

      if (!plate) {
        level.objects.doors.pop();
        usedDoorCells.delete(doorKey);
        level.grid[coords.tileRow][coords.tileCol] = previousTile;
        continue;
      }

      const pairIndex = startIndex + existingPairs + created;
      door.pairIndex = pairIndex;
      plate.pairIndex = pairIndex;
      created += 1;
    }

    return created;
  }

  function placeBonusOrbs(level, bonusConfig, rng, difficultyKey, options = {}) {
    const maxCount = Math.max(0, bonusConfig.maxCount || 0);
    if (maxCount <= 0) {
      return;
    }
    const occupied = new Set();
    level.objects.keys.forEach(item => occupied.add(getCellKey(item.cellRow, item.cellCol)));
    level.objects.doors.forEach(item => occupied.add(getCellKey(item.cellRow, item.cellCol)));
    level.objects.plates.forEach(item => occupied.add(getCellKey(item.cellRow, item.cellCol)));
    occupied.add(getCellKey(level.start.cellRow, level.start.cellCol));
    occupied.add(getCellKey(level.exit.cellRow, level.exit.cellCol));
    const culDepth = Math.max(0, bonusConfig.culDeSacDepth || 0);
    const deadEndCandidates = [];
    const guardCandidates = [];
    const doorCandidates = [];
    const corridorCandidates = [];
    const candidateKeys = new Set();
    const guardPathCells = new Set();
    level.guards.forEach(guard => {
      guard.path.forEach(segment => {
        guardPathCells.add(getCellKey(segment.row, segment.col));
      });
    });
    const doorDistances = options?.keyDoorData?.distancesWithoutDoor || null;
    for (let row = 0; row < level.cellHeight; row += 1) {
      for (let col = 0; col < level.cellWidth; col += 1) {
        const key = getCellKey(row, col);
        if (occupied.has(key)) {
          continue;
        }
        const distance = level.distancesFromStart?.[row]?.[col];
        if (typeof distance !== 'number' || distance < 0) {
          continue;
        }
        const degree = level.adjacency[row][col].size;
        const deadEnd = degree <= 1;
        const candidate = { row, col, key, distance, deadEnd };
        candidateKeys.add(key);
        const behindDoor = Boolean(doorDistances && doorDistances[row]?.[col] === -1);
        if (behindDoor) {
          doorCandidates.push({ ...candidate, behindDoor: true });
        }
        if (guardPathCells.has(key)) {
          guardCandidates.push({ ...candidate, onGuardPath: true });
        }
        if (deadEnd && distance >= culDepth) {
          deadEndCandidates.push(candidate);
        } else if (distance >= culDepth) {
          corridorCandidates.push(candidate);
        }
      }
    }
    const normalizedDifficulty = normalizeDifficultyKey(difficultyKey);
    const rangeConfig = bonusConfig.perDifficulty?.[normalizedDifficulty]
      || bonusConfig.perDifficulty?.[state.config.defaultDifficulty]
      || { min: 0, max: maxCount };
    const availableSlots = Math.min(Math.max(candidateKeys.size, 0), maxCount);
    const rangeMax = Math.min(
      Math.max(rangeConfig.max ?? rangeConfig.min ?? 0, rangeConfig.min ?? 0),
      maxCount,
      availableSlots
    );
    const rangeMin = Math.min(Math.max(rangeConfig.min ?? 0, 0), rangeMax);
    const difficultyFactor = normalizedDifficulty === 'hard' ? 1 : normalizedDifficulty === 'medium' ? 0.75 : 0.4;
    const baseTarget = Math.max(0, Math.min(rangeMax, Math.round(deadEndCandidates.length * 0.5 * difficultyFactor)));
    const requireDoorOrb = options.ensurePlateOrb && doorCandidates.length > 0;
    const requireGuardOrb = options.ensureGuardOrb && guardCandidates.length > 0;
    const specialRequirement = Math.min(rangeMax, (requireDoorOrb ? 1 : 0) + (requireGuardOrb ? 1 : 0));
    let targetCount = clampInteger(baseTarget, rangeMin, rangeMax, rangeMin);
    targetCount = Math.max(targetCount, specialRequirement);
    targetCount = Math.max(targetCount, rangeMin);
    targetCount = Math.min(targetCount, rangeMax);
    if (targetCount <= 0) {
      return;
    }

    function placeOrb(candidate) {
      if (!candidate || occupied.has(candidate.key)) {
        return false;
      }
      const coords = getTileCoords(candidate.row, candidate.col);
      level.grid[coords.tileRow][coords.tileCol] = TILE_TYPES.BONUS;
      const bonusIndex = level.objects.bonuses.length;
      level.objects.bonuses.push({
        id: `bonus-${bonusIndex}`,
        index: bonusIndex,
        cellRow: candidate.row,
        cellCol: candidate.col,
        tileRow: coords.tileRow,
        tileCol: coords.tileCol
      });
      occupied.add(candidate.key);
      return true;
    }

    function tryPlaceFromList(list, { preferDeadEnd = false } = {}) {
      if (!Array.isArray(list) || !list.length) {
        return false;
      }
      const shuffled = list.slice();
      shuffle(shuffled, rng);
      const primary = preferDeadEnd ? shuffled.filter(item => item.deadEnd) : shuffled;
      const secondary = preferDeadEnd ? shuffled.filter(item => !item.deadEnd) : [];
      for (let i = 0; i < primary.length; i += 1) {
        if (placeOrb(primary[i])) {
          return true;
        }
      }
      for (let i = 0; i < secondary.length; i += 1) {
        if (placeOrb(secondary[i])) {
          return true;
        }
      }
      return false;
    }

    let placed = 0;
    if (requireDoorOrb && placed < targetCount) {
      if (tryPlaceFromList(doorCandidates, { preferDeadEnd: true })) {
        placed += 1;
      }
    }
    if (requireGuardOrb && placed < targetCount) {
      if (tryPlaceFromList(guardCandidates, { preferDeadEnd: true })) {
        placed += 1;
      }
    }

    const fillerPools = [
      { list: deadEndCandidates, options: { preferDeadEnd: true } },
      { list: doorCandidates, options: { preferDeadEnd: true } },
      { list: guardCandidates, options: { preferDeadEnd: true } },
      { list: corridorCandidates, options: { preferDeadEnd: false } }
    ];

    while (placed < targetCount) {
      let progress = false;
      for (let i = 0; i < fillerPools.length && placed < targetCount; i += 1) {
        const pool = fillerPools[i];
        if (tryPlaceFromList(pool.list, pool.options)) {
          placed += 1;
          progress = true;
        }
      }
      if (!progress) {
        break;
      }
    }
  }

  function generateGuards(level, difficultyKey, rng) {
    const difficulty = state.config.difficulties[difficultyKey] || state.config.difficulties[state.config.defaultDifficulty];
    const patrolRange = difficulty?.patrols;
    if (!patrolRange || patrolRange.max <= 0) {
      level.guards = [];
      return;
    }

    const baseForbidden = [
      getCellKey(level.start.cellRow, level.start.cellCol),
      getCellKey(level.exit.cellRow, level.exit.cellCol)
    ];
    level.objects.keys.forEach(item => baseForbidden.push(getCellKey(item.cellRow, item.cellCol)));
    level.objects.doors.forEach(item => baseForbidden.push(getCellKey(item.cellRow, item.cellCol)));
    level.objects.plates.forEach(item => baseForbidden.push(getCellKey(item.cellRow, item.cellCol)));

    const paddingMap = computeTilePadding(level.grid);
    const basePadding = Math.max(0, (state.config.patrol?.minCorridorPadding || 0) + 1);
    const minGuards = Math.max(0, patrolRange.min);
    const maxGuards = Math.max(minGuards, patrolRange.max);
    const shortRange = Math.max(1, difficulty?.visionRange?.short ?? DEFAULT_CONFIG.difficulties.medium.visionRange.short);
    const longRange = Math.max(shortRange, difficulty?.visionRange?.long ?? shortRange);

    const attemptConfigs = [];
    const seenConfigs = new Set();
    [
      { minPadding: basePadding, minGuards, maxGuards, minCycleLength: 4 },
      {
        minPadding: Math.max(0, basePadding - 1),
        minGuards: Math.max(1, Math.min(maxGuards, minGuards)),
        maxGuards,
        minCycleLength: 4
      },
      {
        minPadding: Math.max(0, basePadding - 2),
        minGuards: Math.max(0, Math.min(maxGuards, Math.floor(minGuards / 2))),
        maxGuards,
        minCycleLength: 3,
        rangeOverride: shortRange,
        angleOverride: 60
      },
      {
        minPadding: 0,
        minGuards: 0,
        maxGuards,
        minCycleLength: 3,
        rangeOverride: shortRange,
        angleOverride: 60
      }
    ].forEach(config => {
      const key = `${config.minPadding}|${config.minGuards}|${config.minCycleLength}|${config.rangeOverride ?? 'd'}|${config.angleOverride ?? 'd'}`;
      if (!seenConfigs.has(key)) {
        seenConfigs.add(key);
        attemptConfigs.push(config);
      }
    });

    let bestGuards = [];
    for (let i = 0; i < attemptConfigs.length; i += 1) {
      const attempt = attemptConfigs[i];
      const guards = attemptGuardGeneration(level, difficultyKey, rng, {
        ...attempt,
        paddingMap,
        forbiddenCells: baseForbidden,
        defaultLongRange: longRange
      });
      if (guards.length > bestGuards.length) {
        bestGuards = guards;
      }
      const requirementMet = attempt.minGuards > 0 ? guards.length >= attempt.minGuards : guards.length > 0;
      if (requirementMet) {
        level.guards = guards;
        return;
      }
    }

    level.guards = bestGuards;
    const minimumRequired = difficultyKey === 'hard' ? 2 : 1;
    if (level.guards.length < minimumRequired) {
      const fallback = attemptGuardGeneration(level, difficultyKey, rng, {
        minPadding: 0,
        minGuards: minimumRequired,
        maxGuards: Math.max(minimumRequired, maxGuards),
        minCycleLength: 3,
        paddingMap,
        forbiddenCells: baseForbidden,
        rangeOverride: shortRange,
        defaultLongRange: longRange,
        angleOverride: difficultyKey === 'hard' ? 90 : 75
      });
      if (fallback.length >= minimumRequired) {
        level.guards = fallback;
      }
    }
    if (level.guards.length < minimumRequired) {
      const forced = forceMinimalGuards(level, minimumRequired, rng, { forbiddenCells: baseForbidden });
      if (forced.length >= minimumRequired) {
        level.guards = forced;
      }
    }
  }

  function attemptGuardGeneration(level, difficultyKey, rng, options = {}) {
    const difficulty = state.config.difficulties[difficultyKey] || state.config.difficulties[state.config.defaultDifficulty];
    const patrolRange = difficulty?.patrols;
    if (!patrolRange || patrolRange.max <= 0) {
      return [];
    }

    const paddingMap = options.paddingMap || computeTilePadding(level.grid);
    const forbiddenCells = Array.isArray(options.forbiddenCells) ? options.forbiddenCells : [];
    const forbidden = new Set(forbiddenCells);
    const defaultMinPadding = (state.config.patrol?.minCorridorPadding || 0) + 1;
    const minPadding = Math.max(0, options.minPadding ?? defaultMinPadding);
    const maxGuards = Math.max(0, Math.min(patrolRange.max, options.maxGuards ?? patrolRange.max));
    const minGuards = Math.max(0, Math.min(maxGuards, options.minGuards ?? patrolRange.min));
    if (maxGuards <= 0) {
      return [];
    }

    const targetCount = clampInteger(
      Math.floor(minGuards + rng() * (Math.max(maxGuards - minGuards, 0) + 1)),
      minGuards,
      maxGuards,
      minGuards
    );
    if (targetCount <= 0) {
      return [];
    }

    const candidates = [];
    for (let row = 0; row < level.cellHeight; row += 1) {
      for (let col = 0; col < level.cellWidth; col += 1) {
        const key = getCellKey(row, col);
        if (forbidden.has(key)) {
          continue;
        }
        const degree = level.adjacency[row][col].size;
        if (degree < 2) {
          continue;
        }
        const paddingValue = getCellPadding(paddingMap, row, col);
        if (paddingValue < minPadding) {
          continue;
        }
        candidates.push({ row, col });
      }
    }

    if (!candidates.length) {
      return [];
    }

    const guards = [];
    const occupied = new Set();
    const maxAttempts = Math.max(
      1,
      options.maxAttempts
        ?? state.config.patrol?.maxAttempts
        ?? DEFAULT_CONFIG.patrol.maxAttempts
    );
    const minCycleLength = Math.max(3, options.minCycleLength || 4);
    const requestedMaxLength = Number.isFinite(options.maxCycleLength)
      ? Math.max(options.maxCycleLength, minCycleLength)
      : undefined;
    let attempts = 0;
    while (guards.length < targetCount && attempts < maxAttempts) {
      attempts += 1;
      const candidate = candidates[Math.floor(rng() * candidates.length)];
      const startKey = getCellKey(candidate.row, candidate.col);
      if (occupied.has(startKey)) {
        continue;
      }
      const cycle = buildGuardCycle(level, candidate, rng, forbidden, occupied, {
        minLength: minCycleLength,
        maxLength: requestedMaxLength
      });
      if (!cycle || cycle.length < minCycleLength) {
        continue;
      }
      const guardIndex = guards.length;
      const path = cycle.map((cell, index) => {
        const next = cycle[(index + 1) % cycle.length];
        return {
          row: cell.row,
          col: cell.col,
          dir: determineDirection(cell, next)
        };
      });
      cycle.forEach(cell => occupied.add(getCellKey(cell.row, cell.col)));
      const shortRange = Math.max(1, difficulty?.visionRange?.short ?? DEFAULT_CONFIG.difficulties.medium.visionRange.short);
      const longRange = Math.max(shortRange, options.defaultLongRange ?? difficulty?.visionRange?.long ?? shortRange);
      const defaultRange = difficultyKey === 'easy' ? shortRange : longRange;
      const rangeValue = Math.max(1, options.rangeOverride ?? defaultRange);
      const defaultAngle = difficultyKey === 'hard' ? 90 : difficultyKey === 'medium' ? 75 : 60;
      const angleValue = Math.max(30, options.angleOverride ?? defaultAngle);
      guards.push({
        id: `guard-${guardIndex}`,
        path,
        range: rangeValue,
        angle: angleValue,
        templates: [],
        visionUnion: new Set()
      });
    }

    return guards;
  }

  function forceMinimalGuards(level, requirement, rng, context = {}) {
    const target = Math.max(0, requirement);
    if (target <= 0) {
      return [];
    }
    const forbidden = new Set(Array.isArray(context.forbiddenCells) ? context.forbiddenCells : []);
    const occupied = new Set();
    const candidates = [];
    for (let row = 0; row < level.cellHeight; row += 1) {
      for (let col = 0; col < level.cellWidth; col += 1) {
        const key = getCellKey(row, col);
        if (forbidden.has(key)) {
          continue;
        }
        const degree = level.adjacency[row][col].size;
        if (degree === 0) {
          continue;
        }
        candidates.push({ row, col, degree });
      }
    }
    if (!candidates.length) {
      return [];
    }
    shuffle(candidates, rng);
    const difficulty = level.difficulty || state.config.defaultDifficulty;
    const difficultyConfig = state.config.difficulties[difficulty]
      || DEFAULT_CONFIG.difficulties[difficulty]
      || DEFAULT_CONFIG.difficulties[state.config.defaultDifficulty]
      || DEFAULT_CONFIG.difficulties.medium;
    const shortRange = Math.max(1, difficultyConfig?.visionRange?.short ?? DEFAULT_CONFIG.difficulties.medium.visionRange.short);
    const longRange = Math.max(shortRange, difficultyConfig?.visionRange?.long ?? shortRange);
    const result = [];
    let index = 0;
    while (result.length < target && index < candidates.length) {
      const candidate = candidates[index];
      index += 1;
      const key = getCellKey(candidate.row, candidate.col);
      if (occupied.has(key)) {
        continue;
      }
      occupied.add(key);
      const guardIndex = result.length;
      const path = [{ row: candidate.row, col: candidate.col, dir: DIRECTIONS.EAST }];
      result.push({
        id: `guard-forced-${guardIndex}`,
        path,
        range: difficulty === 'easy' ? shortRange : longRange,
        angle: difficulty === 'hard' ? 90 : 75,
        templates: [],
        visionUnion: new Set()
      });
    }
    return result;
  }

  function buildGuardCycle(level, startCell, rng, forbidden, occupied, options = {}) {
    const configuredMax = Number.isFinite(state.config.patrol?.maxLength)
      ? state.config.patrol.maxLength
      : DEFAULT_CONFIG.patrol.maxLength;
    const baseMax = Math.max(4, configuredMax || 22);
    const requestedMax = Number.isFinite(options.maxLength) ? Math.max(options.maxLength, 4) : baseMax;
    const maxLength = Math.max(4, Math.min(baseMax, requestedMax));
    const minLength = Math.max(3, Math.min(options.minLength || 4, maxLength));
    const startKey = getCellKey(startCell.row, startCell.col);
    const stack = [{ cell: startCell, prev: null }];
    const visited = new Set([startKey]);
    const maxIterations = Math.max(100, level.cellWidth * level.cellHeight * maxLength);
    let iterations = 0;
    while (stack.length > 0 && stack.length <= maxLength) {
      iterations += 1;
      if (iterations > maxIterations) {
        break;
      }
      const current = stack[stack.length - 1];
      const neighbors = getNeighborCells(level.adjacency, current.cell.row, current.cell.col);
      shuffle(neighbors, rng);
      let advanced = false;
      for (let i = 0; i < neighbors.length; i += 1) {
        const neighbor = neighbors[i];
        const neighborKey = getCellKey(neighbor.row, neighbor.col);
        if (forbidden.has(neighborKey) || occupied.has(neighborKey)) {
          continue;
        }
        if (current.prev && neighbor.row === current.prev.row && neighbor.col === current.prev.col && neighbors.length > 1) {
          continue;
        }
        if (neighborKey === startKey && stack.length >= minLength) {
          return stack.map(entry => entry.cell);
        }
        if (!visited.has(neighborKey) && stack.length < maxLength) {
          visited.add(neighborKey);
          stack.push({ cell: neighbor, prev: current.cell });
          advanced = true;
          break;
        }
      }
      if (!advanced) {
        const removed = stack.pop();
        if (!removed) {
          break;
        }
        if (stack.length === 0) {
          break;
        }
        if (removed.cell !== startCell) {
          visited.delete(getCellKey(removed.cell.row, removed.cell.col));
        }
      }
    }
    return null;
  }

  function buildGuardVisionTemplate(level, guard, step) {
    const segment = guard.path[step];
    const directionVector = directionToVector(segment.dir);
    const template = [];
    for (let dy = -guard.range; dy <= guard.range; dy += 1) {
      for (let dx = -guard.range; dx <= guard.range; dx += 1) {
        if (dx === 0 && dy === 0) {
          continue;
        }
        const distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (distance > guard.range) {
          continue;
        }
        const target = { row: segment.row + dy, col: segment.col + dx };
        if (!isInsideCell(target.row, target.col, level.cellWidth, level.cellHeight)) {
          continue;
        }
        const angle = angleBetweenVectors(directionVector, { x: dx, y: dy });
        if (angle > guard.angle / 2) {
          continue;
        }
        const guardTile = getTileCoords(segment.row, segment.col);
        const targetTile = getTileCoords(target.row, target.col);
        const rayTiles = bresenhamLine(guardTile.tileCol, guardTile.tileRow, targetTile.tileCol, targetTile.tileRow);
        template.push({
          cell: target,
          tiles: rayTiles,
          distance
        });
      }
    }
    template.sort((a, b) => a.distance - b.distance);
    return template;
  }

  function prepareRuntime(level) {
    const keyByCell = new Map();
    level.objects.keys.forEach(key => {
      keyByCell.set(getCellKey(key.cellRow, key.cellCol), key);
    });
    const doorByCell = new Map();
    const doorByTile = new Map();
    level.objects.doors.forEach((door, index) => {
      door.index = index;
      doorByCell.set(getCellKey(door.cellRow, door.cellCol), door);
      doorByTile.set(getTileKey(door.tileRow, door.tileCol), door);
    });
    const plateByCell = new Map();
    const plateByTile = new Map();
    level.objects.plates.forEach((plate, index) => {
      plate.index = index;
      plateByCell.set(getCellKey(plate.cellRow, plate.cellCol), plate);
      plateByTile.set(getTileKey(plate.tileRow, plate.tileCol), plate);
    });
    const bonusByCell = new Map();
    level.objects.bonuses.forEach((bonus, index) => {
      bonus.index = index;
      bonusByCell.set(getCellKey(bonus.cellRow, bonus.cellCol), bonus);
    });

    let timerIndex = 0;
    level.objects.doors.forEach(door => {
      if (door.plateId) {
        door.timerIndex = timerIndex;
        timerIndex += 1;
      }
    });
    level.objects.plates.forEach(plate => {
      const door = level.objects.doors.find(item => item.id === plate.doorId);
      if (door) {
        plate.doorIndex = door.index;
        plate.timerIndex = door.timerIndex;
      }
    });

    const guardPathTiles = new Set();
    const guardStartTiles = new Set();
    const guardVisionTiles = new Set();

    level.guards.forEach(guard => {
      const templates = [];
      guard.path.forEach((segment, step) => {
        const template = buildGuardVisionTemplate(level, guard, step);
        templates.push(template);
        template.forEach(ray => {
          guard.visionUnion.add(getCellKey(ray.cell.row, ray.cell.col));
        });
        const coords = getTileCoords(segment.row, segment.col);
        guardPathTiles.add(getTileKey(coords.tileRow, coords.tileCol));
        if (step === 0) {
          guardStartTiles.add(getTileKey(coords.tileRow, coords.tileCol));
        }
      });
      guard.templates = templates;
      guard.visionUnion.forEach(cellKey => {
        const { row, col } = parseCellKey(cellKey);
        const coords = getTileCoords(row, col);
        guardVisionTiles.add(getTileKey(coords.tileRow, coords.tileCol));
      });
    });

    const guardCycle = level.guards.length ? lcmArray(level.guards.map(guard => guard.path.length)) : 1;
    const guardStates = Array.from({ length: guardCycle }, (_, phase) => level.guards.map(guard => {
      const step = phase % guard.path.length;
      const segment = guard.path[step];
      const next = guard.path[(step + 1) % guard.path.length];
      return {
        row: segment.row,
        col: segment.col,
        dir: segment.dir,
        nextRow: next.row,
        nextCol: next.col,
        step
      };
    }));

    level.runtime = {
      keyByCell,
      doorByCell,
      doorByTile,
      plateByCell,
      plateByTile,
      bonusByCell,
      timerCount: timerIndex,
      guardCycle,
      guardStates,
      guardPathTiles,
      guardStartTiles,
      guardVisionTiles
    };
  }

  function isDoorOpen(door, keysMask, timers) {
    if (!door) {
      return true;
    }
    if (door.keyIndex !== null && door.keyIndex !== undefined) {
      if ((keysMask & (1 << door.keyIndex)) !== 0) {
        return true;
      }
    }
    if (door.timerIndex !== null && door.timerIndex !== undefined) {
      if ((timers?.[door.timerIndex] || 0) > 0) {
        return true;
      }
    }
    return false;
  }

  function isTileBlockingForVision(level, tileRow, tileCol, keysMask, timers) {
    if (tileRow < 0 || tileRow >= level.tileHeight || tileCol < 0 || tileCol >= level.tileWidth) {
      return true;
    }
    const type = level.grid[tileRow][tileCol];
    if (type === TILE_TYPES.WALL) {
      return true;
    }
    if (type === TILE_TYPES.DOOR) {
      const door = level.runtime?.doorByTile?.get(getTileKey(tileRow, tileCol));
      return !isDoorOpen(door, keysMask, timers);
    }
    return false;
  }

  function getGuardVisionAtPhase(level, phase, keysMask, timers) {
    const visible = new Set();
    if (!level.guards.length) {
      return visible;
    }
    for (let guardIndex = 0; guardIndex < level.guards.length; guardIndex += 1) {
      const guard = level.guards[guardIndex];
      const step = phase % guard.path.length;
      const template = guard.templates?.[step];
      if (!template) {
        continue;
      }
      for (let i = 0; i < template.length; i += 1) {
        const ray = template[i];
        let blocked = false;
        for (let j = 0; j < ray.tiles.length; j += 1) {
          const tile = ray.tiles[j];
          if (isTileBlockingForVision(level, tile.row, tile.col, keysMask, timers)) {
            blocked = true;
            break;
          }
        }
        if (!blocked) {
          visible.add(getCellKey(ray.cell.row, ray.cell.col));
        }
      }
    }
    return visible;
  }

  function encodeState(row, col, keysMask, guardPhase, timers, bonusesMask) {
    return `${row},${col}|${keysMask}|${bonusesMask}|${guardPhase}|${timers.join(',')}`;
  }

  function validateLevelLayout(level) {
    if (!level.runtime) {
      prepareRuntime(level);
    }
    const validationConfig = state.config.validation || DEFAULT_CONFIG.validation;
    const guardCycle = level.runtime?.guardCycle || 1;
    const timerCount = level.runtime?.timerCount || 0;
    const initialTimers = Array.from({ length: timerCount }, () => 0);
    const totalBonuses = level.objects?.bonuses?.length || 0;
    const allBonusesMask = totalBonuses > 0 ? Math.pow(2, totalBonuses) - 1 : 0;
    const startState = {
      row: level.start.cellRow,
      col: level.start.cellCol,
      keysMask: 0,
      guardPhase: 0,
      timers: initialTimers,
      bonusesMask: 0,
      turns: 0,
      parent: -1,
      action: 'start'
    };
    const states = [startState];
    const queue = [0];
    const visited = new Set([
      encodeState(
        startState.row,
        startState.col,
        startState.keysMask,
        startState.guardPhase,
        startState.timers,
        startState.bonusesMask
      )
    ]);
    let successIndex = -1;
    let front = 0;

    while (front < queue.length) {
      const stateIndex = queue[front];
      front += 1;
      const current = states[stateIndex];
      if (current.turns > validationConfig.maxTurns) {
        continue;
      }
      if (
        current.row === level.exit.cellRow
        && current.col === level.exit.cellCol
        && current.bonusesMask === allBonusesMask
      ) {
        successIndex = stateIndex;
        break;
      }
      if (visited.size > validationConfig.maxStates) {
        break;
      }
      const guardPhase = current.guardPhase % guardCycle;
      const guardStatesBefore = level.runtime.guardStates[guardPhase] || [];
      for (let actionIndex = 0; actionIndex < PLAYER_ACTIONS.length; actionIndex += 1) {
        const action = PLAYER_ACTIONS[actionIndex];
        const targetRow = current.row + action.row;
        const targetCol = current.col + action.col;
        if (!isInsideCell(targetRow, targetCol, level.cellWidth, level.cellHeight)) {
          continue;
        }
        if (action.id !== 'wait') {
          const neighborKey = getCellKey(targetRow, targetCol);
          if (!level.adjacency[current.row][current.col].has(neighborKey)) {
            continue;
          }
        }
        const targetKey = getCellKey(targetRow, targetCol);
        const currentKey = getCellKey(current.row, current.col);
        const timersBefore = current.timers.slice();
        let keysMask = current.keysMask;
        let bonusesMask = current.bonusesMask;
        const doorAtTarget = level.runtime.doorByCell.get(targetKey);
        const doorAtCurrent = level.runtime.doorByCell.get(currentKey);
        if (doorAtTarget && !isDoorOpen(doorAtTarget, keysMask, timersBefore)) {
          continue;
        }
        if (action.id === 'wait' && doorAtCurrent && !isDoorOpen(doorAtCurrent, keysMask, timersBefore)) {
          continue;
        }
        const plate = level.runtime.plateByCell.get(targetKey);
        if (plate && Number.isInteger(plate.timerIndex)) {
          timersBefore[plate.timerIndex] = Number.POSITIVE_INFINITY;
        }
        const keyObject = level.runtime.keyByCell.get(targetKey);
        if (keyObject) {
          const bit = 1 << keyObject.index;
          if ((keysMask & bit) === 0) {
            keysMask |= bit;
          }
        }
        const bonusObject = level.runtime.bonusByCell.get(targetKey);
        if (bonusObject) {
          const bit = Math.pow(2, bonusObject.index);
          bonusesMask |= bit;
        }
        const nextPhase = guardCycle > 0 ? (guardPhase + 1) % guardCycle : 0;
        const guardStatesAfter = level.runtime.guardStates[nextPhase] || [];
        let collision = false;
        for (let g = 0; g < guardStatesAfter.length; g += 1) {
          const after = guardStatesAfter[g];
          const before = guardStatesBefore[g];
          if (after && after.row === targetRow && after.col === targetCol) {
            collision = true;
            break;
          }
          if (before && after && before.row === targetRow && before.col === targetCol && after.row === current.row && after.col === current.col) {
            collision = true;
            break;
          }
        }
        if (collision) {
          continue;
        }
        const vision = getGuardVisionAtPhase(level, nextPhase, keysMask, timersBefore);
        if (vision.has(targetKey)) {
          continue;
        }
        const timersAfter = timersBefore.map(value => (value > 0 ? value - 1 : 0));
        const doorAfter = level.runtime.doorByCell.get(targetKey);
        if (doorAfter && !isDoorOpen(doorAfter, keysMask, timersAfter)) {
          continue;
        }
        const newState = {
          row: targetRow,
          col: targetCol,
          keysMask,
          guardPhase: nextPhase,
          timers: timersAfter,
          bonusesMask,
          turns: current.turns + 1,
          parent: stateIndex,
          action: action.id
        };
        const stateKey = encodeState(
          newState.row,
          newState.col,
          newState.keysMask,
          newState.guardPhase,
          newState.timers,
          newState.bonusesMask
        );
        if (!visited.has(stateKey)) {
          visited.add(stateKey);
          states.push(newState);
          queue.push(states.length - 1);
        }
      }
    }

    if (successIndex === -1) {
      return {
        success: false,
        optimalTurns: null,
        explored: visited.size,
        alerts: 0,
        path: []
      };
    }

    const path = [];
    let cursor = successIndex;
    while (cursor >= 0) {
      const state = states[cursor];
      path.push({ row: state.row, col: state.col, action: state.action });
      cursor = state.parent;
    }
    path.reverse();

    return {
      success: true,
      optimalTurns: states[successIndex].turns,
      explored: visited.size,
      alerts: 0,
      path
    };
  }

  function buildLevelMetrics(level, validation) {
    const accessibleCells = level.cellWidth * level.cellHeight;
    const union = new Set();
    level.guards.forEach(guard => {
      guard.visionUnion.forEach(cellKey => union.add(cellKey));
    });
    const coverage = accessibleCells > 0 ? union.size / accessibleCells : 0;
    const guardRanges = level.guards.map(guard => guard.range);
    const guardAngles = level.guards.map(guard => guard.angle);
    const minRange = guardRanges.length ? Math.min(...guardRanges) : 0;
    const maxRange = guardRanges.length ? Math.max(...guardRanges) : 0;
    const maxAngle = guardAngles.length ? Math.max(...guardAngles) : 0;
    const bonuses = level.objects.bonuses.length;
    const par = level.optimalPath.length;
    const optimalTurns = validation.success ? validation.optimalTurns : null;
    const score = optimalTurns !== null ? (par - optimalTurns) + bonuses - (validation.alerts || 0) : null;
    return {
      guardCount: level.guards.length,
      minRange,
      maxRange,
      maxAngle,
      visionCoverage: coverage,
      par,
      optimalTurns,
      bonuses,
      score
    };
  }

  function refreshDoorAndPlateTiles(playState = state.play) {
    if (!state.level || !state.tileMap) {
      return;
    }
    const tileMap = state.tileMap;
    const keysMask = playState && Number.isInteger(playState.keysMask) ? playState.keysMask : 0;
    const timers = Array.isArray(playState?.timers) ? playState.timers : [];
    const doorById = new Map(state.level.objects.doors.map(door => [door.id, door]));

    state.level.objects.doors.forEach(door => {
      const coords = getTileCoords(door.cellRow, door.cellCol);
      const tileKey = getTileKey(coords.tileRow, coords.tileCol);
      const tile = tileMap.get(tileKey);
      if (!tile) {
        return;
      }
      const open = isDoorOpen(door, keysMask, timers);
      if (open) {
        tile.classList.remove('escape__cell--door');
        tile.classList.add('escape__cell--floor', 'escape__cell--door-open');
      } else {
        tile.classList.remove('escape__cell--door-open');
        tile.classList.remove('escape__cell--floor');
        tile.classList.add('escape__cell--door');
      }
      if (Number.isInteger(door.pairIndex)) {
        tile.classList.add(`escape__cell--door-pair-${door.pairIndex}`);
      }
    });

    state.level.objects.plates.forEach(plate => {
      const coords = getTileCoords(plate.cellRow, plate.cellCol);
      const tileKey = getTileKey(coords.tileRow, coords.tileCol);
      const tile = tileMap.get(tileKey);
      if (!tile) {
        return;
      }
      const door = plate.doorId ? doorById.get(plate.doorId) : null;
      const open = door ? isDoorOpen(door, keysMask, timers) : false;
      tile.classList.add('escape__cell--floor');
      if (Number.isInteger(plate.pairIndex)) {
        tile.classList.add(`escape__cell--plate-pair-${plate.pairIndex}`);
      }
      if (open) {
        tile.classList.remove('escape__cell--plate');
        tile.classList.add('escape__cell--plate-open');
      } else {
        tile.classList.remove('escape__cell--plate-open');
        tile.classList.add('escape__cell--plate');
      }
    });
  }

  function renderBoard(level) {
    if (!state.elements || !state.elements.board) {
      return;
    }
    const board = state.elements.board;
    clearRenderedEntities();
    board.innerHTML = '';
    board.style.setProperty('--escape-columns', String(level.grid[0]?.length || 0));
    const guardPathTiles = level.runtime?.guardPathTiles || new Set();
    const guardStartTiles = level.runtime?.guardStartTiles || new Set();
    const guardVisionTiles = level.runtime?.guardVisionTiles || new Set();
    const tileMap = new Map();
    const entityMap = new Map();
    const visionIndicatorMap = new Map();
    const fragment = document.createDocumentFragment();
    for (let row = 0; row < level.grid.length; row += 1) {
      const line = level.grid[row];
      for (let col = 0; col < line.length; col += 1) {
        const cell = document.createElement('div');
        cell.className = 'escape__cell';
        const type = line[col];
        if (type === TILE_TYPES.PLATE) {
          cell.classList.add('escape__cell--floor', 'escape__cell--plate');
        } else if (type === TILE_TYPES.START) {
          cell.classList.add('escape__cell--floor');
        } else if (type && type !== TILE_TYPES.WALL) {
          cell.classList.add(`escape__cell--${type}`);
        }
        const tileKey = getTileKey(row, col);
        if (type === TILE_TYPES.DOOR) {
          const door = level.runtime?.doorByTile?.get(tileKey);
          if (door && Number.isInteger(door.pairIndex)) {
            cell.classList.add(`escape__cell--door-pair-${door.pairIndex}`);
          }
        }
        if (type === TILE_TYPES.PLATE) {
          const plate = level.runtime?.plateByTile?.get(tileKey);
          if (plate && Number.isInteger(plate.pairIndex)) {
            cell.classList.add(`escape__cell--plate-pair-${plate.pairIndex}`);
          }
        }
        if (guardPathTiles.has(tileKey)) {
          cell.classList.add('escape__cell--patrol');
        }
        if (guardStartTiles.has(tileKey)) {
          cell.classList.add('escape__cell--guard');
        }
        const visionIndicator = document.createElement('span');
        visionIndicator.className = 'escape__vision-indicator';
        visionIndicator.setAttribute('aria-hidden', 'true');
        cell.appendChild(visionIndicator);
        const entity = document.createElement('span');
        entity.className = 'escape__cell-entity';
        entity.setAttribute('aria-hidden', 'true');
        cell.appendChild(entity);
        tileMap.set(tileKey, cell);
        entityMap.set(tileKey, entity);
        visionIndicatorMap.set(tileKey, visionIndicator);
        fragment.appendChild(cell);
      }
    }
    board.appendChild(fragment);
    state.tileMap = tileMap;
    state.entityMap = entityMap;
    state.visionMap = visionIndicatorMap;
    refreshDoorAndPlateTiles({ keysMask: 0, timers: [] });
  }

  function clearRenderedEntities() {
    if (!state.renderState) {
      state.renderState = {
        playerEntity: null,
        guardEntities: [],
        visionTiles: [],
        visionIndicators: []
      };
      return;
    }
    if (state.renderState.playerEntity) {
      state.renderState.playerEntity.classList.remove('escape__cell-entity--player');
    }
    if (Array.isArray(state.renderState.guardEntities)) {
      state.renderState.guardEntities.forEach(entity => {
        if (entity) {
          entity.classList.remove('escape__cell-entity--guard');
        }
      });
    }
    if (Array.isArray(state.renderState.visionTiles)) {
      state.renderState.visionTiles.forEach(tile => {
        if (tile) {
          tile.classList.remove('escape__cell--vision-active');
        }
      });
    }
    if (Array.isArray(state.renderState.visionIndicators)) {
      state.renderState.visionIndicators.forEach(indicator => {
        if (indicator) {
          indicator.classList.remove('escape__vision-indicator--active');
        }
      });
    }
    state.renderState.playerEntity = null;
    state.renderState.guardEntities = [];
    state.renderState.visionTiles = [];
    state.renderState.visionIndicators = [];
  }

  function updateBoardEntities() {
    clearRenderedEntities();
    if (!state.level || !state.play || !state.tileMap || !state.entityMap) {
      return;
    }
    const tileMap = state.tileMap;
    const entityMap = state.entityMap;
    const visionIndicatorMap = state.visionMap;
    const playState = state.play;
    const playerCoords = getTileCoords(playState.row, playState.col);
    const playerKey = getTileKey(playerCoords.tileRow, playerCoords.tileCol);
    const playerEntity = entityMap.get(playerKey);
    if (playerEntity) {
      playerEntity.classList.add('escape__cell-entity--player');
      state.renderState.playerEntity = playerEntity;
    }
    const guardStates = state.level.runtime?.guardStates?.[playState.guardPhase] || [];
    const guardEntities = [];
    for (let i = 0; i < guardStates.length; i += 1) {
      const guard = guardStates[i];
      if (!guard) {
        continue;
      }
      const guardCoords = getTileCoords(guard.row, guard.col);
      const guardKey = getTileKey(guardCoords.tileRow, guardCoords.tileCol);
      const guardEntity = entityMap.get(guardKey);
      if (guardEntity) {
        guardEntity.classList.add('escape__cell-entity--guard');
        guardEntities.push(guardEntity);
      }
    }
    state.renderState.guardEntities = guardEntities;
    const visionTiles = [];
    const visionIndicators = [];
    const visionSet = getGuardVisionAtPhase(
      state.level,
      playState.guardPhase,
      playState.keysMask || 0,
      playState.timers || []
    );
    visionSet.forEach(cellKey => {
      const { row, col } = parseCellKey(cellKey);
      const coords = getTileCoords(row, col);
      const tileKey = getTileKey(coords.tileRow, coords.tileCol);
      const tile = tileMap.get(tileKey);
      if (tile) {
        tile.classList.add('escape__cell--vision-active');
        visionTiles.push(tile);
      }
      const indicator = visionIndicatorMap ? visionIndicatorMap.get(tileKey) : null;
      if (indicator) {
        indicator.classList.add('escape__vision-indicator--active');
        visionIndicators.push(indicator);
      }
    });
    state.renderState.visionTiles = visionTiles;
    state.renderState.visionIndicators = visionIndicators;
    refreshDoorAndPlateTiles(playState);
  }

  function countBits(value) {
    let count = 0;
    let working = value >>> 0;
    while (working) {
      count += working & 1;
      working >>>= 1;
    }
    return count;
  }

  function updateGameplayStatus() {
    if (!state.level || !state.play) {
      return;
    }
    const totalKeys = state.level.objects?.keys?.length || 0;
    const collectedKeys = countBits(state.play.keysMask || 0);
    const totalBonuses = state.level.objects?.bonuses?.length || 0;
    const collectedBonuses = state.play.bonusesCollected ? state.play.bonusesCollected.size : 0;
    const difficultyLabel = translate(
      `index.sections.escape.difficulty.${state.level.difficulty}`,
      state.level.difficulty
    );
    const fallback = `Seed ${state.level.seed} — ${difficultyLabel} — Tour ${formatInteger(state.play.turn)} — Clés ${formatInteger(collectedKeys)}/${formatInteger(totalKeys)} · Bonus ${formatInteger(collectedBonuses)}/${formatInteger(totalBonuses)}`;
    setMessage(
      'scripts.arcade.escape.messages.turnStatus',
      fallback,
      {
        seed: state.level.seed,
        difficulty: difficultyLabel,
        turn: formatInteger(state.play.turn),
        keys: formatInteger(collectedKeys),
        keysTotal: formatInteger(totalKeys),
        bonuses: formatInteger(collectedBonuses),
        bonusesTotal: formatInteger(totalBonuses)
      }
    );
  }

  function initializePlayState(level) {
    if (!level) {
      state.play = null;
      clearRenderedEntities();
      return;
    }
    const timerCount = level.runtime?.timerCount || 0;
    state.play = {
      row: level.start?.cellRow ?? 0,
      col: level.start?.cellCol ?? 0,
      guardPhase: 0,
      keysMask: 0,
      timers: Array.from({ length: timerCount }, () => 0),
      turn: 0,
      bonusesCollected: new Set(),
      collectedKeys: new Set(),
      completed: false,
      caught: false
    };
    updateBoardEntities();
    updateGameplayStatus();
  }

  const ACTION_BY_ID = PLAYER_ACTIONS.reduce((accumulator, action) => {
    accumulator[action.id] = action;
    return accumulator;
  }, Object.create(null));

  const KEY_TO_ACTION = Object.freeze({
    ArrowUp: 'north',
    ArrowDown: 'south',
    ArrowLeft: 'west',
    ArrowRight: 'east',
    w: 'north',
    z: 'north',
    s: 'south',
    a: 'west',
    q: 'west',
    d: 'east',
    ' ': 'wait',
    Spacebar: 'wait'
  });

  function canAcceptInput() {
    return state.ready && state.level && state.play && !state.play.completed && !state.play.caught;
  }

  function resolveAction(action) {
    if (!state.level || !state.play || !action) {
      return { success: false, reason: 'invalid' };
    }
    const level = state.level;
    const playState = state.play;
    const currentRow = playState.row;
    const currentCol = playState.col;
    const targetRow = currentRow + action.row;
    const targetCol = currentCol + action.col;
    if (action.id !== 'wait') {
      if (!isInsideCell(targetRow, targetCol, level.cellWidth, level.cellHeight)) {
        return { success: false, reason: 'boundary' };
      }
      const neighborKey = getCellKey(targetRow, targetCol);
      if (!level.adjacency[currentRow][currentCol].has(neighborKey)) {
        return { success: false, reason: 'wall' };
      }
    }

    const targetKey = getCellKey(targetRow, targetCol);
    const currentKey = getCellKey(currentRow, currentCol);
    const timersBefore = Array.isArray(playState.timers) ? playState.timers.slice() : [];
    let keysMask = playState.keysMask || 0;

    const doorAtTarget = level.runtime?.doorByCell.get(targetKey);
    const doorAtCurrent = level.runtime?.doorByCell.get(currentKey);
    if (action.id !== 'wait') {
      if (doorAtTarget && !isDoorOpen(doorAtTarget, keysMask, timersBefore)) {
        return { success: false, reason: 'doorClosed' };
      }
    } else if (doorAtCurrent && !isDoorOpen(doorAtCurrent, keysMask, timersBefore)) {
      return { success: false, reason: 'doorClosed' };
    }

    const plate = level.runtime?.plateByCell.get(targetKey);
    if (plate && Number.isInteger(plate.timerIndex)) {
      timersBefore[plate.timerIndex] = Number.POSITIVE_INFINITY;
    }

    let collectedKey = null;
    const keyObject = level.runtime?.keyByCell.get(targetKey);
    if (keyObject) {
      const bit = 1 << keyObject.index;
      if ((keysMask & bit) === 0) {
        keysMask |= bit;
        collectedKey = keyObject;
      }
    }

    let collectedBonus = null;
    const bonusObject = level.runtime?.bonusByCell.get(targetKey);
    if (bonusObject) {
      collectedBonus = bonusObject;
    }

    const guardCycle = level.runtime?.guardCycle || 1;
    const nextPhase = guardCycle > 0 ? (playState.guardPhase + 1) % guardCycle : 0;
    const guardStatesBefore = level.runtime?.guardStates?.[playState.guardPhase] || [];
    const guardStatesAfter = level.runtime?.guardStates?.[nextPhase] || [];

    for (let i = 0; i < guardStatesAfter.length; i += 1) {
      const after = guardStatesAfter[i];
      if (!after) {
        continue;
      }
      if (after.row === targetRow && after.col === targetCol) {
        return {
          success: false,
          reason: 'guard',
          guardPhase: nextPhase,
          timers: timersBefore
        };
      }
      const before = guardStatesBefore[i];
      if (
        before
        && before.row === targetRow
        && before.col === targetCol
        && after.row === currentRow
        && after.col === currentCol
      ) {
        return {
          success: false,
          reason: 'guard',
          guardPhase: nextPhase,
          timers: timersBefore
        };
      }
    }

    const visionSet = getGuardVisionAtPhase(level, nextPhase, keysMask, timersBefore);
    if (visionSet.has(targetKey)) {
      return {
        success: false,
        reason: 'vision',
        guardPhase: nextPhase,
        timers: timersBefore
      };
    }

    const timersAfter = timersBefore.map(value => (value > 0 ? value - 1 : 0));
    const doorAfter = level.runtime?.doorByCell.get(targetKey);
    if (doorAfter && !isDoorOpen(doorAfter, keysMask, timersAfter)) {
      return { success: false, reason: 'doorClosed' };
    }

    return {
      success: true,
      row: targetRow,
      col: targetCol,
      guardPhase: nextPhase,
      keysMask,
      timers: timersAfter,
      turn: playState.turn + 1,
      collectedKey,
      collectedBonus,
      reachedExit: targetRow === level.exit.cellRow && targetCol === level.exit.cellCol
    };
  }

  function attemptAction(actionId) {
    if (!canAcceptInput()) {
      return;
    }
    const action = ACTION_BY_ID[actionId];
    if (!action) {
      return;
    }
    const result = resolveAction(action);
    if (!result.success) {
      if (result.reason === 'guard' || result.reason === 'vision') {
        if (Number.isInteger(result.guardPhase)) {
          state.play.guardPhase = result.guardPhase;
        }
        if (Array.isArray(result.timers)) {
          state.play.timers = result.timers.slice();
        }
        state.play.turn += 1;
        state.play.caught = true;
        updateBoardEntities();
        setMessage(
          'scripts.arcade.escape.messages.caught',
          'Repéré ! Relancez pour retenter.',
          { turns: formatInteger(state.play.turn) },
          { warning: true }
        );
        return;
      }
      setMessage(
        'scripts.arcade.escape.messages.invalid',
        'Mouvement impossible.',
        null,
        { warning: true }
      );
      return;
    }

    state.play.row = result.row;
    state.play.col = result.col;
    state.play.guardPhase = result.guardPhase;
    state.play.keysMask = result.keysMask;
    state.play.timers = result.timers;
    state.play.turn = result.turn;

    if (result.collectedKey) {
      const key = result.collectedKey;
      state.play.collectedKeys.add(key.id);
      const coords = getTileCoords(key.cellRow, key.cellCol);
      const tileKey = getTileKey(coords.tileRow, coords.tileCol);
      const tile = state.tileMap?.get(tileKey);
      if (tile) {
        tile.classList.remove('escape__cell--key');
        tile.classList.add('escape__cell--floor', 'escape__cell--collected');
      }
      const cellKey = getCellKey(key.cellRow, key.cellCol);
      state.level.runtime?.keyByCell.delete(cellKey);
    }

    if (result.collectedBonus) {
      const bonus = result.collectedBonus;
      state.play.bonusesCollected.add(bonus.id);
      const coords = getTileCoords(bonus.cellRow, bonus.cellCol);
      const tileKey = getTileKey(coords.tileRow, coords.tileCol);
      const tile = state.tileMap?.get(tileKey);
      if (tile) {
        tile.classList.remove('escape__cell--bonus');
        tile.classList.add('escape__cell--floor', 'escape__cell--collected');
      }
      const cellKey = getCellKey(bonus.cellRow, bonus.cellCol);
      state.level.runtime?.bonusByCell.delete(cellKey);
    }

    updateBoardEntities();

    if (result.reachedExit) {
      const totalBonuses = state.level.objects?.bonuses?.length || 0;
      const collectedBonuses = state.play.bonusesCollected ? state.play.bonusesCollected.size : 0;
      const missing = totalBonuses - collectedBonuses;
      if (missing > 0) {
        const suffix = missing === 1 ? '' : 's';
        setMessage(
          'scripts.arcade.escape.messages.missingBonuses',
          'Il manque encore {remaining} orbe{suffix} bonus pour déverrouiller la sortie.',
          { remaining: formatInteger(missing), suffix },
          { warning: true }
        );
        return;
      }

      const rewardTickets = totalBonuses > 0 ? collectedBonuses : 0;
      if (rewardTickets > 0) {
        const awardGacha = typeof gainGachaTickets === 'function'
          ? gainGachaTickets
          : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
            ? window.gainGachaTickets
            : null;
        if (typeof awardGacha === 'function') {
          try {
            awardGacha(rewardTickets, { unlockTicketStar: true });
          } catch (error) {
            console.warn('Escape: unable to grant gacha tickets', error);
          }
        }

        const toast = typeof showToast === 'function'
          ? showToast
          : typeof window !== 'undefined' && typeof window.showToast === 'function'
            ? window.showToast
            : null;
        if (typeof toast === 'function') {
          const toastMessage = translate(
            'scripts.arcade.escape.toast.reward',
            '+{tickets} ticket{suffix} gacha obtenu{suffix} !',
            {
              tickets: formatInteger(rewardTickets),
              suffix: rewardTickets === 1 ? '' : 's'
            }
          );
          try {
            toast(toastMessage);
          } catch (error) {
            console.warn('Escape: unable to display gacha reward toast', error);
          }
        }
      }

      state.play.completed = true;
      if (rewardTickets > 0) {
        const suffix = rewardTickets === 1 ? '' : 's';
        setMessage(
          'scripts.arcade.escape.messages.victoryPerfect',
          'Évasion parfaite en {turns} tours ! +{tickets} ticket{suffix} gacha.',
          {
            turns: formatInteger(state.play.turn),
            tickets: formatInteger(rewardTickets),
            suffix
          }
        );
      } else {
        setMessage(
          'scripts.arcade.escape.messages.victory',
          'Évasion réussie en {turns} tours !',
          { turns: formatInteger(state.play.turn) }
        );
      }
      return;
    }

    updateGameplayStatus();
  }

  function applyLevel(level) {
    state.level = level;
    state.seed = level.seed;
    state.difficulty = level.difficulty;
    renderBoard(level);
    if (state.elements?.difficultySelect) {
      state.elements.difficultySelect.value = level.difficulty;
    }
    updateUrl(level.seed, level.difficulty);
  }

  function regenerateLevel(options = {}) {
    const normalizedDifficulty = normalizeDifficultyKey(options.difficulty || state.difficulty);
    const sanitizedSeed = sanitizeSeed(options.seed);
    const preserve = options.preserveSeed && !!sanitizedSeed;
    const finalSeed = preserve ? sanitizedSeed : sanitizedSeed || generateRandomSeed();
    try {
      const level = createLevel(finalSeed, normalizedDifficulty);
      applyLevel(level);
      initializePlayState(level);
    } catch (error) {
      console.error('Escape generation failed', error);
      setMessage(
        'scripts.arcade.escape.messages.error',
        'Impossible de générer un niveau. Essayez une autre seed.',
        null,
        { warning: true }
      );
    }
  }

  function attachInputListeners() {
    if (state.inputHandlers) {
      return;
    }
    const handlers = {};
    handlers.keydown = event => {
      if (!canAcceptInput()) {
        return;
      }
      const target = event.target;
      if (target && target instanceof HTMLElement) {
        const tag = target.tagName;
        if (target.isContentEditable || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
          return;
        }
      }
      const key = typeof event.key === 'string' && event.key.length === 1
        ? event.key.toLowerCase()
        : event.key;
      const actionId = KEY_TO_ACTION[key] || KEY_TO_ACTION[event.key];
      if (!actionId) {
        return;
      }
      event.preventDefault();
      attemptAction(actionId);
    };
    window.addEventListener('keydown', handlers.keydown);

    const board = state.elements?.board;
    if (board) {
      handlers.touchStart = event => {
        if (!canAcceptInput()) {
          return;
        }
        if (!event.touches || event.touches.length !== 1) {
          state.touchTracking = null;
          return;
        }
        const touch = event.touches[0];
        state.touchTracking = {
          startX: touch.clientX,
          startY: touch.clientY
        };
      };
      handlers.touchMove = event => {
        if (!state.touchTracking) {
          return;
        }
        if (!event.touches || event.touches.length !== 1) {
          state.touchTracking = null;
        }
      };
      handlers.touchEnd = event => {
        const tracking = state.touchTracking;
        state.touchTracking = null;
        if (!tracking || !canAcceptInput()) {
          return;
        }
        if (!event.changedTouches || event.changedTouches.length === 0) {
          return;
        }
        const touch = event.changedTouches[0];
        const dx = touch.clientX - tracking.startX;
        const dy = touch.clientY - tracking.startY;
        const distance = Math.hypot(dx, dy);
        const threshold = 24;
        let actionId = 'wait';
        if (distance >= threshold) {
          if (Math.abs(dx) > Math.abs(dy)) {
            actionId = dx > 0 ? 'east' : 'west';
          } else {
            actionId = dy > 0 ? 'south' : 'north';
          }
        }
        state.touchSkipClick = true;
        window.setTimeout(() => {
          state.touchSkipClick = false;
        }, 0);
        attemptAction(actionId);
      };
      handlers.touchCancel = () => {
        state.touchTracking = null;
        state.touchSkipClick = true;
        window.setTimeout(() => {
          state.touchSkipClick = false;
        }, 0);
      };
      handlers.click = () => {
        if (state.touchSkipClick) {
          state.touchSkipClick = false;
          return;
        }
        if (!canAcceptInput()) {
          return;
        }
        attemptAction('wait');
      };
      board.addEventListener('touchstart', handlers.touchStart, { passive: true });
      board.addEventListener('touchmove', handlers.touchMove, { passive: true });
      board.addEventListener('touchend', handlers.touchEnd, { passive: true });
      board.addEventListener('touchcancel', handlers.touchCancel, { passive: true });
      board.addEventListener('click', handlers.click);
    }

    state.inputHandlers = handlers;
  }

  function detachInputListeners() {
    if (!state.inputHandlers) {
      return;
    }
    const handlers = state.inputHandlers;
    if (handlers.keydown) {
      window.removeEventListener('keydown', handlers.keydown);
    }
    const board = state.elements?.board;
    if (board) {
      if (handlers.touchStart) {
        board.removeEventListener('touchstart', handlers.touchStart);
      }
      if (handlers.touchMove) {
        board.removeEventListener('touchmove', handlers.touchMove);
      }
      if (handlers.touchEnd) {
        board.removeEventListener('touchend', handlers.touchEnd);
      }
      if (handlers.touchCancel) {
        board.removeEventListener('touchcancel', handlers.touchCancel);
      }
      if (handlers.click) {
        board.removeEventListener('click', handlers.click);
      }
    }
    state.inputHandlers = null;
    state.touchTracking = null;
    state.touchSkipClick = false;
  }
  function attachEvents() {
    if (!state.elements) {
      return;
    }
    if (state.elements.difficultySelect) {
      state.elements.difficultySelect.addEventListener('change', event => {
        const value = event.target.value;
        regenerateLevel({ difficulty: value });
      });
    }
    if (state.elements.generateButton) {
      state.elements.generateButton.addEventListener('click', () => {
        const difficulty = state.elements?.difficultySelect?.value || state.difficulty;
        regenerateLevel({ difficulty });
      });
    }
    attachInputListeners();
  }

  async function loadConfig() {
    try {
      const response = await fetch(CONFIG_PATH, { cache: 'no-store' });
      if (response.ok) {
        const data = await response.json();
        state.config = normalizeConfig(data);
        setMessage('scripts.arcade.escape.messages.ready', 'Configuration chargée.');
        return;
      }
      console.warn('Escape config HTTP error', response.status);
    } catch (error) {
      console.warn('Escape config load failed', error);
    }
    state.config = normalizeConfig(DEFAULT_CONFIG);
    setMessage(
      'scripts.arcade.escape.messages.configError',
      'Configuration par défaut appliquée (chargement impossible).',
      null,
      { warning: true }
    );
  }

  function initialize() {
    state.elements = getElements();
    if (!state.elements) {
      return;
    }
    attachEvents();
    attachLanguageListener();
    const initialParams = parseQueryParameters();
    if (state.elements.difficultySelect) {
      state.elements.difficultySelect.value = initialParams.difficulty;
    }
    loadConfig()
      .finally(() => {
        regenerateLevel({
          seed: initialParams.seed,
          difficulty: initialParams.difficulty,
          preserveSeed: !!initialParams.seed
        });
        state.ready = true;
      });
  }

  function destroy() {
    removeLanguageListener();
    detachInputListeners();
    clearRenderedEntities();
    state.play = null;
    state.tileMap = null;
    state.entityMap = null;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }

    window.escapeArcade = {
      regenerate(seed, difficulty) {
        regenerateLevel({ seed, difficulty, preserveSeed: true });
      },
      destroy
    };
})();
