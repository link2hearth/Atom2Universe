(function initMidiPage() {
  'use strict';

  const LANGUAGE_STORAGE_KEY = 'atom2univers.language';

  function getI18n() {
    return typeof globalThis !== 'undefined' ? globalThis.i18n : null;
  }

  function normalizeLanguageCode(raw) {
    if (typeof raw !== 'string') {
      return '';
    }
    return raw.trim().toLowerCase();
  }

  function getAvailableLanguages() {
    const api = getI18n();
    if (api && typeof api.getAvailableLanguages === 'function') {
      try {
        const languages = api.getAvailableLanguages();
        if (Array.isArray(languages) && languages.length) {
          const normalized = languages
            .map(code => (typeof code === 'string' ? code.trim() : ''))
            .filter(Boolean);
          if (normalized.length) {
            return normalized;
          }
        }
      } catch (error) {
        console.warn('Unable to read available languages from i18n', error);
      }
    }
    return ['fr', 'en'];
  }

  function matchAvailableLanguage(raw) {
    const normalized = normalizeLanguageCode(raw);
    if (!normalized) {
      return null;
    }
    const available = getAvailableLanguages();
    const direct = available.find(code => code.toLowerCase() === normalized);
    if (direct) {
      return direct;
    }
    const [base] = normalized.split('-');
    if (base) {
      const baseMatch = available.find(code => code.toLowerCase() === base);
      if (baseMatch) {
        return baseMatch;
      }
    }
    return null;
  }

  function readStoredLanguagePreference() {
    try {
      const stored = globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY);
      if (stored) {
        const matched = matchAvailableLanguage(stored);
        if (matched) {
          return matched;
        }
      }
    } catch (error) {
      console.warn('Unable to read stored language preference', error);
    }
    return null;
  }

  function writeStoredLanguagePreference(lang) {
    try {
      const normalized = matchAvailableLanguage(lang) || normalizeLanguageCode(lang);
      if (normalized) {
        globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, normalized);
      }
    } catch (error) {
      console.warn('Unable to persist language preference', error);
    }
  }

  function detectNavigatorLanguage() {
    const nav = typeof navigator !== 'undefined' ? navigator : null;
    if (!nav) {
      return null;
    }
    const candidates = [];
    if (Array.isArray(nav.languages)) {
      candidates.push(...nav.languages);
    }
    if (typeof nav.language === 'string') {
      candidates.push(nav.language);
    }
    for (let index = 0; index < candidates.length; index += 1) {
      const match = matchAvailableLanguage(candidates[index]);
      if (match) {
        return match;
      }
    }
    return null;
  }

  function getDefaultLanguage() {
    const documentLang = typeof document !== 'undefined' ? document.documentElement?.lang : '';
    const matchedDocumentLang = documentLang ? matchAvailableLanguage(documentLang) : null;
    if (matchedDocumentLang) {
      return matchedDocumentLang;
    }
    const available = getAvailableLanguages();
    if (available.length) {
      return available[0];
    }
    return 'fr';
  }

  function getInitialLanguage() {
    const stored = readStoredLanguagePreference();
    if (stored) {
      return stored;
    }
    const navigatorLanguage = detectNavigatorLanguage();
    if (navigatorLanguage) {
      return navigatorLanguage;
    }
    return getDefaultLanguage();
  }

  function applyLanguage(language) {
    const api = getI18n();
    const fallback = getDefaultLanguage();
    const target = matchAvailableLanguage(language) || fallback;

    if (!api || typeof api.setLanguage !== 'function') {
      if (typeof document !== 'undefined' && document.documentElement) {
        document.documentElement.lang = target;
      }
      writeStoredLanguagePreference(target);
      return Promise.resolve();
    }

    return api
      .setLanguage(target)
      .then(() => {
        if (typeof api.updateTranslations === 'function') {
          api.updateTranslations(document);
        }
        const current = typeof api.getCurrentLanguage === 'function'
          ? api.getCurrentLanguage()
          : target;
        const normalized = matchAvailableLanguage(current) || target;
        if (typeof document !== 'undefined' && document.documentElement) {
          document.documentElement.lang = normalized;
        }
        writeStoredLanguagePreference(normalized);
      })
      .catch((error) => {
        console.error('Unable to initialize language for MIDI page', error);
        if (typeof document !== 'undefined' && document.documentElement) {
          document.documentElement.lang = target;
        }
      });
  }

  function initialize() {
    const initialLanguage = getInitialLanguage();
    applyLanguage(initialLanguage);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }
})();
