let pageHiddenAt = null;
let overlayFadeFallbackTimeout = null;
let startupOverlayFailsafeTimeout = null;
let startupOverlayGlobalFallbackTimeout = null;
let visibilityChangeListenerAttached = false;
let appStartAttempted = false;
let appStartCompleted = false;
let headerTurtleAnimationTimer = null;

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

function getStartupOverlayStatusElement() {
  if (elements && elements.startupOverlayStatus) {
    const statusCandidate = elements.startupOverlayStatus;
    if (typeof HTMLElement === 'undefined' || statusCandidate instanceof HTMLElement) {
      return statusCandidate;
    }
  }

  if (typeof document === 'undefined') {
    return null;
  }

  const status = document.getElementById('startupOverlayStatus');
  if (status && elements && typeof elements === 'object') {
    elements.startupOverlayStatus = status;
  }

  return status || null;
}

function setStartupOverlayStatus(key, fallback) {
  const statusElement = getStartupOverlayStatusElement();
  if (!statusElement) {
    return;
  }
  const normalizedKey = typeof key === 'string' && key.trim()
    ? key.trim()
    : statusElement.dataset?.i18n || 'index.startup.status.default';
  statusElement.dataset.i18n = normalizedKey;
  statusElement.textContent = translateOrDefault(
    normalizedKey,
    fallback || statusElement.textContent || ''
  );
}

const ANDROID_MIDI_STARTUP_FLAG = 'atom2universAndroidMidiStartupRequested';
const ANDROID_SOUND_FONT_STARTUP_FLAG = 'atom2universAndroidSoundFontStartupRequested';
const ANDROID_BACKGROUND_STARTUP_FLAG = 'atom2universAndroidBackgroundStartupRequested';

function isGlobalFlagSet(flagKey) {
  return typeof globalThis !== 'undefined' && globalThis && globalThis[flagKey] === true;
}

function setGlobalFlag(flagKey) {
  if (typeof globalThis === 'undefined' || !globalThis) {
    return;
  }
  globalThis[flagKey] = true;
}

