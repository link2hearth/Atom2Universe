(() => {
  const NEWS_STATUS_KEYS = Object.freeze({
    on: 'index.sections.options.news.state.on',
    off: 'index.sections.options.news.state.off'
  });

  const NEWS_STATUS_FALLBACKS = Object.freeze({
    on: 'Activé',
    off: 'Désactivé'
  });

  const getStorage = () => {
    if (typeof window === 'undefined' || !window.localStorage) {
      return null;
    }
    return window.localStorage;
  };

  const getNewsSettings = () => {
    if (typeof NEWS_SETTINGS === 'object' && NEWS_SETTINGS) {
      return NEWS_SETTINGS;
    }
    return {};
  };

  const resolveInitialState = () => {
    const settings = getNewsSettings();
    const storageKey = settings.enabledStorageKey;
    const storage = getStorage();
    if (storage && typeof storageKey === 'string' && storageKey.trim()) {
      const raw = storage.getItem(storageKey);
      if (raw === 'true') {
        return true;
      }
      if (raw === 'false') {
        return false;
      }
    }
    if (typeof settings.enabledByDefault === 'boolean') {
      return settings.enabledByDefault;
    }
    return true;
  };

  const persistState = enabled => {
    const settings = getNewsSettings();
    const storageKey = settings.enabledStorageKey;
    const storage = getStorage();
    if (!storage || typeof storageKey !== 'string' || !storageKey.trim()) {
      return;
    }
    storage.setItem(storageKey, enabled ? 'true' : 'false');
  };

  const updateToggleStatus = enabled => {
    const statusElement = document.getElementById('newsToggleStatus');
    if (!statusElement) {
      return;
    }
    const key = enabled ? NEWS_STATUS_KEYS.on : NEWS_STATUS_KEYS.off;
    const fallback = enabled ? NEWS_STATUS_FALLBACKS.on : NEWS_STATUS_FALLBACKS.off;
    statusElement.dataset.i18n = key;
    statusElement.textContent = fallback;
    if (globalThis.i18n && typeof globalThis.i18n.updateTranslations === 'function') {
      globalThis.i18n.updateTranslations(statusElement);
    }
  };

  const applyBannerVisibility = enabled => {
    const ticker = document.getElementById('newsTicker');
    if (!ticker) {
      return;
    }
    ticker.hidden = !enabled;
  };

  const syncToggle = (toggle, enabled) => {
    toggle.checked = enabled;
    toggle.setAttribute('aria-checked', enabled ? 'true' : 'false');
  };

  const setEnabled = (toggle, enabled) => {
    syncToggle(toggle, enabled);
    updateToggleStatus(enabled);
    applyBannerVisibility(enabled);
    persistState(enabled);
  };

  const init = () => {
    const toggle = document.getElementById('newsToggle');
    if (!toggle) {
      return;
    }
    const enabled = resolveInitialState();
    setEnabled(toggle, enabled);
    toggle.addEventListener('change', () => {
      setEnabled(toggle, Boolean(toggle.checked));
    });
  };

  if (typeof document === 'undefined') {
    return;
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
