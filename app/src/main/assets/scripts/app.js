    large: Object.freeze({ id: 'large', factor: 1.5 }),
function updateEffectiveUiScaleFactor() {
  if (typeof document === 'undefined') {
    return;
  }
  const root = document.documentElement;
  if (!root || !root.style) {
    return;
  }
  const effective = Math.max(0.4, Math.min(3, baseFactor * autoFactor));
  root.style.setProperty('--font-scale-factor', String(effective));
}

function getActivePageElement() {
  if (typeof document === 'undefined') {
    return null;
  }
  const activePageId = document.body?.dataset?.activePage;
  return activePageId ? document.getElementById(activePageId) : null;
function initializeDomBoundModules() {
  refreshOptionsWelcomeContent();
  subscribeOptionsWelcomeContentUpdates();
  renderThemeOptions();
  updateBigBangVisibility();
  initHeaderBannerToggle();
