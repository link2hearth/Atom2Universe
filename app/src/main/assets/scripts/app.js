const DEFAULT_STARTUP_FADE_DURATION_MS = 2000;
const DEFAULT_STARTUP_OVERLAY_MAX_WAIT_MS = 15000;
let overlayFadeFallbackTimeout = null;
let startupOverlayFailsafeTimeout = null;
let startupOverlayGlobalFallbackTimeout = null;
let startupOverlayMaxWaitTimeout = null;
let visibilityChangeListenerAttached = false;
let appStartAttempted = false;
let appStartCompleted = false;
function getConfiguredStartupFadeDurationMs() {
  return resolveGlobalNumberOption('STARTUP_FADE_DURATION_MS', DEFAULT_STARTUP_FADE_DURATION_MS);
}

function getStartupOverlayMaxWaitMs() {
  return resolveGlobalNumberOption('STARTUP_OVERLAY_MAX_WAIT_MS', DEFAULT_STARTUP_OVERLAY_MAX_WAIT_MS);
}

function getNormalizedStartupFadeDuration() {
  const configuredValue = getConfiguredStartupFadeDurationMs();
  return Math.max(0, Number.isFinite(configuredValue) ? configuredValue : 0);
}
function clearStartupOverlayFailsafe() {
  if (startupOverlayFailsafeTimeout != null) {
    clearTimeout(startupOverlayFailsafeTimeout);
    startupOverlayFailsafeTimeout = null;
  }
}

function clearStartupOverlayMaxWait() {
  if (startupOverlayMaxWaitTimeout != null) {
    clearTimeout(startupOverlayMaxWaitTimeout);
    startupOverlayMaxWaitTimeout = null;
  }
}

function scheduleStartupOverlayFailsafe() {
  if (startupOverlayFailsafeTimeout != null) {
    return;
  }
  }, failsafeDelay);
}

function scheduleStartupOverlayMaxWait() {
  if (startupOverlayMaxWaitTimeout != null) {
    return;
  }

  const maxWaitMs = Math.max(0, getStartupOverlayMaxWaitMs());
  if (!maxWaitMs) {
    return;
  }

  startupOverlayMaxWaitTimeout = setTimeout(() => {
    startupOverlayMaxWaitTimeout = null;

    if (appStartCompleted) {
      return;
    }

    console.warn('Startup overlay max wait reached, forcing visibility reset');

    if (!appStartAttempted) {
      bootApplication();
    } else {
      safelyStartApp({ force: true });
    }

    hideStartupOverlay({ instant: true });
  }, maxWaitMs);
}
  clearStartupOverlayFailsafe();
  clearGlobalStartupOverlayFallback();
  clearStartupOverlayMaxWait();
  applyAtomVariantVisualState();
  applyStartupOverlayDuration();
  scheduleStartupOverlayFailsafe();
  scheduleStartupOverlayMaxWait();
  prefetchAndroidManagedFiles();
