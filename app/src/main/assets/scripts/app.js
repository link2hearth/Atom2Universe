const elements = {
  appHeader: null,
  headerBannerToggle: null,
  startupOverlay: null
};

function hydrateElements() {
  if (typeof document === 'undefined') {
    return;
  }
  elements.appHeader = document.querySelector('.app-header');
  elements.headerBannerToggle = document.getElementById('headerBannerToggle');
  elements.startupOverlay = document.getElementById('startupOverlay');
}

function applyUiScaleSelection(selection, options = {}) {
  if (typeof normalizeUiScaleSelection !== 'function') {
    return selection;
  }
  const normalized = normalizeUiScaleSelection(selection);
  const config = UI_SCALE_CHOICES?.[normalized] || UI_SCALE_CHOICES?.[UI_SCALE_DEFAULT];
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  const factor = config && Number.isFinite(config.factor) && config.factor > 0 ? config.factor : 1;
  if (settings.persist && typeof writeStoredUiScale === 'function') {
    writeStoredUiScale(normalized);
  }
  scheduleFrenzyStatusOffsetUpdate();
  return normalized;
}

function updateHeaderBannerToggleLabel(collapsed) {
  if (!elements.headerBannerToggle) {
    return;
  }
  const isCollapsed = Boolean(collapsed);
  const labelKey = isCollapsed
    ? 'index.header.bannerToggle.expand'
    : 'index.header.bannerToggle.collapse';
  const fallbackLabel = isCollapsed ? 'Déployer la bannière' : 'Réduire la bannière';
  const label = typeof window !== 'undefined' && typeof window.t === 'function'
    ? window.t(labelKey) || fallbackLabel
    : fallbackLabel;
  elements.headerBannerToggle.setAttribute('aria-label', label);
  elements.headerBannerToggle.setAttribute('title', label);
  elements.headerBannerToggle.setAttribute('data-i18n', `aria-label:${labelKey};title:${labelKey}`);
  const hiddenLabel = elements.headerBannerToggle.querySelector('.visually-hidden');
  if (hiddenLabel) {
    hiddenLabel.textContent = label;
    hiddenLabel.setAttribute('data-i18n', labelKey);
  }
}

let pendingFrenzyStatusOffsetFrame = null;

function updateFrenzyStatusOffset() {
  if (typeof document === 'undefined') {
    return;
  }
  if (!elements.appHeader) {
    return;
  }
  const root = document.documentElement;
  if (!root || !root.style) {
    return;
  }
  const headerRect = elements.appHeader.getBoundingClientRect?.();
  const headerHeight = Math.max(
    elements.appHeader.offsetHeight || 0,
    headerRect?.height || 0
  );
  if (!Number.isFinite(headerHeight)) {
    return;
  }
  root.style.setProperty('--frenzy-status-offset', `${Math.max(0, headerHeight)}px`);
}

function scheduleFrenzyStatusOffsetUpdate() {
  if (pendingFrenzyStatusOffsetFrame != null) {
    return;
  }
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    pendingFrenzyStatusOffsetFrame = window.requestAnimationFrame(() => {
      pendingFrenzyStatusOffsetFrame = null;
      updateFrenzyStatusOffset();
    });
  } else {
    pendingFrenzyStatusOffsetFrame = setTimeout(() => {
      pendingFrenzyStatusOffsetFrame = null;
      updateFrenzyStatusOffset();
    }, 16);
  }
}

function initFrenzyStatusOffset() {
  if (!elements.appHeader) {
    return;
  }
  scheduleFrenzyStatusOffsetUpdate();
  if (typeof window === 'undefined') {
    return;
  }
  window.addEventListener('resize', scheduleFrenzyStatusOffsetUpdate, { passive: true });
  window.addEventListener('orientationchange', scheduleFrenzyStatusOffsetUpdate);
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', scheduleFrenzyStatusOffsetUpdate);
    window.visualViewport.addEventListener('scroll', scheduleFrenzyStatusOffsetUpdate);
  }
}

function setHeaderCollapsed(collapsed, options = {}) {
  if (!elements.appHeader || !elements.headerBannerToggle) {
    return;
  }
  const shouldCollapse = Boolean(collapsed);
  elements.appHeader.setAttribute('data-collapsed', shouldCollapse ? 'true' : 'false');
  elements.headerBannerToggle.setAttribute('aria-pressed', shouldCollapse ? 'true' : 'false');
  elements.headerBannerToggle.setAttribute('data-state', shouldCollapse ? 'collapsed' : 'expanded');
  updateHeaderBannerToggleLabel(shouldCollapse);
  scheduleFrenzyStatusOffsetUpdate();
  if (options.persist !== false && typeof writeStoredHeaderCollapsed === 'function') {
    writeStoredHeaderCollapsed(shouldCollapse);
  }
}

function hideStartupOverlay() {
  if (!elements.startupOverlay) {
    return;
  }
  elements.startupOverlay.classList.remove('startup-overlay--visible');
  elements.startupOverlay.setAttribute('aria-hidden', 'true');
  elements.startupOverlay.hidden = true;
}

function initStartupOverlay() {
  hydrateElements();
  if (typeof document === 'undefined') {
    return;
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', hideStartupOverlay, { once: true });
  } else {
    hideStartupOverlay();
  }
}

initStartupOverlay();
