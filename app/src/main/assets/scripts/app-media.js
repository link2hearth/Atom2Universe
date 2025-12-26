function getImageFeedSettings() {
  return ACTIVE_IMAGE_FEED_SETTINGS || DEFAULT_IMAGE_FEED_SETTINGS;
}

function getImageBackgroundRotationMs() {
  const settings = getImageFeedSettings();
  const raw = Number(settings?.favoriteBackgroundRotationMs);
  if (Number.isFinite(raw) && raw > 0) {
    return raw;
  }
  return DEFAULT_IMAGE_FEED_SETTINGS.favoriteBackgroundRotationMs;
}

function getBackgroundRotationDuration() {
  const duration = Number(backgroundRotationMs);
  if (Number.isFinite(duration) && duration > 0) {
    return duration;
  }
  return getImageBackgroundRotationMs();
}

function getImageMaxBytes() {
  const settings = getImageFeedSettings();
  const raw = Number(settings?.maxImageBytes);
  if (Number.isFinite(raw) && raw > 0) {
    return raw;
  }
  return DEFAULT_IMAGE_FEED_SETTINGS.maxImageBytes;
}

async function isImageWithinSizeLimit(url, maxBytes = getImageMaxBytes()) {
  if (!url || !Number.isFinite(maxBytes) || maxBytes <= 0) {
    return true;
  }
  const cacheKey = `${url}::${maxBytes}`;
  if (imageSizeAllowanceCache.has(cacheKey)) {
    return imageSizeAllowanceCache.get(cacheKey);
  }
  if (typeof fetch !== 'function') {
    return true;
  }
  try {
    const response = await fetch(url, { method: 'HEAD' });
    if (!response.ok) {
      return true;
    }
    const contentLength = Number(response.headers.get('content-length'));
    if (Number.isFinite(contentLength) && contentLength > maxBytes) {
      imageSizeAllowanceCache.set(cacheKey, false);
      return false;
    }
  } catch (error) {
    console.warn('Unable to verify image size', error);
  }
  imageSizeAllowanceCache.set(cacheKey, true);
  return true;
}

async function filterImagesBySizeLimit(items, maxBytes = getImageMaxBytes(), maxCount = Infinity) {
  if (!Array.isArray(items) || !items.length) {
    return [];
  }
  const allowed = [];
  for (const item of items) {
    if (allowed.length >= maxCount) {
      break;
    }
    const isAllowed = await isImageWithinSizeLimit(item?.imageUrl, maxBytes);
    if (isAllowed) {
      allowed.push(item);
    }
  }
  return allowed;
}

function normalizeImageSource(source, index = 0) {
  if (!source || typeof source !== 'object') {
    return null;
  }
  const id = typeof source.id === 'string' && source.id.trim()
    ? source.id.trim()
    : `image-source-${index}`;
  const feedUrl = typeof source.feedUrl === 'string' ? source.feedUrl.trim() : '';
  if (!feedUrl) {
    return null;
  }
  const titleKey = typeof source.titleKey === 'string' ? source.titleKey : '';
  const title = typeof source.title === 'string' ? source.title : '';
  return { id, feedUrl, titleKey, title };
}

function getAvailableImageSources() {
  const settings = getImageFeedSettings();
  const rawSources = Array.isArray(settings?.sources) ? settings.sources : [];
  return rawSources
    .map((entry, index) => normalizeImageSource(entry, index))
    .filter(Boolean);
}

function getImageSourceLabel(source) {
  if (!source) {
    return 'Source';
  }
  const fallback = source.title || source.id || 'Source';
  if (source.titleKey) {
    return translateOrDefault(source.titleKey, fallback);
  }
  return fallback;
}

function getImageSourceLabelById(sourceId) {
  const sources = getAvailableImageSources();
  const match = sources.find(entry => entry.id === sourceId);
  return getImageSourceLabel(match);
}

function getEnabledImageSources(availableSources = getAvailableImageSources()) {
  if (!(imageFeedEnabledSources instanceof Set)) {
    imageFeedEnabledSources = readStoredImageSources();
  }
  if (!(imageFeedEnabledSources instanceof Set)) {
    imageFeedEnabledSources = new Set(availableSources.map(source => source.id));
  }
  const validIds = new Set(availableSources.map(source => source.id));
  const normalized = Array.from(imageFeedEnabledSources).filter(id => validIds.has(id));
  if (normalized.length !== imageFeedEnabledSources.size) {
    imageFeedEnabledSources = new Set(normalized);
    writeStoredImageSources(imageFeedEnabledSources);
  }
  return availableSources.filter(source => imageFeedEnabledSources.has(source.id));
}

function readStoredDismissedImages() {
  try {
    const raw = globalThis.localStorage?.getItem(IMAGE_FEED_DISMISSED_STORAGE_KEY);
    if (!raw) {
      return new Set();
    }
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return new Set(parsed.filter(entry => typeof entry === 'string' && entry));
    }
  } catch (error) {
    console.warn('Unable to read dismissed images', error);
  }
  return new Set();
}

function writeStoredDismissedImages(ids) {
  try {
    const values = Array.from(ids || []).filter(entry => typeof entry === 'string' && entry);
    if (!values.length) {
      globalThis.localStorage?.removeItem(IMAGE_FEED_DISMISSED_STORAGE_KEY);
      return;
    }
    globalThis.localStorage?.setItem(IMAGE_FEED_DISMISSED_STORAGE_KEY, JSON.stringify(values));
  } catch (error) {
    console.warn('Unable to persist dismissed images', error);
  }
}

function readStoredImageBackgroundEnabled() {
  try {
    const raw = globalThis.localStorage?.getItem(IMAGE_FEED_BACKGROUND_ENABLED_STORAGE_KEY);
    if (raw === 'true') {
      return true;
    }
    if (raw === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read background preference', error);
  }
  const settings = getImageFeedSettings();
  return Boolean(settings?.favoriteBackgroundEnabledByDefault);
}

function writeStoredImageBackgroundEnabled(enabled) {
  try {
    globalThis.localStorage?.setItem(IMAGE_FEED_BACKGROUND_ENABLED_STORAGE_KEY, enabled ? 'true' : 'false');
  } catch (error) {
    console.warn('Unable to persist background preference', error);
  }
}

function readStoredBackgroundDuration() {
  try {
    const raw = globalThis.localStorage?.getItem(BACKGROUND_DURATION_STORAGE_KEY);
    const parsed = Number(raw);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
  } catch (error) {
    console.warn('Unable to read background duration', error);
  }
  return getImageBackgroundRotationMs();
}

function writeStoredBackgroundDuration(durationMs) {
  try {
    const normalized = Number(durationMs);
    if (!Number.isFinite(normalized) || normalized <= 0) {
      globalThis.localStorage?.removeItem(BACKGROUND_DURATION_STORAGE_KEY);
      return;
    }
    globalThis.localStorage?.setItem(BACKGROUND_DURATION_STORAGE_KEY, String(normalized));
  } catch (error) {
    console.warn('Unable to persist background duration', error);
  }
}

function readStoredLocalBackgroundBank() {
  try {
    const raw = globalThis.localStorage?.getItem(LOCAL_BACKGROUND_BANK_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    const uris = Array.isArray(parsed?.uris)
      ? parsed.uris.filter(uri => typeof uri === 'string' && uri)
      : [];
    const label = typeof parsed?.label === 'string' ? parsed.label : '';
    if (!uris.length) {
      return null;
    }
    return { uris, label };
  } catch (error) {
    console.warn('Unable to read local background bank', error);
  }
  return null;
}

function writeStoredLocalBackgroundBank(bank) {
  try {
    const uris = Array.isArray(bank?.uris)
      ? bank.uris.filter(uri => typeof uri === 'string' && uri)
      : [];
    const label = typeof bank?.label === 'string' ? bank.label : '';
    if (!uris.length) {
      globalThis.localStorage?.removeItem(LOCAL_BACKGROUND_BANK_STORAGE_KEY);
      return;
    }
    const payload = { uris, label };
    globalThis.localStorage?.setItem(LOCAL_BACKGROUND_BANK_STORAGE_KEY, JSON.stringify(payload));
  } catch (error) {
    console.warn('Unable to persist local background bank', error);
  }
}

function readStoredBackgroundRotationState() {
  try {
    const raw = globalThis.localStorage?.getItem(BACKGROUND_ROTATION_STATE_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }
    const poolSize = Math.max(0, Math.floor(Number(parsed.poolSize) || 0));
    const queue = Array.isArray(parsed.queue) ? parsed.queue : [];
    const excluded = Array.isArray(parsed.excluded) ? parsed.excluded : [];
    const lastIndex = Number.isFinite(Number(parsed.lastIndex)) ? Math.floor(Number(parsed.lastIndex)) : null;
    return { poolSize, queue, excluded, lastIndex };
  } catch (error) {
    console.warn('Unable to read background rotation state', error);
  }
  return null;
}

function writeStoredBackgroundRotationState(state) {
  try {
    const poolSize = Math.max(0, Math.floor(Number(state?.poolSize) || 0));
    if (!poolSize) {
      globalThis.localStorage?.removeItem(BACKGROUND_ROTATION_STATE_STORAGE_KEY);
      return;
    }
    const queue = Array.isArray(state?.queue)
      ? state.queue.map(entry => Math.floor(Number(entry))).filter(entry => Number.isFinite(entry))
      : [];
    const excluded = Array.isArray(state?.excluded)
      ? state.excluded.map(entry => Math.floor(Number(entry))).filter(entry => Number.isFinite(entry))
      : [];
    const lastIndex = Number.isFinite(Number(state?.lastIndex))
      ? Math.floor(Number(state.lastIndex))
      : null;
    const payload = { poolSize, queue, excluded, lastIndex };
    globalThis.localStorage?.setItem(BACKGROUND_ROTATION_STATE_STORAGE_KEY, JSON.stringify(payload));
  } catch (error) {
    console.warn('Unable to persist background rotation state', error);
  }
}

function clearStoredBackgroundRotationState() {
  try {
    globalThis.localStorage?.removeItem(BACKGROUND_ROTATION_STATE_STORAGE_KEY);
  } catch (error) {
    console.warn('Unable to clear background rotation state', error);
  }
}

function readStoredImageSources() {
  try {
    const raw = globalThis.localStorage?.getItem(IMAGE_FEED_SOURCES_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return new Set(parsed.filter(entry => typeof entry === 'string' && entry));
    }
  } catch (error) {
    console.warn('Unable to read image sources selection', error);
  }
  return null;
}

function writeStoredImageSources(sourceSet) {
  try {
    const ids = Array.from(sourceSet || []).filter(entry => typeof entry === 'string' && entry);
    if (!ids.length) {
      globalThis.localStorage?.removeItem(IMAGE_FEED_SOURCES_STORAGE_KEY);
      return;
    }
    globalThis.localStorage?.setItem(IMAGE_FEED_SOURCES_STORAGE_KEY, JSON.stringify(ids));
  } catch (error) {
    console.warn('Unable to persist image source selection', error);
  }
}

function readStoredImageCurrentIndex() {
  try {
    const raw = globalThis.localStorage?.getItem(IMAGE_FEED_LAST_INDEX_STORAGE_KEY);
    if (raw == null) {
      return 0;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : 0;
  } catch (error) {
    console.warn('Unable to read image gallery index', error);
  }
  return 0;
}

function writeStoredImageCurrentIndex(index) {
  try {
    const safeIndex = Math.max(0, Math.floor(Number(index) || 0));
    globalThis.localStorage?.setItem(IMAGE_FEED_LAST_INDEX_STORAGE_KEY, String(safeIndex));
  } catch (error) {
    console.warn('Unable to persist image gallery index', error);
  }
}

function getCachedImageAsset(itemId) {
  if (!itemId) {
    return null;
  }
  return imageAssetCache.get(itemId) || null;
}

function getImageItemById(itemId) {
  if (!itemId) {
    return null;
  }
  return (Array.isArray(imageFeedItems) ? imageFeedItems : []).find(item => item.id === itemId) || null;
}

function createDataUrlFromBlob(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('Unable to read image data'));
    reader.readAsDataURL(blob);
  });
}

function buildResizedDataUrl(dataUrl, maxDimension, quality = 0.85) {
  return new Promise(resolve => {
    const image = new Image();
    image.onload = () => {
      const ratio = Math.max(1, Math.max(image.width, image.height) / Math.max(1, maxDimension));
      const targetWidth = Math.max(1, Math.round(image.width / ratio));
      const targetHeight = Math.max(1, Math.round(image.height / ratio));
      const canvas = document.createElement('canvas');
      canvas.width = targetWidth;
      canvas.height = targetHeight;
      const context = canvas.getContext('2d');
      if (context) {
        context.drawImage(image, 0, 0, targetWidth, targetHeight);
        const compressed = canvas.toDataURL('image/jpeg', quality);
        resolve(compressed || dataUrl);
        return;
      }
      resolve(dataUrl);
    };
    image.onerror = () => resolve(dataUrl);
    image.src = dataUrl;
  });
}

async function warmImageAsset(item) {
  if (!item || !item.imageUrl || imageAssetDownloads.has(item.id)) {
    return getCachedImageAsset(item?.id) || null;
  }
  const cached = getCachedImageAsset(item.id);
  if (cached?.fullDataUrl && cached.imageUrl === item.imageUrl) {
    return cached;
  }
  const downloadPromise = (async () => {
    try {
      if (typeof fetch !== 'function') {
        return cached || null;
      }
      const response = await fetch(item.imageUrl);
      if (!response.ok) {
        return cached || null;
      }
      const blob = await response.blob();
      const dataUrl = await createDataUrlFromBlob(blob);
      const resizedFull = await buildResizedDataUrl(dataUrl, IMAGE_FAVORITE_CACHE_MAX_DIMENSION, 0.88);
      const asset = Object.assign({}, cached, {
        imageUrl: item.imageUrl,
        fullDataUrl: resizedFull,
        thumbDataUrl: cached?.thumbDataUrl || '',
        title: item.title || cached?.title || '',
        link: item.link || cached?.link || item.imageUrl,
        sourceId: item.sourceId || cached?.sourceId || '',
        pubDate: item.pubDate || cached?.pubDate || 0,
        updatedAt: Date.now()
      });
      imageAssetCache.set(item.id, asset);
      return asset;
    } catch (error) {
      console.warn('Unable to cache image asset', error);
      return cached || null;
    } finally {
      imageAssetDownloads.delete(item.id);
    }
  })();
  imageAssetDownloads.set(item.id, downloadPromise);
  return downloadPromise;
}

async function generateThumbnailForItem(item) {
  if (!item) {
    return null;
  }
  const cached = getCachedImageAsset(item.id) || (await warmImageAsset(item));
  if (cached?.thumbDataUrl) {
    return cached;
  }
  if (!cached || !cached.fullDataUrl) {
    return cached;
  }
  const thumbDataUrl = await buildResizedDataUrl(cached.fullDataUrl, IMAGE_THUMBNAIL_MAX_DIMENSION, 0.75);
  const updated = Object.assign({}, cached, { thumbDataUrl, updatedAt: Date.now() });
  imageAssetCache.set(item.id, updated);
  return updated;
}

function queueThumbnailGeneration(items = []) {
  const list = Array.isArray(items) ? items : [items];
  list.forEach(item => {
    if (!item || !item.id) {
      return;
    }
    const cached = getCachedImageAsset(item.id);
    if (cached?.thumbDataUrl) {
      return;
    }
    if (imageThumbnailQueue.some(entry => entry.id === item.id)) {
      return;
    }
    imageThumbnailQueue.push(item);
  });
  processThumbnailQueue();
}

function processThumbnailQueue() {
  if (imageThumbnailWorkerActive) {
    return;
  }
  const next = imageThumbnailQueue.shift();
  if (!next) {
    return;
  }
  imageThumbnailWorkerActive = true;
  const runner = async () => {
    try {
      await generateThumbnailForItem(next);
    } catch (error) {
      console.warn('Unable to create thumbnail', error);
    } finally {
      imageThumbnailWorkerActive = false;
      if (imageThumbnailQueue.length) {
        setTimeout(processThumbnailQueue, IMAGE_THUMBNAIL_IDLE_DELAY_MS);
      }
    }
  };
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(runner, { timeout: 1500 });
  } else {
    setTimeout(runner, IMAGE_THUMBNAIL_IDLE_DELAY_MS);
  }
}

function getThumbnailSrc(item) {
  const cached = getCachedImageAsset(item?.id);
  if (cached?.thumbDataUrl) {
    return cached.thumbDataUrl;
  }
  if (item?.thumbnailUrl) {
    return item.thumbnailUrl;
  }
  if (cached?.fullDataUrl) {
    return cached.fullDataUrl;
  }
  return item?.imageUrl || '';
}

function getFullImageSrc(item) {
  const cached = getCachedImageAsset(item?.id);
  if (cached?.fullDataUrl) {
    return cached.fullDataUrl;
  }
  return item?.imageUrl || '';
}

function applyCachedAssetToItem(item) {
  if (!item || !item.id) {
    return item;
  }
  const cached = getCachedImageAsset(item.id);
  if (!cached) {
    return item;
  }
  return Object.assign({}, item, {
    imageUrl: cached.fullDataUrl || cached.imageUrl || item.imageUrl,
    thumbnailUrl: cached.thumbDataUrl || item.thumbnailUrl || '',
    title: item.title || cached.title || '',
    link: item.link || cached.link || item.link || cached.imageUrl || item.imageUrl,
    sourceId: item.sourceId || cached.sourceId || '',
    pubDate: item.pubDate || cached.pubDate || 0
  });
}

function buildImageRequestUrls(feedUrl) {
  const settings = getImageFeedSettings();
  const proxies = [];
  if (Array.isArray(settings?.proxyBaseUrls)) {
    proxies.push(...settings.proxyBaseUrls);
  }
  if (typeof settings?.proxyBaseUrl === 'string') {
    proxies.push(settings.proxyBaseUrl);
  }
  const sanitized = proxies
    .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
    .filter(Boolean);
  const urls = sanitized.map(proxy => {
    if (proxy.includes('{feed}')) {
      return proxy.replace('{feed}', encodeURIComponent(feedUrl));
    }
    const needsEncoding = proxy.includes('?');
    const target = needsEncoding ? encodeURIComponent(feedUrl) : feedUrl;
    return `${proxy}${target}`;
  });
  urls.push(feedUrl);
  return Array.from(new Set(urls));
}

function setImagesStatus(key, fallback, params = {}) {
  if (!elements.imagesStatus) {
    return;
  }
  elements.imagesStatus.setAttribute('data-i18n', key);
  elements.imagesStatus.textContent = translateOrDefault(key, fallback, params);
}

function hashImageKey(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index);
    hash |= 0;
  }
  return Math.abs(hash).toString(36);
}

function buildImageItemId(sourceId, link, title) {
  const base = `${sourceId || 'feed'}|${link || ''}|${title || ''}`;
  return hashImageKey(base || `${Date.now()}-${Math.random()}`);
}

function normalizeImageUrl(url) {
  if (typeof url !== 'string') {
    return '';
  }
  const trimmed = url.trim();
  if (!trimmed) {
    return '';
  }
  if (trimmed.startsWith('//')) {
    return `https:${trimmed}`;
  }
  return trimmed;
}

function extractImageUrlFromHtml(html) {
  if (typeof html !== 'string') {
    return '';
  }
  const match = html.match(/<img[^>]+src=["']([^"']+)["']/i);
  return normalizeImageUrl(match ? match[1] : '');
}

function extractImageUrlFromNode(node) {
  if (!node || typeof node.querySelector !== 'function') {
    return '';
  }
  const mediaContent = node.querySelector('media\\:content');
  const enclosure = node.querySelector('enclosure[url]');
  const linkEnclosure = node.querySelector('link[rel="enclosure"]');
  const descriptionHtml = node.querySelector('content\\:encoded')?.textContent
    || node.querySelector('description')?.textContent
    || '';
  const candidates = [
    mediaContent?.getAttribute('url'),
    enclosure?.getAttribute('url'),
    linkEnclosure?.getAttribute('href'),
    extractImageUrlFromHtml(descriptionHtml)
  ]
    .map(url => normalizeImageUrl(url))
    .filter(Boolean);
  return candidates.length ? candidates[0] : '';
}

