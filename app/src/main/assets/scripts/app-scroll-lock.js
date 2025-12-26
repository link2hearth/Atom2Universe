const SCROLL_BEHAVIOR_MODES = Object.freeze({
  DEFAULT: 'default',
  FORCE: 'force',
  LOCK: 'lock'
});

const SCROLL_BEHAVIOR_ALIAS_MAP = new Map([
  ['default', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['normal', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['auto', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['free', SCROLL_BEHAVIOR_MODES.DEFAULT],
  ['force', SCROLL_BEHAVIOR_MODES.FORCE],
  ['unlock', SCROLL_BEHAVIOR_MODES.FORCE],
  ['enable', SCROLL_BEHAVIOR_MODES.FORCE],
  ['lock', SCROLL_BEHAVIOR_MODES.LOCK],
  ['locked', SCROLL_BEHAVIOR_MODES.LOCK],
  ['none', SCROLL_BEHAVIOR_MODES.LOCK],
  ['disable', SCROLL_BEHAVIOR_MODES.LOCK]
]);

const supportsPassiveEventListeners = (() => {
  if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
    return false;
  }
  let supported = false;
  try {
    const options = Object.defineProperty({}, 'passive', {
      get() {
        supported = true;
        return false;
      }
    });
    window.addEventListener('testPassive', null, options);
    window.removeEventListener('testPassive', null, options);
  } catch (error) {
    supported = false;
  }
  return supported;
})();

const passiveEventListenerOptions = supportsPassiveEventListeners ? { passive: true } : false;
const passiveCaptureEventListenerOptions = supportsPassiveEventListeners
  ? { passive: true, capture: true }
  : true;
const nonPassiveEventListenerOptions = supportsPassiveEventListeners ? { passive: false } : false;
const supportsGlobalPointerEvents = typeof globalThis !== 'undefined'
  && typeof globalThis.PointerEvent === 'function';

let activeBodyScrollBehavior = SCROLL_BEHAVIOR_MODES.DEFAULT;
let bodyScrollTouchMoveListenerAttached = false;
const activeTouchIdentifiers = new Map();
const activePointerTouchIds = new Map();
const TOUCH_TRACKING_STALE_THRESHOLD_MS = 500;
const TOUCH_TRACKING_FORCE_RESET_THRESHOLD_MS = 5000;
const TOUCH_TRACKING_WATCHDOG_INTERVAL_MS = 4000;
const MAX_SCROLL_UNLOCK_FAILURES = 3;
let pendingScrollUnlockCheckId = null;
let consecutiveScrollUnlockFailures = 0;

function getCurrentTimestamp() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

function pruneStaleTouchTracking(now = getCurrentTimestamp()) {
  const cutoff = now - TOUCH_TRACKING_STALE_THRESHOLD_MS;
  activeTouchIdentifiers.forEach((timestamp, identifier) => {
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      activeTouchIdentifiers.delete(identifier);
    }
  });
  activePointerTouchIds.forEach((timestamp, identifier) => {
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      activePointerTouchIds.delete(identifier);
    }
  });
}

function forceResetStuckTouchTracking(now = getCurrentTimestamp()) {
  const cutoff = now - TOUCH_TRACKING_FORCE_RESET_THRESHOLD_MS;
  let hasStaleEntries = false;

  activeTouchIdentifiers.forEach(timestamp => {
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      hasStaleEntries = true;
    }
  });

  activePointerTouchIds.forEach(timestamp => {
    if (!Number.isFinite(timestamp) || timestamp < cutoff) {
      hasStaleEntries = true;
    }
  });

  if (!hasStaleEntries) {
    return;
  }

  resetTouchTrackingState({ reapplyScrollBehavior: false });
  forceUnlockScrollSafe();
}

function cancelScheduledScrollUnlockCheck() {
  const timerHost = typeof window !== 'undefined'
    ? window
    : (typeof globalThis !== 'undefined' ? globalThis : null);
  if (!timerHost || typeof timerHost.clearTimeout !== 'function') {
    pendingScrollUnlockCheckId = null;
    return;
  }
  if (pendingScrollUnlockCheckId !== null) {
    timerHost.clearTimeout(pendingScrollUnlockCheckId);
    pendingScrollUnlockCheckId = null;
  }
}

