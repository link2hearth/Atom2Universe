const STORAGE_DISABLED_ERROR_CODES = new Set(['SecurityError', 'QuotaExceededError']);

function getStorage() {
  if (typeof globalThis === 'undefined') {
    return null;
  }
  try {
    return globalThis.localStorage || null;
  } catch (error) {
    if (error && STORAGE_DISABLED_ERROR_CODES.has(error.name)) {
      return null;
    }
    throw error;
  }
}

export function readString(key) {
  if (typeof key !== 'string' || !key) {
    return null;
  }
  const storage = getStorage();
  if (!storage) {
    return null;
  }
  try {
    const value = storage.getItem(key);
    return typeof value === 'string' ? value : null;
  } catch (error) {
    console.warn('[storage] Unable to read key', key, error);
    return null;
  }
}

export function writeString(key, value) {
  if (typeof key !== 'string' || !key) {
    return false;
  }
  const storage = getStorage();
  if (!storage) {
    return false;
  }
  try {
    if (value == null) {
      storage.removeItem(key);
    } else {
      storage.setItem(key, String(value));
    }
    return true;
  } catch (error) {
    console.warn('[storage] Unable to persist key', key, error);
    return false;
  }
}

export function readJSON(key, fallback = null) {
  const raw = readString(key);
  if (typeof raw !== 'string' || !raw.trim()) {
    return fallback;
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    console.warn('[storage] Unable to parse JSON payload for key', key, error);
    return fallback;
  }
}

export function writeJSON(key, value) {
  if (value == null) {
    return writeString(key, null);
  }
  try {
    const payload = JSON.stringify(value);
    return writeString(key, payload);
  } catch (error) {
    console.warn('[storage] Unable to stringify JSON payload for key', key, error);
    return false;
  }
}

export function remove(key) {
  const storage = getStorage();
  if (!storage || typeof key !== 'string' || !key) {
    return false;
  }
  try {
    storage.removeItem(key);
    return true;
  } catch (error) {
    console.warn('[storage] Unable to remove key', key, error);
    return false;
  }
}

export function isStorageAvailable() {
  return Boolean(getStorage());
}
