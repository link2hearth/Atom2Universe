function normalizeStarCount(value, fallback) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.max(1, Math.floor(numeric));
  }
  const fallbackNumeric = Number(fallback);
  if (Number.isFinite(fallbackNumeric) && fallbackNumeric > 0) {
    return Math.max(1, Math.floor(fallbackNumeric));
  }
  return 1;
}

function randomBetween(min, max) {
  const lower = Number(min);
  const upper = Number(max);
  const hasLower = Number.isFinite(lower);
  const hasUpper = Number.isFinite(upper);
  if (!hasLower && !hasUpper) {
    return Math.random();
  }
  if (!hasLower) {
    return hasUpper ? upper : Math.random();
  }
  if (!hasUpper) {
    return lower;
  }
  if (upper <= lower) {
    return lower;
  }
  return lower + Math.random() * (upper - lower);
}

function resolveRange(range, fallbackMin, fallbackMax) {
  const minCandidate = Number(range?.min);
  const maxCandidate = Number(range?.max);
  const min = Number.isFinite(minCandidate) ? minCandidate : fallbackMin;
  let max = Number.isFinite(maxCandidate) ? maxCandidate : fallbackMax;
  if (max < min) {
    max = min;
  }
  return { min, max };
}

const STARFIELD_CONFIG = CONFIG.presentation?.starfield ?? {};
const STARFIELD_DEFAULT_COUNT = normalizeStarCount(STARFIELD_CONFIG.starCount, 60);
const STARFIELD_ECO_COUNT = normalizeStarCount(
  STARFIELD_CONFIG.ecoStarCount,
  Math.max(8, Math.round(STARFIELD_DEFAULT_COUNT / 2.5))
);
const STARFIELD_MODE_SETTINGS = Object.freeze({
  fluid: Object.freeze({
    count: STARFIELD_DEFAULT_COUNT,
    animated: true,
    scale: Object.freeze({ min: 0.6, max: 2.3 }),
    opacity: Object.freeze({ min: 0.26, max: 0.8 })
  }),
  eco: Object.freeze({
    count: STARFIELD_ECO_COUNT,
    animated: false,
    scale: Object.freeze({ min: 0.55, max: 1.2 }),
    opacity: Object.freeze({ min: 0.3, max: 0.6 })
  })
});
let starfieldInitializedForMode = null;

const NAVIGATION_CONFIG =
  typeof GAME_CONFIG !== 'undefined' && GAME_CONFIG && typeof GAME_CONFIG.navigation === 'object'
    ? GAME_CONFIG.navigation
    : null;

const NAV_POINTER_CLICK_SUPPRESSION_MS = (() => {
  const raw = Number(NAVIGATION_CONFIG?.pointerClickSuppressMs);
  if (Number.isFinite(raw) && raw >= 0) {
    return Math.floor(raw);
  }
  return 350;
})();

let lastPointerNavAt = 0;

function getNavigationNowMs() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

function shouldSuppressNavClick() {
  if (NAV_POINTER_CLICK_SUPPRESSION_MS <= 0) {
    return false;
  }
  const elapsed = getNavigationNowMs() - lastPointerNavAt;
  return elapsed >= 0 && elapsed <= NAV_POINTER_CLICK_SUPPRESSION_MS;
}

function suppressPointerNavigationClick(event) {
  if (!shouldSuppressNavClick()) {
    return false;
  }
  if (event?.defaultPrevented) {
    return true;
  }
  if (typeof event?.preventDefault === 'function') {
    event.preventDefault();
  }
  if (typeof event?.stopPropagation === 'function') {
    event.stopPropagation();
  }
  return true;
}

function handlePageNavigation(target) {
  if (!target) {
    return;
  }
  if (!isPageUnlocked(target)) {
    return;
  }
  showPage(target);
}

function bindPageNavigation(element, resolveTarget) {
  if (!element) {
    return;
  }
  const resolve = typeof resolveTarget === 'function' ? resolveTarget : () => null;
  const handleActivation = () => {
    const target = resolve();
    handlePageNavigation(target);
  };
  element.addEventListener('pointerup', event => {
    if (event.pointerType === 'mouse' && typeof event.button === 'number' && event.button !== 0) {
      return;
    }
    if (typeof event?.preventDefault === 'function') {
      event.preventDefault();
    }
    if (typeof event?.stopPropagation === 'function') {
      event.stopPropagation();
    }
    lastPointerNavAt = getNavigationNowMs();
    handleActivation();
  });
  element.addEventListener('click', () => {
    if (shouldSuppressNavClick()) return;
    handleActivation();
  });
}

function resolveStarfieldMode(modeId) {
  if (typeof modeId === 'string') {
    const normalized = modeId.trim();
    if (normalized && Object.prototype.hasOwnProperty.call(STARFIELD_MODE_SETTINGS, normalized)) {
      return normalized;
    }
  }
  if (Object.prototype.hasOwnProperty.call(STARFIELD_MODE_SETTINGS, PERFORMANCE_MODE_DEFAULT_ID)) {
    return PERFORMANCE_MODE_DEFAULT_ID;
  }
  return 'fluid';
}

function getStarfieldSettings(modeId) {
  const resolved = resolveStarfieldMode(modeId);
  return {
    id: resolved,
    settings: STARFIELD_MODE_SETTINGS[resolved]
  };
}

function initStarfield(modeId = performanceModeState?.id ?? PERFORMANCE_MODE_DEFAULT_ID) {
  if (!elements.starfield) return;
  const { id: resolvedMode, settings } = getStarfieldSettings(modeId);
  if (!settings) return;
  const fragment = document.createDocumentFragment();
  const { min: scaleMin, max: scaleMax } = resolveRange(settings.scale, 0.6, 2.3);
  const { min: opacityMin, max: opacityMax } = resolveRange(settings.opacity, 0.26, 0.54);
  const count = Math.max(0, Math.floor(settings.count || 0));
  for (let i = 0; i < count; i += 1) {
    const star = document.createElement('span');
    star.className = 'starfield__star';
    star.style.left = `${Math.random() * 100}%`;
    star.style.top = `${Math.random() * 100}%`;
    star.style.setProperty('--star-scale', randomBetween(scaleMin, scaleMax).toFixed(2));
    star.style.setProperty('--star-opacity', randomBetween(opacityMin, opacityMax).toFixed(2));
    if (settings.animated) {
      const duration = randomBetween(4, 10);
      star.style.animationDuration = `${duration.toFixed(2)}s`;
      star.style.animationDelay = `-${randomBetween(0, duration).toFixed(2)}s`;
    }
    fragment.appendChild(star);
  }
  if (typeof elements.starfield.replaceChildren === 'function') {
    elements.starfield.replaceChildren(fragment);
  } else {
    elements.starfield.textContent = '';
    elements.starfield.appendChild(fragment);
  }
  elements.starfield.dataset.starfieldMode = settings.animated ? 'animated' : 'static';
  starfieldInitializedForMode = resolvedMode;
}

function getPreferredMusicPage() {
  const stored = readStoredMusicPagePreference();
  const preferred = stored === 'radio' || stored === 'midi' ? stored : 'midi';
  if (preferred === 'midi' && !isMusicModuleEnabled()) {
    return 'radio';
  }
  return preferred;
}

function updateMusicTabs(activePageId) {
  if (!elements?.musicTabs?.length) {
    return;
  }
  const isMusicPage = activePageId === 'radio' || activePageId === 'midi';
  elements.musicTabs.forEach(tab => {
    const target = tab.dataset.target;
    const isActive = isMusicPage && target === activePageId;
    tab.classList.toggle('music-tab--active', isActive);
    tab.setAttribute('aria-selected', isActive ? 'true' : 'false');
    tab.tabIndex = isMusicPage ? '0' : '-1';
  });
}

