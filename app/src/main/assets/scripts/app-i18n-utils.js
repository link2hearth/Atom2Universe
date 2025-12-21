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
    performanceModeState.autoAccumulatedMs = 0;
    return;
  }
  if (gameState.perSecond instanceof LayeredNumber && !gameState.perSecond.isZero()) {
    performanceModeState.autoAccumulatedMs += deltaSeconds * 1000;
  }
}

function flushPendingAutoGain(now, options = {}) {
  const config = Object.assign({ force: false }, options);
  const interval = Number(performanceModeState.settings?.apsFlushIntervalMs) || 0;
  if (interval <= 0) {
    performanceModeState.autoAccumulatedMs = 0;
    performanceModeState.lastAutoFlush = now;
    return;
  }
  if (!config.force) {
    const accumulated = performanceModeState.autoAccumulatedMs;
    if (Number.isFinite(accumulated) && accumulated < interval) {
      return;
    }
  }
  if (gameState.perSecond instanceof LayeredNumber && !gameState.perSecond.isZero()) {
    const accumulatedSeconds = Math.max(0, performanceModeState.autoAccumulatedMs) / 1000;
    if (accumulatedSeconds > 0) {
      const gain = gameState.perSecond.multiplyNumber(accumulatedSeconds);
      if (gain instanceof LayeredNumber && !gain.isZero()) {
        gainAtoms(gain, 'aps');
      }
    }
  }
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
