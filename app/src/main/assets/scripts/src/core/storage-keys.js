const PRIMARY_SAVE_STORAGE_KEY = 'atom2univers';
const RELOAD_SAVE_STORAGE_KEY = 'atom2univers.reloadPendingSave';
const LANGUAGE_STORAGE_KEY = 'atom2univers.language';
const MUSIC_PAGE_STORAGE_KEY = 'atom2univers.music.lastPage';
const CLICK_SOUND_STORAGE_KEY = 'atom2univers.options.clickSoundMuted';
const CRIT_ATOM_VISUALS_STORAGE_KEY = 'atom2univers.options.critAtomVisualsDisabled';
const ATOM_ANIMATION_PREFERENCE_STORAGE_KEY = 'atom2univers.options.atomAnimationsEnabled';
const ATOM_ICON_VISIBILITY_STORAGE_KEY = 'atom2univers.options.atomIconVisible';
const NOTES_STYLE_STORAGE_KEY = 'atom2univers.notes.editorStyle';
const FRENZY_AUTO_COLLECT_STORAGE_KEY = 'atom2univers.options.frenzyAutoCollectEnabled';
const TICKET_STAR_AUTO_COLLECT_STORAGE_KEY = 'atom2univers.options.ticketStarAutoCollectEnabled';
const TICKET_STAR_SPRITE_STORAGE_KEY = 'atom2univers.options.ticketStarSprite';
const CRYPTO_WIDGET_STORAGE_KEY = 'atom2univers.options.cryptoWidgetEnabled';
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
const COLLECTION_DOWNLOADS_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.downloadsCollapsed';
const COLLECTION_VIDEOS_COLLAPSED_STORAGE_KEY = 'atom2univers.collection.videosCollapsed';
const COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY = 'atom2univers.collection.videosUnlocked';
const COLLECTION_VIDEOS_STATE_STORAGE_KEY = 'atom2univers.collection.videosState.v1';
const HEADER_COLLAPSED_STORAGE_KEY = 'atom2univers.ui.headerCollapsed';
const ARCADE_HUB_CARD_STATE_STORAGE_KEY = 'atom2univers.arcadeHub.cardStates.v1';
const ARCADE_HUB_CARD_ORDER_STORAGE_KEY = 'atom2univers.arcadeHub.cardOrder.v1';
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
