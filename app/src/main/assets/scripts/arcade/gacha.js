const DEFAULT_GACHA_TICKET_COST = 1;

function isCollectionSystemActive() {
  if (typeof globalThis !== 'undefined' && typeof globalThis.COLLECTION_SYSTEM_ENABLED === 'boolean') {
    return globalThis.COLLECTION_SYSTEM_ENABLED;
  }
  return true;
}

function isGachaImageCollectionEnabled() {
  return isCollectionSystemActive();
}

function isGachaCardCollectionEnabled() {
  if (typeof globalThis !== 'undefined' && typeof globalThis.COLLECTION_CARDS_ENABLED === 'boolean') {
    return globalThis.COLLECTION_CARDS_ENABLED;
  }
  return true;
}

function isGachaBonusImageCollectionEnabled() {
  if (typeof globalThis !== 'undefined'
    && typeof globalThis.COLLECTION_BONUS_IMAGES_ENABLED === 'boolean'
  ) {
    return globalThis.COLLECTION_BONUS_IMAGES_ENABLED;
  }
  return true;
}

function isCollectionVideoCollectionEnabled() {
  if (typeof globalThis !== 'undefined'
    && typeof globalThis.COLLECTION_VIDEOS_ENABLED === 'boolean'
  ) {
    return globalThis.COLLECTION_VIDEOS_ENABLED;
  }
  return isCollectionSystemActive();
}

const GACHA_SMOKE_FRAME_COUNT = 91;
const GACHA_SMOKE_FRAME_RATE = 12;
const GACHA_SMOKE_FRAME_PAD = 4;
const GACHA_SMOKE_ASSET_PATH = 'Assets/sprites/Smoke';

const gachaSmokeAnimationState = {
  timer: null,
  frame: 0,
  element: null,
  reducedMotion: false,
  lastUpdate: 0,
  timerType: null,
  lastFrameUrl: ''
};

let gachaSmokeFramesPreloaded = false;
const gachaSmokePreloadedImages = [];

function getGachaSmokeFrameUrl(frameIndex) {
  const frameName = formatGachaSmokeFrame(frameIndex);
  return encodeURI(`${GACHA_SMOKE_ASSET_PATH}/${frameName}.png`);
}

function preloadGachaSmokeFrames() {
  if (gachaSmokeFramesPreloaded || typeof Image !== 'function') {
    return;
  }
  gachaSmokeFramesPreloaded = true;
  for (let index = 0; index < GACHA_SMOKE_FRAME_COUNT; index += 1) {
    const image = new Image();
    try {
      image.decoding = 'async';
    } catch (error) {
      // Ignore if the browser does not support setting decoding.
    }
    image.src = getGachaSmokeFrameUrl(index);
    gachaSmokePreloadedImages.push(image);
  }
}

function prefersReducedMotion() {
  if (typeof matchMedia !== 'function') {
    return false;
  }
  try {
    return matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch (error) {
    return false;
  }
}

function formatGachaSmokeFrame(frameIndex) {
  const normalized = ((frameIndex % GACHA_SMOKE_FRAME_COUNT) + GACHA_SMOKE_FRAME_COUNT) % GACHA_SMOKE_FRAME_COUNT;
  return String(normalized).padStart(GACHA_SMOKE_FRAME_PAD, '0');
}

function applyGachaSmokeFrame(element, frameIndex) {
  if (!element) {
    return;
  }
  const frameUrl = getGachaSmokeFrameUrl(frameIndex);
  if (frameUrl === gachaSmokeAnimationState.lastFrameUrl) {
    return;
  }
  gachaSmokeAnimationState.lastFrameUrl = frameUrl;
  element.style.backgroundImage = `url("${frameUrl}")`;
}

function stopGachaFeaturedSmokeAnimation() {
  if (gachaSmokeAnimationState.timer != null) {
    if (gachaSmokeAnimationState.timerType === 'timeout' && typeof clearTimeout === 'function') {
      clearTimeout(gachaSmokeAnimationState.timer);
    } else if (typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(gachaSmokeAnimationState.timer);
    }
  }
  gachaSmokeAnimationState.timer = null;
  gachaSmokeAnimationState.frame = 0;
  if (gachaSmokeAnimationState.element) {
    gachaSmokeAnimationState.element.style.backgroundImage = '';
  }
  gachaSmokeAnimationState.element = null;
  gachaSmokeAnimationState.reducedMotion = false;
  gachaSmokeAnimationState.lastUpdate = 0;
  gachaSmokeAnimationState.timerType = null;
  gachaSmokeAnimationState.lastFrameUrl = '';
}

function startGachaFeaturedSmokeAnimation(element) {
  if (!element) {
    return;
  }
  stopGachaFeaturedSmokeAnimation();
  gachaSmokeAnimationState.element = element;
  gachaSmokeAnimationState.reducedMotion = prefersReducedMotion();
  gachaSmokeAnimationState.frame = 0;
  gachaSmokeAnimationState.lastUpdate = typeof performance !== 'undefined'
    ? performance.now()
    : Date.now();
  preloadGachaSmokeFrames();
  applyGachaSmokeFrame(element, gachaSmokeAnimationState.frame);
  if (gachaSmokeAnimationState.reducedMotion) {
    return;
  }
  const frameDuration = 1000 / GACHA_SMOKE_FRAME_RATE;
  gachaSmokeAnimationState.timerType = typeof requestAnimationFrame === 'function' ? 'raf' : 'timeout';
  const step = (timestamp) => {
    const target = gachaSmokeAnimationState.element;
    if (!target) {
      stopGachaFeaturedSmokeAnimation();
      return;
    }
    const now = timestamp ?? (typeof performance !== 'undefined' ? performance.now() : Date.now());
    const elapsed = now - gachaSmokeAnimationState.lastUpdate;
    if (elapsed >= frameDuration) {
      const framesToAdvance = Math.max(1, Math.floor(elapsed / frameDuration));
      gachaSmokeAnimationState.lastUpdate = now - (elapsed % frameDuration);
      gachaSmokeAnimationState.frame = (gachaSmokeAnimationState.frame + framesToAdvance) % GACHA_SMOKE_FRAME_COUNT;
      applyGachaSmokeFrame(target, gachaSmokeAnimationState.frame);
    }
    if (gachaSmokeAnimationState.timerType === 'timeout') {
      gachaSmokeAnimationState.timer = setTimeout(() => {
        step(typeof performance !== 'undefined' ? performance.now() : Date.now());
      }, frameDuration);
    } else {
      gachaSmokeAnimationState.timer = requestAnimationFrame(step);
    }
  };
  if (gachaSmokeAnimationState.timerType === 'timeout') {
    gachaSmokeAnimationState.timer = setTimeout(() => {
      step(typeof performance !== 'undefined' ? performance.now() : Date.now());
    }, frameDuration);
  } else {
    gachaSmokeAnimationState.timer = requestAnimationFrame(step);
  }
}

function createGachaFeaturedSmokeBackdrop() {
  if (typeof document === 'undefined') {
    return null;
  }
  const element = document.createElement('span');
  element.className = 'gacha-featured-info__smoke';
  startGachaFeaturedSmokeAnimation(element);
  return element;
}

const gachaTranslate = (() => {
  const translator = typeof globalThis !== 'undefined' && typeof globalThis.t === 'function'
    ? globalThis.t.bind(globalThis)
    : null;
  if (translator) {
    return translator;
  }
  return (key, params) => {
    if (typeof key !== 'string' || !key) {
      return '';
    }
    if (!params || typeof params !== 'object') {
      return key;
    }
    return key.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
      const value = params[token];
      return value == null ? match : String(value);
    });
  };
})();

function translateWithFallback(key, fallback = '', params) {
  if (typeof key !== 'string' || !key.trim()) {
    return fallback ?? '';
  }
  const api = typeof getI18nApi === 'function' ? getI18nApi() : null;
  let translator = null;
  if (api && typeof api.t === 'function') {
    translator = api.t.bind(api);
  } else if (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function') {
    translator = globalThis.t.bind(globalThis);
  } else if (typeof t === 'function') {
    translator = t;
  }
  if (translator) {
    try {
      const translated = translator(key, params);
      if (typeof translated === 'string' && translated && translated !== key) {
        return translated;
      }
    } catch (error) {
      console.warn('Unable to translate key', key, error);
    }
  }
  return fallback ?? '';
}

const GACHA_PERIODIC_ELEMENT_I18N_BASE = 'scripts.periodic.elements';

function getGachaElementTranslationBase(definition) {
  const id = typeof definition?.id === 'string' ? definition.id.trim() : '';
  if (!id) {
    return '';
  }
  return `${GACHA_PERIODIC_ELEMENT_I18N_BASE}.${id}`;
}

function translateGachaElementField(definition, field, fallback) {
  if (!field) {
    return fallback ?? '';
  }
  const base = getGachaElementTranslationBase(definition);
  if (!base) {
    return fallback ?? '';
  }
  const key = `${base}.${field}`;
  const translated = translateWithFallback(key, fallback ?? '');
  if (typeof translated === 'string' && translated.trim()) {
    return translated;
  }
  return fallback ?? '';
}

function getGachaPeriodicElementDisplay(definition) {
  if (!definition || typeof definition !== 'object') {
    return { symbol: '', name: '' };
  }
  const fallbackSymbol = typeof definition.symbol === 'string' ? definition.symbol : '';
  const fallbackName = typeof definition.name === 'string' ? definition.name : '';
  const symbol = translateGachaElementField(definition, 'symbol', fallbackSymbol);
  const name = translateGachaElementField(definition, 'name', fallbackName);
  return { symbol, name };
}

function getGachaLocalizedElementName(definition) {
  if (!definition || typeof definition !== 'object') {
    return '';
  }
  const { name, symbol } = getGachaPeriodicElementDisplay(definition);
  if (name && name.trim()) {
    return name.trim();
  }
  if (symbol && symbol.trim()) {
    return symbol.trim();
  }
  if (typeof definition.name === 'string' && definition.name.trim()) {
    return definition.name.trim();
  }
  if (typeof definition.symbol === 'string' && definition.symbol.trim()) {
    return definition.symbol.trim();
  }
  return '';
}

const SPECIAL_GACHA_CARD_CHANCE = 1 / 1000;

const SPECIAL_GACHA_ELEMENT_CARD_MAP = Object.freeze({
  hydrogene: 'hydrogene',
  helium: 'helium'
});

const SPECIAL_GACHA_RARITY_CARD_MAP = Object.freeze({
  stellaire: ['carbone', 'azote', 'oxygene'],
  mythique: ['fer'],
  singulier: ['or', 'argent', 'cuivre'],
  irreel: ['plutonium']
});

const SPECIAL_GACHA_CARD_DEFINITION_INDEX = new Map(
  Array.isArray(GACHA_SPECIAL_CARD_DEFINITIONS)
    ? GACHA_SPECIAL_CARD_DEFINITIONS.map(def => [def.id, def])
    : []
);

const COLLECTION_VIDEO_GACHA_CHANCE = 0.0005;
const BONUS_GACHA_IMAGE_CHANCE = 1 / 100;
const BONUS_GACHA_PERMANENT_IMAGE_CHANCE = 1 / 200;
const BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_CHANCE = 1 / 200;
const BONUS_GACHA_IMAGE_AVAILABILITY_BATCH_SIZE = 12;

const COLLECTION_VIDEO_DEFINITION_INDEX = new Map(
  Array.isArray(COLLECTION_VIDEO_DEFINITIONS)
    ? COLLECTION_VIDEO_DEFINITIONS.map(def => [def.id, def])
    : []
);

const COLLECTION_VIDEO_ALL_IDS = Array.isArray(COLLECTION_VIDEO_DEFINITIONS)
  ? COLLECTION_VIDEO_DEFINITIONS.map(def => def.id).filter(Boolean)
  : [];

const BONUS_GACHA_IMAGE_DEFINITION_INDEX = new Map(
  Array.isArray(GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS)
    ? GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS.map(def => [def.id, def])
    : []
);

const BONUS_GACHA_PERMANENT_IMAGE_DEFINITION_INDEX = new Map(
  Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)
    ? GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => [def.id, def])
    : []
);

const BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_DEFINITION_INDEX = new Map(
  Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)
    ? GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => [def.id, def])
    : []
);

const BONUS_GACHA_IMAGE_ALL_IDS = Array.isArray(GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS)
  ? GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS.map(def => def.id).filter(Boolean)
  : [];

const BONUS_GACHA_PERMANENT_IMAGE_ALL_IDS = Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)
  ? GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => def.id).filter(Boolean)
  : [];

const BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_ALL_IDS = Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)
  ? GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS.map(def => def.id).filter(Boolean)
  : [];

const bonusGachaImageAssetAvailabilityCache = new Map();
const missingBonusGachaImageIds = new Set();

function normalizeBonusGachaAssetPath(path) {
  if (typeof path !== 'string') {
    return '';
  }
  return path.replace(/^[./]+/, '').replace(/\\+/g, '/');
}

function checkBonusGachaImageManifest(normalizedPath) {
  if (typeof globalThis === 'undefined') {
    return null;
  }
  let hasManifest = false;
  const candidates = [
    globalThis.__GACHA_BONUS_IMAGE_FILE_SET__,
    globalThis.__GACHA_BONUS_IMAGE_FILE_LIST__,
    globalThis.GACHA_BONUS_IMAGE_FILES,
    globalThis.gachaBonusImageFiles
  ];
  for (let index = 0; index < candidates.length; index += 1) {
    const source = candidates[index];
    if (!source) {
      continue;
    }
    hasManifest = true;
    if (source instanceof Set) {
      if (source.has(normalizedPath) || source.has(`./${normalizedPath}`)) {
        return true;
      }
      continue;
    }
    if (Array.isArray(source)) {
      const match = source.find(entry => {
        if (typeof entry !== 'string') {
          return false;
        }
        const normalizedEntry = normalizeBonusGachaAssetPath(entry);
        return normalizedEntry === normalizedPath;
      });
      if (match) {
        return true;
      }
      continue;
    }
    if (typeof source === 'object') {
      if (Object.prototype.hasOwnProperty.call(source, normalizedPath)) {
        return Boolean(source[normalizedPath]);
      }
      if (Object.prototype.hasOwnProperty.call(source, `./${normalizedPath}`)) {
        return Boolean(source[`./${normalizedPath}`]);
      }
      const values = Object.values(source);
      const match = values.find(entry => {
        if (typeof entry !== 'string') {
          return false;
        }
        const normalizedEntry = normalizeBonusGachaAssetPath(entry);
        return normalizedEntry === normalizedPath;
      });
      if (match) {
        return true;
      }
      continue;
    }
  }
  if (hasManifest) {
    return false;
  }
  return null;
}

function checkBonusGachaImageAssetWithFs(assetPath) {
  if (typeof require !== 'function') {
    return null;
  }
  try {
    const fs = require('fs');
    const path = require('path');
    const baseDir = typeof __dirname === 'string' ? __dirname : '';
    const resolved = baseDir
      ? path.resolve(baseDir, '..', '..', assetPath)
      : path.resolve(assetPath);
    if (fs.existsSync(resolved)) {
      return true;
    }
    return false;
  } catch (error) {
    return null;
  }
}

function checkBonusGachaImageAssetWithRequest(assetPath) {
  if (typeof XMLHttpRequest !== 'function') {
    return null;
  }
  try {
    const request = new XMLHttpRequest();
    request.open('HEAD', assetPath, false);
    request.send(null);
    if (request.status >= 200 && request.status < 400) {
      return true;
    }
    if (request.status === 0) {
      return null;
    }
    return false;
  } catch (error) {
    return false;
  }
}

function isBonusGachaImageAssetAvailable(definition) {
  if (!definition) {
    return false;
  }
  const assetPath = definition.assetPath;
  if (typeof assetPath !== 'string' || !assetPath.trim()) {
    return false;
  }
  const cached = bonusGachaImageAssetAvailabilityCache.get(assetPath);
  if (typeof cached === 'boolean') {
    return cached;
  }
  const normalizedPath = normalizeBonusGachaAssetPath(assetPath);
  let determined = false;
  let exists = false;

  const manifestResult = checkBonusGachaImageManifest(normalizedPath);
  if (manifestResult != null) {
    exists = Boolean(manifestResult);
    determined = true;
  }

  if (!determined) {
    const fsResult = checkBonusGachaImageAssetWithFs(assetPath);
    if (fsResult != null) {
      exists = Boolean(fsResult);
      determined = true;
    }
  }

  if (!determined) {
    const requestResult = checkBonusGachaImageAssetWithRequest(assetPath);
    if (requestResult != null) {
      exists = Boolean(requestResult);
      determined = true;
    }
  }

  if (!determined) {
    exists = true;
  }

  bonusGachaImageAssetAvailabilityCache.set(assetPath, exists);
  return exists;
}

function markBonusGachaImageMissing(imageId, definition = null) {
  if (!imageId) {
    return;
  }
  missingBonusGachaImageIds.add(imageId);
  if (definition && typeof definition.assetPath === 'string') {
    bonusGachaImageAssetAvailabilityCache.set(definition.assetPath, false);
  }
}

function isBonusGachaImageMarkedMissing(imageId) {
  if (!imageId) {
    return false;
  }
  return missingBonusGachaImageIds.has(imageId);
}

const SPECIAL_GACHA_CARD_ELEMENT_INDEX = (() => {
  const map = new Map();
  if (Array.isArray(periodicElements)) {
    periodicElements.forEach(def => {
      const gachaId = typeof def.gachaId === 'string' ? def.gachaId.trim() : '';
      const id = gachaId || def.id;
      if (id) {
        map.set(id, def);
      }
    });
  }
  return map;
})();

function ensureGachaCardCollection() {
  if (!gameState.gachaCards || typeof gameState.gachaCards !== 'object') {
    if (typeof createInitialGachaCardCollection === 'function') {
      gameState.gachaCards = createInitialGachaCardCollection();
    } else {
      gameState.gachaCards = {};
    }
  }
  return gameState.gachaCards;
}

function ensureGachaImageCollection() {
  if (!gameState.gachaImages || typeof gameState.gachaImages !== 'object') {
    if (typeof createInitialGachaImageCollection === 'function') {
      gameState.gachaImages = createInitialGachaImageCollection();
    } else {
      gameState.gachaImages = {};
    }
  }
  return gameState.gachaImages;
}

function ensureGachaBonusImageCollection() {
  if (!gameState.gachaBonusImages || typeof gameState.gachaBonusImages !== 'object') {
    if (typeof createInitialGachaBonusImageCollection === 'function') {
      gameState.gachaBonusImages = createInitialGachaBonusImageCollection();
    } else {
      gameState.gachaBonusImages = {};
    }
  }
  return gameState.gachaBonusImages;
}

function getOwnedBonusGachaImageCount(imageId) {
  if (!imageId) {
    return 0;
  }
  const bonusCollection = ensureGachaBonusImageCollection();
  const bonusEntry = bonusCollection[imageId];
  const bonusCount = Number(bonusEntry?.count ?? bonusEntry);
  if (Number.isFinite(bonusCount) && bonusCount > 0) {
    return bonusCount;
  }
  const imageCollection = ensureGachaImageCollection();
  const legacyEntry = imageCollection[imageId];
  const legacyCount = Number(legacyEntry?.count ?? legacyEntry);
  return Number.isFinite(legacyCount) && legacyCount > 0 ? legacyCount : 0;
}

function ensureCollectionVideoCollection() {
  if (!gameState.collectionVideos || typeof gameState.collectionVideos !== 'object') {
    if (typeof createInitialCollectionVideoCollection === 'function') {
      gameState.collectionVideos = createInitialCollectionVideoCollection();
    } else {
      gameState.collectionVideos = {};
    }
  }
  return gameState.collectionVideos;
}

function getSpecialGachaCardDefinition(cardId) {
  if (!cardId) {
    return null;
  }
  return SPECIAL_GACHA_CARD_DEFINITION_INDEX.get(cardId) || null;
}

function resolveSpecialGachaCardLabel(cardId) {
  if (!cardId) {
    return '';
  }
  const definition = getSpecialGachaCardDefinition(cardId);
  const elementDef = SPECIAL_GACHA_CARD_ELEMENT_INDEX.get(cardId) || null;
  const elementName = elementDef ? getGachaLocalizedElementName(elementDef) : cardId;
  const fallback = definition?.labelFallback || `Carte ${elementName}`;
  return translateWithFallback(definition?.labelKey, fallback, { element: elementName });
}

function getBonusGachaImageDefinition(imageId) {
  if (!imageId) {
    return null;
  }
  return BONUS_GACHA_IMAGE_DEFINITION_INDEX.get(imageId)
    || BONUS_GACHA_PERMANENT_IMAGE_DEFINITION_INDEX.get(imageId)
    || BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_DEFINITION_INDEX.get(imageId)
    || null;
}

function getCollectionVideoDefinitionForGacha(videoId) {
  if (!videoId) {
    return null;
  }
  if (COLLECTION_VIDEO_DEFINITION_INDEX.has(videoId)) {
    return COLLECTION_VIDEO_DEFINITION_INDEX.get(videoId);
  }
  if (Array.isArray(COLLECTION_VIDEO_DEFINITIONS)) {
    return COLLECTION_VIDEO_DEFINITIONS.find(def => def && def.id === videoId) || null;
  }
  return null;
}

function resolveBonusGachaImageLabel(imageId) {
  if (!imageId) {
    return '';
  }
  const definition = getBonusGachaImageDefinition(imageId);
  if (!definition) {
    return translateWithFallback(`scripts.gacha.images.names.${imageId}`, `Image ${imageId}`);
  }
  const api = getI18nApi();
  const language = api && typeof api.getCurrentLanguage === 'function'
    ? api.getCurrentLanguage()
    : null;
  if (language && definition.names && typeof definition.names === 'object') {
    const direct = definition.names[language];
    if (typeof direct === 'string' && direct.trim()) {
      return direct.trim();
    }
    const [base] = language.split('-');
    if (base && typeof definition.names[base] === 'string' && definition.names[base].trim()) {
      return definition.names[base].trim();
    }
  }
  if (definition.labelKey) {
    const translated = translateWithFallback(definition.labelKey, definition.labelFallback);
    if (translated) {
      return translated;
    }
  }
  return definition.labelFallback || `Image ${imageId}`;
}

function resolveCollectionVideoLabelForGacha(videoId) {
  if (!videoId) {
    return '';
  }
  const definition = getCollectionVideoDefinitionForGacha(videoId);
  if (definition) {
    const api = getI18nApi();
    const language = api && typeof api.getCurrentLanguage === 'function'
      ? api.getCurrentLanguage()
      : null;
    if (language && definition.names && typeof definition.names === 'object') {
      const direct = definition.names[language];
      if (typeof direct === 'string' && direct.trim()) {
        return direct.trim();
      }
      const [base] = language.split('-');
      if (base && typeof definition.names[base] === 'string' && definition.names[base].trim()) {
        return definition.names[base].trim();
      }
    }
    if (definition.labelKey) {
      const translated = translateWithFallback(definition.labelKey, definition.labelFallback);
      if (translated) {
        return translated;
      }
    }
    if (definition.labelFallback) {
      return definition.labelFallback;
    }
  }
  return translateWithFallback(`scripts.collection.videos.names.${videoId}`, `Vidéo ${videoId}`);
}

function rollForSpecialGachaCard() {
  return Math.random() < SPECIAL_GACHA_CARD_CHANCE;
}

function rollForBonusGachaImage() {
  return Math.random() < BONUS_GACHA_IMAGE_CHANCE;
}

function rollForCollectionVideoReward() {
  return Math.random() < COLLECTION_VIDEO_GACHA_CHANCE;
}

function getAvailableCollectionVideoIds() {
  if (!isCollectionVideoCollectionEnabled()) {
    return [];
  }
  if (!COLLECTION_VIDEO_ALL_IDS.length) {
    return [];
  }
  const collection = ensureCollectionVideoCollection();
  return COLLECTION_VIDEO_ALL_IDS.filter(videoId => {
    if (!videoId) {
      return false;
    }
    const stored = collection[videoId];
    const rawCount = Number.isFinite(Number(stored?.count ?? stored))
      ? Math.max(0, Math.floor(Number(stored?.count ?? stored)))
      : 0;
    return rawCount <= 0;
  });
}

function getAvailableBonusGachaImageIds() {
  if (!isGachaImageCollectionEnabled()) {
    return [];
  }
  if (!BONUS_GACHA_IMAGE_ALL_IDS.length) {
    return [];
  }
  const collection = ensureGachaImageCollection();
  if (!collection || typeof collection !== 'object') {
    return BONUS_GACHA_IMAGE_ALL_IDS.filter(imageId => !isBonusGachaImageMarkedMissing(imageId));
  }
  return BONUS_GACHA_IMAGE_ALL_IDS.filter(imageId => {
    if (isBonusGachaImageMarkedMissing(imageId)) {
      return false;
    }
    const stored = collection[imageId];
    const rawCount = Number.isFinite(Number(stored?.count ?? stored))
      ? Math.max(0, Math.floor(Number(stored?.count ?? stored)))
      : 0;
    return rawCount <= 0;
  });
}

function rollForPermanentBonusGachaImage() {
  return Math.random() < BONUS_GACHA_PERMANENT_IMAGE_CHANCE;
}

function isPrimaryBonusImageCollectionCompleteForGacha() {
  if (!Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)) {
    return false;
  }
  return GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS.every(def => {
    if (!def || !def.id) {
      return true;
    }
    const count = getOwnedBonusGachaImageCount(def.id);
    return Number.isFinite(count) && count > 0;
  });
}

function isSecondaryBonusImageCollectionUnlockedForGacha() {
  if (typeof globalThis !== 'undefined'
    && typeof globalThis.isSecondaryBonusImageCollectionUnlocked === 'function'
  ) {
    try {
      return globalThis.isSecondaryBonusImageCollectionUnlocked();
    } catch (error) {
      // Fallback to local computation on unexpected errors.
    }
  }
  return isPrimaryBonusImageCollectionCompleteForGacha();
}

function getAvailablePermanentBonusGachaImageIds() {
  if (!BONUS_GACHA_PERMANENT_IMAGE_ALL_IDS.length) {
    return [];
  }
  return BONUS_GACHA_PERMANENT_IMAGE_ALL_IDS.filter(imageId => {
    if (isBonusGachaImageMarkedMissing(imageId)) {
      return false;
    }
    const rawCount = Math.max(0, Math.floor(getOwnedBonusGachaImageCount(imageId)));
    return rawCount <= 0;
  });
}

function rollForSecondaryPermanentBonusGachaImage() {
  return Math.random() < BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_CHANCE;
}

function getAvailableSecondaryPermanentBonusGachaImageIds() {
  if (!isSecondaryBonusImageCollectionUnlockedForGacha()) {
    return [];
  }
  if (!BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_ALL_IDS.length) {
    return [];
  }
  return BONUS_GACHA_SECONDARY_PERMANENT_IMAGE_ALL_IDS.filter(imageId => {
    if (isBonusGachaImageMarkedMissing(imageId)) {
      return false;
    }
    const rawCount = Math.max(0, Math.floor(getOwnedBonusGachaImageCount(imageId)));
    return rawCount <= 0;
  });
}

function awardSpecialGachaCard(cardId) {
  if (!isGachaCardCollectionEnabled()) {
    return null;
  }
  if (!cardId) {
    return null;
  }
  const collection = ensureGachaCardCollection();
  if (!collection[cardId]) {
    collection[cardId] = { id: cardId, count: 0 };
  }
  const entry = collection[cardId];
  const previousCount = Number(entry.count) || 0;
  const newCount = previousCount + 1;
  entry.count = newCount;
  const definition = getSpecialGachaCardDefinition(cardId);
  const label = resolveSpecialGachaCardLabel(cardId);
  return {
    cardId,
    definition,
    label,
    assetPath: definition?.assetPath || null,
    count: newCount,
    isNew: previousCount === 0,
    type: 'card'
  };
}