function parseImageFeed(xmlText, sourceId) {
  if (typeof DOMParser !== 'function') {
    return [];
  }
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlText, 'application/xml');
  const entries = Array.from(doc.querySelectorAll('item, entry'));
  const items = entries.map(entry => {
    const title = entry.querySelector('title')?.textContent?.trim() || '';
    const linkElement = entry.querySelector('link');
    const link = normalizeImageUrl(linkElement?.getAttribute('href') || linkElement?.textContent || '');
    const pubDateText = entry.querySelector('pubDate, updated, published')?.textContent?.trim();
    const pubDate = pubDateText ? Date.parse(pubDateText) : 0;
    const imageUrl = extractImageUrlFromNode(entry);
    if (!imageUrl) {
      return null;
    }
    return {
      id: buildImageItemId(sourceId, link || imageUrl, title),
      title: title || getImageSourceLabelById(sourceId),
      link: link || imageUrl,
      imageUrl,
      sourceId,
      pubDate: Number.isFinite(pubDate) ? pubDate : 0
    };
  }).filter(Boolean);
  items.sort((first, second) => {
    const a = Number(first?.pubDate) || 0;
    const b = Number(second?.pubDate) || 0;
    return b - a;
  });
  return items;
}

function dismissImageItem(itemId) {
  if (!itemId) {
    return;
  }
  if (!(imageFeedDismissedIds instanceof Set)) {
    imageFeedDismissedIds = new Set();
  }
  imageFeedDismissedIds.add(itemId);
  writeStoredDismissedImages(imageFeedDismissedIds);
  imageFeedItems = (Array.isArray(imageFeedItems) ? imageFeedItems : []).filter(item => item.id !== itemId);
  imageFeedVisibleItems = imageFeedVisibleItems.filter(item => item.id !== itemId);
  if (imageFeedCurrentIndex >= imageFeedVisibleItems.length) {
    imageFeedCurrentIndex = 0;
    writeStoredImageCurrentIndex(imageFeedCurrentIndex);
  }
  refreshImagesDisplay();
}

function getBackgroundItems() {
  return Array.isArray(localBackgroundItems) ? localBackgroundItems : [];
}

function hasLocalBackgrounds() {
  return Array.isArray(localBackgroundItems) && localBackgroundItems.length > 0;
}

function extractFileNameFromUri(uri) {
  if (typeof uri !== 'string') {
    return '';
  }
  const parts = uri.split(/[\\/]/).filter(Boolean);
  const fileName = parts[parts.length - 1] || '';
  if (!fileName) {
    return '';
  }
  try {
    return decodeURIComponent(fileName);
  } catch (error) {
    return fileName;
  }
}

function buildDownloadedImageLabel(position, fileName) {
  const order = Math.max(1, Number(position) || 1);
  const normalizedName = typeof fileName === 'string' && fileName.trim() ? fileName.trim() : '';
  if (normalizedName) {
    return normalizedName;
  }
  return translateOrDefault(
    'index.sections.collection.downloads.itemLabel',
    `Downloaded image ${order}`,
    { index: order }
  );
}

function buildDownloadedImageAlt(position, labelText = '') {
  const order = Math.max(1, Number(position) || 1);
  const normalizedLabel = typeof labelText === 'string' && labelText.trim() ? labelText.trim() : '';
  const fallback = normalizedLabel || `Downloaded image ${order}`;
  return translateOrDefault(
    'index.sections.collection.downloads.itemAlt',
    fallback,
    { index: order, name: normalizedLabel || fallback }
  );
}

function setCollectionDownloadsEntries(entries = []) {
  collectionDownloadsEntries = Array.isArray(entries) ? entries : [];
  if (!collectionDownloadsEntries.length) {
    collectionDownloadsCurrentIndex = 0;
    return;
  }
  const cappedIndex = Math.min(collectionDownloadsCurrentIndex, collectionDownloadsEntries.length - 1);
  collectionDownloadsCurrentIndex = Math.max(0, cappedIndex);
}

function isCollectionDownloadsLightboxOpen() {
  return Boolean(
    elements.collectionDownloadsLightbox && !elements.collectionDownloadsLightbox.hasAttribute('hidden')
  );
}

function openCollectionDownloadFullscreen(index = 0) {
  if (!elements.collectionDownloadsLightbox || !elements.collectionDownloadsLightboxImage) {
    return;
  }
  if (!Array.isArray(collectionDownloadsEntries) || !collectionDownloadsEntries.length) {
    return;
  }

  const safeIndex = Math.min(Math.max(Number(index) || 0, 0), collectionDownloadsEntries.length - 1);
  const entry = collectionDownloadsEntries[safeIndex];
  if (!entry) {
    return;
  }

  collectionDownloadsCurrentIndex = safeIndex;
  const labelText = entry.labelText || buildDownloadedImageLabel(entry.position, entry.fileName);
  const altText = entry.altText || buildDownloadedImageAlt(entry.position, labelText);

  const resolvedUrl = resolveCollectionDownloadUrl(entry);
  if (!resolvedUrl) {
    return;
  }
  elements.collectionDownloadsLightboxImage.src = resolvedUrl;
  elements.collectionDownloadsLightboxImage.alt = altText;
  if (elements.collectionDownloadsLightboxCaption) {
    elements.collectionDownloadsLightboxCaption.textContent = labelText;
  }

  elements.collectionDownloadsLightbox.removeAttribute('hidden');
  refreshLightboxBodyState();
}

function closeCollectionDownloadsFullscreen() {
  if (!elements.collectionDownloadsLightbox || !elements.collectionDownloadsLightboxImage) {
    return;
  }

  elements.collectionDownloadsLightbox.setAttribute('hidden', 'hidden');
  elements.collectionDownloadsLightboxImage.src = '';
  elements.collectionDownloadsLightboxImage.alt = '';
  if (elements.collectionDownloadsLightboxCaption) {
    elements.collectionDownloadsLightboxCaption.textContent = '';
  }
  collectionDownloadsTouchStartX = null;
  refreshLightboxBodyState();
}

function showNextCollectionDownload() {
  if (!Array.isArray(collectionDownloadsEntries) || collectionDownloadsEntries.length === 0) {
    return;
  }
  const nextIndex = (collectionDownloadsCurrentIndex + 1) % collectionDownloadsEntries.length;
  openCollectionDownloadFullscreen(nextIndex);
}

function showPreviousCollectionDownload() {
  if (!Array.isArray(collectionDownloadsEntries) || collectionDownloadsEntries.length === 0) {
    return;
  }
  const previousIndex = (collectionDownloadsCurrentIndex - 1 + collectionDownloadsEntries.length)
    % collectionDownloadsEntries.length;
  openCollectionDownloadFullscreen(previousIndex);
}

function handleCollectionDownloadsTouchStart(event) {
  if (!event?.touches?.length) {
    return;
  }
  collectionDownloadsTouchStartX = event.touches[0].clientX;
}

function handleCollectionDownloadsTouchEnd(event) {
  if (collectionDownloadsTouchStartX === null || !event?.changedTouches?.length) {
    collectionDownloadsTouchStartX = null;
    return;
  }
  const deltaX = event.changedTouches[0].clientX - collectionDownloadsTouchStartX;
  collectionDownloadsTouchStartX = null;
  if (Math.abs(deltaX) < 30) {
    return;
  }
  if (deltaX < 0) {
    showNextCollectionDownload();
    return;
  }
  showPreviousCollectionDownload();
}

function resolveCollectionDownloadUrl(entry) {
  if (!entry) {
    return '';
  }
  const rawUrl = entry.rawUrl || '';
  if (!rawUrl) {
    return '';
  }
  return resolveBackgroundUrl(rawUrl);
}

function applyCollectionDownloadImage(imageElement, entry) {
  if (!imageElement || !entry) {
    return;
  }
  const resolvedUrl = resolveCollectionDownloadUrl(entry);
  if (resolvedUrl) {
    imageElement.src = resolvedUrl;
  }
}

function renderCollectionDownloadsGallery(items = getBackgroundItems()) {
  if (!elements.collectionDownloadsGallery || !elements.collectionDownloadsEmpty) {
    return;
  }

  const gallery = elements.collectionDownloadsGallery;
  gallery.innerHTML = '';

  const entries = Array.isArray(items) ? items : [];
  const normalizedEntries = entries
    .map((entry, index) => {
      const rawUrl = getFullImageSrc(entry);
      if (!rawUrl) {
        return null;
      }
      return {
        rawUrl,
        position: index + 1,
        fileName: extractFileNameFromUri(rawUrl || entry?.imageUrl)
      };
    })
    .filter(Boolean)
    .map(entry => {
      const labelText = buildDownloadedImageLabel(entry.position, entry.fileName);
      const altText = buildDownloadedImageAlt(entry.position, labelText);
      return { ...entry, labelText, altText };
    });

  setCollectionDownloadsEntries(normalizedEntries);

  const hasEntries = normalizedEntries.length > 0;
  gallery.toggleAttribute('hidden', !hasEntries);
  elements.collectionDownloadsEmpty.hidden = hasEntries;
  elements.collectionDownloadsEmpty.setAttribute('aria-hidden', hasEntries ? 'true' : 'false');

  if (!hasEntries) {
    closeCollectionDownloadsFullscreen();
    return;
  }

  const lightboxWasOpen = isCollectionDownloadsLightboxOpen();

  normalizedEntries.forEach((entry, index) => {
    const card = document.createElement('article');
    card.className = 'images-card collection-downloads-card';
    card.setAttribute('role', 'listitem');
    card.tabIndex = 0;

    const thumb = document.createElement('img');
    thumb.className = 'images-card__thumb';
    thumb.loading = 'lazy';
    thumb.decoding = 'async';
    thumb.src = entry.resolvedUrl;
    thumb.alt = entry.altText;

    card.appendChild(thumb);

    const handleOpen = () => openCollectionDownloadFullscreen(index);
    card.addEventListener('click', handleOpen);
    card.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        handleOpen();
      }
    });

    if (typeof IntersectionObserver === 'function') {
      const observer = new IntersectionObserver(entriesList => {
        entriesList.forEach(observed => {
          if (!observed.isIntersecting) {
            return;
          }
          applyCollectionDownloadImage(thumb, entry);
          observer.disconnect();
        });
      }, { root: gallery, rootMargin: '120px' });
      observer.observe(card);
    } else {
      applyCollectionDownloadImage(thumb, entry);
    }

    gallery.appendChild(card);
  });

  if (lightboxWasOpen) {
    openCollectionDownloadFullscreen(collectionDownloadsCurrentIndex);
  }
}

function pickNextBackgroundIndex(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (total <= 1) {
    return 0;
  }
  ensureBackgroundRotationQueue(total);
  if (!backgroundRotationQueue.length) {
    backgroundRotationQueue = buildBackgroundRotationQueue(total);
  }
  if (!backgroundRotationQueue.length) {
    return 0;
  }
  let nextIndex = backgroundRotationQueue.shift();
  if (nextIndex === backgroundIndex && backgroundRotationQueue.length) {
    backgroundRotationQueue.push(nextIndex);
    nextIndex = backgroundRotationQueue.shift();
  }
  return nextIndex;
}

function normalizeBackgroundRotationList(list, poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (!Array.isArray(list) || total <= 0) {
    return [];
  }
  const normalized = [];
  const seen = new Set();
  list.forEach(entry => {
    const value = Math.floor(Number(entry));
    if (!Number.isFinite(value) || value < 0 || value >= total) {
      return;
    }
    if (seen.has(value)) {
      return;
    }
    seen.add(value);
    normalized.push(value);
  });
  return normalized;
}

function restoreBackgroundRotationState(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (!total) {
    clearStoredBackgroundRotationState();
    return false;
  }
  const stored = readStoredBackgroundRotationState();
  if (!stored || stored.poolSize !== total) {
    return false;
  }
  const restoredQueue = normalizeBackgroundRotationList(stored.queue, total);
  const restoredExcluded = normalizeBackgroundRotationList(stored.excluded, total);
  backgroundRotationQueue = restoredQueue;
  backgroundRotationExclusions = new Set(restoredExcluded);
  backgroundRotationPoolSize = total;
  if (Number.isFinite(stored.lastIndex) && stored.lastIndex >= 0 && stored.lastIndex < total) {
    backgroundIndex = stored.lastIndex;
  }
  return true;
}

function persistBackgroundRotationState(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (!total) {
    clearStoredBackgroundRotationState();
    return;
  }
  const queue = normalizeBackgroundRotationList(backgroundRotationQueue, total);
  const excluded = normalizeBackgroundRotationList(Array.from(backgroundRotationExclusions || []), total);
  writeStoredBackgroundRotationState({
    poolSize: total,
    queue,
    excluded,
    lastIndex: backgroundIndex
  });
}

function buildBackgroundRotationQueue(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (!total) {
    return [];
  }
  const excluded = backgroundRotationExclusions instanceof Set ? backgroundRotationExclusions : new Set();
  const indices = [];
  for (let i = 0; i < total; i += 1) {
    if (!excluded.has(i)) {
      indices.push(i);
    }
  }
  for (let i = indices.length - 1; i > 0; i -= 1) {
    const swapIndex = Math.floor(Math.random() * (i + 1));
    [indices[i], indices[swapIndex]] = [indices[swapIndex], indices[i]];
  }
  return indices;
}

function resetBackgroundRotationSession(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  backgroundRotationExclusions = new Set();
  backgroundRotationQueue = buildBackgroundRotationQueue(total);
  backgroundRotationPoolSize = total;
  persistBackgroundRotationState(total);
  renderBackgroundRotationCounter(total);
}

function ensureBackgroundRotationQueue(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (total !== backgroundRotationPoolSize) {
    resetBackgroundRotationSession(total);
    return;
  }
  if (backgroundRotationQueue.length) {
    return;
  }
  if (backgroundRotationExclusions.size >= total) {
    backgroundRotationExclusions.clear();
  }
  backgroundRotationQueue = buildBackgroundRotationQueue(total);
}

function markBackgroundIndexSeen(index, poolLength) {
  const safeIndex = Math.max(0, Math.floor(Number(index) || 0));
  ensureBackgroundRotationQueue(poolLength);
  if (!backgroundRotationQueue.length) {
    return;
  }
  backgroundRotationQueue = backgroundRotationQueue.filter(item => item !== safeIndex);
  persistBackgroundRotationState(poolLength);
  renderBackgroundRotationCounter(poolLength);
}

function markBackgroundIndexExcluded(index, poolLength) {
  const safeIndex = Math.max(0, Math.floor(Number(index) || 0));
  if (!(backgroundRotationExclusions instanceof Set)) {
    backgroundRotationExclusions = new Set();
  }
  backgroundRotationExclusions.add(safeIndex);
  backgroundRotationQueue = backgroundRotationQueue.filter(item => item !== safeIndex);
  if (backgroundRotationExclusions.size >= Math.max(0, Number(poolLength) || 0)) {
    backgroundRotationExclusions.clear();
  }
  persistBackgroundRotationState(poolLength);
  renderBackgroundRotationCounter(poolLength);
}

function setBackgroundLibraryStatus(status) {
  backgroundLibraryStatus = status;
  renderBackgroundLibraryStatus();
}

function renderBackgroundLibraryStatus() {
  if (!elements.backgroundLibraryStatus) {
    return;
  }
  const localPool = Array.isArray(localBackgroundItems) ? localBackgroundItems : [];
  const hasLocal = localPool.length > 0;
  let key = 'index.sections.options.background.status.empty';
  let fallback = 'No image bank selected yet.';
  let params = {};
  if (backgroundLibraryStatus === 'loading') {
    key = 'index.sections.options.background.status.loading';
    fallback = 'Scanning your folder…';
  } else if (hasLocal) {
    key = 'index.sections.options.background.status.ready';
    fallback = '{count} images ready from {name}.';
    params = {
      count: localPool.length,
      name: backgroundLibraryLabel || translateOrDefault(
        'index.sections.options.background.fallbackName',
        'this folder'
      )
    };
  } else if (backgroundLibraryStatus === 'error') {
    key = 'index.sections.options.background.status.error';
    fallback = 'Unable to read this folder.';
  }
  elements.backgroundLibraryStatus.textContent = translateOrDefault(key, fallback, params);
  elements.backgroundLibraryStatus.setAttribute('data-i18n', key);
}

function getBackgroundRotationSeenCount(poolLength) {
  const total = Math.max(0, Number(poolLength) || 0);
  if (!total) {
    return 0;
  }
  const queueLength = Array.isArray(backgroundRotationQueue) ? backgroundRotationQueue.length : 0;
  const excludedCount = backgroundRotationExclusions instanceof Set ? backgroundRotationExclusions.size : 0;
  return Math.max(0, total - queueLength - excludedCount);
}

function renderBackgroundRotationCounter(poolLength) {
  if (!elements.backgroundRotationCounter) {
    return;
  }
  const total = Math.max(0, Number(poolLength) || 0);
  const seen = getBackgroundRotationSeenCount(total);
  elements.backgroundRotationCounter.textContent = `${seen}/${total}`;
}

function renderBackgroundRotationMeta(options = {}) {
  const poolLength = Math.max(
    0,
    Number(options.poolLength != null ? options.poolLength : getBackgroundItems().length) || 0
  );
  renderBackgroundRotationCounter(poolLength);
}

function setLocalBackgroundItems(uris, options = {}) {
  const uniqueUris = Array.from(new Set((uris || []).filter(uri => typeof uri === 'string' && uri)));
  localBackgroundItems = uniqueUris.map((uri, index) => ({ id: `local-background-${index}`, imageUrl: uri }));
  backgroundLibraryLabel = typeof options.label === 'string' ? options.label : backgroundLibraryLabel;
  setBackgroundLibraryStatus(localBackgroundItems.length ? 'ready' : 'idle');
  const shouldRestoreRotation = options.restoreRotationState !== false;
  const didRestore = shouldRestoreRotation && restoreBackgroundRotationState(localBackgroundItems.length);
  if (!didRestore) {
    resetBackgroundRotationSession(localBackgroundItems.length);
  }
  if (options.persist !== false) {
    writeStoredLocalBackgroundBank({ uris: uniqueUris, label: backgroundLibraryLabel });
  }
  if (localBackgroundItems.length) {
    backgroundIndex = pickNextBackgroundIndex(localBackgroundItems.length);
    setImageBackgroundEnabled(true, { resetIndex: false, showEmptyStatus: false, force: true });
  } else {
    backgroundIndex = 0;
    clearStoredBackgroundRotationState();
  }
  persistBackgroundRotationState(localBackgroundItems.length);
  applyBackgroundImage();
  renderCollectionDownloadsGallery(getBackgroundItems());
  updateCollectionDownloadsVisibility();
  renderBackgroundRotationMeta({ poolLength: localBackgroundItems.length });
}

function clearBackgroundTimer() {
  if (backgroundTimerId != null) {
    clearTimeout(backgroundTimerId);
    backgroundTimerId = null;
  }
}

function scheduleBackgroundRotation() {
  clearBackgroundTimer();
  const pool = getBackgroundItems();
  if (!imageBackgroundEnabled || !pool.length) {
    return;
  }
  const delay = Math.max(1000, getBackgroundRotationDuration() || 300000);
  backgroundTimerId = setTimeout(() => {
    const activePool = getBackgroundItems();
    if (!activePool.length) {
      clearBackgroundTimer();
      return;
    }
    backgroundIndex = pickNextBackgroundIndex(activePool.length);
    applyBackgroundImage();
  }, delay);
}

function resolveBackgroundUrl(rawUrl) {
  if (typeof rawUrl !== 'string') {
    return '';
  }
  const normalized = rawUrl.trim();
  if (!normalized) {
    return '';
  }
  const isContentUri = normalized.startsWith('content://');
  if (isContentUri && window.AndroidBridge && typeof window.AndroidBridge.resolveContentUri === 'function') {
    try {
      const resolved = window.AndroidBridge.resolveContentUri(normalized);
      if (typeof resolved === 'string' && resolved.trim()) {
        return resolved.trim();
      }
    } catch (error) {
      console.warn('Unable to resolve background URI', error);
    }
  }
  return normalized;
}