function scheduleScrollUnlockCheck(delayMs = TOUCH_TRACKING_STALE_THRESHOLD_MS) {
  const timerHost = typeof window !== 'undefined'
    ? window
    : (typeof globalThis !== 'undefined' ? globalThis : null);
  if (!timerHost || typeof timerHost.setTimeout !== 'function') {
    pruneStaleTouchTracking();
    if (!hasRemainingActiveTouches()) {
      consecutiveScrollUnlockFailures = 0;
      applyActivePageScrollBehavior();
    }
    return;
  }
  if (pendingScrollUnlockCheckId !== null && typeof timerHost.clearTimeout === 'function') {
    timerHost.clearTimeout(pendingScrollUnlockCheckId);
  }
  const timeout = Math.max(16, Number.isFinite(delayMs) ? delayMs : TOUCH_TRACKING_STALE_THRESHOLD_MS);
  pendingScrollUnlockCheckId = timerHost.setTimeout(() => {
    pendingScrollUnlockCheckId = null;
    pruneStaleTouchTracking();
    if (!hasRemainingActiveTouches()) {
      consecutiveScrollUnlockFailures = 0;
      applyActivePageScrollBehavior();
    } else {
      consecutiveScrollUnlockFailures += 1;
      if (consecutiveScrollUnlockFailures >= MAX_SCROLL_UNLOCK_FAILURES) {
        resetTouchTrackingState();
      } else {
        scheduleScrollUnlockCheck(TOUCH_TRACKING_STALE_THRESHOLD_MS);
      }
    }
  }, timeout);
}

function normalizeTouchIdentifier(touch) {
  if (!touch || typeof touch !== 'object') {
    return null;
  }
  if (Number.isFinite(touch.identifier)) {
    return `touch:${touch.identifier}`;
  }
  if (Number.isFinite(touch.pointerId)) {
    return `pointer:${touch.pointerId}`;
  }
  return null;
}

function forceUnlockScrollSafe(options = {}) {
  const { reapplyScrollBehavior = true } = options || {};
  resetTouchTrackingState({ reapplyScrollBehavior: false });

  const scrollLockManager = typeof globalThis !== 'undefined'
    ? globalThis.ScrollLock
    : null;
  try {
    scrollLockManager?.unlock?.();
  } catch (error) {
    // Ignore failures from optional scroll lock managers.
  }

  if (typeof document !== 'undefined') {
    applyBodyScrollBehavior(SCROLL_BEHAVIOR_MODES.DEFAULT);
    const html = document.documentElement || null;
    const body = document.body || null;

    if (body) {
      body.style.removeProperty('overflow');
      body.style.removeProperty('touch-action');
      body.style.removeProperty('overscroll-behavior');
      body.classList?.remove?.('touch-scroll-lock');
      body.classList?.remove?.('touch-scroll-force');
    }

    if (html) {
      html.style.removeProperty('overflow');
      html.style.removeProperty('touch-action');
      html.style.removeProperty('overscroll-behavior');
      html.classList?.remove?.('touch-scroll-lock');
      html.classList?.remove?.('touch-scroll-force');
    }
  } else {
    activeBodyScrollBehavior = SCROLL_BEHAVIOR_MODES.DEFAULT;
  }

  if (reapplyScrollBehavior) {
    applyActivePageScrollBehavior();
  }
}

