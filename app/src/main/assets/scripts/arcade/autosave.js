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
    'colorStack',
    'reflex',
    'motocross',
    'jumpingCat',
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
    'hex',
    'othello',
    'gomoku',
    'echecs'
  ];

  const normalizeGameId = id => {
    if (typeof id !== 'string') {
      return '';
    }
    return id.trim().toLowerCase().replace(/[^a-z0-9]/g, '');
  };

  const CANONICAL_GAME_ID_MAP = KNOWN_GAME_IDS.reduce((map, id) => {
    const normalized = normalizeGameId(id);
    if (normalized && !map[normalized]) {
      map[normalized] = id;
    }
    return map;
  }, Object.create(null));

  const resolveKnownGameId = id => {
    const normalized = normalizeGameId(id);
    if (!normalized) {
      return null;
    }
    return CANONICAL_GAME_ID_MAP[normalized] || null;
  };

  const clampObject = value => (value && typeof value === 'object' ? value : {});

  const safeClone = value => {
    if (!value || typeof value !== 'object') {
      return null;
    }
    try {
      return JSON.parse(JSON.stringify(value));
    } catch (error) {
      return null;
    }
  };

  const extractArcadeProgressFromNativePayload = payload => {
    if (!payload || typeof payload !== 'object') {
      return null;
    }
    if (payload.entries && typeof payload.entries === 'object') {
      const version = Number(payload.version);
      return {
        version: Number.isFinite(version) && version > 0 ? version : 1,
        entries: payload.entries
      };
    }

    const candidate = (() => {
      if (payload.arcadeProgress && typeof payload.arcadeProgress === 'object') {
        return payload.arcadeProgress;
      }
      const clicker = payload.clicker && typeof payload.clicker === 'object'
        ? payload.clicker
        : null;
      if (clicker && clicker.arcadeProgress && typeof clicker.arcadeProgress === 'object') {
        return clicker.arcadeProgress;
      }
      return null;
    })();

    if (!candidate || typeof candidate !== 'object') {
      return null;
    }

    const entries = candidate.entries && typeof candidate.entries === 'object'
      ? candidate.entries
      : candidate;

    const versionCandidate = Number(candidate.version);
    const fallbackVersion = Number(payload.version);
    const version = Number.isFinite(versionCandidate) && versionCandidate > 0
      ? versionCandidate
      : Number.isFinite(fallbackVersion) && fallbackVersion > 0
        ? fallbackVersion
        : 1;

    return { version, entries };
  };

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

  const cloneEntryRecord = entry => {
    if (!entry || typeof entry !== 'object') {
      return null;
    }
    const updatedAt = Number.isFinite(entry.updatedAt) ? entry.updatedAt : Date.now();
    if (entry.state && typeof entry.state === 'object') {
      const cloned = deepClone(entry.state);
      if (cloned != null) {
        return { state: cloned, updatedAt };
      }
      return null;
    }
    const fallbackState = entry.arcadeState && typeof entry.arcadeState === 'object'
      ? safeClone(entry.arcadeState)
      : deepClone(entry);
    if (fallbackState != null && typeof fallbackState === 'object') {
      return { state: fallbackState, updatedAt };
    }
    return null;
  };

  const upsertRecord = (target, key, record) => {
    const canonicalId = resolveKnownGameId(key);
    if (!canonicalId || !record || typeof record !== 'object') {
      return;
    }
    if (!record.state || typeof record.state !== 'object') {
      return;
    }
    const timestamp = Number.isFinite(record.updatedAt) ? record.updatedAt : Date.now();
    const existing = target[canonicalId];
    if (existing && Number.isFinite(existing.updatedAt) && existing.updatedAt > timestamp) {
      return;
    }
    target[canonicalId] = {
      state: record.state,
      updatedAt: timestamp
    };
  };

  const mergeRawEntry = (target, key, rawEntry) => {
    if (!rawEntry || typeof rawEntry !== 'object') {
      return;
    }
    const record = cloneEntryRecord(rawEntry);
    if (record) {
      upsertRecord(target, key, record);
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

  const isAppReady = () => {
    if (typeof appStartCompleted === 'boolean') {
      return appStartCompleted === true;
    }
    return true;
  };

  const getSaveGameFunction = () => {
    if (typeof window === 'undefined') {
      return null;
    }
    if (!isAppReady()) {
      return null;
    }
    if (typeof window.atom2universSaveGame === 'function') {
      return window.atom2universSaveGame;
    }
    if (typeof window.saveGame === 'function') {
      return window.saveGame;
    }
    return null;
  };

  const readStoredState = () => {
    const result = { version: 1, entries: {} };

    // Prefer loading from the native bridge
    if (typeof window !== 'undefined' && window.AndroidSaveBridge && typeof window.AndroidSaveBridge.loadData === 'function') {
      try {
        const raw = window.AndroidSaveBridge.loadData();
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && typeof parsed === 'object') {
            const progress = extractArcadeProgressFromNativePayload(parsed);
            if (progress && progress.entries && typeof progress.entries === 'object') {
              result.version = Number.isFinite(progress.version) && progress.version > 0
                ? Math.floor(progress.version)
                : result.version;
              const entries = clampObject(progress.entries);
              Object.keys(entries).forEach(key => {
                mergeRawEntry(result.entries, key, entries[key]);
              });
            }
          }
        }
      } catch (error) {
        console.error('[ArcadeAutosave] Error loading data from native bridge', error);
      }
    }

    // Fallback 1: global state (for initialization)
    const globalState = getGlobalState();
    const globalProgress = clampObject(globalState && globalState.arcadeProgress);
    if (globalProgress.entries && typeof globalProgress.entries === 'object') {
      Object.keys(globalProgress.entries).forEach(key => {
        mergeRawEntry(result.entries, key, globalProgress.entries[key]);
      });
      return result;
    }

    // Fallback 2: localStorage (for legacy data)
    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && typeof parsed === 'object') {
            const entries = clampObject(parsed.entries);
            Object.keys(entries).forEach(key => {
              mergeRawEntry(result.entries, key, entries[key]);
            });
          }
        }
      } catch (error) {
        // Ignore storage errors (e.g. quota, private browsing)
      }
    }

    return result;
  };

  const AUTOSAVE_READY_EVENT = 'arcadeAutosaveReady';
  const AUTOSAVE_SYNC_EVENT = 'arcadeAutosaveSync';

  const dispatchAutosaveEvent = (type, detailBuilder) => {
    if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') {
      return;
    }
    try {
      const detail = typeof detailBuilder === 'function' ? detailBuilder() : detailBuilder;
      window.dispatchEvent(new CustomEvent(type, { detail }));
    } catch (error) {
      try {
        window.dispatchEvent(new Event(type));
      } catch (fallbackError) {
        // Ignore dispatch issues on platforms without CustomEvent support
      }
    }
  };

  const state = readStoredState();

  let persistScheduled = false;
  let persistTimer = null;

  const findStoredEntryKey = gameId => {
    const canonicalId = resolveKnownGameId(gameId);
    if (canonicalId && state.entries[canonicalId]) {
      return canonicalId;
    }
    const normalized = normalizeGameId(gameId);
    if (!normalized) {
      return canonicalId;
    }
    const fallbackKey = Object.keys(state.entries).find(key => normalizeGameId(key) === normalized);
    return fallbackKey || canonicalId;
  };

  const normalizeEntriesForGlobalState = entries => {
    const normalized = {};
    KNOWN_GAME_IDS.forEach(id => {
      normalized[id] = null;
    });
    Object.keys(entries).forEach(id => {
      const record = cloneEntryRecord(entries[id]);
      if (!record) {
        return;
      }
      const canonicalId = resolveKnownGameId(id);
      if (!canonicalId) {
        return;
      }
      const current = normalized[canonicalId];
      if (!current || !Number.isFinite(current.updatedAt) || record.updatedAt >= current.updatedAt) {
        normalized[canonicalId] = record;
      }
    });
    return normalized;
  };

  const syncGlobalArcadeProgress = () => {
    const globalState = getGlobalState();
    const normalized = normalizeEntriesForGlobalState(state.entries);
    if (globalState) {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      globalState.arcadeProgress.version = 1;
      globalState.arcadeProgress.entries = normalized;
      const saveFn = getSaveGameFunction();
      if (typeof saveFn === 'function') {
        try {
          saveFn();
        } catch (error) {
          // Ignore save errors for this optional sync.
        }
      }
    }
    return normalized;
  };

  const updateGlobalProgress = () => {
    const entries = syncGlobalArcadeProgress();
    dispatchAutosaveEvent(AUTOSAVE_SYNC_EVENT, () => {
      try {
        return { entries: JSON.parse(JSON.stringify(entries)) };
      } catch (error) {
        return { entries: null };
      }
    });
  };

  updateGlobalProgress();
  if (typeof window !== 'undefined') {
    window.setTimeout(updateGlobalProgress, 0);
    const handleLoad = () => {
      updateGlobalProgress();
      window.removeEventListener('load', handleLoad);
    };
    window.addEventListener('load', handleLoad);
  }

  const persistNow = () => {
    persistScheduled = false;
    persistTimer = null;

    const payload = {
      version: 1,
      entries: normalizeEntriesForGlobalState(state.entries)
    };
    const serializedPayload = JSON.stringify(payload);

    // Prefer saving to the native bridge
    if (typeof window !== 'undefined' && window.AndroidSaveBridge && typeof window.AndroidSaveBridge.saveData === 'function') {
      try {
        window.AndroidSaveBridge.saveData(serializedPayload);
      } catch (error) {
        console.error('[ArcadeAutosave] Error saving data to native bridge', error);
      }
    } else if (typeof window !== 'undefined' && window.localStorage) {
      // Fallback to localStorage if bridge is not available
      try {
        window.localStorage.setItem(STORAGE_KEY, serializedPayload);
      } catch (error) {
        // Ignore storage failures
      }
    }

    // Also update in-memory global state if it exists
    updateGlobalProgress();
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
    const key = findStoredEntryKey(gameId);
    if (!key) {
      return null;
    }
    const entry = state.entries[key];
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
    const canonicalId = resolveKnownGameId(gameId);
    if (!canonicalId) {
      return;
    }
    if (gameState == null) {
      const existingKey = findStoredEntryKey(gameId);
      if (existingKey && state.entries[existingKey]) {
        delete state.entries[existingKey];
        schedulePersist();
        updateGlobalProgress();
      }
      return;
    }
    const cloned = deepClone(gameState);
    if (cloned == null) {
      return;
    }
    const existingKey = findStoredEntryKey(gameId);
    if (existingKey && existingKey !== canonicalId) {
      delete state.entries[existingKey];
    }
    state.entries[canonicalId] = {
      state: cloned,
      updatedAt: Date.now()
    };
    schedulePersist();
    updateGlobalProgress();
  };

  const writeProgressEntry = (gameId, entryState) => {
    if (!entryState || typeof entryState !== 'object') {
      return;
    }
    setEntry(gameId, entryState);
  };

  const clearEntry = gameId => {
    if (!gameId || typeof gameId !== 'string') {
      return;
    }
    const key = findStoredEntryKey(gameId);
    if (key && state.entries[key]) {
      delete state.entries[key];
      schedulePersist();
      updateGlobalProgress();
    }
  };

  const listEntries = () => {
    const result = {};
    Object.keys(state.entries).forEach(id => {
      const canonicalId = resolveKnownGameId(id) || id;
      const entry = getEntry(canonicalId);
      if (canonicalId && entry) {
        result[canonicalId] = entry;
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
    writeProgressEntry,
    clear: clearEntry,
    list: listEntries,
    knownGames: () => KNOWN_GAME_IDS.slice(),
    flush() {
      if (persistTimer != null) {
        clearTimeout(persistTimer);
        persistTimer = null;
        persistScheduled = false;
      }
      persistNow();
    }
  };

  const registerGlobalSaveBridge = () => {
    if (typeof window === 'undefined') {
      return;
    }
    const existing = window.atom2universSaveGame;
    if (existing && existing.__arcadeSaveWrapper) {
      return;
    }
    const wrapper = (...args) => {
      let result;
      if (typeof existing === 'function') {
        try {
          result = existing(...args);
        } catch (error) {
          // Ignore errors from the primary save path.
        }
      }
      try {
        persistNow();
      } catch (error) {
        // Ignore autosave persistence errors.
      }
      return result;
    };
    wrapper.__arcadeSaveWrapper = true;
    wrapper.__arcadeSaveOriginal = existing;
    window.atom2universSaveGame = wrapper;
  };

  if (typeof window !== 'undefined') {
    window.ArcadeAutosave = api;
    registerGlobalSaveBridge();
    dispatchAutosaveEvent(AUTOSAVE_READY_EVENT, () => ({ api }));
  }
})();
