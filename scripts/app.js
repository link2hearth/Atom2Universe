import { readJSON, readString, writeJSON, writeString } from './services/storage.js';
import {
  DEFAULT_MUSIC_VOLUME,
  clampMusicVolume,
  createMusicPlayer
} from './services/audio/music-player.js';
import { initializeUiOptions } from './ui/options/index.js';
import { initializeShopUI } from './ui/shop/index.js';
import { initializeLayout } from './ui/layout.js';
import { initializeCollectionsUI } from './ui/collections/index.js';
import { initializeFusionUI } from './ui/fusion/index.js';
import { initializeGoalsUI } from './ui/goals/index.js';
import { initializeInfoUI } from './ui/info/index.js';
import { initializeArcadeUI } from './ui/arcade/index.js';
import {
  ARCADE_GAME_IDS,
  DEVKIT_STATE,
  createDefaultApsCritState,
  createEmptyProductionBreakdown,
  createEmptyProductionEntry,
  createInitialArcadeProgress,
  createInitialStats,
  ensureApcFrenzyStats,
  ensureApsCritState,
  getApsCritMultiplier,
  getApsCritRemainingSeconds,
  getDevKitAutoFlatBonus,
  getLayeredStat,
  incrementLayeredStat,
  initializeCoreState,
  isDevKitGachaFree,
  isDevKitShopFree,
  normalizeArcadeProgress,
  normalizeApcFrenzyStats,
  normalizeApsCritEffect,
  normalizeApsCritState,
  normalizeFrenzyStats,
  parseStats,
  setDevKitAutoFlatBonus
} from './core/state/index.js';

const APP_DATA = typeof globalThis !== 'undefined' && globalThis.APP_DATA ? globalThis.APP_DATA : {};
const GLOBAL_CONFIG =
  typeof globalThis !== 'undefined' && globalThis.GAME_CONFIG ? globalThis.GAME_CONFIG : {};
const CONFIG_OPTIONS_WELCOME_CARD =
  GLOBAL_CONFIG
  && GLOBAL_CONFIG.uiText
  && GLOBAL_CONFIG.uiText.options
  && GLOBAL_CONFIG.uiText.options.welcomeCard
    ? GLOBAL_CONFIG.uiText.options.welcomeCard
    : null;

if (typeof globalThis !== 'undefined' && typeof globalThis.lastVisibleShopBonusIds === 'undefined') {
  globalThis.lastVisibleShopBonusIds = new Set();
}

const ATOM_SCALE_TROPHY_DATA = Array.isArray(APP_DATA.ATOM_SCALE_TROPHY_DATA)
  ? APP_DATA.ATOM_SCALE_TROPHY_DATA
  : [];

const FALLBACK_MILESTONES = Array.isArray(APP_DATA.FALLBACK_MILESTONES)
  ? APP_DATA.FALLBACK_MILESTONES
  : [];

const FALLBACK_TROPHIES = Array.isArray(APP_DATA.FALLBACK_TROPHIES)
  ? APP_DATA.FALLBACK_TROPHIES
  : [];

const SHOP_UNLOCK_THRESHOLD = new LayeredNumber(15);

const MUSIC_SUPPORTED_EXTENSIONS = Array.isArray(APP_DATA.MUSIC_SUPPORTED_EXTENSIONS)
  && APP_DATA.MUSIC_SUPPORTED_EXTENSIONS.length
    ? [...APP_DATA.MUSIC_SUPPORTED_EXTENSIONS]
    : Array.isArray(APP_DATA.DEFAULT_MUSIC_SUPPORTED_EXTENSIONS)
      && APP_DATA.DEFAULT_MUSIC_SUPPORTED_EXTENSIONS.length
      ? [...APP_DATA.DEFAULT_MUSIC_SUPPORTED_EXTENSIONS]
      : ['mp3', 'ogg', 'wav', 'webm', 'm4a'];

const MUSIC_FALLBACK_TRACKS = Array.isArray(APP_DATA.MUSIC_FALLBACK_TRACKS)
  && APP_DATA.MUSIC_FALLBACK_TRACKS.length
    ? [...APP_DATA.MUSIC_FALLBACK_TRACKS]
    : Array.isArray(APP_DATA.DEFAULT_MUSIC_FALLBACK_TRACKS)
      && APP_DATA.DEFAULT_MUSIC_FALLBACK_TRACKS.length
      ? [...APP_DATA.DEFAULT_MUSIC_FALLBACK_TRACKS]
      : [];

const BRICK_SKIN_CHOICES = Object.freeze(['original', 'metallic', 'neon', 'pastels']);

const BRICK_SKIN_TOAST_KEYS = Object.freeze({
  original: 'scripts.app.brickSkins.applied.original',
  metallic: 'scripts.app.brickSkins.applied.metallic',
  neon: 'scripts.app.brickSkins.applied.neon',
  pastels: 'scripts.app.brickSkins.applied.pastels'
});

const DEFAULT_SUDOKU_COMPLETION_REWARD = Object.freeze({
  enabled: true,
  timeLimitSeconds: 10 * 60,
  bonusSeconds: 6 * 60 * 60,
  multiplier: 1
});

const LANGUAGE_STORAGE_KEY = 'atom2univers.language';
const CLICK_SOUND_STORAGE_KEY = 'atom2univers.options.clickSoundMuted';
const CRIT_ATOM_VISUALS_STORAGE_KEY = 'atom2univers.options.critAtomVisualsDisabled';

const FALLBACK_THEME_DEFINITIONS = Object.freeze([
  Object.freeze({
    id: 'dark',
    name: 'Thème sombre',
    labelKey: 'scripts.config.themes.dark',
    classes: Object.freeze(['theme-dark'])
  }),
  Object.freeze({
    id: 'light',
    name: 'Thème clair',
    labelKey: 'scripts.config.themes.light',
    classes: Object.freeze(['theme-light'])
  }),
  Object.freeze({
    id: 'neon',
    name: 'Thème néon',
    labelKey: 'scripts.config.themes.neon',
    classes: Object.freeze(['theme-neon'])
  }),
  Object.freeze({
    id: 'aurora',
    name: 'Thème Aurore',
    labelKey: 'scripts.config.themes.aurora',
    classes: Object.freeze(['theme-aurora', 'theme-neon'])
  })
]);

function normalizeThemeDefinition(theme, fallbackIndex = 0) {
  if (!theme || typeof theme !== 'object') {
    return null;
  }
  const fallback = FALLBACK_THEME_DEFINITIONS[fallbackIndex] || FALLBACK_THEME_DEFINITIONS[0];
  const id = typeof theme.id === 'string' && theme.id.trim()
    ? theme.id.trim()
    : fallback.id;
  if (!id) {
    return null;
  }
  const labelKey = typeof theme.labelKey === 'string' && theme.labelKey.trim()
    ? theme.labelKey.trim()
    : typeof fallback.labelKey === 'string'
      ? fallback.labelKey
      : null;
  const name = typeof theme.name === 'string' && theme.name.trim()
    ? theme.name.trim()
    : typeof fallback.name === 'string'
      ? fallback.name
      : id;
  const classes = Array.isArray(theme.classes)
    ? theme.classes.map(cls => (typeof cls === 'string' ? cls.trim() : '')).filter(Boolean)
    : Array.isArray(fallback.classes)
      ? [...fallback.classes]
      : [];
  if (!classes.length) {
    classes.push('theme-dark');
  }
  return Object.freeze({ id, labelKey, name, classes: Object.freeze(classes) });
}

const RAW_THEME_DEFINITIONS = Array.isArray(GLOBAL_CONFIG?.themes?.available)
  ? GLOBAL_CONFIG.themes.available
  : null;

const THEME_DEFINITIONS = (() => {
  const source = RAW_THEME_DEFINITIONS && RAW_THEME_DEFINITIONS.length
    ? RAW_THEME_DEFINITIONS
    : FALLBACK_THEME_DEFINITIONS;
  const normalized = [];
  const seen = new Set();
  source.forEach((entry, index) => {
    const theme = normalizeThemeDefinition(entry, index);
    if (!theme || seen.has(theme.id)) {
      return;
    }
    seen.add(theme.id);
    normalized.push(theme);
  });
  if (!normalized.length) {
    FALLBACK_THEME_DEFINITIONS.forEach(theme => {
      if (!seen.has(theme.id)) {
        seen.add(theme.id);
        normalized.push(theme);
      }
    });
  }
  return Object.freeze(normalized);
})();

const THEME_CLASS_LIST = Object.freeze(
  Array.from(
    new Set(
      THEME_DEFINITIONS
        .flatMap(theme => (Array.isArray(theme.classes) ? theme.classes : []))
        .filter(Boolean)
    )
  )
);

const THEME_DEFINITION_MAP = (() => {
  const map = new Map();
  THEME_DEFINITIONS.forEach(theme => {
    map.set(theme.id, theme);
  });
  return map;
})();

const DEFAULT_THEME_ID = (() => {
  const candidates = [
    typeof GLOBAL_CONFIG?.themes?.default === 'string' ? GLOBAL_CONFIG.themes.default.trim() : null,
    typeof GLOBAL_CONFIG?.progression?.defaultTheme === 'string'
      ? GLOBAL_CONFIG.progression.defaultTheme.trim()
      : null,
    THEME_DEFINITIONS[0] ? THEME_DEFINITIONS[0].id : null,
    'dark'
  ];
  for (const candidate of candidates) {
    if (candidate && THEME_DEFINITION_MAP.has(candidate)) {
      return candidate;
    }
  }
  return 'dark';
})();

let critAtomVisualsDisabled = false;
const AVAILABLE_LANGUAGE_CODES = (() => {
  const i18n = globalThis.i18n;
  if (i18n && typeof i18n.getAvailableLanguages === 'function') {
    const languages = i18n.getAvailableLanguages();
    if (Array.isArray(languages) && languages.length) {
      const normalized = languages
        .map(code => (typeof code === 'string' ? code.trim() : ''))
        .filter(Boolean);
      if (normalized.length) {
        return Object.freeze(normalized);
      }
    }
  }
  return Object.freeze(['fr', 'en']);
})();

const LANGUAGE_OPTION_FALLBACK_LABELS = Object.freeze({
  fr: 'Français',
  en: 'English'
});

const DEFAULT_LANGUAGE_CODE = (() => {
  const primary = AVAILABLE_LANGUAGE_CODES[0];
  if (typeof primary === 'string' && primary.trim()) {
    return primary;
  }
  return 'fr';
})();

function normalizeLanguageCode(raw) {
  if (typeof raw !== 'string') {
    return '';
  }
  return raw.trim().toLowerCase();
}

function getI18nApi() {
  return globalThis.i18n;
}

function translateOrDefault(key, fallback, params) {
  if (typeof key !== 'string' || !key.trim()) {
    return fallback;
  }
  const normalizedKey = key.trim();
  const api = getI18nApi();
  const translator = api && typeof api.t === 'function'
    ? api.t
    : typeof globalThis !== 'undefined' && typeof globalThis.t === 'function'
      ? globalThis.t
      : typeof t === 'function'
        ? t
        : null;
  if (translator) {
    try {
      const translated = translator(normalizedKey, params);
      if (typeof translated === 'string') {
        const trimmed = translated.trim();
        if (!trimmed) {
          return fallback;
        }
        const stripped = trimmed.replace(/^!+/, '').replace(/!+$/, '');
        if (trimmed !== normalizedKey && stripped !== normalizedKey) {
          return translated;
        }
      } else if (translated != null) {
        return translated;
      }
    } catch (error) {
      console.warn('Unable to translate key', normalizedKey, error);
      return fallback;
    }
  }
  if (typeof fallback === 'string') {
    return fallback;
  }
  const strippedKey = normalizedKey;
  if (strippedKey) {
    return strippedKey;
  }
  return fallback;
}

function translateMessage(key, params, fallback) {
  const fallbackValue = typeof fallback === 'string'
    ? fallback
    : (typeof key === 'string' ? key : '');
  const translated = translateOrDefault(key, fallbackValue, params);
  if (typeof translated === 'string' && translated.trim()) {
    return translated;
  }
  return fallbackValue;
}

function getThemeDefinition(id) {
  if (typeof id !== 'string') {
    return null;
  }
  const normalized = id.trim();
  if (!normalized) {
    return null;
  }
  return THEME_DEFINITION_MAP.get(normalized) || null;
}

function getThemeClasses(id) {
  const definition = getThemeDefinition(id) || getThemeDefinition(DEFAULT_THEME_ID);
  if (definition && Array.isArray(definition.classes) && definition.classes.length) {
    return definition.classes;
  }
  return ['theme-dark'];
}

function getThemeLabel(theme) {
  if (!theme) {
    return '';
  }
  const fallback = typeof theme.name === 'string' && theme.name.trim() ? theme.name.trim() : theme.id;
  return translateOrDefault(theme.labelKey, fallback);
}

function renderThemeOptions() {
  if (!elements.themeSelect) {
    return;
  }
  const select = elements.themeSelect;
  const previousValue = select.value;
  const doc = select.ownerDocument || (typeof document !== 'undefined' ? document : null);
  const fragment = doc && typeof doc.createDocumentFragment === 'function'
    ? doc.createDocumentFragment()
    : null;
  select.innerHTML = '';
  THEME_DEFINITIONS.forEach(theme => {
    const option = doc && typeof doc.createElement === 'function'
      ? doc.createElement('option')
      : typeof document !== 'undefined' && typeof document.createElement === 'function'
        ? document.createElement('option')
        : null;
    if (!option) {
      return;
    }
    option.value = theme.id;
    option.setAttribute('data-i18n', `index.sections.options.theme.options.${theme.id}`);
    const fallbackLabel = getThemeLabel(theme) || theme.id;
    option.textContent = translateOrDefault(
      `index.sections.options.theme.options.${theme.id}`,
      fallbackLabel
    );
    if (fragment) {
      fragment.appendChild(option);
    } else {
      select.appendChild(option);
    }
  });
  if (fragment) {
    select.appendChild(fragment);
  }
  const i18n = getI18nApi();
  if (i18n && typeof i18n.updateTranslations === 'function') {
    i18n.updateTranslations(select);
  }
  const currentThemeId = typeof gameState !== 'undefined'
    && gameState
    && typeof gameState.theme === 'string'
    ? gameState.theme
    : null;
  const targetThemeId = getThemeDefinition(previousValue)
    ? previousValue
    : getThemeDefinition(currentThemeId)
      ? currentThemeId
      : DEFAULT_THEME_ID;
  select.value = targetThemeId;
}

function translateCollectionEffect(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.effects.${key}`, fallback, params);
}

function translateCollectionNote(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.notes.${key}`, fallback, params);
}