function registerActiveTouches(touchList, fullTouchList = null) {
  cancelScheduledScrollUnlockCheck();
  consecutiveScrollUnlockFailures = 0;

  let activeIdentifiers = null;
  if (fullTouchList && typeof fullTouchList.length === 'number') {
    activeIdentifiers = new Set();
    for (let index = 0; index < fullTouchList.length; index += 1) {
      const identifier = normalizeTouchIdentifier(fullTouchList[index]);
      if (identifier != null) {
        activeIdentifiers.add(identifier);
      }
    }

    activeTouchIdentifiers.forEach((_, identifier) => {
      if (!activeIdentifiers.has(identifier)) {
        activeTouchIdentifiers.delete(identifier);
      }
    });

    if (fullTouchList.length === 0) {
      try { activeTouchIdentifiers.clear?.(); } catch (error) {}
      try { activePointerTouchIds?.clear?.(); } catch (error) {}
      forceUnlockScrollSafe();
      return;
    }
  }

  if (!touchList || typeof touchList.length !== 'number') {
    return;
  }

  const now = getCurrentTimestamp();
  for (let index = 0; index < touchList.length; index += 1) {
    const identifier = normalizeTouchIdentifier(touchList[index]);
    if (identifier != null && (!activeIdentifiers || activeIdentifiers.has(identifier))) {
      activeTouchIdentifiers.set(identifier, now);
    }
  }
}

function unregisterActiveTouches(touchList, activeTouchList = null) {
  if (touchList && typeof touchList.length === 'number') {
    Array.from(touchList).forEach(touch => {
      const identifier = normalizeTouchIdentifier(touch);
      if (identifier) {
        activeTouchIdentifiers.delete(identifier);
      }
    });
  }
  if (activeTouchList && typeof activeTouchList.length === 'number') {
    const currentIdentifiers = new Set();
    Array.from(activeTouchList).forEach(touch => {
      const identifier = normalizeTouchIdentifier(touch);
      if (identifier) {
        currentIdentifiers.add(identifier);
      }
    });
    activeTouchIdentifiers.forEach((_, identifier) => {
      if (!currentIdentifiers.has(identifier)) {
        activeTouchIdentifiers.delete(identifier);
      }
    });
    if (activeTouchList.length === 0) {
      activePointerTouchIds.clear();
    }
  }
}

function hasRemainingActiveTouches(event) {
  if (Number.isFinite(event?.touches?.length) && event.touches.length > 0) {
    return true;
  }
  pruneStaleTouchTracking();
  return activeTouchIdentifiers.size > 0 || activePointerTouchIds.size > 0;
}