function awardBonusGachaImage(imageId, options = null) {
  if (!isGachaImageCollectionEnabled()) {
    return null;
  }
  if (!imageId) {
    return null;
  }
  const { definition: providedDefinition = null, skipAssetCheck = false } = options || {};
  const collection = ensureGachaBonusImageCollection();
  if (!collection[imageId]) {
    collection[imageId] = {
      id: imageId,
      count: 0,
      firstAcquiredAt: null,
      acquiredOrder: null
    };
  }
  const entry = collection[imageId];
  const definition = providedDefinition || getBonusGachaImageDefinition(imageId);
  if (!definition) {
    markBonusGachaImageMissing(imageId, definition);
    return null;
  }
  if (!skipAssetCheck && !isBonusGachaImageAssetAvailable(definition)) {
    markBonusGachaImageMissing(imageId, definition);
    return null;
  }
  const previousCount = Number(entry.count) || 0;
  const newCount = previousCount + 1;
  entry.count = newCount;
  if (previousCount === 0) {
    const now = Date.now();
    if (!Number.isFinite(Number(entry.firstAcquiredAt)) || Number(entry.firstAcquiredAt) <= 0) {
      entry.firstAcquiredAt = now;
    }
    let counter = Number(gameState.gachaBonusImageAcquisitionCounter);
    if (!Number.isFinite(counter) || counter < 0) {
      counter = 0;
    }
    counter = Math.floor(counter) + 1;
    entry.acquiredOrder = counter;
    gameState.gachaBonusImageAcquisitionCounter = counter;
  }
  if (typeof updateCollectionBonusImagesVisibility === 'function') {
    updateCollectionBonusImagesVisibility();
  }
  if (typeof updateCollectionBonus2ImagesVisibility === 'function') {
    updateCollectionBonus2ImagesVisibility();
  }
  if (typeof updateCollectionBonusImagesVisibility === 'function') {
    updateCollectionBonusImagesVisibility();
  }
  if (typeof updateCollectionBonus2ImagesVisibility === 'function') {
    updateCollectionBonus2ImagesVisibility();
  }
  const label = resolveBonusGachaImageLabel(imageId);
  return {
    cardId: imageId,
    definition,
    label,
    assetPath: definition?.assetPath || null,
    count: newCount,
    isNew: previousCount === 0,
    type: 'image',
    collectionType: definition?.collectionType || 'optional'
  };
}

function awardPermanentBonusGachaImage(imageId, options = null) {
  if (!isGachaBonusImageCollectionEnabled()) {
    return null;
  }
  if (!imageId) {
    return null;
  }
  const { definition: providedDefinition = null, skipAssetCheck = false } = options || {};
  const collection = ensureGachaBonusImageCollection();
  if (!collection[imageId]) {
    collection[imageId] = {
      id: imageId,
      count: 0,
      firstAcquiredAt: null,
      acquiredOrder: null
    };
  }
  const entry = collection[imageId];
  const definition = providedDefinition || getBonusGachaImageDefinition(imageId);
  if (!definition) {
    markBonusGachaImageMissing(imageId, definition);
    return null;
  }
  if (!skipAssetCheck && !isBonusGachaImageAssetAvailable(definition)) {
    markBonusGachaImageMissing(imageId, definition);
    return null;
  }
  const previousCount = Number(entry.count) || 0;
  const newCount = previousCount + 1;
  entry.count = newCount;
  if (previousCount === 0) {
    const now = Date.now();
    if (!Number.isFinite(Number(entry.firstAcquiredAt)) || Number(entry.firstAcquiredAt) <= 0) {
      entry.firstAcquiredAt = now;
    }
    let counter = Number(gameState.gachaBonusImageAcquisitionCounter);
    if (!Number.isFinite(counter) || counter < 0) {
      counter = 0;
    }
    counter = Math.floor(counter) + 1;
    entry.acquiredOrder = counter;
    gameState.gachaBonusImageAcquisitionCounter = counter;
  }
  if (typeof updateCollectionBonusImagesVisibility === 'function') {
    updateCollectionBonusImagesVisibility();
  }
  if (typeof updateCollectionBonus2ImagesVisibility === 'function') {
    updateCollectionBonus2ImagesVisibility();
  }
  const label = resolveBonusGachaImageLabel(imageId);
  return {
    cardId: imageId,
    definition,
    label,
    assetPath: definition?.assetPath || null,
    count: newCount,
    isNew: previousCount === 0,
    type: 'image',
    collectionType: definition?.collectionType || 'permanent'
  };
}

function awardCollectionVideo(videoId) {
  if (!isCollectionVideoCollectionEnabled()) {
    return null;
  }
  if (!videoId) {
    return null;
  }
  const collection = ensureCollectionVideoCollection();
  if (!collection[videoId]) {
    collection[videoId] = {
      id: videoId,
      count: 0,
      firstAcquiredAt: null,
      acquiredOrder: null
    };
  }
  const entry = collection[videoId];
  const previousCount = Number(entry.count) || 0;
  if (previousCount > 0) {
    return null;
  }
  const newCount = 1;
  entry.count = newCount;
  if (previousCount === 0) {
    const now = Date.now();
    if (!Number.isFinite(Number(entry.firstAcquiredAt)) || Number(entry.firstAcquiredAt) <= 0) {
      entry.firstAcquiredAt = now;
    }
    if (!Number.isFinite(Number(entry.acquiredOrder)) || Number(entry.acquiredOrder) <= 0) {
      const existingOrders = Object.values(collection)
        .map(item => Number(item?.acquiredOrder))
        .filter(value => Number.isFinite(value) && value > 0);
      const maxOrder = existingOrders.length ? Math.max(...existingOrders) : 0;
      entry.acquiredOrder = maxOrder + 1;
    }
  }
  const definition = getCollectionVideoDefinitionForGacha(videoId);
  const label = resolveCollectionVideoLabelForGacha(videoId);
  const assetPath = definition && typeof definition.assetPath === 'string' && definition.assetPath.trim()
    ? definition.assetPath
    : null;
  const posterPath = definition && typeof definition.posterPath === 'string' && definition.posterPath.trim()
    ? definition.posterPath
    : null;
  const autoplay = definition ? definition.autoplay !== false : true;
  const loop = definition ? definition.loop !== false : true;
  const muted = definition ? definition.muted !== false : true;
  try {
    if (typeof globalThis !== 'undefined'
      && typeof globalThis.persistCollectionVideoUnlockState === 'function') {
      globalThis.persistCollectionVideoUnlockState(true);
    }
    if (typeof globalThis !== 'undefined'
      && typeof globalThis.syncCollectionVideoStateSnapshot === 'function') {
      globalThis.syncCollectionVideoStateSnapshot();
    }
  } catch (error) {
    console.warn('Unable to persist collection video state', error);
  }
  return {
    cardId: videoId,
    definition,
    label,
    assetPath,
    posterPath,
    autoplay,
    loop,
    muted,
    count: newCount,
    isNew: previousCount === 0,
    type: 'video',
    collectionType: 'video'
  };
}

function maybeAwardElementSpecialCard(elementDef) {
  if (!isGachaCardCollectionEnabled()) {
    return null;
  }
  if (!elementDef) {
    return null;
  }
  const gachaId = typeof elementDef.gachaId === 'string' ? elementDef.gachaId.trim() : '';
  if (!gachaId) {
    return null;
  }
  const cardId = SPECIAL_GACHA_ELEMENT_CARD_MAP[gachaId];
  if (!cardId) {
    return null;
  }
  if (!rollForSpecialGachaCard()) {
    return null;
  }
  return awardSpecialGachaCard(cardId);
}

function maybeAwardRaritySpecialCard(rarity) {
  if (!isGachaCardCollectionEnabled()) {
    return null;
  }
  const rarityId = typeof rarity === 'string'
    ? rarity
    : (typeof rarity?.id === 'string' ? rarity.id : '');
  if (!rarityId) {
    return null;
  }
  const candidates = SPECIAL_GACHA_RARITY_CARD_MAP[rarityId];
  if (!Array.isArray(candidates) || candidates.length === 0) {
    return null;
  }
  if (!rollForSpecialGachaCard()) {
    return null;
  }
  let cardId = candidates[0];
  if (candidates.length > 1) {
    const index = Math.floor(Math.random() * candidates.length);
    cardId = candidates[index];
  }
  return awardSpecialGachaCard(cardId);
}

function maybeAwardBonusGachaImage() {
  if (!isGachaImageCollectionEnabled()) {
    return null;
  }
  const availableIds = getAvailableBonusGachaImageIds();
  if (!availableIds.length) {
    return null;
  }
  if (!rollForBonusGachaImage()) {
    return null;
  }
  const shuffledIds = availableIds.slice();
  for (let i = shuffledIds.length - 1; i > 0; i -= 1) {
    const swapIndex = Math.floor(Math.random() * (i + 1));
    const temp = shuffledIds[i];
    shuffledIds[i] = shuffledIds[swapIndex];
    shuffledIds[swapIndex] = temp;
  }
  const batchSize = Math.max(1, Math.floor(BONUS_GACHA_IMAGE_AVAILABILITY_BATCH_SIZE));
  for (let offset = 0; offset < shuffledIds.length; offset += batchSize) {
    const batch = shuffledIds.slice(offset, offset + batchSize);
    for (let index = 0; index < batch.length; index += 1) {
      const imageId = batch[index];
      if (!imageId) {
        continue;
      }
      const definition = getBonusGachaImageDefinition(imageId);
      if (!definition || !isBonusGachaImageAssetAvailable(definition)) {
        markBonusGachaImageMissing(imageId, definition);
        continue;
      }
      const reward = awardBonusGachaImage(imageId, { definition, skipAssetCheck: true });
      if (reward) {
        return reward;
      }
      markBonusGachaImageMissing(imageId, definition);
    }
  }
  return null;
}

function maybeAwardPermanentBonusGachaImage() {
  if (!isGachaBonusImageCollectionEnabled()) {
    return null;
  }
  const availableIds = getAvailablePermanentBonusGachaImageIds();
  if (!availableIds.length) {
    return null;
  }
  if (!rollForPermanentBonusGachaImage()) {
    return null;
  }
  const shuffledIds = availableIds.slice();
  for (let i = shuffledIds.length - 1; i > 0; i -= 1) {
    const swapIndex = Math.floor(Math.random() * (i + 1));
    const temp = shuffledIds[i];
    shuffledIds[i] = shuffledIds[swapIndex];
    shuffledIds[swapIndex] = temp;
  }
  for (let index = 0; index < shuffledIds.length; index += 1) {
    const imageId = shuffledIds[index];
    if (!imageId) {
      continue;
    }
    const definition = getBonusGachaImageDefinition(imageId);
    if (!definition || !isBonusGachaImageAssetAvailable(definition)) {
      markBonusGachaImageMissing(imageId, definition);
      continue;
    }
    const reward = awardPermanentBonusGachaImage(imageId, { definition, skipAssetCheck: true });
    if (reward) {
      return reward;
    }
    markBonusGachaImageMissing(imageId, definition);
  }
  return null;
}

function maybeAwardSecondaryPermanentBonusGachaImage() {
  if (!isGachaBonusImageCollectionEnabled()) {
    return null;
  }
  const availableIds = getAvailableSecondaryPermanentBonusGachaImageIds();
  if (!availableIds.length) {
    return null;
  }
  if (!rollForSecondaryPermanentBonusGachaImage()) {
    return null;
  }
  const shuffledIds = availableIds.slice();
  for (let i = shuffledIds.length - 1; i > 0; i -= 1) {
    const swapIndex = Math.floor(Math.random() * (i + 1));
    const temp = shuffledIds[i];
    shuffledIds[i] = shuffledIds[swapIndex];
    shuffledIds[swapIndex] = temp;
  }
  for (let index = 0; index < shuffledIds.length; index += 1) {
    const imageId = shuffledIds[index];
    if (!imageId) {
      continue;
    }
    const definition = getBonusGachaImageDefinition(imageId);
    if (!definition || !isBonusGachaImageAssetAvailable(definition)) {
      markBonusGachaImageMissing(imageId, definition);
      continue;
    }
    const reward = awardPermanentBonusGachaImage(imageId, { definition, skipAssetCheck: true });
    if (reward) {
      return reward;
    }
    markBonusGachaImageMissing(imageId, definition);
  }
  return null;
}

function maybeAwardCollectionVideo() {
  if (!isCollectionVideoCollectionEnabled()) {
    return null;
  }
  const availableIds = getAvailableCollectionVideoIds();
  if (!availableIds.length) {
    return null;
  }
  if (!rollForCollectionVideoReward()) {
    return null;
  }
  const index = Math.floor(Math.random() * availableIds.length);
  const videoId = availableIds[index];
  if (!videoId) {
    return null;
  }
  return awardCollectionVideo(videoId);
}

const DEFAULT_GACHA_RARITIES = [
  {
    id: 'commun',
    labelKey: 'scripts.gacha.rarities.commun.label',
    descriptionKey: 'scripts.gacha.rarities.commun.description',
    weight: 55,
    color: '#4f7ec2'
  },
  {
    id: 'essentiel',
    labelKey: 'scripts.gacha.rarities.essentiel.label',
    descriptionKey: 'scripts.gacha.rarities.essentiel.description',
    weight: 20,
    color: '#4ba88c'
  },
  {
    id: 'stellaire',
    labelKey: 'scripts.gacha.rarities.stellaire.label',
    descriptionKey: 'scripts.gacha.rarities.stellaire.description',
    weight: 12,
    color: '#8caf58'
  },
  {
    id: 'singulier',
    labelKey: 'scripts.gacha.rarities.singulier.label',
    descriptionKey: 'scripts.gacha.rarities.singulier.description',
    weight: 7,
    color: '#d08a54'
  },
  {
    id: 'mythique',
    labelKey: 'scripts.gacha.rarities.mythique.label',
    descriptionKey: 'scripts.gacha.rarities.mythique.description',
    weight: 4,
    color: '#c46a9a'
  },
  {
    id: 'irreel',
    labelKey: 'scripts.gacha.rarities.irreel.label',
    descriptionKey: 'scripts.gacha.rarities.irreel.description',
    weight: 2,
    color: '#7d6fc9'
  }
].map(entry => ({
  id: entry.id,
  weight: entry.weight,
  color: entry.color,
  label: gachaTranslate(entry.labelKey),
  description: gachaTranslate(entry.descriptionKey)
}));

function sanitizeGachaRarities(rawRarities) {
  const base = Array.isArray(rawRarities) && rawRarities.length
    ? rawRarities
    : DEFAULT_GACHA_RARITIES;
  const seen = new Set();
  const sanitized = [];
  base.forEach(entry => {
    if (!entry || typeof entry !== 'object') return;
    const rawId = entry.id ?? entry.rarity ?? entry.key;
    if (!rawId) return;
    const id = String(rawId).trim();
    if (!id || seen.has(id)) return;
    const weightValue = Number(entry.weight ?? entry.rate ?? entry.dropRate ?? entry.chance ?? 0);
    sanitized.push({
      id,
      label: entry.label ? String(entry.label) : id,
      description: entry.description ? String(entry.description) : '',
      weight: Number.isFinite(weightValue) ? weightValue : 0,
      color: entry.color ? String(entry.color) : null
    });
    seen.add(id);
  });
  if (!sanitized.length) {
    return DEFAULT_GACHA_RARITIES.map(entry => ({ ...entry }));
  }
  return sanitized;
}

const rawGachaConfig = CONFIG.gacha && typeof CONFIG.gacha === 'object'
  ? CONFIG.gacha
  : {};

const GACHA_TICKET_COST = Math.max(
  1,
  Math.floor(
    Number(
      rawGachaConfig.ticketCost
        ?? rawGachaConfig.cost
        ?? DEFAULT_GACHA_TICKET_COST
    ) || DEFAULT_GACHA_TICKET_COST
  )
);

function localizeRarityEntry(entry) {
  if (!entry || typeof entry !== 'object') {
    return {
      id: '',
      label: '',
      labelFallback: '',
      description: '',
      descriptionFallback: '',
      labelKey: null,
      descriptionKey: null,
      weight: 0,
      color: null
    };
  }
  const id = typeof entry.id === 'string' ? entry.id.trim() : '';
  const baseKey = id ? `scripts.gacha.rarities.${id}` : '';
  const fallbackLabel = typeof entry.label === 'string' && entry.label.trim()
    ? entry.label.trim()
    : id;
  const fallbackDescription = typeof entry.description === 'string' && entry.description.trim()
    ? entry.description.trim()
    : '';
  return {
    ...entry,
    id,
    label: fallbackLabel,
    labelFallback: fallbackLabel,
    labelKey: baseKey ? `${baseKey}.label` : null,
    description: fallbackDescription,
    descriptionFallback: fallbackDescription,
    descriptionKey: baseKey ? `${baseKey}.description` : null,
    weight: Math.max(0, Number(entry.weight) || 0),
    color: entry.color ? String(entry.color) : null
  };
}

const BASE_GACHA_RARITIES = sanitizeGachaRarities(rawGachaConfig.rarities).map(localizeRarityEntry);

BASE_GACHA_RARITIES.forEach(entry => {
  configuredRarityIds.delete(entry.id);
});

configuredRarityIds.forEach(id => {
  const fallback = { id, label: id, description: '', weight: 0, color: null };
  BASE_GACHA_RARITIES.push(fallback);
});

const BASE_GACHA_RARITY_ID_SET = new Set(BASE_GACHA_RARITIES.map(entry => entry.id));

function sanitizeGachaCollectionUnlocks(rawUnlocks) {
  if (!Array.isArray(rawUnlocks) || !rawUnlocks.length) {
    return [];
  }
  const sanitized = [];
  rawUnlocks.forEach(entry => {
    if (!entry || typeof entry !== 'object') {
      return;
    }
    const thresholdSource = entry.drawThreshold
      ?? entry.drawCount
      ?? entry.threshold
      ?? entry.minDraw
      ?? entry.start
      ?? entry.unlockAt
      ?? entry.after
      ?? entry.from;
    const thresholdValue = Number(thresholdSource);
    if (!Number.isFinite(thresholdValue) || thresholdValue < 0) {
      return;
    }
    const normalizedThreshold = Math.max(0, Math.floor(thresholdValue));
    let allowedCount = null;
    const countCandidates = [
      entry.allowedRarityCount,
      entry.rarityCount,
      entry.maxRarityCount,
      entry.maxCollections,
      entry.collections
    ];
    for (let index = 0; index < countCandidates.length; index += 1) {
      const candidate = countCandidates[index];
      if (candidate == null) {
        continue;
      }
      if (typeof candidate === 'string') {
        const normalized = candidate.trim().toLowerCase();
        if (normalized === 'all' || normalized === 'any' || normalized === 'full') {
          allowedCount = null;
          break;
        }
      }
      const numeric = Number(candidate);
      if (!Number.isFinite(numeric) || numeric <= 0) {
        continue;
      }
      allowedCount = Math.floor(numeric);
      break;
    }
    if (allowedCount != null) {
      const totalRarities = BASE_GACHA_RARITIES.length;
      if (totalRarities > 0) {
        allowedCount = Math.min(Math.max(1, allowedCount), totalRarities);
      } else {
        allowedCount = Math.max(1, allowedCount);
      }
    }
    if (allowedCount == null) {
      const maxIndexCandidates = [entry.maxRarityIndex, entry.maxIndex];
      for (let index = 0; index < maxIndexCandidates.length; index += 1) {
        const candidate = maxIndexCandidates[index];
        if (candidate == null) {
          continue;
        }
        const numeric = Number(candidate);
        if (!Number.isFinite(numeric) || numeric < 0) {
          continue;
        }
        const normalizedIndex = Math.floor(numeric);
        const totalRarities = BASE_GACHA_RARITIES.length;
        if (totalRarities > 0) {
          allowedCount = Math.min(normalizedIndex + 1, totalRarities);
        } else {
          allowedCount = normalizedIndex + 1;
        }
        break;
      }
      if (allowedCount != null) {
        allowedCount = Math.max(1, allowedCount);
      }
    }
    sanitized.push({
      drawThreshold: normalizedThreshold,
      allowedRarityCount: allowedCount
    });
  });
  sanitized.sort((a, b) => a.drawThreshold - b.drawThreshold);
  const deduped = [];
  sanitized.forEach(entry => {
    if (!deduped.length || deduped[deduped.length - 1].drawThreshold !== entry.drawThreshold) {
      deduped.push(entry);
    } else {
      deduped[deduped.length - 1] = entry;
    }
  });
  return deduped;
}

const GACHA_COLLECTION_UNLOCKS = sanitizeGachaCollectionUnlocks(rawGachaConfig.collectionUnlocks);

const WEEKDAY_KEYS = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];

const GACHA_FEATURED_LABEL_KEYS_BY_DAY = Object.freeze({
  monday: 'scripts.gacha.featured.monday',
  thursday: 'scripts.gacha.featured.thursday',
  tuesday: 'scripts.gacha.featured.tuesday',
  friday: 'scripts.gacha.featured.friday',
  wednesday: 'scripts.gacha.featured.wednesday',
  saturday: 'scripts.gacha.featured.saturday'
});

function sanitizeWeeklyRarityWeights(rawWeights) {
  const sanitized = {};
  WEEKDAY_KEYS.forEach(day => {
    sanitized[day] = {};
  });
  if (!rawWeights || typeof rawWeights !== 'object') {
    return sanitized;
  }
  Object.entries(rawWeights).forEach(([rawDay, value]) => {
    if (!rawDay) return;
    const dayKey = WEEKDAY_KEYS.find(day => day === String(rawDay).toLowerCase());
    if (!dayKey) return;
    if (!value || typeof value !== 'object') return;
    Object.entries(value).forEach(([rawRarity, rawWeight]) => {
      if (!rawRarity) return;
      const rarityId = String(rawRarity).trim();
      if (!rarityId) return;
      if (BASE_GACHA_RARITY_ID_SET.size && !BASE_GACHA_RARITY_ID_SET.has(rarityId)) {
        return;
      }
      const numericWeight = Number(rawWeight);
      if (!Number.isFinite(numericWeight)) return;
      sanitized[dayKey][rarityId] = numericWeight;
    });
  });
  return sanitized;
}

const WEEKLY_RARITY_WEIGHTS = sanitizeWeeklyRarityWeights(
  rawGachaConfig.weeklyRarityWeights
    ?? rawGachaConfig.weeklyWeights
    ?? rawGachaConfig.dailyRarityWeights
    ?? {}
);

function getEffectiveGachaRaritiesForDate(date = new Date()) {
  const targetDate = date instanceof Date && !Number.isNaN(date.getTime())
    ? date
    : new Date();
  const dayKey = WEEKDAY_KEYS[targetDate.getDay()] ?? null;
  const overrides = dayKey ? WEEKLY_RARITY_WEIGHTS[dayKey] ?? {} : {};
  const effective = BASE_GACHA_RARITIES.map(entry => {
    const hasOverride = Object.prototype.hasOwnProperty.call(overrides, entry.id);
    const overrideValue = hasOverride ? Number(overrides[entry.id]) : null;
    const resolvedWeight = Number.isFinite(overrideValue) ? overrideValue : entry.weight;
    return {
      ...entry,
      weight: Math.max(0, Number(resolvedWeight) || 0)
    };
  });
  if (dayKey) {
    Object.entries(overrides).forEach(([rarityId, rawWeight]) => {
      if (BASE_GACHA_RARITY_ID_SET.has(rarityId)) {
        return;
      }
      const numericWeight = Number(rawWeight);
      if (!Number.isFinite(numericWeight)) {
        return;
      }
      effective.push({
        id: rarityId,
        label: rarityId,
        description: '',
        weight: Math.max(0, numericWeight),
        color: null
      });
    });
  }
  return effective;
}

const GACHA_RARITY_MAP = new Map();
let activeGachaWeightDayKey = null;

function refreshGachaRarities(date = new Date(), { force = false } = {}) {
  const targetDate = date instanceof Date && !Number.isNaN(date.getTime())
    ? date
    : new Date();
  const dayKey = WEEKDAY_KEYS[targetDate.getDay()] ?? null;
  if (!force && dayKey && dayKey === activeGachaWeightDayKey) {
    return false;
  }
  const effective = getEffectiveGachaRaritiesForDate(targetDate);
  effective.forEach(applyLocalizedRarity);
  GACHA_RARITIES = effective;
  GACHA_RARITY_MAP.clear();
  GACHA_RARITIES.forEach(entry => {
    GACHA_RARITY_MAP.set(entry.id, entry);
  });
  refreshGachaRarityLocalization({ force: true });
  activeGachaWeightDayKey = dayKey;
  updateGachaFeaturedInfo(dayKey);
  return true;
}

function getGachaFeaturedLabelForDayKey(dayKey) {
  if (!dayKey) {
    return null;
  }
  const key = GACHA_FEATURED_LABEL_KEYS_BY_DAY[dayKey] ?? null;
  return key ? t(key) : null;
}

function updateGachaFeaturedInfo(dayKey = WEEKDAY_KEYS[new Date().getDay()] ?? null) {
  if (typeof elements === 'undefined' || !elements) {
    return;
  }
  const featuredInfo = elements.gachaFeaturedInfo;
  if (!featuredInfo) {
    return;
  }
  const label = getGachaFeaturedLabelForDayKey(dayKey);
  if (label) {
    const normalizedDayKey = typeof dayKey === 'string' ? dayKey : '';
    const previousLabel = featuredInfo.dataset?.featuredLabel ?? '';
    const previousDayKey = featuredInfo.dataset?.featuredDayKey ?? '';
    if (previousLabel === label && previousDayKey === normalizedDayKey) {
      if (featuredInfo.hidden) {
        featuredInfo.hidden = false;
      }
      const activeSmokeElement = gachaSmokeAnimationState.element;
      const hasSmokeElement = featuredInfo.querySelector('.gacha-featured-info__smoke');
      if (!hasSmokeElement || (activeSmokeElement && activeSmokeElement !== hasSmokeElement)) {
        const smokeBackdrop = createGachaFeaturedSmokeBackdrop();
        if (smokeBackdrop) {
          featuredInfo.prepend(smokeBackdrop);
        }
      }
      return;
    }
    stopGachaFeaturedSmokeAnimation();
    featuredInfo.innerHTML = '';
    const todayText = t('scripts.gacha.featured.today');
    const fragment = document.createDocumentFragment();
    const smokeBackdrop = createGachaFeaturedSmokeBackdrop();
    if (smokeBackdrop) {
      fragment.appendChild(smokeBackdrop);
    }
    if (typeof todayText === 'string' && todayText.trim()) {
      const dayElement = document.createElement('span');
      dayElement.className = 'gacha-featured-info__day';
      dayElement.textContent = todayText.trim();
      fragment.appendChild(dayElement);
    }
    const labelElement = document.createElement('span');
    labelElement.className = 'gacha-featured-info__label';
    labelElement.textContent = label;
    fragment.appendChild(labelElement);
    featuredInfo.appendChild(fragment);
    if (featuredInfo.dataset) {
      featuredInfo.dataset.featuredLabel = label;
      featuredInfo.dataset.featuredDayKey = normalizedDayKey;
    }
    featuredInfo.hidden = false;
  } else {
    stopGachaFeaturedSmokeAnimation();
    featuredInfo.innerHTML = '';
    if (featuredInfo.dataset) {
      delete featuredInfo.dataset.featuredLabel;
      delete featuredInfo.dataset.featuredDayKey;
    }
    featuredInfo.hidden = true;
  }
}

