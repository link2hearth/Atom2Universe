import { readString, writeString } from '../../services/storage.js';

const UI_SCALE_STORAGE_KEY = 'atom2univers.options.uiScale';
const TEXT_FONT_STORAGE_KEY = 'atom2univers.options.textFont';
const TEXT_FONT_DEFAULT = 'orbitron';
const TEXT_FONT_CHOICES = Object.freeze({
  orbitron: {
    id: 'orbitron',
    stack: "'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  cinzel: {
    id: 'cinzel',
    stack: "'Cinzel', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  vt323: {
    id: 'vt323',
    stack: "'VT323', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  }
});

const DIGIT_FONT_STORAGE_KEY = 'atom2univers.options.digitFont';
const DIGIT_FONT_DEFAULT = 'orbitron';
const DIGIT_FONT_CHOICES = Object.freeze({
  orbitron: {
    id: 'orbitron',
    stack: "'Orbitron', sans-serif",
    compactStack: "'Orbitron', monospace"
  },
  cinzel: {
    id: 'cinzel',
    stack: "'Cinzel', 'DigitTech7', 'Orbitron', sans-serif",
    compactStack: "'Cinzel', 'Orbitron', monospace"
  },
  digittech7: {
    id: 'digittech7',
    stack: "'DigitTech7', 'Orbitron', sans-serif",
    compactStack: "'DigitTech7', 'Orbitron', monospace"
  },
  vt323: {
    id: 'vt323',
    stack: "'VT323', 'DigitTech7', 'Orbitron', sans-serif",
    compactStack: "'VT323', 'Orbitron', monospace"
  }
});

function createUiScaleConfig(globalConfig) {
  const fallbackChoices = {
    small: Object.freeze({ id: 'small', factor: 0.75 }),
    normal: Object.freeze({ id: 'normal', factor: 1 }),
    large: Object.freeze({ id: 'large', factor: 1.5 })
  };
  const fallback = {
    defaultId: 'normal',
    choices: Object.freeze(fallbackChoices)
  };

  const rawConfig = globalConfig?.ui?.scale;
  if (!rawConfig || !Array.isArray(rawConfig.options)) {
    return fallback;
  }

  const normalizedChoices = {};
  rawConfig.options.forEach(option => {
    if (!option || typeof option.id !== 'string') {
      return;
    }
    const id = option.id.trim().toLowerCase();
    if (!id || Object.prototype.hasOwnProperty.call(normalizedChoices, id)) {
      return;
    }
    const factorValue = Number(option.factor);
    if (!Number.isFinite(factorValue) || factorValue <= 0) {
      return;
    }
    normalizedChoices[id] = Object.freeze({
      id,
      factor: factorValue
    });
  });

  const ids = Object.keys(normalizedChoices);
  if (!ids.length) {
    return fallback;
  }

  let defaultId = 'normal';
  if (typeof rawConfig.default === 'string') {
    const normalizedDefault = rawConfig.default.trim().toLowerCase();
    if (Object.prototype.hasOwnProperty.call(normalizedChoices, normalizedDefault)) {
      defaultId = normalizedDefault;
    }
  }
  if (!Object.prototype.hasOwnProperty.call(normalizedChoices, defaultId)) {
    defaultId = ids[0];
  }

  return {
    defaultId,
    choices: Object.freeze(normalizedChoices)
  };
}

function createUiOptionsController({ elements, globalConfig }) {
  const uiScaleConfig = createUiScaleConfig(globalConfig ?? {});
  const uiScaleChoices = uiScaleConfig.choices;
  const uiScaleDefault = uiScaleConfig.defaultId;

  function normalizeUiScaleSelection(value) {
    if (typeof value !== 'string') {
      return uiScaleDefault;
    }
    const normalized = value.trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(uiScaleChoices, normalized)
      ? normalized
      : uiScaleDefault;
  }

  function readStoredUiScale() {
    const stored = readString(UI_SCALE_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeUiScaleSelection(stored);
    }
    return null;
  }

  function writeStoredUiScale(value) {
    const normalized = normalizeUiScaleSelection(value);
    writeString(UI_SCALE_STORAGE_KEY, normalized);
  }

  function applyUiScaleSelection(selection, options = {}) {
    const normalized = normalizeUiScaleSelection(selection);
    const config = uiScaleChoices[normalized] || uiScaleChoices[uiScaleDefault];
    const settings = Object.assign({ persist: true, updateControl: true }, options);
    const factor = config && Number.isFinite(config.factor) && config.factor > 0 ? config.factor : 1;
    const root = typeof document !== 'undefined' ? document.documentElement : null;
    if (root && root.style) {
      root.style.setProperty('--font-scale-factor', String(factor));
    }
    if (typeof document !== 'undefined' && document.body) {
      document.body.setAttribute('data-ui-scale', normalized);
    }
    if (settings.updateControl && elements?.uiScaleSelect) {
      elements.uiScaleSelect.value = normalized;
    }
    if (settings.persist) {
      writeStoredUiScale(normalized);
    }
    return normalized;
  }

  function initUiScaleOption() {
    const stored = readStoredUiScale();
    const initial = stored ?? uiScaleDefault;
    applyUiScaleSelection(initial, { persist: false, updateControl: true });
    if (!elements?.uiScaleSelect) {
      return;
    }
    elements.uiScaleSelect.addEventListener('change', event => {
      const value = event?.target?.value;
      applyUiScaleSelection(value, { persist: true, updateControl: false });
    });
  }

  function normalizeTextFontSelection(value) {
    if (typeof value !== 'string') {
      return TEXT_FONT_DEFAULT;
    }
    const normalized = value.trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(TEXT_FONT_CHOICES, normalized)
      ? normalized
      : TEXT_FONT_DEFAULT;
  }

  function readStoredTextFont() {
    const stored = readString(TEXT_FONT_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeTextFontSelection(stored);
    }
    return null;
  }

  function writeStoredTextFont(value) {
    const normalized = normalizeTextFontSelection(value);
    writeString(TEXT_FONT_STORAGE_KEY, normalized);
  }

  function applyTextFontSelection(selection, options = {}) {
    const normalized = normalizeTextFontSelection(selection);
    const config = TEXT_FONT_CHOICES[normalized] || TEXT_FONT_CHOICES[TEXT_FONT_DEFAULT];
    const settings = Object.assign({ persist: true, updateControl: true }, options);
    const root = typeof document !== 'undefined' ? document.documentElement : null;
    if (root && root.style) {
      root.style.setProperty('--font-text', config.stack);
    }
    if (typeof document !== 'undefined' && document.body) {
      document.body.setAttribute('data-text-font', normalized);
    }
    if (settings.updateControl && elements?.textFontSelect) {
      elements.textFontSelect.value = normalized;
    }
    if (settings.persist) {
      writeStoredTextFont(normalized);
    }
    return normalized;
  }

  function initTextFontOption() {
    const stored = readStoredTextFont();
    const initial = stored ?? TEXT_FONT_DEFAULT;
    applyTextFontSelection(initial, { persist: false, updateControl: true });
    if (!elements?.textFontSelect) {
      return;
    }
    elements.textFontSelect.addEventListener('change', event => {
      const value = event?.target?.value;
      applyTextFontSelection(value, { persist: true, updateControl: false });
    });
  }

  function normalizeDigitFontSelection(value) {
    if (typeof value !== 'string') {
      return DIGIT_FONT_DEFAULT;
    }
    const normalized = value.trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(DIGIT_FONT_CHOICES, normalized)
      ? normalized
      : DIGIT_FONT_DEFAULT;
  }

  function readStoredDigitFont() {
    const stored = readString(DIGIT_FONT_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeDigitFontSelection(stored);
    }
    return null;
  }

  function writeStoredDigitFont(value) {
    const normalized = normalizeDigitFontSelection(value);
    writeString(DIGIT_FONT_STORAGE_KEY, normalized);
  }

  function applyDigitFontSelection(selection, options = {}) {
    const normalized = normalizeDigitFontSelection(selection);
    const config = DIGIT_FONT_CHOICES[normalized] || DIGIT_FONT_CHOICES[DIGIT_FONT_DEFAULT];
    const settings = Object.assign({ persist: true, updateControl: true }, options);
    const root = typeof document !== 'undefined' ? document.documentElement : null;
    if (root && root.style) {
      root.style.setProperty('--font-digits', config.stack);
      root.style.setProperty('--font-digits-compact', config.compactStack);
    }
    if (typeof document !== 'undefined' && document.body) {
      document.body.setAttribute('data-digit-font', normalized);
    }
    if (settings.updateControl && elements?.digitFontSelect) {
      elements.digitFontSelect.value = normalized;
    }
    if (settings.persist) {
      writeStoredDigitFont(normalized);
    }
    return normalized;
  }

  function initDigitFontOption() {
    const stored = readStoredDigitFont();
    const initial = stored ?? DIGIT_FONT_DEFAULT;
    applyDigitFontSelection(initial, { persist: false, updateControl: true });
    if (!elements?.digitFontSelect) {
      return;
    }
    elements.digitFontSelect.addEventListener('change', event => {
      const value = event?.target?.value;
      applyDigitFontSelection(value, { persist: true, updateControl: false });
    });
  }

  function initialize() {
    initUiScaleOption();
    initTextFontOption();
    initDigitFontOption();
  }

  return {
    init: initialize,
    applyUiScaleSelection,
    applyTextFontSelection,
    applyDigitFontSelection
  };
}

export function initializeUiOptions(options) {
  const controller = createUiOptionsController(options);
  controller.init();
  return controller;
}

export { createUiOptionsController };