function activateArcadePageAfterLoad(pageId) {
  if (document.body?.dataset.activePage !== pageId) {
    return;
  }
  if (pageId === 'arcade' && particulesGame) {
    particulesGame.onEnter();
  }
  if (pageId === 'wave') {
    ensureWaveGame();
    waveGame?.onEnter();
  }
  if (pageId === 'bigger') {
    ensureBiggerGame();
    biggerGame?.onEnter();
  }
  if (pageId === 'balance') {
    ensureBalanceGame();
    balanceGame?.onEnter();
  }
  if (pageId === 'quantum2048') {
    ensureQuantum2048Game();
    quantum2048Game?.onEnter();
  }
  if (pageId === 'escape') {
    ensureEscapeGame();
  }
  if (pageId === 'starBridges') {
    const starBridges = ensureStarBridgesGame();
    starBridges?.onEnter?.();
  }
  if (pageId === 'starsWar') {
    const starsWar = ensureStarsWarGame();
    starsWar?.onEnter?.();
  }
  if (pageId === 'jumpingCat') {
    const jumpingCat = ensureJumpingCatGame();
    jumpingCat?.onEnter?.();
  }
  if (pageId === 'reflex') {
    const reflex = ensureReflexGame();
    reflex?.onEnter?.();
  }
  if (pageId === 'pipeTap') {
    const pipeTap = ensurePipeTapGame();
    pipeTap?.onEnter?.();
  }
  if (pageId === 'colorStack') {
    const colorStack = ensureColorStackGame();
    colorStack?.onEnter?.();
  }
  if (pageId === 'hex') {
    const hex = ensureHexGame();
    hex?.onEnter?.();
  }
  if (pageId === 'motocross') {
    const motocross = ensureMotocrossGame();
    motocross?.onEnter?.();
  }
  if (pageId === 'twins') {
    const twins = ensureTwinsGame();
    twins?.onEnter?.();
  }
  if (pageId === 'sokoban') {
    const sokoban = ensureSokobanGame();
    sokoban?.onEnter?.();
  }
  if (pageId === 'taquin') {
    const taquin = ensureTaquinGame();
    taquin?.onEnter?.();
  }
  if (pageId === 'link') {
    const link = ensureLinkGame();
    link?.onEnter?.();
  }
  if (pageId === 'lightsOut') {
    const lightsOut = ensureLightsOutGame();
    lightsOut?.onEnter?.();
  }
  if (pageId === 'gameOfLife') {
    const gameOfLife = ensureGameOfLifeGame();
    gameOfLife?.onEnter?.();
  }
}

function requestArcadeScriptForPage(pageId) {
  if (typeof requestArcadeScript !== 'function') {
    return;
  }
  requestArcadeScript(pageId, {
    onLoaded: () => activateArcadePageAfterLoad(pageId)
  });
}