function getCurrentGachaTotalWeight() {
  return GACHA_RARITIES.reduce((total, entry) => total + (entry.weight || 0), 0);
}

const RARITY_IDS = BASE_GACHA_RARITIES.map(entry => entry.id);
const GACHA_RARITY_ORDER = new Map(RARITY_IDS.map((id, index) => [id, index]));
const RARITY_LABEL_MAP = new Map();
const INFO_BONUS_RARITIES = RARITY_IDS.length > 0
  ? [...RARITY_IDS]
  : ['commun', 'essentiel', 'stellaire', 'singulier', 'mythique', 'irreel'];
let INFO_BONUS_SUBTITLE = '';
let lastLocalizedRarityLanguage = null;
let GACHA_RARITIES = [];

function applyLocalizedRarity(entry) {
  if (!entry || typeof entry !== 'object') {
    return entry;
  }
  const labelFallback = typeof entry.labelFallback === 'string' && entry.labelFallback.trim()
    ? entry.labelFallback.trim()
    : (typeof entry.label === 'string' ? entry.label : entry.id);
  const descriptionFallback = typeof entry.descriptionFallback === 'string' && entry.descriptionFallback.trim()
    ? entry.descriptionFallback.trim()
    : (typeof entry.description === 'string' ? entry.description : '');
  const label = entry.labelKey
    ? translateWithFallback(entry.labelKey, labelFallback)
    : labelFallback;
  const description = entry.descriptionKey
    ? translateWithFallback(entry.descriptionKey, descriptionFallback)
    : descriptionFallback;
  entry.labelFallback = labelFallback;
  entry.descriptionFallback = descriptionFallback;
  entry.label = typeof label === 'string' && label.trim() ? label.trim() : labelFallback;
  entry.description = typeof description === 'string' && description.trim()
    ? description.trim()
    : descriptionFallback;
  return entry;
}

function getActiveLanguageCode() {
  const api = getI18nApi();
  if (api && typeof api.getCurrentLanguage === 'function') {
    const lang = api.getCurrentLanguage();
    if (typeof lang === 'string' && lang.trim()) {
      return lang.trim();
    }
  }
  if (typeof document !== 'undefined' && document.documentElement?.lang) {
    return document.documentElement.lang;
  }
  return '';
}

function updateLocalizedRarityData({ includeEffective = false, force = false } = {}) {
  const currentLanguage = getActiveLanguageCode();
  if (!force && currentLanguage && currentLanguage === lastLocalizedRarityLanguage) {
    return;
  }
  BASE_GACHA_RARITIES.forEach(applyLocalizedRarity);
  if (includeEffective && Array.isArray(GACHA_RARITIES)) {
    GACHA_RARITIES.forEach(applyLocalizedRarity);
  }
  RARITY_LABEL_MAP.clear();
  BASE_GACHA_RARITIES.forEach(entry => {
    RARITY_LABEL_MAP.set(entry.id, entry.label || entry.id);
  });
  if (INFO_BONUS_RARITIES.length) {
    INFO_BONUS_SUBTITLE = INFO_BONUS_RARITIES
      .map(id => RARITY_LABEL_MAP.get(id) || id)
      .join(' · ');
  } else {
    INFO_BONUS_SUBTITLE = translateWithFallback(
      'scripts.gacha.rarityProgress.unavailable',
      'Rarities unavailable'
    );
  }
  const collectionStep = PRODUCTION_STEP_DEFINITIONS.get('collectionMultiplier');
  if (collectionStep) {
    collectionStep.label = translateWithFallback(
      typeof COLLECTION_MULTIPLIER_LABEL_KEY === 'string'
        ? COLLECTION_MULTIPLIER_LABEL_KEY
        : 'scripts.config.elementBonuses.collectionMultiplier',
      'Multiplicateur de collection'
    );
  }
  RARITY_IDS.forEach(rarityId => {
    const stepId = `rarityMultiplier:${rarityId}`;
    const step = PRODUCTION_STEP_DEFINITIONS.get(stepId);
    if (step) {
      const rarityLabel = RARITY_LABEL_MAP.get(rarityId) || rarityId;
      step.label = `Rareté ${rarityLabel}`;
    }
  });
  lastLocalizedRarityLanguage = currentLanguage || null;
}

function refreshGachaRarityLocalization(options = {}) {
  updateLocalizedRarityData({ includeEffective: true, ...options });
  return {
    labelMap: RARITY_LABEL_MAP,
    subtitle: INFO_BONUS_SUBTITLE
  };
}

const rawTicketStarConfig = CONFIG.ticketStar && typeof CONFIG.ticketStar === 'object'
  ? CONFIG.ticketStar
  : {};

const TICKET_STAR_CONFIG = {
  averageSpawnIntervalMs: (() => {
    const raw = Number(
      rawTicketStarConfig.averageSpawnIntervalSeconds
        ?? rawTicketStarConfig.averageIntervalSeconds
        ?? 60
    );
    const seconds = Number.isFinite(raw) && raw > 0 ? raw : 60;
    return seconds * 1000;
  })(),
  minimumSpawnIntervalMs: (() => {
    const raw = Number(
      rawTicketStarConfig.minimumSpawnIntervalSeconds
        ?? rawTicketStarConfig.minIntervalSeconds
        ?? 5
    );
    const seconds = Number.isFinite(raw) && raw > 0 ? raw : 5;
    return Math.max(1000, seconds * 1000);
  })(),
  clickReductionPerClickMs: (() => {
    const raw = Number(
      rawTicketStarConfig.clickReductionSeconds
        ?? rawTicketStarConfig.clickReduction
        ?? 0.1
    );
    if (!Number.isFinite(raw) || raw <= 0) {
      return 0;
    }
    return raw * 1000;
  })(),
  lifetimeMs: (() => {
    const raw = Number(
      rawTicketStarConfig.lifetimeSeconds
        ?? rawTicketStarConfig.lifetime
        ?? 15
    );
    if (!Number.isFinite(raw) || raw <= 0) {
      return Number.POSITIVE_INFINITY;
    }
    return raw * 1000;
  })(),
  movementMode: (() => {
    const raw = rawTicketStarConfig.movementMode ?? rawTicketStarConfig.motionMode ?? rawTicketStarConfig.motion;
    if (typeof raw === 'string') {
      const normalized = raw.trim().toLowerCase();
      if (normalized === 'static' || normalized === 'fixe') {
        return 'static';
      }
      if (normalized === 'physics' || normalized === 'dynamic' || normalized === 'dynamique') {
        return 'physics';
      }
    } else if (raw === false) {
      return 'static';
    }
    return 'physics';
  })(),
  speed: (() => {
    const raw = Number(
      rawTicketStarConfig.speedPixelsPerSecond
        ?? rawTicketStarConfig.speed
        ?? 90
    );
    return Number.isFinite(raw) && raw > 0 ? raw : 90;
  })(),
  speedVariance: (() => {
    const raw = Number(
      rawTicketStarConfig.speedVarianceFactor
        ?? rawTicketStarConfig.speedVariance
        ?? rawTicketStarConfig.speedVariancePercent
        ?? 0
    );
    if (!Number.isFinite(raw)) {
      return 0;
    }
    const absolute = Math.abs(raw);
    if (absolute <= 0) {
      return 0;
    }
    return Math.min(absolute, 0.95);
  })(),
  edgePadding: (() => {
    const raw = Number(
      rawTicketStarConfig.spawnOffsetPixels
        ?? rawTicketStarConfig.edgePaddingPixels
        ?? rawTicketStarConfig.edgePadding
        ?? 0
    );
    return Number.isFinite(raw) && raw >= 0 ? raw : 0;
  })(),
  size: (() => {
    const raw = Number(rawTicketStarConfig.size ?? rawTicketStarConfig.spriteSize ?? 72);
    return Number.isFinite(raw) && raw > 0 ? raw : 72;
  })(),
  rewardTickets: (() => {
    const raw = Number(rawTicketStarConfig.rewardTickets ?? rawTicketStarConfig.tickets ?? 1);
    return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1;
  })(),
  spriteSources: (() => {
    const spriteConfig = rawTicketStarConfig.sprite ?? rawTicketStarConfig.sprites ?? {};
    const staticSprite = typeof spriteConfig.static === 'string'
      ? spriteConfig.static
      : typeof rawTicketStarConfig.sprite === 'string'
        ? rawTicketStarConfig.sprite
        : 'Assets/Image/Star.png';
    const animatedSprite = typeof spriteConfig.animated === 'string'
      ? spriteConfig.animated
      : typeof rawTicketStarConfig.animatedSprite === 'string'
        ? rawTicketStarConfig.animatedSprite
        : null;
    return {
      static: staticSprite,
      animated: animatedSprite
    };
  })(),
  defaultSpriteId: (() => {
    const spriteConfig = rawTicketStarConfig.sprite ?? rawTicketStarConfig.sprites ?? {};
    const raw = spriteConfig.defaultSpriteId
      ?? spriteConfig.defaultSprite
      ?? rawTicketStarConfig.defaultSpriteId
      ?? rawTicketStarConfig.defaultSprite;
    if (typeof raw === 'string') {
      const normalized = raw.trim().toLowerCase();
      if (normalized === 'animated' || normalized === 'gif' || normalized === 'anim') {
        return 'animated';
      }
    }
    return 'static';
  })(),
  gravity: (() => {
    const raw = Number(rawTicketStarConfig.gravity ?? rawTicketStarConfig.gravityPixelsPerSecondSquared ?? 900);
    return Number.isFinite(raw) ? raw : 900;
  })(),
  bounceRestitution: (() => {
    const raw = Number(rawTicketStarConfig.bounceRestitution ?? rawTicketStarConfig.floorBounce ?? 0.86);
    if (!Number.isFinite(raw)) {
      return 0.86;
    }
    return Math.min(Math.max(raw, 0), 0.98);
  })(),
  wallRestitution: (() => {
    const raw = Number(rawTicketStarConfig.wallRestitution ?? rawTicketStarConfig.wallBounce ?? 0.82);
    if (!Number.isFinite(raw)) {
      return 0.82;
    }
    return Math.min(Math.max(raw, 0), 0.98);
  })(),
  floorFriction: (() => {
    const raw = Number(rawTicketStarConfig.floorFriction ?? rawTicketStarConfig.horizontalFriction ?? 0.9);
    if (!Number.isFinite(raw)) {
      return 0.9;
    }
    return Math.min(Math.max(raw, 0), 1);
  })(),
  launchVerticalSpeed: (() => {
    const raw = Number(
      rawTicketStarConfig.launchVerticalSpeed
        ?? rawTicketStarConfig.initialVerticalSpeed
        ?? 420
    );
    return Number.isFinite(raw) && raw > 0 ? raw : 420;
  })(),
  minHorizontalSpeed: (() => {
    const raw = Number(
      rawTicketStarConfig.minHorizontalSpeed
        ?? rawTicketStarConfig.minimumHorizontalSpeed
        ?? 180
    );
    return Number.isFinite(raw) && raw > 0 ? raw : 180;
  })(),
  horizontalSpeedRange: (() => {
    const baseSpeed = Number(
      rawTicketStarConfig.speedPixelsPerSecond
        ?? rawTicketStarConfig.speed
        ?? 90
    );
    const normalizedBase = Number.isFinite(baseSpeed) && baseSpeed > 0 ? baseSpeed : 90;
    const rawMin = Number(
      rawTicketStarConfig.horizontalSpeedMin
        ?? rawTicketStarConfig.horizontalSpeedRange?.min
    );
    const rawMax = Number(
      rawTicketStarConfig.horizontalSpeedMax
        ?? rawTicketStarConfig.horizontalSpeedRange?.max
    );
    const fallbackMin = normalizedBase * 2.6;
    const fallbackMax = normalizedBase * 4.6;
    const min = Number.isFinite(rawMin) && rawMin > 0 ? rawMin : fallbackMin;
    const max = Number.isFinite(rawMax) && rawMax >= min ? rawMax : Math.max(min, fallbackMax);
    return { min, max };
  })()
};

const TICKET_STAR_SPECIAL_CONFIG = {
  rewardTickets: (() => {
    const special = rawTicketStarConfig.specialStar && typeof rawTicketStarConfig.specialStar === 'object'
      ? rawTicketStarConfig.specialStar
      : {};
    const raw = Number(special.rewardTickets ?? special.tickets ?? 10);
    return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 10;
  })()
};

function clampTicketStarSpecialChance(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  if (numeric > 1) {
    return Math.min(1, numeric / 100);
  }
  return Math.min(1, numeric);
}

function getTicketStarSpecialChance() {
  const raw = gameState.ticketStarSpecialChance ?? 0;
  return clampTicketStarSpecialChance(raw);
}

function getTicketStarSpecialRewardTickets() {
  const raw = gameState.ticketStarSpecialReward ?? TICKET_STAR_SPECIAL_CONFIG.rewardTickets;
  const numeric = Number(raw);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.floor(numeric);
  }
  return TICKET_STAR_SPECIAL_CONFIG.rewardTickets;
}

const DEFAULT_TICKET_STAR_INTERVAL_SECONDS = TICKET_STAR_CONFIG.averageSpawnIntervalMs / 1000;

function isTicketStarStaticMode() {
  return TICKET_STAR_CONFIG.movementMode === 'static';
}

const MYTHIQUE_OFFLINE_BASE = MYTHIQUE_SPECIAL_BONUS_CONFIG.offline.baseMultiplier;
const MYTHIQUE_OFFLINE_PER_DUPLICATE = MYTHIQUE_SPECIAL_BONUS_CONFIG.offline.perDuplicate;
const MYTHIQUE_OFFLINE_CAP = MYTHIQUE_SPECIAL_BONUS_CONFIG.offline.cap;
const MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS = MYTHIQUE_SPECIAL_BONUS_CONFIG.overflow.flatBonus;
const MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS = MYTHIQUE_SPECIAL_BONUS_CONFIG.ticket.minIntervalSeconds;
const MYTHIQUE_TICKET_UNIQUE_REDUCTION_SECONDS = MYTHIQUE_SPECIAL_BONUS_CONFIG.ticket.uniqueReductionSeconds;
const MYTHIQUE_FRENZY_SPAWN_BONUS_MULTIPLIER = MYTHIQUE_SPECIAL_BONUS_CONFIG.frenzy.multiplier;
const MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP = (() => {
  if (
    MYTHIQUE_OFFLINE_PER_DUPLICATE > 0
    && MYTHIQUE_OFFLINE_CAP > MYTHIQUE_OFFLINE_BASE
  ) {
    return Math.ceil((MYTHIQUE_OFFLINE_CAP - MYTHIQUE_OFFLINE_BASE) / MYTHIQUE_OFFLINE_PER_DUPLICATE);
  }
  return Number.POSITIVE_INFINITY;
})();

const PRODUCTION_STEP_DEFINITIONS = new Map();

function defineProductionStep(id, type, label, extra = {}) {
  if (!id || PRODUCTION_STEP_DEFINITIONS.has(id)) {
    return;
  }
  PRODUCTION_STEP_DEFINITIONS.set(id, { id, type, label, ...extra });
}

defineProductionStep('baseFlat', 'base', 'Base flat', { source: 'baseFlat' });
defineProductionStep('shopFlat', 'flat', 'Magasin', { source: 'shopFlat' });
defineProductionStep('elementFlat', 'flat', 'Éléments', { source: 'elementFlat' });
defineProductionStep(
  'fusionFlat',
  'flat',
  translateWithFallback('scripts.gacha.productionSteps.fusionFlat', 'Fusions'),
  { source: 'fusionFlat' }
);
defineProductionStep('frenzy', 'multiplier', 'Frénésie', { source: 'frenzy' });
defineProductionStep(
  'trophyMultiplier',
  'multiplier',
  'Multiplicateur trophées',
  { source: 'trophyMultiplier' }
);
defineProductionStep(
  'bigBangMultiplier',
  'multiplier',
  translateWithFallback('scripts.app.production.bigBangMultiplier', 'Multiplicateur Big Bang'),
  { source: 'bigBang' }
);
defineProductionStep(
  'collectionMultiplier',
  'multiplier',
  translateWithFallback(
    typeof COLLECTION_MULTIPLIER_LABEL_KEY === 'string'
      ? COLLECTION_MULTIPLIER_LABEL_KEY
      : 'scripts.config.elementBonuses.collectionMultiplier',
    'Multiplicateur de collection'
  ),
  { source: 'collectionMultiplier' }
);
defineProductionStep('total', 'total', '= Total');

updateLocalizedRarityData({ includeEffective: false, force: true });

RARITY_IDS.forEach(rarityId => {
  const rarityLabel = RARITY_LABEL_MAP.get(rarityId) || rarityId;
  defineProductionStep(
    `rarityMultiplier:${rarityId}`,
    'multiplier',
    `Rareté ${rarityLabel}`,
    { source: 'rarityMultiplier', rarityId }
  );
});

const DEFAULT_PRODUCTION_STEP_IDS = [
  'baseFlat',
  'shopFlat',
  'fusionFlat',
  'collectionMultiplier',
  'frenzy',
  'trophyMultiplier',
  'bigBangMultiplier',
  'total'
];

function resolveProductionStepOrder(configOrder) {
  const seen = new Set();
  const resolved = [];

  const pushStep = (id, labelOverride = null) => {
    if (!id) return;
    let normalizedId = id;
    if (id === 'rarityMultiplier') {
      normalizedId = 'collectionMultiplier';
    }
    if (normalizedId.startsWith('rarityMultiplier:')) {
      normalizedId = 'collectionMultiplier';
    }
    if (seen.has(normalizedId)) return;
    const base = PRODUCTION_STEP_DEFINITIONS.get(normalizedId);
    if (!base) return;
    const entry = { ...base };
    if (labelOverride && typeof labelOverride === 'string') {
      entry.label = labelOverride;
    }
    resolved.push(entry);
    seen.add(normalizedId);
  };

  if (Array.isArray(configOrder)) {
    configOrder.forEach(item => {
      if (!item) return;
      if (typeof item === 'string') {
        pushStep(item.trim());
        return;
      }
      if (typeof item === 'object') {
        const type = item.type ?? item.kind;
        if ((type === 'rarity' || type === 'rarityMultiplier') && item.rarity) {
          const label = typeof item.label === 'string' ? item.label.trim() : null;
          pushStep('collectionMultiplier', label);
          return;
        }
        const rawId = item.id ?? item.key ?? item.step;
        if (!rawId) return;
        const id = String(rawId).trim();
        const label = typeof item.label === 'string' ? item.label.trim() : null;
        pushStep(id, label);
      }
    });
  }

  DEFAULT_PRODUCTION_STEP_IDS.forEach(id => {
    if (!seen.has(id)) {
      pushStep(id);
    }
  });

  return resolved;
}

const PRODUCTION_STEP_ORDER = resolveProductionStepOrder(CONFIG.infoPanels?.productionOrder);

const gachaPools = new Map();
const gachaRarityRows = new Map();

function rebuildGachaPools() {
  gachaPools.clear();
  GACHA_RARITIES.forEach(entry => {
    gachaPools.set(entry.id, []);
  });
  periodicElements.forEach(def => {
    const rarity = elementRarityIndex.get(def.id);
    if (!rarity) return;
    if (!gachaPools.has(rarity)) {
      gachaPools.set(rarity, []);
    }
    gachaPools.get(rarity).push(def.id);
  });
}

refreshGachaRarities(new Date(), { force: true });
rebuildGachaPools();

function getRarityPoolSize(rarityId) {
  const pool = gachaPools.get(rarityId);
  return Array.isArray(pool) ? pool.length : 0;
}

LayeredNumber.LAYER1_THRESHOLD = CONFIG.numbers?.layer1Threshold ?? 1e6;
LayeredNumber.LAYER1_DOWN = CONFIG.numbers?.layer1Downshift ?? 5;
LayeredNumber.LOG_DIFF_LIMIT = CONFIG.numbers?.logDifferenceLimit ?? 15;
LayeredNumber.EPSILON = CONFIG.numbers?.epsilon ?? 1e-12;

const BASE_PER_CLICK = toLayeredNumber(CONFIG.progression?.basePerClick, 1);
const BASE_PER_SECOND = toLayeredNumber(CONFIG.progression?.basePerSecond, 0);
const DEFAULT_MUSIC_VOLUME = 0.5;
const DEFAULT_MUSIC_ENABLED = true;

function clampMusicVolume(value, fallback = DEFAULT_MUSIC_VOLUME) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  if (numeric <= 0) {
    return 0;
  }
  if (numeric >= 1) {
    return 1;
  }
  return numeric;
}
const OFFLINE_TICKET_CONFIG = normalizeOfflineTicketConfig(CONFIG.progression?.offlineTickets);
const OFFLINE_GAIN_CAP = CONFIG.progression?.offlineCapSeconds ?? 60 * 60 * 12;

function clampCritChance(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  if (numeric >= 1) {
    return 1;
  }
  return numeric;
}

function normalizeCritConfig(raw) {
  const defaults = {
    baseChance: 0.05,
    baseMultiplier: 2,
    maxMultiplier: 100
  };
  if (!raw || typeof raw !== 'object') {
    return { ...defaults };
  }
  const parseNumber = (value, fallback) => {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : fallback;
  };
  const baseChance = clampCritChance(parseNumber(raw.baseChance ?? raw.chance ?? raw.defaultChance, defaults.baseChance));
  const baseMultiplierRaw = parseNumber(
    raw.baseMultiplier ?? raw.multiplier ?? raw.defaultMultiplier,
    defaults.baseMultiplier
  );
  const baseMultiplier = baseMultiplierRaw > 0 ? baseMultiplierRaw : defaults.baseMultiplier;
  const maxMultiplierRaw = parseNumber(raw.maxMultiplier ?? raw.cap ?? raw.maximum, defaults.maxMultiplier);
  const maxMultiplier = Math.max(1, maxMultiplierRaw);
  return {
    baseChance,
    baseMultiplier,
    maxMultiplier: Math.max(baseMultiplier, maxMultiplier)
  };
}

const CRIT_DEFAULTS = normalizeCritConfig(CONFIG.progression?.crit);

function createDefaultCritState() {
  const multiplier = Math.max(1, Math.min(CRIT_DEFAULTS.baseMultiplier, CRIT_DEFAULTS.maxMultiplier));
  return {
    chance: clampCritChance(CRIT_DEFAULTS.baseChance),
    multiplier,
    rawMultiplier: Math.max(1, CRIT_DEFAULTS.baseMultiplier),
    maxMultiplier: Math.max(1, CRIT_DEFAULTS.maxMultiplier)
  };
}

function cloneCritState(state) {
  if (!state || typeof state !== 'object') {
    return createDefaultCritState();
  }
  const baseChance = Number(state.chance ?? state.baseChance);
  const chance = clampCritChance(Number.isFinite(baseChance) ? baseChance : CRIT_DEFAULTS.baseChance);
  const rawMultiplierValue = Number(state.rawMultiplier ?? state.multiplier);
  const rawMultiplier = Number.isFinite(rawMultiplierValue) && rawMultiplierValue > 0
    ? rawMultiplierValue
    : CRIT_DEFAULTS.baseMultiplier;
  const maxMultiplierValue = Number(state.maxMultiplier);
  const maxMultiplier = Number.isFinite(maxMultiplierValue) && maxMultiplierValue > 0
    ? maxMultiplierValue
    : CRIT_DEFAULTS.maxMultiplier;
  const effectiveMultiplier = Math.max(1, Math.min(Number(state.multiplier ?? rawMultiplier) || rawMultiplier, maxMultiplier));
  return {
    chance,
    multiplier: effectiveMultiplier,
    rawMultiplier: Math.max(1, rawMultiplier),
    maxMultiplier: Math.max(1, maxMultiplier)
  };
}

function createCritAccumulator() {
  return {
    chanceAdd: 0,
    chanceMult: 1,
    chanceSet: null,
    multiplierAdd: 0,
    multiplierMult: 1,
    multiplierSet: null,
    maxMultiplierAdd: 0,
    maxMultiplierMult: 1,
    maxMultiplierSet: null
  };
}

function applyCritModifiersFromEffect(accumulator, effect) {
  if (!accumulator || !effect || typeof effect !== 'object') {
    return;
  }
  const readNumber = value => {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
  };
  const nested = effect.crit && typeof effect.crit === 'object' ? effect.crit : null;

  const chanceSetDirect = readNumber(effect.critChanceSet ?? effect.critChance ?? nested?.chanceSet ?? nested?.chance);
  if (chanceSetDirect != null) {
    accumulator.chanceSet = Math.max(0, chanceSetDirect);
  }
  const chanceAdd = readNumber(effect.critChanceAdd ?? nested?.chanceAdd);
  if (chanceAdd != null) {
    accumulator.chanceAdd += chanceAdd;
  }
  const chanceMult = readNumber(effect.critChanceMult ?? nested?.chanceMult);
  if (chanceMult != null) {
    accumulator.chanceMult *= Math.max(0, chanceMult);
  }

  const multiplierSet = readNumber(effect.critMultiplierSet ?? effect.critMultiplier ?? nested?.multiplierSet ?? nested?.multiplier);
  if (multiplierSet != null) {
    accumulator.multiplierSet = Math.max(0, multiplierSet);
  }
  const multiplierAdd = readNumber(effect.critMultiplierAdd ?? nested?.multiplierAdd);
  if (multiplierAdd != null) {
    accumulator.multiplierAdd += multiplierAdd;
  }
  const multiplierMult = readNumber(effect.critMultiplierMult ?? nested?.multiplierMult);
  if (multiplierMult != null) {
    accumulator.multiplierMult *= Math.max(0, multiplierMult);
  }

  const maxMultiplierSet = readNumber(
    effect.critMaxMultiplierSet ?? effect.critMaxMultiplier ?? nested?.maxMultiplierSet ?? nested?.maxMultiplier ?? nested?.cap
  );
  if (maxMultiplierSet != null) {
    accumulator.maxMultiplierSet = Math.max(0, maxMultiplierSet);
  }
  const maxMultiplierAdd = readNumber(effect.critMaxMultiplierAdd ?? nested?.maxMultiplierAdd ?? nested?.capAdd);
  if (maxMultiplierAdd != null) {
    accumulator.maxMultiplierAdd += maxMultiplierAdd;
  }
  const maxMultiplierMult = readNumber(effect.critMaxMultiplierMult ?? nested?.maxMultiplierMult ?? nested?.capMult);
  if (maxMultiplierMult != null) {
    accumulator.maxMultiplierMult *= Math.max(0, maxMultiplierMult);
  }
}

function applyRepeatedCritEffect(accumulator, effectConfig, repetitions) {
  if (!accumulator || !effectConfig) {
    return;
  }
  const times = Math.max(0, Math.floor(Number(repetitions) || 0));
  if (times <= 0) {
    return;
  }
  const effect = {};
  if (effectConfig.chanceSet != null) {
    effect.critChanceSet = Math.max(0, effectConfig.chanceSet);
  }
  if (effectConfig.chanceAdd) {
    effect.critChanceAdd = effectConfig.chanceAdd * times;
  }
  if (effectConfig.chanceMult != null) {
    const base = effectConfig.chanceMult;
    if (base > 0 && base !== 1) {
      effect.critChanceMult = Math.pow(base, times);
    }
  }
  if (effectConfig.multiplierSet != null) {
    effect.critMultiplierSet = Math.max(0, effectConfig.multiplierSet);
  }
  if (effectConfig.multiplierAdd) {
    effect.critMultiplierAdd = effectConfig.multiplierAdd * times;
  }
  if (effectConfig.multiplierMult != null) {
    const base = effectConfig.multiplierMult;
    if (base > 0 && base !== 1) {
      effect.critMultiplierMult = Math.pow(base, times);
    }
  }
  if (effectConfig.maxMultiplierSet != null) {
    effect.critMaxMultiplierSet = Math.max(0, effectConfig.maxMultiplierSet);
  }
  if (effectConfig.maxMultiplierAdd) {
    effect.critMaxMultiplierAdd = effectConfig.maxMultiplierAdd * times;
  }
  if (effectConfig.maxMultiplierMult != null) {
    const base = effectConfig.maxMultiplierMult;
    if (base > 0 && base !== 1) {
      effect.critMaxMultiplierMult = Math.pow(base, times);
    }
  }
  applyCritModifiersFromEffect(accumulator, effect);
}

