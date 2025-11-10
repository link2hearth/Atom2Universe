(function initI18n(global) {
  function normalizeLanguageCode(raw) {
    if (typeof raw !== 'string') {
      return '';
    }
    return raw.trim().toLowerCase();
  }

  const DEFAULT_LANGUAGES = Object.freeze(['fr', 'en']);
  const DEFAULT_LANGUAGE_FALLBACK = DEFAULT_LANGUAGES[0];
  const DEFAULT_LOCALE_OVERRIDES = Object.freeze({ fr: 'fr-FR', en: 'en-US' });

  const globalConfig = global && typeof global.GAME_CONFIG === 'object' ? global.GAME_CONFIG : null;
  const rawI18nConfig = globalConfig && typeof globalConfig.i18n === 'object' ? globalConfig.i18n : {};

  const LANGUAGE_DEFINITIONS = (() => {
    const entries = Array.isArray(rawI18nConfig.languages) ? rawI18nConfig.languages : null;
    if (!entries || !entries.length) {
      return null;
    }
    const sanitized = [];
    entries.forEach((entry) => {
      if (typeof entry === 'string') {
        const code = normalizeLanguageCode(entry);
        if (code) {
          sanitized.push({ code, locale: undefined });
        }
        return;
      }
      if (entry && typeof entry === 'object') {
        const code = normalizeLanguageCode(entry.code);
        if (!code) {
          return;
        }
        const locale = typeof entry.locale === 'string' && entry.locale.trim() ? entry.locale.trim() : undefined;
        sanitized.push({ code, locale });
      }
    });
    return sanitized.length ? sanitized : null;
  })();

  const AVAILABLE_LANGUAGES = (() => {
    const base = LANGUAGE_DEFINITIONS
      ? LANGUAGE_DEFINITIONS.map(({ code }) => code)
      : Array.from(DEFAULT_LANGUAGES);
    const unique = Array.from(new Set(base.filter(Boolean)));
    return Object.freeze(unique.length ? unique : Array.from(DEFAULT_LANGUAGES));
  })();

  const DEFAULT_LANGUAGE = (() => {
    const configured = typeof rawI18nConfig.defaultLanguage === 'string'
      ? normalizeLanguageCode(rawI18nConfig.defaultLanguage)
      : '';
    if (configured && AVAILABLE_LANGUAGES.includes(configured)) {
      return configured;
    }
    const primary = AVAILABLE_LANGUAGES[0];
    return primary || DEFAULT_LANGUAGE_FALLBACK;
  })();

  const LANGUAGE_LOCALE_OVERRIDES = (() => {
    const overrides = {};
    if (LANGUAGE_DEFINITIONS) {
      LANGUAGE_DEFINITIONS.forEach(({ code, locale }) => {
        if (code && locale) {
          overrides[code] = locale;
        }
      });
    }
    const configOverrides = rawI18nConfig.localeOverrides;
    if (configOverrides && typeof configOverrides === 'object') {
      Object.keys(configOverrides).forEach((key) => {
        const normalizedKey = normalizeLanguageCode(key);
        const value = configOverrides[key];
        if (normalizedKey && typeof value === 'string' && value.trim()) {
          overrides[normalizedKey] = value.trim();
        }
      });
    }
    Object.keys(DEFAULT_LOCALE_OVERRIDES).forEach((key) => {
      if (!overrides[key]) {
        overrides[key] = DEFAULT_LOCALE_OVERRIDES[key];
      }
    });
    return Object.freeze(overrides);
  })();

  const LANGUAGE_PATH = (() => {
    const rawPath = typeof rawI18nConfig.path === 'string' ? rawI18nConfig.path.trim() : '';
    if (rawPath) {
      return rawPath.replace(/\\/g, '/').replace(/\/+$/, '');
    }
    return './scripts/i18n';
  })();

  const LANGUAGE_FETCH_OPTIONS = (() => {
    const options = { cache: 'no-store' };
    const rawOptions = rawI18nConfig.fetchOptions && typeof rawI18nConfig.fetchOptions === 'object'
      ? rawI18nConfig.fetchOptions
      : rawI18nConfig.fetch && typeof rawI18nConfig.fetch === 'object'
        ? rawI18nConfig.fetch
        : null;
    if (rawOptions) {
      if (typeof rawOptions.cache === 'string' && rawOptions.cache.trim()) {
        options.cache = rawOptions.cache.trim();
      }
      if (typeof rawOptions.credentials === 'string' && rawOptions.credentials.trim()) {
        options.credentials = rawOptions.credentials.trim();
      }
      if (typeof rawOptions.mode === 'string' && rawOptions.mode.trim()) {
        options.mode = rawOptions.mode.trim();
      }
      if (rawOptions.headers && typeof rawOptions.headers === 'object') {
        options.headers = { ...rawOptions.headers };
      }
    }
    return options;
  })();

  let currentLanguage = DEFAULT_LANGUAGE;
  let currentResolvedLocale = LANGUAGE_LOCALE_OVERRIDES[currentLanguage] || currentLanguage;
  let resources = {};
  const languageResourceCache = new Map();
  const numberFormatterCache = new Map();
  const dateTimeFormatterCache = new Map();
  const collatorCache = new Map();
  const languageChangeListeners = new Set();

  function resolveAvailableLanguage(raw) {
    const normalized = normalizeLanguageCode(raw);
    if (!normalized) {
      return DEFAULT_LANGUAGE;
    }
    const directMatch = AVAILABLE_LANGUAGES.find((lang) => lang.toLowerCase() === normalized);
    if (directMatch) {
      return directMatch;
    }
    const [base] = normalized.split('-');
    if (base) {
      const baseMatch = AVAILABLE_LANGUAGES.find((lang) => lang.toLowerCase() === base);
      if (baseMatch) {
        return baseMatch;
      }
    }
    return DEFAULT_LANGUAGE;
  }

  function cloneResource(resource) {
    if (!resource || typeof resource !== 'object') {
      return null;
    }
    try {
      return JSON.parse(JSON.stringify(resource));
    } catch (error) {
      console.warn('Unable to clone embedded language resource', error);
      return null;
    }
  }

  function getCachedResource(lang) {
    const normalized = normalizeLanguageCode(lang);
    const key = normalized || (typeof lang === 'string' ? lang.trim() : '');
    if (!key) {
      return null;
    }
    if (languageResourceCache.has(key)) {
      return cloneResource(languageResourceCache.get(key));
    }
    const store = global.APP_EMBEDDED_I18N;
    if (store && typeof store === 'object') {
      const direct = store[key] || store[normalized];
      if (direct && typeof direct === 'object') {
        const cloned = cloneResource(direct);
        if (cloned) {
          languageResourceCache.set(key, cloned);
          return cloneResource(cloned);
        }
      }
    }
    return null;
  }

  function rememberLoadedResource(lang, data) {
    if (!lang || !data || typeof data !== 'object') {
      return;
    }
    const normalized = normalizeLanguageCode(lang);
    const key = normalized || lang;
    if (!key) {
      return;
    }
    const cloned = cloneResource(data);
    if (!cloned) {
      return;
    }
    languageResourceCache.set(key, cloned);
    const store = global.APP_EMBEDDED_I18N && typeof global.APP_EMBEDDED_I18N === 'object'
      ? global.APP_EMBEDDED_I18N
      : (global.APP_EMBEDDED_I18N = {});
    store[key] = cloned;
  }

  function deepMerge(target, source) {
    if (!target || typeof target !== 'object') {
      target = {};
    }
    if (!source || typeof source !== 'object') {
      return target;
    }
    Object.keys(source).forEach((key) => {
      const value = source[key];
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        const base = target[key];
        const nextTarget = base && typeof base === 'object' && !Array.isArray(base) ? base : {};
        target[key] = deepMerge({ ...nextTarget }, value);
      } else {
        target[key] = value;
      }
    });
    return target;
  }

  function mergeLanguageResources(base, override) {
    const baseClone = base && typeof base === 'object' ? cloneResource(base) : {};
    if (!override || typeof override !== 'object') {
      return baseClone || {};
    }
    const overrideClone = cloneResource(override) || {};
    return deepMerge(baseClone || {}, overrideClone);
  }

  function getEmbeddedResource(lang) {
    if (!lang) {
      return null;
    }
    const store = global.APP_EMBEDDED_I18N;
    if (!store || typeof store !== 'object') {
      return null;
    }
    const normalized = normalizeLanguageCode(lang);
    if (normalized && store[normalized] && typeof store[normalized] === 'object') {
      const cloned = cloneResource(store[normalized]);
      if (cloned) {
        return cloned;
      }
    }
    if (store[lang] && typeof store[lang] === 'object') {
      const cloned = cloneResource(store[lang]);
      if (cloned) {
        return cloned;
      }
    }
    return null;
  }

  function getValueFromPath(source, path) {
    if (!source || typeof source !== 'object') {
      return undefined;
    }
    return path.split('.').reduce((acc, segment) => {
      if (acc == null) {
        return undefined;
      }
      const key = segment.trim();
      if (!key) {
        return acc;
      }
      return acc[key];
    }, source);
  }

  function formatMessage(message, params = {}) {
    if (typeof message !== 'string' || !params || typeof params !== 'object') {
      return message;
    }
    const normalizedMessage = message.replace(/\{\{\s*([^{}\s]+)\s*\}\}/g, '{$1}');
    return normalizedMessage.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
      const value = params[token];
      return value == null ? match : String(value);
    });
  }

  function translate(key, params) {
    if (typeof key !== 'string' || !key.trim()) {
      return '';
    }
    const value = getValueFromPath(resources, key.trim());
    if (value == null) {
      return key;
    }
    if (typeof value === 'string') {
      return formatMessage(value, params);
    }
    return String(value);
  }

  function getResource(key) {
    if (typeof key !== 'string' || !key.trim()) {
      return null;
    }
    const value = getValueFromPath(resources, key.trim());
    if (value == null) {
      return null;
    }
    if (typeof value === 'object') {
      const cloned = cloneResource(value);
      if (cloned != null) {
        return cloned;
      }
    }
    return value;
  }

  function updateElementTranslations(root = global.document) {
    if (!root || typeof root.querySelectorAll !== 'function') {
      return;
    }
    const elements = root.querySelectorAll('[data-i18n]');
    elements.forEach((element) => {
      const attributeValue = element.getAttribute('data-i18n');
      if (!attributeValue) {
        return;
      }

      const parts = attributeValue
        .split(';')
        .map((part) => part.trim())
        .filter(Boolean);

      parts.forEach((part) => {
        const [target, messageKey] = part.includes(':')
          ? part.split(':', 2).map((segment) => segment.trim())
          : ['text', part.trim()];

        if (!messageKey) {
          return;
        }

        const translated = translate(messageKey);
        if (translated == null) {
          return;
        }

        let value;
        if (typeof translated === 'string') {
          const trimmed = translated.trim();
          if (!trimmed) {
            return;
          }
          const stripped = trimmed.replace(/^!+/, '').replace(/!+$/, '');
          const normalizedKey = messageKey.trim();
          if (trimmed === normalizedKey || stripped === normalizedKey) {
            return;
          }
          value = translated;
        } else {
          value = String(translated);
        }

        if (target === 'text') {
          element.textContent = value;
        } else if (target) {
          element.setAttribute(target, value);
        }
      });
    });
  }

  function applyLanguageToDocument(lang) {
    if (!global.document) {
      return;
    }
    global.document.documentElement.lang = lang;
    updateElementTranslations(global.document);
  }

  async function fetchLanguageResource(lang) {
    const normalized = normalizeLanguageCode(lang);
    const key = normalized || (typeof lang === 'string' ? lang.trim() : '');
    if (!key) {
      throw new Error('Invalid language code');
    }
    const url = `${LANGUAGE_PATH}/${key}.json`;
    if (typeof global.fetch === 'function') {
      try {
        const response = await global.fetch(url, LANGUAGE_FETCH_OPTIONS);
        if (response.ok) {
          const data = await response.json();
          if (data && typeof data === 'object') {
            return data;
          }
          throw new Error(`Invalid language resource format for ${key}`);
        }
        if (response.status === 304) {
          const cached = getCachedResource(key);
          if (cached) {
            return cached;
          }
        }
        throw new Error(`Unable to load language resource: ${key} (status ${response.status})`);
      } catch (error) {
        const cached = getCachedResource(key);
        if (cached) {
          console.warn(`Falling back to cached language resource for ${key}`, error);
          return cached;
        }
        const embedded = getEmbeddedResource(key);
        if (embedded) {
          console.warn(`Falling back to embedded language resource for ${key}`, error);
          return embedded;
        }
        throw error;
      }
    }
    const cached = getCachedResource(key);
    if (cached) {
      return cached;
    }
    const embedded = getEmbeddedResource(key);
    if (embedded) {
      return embedded;
    }
    throw new Error(`Unable to load language resource: ${key}`);
  }

  function getResolvedLocale(lang) {
    const normalized = normalizeLanguageCode(lang);
    if (!normalized) {
      return currentResolvedLocale;
    }
    if (LANGUAGE_LOCALE_OVERRIDES[normalized]) {
      return LANGUAGE_LOCALE_OVERRIDES[normalized];
    }
    const [base] = normalized.split('-');
    if (base && LANGUAGE_LOCALE_OVERRIDES[base]) {
      return LANGUAGE_LOCALE_OVERRIDES[base];
    }
    return normalized;
  }

  function clearIntlCaches() {
    numberFormatterCache.clear();
    dateTimeFormatterCache.clear();
    collatorCache.clear();
  }

  function createCachedFormatter(cache, key, factory) {
    if (cache.has(key)) {
      return cache.get(key);
    }
    const formatter = factory();
    cache.set(key, formatter);
    return formatter;
  }

  function getNumberFormatter(options) {
    const key = options ? JSON.stringify(options) : '__default__';
    return createCachedFormatter(numberFormatterCache, key, () => new Intl.NumberFormat(currentResolvedLocale, options));
  }

  function getDateTimeFormatter(options) {
    const key = options ? JSON.stringify(options) : '__default__';
    return createCachedFormatter(dateTimeFormatterCache, key, () => new Intl.DateTimeFormat(currentResolvedLocale, options));
  }

  function getCollator(options) {
    const key = options ? JSON.stringify(options) : '__default__';
    return createCachedFormatter(collatorCache, key, () => new Intl.Collator(currentResolvedLocale, options));
  }

  function formatNumber(value, options) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '';
    }
    try {
      return getNumberFormatter(options).format(numeric);
    } catch (error) {
      console.warn('Unable to format number with Intl.NumberFormat', error);
      return numeric.toLocaleString(currentResolvedLocale, options);
    }
  }

  function formatInteger(value, options = {}) {
    const mergedOptions = Object.assign({ maximumFractionDigits: 0, minimumFractionDigits: 0 }, options || {});
    return formatNumber(value, mergedOptions);
  }

  function formatDuration(value, options = {}) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '';
    }
    const mergedOptions = Object.assign({ style: 'unit', unit: 'second', unitDisplay: 'short' }, options || {});
    return formatNumber(numeric, mergedOptions);
  }

  function formatDate(value, options) {
    if (value == null) {
      return '';
    }
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    try {
      return getDateTimeFormatter(options).format(date);
    } catch (error) {
      console.warn('Unable to format date with Intl.DateTimeFormat', error);
      return date.toLocaleString(currentResolvedLocale, options);
    }
  }

  function compareText(a, b, options) {
    const first = a == null ? '' : String(a);
    const second = b == null ? '' : String(b);
    try {
      return getCollator(options).compare(first, second);
    } catch (error) {
      console.warn('Unable to compare text with Intl.Collator', error);
      return first.localeCompare(second, currentResolvedLocale, options);
    }
  }

  function toLocaleLowerCase(value) {
    if (typeof value !== 'string') {
      return '';
    }
    try {
      return value.toLocaleLowerCase(currentResolvedLocale);
    } catch (error) {
      console.warn('Unable to convert string to locale lower case', error);
      return value.toLowerCase();
    }
  }

  function toLocaleUpperCase(value) {
    if (typeof value !== 'string') {
      return '';
    }
    try {
      return value.toLocaleUpperCase(currentResolvedLocale);
    } catch (error) {
      console.warn('Unable to convert string to locale upper case', error);
      return value.toUpperCase();
    }
  }

  function notifyLanguageChange() {
    const detail = {
      language: currentLanguage,
      locale: currentResolvedLocale
    };
    languageChangeListeners.forEach((listener) => {
      try {
        listener(detail);
      } catch (error) {
        console.error('Error while notifying language change listener', error);
      }
    });
    if (typeof global.dispatchEvent === 'function' && typeof global.CustomEvent === 'function') {
      const event = new global.CustomEvent('i18n:languagechange', { detail });
      global.dispatchEvent(event);
    }
  }

  function onLanguageChanged(listener) {
    if (typeof listener !== 'function') {
      return () => {};
    }
    languageChangeListeners.add(listener);
    return () => {
      languageChangeListeners.delete(listener);
    };
  }

  function offLanguageChanged(listener) {
    if (typeof listener !== 'function') {
      return;
    }
    languageChangeListeners.delete(listener);
  }

  function initializeIntl(language) {
    currentResolvedLocale = getResolvedLocale(language);
    clearIntlCaches();
    try {
      getNumberFormatter();
      getDateTimeFormatter();
      getCollator();
    } catch (error) {
      console.warn('Unable to initialize default internationalization formatters', error);
    }
  }

  async function loadLanguageResource(lang) {
    const requestedLanguage = resolveAvailableLanguage(lang);
    let effectiveLanguage = requestedLanguage;
    const data = await fetchLanguageResource(requestedLanguage).catch((error) => {
      if (requestedLanguage === DEFAULT_LANGUAGE) {
        throw error;
      }
      effectiveLanguage = DEFAULT_LANGUAGE;
      return fetchLanguageResource(DEFAULT_LANGUAGE);
    });

    const fetchedResource = data && typeof data === 'object' ? data : {};
    const embeddedBase = getEmbeddedResource(effectiveLanguage);
    const cachedBase = getCachedResource(effectiveLanguage);
    const base = mergeLanguageResources(embeddedBase, cachedBase);
    resources = mergeLanguageResources(base, fetchedResource);
    rememberLoadedResource(effectiveLanguage, resources);
    currentLanguage = effectiveLanguage;
    initializeIntl(currentLanguage);
    applyLanguageToDocument(currentLanguage);
    notifyLanguageChange();
    return resources;
  }

  async function setLanguage(lang) {
    await loadLanguageResource(lang);
  }

  function getCurrentLanguage() {
    return currentLanguage;
  }

  function getAvailableLanguages() {
    return AVAILABLE_LANGUAGES.slice();
  }

  function getCurrentLocale() {
    return currentResolvedLocale;
  }

  function updateTranslations(root) {
    updateElementTranslations(root);
  }

  const api = {
    t: translate,
    getResource,
    setLanguage,
    getCurrentLanguage,
    getAvailableLanguages,
    getCurrentLocale,
    updateTranslations,
    formatNumber,
    formatInteger,
    formatDuration,
    formatDate,
    compareText,
    toLocaleLowerCase,
    toLocaleUpperCase,
    onLanguageChanged,
    offLanguageChanged
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  }
  if (typeof global.define === 'function' && global.define.amd) {
    global.define(() => api);
  }

  initializeIntl(currentLanguage);
  global.i18n = api;
  global.t = translate;
})(typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : this);
