(() => {
  const DEFAULT_DEBOUNCE_MS = 1000;
  let debounceTimer = null;

  const getSaveFunction = () => {
    if (typeof globalThis !== 'undefined') {
      if (typeof globalThis.atom2universSaveGame === 'function') {
        return globalThis.atom2universSaveGame;
      }
      if (typeof globalThis.saveGame === 'function') {
        return globalThis.saveGame;
      }
    }
    if (typeof window !== 'undefined') {
      if (typeof window.atom2universSaveGame === 'function') {
        return window.atom2universSaveGame;
      }
      if (typeof window.saveGame === 'function') {
        return window.saveGame;
      }
    }
    return null;
  };

  const resolveDebounceDelay = () => {
    const config = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
    const configured = Number(config?.arcade?.saveDebounceMs);
    if (Number.isFinite(configured) && configured >= 0) {
      return configured;
    }
    return DEFAULT_DEBOUNCE_MS;
  };

  const performSave = () => {
    debounceTimer = null;
    const saveFn = getSaveFunction();
    if (typeof saveFn === 'function') {
      saveFn();
    }
  };

  const requestSaveImmediate = () => {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }
    performSave();
  };

  const requestSaveDebounced = (options = {}) => {
    const { flush = false } = options;
    if (flush) {
      requestSaveImmediate();
      return;
    }
    const delay = resolveDebounceDelay();
    if (delay <= 0) {
      requestSaveImmediate();
      return;
    }
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    debounceTimer = setTimeout(performSave, delay);
  };

  requestSaveDebounced.flush = () => requestSaveDebounced({ flush: true });

  if (typeof globalThis !== 'undefined') {
    globalThis.requestSaveImmediate = requestSaveImmediate;
    globalThis.requestSaveDebounced = requestSaveDebounced;
  } else if (typeof window !== 'undefined') {
    window.requestSaveImmediate = requestSaveImmediate;
    window.requestSaveDebounced = requestSaveDebounced;
  }
})();
