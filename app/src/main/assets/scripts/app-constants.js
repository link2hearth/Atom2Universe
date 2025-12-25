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

const DEFAULT_HEADER_TURTLE_SETTINGS = Object.freeze({
  spriteUrl: 'Assets/sprites/Tortue.png',
  frameWidth: 48,
  frameHeight: 48,
  frameCount: 6,
  frameDurationMs: 500
});

const ACTIVE_HEADER_TURTLE_SETTINGS = typeof HEADER_TURTLE_SETTINGS !== 'undefined'
  && HEADER_TURTLE_SETTINGS
  && typeof HEADER_TURTLE_SETTINGS === 'object'
    ? HEADER_TURTLE_SETTINGS
    : DEFAULT_HEADER_TURTLE_SETTINGS;

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

const DEFAULT_NEWS_SETTINGS = Object.freeze({
  enabledByDefault: true,
  defaultFeedUrl: 'https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr',
  searchUrlTemplate: 'https://news.google.com/rss/search?q={query}&hl=fr&gl=FR&ceid=FR:fr',
  sources: [
    {
      id: 'google-news-fr',
      titleKey: 'index.sections.news.sources.google',
      feedUrl: 'https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr',
      searchUrlTemplate: 'https://news.google.com/rss/search?q={query}&hl=fr&gl=FR&ceid=FR:fr'
    },
    {
      id: 'reuters-world',
      titleKey: 'index.sections.news.sources.reutersWorld',
      feedUrl: 'https://www.reuters.com/world/rss'
    },
    {
      id: 'reuters-world-news',
      titleKey: 'index.sections.news.sources.reutersWorldNews',
      feedUrl: 'https://www.reuters.com/rssFeed/worldNews'
    },
    {
      id: 'gouvernement-fr',
      titleKey: 'index.sections.news.sources.gouvernement',
      feedUrl: 'https://www.gouvernement.fr/actualites.rss'
    },
    {
      id: 'insee',
      titleKey: 'index.sections.news.sources.insee',
      feedUrl: 'https://www.insee.fr/fr/statistiques/serie/rss'
    },
    {
      id: 'nasa',
      titleKey: 'index.sections.news.sources.nasa',
      feedUrl: 'https://www.nasa.gov/rss/dyn/breaking_news.rss'
    },
    {
      id: 'esa',
      titleKey: 'index.sections.news.sources.esa',
      feedUrl: 'https://www.esa.int/rssfeed/ESA_top_news'
    },
    {
      id: 'nature-science',
      titleKey: 'index.sections.news.sources.nature',
      feedUrl: 'https://www.nature.com/subjects/science/rss'
    },
    {
      id: 'nobel-prize',
      titleKey: 'index.sections.news.sources.nobel',
      feedUrl: 'https://www.nobelprize.org/feed/'
    },
    {
      id: 'the-verge',
      titleKey: 'index.sections.news.sources.theVerge',
      feedUrl: 'https://www.theverge.com/rss/index.xml'
    }
  ],
  proxyBaseUrl: 'https://api.allorigins.win/raw?url=',
  proxyBaseUrls: [
    'https://api.allorigins.win/raw?url=',
    'https://cors.isomorphic-git.org/'
  ],
  refreshIntervalMs: 10 * 60 * 1000,
  maxItems: 40,
  bannerItemCount: 12,
  bannerDisplayDurationMs: 15000,
  requestTimeoutMs: 12000
});

const ACTIVE_NEWS_SETTINGS = typeof NEWS_SETTINGS !== 'undefined'
  && NEWS_SETTINGS
  && typeof NEWS_SETTINGS === 'object'
    ? NEWS_SETTINGS
    : DEFAULT_NEWS_SETTINGS;

const DEFAULT_RADIO_SETTINGS = Object.freeze({
  servers: ['https://de1.api.radio-browser.info', 'https://de2.api.radio-browser.info'],
  requestTimeoutMs: 12000,
  maxResults: 50,
  hideBroken: true,
  favoritesStorageKey: 'atom2univers.radio.favorites',
  userAgent: 'Atom2Univers/Radio'
});

