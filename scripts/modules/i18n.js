const DEFAULT_LANGUAGE = 'fr';
const LANGUAGE_PATH = './scripts/i18n';

let currentLanguage = DEFAULT_LANGUAGE;
let resources = {};

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
  return message.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
    const value = params[token];
    return value == null ? match : String(value);
  });
}

export function t(key, params) {
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

function updateElementTranslations(root = document) {
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

      const translated = t(messageKey);
      if (target === 'text') {
        element.textContent = translated;
      } else if (target) {
        element.setAttribute(target, translated);
      }
    });
  });
}

function applyLanguageToDocument(lang) {
  if (typeof document === 'undefined') {
    return;
  }
  document.documentElement.lang = lang;
  updateElementTranslations(document);
}

async function fetchLanguageResource(lang) {
  const response = await fetch(`${LANGUAGE_PATH}/${lang}.json`);
  if (!response.ok) {
    throw new Error(`Unable to load language resource: ${lang}`);
  }
  return response.json();
}

async function loadLanguageResource(lang) {
  const requestedLanguage = typeof lang === 'string' && lang.trim() ? lang.trim() : DEFAULT_LANGUAGE;
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

export async function setLanguage(lang) {
  await loadLanguageResource(lang);
}

export function getCurrentLanguage() {
  return currentLanguage;
}

export function updateTranslations(root) {
  updateElementTranslations(root);
}

export default {
  t,
  setLanguage,
  getCurrentLanguage,
  updateTranslations
};
