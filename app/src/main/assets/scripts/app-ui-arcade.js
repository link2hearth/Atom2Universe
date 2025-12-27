function updateBrickSkinOption() {
  const unlocked = isArcadeUnlocked();
  const selection = normalizeBrickSkinSelection(gameState.arcadeBrickSkin);
  const unlockedMessage = translateOrDefault(
    'scripts.app.options.brickSkin.unlocked',
    'Choisissez l’apparence des briques de Particules.'
  );
  const lockedMessage = translateOrDefault(
    'index.sections.options.brickSkin.note',
    'Débloquez le trophée « Ruée vers le million » pour personnaliser vos briques.'
  );

  if (elements.brickSkinOptionCard) {
    elements.brickSkinOptionCard.hidden = !unlocked;
    elements.brickSkinOptionCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }

  if (elements.brickSkinSelect) {
    elements.brickSkinSelect.disabled = !unlocked;
    if (elements.brickSkinSelect.value !== selection) {
      elements.brickSkinSelect.value = selection;
    }
  }

  if (elements.arcadeBrickSkinSelect) {
    elements.arcadeBrickSkinSelect.disabled = !unlocked;
    if (elements.arcadeBrickSkinSelect.value !== selection) {
      elements.arcadeBrickSkinSelect.value = selection;
    }
    elements.arcadeBrickSkinSelect.title = unlocked ? '' : lockedMessage;
  }

  if (elements.brickSkinStatus) {
    elements.brickSkinStatus.textContent = unlocked ? unlockedMessage : lockedMessage;
  }
}

function updateOptionsIntroDetails(options = {}) {
  if (!elements.optionsArcadeDetails) {
    return;
  }
  const metadata = options.metadata || getOptionsDetailMetadata();
  const renderIds = Array.isArray(options.renderIds)
    ? options.renderIds
    : computeRenderableOptionDetailIds(metadata.detailOrder, metadata.detailMap);
  const key = renderIds.join('|');
  if (elements.optionsArcadeDetails.dataset.renderKey !== key) {
    renderOptionsWelcomeContent({ metadata, renderIds });
    return;
  }
  const hasDetails = renderIds.length > 0;
  elements.optionsArcadeDetails.hidden = !hasDetails;
  elements.optionsArcadeDetails.setAttribute('aria-hidden', hasDetails ? 'false' : 'true');
}

function commitBrickSkinSelection(rawValue) {
  const selection = normalizeBrickSkinSelection(rawValue);
  if (!isArcadeUnlocked()) {
    updateBrickSkinOption();
    return;
  }
  const previous = normalizeBrickSkinSelection(gameState.arcadeBrickSkin);
  if (selection !== previous) {
    gameState.arcadeBrickSkin = selection;
    if (typeof setParticulesBrickSkinPreference === 'function') {
      setParticulesBrickSkinPreference(selection);
    }
    saveGame();
    const messageKey = BRICK_SKIN_TOAST_KEYS[selection] || 'scripts.app.brickSkins.applied.generic';
    showToast(t(messageKey));
  }
  updateBrickSkinOption();
}

function ensureWaveGame() {
  if (waveGame || typeof WaveGame !== 'function') {
    return waveGame;
  }
  if (!elements.waveCanvas) {
    return null;
  }
  waveGame = new WaveGame({
    canvas: elements.waveCanvas,
    stage: elements.waveStage,
    distanceElement: elements.waveDistanceValue,
    speedElement: elements.waveSpeedValue,
    altitudeElement: elements.waveAltitudeValue
  });
  return waveGame;
}

function ensureBiggerGame() {
  if (biggerGame || typeof BiggerGame !== 'function') {
    return biggerGame;
  }
  if (!elements.biggerBoard) {
    return null;
  }
  biggerGame = new BiggerGame({
    pageElement: elements.biggerPage,
    boardElement: elements.biggerBoard,
    restartButton: elements.biggerRestartButton,
    overlayElement: elements.biggerOverlay,
    overlayTitleElement: elements.biggerOverlayTitle,
    overlayMessageElement: elements.biggerOverlayMessage,
    overlayActionElement: elements.biggerOverlayAction,
    overlayDismissElement: elements.biggerOverlayDismiss
  });
  return biggerGame;
}

function ensureBalanceGame() {
  if (balanceGame || typeof BalanceGame !== 'function') {
    return balanceGame;
  }
  if (!elements.balanceBoard) {
    return null;
  }
  balanceGame = new BalanceGame({
    pageElement: elements.balancePage,
    stageElement: elements.balanceStage,
    boardElement: elements.balanceBoard,
    surfaceElement: elements.balanceSurface,
    inventoryElement: elements.balancePieces,
    statusElement: elements.balanceStatus,
    difficultySelect: elements.balanceDifficultySelect,
    difficultyDescription: elements.balanceDifficultyDescription,
    testButton: elements.balanceTestButton,
    resetButton: elements.balanceResetButton,
    dragLayer: elements.balanceDragLayer
  });
  return balanceGame;
}

function ensureQuantum2048Game() {
  if (quantum2048Game || typeof Quantum2048Game !== 'function') {
    return quantum2048Game;
  }
  if (!elements.quantum2048Board) {
    return null;
  }
  quantum2048Game = new Quantum2048Game({
    boardElement: elements.quantum2048Board,
    tilesContainer: elements.quantum2048Tiles,
    backgroundContainer: elements.quantum2048Grid,
    goalElement: elements.quantum2048GoalValue,
    parallelUniverseElement: elements.quantum2048ParallelUniverseValue
  });
  return quantum2048Game;
}

function ensureStarBridgesGame() {
  if (starBridgesGame && typeof starBridgesGame === 'object') {
    return starBridgesGame;
  }
  if (window.starBridgesArcade && typeof window.starBridgesArcade === 'object') {
    starBridgesGame = window.starBridgesArcade;
    return starBridgesGame;
  }
  return null;
}

function ensureStarsWarGame() {
  if (starsWarGame && typeof starsWarGame === 'object') {
    return starsWarGame;
  }
  if (window.starsWarArcade && typeof window.starsWarArcade === 'object') {
    starsWarGame = window.starsWarArcade;
    return starsWarGame;
  }
  return null;
}

function ensureJumpingCatGame() {
  if (jumpingCatGame && typeof jumpingCatGame === 'object') {
    return jumpingCatGame;
  }
  if (window.jumpingCatArcade && typeof window.jumpingCatArcade === 'object') {
    jumpingCatGame = window.jumpingCatArcade;
    return jumpingCatGame;
  }
  return null;
}

function ensureReflexGame() {
  if (reflexGame && typeof reflexGame === 'object') {
    return reflexGame;
  }
  if (window.reflexArcade && typeof window.reflexArcade === 'object') {
    reflexGame = window.reflexArcade;
    return reflexGame;
  }
  return null;
}

function ensurePipeTapGame() {
  if (pipeTapGame && typeof pipeTapGame === 'object') {
    return pipeTapGame;
  }
  if (window.pipeTapArcade && typeof window.pipeTapArcade === 'object') {
    pipeTapGame = window.pipeTapArcade;
    return pipeTapGame;
  }
  return null;
}

function ensureColorStackGame() {
  if (colorStackGame && typeof colorStackGame === 'object') {
    return colorStackGame;
  }
  if (window.colorStackArcade && typeof window.colorStackArcade === 'object') {
    colorStackGame = window.colorStackArcade;
    return colorStackGame;
  }
  return null;
}

function ensureMotocrossGame() {
  if (motocrossGame && typeof motocrossGame === 'object') {
    return motocrossGame;
  }
  if (window.motocrossArcade && typeof window.motocrossArcade === 'object') {
    motocrossGame = window.motocrossArcade;
    return motocrossGame;
  }
  return null;
}

function syncMotocrossRecordsFromSave() {
  const motocross = ensureMotocrossGame();
  if (motocross && typeof motocross.syncRecordsFromSave === 'function') {
    try {
      motocross.syncRecordsFromSave();
    } catch (error) {
      console.warn('Motocross: unable to sync records from save', error);
    }
  }
}

function ensureHexGame() {
  if (hexGame && typeof hexGame === 'object') {
    return hexGame;
  }
  if (window.hexArcade && typeof window.hexArcade === 'object') {
    hexGame = window.hexArcade;
    return hexGame;
  }
  return null;
}

function ensureTwinsGame() {
  if (twinsGame && typeof twinsGame === 'object') {
    return twinsGame;
  }
  if (window.twinsArcade && typeof window.twinsArcade === 'object') {
    twinsGame = window.twinsArcade;
    return twinsGame;
  }
  return null;
}

function ensureSokobanGame() {
  if (sokobanGame && typeof sokobanGame === 'object') {
    return sokobanGame;
  }
  if (window.sokobanArcade && typeof window.sokobanArcade === 'object') {
    sokobanGame = window.sokobanArcade;
    return sokobanGame;
  }
  return null;
}

function ensureTaquinGame() {
  if (taquinGame && typeof taquinGame === 'object') {
    return taquinGame;
  }
  if (window.taquinArcade && typeof window.taquinArcade === 'object') {
    taquinGame = window.taquinArcade;
    return taquinGame;
  }
  return null;
}

function ensureLinkGame() {
  if (linkGame && typeof linkGame === 'object') {
    return linkGame;
  }
  if (window.linkArcade && typeof window.linkArcade === 'object') {
    linkGame = window.linkArcade;
    return linkGame;
  }
  return null;
}

function ensureLightsOutGame() {
  if (lightsOutGame && typeof lightsOutGame === 'object') {
    return lightsOutGame;
  }
  if (window.lightsOutArcade && typeof window.lightsOutArcade === 'object') {
    lightsOutGame = window.lightsOutArcade;
    return lightsOutGame;
  }
  return null;
}