function finalizeCritEffect(accumulator) {
  if (!accumulator) {
    return null;
  }
  const effect = {};
  if (accumulator.chanceSet != null) {
    effect.critChanceSet = accumulator.chanceSet;
  }
  if (accumulator.chanceAdd !== 0) {
    effect.critChanceAdd = accumulator.chanceAdd;
  }
  if (accumulator.chanceMult !== 1) {
    effect.critChanceMult = accumulator.chanceMult;
  }
  if (accumulator.multiplierSet != null) {
    effect.critMultiplierSet = accumulator.multiplierSet;
  }
  if (accumulator.multiplierAdd !== 0) {
    effect.critMultiplierAdd = accumulator.multiplierAdd;
  }
  if (accumulator.multiplierMult !== 1) {
    effect.critMultiplierMult = accumulator.multiplierMult;
  }
  if (accumulator.maxMultiplierSet != null) {
    effect.critMaxMultiplierSet = accumulator.maxMultiplierSet;
  }
  if (accumulator.maxMultiplierAdd !== 0) {
    effect.critMaxMultiplierAdd = accumulator.maxMultiplierAdd;
  }
  if (accumulator.maxMultiplierMult !== 1) {
    effect.critMaxMultiplierMult = accumulator.maxMultiplierMult;
  }
  return Object.keys(effect).length ? effect : null;
}

function resolveCritState(accumulator) {
  const acc = accumulator || createCritAccumulator();
  const baseChance = acc.chanceSet != null ? acc.chanceSet : CRIT_DEFAULTS.baseChance;
  const chance = clampCritChance((baseChance + acc.chanceAdd) * (acc.chanceMult != null ? acc.chanceMult : 1));

  const baseMultiplier = acc.multiplierSet != null ? acc.multiplierSet : CRIT_DEFAULTS.baseMultiplier;
  let rawMultiplier = baseMultiplier + acc.multiplierAdd;
  rawMultiplier = Math.max(0, rawMultiplier);
  rawMultiplier *= acc.multiplierMult != null ? acc.multiplierMult : 1;
  rawMultiplier = Math.max(1, rawMultiplier);

  const baseMaxMultiplier = acc.maxMultiplierSet != null ? acc.maxMultiplierSet : CRIT_DEFAULTS.maxMultiplier;
  let maxMultiplier = baseMaxMultiplier + acc.maxMultiplierAdd;
  maxMultiplier = Math.max(0, maxMultiplier);
  maxMultiplier *= acc.maxMultiplierMult != null ? acc.maxMultiplierMult : 1;
  maxMultiplier = Math.max(1, maxMultiplier);

  const effectiveMultiplier = Math.max(1, Math.min(rawMultiplier, maxMultiplier));
  return {
    chance,
    multiplier: effectiveMultiplier,
    rawMultiplier,
    maxMultiplier
  };
}

const FRENZY_DEFAULTS = {
  displayDurationMs: 5000,
  effectDurationMs: 30000,
  multiplier: 2,
  baseMaxStacks: 1,
  spawnChancePerSecond: {
    perClick: 0.01,
    perSecond: 0.01
  }
};

function normalizeFrenzySpawnChance(raw) {
  const clamp = value => {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric < 0) {
      return 0;
    }
    if (numeric > 1) {
      return 1;
    }
    return numeric;
  };
  const defaults = FRENZY_DEFAULTS.spawnChancePerSecond;
  if (typeof raw === 'number') {
    const value = clamp(raw);
    return { perClick: value, perSecond: value };
  }
  if (raw && typeof raw === 'object') {
    const perClickRaw = raw.perClick ?? raw.click ?? raw.apc;
    const perSecondRaw = raw.perSecond ?? raw.auto ?? raw.aps;
    return {
      perClick: perClickRaw != null ? clamp(perClickRaw) : defaults.perClick,
      perSecond: perSecondRaw != null ? clamp(perSecondRaw) : defaults.perSecond
    };
  }
  return { ...defaults };
}

const FRENZY_CONFIG = {
  displayDurationMs: Math.max(0, Number(CONFIG.frenzies?.displayDurationMs ?? FRENZY_DEFAULTS.displayDurationMs) || 0),
  effectDurationMs: Math.max(0, Number(CONFIG.frenzies?.effectDurationMs ?? FRENZY_DEFAULTS.effectDurationMs) || 0),
  multiplier: Math.max(1, Number(CONFIG.frenzies?.multiplier ?? FRENZY_DEFAULTS.multiplier) || FRENZY_DEFAULTS.multiplier),
  baseMaxStacks: Math.max(1, Number(CONFIG.frenzies?.baseMaxStacks ?? FRENZY_DEFAULTS.baseMaxStacks) || FRENZY_DEFAULTS.baseMaxStacks),
  spawnChancePerSecond: normalizeFrenzySpawnChance(CONFIG.frenzies?.spawnChancePerSecond)
};

const FRENZY_PORTRAIT_ASSETS = Object.freeze([
  'Assets/Image/Astronaute.png',
  'Assets/Image/Darwin.png',
  'Assets/Image/MarieCurie.png',
  'Assets/Image/Monkey.png',
  'Assets/Image/Newton2.png',
  'Assets/Image/Oppenheimer.png',
  'Assets/Image/Sagan.png',
  'Assets/Image/Socrates.png',
  'Assets/Image/Tesla.png'
]);

const FRENZY_TYPE_INFO = {
  perClick: {
    id: 'perClick',
    label: 'Frénésie APC',
    shortLabel: 'APC',
    asset: FRENZY_PORTRAIT_ASSETS[0],
    assets: FRENZY_PORTRAIT_ASSETS
  },
  perSecond: {
    id: 'perSecond',
    label: 'Frénésie APS',
    shortLabel: 'APS',
    asset: FRENZY_PORTRAIT_ASSETS[0],
    assets: FRENZY_PORTRAIT_ASSETS
  }
};

const FRENZY_TYPES = ['perClick', 'perSecond'];

const frenzySpawnChanceBonus = { perClick: 1, perSecond: 1 };
const frenzySpawnChanceBonusAdd = { perClick: 0, perSecond: 0 };

function applyFrenzySpawnChanceBonus(bonus) {
  const perClick = Number(bonus?.perClick);
  const perSecond = Number(bonus?.perSecond);
  frenzySpawnChanceBonus.perClick = Number.isFinite(perClick) && perClick > 0 ? perClick : 1;
  frenzySpawnChanceBonus.perSecond = Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1;
  const addPerClick = Number(bonus?.addPerClick ?? bonus?.perClickAdd);
  const addPerSecond = Number(bonus?.addPerSecond ?? bonus?.perSecondAdd);
  frenzySpawnChanceBonusAdd.perClick = Number.isFinite(addPerClick) && addPerClick > 0 ? addPerClick : 0;
  frenzySpawnChanceBonusAdd.perSecond = Number.isFinite(addPerSecond) && addPerSecond > 0 ? addPerSecond : 0;
}

function getEffectiveFrenzySpawnChance(type) {
  const base = FRENZY_CONFIG.spawnChancePerSecond[type] ?? 0;
  if (!Number.isFinite(base) || base <= 0) {
    return 0;
  }
  const modifier = type === 'perClick'
    ? frenzySpawnChanceBonus.perClick
    : (type === 'perSecond' ? frenzySpawnChanceBonus.perSecond : 1);
  const additive = type === 'perClick'
    ? frenzySpawnChanceBonusAdd.perClick
    : (type === 'perSecond' ? frenzySpawnChanceBonusAdd.perSecond : 0);
  const total = base * modifier + (Number.isFinite(additive) && additive > 0 ? additive : 0);
  if (!Number.isFinite(total) || total <= 0) {
    return 0;
  }
  return Math.min(1, total);
}

const FALLBACK_UPGRADES = (function createFallbackUpgrades() {
  if (typeof createShopBuildingDefinitions === 'function') {
    return createShopBuildingDefinitions();
  }

  const getLevel = (context, id) => {
    if (!context || typeof context !== 'object') {
      return 0;
    }
    const value = Number(context[id] ?? 0);
    return Number.isFinite(value) && value > 0 ? value : 0;
  };

  const getTotal = context => {
    if (!context || typeof context !== 'object') {
      return 0;
    }
    return Object.values(context).reduce((acc, value) => {
      const numeric = Number(value);
      return acc + (Number.isFinite(numeric) && numeric > 0 ? numeric : 0);
    }, 0);
  };

  const withDefaults = def => ({ maxLevel: DEFAULT_UPGRADE_MAX_LEVEL, ...def });

  return [
    {
      id: 'freeElectrons',
      name: 'Électrons libres',
      description: 'Libérez des électrons pour une production de base stable.',
      effectSummary:
        'Production passive : minimum +1 APS par niveau. À 100 exemplaires : chaque électron ajoute +1 APC (valeur arrondie).',
      category: 'auto',
      baseCost: 15,
      costScale: 1.15,
      effect: (level = 0) => {
        const rawAutoAdd = level;
        const autoAdd = level > 0 ? Math.max(level, Math.round(rawAutoAdd)) : 0;
        const clickAdd = level >= 100 ? level : 0;
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        return result;
      }
    },
    {
      id: 'physicsLab',
      name: 'Laboratoire de Physique',
      description: 'Des équipes de chercheurs boostent votre production atomique.',
      effectSummary:
        'Production passive : +2 APS par niveau. Chaque 10 labos accordent +5 % d’APC global. Accélérateur ≥200 : Labos +20 % APS.',
      category: 'auto',
      baseCost: 100,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const acceleratorLevel = getLevel(context, 'particleAccelerator');
        let productionMultiplier = 1;
        if (acceleratorLevel >= 200) {
          productionMultiplier *= 1.2;
        }
        const baseAmount = 2 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickBonus = Math.pow(1.05, Math.floor(level / 10));
        return {
          autoAdd,
          clickMult: clickBonus
        };
      }
    },
    {
      id: 'nuclearReactor',
      name: 'Réacteur nucléaire',
      description: 'Des réacteurs contrôlés libèrent une énergie colossale.',
      effectSummary:
        'Production passive : +10 APS par niveau (+1 % par 50 Électrons, +20 % si Labos ≥200).',
      category: 'auto',
      baseCost: 1000,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const electronLevel = getLevel(context, 'freeElectrons');
        const labLevel = getLevel(context, 'physicsLab');
        let productionMultiplier = 1;
        if (electronLevel > 0) {
          productionMultiplier *= 1 + 0.01 * Math.floor(electronLevel / 50);
        }
        if (labLevel >= 200) {
          productionMultiplier *= 1.2;
        }
        const baseAmount = 10 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        return { autoAdd };
      }
    },
    {
      id: 'particleAccelerator',
      name: 'Accélérateur de particules',
      description: 'Boostez vos particules pour décupler l’APC.',
      effectSummary:
        'Production passive : +50 APS par niveau (bonus si ≥100 Supercalculateurs). Palier 200 : +20 % production des Labos.',
      category: 'hybrid',
      baseCost: 12_000,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const supercomputerLevel = getLevel(context, 'supercomputer');
        let productionMultiplier = 1;
        if (supercomputerLevel >= 100) {
          productionMultiplier *= 1.5;
        }
        const baseAmount = 50 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        return { autoAdd };
      }
    },
    {
      id: 'supercomputer',
      name: 'Supercalculateurs',
      description: 'Des centres de calcul quantique optimisent vos gains.',
      effectSummary:
        'Production passive : +500 APS par niveau (doublée par Stations ≥300). Tous les 50 Supercalculateurs : APS global ×2 (cumulatif).',
      category: 'auto',
      baseCost: 200_000,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const stationLevel = getLevel(context, 'spaceStation');
        let productionMultiplier = 1;
        if (stationLevel >= 300) {
          productionMultiplier *= 2;
        }
        const baseAmount = 500 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const autoMult = Math.pow(2, Math.floor(level / 50));
        return autoMult > 1
          ? { autoAdd, autoMult }
          : { autoAdd };
      }
    },
    {
      id: 'interstellarProbe',
      name: 'Sonde interstellaire',
      description: 'Explorez la galaxie pour récolter toujours plus.',
      effectSummary:
        'Production passive : +5 000 APS par niveau (boostée par Réacteurs). À 150 exemplaires : chaque sonde ajoute +10 APC.',
      category: 'hybrid',
      baseCost: 5e6,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const reactorLevel = getLevel(context, 'nuclearReactor');
        let productionMultiplier = 1;
        if (reactorLevel > 0) {
          productionMultiplier *= 1 + 0.001 * reactorLevel;
        }
        const baseAmount = 5000 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickAdd = level >= 150 ? level * 10 : 0;
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        return result;
      }
    },
    {
      id: 'spaceStation',
      name: 'Station spatiale',
      description: 'Des bases orbitales coordonnent votre expansion.',
      effectSummary:
        'Production passive : +50 000 APS par niveau. Chaque Station accorde +5 % d’APC. Palier 300 : Supercalculateurs +100 %.',
      category: 'hybrid',
      baseCost: 1e8,
      costScale: 1.2,
      effect: (level = 0) => {
        const baseAmount = 50_000 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = Math.pow(1.05, level);
        return { autoAdd, clickMult };
      }
    },
    {
      id: 'starForge',
      name: 'Forgeron d’étoiles',
      description: 'Façonnez des étoiles et dopez votre APC.',
      effectSummary:
        'Production passive : +500 000 APS par niveau (+2 % APS par Station). Tous les 50 Forgerons : APC global ×2 (cumulatif).',
      category: 'hybrid',
      baseCost: 5e10,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const stationLevel = getLevel(context, 'spaceStation');
        let productionMultiplier = 1;
        if (stationLevel > 0) {
          productionMultiplier *= 1 + 0.02 * stationLevel;
        }
        const baseAmount = 500_000 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = Math.pow(2, Math.floor(level / 50));
        return clickMult > 1
          ? { autoAdd, clickMult }
          : { autoAdd };
      }
    },
    {
      id: 'artificialGalaxy',
      name: 'Galaxie artificielle',
      description: 'Ingénierie galactique pour une expansion sans fin.',
      effectSummary:
        'Production passive : +5 000 000 APS par niveau (doublée par Bibliothèque ≥300). Palier 100 : +50 % APC global.',
      category: 'auto',
      baseCost: 1e13,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const libraryLevel = getLevel(context, 'omniverseLibrary');
        let productionMultiplier = 1;
        if (libraryLevel >= 300) {
          productionMultiplier *= 2;
        }
        const baseAmount = 5e6 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = level >= 100 ? 1.5 : 1;
        const result = { autoAdd };
        if (clickMult > 1) {
          result.clickMult = clickMult;
        }
        return result;
      }
    },
    {
      id: 'multiverseSimulator',
      name: 'Simulateur de Multivers',
      description: 'Simulez l’infini pour optimiser chaque seconde.',
      effectSummary:
        'Production passive : +500 000 000 APS par niveau. Palier 200 : coûts des bâtiments −5 %.',
      category: 'auto',
      baseCost: 1e16,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const baseAmount = 5e8 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        return { autoAdd };
      }
    },
    {
      id: 'realityWeaver',
      name: 'Tisseur de Réalité',
      description: 'Tissez les lois physiques à votre avantage.',
      effectSummary:
        'Production passive : +10 000 000 000 APS par niveau. Bonus clic arrondi : +0,1 × bâtiments × niveau. Palier 300 : production totale ×2.',
      category: 'hybrid',
      baseCost: 1e20,
      costScale: 1.25,
      effect: (level = 0, context = {}) => {
        const totalBuildings = getTotal(context);
        const baseAmount = 1e10 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const rawClickAdd = totalBuildings > 0 ? 0.1 * totalBuildings * level : 0;
        const clickAdd = rawClickAdd > 0 ? Math.max(1, Math.round(rawClickAdd)) : 0;
        const globalMult = level >= 300 ? 2 : 1;
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        if (globalMult > 1) {
          result.autoMult = globalMult;
          result.clickMult = globalMult;
        }
        return result;
      }
    },
    {
      id: 'cosmicArchitect',
      name: 'Architecte Cosmique',
      description: 'Réécrivez les plans du cosmos pour réduire les coûts.',
      effectSummary:
        'Production passive : +1 000 000 000 000 APS par niveau. Réduction de 1 % du coût futur par Architecte. Tous les 50 Architectes : APC global ×2 (cumulatif).',
      category: 'hybrid',
      baseCost: 1e25,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e12 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = Math.pow(2, Math.floor(level / 50));
        return clickMult > 1
          ? { autoAdd, clickMult }
          : { autoAdd };
      }
    },
    {
      id: 'parallelUniverse',
      name: 'Univers parallèle',
      description: 'Expérimentez des réalités alternatives à haut rendement.',
      effectSummary:
        'Production passive : +100 000 000 000 000 APS par niveau.',
      category: 'auto',
      baseCost: 1e30,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e14 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        return { autoAdd };
      }
    },
    {
      id: 'omniverseLibrary',
      name: 'Bibliothèque de l’Omnivers',
      description: 'Compilez le savoir infini pour booster toute production.',
      effectSummary:
        'Production passive : +10 000 000 000 000 000 APS par niveau. +2 % boost global par Univers parallèle. Palier 300 : Galaxies artificielles ×2.',
      category: 'hybrid',
      baseCost: 1e36,
      costScale: 1.25,
      effect: (level = 0, context = {}) => {
        const baseAmount = 1e16 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const parallelLevel = getLevel(context, 'parallelUniverse');
        const globalBoost = parallelLevel > 0 ? Math.pow(1.02, parallelLevel) : 1;
        if (globalBoost > 1) {
          return {
            autoAdd,
            autoMult: globalBoost,
            clickMult: globalBoost
          };
        }
        return { autoAdd };
      }
    },
    {
      id: 'quantumOverseer',
      name: 'Grand Ordonnateur Quantique',
      description: 'Ordonnez le multivers et atteignez la singularité.',
      effectSummary:
        'Production passive : +1 000 000 000 000 000 000 APS par niveau. Palier 100 : double définitivement tous les gains.',
      category: 'hybrid',
      baseCost: 1e42,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e18 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const globalMult = level >= 100 ? 2 : 1;
        return globalMult > 1
          ? { autoAdd, autoMult: globalMult, clickMult: globalMult }
          : { autoAdd };
      }
    }
  ].map(withDefaults);
})();

function renderGachaRarityList() {
  if (!elements.gachaRarityList) return;
  const weightsUpdated = refreshGachaRarities(new Date());
  if (weightsUpdated) {
    rebuildGachaPools();
  }
  elements.gachaRarityList.innerHTML = '';
  gachaRarityRows.clear();
  const totalWeight = getCurrentGachaTotalWeight();
  GACHA_RARITIES.forEach(def => {
    const item = document.createElement('li');
    item.className = 'gacha-rarity';
    item.dataset.rarityId = def.id;
    if (def.color) {
      item.style.setProperty('--rarity-color', def.color);
    }

    const header = document.createElement('div');
    header.className = 'gacha-rarity__header';

    const label = document.createElement('span');
    label.className = 'gacha-rarity__label';
    label.textContent = def.label || def.id;

    const chance = document.createElement('span');
    chance.className = 'gacha-rarity__chance';
    if (def.weight > 0 && totalWeight > 0) {
      const ratio = (def.weight / totalWeight) * 100;
      const digits = ratio >= 10 ? 1 : 2;
      const formattedRatio = formatNumberLocalized(ratio, {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits
      });
      chance.textContent = `${formattedRatio}\u00a0%`;
    } else {
      chance.textContent = '—';
    }

    header.append(label, chance);
    item.appendChild(header);

    if (def.description) {
      const description = document.createElement('p');
      description.className = 'gacha-rarity__description';
      description.textContent = def.description;
      item.appendChild(description);
    }

    const progress = document.createElement('div');
    progress.className = 'gacha-rarity__progress';
    progress.setAttribute('role', 'progressbar');
    progress.setAttribute('aria-valuemin', '0');
    progress.setAttribute('aria-valuemax', '0');
    progress.setAttribute('aria-valuenow', '0');

    const bar = document.createElement('div');
    bar.className = 'gacha-rarity__bar';
    progress.appendChild(bar);

    const summary = document.createElement('p');
    summary.className = 'gacha-rarity__summary';
    summary.textContent = t('scripts.gacha.rarityProgress.empty');

    item.append(progress, summary);
    elements.gachaRarityList.appendChild(item);
    gachaRarityRows.set(def.id, { item, progress, bar, summary });
  });

  updateGachaRarityProgress();
}

function updateGachaRarityProgress() {
  if (!gachaRarityRows.size) return;
  const totals = new Map();
  gachaPools.forEach((ids, rarity) => {
    totals.set(rarity, { total: ids.length, owned: 0 });
  });

  const entries = Object.values(gameState.elements || {});
  entries.forEach(entry => {
    if (!entry) return;
    const rarityId = entry.rarity || elementRarityIndex.get(entry.id);
    if (!rarityId || !totals.has(rarityId)) return;
    const bucket = totals.get(rarityId);
    if (hasElementLifetime(entry)) {
      bucket.owned += 1;
    }
  });

  gachaRarityRows.forEach((row, rarityId) => {
    const data = totals.get(rarityId) || { total: 0, owned: 0 };
    const total = data.total;
    const owned = data.owned;
    row.progress.setAttribute('aria-valuemax', String(total));
    row.progress.setAttribute('aria-valuenow', String(owned));
    const percent = total > 0 ? (owned / total) * 100 : 0;
    row.bar.style.width = `${percent}%`;
    row.summary.textContent = total > 0
      ? t('scripts.gacha.rarityProgress.summary', { owned, total })
      : t('scripts.gacha.rarityProgress.empty');
  });
}

const fusionCards = new Map();
let isRenderingFusionList = false;

const FUSION_ATTEMPT_OPTIONS = [1, 10];
let fusionAttemptMultiplier = FUSION_ATTEMPT_OPTIONS[0];
let fusionModeControlInitialized = false;
const HYDROGEN_FUSION_ID = 'hydrogen';
const CARBON_FUSION_ID = 'carbon';
const FUSION_MULTIPLIER_INCREMENT = 1;
const RESTRICTED_FUSION_IDS = new Set([HYDROGEN_FUSION_ID, CARBON_FUSION_ID]);
const GOD_FINGER_UPGRADE_ID = 'godFinger';
const STAR_CORE_UPGRADE_ID = 'starCore';

function normalizeFusionAttemptMultiplier(value) {
  const numeric = Math.max(1, Math.floor(Number(value) || 0));
  if (FUSION_ATTEMPT_OPTIONS.includes(numeric)) {
    return numeric;
  }
  return FUSION_ATTEMPT_OPTIONS[0];
}

function getFusionAttemptMultiplier() {
  fusionAttemptMultiplier = normalizeFusionAttemptMultiplier(fusionAttemptMultiplier);
  return fusionAttemptMultiplier;
}

function computeFusionRequirementTotal(baseCount, multiplier = getFusionAttemptMultiplier()) {
  const normalizedBase = Math.max(0, Number(baseCount) || 0);
  const normalizedMultiplier = Math.max(1, Math.floor(Number(multiplier) || 0));
  return normalizedBase * normalizedMultiplier;
}

function getFusionModeLabel(multiplier = getFusionAttemptMultiplier()) {
  const formattedCount = formatIntegerLocalized(multiplier);
  const modeKey = multiplier > 1 ? 'modeMulti' : 'modeSingle';
  return translateWithFallback(
    `scripts.gacha.fusion.controls.${modeKey}`,
    multiplier > 1 ? `×${formattedCount}` : `×${formattedCount}`,
    { count: formattedCount }
  );
}

function getFusionModeDescription(multiplier = getFusionAttemptMultiplier()) {
  const modeLabel = getFusionModeLabel(multiplier);
  const formattedCount = formatIntegerLocalized(multiplier);
  return translateWithFallback(
    'scripts.gacha.fusion.controls.modeLabel',
    `Tentatives par clic : ${modeLabel}`,
    { mode: modeLabel, count: formattedCount }
  );
}

function refreshFusionModeButton() {
  if (typeof elements === 'undefined' || !elements) {
    return;
  }
  const button = elements.fusionModeButton;
  if (!button) {
    fusionModeControlInitialized = false;
    return;
  }
  const multiplier = getFusionAttemptMultiplier();
  const modeKey = multiplier > 1 ? 'modeMulti' : 'modeSingle';
  const modeLabel = getFusionModeLabel(multiplier);
  const description = getFusionModeDescription(multiplier);
  const formattedCount = formatIntegerLocalized(multiplier);
  button.textContent = modeLabel;
  button.dataset.mode = modeKey === 'modeMulti' ? 'multi' : 'single';
  button.setAttribute('data-i18n', `scripts.gacha.fusion.controls.${modeKey}`);
  button.setAttribute('title', translateWithFallback(
    'scripts.gacha.fusion.controls.modeTitle',
    description,
    { mode: modeLabel, count: formattedCount }
  ));
  button.setAttribute('aria-pressed', multiplier > 1 ? 'true' : 'false');
  button.setAttribute('aria-label', translateWithFallback(
    'scripts.gacha.fusion.controls.modeAria',
    `Basculer le nombre de tentatives de fusion (actuel : ${modeLabel})`,
    { mode: modeLabel, count: formattedCount }
  ));
}

function initFusionModeControl() {
  if (fusionModeControlInitialized) {
    refreshFusionModeButton();
    return;
  }
  if (typeof elements === 'undefined' || !elements) {
    return;
  }
  const button = elements.fusionModeButton;
  if (!button) {
    fusionModeControlInitialized = false;
    return;
  }
  fusionModeControlInitialized = true;
  button.addEventListener('click', event => {
    event.preventDefault();
    toggleFusionAttemptMode();
  });
  refreshFusionModeButton();
}

function setFusionAttemptMultiplier(value) {
  const normalized = normalizeFusionAttemptMultiplier(value);
  if (normalized === fusionAttemptMultiplier) {
    refreshFusionModeButton();
    return;
  }
  fusionAttemptMultiplier = normalized;
  refreshFusionModeButton();
  updateFusionUI();
}

function toggleFusionAttemptMode() {
  const current = getFusionAttemptMultiplier();
  const next = current === FUSION_ATTEMPT_OPTIONS[0]
    ? FUSION_ATTEMPT_OPTIONS[FUSION_ATTEMPT_OPTIONS.length - 1]
    : FUSION_ATTEMPT_OPTIONS[0];
  setFusionAttemptMultiplier(next);
}

function getFusionTryButtonLabel(multiplier = getFusionAttemptMultiplier()) {
  const formattedCount = formatIntegerLocalized(multiplier);
  return translateWithFallback(
    'scripts.gacha.fusion.controls.tryButton',
    `Tenter ×${formattedCount}`,
    { count: formattedCount }
  );
}

function getFusionTryButtonAria(definition, multiplier = getFusionAttemptMultiplier()) {
  const formattedCount = formatIntegerLocalized(multiplier);
  const name = definition?.name || '';
  return translateWithFallback(
    'scripts.gacha.fusion.tryButtonAria',
    `Tenter la fusion ${name} ×${formattedCount}`,
    { name, count: formattedCount }
  );
}

