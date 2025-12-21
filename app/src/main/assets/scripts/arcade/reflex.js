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

  const GAME_ID = 'reflex';
  const MAX_ACTIVE_TARGETS = 5;
  const INITIAL_INTERVAL_MS = 1000;
  const INTERVAL_DECREASE_MS = 10;
  const EARLY_SPAWN_COUNT = 5;
  const MIN_INTERVAL_MS = 220;
  const TARGET_SIZE = 68;
  const TOUCH_MODES = {
    easy: 'easy',
    hard: 'hard'
  };

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
    headerObserver: null
  };

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
      : {};
    const easy = normalizeScore(bestScores.easy);
    const hard = normalizeScore(bestScores.hard);
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

    return empty;
  }

  function computeNextInterval() {
    if (state.spawnCount < EARLY_SPAWN_COUNT) {
      return INITIAL_INTERVAL_MS;
    }
    const extraSpawns = state.spawnCount - EARLY_SPAWN_COUNT + 1;
    const reduced = INITIAL_INTERVAL_MS - INTERVAL_DECREASE_MS * extraSpawns;
    return Math.max(MIN_INTERVAL_MS, reduced);
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

    const boundsWidth = elements.playfield.clientWidth;
    const boundsHeight = elements.playfield.clientHeight;
    const maxX = Math.max(0, boundsWidth - TARGET_SIZE);
    const maxY = Math.max(0, boundsHeight - TARGET_SIZE);
    const x = Math.random() * maxX;
    const y = Math.random() * maxY;
    target.style.left = `${x}px`;
    target.style.top = `${y}px`;
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

    if (elements.layer.children.length > MAX_ACTIVE_TARGETS) {
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

  function attachLayoutListeners() {
    if (layoutState.listenersAttached) {
      return;
    }
    layoutState.listenersAttached = true;
    updateTopOffset();
    window.addEventListener('resize', updateTopOffset, { passive: true });
    const header = document.querySelector('.app-header');
    if (header && typeof MutationObserver !== 'undefined') {
      layoutState.headerObserver = new MutationObserver(updateTopOffset);
      layoutState.headerObserver.observe(header, { attributes: true, attributeFilter: ['data-collapsed'] });
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
  }

  function onEnter() {
    state.bestScores = readStoredBestScores();
    updateTopOffset();
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
