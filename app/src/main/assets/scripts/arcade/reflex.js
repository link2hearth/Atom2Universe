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
    bestScoreValue: document.getElementById('reflexBestScoreValue'),
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
    bestScore: 0,
    spawnCount: 0,
    timerId: null,
    touchMode: TOUCH_MODES.easy
  };

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
    if (elements.bestScoreValue) {
      elements.bestScoreValue.textContent = state.bestScore > 0 ? Math.floor(state.bestScore) : '—';
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

  function persistBestScore(bestScore) {
    const globalState = getGlobalGameState();
    if (globalState) {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
        globalState.arcadeProgress.entries = {};
      }
      globalState.arcadeProgress.entries[GAME_ID] = {
        state: { bestScore },
        updatedAt: Date.now()
      };
    }

    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.set === 'function') {
      try {
        window.ArcadeAutosave.set(GAME_ID, { bestScore });
      } catch (error) {
        // ignore autosave errors
      }
    }

    requestSave();
    window.dispatchEvent(new Event('arcadeAutosaveSync'));
  }

  function recordBestScore(nextScore) {
    if (!Number.isFinite(nextScore) || nextScore <= state.bestScore) {
      return;
    }
    state.bestScore = Math.floor(nextScore);
    updateDisplays();
    persistBestScore(state.bestScore);
    setStatus('index.sections.reflex.status.newRecord', 'Nouveau record !', { score: state.bestScore });
  }

  function readStoredBestScore() {
    const globalState = getGlobalGameState();
    const entries = globalState && globalState.arcadeProgress && typeof globalState.arcadeProgress === 'object'
      ? (globalState.arcadeProgress.entries && typeof globalState.arcadeProgress.entries === 'object'
          ? globalState.arcadeProgress.entries
          : globalState.arcadeProgress)
      : null;

    const entry = entries && entries[GAME_ID];
    if (entry && typeof entry === 'object') {
      const savedState = entry.state && typeof entry.state === 'object' ? entry.state : entry;
      const savedScore = Number(savedState.bestScore ?? savedState.score);
      if (Number.isFinite(savedScore) && savedScore > 0) {
        return Math.floor(savedScore);
      }
    }

    if (window.ArcadeAutosave && typeof window.ArcadeAutosave.get === 'function') {
      try {
        const autosave = window.ArcadeAutosave.get(GAME_ID);
        const autosaveScore = Number(autosave?.bestScore ?? autosave?.score);
        if (Number.isFinite(autosaveScore) && autosaveScore > 0) {
          persistBestScore(Math.floor(autosaveScore));
          return Math.floor(autosaveScore);
        }
      } catch (error) {
        return 0;
      }
    }

    return 0;
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
    state.bestScore = readStoredBestScore();
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
    state.bestScore = readStoredBestScore();
    updateDisplays();
    setTouchMode(state.touchMode);
    attachEvents();
  }

  init();

  window.reflexArcade = {
    onEnter,
    onLeave
  };
})();
