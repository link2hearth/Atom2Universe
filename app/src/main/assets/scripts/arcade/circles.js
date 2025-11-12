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
    seedValue: document.getElementById('circlesSeedValue'),
    movesValue: document.getElementById('circlesMovesValue'),
    rewardValue: document.getElementById('circlesRewardValue'),
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
  const AUTOSAVE_VERSION = 1;
  const AUTOSAVE_DEBOUNCE_MS = 200;
  const DIFFICULTY_ORDER = Object.freeze(['easy', 'medium', 'hard']);

  const DEFAULT_CONFIG = Object.freeze({
    segments: 6,
    colors: Object.freeze(['#e74c3c', '#3498db', '#f1c40f', '#2ecc71', '#9b59b6', '#e67e22']),
    colorOffsets: Object.freeze([0, 3, 2, 5, 1, 4]),
    autoRotateOuterRing: true,
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
    ringMetrics: [],
    seed: '',
    moves: 0,
    solved: false,
    rewardClaimed: false,
    autosaveTimer: null,
    autosaveSuppressed: false,
    resizeObserver: null
  };

  let languageListenerAttached = false;

  initialize();

  function initialize() {
    state.rings = createRingDefinitions(state.ringCount);
    state.rotations = new Array(state.ringCount).fill(0);
    state.initialRotations = state.rotations.slice();

    attachEventListeners();
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
    if (elements.canvas) {
      elements.canvas.addEventListener('pointerdown', handleCanvasPointer, { passive: false });
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
      ringCount: clampInteger(state.ringCount, 2, 12, 3),
      seed: typeof state.seed === 'string' ? state.seed : '',
      moves: clampInteger(state.moves, 0, 9999, 0),
      rotations: state.rotations.slice(),
      initialRotations: state.initialRotations.slice(),
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

    const ringCount = clampInteger(payload.ringCount, 2, 12, state.ringCount);
    applyRingCount(ringCount);

    state.seed = typeof payload.seed === 'string' ? payload.seed : '';
    state.moves = clampInteger(payload.moves, 0, 9999, 0);

    state.rotations = sanitizeRotationArray(payload.rotations, state.ringCount);
    state.initialRotations = sanitizeRotationArray(payload.initialRotations, state.ringCount, state.rotations);
    state.solved = payload.solved === true && checkSolved(state.rotations);
    state.rewardClaimed = payload.rewardClaimed === true;

    updateStats();
    updateWinMessage();
    resizeCanvas();

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
      2,
      ...DIFFICULTY_ORDER.map(key => Math.max(...normalizedDifficulties[key].ringCounts))
    );

    const colorOffsets = buildColorOffsets(raw && raw.colorOffsets, base.colorOffsets, maxRingCount, segments);
    const autoRotateOuterRing = raw && typeof raw.autoRotateOuterRing === 'boolean'
      ? raw.autoRotateOuterRing
      : base.autoRotateOuterRing;
    const maxShuffleAttempts = clampInteger(raw && raw.maxShuffleAttempts, 4, 200, base.maxShuffleAttempts);
    const seedLength = clampInteger(raw && raw.seedLength, 4, 32, base.seedLength);

    return {
      segments,
      colors,
      colorOffsets,
      autoRotateOuterRing,
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
      .map(value => clampInteger(value, 2, 12, null))
      .filter(value => Number.isFinite(value));

    if (!ringCounts.length) {
      ringCounts.push(clampInteger(fallbackRingCounts[0], 2, 12, 3));
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
    const count = clampInteger(ringCount, 2, 12, 3);
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
    const count = clampInteger(ringCount, 2, 12, state.ringCount || 3);
    if (count === state.ringCount && Array.isArray(state.rotations) && state.rotations.length === count) {
      state.rings = createRingDefinitions(count);
      computeRingMetrics();
      return;
    }
    state.ringCount = count;
    state.rings = createRingDefinitions(count);
    state.rotations = new Array(count).fill(0);
    state.initialRotations = state.rotations.slice();
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
      return clampInteger(options[0], 2, 12, state.ringCount);
    }
    const index = randomInt(0, options.length - 1);
    return clampInteger(options[index], 2, 12, options[0]);
  }

  function startNewPuzzle(options) {
    const preserveDifficulty = options && options.preserveDifficulty === true;
    const difficultyKey = preserveDifficulty ? state.difficulty : 'easy';
    const ringCount = pickRingCountForDifficulty(difficultyKey);
    const puzzle = generatePuzzleDefinition(ringCount, difficultyKey);

    withAutosaveSuppressed(() => {
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
    state.rotations = sanitizeRotationArray(definition.rotations, state.ringCount);
    state.initialRotations = state.rotations.slice();
    state.seed = typeof definition.seed === 'string' ? definition.seed : generateSeed(state.settings.seedLength);
    state.moves = 0;
    state.solved = checkSolved(state.rotations);
    state.rewardClaimed = false;
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
      state.rotations = sanitizeRotationArray(state.initialRotations, state.ringCount);
      state.moves = 0;
      state.solved = checkSolved(state.rotations);
      state.rewardClaimed = false;
      hideWinOverlay();
      updateControlsState();
      updateStats();
      draw();
      scheduleAutosave();
    });
  }

  function generatePuzzleDefinition(ringCount, difficultyKey) {
    const count = clampInteger(ringCount, 2, 12, state.ringCount || 3);
    const settings = getDifficultySettings(difficultyKey);
    const minMoves = clampInteger(settings.shuffleMoves.min, 1, 400, 6);
    const maxMoves = clampInteger(settings.shuffleMoves.max, minMoves, 500, Math.max(minMoves, 12));
    const attempts = clampInteger(state.settings.maxShuffleAttempts, 4, 200, 32);

    for (let attempt = 0; attempt < attempts; attempt += 1) {
      const rotations = new Array(count).fill(0);
      const moveCount = randomInt(minMoves, maxMoves);
      for (let move = 0; move < moveCount; move += 1) {
        const index = randomInt(0, count - 1);
        const direction = Math.random() < 0.5 ? 1 : -1;
        applyRotationToArray(rotations, index, direction, state.settings.autoRotateOuterRing, state.segments);
      }
      if (!checkSolved(rotations)) {
        return {
          ringCount: count,
          rotations: rotations.map(value => normalizeRotationValue(value)),
          seed: generateSeed(state.settings.seedLength)
        };
      }
    }

    const fallbackRotations = new Array(count).fill(0);
    fallbackRotations[count - 1] = 1;
    if (state.settings.autoRotateOuterRing && count > 1) {
      fallbackRotations[count - 2] = (fallbackRotations[count - 2] || 0) + 1;
    }
    return {
      ringCount: count,
      rotations: fallbackRotations.map(value => normalizeRotationValue(value)),
      seed: generateSeed(state.settings.seedLength)
    };
  }

  function applyRotationToArray(rotations, index, direction, autoRotateOuterRing, segmentsOverride) {
    if (!Array.isArray(rotations)) {
      return;
    }
    const ringIndex = clampInteger(index, 0, rotations.length - 1, null);
    if (!Number.isFinite(ringIndex)) {
      return;
    }
    const dir = direction > 0 ? 1 : -1;
    rotations[ringIndex] = normalizeRotationValue(rotations[ringIndex] + dir, segmentsOverride);
    if (autoRotateOuterRing && ringIndex > 0) {
      rotations[ringIndex - 1] = normalizeRotationValue(rotations[ringIndex - 1] + dir, segmentsOverride);
    }
  }
  function applyUserRotation(index, direction) {
    if (state.solved) {
      return;
    }
    const ringIndex = clampInteger(index, 0, state.rotations.length - 1, null);
    if (!Number.isFinite(ringIndex)) {
      return;
    }
    applyRotationToArray(state.rotations, ringIndex, direction, state.settings.autoRotateOuterRing, state.segments);
    state.moves += 1;
    updateControlsState();
    updateStats();
    draw();
    scheduleAutosave();
    if (checkSolved(state.rotations)) {
      handleSolved();
    }
  }

  function checkSolved(rotations) {
    if (!Array.isArray(rotations)) {
      return false;
    }
    return rotations.every(value => normalizeRotationValue(value) === 0);
  }

  function handleSolved() {
    if (state.solved) {
      return;
    }
    state.solved = true;
    updateWinMessage();
    showWinOverlay();
    awardCompletionTickets();
    flushAutosave();
  }

  function getCurrentRewardTickets(ringCountOverride) {
    const ringCount = clampInteger(
      ringCountOverride != null ? ringCountOverride : state.ringCount,
      2,
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
    const size = Math.max(100, Math.min(rect.width, rect.height));
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
      const rotation = normalizeRotationValue(state.rotations[index] || 0) * step;
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
    }
    ctx2d.restore();
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
    const direction = event.shiftKey ? -1 : 1;
    applyUserRotation(ringIndex, direction);
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
