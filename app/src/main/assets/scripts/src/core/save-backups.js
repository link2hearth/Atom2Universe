let lastBackupAutoTimestamp = 0;

function getSaveBackupStorage() {
  if (typeof globalThis === 'undefined' || typeof globalThis.localStorage === 'undefined') {
    return null;
  }
  return globalThis.localStorage;
}

function computeSerializedSize(serialized) {
  if (typeof serialized !== 'string') {
    return 0;
  }
  if (typeof TextEncoder !== 'undefined') {
    try {
      return new TextEncoder().encode(serialized).length;
    } catch (error) {
      // Ignore encoding issues and fall back to string length.
    }
  }
  return serialized.length;
}

function normalizeStoredBackupEntry(entry) {
  if (!entry || typeof entry !== 'object') {
    return null;
  }
  const id = typeof entry.id === 'string' && entry.id ? entry.id : null;
  const data = typeof entry.data === 'string' && entry.data ? entry.data : null;
  if (!id || !data) {
    return null;
  }
  const savedAt = Number(entry.savedAt);
  const label = typeof entry.label === 'string' && entry.label.trim() ? entry.label.trim() : null;
  const source = entry.source === 'auto' ? 'auto' : 'manual';
  const size = Math.max(0, Number(entry.size) || computeSerializedSize(data));
  return {
    id,
    data,
    savedAt: Number.isFinite(savedAt) ? savedAt : Date.now(),
    label,
    source,
    size
  };
}

function readLocalSaveBackupState() {
  const storage = getSaveBackupStorage();
  if (!storage) {
    return { version: 1, entries: [] };
  }
  try {
    const raw = storage.getItem(SAVE_BACKUP_STORAGE_KEY);
    if (!raw) {
      return { version: 1, entries: [] };
    }
    const parsed = JSON.parse(raw);
    const sourceEntries = Array.isArray(parsed?.entries) ? parsed.entries : [];
    const entries = sourceEntries
      .map(normalizeStoredBackupEntry)
      .filter(entry => entry);
    return { version: 1, entries };
  } catch (error) {
    try {
      storage.removeItem(SAVE_BACKUP_STORAGE_KEY);
    } catch (cleanupError) {
      // Ignore cleanup issues.
    }
    return { version: 1, entries: [] };
  }
}

function writeLocalSaveBackupState(state) {
  const storage = getSaveBackupStorage();
  if (!storage) {
    return false;
  }
  const safeEntries = Array.isArray(state?.entries) ? state.entries : [];
  try {
    const payload = {
      version: 1,
      entries: safeEntries.map(entry => ({
        id: entry.id,
        savedAt: entry.savedAt,
        label: entry.label,
        source: entry.source,
        size: entry.size,
        data: entry.data
      }))
    };
    storage.setItem(SAVE_BACKUP_STORAGE_KEY, JSON.stringify(payload));
    return true;
  } catch (error) {
    console.error('Unable to persist local save backups', error);
    return false;
  }
}

function toBackupListEntry(entry) {
  if (!entry) {
    return null;
  }
  return {
    id: entry.id,
    savedAt: entry.savedAt,
    label: entry.label,
    source: entry.source,
    size: entry.size
  };
}

function createLocalSaveBackup(serialized, options = {}) {
  if (typeof serialized !== 'string' || !serialized) {
    return null;
  }
  const state = readLocalSaveBackupState();
  const savedAt = Date.now();
  const entry = {
    id: `bk_${savedAt}`,
    data: serialized,
    savedAt,
    label: typeof options.label === 'string' && options.label.trim() ? options.label.trim() : null,
    source: options.source === 'auto' ? 'auto' : 'manual',
    size: computeSerializedSize(serialized)
  };
  state.entries.unshift(entry);
  while (state.entries.length > SAVE_BACKUP_MAX_ENTRIES) {
    state.entries.pop();
  }
  writeLocalSaveBackupState(state);
  return toBackupListEntry(entry);
}

function listLocalSaveBackups() {
  const state = readLocalSaveBackupState();
  return state.entries.map(toBackupListEntry).filter(entry => entry);
}

function loadLocalSaveBackup(id) {
  if (!id) {
    return null;
  }
  const state = readLocalSaveBackupState();
  const match = state.entries.find(entry => entry.id === id);
  return match ? match.data : null;
}

function deleteLocalSaveBackup(id) {
  if (!id) {
    return false;
  }
  const state = readLocalSaveBackupState();
  const nextEntries = state.entries.filter(entry => entry.id !== id);
  if (nextEntries.length === state.entries.length) {
    return false;
  }
  state.entries = nextEntries;
  writeLocalSaveBackupState(state);
  return true;
}