function translateCollectionLabel(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.labels.${key}`, fallback, params);
}

function getCurrentLocale() {
  const api = getI18nApi();
  if (api && typeof api.getCurrentLocale === 'function') {
    return api.getCurrentLocale();
  }
  return 'fr-FR';
}

function formatNumberLocalized(value, options) {
  const api = getI18nApi();
  if (api && typeof api.formatNumber === 'function') {
    const formatted = api.formatNumber(value, options);
    if (formatted !== undefined && formatted !== null) {
      return formatted;
    }
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '';
  }
  const locale = getCurrentLocale();
  try {
    return numeric.toLocaleString(locale, options);
  } catch (error) {
    return numeric.toLocaleString('fr-FR', options);
  }
}

function formatIntegerLocalized(value) {
  return formatNumberLocalized(value, { maximumFractionDigits: 0, minimumFractionDigits: 0 });
}

function formatLayeredLocalized(value, options = {}) {
  const numberOptions = options.numberFormatOptions || {
    maximumFractionDigits: 0,
    minimumFractionDigits: 0
  };
  const mantissaDigits = Number.isFinite(options.mantissaDigits)
    ? Math.min(4, Math.max(0, Math.floor(options.mantissaDigits)))
    : 1;

  const formatSmall = numeric => formatNumberLocalized(numeric, numberOptions);

  const toLayered = input => {
    if (input instanceof LayeredNumber) {
      return input;
    }
    if (typeof LayeredNumber === 'function') {
      try {
        return new LayeredNumber(input);
      } catch (error) {
        return null;
      }
    }
    return null;
  };

  const layered = toLayered(value);

  if (layered) {
    if (layered.sign === 0) {
      return formatSmall(0);
    }
    if (layered.layer === 0) {
      if (Math.abs(layered.exponent) < 6) {
        const numeric = layered.sign * layered.mantissa * Math.pow(10, layered.exponent);
        return formatSmall(numeric);
      }
      const mantissa = layered.sign * layered.mantissa;
      return `${mantissa.toFixed(mantissaDigits)}e${layered.exponent}`;
    }
    if (layered.layer === 1) {
      const exponent = Math.floor(layered.value);
      const fractional = layered.value - exponent;
      const mantissa = layered.sign * Math.pow(10, fractional);
      return `${mantissa.toFixed(mantissaDigits)}e${exponent}`;
    }
    return layered.toString();
  }

  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return formatSmall(0);
  }
  if (Math.abs(numeric) >= 1e6) {
    const exponent = Math.floor(Math.log10(Math.abs(numeric)));
    const mantissa = numeric / Math.pow(10, exponent);
    return `${mantissa.toFixed(mantissaDigits)}e${exponent}`;
  }
  return formatSmall(numeric);
}

function formatDurationLocalized(value, options) {
  const api = getI18nApi();
  if (api && typeof api.formatDuration === 'function') {
    const formatted = api.formatDuration(value, options);
    if (formatted) {
      return formatted;
    }
  }
  return formatNumberLocalized(value, Object.assign({ style: 'unit', unit: 'second', unitDisplay: 'short' }, options));
}

function readPositiveNumber(candidates, transform) {
  if (!Array.isArray(candidates)) {
    return null;
  }
  for (let i = 0; i < candidates.length; i += 1) {
    const numeric = Number(candidates[i]);
    if (Number.isFinite(numeric) && numeric > 0) {
      return typeof transform === 'function' ? transform(numeric) : numeric;
    }
  }
  return null;
}

function normalizeSudokuRewardSettings(raw) {
  const config = {
    enabled: DEFAULT_SUDOKU_COMPLETION_REWARD.enabled,
    timeLimitSeconds: DEFAULT_SUDOKU_COMPLETION_REWARD.timeLimitSeconds,
    bonusSeconds: DEFAULT_SUDOKU_COMPLETION_REWARD.bonusSeconds,
    multiplier: DEFAULT_SUDOKU_COMPLETION_REWARD.multiplier
  };

  if (raw === false) {
    config.enabled = false;
    return config;
  }

  const source = raw && typeof raw === 'object' ? raw : {};

  const limitSeconds = readPositiveNumber([
    source.timeLimitSeconds,
    source.timeLimit,
    source.limitSeconds,
    source.limit,
    source.seconds
  ]);
  if (limitSeconds) {
    config.timeLimitSeconds = limitSeconds;
  } else {
    const limitMinutes = readPositiveNumber([
      source.timeLimitMinutes,
      source.minutes,
      source.minuteLimit
    ], value => value * 60);
    if (limitMinutes) {
      config.timeLimitSeconds = limitMinutes;
    } else {
      const limitHours = readPositiveNumber([
        source.timeLimitHours,
        source.hours
      ], value => value * 60 * 60);
      if (limitHours) {
        config.timeLimitSeconds = limitHours;
      }
    }
  }

  const bonusSeconds = readPositiveNumber([
    source.offlineBonusSeconds,
    source.bonusSeconds,
    source.durationSeconds,
    source.duration,
    source.secondsBonus
  ]);
  if (bonusSeconds) {
    config.bonusSeconds = bonusSeconds;
  } else {
    const bonusMinutes = readPositiveNumber([
      source.offlineBonusMinutes,
      source.bonusMinutes,
      source.durationMinutes
    ], value => value * 60);
    if (bonusMinutes) {
      config.bonusSeconds = bonusMinutes;
    } else {
      const bonusHours = readPositiveNumber([
        source.offlineBonusHours,
        source.bonusHours,
        source.durationHours
      ], value => value * 60 * 60);
      if (bonusHours) {
        config.bonusSeconds = bonusHours;
      }
    }
  }

  const multiplier = readPositiveNumber([
    source.offlineMultiplier,
    source.multiplier,
    source.value
  ]);
  if (multiplier) {
    config.multiplier = multiplier;
  }

  if (source.enabled === false) {
    config.enabled = false;
  }

  if (config.timeLimitSeconds <= 0 || config.bonusSeconds <= 0 || config.multiplier <= 0) {
    config.enabled = false;
  }

  return config;
}

function normalizeSudokuOfflineBonusState(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const maxSeconds = readPositiveNumber([
    raw.maxSeconds,
    raw.seconds,
    raw.durationSeconds,
    raw.limitSeconds,
    raw.capSeconds
  ]);
  const multiplier = readPositiveNumber([
    raw.multiplier,
    raw.offlineMultiplier,
    raw.value
  ]);
  if (!maxSeconds || !multiplier) {
    return null;
  }
  const limitSeconds = readPositiveNumber([
    raw.limitSeconds,
    raw.timeLimitSeconds,
    raw.limit
  ]) || 0;
  const grantedAtCandidate = Number(raw.grantedAt ?? raw.timestamp ?? raw.granted_at);
  const grantedAt = Number.isFinite(grantedAtCandidate) && grantedAtCandidate > 0
    ? grantedAtCandidate
    : Date.now();
  return {
    multiplier,
    maxSeconds,
    limitSeconds,
    grantedAt
  };
}

const SUDOKU_COMPLETION_REWARD_CONFIG = normalizeSudokuRewardSettings(
  GLOBAL_CONFIG
  && GLOBAL_CONFIG.arcade
  && GLOBAL_CONFIG.arcade.sudoku
  && GLOBAL_CONFIG.arcade.sudoku.rewards
  ? GLOBAL_CONFIG.arcade.sudoku.rewards.speedCompletion
  : null
);

function formatTrophyBonusValue(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '0';
  }
  const rounded = Math.round(numeric);
  if (Math.abs(numeric - rounded) <= 1e-9) {
    return formatIntegerLocalized(rounded);
  }
  return formatNumberLocalized(numeric, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function compareTextLocalized(a, b, options) {
  const api = getI18nApi();
  if (api && typeof api.compareText === 'function') {
    return api.compareText(a, b, options);
  }
  const locale = getCurrentLocale();
  return String(a ?? '').localeCompare(String(b ?? ''), locale, options);
}

function matchAvailableLanguage(raw) {
  const normalized = normalizeLanguageCode(raw);
  if (!normalized) {
    return null;
  }
  const directMatch = AVAILABLE_LANGUAGE_CODES.find(code => code.toLowerCase() === normalized);
  if (directMatch) {
    return directMatch;
  }
  const [base] = normalized.split('-');
  if (base) {
    const baseMatch = AVAILABLE_LANGUAGE_CODES.find(code => code.toLowerCase() === base);
    if (baseMatch) {
      return baseMatch;
    }
  }
  return null;
}

function resolveLanguageCode(raw) {
  return matchAvailableLanguage(raw) ?? DEFAULT_LANGUAGE_CODE;
}

function getConfigDefaultLanguage() {
  const configLanguage =
    GLOBAL_CONFIG?.language
    ?? APP_DATA?.DEFAULT_LANGUAGE
    ?? document?.documentElement?.lang;
  if (configLanguage) {
    return matchAvailableLanguage(configLanguage) ?? DEFAULT_LANGUAGE_CODE;
  }
  return DEFAULT_LANGUAGE_CODE;
}

function readStoredLanguagePreference() {
  const stored = readString(LANGUAGE_STORAGE_KEY);
  if (!stored) {
    return null;
  }
  const matched = matchAvailableLanguage(stored);
  if (matched) {
    return matched;
  }
  return null;
}

function writeStoredLanguagePreference(lang) {
  const normalized = resolveLanguageCode(lang);
  if (!normalized) {
    return;
  }
  writeString(LANGUAGE_STORAGE_KEY, normalized);
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

function getInitialLanguagePreference() {
  const stored = readStoredLanguagePreference();
  if (stored) {
    return stored;
  }
  const navigatorLanguage = detectNavigatorLanguage();
  if (navigatorLanguage) {
    return navigatorLanguage;
  }
  return getConfigDefaultLanguage();
}

function normalizeBrickSkinSelection(rawValue) {
  const value = typeof rawValue === 'string' ? rawValue.trim().toLowerCase() : '';
  if (value === 'pastels1' || value === 'pastels2') {
    return 'pastels';
  }
  if (BRICK_SKIN_CHOICES.includes(value)) {
    return value;
  }
  return 'original';
}


function cloneRarityMultipliers(store) {
  if (store instanceof Map) {
    return new Map(store);
  }
  if (store && typeof store === 'object') {
    return { ...store };
  }
  return new Map();
}

function cloneProductionEntry(entry) {
  if (!entry) {
    return createEmptyProductionEntry();
  }
  const clone = {
    base: entry.base instanceof LayeredNumber ? entry.base.clone() : toLayeredValue(entry.base, 0),
    totalAddition: entry.totalAddition instanceof LayeredNumber
      ? entry.totalAddition.clone()
      : toLayeredValue(entry.totalAddition, 0),
    totalMultiplier: entry.totalMultiplier instanceof LayeredNumber
      ? entry.totalMultiplier.clone()
      : toMultiplierLayered(entry.totalMultiplier),
    additions: Array.isArray(entry.additions)
      ? entry.additions.map(add => ({
        ...add,
        value: add.value instanceof LayeredNumber ? add.value.clone() : toLayeredValue(add.value, 0)
      }))
      : [],
    multipliers: Array.isArray(entry.multipliers)
      ? entry.multipliers.map(mult => ({
        ...mult,
        value: mult.value instanceof LayeredNumber ? mult.value.clone() : toMultiplierLayered(mult.value)
      }))
      : [],
    total: entry.total instanceof LayeredNumber ? entry.total.clone() : toLayeredValue(entry.total, 0),
    sources: {
      flats: {
        baseFlat: entry.sources?.flats?.baseFlat instanceof LayeredNumber
          ? entry.sources.flats.baseFlat.clone()
          : toLayeredValue(entry.sources?.flats?.baseFlat, 0),
        shopFlat: entry.sources?.flats?.shopFlat instanceof LayeredNumber
          ? entry.sources.flats.shopFlat.clone()
          : toLayeredValue(entry.sources?.flats?.shopFlat, 0),
        elementFlat: entry.sources?.flats?.elementFlat instanceof LayeredNumber
          ? entry.sources.flats.elementFlat.clone()
          : toLayeredValue(entry.sources?.flats?.elementFlat, 0),
        fusionFlat: entry.sources?.flats?.fusionFlat instanceof LayeredNumber
          ? entry.sources.flats.fusionFlat.clone()
          : toLayeredValue(entry.sources?.flats?.fusionFlat, 0),
        devkitFlat: entry.sources?.flats?.devkitFlat instanceof LayeredNumber
          ? entry.sources.flats.devkitFlat.clone()
          : toLayeredValue(entry.sources?.flats?.devkitFlat, 0)
      },
      multipliers: {
        trophyMultiplier: entry.sources?.multipliers?.trophyMultiplier instanceof LayeredNumber
          ? entry.sources.multipliers.trophyMultiplier.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.trophyMultiplier ?? 1),
        frenzy: entry.sources?.multipliers?.frenzy instanceof LayeredNumber
          ? entry.sources.multipliers.frenzy.clone()
          : LayeredNumber.one(),
        apsCrit: entry.sources?.multipliers?.apsCrit instanceof LayeredNumber
          ? entry.sources.multipliers.apsCrit.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.apsCrit ?? 1),
        collectionMultiplier: entry.sources?.multipliers?.collectionMultiplier instanceof LayeredNumber
          ? entry.sources.multipliers.collectionMultiplier.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.collectionMultiplier ?? 1),
        rarityMultipliers: cloneRarityMultipliers(entry.sources?.multipliers?.rarityMultipliers)
      }
    }
  };
  return clone;
}

const APC_FRENZY_COUNTER_GRACE_MS = 3000;

const frenzyState = {
  perClick: {
    token: null,
    tokenExpire: 0,
    effectUntil: 0,
    currentMultiplier: 1,
    effects: [],
    currentStacks: 0,
    currentClickCount: 0,
    frenziesUsedInChain: 0,
    isActive: false,
    visibleUntil: 0,
    lastDisplayedCount: 0
  },
  perSecond: {
    token: null,
    tokenExpire: 0,
    effectUntil: 0,
    currentMultiplier: 1,
    effects: [],
    currentStacks: 0,
    isActive: false
  },
  spawnAccumulator: 0
};

function getClickerContexts() {
  const contexts = [
    {
      id: 'game',
      pageId: 'game',
      button: elements.atomButton,
      anchor: elements.atomButton,
      frenzyLayer: elements.frenzyLayer,
      ticketLayer: elements.ticketLayer,
      counter: {
        container: elements.apcFrenzyCounter,
        value: elements.apcFrenzyCounterValue,
        bestSingle: elements.apcFrenzyCounterBestSingle,
        bestMulti: elements.apcFrenzyCounterBestMulti
      }
    },
    {
      id: 'wave',
      pageId: 'wave',
      button: null,
      anchor: elements.waveStage || elements.waveCanvas,
      frenzyLayer: elements.waveFrenzyLayer,
      ticketLayer: elements.waveTicketLayer,
      counter: {
        container: elements.waveApcFrenzyCounter,
        value: elements.waveApcFrenzyCounterValue,
        bestSingle: elements.waveApcFrenzyCounterBestSingle,
        bestMulti: elements.waveApcFrenzyCounterBestMulti
      }
    }
  ];

  return contexts.filter(context => {
    const anchorConnected = context.anchor && context.anchor.isConnected;
    const layerConnected = context.frenzyLayer && context.frenzyLayer.isConnected;
    const counterConnected = context.counter?.container && context.counter.container.isConnected;
    const ticketConnected = context.ticketLayer && context.ticketLayer.isConnected;
    return anchorConnected || layerConnected || counterConnected || ticketConnected;
  });
}

function getClickerContextById(id) {
  if (!id) {
    return null;
  }
  const normalized = String(id).trim().toLowerCase();
  if (!normalized) {
    return null;
  }
  return getClickerContexts().find(context => context.id === normalized || context.pageId === normalized) || null;
}

function getActiveClickerContext() {
  const contexts = getClickerContexts();
  const activePage = document.body?.dataset?.activePage;
  if (activePage) {
    const active = contexts.find(context => context.pageId === activePage && context.anchor?.isConnected);
    if (active) {
      return active;
    }
  }
  const fallback = contexts.find(context => context.pageId === 'game' && context.anchor?.isConnected);
  if (fallback) {
    return fallback;
  }
  return contexts.find(context => context.anchor?.isConnected) || null;
}

function getActiveFrenzyContext() {
  const contexts = getClickerContexts();
  const activePage = document.body?.dataset?.activePage;
  if (activePage) {
    const active = contexts.find(context => context.pageId === activePage
      && context.frenzyLayer?.isConnected
      && context.anchor?.isConnected);
    if (active) {
      return active;
    }
  }
  const fallback = contexts.find(context => context.pageId === 'game'
    && context.frenzyLayer?.isConnected
    && context.anchor?.isConnected);
  if (fallback) {
    return fallback;
  }
  return contexts.find(context => context.frenzyLayer?.isConnected && context.anchor?.isConnected) || null;
}

function getCounterContexts() {
  return getClickerContexts().filter(context => context.counter?.container);
}

function getActiveTicketLayerElement() {
  const contexts = getClickerContexts();
  const activePage = document.body?.dataset?.activePage;
  if (activePage) {
    const active = contexts.find(context => context.pageId === activePage && context.ticketLayer?.isConnected);
    if (active) {
      return active.ticketLayer;
    }
  }
  const fallback = contexts.find(context => context.pageId === 'game' && context.ticketLayer?.isConnected);
  if (fallback) {
    return fallback.ticketLayer;
  }
  const any = contexts.find(context => context.ticketLayer?.isConnected);
  return any ? any.ticketLayer : null;
}

if (typeof globalThis !== 'undefined') {
  globalThis.getActiveTicketLayerElement = getActiveTicketLayerElement;
}

function resolveManualClickContext(contextId = null) {
  const explicit = getClickerContextById(contextId);
  if (explicit && explicit.anchor?.isConnected) {
    return explicit;
  }
  const active = getActiveClickerContext();
  if (active) {
    return active;
  }
  return getClickerContextById('game');
}

function isManualClickContextActive() {
  const activePage = document.body?.dataset?.activePage;
  return activePage === 'game' || activePage === 'wave';
}

function getFrenzyMultiplier(type, now = performance.now()) {
  if (!FRENZY_TYPES.includes(type)) {
    return 1;
  }
  const entry = frenzyState[type];
  if (!entry) {
    return 1;
  }
  if (!Array.isArray(entry.effects) || entry.effects.length === 0) {
    return entry.effectUntil > now ? FRENZY_CONFIG.multiplier : 1;
  }
  const activeStacks = entry.effects.filter(expire => expire > now).length;
  if (activeStacks <= 0) {
    return 1;
  }
  return Math.pow(FRENZY_CONFIG.multiplier, activeStacks);
}

function getFrenzyStackCount(type, now = performance.now()) {
  if (!FRENZY_TYPES.includes(type)) return 0;
  const entry = frenzyState[type];
  if (!entry || !Array.isArray(entry.effects)) return entry && entry.effectUntil > now ? 1 : 0;
  return entry.effects.filter(expire => expire > now).length;
}

function isApcFrenzyActive(now = performance.now()) {
  return getFrenzyStackCount('perClick', now) > 0;
}

const apcFrenzyValuePulseTimeoutIds = new Map();

function pulseApcFrenzyValue(targetContext = null) {
  const contexts = targetContext ? [targetContext] : getCounterContexts();
  contexts.forEach(context => {
    const valueElement = context?.counter?.value;
    if (!valueElement) {
      return;
    }
    valueElement.classList.remove('apc-frenzy-counter__value--pulse');
    void valueElement.offsetWidth;
    valueElement.classList.add('apc-frenzy-counter__value--pulse');
    const key = context?.id || context?.pageId || valueElement.id;
    if (apcFrenzyValuePulseTimeoutIds.has(key)) {
      clearTimeout(apcFrenzyValuePulseTimeoutIds.get(key));
    }
    const timeoutId = setTimeout(() => {
      valueElement.classList.remove('apc-frenzy-counter__value--pulse');
      apcFrenzyValuePulseTimeoutIds.delete(key);
    }, 260);
    apcFrenzyValuePulseTimeoutIds.set(key, timeoutId);
  });
}

function formatApcFrenzySingleRecordText(bestClicks) {
  const clicks = Math.max(0, Math.floor(bestClicks || 0));
  if (clicks <= 0) {
    return translateOrDefault(
      'index.sections.game.apcFrenzyCounter.bestEmpty',
      'Record : —'
    );
  }
  const clicksText = formatIntegerLocalized(clicks);
  return translateOrDefault(
    'index.sections.game.apcFrenzyCounter.bestSingleRecord',
    `Record (1 frénésie) : ${clicksText} clics`,
    { count: clicksText }
  );
}

function formatApcFrenzyMultiRecordText(bestClicks, frenziesUsed) {
  const clicks = Math.max(0, Math.floor(bestClicks || 0));
  if (clicks <= 0) {
    return translateOrDefault(
      'index.sections.game.apcFrenzyCounter.bestEmpty',
      'Record : —'
    );
  }
  const frenzies = Math.max(1, Math.floor(frenziesUsed || 0));
  const clicksText = formatIntegerLocalized(clicks);
  if (frenzies <= 1) {
    return translateOrDefault(
      'index.sections.game.apcFrenzyCounter.bestMultiRecordSingle',
      `Record (multi frénésie) : ${clicksText} clics (1 frénésie)`,
      { count: clicksText }
    );
  }
  const frenzyCountText = formatIntegerLocalized(frenzies);
  return translateOrDefault(
    'index.sections.game.apcFrenzyCounter.bestMultiRecord',
    `Record (multi frénésie) : ${clicksText} clics (${frenzyCountText} frén.)`,
    { count: clicksText, frenzies: frenzyCountText }
  );
}

function updateApcFrenzyCounterDisplay(now = performance.now()) {
  const entry = frenzyState.perClick;
  const active = isApcFrenzyActive(now);
  let currentCount = entry ? Math.max(0, Math.floor(entry.currentClickCount || 0)) : 0;
  let bestSingleClicks = 0;
  let bestChainClicks = 0;
  let bestChainFrenzies = 0;
  if (gameState.stats) {
    const sessionStats = ensureApcFrenzyStats(gameState.stats.session);
    const globalStats = ensureApcFrenzyStats(gameState.stats.global);
    bestSingleClicks = Math.max(0, Math.floor(globalStats.bestSingle?.clicks || 0));
    bestChainClicks = Math.max(0, Math.floor(globalStats.best?.clicks || 0));
    bestChainFrenzies = Math.max(0, Math.floor(globalStats.best?.frenziesUsed || 0));
    // Ensure session stats reference stays normalized for future updates.
    gameState.stats.session.apcFrenzy = sessionStats;
    gameState.stats.global.apcFrenzy = globalStats;
  }
  const contexts = getCounterContexts();
  contexts.forEach(context => {
    const { container, value, bestSingle, bestMulti } = context?.counter || {};
    if (bestSingle) {
      bestSingle.textContent = formatApcFrenzySingleRecordText(bestSingleClicks);
    }
    if (bestMulti) {
      bestMulti.textContent = formatApcFrenzyMultiRecordText(bestChainClicks, bestChainFrenzies);
    }
    if (!container) {
      return;
    }
    const contextActive = document.body?.dataset?.activePage === context.pageId;
    let displayCount = currentCount;
    let shouldDisplay = active;
    if (entry) {
      if (active) {
        entry.visibleUntil = Math.max(entry.visibleUntil || 0, now + APC_FRENZY_COUNTER_GRACE_MS);
        entry.lastDisplayedCount = currentCount;
      } else if (Number.isFinite(entry.visibleUntil) && entry.visibleUntil > now) {
        shouldDisplay = true;
        displayCount = Math.max(0, Math.floor(entry.lastDisplayedCount || 0));
      } else {
        entry.visibleUntil = 0;
        entry.lastDisplayedCount = Math.max(0, Math.floor(entry.lastDisplayedCount || 0));
      }
    }
    const isVisible = shouldDisplay && contextActive;
    container.hidden = !isVisible;
    container.style.display = isVisible ? '' : 'none';
    container.setAttribute('aria-hidden', String(!isVisible));
    container.classList.toggle('is-active', active && contextActive);
    container.classList.toggle('is-idle', !(active && contextActive));
    if (value) {
      value.textContent = formatIntegerLocalized(isVisible ? displayCount : currentCount);
    }
  });
}

function registerApcFrenzyClick(now = performance.now(), context = null) {
  const entry = frenzyState.perClick;
  if (!entry || !isApcFrenzyActive(now)) {
    return;
  }
  entry.currentClickCount = Math.max(0, Math.floor(entry.currentClickCount || 0)) + 1;
  entry.lastDisplayedCount = entry.currentClickCount;
  pulseApcFrenzyValue(context);
  updateApcFrenzyCounterDisplay(now);
}

function handleApcFrenzyActivated(wasActive, now = performance.now()) {
  const entry = frenzyState.perClick;
  if (!entry) {
    return;
  }
  if (!wasActive) {
    entry.currentClickCount = 0;
    entry.frenziesUsedInChain = 0;
    entry.lastDisplayedCount = 0;
  }
  entry.frenziesUsedInChain = Math.max(0, Math.floor(entry.frenziesUsedInChain || 0)) + 1;
  entry.isActive = true;
  updateApcFrenzyCounterDisplay(now);
}

function applyApcFrenzyRunToStats(runClicks, frenziesUsed) {
  if (!gameState.stats) {
    return;
  }
  const sanitizedClicks = Math.max(0, Math.floor(runClicks || 0));
  if (sanitizedClicks <= 0) {
    return;
  }
  const sanitizedFrenzies = Math.max(1, Math.floor(frenziesUsed || 0));
  const applyToStore = store => {
    if (!store || typeof store !== 'object') {
      return;
    }
    const statsEntry = ensureApcFrenzyStats(store);
    statsEntry.totalClicks = Math.max(0, Math.floor(statsEntry.totalClicks || 0)) + sanitizedClicks;
    const currentBestClicks = Math.max(0, Math.floor(statsEntry.best?.clicks || 0));
    const currentBestFrenzies = Math.max(0, Math.floor(statsEntry.best?.frenziesUsed || 0)) || 0;
    const currentBestSingle = Math.max(0, Math.floor(statsEntry.bestSingle?.clicks || 0));
    if (
      sanitizedClicks > currentBestClicks
      || (
        sanitizedClicks === currentBestClicks
        && sanitizedFrenzies < Math.max(1, currentBestFrenzies || Infinity)
      )
    ) {
      statsEntry.best = { clicks: sanitizedClicks, frenziesUsed: sanitizedFrenzies };
    }
    if (sanitizedFrenzies <= 1 && sanitizedClicks > currentBestSingle) {
      statsEntry.bestSingle = { clicks: sanitizedClicks };
    }
  };
  applyToStore(gameState.stats.session);
  applyToStore(gameState.stats.global);
}

function finalizeApcFrenzyRun(now = performance.now()) {
  const entry = frenzyState.perClick;
  if (!entry) {
    return;
  }
  const runClicks = Math.max(0, Math.floor(entry.currentClickCount || 0));
  const frenziesUsed = Math.max(0, Math.floor(entry.frenziesUsedInChain || 0));
  if (runClicks > 0) {
    applyApcFrenzyRunToStats(runClicks, frenziesUsed);
    saveGame();
  }
  entry.lastDisplayedCount = runClicks;
  entry.visibleUntil = Math.max(entry.visibleUntil || 0, now + APC_FRENZY_COUNTER_GRACE_MS);
  entry.currentClickCount = 0;
  entry.frenziesUsedInChain = 0;
  entry.isActive = false;
  updateApcFrenzyCounterDisplay(now);
}

function pruneFrenzyEffects(entry, now = performance.now()) {
  if (!entry) return false;
  if (!Array.isArray(entry.effects)) {
    entry.effects = [];
  }
  const before = entry.effects.length;
  entry.effects = entry.effects.filter(expire => expire > now);
  entry.effectUntil = entry.effects.length ? Math.max(...entry.effects) : 0;
  entry.currentStacks = entry.effects.length;
  return before !== entry.effects.length;
}

function applyFrenzyEffects(now = performance.now()) {
  const basePerClick = gameState.basePerClick instanceof LayeredNumber
    ? gameState.basePerClick.clone()
    : BASE_PER_CLICK.clone();
  const basePerSecond = gameState.basePerSecond instanceof LayeredNumber
    ? gameState.basePerSecond.clone()
    : BASE_PER_SECOND.clone();

  const clickMultiplier = getFrenzyMultiplier('perClick', now);
  const autoMultiplier = getFrenzyMultiplier('perSecond', now);

  let perClickResult = basePerClick.multiplyNumber(clickMultiplier);
  let perSecondResult = basePerSecond.multiplyNumber(autoMultiplier);

  perClickResult = normalizeProductionUnit(perClickResult);
  perSecondResult = normalizeProductionUnit(perSecondResult);

  gameState.perClick = perClickResult.clone();
  gameState.perSecond = perSecondResult.clone();

  const baseProduction = gameState.productionBase || createEmptyProductionBreakdown();
  const clickEntry = cloneProductionEntry(baseProduction.perClick);
  const autoEntry = cloneProductionEntry(baseProduction.perSecond);

  const clickMultiplierLayered = toMultiplierLayered(clickMultiplier);
  const autoMultiplierLayered = toMultiplierLayered(autoMultiplier);

  if (clickEntry) {
    clickEntry.sources.multipliers.frenzy = clickMultiplierLayered.clone();
    if (!isLayeredOne(clickMultiplierLayered)) {
      clickEntry.multipliers.push({
        id: 'frenzy',
        label: 'Frénésie',
        value: clickMultiplierLayered.clone(),
        source: 'frenzy'
      });
    }
    clickEntry.totalMultiplier = clickEntry.totalMultiplier.multiply(clickMultiplierLayered);
    clickEntry.total = perClickResult.clone();
  }

  if (autoEntry) {
    autoEntry.sources.multipliers.frenzy = autoMultiplierLayered.clone();
    if (!isLayeredOne(autoMultiplierLayered)) {
      autoEntry.multipliers.push({
        id: 'frenzy',
        label: 'Frénésie',
        value: autoMultiplierLayered.clone(),
        source: 'frenzy'
      });
    }
    autoEntry.totalMultiplier = autoEntry.totalMultiplier.multiply(autoMultiplierLayered);
    autoEntry.total = perSecondResult.clone();
  }

  gameState.production = {
    perClick: clickEntry,
    perSecond: autoEntry
  };

  gameState.crit = cloneCritState(gameState.baseCrit);

  frenzyState.perClick.currentMultiplier = clickMultiplier;
  frenzyState.perSecond.currentMultiplier = autoMultiplier;
  frenzyState.perClick.currentStacks = getFrenzyStackCount('perClick', now);
  frenzyState.perSecond.currentStacks = getFrenzyStackCount('perSecond', now);
}

function clearFrenzyToken(type, immediate = false) {
  if (!FRENZY_TYPES.includes(type)) return;
  const entry = frenzyState[type];
  if (!entry || !entry.token) return;
  const token = entry.token;
  entry.token = null;
  entry.tokenExpire = 0;
  token.disabled = true;
  token.style.pointerEvents = 'none';
  token.classList.add('is-expiring');
  const remove = () => {
    if (token && token.isConnected) {
      token.remove();
    }
  };
  if (immediate) {
    remove();
  } else {
    setTimeout(remove, 180);
  }
}

function positionFrenzyToken(context, type, token) {
  if (!context?.frenzyLayer || !context.anchor) return false;
  const containerRect = context.frenzyLayer.getBoundingClientRect();
  const anchorRect = context.anchor.getBoundingClientRect();
  if (!containerRect.width || !containerRect.height || !anchorRect.width || !anchorRect.height) {
    return false;
  }

  const centerX = anchorRect.left + anchorRect.width / 2;
  const centerY = anchorRect.top + anchorRect.height / 2;
  const baseSize = Math.max(anchorRect.width, anchorRect.height);
  const minRadius = baseSize * 0.45;
  const maxRadius = baseSize * 1.25;
  const radiusRange = Math.max(maxRadius - minRadius, minRadius);
  const radius = minRadius + Math.random() * radiusRange;
  const angle = Math.random() * Math.PI * 2;

  let targetX = centerX + Math.cos(angle) * radius;
  let targetY = centerY + Math.sin(angle) * radius;

  const margin = Math.max(40, baseSize * 0.25);
  targetX = Math.min(containerRect.right - margin, Math.max(containerRect.left + margin, targetX));
  targetY = Math.min(containerRect.bottom - margin, Math.max(containerRect.top + margin, targetY));

  token.style.left = `${targetX - containerRect.left}px`;
  token.style.top = `${targetY - containerRect.top}px`;
  return true;
}

function pickFrenzyAsset(info) {
  if (!info) {
    return '';
  }
  const assets = Array.isArray(info.assets) ? info.assets.filter(asset => typeof asset === 'string' && asset.trim()) : [];
  if (assets.length) {
    const randomIndex = Math.floor(Math.random() * assets.length);
    return assets[randomIndex];
  }
  if (typeof info.asset === 'string' && info.asset.trim()) {
    return info.asset;
  }
  return '';
}

function spawnFrenzyToken(type, now = performance.now()) {
  const info = FRENZY_TYPE_INFO[type];
  if (!info) return;
  const context = getActiveFrenzyContext();
  if (!context?.frenzyLayer || !context.anchor) return;
  if (FRENZY_CONFIG.displayDurationMs <= 0) return;

  const token = document.createElement('button');
  token.type = 'button';
  token.className = `frenzy-token frenzy-token--${type}`;
  token.dataset.frenzyType = type;
  const multiplierText = `×${FRENZY_CONFIG.multiplier}`;
  token.setAttribute('aria-label', `Activer la ${info.label} (${multiplierText})`);
  token.title = `Activer la ${info.label} (${multiplierText})`;

  const img = document.createElement('img');
  const assetSource = pickFrenzyAsset(info);
  if (assetSource) {
    img.src = assetSource;
  }
  img.alt = '';
  img.setAttribute('aria-hidden', 'true');
  token.appendChild(img);

  token.addEventListener('click', event => {
    event.preventDefault();
    event.stopPropagation();
    collectFrenzy(type);
  });

  if (!positionFrenzyToken(context, type, token)) {
    return;
  }

  context.frenzyLayer.appendChild(token);
  frenzyState[type].token = token;
  frenzyState[type].tokenExpire = now + FRENZY_CONFIG.displayDurationMs;
}

function attemptFrenzySpawn(type, now = performance.now()) {
  if (!FRENZY_TYPES.includes(type)) return;
  if (!isManualClickContextActive()) return;
  if (typeof document !== 'undefined' && document.hidden) return;
  const context = getActiveFrenzyContext();
  if (!context?.frenzyLayer || !context.anchor) return;
  const entry = frenzyState[type];
  if (entry.token) return;
  const chance = getEffectiveFrenzySpawnChance(type);
  if (chance <= 0) return;
  if (Math.random() >= chance) return;
  spawnFrenzyToken(type, now);
}

function collectFrenzy(type, now = performance.now()) {
  const info = FRENZY_TYPE_INFO[type];
  if (!info) return;
  if (!FRENZY_TYPES.includes(type)) return;
  const entry = frenzyState[type];
  if (!entry) return;

  clearFrenzyToken(type);
  pruneFrenzyEffects(entry, now);
  if (!Array.isArray(entry.effects)) {
    entry.effects = [];
  }
  const wasActive = entry.effects.length > 0;
  const duration = FRENZY_CONFIG.effectDurationMs;
  const expireAt = now + duration;
  const maxStacks = getTrophyFrenzyCap();
  if (entry.effects.length >= maxStacks && entry.effects.length > 0) {
    entry.effects.sort((a, b) => a - b);
    entry.effects[0] = expireAt;
  } else {
    entry.effects.push(expireAt);
  }
  entry.effectUntil = entry.effects.length ? Math.max(...entry.effects) : expireAt;
  entry.currentStacks = getFrenzyStackCount(type, now);

  applyFrenzyEffects(now);

  registerFrenzyTrigger(type);
  evaluateTrophies();
  updateUI();

  if (type === 'perClick') {
    handleApcFrenzyActivated(wasActive, now);
  }

  const rawSeconds = FRENZY_CONFIG.effectDurationMs / 1000;
  let durationText;
  if (rawSeconds >= 1) {
    if (Number.isInteger(rawSeconds)) {
      durationText = `${rawSeconds.toFixed(0)}s`;
    } else {
      const precision = rawSeconds < 10 ? 1 : 0;
      durationText = `${rawSeconds.toFixed(precision)}s`;
    }
  } else {
    durationText = `${rawSeconds.toFixed(1)}s`;
  }
  showToast(t('scripts.app.frenzyToast', {
    label: info.label,
    multiplier: FRENZY_CONFIG.multiplier,
    duration: durationText
  }));
}

function updateFrenzies(delta, now = performance.now()) {
  if (!Number.isFinite(delta) || delta < 0) {
    delta = 0;
  }

  frenzyState.spawnAccumulator += delta;
  const attempts = Math.floor(frenzyState.spawnAccumulator);
  if (attempts > 0) {
    for (let i = 0; i < attempts; i += 1) {
      FRENZY_TYPES.forEach(type => attemptFrenzySpawn(type, now));
    }
    frenzyState.spawnAccumulator -= attempts;
  }

  let needsUpdate = false;
  FRENZY_TYPES.forEach(type => {
    const entry = frenzyState[type];
    if (!entry) return;
    const wasActive = entry.isActive === true || getFrenzyStackCount(type, now) > 0;
    if (entry.token && now >= entry.tokenExpire) {
      clearFrenzyToken(type);
    }
    const removed = pruneFrenzyEffects(entry, now);
    const isActive = getFrenzyStackCount(type, now) > 0;
    entry.isActive = isActive;
    if (type === 'perClick' && wasActive && !isActive) {
      finalizeApcFrenzyRun(now);
    }
    if (removed || wasActive !== isActive) {
      needsUpdate = true;
    }
  });

  if (needsUpdate) {
    applyFrenzyEffects(now);
    updateUI();
  }
}

function resetFrenzyState(options = {}) {
  const { skipApply = false } = options;
  FRENZY_TYPES.forEach(type => {
    clearFrenzyToken(type, true);
    const entry = frenzyState[type];
    if (!entry) return;
    entry.effectUntil = 0;
    entry.currentMultiplier = 1;
    entry.effects = [];
    entry.currentStacks = 0;
    entry.currentClickCount = 0;
    entry.frenziesUsedInChain = 0;
    entry.isActive = false;
    entry.visibleUntil = 0;
    entry.lastDisplayedCount = 0;
  });
  frenzyState.spawnAccumulator = 0;
  if (!skipApply) {
    applyFrenzyEffects();
    updateUI();
  }
}

function parseStats(saved) {
  const stats = createInitialStats();
  if (!saved || typeof saved !== 'object') {
    return stats;
  }

  let legacySessionStart = null;
  if (saved.session && typeof saved.session.startedAt === 'number') {
    const candidate = Number(saved.session.startedAt);
    if (Number.isFinite(candidate) && candidate > 0) {
      legacySessionStart = candidate;
    }
  }

  if (saved.global) {
    stats.global.manualClicks = Number(saved.global.manualClicks) || 0;
    stats.global.playTimeMs = Number(saved.global.playTimeMs) || 0;
    stats.global.frenzyTriggers = normalizeFrenzyStats(saved.global.frenzyTriggers);
    stats.global.apcAtoms = LayeredNumber.fromJSON(saved.global.apcAtoms);
    stats.global.apsAtoms = LayeredNumber.fromJSON(saved.global.apsAtoms);
    stats.global.offlineAtoms = LayeredNumber.fromJSON(saved.global.offlineAtoms);
    stats.global.apcFrenzy = normalizeApcFrenzyStats(saved.global.apcFrenzy);
    const globalStart = typeof saved.global.startedAt === 'number'
      ? Number(saved.global.startedAt)
      : null;
    if (Number.isFinite(globalStart) && globalStart > 0) {
      stats.global.startedAt = globalStart;
    } else if (legacySessionStart != null) {
      stats.global.startedAt = legacySessionStart;
    }
  } else if (legacySessionStart != null) {
    stats.global.startedAt = legacySessionStart;
  }

  // Always start a fresh session when the game is (re)loaded.
  stats.session = {
    atomsGained: LayeredNumber.zero(),
    apcAtoms: LayeredNumber.zero(),
    apsAtoms: LayeredNumber.zero(),
    offlineAtoms: LayeredNumber.zero(),
    manualClicks: 0,
    onlineTimeMs: 0,
    startedAt: Date.now(),
    frenzyTriggers: { perClick: 0, perSecond: 0, total: 0 },
    apcFrenzy: createInitialApcFrenzyStats()
  };

  return stats;
}

// Game state management
const gameState = initializeCoreState({
  basePerClick: BASE_PER_CLICK,
  basePerSecond: BASE_PER_SECOND,
  defaultThemeId: DEFAULT_THEME_ID,
  offlineTicketConfig: OFFLINE_TICKET_CONFIG,
  defaultTicketStarIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
  defaultMusicVolume: DEFAULT_MUSIC_VOLUME,
  defaultMusicEnabled: DEFAULT_MUSIC_ENABLED
});

const UPGRADE_DEFS = Array.isArray(CONFIG.upgrades) ? CONFIG.upgrades : FALLBACK_UPGRADES;
const UPGRADE_NAME_MAP = new Map(UPGRADE_DEFS.map(def => [def.id, def.name || def.id]));
const UPGRADE_INDEX_MAP = new Map(UPGRADE_DEFS.map((def, index) => [def.id, index]));

const milestoneSource = Array.isArray(CONFIG.milestones) ? CONFIG.milestones : FALLBACK_MILESTONES;

const milestoneList = milestoneSource.map(entry => ({
  amount: toLayeredNumber(entry.amount, 0),
  text: entry.text
}));

function normalizeTrophyCondition(raw) {
  if (!raw || typeof raw !== 'object') {
    return { type: 'lifetimeAtoms', amount: toLayeredNumber(0, 0) };
  }
  const type = raw.type || raw.kind || (raw.frenzy ? 'frenzyTotal' : 'lifetimeAtoms');
  if (type === 'frenzyTotal' || type === 'frenzy') {
    const amount = Number(raw.amount ?? raw.value ?? 0);
    return {
      type: 'frenzyTotal',
      amount: Number.isFinite(amount) && amount > 0 ? Math.floor(amount) : 0
    };
  }
  if (
    type === 'collectionRarities'
    || type === 'rarityCollection'
    || type === 'collectionRarity'
    || type === 'rarityComplete'
  ) {
    const source = raw.rarities ?? raw.ids ?? raw.rarity ?? raw.id ?? [];
    const list = Array.isArray(source) ? source : [source];
    const rarities = Array.from(new Set(list
      .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
      .filter(Boolean)));
    return {
      type: 'collectionRarities',
      rarities
    };
  }
  if (
    type === 'fusionSuccesses'
    || type === 'fusionSuccess'
    || type === 'fusionSet'
    || type === 'fusionGroup'
  ) {
    const source = raw.fusions ?? raw.ids ?? raw.id ?? raw.fusion ?? [];
    const list = Array.isArray(source) ? source : [source];
    const fusions = Array.from(new Set(list
      .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
      .filter(Boolean)));
    return {
      type: 'fusionSuccesses',
      fusions
    };
  }
  const amount = toLayeredNumber(raw.amount ?? raw.value ?? 0, 0);
  return {
    type: 'lifetimeAtoms',
    amount
  };
}

function createTicketStarAutoCollectConfig(delaySeconds) {
  return { delaySeconds };
}

function normalizeTicketStarAutoCollectConfig(raw) {
  if (raw == null || raw === false) {
    return null;
  }
  if (raw === true) {
    return createTicketStarAutoCollectConfig(0);
  }
  const numericValue = coerceFiniteNumber(raw);
  if (numericValue != null) {
    return createTicketStarAutoCollectConfig(Math.max(0, numericValue));
  }
  if (typeof raw === 'object') {
    if (raw.enabled === false) {
      return null;
    }
    const value = raw.delaySeconds ?? raw.delay ?? raw.seconds ?? raw.value ?? raw.time;
    const delayValue = coerceFiniteNumber(value);
    const delaySeconds = Math.max(0, delayValue ?? 0);
    return createTicketStarAutoCollectConfig(delaySeconds);
  }
  return null;
}

function normalizeTrophyReward(raw) {
  if (!raw || typeof raw !== 'object') {
    return {
      multiplier: null,
      frenzyMaxStacks: null,
      description: null,
      trophyMultiplierAdd: 0,
      ticketStarAutoCollect: null
    };
  }
  let multiplier = null;
  if (raw.multiplier != null) {
    if (typeof raw.multiplier === 'number' || raw.multiplier instanceof LayeredNumber) {
      const value = toMultiplierLayered(raw.multiplier);
      multiplier = { perClick: value.clone(), perSecond: value.clone() };
    } else if (typeof raw.multiplier === 'object') {
      const globalRaw = raw.multiplier.global ?? raw.multiplier.all ?? raw.multiplier.total;
      const perClickRaw = raw.multiplier.perClick ?? raw.multiplier.click ?? null;
      const perSecondRaw = raw.multiplier.perSecond ?? raw.multiplier.auto ?? raw.multiplier.aps ?? null;
      const globalMult = globalRaw != null ? toMultiplierLayered(globalRaw) : null;
      const clickMult = perClickRaw != null ? toMultiplierLayered(perClickRaw) : null;
      const autoMult = perSecondRaw != null ? toMultiplierLayered(perSecondRaw) : null;
      multiplier = {
        perClick: clickMult ? clickMult.clone() : (globalMult ? globalMult.clone() : LayeredNumber.one()),
        perSecond: autoMult ? autoMult.clone() : (globalMult ? globalMult.clone() : LayeredNumber.one())
      };
      if (globalMult && !perClickRaw && !perSecondRaw) {
        multiplier.perClick = globalMult.clone();
        multiplier.perSecond = globalMult.clone();
      }
    }
  }
  const frenzyMaxStacksRaw = raw.frenzyMaxStacks ?? raw.frenzyStacks ?? raw.maxStacks;
  const frenzyMaxStacks = Number.isFinite(Number(frenzyMaxStacksRaw))
    ? Math.max(1, Math.floor(Number(frenzyMaxStacksRaw)))
    : null;
  const description = typeof raw.description === 'string' ? raw.description : null;
  const trophyBonusRaw =
    raw.trophyMultiplierAdd
    ?? raw.trophyMultiplierBonus
    ?? raw.trophyMultiplier
    ?? raw.trophyBonus
    ?? null;
  const trophyMultiplierAdd = Number.isFinite(Number(trophyBonusRaw))
    ? Math.max(0, Number(trophyBonusRaw))
    : 0;
  const autoCollectCandidate = raw.ticketStarAutoCollect
    ?? raw.autoCollectTicketStar
    ?? raw.ticketAutoCollect
    ?? raw.autoCollectTickets;
  const ticketStarAutoCollect = normalizeTicketStarAutoCollectConfig(autoCollectCandidate);
  return {
    multiplier,
    frenzyMaxStacks,
    description,
    trophyMultiplierAdd,
    ticketStarAutoCollect
  };
}

function normalizeTrophyDefinition(entry, index) {
  if (!entry || typeof entry !== 'object') {
    return null;
  }
  const id = String(entry.id || entry.key || index).trim();
  if (!id) return null;
  const name = typeof entry.name === 'string' ? entry.name : id;
  const description = typeof entry.description === 'string' ? entry.description : '';
  const condition = normalizeTrophyCondition(entry.condition || entry.requirement || {});
  const reward = normalizeTrophyReward(entry.reward || entry.rewards || {});
  return {
    id,
    name,
    description,
    condition,
    reward,
    rewardText: reward.description || null,
    order: Number.isFinite(Number(entry.order)) ? Number(entry.order) : index,
    targetText: typeof entry.targetText === 'string' ? entry.targetText : null,
    flavor: typeof entry.flavor === 'string' ? entry.flavor : null
  };
}

const trophySource = Array.isArray(CONFIG.trophies) ? CONFIG.trophies : FALLBACK_TROPHIES;
const TROPHY_DEFS = trophySource
  .map((entry, index) => normalizeTrophyDefinition(entry, index))
  .filter(Boolean)
  .sort((a, b) => a.order - b.order);

const TROPHY_MAP = new Map(TROPHY_DEFS.map(def => [def.id, def]));
const BIG_BANG_TROPHY_ID = 'scaleObservableUniverse';
const ARCADE_TROPHY_ID = 'millionAtoms';
const INFO_TROPHY_ID = 'scaleSandGrain';
const ACHIEVEMENTS_UNLOCK_TROPHY_ID = ARCADE_TROPHY_ID;
const LOCKABLE_PAGE_IDS = new Set(['gacha', 'tableau', 'fusion', 'info']);

function getPageUnlockState() {
  if (!gameState.pageUnlocks || typeof gameState.pageUnlocks !== 'object') {
    gameState.pageUnlocks = createInitialPageUnlockState();
  }
  return gameState.pageUnlocks;
}

function isPageUnlocked(pageId) {
  if (pageId === 'arcadeHub' || pageId === 'arcade') {
    return isArcadeUnlocked();
  }
  if (pageId === 'shop') {
    const atoms = gameState.atoms instanceof LayeredNumber
      ? gameState.atoms
      : toLayeredValue(gameState.atoms, 0);
    return atoms.compare(SHOP_UNLOCK_THRESHOLD) >= 0;
  }
  if (pageId === 'info') {
    return true;
  }
  if (!LOCKABLE_PAGE_IDS.has(pageId)) {
    return true;
  }
  const unlocks = getPageUnlockState();
  return unlocks?.[pageId] === true;
}

function unlockPage(pageId, options = {}) {
  if (!LOCKABLE_PAGE_IDS.has(pageId)) {
    return false;
  }
  const unlocks = getPageUnlockState();
  if (unlocks[pageId] === true) {
    return false;
  }
  unlocks[pageId] = true;
  if (pageId === 'gacha') {
    resetTicketStarState({ reschedule: true });
    if (typeof ticketStarState === 'object' && ticketStarState) {
      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      if (!Number.isFinite(ticketStarState.nextSpawnTime) || ticketStarState.nextSpawnTime === Number.POSITIVE_INFINITY) {
        ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
      }
    }
  }
  if (!options.deferUI) {
    updatePageUnlockUI();
  }
  if (options.save !== false) {
    saveGame();
  }
  if (options.announce) {
    const message = typeof options.announce === 'string'
      ? options.announce
      : t('scripts.app.pageUnlocked');
    showToast(message);
  }
  return true;
}

function evaluatePageUnlocks(options = {}) {
  const unlocks = getPageUnlockState();
  let changed = false;

  if (!unlocks.gacha) {
    const ticketCount = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
    if (ticketCount > 0) {
      changed = unlockPage('gacha', { save: false, deferUI: true }) || changed;
    } else {
      const hasElements = Object.values(gameState.elements || {}).some(entry => getElementLifetimeCount(entry) > 0);
      if (hasElements) {
        changed = unlockPage('gacha', { save: false, deferUI: true }) || changed;
      }
    }
  }

  if (!unlocks.tableau) {
    const hasDrawnElement = Object.values(gameState.elements || {}).some(entry => getElementLifetimeCount(entry) > 0);
    if (hasDrawnElement) {
      changed = unlockPage('tableau', { save: false, deferUI: true }) || changed;
    }
  }

  if (!unlocks.fusion) {
    if (isRarityCollectionComplete('commun') && isRarityCollectionComplete('essentiel')) {
      changed = unlockPage('fusion', { save: false, deferUI: true }) || changed;
    }
  }

  if (!unlocks.info) {
    if (getUnlockedTrophySet().has(INFO_TROPHY_ID)) {
      changed = unlockPage('info', { save: false, deferUI: true }) || changed;
    }
  }

  if (changed) {
    updatePageUnlockUI();
    if (options.save !== false) {
      saveGame();
    }
  } else if (!options.deferUI) {
    updatePageUnlockUI();
  }

  return changed;
}

function getUnlockedTrophySet() {
  if (gameState.trophies instanceof Set) {
    return gameState.trophies;
  }
  const list = Array.isArray(gameState.trophies) ? gameState.trophies : [];
  gameState.trophies = new Set(list);
  return gameState.trophies;
}

function setNavButtonLockState(button, unlocked) {
  if (!button) {
    return;
  }
  button.hidden = !unlocked;
  button.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  button.disabled = !unlocked;
  button.setAttribute('aria-disabled', unlocked ? 'false' : 'true');
  if (!unlocked) {
    button.classList.remove('active');
  }
}

function ensureActivePageUnlocked() {
  const activePage = document.body?.dataset?.activePage;
  if (activePage && !isPageUnlocked(activePage) && activePage !== 'game') {
    showPage('game');
  }
}

function getUnlockedTrophyIds() {
  return Array.from(getUnlockedTrophySet());
}

function getRarityCollectionTotals(rarityId) {
  if (!rarityId) {
    return { total: 0, owned: 0 };
  }
  const pool = gachaPools.get(rarityId);
  if (!Array.isArray(pool) || pool.length === 0) {
    return { total: Array.isArray(pool) ? pool.length : 0, owned: 0 };
  }
  let owned = 0;
  pool.forEach(elementId => {
    const entry = gameState.elements?.[elementId];
    if (hasElementLifetime(entry)) {
      owned += 1;
    }
  });
  return { total: pool.length, owned };
}

function isRarityCollectionComplete(rarityId) {
  const { total, owned } = getRarityCollectionTotals(rarityId);
  return total > 0 && owned >= total;
}

function getTotalFrenzyTriggers() {
  const stats = gameState.stats?.global;
  if (!stats) return 0;
  const total = Number(stats.frenzyTriggers?.total ?? 0);
  return Number.isFinite(total) ? total : 0;
}

function computeTrophyEffects() {
  const unlocked = getUnlockedTrophySet();
  let clickMultiplier = LayeredNumber.one();
  let autoMultiplier = LayeredNumber.one();
  let maxStacks = FRENZY_CONFIG.baseMaxStacks;
  const critAccumulator = createCritAccumulator();
  let trophyMultiplierBonus = 0;
  let ticketStarAutoCollect = null;

  unlocked.forEach(id => {
    const def = TROPHY_MAP.get(id);
    if (!def) return;
    const reward = def.reward;
    if (reward?.multiplier) {
      const clickMult = reward.multiplier.perClick instanceof LayeredNumber
        ? reward.multiplier.perClick
        : toMultiplierLayered(reward.multiplier.perClick ?? 1);
      const autoMult = reward.multiplier.perSecond instanceof LayeredNumber
        ? reward.multiplier.perSecond
        : toMultiplierLayered(reward.multiplier.perSecond ?? 1);
      if (!isLayeredOne(clickMult)) {
        clickMultiplier = clickMultiplier.multiply(clickMult);
      }
      if (!isLayeredOne(autoMult)) {
        autoMultiplier = autoMultiplier.multiply(autoMult);
      }
    }
    if (Number.isFinite(reward?.frenzyMaxStacks)) {
      maxStacks = Math.max(maxStacks, reward.frenzyMaxStacks);
    }
    let trophyBonus = reward?.trophyMultiplierAdd;
    if (trophyBonus instanceof LayeredNumber) {
      trophyBonus = trophyBonus.toNumber();
    } else if (trophyBonus != null) {
      trophyBonus = Number(trophyBonus);
    }
    if (Number.isFinite(trophyBonus) && trophyBonus > 0) {
      trophyMultiplierBonus += trophyBonus;
    }
    applyCritModifiersFromEffect(critAccumulator, reward);
    if (reward?.crit) {
      applyCritModifiersFromEffect(critAccumulator, reward.crit);
    }
    if (reward?.ticketStarAutoCollect) {
      const normalized = normalizeTicketStarAutoCollectConfig(reward.ticketStarAutoCollect);
      if (normalized) {
        if (!ticketStarAutoCollect || normalized.delaySeconds < ticketStarAutoCollect.delaySeconds) {
          ticketStarAutoCollect = normalized;
        }
      }
    }
  });

  if (trophyMultiplierBonus > 0) {
    const trophyMultiplierValue = toMultiplierLayered(1 + trophyMultiplierBonus);
    clickMultiplier = clickMultiplier.multiply(trophyMultiplierValue);
    autoMultiplier = autoMultiplier.multiply(trophyMultiplierValue);
  }

  const critEffect = finalizeCritEffect(critAccumulator);

  return { clickMultiplier, autoMultiplier, maxStacks, critEffect, ticketStarAutoCollect };
}

function getTrophyFrenzyCap() {
  let maxStacks = FRENZY_CONFIG.baseMaxStacks;
  getUnlockedTrophySet().forEach(id => {
    const def = TROPHY_MAP.get(id);
    if (!def?.reward) return;
    if (Number.isFinite(def.reward.frenzyMaxStacks)) {
      maxStacks = Math.max(maxStacks, def.reward.frenzyMaxStacks);
    }
  });
  return maxStacks;
}

function formatTrophyProgress(def) {
  const { condition } = def;
  if (condition.type === 'frenzyTotal') {
    const current = getTotalFrenzyTriggers();
    const target = condition.amount || 0;
    const clampedTarget = Math.max(1, target);
    const percent = Math.max(0, Math.min(1, current / clampedTarget));
    return {
      current,
      target,
      percent,
      displayCurrent: formatLayeredLocalized(current, {
        numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
        mantissaDigits: 1
      }),
      displayTarget: formatLayeredLocalized(clampedTarget, {
        numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
        mantissaDigits: 1
      })
    };
  }
  if (condition.type === 'collectionRarities') {
    const rarities = Array.isArray(condition.rarities) ? condition.rarities : [];
    let owned = 0;
    let total = 0;
    rarities.forEach(rarityId => {
      const totals = getRarityCollectionTotals(rarityId);
      owned += totals.owned;
      total += totals.total;
    });
    const percent = total > 0 ? Math.max(0, Math.min(1, owned / total)) : 0;
    return {
      current: owned,
      target: total,
      percent,
      displayCurrent: formatLayeredLocalized(owned, {
        numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
        mantissaDigits: 1
      }),
      displayTarget: formatLayeredLocalized(total, {
        numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
        mantissaDigits: 1
      })
    };
  }
  if (condition.type === 'fusionSuccesses') {
    const fusionIds = Array.isArray(condition.fusions) ? condition.fusions : [];
    const total = fusionIds.length;
    let completed = 0;
    fusionIds.forEach(fusionId => {
      if (getFusionSuccessCount(fusionId) > 0) {
        completed += 1;
      }
    });
    const percent = total > 0 ? Math.max(0, Math.min(1, completed / total)) : 0;
    return {
      current: completed,
      target: total,
      percent,
      displayCurrent: formatLayeredLocalized(completed, {
        numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
        mantissaDigits: 1
      }),
      displayTarget: formatLayeredLocalized(total, {
        numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
        mantissaDigits: 1
      })
    };
  }
  const current = gameState.lifetime;
  const target = condition.amount instanceof LayeredNumber
    ? condition.amount
    : toLayeredNumber(condition.amount, 0);
  let percent = 0;
  if (current instanceof LayeredNumber && target instanceof LayeredNumber) {
    if (current.compare(target) >= 0) {
      percent = 1;
    } else {
      const targetNumber = target.toNumber();
      const currentNumber = current.toNumber();
      if (Number.isFinite(targetNumber) && targetNumber > 0 && Number.isFinite(currentNumber)) {
        percent = Math.max(0, Math.min(1, currentNumber / targetNumber));
      }
    }
  }
  return {
    current,
    target,
    percent,
    displayCurrent: formatLayeredLocalized(current, {
      numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
      mantissaDigits: 1
    }),
    displayTarget: formatLayeredLocalized(target, {
      numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
      mantissaDigits: 1
    })
  };
}

function isTrophyConditionMet(def) {
  if (!def) return false;
  const { condition } = def;
  if (!condition) return false;
  if (condition.type === 'frenzyTotal') {
    return getTotalFrenzyTriggers() >= (condition.amount || 0);
  }
  if (condition.type === 'collectionRarities') {
    const rarities = Array.isArray(condition.rarities) ? condition.rarities : [];
    if (!rarities.length) {
      return false;
    }
    return rarities.every(rarityId => isRarityCollectionComplete(rarityId));
  }
  if (condition.type === 'fusionSuccesses') {
    const fusionIds = Array.isArray(condition.fusions) ? condition.fusions : [];
    if (!fusionIds.length) {
      return false;
    }
    return fusionIds.every(fusionId => getFusionSuccessCount(fusionId) > 0);
  }
  const target = condition.amount instanceof LayeredNumber
    ? condition.amount
    : toLayeredNumber(condition.amount, 0);
  return gameState.lifetime.compare(target) >= 0;
}

function unlockTrophy(def) {
  if (!def) return false;
  const unlocked = getUnlockedTrophySet();
  if (unlocked.has(def.id)) return false;
  unlocked.add(def.id);
  const trophyTexts = goalsController
    ? goalsController.getDisplayTexts(def)
    : { name: typeof def?.name === 'string' ? def.name : (def?.id ?? ''), description: '', reward: '' };
  showToast(t('scripts.app.trophies.unlocked', { name: trophyTexts.name }));
  recalcProduction();
  if (goalsController) {
    goalsController.update();
    goalsController.updateMilestone();
  }
  updateBigBangVisibility();
  updateOptionsIntroDetails();
  updateBrickSkinOption();
  updateBrandPortalState({ animate: def.id === ARCADE_TROPHY_ID });
  updatePrimaryNavigationLocks();
  evaluatePageUnlocks({ save: false });
  return true;
}

function evaluateTrophies() {
  let changed = false;
  TROPHY_DEFS.forEach(def => {
    if (!getUnlockedTrophySet().has(def.id) && isTrophyConditionMet(def)) {
      if (unlockTrophy(def)) {
        changed = true;
      }
    }
  });
  if (changed) {
    saveGame();
  }
}

const elements = {
  brandPortal: document.getElementById('brandPortal'),
  navButtons: document.querySelectorAll('.nav-button'),
  navArcadeButton: document.getElementById('navArcadeButton'),
  navShopButton: document.querySelector('.nav-button[data-target="shop"]'),
  navGachaButton: document.querySelector('.nav-button[data-target="gacha"]'),
  navTableButton: document.querySelector('.nav-button[data-target="tableau"]'),
  navFusionButton: document.querySelector('.nav-button[data-target="fusion"]'),
  navInfoButton: document.querySelector('.nav-button[data-target="info"]'),
  navMidiButton: document.querySelector('.nav-button[data-target="midi"]'),
  navBigBangButton: document.getElementById('navBigBangButton'),
  pages: document.querySelectorAll('.page'),
  statusAtomsButton: document.getElementById('statusAtomsButton'),
  statusAtoms: document.getElementById('statusAtoms'),
  statusApc: document.getElementById('statusApc'),
  statusAps: document.getElementById('statusAps'),
  statusCrit: document.getElementById('statusCrit'),
  statusCritValue: document.getElementById('statusCritValue'),
  statusApsCrit: document.getElementById('statusApsCrit'),
  statusApsCritChrono: document.getElementById('statusApsCritChrono'),
  statusApsCritMultiplier: document.getElementById('statusApsCritMultiplier'),
  statusApsCritSeparator: document.querySelector('.status-aps-crit-separator'),
  frenzyStatus: document.getElementById('frenzyStatus'),
  statusApcFrenzy: {
    container: document.getElementById('statusApcFrenzy'),
    multiplier: document.getElementById('statusApcFrenzyMultiplier'),
    timer: document.getElementById('statusApcFrenzyTimer')
  },
  statusApsFrenzy: {
    container: document.getElementById('statusApsFrenzy'),
    multiplier: document.getElementById('statusApsFrenzyMultiplier'),
    timer: document.getElementById('statusApsFrenzyTimer')
  },
  atomButton: document.getElementById('atomButton'),
  atomVisual: document.querySelector('.atom-visual'),
  atomImage: document.querySelector('#atomButton .atom-image'),
  frenzyLayer: document.getElementById('frenzyLayer'),
  ticketLayer: document.getElementById('ticketLayer'),
  apcFrenzyCounter: document.getElementById('apcFrenzyCounter'),
  apcFrenzyCounterValue: document.getElementById('apcFrenzyCounterValue'),
  apcFrenzyCounterBestSingle: document.getElementById('apcFrenzyCounterBestSingle'),
  apcFrenzyCounterBestMulti: document.getElementById('apcFrenzyCounterBestMulti'),
  starfield: document.querySelector('.starfield'),
  shopActionsHeader: document.getElementById('shopActionsHeader'),
  shopList: document.getElementById('shopList'),
  periodicTable: document.getElementById('periodicTable'),
  fusionList: document.getElementById('fusionList'),
  fusionLog: document.getElementById('fusionLog'),
  elementInfoPanel: document.getElementById('elementInfoPanel'),
  elementInfoPlaceholder: document.getElementById('elementInfoPlaceholder'),
  elementInfoContent: document.getElementById('elementInfoContent'),
  elementInfoNumber: document.getElementById('elementInfoNumber'),
  elementInfoSymbol: document.getElementById('elementInfoSymbol'),
  elementInfoName: document.getElementById('elementInfoName'),
  elementInfoCategoryButton: document.getElementById('elementInfoCategoryButton'),
  elementInfoOwnedCount: document.getElementById('elementInfoOwnedCount'),
  elementInfoCollection: document.getElementById('elementInfoCollection'),
  elementDetailsOverlay: document.getElementById('elementDetailsOverlay'),
  elementDetailsDialog: document.getElementById('elementDetailsDialog'),
  elementDetailsTitle: document.getElementById('elementDetailsTitle'),
  elementDetailsBody: document.getElementById('elementDetailsBody'),
  elementDetailsCloseButton: document.getElementById('elementDetailsCloseButton'),
  elementFamilyOverlay: document.getElementById('elementFamilyOverlay'),
  elementFamilyDialog: document.getElementById('elementFamilyDialog'),
  elementFamilyTitle: document.getElementById('elementFamilyTitle'),
  elementFamilyBody: document.getElementById('elementFamilyBody'),
  elementFamilyCloseButton: document.getElementById('elementFamilyCloseButton'),
  collectionProgress: document.getElementById('elementCollectionProgress'),
  collectionSummaryTile: document.getElementById('elementCollectionSummary'),
  collectionSummaryCurrent: document.getElementById('elementCollectionCurrentTotal'),
  collectionSummaryLifetime: document.getElementById('elementCollectionLifetimeTotal'),
  nextMilestone: document.getElementById('nextMilestone'),
  infoAchievementsCard: document.getElementById('infoAchievementsCard'),
  goalsList: document.getElementById('goalsList'),
  goalsEmpty: document.getElementById('goalsEmpty'),
  gachaResult: document.getElementById('gachaResult'),
  gachaRarityList: document.getElementById('gachaRarityList'),
  gachaOwnedSummary: document.getElementById('gachaOwnedSummary'),
  gachaSunButton: document.getElementById('gachaSunButton'),
  gachaFeaturedInfo: document.getElementById('gachaFeaturedInfo'),
  gachaTicketCounter: document.getElementById('gachaTicketCounter'),
  gachaTicketModeButton: document.getElementById('gachaTicketModeButton'),
  gachaTicketModeLabel: document.getElementById('gachaTicketModeLabel'),
  gachaTicketValue: document.getElementById('gachaTicketValue'),
  gachaAnimation: document.getElementById('gachaAnimation'),
  gachaAnimationConfetti: document.getElementById('gachaAnimationConfetti'),
  gachaContinueHint: document.getElementById('gachaContinueHint'),
  arcadeReturnButton: document.getElementById('arcadeReturnButton'),
  arcadeTicketButtons: document.querySelectorAll('[data-arcade-ticket-button]'),
  arcadeTicketValues: document.querySelectorAll('[data-arcade-ticket-value]'),
  arcadeBonusTicketButtons: document.querySelectorAll('[data-arcade-bonus-button]'),
  arcadeBonusTicketValues: document.querySelectorAll('[data-arcade-bonus-value]'),
  arcadeBonusTicketAnnouncements: document.querySelectorAll('[data-arcade-bonus-announcement]'),
  arcadeCanvas: document.getElementById('arcadeGameCanvas'),
  arcadeParticleLayer: document.getElementById('arcadeParticleLayer'),
  arcadeOverlay: document.getElementById('arcadeOverlay'),
  arcadeOverlayMessage: document.getElementById('arcadeOverlayMessage'),
  arcadeOverlayButton: document.getElementById('arcadeOverlayButton'),
  arcadeOverlayQuitButton: document.getElementById('arcadeOverlayQuitButton'),
  arcadeModeField: document.getElementById('arcadeModeField'),
  arcadeModeSwitch: document.getElementById('arcadeModeSwitch'),
  arcadeModeHint: document.getElementById('arcadeModeHint'),
  arcadeModeButtons: document.querySelectorAll('[data-arcade-mode]'),
  arcadeHubCards: document.querySelectorAll('.arcade-hub-card'),
  arcadeLevelValue: document.getElementById('arcadeLevelValue'),
  arcadeLivesValue: document.getElementById('arcadeLivesValue'),
  arcadeScoreValue: document.getElementById('arcadeScoreValue'),
  arcadeComboMessage: document.getElementById('arcadeComboMessage'),
  arcadeBrickSkinSelect: document.getElementById('arcadeBrickSkinSelect'),
  balancePage: document.getElementById('balance'),
  balanceStage: document.getElementById('balanceStage'),
  balanceBoard: document.getElementById('balanceBoard'),
  balanceSurface: document.getElementById('balanceBoardSurface'),
  balanceInventory: document.getElementById('balanceInventory'),
  balancePieces: document.getElementById('balancePieces'),
  balanceStatus: document.getElementById('balanceStatus'),
  balanceDifficultySelect: document.getElementById('balanceDifficultySelect'),
  balanceDifficultyDescription: document.getElementById('balanceDifficultyDescription'),
  balanceTestButton: document.getElementById('balanceTestButton'),
  balanceResetButton: document.getElementById('balanceResetButton'),
  balanceDragLayer: document.getElementById('balanceDragLayer'),
  waveStage: document.getElementById('waveStage'),
  waveCanvas: document.getElementById('waveCanvas'),
  waveTicketLayer: document.getElementById('waveTicketLayer'),
  waveFrenzyLayer: document.getElementById('waveFrenzyLayer'),
  waveDistanceValue: document.getElementById('waveDistanceValue'),
  waveSpeedValue: document.getElementById('waveSpeedValue'),
  waveAltitudeValue: document.getElementById('waveAltitudeValue'),
  waveApcFrenzyCounter: document.getElementById('waveApcFrenzyCounter'),
  waveApcFrenzyCounterValue: document.getElementById('waveApcFrenzyCounterValue'),
  waveApcFrenzyCounterBestSingle: document.getElementById('waveApcFrenzyCounterBestSingle'),
  waveApcFrenzyCounterBestMulti: document.getElementById('waveApcFrenzyCounterBestMulti'),
  quantum2048Board: document.getElementById('quantum2048Board'),
  quantum2048Tiles: document.getElementById('quantum2048Tiles'),
  quantum2048Grid: document.getElementById('quantum2048Grid'),
  quantum2048GoalValue: document.getElementById('quantum2048GoalValue'),
  quantum2048ParallelUniverseValue: document.getElementById('quantum2048ParallelUniverseValue'),
  quantum2048RestartButton: document.getElementById('quantum2048GoalDisplay'),
  metauxOpenButton: document.getElementById('metauxOpenButton'),
  metauxReturnButton: document.getElementById('metauxReturnButton'),
  metauxBoard: document.getElementById('metauxBoard'),
  metauxTimerLabel: document.getElementById('metauxTimerLabel'),
  metauxTimerValue: document.getElementById('metauxTimerValue'),
  metauxTimerFill: document.getElementById('metauxTimerFill'),
  metauxTimerMaxValue: document.getElementById('metauxTimerMaxValue'),
  metauxFreePlayExitButton: document.getElementById('metauxFreePlayExitButton'),
  metauxEndScreen: document.getElementById('metauxEndScreen'),
  metauxEndTimeValue: document.getElementById('metauxEndTimeValue'),
  metauxEndMatchesValue: document.getElementById('metauxEndMatchesValue'),
  metauxNewGameButton: document.getElementById('metauxNewGameButton'),
  metauxFreePlayButton: document.getElementById('metauxFreePlayButton'),
  metauxNewGameCredits: document.getElementById('metauxNewGameCredits'),
  metauxCreditStatus: document.getElementById('metauxCreditStatus'),
  languageSelect: document.getElementById('languageSelect'),
  uiScaleSelect: document.getElementById('uiScaleSelect'),
  themeSelect: document.getElementById('themeSelect'),
  textFontSelect: document.getElementById('textFontSelect'),
  digitFontSelect: document.getElementById('digitFontSelect'),
  musicTrackSelect: document.getElementById('musicTrackSelect'),
  musicTrackStatus: document.getElementById('musicTrackStatus'),
  musicVolumeSlider: document.getElementById('musicVolumeSlider'),
  optionsWelcomeTitle: document.getElementById('optionsWelcomeTitle'),
  optionsWelcomeIntro: document.getElementById('optionsWelcomeIntro'),
  openMidiModuleButton: document.getElementById('openMidiModuleButton'),
  clickSoundToggleCard: document.getElementById('clickSoundToggleCard'),
  clickSoundToggle: document.getElementById('clickSoundToggle'),
  clickSoundToggleStatus: document.getElementById('clickSoundToggleStatus'),
  critAtomToggleCard: document.getElementById('critAtomToggleCard'),
  critAtomToggle: document.getElementById('critAtomToggle'),
  critAtomToggleStatus: document.getElementById('critAtomToggleStatus'),
  optionsArcadeDetails: document.getElementById('optionsArcadeDetails'),
  brickSkinOptionCard: document.getElementById('brickSkinOptionCard'),
  brickSkinSelect: document.getElementById('brickSkinSelect'),
  brickSkinStatus: document.getElementById('brickSkinStatus'),
  resetButton: document.getElementById('resetButton'),
  bigBangOptionCard: document.getElementById('bigBangOptionCard'),
  bigBangOptionToggle: document.getElementById('bigBangNavToggle'),
  infoApsBreakdown: document.getElementById('infoApsBreakdown'),
  infoApcBreakdown: document.getElementById('infoApcBreakdown'),
  infoSessionAtoms: document.getElementById('infoSessionAtoms'),
  infoSessionClicks: document.getElementById('infoSessionClicks'),
  infoSessionApcAtoms: document.getElementById('infoSessionApcAtoms'),
  infoSessionApsAtoms: document.getElementById('infoSessionApsAtoms'),
  infoSessionOfflineAtoms: document.getElementById('infoSessionOfflineAtoms'),
  infoSessionDuration: document.getElementById('infoSessionDuration'),
  infoSessionFrenzySingle: document.getElementById('infoSessionFrenzySingle'),
  infoSessionFrenzyMulti: document.getElementById('infoSessionFrenzyMulti'),
  infoGlobalAtoms: document.getElementById('infoGlobalAtoms'),
  infoGlobalClicks: document.getElementById('infoGlobalClicks'),
  infoGlobalApcAtoms: document.getElementById('infoGlobalApcAtoms'),
  infoGlobalApsAtoms: document.getElementById('infoGlobalApsAtoms'),
  infoGlobalOfflineAtoms: document.getElementById('infoGlobalOfflineAtoms'),
  infoGlobalDuration: document.getElementById('infoGlobalDuration'),
  infoGlobalFrenzySingle: document.getElementById('infoGlobalFrenzySingle'),
  infoGlobalFrenzyMulti: document.getElementById('infoGlobalFrenzyMulti'),
  infoPhotonDistanceValue: document.getElementById('infoPhotonDistanceValue'),
  infoPhotonSpeedValue: document.getElementById('infoPhotonSpeedValue'),
  infoPhotonAltitudeValue: document.getElementById('infoPhotonAltitudeValue'),
  infoBonusSubtitle: document.getElementById('infoBonusSubtitle'),
  infoElementBonuses: document.getElementById('infoElementBonuses'),
  infoShopBonuses: document.getElementById('infoShopBonuses'),
  infoShopBonusCard: document.getElementById('infoShopBonusCard'),
  infoElementBonusCard: document.getElementById('infoElementBonusCard'),
  critAtomLayer: null,
  devkitOverlay: document.getElementById('devkitOverlay'),
  devkitPanel: document.getElementById('devkitPanel'),
  devkitClose: document.getElementById('devkitCloseButton'),
  devkitAtomsForm: document.getElementById('devkitAtomsForm'),
  devkitAtomsInput: document.getElementById('devkitAtomsInput'),
  devkitAutoForm: document.getElementById('devkitAutoForm'),
  devkitAutoInput: document.getElementById('devkitAutoInput'),
  devkitOfflineTimeForm: document.getElementById('devkitOfflineTimeForm'),
  devkitOfflineTimeInput: document.getElementById('devkitOfflineTimeInput'),
  devkitOnlineTimeForm: document.getElementById('devkitOnlineTimeForm'),
  devkitOnlineTimeInput: document.getElementById('devkitOnlineTimeInput'),
  devkitAutoStatus: document.getElementById('devkitAutoStatus'),
  devkitAutoReset: document.getElementById('devkitResetAuto'),
  devkitTicketsForm: document.getElementById('devkitTicketsForm'),
  devkitTicketsInput: document.getElementById('devkitTicketsInput'),
  devkitMach3TicketsForm: document.getElementById('devkitMach3TicketsForm'),
  devkitMach3TicketsInput: document.getElementById('devkitMach3TicketsInput'),
  devkitUnlockTrophies: document.getElementById('devkitUnlockTrophies'),
  devkitUnlockElements: document.getElementById('devkitUnlockElements'),
  devkitUnlockInfo: document.getElementById('devkitUnlockInfo'),
  devkitToggleShop: document.getElementById('devkitToggleShop'),
  devkitToggleGacha: document.getElementById('devkitToggleGacha')
};

const infoController = initializeInfoUI({
  elements,
  getPageUnlockState,
  getUnlockedTrophySet,
  achievementsTrophyId: ACHIEVEMENTS_UNLOCK_TROPHY_ID
});

function getOptionsWelcomeCardCopy() {
  const api = getI18nApi();
  if (api && typeof api.getResource === 'function') {
    const resource = api.getResource('scripts.config.uiText.options.welcomeCard');
    if (resource && typeof resource === 'object') {
      return resource;
    }
  }
  return CONFIG_OPTIONS_WELCOME_CARD;
}

function extractWelcomeIntroParagraphs(source) {
  if (!source || typeof source !== 'object') {
    return [];
  }
  const paragraphs = [];
  const appendParagraph = value => {
    if (typeof value !== 'string') {
      return;
    }
    const trimmed = value.trim();
    if (trimmed) {
      paragraphs.push(trimmed);
    }
  };
  if (Array.isArray(source.introParagraphs)) {
    source.introParagraphs.forEach(appendParagraph);
  }
  if (Array.isArray(source.intro)) {
    source.intro.forEach(appendParagraph);
  } else if (typeof source.intro === 'string') {
    appendParagraph(source.intro);
  }
  return paragraphs;
}

function extractWelcomeDetails(source) {
  if (!source || typeof source !== 'object') {
    return [];
  }
  const rawDetails = Array.isArray(source.unlockedDetails)
    ? source.unlockedDetails
    : Array.isArray(source.details)
      ? source.details
      : source.details && typeof source.details === 'object'
        ? Object.values(source.details)
        : [];
  return rawDetails
    .map(entry => {
      if (!entry || typeof entry !== 'object') {
        return null;
      }
      const label = typeof entry.label === 'string' ? entry.label.trim() : '';
      const description = typeof entry.description === 'string' ? entry.description.trim() : '';
      if (!label && !description) {
        return null;
      }
      return {
        label,
        description
      };
    })
    .filter(Boolean);
}

function renderOptionsWelcomeContent() {
  const copy = getOptionsWelcomeCardCopy();
  const fallbackCopy = CONFIG_OPTIONS_WELCOME_CARD;

  if (elements.optionsWelcomeTitle) {
    const fallbackTitle = fallbackCopy && typeof fallbackCopy.title === 'string'
      ? fallbackCopy.title
      : 'Bienvenue';
    const titleText = copy && typeof copy.title === 'string' && copy.title.trim()
      ? copy.title.trim()
      : translateOrDefault('scripts.config.uiText.options.welcomeCard.title', fallbackTitle);
    elements.optionsWelcomeTitle.textContent = titleText;
  }

  if (elements.optionsWelcomeIntro) {
    const container = elements.optionsWelcomeIntro;
    container.innerHTML = '';

    const fallbackParagraphs = extractWelcomeIntroParagraphs(fallbackCopy);
    let paragraphs = extractWelcomeIntroParagraphs(copy);
    if (!paragraphs.length) {
      const translatedIntro = translateOrDefault('scripts.config.uiText.options.welcomeCard.intro', '');
      if (translatedIntro) {
        paragraphs = [translatedIntro];
      } else if (fallbackParagraphs.length) {
        paragraphs = [...fallbackParagraphs];
      }
    }

    paragraphs.forEach(paragraph => {
      if (typeof paragraph !== 'string' || !paragraph.trim()) {
        return;
      }
      const node = document.createElement('p');
      node.textContent = paragraph.trim();
      container.appendChild(node);
    });
  }

  if (elements.optionsArcadeDetails) {
    const container = elements.optionsArcadeDetails;
    container.innerHTML = '';

    const fallbackDetails = extractWelcomeDetails(fallbackCopy);
    let details = extractWelcomeDetails(copy);
    if (!details.length && fallbackDetails.length) {
      details = [...fallbackDetails];
    }

    details.forEach(detail => {
      if (!detail) {
        return;
      }
      const { label, description } = detail;
      if (!label && !description) {
        return;
      }
      const paragraph = document.createElement('p');
      if (label) {
        const strong = document.createElement('strong');
        strong.textContent = label;
        paragraph.appendChild(strong);
        if (description) {
          paragraph.appendChild(document.createTextNode(` ${description}`));
        }
      } else {
        paragraph.textContent = description;
      }
      container.appendChild(paragraph);
    });
  }
}

function refreshOptionsWelcomeContent() {
  renderOptionsWelcomeContent();
  updateOptionsIntroDetails();
}

function subscribeOptionsWelcomeContentUpdates() {
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(() => {
      refreshOptionsWelcomeContent();
    });
    return;
  }
  if (typeof globalThis !== 'undefined'
    && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', () => {
      refreshOptionsWelcomeContent();
    });
  }
}

refreshOptionsWelcomeContent();
subscribeOptionsWelcomeContentUpdates();
renderThemeOptions();

function updateLanguageSelectorValue(language) {
  if (!elements.languageSelect) {
    return;
  }
  const resolved = resolveLanguageCode(language);
  if (elements.languageSelect.value !== resolved) {
    elements.languageSelect.value = resolved;
  }
}

function populateLanguageSelectOptions() {
  if (!elements.languageSelect) {
    return;
  }
  const select = elements.languageSelect;
  const previousSelection = select.value;
  select.innerHTML = '';
  AVAILABLE_LANGUAGE_CODES.forEach(code => {
    const option = document.createElement('option');
    option.value = code;
    option.setAttribute('data-i18n', `index.sections.options.language.options.${code}`);
    const fallback = LANGUAGE_OPTION_FALLBACK_LABELS[code] || code.toUpperCase();
    option.textContent = translateOrDefault(
      `index.sections.options.language.options.${code}`,
      fallback
    );
    select.appendChild(option);
  });
  const i18n = globalThis.i18n;
  if (i18n && typeof i18n.updateTranslations === 'function') {
    i18n.updateTranslations(select);
  }
  const desiredSelection = AVAILABLE_LANGUAGE_CODES.includes(previousSelection)
    ? previousSelection
    : getInitialLanguagePreference();
  updateLanguageSelectorValue(desiredSelection);
}

function updateBrickSkinOption() {
  const unlocked = isArcadeUnlocked();
  const selection = normalizeBrickSkinSelection(gameState.arcadeBrickSkin);
  const unlockedMessage = translateOrDefault(
    'scripts.app.options.brickSkin.unlocked',
    'Choisissez l’apparence des briques de Particules.'
  );
  const lockedMessage = translateOrDefault(
    'index.sections.options.brickSkin.note',
    'Débloquez le trophée « Ruée vers le million » pour personnaliser vos briques.'
  );

  if (elements.brickSkinOptionCard) {
    elements.brickSkinOptionCard.hidden = !unlocked;
    elements.brickSkinOptionCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }

  if (elements.brickSkinSelect) {
    elements.brickSkinSelect.disabled = !unlocked;
    if (elements.brickSkinSelect.value !== selection) {
      elements.brickSkinSelect.value = selection;
    }
  }

  if (elements.arcadeBrickSkinSelect) {
    elements.arcadeBrickSkinSelect.disabled = !unlocked;
    if (elements.arcadeBrickSkinSelect.value !== selection) {
      elements.arcadeBrickSkinSelect.value = selection;
    }
    elements.arcadeBrickSkinSelect.title = unlocked ? '' : lockedMessage;
  }

  if (elements.brickSkinStatus) {
    elements.brickSkinStatus.textContent = unlocked ? unlockedMessage : lockedMessage;
  }
}

function updateOptionsIntroDetails() {
  if (!elements.optionsArcadeDetails) {
    return;
  }
  const unlocked = isArcadeUnlocked();
  elements.optionsArcadeDetails.hidden = !unlocked;
  elements.optionsArcadeDetails.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function commitBrickSkinSelection(rawValue) {
  const selection = normalizeBrickSkinSelection(rawValue);
  if (!isArcadeUnlocked()) {
    updateBrickSkinOption();
    return;
  }
  const previous = normalizeBrickSkinSelection(gameState.arcadeBrickSkin);
  if (selection !== previous) {
    gameState.arcadeBrickSkin = selection;
    if (typeof setParticulesBrickSkinPreference === 'function') {
      setParticulesBrickSkinPreference(selection);
    }
    saveGame();
    const messageKey = BRICK_SKIN_TOAST_KEYS[selection] || 'scripts.app.brickSkins.applied.generic';
    showToast(t(messageKey));
  }
  updateBrickSkinOption();
}

function ensureWaveGame() {
  if (waveGame || typeof WaveGame !== 'function') {
    return waveGame;
  }
  if (!elements.waveCanvas) {
    return null;
  }
  waveGame = new WaveGame({
    canvas: elements.waveCanvas,
    stage: elements.waveStage,
    distanceElement: elements.waveDistanceValue,
    speedElement: elements.waveSpeedValue,
    altitudeElement: elements.waveAltitudeValue
  });
  return waveGame;
}

function ensureBalanceGame() {
  if (balanceGame || typeof BalanceGame !== 'function') {
    return balanceGame;
  }
  if (!elements.balanceBoard) {
    return null;
  }
  balanceGame = new BalanceGame({
    pageElement: elements.balancePage,
    stageElement: elements.balanceStage,
    boardElement: elements.balanceBoard,
    surfaceElement: elements.balanceSurface,
    inventoryElement: elements.balancePieces,
    statusElement: elements.balanceStatus,
    difficultySelect: elements.balanceDifficultySelect,
    difficultyDescription: elements.balanceDifficultyDescription,
    testButton: elements.balanceTestButton,
    resetButton: elements.balanceResetButton,
    dragLayer: elements.balanceDragLayer
  });
  return balanceGame;
}

function ensureQuantum2048Game() {
  if (quantum2048Game || typeof Quantum2048Game !== 'function') {
    return quantum2048Game;
  }
  if (!elements.quantum2048Board) {
    return null;
  }
  quantum2048Game = new Quantum2048Game({
    boardElement: elements.quantum2048Board,
    tilesContainer: elements.quantum2048Tiles,
    backgroundContainer: elements.quantum2048Grid,
    goalElement: elements.quantum2048GoalValue,
    parallelUniverseElement: elements.quantum2048ParallelUniverseValue,
    restartButton: elements.quantum2048RestartButton
  });
  return quantum2048Game;
}

function updatePageUnlockUI() {
  const unlocks = getPageUnlockState();
  const buttonConfig = [
    ['gacha', elements.navGachaButton],
    ['tableau', elements.navTableButton],
    ['fusion', elements.navFusionButton]
  ];

  buttonConfig.forEach(([pageId, button]) => {
    const unlocked = unlocks?.[pageId] === true;
    setNavButtonLockState(button, unlocked);
  });

  setNavButtonLockState(elements.navInfoButton, true);
  infoController.updateBonusVisibility();

  ensureActivePageUnlocked();
}

function updatePrimaryNavigationLocks() {
  const atoms = gameState.atoms instanceof LayeredNumber
    ? gameState.atoms
    : toLayeredValue(gameState.atoms, 0);
  const shopUnlocked = atoms.compare(SHOP_UNLOCK_THRESHOLD) >= 0;
  setNavButtonLockState(elements.navShopButton, shopUnlocked);
  shopController.updateUnlockHint();

  infoController.updateAchievementsVisibility();

  ensureActivePageUnlocked();
}

function isBigBangTrophyUnlocked() {
  return getUnlockedTrophySet().has(BIG_BANG_TROPHY_ID);
}

function updateBigBangVisibility() {
  const unlocked = isBigBangTrophyUnlocked();
  if (!unlocked && gameState.bigBangButtonVisible) {
    gameState.bigBangButtonVisible = false;
  }
  if (!elements.bigBangOptionToggle && unlocked && !gameState.bigBangButtonVisible) {
    gameState.bigBangButtonVisible = true;
  }
  if (elements.bigBangOptionCard) {
    elements.bigBangOptionCard.hidden = !unlocked;
  }
  if (elements.bigBangOptionToggle) {
    elements.bigBangOptionToggle.disabled = !unlocked;
    elements.bigBangOptionToggle.checked = unlocked && gameState.bigBangButtonVisible === true;
  }
  const shouldShowButton = unlocked
    && (elements.bigBangOptionToggle ? gameState.bigBangButtonVisible === true : true);
  if (elements.navBigBangButton) {
    elements.navBigBangButton.toggleAttribute('hidden', !shouldShowButton);
    elements.navBigBangButton.setAttribute('aria-hidden', shouldShowButton ? 'false' : 'true');
  }
  if (!shouldShowButton && document.body && document.body.dataset.activePage === 'bigbang') {
    showPage('game');
  }
}

function isArcadeUnlocked() {
  return getUnlockedTrophySet().has(ARCADE_TROPHY_ID);
}

const soundEffects = (() => {
  let popMuted = false;
  const createSilentPool = () => ({ play: () => {} });
  if (typeof window === 'undefined' || typeof Audio === 'undefined') {
    return {
      pop: createSilentPool(),
      crit: createSilentPool(),
      setPopMuted(value) {
        popMuted = !!value;
      },
      isPopMuted() {
        return popMuted;
      }
    };
  }

  const updatePitchPreservation = (audio, shouldPreserve) => {
    const preserve = !!shouldPreserve;
    if ('preservesPitch' in audio) {
      audio.preservesPitch = preserve;
    }
    if ('mozPreservesPitch' in audio) {
      audio.mozPreservesPitch = preserve;
    }
    if ('webkitPreservesPitch' in audio) {
      audio.webkitPreservesPitch = preserve;
    }
  };

  const createSoundPool = (src, poolSize = 4) => {
    const size = Math.max(1, Math.floor(poolSize) || 1);
    const pool = Array.from({ length: size }, () => {
      const audio = new Audio(src);
      audio.preload = 'auto';
      audio.setAttribute('preload', 'auto');
      return audio;
    });
    let index = 0;
    return {
      play(playbackRate = 1) {
        const audio = pool[index];
        index = (index + 1) % pool.length;
        try {
          audio.currentTime = 0;
          if (audio.playbackRate !== playbackRate) {
            audio.playbackRate = playbackRate;
          }
          const shouldPreservePitch = Math.abs(playbackRate - 1) < 1e-3;
          updatePitchPreservation(audio, shouldPreservePitch);
          const playPromise = audio.play();
          if (playPromise && typeof playPromise.catch === 'function') {
            playPromise.catch(() => {});
          }
        } catch (error) {
          // Ignore playback issues (e.g. autoplay restrictions)
        }
      }
    };
  };

  const POP_SOUND_SRC = 'Assets/Sounds/pop.mp3';
  const CRIT_PLAYBACK_RATE = 1.35;

  const popPool = createSoundPool(POP_SOUND_SRC, 6);
  const critPool = createSoundPool(POP_SOUND_SRC, 3);

  const effects = {
    pop: {
      play: () => {
        if (!popMuted) {
          popPool.play(1);
        }
      }
    },
    crit: {
      play: () => {
        if (!popMuted) {
          critPool.play(CRIT_PLAYBACK_RATE);
        }
      }
    },
    setPopMuted(value) {
      popMuted = !!value;
    },
    isPopMuted() {
      return popMuted;
    }
  };

  return effects;
})();

function readStoredClickSoundMuted() {
  const stored = readString(CLICK_SOUND_STORAGE_KEY);
  if (stored == null) {
    return null;
  }
  if (stored === '1' || stored === 'true') {
    return true;
  }
  if (stored === '0' || stored === 'false') {
    return false;
  }
  return null;
}

function writeStoredClickSoundMuted(muted) {
  const value = muted ? '1' : '0';
  writeString(CLICK_SOUND_STORAGE_KEY, value);
}

function readStoredCritAtomVisualsDisabled() {
  const stored = readString(CRIT_ATOM_VISUALS_STORAGE_KEY);
  if (stored == null) {
    return null;
  }
  if (stored === '1' || stored === 'true') {
    return true;
  }
  if (stored === '0' || stored === 'false') {
    return false;
  }
  return null;
}

function writeStoredCritAtomVisualsDisabled(disabled) {
  const value = disabled ? '1' : '0';
  writeString(CRIT_ATOM_VISUALS_STORAGE_KEY, value);
}

function updateClickSoundStatusLabel(muted) {
  if (!elements.clickSoundToggleStatus) {
    return;
  }
  const key = muted
    ? 'index.sections.options.clickSound.state.off'
    : 'index.sections.options.clickSound.state.on';
  const fallback = muted ? 'Click sounds off' : 'Click sounds on';
  elements.clickSoundToggleStatus.setAttribute('data-i18n', key);
  elements.clickSoundToggleStatus.textContent = translateOrDefault(key, fallback);
}

function applyClickSoundMuted(muted, options = {}) {
  const value = !!muted;
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  if (soundEffects && typeof soundEffects.setPopMuted === 'function') {
    soundEffects.setPopMuted(value);
  }
  if (settings.updateControl && elements.clickSoundToggle) {
    elements.clickSoundToggle.checked = !value;
  }
  updateClickSoundStatusLabel(value);
  if (settings.persist) {
    writeStoredClickSoundMuted(value);
  }
}

function initClickSoundOption() {
  if (!elements.clickSoundToggle) {
    return;
  }
  const stored = readStoredClickSoundMuted();
  const initialMuted = stored === null ? false : stored === true;
  applyClickSoundMuted(initialMuted, { persist: false, updateControl: true });
  elements.clickSoundToggle.addEventListener('change', () => {
    const muted = !elements.clickSoundToggle.checked;
    applyClickSoundMuted(muted, { persist: true, updateControl: false });
  });
}

function subscribeClickSoundLanguageUpdates() {
  const handler = () => {
    const muted = soundEffects && typeof soundEffects.isPopMuted === 'function'
      ? soundEffects.isPopMuted()
      : false;
    updateClickSoundStatusLabel(muted);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function updateCritAtomStatusLabel(disabled) {
  if (!elements.critAtomToggleStatus) {
    return;
  }
  const key = disabled
    ? 'index.sections.options.critAtoms.state.off'
    : 'index.sections.options.critAtoms.state.on';
  const fallback = disabled ? 'Critical atom bursts off' : 'Critical atom bursts on';
  elements.critAtomToggleStatus.setAttribute('data-i18n', key);
  elements.critAtomToggleStatus.textContent = translateOrDefault(key, fallback);
}

function applyCritAtomVisualsDisabled(disabled, options = {}) {
  const value = !!disabled;
  const settings = Object.assign({ persist: true, updateControl: true, clearExisting: true }, options);
  critAtomVisualsDisabled = value;
  if (settings.updateControl && elements.critAtomToggle) {
    elements.critAtomToggle.checked = !value;
  }
  updateCritAtomStatusLabel(value);
  if (value && settings.clearExisting && elements.critAtomLayer) {
    const layer = elements.critAtomLayer;
    if (layer && typeof layer.querySelectorAll === 'function') {
      layer.querySelectorAll('.crit-atom').forEach(atom => {
        if (atom && typeof atom.remove === 'function') {
          atom.remove();
        }
      });
    }
    if (layer && layer.isConnected && typeof layer.remove === 'function') {
      layer.remove();
    }
    elements.critAtomLayer = null;
  }
  if (settings.persist) {
    writeStoredCritAtomVisualsDisabled(value);
  }
}

function initCritAtomOption() {
  const stored = readStoredCritAtomVisualsDisabled();
  const initialDisabled = stored === null ? false : stored === true;
  applyCritAtomVisualsDisabled(initialDisabled, {
    persist: false,
    updateControl: Boolean(elements.critAtomToggle)
  });
  if (!elements.critAtomToggle) {
    return;
  }
  elements.critAtomToggle.addEventListener('change', () => {
    const disabled = !elements.critAtomToggle.checked;
    applyCritAtomVisualsDisabled(disabled, { persist: true, updateControl: false });
  });
}

function subscribeCritAtomLanguageUpdates() {
  const handler = () => {
    updateCritAtomStatusLabel(critAtomVisualsDisabled);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

initializeUiOptions({ elements, globalConfig: GLOBAL_CONFIG });
const collectionsController = initializeCollectionsUI({
  elements,
  gameState,
  translateOrDefault,
  t,
  formatNumberLocalized,
  formatIntegerLocalized,
  getI18nApi,
  periodicElements,
  periodicElementIndex,
  elementRarityIndex,
  gachaRarityMap: GACHA_RARITY_MAP,
  categoryLabels: CATEGORY_LABELS,
  totalElementCount: TOTAL_ELEMENT_COUNT,
  getCollectionBonusOverview,
  normalizeCollectionDetailText,
  getElementCurrentCount,
  getElementLifetimeCount,
  hasElementLifetime,
  applyPeriodicCellCollectionColor,
  updateGachaRarityProgress
});
const fusionController = initializeFusionUI({ elements });
const goalsController = initializeGoalsUI({
  elements,
  trophyDefs: TROPHY_DEFS,
  milestoneList,
  gameState,
  translateOrDefault,
  t,
  formatNumberLocalized,
  formatTrophyBonusValue,
  getUnlockedTrophySet,
  areAchievementsFeatureUnlocked: infoController.areAchievementsFeatureUnlocked
});
const shopController = initializeShopUI({
  elements,
  upgradeDefs: UPGRADE_DEFS,
  upgradeIndexMap: UPGRADE_INDEX_MAP,
  gameState,
  translateOrDefault,
  translate: (key, params, fallback) => translateMessage(key, params, fallback),
  formatIntegerLocalized,
  formatShopCost,
  getUpgradeLevel,
  resolveUpgradeMaxLevel,
  getRemainingUpgradeCapacity,
  computeUpgradeCost,
  getShopUnlockSet,
  isDevKitShopFree,
  recalcProduction,
  updateUI,
  showToast,
  toLayeredValue
});
const arcadeController = initializeArcadeUI({
  elements,
  gameState,
  translate: (key, params, fallback) => translateMessage(key, params, fallback),
  showToast,
  showPage,
  saveGame,
  formatIntegerLocalized,
  setNavButtonLockState,
  isPageUnlocked,
  unlockPage,
  isArcadeUnlocked,
  ensureWaveGame,
  ensureBalanceGame,
  ensureQuantum2048Game,
  ensureApsCritState,
  getApsCritMultiplier,
  getApsCritRemainingSeconds,
  recalcProduction,
  updateUI,
  pulseApsCritPanel
});
const {
  updateArcadeTicketDisplay,
  updateBrandPortalState,
  updateMetauxCreditsUI,
  handleMetauxSessionEnd
} = arcadeController;
initClickSoundOption();
subscribeClickSoundLanguageUpdates();
initCritAtomOption();
subscribeCritAtomLanguageUpdates();

if (typeof window !== 'undefined') {
  window.registerSudokuOfflineBonus = registerSudokuOfflineBonus;
  window.registerChessVictoryReward = registerChessVictoryReward;
}

const musicPlayer = createMusicPlayer({
  supportedExtensions: MUSIC_SUPPORTED_EXTENSIONS,
  fallbackTracks: MUSIC_FALLBACK_TRACKS,
  defaultVolume: DEFAULT_MUSIC_VOLUME,
  compareText: (a, b, options) => compareTextLocalized(a, b, options)
});

function updateMusicSelectOptions() {
  const select = elements.musicTrackSelect;
  if (!select) {
    return;
  }
  const tracks = musicPlayer.getTracks();
  const current = musicPlayer.getCurrentTrack();
  const previousValue = select.value;
  select.innerHTML = '';
  if (!tracks.length) {
    const option = document.createElement('option');
    option.value = '';
    option.textContent = t('scripts.app.music.noneAvailable');
    select.appendChild(option);
    select.disabled = true;
    return;
  }
  const noneOption = document.createElement('option');
  noneOption.value = '';
  noneOption.textContent = t('scripts.app.music.noneOption');
  select.appendChild(noneOption);
  tracks.forEach(track => {
    const option = document.createElement('option');
    option.value = track.id;
    option.textContent = track.placeholder
      ? t('scripts.app.music.missingDisplay', { name: track.displayName })
      : track.displayName;
    option.dataset.placeholder = track.placeholder ? 'true' : 'false';
    select.appendChild(option);
  });
  let valueToSelect = current?.id || '';
  if (!valueToSelect && previousValue) {
    if (previousValue === '' || previousValue === 'none') {
      valueToSelect = '';
    } else if (tracks.some(track => track.id === previousValue)) {
      valueToSelect = previousValue;
    }
  }
  if (!valueToSelect
    && gameState.musicTrackId
    && gameState.musicEnabled !== false
    && tracks.some(track => track.id === gameState.musicTrackId)) {
    valueToSelect = gameState.musicTrackId;
  }
  select.value = valueToSelect;
  select.disabled = false;
}

function updateMusicStatus() {
  const status = elements.musicTrackStatus;
  if (!status) {
    return;
  }
  const tracks = musicPlayer.getTracks();
  if (!tracks.length) {
    status.textContent = t('scripts.app.music.addFilesHint');
    return;
  }
  const current = musicPlayer.getCurrentTrack();
  if (!current) {
    if (gameState.musicEnabled === false) {
      status.textContent = t('scripts.app.music.disabled');
    } else {
      status.textContent = t('scripts.app.music.selectTrack');
    }
    return;
  }
  const playbackState = musicPlayer.getPlaybackState();
  let message = t('scripts.app.music.looping', { track: current.displayName });
  if (current.placeholder) {
    message += t('scripts.app.music.missingSuffix');
  }
  if (playbackState === 'unsupported') {
    message += t('scripts.app.music.unsupportedSuffix');
  } else if (playbackState === 'error') {
    message += t('scripts.app.music.errorSuffix');
  } else if (musicPlayer.isAwaitingUserGesture()) {
    message += t('scripts.app.music.awaitingInteractionSuffix');
  } else if (playbackState === 'paused' || playbackState === 'idle') {
    message += t('scripts.app.music.pausedSuffix');
  }
  status.textContent = message;
}

function updateMusicVolumeControl() {
  const slider = elements.musicVolumeSlider;
  if (!slider) {
    return;
  }
  const volume = typeof musicPlayer.getVolume === 'function'
    ? musicPlayer.getVolume()
    : DEFAULT_MUSIC_VOLUME;
  const clamped = Math.round(Math.min(100, Math.max(0, volume * 100)));
  slider.value = String(clamped);
  slider.setAttribute('aria-valuenow', String(clamped));
  slider.setAttribute('aria-valuetext', `${clamped}%`);
  slider.title = t('scripts.app.music.volumeLabel', { value: clamped });
  const playbackState = musicPlayer.getPlaybackState();
  slider.disabled = playbackState === 'unsupported';
}

function refreshMusicControls() {
  updateMusicSelectOptions();
  updateMusicStatus();
  updateMusicVolumeControl();
}

musicPlayer.onChange(event => {
  if (event?.currentTrack && event.currentTrack.id) {
    gameState.musicTrackId = event.currentTrack.id;
    gameState.musicEnabled = true;
  } else if (event?.type === 'stop') {
    gameState.musicTrackId = null;
    gameState.musicEnabled = false;
  } else if (Array.isArray(event?.tracks) && event.tracks.length === 0) {
    gameState.musicTrackId = null;
    gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
  }

  if (event?.type === 'volume') {
    gameState.musicVolume = musicPlayer.getVolume();
  }

  if (event?.type === 'tracks'
    || event?.type === 'track'
    || event?.type === 'state'
    || event?.type === 'error'
    || event?.type === 'volume'
    || event?.type === 'stop') {
    refreshMusicControls();
  }
});

const DEVKIT_AUTO_LABEL = 'DevKit (APS +)';

function parseDevKitLayeredInput(raw) {
  if (raw instanceof LayeredNumber) {
    return raw.clone();
  }
  if (raw == null) {
    return null;
  }
  const normalized = String(raw)
    .trim()
    .replace(/,/g, '.')
    .replace(/\s+/g, '');
  if (!normalized) {
    return null;
  }
  const powMatch = normalized.match(/^10\^([+-]?\d+)$/);
  if (powMatch) {
    const exponent = Number(powMatch[1]);
    if (Number.isFinite(exponent)) {
      return LayeredNumber.fromLayer0(1, exponent);
    }
  }
  const sciMatch = normalized.match(/^([+-]?\d+(?:\.\d+)?)e([+-]?\d+)$/i);
  if (sciMatch) {
    const mantissa = Number(sciMatch[1]);
    const exponent = Number(sciMatch[2]);
    if (Number.isFinite(mantissa) && Number.isFinite(exponent)) {
      return LayeredNumber.fromLayer0(mantissa, exponent);
    }
  }
  const numeric = Number(normalized);
  if (Number.isFinite(numeric)) {
    return new LayeredNumber(numeric);
  }
  return null;
}

function parseDevKitDurationInput(raw) {
  if (raw == null) {
    return null;
  }
  const normalized = String(raw)
    .trim()
    .replace(/,/g, '.')
    .replace(/\s+/g, '');
  if (!normalized) {
    return null;
  }
  const match = normalized.match(/^([+-]?\d+(?:\.\d+)?)([a-zA-Z]*)$/);
  if (!match) {
    return null;
  }
  const value = Number(match[1]);
  if (!Number.isFinite(value)) {
    return null;
  }
  const unitRaw = match[2].toLowerCase();
  const unitMap = new Map([
    ['', 3600],
    ['h', 3600],
    ['hr', 3600],
    ['hrs', 3600],
    ['hour', 3600],
    ['hours', 3600],
    ['heure', 3600],
    ['heures', 3600],
    ['d', 86400],
    ['day', 86400],
    ['days', 86400],
    ['jour', 86400],
    ['jours', 86400],
    ['m', 60],
    ['mn', 60],
    ['min', 60],
    ['mins', 60],
    ['minute', 60],
    ['minutes', 60],
    ['s', 1],
    ['sec', 1],
    ['secs', 1],
    ['second', 1],
    ['seconds', 1],
    ['seconde', 1],
    ['secondes', 1]
  ]);
  const factor = unitMap.get(unitRaw);
  if (!factor) {
    return null;
  }
  const seconds = value * factor;
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return null;
  }
  return seconds;
}

function formatDevKitDuration(seconds) {
  const numeric = Number(seconds);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return formatDurationLocalized(0, { style: 'unit', unit: 'second', unitDisplay: 'short' });
  }
  const absSeconds = Math.abs(numeric);
  if (absSeconds >= 86400) {
    const days = absSeconds / 86400;
    const options = days < 10
      ? { style: 'unit', unit: 'day', unitDisplay: 'short', minimumFractionDigits: 2, maximumFractionDigits: 2 }
      : { style: 'unit', unit: 'day', unitDisplay: 'short', maximumFractionDigits: 1 };
    return formatDurationLocalized(days, options);
  }
  if (absSeconds >= 3600) {
    const hours = absSeconds / 3600;
    const options = hours < 10
      ? { style: 'unit', unit: 'hour', unitDisplay: 'short', minimumFractionDigits: 2, maximumFractionDigits: 2 }
      : { style: 'unit', unit: 'hour', unitDisplay: 'short', maximumFractionDigits: 1 };
    return formatDurationLocalized(hours, options);
  }
  if (absSeconds >= 60) {
    const minutes = absSeconds / 60;
    return formatDurationLocalized(minutes, {
      style: 'unit',
      unit: 'minute',
      unitDisplay: 'short',
      minimumFractionDigits: 1,
      maximumFractionDigits: 1
    });
  }
  return formatDurationLocalized(absSeconds, { style: 'unit', unit: 'second', unitDisplay: 'short', maximumFractionDigits: 0 });
}

function formatSudokuRewardDuration(seconds) {
  const numeric = Number(seconds);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return formatDurationLocalized(0, { style: 'unit', unit: 'second', unitDisplay: 'short', maximumFractionDigits: 0 });
  }
  const absSeconds = Math.abs(numeric);
  if (absSeconds >= 3600) {
    const hours = absSeconds / 3600;
    const options = hours < 10
      ? { style: 'unit', unit: 'hour', unitDisplay: 'short', minimumFractionDigits: 1, maximumFractionDigits: 1 }
      : { style: 'unit', unit: 'hour', unitDisplay: 'short', maximumFractionDigits: 0 };
    return formatDurationLocalized(hours, options);
  }
  if (absSeconds >= 60) {
    const minutes = absSeconds / 60;
    const options = minutes < 10
      ? { style: 'unit', unit: 'minute', unitDisplay: 'short', minimumFractionDigits: 1, maximumFractionDigits: 1 }
      : { style: 'unit', unit: 'minute', unitDisplay: 'short', maximumFractionDigits: 0 };
    return formatDurationLocalized(minutes, options);
  }
  return formatDurationLocalized(absSeconds, { style: 'unit', unit: 'second', unitDisplay: 'short', maximumFractionDigits: 0 });
}

function parseDevKitInteger(raw) {
  if (raw == null) {
    return null;
  }
  const normalized = String(raw)
    .trim()
    .replace(/\s+/g, '');
  if (!normalized) {
    return null;
  }
  const numeric = Number(normalized);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  return Math.floor(numeric);
}

function updateDevKitUI() {
  if (elements.devkitAutoStatus) {
    const bonus = getDevKitAutoFlatBonus();
    const text = bonus instanceof LayeredNumber && !bonus.isZero()
      ? bonus.toString()
      : '0';
    elements.devkitAutoStatus.textContent = text;
  }
  if (elements.devkitToggleShop) {
    const active = isDevKitShopFree();
    elements.devkitToggleShop.dataset.active = active ? 'true' : 'false';
    elements.devkitToggleShop.setAttribute('aria-pressed', active ? 'true' : 'false');
    elements.devkitToggleShop.textContent = `Magasin gratuit : ${active ? 'activé' : 'désactivé'}`;
  }
  if (elements.devkitToggleGacha) {
    const active = isDevKitGachaFree();
    elements.devkitToggleGacha.dataset.active = active ? 'true' : 'false';
    elements.devkitToggleGacha.setAttribute('aria-pressed', active ? 'true' : 'false');
    elements.devkitToggleGacha.textContent = `Tirages gratuits : ${active ? 'activés' : 'désactivés'}`;
  }
  if (elements.devkitUnlockInfo) {
    const unlocked = infoController.areInfoBonusesUnlocked();
    elements.devkitUnlockInfo.disabled = unlocked;
    elements.devkitUnlockInfo.setAttribute('aria-disabled', unlocked ? 'true' : 'false');
    const i18nKey = unlocked
      ? 'index.sections.devkit.unlocks.infoUnlocked'
      : 'index.sections.devkit.unlocks.infoLocked';
    elements.devkitUnlockInfo.setAttribute('data-i18n', i18nKey);
    elements.devkitUnlockInfo.textContent = t(i18nKey);
  }
}

function focusDevKitDefault() {
  const target = elements.devkitAtomsInput || elements.devkitPanel;
  if (!target) return;
  requestAnimationFrame(() => {
    try {
      target.focus({ preventScroll: true });
    } catch (error) {
      target.focus();
    }
  });
}

function openDevKit() {
  if (DEVKIT_STATE.isOpen || !elements.devkitOverlay) {
    return;
  }
  DEVKIT_STATE.isOpen = true;
  DEVKIT_STATE.lastFocusedElement = document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  elements.devkitOverlay.hidden = false;
  elements.devkitOverlay.setAttribute('aria-hidden', 'false');
  document.body.classList.add('devkit-open');
  updateDevKitUI();
  focusDevKitDefault();
}

function closeDevKit() {
  if (!DEVKIT_STATE.isOpen || !elements.devkitOverlay) {
    return;
  }
  DEVKIT_STATE.isOpen = false;
  elements.devkitOverlay.hidden = true;
  elements.devkitOverlay.setAttribute('aria-hidden', 'true');
  document.body.classList.remove('devkit-open');
  if (DEVKIT_STATE.lastFocusedElement && typeof DEVKIT_STATE.lastFocusedElement.focus === 'function') {
    DEVKIT_STATE.lastFocusedElement.focus();
  }
  DEVKIT_STATE.lastFocusedElement = null;
}

function toggleDevKit() {
  if (DEVKIT_STATE.isOpen) {
    closeDevKit();
  } else {
    openDevKit();
  }
}

function handleDevKitAtomsSubmission(value) {
  const amount = parseDevKitLayeredInput(value);
  if (!(amount instanceof LayeredNumber) || amount.isZero() || amount.sign <= 0) {
    showToast(t('scripts.app.devkit.invalidAtoms'));
    return;
  }
  gainAtoms(amount);
  updateUI();
  saveGame();
  updateDevKitUI();
  showToast(t('scripts.app.devkit.atomsAdded', { amount: amount.toString() }));
}

function handleDevKitAutoSubmission(value) {
  const amount = parseDevKitLayeredInput(value);
  if (!(amount instanceof LayeredNumber) || amount.isZero() || amount.sign <= 0) {
    showToast(t('scripts.app.devkit.invalidAps'));
    return;
  }
  const nextBonus = getDevKitAutoFlatBonus().add(amount);
  setDevKitAutoFlatBonus(nextBonus);
  recalcProduction();
  updateUI();
  saveGame();
  updateDevKitUI();
  showToast(t('scripts.app.devkit.apsAdded', { amount: amount.toString() }));
}

function resetDevKitAutoBonus() {
  if (getDevKitAutoFlatBonus().isZero()) {
    showToast(t('scripts.app.devkit.noApsToReset'));
    return;
  }
  setDevKitAutoFlatBonus(LayeredNumber.zero());
  recalcProduction();
  updateUI();
  saveGame();
  updateDevKitUI();
  showToast(t('scripts.app.devkit.apsReset'));
}

function handleDevKitTicketSubmission(value) {
  const numeric = parseDevKitInteger(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    showToast(t('scripts.app.devkit.invalidTickets'));
    return;
  }
  const gained = gainGachaTickets(numeric);
  updateUI();
  saveGame();
  showToast(gained === 1
    ? t('scripts.app.devkit.ticketAdded.single')
    : t('scripts.app.devkit.ticketAdded.multiple', { count: gained }));
}

function handleDevKitMach3TicketSubmission(value) {
  const numeric = parseDevKitInteger(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    showToast(t('scripts.app.devkit.invalidMach3Tickets'));
    return;
  }
  const gained = gainBonusParticulesTickets(numeric);
  updateUI();
  saveGame();
  showToast(gained === 1
    ? t('scripts.app.devkit.mach3TicketAdded.single')
    : t('scripts.app.devkit.mach3TicketAdded.multiple', { count: gained }));
}

function handleDevKitOfflineAdvance(value) {
  const seconds = parseDevKitDurationInput(value);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    showToast(t('scripts.app.devkit.invalidTime'));
    return;
  }

  const result = applyOfflineProgress(seconds, { announceAtoms: false, announceTickets: false });

  const atomsGained = result.atomsGained instanceof LayeredNumber ? result.atomsGained : null;
  if (atomsGained && !atomsGained.isZero()) {
    showToast(t('scripts.app.offline.progressAtoms', { amount: atomsGained.toString() }));
  }

  if (result.ticketsEarned > 0) {
    const unitKey = result.ticketsEarned === 1
      ? 'scripts.app.offlineTickets.ticketSingular'
      : 'scripts.app.offlineTickets.ticketPlural';
    const unit = t(unitKey);
    showToast(t('scripts.app.offline.tickets', { count: result.ticketsEarned, unit }));
  }

  const appliedSeconds = result.appliedSeconds > 0 ? result.appliedSeconds : seconds;
  const durationText = formatDevKitDuration(appliedSeconds);

  if (result.requestedSeconds > result.appliedSeconds + 1e-6) {
    const requestedText = formatDevKitDuration(result.requestedSeconds);
    showToast(t('scripts.app.devkit.timeAdvancedCapped', {
      applied: durationText,
      requested: requestedText
    }));
  } else if ((atomsGained && !atomsGained.isZero()) || result.ticketsEarned > 0) {
    showToast(t('scripts.app.devkit.timeAdvanced', { duration: durationText }));
  } else {
    showToast(t('scripts.app.devkit.timeAdvancedNoReward', { duration: durationText }));
  }

  updateUI();
  saveGame();
  updateDevKitUI();
}

function handleDevKitOnlineAdvance(value) {
  const seconds = parseDevKitDurationInput(value);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    showToast(t('scripts.app.devkit.invalidTime'));
    return;
  }

  const result = applyOnlineProgress(seconds, { stepSeconds: 1 });

  const atomsGained = result.atomsGained instanceof LayeredNumber ? result.atomsGained : null;
  if (atomsGained && !atomsGained.isZero()) {
    showToast(t('scripts.app.devkit.onlineProgressAtoms', { amount: atomsGained.toString() }));
  }

  if (result.ticketsEarned > 0) {
    const unitKey = result.ticketsEarned === 1
      ? 'scripts.app.offlineTickets.ticketSingular'
      : 'scripts.app.offlineTickets.ticketPlural';
    const unit = t(unitKey);
    showToast(t('scripts.app.devkit.onlineTickets', { count: result.ticketsEarned, unit }));
  }

  const appliedSeconds = result.appliedSeconds > 0 ? result.appliedSeconds : seconds;
  const durationText = formatDevKitDuration(appliedSeconds);

  if ((atomsGained && !atomsGained.isZero()) || result.ticketsEarned > 0) {
    showToast(t('scripts.app.devkit.timeAdvancedOnline', { duration: durationText }));
  } else {
    showToast(t('scripts.app.devkit.timeAdvancedOnlineNoReward', { duration: durationText }));
  }

  updateUI();
  saveGame();
  updateDevKitUI();
}

function devkitUnlockAllTrophies() {
  const unlockedSet = getUnlockedTrophySet();
  let newlyUnlocked = 0;
  TROPHY_DEFS.forEach(def => {
    if (!unlockedSet.has(def.id)) {
      unlockedSet.add(def.id);
      newlyUnlocked += 1;
    }
  });
  if (newlyUnlocked > 0) {
    recalcProduction();
    updateUI();
    if (goalsController) {
      goalsController.update();
      goalsController.updateMilestone();
    }
    evaluatePageUnlocks({ save: false });
    saveGame();
    showToast(t('scripts.app.devkit.trophiesUnlocked', { count: newlyUnlocked }));
  } else {
    showToast(t('scripts.app.devkit.allTrophiesUnlocked'));
  }
}

function devkitUnlockAllElements() {
  let newlyOwned = 0;
  const baseCollection = createInitialElementCollection();
  periodicElements.forEach(def => {
    if (!def || !def.id) return;
    let entry = gameState.elements?.[def.id];
    if (!entry) {
      const baseEntry = baseCollection?.[def.id];
      entry = baseEntry
        ? {
            ...baseEntry,
            effects: Array.isArray(baseEntry.effects) ? [...baseEntry.effects] : [],
            bonuses: Array.isArray(baseEntry.bonuses) ? [...baseEntry.bonuses] : [],
            lifetime: getElementLifetimeCount(baseEntry)
          }
        : {
            id: def.id,
            gachaId: def.gachaId ?? def.id,
            rarity: elementRarityIndex.get(def.id) || null,
            effects: [],
            bonuses: [],
            lifetime: 0
          };
      entry.count = 1;
      entry.lifetime = Math.max(getElementLifetimeCount(entry), entry.count, 1);
      entry.owned = entry.lifetime > 0;
      gameState.elements[def.id] = entry;
      newlyOwned += 1;
      return;
    }
    const previouslyOwned = hasElementLifetime(entry);
    if (!previouslyOwned) {
      newlyOwned += 1;
    }
    const sanitizedCount = Math.max(1, getElementCurrentCount(entry));
    entry.count = sanitizedCount;
    const lifetimeCount = Math.max(getElementLifetimeCount(entry), sanitizedCount, 1);
    entry.lifetime = lifetimeCount;
    entry.owned = lifetimeCount > 0;
    if (!entry.rarity) {
      entry.rarity = elementRarityIndex.get(def.id) || entry.rarity || null;
    }
  });
  recalcProduction();
  updateUI();
  evaluateTrophies();
  evaluatePageUnlocks({ save: false });
  saveGame();
  updateDevKitUI();
  showToast(newlyOwned > 0
    ? t('scripts.app.devkit.elementsAdded', { count: newlyOwned })
    : t('scripts.app.devkit.collectionComplete'));
}

function toggleDevKitCheat(key) {
  if (!(key in DEVKIT_STATE.cheats)) {
    return;
  }
  DEVKIT_STATE.cheats[key] = !DEVKIT_STATE.cheats[key];
  updateDevKitUI();
  if (key === 'freeShop') {
    shopController.updateAffordability();
    showToast(DEVKIT_STATE.cheats[key]
      ? t('scripts.app.devkit.freeShopEnabled')
      : t('scripts.app.devkit.freeShopDisabled'));
  } else if (key === 'freeGacha') {
    updateGachaUI();
    showToast(DEVKIT_STATE.cheats[key]
      ? t('scripts.app.devkit.freeGachaEnabled')
      : t('scripts.app.devkit.freeGachaDisabled'));
  }
}

let gamePageVisibleSince = null;

function getShopUnlockSet() {
  let unlocks;
  if (gameState.shopUnlocks instanceof Set) {
    unlocks = new Set(gameState.shopUnlocks);
  } else {
    let stored = [];
    if (Array.isArray(gameState.shopUnlocks)) {
      stored = gameState.shopUnlocks;
    } else if (gameState.shopUnlocks && typeof gameState.shopUnlocks === 'object') {
      stored = Object.keys(gameState.shopUnlocks);
    }
    unlocks = new Set(stored);
  }

  if (!UPGRADE_DEFS.length) {
    unlocks.clear();
    gameState.shopUnlocks = unlocks;
    return unlocks;
  }

  const highestPurchasedIndex = UPGRADE_DEFS.reduce((highest, def, index) => {
    return getUpgradeLevel(gameState.upgrades, def.id) > 0 && index > highest ? index : highest;
  }, -1);

  const sequentialLimit = Math.min(
    UPGRADE_DEFS.length - 1,
    Math.max(0, highestPurchasedIndex + 1)
  );

  if (sequentialLimit >= 0) {
    for (let i = 0; i <= sequentialLimit; i += 1) {
      unlocks.add(UPGRADE_DEFS[i].id);
    }
    unlocks.forEach(id => {
      const index = UPGRADE_INDEX_MAP.get(id);
      if (index == null || index > sequentialLimit) {
        unlocks.delete(id);
      }
    });
  } else {
    unlocks.clear();
  }

  gameState.shopUnlocks = unlocks;
  return unlocks;
}

function formatAtomicMass(value) {
  if (value == null) return '';
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return '—';
    const text = value.toString();
    if (!text.includes('.')) {
      return text;
    }
    const [integer, fraction] = text.split('.');
    return `${integer},${fraction}`;
  }
  return String(value);
}

function formatMultiplier(value) {
  if (value instanceof LayeredNumber) {
    if (value.sign <= 0) {
      return '×—';
    }
    if (value.layer === 0 && Math.abs(value.exponent) < 6) {
      const numeric = value.toNumber();
      const options = { maximumFractionDigits: 2 };
      if (Math.abs(numeric) < 10) {
        options.minimumFractionDigits = 2;
      }
      return `×${formatNumberLocalized(numeric, options)}`;
    }
    return `×${value.toString()}`;
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return '×—';
  }
  if (Math.abs(numeric) < 1e6) {
    const options = { maximumFractionDigits: 2 };
    if (Math.abs(numeric) < 10) {
      options.minimumFractionDigits = 2;
    }
    return `×${formatNumberLocalized(numeric, options)}`;
  }
  const layered = new LayeredNumber(numeric);
  if (layered.sign <= 0) {
    return '×—';
  }
  return `×${layered.toString()}`;
}

function registerSudokuOfflineBonus(options = {}) {
  const rawConfig = options && typeof options === 'object' ? options.config : null;
  const normalizedConfig = normalizeSudokuRewardSettings(rawConfig || SUDOKU_COMPLETION_REWARD_CONFIG);
  if (!normalizedConfig.enabled) {
    return false;
  }
  const elapsed = Number(options.elapsedSeconds);
  if (!Number.isFinite(elapsed) || elapsed <= 0) {
    return false;
  }
  const withinLimit = elapsed <= normalizedConfig.timeLimitSeconds + 1e-6;
  const announce = options.announce !== false;
  const elapsedText = formatSudokuRewardDuration(elapsed);
  const limitText = formatSudokuRewardDuration(normalizedConfig.timeLimitSeconds);

  if (!withinLimit) {
    if (announce && typeof showToast === 'function') {
      const messageKey = 'scripts.app.arcade.sudoku.reward.missed';
      const fallback = `Sudoku solved in ${elapsedText}, slower than the limit (${limitText}). Bonus not granted.`;
      const message = typeof t === 'function'
        ? t(messageKey, { elapsed: elapsedText, limit: limitText })
        : null;
      showToast(message && message !== messageKey ? message : fallback);
    }
    return false;
  }

  const bonus = normalizeSudokuOfflineBonusState({
    multiplier: normalizedConfig.multiplier,
    maxSeconds: normalizedConfig.bonusSeconds,
    limitSeconds: normalizedConfig.timeLimitSeconds,
    grantedAt: Date.now()
  });
  if (!bonus) {
    return false;
  }
  gameState.sudokuOfflineBonus = bonus;

  if (announce && typeof showToast === 'function') {
    const durationText = formatSudokuRewardDuration(normalizedConfig.bonusSeconds);
    const multiplierText = formatMultiplier(normalizedConfig.multiplier);
    const messageKey = 'scripts.app.arcade.sudoku.reward.ready';
    const fallback = `Sudoku bonus ready: next offline gains will be ${multiplierText} for up to ${durationText}.`;
    const message = typeof t === 'function'
      ? t(messageKey, {
        duration: durationText,
        elapsed: elapsedText,
        limit: limitText,
        multiplier: multiplierText
      })
      : null;
    showToast(message && message !== messageKey ? message : fallback);
  }

  saveGame();
  return true;
}

function resolveChessDifficultyConfig() {
  const arcadeConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade;
  if (!arcadeConfig || typeof arcadeConfig !== 'object') {
    return null;
  }
  const chessConfig = arcadeConfig.echecs;
  if (!chessConfig || typeof chessConfig !== 'object') {
    return null;
  }
  const difficultyConfig = chessConfig.difficulty;
  if (!difficultyConfig || typeof difficultyConfig !== 'object') {
    return null;
  }
  return difficultyConfig;
}

function findChessDifficultyConfigMode(id) {
  const difficultyConfig = resolveChessDifficultyConfig();
  if (!difficultyConfig) {
    return null;
  }
  const modes = Array.isArray(difficultyConfig.modes) ? difficultyConfig.modes : [];
  const trimmedId = typeof id === 'string' ? id.trim() : '';
  if (!trimmedId) {
    return null;
  }
  for (let index = 0; index < modes.length; index += 1) {
    const mode = modes[index];
    if (!mode || typeof mode !== 'object') {
      continue;
    }
    if (typeof mode.id === 'string' && mode.id.trim() === trimmedId) {
      return mode;
    }
  }
  return null;
}

function translateChessDifficultyLabel(options = {}) {
  const labelCandidates = [];
  const fallbackLabels = [];
  if (options && typeof options === 'object') {
    if (typeof options.labelKey === 'string') {
      labelCandidates.push(options.labelKey.trim());
    }
    if (typeof options.difficultyLabelKey === 'string') {
      labelCandidates.push(options.difficultyLabelKey.trim());
    }
    if (options.mode && typeof options.mode === 'object') {
      if (typeof options.mode.labelKey === 'string') {
        labelCandidates.push(options.mode.labelKey.trim());
      }
      if (typeof options.mode.label === 'string' && options.mode.label.trim()) {
        fallbackLabels.push(options.mode.label.trim());
      }
      if (typeof options.mode.fallbackLabel === 'string' && options.mode.fallbackLabel.trim()) {
        fallbackLabels.push(options.mode.fallbackLabel.trim());
      }
    }
    if (typeof options.label === 'string' && options.label.trim()) {
      fallbackLabels.push(options.label.trim());
    }
    if (typeof options.fallbackLabel === 'string' && options.fallbackLabel.trim()) {
      fallbackLabels.push(options.fallbackLabel.trim());
    }
  }

  const translator = typeof t === 'function' ? t : null;
  for (let i = 0; i < labelCandidates.length; i += 1) {
    const key = labelCandidates[i];
    if (!key) {
      continue;
    }
    if (translator) {
      try {
        const translated = translator(key);
        if (typeof translated === 'string' && translated && translated !== key) {
          return translated;
        }
      } catch (error) {
        console.warn('Unable to translate chess difficulty label', key, error);
      }
    }
    const api = getI18nApi();
    const fallbackTranslator = api && typeof api.t === 'function' ? api.t : null;
    if (fallbackTranslator) {
      try {
        const translated = fallbackTranslator(key);
        if (typeof translated === 'string' && translated && translated !== key) {
          return translated;
        }
      } catch (error) {
        console.warn('Unable to translate chess difficulty label', key, error);
      }
    }
  }

  for (let i = 0; i < fallbackLabels.length; i += 1) {
    const label = fallbackLabels[i];
    if (label) {
      return label;
    }
  }

  return '';
}

function normalizeChessVictoryReward(raw) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const secondsCandidates = [
    source.offlineSeconds,
    source.seconds,
    source.durationSeconds,
    source.timeSeconds,
    source.time
  ];
  let offlineSeconds = 0;
  for (let i = 0; i < secondsCandidates.length; i += 1) {
    const candidate = Number(secondsCandidates[i]);
    if (Number.isFinite(candidate) && candidate > 0) {
      offlineSeconds = Math.floor(candidate);
      break;
    }
  }

  const multiplierCandidates = [
    source.offlineMultiplier,
    source.multiplier,
    source.value,
    source.amount
  ];
  let offlineMultiplier = 0;
  for (let i = 0; i < multiplierCandidates.length; i += 1) {
    const candidate = Number(multiplierCandidates[i]);
    if (Number.isFinite(candidate) && candidate > 0) {
      offlineMultiplier = candidate;
      break;
    }
  }

  if (offlineSeconds <= 0 || offlineMultiplier <= 0) {
    return null;
  }

  return {
    offlineSeconds,
    offlineMultiplier
  };
}

function registerChessVictoryReward(options = {}) {
  const reward = normalizeChessVictoryReward(options && options.reward);
  if (!reward) {
    return false;
  }

  const bonus = normalizeSudokuOfflineBonusState({
    multiplier: reward.offlineMultiplier,
    maxSeconds: reward.offlineSeconds,
    limitSeconds: 0,
    grantedAt: Date.now()
  });
  if (!bonus) {
    return false;
  }

  gameState.sudokuOfflineBonus = bonus;

  const announce = options.announce !== false;
  if (announce && typeof showToast === 'function') {
    const durationText = formatSudokuRewardDuration(reward.offlineSeconds);
    const multiplierText = formatMultiplier(reward.offlineMultiplier);
    const difficultyId = typeof options.difficultyId === 'string' ? options.difficultyId.trim() : '';
    const modeFromConfig = difficultyId ? findChessDifficultyConfigMode(difficultyId) : null;
    const difficultyLabel = translateChessDifficultyLabel({
      labelKey: options.labelKey,
      difficultyLabelKey: options.difficultyLabelKey,
      fallbackLabel: typeof options.fallbackLabel === 'string' ? options.fallbackLabel : '',
      label: options.label,
      mode: modeFromConfig
    });
    const hasDifficulty = Boolean(difficultyLabel);
    const messageKey = hasDifficulty
      ? 'scripts.app.arcade.chess.reward.ready'
      : 'scripts.app.arcade.chess.reward.readyGeneric';
    const fallback = hasDifficulty
      ? `Chess victory (${difficultyLabel}): offline bonus ready (${durationText} at ${multiplierText}).`
      : `Chess victory: offline bonus ready (${durationText} at ${multiplierText}).`;
    let message = null;
    if (typeof t === 'function') {
      try {
        message = t(messageKey, {
          difficulty: difficultyLabel,
          duration: durationText,
          multiplier: multiplierText
        });
      } catch (error) {
        console.warn('Unable to translate chess reward toast', error);
      }
    }
    if (!message || typeof message !== 'string' || !message.trim() || message === messageKey) {
      const api = getI18nApi();
      if (api && typeof api.t === 'function') {
        try {
          const translated = api.t(messageKey, {
            difficulty: difficultyLabel,
            duration: durationText,
            multiplier: multiplierText
          });
          if (translated && typeof translated === 'string' && translated.trim() && translated !== messageKey) {
            message = translated;
          }
        } catch (error) {
          console.warn('Unable to translate chess reward toast', error);
        }
      }
    }
    showToast(message && message !== messageKey ? message : fallback);
  }

  saveGame();
  return true;
}

function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms <= 0) {
    return '0s';
  }
  const totalSeconds = Math.floor(ms / 1000);
  const seconds = totalSeconds % 60;
  const totalMinutes = Math.floor(totalSeconds / 60);
  const minutes = totalMinutes % 60;
  const totalHours = Math.floor(totalMinutes / 60);
  const hours = totalHours % 24;
  const days = Math.floor(totalHours / 24);
  const parts = [];
  if (days > 0) {
    parts.push(`${days}j`);
  }
  if (hours > 0 || days > 0) {
    const hourStr = hours.toString().padStart(days > 0 ? 2 : 1, '0');
    parts.push(`${hourStr}h`);
  }
  const minuteStr = minutes.toString().padStart(hours > 0 || days > 0 ? 2 : 1, '0');
  parts.push(`${minuteStr}m`);
  parts.push(`${seconds.toString().padStart(2, '0')}s`);
  return parts.join(' ');
}

function toLayeredValue(value, fallback = 0) {
  if (value instanceof LayeredNumber) return value;
  if (value == null) return new LayeredNumber(fallback);
  return new LayeredNumber(value);
}

function normalizeProductionUnit(value, options = {}) {
  const minimum = Math.max(1, Number(options.minimum) || 1);
  const layered = value instanceof LayeredNumber ? value.clone() : new LayeredNumber(value);
  if (layered.isZero() || layered.sign <= 0) {
    return LayeredNumber.zero();
  }
  if (layered.layer > 0) {
    return layered;
  }
  if (layered.exponent < 0) {
    return new LayeredNumber(minimum);
  }
  if (layered.exponent <= 14) {
    const numeric = layered.mantissa * Math.pow(10, layered.exponent);
    const rounded = Math.max(minimum, Math.round(numeric));
    return new LayeredNumber(rounded);
  }
  return layered;
}

function toMultiplierLayered(value) {
  if (value instanceof LayeredNumber) {
    return value.clone();
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return LayeredNumber.one();
  }
  return new LayeredNumber(numeric);
}

function isLayeredOne(value) {
  if (value instanceof LayeredNumber) {
    return value.compare(LayeredNumber.one()) === 0;
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return false;
  }
  return Math.abs(numeric - 1) <= LayeredNumber.EPSILON;
}

function getFlatSourceValue(entry, key) {
  if (!entry || !entry.sources || !entry.sources.flats) {
    return LayeredNumber.zero();
  }
  const raw = entry.sources.flats[key];
  if (raw instanceof LayeredNumber) {
    return raw;
  }
  if (raw == null) {
    return LayeredNumber.zero();
  }
  return new LayeredNumber(raw);
}

function getMultiplierSourceValue(entry, step) {
  if (!entry || !entry.sources || !entry.sources.multipliers) {
    return LayeredNumber.one();
  }
  const multipliers = entry.sources.multipliers;
  if (step.source === 'rarityMultiplier') {
    const store = multipliers.rarityMultipliers;
    if (!store) return LayeredNumber.one();
    if (store instanceof Map) {
      const raw = store.get(step.rarityId);
      const numeric = Number(raw);
      return Number.isFinite(numeric) && numeric > 0
        ? new LayeredNumber(numeric)
        : LayeredNumber.one();
    }
    if (typeof store === 'object' && store !== null) {
      const raw = store[step.rarityId];
      const numeric = Number(raw);
      return Number.isFinite(numeric) && numeric > 0
        ? new LayeredNumber(numeric)
        : LayeredNumber.one();
    }
    return LayeredNumber.one();
  }
  if (step.source === 'collectionMultiplier') {
    const rawCollection = multipliers.collectionMultiplier;
    if (rawCollection instanceof LayeredNumber) {
      return rawCollection;
    }
    if (rawCollection == null) {
      return LayeredNumber.one();
    }
    return toMultiplierLayered(rawCollection);
  }
  const raw = multipliers[step.source];
  if (raw instanceof LayeredNumber) {
    return raw;
  }
  if (raw == null) {
    return LayeredNumber.one();
  }
  return toMultiplierLayered(raw);
}

function formatFlatValue(value) {
  const layered = value instanceof LayeredNumber ? value : toLayeredValue(value, 0);
  const normalized = normalizeProductionUnit(layered);
  return normalized.isZero() ? '+0' : `+${normalized.toString()}`;
}

function formatProductionStepValue(step, entry) {
  if (!step) return '—';
  switch (step.type) {
    case 'base': {
      const baseValue = entry && entry.base != null
        ? normalizeProductionUnit(entry.base)
        : LayeredNumber.zero();
      return baseValue.toString();
    }
    case 'flat': {
      const flatValue = getFlatSourceValue(entry, step.source);
      return formatFlatValue(flatValue);
    }
    case 'multiplier': {
      const multiplier = getMultiplierSourceValue(entry, step);
      return formatMultiplier(multiplier);
    }
    case 'total': {
      const totalValue = entry && entry.total != null
        ? normalizeProductionUnit(entry.total)
        : LayeredNumber.zero();
      return totalValue.toString();
    }
    default:
      return '—';
  }
}

function getProductionStepLabel(step, context) {
  if (!step) return '';
  return step.label;
}

function renderProductionBreakdown(container, entry, context = null) {
  if (!container) return;
  container.innerHTML = '';
  PRODUCTION_STEP_ORDER.forEach(step => {
    if (
      (context === 'perSecond' || context === 'perClick')
      && step.id === 'baseFlat'
    ) {
      return;
    }
    const row = document.createElement('li');
    row.className = `production-breakdown__row production-breakdown__row--${step.type}`;
    row.dataset.step = step.id;

    const label = document.createElement('span');
    label.className = 'production-breakdown__label';
    label.textContent = getProductionStepLabel(step, context);

    const value = document.createElement('span');
    value.className = 'production-breakdown__value';
    value.textContent = formatProductionStepValue(step, entry);

    row.append(label, value);
    container.appendChild(row);
  });
}

let toastElement = null;
let waveGame = null;
let balanceGame = null;
let quantum2048Game = null;
let apsCritPulseTimeoutId = null;

const APS_CRIT_TIMER_EPSILON = 1e-3;

function updateApsCritTimer(deltaSeconds) {
  if (!Number.isFinite(deltaSeconds) || deltaSeconds <= 0) {
    return;
  }
  const state = ensureApsCritState();
  if (!state.effects.length) {
    return;
  }
  const previousMultiplier = getApsCritMultiplier(state);
  state.effects = state.effects
    .map(effect => {
      if (!effect) {
        return null;
      }
      const remaining = Number(effect.remainingSeconds) || 0;
      if (remaining <= 0) {
        return null;
      }
      const next = remaining - deltaSeconds;
      if (next <= APS_CRIT_TIMER_EPSILON) {
        return null;
      }
      return {
        multiplierAdd: Number(effect.multiplierAdd) || 0,
        remainingSeconds: next
      };
    })
    .filter(effect => effect != null);
  if (!state.effects.length) {
    if (previousMultiplier !== 1) {
      recalcProduction();
    }
    updateUI();
    return;
  }
  const currentMultiplier = getApsCritMultiplier(state);
  if (currentMultiplier !== previousMultiplier) {
    recalcProduction();
    updateUI();
  }
}

const CLICK_WINDOW_MS = CONFIG.presentation?.clicks?.windowMs ?? 1000;
const MAX_CLICKS_PER_SECOND = CONFIG.presentation?.clicks?.maxClicksPerSecond ?? 20;
const clickHistory = [];
let targetClickStrength = 0;
let displayedClickStrength = 0;

const atomAnimationState = {
  intensity: 0,
  posX: 0,
  posY: 0,
  velX: 0,
  velY: 0,
  tilt: 0,
  tiltVelocity: 0,
  squash: 0,
  squashVelocity: 0,
  spinPhase: Math.random() * Math.PI * 2,
  noisePhase: Math.random() * Math.PI * 2,
  noiseOffset: Math.random() * Math.PI * 2,
  impulseTimer: 0,
  lastInputIntensity: 0,
  lastTime: null
};

const ATOM_REBOUND_AMPLITUDE_SCALE = 0.5;

function getAtomVisualElement() {
  const current = elements.atomVisual;
  if (current?.isConnected) {
    return current;
  }

  let resolved = null;
  if (elements.atomButton?.isConnected) {
    resolved = elements.atomButton.querySelector('.atom-visual');
  }
  if (!resolved) {
    resolved = document.querySelector('.atom-visual');
  }

  if (resolved) {
    elements.atomVisual = resolved;
    const image = resolved.querySelector('.atom-image');
    if (image) {
      elements.atomImage = image;
    }
  } else {
    elements.atomVisual = null;
  }

  return resolved || null;
}

function getAtomImageElement() {
  const current = elements.atomImage;
  if (current?.isConnected) {
    return current;
  }
  const visual = getAtomVisualElement();
  if (!visual) {
    elements.atomImage = null;
    return null;
  }
  const resolved = visual.querySelector('.atom-image');
  if (resolved) {
    elements.atomImage = resolved;
    return resolved;
  }
  elements.atomImage = null;
  return null;
}

function isGamePageActive() {
  return document.body.dataset.activePage === 'game';
}

function updateClickHistory(now = performance.now()) {
  while (clickHistory.length && now - clickHistory[0] > CLICK_WINDOW_MS) {
    clickHistory.shift();
  }
  const count = clickHistory.length;
  if (count === 0) {
    targetClickStrength = 0;
    return;
  }

  let rawRate = 0;
  if (count === 1) {
    const timeSince = now - clickHistory[0];
    const safeSpan = Math.max(780, Math.min(CLICK_WINDOW_MS, timeSince));
    rawRate = 1000 / safeSpan;
  } else {
    const span = Math.max(140, Math.min(CLICK_WINDOW_MS, clickHistory[count - 1] - clickHistory[0]));
    rawRate = (count - 1) / (span / 1000);
    const windowAverage = count / (CLICK_WINDOW_MS / 1000);
    if (windowAverage > rawRate) {
      rawRate = windowAverage;
    }
  }

  rawRate = Math.min(rawRate, MAX_CLICKS_PER_SECOND * 1.6);
  const normalized = Math.max(0, Math.min(1, rawRate / MAX_CLICKS_PER_SECOND));
  const curved = 1 - Math.exp(-normalized * 3.8);
  targetClickStrength = Math.min(1, curved * 1.08);
}

const glowStops = (() => {
  const source = Array.isArray(APP_DATA.GLOW_STOPS) && APP_DATA.GLOW_STOPS.length
    ? APP_DATA.GLOW_STOPS
    : Array.isArray(APP_DATA.DEFAULT_GLOW_STOPS) && APP_DATA.DEFAULT_GLOW_STOPS.length
      ? APP_DATA.DEFAULT_GLOW_STOPS
      : [];
  if (!source.length) {
    return [
      { stop: 0, color: [255, 255, 255] }
    ];
  }
  return source.map(entry => {
    const stop = Number(entry?.stop);
    const colorSource = Array.isArray(entry?.color) ? entry.color : [];
    const color = [0, 0, 0];
    for (let i = 0; i < 3; i += 1) {
      const value = Number(colorSource[i]);
      color[i] = Number.isFinite(value) ? value : 0;
    }
    return {
      stop: Number.isFinite(stop) ? stop : 0,
      color
    };
  }).sort((a, b) => a.stop - b.stop);
})();

function interpolateGlowColor(strength) {
  const clamped = Math.max(0, Math.min(1, strength));
  for (let i = 0; i < glowStops.length - 1; i += 1) {
    const current = glowStops[i];
    const next = glowStops[i + 1];
    if (clamped <= next.stop) {
      const range = next.stop - current.stop;
      const t = range === 0 ? 0 : (clamped - current.stop) / range;
      const r = Math.round(current.color[0] + (next.color[0] - current.color[0]) * t);
      const g = Math.round(current.color[1] + (next.color[1] - current.color[1]) * t);
      const b = Math.round(current.color[2] + (next.color[2] - current.color[2]) * t);
      return `${r}, ${g}, ${b}`;
    }
  }
  const last = glowStops[glowStops.length - 1];
  return `${last.color[0]}, ${last.color[1]}, ${last.color[2]}`;
}

function applyClickStrength(strength) {
  if (!elements.atomButton) return;
  const clamped = Math.max(0, Math.min(1, strength));
  const heat = Math.pow(clamped, 0.35);
  const button = elements.atomButton;
  button.style.setProperty('--glow-strength', heat.toFixed(3));
  button.style.setProperty('--glow-color', interpolateGlowColor(heat));
  if (clamped > 0.01) {
    button.classList.add('is-active');
  } else {
    button.classList.remove('is-active');
  }
}

function injectAtomImpulse(now = performance.now()) {
  if (!getAtomVisualElement()) return;
  const state = atomAnimationState;
  if (state.lastTime == null) {
    state.lastTime = now;
  }

  const drive = Math.max(targetClickStrength, displayedClickStrength, 0);
  const baseIntensity = Math.max(state.intensity, drive);
  const energy = Math.pow(Math.max(0, baseIntensity), 0.6);
  const impulseAngle = Math.random() * Math.PI * 2;
  const impulseStrength = 180 + energy * 520;

  state.velX += Math.cos(impulseAngle) * impulseStrength;
  state.velY += Math.sin(impulseAngle) * impulseStrength;

  const recenter = 20 + energy * 60;
  state.velX += -state.posX * recenter;
  state.velY += -state.posY * recenter * 1.05;

  const wobbleKick = (Math.random() - 0.5) * (220 + energy * 320);
  state.tiltVelocity += wobbleKick;
  state.squashVelocity += (Math.random() - 0.35) * (160 + energy * 220);

  state.spinPhase += (Math.random() - 0.5) * (0.45 + energy * 1.2);
  state.noiseOffset = Math.random() * Math.PI * 2;

  state.intensity = Math.min(1, baseIntensity + 0.25 + drive * 0.4);
  state.impulseTimer = Math.min(state.impulseTimer, 0.06);
}

function updateAtomSpring(now = performance.now(), drive = 0) {
  const visual = getAtomVisualElement();
  if (!visual) return;
  const state = atomAnimationState;
  if (state.lastTime == null) {
    state.lastTime = now;
  }

  let delta = (now - state.lastTime) / 1000;
  if (!Number.isFinite(delta) || delta < 0) {
    delta = 0;
  }
  delta = Math.min(delta, 0.05);
  state.lastTime = now;

  const input = Math.max(0, Math.min(1, drive));
  state.intensity += (input - state.intensity) * Math.min(1, delta * 9);
  const intensity = state.intensity;
  const energy = Math.pow(intensity, 0.65);

  const rangeX = 6 + energy * 34 + intensity * 6;
  const rangeY = 7 + energy * 40 + intensity * 8;
  const centerPull = 10 + energy * 24;
  const damping = 4.8 + energy * 16;
  const maxSpeed = 120 + energy * 420;

  state.velX -= (state.posX / Math.max(rangeX, 1)) * centerPull * delta;
  state.velY -= (state.posY / Math.max(rangeY, 1)) * centerPull * delta;

  state.velX -= state.velX * damping * delta;
  state.velY -= state.velY * damping * delta;

  state.noisePhase += delta * (1.2 + energy * 6.4);
  const noiseX =
    Math.sin(state.noisePhase * 1.35 + state.noiseOffset) * (0.34 + energy * 0.9) +
    Math.cos(state.noisePhase * 2.35 + state.noiseOffset * 1.7) * (0.22 + energy * 0.55);
  const noiseY =
    Math.cos(state.noisePhase * 1.55 + state.noiseOffset * 0.4) * (0.32 + energy * 0.82) +
    Math.sin(state.noisePhase * 2.1 + state.noiseOffset) * (0.2 + energy * 0.5);
  const noiseStrength = 12 + energy * 210 + intensity * 30;
  state.velX += noiseX * noiseStrength * delta;
  state.velY += noiseY * noiseStrength * delta;

  state.impulseTimer -= delta;
  const impulseDelay = Math.max(0.085, 0.42 - energy * 0.32 - intensity * 0.1);
  if (state.impulseTimer <= 0) {
    const burstAngle = Math.random() * Math.PI * 2;
    const burstStrength = 16 + energy * 240 + intensity * 120;
    state.velX += Math.cos(burstAngle) * burstStrength;
    state.velY += Math.sin(burstAngle) * burstStrength;
    state.impulseTimer = impulseDelay;
  }

  const gain = Math.max(0, input - state.lastInputIntensity);
  if (gain > 0.001) {
    const gainAngle = Math.random() * Math.PI * 2;
    const gainStrength = 38 + gain * 480 + intensity * 140;
    state.velX += Math.cos(gainAngle) * gainStrength;
    state.velY += Math.sin(gainAngle) * gainStrength;
  }
  state.lastInputIntensity = input;

  state.posX += state.velX * delta;
  state.posY += state.velY * delta;

  const restitution = 0.46 + energy * 0.42;
  if (state.posX > rangeX) {
    state.posX = rangeX;
    state.velX = -Math.abs(state.velX) * restitution;
  } else if (state.posX < -rangeX) {
    state.posX = -rangeX;
    state.velX = Math.abs(state.velX) * restitution;
  }
  if (state.posY > rangeY) {
    state.posY = rangeY;
    state.velY = -Math.abs(state.velY) * restitution;
  } else if (state.posY < -rangeY) {
    state.posY = -rangeY;
    state.velY = Math.abs(state.velY) * restitution;
  }

  const speed = Math.hypot(state.velX, state.velY);
  if (speed > maxSpeed) {
    const scale = maxSpeed / speed;
    state.velX *= scale;
    state.velY *= scale;
  }

  state.spinPhase += delta * (2.4 + energy * 14.5);
  if (!Number.isFinite(state.spinPhase)) state.spinPhase = 0;
  if (!Number.isFinite(state.noisePhase)) state.noisePhase = 0;
  if (state.spinPhase > Math.PI * 1000) state.spinPhase %= Math.PI * 2;
  if (state.noisePhase > Math.PI * 1000) state.noisePhase %= Math.PI * 2;

  const spin = Math.sin(state.spinPhase) * (4 + energy * 28);

  const tiltTarget =
    (state.posX / Math.max(rangeX, 1)) * (10 + energy * 18) +
    (state.velX / Math.max(maxSpeed, 1)) * (34 + energy * 30);
  const tiltSpring = 24 + energy * 32;
  const tiltDamping = 7 + energy * 14;
  state.tiltVelocity += (tiltTarget - state.tilt) * tiltSpring * delta;
  state.tiltVelocity -= state.tiltVelocity * tiltDamping * delta;
  state.tilt += state.tiltVelocity * delta;

  const verticalMomentum = state.velY / Math.max(maxSpeed, 1);
  const squashTarget = Math.max(-1, Math.min(1, -verticalMomentum * (1.6 + energy * 1.25)));
  const squashSpring = 22 + energy * 28;
  const squashDamping = 9 + energy * 12;
  state.squashVelocity += (squashTarget - state.squash) * squashSpring * delta;
  state.squashVelocity -= state.squashVelocity * squashDamping * delta;
  state.squash += state.squashVelocity * delta;

  if (!Number.isFinite(state.posX)) state.posX = 0;
  if (!Number.isFinite(state.posY)) state.posY = 0;
  if (!Number.isFinite(state.velX)) state.velX = 0;
  if (!Number.isFinite(state.velY)) state.velY = 0;
  if (!Number.isFinite(state.tilt)) state.tilt = 0;
  if (!Number.isFinite(state.tiltVelocity)) state.tiltVelocity = 0;
  if (!Number.isFinite(state.squash)) state.squash = 0;
  if (!Number.isFinite(state.squashVelocity)) state.squashVelocity = 0;

  if (Math.abs(state.posX) < 0.0005) state.posX = 0;
  if (Math.abs(state.posY) < 0.0005) state.posY = 0;
  if (Math.abs(state.velX) < 0.0005) state.velX = 0;
  if (Math.abs(state.velY) < 0.0005) state.velY = 0;
  if (Math.abs(state.tilt) < 0.0005) state.tilt = 0;
  if (Math.abs(state.squash) < 0.0005) state.squash = 0;

  const offsetX = state.posX * ATOM_REBOUND_AMPLITUDE_SCALE;
  const offsetY = state.posY * ATOM_REBOUND_AMPLITUDE_SCALE;
  visual.style.setProperty('--shake-x', `${offsetX.toFixed(2)}px`);
  visual.style.setProperty('--shake-y', `${offsetY.toFixed(2)}px`);
  visual.style.setProperty(
    '--shake-rot',
    `${((state.tilt + spin) * ATOM_REBOUND_AMPLITUDE_SCALE).toFixed(2)}deg`
  );
  visual.style.setProperty('--shake-scale-x', '1');
  visual.style.setProperty('--shake-scale-y', '1');
}

function updateClickVisuals(now = performance.now()) {
  updateClickHistory(now);
  displayedClickStrength += (targetClickStrength - displayedClickStrength) * 0.28;
  if (Math.abs(targetClickStrength - displayedClickStrength) < 0.0003) {
    displayedClickStrength = targetClickStrength;
  }
  applyClickStrength(displayedClickStrength);
  updateAtomSpring(now, displayedClickStrength);
}

function registerManualClick() {
  const now = performance.now();
  clickHistory.push(now);
  updateClickVisuals(now);
  injectAtomImpulse(now);
  if (gameState.stats) {
    const session = gameState.stats.session;
    const global = gameState.stats.global;
    if (session) {
      if (!Number.isFinite(session.startedAt)) {
        session.startedAt = Date.now();
      }
      session.manualClicks += 1;
    }
    if (global) {
      if (!Number.isFinite(global.startedAt)) {
        global.startedAt = Date.now();
      }
      global.manualClicks += 1;
    }
  }
}

function registerFrenzyTrigger(type) {
  if (!gameState.stats) return;
  const key = type === 'perClick' ? 'perClick' : 'perSecond';

  const applyToStore = store => {
    if (!store) return;
    if (!store.frenzyTriggers) {
      store.frenzyTriggers = { perClick: 0, perSecond: 0, total: 0 };
    }
    store.frenzyTriggers[key] = (store.frenzyTriggers[key] || 0) + 1;
    store.frenzyTriggers.total = (store.frenzyTriggers.total || 0) + 1;
  };

  applyToStore(gameState.stats.session);
  applyToStore(gameState.stats.global);
}

function animateAtomPress(options = {}) {
  const { critical = false, multiplier = 1, context = null } = options;
  let button = context?.button;
  if (!button && !context) {
    button = elements.atomButton;
  }
  if (!button) return;
  if (critical) {
    soundEffects.crit.play();
    button.classList.add('is-critical');
    showCriticalIndicator(multiplier);
    clearTimeout(animateAtomPress.criticalTimeout);
    animateAtomPress.criticalTimeout = setTimeout(() => {
      button.classList.remove('is-critical');
    }, 280);
  }
}

const STAR_COUNT = CONFIG.presentation?.starfield?.starCount ?? 60;
const ATOM_IMAGE_FALLBACK = 'Assets/Image/Atom.png';

function initStarfield() {
  if (!elements.starfield) return;
  const fragment = document.createDocumentFragment();
  for (let i = 0; i < STAR_COUNT; i += 1) {
    const star = document.createElement('span');
    star.className = 'starfield__star';
    star.style.left = `${Math.random() * 100}%`;
    star.style.top = `${Math.random() * 100}%`;
    star.style.setProperty('--star-scale', (0.6 + Math.random() * 1.7).toFixed(2));
    const duration = 4 + Math.random() * 6;
    star.style.animationDuration = `${duration.toFixed(2)}s`;
    star.style.animationDelay = `-${(Math.random() * duration).toFixed(2)}s`;
    star.style.setProperty('--star-opacity', (0.26 + Math.random() * 0.54).toFixed(2));
    fragment.appendChild(star);
  }
  elements.starfield.appendChild(fragment);
}

const CRIT_ATOM_IMAGES = (() => {
  const source = Array.isArray(APP_DATA.CRIT_ATOM_IMAGES) && APP_DATA.CRIT_ATOM_IMAGES.length
    ? APP_DATA.CRIT_ATOM_IMAGES
    : Array.isArray(APP_DATA.DEFAULT_CRIT_ATOM_IMAGES) && APP_DATA.DEFAULT_CRIT_ATOM_IMAGES.length
      ? APP_DATA.DEFAULT_CRIT_ATOM_IMAGES
      : [];
  if (!source.length) {
    return [ATOM_IMAGE_FALLBACK];
  }
  return source.map(value => String(value));
})();

const ATOM_BUTTON_IMAGES = (() => {
  const unique = new Set();
  CRIT_ATOM_IMAGES.forEach(value => {
    if (typeof value === 'string') {
      const normalized = value.trim();
      if (normalized) {
        unique.add(normalized);
      }
    }
  });
  if (!unique.size) {
    unique.add(ATOM_IMAGE_FALLBACK);
  } else if (!unique.has(ATOM_IMAGE_FALLBACK)) {
    unique.add(ATOM_IMAGE_FALLBACK);
  }
  return Array.from(unique);
})();

const CRIT_ATOM_LIFETIME_MS = 6000;
const CRIT_ATOM_FADE_MS = 600;

function ensureCritAtomLayer() {
  if (elements.critAtomLayer && elements.critAtomLayer.isConnected) {
    return elements.critAtomLayer;
  }
  const layer = document.createElement('div');
  layer.className = 'crit-atom-layer';
  layer.setAttribute('aria-hidden', 'true');
  document.body.appendChild(layer);
  elements.critAtomLayer = layer;
  return layer;
}

function pickRandom(array) {
  if (!Array.isArray(array) || array.length === 0) {
    return undefined;
  }
  return array[Math.floor(Math.random() * array.length)];
}

function randomizeAtomButtonImage() {
  const image = getAtomImageElement();
  if (!image) {
    return;
  }
  const current = image.dataset.atomImage || image.getAttribute('src') || ATOM_IMAGE_FALLBACK;
  let next = pickRandom(ATOM_BUTTON_IMAGES) || ATOM_IMAGE_FALLBACK;
  if (ATOM_BUTTON_IMAGES.length > 1) {
    let attempts = 0;
    while (next === current && attempts < 4) {
      next = pickRandom(ATOM_BUTTON_IMAGES) || ATOM_IMAGE_FALLBACK;
      attempts += 1;
    }
  }
  if (image.getAttribute('src') !== next) {
    image.setAttribute('src', next);
  }
  image.dataset.atomImage = next;
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function spawnCriticalAtom(multiplier = 1) {
  if (critAtomVisualsDisabled) {
    return;
  }
  const layer = ensureCritAtomLayer();
  if (!layer) return;

  const safeMultiplier = Math.max(1, Number(multiplier) || 1);
  const fallbackImage = ATOM_IMAGE_FALLBACK;
  const imageSource = pickRandom(CRIT_ATOM_IMAGES) || fallbackImage;

  const atom = document.createElement('img');
  atom.src = imageSource;
  atom.alt = '';
  atom.className = 'crit-atom';
  atom.setAttribute('aria-hidden', 'true');

  const buttonSize = elements.atomButton ? elements.atomButton.offsetWidth : Math.min(window.innerWidth, window.innerHeight) * 0.2;
  const normalizedSize = clamp(buttonSize * (0.62 + Math.min(safeMultiplier, 6) * 0.04), 64, 160);
  atom.style.setProperty('--crit-atom-size', `${normalizedSize.toFixed(2)}px`);

  const baseScale = 0.78 + Math.random() * 0.18;
  const baseRotation = Math.random() * 360;
  atom.style.setProperty('--crit-atom-scale', baseScale.toFixed(3));
  atom.style.setProperty('--crit-atom-x', '0px');
  atom.style.setProperty('--crit-atom-y', '0px');
  atom.style.setProperty('--crit-atom-rotation', `${baseRotation.toFixed(2)}deg`);

  layer.appendChild(atom);

  const layerRect = layer.getBoundingClientRect();
  const viewportWidth = layerRect.width || window.innerWidth;
  const viewportHeight = layerRect.height || window.innerHeight;
  const atomRect = atom.getBoundingClientRect();
  const atomWidth = atomRect.width || normalizedSize;
  const atomHeight = atomRect.height || normalizedSize;

  const buttonRect = elements.atomButton ? elements.atomButton.getBoundingClientRect() : null;
  const startX = (buttonRect ? buttonRect.left + buttonRect.width / 2 : viewportWidth / 2)
    - layerRect.left - atomWidth / 2;
  const startY = (buttonRect ? buttonRect.top + buttonRect.height / 2 : viewportHeight / 2)
    - layerRect.top - atomHeight / 2;

  let x = clamp(startX, 0, Math.max(0, viewportWidth - atomWidth));
  let y = clamp(startY, 0, Math.max(0, viewportHeight - atomHeight));
  let rotation = baseRotation;

  atom.style.setProperty('--crit-atom-x', `${x.toFixed(2)}px`);
  atom.style.setProperty('--crit-atom-y', `${y.toFixed(2)}px`);
  atom.style.setProperty('--crit-atom-rotation', `${rotation.toFixed(2)}deg`);

  requestAnimationFrame(() => {
    atom.classList.add('is-active');
  });

  const maxX = Math.max(0, viewportWidth - atomWidth);
  const maxY = Math.max(0, viewportHeight - atomHeight);
  let vx = (Math.random() * 2 - 1) * (240 + Math.random() * 220);
  if (Math.abs(vx) < 160) {
    vx = 160 * (Math.random() < 0.5 ? -1 : 1);
  }
  let vy = -(360 + Math.random() * 240);
  const gravity = 900;
  const bounceLoss = 0.72 + Math.random() * 0.08;
  const wallLoss = 0.78 + Math.random() * 0.1;
  const floorFriction = 0.88;
  const rotationVelocity = (Math.random() * 160 + 80) * (Math.random() < 0.5 ? -1 : 1);

  const startTime = performance.now();
  let lastTime = startTime;
  let frameId = null;
  let active = true;

  function updateAtomPosition(now) {
    if (!active) {
      return;
    }
    const elapsed = now - startTime;
    const delta = Math.min((now - lastTime) / 1000, 0.04);
    lastTime = now;

    vy += gravity * delta;
    x += vx * delta;
    y += vy * delta;
    rotation += rotationVelocity * delta;

    if (x <= 0) {
      x = 0;
      vx = Math.abs(vx) * wallLoss;
    } else if (x >= maxX) {
      x = maxX;
      vx = -Math.abs(vx) * wallLoss;
    }

    if (y <= 0) {
      y = 0;
      vy = Math.abs(vy) * wallLoss;
    } else if (y >= maxY) {
      y = maxY;
      if (Math.abs(vy) > 80) {
        vy = -Math.abs(vy) * bounceLoss;
      } else {
        vy = 0;
      }
      vx *= floorFriction;
    }

    atom.style.setProperty('--crit-atom-x', `${x.toFixed(2)}px`);
    atom.style.setProperty('--crit-atom-y', `${y.toFixed(2)}px`);
    atom.style.setProperty('--crit-atom-rotation', `${rotation.toFixed(2)}deg`);

    if (elapsed < CRIT_ATOM_LIFETIME_MS) {
      frameId = requestAnimationFrame(updateAtomPosition);
    }
  }

  frameId = requestAnimationFrame(now => {
    lastTime = now;
    updateAtomPosition(now);
  });

  const fadeTimeout = window.setTimeout(() => {
    atom.classList.add('is-fading');
    atom.style.setProperty('--crit-atom-scale', `${(baseScale * 0.6).toFixed(3)}`);
  }, Math.max(0, CRIT_ATOM_LIFETIME_MS - CRIT_ATOM_FADE_MS));

  const removalTimeout = window.setTimeout(() => {
    cleanup();
  }, CRIT_ATOM_LIFETIME_MS);

  function cleanup() {
    if (!active) {
      return;
    }
    active = false;
    window.clearTimeout(fadeTimeout);
    window.clearTimeout(removalTimeout);
    if (frameId) {
      cancelAnimationFrame(frameId);
    }
    if (atom.isConnected) {
      atom.remove();
    }
  }

  atom.addEventListener('transitionend', event => {
    if (event.propertyName !== 'opacity') {
      return;
    }
    const elapsed = performance.now() - startTime;
    if (elapsed + 16 >= CRIT_ATOM_LIFETIME_MS) {
      cleanup();
    }
  });
}

const critBannerState = {
  fadeTimeoutId: null,
  hideTimeoutId: null
};

function clearCritBannerTimers() {
  if (critBannerState.fadeTimeoutId != null) {
    clearTimeout(critBannerState.fadeTimeoutId);
    critBannerState.fadeTimeoutId = null;
  }
  if (critBannerState.hideTimeoutId != null) {
    clearTimeout(critBannerState.hideTimeoutId);
    critBannerState.hideTimeoutId = null;
  }
}

function hideCritBanner(immediate = false) {
  const display = elements.statusCrit;
  const valueElement = elements.statusCritValue;
  if (!display || !valueElement) return;
  clearCritBannerTimers();
  if (immediate) {
    display.hidden = true;
    display.classList.remove('is-active', 'is-fading');
    valueElement.classList.remove('status-crit-value--smash');
    display.removeAttribute('aria-label');
    display.removeAttribute('title');
    return;
  }
  display.classList.add('is-fading');
  critBannerState.hideTimeoutId = setTimeout(() => {
    display.hidden = true;
    display.classList.remove('is-active', 'is-fading');
    valueElement.classList.remove('status-crit-value--smash');
    display.removeAttribute('aria-label');
    display.removeAttribute('title');
    critBannerState.hideTimeoutId = null;
  }, 360);
}

function formatCritLayeredNumber(value) {
  const layered = value instanceof LayeredNumber ? value : toLayeredValue(value, 0);
  if (!layered || layered.sign === 0) {
    return '0';
  }

  if (layered.layer === 0 && Math.abs(layered.exponent) < 6) {
    const numeric = layered.sign * layered.mantissa * Math.pow(10, layered.exponent);
    const formatted = LayeredNumber.formatLocalizedNumber(numeric, {
      maximumFractionDigits: 2,
      minimumFractionDigits: 0
    });
    if (formatted) {
      return formatted;
    }
    return `${numeric}`;
  }

  return layered.toString();
}

function showCritBanner(input) {
  const display = elements.statusCrit;
  const valueElement = elements.statusCritValue;
  if (!display || !valueElement) return;

  const options = input instanceof LayeredNumber || typeof input === 'number'
    ? { bonusAmount: input }
    : (input ?? {});

  const layeredBonus = options.bonusAmount != null
    ? toLayeredNumber(options.bonusAmount, 0)
    : LayeredNumber.zero();
  const layeredBase = options.baseAmount != null
    ? toLayeredNumber(options.baseAmount, 0)
    : null;

  let layeredTotal;
  if (options.totalAmount != null) {
    layeredTotal = toLayeredNumber(options.totalAmount, 0);
  } else if (layeredBase) {
    layeredTotal = layeredBase.add(layeredBonus);
  } else {
    layeredTotal = layeredBonus.clone();
  }

  if (!layeredTotal || layeredTotal.sign <= 0) {
    hideCritBanner(true);
    return;
  }

  const totalText = `+${formatCritLayeredNumber(layeredTotal)}`;

  const multiplierValue = Number(options.multiplier ?? options.multiplierValue);
  const hasMultiplier = Number.isFinite(multiplierValue) && multiplierValue > 1;
  const multiplierText = hasMultiplier
    ? `${formatNumberLocalized(multiplierValue, { maximumFractionDigits: 2 })}×`
    : '';

  valueElement.textContent = hasMultiplier
    ? `${multiplierText} = ${totalText}`
    : totalText;

  display.hidden = false;
  display.classList.remove('is-fading');
  display.classList.add('is-active');

  valueElement.classList.remove('status-crit-value--smash');
  void valueElement.offsetWidth; // force reflow to restart the impact animation
  valueElement.classList.add('status-crit-value--smash');

  const ariaText = hasMultiplier
    ? `Coup critique ${multiplierText} = ${totalText} atomes`
    : `Coup critique : ${totalText} atomes`;
  display.setAttribute('aria-label', ariaText);

  const details = [];
  if (layeredBase && layeredBase.sign > 0) {
    details.push(`base ${formatCritLayeredNumber(layeredBase)}`);
  }
  if (layeredBonus && layeredBonus.sign > 0 && (!layeredBase || layeredBonus.compare(layeredTotal) !== 0)) {
    details.push(`bonus +${formatCritLayeredNumber(layeredBonus)}`);
  }
  const detailText = details.length ? ` — ${details.join(' · ')}` : '';
  display.title = hasMultiplier
    ? `Bonus critique ${multiplierText} = ${totalText}${detailText}`
    : `Bonus critique ${totalText}${detailText}`;

  clearCritBannerTimers();
  critBannerState.fadeTimeoutId = setTimeout(() => {
    display.classList.add('is-fading');
    critBannerState.fadeTimeoutId = null;
    critBannerState.hideTimeoutId = setTimeout(() => {
      display.hidden = true;
      display.classList.remove('is-active', 'is-fading');
      valueElement.classList.remove('status-crit-value--smash');
      display.removeAttribute('aria-label');
      display.removeAttribute('title');
      critBannerState.hideTimeoutId = null;
    }, 360);
  }, 3000);
}

function showCriticalIndicator(multiplier) {
  spawnCriticalAtom(multiplier);
}

function applyCriticalHit(baseAmount) {
  const amount = baseAmount instanceof LayeredNumber
    ? baseAmount.clone()
    : new LayeredNumber(baseAmount ?? 0);
  const critState = cloneCritState(gameState.crit);
  const chance = Number(critState.chance) || 0;
  const effectiveMultiplier = Math.max(1, Math.min(critState.multiplier || 1, critState.maxMultiplier || critState.multiplier || 1));
  if (chance <= 0 || effectiveMultiplier <= 1) {
    return { amount, isCritical: false, multiplier: 1 };
  }
  if (Math.random() >= chance) {
    return { amount, isCritical: false, multiplier: 1 };
  }
  const critAmount = amount.multiplyNumber(effectiveMultiplier);
  return { amount: critAmount, isCritical: true, multiplier: effectiveMultiplier };
}

function handleManualAtomClick(options = {}) {
  const contextId = options?.contextId;
  const context = resolveManualClickContext(contextId);
  const baseAmount = gameState.perClick instanceof LayeredNumber
    ? gameState.perClick
    : toLayeredNumber(gameState.perClick ?? 0, 0);
  const critResult = applyCriticalHit(baseAmount);
  gainAtoms(critResult.amount, 'apc');
  registerManualClick();
  registerApcFrenzyClick(performance.now(), context);
  soundEffects.pop.play();
  if (critResult.isCritical) {
    gameState.lastCritical = {
      at: Date.now(),
      multiplier: critResult.multiplier
    };
    const critBonus = critResult.amount.subtract(baseAmount);
    showCritBanner({
      bonusAmount: critBonus,
      totalAmount: critResult.amount,
      baseAmount,
      multiplier: critResult.multiplier
    });
  }
  animateAtomPress({
    critical: critResult.isCritical,
    multiplier: critResult.multiplier,
    context
  });
}

if (typeof globalThis !== 'undefined') {
  globalThis.handleManualAtomClick = handleManualAtomClick;
  globalThis.isManualClickContextActive = isManualClickContextActive;
}

function shouldTriggerGlobalClick(event) {
  if (!isGamePageActive()) return false;
  if (event.target.closest('.app-header')) return false;
  if (event.target.closest('.status-bar')) return false;
  return true;
}

const MANUAL_LAYOUT_PAGES = Object.freeze(['game', 'wave']);

function ensureLayoutPageReady(pageId) {
  if (pageId === 'wave') {
    ensureWaveGame();
  }
  if (pageId === 'balance') {
    ensureBalanceGame();
  }
  if (pageId === 'quantum2048') {
    ensureQuantum2048Game();
  }
}

function handleLayoutPageDisplayed(pageId) {
  if (pageId === 'game') {
    randomizeAtomButtonImage();
  }
  if (pageId === 'metaux' && typeof initMetauxGame === 'function') {
    initMetauxGame();
  }
  if (typeof particulesGame !== 'undefined' && particulesGame) {
    if (pageId === 'arcade') {
      particulesGame.onEnter();
    } else {
      particulesGame.onLeave();
    }
  }
  if (typeof metauxGame !== 'undefined' && metauxGame) {
    if (pageId === 'metaux') {
      metauxGame.onEnter();
    } else {
      metauxGame.onLeave();
    }
  }
  if (waveGame) {
    if (pageId === 'wave') {
      waveGame.onEnter();
    } else {
      waveGame.onLeave();
    }
  }
  if (balanceGame) {
    if (pageId === 'balance') {
      balanceGame.onEnter();
    } else {
      balanceGame.onLeave();
    }
  }
  if (quantum2048Game) {
    if (pageId === 'quantum2048') {
      quantum2048Game.onEnter();
    } else {
      quantum2048Game.onLeave();
    }
  }
}

function handleLayoutManualVisibilityChange({ active, now }) {
  gamePageVisibleSince = active ? now : null;
}

function handleLayoutPageAfterEffects(pageId) {
  if (pageId !== 'gacha') {
    return;
  }
  if (typeof refreshGachaRarities !== 'function') {
    return;
  }
  const weightsUpdated = refreshGachaRarities(new Date());
  if (!weightsUpdated) {
    return;
  }
  if (typeof rebuildGachaPools === 'function') {
    rebuildGachaPools();
  }
  if (typeof renderGachaRarityList === 'function') {
    renderGachaRarityList();
  }
}

function handleLayoutVisibilityHidden(pageId) {
  if (typeof particulesGame !== 'undefined' && particulesGame && pageId === 'arcade') {
    particulesGame.onLeave();
  }
  if (pageId === 'wave' && waveGame) {
    waveGame.onLeave();
  }
  if (pageId === 'quantum2048' && quantum2048Game) {
    quantum2048Game.onLeave();
  }
}

function handleLayoutVisibilityVisible(pageId, { now }) {
  if (isManualClickContextActive()) {
    handleLayoutManualVisibilityChange({ active: true, now, pageId });
    if (typeof particulesGame !== 'undefined' && particulesGame && pageId === 'arcade') {
      particulesGame.onEnter();
    }
    if (pageId === 'wave') {
      ensureWaveGame();
      waveGame?.onEnter();
    }
  } else if (typeof particulesGame !== 'undefined' && particulesGame && pageId === 'arcade') {
    particulesGame.onEnter();
  } else if (pageId === 'wave') {
    ensureWaveGame();
    waveGame?.onEnter();
  }

  if (pageId === 'quantum2048') {
    ensureQuantum2048Game();
    quantum2048Game?.onEnter();
  }
}

const layoutController = initializeLayout({
  elements,
  isPageUnlocked,
  ensurePageReady: ensureLayoutPageReady,
  handlePageDisplayed: handleLayoutPageDisplayed,
  handleManualPageVisibilityChange: handleLayoutManualVisibilityChange,
  handlePageShownAfterEffects: handleLayoutPageAfterEffects,
  updateFrenzyIndicators,
  manualPages: MANUAL_LAYOUT_PAGES,
  handleVisibilityHidden: handleLayoutVisibilityHidden,
  handleVisibilityVisible: handleLayoutVisibilityVisible
});

const { showPage } = layoutController;

if (typeof globalThis !== 'undefined' && typeof showPage === 'function') {
  globalThis.showPage = showPage;
}

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    layoutController.handleVisibilityChange(Boolean(document.hidden));
  });
}

if (elements.devkitOverlay) {
  elements.devkitOverlay.addEventListener('click', event => {
    if (event.target === elements.devkitOverlay) {
      closeDevKit();
    }
  });
}

if (elements.devkitPanel) {
  elements.devkitPanel.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeDevKit();
    }
  });
}

if (elements.devkitClose) {
  elements.devkitClose.addEventListener('click', event => {
    event.preventDefault();
    closeDevKit();
  });
}

if (elements.devkitAtomsForm) {
  elements.devkitAtomsForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = elements.devkitAtomsInput ? elements.devkitAtomsInput.value : '';
    handleDevKitAtomsSubmission(value);
    if (elements.devkitAtomsInput) {
      elements.devkitAtomsInput.value = '';
    }
  });
}

if (elements.devkitAutoForm) {
  elements.devkitAutoForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = elements.devkitAutoInput ? elements.devkitAutoInput.value : '';
    handleDevKitAutoSubmission(value);
    if (elements.devkitAutoInput) {
      elements.devkitAutoInput.value = '';
    }
  });
}

if (elements.devkitOfflineTimeForm) {
  elements.devkitOfflineTimeForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = elements.devkitOfflineTimeInput ? elements.devkitOfflineTimeInput.value : '';
    handleDevKitOfflineAdvance(value);
    if (elements.devkitOfflineTimeInput) {
      elements.devkitOfflineTimeInput.value = '';
    }
  });
}

if (elements.devkitOnlineTimeForm) {
  elements.devkitOnlineTimeForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = elements.devkitOnlineTimeInput ? elements.devkitOnlineTimeInput.value : '';
    handleDevKitOnlineAdvance(value);
    if (elements.devkitOnlineTimeInput) {
      elements.devkitOnlineTimeInput.value = '';
    }
  });
}

if (elements.devkitAutoReset) {
  elements.devkitAutoReset.addEventListener('click', event => {
    event.preventDefault();
    resetDevKitAutoBonus();
  });
}

if (elements.devkitTicketsForm) {
  elements.devkitTicketsForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = elements.devkitTicketsInput ? elements.devkitTicketsInput.value : '';
    handleDevKitTicketSubmission(value);
    if (elements.devkitTicketsInput) {
      elements.devkitTicketsInput.value = '';
    }
  });
}

if (elements.devkitMach3TicketsForm) {
  elements.devkitMach3TicketsForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = elements.devkitMach3TicketsInput ? elements.devkitMach3TicketsInput.value : '';
    handleDevKitMach3TicketSubmission(value);
    if (elements.devkitMach3TicketsInput) {
      elements.devkitMach3TicketsInput.value = '';
    }
  });
}

if (elements.devkitUnlockTrophies) {
  elements.devkitUnlockTrophies.addEventListener('click', event => {
    event.preventDefault();
    devkitUnlockAllTrophies();
  });
}

if (elements.devkitUnlockElements) {
  elements.devkitUnlockElements.addEventListener('click', event => {
    event.preventDefault();
    devkitUnlockAllElements();
  });
}

if (elements.devkitUnlockInfo) {
  elements.devkitUnlockInfo.addEventListener('click', event => {
    event.preventDefault();
    const unlocked = unlockPage('info', {
      save: true,
      announce: t('scripts.app.devkit.infoPageUnlocked')
    });
    if (!unlocked) {
      showToast(t('scripts.app.devkit.infoPageAlready'));
    }
    updateDevKitUI();
  });
}

if (elements.devkitToggleShop) {
  elements.devkitToggleShop.addEventListener('click', event => {
    event.preventDefault();
    toggleDevKitCheat('freeShop');
  });
}

if (elements.devkitToggleGacha) {
  elements.devkitToggleGacha.addEventListener('click', event => {
    event.preventDefault();
    toggleDevKitCheat('freeGacha');
  });
}

document.addEventListener('keydown', event => {
  if (event.key === 'F9') {
    event.preventDefault();
    toggleDevKit();
  } else if (event.key === 'Escape' && DEVKIT_STATE.isOpen) {
    event.preventDefault();
    closeDevKit();
  }
});

updateDevKitUI();

initParticulesGame();

document.addEventListener('click', event => {
  if (!shouldTriggerGlobalClick(event)) return;
  handleManualAtomClick({ contextId: 'game' });
});

document.addEventListener('selectstart', event => {
  if (isManualClickContextActive()) {
    event.preventDefault();
  }
});

function gainAtoms(amount, source = 'generic') {
  gameState.atoms = gameState.atoms.add(amount);
  gameState.lifetime = gameState.lifetime.add(amount);
  if (gameState.stats) {
    const session = gameState.stats.session;
    const global = gameState.stats.global;
    if (session?.atomsGained) {
      session.atomsGained = session.atomsGained.add(amount);
    }
    if (source === 'apc') {
      incrementLayeredStat(session, 'apcAtoms', amount);
      incrementLayeredStat(global, 'apcAtoms', amount);
    } else if (source === 'offline') {
      incrementLayeredStat(session, 'apsAtoms', amount);
      incrementLayeredStat(session, 'offlineAtoms', amount);
      incrementLayeredStat(global, 'apsAtoms', amount);
      incrementLayeredStat(global, 'offlineAtoms', amount);
    } else if (source === 'aps') {
      incrementLayeredStat(session, 'apsAtoms', amount);
      incrementLayeredStat(global, 'apsAtoms', amount);
    }
  }
  evaluateTrophies();
}

function getUpgradeLevel(state, id) {
  if (!state || typeof state !== 'object') {
    return 0;
  }
  const value = Number(state[id] ?? 0);
  return Number.isFinite(value) && value > 0 ? value : 0;
}

function resolveUpgradeMaxLevel(definition) {
  const raw = definition?.maxLevel ?? definition?.maxPurchase;
  const numeric = Number(raw);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.max(1, Math.floor(numeric));
  }
  return DEFAULT_UPGRADE_MAX_LEVEL;
}

function getRemainingUpgradeCapacity(definition) {
  const maxLevel = resolveUpgradeMaxLevel(definition);
  if (!Number.isFinite(maxLevel)) {
    return Infinity;
  }
  const currentLevel = getUpgradeLevel(gameState.upgrades, definition.id);
  const remaining = Math.floor(maxLevel - currentLevel);
  return Math.max(0, remaining);
}

function computeGlobalCostModifier() {
  return 1;
}

function computeUpgradeCost(def, quantity = 1) {
  if (isDevKitShopFree()) {
    return LayeredNumber.zero();
  }
  const level = getUpgradeLevel(gameState.upgrades, def.id);
  const baseScale = def.costScale ?? 1;
  const modifier = computeGlobalCostModifier();
  const baseCost = def.baseCost;
  const buyAmount = Math.max(1, Math.floor(Number(quantity) || 0));

  if (buyAmount === 1 || !Number.isFinite(baseScale) || baseScale === 1) {
    const singleCost = baseCost * Math.pow(baseScale, level) * modifier;
    return new LayeredNumber(singleCost * buyAmount);
  }

  const startScale = Math.pow(baseScale, level);
  const scalePow = Math.pow(baseScale, buyAmount);
  const sumFactor = (scalePow - 1) / (baseScale - 1);
  const totalCost = baseCost * startScale * sumFactor * modifier;
  return new LayeredNumber(totalCost);
}

function formatShopCost(cost) {
  const value = cost instanceof LayeredNumber ? cost : new LayeredNumber(cost);
  let display = '';

  if (value instanceof LayeredNumber) {
    if (value.layer === 0) {
      const numeric = value.toNumber();
      if (Number.isFinite(numeric)) {
        if (Math.abs(numeric) < 1_000_000) {
          const rounded = Math.round(numeric);
          display = formatIntegerLocalized(rounded);
        }
      }
    }

    if (!display) {
      display = value.toString();
    }
  }

  return translateOrDefault('scripts.app.shop.costLabel', display, {
    value: display
  });
}

function recalcProduction() {
  const clickBase = normalizeProductionUnit(BASE_PER_CLICK);
  const autoBase = normalizeProductionUnit(BASE_PER_SECOND);

  const clickDetails = createEmptyProductionEntry();
  const autoDetails = createEmptyProductionEntry();

  clickDetails.base = clickBase.clone();
  autoDetails.base = autoBase.clone();
  clickDetails.sources.flats.baseFlat = clickBase.clone();
  autoDetails.sources.flats.baseFlat = autoBase.clone();

  let clickShopAddition = LayeredNumber.zero();
  let autoShopAddition = LayeredNumber.zero();
  let clickElementAddition = LayeredNumber.zero();
  let autoElementAddition = LayeredNumber.zero();
  let clickFusionAddition = LayeredNumber.zero();
  let autoFusionAddition = LayeredNumber.zero();
  const critAccumulator = createCritAccumulator();

  let clickShopMultiplier = LayeredNumber.one();
  let autoShopMultiplier = LayeredNumber.one();

  const clickRarityMultipliers = clickDetails.sources.multipliers.rarityMultipliers;
  const autoRarityMultipliers = autoDetails.sources.multipliers.rarityMultipliers;

  const elementCountsByRarity = new Map();
  const elementCountsByFamily = new Map();
  const elementGroupSummaries = new Map();
  const familySummaries = new Map();
  const mythiqueBonuses = {
    ticketIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
    offlineMultiplier: MYTHIQUE_OFFLINE_BASE,
    frenzyChanceMultiplier: 1
  };
  const formatMultiplierTooltip = value => {
    const display = formatElementMultiplierDisplay(value);
    if (display) {
      return display;
    }
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || Math.abs(numeric - 1) < 1e-9) {
      return null;
    }
    const abs = Math.abs(numeric);
    const options = abs >= 100
      ? { maximumFractionDigits: 0 }
      : abs >= 10
        ? { maximumFractionDigits: 1 }
        : { maximumFractionDigits: 2 };
    return `×${formatNumberLocalized(numeric, options)}`;
  };
  const getRarityCounter = rarityId => {
    if (!rarityId) return null;
    let counter = elementCountsByRarity.get(rarityId);
    if (!counter) {
      counter = { copies: 0, unique: 0, active: 0 };
      elementCountsByRarity.set(rarityId, counter);
    }
    return counter;
  };

  const elementEntries = Object.values(gameState.elements || {});
  elementEntries.forEach(entry => {
    if (!entry) return;
    const rarityId = entry.rarity || elementRarityIndex.get(entry.id);
    if (!rarityId) return;
    const normalizedCount = getElementLifetimeCount(entry);
    if (normalizedCount <= 0) return;
    const counter = getRarityCounter(rarityId);
    if (!counter) return;
    const activeCount = Math.max(0, getElementCurrentCount(entry));
    counter.copies += normalizedCount;
    counter.unique += 1;
    counter.active += activeCount;

    const definition = periodicElementIndex.get(entry.id);
    const familyId = definition?.category;
    if (familyId) {
      let familyCounter = elementCountsByFamily.get(familyId);
      if (!familyCounter) {
        familyCounter = { copies: 0, unique: 0, active: 0 };
        elementCountsByFamily.set(familyId, familyCounter);
      }
      familyCounter.copies += normalizedCount;
      familyCounter.unique += 1;
      familyCounter.active += activeCount;
    }
  });

  const singularityCounts = elementCountsByRarity.get('singulier') || { copies: 0, unique: 0, active: 0 };
  const singularityPoolSize = getRarityPoolSize('singulier');
  const isSingularityComplete = singularityPoolSize > 0 && singularityCounts.unique >= singularityPoolSize;
  const stellaireSingularityBoost = isSingularityComplete ? 2 : 1;
  const STELLAIRE_SINGULARITY_BONUS_LABEL = 'Étoiles simples · Supernovae amplifiées';

  const addClickElementFlat = (value, { id, label, rarityId, source = 'elements' }) => {
    if (value == null) return 0;
    const layeredValue = value instanceof LayeredNumber ? value.clone() : new LayeredNumber(value);
    if (layeredValue.isZero()) return 0;
    let finalValue = layeredValue;
    if (stellaireSingularityBoost !== 1 && rarityId === 'stellaire') {
      finalValue = finalValue.multiplyNumber(stellaireSingularityBoost);
    }
    clickElementAddition = clickElementAddition.add(finalValue);
    clickDetails.additions.push({
      id,
      label,
      value: finalValue.clone(),
      source
    });
    return finalValue.toNumber();
  };

  const addAutoElementFlat = (value, { id, label, rarityId, source = 'elements' }) => {
    if (value == null) return 0;
    const layeredValue = value instanceof LayeredNumber ? value.clone() : new LayeredNumber(value);
    if (layeredValue.isZero()) return 0;
    let finalValue = layeredValue;
    if (stellaireSingularityBoost !== 1 && rarityId === 'stellaire') {
      finalValue = finalValue.multiplyNumber(stellaireSingularityBoost);
    }
    autoElementAddition = autoElementAddition.add(finalValue);
    autoDetails.additions.push({
      id,
      label,
      value: finalValue.clone(),
      source
    });
    return finalValue.toNumber();
  };

  const updateRarityMultiplierDetail = (details, detailId, label, value) => {
    if (!details) return;
    const layeredValue = value instanceof LayeredNumber ? value.clone() : new LayeredNumber(value);
    const isNeutral = isLayeredOne(layeredValue);
    const index = details.findIndex(entry => entry.id === detailId);
    if (isNeutral) {
      if (index >= 0) {
        details.splice(index, 1);
      }
      return;
    }
    if (index >= 0) {
      details[index].value = layeredValue.clone();
      if (label) {
        details[index].label = label;
      }
    } else {
      details.push({
        id: detailId,
        label: label || detailId,
        value: layeredValue.clone(),
        source: 'elements'
      });
    }
  };

  ELEMENT_GROUP_BONUS_CONFIG.forEach((groupConfig, rarityId) => {
    const { copies: copyCount = 0, unique: uniqueCount = 0, active: activeCount = 0 } =
      elementCountsByRarity.get(rarityId) || {};
    const rarityLabel = RARITY_LABEL_MAP.get(rarityId) || rarityId;
    const copyLabel = groupConfig.labels?.perCopy || `${rarityLabel} · copies`;
    const setBonusLabel = groupConfig.labels?.setBonus || `${rarityLabel} · bonus de groupe`;
    const multiplierDetailId = `elements:${rarityId}:multiplier`;
    const multiplierLabel = groupConfig.labels?.multiplier || rarityLabel;
    const rarityMultiplierLabel = groupConfig.rarityMultiplierBonus?.label
      || groupConfig.labels?.rarityMultiplier
      || multiplierLabel;
    const duplicateCount = Math.max(0, copyCount - uniqueCount);
    const totalUnique = getRarityPoolSize(rarityId);
    const stellaireBoostActive = stellaireSingularityBoost !== 1 && rarityId === 'stellaire';
    const activeLabelDetails = new Map();
    const normalizeLabelKey = label => {
      if (typeof label !== 'string') return '';
      return label.trim();
    };
    const ensureActiveLabel = (label, type = null) => {
      const key = normalizeLabelKey(label);
      if (!key) return null;
      if (!activeLabelDetails.has(key)) {
        activeLabelDetails.set(key, {
          label: key,
          effects: [],
          notes: [],
          types: new Set()
        });
      }
      const entry = activeLabelDetails.get(key);
      if (type) {
        entry.types.add(type);
      }
      return entry;
    };
    const addLabelEffect = (label, effectText, type = null) => {
      if (!effectText) return;
      const entry = ensureActiveLabel(label, type);
      if (!entry) return;
      if (!entry.effects.includes(effectText)) {
        entry.effects.push(effectText);
      }
    };
    const addLabelNote = (label, noteText, type = null) => {
      if (!noteText) return;
      const entry = ensureActiveLabel(label, type);
      if (!entry) return;
      if (!entry.notes.includes(noteText)) {
        entry.notes.push(noteText);
      }
    };
    const markLabelActive = (label, type = null) => {
      ensureActiveLabel(label, type);
    };
    const summary = {
      type: 'rarity',
      rarityId,
      label: rarityLabel,
      copies: copyCount,
      uniques: uniqueCount,
      duplicates: duplicateCount,
      totalUnique,
      activeCopies: Math.max(0, Number(activeCount) || 0),
      isComplete: totalUnique > 0 && uniqueCount >= totalUnique,
      clickFlatTotal: 0,
      autoFlatTotal: 0,
      critChanceAdd: 0,
      critMultiplierAdd: 0,
      activeLabels: []
    };

    if (copyCount > 0 && groupConfig.perCopy) {
      const {
        clickAdd = 0,
        autoAdd = 0,
        uniqueClickAdd = 0,
        uniqueAutoAdd = 0,
        duplicateClickAdd = 0,
        duplicateAutoAdd = 0,
        minCopies = 0,
        minUnique = 0
      } = groupConfig.perCopy;
      const meetsCopyRequirement = copyCount >= Math.max(1, minCopies);
      const meetsUniqueRequirement = uniqueCount >= Math.max(0, minUnique);
      if (meetsCopyRequirement && meetsUniqueRequirement) {
        let hasEffect = false;
        if (clickAdd) {
          const totalClickAdd = clickAdd * copyCount;
          const applied = addClickElementFlat(totalClickAdd, {
            id: `elements:${rarityId}:copies`,
            label: copyLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.clickFlatTotal += applied;
            hasEffect = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(copyLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'perCopy');
            }
          }
        }
        if (autoAdd) {
          const totalAutoAdd = autoAdd * copyCount;
          const applied = addAutoElementFlat(totalAutoAdd, {
            id: `elements:${rarityId}:copies`,
            label: copyLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.autoFlatTotal += applied;
            hasEffect = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(copyLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'perCopy');
            }
          }
        }
        if (uniqueClickAdd && uniqueCount > 0) {
          const totalUniqueClick = uniqueClickAdd * uniqueCount;
          const applied = addClickElementFlat(totalUniqueClick, {
            id: `elements:${rarityId}:unique`,
            label: copyLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.clickFlatTotal += applied;
            hasEffect = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(copyLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'perCopy');
            }
          }
        }
        if (uniqueAutoAdd && uniqueCount > 0) {
          const totalUniqueAuto = uniqueAutoAdd * uniqueCount;
          const applied = addAutoElementFlat(totalUniqueAuto, {
            id: `elements:${rarityId}:unique`,
            label: copyLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.autoFlatTotal += applied;
            hasEffect = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(copyLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'perCopy');
            }
          }
        }
        if (duplicateClickAdd && duplicateCount > 0) {
          const totalDuplicateClick = duplicateClickAdd * duplicateCount;
          const applied = addClickElementFlat(totalDuplicateClick, {
            id: `elements:${rarityId}:duplicates`,
            label: copyLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.clickFlatTotal += applied;
            hasEffect = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(copyLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'perCopy');
            }
          }
        }
        if (duplicateAutoAdd && duplicateCount > 0) {
          const totalDuplicateAuto = duplicateAutoAdd * duplicateCount;
          const applied = addAutoElementFlat(totalDuplicateAuto, {
            id: `elements:${rarityId}:duplicates`,
            label: copyLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.autoFlatTotal += applied;
            hasEffect = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(copyLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'perCopy');
            }
          }
        }
        if (hasEffect) {
          markLabelActive(copyLabel, 'perCopy');
        }
      }
    }

    const setBonuses = Array.isArray(groupConfig.setBonuses)
      ? groupConfig.setBonuses
      : (groupConfig.setBonus ? [groupConfig.setBonus] : []);
    if (uniqueCount > 0 && setBonuses.length) {
      setBonuses.forEach((setBonusEntry, index) => {
        if (!setBonusEntry) return;
        const {
          clickAdd = 0,
          autoAdd = 0,
          uniqueClickAdd = 0,
          uniqueAutoAdd = 0,
          duplicateClickAdd = 0,
          duplicateAutoAdd = 0,
          minCopies = 0,
          minUnique = 0,
          requireAllUnique = false,
          label: entryLabel
        } = setBonusEntry;
        const requiredCopies = Math.max(0, minCopies);
        let requiredUnique = Math.max(0, minUnique);
        if (requireAllUnique) {
          const poolSize = getRarityPoolSize(rarityId);
          if (poolSize > 0) {
            requiredUnique = Math.max(requiredUnique, poolSize);
          } else if (requiredUnique === 0) {
            requiredUnique = uniqueCount;
          }
        }
        const meetsCopyRequirement = copyCount >= requiredCopies;
        const meetsUniqueRequirement = requiredUnique > 0
          ? uniqueCount >= requiredUnique
          : uniqueCount > 0;
        if (!meetsCopyRequirement || !meetsUniqueRequirement) {
          return;
        }
        const resolvedLabel = entryLabel || setBonusLabel;
        let setBonusTriggered = false;
        if (clickAdd) {
          const applied = addClickElementFlat(clickAdd, {
            id: `elements:${rarityId}:groupFlat:${index}`,
            label: resolvedLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.clickFlatTotal += applied;
            setBonusTriggered = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'setBonus');
            }
          }
        }
        if (autoAdd) {
          const applied = addAutoElementFlat(autoAdd, {
            id: `elements:${rarityId}:groupFlat:${index}`,
            label: resolvedLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.autoFlatTotal += applied;
            setBonusTriggered = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'setBonus');
            }
          }
        }
        if (uniqueClickAdd && uniqueCount > 0) {
          const totalUniqueClick = uniqueClickAdd * uniqueCount;
          const applied = addClickElementFlat(totalUniqueClick, {
            id: `elements:${rarityId}:groupUnique:${index}`,
            label: resolvedLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.clickFlatTotal += applied;
            setBonusTriggered = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'setBonus');
            }
          }
        }
        if (uniqueAutoAdd && uniqueCount > 0) {
          const totalUniqueAuto = uniqueAutoAdd * uniqueCount;
          const applied = addAutoElementFlat(totalUniqueAuto, {
            id: `elements:${rarityId}:groupUnique:${index}`,
            label: resolvedLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.autoFlatTotal += applied;
            setBonusTriggered = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'setBonus');
            }
          }
        }
        if (duplicateClickAdd && duplicateCount > 0) {
          const totalDuplicateClick = duplicateClickAdd * duplicateCount;
          const applied = addClickElementFlat(totalDuplicateClick, {
            id: `elements:${rarityId}:groupDuplicate:${index}`,
            label: resolvedLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.clickFlatTotal += applied;
            setBonusTriggered = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'setBonus');
            }
          }
        }
        if (duplicateAutoAdd && duplicateCount > 0) {
          const totalDuplicateAuto = duplicateAutoAdd * duplicateCount;
          const applied = addAutoElementFlat(totalDuplicateAuto, {
            id: `elements:${rarityId}:groupDuplicate:${index}`,
            label: resolvedLabel,
            rarityId
          });
          if (Number.isFinite(applied) && applied !== 0) {
            summary.autoFlatTotal += applied;
            setBonusTriggered = true;
            const formatted = formatElementFlatBonus(applied);
            if (formatted) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'setBonus');
            }
          }
        }
        if (setBonusTriggered) {
          markLabelActive(resolvedLabel, 'setBonus');
        }
      });
    }

    if (groupConfig.multiplier) {
      const { base, every, increment, targets, label: multiplierLabelOverride } = groupConfig.multiplier;
      let finalMultiplier = Number.isFinite(base) && base > 0 ? base : 1;
      if (copyCount > 0 && every > 0 && increment !== 0) {
        const steps = Math.floor(copyCount / every);
        if (steps > 0) {
          finalMultiplier += steps * increment;
        }
      }
      if (!Number.isFinite(finalMultiplier) || finalMultiplier <= 0) {
        finalMultiplier = 1;
      }
      const multiplierLabelResolved = multiplierLabelOverride || multiplierLabel;
      const appliedMultiplier = stellaireBoostActive
        ? finalMultiplier * stellaireSingularityBoost
        : finalMultiplier;
      let multiplierApplied = false;
      if (targets.has('perClick')) {
        clickRarityMultipliers.set(rarityId, appliedMultiplier);
        updateRarityMultiplierDetail(clickDetails.multipliers, multiplierDetailId, multiplierLabelResolved, appliedMultiplier);
        multiplierApplied = multiplierApplied || Math.abs(appliedMultiplier - 1) > 1e-9;
      }
      if (targets.has('perSecond')) {
        autoRarityMultipliers.set(rarityId, appliedMultiplier);
        updateRarityMultiplierDetail(autoDetails.multipliers, multiplierDetailId, multiplierLabelResolved, appliedMultiplier);
        multiplierApplied = multiplierApplied || Math.abs(appliedMultiplier - 1) > 1e-9;
      }
      if (multiplierApplied) {
        markLabelActive(multiplierLabelResolved, 'multiplier');
        const multiplierText = formatMultiplierTooltip(appliedMultiplier);
        if (multiplierText) {
          if (targets.has('perClick')) {
            addLabelEffect(multiplierLabelResolved, translateCollectionEffect('apcMultiplier', `APC ${multiplierText}`, { value: multiplierText }), 'multiplier');
          }
          if (targets.has('perSecond')) {
            addLabelEffect(multiplierLabelResolved, translateCollectionEffect('apsMultiplier', `APS ${multiplierText}`, { value: multiplierText }), 'multiplier');
          }
        }
      }
    }

    if (groupConfig.crit) {
      let critApplied = false;
      if (groupConfig.crit.perUnique) {
        applyRepeatedCritEffect(critAccumulator, groupConfig.crit.perUnique, uniqueCount);
        const chanceAdd = Number(groupConfig.crit.perUnique.chanceAdd) || 0;
        const multiplierAdd = Number(groupConfig.crit.perUnique.multiplierAdd) || 0;
        if (chanceAdd !== 0 && uniqueCount > 0) {
          summary.critChanceAdd += chanceAdd * uniqueCount;
          critApplied = true;
        }
        if (multiplierAdd !== 0 && uniqueCount > 0) {
          summary.critMultiplierAdd += multiplierAdd * uniqueCount;
          critApplied = true;
        }
      }
      if (groupConfig.crit.perDuplicate) {
        applyRepeatedCritEffect(critAccumulator, groupConfig.crit.perDuplicate, duplicateCount);
        const chanceAdd = Number(groupConfig.crit.perDuplicate.chanceAdd) || 0;
        const multiplierAdd = Number(groupConfig.crit.perDuplicate.multiplierAdd) || 0;
        if (chanceAdd !== 0 && duplicateCount > 0) {
          summary.critChanceAdd += chanceAdd * duplicateCount;
          critApplied = true;
        }
        if (multiplierAdd !== 0 && duplicateCount > 0) {
          summary.critMultiplierAdd += multiplierAdd * duplicateCount;
          critApplied = true;
        }
      }
      if (critApplied) {
        const critLabel = translateCollectionLabel('crit', 'Critique');
        markLabelActive(critLabel, 'crit');
        const chanceText = formatElementCritChanceBonus(summary.critChanceAdd);
        if (chanceText) {
          addLabelEffect(critLabel, translateCollectionEffect('critChance', `Chance +${chanceText}`, { value: chanceText }), 'crit');
        }
        const critMultiplierText = formatElementCritMultiplierBonus(summary.critMultiplierAdd);
        if (critMultiplierText) {
          addLabelEffect(critLabel, translateCollectionEffect('critMultiplier', `Multiplicateur +${critMultiplierText}×`, { value: critMultiplierText }), 'crit');
        }
      }
    }

    if (groupConfig.rarityMultiplierBonus) {
      const {
        amount = 0,
        uniqueThreshold = 0,
        copyThreshold = 0,
        targets,
        label: bonusLabel
      } = groupConfig.rarityMultiplierBonus;
      if (amount !== 0) {
        const meetsUnique = uniqueThreshold > 0 ? uniqueCount >= uniqueThreshold : true;
        const meetsCopies = copyThreshold > 0 ? copyCount >= copyThreshold : true;
        if (meetsUnique && meetsCopies) {
          const resolvedLabel = bonusLabel || rarityMultiplierLabel;
          let labelApplied = false;
          if (targets.has('perClick')) {
            const current = Number(clickRarityMultipliers.get(rarityId)) || 1;
            const updated = Math.max(0, current + amount);
            clickRarityMultipliers.set(rarityId, updated);
            updateRarityMultiplierDetail(clickDetails.multipliers, multiplierDetailId, resolvedLabel, updated);
            const display = formatMultiplierTooltip(updated);
            if (display) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('rarityMultiplierApc', `Multiplicateur de rareté APC ${display}`, { value: display }), 'rarityMultiplier');
              labelApplied = true;
            }
          }
          if (targets.has('perSecond')) {
            const current = Number(autoRarityMultipliers.get(rarityId)) || 1;
            const updated = Math.max(0, current + amount);
            autoRarityMultipliers.set(rarityId, updated);
            updateRarityMultiplierDetail(autoDetails.multipliers, multiplierDetailId, resolvedLabel, updated);
            const display = formatMultiplierTooltip(updated);
            if (display) {
              addLabelEffect(resolvedLabel, translateCollectionEffect('rarityMultiplierAps', `Multiplicateur de rareté APS ${display}`, { value: display }), 'rarityMultiplier');
              labelApplied = true;
            }
          }
          if (labelApplied) {
            markLabelActive(resolvedLabel, 'rarityMultiplier');
          }
        }
      }
    }

    if (rarityId === MYTHIQUE_RARITY_ID) {
      const labels = groupConfig?.labels || {};
      const resolveLabel = (keys, fallback) => {
        const searchKeys = Array.isArray(keys) ? keys : [keys];
        for (const key of searchKeys) {
          if (typeof key !== 'string' || !key) {
            continue;
          }
          const raw = typeof labels[key] === 'string' ? labels[key].trim() : '';
          if (raw) {
            return raw;
          }
        }
        return fallback;
      };
      const ticketLabel = resolveLabel(
        ['ticketBonus', 'ticket'],
        `${rarityLabel} · accélération quantique`
      );
      const offlineLabel = resolveLabel(
        ['offlineBonus', 'offline'],
        `${rarityLabel} · collecte hors ligne`
      );
      const overflowLabel = resolveLabel(
        ['duplicateOverflow', 'overflow'],
        `${rarityLabel} · surcharge fractale`
      );
      const baseTicketSeconds = DEFAULT_TICKET_STAR_INTERVAL_SECONDS;
      // La réduction de l'intervalle des tickets dépend uniquement des éléments
      // mythiques uniques possédés, et non du nombre total de copies.
      const ticketSeconds = Math.max(
        MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS,
        baseTicketSeconds - uniqueCount * MYTHIQUE_TICKET_UNIQUE_REDUCTION_SECONDS
      );
      mythiqueBonuses.ticketIntervalSeconds = ticketSeconds;
      summary.ticketIntervalSeconds = ticketSeconds;
      if (baseTicketSeconds - ticketSeconds > 1e-9) {
        markLabelActive(ticketLabel, 'ticket');
        const ticketText = formatElementTicketInterval(summary.ticketIntervalSeconds);
        if (ticketText) {
          addLabelEffect(ticketLabel, ticketText, 'ticket');
        }
      }

      const duplicates = duplicateCount;
      // Chaque duplicata mythique supplémentaire augmente le multiplicateur
      // hors ligne jusqu'à atteindre le plafond défini dans la configuration.
      const multiplierDuplicates = Math.min(duplicates, MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP);
      const offlineMultiplier = Math.min(
        MYTHIQUE_OFFLINE_CAP,
        MYTHIQUE_OFFLINE_BASE + multiplierDuplicates * MYTHIQUE_OFFLINE_PER_DUPLICATE
      );
      mythiqueBonuses.offlineMultiplier = offlineMultiplier;
      summary.offlineMultiplier = offlineMultiplier;
      if (offlineMultiplier > MYTHIQUE_OFFLINE_BASE + 1e-9) {
        markLabelActive(offlineLabel, 'offline');
        const offlineText = formatMultiplierTooltip(offlineMultiplier);
        if (offlineText) {
          addLabelEffect(offlineLabel, translateCollectionEffect('offline', `Collecte hors ligne ${offlineText}`, { value: offlineText }), 'offline');
        }
      }

      const overflowDuplicates = Math.max(0, duplicates - MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP);
      summary.overflowDuplicates = overflowDuplicates;
      if (overflowDuplicates > 0) {
        const clickLabel = overflowLabel;
        const autoLabel = overflowLabel;
        const overflowClick = addClickElementFlat(
          MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS * overflowDuplicates,
          {
            id: `elements:${rarityId}:overflow:click`,
            label: clickLabel,
            rarityId
          }
        );
        if (Number.isFinite(overflowClick) && overflowClick !== 0) {
          summary.clickFlatTotal += overflowClick;
          const formatted = formatElementFlatBonus(overflowClick);
          if (formatted) {
            addLabelEffect(overflowLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'overflow');
          }
        }
        const overflowAuto = addAutoElementFlat(
          MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS * overflowDuplicates,
          {
            id: `elements:${rarityId}:overflow:auto`,
            label: autoLabel,
            rarityId
          }
        );
        if (Number.isFinite(overflowAuto) && overflowAuto !== 0) {
          summary.autoFlatTotal += overflowAuto;
          const formatted = formatElementFlatBonus(overflowAuto);
          if (formatted) {
            addLabelEffect(overflowLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'overflow');
          }
        }
        if (
          (Number.isFinite(overflowClick) && overflowClick !== 0)
          || (Number.isFinite(overflowAuto) && overflowAuto !== 0)
        ) {
          markLabelActive(overflowLabel, 'overflow');
        }
      } else {
        summary.overflowDuplicates = 0;
      }

      // Le bonus de frénésie n'est appliqué que lorsque la collection mythique
      // est entièrement complétée.
      const frenzyMultiplier = summary.isComplete
        ? MYTHIQUE_FRENZY_SPAWN_BONUS_MULTIPLIER
        : 1;
      mythiqueBonuses.frenzyChanceMultiplier = frenzyMultiplier;
      summary.frenzyChanceMultiplier = frenzyMultiplier;
      if (frenzyMultiplier !== 1) {
        const frenzyLabel = resolveLabel(
          ['frenzyBonus', 'setBonus'],
          `${rarityLabel} · convergence totale`
        );
        markLabelActive(frenzyLabel, 'frenzy');
        const frenzyText = formatMultiplierTooltip(frenzyMultiplier);
        if (frenzyText) {
          addLabelEffect(frenzyLabel, translateCollectionEffect('frenzy', `Chance de frénésie ${frenzyText}`, { value: frenzyText }), 'frenzy');
        }
      }
    }

    if (stellaireBoostActive && copyCount > 0) {
      markLabelActive(STELLAIRE_SINGULARITY_BONUS_LABEL, 'synergy');
      const singularityText = formatMultiplierTooltip(stellaireSingularityBoost);
      if (singularityText) {
        addLabelNote(
          STELLAIRE_SINGULARITY_BONUS_LABEL,
          translateCollectionNote('singularityBoost', `Singularité amplifiée : effets ${singularityText}`, { value: singularityText }),
          'synergy'
        );
      }
    }

    summary.isComplete = totalUnique > 0 && uniqueCount >= totalUnique;
    const labelEntries = [];
    activeLabelDetails.forEach(entry => {
      if (!entry || !entry.label) return;
      const effects = Array.isArray(entry.effects)
        ? entry.effects.filter(text => typeof text === 'string' && text.trim().length > 0)
        : [];
      const notes = Array.isArray(entry.notes)
        ? entry.notes.filter(text => typeof text === 'string' && text.trim().length > 0)
        : [];
      const descriptionParts = [];
      if (effects.length) {
        descriptionParts.push(effects.join(' · '));
      }
      notes.forEach(note => descriptionParts.push(note));
      const labelEntry = { label: entry.label };
      if (effects.length) {
        labelEntry.effects = effects;
      }
      if (notes.length) {
        labelEntry.notes = notes;
      }
      if (entry.types instanceof Set && entry.types.size) {
        labelEntry.types = Array.from(entry.types);
      } else if (Array.isArray(entry.types) && entry.types.length) {
        labelEntry.types = [...entry.types];
      }
      if (descriptionParts.length) {
        labelEntry.description = descriptionParts.join(' · ');
      }
      labelEntries.push(labelEntry);
    });
    summary.activeLabels = labelEntries;
    elementGroupSummaries.set(rarityId, summary);
  });

  if (ELEMENT_FAMILY_CONFIG.size > 0) {
    ELEMENT_FAMILY_CONFIG.forEach((familyConfig, familyId) => {
      const counts = elementCountsByFamily.get(familyId) || { copies: 0, unique: 0, active: 0 };
      const copyCount = Math.max(0, Number(counts.copies) || 0);
      const uniqueCount = Math.max(0, Number(counts.unique) || 0);
      const activeCount = Math.max(0, Number(counts.active) || 0);
      const duplicateCount = Math.max(0, copyCount - uniqueCount);
      const totalUnique = getFamilyPoolSize(familyId);
      const label = normalizeLabel(familyConfig.label) || CATEGORY_LABELS[familyId] || familyId;
      const summary = {
        type: 'family',
        familyId,
        label,
        copies: copyCount,
        uniques: uniqueCount,
        duplicates: duplicateCount,
        totalUnique,
        activeCopies: activeCount,
        isComplete: totalUnique > 0 && uniqueCount >= totalUnique,
        clickFlatTotal: 0,
        autoFlatTotal: 0,
        critChanceAdd: 0,
        critMultiplierAdd: 0,
        activeLabels: [],
        ticketIntervalSeconds: null,
        offlineMultiplier: 1,
        frenzyChanceMultiplier: 1,
        overflowDuplicates: 0
      };

      const activeLabelDetails = new Map();
      const normalizeLabelKey = value => {
        if (typeof value !== 'string') return '';
        return value.trim();
      };
      const ensureActiveLabel = (labelText, type = null) => {
        const key = normalizeLabelKey(labelText);
        if (!key) return null;
        if (!activeLabelDetails.has(key)) {
          activeLabelDetails.set(key, {
            label: key,
            effects: [],
            notes: [],
            types: new Set()
          });
        }
        const entry = activeLabelDetails.get(key);
        if (type) {
          entry.types.add(type);
        }
        return entry;
      };
      const addLabelEffect = (labelText, effectText, type = null) => {
        if (!effectText) return;
        const entry = ensureActiveLabel(labelText, type);
        if (!entry) return;
        if (!entry.effects.includes(effectText)) {
          entry.effects.push(effectText);
        }
      };
      const addLabelNote = (labelText, noteText, type = null) => {
        if (!noteText) return;
        const entry = ensureActiveLabel(labelText, type);
        if (!entry) return;
        if (!entry.notes.includes(noteText)) {
          entry.notes.push(noteText);
        }
      };
      const markLabelActive = (labelText, type = null) => {
        ensureActiveLabel(labelText, type);
      };

      if (summary.isComplete) {
        const bonuses = Array.isArray(familyConfig.bonuses) ? familyConfig.bonuses : [];
        bonuses.forEach((bonus, index) => {
          if (!bonus || typeof bonus !== 'object') {
            return;
          }
          const effectLabel = normalizeLabel(bonus.label) || label;
          const effectIdBase = typeof bonus.id === 'string' && bonus.id.trim()
            ? bonus.id.trim()
            : `${familyId}:bonus:${index + 1}`;
          const effects = bonus.effects && typeof bonus.effects === 'object' ? bonus.effects : {};
          let bonusApplied = false;

          if (typeof bonus.description === 'string' && bonus.description.trim()) {
            addLabelNote(effectLabel, bonus.description.trim(), 'description');
          }
          if (Array.isArray(bonus.notes)) {
            bonus.notes.forEach(note => {
              if (typeof note === 'string' && note.trim()) {
                addLabelNote(effectLabel, note.trim(), 'note');
              }
            });
          }

          if (effects.clickAdd != null) {
            const applied = addClickElementFlat(effects.clickAdd, {
              id: `family:${effectIdBase}:clickAdd`,
              label: effectLabel,
              rarityId: null,
              source: 'family'
            });
            if (Number.isFinite(applied) && applied !== 0) {
              summary.clickFlatTotal += applied;
              bonusApplied = true;
              const formatted = formatElementFlatBonus(applied);
              if (formatted) {
                addLabelEffect(effectLabel, translateCollectionEffect('apcFlat', `APC +${formatted}`, { value: formatted }), 'flat');
              }
            }
          }

          if (effects.autoAdd != null) {
            const applied = addAutoElementFlat(effects.autoAdd, {
              id: `family:${effectIdBase}:autoAdd`,
              label: effectLabel,
              rarityId: null,
              source: 'family'
            });
            if (Number.isFinite(applied) && applied !== 0) {
              summary.autoFlatTotal += applied;
              bonusApplied = true;
              const formatted = formatElementFlatBonus(applied);
              if (formatted) {
                addLabelEffect(effectLabel, translateCollectionEffect('apsFlat', `APS +${formatted}`, { value: formatted }), 'flat');
              }
            }
          }

          const nestedCrit = effects.crit && typeof effects.crit === 'object' ? effects.crit : null;
          const resolveCritValue = (primary, nestedKeys = []) => {
            if (primary != null) {
              const numeric = Number(primary);
              return Number.isFinite(numeric) ? numeric : null;
            }
            if (!nestedCrit) {
              return null;
            }
            for (const key of nestedKeys) {
              if (!(key in nestedCrit)) continue;
              const value = Number(nestedCrit[key]);
              if (Number.isFinite(value)) {
                return value;
              }
            }
            return null;
          };

          const critChanceAdd = resolveCritValue(effects.critChanceAdd, ['chanceAdd', 'add', 'bonus']);
          if (critChanceAdd != null && critChanceAdd !== 0) {
            summary.critChanceAdd += critChanceAdd;
            bonusApplied = true;
            const formatted = formatElementCritChanceBonus(critChanceAdd);
            if (formatted) {
              addLabelEffect(effectLabel, translateCollectionEffect('critChance', `Chance +${formatted}`, { value: formatted }), 'crit');
            }
          }
          const critMultiplierAdd = resolveCritValue(effects.critMultiplierAdd, ['multiplierAdd', 'powerAdd']);
          if (critMultiplierAdd != null && critMultiplierAdd !== 0) {
            summary.critMultiplierAdd += critMultiplierAdd;
            bonusApplied = true;
            const formatted = formatElementCritMultiplierBonus(critMultiplierAdd);
            if (formatted) {
              addLabelEffect(effectLabel, translateCollectionEffect('critMultiplier', `Multiplicateur +${formatted}×`, { value: formatted }), 'crit');
            }
          }

          const critChanceMult = resolveCritValue(effects.critChanceMult, ['chanceMult', 'multiplier']);
          if (critChanceMult != null && Math.abs(critChanceMult - 1) > 1e-9) {
            bonusApplied = true;
            const formatted = formatMultiplierTooltip(critChanceMult);
            if (formatted) {
              addLabelEffect(effectLabel, translateCollectionEffect('critChanceMultiplier', `Chance ${formatted}`, { value: formatted }), 'crit');
            }
          }
          const critMultiplierMult = resolveCritValue(effects.critMultiplierMult, ['multiplierMult', 'powerMult']);
          if (critMultiplierMult != null && Math.abs(critMultiplierMult - 1) > 1e-9) {
            bonusApplied = true;
            const formatted = formatMultiplierTooltip(critMultiplierMult);
            if (formatted) {
              addLabelEffect(effectLabel, translateCollectionEffect('critMultiplierMultiplier', `Multiplicateur ${formatted}`, { value: formatted }), 'crit');
            }
          }
          const critChanceSet = resolveCritValue(effects.critChanceSet, ['chanceSet', 'set']);
          if (critChanceSet != null && critChanceSet > 0) {
            bonusApplied = true;
            const formatted = formatElementCritChanceBonus(critChanceSet);
            if (formatted) {
              addLabelNote(effectLabel, translateCollectionNote('critChanceSet', `Chance fixée à ${formatted}`, { value: formatted }), 'crit');
            }
          }
          const critMultiplierSet = resolveCritValue(effects.critMultiplierSet, ['multiplierSet', 'powerSet']);
          if (critMultiplierSet != null && critMultiplierSet > 0) {
            bonusApplied = true;
            const formatted = formatElementCritMultiplierBonus(critMultiplierSet);
            if (formatted) {
              addLabelNote(effectLabel, translateCollectionNote('critMultiplierSet', `Multiplicateur fixé à ${formatted}×`, { value: formatted }), 'crit');
            }
          }
          const critMaxMultiplierAdd = resolveCritValue(effects.critMaxMultiplierAdd, ['maxMultiplierAdd', 'capAdd']);
          if (critMaxMultiplierAdd != null && critMaxMultiplierAdd !== 0) {
            bonusApplied = true;
            const formatted = formatElementCritMultiplierBonus(critMaxMultiplierAdd);
            if (formatted) {
              addLabelNote(effectLabel, translateCollectionNote('critCapAdd', `Plafond critique +${formatted}×`, { value: formatted }), 'crit');
            }
          }
          const critMaxMultiplierMult = resolveCritValue(effects.critMaxMultiplierMult, ['maxMultiplierMult', 'capMult']);
          if (critMaxMultiplierMult != null && Math.abs(critMaxMultiplierMult - 1) > 1e-9) {
            bonusApplied = true;
            const formatted = formatMultiplierTooltip(critMaxMultiplierMult);
            if (formatted) {
              addLabelNote(effectLabel, translateCollectionNote('critCapMultiplier', `Plafond critique ${formatted}`, { value: formatted }), 'crit');
            }
          }
          const critMaxMultiplierSet = resolveCritValue(effects.critMaxMultiplierSet, ['maxMultiplierSet', 'capSet']);
          if (critMaxMultiplierSet != null && critMaxMultiplierSet > 0) {
            bonusApplied = true;
            const formatted = formatElementCritMultiplierBonus(critMaxMultiplierSet);
            if (formatted) {
              addLabelNote(effectLabel, translateCollectionNote('critCapSet', `Plafond critique fixé à ${formatted}×`, { value: formatted }), 'crit');
            }
          }

          const hasCritEffect = (
            effects.critChanceAdd != null
            || effects.critChanceMult != null
            || effects.critChanceSet != null
            || effects.critMultiplierAdd != null
            || effects.critMultiplierMult != null
            || effects.critMultiplierSet != null
            || effects.critMaxMultiplierAdd != null
            || effects.critMaxMultiplierMult != null
            || effects.critMaxMultiplierSet != null
            || (nestedCrit && Object.keys(nestedCrit).length > 0)
          );
          if (hasCritEffect) {
            applyCritModifiersFromEffect(critAccumulator, effects);
          }

          if (bonusApplied) {
            markLabelActive(effectLabel);
          }
        });
      }

      const labelEntries = [];
      activeLabelDetails.forEach(entry => {
        if (!entry || !entry.label) return;
        const effects = Array.isArray(entry.effects)
          ? entry.effects.filter(text => typeof text === 'string' && text.trim().length > 0)
          : [];
        const notes = Array.isArray(entry.notes)
          ? entry.notes.filter(text => typeof text === 'string' && text.trim().length > 0)
          : [];
        const descriptionParts = [];
        if (effects.length) {
          descriptionParts.push(effects.join(' · '));
        }
        notes.forEach(note => descriptionParts.push(note));
        const labelEntry = { label: entry.label };
        if (effects.length) {
          labelEntry.effects = effects;
        }
        if (notes.length) {
          labelEntry.notes = notes;
        }
        if (entry.types instanceof Set && entry.types.size) {
          labelEntry.types = Array.from(entry.types);
        } else if (Array.isArray(entry.types) && entry.types.length) {
          labelEntry.types = [...entry.types];
        }
        if (descriptionParts.length) {
          labelEntry.description = descriptionParts.join(' · ');
        }
        labelEntries.push(labelEntry);
      });
      summary.activeLabels = labelEntries;
      familySummaries.set(familyId, summary);
    });
  }

  const intervalChanged = setTicketStarAverageIntervalSeconds(mythiqueBonuses.ticketIntervalSeconds);
  if (intervalChanged && !ticketStarState.active) {
    resetTicketStarState({ reschedule: true });
  }
  gameState.offlineGainMultiplier = mythiqueBonuses.offlineMultiplier;
  const frenzyBonus = {
    perClick: mythiqueBonuses.frenzyChanceMultiplier,
    perSecond: mythiqueBonuses.frenzyChanceMultiplier
  };
  gameState.frenzySpawnBonus = frenzyBonus;
  applyFrenzySpawnChanceBonus(frenzyBonus);

  const elementBonusSummary = {};
  elementGroupSummaries.forEach((value, key) => {
    elementBonusSummary[key] = value;
  });
  if (familySummaries.size > 0) {
    const familySummaryStore = {};
    familySummaries.forEach((value, key) => {
      familySummaryStore[key] = value;
    });
    if (Object.keys(familySummaryStore).length > 0) {
      elementBonusSummary.families = familySummaryStore;
    }
  }
  gameState.elementBonusSummary = elementBonusSummary;

  const trophyEffects = computeTrophyEffects();
  const clickTrophyMultiplier = trophyEffects.clickMultiplier instanceof LayeredNumber
    ? trophyEffects.clickMultiplier.clone()
    : toMultiplierLayered(trophyEffects.clickMultiplier ?? 1);
  const autoTrophyMultiplier = trophyEffects.autoMultiplier instanceof LayeredNumber
    ? trophyEffects.autoMultiplier.clone()
    : toMultiplierLayered(trophyEffects.autoMultiplier ?? 1);
  if (trophyEffects.critEffect) {
    applyCritModifiersFromEffect(critAccumulator, trophyEffects.critEffect);
  }
  const autoCollectConfig = normalizeTicketStarAutoCollectConfig(trophyEffects.ticketStarAutoCollect);
  gameState.ticketStarAutoCollect = autoCollectConfig
    ? { delaySeconds: Math.max(0, Number(autoCollectConfig.delaySeconds) || 0) }
    : null;

  UPGRADE_DEFS.forEach(def => {
    const level = getUpgradeLevel(gameState.upgrades, def.id);
    if (!level) return;
    const effects = def.effect(level, gameState.upgrades);
    const displayName = getLocalizedUpgradeName(def);

    if (effects.clickAdd != null) {
      const value = normalizeProductionUnit(effects.clickAdd);
      if (!value.isZero()) {
        clickShopAddition = clickShopAddition.add(value);
        clickDetails.additions.push({ id: def.id, label: displayName, level, value: value.clone(), source: 'shop' });
      }
    }

    if (effects.autoAdd != null) {
      const value = normalizeProductionUnit(effects.autoAdd);
      if (!value.isZero()) {
        autoShopAddition = autoShopAddition.add(value);
        autoDetails.additions.push({ id: def.id, label: displayName, level, value: value.clone(), source: 'shop' });
      }
    }

    if (effects.clickMult != null) {
      const multiplierValue = toMultiplierLayered(effects.clickMult);
      if (!isLayeredOne(multiplierValue)) {
        clickShopMultiplier = clickShopMultiplier.multiply(multiplierValue);
        clickDetails.multipliers.push({
          id: def.id,
          label: displayName,
          level,
          value: multiplierValue.clone(),
          source: 'shop'
        });
      }
    }

    if (effects.autoMult != null) {
      const multiplierValue = toMultiplierLayered(effects.autoMult);
      if (!isLayeredOne(multiplierValue)) {
        autoShopMultiplier = autoShopMultiplier.multiply(multiplierValue);
        autoDetails.multipliers.push({
          id: def.id,
          label: displayName,
          level,
          value: multiplierValue.clone(),
          source: 'shop'
        });
      }
    }
    applyCritModifiersFromEffect(critAccumulator, effects);
  });

  if (FUSION_DEFS.length) {
    const fusionBonusState = getFusionBonusState();
    const fusionLabel = translateOrDefault(
      'scripts.app.fusion.bonusLabel',
      'Fusions moléculaires'
    );
    const apcBonus = Number(fusionBonusState.apcFlat);
    if (Number.isFinite(apcBonus) && apcBonus !== 0) {
      const value = new LayeredNumber(apcBonus);
      if (!value.isZero()) {
        clickFusionAddition = clickFusionAddition.add(value);
        clickDetails.additions.push({
          id: 'fusion:perClick',
          label: fusionLabel,
          value: value.clone(),
          source: 'fusion'
        });
      }
    }
    const apsBonus = Number(fusionBonusState.apsFlat);
    if (Number.isFinite(apsBonus) && apsBonus !== 0) {
      const value = new LayeredNumber(apsBonus);
      if (!value.isZero()) {
        autoFusionAddition = autoFusionAddition.add(value);
        autoDetails.additions.push({
          id: 'fusion:perSecond',
          label: fusionLabel,
          value: value.clone(),
          source: 'fusion'
        });
      }
    }
  }

  clickDetails.sources.flats.shopFlat = clickShopAddition.clone();
  autoDetails.sources.flats.shopFlat = autoShopAddition.clone();
  clickDetails.sources.flats.elementFlat = clickElementAddition.clone();
  autoDetails.sources.flats.elementFlat = autoElementAddition.clone();
  clickDetails.sources.flats.fusionFlat = clickFusionAddition.clone();
  autoDetails.sources.flats.fusionFlat = autoFusionAddition.clone();
  const devkitAutoFlat = getDevKitAutoFlatBonus();
  if (devkitAutoFlat instanceof LayeredNumber && !devkitAutoFlat.isZero()) {
    autoDetails.sources.flats.devkitFlat = devkitAutoFlat.clone();
    autoDetails.additions.push({
      id: 'devkit-auto-flat',
      label: DEVKIT_AUTO_LABEL,
      value: devkitAutoFlat.clone(),
      source: 'devkit'
    });
  } else {
    autoDetails.sources.flats.devkitFlat = LayeredNumber.zero();
  }

  clickDetails.sources.multipliers.trophyMultiplier = clickTrophyMultiplier.clone();
  autoDetails.sources.multipliers.trophyMultiplier = autoTrophyMultiplier.clone();

  const clickShopBonus = clickShopMultiplier.clone();
  const autoShopBonus = autoShopMultiplier.clone();

  const clickRarityProduct = computeRarityMultiplierProduct(clickRarityMultipliers);
  const autoRarityProduct = computeRarityMultiplierProduct(autoRarityMultipliers);

  clickDetails.sources.multipliers.collectionMultiplier = clickRarityProduct.clone();
  autoDetails.sources.multipliers.collectionMultiplier = autoRarityProduct.clone();

  const clickTotalAddition = clickShopAddition
    .add(clickElementAddition)
    .add(clickFusionAddition);
  let autoTotalAddition = autoShopAddition
    .add(autoElementAddition)
    .add(autoFusionAddition);
  if (devkitAutoFlat instanceof LayeredNumber && !devkitAutoFlat.isZero()) {
    autoTotalAddition = autoTotalAddition.add(devkitAutoFlat);
  }

  clickDetails.totalAddition = clickTotalAddition.clone();
  autoDetails.totalAddition = autoTotalAddition.clone();

  const clickTotalMultiplier = LayeredNumber.one()
    .multiply(clickShopBonus)
    .multiply(clickRarityProduct)
    .multiply(clickTrophyMultiplier);
  let autoTotalMultiplier = LayeredNumber.one()
    .multiply(autoShopBonus)
    .multiply(autoRarityProduct)
    .multiply(autoTrophyMultiplier);

  const apsCritMultiplierValue = getApsCritMultiplier();
  const apsCritMultiplier = toMultiplierLayered(apsCritMultiplierValue);
  const hasApsCritMultiplier = !isLayeredOne(apsCritMultiplier);
  if (hasApsCritMultiplier) {
    autoTotalMultiplier = autoTotalMultiplier.multiply(apsCritMultiplier);
  }

  clickDetails.totalMultiplier = clickTotalMultiplier.clone();
  autoDetails.totalMultiplier = autoTotalMultiplier.clone();

  const clickFlatBase = clickDetails.sources.flats.baseFlat
    .add(clickDetails.sources.flats.shopFlat)
    .add(clickDetails.sources.flats.elementFlat)
    .add(clickDetails.sources.flats.fusionFlat || LayeredNumber.zero());
  const autoFlatBase = autoDetails.sources.flats.baseFlat
    .add(autoDetails.sources.flats.shopFlat)
    .add(autoDetails.sources.flats.elementFlat)
    .add(autoDetails.sources.flats.fusionFlat || LayeredNumber.zero())
    .add(autoDetails.sources.flats.devkitFlat || LayeredNumber.zero());

  let perClick = clickFlatBase.clone();
  perClick = perClick.multiply(clickShopBonus);
  perClick = perClick.multiply(clickRarityProduct);
  perClick = perClick.multiply(clickTrophyMultiplier);
  if (perClick.compare(LayeredNumber.zero()) < 0) {
    perClick = LayeredNumber.zero();
  }
  let perSecond = autoFlatBase.clone();
  perSecond = perSecond.multiply(autoShopBonus);
  perSecond = perSecond.multiply(autoRarityProduct);
  perSecond = perSecond.multiply(autoTrophyMultiplier);
  if (hasApsCritMultiplier) {
    perSecond = perSecond.multiply(apsCritMultiplier);
    autoDetails.multipliers.push({
      id: 'apsCrit',
      label: 'Critique APS',
      value: apsCritMultiplier.clone(),
      source: 'metaux'
    });
  }
  autoDetails.sources.multipliers.apsCrit = hasApsCritMultiplier
    ? apsCritMultiplier.clone()
    : LayeredNumber.one();

  perClick = normalizeProductionUnit(perClick);
  perSecond = normalizeProductionUnit(perSecond);

  clickDetails.total = perClick.clone();
  autoDetails.total = perSecond.clone();
  gameState.basePerClick = perClick.clone();
  gameState.basePerSecond = perSecond.clone();
  gameState.productionBase = { perClick: clickDetails, perSecond: autoDetails };

  const baseCritState = resolveCritState(critAccumulator);
  gameState.baseCrit = baseCritState;
  gameState.crit = cloneCritState(baseCritState);

  applyFrenzyEffects();
}

LayeredNumber.prototype.addNumber = function (num) {
  return this.add(new LayeredNumber(num));
};

function updateFrenzyIndicatorFor(type, target, now) {
  const container = target?.container ?? target;
  const multiplierElement = target?.multiplier ?? null;
  const timerElement = target?.timer ?? null;
  if (!container) {
    return false;
  }
  const entry = frenzyState[type];
  if (!entry) {
    container.hidden = true;
    container.setAttribute('aria-hidden', 'true');
    if (multiplierElement) multiplierElement.textContent = '';
    if (timerElement) timerElement.textContent = '';
    if (!multiplierElement && !timerElement) {
      container.textContent = '';
    }
    delete container.dataset.stacks;
    return false;
  }
  pruneFrenzyEffects(entry, now);
  const stacks = getFrenzyStackCount(type, now);
  const effectUntil = Number(entry.effectUntil) || 0;
  if (stacks <= 0 || effectUntil <= now) {
    container.hidden = true;
    container.setAttribute('aria-hidden', 'true');
    if (multiplierElement) multiplierElement.textContent = '';
    if (timerElement) timerElement.textContent = '';
    if (!multiplierElement && !timerElement) {
      container.textContent = '';
    }
    delete container.dataset.stacks;
    return false;
  }
  const remaining = Math.max(0, effectUntil - now);
  const seconds = remaining / 1000;
  const precision = seconds < 10 ? 1 : 0;
  const multiplier = Math.pow(FRENZY_CONFIG.multiplier, stacks);
  const multiplierText = formatNumberLocalized(multiplier, { maximumFractionDigits: 2 });
  const timeText = formatNumberLocalized(seconds, {
    minimumFractionDigits: precision,
    maximumFractionDigits: precision
  });
  if (multiplierElement) {
    multiplierElement.textContent = `×${multiplierText}`;
  }
  if (timerElement) {
    timerElement.textContent = `${timeText}s`;
  }
  if (!multiplierElement && !timerElement) {
    container.textContent = `×${multiplierText} · ${timeText}s`;
  }
  container.hidden = false;
  container.setAttribute('aria-hidden', 'false');
  container.dataset.stacks = stacks;
  return true;
}

function updateFrenzyIndicators(now = performance.now()) {
  const apsActive = updateFrenzyIndicatorFor('perSecond', elements.statusApsFrenzy, now);
  const apcActive = updateFrenzyIndicatorFor('perClick', elements.statusApcFrenzy, now);
  const statusRoot = elements.frenzyStatus;
  if (statusRoot) {
    const activePage = document.body?.dataset?.activePage;
    const displayAllowed = activePage === 'game' || activePage === 'wave';
    const hasActive = Boolean(apsActive || apcActive);
    const shouldShow = Boolean(displayAllowed && hasActive);
    statusRoot.hidden = !shouldShow;
    statusRoot.style.display = shouldShow ? '' : 'none';
    statusRoot.setAttribute('aria-hidden', String(!shouldShow));
  }
}

function formatApsCritChrono(seconds) {
  const totalSeconds = Math.max(0, Math.ceil(Number(seconds) || 0));
  if (totalSeconds >= 3600) {
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;
    const parts = [`${formatIntegerLocalized(hours)} h`];
    if (minutes > 0) {
      parts.push(`${formatIntegerLocalized(minutes)} min`);
    }
    if (secs > 0) {
      parts.push(`${formatIntegerLocalized(secs)} s`);
    }
    return parts.join(' ');
  }
  if (totalSeconds >= 60) {
    const minutes = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    const parts = [`${formatIntegerLocalized(minutes)} min`];
    if (secs > 0) {
      parts.push(`${formatIntegerLocalized(secs)} s`);
    }
    return parts.join(' ');
  }
  return `${formatIntegerLocalized(totalSeconds)} s`;
}

function updateApsCritDisplay() {
  const panel = elements.statusApsCrit;
  if (!panel) {
    return;
  }
  const state = ensureApsCritState();
  const remainingSeconds = getApsCritRemainingSeconds(state);
  const multiplierValue = getApsCritMultiplier(state);
  const isActive = remainingSeconds > APS_CRIT_TIMER_EPSILON && multiplierValue > 1;
  panel.hidden = !isActive;
  panel.style.display = isActive ? '' : 'none';
  panel.classList.toggle('is-active', isActive);
  panel.setAttribute('aria-hidden', String(!isActive));
  const container = panel.closest('.status-item--crit-aps');
  if (container) {
    container.hidden = !isActive;
    container.style.display = isActive ? '' : 'none';
    container.setAttribute('aria-hidden', String(!isActive));
  }
  if (elements.statusApsCritSeparator) {
    elements.statusApsCritSeparator.hidden = !isActive;
  }
  const chronoText = isActive ? formatApsCritChrono(remainingSeconds) : '';
  if (elements.statusApsCritChrono) {
    elements.statusApsCritChrono.textContent = chronoText;
    elements.statusApsCritChrono.hidden = !isActive;
    elements.statusApsCritChrono.setAttribute('aria-hidden', String(!isActive));
  }
  const multiplierText = `×${formatNumberLocalized(multiplierValue)}`;
  const multiplierDisplayText = isActive ? multiplierText : '';
  if (elements.statusApsCritMultiplier) {
    elements.statusApsCritMultiplier.textContent = multiplierDisplayText;
    elements.statusApsCritMultiplier.hidden = !isActive;
    elements.statusApsCritMultiplier.setAttribute('aria-hidden', String(!isActive));
  }
  panel.setAttribute(
    'aria-label',
    isActive
      ? `Compteur critique APS actif : ${multiplierText} pendant ${chronoText}.`
      : `Compteur critique APS inactif.`
  );
}

function pulseApsCritPanel() {
  const panel = elements.statusApsCrit;
  if (!panel || panel.hidden) {
    return;
  }
  panel.classList.remove('is-pulsing');
  void panel.offsetWidth;
  panel.classList.add('is-pulsing');
  if (apsCritPulseTimeoutId != null) {
    clearTimeout(apsCritPulseTimeoutId);
  }
  apsCritPulseTimeoutId = setTimeout(() => {
    panel.classList.remove('is-pulsing');
    apsCritPulseTimeoutId = null;
  }, 620);
  [
    elements.statusApsCritChrono,
    elements.statusApsCritMultiplier
  ].forEach(valueElement => {
    if (!valueElement) {
      return;
    }
    valueElement.classList.remove('status-aps-crit-value--pulse');
    void valueElement.offsetWidth;
    valueElement.classList.add('status-aps-crit-value--pulse');
  });
}

function updateUI() {
  if (typeof refreshGachaRarityLocalization === 'function') {
    refreshGachaRarityLocalization();
  }
  updatePrimaryNavigationLocks();
  updatePageUnlockUI();
  updateBigBangVisibility();
  updateOptionsIntroDetails();
  updateBrickSkinOption();
  updateBrandPortalState();
  updateMetauxCreditsUI();
  if (elements.statusAtoms) {
    elements.statusAtoms.textContent = gameState.atoms.toString();
  }
  if (elements.statusApc) {
    elements.statusApc.textContent = gameState.perClick.toString();
  }
  if (elements.statusAps) {
    elements.statusAps.textContent = gameState.perSecond.toString();
  }
  updateApsCritDisplay();
  updateFrenzyIndicators();
  updateApcFrenzyCounterDisplay();
  updateGachaUI();
  collectionsController.updateCollectionDisplay();
  fusionController.update();
  shopController.updateAffordability();
  if (goalsController) {
    goalsController.updateMilestone();
    goalsController.refreshTexts();
    goalsController.update();
  }
  infoController.updatePanels();
}

function showToast(message) {
  if (!toastElement) {
    toastElement = document.createElement('div');
    toastElement.className = 'toast';
    document.body.appendChild(toastElement);
  }
  toastElement.textContent = message;
  toastElement.classList.add('visible');
  clearTimeout(showToast.timeout);
  showToast.timeout = setTimeout(() => {
    toastElement.classList.remove('visible');
  }, 2200);
}

if (typeof globalThis !== 'undefined') {
  globalThis.showToast = showToast;
  globalThis.updateUI = updateUI;
  globalThis.recalcProduction = recalcProduction;
  globalThis.evaluateTrophies = evaluateTrophies;
  globalThis.saveGame = saveGame;
  globalThis.gameState = gameState;
  if (!globalThis.atom2universElements || globalThis.atom2universElements === elements) {
    globalThis.atom2universElements = elements;
  }
  if (!globalThis.elements || globalThis.elements === elements) {
    globalThis.elements = elements;
  }
}

function applyTheme(requestedThemeId) {
  const requested = typeof requestedThemeId === 'string' ? requestedThemeId.trim() : '';
  const currentThemeId = typeof gameState.theme === 'string' ? gameState.theme : null;
  const appliedId = getThemeDefinition(requested)
    ? requested
    : getThemeDefinition(currentThemeId)
      ? currentThemeId
      : DEFAULT_THEME_ID;
  const theme = getThemeDefinition(appliedId) || getThemeDefinition(DEFAULT_THEME_ID);
  const classes = theme && Array.isArray(theme.classes) && theme.classes.length
    ? theme.classes
    : ['theme-dark'];
  if (document && document.body) {
    document.body.classList.remove(...THEME_CLASS_LIST);
    classes.forEach(cls => document.body.classList.add(cls));
    document.body.setAttribute('data-theme', theme.id);
  }
  if (elements.themeSelect && elements.themeSelect.value !== theme.id) {
    elements.themeSelect.value = theme.id;
  }
  gameState.theme = theme.id;
  return theme.id;
}

if (elements.languageSelect) {
  elements.languageSelect.addEventListener('change', event => {
    const requestedLanguage = resolveLanguageCode(event.target.value);
    const i18n = globalThis.i18n;
    if (!i18n || typeof i18n.setLanguage !== 'function') {
      updateLanguageSelectorValue(requestedLanguage);
      writeStoredLanguagePreference(requestedLanguage);
      if (document && document.documentElement) {
        document.documentElement.lang = requestedLanguage;
      }
      return;
    }
    const previousLanguage = i18n.getCurrentLanguage ? i18n.getCurrentLanguage() : null;
    const normalizedPrevious = previousLanguage ? resolveLanguageCode(previousLanguage) : null;
    if (normalizedPrevious === requestedLanguage) {
      writeStoredLanguagePreference(requestedLanguage);
      return;
    }
    elements.languageSelect.disabled = true;
    i18n
      .setLanguage(requestedLanguage)
      .then(() => {
        writeStoredLanguagePreference(requestedLanguage);
        if (typeof i18n.updateTranslations === 'function') {
          i18n.updateTranslations(document);
        }
        updateLanguageSelectorValue(requestedLanguage);
        refreshOptionsWelcomeContent();
        updateBrickSkinOption();
        updateUI();
        showToast(t('scripts.app.language.updated'));
      })
      .catch(error => {
        console.error('Unable to change language', error);
        if (previousLanguage) {
          updateLanguageSelectorValue(previousLanguage);
        }
      })
      .finally(() => {
        elements.languageSelect.disabled = false;
      });
  });
}

if (elements.themeSelect) {
  elements.themeSelect.addEventListener('change', event => {
    const appliedId = applyTheme(event.target.value);
    renderThemeOptions();
    saveGame();
    const appliedTheme = getThemeDefinition(appliedId);
    const themeLabel = getThemeLabel(appliedTheme) || appliedId;
    showToast(t('scripts.app.themeUpdated', { name: themeLabel }));
  });
}

if (typeof window !== 'undefined') {
  window.addEventListener('i18n:languagechange', () => {
    renderThemeOptions();
    collectionsController.renderPeriodicTable();
    updateUI();
  });
}

if (elements.brickSkinSelect) {
  elements.brickSkinSelect.addEventListener('change', event => {
    commitBrickSkinSelection(event.target.value);
  });
}

if (elements.arcadeBrickSkinSelect) {
  elements.arcadeBrickSkinSelect.addEventListener('change', event => {
    commitBrickSkinSelection(event.target.value);
  });
}

if (elements.musicTrackSelect) {
  elements.musicTrackSelect.addEventListener('change', event => {
    const selectedId = event.target.value;
    if (!selectedId) {
      musicPlayer.stop();
      showToast(t('scripts.app.music.muted'));
      return;
    }
    const success = musicPlayer.playTrackById(selectedId);
    if (!success) {
      showToast(t('scripts.app.music.missing'));
      updateMusicStatus();
      return;
    }
    const currentTrack = musicPlayer.getCurrentTrack();
    if (currentTrack) {
      if (currentTrack.placeholder) {
        showToast(t('scripts.app.music.fileMissing'));
      } else {
        showToast(t('scripts.app.music.nowPlaying', { name: currentTrack.displayName }));
      }
    }
    updateMusicStatus();
  });
}

if (elements.musicVolumeSlider) {
  const applyVolumeFromSlider = (rawValue, { announce = false } = {}) => {
    const numeric = Number(rawValue);
    const percent = Number.isFinite(numeric) ? Math.max(0, Math.min(100, numeric)) : 0;
    const normalized = clampMusicVolume(percent / 100, musicPlayer.getVolume());
    musicPlayer.setVolume(normalized);
    gameState.musicVolume = normalized;
    if (announce) {
      showToast(t('scripts.app.music.volumeLabel', { value: Math.round(normalized * 100) }));
    }
  };
  elements.musicVolumeSlider.addEventListener('input', event => {
    applyVolumeFromSlider(event.target.value);
  });
  elements.musicVolumeSlider.addEventListener('change', event => {
    applyVolumeFromSlider(event.target.value, { announce: true });
  });
}

elements.resetButton.addEventListener('click', () => {
  const confirmationWord = 'RESET';
  const promptMessage = `Réinitialisation complète du jeu. Tapez "${confirmationWord}" pour confirmer.\nCette action est irréversible.`;
  const response = prompt(promptMessage);
  if (response == null) {
    showToast(t('scripts.app.reset.cancelled'));
    return;
  }
  if (response.trim().toUpperCase() !== confirmationWord) {
    showToast(t('scripts.app.reset.invalid'));
    return;
  }
  resetGame();
  showToast(t('scripts.app.reset.done'));
});

if (elements.bigBangOptionToggle) {
  elements.bigBangOptionToggle.addEventListener('change', event => {
    const enabled = event.target.checked;
    if (!isBigBangTrophyUnlocked()) {
      event.target.checked = false;
      gameState.bigBangButtonVisible = false;
      updateBigBangVisibility();
      return;
    }
    gameState.bigBangButtonVisible = enabled;
    updateBigBangVisibility();
    saveGame();
    showToast(enabled
      ? t('scripts.app.bigBangToggle.shown')
      : t('scripts.app.bigBangToggle.hidden'));
  });
}

function cloneArcadeProgress(progress) {
  const base = createInitialArcadeProgress();
  if (!progress || typeof progress !== 'object') {
    return base;
  }

  const result = { version: 1, entries: { ...base.entries } };
  const sourceEntries = progress.entries && typeof progress.entries === 'object'
    ? progress.entries
    : progress;

  ARCADE_GAME_IDS.forEach(id => {
    const rawEntry = sourceEntries[id];
    if (!rawEntry) {
      result.entries[id] = null;
      return;
    }
    if (rawEntry && typeof rawEntry === 'object') {
      const entryState = rawEntry.state && typeof rawEntry.state === 'object'
        ? rawEntry.state
        : rawEntry;
      try {
        result.entries[id] = {
          state: JSON.parse(JSON.stringify(entryState)),
          updatedAt: Number.isFinite(rawEntry.updatedAt) ? rawEntry.updatedAt : Date.now()
        };
      } catch (error) {
        result.entries[id] = null;
      }
    } else {
      result.entries[id] = null;
    }
  });

  return result;
}

function serializeState() {
  const stats = gameState.stats || createInitialStats();
  const sessionApc = getLayeredStat(stats.session, 'apcAtoms');
  const sessionAps = getLayeredStat(stats.session, 'apsAtoms');
  const sessionOffline = getLayeredStat(stats.session, 'offlineAtoms');
  const globalApc = getLayeredStat(stats.global, 'apcAtoms');
  const globalAps = getLayeredStat(stats.global, 'apsAtoms');
  const globalOffline = getLayeredStat(stats.global, 'offlineAtoms');
  const sessionFrenzyStats = ensureApcFrenzyStats(stats.session);
  const globalFrenzyStats = ensureApcFrenzyStats(stats.global);
  return {
    atoms: gameState.atoms.toJSON(),
    lifetime: gameState.lifetime.toJSON(),
    perClick: gameState.perClick.toJSON(),
    perSecond: gameState.perSecond.toJSON(),
    gachaTickets: Number.isFinite(Number(gameState.gachaTickets))
      ? Math.max(0, Math.floor(Number(gameState.gachaTickets)))
      : 0,
    bonusParticulesTickets: Number.isFinite(Number(gameState.bonusParticulesTickets))
      ? Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets)))
      : 0,
    ticketStarUnlocked: gameState.ticketStarUnlocked === true,
    upgrades: gameState.upgrades,
    shopUnlocks: Array.from(getShopUnlockSet()),
    elements: gameState.elements,
    fusions: (() => {
      const base = createInitialFusionState();
      const source = gameState.fusions && typeof gameState.fusions === 'object'
        ? gameState.fusions
        : {};
      const result = {};
      FUSION_DEFS.forEach(def => {
        const entry = source[def.id] || base[def.id] || { attempts: 0, successes: 0 };
        const attempts = Number(entry.attempts);
        const successes = Number(entry.successes);
        result[def.id] = {
          attempts: Number.isFinite(attempts) && attempts > 0 ? Math.floor(attempts) : 0,
          successes: Number.isFinite(successes) && successes > 0 ? Math.floor(successes) : 0
        };
      });
      return result;
    })(),
    pageUnlocks: (() => {
      const unlocks = getPageUnlockState();
      return {
        gacha: unlocks?.gacha === true,
        tableau: unlocks?.tableau === true,
        fusion: unlocks?.fusion === true,
        info: unlocks?.info === true
      };
    })(),
    fusionBonuses: (() => {
      const bonuses = getFusionBonusState();
      return {
        apcFlat: Number.isFinite(Number(bonuses.apcFlat)) ? Number(bonuses.apcFlat) : 0,
        apsFlat: Number.isFinite(Number(bonuses.apsFlat)) ? Number(bonuses.apsFlat) : 0
      };
    })(),
    theme: gameState.theme,
    arcadeBrickSkin: normalizeBrickSkinSelection(gameState.arcadeBrickSkin),
    stats: {
      session: {
        atomsGained: stats.session.atomsGained.toJSON(),
        apcAtoms: sessionApc.toJSON(),
        apsAtoms: sessionAps.toJSON(),
        offlineAtoms: sessionOffline.toJSON(),
        manualClicks: stats.session.manualClicks,
        onlineTimeMs: stats.session.onlineTimeMs,
        startedAt: stats.session.startedAt,
        frenzyTriggers: {
          perClick: stats.session.frenzyTriggers?.perClick || 0,
          perSecond: stats.session.frenzyTriggers?.perSecond || 0,
          total: stats.session.frenzyTriggers?.total || 0
        },
        apcFrenzy: {
          totalClicks: sessionFrenzyStats.totalClicks || 0,
          best: {
            clicks: sessionFrenzyStats.best?.clicks || 0,
            frenziesUsed: sessionFrenzyStats.best?.frenziesUsed || 0
          },
          bestSingle: {
            clicks: sessionFrenzyStats.bestSingle?.clicks || 0
          }
        }
      },
      global: {
        apcAtoms: globalApc.toJSON(),
        apsAtoms: globalAps.toJSON(),
        offlineAtoms: globalOffline.toJSON(),
        manualClicks: stats.global.manualClicks,
        playTimeMs: stats.global.playTimeMs,
        startedAt: stats.global.startedAt,
        frenzyTriggers: {
          perClick: stats.global.frenzyTriggers?.perClick || 0,
          perSecond: stats.global.frenzyTriggers?.perSecond || 0,
          total: stats.global.frenzyTriggers?.total || 0
        },
        apcFrenzy: {
          totalClicks: globalFrenzyStats.totalClicks || 0,
          best: {
            clicks: globalFrenzyStats.best?.clicks || 0,
            frenziesUsed: globalFrenzyStats.best?.frenziesUsed || 0
          },
          bestSingle: {
            clicks: globalFrenzyStats.bestSingle?.clicks || 0
          }
        }
      }
    },
    offlineGainMultiplier: Number.isFinite(Number(gameState.offlineGainMultiplier))
      ? Math.max(0, Number(gameState.offlineGainMultiplier))
      : MYTHIQUE_OFFLINE_BASE,
    offlineTickets: (() => {
      const raw = gameState.offlineTickets || {};
      const secondsPerTicket = Number.isFinite(Number(raw.secondsPerTicket))
        && Number(raw.secondsPerTicket) > 0
        ? Number(raw.secondsPerTicket)
        : OFFLINE_TICKET_CONFIG.secondsPerTicket;
      const capSeconds = Number.isFinite(Number(raw.capSeconds))
        && Number(raw.capSeconds) > 0
        ? Math.max(Number(raw.capSeconds), secondsPerTicket)
        : Math.max(OFFLINE_TICKET_CONFIG.capSeconds, secondsPerTicket);
      const progressSeconds = Number.isFinite(Number(raw.progressSeconds))
        && Number(raw.progressSeconds) > 0
        ? Math.max(0, Math.min(Number(raw.progressSeconds), capSeconds))
        : 0;
      return {
        secondsPerTicket,
        capSeconds,
        progressSeconds
      };
    })(),
    sudokuOfflineBonus: (() => {
      const bonus = normalizeSudokuOfflineBonusState(gameState.sudokuOfflineBonus);
      if (!bonus) {
        return null;
      }
      const grantedAt = Number.isFinite(Number(bonus.grantedAt)) && Number(bonus.grantedAt) > 0
        ? Number(bonus.grantedAt)
        : Date.now();
      return {
        multiplier: bonus.multiplier,
        maxSeconds: bonus.maxSeconds,
        limitSeconds: bonus.limitSeconds,
        grantedAt
      };
    })(),
    ticketStarAutoCollect: gameState.ticketStarAutoCollect
      ? {
          delaySeconds: Math.max(
            0,
            Number.isFinite(Number(gameState.ticketStarAutoCollect.delaySeconds))
              ? Number(gameState.ticketStarAutoCollect.delaySeconds)
              : 0
          )
        }
      : null,
    ticketStarIntervalSeconds: Number.isFinite(Number(gameState.ticketStarAverageIntervalSeconds))
      && Number(gameState.ticketStarAverageIntervalSeconds) > 0
      ? Number(gameState.ticketStarAverageIntervalSeconds)
      : DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
    frenzySpawnBonus: {
      perClick: Number.isFinite(Number(gameState.frenzySpawnBonus?.perClick))
        && Number(gameState.frenzySpawnBonus.perClick) > 0
        ? Number(gameState.frenzySpawnBonus.perClick)
        : 1,
      perSecond: Number.isFinite(Number(gameState.frenzySpawnBonus?.perSecond))
        && Number(gameState.frenzySpawnBonus.perSecond) > 0
        ? Number(gameState.frenzySpawnBonus.perSecond)
        : 1
    },
    apsCrit: (() => {
      const state = ensureApsCritState();
      const effects = Array.isArray(state.effects)
        ? state.effects
            .map(effect => ({
              remainingSeconds: Math.max(0, Number(effect?.remainingSeconds) || 0),
              multiplierAdd: Math.max(0, Number(effect?.multiplierAdd) || 0)
            }))
            .filter(effect => effect.remainingSeconds > 0 && effect.multiplierAdd > 0)
        : [];
      return { effects };
    })(),
    music: {
      selectedTrack: musicPlayer.getCurrentTrackId() ?? gameState.musicTrackId ?? null,
      enabled: gameState.musicEnabled !== false,
      volume: clampMusicVolume(
        typeof musicPlayer.getVolume === 'function'
          ? musicPlayer.getVolume()
          : gameState.musicVolume,
        DEFAULT_MUSIC_VOLUME
      )
    },
    musicTrackId: musicPlayer.getCurrentTrackId() ?? gameState.musicTrackId ?? null,
    musicVolume: clampMusicVolume(
      typeof musicPlayer.getVolume === 'function'
        ? musicPlayer.getVolume()
        : gameState.musicVolume,
      DEFAULT_MUSIC_VOLUME
    ),
    musicEnabled: gameState.musicEnabled !== false,
    bigBangButtonVisible: gameState.bigBangButtonVisible === true,
    trophies: getUnlockedTrophyIds(),
    arcadeProgress: cloneArcadeProgress(gameState.arcadeProgress),
    lastSave: Date.now()
  };
}

function saveGame() {
  try {
    const payload = serializeState();
    writeJSON('atom2univers', payload);
  } catch (err) {
    console.error('Erreur de sauvegarde', err);
  }
}

if (typeof window !== 'undefined') {
  window.atom2universSaveGame = saveGame;
}

function resetGame() {
  Object.assign(gameState, {
    atoms: LayeredNumber.zero(),
    lifetime: LayeredNumber.zero(),
    perClick: BASE_PER_CLICK.clone(),
    perSecond: BASE_PER_SECOND.clone(),
    basePerClick: BASE_PER_CLICK.clone(),
    basePerSecond: BASE_PER_SECOND.clone(),
    gachaTickets: 0,
    bonusParticulesTickets: 0,
    upgrades: {},
    shopUnlocks: new Set(),
    elements: createInitialElementCollection(),
    fusions: createInitialFusionState(),
    fusionBonuses: createInitialFusionBonuses(),
    pageUnlocks: createInitialPageUnlockState(),
    theme: DEFAULT_THEME_ID,
    arcadeBrickSkin: 'original',
    stats: createInitialStats(),
    production: createEmptyProductionBreakdown(),
    productionBase: createEmptyProductionBreakdown(),
    crit: createDefaultCritState(),
    baseCrit: createDefaultCritState(),
    lastCritical: null,
    elementBonusSummary: {},
    trophies: new Set(),
    offlineGainMultiplier: MYTHIQUE_OFFLINE_BASE,
    offlineTickets: {
      secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
      capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
      progressSeconds: 0
    },
    sudokuOfflineBonus: null,
    ticketStarAutoCollect: null,
    ticketStarAverageIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
    ticketStarUnlocked: false,
    frenzySpawnBonus: { perClick: 1, perSecond: 1 },
    musicTrackId: null,
    musicVolume: DEFAULT_MUSIC_VOLUME,
    musicEnabled: DEFAULT_MUSIC_ENABLED,
    bigBangButtonVisible: false,
    apsCrit: createDefaultApsCritState(),
    arcadeProgress: createInitialArcadeProgress()
  });
  applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
  setTicketStarAverageIntervalSeconds(gameState.ticketStarAverageIntervalSeconds);
  resetFrenzyState({ skipApply: true });
  resetTicketStarState({ reschedule: true });
  applyTheme();
  if (typeof setParticulesBrickSkinPreference === 'function') {
    setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
  }
  musicPlayer.stop();
  musicPlayer.setVolume(DEFAULT_MUSIC_VOLUME, { silent: true });
  recalcProduction();
  shopController.render();
  updateUI();
  fusionController.setLog(
    translateOrDefault(
      'scripts.app.fusion.prompt',
      'Sélectionnez une recette pour tenter votre première fusion.'
    )
  );
  saveGame();
}

function applyOnlineProgress(seconds, options = {}) {
  const totalSeconds = Math.max(0, Number(seconds) || 0);
  const result = {
    requestedSeconds: totalSeconds,
    appliedSeconds: 0,
    atomsGained: LayeredNumber.zero(),
    ticketsEarned: 0,
    sudokuBonus: null
  };
  if (totalSeconds <= 0) {
    return result;
  }

  const stepCandidate = Number(options.stepSeconds);
  const stepSeconds = Number.isFinite(stepCandidate) && stepCandidate > 0
    ? Math.min(Math.max(stepCandidate, 0.1), 60)
    : 1;
  const startNow = typeof performance !== 'undefined' ? performance.now() : Date.now();
  let simulatedNow = startNow;
  const originalVisibleSince = gamePageVisibleSince;
  if (gamePageVisibleSince == null) {
    gamePageVisibleSince = simulatedNow - 60000;
  }

  const initialTickets = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  let remaining = totalSeconds;

  try {
    while (remaining > 1e-6) {
      const delta = Math.min(remaining, stepSeconds);
      if (!gameState.perSecond.isZero()) {
        const gain = gameState.perSecond.multiplyNumber(delta);
        if (gain instanceof LayeredNumber && !gain.isZero()) {
          gainAtoms(gain, 'aps');
          result.atomsGained = result.atomsGained.add(gain);
        }
      }

      updateApsCritTimer(delta);
      updatePlaytime(delta);
      simulatedNow += delta * 1000;
      updateFrenzies(delta, simulatedNow);
      updateTicketStar(delta, simulatedNow);

      remaining -= delta;
      result.appliedSeconds += delta;
    }
  } finally {
    gamePageVisibleSince = originalVisibleSince;
  }

  const finalTickets = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const ticketsEarned = finalTickets - initialTickets;
  if (ticketsEarned > 0) {
    result.ticketsEarned = ticketsEarned;
  }

  result.appliedSeconds = Math.min(totalSeconds, result.appliedSeconds);

  return result;
}

function applyOfflineProgress(seconds, options = {}) {
  const totalSeconds = Math.max(0, Number(seconds) || 0);
  const result = {
    requestedSeconds: totalSeconds,
    appliedSeconds: 0,
    atomsGained: LayeredNumber.zero(),
    ticketsEarned: 0
  };
  if (totalSeconds <= 0) {
    return result;
  }

  const appliedSeconds = Math.min(totalSeconds, OFFLINE_GAIN_CAP);
  result.appliedSeconds = appliedSeconds;

  const announceAtoms = options.announceAtoms !== false;
  const announceTickets = options.announceTickets !== false;
  const sudokuBonus = normalizeSudokuOfflineBonusState(gameState.sudokuOfflineBonus);
  let sudokuBonusAppliedSeconds = 0;
  let sudokuBonusMultiplier = 0;
  if (sudokuBonus && appliedSeconds > 0) {
    sudokuBonusAppliedSeconds = Math.min(appliedSeconds, sudokuBonus.maxSeconds);
    const multiplierCandidate = Number(sudokuBonus.multiplier);
    if (Number.isFinite(multiplierCandidate) && multiplierCandidate > 0) {
      sudokuBonusMultiplier = multiplierCandidate;
    }
  }

  if (appliedSeconds > 0) {
    const multiplier = Number.isFinite(Number(gameState.offlineGainMultiplier))
      && Number(gameState.offlineGainMultiplier) > 0
      ? Math.min(MYTHIQUE_OFFLINE_CAP, Number(gameState.offlineGainMultiplier))
      : MYTHIQUE_OFFLINE_BASE;
    const perSecondGain = gameState.perSecond instanceof LayeredNumber && !gameState.perSecond.isZero()
      ? gameState.perSecond
      : null;
    let totalOfflineGain = null;
    if (perSecondGain) {
      if (sudokuBonusAppliedSeconds > 0 && sudokuBonusMultiplier > 0) {
        const bonusGain = perSecondGain.multiplyNumber(sudokuBonusAppliedSeconds * sudokuBonusMultiplier);
        if (bonusGain instanceof LayeredNumber && !bonusGain.isZero()) {
          totalOfflineGain = bonusGain.clone ? bonusGain.clone() : bonusGain;
        }
      }
      const remainingSeconds = appliedSeconds - sudokuBonusAppliedSeconds;
      if (remainingSeconds > 0 && multiplier > 0) {
        const regularGain = perSecondGain.multiplyNumber(remainingSeconds * multiplier);
        if (regularGain instanceof LayeredNumber && !regularGain.isZero()) {
          totalOfflineGain = totalOfflineGain
            ? totalOfflineGain.add(regularGain)
            : regularGain.clone ? regularGain.clone() : regularGain;
        }
      }
      if (totalOfflineGain instanceof LayeredNumber && !totalOfflineGain.isZero()) {
        gainAtoms(totalOfflineGain, 'offline');
        result.atomsGained = totalOfflineGain.clone ? totalOfflineGain.clone() : totalOfflineGain;
        if (announceAtoms) {
          showToast(t('scripts.app.offline.progressAtoms', { amount: totalOfflineGain.toString() }));
        }
      }
    }
  }

  if (sudokuBonus && appliedSeconds > 0) {
    if (announceAtoms && sudokuBonusAppliedSeconds > 0 && sudokuBonusMultiplier > 0 && typeof showToast === 'function') {
      const durationText = formatSudokuRewardDuration(sudokuBonusAppliedSeconds);
      const multiplierText = formatMultiplier(sudokuBonusMultiplier);
      const messageKey = 'scripts.app.arcade.sudoku.reward.applied';
      const fallback = `Sudoku bonus applied: ${durationText} at ${multiplierText}.`;
      const message = typeof t === 'function'
        ? t(messageKey, { duration: durationText, multiplier: multiplierText })
        : null;
      showToast(message && message !== messageKey ? message : fallback);
    }
    result.sudokuBonus = sudokuBonusAppliedSeconds > 0 && sudokuBonusMultiplier > 0
      ? { appliedSeconds: sudokuBonusAppliedSeconds, multiplier: sudokuBonusMultiplier }
      : null;
    gameState.sudokuOfflineBonus = null;
  } else if (sudokuBonus) {
    gameState.sudokuOfflineBonus = sudokuBonus;
  }

  const hasFirstTrophy = getUnlockedTrophySet().has(ARCADE_TROPHY_ID);
  if (hasFirstTrophy) {
    const offlineTickets = gameState.offlineTickets || {
      secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
      capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
      progressSeconds: 0
    };
    const secondsPerTicket = Number.isFinite(Number(offlineTickets.secondsPerTicket))
      && Number(offlineTickets.secondsPerTicket) > 0
      ? Number(offlineTickets.secondsPerTicket)
      : OFFLINE_TICKET_CONFIG.secondsPerTicket;
    const capSeconds = Number.isFinite(Number(offlineTickets.capSeconds))
      && Number(offlineTickets.capSeconds) > 0
      ? Math.max(Number(offlineTickets.capSeconds), secondsPerTicket)
      : Math.max(OFFLINE_TICKET_CONFIG.capSeconds, secondsPerTicket);
    let progressSeconds = Number.isFinite(Number(offlineTickets.progressSeconds))
      && Number(offlineTickets.progressSeconds) > 0
      ? Math.max(0, Math.min(Number(offlineTickets.progressSeconds), capSeconds))
      : 0;
    const effectiveSeconds = Math.min(totalSeconds, capSeconds);
    progressSeconds = Math.min(progressSeconds + effectiveSeconds, capSeconds);
    const ticketsEarned = secondsPerTicket > 0
      ? Math.floor(progressSeconds / secondsPerTicket)
      : 0;
    if (ticketsEarned > 0) {
      const currentTickets = Number.isFinite(Number(gameState.gachaTickets))
        ? Math.max(0, Math.floor(Number(gameState.gachaTickets)))
        : 0;
      gameState.gachaTickets = currentTickets + ticketsEarned;
      evaluatePageUnlocks({ save: false, deferUI: true });
      if (announceTickets) {
        const unitKey = ticketsEarned === 1
          ? 'scripts.app.offlineTickets.ticketSingular'
          : 'scripts.app.offlineTickets.ticketPlural';
        const unit = t(unitKey);
        showToast(t('scripts.app.offline.tickets', { count: ticketsEarned, unit }));
      }
      progressSeconds -= ticketsEarned * secondsPerTicket;
      progressSeconds = Math.max(0, Math.min(progressSeconds, capSeconds));
      result.ticketsEarned = ticketsEarned;
    }
    gameState.offlineTickets = {
      secondsPerTicket,
      capSeconds,
      progressSeconds
    };
  } else {
    const secondsPerTicket = OFFLINE_TICKET_CONFIG.secondsPerTicket;
    const capSeconds = Math.max(OFFLINE_TICKET_CONFIG.capSeconds, secondsPerTicket);
    gameState.offlineTickets = {
      secondsPerTicket,
      capSeconds,
      progressSeconds: 0
    };
  }

  return result;
}

function normalizeArcadeProgress(raw) {
  const base = createInitialArcadeProgress();
  if (!raw || typeof raw !== 'object') {
    return base;
  }
  const result = { version: 1, entries: { ...base.entries } };
  const sourceEntries = raw.entries && typeof raw.entries === 'object' ? raw.entries : raw;

  ARCADE_GAME_IDS.forEach(id => {
    const entry = sourceEntries[id];
    if (!entry) {
      result.entries[id] = null;
      return;
    }
    const payload = entry && typeof entry === 'object' ? (entry.state ?? entry) : null;
    if (!payload || typeof payload !== 'object') {
      result.entries[id] = null;
      return;
    }
    try {
      result.entries[id] = {
        state: JSON.parse(JSON.stringify(payload)),
        updatedAt: Number.isFinite(entry.updatedAt) ? entry.updatedAt : Date.now()
      };
    } catch (error) {
      result.entries[id] = null;
    }
  });

  return result;
}

function loadGame() {
  try {
    resetFrenzyState({ skipApply: true });
    resetTicketStarState({ reschedule: true });
    gameState.baseCrit = createDefaultCritState();
    gameState.crit = createDefaultCritState();
    gameState.lastCritical = null;
    const data = readJSON('atom2univers', null);
    if (!data) {
      gameState.theme = DEFAULT_THEME_ID;
      gameState.stats = createInitialStats();
      gameState.shopUnlocks = new Set();
      applyTheme();
      recalcProduction();
      shopController.render();
      updateUI();
      return;
    }
    gameState.atoms = LayeredNumber.fromJSON(data.atoms);
    gameState.lifetime = LayeredNumber.fromJSON(data.lifetime);
    gameState.perClick = LayeredNumber.fromJSON(data.perClick);
    gameState.perSecond = LayeredNumber.fromJSON(data.perSecond);
    const tickets = Number(data.gachaTickets);
    gameState.gachaTickets = Number.isFinite(tickets) && tickets > 0 ? Math.floor(tickets) : 0;
    const bonusTickets = Number(data.bonusParticulesTickets ?? data.bonusTickets);
    gameState.bonusParticulesTickets = Number.isFinite(bonusTickets) && bonusTickets > 0
      ? Math.floor(bonusTickets)
      : 0;
    const storedTicketStarUnlock = data.ticketStarUnlocked ?? data.ticketStarUnlock;
    if (storedTicketStarUnlock != null) {
      gameState.ticketStarUnlocked = storedTicketStarUnlock === true
        || storedTicketStarUnlock === 'true'
        || storedTicketStarUnlock === 1
        || storedTicketStarUnlock === '1';
    } else {
      gameState.ticketStarUnlocked = gameState.gachaTickets > 0;
    }
    gameState.arcadeProgress = normalizeArcadeProgress(data.arcadeProgress);
    const storedUpgrades = data.upgrades;
    if (storedUpgrades && typeof storedUpgrades === 'object') {
      const normalizedUpgrades = {};
      Object.entries(storedUpgrades).forEach(([id, value]) => {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
          return;
        }
        const sanitized = Math.max(0, Math.floor(numeric));
        if (sanitized > 0) {
          normalizedUpgrades[id] = sanitized;
        }
      });
      gameState.upgrades = normalizedUpgrades;
    } else {
      gameState.upgrades = {};
    }
    const storedShopUnlocks = data.shopUnlocks;
    if (Array.isArray(storedShopUnlocks)) {
      gameState.shopUnlocks = new Set(storedShopUnlocks);
    } else if (storedShopUnlocks && typeof storedShopUnlocks === 'object') {
      gameState.shopUnlocks = new Set(Object.keys(storedShopUnlocks));
    } else {
      gameState.shopUnlocks = new Set();
    }
    gameState.stats = parseStats(data.stats);
    gameState.trophies = new Set(Array.isArray(data.trophies) ? data.trophies : []);
    const storedPageUnlocks = data.pageUnlocks;
    if (storedPageUnlocks && typeof storedPageUnlocks === 'object') {
      const baseUnlocks = createInitialPageUnlockState();
      Object.keys(baseUnlocks).forEach(key => {
        const rawValue = storedPageUnlocks[key];
        baseUnlocks[key] = rawValue === true || rawValue === 'true' || rawValue === 1;
      });
      gameState.pageUnlocks = baseUnlocks;
    } else {
      gameState.pageUnlocks = createInitialPageUnlockState();
    }
    const storedBigBangPreference =
      data.bigBangButtonVisible ?? data.showBigBangButton ?? data.bigBangVisible ?? null;
    const wantsBigBang =
      storedBigBangPreference === true
      || storedBigBangPreference === 'true'
      || storedBigBangPreference === 1
      || storedBigBangPreference === '1';
    const hasBigBangUnlock = gameState.trophies.has(BIG_BANG_TROPHY_ID);
    gameState.bigBangButtonVisible = wantsBigBang && hasBigBangUnlock;
    const storedOffline = Number(data.offlineGainMultiplier);
    if (Number.isFinite(storedOffline) && storedOffline > 0) {
      gameState.offlineGainMultiplier = Math.min(MYTHIQUE_OFFLINE_CAP, storedOffline);
    } else {
      gameState.offlineGainMultiplier = MYTHIQUE_OFFLINE_BASE;
    }
    const storedOfflineTickets = data.offlineTickets;
    if (storedOfflineTickets && typeof storedOfflineTickets === 'object') {
      const secondsPerTicket = Number(storedOfflineTickets.secondsPerTicket);
      const normalizedSecondsPerTicket = Number.isFinite(secondsPerTicket) && secondsPerTicket > 0
        ? secondsPerTicket
        : OFFLINE_TICKET_CONFIG.secondsPerTicket;
      const capSeconds = Number(storedOfflineTickets.capSeconds);
      const normalizedCapSeconds = Number.isFinite(capSeconds) && capSeconds > 0
        ? Math.max(capSeconds, normalizedSecondsPerTicket)
        : Math.max(OFFLINE_TICKET_CONFIG.capSeconds, normalizedSecondsPerTicket);
      const progressSeconds = Number(storedOfflineTickets.progressSeconds);
      const normalizedProgressSeconds = Number.isFinite(progressSeconds) && progressSeconds > 0
        ? Math.max(0, Math.min(progressSeconds, normalizedCapSeconds))
        : 0;
      gameState.offlineTickets = {
        secondsPerTicket: normalizedSecondsPerTicket,
        capSeconds: normalizedCapSeconds,
        progressSeconds: normalizedProgressSeconds
      };
    } else if (typeof storedOfflineTickets === 'number' && storedOfflineTickets > 0) {
      const interval = Number(storedOfflineTickets);
      const normalizedSecondsPerTicket = Number.isFinite(interval) && interval > 0
        ? interval
        : OFFLINE_TICKET_CONFIG.secondsPerTicket;
      const normalizedCapSeconds = Math.max(
        OFFLINE_TICKET_CONFIG.capSeconds,
        normalizedSecondsPerTicket
      );
      gameState.offlineTickets = {
        secondsPerTicket: normalizedSecondsPerTicket,
        capSeconds: normalizedCapSeconds,
        progressSeconds: 0
      };
    } else {
      gameState.offlineTickets = {
        secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
        capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
        progressSeconds: 0
      };
    }
    gameState.sudokuOfflineBonus = normalizeSudokuOfflineBonusState(data.sudokuOfflineBonus);
    const storedInterval = Number(data.ticketStarIntervalSeconds);
    if (Number.isFinite(storedInterval) && storedInterval > 0) {
      gameState.ticketStarAverageIntervalSeconds = storedInterval;
    } else {
      gameState.ticketStarAverageIntervalSeconds = DEFAULT_TICKET_STAR_INTERVAL_SECONDS;
    }
    const storedAutoCollect = data.ticketStarAutoCollect;
    if (storedAutoCollect && typeof storedAutoCollect === 'object') {
      const rawDelay = storedAutoCollect.delaySeconds
        ?? storedAutoCollect.delay
        ?? storedAutoCollect.seconds
        ?? storedAutoCollect.value;
      const delaySeconds = Number(rawDelay);
      gameState.ticketStarAutoCollect = Number.isFinite(delaySeconds) && delaySeconds >= 0
        ? { delaySeconds }
        : null;
    } else if (storedAutoCollect === true) {
      gameState.ticketStarAutoCollect = { delaySeconds: 0 };
    } else if (typeof storedAutoCollect === 'number' && Number.isFinite(storedAutoCollect) && storedAutoCollect >= 0) {
      gameState.ticketStarAutoCollect = { delaySeconds: storedAutoCollect };
    } else {
      gameState.ticketStarAutoCollect = null;
    }
    const storedFrenzyBonus = data.frenzySpawnBonus;
    if (storedFrenzyBonus && typeof storedFrenzyBonus === 'object') {
      const perClick = Number(storedFrenzyBonus.perClick);
      const perSecond = Number(storedFrenzyBonus.perSecond);
      gameState.frenzySpawnBonus = {
        perClick: Number.isFinite(perClick) && perClick > 0 ? perClick : 1,
        perSecond: Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1
      };
    } else {
      gameState.frenzySpawnBonus = { perClick: 1, perSecond: 1 };
    }
    applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
    gameState.apsCrit = normalizeApsCritState(data.apsCrit);
    const intervalChanged = setTicketStarAverageIntervalSeconds(gameState.ticketStarAverageIntervalSeconds);
    if (intervalChanged) {
      resetTicketStarState({ reschedule: true });
    }
    const baseCollection = createInitialElementCollection();
    if (data.elements && typeof data.elements === 'object') {
      Object.entries(data.elements).forEach(([id, saved]) => {
        if (!baseCollection[id]) return;
        const reference = baseCollection[id];
        const savedCount = Number(saved?.count);
        const normalizedCount = Number.isFinite(savedCount) && savedCount > 0
          ? Math.floor(savedCount)
          : 0;
        const savedLifetime = Number(saved?.lifetime);
        let normalizedLifetime = Number.isFinite(savedLifetime) && savedLifetime > 0
          ? Math.floor(savedLifetime)
          : 0;
        if (normalizedLifetime === 0 && (saved?.owned || normalizedCount > 0)) {
          normalizedLifetime = Math.max(normalizedCount, 1);
        }
        if (normalizedLifetime < normalizedCount) {
          normalizedLifetime = normalizedCount;
        }
        baseCollection[id] = {
          id,
          gachaId: reference.gachaId,
          owned: normalizedLifetime > 0,
          count: normalizedCount,
          lifetime: normalizedLifetime,
          rarity: reference.rarity ?? (typeof saved?.rarity === 'string' ? saved.rarity : null),
          effects: Array.isArray(saved?.effects) ? [...saved.effects] : [],
          bonuses: Array.isArray(saved?.bonuses) ? [...saved.bonuses] : []
        };
      });
    }
    gameState.elements = baseCollection;
    const fusionState = createInitialFusionState();
    if (data.fusions && typeof data.fusions === 'object') {
      Object.keys(fusionState).forEach(id => {
        const stored = data.fusions[id];
        if (!stored || typeof stored !== 'object') {
          fusionState[id] = { attempts: 0, successes: 0 };
          return;
        }
        const attemptsRaw = Number(
          stored.attempts
            ?? stored.tries
            ?? stored.tentatives
            ?? stored.total
            ?? 0
        );
        const successesRaw = Number(
          stored.successes
            ?? stored.success
            ?? stored.victories
            ?? stored.wins
            ?? 0
        );
        fusionState[id] = {
          attempts: Number.isFinite(attemptsRaw) && attemptsRaw > 0 ? Math.floor(attemptsRaw) : 0,
          successes: Number.isFinite(successesRaw) && successesRaw > 0 ? Math.floor(successesRaw) : 0
        };
      });
    }
    gameState.fusions = fusionState;
    const fusionBonuses = createInitialFusionBonuses();
    const storedFusionBonuses = data.fusionBonuses;
    if (storedFusionBonuses && typeof storedFusionBonuses === 'object') {
      const apc = Number(
        storedFusionBonuses.apcFlat
          ?? storedFusionBonuses.apc
          ?? storedFusionBonuses.perClick
          ?? storedFusionBonuses.click
          ?? 0
      );
      const aps = Number(
        storedFusionBonuses.apsFlat
          ?? storedFusionBonuses.aps
          ?? storedFusionBonuses.perSecond
          ?? storedFusionBonuses.auto
          ?? 0
      );
      fusionBonuses.apcFlat = Number.isFinite(apc) ? apc : 0;
      fusionBonuses.apsFlat = Number.isFinite(aps) ? aps : 0;
    }
    gameState.fusionBonuses = fusionBonuses;
    gameState.theme = getThemeDefinition(data.theme) ? data.theme : DEFAULT_THEME_ID;
    const storedBrickSkin = data.arcadeBrickSkin
      ?? data.particulesBrickSkin
      ?? data.brickSkin
      ?? null;
    gameState.arcadeBrickSkin = normalizeBrickSkinSelection(storedBrickSkin);
    if (typeof setParticulesBrickSkinPreference === 'function') {
      setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
    }
    const parseStoredVolume = raw => {
      const numeric = Number(raw);
      if (!Number.isFinite(numeric)) {
        return null;
      }
      if (numeric > 1) {
        return clampMusicVolume(numeric / 100, DEFAULT_MUSIC_VOLUME);
      }
      return clampMusicVolume(numeric, DEFAULT_MUSIC_VOLUME);
    };

    const storedMusic = data.music;
    if (storedMusic && typeof storedMusic === 'object') {
      const selected = storedMusic.selectedTrack
        ?? storedMusic.track
        ?? storedMusic.id
        ?? storedMusic.filename;
      gameState.musicTrackId = typeof selected === 'string' && selected ? selected : null;
      const storedVolume = parseStoredVolume(storedMusic.volume);
      if (storedVolume != null) {
        gameState.musicVolume = storedVolume;
      } else if (typeof data.musicVolume === 'number') {
        const fallbackVolume = parseStoredVolume(data.musicVolume);
        gameState.musicVolume = fallbackVolume != null ? fallbackVolume : DEFAULT_MUSIC_VOLUME;
      } else {
        gameState.musicVolume = DEFAULT_MUSIC_VOLUME;
      }
      if (typeof storedMusic.enabled === 'boolean') {
        gameState.musicEnabled = storedMusic.enabled;
      } else if (typeof data.musicEnabled === 'boolean') {
        gameState.musicEnabled = data.musicEnabled;
      } else if (gameState.musicTrackId) {
        gameState.musicEnabled = true;
      } else {
        gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
      }
    } else {
      if (typeof data.musicTrackId === 'string' && data.musicTrackId) {
        gameState.musicTrackId = data.musicTrackId;
      } else if (typeof data.musicTrack === 'string' && data.musicTrack) {
        gameState.musicTrackId = data.musicTrack;
      } else {
        gameState.musicTrackId = null;
      }
      if (typeof data.musicEnabled === 'boolean') {
        gameState.musicEnabled = data.musicEnabled;
      } else if (gameState.musicTrackId) {
        gameState.musicEnabled = true;
      } else {
        gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
      }
      const fallbackVolume = parseStoredVolume(data.musicVolume);
      gameState.musicVolume = fallbackVolume != null ? fallbackVolume : DEFAULT_MUSIC_VOLUME;
    }
    if (gameState.musicEnabled === false) {
      gameState.musicTrackId = null;
    }
    evaluatePageUnlocks({ save: false, deferUI: true });
    getShopUnlockSet();
    applyTheme();
    recalcProduction();
    shopController.render();
    updateBigBangVisibility();
    updateUI();
    if (data.lastSave) {
      const diff = Math.max(0, (Date.now() - data.lastSave) / 1000);
      applyOfflineProgress(diff);
    }
  } catch (err) {
    console.error('Erreur de chargement', err);
    resetGame();
  }
}

let lastUpdate = performance.now();
let lastSaveTime = performance.now();
let lastUIUpdate = performance.now();

function updatePlaytime(deltaSeconds) {
  if (!gameState.stats) return;
  const deltaMs = deltaSeconds * 1000;
  if (!Number.isFinite(deltaMs) || deltaMs <= 0) return;
  gameState.stats.session.onlineTimeMs += deltaMs;
  gameState.stats.global.playTimeMs += deltaMs;
}

function loop(now) {
  const delta = Math.max(0, (now - lastUpdate) / 1000);
  lastUpdate = now;

  if (!gameState.perSecond.isZero()) {
    const gain = gameState.perSecond.multiplyNumber(delta);
    gainAtoms(gain, 'aps');
  }

  updateApsCritTimer(delta);

  updateClickVisuals(now);
  updatePlaytime(delta);
  updateFrenzies(delta, now);
  updateTicketStar(delta, now);

  if (now - lastUIUpdate > 250) {
    updateUI();
    lastUIUpdate = now;
  }

  if (now - lastSaveTime > 5000) {
    saveGame();
    lastSaveTime = now;
  }

  requestAnimationFrame(loop);
}

window.addEventListener('beforeunload', saveGame);

function startApp() {
  loadGame();
  musicPlayer.init({
    preferredTrackId: gameState.musicTrackId,
    autoplay: gameState.musicEnabled !== false,
    volume: gameState.musicVolume
  });
  musicPlayer.ready().then(() => {
    refreshMusicControls();
  });
  recalcProduction();
  evaluateTrophies();
  shopController.render();
  if (goalsController) {
    goalsController.render();
  }
  updateUI();
  randomizeAtomButtonImage();
  initStarfield();
  requestAnimationFrame(loop);
}

function initializeApp() {
  populateLanguageSelectOptions();
  const i18n = globalThis.i18n;
  if (i18n && typeof i18n.setLanguage === 'function') {
    const preferredLanguage = getInitialLanguagePreference();
    updateLanguageSelectorValue(preferredLanguage);
    i18n
      .setLanguage(preferredLanguage)
      .then(() => {
        const appliedLanguage = i18n.getCurrentLanguage
          ? i18n.getCurrentLanguage()
          : preferredLanguage;
        writeStoredLanguagePreference(appliedLanguage);
      })
      .catch((error) => {
        console.error('Unable to load language resources', error);
      })
      .finally(() => {
        if (typeof i18n.updateTranslations === 'function') {
          i18n.updateTranslations(document);
        }
        updateLanguageSelectorValue(i18n.getCurrentLanguage ? i18n.getCurrentLanguage() : preferredLanguage);
        startApp();
      });
    return;
  }
  updateLanguageSelectorValue(getInitialLanguagePreference());
  startApp();
}

export { gameState, initializeApp, loadGame, saveGame, startApp };