function formatFusionChance(chance) {
  const ratio = Math.max(0, Math.min(1, Number(chance) || 0));
  const percent = ratio * 100;
  const digits = percent < 10 ? 1 : 0;
  const formattedPercent = formatNumberLocalized(percent, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  });
  return `${formattedPercent} %`;
}

function getFusionStateById(fusionId) {
  if (!gameState.fusions || typeof gameState.fusions !== 'object') {
    gameState.fusions = createInitialFusionState();
  }
  if (!gameState.fusions[fusionId]) {
    gameState.fusions[fusionId] = { attempts: 0, successes: 0 };
  }
  const state = gameState.fusions[fusionId];
  const attempts = Number(state.attempts);
  const successes = Number(state.successes);
  state.attempts = Number.isFinite(attempts) && attempts > 0 ? Math.floor(attempts) : 0;
  state.successes = Number.isFinite(successes) && successes > 0 ? Math.floor(successes) : 0;
  return state;
}

function getFusionBonusState() {
  if (!gameState.fusionBonuses || typeof gameState.fusionBonuses !== 'object') {
    gameState.fusionBonuses = createInitialFusionBonuses();
  }
  const bonuses = gameState.fusionBonuses;
  const apc = Number(bonuses.apcFlat);
  const aps = Number(bonuses.apsFlat);
  let apcBase = Number(bonuses.apcHydrogenBase);
  let apsBase = Number(bonuses.apsHydrogenBase);
  let apcBaseBoost = Number(bonuses.apcBaseBoost);
  let apsBaseBoost = Number(bonuses.apsBaseBoost);
  const multiplier = Number(bonuses.fusionMultiplier);
  let apcFrenzyDurationSeconds = Number(bonuses.apcFrenzyDurationSeconds);
  let apsFrenzyDurationSeconds = Number(bonuses.apsFrenzyDurationSeconds);
  bonuses.apcFlat = Number.isFinite(apc) ? apc : 0;
  bonuses.apsFlat = Number.isFinite(aps) ? aps : 0;
  apcBase = Number.isFinite(apcBase) ? apcBase : 0;
  apsBase = Number.isFinite(apsBase) ? apsBase : 0;
  if (apcBase === 0) {
    const reconstructedApc = reconstructHydrogenBase('apcFlat');
    if (reconstructedApc > 0) {
      apcBase = reconstructedApc;
    }
  }
  if (apsBase === 0) {
    const reconstructedAps = reconstructHydrogenBase('apsFlat');
    if (reconstructedAps > 0) {
      apsBase = reconstructedAps;
    }
  }
  bonuses.apcHydrogenBase = apcBase;
  bonuses.apsHydrogenBase = apsBase;
  apcBaseBoost = Number.isFinite(apcBaseBoost) ? apcBaseBoost : 0;
  apsBaseBoost = Number.isFinite(apsBaseBoost) ? apsBaseBoost : 0;
  bonuses.apcBaseBoost = apcBaseBoost;
  bonuses.apsBaseBoost = apsBaseBoost;
  apcFrenzyDurationSeconds = Number.isFinite(apcFrenzyDurationSeconds)
    ? Math.max(0, apcFrenzyDurationSeconds)
    : 0;
  apsFrenzyDurationSeconds = Number.isFinite(apsFrenzyDurationSeconds)
    ? Math.max(0, apsFrenzyDurationSeconds)
    : 0;
  bonuses.apcFrenzyDurationSeconds = apcFrenzyDurationSeconds;
  bonuses.apsFrenzyDurationSeconds = apsFrenzyDurationSeconds;
  bonuses.fusionMultiplier = Number.isFinite(multiplier) && multiplier > 0 ? multiplier : 1;
  return bonuses;
}

function reconstructHydrogenBase(rewardKey) {
  if (!rewardKey) {
    return 0;
  }
  const definition = FUSION_DEFINITION_MAP?.get(HYDROGEN_FUSION_ID);
  if (!definition) {
    return 0;
  }
  const rewardValue = Number(definition.rewards?.[rewardKey]);
  if (!Number.isFinite(rewardValue) || rewardValue === 0) {
    return 0;
  }
  const state = getFusionStateById(HYDROGEN_FUSION_ID);
  const successes = state?.successes ?? 0;
  if (!successes) {
    return 0;
  }
  return computeFusionRewardSum(definition, rewardValue, successes);
}

function setFusionLog(message, status = null) {
  if (!elements.fusionLog) return;
  elements.fusionLog.textContent = message;
  elements.fusionLog.classList.remove('fusion-log--success', 'fusion-log--failure');
  if (status === 'success') {
    elements.fusionLog.classList.add('fusion-log--success');
  } else if (status === 'failure') {
    elements.fusionLog.classList.add('fusion-log--failure');
  }
}

function getFusionFlatBonusTotal() {
  const bonuses = getFusionBonusState();
  const apc = Number(bonuses?.apcFlat);
  const aps = Number(bonuses?.apsFlat);
  const total = (Number.isFinite(apc) ? apc : 0) + (Number.isFinite(aps) ? aps : 0);
  return Number.isFinite(total) && total > 0 ? total : 0;
}

function getShopBaselineProductionTotal() {
  if (typeof getCompressedShopBonus !== 'function') {
    return 0;
  }
  const upgrades = gameState?.upgrades;
  const apcFromShop = getCompressedShopBonus(
    typeof getUpgradeLevel === 'function' ? getUpgradeLevel(upgrades, GOD_FINGER_UPGRADE_ID) : 0
  );
  const apsFromShop = getCompressedShopBonus(
    typeof getUpgradeLevel === 'function' ? getUpgradeLevel(upgrades, STAR_CORE_UPGRADE_ID) : 0
  );
  const total = Number(apcFromShop) + Number(apsFromShop);
  return Number.isFinite(total) && total > 0 ? total : 0;
}

function isFusionBlockedByBonus(definition) {
  if (!definition || !RESTRICTED_FUSION_IDS.has(definition.id)) {
    return false;
  }
  const productionBaseline = getShopBaselineProductionTotal();
  if (productionBaseline <= 0) {
    return false;
  }
  return getFusionFlatBonusTotal() > productionBaseline;
}

function canAttemptFusion(definition, attemptCount = getFusionAttemptMultiplier()) {
  if (!definition) return false;
  if (!Array.isArray(definition.inputs) || !definition.inputs.length) {
    return false;
  }
  if (isFusionBlockedByBonus(definition)) {
    return false;
  }
  return definition.inputs.every(input => {
    const entry = gameState.elements?.[input.elementId];
    const available = getElementCurrentCount(entry);
    const required = computeFusionRequirementTotal(input.count, attemptCount);
    return available >= required;
  });
}

function formatFusionElementLabel(reward) {
  if (!reward || typeof reward !== 'object') {
    return t('scripts.gacha.results.unknownElement');
  }
  const elementId = typeof reward.elementId === 'string' ? reward.elementId : '';
  const definition = reward.elementDef
    || (elementId && periodicElementIndex ? periodicElementIndex.get(elementId) : null)
    || null;
  if (!definition) {
    if (elementId && elementId.trim()) {
      return elementId.trim();
    }
    return t('scripts.gacha.results.unknownElement');
  }
  const { name, symbol } = getGachaPeriodicElementDisplay(definition);
  const trimmedName = typeof name === 'string' ? name.trim() : '';
  const trimmedSymbol = typeof symbol === 'string' ? symbol.trim() : '';
  if (trimmedName && trimmedSymbol && trimmedName !== trimmedSymbol) {
    return `${trimmedName} (${trimmedSymbol})`;
  }
  if (trimmedName) {
    return trimmedName;
  }
  if (trimmedSymbol) {
    return trimmedSymbol;
  }
  if (typeof definition.name === 'string' && definition.name.trim()) {
    return definition.name.trim();
  }
  if (typeof definition.symbol === 'string' && definition.symbol.trim()) {
    return definition.symbol.trim();
  }
  if (elementId && elementId.trim()) {
    return elementId.trim();
  }
  return t('scripts.gacha.results.unknownElement');
}

function getFusionRewardGrowthFactor(definition) {
  const multiplier = Number(definition?.rewardGrowthMultiplier);
  if (!Number.isFinite(multiplier) || multiplier <= 1) {
    return 1;
  }
  return multiplier;
}

function getFusionRewardMultiplier(definition, successCount) {
  const factor = getFusionRewardGrowthFactor(definition);
  if (factor <= 1) {
    return 1;
  }
  const normalizedCount = Math.max(0, Math.floor(Number(successCount) || 0));
  if (normalizedCount <= 1) {
    return 1;
  }
  return factor ** (normalizedCount - 1);
}

function computeFusionRewardSum(definition, baseValue, successCount, previousSuccessCount = 0) {
  if (!Number.isFinite(baseValue) || baseValue === 0) {
    return 0;
  }
  const totalSuccesses = Math.max(0, Math.floor(Number(successCount) || 0));
  if (totalSuccesses <= 0) {
    return 0;
  }
  const factor = getFusionRewardGrowthFactor(definition);
  if (factor <= 1) {
    return baseValue * totalSuccesses;
  }
  const startExponent = Math.max(0, Math.floor(Number(previousSuccessCount) || 0));
  const powerOffset = factor ** startExponent;
  const growthSpan = factor ** totalSuccesses;
  return baseValue * powerOffset * ((growthSpan - 1) / (factor - 1));
}

function formatFusionRewardValue(value) {
  if (typeof formatLayeredLocalized === 'function') {
    const formatted = formatLayeredLocalized(value, {
      mantissaDigits: 2,
      numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 }
    });
    if (typeof formatted === 'string' && formatted.length > 0) {
      return formatted;
    }
  }
  const numeric = Number(value);
  if (Number.isFinite(numeric)) {
    const clamped = Math.max(0, Math.floor(numeric));
    return formatNumberLocalized(clamped, { maximumFractionDigits: 0, minimumFractionDigits: 0 });
  }
  if (value instanceof LayeredNumber) {
    return value.toString();
  }
  return formatNumberLocalized(0, { maximumFractionDigits: 0, minimumFractionDigits: 0 });
}

function applyFusionRewards(rewards, multiplier = 1, options = {}) {
  if (!rewards || typeof rewards !== 'object') {
    return [];
  }
  const { skipApcAps = false, skipElements = false } = options || {};
  const bonuses = getFusionBonusState();
  const summaries = [];
  let awardedElements = false;
  const appliedMultiplier = Number.isFinite(Number(multiplier)) ? Number(multiplier) : 1;
  if (!skipApcAps) {
    const apcIncrement = Number(rewards.apcFlat);
    if (Number.isFinite(apcIncrement) && apcIncrement !== 0 && appliedMultiplier !== 0) {
      const apcGain = apcIncrement * appliedMultiplier;
      bonuses.apcFlat += apcGain;
      const formatted = formatFusionRewardValue(apcGain);
      summaries.push(t('scripts.gacha.fusion.apcBonus', { value: formatted }));
    }
    const apsIncrement = Number(rewards.apsFlat);
    if (Number.isFinite(apsIncrement) && apsIncrement !== 0 && appliedMultiplier !== 0) {
      const apsGain = apsIncrement * appliedMultiplier;
      bonuses.apsFlat += apsGain;
      const formatted = formatFusionRewardValue(apsGain);
      summaries.push(t('scripts.gacha.fusion.apsBonus', { value: formatted }));
    }
  }
  if (appliedMultiplier !== 0) {
    const apcBaseIncrement = Number(rewards.apcBaseBoost);
    if (Number.isFinite(apcBaseIncrement) && apcBaseIncrement !== 0) {
      const apcBaseGain = apcBaseIncrement * appliedMultiplier;
      const previousBaseBoost = Number(bonuses.apcBaseBoost) || 0;
      bonuses.apcBaseBoost = previousBaseBoost + apcBaseGain;
      const formatted = formatFusionRewardValue(apcBaseGain);
      summaries.push(t('scripts.gacha.fusion.apcBaseBonus', { value: formatted }));
    }
    const apsBaseIncrement = Number(rewards.apsBaseBoost);
    if (Number.isFinite(apsBaseIncrement) && apsBaseIncrement !== 0) {
      const apsBaseGain = apsBaseIncrement * appliedMultiplier;
      const previousApsBoost = Number(bonuses.apsBaseBoost) || 0;
      bonuses.apsBaseBoost = previousApsBoost + apsBaseGain;
      const formatted = formatFusionRewardValue(apsBaseGain);
      summaries.push(t('scripts.gacha.fusion.apsBaseBonus', { value: formatted }));
    }
    const apcFrenzyIncrement = Number(rewards.apcFrenzyDurationSeconds);
    if (Number.isFinite(apcFrenzyIncrement) && apcFrenzyIncrement !== 0) {
      const apcFrenzyGain = apcFrenzyIncrement * appliedMultiplier;
      const previousApcFrenzy = Number(bonuses.apcFrenzyDurationSeconds) || 0;
      bonuses.apcFrenzyDurationSeconds = previousApcFrenzy + apcFrenzyGain;
      const formatted = formatFusionRewardValue(apcFrenzyGain);
      summaries.push(t('scripts.gacha.fusion.apcFrenzyBonus', { value: formatted }));
    }
    const apsFrenzyIncrement = Number(rewards.apsFrenzyDurationSeconds);
    if (Number.isFinite(apsFrenzyIncrement) && apsFrenzyIncrement !== 0) {
      const apsFrenzyGain = apsFrenzyIncrement * appliedMultiplier;
      const previousApsFrenzy = Number(bonuses.apsFrenzyDurationSeconds) || 0;
      bonuses.apsFrenzyDurationSeconds = previousApsFrenzy + apsFrenzyGain;
      const formatted = formatFusionRewardValue(apsFrenzyGain);
      summaries.push(t('scripts.gacha.fusion.apsFrenzyBonus', { value: formatted }));
    }
  }
  if (!skipElements) {
    const elementRewards = Array.isArray(rewards.elements) ? rewards.elements : [];
    elementRewards.forEach(reward => {
      const increment = Number(reward?.count ?? reward?.quantity ?? reward?.amount ?? 0);
      if (!Number.isFinite(increment) || increment <= 0) {
        return;
      }
      if (!gameState.elements || typeof gameState.elements !== 'object') {
        gameState.elements = {};
      }
      const elementId = typeof reward.elementId === 'string' ? reward.elementId : '';
      if (!elementId) {
        return;
      }
      let entry = gameState.elements[elementId];
      if (!entry) {
        entry = {
          id: elementId,
          gachaId: reward.elementDef?.gachaId ?? elementId,
          owned: false,
          count: 0,
          lifetime: 0,
          rarity: reward.elementDef?.rarity ?? null,
          effects: [],
          bonuses: []
        };
        gameState.elements[elementId] = entry;
      }
      const previousCount = getElementCurrentCount(entry);
      const previousLifetime = getElementLifetimeCount(entry);
      entry.count = previousCount + increment;
      entry.lifetime = previousLifetime + increment;
      if (!entry.gachaId) {
        entry.gachaId = reward.elementDef?.gachaId ?? elementId;
      }
      entry.owned = entry.lifetime > 0;
      const formattedCount = formatNumberLocalized(increment, { maximumFractionDigits: 0 });
      const label = formatFusionElementLabel(reward);
      summaries.push(t('scripts.gacha.fusion.elementBonus', { count: formattedCount, element: label }));
      awardedElements = true;
    });
    if (awardedElements && typeof evaluatePageUnlocks === 'function') {
      evaluatePageUnlocks({ save: false, deferUI: true });
    }
  }
  return summaries;
}

function getFusionMultiplierValue() {
  const bonuses = getFusionBonusState();
  return Number(bonuses.fusionMultiplier) || 1;
}

function applyHydrogenBaseGain(bonuses, baseKey, totalKey, baseGain) {
  const appliedBaseGain = Number(baseGain);
  if (!Number.isFinite(appliedBaseGain) || appliedBaseGain === 0) {
    return 0;
  }
  const multiplier = Number(bonuses.fusionMultiplier) || 1;
  const previousBase = Number(bonuses[baseKey]) || 0;
  const previousContribution = previousBase * multiplier;
  const nextBase = previousBase + appliedBaseGain;
  bonuses[baseKey] = nextBase;
  const nextContribution = nextBase * multiplier;
  const delta = nextContribution - previousContribution;
  bonuses[totalKey] += delta;
  return delta;
}

function applyHydrogenFusionRewards(definition, rewardMultiplier = 1) {
  const rewards = definition?.rewards || {};
  const bonuses = getFusionBonusState();
  const summaries = [];
  const appliedMultiplier = Number.isFinite(Number(rewardMultiplier)) ? Number(rewardMultiplier) : 1;
  if (appliedMultiplier !== 0) {
    const apcIncrement = Number(rewards.apcFlat);
    if (Number.isFinite(apcIncrement) && apcIncrement !== 0) {
      const baseGain = apcIncrement * appliedMultiplier;
      const apcDelta = applyHydrogenBaseGain(bonuses, 'apcHydrogenBase', 'apcFlat', baseGain);
      if (apcDelta !== 0) {
        summaries.push(t('scripts.gacha.fusion.apcBonus', { value: formatFusionRewardValue(apcDelta) }));
      }
    }
    const apsIncrement = Number(rewards.apsFlat);
    if (Number.isFinite(apsIncrement) && apsIncrement !== 0) {
      const baseGain = apsIncrement * appliedMultiplier;
      const apsDelta = applyHydrogenBaseGain(bonuses, 'apsHydrogenBase', 'apsFlat', baseGain);
      if (apsDelta !== 0) {
        summaries.push(t('scripts.gacha.fusion.apsBonus', { value: formatFusionRewardValue(apsDelta) }));
      }
    }
  }
  const elementSummaries = applyFusionRewards(rewards, rewardMultiplier, { skipApcAps: true });
  return summaries.concat(elementSummaries);
}

function incrementFusionMultiplier(bonuses, increment = FUSION_MULTIPLIER_INCREMENT) {
  const normalizedIncrement = Math.max(0, Number(increment) || 0);
  if (normalizedIncrement <= 0) {
    return null;
  }
  const previousMultiplier = Number(bonuses.fusionMultiplier) || 1;
  const nextMultiplier = previousMultiplier + normalizedIncrement;
  const apcBase = Number(bonuses.apcHydrogenBase) || 0;
  const apsBase = Number(bonuses.apsHydrogenBase) || 0;
  const multiplierDelta = nextMultiplier - previousMultiplier;
  const apcDelta = apcBase * multiplierDelta;
  const apsDelta = apsBase * multiplierDelta;
  bonuses.apcFlat += apcDelta;
  bonuses.apsFlat += apsDelta;
  bonuses.fusionMultiplier = nextMultiplier;
  return {
    increment: normalizedIncrement,
    newMultiplier: nextMultiplier,
    apcDelta,
    apsDelta
  };
}

function applyCarbonFusionRewards(definition, rewardMultiplier = 1) {
  const rewards = definition?.rewards || {};
  const bonuses = getFusionBonusState();
  const summaries = [];
  const incrementValue = Number(rewards.fusionMultiplier) || FUSION_MULTIPLIER_INCREMENT;
  const result = incrementFusionMultiplier(bonuses, incrementValue);
  if (result && result.increment > 0) {
    const formattedIncrement = formatNumberLocalized(result.increment, { maximumFractionDigits: 0 });
    const formattedTotal = formatNumberLocalized(result.newMultiplier, { maximumFractionDigits: 0 });
    summaries.push(t('scripts.gacha.fusion.multiplierBonus', { value: formattedIncrement, total: formattedTotal }));
  }
  const elementSummaries = applyFusionRewards(rewards, rewardMultiplier, { skipApcAps: true });
  return summaries.concat(elementSummaries);
}

function applyFusionRewardsForDefinition(definition, rewardMultiplier = 1) {
  if (!definition) {
    return [];
  }
  if (definition.id === HYDROGEN_FUSION_ID) {
    return applyHydrogenFusionRewards(definition, rewardMultiplier);
  }
  if (definition.id === CARBON_FUSION_ID) {
    return applyCarbonFusionRewards(definition, rewardMultiplier);
  }
  return applyFusionRewards(definition.rewards, rewardMultiplier);
}

function getFusionBatchRewardSummary(definition, successCount, previousSuccessCount = 0) {
  if (!definition || successCount <= 0) {
    return [];
  }
  const rewards = definition.rewards || {};
  const summary = [];
  const fusionMultiplier = getFusionMultiplierValue();
  const isHydrogen = definition.id === HYDROGEN_FUSION_ID;
  if (rewards.apcFlat) {
    let totalApc = computeFusionRewardSum(definition, rewards.apcFlat, successCount, previousSuccessCount);
    if (isHydrogen) {
      totalApc *= fusionMultiplier;
    }
    if (totalApc) {
      summary.push(t('scripts.gacha.fusion.apcBonus', {
        value: formatFusionRewardValue(totalApc)
      }));
    }
  }
  if (rewards.apsFlat) {
    let totalAps = computeFusionRewardSum(definition, rewards.apsFlat, successCount, previousSuccessCount);
    if (isHydrogen) {
      totalAps *= fusionMultiplier;
    }
    if (totalAps) {
      summary.push(t('scripts.gacha.fusion.apsBonus', {
        value: formatFusionRewardValue(totalAps)
      }));
    }
  }
  if (rewards.apcBaseBoost) {
    const totalBaseApc = computeFusionRewardSum(
      definition,
      rewards.apcBaseBoost,
      successCount,
      previousSuccessCount
    );
    if (totalBaseApc) {
      summary.push(t('scripts.gacha.fusion.apcBaseBonus', {
        value: formatFusionRewardValue(totalBaseApc)
      }));
    }
  }
  if (rewards.apsBaseBoost) {
    const totalBaseAps = computeFusionRewardSum(
      definition,
      rewards.apsBaseBoost,
      successCount,
      previousSuccessCount
    );
    if (totalBaseAps) {
      summary.push(t('scripts.gacha.fusion.apsBaseBonus', {
        value: formatFusionRewardValue(totalBaseAps)
      }));
    }
  }
  if (rewards.apcFrenzyDurationSeconds) {
    const totalApcFrenzy = computeFusionRewardSum(
      definition,
      rewards.apcFrenzyDurationSeconds,
      successCount,
      previousSuccessCount
    );
    if (totalApcFrenzy) {
      summary.push(t('scripts.gacha.fusion.apcFrenzyBonus', {
        value: formatFusionRewardValue(totalApcFrenzy)
      }));
    }
  }
  if (rewards.apsFrenzyDurationSeconds) {
    const totalApsFrenzy = computeFusionRewardSum(
      definition,
      rewards.apsFrenzyDurationSeconds,
      successCount,
      previousSuccessCount
    );
    if (totalApsFrenzy) {
      summary.push(t('scripts.gacha.fusion.apsFrenzyBonus', {
        value: formatFusionRewardValue(totalApsFrenzy)
      }));
    }
  }
  if (rewards.fusionMultiplier && !isHydrogen) {
    const totalIncrement = rewards.fusionMultiplier * successCount;
    if (totalIncrement > 0) {
      const formattedIncrement = formatNumberLocalized(totalIncrement, { maximumFractionDigits: 0 });
      const formattedTotal = formatNumberLocalized(fusionMultiplier, { maximumFractionDigits: 0 });
      summary.push(t('scripts.gacha.fusion.multiplierBonus', { value: formattedIncrement, total: formattedTotal }));
    }
  }
  if (Array.isArray(rewards.elements)) {
    rewards.elements.forEach(reward => {
      const baseCount = Number(reward?.count ?? reward?.quantity ?? reward?.amount ?? 0);
      if (!Number.isFinite(baseCount) || baseCount <= 0) {
        return;
      }
      const totalCount = baseCount * successCount;
      if (totalCount <= 0) {
        return;
      }
      const formattedTotal = formatNumberLocalized(totalCount, { maximumFractionDigits: 0 });
      const label = formatFusionElementLabel(reward);
      summary.push(t('scripts.gacha.fusion.elementBonus', { count: formattedTotal, element: label }));
    });
  }
  return summary;
}

function buildFusionCard(definition) {
  const card = document.createElement('article');
  card.className = 'fusion-card';
  card.dataset.fusionId = definition.id;
  card.setAttribute('role', 'listitem');

  const header = document.createElement('div');
  header.className = 'fusion-card__header';

  const title = document.createElement('h3');
  title.className = 'fusion-card__title';
  title.textContent = definition.name;

  const chance = document.createElement('p');
  chance.className = 'fusion-card__chance';
  chance.textContent = t('scripts.gacha.fusion.successChance', {
    chance: formatFusionChance(definition.successChance)
  });

  header.append(title, chance);

  const bodyFragment = document.createDocumentFragment();
  bodyFragment.appendChild(header);

  if (definition.description) {
    const description = document.createElement('p');
    description.className = 'fusion-card__description';
    description.textContent = definition.description;
    bodyFragment.appendChild(description);
  }

  const requirementList = document.createElement('ul');
  requirementList.className = 'fusion-card__requirements';

  const requirements = definition.inputs.map(input => {
    const item = document.createElement('li');
    item.className = 'fusion-requirement';

    const symbol = document.createElement('span');
    symbol.className = 'fusion-requirement__symbol';
    symbol.textContent = input.elementDef?.symbol ?? input.elementId;

    const name = document.createElement('span');
    name.className = 'fusion-requirement__name';
    name.textContent = input.elementDef?.name ?? input.elementId;

    const count = document.createElement('span');
    count.className = 'fusion-requirement__count';
    const initialRequired = computeFusionRequirementTotal(input.count);
    const formattedRequired = formatNumberLocalized(initialRequired, { maximumFractionDigits: 0 });
    count.textContent = `×${formattedRequired}`;

    const availability = document.createElement('span');
    availability.className = 'fusion-requirement__availability';
    availability.textContent = t('scripts.gacha.fusion.availabilityInitial');

    item.append(symbol, name, count, availability);
    requirementList.appendChild(item);
    return {
      elementId: input.elementId,
      baseCount: input.count,
      countLabel: count,
      availabilityLabel: availability
    };
  });

  bodyFragment.appendChild(requirementList);

  const actions = document.createElement('div');
  actions.className = 'fusion-card__actions';

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'fusion-card__button';
  const initialLabel = getFusionTryButtonLabel();
  button.textContent = initialLabel;
  button.setAttribute('title', initialLabel);
  button.setAttribute('aria-label', getFusionTryButtonAria(definition));
  button.addEventListener('click', () => {
    handleFusionAttempt(definition.id);
  });

  const status = document.createElement('span');
  status.className = 'fusion-card__feedback fusion-card__status';
  status.textContent = t('scripts.gacha.fusion.checking');

  actions.append(button, status);

  const stats = document.createElement('p');
  stats.className = 'fusion-card__stats';
  stats.textContent = t('scripts.gacha.fusion.stats', { attempts: 0, successes: 0 });

  const bonusParts = [];
  const isHydrogen = definition.id === HYDROGEN_FUSION_ID;
  if (definition.rewards.apcFlat) {
    bonusParts.push(t('scripts.gacha.fusion.apcBonus', {
      value: formatNumberLocalized(definition.rewards.apcFlat)
    }));
  }
  if (definition.rewards.apsFlat) {
    bonusParts.push(t('scripts.gacha.fusion.apsBonus', {
      value: formatNumberLocalized(definition.rewards.apsFlat)
    }));
  }
  if (definition.rewards.apcBaseBoost) {
    bonusParts.push(t('scripts.gacha.fusion.apcBaseBonus', {
      value: formatNumberLocalized(definition.rewards.apcBaseBoost)
    }));
  }
  if (definition.rewards.apsBaseBoost) {
    bonusParts.push(t('scripts.gacha.fusion.apsBaseBonus', {
      value: formatNumberLocalized(definition.rewards.apsBaseBoost)
    }));
  }
  if (definition.rewards.apcFrenzyDurationSeconds) {
    bonusParts.push(t('scripts.gacha.fusion.apcFrenzyBonus', {
      value: formatNumberLocalized(definition.rewards.apcFrenzyDurationSeconds)
    }));
  }
  if (definition.rewards.apsFrenzyDurationSeconds) {
    bonusParts.push(t('scripts.gacha.fusion.apsFrenzyBonus', {
      value: formatNumberLocalized(definition.rewards.apsFrenzyDurationSeconds)
    }));
  }
  if (Array.isArray(definition.rewards.elements)) {
    definition.rewards.elements.forEach(reward => {
      const count = Number(reward?.count ?? reward?.quantity ?? reward?.amount ?? 0);
      if (!Number.isFinite(count) || count <= 0) {
        return;
      }
      const formattedCount = formatNumberLocalized(count, { maximumFractionDigits: 0 });
      const label = formatFusionElementLabel(reward);
      bonusParts.push(t('scripts.gacha.fusion.elementBonus', { count: formattedCount, element: label }));
    });
  }
  if (definition.rewards.fusionMultiplier) {
    bonusParts.push(t('scripts.gacha.fusion.multiplierReward', {
      value: formatNumberLocalized(definition.rewards.fusionMultiplier, { maximumFractionDigits: 0 })
    }));
  }
  if (isHydrogen) {
    bonusParts.push(t('scripts.gacha.fusion.multiplierNote'));
  }
  const bonus = document.createElement('p');
  bonus.className = 'fusion-card__bonus';
  bonus.textContent = bonusParts.length
    ? t('scripts.gacha.fusion.successBonus', { bonus: bonusParts.join(' · ') })
    : t('scripts.gacha.fusion.noBonus');

  const totalBonus = document.createElement('p');
  totalBonus.className = 'fusion-card__feedback fusion-card__total';
  totalBonus.textContent = t('scripts.gacha.fusion.totalBonus', { bonus: '—' });

  card.append(bodyFragment, actions, stats, bonus, totalBonus);

  return {
    root: card,
    button,
    requirements,
    stats,
    status,
    bonus,
    totalBonus
  };
}

