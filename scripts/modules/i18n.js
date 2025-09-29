(function initI18n(global) {
  const AVAILABLE_LANGUAGES = Object.freeze(['fr', 'en']);
  const DEFAULT_LANGUAGE = AVAILABLE_LANGUAGES[0] || 'fr';
  const LANGUAGE_PATH = './scripts/i18n';

  const LANGUAGE_LOCALE_OVERRIDES = Object.freeze({
    fr: 'fr-FR',
    en: 'en-US'
  });

  let currentLanguage = DEFAULT_LANGUAGE;
  let currentResolvedLocale = LANGUAGE_LOCALE_OVERRIDES[currentLanguage] || currentLanguage;
  let resources = {};
  const numberFormatterCache = new Map();
  const dateTimeFormatterCache = new Map();
  const collatorCache = new Map();
  const languageChangeListeners = new Set();

  function normalizeLanguageCode(raw) {
    if (typeof raw !== 'string') {
      return '';
    }
    return raw.trim().toLowerCase();
  }

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

        const translated = translate(messageKey);
        if (target === 'text') {
          element.textContent = translated;
        } else if (target) {
          element.setAttribute(target, translated);
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
    const response = await global.fetch(`${LANGUAGE_PATH}/${lang}.json`);
    if (!response.ok) {
      throw new Error(`Unable to load language resource: ${lang}`);
    }
    return response.json();
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

    resources = data && typeof data === 'object' ? data : {};
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