function showPage(pageId) {
  if (pageId === 'midi' && !isMusicModuleEnabled()) {
    if (document.body?.dataset?.activePage !== 'options') {
      showPage('options');
    }
    return;
  }
  if (pageId !== 'notes' && notesIsFullscreen) {
    setNotesFullscreen(false);
  }
  if (!isPageUnlocked(pageId)) {
    if (pageId !== 'game') {
      showPage('game');
    }
    return;
  }
  const previousPageId = document.body?.dataset?.activePage;
  const previousPageElement = previousPageId ? document.getElementById(previousPageId) : null;
  const previousPageGroup = previousPageElement?.dataset?.pageGroup || '';
  const leavingArcadePage = previousPageId
    && previousPageId !== pageId
    && typeof previousPageGroup === 'string'
    && previousPageGroup.trim().toLowerCase() === 'arcade';
  const isMusicPage = pageId === 'radio' || pageId === 'midi';
  const navActiveTarget = isMusicPage ? 'radio' : pageId;
  forceUnlockScrollSafe({ reapplyScrollBehavior: false });
  const now = performance.now();
  if (pageId === 'wave') {
    ensureWaveGame();
  }
  if (pageId === 'bigger') {
    ensureBiggerGame();
  }
  if (pageId === 'balance') {
    ensureBalanceGame();
  }
  if (pageId === 'quantum2048') {
    ensureQuantum2048Game();
  }
  if (pageId === 'escape') {
    ensureEscapeGame();
  }
  if (pageId === 'starBridges') {
    ensureStarBridgesGame();
  }
  if (pageId === 'starsWar') {
    ensureStarsWarGame();
  }
  if (pageId === 'jumpingCat') {
    ensureJumpingCatGame();
  }
  if (pageId === 'reflex') {
    ensureReflexGame();
  }
  if (pageId === 'pipeTap') {
    ensurePipeTapGame();
  }
  if (pageId === 'colorStack') {
    ensureColorStackGame();
  }
  if (pageId === 'hex') {
    ensureHexGame();
  }
  if (pageId === 'sokoban') {
    ensureSokobanGame();
  }
  if (pageId === 'taquin') {
    ensureTaquinGame();
  }
  if (pageId === 'link') {
    ensureLinkGame();
  }
  if (pageId === 'lightsOut') {
    ensureLightsOutGame();
  }
  elements.pages.forEach(page => {
    const isActive = page.id === pageId;
    page.classList.toggle('active', isActive);
    page.toggleAttribute('hidden', !isActive);
  });
  elements.navButtons.forEach(btn => {
    const target = btn.dataset.target;
    const isActiveNav = target === navActiveTarget;
    btn.classList.toggle('active', isActiveNav);
  });
  document.body.dataset.activePage = pageId;
  if (isMusicPage) {
    writeStoredMusicPagePreference(pageId);
  }
  updateMusicTabs(pageId);
  requestArcadeScriptForPage(pageId);
  if (pageId === 'info') {
    updateDevKitUI();
  }
  const activePageElement = typeof document !== 'undefined'
    ? document.getElementById(pageId)
    : null;
  const rawPageGroup = activePageElement?.dataset?.pageGroup || 'clicker';
  const normalizedPageGroup = typeof rawPageGroup === 'string'
    ? rawPageGroup.trim().toLowerCase()
    : '';
  const activePageGroup = normalizedPageGroup === 'arcade'
    ? 'arcade'
    : 'clicker';
  document.body.dataset.pageGroup = activePageGroup;
  applyActivePageScrollBehavior(activePageElement);
  if (leavingArcadePage) {
    const scrollLockManager = typeof globalThis !== 'undefined' ? globalThis : null;
    scrollLockManager?.resetTouchTrackingState?.({ reapplyScrollBehavior: false });
    forceUnlockScrollSafe({ reapplyScrollBehavior: true });
  }
  document.body.classList.toggle('view-game', pageId === 'game');
  document.body.classList.toggle('view-arcade', pageId === 'arcade');
  document.body.classList.toggle('view-arcade-hub', pageId === 'arcadeHub');
  document.body.classList.toggle('view-metaux', pageId === 'metaux');
  document.body.classList.toggle('view-bigger', pageId === 'bigger');
  document.body.classList.toggle('view-wave', pageId === 'wave');
  document.body.classList.toggle('view-balance', pageId === 'balance');
  document.body.classList.toggle('view-quantum2048', pageId === 'quantum2048');
  document.body.classList.toggle('view-star-bridges', pageId === 'starBridges');
  document.body.classList.toggle('view-stars-war', pageId === 'starsWar');
  document.body.classList.toggle('view-jumping-cat', pageId === 'jumpingCat');
  document.body.classList.toggle('view-reflex', pageId === 'reflex');
  document.body.classList.toggle('view-pipe-tap', pageId === 'pipeTap');
  document.body.classList.toggle('view-color-stack', pageId === 'colorStack');
  document.body.classList.toggle('view-motocross', pageId === 'motocross');
  document.body.classList.toggle('view-hex', pageId === 'hex');
  document.body.classList.toggle('view-twins', pageId === 'twins');
  document.body.classList.toggle('view-sokoban', pageId === 'sokoban');
  document.body.classList.toggle('view-taquin', pageId === 'taquin');
  document.body.classList.toggle('view-sudoku', pageId === 'sudoku');
  document.body.classList.toggle('view-lights-out', pageId === 'lightsOut');
  document.body.classList.toggle('view-link', pageId === 'link');
  document.body.classList.toggle('view-game-of-life', pageId === 'gameOfLife');
  document.body.classList.toggle('view-news', pageId === 'news');
  document.body.classList.toggle('view-images', pageId === 'images');
  document.body.classList.toggle('view-radio', isMusicPage);
  document.body.classList.toggle('view-notes', pageId === 'notes');
  applyBackgroundImage();
  if (pageId === 'game') {
    randomizeAtomButtonImage();
  }
  if (pageId === 'metaux') {
    initMetauxGame();
  }
  if (particulesGame) {
    if (pageId === 'arcade') {
      particulesGame.onEnter();
    } else {
      particulesGame.onLeave();
    }
  }
  if (metauxGame) {
    if (pageId === 'metaux') {
      metauxGame.onEnter();
    } else {
      metauxGame.onLeave();
    }
  }
  if (biggerGame) {
    if (pageId === 'bigger') {
      biggerGame.onEnter();
    } else {
      biggerGame.onLeave();
    }
  }
  if (waveGame) {
    if (pageId === 'wave') {
      waveGame.onEnter();
    } else {
      waveGame.onLeave();
    }
  }
  if (balanceGame) {
    if (pageId === 'balance') {
      balanceGame.onEnter();
    } else {
      balanceGame.onLeave();
    }
  }
  if (pageId === 'news') {
    renderNewsList();
  }
  if (pageId === 'radio') {
    ensureRadioFiltersLoaded();
  }
  if (pageId === 'notes' && !notesItems.length) {
    refreshNotesList({ silent: true });
  }
  renderNewsTicker();
  if (quantum2048Game) {
    if (pageId === 'quantum2048') {
      quantum2048Game.onEnter();
    } else {
      quantum2048Game.onLeave();
    }
  }
  const starBridges = ensureStarBridgesGame();
  if (starBridges) {
    if (pageId === 'starBridges') {
      starBridges.onEnter?.();
    } else {
      starBridges.onLeave?.();
    }
  }
  const starsWar = ensureStarsWarGame();
  if (starsWar) {
    if (pageId === 'starsWar') {
      starsWar.onEnter?.();
    } else {
      starsWar.onLeave?.();
    }
  }
  const jumpingCat = ensureJumpingCatGame();
  if (jumpingCat) {
    if (pageId === 'jumpingCat') {
      jumpingCat.onEnter?.();
    } else {
      jumpingCat.onLeave?.();
    }
  }
  const reflex = ensureReflexGame();
  if (reflex) {
    if (pageId === 'reflex') {
      reflex.onEnter?.();
    } else {
      reflex.onLeave?.();
    }
  }
  const pipeTap = ensurePipeTapGame();
  if (pipeTap) {
    if (pageId === 'pipeTap') {
      pipeTap.onEnter?.();
    } else {
      pipeTap.onLeave?.();
    }
  }
  const colorStack = ensureColorStackGame();
  if (colorStack) {
    if (pageId === 'colorStack') {
      colorStack.onEnter?.();
    } else {
      colorStack.onLeave?.();
    }
  }
  const hex = ensureHexGame();
  if (hex) {
    if (pageId === 'hex') {
      hex.onEnter?.();
    } else {
      hex.onLeave?.();
    }
  }
  const motocross = ensureMotocrossGame();
  if (motocross) {
    if (pageId === 'motocross') {
      motocross.onEnter?.();
    } else {
      motocross.onLeave?.();
    }
  }
  const twins = ensureTwinsGame();
  if (twins) {
    if (pageId === 'twins') {
      twins.onEnter?.();
    } else {
      twins.onLeave?.();
    }
  }
  const sokoban = ensureSokobanGame();
  if (sokoban) {
    if (pageId === 'sokoban') {
      sokoban.onEnter?.();
    } else {
      sokoban.onLeave?.();
    }
  }
  const taquin = ensureTaquinGame();
  if (taquin) {
    if (pageId === 'taquin') {
      taquin.onEnter?.();
    } else {
      taquin.onLeave?.();
    }
  }
  const link = ensureLinkGame();
  if (link) {
    if (pageId === 'link') {
      link.onEnter?.();
    } else {
      link.onLeave?.();
    }
  }
  const lightsOut = ensureLightsOutGame();
  if (lightsOut) {
    if (pageId === 'lightsOut') {
      lightsOut.onEnter?.();
    } else {
      lightsOut.onLeave?.();
    }
  }
  const gameOfLife = ensureGameOfLifeGame();
  if (gameOfLife) {
    if (pageId === 'gameOfLife') {
      gameOfLife.onEnter?.();
    } else {
      gameOfLife.onLeave?.();
    }
  }
  const manualPageActive = pageId === 'game' || pageId === 'wave';
  if (manualPageActive && (typeof document === 'undefined' || !document.hidden)) {
    gamePageVisibleSince = now;
  } else {
    gamePageVisibleSince = null;
  }
  if (pageId === 'gacha') {
    const weightsUpdated = refreshGachaRarities(new Date());
    if (weightsUpdated) {
      rebuildGachaPools();
      renderGachaRarityList();
    }
  }
  scheduleAutoUiScaleUpdate();
  updateFrenzyIndicators(now);
}