const ACTIVE_RADIO_SETTINGS = typeof RADIO_SETTINGS !== 'undefined'
  && RADIO_SETTINGS
  && typeof RADIO_SETTINGS === 'object'
    ? RADIO_SETTINGS
    : DEFAULT_RADIO_SETTINGS;

const RADIO_REQUEST_TIMEOUT_MS = (() => {
  const fallback = DEFAULT_RADIO_SETTINGS.requestTimeoutMs;
  const raw = Number(ACTIVE_RADIO_SETTINGS?.requestTimeoutMs);
  if (Number.isFinite(raw) && raw > 0) {
    return Math.floor(raw);
  }
  return fallback;
})();

const RADIO_MAX_RESULTS = (() => {
  const fallback = DEFAULT_RADIO_SETTINGS.maxResults;
  const raw = Number(ACTIVE_RADIO_SETTINGS?.maxResults);
  if (Number.isFinite(raw) && raw > 0) {
    return Math.floor(raw);
  }
  return fallback;
})();

const RADIO_HIDE_BROKEN = ACTIVE_RADIO_SETTINGS?.hideBroken !== false;

const RADIO_FAVORITES_STORAGE_KEY = (() => {
  const raw = typeof ACTIVE_RADIO_SETTINGS?.favoritesStorageKey === 'string'
    ? ACTIVE_RADIO_SETTINGS.favoritesStorageKey.trim()
    : '';
  if (raw) {
    return raw;
  }
  return DEFAULT_RADIO_SETTINGS.favoritesStorageKey;
})();

const RADIO_USER_AGENT = typeof ACTIVE_RADIO_SETTINGS?.userAgent === 'string'
  && ACTIVE_RADIO_SETTINGS.userAgent.trim()
  ? ACTIVE_RADIO_SETTINGS.userAgent.trim()
  : DEFAULT_RADIO_SETTINGS.userAgent;

const DEFAULT_IMAGE_FEED_SETTINGS = Object.freeze({
  enabledByDefault: true,
  maxItems: 15,
  refreshIntervalMs: 30 * 60 * 1000,
  requestTimeoutMs: 15000,
  maxImageBytes: 10 * 1024 * 1024,
  favoriteBackgroundRotationMs: 5 * 60 * 1000,
  favoriteBackgroundEnabledByDefault: false,
  proxyBaseUrls: [
    'https://api.allorigins.win/raw?url=',
    'https://cors.isomorphic-git.org/'
  ],
  sources: [
    { id: 'flickr-public', titleKey: 'index.sections.images.sources.flickr', feedUrl: 'https://www.flickr.com/services/feeds/photos_public.gne' },
    { id: 'nasa-image-day', titleKey: 'index.sections.images.sources.nasaImageDay', feedUrl: 'https://www.nasa.gov/rss/dyn/lg_image_of_the_day.rss' },
    { id: 'wikimedia-potd', titleKey: 'index.sections.images.sources.wikimediaPotd', feedUrl: 'https://commons.wikimedia.org/w/api.php?action=featuredfeed&feed=potd&language=fr&feedformat=rss' }
  ]
});

const ACTIVE_IMAGE_FEED_SETTINGS = typeof IMAGE_FEED_SETTINGS !== 'undefined'
  && IMAGE_FEED_SETTINGS
  && typeof IMAGE_FEED_SETTINGS === 'object'
    ? IMAGE_FEED_SETTINGS
    : DEFAULT_IMAGE_FEED_SETTINGS;

const IMAGE_DOWNLOAD_TARGET_PATH = 'Pictures/Atom2Univers';

const IMAGE_FAVORITE_CACHE_MAX_DIMENSION = 1280;
const IMAGE_THUMBNAIL_MAX_DIMENSION = 512;
const IMAGE_THUMBNAIL_IDLE_DELAY_MS = 300;
const LOCAL_BACKGROUND_BANK_STORAGE_KEY = 'atom2univers.background.bank.v1';
const BACKGROUND_DURATION_STORAGE_KEY = 'atom2univers.background.duration.ms';
