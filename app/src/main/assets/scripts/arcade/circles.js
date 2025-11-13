(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-circles-root]');
  if (!root) {
    return;
  }

  const elements = {
    root,
    canvas: document.getElementById('circlesCanvas'),
    canvasWrapper: root.querySelector('.circles__canvas-wrapper'),
    difficultySelect: document.getElementById('circlesDifficultySelect'),
    newButton: document.getElementById('circlesNewButton'),
    restartButton: document.getElementById('circlesRestartButton'),
    hintButton: document.getElementById('circlesHintButton'),
    seedValue: document.getElementById('circlesSeedValue'),
    movesValue: document.getElementById('circlesMovesValue'),
    rewardValue: document.getElementById('circlesRewardValue'),
    hintMessage: document.getElementById('circlesHintMessage'),
    winOverlay: document.getElementById('circlesWin'),
    winMessage: document.getElementById('circlesWinMessage'),
    againButton: document.getElementById('circlesAgainButton'),
    nextButton: document.getElementById('circlesNextButton')
  };

  if (!elements.canvas) {
    return;
  }

  const ctx = elements.canvas.getContext('2d');
  if (!ctx) {
    return;
  }

  const AUTOSAVE_GAME_ID = 'circles';
  const AUTOSAVE_VERSION = 3;
  const AUTOSAVE_DEBOUNCE_MS = 200;
  const DIFFICULTY_ORDER = Object.freeze(['easy', 'medium', 'hard']);
  const ROTATION_ANIMATION_DURATION = 320;
  const TOUCH_GESTURE_MIN_ANGLE = 0.2;

  const DEFAULT_CONFIG = Object.freeze({
    segments: 6,
    colors: Object.freeze(['#e74c3c', '#3498db', '#f1c40f', '#2ecc71', '#9b59b6', '#e67e22']),
    colorOffsets: Object.freeze([0, 3, 2, 5, 1, 4]),
    maxShuffleAttempts: 32,
    seedLength: 8,
    difficulties: Object.freeze({
      easy: Object.freeze({
        ringCount: 3,
        shuffleMoves: Object.freeze({ min: 6, max: 12 }),
        gachaTickets: 1
      }),
      medium: Object.freeze({
        ringCount: 4,
        shuffleMoves: Object.freeze({ min: 10, max: 18 }),
        gachaTickets: 2
      }),
      hard: Object.freeze({
        ringCounts: Object.freeze([5, 6]),
        shuffleMoves: Object.freeze({ min: 18, max: 32 }),
        gachaTickets: Object.freeze({ 5: 4, 6: 5 })
      })
    })
  });

  const RAW_CONFIG =
    typeof ARCADE_CIRCLES_CONFIG === 'object' && ARCADE_CIRCLES_CONFIG
      ? ARCADE_CIRCLES_CONFIG
      : null;

  const SETTINGS = normalizeSettings(RAW_CONFIG);

  const state = {
    settings: SETTINGS,
    ctx,
    difficulty: 'easy',
    segments: SETTINGS.segments,
    step: (Math.PI * 2) / SETTINGS.segments,
    ringCount: SETTINGS.difficulties.easy.ringCounts[0] || 3,
    rings: [],
    rotations: [],
    initialRotations: [],
    rotationLinks: [],
    ringMetrics: [],
    seed: '',
    moves: 0,
    solved: false,
    pendingSolved: false,
    rewardClaimed: false,
    autosaveTimer: null,
    autosaveSuppressed: false,
    resizeObserver: null,
    animation: createAnimationState(),
    solutionMap: null,
    hintContext: { type: 'default' },
    hintHighlight: null
  };

  const pointerGesture = createPointerGestureState();

  let languageListenerAttached = false;

  initialize();

  function initialize() {
    state.rings = createRingDefinitions(state.ringCount);
    state.rotations = new Array(state.ringCount).fill(0);
    state.initialRotations = state.rotations.slice();
    state.rotationLinks = generateRotationLinks(state.ringCount);

    attachEventListeners();
    renderHintMessage();
    resizeCanvas();

    if (!restoreFromAutosave()) {
      startNewPuzzle({ preserveDifficulty: true });
    } else {
      updateControlsState();
      draw();
    }
  }

  function attachEventListeners() {
    if (elements.difficultySelect) {
      elements.difficultySelect.addEventListener('change', handleDifficultyChange);
    }
    if (elements.newButton) {
      elements.newButton.addEventListener('click', () => {
        startNewPuzzle({ preserveDifficulty: true });
      });
    }
    if (elements.restartButton) {
      elements.restartButton.addEventListener('click', () => {
        restartPuzzle();
      });
    }
    if (elements.hintButton) {
      elements.hintButton.addEventListener('click', handleHintButtonClick);
    }
    if (elements.canvas) {
      elements.canvas.addEventListener('pointerdown', handleCanvasPointer, { passive: false });
      elements.canvas.addEventListener('pointermove', handleCanvasPointerMove, { passive: false });
      elements.canvas.addEventListener('pointerup', handleCanvasPointerEnd, { passive: false });
      elements.canvas.addEventListener('pointercancel', handleCanvasPointerEnd, { passive: false });
      elements.canvas.addEventListener('keydown', handleCanvasKeyDown);
    }
    if (elements.againButton) {
      elements.againButton.addEventListener('click', () => {
        hideWinOverlay();
        startNewPuzzle({ preserveDifficulty: true });
      });
    }
    if (elements.nextButton) {
      elements.nextButton.addEventListener('click', () => {
        const nextDifficulty = getNextDifficulty(state.difficulty);
        setDifficulty(nextDifficulty, { updateSelect: true, startPuzzle: true });
      });
    }

    if (typeof window !== 'undefined') {
      window.addEventListener('resize', resizeCanvas, { passive: true });
      if (typeof window.ResizeObserver === 'function' && elements.canvasWrapper) {
        state.resizeObserver = new window.ResizeObserver(() => {
          resizeCanvas();
        });
        state.resizeObserver.observe(elements.canvasWrapper);
      }
    }

    if (!languageListenerAttached && typeof document !== 'undefined') {
      document.addEventListener('i18n:change', handleLanguageChange);
      languageListenerAttached = true;
    }
  }

  function handleLanguageChange() {
    updateStats();
    updateWinMessage();
    renderHintMessage();
  }
  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const autosave = window.ArcadeAutosave;
    if (!autosave || typeof autosave !== 'object') {
      return null;
    }
    if (typeof autosave.get !== 'function' || typeof autosave.set !== 'function') {
      return null;
    }
    return autosave;
  }

  function buildAutosavePayload() {
    return {
      version: AUTOSAVE_VERSION,
      difficulty: state.difficulty,
      ringCount: clampInteger(state.ringCount, 3, 12, 3),
      seed: typeof state.seed === 'string' ? state.seed : '',
      moves: clampInteger(state.moves, 0, 9999, 0),
      rotations: state.rotations.slice(),
      initialRotations: state.initialRotations.slice(),
      rotationLinks: Array.isArray(state.rotationLinks) ? state.rotationLinks.slice() : [],
      solved: Boolean(state.solved),
      rewardClaimed: Boolean(state.rewardClaimed)
    };
  }

  function persistAutosaveNow() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    const payload = buildAutosavePayload();
    try {
      autosave.set(AUTOSAVE_GAME_ID, payload);
    } catch (error) {
      // Ignore persistence errors.
    }
  }

  function scheduleAutosave() {
    if (state.autosaveSuppressed) {
      return;
    }
    const autosave = getAutosaveApi();
    if (!autosave) {
      return;
    }
    if (typeof window === 'undefined') {
      persistAutosaveNow();
      return;
    }
    if (state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
    }
    state.autosaveTimer = window.setTimeout(() => {
      state.autosaveTimer = null;
      persistAutosaveNow();
    }, AUTOSAVE_DEBOUNCE_MS);
  }

  function flushAutosave() {
    if (typeof window !== 'undefined' && state.autosaveTimer != null) {
      window.clearTimeout(state.autosaveTimer);
      state.autosaveTimer = null;
    }
    persistAutosaveNow();
  }

  function withAutosaveSuppressed(callback) {
    state.autosaveSuppressed = true;
    try {
      callback();
    } finally {
      state.autosaveSuppressed = false;
    }
  }

  function restoreFromAutosave() {
    const autosave = getAutosaveApi();
    if (!autosave) {
      return false;
    }
    let payload = null;
    try {
      payload = autosave.get(AUTOSAVE_GAME_ID);
    } catch (error) {
      return false;
    }
    if (!payload || typeof payload !== 'object') {
      return false;
    }
    if (Number(payload.version) !== AUTOSAVE_VERSION) {
      return false;
    }

    const difficulty = typeof payload.difficulty === 'string' && DIFFICULTY_ORDER.includes(payload.difficulty)
      ? payload.difficulty
      : 'easy';
    setDifficulty(difficulty, { updateSelect: true, startPuzzle: false });

    const ringCount = clampInteger(payload.ringCount, 3, 12, state.ringCount);
    applyRingCount(ringCount);

    const rotationLinks = sanitizeRotationLinks(payload.rotationLinks, state.ringCount);
    if (!rotationLinks) {
      return false;
    }
    state.rotationLinks = rotationLinks;

    state.seed = typeof payload.seed === 'string' ? payload.seed : '';
    state.moves = clampInteger(payload.moves, 0, 9999, 0);

    state.rotations = sanitizeRotationArray(payload.rotations, state.ringCount);
    state.initialRotations = sanitizeRotationArray(payload.initialRotations, state.ringCount, state.rotations);
    state.solved = payload.solved === true && checkSolved(state.rotations);
    state.pendingSolved = false;
    state.rewardClaimed = payload.rewardClaimed === true;

    updateStats();
    updateWinMessage();
    resizeCanvas();

    recomputeSolutionMap();
    setHintHighlight(null);
    setHintContext(state.solved ? { type: 'solved' } : { type: 'default' });

    if (state.solved) {
      showWinOverlay();
    } else {
      hideWinOverlay();
    }
    return true;
  }

  function sanitizeRotationArray(source, expectedLength, fallback) {
    const length = clampInteger(expectedLength, 1, 12, 1);
    if (!Array.isArray(source) || source.length < length) {
      return Array.isArray(fallback) && fallback.length >= length
        ? fallback.slice(0, length)
        : new Array(length).fill(0);
    }
    const sanitized = new Array(length);
    for (let i = 0; i < length; i += 1) {
      sanitized[i] = normalizeRotationValue(source[i]);
    }
    return sanitized;
  }

  function sanitizeRotationLinks(source, expectedLength) {
    const length = clampInteger(expectedLength, 1, 12, 1);
    if (!Array.isArray(source) || source.length < length) {
      return null;
    }
    const sanitized = new Array(length);
    const usedTargets = new Set();
    for (let i = 0; i < length; i += 1) {
      const candidate = Number(source[i]);
      if (
        Number.isFinite(candidate) &&
        candidate >= 0 &&
        candidate < length &&
        Math.floor(candidate) === candidate &&
        candidate !== i &&
        !usedTargets.has(candidate)
      ) {
        sanitized[i] = candidate;
        usedTargets.add(candidate);
      } else {
        return null;
      }
    }
    for (let i = 0; i < length; i += 1) {
      const target = sanitized[i];
      if (Number.isFinite(target) && target >= 0 && target < length) {
        const reciprocal = sanitized[target];
        if (reciprocal === i) {
          return null;
        }
      }
    }
    return sanitized;
  }

  function normalizeSettings(raw) {
    const base = DEFAULT_CONFIG;
    const segments = clampInteger(raw && raw.segments, 3, 18, base.segments);
    const colors = buildColorPalette(raw && raw.colors, base.colors, segments);

    const normalizedDifficulties = {};
    const rawDifficulties = raw && typeof raw === 'object' ? raw.difficulties : null;

    DIFFICULTY_ORDER.forEach(key => {
      const fallbackDefinition = base.difficulties[key];
      const rawDefinition = rawDifficulties && typeof rawDifficulties === 'object' ? rawDifficulties[key] : null;
      normalizedDifficulties[key] = normalizeDifficultyDefinition(rawDefinition, fallbackDefinition);
    });

    const maxRingCount = Math.max(
      3,
      ...DIFFICULTY_ORDER.map(key => Math.max(...normalizedDifficulties[key].ringCounts))
    );

    const colorOffsets = buildColorOffsets(raw && raw.colorOffsets, base.colorOffsets, maxRingCount, segments);
    const maxShuffleAttempts = clampInteger(raw && raw.maxShuffleAttempts, 4, 200, base.maxShuffleAttempts);
    const seedLength = clampInteger(raw && raw.seedLength, 4, 32, base.seedLength);

    return {
      segments,
      colors,
      colorOffsets,
      maxShuffleAttempts,
      seedLength,
      difficulties: normalizedDifficulties
    };
  }

  function normalizeDifficultyDefinition(rawDefinition, fallbackDefinition) {
    const fallbackRingCounts = Array.isArray(fallbackDefinition.ringCounts)
      ? fallbackDefinition.ringCounts.slice()
      : [fallbackDefinition.ringCount];

    const rawRingCounts = rawDefinition && Array.isArray(rawDefinition.ringCounts)
      ? rawDefinition.ringCounts
      : Number.isFinite(rawDefinition && rawDefinition.ringCount)
        ? [rawDefinition.ringCount]
        : fallbackRingCounts;

    const ringCounts = rawRingCounts
      .map(value => clampInteger(value, 3, 12, null))
      .filter(value => Number.isFinite(value));

    if (!ringCounts.length) {
      ringCounts.push(clampInteger(fallbackRingCounts[0], 3, 12, 3));
    }

    const fallbackShuffle = fallbackDefinition.shuffleMoves || { min: 4, max: 12 };
    const rawShuffle = rawDefinition && rawDefinition.shuffleMoves;
    const shuffleMin = clampInteger(rawShuffle && rawShuffle.min, 0, 400, fallbackShuffle.min || 0);
    const shuffleMax = clampInteger(rawShuffle && rawShuffle.max, shuffleMin, 500, fallbackShuffle.max || shuffleMin);

    const gachaTickets = normalizeGachaTickets(rawDefinition && rawDefinition.gachaTickets, fallbackDefinition.gachaTickets);

    return {
      ringCounts,
      shuffleMoves: { min: shuffleMin, max: Math.max(shuffleMin, shuffleMax) },
      gachaTickets
    };
  }

  function normalizeGachaTickets(rawTickets, fallbackTickets) {
    if (Number.isFinite(rawTickets)) {
      return Math.max(0, Math.floor(rawTickets));
    }
    if (rawTickets && typeof rawTickets === 'object') {
      const normalized = {};
      Object.keys(rawTickets).forEach(key => {
        const value = clampInteger(rawTickets[key], 0, 99, null);
        if (Number.isFinite(value)) {
          normalized[String(key)] = value;
        }
      });
      if (Object.keys(normalized).length > 0) {
        return normalized;
      }
    }
    if (Number.isFinite(fallbackTickets)) {
      return Math.max(0, Math.floor(fallbackTickets));
    }
    if (fallbackTickets && typeof fallbackTickets === 'object') {
      const normalized = {};
      Object.keys(fallbackTickets).forEach(key => {
        const value = clampInteger(fallbackTickets[key], 0, 99, null);
        if (Number.isFinite(value)) {
          normalized[String(key)] = value;
        }
      });
      if (Object.keys(normalized).length > 0) {
        return normalized;
      }
    }
    return 0;
  }

  function buildColorPalette(rawColors, fallbackColors, segments) {
    const palette = Array.isArray(rawColors) && rawColors.length > 0 ? rawColors.slice() : fallbackColors.slice();
    const normalized = palette
      .filter(color => typeof color === 'string' && color.trim())
      .map(color => color.trim());
    if (!normalized.length) {
      normalized.push('#ffffff');
    }
    while (normalized.length < segments) {
      normalized.push(normalized[normalized.length % palette.length]);
    }
    return normalized.slice(0, segments);
  }

  function buildColorOffsets(rawOffsets, fallbackOffsets, requiredLength, segments) {
    const source = Array.isArray(rawOffsets) && rawOffsets.length > 0
      ? rawOffsets
      : Array.isArray(fallbackOffsets)
        ? fallbackOffsets
        : [0];
    const offsets = new Array(Math.max(requiredLength, source.length));
    for (let i = 0; i < offsets.length; i += 1) {
      const raw = source[i] != null ? source[i] : source[i % source.length];
      offsets[i] = normalizeRotationValue(raw, segments);
    }
    return offsets;
  }
  function clampInteger(value, min, max, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return Number.isFinite(fallback) ? Math.min(Math.max(fallback, min), max) : min;
    }
    if (numeric < min) {
      return min;
    }
    if (numeric > max) {
      return max;
    }
    return Math.floor(numeric);
  }

  function normalizeRotationValue(value, segmentsOverride) {
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || !Number.isFinite(segments) || segments <= 0) {
      return 0;
    }
    const modulo = ((Math.floor(numeric) % segments) + segments) % segments;
    return modulo;
  }

  function normalizeRotationForDisplay(value, segmentsOverride) {
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || !Number.isFinite(segments) || segments <= 0) {
      return 0;
    }
    const modulo = ((numeric % segments) + segments) % segments;
    return modulo;
  }

  function randomInt(min, max) {
    const lower = Number.isFinite(min) ? Math.floor(min) : 0;
    const upper = Number.isFinite(max) ? Math.floor(max) : lower;
    if (upper <= lower) {
      return lower;
    }
    return lower + Math.floor(Math.random() * (upper - lower + 1));
  }

  function generateSeed(length) {
    const size = clampInteger(length, 4, 32, state.settings.seedLength);
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    if (typeof window !== 'undefined' && window.crypto && typeof window.crypto.getRandomValues === 'function') {
      const bytes = new Uint8Array(size);
      window.crypto.getRandomValues(bytes);
      return Array.from(bytes, byte => alphabet[byte % alphabet.length]).join('');
    }
    let seed = '';
    for (let i = 0; i < size; i += 1) {
      seed += alphabet[Math.floor(Math.random() * alphabet.length)];
    }
    return seed;
  }

  function formatNumber(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    if (typeof formatIntegerLocalized === 'function') {
      return formatIntegerLocalized(numeric);
    }
    if (typeof Intl !== 'undefined' && Intl.NumberFormat) {
      return new Intl.NumberFormat().format(numeric);
    }
    return String(Math.floor(numeric));
  }

  function translateText(key, fallback, params) {
    const translator = typeof translate === 'function'
      ? translate
      : typeof window !== 'undefined' && typeof window.translate === 'function'
        ? window.translate
        : null;
    if (!translator) {
      if (fallback && typeof fallback === 'string' && params && typeof params === 'object') {
        return fallback.replace(/\{(\w+)\}/g, (_, token) => (token in params ? params[token] : ''));
      }
      return typeof fallback === 'string' ? fallback : '';
    }
    return translator(key, fallback, params || undefined);
  }

  function createRingDefinitions(ringCount) {
    const count = clampInteger(ringCount, 3, 12, 3);
    const rings = new Array(count);
    for (let i = 0; i < count; i += 1) {
      const offset = state.settings.colorOffsets[i] != null
        ? state.settings.colorOffsets[i]
        : state.settings.colorOffsets[i % state.settings.colorOffsets.length];
      rings[i] = {
        colors: rotateColors(state.settings.colors, offset)
      };
    }
    return rings;
  }

  function getSolvedRotations(ringCount, segmentsOverride) {
    const count = clampInteger(ringCount, 1, 12, 1);
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    const offsets = Array.isArray(state.settings.colorOffsets) && state.settings.colorOffsets.length > 0
      ? state.settings.colorOffsets
      : [0];
    const rotations = new Array(count);
    for (let i = 0; i < count; i += 1) {
      const offset = offsets[i] != null ? offsets[i] : offsets[i % offsets.length];
      rotations[i] = normalizeRotationValue(offset, segments);
    }
    return rotations;
  }

  function rotateColors(colors, offset) {
    const palette = Array.isArray(colors) && colors.length > 0 ? colors : ['#ffffff'];
    const normalizedOffset = normalizeRotationValue(offset, palette.length);
    const rotated = new Array(palette.length);
    for (let i = 0; i < palette.length; i += 1) {
      rotated[i] = palette[(i + normalizedOffset) % palette.length];
    }
    return rotated;
  }
  function applyRingCount(ringCount) {
    const count = clampInteger(ringCount, 3, 12, Math.max(state.ringCount || 3, 3));
    if (count === state.ringCount && Array.isArray(state.rotations) && state.rotations.length === count) {
      state.rings = createRingDefinitions(count);
      computeRingMetrics();
      return;
    }
    state.ringCount = count;
    state.rings = createRingDefinitions(count);
    state.rotations = new Array(count).fill(0);
    state.initialRotations = state.rotations.slice();
    state.rotationLinks = generateRotationLinks(count);
    resetAnimationState();
    computeRingMetrics();
  }

  function getDifficultySettings(difficultyKey) {
    const key = DIFFICULTY_ORDER.includes(difficultyKey) ? difficultyKey : 'easy';
    return state.settings.difficulties[key] || state.settings.difficulties.easy;
  }

  function pickRingCountForDifficulty(difficultyKey) {
    const settings = getDifficultySettings(difficultyKey);
    const options = Array.isArray(settings.ringCounts) && settings.ringCounts.length > 0
      ? settings.ringCounts
      : [state.ringCount];
    if (options.length === 1) {
      return clampInteger(options[0], 3, 12, state.ringCount);
    }
    const index = randomInt(0, options.length - 1);
    return clampInteger(options[index], 3, 12, options[0]);
  }

  function startNewPuzzle(options) {
    const preserveDifficulty = options && options.preserveDifficulty === true;
    const difficultyKey = preserveDifficulty ? state.difficulty : 'easy';
    const ringCount = pickRingCountForDifficulty(difficultyKey);
    const puzzle = generatePuzzleDefinition(ringCount, difficultyKey);

    withAutosaveSuppressed(() => {
      resetAnimationState();
      applyPuzzleDefinition(puzzle, difficultyKey);
      hideWinOverlay();
      scheduleAutosave();
    });
  }

  function applyPuzzleDefinition(definition, difficultyKey) {
    if (!definition || typeof definition !== 'object') {
      return;
    }
    state.difficulty = DIFFICULTY_ORDER.includes(difficultyKey) ? difficultyKey : state.difficulty;
    applyRingCount(definition.ringCount);
    const links = sanitizeRotationLinks(definition.rotationLinks, state.ringCount);
    state.rotationLinks = links || generateRotationLinks(state.ringCount);
    state.rotations = sanitizeRotationArray(definition.rotations, state.ringCount);
    state.initialRotations = state.rotations.slice();
    state.seed = typeof definition.seed === 'string' ? definition.seed : generateSeed(state.settings.seedLength);
    state.moves = 0;
    state.solved = checkSolved(state.rotations);
    state.pendingSolved = false;
    state.rewardClaimed = false;
    recomputeSolutionMap();
    setHintHighlight(null);
    setHintContext(state.solved ? { type: 'solved' } : { type: 'default' });
    updateControlsState();
    updateStats();
    updateWinMessage();
    resizeCanvas();
    draw();
  }

  function restartPuzzle() {
    if (!Array.isArray(state.initialRotations) || !state.initialRotations.length) {
      return;
    }
    withAutosaveSuppressed(() => {
      resetAnimationState();
      state.rotations = sanitizeRotationArray(state.initialRotations, state.ringCount);
      state.moves = 0;
      state.solved = checkSolved(state.rotations);
      state.pendingSolved = false;
      state.rewardClaimed = false;
      hideWinOverlay();
      recomputeSolutionMap();
      setHintHighlight(null);
      setHintContext(state.solved ? { type: 'solved' } : { type: 'default' });
      updateControlsState();
      updateStats();
      draw();
      scheduleAutosave();
    });
  }

  function generatePuzzleDefinition(ringCount, difficultyKey) {
    const count = clampInteger(ringCount, 3, 12, Math.max(state.ringCount || 3, 3));
    const settings = getDifficultySettings(difficultyKey);
    const minMoves = clampInteger(settings.shuffleMoves.min, 1, 400, 6);
    const maxMoves = clampInteger(settings.shuffleMoves.max, minMoves, 500, Math.max(minMoves, 12));
    const attempts = clampInteger(state.settings.maxShuffleAttempts, 4, 200, 32);
    const rings = createRingDefinitions(count);
    const segments = state.segments;
    const baselineRotations = getSolvedRotations(count, segments);

    for (let attempt = 0; attempt < attempts; attempt += 1) {
      const candidateLinks = sanitizeRotationLinks(generateRotationLinks(count), count);
      if (!candidateLinks) {
        continue;
      }
      const moveCount = randomInt(minMoves, maxMoves);
      const rotations = createScrambledRotations(
        count,
        candidateLinks,
        moveCount,
        segments,
        baselineRotations
      );
      if (!rotations) {
        continue;
      }
      if (!isSolvedForRings(rotations, rings, segments)) {
        return {
          ringCount: count,
          rotations,
          rotationLinks: candidateLinks,
          seed: generateSeed(state.settings.seedLength)
        };
      }
    }

    const fallbackLinks = sanitizeRotationLinks(generateRotationLinks(count), count) || generateRotationLinks(count);
    const fallbackRotations = getSolvedRotations(count, segments);
    if (count > 0) {
      const index = Math.max(0, count - 1);
      applyRotationToArray(fallbackRotations, index, 1, fallbackLinks, segments);
    }
    return {
      ringCount: count,
      rotations: fallbackRotations.map(value => normalizeRotationValue(value, segments)),
      rotationLinks: fallbackLinks,
      seed: generateSeed(state.settings.seedLength)
    };
  }

  function createScrambledRotations(
    count,
    rotationLinks,
    moveCount,
    segmentsOverride,
    baselineRotations
  ) {
    const length = clampInteger(count, 1, 12, count);
    if (!Array.isArray(rotationLinks) || rotationLinks.length !== length) {
      return null;
    }
    const moves = clampInteger(moveCount, 0, 2000, 0);
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    const startRotations = Array.isArray(baselineRotations) && baselineRotations.length >= length
      ? baselineRotations.slice(0, length)
      : getSolvedRotations(length, segments);
    const rotations = startRotations.slice();
    if (moves <= 0) {
      return rotations;
    }
    for (let move = 0; move < moves; move += 1) {
      const index = randomInt(0, length - 1);
      const direction = Math.random() < 0.5 ? 1 : -1;
      applyRotationToArray(rotations, index, direction, rotationLinks, segments);
    }
    for (let i = 0; i < rotations.length; i += 1) {
      rotations[i] = normalizeRotationValue(rotations[i], segments);
    }
    return rotations;
  }

  function applyRotationToArray(rotations, index, direction, rotationLinks, segmentsOverride) {
    if (!Array.isArray(rotations)) {
      return [];
    }
    const numericIndex = Number(index);
    if (!Number.isFinite(numericIndex)) {
      return [];
    }
    const ringIndex = Math.min(Math.max(Math.floor(numericIndex), 0), rotations.length - 1);
    const dir = direction > 0 ? 1 : -1;
    rotations[ringIndex] = normalizeRotationValue(rotations[ringIndex] + dir, segmentsOverride);
    const affected = [ringIndex];
    if (Array.isArray(rotationLinks) && rotationLinks.length === rotations.length) {
      const targetCandidate = rotationLinks[ringIndex];
      const targetIndex = Number(targetCandidate);
      if (
        Number.isFinite(targetIndex) &&
        targetIndex >= 0 &&
        targetIndex < rotations.length &&
        Math.floor(targetIndex) === targetIndex &&
        targetIndex !== ringIndex
      ) {
        rotations[targetIndex] = normalizeRotationValue(rotations[targetIndex] + dir, segmentsOverride);
        if (!affected.includes(targetIndex)) {
          affected.push(targetIndex);
        }
      }
    }
    return affected;
  }
  function applyUserRotation(index, direction) {
    if (state.solved || (state.animation && state.animation.active)) {
      return false;
    }
    const ringIndex = clampInteger(index, 0, state.rotations.length - 1, null);
    if (!Number.isFinite(ringIndex)) {
      return false;
    }
    if (!Array.isArray(state.rotationLinks) || state.rotationLinks.length !== state.ringCount) {
      state.rotationLinks = generateRotationLinks(state.ringCount);
    }
    const affected = applyRotationToArray(state.rotations, ringIndex, direction, state.rotationLinks, state.segments);
    if (!Array.isArray(affected) || affected.length === 0) {
      return false;
    }
    setHintHighlight(null);
    setHintContext({ type: 'default' });
    state.moves += 1;
    updateControlsState();
    updateStats();
    scheduleAutosave();
    const solved = checkSolved(state.rotations);
    state.pendingSolved = solved;
    if (!solved) {
      state.pendingSolved = false;
    }
    startRotationAnimation(affected, direction);
    return true;
  }

  function checkSolved(rotations) {
    return isSolvedForRings(rotations, state.rings, state.segments);
  }

  function isSolvedForRings(rotations, rings, segmentsOverride) {
    if (!Array.isArray(rotations) || !Array.isArray(rings) || rings.length === 0) {
      return false;
    }
    const ringCount = Math.min(rings.length, rotations.length);
    if (ringCount === 0) {
      return false;
    }
    const segments = Math.max(1, Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments);
    for (let segmentIndex = 0; segmentIndex < segments; segmentIndex += 1) {
      let expectedColor = null;
      for (let ringIndex = 0; ringIndex < ringCount; ringIndex += 1) {
        const ring = rings[ringIndex];
        if (!ring || !Array.isArray(ring.colors) || ring.colors.length === 0) {
          return false;
        }
        const paletteSize = ring.colors.length;
        const rotationValue = normalizeRotationValue(rotations[ringIndex] || 0, segments);
        const colorIndex = ((segmentIndex - rotationValue) % paletteSize + paletteSize) % paletteSize;
        const color = normalizeColorString(ring.colors[colorIndex]);
        if (!color) {
          return false;
        }
        if (expectedColor == null) {
          expectedColor = color;
        } else if (color !== expectedColor) {
          return false;
        }
      }
    }
    return true;
  }

  function handleSolved() {
    if (state.solved) {
      return;
    }
    state.solved = true;
    state.pendingSolved = false;
    setHintHighlight(null);
    setHintContext({ type: 'solved' });
    updateWinMessage();
    showWinOverlay();
    awardCompletionTickets();
    flushAutosave();
  }

  function getCurrentRewardTickets(ringCountOverride) {
    const ringCount = clampInteger(
      ringCountOverride != null ? ringCountOverride : state.ringCount,
      3,
      12,
      state.ringCount
    );
    const settings = getDifficultySettings(state.difficulty);
    const rewardDefinition = settings.gachaTickets;
    if (Number.isFinite(rewardDefinition)) {
      return Math.max(0, Math.floor(rewardDefinition));
    }
    if (rewardDefinition && typeof rewardDefinition === 'object') {
      const direct = rewardDefinition[String(ringCount)];
      if (Number.isFinite(direct)) {
        return Math.max(0, Math.floor(direct));
      }
      const firstValue = Object.values(rewardDefinition).find(value => Number.isFinite(value));
      if (Number.isFinite(firstValue)) {
        return Math.max(0, Math.floor(firstValue));
      }
    }
    return 0;
  }

  function awardCompletionTickets() {
    if (state.rewardClaimed) {
      return;
    }
    const tickets = getCurrentRewardTickets();
    if (!Number.isFinite(tickets) || tickets <= 0) {
      state.rewardClaimed = true;
      return;
    }
    const awardFunction = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardFunction !== 'function') {
      state.rewardClaimed = true;
      return;
    }
    let gained = 0;
    try {
      gained = awardFunction(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Circles: unable to grant gacha tickets', error);
      gained = tickets;
    }
    state.rewardClaimed = true;
    scheduleAutosave();
    const granted = Number.isFinite(gained) && gained > 0 ? Math.floor(gained) : tickets;
    if (granted > 0) {
      const suffix = granted > 1 ? 's' : '';
      const message = translateText(
        'scripts.arcade.circles.rewardToast',
        'Circles : +{count} ticket{suffix} gacha !',
        { count: formatNumber(granted), suffix }
      );
      showToastMessage(message);
    }
  }

  function showToastMessage(message) {
    if (!message) {
      return;
    }
    if (typeof showToast === 'function') {
      showToast(message);
      return;
    }
    if (typeof window !== 'undefined' && typeof window.showToast === 'function') {
      window.showToast(message);
    }
  }

  function handleHintButtonClick() {
    if (state.solved) {
      setHintHighlight(null);
      setHintContext({ type: 'solved' });
      return;
    }
    if (!(state.solutionMap instanceof Map)) {
      setHintHighlight(null);
      setHintContext({ type: 'unavailable' });
      return;
    }
    const key = serializeRotations(state.rotations, state.segments);
    const entry = key ? state.solutionMap.get(key) : null;
    if (!entry || !Number.isFinite(entry.distance)) {
      setHintHighlight(null);
      setHintContext({ type: 'unavailable' });
      return;
    }
    if (!entry.bestMove || entry.distance <= 0) {
      setHintHighlight(null);
      setHintContext({ type: 'solved' });
      return;
    }
    const ringIndex = Number(entry.bestMove.ringIndex);
    const direction = Number(entry.bestMove.direction);
    if (!Number.isFinite(ringIndex) || !Number.isFinite(direction)) {
      setHintHighlight(null);
      setHintContext({ type: 'unavailable' });
      return;
    }
    const moved = applyUserRotation(ringIndex, direction);
    if (!moved) {
      setHintHighlight(null);
      setHintContext({ type: 'unavailable' });
      return;
    }
    const remaining = Math.max(0, Math.floor(entry.distance - 1));
    const contextType = remaining > 0 ? 'auto' : 'autoSolved';
    setHintHighlight(null);
    setHintContext({
      type: contextType,
      ringIndex,
      direction,
      remaining
    });
  }

  function setHintContext(context) {
    const fallbackContext = { type: 'default' };
    const next = context && typeof context === 'object' && typeof context.type === 'string'
      ? context
      : fallbackContext;
    state.hintContext = {
      type: next.type,
      ringIndex: Number.isFinite(next.ringIndex) ? next.ringIndex : null,
      direction: Number.isFinite(next.direction) ? Math.sign(next.direction) || 1 : null,
      remaining: Number.isFinite(next.remaining) ? Math.max(0, Math.floor(next.remaining)) : null
    };
    renderHintMessage();
  }

  function renderHintMessage() {
    if (!elements.hintMessage) {
      return;
    }
    const context = state.hintContext && typeof state.hintContext === 'object'
      ? state.hintContext
      : { type: 'default' };
    let message = '';
    const isMoveContext =
      (context.type === 'suggestion' || context.type === 'auto' || context.type === 'autoSolved') &&
      context.ringIndex != null &&
      context.direction != null &&
      context.remaining != null;
    if (isMoveContext) {
      const ringNumber = formatNumber(context.ringIndex + 1);
      const directionText = getHintDirectionLabel(context.direction);
      const moves = Math.max(0, context.remaining);
      const suffix = moves > 1 ? 's' : '';
      if (context.type === 'autoSolved' || (context.type === 'auto' && moves === 0)) {
        message = translateText(
          'scripts.arcade.circles.hint.autoSolved',
          'Aide a tourné l’anneau {ring} vers le {direction}. Puzzle résolu !',
          {
            ring: ringNumber,
            direction: directionText
          }
        );
      } else if (context.type === 'auto') {
        message = translateText(
          'scripts.arcade.circles.hint.auto',
          'Aide a tourné l’anneau {ring} vers le {direction}. Il reste {moves} coup{suffix}.',
          {
            ring: ringNumber,
            direction: directionText,
            moves: formatNumber(moves),
            suffix
          }
        );
      } else {
        message = translateText(
          'scripts.arcade.circles.hint.move',
          'Tournez l’anneau {ring} vers le {direction}. Il reste {moves} coup{suffix} à jouer.',
          {
            ring: ringNumber,
            direction: directionText,
            moves: formatNumber(moves),
            suffix
          }
        );
      }
    } else if (context.type === 'solved') {
      message = translateText(
        'scripts.arcade.circles.hint.solved',
        'Puzzle déjà résolu ! Lancez un nouveau puzzle pour continuer.'
      );
    } else if (context.type === 'unavailable') {
      message = translateText(
        'scripts.arcade.circles.hint.unavailable',
        'Aucun indice disponible pour ce puzzle. Relancez ou réinitialisez.'
      );
    } else {
      message = translateText(
        'scripts.arcade.circles.hint.default',
        'Besoin d’un coup de pouce ? Cliquez sur « Aide » pour jouer automatiquement le meilleur coup.'
      );
    }
    elements.hintMessage.textContent = message;
  }

  function getHintDirectionLabel(direction) {
    const clockwise = direction > 0;
    return translateText(
      clockwise
        ? 'scripts.arcade.circles.hint.direction.clockwise'
        : 'scripts.arcade.circles.hint.direction.counterClockwise',
      clockwise ? 'sens horaire' : 'sens antihoraire'
    );
  }

  function setHintHighlight(indices) {
    if (Array.isArray(indices) && indices.length > 0) {
      const sanitized = new Set();
      for (let i = 0; i < indices.length; i += 1) {
        const value = Number(indices[i]);
        if (Number.isFinite(value)) {
          const normalized = Math.floor(value);
          if (normalized >= 0 && normalized < state.ringCount) {
            sanitized.add(normalized);
          }
        }
      }
      state.hintHighlight = sanitized.size > 0 ? sanitized : null;
    } else {
      state.hintHighlight = null;
    }
    draw();
  }

  function recomputeSolutionMap() {
    if (!Array.isArray(state.rotationLinks) || state.rotationLinks.length !== state.ringCount) {
      state.solutionMap = null;
      return;
    }
    state.solutionMap = buildSolutionMap(state.rotationLinks, state.ringCount, state.segments);
  }

  function buildSolutionMap(rotationLinks, ringCount, segmentsOverride) {
    const length = clampInteger(ringCount, 1, 12, ringCount);
    if (length <= 0) {
      return null;
    }
    const sanitizedLinks = sanitizeRotationLinks(rotationLinks, length);
    if (!sanitizedLinks) {
      return null;
    }
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    const start = getSolvedRotations(length, segments);
    const startKey = serializeRotations(start, segments);
    const queue = [start];
    const visited = new Map();
    visited.set(startKey, { distance: 0, bestMove: null });
    let index = 0;
    while (index < queue.length) {
      const current = queue[index];
      index += 1;
      const currentKey = serializeRotations(current, segments);
      const info = visited.get(currentKey);
      for (let ringIndex = 0; ringIndex < length; ringIndex += 1) {
        for (let direction = -1; direction <= 1; direction += 2) {
          const next = current.slice();
          applyRotationToArray(next, ringIndex, direction, sanitizedLinks, segments);
          const key = serializeRotations(next, segments);
          if (!visited.has(key)) {
            visited.set(key, {
              distance: info.distance + 1,
              bestMove: { ringIndex, direction: direction * -1 }
            });
            queue.push(next);
          }
        }
      }
    }
    return visited;
  }

  function serializeRotations(rotations, segmentsOverride) {
    if (!Array.isArray(rotations)) {
      return '';
    }
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    const normalized = new Array(rotations.length);
    for (let i = 0; i < rotations.length; i += 1) {
      normalized[i] = normalizeRotationValue(rotations[i], segments);
    }
    return normalized.join(',');
  }

  function updateStats() {
    if (elements.seedValue) {
      elements.seedValue.textContent = state.seed || '—';
    }
    if (elements.movesValue) {
      elements.movesValue.textContent = formatNumber(state.moves);
    }
    updateRewardDisplay();
  }

  function updateControlsState() {
    if (!elements.restartButton) {
      return;
    }
    const disabled = !Array.isArray(state.initialRotations) || state.initialRotations.length === 0;
    elements.restartButton.disabled = disabled;
    if (disabled) {
      elements.restartButton.setAttribute('aria-disabled', 'true');
    } else {
      elements.restartButton.removeAttribute('aria-disabled');
    }
  }

  function updateRewardDisplay() {
    if (!elements.rewardValue) {
      return;
    }
    const tickets = getCurrentRewardTickets();
    elements.rewardValue.textContent = formatNumber(tickets);
  }

  function updateWinMessage() {
    if (!elements.winMessage) {
      return;
    }
    const moves = formatNumber(state.moves);
    const moveSuffix = state.moves > 1 ? 's' : '';
    const tickets = getCurrentRewardTickets();
    const ticketSuffix = tickets > 1 ? 's' : '';
    const message = translateText(
      'scripts.arcade.circles.winMessage',
      'Synchronisation parfaite en {moves} coup{moveSuffix} !',
      { moves, moveSuffix }
    );
    const rewardPart = tickets > 0
      ? translateText(
          'scripts.arcade.circles.winReward',
          'Récompense : +{tickets} ticket{ticketSuffix} gacha.',
          { tickets: formatNumber(tickets), ticketSuffix }
        )
      : '';
    elements.winMessage.textContent = rewardPart ? `${message} ${rewardPart}` : message;
  }

  function showWinOverlay() {
    if (elements.winOverlay) {
      elements.winOverlay.hidden = false;
    }
  }

  function hideWinOverlay() {
    if (elements.winOverlay) {
      elements.winOverlay.hidden = true;
    }
  }
  function resizeCanvas() {
    if (!elements.canvas || !elements.canvasWrapper) {
      return;
    }
    const rect = elements.canvasWrapper.getBoundingClientRect();
    let width = rect.width;
    let height = rect.height;
    if (typeof window !== 'undefined' && typeof window.getComputedStyle === 'function') {
      const styles = window.getComputedStyle(elements.canvasWrapper);
      const paddingX = (parseFloat(styles.paddingLeft) || 0) + (parseFloat(styles.paddingRight) || 0);
      const paddingY = (parseFloat(styles.paddingTop) || 0) + (parseFloat(styles.paddingBottom) || 0);
      width = Math.max(0, width - paddingX);
      height = Math.max(0, height - paddingY);
    }
    const size = Math.max(100, Math.min(width, height));
    const ratio = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
      ? window.devicePixelRatio
      : 1;
    const pixelSize = Math.floor(size * ratio);
    if (elements.canvas.width !== pixelSize || elements.canvas.height !== pixelSize) {
      elements.canvas.width = pixelSize;
      elements.canvas.height = pixelSize;
    }
    elements.canvas.style.width = `${size}px`;
    elements.canvas.style.height = `${size}px`;
    computeRingMetrics();
    draw();
  }

  function computeRingMetrics() {
    if (!elements.canvas) {
      return;
    }
    const width = elements.canvas.width;
    const height = elements.canvas.height;
    const radius = Math.max(10, Math.min(width, height) / 2 - width * 0.05);
    const ringCount = Math.max(1, state.ringCount);
    const thickness = radius / (ringCount + 0.25);
    const gap = Math.min(thickness * 0.18, 6 * (typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio) ? window.devicePixelRatio : 1));

    state.ringMetrics = new Array(ringCount);
    let currentOuter = radius;
    for (let i = 0; i < ringCount; i += 1) {
      const inner = Math.max(0, currentOuter - (thickness - gap));
      state.ringMetrics[i] = { outer: currentOuter, inner };
      currentOuter = inner - gap;
      if (currentOuter < 0) {
        currentOuter = 0;
      }
    }
  }

  function draw() {
    if (!elements.canvas || !state.ctx) {
      return;
    }
    const ctx2d = state.ctx;
    const width = elements.canvas.width;
    const height = elements.canvas.height;
    ctx2d.clearRect(0, 0, width, height);
    ctx2d.save();
    ctx2d.translate(width / 2, height / 2);
    const step = state.step;
    for (let index = 0; index < state.ringCount; index += 1) {
      const metrics = state.ringMetrics[index];
      const ring = state.rings[index];
      if (!metrics || !ring) {
        continue;
      }
      const rotationValue = getDisplayRotationValue(index);
      const rotation = rotationValue * step;
      for (let i = 0; i < state.segments; i += 1) {
        const startAngle = rotation + i * step;
        const endAngle = startAngle + step;
        ctx2d.beginPath();
        ctx2d.arc(0, 0, metrics.outer, startAngle, endAngle);
        ctx2d.arc(0, 0, Math.max(metrics.inner, 0), endAngle, startAngle, true);
        ctx2d.closePath();
        ctx2d.fillStyle = ring.colors[i % ring.colors.length] || '#ffffff';
        ctx2d.fill();
      }
      const animationHighlighted =
        state.animation &&
        state.animation.active &&
        state.animation.highlight instanceof Set &&
        state.animation.highlight.has(index);
      const hintHighlighted = state.hintHighlight instanceof Set && state.hintHighlight.has(index);
      if (animationHighlighted || hintHighlighted) {
        drawRingHighlight(metrics);
      }
    }
    ctx2d.restore();
  }

  function getDisplayRotationValue(index) {
    const rotationsLength = Array.isArray(state.rotations) ? state.rotations.length : 0;
    if (index < 0 || index >= rotationsLength) {
      return 0;
    }
    if (state.animation && state.animation.active && state.animation.current instanceof Map && state.animation.current.has(index)) {
      return normalizeRotationForDisplay(state.animation.current.get(index), state.segments);
    }
    return normalizeRotationForDisplay(state.rotations[index] || 0, state.segments);
  }

  function drawRingHighlight(metrics) {
    if (!metrics || !state.ctx) {
      return;
    }
    const ctx2d = state.ctx;
    const inner = Math.max(metrics.inner, 0);
    const thickness = Math.max(metrics.outer - inner, 0);
    ctx2d.save();
    ctx2d.beginPath();
    ctx2d.arc(0, 0, metrics.outer, 0, Math.PI * 2);
    ctx2d.arc(0, 0, inner, Math.PI * 2, 0, true);
    ctx2d.closePath();
    ctx2d.fillStyle = 'rgba(255, 255, 255, 0.25)';
    ctx2d.fill();
    ctx2d.beginPath();
    ctx2d.arc(0, 0, (metrics.outer + inner) / 2, 0, Math.PI * 2);
    ctx2d.strokeStyle = 'rgba(94, 213, 255, 0.85)';
    ctx2d.lineWidth = Math.max(2, thickness * 0.12);
    ctx2d.shadowColor = 'rgba(94, 213, 255, 0.6)';
    ctx2d.shadowBlur = Math.max(4, thickness * 0.35);
    ctx2d.stroke();
    ctx2d.restore();
  }

  function createAnimationState() {
    return {
      active: false,
      start: 0,
      duration: ROTATION_ANIMATION_DURATION,
      frameRequest: null,
      from: new Map(),
      to: new Map(),
      current: new Map(),
      highlight: new Set()
    };
  }

  function resetAnimationState() {
    if (state.animation && state.animation.frameRequest != null) {
      cancelAnimationFrameSafe(state.animation.frameRequest);
    }
    state.animation = createAnimationState();
  }

  function startRotationAnimation(indices, direction) {
    const dir = direction > 0 ? 1 : -1;
    const sanitized = Array.isArray(indices)
      ? Array.from(
          new Set(
            indices
              .map(value => Number(value))
              .filter(value =>
                Number.isFinite(value) &&
                value >= 0 &&
                Math.floor(value) === value &&
                value < state.rotations.length
              )
          )
        )
      : [];
    if (!sanitized.length) {
      draw();
      if (state.pendingSolved) {
        const shouldHandle = state.pendingSolved;
        state.pendingSolved = false;
        if (shouldHandle) {
          handleSolved();
        }
      }
      return;
    }
    resetAnimationState();
    const animation = state.animation;
    animation.active = true;
    animation.start = getTimestamp();
    sanitized.forEach(index => {
      const endValue = normalizeRotationForDisplay(state.rotations[index] || 0, state.segments);
      const startValue = normalizeRotationForDisplay(endValue - dir, state.segments);
      animation.from.set(index, startValue);
      animation.to.set(index, endValue);
      animation.current.set(index, startValue);
      animation.highlight.add(index);
    });
    draw();
    const step = timestamp => {
      if (!state.animation || !state.animation.active) {
        return;
      }
      const anim = state.animation;
      anim.frameRequest = null;
      const time = Number.isFinite(timestamp) ? timestamp : getTimestamp();
      const duration = anim.duration > 0 ? anim.duration : ROTATION_ANIMATION_DURATION;
      const elapsed = time - anim.start;
      const progress = duration > 0 ? Math.min(Math.max(elapsed / duration, 0), 1) : 1;
      const eased = easeInOutCubic(progress);
      anim.from.forEach((startValue, index) => {
        const endValue = anim.to.get(index);
        const delta = computeShortestDelta(startValue, endValue, state.segments);
        anim.current.set(index, startValue + delta * eased);
      });
      draw();
      if (progress < 1) {
        anim.frameRequest = requestAnimationFrameSafe(step);
        return;
      }
      const shouldHandleSolved = state.pendingSolved;
      state.pendingSolved = false;
      resetAnimationState();
      draw();
      if (shouldHandleSolved) {
        handleSolved();
      }
    };
    const handle = requestAnimationFrameSafe(step);
    if (handle != null && state.animation && state.animation.active) {
      state.animation.frameRequest = handle;
    }
  }

  function requestAnimationFrameSafe(callback) {
    if (typeof window !== 'undefined') {
      if (typeof window.requestAnimationFrame === 'function') {
        return window.requestAnimationFrame(callback);
      }
      if (typeof window.setTimeout === 'function') {
        return window.setTimeout(() => {
          callback(getTimestamp());
        }, 16);
      }
    }
    callback(getTimestamp());
    return null;
  }

  function cancelAnimationFrameSafe(handle) {
    if (handle == null) {
      return;
    }
    if (typeof window !== 'undefined') {
      if (typeof window.cancelAnimationFrame === 'function') {
        window.cancelAnimationFrame(handle);
        return;
      }
      if (typeof window.clearTimeout === 'function') {
        window.clearTimeout(handle);
      }
    }
  }

  function getTimestamp() {
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
      return performance.now();
    }
    return Date.now();
  }

  function easeInOutCubic(t) {
    const clamped = Math.min(Math.max(t, 0), 1);
    return clamped < 0.5
      ? 4 * clamped * clamped * clamped
      : 1 - Math.pow(-2 * clamped + 2, 3) / 2;
  }

  function computeShortestDelta(start, end, segmentsOverride) {
    const segments = Number.isFinite(segmentsOverride) ? segmentsOverride : state.segments;
    if (!Number.isFinite(segments) || segments <= 0) {
      return 0;
    }
    let delta = end - start;
    const half = segments / 2;
    if (delta > half) {
      delta -= segments;
    } else if (delta < -half) {
      delta += segments;
    }
    return delta;
  }

  function generateRotationLinks(count) {
    const length = clampInteger(count, 3, 12, 3);
    if (length <= 1) {
      return [0];
    }
    const indices = new Array(length);
    for (let i = 0; i < length; i += 1) {
      indices[i] = i;
    }
    for (let i = length - 1; i > 0; i -= 1) {
      const j = randomInt(0, i);
      const temp = indices[i];
      indices[i] = indices[j];
      indices[j] = temp;
    }
    const links = new Array(length);
    for (let i = 0; i < length; i += 1) {
      const current = indices[i];
      const next = indices[(i + 1) % length];
      links[current] = next;
    }
    return links;
  }

  function handleCanvasPointer(event) {
    if (!elements.canvas) {
      return;
    }
    event.preventDefault();
    elements.canvas.focus();
    const position = getCanvasEventPosition(event);
    if (!position) {
      return;
    }
    const ringIndex = findRingIndexForDistance(position.distance);
    if (!Number.isFinite(ringIndex)) {
      return;
    }
    const pointerType = getPointerInputType(event);
    if (pointerType === 'mouse') {
      const direction = event.shiftKey ? -1 : 1;
      applyUserRotation(ringIndex, direction);
      return;
    }
    startTouchGesture(event, ringIndex, position);
  }

  function handleCanvasPointerMove(event) {
    if (!pointerGesture.active || event.pointerId !== pointerGesture.pointerId) {
      return;
    }
    if (getPointerInputType(event) === 'mouse') {
      return;
    }
    event.preventDefault();
    const position = getCanvasEventPosition(event);
    if (!position) {
      return;
    }
    const angle = Math.atan2(position.y, position.x);
    pointerGesture.lastAngle = angle;
    const delta = normalizeAngleDelta(angle - pointerGesture.startAngle);
    if (Math.abs(delta) < getTouchRotationThreshold()) {
      if (!pointerGesture.moved && Math.abs(delta) > 0.01) {
        pointerGesture.moved = true;
      }
      return;
    }
    pointerGesture.moved = true;
    if (state.animation && state.animation.active) {
      return;
    }
    const direction = delta > 0 ? 1 : -1;
    const rotated = applyUserRotation(pointerGesture.ringIndex, direction);
    if (rotated) {
      pointerGesture.startAngle = angle;
    }
  }

  function handleCanvasPointerEnd(event) {
    if (!pointerGesture.active || event.pointerId !== pointerGesture.pointerId) {
      return;
    }
    if (getPointerInputType(event) === 'mouse') {
      resetPointerGestureState();
      return;
    }
    event.preventDefault();
    if (
      elements.canvas &&
      typeof elements.canvas.releasePointerCapture === 'function' &&
      typeof elements.canvas.hasPointerCapture === 'function' &&
      elements.canvas.hasPointerCapture(event.pointerId)
    ) {
      elements.canvas.releasePointerCapture(event.pointerId);
    }
    const ringIndex = pointerGesture.ringIndex;
    const position = getCanvasEventPosition(event);
    const endAngle = position ? Math.atan2(position.y, position.x) : pointerGesture.lastAngle;
    const delta = normalizeAngleDelta(endAngle - pointerGesture.startAngle);
    const threshold = getTouchRotationThreshold();
    let direction = 0;
    if (Math.abs(delta) >= threshold) {
      direction = delta > 0 ? 1 : -1;
    }
    resetPointerGestureState();
    if (direction !== 0 && Number.isFinite(ringIndex)) {
      applyUserRotation(ringIndex, direction);
    }
  }

  function getCanvasEventPosition(event) {
    if (!elements.canvas) {
      return null;
    }
    const rect = elements.canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) {
      return null;
    }
    const scaleX = elements.canvas.width / rect.width;
    const scaleY = elements.canvas.height / rect.height;
    const x = (event.clientX - rect.left) * scaleX - elements.canvas.width / 2;
    const y = (event.clientY - rect.top) * scaleY - elements.canvas.height / 2;
    const distance = Math.sqrt(x * x + y * y);
    return { x, y, distance };
  }

  function startTouchGesture(event, ringIndex, position) {
    resetPointerGestureState();
    pointerGesture.active = true;
    pointerGesture.pointerId = event.pointerId;
    pointerGesture.ringIndex = ringIndex;
    const angle = Math.atan2(position.y, position.x);
    pointerGesture.startAngle = angle;
    pointerGesture.lastAngle = angle;
    pointerGesture.moved = false;
    if (elements.canvas && typeof elements.canvas.setPointerCapture === 'function') {
      elements.canvas.setPointerCapture(event.pointerId);
    }
  }

  function findRingIndexForDistance(distance) {
    if (!Array.isArray(state.ringMetrics)) {
      return null;
    }
    for (let i = 0; i < state.ringMetrics.length; i += 1) {
      const metrics = state.ringMetrics[i];
      if (!metrics) {
        continue;
      }
      if (distance <= metrics.outer && distance >= metrics.inner) {
        return i;
      }
    }
    return null;
  }

  function handleCanvasKeyDown(event) {
    if (!event || typeof event.key !== 'string') {
      return;
    }
    const digit = Number.parseInt(event.key, 10);
    if (!Number.isFinite(digit) || digit < 1) {
      return;
    }
    const ringIndex = digit - 1;
    if (ringIndex >= state.ringCount) {
      return;
    }
    event.preventDefault();
    const direction = event.shiftKey ? -1 : 1;
    applyUserRotation(ringIndex, direction);
  }

  function getNextDifficulty(current) {
    const index = DIFFICULTY_ORDER.indexOf(current);
    if (index < 0) {
      return DIFFICULTY_ORDER[0];
    }
    return DIFFICULTY_ORDER[(index + 1) % DIFFICULTY_ORDER.length];
  }

  function setDifficulty(difficulty, options) {
    const key = DIFFICULTY_ORDER.includes(difficulty) ? difficulty : 'easy';
    state.difficulty = key;
    if (options && options.updateSelect && elements.difficultySelect) {
      elements.difficultySelect.value = key;
    }
    if (options && options.startPuzzle) {
      startNewPuzzle({ preserveDifficulty: true });
    }
  }

  function resetPointerGestureState() {
    pointerGesture.active = false;
    pointerGesture.pointerId = null;
    pointerGesture.ringIndex = null;
    pointerGesture.startAngle = 0;
    pointerGesture.lastAngle = 0;
    pointerGesture.moved = false;
  }

  function getTouchRotationThreshold() {
    const segments = Math.max(1, state.segments);
    const segmentAngle = (Math.PI * 2) / segments;
    return Math.max(TOUCH_GESTURE_MIN_ANGLE, segmentAngle * 0.45);
  }

  function normalizeAngleDelta(delta) {
    let angle = delta;
    const fullTurn = Math.PI * 2;
    while (angle <= -Math.PI) {
      angle += fullTurn;
    }
    while (angle > Math.PI) {
      angle -= fullTurn;
    }
    return angle;
  }

  function normalizeColorString(color) {
    if (typeof color !== 'string') {
      return '';
    }
    return color.trim().toLowerCase();
  }

  function getPointerInputType(event) {
    if (!event || typeof event.pointerType !== 'string' || !event.pointerType) {
      return 'mouse';
    }
    return event.pointerType.toLowerCase();
  }

  function createPointerGestureState() {
    return {
      active: false,
      pointerId: null,
      ringIndex: null,
      startAngle: 0,
      lastAngle: 0,
      moved: false
    };
  }

  function handleDifficultyChange(event) {
    const value = event && event.target ? event.target.value : null;
    if (!value) {
      return;
    }
    setDifficulty(value, { updateSelect: false, startPuzzle: true });
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      flushAutosave();
    }
  });
})();