document.addEventListener('visibilitychange', () => {
  if (typeof document === 'undefined') {
    return;
  }
  const activePage = document.body?.dataset.activePage;
  if (document.hidden) {
    forceUnlockScrollSafe({ reapplyScrollBehavior: false });
    hiddenSinceTimestamp = Date.now();
    gamePageVisibleSince = null;
    if (particulesGame && activePage === 'arcade') {
      particulesGame.onLeave();
    }
    if (waveGame && activePage === 'wave') {
      waveGame.onLeave();
    }
    if (quantum2048Game && activePage === 'quantum2048') {
      quantum2048Game.onLeave();
    }
    const sokoban = ensureSokobanGame();
    if (sokoban && activePage === 'sokoban') {
      sokoban.onLeave?.();
    }
    const taquin = ensureTaquinGame();
    if (taquin && activePage === 'taquin') {
      taquin.onLeave?.();
    }
    const lightsOut = ensureLightsOutGame();
    if (lightsOut && activePage === 'lightsOut') {
      lightsOut.onLeave?.();
    }
    const starBridgesGameInstance = ensureStarBridgesGame();
    if (starBridgesGameInstance && activePage === 'starBridges') {
      starBridgesGameInstance.onLeave?.();
    }
    const starsWarInstance = ensureStarsWarGame();
    if (starsWarInstance && activePage === 'starsWar') {
      starsWarInstance.onLeave?.();
    }
    const jumpingCatInstance = ensureJumpingCatGame();
    if (jumpingCatInstance && activePage === 'jumpingCat') {
      jumpingCatInstance.onLeave?.();
    }
    const reflexInstance = ensureReflexGame();
    if (reflexInstance && activePage === 'reflex') {
      reflexInstance.onLeave?.();
    }
    const pipeTap = ensurePipeTapGame();
    if (pipeTap && activePage === 'pipeTap') {
      pipeTap.onLeave?.();
    }
    const colorStack = ensureColorStackGame();
    if (colorStack && activePage === 'colorStack') {
      colorStack.onLeave?.();
    }
    const hex = ensureHexGame();
    if (hex && activePage === 'hex') {
      hex.onLeave?.();
    }
    const motocross = ensureMotocrossGame();
    if (motocross && activePage === 'motocross') {
      motocross.onLeave?.();
    }
    const twins = ensureTwinsGame();
    if (twins && activePage === 'twins') {
      twins.onLeave?.();
    }
    saveGame();
    return;
  }

  const becameVisibleAt = typeof performance !== 'undefined' ? performance.now() : null;
  if (becameVisibleAt != null) {
    lastUpdate = becameVisibleAt;
    lastUIUpdate = becameVisibleAt;
    lastSaveTime = becameVisibleAt;
  }

  const nowTimestamp = Date.now();
  const hiddenDurationSeconds = hiddenSinceTimestamp != null
    ? Math.max(0, (nowTimestamp - hiddenSinceTimestamp) / 1000)
    : 0;
  hiddenSinceTimestamp = null;
  if (hiddenDurationSeconds > 0) {
    const offlineResult = applyOfflineProgress(hiddenDurationSeconds);
    if (offlineResult.appliedSeconds > 0) {
      updateUI();
      saveGame();
    }
  }

  if (isManualClickContextActive()) {
    gamePageVisibleSince = performance.now();
    if (particulesGame && activePage === 'arcade') {
      particulesGame.onEnter();
    }
    if (activePage === 'wave') {
      ensureWaveGame();
      waveGame?.onEnter();
    }
  } else if (particulesGame && activePage === 'arcade') {
    particulesGame.onEnter();
  } else if (activePage === 'wave') {
    ensureWaveGame();
    waveGame?.onEnter();
  }

  if (activePage === 'quantum2048') {
    ensureQuantum2048Game();
    quantum2048Game?.onEnter();
  }
  if (activePage === 'starBridges') {
    const starBridgesGameInstance = ensureStarBridgesGame();
    starBridgesGameInstance?.onEnter?.();
  }
  if (activePage === 'starsWar') {
    const starsWarInstance = ensureStarsWarGame();
    starsWarInstance?.onEnter?.();
  }
  if (activePage === 'jumpingCat') {
    const jumpingCatInstance = ensureJumpingCatGame();
    jumpingCatInstance?.onEnter?.();
  }
  if (activePage === 'reflex') {
    const reflexInstance = ensureReflexGame();
    reflexInstance?.onEnter?.();
  }
  if (activePage === 'pipeTap') {
    const pipeTap = ensurePipeTapGame();
    pipeTap?.onEnter?.();
  }
  if (activePage === 'colorStack') {
    const colorStack = ensureColorStackGame();
    colorStack?.onEnter?.();
  }
  if (activePage === 'hex') {
    const hex = ensureHexGame();
    hex?.onEnter?.();
  }
  if (activePage === 'motocross') {
    const motocross = ensureMotocrossGame();
    motocross?.onEnter?.();
  }
  if (activePage === 'twins') {
    const twins = ensureTwinsGame();
    twins?.onEnter?.();
  }
  if (activePage === 'taquin') {
    const taquin = ensureTaquinGame();
    taquin?.onEnter?.();
  }
});

