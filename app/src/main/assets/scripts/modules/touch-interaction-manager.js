(() => {
  const GLOBAL = typeof globalThis !== 'undefined' ? globalThis : window;

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
    ['scroll', SCROLL_BEHAVIOR_MODES.FORCE],
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
      window.addEventListener('touchstart', null, options);
      window.removeEventListener('touchstart', null, options);
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

  const supportsGlobalPointerEvents = typeof GLOBAL !== 'undefined'
    && typeof GLOBAL.PointerEvent === 'function';

  function normalizeScrollBehaviorValue(value) {
    if (typeof value !== 'string') {
      return SCROLL_BEHAVIOR_MODES.DEFAULT;
    }
    const key = value.trim().toLowerCase();
    return SCROLL_BEHAVIOR_ALIAS_MAP.get(key) || SCROLL_BEHAVIOR_MODES.DEFAULT;
  }

  function preventDefault(event) {
    if (event && typeof event.preventDefault === 'function') {
      event.preventDefault();
    }
  }

  class TouchInteractionManager {
    constructor(options = {}) {
      const {
        rootElement = typeof document !== 'undefined' ? document : null,
        scrollTarget = typeof document !== 'undefined' ? document.body : null,
        defaultMode = SCROLL_BEHAVIOR_MODES.DEFAULT,
        unlockDelayMs = 200
      } = options;

      this.rootElement = rootElement;
      this.scrollTarget = scrollTarget;
      this.defaultMode = normalizeScrollBehaviorValue(defaultMode);
      this.pageMode = this.defaultMode;
      this.unlockDelayMs = Number.isFinite(unlockDelayMs) ? Math.max(0, unlockDelayMs) : 200;

      this.activeTouches = new Map();
      this.activePointerIds = new Map();
      this.pendingUnlockId = null;

      this.scrollTouchMoveListenerAttached = false;
      this.started = false;

      this.handleTouchStart = this.handleTouchStart.bind(this);
      this.handleTouchEnd = this.handleTouchEnd.bind(this);
      this.handlePointerStart = this.handlePointerStart.bind(this);
      this.handlePointerEnd = this.handlePointerEnd.bind(this);
      this.handleVisibilityChange = this.handleVisibilityChange.bind(this);
      this.applyPageMode = this.applyPageMode.bind(this);
      this.forceUnlock = this.forceUnlock.bind(this);
    }

    get hasActiveContacts() {
      return this.activeTouches.size > 0 || this.activePointerIds.size > 0;
    }

    scheduleUnlockCheck() {
      if (!Number.isFinite(this.unlockDelayMs) || this.unlockDelayMs <= 0) {
        this.updateScrollBehavior();
        return;
      }
      this.cancelPendingUnlock();
      const timerHost = typeof window !== 'undefined' ? window : GLOBAL;
      if (!timerHost || typeof timerHost.setTimeout !== 'function') {
        this.updateScrollBehavior();
        return;
      }
      this.pendingUnlockId = timerHost.setTimeout(() => {
        this.pendingUnlockId = null;
        this.updateScrollBehavior();
      }, this.unlockDelayMs);
    }

    cancelPendingUnlock() {
      const timerHost = typeof window !== 'undefined' ? window : GLOBAL;
      if (this.pendingUnlockId === null || !timerHost || typeof timerHost.clearTimeout !== 'function') {
        this.pendingUnlockId = null;
        return;
      }
      timerHost.clearTimeout(this.pendingUnlockId);
      this.pendingUnlockId = null;
    }

    start() {
      if (this.started || !this.rootElement) {
        return;
      }
      this.started = true;
      const root = this.rootElement;
      if (typeof root.addEventListener === 'function') {
        root.addEventListener('touchstart', this.handleTouchStart, passiveCaptureEventListenerOptions);
        root.addEventListener('touchend', this.handleTouchEnd, passiveCaptureEventListenerOptions);
        root.addEventListener('touchcancel', this.handleTouchEnd, passiveCaptureEventListenerOptions);
        if (supportsGlobalPointerEvents) {
          root.addEventListener('pointerdown', this.handlePointerStart, passiveCaptureEventListenerOptions);
        }
      }
      if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
        document.addEventListener('visibilitychange', this.handleVisibilityChange);
      }
      if (supportsGlobalPointerEvents && typeof GLOBAL?.addEventListener === 'function') {
        GLOBAL.addEventListener('pointerup', this.handlePointerEnd, passiveCaptureEventListenerOptions);
        GLOBAL.addEventListener('pointercancel', this.handlePointerEnd, passiveCaptureEventListenerOptions);
      }
      if (typeof GLOBAL?.addEventListener === 'function') {
        GLOBAL.addEventListener('blur', this.forceUnlock, passiveEventListenerOptions);
        GLOBAL.addEventListener('pageshow', this.forceUnlock, passiveEventListenerOptions);
        GLOBAL.addEventListener('focus', this.forceUnlock, passiveEventListenerOptions);
      }
      this.updateScrollBehavior();
    }

    stop({ resetState = true } = {}) {
      if (!this.started) {
        return;
      }
      this.started = false;
      const root = this.rootElement;
      if (root && typeof root.removeEventListener === 'function') {
        root.removeEventListener('touchstart', this.handleTouchStart, passiveCaptureEventListenerOptions);
        root.removeEventListener('touchend', this.handleTouchEnd, passiveCaptureEventListenerOptions);
        root.removeEventListener('touchcancel', this.handleTouchEnd, passiveCaptureEventListenerOptions);
        if (supportsGlobalPointerEvents) {
          root.removeEventListener('pointerdown', this.handlePointerStart, passiveCaptureEventListenerOptions);
        }
      }
      if (supportsGlobalPointerEvents && typeof GLOBAL?.removeEventListener === 'function') {
        GLOBAL.removeEventListener('pointerup', this.handlePointerEnd, passiveCaptureEventListenerOptions);
        GLOBAL.removeEventListener('pointercancel', this.handlePointerEnd, passiveCaptureEventListenerOptions);
      }
      if (typeof GLOBAL?.removeEventListener === 'function') {
        GLOBAL.removeEventListener('blur', this.forceUnlock, passiveEventListenerOptions);
        GLOBAL.removeEventListener('pageshow', this.forceUnlock, passiveEventListenerOptions);
        GLOBAL.removeEventListener('focus', this.forceUnlock, passiveEventListenerOptions);
      }
      if (typeof document !== 'undefined' && typeof document.removeEventListener === 'function') {
        document.removeEventListener('visibilitychange', this.handleVisibilityChange);
      }
      this.cancelPendingUnlock();
      if (resetState) {
        this.reset({ reapplyScrollBehavior: false });
      }
      this.applyScrollBehavior(this.defaultMode);
    }

    reset({ reapplyScrollBehavior = true } = {}) {
      this.cancelPendingUnlock();
      this.activeTouches.clear();
      this.activePointerIds.clear();
      if (reapplyScrollBehavior) {
        this.updateScrollBehavior();
      }
    }

    forceUnlock() {
      this.reset({ reapplyScrollBehavior: true });
    }

    applyScrollBehavior(requestedBehavior) {
      const target = this.scrollTarget;
      const mode = normalizeScrollBehaviorValue(requestedBehavior);
      this.activeScrollMode = mode;
      if (!target) {
        return;
      }

      if (mode === SCROLL_BEHAVIOR_MODES.LOCK) {
        if (!this.scrollTouchMoveListenerAttached && typeof target.addEventListener === 'function') {
          target.addEventListener('touchmove', preventDefault, nonPassiveEventListenerOptions);
          this.scrollTouchMoveListenerAttached = true;
        }
        target.style.setProperty('touch-action', 'none');
        target.style.setProperty('overscroll-behavior', 'none');
        target.classList.add('touch-scroll-lock');
        target.classList.remove('touch-scroll-force');
        return;
      }

      if (this.scrollTouchMoveListenerAttached && typeof target.removeEventListener === 'function') {
        target.removeEventListener('touchmove', preventDefault, nonPassiveEventListenerOptions);
        this.scrollTouchMoveListenerAttached = false;
      }

      if (mode === SCROLL_BEHAVIOR_MODES.FORCE) {
        target.style.setProperty('touch-action', 'auto');
        target.style.setProperty('overscroll-behavior', 'auto');
        target.classList.add('touch-scroll-force');
        target.classList.remove('touch-scroll-lock');
        return;
      }

      target.style.removeProperty('touch-action');
      target.style.removeProperty('overscroll-behavior');
      target.classList.remove('touch-scroll-lock');
      target.classList.remove('touch-scroll-force');
    }

    applyPageMode(pageElement) {
      const mode = this.resolvePageScrollBehavior(pageElement);
      this.pageMode = mode;
      if (!this.hasActiveContacts) {
        this.applyScrollBehavior(mode);
      }
    }

    resolvePageScrollBehavior(pageElement) {
      if (!pageElement) {
        return this.defaultMode;
      }
      const dataset = pageElement.dataset || {};
      if (typeof dataset.scrollMode === 'string') {
        return normalizeScrollBehaviorValue(dataset.scrollMode);
      }
      if (typeof dataset.scrollBehavior === 'string') {
        return normalizeScrollBehaviorValue(dataset.scrollBehavior);
      }
      if (pageElement.hasAttribute && pageElement.hasAttribute('data-force-scroll')) {
        return SCROLL_BEHAVIOR_MODES.FORCE;
      }
      return this.defaultMode;
    }

    updateScrollBehavior() {
      if (this.hasActiveContacts) {
        this.applyScrollBehavior(SCROLL_BEHAVIOR_MODES.LOCK);
        return;
      }
      this.applyScrollBehavior(this.pageMode || this.defaultMode);
    }

    registerTouchList(touchList) {
      if (!touchList) {
        return;
      }
      const now = Date.now();
      for (let index = 0; index < touchList.length; index += 1) {
        const touch = touchList[index];
        if (touch && Number.isFinite(touch.identifier)) {
          this.activeTouches.set(touch.identifier, now);
        }
      }
    }

    unregisterTouchList(touchList) {
      if (!touchList) {
        return;
      }
      for (let index = 0; index < touchList.length; index += 1) {
        const touch = touchList[index];
        if (touch && Number.isFinite(touch.identifier)) {
          this.activeTouches.delete(touch.identifier);
        }
      }
    }

    handleTouchStart(event) {
      this.cancelPendingUnlock();
      this.registerTouchList(event?.changedTouches || event?.touches);
      this.updateScrollBehavior();
    }

    handleTouchEnd(event) {
      this.unregisterTouchList(event?.changedTouches || event?.touches);
      if (this.hasActiveContacts) {
        this.scheduleUnlockCheck();
        return;
      }
      this.updateScrollBehavior();
    }

    handlePointerStart(event) {
      if (!event || event.pointerType !== 'touch') {
        return;
      }
      this.cancelPendingUnlock();
      if (Number.isFinite(event.pointerId)) {
        this.activePointerIds.set(event.pointerId, Date.now());
      }
      this.updateScrollBehavior();
    }

    handlePointerEnd(event) {
      if (!event || event.pointerType !== 'touch') {
        return;
      }
      if (Number.isFinite(event.pointerId)) {
        this.activePointerIds.delete(event.pointerId);
      } else {
        this.activePointerIds.clear();
      }
      if (this.hasActiveContacts) {
        this.scheduleUnlockCheck();
        return;
      }
      this.updateScrollBehavior();
    }

    handleVisibilityChange() {
      if (typeof document === 'undefined') {
        return;
      }
      if (document.hidden) {
        this.reset({ reapplyScrollBehavior: false });
        this.applyScrollBehavior(this.defaultMode);
        return;
      }
      this.updateScrollBehavior();
    }
  }

  if (typeof GLOBAL !== 'undefined') {
    GLOBAL.TouchInteractionManager = TouchInteractionManager;
  }
})();
