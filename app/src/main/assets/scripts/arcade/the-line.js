(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/the-line.json';
  const DEFAULT_CONFIG = Object.freeze({
    maxGenerationAttempts: 200,
    holeRetryLimit: 24,
    difficulties: Object.freeze({
      easy: Object.freeze({
        gridSizes: Object.freeze([[5, 5], [5, 6], [6, 6]]),
        holeRange: Object.freeze({ min: 1, max: 3 }),
        minTurns: 6,
        multiPairs: Object.freeze({ min: 2, max: 3 })
      }),
      medium: Object.freeze({
        gridSizes: Object.freeze([[7, 6], [7, 7], [8, 6], [8, 7]]),
        holeRange: Object.freeze({ min: 3, max: 8 }),
        minTurns: 12,
        multiPairs: Object.freeze({ min: 3, max: 4 })
      }),
      hard: Object.freeze({
        gridSizes: Object.freeze([[8, 8], [9, 7], [9, 8], [9, 9]]),
        holeRange: Object.freeze({ min: 5, max: 12 }),
        minTurns: 18,
        multiPairs: Object.freeze({ min: 4, max: 5 })
      })
    }),
    layout: Object.freeze({
      centerBias: Object.freeze({
        easy: 0.35,
        medium: 0.58,
        hard: 0.85
      }),
      clusterBias: Object.freeze({
        easy: 0.3,
        medium: 0.5,
        hard: 0.7
      }),
      distribution: Object.freeze({
        easy: Object.freeze({
          centerMinRatio: 0.4,
          rightMinRatio: 0.3,
          diagonalGroupChance: 0.45,
          serpentineChance: 0.42,
          serpentineDensity: Object.freeze({ min: 0.38, max: 0.58 })
        }),
        medium: Object.freeze({
          centerMinRatio: 0.32,
          rightMinRatio: 0.26,
          diagonalGroupChance: 0.3,
          serpentineChance: 0.18,
          serpentineDensity: Object.freeze({ min: 0.32, max: 0.48 })
        }),
        hard: Object.freeze({
          centerMinRatio: 0.3,
          rightMinRatio: 0.24,
          diagonalGroupChance: 0.25,
          serpentineChance: 0.12,
          serpentineDensity: Object.freeze({ min: 0.28, max: 0.45 })
        })
      }),
      templates: Object.freeze({
        easy: Object.freeze([]),
        medium: Object.freeze([
          Object.freeze({
            width: 7,
            height: 7,
            mask: Object.freeze([
              '..###..',
              '.#####.',
              '.##.##.',
              '.#...#.',
              '.##.##.',
              '.#####.',
              '..###..'
            ])
          })
        ]),
        hard: Object.freeze([
          Object.freeze({
            width: 8,
            height: 8,
            mask: Object.freeze([
              '..####..',
              '.######.',
              '.##..##.',
              '.#....#.',
              '.##..##.',
              '.######.',
              '..####..',
              '........'
            ])
          }),
          Object.freeze({
            width: 9,
            height: 9,
            mask: Object.freeze([
              '...###...',
              '..#####..',
              '.#######.',
              '.##...##.',
              '.##...##.',
              '.#######.',
              '..#####..',
              '...###...',
              '.........'
            ])
          })
        ])
      })
    })
  });

  const SINGLE_PATH_COLOR = Object.freeze({ id: 'single', value: '#7ad3ff' });
  const ENDPOINT_TYPES = Object.freeze({
    START: 'start',
    FINISH: 'finish'
  });
  const ENDPOINT_LABEL_CONFIG = Object.freeze({
    [ENDPOINT_TYPES.START]: Object.freeze({
      symbolKey: 'index.sections.theLine.endpointLabels.start.symbol',
      ariaKey: 'index.sections.theLine.endpointLabels.start.aria',
      fallbackSymbol: 'S',
      fallbackAria: 'Start tile'
    }),
    [ENDPOINT_TYPES.FINISH]: Object.freeze({
      symbolKey: 'index.sections.theLine.endpointLabels.finish.symbol',
      ariaKey: 'index.sections.theLine.endpointLabels.finish.aria',
      fallbackSymbol: 'F',
      fallbackAria: 'Finish tile'
    })
  });
  const COLOR_PALETTE = Object.freeze([
    Object.freeze({ id: 'amber', value: '#f7b731' }),
    Object.freeze({ id: 'azure', value: '#34d1ff' }),
    Object.freeze({ id: 'orchid', value: '#a162f7' }),
    Object.freeze({ id: 'coral', value: '#ff6b6b' }),
    Object.freeze({ id: 'emerald', value: '#7bd88f' }),
    Object.freeze({ id: 'rose', value: '#ff8ad6' }),
    Object.freeze({ id: 'citrus', value: '#ffc371' })
  ]);

  const COMPLETION_REWARD = Object.freeze({ chance: 0.5, gachaTickets: 1 });

  const state = {
    config: null,
    mode: 'single',
    difficulty: 'easy',
    levelCounters: {
      single: { easy: 1, medium: 1, hard: 1 },
      multi: { easy: 1, medium: 1, hard: 1 }
    },
    board: null,
    paths: new Map(),
    activePath: null,
    cellsRemaining: 0,
    messageTimeout: null,
    currentPuzzle: null,
    elements: null,
    lastMessage: null,
    languageListenerAttached: false,
    languageChangeHandler: null
  };

  onReady(() => {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    updateModeButtons();
    updateDifficultyButtons();
    updateLevelDisplay();
    setupButtons();
    setupBoardEvents();
    setupLanguageChangeListener();
    state.config = normalizeConfig(DEFAULT_CONFIG, null);
    prepareNewPuzzle();
    loadRemoteConfig();
  });

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return typeof fallback === 'string' ? fallback : '';
    }
    const translator = typeof window !== 'undefined'
      && window.i18n
      && typeof window.i18n.t === 'function'
        ? window.i18n.t
        : typeof window !== 'undefined' && typeof window.t === 'function'
          ? window.t
          : null;
    if (translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string' && result.trim()) {
          return result;
        }
      } catch (error) {
        console.warn('The Line translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return fallback;
    }
    return key;
  }

  function formatIntegerLocalized(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    const integer = Math.trunc(numeric);
    try {
      return integer.toLocaleString();
    } catch (error) {
      return String(integer);
    }
  }

  function clampNumber(value, min, max, fallback) {
    if (Number.isFinite(value)) {
      let numeric = value;
      if (typeof min === 'number') {
        numeric = Math.max(min, numeric);
      }
      if (typeof max === 'number') {
        numeric = Math.min(max, numeric);
      }
      return numeric;
    }
    return fallback;
  }

  function randomInt(min, max) {
    const low = Math.ceil(min);
    const high = Math.floor(max);
    return Math.floor(Math.random() * (high - low + 1)) + low;
  }

  function shuffle(array) {
    for (let i = array.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = array[i];
      array[i] = array[j];
      array[j] = temp;
    }
    return array;
  }

  function normalizeConfig(config, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : DEFAULT_CONFIG;
    const source = config && typeof config === 'object' ? config : {};
    const maxGenerationAttempts = clampNumber(
      source.maxGenerationAttempts,
      20,
      1200,
      base.maxGenerationAttempts
    );
    const holeRetryLimit = clampNumber(
      source.holeRetryLimit,
      4,
      200,
      base.holeRetryLimit
    );
    const difficulties = {};
    ['easy', 'medium', 'hard'].forEach(key => {
      difficulties[key] = normalizeDifficultyConfig(
        source.difficulties && source.difficulties[key],
        base.difficulties[key]
      );
    });
    const layout = normalizeLayoutConfig(source.layout, base.layout);
    return {
      maxGenerationAttempts,
      holeRetryLimit,
      difficulties,
      layout
    };
  }

  function normalizeDifficultyConfig(config, fallback) {
    const base = fallback && typeof fallback === 'object'
      ? fallback
      : DEFAULT_CONFIG.difficulties.easy;
    const source = config && typeof config === 'object' ? config : {};

    const gridSizesSource = Array.isArray(source.gridSizes) ? source.gridSizes : base.gridSizes;
    const gridSizes = [];
    gridSizesSource.forEach(entry => {
      if (!Array.isArray(entry) || entry.length !== 2) {
        return;
      }
      const width = Math.max(3, Math.floor(entry[0]));
      const height = Math.max(3, Math.floor(entry[1]));
      if (!Number.isFinite(width) || !Number.isFinite(height)) {
        return;
      }
      gridSizes.push([width, height]);
    });
    if (!gridSizes.length) {
      gridSizes.push([5, 5]);
    }

    const holeRangeSource = source.holeRange && typeof source.holeRange === 'object'
      ? source.holeRange
      : base.holeRange;
    const minHole = Number.isFinite(holeRangeSource?.min)
      ? Math.max(0, Math.floor(holeRangeSource.min))
      : Math.max(0, Math.floor(base.holeRange?.min || 0));
    const maxHole = Number.isFinite(holeRangeSource?.max)
      ? Math.max(minHole, Math.floor(holeRangeSource.max))
      : Math.max(minHole, Math.floor(base.holeRange?.max || minHole));

    const minTurns = Number.isFinite(source.minTurns)
      ? Math.max(0, Math.floor(source.minTurns))
      : Math.max(0, Math.floor(base.minTurns || 0));

    const multiPairsSource = source.multiPairs && typeof source.multiPairs === 'object'
      ? source.multiPairs
      : base.multiPairs;
    const multiMin = Number.isFinite(multiPairsSource?.min)
      ? Math.max(2, Math.floor(multiPairsSource.min))
      : Math.max(2, Math.floor(base.multiPairs?.min || 2));
    const multiMax = Number.isFinite(multiPairsSource?.max)
      ? Math.max(multiMin, Math.floor(multiPairsSource.max))
      : Math.max(multiMin, Math.floor(base.multiPairs?.max || multiMin));

    return {
      gridSizes,
      holeRange: { min: minHole, max: maxHole },
      minTurns,
      multiPairs: { min: multiMin, max: multiMax }
    };
  }

  function normalizeLayoutConfig(config, fallback) {
    const base = fallback && typeof fallback === 'object'
      ? fallback
      : DEFAULT_CONFIG.layout;
    const source = config && typeof config === 'object' ? config : {};

    const centerBias = {};
    const clusterBias = {};
    const distribution = {};
    ['easy', 'medium', 'hard'].forEach(key => {
      const baseCenter = clampNumber(base?.centerBias?.[key], 0, 4, 0.4);
      const baseCluster = clampNumber(base?.clusterBias?.[key], 0, 1, 0.4);
      centerBias[key] = clampNumber(source?.centerBias?.[key], 0, 4, baseCenter);
      clusterBias[key] = clampNumber(source?.clusterBias?.[key], 0, 1, baseCluster);
      distribution[key] = normalizeDistributionConfig(
        source?.distribution?.[key],
        base?.distribution?.[key]
      );
    });

    const templates = {};
    const templateSource = source.templates && typeof source.templates === 'object'
      ? source.templates
      : base?.templates || {};
    ['easy', 'medium', 'hard'].forEach(key => {
      const list = Array.isArray(templateSource?.[key]) ? templateSource[key] : [];
      const normalized = [];
      list.forEach(entry => {
        if (!entry || typeof entry !== 'object') {
          return;
        }
        const width = Number.isFinite(entry.width) ? Math.max(3, Math.floor(entry.width)) : null;
        const height = Number.isFinite(entry.height) ? Math.max(3, Math.floor(entry.height)) : null;
        if (!width || !height) {
          return;
        }
        const rawMask = Array.isArray(entry.mask)
          ? entry.mask
          : Array.isArray(entry.masks)
            ? entry.masks
            : null;
        const directIndices = Array.isArray(entry.indices)
          ? entry.indices.filter(index => Number.isFinite(index) && index >= 0)
          : null;
        const indices = [];
        if (directIndices && directIndices.length) {
          directIndices.forEach(index => {
            const normalizedIndex = Math.floor(index);
            if (normalizedIndex >= 0 && normalizedIndex < width * height) {
              indices.push(normalizedIndex);
            }
          });
        } else if (rawMask && rawMask.length) {
          for (let y = 0; y < Math.min(rawMask.length, height); y += 1) {
            const row = rawMask[y];
            if (typeof row !== 'string') {
              continue;
            }
            for (let x = 0; x < Math.min(row.length, width); x += 1) {
              const symbol = row[x];
              if (symbol === '#' || symbol === '1' || symbol === 'X' || symbol === 'x') {
                indices.push(y * width + x);
              }
            }
          }
        }
        if (!indices.length) {
          return;
        }
        const unique = Array.from(new Set(indices)).filter(index => index >= 0 && index < width * height);
        if (!unique.length) {
          return;
        }
        normalized.push({ width, height, indices: unique });
      });
      if (normalized.length) {
        templates[key] = normalized;
      }
    });

    return { centerBias, clusterBias, distribution, templates };
  }

  function normalizeDistributionConfig(config, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : {};
    const source = config && typeof config === 'object' ? config : {};
    const centerMinRatio = clampNumber(
      source.centerMinRatio,
      0,
      0.9,
      clampNumber(base.centerMinRatio, 0, 0.9, 0)
    );
    const rightMinRatio = clampNumber(
      source.rightMinRatio,
      0,
      0.9,
      clampNumber(base.rightMinRatio, 0, 0.9, 0)
    );
    const diagonalGroupChance = clampNumber(
      source.diagonalGroupChance,
      0,
      1,
      clampNumber(base.diagonalGroupChance, 0, 1, 0)
    );
    const serpentineChance = clampNumber(
      source.serpentineChance,
      0,
      1,
      clampNumber(base.serpentineChance, 0, 1, 0)
    );
    const baseSerp = base && typeof base.serpentineDensity === 'object'
      ? base.serpentineDensity
      : null;
    const serpSource = source && typeof source.serpentineDensity === 'object'
      ? source.serpentineDensity
      : baseSerp;
    const serpMin = clampNumber(
      serpSource?.min,
      0.15,
      0.85,
      clampNumber(baseSerp?.min, 0.15, 0.85, 0.4)
    );
    const serpMax = clampNumber(
      serpSource?.max,
      0.2,
      0.95,
      clampNumber(baseSerp?.max, 0.2, 0.95, Math.max(serpMin, 0.5))
    );
    const minDensity = Math.min(serpMin, serpMax);
    const maxDensity = Math.max(serpMin, serpMax);
    return {
      centerMinRatio,
      rightMinRatio,
      diagonalGroupChance,
      serpentineChance,
      serpentineDensity: {
        min: clampNumber(minDensity, 0.15, 0.95, 0.35),
        max: clampNumber(maxDensity, 0.2, 0.98, Math.max(minDensity, 0.55))
      }
    };
  }

  function loadRemoteConfig() {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          state.config = normalizeConfig(data, state.config || DEFAULT_CONFIG);
        }
      })
      .catch(error => {
        console.warn('The Line config load error', error);
      });
  }

  function getElements() {
    const section = document.getElementById('theLine');
    if (!section) {
      return null;
    }
    return {
      section,
      board: document.getElementById('theLineBoard'),
      message: document.getElementById('theLineMessage'),
      reset: document.getElementById('theLineResetButton'),
      level: document.getElementById('theLineLevelValue'),
      remaining: document.getElementById('theLineRemainingValue'),
      modeButtons: Array.from(section.querySelectorAll('[data-line-mode]')),
      difficultyButtons: Array.from(section.querySelectorAll('[data-line-difficulty]'))
    };
  }

  function setupButtons() {
    const elements = state.elements;
    if (!elements) {
      return;
    }
    elements.modeButtons.forEach(button => {
      button.addEventListener('click', () => {
        const mode = button.dataset.lineMode;
        setMode(mode);
      });
    });
    elements.difficultyButtons.forEach(button => {
      button.addEventListener('click', () => {
        const difficulty = button.dataset.lineDifficulty;
        setDifficulty(difficulty);
      });
    });
    if (elements.reset) {
      elements.reset.addEventListener('click', () => {
        clearMessageTimeout();
        prepareNewPuzzle();
      });
    }
  }

  function setupBoardEvents() {
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    const board = elements.board;
    board.addEventListener('pointerdown', handlePointerDown);
    board.addEventListener('pointermove', handlePointerMove);
    board.addEventListener('pointerup', handlePointerUp);
    board.addEventListener('pointercancel', handlePointerUp);
  }

  function setupLanguageChangeListener() {
    if (state.languageListenerAttached) {
      return;
    }
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
      return;
    }
    const handler = () => {
      refreshEndpointLabels();
      const message = state.lastMessage;
      if (!message) {
        return;
      }
      setMessage(message.key, message.fallback, message.params, true);
    };
    state.languageChangeHandler = handler;
    window.addEventListener('i18n:languagechange', handler);
    state.languageListenerAttached = true;
  }

  function setMode(mode) {
    if (mode !== 'single' && mode !== 'multi') {
      return;
    }
    if (state.mode === mode) {
      return;
    }
    state.mode = mode;
    updateModeButtons();
    updateLevelDisplay();
    clearMessageTimeout();
    prepareNewPuzzle();
  }

  function setDifficulty(difficulty) {
    if (!['easy', 'medium', 'hard'].includes(difficulty)) {
      return;
    }
    if (state.difficulty === difficulty) {
      return;
    }
    state.difficulty = difficulty;
    updateDifficultyButtons();
    updateLevelDisplay();
    clearMessageTimeout();
    prepareNewPuzzle();
  }

  function updateModeButtons() {
    const elements = state.elements;
    if (!elements) {
      return;
    }
    elements.modeButtons.forEach(button => {
      const mode = button.dataset.lineMode;
      const isActive = mode === state.mode;
      button.classList.toggle('the-line__toggle--active', isActive);
      button.setAttribute('aria-pressed', String(isActive));
    });
  }

  function updateDifficultyButtons() {
    const elements = state.elements;
    if (!elements) {
      return;
    }
    elements.difficultyButtons.forEach(button => {
      const difficulty = button.dataset.lineDifficulty;
      const isActive = difficulty === state.difficulty;
      button.classList.toggle('the-line__toggle--active', isActive);
      button.setAttribute('aria-pressed', String(isActive));
    });
  }

  function updateLevelDisplay() {
    const elements = state.elements;
    if (!elements || !elements.level) {
      return;
    }
    const counters = state.levelCounters[state.mode] || state.levelCounters.single;
    const level = counters[state.difficulty] || 1;
    elements.level.textContent = String(level);
  }

  function updateRemainingValue() {
    const elements = state.elements;
    if (!elements || !elements.remaining) {
      return;
    }
    elements.remaining.textContent = String(Math.max(0, state.cellsRemaining));
  }

  function clearMessageTimeout() {
    if (state.messageTimeout) {
      window.clearTimeout(state.messageTimeout);
      state.messageTimeout = null;
    }
  }

  function setMessage(key, fallback, params, isRefresh = false) {
    const elements = state.elements;
    if (!elements || !elements.message) {
      return;
    }
    const normalizedKey = typeof key === 'string' && key.trim() ? key.trim() : '';
    if (normalizedKey) {
      elements.message.setAttribute('data-i18n', normalizedKey);
    } else if (!isRefresh) {
      elements.message.removeAttribute('data-i18n');
    }
    const text = normalizedKey
      ? translate(normalizedKey, fallback, params)
      : typeof fallback === 'string'
        ? fallback
        : '';
    elements.message.textContent = text;
    if (!isRefresh) {
      const storedParams = params && typeof params === 'object'
        ? Object.assign({}, params)
        : null;
      state.lastMessage = {
        key: normalizedKey || null,
        fallback: typeof fallback === 'string' ? fallback : '',
        params: storedParams
      };
    }
  }

  function prepareNewPuzzle() {
    cancelActivePath();
    const config = state.config || normalizeConfig(DEFAULT_CONFIG, null);
    const puzzle = generatePuzzle(state.mode, state.difficulty, config);
    if (!puzzle) {
      setMessage(
        'index.sections.theLine.messages.error',
        'Impossible de générer une nouvelle grille. Réessayez.'
      );
      return;
    }
    state.currentPuzzle = puzzle;
    renderPuzzle(puzzle);
    const hintKey = state.mode === 'multi'
      ? 'index.sections.theLine.messages.multi'
      : 'index.sections.theLine.messages.single';
    const fallback = state.mode === 'multi'
      ? 'Reliez chaque paire de couleurs sans croiser les chemins.'
      : 'Tracez un parcours continu qui visite chaque case.';
    setMessage(hintKey, fallback, { width: puzzle.width, height: puzzle.height });
  }
  function generatePuzzle(mode, difficulty, config) {
    const difficultyConfig = config.difficulties[difficulty] || config.difficulties.easy;
    const layoutConfig = config.layout || DEFAULT_CONFIG.layout;
    const sizes = Array.isArray(difficultyConfig.gridSizes)
      ? difficultyConfig.gridSizes
      : [[5, 5]];
    const [width, height] = sizes[randomInt(0, sizes.length - 1)];
    const totalCells = width * height;
    const holeRange = difficultyConfig.holeRange || { min: 0, max: 0 };
    let holeMin = clampNumber(holeRange.min, 0, totalCells - 2, 0);
    let holeMax = clampNumber(holeRange.max, holeMin, totalCells - 2, holeMin);
    if (mode === 'multi') {
      holeMax = Math.min(holeMax, Math.max(holeMin, 3));
    }
    const maxAttempts = clampNumber(config.maxGenerationAttempts, 20, 2000, 200);
    const holeRetryLimit = Math.max(1, Math.floor(config.holeRetryLimit || 16));
    const generationLimiter = createTimeLimiter(
      computeGenerationLimitMs(width, height, mode, difficulty)
    );

    let attempt = 0;
    let holeAttempts = 0;
    let currentHoleMax = holeMax;
    let bestCandidate = null;
    const holeStrategy = selectHoleStrategy(
      mode,
      difficulty,
      layoutConfig,
      totalCells,
      holeMin,
      holeMax
    );
    let useFixedHoles = holeStrategy.type === 'fixed' && Number.isFinite(holeStrategy.count);
    let fixedHoleAttempts = 0;

    while (attempt < maxAttempts && !generationLimiter.timedOut()) {
      attempt += 1;
      holeAttempts += 1;
      let holeCount = holeMin === currentHoleMax
        ? holeMin
        : randomInt(holeMin, currentHoleMax);
      if (useFixedHoles) {
        holeCount = Math.max(holeMin, Math.min(totalCells - 2, Math.floor(holeStrategy.count)));
        fixedHoleAttempts += 1;
      }
      const blocked = generateBlockedSet(
        width,
        height,
        holeCount,
        layoutConfig,
        difficulty
      );
      const path = findHamiltonianPath(width, height, blocked, generationLimiter);
      if (generationLimiter.timedOut()) {
        break;
      }
      if (!path) {
        if (useFixedHoles && fixedHoleAttempts >= holeRetryLimit) {
          useFixedHoles = false;
          holeAttempts = 0;
        }
        if (holeAttempts >= holeRetryLimit && currentHoleMax > holeMin) {
          currentHoleMax = Math.max(holeMin, currentHoleMax - 1);
          holeAttempts = 0;
        }
        continue;
      }
      const turns = countTurns(path, width);
      if (mode === 'single' && turns < Math.max(2, difficultyConfig.minTurns || 0)) {
        if (!bestCandidate || turns > bestCandidate.turns) {
          bestCandidate = { path, blocked, turns };
        }
        continue;
      }
      return buildPuzzleFromPath(mode, width, height, blocked, path, difficultyConfig);
    }

    if (generationLimiter.timedOut() && bestCandidate) {
      return buildPuzzleFromPath(
        mode,
        width,
        height,
        bestCandidate.blocked,
        bestCandidate.path,
        difficultyConfig
      );
    }

    if (bestCandidate) {
      return buildPuzzleFromPath(
        mode,
        width,
        height,
        bestCandidate.blocked,
        bestCandidate.path,
        difficultyConfig
      );
    }

    const fallbackPuzzle = buildFallbackPuzzle(
      mode,
      width,
      height,
      difficultyConfig,
      layoutConfig,
      difficulty
    );
    if (fallbackPuzzle) {
      return fallbackPuzzle;
    }
    return null;
  }

  function selectHoleStrategy(mode, difficultyKey, layoutConfig, totalCells, holeMin, holeMax) {
    if (mode !== 'single') {
      return { type: 'range' };
    }
    const distribution = layoutConfig?.distribution?.[difficultyKey];
    if (!distribution) {
      return { type: 'range' };
    }
    const chance = clampNumber(distribution.serpentineChance, 0, 1, 0);
    if (!(Math.random() < chance)) {
      return { type: 'range' };
    }
    const density = distribution.serpentineDensity;
    if (!density || typeof density !== 'object') {
      return { type: 'range' };
    }
    const minRatio = clampNumber(density.min, 0.15, 0.9, 0.35);
    const maxRatio = clampNumber(density.max, minRatio, 0.95, Math.max(minRatio, 0.55));
    const ratio = minRatio === maxRatio
      ? minRatio
      : minRatio + Math.random() * (maxRatio - minRatio);
    const count = Math.max(holeMin, Math.min(totalCells - 2, Math.round(totalCells * ratio)));
    if (!Number.isFinite(count) || count <= holeMin) {
      return { type: 'range' };
    }
    return { type: 'fixed', count: Math.max(holeMin, Math.min(totalCells - 2, count)) };
  }

  function generateBlockedSet(width, height, count, layoutConfig, difficultyKey) {
    const total = width * height;
    const limit = Math.max(0, Math.min(count, total - 2));
    if (limit <= 0) {
      return new Set();
    }
    const blocked = new Set();
    const protectedIndices = new Set();
    const clusterProbability = clampNumber(
      layoutConfig?.clusterBias?.[difficultyKey],
      0,
      1,
      clampNumber(DEFAULT_CONFIG.layout.clusterBias?.[difficultyKey], 0, 1, 0.4)
    );
    const centerBiasValue = clampNumber(
      layoutConfig?.centerBias?.[difficultyKey],
      0,
      4,
      clampNumber(DEFAULT_CONFIG.layout.centerBias?.[difficultyKey], 0, 4, 0.4)
    );
    const weights = createCenterWeightedMap(width, height, centerBiasValue);
    const preferred = selectTemplateIndices(layoutConfig?.templates, difficultyKey, width, height);
    if (preferred && preferred.length) {
      const prioritized = shuffle(preferred.slice());
      for (let i = 0; i < prioritized.length && blocked.size < limit; i += 1) {
        blocked.add(prioritized[i]);
        protectedIndices.add(prioritized[i]);
      }
    }
    let safety = 0;
    while (blocked.size < limit && safety < total * 4) {
      safety += 1;
      let candidate = null;
      if (blocked.size > 0 && Math.random() < clusterProbability) {
        candidate = selectClusterNeighbor(blocked, width, height, weights);
      }
      if (candidate === null || blocked.has(candidate)) {
        candidate = selectWeightedIndex(weights, blocked);
      }
      if (candidate === null) {
        break;
      }
      blocked.add(candidate);
    }
    maybeInjectPattern(blocked, protectedIndices, width, height, limit, layoutConfig, difficultyKey);
    enforceDistributionTargets(blocked, protectedIndices, width, height, limit, layoutConfig, difficultyKey);
    return blocked;
  }

  function selectTemplateIndices(templates, difficultyKey, width, height) {
    if (!templates || !templates[difficultyKey]) {
      return null;
    }
    const candidates = templates[difficultyKey].filter(template => (
      template
      && template.width === width
      && template.height === height
      && Array.isArray(template.indices)
    ));
    if (!candidates.length) {
      return null;
    }
    const choice = candidates[randomInt(0, candidates.length - 1)];
    return choice.indices ? choice.indices.slice() : null;
  }

  function createCenterWeightedMap(width, height, biasValue) {
    const weights = new Array(width * height);
    const centerX = (width - 1) / 2;
    const centerY = (height - 1) / 2;
    const maxDistance = Math.sqrt(centerX * centerX + centerY * centerY) || 1;
    const exponent = 1 + Math.max(0, biasValue || 0) * 2.5;
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const dx = x - centerX;
        const dy = y - centerY;
        const distance = Math.sqrt(dx * dx + dy * dy);
        const normalized = Math.max(0, Math.min(1, distance / maxDistance));
        const inverted = 1 - normalized;
        const weight = Math.pow(Math.max(0, inverted), exponent) + 0.05;
        const jitter = 0.85 + Math.random() * 0.3;
        weights[y * width + x] = weight * jitter;
      }
    }
    return weights;
  }

  function selectWeightedIndex(weights, blocked) {
    if (!Array.isArray(weights) || !weights.length) {
      return null;
    }
    let totalWeight = 0;
    for (let i = 0; i < weights.length; i += 1) {
      if (!blocked.has(i)) {
        totalWeight += Math.max(0, weights[i] || 0);
      }
    }
    if (totalWeight <= 0) {
      return null;
    }
    let threshold = Math.random() * totalWeight;
    for (let i = 0; i < weights.length; i += 1) {
      if (blocked.has(i)) {
        continue;
      }
      threshold -= Math.max(0, weights[i] || 0);
      if (threshold <= 0) {
        return i;
      }
    }
    for (let i = weights.length - 1; i >= 0; i -= 1) {
      if (!blocked.has(i)) {
        return i;
      }
    }
    return null;
  }

  function selectClusterNeighbor(blocked, width, height, weights) {
    const blockedIndices = Array.from(blocked);
    if (!blockedIndices.length) {
      return null;
    }
    for (let attempt = 0; attempt < blockedIndices.length * 2; attempt += 1) {
      const anchor = blockedIndices[randomInt(0, blockedIndices.length - 1)];
      const neighbors = getNeighborIndices(anchor, width, height);
      const available = neighbors.filter(index => !blocked.has(index));
      if (!available.length) {
        continue;
      }
      if (!weights || !weights.length) {
        return available[randomInt(0, available.length - 1)];
      }
      let bestCandidate = null;
      let bestWeight = -1;
      available.forEach(index => {
        const weight = Math.max(0, weights[index] || 0);
        const noise = 0.9 + Math.random() * 0.2;
        const score = weight * noise;
        if (score > bestWeight) {
          bestWeight = score;
          bestCandidate = index;
        }
      });
      if (bestCandidate !== null && bestCandidate !== undefined) {
        return bestCandidate;
      }
    }
    return null;
  }

  function maybeInjectPattern(blocked, protectedIndices, width, height, limit, layoutConfig, difficultyKey) {
    if (!blocked || !blocked.size || limit < 2) {
      return;
    }
    const distribution = layoutConfig?.distribution?.[difficultyKey];
    if (!distribution) {
      return;
    }
    const chance = clampNumber(distribution.diagonalGroupChance, 0, 1, 0);
    if (Math.random() >= chance) {
      return;
    }
    const data = computeDistributionData(blocked, width, height, protectedIndices);
    const anchors = data.availableInterior.length ? data.availableInterior : data.availableCells;
    const anchor = pickRandomCell(anchors);
    if (!anchor) {
      return;
    }
    const directions = shuffle([
      { dx: 1, dy: 1 },
      { dx: 1, dy: -1 },
      { dx: -1, dy: 1 },
      { dx: -1, dy: -1 },
      { dx: 1, dy: 0 },
      { dx: 0, dy: 1 }
    ]);
    const donorCandidates = buildDonorList([
      data.blockedCorners,
      data.blockedLeftEdge,
      data.blockedRightEdge,
      data.blockedNeutralEdge,
      data.blockedLeftInterior,
      data.blockedRightInterior,
      data.blockedInterior
    ], protectedIndices);
    if (!donorCandidates.length) {
      return;
    }
    for (let i = 0; i < directions.length; i += 1) {
      const pattern = collectPatternCells(anchor, directions[i], width, height, blocked, limit);
      if (!pattern.length) {
        continue;
      }
      const additions = pattern.filter(index => !blocked.has(index));
      if (!additions.length || additions.length > donorCandidates.length) {
        continue;
      }
      const donorPool = donorCandidates.slice();
      const donors = [];
      let valid = true;
      for (let j = 0; j < additions.length; j += 1) {
        if (!donorPool.length) {
          valid = false;
          break;
        }
        const donor = donorPool.splice(randomInt(0, donorPool.length - 1), 1)[0];
        if (!donor) {
          valid = false;
          break;
        }
        donors.push(donor);
      }
      if (!valid || donors.length !== additions.length) {
        continue;
      }
      donors.forEach(donor => {
        blocked.delete(donor.index);
      });
      additions.forEach(index => {
        blocked.add(index);
      });
      return;
    }
  }

  function enforceDistributionTargets(blocked, protectedIndices, width, height, limit, layoutConfig, difficultyKey) {
    if (!blocked || !blocked.size || !limit) {
      return;
    }
    const distribution = layoutConfig?.distribution?.[difficultyKey];
    if (!distribution) {
      return;
    }
    const centerRatio = clampNumber(distribution.centerMinRatio, 0, 0.9, 0);
    const rightRatio = clampNumber(distribution.rightMinRatio, 0, 0.9, 0);
    const targetCenter = Math.min(limit, Math.round(limit * centerRatio));
    const targetRight = Math.min(limit, Math.round(limit * rightRatio));
    if (targetCenter <= 0 && targetRight <= 0) {
      return;
    }
    const maxIterations = Math.max(1, limit * 3);
    for (let iteration = 0; iteration < maxIterations; iteration += 1) {
      const data = computeDistributionData(blocked, width, height, protectedIndices);
      let adjusted = false;
      if (targetCenter > 0 && data.blockedCenter.length < targetCenter) {
        const recipient = pickRandomCell(data.availableCenter);
        const donors = buildDonorList([
          data.blockedCorners,
          data.blockedLeftEdge,
          data.blockedRightEdge,
          data.blockedNeutralEdge,
          data.blockedLeftInterior,
          data.blockedRightInterior,
          data.blockedInterior
        ], protectedIndices);
        const donor = pickPriorityDonor(donors);
        if (recipient && donor) {
          blocked.delete(donor.index);
          blocked.add(recipient.index);
          adjusted = true;
          continue;
        }
      }
      if (targetRight > 0 && data.blockedRight.length < targetRight) {
        const recipient = pickRandomCell(data.availableRight);
        const donors = buildDonorList([
          data.blockedLeftInterior,
          data.blockedLeftEdge,
          data.blockedLeftCorners,
          data.blockedNeutralEdge,
          data.blockedInterior,
          data.blockedCells
        ], protectedIndices);
        const donor = pickPriorityDonor(donors);
        if (recipient && donor) {
          blocked.delete(donor.index);
          blocked.add(recipient.index);
          adjusted = true;
          continue;
        }
      }
      if (!adjusted) {
        break;
      }
    }
  }

  function computeDistributionData(blocked, width, height, protectedIndices) {
    const total = width * height;
    const protectedSet = protectedIndices instanceof Set ? protectedIndices : null;
    const data = {
      blockedCells: [],
      blockedCenter: [],
      blockedRight: [],
      blockedLeftInterior: [],
      blockedRightInterior: [],
      blockedInterior: [],
      blockedCorners: [],
      blockedLeftCorners: [],
      blockedRightCorners: [],
      blockedLeftEdge: [],
      blockedRightEdge: [],
      blockedNeutralEdge: [],
      availableCells: [],
      availableCenter: [],
      availableRight: [],
      availableInterior: [],
      availableLeftInterior: [],
      availableRightInterior: []
    };
    for (let index = 0; index < total; index += 1) {
      const cell = describeCell(index, width, height);
      cell.protected = protectedSet ? protectedSet.has(index) : false;
      if (blocked.has(index)) {
        data.blockedCells.push(cell);
        if (cell.isCenter) {
          data.blockedCenter.push(cell);
        }
        if (!cell.isEdge) {
          data.blockedInterior.push(cell);
        }
        if (!cell.isEdge && cell.isLeft) {
          data.blockedLeftInterior.push(cell);
        }
        if (!cell.isEdge && cell.isRight) {
          data.blockedRightInterior.push(cell);
        }
        if (cell.isCorner) {
          data.blockedCorners.push(cell);
          if (cell.isLeft) {
            data.blockedLeftCorners.push(cell);
          }
          if (cell.isRight) {
            data.blockedRightCorners.push(cell);
          }
        } else if (cell.isEdge) {
          if (cell.isLeft) {
            data.blockedLeftEdge.push(cell);
          } else if (cell.isRight) {
            data.blockedRightEdge.push(cell);
          } else {
            data.blockedNeutralEdge.push(cell);
          }
        }
        if (cell.isRight) {
          data.blockedRight.push(cell);
        }
      } else {
        data.availableCells.push(cell);
        if (cell.isCenter) {
          data.availableCenter.push(cell);
        }
        if (!cell.isEdge) {
          data.availableInterior.push(cell);
        }
        if (!cell.isEdge && cell.isLeft) {
          data.availableLeftInterior.push(cell);
        }
        if (!cell.isEdge && cell.isRight) {
          data.availableRightInterior.push(cell);
        }
        if (cell.isRight) {
          data.availableRight.push(cell);
        }
      }
    }
    return data;
  }

  function describeCell(index, width, height) {
    const x = index % width;
    const y = Math.floor(index / width);
    const isLeftEdge = x === 0;
    const isRightEdge = x === width - 1;
    const isTopEdge = y === 0;
    const isBottomEdge = y === height - 1;
    const isCorner = (isLeftEdge || isRightEdge) && (isTopEdge || isBottomEdge);
    const isEdge = isCorner || isLeftEdge || isRightEdge || isTopEdge || isBottomEdge;
    const midLeft = Math.floor((width - 1) / 2);
    const midRight = Math.ceil(width / 2);
    const isLeft = x <= midLeft;
    const isRight = x >= midRight;
    return {
      index,
      x,
      y,
      isCorner,
      isEdge,
      isCenter: !isEdge,
      isLeft,
      isRight
    };
  }

  function collectPatternCells(anchor, direction, width, height, blocked, limit) {
    if (!anchor || !direction) {
      return [];
    }
    const maxLength = Math.max(2, Math.min(limit, Math.max(width, height)));
    const length = Math.max(2, Math.min(maxLength, randomInt(2, Math.min(5, maxLength))));
    const cells = [];
    let x = anchor.x;
    let y = anchor.y;
    for (let step = 0; step < length; step += 1) {
      if (x < 0 || x >= width || y < 0 || y >= height) {
        break;
      }
      const index = y * width + x;
      if (step > 0 && blocked.has(index)) {
        break;
      }
      cells.push(index);
      x += direction.dx;
      y += direction.dy;
    }
    return cells;
  }

  function buildDonorList(groups, protectedIndices) {
    const donors = [];
    const seen = new Set();
    if (!Array.isArray(groups)) {
      return donors;
    }
    groups.forEach(group => {
      if (!Array.isArray(group) || !group.length) {
        return;
      }
      group.forEach(cell => {
        if (!cell || cell.protected || seen.has(cell.index)) {
          return;
        }
        donors.push(cell);
        seen.add(cell.index);
      });
    });
    return donors;
  }

  function pickPriorityDonor(donors) {
    if (!Array.isArray(donors) || !donors.length) {
      return null;
    }
    const sampleSize = Math.min(donors.length, 3);
    return donors[randomInt(0, sampleSize - 1)];
  }

  function pickRandomCell(cells) {
    if (!Array.isArray(cells) || !cells.length) {
      return null;
    }
    return cells[randomInt(0, cells.length - 1)];
  }

  function getNeighborIndices(index, width, height) {
    const neighbors = [];
    const x = index % width;
    const y = Math.floor(index / width);
    if (x > 0) {
      neighbors.push(index - 1);
    }
    if (x < width - 1) {
      neighbors.push(index + 1);
    }
    if (y > 0) {
      neighbors.push(index - width);
    }
    if (y < height - 1) {
      neighbors.push(index + width);
    }
    return neighbors;
  }

  function findHamiltonianPath(width, height, blockedIndices, limiter) {
    const totalCells = width * height;
    const totalAccessible = totalCells - blockedIndices.size;
    if (totalAccessible <= 0) {
      return null;
    }

    const neighborCache = new Array(totalCells);
    const accessible = [];
    for (let index = 0; index < totalCells; index += 1) {
      if (blockedIndices.has(index)) {
        neighborCache[index] = [];
        continue;
      }
      const coord = indexToCoord(index, width);
      const neighbors = [];
      if (coord.x > 0) {
        const left = index - 1;
        if (!blockedIndices.has(left)) {
          neighbors.push(left);
        }
      }
      if (coord.x < width - 1) {
        const right = index + 1;
        if (!blockedIndices.has(right)) {
          neighbors.push(right);
        }
      }
      if (coord.y > 0) {
        const up = index - width;
        if (!blockedIndices.has(up)) {
          neighbors.push(up);
        }
      }
      if (coord.y < height - 1) {
        const down = index + width;
        if (!blockedIndices.has(down)) {
          neighbors.push(down);
        }
      }
      neighborCache[index] = neighbors;
      accessible.push(index);
    }

    if (!accessible.length) {
      return null;
    }

    const visited = new Uint8Array(totalCells);
    blockedIndices.forEach(index => {
      visited[index] = 1;
    });
    const path = new Array(totalAccessible);
    const startCandidates = shuffle(accessible.slice());
    const maxStartCandidates = Math.min(
      startCandidates.length,
      Math.max(6, Math.ceil(totalAccessible / 12))
    );
    const iterationLimit = Math.max(8000, totalAccessible * 60);
    let iterationCount = 0;

    for (let i = 0; i < maxStartCandidates; i += 1) {
      const start = startCandidates[i];
      path[0] = start;
      visited[start] = 1;
      if (limiter && limiter.timedOut()) {
        visited[start] = 0;
        path[0] = undefined;
        break;
      }
      if (search(1, start)) {
        return path.slice();
      }
      visited[start] = 0;
      path[0] = undefined;
    }
    return null;

    function search(step, current) {
      iterationCount += 1;
      if (iterationCount >= iterationLimit) {
        if (limiter && typeof limiter.forceTimeout === 'function') {
          limiter.forceTimeout();
        }
        return false;
      }
      if (limiter && limiter.timedOut()) {
        return false;
      }
      if (step >= totalAccessible) {
        return true;
      }
      const neighbors = neighborCache[current];
      if (!neighbors || !neighbors.length) {
        return false;
      }
      const ordered = orderNeighbors(neighbors);
      for (let j = 0; j < ordered.length; j += 1) {
        const next = ordered[j];
        if (visited[next]) {
          continue;
        }
        visited[next] = 1;
        path[step] = next;
        if (search(step + 1, next)) {
          return true;
        }
        visited[next] = 0;
        path[step] = undefined;
        if (limiter && limiter.timedOut()) {
          return false;
        }
      }
      return false;
    }

    function orderNeighbors(list) {
      const candidates = [];
      for (let k = 0; k < list.length; k += 1) {
        const neighbor = list[k];
        if (visited[neighbor]) {
          continue;
        }
        const neighbors = neighborCache[neighbor];
        let degree = 0;
        for (let d = 0; d < neighbors.length; d += 1) {
          if (!visited[neighbors[d]]) {
            degree += 1;
          }
        }
        candidates.push({ index: neighbor, degree });
      }
      shuffle(candidates);
      candidates.sort((a, b) => a.degree - b.degree);
      return candidates.map(candidate => candidate.index);
    }
  }

  function countTurns(path, width) {
    if (!Array.isArray(path) || path.length < 3) {
      return 0;
    }
    let turns = 0;
    let previousDirection = null;
    for (let i = 1; i < path.length; i += 1) {
      const prevCoord = indexToCoord(path[i - 1], width);
      const currentCoord = indexToCoord(path[i], width);
      const dx = currentCoord.x - prevCoord.x;
      const dy = currentCoord.y - prevCoord.y;
      const direction = `${dx},${dy}`;
      if (previousDirection && direction !== previousDirection) {
        turns += 1;
      }
      previousDirection = direction;
    }
    return turns;
  }

  function buildPuzzleFromPath(mode, width, height, blockedIndices, pathIndices, difficultyConfig) {
    const pathCoords = pathIndices.map(index => indexToCoord(index, width));
    if (mode === 'multi') {
      const segments = createColorSegments(pathCoords, difficultyConfig);
      return {
        mode,
        width,
        height,
        blockedIndices,
        path: pathCoords,
        segments
      };
    }
    return {
      mode,
      width,
      height,
      blockedIndices,
      path: pathCoords,
      endpoints: {
        start: pathCoords[0],
        end: pathCoords[pathCoords.length - 1]
      }
    };
  }

  function createSimpleHamiltonianPath(width, height) {
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return null;
    }
    const path = [];
    for (let y = 0; y < height; y += 1) {
      if (y % 2 === 0) {
        for (let x = 0; x < width; x += 1) {
          path.push(y * width + x);
        }
      } else {
        for (let x = width - 1; x >= 0; x -= 1) {
          path.push(y * width + x);
        }
      }
    }
    return path;
  }

  function buildFallbackPuzzle(mode, width, height, difficultyConfig, layoutConfig, difficultyKey) {
    const basePath = createSimpleHamiltonianPath(width, height);
    if (!Array.isArray(basePath) || basePath.length < 2) {
      return null;
    }
    const holeRange = difficultyConfig.holeRange || { min: 0, max: 0 };
    const availableLength = basePath.length;
    const minHoles = clampNumber(holeRange.min, 0, availableLength - 2, 0);
    const maxHoles = clampNumber(holeRange.max, minHoles, availableLength - 2, minHoles);
    if (maxHoles <= 0) {
      return buildPuzzleFromPath(mode, width, height, new Set(), basePath, difficultyConfig);
    }
    const targetHoles = Math.max(minHoles, Math.min(maxHoles, Math.round((minHoles + maxHoles) / 2)));
    const biasedBlocked = generateBlockedSet(
      width,
      height,
      targetHoles,
      layoutConfig,
      difficultyKey
    );
    const biasedPath = findHamiltonianPath(width, height, biasedBlocked, null);
    if (biasedPath && biasedPath.length >= 2) {
      return buildPuzzleFromPath(mode, width, height, biasedBlocked, biasedPath, difficultyConfig);
    }
    const blocked = new Set();
    let startIndex = 0;
    let endIndex = basePath.length - 1;
    let holesCreated = 0;
    while (holesCreated < targetHoles && endIndex - startIndex + 1 > 2) {
      const canRemoveStart = startIndex < endIndex - 1;
      const canRemoveEnd = endIndex > startIndex + 1;
      let removeStart = canRemoveStart && !canRemoveEnd ? true : false;
      if (!removeStart && canRemoveEnd && canRemoveStart) {
        removeStart = Math.random() < 0.5;
      }
      if (removeStart && canRemoveStart) {
        blocked.add(basePath[startIndex]);
        startIndex += 1;
      } else if (canRemoveEnd) {
        blocked.add(basePath[endIndex]);
        endIndex -= 1;
      } else {
        break;
      }
      holesCreated += 1;
    }
    const trimmedPath = basePath.slice(startIndex, endIndex + 1);
    if (trimmedPath.length < 2) {
      return buildPuzzleFromPath(mode, width, height, new Set(), basePath, difficultyConfig);
    }
    return buildPuzzleFromPath(mode, width, height, blocked, trimmedPath, difficultyConfig);
  }

  function createColorSegments(pathCoords, difficultyConfig) {
    const total = pathCoords.length;
    const configPairs = difficultyConfig.multiPairs || { min: 2, max: 3 };
    const maxPossible = Math.max(2, Math.floor(total / 4));
    const minPairs = Math.min(Math.max(2, configPairs.min || 2), maxPossible);
    const maxPairs = Math.min(Math.max(minPairs, configPairs.max || minPairs), maxPossible);
    const pairCount = minPairs === maxPairs ? minPairs : randomInt(minPairs, maxPairs);
    const segments = splitPathSegments(pathCoords, pairCount);
    return segments.map((segment, index) => {
      const color = COLOR_PALETTE[index % COLOR_PALETTE.length];
      return {
        colorId: color.id,
        colorValue: color.value,
        cells: segment,
        start: segment[0],
        end: segment[segment.length - 1]
      };
    });
  }

  function splitPathSegments(pathCoords, segmentCount) {
    const segments = [];
    const total = pathCoords.length;
    let remaining = total;
    let cursor = 0;
    for (let i = 0; i < segmentCount; i += 1) {
      const segmentsLeft = segmentCount - i;
      const minRemaining = 2 * (segmentsLeft - 1);
      let minLength = 2;
      let maxLength = remaining - minRemaining;
      if (i === segmentCount - 1 || maxLength < minLength) {
        maxLength = remaining;
      } else {
        maxLength = Math.max(minLength, maxLength);
      }
      const length = i === segmentCount - 1
        ? remaining
        : randomInt(minLength, maxLength);
      const segment = pathCoords.slice(cursor, cursor + length);
      segments.push(segment);
      cursor += length;
      remaining -= length;
    }
    return segments;
  }

  function indexToCoord(index, width) {
    const x = index % width;
    const y = Math.floor(index / width);
    return { x, y };
  }

  function computeGenerationLimitMs(width, height, mode, difficulty) {
    const totalCells = width * height;
    let base = totalCells <= 36 ? 140 : totalCells <= 56 ? 220 : 380;
    if (mode === 'multi') {
      base += 60;
    }
    if (difficulty === 'hard') {
      base += 90;
    }
    return base;
  }

  function createTimeLimiter(limitMs) {
    const limit = Number.isFinite(limitMs) && limitMs > 0 ? limitMs : 0;
    const getNow = typeof performance !== 'undefined'
      && performance
      && typeof performance.now === 'function'
        ? () => performance.now()
        : () => Date.now();
    const start = getNow();
    let timeoutReached = false;
    return {
      timedOut() {
        if (timeoutReached) {
          return true;
        }
        if (!limit) {
          return false;
        }
        timeoutReached = getNow() - start >= limit;
        return timeoutReached;
      },
      forceTimeout() {
        timeoutReached = true;
      },
      get hasTimedOut() {
        return timeoutReached;
      }
    };
  }
  function renderPuzzle(puzzle) {
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    cancelActivePath();
    state.paths.clear();
    state.cellsRemaining = 0;

    const board = elements.board;
    board.innerHTML = '';
    board.style.setProperty('--the-line-columns', String(puzzle.width));
    board.setAttribute('data-line-columns', String(puzzle.width));
    board.dataset.lineDifficulty = state.difficulty;

    const fragment = document.createDocumentFragment();
    const cells = [];
    for (let y = 0; y < puzzle.height; y += 1) {
      const row = [];
      for (let x = 0; x < puzzle.width; x += 1) {
        const cellElement = document.createElement('div');
        cellElement.className = 'the-line__cell';
        cellElement.dataset.x = String(x);
        cellElement.dataset.y = String(y);
        const index = y * puzzle.width + x;
        const blocked = puzzle.blockedIndices.has(index);
        if (blocked) {
          cellElement.classList.add('the-line__cell--blocked');
          cellElement.setAttribute('data-blocked', 'true');
        } else {
          state.cellsRemaining += 1;
        }
        fragment.appendChild(cellElement);
        row.push({
          x,
          y,
          index,
          blocked,
          element: cellElement,
          endpointColor: null,
          endpointColorValue: null,
          endpointType: null,
          occupantColor: null
        });
      }
      cells.push(row);
    }
    board.appendChild(fragment);
    state.board = {
      width: puzzle.width,
      height: puzzle.height,
      cells,
      blocked: puzzle.blockedIndices
    };
    state.paths.clear();

    if (puzzle.mode === 'multi' && Array.isArray(puzzle.segments) && puzzle.segments.length) {
      puzzle.segments.forEach((segment, index) => {
        const startCell = getCellAt(segment.start.x, segment.start.y);
        const endCell = getCellAt(segment.end.x, segment.end.y);
        const color = COLOR_PALETTE[index % COLOR_PALETTE.length];
        markEndpoint(startCell, color, ENDPOINT_TYPES.START);
        markEndpoint(endCell, color, ENDPOINT_TYPES.FINISH);
        state.paths.set(color.id, {
          colorId: color.id,
          colorValue: color.value,
          endpoints: [startCell, endCell],
          sequence: [],
          complete: false
        });
      });
    } else {
      const startCoord = puzzle.path[0];
      const endCoord = puzzle.path[puzzle.path.length - 1];
      const startCell = getCellAt(startCoord.x, startCoord.y);
      const endCell = getCellAt(endCoord.x, endCoord.y);
      markEndpoint(startCell, SINGLE_PATH_COLOR, ENDPOINT_TYPES.START);
      markEndpoint(endCell, SINGLE_PATH_COLOR, ENDPOINT_TYPES.FINISH);
      state.paths.set(SINGLE_PATH_COLOR.id, {
        colorId: SINGLE_PATH_COLOR.id,
        colorValue: SINGLE_PATH_COLOR.value,
        endpoints: [startCell, endCell],
        sequence: [],
        complete: false
      });
    }

    updateRemainingValue();
  }

  function markEndpoint(cell, color, type) {
    if (!cell) {
      return;
    }
    cell.endpointColor = color.id;
    cell.endpointColorValue = color.value;
    cell.endpointType = type === ENDPOINT_TYPES.START || type === ENDPOINT_TYPES.FINISH
      ? type
      : null;
    cell.element.classList.add('the-line__cell--endpoint');
    cell.element.classList.toggle('the-line__cell--endpoint-start', cell.endpointType === ENDPOINT_TYPES.START);
    cell.element.classList.toggle('the-line__cell--endpoint-finish', cell.endpointType === ENDPOINT_TYPES.FINISH);
    if (cell.endpointType) {
      cell.element.dataset.lineEndpoint = cell.endpointType;
    } else {
      delete cell.element.dataset.lineEndpoint;
    }
    updateEndpointLabel(cell);
    cell.element.dataset.lineColor = color.id;
    cell.element.style.setProperty('--line-color-value', color.value);
  }

  function updateEndpointLabel(cell) {
    if (!cell || !cell.element) {
      return;
    }
    const type = cell.endpointType;
    const config = type ? ENDPOINT_LABEL_CONFIG[type] : null;
    if (!config) {
      delete cell.element.dataset.lineEndpointLabel;
      cell.element.removeAttribute('aria-label');
      return;
    }
    const symbol = translate(config.symbolKey, config.fallbackSymbol);
    const trimmedSymbol = typeof symbol === 'string' ? symbol.trim() : '';
    if (trimmedSymbol) {
      cell.element.dataset.lineEndpointLabel = trimmedSymbol;
    } else {
      delete cell.element.dataset.lineEndpointLabel;
    }
    const ariaText = translate(config.ariaKey, config.fallbackAria);
    const trimmedAria = typeof ariaText === 'string' ? ariaText.trim() : '';
    if (trimmedAria) {
      const finalLabel = trimmedSymbol ? `${trimmedAria} (${trimmedSymbol})` : trimmedAria;
      cell.element.setAttribute('aria-label', finalLabel);
    } else if (trimmedSymbol) {
      cell.element.setAttribute('aria-label', trimmedSymbol);
    } else {
      cell.element.removeAttribute('aria-label');
    }
  }

  function refreshEndpointLabels() {
    if (!state.paths || typeof state.paths.forEach !== 'function') {
      return;
    }
    state.paths.forEach(pathState => {
      if (!pathState || !Array.isArray(pathState.endpoints)) {
        return;
      }
      pathState.endpoints.forEach(cell => {
        if (!cell) {
          return;
        }
        updateEndpointLabel(cell);
      });
    });
  }

  function getCellAt(x, y) {
    if (!state.board) {
      return null;
    }
    if (y < 0 || y >= state.board.height || x < 0 || x >= state.board.width) {
      return null;
    }
    return state.board.cells[y][x];
  }

  function cancelActivePath() {
    const active = state.activePath;
    if (!active) {
      return;
    }
    const board = state.elements && state.elements.board;
    if (board && typeof board.releasePointerCapture === 'function') {
      try {
        board.releasePointerCapture(active.pointerId);
      } catch (error) {
        /* ignore */
      }
    }
    if (active.lastCell) {
      active.lastCell.element.classList.remove('the-line__cell--active');
    }
    state.activePath = null;
  }

  function occupyCellIfNeeded(cell, pathState) {
    if (!cell || cell.blocked) {
      return;
    }
    if (cell.occupantColor === pathState.colorId) {
      return;
    }
    if (cell.occupantColor) {
      clearCellOccupant(cell);
    }
    cell.occupantColor = pathState.colorId;
    cell.element.classList.add('the-line__cell--filled');
    cell.element.dataset.lineColor = pathState.colorId;
    cell.element.style.setProperty('--line-color-value', pathState.colorValue);
    state.cellsRemaining = Math.max(0, state.cellsRemaining - 1);
    updateRemainingValue();
  }

  function clearCellOccupant(cell) {
    if (!cell || !cell.occupantColor) {
      return;
    }
    cell.occupantColor = null;
    cell.element.classList.remove('the-line__cell--filled');
    state.cellsRemaining += 1;
    if (cell.endpointColor) {
      cell.element.dataset.lineColor = cell.endpointColor;
      cell.element.style.setProperty('--line-color-value', cell.endpointColorValue || '');
    } else {
      cell.element.removeAttribute('data-line-color');
      cell.element.style.removeProperty('--line-color-value');
    }
    updateRemainingValue();
  }

  function clearPath(pathState) {
    if (!pathState || !Array.isArray(pathState.sequence)) {
      return;
    }
    for (let i = 0; i < pathState.sequence.length; i += 1) {
      const cell = pathState.sequence[i];
      clearCellOccupant(cell);
    }
    pathState.sequence = [];
    pathState.complete = false;
  }

  function resolvePathEntry(cell) {
    if (!cell || !state.currentPuzzle) {
      return null;
    }
    if (state.mode === 'single') {
      const pathState = state.paths.get(SINGLE_PATH_COLOR.id);
      if (!pathState) {
        return null;
      }
      const startCell = pathState.endpoints[0];
      const lastCell = pathState.sequence[pathState.sequence.length - 1] || null;
      if (!pathState.sequence.length) {
        return cell === startCell ? { pathState, reset: true } : null;
      }
      if (pathState.complete) {
        return cell === startCell ? { pathState, reset: true } : null;
      }
      if (cell === lastCell) {
        return { pathState, reset: false };
      }
      if (cell === startCell) {
        return { pathState, reset: true };
      }
      return null;
    }
    const colorId = cell.endpointColor;
    if (!colorId) {
      return null;
    }
    const pathState = state.paths.get(colorId);
    if (!pathState) {
      return null;
    }
    return { pathState, reset: true };
  }

  function startActivePath(cell, entry, pointerId) {
    cancelActivePath();
    const pathState = entry.pathState;
    if (entry.reset) {
      clearPath(pathState);
    }
    if (!pathState.sequence.length) {
      pathState.sequence.push(cell);
      occupyCellIfNeeded(cell, pathState);
    }
    cell.element.classList.add('the-line__cell--active');
    state.activePath = {
      pointerId,
      pathState,
      lastCell: cell
    };
  }

  function areAdjacent(a, b) {
    if (!a || !b) {
      return false;
    }
    const dx = Math.abs(a.x - b.x);
    const dy = Math.abs(a.y - b.y);
    return (dx === 1 && dy === 0) || (dx === 0 && dy === 1);
  }

  function getCellElementFromEvent(event) {
    if (!event) {
      return null;
    }
    const directTarget = event.target && typeof event.target.closest === 'function'
      ? event.target.closest('.the-line__cell')
      : null;
    if (directTarget) {
      return directTarget;
    }
    if (typeof document === 'undefined' || typeof document.elementFromPoint !== 'function') {
      return null;
    }
    const fallbackTarget = document.elementFromPoint(event.clientX, event.clientY);
    if (fallbackTarget && typeof fallbackTarget.closest === 'function') {
      return fallbackTarget.closest('.the-line__cell');
    }
    return null;
  }

  function handlePointerDown(event) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    const elements = state.elements;
    if (!elements || !elements.board) {
      return;
    }
    const target = getCellElementFromEvent(event);
    if (!target || target.getAttribute('data-blocked') === 'true') {
      return;
    }
    const x = Number.parseInt(target.dataset.x, 10);
    const y = Number.parseInt(target.dataset.y, 10);
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      return;
    }
    const cell = getCellAt(x, y);
    const entry = resolvePathEntry(cell);
    if (!entry) {
      return;
    }
    event.preventDefault();
    clearMessageTimeout();
    if (typeof elements.board.setPointerCapture === 'function') {
      try {
        elements.board.setPointerCapture(event.pointerId);
      } catch (error) {
        /* ignore */
      }
    }
    startActivePath(cell, entry, event.pointerId);
  }

  function handlePointerMove(event) {
    const active = state.activePath;
    if (!active || active.pointerId !== event.pointerId) {
      return;
    }
    const target = getCellElementFromEvent(event);
    if (!target || target.getAttribute('data-blocked') === 'true') {
      return;
    }
    const x = Number.parseInt(target.dataset.x, 10);
    const y = Number.parseInt(target.dataset.y, 10);
    const cell = getCellAt(x, y);
    if (!cell) {
      return;
    }
    const pathState = active.pathState;
    const lastCell = active.lastCell || pathState.sequence[pathState.sequence.length - 1];
    if (!lastCell || cell === lastCell) {
      return;
    }
    if (!areAdjacent(cell, lastCell)) {
      return;
    }
    if (cell.occupantColor && cell.occupantColor !== pathState.colorId) {
      return;
    }
    const existingIndex = pathState.sequence.indexOf(cell);
    if (existingIndex >= 0) {
      if (existingIndex === pathState.sequence.length - 2) {
        const removed = pathState.sequence.pop();
        if (removed && removed !== cell) {
          clearCellOccupant(removed);
        }
        lastCell.element.classList.remove('the-line__cell--active');
        cell.element.classList.add('the-line__cell--active');
        active.lastCell = cell;
      }
      return;
    }
    if (cell.endpointColor && cell.endpointColor !== pathState.colorId) {
      return;
    }
    pathState.sequence.push(cell);
    occupyCellIfNeeded(cell, pathState);
    if (active.lastCell) {
      active.lastCell.element.classList.remove('the-line__cell--active');
    }
    cell.element.classList.add('the-line__cell--active');
    active.lastCell = cell;

    if (pathState.endpoints && pathState.endpoints.length === 2) {
      const startCell = pathState.sequence[0];
      const targetEndpoint = pathState.endpoints[0] === startCell
        ? pathState.endpoints[1]
        : pathState.endpoints[0];
      if (cell === targetEndpoint) {
        if (state.mode === 'single') {
          if (state.cellsRemaining === 0) {
            pathState.complete = true;
            finalizeActivePath(event.pointerId, true);
          }
        } else {
          pathState.complete = true;
          finalizeActivePath(event.pointerId, true);
        }
      }
    }
  }

  function handlePointerUp(event) {
    const active = state.activePath;
    if (!active || active.pointerId !== event.pointerId) {
      return;
    }
    finalizeActivePath(event.pointerId, false);
  }

  function finalizeActivePath(pointerId, triggeredCompletion) {
    const active = state.activePath;
    if (!active || active.pointerId !== pointerId) {
      cancelActivePath();
      return;
    }
    const board = state.elements && state.elements.board;
    if (board && typeof board.releasePointerCapture === 'function') {
      try {
        board.releasePointerCapture(pointerId);
      } catch (error) {
        /* ignore */
      }
    }
    if (active.lastCell) {
      active.lastCell.element.classList.remove('the-line__cell--active');
    }
    state.activePath = null;

    const pathState = active.pathState;
    if (!triggeredCompletion) {
      if (state.mode === 'multi') {
        clearPath(pathState);
        setMessage(
          'index.sections.theLine.messages.incompletePath',
          'Reliez la paire jusqu’à l’autre extrémité pour valider la couleur.'
        );
      }
      return;
    }

    if (state.mode === 'single') {
      if (pathState.complete) {
        checkForCompletion();
      }
      return;
    }

    checkForCompletion();
  }

  function checkForCompletion() {
    if (!state.currentPuzzle) {
      return;
    }
    if (state.mode === 'single') {
      const pathState = state.paths.get(SINGLE_PATH_COLOR.id);
      if (!pathState || !pathState.complete) {
        return;
      }
      if (state.cellsRemaining > 0) {
        return;
      }
      handlePuzzleCompleted();
      return;
    }
    for (const pathState of state.paths.values()) {
      if (!pathState.complete) {
        return;
      }
    }
    if (state.cellsRemaining > 0) {
      return;
    }
    handlePuzzleCompleted();
  }

  function handlePuzzleCompleted() {
    clearMessageTimeout();
    const counters = state.levelCounters[state.mode] || state.levelCounters.single;
    const currentLevel = counters[state.difficulty] || 1;
    setMessage(
      'index.sections.theLine.messages.completed',
      `Niveau ${currentLevel} terminé !`,
      { level: currentLevel }
    );
    maybeAwardCompletionReward();
    counters[state.difficulty] = currentLevel + 1;
    updateLevelDisplay();
    state.messageTimeout = window.setTimeout(() => {
      setMessage(
        'index.sections.theLine.messages.autoNext',
        'Nouveau parcours généré !',
        { level: counters[state.difficulty] }
      );
      state.messageTimeout = null;
      prepareNewPuzzle();
    }, 2200);
  }

  function maybeAwardCompletionReward() {
    if (!COMPLETION_REWARD || Math.random() >= COMPLETION_REWARD.chance) {
      return;
    }
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      return;
    }
    let gained = 0;
    try {
      gained = awardGacha(COMPLETION_REWARD.gachaTickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('The Line: unable to grant gacha tickets', error);
      gained = 0;
    }
    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = gained > 1 ? 's' : '';
      showToast(translate(
        'scripts.arcade.theLine.rewards.gachaWin',
        'Ticket gacha obtenu !',
        { count: formatIntegerLocalized(gained), suffix }
      ));
    }
  }

})();