function bindDomEventListeners() {
  const initiallyActivePage = document.querySelector('.page.active') || elements.pages[0];
  if (initiallyActivePage) {
    showPage(initiallyActivePage.id);
  } else {
    document.body.classList.remove('view-game');
  }

  initInfoWelcomeCard();
  initInfoAchievementsCard();
  initInfoCalculationsCard();
  initInfoProgressCard();
  initInfoScoresCard();
  initInfoCharactersCard();
  initInfoCardsCard();
  initOptionCards();
  initCollectionImagesCard();
  initCollectionVideosCard();
  initCollectionBonusImagesCard();
  initCollectionBonus1ImagesCard();
  initCollectionBonus2ImagesCard();
  initCollectionBonus3ImagesCard();
  initCollectionDownloadsCard();
  if (typeof initSpecialCardOverlay === 'function') {
    initSpecialCardOverlay();
  }
  initManualBackupUi();

  if (elements.devkitOverlay) {
    elements.devkitOverlay.addEventListener('click', event => {
      if (event.target === elements.devkitOverlay) {
        closeDevKit();
      }
    });
  }

  if (elements.devkitPanel) {
    elements.devkitPanel.addEventListener('keydown', event => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeDevKit();
      }
    });
  }

  if (elements.devkitClose) {
    elements.devkitClose.addEventListener('click', event => {
      event.preventDefault();
      closeDevKit();
    });
  }

  if (elements.devkitAtomsForm) {
    elements.devkitAtomsForm.addEventListener('submit', event => {
      event.preventDefault();
      const value = elements.devkitAtomsInput ? elements.devkitAtomsInput.value : '';
      handleDevKitAtomsSubmission(value);
      if (elements.devkitAtomsInput) {
        elements.devkitAtomsInput.value = '';
      }
    });
  }

  if (elements.devkitAutoForm) {
    elements.devkitAutoForm.addEventListener('submit', event => {
      event.preventDefault();
      const value = elements.devkitAutoInput ? elements.devkitAutoInput.value : '';
      handleDevKitAutoSubmission(value);
      if (elements.devkitAutoInput) {
        elements.devkitAutoInput.value = '';
      }
    });
  }

  if (elements.devkitOfflineTimeForm) {
    elements.devkitOfflineTimeForm.addEventListener('submit', event => {
      event.preventDefault();
      const value = elements.devkitOfflineTimeInput ? elements.devkitOfflineTimeInput.value : '';
      handleDevKitOfflineAdvance(value);
      if (elements.devkitOfflineTimeInput) {
        elements.devkitOfflineTimeInput.value = '';
      }
    });
  }

  if (elements.devkitOnlineTimeForm) {
    elements.devkitOnlineTimeForm.addEventListener('submit', event => {
      event.preventDefault();
      const value = elements.devkitOnlineTimeInput ? elements.devkitOnlineTimeInput.value : '';
      handleDevKitOnlineAdvance(value);
      if (elements.devkitOnlineTimeInput) {
        elements.devkitOnlineTimeInput.value = '';
      }
    });
  }

  if (elements.devkitAutoReset) {
    elements.devkitAutoReset.addEventListener('click', event => {
      event.preventDefault();
      resetDevKitAutoBonus();
    });
  }

  if (elements.devkitTicketsForm) {
    elements.devkitTicketsForm.addEventListener('submit', event => {
      event.preventDefault();
      const value = elements.devkitTicketsInput ? elements.devkitTicketsInput.value : '';
      handleDevKitTicketSubmission(value);
      if (elements.devkitTicketsInput) {
        elements.devkitTicketsInput.value = '';
      }
    });
  }

  if (elements.devkitMach3TicketsForm) {
    elements.devkitMach3TicketsForm.addEventListener('submit', event => {
      event.preventDefault();
      const value = elements.devkitMach3TicketsInput ? elements.devkitMach3TicketsInput.value : '';
      handleDevKitMach3TicketSubmission(value);
      if (elements.devkitMach3TicketsInput) {
        elements.devkitMach3TicketsInput.value = '';
      }
    });
  }

  if (elements.devkitUnlockTrophies) {
    elements.devkitUnlockTrophies.addEventListener('click', event => {
      event.preventDefault();
      devkitUnlockAllTrophies();
    });
  }

  if (elements.devkitUnlockElements) {
    elements.devkitUnlockElements.addEventListener('click', event => {
      event.preventDefault();
      devkitUnlockAllElements();
    });
  }

  if (elements.devkitUnlockInfo) {
    elements.devkitUnlockInfo.addEventListener('click', event => {
      event.preventDefault();
      const unlocked = unlockPage('info', {
        save: true,
        announce: t('scripts.app.devkit.infoPageUnlocked')
      });
      if (!unlocked) {
        showToast(t('scripts.app.devkit.infoPageAlready'));
      }
      updateDevKitUI();
    });
  }

  if (elements.devkitToggleShop) {
    elements.devkitToggleShop.addEventListener('click', event => {
      event.preventDefault();
      toggleDevKitCheat('freeShop');
    });
  }

  if (elements.devkitToggleGacha) {
    elements.devkitToggleGacha.addEventListener('click', event => {
      event.preventDefault();
      toggleDevKitCheat('freeGacha');
    });
  }

  if (elements.holdemRestartButton) {
    elements.holdemRestartButton.addEventListener('click', event => {
      event.preventDefault();
      handleHoldemRestartRequest();
    });
  }

  if (elements.holdemBlindMultiplyButton) {
    elements.holdemBlindMultiplyButton.addEventListener('click', event => {
      event.preventDefault();
      handleHoldemBlindScaling(10);
    });
  }

  if (elements.holdemBlindDivideButton) {
    elements.holdemBlindDivideButton.addEventListener('click', event => {
      event.preventDefault();
      handleHoldemBlindScaling(0.1);
    });
  }

  document.addEventListener('keydown', event => {
    if (isDevkitFeatureEnabled() && event.key === 'F9') {
      event.preventDefault();
      toggleDevKit();
    } else if (event.key === 'Escape' && DEVKIT_STATE.isOpen) {
      event.preventDefault();
      closeDevKit();
    }
  });

  function handleMetauxSessionEnd(summary) {
    updateMetauxCreditsUI();
    if (!summary || typeof summary !== 'object') {
      return;
    }
    const elapsedMs = Number(summary.elapsedMs ?? summary.time ?? 0);
    const matches = Number(summary.matches ?? summary.matchCount ?? 0);
    const secondsEarned = Number.isFinite(elapsedMs) && elapsedMs > 0
      ? Math.max(0, Math.round(elapsedMs / 1000))
      : 0;
    const matchesEarned = Number.isFinite(matches) && matches > 0
      ? Math.max(0, Math.floor(matches))
      : 0;
    if (secondsEarned <= 0 && matchesEarned <= 0) {
      return;
    }
    const apsCrit = ensureApsCritState();
    const hadEffects = apsCrit.effects.length > 0;
    const previousMultiplier = getApsCritMultiplier(apsCrit);
    const currentRemaining = getApsCritRemainingSeconds(apsCrit);
    let chronoAdded = 0;
    let effectAdded = false;
    const currentBonus = Math.max(0, previousMultiplier - 1);
    const availableBonus = Math.max(0, METAUX_CRIT_MAX_BONUS - currentBonus);
    const matchesToGrant = Math.min(matchesEarned, availableBonus);
    let matchesGranted = 0;
    if (!hadEffects) {
      if (secondsEarned > 0 && matchesToGrant > 0) {
        apsCrit.effects.push({
          multiplierAdd: matchesToGrant,
          remainingSeconds: secondsEarned
        });
        chronoAdded = secondsEarned;
        effectAdded = true;
        matchesGranted = matchesToGrant;
      }
    } else if (matchesToGrant > 0 && currentRemaining > 0) {
      apsCrit.effects.push({
        multiplierAdd: matchesToGrant,
        remainingSeconds: currentRemaining
      });
      effectAdded = true;
      matchesGranted = matchesToGrant;
    }
    if (!effectAdded) {
      return;
    }
    apsCrit.effects = apsCrit.effects.filter(effect => {
      const remaining = Number(effect?.remainingSeconds) || 0;
      const value = Number(effect?.multiplierAdd) || 0;
      return remaining > 0 && value > 0;
    });
    if (!apsCrit.effects.length) {
      return;
    }
    const newMultiplier = getApsCritMultiplier(apsCrit);
    if (newMultiplier !== previousMultiplier) {
      recalcProduction();
    }
    updateUI();
    pulseApsCritPanel();
    const messageParts = [];
    if (chronoAdded > 0) {
      messageParts.push(t('scripts.app.metaux.chronoBonus', {
        value: formatIntegerLocalized(chronoAdded)
      }));
    }
    if (matchesGranted > 0) {
      messageParts.push(t('scripts.app.metaux.multiBonus', {
        value: formatIntegerLocalized(matchesGranted)
      }));
    }
    if (messageParts.length) {
      showToast(t('scripts.app.metaux.toast', { details: messageParts.join(' · ') }));
    }
    saveGame();
  }

  window.handleMetauxSessionEnd = handleMetauxSessionEnd;

  elements.navButtons.forEach(btn => {
    bindPageNavigation(btn, () => {
      const rawTarget = btn.dataset.target;
      if (rawTarget === 'radio') {
        return getPreferredMusicPage();
      }
      return rawTarget;
    });
  });

  if (elements.gachaPeriodicButton) {
    bindPageNavigation(elements.gachaPeriodicButton, () => 'tableau');
  }

  if (elements.musicTabs?.length) {
    elements.musicTabs.forEach(tab => {
      bindPageNavigation(tab, () => {
        const target = tab.dataset.target;
        if (target === 'radio' || target === 'midi') {
          return target;
        }
        return null;
      });
    });
  }

  if (elements.imagesCollectionButton) {
    bindPageNavigation(elements.imagesCollectionButton, () => 'collection');
  }

  if (elements.imagesRefreshButton) {
    elements.imagesRefreshButton.addEventListener('click', () => {
      fetchImageFeeds();
    });
  }
  if (elements.imagesHideButton) {
    elements.imagesHideButton.addEventListener('click', hideCurrentImage);
  }
  if (elements.imagesSourcesReset) {
    elements.imagesSourcesReset.addEventListener('click', () => {
      const availableSources = getAvailableImageSources();
      imageFeedEnabledSources = new Set(availableSources.map(source => source.id));
      writeStoredImageSources(imageFeedEnabledSources);
      renderImageSources();
      refreshImagesDisplay({ skipStatus: true });
      setImagesStatus('index.sections.images.status.idle', 'Click refresh to load images.');
    });
  }
  if (elements.imagesPrevButton) {
    elements.imagesPrevButton.addEventListener('click', () => {
      const total = imageFeedVisibleItems.length;
      if (!total) {
        return;
      }
      setImagesCurrentIndex((imageFeedCurrentIndex - 1 + total) % total);
      refreshImagesDisplay({ skipStatus: true });
    });
  }
  if (elements.imagesNextButton) {
    elements.imagesNextButton.addEventListener('click', () => {
      const total = imageFeedVisibleItems.length;
      if (!total) {
        return;
      }
      setImagesCurrentIndex((imageFeedCurrentIndex + 1) % total);
      refreshImagesDisplay({ skipStatus: true });
    });
  }
  if (elements.imagesOpenButton) {
    elements.imagesOpenButton.addEventListener('click', openCurrentImage);
  }
  if (elements.imagesDownloadButton) {
    elements.imagesDownloadButton.addEventListener('click', downloadCurrentImage);
  }
  if (elements.imagesActiveImage) {
    elements.imagesActiveImage.addEventListener('click', openActiveImageFullscreen);
  }
  if (elements.imagesLightboxClose) {
    elements.imagesLightboxClose.addEventListener('click', closeImageFullscreen);
  }
  if (elements.imagesLightbox) {
    elements.imagesLightbox.addEventListener('click', event => {
      const target = event?.target;
      if (target === elements.imagesLightbox || target?.classList?.contains('images-lightbox__backdrop')) {
        closeImageFullscreen();
      }
    });
  }
  if (elements.collectionDownloadsLightboxClose) {
    elements.collectionDownloadsLightboxClose.addEventListener('click', closeCollectionDownloadsFullscreen);
  }
  if (elements.collectionDownloadsLightbox) {
    elements.collectionDownloadsLightbox.addEventListener('click', event => {
      const target = event?.target;
      if (target === elements.collectionDownloadsLightbox
        || target?.classList?.contains('images-lightbox__backdrop')) {
        closeCollectionDownloadsFullscreen();
      }
    });
    elements.collectionDownloadsLightbox.addEventListener('touchstart', handleCollectionDownloadsTouchStart, {
      passive: true
    });
    elements.collectionDownloadsLightbox.addEventListener('touchend', handleCollectionDownloadsTouchEnd, {
      passive: true
    });
  }
  if (elements.collectionDownloadsPrev) {
    elements.collectionDownloadsPrev.addEventListener('click', showPreviousCollectionDownload);
  }
  if (elements.collectionDownloadsNext) {
    elements.collectionDownloadsNext.addEventListener('click', showNextCollectionDownload);
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('keydown', event => {
      if (event?.key === 'Escape') {
        closeImageFullscreen();
        closeCollectionDownloadsFullscreen();
        return;
      }
      if (event?.key === 'ArrowRight' && isCollectionDownloadsLightboxOpen()) {
        showNextCollectionDownload();
        return;
      }
      if (event?.key === 'ArrowLeft' && isCollectionDownloadsLightboxOpen()) {
        showPreviousCollectionDownload();
      }
    });
  }

  if (elements.newsSearchButton) {
    elements.newsSearchButton.addEventListener('click', () => {
      handleNewsSearchSubmit();
    });
  }
  if (elements.newsSearchInput) {
    elements.newsSearchInput.addEventListener('keydown', event => {
      if (event && event.key === 'Enter') {
        event.preventDefault();
        handleNewsSearchSubmit();
      }
    });
  }
  if (elements.newsClearSearchButton) {
    elements.newsClearSearchButton.addEventListener('click', handleNewsClearSearch);
  }
  if (elements.newsRefreshButton) {
    elements.newsRefreshButton.addEventListener('click', handleNewsRefresh);
  }
  if (elements.newsRestoreHiddenButton) {
    elements.newsRestoreHiddenButton.addEventListener('click', restoreHiddenNewsStories);
  }
  if (elements.newsBannedWordsSave) {
    elements.newsBannedWordsSave.addEventListener('click', handleNewsBannedWordsSave);
  }
  if (elements.newsTickerOpenButton) {
    elements.newsTickerOpenButton.addEventListener('click', () => {
      newsHighlightedStoryId = newsTickerActiveStoryId;
      showPage('news');
      applyNewsHighlight({ scroll: true });
    });
  }
  if (elements.newsTickerHideButton) {
    elements.newsTickerHideButton.addEventListener('click', () => {
      if (!newsTickerActiveStoryId) {
        return;
      }
      hideNewsStory(newsTickerActiveStoryId);
    });
  }

  if (elements.radioSearchForm) {
    elements.radioSearchForm.addEventListener('submit', handleRadioSearchSubmit);
  }
  if (elements.radioResetButton) {
    elements.radioResetButton.addEventListener('click', handleRadioReset);
  }
  if (elements.radioResults) {
    elements.radioResults.addEventListener('click', handleRadioResultsClick);
  }
  if (elements.radioFavoritesList) {
    elements.radioFavoritesList.addEventListener('click', handleRadioFavoritesClick);
  }
  if (elements.radioFavoritesManualForm) {
    elements.radioFavoritesManualForm.addEventListener('submit', handleManualRadioFavoriteFormSubmit);
  }
  if (elements.radioFavoritesManualCancel) {
    elements.radioFavoritesManualCancel.addEventListener('click', handleManualRadioFavoriteCancel);
  }
  if (elements.radioFavoritesAddUrlButton) {
    elements.radioFavoritesAddUrlButton.addEventListener('click', handleManualRadioFavorite);
  }
  if (elements.radioPlayButton) {
    elements.radioPlayButton.addEventListener('click', () => playSelectedRadioStation());
  }
  if (elements.radioRecordButton) {
    elements.radioRecordButton.addEventListener('click', startRadioRecording);
  }
  if (elements.radioRecordStopButton) {
    elements.radioRecordStopButton.addEventListener('click', () => stopRadioRecording());
  }
  if (elements.radioPauseButton) {
    elements.radioPauseButton.addEventListener('click', pauseRadioPlayback);
  }
  if (elements.radioStopButton) {
    elements.radioStopButton.addEventListener('click', stopRadioPlayback);
  }
  if (elements.radioReloadButton) {
    elements.radioReloadButton.addEventListener('click', reloadRadioStream);
  }
  if (elements.radioFavoriteButton) {
    elements.radioFavoriteButton.addEventListener('click', () => {
      if (radioSelectedStation) {
        toggleRadioFavorite(radioSelectedStation);
      }
    });
  }

  if (elements.notesNewButton) {
    elements.notesNewButton.addEventListener('click', startNewNote);
  }
  if (elements.notesRefreshButton) {
    elements.notesRefreshButton.addEventListener('click', () => refreshNotesList());
  }
  if (elements.notesPicker) {
    elements.notesPicker.addEventListener('change', event => {
      const selectedName = event?.target?.value || '';
      const selectedNote = notesItems.find(item => item?.name === selectedName);
      if (selectedNote) {
        openNote(selectedNote);
      } else if (!selectedName) {
        startNewNote();
      }
      updateNotesControlsAvailability();
    });
  }
  if (elements.notesDeleteButton) {
    elements.notesDeleteButton.addEventListener('click', () => {
      const selectedName = elements?.notesPicker?.value;
      const target = notesItems.find(item => item?.name === selectedName);
      if (target) {
        handleDeleteNote(target);
      }
    });
  }
  if (elements.notesSaveButton) {
    elements.notesSaveButton.addEventListener('click', event => {
      event.preventDefault();
      saveActiveNote();
    });
  }
  if (elements.notesCancelButton) {
    elements.notesCancelButton.addEventListener('click', event => {
      if (event) {
        event.preventDefault();
      }
      if (notesEditingNote) {
        openNote(notesEditingNote);
      } else {
        startNewNote();
      }
    });
  }
  if (elements.notesFullscreenButton) {
    elements.notesFullscreenButton.addEventListener('click', toggleNotesFullscreen);
  }
  if (elements.notesExitFullscreenButton) {
    elements.notesExitFullscreenButton.addEventListener('click', () => setNotesFullscreen(false));
  }
  ['notesFontSelect', 'notesFontSize', 'notesTextColor', 'notesBackgroundColor']
    .forEach(key => {
      if (elements[key]) {
        elements[key].addEventListener('change', handleNotesStyleChange);
      }
    });

  if (elements.arcadeHubCards?.length) {
    elements.arcadeHubCards.forEach(card => {
      initializeArcadeHubCard(card);
      card.addEventListener('click', event => {
        if (
          card.dataset.arcadeDragSuppressClick === 'true'
          || card.dataset.arcadeDragging === 'true'
          || card.dataset.arcadeDragPending === 'true'
        ) {
          if (event) {
            event.preventDefault();
            event.stopPropagation();
          }
          delete card.dataset.arcadeDragSuppressClick;
          return;
        }
        if (event && event.target && event.target.closest('.arcade-hub-card__toggle')) {
          return;
        }
        if (event?.defaultPrevented) {
          return;
        }
        activateArcadeHubCard(card);
      });
      card.addEventListener('keydown', event => {
        if (!event) {
          return;
        }
        if (event.target && event.target.closest('.arcade-hub-card__toggle')) {
          return;
        }
        const key = event.key;
        if (key === 'Enter' || key === ' ') {
          event.preventDefault();
          activateArcadeHubCard(card);
        }
      });
    });
  }

  if (elements.metauxOpenButton) {
    elements.metauxOpenButton.addEventListener('click', () => {
      showPage('metaux');
    });
  }

  if (elements.metauxReturnButton) {
    elements.metauxReturnButton.addEventListener('click', () => {
      showPage('game');
    });
  }

  if (elements.metauxNewGameButton) {
    elements.metauxNewGameButton.addEventListener('click', () => {
      initMetauxGame();
      const credits = getMetauxCreditCount();
      if (!metauxGame) {
        showToast(t('scripts.app.metaux.unavailable'));
        return;
      }
      if (isMetauxSessionRunning()) {
        showToast(t('scripts.app.metaux.gameInProgress'));
        updateMetauxCreditsUI();
        return;
      }
      if (credits <= 0) {
        showToast(t('scripts.app.metaux.noCredits'));
        updateMetauxCreditsUI();
        return;
      }
      gameState.bonusParticulesTickets = credits - 1;
      metauxGame.restart();
      updateMetauxCreditsUI();
      saveGame();
    });
  }

  if (elements.metauxFreePlayButton) {
    elements.metauxFreePlayButton.addEventListener('click', () => {
      initMetauxGame();
      if (!metauxGame) {
        showToast(t('scripts.app.metaux.unavailable'));
        return;
      }
      if (isMetauxSessionRunning()) {
        showToast(t('scripts.app.metaux.gameInProgress'));
        updateMetauxCreditsUI();
        return;
      }
      if (typeof metauxGame.startFreePlay === 'function') {
        metauxGame.startFreePlay();
      } else {
        metauxGame.restart({ freePlay: true });
      }
      updateMetauxCreditsUI();
    });
  }

  if (elements.metauxFreePlayExitButton) {
    elements.metauxFreePlayExitButton.addEventListener('click', () => {
      initMetauxGame();
      if (!metauxGame) {
        showToast(t('scripts.app.metaux.unavailable'));
        return;
      }
      if (typeof metauxGame.isFreePlayMode === 'function' && !metauxGame.isFreePlayMode()) {
        return;
      }
      if (metauxGame.processing) {
        showToast('Patientez, la réaction en chaîne est en cours.');
        return;
      }
      if (typeof metauxGame.endFreePlaySession === 'function') {
        const ended = metauxGame.endFreePlaySession({ showEndScreen: true });
        if (ended && typeof window.updateMetauxCreditsUI === 'function') {
          window.updateMetauxCreditsUI();
        }
      }
    });
  }

  if (elements.brandHomeButton) {
    elements.brandHomeButton.addEventListener('click', () => {
      showPage('game');
    });
  }

  if (elements.brandPortal) {
    elements.brandPortal.addEventListener('click', () => {
      if (!isArcadeUnlocked()) {
        return;
      }
      showPage('arcadeHub');
    });
  }

  if (elements.statusAtomsButton) {
    elements.statusAtomsButton.addEventListener('click', () => {
      if (document?.body?.dataset?.activePage === 'game') {
        return;
      }
      showPage('game');
    });
  }

  if (elements.arcadeReturnButton) {
    elements.arcadeReturnButton.addEventListener('click', () => {
      showPage('game');
      if (isArcadeUnlocked()) {
        triggerBrandPortalPulse();
      }
    });
  }

  if (elements.arcadeTicketButtons?.length) {
    elements.arcadeTicketButtons.forEach(button => {
      button.addEventListener('click', () => {
        if (!isPageUnlocked('gacha')) {
          return;
        }
        showPage('gacha');
      });
    });
  }

  if (elements.arcadeBonusTicketButtons?.length) {
    elements.arcadeBonusTicketButtons.forEach(button => {
      button.addEventListener('click', () => {
        if (button.disabled) {
          return;
        }
        showPage('metaux');
      });
    });
  }

  if (elements.elementInfoSymbol) {
    elements.elementInfoSymbol.addEventListener('click', event => {
      if (elements.elementInfoSymbol.disabled || !selectedElementId) {
        return;
      }
      event.preventDefault();
      openElementDetailsModal(selectedElementId, { trigger: elements.elementInfoSymbol });
    });
  }

  if (elements.elementInfoCategoryButton) {
    elements.elementInfoCategoryButton.addEventListener('click', event => {
      if (elements.elementInfoCategoryButton.disabled) {
        return;
      }
      const familyId = elements.elementInfoCategoryButton.dataset.familyId || null;
      if (!familyId) {
        return;
      }
      event.preventDefault();
      openElementFamilyModal(familyId, { trigger: elements.elementInfoCategoryButton });
    });
  }

  if (elements.elementDetailsOverlay) {
    elements.elementDetailsOverlay.addEventListener('click', event => {
      const target = event.target.closest('[data-element-details-close]');
      if (target) {
        event.preventDefault();
        closeElementDetailsModal();
      }
    });
  }

  if (elements.elementFamilyOverlay) {
    elements.elementFamilyOverlay.addEventListener('click', event => {
      const target = event.target.closest('[data-element-family-close]');
      if (target) {
        event.preventDefault();
        closeElementFamilyModal();
      }
    });
  }
  if (elements.atomButton) {
    const atomButton = elements.atomButton;

    atomButton.addEventListener('pointerdown', event => {
      activateEcoClickFeedback();
      if (!isTouchLikePointerEvent(event)) {
        return;
      }
      if (typeof event.preventDefault === 'function') {
        event.preventDefault();
      }
      if (typeof event.stopPropagation === 'function') {
        event.stopPropagation();
      }
      markTouchPointerManualClick();
      handleManualAtomClick({ contextId: 'game' });
      const currentTarget = event.currentTarget;
      if (currentTarget && typeof currentTarget.setPointerCapture === 'function') {
        try {
          currentTarget.setPointerCapture(event.pointerId);
        } catch (error) {
          // Ignore errors from pointer capture (unsupported or already captured)
        }
      }
    });

    const handleEcoPointerRelease = () => {
      if (!isEcoPerformanceModeActive()) {
        return;
      }
      triggerEcoClickFeedbackPulse();
    };

    const handlePointerCompletion = event => {
      handleEcoPointerRelease();
      if (!isTouchLikePointerEvent(event)) {
        return;
      }
      const currentTarget = event.currentTarget;
      if (!currentTarget || typeof currentTarget.releasePointerCapture !== 'function') {
        return;
      }
      try {
        currentTarget.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore pointer capture release errors
      }
    };

    atomButton.addEventListener('pointerup', handlePointerCompletion);
    atomButton.addEventListener('pointerleave', handleEcoPointerRelease);
    atomButton.addEventListener('pointercancel', handlePointerCompletion);
    atomButton.addEventListener('lostpointercapture', handlePointerCompletion);

    atomButton.addEventListener('click', event => {
      if (shouldSuppressPointerDerivedClick()) {
        if (typeof event.preventDefault === 'function') {
          event.preventDefault();
        }
        if (typeof event.stopPropagation === 'function') {
          event.stopPropagation();
        }
        return;
      }
      if (typeof event?.preventDefault === 'function') {
        event.preventDefault();
      }
      if (typeof event?.stopPropagation === 'function') {
        event.stopPropagation();
      }
      handleManualAtomClick({ contextId: 'game' });
    });

    atomButton.addEventListener('dragstart', event => {
      event.preventDefault();
    });
  }

  if (elements.gachaSunButton) {
    elements.gachaSunButton.addEventListener('click', event => {
      event.preventDefault();
      handleGachaSunClick().catch(error => {
        console.error('Erreur lors du tirage cosmique', error);
      });
    });
  }

  if (elements.gachaTicketModeButton) {
    elements.gachaTicketModeButton.addEventListener('click', event => {
      event.preventDefault();
      if (gachaAnimationInProgress) return;
      toggleGachaRollMode();
    });
  }

  if (elements.languageSelect) {
    elements.languageSelect.addEventListener('change', event => {
      const requestedLanguage = resolveLanguageCode(event.target.value);
      const i18n = globalThis.i18n;
      if (!i18n || typeof i18n.setLanguage !== 'function') {
        updateLanguageSelectorValue(requestedLanguage);
        writeStoredLanguagePreference(requestedLanguage);
        if (document && document.documentElement) {
          document.documentElement.lang = requestedLanguage;
        }
        return;
      }
      const previousLanguage = i18n.getCurrentLanguage ? i18n.getCurrentLanguage() : null;
      const normalizedPrevious = previousLanguage ? resolveLanguageCode(previousLanguage) : null;
      if (normalizedPrevious === requestedLanguage) {
        writeStoredLanguagePreference(requestedLanguage);
        return;
      }
      elements.languageSelect.disabled = true;
      i18n
        .setLanguage(requestedLanguage)
        .then(() => {
          writeStoredLanguagePreference(requestedLanguage);
          if (typeof i18n.updateTranslations === 'function') {
            i18n.updateTranslations(document);
          }
          updateLanguageSelectorValue(requestedLanguage);
          refreshOptionsWelcomeContent();
          updateBrickSkinOption();
          updateResetDialogCopy();
          updateUI();
          showToast(t('scripts.app.language.updated'));
        })
        .catch(error => {
          console.error('Unable to change language', error);
          if (previousLanguage) {
            updateLanguageSelectorValue(previousLanguage);
          }
        })
        .finally(() => {
          elements.languageSelect.disabled = false;
        });
    });
  }

  if (elements.backupSaveButton) {
    elements.backupSaveButton.addEventListener('click', event => {
      event.preventDefault?.();
      requestManualBackupExport();
    });
  }

  if (elements.backupLoadButton) {
    elements.backupLoadButton.addEventListener('click', event => {
      event.preventDefault?.();
      requestManualBackupImport();
    });
  }

  if (elements.openNotesButton) {
    elements.openNotesButton.addEventListener('click', event => {
      event.preventDefault?.();
      if (!isPageUnlocked('notes')) {
        return;
      }
      showPage('notes');
    });
  }

  if (elements.themeSelect) {
    elements.themeSelect.addEventListener('change', event => {
      const appliedId = applyTheme(event.target.value);
      renderThemeOptions();
      saveGame();
      const appliedTheme = getThemeDefinition(appliedId);
      const themeLabel = getThemeLabel(appliedTheme) || appliedId;
      showToast(t('scripts.app.themeUpdated', { name: themeLabel }));
    });
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('i18n:languagechange', () => {
      renderThemeOptions();
      renderPeriodicTable();
      updateUI();
      updateResetDialogCopy();
      renderManualBackupStatus();
    });
    window.addEventListener('focus', () => {
      refreshManualBackupSupportState();
    });
  }

  if (elements.brickSkinSelect) {
    elements.brickSkinSelect.addEventListener('change', event => {
      commitBrickSkinSelection(event.target.value);
    });
  }

  if (elements.arcadeBrickSkinSelect) {
    elements.arcadeBrickSkinSelect.addEventListener('change', event => {
      commitBrickSkinSelection(event.target.value);
    });
  }

  if (elements.musicTrackSelect) {
    elements.musicTrackSelect.addEventListener('change', event => {
      if (!isMusicModuleEnabled()) {
        if (typeof event.preventDefault === 'function') {
          event.preventDefault();
        }
        refreshMusicControls();
        return;
      }
      const selectedId = event.target.value;
      if (!selectedId) {
        musicPlayer.stop();
        showToast(t('scripts.app.music.muted'));
        return;
      }
      const success = musicPlayer.playTrackById(selectedId);
      if (!success) {
        showToast(t('scripts.app.music.missing'));
        updateMusicStatus();
        return;
      }
      const currentTrack = musicPlayer.getCurrentTrack();
      if (currentTrack) {
        if (currentTrack.placeholder) {
          showToast(t('scripts.app.music.fileMissing'));
        } else {
          showToast(t('scripts.app.music.nowPlaying', { name: currentTrack.displayName }));
        }
      }
      updateMusicStatus();
    });
  }

  if (elements.musicVolumeSlider) {
    const applyVolumeFromSlider = (rawValue, { announce = false } = {}) => {
      const numeric = Number(rawValue);
      const percent = Number.isFinite(numeric) ? Math.max(0, Math.min(100, numeric)) : 0;
      const normalized = clampMusicVolume(percent / 100, musicPlayer.getVolume());
      musicPlayer.setVolume(normalized);
      gameState.musicVolume = normalized;
      if (announce) {
        showToast(t('scripts.app.music.volumeLabel', { value: Math.round(normalized * 100) }));
      }
    };
    elements.musicVolumeSlider.addEventListener('input', event => {
      if (!isMusicModuleEnabled()) {
        event.preventDefault?.();
        refreshMusicControls();
        return;
      }
      applyVolumeFromSlider(event.target.value);
    });
    elements.musicVolumeSlider.addEventListener('change', event => {
      if (!isMusicModuleEnabled()) {
        event.preventDefault?.();
        refreshMusicControls();
        return;
      }
      applyVolumeFromSlider(event.target.value, { announce: true });
    });
  }

  if (elements.openDevkitButton) {
    elements.openDevkitButton.addEventListener('click', event => {
      event.preventDefault();
      if (!isDevkitFeatureEnabled()) {
        return;
      }
      toggleDevKit();
    });
    updateDevkitButtonVisibility();
  }

  if (elements.resetButton) {
    if (elements.resetDialog && elements.resetDialogForm && elements.resetDialogInput) {
      elements.resetButton.addEventListener('click', event => {
        event.preventDefault();
        openResetDialog();
      });
      elements.resetDialogForm.addEventListener('submit', handleResetDialogSubmit);
      if (elements.resetDialogCancel) {
        elements.resetDialogCancel.addEventListener('click', handleResetDialogCancel);
      }
      if (elements.resetDialog) {
        elements.resetDialog.addEventListener('click', handleResetDialogBackdrop);
      }
      updateResetDialogCopy();
      setResetDialogError();
    } else {
      elements.resetButton.addEventListener('click', event => {
        event.preventDefault();
        handleResetPromptFallback();
      });
    }
  }

  if (elements.bigBangOptionToggle) {
    elements.bigBangOptionToggle.addEventListener('change', event => {
      const enabled = event.target.checked;
      if (!isBigBangUnlocked()) {
        event.target.checked = false;
        gameState.bigBangButtonVisible = false;
        updateBigBangVisibility();
        return;
      }
      gameState.bigBangButtonVisible = enabled;
      updateBigBangVisibility();
      saveGame();
      showToast(enabled
        ? t('scripts.app.bigBangToggle.shown')
        : t('scripts.app.bigBangToggle.hidden'));
    });
  }

  if (elements.bigBangRestartButton) {
    elements.bigBangRestartButton.addEventListener('click', event => {
      event.preventDefault();
      handleBigBangRestart();
    });
  }

  if (elements.bigBangDialogConfirm) {
    elements.bigBangDialogConfirm.addEventListener('click', handleBigBangDialogConfirm);
  }
  if (elements.bigBangDialogCancel) {
    elements.bigBangDialogCancel.addEventListener('click', handleBigBangDialogCancel);
  }
  if (elements.bigBangDialog) {
    elements.bigBangDialog.addEventListener('click', handleBigBangDialogBackdrop);
  }



}

document.addEventListener('click', event => {
  suppressPointerNavigationClick(event);
}, true);

document.addEventListener('click', event => {
  if (shouldSuppressPointerDerivedClick()) return;
  if (!shouldTriggerGlobalClick(event)) return;
  handleManualAtomClick({ contextId: 'game' });
});

document.addEventListener('selectstart', event => {
  if (isManualClickContextActive()) {
    event.preventDefault();
  }
});