function ensureGameOfLifeGame() {
  if (gameOfLifeGame && typeof gameOfLifeGame === 'object') {
    return gameOfLifeGame;
  }
  if (window.gameOfLifeArcade && typeof window.gameOfLifeArcade === 'object') {
    gameOfLifeGame = window.gameOfLifeArcade;
    return gameOfLifeGame;
  }
  return null;
}

function ensureEscapeGame() {
  if (escapeGame && typeof escapeGame === 'object') {
    if (typeof escapeGame.initialize === 'function') {
      escapeGame.initialize();
    }
    return escapeGame;
  }
  if (window.escapeArcade && typeof window.escapeArcade === 'object') {
    escapeGame = window.escapeArcade;
    if (typeof escapeGame.initialize === 'function') {
      escapeGame.initialize();
    }
    return escapeGame;
  }
  return null;
}

function areInfoBonusesUnlocked() {
  const unlocks = getPageUnlockState();
  return unlocks?.info === true;
}

function areAchievementsFeatureUnlocked() {
  return getUnlockedTrophySet().has(ACHIEVEMENTS_UNLOCK_TROPHY_ID);
}

function updateInfoAchievementsVisibility() {
  if (!elements.infoAchievementsCard) {
    return;
  }
  const unlocked = areAchievementsFeatureUnlocked();
  elements.infoAchievementsCard.hidden = !unlocked;
  elements.infoAchievementsCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  if (!unlocked && elements.goalsEmpty) {
    elements.goalsEmpty.hidden = true;
    elements.goalsEmpty.setAttribute('aria-hidden', 'true');
  }
}

function updateInfoBonusVisibility() {
  const visible = areInfoBonusesUnlocked() && isInfoSectionsFeatureEnabled();
  if (elements.infoElementBonusCard) {
    elements.infoElementBonusCard.hidden = !visible;
    elements.infoElementBonusCard.setAttribute('aria-hidden', visible ? 'false' : 'true');
  }
}

function updateInfoCardsVisibility() {
  if (!elements.infoCardsCard) {
    return;
  }
  const unlocked = isPageUnlocked('collection');
  elements.infoCardsCard.hidden = !unlocked;
  elements.infoCardsCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateCollectionImagesVisibility() {
  if (!elements.collectionImagesCard) {
    return;
  }
  const unlocked = isCollectionFeatureEnabled() && isPageUnlocked('collection');
  elements.collectionImagesCard.hidden = !unlocked;
  elements.collectionImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

  function updateCollectionBonusImagesVisibility() {
    if (!elements.collectionBonusImagesCard) {
      return;
    }
    const unlocked = isPageUnlocked('collection') && hasOwnedGachaBonusImages();
    elements.collectionBonusImagesCard.hidden = !unlocked;
    elements.collectionBonusImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }

  function updateCollectionBonus1ImagesVisibility() {
    if (!elements.collectionBonus1ImagesCard) {
      return;
    }
    const unlocked = isPageUnlocked('collection')
      && (isSecondaryBonusImageCollectionUnlocked() || hasOwnedGachaBonus1Images());
    elements.collectionBonus1ImagesCard.hidden = !unlocked;
    elements.collectionBonus1ImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }

  function updateCollectionBonus2ImagesVisibility() {
    if (!elements.collectionBonus2ImagesCard) {
      return;
    }
    const unlocked = isPageUnlocked('collection')
      && (isTertiaryBonusImageCollectionUnlocked() || hasOwnedGachaBonus2Images());
    elements.collectionBonus2ImagesCard.hidden = !unlocked;
    elements.collectionBonus2ImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }

  function updateCollectionBonus3ImagesVisibility() {
    if (!elements.collectionBonus3ImagesCard) {
      return;
    }
    const unlocked = isPageUnlocked('collection')
      && (isQuaternaryBonusImageCollectionUnlocked() || hasOwnedGachaBonus3Images());
    elements.collectionBonus3ImagesCard.hidden = !unlocked;
    elements.collectionBonus3ImagesCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
  }

function updateCollectionVideosVisibility() {
  if (!elements.collectionVideosCard) {
    return;
  }
  const hasVideos = hasUnlockedCollectionVideos();
  const unlocked = isCollectionVideosFeatureEnabled() && isPageUnlocked('collection') && hasVideos;
  elements.collectionVideosCard.hidden = !unlocked;
  elements.collectionVideosCard.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function hasAndroidBackgroundBridge() {
  return typeof window !== 'undefined'
    && window.AndroidBridge
    && typeof window.AndroidBridge.loadBackgroundImageBank === 'function';
}

function hasAndroidCollectionDownloadsBridge() {
  return typeof window !== 'undefined'
    && window.AndroidBridge
    && typeof window.AndroidBridge.loadCollectionDownloads === 'function';
}

function shouldShowCollectionDownloadsCard() {
  return isPageUnlocked('collection') && hasAndroidCollectionDownloadsBridge();
}

function updateCollectionDownloadsVisibility() {
  if (!elements.collectionDownloadsCard) {
    return;
  }
  const visible = shouldShowCollectionDownloadsCard();
  elements.collectionDownloadsCard.hidden = !visible;
  elements.collectionDownloadsCard.setAttribute('aria-hidden', visible ? 'false' : 'true');
  updateCollectionDownloadsRefreshState();
}

function updateCollectionShortcutState() {
  if (!elements.imagesCollectionButton) {
    return;
  }
  const unlocked = isPageUnlocked('collection');
  elements.imagesCollectionButton.disabled = !unlocked;
  elements.imagesCollectionButton.setAttribute('aria-disabled', unlocked ? 'false' : 'true');
  elements.imagesCollectionButton.hidden = !unlocked;
  elements.imagesCollectionButton.setAttribute('aria-hidden', unlocked ? 'false' : 'true');
}

function updateInfoDevkitVisibility() {
  if (!elements.infoDevkitShopCard) {
    return;
  }
  const visible = isInfoSectionsFeatureEnabled();
  elements.infoDevkitShopCard.hidden = !visible;
  elements.infoDevkitShopCard.setAttribute('aria-hidden', visible ? 'false' : 'true');
}

function readStoredInfoCardCollapsed(storageKey, defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(storageKey);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read info card preference', storageKey, error);
  }
  return !!defaultValue;
}

function writeStoredInfoCardCollapsed(storageKey, collapsed) {
  try {
    globalThis.localStorage?.setItem(storageKey, collapsed ? 'true' : 'false');
  } catch (error) {
    console.warn('Unable to persist info card preference', storageKey, error);
  }
}

function readStoredCollectionVideosUnlocked(defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read collection video unlock state', error);
  }
  return !!defaultValue;
}

function writeStoredCollectionVideosUnlocked(unlocked) {
  try {
    if (!globalThis.localStorage) {
      collectionVideosUnlockedCache = unlocked === true;
      return;
    }
    if (unlocked) {
      globalThis.localStorage.setItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY, 'true');
    } else {
      globalThis.localStorage.removeItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    }
    collectionVideosUnlockedCache = unlocked === true;
  } catch (error) {
    console.warn('Unable to persist collection video unlock state', error);
  }
}

function persistCollectionVideoUnlockState(unlocked) {
  writeStoredCollectionVideosUnlocked(unlocked === true);
}

function readStoredCollectionVideoSnapshotEntries() {
  try {
    const raw = globalThis.localStorage?.getItem(COLLECTION_VIDEOS_STATE_STORAGE_KEY);
    if (typeof raw !== 'string' || !raw.trim()) {
      return [];
    }
    const parsed = JSON.parse(raw);
    const sourceEntries = Array.isArray(parsed?.entries) ? parsed.entries : [];
    return sourceEntries
      .map(entry => {
        const id = typeof entry?.id === 'string' && entry.id.trim() ? entry.id.trim() : null;
        if (!id) {
          return null;
        }
        const acquiredOrder = Number(entry?.acquiredOrder);
        const firstAcquiredAt = Number(entry?.firstAcquiredAt);
        return {
          id,
          acquiredOrder: Number.isFinite(acquiredOrder) && acquiredOrder > 0
            ? Math.floor(acquiredOrder)
            : null,
          firstAcquiredAt: Number.isFinite(firstAcquiredAt) && firstAcquiredAt > 0
            ? firstAcquiredAt
            : null
        };
      })
      .filter(entry => entry);
  } catch (error) {
    console.warn('Unable to read collection video snapshot', error);
  }
  return [];
}

function writeStoredCollectionVideoSnapshot(entries) {
  try {
    if (!globalThis.localStorage) {
      return false;
    }
    if (!Array.isArray(entries) || entries.length === 0) {
      globalThis.localStorage.removeItem(COLLECTION_VIDEOS_STATE_STORAGE_KEY);
      return true;
    }
    const payload = {
      version: 1,
      entries: entries.map(entry => ({
        id: entry.id,
        acquiredOrder: entry.acquiredOrder || null,
        firstAcquiredAt: entry.firstAcquiredAt || null
      }))
    };
    globalThis.localStorage.setItem(COLLECTION_VIDEOS_STATE_STORAGE_KEY, JSON.stringify(payload));
    return true;
  } catch (error) {
    console.warn('Unable to persist collection video snapshot', error);
  }
  return false;
}

function clearStoredCollectionVideoSnapshot() {
  writeStoredCollectionVideoSnapshot([]);
}

