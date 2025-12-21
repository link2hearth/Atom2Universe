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
const CRYPTO_WIDGET_STORAGE_KEY = 'atom2univers.options.cryptoWidgetEnabled';

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
