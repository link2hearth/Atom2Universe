const FRENZY_STATUS_OFFSET_VH = (() => {
  const raw = GLOBAL_CONFIG?.ui?.frenzyStatusOffsetVh;
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return null;
  }
  return Math.max(0, Math.min(100, value));
})();

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

function applyFrenzyStatusOffset() {
  if (typeof document === 'undefined') {
    return;
  }
  const root = document.documentElement;
  if (!root || !root.style) {
    return;
  }
  if (Number.isFinite(FRENZY_STATUS_OFFSET_VH)) {
    root.style.setProperty('--frenzy-status-offset', `${FRENZY_STATUS_OFFSET_VH}vh`);
  } else {
    root.style.removeProperty('--frenzy-status-offset');
  }
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
  applyFrenzyStatusOffset();