const VISIBLE_FUSION_IDS = new Set(['hydrogen', 'carbon', 'oxygen', 'hydroperoxyl']);

function isFusionDefinitionVisible(def) {
  if (!def || !VISIBLE_FUSION_IDS.has(def.id)) {
    return false;
  }
  if (def.id === 'carbon') {
    return getFusionSuccessCount('hydrogen') >= 10;
  }
  if (def.id === 'oxygen' || def.id === 'hydroperoxyl') {
    return getFusionSuccessCount('carbon') >= 50;
  }
  return true;
}

function getVisibleFusionDefinitions() {
  return FUSION_DEFS.filter(isFusionDefinitionVisible);
}

function ensureFusionListVisibility() {
  const visibleDefs = getVisibleFusionDefinitions();
  const expectedIds = new Set(visibleDefs.map(def => def.id));
  if (fusionCards.size !== expectedIds.size) {
    renderFusionList();
    return true;
  }
  for (const id of expectedIds) {
    if (!fusionCards.has(id)) {
      renderFusionList();
      return true;
    }
  }
  return false;
}

function renderFusionList() {
  initFusionModeControl();
  refreshFusionModeButton();
  if (!elements.fusionList || isRenderingFusionList) return;
  isRenderingFusionList = true;
  try {
    fusionCards.clear();
    elements.fusionList.innerHTML = '';
    const visibleFusions = getVisibleFusionDefinitions();
    if (!visibleFusions.length) {
      const empty = document.createElement('p');
      empty.className = 'fusion-empty';
      empty.textContent = t('scripts.gacha.fusion.empty');
      empty.setAttribute('role', 'listitem');
      elements.fusionList.appendChild(empty);
      return;
    }
    const fragment = document.createDocumentFragment();
    visibleFusions.forEach(def => {
      const card = buildFusionCard(def);
      fragment.appendChild(card.root);
      fusionCards.set(def.id, card);
    });
    elements.fusionList.appendChild(fragment);
    if (elements.fusionLog && !elements.fusionLog.textContent.trim()) {
      setFusionLog(t('scripts.app.fusion.prompt'));
    }
    updateFusionUI();
  } finally {
    isRenderingFusionList = false;
  }
}

function updateFusionUI() {
  if (!elements.fusionList || !FUSION_DEFS.length) {
    return;
  }
  initFusionModeControl();
  refreshFusionModeButton();
  if (ensureFusionListVisibility()) {
    return;
  }
  const multiplier = getFusionAttemptMultiplier();
  const tryButtonLabel = getFusionTryButtonLabel(multiplier);
  FUSION_DEFS.forEach(def => {
    const card = fusionCards.get(def.id);
    if (!card) {
      return;
    }
    const state = getFusionStateById(def.id);
    const bonusBlocked = isFusionBlockedByBonus(def);
    card.stats.textContent = t('scripts.gacha.fusion.stats', {
      attempts: state.attempts,
      successes: state.successes
    });
    card.button.textContent = tryButtonLabel;
    card.button.setAttribute('title', tryButtonLabel);
    card.button.setAttribute('aria-label', getFusionTryButtonAria(def, multiplier));
    let canAttempt = true;
    card.requirements.forEach(requirement => {
      const entry = gameState.elements?.[requirement.elementId];
      const available = getElementCurrentCount(entry);
      const availableText = formatNumberLocalized(available, { maximumFractionDigits: 0 });
      const requiredCount = computeFusionRequirementTotal(requirement.baseCount, multiplier);
      const requiredText = formatNumberLocalized(requiredCount, { maximumFractionDigits: 0 });
      if (requirement.countLabel) {
        requirement.countLabel.textContent = `×${requiredText}`;
      }
      requirement.availabilityLabel.textContent = t('scripts.gacha.fusion.availabilityProgress', {
        available: availableText,
        required: requiredText
      });
      if (available < requiredCount) {
        canAttempt = false;
      }
    });
    const canClick = canAttempt && !bonusBlocked;
    card.button.disabled = !canClick;
    card.button.setAttribute('aria-disabled', canClick ? 'false' : 'true');
    card.status.textContent = bonusBlocked
      ? t('scripts.gacha.fusion.statusBonusCap')
      : canAttempt
      ? t('scripts.gacha.fusion.statusReady')
      : t('scripts.gacha.fusion.statusMissing');
    const totalParts = [];
    const isHydrogen = def.id === HYDROGEN_FUSION_ID;
    const fusionMultiplier = getFusionMultiplierValue();
    if (def.rewards.apcFlat) {
      let totalApc = computeFusionRewardSum(def, def.rewards.apcFlat, state.successes);
      if (isHydrogen) {
        totalApc *= fusionMultiplier;
      }
      totalParts.push(t('scripts.gacha.fusion.apcTotal', {
        value: formatFusionRewardValue(totalApc)
      }));
    }
    if (def.rewards.apsFlat) {
      let totalAps = computeFusionRewardSum(def, def.rewards.apsFlat, state.successes);
      if (isHydrogen) {
        totalAps *= fusionMultiplier;
      }
      totalParts.push(t('scripts.gacha.fusion.apsTotal', {
        value: formatFusionRewardValue(totalAps)
      }));
    }
    if (def.rewards.apcBaseBoost) {
      const totalBaseApc = computeFusionRewardSum(def, def.rewards.apcBaseBoost, state.successes);
      if (totalBaseApc) {
        totalParts.push(t('scripts.gacha.fusion.apcBaseTotal', {
          value: formatFusionRewardValue(totalBaseApc)
        }));
      }
    }
    if (def.rewards.apsBaseBoost) {
      const totalBaseAps = computeFusionRewardSum(def, def.rewards.apsBaseBoost, state.successes);
      if (totalBaseAps) {
        totalParts.push(t('scripts.gacha.fusion.apsBaseTotal', {
          value: formatFusionRewardValue(totalBaseAps)
        }));
      }
    }
    if (def.rewards.apcFrenzyDurationSeconds) {
      const totalApcFrenzy = computeFusionRewardSum(def, def.rewards.apcFrenzyDurationSeconds, state.successes);
      if (totalApcFrenzy) {
        totalParts.push(t('scripts.gacha.fusion.apcFrenzyTotal', {
          value: formatFusionRewardValue(totalApcFrenzy)
        }));
      }
    }
    if (def.rewards.apsFrenzyDurationSeconds) {
      const totalApsFrenzy = computeFusionRewardSum(def, def.rewards.apsFrenzyDurationSeconds, state.successes);
      if (totalApsFrenzy) {
        totalParts.push(t('scripts.gacha.fusion.apsFrenzyTotal', {
          value: formatFusionRewardValue(totalApsFrenzy)
        }));
      }
    }
    if (Array.isArray(def.rewards.elements)) {
      def.rewards.elements.forEach(reward => {
        const baseCount = Number(reward?.count ?? reward?.quantity ?? reward?.amount ?? 0);
        if (!Number.isFinite(baseCount) || baseCount <= 0) {
          return;
        }
        const totalCount = baseCount * state.successes;
        if (totalCount <= 0) {
          return;
        }
        const formattedTotal = formatNumberLocalized(totalCount, { maximumFractionDigits: 0 });
        const label = formatFusionElementLabel(reward);
        totalParts.push(t('scripts.gacha.fusion.elementTotal', { count: formattedTotal, element: label }));
      });
    }
    if (def.rewards.fusionMultiplier) {
      if (def.id === CARBON_FUSION_ID) {
        totalParts.push(t('scripts.gacha.fusion.multiplierTotal', {
          value: formatNumberLocalized(fusionMultiplier, { maximumFractionDigits: 0 })
        }));
      } else {
        const totalIncrement = def.rewards.fusionMultiplier * state.successes;
        if (totalIncrement > 0) {
          totalParts.push(t('scripts.gacha.fusion.multiplierReward', {
            value: formatNumberLocalized(totalIncrement, { maximumFractionDigits: 0 })
          }));
        }
      }
    }
    card.totalBonus.textContent = t('scripts.gacha.fusion.totalBonus', {
      bonus: totalParts.length ? totalParts.join(' · ') : '—'
    });
  });
}

function handleFusionAttempt(fusionId, attemptCount = getFusionAttemptMultiplier()) {
  const definition = FUSION_DEFINITION_MAP.get(fusionId);
  if (!definition) {
    return;
  }
  const attempts = Math.max(1, Math.floor(Number(attemptCount) || 0));
  if (isFusionBlockedByBonus(definition)) {
    setFusionLog(t('scripts.gacha.fusion.logBonusCap'), 'failure');
    showToast(t('scripts.gacha.fusion.toastBonusCap'));
    return;
  }
  if (!canAttemptFusion(definition, attempts)) {
    setFusionLog(t('scripts.gacha.fusion.logMissingResources'), 'failure');
    showToast(t('scripts.gacha.fusion.toastMissingResources'));
    return;
  }

  definition.inputs.forEach(input => {
    const entry = gameState.elements?.[input.elementId];
    if (!entry) {
      return;
    }
    const current = getElementCurrentCount(entry);
    const required = computeFusionRequirementTotal(input.count, attempts);
    const nextCount = Math.max(0, current - required);
    entry.count = nextCount;
  });

  const state = getFusionStateById(definition.id);
  let successCount = 0;
  for (let index = 0; index < attempts; index += 1) {
    state.attempts += 1;
    if (Math.random() < definition.successChance) {
      successCount += 1;
      state.successes += 1;
      const rewardMultiplier = getFusionRewardMultiplier(definition, state.successes);
      applyFusionRewardsForDefinition(definition, rewardMultiplier);
    }
  }

  recalcProduction();
  updateUI();
  evaluateTrophies();
  saveGame();

  if (successCount > 0) {
    const previousSuccessCount = Math.max(0, state.successes - successCount);
    const rewardSummary = getFusionBatchRewardSummary(definition, successCount, previousSuccessCount);
    const rewardText = rewardSummary.length
      ? rewardSummary.join(' · ')
      : t('scripts.gacha.fusion.noRewardSummary');
    if (attempts > 1) {
      const attemptsText = formatIntegerLocalized(attempts);
      const successesText = formatIntegerLocalized(successCount);
      const failuresText = formatIntegerLocalized(Math.max(0, attempts - successCount));
      setFusionLog(t('scripts.gacha.fusion.logBatchSuccess', {
        name: definition.name,
        attempts: attemptsText,
        successes: successesText,
        failures: failuresText,
        reward: rewardText
      }), 'success');
    } else {
      setFusionLog(t('scripts.gacha.fusion.logSuccess', {
        name: definition.name,
        reward: rewardText
      }), 'success');
    }
    showToast(t('scripts.gacha.fusion.toastSuccess'));
  } else {
    if (attempts > 1) {
      const attemptsText = formatIntegerLocalized(attempts);
      setFusionLog(t('scripts.gacha.fusion.logBatchFailure', {
        name: definition.name,
        attempts: attemptsText
      }), 'failure');
    } else {
      setFusionLog(t('scripts.gacha.fusion.logFailure', { name: definition.name }), 'failure');
    }
    showToast(t('scripts.gacha.fusion.toastFailure'));
  }
}

function getAllowedRarityCountForDraw(drawIndex) {
  if (!Array.isArray(GACHA_COLLECTION_UNLOCKS) || !GACHA_COLLECTION_UNLOCKS.length) {
    return null;
  }
  const numericIndex = Math.max(0, Math.floor(Number(drawIndex) || 0));
  let matched = GACHA_COLLECTION_UNLOCKS[0];
  for (let index = 1; index < GACHA_COLLECTION_UNLOCKS.length; index += 1) {
    const entry = GACHA_COLLECTION_UNLOCKS[index];
    if (numericIndex < entry.drawThreshold) {
      break;
    }
    matched = entry;
  }
  return matched ? matched.allowedRarityCount ?? null : null;
}

function filterAvailableRarities(rarities) {
  if (!Array.isArray(rarities)) {
    return [];
  }
  return rarities.filter(def => {
    if (!def?.id) {
      return false;
    }
    const pool = gachaPools.get(def.id);
    return Array.isArray(pool) && pool.length > 0;
  });
}

function pickGachaRarity(drawIndex = null) {
  const allowedCount = getAllowedRarityCountForDraw(drawIndex);
  let candidates = GACHA_RARITIES;
  let restricted = false;
  if (allowedCount != null) {
    const totalRarities = GACHA_RARITIES.length;
    if (totalRarities > 0) {
      const limit = Math.min(Math.max(1, allowedCount), totalRarities);
      if (limit < totalRarities) {
        candidates = GACHA_RARITIES.slice(0, limit);
        restricted = true;
      }
    }
  }
  let available = filterAvailableRarities(candidates);
  if (!available.length && restricted) {
    available = filterAvailableRarities(GACHA_RARITIES);
  }
  if (!available.length) {
    return null;
  }
  const totalWeight = available.reduce((sum, def) => sum + (def.weight || 0), 0);
  if (totalWeight <= 0) {
    const index = Math.floor(Math.random() * available.length);
    return available[index];
  }
  let roll = Math.random() * totalWeight;
  for (const def of available) {
    const weight = Math.max(0, def.weight || 0);
    if (weight <= 0) {
      continue;
    }
    if (roll < weight) {
      return def;
    }
    roll -= weight;
  }
  return available[available.length - 1];
}

function pickRandomElementFromRarity(rarityId) {
  const pool = gachaPools.get(rarityId);
  if (!pool || !pool.length) {
    return null;
  }
  const index = Math.floor(Math.random() * pool.length);
  const elementId = pool[index];
  return periodicElementIndex.get(elementId) || null;
}

function getTotalGachaDrawCount() {
  if (!gameState || typeof gameState !== 'object' || !gameState.elements || typeof gameState.elements !== 'object') {
    return 0;
  }
  const entries = Object.values(gameState.elements);
  let total = 0;
  for (let index = 0; index < entries.length; index += 1) {
    total += getElementLifetimeCount(entries[index]);
  }
  return total;
}

function renderGachaResult(outcome) {
  if (!elements.gachaResult) return;
  const container = elements.gachaResult;
  container.innerHTML = '';
  container.style.removeProperty('--rarity-color');

  if (!outcome || !Array.isArray(outcome.focus) || !outcome.focus.length) {
    container.textContent = t('scripts.gacha.results.unavailable');
    return;
  }

  const grid = document.createElement('div');
  grid.className = 'gacha-result__grid';
  grid.setAttribute('role', 'list');

  const newCount = Number(outcome.newCount) || 0;

  outcome.focus.forEach(entry => {
    if (!entry?.elementDef) return;
    const card = document.createElement('article');
    card.className = 'gacha-result-card';
    card.setAttribute('role', 'listitem');
    if (entry.isNew) {
      card.classList.add('is-new');
    } else {
      card.classList.add('is-duplicate');
    }
    if (entry.rarity?.color) {
      card.style.setProperty('--rarity-color', entry.rarity.color);
    }

    const rarity = document.createElement('span');
    rarity.className = 'gacha-result-card__rarity';
    rarity.textContent = entry.rarity?.label || entry.rarity?.id || t('scripts.gacha.results.unknownRarity');

    const name = document.createElement('span');
    name.className = 'gacha-result-card__name';
    const { name: localizedName, symbol: localizedSymbol } = getGachaPeriodicElementDisplay(entry.elementDef);
    const baseName = localizedName || localizedSymbol || t('scripts.gacha.results.unknownElement');
    const symbol = localizedSymbol && localizedName ? ` (${localizedSymbol})` : '';
    name.textContent = `${baseName}${symbol}`;

    const status = document.createElement('span');
    status.className = 'gacha-result-card__status';
    status.textContent = entry.isNew
      ? t('scripts.gacha.results.newTag')
      : t('scripts.gacha.results.duplicateTag');

    if (entry.count > 1) {
      const count = document.createElement('span');
      count.className = 'gacha-result-card__count';
      count.textContent = `x${entry.count}`;
      card.appendChild(count);
    }

    card.appendChild(rarity);
    card.appendChild(name);
    card.appendChild(status);

    grid.appendChild(card);
  });

  if (newCount > 8 && grid.children.length >= 9) {
    grid.classList.add('gacha-result__grid--wide');
  }

  if (!grid.children.length) {
    container.textContent = t('scripts.gacha.results.unavailable');
    return;
  }

  container.appendChild(grid);

  const drawCount = Math.max(1, Math.floor(Number(outcome.drawCount) || 0));
  const summary = document.createElement('p');
  summary.className = 'gacha-result__summary';
  const drawLabel = drawCount > 1
    ? t('scripts.gacha.results.drawLabel', { count: drawCount })
    : t('scripts.gacha.results.drawSingle');
  const duplicateCount = Number(outcome.duplicateCount) || 0;
  const summaryParts = [drawLabel];
  if (newCount > 0) {
    const newLabel = newCount === 1
      ? t('scripts.gacha.results.newSingle')
      : t('scripts.gacha.results.newPlural', { count: newCount });
    summaryParts.push(newLabel);
  }
  if (duplicateCount > 0) {
    const duplicateLabel = duplicateCount === 1
      ? t('scripts.gacha.results.duplicateSingle')
      : t('scripts.gacha.results.duplicatePlural', { count: duplicateCount });
    summaryParts.push(duplicateLabel);
  }
  summary.textContent = summaryParts.join(' · ');
  container.appendChild(summary);
}

function formatTicketLabel(count) {
  const numeric = Math.max(0, Math.floor(Number(count) || 0));
  const formatted = formatIntegerLocalized(numeric);
  const unit = numeric === 1
    ? t('scripts.gacha.tickets.single')
    : t('scripts.gacha.tickets.plural');
  return `${formatted} ${unit}`;
}

function formatBonusTicketLabel(count) {
  const numeric = Math.max(0, Math.floor(Number(count) || 0));
  const formatted = formatIntegerLocalized(numeric);
  const unit = numeric === 1
    ? t('scripts.gacha.tickets.bonusSingle')
    : t('scripts.gacha.tickets.bonusPlural');
  return `${formatted} ${unit}`;
}

function getGachaRarityRank(rarity) {
  if (!rarity) return -1;
  const id = typeof rarity === 'string' ? rarity : rarity.id;
  if (!id) return -1;
  const rank = GACHA_RARITY_ORDER.has(id) ? GACHA_RARITY_ORDER.get(id) : -1;
  return Number.isFinite(rank) ? rank : -1;
}

function compareGachaRaritiesDesc(a, b) {
  const rankA = getGachaRarityRank(a);
  const rankB = getGachaRarityRank(b);
  if (rankA === rankB) return 0;
  return rankB - rankA;
}

function compareGachaEntries(a, b) {
  const rarityDiff = compareGachaRaritiesDesc(a?.rarity, b?.rarity);
  if (rarityDiff !== 0) {
    return rarityDiff;
  }
  const nameA = a?.elementDef?.name || '';
  const nameB = b?.elementDef?.name || '';
  if (nameA && nameB) {
    return compareTextLocalized(nameA, nameB, { sensitivity: 'base' });
  }
  const idA = a?.elementDef?.id || '';
  const idB = b?.elementDef?.id || '';
  return compareTextLocalized(idA, idB, { sensitivity: 'base' });
}

function buildGachaDisplayData(results) {
  const aggregatedMap = new Map();
  const rarityColorMap = new Map();

  results.forEach(result => {
    if (!result) return;
    const { elementDef, rarity, isNew } = result;
    if (rarity?.id && !rarityColorMap.has(rarity.id)) {
      rarityColorMap.set(rarity.id, rarity.color || null);
    }
    if (!elementDef?.id) {
      return;
    }
    let entry = aggregatedMap.get(elementDef.id);
    if (!entry) {
      entry = {
        elementDef,
        rarity,
        count: 0,
        isNew: false
      };
      aggregatedMap.set(elementDef.id, entry);
    }
    entry.count += 1;
    if (isNew) {
      entry.isNew = true;
    }
    if (!entry.rarity && rarity) {
      entry.rarity = rarity;
    }
  });

  const aggregated = Array.from(aggregatedMap.values()).sort(compareGachaEntries);
  const newEntries = aggregated.filter(entry => entry.isNew);
  const duplicateEntries = aggregated.filter(entry => !entry.isNew);

  let focus = newEntries.slice();
  if (!focus.length && duplicateEntries.length) {
    focus = duplicateEntries.slice(0, Math.min(3, duplicateEntries.length));
  }
  if (!focus.length && aggregated.length) {
    focus = aggregated.slice(0, Math.min(3, aggregated.length));
  }

  return { aggregated, focus, newEntries, rarityColorMap };
}

const GACHA_CONFETTI_BASE_COUNT = 120;
const GACHA_CONFETTI_MIN_COUNT = 36;
const GACHA_CONFETTI_REFERENCE_AREA = 720 * 960;
const GACHA_ANIMATION_REVEAL_DELAY = 2500;
const GACHA_CONFETTI_BASE_RARITY_ID = 'commun';
const DEFAULT_GACHA_CONFETTI_COLOR = '#4f7ec2';
const DEFAULT_GACHA_CONFETTI_RGB = { r: 79, g: 126, b: 194 };
let gachaAnimationInProgress = false;
let gachaRollMode = 1;

function updateGachaUI() {
  updateGachaFeaturedInfo();
  const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const formattedCount = formatIntegerLocalized(available);
  if (elements.gachaTicketValue) {
    elements.gachaTicketValue.textContent = formattedCount;
  } else if (elements.gachaTicketCounter) {
    elements.gachaTicketCounter.textContent = formattedCount;
  }
  if (elements.gachaTicketCounter) {
    elements.gachaTicketCounter.setAttribute('aria-label', formatTicketLabel(available));
  }
  updateArcadeTicketDisplay();
  if (elements.gachaTicketModeLabel) {
    const modeLabel = gachaRollMode > 1
      ? t('scripts.gacha.controls.modeMulti', { count: gachaRollMode })
      : t('scripts.gacha.controls.modeSingle');
    elements.gachaTicketModeLabel.textContent = modeLabel;
  }
  if (elements.gachaTicketModeButton) {
    const modeLabel = gachaRollMode > 1
      ? t('scripts.gacha.controls.modeMulti', { count: gachaRollMode })
      : t('scripts.gacha.controls.modeSingle');
    elements.gachaTicketModeButton.setAttribute('aria-label', t('scripts.gacha.controls.toggleModeAria', {
      mode: modeLabel
    }));
    elements.gachaTicketModeButton.title = t('scripts.gacha.controls.toggleModeTitle', {
      mode: modeLabel
    });
  }
  if (elements.gachaSunButton) {
    const gachaFree = isDevKitGachaFree();
    const totalCost = gachaRollMode * GACHA_TICKET_COST;
    const affordable = gachaFree || available >= totalCost;
    const busy = gachaAnimationInProgress;
    const costLabel = gachaFree ? t('scripts.app.shop.free') : formatTicketLabel(totalCost);
    const drawLabel = gachaRollMode > 1
      ? t('scripts.gacha.controls.drawMulti', { count: gachaRollMode })
      : t('scripts.gacha.controls.drawSingleLabel');
    let label;
    if (busy) {
      label = t('scripts.gacha.controls.drawInProgress');
    } else if (gachaFree) {
      label = t('scripts.gacha.controls.drawFree', { draw: drawLabel });
    } else if (affordable) {
      label = t('scripts.gacha.controls.drawPaid', { draw: drawLabel });
    } else {
      label = t('scripts.gacha.controls.drawLocked', { draw: drawLabel });
    }
    elements.gachaSunButton.classList.toggle('is-locked', !affordable || busy);
    elements.gachaSunButton.setAttribute('aria-disabled', !affordable || busy ? 'true' : 'false');
    elements.gachaSunButton.setAttribute('aria-label', label);
    elements.gachaSunButton.title = gachaFree
      ? label
      : t('scripts.gacha.controls.drawTitle', { label, cost: costLabel });
    if (busy) {
      elements.gachaSunButton.disabled = true;
    } else if (elements.gachaSunButton.disabled) {
      elements.gachaSunButton.disabled = false;
    }
  }
}


let particulesBrickSkinPreference = null;
let particulesGame = null;

function normalizeParticulesBrickSkin(value) {
  if (value == null) {
    return null;
  }
  const normalized = String(value).trim().toLowerCase();
  if (!normalized || normalized === 'original' || normalized === 'default') {
    return null;
  }
  if (normalized === 'pastels' || normalized === 'pastels1' || normalized === 'pastels2') {
    return 'pastels';
  }
  if (normalized === 'particules') {
    return 'particules';
  }
  if (normalized === 'metallic' || normalized === 'neon') {
    return normalized;
  }
  return null;
}

function setParticulesBrickSkinPreference(value) {
  particulesBrickSkinPreference = normalizeParticulesBrickSkin(value);
  if (particulesGame && typeof particulesGame.setBrickSkin === 'function') {
    particulesGame.setBrickSkin(particulesBrickSkinPreference);
  }
}