function parseNativeBackupEntry(entry) {
  if (!entry) {
    return null;
  }
  let payload = entry;
  if (typeof entry === 'string') {
    try {
      payload = JSON.parse(entry);
    } catch (error) {
      return null;
    }
  }
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  const id = typeof payload.id === 'string' && payload.id ? payload.id : null;
  if (!id) {
    return null;
  }
  const savedAt = Number(payload.savedAt);
  const label = typeof payload.label === 'string' && payload.label.trim() ? payload.label.trim() : null;
  const source = payload.source === 'auto' ? 'auto' : 'manual';
  const size = Math.max(0, Number(payload.size) || 0);
  return {
    id,
    savedAt: Number.isFinite(savedAt) ? savedAt : Date.now(),
    label,
    source,
    size
  };
}

function fetchNativeSaveBackups() {
  const bridge = getAndroidSaveBridge();
  if (!bridge || typeof bridge.listBackups !== 'function') {
    return null;
  }
  try {
    const raw = bridge.listBackups();
    if (!raw) {
      return [];
    }
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.map(parseNativeBackupEntry).filter(entry => entry);
  } catch (error) {
    console.error('Unable to list native backups', error);
    return [];
  }
}

function nativeSaveBackup(serialized, options = {}) {
  const bridge = getAndroidSaveBridge();
  if (!bridge || typeof bridge.saveBackup !== 'function') {
    return null;
  }
  try {
    const label = typeof options.label === 'string' && options.label.trim() ? options.label.trim() : null;
    const source = options.source === 'auto' ? 'auto' : 'manual';
    const raw = bridge.saveBackup(serialized, label, source);
    if (!raw) {
      return null;
    }
    return parseNativeBackupEntry(typeof raw === 'string' ? JSON.parse(raw) : raw);
  } catch (error) {
    console.error('Unable to create native backup', error);
    return null;
  }
}

function nativeLoadBackup(id) {
  const bridge = getAndroidSaveBridge();
  if (!bridge || typeof bridge.loadBackup !== 'function') {
    return null;
  }
  try {
    const raw = bridge.loadBackup(id);
    return typeof raw === 'string' && raw ? raw : null;
  } catch (error) {
    console.error('Unable to load native backup', error);
    return null;
  }
}

function nativeDeleteBackup(id) {
  const bridge = getAndroidSaveBridge();
  if (!bridge || typeof bridge.deleteBackup !== 'function') {
    return false;
  }
  try {
    bridge.deleteBackup(id);
    return true;
  } catch (error) {
    console.error('Unable to delete native backup', error);
    return false;
  }
}

const saveBackupManager = (() => {
  function hasNativeSupport() {
    const bridge = getAndroidSaveBridge();
    return !!(bridge && typeof bridge.listBackups === 'function');
  }

  return {
    list() {
      const entries = hasNativeSupport()
        ? fetchNativeSaveBackups()
        : listLocalSaveBackups();
      if (!Array.isArray(entries)) {
        return [];
      }
      return entries
        .filter(entry => entry)
        .sort((a, b) => Number(b.savedAt || 0) - Number(a.savedAt || 0));
    },
    createManual(serialized, options = {}) {
      if (typeof serialized !== 'string' || !serialized) {
        return null;
      }
      let entry = null;
      if (hasNativeSupport()) {
        entry = nativeSaveBackup(serialized, Object.assign({}, options, { source: 'manual' }));
      } else {
        entry = createLocalSaveBackup(serialized, Object.assign({}, options, { source: 'manual' }));
      }
      if (entry) {
        lastBackupAutoTimestamp = Date.now();
      }
      return entry;
    },
    load(id) {
      if (!id) {
        return null;
      }
      if (hasNativeSupport()) {
        return nativeLoadBackup(id);
      }
      return loadLocalSaveBackup(id);
    },
    remove(id) {
      if (!id) {
        return false;
      }
      const nativeRemoved = hasNativeSupport() ? nativeDeleteBackup(id) : false;
      const localRemoved = deleteLocalSaveBackup(id);
      return nativeRemoved || localRemoved;
    },
    recordSuccessfulSave(options = {}) {
      const previous = typeof options.previousSerialized === 'string' ? options.previousSerialized : null;
      const current = typeof options.currentSerialized === 'string' ? options.currentSerialized : null;
      if (!previous || previous === current) {
        if (hasNativeSupport()) {
          lastBackupAutoTimestamp = Date.now();
        }
        return;
      }
      if (hasNativeSupport()) {
        lastBackupAutoTimestamp = Date.now();
        return;
      }
      const now = Date.now();
      if (now - lastBackupAutoTimestamp < SAVE_BACKUP_MIN_AUTO_INTERVAL_MS) {
        return;
      }
      createLocalSaveBackup(previous, { source: 'auto' });
      lastBackupAutoTimestamp = now;
    }
  };
})();
