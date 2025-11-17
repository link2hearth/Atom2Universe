const APP_DATA = typeof globalThis !== 'undefined' && globalThis.APP_DATA ? globalThis.APP_DATA : {};
const GLOBAL_CONFIG =
  typeof globalThis !== 'undefined' && globalThis.GAME_CONFIG ? globalThis.GAME_CONFIG : {};

function resolveGlobalBooleanFlag(flagName, fallback = true) {
  if (typeof globalThis !== 'undefined' && typeof globalThis[flagName] === 'boolean') {
    return globalThis[flagName];
  }
  return fallback;
}

function isDevkitFeatureEnabled() {
  return resolveGlobalBooleanFlag('DEVKIT_ENABLED', true);
}

function isCollectionFeatureEnabled() {
  return resolveGlobalBooleanFlag('COLLECTION_SYSTEM_ENABLED', true);
}

function isInfoSectionsFeatureEnabled() {
  return resolveGlobalBooleanFlag('INFO_SECTIONS_ENABLED', true);
}

function isMusicModuleEnabled() {
  return resolveGlobalBooleanFlag('MUSIC_MODULE_ENABLED', true);
}

function isEscapeAdvancedDifficultiesEnabled() {
  return resolveGlobalBooleanFlag('ESCAPE_ADVANCED_DIFFICULTIES_ENABLED', false);
}

function isAtomImageVariantEnabled() {
  return resolveGlobalBooleanFlag('ATOM_IMAGE_VARIANT_ENABLED', false);
}

function toggleDevkitFeatureAvailability() {
  if (typeof globalThis !== 'undefined' && typeof globalThis.toggleDevkitFeatureEnabled === 'function') {
    return globalThis.toggleDevkitFeatureEnabled();
  }
  const next = !isDevkitFeatureEnabled();
  if (typeof globalThis !== 'undefined') {
    globalThis.DEVKIT_ENABLED = next;
  }
  return next;
}

function toggleCollectionFeatureAvailability() {
  if (
    typeof globalThis !== 'undefined'
    && typeof globalThis.toggleCollectionFeatureEnabled === 'function'
  ) {
    return globalThis.toggleCollectionFeatureEnabled();
  }
  const next = !isCollectionFeatureEnabled();
  if (typeof globalThis !== 'undefined') {
    globalThis.COLLECTION_SYSTEM_ENABLED = next;
  }
  return next;
}

function toggleInfoSectionsFeatureAvailability() {
  if (
    typeof globalThis !== 'undefined'
    && typeof globalThis.toggleInfoSectionsFeatureEnabled === 'function'
  ) {
    return globalThis.toggleInfoSectionsFeatureEnabled();
  }
  const next = !isInfoSectionsFeatureEnabled();
  if (typeof globalThis !== 'undefined') {
    globalThis.INFO_SECTIONS_ENABLED = next;
  }
  return next;
}

function toggleMusicModuleAvailability() {
  if (
    typeof globalThis !== 'undefined'
    && typeof globalThis.toggleMusicModuleEnabled === 'function'
  ) {
    return globalThis.toggleMusicModuleEnabled();
  }
  const next = !isMusicModuleEnabled();
  if (typeof globalThis !== 'undefined') {
    globalThis.MUSIC_MODULE_ENABLED = next;
  }
  return next;
}

function toggleAtomImageVariantAvailability() {
  if (
    typeof globalThis !== 'undefined'
    && typeof globalThis.toggleAtomImageVariantEnabled === 'function'
  ) {
    return globalThis.toggleAtomImageVariantEnabled();
  }
  const next = !isAtomImageVariantEnabled();
  if (typeof globalThis !== 'undefined') {
    globalThis.ATOM_IMAGE_VARIANT_ENABLED = next;
  }
  return next;
}

function toggleEscapeAdvancedDifficultiesAvailability() {
  if (
    typeof globalThis !== 'undefined'
    && typeof globalThis.toggleEscapeAdvancedDifficultiesEnabled === 'function'
  ) {
    return globalThis.toggleEscapeAdvancedDifficultiesEnabled();
  }
  const next = !isEscapeAdvancedDifficultiesEnabled();
  if (typeof globalThis !== 'undefined') {
    globalThis.ESCAPE_ADVANCED_DIFFICULTIES_ENABLED = next;
  }
  if (typeof window !== 'undefined') {
    try {
      const detail = { enabled: next };
      const eventName = 'escape:advanced-difficulties-changed';
      if (typeof window.CustomEvent === 'function') {
        window.dispatchEvent(new CustomEvent(eventName, { detail }));
      } else if (typeof document !== 'undefined' && typeof document.createEvent === 'function') {
        const event = document.createEvent('CustomEvent');
        event.initCustomEvent(eventName, false, false, detail);
        window.dispatchEvent(event);
      }
    } catch (error) {
      // Ignore dispatch errors in the fallback path.
    }
  }
  return next;
}

const CONFIG_OPTIONS_WELCOME_CARD =
  GLOBAL_CONFIG
  && GLOBAL_CONFIG.uiText
  && GLOBAL_CONFIG.uiText.options
  && GLOBAL_CONFIG.uiText.options.welcomeCard
    ? GLOBAL_CONFIG.uiText.options.welcomeCard
    : null;

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

const DEFAULT_STARTUP_FADE_DURATION_MS = 2000;

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

const BRICK_SKIN_CHOICES = Object.freeze(['original', 'particules', 'metallic', 'neon', 'pastels']);

const BRICK_SKIN_TOAST_KEYS = Object.freeze({
  original: 'scripts.app.brickSkins.applied.original',
  particules: 'scripts.app.brickSkins.applied.particules',
  metallic: 'scripts.app.brickSkins.applied.metallic',
  neon: 'scripts.app.brickSkins.applied.neon',
  pastels: 'scripts.app.brickSkins.applied.pastels'
});

const DEFAULT_FRENZY_AUTO_COLLECT_DELAY_MS = 1000;

const FRENZY_AUTO_COLLECT_DELAY_MS = (() => {
  const config = GLOBAL_CONFIG?.frenzies?.autoCollect;
  if (config && typeof config === 'object') {
    const seconds = Number(config.delaySeconds ?? config.seconds);
    if (Number.isFinite(seconds) && seconds >= 0) {
      return Math.floor(seconds * 1000);
    }
    const millis = Number(config.delayMs ?? config.milliseconds);
    if (Number.isFinite(millis) && millis >= 0) {
      return Math.floor(millis);
    }
  }
  return DEFAULT_FRENZY_AUTO_COLLECT_DELAY_MS;
})();

const CRYPTO_WIDGET_CONFIG = typeof CRYPTO_WIDGET_SETTINGS !== 'undefined'
  && CRYPTO_WIDGET_SETTINGS
  && typeof CRYPTO_WIDGET_SETTINGS === 'object'
    ? CRYPTO_WIDGET_SETTINGS
    : null;

const CRYPTO_WIDGET_REFRESH_INTERVAL_MS = (() => {
  const fallback = 60 * 1000;
  const raw = Number(CRYPTO_WIDGET_CONFIG?.refreshIntervalMs);
  if (Number.isFinite(raw) && raw >= 1000) {
    return Math.floor(raw);
  }
  return fallback;
})();

const CRYPTO_WIDGET_BASE_URL = (() => {
  const raw = typeof CRYPTO_WIDGET_CONFIG?.apiBaseUrl === 'string'
    ? CRYPTO_WIDGET_CONFIG.apiBaseUrl.trim()
    : '';
  if (!raw) {
    return 'https://api.binance.com';
  }
  return raw.replace(/\/+$/, '');
})();

function normalizeCryptoWidgetEndpoint(endpoint, fallback) {
  if (typeof endpoint === 'string' && endpoint.trim()) {
    return endpoint.trim();
  }
  if (typeof fallback === 'string') {
    return fallback;
  }
  return '';
}

const CRYPTO_WIDGET_ENDPOINTS = Object.freeze({
  btc: normalizeCryptoWidgetEndpoint(CRYPTO_WIDGET_CONFIG?.btcEndpoint, '/api/v3/ticker/price?symbol=BTCUSDT'),
  eth: normalizeCryptoWidgetEndpoint(CRYPTO_WIDGET_CONFIG?.ethEndpoint, '/api/v3/ticker/price?symbol=ETHUSDT')
});

const CRYPTO_WIDGET_DEFAULT_ENABLED = CRYPTO_WIDGET_CONFIG?.enabledByDefault === true;
const CRYPTO_WIDGET_LOADING_KEY = 'index.sections.game.cryptoWidget.loading';
const CRYPTO_WIDGET_ERROR_KEY = 'index.sections.game.cryptoWidget.error';
const CRYPTO_WIDGET_PLACEHOLDER = '---';

  const PAGE_FEATURE_MAP = Object.freeze({
    arcadeHub: 'arcade.hub',
    arcade: 'arcade.particules',
    metaux: 'arcade.metaux',
    wave: 'arcade.photon',
    quantum2048: 'arcade.objectx',
    bigger: 'arcade.bigger',
    balance: 'arcade.balance',
    theLine: 'arcade.theLine',
    lightsOut: 'arcade.lightsOut',
    link: 'arcade.link',
    starBridges: 'arcade.starBridges',
    starsWar: 'arcade.starsWar',
    pipeTap: 'arcade.pipeTap',
    colorStack: 'arcade.colorStack',
    motocross: 'arcade.motocross',
    hex: 'arcade.hex',
    twins: 'arcade.twins',
    sokoban: 'arcade.sokoban',
    taquin: 'arcade.taquin',
    math: 'arcade.math',
    sudoku: 'arcade.sudoku',
    minesweeper: 'arcade.demineur',
    solitaire: 'arcade.solitaire',
    pachinko: 'arcade.pachinko',
    blackjack: 'arcade.blackjack',
    holdem: 'arcade.holdem',
    dice: 'arcade.dice',
    echecs: 'arcade.echecs',
    gameOfLife: 'arcade.gameOfLife',
    escape: 'arcade.escape'
  });

const OPTIONS_DETAIL_FEATURE_MAP = Object.freeze({
  particles: 'arcade.particules',
  match3: 'arcade.metaux',
  photon: 'arcade.photon',
  objectx: 'arcade.objectx',
  balance: 'arcade.balance',
  math: 'arcade.math',
  sudoku: 'arcade.sudoku',
  demineur: 'arcade.demineur',
  solitaire: 'arcade.solitaire',
  pachinko: 'arcade.pachinko',
  echecs: 'arcade.echecs',
  holdem: 'arcade.holdem',
  dice: 'arcade.dice',
  gameOfLife: 'arcade.gameOfLife',
  lightsOut: 'arcade.lightsOut',
  starBridges: 'arcade.starBridges',
  pipeTap: 'arcade.pipeTap',
  colorStack: 'arcade.colorStack',
  motocross: 'arcade.motocross',
  twins: 'arcade.twins',
  sokoban: 'arcade.sokoban',
  taquin: 'arcade.taquin',
  blackjack: 'arcade.blackjack',
  gacha: 'system.gacha',
  tableau: 'system.tableau',
  fusion: 'system.fusion',
  musique: 'system.musique'
});

const METAL_FEATURE_ID = 'arcade.metaux';

const FEATURE_UNLOCK_DEFINITIONS = buildFeatureUnlockDefinitions(
  GLOBAL_CONFIG?.progression?.featureUnlocks
);

let featureUnlockCache = new Map();
let cachedOptionsDetailMetadata = null;
let lastArcadeUnlockState = null;
let arcadeHubCardStateCache = null;
let arcadeHubCardOrderCache = null;
let activeArcadeHubCardDrag = null;
let lastBigBangUnlockedState = false;
let collectionVideosUnlockedCache = null;

const ARCADE_HUB_CARD_COLLAPSE_LABEL_KEY = 'index.sections.arcadeHub.cards.toggle.collapse';
const ARCADE_HUB_CARD_EXPAND_LABEL_KEY = 'index.sections.arcadeHub.cards.toggle.expand';

const DEFAULT_SUDOKU_COMPLETION_REWARD = Object.freeze({
  enabled: true,
  levels: Object.freeze({
    facile: Object.freeze({
      bonusSeconds: 6 * 60 * 60,
      multiplier: 1,
      validSeconds: 6 * 60 * 60
    }),
    moyen: Object.freeze({
      bonusSeconds: 12 * 60 * 60,
      multiplier: 1,
      validSeconds: 12 * 60 * 60
    }),
    difficile: Object.freeze({
      bonusSeconds: 24 * 60 * 60,
      multiplier: 1,
      validSeconds: 24 * 60 * 60
    })
  })
});

const PRIMARY_SAVE_STORAGE_KEY = 'atom2univers';
const RELOAD_SAVE_STORAGE_KEY = 'atom2univers.reloadPendingSave';
const LANGUAGE_STORAGE_KEY = 'atom2univers.language';
const CLICK_SOUND_STORAGE_KEY = 'atom2univers.options.clickSoundMuted';
const CRIT_ATOM_VISUALS_STORAGE_KEY = 'atom2univers.options.critAtomVisualsDisabled';
const ATOM_ANIMATION_PREFERENCE_STORAGE_KEY = 'atom2univers.options.atomAnimationsEnabled';
const FRENZY_AUTO_COLLECT_STORAGE_KEY = 'atom2univers.options.frenzyAutoCollectEnabled';
const CRYPTO_WIDGET_STORAGE_KEY = 'atom2univers.options.cryptoWidgetEnabled';
const SCREEN_WAKE_LOCK_STORAGE_KEY = 'atom2univers.options.screenWakeLockEnabled';
const TEXT_FONT_STORAGE_KEY = 'atom2univers.options.textFont';
const INFO_WELCOME_COLLAPSED_STORAGE_KEY = 'atom2univers.info.welcomeCollapsed';
const INFO_ACHIEVEMENTS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.achievementsCollapsed';
const INFO_CHARACTERS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.charactersCollapsed';
const INFO_CARDS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.cardsCollapsed';
const INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.calculationsCollapsed';
const INFO_PROGRESS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.progressCollapsed';
const INFO_SCORES_COLLAPSED_STORAGE_KEY = 'atom2univers.info.scoresCollapsed';
const COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.imagesCollapsed';
const COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.bonusImagesCollapsed';
const COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.bonus2ImagesCollapsed';
const COLLECTION_VIDEOS_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.videosCollapsed';
const COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY = 'atom2univers.collection.videosUnlocked';
const COLLECTION_VIDEOS_STATE_STORAGE_KEY = 'atom2univers.collection.videosState.v1';
const HEADER_COLLAPSED_STORAGE_KEY = 'atom2univers.ui.headerCollapsed';
const ARCADE_HUB_CARD_STATE_STORAGE_KEY = 'atom2univers.arcadeHub.cardStates.v1';
const ARCADE_HUB_CARD_ORDER_STORAGE_KEY = 'atom2univers.arcadeHub.cardOrder.v1';
const ARCADE_HUB_CARD_REORDER_DELAY_MS = 1500;
const ARCADE_HUB_CARD_REORDER_MOVE_THRESHOLD = 16;
const ARCADE_HUB_CARD_REORDER_TOUCH_MOVE_THRESHOLD = 36;
const ARCADE_HUB_CARD_REORDER_PEN_MOVE_THRESHOLD = 24;
const ARCADE_AUTOSAVE_STORAGE_KEY = 'atom2univers.arcadeSaves.v1';
const CHESS_LIBRARY_STORAGE_KEY = 'atom2univers.arcade.echecs';
const QUANTUM_2048_STORAGE_KEY = 'atom2univers.quantum2048.parallelUniverses';
const BIGGER_STORAGE_KEY = 'atom2univers.arcade.bigger';
const GAME_OF_LIFE_STORAGE_KEYS = [
  'atom2univers.arcade.gameOfLife.seed',
  'atom2univers.arcade.gameOfLife.customPatterns',
  'atom2univers.arcade.gameOfLife.customPatternCounter'
];

const SAVE_BACKUP_SETTINGS_GLOBAL = typeof globalThis !== 'undefined'
  && globalThis.SAVE_BACKUP_SETTINGS
  && typeof globalThis.SAVE_BACKUP_SETTINGS === 'object'
  ? globalThis.SAVE_BACKUP_SETTINGS
  : null;
const SAVE_BACKUP_STORAGE_KEY = SAVE_BACKUP_SETTINGS_GLOBAL?.storageKey || 'atom2univers.backups';
const SAVE_BACKUP_MAX_ENTRIES = Math.max(
  1,
  Math.floor(Number(SAVE_BACKUP_SETTINGS_GLOBAL?.maxEntries) || 8)
);
const SAVE_BACKUP_MIN_AUTO_INTERVAL_MS = Math.max(
  0,
  Math.floor(Number(SAVE_BACKUP_SETTINGS_GLOBAL?.minAutoIntervalMs) || 5 * 60 * 1000)
);
const SAVE_BACKUP_NATIVE_LIMIT = Math.max(
  1,
  Math.floor(Number(SAVE_BACKUP_SETTINGS_GLOBAL?.maxNativeEntries) || SAVE_BACKUP_MAX_ENTRIES)
);

let lastSerializedSave = null;
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

const PERFORMANCE_MODE_STORAGE_KEY = 'atom2univers.options.performanceMode';
const GLOBAL_PERFORMANCE_MODE_SETTINGS = typeof globalThis !== 'undefined'
  ? globalThis.PERFORMANCE_MODE_SETTINGS
  : null;
const RESOLVED_PERFORMANCE_MODE_SETTINGS = GLOBAL_PERFORMANCE_MODE_SETTINGS
  && typeof GLOBAL_PERFORMANCE_MODE_SETTINGS === 'object'
  ? GLOBAL_PERFORMANCE_MODE_SETTINGS
  : {
    fluid: Object.freeze({
      apcFlushIntervalMs: 0,
      apsFlushIntervalMs: 0,
      frameIntervalMs: 0,
      atomAnimation: Object.freeze({
        amplitudeScale: 1,
        motionScale: 1
      })
    }),
    eco: Object.freeze({
      apcFlushIntervalMs: 200,
      apsFlushIntervalMs: 1000,
      frameIntervalMs: 120,
      atomAnimation: Object.freeze({
        amplitudeScale: 0.5,
        motionScale: 0.65
      })
    })
  };
const GAME_LOOP_MIN_TIMEOUT_MS = 16;
const GLOBAL_PERFORMANCE_MODE_DEFINITIONS = typeof globalThis !== 'undefined'
  ? globalThis.PERFORMANCE_MODE_DEFINITIONS
  : null;
const RESOLVED_PERFORMANCE_MODE_DEFINITIONS = Array.isArray(GLOBAL_PERFORMANCE_MODE_DEFINITIONS)
  && GLOBAL_PERFORMANCE_MODE_DEFINITIONS.length
  ? GLOBAL_PERFORMANCE_MODE_DEFINITIONS
  : [
    Object.freeze({ id: 'fluid', labelKey: 'index.sections.options.performance.options.fluid' }),
    Object.freeze({ id: 'eco', labelKey: 'index.sections.options.performance.options.eco', isDefault: true })
  ];
const PERFORMANCE_MODE_IDS = RESOLVED_PERFORMANCE_MODE_DEFINITIONS
  .map(def => (def && typeof def.id === 'string') ? def.id.trim().toLowerCase() : null)
  .filter((id, index, array) => id && array.indexOf(id) === index);
const PERFORMANCE_MODE_DEFAULT_ID = resolveDefaultPerformanceModeId();
const PERFORMANCE_MODE_NOTE_FALLBACKS = Object.freeze({
  fluid: 'Fluid mode: frequent updates for instant feedback.',
  eco: 'Eco mode: batches clicks every 200 ms and APS once per second to save battery.'
});

const DEFAULT_ATOM_ANIMATION_SETTINGS = Object.freeze({
  amplitudeScale: 1,
  motionScale: 1
});

function normalizeAtomAnimationSettings(settings) {
  const amplitudeRaw = Number(settings?.amplitudeScale);
  const motionRaw = Number(settings?.motionScale);
  const amplitudeScale = Number.isFinite(amplitudeRaw) && amplitudeRaw > 0
    ? Math.min(Math.max(amplitudeRaw, 0.1), 1)
    : DEFAULT_ATOM_ANIMATION_SETTINGS.amplitudeScale;
  const motionScale = Number.isFinite(motionRaw) && motionRaw > 0
    ? Math.min(Math.max(motionRaw, 0.1), 1)
    : DEFAULT_ATOM_ANIMATION_SETTINGS.motionScale;
  return Object.freeze({ amplitudeScale, motionScale });
}

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

const UI_SCALE_STORAGE_KEY = 'atom2univers.options.uiScale';

const UI_SCALE_CONFIG = (() => {
  const fallbackChoices = {
    small: Object.freeze({ id: 'small', factor: 0.75 }),
    normal: Object.freeze({ id: 'normal', factor: 1 }),
    large: Object.freeze({ id: 'large', factor: 1.5 }),
    x2: Object.freeze({ id: 'x2', factor: 2 })
  };
  const fallback = {
    defaultId: 'large',
    choices: Object.freeze(fallbackChoices)
  };

  const rawConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.ui && GLOBAL_CONFIG.ui.scale;
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
})();

const UI_SCALE_CHOICES = UI_SCALE_CONFIG.choices;
const UI_SCALE_DEFAULT = UI_SCALE_CONFIG.defaultId;

const DEFAULT_UI_SCALE_FACTOR = (() => {
  const config = UI_SCALE_CHOICES?.[UI_SCALE_DEFAULT];
  const factor = Number(config?.factor);
  return Number.isFinite(factor) && factor > 0 ? factor : 1;
})();

let currentUiScaleSelection = UI_SCALE_DEFAULT;
let currentUiScaleFactor = DEFAULT_UI_SCALE_FACTOR;
let autoUiScaleFactor = 1;
let pendingAutoUiScaleFrame = null;
let pendingAutoUiScaleTimeoutId = null;
let autoUiScaleFrameUsesTimeout = false;

const AUTO_UI_SCALE_MIN_FACTOR = 0.7;
const AUTO_UI_SCALE_TOLERANCE = 0.015;
const AUTO_UI_SCALE_SAFE_PADDING = 24;
const AUTO_UI_SCALE_OVERFLOW_TOLERANCE = 12;
const AUTO_UI_SCALE_VERTICAL_PADDING = 32;
const AUTO_UI_SCALE_HEIGHT_TOLERANCE = 32;

let critAtomVisualsDisabled = false;
let atomAnimationsEnabled = true;
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

const HOLDEM_NUMBER_FORMAT = Object.freeze({ maximumFractionDigits: 0, minimumFractionDigits: 0 });

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

function resolveLayeredConfigValue(entry, fallback) {
  const fallbackValue = fallback instanceof LayeredNumber
    ? fallback.clone()
    : new LayeredNumber(fallback != null ? fallback : 0);
  if (entry instanceof LayeredNumber) {
    return entry.clone();
  }
  if (typeof entry === 'number' || typeof entry === 'string') {
    return new LayeredNumber(entry);
  }
  if (entry && typeof entry === 'object') {
    if (typeof entry.sign === 'number' && typeof entry.layer === 'number') {
      return LayeredNumber.fromJSON(entry);
    }
    const type = entry.type ?? entry.layer;
    if (type === 'layer0' || type === 0) {
      const mantissa = Number(entry.mantissa ?? entry.value ?? entry.amount ?? 1);
      const exponent = Number(entry.exponent ?? entry.power ?? entry.exp ?? 0);
      if (Number.isFinite(mantissa) && Number.isFinite(exponent)) {
        return LayeredNumber.fromLayer0(mantissa, exponent);
      }
    }
    if (type === 'layer1' || type === 1) {
      const value = Number(entry.value ?? entry.amount ?? entry.exponent ?? 0);
      if (Number.isFinite(value)) {
        return LayeredNumber.fromLayer1(value);
      }
    }
    if (typeof entry.value === 'number') {
      return new LayeredNumber(entry.value);
    }
  }
  return fallbackValue;
}

function resolveDefaultPerformanceModeId() {
  const preferred = RESOLVED_PERFORMANCE_MODE_DEFINITIONS.find(def => {
    return def && typeof def.id === 'string' && def.isDefault === true;
  });
  if (preferred) {
    const normalized = preferred.id.trim().toLowerCase();
    if (normalized) {
      return normalized;
    }
  }
  if (PERFORMANCE_MODE_IDS.length) {
    return PERFORMANCE_MODE_IDS[0];
  }
  return 'fluid';
}

function normalizePerformanceMode(value) {
  if (typeof value !== 'string') {
    return PERFORMANCE_MODE_DEFAULT_ID;
  }
  const normalized = value.trim().toLowerCase();
  if (!normalized) {
    return PERFORMANCE_MODE_DEFAULT_ID;
  }
  return PERFORMANCE_MODE_IDS.includes(normalized) ? normalized : PERFORMANCE_MODE_DEFAULT_ID;
}

function getPerformanceModeSettings(modeId) {
  const normalized = normalizePerformanceMode(modeId);
  const settings = RESOLVED_PERFORMANCE_MODE_SETTINGS?.[normalized];
  if (settings && typeof settings === 'object') {
    return settings;
  }
  const fallback = RESOLVED_PERFORMANCE_MODE_SETTINGS?.[PERFORMANCE_MODE_DEFAULT_ID];
  if (fallback && typeof fallback === 'object') {
    return fallback;
  }
  return { apcFlushIntervalMs: 0, apsFlushIntervalMs: 0 };
}

function readStoredPerformanceMode() {
  try {
    const stored = globalThis.localStorage?.getItem(PERFORMANCE_MODE_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizePerformanceMode(stored);
    }
  } catch (error) {
    console.warn('Unable to read performance mode preference', error);
  }
  return null;
}

function writeStoredPerformanceMode(value) {
  try {
    const normalized = normalizePerformanceMode(value);
    globalThis.localStorage?.setItem(PERFORMANCE_MODE_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist performance mode preference', error);
  }
}

function updatePerformanceModeNote(modeId) {
  if (!elements.performanceModeNote) {
    return;
  }
  const normalized = normalizePerformanceMode(modeId);
  const key = `index.sections.options.performance.note.${normalized}`;
  const fallback = PERFORMANCE_MODE_NOTE_FALLBACKS[normalized]
    || PERFORMANCE_MODE_NOTE_FALLBACKS[PERFORMANCE_MODE_DEFAULT_ID]
    || '';
  elements.performanceModeNote.setAttribute('data-i18n', key);
  elements.performanceModeNote.textContent = translateOrDefault(key, fallback);
}

function flushManualApcGains(now, options = {}) {
  const config = Object.assign({ force: false }, options);
  const interval = Number(performanceModeState.settings?.apcFlushIntervalMs) || 0;
  const pending = performanceModeState.pendingManualGain;
  if (!(pending instanceof LayeredNumber) || pending.isZero() || pending.sign <= 0) {
    if (config.force) {
      performanceModeState.pendingManualGain = null;
      performanceModeState.lastManualFlush = now;
    }
    return;
  }
  if (!config.force && interval > 0) {
    const elapsed = now - performanceModeState.lastManualFlush;
    if (Number.isFinite(elapsed) && elapsed < interval) {
      return;
    }
  }
  gainAtoms(pending, 'apc');
  performanceModeState.pendingManualGain = null;
  performanceModeState.lastManualFlush = now;
}

function queueManualApcGain(amount, now = (typeof performance !== 'undefined' && typeof performance.now === 'function'
  ? performance.now()
  : Date.now())) {
  if (!(amount instanceof LayeredNumber) || amount.isZero() || amount.sign <= 0) {
    return;
  }
  const interval = Number(performanceModeState.settings?.apcFlushIntervalMs) || 0;
  if (interval <= 0) {
    gainAtoms(amount, 'apc');
    performanceModeState.lastManualFlush = now;
    return;
  }
  const hasPending = performanceModeState.pendingManualGain instanceof LayeredNumber
    && !performanceModeState.pendingManualGain.isZero()
    && performanceModeState.pendingManualGain.sign > 0;
  if (!hasPending) {
    performanceModeState.lastManualFlush = now;
    performanceModeState.pendingManualGain = amount.clone();
    return;
  }
  performanceModeState.pendingManualGain = performanceModeState.pendingManualGain.add(amount);
}

function accumulateAutoProduction(deltaSeconds) {
  if (!Number.isFinite(deltaSeconds) || deltaSeconds <= 0) {
    return;
  }
  const interval = Number(performanceModeState.settings?.apsFlushIntervalMs) || 0;
  if (interval <= 0) {
    if (gameState.perSecond instanceof LayeredNumber && !gameState.perSecond.isZero()) {
      const gain = gameState.perSecond.multiplyNumber(deltaSeconds);
      if (gain instanceof LayeredNumber && !gain.isZero()) {
        gainAtoms(gain, 'aps');
      }
    }
    performanceModeState.pendingAutoGain = null;
    performanceModeState.autoAccumulatedMs = 0;
    return;
  }
  let added = false;
  if (gameState.perSecond instanceof LayeredNumber && !gameState.perSecond.isZero()) {
    const increment = gameState.perSecond.multiplyNumber(deltaSeconds);
    if (increment instanceof LayeredNumber && !increment.isZero()) {
      performanceModeState.pendingAutoGain = performanceModeState.pendingAutoGain
        ? performanceModeState.pendingAutoGain.add(increment)
        : increment;
      added = true;
    }
  }
  if (added || (performanceModeState.pendingAutoGain instanceof LayeredNumber
    && !performanceModeState.pendingAutoGain.isZero())) {
    performanceModeState.autoAccumulatedMs += deltaSeconds * 1000;
  }
}

function flushPendingAutoGain(now, options = {}) {
  const config = Object.assign({ force: false }, options);
  const interval = Number(performanceModeState.settings?.apsFlushIntervalMs) || 0;
  if (interval <= 0) {
    performanceModeState.pendingAutoGain = null;
    performanceModeState.autoAccumulatedMs = 0;
    performanceModeState.lastAutoFlush = now;
    return;
  }
  const pending = performanceModeState.pendingAutoGain;
  if (!(pending instanceof LayeredNumber) || pending.isZero() || pending.sign <= 0) {
    if (config.force) {
      performanceModeState.pendingAutoGain = null;
      performanceModeState.autoAccumulatedMs = 0;
      performanceModeState.lastAutoFlush = now;
    }
    return;
  }
  if (!config.force) {
    const accumulated = performanceModeState.autoAccumulatedMs;
    if (Number.isFinite(accumulated) && accumulated < interval) {
      return;
    }
  }
  gainAtoms(pending, 'aps');
  performanceModeState.pendingAutoGain = null;
  performanceModeState.autoAccumulatedMs = 0;
  performanceModeState.lastAutoFlush = now;
}

function flushPendingPerformanceQueues(options = {}) {
  const now = typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now();
  flushManualApcGains(now, options);
  flushPendingAutoGain(now, options);
}

function applyPerformanceMode(modeId, options = {}) {
  const normalized = normalizePerformanceMode(modeId);
  const settings = getPerformanceModeSettings(normalized);
  const animationSettings = normalizeAtomAnimationSettings(settings?.atomAnimation);
  const config = Object.assign({ persist: true, updateControl: true }, options);
  const now = typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now();
  const changed = performanceModeState.id !== normalized
    || performanceModeState.settings !== settings;
  if (changed) {
    flushManualApcGains(now, { force: true });
    flushPendingAutoGain(now, { force: true });
    performanceModeState.pendingManualGain = null;
    performanceModeState.pendingAutoGain = null;
    performanceModeState.autoAccumulatedMs = 0;
  }
  performanceModeState.id = normalized;
  performanceModeState.settings = settings;
  performanceModeState.atomAnimation = animationSettings;
  performanceModeState.lastManualFlush = now;
  performanceModeState.lastAutoFlush = now;
  if (typeof document !== 'undefined' && document.body) {
    document.body.setAttribute('data-performance-mode', normalized);
  }
  syncAtomVisualForPerformanceMode(normalized);
  if (elements && elements.starfield && (changed || starfieldInitializedForMode !== normalized)) {
    initStarfield(normalized);
  }
  if (changed && gameLoopControl.isActive) {
    restartGameLoop({ immediate: true });
  }
  if (config.updateControl && elements.performanceModeSelect) {
    if (elements.performanceModeSelect.value !== normalized) {
      elements.performanceModeSelect.value = normalized;
    }
  }
  updatePerformanceModeNote(normalized);
  if (config.persist) {
    writeStoredPerformanceMode(normalized);
  }
}

function initPerformanceModeOption() {
  const stored = readStoredPerformanceMode();
  const initial = stored ?? performanceModeState.id ?? PERFORMANCE_MODE_DEFAULT_ID;
  applyPerformanceMode(initial, { persist: false, updateControl: true });
  if (!elements.performanceModeSelect) {
    return;
  }
  elements.performanceModeSelect.addEventListener('change', () => {
    const selected = elements.performanceModeSelect.value;
    applyPerformanceMode(selected, { persist: true, updateControl: false });
  });
}

function subscribePerformanceModeLanguageUpdates() {
  const handler = () => {
    updatePerformanceModeNote(performanceModeState.id);
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

const PERIODIC_ELEMENT_I18N_BASE = 'scripts.periodic.elements';

function getPeriodicElementTranslationBase(definition) {
  const id = typeof definition?.id === 'string' ? definition.id.trim() : '';
  if (!id) {
    return '';
  }
  return `${PERIODIC_ELEMENT_I18N_BASE}.${id}`;
}

function translatePeriodicElementField(definition, field, fallback) {
  if (!field) {
    return fallback ?? '';
  }
  const base = getPeriodicElementTranslationBase(definition);
  if (!base) {
    return fallback ?? '';
  }
  const translated = translateOrDefault(`${base}.${field}`, fallback ?? '');
  if (typeof translated === 'string' && translated.trim()) {
    return translated;
  }
  return fallback ?? '';
}

function getPeriodicElementDisplay(definition) {
  if (!definition || typeof definition !== 'object') {
    return { symbol: '', name: '' };
  }
  const fallbackSymbol = typeof definition.symbol === 'string' ? definition.symbol : '';
  const fallbackName = typeof definition.name === 'string' ? definition.name : '';
  const symbol = translatePeriodicElementField(definition, 'symbol', fallbackSymbol);
  const name = translatePeriodicElementField(definition, 'name', fallbackName);
  return { symbol, name };
}

function getPeriodicElementDetails(definition) {
  if (!definition || typeof definition !== 'object') {
    return null;
  }
  const base = getPeriodicElementTranslationBase(definition);
  if (!base) {
    return null;
  }
  const api = getI18nApi();
  if (api && typeof api.getResource === 'function') {
    const resource = api.getResource(`${base}.details`);
    if (resource && typeof resource === 'object') {
      return resource;
    }
  }
  return null;
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

function formatDateTimeLocalized(value, options = {}) {
  const timestamp = Number(value);
  if (!Number.isFinite(timestamp)) {
    return '';
  }
  const date = new Date(timestamp);
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    return '';
  }
  const locale = getCurrentLocale();
  const formatOptions = Object.assign({ dateStyle: 'medium', timeStyle: 'short' }, options);
  try {
    return new Intl.DateTimeFormat(locale, formatOptions).format(date);
  } catch (error) {
    return new Intl.DateTimeFormat('fr-FR', formatOptions).format(date);
  }
}

function formatByteSizeLocalized(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B';
  }
  const thresholds = [
    { unit: 'GB', size: 1024 * 1024 * 1024 },
    { unit: 'MB', size: 1024 * 1024 },
    { unit: 'KB', size: 1024 }
  ];
  for (let index = 0; index < thresholds.length; index += 1) {
    const threshold = thresholds[index];
    if (bytes >= threshold.size) {
      const valueInUnit = bytes / threshold.size;
      return `${formatNumberLocalized(valueInUnit, { maximumFractionDigits: 1 })} ${threshold.unit}`;
    }
  }
  return `${formatIntegerLocalized(bytes)} B`;
}

function getHoldemBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.atom2universHoldem;
  if (!bridge || typeof bridge !== 'object') {
    return null;
  }
  return bridge;
}

function formatHoldemOptionValue(value) {
  let normalized = value;
  if (!(normalized instanceof LayeredNumber)) {
    const numeric = Number(normalized);
    normalized = Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0;
  }

  if (typeof formatLayeredLocalized === 'function') {
    const formatted = formatLayeredLocalized(normalized, {
      mantissaDigits: 2,
      numberFormatOptions: HOLDEM_NUMBER_FORMAT
    });
    if (typeof formatted === 'string' && formatted.length > 0) {
      return formatted;
    }
  }

  if (normalized instanceof LayeredNumber) {
    return normalized.toString();
  }

  return formatNumberLocalized(normalized, HOLDEM_NUMBER_FORMAT);
}

function updateHoldemBlindOption(blind) {
  if (!elements.holdemBlindValue) {
    return;
  }
  const resolveBlindValue = source => {
    if (typeof LayeredNumber === 'function') {
      try {
        const layered = source instanceof LayeredNumber
          ? (typeof source.clone === 'function' ? source.clone() : new LayeredNumber(source))
          : new LayeredNumber(source);
        if (layered && layered.sign > 0 && !layered.isZero()) {
          if (layered.layer === 0) {
            const numeric = layered.toNumber();
            if (Number.isFinite(numeric)) {
              return Math.max(1, Math.floor(numeric));
            }
          }
          return layered;
        }
      } catch (error) {
        // Ignore invalid layered values and fall back to numeric parsing.
      }
    }
    const numeric = Number(source);
    if (Number.isFinite(numeric) && numeric > 0) {
      return Math.max(1, Math.floor(numeric));
    }
    return null;
  };

  let resolved = resolveBlindValue(blind);
  if (!resolved) {
    const bridge = getHoldemBridge();
    if (bridge && typeof bridge.getBlind === 'function') {
      try {
        resolved = resolveBlindValue(bridge.getBlind());
      } catch (error) {
        resolved = null;
      }
    }
  }

  if (resolved) {
    elements.holdemBlindValue.textContent = formatHoldemOptionValue(resolved);
  } else {
    elements.holdemBlindValue.textContent = '—';
  }
}

function handleHoldemRestartRequest() {
  const bridge = getHoldemBridge();
  if (!bridge) {
    showToast(t('scripts.app.holdemOptions.restartFailure', 'Hold’em table unavailable.'));
    return;
  }
  const action = typeof bridge.restart === 'function'
    ? bridge.restart
    : typeof bridge.wipeOpponents === 'function'
      ? bridge.wipeOpponents
      : null;
  if (!action) {
    showToast(t('scripts.app.holdemOptions.restartFailure', 'Hold’em table unavailable.'));
    return;
  }
  try {
    const result = action();
    if (result && result.success) {
      updateHoldemBlindOption(result.blind);
      const stackLabel = formatHoldemOptionValue(result.stack);
      showToast(t('scripts.app.holdemOptions.restartSuccess', { stack: stackLabel }));
      return;
    }
  } catch (error) {
    console.error('Unable to restart Hold’em table', error);
  }
  showToast(t('scripts.app.holdemOptions.restartFailure', 'Hold’em table unavailable.'));
}

function handleHoldemBlindScaling(factor) {
  const bridge = getHoldemBridge();
  if (!bridge || typeof bridge.scaleBlind !== 'function') {
    showToast(t('scripts.app.holdemOptions.blindUnavailable', 'Unable to adjust the blind right now.'));
    return;
  }
  try {
    const result = bridge.scaleBlind(factor);
    if (result && result.success) {
      updateHoldemBlindOption(result.blind);
      const blindLabel = formatHoldemOptionValue(result.blind);
      showToast(t('scripts.app.holdemOptions.blindUpdated', { blind: blindLabel }));
      return;
    }
  } catch (error) {
    console.error('Unable to adjust Hold’em blind', error);
  }
  showToast(t('scripts.app.holdemOptions.blindUnavailable', 'Unable to adjust the blind right now.'));
}

function initializeHoldemOptionsUI() {
  updateHoldemBlindOption();
  if (!holdemBlindListenerAttached && typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('holdem:blindChange', event => {
      const detail = event && event.detail ? event.detail.blind : undefined;
      updateHoldemBlindOption(detail);
    });
    window.addEventListener('holdem:gameRestart', event => {
      const detail = event && event.detail ? event.detail.blind : undefined;
      updateHoldemBlindOption(detail);
    });
    holdemBlindListenerAttached = true;
  }
}

function subscribeHoldemOptionsLanguageUpdates() {
  const handler = () => {
    updateHoldemBlindOption();
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

function freezeFeatureUnlockDefinition(definition) {
  if (!definition || typeof definition !== 'object') {
    return Object.freeze({ type: 'always' });
  }
  const normalized = { ...definition };
  if (Array.isArray(normalized.requires)) {
    normalized.requires = Object.freeze([...normalized.requires]);
  }
  return Object.freeze(normalized);
}

function createDefaultFeatureUnlockDefinitions() {
  const defaults = new Map();
  [
    'arcade.hub',
    'arcade.particules',
    METAL_FEATURE_ID,
    'arcade.photon',
    'arcade.objectx',
    'arcade.balance',
    'arcade.math',
    'arcade.sudoku',
    'arcade.demineur',
    'arcade.solitaire',
    'arcade.holdem',
    'arcade.dice',
    'arcade.blackjack',
    'arcade.echecs',
    'arcade.starBridges',
    'arcade.pipeTap',
    'arcade.twins',
    'arcade.gameOfLife'
  ].forEach(id => {
    defaults.set(id, freezeFeatureUnlockDefinition({ type: 'always' }));
  });
  defaults.set('system.gacha', freezeFeatureUnlockDefinition({ type: 'page', pageId: 'gacha' }));
  defaults.set('system.tableau', freezeFeatureUnlockDefinition({ type: 'page', pageId: 'tableau' }));
  defaults.set('system.fusion', freezeFeatureUnlockDefinition({ type: 'page', pageId: 'fusion' }));
  defaults.set('system.musique', freezeFeatureUnlockDefinition({ type: 'always' }));
  return defaults;
}

function normalizeFeatureUnlockCondition(featureId, rawEntry) {
  if (rawEntry == null) {
    return null;
  }
  if (typeof rawEntry === 'number' || typeof rawEntry === 'string') {
    return freezeFeatureUnlockDefinition({
      type: 'lifetimeAtoms',
      amount: toLayeredNumber(rawEntry, 0)
    });
  }
  if (typeof rawEntry !== 'object') {
    return null;
  }
  const typeCandidate = rawEntry.type ?? rawEntry.kind ?? rawEntry.mode;
  let type = typeof typeCandidate === 'string' ? typeCandidate.trim().toLowerCase() : '';
  if (!type) {
    if (rawEntry.amount != null || rawEntry.value != null || rawEntry.threshold != null) {
      type = 'lifetimeatoms';
    } else if (rawEntry.trophyId || rawEntry.trophy) {
      type = 'trophy';
    } else if (rawEntry.pageId || rawEntry.page) {
      type = 'page';
    } else if (rawEntry.requires || rawEntry.require || rawEntry.dependencies || rawEntry.dependency) {
      type = 'feature';
    } else if (rawEntry === true) {
      type = 'always';
    }
  }
  switch (type) {
    case 'always':
    case 'true':
    case 'unlocked':
      return freezeFeatureUnlockDefinition({ type: 'always' });
    case 'lifetimeatoms':
    case 'atoms':
    case 'lifetime':
    case 'threshold': {
      const amountSource =
        rawEntry.amount ?? rawEntry.value ?? rawEntry.threshold ?? rawEntry.required ?? rawEntry.target ?? 0;
      return freezeFeatureUnlockDefinition({
        type: 'lifetimeAtoms',
        amount: toLayeredNumber(amountSource, 0)
      });
    }
    case 'feature':
    case 'requires':
    case 'dependency':
    case 'dependencies': {
      const requiresSource =
        rawEntry.requires ?? rawEntry.require ?? rawEntry.dependencies ?? rawEntry.dependency ?? [];
      const requiresList = Array.isArray(requiresSource) ? requiresSource : [requiresSource];
      const normalizedRequires = requiresList
        .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
        .filter(Boolean);
      return freezeFeatureUnlockDefinition({ type: 'feature', requires: normalizedRequires });
    }
    case 'trophy':
    case 'achievement': {
      const trophyIdCandidate = rawEntry.trophyId ?? rawEntry.trophy ?? rawEntry.id ?? rawEntry.target;
      const trophyId = typeof trophyIdCandidate === 'string' ? trophyIdCandidate.trim() : '';
      if (!trophyId) {
        return null;
      }
      return freezeFeatureUnlockDefinition({ type: 'trophy', trophyId });
    }
    case 'page':
    case 'pageunlock':
    case 'pagestate': {
      const pageCandidate = rawEntry.pageId ?? rawEntry.page ?? rawEntry.id ?? rawEntry.target;
      const pageId = typeof pageCandidate === 'string' ? pageCandidate.trim() : '';
      if (!pageId) {
        return null;
      }
      return freezeFeatureUnlockDefinition({ type: 'page', pageId });
    }
    case 'flag':
    case 'featureflag':
    case 'flagunlock':
    case 'flagged': {
      const flagCandidate = rawEntry.flagId ?? rawEntry.flag ?? rawEntry.id ?? rawEntry.target ?? featureId;
      const flagId = typeof flagCandidate === 'string' ? flagCandidate.trim() : '';
      if (!flagId) {
        return null;
      }
      return freezeFeatureUnlockDefinition({ type: 'flag', flagId });
    }
    default:
      return null;
  }
}

function buildFeatureUnlockDefinitions(rawConfig) {
  const defaults = createDefaultFeatureUnlockDefinitions();
  if (!rawConfig || typeof rawConfig !== 'object') {
    return defaults;
  }
  const result = new Map(defaults);
  const addEntry = (featureId, entry) => {
    const id = typeof featureId === 'string' ? featureId.trim() : '';
    if (!id) {
      return;
    }
    const normalized = normalizeFeatureUnlockCondition(id, entry);
    if (normalized) {
      result.set(id, normalized);
    }
  };
  const processGroup = (group, prefix) => {
    if (!group || typeof group !== 'object') {
      return;
    }
    Object.entries(group).forEach(([key, value]) => {
      const keyId = typeof key === 'string' ? key.trim() : '';
      if (!keyId) {
        return;
      }
      const featureId = prefix ? `${prefix}.${keyId}` : keyId;
      addEntry(featureId, value);
    });
  };
  if (Array.isArray(rawConfig.entries)) {
    rawConfig.entries.forEach(entry => {
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const id = typeof entry.id === 'string' ? entry.id.trim() : '';
      if (!id) {
        return;
      }
      addEntry(id, entry);
    });
  }
  processGroup(rawConfig.arcade, 'arcade');
  processGroup(rawConfig.systems, 'system');
  if (rawConfig.features) {
    processGroup(rawConfig.features, '');
  }
  return result;
}

function getFeatureUnlockDefinition(featureId) {
  if (typeof featureId !== 'string' || !featureId.trim()) {
    return null;
  }
  return FEATURE_UNLOCK_DEFINITIONS.get(featureId.trim()) || null;
}

function normalizeFeatureUnlockFlags(raw) {
  const flags = new Set();
  if (raw == null) {
    return flags;
  }
  if (Array.isArray(raw)) {
    raw.forEach(entry => {
      const id = typeof entry === 'string' ? entry.trim() : '';
      if (id) {
        flags.add(id);
      }
    });
    return flags;
  }
  if (typeof raw === 'string') {
    const id = raw.trim();
    if (id) {
      flags.add(id);
    }
    return flags;
  }
  if (typeof raw === 'object') {
    Object.entries(raw).forEach(([key, value]) => {
      const id = typeof key === 'string' ? key.trim() : '';
      if (!id) {
        return;
      }
      if (
        value === true
        || value === 'true'
        || value === 1
        || (typeof value === 'string' && value.trim().toLowerCase() === 'true')
      ) {
        flags.add(id);
      }
    });
  }
  return flags;
}

function ensureFeatureUnlockFlagSet() {
  if (gameState.featureUnlockFlags instanceof Set) {
    return gameState.featureUnlockFlags;
  }
  const raw = gameState.featureUnlockFlags;
  const flags = normalizeFeatureUnlockFlags(raw);
  gameState.featureUnlockFlags = flags;
  return flags;
}

function hasFeatureUnlockFlag(flagId) {
  const id = typeof flagId === 'string' ? flagId.trim() : '';
  if (!id) {
    return false;
  }
  return ensureFeatureUnlockFlagSet().has(id);
}

function setFeatureUnlockFlag(flagId) {
  const id = typeof flagId === 'string' ? flagId.trim() : '';
  if (!id) {
    return false;
  }
  const flags = ensureFeatureUnlockFlagSet();
  if (flags.has(id)) {
    return false;
  }
  flags.add(id);
  return true;
}

function applyDerivedFeatureUnlockFlags() {
  const flags = ensureFeatureUnlockFlagSet();
  let changed = false;
  if (!flags.has(METAL_FEATURE_ID)) {
    const credits = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
    const hasMetauxProgress = !!(
      gameState.arcadeProgress
      && gameState.arcadeProgress.entries
      && gameState.arcadeProgress.entries.metaux
    );
    if (credits > 0 || hasMetauxProgress) {
      flags.add(METAL_FEATURE_ID);
      changed = true;
    }
  }
  return changed;
}

function evaluateFeatureUnlockCondition(definition, stack = new Set()) {
  if (!definition || typeof definition !== 'object') {
    return true;
  }
  switch (definition.type) {
    case 'always':
      return true;
    case 'lifetimeAtoms': {
      const target = definition.amount instanceof LayeredNumber
        ? definition.amount
        : toLayeredNumber(definition.amount ?? 0, 0);
      const lifetime = gameState.lifetime instanceof LayeredNumber
        ? gameState.lifetime
        : toLayeredNumber(gameState.lifetime ?? 0, 0);
      return lifetime.compare(target) >= 0;
    }
    case 'feature': {
      const requires = Array.isArray(definition.requires) ? definition.requires : [];
      if (!requires.length) {
        return true;
      }
      return requires.every(dep => isFeatureUnlocked(dep, stack));
    }
    case 'trophy':
      return !!definition.trophyId && getUnlockedTrophySet().has(definition.trophyId);
    case 'page': {
      const pageId = typeof definition.pageId === 'string' ? definition.pageId : '';
      if (!pageId) {
        return false;
      }
      const unlocks = getPageUnlockState();
      return unlocks?.[pageId] === true;
    }
    case 'flag': {
      const flagId = typeof definition.flagId === 'string' ? definition.flagId.trim() : '';
      if (!flagId) {
        return false;
      }
      return hasFeatureUnlockFlag(flagId);
    }
    default:
      return false;
  }
}

function isFeatureUnlocked(featureId, stack = new Set()) {
  const id = typeof featureId === 'string' ? featureId.trim() : '';
  if (!id) {
    return true;
  }
  if (featureUnlockCache.has(id)) {
    return featureUnlockCache.get(id);
  }
  if (stack.has(id)) {
    return false;
  }
  stack.add(id);
  const definition = getFeatureUnlockDefinition(id);
  const unlocked = definition ? evaluateFeatureUnlockCondition(definition, stack) : true;
  stack.delete(id);
  featureUnlockCache.set(id, unlocked);
  return unlocked;
}

function invalidateFeatureUnlockCache(options = {}) {
  featureUnlockCache.clear();
  if (options.resetArcadeState) {
    lastArcadeUnlockState = null;
  }
}

function isOptionsDetailUnlocked(detailId) {
  const featureId = OPTIONS_DETAIL_FEATURE_MAP[detailId];
  if (!featureId) {
    return true;
  }
  return isFeatureUnlocked(featureId);
}

function createDetailId(candidate, label, fallbackIndex = 0) {
  if (typeof candidate === 'string') {
    const trimmed = candidate.trim();
    if (trimmed) {
      return trimmed;
    }
  }
  if (typeof label === 'string') {
    const slug = label
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    if (slug) {
      return slug;
    }
  }
  return `detail-${fallbackIndex}`;
}

function getOptionsDetailMetadata(override = {}) {
  if (cachedOptionsDetailMetadata && !override.forceRefresh) {
    return cachedOptionsDetailMetadata;
  }
  const copy = override.copy ?? getOptionsWelcomeCardCopy();
  const fallbackCopy = override.fallback ?? CONFIG_OPTIONS_WELCOME_CARD;
  const localizedDetails = extractWelcomeDetails(copy);
  const fallbackDetails = extractWelcomeDetails(fallbackCopy);
  const detailMap = new Map();
  fallbackDetails.forEach(detail => {
    if (detail && detail.id) {
      detailMap.set(detail.id, detail);
    }
  });
  localizedDetails.forEach(detail => {
    if (detail && detail.id) {
      detailMap.set(detail.id, detail);
    }
  });
  const detailOrder = localizedDetails.length
    ? localizedDetails.map(detail => detail.id)
    : fallbackDetails.map(detail => detail.id);
  cachedOptionsDetailMetadata = { detailMap, detailOrder };
  return cachedOptionsDetailMetadata;
}

function computeRenderableOptionDetailIds(detailOrder, detailMap) {
  const order = Array.isArray(detailOrder) ? detailOrder : [];
  const map = detailMap instanceof Map ? detailMap : new Map();
  const unlocked = [];
  const seen = new Set();
  order.forEach(id => {
    if (!id || seen.has(id) || !map.has(id)) {
      return;
    }
    if (isOptionsDetailUnlocked(id)) {
      unlocked.push(id);
    }
    seen.add(id);
  });
  Object.keys(OPTIONS_DETAIL_FEATURE_MAP).forEach(id => {
    if (seen.has(id) || !map.has(id)) {
      return;
    }
    if (isOptionsDetailUnlocked(id)) {
      unlocked.push(id);
    }
    seen.add(id);
  });
  return unlocked;
}

function getTrophyDisplayName(trophyId) {
  if (typeof trophyId !== 'string' || !trophyId.trim()) {
    return '';
  }
  const def = TROPHY_MAP.get(trophyId.trim());
  if (!def) {
    return trophyId.trim();
  }
  const texts = getTrophyDisplayTexts(def);
  if (texts.name && typeof texts.name === 'string' && texts.name.trim()) {
    return texts.name.trim();
  }
  if (typeof def.name === 'string' && def.name.trim()) {
    return def.name.trim();
  }
  return def.id || trophyId.trim();
}

function getFeatureLockedReason(featureId, visited = new Set()) {
  const id = typeof featureId === 'string' ? featureId.trim() : '';
  if (!id || visited.has(id)) {
    return '';
  }
  visited.add(id);
  const definition = getFeatureUnlockDefinition(id);
  if (!definition) {
    return '';
  }
  if (definition.type === 'lifetimeAtoms') {
    const amount = definition.amount instanceof LayeredNumber
      ? definition.amount
      : toLayeredNumber(definition.amount ?? 0, 0);
    const amountText = formatLayeredLocalized(amount, { mantissaDigits: 1 });
    return translateOrDefault(
      'index.sections.arcadeHub.locked.requiresAtoms',
      `Collectez ${amountText} atomes pour débloquer l’arcade.`,
      { amount: amountText }
    );
  }
  if (definition.type === 'trophy') {
    const trophyName = getTrophyDisplayName(definition.trophyId);
    return translateOrDefault(
      'index.sections.arcadeHub.locked.requiresTrophy',
      `Débloquez le trophée « ${trophyName} » pour accéder à ce mini-jeu.`,
      { trophy: trophyName }
    );
  }
  if (definition.type === 'flag' && definition.flagId === METAL_FEATURE_ID) {
    return translateOrDefault(
      'index.sections.arcadeHub.locked.requiresMach3',
      'Obtenez un ticket Mach3 pour débloquer ce mini-jeu.'
    );
  }
  if (definition.type === 'feature') {
    const requires = Array.isArray(definition.requires) ? definition.requires : [];
    for (const dependency of requires) {
      if (!isFeatureUnlocked(dependency)) {
        const nested = getFeatureLockedReason(dependency, visited);
        if (nested) {
          return nested;
        }
      }
    }
  }
  return translateOrDefault(
    'index.sections.arcadeHub.locked.default',
    'Débloquez ce mini-jeu pour y accéder.'
  );
}

function readStoredArcadeHubCardStates() {
  try {
    const raw = globalThis.localStorage?.getItem(ARCADE_HUB_CARD_STATE_STORAGE_KEY);
    if (typeof raw === 'string' && raw.trim()) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') {
        const normalized = {};
        Object.keys(parsed).forEach(key => {
          if (typeof key === 'string' && key.trim()) {
            normalized[key.trim()] = !!parsed[key];
          }
        });
        return normalized;
      }
    }
  } catch (error) {
    console.warn('Unable to read arcade hub card states', error);
  }
  return {};
}

function writeStoredArcadeHubCardStates(map) {
  const normalized = {};
  if (map && typeof map === 'object') {
    Object.keys(map).forEach(key => {
      if (typeof key === 'string') {
        const trimmed = key.trim();
        if (trimmed) {
          normalized[trimmed] = !!map[key];
        }
      }
    });
  }
  arcadeHubCardStateCache = normalized;
  try {
    globalThis.localStorage?.setItem(ARCADE_HUB_CARD_STATE_STORAGE_KEY, JSON.stringify(normalized));
  } catch (error) {
    console.warn('Unable to persist arcade hub card states', error);
  }
}

function getArcadeHubCardStateMap() {
  if (!arcadeHubCardStateCache) {
    arcadeHubCardStateCache = readStoredArcadeHubCardStates();
  }
  return arcadeHubCardStateCache;
}

function getArcadeHubCardId(card) {
  if (!card || !card.dataset) {
    return '';
  }
  if (typeof card.dataset.cardId === 'string' && card.dataset.cardId.trim()) {
    return card.dataset.cardId.trim();
  }
  if (typeof card.dataset.pageTarget === 'string' && card.dataset.pageTarget.trim()) {
    return card.dataset.pageTarget.trim();
  }
  return '';
}

function updateArcadeHubCardToggleLabel(toggleButton, collapsed) {
  if (!toggleButton) {
    return;
  }
  const labelKey = collapsed ? ARCADE_HUB_CARD_EXPAND_LABEL_KEY : ARCADE_HUB_CARD_COLLAPSE_LABEL_KEY;
  const fallback = collapsed ? 'Afficher la description' : 'Masquer la description';
  const label = translateOrDefault(labelKey, fallback);
  toggleButton.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
  toggleButton.setAttribute('aria-label', label);
  toggleButton.setAttribute('title', label);
  toggleButton.dataset.i18n = `aria-label:${labelKey};title:${labelKey}`;
  const hiddenLabel = toggleButton.querySelector('[data-role="arcade-hub-card-toggle-label"]');
  if (hiddenLabel) {
    hiddenLabel.textContent = label;
    hiddenLabel.dataset.i18n = labelKey;
  }
}

function applyArcadeHubCardCollapsedState(card, collapsed, options = {}) {
  if (!card) {
    return;
  }
  const { persist = true } = options;
  const normalized = !!collapsed;
  card.classList.toggle('arcade-hub-card--collapsed', normalized);
  card.setAttribute('aria-expanded', normalized ? 'false' : 'true');
  const description = card.querySelector('.arcade-hub-card__description');
  if (description) {
    description.hidden = normalized;
    description.setAttribute('aria-hidden', normalized ? 'true' : 'false');
  }
  const toggleButton = card.querySelector('.arcade-hub-card__toggle');
  if (toggleButton) {
    updateArcadeHubCardToggleLabel(toggleButton, normalized);
  }
  if (persist) {
    const cardId = getArcadeHubCardId(card);
    if (cardId) {
      const map = Object.assign({}, getArcadeHubCardStateMap());
      if (normalized) {
        map[cardId] = true;
      } else {
        delete map[cardId];
      }
      writeStoredArcadeHubCardStates(map);
    }
  }
}

function initializeArcadeHubCard(card) {
  if (!card) {
    return;
  }
  card.setAttribute('aria-expanded', 'true');
  const cardId = getArcadeHubCardId(card);
  const description = card.querySelector('.arcade-hub-card__description');
  if (description) {
    if (!description.id && cardId) {
      description.id = `arcadeCardDescription-${cardId}`;
    }
    if (description.id) {
      card.setAttribute('aria-describedby', description.id);
    }
    description.setAttribute('aria-hidden', 'false');
  }
  const toggleButton = card.querySelector('.arcade-hub-card__toggle');
  if (toggleButton) {
    toggleButton.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      const collapsed = card.classList.contains('arcade-hub-card--collapsed');
      applyArcadeHubCardCollapsedState(card, !collapsed);
    });
    toggleButton.addEventListener('keydown', event => {
      const key = event.key;
      if (key === ' ' || key === 'Enter') {
        event.preventDefault();
        event.stopPropagation();
        const collapsed = card.classList.contains('arcade-hub-card--collapsed');
        applyArcadeHubCardCollapsedState(card, !collapsed);
      }
    });
  }
  const stateMap = getArcadeHubCardStateMap();
  const initialCollapsed = cardId ? stateMap?.[cardId] === true : false;
  applyArcadeHubCardCollapsedState(card, initialCollapsed, { persist: false });
  card.addEventListener('pointerdown', event => {
    handleArcadeHubCardPointerDown(event, card);
  });
}

function isArcadeHubCardLocked(card) {
  if (!card) {
    return true;
  }
  return card.classList.contains('arcade-hub-card--locked');
}

function activateArcadeHubCard(card) {
  if (!card) {
    return;
  }
  if (isArcadeHubCardLocked(card)) {
    return;
  }
  const target = card.dataset?.pageTarget;
  if (!target || !isPageUnlocked(target)) {
    return;
  }
  if (target === 'wave') {
    ensureWaveGame();
  }
  if (target === 'quantum2048') {
    ensureQuantum2048Game();
  }
  if (target === 'sokoban') {
    ensureSokobanGame();
  }
  if (target === 'taquin') {
    ensureTaquinGame();
  }
  if (target === 'link') {
    ensureLinkGame();
  }
  if (target === 'lightsOut') {
    ensureLightsOutGame();
  }
  if (target === 'gameOfLife') {
    ensureGameOfLifeGame();
  }
  if (target === 'motocross') {
    ensureMotocrossGame();
  }
  if (target === 'twins') {
    ensureTwinsGame();
  }
  if (target === 'escape') {
    ensureEscapeGame();
  }
  showPage(target);
}

function getArcadeCardLockedMessage(pageId) {
  const featureId = PAGE_FEATURE_MAP[pageId];
  if (!featureId) {
    return translateOrDefault(
      'index.sections.arcadeHub.locked.default',
      'Débloquez ce mini-jeu pour y accéder.'
    );
  }
  if (isFeatureUnlocked(featureId)) {
    return '';
  }
  const reason = getFeatureLockedReason(featureId);
  return reason
    || translateOrDefault(
      'index.sections.arcadeHub.locked.default',
      'Débloquez ce mini-jeu pour y accéder.'
    );
}

function updateArcadeHubLocks() {
  if (!elements || !elements.arcadeHubCards?.length) {
    return;
  }
  elements.arcadeHubCards.forEach(card => {
    if (!card) {
      return;
    }
    const target = card.dataset?.pageTarget;
    if (!target) {
      return;
    }
    const featureId = PAGE_FEATURE_MAP[target];
    const unlocked = featureId ? isFeatureUnlocked(featureId) : true;
    const originalLabel = card.dataset.originalAriaLabel || card.getAttribute('aria-label') || '';
    if (!card.dataset.originalAriaLabel && originalLabel) {
      card.dataset.originalAriaLabel = originalLabel;
    }
    if (unlocked) {
      delete card.dataset.arcadeLocked;
      card.classList.remove('arcade-hub-card--locked');
      card.setAttribute('aria-disabled', 'false');
      card.setAttribute('tabindex', '0');
      card.removeAttribute('title');
      if (originalLabel) {
        card.setAttribute('aria-label', originalLabel);
      }
      return;
    }
    const hint = getArcadeCardLockedMessage(target);
    card.dataset.arcadeLocked = 'true';
    card.classList.add('arcade-hub-card--locked');
    card.setAttribute('aria-disabled', 'true');
    card.setAttribute('tabindex', '-1');
    if (hint) {
      card.title = hint;
      const combined = originalLabel ? `${originalLabel} — ${hint}` : hint;
      card.setAttribute('aria-label', combined);
    } else if (originalLabel) {
      card.removeAttribute('title');
      card.setAttribute('aria-label', originalLabel);
    } else {
      card.removeAttribute('title');
      card.removeAttribute('aria-label');
    }
  });
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

function cloneDefaultSudokuRewardLevels() {
  const defaultLevels = DEFAULT_SUDOKU_COMPLETION_REWARD.levels || {};
  return Object.keys(defaultLevels).reduce((acc, levelId) => {
    const level = defaultLevels[levelId] || {};
    acc[levelId] = {
      bonusSeconds: Number(level.bonusSeconds) || 0,
      multiplier: Number(level.multiplier) || 0,
      validSeconds: Number(level.validSeconds || level.bonusSeconds) || 0
    };
    return acc;
  }, {});
}

function normalizeSudokuRewardLevel(source, fallback) {
  const base = {
    bonusSeconds: Number(fallback?.bonusSeconds) || 0,
    multiplier: Number(fallback?.multiplier) || 0,
    validSeconds: Number(fallback?.validSeconds || fallback?.bonusSeconds) || 0
  };
  const data = source && typeof source === 'object' ? source : {};

  const bonusSeconds = readPositiveNumber([
    data.offlineBonusSeconds,
    data.bonusSeconds,
    data.durationSeconds,
    data.duration,
    data.secondsBonus
  ]);
  if (bonusSeconds) {
    base.bonusSeconds = bonusSeconds;
  } else {
    const bonusMinutes = readPositiveNumber([
      data.offlineBonusMinutes,
      data.bonusMinutes,
      data.durationMinutes
    ], value => value * 60);
    if (bonusMinutes) {
      base.bonusSeconds = bonusMinutes;
    } else {
      const bonusHours = readPositiveNumber([
        data.offlineBonusHours,
        data.bonusHours,
        data.durationHours
      ], value => value * 60 * 60);
      if (bonusHours) {
        base.bonusSeconds = bonusHours;
      }
    }
  }

  const multiplier = readPositiveNumber([
    data.offlineMultiplier,
    data.multiplier,
    data.value
  ]);
  if (multiplier) {
    base.multiplier = multiplier;
  }

  const validSeconds = readPositiveNumber([
    data.validSeconds,
    data.validDurationSeconds,
    data.validitySeconds,
    data.validSecondsRemaining
  ]);
  if (validSeconds) {
    base.validSeconds = validSeconds;
  } else {
    const validMinutes = readPositiveNumber([
      data.validMinutes,
      data.validDurationMinutes,
      data.validityMinutes
    ], value => value * 60);
    if (validMinutes) {
      base.validSeconds = validMinutes;
    } else {
      const validHours = readPositiveNumber([
        data.validHours,
        data.validDurationHours,
        data.validityHours
      ], value => value * 60 * 60);
      if (validHours) {
        base.validSeconds = validHours;
      }
    }
  }

  if (!Number.isFinite(base.validSeconds) || base.validSeconds <= 0) {
    base.validSeconds = base.bonusSeconds;
  }

  return base;
}

function normalizeSudokuRewardSettings(raw) {
  const config = {
    enabled: DEFAULT_SUDOKU_COMPLETION_REWARD.enabled !== false,
    levels: cloneDefaultSudokuRewardLevels()
  };

  if (raw === false) {
    config.enabled = false;
    return config;
  }

  const source = raw && typeof raw === 'object' ? raw : {};
  if (source.enabled === false) {
    config.enabled = false;
  }

  const levelSource = (() => {
    if (source.levels && typeof source.levels === 'object') {
      return source.levels;
    }
    if (source.offlineBonus && typeof source.offlineBonus === 'object') {
      return source.offlineBonus;
    }
    if (
      source.speedCompletion
      && typeof source.speedCompletion === 'object'
    ) {
      return source.speedCompletion;
    }
    if (Object.keys(source).some(key => typeof source[key] === 'object')) {
      return source;
    }
    return null;
  })();

  const fallbackLevel = config.levels.moyen
    || Object.values(config.levels)[0]
    || { bonusSeconds: 0, multiplier: 0, validSeconds: 0 };

  if (levelSource && typeof levelSource === 'object') {
    Object.entries(levelSource).forEach(([levelId, levelConfig]) => {
      const normalized = normalizeSudokuRewardLevel(levelConfig, config.levels[levelId] || fallbackLevel);
      if (normalized.bonusSeconds > 0 && normalized.multiplier > 0) {
        config.levels[levelId] = normalized;
      }
    });
  }

  const hasValidLevel = Object.values(config.levels).some(level => (
    Number.isFinite(level.bonusSeconds)
      && level.bonusSeconds > 0
      && Number.isFinite(level.multiplier)
      && level.multiplier > 0
      && Number.isFinite(level.validSeconds)
      && level.validSeconds > 0
  ));

  if (!hasValidLevel) {
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

  const grantedAtCandidate = Number(raw.grantedAt ?? raw.timestamp ?? raw.granted_at);
  const grantedAt = Number.isFinite(grantedAtCandidate) && grantedAtCandidate > 0
    ? grantedAtCandidate
    : Date.now();

  const remainingSecondsCandidate = readPositiveNumber([
    raw.remainingSeconds,
    raw.remaining,
    raw.remainingDurationSeconds
  ]);
  const remainingSeconds = remainingSecondsCandidate
    ? Math.min(remainingSecondsCandidate, maxSeconds)
    : maxSeconds;

  const validSecondsCandidate = readPositiveNumber([
    raw.validSeconds,
    raw.validDurationSeconds,
    raw.validitySeconds
  ]);
  const validSeconds = validSecondsCandidate && validSecondsCandidate > 0
    ? validSecondsCandidate
    : maxSeconds;

  const expiresAtCandidate = Number(raw.expiresAt ?? raw.expiration ?? raw.expires_at);
  const expiresAt = Number.isFinite(expiresAtCandidate) && expiresAtCandidate > grantedAt
    ? expiresAtCandidate
    : grantedAt + validSeconds * 1000;

  if (remainingSeconds <= 0 || !Number.isFinite(expiresAt) || expiresAt <= grantedAt) {
    return null;
  }

  const level = typeof raw.level === 'string' && raw.level.trim()
    ? raw.level.trim()
    : typeof raw.difficulty === 'string' && raw.difficulty.trim()
      ? raw.difficulty.trim()
      : null;

  return {
    multiplier,
    maxSeconds,
    remainingSeconds,
    grantedAt,
    expiresAt,
    level
  };
}

const SUDOKU_COMPLETION_REWARD_CONFIG = normalizeSudokuRewardSettings(
  GLOBAL_CONFIG?.arcade?.sudoku?.rewards ?? null
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
    const normalized = resolveLanguageCode(lang);
    globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, normalized);
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

function createInitialApcFrenzyStats() {
  return {
    totalClicks: 0,
    best: {
      clicks: 0,
      frenziesUsed: 0
    },
    bestSingle: {
      clicks: 0
    }
  };
}

function normalizeApcFrenzyStats(raw) {
  const base = createInitialApcFrenzyStats();
  if (!raw || typeof raw !== 'object') {
    return base;
  }
  const total = Number(raw.totalClicks ?? raw.total ?? 0);
  base.totalClicks = Number.isFinite(total) ? Math.max(0, Math.floor(total)) : 0;
  const bestRaw = raw.best && typeof raw.best === 'object' ? raw.best : {};
  const bestClicks = Number(bestRaw.clicks ?? bestRaw.count ?? 0);
  const bestFrenzies = Number(bestRaw.frenziesUsed ?? bestRaw.frenzies ?? 0);
  base.best.clicks = Number.isFinite(bestClicks) ? Math.max(0, Math.floor(bestClicks)) : 0;
  base.best.frenziesUsed = Number.isFinite(bestFrenzies) ? Math.max(0, Math.floor(bestFrenzies)) : 0;
  const bestSingleRaw = raw.bestSingle && typeof raw.bestSingle === 'object' ? raw.bestSingle : {};
  const bestSingleClicks = Number(bestSingleRaw.clicks ?? bestSingleRaw.count ?? 0);
  base.bestSingle.clicks = Number.isFinite(bestSingleClicks)
    ? Math.max(0, Math.floor(bestSingleClicks))
    : 0;
  if ((!raw.bestSingle || typeof raw.bestSingle !== 'object') && base.best.frenziesUsed <= 1) {
    base.bestSingle.clicks = Math.max(base.bestSingle.clicks, base.best.clicks);
  }
  return base;
}

function ensureApcFrenzyStats(store) {
  if (!store || typeof store !== 'object') {
    return createInitialApcFrenzyStats();
  }
  if (!store.apcFrenzy || typeof store.apcFrenzy !== 'object') {
    store.apcFrenzy = createInitialApcFrenzyStats();
    return store.apcFrenzy;
  }
  store.apcFrenzy = normalizeApcFrenzyStats(store.apcFrenzy);
  return store.apcFrenzy;
}

function createInitialStats() {
  const now = Date.now();
  return {
    session: {
      atomsGained: LayeredNumber.zero(),
      apcAtoms: LayeredNumber.zero(),
      apsAtoms: LayeredNumber.zero(),
      offlineAtoms: LayeredNumber.zero(),
      manualClicks: 0,
      onlineTimeMs: 0,
      startedAt: now,
      frenzyTriggers: {
        perClick: 0,
        perSecond: 0,
        total: 0
      },
      apcFrenzy: createInitialApcFrenzyStats()
    },
    global: {
      apcAtoms: LayeredNumber.zero(),
      apsAtoms: LayeredNumber.zero(),
      offlineAtoms: LayeredNumber.zero(),
      manualClicks: 0,
      playTimeMs: 0,
      startedAt: null,
      frenzyTriggers: {
        perClick: 0,
        perSecond: 0,
        total: 0
      },
      apcFrenzy: createInitialApcFrenzyStats()
    }
  };
}

function getLayeredStat(store, key) {
  if (!store || typeof store !== 'object') {
    return LayeredNumber.zero();
  }
  const current = store[key];
  if (current instanceof LayeredNumber) {
    return current;
  }
  if (current && typeof current === 'object') {
    try {
      const normalized = LayeredNumber.fromJSON(current);
      store[key] = normalized;
      return normalized;
    } catch (err) {
      // Ignore malformed values and fall through to zero
    }
  }
  if (current != null) {
    const numeric = Number(current);
    if (Number.isFinite(numeric) && numeric !== 0) {
      const normalized = new LayeredNumber(numeric);
      store[key] = normalized;
      return normalized;
    }
  }
  const zero = LayeredNumber.zero();
  store[key] = zero;
  return zero;
}

function incrementLayeredStat(store, key, amount) {
  if (!store || typeof store !== 'object') {
    return;
  }
  const current = getLayeredStat(store, key);
  store[key] = current.add(amount);
}

function normalizeFrenzyStats(raw) {
  if (!raw || typeof raw !== 'object') {
    return { perClick: 0, perSecond: 0, total: 0 };
  }
  const perClick = Number(raw.perClick ?? raw.click ?? 0);
  const perSecond = Number(raw.perSecond ?? raw.auto ?? raw.aps ?? 0);
  const totalRaw = raw.total != null ? Number(raw.total) : perClick + perSecond;
  return {
    perClick: Number.isFinite(perClick) && perClick > 0 ? Math.floor(perClick) : 0,
    perSecond: Number.isFinite(perSecond) && perSecond > 0 ? Math.floor(perSecond) : 0,
    total: Number.isFinite(totalRaw) && totalRaw > 0 ? Math.floor(totalRaw) : 0
  };
}

function createDefaultApsCritState() {
  return { effects: [] };
}

function normalizeApsCritEffect(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const multiplierAdd = Number(raw.multiplierAdd ?? raw.add ?? raw.multiplier ?? raw.value ?? 0);
  const remainingSeconds = Number(
    raw.remainingSeconds ?? raw.seconds ?? raw.time ?? raw.duration ?? raw.chrono ?? 0
  );
  if (!Number.isFinite(multiplierAdd) || !Number.isFinite(remainingSeconds)) {
    return null;
  }
  if (multiplierAdd <= 0 || remainingSeconds <= 0) {
    return null;
  }
  return {
    multiplierAdd: Math.max(0, multiplierAdd),
    remainingSeconds: Math.max(0, remainingSeconds)
  };
}

function normalizeApsCritState(raw) {
  const state = createDefaultApsCritState();
  if (!raw || typeof raw !== 'object') {
    return state;
  }
  if (Array.isArray(raw.effects)) {
    state.effects = raw.effects
      .map(entry => normalizeApsCritEffect(entry))
      .filter(effect => effect != null);
    if (state.effects.length) {
      return state;
    }
  }
  const chronoValue = Number(
    raw.chronoSeconds ?? raw.chrono ?? raw.time ?? raw.seconds ?? raw.chronoSecs ?? 0
  );
  const multiplierValue = Number(raw.multiplier ?? raw.multi ?? raw.factor ?? 0);
  if (Number.isFinite(chronoValue) && chronoValue > 0 && Number.isFinite(multiplierValue) && multiplierValue > 1) {
    state.effects = [{
      multiplierAdd: Math.max(0, multiplierValue - 1),
      remainingSeconds: Math.max(0, chronoValue)
    }];
  }
  return state;
}

function createEmptyProductionEntry() {
  const rarityMultipliers = new Map();
  RARITY_IDS.forEach(id => {
    rarityMultipliers.set(id, 1);
  });
  return {
    base: LayeredNumber.zero(),
    totalAddition: LayeredNumber.zero(),
    totalMultiplier: LayeredNumber.one(),
    additions: [],
    multipliers: [],
    total: LayeredNumber.zero(),
    sources: {
      flats: {
        baseFlat: LayeredNumber.zero(),
        shopFlat: LayeredNumber.zero(),
        elementFlat: LayeredNumber.zero(),
        fusionFlat: LayeredNumber.zero(),
        devkitFlat: LayeredNumber.zero()
      },
      multipliers: {
        trophyMultiplier: LayeredNumber.one(),
        frenzy: LayeredNumber.one(),
        apsCrit: LayeredNumber.one(),
        collectionMultiplier: LayeredNumber.one(),
        rarityMultipliers
      }
    }
  };
}

function createEmptyProductionBreakdown() {
  return {
    perClick: createEmptyProductionEntry(),
    perSecond: createEmptyProductionEntry()
  };
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
    lastDisplayedCount: 0,
    autoCollectTimeout: null,
    autoCollectDeadline: 0
  },
  perSecond: {
    token: null,
    tokenExpire: 0,
    effectUntil: 0,
    currentMultiplier: 1,
    effects: [],
    currentStacks: 0,
    isActive: false,
    autoCollectTimeout: null,
    autoCollectDeadline: 0
  },
  spawnAccumulator: 0
};

function cancelFrenzyAutoCollect(type) {
  if (!FRENZY_TYPES.includes(type)) return;
  const entry = frenzyState[type];
  if (!entry) return;
  if (entry.autoCollectTimeout != null) {
    const clearFn = typeof globalThis !== 'undefined' && typeof globalThis.clearTimeout === 'function'
      ? globalThis.clearTimeout
      : typeof clearTimeout === 'function'
        ? clearTimeout
        : null;
    if (clearFn) {
      clearFn(entry.autoCollectTimeout);
    }
  }
  entry.autoCollectTimeout = null;
  entry.autoCollectDeadline = 0;
}

function scheduleFrenzyAutoCollect(type, now = (() => {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
})()) {
  if (!FRENZY_TYPES.includes(type)) return;
  if (!isFrenzyAutoCollectActive()) {
    cancelFrenzyAutoCollect(type);
    return;
  }
  const entry = frenzyState[type];
  if (!entry || !entry.token) {
    cancelFrenzyAutoCollect(type);
    return;
  }
  cancelFrenzyAutoCollect(type);
  const delay = Math.max(0, Math.floor(Number(FRENZY_AUTO_COLLECT_DELAY_MS) || 0));
  if (delay <= 0) {
    collectFrenzy(type, now);
    return;
  }
  const setFn = typeof globalThis !== 'undefined' && typeof globalThis.setTimeout === 'function'
    ? globalThis.setTimeout
    : typeof setTimeout === 'function'
      ? setTimeout
      : null;
  if (!setFn) {
    return;
  }
  entry.autoCollectDeadline = now + delay;
  entry.autoCollectTimeout = setFn(() => {
    entry.autoCollectTimeout = null;
    entry.autoCollectDeadline = 0;
    if (!isFrenzyAutoCollectActive()) {
      return;
    }
    if (!entry.token) {
      return;
    }
    collectFrenzy(type);
  }, delay);
}

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
  cancelFrenzyAutoCollect(type);
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
  scheduleFrenzyAutoCollect(type, now);
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
const DEFAULT_STATE = {
  atoms: LayeredNumber.zero(),
  lifetime: LayeredNumber.zero(),
  perClick: BASE_PER_CLICK.clone(),
  perSecond: BASE_PER_SECOND.clone(),
  basePerClick: BASE_PER_CLICK.clone(),
  basePerSecond: BASE_PER_SECOND.clone(),
  gachaTickets: 0,
  bonusParticulesTickets: 0,
  upgrades: {},
  shopUnlocks: [],
  elements: createInitialElementCollection(),
  fusions: createInitialFusionState(),
  fusionBonuses: createInitialFusionBonuses(),
  gachaImages: createInitialGachaImageCollection(),
  gachaImageAcquisitionCounter: 0,
  collectionVideos: createInitialCollectionVideoCollection(),
  gachaBonusImages: createInitialGachaBonusImageCollection(),
  gachaBonusImageAcquisitionCounter: 0,
  pageUnlocks: createInitialPageUnlockState(),
  lastSave: Date.now(),
  theme: DEFAULT_THEME_ID,
  arcadeBrickSkin: 'original',
  stats: createInitialStats(),
  production: createEmptyProductionBreakdown(),
  productionBase: createEmptyProductionBreakdown(),
  crit: createDefaultCritState(),
  baseCrit: createDefaultCritState(),
  lastCritical: null,
  elementBonusSummary: {},
  trophies: [],
  offlineGainMultiplier: MYTHIQUE_OFFLINE_BASE,
  bigBangLevelBonus: 0,
  offlineTickets: {
    secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
    capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
    progressSeconds: 0
  },
  sudokuOfflineBonus: null,
  ticketStarAutoCollect: null,
  ticketStarAverageIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
  ticketStarUnlocked: false,
  frenzySpawnBonus: { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 },
  musicTrackId: null,
  musicVolume: DEFAULT_MUSIC_VOLUME,
  musicEnabled: DEFAULT_MUSIC_ENABLED,
  bigBangButtonVisible: false,
  apsCrit: createDefaultApsCritState(),
  featureUnlockFlags: []
};

const ARCADE_GAME_IDS = Object.freeze([
  'particules',
  'metaux',
  'wave',
  'starsWar',
  'quantum2048',
  'bigger',
  'math',
  'theLine',
  'lightsOut',
  'link',
  'starBridges',
  'pipeTap',
  'colorStack',
  'motocross',
  'hex',
  'twins',
  'sokoban',
  'taquin',
  'balance',
  'sudoku',
  'minesweeper',
  'solitaire',
  'holdem',
  'blackjack',
  'echecs'
]);

function createInitialArcadeProgress() {
  const entries = {};
  ARCADE_GAME_IDS.forEach(id => {
    entries[id] = null;
  });
  return {
    version: 1,
    entries
  };
}

const gameState = {
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
  gachaCards: createInitialGachaCardCollection(),
  gachaImages: createInitialGachaImageCollection(),
  gachaImageAcquisitionCounter: 0,
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
  bigBangLevelBonus: 0,
  offlineTickets: {
    secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
    capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
    progressSeconds: 0
  },
  sudokuOfflineBonus: null,
  ticketStarAutoCollect: null,
  ticketStarAverageIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
  ticketStarUnlocked: false,
  frenzySpawnBonus: { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 },
  musicTrackId: null,
  musicVolume: DEFAULT_MUSIC_VOLUME,
  musicEnabled: DEFAULT_MUSIC_ENABLED,
  bigBangButtonVisible: false,
  apsCrit: createDefaultApsCritState(),
  arcadeProgress: createInitialArcadeProgress(),
  featureUnlockFlags: new Set()
};

if (typeof window !== 'undefined') {
  window.atom2universGameState = gameState;
}

const gachaTicketIncreaseListeners = new Set();
let gachaTicketObserverPauseCount = 0;
let internalGachaTicketValue = sanitizeGachaTicketValue(gameState.gachaTickets);

Object.defineProperty(gameState, 'gachaTickets', {
  configurable: true,
  enumerable: true,
  get() {
    return internalGachaTicketValue;
  },
  set(value) {
    const nextValue = sanitizeGachaTicketValue(value);
    const previousValue = internalGachaTicketValue;
    internalGachaTicketValue = nextValue;
    if (nextValue > previousValue && gachaTicketObserverPauseCount <= 0) {
      notifyGachaTicketIncrease(previousValue, nextValue);
    }
  }
});

function sanitizeGachaTicketValue(rawValue) {
  const numeric = Number(rawValue);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  return Math.max(0, Math.floor(numeric));
}

function notifyGachaTicketIncrease(previousValue, currentValue) {
  if (currentValue <= previousValue) {
    return;
  }
  const delta = currentValue - previousValue;
  gachaTicketIncreaseListeners.forEach(listener => {
    try {
      listener(delta, { previous: previousValue, current: currentValue });
    } catch (error) {
      console.error('Gacha ticket increase listener failed', error);
    }
  });
}

function pauseGachaTicketObservers() {
  gachaTicketObserverPauseCount += 1;
}

function resumeGachaTicketObservers() {
  gachaTicketObserverPauseCount = Math.max(0, gachaTicketObserverPauseCount - 1);
}

function setGachaTicketsSilently(value) {
  pauseGachaTicketObservers();
  try {
    gameState.gachaTickets = value;
    return gameState.gachaTickets;
  } finally {
    resumeGachaTicketObservers();
  }
}

function onGachaTicketsIncrease(listener) {
  if (typeof listener !== 'function') {
    return () => {};
  }
  gachaTicketIncreaseListeners.add(listener);
  return () => {
    gachaTicketIncreaseListeners.delete(listener);
  };
}

applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
if (typeof setParticulesBrickSkinPreference === 'function') {
  setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
}

const initialPerformanceModeSettings = getPerformanceModeSettings(PERFORMANCE_MODE_DEFAULT_ID);

const performanceModeState = {
  id: PERFORMANCE_MODE_DEFAULT_ID,
  settings: initialPerformanceModeSettings,
  atomAnimation: normalizeAtomAnimationSettings(initialPerformanceModeSettings?.atomAnimation),
  pendingManualGain: null,
  pendingAutoGain: null,
  autoAccumulatedMs: 0,
  lastManualFlush: typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now(),
  lastAutoFlush: typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now()
};

function getAtomAnimationSettings() {
  return performanceModeState?.atomAnimation || DEFAULT_ATOM_ANIMATION_SETTINGS;
}

const gameLoopControl = {
  handle: null,
  type: null,
  isActive: false
};

function getLoopTimestamp() {
  return typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now();
}

function clearScheduledGameLoop() {
  if (gameLoopControl.handle == null) {
    return;
  }
  if (gameLoopControl.type === 'timeout') {
    const globalTarget = typeof globalThis !== 'undefined'
      ? globalThis
      : (typeof window !== 'undefined' ? window : null);
    if (globalTarget && typeof globalTarget.clearTimeout === 'function') {
      globalTarget.clearTimeout(gameLoopControl.handle);
    } else if (typeof clearTimeout === 'function') {
      clearTimeout(gameLoopControl.handle);
    }
  } else if (gameLoopControl.type === 'raf') {
    if (typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(gameLoopControl.handle);
    } else if (typeof globalThis !== 'undefined'
      && typeof globalThis.cancelAnimationFrame === 'function') {
      globalThis.cancelAnimationFrame(gameLoopControl.handle);
    }
  }
  gameLoopControl.handle = null;
  gameLoopControl.type = null;
}

function scheduleGameLoop(options = {}) {
  const config = Object.assign({ immediate: false, force: false }, options);
  if (!gameLoopControl.isActive && !config.force) {
    return;
  }
  clearScheduledGameLoop();
  const settings = performanceModeState.settings || {};
  const intervalRaw = Number(settings?.frameIntervalMs);
  const frameInterval = Number.isFinite(intervalRaw) && intervalRaw > 0 ? intervalRaw : 0;
  const nowProvider = getLoopTimestamp;
  const hasWindow = typeof window !== 'undefined';
  const hasRaf = hasWindow && typeof window.requestAnimationFrame === 'function';
  const useTimeout = frameInterval > 0 || !hasRaf;
  const delay = useTimeout && !config.immediate
    ? Math.max(frameInterval > 0 ? frameInterval : GAME_LOOP_MIN_TIMEOUT_MS, GAME_LOOP_MIN_TIMEOUT_MS)
    : 0;
  if (useTimeout) {
    const timeoutTarget = typeof globalThis !== 'undefined'
      ? globalThis
      : (hasWindow ? window : null);
    if (timeoutTarget && typeof timeoutTarget.setTimeout === 'function') {
      gameLoopControl.type = 'timeout';
      gameLoopControl.handle = timeoutTarget.setTimeout(() => {
        loop(nowProvider());
      }, delay);
      return;
    }
    gameLoopControl.type = 'timeout';
    gameLoopControl.handle = setTimeout(() => {
      loop(nowProvider());
    }, delay);
    return;
  }
  gameLoopControl.type = 'raf';
  gameLoopControl.handle = window.requestAnimationFrame(loop);
}

function startGameLoop(options = {}) {
  if (gameLoopControl.isActive) {
    restartGameLoop(options);
    return;
  }
  gameLoopControl.isActive = true;
  const now = getLoopTimestamp();
  lastUpdate = now;
  lastSaveTime = now;
  lastUIUpdate = now;
  scheduleGameLoop(Object.assign({ immediate: true, force: true }, options));
}

function restartGameLoop(options = {}) {
  if (!gameLoopControl.isActive) {
    return;
  }
  const now = getLoopTimestamp();
  lastUpdate = now;
  lastSaveTime = now;
  lastUIUpdate = now;
  clearScheduledGameLoop();
  scheduleGameLoop(Object.assign({ immediate: true, force: true }, options));
}

function ensureApsCritState() {
  if (!gameState.apsCrit || typeof gameState.apsCrit !== 'object') {
    gameState.apsCrit = createDefaultApsCritState();
    return gameState.apsCrit;
  }
  const state = gameState.apsCrit;
  if (!Array.isArray(state.effects)) {
    state.effects = [];
  }
  state.effects = state.effects
    .map(entry => normalizeApsCritEffect(entry))
    .filter(effect => effect != null);
  return state;
}

function getApsCritRemainingSeconds(state = ensureApsCritState()) {
  if (!state || !Array.isArray(state.effects) || !state.effects.length) {
    return 0;
  }
  return state.effects.reduce(
    (max, effect) => Math.max(max, Number(effect?.remainingSeconds) || 0),
    0
  );
}

function getApsCritMultiplier(state = ensureApsCritState()) {
  if (!state || !Array.isArray(state.effects) || !state.effects.length) {
    return 1;
  }
  const totalAdd = state.effects.reduce((sum, effect) => {
    const timeLeft = Number(effect?.remainingSeconds) || 0;
    if (timeLeft <= 0) {
      return sum;
    }
    const value = Number(effect?.multiplierAdd) || 0;
    if (value <= 0) {
      return sum;
    }
    return sum + value;
  }, 0);
  return totalAdd > 0 ? 1 + totalAdd : 1;
}

const DEVKIT_STATE = {
  isOpen: false,
  lastFocusedElement: null,
  cheats: {
    freeShop: false,
    freeGacha: false
  },
  bonuses: {
    autoFlat: LayeredNumber.zero()
  }
};

function isDevKitShopFree() {
  return DEVKIT_STATE.cheats.freeShop === true;
}

function isDevKitGachaFree() {
  return DEVKIT_STATE.cheats.freeGacha === true;
}

function getDevKitAutoFlatBonus() {
  return DEVKIT_STATE.bonuses.autoFlat instanceof LayeredNumber
    ? DEVKIT_STATE.bonuses.autoFlat
    : LayeredNumber.zero();
}

function setDevKitAutoFlatBonus(value) {
  DEVKIT_STATE.bonuses.autoFlat = value instanceof LayeredNumber
    ? value.clone()
    : new LayeredNumber(value || 0);
}

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
const BIG_BANG_CONFIG = GLOBAL_CONFIG?.bigBang || {};
const BIG_BANG_LEVEL_BONUS_STEP = (() => {
  const rawStep = Number(BIG_BANG_CONFIG.levelBonusStep);
  if (Number.isFinite(rawStep)) {
    return Math.max(0, Math.floor(rawStep));
  }
  return 100;
})();
const ARCADE_TROPHY_ID = 'millionAtoms';
const INFO_TROPHY_ID = 'scaleSandGrain';
const ACHIEVEMENTS_UNLOCK_TROPHY_ID = ARCADE_TROPHY_ID;
const FRENZY_AUTO_COLLECT_TROPHY_ID = 'frenzyMaster';
const LOCKABLE_PAGE_IDS = new Set(['gacha', 'tableau', 'fusion', 'info', 'collection']);

function hasOwnedGachaCards() {
  return Object.values(gameState.gachaCards || {}).some(entry => {
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  });
}

function hasOwnedGachaImages() {
  return Object.values(gameState.gachaImages || {}).some(entry => {
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  });
}

function hasOwnedGachaBonusImages() {
  return Object.values(gameState.gachaBonusImages || {}).some(entry => {
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  });
}

function getSpecialGachaCardName(cardId) {
  if (typeof cardId !== 'string' || !cardId) {
    return '';
  }
  const definition = Array.isArray(GACHA_SPECIAL_CARD_DEFINITIONS)
    ? GACHA_SPECIAL_CARD_DEFINITIONS.find(entry => entry?.id === cardId)
    : null;
  if (!definition) {
    return cardId;
  }
  const fallback = typeof definition.labelFallback === 'string' && definition.labelFallback.trim()
    ? definition.labelFallback.trim()
    : cardId;
  return translateOrDefault(definition.labelKey, fallback);
}

function getDuplicateCardCostInfo(costDefinition, quantity = 1) {
  if (!costDefinition || typeof costDefinition !== 'object') {
    return {
      required: 0,
      available: 0,
      entries: [],
      retain: 0,
      cardIds: [],
      hasEligibleCards: false
    };
  }
  const cardIds = Array.isArray(costDefinition.cardIds)
    ? costDefinition.cardIds.filter(id => typeof id === 'string' && id)
    : [];
  const retain = Math.max(0, Math.floor(Number(costDefinition.retain ?? 0)));
  const perPurchase = Math.max(0, Math.floor(Number(costDefinition.amount ?? 1)));
  const normalizedQuantity = Math.max(1, Math.floor(Number(quantity) || 0));
  const required = Math.max(0, perPurchase * normalizedQuantity);
  const entries = cardIds.map(cardId => {
    const stored = gameState.gachaCards?.[cardId];
    const total = Number.isFinite(Number(stored?.count ?? stored))
      ? Math.max(0, Math.floor(Number(stored?.count ?? stored)))
      : 0;
    const spendable = Math.max(0, total - retain);
    return { cardId, total, spendable };
  });
  const available = entries.reduce((sum, entry) => sum + entry.spendable, 0);
  const hasEligibleCards = entries.some(entry => entry.spendable > 0);
  return { required, available, entries, retain, cardIds, hasEligibleCards };
}

function hasDuplicateCardResources(costDefinition, quantity = 1) {
  const info = getDuplicateCardCostInfo(costDefinition, quantity);
  if (info.required <= 0) {
    return true;
  }
  return info.available >= info.required;
}

function spendDuplicateCardCost(costDefinition, quantity = 1) {
  const info = getDuplicateCardCostInfo(costDefinition, quantity);
  if (info.required <= 0) {
    return [];
  }
  let remaining = info.required;
  const consumed = [];
  info.entries.forEach(entry => {
    if (remaining <= 0 || entry.spendable <= 0) {
      return;
    }
    const amount = Math.min(entry.spendable, remaining);
    const cardEntry = gameState.gachaCards?.[entry.cardId];
    if (cardEntry && amount > 0) {
      const currentCount = Number.isFinite(Number(cardEntry.count))
        ? Math.max(0, Math.floor(Number(cardEntry.count)))
        : 0;
      const nextCount = Math.max(info.retain, currentCount - amount);
      cardEntry.count = nextCount;
      consumed.push({ cardId: entry.cardId, amount });
      remaining -= amount;
    }
  });
  return consumed;
}

function formatDuplicateCostText(info) {
  if (!info) {
    return '';
  }
  const required = formatIntegerLocalized(Math.max(0, Number(info.required) || 0));
  const available = formatIntegerLocalized(Math.max(0, Number(info.available) || 0));
  return translateOrDefault(
    'scripts.app.shop.rareDuplicatePrice',
    `Doublons rares : ${required}/${available}`,
    { required, available }
  );
}

function formatDuplicateSpendToast(consumedEntries) {
  if (!Array.isArray(consumedEntries)) {
    return '';
  }
  const parts = consumedEntries
    .filter(entry => entry && entry.amount > 0)
    .map(entry => {
      const label = getSpecialGachaCardName(entry.cardId) || entry.cardId || '';
      if (!label) {
        return '';
      }
      if (entry.amount > 1) {
        const amountText = formatIntegerLocalized(entry.amount);
        return `${label} ×${amountText}`;
      }
      return label;
    })
    .filter(text => text);
  return parts.join(', ');
}

function normalizeFrenzyChanceAdd(raw) {
  if (raw == null) {
    return { perClick: 0, perSecond: 0 };
  }
  if (typeof raw === 'number') {
    const numeric = Number(raw);
    const value = Number.isFinite(numeric) && numeric > 0 ? numeric : 0;
    return { perClick: value, perSecond: value };
  }
  if (typeof raw === 'object') {
    const perClick = Number(raw.perClick ?? raw.click ?? raw.apc);
    const perSecond = Number(raw.perSecond ?? raw.auto ?? raw.aps);
    return {
      perClick: Number.isFinite(perClick) && perClick > 0 ? perClick : 0,
      perSecond: Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 0
    };
  }
  return { perClick: 0, perSecond: 0 };
}

function hasOwnedGachaBonus2Images() {
  const collection = gameState.gachaBonusImages && typeof gameState.gachaBonusImages === 'object'
    ? gameState.gachaBonusImages
    : {};
  if (!Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)) {
    return false;
  }
  return GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS.some(def => {
    if (!def || !def.id) {
      return false;
    }
    const entry = collection[def.id];
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  });
}

function hasOwnedCollectionVideos() {
  return Object.values(gameState.collectionVideos || {}).some(entry => {
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  });
}

function hasUnlockedCollectionVideos() {
  if (hasOwnedCollectionVideos()) {
    persistCollectionVideoUnlockState(true);
    return true;
  }
  if (collectionVideosUnlockedCache == null) {
    collectionVideosUnlockedCache = readStoredCollectionVideosUnlocked(false);
  }
  return collectionVideosUnlockedCache === true;
}

function getPageUnlockState() {
  if (!gameState.pageUnlocks || typeof gameState.pageUnlocks !== 'object') {
    gameState.pageUnlocks = createInitialPageUnlockState();
  }
  return gameState.pageUnlocks;
}

function isPageUnlocked(pageId) {
  if (pageId === 'midi') {
    return isMusicModuleEnabled();
  }
  if (pageId === 'collection') {
    if (!isCollectionFeatureEnabled()) {
      return hasOwnedGachaCards() || hasOwnedGachaBonusImages() || hasOwnedCollectionVideos();
    }
  }
  const featureId = PAGE_FEATURE_MAP[pageId];
  if (featureId) {
    return isFeatureUnlocked(featureId);
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
  if (pageId === 'collection' && !isCollectionFeatureEnabled()) {
    return false;
  }
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
  invalidateFeatureUnlockCache();
  refreshOptionsWelcomeContent();
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

  if (!unlocks.collection) {
    if (hasOwnedGachaCards()
      || hasOwnedGachaImages()
      || hasOwnedGachaBonusImages()
      || hasOwnedCollectionVideos()) {
      changed = unlockPage('collection', { save: false, deferUI: true }) || changed;
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

const trophyCards = new Map();

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
  if (!activePage) {
    return;
  }
  if (activePage === 'midi' && !isMusicModuleEnabled()) {
    showPage('options');
    return;
  }
  if (!isPageUnlocked(activePage) && activePage !== 'game') {
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
  const trophyTexts = getTrophyDisplayTexts(def);
  showToast(t('scripts.app.trophies.unlocked', { name: trophyTexts.name }));
  recalcProduction();
  updateGoalsUI();
  updateBigBangVisibility();
  invalidateFeatureUnlockCache();
  refreshOptionsWelcomeContent();
  updateBrickSkinOption();
  updateFrenzyAutoCollectOptionVisibility();
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

let elements = {};
let musicModuleInitRequested = false;
let holdemBlindListenerAttached = false;
let frenzyAutoCollectPreference = false;
let lastFrenzyAutoCollectUnlockedState = null;
const screenWakeLockState = {
  supported: false,
  enabled: false
};
const cryptoWidgetState = {
  isWidgetEnabled: false,
  btcPriceUsd: null,
  previousBtcPriceUsd: null,
  ethPriceUsd: null,
  previousEthPriceUsd: null,
  isLoading: false,
  errorMessage: null,
  errorMessageKey: null,
  intervalId: null,
  abortController: null
};
let cryptoWidgetNumberFormat = null;
let cryptoWidgetNumberFormatLocale = null;

let pageHiddenAt = null;
let overlayFadeFallbackTimeout = null;
let startupOverlayFailsafeTimeout = null;
let startupOverlayGlobalFallbackTimeout = null;
let visibilityChangeListenerAttached = false;
let appStartAttempted = false;
let appStartCompleted = false;

function getStartupOverlayElement() {
  if (elements && elements.startupOverlay) {
    const overlayCandidate = elements.startupOverlay;
    if (typeof HTMLElement === 'undefined' || overlayCandidate instanceof HTMLElement) {
      return overlayCandidate;
    }
  }

  if (typeof document === 'undefined') {
    return null;
  }

  const overlay = document.getElementById('startupOverlay');
  if (overlay && elements && typeof elements === 'object') {
    elements.startupOverlay = overlay;
  }

  return overlay || null;
}

function resolveGlobalNumberOption(optionKey, fallback) {
  if (typeof optionKey !== 'string' || !optionKey) {
    return Number.isFinite(fallback) ? fallback : 0;
  }
  if (typeof globalThis !== 'undefined') {
    const value = globalThis[optionKey];
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }
  }
  return Number.isFinite(fallback) ? fallback : 0;
}

function getConfiguredStartupFadeDurationMs() {
  return resolveGlobalNumberOption('STARTUP_FADE_DURATION_MS', DEFAULT_STARTUP_FADE_DURATION_MS);
}

function getNormalizedStartupFadeDuration() {
  const configuredValue = getConfiguredStartupFadeDurationMs();
  return Math.max(0, Number.isFinite(configuredValue) ? configuredValue : 0);
}

function getStartupOverlaySafetyDelay() {
  const fadeDuration = getNormalizedStartupFadeDuration();
  const bufferDuration = Math.max(2000, fadeDuration);
  return fadeDuration + bufferDuration;
}

function clearGlobalStartupOverlayFallback() {
  if (startupOverlayGlobalFallbackTimeout != null) {
    clearTimeout(startupOverlayGlobalFallbackTimeout);
    startupOverlayGlobalFallbackTimeout = null;
  }
}

function armStartupOverlayFallback(options = {}) {
  const reset = options && options.reset === true;
  if (!reset && startupOverlayGlobalFallbackTimeout != null) {
    return;
  }

  if (reset) {
    clearGlobalStartupOverlayFallback();
  } else if (startupOverlayGlobalFallbackTimeout != null) {
    return;
  }

  const failsafeDelay = getStartupOverlaySafetyDelay();
  const fallbackDelay = Number.isFinite(failsafeDelay)
    ? failsafeDelay + 250
    : 4000;

  startupOverlayGlobalFallbackTimeout = setTimeout(() => {
    startupOverlayGlobalFallbackTimeout = null;
    hideStartupOverlay({ instant: true });
  }, fallbackDelay);
}

function clearStartupOverlayFailsafe() {
  if (startupOverlayFailsafeTimeout != null) {
    clearTimeout(startupOverlayFailsafeTimeout);
    startupOverlayFailsafeTimeout = null;
  }
}

function scheduleStartupOverlayFailsafe() {
  if (startupOverlayFailsafeTimeout != null) {
    return;
  }

  const failsafeDelay = getStartupOverlaySafetyDelay();

  armStartupOverlayFallback({ reset: true });

  startupOverlayFailsafeTimeout = setTimeout(() => {
    startupOverlayFailsafeTimeout = null;
    const needsForcedStart = !appStartCompleted;
    console.warn(
      needsForcedStart
        ? 'Startup overlay failsafe triggered, forcing application start'
        : 'Startup overlay failsafe triggered'
    );
    if (needsForcedStart) {
      safelyStartApp({ force: true });
    }
    hideStartupOverlay({ instant: true });
  }, failsafeDelay);
}
const RESET_DIALOG_FOCUSABLE_SELECTOR = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

const resetDialogState = {
  isOpen: false,
  previousFocus: null,
  isProcessing: false
};

const BIG_BANG_DIALOG_FOCUSABLE_SELECTOR = RESET_DIALOG_FOCUSABLE_SELECTOR;

const bigBangDialogState = {
  isOpen: false,
  previousFocus: null
};

const CONFIG_RELOAD_DELAY_MS = 500;
let configReloadTimerId = null;

const RESET_KEYWORD_ACTIONS = Object.freeze({
  DEVKIT: Object.freeze({
    toggle: toggleDevkitFeatureAvailability,
    enabledKey: 'devkitEnabled',
    enabledFallback: 'DevKit enabled. Changes applied.',
    disabledKey: 'devkitDisabled',
    disabledFallback: 'DevKit disabled. Changes applied.'
  }),
  COLLECTION: Object.freeze({
    toggle: toggleCollectionFeatureAvailability,
    enabledKey: 'collectionEnabled',
    enabledFallback: 'Collection enabled. Changes applied.',
    disabledKey: 'collectionDisabled',
    disabledFallback: 'Collection disabled. Changes applied.'
  }),
  INFO: Object.freeze({
    toggle: toggleInfoSectionsFeatureAvailability,
    enabledKey: 'infoEnabled',
    enabledFallback: 'Info sections enabled. Changes applied.',
    disabledKey: 'infoDisabled',
    disabledFallback: 'Info sections disabled. Changes applied.'
  }),
  MUSIC: Object.freeze({
    toggle: toggleMusicModuleAvailability,
    enabledKey: 'musicEnabled',
    enabledFallback: 'Music module enabled. Changes applied.',
    disabledKey: 'musicDisabled',
    disabledFallback: 'Music module disabled. Changes applied.'
  }),
  ATOM: Object.freeze({
    toggle: toggleAtomImageVariantAvailability,
    enabledKey: 'atomVariantEnabled',
    enabledFallback: 'Alternate atom visuals enabled. Changes applied.',
    disabledKey: 'atomVariantDisabled',
    disabledFallback: 'Alternate atom visuals disabled. Changes applied.'
  }),
  ESCAPE: Object.freeze({
    toggle: toggleEscapeAdvancedDifficultiesAvailability,
    enabledKey: 'escapeDifficultiesEnabled',
    enabledFallback: 'Escape advanced difficulties enabled. Changes applied.',
    disabledKey: 'escapeDifficultiesDisabled',
    disabledFallback: 'Escape advanced difficulties disabled. Changes applied.'
  })
});

function isResetSpecialKeyword(normalizedKeyword) {
  if (typeof normalizedKeyword !== 'string' || !normalizedKeyword) {
    return false;
  }
  return Object.prototype.hasOwnProperty.call(RESET_KEYWORD_ACTIONS, normalizedKeyword);
}

function translateResetString(key, fallback, params) {
  return translateOrDefault(`scripts.app.reset.${key}`, fallback, params);
}

function getResetConfirmationKeyword() {
  const fallbackKeyword = 'RESET';
  const translated = translateResetString('keyword', fallbackKeyword);
  if (typeof translated === 'string' && translated.trim()) {
    return translated.trim();
  }
  return fallbackKeyword;
}

function normalizeResetConfirmation(value) {
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim().toUpperCase();
}

function getSessionStorageSafe() {
  if (typeof globalThis === 'undefined') {
    return null;
  }
  try {
    return globalThis.sessionStorage || null;
  } catch (error) {
    return null;
  }
}

function storeReloadSaveSnapshot(serialized) {
  if (typeof serialized !== 'string' || !serialized) {
    return;
  }
  const storage = getSessionStorageSafe();
  if (!storage) {
    return;
  }
  try {
    storage.setItem(RELOAD_SAVE_STORAGE_KEY, serialized);
  } catch (error) {
    console.warn('Unable to cache reload save data', error);
  }
}

function consumeReloadSaveSnapshot() {
  const storage = getSessionStorageSafe();
  if (!storage) {
    return null;
  }
  try {
    const snapshot = storage.getItem(RELOAD_SAVE_STORAGE_KEY);
    storage.removeItem(RELOAD_SAVE_STORAGE_KEY);
    return typeof snapshot === 'string' && snapshot ? snapshot : null;
  } catch (error) {
    console.warn('Unable to consume reload save data', error);
    return null;
  }
}

function clearReloadSaveSnapshot() {
  const storage = getSessionStorageSafe();
  if (!storage) {
    return;
  }
  try {
    storage.removeItem(RELOAD_SAVE_STORAGE_KEY);
  } catch (error) {
    // Ignore cleanup failures silently to avoid blocking resets.
  }
}

function scheduleConfigReload() {
  if (typeof globalThis === 'undefined') {
    return;
  }
  const location = globalThis.location;
  if (!location || typeof location.reload !== 'function') {
    return;
  }
  if (configReloadTimerId !== null) {
    return;
  }
  const setTimer = typeof globalThis.setTimeout === 'function' ? globalThis.setTimeout : null;
  if (!setTimer) {
    try {
      location.reload();
    } catch (error) {
      if (typeof location.replace === 'function' && typeof location.href === 'string') {
        location.replace(location.href);
      }
    }
    return;
  }
  configReloadTimerId = setTimer(() => {
    configReloadTimerId = null;
    try {
      location.reload();
    } catch (error) {
      if (typeof location.replace === 'function' && typeof location.href === 'string') {
        location.replace(location.href);
      }
    }
  }, CONFIG_RELOAD_DELAY_MS);
}

function handleResetSpecialKeyword(normalizedKeyword) {
  const action = RESET_KEYWORD_ACTIONS[normalizedKeyword];
  if (!action) {
    return false;
  }
  const saved = saveGame();
  if (!saved) {
    showToast(translateResetString('saveFailed', 'Unable to save progress. Changes cancelled.'));
    return true;
  }
  const toggleFn = action.toggle;
  const nextValue = typeof toggleFn === 'function' ? toggleFn() : undefined;
  if (typeof nextValue !== 'boolean') {
    showToast(translateResetString('toggleFailed', 'Unable to update configuration.'));
    return true;
  }
  const messageKey = nextValue ? action.enabledKey : action.disabledKey;
  const fallbackMessage = nextValue ? action.enabledFallback : action.disabledFallback;
  showToast(translateResetString(messageKey, fallbackMessage));
  if (normalizedKeyword === 'MUSIC') {
    updateMusicModuleVisibility();
  } else if (normalizedKeyword === 'ATOM') {
    applyAtomVariantVisualState();
    randomizeAtomButtonImage();
  }
  return true;
}

function updateResetDialogCopy() {
  if (!elements.resetDialog) {
    return;
  }
  const keyword = getResetConfirmationKeyword();
  const descriptionFallback = `Type ${keyword} to confirm. This action cannot be undone.`;
  if (elements.resetDialogTitle) {
    elements.resetDialogTitle.textContent = translateOrDefault(
      'index.sections.options.resetDialog.title',
      'Confirm reset'
    );
  }
  if (elements.resetDialogMessage) {
    elements.resetDialogMessage.textContent = translateOrDefault(
      'index.sections.options.resetDialog.description',
      descriptionFallback,
      { keyword }
    );
  }
  if (elements.resetDialogLabel) {
    elements.resetDialogLabel.textContent = translateOrDefault(
      'index.sections.options.resetDialog.inputLabel',
      'Confirmation word'
    );
  }
  if (elements.resetDialogInput) {
    const placeholderFallback = `Type ${keyword}`;
    elements.resetDialogInput.placeholder = translateOrDefault(
      'index.sections.options.resetDialog.placeholder',
      placeholderFallback,
      { keyword }
    );
  }
  if (elements.resetDialogCancel) {
    elements.resetDialogCancel.textContent = translateOrDefault(
      'index.sections.options.resetDialog.cancel',
      'Keep my progress'
    );
  }
  if (elements.resetDialogConfirm) {
    elements.resetDialogConfirm.textContent = translateOrDefault(
      'index.sections.options.resetDialog.confirm',
      'Reset progress'
    );
  }
}

function setResetDialogError(message) {
  if (!elements.resetDialog || !elements.resetDialogError) {
    return;
  }
  if (typeof message === 'string' && message.trim()) {
    elements.resetDialogError.textContent = message.trim();
    elements.resetDialogError.hidden = false;
    elements.resetDialog.setAttribute('aria-describedby', 'resetDialogMessage resetDialogError');
  } else {
    elements.resetDialogError.textContent = '';
    elements.resetDialogError.hidden = true;
    elements.resetDialog.setAttribute('aria-describedby', 'resetDialogMessage');
  }
}

function getResetDialogFocusableElements() {
  if (!elements.resetDialog) {
    return [];
  }
  return Array.from(elements.resetDialog.querySelectorAll(RESET_DIALOG_FOCUSABLE_SELECTOR))
    .filter(node => node instanceof HTMLElement && !node.hasAttribute('disabled') && !node.hidden);
}

function handleResetDialogKeydown(event) {
  if (!resetDialogState.isOpen) {
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    closeResetDialog({ cancelled: true });
    return;
  }
  if (event.key !== 'Tab') {
    return;
  }
  const focusable = getResetDialogFocusableElements();
  if (!focusable.length) {
    event.preventDefault();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document && document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  if (event.shiftKey) {
    if (!active || active === first) {
      event.preventDefault();
      last.focus();
    }
  } else if (active === last) {
    event.preventDefault();
    first.focus();
  }
}

function openResetDialog() {
  if (!elements.resetDialog || !elements.resetDialogForm || !elements.resetDialogInput) {
    handleResetPromptFallback();
    return;
  }
  if (resetDialogState.isOpen) {
    return;
  }
  resetDialogState.isProcessing = false;
  resetDialogState.isOpen = true;
  resetDialogState.previousFocus = document && document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  updateResetDialogCopy();
  setResetDialogError();
  elements.resetDialogInput.value = '';
  elements.resetDialog.hidden = false;
  document.addEventListener('keydown', handleResetDialogKeydown);
  requestAnimationFrame(() => {
    if (elements.resetDialogInput) {
      elements.resetDialogInput.focus();
    }
  });
}

function closeResetDialog({ cancelled = false } = {}) {
  if (!resetDialogState.isOpen) {
    return;
  }
  resetDialogState.isProcessing = false;
  resetDialogState.isOpen = false;
  if (elements.resetDialog) {
    elements.resetDialog.hidden = true;
  }
  if (elements.resetDialogInput) {
    elements.resetDialogInput.value = '';
    elements.resetDialogInput.blur();
  }
  setResetDialogError();
  document.removeEventListener('keydown', handleResetDialogKeydown);
  const { previousFocus } = resetDialogState;
  resetDialogState.previousFocus = null;
  const focusTarget = previousFocus && typeof previousFocus.focus === 'function'
    ? previousFocus
    : elements.resetButton && typeof elements.resetButton.focus === 'function'
      ? elements.resetButton
      : null;
  if (focusTarget) {
    focusTarget.focus();
  }
  if (cancelled) {
    showToast(translateResetString('cancelled', 'Reset cancelled'));
  }
}

// ✅ Nouvelle version corrigée
function handleResetDialogSubmit(event) {
  event.preventDefault();

  if (resetDialogState.isProcessing) {
    return;
  }
  resetDialogState.isProcessing = true;

  try {
    const keyword = getResetConfirmationKeyword();
    const expected = normalizeResetConfirmation(keyword);
    const rawInput = elements.resetDialogInput ? elements.resetDialogInput.value : '';
    const provided = normalizeResetConfirmation(rawInput);

    const isSpecialKeyword = isResetSpecialKeyword(provided);

    // 1) Mot-clé spécial (devkit / collection / infos)
    if (handleResetSpecialKeyword(provided)) {
      closeResetDialog();
      return;
    }
    // 2) Mot de confirmation correct -> reset
    else if (!isSpecialKeyword && provided === expected) {
      closeResetDialog();
      resetGame();
      showToast(translateResetString('done', 'Progress reset'));
      scheduleConfigReload();
      return;
    }
    // 3) Mot incorrect -> erreur
    else {
      const invalidMessage = translateResetString('invalid', 'Incorrect confirmation word');
      setResetDialogError(invalidMessage);
      if (elements.resetDialogInput) {
        elements.resetDialogInput.focus();
        elements.resetDialogInput.select();
      }
      showToast(invalidMessage);
      return;
    }
  } finally {
    resetDialogState.isProcessing = false;
  }
}

function handleResetDialogCancel(event) {
  event.preventDefault();
  closeResetDialog({ cancelled: true });
}

function handleResetDialogBackdrop(event) {
  if (!elements.resetDialog || event.target !== elements.resetDialog) {
    return;
  }
  event.preventDefault();
  closeResetDialog({ cancelled: true });
}

// ✅ Nouvelle version corrigée
function handleResetPromptFallback() {
  const keyword = getResetConfirmationKeyword();
  const expected = normalizeResetConfirmation(keyword);
  const promptMessage = translateResetString(
    'prompt',
    `Full game reset. Type "${keyword}" to confirm.\nThis action cannot be undone.`,
    { keyword }
  );
  const promptFn = typeof window !== 'undefined' && typeof window.prompt === 'function'
    ? window.prompt
    : null;

  if (!promptFn) {
    showToast(translateResetString('cancelled', 'Reset cancelled'));
    return;
  }

  const response = promptFn(promptMessage);
  if (response == null) {
    showToast(translateResetString('cancelled', 'Reset cancelled'));
    return;
  }

  const provided = normalizeResetConfirmation(response);

  const isSpecialKeyword = isResetSpecialKeyword(provided);

  // 1) Mot-clé spécial
  if (handleResetSpecialKeyword(provided)) {
    return;
  }
  // 2) Mot correct -> reset
  else if (!isSpecialKeyword && provided === expected) {
    resetGame();
    showToast(translateResetString('done', 'Progress reset'));
    scheduleConfigReload();
    return;
  }
  // 3) Mot incorrect -> erreur
  else {
    showToast(translateResetString('invalid', 'Incorrect confirmation word'));
    return;
  }
}

function collectDomElements() {
  return {
    startupOverlay: getStartupOverlayElement(),
    appHeader: document.querySelector('.app-header'),
    headerBannerToggle: document.getElementById('headerBannerToggle'),
    pageContainer: document.getElementById('pageContainer'),
    brandHomeButton: document.getElementById('brandHomeButton'),
    brandPortal: document.getElementById('brandPortal'),
    navMenu: document.querySelector('.nav-menu'),
    navButtons: document.querySelectorAll('.nav-button'),
    navArcadeButton: document.getElementById('navArcadeButton'),
    navShopButton: document.querySelector('.nav-button[data-target="shop"]'),
    navGachaButton: document.querySelector('.nav-button[data-target="gacha"]'),
    navTableButton: document.querySelector('.nav-button[data-target="tableau"]'),
    navFusionButton: document.querySelector('.nav-button[data-target="fusion"]'),
    navInfoButton: document.querySelector('.nav-button[data-target="info"]'),
    navCollectionButton: document.querySelector('.nav-button[data-target="collection"]'),
    navMidiButton: document.querySelector('.nav-button[data-target="midi"]'),
    navBigBangButton: document.getElementById('navBigBangButton'),
  bigBangSummary: document.getElementById('bigBangSummary'),
  bigBangBonusInfo: document.getElementById('bigBangBonusInfo'),
  bigBangRequirement: document.getElementById('bigBangRequirement'),
  bigBangRestartButton: document.getElementById('bigBangRestartButton'),
  bigBangDialog: document.getElementById('bigBangDialog'),
  bigBangDialogTitle: document.getElementById('bigBangDialogTitle'),
  bigBangDialogMessage: document.getElementById('bigBangDialogMessage'),
  bigBangDialogConfirm: document.getElementById('bigBangDialogConfirm'),
  bigBangDialogCancel: document.getElementById('bigBangDialogCancel'),
    headerPlaybackButton: document.getElementById('headerPlaybackToggle'),
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
  statusBar: document.querySelector('.status-bar'),
  cryptoWidget: document.getElementById('cryptoWidget'),
  cryptoWidgetStatus: document.getElementById('cryptoWidgetStatus'),
  cryptoWidgetBtcValue: document.getElementById('cryptoWidgetBtcValue'),
  cryptoWidgetEthValue: document.getElementById('cryptoWidgetEthValue'),
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
  atomButtonCore: document.getElementById('atomButtonCore'),
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
  fusionModeButton: document.getElementById('fusionModeButton'),
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
  arcadeHubCards: document.querySelectorAll('.arcade-hub-card'),
  arcadeLevelValue: document.getElementById('arcadeLevelValue'),
  arcadeLivesValue: document.getElementById('arcadeLivesValue'),
  arcadeScoreValue: document.getElementById('arcadeScoreValue'),
  arcadeComboMessage: document.getElementById('arcadeComboMessage'),
  lightsOutPage: document.getElementById('lightsOut'),
  lightsOutBoard: document.getElementById('lightsOutBoard'),
  lightsOutMessage: document.getElementById('lightsOutMessage'),
  lightsOutDifficultyButtons: document.querySelectorAll('[data-lights-difficulty]'),
  lightsOutNewButton: document.getElementById('lightsOutNewButton'),
  biggerPage: document.getElementById('bigger'),
  biggerBoard: document.getElementById('biggerBoard'),
  biggerRestartButton: document.getElementById('biggerRestartButton'),
  biggerOverlay: document.getElementById('biggerOverlay'),
  biggerOverlayTitle: document.getElementById('biggerOverlayTitle'),
  biggerOverlayMessage: document.getElementById('biggerOverlayMessage'),
  biggerOverlayAction: document.getElementById('biggerOverlayAction'),
  biggerOverlayDismiss: document.getElementById('biggerOverlayDismiss'),
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
  metauxOpenButton: document.getElementById('metauxOpenButton'),
  metauxReturnButton: document.getElementById('metauxReturnButton'),
  metauxBoard: document.getElementById('metauxBoard'),
  metauxTimerLabel: document.getElementById('metauxTimerLabel'),
  metauxTimerValue: document.getElementById('metauxTimerValue'),
  metauxMessage: document.getElementById('metauxMessage'),
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
  performanceModeSelect: document.getElementById('performanceModeSelect'),
  performanceModeNote: document.getElementById('performanceModeNote'),
  uiScaleSelect: document.getElementById('uiScaleSelect'),
  themeSelect: document.getElementById('themeSelect'),
  textFontSelect: document.getElementById('textFontSelect'),
  digitFontSelect: document.getElementById('digitFontSelect'),
  musicTrackSelect: document.getElementById('musicTrackSelect'),
  musicTrackStatus: document.getElementById('musicTrackStatus'),
  musicVolumeSlider: document.getElementById('musicVolumeSlider'),
    optionsWelcomeTitle: document.getElementById('optionsWelcomeTitle'),
    optionsWelcomeIntro: document.getElementById('optionsWelcomeIntro'),
    musicOptionCard: document.querySelector('.option-card--chiptune-link'),
    musicOptionRow: document.querySelector('.option-row--midi-link'),
    openMidiModuleButton: document.getElementById('openMidiModuleButton'),
    midiPage: document.getElementById('midi'),
    midiModuleCard: document.querySelector('.midi-page__module'),
    midiKeyboardArea: document.getElementById('midiKeyboardArea'),
  clickSoundToggleCard: document.getElementById('clickSoundToggleCard'),
  clickSoundToggle: document.getElementById('clickSoundToggle'),
  clickSoundToggleStatus: document.getElementById('clickSoundToggleStatus'),
  atomAnimationToggleCard: document.getElementById('atomAnimationToggleCard'),
  atomAnimationToggle: document.getElementById('atomAnimationToggle'),
  atomAnimationToggleStatus: document.getElementById('atomAnimationToggleStatus'),
  critAtomToggleCard: document.getElementById('critAtomToggleCard'),
  critAtomToggle: document.getElementById('critAtomToggle'),
  critAtomToggleStatus: document.getElementById('critAtomToggleStatus'),
  screenWakeLockToggleCard: document.getElementById('screenWakeLockToggleCard'),
  screenWakeLockToggle: document.getElementById('screenWakeLockToggle'),
  screenWakeLockToggleStatus: document.getElementById('screenWakeLockToggleStatus'),
  frenzyAutoCollectCard: document.getElementById('frenzyAutoCollectCard'),
  frenzyAutoCollectToggle: document.getElementById('frenzyAutoCollectToggle'),
  frenzyAutoCollectStatus: document.getElementById('frenzyAutoCollectStatus'),
  cryptoWidgetToggleCard: document.getElementById('cryptoWidgetToggleCard'),
  cryptoWidgetToggle: document.getElementById('cryptoWidgetToggle'),
  cryptoWidgetToggleStatus: document.getElementById('cryptoWidgetToggleStatus'),
  optionsArcadeDetails: document.getElementById('optionsArcadeDetails'),
  brickSkinOptionCard: document.getElementById('brickSkinOptionCard'),
  brickSkinSelect: document.getElementById('brickSkinSelect'),
  brickSkinStatus: document.getElementById('brickSkinStatus'),
  holdemOptionCard: document.getElementById('holdemOptionCard'),
  holdemRestartButton: document.getElementById('holdemRestartButton'),
  holdemBlindValue: document.getElementById('holdemBlindValue'),
  holdemBlindDivideButton: document.getElementById('holdemBlindDivideButton'),
  holdemBlindMultiplyButton: document.getElementById('holdemBlindMultiplyButton'),
  openDevkitButton: document.getElementById('openDevkitButton'),
  resetButton: document.getElementById('resetButton'),
  resetDialog: document.getElementById('resetDialog'),
  resetDialogForm: document.getElementById('resetDialogForm'),
  resetDialogInput: document.getElementById('resetDialogInput'),
  resetDialogMessage: document.getElementById('resetDialogMessage'),
  resetDialogError: document.getElementById('resetDialogError'),
  resetDialogCancel: document.getElementById('resetDialogCancel'),
  resetDialogConfirm: document.getElementById('resetDialogConfirm'),
  resetDialogLabel: document.getElementById('resetDialogLabel'),
  resetDialogTitle: document.getElementById('resetDialogTitle'),
  bigBangOptionCard: document.getElementById('bigBangOptionCard'),
  bigBangOptionToggle: document.getElementById('bigBangNavToggle'),
  infoApsBreakdown: document.getElementById('infoApsBreakdown'),
  infoApcBreakdown: document.getElementById('infoApcBreakdown'),
  infoCalculationsCard: document.getElementById('infoCalculationsCard'),
  infoCalculationsContent: document.getElementById('info-calculations-content'),
  infoCalculationsToggle: document.getElementById('infoCalculationsToggle'),
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
  infoProgressCard: document.getElementById('infoProgressCard'),
  infoProgressContent: document.getElementById('info-progress-content'),
  infoProgressToggle: document.getElementById('infoProgressToggle'),
  infoScoresCard: document.getElementById('infoScoresCard'),
  infoScoresContent: document.getElementById('info-scores-content'),
  infoScoresToggle: document.getElementById('infoScoresToggle'),
  infoPhotonDistanceValue: document.getElementById('infoPhotonDistanceValue'),
  infoPhotonSpeedValue: document.getElementById('infoPhotonSpeedValue'),
  infoPhotonAltitudeValue: document.getElementById('infoPhotonAltitudeValue'),
  infoStarsWarHardValue: document.getElementById('infoStarsWarHardValue'),
  infoStarsWarEasyValue: document.getElementById('infoStarsWarEasyValue'),
  starsWarHighscoreHard: document.getElementById('starsWarHighscoreHard'),
  starsWarHighscoreEasy: document.getElementById('starsWarHighscoreEasy'),
  infoBonusSubtitle: document.getElementById('infoBonusSubtitle'),
  infoElementBonuses: document.getElementById('infoElementBonuses'),
  infoElementBonusCard: document.getElementById('infoElementBonusCard'),
  infoAchievementsCard: document.getElementById('infoAchievementsCard'),
  infoAchievementsContent: document.getElementById('info-achievements-content'),
  infoAchievementsToggle: document.getElementById('infoAchievementsToggle'),
  infoDevkitShopCard: document.getElementById('infoDevkitShopCard'),
  infoWelcomeCard: document.querySelector('.info-card--welcome'),
  infoWelcomeContent: document.getElementById('info-welcome-content'),
  infoWelcomeToggle: document.getElementById('infoWelcomeToggle'),
  infoCardsCard: document.getElementById('infoCardsCard'),
  infoCardsContent: document.getElementById('info-cards-content'),
  infoCardsToggle: document.getElementById('infoCardsToggle'),
  infoCardsList: document.getElementById('infoCardsList'),
  infoCardsEmpty: document.getElementById('infoCardsEmpty'),
  collectionImagesCard: document.getElementById('collectionImagesCard'),
  collectionImagesContent: document.getElementById('collection-images-content'),
  collectionImagesToggle: document.getElementById('collectionImagesToggle'),
  collectionImagesList: document.getElementById('collectionImagesList'),
  collectionImagesEmpty: document.getElementById('collectionImagesEmpty'),
  collectionVideosCard: document.getElementById('collectionVideosCard'),
  collectionVideosContent: document.getElementById('collection-videos-content'),
  collectionVideosToggle: document.getElementById('collectionVideosToggle'),
  collectionVideosList: document.getElementById('collectionVideosList'),
  collectionVideosEmpty: document.getElementById('collectionVideosEmpty'),
  collectionBonusImagesCard: document.getElementById('collectionBonusImagesCard'),
  collectionBonusImagesContent: document.getElementById('collection-bonus-images-content'),
  collectionBonusImagesToggle: document.getElementById('collectionBonusImagesToggle'),
  collectionBonusImagesList: document.getElementById('collectionBonusImagesList'),
  collectionBonusImagesEmpty: document.getElementById('collectionBonusImagesEmpty'),
  collectionBonus2ImagesCard: document.getElementById('collectionBonus2ImagesCard'),
  collectionBonus2ImagesContent: document.getElementById('collection-bonus2-images-content'),
  collectionBonus2ImagesToggle: document.getElementById('collectionBonus2ImagesToggle'),
  collectionBonus2ImagesList: document.getElementById('collectionBonus2ImagesList'),
  collectionBonus2ImagesEmpty: document.getElementById('collectionBonus2ImagesEmpty'),
  infoCharactersCard: document.querySelector('.info-card--characters'),
  infoCharactersContent: document.getElementById('info-characters-content'),
  infoCharactersToggle: document.getElementById('infoCharactersToggle'),
  gachaCardOverlay: document.getElementById('gachaCardOverlay'),
  gachaCardOverlayDialog: document.getElementById('gachaCardOverlayDialog'),
  gachaCardOverlayClose: document.getElementById('gachaCardOverlayClose'),
  gachaCardOverlayImage: document.getElementById('gachaCardOverlayImage'),
  gachaCardOverlayVideo: document.getElementById('gachaCardOverlayVideo'),
  gachaCardOverlayLabel: document.getElementById('gachaCardOverlayLabel'),
  gachaCardOverlayCount: document.getElementById('gachaCardOverlayCount'),
  gachaCardOverlayTitle: document.getElementById('gachaCardOverlayTitle'),
  gachaCardOverlayHint: document.getElementById('gachaCardOverlayHint'),
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
  devkitShopDetails: document.getElementById('devkitShopDetails'),
  devkitToggleShop: document.getElementById('devkitToggleShop'),
  devkitToggleGacha: document.getElementById('devkitToggleGacha')
  };
}

function applyStartupOverlayDuration() {
  if (typeof document === 'undefined') {
    return;
  }

  const root = document.documentElement;
  if (!root || !root.style || typeof root.style.setProperty !== 'function') {
    return;
  }

  const normalizedDuration = getNormalizedStartupFadeDuration();

  root.style.setProperty('--startup-fade-duration', `${normalizedDuration}ms`);
}

function showStartupOverlay(options = {}) {
  const overlay = getStartupOverlayElement();
  if (!overlay) {
    return;
  }

  if (overlayFadeFallbackTimeout != null) {
    clearTimeout(overlayFadeFallbackTimeout);
    overlayFadeFallbackTimeout = null;
  }

  overlay.removeAttribute('hidden');

  const instant = options && options.instant === true;
  if (instant) {
    overlay.style.transitionDuration = '0ms';
  }

  if (!overlay.classList.contains('startup-overlay--visible')) {
    overlay.classList.add('startup-overlay--visible');
  }

  if (instant) {
    requestAnimationFrame(() => {
      overlay.style.transitionDuration = '';
    });
  }

  armStartupOverlayFallback({ reset: true });
}

function hideStartupOverlay(options = {}) {
  const overlay = getStartupOverlayElement();

  const delayMs = options && typeof options.delayMs === 'number' && options.delayMs > 0
    ? options.delayMs
    : 0;
  const instant = options && options.instant === true;

  clearStartupOverlayFailsafe();
  clearGlobalStartupOverlayFallback();

  if (!overlay) {
    return;
  }

  const startFade = () => {
    if (!overlay.classList.contains('startup-overlay--visible')) {
      overlay.setAttribute('hidden', '');
      return;
    }

    const finalize = () => {
      if (overlayFadeFallbackTimeout != null) {
        clearTimeout(overlayFadeFallbackTimeout);
        overlayFadeFallbackTimeout = null;
      }
      overlay.setAttribute('hidden', '');
    };

    if (instant) {
      overlay.classList.remove('startup-overlay--visible');
      finalize();
      return;
    }

    overlay.addEventListener('transitionend', finalize, { once: true });
    overlay.classList.remove('startup-overlay--visible');

    const fallbackDelay = getNormalizedStartupFadeDuration();

    overlayFadeFallbackTimeout = setTimeout(() => {
      overlay.removeEventListener('transitionend', finalize);
      finalize();
    }, fallbackDelay + 60);

    if (overlay.style.transitionDuration === '0ms') {
      overlay.style.transitionDuration = '';
    }
  };

  if (delayMs > 0) {
    setTimeout(startFade, delayMs);
  } else {
    requestAnimationFrame(startFade);
  }
}

function handleVisibilityChange() {
  if (typeof document === 'undefined') {
    return;
  }

  const isHidden = document.visibilityState
    ? document.visibilityState === 'hidden'
    : document.hidden === true;

  if (isHidden) {
    pageHiddenAt = Date.now();
    return;
  }

  pageHiddenAt = null;
}
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
  const normalizeDetail = (entry, index, key) => {
    if (!entry || typeof entry !== 'object') {
      return null;
    }
    const label = typeof entry.label === 'string' ? entry.label.trim() : '';
    const description = typeof entry.description === 'string' ? entry.description.trim() : '';
    if (!label && !description) {
      return null;
    }
    const idCandidate = entry.id ?? key;
    const id = createDetailId(idCandidate, label, index);
    return { id, label, description };
  };
  if (source.details && typeof source.details === 'object' && !Array.isArray(source.details)) {
    return Object.entries(source.details)
      .map(([key, entry], index) => normalizeDetail(entry, index, key))
      .filter(Boolean);
  }
  const rawDetails = Array.isArray(source.unlockedDetails)
    ? source.unlockedDetails
    : Array.isArray(source.details)
      ? source.details
      : [];
  return rawDetails
    .map((entry, index) => normalizeDetail(entry, index))
    .filter(Boolean);
}

function renderOptionsWelcomeContent(options = {}) {
  const copy = options.copy ?? getOptionsWelcomeCardCopy();
  const fallbackCopy = options.fallback ?? CONFIG_OPTIONS_WELCOME_CARD;

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
    const metadata = options.metadata
      || getOptionsDetailMetadata({ copy, fallback: fallbackCopy, forceRefresh: options.forceMetadata });
    const renderIds = Array.isArray(options.renderIds)
      ? options.renderIds
      : computeRenderableOptionDetailIds(metadata.detailOrder, metadata.detailMap);
    container.innerHTML = '';
    renderIds.forEach(id => {
      const detail = metadata.detailMap.get(id);
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
    const key = renderIds.join('|');
    container.dataset.renderKey = key;
    const hasDetails = renderIds.length > 0;
    container.hidden = !hasDetails;
    container.setAttribute('aria-hidden', hasDetails ? 'false' : 'true');
    return renderIds;
  }
  return [];
}

function refreshOptionsWelcomeContent() {
  cachedOptionsDetailMetadata = null;
  const copy = getOptionsWelcomeCardCopy();
  const fallbackCopy = CONFIG_OPTIONS_WELCOME_CARD;
  const metadata = getOptionsDetailMetadata({ copy, fallback: fallbackCopy, forceRefresh: true });
  const renderIds = computeRenderableOptionDetailIds(metadata.detailOrder, metadata.detailMap);
  renderOptionsWelcomeContent({ copy, fallback: fallbackCopy, metadata, renderIds });
  updateOptionsIntroDetails({ metadata, renderIds });
}

function subscribeOptionsWelcomeContentUpdates() {
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(() => {
      refreshOptionsWelcomeContent();
      updateArcadeHubLocks();
    });
    return;
  }
  if (typeof globalThis !== 'undefined'
    && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', () => {
      refreshOptionsWelcomeContent();
      updateArcadeHubLocks();
    });
  }
}


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

function normalizeUiScaleSelection(value) {
  if (typeof value !== 'string') {
    return UI_SCALE_DEFAULT;
  }
  const normalized = value.trim().toLowerCase();
  return Object.prototype.hasOwnProperty.call(UI_SCALE_CHOICES, normalized)
    ? normalized
    : UI_SCALE_DEFAULT;
}

function readStoredUiScale() {
  try {
    const stored = globalThis.localStorage?.getItem(UI_SCALE_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeUiScaleSelection(stored);
    }
  } catch (error) {
    console.warn('Unable to read UI scale preference', error);
  }
  return null;
}

function writeStoredUiScale(value) {
  try {
    const normalized = normalizeUiScaleSelection(value);
    globalThis.localStorage?.setItem(UI_SCALE_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist UI scale preference', error);
  }
}

function updateEffectiveUiScaleFactor() {
  if (typeof document === 'undefined') {
    return;
  }
  const root = document.documentElement;
  if (!root || !root.style) {
    return;
  }
  const baseFactor = Number.isFinite(currentUiScaleFactor) && currentUiScaleFactor > 0
    ? currentUiScaleFactor
    : 1;
  const autoFactor = Number.isFinite(autoUiScaleFactor) && autoUiScaleFactor > 0
    ? autoUiScaleFactor
    : 1;
  const effective = Math.max(0.4, Math.min(3, baseFactor * autoFactor));
  root.style.setProperty('--font-scale-factor', String(effective));
}

function getActivePageElement() {
  if (typeof document === 'undefined') {
    return null;
  }
  const activePageId = document.body?.dataset?.activePage;
  return activePageId ? document.getElementById(activePageId) : null;
}

function computeAutoUiScaleForPage(pageElement) {
  if (typeof window === 'undefined') {
    return 1;
  }
  const viewportWidth = Math.max(
    window.innerWidth || 0,
    document?.documentElement?.clientWidth || 0,
    document?.body?.clientWidth || 0
  );
  const viewportHeight = Math.max(
    window.innerHeight || 0,
    document?.documentElement?.clientHeight || 0,
    document?.body?.clientHeight || 0
  );
  const headerRect = elements?.appHeader?.getBoundingClientRect?.();
  const headerWidth = Math.max(
    elements?.appHeader?.scrollWidth || 0,
    headerRect?.width || 0
  );
  const headerHeight = Math.max(
    headerRect?.height || 0,
    elements?.appHeader?.offsetHeight || 0
  );

  let widthScale = 1;
  if (viewportWidth > 0) {
    const safeWidth = Math.max(0, viewportWidth - AUTO_UI_SCALE_SAFE_PADDING);
    const pageWidth = Math.max(
      pageElement?.scrollWidth || 0,
      pageElement?.getBoundingClientRect?.().width || 0
    );
    const containerWidth = Math.max(
      elements?.pageContainer?.scrollWidth || 0,
      elements?.pageContainer?.getBoundingClientRect?.().width || 0
    );
    const documentWidth = Math.max(
      document?.documentElement?.scrollWidth || 0,
      document?.body?.scrollWidth || 0
    );
    const widestContent = Math.max(
      viewportWidth,
      headerWidth,
      pageWidth,
      containerWidth,
      documentWidth
    );
    if (widestContent > safeWidth + AUTO_UI_SCALE_OVERFLOW_TOLERANCE) {
      if (safeWidth <= 0) {
        widthScale = AUTO_UI_SCALE_MIN_FACTOR;
      } else {
        const ratio = safeWidth / widestContent;
        if (Number.isFinite(ratio) && ratio > 0) {
          widthScale = Math.max(AUTO_UI_SCALE_MIN_FACTOR, Math.min(1, ratio));
        }
      }
    }
  }

  let heightScale = 1;
  if (pageElement) {
    const rawPageGroup = pageElement.dataset?.pageGroup;
    const pageGroup = typeof rawPageGroup === 'string' ? rawPageGroup.trim().toLowerCase() : '';
    const shouldClampHeight = pageGroup === 'arcade' || pageElement.id === 'midi';
    if (shouldClampHeight && viewportHeight > 0) {
      const availableHeight = Math.max(0, viewportHeight - headerHeight - AUTO_UI_SCALE_VERTICAL_PADDING);
      const pageHeight = Math.max(
        pageElement.scrollHeight || 0,
        pageElement.getBoundingClientRect?.().height || 0
      );
      const containerHeight = Math.max(
        elements?.pageContainer?.scrollHeight || 0,
        elements?.pageContainer?.getBoundingClientRect?.().height || 0
      );
      const tallestContent = Math.max(pageHeight, containerHeight);
      if (tallestContent > availableHeight + AUTO_UI_SCALE_HEIGHT_TOLERANCE) {
        if (availableHeight <= 0) {
          heightScale = AUTO_UI_SCALE_MIN_FACTOR;
        } else {
          const ratio = availableHeight / tallestContent;
          if (Number.isFinite(ratio) && ratio > 0) {
            heightScale = Math.max(AUTO_UI_SCALE_MIN_FACTOR, Math.min(1, ratio));
          }
        }
      }
    }
  }

  const finalScale = Math.min(widthScale, heightScale);
  if (!Number.isFinite(finalScale) || finalScale <= 0) {
    return 1;
  }
  return Math.max(AUTO_UI_SCALE_MIN_FACTOR, Math.min(1, finalScale));
}

function recalculateAutoUiScaleFactor() {
  pendingAutoUiScaleFrame = null;
  autoUiScaleFrameUsesTimeout = false;
  const activePage = getActivePageElement();
  const newFactor = computeAutoUiScaleForPage(activePage);
  if (!Number.isFinite(newFactor) || newFactor <= 0) {
    if (autoUiScaleFactor !== 1) {
      autoUiScaleFactor = 1;
      updateEffectiveUiScaleFactor();
    }
    return;
  }
  if (Math.abs(newFactor - autoUiScaleFactor) <= AUTO_UI_SCALE_TOLERANCE && newFactor < 1) {
    return;
  }
  if (Math.abs(newFactor - autoUiScaleFactor) <= AUTO_UI_SCALE_TOLERANCE && newFactor >= 1 && autoUiScaleFactor >= 1) {
    return;
  }
  autoUiScaleFactor = newFactor;
  updateEffectiveUiScaleFactor();
}

function scheduleAutoUiScaleUpdate(options = {}) {
  if (typeof window === 'undefined') {
    return;
  }
  const immediate = options?.immediate === true;
  if (immediate) {
    if (pendingAutoUiScaleFrame != null) {
      if (autoUiScaleFrameUsesTimeout) {
        clearTimeout(pendingAutoUiScaleFrame);
      } else {
        window.cancelAnimationFrame?.(pendingAutoUiScaleFrame);
      }
      pendingAutoUiScaleFrame = null;
      autoUiScaleFrameUsesTimeout = false;
    }
    recalculateAutoUiScaleFactor();
    return;
  }
  if (pendingAutoUiScaleFrame != null) {
    return;
  }
  if (typeof window.requestAnimationFrame === 'function') {
    pendingAutoUiScaleFrame = window.requestAnimationFrame(recalculateAutoUiScaleFactor);
    autoUiScaleFrameUsesTimeout = false;
  } else {
    pendingAutoUiScaleFrame = setTimeout(recalculateAutoUiScaleFactor, 16);
    autoUiScaleFrameUsesTimeout = true;
  }
}

function handleAutoUiScaleResize() {
  scheduleAutoUiScaleUpdate();
}

function handleAutoUiScaleOrientationChange() {
  scheduleAutoUiScaleUpdate();
  if (pendingAutoUiScaleTimeoutId != null) {
    clearTimeout(pendingAutoUiScaleTimeoutId);
  }
  pendingAutoUiScaleTimeoutId = setTimeout(() => {
    pendingAutoUiScaleTimeoutId = null;
    scheduleAutoUiScaleUpdate({ immediate: true });
  }, 320);
}

function initResponsiveAutoScale() {
  if (typeof window === 'undefined') {
    return;
  }
  scheduleAutoUiScaleUpdate({ immediate: true });
  window.addEventListener('resize', handleAutoUiScaleResize, { passive: true });
  window.addEventListener('orientationchange', handleAutoUiScaleOrientationChange);
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', handleAutoUiScaleResize);
    window.visualViewport.addEventListener('scroll', handleAutoUiScaleResize);
  }
}

function applyUiScaleSelection(selection, options = {}) {
  const normalized = normalizeUiScaleSelection(selection);
  const config = UI_SCALE_CHOICES[normalized] || UI_SCALE_CHOICES[UI_SCALE_DEFAULT];
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  const factor = config && Number.isFinite(config.factor) && config.factor > 0 ? config.factor : 1;
  currentUiScaleSelection = normalized;
  currentUiScaleFactor = factor;
  updateEffectiveUiScaleFactor();
  scheduleAutoUiScaleUpdate();
  if (typeof document !== 'undefined' && document.body) {
    document.body.setAttribute('data-ui-scale', normalized);
  }
  if (settings.updateControl && elements.uiScaleSelect) {
    elements.uiScaleSelect.value = normalized;
  }
  if (settings.persist) {
    writeStoredUiScale(normalized);
  }
  return normalized;
}

function initUiScaleOption() {
  const stored = readStoredUiScale();
  const initial = stored ?? UI_SCALE_DEFAULT;
  applyUiScaleSelection(initial, { persist: false, updateControl: true });
  if (!elements.uiScaleSelect) {
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
  try {
    const stored = globalThis.localStorage?.getItem(TEXT_FONT_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeTextFontSelection(stored);
    }
  } catch (error) {
    console.warn('Unable to read text font preference', error);
  }
  return null;
}

function writeStoredTextFont(value) {
  try {
    const normalized = normalizeTextFontSelection(value);
    globalThis.localStorage?.setItem(TEXT_FONT_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist text font preference', error);
  }
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
  if (settings.updateControl && elements.textFontSelect) {
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
  if (!elements.textFontSelect) {
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
  try {
    const stored = globalThis.localStorage?.getItem(DIGIT_FONT_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeDigitFontSelection(stored);
    }
  } catch (error) {
    console.warn('Unable to read digit font preference', error);
  }
  return null;
}

function writeStoredDigitFont(value) {
  try {
    const normalized = normalizeDigitFontSelection(value);
    globalThis.localStorage?.setItem(DIGIT_FONT_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist digit font preference', error);
  }
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
  if (settings.updateControl && elements.digitFontSelect) {
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
  if (!elements.digitFontSelect) {
    return;
  }
  elements.digitFontSelect.addEventListener('change', event => {
    const value = event?.target?.value;
    applyDigitFontSelection(value, { persist: true, updateControl: false });
  });
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

function updateOptionsIntroDetails(options = {}) {
  if (!elements.optionsArcadeDetails) {
    return;
  }
  const metadata = options.metadata || getOptionsDetailMetadata();
  const renderIds = Array.isArray(options.renderIds)
    ? options.renderIds
    : computeRenderableOptionDetailIds(metadata.detailOrder, metadata.detailMap);
  const key = renderIds.join('|');
  if (elements.optionsArcadeDetails.dataset.renderKey !== key) {
    renderOptionsWelcomeContent({ metadata, renderIds });
    return;
  }
  const hasDetails = renderIds.length > 0;
  elements.optionsArcadeDetails.hidden = !hasDetails;
  elements.optionsArcadeDetails.setAttribute('aria-hidden', hasDetails ? 'false' : 'true');
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

function ensureBiggerGame() {
  if (biggerGame || typeof BiggerGame !== 'function') {
    return biggerGame;
  }
  if (!elements.biggerBoard) {
    return null;
  }
  biggerGame = new BiggerGame({
    pageElement: elements.biggerPage,
    boardElement: elements.biggerBoard,
    restartButton: elements.biggerRestartButton,
    overlayElement: elements.biggerOverlay,
    overlayTitleElement: elements.biggerOverlayTitle,
    overlayMessageElement: elements.biggerOverlayMessage,
    overlayActionElement: elements.biggerOverlayAction,
    overlayDismissElement: elements.biggerOverlayDismiss
  });
  return biggerGame;
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
    parallelUniverseElement: elements.quantum2048ParallelUniverseValue
  });
  return quantum2048Game;
}

function ensureStarBridgesGame() {
  if (starBridgesGame && typeof starBridgesGame === 'object') {
    return starBridgesGame;
  }
  if (window.starBridgesArcade && typeof window.starBridgesArcade === 'object') {
    starBridgesGame = window.starBridgesArcade;
    return starBridgesGame;
  }
  return null;
}

function ensureStarsWarGame() {
  if (starsWarGame && typeof starsWarGame === 'object') {
    return starsWarGame;
  }
  if (window.starsWarArcade && typeof window.starsWarArcade === 'object') {
    starsWarGame = window.starsWarArcade;
    return starsWarGame;
  }
  return null;
}

function ensurePipeTapGame() {
  if (pipeTapGame && typeof pipeTapGame === 'object') {
    return pipeTapGame;
  }
  if (window.pipeTapArcade && typeof window.pipeTapArcade === 'object') {
    pipeTapGame = window.pipeTapArcade;
    return pipeTapGame;
  }
  return null;
}

function ensureColorStackGame() {
  if (colorStackGame && typeof colorStackGame === 'object') {
    return colorStackGame;
  }
  if (window.colorStackArcade && typeof window.colorStackArcade === 'object') {
    colorStackGame = window.colorStackArcade;
    return colorStackGame;
  }
  return null;
}

function ensureMotocrossGame() {
  if (motocrossGame && typeof motocrossGame === 'object') {
    return motocrossGame;
  }
  if (window.motocrossArcade && typeof window.motocrossArcade === 'object') {
    motocrossGame = window.motocrossArcade;
    return motocrossGame;
  }
  return null;
}

function ensureHexGame() {
  if (hexGame && typeof hexGame === 'object') {
    return hexGame;
  }
  if (window.hexArcade && typeof window.hexArcade === 'object') {
    hexGame = window.hexArcade;
    return hexGame;
  }
  return null;
}

function ensureTwinsGame() {
  if (twinsGame && typeof twinsGame === 'object') {
    return twinsGame;
  }
  if (window.twinsArcade && typeof window.twinsArcade === 'object') {
    twinsGame = window.twinsArcade;
    return twinsGame;
  }
  return null;
}

function ensureSokobanGame() {
  if (sokobanGame && typeof sokobanGame === 'object') {
    return sokobanGame;
  }
  if (window.sokobanArcade && typeof window.sokobanArcade === 'object') {
    sokobanGame = window.sokobanArcade;
    return sokobanGame;
  }
  return null;
}

function ensureTaquinGame() {
  if (taquinGame && typeof taquinGame === 'object') {
    return taquinGame;
  }
  if (window.taquinArcade && typeof window.taquinArcade === 'object') {
    taquinGame = window.taquinArcade;
    return taquinGame;
  }
  return null;
}

function ensureLinkGame() {
  if (linkGame && typeof linkGame === 'object') {
    return linkGame;
  }
  if (window.linkArcade && typeof window.linkArcade === 'object') {
    linkGame = window.linkArcade;
    return linkGame;
  }
  return null;
}

function ensureLightsOutGame() {
  if (lightsOutGame && typeof lightsOutGame === 'object') {
    return lightsOutGame;
  }
  if (window.lightsOutArcade && typeof window.lightsOutArcade === 'object') {
    lightsOutGame = window.lightsOutArcade;
    return lightsOutGame;
  }
  return null;
}

function ensureGameOfLifeGame() {
  if (gameOfLifeGame && typeof gameOfLifeGame === 'object') {
    return gameOfLifeGame;
  }
  if (window.gameOfLifeArcade && typeof window.gameOfLifeArcade === 'object') {
    gameOfLifeGame = window.gameOfLifeArcade;
    return gameOfLifeGame;
  }
  return null;
}

function ensureEscapeGame() {
  if (escapeGame && typeof escapeGame === 'object') {
    if (typeof escapeGame.initialize === 'function') {
      escapeGame.initialize();
    }
    return escapeGame;
  }
  if (window.escapeArcade && typeof window.escapeArcade === 'object') {
    escapeGame = window.escapeArcade;
    if (typeof escapeGame.initialize === 'function') {
      escapeGame.initialize();
    }
    return escapeGame;
  }
  return null;
}

function areInfoBonusesUnlocked() {
  const unlocks = getPageUnlockState();
  return unlocks?.info === true;
}

function areAchievementsFeatureUnlocked() {
  return getUnlockedTrophySet().has(ACHIEVEMENTS_UNLOCK_TROPHY_ID);
}

function updateInfoAchievementsVisibility() {
  if (!elements.infoAchievementsCard) {
    return;
  }
  const unlocked = areAchievementsFeatureUnlocked();
  elements.infoAchievementsCard.hidden = !unlocked;
  elements.infoAchievementsCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  if (!unlocked && elements.goalsEmpty) {
    elements.goalsEmpty.hidden = true;
    elements.goalsEmpty.setAttribute('aria-hidden', 'true');
  }
}

function updateInfoBonusVisibility() {
  const visible = areInfoBonusesUnlocked() && isInfoSectionsFeatureEnabled();
  if (elements.infoElementBonusCard) {
    elements.infoElementBonusCard.hidden = !visible;
    elements.infoElementBonusCard.setAttribute('aria-hidden', visible ? 'false' : 'true');
  }
}

function updateInfoCardsVisibility() {
  if (!elements.infoCardsCard) {
    return;
  }
  const unlocked = isPageUnlocked('collection');
  elements.infoCardsCard.hidden = !unlocked;
  elements.infoCardsCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateCollectionImagesVisibility() {
  if (!elements.collectionImagesCard) {
    return;
  }
  const unlocked = isCollectionFeatureEnabled() && isPageUnlocked('collection');
  elements.collectionImagesCard.hidden = !unlocked;
  elements.collectionImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateCollectionBonusImagesVisibility() {
  if (!elements.collectionBonusImagesCard) {
    return;
  }
  const unlocked = isPageUnlocked('collection') && hasOwnedGachaBonusImages();
  elements.collectionBonusImagesCard.hidden = !unlocked;
  elements.collectionBonusImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateCollectionBonus2ImagesVisibility() {
  if (!elements.collectionBonus2ImagesCard) {
    return;
  }
  const unlocked = isPageUnlocked('collection') && hasOwnedGachaBonus2Images();
  elements.collectionBonus2ImagesCard.hidden = !unlocked;
  elements.collectionBonus2ImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateCollectionVideosVisibility() {
  if (!elements.collectionVideosCard) {
    return;
  }
  const hasVideos = hasUnlockedCollectionVideos();
  const unlocked = isCollectionFeatureEnabled() && isPageUnlocked('collection') && hasVideos;
  elements.collectionVideosCard.hidden = !unlocked;
  elements.collectionVideosCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateInfoDevkitVisibility() {
  if (!elements.infoDevkitShopCard) {
    return;
  }
  const visible = isInfoSectionsFeatureEnabled();
  elements.infoDevkitShopCard.hidden = !visible;
  elements.infoDevkitShopCard.setAttribute('aria-hidden', visible ? 'false' : 'true');
}

function readStoredInfoCardCollapsed(storageKey, defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(storageKey);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read info card preference', storageKey, error);
  }
  return !!defaultValue;
}

function writeStoredInfoCardCollapsed(storageKey, collapsed) {
  try {
    globalThis.localStorage?.setItem(storageKey, collapsed ? 'true' : 'false');
  } catch (error) {
    console.warn('Unable to persist info card preference', storageKey, error);
  }
}

function readStoredCollectionVideosUnlocked(defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read collection video unlock state', error);
  }
  return !!defaultValue;
}

function writeStoredCollectionVideosUnlocked(unlocked) {
  try {
    if (!globalThis.localStorage) {
      collectionVideosUnlockedCache = unlocked === true;
      return;
    }
    if (unlocked) {
      globalThis.localStorage.setItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY, 'true');
    } else {
      globalThis.localStorage.removeItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    }
    collectionVideosUnlockedCache = unlocked === true;
  } catch (error) {
    console.warn('Unable to persist collection video unlock state', error);
  }
}

function persistCollectionVideoUnlockState(unlocked) {
  writeStoredCollectionVideosUnlocked(unlocked === true);
}

function readStoredCollectionVideoSnapshotEntries() {
  try {
    const raw = globalThis.localStorage?.getItem(COLLECTION_VIDEOS_STATE_STORAGE_KEY);
    if (typeof raw !== 'string' || !raw.trim()) {
      return [];
    }
    const parsed = JSON.parse(raw);
    const sourceEntries = Array.isArray(parsed?.entries) ? parsed.entries : [];
    return sourceEntries
      .map(entry => {
        const id = typeof entry?.id === 'string' && entry.id.trim() ? entry.id.trim() : null;
        if (!id) {
          return null;
        }
        const acquiredOrder = Number(entry?.acquiredOrder);
        const firstAcquiredAt = Number(entry?.firstAcquiredAt);
        return {
          id,
          acquiredOrder: Number.isFinite(acquiredOrder) && acquiredOrder > 0
            ? Math.floor(acquiredOrder)
            : null,
          firstAcquiredAt: Number.isFinite(firstAcquiredAt) && firstAcquiredAt > 0
            ? firstAcquiredAt
            : null
        };
      })
      .filter(entry => entry);
  } catch (error) {
    console.warn('Unable to read collection video snapshot', error);
  }
  return [];
}

function writeStoredCollectionVideoSnapshot(entries) {
  try {
    if (!globalThis.localStorage) {
      return false;
    }
    if (!Array.isArray(entries) || entries.length === 0) {
      globalThis.localStorage.removeItem(COLLECTION_VIDEOS_STATE_STORAGE_KEY);
      return true;
    }
    const payload = {
      version: 1,
      entries: entries.map(entry => ({
        id: entry.id,
        acquiredOrder: entry.acquiredOrder || null,
        firstAcquiredAt: entry.firstAcquiredAt || null
      }))
    };
    globalThis.localStorage.setItem(COLLECTION_VIDEOS_STATE_STORAGE_KEY, JSON.stringify(payload));
    return true;
  } catch (error) {
    console.warn('Unable to persist collection video snapshot', error);
  }
  return false;
}

function clearStoredCollectionVideoSnapshot() {
  writeStoredCollectionVideoSnapshot([]);
}

function mergeStoredCollectionVideoSnapshot(collection) {
  const target = collection && typeof collection === 'object' ? collection : null;
  if (!target) {
    return false;
  }
  const snapshotEntries = readStoredCollectionVideoSnapshotEntries();
  if (!snapshotEntries.length) {
    return false;
  }
  let merged = false;
  snapshotEntries.forEach(entry => {
    if (!entry?.id) {
      return;
    }
    const reference = target[entry.id];
    if (!reference) {
      return;
    }
    const rawCount = Number(reference?.count ?? reference);
    if (!Number.isFinite(rawCount) || rawCount <= 0) {
      reference.count = 1;
      merged = true;
    }
    if (entry.acquiredOrder != null) {
      const storedOrder = Number(reference?.acquiredOrder);
      if (!Number.isFinite(storedOrder) || storedOrder <= 0) {
        reference.acquiredOrder = entry.acquiredOrder;
        merged = true;
      }
    }
    if (entry.firstAcquiredAt != null) {
      const storedFirst = Number(reference?.firstAcquiredAt);
      if (!Number.isFinite(storedFirst) || storedFirst <= 0) {
        reference.firstAcquiredAt = entry.firstAcquiredAt;
        merged = true;
      }
    }
  });
  return merged;
}

function syncCollectionVideoStateSnapshot(options = {}) {
  const collection = options.collection && typeof options.collection === 'object'
    ? options.collection
    : (gameState.collectionVideos && typeof gameState.collectionVideos === 'object'
      ? gameState.collectionVideos
      : null);
  if (!collection) {
    clearStoredCollectionVideoSnapshot();
    return;
  }
  const ownedEntries = Object.values(collection)
    .map(entry => {
      if (!entry) {
        return null;
      }
      const count = Number(entry?.count ?? entry);
      if (!Number.isFinite(count) || count <= 0) {
        return null;
      }
      return {
        id: entry.id,
        acquiredOrder: Number.isFinite(Number(entry?.acquiredOrder)) && Number(entry?.acquiredOrder) > 0
          ? Math.floor(Number(entry.acquiredOrder))
          : null,
        firstAcquiredAt: Number.isFinite(Number(entry?.firstAcquiredAt)) && Number(entry?.firstAcquiredAt) > 0
          ? Number(entry.firstAcquiredAt)
          : null
      };
    })
    .filter(entry => entry && entry.id);
  writeStoredCollectionVideoSnapshot(ownedEntries);
}

if (typeof globalThis !== 'undefined') {
  globalThis.syncCollectionVideoStateSnapshot = syncCollectionVideoStateSnapshot;
}


function readStoredCollectionVideosUnlocked(defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read collection video unlock state', error);
  }
  return !!defaultValue;
}

function writeStoredCollectionVideosUnlocked(unlocked) {
  try {
    if (!globalThis.localStorage) {
      collectionVideosUnlockedCache = unlocked === true;
      return;
    }
    if (unlocked) {
      globalThis.localStorage.setItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY, 'true');
    } else {
      globalThis.localStorage.removeItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    }
    collectionVideosUnlockedCache = unlocked === true;
  } catch (error) {
    console.warn('Unable to persist collection video unlock state', error);
  }
}

function persistCollectionVideoUnlockState(unlocked) {
  writeStoredCollectionVideosUnlocked(unlocked === true);
}


function readStoredHeaderCollapsed(defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(HEADER_COLLAPSED_STORAGE_KEY);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true' || normalized === '1') {
        return true;
      }
      if (normalized === 'false' || normalized === '0') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read header collapse preference', error);
  }
  return !!defaultValue;
}

function writeStoredHeaderCollapsed(collapsed) {
  try {
    globalThis.localStorage?.setItem(HEADER_COLLAPSED_STORAGE_KEY, collapsed ? 'true' : 'false');
  } catch (error) {
    console.warn('Unable to persist header collapse preference', error);
  }
}

function updateHeaderBannerToggleLabel(collapsed) {
  if (!elements.headerBannerToggle) {
    return;
  }
  const key = collapsed
    ? 'index.header.bannerToggle.expand'
    : 'index.header.bannerToggle.collapse';
  const fallback = collapsed ? 'Déployer la bannière' : 'Réduire la bannière';
  const label = translateOrDefault(key, fallback);
  elements.headerBannerToggle.setAttribute(
    'data-i18n',
    `aria-label:${key};title:${key}`
  );
  elements.headerBannerToggle.setAttribute('aria-label', label);
  elements.headerBannerToggle.setAttribute('title', label);
  const hiddenLabel = elements.headerBannerToggle.querySelector('.visually-hidden');
  if (hiddenLabel) {
    hiddenLabel.textContent = label;
    hiddenLabel.setAttribute('data-i18n', key);
  }
}

function setHeaderCollapsed(collapsed, options = {}) {
  if (!elements.appHeader || !elements.headerBannerToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  const isCollapsed = elements.appHeader.dataset.collapsed === 'true';
  if (!options.force && shouldCollapse === isCollapsed) {
    updateHeaderBannerToggleLabel(shouldCollapse);
    return;
  }

  if (shouldCollapse) {
    elements.appHeader.dataset.collapsed = 'true';
  } else {
    delete elements.appHeader.dataset.collapsed;
  }

  if (elements.navMenu) {
    if (shouldCollapse) {
      elements.navMenu.hidden = true;
      elements.navMenu.setAttribute('aria-hidden', 'true');
    } else {
      elements.navMenu.hidden = false;
      elements.navMenu.removeAttribute('aria-hidden');
    }
  }

  if (elements.statusBar) {
    if (shouldCollapse) {
      elements.statusBar.setAttribute('aria-hidden', 'true');
    } else {
      elements.statusBar.removeAttribute('aria-hidden');
    }
  }

  if (elements.frenzyStatus) {
    if (shouldCollapse) {
      elements.frenzyStatus.setAttribute('aria-hidden', 'true');
    } else if (elements.frenzyStatus.hidden) {
      elements.frenzyStatus.removeAttribute('aria-hidden');
    } else {
      elements.frenzyStatus.setAttribute('aria-hidden', 'false');
    }
  }

  elements.headerBannerToggle.setAttribute('aria-pressed', shouldCollapse ? 'true' : 'false');
  elements.headerBannerToggle.setAttribute('data-state', shouldCollapse ? 'collapsed' : 'expanded');
  updateHeaderBannerToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredHeaderCollapsed(shouldCollapse);
  }
}

function toggleHeaderCollapsed() {
  if (!elements.appHeader) {
    return;
  }
  const currentlyCollapsed = elements.appHeader.dataset.collapsed === 'true';
  setHeaderCollapsed(!currentlyCollapsed);
}

function initHeaderBannerToggle() {
  if (!elements.headerBannerToggle || !elements.appHeader) {
    return;
  }
  const initiallyCollapsed = readStoredHeaderCollapsed(false);
  setHeaderCollapsed(initiallyCollapsed, { persist: false, force: true });
  elements.headerBannerToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleHeaderCollapsed();
  });
}

function subscribeHeaderBannerLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.appHeader
      ? elements.appHeader.dataset.collapsed === 'true'
      : false;
    updateHeaderBannerToggleLabel(collapsed);
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

function updateInfoWelcomeToggleLabel(collapsed) {
  if (!elements.infoWelcomeToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.welcome.toggle.expand'
    : 'index.sections.info.welcome.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoWelcomeToggle.setAttribute('data-i18n', key);
  elements.infoWelcomeToggle.textContent = label;
  elements.infoWelcomeToggle.setAttribute('aria-label', label);
}

function setInfoWelcomeCollapsed(collapsed, options = {}) {
  if (!elements.infoWelcomeCard || !elements.infoWelcomeContent || !elements.infoWelcomeToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoWelcomeCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoWelcomeContent.hidden = shouldCollapse;
  elements.infoWelcomeContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoWelcomeToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoWelcomeToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_WELCOME_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoWelcomeCollapsed() {
  if (!elements.infoWelcomeCard) {
    return;
  }
  const currentlyCollapsed = elements.infoWelcomeCard.classList.contains('info-card--collapsed');
  setInfoWelcomeCollapsed(!currentlyCollapsed);
}

function initInfoWelcomeCard() {
  if (!elements.infoWelcomeCard || !elements.infoWelcomeContent || !elements.infoWelcomeToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_WELCOME_COLLAPSED_STORAGE_KEY, false);
  setInfoWelcomeCollapsed(initialCollapsed, { persist: false });
  elements.infoWelcomeToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoWelcomeCollapsed();
  });
}

function updateInfoAchievementsToggleLabel(collapsed) {
  if (!elements.infoAchievementsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.achievements.toggle.expand'
    : 'index.sections.info.achievements.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoAchievementsToggle.setAttribute('data-i18n', key);
  elements.infoAchievementsToggle.textContent = label;
  elements.infoAchievementsToggle.setAttribute('aria-label', label);
}

function setInfoAchievementsCollapsed(collapsed, options = {}) {
  if (!elements.infoAchievementsCard
    || !elements.infoAchievementsContent
    || !elements.infoAchievementsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoAchievementsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoAchievementsContent.hidden = shouldCollapse;
  elements.infoAchievementsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoAchievementsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoAchievementsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_ACHIEVEMENTS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoAchievementsCollapsed() {
  if (!elements.infoAchievementsCard) {
    return;
  }
  const currentlyCollapsed = elements.infoAchievementsCard.classList.contains('info-card--collapsed');
  setInfoAchievementsCollapsed(!currentlyCollapsed);
}

function initInfoAchievementsCard() {
  if (!elements.infoAchievementsCard
    || !elements.infoAchievementsContent
    || !elements.infoAchievementsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_ACHIEVEMENTS_COLLAPSED_STORAGE_KEY, false);
  setInfoAchievementsCollapsed(initialCollapsed, { persist: false });
  elements.infoAchievementsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoAchievementsCollapsed();
  });
}

function updateInfoCharactersToggleLabel(collapsed) {
  if (!elements.infoCharactersToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.characters.toggle.expand'
    : 'index.sections.info.characters.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoCharactersToggle.setAttribute('data-i18n', key);
  elements.infoCharactersToggle.textContent = label;
  elements.infoCharactersToggle.setAttribute('aria-label', label);
}

function updateInfoCardsToggleLabel(collapsed) {
  if (!elements.infoCardsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.cards.toggle.expand'
    : 'index.sections.info.cards.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoCardsToggle.setAttribute('data-i18n', key);
  elements.infoCardsToggle.textContent = label;
  elements.infoCardsToggle.setAttribute('aria-label', label);
}

function updateInfoCalculationsToggleLabel(collapsed) {
  if (!elements.infoCalculationsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.calculations.toggle.expand'
    : 'index.sections.info.calculations.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoCalculationsToggle.setAttribute('data-i18n', key);
  elements.infoCalculationsToggle.textContent = label;
  elements.infoCalculationsToggle.setAttribute('aria-label', label);
}

function updateInfoProgressToggleLabel(collapsed) {
  if (!elements.infoProgressToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.progress.toggle.expand'
    : 'index.sections.info.progress.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoProgressToggle.setAttribute('data-i18n', key);
  elements.infoProgressToggle.textContent = label;
  elements.infoProgressToggle.setAttribute('aria-label', label);
}

function updateInfoScoresToggleLabel(collapsed) {
  if (!elements.infoScoresToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.scores.toggle.expand'
    : 'index.sections.info.scores.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoScoresToggle.setAttribute('data-i18n', key);
  elements.infoScoresToggle.textContent = label;
  elements.infoScoresToggle.setAttribute('aria-label', label);
}

function updateCollectionImagesToggleLabel(collapsed) {
  if (!elements.collectionImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.images.toggle.expand'
    : 'index.sections.collection.images.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionImagesToggle.setAttribute('data-i18n', key);
  elements.collectionImagesToggle.textContent = label;
  elements.collectionImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionBonusImagesToggleLabel(collapsed) {
  if (!elements.collectionBonusImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.bonusImages.toggle.expand'
    : 'index.sections.collection.bonusImages.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionBonusImagesToggle.setAttribute('data-i18n', key);
  elements.collectionBonusImagesToggle.textContent = label;
  elements.collectionBonusImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionBonus2ImagesToggleLabel(collapsed) {
  if (!elements.collectionBonus2ImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.bonus2Images.toggle.expand'
    : 'index.sections.collection.bonus2Images.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionBonus2ImagesToggle.setAttribute('data-i18n', key);
  elements.collectionBonus2ImagesToggle.textContent = label;
  elements.collectionBonus2ImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionVideosToggleLabel(collapsed) {
  if (!elements.collectionVideosToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.videos.toggle.expand'
    : 'index.sections.collection.videos.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionVideosToggle.setAttribute('data-i18n', key);
  elements.collectionVideosToggle.textContent = label;
  elements.collectionVideosToggle.setAttribute('aria-label', label);
}

function setInfoCharactersCollapsed(collapsed, options = {}) {
  if (!elements.infoCharactersCard || !elements.infoCharactersContent || !elements.infoCharactersToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoCharactersCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoCharactersContent.hidden = shouldCollapse;
  elements.infoCharactersContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoCharactersToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoCharactersToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_CHARACTERS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoCharactersCollapsed() {
  if (!elements.infoCharactersCard) {
    return;
  }
  const currentlyCollapsed = elements.infoCharactersCard.classList.contains('info-card--collapsed');
  setInfoCharactersCollapsed(!currentlyCollapsed);
}

function initInfoCharactersCard() {
  if (!elements.infoCharactersCard || !elements.infoCharactersContent || !elements.infoCharactersToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_CHARACTERS_COLLAPSED_STORAGE_KEY, false);
  setInfoCharactersCollapsed(initialCollapsed, { persist: false });
  elements.infoCharactersToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoCharactersCollapsed();
  });
}

function setInfoCardsCollapsed(collapsed, options = {}) {
  if (!elements.infoCardsCard || !elements.infoCardsContent || !elements.infoCardsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoCardsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoCardsContent.hidden = shouldCollapse;
  elements.infoCardsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoCardsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoCardsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_CARDS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoCardsCollapsed() {
  if (!elements.infoCardsCard) {
    return;
  }
  const currentlyCollapsed = elements.infoCardsCard.classList.contains('info-card--collapsed');
  setInfoCardsCollapsed(!currentlyCollapsed);
}

function initInfoCardsCard() {
  if (!elements.infoCardsCard || !elements.infoCardsContent || !elements.infoCardsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_CARDS_COLLAPSED_STORAGE_KEY, false);
  setInfoCardsCollapsed(initialCollapsed, { persist: false });
  elements.infoCardsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoCardsCollapsed();
  });
}

function setInfoCalculationsCollapsed(collapsed, options = {}) {
  if (!elements.infoCalculationsCard
    || !elements.infoCalculationsContent
    || !elements.infoCalculationsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoCalculationsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoCalculationsContent.hidden = shouldCollapse;
  elements.infoCalculationsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoCalculationsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoCalculationsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoCalculationsCollapsed() {
  if (!elements.infoCalculationsCard) {
    return;
  }
  const currentlyCollapsed = elements.infoCalculationsCard.classList.contains('info-card--collapsed');
  setInfoCalculationsCollapsed(!currentlyCollapsed);
}

function initInfoCalculationsCard() {
  if (!elements.infoCalculationsCard
    || !elements.infoCalculationsContent
    || !elements.infoCalculationsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY, false);
  setInfoCalculationsCollapsed(initialCollapsed, { persist: false });
  elements.infoCalculationsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoCalculationsCollapsed();
  });
}

function setInfoProgressCollapsed(collapsed, options = {}) {
  if (!elements.infoProgressCard
    || !elements.infoProgressContent
    || !elements.infoProgressToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoProgressCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoProgressContent.hidden = shouldCollapse;
  elements.infoProgressContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoProgressToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoProgressToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_PROGRESS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoProgressCollapsed() {
  if (!elements.infoProgressCard) {
    return;
  }
  const currentlyCollapsed = elements.infoProgressCard.classList.contains('info-card--collapsed');
  setInfoProgressCollapsed(!currentlyCollapsed);
}

function initInfoProgressCard() {
  if (!elements.infoProgressCard
    || !elements.infoProgressContent
    || !elements.infoProgressToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_PROGRESS_COLLAPSED_STORAGE_KEY, false);
  setInfoProgressCollapsed(initialCollapsed, { persist: false });
  elements.infoProgressToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoProgressCollapsed();
  });
}

function setInfoScoresCollapsed(collapsed, options = {}) {
  if (!elements.infoScoresCard
    || !elements.infoScoresContent
    || !elements.infoScoresToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoScoresCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoScoresContent.hidden = shouldCollapse;
  elements.infoScoresContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoScoresToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoScoresToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_SCORES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoScoresCollapsed() {
  if (!elements.infoScoresCard) {
    return;
  }
  const currentlyCollapsed = elements.infoScoresCard.classList.contains('info-card--collapsed');
  setInfoScoresCollapsed(!currentlyCollapsed);
}

function initInfoScoresCard() {
  if (!elements.infoScoresCard
    || !elements.infoScoresContent
    || !elements.infoScoresToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_SCORES_COLLAPSED_STORAGE_KEY, false);
  setInfoScoresCollapsed(initialCollapsed, { persist: false });
  elements.infoScoresToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoScoresCollapsed();
  });
}

function setCollectionImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionImagesCard
    || !elements.collectionImagesContent
    || !elements.collectionImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionImagesContent.hidden = shouldCollapse;
  elements.collectionImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionBonusImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionBonusImagesCard
    || !elements.collectionBonusImagesContent
    || !elements.collectionBonusImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionBonusImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionBonusImagesContent.hidden = shouldCollapse;
  elements.collectionBonusImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionBonusImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionBonusImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionBonus2ImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionBonus2ImagesCard
    || !elements.collectionBonus2ImagesContent
    || !elements.collectionBonus2ImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionBonus2ImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionBonus2ImagesContent.hidden = shouldCollapse;
  elements.collectionBonus2ImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionBonus2ImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionBonus2ImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionVideosCollapsed(collapsed, options = {}) {
  if (!elements.collectionVideosCard
    || !elements.collectionVideosContent
    || !elements.collectionVideosToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionVideosCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionVideosContent.hidden = shouldCollapse;
  elements.collectionVideosContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionVideosToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionVideosToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_VIDEOS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleCollectionImagesCollapsed() {
  if (!elements.collectionImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionImagesCard.classList.contains('info-card--collapsed');
  setCollectionImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionBonusImagesCollapsed() {
  if (!elements.collectionBonusImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionBonusImagesCard.classList.contains('info-card--collapsed');
  setCollectionBonusImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionBonus2ImagesCollapsed() {
  if (!elements.collectionBonus2ImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionBonus2ImagesCard.classList.contains('info-card--collapsed');
  setCollectionBonus2ImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionVideosCollapsed() {
  if (!elements.collectionVideosCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionVideosCard.classList.contains('info-card--collapsed');
  setCollectionVideosCollapsed(!currentlyCollapsed);
}

function initCollectionImagesCard() {
  if (!elements.collectionImagesCard
    || !elements.collectionImagesContent
    || !elements.collectionImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY, false);
  setCollectionImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionImagesCollapsed();
  });
}

function initCollectionBonusImagesCard() {
  if (!elements.collectionBonusImagesCard
    || !elements.collectionBonusImagesContent
    || !elements.collectionBonusImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionBonusImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionBonusImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionBonusImagesCollapsed();
  });
}

function initCollectionBonus2ImagesCard() {
  if (!elements.collectionBonus2ImagesCard
    || !elements.collectionBonus2ImagesContent
    || !elements.collectionBonus2ImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionBonus2ImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionBonus2ImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionBonus2ImagesCollapsed();
  });
}

function initCollectionVideosCard() {
  if (!elements.collectionVideosCard
    || !elements.collectionVideosContent
    || !elements.collectionVideosToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_VIDEOS_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionVideosCollapsed(initialCollapsed, { persist: false });
  elements.collectionVideosToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionVideosCollapsed();
  });
}

function subscribeInfoWelcomeLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoWelcomeCard
      ? elements.infoWelcomeCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoWelcomeToggleLabel(collapsed);
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

function subscribeInfoAchievementsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoAchievementsCard
      ? elements.infoAchievementsCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoAchievementsToggleLabel(collapsed);
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

function subscribeInfoCharactersLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoCharactersCard
      ? elements.infoCharactersCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoCharactersToggleLabel(collapsed);
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

function subscribeInfoCardsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoCardsCard
      ? elements.infoCardsCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoCardsToggleLabel(collapsed);
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

function subscribeInfoCalculationsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoCalculationsCard
      ? elements.infoCalculationsCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoCalculationsToggleLabel(collapsed);
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

function subscribeInfoProgressLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoProgressCard
      ? elements.infoProgressCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoProgressToggleLabel(collapsed);
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

function subscribeCollectionImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionImagesCard
      ? elements.collectionImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionImagesToggleLabel(collapsed);
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

function subscribeCollectionBonusImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionBonusImagesCard
      ? elements.collectionBonusImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionBonusImagesToggleLabel(collapsed);
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

function subscribeCollectionBonus2ImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionBonus2ImagesCard
      ? elements.collectionBonus2ImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionBonus2ImagesToggleLabel(collapsed);
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

function subscribeCollectionVideosLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionVideosCard
      ? elements.collectionVideosCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionVideosToggleLabel(collapsed);
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

function subscribeBigBangLanguageUpdates() {
  const handler = () => {
    updateBigBangActionUI();
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

function updatePageUnlockUI() {
  const buttonConfig = [
    ['gacha', elements.navGachaButton],
    ['tableau', elements.navTableButton],
    ['fusion', elements.navFusionButton],
    ['collection', elements.navCollectionButton]
  ];

  buttonConfig.forEach(([pageId, button]) => {
    const unlocked = isPageUnlocked(pageId);
    setNavButtonLockState(button, unlocked);
  });

  setNavButtonLockState(elements.navInfoButton, true);
  updateInfoBonusVisibility();

  ensureActivePageUnlocked();
}

function updatePrimaryNavigationLocks() {
  const atoms = gameState.atoms instanceof LayeredNumber
    ? gameState.atoms
    : toLayeredValue(gameState.atoms, 0);
  const shopUnlocked = atoms.compare(SHOP_UNLOCK_THRESHOLD) >= 0;
  setNavButtonLockState(elements.navShopButton, shopUnlocked);
  updateShopUnlockHint();

  updateInfoAchievementsVisibility();
  updateInfoDevkitVisibility();
  updateInfoCardsVisibility();
  updateCollectionImagesVisibility();
  updateCollectionVideosVisibility();
  updateCollectionBonusImagesVisibility();
  updateCollectionBonus2ImagesVisibility();
  updateMusicModuleVisibility();

  ensureActivePageUnlocked();
}

function getBigBangLevelBonus() {
  const raw = Number(gameState.bigBangLevelBonus ?? 0);
  if (!Number.isFinite(raw)) {
    return 0;
  }
  return Math.max(0, Math.floor(raw));
}

function setBigBangLevelBonus(value) {
  const numeric = Number(value);
  const sanitized = Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0;
  gameState.bigBangLevelBonus = sanitized;
  return sanitized;
}

function getBigBangCompletionCount() {
  const step = Number(BIG_BANG_LEVEL_BONUS_STEP);
  if (!Number.isFinite(step) || step <= 0) {
    return 0;
  }
  const bonus = getBigBangLevelBonus();
  if (!Number.isFinite(bonus) || bonus <= 0) {
    return 0;
  }
  return Math.max(0, Math.floor(bonus / step));
}

const BIG_BANG_REQUIRED_SHOP_IDS = ['godFinger', 'starCore'];

function getBigBangRequiredUpgrades() {
  if (!Array.isArray(UPGRADE_DEFS) || UPGRADE_DEFS.length === 0) {
    return [];
  }
  const requiredIds = new Set(BIG_BANG_REQUIRED_SHOP_IDS);
  return UPGRADE_DEFS.filter(def => requiredIds.has(def.id));
}

function getBigBangRequiredShopNames() {
  return BIG_BANG_REQUIRED_SHOP_IDS.map(id => UPGRADE_NAME_MAP.get(id) || id);
}

function getBigBangRemainingLevels() {
  const requiredUpgrades = getBigBangRequiredUpgrades();
  if (!requiredUpgrades.length) {
    return { total: 0, infinite: false, display: '0' };
  }
  let total = 0;
  let infinite = false;
  requiredUpgrades.forEach(def => {
    const remaining = getRemainingUpgradeCapacity(def);
    if (!Number.isFinite(remaining)) {
      infinite = true;
    } else {
      total += remaining;
    }
  });
  const display = infinite ? '∞' : formatIntegerLocalized(total);
  return { total, infinite, display };
}

function isBigBangUnlocked() {
  const { total, infinite } = getBigBangRemainingLevels();
  return !infinite && total <= 0;
}

function getBigBangBonusStepDisplay() {
  return formatIntegerLocalized(BIG_BANG_LEVEL_BONUS_STEP);
}

function canPerformBigBang() {
  return isBigBangUnlocked();
}

function updateBigBangActionUI() {
  const bonusTotal = getBigBangLevelBonus();
  const bonusDisplay = formatIntegerLocalized(bonusTotal);
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  if (elements.bigBangSummary) {
    const summaryFallback = `Réinitialise vos atomes actuels ainsi que les deux pistes du magasin pour augmenter leur plafond de +${bonusStepDisplay} niveaux.`;
    const summaryText = translateOrDefault(
      'index.sections.bigbang.restart.summary',
      summaryFallback,
      { bonus: bonusStepDisplay }
    );
    elements.bigBangSummary.textContent = summaryText;
  }
  if (elements.bigBangBonusInfo) {
    const fallbackBonus = `Niveaux supplémentaires débloqués : +${bonusDisplay}`;
    elements.bigBangBonusInfo.textContent = translateOrDefault(
      'index.sections.bigbang.restart.bonus',
      fallbackBonus,
      { bonus: bonusDisplay }
    );
  }
  if (elements.bigBangRequirement) {
    const remainingInfo = getBigBangRemainingLevels();
    const ready = canPerformBigBang();
    const [firstShopName = 'Doigt créateur', secondShopName = 'Cœur d’étoile'] = getBigBangRequiredShopNames();
    const key = ready
      ? 'index.sections.bigbang.restart.requirementReady'
      : 'index.sections.bigbang.restart.requirementLocked';
    const fallback = ready
      ? `${firstShopName} et ${secondShopName} sont au maximum ! Lancez un nouveau Big Bang pour ajouter +${bonusStepDisplay} niveaux au magasin.`
      : `Montez ${firstShopName} et ${secondShopName} au maximum (${remainingInfo.display} niveaux restants) pour déclencher un nouveau Big Bang.`;
    const message = translateOrDefault(key, fallback, {
      remaining: remainingInfo.display,
      bonus: bonusStepDisplay,
      firstShop: firstShopName,
      secondShop: secondShopName
    });
    elements.bigBangRequirement.textContent = message;
    if (elements.bigBangRestartButton) {
      elements.bigBangRestartButton.title = message;
      const actionLabel = translateOrDefault(
        'index.sections.bigbang.restart.cta',
        elements.bigBangRestartButton.textContent?.trim() || 'Trigger the Big Bang'
      );
      elements.bigBangRestartButton.setAttribute('aria-label', `${actionLabel} — ${message}`);
    }
  }
  if (elements.bigBangRestartButton) {
    const ready = canPerformBigBang();
    elements.bigBangRestartButton.disabled = !ready;
    elements.bigBangRestartButton.setAttribute('aria-disabled', ready ? 'false' : 'true');
  }
  updateBigBangDialogCopy();
}

function updateBigBangDialogCopy() {
  if (!elements.bigBangDialog) {
    return;
  }
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  if (elements.bigBangDialogTitle) {
    elements.bigBangDialogTitle.textContent = translateOrDefault(
      'index.sections.bigbang.confirm.title',
      'Confirmer le Big Bang'
    );
  }
  if (elements.bigBangDialogMessage) {
    const fallback = `Relancer l’univers ? Vous perdrez vos atomes actuels, vos APC/APS et les niveaux du magasin, mais débloquerez +${bonusStepDisplay} niveaux supplémentaires.`;
    elements.bigBangDialogMessage.textContent = translateOrDefault(
      'index.sections.bigbang.confirm.message',
      fallback,
      { bonus: bonusStepDisplay }
    );
    elements.bigBangDialog.setAttribute('aria-describedby', 'bigBangDialogMessage');
  } else {
    elements.bigBangDialog.removeAttribute('aria-describedby');
  }
  if (elements.bigBangDialogCancel) {
    elements.bigBangDialogCancel.textContent = translateOrDefault(
      'index.sections.bigbang.confirm.cancel',
      'Annuler'
    );
  }
  if (elements.bigBangDialogConfirm) {
    const actionLabel = translateOrDefault(
      'index.sections.bigbang.confirm.confirm',
      'Déclencher le Big Bang'
    );
    elements.bigBangDialogConfirm.textContent = actionLabel;
    elements.bigBangDialogConfirm.setAttribute('aria-label', actionLabel);
  }
}

function getBigBangDialogFocusableElements() {
  if (!elements.bigBangDialog) {
    return [];
  }
  return Array.from(
    elements.bigBangDialog.querySelectorAll(BIG_BANG_DIALOG_FOCUSABLE_SELECTOR)
  ).filter(node => node instanceof HTMLElement && !node.hasAttribute('disabled') && !node.hidden);
}

function handleBigBangDialogKeydown(event) {
  if (!bigBangDialogState.isOpen) {
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    closeBigBangDialog({ cancelled: true });
    return;
  }
  if (event.key !== 'Tab') {
    return;
  }
  const focusable = getBigBangDialogFocusableElements();
  if (!focusable.length) {
    event.preventDefault();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document && document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  if (event.shiftKey) {
    if (!active || active === first) {
      event.preventDefault();
      last.focus();
    }
  } else if (active === last) {
    event.preventDefault();
    first.focus();
  }
}

function openBigBangDialog() {
  if (
    !elements.bigBangDialog
    || !elements.bigBangDialogConfirm
    || !elements.bigBangDialogCancel
  ) {
    return false;
  }
  if (bigBangDialogState.isOpen) {
    return true;
  }
  bigBangDialogState.isOpen = true;
  bigBangDialogState.previousFocus = document && document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  updateBigBangDialogCopy();
  elements.bigBangDialog.hidden = false;
  document.addEventListener('keydown', handleBigBangDialogKeydown);
  requestAnimationFrame(() => {
    const focusTarget = elements.bigBangDialogConfirm || elements.bigBangDialogCancel;
    if (focusTarget && typeof focusTarget.focus === 'function') {
      focusTarget.focus();
    }
  });
  return true;
}

function closeBigBangDialog({ cancelled = false } = {}) {
  if (!bigBangDialogState.isOpen) {
    return;
  }
  bigBangDialogState.isOpen = false;
  if (elements.bigBangDialog) {
    elements.bigBangDialog.hidden = true;
  }
  document.removeEventListener('keydown', handleBigBangDialogKeydown);
  const { previousFocus } = bigBangDialogState;
  bigBangDialogState.previousFocus = null;
  const focusTarget = previousFocus && typeof previousFocus.focus === 'function'
    ? previousFocus
    : elements.bigBangRestartButton && typeof elements.bigBangRestartButton.focus === 'function'
      ? elements.bigBangRestartButton
      : null;
  if (focusTarget) {
    focusTarget.focus();
  }
  if (cancelled) {
    showToast(translateOrDefault('scripts.app.bigBang.cancelled', 'Big Bang annulé'));
  }
}

function handleBigBangDialogConfirm(event) {
  event.preventDefault();
  if (!canPerformBigBang()) {
    closeBigBangDialog();
    const remainingInfo = getBigBangRemainingLevels();
    const [firstShopName = 'Doigt créateur', secondShopName = 'Cœur d’étoile'] = getBigBangRequiredShopNames();
    const fallback = `Montez ${firstShopName} et ${secondShopName} au maximum (${remainingInfo.display} niveaux restants) pour relancer l’univers.`;
    showToast(translateOrDefault(
      'scripts.app.bigBang.notReady',
      fallback,
      { remaining: remainingInfo.display, firstShop: firstShopName, secondShop: secondShopName }
    ));
    return;
  }
  closeBigBangDialog();
  performBigBang();
}

function handleBigBangDialogCancel(event) {
  event.preventDefault();
  closeBigBangDialog({ cancelled: true });
}

function handleBigBangDialogBackdrop(event) {
  if (!elements.bigBangDialog || event.target !== elements.bigBangDialog) {
    return;
  }
  event.preventDefault();
  closeBigBangDialog({ cancelled: true });
}

function handleBigBangRestart() {
  if (!canPerformBigBang()) {
    const remainingInfo = getBigBangRemainingLevels();
    const [firstShopName = 'Doigt créateur', secondShopName = 'Cœur d’étoile'] = getBigBangRequiredShopNames();
    const fallback = `Montez ${firstShopName} et ${secondShopName} au maximum (${remainingInfo.display} niveaux restants) pour relancer l’univers.`;
    showToast(translateOrDefault(
      'scripts.app.bigBang.notReady',
      fallback,
      { remaining: remainingInfo.display, firstShop: firstShopName, secondShop: secondShopName }
    ));
    return;
  }
  if (openBigBangDialog()) {
    return;
  }
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  const confirmMessage = translateOrDefault(
    'scripts.app.bigBang.confirm',
    `Relancer l’univers ? Vous perdrez vos atomes actuels et les niveaux du magasin, mais gagnerez +${bonusStepDisplay} niveaux maximum.`,
    { bonus: bonusStepDisplay }
  );
  const confirmFn = typeof window !== 'undefined' && typeof window.confirm === 'function'
    ? window.confirm
    : null;
  if (confirmFn && !confirmFn(confirmMessage)) {
    showToast(translateOrDefault('scripts.app.bigBang.cancelled', 'Big Bang annulé'));
    return;
  }
  performBigBang();
}

function performBigBang() {
  const totalBonus = setBigBangLevelBonus(getBigBangLevelBonus() + BIG_BANG_LEVEL_BONUS_STEP);
  resetPendingProductionGains();
  gameState.atoms = LayeredNumber.zero();
  gameState.perClick = BASE_PER_CLICK.clone();
  gameState.perSecond = BASE_PER_SECOND.clone();
  gameState.basePerClick = BASE_PER_CLICK.clone();
  gameState.basePerSecond = BASE_PER_SECOND.clone();
  gameState.upgrades = {};
  gameState.shopUnlocks = new Set();
  recalcProduction();
  renderShop();
  invalidateFeatureUnlockCache();
  updateBigBangActionUI();
  updateUI();
  saveGame();
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  const totalDisplay = formatIntegerLocalized(totalBonus);
  const fallback = `Big Bang réussi ! Cap du magasin augmenté de +${bonusStepDisplay} (total +${totalDisplay}).`;
  showToast(translateOrDefault('scripts.app.bigBang.done', fallback, {
    bonus: bonusStepDisplay,
    total: totalDisplay
  }));
}

function resetPendingProductionGains(now = getLoopTimestamp()) {
  performanceModeState.pendingManualGain = null;
  performanceModeState.pendingAutoGain = null;
  performanceModeState.autoAccumulatedMs = 0;
  performanceModeState.lastManualFlush = now;
  performanceModeState.lastAutoFlush = now;
}

function updateBigBangVisibility() {
  const unlocked = isBigBangUnlocked();
  if (!unlocked && gameState.bigBangButtonVisible) {
    gameState.bigBangButtonVisible = false;
  }
  if (unlocked && !lastBigBangUnlockedState && !gameState.bigBangButtonVisible) {
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
  lastBigBangUnlockedState = unlocked;
  updateBigBangActionUI();
}

function isArcadeUnlocked() {
  return isFeatureUnlocked('arcade.hub');
}

function triggerBrandPortalPulse() {
  const pulseTarget = elements.brandPortal || elements.navArcadeButton;
  if (!pulseTarget) {
    return;
  }
  const pulseClass = pulseTarget === elements.brandPortal ? 'brand--pulse' : 'nav-button--pulse';
  pulseTarget.classList.add(pulseClass);
  clearTimeout(triggerBrandPortalPulse.timeoutId);
  triggerBrandPortalPulse.timeoutId = setTimeout(() => {
    if (elements.brandPortal) {
      elements.brandPortal.classList.remove('brand--pulse');
    }
    if (elements.navArcadeButton) {
      elements.navArcadeButton.classList.remove('nav-button--pulse');
    }
  }, 1600);
}

function updateArcadeTicketDisplay() {
  const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const ticketLabel = formatTicketLabel(available);
  if (elements.arcadeTicketValues?.length) {
    elements.arcadeTicketValues.forEach(valueElement => {
      valueElement.textContent = ticketLabel;
    });
  }
  if (elements.arcadeTicketButtons?.length) {
    const gachaUnlocked = isPageUnlocked('gacha');
    elements.arcadeTicketButtons.forEach(button => {
      button.disabled = !gachaUnlocked;
      button.setAttribute('aria-disabled', gachaUnlocked ? 'false' : 'true');
      if (gachaUnlocked) {
        button.setAttribute('aria-label', `Ouvrir le portail Gacha (${ticketLabel})`);
        button.title = `Tickets disponibles : ${ticketLabel}`;
      } else {
        button.setAttribute('aria-label', 'Portail Gacha verrouillé');
        button.title = 'Obtenez un ticket de tirage pour débloquer le portail Gacha';
      }
    });
  }
  const bonusCount = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
  const bonusValue = formatIntegerLocalized(bonusCount);
  const bonusLabel = formatMetauxCreditLabel(bonusCount);
  if (elements.arcadeBonusTicketValues?.length) {
    elements.arcadeBonusTicketValues.forEach(valueElement => {
      valueElement.textContent = bonusValue;
    });
  }
  if (elements.arcadeBonusTicketAnnouncements?.length) {
    elements.arcadeBonusTicketAnnouncements.forEach(announcement => {
      announcement.textContent = `Mach3 : ${bonusLabel}`;
    });
  }
  if (elements.arcadeBonusTicketButtons?.length) {
    const hasCredits = bonusCount > 0;
    elements.arcadeBonusTicketButtons.forEach(button => {
      button.disabled = !hasCredits;
      button.setAttribute('aria-disabled', hasCredits ? 'false' : 'true');
      if (hasCredits) {
        button.title = `Lancer Métaux — ${bonusLabel}`;
        button.setAttribute('aria-label', `Ouvrir Métaux (Mach3 : ${bonusLabel})`);
      } else {
        button.title = 'Attrapez un graviton pour gagner un Mach3.';
        button.setAttribute('aria-label', 'Mach3 indisponible — attrapez un graviton.');
      }
    });
  }
  updateMetauxCreditsUI();
}

function getMetauxCreditCount() {
  return Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
}

function formatMetauxCreditLabel(count) {
  if (typeof formatBonusTicketLabel === 'function') {
    return formatBonusTicketLabel(count);
  }
  const numeric = Math.max(0, Math.floor(Number(count) || 0));
  const unit = numeric === 1 ? 'crédit' : 'crédits';
  return `${formatIntegerLocalized(numeric)} ${unit}`;
}

function isMetauxSessionRunning() {
  if (!metauxGame) {
    return false;
  }
  if (typeof metauxGame.isSessionRunning === 'function') {
    return metauxGame.isSessionRunning();
  }
  return metauxGame.gameOver === false;
}

function updateMetauxCreditsUI() {
  const available = getMetauxCreditCount();
  const active = isMetauxSessionRunning();
  if (elements.metauxNewGameCredits) {
    elements.metauxNewGameCredits.textContent = `Mach3 : ${formatMetauxCreditLabel(available)}`;
  }
  if (elements.metauxNewGameButton) {
    const canStart = available > 0 && !active;
    elements.metauxNewGameButton.disabled = !canStart;
    elements.metauxNewGameButton.setAttribute('aria-disabled', canStart ? 'false' : 'true');
    const tooltip = canStart
      ? `Consomme 1 crédit Mach3 (restant : ${formatMetauxCreditLabel(available)}).`
      : available > 0
        ? 'Partie en cours… Terminez-la avant de relancer.'
        : 'Aucun crédit Mach3 disponible. Jouez à Atom2Univers pour en gagner.';
    elements.metauxNewGameButton.title = tooltip;
    elements.metauxNewGameButton.setAttribute('aria-label', `${available > 0 ? 'Nouvelle partie' : 'Crédit indisponible'} — ${tooltip}`);
  }
  if (elements.metauxFreePlayButton) {
    const disabled = active;
    elements.metauxFreePlayButton.disabled = disabled;
    elements.metauxFreePlayButton.setAttribute('aria-disabled', disabled ? 'true' : 'false');
    const tooltip = disabled
      ? 'Partie en cours… Terminez-la avant de relancer.'
      : 'Partie libre : aucun crédit requis.';
    elements.metauxFreePlayButton.title = tooltip;
    const label = (elements.metauxFreePlayButton.textContent || '').trim() || 'Free Play';
    elements.metauxFreePlayButton.setAttribute('aria-label', `${label} — ${tooltip}`);
  }
  if (elements.metauxCreditStatus) {
    let statusText = '';
    const freeMode = metauxGame && typeof metauxGame.isFreePlayMode === 'function' && metauxGame.isFreePlayMode();
    if (active) {
      statusText = freeMode
        ? 'Partie libre en cours — expérimentez sans pression.'
        : 'Forge en cours… Utilisez vos déplacements pour créer des alliages !';
    } else if (available > 0) {
      statusText = `Crédits disponibles : ${formatMetauxCreditLabel(available)}.`;
    } else {
      statusText = 'Aucun crédit Mach3 disponible. Lancez une partie libre ou jouez à Atom2Univers pour en gagner.';
    }
    elements.metauxCreditStatus.textContent = statusText;
    elements.metauxCreditStatus.hidden = false;
  }
}

window.updateMetauxCreditsUI = updateMetauxCreditsUI;
window.registerSudokuOfflineBonus = registerSudokuOfflineBonus;
window.registerChessVictoryReward = registerChessVictoryReward;

function updateBrandPortalState(options = {}) {
  const unlocked = isArcadeUnlocked();
  const wasUnlocked = lastArcadeUnlockState === true;
  const justUnlocked = !wasUnlocked && unlocked;
  lastArcadeUnlockState = unlocked;
  if (elements.brandPortal) {
    elements.brandPortal.disabled = !unlocked;
    elements.brandPortal.setAttribute('aria-disabled', unlocked ? 'false' : 'true');
    elements.brandPortal.classList.toggle('brand--locked', !unlocked);
    elements.brandPortal.classList.toggle('brand--portal-ready', unlocked);
  }
  if (elements.navArcadeButton) {
    setNavButtonLockState(elements.navArcadeButton, unlocked);
    if (!unlocked) {
      elements.navArcadeButton.classList.remove('nav-button--pulse');
    } else {
      updateArcadeTicketDisplay();
      if (options.animate || justUnlocked) {
        triggerBrandPortalPulse();
      }
    }
  } else if (unlocked) {
    updateArcadeTicketDisplay();
  }
  updateArcadeHubLocks();
  if (justUnlocked) {
    refreshOptionsWelcomeContent();
    updateBrickSkinOption();
  }
}


const soundEffects = (() => {
  let popMuted = false;
  const createSilentPool = () => ({ play: () => {} });
  if (typeof window === 'undefined' || typeof Audio === 'undefined') {
    return {
      pop: createSilentPool(),
      crit: createSilentPool(),
      coin: createSilentPool(),
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
  const COIN_SOUND_SRC = 'Assets/Sounds/coin.mp3';
  const COIN_MAX_PLAYS = 10;
  const COIN_INTERVAL_MS = 250;
  const CRIT_PLAYBACK_RATE = 1.35;

  const popPool = createSoundPool(POP_SOUND_SRC, 6);
  const critPool = createSoundPool(POP_SOUND_SRC, 3);
  const coinPool = createSoundPool(COIN_SOUND_SRC, 4);
  const coinTimeouts = new Set();

  const scheduleCoinPlayback = times => {
    coinTimeouts.forEach(timeoutId => {
      clearTimeout(timeoutId);
    });
    coinTimeouts.clear();

    const numericTimes = Number(times);
    const target = Number.isFinite(numericTimes) ? numericTimes : 0;
    const repetitions = Math.min(COIN_MAX_PLAYS, Math.max(0, Math.floor(target)));

    if (repetitions <= 0) {
      return;
    }

    for (let index = 0; index < repetitions; index += 1) {
      const timeoutId = setTimeout(() => {
        coinPool.play(1);
        coinTimeouts.delete(timeoutId);
      }, index * COIN_INTERVAL_MS);
      coinTimeouts.add(timeoutId);
    }
  };

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
    coin: {
      play: times => {
        if (popMuted) {
          return;
        }
        scheduleCoinPlayback(times == null ? 1 : times);
      }
    },
    setPopMuted(value) {
      popMuted = !!value;
      if (popMuted) {
        coinTimeouts.forEach(timeoutId => {
          clearTimeout(timeoutId);
        });
        coinTimeouts.clear();
      }
    },
    isPopMuted() {
      return popMuted;
    }
  };

  return effects;
})();

onGachaTicketsIncrease(delta => {
  if (!delta || delta <= 0) {
    return;
  }
  if (soundEffects && soundEffects.coin && typeof soundEffects.coin.play === 'function') {
    soundEffects.coin.play(delta);
  }
});

function readStoredClickSoundMuted() {
  try {
    const stored = globalThis.localStorage?.getItem(CLICK_SOUND_STORAGE_KEY);
    if (stored == null) {
      return null;
    }
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read click sound preference', error);
  }
  return null;
}

function writeStoredClickSoundMuted(muted) {
  try {
    const value = muted ? '1' : '0';
    globalThis.localStorage?.setItem(CLICK_SOUND_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist click sound preference', error);
  }
}

function readStoredCritAtomVisualsDisabled() {
  try {
    const stored = globalThis.localStorage?.getItem(CRIT_ATOM_VISUALS_STORAGE_KEY);
    if (stored == null) {
      return null;
    }
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read critical atom visual preference', error);
  }
  return null;
}

function writeStoredCritAtomVisualsDisabled(disabled) {
  try {
    const value = disabled ? '1' : '0';
    globalThis.localStorage?.setItem(CRIT_ATOM_VISUALS_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist critical atom visual preference', error);
  }
}

function readStoredScreenWakeLockEnabled() {
  try {
    const stored = globalThis.localStorage?.getItem(SCREEN_WAKE_LOCK_STORAGE_KEY);
    if (stored == null) {
      return null;
    }
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read screen wake lock preference', error);
  }
  return null;
}

function writeStoredScreenWakeLockEnabled(enabled) {
  try {
    const value = enabled ? '1' : '0';
    globalThis.localStorage?.setItem(SCREEN_WAKE_LOCK_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist screen wake lock preference', error);
  }
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

function readStoredAtomAnimationsEnabled() {
  try {
    const stored = globalThis.localStorage?.getItem(ATOM_ANIMATION_PREFERENCE_STORAGE_KEY);
    if (stored == null) {
      return null;
    }
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read atom animation preference', error);
  }
  return null;
}

function writeStoredAtomAnimationsEnabled(enabled) {
  try {
    const value = enabled ? '1' : '0';
    globalThis.localStorage?.setItem(ATOM_ANIMATION_PREFERENCE_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist atom animation preference', error);
  }
}

function areAtomAnimationsEnabled() {
  return atomAnimationsEnabled !== false;
}

function isAtomAnimationSuppressed() {
  return !areAtomAnimationsEnabled();
}

function updateAtomAnimationStatusLabel(enabled) {
  if (!elements.atomAnimationToggleStatus) {
    return;
  }
  const key = enabled
    ? 'index.sections.options.atomAnimation.state.on'
    : 'index.sections.options.atomAnimation.state.off';
  const fallback = enabled ? 'Animations enabled' : 'Animations minimized';
  elements.atomAnimationToggleStatus.setAttribute('data-i18n', key);
  elements.atomAnimationToggleStatus.textContent = translateOrDefault(key, fallback);
}

function syncAtomAnimationPreferenceEffects() {
  clickHistory.length = 0;
  targetClickStrength = 0;
  displayedClickStrength = 0;
  cancelMinimalAtomShakeCue();
  resetAtomSpringStyles();
  resetAtomAnimationState();
  resetGlowEffects();
}

function applyAtomAnimationPreference(enabled, options = {}) {
  const previousValue = areAtomAnimationsEnabled();
  const settings = Object.assign({ persist: true, updateControl: true, force: false }, options);
  const nextValue = enabled !== false;
  atomAnimationsEnabled = nextValue;
  if (settings.updateControl && elements.atomAnimationToggle) {
    elements.atomAnimationToggle.checked = nextValue;
  }
  updateAtomAnimationStatusLabel(nextValue);
  if (settings.force || nextValue !== previousValue) {
    syncAtomAnimationPreferenceEffects();
  }
  if (settings.persist) {
    writeStoredAtomAnimationsEnabled(nextValue);
  }
  return nextValue;
}

function initAtomAnimationOption() {
  const stored = readStoredAtomAnimationsEnabled();
  const initialEnabled = stored === null ? true : stored === true;
  applyAtomAnimationPreference(initialEnabled, {
    persist: false,
    updateControl: Boolean(elements.atomAnimationToggle),
    force: true
  });
  if (!elements.atomAnimationToggle) {
    return;
  }
  elements.atomAnimationToggle.addEventListener('change', () => {
    const enabled = elements.atomAnimationToggle.checked;
    applyAtomAnimationPreference(enabled, { persist: true, updateControl: false });
  });
}

function subscribeAtomAnimationLanguageUpdates() {
  const handler = () => {
    updateAtomAnimationStatusLabel(areAtomAnimationsEnabled());
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

function detectScreenWakeLockSupport() {
  const bridge = getAndroidSystemBridge();
  const supported = Boolean(bridge && typeof bridge.setScreenAwake === 'function');
  screenWakeLockState.supported = supported;
  if (!supported) {
    screenWakeLockState.enabled = false;
  }
  if (elements.screenWakeLockToggleCard) {
    if (supported) {
      elements.screenWakeLockToggleCard.removeAttribute('hidden');
      elements.screenWakeLockToggleCard.removeAttribute('aria-hidden');
    } else {
      elements.screenWakeLockToggleCard.setAttribute('hidden', '');
      elements.screenWakeLockToggleCard.setAttribute('aria-hidden', 'true');
    }
  }
  if (elements.screenWakeLockToggle) {
    elements.screenWakeLockToggle.disabled = !supported;
  }
  return supported;
}

function updateScreenWakeLockStatusLabel(enabled) {
  if (!elements.screenWakeLockToggleStatus) {
    return;
  }
  const key = enabled
    ? 'index.sections.options.screenWakeLock.state.on'
    : 'index.sections.options.screenWakeLock.state.off';
  const fallback = enabled ? 'Enabled' : 'Disabled';
  elements.screenWakeLockToggleStatus.setAttribute('data-i18n', key);
  elements.screenWakeLockToggleStatus.textContent = translateOrDefault(key, fallback);
}

function setNativeScreenWakeLockEnabled(enabled) {
  const bridge = getAndroidSystemBridge();
  if (!bridge || typeof bridge.setScreenAwake !== 'function') {
    return false;
  }
  try {
    const result = bridge.setScreenAwake(!!enabled);
    if (typeof result === 'boolean') {
      return result;
    }
    return !!enabled;
  } catch (error) {
    console.warn('Unable to update screen wake lock state', error);
  }
  return false;
}

function applyScreenWakeLockEnabled(enabled, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  let applied = false;
  if (screenWakeLockState.supported) {
    applied = setNativeScreenWakeLockEnabled(!!enabled);
  }
  screenWakeLockState.enabled = applied;
  if (settings.updateControl && elements.screenWakeLockToggle) {
    elements.screenWakeLockToggle.checked = applied;
  }
  updateScreenWakeLockStatusLabel(applied);
  if (settings.persist) {
    writeStoredScreenWakeLockEnabled(applied);
  }
  return applied;
}

function initScreenWakeLockOption() {
  if (!elements.screenWakeLockToggleCard) {
    return;
  }
  detectScreenWakeLockSupport();
  updateScreenWakeLockStatusLabel(false);
  if (!screenWakeLockState.supported) {
    if (elements.screenWakeLockToggle) {
      elements.screenWakeLockToggle.checked = false;
    }
    return;
  }
  const stored = readStoredScreenWakeLockEnabled();
  const initialEnabled = stored === true;
  applyScreenWakeLockEnabled(initialEnabled, { persist: false, updateControl: true });
  if (!elements.screenWakeLockToggle) {
    return;
  }
  elements.screenWakeLockToggle.addEventListener('change', () => {
    const requested = elements.screenWakeLockToggle.checked;
    const applied = applyScreenWakeLockEnabled(requested, { persist: true, updateControl: false });
    if (requested !== applied) {
      elements.screenWakeLockToggle.checked = applied;
    }
  });
}

function subscribeScreenWakeLockLanguageUpdates() {
  const handler = () => {
    updateScreenWakeLockStatusLabel(screenWakeLockState.enabled);
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

function readStoredFrenzyAutoCollectEnabled() {
  try {
    const stored = globalThis.localStorage?.getItem(FRENZY_AUTO_COLLECT_STORAGE_KEY);
    if (stored == null) {
      return null;
    }
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read frenzy auto-collect preference', error);
  }
  return null;
}

function writeStoredFrenzyAutoCollectEnabled(enabled) {
  try {
    const value = enabled ? '1' : '0';
    globalThis.localStorage?.setItem(FRENZY_AUTO_COLLECT_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist frenzy auto-collect preference', error);
  }
}

function isFrenzyAutoCollectFeatureUnlocked() {
  return getUnlockedTrophySet().has(FRENZY_AUTO_COLLECT_TROPHY_ID);
}

function isFrenzyAutoCollectActive() {
  return frenzyAutoCollectPreference && isFrenzyAutoCollectFeatureUnlocked();
}

function updateFrenzyAutoCollectStatusLabel(active) {
  if (!elements.frenzyAutoCollectStatus) {
    return;
  }
  const key = active
    ? 'index.sections.options.frenzyAutoCollect.state.on'
    : 'index.sections.options.frenzyAutoCollect.state.off';
  const fallback = active ? 'Enabled' : 'Disabled';
  elements.frenzyAutoCollectStatus.setAttribute('data-i18n', key);
  elements.frenzyAutoCollectStatus.textContent = translateOrDefault(key, fallback);
}

function applyFrenzyAutoCollectEnabled(enabled, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true, updateTimers: true }, options);
  frenzyAutoCollectPreference = !!enabled;
  if (settings.updateControl && elements.frenzyAutoCollectToggle) {
    elements.frenzyAutoCollectToggle.checked = frenzyAutoCollectPreference;
  }
  const active = isFrenzyAutoCollectActive();
  updateFrenzyAutoCollectStatusLabel(active);
  if (settings.persist) {
    writeStoredFrenzyAutoCollectEnabled(frenzyAutoCollectPreference);
  }
  if (settings.updateTimers !== false) {
    FRENZY_TYPES.forEach(type => {
      if (active) {
        scheduleFrenzyAutoCollect(type);
      } else {
        cancelFrenzyAutoCollect(type);
      }
    });
  }
}

function initFrenzyAutoCollectOption() {
  const stored = readStoredFrenzyAutoCollectEnabled();
  const initialPreference = stored === null ? false : stored === true;
  applyFrenzyAutoCollectEnabled(initialPreference, { persist: false, updateControl: true });
  if (elements.frenzyAutoCollectToggle) {
    elements.frenzyAutoCollectToggle.addEventListener('change', () => {
      const enabled = elements.frenzyAutoCollectToggle.checked;
      applyFrenzyAutoCollectEnabled(enabled, { persist: true, updateControl: false });
    });
  }
  updateFrenzyAutoCollectOptionVisibility();
}

function subscribeFrenzyAutoCollectLanguageUpdates() {
  const handler = () => {
    updateFrenzyAutoCollectStatusLabel(isFrenzyAutoCollectActive());
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

function updateFrenzyAutoCollectOptionVisibility() {
  const unlocked = isFrenzyAutoCollectFeatureUnlocked();
  if (elements.frenzyAutoCollectCard) {
    elements.frenzyAutoCollectCard.hidden = !unlocked;
    elements.frenzyAutoCollectCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }
  if (elements.frenzyAutoCollectToggle) {
    elements.frenzyAutoCollectToggle.disabled = !unlocked;
  }
  if (lastFrenzyAutoCollectUnlockedState !== unlocked) {
    lastFrenzyAutoCollectUnlockedState = unlocked;
    applyFrenzyAutoCollectEnabled(frenzyAutoCollectPreference, { persist: false, updateControl: true });
  } else {
    updateFrenzyAutoCollectStatusLabel(isFrenzyAutoCollectActive());
  }
}

function buildCryptoWidgetUrl(endpoint) {
  if (typeof endpoint === 'string' && /^https?:\/\//i.test(endpoint)) {
    return endpoint;
  }
  const normalized = typeof endpoint === 'string' ? endpoint.trim() : '';
  if (!normalized) {
    return CRYPTO_WIDGET_BASE_URL;
  }
  if (normalized.startsWith('/')) {
    return `${CRYPTO_WIDGET_BASE_URL}${normalized}`;
  }
  return `${CRYPTO_WIDGET_BASE_URL}/${normalized}`;
}

function getCryptoWidgetNumberFormatter() {
  const api = getI18nApi();
  const locale = api && typeof api.getCurrentLanguage === 'function'
    ? api.getCurrentLanguage()
    : undefined;
  if (cryptoWidgetNumberFormat && cryptoWidgetNumberFormatLocale === locale) {
    return cryptoWidgetNumberFormat;
  }
  if (typeof Intl === 'undefined' || typeof Intl.NumberFormat !== 'function') {
    return null;
  }
  cryptoWidgetNumberFormat = new Intl.NumberFormat(locale || undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  cryptoWidgetNumberFormatLocale = locale || undefined;
  return cryptoWidgetNumberFormat;
}

function resetCryptoWidgetNumberFormat() {
  cryptoWidgetNumberFormat = null;
  cryptoWidgetNumberFormatLocale = null;
}

function formatCryptoWidgetPrice(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return CRYPTO_WIDGET_PLACEHOLDER;
  }
  const formatter = getCryptoWidgetNumberFormatter();
  if (formatter) {
    try {
      return formatter.format(numeric);
    } catch (error) {
      // Ignore formatting errors and fall back to fixed decimals.
    }
  }
  return numeric.toFixed(2);
}

function getCryptoWidgetFormattedPriceParts(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  const formatter = getCryptoWidgetNumberFormatter();
  if (formatter && typeof formatter.formatToParts === 'function') {
    const majorParts = [];
    const fractionParts = [];
    const suffixParts = [];
    let decimalSeparator = '';
    formatter.formatToParts(numeric).forEach(part => {
      switch (part.type) {
        case 'integer':
        case 'group':
        case 'minusSign':
        case 'plusSign':
          majorParts.push(part.value);
          break;
        case 'decimal':
          decimalSeparator = part.value;
          break;
        case 'fraction':
          fractionParts.push(part.value);
          break;
        default:
          suffixParts.push(part.value);
          break;
      }
    });
    return {
      major: majorParts.join('') || '0',
      minor: fractionParts.length ? `${decimalSeparator}${fractionParts.join('')}` : '',
      suffix: suffixParts.join('')
    };
  }
  const fallback = numeric.toFixed(2);
  const [major, minor] = fallback.split('.');
  return {
    major: major || '0',
    minor: minor ? `.${minor}` : '',
    suffix: ''
  };
}

function getCryptoWidgetTrend(current, previous) {
  const currentValue = Number(current);
  const previousValue = Number(previous);
  if (!Number.isFinite(currentValue) || !Number.isFinite(previousValue)) {
    return null;
  }
  if (currentValue > previousValue) {
    return 'up';
  }
  if (currentValue < previousValue) {
    return 'down';
  }
  return null;
}

function renderCryptoWidgetAmount(element, value, previousValue) {
  if (!element) {
    return;
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    element.textContent = CRYPTO_WIDGET_PLACEHOLDER;
    return;
  }
  const parts = getCryptoWidgetFormattedPriceParts(numeric);
  if (!parts || typeof document === 'undefined' || typeof element.replaceChildren !== 'function') {
    element.textContent = formatCryptoWidgetPrice(numeric);
    return;
  }
  const fragment = document.createDocumentFragment();
  const trend = getCryptoWidgetTrend(numeric, previousValue);
  const majorSpan = document.createElement('span');
  majorSpan.className = 'crypto-widget__amount-major';
  if (trend === 'up') {
    majorSpan.classList.add('is-up');
  } else if (trend === 'down') {
    majorSpan.classList.add('is-down');
  }
  majorSpan.textContent = parts.major;
  fragment.append(majorSpan);
  if (parts.minor) {
    const minorSpan = document.createElement('span');
    minorSpan.className = 'crypto-widget__amount-minor';
    minorSpan.textContent = parts.minor;
    fragment.append(minorSpan);
  }
  if (parts.suffix) {
    fragment.append(parts.suffix);
  }
  element.replaceChildren(fragment);
}

function updateCryptoWidgetPriceDisplay() {
  renderCryptoWidgetAmount(
    elements.cryptoWidgetBtcValue,
    cryptoWidgetState.btcPriceUsd,
    cryptoWidgetState.previousBtcPriceUsd
  );
  renderCryptoWidgetAmount(
    elements.cryptoWidgetEthValue,
    cryptoWidgetState.ethPriceUsd,
    cryptoWidgetState.previousEthPriceUsd
  );
}

function updateCryptoWidgetVisibility() {
  if (!elements.cryptoWidget) {
    return;
  }
  if (cryptoWidgetState.isWidgetEnabled) {
    elements.cryptoWidget.removeAttribute('hidden');
    elements.cryptoWidget.setAttribute('aria-hidden', 'false');
  } else {
    elements.cryptoWidget.setAttribute('hidden', '');
    elements.cryptoWidget.setAttribute('aria-hidden', 'true');
  }
}

function updateCryptoWidgetStatusMessage() {
  if (!elements.cryptoWidgetStatus) {
    return;
  }
  if (!cryptoWidgetState.isWidgetEnabled) {
    elements.cryptoWidgetStatus.textContent = '';
    elements.cryptoWidgetStatus.setAttribute('hidden', '');
    elements.cryptoWidgetStatus.removeAttribute('data-i18n');
    return;
  }
  if (cryptoWidgetState.isLoading) {
    const fallback = 'Loading prices…';
    elements.cryptoWidgetStatus.setAttribute('data-i18n', CRYPTO_WIDGET_LOADING_KEY);
    elements.cryptoWidgetStatus.textContent = translateOrDefault(CRYPTO_WIDGET_LOADING_KEY, fallback);
    elements.cryptoWidgetStatus.removeAttribute('hidden');
    return;
  }
  if (cryptoWidgetState.errorMessage) {
    const fallback = cryptoWidgetState.errorMessage;
    if (cryptoWidgetState.errorMessageKey) {
      elements.cryptoWidgetStatus.setAttribute('data-i18n', cryptoWidgetState.errorMessageKey);
      elements.cryptoWidgetStatus.textContent = translateOrDefault(
        cryptoWidgetState.errorMessageKey,
        fallback || 'Unable to refresh crypto prices right now.'
      );
    } else {
      elements.cryptoWidgetStatus.removeAttribute('data-i18n');
      elements.cryptoWidgetStatus.textContent = fallback;
    }
    elements.cryptoWidgetStatus.removeAttribute('hidden');
    return;
  }
  elements.cryptoWidgetStatus.textContent = '';
  elements.cryptoWidgetStatus.setAttribute('hidden', '');
  elements.cryptoWidgetStatus.removeAttribute('data-i18n');
}

function setCryptoWidgetError(messageKey, fallback) {
  cryptoWidgetState.errorMessageKey = typeof messageKey === 'string' ? messageKey : null;
  cryptoWidgetState.errorMessage = typeof fallback === 'string'
    ? fallback
    : 'Unable to refresh crypto prices right now.';
  updateCryptoWidgetStatusMessage();
}

function clearCryptoWidgetError() {
  cryptoWidgetState.errorMessage = null;
  cryptoWidgetState.errorMessageKey = null;
  updateCryptoWidgetStatusMessage();
}

function setCryptoWidgetLoading(value) {
  cryptoWidgetState.isLoading = !!value;
  updateCryptoWidgetStatusMessage();
}

function readStoredCryptoWidgetEnabled() {
  try {
    const stored = globalThis.localStorage?.getItem(CRYPTO_WIDGET_STORAGE_KEY);
    if (stored == null) {
      return null;
    }
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read crypto widget preference', error);
  }
  return null;
}

function writeStoredCryptoWidgetEnabled(enabled) {
  try {
    const value = enabled ? '1' : '0';
    globalThis.localStorage?.setItem(CRYPTO_WIDGET_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist crypto widget preference', error);
  }
}

function updateCryptoWidgetToggleStatusLabel(enabled) {
  if (!elements.cryptoWidgetToggleStatus) {
    return;
  }
  const key = enabled
    ? 'index.sections.options.cryptoWidget.state.on'
    : 'index.sections.options.cryptoWidget.state.off';
  const fallback = enabled ? 'Visible' : 'Hidden';
  elements.cryptoWidgetToggleStatus.setAttribute('data-i18n', key);
  elements.cryptoWidgetToggleStatus.textContent = translateOrDefault(key, fallback);
}

function stopCryptoWidgetRefresh(options = {}) {
  const settings = Object.assign({ resetState: false }, options);
  if (cryptoWidgetState.intervalId != null) {
    clearInterval(cryptoWidgetState.intervalId);
    cryptoWidgetState.intervalId = null;
  }
  if (cryptoWidgetState.abortController && typeof cryptoWidgetState.abortController.abort === 'function') {
    try {
      cryptoWidgetState.abortController.abort();
    } catch (error) {
      // Ignore abort errors.
    }
  }
  cryptoWidgetState.abortController = null;
  if (settings.resetState) {
    cryptoWidgetState.btcPriceUsd = null;
    cryptoWidgetState.previousBtcPriceUsd = null;
    cryptoWidgetState.ethPriceUsd = null;
    cryptoWidgetState.previousEthPriceUsd = null;
    cryptoWidgetState.isLoading = false;
    clearCryptoWidgetError();
    updateCryptoWidgetPriceDisplay();
  }
}

function startCryptoWidgetRefresh(options = {}) {
  if (!cryptoWidgetState.isWidgetEnabled) {
    return;
  }
  const settings = Object.assign({ immediate: true }, options);
  stopCryptoWidgetRefresh({ resetState: false });
  const intervalDelay = Math.max(1000, Math.floor(Number(CRYPTO_WIDGET_REFRESH_INTERVAL_MS) || 0));
  if (settings.immediate) {
    fetchCryptoPrices();
  }
  if (intervalDelay > 0 && typeof setInterval === 'function') {
    cryptoWidgetState.intervalId = setInterval(() => {
      fetchCryptoPrices();
    }, intervalDelay);
  }
}

async function fetchCryptoWidgetPrice(endpoint, controller) {
  const url = buildCryptoWidgetUrl(endpoint);
  const options = {};
  if (controller && controller.signal) {
    options.signal = controller.signal;
  }
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const data = await response.json();
  const price = Number.parseFloat(data?.price);
  if (!Number.isFinite(price)) {
    throw new Error('Invalid price payload');
  }
  return price;
}

async function fetchCryptoPrices() {
  if (!cryptoWidgetState.isWidgetEnabled || cryptoWidgetState.isLoading) {
    return;
  }
  clearCryptoWidgetError();
  setCryptoWidgetLoading(true);
  if (typeof fetch !== 'function') {
    setCryptoWidgetError(CRYPTO_WIDGET_ERROR_KEY, 'Unable to refresh crypto prices right now.');
    setCryptoWidgetLoading(false);
    return;
  }
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  cryptoWidgetState.abortController = controller;
  try {
    const [btcPrice, ethPrice] = await Promise.all([
      fetchCryptoWidgetPrice(CRYPTO_WIDGET_ENDPOINTS.btc, controller),
      fetchCryptoWidgetPrice(CRYPTO_WIDGET_ENDPOINTS.eth, controller)
    ]);
    cryptoWidgetState.previousBtcPriceUsd = Number.isFinite(cryptoWidgetState.btcPriceUsd)
      ? cryptoWidgetState.btcPriceUsd
      : null;
    cryptoWidgetState.btcPriceUsd = btcPrice;
    cryptoWidgetState.previousEthPriceUsd = Number.isFinite(cryptoWidgetState.ethPriceUsd)
      ? cryptoWidgetState.ethPriceUsd
      : null;
    cryptoWidgetState.ethPriceUsd = ethPrice;
    updateCryptoWidgetPriceDisplay();
    clearCryptoWidgetError();
  } catch (error) {
    if (error && error.name === 'AbortError') {
      return;
    }
    console.warn('Unable to fetch crypto prices', error);
    setCryptoWidgetError(CRYPTO_WIDGET_ERROR_KEY, 'Unable to refresh crypto prices right now.');
  } finally {
    if (cryptoWidgetState.abortController === controller) {
      cryptoWidgetState.abortController = null;
    }
    setCryptoWidgetLoading(false);
  }
}

function applyCryptoWidgetEnabled(enabled, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  cryptoWidgetState.isWidgetEnabled = !!enabled;
  if (settings.updateControl && elements.cryptoWidgetToggle) {
    elements.cryptoWidgetToggle.checked = cryptoWidgetState.isWidgetEnabled;
  }
  updateCryptoWidgetToggleStatusLabel(cryptoWidgetState.isWidgetEnabled);
  updateCryptoWidgetVisibility();
  updateCryptoWidgetPriceDisplay();
  updateCryptoWidgetStatusMessage();
  if (cryptoWidgetState.isWidgetEnabled) {
    startCryptoWidgetRefresh({ immediate: settings.immediate !== false });
  } else {
    stopCryptoWidgetRefresh({ resetState: true });
  }
  if (settings.persist) {
    writeStoredCryptoWidgetEnabled(cryptoWidgetState.isWidgetEnabled);
  }
}

function initCryptoWidgetOption() {
  const stored = readStoredCryptoWidgetEnabled();
  const initialEnabled = stored === null ? CRYPTO_WIDGET_DEFAULT_ENABLED : stored === true;
  applyCryptoWidgetEnabled(initialEnabled, { persist: false, updateControl: true });
  if (!elements.cryptoWidgetToggle) {
    return;
  }
  elements.cryptoWidgetToggle.addEventListener('change', () => {
    const enabled = elements.cryptoWidgetToggle.checked;
    applyCryptoWidgetEnabled(enabled, { persist: true, updateControl: false });
  });
}

function subscribeCryptoWidgetLanguageUpdates() {
  const handler = () => {
    resetCryptoWidgetNumberFormat();
    updateCryptoWidgetPriceDisplay();
    updateCryptoWidgetStatusMessage();
    updateCryptoWidgetToggleStatusLabel(cryptoWidgetState.isWidgetEnabled);
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


const musicPlayer = (() => {
  const MUSIC_DIR = 'Assets/Music/';
  const SUPPORTED_EXTENSIONS = MUSIC_SUPPORTED_EXTENSIONS;
  const FALLBACK_TRACKS = MUSIC_FALLBACK_TRACKS;

  const SUPPORTED_EXTENSION_PATTERN = (() => {
    const uniqueExtensions = Array.from(new Set(
      SUPPORTED_EXTENSIONS
        .map(ext => (typeof ext === 'string' ? ext.trim().toLowerCase() : ''))
        .map(ext => ext.replace(/[^a-z0-9]/g, ''))
        .filter(Boolean)
    ));
    if (!uniqueExtensions.length) {
      return /href="([^"?#]+\.(?:mp3|ogg|wav|webm|m4a))"/gi;
    }
    const pattern = uniqueExtensions.join('|');
    return new RegExp(`href="([^"?#]+\\.(?:${pattern}))"`, 'gi');
  })();

  if (typeof window === 'undefined' || typeof Audio === 'undefined') {
    const resolved = Promise.resolve([]);
    let stubVolume = DEFAULT_MUSIC_VOLUME;
    return {
      init: options => {
        if (options && typeof options.volume === 'number') {
          stubVolume = clampMusicVolume(options.volume, stubVolume);
        }
        return resolved;
      },
      ready: () => resolved,
      getTracks: () => [],
      getCurrentTrack: () => null,
      getCurrentTrackId: () => null,
      getPlaybackState: () => 'unsupported',
      playTrackById: id => {
        const normalized = typeof id === 'string' ? id.trim().toLowerCase() : '';
        return !normalized || normalized === 'none';
      },
      stop: () => true,
      setVolume: value => {
        stubVolume = clampMusicVolume(value, stubVolume);
        return stubVolume;
      },
      getVolume: () => stubVolume,
      onChange: () => () => {},
      isAwaitingUserGesture: () => false
    };
  }

  let tracks = [];
  let audioElement = null;
  let currentIndex = -1;
  let readyPromise = null;
  let preferredTrackId = null;
  let awaitingUserGesture = true;
  let unlockListenersAttached = false;
  let volume = DEFAULT_MUSIC_VOLUME;
  const changeListeners = new Set();

  const formatDisplayName = fileName => {
    if (!fileName) {
      return '';
    }
    const segments = String(fileName).split('/').filter(Boolean);
    const lastSegment = segments.length ? segments[segments.length - 1] : String(fileName);
    const baseName = lastSegment
      .replace(/\.[^/.]+$/, '')
      .replace(/[_-]+/g, ' ')
      .trim();
    if (!baseName) {
      return lastSegment || fileName;
    }
    return baseName.replace(/\b\w/g, char => char.toUpperCase());
  };

  const sanitizeFileName = input => {
    if (!input || typeof input !== 'string') {
      return '';
    }
    let value = input.trim();
    if (!value) {
      return '';
    }
    try {
      value = decodeURIComponent(value);
    } catch (error) {
      // Ignore decoding issues and keep the original value.
    }
    value = value.replace(/^[./]+/, '');
    value = value.replace(/^Assets\/?Music\//i, '');
    value = value.replace(/^assets\/?music\//i, '');
    value = value.split(/[?#]/)[0];
    value = value.replace(/\\/g, '/');
    const parts = value
      .split('/')
      .map(part => part.trim())
      .filter(part => part && part !== '..');
    return parts.join('/');
  };

  const isSupportedFile = fileName => {
    const cleanName = sanitizeFileName(fileName);
    const segments = cleanName.split('.');
    if (segments.length <= 1) {
      return false;
    }
    const extension = segments.pop().toLowerCase();
    return SUPPORTED_EXTENSIONS.includes(extension);
  };

  const createTrack = (fileName, { placeholder = false } = {}) => {
    const cleanName = sanitizeFileName(fileName);
    if (!cleanName || !isSupportedFile(cleanName)) {
      return null;
    }
    const encodedPath = cleanName
      .split('/')
      .map(segment => encodeURIComponent(segment))
      .join('/');
    return {
      id: cleanName,
      filename: cleanName,
      src: `${MUSIC_DIR}${encodedPath}`,
      displayName: formatDisplayName(cleanName),
      placeholder
    };
  };

  const normalizeCandidate = entry => {
    if (!entry) {
      return '';
    }
    if (typeof entry === 'string') {
      return sanitizeFileName(entry);
    }
    if (typeof entry === 'object') {
      const candidate = entry.path
        ?? entry.src
        ?? entry.url
        ?? entry.file
        ?? entry.filename
        ?? entry.name;
      return typeof candidate === 'string' ? sanitizeFileName(candidate) : '';
    }
    return '';
  };

  const findIndexForId = id => {
    if (!id) {
      return -1;
    }
    const trimmed = typeof id === 'string' ? id.trim().toLowerCase() : '';
    const sanitized = sanitizeFileName(id).toLowerCase();
    const candidates = new Set([trimmed, sanitized]);
    const addBaseVariant = value => {
      if (value && value.includes('.')) {
        candidates.add(value.replace(/\.[^/.]+$/, ''));
      }
    };
    addBaseVariant(trimmed);
    addBaseVariant(sanitized);
    return tracks.findIndex(track => {
      const name = track.id?.toLowerCase?.() ?? '';
      const file = track.filename?.toLowerCase?.() ?? '';
      const src = track.src?.toLowerCase?.() ?? '';
      const base = track.filename?.split('/')?.pop()?.toLowerCase?.() ?? '';
      const display = track.displayName?.toLowerCase?.() ?? '';
      return (
        candidates.has(name)
        || candidates.has(file)
        || candidates.has(src)
        || candidates.has(base)
        || candidates.has(display)
      );
    });
  };

  const getPlaybackState = () => {
    if (!audioElement) {
      return 'idle';
    }
    if (audioElement.error) {
      return 'error';
    }
    if (audioElement.paused) {
      return audioElement.currentTime > 0 ? 'paused' : 'idle';
    }
    return 'playing';
  };

  const emitChange = type => {
    const payload = {
      type,
      tracks: tracks.map(track => ({ ...track })),
      currentTrack: tracks[currentIndex] ? { ...tracks[currentIndex] } : null,
      state: getPlaybackState(),
      awaitingUserGesture
    };
    changeListeners.forEach(listener => {
      try {
        listener(payload);
      } catch (error) {
        console.error('Music listener error', error);
      }
    });
  };

  const applyVolumeToAudio = () => {
    if (audioElement) {
      audioElement.volume = volume;
    }
  };

  const setVolumeValue = (value, { silent = false } = {}) => {
    const normalized = clampMusicVolume(value, volume);
    if (normalized === volume) {
      return volume;
    }
    volume = normalized;
    applyVolumeToAudio();
    if (!silent) {
      emitChange('volume');
    }
    return volume;
  };

  const getVolumeValue = () => volume;

  const getAudioElement = () => {
    if (!audioElement) {
      audioElement = new Audio();
      audioElement.loop = true;
      audioElement.preload = 'auto';
      audioElement.setAttribute('preload', 'auto');
      audioElement.volume = volume;
      audioElement.addEventListener('playing', () => {
        awaitingUserGesture = false;
        emitChange('state');
      });
      audioElement.addEventListener('pause', () => {
        emitChange('state');
      });
      audioElement.addEventListener('error', () => {
        emitChange('error');
      });
    }
    return audioElement;
  };

  const tryPlay = () => {
    const audio = getAudioElement();
    if (!audio.src) {
      return;
    }
    audio.volume = volume;
    const playPromise = audio.play();
    if (playPromise && typeof playPromise.catch === 'function') {
      playPromise.catch(() => {});
    }
  };

  const stop = ({ keepPreference = false, silent = false } = {}) => {
    let hadSource = false;
    if (audioElement) {
      try {
        audioElement.pause();
      } catch (error) {
        // Ignore pause issues.
      }
      try {
        if (audioElement.currentTime) {
          audioElement.currentTime = 0;
        }
      } catch (error) {
        // Ignore reset issues.
      }
      if (audioElement.src) {
        hadSource = true;
      }
      audioElement.removeAttribute('src');
      audioElement.src = '';
    }
    const wasPlaying = hadSource || currentIndex !== -1;
    currentIndex = -1;
    if (!keepPreference) {
      preferredTrackId = null;
    }
    if (!silent) {
      emitChange('stop');
    } else {
      emitChange('track');
    }
    return wasPlaying;
  };

  const playIndex = index => {
    if (!tracks.length) {
      currentIndex = -1;
      emitChange('track');
      return false;
    }
    const wrappedIndex = ((index % tracks.length) + tracks.length) % tracks.length;
    const track = tracks[wrappedIndex];
    const audio = getAudioElement();
    if (audio.src !== track.src) {
      audio.src = track.src;
    }
    audio.currentTime = 0;
    audio.volume = volume;
    currentIndex = wrappedIndex;
    preferredTrackId = track.id;
    emitChange('track');
    tryPlay();
    return true;
  };

  const setupUnlockListeners = () => {
    if (unlockListenersAttached || typeof document === 'undefined') {
      return;
    }
    unlockListenersAttached = true;
    awaitingUserGesture = true;
    const unlock = () => {
      awaitingUserGesture = false;
      tryPlay();
    };
    document.addEventListener('pointerdown', unlock, { once: true, capture: false });
    document.addEventListener('keydown', unlock, { once: true, capture: false });
  };

  const loadJsonList = async fileName => {
    try {
      const response = await fetch(`${MUSIC_DIR}${fileName}`, { cache: 'no-store' });
      if (!response.ok) {
        return [];
      }
      const data = await response.json();
      if (Array.isArray(data)) {
        return data;
      }
      if (data && Array.isArray(data.files)) {
        return data.files;
      }
      if (data && Array.isArray(data.tracks)) {
        return data.tracks;
      }
      return [];
    } catch (error) {
      return [];
    }
  };

  const loadDirectoryListing = async () => {
    try {
      const response = await fetch(MUSIC_DIR, { cache: 'no-store' });
      if (!response.ok) {
        return [];
      }
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const data = await response.json();
        if (Array.isArray(data)) {
          return data;
        }
        if (data && Array.isArray(data.files)) {
          return data.files;
        }
        if (data && Array.isArray(data.tracks)) {
          return data.tracks;
        }
        return [];
      }
      const text = await response.text();
      const matches = Array.from(text.matchAll(SUPPORTED_EXTENSION_PATTERN));
      return matches.map(match => match[1]).filter(Boolean);
    } catch (error) {
      return [];
    }
  };

  const sortTrackList = list => {
    return list
      .slice()
      .sort((a, b) => {
        const nameA = (a?.displayName || a?.filename || '').toString();
        const nameB = (b?.displayName || b?.filename || '').toString();
        return compareTextLocalized(nameA, nameB, { sensitivity: 'base' });
      });
  };

  const verifyTrackAvailability = async list => {
    const results = await Promise.all(
      list.map(async track => {
        if (!track || !track.placeholder) {
          return track;
        }
        if (typeof window !== 'undefined' && window.location?.protocol === 'file:') {
          return { ...track, placeholder: false };
        }
        try {
          const response = await fetch(track.src, { method: 'HEAD', cache: 'no-store' });
          if (response.ok) {
            return { ...track, placeholder: false };
          }
          if (response.status === 405) {
            const rangeResponse = await fetch(track.src, {
              method: 'GET',
              headers: { Range: 'bytes=0-0' },
              cache: 'no-store'
            });
            if (rangeResponse.ok) {
              return { ...track, placeholder: false };
            }
          }
        } catch (error) {
          try {
            const fallbackResponse = await fetch(track.src, {
              method: 'GET',
              headers: { Range: 'bytes=0-0' },
              cache: 'no-store'
            });
            if (fallbackResponse.ok) {
              return { ...track, placeholder: false };
            }
          } catch (innerError) {
            // Ignore network failures and keep placeholder flag.
          }
        }
        return track;
      })
    );
    return results;
  };

  const discoverTracks = async () => {
    const discovered = new Set();
    const pushCandidate = candidate => {
      const normalized = normalizeCandidate(candidate);
      if (!normalized || !isSupportedFile(normalized)) {
        return;
      }
      discovered.add(normalized);
    };

    for (const manifest of ['tracks.json', 'manifest.json', 'playlist.json', 'music.json', 'list.json']) {
      const entries = await loadJsonList(manifest);
      entries.forEach(pushCandidate);
      if (discovered.size > 0) {
        break;
      }
    }

    if (discovered.size === 0) {
      const listing = await loadDirectoryListing();
      listing.forEach(pushCandidate);
    }

    if (discovered.size > 0) {
      return Array.from(discovered)
        .map(name => createTrack(name))
        .filter(Boolean);
    }

    return FALLBACK_TRACKS.map(name => createTrack(name, { placeholder: true })).filter(Boolean);
  };

  const init = (options = {}) => {
    if (readyPromise) {
      if (typeof options.volume === 'number') {
        setVolumeValue(options.volume);
      }
      if (typeof options.preferredTrackId === 'string') {
        const trimmed = options.preferredTrackId.trim();
        if (!trimmed || ['none', 'off', 'stop'].includes(trimmed.toLowerCase())) {
          preferredTrackId = null;
          stop({ keepPreference: false });
        } else {
          preferredTrackId = sanitizeFileName(trimmed) || null;
          if (preferredTrackId && options.autoplay !== false) {
            const preferredIndex = findIndexForId(preferredTrackId);
            if (preferredIndex >= 0) {
              playIndex(preferredIndex);
            }
          } else if (options.autoplay === false) {
            stop({ keepPreference: Boolean(preferredTrackId) });
          }
        }
      } else if (options.autoplay === false) {
        stop({ keepPreference: Boolean(preferredTrackId) });
      }
      return readyPromise;
    }

    if (typeof options.volume === 'number') {
      setVolumeValue(options.volume, { silent: true });
    }

    if (typeof options.preferredTrackId === 'string') {
      const rawPreference = options.preferredTrackId.trim();
      if (!rawPreference || ['none', 'off', 'stop'].includes(rawPreference.toLowerCase())) {
        preferredTrackId = null;
      } else {
        preferredTrackId = sanitizeFileName(rawPreference);
        if (preferredTrackId && !preferredTrackId.trim()) {
          preferredTrackId = null;
        }
      }
    } else {
      preferredTrackId = null;
    }

    const autoplay = options?.autoplay !== false;

    setupUnlockListeners();

    readyPromise = discoverTracks()
      .then(async foundTracks => {
        const verified = await verifyTrackAvailability(foundTracks);
        tracks = sortTrackList(verified);
        emitChange('tracks');
        if (!tracks.length) {
          currentIndex = -1;
          emitChange('track');
          if (!autoplay) {
            stop({ keepPreference: Boolean(preferredTrackId) });
          }
          return tracks;
        }
        if (!autoplay) {
          stop({ keepPreference: Boolean(preferredTrackId) });
          return tracks;
        }
        const preferredIndex = preferredTrackId ? findIndexForId(preferredTrackId) : -1;
        const indexToPlay = preferredIndex >= 0
          ? preferredIndex
          : Math.floor(Math.random() * tracks.length);
        playIndex(indexToPlay);
        return tracks;
      })
      .catch(error => {
        console.error('Erreur de découverte des pistes musicales', error);
        const fallbackList = FALLBACK_TRACKS.map(name => createTrack(name, { placeholder: true })).filter(Boolean);
        return verifyTrackAvailability(fallbackList).then(verifiedFallback => {
          tracks = sortTrackList(verifiedFallback);
          emitChange('tracks');
          if (!tracks.length) {
            currentIndex = -1;
            emitChange('track');
            return tracks;
          }
          if (!autoplay) {
            stop({ keepPreference: Boolean(preferredTrackId) });
            return tracks;
          }
          const preferredIndex = preferredTrackId ? findIndexForId(preferredTrackId) : -1;
          playIndex(preferredIndex >= 0 ? preferredIndex : Math.floor(Math.random() * tracks.length));
          return tracks;
        });
      });

    return readyPromise;
  };

  const ready = () => {
    if (readyPromise) {
      return readyPromise;
    }
    return init();
  };

  const getTracks = () => tracks.map(track => ({ ...track }));

  const getCurrentTrack = () => {
    if (currentIndex < 0 || currentIndex >= tracks.length) {
      return null;
    }
    return { ...tracks[currentIndex] };
  };

  const getCurrentTrackId = () => {
    const current = getCurrentTrack();
    return current ? current.id : null;
  };

  const playTrackById = id => {
    const raw = typeof id === 'string' ? id.trim() : '';
    const normalized = raw.toLowerCase();
    if (!raw || normalized === 'none' || normalized === 'off' || normalized === 'stop') {
      stop({ keepPreference: false });
      return true;
    }
    const sanitized = sanitizeFileName(raw);
    if (!sanitized) {
      stop({ keepPreference: false });
      return true;
    }
    preferredTrackId = sanitized;
    if (!tracks.length) {
      emitChange('track');
      return false;
    }
    const index = findIndexForId(sanitized);
    if (index === -1) {
      emitChange('track');
      return false;
    }
    return playIndex(index);
  };

  const onChange = listener => {
    if (typeof listener !== 'function') {
      return () => {};
    }
    changeListeners.add(listener);
    return () => {
      changeListeners.delete(listener);
    };
  };

  return {
    init,
    ready,
    getTracks,
    getCurrentTrack,
    getCurrentTrackId,
    getPlaybackState,
    playTrackById,
    stop,
    setVolume: (value, options) => setVolumeValue(value, options || {}),
    getVolume: () => getVolumeValue(),
    onChange,
    isAwaitingUserGesture: () => awaitingUserGesture
  };
})();

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
  if (!isMusicModuleEnabled()) {
    if (elements.musicTrackSelect) {
      elements.musicTrackSelect.value = '';
      elements.musicTrackSelect.disabled = true;
    }
    if (elements.musicTrackStatus) {
      elements.musicTrackStatus.textContent = t('scripts.app.music.disabled');
    }
    if (elements.musicVolumeSlider) {
      elements.musicVolumeSlider.value = '0';
      elements.musicVolumeSlider.setAttribute('aria-valuenow', '0');
      elements.musicVolumeSlider.setAttribute('aria-valuetext', '0%');
      elements.musicVolumeSlider.disabled = true;
    }
    return;
  }
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

function updateMusicModuleVisibility() {
  const enabled = isMusicModuleEnabled();

  if (enabled && appStartCompleted && !musicModuleInitRequested) {
    musicModuleInitRequested = true;
    musicPlayer.init({
      preferredTrackId: gameState.musicTrackId,
      autoplay: gameState.musicEnabled !== false,
      volume: gameState.musicVolume
    });
    musicPlayer.ready().then(() => {
      refreshMusicControls();
    });
  }

  if (elements.musicOptionCard) {
    elements.musicOptionCard.hidden = !enabled;
    elements.musicOptionCard.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  }

  if (elements.musicOptionRow) {
    elements.musicOptionRow.hidden = !enabled;
    elements.musicOptionRow.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  }

  if (elements.openMidiModuleButton) {
    elements.openMidiModuleButton.disabled = !enabled;
    elements.openMidiModuleButton.setAttribute('aria-disabled', enabled ? 'false' : 'true');
    if (!enabled) {
      elements.openMidiModuleButton.setAttribute('tabindex', '-1');
      if (typeof elements.openMidiModuleButton.blur === 'function') {
        elements.openMidiModuleButton.blur();
      }
    } else {
      elements.openMidiModuleButton.removeAttribute('tabindex');
    }
  }

  setNavButtonLockState(elements.navMidiButton, enabled);

  if (elements.headerPlaybackButton) {
    elements.headerPlaybackButton.hidden = !enabled;
    elements.headerPlaybackButton.setAttribute('aria-hidden', enabled ? 'false' : 'true');
    elements.headerPlaybackButton.setAttribute('aria-disabled', enabled ? 'false' : 'true');
    elements.headerPlaybackButton.disabled = !enabled;
    if (!enabled) {
      elements.headerPlaybackButton.setAttribute('disabled', '');
    } else {
      elements.headerPlaybackButton.removeAttribute('disabled');
    }
    if (!enabled) {
      elements.headerPlaybackButton.setAttribute('tabindex', '-1');
      if (typeof elements.headerPlaybackButton.blur === 'function') {
        elements.headerPlaybackButton.blur();
      }
    } else {
      elements.headerPlaybackButton.removeAttribute('tabindex');
    }
  }

  if (elements.midiPage) {
    elements.midiPage.setAttribute('data-music-disabled', enabled ? 'false' : 'true');
    elements.midiPage.setAttribute('aria-hidden', enabled ? 'false' : 'true');
    if (!enabled && document.body?.dataset?.activePage === 'midi') {
      showPage('options');
    }
  }

  if (elements.midiModuleCard) {
    elements.midiModuleCard.hidden = !enabled;
    elements.midiModuleCard.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  }

  if (elements.midiKeyboardArea) {
    elements.midiKeyboardArea.hidden = !enabled;
    elements.midiKeyboardArea.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  }

  if (!enabled) {
    if (musicPlayer && typeof musicPlayer.stop === 'function') {
      const currentTrackId = typeof musicPlayer.getCurrentTrackId === 'function'
        ? musicPlayer.getCurrentTrackId()
        : null;
      if (currentTrackId) {
        musicPlayer.stop();
      }
    }
    const midiPlayer = typeof globalThis !== 'undefined'
      ? globalThis.atom2universMidiPlayer
      : null;
    if (midiPlayer && typeof midiPlayer.stop === 'function') {
      midiPlayer.stop(true);
    }
    musicModuleInitRequested = false;
    gameState.musicTrackId = null;
    gameState.musicEnabled = false;
  }

  refreshMusicControls();
}

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

function collectDevkitShopSummaries() {
  const productionBase = gameState.productionBase || createEmptyProductionBreakdown();
  const clickEntry = productionBase.perClick || createEmptyProductionEntry();
  const autoEntry = productionBase.perSecond || createEmptyProductionEntry();

  const summaries = new Map();

  UPGRADE_DEFS.forEach(def => {
    const texts = getShopBuildingTexts(def);
    const level = getUpgradeLevel(gameState.upgrades, def.id);
    summaries.set(def.id, {
      id: def.id,
      name: texts.name || def.name || def.id,
      level,
      clickAdd: LayeredNumber.zero(),
      autoAdd: LayeredNumber.zero(),
      clickMult: LayeredNumber.one(),
      autoMult: LayeredNumber.one(),
      totalCost: computeUpgradeTotalSpent(def, level)
    });
  });

  const accumulateAddition = (list, key) => {
    if (!Array.isArray(list)) return;
    list.forEach(entry => {
      if (!entry || entry.source !== 'shop') return;
      const summary = summaries.get(entry.id);
      if (!summary) return;
      const value = entry.value instanceof LayeredNumber
        ? entry.value
        : toLayeredValue(entry.value, 0);
      if (!(value instanceof LayeredNumber) || value.isZero() || value.sign <= 0) {
        return;
      }
      summary[key] = summary[key].add(value);
    });
  };

  const accumulateMultiplier = (list, key) => {
    if (!Array.isArray(list)) return;
    list.forEach(entry => {
      if (!entry || entry.source !== 'shop') return;
      const summary = summaries.get(entry.id);
      if (!summary) return;
      const value = entry.value instanceof LayeredNumber
        ? entry.value
        : toMultiplierLayered(entry.value);
      summary[key] = summary[key].multiply(value);
    });
  };

  accumulateAddition(clickEntry.additions, 'clickAdd');
  accumulateAddition(autoEntry.additions, 'autoAdd');
  accumulateMultiplier(clickEntry.multipliers, 'clickMult');
  accumulateMultiplier(autoEntry.multipliers, 'autoMult');

  return UPGRADE_DEFS.map(def => summaries.get(def.id)).filter(Boolean);
}

function formatDevKitShopFlatBonus(value) {
  if (!(value instanceof LayeredNumber)) {
    value = toLayeredValue(value, 0);
  }
  if (!(value instanceof LayeredNumber) || value.isZero() || value.sign <= 0) {
    return '';
  }
  const normalized = normalizeProductionUnit(value);
  if (!(normalized instanceof LayeredNumber) || normalized.isZero() || normalized.sign <= 0) {
    return '';
  }
  return formatLayeredLocalized(normalized, { mantissaDigits: 2 });
}

function formatDevKitShopMultiplier(value) {
  if (!(value instanceof LayeredNumber)) {
    value = toMultiplierLayered(value);
  }
  if (!(value instanceof LayeredNumber) || isLayeredOne(value)) {
    return '';
  }
  return formatMultiplier(value);
}

function renderDevkitShopDetails() {
  const container = elements.devkitShopDetails;
  if (!container) {
    return;
  }

  container.innerHTML = '';

  const summaries = collectDevkitShopSummaries().filter(summary => summary && summary.level > 0);

  if (!summaries.length) {
    const empty = document.createElement('p');
    empty.className = 'devkit-shop-empty';
    empty.textContent = translateOrDefault(
      'index.sections.devkit.shop.empty',
      'No buildings purchased yet.'
    );
    container.appendChild(empty);
    return;
  }

  summaries.forEach(summary => {
    const card = document.createElement('article');
    card.className = 'devkit-shop-card';
    card.setAttribute('role', 'listitem');
    card.dataset.upgradeId = summary.id;

    const header = document.createElement('header');
    header.className = 'devkit-shop-card__header';

    const title = document.createElement('h4');
    title.className = 'devkit-shop-card__title';
    title.textContent = summary.name;

    const levelDisplay = formatIntegerLocalized(summary.level);
    let levelText = translateOrDefault('scripts.info.shop.level', '', { level: levelDisplay });
    if (!levelText) {
      levelText = `Level ${levelDisplay}`;
    }
    const level = document.createElement('span');
    level.className = 'devkit-shop-card__level';
    level.textContent = levelText;

    header.append(title, level);
    card.appendChild(header);

    const costValue = formatLayeredLocalized(summary.totalCost, { mantissaDigits: 2 });
    let costText = translateOrDefault(
      'index.sections.devkit.shop.cost',
      '',
      { value: costValue }
    );
    if (!costText) {
      costText = `Total cost: ${costValue}`;
    }
    const cost = document.createElement('p');
    cost.className = 'devkit-shop-card__cost';
    cost.textContent = costText;
    card.appendChild(cost);

    const stats = document.createElement('dl');
    stats.className = 'devkit-shop-card__stats';
    let hasStat = false;

    const appendStat = (labelKey, fallback, valueText) => {
      if (!valueText) {
        return;
      }
      const label = document.createElement('dt');
      label.textContent = translateOrDefault(labelKey, fallback);
      const value = document.createElement('dd');
      value.textContent = valueText;
      stats.append(label, value);
      hasStat = true;
    };

    const clickAddText = formatDevKitShopFlatBonus(summary.clickAdd);
    const autoAddText = formatDevKitShopFlatBonus(summary.autoAdd);
    const clickMultText = formatDevKitShopMultiplier(summary.clickMult);
    const autoMultText = formatDevKitShopMultiplier(summary.autoMult);

    appendStat('index.sections.devkit.shop.labels.apcAdd', 'APC +', clickAddText);
    appendStat('index.sections.devkit.shop.labels.apcMult', 'APC ×', clickMultText);
    appendStat('index.sections.devkit.shop.labels.apsAdd', 'APS +', autoAddText);
    appendStat('index.sections.devkit.shop.labels.apsMult', 'APS ×', autoMultText);

    if (hasStat) {
      card.appendChild(stats);
    }

    container.appendChild(card);
  });
}

function updateDevKitUI() {
  if (elements.devkitAutoStatus) {
    const bonus = getDevKitAutoFlatBonus();
    const text = bonus instanceof LayeredNumber && !bonus.isZero()
      ? bonus.toString()
      : '0';
    elements.devkitAutoStatus.textContent = text;
  }
  if (elements.devkitShopDetails) {
    renderDevkitShopDetails();
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
    const unlocked = areInfoBonusesUnlocked();
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
  if (!isDevkitFeatureEnabled() || DEVKIT_STATE.isOpen || !elements.devkitOverlay) {
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
  if (!isDevkitFeatureEnabled()) {
    return;
  }
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
    updateGoalsUI();
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
    updateShopAffordability();
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

const SHOP_PURCHASE_AMOUNTS = [1, 10, 100];
let activeShopPurchaseAmount = SHOP_PURCHASE_AMOUNTS[0] ?? 1;
const shopPurchaseControls = new Map();
const shopRows = new Map();
let lastVisibleShopIndex = -1;
let lastVisibleShopBonusIds = new Set();
const FAMILY_DESCRIPTION_KEYS = {
  'alkali-metal': 'scripts.app.table.family.descriptions.alkaliMetal',
  'alkaline-earth-metal': 'scripts.app.table.family.descriptions.alkalineEarthMetal',
  'transition-metal': 'scripts.app.table.family.descriptions.transitionMetal',
  'post-transition-metal': 'scripts.app.table.family.descriptions.postTransitionMetal',
  metalloid: 'scripts.app.table.family.descriptions.metalloid',
  nonmetal: 'scripts.app.table.family.descriptions.nonmetal',
  halogen: 'scripts.app.table.family.descriptions.halogen',
  'noble-gas': 'scripts.app.table.family.descriptions.nobleGas',
  lanthanide: 'scripts.app.table.family.descriptions.lanthanide',
  actinide: 'scripts.app.table.family.descriptions.actinide'
};
const periodicCells = new Map();
let selectedElementId = null;
let elementDetailsLastTrigger = null;
let elementFamilyLastTrigger = null;
let gamePageVisibleSince = null;
let hiddenSinceTimestamp = null;

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
  const difficulty = typeof options.difficulty === 'string' && options.difficulty.trim()
    ? options.difficulty.trim()
    : null;
  const defaultLevel = normalizedConfig.levels.moyen
    || Object.values(normalizedConfig.levels).find(level => level && level.bonusSeconds > 0 && level.multiplier > 0)
    || null;
  const levelConfig = difficulty && normalizedConfig.levels[difficulty]
    ? normalizedConfig.levels[difficulty]
    : defaultLevel;
  if (!levelConfig) {
    return false;
  }

  const multiplier = Number(levelConfig.multiplier);
  const bonusSeconds = Number(levelConfig.bonusSeconds);
  const validSeconds = Number(levelConfig.validSeconds || levelConfig.bonusSeconds);
  if (
    !Number.isFinite(multiplier)
    || multiplier <= 0
    || !Number.isFinite(bonusSeconds)
    || bonusSeconds <= 0
  ) {
    return false;
  }

  const grantedAt = Date.now();
  const expiresAt = grantedAt + Math.max(validSeconds, bonusSeconds) * 1000;
  const bonus = normalizeSudokuOfflineBonusState({
    multiplier,
    maxSeconds: bonusSeconds,
    remainingSeconds: bonusSeconds,
    grantedAt,
    expiresAt,
    level: difficulty
  });
  if (!bonus) {
    return false;
  }
  gameState.sudokuOfflineBonus = bonus;

  const announce = options.announce !== false;
  if (announce && typeof showToast === 'function') {
    const durationText = formatSudokuRewardDuration(bonusSeconds);
    const multiplierText = formatMultiplier(multiplier);
    const messageKey = 'scripts.app.arcade.sudoku.reward.ready';
    const elapsedSeconds = Number(options.elapsedSeconds);
    const elapsedText = Number.isFinite(elapsedSeconds) && elapsedSeconds > 0
      ? formatSudokuRewardDuration(elapsedSeconds)
      : null;
    const validText = formatSudokuRewardDuration(Math.max(0, (bonus.expiresAt - bonus.grantedAt) / 1000));
    const fallback = `Sudoku bonus ready: ${durationText} at ${multiplierText}. Active for the next ${validText}.`;
    const message = typeof t === 'function'
      ? t(messageKey, {
        duration: durationText,
        elapsed: elapsedText,
        multiplier: multiplierText,
        valid: validText
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

  const gachaCandidates = [source.gachaTickets, source.tickets, source.amount];
  let gachaTickets = 0;
  for (let i = 0; i < gachaCandidates.length; i += 1) {
    const candidate = Number(gachaCandidates[i]);
    if (Number.isFinite(candidate) && candidate > 0) {
      gachaTickets = Math.floor(candidate);
      break;
    }
  }

  const critSource = source.crit && typeof source.crit === 'object'
    ? source.crit
    : source.critical && typeof source.critical === 'object'
      ? source.critical
      : null;
  let multiplier = 0;
  let durationSeconds = 0;
  if (critSource) {
    const multiplierCandidates = [
      critSource.multiplier,
      critSource.value,
      critSource.multiplierAdd != null ? Number(critSource.multiplierAdd) + 1 : null
    ];
    for (let index = 0; index < multiplierCandidates.length; index += 1) {
      const candidate = Number(multiplierCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 1) {
        multiplier = candidate;
        break;
      }
    }
    const durationCandidates = [
      critSource.durationSeconds,
      critSource.seconds,
      critSource.duration,
      critSource.time
    ];
    for (let index = 0; index < durationCandidates.length; index += 1) {
      const candidate = Number(durationCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        durationSeconds = Math.floor(candidate);
        break;
      }
    }
  }

  const crit = multiplier > 1 && durationSeconds > 0
    ? { multiplier, durationSeconds }
    : null;

  if (gachaTickets <= 0 && !crit) {
    return null;
  }

  return {
    gachaTickets: Math.max(0, gachaTickets),
    crit
  };
}

function applyChessCritBonus(multiplier, durationSeconds) {
  const numericMultiplier = Number(multiplier);
  const numericDuration = Number(durationSeconds);
  if (!Number.isFinite(numericMultiplier) || numericMultiplier <= 1) {
    return null;
  }
  if (!Number.isFinite(numericDuration) || numericDuration <= 0) {
    return null;
  }
  const duration = Math.floor(numericDuration);
  const state = ensureApsCritState();
  const previousMultiplier = getApsCritMultiplier(state);
  state.effects.push({
    multiplierAdd: Math.max(0, numericMultiplier - 1),
    remainingSeconds: duration
  });
  state.effects = state.effects.filter(effect => {
    const remaining = Number(effect?.remainingSeconds) || 0;
    const value = Number(effect?.multiplierAdd) || 0;
    return remaining > 0 && value > 0;
  });
  if (!state.effects.length) {
    return null;
  }
  const newMultiplier = getApsCritMultiplier(state);
  if (newMultiplier !== previousMultiplier) {
    recalcProduction();
  }
  updateUI();
  pulseApsCritPanel();
  return {
    multiplier: numericMultiplier,
    durationSeconds: duration
  };
}

function registerChessVictoryReward(options = {}) {
  const reward = normalizeChessVictoryReward(options && options.reward);
  if (!reward) {
    return false;
  }

  let gachaGained = 0;
  if (reward.gachaTickets > 0) {
    const award = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof award === 'function') {
      try {
        gachaGained = award(reward.gachaTickets, { unlockTicketStar: true });
      } catch (error) {
        console.warn('Chess reward: unable to grant gacha tickets', error);
        gachaGained = 0;
      }
    }
  }

  const critResult = reward.crit
    ? applyChessCritBonus(reward.crit.multiplier, reward.crit.durationSeconds)
    : null;
  const hasCrit = Boolean(critResult);
  if ((!Number.isFinite(gachaGained) || gachaGained <= 0) && !hasCrit) {
    return false;
  }

  const announce = options.announce !== false;
  if (announce && typeof showToast === 'function') {
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
    const parts = [];
    if (Number.isFinite(gachaGained) && gachaGained > 0) {
      const suffix = gachaGained > 1 ? 's' : '';
      parts.push(translateOrDefault(
        'scripts.arcade.chess.reward.tickets',
        '{count} ticket{suffix} gacha',
        { count: formatNumberLocalized(gachaGained), suffix }
      ));
    }
    if (hasCrit && critResult) {
      const multiplierText = formatNumberLocalized(critResult.multiplier, { maximumFractionDigits: 2 });
      const durationText = formatDuration(critResult.durationSeconds * 1000);
      parts.push(translateOrDefault(
        'scripts.arcade.chess.reward.crit',
        'Critique ×{multiplier} pendant {duration}',
        { multiplier: multiplierText, duration: durationText }
      ));
    }
    const rewardsText = parts.filter(Boolean).join(' · ');
    const messageKey = hasDifficulty
      ? 'scripts.arcade.chess.reward.granted'
      : 'scripts.arcade.chess.reward.grantedGeneric';
    const fallback = hasDifficulty
      ? `Victoire aux échecs (${difficultyLabel}) : ${rewardsText}.`
      : `Victoire aux échecs : ${rewardsText}.`;
    let message = null;
    if (typeof t === 'function') {
      try {
        message = t(messageKey, {
          difficulty: difficultyLabel,
          rewards: rewardsText
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
            rewards: rewardsText
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

function isElementDetailsModalOpen() {
  return Boolean(elements.elementDetailsOverlay && !elements.elementDetailsOverlay.hasAttribute('hidden'));
}

function updateElementDetailsModalContent(definition) {
  if (!definition || !elements.elementDetailsOverlay) {
    return;
  }
  const { symbol, name } = getPeriodicElementDisplay(definition);
  const displaySymbol = symbol || '';
  const displayName = name || '';
  const atomicNumber = definition.atomicNumber != null ? definition.atomicNumber : '';
  if (elements.elementDetailsTitle) {
    let titleKey = 'index.sections.table.modal.titleFallback';
    let fallbackTitle = atomicNumber ? `Element ${atomicNumber}` : 'Element details';
    if (displayName && displaySymbol) {
      titleKey = 'index.sections.table.modal.title';
      fallbackTitle = `${displayName} (${displaySymbol})`;
    } else if (displayName) {
      titleKey = 'index.sections.table.modal.titleNameOnly';
      fallbackTitle = displayName;
    } else if (displaySymbol) {
      titleKey = 'index.sections.table.modal.titleSymbolOnly';
      fallbackTitle = displaySymbol;
    }
    const titleText = translateOrDefault(titleKey, fallbackTitle, {
      name: displayName,
      symbol: displaySymbol,
      number: atomicNumber || definition.id || '',
    });
    elements.elementDetailsTitle.textContent = titleText;
  }
  if (elements.elementDetailsBody) {
    const container = elements.elementDetailsBody;
    const fallbackText = t('index.sections.table.modal.comingSoon');
    container.innerHTML = '';
    container.classList.remove('element-details-overlay__content--placeholder');
    const details = getPeriodicElementDetails(definition);

    const summaryText = typeof details?.summary === 'string' ? details.summary.trim() : '';
    const bodyText = typeof details?.body === 'string' ? details.body.trim() : '';
    const paragraphTexts = Array.isArray(details?.paragraphs)
      ? details.paragraphs
          .map(paragraph => (typeof paragraph === 'string' ? paragraph.trim() : ''))
          .filter(text => text.length)
      : [];
    const sources = Array.isArray(details?.sources)
      ? details.sources
          .map(source => (typeof source === 'string' ? source.trim() : ''))
          .filter(text => text.length)
      : [];

    let hasContent = false;
    const fragment = document.createDocumentFragment();

    if (summaryText) {
      const summary = document.createElement('p');
      summary.className = 'element-details-overlay__summary';
      summary.textContent = summaryText;
      fragment.appendChild(summary);
      hasContent = true;
    }

    const effectiveParagraphs = paragraphTexts.length ? paragraphTexts : (bodyText ? [bodyText] : []);
    effectiveParagraphs.forEach(text => {
      const paragraph = document.createElement('p');
      paragraph.className = 'element-details-overlay__paragraph';
      paragraph.textContent = text;
      fragment.appendChild(paragraph);
      hasContent = true;
    });

    if (sources.length) {
      const sourcesWrapper = document.createElement('div');
      sourcesWrapper.className = 'element-details-overlay__sources';

      const label = document.createElement('p');
      label.className = 'element-details-overlay__sources-label';
      label.textContent = t('index.sections.table.modal.sourcesLabel');
      sourcesWrapper.appendChild(label);

      const list = document.createElement('ul');
      list.className = 'element-details-overlay__sources-list';
      sources.forEach(text => {
        const item = document.createElement('li');
        item.className = 'element-details-overlay__sources-item';
        item.textContent = text;
        list.appendChild(item);
      });
      sourcesWrapper.appendChild(list);
      fragment.appendChild(sourcesWrapper);
      hasContent = true;
    }

    if (hasContent) {
      container.appendChild(fragment);
    } else {
      const placeholder = document.createElement('p');
      placeholder.className = 'element-details-overlay__placeholder';
      placeholder.textContent = fallbackText;
      container.appendChild(placeholder);
      container.classList.add('element-details-overlay__content--placeholder');
    }
  }
  elements.elementDetailsOverlay.dataset.elementId = definition.id;
  if (elements.elementDetailsDialog) {
    elements.elementDetailsDialog.dataset.elementId = definition.id;
  }
}

function openElementDetailsModal(elementId, { trigger = null } = {}) {
  if (!elements.elementDetailsOverlay || !periodicElementIndex.has(elementId)) {
    return;
  }
  const definition = periodicElementIndex.get(elementId);
  updateElementDetailsModalContent(definition);
  elements.elementDetailsOverlay.hidden = false;
  elements.elementDetailsOverlay.setAttribute('aria-hidden', 'false');
  document.body.classList.add('element-details-modal-open');
  elementDetailsLastTrigger = trigger || document.activeElement || elements.elementInfoSymbol;
  if (elements.elementInfoSymbol) {
    elements.elementInfoSymbol.setAttribute('aria-expanded', 'true');
  }
  if (elements.elementDetailsDialog) {
    elements.elementDetailsDialog.focus({ preventScroll: true });
  }
  if (elements.elementDetailsCloseButton) {
    const closeLabel = t('index.sections.table.modal.close');
    if (closeLabel) {
      elements.elementDetailsCloseButton.setAttribute('aria-label', closeLabel);
    }
  }
  document.addEventListener('keydown', handleElementDetailsKeydown, true);
}

function closeElementDetailsModal({ restoreFocus = true } = {}) {
  if (!isElementDetailsModalOpen()) {
    return;
  }
  elements.elementDetailsOverlay.hidden = true;
  elements.elementDetailsOverlay.setAttribute('aria-hidden', 'true');
  document.body.classList.remove('element-details-modal-open');
  if (elements.elementInfoSymbol) {
    elements.elementInfoSymbol.setAttribute('aria-expanded', 'false');
  }
  if (elements.elementDetailsOverlay.dataset.elementId) {
    delete elements.elementDetailsOverlay.dataset.elementId;
  }
  if (elements.elementDetailsDialog?.dataset?.elementId) {
    delete elements.elementDetailsDialog.dataset.elementId;
  }
  document.removeEventListener('keydown', handleElementDetailsKeydown, true);
  const lastTrigger = elementDetailsLastTrigger;
  elementDetailsLastTrigger = null;
  if (restoreFocus && lastTrigger && typeof lastTrigger.focus === 'function') {
    lastTrigger.focus({ preventScroll: true });
  }
}

function handleElementDetailsKeydown(event) {
  if (!isElementDetailsModalOpen()) {
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    closeElementDetailsModal();
    return;
  }
  if (event.key === 'Tab' && elements.elementDetailsDialog) {
    const focusableSelectors = [
      'button:not([disabled])',
      '[href]',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ];
    const focusable = Array.from(
      elements.elementDetailsDialog.querySelectorAll(focusableSelectors.join(','))
    ).filter(el => !(el.hasAttribute('disabled') || el.getAttribute('aria-hidden') === 'true'));
    if (!focusable.length) {
      event.preventDefault();
      elements.elementDetailsDialog.focus({ preventScroll: true });
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey) {
      if (document.activeElement === first || document.activeElement === elements.elementDetailsDialog) {
        event.preventDefault();
        last.focus({ preventScroll: true });
      }
    } else if (document.activeElement === last) {
      event.preventDefault();
      first.focus({ preventScroll: true });
    }
  }
}

function isElementFamilyModalOpen() {
  return Boolean(elements.elementFamilyOverlay && !elements.elementFamilyOverlay.hasAttribute('hidden'));
}

function getFamilyDescription(familyId, familyLabel) {
  if (!familyId) {
    return translateOrDefault(
      'scripts.app.table.family.descriptions.placeholder',
      `Description de la famille ${familyLabel} à venir.`,
      { family: familyLabel }
    );
  }
  const messageKey = FAMILY_DESCRIPTION_KEYS[familyId];
  if (messageKey) {
    const translated = t(messageKey);
    if (translated && translated !== messageKey) {
      return translated;
    }
  }
  return translateOrDefault(
    'scripts.app.table.family.descriptions.placeholder',
    `Description de la famille ${familyLabel} à venir.`,
    { family: familyLabel }
  );
}

function updateElementFamilyModalContent(familyId) {
  if (!familyId || !elements.elementFamilyOverlay) {
    return;
  }
  const familyLabel = CATEGORY_LABELS[familyId] || familyId;
  if (elements.elementFamilyTitle) {
    const titleText = translateOrDefault(
      'index.sections.table.family.modal.title',
      `Famille · ${familyLabel}`,
      { family: familyLabel }
    );
    elements.elementFamilyTitle.textContent = titleText;
  }
  if (elements.elementFamilyBody) {
    const description = getFamilyDescription(familyId, familyLabel);
    elements.elementFamilyBody.textContent = description;
  }
  elements.elementFamilyOverlay.dataset.familyId = familyId;
  if (elements.elementFamilyDialog) {
    elements.elementFamilyDialog.dataset.familyId = familyId;
  }
}

function openElementFamilyModal(familyId, { trigger = null } = {}) {
  if (!familyId || !elements.elementFamilyOverlay) {
    return;
  }
  updateElementFamilyModalContent(familyId);
  elements.elementFamilyOverlay.hidden = false;
  elements.elementFamilyOverlay.setAttribute('aria-hidden', 'false');
  document.body.classList.add('element-family-modal-open');
  elementFamilyLastTrigger = trigger || document.activeElement || elements.elementInfoCategoryButton;
  if (elements.elementInfoCategoryButton) {
    elements.elementInfoCategoryButton.setAttribute('aria-expanded', 'true');
  }
  if (elements.elementFamilyDialog) {
    elements.elementFamilyDialog.focus({ preventScroll: true });
  }
  if (elements.elementFamilyCloseButton) {
    const closeLabel = t('index.sections.table.modal.close');
    if (closeLabel) {
      elements.elementFamilyCloseButton.setAttribute('aria-label', closeLabel);
    }
  }
  document.addEventListener('keydown', handleElementFamilyKeydown, true);
}

function closeElementFamilyModal({ restoreFocus = true } = {}) {
  if (!isElementFamilyModalOpen()) {
    return;
  }
  elements.elementFamilyOverlay.hidden = true;
  elements.elementFamilyOverlay.setAttribute('aria-hidden', 'true');
  document.body.classList.remove('element-family-modal-open');
  if (elements.elementInfoCategoryButton) {
    elements.elementInfoCategoryButton.setAttribute('aria-expanded', 'false');
  }
  if (elements.elementFamilyOverlay.dataset.familyId) {
    delete elements.elementFamilyOverlay.dataset.familyId;
  }
  if (elements.elementFamilyDialog?.dataset?.familyId) {
    delete elements.elementFamilyDialog.dataset.familyId;
  }
  document.removeEventListener('keydown', handleElementFamilyKeydown, true);
  const lastTrigger = elementFamilyLastTrigger;
  elementFamilyLastTrigger = null;
  if (restoreFocus && lastTrigger && typeof lastTrigger.focus === 'function') {
    lastTrigger.focus({ preventScroll: true });
  }
}

function handleElementFamilyKeydown(event) {
  if (!isElementFamilyModalOpen()) {
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    closeElementFamilyModal();
    return;
  }
  if (event.key === 'Tab' && elements.elementFamilyDialog) {
    const focusableSelectors = [
      'button:not([disabled])',
      '[href]',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ];
    const focusable = Array.from(
      elements.elementFamilyDialog.querySelectorAll(focusableSelectors.join(','))
    ).filter(el => !(el.hasAttribute('disabled') || el.getAttribute('aria-hidden') === 'true'));
    if (!focusable.length) {
      event.preventDefault();
      elements.elementFamilyDialog.focus({ preventScroll: true });
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey) {
      if (document.activeElement === first || document.activeElement === elements.elementFamilyDialog) {
        event.preventDefault();
        last.focus({ preventScroll: true });
      }
    } else if (document.activeElement === last) {
      event.preventDefault();
      first.focus({ preventScroll: true });
    }
  }
}

function updateElementInfoPanel(definition) {
  const panel = elements.elementInfoPanel;
  const placeholder = elements.elementInfoPlaceholder;
  const content = elements.elementInfoContent;
  if (!panel) return;

  if (!definition) {
    if (elements.elementInfoSymbol) {
      elements.elementInfoSymbol.textContent = '';
      elements.elementInfoSymbol.disabled = true;
      elements.elementInfoSymbol.setAttribute('aria-expanded', 'false');
      elements.elementInfoSymbol.removeAttribute('aria-label');
      elements.elementInfoSymbol.removeAttribute('title');
      if (elements.elementInfoSymbol.dataset.elementId) {
        delete elements.elementInfoSymbol.dataset.elementId;
      }
    }
    if (elements.elementInfoCategoryButton) {
      elements.elementInfoCategoryButton.textContent = '';
      elements.elementInfoCategoryButton.disabled = true;
      elements.elementInfoCategoryButton.setAttribute('aria-expanded', 'false');
      elements.elementInfoCategoryButton.removeAttribute('aria-label');
      elements.elementInfoCategoryButton.removeAttribute('title');
      if (elements.elementInfoCategoryButton.dataset.familyId) {
        delete elements.elementInfoCategoryButton.dataset.familyId;
      }
    }
    if (isElementDetailsModalOpen()) {
      closeElementDetailsModal({ restoreFocus: false });
    }
    if (isElementFamilyModalOpen()) {
      closeElementFamilyModal({ restoreFocus: false });
    }
    if (panel.dataset.category) {
      delete panel.dataset.category;
    }
    if (content) {
      content.hidden = true;
    }
    if (placeholder) {
      placeholder.hidden = false;
    }
    return;
  }

  if (definition.category) {
    panel.dataset.category = definition.category;
  } else if (panel.dataset.category) {
    delete panel.dataset.category;
  }

  if (placeholder) {
    placeholder.hidden = true;
  }
  if (content) {
    content.hidden = false;
  }

  if (elements.elementInfoNumber) {
    elements.elementInfoNumber.textContent =
      definition.atomicNumber != null ? definition.atomicNumber : '—';
  }
  const { symbol, name } = getPeriodicElementDisplay(definition);
  if (elements.elementInfoSymbol) {
    const symbolButton = elements.elementInfoSymbol;
    const displaySymbol = symbol ?? '';
    symbolButton.textContent = displaySymbol;
    const hasSymbol = Boolean(displaySymbol);
    const hasName = Boolean(name);
    symbolButton.disabled = !(hasSymbol || hasName);
    if (!symbolButton.disabled) {
      const openLabel = hasName
        ? translateOrDefault(
            'index.sections.table.modal.open',
            `Open detailed sheet for ${name}`,
            { name, symbol: displaySymbol }
          )
        : translateOrDefault(
            'index.sections.table.modal.openSymbol',
            `Open detailed sheet for ${displaySymbol}`,
            { symbol: displaySymbol }
          );
      symbolButton.setAttribute('aria-label', openLabel);
      symbolButton.setAttribute('title', openLabel);
      symbolButton.dataset.elementId = definition.id;
    } else {
      symbolButton.removeAttribute('aria-label');
      symbolButton.removeAttribute('title');
      if (symbolButton.dataset.elementId) {
        delete symbolButton.dataset.elementId;
      }
    }
    const openElementId = elements.elementDetailsOverlay?.dataset?.elementId || null;
    symbolButton.setAttribute('aria-expanded', openElementId === definition.id ? 'true' : 'false');
  }
  if (elements.elementInfoName) {
    elements.elementInfoName.textContent = name ?? '';
  }
  if (elements.elementInfoCategoryButton) {
    const categoryButton = elements.elementInfoCategoryButton;
    const hasCategory = Boolean(definition.category);
    const label = hasCategory
      ? CATEGORY_LABELS[definition.category] || definition.category
      : '—';
    categoryButton.textContent = label;
    categoryButton.disabled = !hasCategory;
    const familyLabelText = translateOrDefault(
      'index.sections.table.details.family',
      'Famille'
    );
    const openFamilyId = elements.elementFamilyOverlay?.dataset?.familyId || null;
    if (hasCategory) {
      categoryButton.dataset.familyId = definition.category;
      const openLabel = translateOrDefault(
        'index.sections.table.family.open',
        `${familyLabelText ? `${familyLabelText} : ` : ''}${label}. Ouvrir la fiche famille.`,
        { family: label, label: familyLabelText || '' }
      );
      if (openLabel) {
        categoryButton.setAttribute('aria-label', openLabel);
        categoryButton.setAttribute('title', openLabel);
      } else {
        categoryButton.removeAttribute('aria-label');
        categoryButton.removeAttribute('title');
      }
      categoryButton.setAttribute('aria-expanded', openFamilyId === definition.category ? 'true' : 'false');
      if (isElementFamilyModalOpen() && openFamilyId === definition.category) {
        updateElementFamilyModalContent(definition.category);
      }
    } else {
      if (categoryButton.dataset.familyId) {
        delete categoryButton.dataset.familyId;
      }
      categoryButton.setAttribute('aria-expanded', 'false');
      categoryButton.removeAttribute('aria-label');
      categoryButton.removeAttribute('title');
      if (isElementFamilyModalOpen()) {
        closeElementFamilyModal({ restoreFocus: false });
      }
    }
  }
  const entry = gameState.elements?.[definition.id];
  const count = getElementCurrentCount(entry);
  const lifetimeCount = getElementLifetimeCount(entry);
  if (elements.elementInfoOwnedCount) {
    const displayCount = formatIntegerLocalized(count);
    const lifetimeDisplay = formatIntegerLocalized(lifetimeCount);
    elements.elementInfoOwnedCount.textContent = displayCount;
    const ownedAria = translateOrDefault(
      'scripts.app.table.info.ownedAria',
      `Copies actives\u00a0: ${displayCount}. Collectées au total\u00a0: ${lifetimeDisplay}`,
      { active: displayCount, lifetime: lifetimeDisplay }
    );
    elements.elementInfoOwnedCount.setAttribute('aria-label', ownedAria);
    const ownedTitle = translateOrDefault(
      'scripts.app.table.info.ownedTitle',
      `Copies actives\u00a0: ${displayCount}\nCollectées au total\u00a0: ${lifetimeDisplay}`,
      { active: displayCount, lifetime: lifetimeDisplay }
    );
    elements.elementInfoOwnedCount.setAttribute('title', ownedTitle);
  }
  if (elements.elementInfoCollection) {
    const rarityId = entry?.rarity || elementRarityIndex.get(definition.id);
    const rarityDef = rarityId ? GACHA_RARITY_MAP.get(rarityId) : null;
    const rarityLabel = rarityDef?.label || rarityId || '—';
    const hasRarityLabel = Boolean(rarityLabel && rarityLabel !== '—');
    const bonusDetails = [];
    const seenDetailTexts = new Set();
    const addDetail = detail => {
      const normalized = normalizeCollectionDetailText(detail);
      if (!normalized) {
        return;
      }
      if (seenDetailTexts.has(normalized)) {
        return;
      }
      seenDetailTexts.add(normalized);
      bonusDetails.push(detail.trim());
    };
    if (rarityId) {
      const overview = getCollectionBonusOverview(rarityId);
      overview.forEach(addDetail);
    }

    if (!bonusDetails.length && rarityDef?.description) {
      addDetail(rarityDef.description);
    }

    if (bonusDetails.length) {
      elements.elementInfoCollection.textContent = hasRarityLabel
        ? `${rarityLabel} · ${bonusDetails.join(' · ')}`
        : bonusDetails.join(' · ');
    } else {
      elements.elementInfoCollection.textContent = rarityLabel;
    }
  }

  if (isElementDetailsModalOpen()) {
    const openId = elements.elementDetailsOverlay?.dataset?.elementId;
    if (openId === definition.id) {
      updateElementDetailsModalContent(definition);
    }
  }
}

function selectPeriodicElement(id, { focus = false } = {}) {
  if (!id || !periodicElementIndex.has(id)) {
    selectedElementId = null;
    periodicCells.forEach(cell => {
      cell.classList.remove('is-selected');
      cell.setAttribute('aria-pressed', 'false');
    });
    updateElementInfoPanel(null);
    return;
  }

  selectedElementId = id;
  periodicCells.forEach((cell, elementId) => {
    const isSelected = elementId === id;
    cell.classList.toggle('is-selected', isSelected);
    cell.setAttribute('aria-pressed', isSelected ? 'true' : 'false');
  });

  const definition = periodicElementIndex.get(id);
  updateElementInfoPanel(definition);

  if (focus) {
    const target = periodicCells.get(id);
    if (target) {
      target.focus();
    }
  }
}

function renderPeriodicTable() {
  if (!elements.periodicTable) return;
  const infoPanel = elements.elementInfoPanel;
  const summaryTile = elements.collectionSummaryTile;
  if (infoPanel) {
    infoPanel.remove();
  }
  if (summaryTile) {
    summaryTile.remove();
  }

  elements.periodicTable.innerHTML = '';
  periodicCells.clear();

  if (infoPanel) {
    elements.periodicTable.appendChild(infoPanel);
  }
  if (summaryTile) {
    elements.periodicTable.appendChild(summaryTile);
  }

  if (!periodicElements.length) {
    const placeholder = document.createElement('p');
    placeholder.className = 'periodic-placeholder';
    placeholder.textContent = t('scripts.app.table.placeholder');
    elements.periodicTable.appendChild(placeholder);
    if (elements.collectionProgress) {
      elements.collectionProgress.textContent = t('scripts.app.collection.pending');
    }
    selectPeriodicElement(null);
    return;
  }

  const fragment = document.createDocumentFragment();
  periodicElements.forEach(def => {
    const cell = document.createElement('button');
    cell.type = 'button';
    cell.className = 'periodic-element';
    cell.dataset.elementId = def.id;
    cell.dataset.category = def.category ?? 'unknown';
    if (def.atomicNumber != null) {
      cell.dataset.atomicNumber = String(def.atomicNumber);
    }
    if (def.period != null) {
      cell.dataset.period = String(def.period);
    }
    if (def.group != null) {
      cell.dataset.group = String(def.group);
    }
    if (def.gachaId) {
      cell.dataset.gachaId = def.gachaId;
    }
    const rarityId = elementRarityIndex.get(def.id);
    if (rarityId) {
      cell.dataset.rarity = rarityId;
      const rarityDef = GACHA_RARITY_MAP.get(rarityId);
      if (rarityDef?.color) {
        cell.dataset.rarityColor = rarityDef.color;
      } else {
        delete cell.dataset.rarityColor;
      }
    } else {
      delete cell.dataset.rarityColor;
      cell.style.removeProperty('--rarity-color');
    }
    const { row, column } = def.position || {};
    if (column) {
      cell.style.gridColumn = String(column);
    }
    if (row) {
      cell.style.gridRow = String(row);
    }
    const { symbol, name } = getPeriodicElementDisplay(def);
    const displaySymbol = symbol || '';
    const displayName = name || '';
    const massText = formatAtomicMass(def.atomicMass);
    const labelParts = [];
    if (displayName) {
      if (displaySymbol) {
        labelParts.push(
          translateOrDefault(
            'scripts.app.table.aria.nameWithSymbol',
            `${displayName} (${displaySymbol})`,
            { name: displayName, symbol: displaySymbol }
          )
        );
      } else {
        labelParts.push(
          translateOrDefault(
            'scripts.app.table.aria.name',
            displayName,
            { name: displayName }
          )
        );
      }
    } else if (displaySymbol) {
      labelParts.push(
        translateOrDefault(
          'scripts.app.table.aria.symbol',
          displaySymbol,
          { symbol: displaySymbol }
        )
      );
    }
    if (def.atomicNumber != null) {
      labelParts.push(
        translateOrDefault(
          'scripts.app.table.aria.atomicNumber',
          `numéro atomique ${def.atomicNumber}`,
          { number: def.atomicNumber }
        )
      );
    }
    if (massText) {
      labelParts.push(
        translateOrDefault(
          'scripts.app.table.aria.atomicMass',
          `masse atomique ${massText}`,
          { mass: massText }
        )
      );
    }
    if (def.category) {
      const categoryLabel = CATEGORY_LABELS[def.category] || def.category;
      labelParts.push(
        translateOrDefault(
          'scripts.app.table.aria.family',
          `famille ${categoryLabel}`,
          { family: categoryLabel }
        )
      );
    }
    cell.setAttribute('aria-label', labelParts.join(', '));

    cell.innerHTML = `
      <span class="periodic-element__symbol">${displaySymbol}</span>
      <span class="periodic-element__number">${def.atomicNumber}</span>
    `;
    cell.setAttribute('aria-pressed', 'false');
    cell.addEventListener('click', () => selectPeriodicElement(def.id));
    cell.addEventListener('focus', () => selectPeriodicElement(def.id));

    const state = gameState.elements[def.id];
    const isOwned = hasElementLifetime(state);
    applyPeriodicCellCollectionColor(cell, isOwned);
    if (isOwned) {
      cell.classList.add('is-owned');
    }

    periodicCells.set(def.id, cell);
    fragment.appendChild(cell);
  });

  elements.periodicTable.appendChild(fragment);
  if (selectedElementId && periodicCells.has(selectedElementId)) {
    selectPeriodicElement(selectedElementId);
  } else if (periodicElements.length) {
    selectPeriodicElement(periodicElements[0].id);
  } else {
    selectPeriodicElement(null);
  }
  updateCollectionDisplay();
}

function updateCollectionDisplay() {
  const elementEntries = Object.values(gameState.elements || {});
  const ownedCount = elementEntries.reduce((total, entry) => {
    return total + (hasElementLifetime(entry) ? 1 : 0);
  }, 0);
  const total = TOTAL_ELEMENT_COUNT || elementEntries.length;
  const lifetimeTotal = elementEntries.reduce((sum, entry) => {
    return sum + getElementLifetimeCount(entry);
  }, 0);
  const currentTotal = elementEntries.reduce((sum, entry) => {
    return sum + getElementCurrentCount(entry);
  }, 0);

  if (elements.collectionProgress) {
    if (total > 0) {
      const ownedDisplay = formatIntegerLocalized(ownedCount);
      const totalDisplay = formatIntegerLocalized(total);
      elements.collectionProgress.textContent = translateOrDefault(
        'scripts.app.table.collection.progress',
        `Collection\u00a0: ${ownedDisplay} / ${totalDisplay} éléments`,
        { owned: ownedDisplay, total: totalDisplay }
      );
    } else {
      elements.collectionProgress.textContent = t('scripts.app.collection.pending');
    }
  }

  if (elements.gachaOwnedSummary) {
    if (total > 0) {
      const ownedDisplay = formatIntegerLocalized(ownedCount);
      const totalDisplay = formatIntegerLocalized(total);
      const ratio = (ownedCount / total) * 100;
      let ratioValue = ratio;
      let ratioOptions = { maximumFractionDigits: 2 };
      if (!Number.isFinite(ratioValue) || ratioValue < 0) {
        ratioValue = 0;
      }
      if (ratioValue >= 99.95) {
        ratioValue = 100;
        ratioOptions = { maximumFractionDigits: 0 };
      } else if (ratioValue >= 10) {
        ratioOptions = { maximumFractionDigits: 1 };
      }
      const ratioDisplay = formatNumberLocalized(ratioValue, ratioOptions);
      elements.gachaOwnedSummary.textContent = translateOrDefault(
        'scripts.app.table.collection.summary',
        `Collection\u00a0: ${ownedDisplay} / ${totalDisplay} éléments (${ratioDisplay}\u00a0%)`,
        { owned: ownedDisplay, total: totalDisplay, ratio: ratioDisplay }
      );
    } else {
      elements.gachaOwnedSummary.textContent = t('scripts.app.collection.pending');
    }
  }

  const currentDisplay = formatIntegerLocalized(currentTotal);
  const lifetimeDisplay = formatIntegerLocalized(lifetimeTotal);

  if (elements.collectionSummaryCurrent) {
    elements.collectionSummaryCurrent.textContent = currentDisplay;
  }

  if (elements.collectionSummaryLifetime) {
    elements.collectionSummaryLifetime.textContent = lifetimeDisplay;
  }

  if (elements.collectionSummaryTile) {
    const summaryLabel = translateOrDefault(
      'scripts.app.table.summaryTile.aria',
      `Total actuel\u00a0: ${currentDisplay} · Total historique\u00a0: ${lifetimeDisplay}`,
      { current: currentDisplay, lifetime: lifetimeDisplay }
    );
    elements.collectionSummaryTile.setAttribute('aria-label', summaryLabel);
    elements.collectionSummaryTile.setAttribute('title', summaryLabel);
  }

  periodicCells.forEach((cell, id) => {
    const entry = gameState.elements?.[id];
    const isOwned = hasElementLifetime(entry);
    cell.classList.toggle('is-owned', isOwned);
    applyPeriodicCellCollectionColor(cell, isOwned);
  });

  if (selectedElementId && periodicElementIndex.has(selectedElementId)) {
    updateElementInfoPanel(periodicElementIndex.get(selectedElementId));
  }

  updateGachaRarityProgress();
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
    if (step.id === 'frenzy') {
      // Le multiplicateur de frénésie reste actif en jeu mais n'est pas affiché dans la décomposition.
      return;
    }
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
let biggerGame = null;
let waveGame = null;
let balanceGame = null;
let quantum2048Game = null;
let sokobanGame = null;
let taquinGame = null;
let linkGame = null;
let lightsOutGame = null;
let gameOfLifeGame = null;
let escapeGame = null;
let starBridgesGame = null;
let starsWarGame = null;
let pipeTapGame = null;
let colorStackGame = null;
let motocrossGame = null;
let twinsGame = null;
let hexGame = null;
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

const TOUCH_POINTER_CLICK_SUPPRESSION_MS = 320;
let suppressPointerClickUntil = 0;

function getHighResTimestamp() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

function markTouchPointerManualClick(now = getHighResTimestamp()) {
  suppressPointerClickUntil = now + TOUCH_POINTER_CLICK_SUPPRESSION_MS;
}

function shouldSuppressPointerDerivedClick(now = getHighResTimestamp()) {
  if (suppressPointerClickUntil <= 0) {
    return false;
  }
  if (now <= suppressPointerClickUntil) {
    return true;
  }
  if (now - suppressPointerClickUntil > TOUCH_POINTER_CLICK_SUPPRESSION_MS * 4) {
    suppressPointerClickUntil = 0;
  }
  return false;
}

function isTouchLikePointerEvent(event) {
  const pointerType = typeof event?.pointerType === 'string'
    ? event.pointerType.toLowerCase()
    : '';
  return pointerType === 'touch' || pointerType === 'pen';
}

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

const BASE_ATOM_REBOUND_AMPLITUDE_SCALE = 0.5;

function getAtomReboundAmplitudeScale() {
  return BASE_ATOM_REBOUND_AMPLITUDE_SCALE * getAtomAnimationSettings().amplitudeScale;
}

function getAtomMotionScale() {
  return getAtomAnimationSettings().motionScale;
}

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

function isEcoPerformanceModeActive(modeId = performanceModeState?.id) {
  return modeId === 'eco';
}

function resetAtomAnimationState() {
  atomAnimationState.intensity = 0;
  atomAnimationState.posX = 0;
  atomAnimationState.posY = 0;
  atomAnimationState.velX = 0;
  atomAnimationState.velY = 0;
  atomAnimationState.tilt = 0;
  atomAnimationState.tiltVelocity = 0;
  atomAnimationState.squash = 0;
  atomAnimationState.squashVelocity = 0;
  atomAnimationState.spinPhase = Math.random() * Math.PI * 2;
  atomAnimationState.noisePhase = Math.random() * Math.PI * 2;
  atomAnimationState.noiseOffset = Math.random() * Math.PI * 2;
  atomAnimationState.impulseTimer = 0;
  atomAnimationState.lastInputIntensity = 0;
  atomAnimationState.lastTime = null;
}

function resetAtomSpringStyles() {
  const visual = getAtomVisualElement();
  if (!visual) return;
  visual.style.setProperty('--shake-x', '0px');
  visual.style.setProperty('--shake-y', '0px');
  visual.style.setProperty('--shake-rot', '0deg');
  visual.style.setProperty('--shake-scale-x', '1');
  visual.style.setProperty('--shake-scale-y', '1');
}

function resetGlowEffects() {
  const button = elements.atomButton;
  if (!button) return;
  button.style.setProperty('--glow-strength', '0');
  button.style.setProperty('--glow-color', interpolateGlowColor(0));
  button.classList.remove('is-active');
}

let ecoClickFeedbackTimeoutId = null;
let minimalAtomShakeTimeoutId = null;

function clearEcoClickFeedbackTimeout() {
  if (ecoClickFeedbackTimeoutId != null) {
    clearTimeout(ecoClickFeedbackTimeoutId);
    ecoClickFeedbackTimeoutId = null;
  }
}

function cancelMinimalAtomShakeCue() {
  if (minimalAtomShakeTimeoutId != null) {
    clearTimeout(minimalAtomShakeTimeoutId);
    minimalAtomShakeTimeoutId = null;
  }
}

function setEcoClickFeedbackActive(active) {
  const image = getAtomImageElement();
  if (!image) return;
  if (active) {
    image.classList.add('atom-image--eco-pressed');
  } else {
    image.classList.remove('atom-image--eco-pressed');
  }
}

function activateEcoClickFeedback() {
  if (!isEcoPerformanceModeActive()) return;
  clearEcoClickFeedbackTimeout();
  setEcoClickFeedbackActive(true);
}

function triggerEcoClickFeedbackPulse(duration = 140) {
  if (!isEcoPerformanceModeActive()) return;
  activateEcoClickFeedback();
  ecoClickFeedbackTimeoutId = setTimeout(() => {
    setEcoClickFeedbackActive(false);
    ecoClickFeedbackTimeoutId = null;
  }, Math.max(0, duration));
}

function resetEcoClickFeedback() {
  clearEcoClickFeedbackTimeout();
  setEcoClickFeedbackActive(false);
}

function triggerMinimalAtomClickCue() {
  if (!isAtomAnimationSuppressed()) {
    return;
  }
  const visual = getAtomVisualElement();
  if (!visual) {
    return;
  }
  const magnitude = 1.4;
  const angle = Math.random() * Math.PI * 2;
  const offsetX = Math.cos(angle) * magnitude;
  const offsetY = Math.sin(angle) * (magnitude * 0.6);
  const rotation = Math.sin(angle * 1.35) * 1.2;
  visual.style.setProperty('--shake-x', `${offsetX.toFixed(2)}px`);
  visual.style.setProperty('--shake-y', `${offsetY.toFixed(2)}px`);
  visual.style.setProperty('--shake-rot', `${rotation.toFixed(2)}deg`);
  visual.style.setProperty('--shake-scale-x', '1');
  visual.style.setProperty('--shake-scale-y', '1');
  cancelMinimalAtomShakeCue();
  minimalAtomShakeTimeoutId = setTimeout(() => {
    minimalAtomShakeTimeoutId = null;
    resetAtomSpringStyles();
  }, 90);
}

function syncAtomVisualForPerformanceMode(modeId = performanceModeState?.id) {
  if (!modeId) return;
  resetEcoClickFeedback();
  resetAtomAnimationState();
  resetAtomSpringStyles();
  resetGlowEffects();
  targetClickStrength = 0;
  displayedClickStrength = 0;
  clickHistory.length = 0;
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
  if (isEcoPerformanceModeActive()) {
    resetGlowEffects();
    return;
  }
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

  const motionScale = getAtomMotionScale();
  const motionStrength = 0.35 + motionScale * 0.65;
  const drive = Math.max(targetClickStrength, displayedClickStrength, 0);
  const baseIntensity = Math.max(state.intensity, drive);
  const energy = Math.pow(Math.max(0, baseIntensity), 0.6);
  const impulseAngle = Math.random() * Math.PI * 2;
  const impulseStrength = (180 + energy * 520) * motionStrength;

  state.velX += Math.cos(impulseAngle) * impulseStrength;
  state.velY += Math.sin(impulseAngle) * impulseStrength;

  const recenter = (20 + energy * 60) * motionStrength;
  state.velX += -state.posX * recenter;
  state.velY += -state.posY * recenter * 1.05;

  const wobbleKick = (Math.random() - 0.5) * (220 + energy * 320) * motionStrength;
  state.tiltVelocity += wobbleKick;
  state.squashVelocity += (Math.random() - 0.35) * (160 + energy * 220) * motionStrength;

  state.spinPhase += (Math.random() - 0.5) * (0.45 + energy * 1.2) * motionStrength;
  state.noiseOffset = Math.random() * Math.PI * 2;

  const intensityBoost = (0.25 + drive * 0.4) * motionStrength;
  state.intensity = Math.min(1, baseIntensity + intensityBoost);
  const impulseCooldown = 0.06 + (1 - motionScale) * 0.04;
  state.impulseTimer = Math.min(state.impulseTimer, impulseCooldown);
}

function updateAtomSpring(now = performance.now(), drive = 0) {
  const visual = getAtomVisualElement();
  if (!visual) return;
  const state = atomAnimationState;
  if (state.lastTime == null) {
    state.lastTime = now;
  }

  const motionScale = getAtomMotionScale();
  const motionStrength = 0.35 + motionScale * 0.65;
  let delta = (now - state.lastTime) / 1000;
  if (!Number.isFinite(delta) || delta < 0) {
    delta = 0;
  }
  delta = Math.min(delta, 0.05);
  state.lastTime = now;

  const input = Math.max(0, Math.min(1, drive));
  state.intensity += (input - state.intensity) * Math.min(1, delta * 9 * motionStrength);
  const intensity = state.intensity;
  const energy = Math.pow(intensity, 0.65);

  const rangeX = 6 + energy * 34 + intensity * 6;
  const rangeY = 7 + energy * 40 + intensity * 8;
  const centerPull = (10 + energy * 24) * motionStrength;
  const damping = (4.8 + energy * 16) * motionStrength;
  const maxSpeed = (120 + energy * 420) * motionStrength;

  state.velX -= (state.posX / Math.max(rangeX, 1)) * centerPull * delta;
  state.velY -= (state.posY / Math.max(rangeY, 1)) * centerPull * delta;

  state.velX -= state.velX * damping * delta;
  state.velY -= state.velY * damping * delta;

  state.noisePhase += delta * (1.2 + energy * 6.4) * motionStrength;
  const noiseX =
    Math.sin(state.noisePhase * 1.35 + state.noiseOffset) * (0.34 + energy * 0.9) +
    Math.cos(state.noisePhase * 2.35 + state.noiseOffset * 1.7) * (0.22 + energy * 0.55);
  const noiseY =
    Math.cos(state.noisePhase * 1.55 + state.noiseOffset * 0.4) * (0.32 + energy * 0.82) +
    Math.sin(state.noisePhase * 2.1 + state.noiseOffset) * (0.2 + energy * 0.5);
  const noiseStrength = (12 + energy * 210 + intensity * 30) * motionStrength;
  state.velX += noiseX * noiseStrength * delta;
  state.velY += noiseY * noiseStrength * delta;

  state.impulseTimer -= delta;
  const impulseDelay = Math.max(0.085, 0.42 - energy * 0.32 - intensity * 0.1) + (1 - motionScale) * 0.12;
  if (state.impulseTimer <= 0) {
    const burstAngle = Math.random() * Math.PI * 2;
    const burstStrength = (16 + energy * 240 + intensity * 120) * motionStrength;
    state.velX += Math.cos(burstAngle) * burstStrength;
    state.velY += Math.sin(burstAngle) * burstStrength;
    state.impulseTimer = impulseDelay;
  }

  const gain = Math.max(0, input - state.lastInputIntensity);
  if (gain > 0.001) {
    const gainAngle = Math.random() * Math.PI * 2;
    const gainStrength = (38 + gain * 480 + intensity * 140) * motionStrength;
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

  state.spinPhase += delta * (2.4 + energy * 14.5) * motionStrength;
  if (!Number.isFinite(state.spinPhase)) state.spinPhase = 0;
  if (!Number.isFinite(state.noisePhase)) state.noisePhase = 0;
  if (state.spinPhase > Math.PI * 1000) state.spinPhase %= Math.PI * 2;
  if (state.noisePhase > Math.PI * 1000) state.noisePhase %= Math.PI * 2;

  const spin = Math.sin(state.spinPhase) * (4 + energy * 28) * motionStrength;

  const tiltTarget =
    (state.posX / Math.max(rangeX, 1)) * (10 + energy * 18) * motionStrength +
    (state.velX / Math.max(maxSpeed, 1)) * (34 + energy * 30) * motionStrength;
  const tiltSpring = (24 + energy * 32) * motionStrength;
  const tiltDamping = (7 + energy * 14) * motionStrength;
  state.tiltVelocity += (tiltTarget - state.tilt) * tiltSpring * delta;
  state.tiltVelocity -= state.tiltVelocity * tiltDamping * delta;
  state.tilt += state.tiltVelocity * delta;

  const verticalMomentum = state.velY / Math.max(maxSpeed, 1);
  const squashTarget = Math.max(-1, Math.min(1, -verticalMomentum * (1.6 + energy * 1.25) * motionStrength));
  const squashSpring = (22 + energy * 28) * motionStrength;
  const squashDamping = (9 + energy * 12) * motionStrength;
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

  const amplitudeScale = getAtomReboundAmplitudeScale();
  const offsetX = state.posX * amplitudeScale;
  const offsetY = state.posY * amplitudeScale;
  visual.style.setProperty('--shake-x', `${offsetX.toFixed(2)}px`);
  visual.style.setProperty('--shake-y', `${offsetY.toFixed(2)}px`);
  visual.style.setProperty(
    '--shake-rot',
    `${((state.tilt + spin) * amplitudeScale).toFixed(2)}deg`
  );
  visual.style.setProperty('--shake-scale-x', '1');
  visual.style.setProperty('--shake-scale-y', '1');
}

function updateClickVisuals(now = performance.now()) {
  if (isAtomAnimationSuppressed()) {
    clickHistory.length = 0;
    targetClickStrength = 0;
    displayedClickStrength = 0;
    resetGlowEffects();
    return;
  }
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
  if (areAtomAnimationsEnabled()) {
    clickHistory.push(now);
  }
  updateClickVisuals(now);
  triggerEcoClickFeedbackPulse();
  if (isAtomAnimationSuppressed()) {
    triggerMinimalAtomClickCue();
  } else {
    injectAtomImpulse(now);
  }
  if (
    typeof globalThis !== 'undefined'
    && typeof globalThis.registerTicketStarClickReduction === 'function'
  ) {
    globalThis.registerTicketStarClickReduction(1);
  }
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

function normalizeStarCount(value, fallback) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.max(1, Math.floor(numeric));
  }
  const fallbackNumeric = Number(fallback);
  if (Number.isFinite(fallbackNumeric) && fallbackNumeric > 0) {
    return Math.max(1, Math.floor(fallbackNumeric));
  }
  return 1;
}

function randomBetween(min, max) {
  const lower = Number(min);
  const upper = Number(max);
  const hasLower = Number.isFinite(lower);
  const hasUpper = Number.isFinite(upper);
  if (!hasLower && !hasUpper) {
    return Math.random();
  }
  if (!hasLower) {
    return hasUpper ? upper : Math.random();
  }
  if (!hasUpper) {
    return lower;
  }
  if (upper <= lower) {
    return lower;
  }
  return lower + Math.random() * (upper - lower);
}

function resolveRange(range, fallbackMin, fallbackMax) {
  const minCandidate = Number(range?.min);
  const maxCandidate = Number(range?.max);
  const min = Number.isFinite(minCandidate) ? minCandidate : fallbackMin;
  let max = Number.isFinite(maxCandidate) ? maxCandidate : fallbackMax;
  if (max < min) {
    max = min;
  }
  return { min, max };
}

const STARFIELD_CONFIG = CONFIG.presentation?.starfield ?? {};
const STARFIELD_DEFAULT_COUNT = normalizeStarCount(STARFIELD_CONFIG.starCount, 60);
const STARFIELD_ECO_COUNT = normalizeStarCount(
  STARFIELD_CONFIG.ecoStarCount,
  Math.max(8, Math.round(STARFIELD_DEFAULT_COUNT / 2.5))
);
const STARFIELD_MODE_SETTINGS = Object.freeze({
  fluid: Object.freeze({
    count: STARFIELD_DEFAULT_COUNT,
    animated: true,
    scale: Object.freeze({ min: 0.6, max: 2.3 }),
    opacity: Object.freeze({ min: 0.26, max: 0.8 })
  }),
  eco: Object.freeze({
    count: STARFIELD_ECO_COUNT,
    animated: false,
    scale: Object.freeze({ min: 0.55, max: 1.2 }),
    opacity: Object.freeze({ min: 0.3, max: 0.6 })
  })
});
let starfieldInitializedForMode = null;

function resolveStarfieldMode(modeId) {
  if (typeof modeId === 'string') {
    const normalized = modeId.trim();
    if (normalized && Object.prototype.hasOwnProperty.call(STARFIELD_MODE_SETTINGS, normalized)) {
      return normalized;
    }
  }
  if (Object.prototype.hasOwnProperty.call(STARFIELD_MODE_SETTINGS, PERFORMANCE_MODE_DEFAULT_ID)) {
    return PERFORMANCE_MODE_DEFAULT_ID;
  }
  return 'fluid';
}

function getStarfieldSettings(modeId) {
  const resolved = resolveStarfieldMode(modeId);
  return {
    id: resolved,
    settings: STARFIELD_MODE_SETTINGS[resolved]
  };
}

const ATOM_IMAGE_FALLBACK = 'Assets/Image/Atom.png';

function initStarfield(modeId = performanceModeState?.id ?? PERFORMANCE_MODE_DEFAULT_ID) {
  if (!elements.starfield) return;
  const { id: resolvedMode, settings } = getStarfieldSettings(modeId);
  if (!settings) return;
  const fragment = document.createDocumentFragment();
  const { min: scaleMin, max: scaleMax } = resolveRange(settings.scale, 0.6, 2.3);
  const { min: opacityMin, max: opacityMax } = resolveRange(settings.opacity, 0.26, 0.54);
  const count = Math.max(0, Math.floor(settings.count || 0));
  for (let i = 0; i < count; i += 1) {
    const star = document.createElement('span');
    star.className = 'starfield__star';
    star.style.left = `${Math.random() * 100}%`;
    star.style.top = `${Math.random() * 100}%`;
    star.style.setProperty('--star-scale', randomBetween(scaleMin, scaleMax).toFixed(2));
    star.style.setProperty('--star-opacity', randomBetween(opacityMin, opacityMax).toFixed(2));
    if (settings.animated) {
      const duration = randomBetween(4, 10);
      star.style.animationDuration = `${duration.toFixed(2)}s`;
      star.style.animationDelay = `-${randomBetween(0, duration).toFixed(2)}s`;
    }
    fragment.appendChild(star);
  }
  if (typeof elements.starfield.replaceChildren === 'function') {
    elements.starfield.replaceChildren(fragment);
  } else {
    elements.starfield.textContent = '';
    elements.starfield.appendChild(fragment);
  }
  elements.starfield.dataset.starfieldMode = settings.animated ? 'animated' : 'static';
  starfieldInitializedForMode = resolvedMode;
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

const MAIN_ATOM_IMAGES = (() => {
  const source = Array.isArray(APP_DATA.MAIN_ATOM_IMAGES) && APP_DATA.MAIN_ATOM_IMAGES.length
    ? APP_DATA.MAIN_ATOM_IMAGES
    : Array.isArray(APP_DATA.DEFAULT_MAIN_ATOM_IMAGES) && APP_DATA.DEFAULT_MAIN_ATOM_IMAGES.length
      ? APP_DATA.DEFAULT_MAIN_ATOM_IMAGES
      : [];
  if (!source.length) {
    return [ATOM_IMAGE_FALLBACK];
  }
  return source.map(value => String(value));
})();

const ALTERNATE_MAIN_ATOM_IMAGES = (() => {
  const source = Array.isArray(APP_DATA.ALTERNATE_MAIN_ATOM_IMAGES)
    && APP_DATA.ALTERNATE_MAIN_ATOM_IMAGES.length
      ? APP_DATA.ALTERNATE_MAIN_ATOM_IMAGES
      : Array.isArray(APP_DATA.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES)
        && APP_DATA.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES.length
        ? APP_DATA.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES
        : [];
  if (!source.length) {
    return [];
  }
  return source.map(value => String(value));
})();

function buildAtomButtonImagePool(sourceImages) {
  const unique = new Set();
  if (Array.isArray(sourceImages)) {
    sourceImages.forEach(value => {
      if (typeof value === 'string') {
        const normalized = value.trim();
        if (normalized) {
          unique.add(normalized);
        }
      }
    });
  }
  if (!unique.size) {
    unique.add(ATOM_IMAGE_FALLBACK);
  } else if (!unique.has(ATOM_IMAGE_FALLBACK)) {
    unique.add(ATOM_IMAGE_FALLBACK);
  }
  return Array.from(unique);
}

const DEFAULT_ATOM_BUTTON_IMAGES = buildAtomButtonImagePool(MAIN_ATOM_IMAGES);
const ALTERNATE_ATOM_BUTTON_IMAGES = buildAtomButtonImagePool(ALTERNATE_MAIN_ATOM_IMAGES);

function getAtomButtonImagePool() {
  const pool = isAtomImageVariantEnabled()
    ? ALTERNATE_ATOM_BUTTON_IMAGES
    : DEFAULT_ATOM_BUTTON_IMAGES;
  return Array.isArray(pool) && pool.length ? pool : [ATOM_IMAGE_FALLBACK];
}

function applyAtomVariantVisualState() {
  if (typeof document === 'undefined' || !document.body) {
    return;
  }
  document.body.dataset.atomVariant = isAtomImageVariantEnabled() ? 'alternate' : 'default';
}

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
  const pool = getAtomButtonImagePool();
  const current = image.dataset.atomImage || image.getAttribute('src') || ATOM_IMAGE_FALLBACK;
  let next = pickRandom(pool) || ATOM_IMAGE_FALLBACK;
  if (pool.length > 1) {
    let attempts = 0;
    while (next === current && attempts < 4) {
      next = pickRandom(pool) || ATOM_IMAGE_FALLBACK;
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

  const buttonSize = elements.atomButtonCore
    ? elements.atomButtonCore.offsetWidth
    : elements.atomButton
    ? elements.atomButton.offsetWidth
    : Math.min(window.innerWidth, window.innerHeight) * 0.2;
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

  const buttonRect = elements.atomButtonCore
    ? elements.atomButtonCore.getBoundingClientRect()
    : elements.atomButton
    ? elements.atomButton.getBoundingClientRect()
    : null;
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
  queueManualApcGain(critResult.amount);
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
  globalThis.resetTouchTrackingState = resetTouchTrackingState;
  globalThis.forceUnlockScrollSafe = forceUnlockScrollSafe;
}

function shouldTriggerGlobalClick(event) {
  if (!isGamePageActive()) return false;
  if (event.target.closest('.app-header')) return false;
  if (event.target.closest('.status-bar')) return false;
  return true;
}

const SCROLL_BEHAVIOR_MODES = Object.freeze({
  DEFAULT: 'default',
  FORCE: 'force',
  LOCK: 'lock'
});

const SCROLL_BEHAVIOR_ALIAS_MAP = new Map([
  ['default', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['normal', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['auto', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['free', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['force', SCROLL_BEHAVIOR_MODES.FORCE],
  ['unlock', SCROLL_BEHAVIOR_MODES.FORCE],
  ['enable', SCROLL_BEHAVIOR_MODES.FORCE],
  ['lock', SCROLL_BEHAVIOR_MODES.LOCK],
  ['locked', SCROLL_BEHAVIOR_MODES.LOCK],
  ['none', SCROLL_BEHAVIOR_MODES.LOCK],
  ['disable', SCROLL_BEHAVIOR_MODES.LOCK]
]);

const supportsPassiveEventListeners = (() => {
  if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
    return false;
  }
  let supported = false;
  try {
    const options = Object.defineProperty({}, 'passive', {
      get() {
        supported = true;
        return false;
      }
    });
    window.addEventListener('testPassive', null, options);
    window.removeEventListener('testPassive', null, options);
  } catch (error) {
    supported = false;
  }
  return supported;
})();

const passiveEventListenerOptions = supportsPassiveEventListeners ? { passive: true } : false;
const passiveCaptureEventListenerOptions = supportsPassiveEventListeners
  ? { passive: true, capture: true }
  : true;
const nonPassiveEventListenerOptions = supportsPassiveEventListeners ? { passive: false } : false;
const supportsGlobalPointerEvents = typeof globalThis !== 'undefined'
  && typeof globalThis.PointerEvent === 'function';

let activeBodyScrollBehavior = SCROLL_BEHAVIOR_MODES.DEFAULT;
let bodyScrollTouchMoveListenerAttached = false;
const activeTouchIdentifiers = new Map();
const activePointerTouchIds = new Map();
const TOUCH_TRACKING_STALE_THRESHOLD_MS = 500;
const MAX_SCROLL_UNLOCK_FAILURES = 3;
let pendingScrollUnlockCheckId = null;
let consecutiveScrollUnlockFailures = 0;

function getCurrentTimestamp() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

function pruneStaleTouchTracking(now = getCurrentTimestamp()) {
  const cutoff = now - TOUCH_TRACKING_STALE_THRESHOLD_MS;
  activeTouchIdentifiers.forEach((timestamp, identifier) => {
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      activeTouchIdentifiers.delete(identifier);
    }
  });
  activePointerTouchIds.forEach((timestamp, identifier) => {
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      activePointerTouchIds.delete(identifier);
    }
  });
}

function cancelScheduledScrollUnlockCheck() {
  const timerHost = typeof window !== 'undefined'
    ? window
    : (typeof globalThis !== 'undefined' ? globalThis : null);
  if (!timerHost || typeof timerHost.clearTimeout !== 'function') {
    pendingScrollUnlockCheckId = null;
    return;
  }
  if (pendingScrollUnlockCheckId !== null) {
    timerHost.clearTimeout(pendingScrollUnlockCheckId);
    pendingScrollUnlockCheckId = null;
  }
}

function scheduleScrollUnlockCheck(delayMs = TOUCH_TRACKING_STALE_THRESHOLD_MS) {
  const timerHost = typeof window !== 'undefined'
    ? window
    : (typeof globalThis !== 'undefined' ? globalThis : null);
  if (!timerHost || typeof timerHost.setTimeout !== 'function') {
    pruneStaleTouchTracking();
    if (!hasRemainingActiveTouches()) {
      consecutiveScrollUnlockFailures = 0;
      applyActivePageScrollBehavior();
    }
    return;
  }
  if (pendingScrollUnlockCheckId !== null && typeof timerHost.clearTimeout === 'function') {
    timerHost.clearTimeout(pendingScrollUnlockCheckId);
  }
  const timeout = Math.max(16, Number.isFinite(delayMs) ? delayMs : TOUCH_TRACKING_STALE_THRESHOLD_MS);
  pendingScrollUnlockCheckId = timerHost.setTimeout(() => {
    pendingScrollUnlockCheckId = null;
    pruneStaleTouchTracking();
    if (!hasRemainingActiveTouches()) {
      consecutiveScrollUnlockFailures = 0;
      applyActivePageScrollBehavior();
    } else {
      consecutiveScrollUnlockFailures += 1;
      if (consecutiveScrollUnlockFailures >= MAX_SCROLL_UNLOCK_FAILURES) {
        resetTouchTrackingState();
      } else {
        scheduleScrollUnlockCheck(TOUCH_TRACKING_STALE_THRESHOLD_MS);
      }
    }
  }, timeout);
}

function normalizeTouchIdentifier(touch) {
  if (!touch || typeof touch !== 'object') {
    return null;
  }
  if (Number.isFinite(touch.identifier)) {
    return `touch:${touch.identifier}`;
  }
  if (Number.isFinite(touch.pointerId)) {
    return `pointer:${touch.pointerId}`;
  }
  return null;
}

function forceUnlockScrollSafe(options = {}) {
  const { reapplyScrollBehavior = true } = options || {};
  resetTouchTrackingState({ reapplyScrollBehavior: false });

  const scrollLockManager = typeof globalThis !== 'undefined'
    ? globalThis.ScrollLock
    : null;
  try {
    scrollLockManager?.unlock?.();
  } catch (error) {
    // Ignore failures from optional scroll lock managers.
  }

  if (typeof document !== 'undefined') {
    applyBodyScrollBehavior(SCROLL_BEHAVIOR_MODES.DEFAULT);
    const html = document.documentElement || null;
    const body = document.body || null;

    if (body) {
      body.style.removeProperty('overflow');
      body.style.removeProperty('touch-action');
      body.style.removeProperty('overscroll-behavior');
      body.classList?.remove?.('touch-scroll-lock');
      body.classList?.remove?.('touch-scroll-force');
    }

    if (html) {
      html.style.removeProperty('overflow');
      html.style.removeProperty('touch-action');
      html.style.removeProperty('overscroll-behavior');
      html.classList?.remove?.('touch-scroll-lock');
      html.classList?.remove?.('touch-scroll-force');
    }
  } else {
    activeBodyScrollBehavior = SCROLL_BEHAVIOR_MODES.DEFAULT;
  }

  if (reapplyScrollBehavior) {
    applyActivePageScrollBehavior();
  }
}

function registerActiveTouches(touchList, fullTouchList = null) {
  cancelScheduledScrollUnlockCheck();
  consecutiveScrollUnlockFailures = 0;

  let activeIdentifiers = null;
  if (fullTouchList && typeof fullTouchList.length === 'number') {
    activeIdentifiers = new Set();
    for (let index = 0; index < fullTouchList.length; index += 1) {
      const identifier = normalizeTouchIdentifier(fullTouchList[index]);
      if (identifier != null) {
        activeIdentifiers.add(identifier);
      }
    }

    activeTouchIdentifiers.forEach((_, identifier) => {
      if (!activeIdentifiers.has(identifier)) {
        activeTouchIdentifiers.delete(identifier);
      }
    });

    if (fullTouchList.length === 0) {
      try { activeTouchIdentifiers.clear?.(); } catch (error) {}
      try { activePointerTouchIds?.clear?.(); } catch (error) {}
      forceUnlockScrollSafe();
      return;
    }
  }

  if (!touchList || typeof touchList.length !== 'number') {
    return;
  }

  const now = getCurrentTimestamp();
  for (let index = 0; index < touchList.length; index += 1) {
    const identifier = normalizeTouchIdentifier(touchList[index]);
    if (identifier != null && (!activeIdentifiers || activeIdentifiers.has(identifier))) {
      activeTouchIdentifiers.set(identifier, now);
    }
  }
}

function unregisterActiveTouches(touchList, activeTouchList = null) {
  if (touchList && typeof touchList.length === 'number') {
    Array.from(touchList).forEach(touch => {
      const identifier = normalizeTouchIdentifier(touch);
      if (identifier) {
        activeTouchIdentifiers.delete(identifier);
      }
    });
  }
  if (activeTouchList && typeof activeTouchList.length === 'number') {
    const currentIdentifiers = new Set();
    Array.from(activeTouchList).forEach(touch => {
      const identifier = normalizeTouchIdentifier(touch);
      if (identifier) {
        currentIdentifiers.add(identifier);
      }
    });
    activeTouchIdentifiers.forEach((_, identifier) => {
      if (!currentIdentifiers.has(identifier)) {
        activeTouchIdentifiers.delete(identifier);
      }
    });
    if (activeTouchList.length === 0) {
      activePointerTouchIds.clear();
    }
  }
}

function hasRemainingActiveTouches(event) {
  if (Number.isFinite(event?.touches?.length) && event.touches.length > 0) {
    return true;
  }
  pruneStaleTouchTracking();
  return activeTouchIdentifiers.size > 0 || activePointerTouchIds.size > 0;
}

function preventBodyScrollTouchMove(event) {
  if (!event) {
    return;
  }
  if (typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
}

function normalizeScrollBehaviorValue(value) {
  if (typeof value !== 'string') {
    return SCROLL_BEHAVIOR_MODES.DEFAULT;
  }
  const normalized = value.trim().toLowerCase();
  if (!normalized) {
    return SCROLL_BEHAVIOR_MODES.DEFAULT;
  }
  return SCROLL_BEHAVIOR_ALIAS_MAP.get(normalized) || SCROLL_BEHAVIOR_MODES.DEFAULT;
}

function resolvePageScrollBehavior(pageElement) {
  if (!pageElement || typeof pageElement !== 'object') {
    return SCROLL_BEHAVIOR_MODES.DEFAULT;
  }
  const dataset = pageElement.dataset || {};
  const rawBehavior = typeof dataset.scrollBehavior === 'string'
    ? dataset.scrollBehavior
    : typeof dataset.scrollMode === 'string'
      ? dataset.scrollMode
      : null;
  if (rawBehavior) {
    return normalizeScrollBehaviorValue(rawBehavior);
  }
  if (typeof pageElement.hasAttribute === 'function' && pageElement.hasAttribute('data-force-scroll')) {
    return SCROLL_BEHAVIOR_MODES.FORCE;
  }
  const pageId = typeof pageElement.id === 'string'
    ? pageElement.id.trim().toLowerCase()
    : '';
  if (pageId === 'game') {
    return SCROLL_BEHAVIOR_MODES.LOCK;
  }
  return SCROLL_BEHAVIOR_MODES.DEFAULT;
}

function applyBodyScrollBehavior(requestedBehavior) {
  if (typeof document === 'undefined') {
    activeBodyScrollBehavior = normalizeScrollBehaviorValue(requestedBehavior);
    return;
  }
  const body = document.body;
  if (!body) {
    activeBodyScrollBehavior = normalizeScrollBehaviorValue(requestedBehavior);
    return;
  }
  const nextBehavior = normalizeScrollBehaviorValue(requestedBehavior);
  activeBodyScrollBehavior = nextBehavior;

  if (nextBehavior === SCROLL_BEHAVIOR_MODES.LOCK) {
    if (!bodyScrollTouchMoveListenerAttached && typeof body.addEventListener === 'function') {
      body.addEventListener('touchmove', preventBodyScrollTouchMove, nonPassiveEventListenerOptions);
      bodyScrollTouchMoveListenerAttached = true;
    }
    body.style.setProperty('touch-action', 'none');
    body.style.setProperty('overscroll-behavior', 'none');
    body.classList.add('touch-scroll-lock');
    body.classList.remove('touch-scroll-force');
    return;
  }

  if (bodyScrollTouchMoveListenerAttached && typeof body.removeEventListener === 'function') {
    body.removeEventListener('touchmove', preventBodyScrollTouchMove, nonPassiveEventListenerOptions);
    bodyScrollTouchMoveListenerAttached = false;
  }

  if (nextBehavior === SCROLL_BEHAVIOR_MODES.FORCE) {
    body.style.setProperty('touch-action', 'auto');
    body.style.setProperty('overscroll-behavior', 'auto');
    body.classList.add('touch-scroll-force');
    body.classList.remove('touch-scroll-lock');
  } else {
    body.style.removeProperty('touch-action');
    body.style.removeProperty('overscroll-behavior');
    body.classList.remove('touch-scroll-lock');
    body.classList.remove('touch-scroll-force');
  }
}

function applyActivePageScrollBehavior(activePageElement) {
  if (typeof document === 'undefined') {
    activeBodyScrollBehavior = normalizeScrollBehaviorValue(activeBodyScrollBehavior);
    return;
  }
  let pageElement = activePageElement;
  if (!pageElement) {
    const activePageId = document.body?.dataset?.activePage;
    if (activePageId) {
      pageElement = document.getElementById(activePageId);
    }
  }
  if (!pageElement && typeof document.querySelector === 'function') {
    pageElement = document.querySelector('.page.active');
  }
  applyBodyScrollBehavior(resolvePageScrollBehavior(pageElement));
}

function handleGlobalTouchStart(event) {
  registerActiveTouches(event?.changedTouches, event?.touches);
}

function handleGlobalTouchMove(event) {
  registerActiveTouches(event?.changedTouches, event?.touches);
}

function handleGlobalTouchCompletion(event) {
  registerActiveTouches(event?.changedTouches, event?.touches);
  unregisterActiveTouches(event?.changedTouches, event?.touches);
  if (event?.type === 'touchcancel' && (!event.changedTouches || event.changedTouches.length === 0)) {
    activeTouchIdentifiers.clear();
    activePointerTouchIds.clear();
  }
  if (hasRemainingActiveTouches(event)) {
    scheduleScrollUnlockCheck();
    return;
  }
  forceUnlockScrollSafe();
}

function isTouchLikePointerEvent(event) {
  if (!event) {
    return false;
  }
  const pointerType = typeof event.pointerType === 'string'
    ? event.pointerType.toLowerCase()
    : '';
  if (pointerType === 'touch') {
    return true;
  }
  if (!pointerType && Number.isFinite(event.pointerId)) {
    return activePointerTouchIds.has(event.pointerId);
  }
  return false;
}

function handleGlobalPointerCompletion(event) {
  if (!isTouchLikePointerEvent(event)) {
    return;
  }
  if (event.type === 'pointerout' && event.relatedTarget) {
    return;
  }
  if (Number.isFinite(event.pointerId)) {
    activePointerTouchIds.delete(event.pointerId);
  } else {
    activePointerTouchIds.clear();
  }
  if (hasRemainingActiveTouches()) {
    scheduleScrollUnlockCheck();
    return;
  }
  forceUnlockScrollSafe();
}

function handleGlobalPointerStart(event) {
  if (!event || event.pointerType !== 'touch') {
    return;
  }
  cancelScheduledScrollUnlockCheck();
  const now = getCurrentTimestamp();
  if (Number.isFinite(event.pointerId)) {
    activePointerTouchIds.set(event.pointerId, now);
  } else {
    activePointerTouchIds.set('pointer', now);
  }
}

function resetTouchTrackingState(options = {}) {
  const { reapplyScrollBehavior = true } = options;
  activeTouchIdentifiers.clear();
  activePointerTouchIds.clear();
  cancelScheduledScrollUnlockCheck();
  consecutiveScrollUnlockFailures = 0;
  if (reapplyScrollBehavior) {
    applyActivePageScrollBehavior();
  }
}

if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      forceUnlockScrollSafe({ reapplyScrollBehavior: false });
      return;
    }
    applyActivePageScrollBehavior();
  });

  document.addEventListener('touchstart', handleGlobalTouchStart, passiveCaptureEventListenerOptions);
  document.addEventListener('touchmove', handleGlobalTouchMove, passiveCaptureEventListenerOptions);
  ['touchend', 'touchcancel'].forEach(eventName => {
    document.addEventListener(eventName, handleGlobalTouchCompletion, passiveCaptureEventListenerOptions);
  });

  if (supportsGlobalPointerEvents) {
    document.addEventListener('pointerdown', handleGlobalPointerStart, passiveCaptureEventListenerOptions);
    ['pointerup', 'pointercancel', 'pointerleave', 'pointerout', 'lostpointercapture'].forEach(eventName => {
      document.addEventListener(eventName, handleGlobalPointerCompletion, passiveCaptureEventListenerOptions);
    });
  }
}

if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('pageshow', () => {
    applyActivePageScrollBehavior();
  }, passiveEventListenerOptions);
  window.addEventListener('focus', () => {
    applyActivePageScrollBehavior();
  }, passiveEventListenerOptions);
  window.addEventListener('atom2univers:scroll-reset', () => {
    forceUnlockScrollSafe();
  });
  window.addEventListener('touchstart', handleGlobalTouchStart, passiveCaptureEventListenerOptions);
  window.addEventListener('touchmove', handleGlobalTouchMove, passiveCaptureEventListenerOptions);
  ['touchend', 'touchcancel'].forEach(eventName => {
    window.addEventListener(eventName, handleGlobalTouchCompletion, passiveCaptureEventListenerOptions);
  });
  if (supportsGlobalPointerEvents) {
    window.addEventListener('pointerdown', handleGlobalPointerStart, passiveCaptureEventListenerOptions);
    ['pointerup', 'pointercancel', 'pointerleave', 'pointerout', 'lostpointercapture'].forEach(eventName => {
      window.addEventListener(eventName, handleGlobalPointerCompletion, passiveCaptureEventListenerOptions);
    });
  }
  window.addEventListener('pointercancel', forceUnlockScrollSafe, passiveEventListenerOptions);
  window.addEventListener('blur', forceUnlockScrollSafe);
  window.addEventListener('pagehide', forceUnlockScrollSafe);
}

function showPage(pageId) {
  if (pageId === 'midi' && !isMusicModuleEnabled()) {
    if (document.body?.dataset?.activePage !== 'options') {
      showPage('options');
    }
    return;
  }
  if (!isPageUnlocked(pageId)) {
    if (pageId !== 'game') {
      showPage('game');
    }
    return;
  }
  forceUnlockScrollSafe({ reapplyScrollBehavior: false });
  const now = performance.now();
  if (pageId === 'wave') {
    ensureWaveGame();
  }
  if (pageId === 'bigger') {
    ensureBiggerGame();
  }
  if (pageId === 'balance') {
    ensureBalanceGame();
  }
  if (pageId === 'quantum2048') {
    ensureQuantum2048Game();
  }
  if (pageId === 'escape') {
    ensureEscapeGame();
  }
  if (pageId === 'starBridges') {
    ensureStarBridgesGame();
  }
  if (pageId === 'starsWar') {
    ensureStarsWarGame();
  }
  if (pageId === 'pipeTap') {
    ensurePipeTapGame();
  }
  if (pageId === 'colorStack') {
    ensureColorStackGame();
  }
  if (pageId === 'hex') {
    ensureHexGame();
  }
  if (pageId === 'sokoban') {
    ensureSokobanGame();
  }
  if (pageId === 'taquin') {
    ensureTaquinGame();
  }
  if (pageId === 'link') {
    ensureLinkGame();
  }
  if (pageId === 'lightsOut') {
    ensureLightsOutGame();
  }
  elements.pages.forEach(page => {
    const isActive = page.id === pageId;
    page.classList.toggle('active', isActive);
    page.toggleAttribute('hidden', !isActive);
  });
  elements.navButtons.forEach(btn => {
    btn.classList.toggle('active', btn.dataset.target === pageId);
  });
  document.body.dataset.activePage = pageId;
  if (pageId === 'info') {
    updateDevKitUI();
  }
  const activePageElement = typeof document !== 'undefined'
    ? document.getElementById(pageId)
    : null;
  const rawPageGroup = activePageElement?.dataset?.pageGroup || 'clicker';
  const normalizedPageGroup = typeof rawPageGroup === 'string'
    ? rawPageGroup.trim().toLowerCase()
    : '';
  const activePageGroup = normalizedPageGroup === 'arcade'
    ? 'arcade'
    : 'clicker';
  document.body.dataset.pageGroup = activePageGroup;
  applyActivePageScrollBehavior(activePageElement);
  document.body.classList.toggle('view-game', pageId === 'game');
  document.body.classList.toggle('view-arcade', pageId === 'arcade');
  document.body.classList.toggle('view-arcade-hub', pageId === 'arcadeHub');
  document.body.classList.toggle('view-metaux', pageId === 'metaux');
  document.body.classList.toggle('view-bigger', pageId === 'bigger');
  document.body.classList.toggle('view-wave', pageId === 'wave');
  document.body.classList.toggle('view-balance', pageId === 'balance');
  document.body.classList.toggle('view-quantum2048', pageId === 'quantum2048');
  document.body.classList.toggle('view-star-bridges', pageId === 'starBridges');
  document.body.classList.toggle('view-stars-war', pageId === 'starsWar');
  document.body.classList.toggle('view-pipe-tap', pageId === 'pipeTap');
  document.body.classList.toggle('view-color-stack', pageId === 'colorStack');
  document.body.classList.toggle('view-motocross', pageId === 'motocross');
  document.body.classList.toggle('view-hex', pageId === 'hex');
  document.body.classList.toggle('view-twins', pageId === 'twins');
  document.body.classList.toggle('view-sokoban', pageId === 'sokoban');
  document.body.classList.toggle('view-taquin', pageId === 'taquin');
  document.body.classList.toggle('view-sudoku', pageId === 'sudoku');
  document.body.classList.toggle('view-lights-out', pageId === 'lightsOut');
  document.body.classList.toggle('view-link', pageId === 'link');
  document.body.classList.toggle('view-game-of-life', pageId === 'gameOfLife');
  if (pageId === 'game') {
    randomizeAtomButtonImage();
  }
  if (pageId === 'metaux') {
    initMetauxGame();
  }
  if (particulesGame) {
    if (pageId === 'arcade') {
      particulesGame.onEnter();
    } else {
      particulesGame.onLeave();
    }
  }
  if (metauxGame) {
    if (pageId === 'metaux') {
      metauxGame.onEnter();
    } else {
      metauxGame.onLeave();
    }
  }
  if (biggerGame) {
    if (pageId === 'bigger') {
      biggerGame.onEnter();
    } else {
      biggerGame.onLeave();
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
  const starBridges = ensureStarBridgesGame();
  if (starBridges) {
    if (pageId === 'starBridges') {
      starBridges.onEnter?.();
    } else {
      starBridges.onLeave?.();
    }
  }
  const starsWar = ensureStarsWarGame();
  if (starsWar) {
    if (pageId === 'starsWar') {
      starsWar.onEnter?.();
    } else {
      starsWar.onLeave?.();
    }
  }
  const pipeTap = ensurePipeTapGame();
  if (pipeTap) {
    if (pageId === 'pipeTap') {
      pipeTap.onEnter?.();
    } else {
      pipeTap.onLeave?.();
    }
  }
  const colorStack = ensureColorStackGame();
  if (colorStack) {
    if (pageId === 'colorStack') {
      colorStack.onEnter?.();
    } else {
      colorStack.onLeave?.();
    }
  }
  const hex = ensureHexGame();
  if (hex) {
    if (pageId === 'hex') {
      hex.onEnter?.();
    } else {
      hex.onLeave?.();
    }
  }
  const motocross = ensureMotocrossGame();
  if (motocross) {
    if (pageId === 'motocross') {
      motocross.onEnter?.();
    } else {
      motocross.onLeave?.();
    }
  }
  const twins = ensureTwinsGame();
  if (twins) {
    if (pageId === 'twins') {
      twins.onEnter?.();
    } else {
      twins.onLeave?.();
    }
  }
  const sokoban = ensureSokobanGame();
  if (sokoban) {
    if (pageId === 'sokoban') {
      sokoban.onEnter?.();
    } else {
      sokoban.onLeave?.();
    }
  }
  const taquin = ensureTaquinGame();
  if (taquin) {
    if (pageId === 'taquin') {
      taquin.onEnter?.();
    } else {
      taquin.onLeave?.();
    }
  }
  const link = ensureLinkGame();
  if (link) {
    if (pageId === 'link') {
      link.onEnter?.();
    } else {
      link.onLeave?.();
    }
  }
  const lightsOut = ensureLightsOutGame();
  if (lightsOut) {
    if (pageId === 'lightsOut') {
      lightsOut.onEnter?.();
    } else {
      lightsOut.onLeave?.();
    }
  }
  const gameOfLife = ensureGameOfLifeGame();
  if (gameOfLife) {
    if (pageId === 'gameOfLife') {
      gameOfLife.onEnter?.();
    } else {
      gameOfLife.onLeave?.();
    }
  }
  const manualPageActive = pageId === 'game' || pageId === 'wave';
  if (manualPageActive && (typeof document === 'undefined' || !document.hidden)) {
    gamePageVisibleSince = now;
  } else {
    gamePageVisibleSince = null;
  }
  if (pageId === 'gacha') {
    const weightsUpdated = refreshGachaRarities(new Date());
    if (weightsUpdated) {
      rebuildGachaPools();
      renderGachaRarityList();
    }
  }
  scheduleAutoUiScaleUpdate();
  updateFrenzyIndicators(now);
}

document.addEventListener('visibilitychange', () => {
  if (typeof document === 'undefined') {
    return;
  }
  const activePage = document.body?.dataset.activePage;
  if (document.hidden) {
    forceUnlockScrollSafe({ reapplyScrollBehavior: false });
    hiddenSinceTimestamp = Date.now();
    gamePageVisibleSince = null;
    if (particulesGame && activePage === 'arcade') {
      particulesGame.onLeave();
    }
    if (waveGame && activePage === 'wave') {
      waveGame.onLeave();
    }
    if (quantum2048Game && activePage === 'quantum2048') {
      quantum2048Game.onLeave();
    }
    const sokoban = ensureSokobanGame();
    if (sokoban && activePage === 'sokoban') {
      sokoban.onLeave?.();
    }
    const taquin = ensureTaquinGame();
    if (taquin && activePage === 'taquin') {
      taquin.onLeave?.();
    }
    const lightsOut = ensureLightsOutGame();
    if (lightsOut && activePage === 'lightsOut') {
      lightsOut.onLeave?.();
    }
    const starBridgesGameInstance = ensureStarBridgesGame();
    if (starBridgesGameInstance && activePage === 'starBridges') {
      starBridgesGameInstance.onLeave?.();
    }
    const starsWarInstance = ensureStarsWarGame();
    if (starsWarInstance && activePage === 'starsWar') {
      starsWarInstance.onLeave?.();
    }
    const pipeTap = ensurePipeTapGame();
    if (pipeTap && activePage === 'pipeTap') {
      pipeTap.onLeave?.();
    }
    const colorStack = ensureColorStackGame();
    if (colorStack && activePage === 'colorStack') {
      colorStack.onLeave?.();
    }
    const hex = ensureHexGame();
    if (hex && activePage === 'hex') {
      hex.onLeave?.();
    }
    const motocross = ensureMotocrossGame();
    if (motocross && activePage === 'motocross') {
      motocross.onLeave?.();
    }
    const twins = ensureTwinsGame();
    if (twins && activePage === 'twins') {
      twins.onLeave?.();
    }
    saveGame();
    return;
  }

  const becameVisibleAt = typeof performance !== 'undefined' ? performance.now() : null;
  if (becameVisibleAt != null) {
    lastUpdate = becameVisibleAt;
    lastUIUpdate = becameVisibleAt;
    lastSaveTime = becameVisibleAt;
  }

  const nowTimestamp = Date.now();
  const hiddenDurationSeconds = hiddenSinceTimestamp != null
    ? Math.max(0, (nowTimestamp - hiddenSinceTimestamp) / 1000)
    : 0;
  hiddenSinceTimestamp = null;
  if (hiddenDurationSeconds > 0) {
    const offlineResult = applyOfflineProgress(hiddenDurationSeconds);
    if (offlineResult.appliedSeconds > 0) {
      updateUI();
      saveGame();
    }
  }

  if (isManualClickContextActive()) {
    gamePageVisibleSince = performance.now();
    if (particulesGame && activePage === 'arcade') {
      particulesGame.onEnter();
    }
    if (activePage === 'wave') {
      ensureWaveGame();
      waveGame?.onEnter();
    }
  } else if (particulesGame && activePage === 'arcade') {
    particulesGame.onEnter();
  } else if (activePage === 'wave') {
    ensureWaveGame();
    waveGame?.onEnter();
  }

  if (activePage === 'quantum2048') {
    ensureQuantum2048Game();
    quantum2048Game?.onEnter();
  }
  if (activePage === 'starBridges') {
    const starBridgesGameInstance = ensureStarBridgesGame();
    starBridgesGameInstance?.onEnter?.();
  }
  if (activePage === 'starsWar') {
    const starsWarInstance = ensureStarsWarGame();
    starsWarInstance?.onEnter?.();
  }
  if (activePage === 'pipeTap') {
    const pipeTap = ensurePipeTapGame();
    pipeTap?.onEnter?.();
  }
  if (activePage === 'colorStack') {
    const colorStack = ensureColorStackGame();
    colorStack?.onEnter?.();
  }
  if (activePage === 'hex') {
    const hex = ensureHexGame();
    hex?.onEnter?.();
  }
  if (activePage === 'motocross') {
    const motocross = ensureMotocrossGame();
    motocross?.onEnter?.();
  }
  if (activePage === 'twins') {
    const twins = ensureTwinsGame();
    twins?.onEnter?.();
  }
  if (activePage === 'taquin') {
    const taquin = ensureTaquinGame();
    taquin?.onEnter?.();
  }
});

function bindDomEventListeners() {
  const initiallyActivePage = document.querySelector('.page.active') || elements.pages[0];
  if (initiallyActivePage) {
    showPage(initiallyActivePage.id);
  } else {
    document.body.classList.remove('view-game');
  }

  initInfoWelcomeCard();
  initInfoAchievementsCard();
  initInfoCalculationsCard();
  initInfoProgressCard();
  initInfoScoresCard();
  initInfoCharactersCard();
  initInfoCardsCard();
  initCollectionImagesCard();
  initCollectionVideosCard();
  initCollectionBonusImagesCard();
  initCollectionBonus2ImagesCard();
  if (typeof initSpecialCardOverlay === 'function') {
    initSpecialCardOverlay();
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

  if (elements.holdemRestartButton) {
    elements.holdemRestartButton.addEventListener('click', event => {
      event.preventDefault();
      handleHoldemRestartRequest();
    });
  }

  if (elements.holdemBlindMultiplyButton) {
    elements.holdemBlindMultiplyButton.addEventListener('click', event => {
      event.preventDefault();
      handleHoldemBlindScaling(10);
    });
  }

  if (elements.holdemBlindDivideButton) {
    elements.holdemBlindDivideButton.addEventListener('click', event => {
      event.preventDefault();
      handleHoldemBlindScaling(0.1);
    });
  }

  document.addEventListener('keydown', event => {
    if (isDevkitFeatureEnabled() && event.key === 'F9') {
      event.preventDefault();
      toggleDevKit();
    } else if (event.key === 'Escape' && DEVKIT_STATE.isOpen) {
      event.preventDefault();
      closeDevKit();
    }
  });

  function handleMetauxSessionEnd(summary) {
    updateMetauxCreditsUI();
    if (!summary || typeof summary !== 'object') {
      return;
    }
    const elapsedMs = Number(summary.elapsedMs ?? summary.time ?? 0);
    const matches = Number(summary.matches ?? summary.matchCount ?? 0);
    const secondsEarned = Number.isFinite(elapsedMs) && elapsedMs > 0
      ? Math.max(0, Math.round(elapsedMs / 1000))
      : 0;
    const matchesEarned = Number.isFinite(matches) && matches > 0
      ? Math.max(0, Math.floor(matches))
      : 0;
    if (secondsEarned <= 0 && matchesEarned <= 0) {
      return;
    }
    const apsCrit = ensureApsCritState();
    const hadEffects = apsCrit.effects.length > 0;
    const previousMultiplier = getApsCritMultiplier(apsCrit);
    const currentRemaining = getApsCritRemainingSeconds(apsCrit);
    let chronoAdded = 0;
    let effectAdded = false;
    if (!hadEffects) {
      if (secondsEarned > 0 && matchesEarned > 0) {
        apsCrit.effects.push({
          multiplierAdd: matchesEarned,
          remainingSeconds: secondsEarned
        });
        chronoAdded = secondsEarned;
        effectAdded = true;
      }
    } else if (matchesEarned > 0 && currentRemaining > 0) {
      apsCrit.effects.push({
        multiplierAdd: matchesEarned,
        remainingSeconds: currentRemaining
      });
      effectAdded = true;
    }
    if (!effectAdded) {
      return;
    }
    apsCrit.effects = apsCrit.effects.filter(effect => {
      const remaining = Number(effect?.remainingSeconds) || 0;
      const value = Number(effect?.multiplierAdd) || 0;
      return remaining > 0 && value > 0;
    });
    if (!apsCrit.effects.length) {
      return;
    }
    const newMultiplier = getApsCritMultiplier(apsCrit);
    if (newMultiplier !== previousMultiplier) {
      recalcProduction();
    }
    updateUI();
    pulseApsCritPanel();
    const messageParts = [];
    if (chronoAdded > 0) {
      messageParts.push(t('scripts.app.metaux.chronoBonus', {
        value: formatIntegerLocalized(chronoAdded)
      }));
    }
    if (matchesEarned > 0) {
      messageParts.push(t('scripts.app.metaux.multiBonus', {
        value: formatIntegerLocalized(matchesEarned)
      }));
    }
    if (messageParts.length) {
      showToast(t('scripts.app.metaux.toast', { details: messageParts.join(' · ') }));
    }
    saveGame();
  }

  window.handleMetauxSessionEnd = handleMetauxSessionEnd;

  elements.navButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.dataset.target;
      if (!isPageUnlocked(target)) {
        return;
      }
      showPage(target);
    });
  });

  if (elements.arcadeHubCards?.length) {
    elements.arcadeHubCards.forEach(card => {
      initializeArcadeHubCard(card);
      card.addEventListener('click', event => {
        if (
          card.dataset.arcadeDragSuppressClick === 'true'
          || card.dataset.arcadeDragging === 'true'
          || card.dataset.arcadeDragPending === 'true'
        ) {
          if (event) {
            event.preventDefault();
            event.stopPropagation();
          }
          delete card.dataset.arcadeDragSuppressClick;
          return;
        }
        if (event && event.target && event.target.closest('.arcade-hub-card__toggle')) {
          return;
        }
        if (event?.defaultPrevented) {
          return;
        }
        activateArcadeHubCard(card);
      });
      card.addEventListener('keydown', event => {
        if (!event) {
          return;
        }
        if (event.target && event.target.closest('.arcade-hub-card__toggle')) {
          return;
        }
        const key = event.key;
        if (key === 'Enter' || key === ' ') {
          event.preventDefault();
          activateArcadeHubCard(card);
        }
      });
    });
  }

  if (elements.openMidiModuleButton) {
    elements.openMidiModuleButton.addEventListener('click', () => {
      if (!isMusicModuleEnabled()) {
        showToast(t('scripts.app.music.disabled'));
        return;
      }
      showPage('midi');
    });
  }

  if (elements.metauxOpenButton) {
    elements.metauxOpenButton.addEventListener('click', () => {
      showPage('metaux');
    });
  }

  if (elements.metauxReturnButton) {
    elements.metauxReturnButton.addEventListener('click', () => {
      showPage('game');
    });
  }

  if (elements.metauxNewGameButton) {
    elements.metauxNewGameButton.addEventListener('click', () => {
      initMetauxGame();
      const credits = getMetauxCreditCount();
      if (!metauxGame) {
        showToast(t('scripts.app.metaux.unavailable'));
        return;
      }
      if (isMetauxSessionRunning()) {
        showToast(t('scripts.app.metaux.gameInProgress'));
        updateMetauxCreditsUI();
        return;
      }
      if (credits <= 0) {
        showToast(t('scripts.app.metaux.noCredits'));
        updateMetauxCreditsUI();
        return;
      }
      gameState.bonusParticulesTickets = credits - 1;
      metauxGame.restart();
      updateMetauxCreditsUI();
      saveGame();
    });
  }

  if (elements.metauxFreePlayButton) {
    elements.metauxFreePlayButton.addEventListener('click', () => {
      initMetauxGame();
      if (!metauxGame) {
        showToast(t('scripts.app.metaux.unavailable'));
        return;
      }
      if (isMetauxSessionRunning()) {
        showToast(t('scripts.app.metaux.gameInProgress'));
        updateMetauxCreditsUI();
        return;
      }
      if (typeof metauxGame.startFreePlay === 'function') {
        metauxGame.startFreePlay();
      } else {
        metauxGame.restart({ freePlay: true });
      }
      updateMetauxCreditsUI();
    });
  }

  if (elements.metauxFreePlayExitButton) {
    elements.metauxFreePlayExitButton.addEventListener('click', () => {
      initMetauxGame();
      if (!metauxGame) {
        showToast(t('scripts.app.metaux.unavailable'));
        return;
      }
      if (typeof metauxGame.isFreePlayMode === 'function' && !metauxGame.isFreePlayMode()) {
        return;
      }
      if (metauxGame.processing) {
        showToast('Patientez, la réaction en chaîne est en cours.');
        return;
      }
      if (typeof metauxGame.endFreePlaySession === 'function') {
        const ended = metauxGame.endFreePlaySession({ showEndScreen: true });
        if (ended && typeof window.updateMetauxCreditsUI === 'function') {
          window.updateMetauxCreditsUI();
        }
      }
    });
  }

  if (elements.brandHomeButton) {
    elements.brandHomeButton.addEventListener('click', () => {
      showPage('game');
    });
  }

  if (elements.brandPortal) {
    elements.brandPortal.addEventListener('click', () => {
      if (!isArcadeUnlocked()) {
        return;
      }
      showPage('arcadeHub');
    });
  }

  if (elements.statusAtomsButton) {
    elements.statusAtomsButton.addEventListener('click', () => {
      if (document?.body?.dataset?.activePage === 'game') {
        return;
      }
      showPage('game');
    });
  }

  if (elements.arcadeReturnButton) {
    elements.arcadeReturnButton.addEventListener('click', () => {
      showPage('game');
      if (isArcadeUnlocked()) {
        triggerBrandPortalPulse();
      }
    });
  }

  if (elements.arcadeTicketButtons?.length) {
    elements.arcadeTicketButtons.forEach(button => {
      button.addEventListener('click', () => {
        if (!isPageUnlocked('gacha')) {
          return;
        }
        showPage('gacha');
      });
    });
  }

  if (elements.arcadeBonusTicketButtons?.length) {
    elements.arcadeBonusTicketButtons.forEach(button => {
      button.addEventListener('click', () => {
        if (button.disabled) {
          return;
        }
        showPage('metaux');
      });
    });
  }

  if (elements.elementInfoSymbol) {
    elements.elementInfoSymbol.addEventListener('click', event => {
      if (elements.elementInfoSymbol.disabled || !selectedElementId) {
        return;
      }
      event.preventDefault();
      openElementDetailsModal(selectedElementId, { trigger: elements.elementInfoSymbol });
    });
  }

  if (elements.elementInfoCategoryButton) {
    elements.elementInfoCategoryButton.addEventListener('click', event => {
      if (elements.elementInfoCategoryButton.disabled) {
        return;
      }
      const familyId = elements.elementInfoCategoryButton.dataset.familyId || null;
      if (!familyId) {
        return;
      }
      event.preventDefault();
      openElementFamilyModal(familyId, { trigger: elements.elementInfoCategoryButton });
    });
  }

  if (elements.elementDetailsOverlay) {
    elements.elementDetailsOverlay.addEventListener('click', event => {
      const target = event.target.closest('[data-element-details-close]');
      if (target) {
        event.preventDefault();
        closeElementDetailsModal();
      }
    });
  }

  if (elements.elementFamilyOverlay) {
    elements.elementFamilyOverlay.addEventListener('click', event => {
      const target = event.target.closest('[data-element-family-close]');
      if (target) {
        event.preventDefault();
        closeElementFamilyModal();
      }
    });
  }
  if (elements.atomButton) {
    const atomButton = elements.atomButton;

    atomButton.addEventListener('pointerdown', event => {
      activateEcoClickFeedback();
      if (!isTouchLikePointerEvent(event)) {
        return;
      }
      if (typeof event.preventDefault === 'function') {
        event.preventDefault();
      }
      if (typeof event.stopPropagation === 'function') {
        event.stopPropagation();
      }
      markTouchPointerManualClick();
      handleManualAtomClick({ contextId: 'game' });
      const currentTarget = event.currentTarget;
      if (currentTarget && typeof currentTarget.setPointerCapture === 'function') {
        try {
          currentTarget.setPointerCapture(event.pointerId);
        } catch (error) {
          // Ignore errors from pointer capture (unsupported or already captured)
        }
      }
    });

    const handleEcoPointerRelease = () => {
      if (!isEcoPerformanceModeActive()) {
        return;
      }
      triggerEcoClickFeedbackPulse();
    };

    const handlePointerCompletion = event => {
      handleEcoPointerRelease();
      if (!isTouchLikePointerEvent(event)) {
        return;
      }
      const currentTarget = event.currentTarget;
      if (!currentTarget || typeof currentTarget.releasePointerCapture !== 'function') {
        return;
      }
      try {
        currentTarget.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore pointer capture release errors
      }
    };

    atomButton.addEventListener('pointerup', handlePointerCompletion);
    atomButton.addEventListener('pointerleave', handleEcoPointerRelease);
    atomButton.addEventListener('pointercancel', handlePointerCompletion);
    atomButton.addEventListener('lostpointercapture', handlePointerCompletion);

    atomButton.addEventListener('click', event => {
      if (shouldSuppressPointerDerivedClick()) {
        if (typeof event.preventDefault === 'function') {
          event.preventDefault();
        }
        if (typeof event.stopPropagation === 'function') {
          event.stopPropagation();
        }
        return;
      }
      if (typeof event?.preventDefault === 'function') {
        event.preventDefault();
      }
      if (typeof event?.stopPropagation === 'function') {
        event.stopPropagation();
      }
      handleManualAtomClick({ contextId: 'game' });
    });

    atomButton.addEventListener('dragstart', event => {
      event.preventDefault();
    });
  }

  if (elements.gachaSunButton) {
    elements.gachaSunButton.addEventListener('click', event => {
      event.preventDefault();
      handleGachaSunClick().catch(error => {
        console.error('Erreur lors du tirage cosmique', error);
      });
    });
  }

  if (elements.gachaTicketModeButton) {
    elements.gachaTicketModeButton.addEventListener('click', event => {
      event.preventDefault();
      if (gachaAnimationInProgress) return;
      toggleGachaRollMode();
    });
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
          updateResetDialogCopy();
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
      renderPeriodicTable();
      updateUI();
      updateResetDialogCopy();
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
      if (!isMusicModuleEnabled()) {
        if (typeof event.preventDefault === 'function') {
          event.preventDefault();
        }
        refreshMusicControls();
        return;
      }
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
      if (!isMusicModuleEnabled()) {
        event.preventDefault?.();
        refreshMusicControls();
        return;
      }
      applyVolumeFromSlider(event.target.value);
    });
    elements.musicVolumeSlider.addEventListener('change', event => {
      if (!isMusicModuleEnabled()) {
        event.preventDefault?.();
        refreshMusicControls();
        return;
      }
      applyVolumeFromSlider(event.target.value, { announce: true });
    });
  }

  if (elements.openDevkitButton) {
    if (!isDevkitFeatureEnabled()) {
      if (typeof elements.openDevkitButton.remove === 'function') {
        elements.openDevkitButton.remove();
      } else {
        elements.openDevkitButton.style.display = 'none';
      }
      elements.openDevkitButton = null;
    } else {
      elements.openDevkitButton.addEventListener('click', event => {
        event.preventDefault();
        toggleDevKit();
      });
    }
  }

  if (elements.resetButton) {
    if (elements.resetDialog && elements.resetDialogForm && elements.resetDialogInput) {
      elements.resetButton.addEventListener('click', event => {
        event.preventDefault();
        openResetDialog();
      });
      elements.resetDialogForm.addEventListener('submit', handleResetDialogSubmit);
      if (elements.resetDialogCancel) {
        elements.resetDialogCancel.addEventListener('click', handleResetDialogCancel);
      }
      if (elements.resetDialog) {
        elements.resetDialog.addEventListener('click', handleResetDialogBackdrop);
      }
      updateResetDialogCopy();
      setResetDialogError();
    } else {
      elements.resetButton.addEventListener('click', event => {
        event.preventDefault();
        handleResetPromptFallback();
      });
    }
  }

  if (elements.bigBangOptionToggle) {
    elements.bigBangOptionToggle.addEventListener('change', event => {
      const enabled = event.target.checked;
      if (!isBigBangUnlocked()) {
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

  if (elements.bigBangRestartButton) {
    elements.bigBangRestartButton.addEventListener('click', event => {
      event.preventDefault();
      handleBigBangRestart();
    });
  }

  if (elements.bigBangDialogConfirm) {
    elements.bigBangDialogConfirm.addEventListener('click', handleBigBangDialogConfirm);
  }
  if (elements.bigBangDialogCancel) {
    elements.bigBangDialogCancel.addEventListener('click', handleBigBangDialogCancel);
  }
  if (elements.bigBangDialog) {
    elements.bigBangDialog.addEventListener('click', handleBigBangDialogBackdrop);
  }



}

document.addEventListener('click', event => {
  if (shouldSuppressPointerDerivedClick()) return;
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
  invalidateFeatureUnlockCache();
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
  if (lastArcadeUnlockState !== true && isFeatureUnlocked('arcade.hub')) {
    updateBrandPortalState({ animate: true });
  }
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
  let baseLevel;
  if (Number.isFinite(numeric) && numeric > 0) {
    baseLevel = Math.max(1, Math.floor(numeric));
  } else {
    baseLevel = DEFAULT_UPGRADE_MAX_LEVEL;
  }
  if (!Number.isFinite(baseLevel)) {
    return baseLevel;
  }
  const bonus = getBigBangLevelBonus();
  const bonusMultiplierRaw = Number(definition?.bigBangLevelBonusMultiplier ?? 1);
  // Permet d'augmenter (ou réduire) l'impact du Big Bang sur la limite de niveaux d'une amélioration.
  const bonusMultiplier = Number.isFinite(bonusMultiplierRaw) && bonusMultiplierRaw > 0
    ? bonusMultiplierRaw
    : 1;
  return baseLevel + bonus * bonusMultiplier;
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

function getUpgradeCostBigBangMultiplier(definition) {
  const multiplierRaw = Number(definition?.bigBangBaseCostMultiplier ?? 1);
  if (!Number.isFinite(multiplierRaw) || multiplierRaw <= 0 || Math.abs(multiplierRaw - 1) < 1e-9) {
    return 1;
  }
  const completions = getBigBangCompletionCount();
  if (!Number.isFinite(completions) || completions <= 0) {
    return 1;
  }
  const factor = Math.pow(multiplierRaw, completions);
  return Number.isFinite(factor) && factor > 0 ? factor : 1;
}

function computeUpgradeCost(def, quantity = 1) {
  if (isDevKitShopFree()) {
    return LayeredNumber.zero();
  }
  const level = getUpgradeLevel(gameState.upgrades, def.id);
  const baseScale = def.costScale ?? 1;
  const modifier = computeGlobalCostModifier();
  const baseCostRaw = Number(def.baseCost ?? 0);
  const buyAmount = Math.max(1, Math.floor(Number(quantity) || 0));

  const linearIncrementRaw = Number(def.costIncrement ?? 0);
  const bigBangMultiplier = getUpgradeCostBigBangMultiplier(def);
  const baseCost = baseCostRaw * bigBangMultiplier;
  const linearIncrement = linearIncrementRaw * bigBangMultiplier;
  const hasLinearIncrement = Number.isFinite(linearIncrement) && Math.abs(linearIncrement) > 1e-9;
  if (hasLinearIncrement) {
    const normalizedLevel = Math.max(0, Math.floor(Number(level) || 0));
    const startCost = baseCost + linearIncrement * normalizedLevel;
    const totalCost = buyAmount * (2 * startCost + linearIncrement * (buyAmount - 1)) / 2;
    const adjusted = totalCost * modifier;
    if (!Number.isFinite(adjusted) || adjusted <= 0) {
      return LayeredNumber.zero();
    }
    return new LayeredNumber(adjusted);
  }

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

function computeUpgradeTotalSpent(definition, level) {
  if (!definition) {
    return LayeredNumber.zero();
  }
  const normalizedLevel = Number.isFinite(level) ? Math.max(0, Math.floor(level)) : 0;
  if (normalizedLevel <= 0) {
    return LayeredNumber.zero();
  }
  const baseCost = Number(definition.baseCost ?? 0);
  if (!Number.isFinite(baseCost) || baseCost <= 0) {
    return LayeredNumber.zero();
  }
  const linearIncrement = Number(definition.costIncrement ?? 0);
  const hasLinearIncrement = Number.isFinite(linearIncrement) && Math.abs(linearIncrement) > 1e-9;
  let total;
  if (hasLinearIncrement) {
    const sum = normalizedLevel * (2 * baseCost + linearIncrement * (normalizedLevel - 1)) / 2;
    total = new LayeredNumber(sum);
  } else {
    const baseLayered = new LayeredNumber(baseCost);
    const scaleValue = Number(definition.costScale ?? 1);
    if (!Number.isFinite(scaleValue) || scaleValue <= 0 || Math.abs(scaleValue - 1) <= 1e-9) {
      total = baseLayered.multiplyNumber(normalizedLevel);
    } else {
      const scaleLayered = new LayeredNumber(scaleValue);
      const numerator = scaleLayered.pow(normalizedLevel).subtract(LayeredNumber.one());
      const denominator = scaleLayered.subtract(LayeredNumber.one());
      if (denominator.isZero() || denominator.sign === 0) {
        total = baseLayered.multiplyNumber(normalizedLevel);
      } else {
        total = baseLayered.multiply(numerator.divide(denominator));
      }
    }
  }
  const modifier = Number(computeGlobalCostModifier());
  if (!Number.isFinite(modifier) || modifier === 1) {
    return total;
  }
  return total.multiplyNumber(modifier);
}

function computeShopGachaTicketCost(definition, purchaseAmount) {
  if (!definition || typeof definition !== 'object') {
    return 0;
  }
  const perPurchaseRaw = Number(definition.gachaTicketCostPerPurchase ?? 0);
  if (!Number.isFinite(perPurchaseRaw) || perPurchaseRaw <= 0) {
    return 0;
  }
  const perPurchase = Math.max(0, Math.floor(perPurchaseRaw));
  if (perPurchase <= 0) {
    return 0;
  }
  const amount = Math.max(0, Math.floor(Number(purchaseAmount) || 0));
  if (amount <= 0) {
    return 0;
  }
  const total = perPurchase * amount;
  return Number.isFinite(total) && total > 0 ? total : 0;
}

function grantShopGachaTickets(definition, purchaseAmount) {
  if (!definition || typeof definition !== 'object') {
    return 0;
  }
  const perPurchase = Number(definition.gachaTicketsPerPurchase ?? 0);
  if (!Number.isFinite(perPurchase) || perPurchase <= 0) {
    return 0;
  }
  const normalizedAmount = Math.max(0, Math.floor(Number(purchaseAmount) || 0));
  if (normalizedAmount <= 0) {
    return 0;
  }
  const requested = Math.max(0, Math.floor(perPurchase * normalizedAmount));
  if (!Number.isFinite(requested) || requested <= 0) {
    return 0;
  }

  let awardFn = null;
  if (typeof gainGachaTickets === 'function') {
    awardFn = gainGachaTickets;
  } else if (typeof globalThis !== 'undefined' && typeof globalThis.gainGachaTickets === 'function') {
    awardFn = globalThis.gainGachaTickets;
  }

  if (awardFn) {
    try {
      const granted = awardFn(requested, { unlockTicketStar: true });
      const grantedNumeric = Math.floor(Number(granted));
      if (Number.isFinite(grantedNumeric) && grantedNumeric > 0) {
        return grantedNumeric;
      }
      return requested;
    } catch (error) {
      console.warn('Shop: unable to grant gacha tickets via award function', error);
    }
  }

  const fallbackGain = requested;
  const currentTickets = Number.isFinite(Number(gameState.gachaTickets))
    ? Math.max(0, Math.floor(Number(gameState.gachaTickets)))
    : 0;
  gameState.gachaTickets = currentTickets + fallbackGain;
  if (fallbackGain > 0 && gameState.ticketStarUnlocked !== true) {
    gameState.ticketStarUnlocked = true;
    if (typeof resetTicketStarState === 'function') {
      try {
        resetTicketStarState({ reschedule: true });
      } catch (error) {
        console.warn('Shop: unable to reset ticket star state', error);
      }
    }
  }
  if (typeof evaluatePageUnlocks === 'function') {
    try {
      evaluatePageUnlocks();
    } catch (error) {
      console.warn('Shop: unable to refresh page unlocks after granting tickets', error);
    }
  }
  if (typeof updateGachaUI === 'function') {
    try {
      updateGachaUI();
    } catch (error) {
      console.warn('Shop: unable to refresh gacha UI after granting tickets', error);
    }
  }
  return fallbackGain;
}

function grantShopMach3Tickets(definition, purchaseAmount) {
  if (!definition || typeof definition !== 'object') {
    return 0;
  }
  const perPurchase = Number(definition.mach3TicketsPerPurchase ?? 0);
  if (!Number.isFinite(perPurchase) || perPurchase <= 0) {
    return 0;
  }
  const normalizedAmount = Math.max(0, Math.floor(Number(purchaseAmount) || 0));
  if (normalizedAmount <= 0) {
    return 0;
  }
  const requested = Math.max(0, Math.floor(perPurchase * normalizedAmount));
  if (!Number.isFinite(requested) || requested <= 0) {
    return 0;
  }

  if (typeof gainBonusParticulesTickets === 'function') {
    try {
      const granted = gainBonusParticulesTickets(requested);
      const grantedNumeric = Math.floor(Number(granted));
      if (Number.isFinite(grantedNumeric) && grantedNumeric > 0) {
        return grantedNumeric;
      }
      return requested;
    } catch (error) {
      console.warn('Shop: unable to grant Mach3 tickets via award function', error);
    }
  }

  const fallbackGain = requested;
  const currentTickets = Number.isFinite(Number(gameState.bonusParticulesTickets))
    ? Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets)))
    : 0;
  gameState.bonusParticulesTickets = currentTickets + fallbackGain;
  if (typeof updateArcadeTicketDisplay === 'function') {
    try {
      updateArcadeTicketDisplay();
    } catch (error) {
      console.warn('Shop: unable to refresh Mach3 UI after granting tickets', error);
    }
  }
  return fallbackGain;
}

function formatShopCost({ atomCost, gachaTicketCost }) {
  const value = atomCost instanceof LayeredNumber
    ? atomCost
    : new LayeredNumber(atomCost ?? 0);
  let atomDisplay = '';

  if (value instanceof LayeredNumber && !value.isZero()) {
    if (value.layer === 0) {
      const numeric = value.toNumber();
      if (Number.isFinite(numeric) && Math.abs(numeric) < 1_000_000) {
        const rounded = Math.round(numeric);
        atomDisplay = formatIntegerLocalized(rounded);
      }
    }

    if (!atomDisplay) {
      atomDisplay = value.toString();
    }
  }

  const parts = [];
  if (atomDisplay) {
    parts.push(atomDisplay);
  }

  const ticketCostNumeric = Math.max(0, Math.floor(Number(gachaTicketCost) || 0));
  if (ticketCostNumeric > 0) {
    const unitKey = ticketCostNumeric === 1
      ? 'scripts.app.shop.ticketUnit.single'
      : 'scripts.app.shop.ticketUnit.plural';
    const fallbackUnit = ticketCostNumeric === 1 ? 'gacha ticket' : 'gacha tickets';
    const unit = translateOrDefault(unitKey, fallbackUnit);
    const countLabel = formatIntegerLocalized(ticketCostNumeric);
    const ticketLabel = translateOrDefault(
      'scripts.app.shop.gachaTicketCost',
      `${countLabel} ${unit}`,
      { count: countLabel, unit }
    );
    parts.push(ticketLabel);
  }

  if (parts.length === 0) {
    parts.push(atomDisplay || '0');
  }

  const label = parts.join(' + ');
  return translateOrDefault('scripts.app.shop.costLabel', label, {
    value: label
  });
}

function formatShopCombinedCost(parts) {
  if (!Array.isArray(parts)) {
    return '';
  }
  const filtered = parts
    .map(part => (typeof part === 'string' ? part.trim() : ''))
    .filter(part => part);
  if (!filtered.length) {
    return '';
  }
  if (filtered.length === 1) {
    return filtered[0];
  }
  const [first, ...rest] = filtered;
  const second = rest.join(' + ');
  const fallback = `${first} + ${second}`;
  return translateOrDefault('scripts.app.shop.combinedCost', fallback, { first, second });
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
  const frenzyChanceAdd = { perClick: 0, perSecond: 0 };
  const critAccumulator = createCritAccumulator();

  let clickShopMultiplier = LayeredNumber.one();
  let autoShopMultiplier = LayeredNumber.one();

  const clickRarityMultipliers = clickDetails.sources.multipliers.rarityMultipliers;
  const autoRarityMultipliers = autoDetails.sources.multipliers.rarityMultipliers;

  const elementCountsByRarity = new Map();
  const elementCountsByFamily = new Map();
  const elementGroupSummaries = new Map();
  const familySummaries = new Map();
  const elementEffectSummaries = new Map();
  const pendingElementFlatEffects = [];
  const ensureElementEffectSummary = (elementId, { label, rarityId, lifetimeCount, activeCount }) => {
    if (!elementEffectSummaries.has(elementId)) {
      elementEffectSummaries.set(elementId, {
        type: 'element',
        elementId,
        label,
        rarityId: rarityId || null,
        copies: Math.max(0, Number(lifetimeCount) || 0),
        uniques: lifetimeCount > 0 ? 1 : 0,
        duplicates: Math.max(0, (Number(lifetimeCount) || 0) - (lifetimeCount > 0 ? 1 : 0)),
        totalUnique: 1,
        activeCopies: Math.max(0, Number(activeCount) || 0),
        isComplete: lifetimeCount > 0,
        clickFlatTotal: 0,
        autoFlatTotal: 0,
        critChanceAdd: 0,
        critMultiplierAdd: 0,
        activeLabels: [],
        _labelDetails: new Map()
      });
    }
    const summary = elementEffectSummaries.get(elementId);
    if (label && typeof label === 'string') {
      summary.label = label;
    }
    if (rarityId && typeof rarityId === 'string') {
      summary.rarityId = rarityId;
    }
    const normalizedLifetime = Math.max(0, Number(lifetimeCount) || 0);
    summary.copies = normalizedLifetime;
    summary.uniques = normalizedLifetime > 0 ? 1 : 0;
    summary.duplicates = Math.max(0, normalizedLifetime - summary.uniques);
    summary.totalUnique = 1;
    summary.activeCopies = Math.max(0, Number(activeCount) || 0);
    summary.isComplete = normalizedLifetime > 0;
    return summary;
  };
  const ensureElementEffectLabelEntry = (summary, labelText) => {
    const effectiveLabel = typeof labelText === 'string' && labelText.trim()
      ? labelText.trim()
      : summary.label;
    if (!summary._labelDetails.has(effectiveLabel)) {
      const entry = { label: effectiveLabel, effects: [], notes: [] };
      summary._labelDetails.set(effectiveLabel, entry);
      summary.activeLabels.push(entry);
    }
    return summary._labelDetails.get(effectiveLabel);
  };
  const appendElementLabelEffect = (labelEntry, text) => {
    if (!text) return;
    const trimmed = String(text).trim();
    if (!trimmed) return;
    if (!Array.isArray(labelEntry.effects)) {
      labelEntry.effects = [];
    }
    if (!labelEntry.effects.includes(trimmed)) {
      labelEntry.effects.push(trimmed);
    }
  };
  const appendElementLabelNote = (labelEntry, text) => {
    if (!text) return;
    const trimmed = String(text).trim();
    if (!trimmed) return;
    if (!Array.isArray(labelEntry.notes)) {
      labelEntry.notes = [];
    }
    if (!labelEntry.notes.includes(trimmed)) {
      labelEntry.notes.push(trimmed);
    }
  };
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

    if (Array.isArray(entry.effects) && entry.effects.length) {
      const displayInfo = definition ? getPeriodicElementDisplay(definition) : null;
      const elementLabel = (displayInfo?.name && displayInfo.name.trim())
        ? displayInfo.name.trim()
        : (typeof definition?.name === 'string' && definition.name.trim()
          ? definition.name.trim()
          : (typeof definition?.symbol === 'string' && definition.symbol.trim()
            ? definition.symbol.trim()
            : entry.id));
      const elementSummary = ensureElementEffectSummary(entry.id, {
        label: elementLabel,
        rarityId,
        lifetimeCount: normalizedCount,
        activeCount
      });
      entry.effects.forEach((effect, index) => {
        if (!effect || typeof effect !== 'object') {
          return;
        }
        const stats = effect.effects && typeof effect.effects === 'object' ? effect.effects : {};
        if (Object.keys(stats).length === 0) {
          return;
        }
        const requiredLifetime = Math.max(
          0,
          Number(effect.minLifetime ?? (effect.trigger === 'firstAcquisition' ? 1 : (effect.trigger === 'always' ? 0 : 1))) || 0
        );
        const requiredActive = Math.max(
          0,
          Number(effect.minActive ?? (effect.trigger === 'active' ? 1 : 0)) || 0
        );
        if (normalizedCount < requiredLifetime) {
          return;
        }
        if (activeCount < requiredActive) {
          return;
        }
        if (effect.trigger === 'firstAcquisition' && normalizedCount <= 0) {
          return;
        }
        if (effect.trigger === 'active' && activeCount <= 0) {
          return;
        }
        const effectId = typeof effect.id === 'string' && effect.id.trim()
          ? effect.id.trim()
          : `${entry.id}:effect:${index + 1}`;
        const effectLabel = typeof effect.label === 'string' && effect.label.trim()
          ? effect.label.trim()
          : elementSummary.label;
        const labelEntry = ensureElementEffectLabelEntry(elementSummary, effectLabel);
        if (typeof effect.description === 'string' && effect.description.trim()) {
          const descriptionText = effect.description.trim();
          if (!labelEntry.description) {
            labelEntry.description = descriptionText;
          } else if (!labelEntry.description.includes(descriptionText)) {
            labelEntry.description = `${labelEntry.description} · ${descriptionText}`;
          }
        }
        if (Array.isArray(effect.notes)) {
          effect.notes.forEach(note => {
            if (typeof note === 'string' && note.trim()) {
              appendElementLabelNote(labelEntry, note.trim());
            }
          });
        }
        if (stats.clickAdd != null) {
          pendingElementFlatEffects.push({
            type: 'click',
            value: stats.clickAdd,
            meta: {
              id: `element:${effectId}:clickAdd`,
              label: effectLabel,
              rarityId,
              source: 'elements'
            },
            summary: elementSummary,
            labelEntry,
            translationKey: 'apcFlat'
          });
        }
        if (stats.autoAdd != null) {
          pendingElementFlatEffects.push({
            type: 'auto',
            value: stats.autoAdd,
            meta: {
              id: `element:${effectId}:autoAdd`,
              label: effectLabel,
              rarityId,
              source: 'elements'
            },
            summary: elementSummary,
            labelEntry,
            translationKey: 'apsFlat'
          });
        }
      });
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

  pendingElementFlatEffects.forEach(entry => {
    const { type, value, meta, summary, labelEntry, translationKey } = entry;
    const applied = type === 'click'
      ? addClickElementFlat(value, meta)
      : addAutoElementFlat(value, meta);
    if (!Number.isFinite(applied) || applied === 0) {
      return;
    }
    if (summary) {
      if (type === 'click') {
        summary.clickFlatTotal += applied;
      } else {
        summary.autoFlatTotal += applied;
      }
    }
    if (labelEntry) {
      const formatted = formatElementFlatBonus(applied);
      if (formatted) {
        const fallback = translationKey === 'apcFlat'
          ? `APC +${formatted}`
          : `APS +${formatted}`;
        appendElementLabelEffect(
          labelEntry,
          translateCollectionEffect(translationKey, fallback, { value: formatted })
        );
      }
    }
  });

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

  elementEffectSummaries.forEach(summary => {
    if (!summary) {
      return;
    }
    if (Array.isArray(summary.activeLabels)) {
      summary.activeLabels = summary.activeLabels
        .map(entry => {
          if (!entry || typeof entry !== 'object') {
            return null;
          }
          const label = typeof entry.label === 'string' ? entry.label.trim() : '';
          if (!label) {
            return null;
          }
          const cleaned = { label };
          if (Array.isArray(entry.effects)) {
            const effects = entry.effects
              .map(effect => (typeof effect === 'string' ? effect.trim() : ''))
              .filter(effect => effect);
            if (effects.length) {
              cleaned.effects = effects;
            }
          }
          if (Array.isArray(entry.notes)) {
            const notes = entry.notes
              .map(note => (typeof note === 'string' ? note.trim() : ''))
              .filter(note => note);
            if (notes.length) {
              cleaned.notes = notes;
            }
          }
          if (typeof entry.description === 'string' && entry.description.trim()) {
            cleaned.description = entry.description.trim();
          }
          return cleaned;
        })
        .filter(entry => entry);
    } else {
      summary.activeLabels = [];
    }
    if (summary._labelDetails instanceof Map) {
      summary._labelDetails.clear();
    }
    delete summary._labelDetails;
  });

  const intervalChanged = setTicketStarAverageIntervalSeconds(mythiqueBonuses.ticketIntervalSeconds);
  if (intervalChanged && !ticketStarState.active) {
    resetTicketStarState({ reschedule: true });
  }
  gameState.offlineGainMultiplier = mythiqueBonuses.offlineMultiplier;
  const frenzyBonus = {
    perClick: mythiqueBonuses.frenzyChanceMultiplier,
    perSecond: mythiqueBonuses.frenzyChanceMultiplier,
    addPerClick: Math.max(0, frenzyChanceAdd.perClick || 0),
    addPerSecond: Math.max(0, frenzyChanceAdd.perSecond || 0)
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
  if (elementEffectSummaries.size > 0) {
    const elementSummaryStore = {};
    elementEffectSummaries.forEach((value, key) => {
      if (!value) {
        return;
      }
      elementSummaryStore[key] = {
        ...value,
        activeLabels: Array.isArray(value.activeLabels)
          ? value.activeLabels.map(entry => ({ ...entry }))
          : []
      };
    });
    if (Object.keys(elementSummaryStore).length > 0) {
      elementBonusSummary.elements = elementSummaryStore;
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

    const frenzyAdd = normalizeFrenzyChanceAdd(effects.frenzyChanceAdd);
    if (frenzyAdd.perClick > 0) {
      frenzyChanceAdd.perClick += frenzyAdd.perClick;
    }
    if (frenzyAdd.perSecond > 0) {
      frenzyChanceAdd.perSecond += frenzyAdd.perSecond;
    }

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

  let clickTotalMultiplier = LayeredNumber.one()
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
  const apsCritLabel = translateOrDefault('scripts.app.metaux.critLabel', 'Critique APC/APS');
  if (hasApsCritMultiplier) {
    clickTotalMultiplier = clickTotalMultiplier.multiply(apsCritMultiplier);
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
  if (hasApsCritMultiplier) {
    perClick = perClick.multiply(apsCritMultiplier);
    clickDetails.multipliers.push({
      id: 'apsCrit',
      label: apsCritLabel,
      value: apsCritMultiplier.clone(),
      source: 'metaux'
    });
  }
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
      label: apsCritLabel,
      value: apsCritMultiplier.clone(),
      source: 'metaux'
    });
  }
  clickDetails.sources.multipliers.apsCrit = hasApsCritMultiplier
    ? apsCritMultiplier.clone()
    : LayeredNumber.one();
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

function getShopBuildingTexts(def) {
  if (!def || typeof def !== 'object') {
    return { name: '', description: '' };
  }
  const id = typeof def.id === 'string' ? def.id.trim() : '';
  const baseKey = id ? `config.shop.buildings.${id}` : '';
  const fallbackName = typeof def.name === 'string' ? def.name : id;
  let name = fallbackName;
  if (baseKey) {
    const translatedName = translateOrDefault(`${baseKey}.name`, fallbackName);
    if (translatedName) {
      name = translatedName;
    }
  }
  const fallbackDescription = typeof def.effectSummary === 'string' && def.effectSummary.trim()
    ? def.effectSummary.trim()
    : (typeof def.description === 'string' ? def.description.trim() : '');
  let description = '';
  if (baseKey) {
    description =
      translateOrDefault(`${baseKey}.effectSummary`, '')
      || translateOrDefault(`${baseKey}.effect`, '')
      || translateOrDefault(`${baseKey}.description`, '');
  }
  if (!description) {
    description = fallbackDescription;
  }
  return { name, description };
}

function getShopActionLabel(level) {
  const isUpgrade = Number.isFinite(level) && Number(level) > 0;
  const key = isUpgrade ? 'scripts.app.shop.actionUpgrade' : 'scripts.app.shop.actionBuy';
  const fallback = isUpgrade ? 'Améliorer' : 'Acheter';
  return translateOrDefault(key, fallback);
}

function formatShopLevelLabel(level, maxLevel, capReached) {
  const resolvedLevel = Number.isFinite(level) ? Math.max(0, Math.floor(level)) : 0;
  const params = { level: formatIntegerLocalized(resolvedLevel) };
  let label;
  if (Number.isFinite(maxLevel)) {
    params.max = formatIntegerLocalized(Math.max(0, Math.floor(maxLevel)));
    label = translateOrDefault(
      'scripts.app.shop.levelLabelWithMax',
      `Niveau ${params.level} / ${params.max}`,
      params
    );
    if (capReached) {
      const suffix = translateOrDefault('scripts.app.shop.levelMaxSuffix', ' (max)');
      label += suffix;
    }
  } else {
    label = translateOrDefault('scripts.app.shop.levelLabel', `Niveau ${params.level}`, params);
  }
  return label;
}

function getShopLimitSuffix(limited) {
  if (!limited) {
    return '';
  }
  return translateOrDefault('scripts.app.shop.limitSuffix', ' (limité aux niveaux restants)');
}

function formatShopPriceText({ isFree, limitedQuantity, quantity, priceText }) {
  if (isFree) {
    return translateOrDefault('scripts.app.shop.free', 'Gratuit');
  }
  if (limitedQuantity) {
    return translateOrDefault(
      'scripts.app.shop.priceLimited',
      `Limité à x${quantity} — ${priceText}`,
      { quantity: formatIntegerLocalized(quantity), price: priceText }
    );
  }
  return priceText;
}

function formatShopAriaLabel({ state, action, name, quantity, limitNote, costValue }) {
  const params = {
    action: action || '',
    name: name || '',
    quantity: formatIntegerLocalized(Number.isFinite(quantity) ? quantity : Number(quantity) || 0),
    limitNote: limitNote || ''
  };
  const fallbackQuantity = `×${params.quantity}${params.limitNote}`;
  if (state === 'free') {
    return translateOrDefault(
      'scripts.app.shop.ariaActionFree',
      `${params.action} ${params.name} ${fallbackQuantity} (gratuit)`,
      params
    );
  }
  params.cost = costValue || '';
  if (state === 'cost') {
    return translateOrDefault(
      'scripts.app.shop.ariaActionCost',
      `${params.action} ${params.name} ${fallbackQuantity} (coût ${params.cost})`,
      params
    );
  }
  if (state === 'insufficientTickets') {
    return translateOrDefault(
      'scripts.app.shop.ariaActionInsufficientTickets',
      `${params.action} ${params.name} ${fallbackQuantity} (tickets gacha insuffisants)`,
      params
    );
  }
  if (state === 'insufficientCards') {
    return translateOrDefault(
      'scripts.app.shop.ariaActionInsufficientCards',
      `${params.action} ${params.name} ${fallbackQuantity} (cartes rares insuffisantes)`,
      params
    );
  }
  return translateOrDefault(
    'scripts.app.shop.ariaActionInsufficient',
    `${params.action} ${params.name} ${fallbackQuantity} (atomes insuffisants)`,
    params
  );
}

function getActiveShopPurchaseAmount() {
  if (!Array.isArray(SHOP_PURCHASE_AMOUNTS) || SHOP_PURCHASE_AMOUNTS.length === 0) {
    activeShopPurchaseAmount = 1;
    return activeShopPurchaseAmount;
  }
  if (!SHOP_PURCHASE_AMOUNTS.includes(activeShopPurchaseAmount)) {
    activeShopPurchaseAmount = SHOP_PURCHASE_AMOUNTS[0];
  }
  return activeShopPurchaseAmount;
}

function updateShopPurchaseControlState() {
  if (!shopPurchaseControls.size) {
    return;
  }
  if (elements.shopActionsHeader) {
    const controls = elements.shopActionsHeader.querySelector('.shop-actions-header__controls');
    if (controls) {
      const groupLabel = translateOrDefault(
        'index.sections.shop.quantityGroupLabel',
        "Quantité d'achat"
      );
      controls.setAttribute('aria-label', groupLabel);
    }
  }
  const activeQuantity = getActiveShopPurchaseAmount();
  shopPurchaseControls.forEach((button, quantity) => {
    if (!button) return;
    const isActive = quantity === activeQuantity;
    const formattedQuantity = `x${quantity}`;
    button.classList.toggle('is-active', isActive);
    button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    const key = isActive
      ? 'index.sections.shop.quantityToggleSelected'
      : 'index.sections.shop.quantityToggle';
    const fallback = isActive
      ? `${formattedQuantity} purchase amount selected`
      : `Select ${formattedQuantity} purchase amount`;
    const ariaLabel = translateOrDefault(key, fallback, { quantity: formattedQuantity });
    button.setAttribute('aria-label', ariaLabel);
    button.title = ariaLabel;
  });
}

function setActiveShopPurchaseAmount(quantity) {
  if (!Array.isArray(SHOP_PURCHASE_AMOUNTS) || SHOP_PURCHASE_AMOUNTS.length === 0) {
    activeShopPurchaseAmount = 1;
    updateShopPurchaseControlState();
    updateShopAffordability();
    return;
  }
  const numericQuantity = Number(quantity);
  const normalized = SHOP_PURCHASE_AMOUNTS.find(value => value === numericQuantity)
    ?? SHOP_PURCHASE_AMOUNTS[0];
  if (activeShopPurchaseAmount === normalized) {
    updateShopPurchaseControlState();
    return;
  }
  activeShopPurchaseAmount = normalized;
  updateShopPurchaseControlState();
  updateShopAffordability();
}

function renderShopPurchaseHeader() {
  if (!elements.shopActionsHeader) return;
  const header = elements.shopActionsHeader;
  header.innerHTML = '';
  if (!Array.isArray(SHOP_PURCHASE_AMOUNTS) || SHOP_PURCHASE_AMOUNTS.length === 0) {
    header.hidden = true;
    return;
  }
  header.hidden = false;
  const fragment = document.createDocumentFragment();
  const spacer = document.createElement('div');
  spacer.className = 'shop-actions-header__spacer';
  fragment.appendChild(spacer);
  const controls = document.createElement('div');
  controls.className = 'shop-actions-header__controls';
  const groupLabel = translateOrDefault(
    'index.sections.shop.quantityGroupLabel',
    "Quantité d'achat"
  );
  controls.setAttribute('role', 'group');
  controls.setAttribute('aria-label', groupLabel);
  shopPurchaseControls.clear();
  SHOP_PURCHASE_AMOUNTS.forEach(quantity => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'shop-actions-header__control';
    button.textContent = `x${quantity}`;
    button.dataset.quantity = String(quantity);
    button.addEventListener('click', () => {
      setActiveShopPurchaseAmount(quantity);
    });
    controls.appendChild(button);
    shopPurchaseControls.set(quantity, button);
  });
  fragment.appendChild(controls);
  header.appendChild(fragment);
  updateShopPurchaseControlState();
}

function getLocalizedUpgradeName(def) {
  return getShopBuildingTexts(def).name;
}

function getShopMaxPurchasePerAction(definition) {
  const raw = Number(definition?.maxQuantityPerPurchase);
  if (!Number.isFinite(raw) || raw <= 0) {
    return Infinity;
  }
  return Math.max(1, Math.floor(raw));
}

function buildShopItem(def) {
  const item = document.createElement('article');
  item.className = 'shop-item';
  item.dataset.upgradeId = def.id;
  item.setAttribute('role', 'listitem');

  const header = document.createElement('header');
  header.className = 'shop-item__header';

  const title = document.createElement('h3');
  const texts = getShopBuildingTexts(def);
  title.textContent = texts.name;

  const level = document.createElement('span');
  level.className = 'shop-item__level';

  header.append(title, level);

  const desc = document.createElement('p');
  desc.className = 'shop-item__description';
  desc.textContent = texts.description;

  const actions = document.createElement('div');
  actions.className = 'shop-item__actions';

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'shop-item__action';

  const priceLabel = document.createElement('span');
  priceLabel.className = 'shop-item__action-price';
  priceLabel.textContent = '—';

  button.append(priceLabel);
  button.addEventListener('click', () => {
    attemptPurchase(def, getActiveShopPurchaseAmount());
  });

  actions.appendChild(button);

  item.append(header, desc, actions);

  return {
    root: item,
    title,
    level,
    description: desc,
    action: {
      button,
      price: priceLabel
    }
  };
}

function updateShopUnlockHint() {
  const button = elements.navShopButton;
  if (!button) {
    return;
  }

  let shouldVibrate = false;
  if (!button.hidden && !button.disabled && lastVisibleShopIndex >= 0 && lastVisibleShopIndex < UPGRADE_DEFS.length) {
    const def = UPGRADE_DEFS[lastVisibleShopIndex];
    if (def) {
      const level = getUpgradeLevel(gameState.upgrades, def.id);
      if (!Number.isFinite(level) || level <= 0) {
        const remainingLevels = getRemainingUpgradeCapacity(def);
        if (!Number.isFinite(remainingLevels) || remainingLevels > 0) {
          const cost = computeUpgradeCost(def, 1);
          const atoms = toLayeredValue(gameState.atoms, 0);
          shouldVibrate = atoms.compare(cost) >= 0;
        }
      }
    }
  }

  button.classList.toggle('nav-button--vibrate', shouldVibrate);
}

function updateShopVisibility() {
  if (!shopRows.size) {
    lastVisibleShopIndex = -1;
    updateShopUnlockHint();
    return;
  }
  const unlocks = getShopUnlockSet();

  let visibleLimit = -1;
  unlocks.forEach(id => {
    const unlockIndex = UPGRADE_INDEX_MAP.get(id);
    if (unlockIndex != null && unlockIndex > visibleLimit) {
      visibleLimit = unlockIndex;
    }
  });
  if (visibleLimit < 0 && UPGRADE_DEFS.length > 0) {
    visibleLimit = 0;
  }
  if (visibleLimit >= UPGRADE_DEFS.length) {
    visibleLimit = UPGRADE_DEFS.length - 1;
  }

  let highestVisibleIndex = -1;

  UPGRADE_DEFS.forEach((def, index) => {
    const row = shopRows.get(def.id);
    if (!row) return;

    const shouldReveal = index <= visibleLimit;
    const requiresDuplicates = Array.isArray(def?.duplicateCardCost?.cardIds)
      && def.duplicateCardCost.cardIds.length > 0;
    const hasRequiredCards = !requiresDuplicates
      || hasDuplicateCardResources(def.duplicateCardCost, 1);
    const reveal = shouldReveal && hasRequiredCards;
    row.root.hidden = !reveal;
    row.root.classList.toggle('shop-item--locked', !reveal);
    if (reveal) {
      unlocks.add(def.id);
      if (index > highestVisibleIndex) {
        highestVisibleIndex = index;
      }
    }
  });

  lastVisibleShopIndex = highestVisibleIndex >= 0 ? highestVisibleIndex : visibleLimit;
  updateShopUnlockHint();
}

function updateShopAffordability() {
  updateShopPurchaseControlState();
  if (!shopRows.size) return;
  UPGRADE_DEFS.forEach(def => {
    const row = shopRows.get(def.id);
    if (!row) return;
    const texts = getShopBuildingTexts(def);
    if (row.title) {
      row.title.textContent = texts.name;
    }
    if (row.description) {
      row.description.textContent = texts.description;
    }
    const level = getUpgradeLevel(gameState.upgrades, def.id);
    const maxLevel = resolveUpgradeMaxLevel(def);
    const remainingLevels = getRemainingUpgradeCapacity(def);
    const hasFiniteCap = Number.isFinite(maxLevel);
    const capReached = Number.isFinite(remainingLevels) && remainingLevels <= 0;
    row.level.textContent = formatShopLevelLabel(level, maxLevel, capReached);
    let anyAffordable = false;
    const actionLabel = getShopActionLabel(level);
    const displayName = texts.name;
    const shopFree = isDevKitShopFree();
    const entry = row.action;

    if (!entry) {
      row.root.classList.toggle('shop-item--ready', anyAffordable);
      return;
    }

    const baseQuantity = getActiveShopPurchaseAmount();

    if (capReached) {
      entry.price.textContent = t('scripts.app.shop.limitReached');
      entry.button.disabled = true;
      entry.button.classList.remove('is-ready');
      const ariaLabel = translateOrDefault(
        'scripts.app.shop.ariaMaxLevel',
        `${displayName} a atteint son niveau maximum`,
        { name: displayName }
      );
      entry.button.setAttribute('aria-label', ariaLabel);
      entry.button.title = ariaLabel;
      row.root.classList.toggle('shop-item--ready', anyAffordable);
      return;
    }

    let effectiveQuantity = Number.isFinite(remainingLevels)
      ? Math.min(baseQuantity, remainingLevels)
      : baseQuantity;
    let limited = Number.isFinite(remainingLevels) && effectiveQuantity !== baseQuantity;
    const perPurchaseLimit = getShopMaxPurchasePerAction(def);
    if (Number.isFinite(perPurchaseLimit) && perPurchaseLimit > 0 && effectiveQuantity > perPurchaseLimit) {
      effectiveQuantity = perPurchaseLimit;
      limited = true;
    }

    const cost = computeUpgradeCost(def, effectiveQuantity);
    const gachaCost = computeShopGachaTicketCost(def, effectiveQuantity);
    const ticketsAvailable = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
    const ticketAffordable = gachaCost <= 0 || ticketsAvailable >= gachaCost;
    const atomAffordable = gameState.atoms.compare(cost) >= 0;
    const duplicateInfo = def.duplicateCardCost
      ? getDuplicateCardCostInfo(def.duplicateCardCost, effectiveQuantity)
      : null;
    const duplicateAffordable = !duplicateInfo || duplicateInfo.available >= duplicateInfo.required;
    const duplicateLabel = duplicateInfo ? formatDuplicateCostText(duplicateInfo) : '';
    const affordable = shopFree || (atomAffordable && ticketAffordable && duplicateAffordable);
    const costDisplay = formatShopCost({ atomCost: cost, gachaTicketCost: gachaCost });
    const normalizedCostDisplay = costDisplay === '0' && duplicateLabel ? '' : costDisplay;
    const combinedPrice = formatShopCombinedCost([duplicateLabel, normalizedCostDisplay]);
    const priceText = combinedPrice
      || normalizedCostDisplay
      || duplicateLabel
      || costDisplay;
    entry.price.textContent = formatShopPriceText({
      isFree: shopFree,
      limitedQuantity: limited,
      quantity: limited ? effectiveQuantity : baseQuantity,
      priceText: priceText || costDisplay
    });
    const enabled = affordable && effectiveQuantity > 0;
    entry.button.disabled = !enabled;
    entry.button.classList.toggle('is-ready', enabled);
    if (enabled) {
      anyAffordable = true;
    }
    const displayQuantity = limited ? effectiveQuantity : baseQuantity;
    const limitNote = getShopLimitSuffix(limited);
    let ariaState = enabled ? (shopFree ? 'free' : 'cost') : 'cost';
    if (!enabled && !shopFree) {
      if (!ticketAffordable) {
        ariaState = 'insufficientTickets';
      } else if (!duplicateAffordable) {
        ariaState = 'insufficientCards';
      } else if (!atomAffordable) {
        ariaState = 'insufficient';
      }
    }
    const ariaLabel = formatShopAriaLabel({
      state: ariaState,
      action: actionLabel,
      name: displayName,
      quantity: displayQuantity,
      limitNote,
      costValue: priceText || costDisplay
    });
    entry.button.setAttribute('aria-label', ariaLabel);
    entry.button.title = ariaLabel;

    row.root.classList.toggle('shop-item--ready', anyAffordable);
  });
  updateShopVisibility();
}

function renderShop() {
  if (!elements.shopList) return;
  renderShopPurchaseHeader();
  shopRows.clear();
  elements.shopList.innerHTML = '';
  const fragment = document.createDocumentFragment();
  UPGRADE_DEFS.forEach(def => {
    const row = buildShopItem(def);
    fragment.appendChild(row.root);
    shopRows.set(def.id, row);
  });
  elements.shopList.appendChild(fragment);
  updateShopAffordability();
}

function getTrophyRewardParams(def) {
  if (!def || typeof def !== 'object' || !def.reward) {
    return null;
  }
  const params = {};
  const reward = def.reward;
  const bonusCandidates = [
    reward.trophyMultiplierAdd,
    reward.trophyMultiplierBonus,
    reward.trophyMultiplier,
    reward.trophyBonus
  ];
  const bonusValue = bonusCandidates.find(value => Number.isFinite(Number(value)));
  if (bonusValue != null && Number.isFinite(Number(bonusValue))) {
    const numeric = Number(bonusValue);
    params.bonus = formatTrophyBonusValue(numeric);
    params.total = formatTrophyBonusValue(1 + numeric);
  }
  let multiplierValue = null;
  if (reward.multiplier != null) {
    if (typeof reward.multiplier === 'number') {
      multiplierValue = reward.multiplier;
    } else if (reward.multiplier instanceof LayeredNumber) {
      multiplierValue = reward.multiplier.toNumber();
    } else if (typeof reward.multiplier === 'object') {
      multiplierValue = reward.multiplier.global
        ?? reward.multiplier.all
        ?? reward.multiplier.total
        ?? reward.multiplier.perClick
        ?? reward.multiplier.click
        ?? reward.multiplier.perSecond
        ?? reward.multiplier.auto;
    }
  }
  if (multiplierValue != null && Number.isFinite(Number(multiplierValue))) {
    params.multiplier = formatNumberLocalized(Number(multiplierValue), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }
  return Object.keys(params).length ? params : null;
}

function getTrophyTranslationBases(id) {
  if (typeof id !== 'string' || !id) {
    return [];
  }
  const bases = [];
  if (id.startsWith('scale')) {
    bases.push(`scripts.appData.atomScale.trophies.${id}`);
    bases.push(`scripts.appData.trophies.presets.${id}`);
    bases.push(`config.trophies.presets.${id}`);
  }
  bases.push(`scripts.appData.trophies.${id}`);
  bases.push(`config.trophies.${id}`);
  return bases;
}

function translateTrophyField(def, field, fallback, params) {
  const bases = getTrophyTranslationBases(def?.id);
  for (const base of bases) {
    const key = `${base}.${field}`;
    const translated = translateOrDefault(key, '', params);
    if (translated) {
      return translated;
    }
  }
  return fallback || '';
}

function getTrophyDisplayTexts(def) {
  if (!def || typeof def !== 'object') {
    return { name: '', description: '', reward: '' };
  }
  const fallbackName = typeof def.name === 'string' ? def.name : '';
  const fallbackDescription = typeof def.description === 'string' ? def.description : '';
  const fallbackReward = typeof def.rewardText === 'string'
    ? def.rewardText
    : (def.reward && typeof def.reward.description === 'string' ? def.reward.description : '');

  let name = translateTrophyField(def, 'name', fallbackName);
  if (!name) {
    name = fallbackName;
  }

  let descriptionParams = null;
  const descriptionTarget = def.targetText || '';
  const descriptionFlavor = translateTrophyField(def, 'flavor', def.flavor || '');
  if (descriptionTarget || descriptionFlavor) {
    descriptionParams = {
      target: descriptionTarget,
      flavor: descriptionFlavor
    };
  }
  let description = '';
  if (descriptionParams) {
    description = translateOrDefault(
      'scripts.appData.atomScale.trophies.description',
      '',
      descriptionParams
    );
    if (!description) {
      description = translateOrDefault('config.trophies.description', '', descriptionParams);
    }
  }
  if (!description) {
    description = translateTrophyField(def, 'description', '', descriptionParams);
  }
  if (!description) {
    description = fallbackDescription;
  }

  const rewardParams = getTrophyRewardParams(def);
  let rewardText = translateTrophyField(def, 'reward', '', rewardParams);
  if (!rewardText && rewardParams) {
    rewardText = translateOrDefault('scripts.appData.atomScale.trophies.reward', '', rewardParams);
  }
  if (!rewardText && rewardParams) {
    rewardText = translateOrDefault('config.trophies.reward.description', '', rewardParams);
  }
  if (!rewardText) {
    rewardText = fallbackReward;
  }

  return {
    name,
    description,
    reward: rewardText
  };
}

function buildGoalCard(def) {
  const card = document.createElement('article');
  card.className = 'goal-card';
  card.dataset.trophyId = def.id;
  card.setAttribute('role', 'listitem');
  card.classList.add('goal-card--locked');
  card.hidden = true;
  card.setAttribute('aria-hidden', 'true');

  const header = document.createElement('header');
  header.className = 'goal-card__header';

  const title = document.createElement('h3');
  const texts = getTrophyDisplayTexts(def);
  title.textContent = texts.name;
  title.className = 'goal-card__title';

  header.append(title);

  const description = document.createElement('p');
  description.className = 'goal-card__description';
  description.textContent = texts.description || '';

  card.append(header, description);

  const reward = document.createElement('p');
  reward.className = 'goal-card__reward';
  reward.textContent = texts.reward || '';
  reward.hidden = !texts.reward;
  card.appendChild(reward);

  return { root: card, title, description, reward };
}

function renderGoals() {
  if (!elements.goalsList) return;
  trophyCards.clear();
  elements.goalsList.innerHTML = '';
  if (!TROPHY_DEFS.length) {
    if (elements.goalsEmpty) {
      elements.goalsEmpty.hidden = false;
      elements.goalsEmpty.setAttribute('aria-hidden', 'false');
    }
    return;
  }
  const fragment = document.createDocumentFragment();
  TROPHY_DEFS.forEach(def => {
    const card = buildGoalCard(def);
    trophyCards.set(def.id, card);
    fragment.appendChild(card.root);
  });
  elements.goalsList.appendChild(fragment);
  refreshGoalCardTexts();
  updateGoalsUI();
}

function refreshGoalCardTexts() {
  if (!trophyCards.size) {
    return;
  }
  TROPHY_DEFS.forEach(def => {
    const card = trophyCards.get(def.id);
    if (!card) {
      return;
    }
    const texts = getTrophyDisplayTexts(def);
    if (card.title) {
      card.title.textContent = texts.name;
    }
    if (card.description) {
      card.description.textContent = texts.description || '';
    }
    if (card.reward) {
      if (texts.reward) {
        card.reward.textContent = texts.reward;
        card.reward.hidden = false;
      } else {
        card.reward.textContent = '';
        card.reward.hidden = true;
      }
    }
  });
}

function attemptPurchase(def, quantity = 1) {
  const buyAmount = Math.max(1, Math.floor(Number(quantity) || 0));
  const remainingLevels = getRemainingUpgradeCapacity(def);
  const cappedOut = Number.isFinite(remainingLevels) && remainingLevels <= 0;
  if (cappedOut) {
    showToast(t('scripts.app.shop.maxLevel'));
    return;
  }
  const finalAmount = Number.isFinite(remainingLevels)
    ? Math.min(buyAmount, remainingLevels)
    : buyAmount;
  if (!Number.isFinite(finalAmount) || finalAmount <= 0) {
    showToast(t('scripts.app.shop.maxLevel'));
    return;
  }
  const perPurchaseLimit = getShopMaxPurchasePerAction(def);
  const purchaseAmount = Number.isFinite(perPurchaseLimit) && perPurchaseLimit > 0
    ? Math.min(finalAmount, perPurchaseLimit)
    : finalAmount;
  if (!Number.isFinite(purchaseAmount) || purchaseAmount <= 0) {
    showToast(t('scripts.app.shop.maxLevel'));
    return;
  }
  const cost = computeUpgradeCost(def, purchaseAmount);
  const shopFree = isDevKitShopFree();
  const gachaCost = computeShopGachaTicketCost(def, purchaseAmount);
  const availableTickets = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const duplicateInfo = def.duplicateCardCost
    ? getDuplicateCardCostInfo(def.duplicateCardCost, purchaseAmount)
    : null;
  if (!shopFree && gameState.atoms.compare(cost) < 0) {
    showToast(t('scripts.app.shop.notEnoughAtoms'));
    return;
  }
  if (!shopFree && gachaCost > availableTickets) {
    showToast(t('scripts.app.shop.notEnoughGachaTickets'));
    return;
  }
  if (!shopFree && duplicateInfo && duplicateInfo.available < duplicateInfo.required) {
    showToast(t('scripts.app.shop.notEnoughRareCards'));
    return;
  }
  if (!shopFree) {
    gameState.atoms = gameState.atoms.subtract(cost);
    if (gachaCost > 0) {
      gameState.gachaTickets = availableTickets - gachaCost;
      if (typeof updateArcadeTicketDisplay === 'function') {
        try {
          updateArcadeTicketDisplay();
        } catch (error) {
          console.warn('Shop: unable to refresh ticket display after purchase', error);
        }
      }
    }
    if (duplicateInfo && duplicateInfo.required > 0) {
      const consumed = spendDuplicateCardCost(def.duplicateCardCost, purchaseAmount);
      const summary = formatDuplicateSpendToast(consumed);
      if (summary) {
        showToast(t('scripts.app.shop.rareDuplicateSpent', { cards: summary }));
      }
    }
  }
  const currentLevel = Number(gameState.upgrades[def.id]);
  const normalizedLevel = Number.isFinite(currentLevel) && currentLevel > 0
    ? Math.floor(currentLevel)
    : 0;
  gameState.upgrades[def.id] = normalizedLevel + purchaseAmount;
  const ticketsAwarded = grantShopGachaTickets(def, purchaseAmount);
  const mach3TicketsAwarded = grantShopMach3Tickets(def, purchaseAmount);
  recalcProduction();
  updateUI();
  const limitSuffix = purchaseAmount < buyAmount ? getShopLimitSuffix(true) : '';
  const displayName = getLocalizedUpgradeName(def);
  showToast(shopFree
    ? t('scripts.app.shop.devkitFreePurchase', {
      name: displayName,
      quantity: purchaseAmount,
      suffix: limitSuffix
    })
    : t('scripts.app.shop.purchase', {
      name: displayName,
      quantity: purchaseAmount,
      suffix: limitSuffix
    }));
  if (ticketsAwarded > 0) {
    const unitKey = ticketsAwarded === 1
      ? 'scripts.app.shop.ticketUnit.single'
      : 'scripts.app.shop.ticketUnit.plural';
    const fallbackUnit = ticketsAwarded === 1 ? 'gacha ticket' : 'gacha tickets';
    const unitLabel = translateOrDefault(unitKey, fallbackUnit);
    const countLabel = formatIntegerLocalized(ticketsAwarded);
    showToast(t('scripts.app.shop.ticketReward', { count: countLabel, unit: unitLabel }));
  }
  if (mach3TicketsAwarded > 0) {
    const unitKey = mach3TicketsAwarded === 1
      ? 'scripts.app.shop.mach3Unit.single'
      : 'scripts.app.shop.mach3Unit.plural';
    const fallbackUnit = mach3TicketsAwarded === 1 ? 'ticket Mach3' : 'tickets Mach3';
    const unitLabel = translateOrDefault(unitKey, fallbackUnit);
    const countLabel = formatIntegerLocalized(mach3TicketsAwarded);
    showToast(t('scripts.app.shop.mach3Reward', { count: countLabel, unit: unitLabel }));
  }
}

function getArcadeHubCardContainer(card) {
  return card?.closest('.arcade-hub__grid') || null;
}

function clearArcadeHubCardLongPressTimer(state) {
  if (state?.longPressTimer) {
    clearTimeout(state.longPressTimer);
    state.longPressTimer = null;
  }
}

function setArcadeHubCardDropTarget(state, target) {
  if (!state) {
    return;
  }
  if (state.dropTarget && state.dropTarget !== target) {
    state.dropTarget.classList.remove('arcade-hub-card--drop-target');
  }
  const normalizedTarget = target && target !== state.card ? target : null;
  state.dropTarget = normalizedTarget;
  if (state.dropTarget) {
    state.dropTarget.classList.add('arcade-hub-card--drop-target');
  }
}

function findArcadeHubCardDropTarget(state, clientX, clientY) {
  if (!state || !state.container) {
    return null;
  }
  const element = document.elementFromPoint(clientX, clientY);
  if (!element) {
    return null;
  }
  const candidate = element.closest('.arcade-hub-card');
  if (!candidate || candidate === state.card) {
    return null;
  }
  if (candidate.parentElement !== state.container) {
    return null;
  }
  return candidate;
}

function updateArcadeHubCardDragPosition(state, clientX, clientY) {
  if (!state || !state.card) {
    return;
  }
  const targetLeft = clientX - state.offsetX;
  const targetTop = clientY - state.offsetY;
  const deltaX = targetLeft - state.originLeft;
  const deltaY = targetTop - state.originTop;
  state.card.style.setProperty('--arcade-hub-card-translate-x', `${deltaX}px`);
  state.card.style.setProperty('--arcade-hub-card-translate-y', `${deltaY}px`);
}

function beginArcadeHubCardDrag(state) {
  if (!state || state.dragging || activeArcadeHubCardDrag !== state) {
    return;
  }
  if (!state.card || !state.container) {
    return;
  }
  state.dragging = true;
  const card = state.card;
  card.dataset.arcadeDragging = 'true';
  card.classList.add('arcade-hub-card--dragging');
  card.style.transition = 'none';
  card.style.pointerEvents = 'none';
  card.style.touchAction = 'none';
  card.style.zIndex = '1000';
  card.style.willChange = 'transform';
  if (state.pointerType === 'touch') {
    if (state.container) {
      state.container.style.touchAction = 'none';
    }
    if (document?.body) {
      document.body.style.touchAction = 'none';
    }
  }
  document.body.classList.add('arcade-hub-reordering');
  updateArcadeHubCardDragPosition(state, state.lastClientX, state.lastClientY);
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      navigator.vibrate(10);
    }
  } catch (error) {
    // Ignore vibration errors (unsupported device or permissions)
  }
}

function swapArcadeHubCards(source, target) {
  if (!source || !target) {
    return false;
  }
  const parent = source.parentElement;
  if (!parent || parent !== target.parentElement) {
    return false;
  }
  const marker = document.createTextNode('');
  parent.insertBefore(marker, source);
  parent.replaceChild(source, target);
  parent.insertBefore(target, marker);
  parent.removeChild(marker);
  return true;
}

function finalizeArcadeHubCardDrag({ dropTarget } = {}) {
  const state = activeArcadeHubCardDrag;
  if (!state) {
    return;
  }
  activeArcadeHubCardDrag = null;
  clearArcadeHubCardLongPressTimer(state);
  const card = state.card;
  const container = state.container;
  const target = dropTarget && dropTarget !== card ? dropTarget : state.dropTarget;
  let swapped = false;
  if (state.dragging && card && container && target && target.parentElement === container) {
    swapped = swapArcadeHubCards(card, target);
    if (swapped) {
      persistArcadeHubCardOrder(container);
      target.classList.add('arcade-hub-card--swap-feedback');
      card.classList.add('arcade-hub-card--swap-feedback');
      setTimeout(() => {
        target.classList.remove('arcade-hub-card--swap-feedback');
        card.classList.remove('arcade-hub-card--swap-feedback');
      }, 220);
    }
  }
  setArcadeHubCardDropTarget(state, null);
  if (state.dragging && card) {
    card.dataset.arcadeDragSuppressClick = 'true';
    setTimeout(() => {
      if (card.dataset.arcadeDragSuppressClick === 'true') {
        delete card.dataset.arcadeDragSuppressClick;
      }
    }, 0);
  }
  if (card) {
    if (state.dragging) {
      card.style.transition = 'transform 180ms ease';
      requestAnimationFrame(() => {
        if (state.originalTranslateX) {
          card.style.setProperty('--arcade-hub-card-translate-x', state.originalTranslateX);
        } else {
          card.style.removeProperty('--arcade-hub-card-translate-x');
        }
        if (state.originalTranslateY) {
          card.style.setProperty('--arcade-hub-card-translate-y', state.originalTranslateY);
        } else {
          card.style.removeProperty('--arcade-hub-card-translate-y');
        }
      });
    } else {
      if (state.originalTranslateX) {
        card.style.setProperty('--arcade-hub-card-translate-x', state.originalTranslateX);
      } else {
        card.style.removeProperty('--arcade-hub-card-translate-x');
      }
      if (state.originalTranslateY) {
        card.style.setProperty('--arcade-hub-card-translate-y', state.originalTranslateY);
      } else {
        card.style.removeProperty('--arcade-hub-card-translate-y');
      }
      card.style.transition = state.originalTransition || '';
    }
  }
  const restore = () => {
    if (!card) {
      return;
    }
    card.style.transition = state.originalTransition || '';
    card.style.pointerEvents = state.originalPointerEvents || '';
    card.style.touchAction = state.originalTouchAction || '';
    card.style.zIndex = state.originalZIndex || '';
    card.style.willChange = state.originalWillChange || '';
    card.classList.remove('arcade-hub-card--dragging');
    delete card.dataset.arcadeDragging;
    delete card.dataset.arcadeDragPending;
    document.body.classList.remove('arcade-hub-reordering');
    if (state.pointerType === 'touch') {
      if (state.container) {
        state.container.style.touchAction = state.originalContainerTouchAction || '';
      }
      if (document?.body) {
        document.body.style.touchAction = state.originalBodyTouchAction || '';
      }
    }
    if (state.focusOnReturn && typeof card.focus === 'function') {
      card.focus({ preventScroll: true });
    }
  };
  if (state.dragging && card) {
    setTimeout(restore, 200);
  } else {
    restore();
  }
}

function cancelArcadeHubCardDrag(options = {}) {
  const state = activeArcadeHubCardDrag;
  if (!state) {
    return;
  }
  activeArcadeHubCardDrag = null;
  clearArcadeHubCardLongPressTimer(state);
  setArcadeHubCardDropTarget(state, null);
  const card = state.card;
  if (!card) {
    return;
  }
  if (state.originalTranslateX) {
    card.style.setProperty('--arcade-hub-card-translate-x', state.originalTranslateX);
  } else {
    card.style.removeProperty('--arcade-hub-card-translate-x');
  }
  if (state.originalTranslateY) {
    card.style.setProperty('--arcade-hub-card-translate-y', state.originalTranslateY);
  } else {
    card.style.removeProperty('--arcade-hub-card-translate-y');
  }
  card.style.transition = state.originalTransition || '';
  card.style.pointerEvents = state.originalPointerEvents || '';
  card.style.touchAction = state.originalTouchAction || '';
  card.style.zIndex = state.originalZIndex || '';
  card.style.willChange = state.originalWillChange || '';
  card.classList.remove('arcade-hub-card--dragging');
  delete card.dataset.arcadeDragging;
  delete card.dataset.arcadeDragPending;
  document.body.classList.remove('arcade-hub-reordering');
  if (state.pointerType === 'touch') {
    if (state.container) {
      state.container.style.touchAction = state.originalContainerTouchAction || '';
    }
    if (document?.body) {
      document.body.style.touchAction = state.originalBodyTouchAction || '';
    }
  }
  if (options.suppressClick) {
    card.dataset.arcadeDragSuppressClick = 'true';
    setTimeout(() => {
      if (card.dataset.arcadeDragSuppressClick === 'true') {
        delete card.dataset.arcadeDragSuppressClick;
      }
    }, 0);
  }
}

function handleArcadeHubCardPointerDown(event, card) {
  if (!card || activeArcadeHubCardDrag) {
    return;
  }
  if (event.button !== undefined && event.button !== 0) {
    return;
  }
  if (event.target && event.target.closest('.arcade-hub-card__toggle')) {
    return;
  }
  const container = getArcadeHubCardContainer(card);
  if (!container) {
    return;
  }
  const rect = card.getBoundingClientRect();
  activeArcadeHubCardDrag = {
    card,
    container,
    pointerId: event.pointerId,
    pointerType: event.pointerType || '',
    originLeft: rect.left,
    originTop: rect.top,
    offsetX: event.clientX - rect.left,
    offsetY: event.clientY - rect.top,
    startClientX: event.clientX,
    startClientY: event.clientY,
    lastClientX: event.clientX,
    lastClientY: event.clientY,
    originalTransition: card.style.transition || '',
    originalPointerEvents: card.style.pointerEvents || '',
    originalTouchAction: card.style.touchAction || '',
    originalZIndex: card.style.zIndex || '',
    originalWillChange: card.style.willChange || '',
    originalTranslateX: card.style.getPropertyValue('--arcade-hub-card-translate-x') || '',
    originalTranslateY: card.style.getPropertyValue('--arcade-hub-card-translate-y') || '',
    originalContainerTouchAction: container?.style?.touchAction || '',
    originalBodyTouchAction: document?.body?.style?.touchAction || '',
    dropTarget: null,
    dragging: false,
    focusOnReturn: card === document.activeElement
  };
  const state = activeArcadeHubCardDrag;
  state.longPressTimer = setTimeout(() => {
    beginArcadeHubCardDrag(state);
  }, ARCADE_HUB_CARD_REORDER_DELAY_MS);
  card.dataset.arcadeDragPending = 'true';
}

function getArcadeHubCardMoveThreshold(state) {
  if (!state) {
    return ARCADE_HUB_CARD_REORDER_MOVE_THRESHOLD;
  }
  const pointerType = state.pointerType;
  if (pointerType === 'touch') {
    return ARCADE_HUB_CARD_REORDER_TOUCH_MOVE_THRESHOLD;
  }
  if (pointerType === 'pen') {
    return ARCADE_HUB_CARD_REORDER_PEN_MOVE_THRESHOLD;
  }
  return ARCADE_HUB_CARD_REORDER_MOVE_THRESHOLD;
}

function handleArcadeHubPointerMove(event) {
  const state = activeArcadeHubCardDrag;
  if (!state || event.pointerId !== state.pointerId) {
    return;
  }
  state.lastClientX = event.clientX;
  state.lastClientY = event.clientY;
  if (!state.dragging) {
    const deltaX = event.clientX - state.startClientX;
    const deltaY = event.clientY - state.startClientY;
    const threshold = getArcadeHubCardMoveThreshold(state);
    if (Math.hypot(deltaX, deltaY) > threshold) {
      cancelArcadeHubCardDrag();
    }
    return;
  }
  if (event.cancelable) {
    event.preventDefault();
  }
  updateArcadeHubCardDragPosition(state, event.clientX, event.clientY);
  const target = findArcadeHubCardDropTarget(state, event.clientX, event.clientY);
  setArcadeHubCardDropTarget(state, target);
}

function handleArcadeHubPointerUp(event) {
  const state = activeArcadeHubCardDrag;
  if (!state || event.pointerId !== state.pointerId) {
    return;
  }
  if (state.dragging && event.cancelable) {
    event.preventDefault();
  }
  finalizeArcadeHubCardDrag();
}

function handleArcadeHubPointerCancel(event) {
  const state = activeArcadeHubCardDrag;
  if (!state || event.pointerId !== state.pointerId) {
    return;
  }
  cancelArcadeHubCardDrag({ suppressClick: true });
}

if (typeof window !== 'undefined') {
  window.addEventListener('pointermove', handleArcadeHubPointerMove, { passive: false });
  window.addEventListener('pointerup', handleArcadeHubPointerUp);
  window.addEventListener('pointercancel', handleArcadeHubPointerCancel);
}

function readStoredArcadeHubCardOrder() {
  try {
    const raw = globalThis.localStorage?.getItem(ARCADE_HUB_CARD_ORDER_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map(value => (typeof value === 'string' ? value.trim() : ''))
      .filter(value => !!value);
  } catch (error) {
    console.warn('Unable to read arcade hub card order', error);
  }
  return [];
}

function writeStoredArcadeHubCardOrder(order) {
  const normalized = [];
  if (Array.isArray(order)) {
    const seen = new Set();
    order.forEach(value => {
      if (typeof value !== 'string') {
        return;
      }
      const trimmed = value.trim();
      if (!trimmed || seen.has(trimmed)) {
        return;
      }
      seen.add(trimmed);
      normalized.push(trimmed);
    });
  }
  arcadeHubCardOrderCache = normalized;
  try {
    globalThis.localStorage?.setItem(ARCADE_HUB_CARD_ORDER_STORAGE_KEY, JSON.stringify(normalized));
  } catch (error) {
    console.warn('Unable to persist arcade hub card order', error);
  }
}

function getArcadeHubCardOrder() {
  if (!Array.isArray(arcadeHubCardOrderCache)) {
    arcadeHubCardOrderCache = readStoredArcadeHubCardOrder();
  }
  return arcadeHubCardOrderCache;
}

function persistArcadeHubCardOrder(container) {
  const parent = container || document.querySelector('.arcade-hub__grid');
  if (!parent) {
    return;
  }
  const ids = Array.from(parent.querySelectorAll('.arcade-hub-card'))
    .map(card => getArcadeHubCardId(card))
    .filter(id => !!id);
  if (!ids.length) {
    writeStoredArcadeHubCardOrder([]);
    return;
  }
  const cached = getArcadeHubCardOrder();
  if (
    cached.length === ids.length
    && cached.every((value, index) => value === ids[index])
  ) {
    return;
  }
  writeStoredArcadeHubCardOrder(ids);
}

function applyStoredArcadeHubCardOrder() {
  const order = getArcadeHubCardOrder();
  if (!order.length) {
    return;
  }
  const grid = document.querySelector('.arcade-hub__grid');
  if (!grid) {
    return;
  }
  const cards = Array.from(grid.querySelectorAll('.arcade-hub-card'));
  if (!cards.length) {
    return;
  }
  const cardMap = new Map();
  cards.forEach(card => {
    const id = getArcadeHubCardId(card);
    if (id) {
      cardMap.set(id, card);
    }
  });
  const fragment = document.createDocumentFragment();
  order.forEach(id => {
    const card = cardMap.get(id);
    if (card && card.parentElement === grid) {
      fragment.appendChild(card);
      cardMap.delete(id);
    }
  });
  cardMap.forEach(card => {
    if (card.parentElement === grid) {
      fragment.appendChild(card);
    }
  });
  if (fragment.childNodes.length) {
    grid.appendChild(fragment);
  }
}

function updateMilestone() {
  if (!elements.nextMilestone) return;
  for (const milestone of milestoneList) {
    if (gameState.lifetime.compare(milestone.amount) < 0) {
      elements.nextMilestone.textContent = milestone.text;
      return;
    }
  }
  elements.nextMilestone.textContent = t('scripts.app.shop.milestoneHint');
}

function updateGoalsUI() {
  if (!elements.goalsList || !trophyCards.size) return;
  const unlockedSet = getUnlockedTrophySet();
  const featureUnlocked = areAchievementsFeatureUnlocked();
  let visibleCount = 0;
  TROPHY_DEFS.forEach(def => {
    const card = trophyCards.get(def.id);
    if (!card) return;
    const isUnlocked = unlockedSet.has(def.id);
    const shouldShow = featureUnlocked && isUnlocked;
    card.root.classList.toggle('goal-card--completed', isUnlocked);
    card.root.classList.toggle('goal-card--locked', !isUnlocked);
    card.root.hidden = !shouldShow;
    card.root.setAttribute('aria-hidden', String(!shouldShow));
    if (shouldShow) {
      visibleCount += 1;
    }
  });
  if (elements.goalsEmpty) {
    const hideEmpty = !featureUnlocked || visibleCount > 0;
    elements.goalsEmpty.hidden = hideEmpty;
    elements.goalsEmpty.setAttribute('aria-hidden', hideEmpty ? 'true' : 'false');
  }
}

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
    const apsCritPanel = elements.statusApsCrit;
    const apsCritActive = Boolean(apsCritPanel && !apsCritPanel.hidden);
    const hasActive = Boolean(apsActive || apcActive || apsCritActive);
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

const uiTextContentCache = new WeakMap();
const uiAttributeCache = new WeakMap();

function setTextContentIfChanged(element, nextValue) {
  if (!element) {
    return false;
  }
  const normalized = nextValue == null ? '' : String(nextValue);
  const previous = uiTextContentCache.get(element);
  if (previous === normalized) {
    return false;
  }
  element.textContent = normalized;
  uiTextContentCache.set(element, normalized);
  return true;
}

function setAttributeIfChanged(element, name, value) {
  if (!element || !name) {
    return false;
  }
  const normalizedName = String(name);
  const normalizedValue = value == null ? null : String(value);
  let attributeState = uiAttributeCache.get(element);
  if (!attributeState) {
    attributeState = new Map();
    uiAttributeCache.set(element, attributeState);
  }
  const previousValue = attributeState.get(normalizedName) ?? null;
  if (previousValue === normalizedValue) {
    return false;
  }
  if (normalizedValue === null) {
    element.removeAttribute(normalizedName);
    attributeState.delete(normalizedName);
  } else {
    element.setAttribute(normalizedName, normalizedValue);
    attributeState.set(normalizedName, normalizedValue);
  }
  return true;
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
  const hidden = !isActive;
  if (panel.hidden !== hidden) {
    panel.hidden = hidden;
  }
  const displayValue = isActive ? '' : 'none';
  if (panel.style.display !== displayValue) {
    panel.style.display = displayValue;
  }
  panel.classList.toggle('is-active', isActive);
  setAttributeIfChanged(panel, 'aria-hidden', String(hidden));
  const container = panel.closest('.status-item--crit-aps');
  if (container) {
    if (container.hidden !== hidden) {
      container.hidden = hidden;
    }
    if (container.style.display !== displayValue) {
      container.style.display = displayValue;
    }
    setAttributeIfChanged(container, 'aria-hidden', String(hidden));
  }
  if (elements.statusApsCritSeparator) {
    const separatorHidden = !isActive;
    if (elements.statusApsCritSeparator.hidden !== separatorHidden) {
      elements.statusApsCritSeparator.hidden = separatorHidden;
    }
  }
  const chronoText = isActive ? formatApsCritChrono(remainingSeconds) : '';
  if (elements.statusApsCritChrono) {
    setTextContentIfChanged(elements.statusApsCritChrono, chronoText);
    const chronoHidden = !isActive;
    if (elements.statusApsCritChrono.hidden !== chronoHidden) {
      elements.statusApsCritChrono.hidden = chronoHidden;
    }
    setAttributeIfChanged(elements.statusApsCritChrono, 'aria-hidden', String(chronoHidden));
  }
  const multiplierText = `×${formatNumberLocalized(multiplierValue)}`;
  const multiplierDisplayText = isActive ? multiplierText : '';
  if (elements.statusApsCritMultiplier) {
    setTextContentIfChanged(elements.statusApsCritMultiplier, multiplierDisplayText);
    const multiplierHidden = !isActive;
    if (elements.statusApsCritMultiplier.hidden !== multiplierHidden) {
      elements.statusApsCritMultiplier.hidden = multiplierHidden;
    }
    setAttributeIfChanged(elements.statusApsCritMultiplier, 'aria-hidden', String(multiplierHidden));
  }
  const activeAriaLabel = translateOrDefault(
    'scripts.app.metaux.critAriaActive',
    `Compteur critique APC/APS actif : ${multiplierText} pendant ${chronoText}.`,
    { multiplier: multiplierText, duration: chronoText }
  );
  const inactiveAriaLabel = translateOrDefault(
    'scripts.app.metaux.critAriaInactive',
    'Compteur critique APC/APS inactif.'
  );
  setAttributeIfChanged(panel, 'aria-label', isActive ? activeAriaLabel : inactiveAriaLabel);
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
    const atomsText = gameState.atoms.toString();
    setTextContentIfChanged(elements.statusAtoms, atomsText);
  }
  if (elements.statusApc) {
    const apcText = gameState.perClick.toString();
    setTextContentIfChanged(elements.statusApc, apcText);
  }
  if (elements.statusAps) {
    const apsText = gameState.perSecond.toString();
    setTextContentIfChanged(elements.statusAps, apsText);
  }
  updateApsCritDisplay();
  updateFrenzyIndicators();
  updateApcFrenzyCounterDisplay();
  updateGachaUI();
  updateCollectionDisplay();
  updateFusionUI();
  updateShopAffordability();
  updateMilestone();
  refreshGoalCardTexts();
  updateGoalsUI();
  updateInfoPanels();
  const infoPageActive = typeof document !== 'undefined'
    && document.body?.dataset?.activePage === 'info';
  const shouldRefreshDevkit = infoPageActive || (DEVKIT_STATE && DEVKIT_STATE.isOpen);
  if (shouldRefreshDevkit) {
    updateDevKitUI();
  }
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

function normalizeArcadeProgressKey(key) {
  if (typeof key !== 'string') {
    return '';
  }
  return key.trim().toLowerCase().replace(/[^a-z0-9]/g, '');
}

function resolveArcadeProgressEntrySource(entries, id) {
  if (!entries || typeof entries !== 'object' || !id) {
    return null;
  }
  const direct = entries[id];
  if (direct && typeof direct === 'object') {
    return direct;
  }
  if (typeof id === 'string') {
    const lowerId = id.toLowerCase();
    if (lowerId !== id) {
      const lowerEntry = entries[lowerId];
      if (lowerEntry && typeof lowerEntry === 'object') {
        return lowerEntry;
      }
    }
    const normalizedTarget = normalizeArcadeProgressKey(id);
    if (normalizedTarget) {
      const fallbackKey = Object.keys(entries).find(key => {
        if (typeof key !== 'string' || !entries[key] || typeof entries[key] !== 'object') {
          return false;
        }
        return normalizeArcadeProgressKey(key) === normalizedTarget;
      });
      if (fallbackKey && entries[fallbackKey] && typeof entries[fallbackKey] === 'object') {
        return entries[fallbackKey];
      }
    }
  }
  return null;
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
    const rawEntry = resolveArcadeProgressEntrySource(sourceEntries, id);
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

function getAndroidSaveBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.AndroidSaveBridge;
  if (!bridge) {
    return null;
  }
  const type = typeof bridge;
  if (type === 'object' || type === 'function') {
    return bridge;
  }
  return null;
}

function getAndroidSystemBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.AndroidSystemBridge;
  if (!bridge) {
    return null;
  }
  const type = typeof bridge;
  if (type === 'object' || type === 'function') {
    return bridge;
  }
  return null;
}

function normalizeNativeBridgePayload(raw) {
  if (typeof raw !== 'string' || !raw) {
    return raw;
  }
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') {
      return raw;
    }
    const schema = typeof parsed.schema === 'string' ? parsed.schema.toLowerCase() : '';
    const version = Number(parsed.version);
    const hasClicker = parsed.clicker && typeof parsed.clicker === 'object';
    const isEnvelope = schema === 'atom2univers.save.v2'
      || (Number.isFinite(version) && version >= 2 && hasClicker);
    if (!isEnvelope) {
      return raw;
    }

    const safeClone = value => {
      if (!value || typeof value !== 'object') {
        return undefined;
      }
      try {
        return JSON.parse(JSON.stringify(value));
      } catch (error) {
        return undefined;
      }
    };

    const result = hasClicker ? safeClone(parsed.clicker) || {} : {};

    if (!result.arcadeProgress && parsed.arcadeProgress && typeof parsed.arcadeProgress === 'object') {
      const clonedProgress = safeClone(parsed.arcadeProgress);
      if (clonedProgress) {
        result.arcadeProgress = clonedProgress;
      }
    }

    if (!result.meta && parsed.meta && typeof parsed.meta === 'object') {
      const clonedMeta = safeClone(parsed.meta);
      if (clonedMeta) {
        result.meta = clonedMeta;
      }
    }

    if (result.lastSave == null && parsed.lastSave != null) {
      result.lastSave = parsed.lastSave;
    }

    if (result.updatedAt == null && parsed.updatedAt != null) {
      result.updatedAt = parsed.updatedAt;
    }

    const hasCoreClickerFields = result
      && typeof result === 'object'
      && result.atoms != null
      && result.lifetime != null
      && result.perClick != null
      && result.perSecond != null;

    if (!hasCoreClickerFields) {
      return null;
    }

    try {
      return JSON.stringify(result);
    } catch (error) {
      return raw;
    }
  } catch (error) {
    return raw;
  }
}

function readNativeSaveData() {
  const bridge = getAndroidSaveBridge();
  if (!bridge || typeof bridge.loadData !== 'function') {
    return null;
  }
  try {
    const raw = bridge.loadData();
    if (raw == null) {
      return null;
    }
    const normalized = normalizeNativeBridgePayload(typeof raw === 'string' ? raw : String(raw));
    return typeof normalized === 'string' ? normalized : null;
  } catch (error) {
    console.error('Unable to read native save data', error);
  }
  return null;
}

function writeNativeSaveData(serialized) {
  const bridge = getAndroidSaveBridge();
  if (!bridge) {
    return false;
  }
  if (!serialized) {
    if (typeof bridge.clearData === 'function') {
      try {
        bridge.clearData();
        return true;
      } catch (error) {
        console.error('Unable to clear native save data', error);
        return false;
      }
    }
    if (typeof bridge.saveData === 'function') {
      try {
        bridge.saveData(null);
        return true;
      } catch (error) {
        console.error('Unable to clear native save data', error);
        return false;
      }
    }
    return false;
  }
  if (typeof bridge.saveData === 'function') {
    try {
      bridge.saveData(serialized);
      return true;
    } catch (error) {
      console.error('Unable to write native save data', error);
      return false;
    }
  }
  return false;
}

function serializeState() {
  flushPendingPerformanceQueues({ force: true });
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
    featureUnlockFlags: Array.from(ensureFeatureUnlockFlagSet()),
    bigBangLevelBonus: getBigBangLevelBonus(),
    upgrades: gameState.upgrades,
    shopUnlocks: Array.from(getShopUnlockSet()),
    elements: gameState.elements,
    gachaCards: (() => {
      const source = gameState.gachaCards && typeof gameState.gachaCards === 'object'
        ? gameState.gachaCards
        : {};
      const result = {};
      const knownIds = Array.isArray(GACHA_SPECIAL_CARD_DEFINITIONS)
        ? GACHA_SPECIAL_CARD_DEFINITIONS.map(def => def.id)
        : Object.keys(source);
      knownIds.forEach(cardId => {
        if (!cardId) {
          return;
        }
        const entry = source[cardId];
        const rawCount = Number(entry?.count ?? entry);
        if (Number.isFinite(rawCount) && rawCount > 0) {
          result[cardId] = { count: Math.floor(rawCount) };
        }
      });
      return result;
    })(),
    gachaImages: (() => {
      const source = gameState.gachaImages && typeof gameState.gachaImages === 'object'
        ? gameState.gachaImages
        : {};
      const result = {};
      const knownIds = Array.isArray(GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS)
        ? GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS.map(def => def.id)
        : Object.keys(source);
      knownIds.forEach(imageId => {
        if (!imageId) {
          return;
        }
        const entry = source[imageId];
        const rawCount = Number(entry?.count ?? entry);
        if (Number.isFinite(rawCount) && rawCount > 0) {
          const stored = { count: Math.floor(rawCount) };
          const acquiredOrder = Number(entry?.acquiredOrder);
          if (Number.isFinite(acquiredOrder) && acquiredOrder > 0) {
            stored.acquiredOrder = Math.floor(acquiredOrder);
          }
          const firstAcquiredAt = Number(entry?.firstAcquiredAt);
          if (Number.isFinite(firstAcquiredAt) && firstAcquiredAt > 0) {
            stored.firstAcquiredAt = firstAcquiredAt;
          }
          result[imageId] = stored;
        }
      });
      return result;
    })(),
    collectionVideos: (() => {
      const source = gameState.collectionVideos && typeof gameState.collectionVideos === 'object'
        ? gameState.collectionVideos
        : {};
      const result = {};
      const knownIds = Array.isArray(COLLECTION_VIDEO_DEFINITIONS)
        ? COLLECTION_VIDEO_DEFINITIONS.map(def => def.id)
        : Object.keys(source);
      knownIds.forEach(videoId => {
        if (!videoId) {
          return;
        }
        const entry = source[videoId];
        const rawCount = Number(entry?.count ?? entry);
        if (Number.isFinite(rawCount) && rawCount > 0) {
          const stored = { count: 1 };
          const acquiredOrder = Number(entry?.acquiredOrder);
          if (Number.isFinite(acquiredOrder) && acquiredOrder > 0) {
            stored.acquiredOrder = Math.floor(acquiredOrder);
          }
          const firstAcquiredAt = Number(entry?.firstAcquiredAt);
          if (Number.isFinite(firstAcquiredAt) && firstAcquiredAt > 0) {
            stored.firstAcquiredAt = firstAcquiredAt;
          }
          result[videoId] = stored;
        }
      });
      return result;
    })(),
    gachaImageAcquisitionCounter: (() => {
      const counter = Number(gameState.gachaImageAcquisitionCounter);
      if (!Number.isFinite(counter) || counter <= 0) {
        return 0;
      }
      return Math.max(0, Math.floor(counter));
    })(),
    gachaBonusImages: (() => {
      const source = gameState.gachaBonusImages && typeof gameState.gachaBonusImages === 'object'
        ? gameState.gachaBonusImages
        : {};
      const result = {};
      const knownIdSet = new Set();
      if (Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)) {
        GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS.forEach(def => {
          if (def && def.id) {
            knownIdSet.add(def.id);
          }
        });
      }
      if (Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)) {
        GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS.forEach(def => {
          if (def && def.id) {
            knownIdSet.add(def.id);
          }
        });
      }
      const knownIds = knownIdSet.size > 0 ? Array.from(knownIdSet) : Object.keys(source);
      knownIds.forEach(imageId => {
        if (!imageId) {
          return;
        }
        const entry = source[imageId];
        const rawCount = Number(entry?.count ?? entry);
        if (Number.isFinite(rawCount) && rawCount > 0) {
          const stored = { count: Math.floor(rawCount) };
          const acquiredOrder = Number(entry?.acquiredOrder);
          if (Number.isFinite(acquiredOrder) && acquiredOrder > 0) {
            stored.acquiredOrder = Math.floor(acquiredOrder);
          }
          const firstAcquiredAt = Number(entry?.firstAcquiredAt);
          if (Number.isFinite(firstAcquiredAt) && firstAcquiredAt > 0) {
            stored.firstAcquiredAt = firstAcquiredAt;
          }
          result[imageId] = stored;
        }
      });
      return result;
    })(),
    gachaBonusImageAcquisitionCounter: (() => {
      const counter = Number(gameState.gachaBonusImageAcquisitionCounter);
      if (!Number.isFinite(counter) || counter <= 0) {
        return 0;
      }
      return Math.max(0, Math.floor(counter));
    })(),
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
        info: unlocks?.info === true,
        collection: unlocks?.collection === true
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
      return {
        multiplier: bonus.multiplier,
        maxSeconds: bonus.maxSeconds,
        remainingSeconds: bonus.remainingSeconds,
        grantedAt: bonus.grantedAt,
        expiresAt: bonus.expiresAt,
        level: bonus.level
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
        : 1,
      addPerClick: Number.isFinite(Number(gameState.frenzySpawnBonus?.addPerClick))
        && Number(gameState.frenzySpawnBonus.addPerClick) > 0
        ? Number(gameState.frenzySpawnBonus.addPerClick)
        : 0,
      addPerSecond: Number.isFinite(Number(gameState.frenzySpawnBonus?.addPerSecond))
        && Number(gameState.frenzySpawnBonus.addPerSecond) > 0
        ? Number(gameState.frenzySpawnBonus.addPerSecond)
        : 0
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
  let serialized;
  const previousSerialized = lastSerializedSave;
  try {
    const payload = serializeState();
    serialized = JSON.stringify(payload);
  } catch (err) {
    console.error('Erreur de sauvegarde', err);
    lastSerializedSave = null;
    return false;
  }

  let persisted = false;

  if (typeof localStorage !== 'undefined' && localStorage) {
    try {
      localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, serialized);
      persisted = true;
    } catch (storageError) {
      console.error('Erreur de sauvegarde locale', storageError);
    }
  }

  const nativePersisted = writeNativeSaveData(serialized);
  const success = persisted || nativePersisted;

  if (success) {
    saveBackupManager.recordSuccessfulSave({
      previousSerialized,
      currentSerialized: serialized
    });
    if (lastSerializedSave !== serialized) {
      storeReloadSaveSnapshot(serialized);
    }
    lastSerializedSave = serialized;
  } else {
    lastSerializedSave = null;
  }

  return success;
}

if (typeof window !== 'undefined') {
  window.atom2universSaveGame = saveGame;
  window.saveGame = saveGame;
}

const RESET_LOCAL_STORAGE_KEYS = [
  PRIMARY_SAVE_STORAGE_KEY,
  LANGUAGE_STORAGE_KEY,
  CLICK_SOUND_STORAGE_KEY,
  ATOM_ANIMATION_PREFERENCE_STORAGE_KEY,
  CRIT_ATOM_VISUALS_STORAGE_KEY,
  FRENZY_AUTO_COLLECT_STORAGE_KEY,
  TEXT_FONT_STORAGE_KEY,
  DIGIT_FONT_STORAGE_KEY,
  INFO_WELCOME_COLLAPSED_STORAGE_KEY,
  INFO_CHARACTERS_COLLAPSED_STORAGE_KEY,
  INFO_CARDS_COLLAPSED_STORAGE_KEY,
  INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY,
  INFO_PROGRESS_COLLAPSED_STORAGE_KEY,
  COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY,
  COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY,
  COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY,
  COLLECTION_VIDEOS_STATE_STORAGE_KEY,
  HEADER_COLLAPSED_STORAGE_KEY,
  PERFORMANCE_MODE_STORAGE_KEY,
  UI_SCALE_STORAGE_KEY,
  QUANTUM_2048_STORAGE_KEY,
  BIGGER_STORAGE_KEY,
  ...GAME_OF_LIFE_STORAGE_KEYS
];

function clearArcadeAutosaveData(options = {}) {
  const preservedIds = new Set(
    Array.isArray(options.preserveGameIds)
      ? options.preserveGameIds.filter(id => typeof id === 'string' && id)
      : []
  );

  const globalScope = typeof window !== 'undefined' ? window : null;
  const autosaveApi = globalScope && globalScope.ArcadeAutosave;

  if (autosaveApi) {
    let knownGames = [];
    if (typeof autosaveApi.knownGames === 'function') {
      try {
        knownGames = autosaveApi.knownGames() || [];
      } catch (error) {
        knownGames = [];
      }
    }
    if (!Array.isArray(knownGames) || knownGames.length === 0) {
      if (typeof autosaveApi.list === 'function') {
        try {
          knownGames = Object.keys(autosaveApi.list() || {});
        } catch (error) {
          knownGames = [];
        }
      }
    }

    if (Array.isArray(knownGames) && knownGames.length > 0) {
      knownGames.forEach(gameId => {
        if (!preservedIds.has(gameId)) {
          try {
            if (typeof autosaveApi.clear === 'function') {
              autosaveApi.clear(gameId);
            } else if (typeof autosaveApi.set === 'function') {
              autosaveApi.set(gameId, null);
            }
          } catch (error) {
            // Ignore autosave cleanup errors for individual games.
          }
        }
      });
    }
  }

  const storage = typeof globalThis !== 'undefined' ? globalThis.localStorage : null;
  if (!storage) {
    return;
  }

  if (autosaveApi && typeof autosaveApi.list === 'function') {
    try {
      const entries = autosaveApi.list();
      const filteredEntries = {};
      Object.keys(entries || {}).forEach(id => {
        if (preservedIds.size === 0 || preservedIds.has(id)) {
          const entry = entries[id];
          if (entry && typeof entry === 'object') {
            filteredEntries[id] = entry;
          }
        }
      });
      if (Object.keys(filteredEntries).length > 0) {
        storage.setItem(
          ARCADE_AUTOSAVE_STORAGE_KEY,
          JSON.stringify({ version: 1, entries: filteredEntries })
        );
      } else {
        storage.removeItem(ARCADE_AUTOSAVE_STORAGE_KEY);
      }
      return;
    } catch (error) {
      // Fall back to raw storage filtering if listing fails.
    }
  }

  try {
    const raw = storage.getItem(ARCADE_AUTOSAVE_STORAGE_KEY);
    if (!raw) {
      return;
    }
    const parsed = JSON.parse(raw);
    const sourceEntries =
      parsed && typeof parsed === 'object' && parsed.entries && typeof parsed.entries === 'object'
        ? parsed.entries
        : {};
    const filteredEntries = {};
    Object.keys(sourceEntries).forEach(id => {
      if (preservedIds.size === 0 || preservedIds.has(id)) {
        const entry = sourceEntries[id];
        if (entry != null) {
          filteredEntries[id] = entry;
        }
      }
    });
    if (Object.keys(filteredEntries).length > 0) {
      const version = Number.isFinite(parsed.version) ? parsed.version : 1;
      storage.setItem(
        ARCADE_AUTOSAVE_STORAGE_KEY,
        JSON.stringify({ version, entries: filteredEntries })
      );
    } else {
      storage.removeItem(ARCADE_AUTOSAVE_STORAGE_KEY);
    }
  } catch (error) {
    // Ignore storage filtering issues.
  }
}

function clearLocalStorageForReset() {
  const storage = typeof globalThis !== 'undefined' ? globalThis.localStorage : null;
  if (!storage) {
    clearReloadSaveSnapshot();
    return;
  }

  clearReloadSaveSnapshot();

  RESET_LOCAL_STORAGE_KEYS.forEach(key => {
    if (!key || key === CHESS_LIBRARY_STORAGE_KEY || key === ARCADE_AUTOSAVE_STORAGE_KEY) {
      return;
    }
    try {
      storage.removeItem(key);
    } catch (error) {
      // Ignore cleanup errors for individual keys.
    }
  });
}

function resetGame() {
  clearArcadeAutosaveData({ preserveGameIds: ['echecs'] });
  clearLocalStorageForReset();
  persistCollectionVideoUnlockState(false);
  clearStoredCollectionVideoSnapshot();
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
    gachaCards: createInitialGachaCardCollection(),
    gachaImages: createInitialGachaImageCollection(),
    gachaImageAcquisitionCounter: 0,
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
    bigBangLevelBonus: 0,
    offlineTickets: {
      secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
      capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
      progressSeconds: 0
    },
    sudokuOfflineBonus: null,
    ticketStarAutoCollect: null,
    ticketStarAverageIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
    ticketStarUnlocked: false,
    frenzySpawnBonus: { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 },
    musicTrackId: null,
    musicVolume: DEFAULT_MUSIC_VOLUME,
    musicEnabled: DEFAULT_MUSIC_ENABLED,
    bigBangButtonVisible: false,
    apsCrit: createDefaultApsCritState(),
    arcadeProgress: createInitialArcadeProgress(),
    featureUnlockFlags: new Set()
  });
  applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
  setTicketStarAverageIntervalSeconds(gameState.ticketStarAverageIntervalSeconds);
  resetFrenzyState({ skipApply: true });
  resetTicketStarState({ reschedule: true });
  invalidateFeatureUnlockCache({ resetArcadeState: true });
  applyTheme();
  if (typeof setParticulesBrickSkinPreference === 'function') {
    setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
  }
  musicPlayer.stop();
  musicPlayer.setVolume(DEFAULT_MUSIC_VOLUME, { silent: true });
  recalcProduction();
  renderShop();
  updateUI();
  applyFrenzyAutoCollectEnabled(false, { persist: false });
  updateFrenzyAutoCollectOptionVisibility();
  setFusionLog(
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
  const sudokuBonusState = normalizeSudokuOfflineBonusState(gameState.sudokuOfflineBonus);
  let sudokuBonusAppliedSeconds = 0;
  let sudokuBonusMultiplier = 0;
  let updatedSudokuBonus = sudokuBonusState;
  const now = Date.now();
  if (sudokuBonusState && appliedSeconds > 0) {
    const expiresAt = Number(sudokuBonusState.expiresAt);
    if (Number.isFinite(expiresAt) && now >= expiresAt) {
      updatedSudokuBonus = null;
    } else {
      const offlineEnd = now;
      const offlineStart = offlineEnd - appliedSeconds * 1000;
      const grantedAt = Number(sudokuBonusState.grantedAt) || offlineStart;
      const validStart = Math.max(offlineStart, grantedAt);
      const validEnd = Number.isFinite(expiresAt)
        ? Math.min(offlineEnd, expiresAt)
        : offlineEnd;
      const overlapMs = Math.max(0, validEnd - validStart);
      const overlapSeconds = overlapMs / 1000;
      const rawRemaining = Number(sudokuBonusState.remainingSeconds);
      const rawMax = Number(sudokuBonusState.maxSeconds);
      const maxSeconds = Number.isFinite(rawMax) && rawMax > 0 ? rawMax : 0;
      const remainingSeconds = (() => {
        if (Number.isFinite(rawRemaining) && rawRemaining > 0) {
          return Math.max(0, Math.min(rawRemaining, maxSeconds || rawRemaining));
        }
        return Math.max(0, maxSeconds);
      })();
      const eligibleSeconds = Math.min(overlapSeconds, remainingSeconds, appliedSeconds);
      const multiplierCandidate = Number(sudokuBonusState.multiplier);
      if (eligibleSeconds > 0 && Number.isFinite(multiplierCandidate) && multiplierCandidate > 0) {
        sudokuBonusAppliedSeconds = eligibleSeconds;
        sudokuBonusMultiplier = multiplierCandidate;
        const remainingAfter = Math.max(0, remainingSeconds - sudokuBonusAppliedSeconds);
        if (remainingAfter > 0 && (!Number.isFinite(expiresAt) || now < expiresAt)) {
          updatedSudokuBonus = {
            multiplier: sudokuBonusState.multiplier,
            maxSeconds: sudokuBonusState.maxSeconds,
            remainingSeconds: remainingAfter,
            grantedAt: sudokuBonusState.grantedAt,
            expiresAt: sudokuBonusState.expiresAt,
            level: sudokuBonusState.level
          };
        } else {
          updatedSudokuBonus = null;
        }
      }
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

  if (sudokuBonusState && appliedSeconds > 0) {
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
    gameState.sudokuOfflineBonus = updatedSudokuBonus;
  } else if (sudokuBonusState) {
    gameState.sudokuOfflineBonus = updatedSudokuBonus;
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
    const entry = resolveArcadeProgressEntrySource(sourceEntries, id);
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

function applySerializedGameState(raw) {
  const data = JSON.parse(raw);
  gameState.atoms = LayeredNumber.fromJSON(data.atoms);
  gameState.lifetime = LayeredNumber.fromJSON(data.lifetime);
  gameState.perClick = LayeredNumber.fromJSON(data.perClick);
  gameState.perSecond = LayeredNumber.fromJSON(data.perSecond);
  const tickets = Number(data.gachaTickets);
  const storedTickets = Number.isFinite(tickets) && tickets > 0 ? Math.floor(tickets) : 0;
  setGachaTicketsSilently(storedTickets);
  const bonusTickets = Number(data.bonusParticulesTickets ?? data.bonusTickets);
  gameState.bonusParticulesTickets = Number.isFinite(bonusTickets) && bonusTickets > 0
    ? Math.floor(bonusTickets)
    : 0;
  const storedFeatureFlags = data.featureUnlockFlags ?? data.featureFlags ?? null;
  gameState.featureUnlockFlags = normalizeFeatureUnlockFlags(storedFeatureFlags);
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
  applyDerivedFeatureUnlockFlags();
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
  const storedBigBangBonus = Number(
    data.bigBangLevelBonus
    ?? data.bigBangBonus
    ?? data.bigBangLevels
    ?? data.bigBangLevel
    ?? 0
  );
  setBigBangLevelBonus(storedBigBangBonus);
  const storedBigBangPreference =
    data.bigBangButtonVisible ?? data.showBigBangButton ?? data.bigBangVisible ?? null;
  const wantsBigBang =
    storedBigBangPreference === true
    || storedBigBangPreference === 'true'
    || storedBigBangPreference === 1
    || storedBigBangPreference === '1';
  const hasBigBangUnlock = isBigBangUnlocked();
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
    const addPerClick = Number(storedFrenzyBonus.addPerClick ?? storedFrenzyBonus.perClickAdd);
    const addPerSecond = Number(storedFrenzyBonus.addPerSecond ?? storedFrenzyBonus.perSecondAdd);
    gameState.frenzySpawnBonus = {
      perClick: Number.isFinite(perClick) && perClick > 0 ? perClick : 1,
      perSecond: Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1,
      addPerClick: Number.isFinite(addPerClick) && addPerClick > 0 ? addPerClick : 0,
      addPerSecond: Number.isFinite(addPerSecond) && addPerSecond > 0 ? addPerSecond : 0
    };
  } else {
    gameState.frenzySpawnBonus = { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 };
  }
  applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
  gameState.apsCrit = normalizeApsCritState(data.apsCrit);
  const storedApsCritEffects = data.apsCrit?.effects;
  if (Array.isArray(storedApsCritEffects) && storedApsCritEffects.length > 0) {
    gameState.apsCrit.effects = storedApsCritEffects
      .map(effect => ({
        remainingSeconds: Math.max(0, Number(effect?.remainingSeconds) || 0),
        multiplierAdd: Math.max(0, Number(effect?.multiplierAdd) || 0)
      }))
      .filter(effect => effect.remainingSeconds > 0 && effect.multiplierAdd > 0);
  }
  const storedBigBangState = data.bigBangButtonVisible ?? data.bigBangVisible;
  if (typeof storedBigBangState === 'boolean') {
    gameState.bigBangButtonVisible = storedBigBangState && isBigBangUnlocked();
  }
  const storedElementSummary = data.elementBonusSummary;
  gameState.elementBonusSummary = storedElementSummary && typeof storedElementSummary === 'object'
    ? storedElementSummary
    : {};
  const storedGachaCards = data.gachaCards;
  if (storedGachaCards && typeof storedGachaCards === 'object') {
    const base = createInitialGachaCardCollection();
    Object.keys(base).forEach(id => {
      const stored = storedGachaCards[id];
      if (!stored || typeof stored !== 'object') {
        base[id] = { owned: 0, seen: 0, level: 0 };
        return;
      }
      const owned = Number(stored.owned ?? stored.count ?? stored.total);
      const seen = Number(stored.seen ?? stored.viewed ?? stored.visited);
      const level = Number(stored.level ?? stored.rank);
      base[id] = {
        owned: Number.isFinite(owned) && owned > 0 ? Math.floor(owned) : 0,
        seen: Number.isFinite(seen) && seen > 0 ? Math.floor(seen) : 0,
        level: Number.isFinite(level) && level > 0 ? Math.floor(level) : 0
      };
    });
    gameState.gachaCards = base;
  } else {
    gameState.gachaCards = createInitialGachaCardCollection();
  }
  const storedGachaImages = data.gachaImages;
  const baseImageCollection = createInitialGachaImageCollection();
  let inferredImageAcquisitionCounter = 0;
  if (storedGachaImages && typeof storedGachaImages === 'object') {
    Object.entries(storedGachaImages).forEach(([imageId, stored]) => {
      const reference = baseImageCollection[imageId];
      if (!reference) {
        return;
      }
      const rawCount = Number(stored?.count ?? stored);
      const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
        ? Math.floor(rawCount)
        : 0;
      reference.count = normalizedCount;
      if (normalizedCount > 0) {
        const storedOrder = Number(stored?.acquiredOrder);
        if (Number.isFinite(storedOrder) && storedOrder > 0) {
          reference.acquiredOrder = Math.floor(storedOrder);
          if (reference.acquiredOrder > inferredImageAcquisitionCounter) {
            inferredImageAcquisitionCounter = reference.acquiredOrder;
          }
        }
        const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
        if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
          reference.firstAcquiredAt = storedFirstAcquiredAt;
        }
      }
    });
  }
  gameState.gachaImages = baseImageCollection;
  const storedImageCounter = Number(data.gachaImageAcquisitionCounter);
  if (Number.isFinite(storedImageCounter) && storedImageCounter > inferredImageAcquisitionCounter) {
    gameState.gachaImageAcquisitionCounter = Math.floor(storedImageCounter);
  } else {
    gameState.gachaImageAcquisitionCounter = inferredImageAcquisitionCounter;
  }
  const baseVideoCollection = createInitialCollectionVideoCollection();
  if (data.collectionVideos && typeof data.collectionVideos === 'object') {
    Object.entries(data.collectionVideos).forEach(([videoId, stored]) => {
      const reference = baseVideoCollection[videoId];
      if (!reference) {
        return;
      }
      const rawCount = Number(stored?.count ?? stored);
      const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
        ? 1
        : 0;
      reference.count = normalizedCount;
      if (normalizedCount > 0) {
        const storedOrder = Number(stored?.acquiredOrder);
        if (Number.isFinite(storedOrder) && storedOrder > 0) {
          reference.acquiredOrder = Math.floor(storedOrder);
        }
        const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
        if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
          reference.firstAcquiredAt = storedFirstAcquiredAt;
        }
      }
    });
  }
  mergeStoredCollectionVideoSnapshot(baseVideoCollection);
  gameState.collectionVideos = baseVideoCollection;
  persistCollectionVideoUnlockState(hasOwnedCollectionVideos());
  syncCollectionVideoStateSnapshot({ collection: baseVideoCollection });
  const baseBonusImageCollection = createInitialGachaBonusImageCollection();
  let inferredBonusImageAcquisitionCounter = 0;
  if (data.gachaBonusImages && typeof data.gachaBonusImages === 'object') {
    Object.entries(data.gachaBonusImages).forEach(([imageId, stored]) => {
      const reference = baseBonusImageCollection[imageId];
      if (!reference) {
        return;
      }
      const rawCount = Number(stored?.count ?? stored);
      const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
        ? Math.floor(rawCount)
        : 0;
      reference.count = normalizedCount;
      if (normalizedCount > 0) {
        const storedOrder = Number(stored?.acquiredOrder);
        if (Number.isFinite(storedOrder) && storedOrder > 0) {
          reference.acquiredOrder = Math.floor(storedOrder);
          if (reference.acquiredOrder > inferredBonusImageAcquisitionCounter) {
            inferredBonusImageAcquisitionCounter = reference.acquiredOrder;
          }
        }
        const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
        if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
          reference.firstAcquiredAt = storedFirstAcquiredAt;
        }
      }
    });
  }
  gameState.gachaBonusImages = baseBonusImageCollection;
  const storedBonusCounter = Number(data.gachaBonusImageAcquisitionCounter);
  if (Number.isFinite(storedBonusCounter) && storedBonusCounter > inferredBonusImageAcquisitionCounter) {
    gameState.gachaBonusImageAcquisitionCounter = Math.floor(storedBonusCounter);
  } else {
    gameState.gachaBonusImageAcquisitionCounter = inferredBonusImageAcquisitionCounter;
  }
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
  const parseStoredVolume = rawVolume => {
    const numeric = Number(rawVolume);
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
  invalidateFeatureUnlockCache({ resetArcadeState: true });
  applyTheme();
  recalcProduction();
  renderShop();
  updateBigBangVisibility();
  updateUI();
  if (data.lastSave) {
    const diff = Math.max(0, (Date.now() - data.lastSave) / 1000);
    applyOfflineProgress(diff);
  }
}

function loadGame() {
  try {
    resetFrenzyState({ skipApply: true });
    resetTicketStarState({ reschedule: true });
    gameState.baseCrit = createDefaultCritState();
    gameState.crit = createDefaultCritState();
    gameState.lastCritical = null;
    let raw = null;
    const reloadSnapshot = consumeReloadSaveSnapshot();
    try {
      if (typeof localStorage !== 'undefined' && localStorage) {
        raw = localStorage.getItem(PRIMARY_SAVE_STORAGE_KEY);
      }
    } catch (error) {
      console.error('Erreur de lecture de la sauvegarde locale', error);
    }
    if (!raw) {
      const nativeRaw = readNativeSaveData();
      if (nativeRaw) {
        raw = nativeRaw;
        try {
          if (typeof localStorage !== 'undefined' && localStorage) {
            localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, nativeRaw);
          }
        } catch (syncError) {
          console.warn('Unable to sync native save with local storage', syncError);
        }
      }
    }
    if (!raw && reloadSnapshot) {
      raw = reloadSnapshot;
      try {
        if (typeof localStorage !== 'undefined' && localStorage) {
          localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, reloadSnapshot);
        }
      } catch (syncError) {
        console.warn('Unable to persist reload snapshot to local storage', syncError);
      }
    }
    if (!raw) {
      gameState.theme = DEFAULT_THEME_ID;
      gameState.stats = createInitialStats();
      gameState.shopUnlocks = new Set();
      invalidateFeatureUnlockCache({ resetArcadeState: true });
      applyTheme();
      recalcProduction();
      renderShop();
      updateUI();
      return;
    }
    const data = JSON.parse(raw);
    gameState.atoms = LayeredNumber.fromJSON(data.atoms);
    gameState.lifetime = LayeredNumber.fromJSON(data.lifetime);
    gameState.perClick = LayeredNumber.fromJSON(data.perClick);
    gameState.perSecond = LayeredNumber.fromJSON(data.perSecond);
    const tickets = Number(data.gachaTickets);
    const storedTickets = Number.isFinite(tickets) && tickets > 0 ? Math.floor(tickets) : 0;
    setGachaTicketsSilently(storedTickets);
    const bonusTickets = Number(data.bonusParticulesTickets ?? data.bonusTickets);
    gameState.bonusParticulesTickets = Number.isFinite(bonusTickets) && bonusTickets > 0
      ? Math.floor(bonusTickets)
      : 0;
    const storedFeatureFlags = data.featureUnlockFlags ?? data.featureFlags ?? null;
    gameState.featureUnlockFlags = normalizeFeatureUnlockFlags(storedFeatureFlags);
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
    applyDerivedFeatureUnlockFlags();
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
    const storedBigBangBonus = Number(
      data.bigBangLevelBonus
      ?? data.bigBangBonus
      ?? data.bigBangLevels
      ?? data.bigBangLevel
      ?? 0
    );
    setBigBangLevelBonus(storedBigBangBonus);
    const storedBigBangPreference =
      data.bigBangButtonVisible ?? data.showBigBangButton ?? data.bigBangVisible ?? null;
    const wantsBigBang =
      storedBigBangPreference === true
      || storedBigBangPreference === 'true'
      || storedBigBangPreference === 1
      || storedBigBangPreference === '1';
    const hasBigBangUnlock = isBigBangUnlocked();
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
      const addPerClick = Number(storedFrenzyBonus.addPerClick ?? storedFrenzyBonus.perClickAdd);
      const addPerSecond = Number(storedFrenzyBonus.addPerSecond ?? storedFrenzyBonus.perSecondAdd);
      gameState.frenzySpawnBonus = {
        perClick: Number.isFinite(perClick) && perClick > 0 ? perClick : 1,
        perSecond: Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1,
        addPerClick: Number.isFinite(addPerClick) && addPerClick > 0 ? addPerClick : 0,
        addPerSecond: Number.isFinite(addPerSecond) && addPerSecond > 0 ? addPerSecond : 0
      };
    } else {
      gameState.frenzySpawnBonus = { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 };
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
          effects: Array.isArray(reference?.effects) ? [...reference.effects] : [],
          bonuses: Array.isArray(reference?.bonuses) ? [...reference.bonuses] : []
        };
      });
    }
    gameState.elements = baseCollection;
    const baseCardCollection = createInitialGachaCardCollection();
    if (data.gachaCards && typeof data.gachaCards === 'object') {
      Object.entries(data.gachaCards).forEach(([cardId, stored]) => {
        if (!baseCardCollection[cardId]) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        baseCardCollection[cardId].count = Number.isFinite(rawCount) && rawCount > 0
          ? Math.floor(rawCount)
          : 0;
      });
    }
    gameState.gachaCards = baseCardCollection;
    const baseImageCollection = createInitialGachaImageCollection();
    let inferredImageAcquisitionCounter = 0;
    if (data.gachaImages && typeof data.gachaImages === 'object') {
      Object.entries(data.gachaImages).forEach(([imageId, stored]) => {
        const reference = baseImageCollection[imageId];
        if (!reference) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
          ? Math.floor(rawCount)
          : 0;
        reference.count = normalizedCount;
        if (normalizedCount > 0) {
          const storedOrder = Number(stored?.acquiredOrder);
          if (Number.isFinite(storedOrder) && storedOrder > 0) {
            reference.acquiredOrder = Math.floor(storedOrder);
            if (reference.acquiredOrder > inferredImageAcquisitionCounter) {
              inferredImageAcquisitionCounter = reference.acquiredOrder;
            }
          }
          const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
          if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
            reference.firstAcquiredAt = storedFirstAcquiredAt;
          }
        }
      });
    }
    gameState.gachaImages = baseImageCollection;
    const storedImageCounter = Number(data.gachaImageAcquisitionCounter);
    if (Number.isFinite(storedImageCounter) && storedImageCounter > inferredImageAcquisitionCounter) {
      gameState.gachaImageAcquisitionCounter = Math.floor(storedImageCounter);
    } else {
      gameState.gachaImageAcquisitionCounter = inferredImageAcquisitionCounter;
    }
    const baseVideoCollection = createInitialCollectionVideoCollection();
    if (data.collectionVideos && typeof data.collectionVideos === 'object') {
      Object.entries(data.collectionVideos).forEach(([videoId, stored]) => {
        const reference = baseVideoCollection[videoId];
        if (!reference) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
          ? 1
          : 0;
        reference.count = normalizedCount;
        if (normalizedCount > 0) {
          const storedOrder = Number(stored?.acquiredOrder);
          if (Number.isFinite(storedOrder) && storedOrder > 0) {
            reference.acquiredOrder = Math.floor(storedOrder);
          }
          const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
          if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
            reference.firstAcquiredAt = storedFirstAcquiredAt;
          }
        }
      });
    }
    mergeStoredCollectionVideoSnapshot(baseVideoCollection);
    gameState.collectionVideos = baseVideoCollection;
    persistCollectionVideoUnlockState(hasOwnedCollectionVideos());
    syncCollectionVideoStateSnapshot({ collection: baseVideoCollection });
    const baseBonusImageCollection = createInitialGachaBonusImageCollection();
    let inferredBonusImageAcquisitionCounter = 0;
    if (data.gachaBonusImages && typeof data.gachaBonusImages === 'object') {
      Object.entries(data.gachaBonusImages).forEach(([imageId, stored]) => {
        const reference = baseBonusImageCollection[imageId];
        if (!reference) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
          ? Math.floor(rawCount)
          : 0;
        reference.count = normalizedCount;
        if (normalizedCount > 0) {
          const storedOrder = Number(stored?.acquiredOrder);
          if (Number.isFinite(storedOrder) && storedOrder > 0) {
            reference.acquiredOrder = Math.floor(storedOrder);
            if (reference.acquiredOrder > inferredBonusImageAcquisitionCounter) {
              inferredBonusImageAcquisitionCounter = reference.acquiredOrder;
            }
          }
          const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
          if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
            reference.firstAcquiredAt = storedFirstAcquiredAt;
          }
        }
      });
    }
    gameState.gachaBonusImages = baseBonusImageCollection;
    const storedBonusCounter = Number(data.gachaBonusImageAcquisitionCounter);
    if (Number.isFinite(storedBonusCounter) && storedBonusCounter > inferredBonusImageAcquisitionCounter) {
      gameState.gachaBonusImageAcquisitionCounter = Math.floor(storedBonusCounter);
    } else {
      gameState.gachaBonusImageAcquisitionCounter = inferredBonusImageAcquisitionCounter;
    }
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
    invalidateFeatureUnlockCache({ resetArcadeState: true });
    applyTheme();
    recalcProduction();
    renderShop();
    updateBigBangVisibility();
    updateUI();
    if (data.lastSave) {
      const diff = Math.max(0, (Date.now() - data.lastSave) / 1000);
      applyOfflineProgress(diff);
    }
  } catch (err) {
    console.error('Erreur de chargement', err);
    if (attemptRestoreFromBackup()) {
      return;
    }
    resetGame();
  }
}

function attemptRestoreFromBackup() {
  try {
    const entries = saveBackupManager.list();
    if (!Array.isArray(entries) || entries.length === 0) {
      return false;
    }
    for (let index = 0; index < entries.length; index += 1) {
      const entry = entries[index];
      if (!entry || typeof entry.id !== 'string' || !entry.id) {
        continue;
      }
      const serialized = saveBackupManager.load(entry.id);
      if (typeof serialized !== 'string' || !serialized) {
        continue;
      }
      try {
        applySerializedGameState(serialized);
        if (typeof localStorage !== 'undefined' && localStorage) {
          try {
            localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, serialized);
          } catch (storageError) {
            console.warn('Unable to persist restored backup to local storage', storageError);
          }
        }
        writeNativeSaveData(serialized);
        lastSerializedSave = serialized;
        storeReloadSaveSnapshot(serialized);
        const message = translateOrDefault(
          'scripts.app.saves.fallbackRestored',
          'Sauvegarde de secours restaurée.'
        );
        showToast(message);
        return true;
      } catch (error) {
        console.error('Unable to apply backup save', error);
      }
    }
    const failureMessage = translateOrDefault(
      'scripts.app.saves.fallbackFailed',
      'Aucune sauvegarde de secours valide n’a pu être chargée.'
    );
    showToast(failureMessage);
  } catch (error) {
    console.error('Unable to restore from backup', error);
  }
  return false;
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
  gameLoopControl.handle = null;
  gameLoopControl.type = null;
  const delta = Math.max(0, (now - lastUpdate) / 1000);
  lastUpdate = now;

  accumulateAutoProduction(delta);
  flushManualApcGains(now);
  flushPendingAutoGain(now);

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

  scheduleGameLoop();
}

window.addEventListener('beforeunload', saveGame);

function startApp() {
  loadGame();
  if (isMusicModuleEnabled()) {
    musicPlayer.init({
      preferredTrackId: gameState.musicTrackId,
      autoplay: gameState.musicEnabled !== false,
      volume: gameState.musicVolume
    });
    musicModuleInitRequested = true;
    musicPlayer.ready().then(() => {
      refreshMusicControls();
    });
  } else {
    if (typeof musicPlayer.stop === 'function') {
      musicPlayer.stop();
    }
    musicModuleInitRequested = false;
    gameState.musicTrackId = null;
    gameState.musicEnabled = false;
    refreshMusicControls();
  }
  recalcProduction();
  evaluateTrophies();
  renderShop();
  renderGoals();
  updateUI();
  applyAtomVariantVisualState();
  randomizeAtomButtonImage();
  initStarfield();
  scheduleAutoUiScaleUpdate({ immediate: true });
  startGameLoop();
  hideStartupOverlay();
}

function safelyStartApp(options = {}) {
  const forceStart = options && options.force === true;

  if (appStartCompleted) {
    return;
  }

  if (appStartAttempted && !forceStart) {
    return;
  }

  if (forceStart && appStartAttempted && !appStartCompleted) {
    console.warn('Retrying application start after failsafe trigger');
  }

  appStartAttempted = true;

  try {
    startApp();
    appStartCompleted = true;
  } catch (error) {
    console.error('Unable to start the application', error);
    hideStartupOverlay({ instant: true });
    appStartAttempted = false;
  }
}

function initializeDomBoundModules() {
  refreshOptionsWelcomeContent();
  subscribeOptionsWelcomeContentUpdates();
  renderThemeOptions();
  updateBigBangVisibility();
  initHeaderBannerToggle();
  bindDomEventListeners();
  updateMusicModuleVisibility();
  initializeHoldemOptionsUI();
  initUiScaleOption();
  initResponsiveAutoScale();
  initTextFontOption();
  initDigitFontOption();
  initPerformanceModeOption();
  initClickSoundOption();
  subscribeClickSoundLanguageUpdates();
  initAtomAnimationOption();
  subscribeAtomAnimationLanguageUpdates();
  initCritAtomOption();
  subscribeCritAtomLanguageUpdates();
  initScreenWakeLockOption();
  subscribeScreenWakeLockLanguageUpdates();
  initFrenzyAutoCollectOption();
  subscribeFrenzyAutoCollectLanguageUpdates();
  initCryptoWidgetOption();
  subscribeCryptoWidgetLanguageUpdates();
  subscribeHeaderBannerLanguageUpdates();
  subscribePerformanceModeLanguageUpdates();
  subscribeInfoWelcomeLanguageUpdates();
  subscribeInfoAchievementsLanguageUpdates();
  subscribeInfoCalculationsLanguageUpdates();
  subscribeInfoProgressLanguageUpdates();
  subscribeInfoCharactersLanguageUpdates();
  subscribeInfoCardsLanguageUpdates();
  subscribeCollectionImagesLanguageUpdates();
  subscribeCollectionVideosLanguageUpdates();
  subscribeCollectionBonusImagesLanguageUpdates();
  subscribeCollectionBonus2ImagesLanguageUpdates();
  subscribeBigBangLanguageUpdates();
  if (typeof subscribeSpecialCardOverlayLanguageUpdates === 'function') {
    subscribeSpecialCardOverlayLanguageUpdates();
  }
  subscribeHoldemOptionsLanguageUpdates();
  updateDevKitUI();
  if (typeof initParticulesGame === 'function') {
    initParticulesGame();
  }
  renderPeriodicTable();
  renderGachaRarityList();
  renderFusionList();
}

function initializeApp() {
  applyStoredArcadeHubCardOrder();
  elements = collectDomElements();
  applyAtomVariantVisualState();
  applyStartupOverlayDuration();
  scheduleStartupOverlayFailsafe();
  if (!visibilityChangeListenerAttached) {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    visibilityChangeListenerAttached = true;
  }
  applyActivePageScrollBehavior(document.querySelector('.page.active'));
  initializeDomBoundModules();
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
        safelyStartApp();
      });
    return;
  }
  updateLanguageSelectorValue(getInitialLanguagePreference());
  safelyStartApp();
}

function bootApplication() {
  try {
    initializeApp();
  } catch (error) {
    console.error('Unable to initialize the application', error);
    hideStartupOverlay({ instant: true });
  }
}

armStartupOverlayFallback({ reset: true });

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bootApplication, { once: true });
} else {
  bootApplication();
}


