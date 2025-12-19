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


(function initStartupOverlay() {
  const overlay = document.getElementById('startupOverlay');
  if (!overlay) {
    return;
  }

  const finalizeHide = () => {
    overlay.classList.remove('startup-overlay--visible');
    overlay.setAttribute('aria-hidden', 'true');

    const hideOverlay = () => {
      overlay.hidden = true;
    };

    overlay.addEventListener('transitionend', hideOverlay, { once: true });
    setTimeout(hideOverlay, 2500);
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', finalizeHide, { once: true });
  } else {
    finalizeHide();
  }
})();