function initParticulesGame() {
  if (particulesGame || !elements.arcadeCanvas || typeof ParticulesGame !== 'function') {
    return;
  }
  let initialState = null;
  if (typeof window !== 'undefined' && window.ArcadeAutosave && typeof window.ArcadeAutosave.get === 'function') {
    try {
      initialState = window.ArcadeAutosave.get('particules');
    } catch (error) {
      initialState = null;
    }
  }
  if (initialState && typeof initialState === 'object' && initialState.brickSkin != null) {
    particulesBrickSkinPreference = normalizeParticulesBrickSkin(initialState.brickSkin);
  }
  particulesGame = new ParticulesGame({
    canvas: elements.arcadeCanvas,
    particleLayer: elements.arcadeParticleLayer,
    overlay: elements.arcadeOverlay,
    overlayButton: elements.arcadeOverlayButton,
    overlaySecondaryButton: elements.arcadeOverlayQuitButton,
    overlayMessage: elements.arcadeOverlayMessage,
    levelLabel: elements.arcadeLevelValue,
    livesLabel: elements.arcadeLivesValue,
    scoreLabel: elements.arcadeScoreValue,
    comboLabel: elements.arcadeComboMessage,
    brickSkin: particulesBrickSkinPreference,
    initialState,
    formatTicketLabel,
    formatBonusTicketLabel,
    onTicketsEarned: (count = 0) => {
      const reward = Math.max(0, Math.floor(Number(count) || 0));
      if (reward <= 0) {
        return;
      }
      const gained = gainGachaTickets(reward, { unlockTicketStar: true });
      saveGame();
      const rewardLabel = formatTicketLabel(gained);
      showToast(t('scripts.gacha.arcade.reward', { reward: rewardLabel }));
    },
    onSpecialTicket: (count = 0) => {
      const reward = Math.max(0, Math.floor(Number(count) || 0));
      if (reward <= 0) {
        return;
      }
      const gained = gainBonusParticulesTickets(reward);
      saveGame();
      const label = formatBonusTicketLabel(gained);
      showToast(t('scripts.gacha.arcade.bonus', { reward: label }));
    }
  });
  updateArcadeTicketDisplay();
}

function performGachaRoll(count = 1) {
  const weightsUpdated = refreshGachaRarities(new Date());
  if (weightsUpdated) {
    rebuildGachaPools();
    if (elements.gachaRarityList) {
      renderGachaRarityList();
    }
  }
  const drawCount = Math.max(1, Math.floor(Number(count) || 1));
  const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const gachaFree = isDevKitGachaFree();
  const totalCost = drawCount * GACHA_TICKET_COST;

  if (!gachaFree && available < totalCost) {
    showToast(t('scripts.gacha.errors.notEnoughTickets', {
      cost: formatTicketLabel(totalCost)
    }));
    return null;
  }

  const hasAvailableElements = GACHA_RARITIES.some(def => {
    const pool = gachaPools.get(def.id);
    return Array.isArray(pool) && pool.length > 0;
  });
  if (!hasAvailableElements) {
    showToast(t('scripts.gacha.errors.noElements'));
    return null;
  }

  if (!gachaFree) {
    gameState.gachaTickets = available - totalCost;
  }

  const initialDrawCount = getTotalGachaDrawCount();
  const results = [];
  const collectionRewards = [];
  const shouldAwardCollectionRewards = isGachaCardCollectionEnabled()
    || isGachaImageCollectionEnabled()
    || isGachaBonusImageCollectionEnabled()
    || isCollectionVideoCollectionEnabled();
  for (let rollIndex = 0; rollIndex < drawCount; rollIndex += 1) {
    const rarity = pickGachaRarity(initialDrawCount + rollIndex);
    if (!rarity) {
      showToast(t('scripts.gacha.errors.noElements'));
      break;
    }

    const elementDef = pickRandomElementFromRarity(rarity.id);
    if (!elementDef) {
      showToast(t('scripts.gacha.errors.instableFlux'));
      continue;
    }

    let entry = gameState.elements[elementDef.id];
    if (!entry) {
      entry = {
        id: elementDef.id,
        gachaId: elementDef.gachaId ?? elementDef.id,
        owned: false,
        count: 0,
        lifetime: 0,
        rarity: rarity.id,
        effects: [],
        bonuses: []
      };
      gameState.elements[elementDef.id] = entry;
    }

    const previousCount = getElementCurrentCount(entry);
    const previousLifetime = getElementLifetimeCount(entry);
    entry.count = previousCount + 1;
    entry.lifetime = previousLifetime + 1;

    if (!entry.rarity) {
      entry.rarity = rarity.id;
    }
    entry.owned = entry.lifetime > 0;

    if (shouldAwardCollectionRewards) {
      const elementCardReward = maybeAwardElementSpecialCard(elementDef);
      if (elementCardReward) {
        collectionRewards.push(elementCardReward);
      }
      const rarityCardReward = maybeAwardRaritySpecialCard(rarity);
      if (rarityCardReward) {
        collectionRewards.push(rarityCardReward);
      }
      const bonusImageReward = maybeAwardBonusGachaImage();
      if (bonusImageReward) {
        collectionRewards.push(bonusImageReward);
      }
      const permanentImageReward = maybeAwardPermanentBonusGachaImage();
      if (permanentImageReward) {
        collectionRewards.push(permanentImageReward);
      }
      const secondaryPermanentImageReward = maybeAwardSecondaryPermanentBonusGachaImage();
      if (secondaryPermanentImageReward) {
        collectionRewards.push(secondaryPermanentImageReward);
      }
      const collectionVideoReward = maybeAwardCollectionVideo();
      if (collectionVideoReward) {
        collectionRewards.push(collectionVideoReward);
      }
    }

    const isNew = previousLifetime === 0;
    results.push({ rarity, elementDef, isNew });
  }

  if (!results.length) {
    if (!gachaFree) {
      gameState.gachaTickets = available;
      updateGachaUI();
    }
    return null;
  }

  if (collectionRewards.length) {
    collectionRewards.forEach(reward => {
      if (!reward) {
        return;
      }
      const isImage = reward.type === 'image';
      const isVideo = reward.type === 'video';
      const imageCollectionType = typeof reward.collectionType === 'string'
        ? reward.collectionType.toLowerCase()
        : '';
      const isPermanentImage = isImage
        && (imageCollectionType === 'permanent' || imageCollectionType === 'permanent2');
      const isSecondaryPermanentImage = isImage && imageCollectionType === 'permanent2';
      const namespace = isImage
        ? (isSecondaryPermanentImage
          ? 'scripts.gacha.bonusImages2'
          : (isPermanentImage ? 'scripts.gacha.bonusImages' : 'scripts.gacha.images'))
        : (isVideo ? 'scripts.collection.videos' : 'scripts.gacha.cards');
      const toastKey = reward.isNew
        ? `${namespace}.toastNew`
        : `${namespace}.toastDuplicate`;
      const fallback = reward.isNew
        ? (isImage
            ? (isPermanentImage
                ? 'Bonus image unlocked: {card}'
                : 'Gallery image unlocked: {card}')
            : (isVideo
              ? 'Bonus video unlocked: {card}'
              : 'Special card unlocked: {card}'))
        : (isImage
            ? (isPermanentImage
                ? 'Bonus image found again: {card}'
                : 'Gallery image found again: {card}')
            : (isVideo
              ? 'Bonus video found again: {card}'
              : 'Special card obtained again: {card}'));
      const message = translateWithFallback(toastKey, fallback, { card: reward.label });
      if (message) {
        showToast(message);
      }
    });
    if (typeof window !== 'undefined' && typeof window.enqueueSpecialCardReveal === 'function') {
      window.enqueueSpecialCardReveal(collectionRewards);
    }
  }

  recalcProduction();
  evaluatePageUnlocks({ save: false });
  updateUI();
  saveGame();
  evaluateTrophies();

  const { aggregated, focus, newEntries, rarityColorMap } = buildGachaDisplayData(results);
  const confettiColors = [];
  rarityColorMap.forEach(color => {
    if (color != null) {
      confettiColors.push(color);
    }
  });
  const newCount = results.reduce((sum, result) => sum + (result.isNew ? 1 : 0), 0);
  const duplicateCount = Math.max(0, results.length - newCount);

  return {
    drawCount: results.length,
    results,
    aggregated,
    focus,
    newEntries,
    confettiColors,
    newCount,
    duplicateCount,
    specialCards: collectionRewards
  };
}

function displayGachaResult(outcome) {
  if (!outcome) return;
  renderGachaResult(outcome);
}

function getGachaToastMessage(outcome) {
  if (!outcome) return '';
  const results = Array.isArray(outcome.results) ? outcome.results : [];
  if (!results.length) {
    return '';
  }

  if (results.length === 1) {
    const single = results[0];
    if (!single?.elementDef) return '';
    const elementName = getGachaLocalizedElementName(single.elementDef)
      || t('scripts.gacha.results.unknownElement');
    return single.isNew
      ? t('scripts.gacha.toast.newSingle', { name: elementName })
      : t('scripts.gacha.toast.duplicateSingle', { name: elementName });
  }

  const newCount = Number.isFinite(Number(outcome.newCount))
    ? Number(outcome.newCount)
    : results.reduce((sum, result) => sum + (result.isNew ? 1 : 0), 0);

  if (newCount > 1) {
    return t('scripts.gacha.toast.newMultiple', { count: newCount });
  }

  if (newCount === 1) {
    const focusNew = Array.isArray(outcome.focus)
      ? outcome.focus.find(entry => entry?.isNew && entry.elementDef)
      : null;
    const rawNew = results.find(result => result.isNew && result.elementDef);
    const target = focusNew || rawNew;
    if (target?.elementDef) {
      const elementName = getGachaLocalizedElementName(target.elementDef)
        || t('scripts.gacha.results.unknownElement');
      return t('scripts.gacha.toast.newSingle', { name: elementName });
    }
  }

  const focusEntry = Array.isArray(outcome.focus) && outcome.focus.length
    ? outcome.focus[0]
    : null;
  if (focusEntry?.elementDef) {
    const rarityLabel = focusEntry.rarity?.label || focusEntry.rarity?.id;
    if (rarityLabel) {
      const elementName = getGachaLocalizedElementName(focusEntry.elementDef)
        || t('scripts.gacha.results.unknownElement');
      return t('scripts.gacha.toast.duplicateWithRarity', {
        name: elementName,
        rarity: rarityLabel
      });
    }
    const elementName = getGachaLocalizedElementName(focusEntry.elementDef)
      || t('scripts.gacha.results.unknownElement');
    return t('scripts.gacha.toast.duplicateSingle', { name: elementName });
  }

  const fallback = results[0];
  if (fallback?.elementDef) {
    const elementName = getGachaLocalizedElementName(fallback.elementDef)
      || t('scripts.gacha.results.unknownElement');
    return t('scripts.gacha.toast.duplicateSingle', { name: elementName });
  }

  return '';
}

function wait(duration) {
  return new Promise(resolve => {
    setTimeout(resolve, Math.max(0, Number(duration) || 0));
  });
}

const gachaConfettiState = {
  canvas: null,
  ctx: null,
  particles: [],
  animationFrameId: null,
  handleResize: null,
  resizeObserver: null,
  dpr: 1,
  width: 0,
  height: 0,
  centerX: 0,
  centerY: 0,
  baseColorRgb: DEFAULT_GACHA_CONFETTI_RGB
};

function getGachaConfettiParticleBudget() {
  let budget = GACHA_CONFETTI_BASE_COUNT;

  if (prefersReducedMotion()) {
    budget *= 0.4;
  }

  const area = Math.max(0, Math.round(gachaConfettiState.width * gachaConfettiState.height));
  if (area > 0) {
    const normalizedArea = Math.min(1, area / GACHA_CONFETTI_REFERENCE_AREA);
    budget *= Math.max(0.45, normalizedArea);
  }

  if (typeof matchMedia === 'function') {
    try {
      if (matchMedia('(pointer: coarse)').matches) {
        budget *= 0.85;
      }
    } catch (error) {
      // Ignore if pointer media query is not supported.
    }
  }

  if (typeof navigator !== 'undefined') {
    const cores = Number(navigator.hardwareConcurrency);
    if (Number.isFinite(cores) && cores > 0 && cores <= 4) {
      budget *= 0.75;
    }
  }

  return Math.max(GACHA_CONFETTI_MIN_COUNT, Math.round(budget));
}

function normalizeHexColor(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  const match = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(trimmed);
  if (!match) return null;
  let hex = match[1];
  if (hex.length === 3) {
    hex = hex.split('').map(part => part + part).join('');
  }
  return `#${hex.toLowerCase()}`;
}

function parseHexColorToRgb(input) {
  const normalized = normalizeHexColor(input);
  if (!normalized) return null;
  const value = normalized.slice(1);
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) {
    return null;
  }
  return { r, g, b };
}

function mixRgb(color, target, amount) {
  const mixAmount = Math.max(0, Math.min(1, Number(amount) || 0));
  const mixChannel = (base, goal) => {
    const value = base + (goal - base) * mixAmount;
    return Math.max(0, Math.min(255, Math.round(value)));
  };
  return {
    r: mixChannel(color.r, target.r),
    g: mixChannel(color.g, target.g),
    b: mixChannel(color.b, target.b)
  };
}

function lightenRgb(color, amount) {
  return mixRgb(color, { r: 255, g: 255, b: 255 }, amount);
}

function darkenRgb(color, amount) {
  return mixRgb(color, { r: 0, g: 0, b: 0 }, amount);
}

function rgbToHex(color) {
  if (!color || typeof color.r !== 'number' || typeof color.g !== 'number' || typeof color.b !== 'number') {
    return null;
  }
  const clampChannel = value => {
    return Math.max(0, Math.min(255, Math.round(value)));
  };
  const toHex = value => clampChannel(value).toString(16).padStart(2, '0');
  return `#${toHex(color.r)}${toHex(color.g)}${toHex(color.b)}`;
}

function getPeriodicCellCollectionColor(baseColor, isOwned) {
  const normalized = normalizeHexColor(baseColor);
  if (!normalized) {
    return null;
  }
  if (isOwned) {
    return normalized;
  }
  const rgb = parseHexColorToRgb(normalized);
  if (!rgb) {
    return normalized;
  }
  const darkened = darkenRgb(rgb, 0.4);
  return rgbToHex(darkened) || normalized;
}

function applyPeriodicCellCollectionColor(cell, isOwned) {
  if (!cell) return;
  const baseColor = cell.dataset.rarityColor;
  const resolved = getPeriodicCellCollectionColor(baseColor, isOwned);
  if (resolved) {
    cell.style.setProperty('--rarity-color', resolved);
  } else {
    cell.style.removeProperty('--rarity-color');
  }
}

function resolveGachaConfettiColor(input, fallback = DEFAULT_GACHA_CONFETTI_COLOR) {
  return normalizeHexColor(input) || normalizeHexColor(fallback) || DEFAULT_GACHA_CONFETTI_COLOR;
}

function clamp01(value) {
  if (!Number.isFinite(value)) return 0;
  if (value <= 0) return 0;
  if (value >= 1) return 1;
  return value;
}

function lerpRgbColor(from, to, t) {
  const amount = clamp01(t);
  const mixChannel = (start, end) => {
    const value = start + (end - start) * amount;
    return Math.max(0, Math.min(255, Math.round(value)));
  };
  return {
    r: mixChannel(from.r, to.r),
    g: mixChannel(from.g, to.g),
    b: mixChannel(from.b, to.b)
  };
}

function getNow() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

function ensureGachaConfettiCanvas() {
  const canvas = elements.gachaAnimationConfetti;
  if (!canvas || typeof canvas.getContext !== 'function') {
    return false;
  }
  const context = canvas.getContext('2d');
  if (!context) {
    return false;
  }
  if (gachaConfettiState.canvas !== canvas) {
    gachaConfettiState.canvas = canvas;
    gachaConfettiState.ctx = context;
  }
  if (canvas.style) {
    canvas.style.removeProperty('width');
    canvas.style.removeProperty('height');
  }
  if (typeof window !== 'undefined') {
    if (!gachaConfettiState.handleResize) {
      gachaConfettiState.handleResize = () => updateGachaConfettiCanvasSize();
      window.addEventListener('resize', gachaConfettiState.handleResize);
    }
    if (!gachaConfettiState.resizeObserver && typeof ResizeObserver !== 'undefined') {
      gachaConfettiState.resizeObserver = new ResizeObserver(() => {
        updateGachaConfettiCanvasSize();
      });
      gachaConfettiState.resizeObserver.observe(canvas);
    }
  }
  updateGachaConfettiCanvasSize();
  return true;
}

function updateGachaConfettiCanvasSize() {
  const { canvas, ctx } = gachaConfettiState;
  if (!canvas || !ctx) {
    return;
  }
  const rect = typeof canvas.getBoundingClientRect === 'function'
    ? canvas.getBoundingClientRect()
    : { width: 0, height: 0 };
  const measuredWidth = Number(rect.width) || 0;
  const measuredHeight = Number(rect.height) || 0;
  const fallbackWidth = canvas.offsetWidth || canvas.clientWidth || 0;
  const fallbackHeight = canvas.offsetHeight || canvas.clientHeight || 0;
  const width = measuredWidth > 0 ? measuredWidth : (fallbackWidth > 0 ? fallbackWidth : 0);
  const height = measuredHeight > 0 ? measuredHeight : (fallbackHeight > 0 ? fallbackHeight : 0);

  const rawDpr = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
    ? window.devicePixelRatio
    : 1;
  const configuredMaxDpr = typeof globalThis !== 'undefined' && typeof globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO === 'number'
    ? Math.max(1, globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO)
    : 1;
  const dpr = Math.min(rawDpr, configuredMaxDpr);
  if (width <= 0 || height <= 0) {
    gachaConfettiState.dpr = dpr;
    gachaConfettiState.width = 0;
    gachaConfettiState.height = 0;
    gachaConfettiState.centerX = 0;
    gachaConfettiState.centerY = 0;
    return;
  }
  const displayWidth = Math.max(1, Math.round(width));
  const displayHeight = Math.max(1, Math.round(height));
  const pixelWidth = Math.max(1, Math.round(displayWidth * dpr));
  const pixelHeight = Math.max(1, Math.round(displayHeight * dpr));

  if (canvas.width !== pixelWidth) {
    canvas.width = pixelWidth;
  }
  if (canvas.height !== pixelHeight) {
    canvas.height = pixelHeight;
  }

  gachaConfettiState.dpr = dpr;
  gachaConfettiState.width = width;
  gachaConfettiState.height = height;
  gachaConfettiState.centerX = width / 2;
  gachaConfettiState.centerY = height / 2;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
}

function createGachaConfettiParticle(finalColorRgb, birthTime) {
  const now = Number.isFinite(birthTime) ? birthTime : getNow();
  return {
    birth: now,
    life: 2000 + Math.random() * 1000,
    angle: Math.random() * Math.PI * 2,
    speed: 1.2 + Math.random() * 3.2,
    size: 2.6 + Math.random() * 3.4,
    spiralFactor: 0.45 + Math.random() * 0.55,
    wobbleAmplitude: 8 + Math.random() * 24,
    wobbleFrequency: 1.5 + Math.random() * 2.5,
    spinPhase: Math.random() * Math.PI * 2,
    drift: (Math.random() - 0.5) * 0.3,
    finalColor: finalColorRgb
  };
}

function easeOutCubic(t) {
  const value = 1 - clamp01(t);
  return 1 - value * value * value;
}

function drawGachaConfettiParticle(particle, now) {
  const { ctx, centerX, centerY, baseColorRgb } = gachaConfettiState;
  if (!ctx) {
    return false;
  }
  const age = now - particle.birth;
  if (age <= 0) {
    return true;
  }
  if (age >= particle.life) {
    return false;
  }
  const progress = clamp01(age / particle.life);
  const eased = easeOutCubic(progress);
  const radius = particle.speed * age * 0.03;
  const angle = particle.angle + eased * Math.PI * 6 * particle.spiralFactor;
  const baseX = centerX + Math.cos(angle) * (radius + 6);
  const baseY = centerY + Math.sin(angle) * (radius + 6) + age * particle.drift * 0.08;
  const wobble = Math.sin(progress * Math.PI * particle.wobbleFrequency + particle.spinPhase) * particle.wobbleAmplitude;
  const x = baseX + Math.cos(angle) * wobble * 0.2;
  const y = baseY + wobble;
  const color = lerpRgbColor(baseColorRgb, particle.finalColor, eased);
  const alpha = Math.pow(1 - progress, 1.25);
  ctx.beginPath();
  ctx.fillStyle = `rgba(${color.r}, ${color.g}, ${color.b}, ${(alpha * 0.9).toFixed(3)})`;
  ctx.arc(x, y, particle.size, 0, Math.PI * 2);
  ctx.fill();
  return true;
}

function renderGachaConfettiFrame(now) {
  const state = gachaConfettiState;
  if (!state.canvas || !state.ctx) {
    state.animationFrameId = null;
    state.particles = [];
    return;
  }
  const timestamp = Number.isFinite(now) ? now : getNow();
  const { ctx, canvas, dpr } = state;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.globalCompositeOperation = 'lighter';
  state.particles = state.particles.filter(particle => drawGachaConfettiParticle(particle, timestamp));
  ctx.globalCompositeOperation = 'source-over';
  if (state.particles.length && typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    state.animationFrameId = window.requestAnimationFrame(renderGachaConfettiFrame);
  } else {
    state.animationFrameId = null;
  }
}

function stopGachaConfettiAnimation() {
  const state = gachaConfettiState;
  if (typeof window !== 'undefined' && typeof window.cancelAnimationFrame === 'function' && state.animationFrameId != null) {
    window.cancelAnimationFrame(state.animationFrameId);
  }
  state.animationFrameId = null;
  state.particles = [];
  const { ctx, canvas, dpr } = state;
  if (ctx && canvas) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.setTransform(dpr || 1, 0, 0, dpr || 1, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
  }
}

function startGachaConfettiAnimation(outcome) {
  if (!ensureGachaConfettiCanvas()) {
    stopGachaConfettiAnimation();
    return;
  }
  stopGachaConfettiAnimation();

  const baseRarityColor = resolveGachaConfettiColor(
    GACHA_RARITY_MAP.get(GACHA_CONFETTI_BASE_RARITY_ID)?.color,
    DEFAULT_GACHA_CONFETTI_COLOR
  );
  const baseColor = baseRarityColor || DEFAULT_GACHA_CONFETTI_COLOR;
  const baseColorRgb = parseHexColorToRgb(baseColor) || DEFAULT_GACHA_CONFETTI_RGB;

  const paletteSet = new Set();
  if (Array.isArray(outcome?.confettiColors)) {
    outcome.confettiColors.forEach(color => {
      const normalized = resolveGachaConfettiColor(color, baseColor);
      if (normalized) {
        paletteSet.add(normalized);
      }
    });
  }

  if (!paletteSet.size && Array.isArray(outcome?.focus)) {
    outcome.focus.forEach(entry => {
      if (!entry?.rarity?.color) return;
      const normalized = resolveGachaConfettiColor(entry.rarity.color, baseColor);
      if (normalized) {
        paletteSet.add(normalized);
      }
    });
  }

  if (!paletteSet.size) {
    paletteSet.add(baseColor);
  }

  const paletteRgb = Array.from(paletteSet)
    .map(color => parseHexColorToRgb(color) || baseColorRgb)
    .filter(Boolean);
  if (!paletteRgb.length) {
    paletteRgb.push(baseColorRgb);
  }

  gachaConfettiState.baseColorRgb = baseColorRgb;

  let measureAttempts = 0;
  const maxMeasureAttempts = 4;

  const beginAnimation = () => {
    updateGachaConfettiCanvasSize();
    const { width, height } = gachaConfettiState;
    const hasValidSize = width > 2 && height > 2;
    if (!hasValidSize && measureAttempts < maxMeasureAttempts
      && typeof window !== 'undefined'
      && typeof window.requestAnimationFrame === 'function') {
      measureAttempts += 1;
      window.requestAnimationFrame(beginAnimation);
      return;
    }

    const now = getNow();
    const particles = [];
    const paletteLength = paletteRgb.length;
    const particleBudget = getGachaConfettiParticleBudget();
    for (let i = 0; i < particleBudget; i += 1) {
      const color = paletteRgb[paletteLength > 0 ? i % paletteLength : 0] || baseColorRgb;
      particles.push(createGachaConfettiParticle(color, now));
    }
    gachaConfettiState.particles = particles;

    if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
      gachaConfettiState.animationFrameId = window.requestAnimationFrame(renderGachaConfettiFrame);
    } else {
      renderGachaConfettiFrame(now);
    }
  };

  beginAnimation();
}

function waitForGachaAnimationDismiss(layer, options = {}) {
  const { ignoreClicksUntil = 0 } = options;
  return new Promise(resolve => {
    let inputGuardTime = Number(ignoreClicksUntil) || 0;
    const cleanup = () => {
      layer.removeEventListener('pointerdown', handlePointerDown);
      layer.removeEventListener('click', handleClick, true);
      layer.removeEventListener('keydown', handleKeyDown);
      resolve();
    };
    const handlePointerDown = event => {
      const timeStamp = typeof event.timeStamp === 'number' ? event.timeStamp : 0;
      if (inputGuardTime && timeStamp <= inputGuardTime) {
        inputGuardTime = 0;
        return;
      }
      if ('isPrimary' in event && event.isPrimary === false) return;
      if (typeof event.button === 'number' && event.button > 0) return;
      event.preventDefault();
      event.stopPropagation();
      cleanup();
    };
    const handleClick = event => {
      const timeStamp = typeof event.timeStamp === 'number' ? event.timeStamp : 0;
      if (inputGuardTime && timeStamp <= inputGuardTime) {
        inputGuardTime = 0;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      cleanup();
    };
    const handleKeyDown = event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        cleanup();
      } else if (event.key === 'Escape') {
        cleanup();
      }
    };
    requestAnimationFrame(() => {
      try {
        layer.focus({ preventScroll: true });
      } catch (err) {
        layer.focus();
      }
    });
    layer.addEventListener('pointerdown', handlePointerDown);
    layer.addEventListener('click', handleClick, true);
    layer.addEventListener('keydown', handleKeyDown);
  });
}

function waitForGachaReveal(layer, delay) {
  const revealDelay = Math.max(0, Number(delay) || 0);
  return new Promise(resolve => {
    let resolved = false;
    let timeoutId = window.setTimeout(() => finish(false, 0), revealDelay);
    const cleanup = () => {
      if (timeoutId !== null) {
        clearTimeout(timeoutId);
        timeoutId = null;
      }
      layer.removeEventListener('pointerup', handlePointerUp);
      layer.removeEventListener('click', handleClick, true);
      layer.removeEventListener('keydown', handleKeyDown);
    };
    const finish = (skipped, guardTime) => {
      if (resolved) return;
      resolved = true;
      cleanup();
      resolve({ skipped, guardTime });
    };
    const resolveWithGuardTime = event => {
      const timeStamp = typeof event.timeStamp === 'number' ? event.timeStamp + 16 : 0;
      finish(true, timeStamp);
    };
    const handlePointerUp = event => {
      if (resolved) return;
      if ('isPrimary' in event && event.isPrimary === false) return;
      if (typeof event.button === 'number' && event.button > 0) return;
      event.preventDefault();
      event.stopPropagation();
      resolveWithGuardTime(event);
    };
    const handleClick = event => {
      if (resolved) return;
      event.preventDefault();
      event.stopPropagation();
      resolveWithGuardTime(event);
    };
    const handleKeyDown = event => {
      if (resolved) return;
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        finish(true, 0);
      } else if (event.key === 'Escape') {
        finish(true, 0);
      }
    };
    layer.addEventListener('pointerup', handlePointerUp);
    layer.addEventListener('click', handleClick, true);
    layer.addEventListener('keydown', handleKeyDown);
  });
}

