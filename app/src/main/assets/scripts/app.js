const DEFAULT_FRENZY_AUTO_COLLECT_DELAY_MS = 1000;
const CRYPTO_WIDGET_BASE_URL = (() => {
  return raw.replace(/\/+$/, '');
})();


const DEFAULT_NEWS_SETTINGS = Object.freeze({
function updateHeaderBannerToggleLabel(collapsed) {
  if (!elements.headerBannerToggle) {
    return;
  }
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
}
function initializeDomBoundModules() {
  refreshOptionsWelcomeContent();
  subscribeOptionsWelcomeContentUpdates();
  renderThemeOptions();
  updateBigBangVisibility();
  initHeaderBannerToggle();