function prefetchAndroidManagedFiles() {
  if (typeof window === 'undefined') {
    return;
  }

  const bridge = window.AndroidBridge;
  const hasBridge = bridge && (typeof bridge === 'object' || typeof bridge === 'function');

  if (!hasBridge) {
    return;
  }

  const canLoadBackgrounds = typeof bridge.loadBackgroundImageBank === 'function'
    && !isGlobalFlagSet(ANDROID_BACKGROUND_STARTUP_FLAG);
  const canLoadMidiLibrary = typeof bridge.loadMidiFolder === 'function'
    && !isGlobalFlagSet(ANDROID_MIDI_STARTUP_FLAG);
  const canLoadSoundFont = typeof bridge.loadCachedSoundFont === 'function'
    && !isGlobalFlagSet(ANDROID_SOUND_FONT_STARTUP_FLAG);

  if (!canLoadBackgrounds && !canLoadMidiLibrary && !canLoadSoundFont) {
    return;
  }

  setStartupOverlayStatus(
    'index.startup.status.android',
    'Chargement des fichiers Android…'
  );

  if (canLoadBackgrounds) {
    setGlobalFlag(ANDROID_BACKGROUND_STARTUP_FLAG);
    try {
      requestNativeBackgroundBank();
    } catch (error) {
      console.warn('Unable to request Android background bank during startup', error);
    }
  }

  if (canLoadMidiLibrary) {
    setGlobalFlag(ANDROID_MIDI_STARTUP_FLAG);
    try {
      bridge.loadMidiFolder();
    } catch (error) {
      console.warn('Unable to preload Android MIDI library', error);
    }
  }

  if (canLoadSoundFont) {
    setGlobalFlag(ANDROID_SOUND_FONT_STARTUP_FLAG);
    try {
      bridge.loadCachedSoundFont();
    } catch (error) {
      console.warn('Unable to preload Android SoundFont', error);
    }
  }
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
  VIDEOS: Object.freeze({
    toggle: toggleCollectionVideosFeatureAvailability,
    enabledKey: 'collectionVideosEnabled',
    enabledFallback: 'Collection videos enabled. Changes applied.',
    disabledKey: 'collectionVideosDisabled',
    disabledFallback: 'Collection videos disabled. Changes applied.'
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
  } else if (normalizedKeyword === 'DEVKIT') {
    updateDevkitButtonVisibility();
  } else {
    scheduleConfigReload();
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
    startupOverlayStatus: getStartupOverlayStatusElement(),
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
    navImagesButton: document.querySelector('.nav-button[data-target="images"]'),
    navNewsButton: document.querySelector('.nav-button[data-target="news"]'),
    navRadioButton: document.querySelector('.nav-button[data-target="radio"]'),
    navCollectionButton: document.querySelector('.nav-button[data-target="collection"]'),
    navMidiButton: document.querySelector('.nav-button[data-target="midi"]'),
    navBigBangButton: document.getElementById('navBigBangButton'),
    radioPage: document.getElementById('radio'),
    midiPage: document.getElementById('midi'),
    musicTabs: document.querySelectorAll('.music-tab'),
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
    headerNextButton: document.getElementById('headerNextTrack'),
    nowPlayingBar: document.getElementById('nowPlayingBar'),
    nowPlayingTrack: document.getElementById('nowPlayingTrack'),
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
  favoriteBackground: document.getElementById('favoriteBackground'),
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
  gachaPeriodicLink: document.getElementById('gachaPeriodicLink'),
  gachaPeriodicButton: document.getElementById('gachaPeriodicButton'),
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
  textScaleSelect: document.getElementById('textScaleSelect'),
  textScaleResetButton: document.getElementById('textScaleResetButton'),
  themeSelect: document.getElementById('themeSelect'),
  textFontSelect: document.getElementById('textFontSelect'),
  digitFontSelect: document.getElementById('digitFontSelect'),
  atomDecimalSelect: document.getElementById('atomDecimalSelect'),
  optionsPreferencesCard: document.getElementById('optionsPreferencesCard'),
  optionsPreferencesContent: document.getElementById('options-preferences-content'),
  optionsPreferencesToggle: document.getElementById('optionsPreferencesToggle'),
  optionsBackgroundCard: document.getElementById('optionsBackgroundCard'),
  optionsBackgroundContent: document.getElementById('options-background-content'),
  optionsBackgroundToggle: document.getElementById('optionsBackgroundToggle'),
  optionsBackupsCard: document.getElementById('optionsBackupsCard'),
  optionsBackupsContent: document.getElementById('options-backups-content'),
  optionsBackupsToggle: document.getElementById('optionsBackupsToggle'),
  optionsCustomPagesCard: document.getElementById('optionsCustomPagesCard'),
  optionsCustomPagesContent: document.getElementById('options-custom-pages-content'),
  optionsCustomPagesToggle: document.getElementById('optionsCustomPagesToggle'),
  optionsNotesCard: document.getElementById('optionsNotesCard'),
  optionsNotesContent: document.getElementById('options-notes-content'),
  optionsNotesToggle: document.getElementById('optionsNotesToggle'),
  musicTrackSelect: document.getElementById('musicTrackSelect'),
  musicTrackStatus: document.getElementById('musicTrackStatus'),
  musicVolumeSlider: document.getElementById('musicVolumeSlider'),
    optionsWelcomeTitle: document.getElementById('optionsWelcomeTitle'),
    optionsWelcomeIntro: document.getElementById('optionsWelcomeIntro'),
    backgroundLibraryButton: document.getElementById('backgroundLibraryButton'),
    backgroundLibraryStatus: document.getElementById('backgroundLibraryStatus'),
    backgroundDurationSelect: document.getElementById('backgroundDurationSelect'),
    backgroundToggleButton: document.getElementById('backgroundToggleButton'),
    backupSaveButton: document.getElementById('backupSaveButton'),
    backupLoadButton: document.getElementById('backupLoadButton'),
    backupStatus: document.getElementById('backupStatus'),
    midiPage: document.getElementById('midi'),
    midiModuleCard: document.querySelector('.midi-page__module'),
    midiKeyboardArea: document.getElementById('midiKeyboardArea'),
  atomIconToggleCard: document.getElementById('atomIconToggleCard'),
  atomIconToggle: document.getElementById('atomIconToggle'),
  atomIconToggleStatus: document.getElementById('atomIconToggleStatus'),
  clickSoundToggleCard: document.getElementById('clickSoundToggleCard'),
  clickSoundToggle: document.getElementById('clickSoundToggle'),
  clickSoundToggleStatus: document.getElementById('clickSoundToggleStatus'),
  atomAnimationToggleCard: document.getElementById('atomAnimationToggleCard'),
  atomAnimationToggle: document.getElementById('atomAnimationToggle'),
  atomAnimationToggleStatus: document.getElementById('atomAnimationToggleStatus'),
  critAtomToggleCard: document.getElementById('critAtomToggleCard'),
  critAtomToggle: document.getElementById('critAtomToggle'),
  critAtomToggleStatus: document.getElementById('critAtomToggleStatus'),
  androidStatusBarToggleCard: document.getElementById('androidStatusBarToggleCard'),
  androidStatusBarToggle: document.getElementById('androidStatusBarToggle'),
  androidStatusBarToggleStatus: document.getElementById('androidStatusBarToggleStatus'),
  screenWakeLockToggleCard: document.getElementById('screenWakeLockToggleCard'),
  screenWakeLockToggle: document.getElementById('screenWakeLockToggle'),
  screenWakeLockToggleStatus: document.getElementById('screenWakeLockToggleStatus'),
  frenzyAutoCollectCard: document.getElementById('frenzyAutoCollectCard'),
  frenzyAutoCollectToggle: document.getElementById('frenzyAutoCollectToggle'),
  frenzyAutoCollectStatus: document.getElementById('frenzyAutoCollectStatus'),
  ticketStarAutoCollectCard: document.getElementById('ticketStarAutoCollectCard'),
  ticketStarAutoCollectToggle: document.getElementById('ticketStarAutoCollectToggle'),
  ticketStarAutoCollectStatus: document.getElementById('ticketStarAutoCollectStatus'),
  ticketStarSpriteCard: document.getElementById('ticketStarSpriteCard'),
  ticketStarSpriteToggle: document.getElementById('ticketStarSpriteToggle'),
  ticketStarSpriteStatus: document.getElementById('ticketStarSpriteStatus'),
  cryptoWidgetToggleCard: document.getElementById('cryptoWidgetToggleCard'),
  cryptoWidgetToggle: document.getElementById('cryptoWidgetToggle'),
  cryptoWidgetToggleStatus: document.getElementById('cryptoWidgetToggleStatus'),
  imagesCollectionButton: document.getElementById('imagesCollectionButton'),
  imagesStatus: document.getElementById('imagesStatus'),
  imagesRefreshButton: document.getElementById('imagesRefreshButton'),
  imagesOpenButton: document.getElementById('imagesOpenButton'),
  imagesDownloadButton: document.getElementById('imagesDownloadButton'),
  imagesSourcesList: document.getElementById('imagesSourcesList'),
  imagesSourcesReset: document.getElementById('imagesSourcesReset'),
  imagesGallery: document.getElementById('imagesGallery'),
  imagesEmptyState: document.getElementById('imagesEmptyState'),
  imagesPrevButton: document.getElementById('imagesPrevButton'),
  imagesNextButton: document.getElementById('imagesNextButton'),
  imagesActiveImage: document.getElementById('imagesActiveImage'),
  imagesHideButton: document.getElementById('imagesHideButton'),
  imagesActiveTitle: document.getElementById('imagesActiveTitle'),
  imagesActiveSource: document.getElementById('imagesActiveSource'),
  imagesLightbox: document.getElementById('imagesLightbox'),
  imagesLightboxImage: document.getElementById('imagesLightboxImage'),
  imagesLightboxCaption: document.getElementById('imagesLightboxCaption'),
  imagesLightboxClose: document.getElementById('imagesLightboxClose'),
  newsToggleCard: document.getElementById('newsToggleCard'),
  newsToggle: document.getElementById('newsToggle'),
  newsToggleStatus: document.getElementById('newsToggleStatus'),
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
  infoGlobalAtoms: document.getElementById('infoGlobalAtoms'),
  infoGlobalClicks: document.getElementById('infoGlobalClicks'),
  infoGlobalApcAtoms: document.getElementById('infoGlobalApcAtoms'),
  infoGlobalApsAtoms: document.getElementById('infoGlobalApsAtoms'),
  infoGlobalOfflineAtoms: document.getElementById('infoGlobalOfflineAtoms'),
  infoGlobalDuration: document.getElementById('infoGlobalDuration'),
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
  infoJumpingCatScoreValue: document.getElementById('infoJumpingCatScoreValue'),
  infoJumpingCatTimeValue: document.getElementById('infoJumpingCatTimeValue'),
  infoReflexBestScoreEasyValue: document.getElementById('infoReflexBestScoreEasyValue'),
  infoReflexBestScoreHardValue: document.getElementById('infoReflexBestScoreHardValue'),
  infoMotocrossDistanceValue: document.getElementById('infoMotocrossDistanceValue'),
  infoMotocrossSpeedValue: document.getElementById('infoMotocrossSpeedValue'),
  infoFrenzyHighscoreSingle: document.getElementById('infoFrenzyHighscoreSingle'),
  infoFrenzyHighscoreSingleEnhanced: document.getElementById('infoFrenzyHighscoreSingleEnhanced'),
  infoFrenzyHighscoreMulti: document.getElementById('infoFrenzyHighscoreMulti'),
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
  collectionBonus1ImagesCard: document.getElementById('collectionBonus1ImagesCard'),
  collectionBonus1ImagesContent: document.getElementById('collection-bonus1-images-content'),
  collectionBonus1ImagesToggle: document.getElementById('collectionBonus1ImagesToggle'),
  collectionBonus1ImagesList: document.getElementById('collectionBonus1ImagesList'),
  collectionBonus1ImagesEmpty: document.getElementById('collectionBonus1ImagesEmpty'),
  collectionBonus2ImagesCard: document.getElementById('collectionBonus2ImagesCard'),
  collectionBonus2ImagesContent: document.getElementById('collection-bonus2-images-content'),
  collectionBonus2ImagesToggle: document.getElementById('collectionBonus2ImagesToggle'),
  collectionBonus2ImagesList: document.getElementById('collectionBonus2ImagesList'),
  collectionBonus2ImagesEmpty: document.getElementById('collectionBonus2ImagesEmpty'),
  collectionBonus3ImagesCard: document.getElementById('collectionBonus3ImagesCard'),
  collectionBonus3ImagesContent: document.getElementById('collection-bonus3-images-content'),
  collectionBonus3ImagesToggle: document.getElementById('collectionBonus3ImagesToggle'),
  collectionBonus3ImagesList: document.getElementById('collectionBonus3ImagesList'),
  collectionBonus3ImagesEmpty: document.getElementById('collectionBonus3ImagesEmpty'),
  collectionDownloadsCard: document.getElementById('collectionDownloadsCard'),
  collectionDownloadsContent: document.getElementById('collection-downloads-content'),
  collectionDownloadsToggle: document.getElementById('collectionDownloadsToggle'),
  collectionDownloadsGallery: document.getElementById('collectionDownloadsGallery'),
  collectionDownloadsEmpty: document.getElementById('collectionDownloadsEmpty'),
  collectionDownloadsRefresh: document.getElementById('collectionDownloadsRefresh'),
  collectionDownloadsLightbox: document.getElementById('collectionDownloadsLightbox'),
  collectionDownloadsLightboxImage: document.getElementById('collectionDownloadsLightboxImage'),
  collectionDownloadsLightboxCaption: document.getElementById('collectionDownloadsLightboxCaption'),
  collectionDownloadsLightboxClose: document.getElementById('collectionDownloadsLightboxClose'),
  collectionDownloadsPrev: document.getElementById('collectionDownloadsPrev'),
  collectionDownloadsNext: document.getElementById('collectionDownloadsNext'),
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
  devkitToggleGacha: document.getElementById('devkitToggleGacha'),
  newsFeedLabel: document.getElementById('newsFeedLabel'),
  newsStatus: document.getElementById('newsStatus'),
  newsList: document.getElementById('newsList'),
  newsEmptyState: document.getElementById('newsEmptyState'),
  newsDisabledNotice: document.getElementById('newsDisabledNotice'),
  newsSearchInput: document.getElementById('newsSearchInput'),
  newsSearchButton: document.getElementById('newsSearchButton'),
  newsClearSearchButton: document.getElementById('newsClearSearchButton'),
  newsRefreshButton: document.getElementById('newsRefreshButton'),
  newsRestoreHiddenButton: document.getElementById('newsRestoreHiddenButton'),
  newsBannedWordsInput: document.getElementById('newsBannedWordsInput'),
  newsBannedWordsSave: document.getElementById('newsBannedWordsSave'),
  newsSourcesList: document.getElementById('newsSourcesList'),
  newsSourcesEmpty: document.getElementById('newsSourcesEmpty'),
  newsTicker: document.getElementById('newsTicker'),
  newsTickerItems: document.getElementById('newsTickerItems'),
  newsTickerOpenButton: document.getElementById('newsTickerOpenButton'),
  newsTickerHideButton: document.getElementById('newsTickerHideButton'),
  radioStatus: document.getElementById('radioStatus'),
  radioSearchForm: document.getElementById('radioSearchForm'),
  radioSearchInput: document.getElementById('radioQuery'),
  radioSearchButton: document.getElementById('radioSearchButton'),
  radioResetButton: document.getElementById('radioResetButton'),
  radioCountrySelect: document.getElementById('radioCountry'),
  radioLanguageSelect: document.getElementById('radioLanguage'),
  radioResults: document.getElementById('radioResults'),
  radioEmptyState: document.getElementById('radioEmptyState'),
  radioFavoritesList: document.getElementById('radioFavoritesList'),
  radioFavoritesEmpty: document.getElementById('radioFavoritesEmpty'),
  radioFavoritesManualForm: document.getElementById('radioFavoritesManualForm'),
  radioFavoritesUrlInput: document.getElementById('radioFavoritesUrlInput'),
  radioFavoritesNameInput: document.getElementById('radioFavoritesNameInput'),
  radioFavoritesManualCancel: document.getElementById('radioFavoritesManualCancel'),
  radioFavoritesAddUrlButton: document.getElementById('radioFavoritesAddUrlButton'),
  radioPlayButton: document.getElementById('radioPlayButton'),
  radioRecordButton: document.getElementById('radioRecordButton'),
  radioRecordStopButton: document.getElementById('radioRecordStopButton'),
  radioPauseButton: document.getElementById('radioPauseButton'),
  radioStopButton: document.getElementById('radioStopButton'),
  radioReloadButton: document.getElementById('radioReloadButton'),
  radioFavoriteButton: document.getElementById('radioFavoriteButton'),
  radioStationName: document.getElementById('radioStationName'),
  radioStationDetails: document.getElementById('radioStationDetails'),
  radioPlayerStatus: document.getElementById('radioPlayerStatus'),
  radioNowPlaying: document.getElementById('radioNowPlaying'),
  radioStationLogo: document.getElementById('radioStationLogo'),
  notesPage: document.getElementById('notes'),
  notesRoot: document.getElementById('notesRoot'),
  notesStatus: document.getElementById('notesStatus'),
  notesPicker: document.getElementById('notesPicker'),
  notesNewButton: document.getElementById('notesNewButton'),
  notesRefreshButton: document.getElementById('notesRefreshButton'),
  notesDeleteButton: document.getElementById('notesDeleteButton'),
  openNotesButton: document.getElementById('openNotesButton'),
  notesTitleInput: document.getElementById('notesTitleInput'),
  notesFormatSelect: document.getElementById('notesFormatSelect'),
  notesFontSelect: document.getElementById('notesFontSelect'),
  notesFontSize: document.getElementById('notesFontSize'),
  notesTextColor: document.getElementById('notesTextColor'),
  notesBackgroundColor: document.getElementById('notesBackgroundColor'),
  notesContent: document.getElementById('notesContent'),
  notesSaveButton: document.getElementById('notesSaveButton'),
  notesCancelButton: document.getElementById('notesCancelButton'),
  notesFullscreenButton: document.getElementById('notesFullscreenButton'),
  notesExitFullscreenButton: document.getElementById('notesExitFullscreenButton')
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

function applyHeaderTurtleSettings() {
  if (typeof document === 'undefined') {
    return;
  }

  const root = document.documentElement;
  if (!root || !root.style || typeof root.style.setProperty !== 'function') {
    return;
  }

  const normalizedSettings = getNormalizedHeaderTurtleSettings();
  const {
    frameWidth: normalizedFrameWidth,
    frameHeight: normalizedFrameHeight,
    frameCount: normalizedFrameCount,
    frameDurationMs: normalizedFrameDurationMs,
    spriteUrl: normalizedSpriteUrl
  } = normalizedSettings;

  const totalDurationMs = normalizedFrameDurationMs * normalizedFrameCount;
  const spriteOffsetPx = Math.max(0, normalizedFrameWidth * (normalizedFrameCount - 1));

  root.style.setProperty('--header-turtle-frame-width', `${normalizedFrameWidth}px`);
  root.style.setProperty('--header-turtle-frame-height', `${normalizedFrameHeight}px`);
  root.style.setProperty('--header-turtle-frame-count', String(normalizedFrameCount));
  root.style.setProperty('--header-turtle-animation-duration', `${totalDurationMs}ms`);
  root.style.setProperty('--header-turtle-sprite-offset', `${spriteOffsetPx}px`);
  root.style.setProperty('--header-turtle-sprite', `url("${normalizedSpriteUrl}")`);
}

function applyHeaderRabbitSettings() {
  if (typeof document === 'undefined') {
    return;
  }

  const root = document.documentElement;
  if (!root || !root.style || typeof root.style.setProperty !== 'function') {
    return;
  }

  const normalizedSettings = getNormalizedHeaderRabbitSettings();
  const {
    frameWidth: normalizedFrameWidth,
    frameHeight: normalizedFrameHeight,
    frameCount: normalizedFrameCount,
    spriteUrl: normalizedSpriteUrl
  } = normalizedSettings;

  root.style.setProperty('--header-rabbit-frame-width', `${normalizedFrameWidth}px`);
  root.style.setProperty('--header-rabbit-frame-height', `${normalizedFrameHeight}px`);
  root.style.setProperty('--header-rabbit-frame-count', String(normalizedFrameCount));
  root.style.setProperty('--header-rabbit-frame-offset', '0px');
  root.style.setProperty('--header-rabbit-sprite', `url("${normalizedSpriteUrl}")`);
}

function applyHeaderBannerSpriteScale() {
  if (typeof document === 'undefined') {
    return;
  }

  const root = document.documentElement;
  if (!root || !root.style || typeof root.style.setProperty !== 'function') {
    return;
  }

  const normalizedScale = getNormalizedHeaderBannerSpriteScale();

  root.style.setProperty('--header-banner-sprite-scale', String(normalizedScale));
}

function getNormalizedHeaderTurtleSettings() {
  const fallback = DEFAULT_HEADER_TURTLE_SETTINGS;
  const settings = typeof ACTIVE_HEADER_TURTLE_SETTINGS === 'object' && ACTIVE_HEADER_TURTLE_SETTINGS
    ? ACTIVE_HEADER_TURTLE_SETTINGS
    : fallback;

  const spriteUrl = typeof settings.spriteUrl === 'string' && settings.spriteUrl.trim()
    ? settings.spriteUrl.trim()
    : fallback.spriteUrl;
  const frameWidth = Number(settings.frameWidth);
  const frameHeight = Number(settings.frameHeight);
  const frameCount = Number(settings.frameCount);
  const frameDurationMs = Number(settings.frameDurationMs);

  const normalizedFrameWidth = Number.isFinite(frameWidth) && frameWidth > 0
    ? Math.round(frameWidth)
    : fallback.frameWidth;
  const normalizedFrameHeight = Number.isFinite(frameHeight) && frameHeight > 0
    ? Math.round(frameHeight)
    : fallback.frameHeight;
  const normalizedFrameCount = Number.isFinite(frameCount) && frameCount > 0
    ? Math.max(1, Math.round(frameCount))
    : fallback.frameCount;
  const normalizedFrameDurationMs = Number.isFinite(frameDurationMs) && frameDurationMs > 0
    ? Math.max(50, Math.round(frameDurationMs))
    : fallback.frameDurationMs;

  const normalizedSpriteUrl = (() => {
    if (!spriteUrl) {
      return '';
    }
    if (/^(?:https?:|data:|blob:)/i.test(spriteUrl)) {
      return spriteUrl;
    }
    if (spriteUrl.startsWith('/') || spriteUrl.startsWith('./') || spriteUrl.startsWith('../')) {
      return spriteUrl;
    }
    return `../../${spriteUrl.replace(/^\/+/, '')}`;
  })();

  return {
    spriteUrl: normalizedSpriteUrl,
    frameWidth: normalizedFrameWidth,
    frameHeight: normalizedFrameHeight,
    frameCount: normalizedFrameCount,
    frameDurationMs: normalizedFrameDurationMs
  };
}

function getNormalizedHeaderBannerSpriteScale() {
  const fallback = DEFAULT_HEADER_BANNER_SPRITE_SCALE;
  const scale = Number(ACTIVE_HEADER_BANNER_SPRITE_SCALE);

  if (!Number.isFinite(scale) || scale <= 0) {
    return fallback;
  }

  return Math.max(0.1, Math.round(scale * 100) / 100);
}

function getNormalizedHeaderRabbitSettings() {
  const fallback = DEFAULT_HEADER_RABBIT_SETTINGS;
  const settings = typeof ACTIVE_HEADER_RABBIT_SETTINGS === 'object' && ACTIVE_HEADER_RABBIT_SETTINGS
    ? ACTIVE_HEADER_RABBIT_SETTINGS
    : fallback;

  const spriteUrl = typeof settings.spriteUrl === 'string' && settings.spriteUrl.trim()
    ? settings.spriteUrl.trim()
    : fallback.spriteUrl;
  const frameWidth = Number(settings.frameWidth);
  const frameHeight = Number(settings.frameHeight);
  const frameCount = Number(settings.frameCount);
  const minClicksPerSecond = Number(settings.minClicksPerSecond);
  const maxClicksPerSecond = Number(settings.maxClicksPerSecond);
  const maxClickGapMs = Number(settings.maxClickGapMs);
  const minFrameDurationMs = Number(settings.minFrameDurationMs);
  const maxFrameDurationMs = Number(settings.maxFrameDurationMs);

  const normalizedFrameWidth = Number.isFinite(frameWidth) && frameWidth > 0
    ? Math.round(frameWidth)
    : fallback.frameWidth;
  const normalizedFrameHeight = Number.isFinite(frameHeight) && frameHeight > 0
    ? Math.round(frameHeight)
    : fallback.frameHeight;
  const normalizedFrameCount = Number.isFinite(frameCount) && frameCount > 0
    ? Math.max(1, Math.round(frameCount))
    : fallback.frameCount;
  const normalizedMinClicksPerSecond = Number.isFinite(minClicksPerSecond) && minClicksPerSecond >= 0
    ? Math.max(0, minClicksPerSecond)
    : fallback.minClicksPerSecond;
  const normalizedMaxClicksPerSecond = Number.isFinite(maxClicksPerSecond) && maxClicksPerSecond > 0
    ? Math.max(normalizedMinClicksPerSecond, maxClicksPerSecond)
    : fallback.maxClicksPerSecond;
  const normalizedMaxClickGapMs = Number.isFinite(maxClickGapMs) && maxClickGapMs > 0
    ? Math.max(1, Math.round(maxClickGapMs))
    : fallback.maxClickGapMs;
  const normalizedMinFrameDurationMs = Number.isFinite(minFrameDurationMs) && minFrameDurationMs > 0
    ? Math.max(50, Math.round(minFrameDurationMs))
    : fallback.minFrameDurationMs;
  const normalizedMaxFrameDurationMs = Number.isFinite(maxFrameDurationMs) && maxFrameDurationMs > 0
    ? Math.max(normalizedMinFrameDurationMs, Math.round(maxFrameDurationMs))
    : fallback.maxFrameDurationMs;

  const normalizedSpriteUrl = (() => {
    if (!spriteUrl) {
      return '';
    }
    if (/^(?:https?:|data:|blob:)/i.test(spriteUrl)) {
      return spriteUrl;
    }
    if (spriteUrl.startsWith('/') || spriteUrl.startsWith('./') || spriteUrl.startsWith('../')) {
      return spriteUrl;
    }
    return `../../${spriteUrl.replace(/^\/+/, '')}`;
  })();

  return {
    spriteUrl: normalizedSpriteUrl,
    frameWidth: normalizedFrameWidth,
    frameHeight: normalizedFrameHeight,
    frameCount: normalizedFrameCount,
    minClicksPerSecond: normalizedMinClicksPerSecond,
    maxClicksPerSecond: normalizedMaxClicksPerSecond,
    maxClickGapMs: normalizedMaxClickGapMs,
    minFrameDurationMs: normalizedMinFrameDurationMs,
    maxFrameDurationMs: normalizedMaxFrameDurationMs
  };
}

function startHeaderTurtleAnimation() {
  if (typeof document === 'undefined') {
    return;
  }

  const turtle = document.querySelector('.status-turtle');
  if (!turtle) {
    return;
  }

  const { frameWidth, frameCount, frameDurationMs } = getNormalizedHeaderTurtleSettings();

  if (headerTurtleAnimationTimer != null) {
    clearInterval(headerTurtleAnimationTimer);
    headerTurtleAnimationTimer = null;
  }

  let currentFrame = 0;

  const applyFrame = () => {
    const offset = -frameWidth * currentFrame;
    turtle.style.setProperty('--header-turtle-frame-offset', `${offset}px`);
    currentFrame = (currentFrame + 1) % frameCount;
  };

  applyFrame();
  headerTurtleAnimationTimer = setInterval(applyFrame, frameDurationMs);
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