async function playGachaAnimation(outcome) {
  if (!outcome) return;
  if (!elements.gachaAnimation) {
    displayGachaResult(outcome);
    return;
  }

  const layer = elements.gachaAnimation;

  layer.hidden = false;
  layer.setAttribute('aria-hidden', 'false');
  layer.classList.remove('show-result');
  layer.classList.add('is-active');
  if (elements.gachaResult) {
    elements.gachaResult.innerHTML = '';
    elements.gachaResult.style.removeProperty('--rarity-color');
  }

  startGachaConfettiAnimation(outcome);

  let guardTime = 0;
  try {
    const revealResult = await waitForGachaReveal(layer, GACHA_ANIMATION_REVEAL_DELAY);

    displayGachaResult(outcome);
    layer.classList.add('show-result');

    guardTime = revealResult && typeof revealResult.guardTime === 'number'
      ? revealResult.guardTime
      : 0;

    await waitForGachaAnimationDismiss(layer, { ignoreClicksUntil: guardTime });
  } finally {
    stopGachaConfettiAnimation();
  }

  layer.classList.remove('show-result');
  layer.classList.remove('is-active');
  layer.setAttribute('aria-hidden', 'true');
  layer.hidden = true;
  if (elements.gachaResult) {
    elements.gachaResult.innerHTML = '';
    elements.gachaResult.style.removeProperty('--rarity-color');
  }
}

function handleGachaRoll() {
  const outcome = performGachaRoll(gachaRollMode);
  if (!outcome) return;
  displayGachaResult(outcome);
  const message = getGachaToastMessage(outcome);
  if (message) {
    showToast(message);
  }
}

function toggleGachaRollMode() {
  gachaRollMode = gachaRollMode === 1 ? 10 : 1;
  updateGachaUI();
}

async function handleGachaSunClick() {
  if (gachaAnimationInProgress) return;
  const outcome = performGachaRoll(gachaRollMode);
  if (!outcome) {
    return;
  }

  gachaAnimationInProgress = true;
  updateGachaUI();

  try {
    await playGachaAnimation(outcome);
    const message = getGachaToastMessage(outcome);
    if (message) {
      showToast(message);
    }
  } finally {
    gachaAnimationInProgress = false;
    if (elements.gachaSunButton) {
      elements.gachaSunButton.disabled = false;
    }
    updateGachaUI();
  }
}

function gainGachaTickets(amount = 1, options = {}) {
  const gain = Math.max(1, Math.floor(Number(amount) || 0));
  const current = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  gameState.gachaTickets = current + gain;
  if (options && options.unlockTicketStar && gain > 0 && gameState.ticketStarUnlocked !== true) {
    gameState.ticketStarUnlocked = true;
    resetTicketStarState({ reschedule: true });
  }
  evaluatePageUnlocks();
  updateGachaUI();
  return gain;
}

function gainBonusParticulesTickets(amount = 1) {
  const gain = Math.max(1, Math.floor(Number(amount) || 0));
  const current = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
  gameState.bonusParticulesTickets = current + gain;
  const featureId = typeof METAL_FEATURE_ID === 'string' ? METAL_FEATURE_ID : 'arcade.metaux';
  const unlockedNow = typeof setFeatureUnlockFlag === 'function'
    ? setFeatureUnlockFlag(featureId)
    : false;
  if (unlockedNow && typeof invalidateFeatureUnlockCache === 'function') {
    invalidateFeatureUnlockCache();
  }
  updateArcadeTicketDisplay();
  if (unlockedNow && typeof refreshOptionsWelcomeContent === 'function') {
    refreshOptionsWelcomeContent();
  }
  if (typeof updateBrandPortalState === 'function') {
    updateBrandPortalState({ animate: unlockedNow });
  }
  if (typeof window !== 'undefined' && typeof window.updateMetauxCreditsUI === 'function') {
    window.updateMetauxCreditsUI();
  }
  return gain;
}

let ticketStarAverageIntervalMsOverride = TICKET_STAR_CONFIG.averageSpawnIntervalMs;
let ticketStarDelayReductionMs = 0;

function getTicketStarAverageIntervalMs() {
  const value = Number.isFinite(ticketStarAverageIntervalMsOverride) && ticketStarAverageIntervalMsOverride > 0
    ? ticketStarAverageIntervalMsOverride
    : TICKET_STAR_CONFIG.averageSpawnIntervalMs;
  return Math.max(TICKET_STAR_CONFIG.minimumSpawnIntervalMs, value);
}

function setTicketStarAverageIntervalSeconds(seconds) {
  const baseSeconds = DEFAULT_TICKET_STAR_INTERVAL_SECONDS;
  const numericSeconds = Number(seconds);
  const resolvedSeconds = Number.isFinite(numericSeconds) && numericSeconds > 0
    ? numericSeconds
    : baseSeconds;
  const normalizedMs = Math.max(TICKET_STAR_CONFIG.minimumSpawnIntervalMs, resolvedSeconds * 1000);
  if (Math.abs(ticketStarAverageIntervalMsOverride - normalizedMs) < 1) {
    gameState.ticketStarAverageIntervalSeconds = normalizedMs / 1000;
    return false;
  }
  ticketStarAverageIntervalMsOverride = normalizedMs;
  gameState.ticketStarAverageIntervalSeconds = normalizedMs / 1000;
  return true;
}

function computeTicketStarDelay() {
  const average = getTicketStarAverageIntervalMs();
  const jitter = 0.5 + Math.random();
  let delay = average * jitter;
  if (ticketStarDelayReductionMs > 0) {
    const minDelay = TICKET_STAR_CONFIG.minimumSpawnIntervalMs;
    const maxReduction = Math.max(0, delay - minDelay);
    const applied = Math.min(ticketStarDelayReductionMs, maxReduction);
    if (applied > 0) {
      delay -= applied;
      ticketStarDelayReductionMs = Math.max(0, ticketStarDelayReductionMs - applied);
    }
  }
  return Math.max(TICKET_STAR_CONFIG.minimumSpawnIntervalMs, delay);
}

function isTicketStarFeatureUnlocked() {
  return isPageUnlocked('gacha') && gameState.ticketStarUnlocked === true;
}

function getTicketStarAutoCollectDelayMs() {
  if (!gameState.ticketStarAutoCollectEnabled) {
    return null;
  }
  const config = gameState.ticketStarAutoCollect;
  if (!config) {
    return null;
  }
  const rawDelay = config.delaySeconds ?? config.delay ?? config.seconds ?? config.value;
  const delaySeconds = Number(rawDelay);
  if (!Number.isFinite(delaySeconds) || delaySeconds < 0) {
    return 0;
  }
  return delaySeconds * 1000;
}

function shouldAutoCollectTicketStar(now = performance.now()) {
  const delayMs = getTicketStarAutoCollectDelayMs();
  if (delayMs == null) {
    return false;
  }
  if (!ticketStarState.active) {
    return false;
  }
  if (!isClickerInteractionPageActive()) {
    return false;
  }
  if (typeof document !== 'undefined' && document.hidden) {
    return false;
  }
  if (gamePageVisibleSince == null || now - gamePageVisibleSince < delayMs) {
    return false;
  }
  const spawnTime = Number(ticketStarState.spawnTime) || 0;
  if (spawnTime <= 0 || now - spawnTime < delayMs) {
    return false;
  }
  return true;
}

function normalizeTicketStarSpriteId(raw) {
  if (typeof raw !== 'string') {
    return null;
  }
  const normalized = raw.trim().toLowerCase();
  if (normalized === 'animated' || normalized === 'gif' || normalized === 'anim') {
    return 'animated';
  }
  return 'static';
}

function resolveTicketStarSpriteId(preferredId) {
  const normalized = normalizeTicketStarSpriteId(preferredId)
    ?? normalizeTicketStarSpriteId(TICKET_STAR_CONFIG.defaultSpriteId)
    ?? 'static';
  const hasAnimatedSprite = typeof TICKET_STAR_CONFIG.spriteSources?.animated === 'string'
    && TICKET_STAR_CONFIG.spriteSources.animated.length > 0;
  if (normalized === 'animated' && hasAnimatedSprite) {
    return 'animated';
  }
  return 'static';
}

function getTicketStarSpriteSource(preferredId) {
  const resolvedId = resolveTicketStarSpriteId(preferredId);
  const sprites = TICKET_STAR_CONFIG.spriteSources || {};
  const source = sprites[resolvedId];
  if (typeof source === 'string' && source.length > 0) {
    return source;
  }
  if (resolvedId !== 'static' && typeof sprites.static === 'string' && sprites.static.length > 0) {
    return sprites.static;
  }
  return 'Assets/Image/Star.png';
}

function refreshTicketStarSprite(preferredId) {
  const resolvedId = resolveTicketStarSpriteId(preferredId ?? ticketStarState.spriteId);
  ticketStarState.spriteId = resolvedId;
  const star = ticketStarState.element;
  if (!star) {
    return;
  }
  const image = star.querySelector('img');
  if (!image) {
    return;
  }
  const nextSrc = getTicketStarSpriteSource(resolvedId);
  if (typeof nextSrc === 'string' && nextSrc.length > 0) {
    image.src = nextSrc;
  }
}

const ticketStarState = {
  element: null,
  active: false,
  position: { x: 0, y: 0 },
  velocity: { x: 0, y: 0 },
  width: 0,
  height: 0,
  nextSpawnTime: performance.now() + computeTicketStarDelay(),
  spawnTime: 0,
  expiryTime: 0,
  spriteId: resolveTicketStarSpriteId(TICKET_STAR_CONFIG.defaultSpriteId),
  isSpecial: false,
  rewardTickets: TICKET_STAR_CONFIG.rewardTickets
};

function resolveTicketLayer() {
  if (typeof getActiveTicketLayerElement === 'function') {
    const layer = getActiveTicketLayerElement();
    if (layer) {
      return layer;
    }
  }
  if (typeof document !== 'undefined') {
    const fallback = document.getElementById('ticketLayer');
    if (fallback) {
      return fallback;
    }
  }
  return null;
}

function isClickerInteractionPageActive() {
  if (typeof document !== 'undefined') {
    const activePage = document.body?.dataset?.activePage;
    if (activePage === 'wave') {
      return true;
    }
  }
  return typeof isGamePageActive === 'function' ? isGamePageActive() : false;
}

function resetTicketStarState(options = {}) {
  if (ticketStarState.element && ticketStarState.element.parentNode) {
    ticketStarState.element.remove();
  }
  ticketStarState.element = null;
  ticketStarState.active = false;
  ticketStarState.position.x = 0;
  ticketStarState.position.y = 0;
  ticketStarState.velocity.x = 0;
  ticketStarState.velocity.y = 0;
  ticketStarState.width = 0;
  ticketStarState.height = 0;
  ticketStarState.spawnTime = 0;
  ticketStarState.expiryTime = 0;
  ticketStarState.isSpecial = false;
  ticketStarState.rewardTickets = TICKET_STAR_CONFIG.rewardTickets;
  const now = performance.now();
  if (!isTicketStarFeatureUnlocked()) {
    ticketStarState.nextSpawnTime = Number.POSITIVE_INFINITY;
    ticketStarDelayReductionMs = 0;
    return;
  }
  if (options.reschedule) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
  } else if (!Number.isFinite(ticketStarState.nextSpawnTime) || ticketStarState.nextSpawnTime <= now) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
  }
}

function collectTicketStar(event) {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }
  if (!ticketStarState.active) {
    return;
  }
  if (!isTicketStarFeatureUnlocked()) {
    return;
  }
  const rewardTickets = Math.max(
    1,
    Math.floor(Number(ticketStarState.rewardTickets) || TICKET_STAR_CONFIG.rewardTickets)
  );
  const gained = gainGachaTickets(rewardTickets);
  const isSpecialStar = ticketStarState.isSpecial === true;
  if (isSpecialStar) {
    const key = 'scripts.gacha.ticketStar.special';
    const fallback = `Étoile spéciale ! +${gained} tickets de tirage`;
    const message = t(key, { count: gained, tickets: gained });
    showToast(message && message !== key ? message : fallback);
  } else {
    showToast(gained === 1
      ? t('scripts.gacha.ticketStar.single')
      : t('scripts.gacha.ticketStar.multiple', { count: gained }));
  }
  if (ticketStarState.element && ticketStarState.element.parentNode) {
    ticketStarState.element.remove();
  }
  ticketStarState.element = null;
  ticketStarState.active = false;
  ticketStarState.spawnTime = 0;
  ticketStarState.width = 0;
  ticketStarState.height = 0;
  ticketStarState.velocity.x = 0;
  ticketStarState.velocity.y = 0;
  ticketStarState.position.x = 0;
  ticketStarState.position.y = 0;
  ticketStarState.expiryTime = 0;
  ticketStarState.spriteId = resolveTicketStarSpriteId(ticketStarState.spriteId);
  ticketStarState.isSpecial = false;
  ticketStarState.rewardTickets = TICKET_STAR_CONFIG.rewardTickets;
  ticketStarState.nextSpawnTime = performance.now() + computeTicketStarDelay();
  ticketStarDelayReductionMs = 0;
  saveGame();
}

function spawnTicketStar(now = performance.now()) {
  if (!isTicketStarFeatureUnlocked()) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
    return;
  }
  const layer = resolveTicketLayer();
  if (!layer) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
    return;
  }
  const layerWidth = layer.clientWidth;
  const layerHeight = layer.clientHeight;
  if (layerWidth <= 0 || layerHeight <= 0) {
    ticketStarState.nextSpawnTime = now + 2000;
    return;
  }

  const specialChance = getTicketStarSpecialChance();
  const isSpecialStar = specialChance > 0 && Math.random() < specialChance;
  const rewardTickets = isSpecialStar
    ? getTicketStarSpecialRewardTickets()
    : TICKET_STAR_CONFIG.rewardTickets;

  if (ticketStarState.element && ticketStarState.element.parentNode) {
    ticketStarState.element.remove();
  }

  const star = document.createElement('button');
  star.type = 'button';
  star.className = 'ticket-star';
  star.dataset.ticketStarType = isSpecialStar ? 'special' : 'standard';
  star.style.setProperty('--ticket-star-size', `${TICKET_STAR_CONFIG.size}px`);

  const ticketStarLabel = translateWithFallback(
    isSpecialStar
      ? 'scripts.gacha.ticketStar.specialCollectAria'
      : 'scripts.gacha.ticketStar.collectAria',
    isSpecialStar
      ? `Collect the special star (+${rewardTickets} draw tickets)`
      : 'Collect a draw ticket'
  );
  star.setAttribute('aria-label', ticketStarLabel);

  const ticketStarImageAlt = translateWithFallback(
    isSpecialStar
      ? 'scripts.gacha.ticketStar.specialImageAlt'
      : 'scripts.gacha.ticketStar.imageAlt',
    isSpecialStar ? 'Special bonus star' : 'Bonus star'
  );

  const preferredSpriteId = typeof getTicketStarSpritePreference === 'function'
    ? getTicketStarSpritePreference()
    : ticketStarState.spriteId;
  const spriteId = resolveTicketStarSpriteId(preferredSpriteId);
  const spriteSource = getTicketStarSpriteSource(spriteId);

  const starImage = document.createElement('img');
  starImage.src = spriteSource;
  starImage.alt = ticketStarImageAlt;
  starImage.draggable = false;
  star.appendChild(starImage);
  star.addEventListener('click', collectTicketStar);
  star.addEventListener('dragstart', event => event.preventDefault());

  if (isSpecialStar) {
    star.classList.add('ticket-star--special');
  }

  layer.appendChild(star);

  const starWidth = star.offsetWidth || TICKET_STAR_CONFIG.size;
  const starHeight = star.offsetHeight || TICKET_STAR_CONFIG.size;
  const padding = TICKET_STAR_CONFIG.edgePadding;
  const interiorMaxX = Math.max(0, layerWidth - starWidth);
  const interiorMaxY = Math.max(0, layerHeight - starHeight);
  const minX = -padding;
  const maxX = interiorMaxX + padding;
  const minY = -padding;
  const maxY = interiorMaxY + padding;

  let layerRect = null;
  if (typeof layer.getBoundingClientRect === 'function') {
    layerRect = layer.getBoundingClientRect();
  }
  const layerOffsetLeft = layerRect ? layerRect.left : 0;
  const layerOffsetTop = layerRect ? layerRect.top : 0;

  const rootElements = typeof elements === 'object' && elements ? elements : null;
  const atomCore = rootElements?.atomButtonCore;
  const atomButton = rootElements?.atomButton;
  const atomRect = atomCore?.getBoundingClientRect?.()
    || atomButton?.getBoundingClientRect?.()
    || null;

  let startX;
  let startY;
  let velocityX = 0;
  let velocityY = 0;

  if (isTicketStarStaticMode()) {
    const safePaddingX = Math.min(padding, interiorMaxX / 2);
    const safePaddingY = Math.min(padding, interiorMaxY / 2);
    const minStaticX = safePaddingX;
    const maxStaticX = Math.max(minStaticX, interiorMaxX - safePaddingX);
    const minStaticY = safePaddingY;
    const maxStaticY = Math.max(minStaticY, interiorMaxY - safePaddingY);
    const rangeX = Math.max(0, maxStaticX - minStaticX);
    const rangeY = Math.max(0, maxStaticY - minStaticY);
    startX = minStaticX + (rangeX > 0 ? Math.random() * rangeX : 0);
    startY = minStaticY + (rangeY > 0 ? Math.random() * rangeY : 0);
  } else {
    const fallbackCenterX = layerRect ? layerRect.width / 2 : layerWidth / 2;
    const fallbackCenterY = layerRect ? layerRect.height / 2 : layerHeight / 2;

    const atomCenterX = atomRect
      ? atomRect.left + atomRect.width / 2 - layerOffsetLeft
      : fallbackCenterX;
    const atomCenterY = atomRect
      ? atomRect.top + atomRect.height / 2 - layerOffsetTop
      : fallbackCenterY;

    startX = atomCenterX - starWidth / 2;
    startY = atomCenterY - starHeight / 2;

    startX = Math.min(Math.max(startX, minX), maxX);
    startY = Math.min(Math.max(startY, minY), maxY);

    const speedRange = TICKET_STAR_CONFIG.horizontalSpeedRange;
    const minHorizontal = Math.max(0, speedRange?.min ?? 0);
    const maxHorizontal = Math.max(minHorizontal, speedRange?.max ?? minHorizontal);
    let horizontalSpeed = minHorizontal + Math.random() * Math.max(0, maxHorizontal - minHorizontal);
    const variance = Math.max(0, TICKET_STAR_CONFIG.speedVariance);
    if (variance > 0 && Number.isFinite(horizontalSpeed) && horizontalSpeed > 0) {
      const minMultiplier = Math.max(0.1, 1 - variance);
      const maxMultiplier = 1 + variance;
      const factor = minMultiplier + Math.random() * (maxMultiplier - minMultiplier);
      horizontalSpeed *= factor;
    }
    if (!Number.isFinite(horizontalSpeed) || horizontalSpeed <= 0) {
      horizontalSpeed = TICKET_STAR_CONFIG.minHorizontalSpeed;
    }
    const direction = Math.random() < 0.5 ? -1 : 1;
    velocityX = horizontalSpeed * direction;
    const minSpeed = TICKET_STAR_CONFIG.minHorizontalSpeed;
    if (Math.abs(velocityX) < minSpeed) {
      velocityX = minSpeed * (direction >= 0 ? 1 : -1);
    }

    const baseVertical = TICKET_STAR_CONFIG.launchVerticalSpeed;
    velocityY = -(baseVertical * (0.9 + Math.random() * 0.5));
    if (!Number.isFinite(velocityY)) {
      velocityY = -baseVertical;
    }
  }

  ticketStarState.element = star;
  ticketStarState.active = true;
  ticketStarState.position.x = startX;
  ticketStarState.position.y = startY;
  ticketStarState.width = starWidth;
  ticketStarState.height = starHeight;
  ticketStarState.velocity.x = velocityX;
  ticketStarState.velocity.y = velocityY;
  ticketStarState.nextSpawnTime = Number.POSITIVE_INFINITY;
  ticketStarState.spawnTime = now;
  ticketStarState.expiryTime = 0;
  ticketStarState.spriteId = spriteId;
  ticketStarState.isSpecial = isSpecialStar;
  ticketStarState.rewardTickets = rewardTickets;

  star.style.transform = `translate(${startX}px, ${startY}px)`;
}

function registerTicketStarClickReduction(clickCount = 1) {
  const perClick = TICKET_STAR_CONFIG.clickReductionPerClickMs;
  if (!Number.isFinite(perClick) || perClick <= 0) {
    return;
  }
  if (!isTicketStarFeatureUnlocked()) {
    return;
  }
  const normalizedClicks = Number.isFinite(Number(clickCount)) ? Number(clickCount) : 0;
  if (normalizedClicks <= 0) {
    return;
  }
  const added = perClick * normalizedClicks;
  if (!Number.isFinite(added) || added <= 0) {
    return;
  }
  ticketStarDelayReductionMs = Math.max(0, ticketStarDelayReductionMs + added);
  if (
    !ticketStarState.active
    && Number.isFinite(ticketStarState.nextSpawnTime)
    && ticketStarState.nextSpawnTime !== Number.POSITIVE_INFINITY
  ) {
    const nowTime = performance.now();
    const minTime = nowTime + TICKET_STAR_CONFIG.minimumSpawnIntervalMs;
    const targetTime = Math.max(minTime, ticketStarState.nextSpawnTime - added);
    const applied = ticketStarState.nextSpawnTime - targetTime;
    if (applied > 0) {
      ticketStarState.nextSpawnTime = targetTime;
      ticketStarDelayReductionMs = Math.max(0, ticketStarDelayReductionMs - applied);
    }
  }
}

if (typeof globalThis !== 'undefined') {
  globalThis.registerTicketStarClickReduction = registerTicketStarClickReduction;
  globalThis.refreshTicketStarSprite = refreshTicketStarSprite;
}

function updateTicketStar(deltaSeconds, now = performance.now()) {
  const layer = resolveTicketLayer();
  if (!layer) {
    return;
  }
  if (!isTicketStarFeatureUnlocked()) {
    if (ticketStarState.active) {
      if (ticketStarState.element && ticketStarState.element.parentNode) {
        ticketStarState.element.remove();
      }
      ticketStarState.element = null;
      ticketStarState.active = false;
      ticketStarState.spawnTime = 0;
      ticketStarState.expiryTime = 0;
      ticketStarState.velocity.x = 0;
      ticketStarState.velocity.y = 0;
      ticketStarState.position.x = 0;
      ticketStarState.position.y = 0;
      ticketStarState.width = 0;
      ticketStarState.height = 0;
      ticketStarState.isSpecial = false;
      ticketStarState.rewardTickets = TICKET_STAR_CONFIG.rewardTickets;
    }
    ticketStarState.nextSpawnTime = Number.POSITIVE_INFINITY;
    ticketStarDelayReductionMs = 0;
    return;
  }
  if (!ticketStarState.active && !Number.isFinite(ticketStarState.nextSpawnTime)) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
  }
  if (!ticketStarState.active) {
    if (now >= ticketStarState.nextSpawnTime) {
      spawnTicketStar(now);
    }
    return;
  }
  const star = ticketStarState.element;
  if (!star) {
    ticketStarState.active = false;
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
    ticketStarState.spawnTime = 0;
    ticketStarState.expiryTime = 0;
    ticketStarState.velocity.x = 0;
    ticketStarState.velocity.y = 0;
    ticketStarState.position.x = 0;
    ticketStarState.position.y = 0;
    ticketStarState.width = 0;
    ticketStarState.height = 0;
    ticketStarState.spriteId = resolveTicketStarSpriteId(ticketStarState.spriteId);
    ticketStarState.isSpecial = false;
    ticketStarState.rewardTickets = TICKET_STAR_CONFIG.rewardTickets;
    ticketStarDelayReductionMs = 0;
    return;
  }
  const width = layer.clientWidth;
  const height = layer.clientHeight;
  if (width <= 0 || height <= 0) {
    return;
  }
  if (shouldAutoCollectTicketStar(now)) {
    collectTicketStar();
    return;
  }
  const starWidth = star.offsetWidth || ticketStarState.width || TICKET_STAR_CONFIG.size;
  const starHeight = star.offsetHeight || ticketStarState.height || TICKET_STAR_CONFIG.size;
  const interiorMaxX = Math.max(0, width - starWidth);
  const interiorMaxY = Math.max(0, height - starHeight);
  const padding = TICKET_STAR_CONFIG.edgePadding;
  const minX = -padding;
  const maxX = interiorMaxX + padding;
  const minY = -padding;
  const maxY = interiorMaxY + padding;
  const delta = Math.min(Math.max(deltaSeconds, 0), 0.05);
  const gravity = TICKET_STAR_CONFIG.gravity;
  if (isTicketStarStaticMode()) {
    const clampedX = Math.min(Math.max(ticketStarState.position.x, 0), interiorMaxX);
    const clampedY = Math.min(Math.max(ticketStarState.position.y, 0), interiorMaxY);
    ticketStarState.position.x = clampedX;
    ticketStarState.position.y = clampedY;
    ticketStarState.width = starWidth;
    ticketStarState.height = starHeight;
    ticketStarState.velocity.x = 0;
    ticketStarState.velocity.y = 0;
    star.style.transform = `translate(${clampedX}px, ${clampedY}px)`;
    return;
  }
  ticketStarState.velocity.y += gravity * delta;
  let nextX = ticketStarState.position.x + ticketStarState.velocity.x * delta;
  let nextY = ticketStarState.position.y + ticketStarState.velocity.y * delta;

  const wallRestitution = TICKET_STAR_CONFIG.wallRestitution;
  const bounceRestitution = TICKET_STAR_CONFIG.bounceRestitution;
  const floorFriction = TICKET_STAR_CONFIG.floorFriction;

  if (nextX <= minX) {
    nextX = minX;
    ticketStarState.velocity.x = Math.abs(ticketStarState.velocity.x) * wallRestitution;
  } else if (nextX >= maxX) {
    nextX = maxX;
    ticketStarState.velocity.x = -Math.abs(ticketStarState.velocity.x) * wallRestitution;
  }

  if (nextY <= minY) {
    nextY = minY;
    ticketStarState.velocity.y = Math.abs(ticketStarState.velocity.y) * wallRestitution;
  } else if (nextY >= maxY) {
    nextY = maxY;
    if (Math.abs(ticketStarState.velocity.y) > 60) {
      ticketStarState.velocity.y = -Math.abs(ticketStarState.velocity.y) * bounceRestitution;
    } else {
      ticketStarState.velocity.y = 0;
    }
    ticketStarState.velocity.x *= floorFriction;
  }

  ticketStarState.position.x = nextX;
  ticketStarState.position.y = nextY;
  ticketStarState.width = starWidth;
  ticketStarState.height = starHeight;
  star.style.transform = `translate(${nextX}px, ${nextY}px)`;
}

function getI18nApi() {
  return globalThis.i18n;
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
    return api.formatNumber(value, options) || '';
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '';
  }
  return numeric.toLocaleString(getCurrentLocale(), options);
}

function formatIntegerLocalized(value) {
  return formatNumberLocalized(value, { maximumFractionDigits: 0, minimumFractionDigits: 0 });
}

function compareTextLocalized(a, b, options) {
  const api = getI18nApi();
  if (api && typeof api.compareText === 'function') {
    return api.compareText(a, b, options);
  }
  const locale = getCurrentLocale();
  return String(a ?? '').localeCompare(String(b ?? ''), locale, options);
}

if (typeof window !== 'undefined') {
  window.refreshGachaRarityLocalization = refreshGachaRarityLocalization;
  window.addEventListener('i18n:languagechange', () => {
    refreshGachaRarityLocalization({ force: true });
    if (typeof refreshFusionLocalization === 'function') {
      refreshFusionLocalization();
    }
    if (elements.gachaRarityList) {
      renderGachaRarityList();
    }
    renderFusionList();
    updateGachaUI();
  });
}

