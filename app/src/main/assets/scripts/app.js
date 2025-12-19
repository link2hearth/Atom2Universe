function applyUiScaleSelection(selection, options = {}) {
  const normalized = normalizeUiScaleSelection(selection);
  const config = UI_SCALE_CHOICES[normalized] || UI_SCALE_CHOICES[UI_SCALE_DEFAULT];
  const settings = Object.assign({ persist: true, updateControl: true }, options);
  const factor = config && Number.isFinite(config.factor) && config.factor > 0 ? config.factor : 1;
  if (settings.persist) {
    writeStoredUiScale(normalized);
  }
  scheduleFrenzyStatusOffsetUpdate();
  return normalized;
}
  scheduleFrenzyStatusOffsetUpdate();
function updateHeaderBannerToggleLabel(collapsed) {
  if (!elements.headerBannerToggle) {
    return;
  }
  if (hiddenLabel) {
    hiddenLabel.textContent = label;
    hiddenLabel.setAttribute('data-i18n', key);
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
  elements.headerBannerToggle.setAttribute('aria-pressed', shouldCollapse ? 'true' : 'false');
  elements.headerBannerToggle.setAttribute('data-state', shouldCollapse ? 'collapsed' : 'expanded');
  updateHeaderBannerToggleLabel(shouldCollapse);
  scheduleFrenzyStatusOffsetUpdate();
  if (options.persist !== false) {
    writeStoredHeaderCollapsed(shouldCollapse);
  }
}
  updateBigBangVisibility();
  initHeaderBannerToggle();
  initFrenzyStatusOffset();