function preventBodyScrollTouchMove(event) {
  if (!event) {
    return;
  }
  if (typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
}

function normalizeScrollBehaviorValue(value) {
  if (typeof value !== 'string') {
    return SCROLL_BEHAVIOR_MODES.DEFAULT;
  }
  const normalized = value.trim().toLowerCase();
  if (!normalized) {
    return SCROLL_BEHAVIOR_MODES.DEFAULT;
  }
  return SCROLL_BEHAVIOR_ALIAS_MAP.get(normalized) || SCROLL_BEHAVIOR_MODES.DEFAULT;
}

function resolvePageScrollBehavior(pageElement) {
  if (!pageElement || typeof pageElement !== 'object') {
    return SCROLL_BEHAVIOR_MODES.DEFAULT;
  }
  const dataset = pageElement.dataset || {};
  const rawBehavior = typeof dataset.scrollBehavior === 'string'
    ? dataset.scrollBehavior
    : typeof dataset.scrollMode === 'string'
      ? dataset.scrollMode
      : null;
  if (rawBehavior) {
    return normalizeScrollBehaviorValue(rawBehavior);
  }
  if (typeof pageElement.hasAttribute === 'function' && pageElement.hasAttribute('data-force-scroll')) {
    return SCROLL_BEHAVIOR_MODES.FORCE;
  }
  const pageId = typeof pageElement.id === 'string'
    ? pageElement.id.trim().toLowerCase()
    : '';
  if (pageId === 'game') {
    return SCROLL_BEHAVIOR_MODES.LOCK;
  }
  return SCROLL_BEHAVIOR_MODES.DEFAULT;
}

function applyBodyScrollBehavior(requestedBehavior) {
  if (typeof document === 'undefined') {
    activeBodyScrollBehavior = normalizeScrollBehaviorValue(requestedBehavior);
    return;
  }
  const body = document.body;
  if (!body) {
    activeBodyScrollBehavior = normalizeScrollBehaviorValue(requestedBehavior);
    return;
  }
  const nextBehavior = normalizeScrollBehaviorValue(requestedBehavior);
  activeBodyScrollBehavior = nextBehavior;

  if (nextBehavior === SCROLL_BEHAVIOR_MODES.LOCK) {
    if (!bodyScrollTouchMoveListenerAttached && typeof body.addEventListener === 'function') {
      body.addEventListener('touchmove', preventBodyScrollTouchMove, nonPassiveEventListenerOptions);
      bodyScrollTouchMoveListenerAttached = true;
    }
    body.style.setProperty('touch-action', 'none');
    body.style.setProperty('overscroll-behavior', 'none');
    body.classList.add('touch-scroll-lock');
    body.classList.remove('touch-scroll-force');
    return;
  }

  if (bodyScrollTouchMoveListenerAttached && typeof body.removeEventListener === 'function') {
    body.removeEventListener('touchmove', preventBodyScrollTouchMove, nonPassiveEventListenerOptions);
    bodyScrollTouchMoveListenerAttached = false;
  }

  if (nextBehavior === SCROLL_BEHAVIOR_MODES.FORCE) {
    body.style.setProperty('touch-action', 'auto');
    body.style.setProperty('overscroll-behavior', 'auto');
    body.classList.add('touch-scroll-force');
    body.classList.remove('touch-scroll-lock');
  } else {
    body.style.removeProperty('touch-action');
    body.style.removeProperty('overscroll-behavior');
    body.classList.remove('touch-scroll-lock');
    body.classList.remove('touch-scroll-force');
  }
}

function applyActivePageScrollBehavior(activePageElement) {
  if (typeof document === 'undefined') {
    activeBodyScrollBehavior = normalizeScrollBehaviorValue(activeBodyScrollBehavior);
    return;
  }
  let pageElement = activePageElement;
  if (!pageElement) {
    const activePageId = document.body?.dataset?.activePage;
    if (activePageId) {
      pageElement = document.getElementById(activePageId);
    }
  }
  if (!pageElement && typeof document.querySelector === 'function') {
    pageElement = document.querySelector('.page.active');
  }
  applyBodyScrollBehavior(resolvePageScrollBehavior(pageElement));
}

function handleGlobalTouchStart(event) {
  registerActiveTouches(event?.changedTouches, event?.touches);
}

function handleGlobalTouchMove(event) {
  registerActiveTouches(event?.changedTouches, event?.touches);
}

function handleGlobalTouchCompletion(event) {
  registerActiveTouches(event?.changedTouches, event?.touches);
  unregisterActiveTouches(event?.changedTouches, event?.touches);
  if (event?.type === 'touchcancel' && (!event.changedTouches || event.changedTouches.length === 0)) {
    activeTouchIdentifiers.clear();
    activePointerTouchIds.clear();
  }
  if (hasRemainingActiveTouches(event)) {
    scheduleScrollUnlockCheck();
    return;
  }
  forceUnlockScrollSafe();
}

function isTouchLikePointerEvent(event) {
  if (!event) {
    return false;
  }
  const pointerType = typeof event.pointerType === 'string'
    ? event.pointerType.toLowerCase()
    : '';
  if (pointerType === 'touch') {
    return true;
  }
  if (!pointerType && Number.isFinite(event.pointerId)) {
    return activePointerTouchIds.has(event.pointerId);
  }
  return false;
}

function handleGlobalPointerCompletion(event) {
  if (!isTouchLikePointerEvent(event)) {
    return;
  }
  if (event.type === 'pointerout' && event.relatedTarget) {
    return;
  }
  if (Number.isFinite(event.pointerId)) {
    activePointerTouchIds.delete(event.pointerId);
  } else {
    activePointerTouchIds.clear();
  }
  if (hasRemainingActiveTouches()) {
    scheduleScrollUnlockCheck();
    return;
  }
  forceUnlockScrollSafe();
}

function handleGlobalPointerStart(event) {
  if (!event || event.pointerType !== 'touch') {
    return;
  }
  cancelScheduledScrollUnlockCheck();
  const now = getCurrentTimestamp();
  if (Number.isFinite(event.pointerId)) {
    activePointerTouchIds.set(event.pointerId, now);
  } else {
    activePointerTouchIds.set('pointer', now);
  }
}

function handleGlobalPointerMove(event) {
  if (!isTouchLikePointerEvent(event)) {
    return;
  }
  const now = getCurrentTimestamp();
  if (Number.isFinite(event.pointerId)) {
    activePointerTouchIds.set(event.pointerId, now);
  } else {
    activePointerTouchIds.set('pointer', now);
  }
  scheduleScrollUnlockCheck();
}

function resetTouchTrackingState(options = {}) {
  const { reapplyScrollBehavior = true } = options;
  activeTouchIdentifiers.clear();
  activePointerTouchIds.clear();
  cancelScheduledScrollUnlockCheck();
  consecutiveScrollUnlockFailures = 0;
  if (reapplyScrollBehavior) {
    applyActivePageScrollBehavior();
  }
}

if (typeof globalThis !== 'undefined') {
  globalThis.resetTouchTrackingState = resetTouchTrackingState;
  globalThis.forceUnlockScrollSafe = forceUnlockScrollSafe;
}

if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      forceUnlockScrollSafe({ reapplyScrollBehavior: false });
      return;
    }
    applyActivePageScrollBehavior();
  });

  document.addEventListener('touchstart', handleGlobalTouchStart, passiveCaptureEventListenerOptions);
  document.addEventListener('touchmove', handleGlobalTouchMove, passiveCaptureEventListenerOptions);
  ['touchend', 'touchcancel'].forEach(eventName => {
    document.addEventListener(eventName, handleGlobalTouchCompletion, passiveCaptureEventListenerOptions);
  });

  if (supportsGlobalPointerEvents) {
    document.addEventListener('pointerdown', handleGlobalPointerStart, passiveCaptureEventListenerOptions);
    document.addEventListener('pointermove', handleGlobalPointerMove, passiveCaptureEventListenerOptions);
    ['pointerup', 'pointercancel', 'pointerleave', 'pointerout', 'lostpointercapture'].forEach(eventName => {
      document.addEventListener(eventName, handleGlobalPointerCompletion, passiveCaptureEventListenerOptions);
    });
  }
}