function mergeStoredCollectionVideoSnapshot(collection) {
  const target = collection && typeof collection === 'object' ? collection : null;
  if (!target) {
    return false;
  }
  const snapshotEntries = readStoredCollectionVideoSnapshotEntries();
  if (!snapshotEntries.length) {
    return false;
  }
  let merged = false;
  snapshotEntries.forEach(entry => {
    if (!entry?.id) {
      return;
    }
    const reference = target[entry.id];
    if (!reference) {
      return;
    }
    const rawCount = Number(reference?.count ?? reference);
    if (!Number.isFinite(rawCount) || rawCount <= 0) {
      reference.count = 1;
      merged = true;
    }
    if (entry.acquiredOrder != null) {
      const storedOrder = Number(reference?.acquiredOrder);
      if (!Number.isFinite(storedOrder) || storedOrder <= 0) {
        reference.acquiredOrder = entry.acquiredOrder;
        merged = true;
      }
    }
    if (entry.firstAcquiredAt != null) {
      const storedFirst = Number(reference?.firstAcquiredAt);
      if (!Number.isFinite(storedFirst) || storedFirst <= 0) {
        reference.firstAcquiredAt = entry.firstAcquiredAt;
        merged = true;
      }
    }
  });
  return merged;
}

function syncCollectionVideoStateSnapshot(options = {}) {
  const collection = options.collection && typeof options.collection === 'object'
    ? options.collection
    : (gameState.collectionVideos && typeof gameState.collectionVideos === 'object'
      ? gameState.collectionVideos
      : null);
  if (!collection) {
    clearStoredCollectionVideoSnapshot();
    return;
  }
  const ownedEntries = Object.values(collection)
    .map(entry => {
      if (!entry) {
        return null;
      }
      const count = Number(entry?.count ?? entry);
      if (!Number.isFinite(count) || count <= 0) {
        return null;
      }
      return {
        id: entry.id,
        acquiredOrder: Number.isFinite(Number(entry?.acquiredOrder)) && Number(entry?.acquiredOrder) > 0
          ? Math.floor(Number(entry.acquiredOrder))
          : null,
        firstAcquiredAt: Number.isFinite(Number(entry?.firstAcquiredAt)) && Number(entry?.firstAcquiredAt) > 0
          ? Number(entry.firstAcquiredAt)
          : null
      };
    })
    .filter(entry => entry && entry.id);
  writeStoredCollectionVideoSnapshot(ownedEntries);
}

if (typeof globalThis !== 'undefined') {
  globalThis.syncCollectionVideoStateSnapshot = syncCollectionVideoStateSnapshot;
}


function readStoredCollectionVideosUnlocked(defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read collection video unlock state', error);
  }
  return !!defaultValue;
}

function writeStoredCollectionVideosUnlocked(unlocked) {
  try {
    if (!globalThis.localStorage) {
      collectionVideosUnlockedCache = unlocked === true;
      return;
    }
    if (unlocked) {
      globalThis.localStorage.setItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY, 'true');
    } else {
      globalThis.localStorage.removeItem(COLLECTION_VIDEOS_UNLOCKED_STORAGE_KEY);
    }
    collectionVideosUnlockedCache = unlocked === true;
  } catch (error) {
    console.warn('Unable to persist collection video unlock state', error);
  }
}

function persistCollectionVideoUnlockState(unlocked) {
  writeStoredCollectionVideosUnlocked(unlocked === true);
}


function readStoredHeaderCollapsed(defaultValue = false) {
  try {
    const stored = globalThis.localStorage?.getItem(HEADER_COLLAPSED_STORAGE_KEY);
    if (typeof stored === 'string') {
      const normalized = stored.trim().toLowerCase();
      if (normalized === 'true' || normalized === '1') {
        return true;
      }
      if (normalized === 'false' || normalized === '0') {
        return false;
      }
    }
  } catch (error) {
    console.warn('Unable to read header collapse preference', error);
  }
  return !!defaultValue;
}

function writeStoredHeaderCollapsed(collapsed) {
  try {
    globalThis.localStorage?.setItem(HEADER_COLLAPSED_STORAGE_KEY, collapsed ? 'true' : 'false');
  } catch (error) {
    console.warn('Unable to persist header collapse preference', error);
  }
}

function updateHeaderBannerToggleLabel(collapsed) {
  if (!elements.headerBannerToggle) {
    return;
  }
  const key = collapsed
    ? 'index.header.bannerToggle.expand'
    : 'index.header.bannerToggle.collapse';
  const fallback = collapsed ? 'Déployer la bannière' : 'Réduire la bannière';
  const label = translateOrDefault(key, fallback);
  elements.headerBannerToggle.setAttribute(
    'data-i18n',
    `aria-label:${key};title:${key}`
  );
  elements.headerBannerToggle.setAttribute('aria-label', label);
  elements.headerBannerToggle.setAttribute('title', label);
  const hiddenLabel = elements.headerBannerToggle.querySelector('.visually-hidden');
  if (hiddenLabel) {
    hiddenLabel.textContent = label;
    hiddenLabel.setAttribute('data-i18n', key);
  }
}

function updateHeaderBackgroundOffset() {
  if (typeof document === 'undefined') {
    return;
  }
  const root = document.documentElement;
  if (!root || !elements.appHeader) {
    return;
  }
  const rect = elements.appHeader.getBoundingClientRect?.();
  const height = Math.max(
    elements.appHeader.offsetHeight || 0,
    rect?.height || 0
  );
  root.style.setProperty('--favorite-background-top', `${height}px`);
}

function setHeaderCollapsed(collapsed, options = {}) {
  if (!elements.appHeader || !elements.headerBannerToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  const isCollapsed = elements.appHeader.dataset.collapsed === 'true';
  if (!options.force && shouldCollapse === isCollapsed) {
    updateHeaderBannerToggleLabel(shouldCollapse);
    return;
  }

  if (shouldCollapse) {
    elements.appHeader.dataset.collapsed = 'true';
  } else {
    delete elements.appHeader.dataset.collapsed;
  }

  if (elements.navMenu) {
    if (shouldCollapse) {
      elements.navMenu.hidden = true;
      elements.navMenu.setAttribute('aria-hidden', 'true');
    } else {
      elements.navMenu.hidden = false;
      elements.navMenu.removeAttribute('aria-hidden');
    }
  }

  if (elements.statusBar) {
    if (shouldCollapse) {
      elements.statusBar.setAttribute('aria-hidden', 'true');
    } else {
      elements.statusBar.removeAttribute('aria-hidden');
    }
  }

  if (elements.frenzyStatus) {
    if (shouldCollapse) {
      elements.frenzyStatus.setAttribute('aria-hidden', 'true');
    } else if (elements.frenzyStatus.hidden) {
      elements.frenzyStatus.removeAttribute('aria-hidden');
    } else {
      elements.frenzyStatus.setAttribute('aria-hidden', 'false');
    }
  }

  elements.headerBannerToggle.setAttribute('aria-pressed', shouldCollapse ? 'true' : 'false');
  elements.headerBannerToggle.setAttribute('data-state', shouldCollapse ? 'collapsed' : 'expanded');
  updateHeaderBannerToggleLabel(shouldCollapse);
  updateHeaderBackgroundOffset();
  if (options.persist !== false) {
    writeStoredHeaderCollapsed(shouldCollapse);
  }
}

function toggleHeaderCollapsed() {
  if (!elements.appHeader) {
    return;
  }
  const currentlyCollapsed = elements.appHeader.dataset.collapsed === 'true';
  setHeaderCollapsed(!currentlyCollapsed);
}

function initHeaderBannerToggle() {
  if (!elements.headerBannerToggle || !elements.appHeader) {
    return;
  }
  const initiallyCollapsed = readStoredHeaderCollapsed(false);
  setHeaderCollapsed(initiallyCollapsed, { persist: false, force: true });
  updateHeaderBackgroundOffset();
  elements.headerBannerToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleHeaderCollapsed();
  });
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', updateHeaderBackgroundOffset, { passive: true });
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', updateHeaderBackgroundOffset);
    }
  }
}

function subscribeHeaderBannerLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.appHeader
      ? elements.appHeader.dataset.collapsed === 'true'
      : false;
    updateHeaderBannerToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function updateOptionCardToggleLabel(toggleButton, collapsed) {
  if (!toggleButton) {
    return;
  }
  const key = collapsed
    ? 'index.sections.options.cards.toggle.expand'
    : 'index.sections.options.cards.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  toggleButton.setAttribute('data-i18n', key);
  toggleButton.textContent = label;
  toggleButton.setAttribute('aria-label', label);
}

function setOptionCardCollapsed(card, content, toggleButton, storageKey, collapsed, options = {}) {
  if (!card || !content || !toggleButton) {
    return;
  }
  const shouldCollapse = !!collapsed;
  card.classList.toggle('option-card--collapsed', shouldCollapse);
  content.hidden = shouldCollapse;
  content.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  toggleButton.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateOptionCardToggleLabel(toggleButton, shouldCollapse);
  if (options.persist !== false && storageKey) {
    writeStoredInfoCardCollapsed(storageKey, shouldCollapse);
  }
}

function initOptionCardCollapse(card, content, toggleButton, storageKey) {
  if (!card || !content || !toggleButton) {
    return;
  }
  const initialCollapsed = storageKey
    ? readStoredInfoCardCollapsed(storageKey, false)
    : false;
  setOptionCardCollapsed(card, content, toggleButton, storageKey, initialCollapsed, { persist: false });
  toggleButton.addEventListener('click', event => {
    event.preventDefault();
    const currentlyCollapsed = card.classList.contains('option-card--collapsed');
    setOptionCardCollapsed(card, content, toggleButton, storageKey, !currentlyCollapsed);
  });
}