function applyBackgroundImage() {
  clearBackgroundTimer();
  if (backgroundLoadTimerId != null) {
    clearTimeout(backgroundLoadTimerId);
    backgroundLoadTimerId = null;
  }
  const pool = getBackgroundItems();
  const hasPool = pool.length > 0;
  const canDisplay = imageBackgroundEnabled && hasPool;
  if (!elements.favoriteBackground) {
    if (canDisplay) {
      scheduleBackgroundRotation();
    }
    return;
  }

  if (!canDisplay) {
    lastLoadedBackgroundUrl = '';
    elements.favoriteBackground.style.backgroundImage = '';
    elements.favoriteBackground.toggleAttribute('hidden', true);
    document.body.classList.toggle('favorite-background-active', false);
    renderBackgroundRotationMeta({ poolLength: pool.length });
    return;
  }

  if (backgroundIndex >= pool.length) {
    backgroundIndex = 0;
  }
  const current = pool[backgroundIndex] || null;
  const backgroundUrl = resolveBackgroundUrl(current ? getFullImageSrc(current) : '');
  const activePage = document.body?.dataset?.activePage;
  const isGameActive = !activePage || activePage === 'game';
  const shouldShow = Boolean(current) && isGameActive;
  const fallbackShouldShow = Boolean(lastLoadedBackgroundUrl) && isGameActive;
  const handleMissingBackground = () => {
    markBackgroundIndexExcluded(backgroundIndex, pool.length);
    if (fallbackShouldShow) {
      elements.favoriteBackground.style.backgroundImage = `url("${lastLoadedBackgroundUrl}")`;
      elements.favoriteBackground.toggleAttribute('hidden', !fallbackShouldShow);
      document.body.classList.toggle('favorite-background-active', fallbackShouldShow);
    } else {
      lastLoadedBackgroundUrl = '';
      elements.favoriteBackground.style.backgroundImage = '';
      elements.favoriteBackground.toggleAttribute('hidden', true);
      document.body.classList.toggle('favorite-background-active', false);
    }
    renderBackgroundRotationMeta({ poolLength: pool.length });
    if (pool.length > 1) {
      backgroundIndex = pickNextBackgroundIndex(pool.length);
      setTimeout(() => applyBackgroundImage(), 80);
      return;
    }
    scheduleBackgroundRotation();
  };

  if (!backgroundUrl) {
    handleMissingBackground();
    return;
  }

  const applyLoadedBackground = () => {
    elements.favoriteBackground.style.backgroundImage = `url("${backgroundUrl}")`;
    elements.favoriteBackground.toggleAttribute('hidden', !shouldShow);
    document.body.classList.toggle('favorite-background-active', shouldShow);
    lastLoadedBackgroundUrl = backgroundUrl;
    markBackgroundIndexSeen(backgroundIndex, pool.length);
    renderBackgroundRotationMeta({ poolLength: pool.length });
    scheduleBackgroundRotation();
  };

  const settleWithMissingBackground = () => {
    if (backgroundLoadTimerId != null) {
      clearTimeout(backgroundLoadTimerId);
      backgroundLoadTimerId = null;
    }
    handleMissingBackground();
  };

  const settleWithLoadedBackground = () => {
    if (backgroundLoadTimerId != null) {
      clearTimeout(backgroundLoadTimerId);
      backgroundLoadTimerId = null;
    }
    applyLoadedBackground();
  };

  const loader = new Image();
  loader.onload = settleWithLoadedBackground;
  loader.onerror = settleWithMissingBackground;
  loader.referrerPolicy = 'no-referrer';
  backgroundLoadTimerId = setTimeout(() => {
    if (loader.complete && loader.naturalWidth > 0) {
      settleWithLoadedBackground();
      return;
    }
    settleWithMissingBackground();
  }, 2500);
  loader.src = backgroundUrl;
}

function refreshBackgroundPool(options = {}) {
  const pool = getBackgroundItems();
  const shouldRandomize = options.resetIndex === true;
  if (shouldRandomize && pool.length) {
    resetBackgroundRotationSession(pool.length);
    backgroundIndex = pickNextBackgroundIndex(pool.length);
  } else if (backgroundIndex >= pool.length) {
    backgroundIndex = 0;
  }
  persistBackgroundRotationState(pool.length);
  applyBackgroundImage();
}

function normalizeBackgroundBankPayload(payload) {
  if (typeof payload === 'string') {
    try {
      const parsed = JSON.parse(payload);
      return normalizeBackgroundBankPayload(parsed);
    } catch (error) {
      console.warn('Unable to parse background bank payload', error);
      return null;
    }
  }
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  const uris = Array.isArray(payload.uris)
    ? payload.uris.filter(uri => typeof uri === 'string' && uri)
    : [];
  const label = typeof payload.label === 'string' ? payload.label : '';
  return { uris, label };
}

function handleNativeBackgroundBank(payload) {
  const normalized = normalizeBackgroundBankPayload(payload);
  setBackgroundLibraryStatus('ready');
  if (!normalized || !normalized.uris.length) {
    setLocalBackgroundItems([], { label: normalized?.label || '', persist: false, restoreRotationState: false });
    return;
  }
  setLocalBackgroundItems(normalized.uris, { label: normalized.label, persist: true, restoreRotationState: false });
  showToast(
    translateOrDefault(
      'scripts.app.background.bankReady',
      'Image bank added: {count} images.',
      { count: normalized.uris.length }
    )
  );
}

function handleBackgroundBankError(reason) {
  const normalized = typeof reason === 'string' ? reason : '';
  setBackgroundLibraryStatus(hasLocalBackgrounds() ? 'ready' : 'error');
  let key = 'scripts.app.background.error';
  let fallback = 'Unable to access this folder.';
  if (normalized === 'permission-denied') {
    key = 'scripts.app.background.permission';
    fallback = 'Allow image access to use this folder.';
  } else if (normalized === 'empty') {
    key = 'scripts.app.background.empty';
    fallback = 'No images found in this folder.';
  } else if (normalized === 'cancelled') {
    key = 'scripts.app.background.cancelled';
    fallback = 'Selection cancelled.';
  }
  showToast(translateOrDefault(key, fallback));
}

function applyStoredLocalBackgroundBank() {
  const stored = readStoredLocalBackgroundBank();
  if (!stored || !stored.uris?.length) {
    return;
  }
  backgroundLibraryLabel = stored.label || backgroundLibraryLabel;
  setLocalBackgroundItems(stored.uris, { label: stored.label || '', persist: false, restoreRotationState: true });
}

function requestNativeBackgroundBank() {
  if (window.AndroidBridge && typeof window.AndroidBridge.loadBackgroundImageBank === 'function') {
    if (!hasLocalBackgrounds()) {
      setBackgroundLibraryStatus('loading');
    }
    window.AndroidBridge.loadBackgroundImageBank();
  }
}

function handleBackgroundLibraryRequest() {
  setBackgroundLibraryStatus('loading');
  if (window.AndroidBridge && typeof window.AndroidBridge.pickBackgroundImageBank === 'function') {
    window.AndroidBridge.pickBackgroundImageBank();
    return;
  }
  setBackgroundLibraryStatus(hasLocalBackgrounds() ? 'ready' : 'idle');
  showToast(translateOrDefault('scripts.app.background.unsupported', 'This action requires the Android app.'));
}

function updateBackgroundDurationControl() {
  if (!elements.backgroundDurationSelect) {
    return;
  }
  elements.backgroundDurationSelect.value = String(getBackgroundRotationDuration());
}

function handleBackgroundDurationChange() {
  if (!elements.backgroundDurationSelect) {
    return;
  }
  const selected = Number(elements.backgroundDurationSelect.value);
  backgroundRotationMs = Number.isFinite(selected) && selected > 0
    ? selected
    : getImageBackgroundRotationMs();
  writeStoredBackgroundDuration(backgroundRotationMs);
  applyBackgroundImage();
}

function handleBackgroundToggleClick() {
  setImageBackgroundEnabled(!imageBackgroundEnabled, { resetIndex: true });
}

function initBackgroundOptions() {
  updateBackgroundDurationControl();
  if (elements.backgroundDurationSelect) {
    elements.backgroundDurationSelect.addEventListener('change', handleBackgroundDurationChange);
  }
  if (elements.backgroundLibraryButton) {
    elements.backgroundLibraryButton.addEventListener('click', handleBackgroundLibraryRequest);
  }
  if (elements.backgroundToggleButton) {
    elements.backgroundToggleButton.addEventListener('click', handleBackgroundToggleClick);
  }
  updateBackgroundToggleLabel();
  applyStoredLocalBackgroundBank();
  renderBackgroundLibraryStatus();
  renderBackgroundRotationMeta();
}

