const DEFAULT_FRENZY_AUTO_COLLECT_DELAY_MS = 1000;
const CRYPTO_WIDGET_BASE_URL = (() => {
  return raw.replace(/\/+$/, '');
})();

let pendingHeaderOffsetFrame = null;
let lastHeaderOffset = null;

const DEFAULT_NEWS_SETTINGS = Object.freeze({
function updateHeaderBannerToggleLabel(collapsed) {
  if (!elements.headerBannerToggle) {
    return;
  }
  }
}

function getAppHeaderOffsetHeight() {
  const header = elements?.appHeader;
  if (!header) {
    return 0;
  }
  const rect = header.getBoundingClientRect?.();
  return Math.max(
    rect?.height || 0,
    header.offsetHeight || 0,
    header.scrollHeight || 0
  );
}

function applyHeaderOffsetCss() {
  if (typeof document === 'undefined') {
    return;
  }
  const offset = Math.max(0, Math.round(getAppHeaderOffsetHeight()));
  const root = document.documentElement;
  if (!root) {
    return;
  }
  if (lastHeaderOffset === offset) {
    return;
  }
  lastHeaderOffset = offset;
  root.style.setProperty('--app-header-offset', `${offset}px`);
}

function scheduleHeaderOffsetUpdate() {
  if (typeof window === 'undefined') {
    return;
  }
  if (pendingHeaderOffsetFrame != null) {
    return;
  }
  if (typeof window.requestAnimationFrame === 'function') {
    pendingHeaderOffsetFrame = window.requestAnimationFrame(() => {
      pendingHeaderOffsetFrame = null;
      applyHeaderOffsetCss();
    });
  } else {
    pendingHeaderOffsetFrame = setTimeout(() => {
      pendingHeaderOffsetFrame = null;
      applyHeaderOffsetCss();
    }, 16);
  }
}

function initHeaderOffsetTracking() {
  if (typeof window === 'undefined') {
    return;
  }
  applyHeaderOffsetCss();
  window.addEventListener('resize', scheduleHeaderOffsetUpdate, { passive: true });
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', scheduleHeaderOffsetUpdate);
  }
}

function setHeaderCollapsed(collapsed, options = {}) {
  if (!elements.appHeader || !elements.headerBannerToggle) {
    return;
  }
  updateHeaderBannerToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredHeaderCollapsed(shouldCollapse);
  }
  scheduleHeaderOffsetUpdate();
}
function initializeDomBoundModules() {
  refreshOptionsWelcomeContent();
  subscribeOptionsWelcomeContentUpdates();
  renderThemeOptions();
  updateBigBangVisibility();
  initHeaderBannerToggle();
  initHeaderOffsetTracking();
