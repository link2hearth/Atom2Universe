(() => {
  const DEFAULT_SETTINGS = Object.freeze({
    enabled: true,
    prefetchOnIdle: false,
    prefetchDelayMs: 2500
  });

  const rawConfig = typeof globalThis !== 'undefined'
    ? globalThis.GAME_CONFIG?.lazyLoad?.arcade
    : null;

  const resolveBoolean = (value, fallback) => (typeof value === 'boolean' ? value : fallback);
  const resolveDelayMs = (value, fallback) => {
    const numeric = Number(value);
    if (Number.isFinite(numeric) && numeric >= 0) {
      return Math.floor(numeric);
    }
    return fallback;
  };

  const ACTIVE_SETTINGS = Object.freeze({
    enabled: resolveBoolean(rawConfig?.enabled, DEFAULT_SETTINGS.enabled),
    prefetchOnIdle: resolveBoolean(rawConfig?.prefetchOnIdle, DEFAULT_SETTINGS.prefetchOnIdle),
    prefetchDelayMs: resolveDelayMs(rawConfig?.prefetchDelayMs, DEFAULT_SETTINGS.prefetchDelayMs)
  });

  const ARCADE_SCRIPT_MANIFEST = Object.freeze({
    arcade: 'scripts/arcade/particules.js',
    wave: 'scripts/arcade/wave.js',
    balance: 'scripts/arcade/balance.js',
    bigger: 'scripts/arcade/bigger.js',
    math: 'scripts/arcade/math.js',
    sudoku: 'scripts/arcade/sudoku.js',
    minesweeper: 'scripts/arcade/minesweeper.js',
    solitaire: 'scripts/arcade/solitaire.js',
    roulette: 'scripts/arcade/roulette.js',
    pachinko: 'scripts/arcade/pachinko.js',
    dice: 'scripts/arcade/dice.js',
    holdem: 'scripts/arcade/holdem.js',
    blackjack: 'scripts/arcade/blackjack.js',
    echecs: 'scripts/arcade/echecs.js',
    othello: 'scripts/arcade/othello.js',
    p4: 'scripts/arcade/p4.js',
    hex: 'scripts/arcade/hex.js',
    gameOfLife: 'scripts/arcade/game-of-life.js',
    quantum2048: 'scripts/arcade/quantum-2048.js',
    lightsOut: 'scripts/arcade/lights-out.js',
    link: 'scripts/arcade/link.js',
    twins: 'scripts/arcade/twins.js',
    starBridges: 'scripts/arcade/star-bridges.js',
    pipeTap: 'scripts/arcade/pipetap.js',
    colorStack: 'scripts/arcade/color-stack.js',
    circles: 'scripts/arcade/circles.js',
    starsWar: 'scripts/arcade/stars-war.js',
    jumpingCat: 'scripts/arcade/jumping-cat.js',
    reflex: 'scripts/arcade/reflex.js',
    motocross: 'scripts/arcade/motocross.js',
    sokoban: 'scripts/arcade/sokoban.js',
    taquin: 'scripts/arcade/taquin.js',
    escape: 'scripts/arcade/escape.js',
    theLine: 'scripts/arcade/the-line.js'
  });

  const scriptState = new Map();

  const getScriptEntry = pageId => {
    if (!pageId) {
      return null;
    }
    const key = String(pageId);
    const src = ARCADE_SCRIPT_MANIFEST[key];
    if (!src) {
      return null;
    }
    return { id: key, src };
  };

  const markScriptLoaded = (pageId, state) => {
    if (!pageId) {
      return;
    }
    const current = scriptState.get(pageId) || {};
    scriptState.set(pageId, {
      ...current,
      loaded: true,
      promise: current.promise || Promise.resolve(true),
      error: state?.error || null
    });
  };

  const findExistingScriptTag = entry => {
    if (!entry || typeof document === 'undefined') {
      return null;
    }
    return document.querySelector(`script[data-arcade-script="${entry.id}"]`);
  };

  const loadArcadeScript = entry => {
    if (!entry) {
      return Promise.resolve(false);
    }
    const existingState = scriptState.get(entry.id);
    if (existingState?.loaded) {
      return Promise.resolve(true);
    }
    if (existingState?.promise) {
      return existingState.promise;
    }
    if (typeof document === 'undefined' || typeof document.createElement !== 'function') {
      return Promise.resolve(false);
    }
    const existingTag = findExistingScriptTag(entry);
    if (existingTag) {
      if (existingTag.dataset.loaded === 'true') {
        markScriptLoaded(entry.id);
        return Promise.resolve(true);
      }
      const promise = new Promise(resolve => {
        existingTag.addEventListener('load', () => {
          existingTag.dataset.loaded = 'true';
          markScriptLoaded(entry.id);
          resolve(true);
        });
        existingTag.addEventListener('error', () => {
          scriptState.set(entry.id, { loaded: false, promise: null, error: true });
          resolve(false);
        });
      });
      scriptState.set(entry.id, { loaded: false, promise, error: null });
      return promise;
    }
    const promise = new Promise(resolve => {
      const script = document.createElement('script');
      script.src = entry.src;
      script.async = true;
      script.dataset.arcadeScript = entry.id;
      script.addEventListener('load', () => {
        script.dataset.loaded = 'true';
        markScriptLoaded(entry.id);
        resolve(true);
      });
      script.addEventListener('error', () => {
        scriptState.set(entry.id, { loaded: false, promise: null, error: true });
        resolve(false);
      });
      document.body.appendChild(script);
    });
    scriptState.set(entry.id, { loaded: false, promise, error: null });
    return promise;
  };

  const requestArcadeScript = (pageId, options = {}) => {
    const entry = getScriptEntry(pageId);
    if (!entry) {
      return Promise.resolve(false);
    }
    return loadArcadeScript(entry).then(loaded => {
      if (loaded && typeof options.onLoaded === 'function') {
        options.onLoaded();
      }
      return loaded;
    });
  };

  const requestAllArcadeScripts = () => {
    Object.keys(ARCADE_SCRIPT_MANIFEST).forEach(pageId => {
      const entry = getScriptEntry(pageId);
      if (entry) {
        loadArcadeScript(entry);
      }
    });
  };

  const schedulePrefetch = () => {
    if (!ACTIVE_SETTINGS.prefetchOnIdle) {
      return;
    }
    const delay = ACTIVE_SETTINGS.prefetchDelayMs;
    const prefetch = () => requestAllArcadeScripts();
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(prefetch, { timeout: delay });
    } else {
      setTimeout(prefetch, delay);
    }
  };

  if (!ACTIVE_SETTINGS.enabled) {
    requestAllArcadeScripts();
  } else {
    schedulePrefetch();
  }

  if (typeof globalThis !== 'undefined') {
    globalThis.requestArcadeScript = requestArcadeScript;
  } else if (typeof window !== 'undefined') {
    window.requestArcadeScript = requestArcadeScript;
  }
})();