function getOptionCardCollapseConfigs() {
  return [
    {
      card: elements.optionsPreferencesCard,
      content: elements.optionsPreferencesContent,
      toggle: elements.optionsPreferencesToggle,
      storageKey: OPTIONS_PREFERENCES_COLLAPSED_STORAGE_KEY
    },
    {
      card: elements.optionsBackgroundCard,
      content: elements.optionsBackgroundContent,
      toggle: elements.optionsBackgroundToggle,
      storageKey: OPTIONS_BACKGROUND_COLLAPSED_STORAGE_KEY
    },
    {
      card: elements.optionsBackupsCard,
      content: elements.optionsBackupsContent,
      toggle: elements.optionsBackupsToggle,
      storageKey: OPTIONS_BACKUPS_COLLAPSED_STORAGE_KEY
    },
    {
      card: elements.optionsCustomPagesCard,
      content: elements.optionsCustomPagesContent,
      toggle: elements.optionsCustomPagesToggle,
      storageKey: OPTIONS_CUSTOM_PAGES_COLLAPSED_STORAGE_KEY
    },
    {
      card: elements.optionsNotesCard,
      content: elements.optionsNotesContent,
      toggle: elements.optionsNotesToggle,
      storageKey: OPTIONS_NOTES_COLLAPSED_STORAGE_KEY
    }
  ];
}

function initOptionCards() {
  const configs = getOptionCardCollapseConfigs();
  configs.forEach(({ card, content, toggle, storageKey }) => {
    initOptionCardCollapse(card, content, toggle, storageKey);
  });
}