function subscribeBackgroundLanguageUpdates() {
  const handler = () => {
    updateBackgroundDurationControl();
    renderBackgroundLibraryStatus();
    updateBackgroundToggleLabel();
    renderBackgroundRotationMeta();
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

function getVisibleImageItems() {
  const enabledSources = new Set(getEnabledImageSources().map(source => source.id));
  const dismissed = imageFeedDismissedIds instanceof Set ? imageFeedDismissedIds : new Set();
  const normalizedItems = (Array.isArray(imageFeedItems) ? imageFeedItems : [])
    .filter(item => enabledSources.has(item.sourceId))
    .filter(item => !dismissed.has(item.id));
  const hydrated = normalizedItems.map(entry => applyCachedAssetToItem(entry));
  imageFeedVisibleItems = hydrated;
  if (hydrated.length === 0) {
    imageFeedCurrentIndex = 0;
  } else if (imageFeedCurrentIndex >= hydrated.length) {
    imageFeedCurrentIndex = 0;
  }
  writeStoredImageCurrentIndex(imageFeedCurrentIndex);
  return hydrated;
}

function updateImagesEmptyState(visibleItems = imageFeedVisibleItems) {
  if (!elements.imagesEmptyState) {
    return;
  }
  const shouldShow = !(Array.isArray(visibleItems) && visibleItems.length);
  elements.imagesEmptyState.toggleAttribute('hidden', !shouldShow);
}

function setImagesCurrentIndex(nextIndex) {
  const total = Array.isArray(imageFeedVisibleItems) ? imageFeedVisibleItems.length : 0;
  if (!total) {
    imageFeedCurrentIndex = 0;
    writeStoredImageCurrentIndex(imageFeedCurrentIndex);
    return;
  }
  const clamped = Math.max(0, Math.min(Number(nextIndex) || 0, total - 1));
  imageFeedCurrentIndex = clamped;
  writeStoredImageCurrentIndex(imageFeedCurrentIndex);
}

function renderImagesViewer(visibleItems = imageFeedVisibleItems) {
  const items = Array.isArray(visibleItems) ? visibleItems : [];
  const normalizedItems = items.map(entry => applyCachedAssetToItem(entry));
  const current = normalizedItems[imageFeedCurrentIndex] || null;
  if (!elements.imagesActiveImage || !elements.imagesActiveTitle || !elements.imagesActiveSource) {
    return;
  }
  if (!current) {
    elements.imagesActiveImage.src = '';
    elements.imagesActiveImage.alt = '';
    elements.imagesActiveTitle.textContent = '—';
    elements.imagesActiveSource.textContent = '';
    updateImagesEmptyState(normalizedItems);
    if (elements.imagesHideButton) {
      elements.imagesHideButton.disabled = true;
    }
    if (elements.imagesOpenButton) {
      elements.imagesOpenButton.disabled = true;
    }
    if (elements.imagesDownloadButton) {
      elements.imagesDownloadButton.disabled = true;
    }
    return;
  }
  const fullSrc = getFullImageSrc(current);
  elements.imagesActiveImage.src = fullSrc;
  elements.imagesActiveImage.alt = current.title || getImageSourceLabelById(current.sourceId);
  elements.imagesActiveTitle.textContent = current.title || '';
  elements.imagesActiveSource.textContent = getImageSourceLabelById(current.sourceId);
  updateImagesEmptyState(normalizedItems);
  warmImageAsset(current);
  queueThumbnailGeneration([current]);
  if (elements.imagesHideButton) {
    elements.imagesHideButton.disabled = false;
  }
  if (elements.imagesOpenButton) {
    elements.imagesOpenButton.disabled = false;
  }
  if (elements.imagesDownloadButton) {
    elements.imagesDownloadButton.disabled = false;
  }
}

function renderImagesGallery(visibleItems = imageFeedVisibleItems) {
  if (!elements.imagesGallery) {
    return;
  }
  elements.imagesGallery.replaceChildren();
  const items = Array.isArray(visibleItems) ? visibleItems : [];
  const normalizedItems = items.map(entry => applyCachedAssetToItem(entry));
  normalizedItems.forEach((item, index) => {
    const card = document.createElement('article');
    card.className = 'images-card';
    card.dataset.id = item.id;
    if (index === imageFeedCurrentIndex) {
      card.classList.add('is-active');
    }
    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'images-card__dismiss';
    dismiss.textContent = '✕';
    dismiss.setAttribute('aria-label', translateOrDefault(
      'index.sections.images.actions.dismiss',
      'Remove this image'
    ));
    dismiss.setAttribute('data-i18n', 'index.sections.images.actions.dismiss');
    dismiss.addEventListener('click', event => {
      event.stopPropagation();
      dismissImageItem(item.id);
    });
    const thumb = document.createElement('img');
    thumb.className = 'images-card__thumb';
    thumb.loading = 'lazy';
    thumb.decoding = 'async';
    thumb.src = getThumbnailSrc(item);
    thumb.alt = item.title || getImageSourceLabelById(item.sourceId);
    const body = document.createElement('div');
    body.className = 'images-card__body';
    const title = document.createElement('p');
    title.className = 'images-card__title';
    title.textContent = item.title || '';
    const source = document.createElement('p');
    source.className = 'images-card__source';
    source.textContent = getImageSourceLabelById(item.sourceId);
    body.append(title, source);
    card.append(dismiss, thumb, body);
    card.addEventListener('click', () => {
      setImagesCurrentIndex(index);
      refreshImagesDisplay({ skipStatus: true });
    });
    elements.imagesGallery.append(card);
  });
  queueThumbnailGeneration(normalizedItems);
  updateImagesEmptyState(normalizedItems);
}

function renderImageSources() {
  if (!elements.imagesSourcesList) {
    return;
  }
  const sources = getAvailableImageSources();
  const enabledSources = new Set(getEnabledImageSources(sources).map(source => source.id));
  elements.imagesSourcesList.replaceChildren();
  sources.forEach(source => {
    const listItem = document.createElement('li');
    listItem.className = 'images-source';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.id = `images-source-${source.id}`;
    input.checked = enabledSources.has(source.id);
    const label = document.createElement('label');
    label.htmlFor = input.id;
    label.className = 'images-source__label';
    const sourceLabel = getImageSourceLabel(source);
    label.textContent = sourceLabel;
    label.setAttribute(
      'aria-label',
      translateOrDefault('index.sections.images.sources.toggle', 'Toggle {source}', { source: sourceLabel })
    );
    input.addEventListener('change', () => {
      if (!(imageFeedEnabledSources instanceof Set)) {
        imageFeedEnabledSources = new Set(enabledSources);
      }
      if (input.checked) {
        imageFeedEnabledSources.add(source.id);
      } else {
        imageFeedEnabledSources.delete(source.id);
      }
      writeStoredImageSources(imageFeedEnabledSources);
      refreshImagesDisplay({ skipStatus: true });
      setImagesStatus('index.sections.images.status.idle', 'Click refresh to load images.');
    });
    listItem.append(input, label);
    elements.imagesSourcesList.append(listItem);
  });
}

function updateBackgroundToggleLabel() {
  if (!elements.backgroundToggleButton) {
    return;
  }
  const key = imageBackgroundEnabled
    ? 'index.sections.options.background.toggle.on'
    : 'index.sections.options.background.toggle.off';
  const fallback = imageBackgroundEnabled ? 'Disable slideshow' : 'Enable slideshow';
  elements.backgroundToggleButton.textContent = translateOrDefault(key, fallback);
  elements.backgroundToggleButton.setAttribute('data-i18n', key);
  elements.backgroundToggleButton.dataset.state = imageBackgroundEnabled ? 'on' : 'off';
}

function setImageBackgroundEnabled(enabled, options = {}) {
  const nextValue = Boolean(enabled);
  const force = options.force === true;
  if (imageBackgroundEnabled === nextValue && !force) {
    updateBackgroundToggleLabel();
    return imageBackgroundEnabled;
  }
  imageBackgroundEnabled = nextValue;
  writeStoredImageBackgroundEnabled(imageBackgroundEnabled);
  updateBackgroundToggleLabel();
  const pool = getBackgroundItems();
  if (imageBackgroundEnabled) {
    if (!pool.length && options.showEmptyStatus !== false) {
      showToast(
        translateOrDefault(
          'index.sections.options.background.status.empty',
          'Select a background folder in Options > Background.'
        )
      );
    }
    const shouldResetIndex = options.resetIndex !== false;
    refreshBackgroundPool({ resetIndex: shouldResetIndex });
  } else {
    applyBackgroundImage();
  }
  return imageBackgroundEnabled;
}

function selectImageById(itemId) {
  const index = imageFeedVisibleItems.findIndex(item => item.id === itemId);
  if (index >= 0) {
    setImagesCurrentIndex(index);
    refreshImagesDisplay({ skipStatus: true });
  }
}

function refreshLightboxBodyState() {
  const imageLightboxOpen = elements.imagesLightbox && !elements.imagesLightbox.hasAttribute('hidden');
  const downloadsLightboxOpen = elements.collectionDownloadsLightbox
    && !elements.collectionDownloadsLightbox.hasAttribute('hidden');

  if (imageLightboxOpen || downloadsLightboxOpen) {
    document.body.classList.add('images-lightbox-open');
    return;
  }

  document.body.classList.remove('images-lightbox-open');
}

function openCurrentImage() {
  const current = imageFeedVisibleItems[imageFeedCurrentIndex];
  if (!current || typeof window?.open !== 'function') {
    return;
  }
  const url = current.link || current.imageUrl;
  if (url) {
    window.open(url, '_blank', 'noopener');
  }
}

function downloadCurrentImage() {
  const current = imageFeedVisibleItems[imageFeedCurrentIndex];
  if (!current) {
    return;
  }
  const downloadUrl = getFullImageSrc(current) || current.imageUrl;
  if (window.AndroidBridge && typeof window.AndroidBridge.saveImageToDevice === 'function') {
    window.AndroidBridge.saveImageToDevice(downloadUrl, current.id);
    return;
  }
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.download = '';
  document.body.append(link);
  link.click();
  link.remove();
}

function hideCurrentImage() {
  const current = imageFeedVisibleItems[imageFeedCurrentIndex];
  if (!current) {
    return;
  }
  dismissImageItem(current.id);
}

function openActiveImageFullscreen() {
  const current = imageFeedVisibleItems[imageFeedCurrentIndex];
  if (!current || !elements.imagesLightbox || !elements.imagesLightboxImage || !elements.imagesLightboxCaption) {
    return;
  }
  const fullSrc = getFullImageSrc(current);
  elements.imagesLightboxImage.src = fullSrc;
  elements.imagesLightboxImage.alt = current.title || getImageSourceLabelById(current.sourceId);
  elements.imagesLightboxCaption.textContent = current.title || getImageSourceLabelById(current.sourceId) || '';
  elements.imagesLightbox.removeAttribute('hidden');
  refreshLightboxBodyState();
}

function closeImageFullscreen() {
  if (!elements.imagesLightbox || !elements.imagesLightboxImage) {
    return;
  }
  elements.imagesLightbox.setAttribute('hidden', 'hidden');
  elements.imagesLightboxImage.src = '';
  elements.imagesLightboxImage.alt = '';
  if (elements.imagesLightboxCaption) {
    elements.imagesLightboxCaption.textContent = '';
  }
  refreshLightboxBodyState();
}

function handleImageSavedOnDevice(success) {
  const key = success
    ? 'index.sections.images.status.downloaded'
    : 'index.sections.images.status.downloadError';
  const fallback = success
    ? 'Image saved to Pictures/Atom2Univers.'
    : 'Unable to save image.';
  showToast(translateOrDefault(key, fallback, { path: IMAGE_DOWNLOAD_TARGET_PATH }));
}

if (typeof globalThis !== 'undefined') {
  globalThis.onImageSaved = function onImageSaved(success) {
    handleImageSavedOnDevice(Boolean(success));
  };
}

if (typeof globalThis !== 'undefined') {
  globalThis.onBackgroundImageBankLoaded = function onBackgroundImageBankLoaded(payload) {
    handleNativeBackgroundBank(payload);
  };
  globalThis.onBackgroundImageBankError = function onBackgroundImageBankError(reason) {
    handleBackgroundBankError(reason);
  };
}

function refreshImagesDisplay(options = {}) {
  const visibleItems = getVisibleImageItems();
  renderImagesViewer(visibleItems);
  renderImagesGallery(visibleItems);
  if (!options.skipStatus && !imageFeedIsLoading && !imageFeedLastError) {
    const key = visibleItems.length
      ? 'index.sections.images.status.ready'
      : 'index.sections.images.status.empty';
    const fallback = visibleItems.length ? `${visibleItems.length} images loaded.` : 'No images to display with these filters.';
    setImagesStatus(key, fallback, { count: visibleItems.length });
  }
  return visibleItems;
}

function abortImageFeedRequest() {
  if (imageFeedAbortController && typeof imageFeedAbortController.abort === 'function') {
    try {
      imageFeedAbortController.abort();
    } catch (error) {
      // Ignore abort errors.
    }
  }
  imageFeedAbortController = null;
}

async function fetchImageFeeds(options = {}) {
  const settings = Object.assign({ silent: false }, options);
  const availableSources = getAvailableImageSources();
  const enabledSources = getEnabledImageSources(availableSources);
  if (!enabledSources.length) {
    imageFeedItems = [];
    imageFeedLastError = null;
    setImagesStatus('index.sections.images.status.sources', 'Enable at least one source to display images.');
    refreshImagesDisplay({ skipStatus: true });
    return [];
  }
  if (typeof fetch !== 'function') {
    setImagesStatus('index.sections.images.status.error', 'Unable to load images right now.');
    return [];
  }

  abortImageFeedRequest();
  imageFeedIsLoading = !settings.silent;
  if (!settings.silent) {
    setImagesStatus('index.sections.images.status.loading', 'Loading images…');
  }
  const timeoutMs = Number(getImageFeedSettings()?.requestTimeoutMs) || 12000;
  const maxImageBytes = getImageMaxBytes();
  const aggregatedItems = [];
  let lastError = null;

  const fetchWithTimeout = async url => {
    const controller = typeof AbortController === 'function' ? new AbortController() : null;
    const timeoutId = timeoutMs > 0 && typeof setTimeout === 'function'
      ? setTimeout(() => controller?.abort?.(), timeoutMs)
      : null;
    abortImageFeedRequest();
    imageFeedAbortController = controller;
    try {
      const response = await fetch(url, controller ? { signal: controller.signal } : undefined);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return await response.text();
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      if (imageFeedAbortController === controller) {
        imageFeedAbortController = null;
      }
    }
  };

  for (const source of enabledSources) {
    const requestUrls = buildImageRequestUrls(source.feedUrl);
    let xmlText = null;
    for (const url of requestUrls) {
      try {
        xmlText = await fetchWithTimeout(url);
        if (xmlText) {
          break;
        }
      } catch (error) {
        lastError = error;
        if (error?.name === 'AbortError') {
          break;
        }
      }
    }
    if (!xmlText) {
      continue;
    }
    const parsed = parseImageFeed(xmlText, source.id)
      .map(item => Object.assign({}, item, { sourceId: source.id }));
    aggregatedItems.push(...parsed);
  }

  try {
    const uniqueMap = new Map();
    aggregatedItems.forEach(item => {
      if (item && !uniqueMap.has(item.id)) {
        uniqueMap.set(item.id, item);
      }
    });
    const maxItems = Math.max(1, Number(getImageFeedSettings()?.maxItems) || DEFAULT_IMAGE_FEED_SETTINGS.maxItems);
    const dismissed = imageFeedDismissedIds instanceof Set ? imageFeedDismissedIds : new Set();
    const sorted = Array.from(uniqueMap.values())
      .filter(item => item && !dismissed.has(item.id))
      .sort((a, b) => {
        const first = Number(a?.pubDate) || 0;
        const second = Number(b?.pubDate) || 0;
        return second - first;
      });
    const filteredBySize = await filterImagesBySizeLimit(sorted, maxImageBytes, maxItems);
    const mergedWithCache = filteredBySize.map(item => applyCachedAssetToItem(item));
    imageFeedItems = mergedWithCache.slice(0, maxItems);
    imageFeedLastError = null;
    const visibleItems = refreshImagesDisplay({ skipStatus: true });
    setImagesStatus(
      'index.sections.images.status.ready',
      `${visibleItems.length} images loaded.`,
      { count: visibleItems.length }
    );
    return visibleItems;
  } catch (error) {
    imageFeedLastError = error || lastError;
    console.warn('Unable to load image feeds', error);
    setImagesStatus('index.sections.images.status.error', 'Unable to load images right now.');
    return [];
  } finally {
    imageFeedIsLoading = false;
  }
}

function initImagesModule() {
  imageAssetCache = new Map();
  imageFeedEnabledSources = readStoredImageSources();
  imageFeedDismissedIds = readStoredDismissedImages();
  imageFeedCurrentIndex = readStoredImageCurrentIndex();
  imageBackgroundEnabled = readStoredImageBackgroundEnabled();
  renderImageSources();
  refreshImagesDisplay({ skipStatus: true });
  setImagesStatus('index.sections.images.status.idle', 'Click refresh to load images.');
  applyBackgroundImage();
}

function subscribeImagesLanguageUpdates() {
  const handler = () => {
    renderImageSources();
    refreshImagesDisplay({ skipStatus: true });
    setImagesStatus('index.sections.images.status.idle', 'Click refresh to load images.');
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

function getNewsSettings() {
  return ACTIVE_NEWS_SETTINGS || DEFAULT_NEWS_SETTINGS;
}

function normalizeNewsSource(source, index = 0) {
  if (!source || typeof source !== 'object') {
    return null;
  }
  const id = typeof source.id === 'string' && source.id.trim()
    ? source.id.trim()
    : `source-${index}`;
  const feedUrl = typeof source.feedUrl === 'string' ? source.feedUrl.trim() : '';
  if (!feedUrl) {
    return null;
  }
  const titleKey = typeof source.titleKey === 'string' ? source.titleKey : '';
  const title = typeof source.title === 'string' ? source.title : '';
  const searchUrlTemplate = typeof source.searchUrlTemplate === 'string'
    ? source.searchUrlTemplate
    : '';
  return {
    id,
    titleKey,
    title,
    feedUrl,
    searchUrlTemplate
  };
}

function getAvailableNewsSources() {
  const settings = getNewsSettings();
  const rawSources = Array.isArray(settings?.sources) ? settings.sources : [];
  const normalized = rawSources
    .map((source, index) => normalizeNewsSource(source, index))
    .filter(Boolean);
  if (normalized.length) {
    return normalized;
  }
  const fallbackFeed = typeof settings?.defaultFeedUrl === 'string'
    ? settings.defaultFeedUrl
    : DEFAULT_NEWS_SETTINGS.defaultFeedUrl;
  const fallbackSearch = typeof settings?.searchUrlTemplate === 'string'
    ? settings.searchUrlTemplate
    : DEFAULT_NEWS_SETTINGS.searchUrlTemplate;
  return [normalizeNewsSource({
    id: 'default',
    titleKey: 'index.sections.news.sources.default',
    feedUrl: fallbackFeed,
    searchUrlTemplate: fallbackSearch
  })].filter(Boolean);
}

function readStoredNewsSources() {
  try {
    const raw = globalThis.localStorage?.getItem(NEWS_SOURCES_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      const ids = parsed.filter(entry => typeof entry === 'string' && entry.trim());
      return new Set(ids);
    }
  } catch (error) {
    console.warn('Unable to read enabled news sources', error);
  }
  return null;
}

function writeStoredNewsSources(enabledSources) {
  try {
    const ids = Array.from(enabledSources || []).filter(id => typeof id === 'string' && id);
    if (!ids.length) {
      globalThis.localStorage?.setItem(NEWS_SOURCES_STORAGE_KEY, JSON.stringify([]));
      return;
    }
    globalThis.localStorage?.setItem(NEWS_SOURCES_STORAGE_KEY, JSON.stringify(ids));
  } catch (error) {
    console.warn('Unable to persist enabled news sources', error);
  }
}

function getNewsSourceLabel(source) {
  if (!source) {
    return '';
  }
  const fallback = source.title || source.id || 'News source';
  if (source.titleKey) {
    return translateOrDefault(source.titleKey, fallback);
  }
  return fallback;
}

function getNewsSourceLabelById(sourceId) {
  const sources = getAvailableNewsSources();
  const match = sources.find(source => source.id === sourceId);
  return getNewsSourceLabel(match);
}

function getEnabledNewsSources(availableSources = getAvailableNewsSources()) {
  if (!(newsEnabledSources instanceof Set)) {
    newsEnabledSources = readStoredNewsSources();
  }
  if (!(newsEnabledSources instanceof Set)) {
    newsEnabledSources = new Set(availableSources.map(source => source.id));
  }
  const validIds = new Set(availableSources.map(source => source.id));
  const normalizedIds = Array.from(newsEnabledSources).filter(id => validIds.has(id));
  if (normalizedIds.length !== newsEnabledSources.size) {
    newsEnabledSources = new Set(normalizedIds);
    writeStoredNewsSources(newsEnabledSources);
  }
  return availableSources.filter(source => newsEnabledSources.has(source.id));
}

function readStoredNewsEnabled() {
  try {
    const stored = globalThis.localStorage?.getItem(NEWS_FEATURE_ENABLED_STORAGE_KEY);
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
    console.warn('Unable to read news preference', error);
  }
  return null;
}

function writeStoredNewsEnabled(enabled) {
  try {
    const value = enabled ? '1' : '0';
    globalThis.localStorage?.setItem(NEWS_FEATURE_ENABLED_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist news preference', error);
  }
}

function readStoredNewsHiddenItems(now = Date.now()) {
  try {
    const raw = globalThis.localStorage?.getItem(NEWS_HIDDEN_ITEMS_STORAGE_KEY);
    if (!raw) {
      return new Map();
    }
    const parsed = JSON.parse(raw);
    const entries = Array.isArray(parsed)
      ? parsed
      : Array.isArray(parsed?.items)
        ? parsed.items
        : [];
    const cutoff = now - NEWS_HIDDEN_ENTRY_TTL_MS;
    const normalized = new Map();
    entries.forEach(entry => {
      const key = typeof entry === 'string'
        ? entry
        : typeof entry?.key === 'string'
          ? entry.key
          : typeof entry?.id === 'string'
            ? entry.id
            : '';
      const timestamp = Number(entry?.hiddenAt);
      const hiddenAt = Number.isFinite(timestamp) ? timestamp : now;
      if (key && hiddenAt >= cutoff) {
        const existing = normalized.get(key);
        normalized.set(key, Math.min(hiddenAt, existing ?? hiddenAt));
      }
    });
    if (normalized.size !== entries.length) {
      writeStoredNewsHiddenItems(normalized, now);
    }
    return normalized;
  } catch (error) {
    console.warn('Unable to read hidden news list', error);
  }
  return new Map();
}

function writeStoredNewsHiddenItems(hiddenEntries, now = Date.now()) {
  try {
    const cutoff = now - NEWS_HIDDEN_ENTRY_TTL_MS;
    const items = [];
    if (hiddenEntries instanceof Map) {
      Array.from(hiddenEntries.entries()).forEach(([key, hiddenAt]) => {
        const normalizedKey = typeof key === 'string' ? key : '';
        const timestamp = Number(hiddenAt);
        const normalizedTimestamp = Number.isFinite(timestamp) ? timestamp : now;
        if (!normalizedKey || normalizedTimestamp < cutoff) {
          hiddenEntries.delete(key);
          return;
        }
        items.push({ key: normalizedKey, hiddenAt: normalizedTimestamp });
      });
    }
    if (!items.length) {
      globalThis.localStorage?.removeItem(NEWS_HIDDEN_ITEMS_STORAGE_KEY);
      return;
    }
    items.sort((first, second) => (first.hiddenAt ?? 0) - (second.hiddenAt ?? 0));
    globalThis.localStorage?.setItem(NEWS_HIDDEN_ITEMS_STORAGE_KEY, JSON.stringify(items));
  } catch (error) {
    console.warn('Unable to persist hidden news list', error);
  }
}

function pruneHiddenNewsItems(validItems = [], now = Date.now()) {
  if (!(newsHiddenIds instanceof Map)) {
    newsHiddenIds = new Map();
  }
  const cutoff = now - NEWS_HIDDEN_ENTRY_TTL_MS;
  const currentItems = Array.isArray(validItems) ? validItems : [];
  let changed = false;

  currentItems.forEach(item => {
    const candidates = getNewsItemCandidateIds(item);
    if (candidates.some(id => newsHiddenIds.has(id))) {
      const keys = buildHiddenNewsKeys(item);
      keys.forEach(key => {
        if (!newsHiddenIds.has(key)) {
          newsHiddenIds.set(key, now);
          changed = true;
        }
      });
    }
  });

  for (const [key, hiddenAt] of Array.from(newsHiddenIds.entries())) {
    const timestamp = Number(hiddenAt);
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      newsHiddenIds.delete(key);
      changed = true;
    }
  }

  if (changed) {
    writeStoredNewsHiddenItems(newsHiddenIds, now);
  }
}

function readStoredNewsQuery() {
  try {
    const stored = globalThis.localStorage?.getItem(NEWS_LAST_QUERY_STORAGE_KEY);
    if (typeof stored === 'string') {
      return stored.trim();
    }
  } catch (error) {
    console.warn('Unable to read last news query', error);
  }
  return '';
}

function writeStoredNewsQuery(query) {
  try {
    const normalized = typeof query === 'string' ? query.trim() : '';
    if (!normalized) {
      globalThis.localStorage?.removeItem(NEWS_LAST_QUERY_STORAGE_KEY);
      return;
    }
    globalThis.localStorage?.setItem(NEWS_LAST_QUERY_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist last news query', error);
  }
}

function normalizeNewsBannedWords(words) {
  if (typeof words === 'string') {
    return normalizeNewsBannedWords(words.split(','));
  }
  if (!Array.isArray(words)) {
    return [];
  }
  const seen = new Set();
  const normalized = [];
  words.forEach(entry => {
    const word = typeof entry === 'string' ? entry.trim() : '';
    const lower = word.toLowerCase();
    if (word && !seen.has(lower)) {
      seen.add(lower);
      normalized.push(word);
    }
  });
  return normalized;
}

function getNormalizedNewsBannedWords() {
  return (Array.isArray(newsBannedWords) ? newsBannedWords : [])
    .map(word => (typeof word === 'string' ? word.trim().toLowerCase() : ''))
    .filter(Boolean);
}

function readStoredNewsBannedWords() {
  try {
    const raw = globalThis.localStorage?.getItem(NEWS_BANNED_WORDS_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    return normalizeNewsBannedWords(parsed);
  } catch (error) {
    console.warn('Unable to read banned news words', error);
  }
  return [];
}

function writeStoredNewsBannedWords(words) {
  try {
    const normalized = normalizeNewsBannedWords(words);
    globalThis.localStorage?.setItem(NEWS_BANNED_WORDS_STORAGE_KEY, JSON.stringify(normalized));
    return normalized;
  } catch (error) {
    console.warn('Unable to persist banned news words', error);
  }
  return normalizeNewsBannedWords(words);
}

function isAndroidNewsBridgeAvailable() {
  const bridge = getAndroidManualBackupBridge();
  return !!(bridge && typeof bridge.loadNews === 'function');
}

function resetAndroidNewsRequestState() {
  if (newsAndroidTimeoutId != null) {
    clearTimeout(newsAndroidTimeoutId);
    newsAndroidTimeoutId = null;
  }
  newsAndroidResponseResolver = null;
}

function requestNewsViaAndroid(feedUrl, timeoutMs) {
  const bridge = getAndroidManualBackupBridge();
  if (!bridge || typeof bridge.loadNews !== 'function') {
    return null;
  }
  resetAndroidNewsRequestState();
  return new Promise(resolve => {
    const safeResolve = xml => {
      resetAndroidNewsRequestState();
      resolve(typeof xml === 'string' ? xml : '');
    };
    newsAndroidResponseResolver = safeResolve;
    const timeout = Math.max(0, Number(timeoutMs) || 0);
    if (timeout > 0) {
      newsAndroidTimeoutId = setTimeout(() => {
        if (newsAndroidResponseResolver === safeResolve) {
          newsAndroidResponseResolver = null;
          safeResolve('');
        }
      }, timeout);
    }
    try {
      bridge.loadNews(feedUrl || '');
    } catch (error) {
      newsAndroidResponseResolver = null;
      safeResolve('');
    }
  });
}

if (typeof window !== 'undefined') {
  window.onNewsLoaded = function onNewsLoaded(xmlText) {
    if (!newsAndroidResponseResolver) {
      return;
    }
    const resolver = newsAndroidResponseResolver;
    resetAndroidNewsRequestState();
    resolver(typeof xmlText === 'string' ? xmlText : '');
  };
}

function isNewsEnabled() {
  return newsFeatureEnabled !== false;
}

function buildNewsFeedUrl(source, query) {
  if (!source || typeof source.feedUrl !== 'string') {
    return '';
  }
  const trimmedQuery = typeof query === 'string' ? query.trim() : '';
  if (trimmedQuery && source.searchUrlTemplate) {
    return source.searchUrlTemplate.replace('{query}', encodeURIComponent(trimmedQuery));
  }
  return source.feedUrl;
}

function buildNewsRequestUrls(feedUrl) {
  const settings = getNewsSettings();
  const proxies = [];
  if (Array.isArray(settings?.proxyBaseUrls)) {
    proxies.push(...settings.proxyBaseUrls);
  }
  if (typeof settings?.proxyBaseUrl === 'string') {
    proxies.push(settings.proxyBaseUrl);
  }
  const sanitizedProxies = proxies
    .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
    .filter(Boolean);
  const urls = sanitizedProxies.map(proxy => {
    if (proxy.includes('{feed}')) {
      return proxy.replace('{feed}', encodeURIComponent(feedUrl));
    }
    const needsEncoding = proxy.includes('?');
    const target = needsEncoding ? encodeURIComponent(feedUrl) : feedUrl;
    return `${proxy}${target}`;
  });
  urls.push(feedUrl);
  return Array.from(new Set(urls));
}

function isNewsItemBlocked(item, normalizedBannedWords = getNormalizedNewsBannedWords()) {
  if (!item || !normalizedBannedWords.length) {
    return false;
  }
  const text = `${item.title || ''} ${item.description || ''}`.toLowerCase();
  return normalizedBannedWords.some(word => word && text.includes(word));
}

function filterNewsItems(items) {
  if (!Array.isArray(items)) {
    return [];
  }
  const normalizedBannedWords = getNormalizedNewsBannedWords();
  if (!normalizedBannedWords.length) {
    return items.filter(Boolean);
  }
  return items.filter(item => item && !isNewsItemBlocked(item, normalizedBannedWords));
}

function filterNewsItemsByQuery(items, query) {
  const normalizedQuery = typeof query === 'string' ? query.trim().toLowerCase() : '';
  if (!normalizedQuery) {
    return Array.isArray(items) ? items : [];
  }
  return (Array.isArray(items) ? items : []).filter(item => {
    const text = `${item?.title || ''} ${item?.description || ''}`.toLowerCase();
    return text.includes(normalizedQuery);
  });
}

function normalizeNewsTitle(title) {
  if (typeof title !== 'string') {
    return '';
  }
  let normalized = title.trim();
  // Remove leading tags such as "[VIDÉO]" or "[LIVE]" that often get prepended to Google News titles.
  normalized = normalized.replace(/^\s*\[[^\]]+]\s*/g, '');
  normalized = normalized.toLowerCase();
  normalized = normalized.replace(/\s+/g, ' ').trim();
  return normalized;
}

function normalizeNewsUrl(url) {
  if (typeof url !== 'string') {
    return '';
  }
  const trimmed = url.trim();
  try {
    const parsed = new URL(trimmed);
    parsed.hostname = parsed.hostname.toLowerCase();
    const paramsToStrip = ['ved', 'amp', 'oc'];
    Array.from(parsed.searchParams.keys()).forEach(name => {
      const lower = name.toLowerCase();
      if (lower.startsWith('utm_') || paramsToStrip.includes(lower)) {
        parsed.searchParams.delete(name);
      }
    });
    parsed.hash = '';
    // Remove trailing "/amp" path segments often used by Google News mirrors.
    parsed.pathname = parsed.pathname.replace(/\/amp\/?$/i, '/');
    const normalizedSearch = parsed.searchParams.toString();
    parsed.search = normalizedSearch ? `?${normalizedSearch}` : '';
    return parsed.toString();
  } catch (error) {
    return trimmed.toLowerCase();
  }
}

function hashCanonicalNewsKey(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index);
    hash |= 0;
  }
  return Math.abs(hash).toString(36);
}

function buildCanonicalNewsId(title, url) {
  const normalizedTitle = normalizeNewsTitle(title);
  const normalizedUrl = normalizeNewsUrl(url);
  const base = `${normalizedTitle}|${normalizedUrl}`;
  const hashed = hashCanonicalNewsKey(base);
  return hashed || base || normalizedTitle || normalizedUrl || 'news-item';
}

function buildNewsAlternateIds(title, link, guid, pubDate) {
  const alternates = [guid, link, `${title}-${pubDate}`]
    .filter(candidate => typeof candidate === 'string' && candidate.trim())
    .map(candidate => candidate.trim());
  return Array.from(new Set(alternates));
}

function buildHiddenNewsKeys(item, fallbackId = '') {
  const keys = new Set();
  const candidates = getNewsItemCandidateIds(item);
  candidates.forEach(id => {
    if (typeof id === 'string' && id) {
      keys.add(id);
    }
  });
  const normalizedTitle = normalizeNewsTitle(item?.title);
  if (normalizedTitle) {
    keys.add(`title:${normalizedTitle}`);
    keys.add(`title-hash:${hashCanonicalNewsKey(normalizedTitle)}`);
  }
  const normalizedUrl = normalizeNewsUrl(item?.link);
  if (normalizedUrl) {
    keys.add(`url:${normalizedUrl}`);
  }
  const normalizedFallback = typeof fallbackId === 'string' ? fallbackId : '';
  if (normalizedFallback) {
    keys.add(normalizedFallback);
  }
  return keys;
}

function getNewsItemCandidateIds(item) {
  if (!item) {
    return [];
  }
  const ids = [];
  if (typeof item.id === 'string' && item.id) {
    ids.push(item.id);
  }
  if (Array.isArray(item.alternateIds)) {
    item.alternateIds.forEach(extraId => {
      if (typeof extraId === 'string' && extraId) {
        ids.push(extraId);
      }
    });
  }
  return ids;
}

function parseNewsFeed(xmlText) {
  if (typeof DOMParser !== 'function') {
    return [];
  }
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlText, 'application/xml');
  const items = [];
  doc.querySelectorAll('item').forEach(item => {
    const title = item.querySelector('title')?.textContent?.trim() || '';
    const link = item.querySelector('link')?.textContent?.trim() || '';
    const guid = item.querySelector('guid')?.textContent?.trim();
    const description = item.querySelector('description')?.textContent?.trim() || '';
    const pubDateText = item.querySelector('pubDate')?.textContent?.trim();
    const pubDate = pubDateText ? Date.parse(pubDateText) : 0;
    const canonicalId = buildCanonicalNewsId(title, link || guid || '');
    if (!canonicalId || !title) {
      return;
    }
    const story = {
      id: canonicalId,
      alternateIds: buildNewsAlternateIds(title, link, guid, pubDate),
      title,
      link,
      description,
      pubDate: Number.isFinite(pubDate) ? pubDate : 0
    };
    items.push(story);
  });
  items.sort((first, second) => {
    const a = Number(first?.pubDate) || 0;
    const b = Number(second?.pubDate) || 0;
    return b - a;
  });
  return items;
}

function isNewsItemHidden(item, hiddenEntries = newsHiddenIds instanceof Map ? newsHiddenIds : new Map()) {
  if (!item) {
    return false;
  }
  const cutoff = Date.now() - NEWS_HIDDEN_ENTRY_TTL_MS;
  const keys = buildHiddenNewsKeys(item);
  for (const key of keys) {
    if (!hiddenEntries.has(key)) {
      continue;
    }
    const timestamp = Number(hiddenEntries.get(key));
    if (!Number.isFinite(timestamp) || timestamp >= cutoff) {
      return true;
    }
  }
  return false;
}

function getVisibleNewsItems() {
  const hidden = newsHiddenIds instanceof Map ? newsHiddenIds : new Map();
  return (Array.isArray(newsItems) ? newsItems : [])
    .filter(item => item && typeof item.id === 'string' && !isNewsItemHidden(item, hidden));
}

function applyNewsBannedWordsFilter() {
  const baseItems = Array.isArray(newsRawItems) && newsRawItems.length ? newsRawItems : newsItems;
  const filteredItems = filterNewsItems(baseItems);
  const maxItems = Math.max(1, Number(getNewsSettings()?.maxItems) || DEFAULT_NEWS_SETTINGS.maxItems);
  newsItems = filteredItems.slice(0, maxItems);
  renderNewsList();
  renderNewsTicker();
}

function updateNewsFeedLabel(query) {
  if (!elements.newsFeedLabel) {
    return;
  }
  const hasQuery = typeof query === 'string' && query.trim();
  const key = hasQuery
    ? 'index.sections.news.feed.search'
    : 'index.sections.news.feed.default';
  const fallback = hasQuery
    ? `Results for “${query}”`
    : 'Latest headlines';
  const translated = translateOrDefault(key, fallback, { query });
  elements.newsFeedLabel.setAttribute('data-i18n', key);
  elements.newsFeedLabel.textContent = translated;
}

function setNewsStatus(key, fallback, params = {}) {
  if (!elements.newsStatus) {
    return;
  }
  const text = translateOrDefault(key, fallback, params);
  elements.newsStatus.setAttribute('data-i18n', key);
  elements.newsStatus.textContent = text;
}

function renderNewsList() {
  if (!elements.newsList) {
    return;
  }
  const disabled = !isNewsEnabled();
  if (elements.newsDisabledNotice) {
    elements.newsDisabledNotice.toggleAttribute('hidden', !disabled);
  }
  if (disabled) {
    newsHighlightedStoryId = null;
    elements.newsList.replaceChildren();
    if (elements.newsEmptyState) {
      elements.newsEmptyState.setAttribute('hidden', '');
    }
    setNewsStatus('index.sections.news.disabled', 'Enable News in Options to display this feed.');
    renderNewsTicker();
    return;
  }

  const enabledSources = getEnabledNewsSources();
  if (!enabledSources.length) {
    newsHighlightedStoryId = null;
    elements.newsList.replaceChildren();
    if (elements.newsEmptyState) {
      elements.newsEmptyState.setAttribute('hidden', '');
    }
    setNewsStatus('index.sections.news.sources.disabled', 'Enable at least one news source to display headlines.');
    renderNewsTicker();
    return;
  }

  const visibleItems = getVisibleNewsItems();
  elements.newsList.replaceChildren();

  if (elements.newsEmptyState) {
    elements.newsEmptyState.toggleAttribute('hidden', visibleItems.length > 0 || newsIsLoading);
  }

  if (!visibleItems.length && !newsIsLoading && !newsLastError) {
    setNewsStatus('index.sections.news.status.empty', 'No news to display.');
  }

  visibleItems.forEach(item => {
    const listItem = document.createElement('li');
    listItem.className = 'news-card';
    listItem.dataset.articleId = item.id;

    if (newsHighlightedStoryId && item.id === newsHighlightedStoryId) {
      listItem.classList.add('news-card--highlight');
    }

    const content = document.createElement('div');
    const actions = document.createElement('div');
    actions.className = 'news-card__actions';

    const title = document.createElement('h3');
    title.className = 'news-card__title';
    if (item.link) {
      const anchor = document.createElement('a');
      anchor.href = item.link;
      anchor.target = '_blank';
      anchor.rel = 'noreferrer noopener';
      anchor.textContent = item.title;
      title.append(anchor);
    } else {
      title.textContent = item.title;
    }

    const meta = document.createElement('p');
    meta.className = 'news-card__meta';
    const formattedDate = formatDateTimeLocalized(item.pubDate);
    const metaKey = 'index.sections.news.published';
    const metaFallback = formattedDate ? `Published on ${formattedDate}` : '';
    meta.setAttribute('data-i18n', metaKey);
    meta.textContent = formattedDate
      ? translateOrDefault(metaKey, metaFallback, { date: formattedDate })
      : '';

    content.append(title);
    if (meta.textContent) {
      content.append(meta);
    }

    const sourceLabel = item.sourceId ? getNewsSourceLabelById(item.sourceId) : '';
    if (sourceLabel) {
      const source = document.createElement('p');
      source.className = 'news-card__source';
      const sourceKey = 'index.sections.news.sources.label';
      source.setAttribute('data-i18n', sourceKey);
      source.textContent = translateOrDefault(sourceKey, `Source: ${sourceLabel}`, { source: sourceLabel });
      content.append(source);
    }

    const openButton = document.createElement('a');
    openButton.className = 'news-card__button news-card__button--primary';
    openButton.href = item.link || '#';
    openButton.target = '_blank';
    openButton.rel = 'noreferrer noopener';
    const openKey = 'index.sections.news.actions.openArticle';
    openButton.setAttribute('data-i18n', openKey);
    openButton.textContent = translateOrDefault(openKey, 'Open article');
    if (!item.link) {
      openButton.addEventListener('click', event => {
        event.preventDefault();
      });
    }
    actions.append(openButton);

    const hideButton = document.createElement('button');
    hideButton.type = 'button';
    hideButton.className = 'news-card__button';
    hideButton.dataset.articleId = item.id;
    const hideKey = 'index.sections.news.actions.hideArticle';
    hideButton.setAttribute('data-i18n', hideKey);
    hideButton.textContent = translateOrDefault(hideKey, 'Hide this story');
    hideButton.addEventListener('click', () => {
      hideNewsStory(item.id);
    });
    actions.append(hideButton);

    listItem.append(content, actions);
    elements.newsList.append(listItem);
  });

  applyNewsHighlight();
  renderNewsTicker();
}

function handleNewsSourceToggle(sourceId, enabled) {
  const availableSources = getAvailableNewsSources();
  const validIds = new Set(availableSources.map(source => source.id));
  if (!validIds.has(sourceId)) {
    return;
  }
  if (!(newsEnabledSources instanceof Set)) {
    newsEnabledSources = new Set(validIds);
  }
  if (enabled) {
    newsEnabledSources.add(sourceId);
  } else {
    newsEnabledSources.delete(sourceId);
  }
  writeStoredNewsSources(newsEnabledSources);
  renderNewsSources();
  if (isNewsEnabled()) {
    fetchNewsFeed(newsCurrentQuery, { silent: true });
  }
}

function renderNewsSources() {
  if (!elements.newsSourcesList) {
    return;
  }
  const sources = getAvailableNewsSources();
  const enabledSources = getEnabledNewsSources(sources);
  const enabledIds = new Set(enabledSources.map(source => source.id));

  elements.newsSourcesList.replaceChildren();
  sources.forEach(source => {
    const listItem = document.createElement('li');
    listItem.className = 'news-sources__item';

    const label = document.createElement('label');
    label.className = 'news-sources__toggle';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = enabledIds.has(source.id);
    checkbox.dataset.sourceId = source.id;
    const toggleKey = 'index.sections.news.sources.toggleLabel';
    const sourceLabel = getNewsSourceLabel(source);
    checkbox.setAttribute(
      'aria-label',
      translateOrDefault(toggleKey, `Toggle ${sourceLabel}`, { source: sourceLabel })
    );
    checkbox.addEventListener('change', () => {
      handleNewsSourceToggle(source.id, checkbox.checked);
    });

    const name = document.createElement('span');
    name.className = 'news-sources__name';
    name.textContent = sourceLabel;
    if (source.titleKey) {
      name.setAttribute('data-i18n', source.titleKey);
    }

    label.append(checkbox, name);
    listItem.append(label);

    const url = document.createElement('span');
    url.className = 'news-sources__url';
    try {
      const hostname = new URL(source.feedUrl).hostname.replace(/^www\./, '');
      url.textContent = hostname;
    } catch (error) {
      url.textContent = source.feedUrl;
    }
    listItem.append(url);
    elements.newsSourcesList.append(listItem);
  });

  if (elements.newsSourcesEmpty) {
    const emptyKey = 'index.sections.news.sources.empty';
    const shouldShow = sources.length > 0 && enabledIds.size === 0;
    elements.newsSourcesEmpty.setAttribute('data-i18n', emptyKey);
    elements.newsSourcesEmpty.textContent = translateOrDefault(
      emptyKey,
      'Enable at least one source to display news.'
    );
    elements.newsSourcesEmpty.toggleAttribute('hidden', !shouldShow);
  }
}

function applyNewsHighlight(options = {}) {
  if (!elements.newsList) {
    return null;
  }
  let highlightedCard = null;
  elements.newsList.querySelectorAll('.news-card').forEach(card => {
    const isMatch = newsHighlightedStoryId && card.dataset.articleId === newsHighlightedStoryId;
    card.classList.toggle('news-card--highlight', Boolean(isMatch));
    if (isMatch) {
      highlightedCard = card;
    }
  });

  if (!highlightedCard) {
    newsHighlightedStoryId = null;
    return null;
  }

  if (options.scroll && typeof highlightedCard.scrollIntoView === 'function') {
    highlightedCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
  return highlightedCard;
}

function clearNewsTickerRotation() {
  if (newsTickerTimerId != null) {
    clearTimeout(newsTickerTimerId);
    newsTickerTimerId = null;
  }
}

function renderCurrentNewsTickerItem() {
  if (!elements.newsTickerItems) {
    return;
  }
  const currentItem = newsTickerEntries[newsTickerCurrentIndex];
  elements.newsTickerItems.replaceChildren();
  newsTickerActiveStoryId = currentItem?.id || null;
  if (!currentItem) {
    return;
  }
  const span = document.createElement('div');
  span.className = 'news-ticker__item';
  span.textContent = currentItem.title;
  span.title = currentItem.title;
  span.dataset.articleId = currentItem.id;
  elements.newsTickerItems.append(span);
}

function scheduleNewsTickerRotation(displayDurationMs) {
  clearNewsTickerRotation();
  if (!newsTickerEntries.length || newsTickerEntries.length === 1) {
    return;
  }
  const duration = Math.max(1000, displayDurationMs);
  newsTickerTimerId = setTimeout(() => {
    newsTickerCurrentIndex = (newsTickerCurrentIndex + 1) % newsTickerEntries.length;
    renderCurrentNewsTickerItem();
    scheduleNewsTickerRotation(duration);
  }, duration);
}

function renderNewsTicker() {
  if (!elements.newsTicker || !elements.newsTickerItems) {
    return;
  }
  const settings = getNewsSettings();
  const pageId = document.body?.dataset?.activePage || '';
  const visibleItems = getVisibleNewsItems();
  const maxItems = Math.max(1, Number(settings?.bannerItemCount) || DEFAULT_NEWS_SETTINGS.bannerItemCount);
  const tickerItems = visibleItems.slice(0, maxItems);
  const shouldShow = isNewsEnabled() && pageId === 'game' && tickerItems.length > 0;
  const displayDurationMs = Math.max(
    1000,
    Number(settings?.bannerDisplayDurationMs) || DEFAULT_NEWS_SETTINGS.bannerDisplayDurationMs || 15000
  );

  elements.newsTicker.toggleAttribute('hidden', !shouldShow);
  elements.newsTicker.setAttribute('aria-hidden', shouldShow ? 'false' : 'true');
  if (!shouldShow) {
    newsTickerEntries = [];
    newsTickerActiveStoryId = null;
    newsTickerCurrentIndex = 0;
    elements.newsTickerItems.replaceChildren();
    clearNewsTickerRotation();
    return;
  }

  newsTickerEntries = tickerItems;
  if (newsTickerCurrentIndex >= newsTickerEntries.length) {
    newsTickerCurrentIndex = 0;
  }

  renderCurrentNewsTickerItem();
  scheduleNewsTickerRotation(displayDurationMs);
}

function hideNewsStory(storyId) {
  if (!storyId) {
    return;
  }
  const story = (Array.isArray(newsItems) ? newsItems : []).find(item => item?.id === storyId) || null;
  const keys = buildHiddenNewsKeys(story, storyId);
  const now = Date.now();
  keys.forEach(key => newsHiddenIds.set(key, now));
  writeStoredNewsHiddenItems(newsHiddenIds, now);
  renderNewsList();
}

function restoreHiddenNewsStories() {
  newsHiddenIds = new Map();
  writeStoredNewsHiddenItems(newsHiddenIds);
  renderNewsList();
}

function clearNewsRefreshTimer() {
  if (newsRefreshTimerId != null) {
    clearTimeout(newsRefreshTimerId);
    newsRefreshTimerId = null;
  }
}

function abortNewsRequest() {
  if (newsFetchAbortController && typeof newsFetchAbortController.abort === 'function') {
    try {
      newsFetchAbortController.abort();
    } catch (error) {
      // Ignore abort errors.
    }
  }
  newsFetchAbortController = null;
  resetAndroidNewsRequestState();
}

async function fetchNewsFeed(query = newsCurrentQuery, options = {}) {
  if (!isNewsEnabled()) {
    return [];
  }
  const settings = Object.assign({ silent: false }, options);
  const timeoutMs = Number(getNewsSettings()?.requestTimeoutMs) || DEFAULT_NEWS_SETTINGS.requestTimeoutMs;
  const availableSources = getAvailableNewsSources();
  const enabledSources = getEnabledNewsSources(availableSources);
  const useAndroidBridge = isAndroidNewsBridgeAvailable();

  if (!enabledSources.length) {
    newsRawItems = [];
    newsItems = [];
    newsLastError = null;
    setNewsStatus('index.sections.news.sources.disabled', 'Enable at least one news source to display headlines.');
    renderNewsList();
    renderNewsTicker();
    return [];
  }

  if (!useAndroidBridge && typeof fetch !== 'function') {
    setNewsStatus('index.sections.news.status.error', 'Unable to fetch news right now.');
    return [];
  }

  abortNewsRequest();
  newsIsLoading = !settings.silent;

  if (!settings.silent) {
    setNewsStatus('index.sections.news.status.loading', 'Loading news…');
  }

  const aggregatedItems = [];
  let successfulSources = 0;
  let lastError = null;
  const fetchWithTimeout = async url => {
    const controller = typeof AbortController === 'function' ? new AbortController() : null;
    const timeoutId = timeoutMs > 0 && typeof setTimeout === 'function'
      ? setTimeout(() => controller?.abort?.(), timeoutMs)
      : null;
    abortNewsRequest();
    newsFetchAbortController = controller;
    try {
      const response = await fetch(url, controller ? { signal: controller.signal } : undefined);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return await response.text();
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      if (newsFetchAbortController === controller) {
        newsFetchAbortController = null;
      }
    }
  };

  for (const source of enabledSources) {
    const feedUrl = buildNewsFeedUrl(source, query);
    if (!feedUrl) {
      continue;
    }
    const requestUrls = buildNewsRequestUrls(feedUrl);
    const targetUrl = requestUrls.length ? requestUrls[requestUrls.length - 1] : feedUrl;
    let xmlText = null;
    if (useAndroidBridge) {
      try {
        xmlText = await requestNewsViaAndroid(targetUrl, timeoutMs);
      } catch (error) {
        lastError = error;
      }
    } else {
      for (const requestUrl of requestUrls) {
        try {
          xmlText = await fetchWithTimeout(requestUrl);
          if (xmlText) {
            break;
          }
        } catch (error) {
          lastError = error;
          if (error?.name === 'AbortError') {
            break;
          }
        }
      }
    }

    if (!xmlText) {
      continue;
    }
    successfulSources += 1;
    let parsedItems = parseNewsFeed(xmlText);
    if (query && !source.searchUrlTemplate) {
      parsedItems = filterNewsItemsByQuery(parsedItems, query);
    }
    const taggedItems = parsedItems.map(item => Object.assign({}, item, { sourceId: source.id }));
    aggregatedItems.push(...taggedItems);
  }

  try {
    if (!successfulSources) {
      throw lastError || new Error('No response received');
    }
    const filteredItems = filterNewsItems(aggregatedItems);
    const maxItems = Math.max(1, Number(getNewsSettings()?.maxItems) || DEFAULT_NEWS_SETTINGS.maxItems);
    pruneHiddenNewsItems(aggregatedItems);
    newsRawItems = aggregatedItems;
    newsItems = filteredItems.slice(0, maxItems);
    newsLastError = null;
    updateNewsFeedLabel(query);
    setNewsStatus(
      query ? 'index.sections.news.feed.search' : 'index.sections.news.feed.default',
      query ? `Results for “${query}”` : 'Latest headlines',
      { query }
    );
    renderNewsList();
    scheduleNewsRefresh();
    return newsItems;
  } catch (error) {
    newsLastError = error;
    console.warn('Unable to load news feed', error);
    const isTimeout = error?.name === 'AbortError';
    const key = isTimeout
      ? 'index.sections.news.status.timeout'
      : 'index.sections.news.status.error';
    const fallback = isTimeout
      ? 'News request timed out. Please try again.'
      : 'Unable to fetch news right now.';
    setNewsStatus(key, fallback);
    renderNewsList();
    scheduleNewsRefresh();
    return [];
  } finally {
    newsIsLoading = false;
  }
}

function scheduleNewsRefresh() {
  clearNewsRefreshTimer();
  const delay = Math.max(60 * 1000, Number(getNewsSettings()?.refreshIntervalMs) || DEFAULT_NEWS_SETTINGS.refreshIntervalMs);
  if (!isNewsEnabled() || delay <= 0) {
    return;
  }
  newsRefreshTimerId = setTimeout(() => {
    fetchNewsFeed(newsCurrentQuery, { silent: true });
  }, delay);
}

function updateNewsToggleStatusLabel(enabled) {
  if (!elements.newsToggleStatus) {
    return;
  }
  const key = enabled
    ? 'index.sections.options.news.state.on'
    : 'index.sections.options.news.state.off';
  const fallback = enabled ? 'News enabled' : 'News hidden';
  elements.newsToggleStatus.setAttribute('data-i18n', key);
  elements.newsToggleStatus.textContent = translateOrDefault(key, fallback);
}

function updateNewsControlsAvailability() {
  const disabled = !isNewsEnabled();
  [
    'newsSearchInput',
    'newsSearchButton',
    'newsClearSearchButton',
    'newsRefreshButton'
  ].forEach(key => {
    const element = elements[key];
    if (element) {
      element.disabled = disabled;
    }
  });
}

function applyNewsEnabled(enabled, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true, skipFetch: false }, options);
  newsFeatureEnabled = enabled !== false;
  if (settings.updateControl && elements.newsToggle) {
    elements.newsToggle.checked = newsFeatureEnabled;
  }
  updateNewsToggleStatusLabel(newsFeatureEnabled);
  updateNewsControlsAvailability();
  if (typeof setNavButtonLockState === 'function') {
    setNavButtonLockState(elements.navNewsButton, newsFeatureEnabled);
  } else if (elements.navNewsButton) {
    elements.navNewsButton.hidden = !newsFeatureEnabled;
    elements.navNewsButton.setAttribute('aria-hidden', newsFeatureEnabled ? 'false' : 'true');
    elements.navNewsButton.disabled = !newsFeatureEnabled;
    elements.navNewsButton.setAttribute('aria-disabled', newsFeatureEnabled ? 'false' : 'true');
    if (!newsFeatureEnabled) {
      elements.navNewsButton.classList.remove('active');
    }
  }
  if (settings.persist) {
    writeStoredNewsEnabled(newsFeatureEnabled);
  }
  if (!newsFeatureEnabled) {
    clearNewsRefreshTimer();
    abortNewsRequest();
    newsIsLoading = false;
    setNewsStatus('index.sections.news.disabled', 'Enable News in Options to display this feed.');
    renderNewsList();
    return false;
  }
  renderNewsList();
  if (!settings.skipFetch) {
    fetchNewsFeed(newsCurrentQuery, { silent: true });
  }
  return newsFeatureEnabled;
}

function handleNewsSearchSubmit() {
  if (!isNewsEnabled()) {
    return;
  }
  const query = elements.newsSearchInput?.value || '';
  newsCurrentQuery = query.trim();
  writeStoredNewsQuery(newsCurrentQuery);
  updateNewsFeedLabel(newsCurrentQuery);
  fetchNewsFeed(newsCurrentQuery);
}

function handleNewsClearSearch() {
  newsCurrentQuery = '';
  if (elements.newsSearchInput) {
    elements.newsSearchInput.value = '';
  }
  writeStoredNewsQuery('');
  updateNewsFeedLabel(newsCurrentQuery);
  if (isNewsEnabled()) {
    fetchNewsFeed(newsCurrentQuery);
  }
}

function handleNewsRefresh() {
  if (!isNewsEnabled()) {
    return;
  }
  fetchNewsFeed(newsCurrentQuery);
}

function handleNewsBannedWordsSave() {
  if (!elements.newsBannedWordsInput) {
    return;
  }
  const inputValue = elements.newsBannedWordsInput.value || '';
  const normalized = normalizeNewsBannedWords(inputValue.split(','));
  newsBannedWords = writeStoredNewsBannedWords(normalized);
  elements.newsBannedWordsInput.value = newsBannedWords.join(', ');
  applyNewsBannedWordsFilter();
  if (isNewsEnabled()) {
    fetchNewsFeed(newsCurrentQuery, { silent: true });
  }
}

function initNewsModule() {
  newsHiddenIds = readStoredNewsHiddenItems();
  if (!(newsHiddenIds instanceof Map)) {
    newsHiddenIds = new Map();
  }
  newsCurrentQuery = readStoredNewsQuery();
  newsBannedWords = readStoredNewsBannedWords();
  const storedSources = readStoredNewsSources();
  if (storedSources instanceof Set) {
    newsEnabledSources = storedSources;
  }
  if (elements.newsSearchInput) {
    elements.newsSearchInput.value = newsCurrentQuery;
  }
  if (elements.newsBannedWordsInput) {
    elements.newsBannedWordsInput.value = newsBannedWords.join(', ');
  }
  renderNewsSources();
  updateNewsFeedLabel(newsCurrentQuery);
  renderNewsList();
  if (isNewsEnabled()) {
    fetchNewsFeed(newsCurrentQuery, { silent: true });
  }
}

function initNewsOption() {
  const stored = readStoredNewsEnabled();
  const defaultEnabled = getNewsSettings()?.enabledByDefault !== false;
  const initialEnabled = stored === null ? defaultEnabled : stored === true;
  applyNewsEnabled(initialEnabled, { persist: false, updateControl: true, skipFetch: true });
  if (elements.newsToggle) {
    elements.newsToggle.addEventListener('change', () => {
      applyNewsEnabled(elements.newsToggle.checked, { persist: true, updateControl: false });
    });
  }
}

function subscribeNewsLanguageUpdates() {
  const handler = () => {
    updateNewsToggleStatusLabel(isNewsEnabled());
    updateNewsFeedLabel(newsCurrentQuery);
    renderNewsSources();
    renderNewsList();
    const currentStatusKey = elements.newsStatus?.getAttribute('data-i18n');
    if (currentStatusKey) {
      setNewsStatus(currentStatusKey, elements.newsStatus.textContent, { query: newsCurrentQuery });
    }
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


function getRadioSettings() {
  return ACTIVE_RADIO_SETTINGS || DEFAULT_RADIO_SETTINGS;
}

function normalizeRadioServer(server) {
  if (typeof server !== 'string') {
    return '';
  }
  const trimmed = server.trim();
  if (!trimmed) {
    return '';
  }
  const normalized = trimmed.replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(normalized)) {
    return '';
  }
  return normalized;
}

function getRadioServers() {
  const settings = getRadioSettings();
  const servers = Array.isArray(settings?.servers) ? settings.servers : [];
  const normalized = servers.map(normalizeRadioServer).filter(Boolean);
  if (normalized.length) {
    return normalized;
  }
  return DEFAULT_RADIO_SETTINGS.servers.map(normalizeRadioServer).filter(Boolean);
}

function normalizeRadioStation(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const id = typeof raw.stationuuid === 'string' && raw.stationuuid.trim()
    ? raw.stationuuid.trim()
    : typeof raw.id === 'string' && raw.id.trim()
      ? raw.id.trim()
      : '';
  const url = typeof raw.url === 'string' && raw.url.trim() ? raw.url.trim() : '';
  if (!id || !url) {
    return null;
  }
  const name = typeof raw.name === 'string' && raw.name.trim() ? raw.name.trim() : 'Station';
  const country = typeof raw.country === 'string' ? raw.country.trim() : '';
  const language = typeof raw.language === 'string' ? raw.language.trim() : '';
  const favicon = typeof raw.favicon === 'string' && raw.favicon.trim() ? raw.favicon.trim() : '';
  const bitrateValue = Number(raw.bitrate);
  const bitrate = Number.isFinite(bitrateValue) && bitrateValue > 0 ? Math.round(bitrateValue) : null;
  return { id, stationuuid: id, name, url, country, language, favicon, bitrate };
}

function readStoredRadioFavorites() {
  try {
    const raw = globalThis.localStorage?.getItem(RADIO_FAVORITES_STORAGE_KEY);
    if (!raw) {
      return new Map();
    }
    const parsed = JSON.parse(raw);
    const entries = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.items) ? parsed.items : [];
    const map = new Map();
    entries.forEach(entry => {
      const normalized = normalizeRadioStation(entry);
      if (normalized) {
        map.set(normalized.id, normalized);
      }
    });
    return map;
  } catch (error) {
    console.warn('Unable to read radio favorites', error);
    return new Map();
  }
}

function writeStoredRadioFavorites(favoritesMap) {
  try {
    const values = Array.from(favoritesMap?.values?.() || []);
    globalThis.localStorage?.setItem(RADIO_FAVORITES_STORAGE_KEY, JSON.stringify(values));
  } catch (error) {
    console.warn('Unable to persist radio favorites', error);
  }
}

function setRadioStatus(key, fallback, params) {
  radioStatusState = { key, fallback, params };
  if (!elements?.radioStatus) {
    return;
  }
  const label = translateOrDefault(key, fallback, params);
  elements.radioStatus.textContent = label;
  if (key) {
    elements.radioStatus.setAttribute('data-i18n', key);
  }
}

function setRadioPlayerStatus(key, fallback, params) {
  radioPlayerStatusState = { key, fallback, params };
  if (!elements?.radioPlayerStatus) {
    return;
  }
  const label = translateOrDefault(key, fallback, params);
  elements.radioPlayerStatus.textContent = label;
  if (key) {
    elements.radioPlayerStatus.setAttribute('data-i18n', key);
  }
}

function formatRadioNowPlaying(payload) {
  if (!payload) {
    return '';
  }
  if (typeof payload === 'string') {
    return payload.trim();
  }
  const title = typeof payload.title === 'string' ? payload.title.trim() : '';
  const artist = typeof payload.artist === 'string' ? payload.artist.trim() : '';
  if (artist && title) {
    return `${artist} — ${title}`;
  }
  return artist || title;
}

function setRadioNowPlaying(payload) {
  radioNowPlayingInfo = formatRadioNowPlaying(payload);
  renderRadioPlayer();
}

function setRadioLoading(isLoading) {
  radioIsLoading = !!isLoading;
  if (elements?.radioSearchButton) {
    elements.radioSearchButton.disabled = isLoading;
  }
  if (elements?.radioResetButton) {
    elements.radioResetButton.disabled = isLoading;
  }
  if (elements?.radioStatus) {
    elements.radioStatus.setAttribute('aria-busy', isLoading ? 'true' : 'false');
  }
}

function formatRadioStationMeta(station) {
  if (!station) {
    return '';
  }
  const parts = [];
  const country = station.country || '';
  const language = station.language || '';
  if (country && language) {
    parts.push(translateOrDefault('index.sections.radio.meta.countryLanguage', `${country} · ${language}`, { country, language }));
  } else if (country) {
    parts.push(country);
  } else if (language) {
    parts.push(language);
  }
  if (station.bitrate) {
    parts.push(translateOrDefault('index.sections.radio.meta.bitrate', `${station.bitrate} kbps`, { value: station.bitrate }));
  }
  return parts.join(' · ');
}

function renderRadioPlayer() {
  if (!elements) {
    return;
  }
  const station = radioSelectedStation;
  const hasStation = !!(station && station.id);
  const androidRecordingAvailable = isAndroidRadioBridgeAvailable();
  if (elements.radioStationLogo) {
    if (hasStation && station.favicon) {
      elements.radioStationLogo.src = station.favicon;
      elements.radioStationLogo.hidden = false;
    } else {
      elements.radioStationLogo.hidden = true;
      elements.radioStationLogo.removeAttribute('src');
    }
  }
  if (elements.radioStationName) {
    if (hasStation) {
      elements.radioStationName.textContent = station.name;
      elements.radioStationName.removeAttribute('data-i18n');
    } else {
      elements.radioStationName.textContent = translateOrDefault(
        'index.sections.radio.player.empty',
        'Aucune station sélectionnée.'
      );
      elements.radioStationName.setAttribute('data-i18n', 'index.sections.radio.player.empty');
    }
  }
  if (elements.radioStationDetails) {
    const meta = hasStation ? formatRadioStationMeta(station) : '';
    elements.radioStationDetails.textContent = meta;
  }
  if (elements.radioNowPlaying) {
    elements.radioNowPlaying.textContent = radioNowPlayingInfo || '';
    elements.radioNowPlaying.hidden = !radioNowPlayingInfo;
    elements.radioNowPlaying.title = radioNowPlayingInfo || '';
  }
  const favoriteLabel = hasStation && radioFavorites.has(station.id)
    ? translateOrDefault('index.sections.radio.actions.favoriteRemove', 'Retirer des favoris')
    : translateOrDefault('index.sections.radio.actions.favorite', 'Ajouter aux favoris');
  if (elements.radioFavoriteButton) {
    elements.radioFavoriteButton.textContent = favoriteLabel;
    elements.radioFavoriteButton.disabled = !hasStation;
  }
  if (elements.radioPlayButton) {
    elements.radioPlayButton.disabled = !hasStation;
  }
  if (elements.radioRecordButton) {
    elements.radioRecordButton.disabled = !hasStation || radioIsRecording || !androidRecordingAvailable;
    elements.radioRecordButton.title = androidRecordingAvailable
      ? ''
      : translateOrDefault(
        'index.sections.radio.player.status.recordUnavailable',
        'Enregistrement disponible uniquement sur Android.'
      );
  }
  if (elements.radioRecordStopButton) {
    elements.radioRecordStopButton.disabled = !hasStation || !radioIsRecording || !androidRecordingAvailable;
  }
  if (elements.radioPauseButton) {
    elements.radioPauseButton.disabled = !hasStation;
  }
  if (elements.radioStopButton) {
    elements.radioStopButton.disabled = !hasStation;
  }
  if (elements.radioReloadButton) {
    elements.radioReloadButton.disabled = !hasStation;
  }
  if (radioIsRecording) {
    setRadioPlayerStatus(
      'index.sections.radio.player.status.recording',
      'Enregistrement en cours…'
    );
  }
}

function renderRadioFavorites() {
  if (!elements?.radioFavoritesList) {
    return;
  }
  const favorites = Array.from(radioFavorites.values());
  elements.radioFavoritesList.innerHTML = '';
  if (!favorites.length) {
    if (elements.radioFavoritesEmpty) {
      elements.radioFavoritesEmpty.hidden = false;
    }
    return;
  }
  if (elements.radioFavoritesEmpty) {
    elements.radioFavoritesEmpty.hidden = true;
  }
  const listenLabel = translateOrDefault('index.sections.radio.actions.listen', 'Écouter');
  const removeLabel = translateOrDefault('index.sections.radio.actions.favoriteRemove', 'Retirer des favoris');
  favorites.forEach(station => {
    const item = document.createElement('li');
    item.className = 'radio-favorite';
    const title = document.createElement('p');
    title.className = 'radio-favorite__title';
    title.textContent = station.name;
    const meta = document.createElement('p');
    meta.className = 'radio-favorite__meta';
    meta.textContent = formatRadioStationMeta(station);
    const actions = document.createElement('div');
    actions.className = 'radio-favorite__actions';
    const openButton = document.createElement('button');
    openButton.type = 'button';
    openButton.className = 'radio-player__button radio-player__button--primary';
    openButton.textContent = listenLabel;
    openButton.dataset.radioAction = 'open';
    openButton.dataset.stationId = station.id;
    const removeButton = document.createElement('button');
    removeButton.type = 'button';
    removeButton.className = 'radio-player__button';
    removeButton.textContent = removeLabel;
    removeButton.dataset.radioAction = 'favorite';
    removeButton.dataset.stationId = station.id;
    actions.appendChild(openButton);
    actions.appendChild(removeButton);
    item.appendChild(title);
    item.appendChild(meta);
    item.appendChild(actions);
    elements.radioFavoritesList.appendChild(item);
  });
}

function hideManualRadioFavoriteForm() {
  if (elements?.radioFavoritesManualForm) {
    elements.radioFavoritesManualForm.hidden = true;
    elements.radioFavoritesManualForm.setAttribute('aria-hidden', 'true');
  }
}

function showManualRadioFavoriteForm() {
  if (!elements?.radioFavoritesManualForm) {
    return false;
  }
  elements.radioFavoritesManualForm.hidden = false;
  elements.radioFavoritesManualForm.setAttribute('aria-hidden', 'false');
  if (elements.radioFavoritesUrlInput) {
    elements.radioFavoritesUrlInput.value = '';
    elements.radioFavoritesUrlInput.focus();
  }
  if (elements.radioFavoritesNameInput) {
    elements.radioFavoritesNameInput.value = '';
  }
  return true;
}

function addManualRadioFavoriteEntry(urlInput, rawName) {
  const normalizedUrl = normalizeRadioServer(urlInput);
  if (!normalizedUrl) {
    if (typeof globalThis?.alert === 'function') {
      alert(
        translateOrDefault(
          'index.sections.radio.favorites.dialog.invalidUrl',
          'Merci de saisir une URL valide commençant par http:// ou https://.'
        )
      );
    }
    return false;
  }
  const duplicate = Array.from(radioFavorites.values()).find(station => station.url === normalizedUrl);
  if (duplicate) {
    if (typeof globalThis?.alert === 'function') {
      alert(
        translateOrDefault(
          'index.sections.radio.favorites.dialog.duplicate',
          'Cette station est déjà dans vos favoris.'
        )
      );
    }
    return false;
  }
  const name = rawName && rawName.trim() ? rawName.trim() : normalizedUrl;
  const station = {
    id: `manual-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    name,
    url: normalizedUrl,
    country: '',
    language: '',
    favicon: '',
    bitrate: null
  };
  radioFavorites.set(station.id, station);
  writeStoredRadioFavorites(radioFavorites);
  renderRadioFavorites();
  renderRadioResults();
  renderRadioPlayer();
  return true;
}

function handleManualRadioFavoriteFormSubmit(event) {
  event.preventDefault();
  const urlInput = elements?.radioFavoritesUrlInput?.value?.trim() || '';
  const nameInput = elements?.radioFavoritesNameInput?.value?.trim() || '';
  const success = addManualRadioFavoriteEntry(urlInput, nameInput);
  if (success) {
    hideManualRadioFavoriteForm();
  }
}

function handleManualRadioFavoriteCancel() {
  hideManualRadioFavoriteForm();
}

function handleManualRadioFavorite() {
  if (showManualRadioFavoriteForm()) {
    return;
  }
  if (typeof globalThis?.prompt !== 'function') {
    return;
  }
  const urlPrompt = translateOrDefault(
    'index.sections.radio.favorites.dialog.urlPrompt',
    'Entrez l’URL du flux radio à ajouter :'
  );
  const urlInput = prompt(urlPrompt, '');
  if (urlInput === null) {
    return;
  }
  const normalizedUrl = normalizeRadioServer(urlInput);
  if (!normalizedUrl) {
    if (typeof globalThis?.alert === 'function') {
      alert(
        translateOrDefault(
          'index.sections.radio.favorites.dialog.invalidUrl',
          'Merci de saisir une URL valide commençant par http:// ou https://.'
        )
      );
    }
    return;
  }
  const duplicate = Array.from(radioFavorites.values()).find(station => station.url === normalizedUrl);
  if (duplicate) {
    if (typeof globalThis?.alert === 'function') {
      alert(
        translateOrDefault(
          'index.sections.radio.favorites.dialog.duplicate',
          'Cette station est déjà dans vos favoris.'
        )
      );
    }
    return;
  }
  const namePrompt = translateOrDefault(
    'index.sections.radio.favorites.dialog.namePrompt',
    'Nom de la station (optionnel) :'
  );
  const rawName = prompt(namePrompt, '');
  addManualRadioFavoriteEntry(normalizedUrl, rawName || '');
}

function renderRadioResults() {
  if (!elements?.radioResults) {
    return;
  }
  elements.radioResults.innerHTML = '';
  const hasResults = Array.isArray(radioStations) && radioStations.length > 0;
  if (elements.radioEmptyState) {
    elements.radioEmptyState.hidden = hasResults || radioIsLoading;
  }
  if (!hasResults) {
    return;
  }
  const listenLabel = translateOrDefault('index.sections.radio.actions.listen', 'Écouter');
  radioStations.forEach(station => {
    const item = document.createElement('li');
    item.className = 'radio-station';
    const content = document.createElement('div');
    const title = document.createElement('p');
    title.className = 'radio-station__title';
    title.textContent = station.name;
    const meta = document.createElement('p');
    meta.className = 'radio-station__meta';
    meta.textContent = formatRadioStationMeta(station);
    content.appendChild(title);
    content.appendChild(meta);
    const actions = document.createElement('div');
    actions.className = 'radio-station__actions';
    const listenButton = document.createElement('button');
    listenButton.type = 'button';
    listenButton.className = 'radio-player__button radio-player__button--primary';
    listenButton.textContent = listenLabel;
    listenButton.dataset.radioAction = 'listen';
    listenButton.dataset.stationId = station.id;
    const favoriteButton = document.createElement('button');
    favoriteButton.type = 'button';
    favoriteButton.className = 'radio-player__button';
    favoriteButton.dataset.radioAction = 'favorite';
    favoriteButton.dataset.stationId = station.id;
    favoriteButton.textContent = radioFavorites.has(station.id)
      ? translateOrDefault('index.sections.radio.actions.favoriteRemove', 'Retirer des favoris')
      : translateOrDefault('index.sections.radio.actions.favorite', 'Ajouter aux favoris');
    actions.appendChild(listenButton);
    actions.appendChild(favoriteButton);
    item.appendChild(content);
    item.appendChild(actions);
    elements.radioResults.appendChild(item);
  });
}

function getAndroidBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.AndroidBridge;
  if (!bridge) {
    return null;
  }
  const bridgeType = typeof bridge;
  if (bridgeType === 'object' || bridgeType === 'function') {
    return bridge;
  }
  return null;
}

function getAndroidRadioBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.Android;
  if (!bridge) {
    return null;
  }
  const bridgeType = typeof bridge;
  if (bridgeType !== 'object' && bridgeType !== 'function') {
    return null;
  }
  if (typeof bridge.playStream !== 'function') {
    return null;
  }
  return bridge;
}

function isAndroidRadioBridgeAvailable() {
  const bridge = getAndroidRadioBridge();
  if (!bridge) {
    return false;
  }
  return typeof bridge.startRecording === 'function' && typeof bridge.stopRecording === 'function';
}

function setRadioRecordingState(isRecording) {
  radioIsRecording = !!isRecording;
  renderRadioPlayer();
}

function updateAndroidRadioPlayback(isPlaying) {
  const bridge = getAndroidBridge();
  if (!bridge) {
    return;
  }
  const title = radioSelectedStation?.name || '';
  const artist = radioSelectedStation ? formatRadioStationMeta(radioSelectedStation) : '';
  if (isPlaying) {
    if (typeof bridge.startForegroundAudio === 'function') {
      bridge.startForegroundAudio(title, artist, true);
      return;
    }
    if (typeof bridge.updateForegroundAudio === 'function') {
      bridge.updateForegroundAudio(title, artist, true);
    }
    return;
  }
  if (typeof bridge.updateForegroundAudio === 'function') {
    bridge.updateForegroundAudio(title, artist, false);
  }
}

function stopAndroidRadioPlayback() {
  const bridge = getAndroidBridge();
  if (!bridge) {
    return;
  }
  if (typeof bridge.stopForegroundAudio === 'function') {
    bridge.stopForegroundAudio();
  } else if (typeof bridge.updateForegroundAudio === 'function') {
    const title = radioSelectedStation?.name || '';
    const artist = radioSelectedStation ? formatRadioStationMeta(radioSelectedStation) : '';
    bridge.updateForegroundAudio(title, artist, false);
  }
}

function normalizeRadioDirectoryEntries(items, labelField = 'name') {
  if (!Array.isArray(items)) {
    return [];
  }
  return items
    .map(item => {
      const label = typeof item?.[labelField] === 'string' ? item[labelField].trim() : '';
      if (!label) {
        return null;
      }
      return label;
    })
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b));
}

async function fetchRadioDirectory(path) {
  const servers = getRadioServers();
  let lastError = null;
  for (let index = 0; index < servers.length; index += 1) {
    const base = servers[index];
    const url = `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
    const controller = typeof AbortController === 'function' ? new AbortController() : null;
    const timeoutId = controller && RADIO_REQUEST_TIMEOUT_MS > 0
      ? setTimeout(() => controller.abort(), RADIO_REQUEST_TIMEOUT_MS)
      : null;
    try {
      const response = await fetch(url, {
        headers: { Accept: 'application/json' },
        signal: controller?.signal,
        cache: 'no-cache'
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = await response.json();
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      return payload;
    } catch (error) {
      lastError = error;
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    }
  }
  throw lastError || new Error('Radio directory unavailable');
}

async function ensureRadioFiltersLoaded() {
  if (radioFiltersPromise) {
    return radioFiltersPromise;
  }
  radioFiltersPromise = (async () => {
    try {
      const showLoadingStatus = !radioCountries.length && !radioLanguages.length && !radioIsLoading;
      if (showLoadingStatus) {
        setRadioStatus('index.sections.radio.status.filtersLoading', 'Chargement des filtres en cours…');
      }
      const [countries, languages] = await Promise.all([
        fetchRadioDirectory('json/countries'),
        fetchRadioDirectory('json/languages')
      ]);
      radioCountries = normalizeRadioDirectoryEntries(countries, 'name');
      radioLanguages = normalizeRadioDirectoryEntries(languages, 'name');
      applyRadioFilters();
      if (showLoadingStatus) {
        setRadioStatus('index.sections.radio.status.idle', 'Entrez une requête pour démarrer une recherche.');
      }
    } catch (error) {
      radioLastError = error;
      radioFiltersPromise = null;
      console.warn('Unable to load radio filters', error);
      if (!radioCountries.length && !radioLanguages.length && !radioIsLoading) {
        setRadioStatus('index.sections.radio.status.filtersError', 'Impossible de charger les listes de pays et langues.');
      }
    }
  })();
  return radioFiltersPromise;
}

function applyRadioFilters() {
  if (elements?.radioCountrySelect) {
    const selected = elements.radioCountrySelect.value;
    while (elements.radioCountrySelect.options.length > 1) {
      elements.radioCountrySelect.remove(1);
    }
    radioCountries.forEach(country => {
      const option = document.createElement('option');
      option.value = country;
      option.textContent = country;
      elements.radioCountrySelect.appendChild(option);
    });
    elements.radioCountrySelect.value = selected;
  }
  if (elements?.radioLanguageSelect) {
    const selectedLang = elements.radioLanguageSelect.value;
    while (elements.radioLanguageSelect.options.length > 1) {
      elements.radioLanguageSelect.remove(1);
    }
    radioLanguages.forEach(language => {
      const option = document.createElement('option');
      option.value = language;
      option.textContent = language;
      elements.radioLanguageSelect.appendChild(option);
    });
    elements.radioLanguageSelect.value = selectedLang;
  }
}

function selectRadioStation(station, options = {}) {
  if (!station) {
    if (radioIsRecording) {
      stopRadioRecording({ silent: true });
    }
    radioSelectedStation = null;
    setRadioNowPlaying(null);
    renderRadioPlayer();
    return;
  }
  if (radioIsRecording) {
    stopRadioRecording({ silent: true });
  }
  radioSelectedStation = station;
  setRadioNowPlaying(null);
  renderRadioPlayer();
  if (options.autoplay) {
    playSelectedRadioStation();
  }
}

function handleRadioSearchSubmit(event) {
  event.preventDefault();
  const query = elements.radioSearchInput?.value?.trim() || '';
  const country = elements.radioCountrySelect?.value || '';
  const language = elements.radioLanguageSelect?.value || '';
  searchRadioStations({ query, country, language });
}

function handleRadioReset(event) {
  if (event) {
    event.preventDefault();
  }
  if (elements.radioSearchInput) {
    elements.radioSearchInput.value = '';
  }
  if (elements.radioCountrySelect) {
    elements.radioCountrySelect.value = '';
  }
  if (elements.radioLanguageSelect) {
    elements.radioLanguageSelect.value = '';
  }
  radioStations = [];
  renderRadioResults();
  setRadioStatus('index.sections.radio.status.idle', 'Entrez une requête pour démarrer une recherche.');
}

function handleRadioResultsClick(event) {
  const action = event.target?.closest('[data-radio-action]');
  if (!action) {
    return;
  }
  const stationId = action.dataset.stationId;
  if (!stationId) {
    return;
  }
  const station = radioStations.find(item => item.id === stationId) || radioFavorites.get(stationId);
  if (!station) {
    return;
  }
  const type = action.dataset.radioAction;
  if (type === 'listen') {
    selectRadioStation(station, { autoplay: true });
  } else if (type === 'favorite') {
    toggleRadioFavorite(station);
  }
}

function handleRadioFavoritesClick(event) {
  const action = event.target?.closest('[data-radio-action]');
  if (!action) {
    return;
  }
  const stationId = action.dataset.stationId;
  if (!stationId) {
    return;
  }
  const station = radioFavorites.get(stationId) || radioStations.find(item => item.id === stationId);
  if (!station) {
    return;
  }
  const type = action.dataset.radioAction;
  if (type === 'open') {
    selectRadioStation(station, { autoplay: true });
  } else if (type === 'favorite') {
    toggleRadioFavorite(station);
  }
}

function toggleRadioFavorite(station) {
  if (!station || !station.id) {
    return;
  }
  const exists = radioFavorites.has(station.id);
  if (exists) {
    radioFavorites.delete(station.id);
  } else {
    radioFavorites.set(station.id, station);
  }
  writeStoredRadioFavorites(radioFavorites);
  renderRadioFavorites();
  renderRadioResults();
  renderRadioPlayer();
}

function ensureRadioAudio() {
  if (getAndroidRadioBridge()) {
    return null;
  }
  if (radioAudioElement) {
    return radioAudioElement;
  }
  if (typeof Audio === 'undefined') {
    return null;
  }
  const audio = new Audio();
  audio.preload = 'none';
  audio.addEventListener('playing', () => {
    setRadioPlayerStatus('index.sections.radio.player.status.playing', 'Lecture en cours');
    updateAndroidRadioPlayback(true);
  });
  audio.addEventListener('pause', () => {
    if (audio.currentTime > 0 && !audio.ended) {
      setRadioPlayerStatus('index.sections.radio.player.status.paused', 'Lecture en pause');
      updateAndroidRadioPlayback(false);
    }
  });
  audio.addEventListener('ended', () => {
    setRadioPlayerStatus('index.sections.radio.player.status.stopped', 'Lecture arrêtée');
    stopAndroidRadioPlayback();
  });
  audio.addEventListener('error', () => {
    radioLastError = audio.error;
    setRadioPlayerStatus('index.sections.radio.player.status.error', 'Impossible de lire le flux.');
    stopAndroidRadioPlayback();
  });
  radioAudioElement = audio;
  return radioAudioElement;
}

async function playSelectedRadioStation(options = {}) {
  const station = radioSelectedStation;
  if (!station || !station.url) {
    setRadioPlayerStatus('index.sections.radio.player.status.idle', 'Sélectionnez une station pour lancer la lecture.');
    return;
  }
  const androidBridge = getAndroidRadioBridge();
  if (androidBridge) {
    androidBridge.playStream(station.url, station.name || '');
    setRadioPlayerStatus('index.sections.radio.player.status.playing', 'Lecture en cours');
    updateAndroidRadioPlayback(true);
    return;
  }
  const audio = ensureRadioAudio();
  if (!audio) {
    return;
  }
  const shouldReload = options.forceReload || audio.src !== station.url;
  if (shouldReload) {
    audio.src = station.url;
    audio.load();
  }
  try {
    await audio.play();
    setRadioPlayerStatus('index.sections.radio.player.status.playing', 'Lecture en cours');
    updateAndroidRadioPlayback(true);
  } catch (error) {
    radioLastError = error;
    setRadioPlayerStatus('index.sections.radio.player.status.error', 'Impossible de lire le flux.');
  }
}

function pauseRadioPlayback() {
  const androidBridge = getAndroidRadioBridge();
  if (androidBridge && typeof androidBridge.pauseStream === 'function') {
    androidBridge.pauseStream();
    setRadioPlayerStatus('index.sections.radio.player.status.paused', 'Lecture en pause');
    updateAndroidRadioPlayback(false);
    return;
  }
  const audio = ensureRadioAudio();
  if (!audio) {
    return;
  }
  audio.pause();
  setRadioPlayerStatus('index.sections.radio.player.status.paused', 'Lecture en pause');
  updateAndroidRadioPlayback(false);
}

function stopRadioPlayback() {
  if (radioIsRecording) {
    stopRadioRecording({ silent: true });
  }
  const androidBridge = getAndroidRadioBridge();
  if (androidBridge && typeof androidBridge.stopStream === 'function') {
    androidBridge.stopStream();
    setRadioPlayerStatus('index.sections.radio.player.status.stopped', 'Lecture arrêtée');
    stopAndroidRadioPlayback();
    setRadioNowPlaying(null);
    return;
  }
  const audio = ensureRadioAudio();
  if (!audio) {
    return;
  }
  audio.pause();
  audio.currentTime = 0;
  audio.removeAttribute('src');
  audio.load();
  setRadioPlayerStatus('index.sections.radio.player.status.stopped', 'Lecture arrêtée');
  stopAndroidRadioPlayback();
  setRadioNowPlaying(null);
}

function reloadRadioStream() {
  if (!radioSelectedStation) {
    return;
  }
  const androidBridge = getAndroidRadioBridge();
  if (androidBridge) {
    if (typeof androidBridge.stopStream === 'function') {
      androidBridge.stopStream();
    }
    androidBridge.playStream(radioSelectedStation.url, radioSelectedStation.name || '');
    setRadioPlayerStatus('index.sections.radio.player.status.playing', 'Lecture en cours');
    setRadioNowPlaying(null);
    return;
  }
  setRadioNowPlaying(null);
  playSelectedRadioStation({ forceReload: true });
}

function startRadioRecording() {
  const station = radioSelectedStation;
  if (!station || !station.url) {
    setRadioPlayerStatus('index.sections.radio.player.status.idle', 'Sélectionnez une station pour lancer la lecture.');
    return;
  }
  const androidBridge = getAndroidRadioBridge();
  if (!androidBridge || typeof androidBridge.startRecording !== 'function') {
    setRadioPlayerStatus(
      'index.sections.radio.player.status.recordUnavailable',
      'Enregistrement disponible uniquement sur Android.'
    );
    return;
  }
  androidBridge.playStream(station.url, station.name || '');
  androidBridge.startRecording();
  setRadioRecordingState(true);
  setRadioPlayerStatus('index.sections.radio.player.status.recording', 'Enregistrement en cours…');
}

function stopRadioRecording(options = {}) {
  const androidBridge = getAndroidRadioBridge();
  if (androidBridge && typeof androidBridge.stopRecording === 'function') {
    androidBridge.stopRecording();
  }
  setRadioRecordingState(false);
  if (!options?.silent) {
    setRadioPlayerStatus('index.sections.radio.player.status.recordingStopped', 'Enregistrement arrêté.');
  }
}

function handleAndroidMediaCommand(command) {
  if (typeof command !== 'string' || !command) {
    return;
  }
  const normalized = command.toLowerCase();
  if (normalized === 'play') {
    playSelectedRadioStation();
  } else if (normalized === 'pause') {
    pauseRadioPlayback();
  } else if (normalized === 'stop') {
    stopRadioPlayback();
  }
}

function handleAndroidRadioStateChange(state) {
  if (typeof state !== 'string') {
    return;
  }
  const normalized = state.toLowerCase();
  if (normalized === 'playing') {
    setRadioPlayerStatus('index.sections.radio.player.status.playing', 'Lecture en cours');
    updateAndroidRadioPlayback(true);
  } else if (normalized === 'paused') {
    setRadioPlayerStatus('index.sections.radio.player.status.paused', 'Lecture en pause');
    updateAndroidRadioPlayback(false);
  } else if (normalized === 'stopped') {
    setRadioPlayerStatus('index.sections.radio.player.status.stopped', 'Lecture arrêtée');
    stopAndroidRadioPlayback();
    setRadioNowPlaying(null);
  } else if (normalized === 'error') {
    setRadioPlayerStatus('index.sections.radio.player.status.error', 'Impossible de lire le flux.');
    stopAndroidRadioPlayback();
    setRadioNowPlaying(null);
  }
}

window.onAndroidRadioStateChanged = handleAndroidRadioStateChange;

function handleAndroidRadioMetadataChange(payload) {
  if (!payload) {
    setRadioNowPlaying(null);
    return;
  }
  const info = {
    artist: typeof payload.artist === 'string' ? payload.artist : '',
    title: typeof payload.title === 'string' ? payload.title : ''
  };
  setRadioNowPlaying(info);
}

window.onAndroidRadioMetadataChanged = handleAndroidRadioMetadataChange;

window.onRadioRecordingChanged = isRecording => {
  const wasRecording = radioIsRecording;
  const recording = !!isRecording;
  setRadioRecordingState(recording);
  if (recording) {
    setRadioPlayerStatus('index.sections.radio.player.status.recording', 'Enregistrement en cours…');
  } else if (wasRecording) {
    setRadioPlayerStatus('index.sections.radio.player.status.recordingStopped', 'Enregistrement arrêté.');
  }
};

async function searchRadioStations(params = {}) {
  const query = typeof params.query === 'string' ? params.query.trim() : '';
  const country = typeof params.country === 'string' ? params.country.trim() : '';
  const language = typeof params.language === 'string' ? params.language.trim() : '';
  radioLastError = null;
  setRadioLoading(true);
  setRadioStatus('index.sections.radio.status.loading', 'Recherche de stations en cours…');
  if (radioSearchAbortController) {
    radioSearchAbortController.abort();
  }
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  radioSearchAbortController = controller;
  const timeoutId = controller && RADIO_REQUEST_TIMEOUT_MS > 0
    ? setTimeout(() => controller.abort(), RADIO_REQUEST_TIMEOUT_MS)
    : null;
  const servers = getRadioServers();
  let normalizedStations = null;
  for (let index = 0; index < servers.length; index += 1) {
    const base = servers[index];
    const url = new URL('/json/stations/search', base);
    if (query) {
      url.searchParams.set('name', query);
    }
    if (country) {
      url.searchParams.set('country', country);
    }
    if (language) {
      url.searchParams.set('language', language);
    }
    if (RADIO_HIDE_BROKEN) {
      url.searchParams.set('hidebroken', 'true');
    }
    url.searchParams.set('limit', String(RADIO_MAX_RESULTS));
    try {
      const response = await fetch(url.toString(), controller ? { signal: controller.signal } : undefined);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = await response.json();
      normalizedStations = Array.isArray(payload)
        ? payload.map(normalizeRadioStation).filter(Boolean)
        : [];
      break;
    } catch (error) {
      radioLastError = error;
      if (error?.name === 'AbortError') {
        break;
      }
    }
  }
  if (timeoutId) {
    clearTimeout(timeoutId);
  }
  if (radioSearchAbortController === controller) {
    radioSearchAbortController = null;
  }
  setRadioLoading(false);
  if (controller?.signal?.aborted) {
    setRadioStatus('index.sections.radio.status.idle', 'Entrez une requête pour démarrer une recherche.');
    return;
  }
  if (!normalizedStations) {
    setRadioStatus('index.sections.radio.status.error', 'Impossible de récupérer les stations.');
    radioStations = [];
    renderRadioResults();
    return;
  }
  radioStations = normalizedStations.slice(0, RADIO_MAX_RESULTS);
  renderRadioResults();
  if (radioStations.length) {
    setRadioStatus('index.sections.radio.status.loaded', `${radioStations.length} stations found.`, { count: radioStations.length });
    const stillAvailable = radioSelectedStation && radioStations.find(station => station.id === radioSelectedStation.id);
    if (!stillAvailable) {
      selectRadioStation(radioStations[0]);
    }
  } else {
    setRadioStatus('index.sections.radio.status.empty', 'Aucune station trouvée.');
  }
}

function initRadioModule() {
  radioFavorites = readStoredRadioFavorites();
  if (!(radioFavorites instanceof Map)) {
    radioFavorites = new Map();
  }
  setRadioStatus(radioStatusState.key, radioStatusState.fallback, radioStatusState.params);
  setRadioPlayerStatus(radioPlayerStatusState.key, radioPlayerStatusState.fallback, radioPlayerStatusState.params);
  renderRadioFavorites();
  renderRadioResults();
  renderRadioPlayer();
  if (elements.radioStationLogo) {
    elements.radioStationLogo.addEventListener('error', () => {
      elements.radioStationLogo.hidden = true;
    });
  }
  ensureRadioFiltersLoaded();
}

function subscribeRadioLanguageUpdates() {
  const handler = () => {
    setRadioStatus(radioStatusState.key, radioStatusState.fallback, radioStatusState.params);
    setRadioPlayerStatus(radioPlayerStatusState.key, radioPlayerStatusState.fallback, radioPlayerStatusState.params);
    renderRadioFavorites();
    renderRadioResults();
    renderRadioPlayer();
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

function getAndroidNotesBridge() {
  const bridge = getAndroidBridge();
  if (!bridge) {
    return null;
  }
  const supportsNotes = typeof bridge.listUserNotes === 'function'
    && typeof bridge.readUserNote === 'function'
    && typeof bridge.saveUserNote === 'function'
    && typeof bridge.deleteUserNote === 'function';
  return supportsNotes ? bridge : null;
}

function isNotesFeatureSupported() {
  return !!getAndroidNotesBridge();
}

function normalizeNoteFormat(format) {
  if (typeof format !== 'string') {
    return 'txt';
  }
  const normalized = format.trim().toLowerCase();
  return NOTE_SUPPORTED_FORMATS.includes(normalized) ? normalized : 'txt';
}

function sanitizeNoteName(raw) {
  if (typeof raw !== 'string') {
    return '';
  }
  const normalized = raw
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .trim()
    .replace(/^[-]+|[-]+$/g, '');
  return normalized;
}

function buildNoteFileName(baseName, format) {
  const safeBase = sanitizeNoteName(baseName);
  const safeFormat = normalizeNoteFormat(format);
  const fallback = `note-${Date.now()}`;
  return `${safeBase || fallback}.${safeFormat}`;
}

function extractNoteBaseName(fileName) {
  if (typeof fileName !== 'string') {
    return '';
  }
  const normalized = fileName.trim();
  const lastDot = normalized.lastIndexOf('.');
  if (lastDot > 0) {
    return normalized.substring(0, lastDot);
  }
  return normalized;
}

function getNoteMimeType(format) {
  const normalized = normalizeNoteFormat(format);
  return normalized === 'md' ? 'text/markdown' : 'text/plain';
}

function normalizeNoteEntry(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const name = typeof raw.name === 'string' && raw.name.trim() ? raw.name.trim() : '';
  if (!name) {
    return null;
  }
  const mimeType = typeof raw.mimeType === 'string' ? raw.mimeType : '';
  const uri = typeof raw.uri === 'string' ? raw.uri : '';
  const updatedAtValue = Number(raw.updatedAt);
  const updatedAt = Number.isFinite(updatedAtValue) ? updatedAtValue : null;
  return { name, mimeType, uri, updatedAt };
}

function formatNoteDate(timestamp) {
  if (!Number.isFinite(timestamp)) {
    return '';
  }
  const api = getI18nApi();
  const locale = api && typeof api.getCurrentLanguage === 'function'
    ? api.getCurrentLanguage()
    : (typeof navigator !== 'undefined' && navigator.language ? navigator.language : undefined);
  try {
    return new Date(timestamp).toLocaleString(locale || undefined);
  } catch (error) {
    return new Date(timestamp).toLocaleString();
  }
}

function setNotesStatus(key, fallback, params) {
  notesStatusState = { key, fallback, params: params || null };
  if (!elements?.notesStatus) {
    return;
  }
  const label = translateOrDefault(key, fallback, params);
  elements.notesStatus.textContent = label;
  if (key) {
    elements.notesStatus.setAttribute('data-i18n', key);
  }
}

function updateNotesControlsAvailability() {
  const supported = isNotesFeatureSupported();
  const disableActions = notesBusy || !supported;
  const actionTargets = [
    elements?.notesNewButton,
    elements?.notesRefreshButton,
    elements?.notesSaveButton,
    elements?.notesCancelButton
  ];
  actionTargets.forEach(target => {
    if (target) {
      target.disabled = disableActions;
    }
  });
  if (elements?.notesDeleteButton) {
    const hasSelection = !!elements?.notesPicker?.value;
    elements.notesDeleteButton.disabled = disableActions || !hasSelection;
  }
  const fields = [
    elements?.notesTitleInput,
    elements?.notesFormatSelect,
    elements?.notesFontSelect,
    elements?.notesFontSize,
    elements?.notesTextColor,
    elements?.notesBackgroundColor,
    elements?.notesContent
  ];
  fields.forEach(field => {
    if (field) {
      field.disabled = !supported;
    }
  });
  if (elements?.notesPicker) {
    const hasItems = Array.isArray(notesItems) && notesItems.length > 0;
    elements.notesPicker.disabled = notesBusy || !supported || !hasItems;
  }
}

function setNotesBusyState(isBusy) {
  notesBusy = !!isBusy;
  if (elements?.notesStatus) {
    elements.notesStatus.setAttribute('aria-busy', notesBusy ? 'true' : 'false');
  }
  updateNotesControlsAvailability();
}

function updateNotesFullscreenUi() {
  const page = elements?.notesPage;
  const root = elements?.notesRoot;
  if (page) {
    page.classList.toggle('page--notes-fullscreen', notesIsFullscreen);
  }
  if (root) {
    root.classList.toggle('notes--fullscreen', notesIsFullscreen);
  }
  const body = typeof document !== 'undefined' ? document.body : null;
  if (body) {
    body.classList.toggle('notes-fullscreen-active', notesIsFullscreen);
  }
  if (elements?.notesFullscreenButton) {
    elements.notesFullscreenButton.setAttribute('aria-pressed', notesIsFullscreen ? 'true' : 'false');
  }
  if (elements?.notesExitFullscreenButton) {
    elements.notesExitFullscreenButton.hidden = !notesIsFullscreen;
  }
}

function setNotesFullscreen(active) {
  const next = !!active;
  if (next === notesIsFullscreen) {
    return;
  }
  notesIsFullscreen = next;
  updateNotesFullscreenUi();
  if (notesIsFullscreen && elements?.notesContent) {
    elements.notesContent.focus();
  }
}

function toggleNotesFullscreen() {
  setNotesFullscreen(!notesIsFullscreen);
}

function normalizeNotesStyle(raw) {
  const base = { ...NOTES_DEFAULT_STYLE };
  if (raw && typeof raw === 'object') {
    if (typeof raw.font === 'string' && raw.font.trim()) {
      base.font = raw.font.trim();
    }
    const sizeValue = Number(raw.fontSize);
    if (Number.isFinite(sizeValue) && sizeValue >= 10 && sizeValue <= 48) {
      base.fontSize = sizeValue;
    }
    if (typeof raw.textColor === 'string' && raw.textColor.trim()) {
      base.textColor = raw.textColor.trim();
    }
    if (typeof raw.backgroundColor === 'string' && raw.backgroundColor.trim()) {
      base.backgroundColor = raw.backgroundColor.trim();
    }
  }
  return base;
}

function readStoredNotesStyle() {
  try {
    const raw = globalThis.localStorage?.getItem(NOTES_STYLE_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      return normalizeNotesStyle(parsed);
    }
  } catch (error) {
    console.warn('Unable to read notes editor style', error);
  }
  return { ...NOTES_DEFAULT_STYLE };
}

function writeStoredNotesStyle(style) {
  try {
    globalThis.localStorage?.setItem(NOTES_STYLE_STORAGE_KEY, JSON.stringify(style));
  } catch (error) {
    console.warn('Unable to persist notes editor style', error);
  }
}

function resolveNotesFontFamily(fontKey) {
  const normalized = typeof fontKey === 'string' ? fontKey.trim().toLowerCase() : 'system';
  switch (normalized) {
    case 'serif':
      return 'Georgia, Cambria, "Times New Roman", serif';
    case 'sans':
      return 'Inter, "Segoe UI", system-ui, sans-serif';
    case 'system':
      return 'system-ui, -apple-system, "Segoe UI", sans-serif';
    default:
      return '"Courier New", Courier, monospace';
  }
}

function applyNotesEditorStyle(style) {
  const normalized = normalizeNotesStyle(style);
  notesStylePreference = normalized;
  if (elements?.notesFontSelect && normalized.font) {
    elements.notesFontSelect.value = normalized.font;
  }
  if (elements?.notesFontSize && Number.isFinite(normalized.fontSize)) {
    elements.notesFontSize.value = normalized.fontSize;
  }
  if (elements?.notesTextColor && normalized.textColor) {
    elements.notesTextColor.value = normalized.textColor;
  }
  if (elements?.notesBackgroundColor && normalized.backgroundColor) {
    elements.notesBackgroundColor.value = normalized.backgroundColor;
  }
  if (elements?.notesContent) {
    elements.notesContent.style.color = normalized.textColor;
    elements.notesContent.style.backgroundColor = normalized.backgroundColor;
    elements.notesContent.style.fontSize = `${normalized.fontSize}px`;
    elements.notesContent.style.fontFamily = resolveNotesFontFamily(normalized.font);
  }
  writeStoredNotesStyle(normalized);
}

function getNotesStyleFromInputs() {
  return normalizeNotesStyle({
    font: elements?.notesFontSelect?.value,
    fontSize: Number(elements?.notesFontSize?.value),
    textColor: elements?.notesTextColor?.value,
    backgroundColor: elements?.notesBackgroundColor?.value
  });
}

function handleNotesStyleChange() {
  applyNotesEditorStyle(getNotesStyleFromInputs());
}

function renderNotesList() {
  if (!elements?.notesPicker) {
    return;
  }
  const hasItems = Array.isArray(notesItems) && notesItems.length > 0;
  const previousSelection = elements.notesPicker.value;
  elements.notesPicker.innerHTML = '';

  const placeholder = document.createElement('option');
  placeholder.value = '';
  placeholder.textContent = translateOrDefault(
    'index.sections.notes.selector.placeholder',
    'Sélectionnez une note'
  );
  elements.notesPicker.appendChild(placeholder);
  elements.notesPicker.disabled = !hasItems;

  if (!hasItems) {
    updateNotesControlsAvailability();
    return;
  }

  notesItems.forEach(note => {
    const option = document.createElement('option');
    option.value = note.name;
    option.textContent = note.name;
    const extension = note.name.includes('.') ? note.name.split('.').pop() : 'txt';
    const dateLabel = note.updatedAt ? formatNoteDate(note.updatedAt) : '';
    if (dateLabel) {
      option.title = translateOrDefault(
        'index.sections.notes.list.meta',
        `${extension} · Mis à jour ${dateLabel}`,
        { extension, date: dateLabel }
      );
    }
    elements.notesPicker.appendChild(option);
  });

  const preferredSelection = notesEditingNote?.name || previousSelection;
  if (preferredSelection && notesItems.some(item => item?.name === preferredSelection)) {
    elements.notesPicker.value = preferredSelection;
  }

  updateNotesControlsAvailability();
}

function startNewNote() {
  if (elements?.notesTitleInput) {
    elements.notesTitleInput.value = '';
  }
  if (elements?.notesFormatSelect) {
    elements.notesFormatSelect.value = 'txt';
  }
  if (elements?.notesContent) {
    elements.notesContent.value = '';
  }
  if (elements?.notesPicker) {
    elements.notesPicker.value = '';
  }
  notesEditingNote = null;
  setNotesStatus('index.sections.notes.status.new', 'Nouvelle note prête à être écrite.');
  updateNotesControlsAvailability();
}

function refreshNotesList(options = {}) {
  const supported = isNotesFeatureSupported();
  if (!supported) {
    setNotesStatus(
      'index.sections.notes.status.unsupported',
      'Disponible uniquement sur l’application Android.'
    );
    updateNotesControlsAvailability();
    return;
  }
  const silent = options && options.silent;
  setNotesBusyState(true);
  setNotesStatus('index.sections.notes.status.loading', 'Chargement de vos notes…');
  try {
    const bridge = getAndroidNotesBridge();
    if (!bridge) {
      throw new Error('Missing Android notes bridge');
    }
    const response = bridge.listUserNotes();
    const parsed = response ? JSON.parse(response) : [];
    const normalized = Array.isArray(parsed)
      ? parsed.map(normalizeNoteEntry).filter(Boolean)
      : [];
    normalized.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    notesItems = normalized;
    renderNotesList();
    if (!silent) {
      setNotesStatus(
        'index.sections.notes.status.loaded',
        'Notes mises à jour.',
        { count: notesItems.length }
      );
    }
  } catch (error) {
    console.error('Unable to load notes list', error);
    setNotesStatus('index.sections.notes.status.error', 'Impossible de charger vos notes.');
  } finally {
    setNotesBusyState(false);
  }
}

function openNote(note) {
  const supported = isNotesFeatureSupported();
  if (!supported) {
    setNotesStatus(
      'index.sections.notes.status.unsupported',
      'Disponible uniquement sur l’application Android.'
    );
    return;
  }
  const target = note && typeof note === 'object' ? note : null;
  if (!target) {
    return;
  }
  setNotesBusyState(true);
  setNotesStatus(
    'index.sections.notes.status.loadingNote',
    'Ouverture de la note…',
    { name: target.name }
  );
  try {
    const bridge = getAndroidNotesBridge();
    if (!bridge) {
      throw new Error('Missing Android bridge');
    }
    const content = bridge.readUserNote(target.name) || '';
    if (elements?.notesTitleInput) {
      elements.notesTitleInput.value = extractNoteBaseName(target.name);
    }
    const format = target.name.includes('.') ? target.name.split('.').pop() : 'txt';
    if (elements?.notesFormatSelect) {
      elements.notesFormatSelect.value = normalizeNoteFormat(format);
    }
    if (elements?.notesContent) {
      elements.notesContent.value = content;
    }
    if (elements?.notesPicker) {
      elements.notesPicker.value = target.name;
    }
    notesEditingNote = { ...target };
    setNotesStatus(
      'index.sections.notes.status.loadedNote',
      'Note chargée.',
      { name: target.name }
    );
  } catch (error) {
    console.error('Unable to open note', error);
    setNotesStatus('index.sections.notes.status.error', 'Impossible de charger vos notes.');
  } finally {
    setNotesBusyState(false);
  }
}

function saveActiveNote() {
  const supported = isNotesFeatureSupported();
  if (!supported) {
    setNotesStatus(
      'index.sections.notes.status.unsupported',
      'Disponible uniquement sur l’application Android.'
    );
    return;
  }
  const baseName = elements?.notesTitleInput?.value || '';
  const format = elements?.notesFormatSelect?.value || 'txt';
  const fileName = buildNoteFileName(baseName, format);
  const mimeType = getNoteMimeType(format);
  const content = elements?.notesContent?.value || '';
  setNotesBusyState(true);
  setNotesStatus(
    'index.sections.notes.status.saving',
    'Enregistrement de la note…',
    { name: fileName }
  );
  try {
    const bridge = getAndroidNotesBridge();
    if (!bridge) {
      throw new Error('Missing Android bridge');
    }
    const result = bridge.saveUserNote(fileName, content, mimeType);
    const uri = typeof result === 'string' && result.trim() ? result.trim() : (notesEditingNote?.uri || '');
    notesEditingNote = { name: fileName, mimeType, uri, updatedAt: Date.now() };
    refreshNotesList({ silent: true });
    setNotesStatus(
      'index.sections.notes.status.saved',
      'Note enregistrée.',
      { name: fileName }
    );
  } catch (error) {
    console.error('Unable to save note', error);
    setNotesStatus('index.sections.notes.status.error', 'Impossible d’enregistrer la note.');
  } finally {
    setNotesBusyState(false);
  }
}

function handleDeleteNote(note) {
  const target = note && typeof note === 'object' ? note : null;
  if (!target) {
    return;
  }
  const message = translateOrDefault(
    'index.sections.notes.actions.deleteConfirm',
    'Supprimer la note {{name}} ?',
    { name: target.name }
  );
  const confirmed = typeof window !== 'undefined'
    && typeof window.confirm === 'function'
    ? window.confirm(message)
    : true;
  if (!confirmed) {
    return;
  }
  const bridge = getAndroidNotesBridge();
  if (!bridge) {
    setNotesStatus(
      'index.sections.notes.status.unsupported',
      'Disponible uniquement sur l’application Android.'
    );
    return;
  }
  setNotesBusyState(true);
  try {
    const result = bridge.deleteUserNote(target.name);
    const success = result === true || result === undefined;
    if (success) {
      notesItems = notesItems.filter(item => item?.name !== target.name);
      renderNotesList();
      if (notesEditingNote && notesEditingNote.name === target.name) {
        startNewNote();
      }
      setNotesStatus(
        'index.sections.notes.status.deleted',
        'Note supprimée.',
        { name: target.name }
      );
    } else {
      setNotesStatus('index.sections.notes.status.error', 'Impossible de supprimer la note.');
    }
  } catch (error) {
    console.error('Unable to delete note', error);
    setNotesStatus('index.sections.notes.status.error', 'Impossible de supprimer la note.');
  } finally {
    setNotesBusyState(false);
  }
}

function subscribeNotesLanguageUpdates() {
  const handler = () => {
    setNotesStatus(notesStatusState.key, notesStatusState.fallback, notesStatusState.params);
    renderNotesList();
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

function initNotesModule() {
  notesStylePreference = readStoredNotesStyle();
  applyNotesEditorStyle(notesStylePreference);
  updateNotesFullscreenUi();
  updateNotesControlsAvailability();
  startNewNote();
  if (isNotesFeatureSupported()) {
    refreshNotesList({ silent: true });
  } else {
    setNotesStatus(
      'index.sections.notes.status.unsupported',
      'Disponible uniquement sur l’application Android.'
    );
  }
}

if (typeof globalThis !== 'undefined') {
  globalThis.onAndroidMediaCommand = handleAndroidMediaCommand;
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
