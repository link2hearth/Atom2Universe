(() => {
  const STORAGE_KEY = 'atom2univers.arcadeSaves.v1';
  const PRIMARY_SAVE_STORAGE_KEY = 'atom2univers';
  const KNOWN_GAME_IDS = [
    'particules',
    'metaux',
    'wave',
    'quantum2048',
    'gameOfLife',
    'lightsOut',
    'starBridges',
    'pipeTap',
    'colorStack',
    'motocross',
    'sokoban',
    'bigger',
    'math',
    'starsWar',
    'balance',
    'sudoku',
    'minesweeper',
    'solitaire',
    'theLine',
    'holdem',
    'dice',
    'blackjack',
    'othello',
    'echecs'
  ];

  const clampObject = value => (value && typeof value === 'object' ? value : {});

  const deepClone = value => {
    if (value == null) {
      return value;
    }
    try {
      return JSON.parse(JSON.stringify(value));
    } catch (error) {
      return null;
    }
  };

  const getGlobalState = () => {
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
  };

  const isPrimarySaveReady = () => {
    const globalState = getGlobalState();
    if (globalState && globalState.__primarySaveReady === true) {
      return true;
    }
    return Boolean(
      typeof window !== 'undefined'
        && window.atom2universPrimarySaveReady === true
    );
  };

  const readStoredState = () => {
    const result = { version: 1, entries: {} };

    const applyPayload = payload => {
      if (!payload || typeof payload !== 'object') {
        return false;
      }
      const entries = clampObject(
        payload.entries && typeof payload.entries === 'object'
          ? payload.entries
          : payload
      );
      let found = false;
      KNOWN_GAME_IDS.forEach(id => {
        const entry = entries[id];
        if (entry == null) {
          return;
        }
        const sourceState = entry && typeof entry === 'object' && 'state' in entry
          ? entry.state
          : entry;
        if (sourceState == null) {
          return;
        }
        const cloned = deepClone(sourceState);
        if (cloned == null) {
          return;
        }
        const updatedAt =
          entry && typeof entry === 'object' && Number.isFinite(entry.updatedAt)
            ? entry.updatedAt
            : Date.now();
        result.entries[id] = {
          state: cloned,
          updatedAt
        };
        found = true;
      });
      return found;
    };

    const extractArcadeProgress = data => {
      if (!data || typeof data !== 'object') {
        return null;
      }
      if (data.arcadeProgress && typeof data.arcadeProgress === 'object') {
        return data.arcadeProgress;
      }
      if (data.entries && typeof data.entries === 'object') {
        return data;
      }
      return null;
    };

    const tryLoadSerialized = raw => {
      if (!raw) {
        return false;
      }
      try {
        const parsed = JSON.parse(raw);
        const payload = extractArcadeProgress(parsed);
        return applyPayload(payload);
      } catch (error) {
        // Ignore malformed payloads
        return false;
      }
    };

    // Prefer using the in-memory global state if it already exists.
    const globalState = getGlobalState();
    if (globalState && applyPayload(globalState.arcadeProgress)) {
      return result;
    }

    // Native bridge stores the full primary save on Android.
    if (
      typeof window !== 'undefined'
      && window.AndroidSaveBridge
      && typeof window.AndroidSaveBridge.loadData === 'function'
    ) {
      try {
        const raw = window.AndroidSaveBridge.loadData();
        if (tryLoadSerialized(raw)) {
          return result;
        }
      } catch (error) {
        console.error('[ArcadeAutosave] Error loading data from native bridge', error);
      }
    }

    if (typeof window !== 'undefined' && window.localStorage) {
      // Primary save slot in localStorage (web platform).
      if (tryLoadSerialized(window.localStorage.getItem(PRIMARY_SAVE_STORAGE_KEY))) {
        return result;
      }

      // Legacy arcade-only storage for backwards compatibility.
      try {
        const legacyRaw = window.localStorage.getItem(STORAGE_KEY);
        if (tryLoadSerialized(legacyRaw)) {
          return result;
        }
      } catch (error) {
        // Ignore storage errors (e.g. quota, private browsing)
      }
    }

    // As a last resort, retry with the global state (it may have been populated meanwhile).
    if (globalState && applyPayload(globalState.arcadeProgress)) {
      return result;
    }

    return result;
  };

  const state = readStoredState();

  let persistScheduled = false;
  let persistTimer = null;
  let pendingPrimarySaveRetry = null;

  const normalizeEntriesForGlobalState = entries => {
    const normalized = {};
    KNOWN_GAME_IDS.forEach(id => {
      normalized[id] = null;
    });
    Object.keys(entries).forEach(id => {
      const entry = entries[id];
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const cloned = deepClone(entry.state);
      if (cloned != null) {
        normalized[id] = {
          state: cloned,
          updatedAt: Number.isFinite(entry.updatedAt) ? entry.updatedAt : Date.now()
        };
      }
    });
    return normalized;
  };

  const requestPrimarySave = () => {
    if (typeof window === 'undefined') {
      return;
    }
    if (!isPrimarySaveReady()) {
      if (pendingPrimarySaveRetry == null) {
        pendingPrimarySaveRetry = setTimeout(() => {
          pendingPrimarySaveRetry = null;
          requestPrimarySave();
        }, 500);
      }
      return;
    }
    if (pendingPrimarySaveRetry != null) {
      clearTimeout(pendingPrimarySaveRetry);
      pendingPrimarySaveRetry = null;
    }
    const saveFn =
      typeof window.atom2universSaveGame === 'function'
        ? window.atom2universSaveGame
        : typeof window.saveGame === 'function'
          ? window.saveGame
          : null;
    if (!saveFn) {
      return;
    }
    try {
      saveFn();
    } catch (error) {
      console.error('[ArcadeAutosave] Unable to request primary save', error);
    }
  };

  const persistNow = () => {
    persistScheduled = false;
    persistTimer = null;

    const normalizedEntries = normalizeEntriesForGlobalState(state.entries);
    const payload = {
      version: 1,
      entries: normalizedEntries
    };
    const serializedPayload = JSON.stringify(payload);

    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        window.localStorage.setItem(STORAGE_KEY, serializedPayload);
      } catch (error) {
        // Ignore storage failures
      }
    }

    // Also update in-memory global state if it exists
    const globalState = getGlobalState();
    if (globalState) {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      globalState.arcadeProgress.version = 1;
      globalState.arcadeProgress.entries = normalizedEntries;
    }

    requestPrimarySave();
  };

  const schedulePersist = () => {
    if (persistScheduled) {
      return;
    }
    persistScheduled = true;
    persistTimer = setTimeout(persistNow, 150);
  };

  const getEntry = gameId => {
    if (!gameId || typeof gameId !== 'string') {
      return null;
    }
    const entry = state.entries[gameId];
    if (!entry || typeof entry !== 'object') {
      return null;
    }
    const clonedState = deepClone(entry.state);
    if (clonedState == null) {
      return null;
    }
    return {
      state: clonedState,
      updatedAt: Number.isFinite(entry.updatedAt) ? entry.updatedAt : null
    };
  };

  const setEntry = (gameId, gameState) => {
    if (!gameId || typeof gameId !== 'string') {
      return;
    }
    if (gameState == null) {
      delete state.entries[gameId];
      schedulePersist();
      return;
    }
    const cloned = deepClone(gameState);
    if (cloned == null) {
      return;
    }
    state.entries[gameId] = {
      state: cloned,
      updatedAt: Date.now()
    };
    schedulePersist();
  };

  const clearEntry = gameId => {
    if (!gameId || typeof gameId !== 'string') {
      return;
    }
    if (state.entries[gameId]) {
      delete state.entries[gameId];
      schedulePersist();
    }
  };

  const listEntries = () => {
    const result = {};
    Object.keys(state.entries).forEach(id => {
      const entry = getEntry(id);
      if (entry) {
        result[id] = entry;
      }
    });
    return result;
  };

  const api = {
    get(gameId) {
      const entry = getEntry(gameId);
      return entry ? entry.state : null;
    },
    getEntry,
    set: setEntry,
    clear: clearEntry,
    list: listEntries,
    knownGames: () => KNOWN_GAME_IDS.slice()
  };

  if (typeof window !== 'undefined') {
    window.ArcadeAutosave = api;
  }
})();