function subscribeOptionCardsLanguageUpdates() {
  const handler = () => {
    const configs = getOptionCardCollapseConfigs();
    configs.forEach(({ card, toggle }) => {
      if (!toggle) {
        return;
      }
      const collapsed = card ? card.classList.contains('option-card--collapsed') : false;
      updateOptionCardToggleLabel(toggle, collapsed);
    });
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function updateInfoWelcomeToggleLabel(collapsed) {
  if (!elements.infoWelcomeToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.welcome.toggle.expand'
    : 'index.sections.info.welcome.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoWelcomeToggle.setAttribute('data-i18n', key);
  elements.infoWelcomeToggle.textContent = label;
  elements.infoWelcomeToggle.setAttribute('aria-label', label);
}

function setInfoWelcomeCollapsed(collapsed, options = {}) {
  if (!elements.infoWelcomeCard || !elements.infoWelcomeContent || !elements.infoWelcomeToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoWelcomeCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoWelcomeContent.hidden = shouldCollapse;
  elements.infoWelcomeContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoWelcomeToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoWelcomeToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_WELCOME_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoWelcomeCollapsed() {
  if (!elements.infoWelcomeCard) {
    return;
  }
  const currentlyCollapsed = elements.infoWelcomeCard.classList.contains('info-card--collapsed');
  setInfoWelcomeCollapsed(!currentlyCollapsed);
}

function initInfoWelcomeCard() {
  if (!elements.infoWelcomeCard || !elements.infoWelcomeContent || !elements.infoWelcomeToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_WELCOME_COLLAPSED_STORAGE_KEY, false);
  setInfoWelcomeCollapsed(initialCollapsed, { persist: false });
  elements.infoWelcomeToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoWelcomeCollapsed();
  });
}

function updateInfoAchievementsToggleLabel(collapsed) {
  if (!elements.infoAchievementsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.achievements.toggle.expand'
    : 'index.sections.info.achievements.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoAchievementsToggle.setAttribute('data-i18n', key);
  elements.infoAchievementsToggle.textContent = label;
  elements.infoAchievementsToggle.setAttribute('aria-label', label);
}

function setInfoAchievementsCollapsed(collapsed, options = {}) {
  if (!elements.infoAchievementsCard
    || !elements.infoAchievementsContent
    || !elements.infoAchievementsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoAchievementsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoAchievementsContent.hidden = shouldCollapse;
  elements.infoAchievementsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoAchievementsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoAchievementsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_ACHIEVEMENTS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoAchievementsCollapsed() {
  if (!elements.infoAchievementsCard) {
    return;
  }
  const currentlyCollapsed = elements.infoAchievementsCard.classList.contains('info-card--collapsed');
  setInfoAchievementsCollapsed(!currentlyCollapsed);
}

function initInfoAchievementsCard() {
  if (!elements.infoAchievementsCard
    || !elements.infoAchievementsContent
    || !elements.infoAchievementsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_ACHIEVEMENTS_COLLAPSED_STORAGE_KEY, false);
  setInfoAchievementsCollapsed(initialCollapsed, { persist: false });
  elements.infoAchievementsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoAchievementsCollapsed();
  });
}

function updateInfoCharactersToggleLabel(collapsed) {
  if (!elements.infoCharactersToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.characters.toggle.expand'
    : 'index.sections.info.characters.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoCharactersToggle.setAttribute('data-i18n', key);
  elements.infoCharactersToggle.textContent = label;
  elements.infoCharactersToggle.setAttribute('aria-label', label);
}

function updateInfoCardsToggleLabel(collapsed) {
  if (!elements.infoCardsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.cards.toggle.expand'
    : 'index.sections.info.cards.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoCardsToggle.setAttribute('data-i18n', key);
  elements.infoCardsToggle.textContent = label;
  elements.infoCardsToggle.setAttribute('aria-label', label);
}

function updateInfoCalculationsToggleLabel(collapsed) {
  if (!elements.infoCalculationsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.calculations.toggle.expand'
    : 'index.sections.info.calculations.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoCalculationsToggle.setAttribute('data-i18n', key);
  elements.infoCalculationsToggle.textContent = label;
  elements.infoCalculationsToggle.setAttribute('aria-label', label);
}

function updateInfoProgressToggleLabel(collapsed) {
  if (!elements.infoProgressToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.progress.toggle.expand'
    : 'index.sections.info.progress.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoProgressToggle.setAttribute('data-i18n', key);
  elements.infoProgressToggle.textContent = label;
  elements.infoProgressToggle.setAttribute('aria-label', label);
}

function updateInfoScoresToggleLabel(collapsed) {
  if (!elements.infoScoresToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.info.scores.toggle.expand'
    : 'index.sections.info.scores.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.infoScoresToggle.setAttribute('data-i18n', key);
  elements.infoScoresToggle.textContent = label;
  elements.infoScoresToggle.setAttribute('aria-label', label);
}

function updateCollectionImagesToggleLabel(collapsed) {
  if (!elements.collectionImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.images.toggle.expand'
    : 'index.sections.collection.images.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionImagesToggle.setAttribute('data-i18n', key);
  elements.collectionImagesToggle.textContent = label;
  elements.collectionImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionBonusImagesToggleLabel(collapsed) {
  if (!elements.collectionBonusImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.bonusImages.toggle.expand'
    : 'index.sections.collection.bonusImages.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionBonusImagesToggle.setAttribute('data-i18n', key);
  elements.collectionBonusImagesToggle.textContent = label;
  elements.collectionBonusImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionBonus1ImagesToggleLabel(collapsed) {
  if (!elements.collectionBonus1ImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.bonus1Images.toggle.expand'
    : 'index.sections.collection.bonus1Images.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionBonus1ImagesToggle.setAttribute('data-i18n', key);
  elements.collectionBonus1ImagesToggle.textContent = label;
  elements.collectionBonus1ImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionBonus2ImagesToggleLabel(collapsed) {
  if (!elements.collectionBonus2ImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.bonus2Images.toggle.expand'
    : 'index.sections.collection.bonus2Images.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionBonus2ImagesToggle.setAttribute('data-i18n', key);
  elements.collectionBonus2ImagesToggle.textContent = label;
  elements.collectionBonus2ImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionBonus3ImagesToggleLabel(collapsed) {
  if (!elements.collectionBonus3ImagesToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.bonus3Images.toggle.expand'
    : 'index.sections.collection.bonus3Images.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionBonus3ImagesToggle.setAttribute('data-i18n', key);
  elements.collectionBonus3ImagesToggle.textContent = label;
  elements.collectionBonus3ImagesToggle.setAttribute('aria-label', label);
}

function updateCollectionDownloadsToggleLabel(collapsed) {
  if (!elements.collectionDownloadsToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.downloads.toggle.expand'
    : 'index.sections.collection.downloads.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionDownloadsToggle.setAttribute('data-i18n', key);
  elements.collectionDownloadsToggle.textContent = label;
  elements.collectionDownloadsToggle.setAttribute('aria-label', label);
}

function updateCollectionDownloadsRefreshLabel() {
  if (!elements.collectionDownloadsRefresh) {
    return;
  }
  const key = 'index.sections.collection.downloads.refresh';
  const fallback = 'Refresh';
  const label = translateOrDefault(key, fallback);
  elements.collectionDownloadsRefresh.setAttribute('data-i18n', key);
  elements.collectionDownloadsRefresh.textContent = label;
  elements.collectionDownloadsRefresh.setAttribute('aria-label', label);
  elements.collectionDownloadsRefresh.title = label;
}

function updateCollectionDownloadsRefreshState() {
  if (!elements.collectionDownloadsRefresh) {
    return;
  }
  const available = hasAndroidCollectionDownloadsBridge();
  elements.collectionDownloadsRefresh.disabled = !available;
  elements.collectionDownloadsRefresh.setAttribute('aria-disabled', available ? 'false' : 'true');
  elements.collectionDownloadsRefresh.hidden = !available;
  elements.collectionDownloadsRefresh.setAttribute('aria-hidden', available ? 'false' : 'true');
}

function updateCollectionVideosToggleLabel(collapsed) {
  if (!elements.collectionVideosToggle) {
    return;
  }
  const key = collapsed
    ? 'index.sections.collection.videos.toggle.expand'
    : 'index.sections.collection.videos.toggle.collapse';
  const fallback = collapsed ? 'Expand' : 'Collapse';
  const label = translateOrDefault(key, fallback);
  elements.collectionVideosToggle.setAttribute('data-i18n', key);
  elements.collectionVideosToggle.textContent = label;
  elements.collectionVideosToggle.setAttribute('aria-label', label);
}

function setInfoCharactersCollapsed(collapsed, options = {}) {
  if (!elements.infoCharactersCard || !elements.infoCharactersContent || !elements.infoCharactersToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoCharactersCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoCharactersContent.hidden = shouldCollapse;
  elements.infoCharactersContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoCharactersToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoCharactersToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_CHARACTERS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoCharactersCollapsed() {
  if (!elements.infoCharactersCard) {
    return;
  }
  const currentlyCollapsed = elements.infoCharactersCard.classList.contains('info-card--collapsed');
  setInfoCharactersCollapsed(!currentlyCollapsed);
}

function initInfoCharactersCard() {
  if (!elements.infoCharactersCard || !elements.infoCharactersContent || !elements.infoCharactersToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_CHARACTERS_COLLAPSED_STORAGE_KEY, false);
  setInfoCharactersCollapsed(initialCollapsed, { persist: false });
  elements.infoCharactersToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoCharactersCollapsed();
  });
}

function setInfoCardsCollapsed(collapsed, options = {}) {
  if (!elements.infoCardsCard || !elements.infoCardsContent || !elements.infoCardsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoCardsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoCardsContent.hidden = shouldCollapse;
  elements.infoCardsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoCardsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoCardsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_CARDS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoCardsCollapsed() {
  if (!elements.infoCardsCard) {
    return;
  }
  const currentlyCollapsed = elements.infoCardsCard.classList.contains('info-card--collapsed');
  setInfoCardsCollapsed(!currentlyCollapsed);
}

function initInfoCardsCard() {
  if (!elements.infoCardsCard || !elements.infoCardsContent || !elements.infoCardsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_CARDS_COLLAPSED_STORAGE_KEY, false);
  setInfoCardsCollapsed(initialCollapsed, { persist: false });
  elements.infoCardsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoCardsCollapsed();
  });
}

function setInfoCalculationsCollapsed(collapsed, options = {}) {
  if (!elements.infoCalculationsCard
    || !elements.infoCalculationsContent
    || !elements.infoCalculationsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoCalculationsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoCalculationsContent.hidden = shouldCollapse;
  elements.infoCalculationsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoCalculationsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoCalculationsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoCalculationsCollapsed() {
  if (!elements.infoCalculationsCard) {
    return;
  }
  const currentlyCollapsed = elements.infoCalculationsCard.classList.contains('info-card--collapsed');
  setInfoCalculationsCollapsed(!currentlyCollapsed);
}

function initInfoCalculationsCard() {
  if (!elements.infoCalculationsCard
    || !elements.infoCalculationsContent
    || !elements.infoCalculationsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_CALCULATIONS_COLLAPSED_STORAGE_KEY, false);
  setInfoCalculationsCollapsed(initialCollapsed, { persist: false });
  elements.infoCalculationsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoCalculationsCollapsed();
  });
}

function setInfoProgressCollapsed(collapsed, options = {}) {
  if (!elements.infoProgressCard
    || !elements.infoProgressContent
    || !elements.infoProgressToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoProgressCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoProgressContent.hidden = shouldCollapse;
  elements.infoProgressContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoProgressToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoProgressToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_PROGRESS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoProgressCollapsed() {
  if (!elements.infoProgressCard) {
    return;
  }
  const currentlyCollapsed = elements.infoProgressCard.classList.contains('info-card--collapsed');
  setInfoProgressCollapsed(!currentlyCollapsed);
}

function initInfoProgressCard() {
  if (!elements.infoProgressCard
    || !elements.infoProgressContent
    || !elements.infoProgressToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_PROGRESS_COLLAPSED_STORAGE_KEY, false);
  setInfoProgressCollapsed(initialCollapsed, { persist: false });
  elements.infoProgressToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoProgressCollapsed();
  });
}

function setInfoScoresCollapsed(collapsed, options = {}) {
  if (!elements.infoScoresCard
    || !elements.infoScoresContent
    || !elements.infoScoresToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.infoScoresCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.infoScoresContent.hidden = shouldCollapse;
  elements.infoScoresContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.infoScoresToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateInfoScoresToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(INFO_SCORES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleInfoScoresCollapsed() {
  if (!elements.infoScoresCard) {
    return;
  }
  const currentlyCollapsed = elements.infoScoresCard.classList.contains('info-card--collapsed');
  setInfoScoresCollapsed(!currentlyCollapsed);
}

function initInfoScoresCard() {
  if (!elements.infoScoresCard
    || !elements.infoScoresContent
    || !elements.infoScoresToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(INFO_SCORES_COLLAPSED_STORAGE_KEY, false);
  setInfoScoresCollapsed(initialCollapsed, { persist: false });
  elements.infoScoresToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleInfoScoresCollapsed();
  });
}

function setCollectionImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionImagesCard
    || !elements.collectionImagesContent
    || !elements.collectionImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionImagesContent.hidden = shouldCollapse;
  elements.collectionImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionBonusImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionBonusImagesCard
    || !elements.collectionBonusImagesContent
    || !elements.collectionBonusImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionBonusImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionBonusImagesContent.hidden = shouldCollapse;
  elements.collectionBonusImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionBonusImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionBonusImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionBonus1ImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionBonus1ImagesCard
    || !elements.collectionBonus1ImagesContent
    || !elements.collectionBonus1ImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionBonus1ImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionBonus1ImagesContent.hidden = shouldCollapse;
  elements.collectionBonus1ImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionBonus1ImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionBonus1ImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_BONUS1_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionBonus2ImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionBonus2ImagesCard
    || !elements.collectionBonus2ImagesContent
    || !elements.collectionBonus2ImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionBonus2ImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionBonus2ImagesContent.hidden = shouldCollapse;
  elements.collectionBonus2ImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionBonus2ImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionBonus2ImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionBonus3ImagesCollapsed(collapsed, options = {}) {
  if (!elements.collectionBonus3ImagesCard
    || !elements.collectionBonus3ImagesContent
    || !elements.collectionBonus3ImagesToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionBonus3ImagesCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionBonus3ImagesContent.hidden = shouldCollapse;
  elements.collectionBonus3ImagesContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionBonus3ImagesToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionBonus3ImagesToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_BONUS3_IMAGES_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionDownloadsCollapsed(collapsed, options = {}) {
  if (!elements.collectionDownloadsCard
    || !elements.collectionDownloadsContent
    || !elements.collectionDownloadsToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionDownloadsCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionDownloadsContent.hidden = shouldCollapse;
  elements.collectionDownloadsContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionDownloadsToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionDownloadsToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_DOWNLOADS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function setCollectionVideosCollapsed(collapsed, options = {}) {
  if (!elements.collectionVideosCard
    || !elements.collectionVideosContent
    || !elements.collectionVideosToggle) {
    return;
  }
  const shouldCollapse = !!collapsed;
  elements.collectionVideosCard.classList.toggle('info-card--collapsed', shouldCollapse);
  elements.collectionVideosContent.hidden = shouldCollapse;
  elements.collectionVideosContent.setAttribute('aria-hidden', shouldCollapse ? 'true' : 'false');
  elements.collectionVideosToggle.setAttribute('aria-expanded', shouldCollapse ? 'false' : 'true');
  updateCollectionVideosToggleLabel(shouldCollapse);
  if (options.persist !== false) {
    writeStoredInfoCardCollapsed(COLLECTION_VIDEOS_COLLAPSED_STORAGE_KEY, shouldCollapse);
  }
}

function toggleCollectionImagesCollapsed() {
  if (!elements.collectionImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionImagesCard.classList.contains('info-card--collapsed');
  setCollectionImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionBonusImagesCollapsed() {
  if (!elements.collectionBonusImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionBonusImagesCard.classList.contains('info-card--collapsed');
  setCollectionBonusImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionBonus1ImagesCollapsed() {
  if (!elements.collectionBonus1ImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionBonus1ImagesCard.classList.contains('info-card--collapsed');
  setCollectionBonus1ImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionBonus2ImagesCollapsed() {
  if (!elements.collectionBonus2ImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionBonus2ImagesCard.classList.contains('info-card--collapsed');
  setCollectionBonus2ImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionBonus3ImagesCollapsed() {
  if (!elements.collectionBonus3ImagesCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionBonus3ImagesCard.classList.contains('info-card--collapsed');
  setCollectionBonus3ImagesCollapsed(!currentlyCollapsed);
}

function toggleCollectionDownloadsCollapsed() {
  if (!elements.collectionDownloadsCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionDownloadsCard.classList.contains('info-card--collapsed');
  setCollectionDownloadsCollapsed(!currentlyCollapsed);
}

function handleCollectionDownloadsRefresh(event) {
  if (event && typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
  if (!hasAndroidBackgroundBridge()) {
    showToast(translateOrDefault('scripts.app.background.unsupported', 'This action requires the Android app.'));
    return;
  }
  requestNativeCollectionDownloads();
}

function toggleCollectionVideosCollapsed() {
  if (!elements.collectionVideosCard) {
    return;
  }
  const currentlyCollapsed = elements.collectionVideosCard.classList.contains('info-card--collapsed');
  setCollectionVideosCollapsed(!currentlyCollapsed);
}

function initCollectionImagesCard() {
  if (!elements.collectionImagesCard
    || !elements.collectionImagesContent
    || !elements.collectionImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(COLLECTION_IMAGES_COLLAPSED_STORAGE_KEY, false);
  setCollectionImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionImagesCollapsed();
  });
}

function initCollectionBonusImagesCard() {
  if (!elements.collectionBonusImagesCard
    || !elements.collectionBonusImagesContent
    || !elements.collectionBonusImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_BONUS_IMAGES_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionBonusImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionBonusImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionBonusImagesCollapsed();
  });
}

function initCollectionBonus1ImagesCard() {
  if (!elements.collectionBonus1ImagesCard
    || !elements.collectionBonus1ImagesContent
    || !elements.collectionBonus1ImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_BONUS1_IMAGES_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionBonus1ImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionBonus1ImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionBonus1ImagesCollapsed();
  });
}

function initCollectionBonus2ImagesCard() {
  if (!elements.collectionBonus2ImagesCard
    || !elements.collectionBonus2ImagesContent
    || !elements.collectionBonus2ImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_BONUS2_IMAGES_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionBonus2ImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionBonus2ImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionBonus2ImagesCollapsed();
  });
}

function initCollectionBonus3ImagesCard() {
  if (!elements.collectionBonus3ImagesCard
    || !elements.collectionBonus3ImagesContent
    || !elements.collectionBonus3ImagesToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_BONUS3_IMAGES_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionBonus3ImagesCollapsed(initialCollapsed, { persist: false });
  elements.collectionBonus3ImagesToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionBonus3ImagesCollapsed();
  });
}

function initCollectionDownloadsCard() {
  if (!elements.collectionDownloadsCard
    || !elements.collectionDownloadsContent
    || !elements.collectionDownloadsToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_DOWNLOADS_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionDownloadsCollapsed(initialCollapsed, { persist: false });
  updateCollectionDownloadsRefreshLabel();
  updateCollectionDownloadsRefreshState();
  elements.collectionDownloadsToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionDownloadsCollapsed();
  });
  if (elements.collectionDownloadsRefresh) {
    elements.collectionDownloadsRefresh.addEventListener('click', handleCollectionDownloadsRefresh);
  }
  renderCollectionDownloadsGallery(getCollectionDownloadsItems());
  if (hasAndroidCollectionDownloadsBridge()) {
    requestNativeCollectionDownloads();
  }
  updateCollectionDownloadsVisibility();
}

function initCollectionVideosCard() {
  if (!elements.collectionVideosCard
    || !elements.collectionVideosContent
    || !elements.collectionVideosToggle) {
    return;
  }
  const initialCollapsed = readStoredInfoCardCollapsed(
    COLLECTION_VIDEOS_COLLAPSED_STORAGE_KEY,
    false
  );
  setCollectionVideosCollapsed(initialCollapsed, { persist: false });
  elements.collectionVideosToggle.addEventListener('click', event => {
    event.preventDefault();
    toggleCollectionVideosCollapsed();
  });
}

function subscribeInfoWelcomeLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoWelcomeCard
      ? elements.infoWelcomeCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoWelcomeToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeInfoAchievementsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoAchievementsCard
      ? elements.infoAchievementsCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoAchievementsToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeInfoCharactersLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoCharactersCard
      ? elements.infoCharactersCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoCharactersToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeInfoCardsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoCardsCard
      ? elements.infoCardsCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoCardsToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeInfoCalculationsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoCalculationsCard
      ? elements.infoCalculationsCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoCalculationsToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeInfoProgressLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.infoProgressCard
      ? elements.infoProgressCard.classList.contains('info-card--collapsed')
      : false;
    updateInfoProgressToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionImagesCard
      ? elements.collectionImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionImagesToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionBonusImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionBonusImagesCard
      ? elements.collectionBonusImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionBonusImagesToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionBonus1ImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionBonus1ImagesCard
      ? elements.collectionBonus1ImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionBonus1ImagesToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionBonus2ImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionBonus2ImagesCard
      ? elements.collectionBonus2ImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionBonus2ImagesToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionBonus3ImagesLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionBonus3ImagesCard
      ? elements.collectionBonus3ImagesCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionBonus3ImagesToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionDownloadsLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionDownloadsCard
      ? elements.collectionDownloadsCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionDownloadsToggleLabel(collapsed);
    updateCollectionDownloadsRefreshLabel();
    renderCollectionDownloadsGallery(getCollectionDownloadsItems());
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeCollectionVideosLanguageUpdates() {
  const handler = () => {
    const collapsed = elements.collectionVideosCard
      ? elements.collectionVideosCard.classList.contains('info-card--collapsed')
      : false;
    updateCollectionVideosToggleLabel(collapsed);
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function subscribeBigBangLanguageUpdates() {
  const handler = () => {
    updateBigBangActionUI();
  };
  const api = getI18nApi();
  if (api && typeof api.onLanguageChanged === 'function') {
    api.onLanguageChanged(handler);
    return;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
    globalThis.addEventListener('i18n:languagechange', handler);
  }
}

function updatePageUnlockUI() {
  const buttonConfig = [
    ['gacha', elements.navGachaButton],
    ['tableau', elements.navTableButton],
    ['fusion', elements.navFusionButton],
    ['collection', elements.navCollectionButton]
  ];

  buttonConfig.forEach(([pageId, button]) => {
    const unlocked = isPageUnlocked(pageId);
    setNavButtonLockState(button, unlocked);
  });

  const tableauUnlocked = isPageUnlocked('tableau');
  setNavButtonLockState(elements.gachaPeriodicButton, tableauUnlocked);
  if (elements.gachaPeriodicLink) {
    elements.gachaPeriodicLink.hidden = !tableauUnlocked;
    elements.gachaPeriodicLink.setAttribute('aria-hidden', tableauUnlocked ? 'false' : 'true');
  }

  setNavButtonLockState(elements.navInfoButton, true);
  updateCollectionShortcutState();
  updateInfoBonusVisibility();

  ensureActivePageUnlocked();
}

function updatePrimaryNavigationLocks() {
  const atoms = gameState.atoms instanceof LayeredNumber
    ? gameState.atoms
    : toLayeredValue(gameState.atoms, 0);
  const shopUnlocked = atoms.compare(SHOP_UNLOCK_THRESHOLD) >= 0;
  setNavButtonLockState(elements.navShopButton, shopUnlocked);
  updateShopUnlockHint();

  updateInfoAchievementsVisibility();
  updateInfoDevkitVisibility();
  updateInfoCardsVisibility();
  updateCollectionImagesVisibility();
  updateCollectionVideosVisibility();
  updateCollectionBonusImagesVisibility();
  updateCollectionBonus1ImagesVisibility();
  updateCollectionBonus2ImagesVisibility();
  updateCollectionBonus3ImagesVisibility();
  updateCollectionDownloadsVisibility();
  updateMusicModuleVisibility();

  updateCollectionShortcutState();

  ensureActivePageUnlocked();
}

function getBigBangLevelBonus() {
  const raw = Number(gameState.bigBangLevelBonus ?? 0);
  if (!Number.isFinite(raw)) {
    return 0;
  }
  return Math.max(0, Math.floor(raw));
}

function setBigBangLevelBonus(value) {
  const numeric = Number(value);
  const sanitized = Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0;
  gameState.bigBangLevelBonus = sanitized;
  return sanitized;
}

function getBigBangCompletionCount() {
  const step = Number(BIG_BANG_LEVEL_BONUS_STEP);
  if (!Number.isFinite(step) || step <= 0) {
    return 0;
  }
  const bonus = getBigBangLevelBonus();
  if (!Number.isFinite(bonus) || bonus <= 0) {
    return 0;
  }
  return Math.max(0, Math.floor(bonus / step));
}

function collectPreservedUpgradesForBigBang() {
  const preserved = new Map();
  UPGRADE_DEFS.forEach(def => {
    if (!def?.preserveLevelOnBigBang) {
      return;
    }
    const level = getUpgradeLevel(gameState.upgrades, def.id);
    if (level > 0) {
      preserved.set(def.id, level);
    }
  });
  return preserved;
}

function restorePreservedUpgradesAfterBigBang(preserved) {
  if (!(preserved instanceof Map) || preserved.size === 0) {
    return;
  }
  preserved.forEach((level, id) => {
    const index = UPGRADE_INDEX_MAP.get(id);
    const def = Number.isInteger(index) ? UPGRADE_DEFS[index] : null;
    if (!def) {
      return;
    }
    const maxLevel = resolveUpgradeMaxLevel(def);
    const clampedLevel = Number.isFinite(maxLevel) ? Math.min(level, maxLevel) : level;
    if (clampedLevel > 0) {
      gameState.upgrades[id] = clampedLevel;
    }
  });
}

const BIG_BANG_REQUIRED_SHOP_IDS = ['godFinger', 'starCore'];

function getBigBangRequiredUpgrades() {
  if (!Array.isArray(UPGRADE_DEFS) || UPGRADE_DEFS.length === 0) {
    return [];
  }
  const requiredIds = new Set(BIG_BANG_REQUIRED_SHOP_IDS);
  return UPGRADE_DEFS.filter(def => requiredIds.has(def.id));
}

function getBigBangRequiredShopNames() {
  return BIG_BANG_REQUIRED_SHOP_IDS.map(id => UPGRADE_NAME_MAP.get(id) || id);
}

function getBigBangRemainingLevels() {
  const requiredUpgrades = getBigBangRequiredUpgrades();
  if (!requiredUpgrades.length) {
    return { total: 0, infinite: false, display: '0' };
  }
  let total = 0;
  let infinite = false;
  requiredUpgrades.forEach(def => {
    const remaining = getRemainingUpgradeCapacity(def);
    if (!Number.isFinite(remaining)) {
      infinite = true;
    } else {
      total += remaining;
    }
  });
  const display = infinite ? '∞' : formatIntegerLocalized(total);
  return { total, infinite, display };
}

function isBigBangUnlocked() {
  const { total, infinite } = getBigBangRemainingLevels();
  return !infinite && total <= 0;
}

function getBigBangBonusStepDisplay() {
  return formatIntegerLocalized(BIG_BANG_LEVEL_BONUS_STEP);
}

function canPerformBigBang() {
  return isBigBangUnlocked();
}

function updateBigBangActionUI() {
  const bonusTotal = getBigBangLevelBonus();
  const bonusDisplay = formatIntegerLocalized(bonusTotal);
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  if (elements.bigBangSummary) {
    const summaryFallback = `Réinitialise vos atomes actuels ainsi que les deux pistes du magasin pour augmenter leur plafond de +${bonusStepDisplay} niveaux.`;
    const summaryText = translateOrDefault(
      'index.sections.bigbang.restart.summary',
      summaryFallback,
      { bonus: bonusStepDisplay }
    );
    elements.bigBangSummary.textContent = summaryText;
  }
  if (elements.bigBangBonusInfo) {
    const fallbackBonus = `Niveaux supplémentaires débloqués : +${bonusDisplay}`;
    elements.bigBangBonusInfo.textContent = translateOrDefault(
      'index.sections.bigbang.restart.bonus',
      fallbackBonus,
      { bonus: bonusDisplay }
    );
  }
  if (elements.bigBangRequirement) {
    const remainingInfo = getBigBangRemainingLevels();
    const ready = canPerformBigBang();
    const [firstShopName = 'Doigt créateur', secondShopName = 'Cœur d’étoile'] = getBigBangRequiredShopNames();
    const key = ready
      ? 'index.sections.bigbang.restart.requirementReady'
      : 'index.sections.bigbang.restart.requirementLocked';
    const fallback = ready
      ? `${firstShopName} et ${secondShopName} sont au maximum ! Lancez un nouveau Big Bang pour ajouter +${bonusStepDisplay} niveaux au magasin.`
      : `Montez ${firstShopName} et ${secondShopName} au maximum (${remainingInfo.display} niveaux restants) pour déclencher un nouveau Big Bang.`;
    const message = translateOrDefault(key, fallback, {
      remaining: remainingInfo.display,
      bonus: bonusStepDisplay,
      firstShop: firstShopName,
      secondShop: secondShopName
    });
    elements.bigBangRequirement.textContent = message;
    if (elements.bigBangRestartButton) {
      elements.bigBangRestartButton.title = message;
      const actionLabel = translateOrDefault(
        'index.sections.bigbang.restart.cta',
        elements.bigBangRestartButton.textContent?.trim() || 'Trigger the Big Bang'
      );
      elements.bigBangRestartButton.setAttribute('aria-label', `${actionLabel} — ${message}`);
    }
  }
  if (elements.bigBangRestartButton) {
    const ready = canPerformBigBang();
    elements.bigBangRestartButton.disabled = !ready;
    elements.bigBangRestartButton.setAttribute('aria-disabled', ready ? 'false' : 'true');
  }
  updateBigBangDialogCopy();
}

function updateBigBangDialogCopy() {
  if (!elements.bigBangDialog) {
    return;
  }
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  if (elements.bigBangDialogTitle) {
    elements.bigBangDialogTitle.textContent = translateOrDefault(
      'index.sections.bigbang.confirm.title',
      'Confirmer le Big Bang'
    );
  }
  if (elements.bigBangDialogMessage) {
    const fallback = `Relancer l’univers ? Vous perdrez vos atomes actuels, vos APC/APS et les niveaux du magasin, mais débloquerez +${bonusStepDisplay} niveaux supplémentaires.`;
    elements.bigBangDialogMessage.textContent = translateOrDefault(
      'index.sections.bigbang.confirm.message',
      fallback,
      { bonus: bonusStepDisplay }
    );
    elements.bigBangDialog.setAttribute('aria-describedby', 'bigBangDialogMessage');
  } else {
    elements.bigBangDialog.removeAttribute('aria-describedby');
  }
  if (elements.bigBangDialogCancel) {
    elements.bigBangDialogCancel.textContent = translateOrDefault(
      'index.sections.bigbang.confirm.cancel',
      'Annuler'
    );
  }
  if (elements.bigBangDialogConfirm) {
    const actionLabel = translateOrDefault(
      'index.sections.bigbang.confirm.confirm',
      'Déclencher le Big Bang'
    );
    elements.bigBangDialogConfirm.textContent = actionLabel;
    elements.bigBangDialogConfirm.setAttribute('aria-label', actionLabel);
  }
}

function getBigBangDialogFocusableElements() {
  if (!elements.bigBangDialog) {
    return [];
  }
  return Array.from(
    elements.bigBangDialog.querySelectorAll(BIG_BANG_DIALOG_FOCUSABLE_SELECTOR)
  ).filter(node => node instanceof HTMLElement && !node.hasAttribute('disabled') && !node.hidden);
}

function handleBigBangDialogKeydown(event) {
  if (!bigBangDialogState.isOpen) {
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    closeBigBangDialog({ cancelled: true });
    return;
  }
  if (event.key !== 'Tab') {
    return;
  }
  const focusable = getBigBangDialogFocusableElements();
  if (!focusable.length) {
    event.preventDefault();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document && document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  if (event.shiftKey) {
    if (!active || active === first) {
      event.preventDefault();
      last.focus();
    }
  } else if (active === last) {
    event.preventDefault();
    first.focus();
  }
}

function openBigBangDialog() {
  if (
    !elements.bigBangDialog
    || !elements.bigBangDialogConfirm
    || !elements.bigBangDialogCancel
  ) {
    return false;
  }
  if (bigBangDialogState.isOpen) {
    return true;
  }
  bigBangDialogState.isOpen = true;
  bigBangDialogState.previousFocus = document && document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  updateBigBangDialogCopy();
  elements.bigBangDialog.hidden = false;
  document.addEventListener('keydown', handleBigBangDialogKeydown);
  requestAnimationFrame(() => {
    const focusTarget = elements.bigBangDialogConfirm || elements.bigBangDialogCancel;
    if (focusTarget && typeof focusTarget.focus === 'function') {
      focusTarget.focus();
    }
  });
  return true;
}

function closeBigBangDialog({ cancelled = false } = {}) {
  if (!bigBangDialogState.isOpen) {
    return;
  }
  bigBangDialogState.isOpen = false;
  if (elements.bigBangDialog) {
    elements.bigBangDialog.hidden = true;
  }
  document.removeEventListener('keydown', handleBigBangDialogKeydown);
  const { previousFocus } = bigBangDialogState;
  bigBangDialogState.previousFocus = null;
  const focusTarget = previousFocus && typeof previousFocus.focus === 'function'
    ? previousFocus
    : elements.bigBangRestartButton && typeof elements.bigBangRestartButton.focus === 'function'
      ? elements.bigBangRestartButton
      : null;
  if (focusTarget) {
    focusTarget.focus();
  }
  if (cancelled) {
    showToast(translateOrDefault('scripts.app.bigBang.cancelled', 'Big Bang annulé'));
  }
}

function handleBigBangDialogConfirm(event) {
  event.preventDefault();
  if (!canPerformBigBang()) {
    closeBigBangDialog();
    const remainingInfo = getBigBangRemainingLevels();
    const [firstShopName = 'Doigt créateur', secondShopName = 'Cœur d’étoile'] = getBigBangRequiredShopNames();
    const fallback = `Montez ${firstShopName} et ${secondShopName} au maximum (${remainingInfo.display} niveaux restants) pour relancer l’univers.`;
    showToast(translateOrDefault(
      'scripts.app.bigBang.notReady',
      fallback,
      { remaining: remainingInfo.display, firstShop: firstShopName, secondShop: secondShopName }
    ));
    return;
  }
  closeBigBangDialog();
  performBigBang();
}

function handleBigBangDialogCancel(event) {
  event.preventDefault();
  closeBigBangDialog({ cancelled: true });
}

function handleBigBangDialogBackdrop(event) {
  if (!elements.bigBangDialog || event.target !== elements.bigBangDialog) {
    return;
  }
  event.preventDefault();
  closeBigBangDialog({ cancelled: true });
}

function handleBigBangRestart() {
  if (!canPerformBigBang()) {
    const remainingInfo = getBigBangRemainingLevels();
    const [firstShopName = 'Doigt créateur', secondShopName = 'Cœur d’étoile'] = getBigBangRequiredShopNames();
    const fallback = `Montez ${firstShopName} et ${secondShopName} au maximum (${remainingInfo.display} niveaux restants) pour relancer l’univers.`;
    showToast(translateOrDefault(
      'scripts.app.bigBang.notReady',
      fallback,
      { remaining: remainingInfo.display, firstShop: firstShopName, secondShop: secondShopName }
    ));
    return;
  }
  if (openBigBangDialog()) {
    return;
  }
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  const confirmMessage = translateOrDefault(
    'scripts.app.bigBang.confirm',
    `Relancer l’univers ? Vous perdrez vos atomes actuels et les niveaux du magasin, mais gagnerez +${bonusStepDisplay} niveaux maximum.`,
    { bonus: bonusStepDisplay }
  );
  const confirmFn = typeof window !== 'undefined' && typeof window.confirm === 'function'
    ? window.confirm
    : null;
  if (confirmFn && !confirmFn(confirmMessage)) {
    showToast(translateOrDefault('scripts.app.bigBang.cancelled', 'Big Bang annulé'));
    return;
  }
  performBigBang();
}

function performBigBang() {
  const preservedUpgrades = collectPreservedUpgradesForBigBang();
  const totalBonus = setBigBangLevelBonus(getBigBangLevelBonus() + BIG_BANG_LEVEL_BONUS_STEP);
  resetPendingProductionGains();
  gameState.atoms = LayeredNumber.zero();
  gameState.perClick = BASE_PER_CLICK.clone();
  gameState.perSecond = BASE_PER_SECOND.clone();
  gameState.basePerClick = BASE_PER_CLICK.clone();
  gameState.basePerSecond = BASE_PER_SECOND.clone();
  gameState.upgrades = {};
  gameState.shopUnlocks = new Set();
  restorePreservedUpgradesAfterBigBang(preservedUpgrades);
  recalcProduction();
  renderShop();
  invalidateFeatureUnlockCache();
  updateBigBangActionUI();
  updateUI();
  saveGame();
  const bonusStepDisplay = getBigBangBonusStepDisplay();
  const totalDisplay = formatIntegerLocalized(totalBonus);
  const fallback = `Big Bang réussi ! Cap du magasin augmenté de +${bonusStepDisplay} (total +${totalDisplay}).`;
  showToast(translateOrDefault('scripts.app.bigBang.done', fallback, {
    bonus: bonusStepDisplay,
    total: totalDisplay
  }));
}

function resetPendingProductionGains(now = getLoopTimestamp()) {
  performanceModeState.pendingManualGain = null;
  performanceModeState.pendingAutoGain = null;
  performanceModeState.autoAccumulatedMs = 0;
  performanceModeState.lastManualFlush = now;
  performanceModeState.lastAutoFlush = now;
}

function updateBigBangVisibility() {
  const unlocked = isBigBangUnlocked();
  if (!unlocked && gameState.bigBangButtonVisible) {
    gameState.bigBangButtonVisible = false;
  }
  if (unlocked && !lastBigBangUnlockedState && !gameState.bigBangButtonVisible) {
    gameState.bigBangButtonVisible = true;
  }
  if (elements.bigBangOptionCard) {
    elements.bigBangOptionCard.hidden = !unlocked;
  }
  if (elements.bigBangOptionToggle) {
    elements.bigBangOptionToggle.disabled = !unlocked;
    elements.bigBangOptionToggle.checked = unlocked && gameState.bigBangButtonVisible === true;
  }
  const shouldShowButton = unlocked
    && (elements.bigBangOptionToggle ? gameState.bigBangButtonVisible === true : true);
  if (elements.navBigBangButton) {
    elements.navBigBangButton.toggleAttribute('hidden', !shouldShowButton);
    elements.navBigBangButton.setAttribute('aria-hidden', shouldShowButton ? 'false' : 'true');
  }
  if (!shouldShowButton && document.body && document.body.dataset.activePage === 'bigbang') {
    showPage('game');
  }
  lastBigBangUnlockedState = unlocked;
  updateBigBangActionUI();
}

function isArcadeUnlocked() {
  return isFeatureUnlocked('arcade.hub');
}

function triggerBrandPortalPulse() {
  const pulseTarget = elements.brandPortal || elements.navArcadeButton;
  if (!pulseTarget) {
    return;
  }
  const pulseClass = pulseTarget === elements.brandPortal ? 'brand--pulse' : 'nav-button--pulse';
  pulseTarget.classList.add(pulseClass);
  clearTimeout(triggerBrandPortalPulse.timeoutId);
  triggerBrandPortalPulse.timeoutId = setTimeout(() => {
    if (elements.brandPortal) {
      elements.brandPortal.classList.remove('brand--pulse');
    }
    if (elements.navArcadeButton) {
      elements.navArcadeButton.classList.remove('nav-button--pulse');
    }
  }, 1600);
}

function updateArcadeTicketDisplay() {
  const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const ticketLabel = formatTicketLabel(available);
  if (elements.arcadeTicketValues?.length) {
    elements.arcadeTicketValues.forEach(valueElement => {
      valueElement.textContent = ticketLabel;
    });
  }
  if (elements.arcadeTicketButtons?.length) {
    const gachaUnlocked = isPageUnlocked('gacha');
    elements.arcadeTicketButtons.forEach(button => {
      button.disabled = !gachaUnlocked;
      button.setAttribute('aria-disabled', gachaUnlocked ? 'false' : 'true');
      if (gachaUnlocked) {
        button.setAttribute('aria-label', `Ouvrir le portail Gacha (${ticketLabel})`);
        button.title = `Tickets disponibles : ${ticketLabel}`;
      } else {
        button.setAttribute('aria-label', 'Portail Gacha verrouillé');
        button.title = 'Obtenez un ticket de tirage pour débloquer le portail Gacha';
      }
    });
  }
  const bonusCount = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
  const bonusValue = formatIntegerLocalized(bonusCount);
  const bonusLabel = formatMetauxCreditLabel(bonusCount);
  if (elements.arcadeBonusTicketValues?.length) {
    elements.arcadeBonusTicketValues.forEach(valueElement => {
      valueElement.textContent = bonusValue;
    });
  }
  if (elements.arcadeBonusTicketAnnouncements?.length) {
    elements.arcadeBonusTicketAnnouncements.forEach(announcement => {
      announcement.textContent = `Mach3 : ${bonusLabel}`;
    });
  }
  if (elements.arcadeBonusTicketButtons?.length) {
    const hasCredits = bonusCount > 0;
    elements.arcadeBonusTicketButtons.forEach(button => {
      button.disabled = !hasCredits;
      button.setAttribute('aria-disabled', hasCredits ? 'false' : 'true');
      if (hasCredits) {
        button.title = `Lancer Métaux — ${bonusLabel}`;
        button.setAttribute('aria-label', `Ouvrir Métaux (Mach3 : ${bonusLabel})`);
      } else {
        button.title = 'Attrapez un graviton pour gagner un Mach3.';
        button.setAttribute('aria-label', 'Mach3 indisponible — attrapez un graviton.');
      }
    });
  }
  updateMetauxCreditsUI();
}

function getMetauxCreditCount() {
  return Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
}

function formatMetauxCreditLabel(count) {
  if (typeof formatBonusTicketLabel === 'function') {
    return formatBonusTicketLabel(count);
  }
  const numeric = Math.max(0, Math.floor(Number(count) || 0));
  const unit = numeric === 1 ? 'crédit' : 'crédits';
  return `${formatIntegerLocalized(numeric)} ${unit}`;
}

function isMetauxSessionRunning() {
  if (!metauxGame) {
    return false;
  }
  if (typeof metauxGame.isSessionRunning === 'function') {
    return metauxGame.isSessionRunning();
  }
  return metauxGame.gameOver === false;
}

function updateMetauxCreditsUI() {
  const available = getMetauxCreditCount();
  const active = isMetauxSessionRunning();
  if (elements.metauxNewGameCredits) {
    elements.metauxNewGameCredits.textContent = `Mach3 : ${formatMetauxCreditLabel(available)}`;
  }
  if (elements.metauxNewGameButton) {
    const canStart = available > 0 && !active;
    elements.metauxNewGameButton.disabled = !canStart;
    elements.metauxNewGameButton.setAttribute('aria-disabled', canStart ? 'false' : 'true');
    const tooltip = canStart
      ? `Consomme 1 crédit Mach3 (restant : ${formatMetauxCreditLabel(available)}).`
      : available > 0
        ? 'Partie en cours… Terminez-la avant de relancer.'
        : 'Aucun crédit Mach3 disponible. Jouez à Atom2Univers pour en gagner.';
    elements.metauxNewGameButton.title = tooltip;
    elements.metauxNewGameButton.setAttribute('aria-label', `${available > 0 ? 'Nouvelle partie' : 'Crédit indisponible'} — ${tooltip}`);
  }
  if (elements.metauxFreePlayButton) {
    const disabled = active;
    elements.metauxFreePlayButton.disabled = disabled;
    elements.metauxFreePlayButton.setAttribute('aria-disabled', disabled ? 'true' : 'false');
    const tooltip = disabled
      ? 'Partie en cours… Terminez-la avant de relancer.'
      : 'Partie libre : aucun crédit requis.';
    elements.metauxFreePlayButton.title = tooltip;
    const label = (elements.metauxFreePlayButton.textContent || '').trim() || 'Free Play';
    elements.metauxFreePlayButton.setAttribute('aria-label', `${label} — ${tooltip}`);
  }
  if (elements.metauxCreditStatus) {
    let statusText = '';
    const freeMode = metauxGame && typeof metauxGame.isFreePlayMode === 'function' && metauxGame.isFreePlayMode();
    if (active) {
      statusText = freeMode
        ? 'Partie libre en cours — expérimentez sans pression.'
        : 'Forge en cours… Utilisez vos déplacements pour créer des alliages !';
    } else if (available > 0) {
      statusText = `Crédits disponibles : ${formatMetauxCreditLabel(available)}.`;
    } else {
      statusText = 'Aucun crédit Mach3 disponible. Lancez une partie libre ou jouez à Atom2Univers pour en gagner.';
    }
    elements.metauxCreditStatus.textContent = statusText;
    elements.metauxCreditStatus.hidden = false;
  }
}
