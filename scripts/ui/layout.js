const BODY_CLASS_BY_PAGE = Object.freeze({
  'view-game': 'game',
  'view-arcade': 'arcade',
  'view-arcade-hub': 'arcadeHub',
  'view-metaux': 'metaux',
  'view-wave': 'wave',
  'view-balance': 'balance',
  'view-quantum2048': 'quantum2048',
  'view-sudoku': 'sudoku'
});

function toArray(collection) {
  if (!collection) {
    return [];
  }
  if (Array.isArray(collection)) {
    return collection;
  }
  return typeof collection.forEach === 'function'
    ? Array.from(collection)
    : [];
}

export function createLayoutController(options = {}) {
  const {
    elements = {},
    isPageUnlocked = () => true,
    ensurePageReady,
    handlePageDisplayed,
    handleManualPageVisibilityChange,
    handlePageShownAfterEffects,
    updateFrenzyIndicators,
    manualPages = [],
    handleVisibilityHidden,
    handleVisibilityVisible
  } = options;

  const manualPageSet = new Set(manualPages);
  let activePageId = null;

  function resolveTargetPageId(requestedPageId) {
    const normalized = typeof requestedPageId === 'string'
      ? requestedPageId.trim()
      : '';
    if (!normalized) {
      return null;
    }
    if (isPageUnlocked(normalized)) {
      return normalized;
    }
    if (normalized !== 'game' && isPageUnlocked('game')) {
      return 'game';
    }
    return isPageUnlocked(normalized) ? normalized : null;
  }

  function updateDomForPage(pageId) {
    const pages = toArray(elements.pages);
    pages.forEach(page => {
      if (!page || typeof page.id !== 'string') {
        return;
      }
      const isActive = page.id === pageId;
      page.classList.toggle('active', isActive);
      if (typeof page.toggleAttribute === 'function') {
        page.toggleAttribute('hidden', !isActive);
      }
    });

    const navButtons = toArray(elements.navButtons);
    navButtons.forEach(button => {
      if (!button) {
        return;
      }
      const target = typeof button.dataset?.target === 'string'
        ? button.dataset.target
        : '';
      button.classList.toggle('active', target === pageId);
    });

    if (typeof document === 'undefined') {
      return;
    }

    const body = document.body;
    if (!body) {
      return;
    }

    body.dataset.activePage = pageId;
    const activePageElement = document.getElementById(pageId);
    const rawGroup = activePageElement?.dataset?.pageGroup || 'clicker';
    const normalizedGroup = typeof rawGroup === 'string'
      ? rawGroup.trim().toLowerCase()
      : '';
    body.dataset.pageGroup = normalizedGroup === 'arcade' ? 'arcade' : 'clicker';

    Object.entries(BODY_CLASS_BY_PAGE).forEach(([className, expectedPageId]) => {
      body.classList.toggle(className, expectedPageId === pageId);
    });
  }

  function showPage(requestedPageId) {
    const targetPageId = resolveTargetPageId(requestedPageId);
    if (!targetPageId) {
      return null;
    }

    const now = typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Date.now();

    if (typeof ensurePageReady === 'function') {
      ensurePageReady(targetPageId);
    }

    updateDomForPage(targetPageId);
    activePageId = targetPageId;

    if (typeof handlePageDisplayed === 'function') {
      handlePageDisplayed(targetPageId, { now });
    }

    const docHidden = typeof document !== 'undefined' && Boolean(document.hidden);
    const manualActive = manualPageSet.has(targetPageId) && !docHidden;
    if (typeof handleManualPageVisibilityChange === 'function') {
      handleManualPageVisibilityChange({
        active: manualActive,
        pageId: targetPageId,
        now
      });
    }

    if (typeof handlePageShownAfterEffects === 'function') {
      handlePageShownAfterEffects(targetPageId, { now });
    }

    if (typeof updateFrenzyIndicators === 'function') {
      updateFrenzyIndicators(now);
    }

    return targetPageId;
  }

  function bindNavigationButtons() {
    const navButtons = toArray(elements.navButtons);
    navButtons.forEach(button => {
      if (!button || typeof button.addEventListener !== 'function') {
        return;
      }
      button.addEventListener('click', () => {
        const target = typeof button.dataset?.target === 'string'
          ? button.dataset.target
          : '';
        if (!target) {
          return;
        }
        showPage(target);
      });
    });
  }

  function syncInitialPage() {
    if (typeof document === 'undefined') {
      return;
    }
    const initial = document.querySelector('.page.active')
      || toArray(elements.pages)[0]
      || null;
    if (initial && typeof initial.id === 'string' && initial.id) {
      showPage(initial.id);
      return;
    }
    document.body?.classList?.remove('view-game');
  }

  function handleVisibilityChange(hidden) {
    const now = typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Date.now();
    const pageId = activePageId
      || (typeof document !== 'undefined' ? document.body?.dataset?.activePage : null)
      || null;
    if (!pageId) {
      return;
    }

    if (hidden) {
      if (typeof handleManualPageVisibilityChange === 'function') {
        handleManualPageVisibilityChange({ active: false, pageId, now });
      }
      if (typeof handleVisibilityHidden === 'function') {
        handleVisibilityHidden(pageId, { now });
      }
      return;
    }

    if (typeof handleVisibilityVisible === 'function') {
      handleVisibilityVisible(pageId, { now });
    }
  }

  function init() {
    bindNavigationButtons();
    syncInitialPage();
  }

  return {
    init,
    showPage,
    bindNavigationButtons,
    syncInitialPage,
    handleVisibilityChange,
    getActivePageId: () => activePageId
  };
}

export function initializeLayout(options) {
  const controller = createLayoutController(options);
  if (controller && typeof controller.init === 'function') {
    controller.init();
  }
  return controller;
}

export { BODY_CLASS_BY_PAGE };
