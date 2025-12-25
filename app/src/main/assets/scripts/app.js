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
    jumpingCat: 'arcade.jumpingCat',
    reflex: 'arcade.reflex',
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
const METAUX_GLOBAL_CONFIG =
  GLOBAL_CONFIG && typeof GLOBAL_CONFIG === 'object' && typeof GLOBAL_CONFIG.metaux === 'object'
    ? GLOBAL_CONFIG.metaux
    : {};
const METAUX_GLOBAL_CRIT_BONUS_CONFIG =
  METAUX_GLOBAL_CONFIG && typeof METAUX_GLOBAL_CONFIG.critBonus === 'object'
    ? METAUX_GLOBAL_CONFIG.critBonus
    : null;
const DEFAULT_METAUX_CRIT_MAX_MULTIPLIER = 100;
const METAUX_CRIT_MAX_MULTIPLIER = (() => {
  const raw = METAUX_GLOBAL_CRIT_BONUS_CONFIG
    ? METAUX_GLOBAL_CRIT_BONUS_CONFIG.maxMultiplier
        ?? METAUX_GLOBAL_CRIT_BONUS_CONFIG.max
        ?? METAUX_GLOBAL_CRIT_BONUS_CONFIG.cap
    : null;
  const rawValue = Number(raw);
  if (Number.isFinite(rawValue) && rawValue >= 1) {
    return rawValue;
  }
  const fallback = METAUX_GLOBAL_CONFIG && typeof METAUX_GLOBAL_CONFIG === 'object'
    ? METAUX_GLOBAL_CONFIG.critBonusMaxMultiplier ?? METAUX_GLOBAL_CONFIG.critBonusCap
    : null;
  const fallbackValue = Number(fallback);
  if (Number.isFinite(fallbackValue) && fallbackValue >= 1) {
    return fallbackValue;
  }
  return DEFAULT_METAUX_CRIT_MAX_MULTIPLIER;
})();
const METAUX_CRIT_MAX_BONUS = Math.max(0, METAUX_CRIT_MAX_MULTIPLIER - 1);

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
let newsFeatureEnabled = true;
let newsBannedWords = [];
let newsItems = [];
let newsRawItems = [];
let newsHiddenIds = new Map();
let newsCurrentQuery = '';
let newsEnabledSources = null;
let newsRefreshTimerId = null;
let newsFetchAbortController = null;
let newsIsLoading = false;
let newsLastError = null;
let newsAndroidResponseResolver = null;
let newsAndroidTimeoutId = null;
let newsTickerEntries = [];
let newsTickerCurrentIndex = 0;
let newsTickerTimerId = null;
let newsTickerActiveStoryId = null;
let newsHighlightedStoryId = null;

let radioStations = [];
let radioFavorites = new Map();
let radioSelectedStation = null;
let radioSearchAbortController = null;
let radioIsLoading = false;
let radioCountries = [];
let radioLanguages = [];
let radioFiltersPromise = null;
let radioAudioElement = null;
let radioIsRecording = false;
let radioStatusState = {
  key: 'index.sections.radio.status.idle',
  fallback: 'Entrez une requête pour démarrer une recherche.',
  params: null
};
let radioPlayerStatusState = {
  key: 'index.sections.radio.player.status.idle',
  fallback: 'Sélectionnez une station pour lancer la lecture.'
};
let radioLastError = null;
let radioNowPlayingInfo = '';

let notesItems = [];
let notesEditingNote = null;
let notesStylePreference = null;
let notesBusy = false;
let notesIsFullscreen = false;
let notesStatusState = {
  key: 'index.sections.notes.status.idle',
  fallback: 'Prêt à charger vos notes.',
  params: null
};

let imageFeedItems = [];
let imageFeedVisibleItems = [];
let imageFeedDismissedIds = new Set();
let imageFeedEnabledSources = null;
let imageFeedCurrentIndex = 0;
let imageFeedIsLoading = false;
let imageFeedLastError = null;
let imageFeedAbortController = null;
let imageBackgroundEnabled = false;
let backgroundIndex = 0;
let backgroundTimerId = null;
let backgroundLoadTimerId = null;
let lastLoadedBackgroundUrl = '';
let localBackgroundItems = [];
let backgroundRotationQueue = [];
let backgroundRotationExclusions = new Set();
let backgroundRotationPoolSize = 0;
let backgroundLibraryLabel = '';
let backgroundLibraryStatus = 'idle';
let backgroundRotationMs = readStoredBackgroundDuration();
let collectionDownloadsEntries = [];
let collectionDownloadsCurrentIndex = 0;
let collectionDownloadsTouchStartX = null;
const imageSizeAllowanceCache = new Map();
let imageAssetCache = new Map();
const imageAssetDownloads = new Map();
let imageThumbnailQueue = [];
let imageThumbnailWorkerActive = false;

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
const MUSIC_PAGE_STORAGE_KEY = 'atom2univers.music.lastPage';
const CLICK_SOUND_STORAGE_KEY = 'atom2univers.options.clickSoundMuted';
const CRIT_ATOM_VISUALS_STORAGE_KEY = 'atom2univers.options.critAtomVisualsDisabled';
const ATOM_ANIMATION_PREFERENCE_STORAGE_KEY = 'atom2univers.options.atomAnimationsEnabled';
const ATOM_ICON_VISIBILITY_STORAGE_KEY = 'atom2univers.options.atomIconVisible';
const NOTES_STYLE_STORAGE_KEY = 'atom2univers.notes.editorStyle';
const NOTE_SUPPORTED_FORMATS = Object.freeze(['txt', 'md']);
const NOTES_DEFAULT_STYLE = Object.freeze({
  font: 'monospace',
  fontSize: 16,
  textColor: '#f5f5f5',
  backgroundColor: '#0b1021'
});
const FRENZY_AUTO_COLLECT_STORAGE_KEY = 'atom2univers.options.frenzyAutoCollectEnabled';
const TICKET_STAR_AUTO_COLLECT_STORAGE_KEY = 'atom2univers.options.ticketStarAutoCollectEnabled';
const TICKET_STAR_SPRITE_STORAGE_KEY = 'atom2univers.options.ticketStarSprite';
const NEWS_FEATURE_ENABLED_STORAGE_KEY = 'atom2univers.options.newsEnabled';
const NEWS_HIDDEN_ITEMS_STORAGE_KEY = 'atom2univers.news.hiddenItems.v1';
const NEWS_LAST_QUERY_STORAGE_KEY = 'atom2univers.news.lastQuery';
const NEWS_BANNED_WORDS_STORAGE_KEY = 'atom2univers.news.bannedWords.v1';
const NEWS_SOURCES_STORAGE_KEY = 'atom2univers.news.sources.v1';
const NEWS_HIDDEN_ENTRY_TTL_MS = 72 * 60 * 60 * 1000;
const IMAGE_FEED_SOURCES_STORAGE_KEY = 'atom2univers.images.sources.v1';
const IMAGE_FEED_LAST_INDEX_STORAGE_KEY = 'atom2univers.images.lastIndex';
const IMAGE_FEED_BACKGROUND_ENABLED_STORAGE_KEY = 'atom2univers.images.background.enabled';
const IMAGE_FEED_DISMISSED_STORAGE_KEY = 'atom2univers.images.dismissed.v1';
const SCREEN_WAKE_LOCK_STORAGE_KEY = 'atom2univers.options.screenWakeLockEnabled';
const ANDROID_STATUS_BAR_STORAGE_KEY = 'atom2univers.options.androidStatusBarVisible';
const TEXT_FONT_STORAGE_KEY = 'atom2univers.options.textFont';
const ATOM_DECIMAL_STORAGE_KEY = 'atom2univers.options.atomCounterDecimals';
const INFO_WELCOME_COLLAPSED_STORAGE_KEY = 'atom2univers.info.welcomeCollapsed';
const INFO_ACHIEVEMENTS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.achievementsCollapsed';
const INFO_CHARACTERS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.charactersCollapsed';
const INFO_CARDS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.cardsCollapsed';
const INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.calculationsCollapsed';
const INFO_PROGRESS_COLLAPSED_STORAGE_KEY = 'atom2univers.info.progressCollapsed';
const INFO_SCORES_COLLAPSED_STORAGE_KEY = 'atom2univers.info.scoresCollapsed';
const COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.imagesCollapsed';
const COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.bonusImagesCollapsed';
const COLLECTION_BONUS1_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.bonus1ImagesCollapsed';
const COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.bonus2ImagesCollapsed';
const COLLECTION_DOWNLOADS_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.downloadsCollapsed';
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
const UI_UPDATE_INTERVALS = CONFIG?.ui?.updateIntervals || {};
const UI_FAST_UPDATE_INTERVAL_MS = (() => {
  const raw = Number(UI_UPDATE_INTERVALS.fastMs);
  if (Number.isFinite(raw) && raw > 0) {
    return Math.floor(raw);
  }
  return 250;
})();
const UI_SLOW_UPDATE_INTERVAL_MS = (() => {
  const raw = Number(UI_UPDATE_INTERVALS.slowMs);
  if (Number.isFinite(raw) && raw > 0) {
    return Math.floor(raw);
  }
  return 1000;
})();
const UI_SLOW_UPDATE_INTERVAL_LIMIT_MS = Math.max(
  UI_FAST_UPDATE_INTERVAL_MS,
  UI_SLOW_UPDATE_INTERVAL_MS
);
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
const TEXT_SCALE_STORAGE_KEY = 'atom2univers.options.textScale';

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
let autoUiScaleReferenceViewport = { width: 0, height: 0 };

