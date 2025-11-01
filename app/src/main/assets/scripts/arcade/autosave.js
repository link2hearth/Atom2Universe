(() => {
  const STORAGE_KEY = 'atom2univers.arcadeSaves.v1';
  const KNOWN_GAME_IDS = [
    'particules',
    'metaux',
    'wave',
    'quantum2048',
    'gameOfLife',
    'lightsOut',
    'starBridges',
    'pipeTap',
    'sokoban',
    'bigger',
    'math',
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

  const readStoredState = () => {
    const result = { version: 1, entries: {} };
    const globalState = getGlobalState();
    const globalProgress = clampObject(globalState && globalState.arcadeProgress);
    if (globalProgress.entries && typeof globalProgress.entries === 'object') {
      Object.keys(globalProgress.entries).forEach(key => {
        const entry = globalProgress.entries[key];
        if (!entry || typeof entry !== 'object') {
          return;
        }
        const clonedState = deepClone(entry.state);
        if (clonedState != null) {
          result.entries[key] = {
            state: clonedState,
            updatedAt: Number.isFinite(entry.updatedAt) ? entry.updatedAt : Date.now()
          };
        }
      });
      return result;
    }

    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && typeof parsed === 'object') {
            const entries = clampObject(parsed.entries);
            Object.keys(entries).forEach(key => {
              const entry = entries[key];
              if (entry && typeof entry === 'object' && entry.state != null) {
                const cloned = deepClone(entry.state);
                if (cloned != null) {
                  result.entries[key] = {
                    state: cloned,
                    updatedAt: Number.isFinite(entry.updatedAt) ? entry.updatedAt : Date.now()
                  };
                }
              }
            });
          }
        }
      } catch (error) {
        // Ignore storage errors (e.g. quota, private browsing)
      }
    }

    return result;
  };

  const state = readStoredState();

  let persistScheduled = false;
  let persistTimer = null;

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

  const persistNow = () => {
    persistScheduled = false;
    persistTimer = null;
    const globalState = getGlobalState();
    if (globalState) {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      globalState.arcadeProgress.version = 1;
      globalState.arcadeProgress.entries = normalizeEntriesForGlobalState(state.entries);
    }

    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        const payload = {
          version: 1,
          entries: normalizeEntriesForGlobalState(state.entries)
        };
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      } catch (error) {
        // Ignore storage failures
      }
    }

    if (typeof window !== 'undefined') {
      if (typeof window.atom2universSaveGame === 'function') {
        window.atom2universSaveGame();
      } else if (typeof window.saveGame === 'function') {
        window.saveGame();
      }
    }
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