if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('pageshow', () => {
    applyActivePageScrollBehavior();
  }, passiveEventListenerOptions);
  window.addEventListener('focus', () => {
    applyActivePageScrollBehavior();
  }, passiveEventListenerOptions);
  window.addEventListener('resize', () => {
    forceResetStuckTouchTracking();
    applyActivePageScrollBehavior();
  }, passiveEventListenerOptions);
  window.addEventListener('orientationchange', () => {
    forceUnlockScrollSafe();
  }, passiveEventListenerOptions);
  window.addEventListener('atom2univers:scroll-reset', () => {
    forceUnlockScrollSafe();
  });
  window.addEventListener('touchstart', handleGlobalTouchStart, passiveCaptureEventListenerOptions);
  window.addEventListener('touchmove', handleGlobalTouchMove, passiveCaptureEventListenerOptions);
  ['touchend', 'touchcancel'].forEach(eventName => {
    window.addEventListener(eventName, handleGlobalTouchCompletion, passiveCaptureEventListenerOptions);
  });
  if (supportsGlobalPointerEvents) {
    window.addEventListener('pointerdown', handleGlobalPointerStart, passiveCaptureEventListenerOptions);
    window.addEventListener('pointermove', handleGlobalPointerMove, passiveCaptureEventListenerOptions);
    ['pointerup', 'pointercancel', 'pointerleave', 'pointerout', 'lostpointercapture'].forEach(eventName => {
      window.addEventListener(eventName, handleGlobalPointerCompletion, passiveCaptureEventListenerOptions);
    });
  }
  window.addEventListener('pointercancel', forceUnlockScrollSafe, passiveEventListenerOptions);
  window.addEventListener('blur', forceUnlockScrollSafe);
  window.addEventListener('pagehide', forceUnlockScrollSafe);
}

if (typeof window !== 'undefined' && typeof window.setInterval === 'function') {
  window.setInterval(() => {
    forceResetStuckTouchTracking();
    if (!hasRemainingActiveTouches()) {
      applyActivePageScrollBehavior();
    }
  }, TOUCH_TRACKING_WATCHDOG_INTERVAL_MS);
}
