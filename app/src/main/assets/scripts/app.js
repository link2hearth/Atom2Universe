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

function updateNewsNavigationVisibility() {
  if (!elements.navNewsButton) {
    return;
  }
  const enabled = isNewsEnabled();
  elements.navNewsButton.hidden = !enabled;
  elements.navNewsButton.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  elements.navNewsButton.disabled = !enabled;
  elements.navNewsButton.setAttribute('aria-disabled', enabled ? 'false' : 'true');
  if (!enabled) {
    elements.navNewsButton.classList.remove('active');
    if (document.body?.dataset?.activePage === 'news') {
      showPage('game');
    }
  }
}

function applyNewsEnabled(enabled, options = {}) {
  const settings = Object.assign({ persist: true, updateControl: true, skipFetch: false }, options);
  newsFeatureEnabled = enabled !== false;
  if (settings.updateControl && elements.newsToggle) {
    elements.newsToggle.checked = newsFeatureEnabled;
  }
  updateNewsToggleStatusLabel(newsFeatureEnabled);
  updateNewsControlsAvailability();
  updateNewsNavigationVisibility();
  if (settings.persist) {
    writeStoredNewsEnabled(newsFeatureEnabled);
  }
}

function hideStartupOverlay() {
  const overlay = document.getElementById('startupOverlay');
  if (!overlay) {
    return;
  }
  const durationValue = getComputedStyle(document.documentElement)
    .getPropertyValue('--startup-fade-duration')
    .trim();
  const duration = Number.parseFloat(durationValue) || 0;
  let cleanedUp = false;
  const cleanup = () => {
    if (cleanedUp) {
      return;
    }
    cleanedUp = true;
    overlay.hidden = true;
    overlay.removeEventListener('transitionend', cleanup);
  };

  overlay.addEventListener('transitionend', cleanup, { once: true });
  overlay.classList.remove('startup-overlay--visible');
  overlay.setAttribute('aria-hidden', 'true');
  overlay.style.pointerEvents = 'none';
  window.setTimeout(cleanup, Math.max(0, duration) + 120);
}

function setupStartupOverlay() {
  if (document.readyState !== 'loading') {
    hideStartupOverlay();
    return;
  }
  document.addEventListener('DOMContentLoaded', () => {
    hideStartupOverlay();
  }, { once: true });
}

setupStartupOverlay();
