(function initI18n(global) {
  const AVAILABLE_LANGUAGES = Object.freeze(['fr', 'en']);
  const DEFAULT_LANGUAGE = AVAILABLE_LANGUAGES[0] || 'fr';
  const LANGUAGE_PATH = './scripts/i18n';

  let currentLanguage = DEFAULT_LANGUAGE;
  let resources = {};

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
    applyLanguageToDocument(currentLanguage);
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

  function updateTranslations(root) {
    updateElementTranslations(root);
  }

  const api = {
    t: translate,
    setLanguage,
    getCurrentLanguage,
    getAvailableLanguages,
    updateTranslations
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  }
  if (typeof global.define === 'function' && global.define.amd) {
    global.define(() => api);
  }

  global.i18n = api;
  global.t = translate;
})(typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : this);