const AUTO_UI_SCALE_MIN_FACTOR = 0.7;
const AUTO_UI_SCALE_TOLERANCE = 0.015;
const AUTO_UI_SCALE_SAFE_PADDING = 24;
const AUTO_UI_SCALE_OVERFLOW_TOLERANCE = 12;
const AUTO_UI_SCALE_VERTICAL_PADDING = 32;
const AUTO_UI_SCALE_HEIGHT_TOLERANCE = 32;
const AUTO_UI_SCALE_VIEWPORT_TOLERANCE = 18;

const TEXT_SCALE_CONFIG = (() => {
  const fallbackChoices = {
    compact: Object.freeze({ id: 'compact', factor: 0.9 }),
    normal: Object.freeze({ id: 'normal', factor: 1 }),
    comfortable: Object.freeze({ id: 'comfortable', factor: 1.1 }),
    reading: Object.freeze({ id: 'reading', factor: 1.25 })
  };
  const fallback = {
    defaultId: 'normal',
    choices: Object.freeze(fallbackChoices)
  };

  const rawConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.ui && GLOBAL_CONFIG.ui.textScale;
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

const TEXT_SCALE_CHOICES = TEXT_SCALE_CONFIG.choices;
const TEXT_SCALE_DEFAULT = TEXT_SCALE_CONFIG.defaultId;
const TEXT_SCALE_MIN_FACTOR = 0.7;
const TEXT_SCALE_MAX_FACTOR = 2.5;

const DEFAULT_TEXT_SCALE_FACTOR = (() => {
  const config = TEXT_SCALE_CHOICES?.[TEXT_SCALE_DEFAULT];
  const factor = Number(config?.factor);
  return Number.isFinite(factor) && factor > 0 ? factor : 1;
})();

let currentTextScaleSelection = TEXT_SCALE_DEFAULT;
let currentTextScaleFactor = DEFAULT_TEXT_SCALE_FACTOR;

const ATOM_DECIMAL_CONFIG = (() => {
  const fallbackOptions = [1, 2, 3, 4];
  const fallback = {
    defaultValue: 2,
    options: Object.freeze(fallbackOptions)
  };

  const rawConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.ui && GLOBAL_CONFIG.ui.atomCounterDecimals;
  if (!rawConfig || !Array.isArray(rawConfig.options)) {
    return fallback;
  }

  const normalizedOptions = [];
  rawConfig.options.forEach(option => {
    const numeric = Number(option);
    if (!Number.isFinite(numeric)) {
      return;
    }
    const rounded = Math.round(numeric);
    if (rounded <= 0 || normalizedOptions.includes(rounded)) {
      return;
    }
    normalizedOptions.push(rounded);
  });

  if (!normalizedOptions.length) {
    return fallback;
  }

  let defaultValue = fallback.defaultValue;
  const rawDefault = Number(rawConfig.default);
  if (Number.isFinite(rawDefault)) {
    const roundedDefault = Math.round(rawDefault);
    if (normalizedOptions.includes(roundedDefault)) {
      defaultValue = roundedDefault;
    }
  }
  if (!normalizedOptions.includes(defaultValue)) {
    defaultValue = normalizedOptions[0];
  }

  return {
    defaultValue,
    options: Object.freeze(normalizedOptions)
  };
})();

const ATOM_DECIMAL_OPTIONS = ATOM_DECIMAL_CONFIG.options;
const ATOM_DECIMAL_DEFAULT = ATOM_DECIMAL_CONFIG.defaultValue;
let currentAtomDecimalSelection = ATOM_DECIMAL_DEFAULT;

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
    'arcade.jumpingCat',
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

function normalizeUnlockedTrophies(raw) {
  const trophies = new Set();
  if (raw == null) {
    return trophies;
  }
  const addTrophyId = (value) => {
    if (typeof value !== 'string') {
      return;
    }
    const id = value.trim();
    if (id) {
      trophies.add(id);
    }
  };

  if (raw instanceof Set) {
    raw.forEach(addTrophyId);
    return trophies;
  }

  if (Array.isArray(raw)) {
    raw.forEach(entry => {
      if (typeof entry === 'string') {
        addTrophyId(entry);
        return;
      }
      if (entry && typeof entry === 'object') {
        addTrophyId(entry.id ?? entry.trophyId);
      }
    });
    return trophies;
  }

  if (raw && typeof raw === 'object') {
    Object.keys(raw).forEach(addTrophyId);
  }

  return trophies;
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
  const pageHref = card.dataset?.pageHref;
  if (pageHref) {
    window.location.href = pageHref;
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

function readStoredAtomIconVisibility() {
  try {
    const stored = globalThis.localStorage?.getItem(ATOM_ICON_VISIBILITY_STORAGE_KEY);
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read atom icon preference', error);
  }
  return null;
}

function writeStoredAtomIconVisibility(visible) {
  try {
    const value = visible ? '1' : '0';
    globalThis.localStorage?.setItem(ATOM_ICON_VISIBILITY_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist atom icon preference', error);
  }
}

function readStoredMusicPagePreference() {
  try {
    const stored = globalThis.localStorage?.getItem(MUSIC_PAGE_STORAGE_KEY);
    if (stored === 'radio' || stored === 'midi') {
      return stored;
    }
  } catch (error) {
    console.warn('Unable to read stored music page preference', error);
  }
  return null;
}

function writeStoredMusicPagePreference(pageId) {
  try {
    if (pageId === 'radio' || pageId === 'midi') {
      globalThis.localStorage?.setItem(MUSIC_PAGE_STORAGE_KEY, pageId);
    }
  } catch (error) {
    console.warn('Unable to persist music page preference', error);
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
    bigBangCount: 0,
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
        bigBang: LayeredNumber.one(),
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
    bigBangCount: Number.isFinite(entry.bigBangCount) ? entry.bigBangCount : 0,
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
        bigBang: entry.sources?.multipliers?.bigBang instanceof LayeredNumber
          ? entry.sources.multipliers.bigBang.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.bigBang ?? 1),
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
  ticketStarAutoCollectEnabled: false,
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


const DEFAULT_TICKET_STAR_SPRITE_ID = (() => {
  const raw = CONFIG?.ticketStar?.sprite?.defaultSprite
    ?? CONFIG?.ticketStar?.sprite?.defaultSpriteId
    ?? CONFIG?.ticketStar?.defaultSprite;
  if (typeof raw === 'string') {
    const normalized = raw.trim().toLowerCase();
    if (normalized === 'animated' || normalized === 'gif' || normalized === 'anim') {
      return 'animated';
    }
  }
  return 'static';
})();

const DEFAULT_TICKET_STAR_SPECIAL_REWARD = (() => {
  const raw = Number(CONFIG?.ticketStar?.specialStar?.rewardTickets ?? CONFIG?.ticketStar?.specialStar?.tickets ?? 10);
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 10;
})();

function clampTicketStarSpecialChance(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 0;
  }
  if (numeric <= 0) {
    return 0;
  }
  if (numeric > 1) {
    return Math.min(1, numeric / 100);
  }
  return Math.min(1, numeric);
}

function normalizeTicketStarSpecialReward(value, fallback = DEFAULT_TICKET_STAR_SPECIAL_REWARD) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.floor(numeric);
  }
  const fallbackNumeric = Number(fallback);
  if (Number.isFinite(fallbackNumeric) && fallbackNumeric > 0) {
    return Math.floor(fallbackNumeric);
  }
  return 1;
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
  ticketStarSpriteId: DEFAULT_TICKET_STAR_SPRITE_ID,
  ticketStarSpecialChance: 0,
  ticketStarSpecialReward: DEFAULT_TICKET_STAR_SPECIAL_REWARD,
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
    type === 'bonusCollection'
    || type === 'bonusImages'
    || type === 'bonusCollectionComplete'
  ) {
    const collection = normalizeBonusCollectionId(
      raw.collection ?? raw.target ?? raw.id ?? raw.collectionId
    );
    return {
      type: 'bonusCollection',
      collection: collection || 'primary'
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
  if (
    type === 'manualClicks'
    || type === 'manualClick'
    || type === 'clicks'
    || type === 'manual'
  ) {
    const amount = Number(raw.amount ?? raw.value ?? 0);
    return {
      type: 'manualClicks',
      amount: Number.isFinite(amount) && amount > 0 ? Math.floor(amount) : 0
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
      ticketStarAutoCollect: null,
      ticketStarSpecialChance: 0,
      ticketStarSpecialReward: DEFAULT_TICKET_STAR_SPECIAL_REWARD
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
  const specialChance = clampTicketStarSpecialChance(
    raw.ticketStarSpecialChance
      ?? raw.specialTicketStarChance
      ?? raw.specialTicketChance
      ?? raw.specialChance
  );
  const specialReward = normalizeTicketStarSpecialReward(
    raw.ticketStarSpecialReward
      ?? raw.ticketStarSpecialRewardTickets
      ?? raw.ticketStarSpecialTickets
      ?? raw.specialTicketStarReward
      ?? raw.specialTicketReward
      ?? raw.specialRewardTickets
  );
  return {
    multiplier,
    frenzyMaxStacks,
    description,
    trophyMultiplierAdd,
    ticketStarAutoCollect,
    ticketStarSpecialChance: specialChance,
    ticketStarSpecialReward: specialReward
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
const FRENZY_AUTO_COLLECT_TROPHY_ID = 'frenzyCollector';
const TICKET_STAR_AUTO_COLLECT_TROPHY_ID = 'ticketHarvester';
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
  const bonusEntries = Object.entries(gameState.gachaBonusImages || {});
  if (bonusEntries.some(([, entry]) => {
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  })) {
    return true;
  }
  const definitions = new Map([
    ...(Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)
      ? GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => [def.id, def])
      : []),
    ...(Array.isArray(GACHA_INTERMEDIATE_PERMANENT_BONUS_IMAGE_DEFINITIONS)
      ? GACHA_INTERMEDIATE_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => [def.id, def])
      : []),
    ...(Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)
      ? GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => [def.id, def])
      : [])
  ]);
  return Object.entries(gameState.gachaImages || {}).some(([imageId, entry]) => {
    if (!definitions.has(imageId)) {
      return false;
    }
    const count = Number(entry?.count ?? entry);
    return Number.isFinite(count) && count > 0;
  });
}

function normalizeBonusCollectionId(value) {
  const normalized = typeof value === 'string' ? value.trim().toLowerCase() : '';
  if (normalized === 'bonus2' || normalized === 'tertiary' || normalized === 'permanent2') {
    return 'tertiary';
  }
  if (normalized === 'bonus1' || normalized === 'secondary' || normalized === 'permanent1') {
    return 'secondary';
  }
  if (normalized === 'bonus'
    || normalized === 'primary' || normalized === 'permanent'
  ) {
    return 'primary';
  }
  return '';
}

function getBonusImageCollectionTotals(collectionId = 'primary') {
  const normalized = normalizeBonusCollectionId(collectionId) || 'primary';
  let definitions = GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS;
  if (normalized === 'secondary') {
    definitions = GACHA_INTERMEDIATE_PERMANENT_BONUS_IMAGE_DEFINITIONS;
  } else if (normalized === 'tertiary') {
    definitions = GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS;
  }
  const collection = gameState.gachaBonusImages && typeof gameState.gachaBonusImages === 'object'
    ? gameState.gachaBonusImages
    : {};
  const legacyImages = gameState.gachaImages && typeof gameState.gachaImages === 'object'
    ? gameState.gachaImages
    : {};
  let total = 0;
  let owned = 0;
  if (Array.isArray(definitions)) {
    definitions.forEach(def => {
      if (!def || !def.id) {
        return;
      }
      total += 1;
      const entry = collection[def.id];
      const count = Number(entry?.count ?? entry);
      const legacyEntry = legacyImages[def.id];
      const legacyCount = Number(legacyEntry?.count ?? legacyEntry);
      if ((Number.isFinite(count) && count > 0)
        || (Number.isFinite(legacyCount) && legacyCount > 0)) {
        owned += 1;
      }
    });
  }
  return { total, owned };
}

function isBonusImageCollectionComplete(collectionId = 'primary') {
  const { total, owned } = getBonusImageCollectionTotals(collectionId);
  return total > 0 && owned >= total;
}

function isSecondaryBonusImageCollectionUnlocked() {
  return isBonusImageCollectionComplete('primary');
}

function isTertiaryBonusImageCollectionUnlocked() {
  return isBonusImageCollectionComplete('secondary');
}

function hasOwnedGachaBonus1Images() {
  const { owned } = getBonusImageCollectionTotals('secondary');
  return owned > 0;
}

function hasOwnedGachaBonus2Images() {
  const { owned } = getBonusImageCollectionTotals('tertiary');
  return owned > 0;
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
  gameState.trophies = normalizeUnlockedTrophies(gameState.trophies);
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

function getTotalManualClicks() {
  const stats = gameState.stats?.global;
  if (!stats) return 0;
  const total = Number(stats.manualClicks ?? 0);
  return Number.isFinite(total) ? Math.max(0, Math.floor(total)) : 0;
}

function computeTrophyEffects() {
  const unlocked = getUnlockedTrophySet();
  let clickMultiplier = LayeredNumber.one();
  let autoMultiplier = LayeredNumber.one();
  let maxStacks = FRENZY_CONFIG.baseMaxStacks;
  const critAccumulator = createCritAccumulator();
  let trophyMultiplierBonus = 0;
  let ticketStarAutoCollect = null;
  let ticketStarSpecialChance = 0;
  let ticketStarSpecialReward = DEFAULT_TICKET_STAR_SPECIAL_REWARD;

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
    if (Number.isFinite(reward?.ticketStarSpecialChance)) {
      ticketStarSpecialChance = Math.max(
        ticketStarSpecialChance,
        clampTicketStarSpecialChance(reward.ticketStarSpecialChance)
      );
    }
    if (reward?.ticketStarSpecialReward != null) {
      ticketStarSpecialReward = Math.max(
        ticketStarSpecialReward,
        normalizeTicketStarSpecialReward(reward.ticketStarSpecialReward)
      );
    }
  });

  if (trophyMultiplierBonus > 0) {
    const trophyMultiplierValue = toMultiplierLayered(1 + trophyMultiplierBonus);
    clickMultiplier = clickMultiplier.multiply(trophyMultiplierValue);
    autoMultiplier = autoMultiplier.multiply(trophyMultiplierValue);
  }

  const critEffect = finalizeCritEffect(critAccumulator);

  return {
    clickMultiplier,
    autoMultiplier,
    maxStacks,
    critEffect,
    ticketStarAutoCollect,
    ticketStarSpecialChance,
    ticketStarSpecialReward
  };
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
  if (condition.type === 'bonusCollection') {
    const { total, owned } = getBonusImageCollectionTotals(condition.collection);
    const percent = total > 0 ? Math.max(0, Math.min(1, owned / total)) : 0;
    return {
      current: owned,
      target: total,
      percent,
      displayCurrent: formatIntegerLocalized(owned),
      displayTarget: formatIntegerLocalized(total)
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
  if (condition.type === 'manualClicks') {
    const current = getTotalManualClicks();
    const target = Math.max(1, Number(condition.amount ?? 0));
    const percent = Math.max(0, Math.min(1, current / target));
    return {
      current,
      target,
      percent,
      displayCurrent: formatIntegerLocalized(current),
      displayTarget: formatIntegerLocalized(target)
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
  if (condition.type === 'bonusCollection') {
    return isBonusImageCollectionComplete(condition.collection);
  }
  if (condition.type === 'fusionSuccesses') {
    const fusionIds = Array.isArray(condition.fusions) ? condition.fusions : [];
    if (!fusionIds.length) {
      return false;
    }
    return fusionIds.every(fusionId => getFusionSuccessCount(fusionId) > 0);
  }
  if (condition.type === 'manualClicks') {
    const target = Math.max(1, Number(condition.amount ?? 0));
    return getTotalManualClicks() >= target;
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
  updateTicketStarAutoCollectOptionVisibility();
  updateTicketStarSpriteOptionVisibility();
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
let atomIconVisiblePreference = true;
let frenzyAutoCollectPreference = false;
let lastFrenzyAutoCollectUnlockedState = null;
let ticketStarAutoCollectPreference = false;
let lastTicketStarAutoCollectUnlockedState = null;
let ticketStarSpritePreference = DEFAULT_TICKET_STAR_SPRITE_ID;
const androidStatusBarState = {
  supported: false,
  visible: false
};
const screenWakeLockState = {
  supported: false,
  enabled: false
};
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
    return { factor: 1, viewportWidth: 0, viewportHeight: 0 };
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
    return { factor: 1, viewportWidth, viewportHeight };
  }
  return {
    factor: Math.max(AUTO_UI_SCALE_MIN_FACTOR, Math.min(1, finalScale)),
    viewportWidth,
    viewportHeight
  };
}

function recalculateAutoUiScaleFactor() {
  pendingAutoUiScaleFrame = null;
  autoUiScaleFrameUsesTimeout = false;
  const activePage = getActivePageElement();
  const { factor: newFactor, viewportWidth, viewportHeight } = computeAutoUiScaleForPage(activePage) || {};
  const effectiveViewportWidth = Number.isFinite(viewportWidth) ? viewportWidth : 0;
  const effectiveViewportHeight = Number.isFinite(viewportHeight) ? viewportHeight : 0;
  if (!Number.isFinite(newFactor) || newFactor <= 0) {
    if (autoUiScaleFactor !== 1) {
      autoUiScaleFactor = 1;
      updateEffectiveUiScaleFactor();
    }
    return;
  }
  if (!autoUiScaleReferenceViewport.width && effectiveViewportWidth > 0) {
    autoUiScaleReferenceViewport.width = effectiveViewportWidth;
  }
  if (!autoUiScaleReferenceViewport.height && effectiveViewportHeight > 0) {
    autoUiScaleReferenceViewport.height = effectiveViewportHeight;
  }
  const wantsDecrease = newFactor + AUTO_UI_SCALE_TOLERANCE < autoUiScaleFactor;
  const wantsIncrease = newFactor - AUTO_UI_SCALE_TOLERANCE > autoUiScaleFactor;
  if (!wantsDecrease && !wantsIncrease) {
    return;
  }
  if (wantsDecrease) {
    autoUiScaleFactor = newFactor;
    autoUiScaleReferenceViewport = {
      width: effectiveViewportWidth || autoUiScaleReferenceViewport.width,
      height: effectiveViewportHeight || autoUiScaleReferenceViewport.height
    };
    updateEffectiveUiScaleFactor();
    return;
  }
  const viewportGrewEnough =
    effectiveViewportWidth > autoUiScaleReferenceViewport.width + AUTO_UI_SCALE_VIEWPORT_TOLERANCE ||
    effectiveViewportHeight > autoUiScaleReferenceViewport.height + AUTO_UI_SCALE_VIEWPORT_TOLERANCE;
  if (!viewportGrewEnough) {
    return;
  }
  autoUiScaleFactor = newFactor;
  autoUiScaleReferenceViewport = {
    width: Math.max(autoUiScaleReferenceViewport.width, effectiveViewportWidth),
    height: Math.max(autoUiScaleReferenceViewport.height, effectiveViewportHeight)
  };
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

function clampTextScaleFactor(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.max(TEXT_SCALE_MIN_FACTOR, Math.min(TEXT_SCALE_MAX_FACTOR, value));
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 1;
  }
  return Math.max(TEXT_SCALE_MIN_FACTOR, Math.min(TEXT_SCALE_MAX_FACTOR, numeric));
}

function normalizeTextScaleSelection(value) {
  if (typeof value !== 'string') {
    return TEXT_SCALE_DEFAULT;
  }
  const normalized = value.trim().toLowerCase();
  return Object.prototype.hasOwnProperty.call(TEXT_SCALE_CHOICES, normalized)
    ? normalized
    : TEXT_SCALE_DEFAULT;
}

function readStoredTextScale() {
  try {
    const stored = globalThis.localStorage?.getItem(TEXT_SCALE_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeTextScaleSelection(stored);
    }
  } catch (error) {
    console.warn('Unable to read text scale preference', error);
  }
  return null;
}

function writeStoredTextScale(value) {
  try {
    const normalized = normalizeTextScaleSelection(value);
    globalThis.localStorage?.setItem(TEXT_SCALE_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist text scale preference', error);
  }
}

function updateTextScaleCssVariable(factor) {
  if (typeof document === 'undefined') {
    return;
  }
  const root = document.documentElement;
  if (!root || !root.style) {
    return;
  }
  const clamped = clampTextScaleFactor(factor);
  root.style.setProperty('--font-text-user-scale', String(clamped));
  return clamped;
}

function formatTextScaleMultiplier(factor) {
  const clamped = clampTextScaleFactor(factor);
  const maximumFractionDigits = clamped >= 2 || Number.isInteger(clamped) ? 0 : 2;
  try {
    const localized = clamped.toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits
    });
    return `×${localized}`;
  } catch (error) {
    const raw = clamped.toFixed(maximumFractionDigits || 0).replace(/\\.0+$/, '');
    return `×${raw}`;
  }
}

function getTextScaleFallbackLabel(option) {
  const multiplier = formatTextScaleMultiplier(option?.factor ?? 1);
  switch (option?.id) {
    case 'compact':
      return `Compact ${multiplier}`;
    case 'comfortable':
      return `Comfort ${multiplier}`;
    case 'reading':
      return `Reading ${multiplier}`;
    default:
      return `Standard ${multiplier}`;
  }
}

function renderTextScaleOptions() {
  if (!elements.textScaleSelect) {
    return;
  }
  const select = elements.textScaleSelect;
  const previousSelection = select.value || currentTextScaleSelection || TEXT_SCALE_DEFAULT;
  select.innerHTML = '';
  Object.values(TEXT_SCALE_CHOICES).forEach(option => {
    const optionElement = document.createElement('option');
    optionElement.value = option.id;
    optionElement.setAttribute('data-i18n', `index.sections.options.textScale.options.${option.id}`);
    optionElement.textContent = translateOrDefault(
      `index.sections.options.textScale.options.${option.id}`,
      getTextScaleFallbackLabel(option)
    );
    select.appendChild(optionElement);
  });
  const i18n = getI18nApi();
  if (i18n && typeof i18n.updateTranslations === 'function') {
    i18n.updateTranslations(select);
  }
  const normalized = normalizeTextScaleSelection(previousSelection);
  if (select.value !== normalized) {
    select.value = normalized;
  }
}

function applyTextScaleSelection(selection, options = {}) {
  const normalized = normalizeTextScaleSelection(selection);
  const config = TEXT_SCALE_CHOICES[normalized] || TEXT_SCALE_CHOICES[TEXT_SCALE_DEFAULT];
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  const factor = clampTextScaleFactor(config?.factor);
  currentTextScaleSelection = normalized;
  currentTextScaleFactor = factor;
  updateTextScaleCssVariable(factor);
  if (typeof document !== 'undefined' && document.body) {
    document.body.setAttribute('data-text-scale', normalized);
  }
  if (settings.updateControl && elements.textScaleSelect) {
    elements.textScaleSelect.value = normalized;
  }
  if (settings.persist) {
    writeStoredTextScale(normalized);
  }
  return normalized;
}

function initTextScaleOption() {
  renderTextScaleOptions();
  const stored = readStoredTextScale();
  const initial = stored ?? TEXT_SCALE_DEFAULT;
  applyTextScaleSelection(initial, { persist: false, updateControl: true });
  if (elements.textScaleSelect) {
    elements.textScaleSelect.addEventListener('change', event => {
      const value = event?.target?.value;
      applyTextScaleSelection(value, { persist: true, updateControl: false });
    });
  }
  if (elements.textScaleResetButton) {
    elements.textScaleResetButton.addEventListener('click', () => {
      applyTextScaleSelection(TEXT_SCALE_DEFAULT, { persist: true, updateControl: true });
    });
  }
}

function subscribeTextScaleLanguageUpdates() {
  const api = getI18nApi();
  const refresh = () => {
    renderTextScaleOptions();
    const i18n = getI18nApi();
    if (elements.textScaleResetButton) {
      if (i18n && typeof i18n.updateTranslations === 'function') {
        i18n.updateTranslations(elements.textScaleResetButton);
      } else {
        elements.textScaleResetButton.textContent = translateOrDefault(
          'index.sections.options.textScale.reset',
          'Réinitialiser la taille du texte'
        );
      }
    }
  };
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(refresh);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', refresh);
  }
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
    const scale = resolveFontScaleMultiplier(FONT_SCALE_CONFIG.text, normalized);
    root.style.setProperty('--font-text-font-scale', String(scale));
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
    const scale = resolveFontScaleMultiplier(FONT_SCALE_CONFIG.digits, normalized);
    root.style.setProperty('--font-digits-scale-factor', String(scale));
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

function normalizeAtomDecimalSelection(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return ATOM_DECIMAL_DEFAULT;
  }
  const rounded = Math.round(numeric);
  return ATOM_DECIMAL_OPTIONS.includes(rounded)
    ? rounded
    : ATOM_DECIMAL_DEFAULT;
}

function readStoredAtomDecimal() {
  try {
    const stored = globalThis.localStorage?.getItem(ATOM_DECIMAL_STORAGE_KEY);
    if (typeof stored === 'string' && stored.trim()) {
      return normalizeAtomDecimalSelection(stored);
    }
  } catch (error) {
    console.warn('Unable to read atom decimal preference', error);
  }
  return null;
}

function writeStoredAtomDecimal(value) {
  try {
    const normalized = normalizeAtomDecimalSelection(value);
    globalThis.localStorage?.setItem(ATOM_DECIMAL_STORAGE_KEY, String(normalized));
  } catch (error) {
    console.warn('Unable to persist atom decimal preference', error);
  }
}

function applyAtomDecimalSelection(selection, options = {}) {
  const normalized = normalizeAtomDecimalSelection(selection);
  const settings = Object.assign({ persist: true, updateControl: true, refreshUi: true }, options);
  currentAtomDecimalSelection = normalized;
  LayeredNumber.MANTISSA_FRACTION_DIGITS = normalized;
  if (settings.updateControl && elements.atomDecimalSelect) {
    elements.atomDecimalSelect.value = String(normalized);
  }
  if (settings.persist) {
    writeStoredAtomDecimal(normalized);
  }
  if (settings.refreshUi) {
    updateUI();
  }
  return normalized;
}

function initAtomDecimalOption() {
  const stored = readStoredAtomDecimal();
  const initial = stored ?? ATOM_DECIMAL_DEFAULT;
  applyAtomDecimalSelection(initial, { persist: false, updateControl: true, refreshUi: false });
  if (!elements.atomDecimalSelect) {
    return;
  }
  elements.atomDecimalSelect.addEventListener('change', event => {
    const value = event?.target?.value;
    applyAtomDecimalSelection(value, { persist: true, updateControl: false, refreshUi: true });
  });
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

function readStoredAndroidStatusBarVisible() {
  try {
    const stored = globalThis.localStorage?.getItem(ANDROID_STATUS_BAR_STORAGE_KEY);
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
    console.warn('Unable to read Android status bar preference', error);
  }
  return null;
}

function writeStoredAndroidStatusBarVisible(visible) {
  try {
    const value = visible ? '1' : '0';
    globalThis.localStorage?.setItem(ANDROID_STATUS_BAR_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist Android status bar preference', error);
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

function detectAndroidStatusBarSupport() {
  const bridge = getAndroidSystemBridge();
  const supported = !!(bridge && typeof bridge.setStatusBarVisible === 'function');
  androidStatusBarState.supported = supported;
  if (elements.androidStatusBarToggleCard) {
    if (supported) {
      elements.androidStatusBarToggleCard.removeAttribute('hidden');
      elements.androidStatusBarToggleCard.removeAttribute('aria-hidden');
    } else {
      elements.androidStatusBarToggleCard.setAttribute('hidden', '');
      elements.androidStatusBarToggleCard.setAttribute('aria-hidden', 'true');
    }
  }
  if (elements.androidStatusBarToggle) {
    elements.androidStatusBarToggle.disabled = !supported;
  }
  return supported;
}

function updateAndroidStatusBarStatusLabel(visible) {
  if (!elements.androidStatusBarToggleStatus) {
    return;
  }
  const key = visible
    ? 'index.sections.options.androidStatusBar.state.on'
    : 'index.sections.options.androidStatusBar.state.off';
  const fallback = visible ? 'Visible' : 'Hidden';
  elements.androidStatusBarToggleStatus.setAttribute('data-i18n', key);
  elements.androidStatusBarToggleStatus.textContent = translateOrDefault(key, fallback);
}

function setNativeAndroidStatusBarVisible(visible) {
  const bridge = getAndroidSystemBridge();
  if (!bridge || typeof bridge.setStatusBarVisible !== 'function') {
    return false;
  }
  try {
    const result = bridge.setStatusBarVisible(!!visible);
    if (typeof result === 'boolean') {
      return result;
    }
    return !!visible;
  } catch (error) {
    console.warn('Unable to update Android status bar visibility', error);
  }
  return false;
}

function applyAndroidStatusBarVisible(visible, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  let applied = false;
  if (androidStatusBarState.supported) {
    applied = setNativeAndroidStatusBarVisible(!!visible);
  }
  androidStatusBarState.visible = applied;
  if (settings.updateControl && elements.androidStatusBarToggle) {
    elements.androidStatusBarToggle.checked = applied;
  }
  updateAndroidStatusBarStatusLabel(applied);
  if (settings.persist) {
    writeStoredAndroidStatusBarVisible(applied);
  }
  return applied;
}

function initAndroidStatusBarOption() {
  if (!elements.androidStatusBarToggleCard) {
    return;
  }
  detectAndroidStatusBarSupport();
  updateAndroidStatusBarStatusLabel(false);
  if (!androidStatusBarState.supported) {
    if (elements.androidStatusBarToggle) {
      elements.androidStatusBarToggle.checked = false;
    }
    return;
  }
  const stored = readStoredAndroidStatusBarVisible();
  const initialVisible = stored === true;
  applyAndroidStatusBarVisible(initialVisible, { persist: false, updateControl: true });
  if (!elements.androidStatusBarToggle) {
    return;
  }
  elements.androidStatusBarToggle.addEventListener('change', () => {
    const requested = elements.androidStatusBarToggle.checked;
    const applied = applyAndroidStatusBarVisible(requested, { persist: true, updateControl: false });
    if (requested !== applied) {
      elements.androidStatusBarToggle.checked = applied;
    }
  });
}

function subscribeAndroidStatusBarLanguageUpdates() {
  const handler = () => {
    updateAndroidStatusBarStatusLabel(androidStatusBarState.visible);
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

function updateAtomIconStatusLabel(visible) {
  if (!elements.atomIconToggleStatus) {
    return;
  }
  const key = visible
    ? 'index.sections.options.atomIcon.state.on'
    : 'index.sections.options.atomIcon.state.off';
  const fallback = visible ? 'Visible' : 'Hidden';
  elements.atomIconToggleStatus.setAttribute('data-i18n', key);
  elements.atomIconToggleStatus.textContent = translateOrDefault(key, fallback);
}

function updateAtomIconVisibility() {
  const image = elements.atomImage;
  if (!image) {
    return;
  }
  const visible = atomIconVisiblePreference !== false;
  image.style.visibility = visible ? '' : 'hidden';
  image.setAttribute('aria-hidden', visible ? 'false' : 'true');
}

function applyAtomIconVisibilityPreference(visible, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  atomIconVisiblePreference = visible !== false;
  if (settings.updateControl && elements.atomIconToggle) {
    elements.atomIconToggle.checked = atomIconVisiblePreference;
  }
  updateAtomIconStatusLabel(atomIconVisiblePreference);
  updateAtomIconVisibility();
  if (settings.persist) {
    writeStoredAtomIconVisibility(atomIconVisiblePreference);
  }
}

function initAtomIconOption() {
  const stored = readStoredAtomIconVisibility();
  const initialVisibility = stored === null ? true : stored === true;
  applyAtomIconVisibilityPreference(initialVisibility, { persist: false, updateControl: true });
  if (elements.atomIconToggle) {
    elements.atomIconToggle.addEventListener('change', () => {
      const visible = elements.atomIconToggle.checked;
      applyAtomIconVisibilityPreference(visible, { persist: true, updateControl: false });
    });
  }
}

function subscribeAtomIconLanguageUpdates() {
  const handler = () => {
    updateAtomIconStatusLabel(atomIconVisiblePreference);
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
  const storedCritPreference = readStoredCritAtomVisualsDisabled();
  const storedAtomPreference = readStoredAtomAnimationsEnabled();
  let animationsEnabled = true;
  if (storedCritPreference === true) {
    animationsEnabled = false;
  } else if (storedAtomPreference !== null) {
    animationsEnabled = storedAtomPreference === true;
  } else if (storedCritPreference === false) {
    animationsEnabled = true;
  }
  const initialDisabled = !animationsEnabled;
  applyAtomAnimationPreference(animationsEnabled, {
    persist: true,
    updateControl: false
  });
  applyCritAtomVisualsDisabled(initialDisabled, {
    persist: true,
    updateControl: Boolean(elements.critAtomToggle)
  });
  if (!elements.critAtomToggle) {
    return;
  }
  elements.critAtomToggle.addEventListener('change', () => {
    const disabled = !elements.critAtomToggle.checked;
    applyCritAtomVisualsDisabled(disabled, { persist: true, updateControl: false });
    applyAtomAnimationPreference(!disabled, { persist: true, updateControl: false });
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

function readStoredTicketStarAutoCollectEnabled() {
  try {
    const stored = globalThis.localStorage?.getItem(TICKET_STAR_AUTO_COLLECT_STORAGE_KEY);
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
    console.warn('Unable to read ticket star auto-collect preference', error);
  }
  return null;
}

function writeStoredTicketStarAutoCollectEnabled(enabled) {
  try {
    const value = enabled ? '1' : '0';
    globalThis.localStorage?.setItem(TICKET_STAR_AUTO_COLLECT_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist ticket star auto-collect preference', error);
  }
}

function resolveTicketStarAutoCollectConfig() {
  if (gameState.ticketStarAutoCollect) {
    return gameState.ticketStarAutoCollect;
  }
  const def = TROPHY_MAP.get(TICKET_STAR_AUTO_COLLECT_TROPHY_ID);
  return normalizeTicketStarAutoCollectConfig(def?.reward?.ticketStarAutoCollect);
}

function isTicketStarAutoCollectFeatureUnlocked() {
  const unlockedTrophies = getUnlockedTrophySet();
  const storedUnlock = gameState.ticketStarAutoCollectEnabled === true
    || gameState.ticketStarAutoCollect != null;
  if (!unlockedTrophies.has(TICKET_STAR_AUTO_COLLECT_TROPHY_ID) && !storedUnlock) {
    return false;
  }
  const config = resolveTicketStarAutoCollectConfig();
  if (config && !gameState.ticketStarAutoCollect) {
    gameState.ticketStarAutoCollect = config;
  }
  if (config && storedUnlock && !unlockedTrophies.has(TICKET_STAR_AUTO_COLLECT_TROPHY_ID)) {
    unlockedTrophies.add(TICKET_STAR_AUTO_COLLECT_TROPHY_ID);
  }
  return !!config;
}

function isTicketStarAutoCollectActive() {
  return ticketStarAutoCollectPreference && isTicketStarAutoCollectFeatureUnlocked();
}

function updateTicketStarAutoCollectStatusLabel(active) {
  if (!elements.ticketStarAutoCollectStatus) {
    return;
  }
  const key = active
    ? 'index.sections.options.ticketStarAutoCollect.state.on'
    : 'index.sections.options.ticketStarAutoCollect.state.off';
  const fallback = active ? 'Enabled' : 'Disabled';
  elements.ticketStarAutoCollectStatus.setAttribute('data-i18n', key);
  elements.ticketStarAutoCollectStatus.textContent = translateOrDefault(key, fallback);
}

function applyTicketStarAutoCollectEnabled(enabled, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  ticketStarAutoCollectPreference = !!enabled;
  gameState.ticketStarAutoCollectEnabled = ticketStarAutoCollectPreference
    && isTicketStarAutoCollectFeatureUnlocked();
  if (settings.updateControl && elements.ticketStarAutoCollectToggle) {
    elements.ticketStarAutoCollectToggle.checked = ticketStarAutoCollectPreference;
  }
  const active = isTicketStarAutoCollectActive();
  updateTicketStarAutoCollectStatusLabel(active);
  if (settings.persist) {
    writeStoredTicketStarAutoCollectEnabled(ticketStarAutoCollectPreference);
  }
}

function initTicketStarAutoCollectOption() {
  const stored = readStoredTicketStarAutoCollectEnabled();
  const initialPreference = stored === null ? false : stored === true;
  applyTicketStarAutoCollectEnabled(initialPreference, { persist: false, updateControl: true });
  if (elements.ticketStarAutoCollectToggle) {
    elements.ticketStarAutoCollectToggle.addEventListener('change', () => {
      const enabled = elements.ticketStarAutoCollectToggle.checked;
      applyTicketStarAutoCollectEnabled(enabled, { persist: true, updateControl: false });
    });
  }
  updateTicketStarAutoCollectOptionVisibility();
}

function subscribeTicketStarAutoCollectLanguageUpdates() {
  const handler = () => {
    updateTicketStarAutoCollectStatusLabel(isTicketStarAutoCollectActive());
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

function updateTicketStarAutoCollectOptionVisibility() {
  const unlocked = isTicketStarAutoCollectFeatureUnlocked();
  if (elements.ticketStarAutoCollectCard) {
    elements.ticketStarAutoCollectCard.hidden = !unlocked;
    elements.ticketStarAutoCollectCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }
  if (elements.ticketStarAutoCollectToggle) {
    elements.ticketStarAutoCollectToggle.disabled = !unlocked;
  }
  if (lastTicketStarAutoCollectUnlockedState !== unlocked) {
    lastTicketStarAutoCollectUnlockedState = unlocked;
    applyTicketStarAutoCollectEnabled(ticketStarAutoCollectPreference, { persist: false, updateControl: true });
  } else {
    updateTicketStarAutoCollectStatusLabel(isTicketStarAutoCollectActive());
  }
}

function normalizeTicketStarSpritePreference(value) {
  if (typeof value !== 'string') {
    return DEFAULT_TICKET_STAR_SPRITE_ID;
  }
  const normalized = value.trim().toLowerCase();
  if (normalized === 'animated' || normalized === 'gif' || normalized === 'anim') {
    return 'animated';
  }
  return 'static';
}

function getTicketStarSpritePreference() {
  return ticketStarSpritePreference || DEFAULT_TICKET_STAR_SPRITE_ID;
}

function readStoredTicketStarSpritePreference() {
  try {
    const stored = globalThis.localStorage?.getItem(TICKET_STAR_SPRITE_STORAGE_KEY);
    if (typeof stored === 'string' && stored.length > 0) {
      return normalizeTicketStarSpritePreference(stored);
    }
  } catch (error) {
    console.warn('Unable to read ticket star sprite preference', error);
  }
  return null;
}

function writeStoredTicketStarSpritePreference(preferenceId) {
  try {
    const normalized = normalizeTicketStarSpritePreference(preferenceId);
    globalThis.localStorage?.setItem(TICKET_STAR_SPRITE_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist ticket star sprite preference', error);
  }
}

function updateTicketStarSpriteStatusLabel(animated) {
  if (!elements.ticketStarSpriteStatus) {
    return;
  }
  const key = animated
    ? 'index.sections.options.ticketStarSprite.state.animated'
    : 'index.sections.options.ticketStarSprite.state.static';
  const fallback = animated ? 'Animated' : 'Static';
  elements.ticketStarSpriteStatus.setAttribute('data-i18n', key);
  elements.ticketStarSpriteStatus.textContent = translateOrDefault(key, fallback);
}

function applyTicketStarSpritePreference(preferenceId, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true, refresh: true }, options);
  const normalized = normalizeTicketStarSpritePreference(preferenceId);
  ticketStarSpritePreference = normalized;
  gameState.ticketStarSpriteId = normalized;
  if (settings.updateControl && elements.ticketStarSpriteToggle) {
    elements.ticketStarSpriteToggle.checked = normalized === 'animated';
  }
  updateTicketStarSpriteStatusLabel(normalized === 'animated');
  if (settings.persist) {
    writeStoredTicketStarSpritePreference(normalized);
  }
  if (settings.refresh && typeof refreshTicketStarSprite === 'function') {
    try {
      refreshTicketStarSprite(normalized);
    } catch (error) {
      console.warn('Unable to refresh ticket star sprite', error);
    }
  }
}

function initTicketStarSpriteOption() {
  const stored = readStoredTicketStarSpritePreference();
  const initial = stored ?? DEFAULT_TICKET_STAR_SPRITE_ID;
  applyTicketStarSpritePreference(initial, { persist: false, updateControl: true, refresh: true });
  if (elements.ticketStarSpriteToggle) {
    elements.ticketStarSpriteToggle.addEventListener('change', () => {
      const animated = elements.ticketStarSpriteToggle.checked;
      const preferenceId = animated ? 'animated' : 'static';
      applyTicketStarSpritePreference(preferenceId, { persist: true, updateControl: false, refresh: true });
    });
  }
  updateTicketStarSpriteOptionVisibility();
}

function subscribeTicketStarSpriteLanguageUpdates() {
  const handler = () => {
    updateTicketStarSpriteStatusLabel(getTicketStarSpritePreference() === 'animated');
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

function updateTicketStarSpriteOptionVisibility() {
  if (elements.ticketStarSpriteCard) {
    elements.ticketStarSpriteCard.hidden = false;
    elements.ticketStarSpriteCard.setAttribute('aria-hidden', 'false');
  }
  if (elements.ticketStarSpriteToggle) {
    elements.ticketStarSpriteToggle.disabled = false;
    elements.ticketStarSpriteToggle.setAttribute('aria-disabled', 'false');
  }
  updateTicketStarSpriteStatusLabel(getTicketStarSpritePreference() === 'animated');
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

function updateDevkitButtonVisibility() {
  if (!elements.openDevkitButton) {
    return;
  }
  const enabled = isDevkitFeatureEnabled();
  elements.openDevkitButton.hidden = !enabled;
  elements.openDevkitButton.style.display = enabled ? '' : 'none';
  elements.openDevkitButton.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  if (!enabled && DEVKIT_STATE.isOpen) {
    closeDevKit();
  }
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
  const defaultDurationUnits = {
    daysPerWeek: 7,
    daysPerMonth: 30,
    daysPerYear: 365
  };
  const durationUnits = GLOBAL_CONFIG?.progression?.durationUnits || {};
  const daysPerWeek = Number(durationUnits.daysPerWeek);
  const daysPerMonth = Number(durationUnits.daysPerMonth);
  const daysPerYear = Number(durationUnits.daysPerYear);
  const normalizedDaysPerWeek = Number.isFinite(daysPerWeek) && daysPerWeek > 0
    ? daysPerWeek
    : defaultDurationUnits.daysPerWeek;
  const normalizedDaysPerMonth = Number.isFinite(daysPerMonth) && daysPerMonth > 0
    ? daysPerMonth
    : defaultDurationUnits.daysPerMonth;
  const normalizedDaysPerYear = Number.isFinite(daysPerYear) && daysPerYear > 0
    ? daysPerYear
    : defaultDurationUnits.daysPerYear;
  const units = {
    year: translateOrDefault('scripts.app.duration.units.year', 'y'),
    month: translateOrDefault('scripts.app.duration.units.month', 'mo'),
    week: translateOrDefault('scripts.app.duration.units.week', 'w'),
    day: translateOrDefault('scripts.app.duration.units.day', 'd'),
    hour: translateOrDefault('scripts.app.duration.units.hour', 'h'),
    minute: translateOrDefault('scripts.app.duration.units.minute', 'm'),
    second: translateOrDefault('scripts.app.duration.units.second', 's')
  };
  if (!Number.isFinite(ms) || ms <= 0) {
    return `0${units.second}`;
  }
  const totalSeconds = Math.floor(ms / 1000);
  const seconds = totalSeconds % 60;
  const totalMinutes = Math.floor(totalSeconds / 60);
  const minutes = totalMinutes % 60;
  const totalHours = Math.floor(totalMinutes / 60);
  const hours = totalHours % 24;
  const totalDays = Math.floor(totalHours / 24);
  const years = Math.floor(totalDays / normalizedDaysPerYear);
  const remainingAfterYears = totalDays % normalizedDaysPerYear;
  const months = Math.floor(remainingAfterYears / normalizedDaysPerMonth);
  const remainingAfterMonths = remainingAfterYears % normalizedDaysPerMonth;
  const weeks = Math.floor(remainingAfterMonths / normalizedDaysPerWeek);
  const days = remainingAfterMonths % normalizedDaysPerWeek;
  const parts = [];
  if (years > 0) {
    parts.push(`${years}${units.year}`);
  }
  if (months > 0) {
    parts.push(`${months}${units.month}`);
  }
  if (weeks > 0) {
    parts.push(`${weeks}${units.week}`);
  }
  if (days > 0) {
    parts.push(`${days}${units.day}`);
  }
  const hasLargerUnit = years > 0 || months > 0 || weeks > 0 || days > 0;
  if (hours > 0 || hasLargerUnit) {
    const hourStr = hours.toString().padStart(hasLargerUnit ? 2 : 1, '0');
    parts.push(`${hourStr}${units.hour}`);
  }
  const minuteStr = minutes.toString().padStart(hours > 0 || hasLargerUnit ? 2 : 1, '0');
  parts.push(`${minuteStr}${units.minute}`);
  parts.push(`${seconds.toString().padStart(2, '0')}${units.second}`);
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
    const rarityId =
      entry?.rarity
      || definition.rarity
      || definition.rarete
      || elementRarityIndex.get(definition.id);
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

function computeRarityMultiplierProduct(store) {
  if (!store) return LayeredNumber.one();
  const accumulate = raw => {
    const numeric = Number(raw);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return 0;
    }
    return numeric - 1;
  };
  let bonusTotal = 0;
  if (store instanceof Map) {
    store.forEach(raw => {
      bonusTotal += accumulate(raw);
    });
  } else if (typeof store === 'object' && store !== null) {
    Object.values(store).forEach(raw => {
      bonusTotal += accumulate(raw);
    });
  }
  const total = 1 + bonusTotal;
  if (!Number.isFinite(total) || total <= 0) {
    return LayeredNumber.one();
  }
  return new LayeredNumber(total);
}

function formatFlatValue(value) {
  const layered = value instanceof LayeredNumber ? value : toLayeredValue(value, 0);
  const normalized = normalizeProductionUnit(layered);
  return normalized.isZero() ? '+0' : `+${normalized.toString()}`;
}

function formatBigBangMultiplierValue(count) {
  const formattedCount = formatIntegerLocalized(Math.max(0, Number(count) || 0));
  return translateOrDefault(
    'scripts.app.production.bigBangMultiplierValue',
    `×2^{${formattedCount}}`,
    { count: formattedCount }
  );
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
      if (step.id === 'bigBangMultiplier') {
        const bigBangCount = entry && Number.isFinite(entry.bigBangCount)
          ? entry.bigBangCount
          : getBigBangCompletionCount();
        return formatBigBangMultiplierValue(bigBangCount);
      }
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
let jumpingCatGame = null;
let reflexGame = null;
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
  const customBase = Number(definition?.bigBangBaseMaxLevel);
  if (Number.isFinite(customBase)) {
    const baseLevel = Math.max(0, Math.floor(customBase));
    const increment = Math.max(0, Math.floor(Number(definition?.bigBangLevelIncrement ?? 0)));
    const completions = getBigBangCompletionCount();
    let maxLevel = baseLevel + (Number.isFinite(completions) ? completions * increment : 0);
    const capRaw = Number(definition?.bigBangMaxLevelCap);
    if (Number.isFinite(capRaw)) {
      const cap = Math.max(0, Math.floor(capRaw));
      maxLevel = Math.min(maxLevel, cap);
    }
    return maxLevel;
  }

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

function calculateCompressedShopBatchCost(startLevel, quantity) {
  const buyAmount = Math.max(1, Math.floor(Number(quantity) || 0));
  const initialLevel = Math.max(0, Math.floor(Number(startLevel) || 0));
  let total = LayeredNumber.zero();
  for (let i = 1; i <= buyAmount; i += 1) {
    const levelCost = getCompressedShopCost(initialLevel + i);
    if (levelCost > 0) {
      total = total.add(new LayeredNumber(levelCost));
    }
  }
  return total;
}

function computeCompressedShopCost(definition, quantity = 1) {
  const level = getUpgradeLevel(gameState.upgrades, definition.id);
  return calculateCompressedShopBatchCost(level, quantity);
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

  if (def.pricingModel === 'compressed') {
    const compressedCost = computeCompressedShopCost(def, buyAmount);
    return modifier !== 1 ? compressedCost.multiplyNumber(modifier) : compressedCost;
  }

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
  if (definition.pricingModel === 'compressed') {
    return calculateCompressedShopBatchCost(0, normalizedLevel);
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
    updateTicketStarSpriteOptionVisibility();
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
  let clickBase = normalizeProductionUnit(BASE_PER_CLICK);
  let autoBase = normalizeProductionUnit(BASE_PER_SECOND);
  const hasFusionSystem = FUSION_DEFS.length > 0;
  let fusionBonusState = null;
  if (hasFusionSystem) {
    fusionBonusState = getFusionBonusState();
    const baseApcBoost = Number(fusionBonusState.apcBaseBoost);
    if (Number.isFinite(baseApcBoost) && baseApcBoost !== 0) {
      clickBase = clickBase.add(new LayeredNumber(baseApcBoost));
    }
    const baseApsBoost = Number(fusionBonusState.apsBaseBoost);
    if (Number.isFinite(baseApsBoost) && baseApsBoost !== 0) {
      autoBase = autoBase.add(new LayeredNumber(baseApsBoost));
    }
  }

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
  gameState.ticketStarSpecialChance = clampTicketStarSpecialChance(trophyEffects.ticketStarSpecialChance);
  gameState.ticketStarSpecialReward = normalizeTicketStarSpecialReward(trophyEffects.ticketStarSpecialReward);

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

  if (hasFusionSystem && fusionBonusState) {
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

  const bigBangCount = getBigBangCompletionCount();
  const bigBangMultiplier = bigBangCount > 0
    ? new LayeredNumber(2).pow(bigBangCount)
    : LayeredNumber.one();
  const hasBigBangMultiplier = !isLayeredOne(bigBangMultiplier);
  const bigBangMultiplierLabel = translateOrDefault(
    'scripts.app.production.bigBangMultiplier',
    'Multiplicateur Big Bang'
  );
  clickDetails.bigBangCount = bigBangCount;
  autoDetails.bigBangCount = bigBangCount;
  clickDetails.sources.multipliers.bigBang = bigBangMultiplier.clone();
  autoDetails.sources.multipliers.bigBang = bigBangMultiplier.clone();
  if (hasBigBangMultiplier) {
    clickDetails.multipliers.push({
      id: 'bigBangMultiplier',
      label: bigBangMultiplierLabel,
      value: bigBangMultiplier.clone(),
      source: 'bigBang'
    });
    autoDetails.multipliers.push({
      id: 'bigBangMultiplier',
      label: bigBangMultiplierLabel,
      value: bigBangMultiplier.clone(),
      source: 'bigBang'
    });
  }
  clickTotalMultiplier = clickTotalMultiplier.multiply(bigBangMultiplier);
  autoTotalMultiplier = autoTotalMultiplier.multiply(bigBangMultiplier);

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
  perClick = perClick.multiply(bigBangMultiplier);
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
  perSecond = perSecond.multiply(bigBangMultiplier);
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

  let shouldHighlight = false;
  if (!button.hidden && !button.disabled) {
    const atoms = toLayeredValue(gameState.atoms, 0);
    const candidateDefs = ['godFinger', 'starCore']
      .map(id => UPGRADE_DEFS[UPGRADE_INDEX_MAP.get(id)])
      .filter(Boolean);
    shouldHighlight = candidateDefs.some(def => {
      const remainingLevels = getRemainingUpgradeCapacity(def);
      if (Number.isFinite(remainingLevels) && remainingLevels <= 0) {
        return false;
      }
      const cost = computeUpgradeCost(def, 1);
      return atoms.compare(cost) >= 0;
    });
  }

  button.classList.toggle('nav-button--shop-ready', shouldHighlight);
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

    const sequentiallyUnlocked = index <= visibleLimit;
    const requiresDuplicates = Array.isArray(def?.duplicateCardCost?.cardIds)
      && def.duplicateCardCost.cardIds.length > 0;
    const hasRequiredCards = !requiresDuplicates
      || hasDuplicateCardResources(def.duplicateCardCost, 1);
    const bypassSequentialLock = requiresDuplicates && hasRequiredCards;
    // Les offres basées sur les doublons peuvent apparaître dès que les cartes nécessaires sont disponibles,
    // même si la progression séquentielle habituelle du magasin n'a pas encore été atteinte.
    const reveal = hasRequiredCards && (sequentiallyUnlocked || bypassSequentialLock);
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
  if (Number.isFinite(reward.ticketStarSpecialChance)) {
    const percent = clampTicketStarSpecialChance(reward.ticketStarSpecialChance) * 100;
    const formattedChance = formatNumberLocalized(percent, {
      minimumFractionDigits: 0,
      maximumFractionDigits: 1
    });
    params.specialChance = formattedChance;
    params.chance = formattedChance;
  }
  if (reward.ticketStarSpecialReward != null) {
    const tickets = formatIntegerLocalized(
      normalizeTicketStarSpecialReward(reward.ticketStarSpecialReward)
    );
    params.specialTickets = tickets;
    params.tickets = tickets;
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

function updateUI(now) {
  const timestamp = Number.isFinite(now) ? now : getLoopTimestamp();
  const shouldUpdateSlow = timestamp - lastSlowUiUpdate > UI_SLOW_UPDATE_INTERVAL_LIMIT_MS;

  if (shouldUpdateSlow) {
    lastSlowUiUpdate = timestamp;
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
    const hasCoreClickerFields = candidate => candidate
      && typeof candidate === 'object'
      && candidate.atoms != null
      && candidate.lifetime != null
      && candidate.perClick != null
      && candidate.perSecond != null;
    const readClickerCandidate = value => {
      if (!value) {
        return null;
      }
      if (typeof value === 'string') {
        try {
          const decoded = JSON.parse(value);
          return readClickerCandidate(decoded);
        } catch (error) {
          return null;
        }
      }
      if (typeof value !== 'object') {
        return null;
      }
      const cloned = safeClone(value);
      if (!cloned || !hasCoreClickerFields(cloned)) {
        return null;
      }
      return cloned;
    };
    const schema = typeof parsed.schema === 'string' ? parsed.schema.toLowerCase() : '';
    const version = Number(parsed.version);
    const hasClicker = parsed.clicker && typeof parsed.clicker === 'object';
    const isEnvelope = schema === 'atom2univers.save.v2'
      || (Number.isFinite(version) && version >= 2 && hasClicker);
    const clickerPayload = readClickerCandidate(parsed.clicker)
      || readClickerCandidate(parsed.data)
      || readClickerCandidate(parsed.state);
    if (!isEnvelope && !clickerPayload) {
      return raw;
    }
    const result = clickerPayload || (hasClicker ? safeClone(parsed.clicker) || {} : {});

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

    if (!hasCoreClickerFields(result)) {
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

function serializeFusionBonusesForSave() {
  const bonuses = getFusionBonusState() || {};
  const serialized = createInitialFusionBonuses();
  const numericOrZero = value => (Number.isFinite(Number(value)) ? Number(value) : 0);
  serialized.apcFlat = numericOrZero(bonuses.apcFlat);
  serialized.apsFlat = numericOrZero(bonuses.apsFlat);
  serialized.apcHydrogenBase = numericOrZero(bonuses.apcHydrogenBase);
  serialized.apsHydrogenBase = numericOrZero(bonuses.apsHydrogenBase);
  serialized.apcBaseBoost = numericOrZero(bonuses.apcBaseBoost);
  serialized.apsBaseBoost = numericOrZero(bonuses.apsBaseBoost);
  const apcFrenzySeconds = numericOrZero(bonuses.apcFrenzyDurationSeconds);
  const apsFrenzySeconds = numericOrZero(bonuses.apsFrenzyDurationSeconds);
  serialized.apcFrenzyDurationSeconds = Math.max(0, apcFrenzySeconds);
  serialized.apsFrenzyDurationSeconds = Math.max(0, apsFrenzySeconds);
  const multiplier = Number.isFinite(Number(bonuses.fusionMultiplier))
    && Number(bonuses.fusionMultiplier) > 0
      ? Number(bonuses.fusionMultiplier)
      : 1;
  serialized.fusionMultiplier = multiplier;
  return serialized;
}

function normalizeStoredFusionBonuses(storedFusionBonuses) {
  const normalized = createInitialFusionBonuses();
  if (!storedFusionBonuses || typeof storedFusionBonuses !== 'object') {
    return normalized;
  }
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
  const apcHydrogenBase = Number(
    storedFusionBonuses.apcHydrogenBase
      ?? storedFusionBonuses.hydrogenApc
      ?? 0
  );
  const apsHydrogenBase = Number(
    storedFusionBonuses.apsHydrogenBase
      ?? storedFusionBonuses.hydrogenAps
      ?? 0
  );
  const apcBaseBoost = Number(storedFusionBonuses.apcBaseBoost);
  const apsBaseBoost = Number(storedFusionBonuses.apsBaseBoost);
  const apcFrenzySeconds = Number(
    storedFusionBonuses.apcFrenzyDurationSeconds
      ?? storedFusionBonuses.apcFrenzySeconds
      ?? storedFusionBonuses.apcFrenzy
      ?? 0
  );
  const apsFrenzySeconds = Number(
    storedFusionBonuses.apsFrenzyDurationSeconds
      ?? storedFusionBonuses.apsFrenzySeconds
      ?? storedFusionBonuses.apsFrenzy
      ?? 0
  );
  const storedMultiplier = Number(
    storedFusionBonuses.fusionMultiplier
      ?? storedFusionBonuses.multiplier
      ?? storedFusionBonuses.multi
      ?? 1
  );
  normalized.apcFlat = Number.isFinite(apc) ? apc : 0;
  normalized.apsFlat = Number.isFinite(aps) ? aps : 0;
  normalized.apcHydrogenBase = Number.isFinite(apcHydrogenBase) ? apcHydrogenBase : 0;
  normalized.apsHydrogenBase = Number.isFinite(apsHydrogenBase) ? apsHydrogenBase : 0;
  normalized.apcBaseBoost = Number.isFinite(apcBaseBoost) ? apcBaseBoost : 0;
  normalized.apsBaseBoost = Number.isFinite(apsBaseBoost) ? apsBaseBoost : 0;
  normalized.apcFrenzyDurationSeconds = Number.isFinite(apcFrenzySeconds)
    ? Math.max(0, apcFrenzySeconds)
    : 0;
  normalized.apsFrenzyDurationSeconds = Number.isFinite(apsFrenzySeconds)
    ? Math.max(0, apsFrenzySeconds)
    : 0;
  normalized.fusionMultiplier = Number.isFinite(storedMultiplier) && storedMultiplier > 0
    ? storedMultiplier
    : 1;
  return normalized;
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
    elements: (() => {
      const baseCollection = createInitialElementCollection();
      const serializedElements = {};
      Object.keys(baseCollection).forEach(id => {
        const reference = baseCollection[id];
        if (!reference) {
          return;
        }
        const stored = gameState.elements?.[id];
        const rawCount = Number(stored?.count);
        const count = Number.isFinite(rawCount) && rawCount > 0 ? Math.floor(rawCount) : 0;
        const rawLifetime = Number(stored?.lifetime);
        const lifetime = Number.isFinite(rawLifetime) && rawLifetime > 0
          ? Math.floor(rawLifetime)
          : count > 0 || stored?.owned
            ? Math.max(count, 1)
            : 0;
        serializedElements[id] = {
          id,
          gachaId: reference.gachaId,
          count,
          lifetime,
          owned: lifetime > 0,
          rarity: reference.rarity ?? (typeof stored?.rarity === 'string' ? stored.rarity : null),
          effects: Array.isArray(reference.effects) ? [...reference.effects] : [],
          bonuses: Array.isArray(reference.bonuses) ? [...reference.bonuses] : []
        };
      });
      return serializedElements;
    })(),
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
            if (Array.isArray(GACHA_INTERMEDIATE_PERMANENT_BONUS_IMAGE_DEFINITIONS)) {
        GACHA_INTERMEDIATE_PERMANENT_BONUS_IMAGE_DEFINITIONS.forEach(def => {
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
    fusionBonuses: serializeFusionBonusesForSave(),
    theme: gameState.theme,
    newsEnabled: isNewsEnabled(),
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
    ticketStarAutoCollectEnabled: gameState.ticketStarAutoCollectEnabled === true,
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
    ticketStarSpriteId: normalizeTicketStarSpritePreference(gameState.ticketStarSpriteId),
    ticketStarSpecialChance: clampTicketStarSpecialChance(gameState.ticketStarSpecialChance),
    ticketStarSpecialReward: normalizeTicketStarSpecialReward(gameState.ticketStarSpecialReward),
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
  MUSIC_PAGE_STORAGE_KEY,
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
  COLLECTION_BONUS1_IMAGES_COLLAPSED_STORAGE_KEY,
  COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY,
  COLLECTION_DOWNLOADS_COLLAPSED_STORAGE_KEY,
  COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY,
  COLLECTION_VIDEOS_STATE_STORAGE_KEY,
  HEADER_COLLAPSED_STORAGE_KEY,
  PERFORMANCE_MODE_STORAGE_KEY,
  UI_SCALE_STORAGE_KEY,
  TEXT_SCALE_STORAGE_KEY,
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
    ticketStarAutoCollectEnabled: false,
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
  applyTicketStarAutoCollectEnabled(false, { persist: false });
  updateTicketStarAutoCollectOptionVisibility();
  updateTicketStarSpriteOptionVisibility();
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

let lastUpdate = performance.now();
let lastSaveTime = performance.now();
let lastUIUpdate = performance.now();
let lastSlowUiUpdate = performance.now();

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

  if (now - lastUIUpdate > UI_FAST_UPDATE_INTERVAL_MS) {
    updateUI(now);
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
  initTextScaleOption();
  subscribeTextScaleLanguageUpdates();
  initResponsiveAutoScale();
  initTextFontOption();
  initDigitFontOption();
  initAtomDecimalOption();
  initPerformanceModeOption();
  initAtomIconOption();
  subscribeAtomIconLanguageUpdates();
  initClickSoundOption();
  subscribeClickSoundLanguageUpdates();
  initAtomAnimationOption();
  subscribeAtomAnimationLanguageUpdates();
  initCritAtomOption();
  subscribeCritAtomLanguageUpdates();
  initAndroidStatusBarOption();
  subscribeAndroidStatusBarLanguageUpdates();
  initScreenWakeLockOption();
  subscribeScreenWakeLockLanguageUpdates();
  initFrenzyAutoCollectOption();
  subscribeFrenzyAutoCollectLanguageUpdates();
  initTicketStarAutoCollectOption();
  subscribeTicketStarAutoCollectLanguageUpdates();
  initTicketStarSpriteOption();
  subscribeTicketStarSpriteLanguageUpdates();
  initCryptoWidgetOption();
  subscribeCryptoWidgetLanguageUpdates();
  initImagesModule();
  subscribeImagesLanguageUpdates();
  initBackgroundOptions();
  subscribeBackgroundLanguageUpdates();
  initNewsOption();
  subscribeNewsLanguageUpdates();
  initNewsModule();
  initRadioModule();
  subscribeRadioLanguageUpdates();
  initNotesModule();
  subscribeNotesLanguageUpdates();
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
  subscribeCollectionBonus1ImagesLanguageUpdates();
  subscribeCollectionBonus2ImagesLanguageUpdates();
  subscribeCollectionDownloadsLanguageUpdates();
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
  if (typeof document !== 'undefined' && document.body && !document.body.dataset.activePage) {
    const activePageElement = typeof document.querySelector === 'function'
      ? document.querySelector('.page.active')
      : null;
    const initialPageId = activePageElement?.id || 'game';
    const rawGroup = activePageElement?.dataset?.pageGroup || 'clicker';
    const normalizedGroup = typeof rawGroup === 'string'
      ? rawGroup.trim().toLowerCase()
      : 'clicker';
    document.body.dataset.activePage = initialPageId;
    document.body.dataset.pageGroup = normalizedGroup === 'arcade' ? 'arcade' : 'clicker';
  }
  applyAtomVariantVisualState();
  applyStartupOverlayDuration();
  scheduleStartupOverlayFailsafe();
  prefetchAndroidManagedFiles();
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
