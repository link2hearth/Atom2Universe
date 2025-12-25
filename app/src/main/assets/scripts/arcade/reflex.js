(() => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-reflex-root]');
  if (!root) {
    return;
  }

  const elements = {
    playfield: document.getElementById('reflexPlayfield'),
    layer: document.getElementById('reflexTargetLayer'),
    startButton: document.getElementById('reflexStartButton'),
    scoreValue: document.getElementById('reflexScoreValue'),
    bestScoreEasyValue: document.getElementById('reflexBestScoreEasyValue'),
    bestScoreHardValue: document.getElementById('reflexBestScoreHardValue'),
    status: document.getElementById('reflexStatus'),
    hint: document.getElementById('reflexHint'),
    modeOptions: Array.from(document.querySelectorAll('[data-reflex-mode-option]'))
  };

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const RAW_CONFIG =
    GLOBAL_CONFIG
    && GLOBAL_CONFIG.arcade
    && GLOBAL_CONFIG.arcade.reflex
      ? GLOBAL_CONFIG.arcade.reflex
      : {};

  const GAME_ID = 'reflex';
  const LOCAL_BEST_SCORES_KEY = 'atom2univers.arcade.reflex.bestScores.v1';
  const TOUCH_MODES = {
    easy: 'easy',
    hard: 'hard'
  };

  const DEFAULT_CONFIG = Object.freeze({
    target: {
      sizePx: 68,
      easyHitScale: 2
    },
    difficulty: {
      easy: {
        maxActiveTargets: 6,
        initialIntervalMs: 1150,
        intervalDecreaseMs: 8,
        earlySpawnCount: 6,
        minIntervalMs: 260
      },
      hard: {
        maxActiveTargets: 5,
        initialIntervalMs: 1000,
        intervalDecreaseMs: 10,
        earlySpawnCount: 5,
        minIntervalMs: 220
      }
    }
  });

  const state = {
    running: false,
    score: 0,
    bestScores: {
      easy: 0,
      hard: 0
    },
    spawnCount: 0,
    timerId: null,
    touchMode: TOUCH_MODES.easy
  };

  const layoutState = {
    listenersAttached: false,
    headerObserver: null,
    resizeObserver: null,
    rafId: null
  };

  function normalizeConfig(raw) {
    const config = {
      target: { ...DEFAULT_CONFIG.target },
      difficulty: {
        easy: { ...DEFAULT_CONFIG.difficulty.easy },
        hard: { ...DEFAULT_CONFIG.difficulty.hard }
      }
    };

    if (raw && typeof raw === 'object') {
      if (raw.target && typeof raw.target === 'object') {
        const size = Number(raw.target.sizePx);
        if (Number.isFinite(size) && size > 0) {
          config.target.sizePx = Math.floor(size);
        }
        const scale = Number(raw.target.easyHitScale);
        if (Number.isFinite(scale) && scale >= 1) {
          config.target.easyHitScale = scale;
        }
      }
      if (raw.difficulty && typeof raw.difficulty === 'object') {
        ['easy', 'hard'].forEach((mode) => {
          const source = raw.difficulty[mode];
          if (!source || typeof source !== 'object') {
            return;
          }
          const target = config.difficulty[mode];
          const maxActiveTargets = Number(source.maxActiveTargets);
          if (Number.isFinite(maxActiveTargets) && maxActiveTargets > 0) {
            target.maxActiveTargets = Math.floor(maxActiveTargets);
          }
          const initialIntervalMs = Number(source.initialIntervalMs);
          if (Number.isFinite(initialIntervalMs) && initialIntervalMs > 0) {
            target.initialIntervalMs = Math.floor(initialIntervalMs);
          }
          const intervalDecreaseMs = Number(source.intervalDecreaseMs);
          if (Number.isFinite(intervalDecreaseMs) && intervalDecreaseMs > 0) {
            target.intervalDecreaseMs = Math.floor(intervalDecreaseMs);
          }
          const earlySpawnCount = Number(source.earlySpawnCount);
          if (Number.isFinite(earlySpawnCount) && earlySpawnCount >= 0) {
            target.earlySpawnCount = Math.floor(earlySpawnCount);
          }
          const minIntervalMs = Number(source.minIntervalMs);
          if (Number.isFinite(minIntervalMs) && minIntervalMs > 0) {
            target.minIntervalMs = Math.floor(minIntervalMs);
          }
        });
      }
    }

    return config;
  }

  const CONFIG = normalizeConfig(RAW_CONFIG);

  function getModeSettings() {
    return state.touchMode === TOUCH_MODES.hard
      ? CONFIG.difficulty.hard
      : CONFIG.difficulty.easy;
  }

  function normalizeScore(value) {
    const number = Number(value);
    if (!Number.isFinite(number) || number <= 0) {
      return 0;
    }
    return Math.floor(number);
  }

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window.t === 'function') {
      try {
        const value = window.t(key, params);
        if (value != null) {
          return value;
        }
      } catch (error) {
        return fallback;
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

  function readLocalBestScores() {
    if (typeof window === 'undefined' || !window.localStorage) {
      return null;
    }
    try {
      const raw = window.localStorage.getItem(LOCAL_BEST_SCORES_KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw);
      return normalizeStoredBestScores(parsed);
    } catch (error) {
      return null;
    }
  }

  function persistLocalBestScores(bestScores) {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    try {
      window.localStorage.setItem(LOCAL_BEST_SCORES_KEY, JSON.stringify({ bestScores }));
    } catch (error) {
      // ignore local storage errors
    }
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

  function updateDisplays() {
    if (elements.scoreValue) {
      elements.scoreValue.textContent = Math.floor(state.score);
    }
    if (elements.bestScoreEasyValue) {
      elements.bestScoreEasyValue.textContent = state.bestScores.easy > 0
        ? Math.floor(state.bestScores.easy)
        : '—';
    }
    if (elements.bestScoreHardValue) {
      elements.bestScoreHardValue.textContent = state.bestScores.hard > 0
        ? Math.floor(state.bestScores.hard)
        : '—';
    }
  }

  function setStatus(messageKey, fallback, params) {
    const text = translate(messageKey, fallback, params);
    if (elements.hint) {
      elements.hint.textContent = text;
    }
    if (elements.status) {
      elements.status.textContent = text;
    }
  }

  function clearTargets() {
    if (!elements.layer) {
      return;
    }
    while (elements.layer.firstChild) {
      elements.layer.removeChild(elements.layer.firstChild);
    }
  }

  function persistBestScores(bestScores) {
    const normalized = {
      easy: normalizeScore(bestScores?.easy),
      hard: normalizeScore(bestScores?.hard)
    };
    const bestScore = Math.max(normalized.easy, normalized.hard);
    const globalState = getGlobalGameState();
    const canPersistToGlobal = globalState && window.appStartCompleted === true;

    if (canPersistToGlobal) {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
        globalState.arcadeProgress.entries = {};
      }
      globalState.arcadeProgress.entries[GAME_ID] = {
        state: {
          bestScores: normalized,
          bestScore
        },
        updatedAt: Date.now()
      };
    }

    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.set === 'function') {
      try {
        window.ArcadeAutosave.set(GAME_ID, {
          bestScores: normalized,
          bestScore
        });
      } catch (error) {
        // ignore autosave errors
      }
    }

    if (canPersistToGlobal) {
      requestSave();
    }
    persistLocalBestScores(normalized);
    window.dispatchEvent(new Event('arcadeAutosaveSync'));
  }

  function recordBestScore(nextScore) {
    const normalizedScore = normalizeScore(nextScore);
    if (!normalizedScore) {
      return;
    }
    const currentMode = state.touchMode === TOUCH_MODES.hard ? TOUCH_MODES.hard : TOUCH_MODES.easy;
    const currentBest = state.bestScores[currentMode] || 0;
    if (normalizedScore <= currentBest) {
      return;
    }
    state.bestScores[currentMode] = normalizedScore;
    updateDisplays();
    persistBestScores(state.bestScores);
    setStatus('index.sections.reflex.status.newRecord', 'Nouveau record !', { score: normalizedScore });
  }

  function normalizeStoredBestScores(record) {
    if (!record || typeof record !== 'object') {
      return { easy: 0, hard: 0 };
    }
    const bestScores = record.bestScores && typeof record.bestScores === 'object'
      ? record.bestScores
      : record;
    const easy = normalizeScore(bestScores.easy ?? record.easy);
    const hard = normalizeScore(bestScores.hard ?? record.hard);
    const legacy = normalizeScore(record.bestScore ?? record.score);
    const merged = {
      easy,
      hard: Math.max(hard, legacy)
    };
    return merged;
  }

  function readStoredBestScores() {
    const empty = { easy: 0, hard: 0 };
    const globalState = getGlobalGameState();
    const entries = globalState && globalState.arcadeProgress && typeof globalState.arcadeProgress === 'object'
      ? (globalState.arcadeProgress.entries && typeof globalState.arcadeProgress.entries === 'object'
          ? globalState.arcadeProgress.entries
          : globalState.arcadeProgress)
      : null;

    const entry = entries && entries[GAME_ID];
    if (entry && typeof entry === 'object') {
      const savedState = entry.state && typeof entry.state === 'object' ? entry.state : entry;
      const normalized = normalizeStoredBestScores(savedState);
      if (normalized.easy > 0 || normalized.hard > 0) {
        return normalized;
      }
    }

    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.get === 'function') {
      try {
        const autosave = window.ArcadeAutosave.get(GAME_ID);
        const normalized = normalizeStoredBestScores(autosave);
        if (normalized.easy > 0 || normalized.hard > 0) {
          persistBestScores(normalized);
          return normalized;
        }
      } catch (error) {
        return empty;
      }
    }

    const local = readLocalBestScores();
    if (local && (local.easy > 0 || local.hard > 0)) {
      persistBestScores(local);
      return local;
    }

    return empty;
  }

  function computeNextInterval() {
    const settings = getModeSettings();
    if (state.spawnCount < settings.earlySpawnCount) {
      return settings.initialIntervalMs;
    }
    const extraSpawns = state.spawnCount - settings.earlySpawnCount + 1;
    const reduced = settings.initialIntervalMs - settings.intervalDecreaseMs * extraSpawns;
    return Math.max(settings.minIntervalMs, reduced);
  }

  function scheduleNextSpawn() {
    if (!state.running) {
      return;
    }
    const delay = computeNextInterval();
    clearTimeout(state.timerId);
    state.timerId = window.setTimeout(spawnTarget, delay);
  }

  function gameOver(reason = 'overload') {
    state.running = false;
    clearTimeout(state.timerId);
    state.timerId = null;
    state.spawnCount = 0;
    clearTargets();
    recordBestScore(state.score);

    const messageKey = reason === 'overload'
      ? 'index.sections.reflex.status.overload'
      : 'index.sections.reflex.status.gameOver';
    const fallback = reason === 'overload'
      ? 'Game over : trop de cercles à l’écran.'
      : 'Partie terminée.';
    setStatus(messageKey, fallback, { score: Math.floor(state.score) });
  }

  function handleTargetClick(target) {
    if (!state.running || !target || !elements.layer) {
      return;
    }

    if (target.dataset.resolved === '1') {
      return;
    }

    target.dataset.resolved = '1';
    const createdAt = Number(target.dataset.createdAt || performance.now());
    const delayMs = Math.max(0, performance.now() - createdAt);
    const points = Math.max(0, Math.round(1000 - delayMs * 0.5));
    state.score += points;
    if (target.parentElement === elements.layer) {
      elements.layer.removeChild(target);
    }
    updateDisplays();
    recordBestScore(state.score);
    setStatus('index.sections.reflex.status.hit', `Touché ! +${points}`, {
      points: points,
      score: Math.floor(state.score)
    });
  }

  function spawnTarget() {
    if (!state.running || !elements.layer || !elements.playfield) {
      return;
    }

    state.spawnCount += 1;
    const target = document.createElement('button');
    target.type = 'button';
    target.className = 'reflex__target';
    target.setAttribute('aria-label', translate('index.sections.reflex.targetAria', 'Cible Reflex')); 

    const size = CONFIG.target.sizePx;
    const hitScale = state.touchMode === TOUCH_MODES.easy ? CONFIG.target.easyHitScale : 1;
    const hitSize = Math.round(size * hitScale);
    const boundsWidth = elements.playfield.clientWidth;
    const boundsHeight = elements.playfield.clientHeight;
    const maxX = Math.max(0, boundsWidth - hitSize);
    const maxY = Math.max(0, boundsHeight - hitSize);
    const x = Math.random() * maxX;
    const y = Math.random() * maxY;
    target.style.left = `${x}px`;
    target.style.top = `${y}px`;
    target.style.setProperty('--reflex-target-size', `${size}px`);
    target.style.setProperty('--reflex-target-hit-size', `${hitSize}px`);
    target.dataset.createdAt = performance.now().toString();

    target.addEventListener('click', () => handleTargetClick(target));
    target.addEventListener(
      'touchstart',
      (event) => {
        if (state.touchMode !== TOUCH_MODES.easy) {
          return;
        }
        event.preventDefault();
        handleTargetClick(target);
      },
      { passive: false }
    );
    target.addEventListener('pointerdown', (event) => {
      if (state.touchMode !== TOUCH_MODES.easy) {
        return;
      }
      if (event.pointerType === 'mouse') {
        return;
      }
      handleTargetClick(target);
    });
    elements.layer.appendChild(target);

    if (elements.layer.children.length > getModeSettings().maxActiveTargets) {
      gameOver('overload');
      return;
    }

    scheduleNextSpawn();
  }

  function resetRun() {
    clearTimeout(state.timerId);
    state.timerId = null;
    state.score = 0;
    state.spawnCount = 0;
    state.running = true;
    clearTargets();
    updateDisplays();
    setStatus('index.sections.reflex.status.ready', 'Prêt ? Cliquez sur les cercles dès qu’ils apparaissent.');
    spawnTarget();
  }

  function setTouchMode(mode) {
    if (!Object.values(TOUCH_MODES).includes(mode)) {
      return;
    }
    state.touchMode = mode;
    elements.modeOptions.forEach((button) => {
      const isActive = button.dataset.reflexModeOption === mode;
      button.classList.toggle('reflex__mode-option--active', isActive);
      button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    });
  }

  function measureHeaderHeight() {
    const header = document.querySelector('.app-header');
    if (!header || typeof header.getBoundingClientRect !== 'function') {
      return 32;
    }
    const rect = header.getBoundingClientRect();
    const measured = Number.isFinite(rect.height) ? rect.height : 0;
    return Math.max(32, Math.round(measured));
  }

  function updateTopOffset() {
    const offset = measureHeaderHeight();
    if (root && typeof root.style?.setProperty === 'function') {
      root.style.setProperty('--reflex-banner-offset', `${offset}px`);
    }
    const page = root?.closest?.('.page');
    if (page && typeof page.style?.setProperty === 'function') {
      page.style.setProperty('--reflex-banner-offset', `${offset}px`);
    }
  }

  function updatePlayfieldHeight() {
    if (!elements.playfield) {
      return;
    }
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    const playfieldRect = elements.playfield.getBoundingClientRect();
    const rootStyle = root && typeof window.getComputedStyle === 'function'
      ? window.getComputedStyle(root)
      : null;
    const paddingBottom = rootStyle ? Number.parseFloat(rootStyle.paddingBottom) : 0;
    const safePadding = Number.isFinite(paddingBottom) ? paddingBottom : 0;
    const available = Math.max(0, Math.floor(viewportHeight - playfieldRect.top - safePadding));
    if (available > 0) {
      elements.playfield.style.minHeight = `${available}px`;
    } else {
      elements.playfield.style.minHeight = '';
    }
  }

  function scheduleLayoutUpdate() {
    if (layoutState.rafId) {
      cancelAnimationFrame(layoutState.rafId);
    }
    layoutState.rafId = requestAnimationFrame(() => {
      layoutState.rafId = null;
      updateTopOffset();
      updatePlayfieldHeight();
    });
  }

  function attachLayoutListeners() {
    if (layoutState.listenersAttached) {
      return;
    }
    layoutState.listenersAttached = true;
    scheduleLayoutUpdate();
    window.addEventListener('resize', scheduleLayoutUpdate, { passive: true });
    const header = document.querySelector('.app-header');
    if (header && typeof MutationObserver !== 'undefined') {
      layoutState.headerObserver = new MutationObserver(scheduleLayoutUpdate);
      layoutState.headerObserver.observe(header, { attributes: true, attributeFilter: ['data-collapsed'] });
    }
    if (typeof ResizeObserver !== 'undefined') {
      layoutState.resizeObserver = new ResizeObserver(scheduleLayoutUpdate);
      layoutState.resizeObserver.observe(root);
      if (elements.playfield) {
        layoutState.resizeObserver.observe(elements.playfield);
      }
      const controls = root.querySelector('.reflex__controls');
      if (controls) {
        layoutState.resizeObserver.observe(controls);
      }
    }
  }

  function attachEvents() {
    if (elements.startButton) {
      elements.startButton.addEventListener('click', () => {
        resetRun();
      });
    }

    if (elements.modeOptions.length > 0) {
      elements.modeOptions.forEach((button) => {
        button.addEventListener('click', () => {
          const mode = button.dataset.reflexModeOption;
          setTouchMode(mode);
        });
      });
    }

    const flushAutosave = () => {
      if (window.ArcadeAutosave && typeof window.ArcadeAutosave.flush === 'function') {
        try {
          window.ArcadeAutosave.flush();
        } catch (error) {
          // ignore flush errors
        }
      }
    };

    window.addEventListener('pagehide', flushAutosave);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        flushAutosave();
      }
    });
  }

  function onEnter() {
    state.bestScores = readStoredBestScores();
    scheduleLayoutUpdate();
    updateDisplays();
    setStatus('index.sections.reflex.status.ready', 'Prêt ? Cliquez sur les cercles dès qu’ils apparaissent.');
  }

  function onLeave() {
    clearTimeout(state.timerId);
    state.timerId = null;
    state.running = false;
    clearTargets();
  }

  function init() {
    state.bestScores = readStoredBestScores();
    updateDisplays();
    setTouchMode(state.touchMode);
    attachLayoutListeners();
    attachEvents();
  }

  init();

  window.reflexArcade = {
    onEnter,
    onLeave
  };
})();
