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
  overlay.classList.remove('startup-overlay--visible');
  overlay.setAttribute('aria-hidden', 'true');
  const finalize = () => {
    overlay.hidden = true;
  };
  overlay.addEventListener('transitionend', finalize, { once: true });
  window.setTimeout(finalize, 2200);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', hideStartupOverlay, { once: true });
} else {
  hideStartupOverlay();
}
