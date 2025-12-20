function applySerializedGameState(raw) {
  const data = JSON.parse(raw);
  gameState.atoms = LayeredNumber.fromJSON(data.atoms);
  gameState.lifetime = LayeredNumber.fromJSON(data.lifetime);
  gameState.perClick = LayeredNumber.fromJSON(data.perClick);
  gameState.perSecond = LayeredNumber.fromJSON(data.perSecond);
  const tickets = Number(data.gachaTickets);
  const storedTickets = Number.isFinite(tickets) && tickets > 0 ? Math.floor(tickets) : 0;
  setGachaTicketsSilently(storedTickets);
  const bonusTickets = Number(data.bonusParticulesTickets ?? data.bonusTickets);
  gameState.bonusParticulesTickets = Number.isFinite(bonusTickets) && bonusTickets > 0
    ? Math.floor(bonusTickets)
    : 0;
  const storedFeatureFlags = data.featureUnlockFlags ?? data.featureFlags ?? null;
  gameState.featureUnlockFlags = normalizeFeatureUnlockFlags(storedFeatureFlags);
  const storedTicketStarUnlock = data.ticketStarUnlocked ?? data.ticketStarUnlock;
  if (storedTicketStarUnlock != null) {
    gameState.ticketStarUnlocked = storedTicketStarUnlock === true
      || storedTicketStarUnlock === 'true'
      || storedTicketStarUnlock === 1
      || storedTicketStarUnlock === '1';
  } else {
    gameState.ticketStarUnlocked = gameState.gachaTickets > 0;
  }
  gameState.arcadeProgress = normalizeArcadeProgress(data.arcadeProgress);
  syncMotocrossRecordsFromSave();
  applyDerivedFeatureUnlockFlags();
  const storedUpgrades = data.upgrades;
  if (storedUpgrades && typeof storedUpgrades === 'object') {
    const normalizedUpgrades = {};
    Object.entries(storedUpgrades).forEach(([id, value]) => {
      const numeric = Number(value);
      if (!Number.isFinite(numeric)) {
        return;
      }
      const sanitized = Math.max(0, Math.floor(numeric));
      if (sanitized > 0) {
        normalizedUpgrades[id] = sanitized;
      }
    });
    gameState.upgrades = normalizedUpgrades;
  } else {
    gameState.upgrades = {};
  }
  const storedShopUnlocks = data.shopUnlocks;
  if (Array.isArray(storedShopUnlocks)) {
    gameState.shopUnlocks = new Set(storedShopUnlocks);
  } else if (storedShopUnlocks && typeof storedShopUnlocks === 'object') {
    gameState.shopUnlocks = new Set(Object.keys(storedShopUnlocks));
  } else {
    gameState.shopUnlocks = new Set();
  }
  gameState.stats = parseStats(data.stats);
  gameState.trophies = new Set(Array.isArray(data.trophies) ? data.trophies : []);
  const storedPageUnlocks = data.pageUnlocks;
  if (storedPageUnlocks && typeof storedPageUnlocks === 'object') {
    const baseUnlocks = createInitialPageUnlockState();
    Object.keys(baseUnlocks).forEach(key => {
      const rawValue = storedPageUnlocks[key];
      baseUnlocks[key] = rawValue === true || rawValue === 'true' || rawValue === 1;
    });
    gameState.pageUnlocks = baseUnlocks;
  } else {
    gameState.pageUnlocks = createInitialPageUnlockState();
  }
  const storedBigBangBonus = Number(
    data.bigBangLevelBonus
    ?? data.bigBangBonus
    ?? data.bigBangLevels
    ?? data.bigBangLevel
    ?? 0
  );
  setBigBangLevelBonus(storedBigBangBonus);
  const storedBigBangPreference =
    data.bigBangButtonVisible ?? data.showBigBangButton ?? data.bigBangVisible ?? null;
  const wantsBigBang =
    storedBigBangPreference === true
    || storedBigBangPreference === 'true'
    || storedBigBangPreference === 1
    || storedBigBangPreference === '1';
  const hasBigBangUnlock = isBigBangUnlocked();
  gameState.bigBangButtonVisible = wantsBigBang && hasBigBangUnlock;
  const storedOffline = Number(data.offlineGainMultiplier);
  if (Number.isFinite(storedOffline) && storedOffline > 0) {
    gameState.offlineGainMultiplier = Math.min(MYTHIQUE_OFFLINE_CAP, storedOffline);
  } else {
    gameState.offlineGainMultiplier = MYTHIQUE_OFFLINE_BASE;
  }
  const storedOfflineTickets = data.offlineTickets;
  if (storedOfflineTickets && typeof storedOfflineTickets === 'object') {
    const secondsPerTicket = Number(storedOfflineTickets.secondsPerTicket);
    const normalizedSecondsPerTicket = Number.isFinite(secondsPerTicket) && secondsPerTicket > 0
      ? secondsPerTicket
      : OFFLINE_TICKET_CONFIG.secondsPerTicket;
    const capSeconds = Number(storedOfflineTickets.capSeconds);
    const normalizedCapSeconds = Number.isFinite(capSeconds) && capSeconds > 0
      ? Math.max(capSeconds, normalizedSecondsPerTicket)
      : Math.max(OFFLINE_TICKET_CONFIG.capSeconds, normalizedSecondsPerTicket);
    const progressSeconds = Number(storedOfflineTickets.progressSeconds);
    const normalizedProgressSeconds = Number.isFinite(progressSeconds) && progressSeconds > 0
      ? Math.max(0, Math.min(progressSeconds, normalizedCapSeconds))
      : 0;
    gameState.offlineTickets = {
      secondsPerTicket: normalizedSecondsPerTicket,
      capSeconds: normalizedCapSeconds,
      progressSeconds: normalizedProgressSeconds
    };
  } else if (typeof storedOfflineTickets === 'number' && storedOfflineTickets > 0) {
    const interval = Number(storedOfflineTickets);
    const normalizedSecondsPerTicket = Number.isFinite(interval) && interval > 0
      ? interval
      : OFFLINE_TICKET_CONFIG.secondsPerTicket;
    const normalizedCapSeconds = Math.max(
      OFFLINE_TICKET_CONFIG.capSeconds,
      normalizedSecondsPerTicket
    );
    gameState.offlineTickets = {
      secondsPerTicket: normalizedSecondsPerTicket,
      capSeconds: normalizedCapSeconds,
      progressSeconds: 0
    };
  } else {
    gameState.offlineTickets = {
      secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
      capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
      progressSeconds: 0
    };
  }
  gameState.sudokuOfflineBonus = normalizeSudokuOfflineBonusState(data.sudokuOfflineBonus);
  const storedInterval = Number(data.ticketStarIntervalSeconds);
  if (Number.isFinite(storedInterval) && storedInterval > 0) {
    gameState.ticketStarAverageIntervalSeconds = storedInterval;
  } else {
    gameState.ticketStarAverageIntervalSeconds = DEFAULT_TICKET_STAR_INTERVAL_SECONDS;
  }
  const storedAutoCollect = data.ticketStarAutoCollect;
  let autoCollectFromSave = null;
  if (storedAutoCollect && typeof storedAutoCollect === 'object') {
    const rawDelay = storedAutoCollect.delaySeconds
      ?? storedAutoCollect.delay
      ?? storedAutoCollect.seconds
      ?? storedAutoCollect.value;
    const delaySeconds = Number(rawDelay);
    autoCollectFromSave = Number.isFinite(delaySeconds) && delaySeconds >= 0
      ? { delaySeconds }
      : null;
  } else if (storedAutoCollect === true) {
    autoCollectFromSave = { delaySeconds: 0 };
  } else if (typeof storedAutoCollect === 'number' && Number.isFinite(storedAutoCollect) && storedAutoCollect >= 0) {
    autoCollectFromSave = { delaySeconds: storedAutoCollect };
  }
  gameState.ticketStarAutoCollect = autoCollectFromSave;
  const storedAutoCollectEnabledRaw =
    data.ticketStarAutoCollectEnabled
    ?? data.ticketStarAutoCollectEnable
    ?? data.ticketStarAutoCollectOn;
  const storedAutoCollectEnabled = storedAutoCollectEnabledRaw === true
    || storedAutoCollectEnabledRaw === 'true'
    || storedAutoCollectEnabledRaw === 1
    || storedAutoCollectEnabledRaw === '1';
  if (autoCollectFromSave || storedAutoCollectEnabledRaw != null) {
    getUnlockedTrophySet().add(TICKET_STAR_AUTO_COLLECT_TROPHY_ID);
  }
  applyTicketStarAutoCollectEnabled(
    storedAutoCollectEnabled || storedAutoCollectEnabledRaw === false
      ? storedAutoCollectEnabled
      : ticketStarAutoCollectPreference,
    {
      persist: storedAutoCollectEnabledRaw != null,
      updateControl: true
    }
  );
  updateTicketStarAutoCollectOptionVisibility();
  const storedTicketStarSprite = data.ticketStarSpriteId ?? data.ticketStarSprite;
  applyTicketStarSpritePreference(
    storedTicketStarSprite != null ? storedTicketStarSprite : getTicketStarSpritePreference(),
    {
      persist: storedTicketStarSprite != null,
      updateControl: true,
      refresh: true
    }
  );
  updateTicketStarSpriteOptionVisibility();
  gameState.ticketStarSpecialChance = clampTicketStarSpecialChance(
    data.ticketStarSpecialChance ?? data.ticketStarSpecialRate
  );
  gameState.ticketStarSpecialReward = normalizeTicketStarSpecialReward(
    data.ticketStarSpecialReward ?? data.ticketStarSpecialTickets
  );
  const storedFrenzyBonus = data.frenzySpawnBonus;
  if (storedFrenzyBonus && typeof storedFrenzyBonus === 'object') {
    const perClick = Number(storedFrenzyBonus.perClick);
    const perSecond = Number(storedFrenzyBonus.perSecond);
    const addPerClick = Number(storedFrenzyBonus.addPerClick ?? storedFrenzyBonus.perClickAdd);
    const addPerSecond = Number(storedFrenzyBonus.addPerSecond ?? storedFrenzyBonus.perSecondAdd);
    gameState.frenzySpawnBonus = {
      perClick: Number.isFinite(perClick) && perClick > 0 ? perClick : 1,
      perSecond: Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1,
      addPerClick: Number.isFinite(addPerClick) && addPerClick > 0 ? addPerClick : 0,
      addPerSecond: Number.isFinite(addPerSecond) && addPerSecond > 0 ? addPerSecond : 0
    };
  } else {
    gameState.frenzySpawnBonus = { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 };
  }
  applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
  gameState.apsCrit = normalizeApsCritState(data.apsCrit);
  const storedApsCritEffects = data.apsCrit?.effects;
  if (Array.isArray(storedApsCritEffects) && storedApsCritEffects.length > 0) {
    gameState.apsCrit.effects = storedApsCritEffects
      .map(effect => ({
        remainingSeconds: Math.max(0, Number(effect?.remainingSeconds) || 0),
        multiplierAdd: Math.max(0, Number(effect?.multiplierAdd) || 0)
      }))
      .filter(effect => effect.remainingSeconds > 0 && effect.multiplierAdd > 0);
  }
  const storedBigBangState = data.bigBangButtonVisible ?? data.bigBangVisible;
  if (typeof storedBigBangState === 'boolean') {
    gameState.bigBangButtonVisible = storedBigBangState && isBigBangUnlocked();
  }
  const storedElementSummary = data.elementBonusSummary;
  gameState.elementBonusSummary = storedElementSummary && typeof storedElementSummary === 'object'
    ? storedElementSummary
    : {};
  const storedGachaCards = data.gachaCards;
  if (storedGachaCards && typeof storedGachaCards === 'object') {
    const base = createInitialGachaCardCollection();
    Object.keys(base).forEach(id => {
      const stored = storedGachaCards[id];
      if (!stored || typeof stored !== 'object') {
        base[id] = { owned: 0, seen: 0, level: 0 };
        return;
      }
      const owned = Number(stored.owned ?? stored.count ?? stored.total);
      const seen = Number(stored.seen ?? stored.viewed ?? stored.visited);
      const level = Number(stored.level ?? stored.rank);
      base[id] = {
        owned: Number.isFinite(owned) && owned > 0 ? Math.floor(owned) : 0,
        seen: Number.isFinite(seen) && seen > 0 ? Math.floor(seen) : 0,
        level: Number.isFinite(level) && level > 0 ? Math.floor(level) : 0
      };
    });
    gameState.gachaCards = base;
  } else {
    gameState.gachaCards = createInitialGachaCardCollection();
  }
  const storedGachaImages = data.gachaImages;
  const baseImageCollection = createInitialGachaImageCollection();
  let inferredImageAcquisitionCounter = 0;
  if (storedGachaImages && typeof storedGachaImages === 'object') {
    Object.entries(storedGachaImages).forEach(([imageId, stored]) => {
      const reference = baseImageCollection[imageId];
      if (!reference) {
        return;
      }
      const rawCount = Number(stored?.count ?? stored);
      const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
        ? Math.floor(rawCount)
        : 0;
      reference.count = normalizedCount;
      if (normalizedCount > 0) {
        const storedOrder = Number(stored?.acquiredOrder);
        if (Number.isFinite(storedOrder) && storedOrder > 0) {
          reference.acquiredOrder = Math.floor(storedOrder);
          if (reference.acquiredOrder > inferredImageAcquisitionCounter) {
            inferredImageAcquisitionCounter = reference.acquiredOrder;
          }
        }
        const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
        if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
          reference.firstAcquiredAt = storedFirstAcquiredAt;
        }
      }
    });
  }
  gameState.gachaImages = baseImageCollection;
  const storedImageCounter = Number(data.gachaImageAcquisitionCounter);
  if (Number.isFinite(storedImageCounter) && storedImageCounter > inferredImageAcquisitionCounter) {
    gameState.gachaImageAcquisitionCounter = Math.floor(storedImageCounter);
  } else {
    gameState.gachaImageAcquisitionCounter = inferredImageAcquisitionCounter;
  }
  const baseVideoCollection = createInitialCollectionVideoCollection();
  if (data.collectionVideos && typeof data.collectionVideos === 'object') {
    Object.entries(data.collectionVideos).forEach(([videoId, stored]) => {
      const reference = baseVideoCollection[videoId];
      if (!reference) {
        return;
      }
      const rawCount = Number(stored?.count ?? stored);
      const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
        ? 1
        : 0;
      reference.count = normalizedCount;
      if (normalizedCount > 0) {
        const storedOrder = Number(stored?.acquiredOrder);
        if (Number.isFinite(storedOrder) && storedOrder > 0) {
          reference.acquiredOrder = Math.floor(storedOrder);
        }
        const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
        if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
          reference.firstAcquiredAt = storedFirstAcquiredAt;
        }
      }
    });
  }
  mergeStoredCollectionVideoSnapshot(baseVideoCollection);
  gameState.collectionVideos = baseVideoCollection;
  persistCollectionVideoUnlockState(hasOwnedCollectionVideos());
  syncCollectionVideoStateSnapshot({ collection: baseVideoCollection });
  const baseBonusImageCollection = createInitialGachaBonusImageCollection();
  let inferredBonusImageAcquisitionCounter = 0;
  if (data.gachaBonusImages && typeof data.gachaBonusImages === 'object') {
    Object.entries(data.gachaBonusImages).forEach(([imageId, stored]) => {
      const reference = baseBonusImageCollection[imageId];
      if (!reference) {
        return;
      }
      const rawCount = Number(stored?.count ?? stored);
      const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
        ? Math.floor(rawCount)
        : 0;
      reference.count = normalizedCount;
      if (normalizedCount > 0) {
        const storedOrder = Number(stored?.acquiredOrder);
        if (Number.isFinite(storedOrder) && storedOrder > 0) {
          reference.acquiredOrder = Math.floor(storedOrder);
          if (reference.acquiredOrder > inferredBonusImageAcquisitionCounter) {
            inferredBonusImageAcquisitionCounter = reference.acquiredOrder;
          }
        }
        const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
        if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
          reference.firstAcquiredAt = storedFirstAcquiredAt;
        }
      }
    });
  }
  gameState.gachaBonusImages = baseBonusImageCollection;
  const storedBonusCounter = Number(data.gachaBonusImageAcquisitionCounter);
  if (Number.isFinite(storedBonusCounter) && storedBonusCounter > inferredBonusImageAcquisitionCounter) {
    gameState.gachaBonusImageAcquisitionCounter = Math.floor(storedBonusCounter);
  } else {
    gameState.gachaBonusImageAcquisitionCounter = inferredBonusImageAcquisitionCounter;
  }
  const fusionState = createInitialFusionState();
  if (data.fusions && typeof data.fusions === 'object') {
    Object.keys(fusionState).forEach(id => {
      const stored = data.fusions[id];
      if (!stored || typeof stored !== 'object') {
        fusionState[id] = { attempts: 0, successes: 0 };
        return;
      }
      const attemptsRaw = Number(
        stored.attempts
          ?? stored.tries
          ?? stored.tentatives
          ?? stored.total
          ?? 0
      );
      const successesRaw = Number(
        stored.successes
          ?? stored.success
          ?? stored.victories
          ?? stored.wins
          ?? 0
      );
      fusionState[id] = {
        attempts: Number.isFinite(attemptsRaw) && attemptsRaw > 0 ? Math.floor(attemptsRaw) : 0,
        successes: Number.isFinite(successesRaw) && successesRaw > 0 ? Math.floor(successesRaw) : 0
      };
    });
  }
  gameState.fusions = fusionState;
  gameState.fusionBonuses = normalizeStoredFusionBonuses(data.fusionBonuses);
  gameState.theme = getThemeDefinition(data.theme) ? data.theme : DEFAULT_THEME_ID;
  if (typeof data.newsEnabled === 'boolean') {
    applyNewsEnabled(data.newsEnabled, { persist: true, updateControl: true, skipFetch: true });
  }
  const storedBrickSkin = data.arcadeBrickSkin
    ?? data.particulesBrickSkin
    ?? data.brickSkin
    ?? null;
  gameState.arcadeBrickSkin = normalizeBrickSkinSelection(storedBrickSkin);
  if (typeof setParticulesBrickSkinPreference === 'function') {
    setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
  }
  const parseStoredVolume = rawVolume => {
    const numeric = Number(rawVolume);
    if (!Number.isFinite(numeric)) {
      return null;
    }
    if (numeric > 1) {
      return clampMusicVolume(numeric / 100, DEFAULT_MUSIC_VOLUME);
    }
    return clampMusicVolume(numeric, DEFAULT_MUSIC_VOLUME);
  };

  const storedMusic = data.music;
  if (storedMusic && typeof storedMusic === 'object') {
    const selected = storedMusic.selectedTrack
      ?? storedMusic.track
      ?? storedMusic.id
      ?? storedMusic.filename;
    gameState.musicTrackId = typeof selected === 'string' && selected ? selected : null;
    const storedVolume = parseStoredVolume(storedMusic.volume);
    if (storedVolume != null) {
      gameState.musicVolume = storedVolume;
    } else if (typeof data.musicVolume === 'number') {
      const fallbackVolume = parseStoredVolume(data.musicVolume);
      gameState.musicVolume = fallbackVolume != null ? fallbackVolume : DEFAULT_MUSIC_VOLUME;
    } else {
      gameState.musicVolume = DEFAULT_MUSIC_VOLUME;
    }
    if (typeof storedMusic.enabled === 'boolean') {
      gameState.musicEnabled = storedMusic.enabled;
    } else if (typeof data.musicEnabled === 'boolean') {
      gameState.musicEnabled = data.musicEnabled;
    } else if (gameState.musicTrackId) {
      gameState.musicEnabled = true;
    } else {
      gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
    }
  } else {
    if (typeof data.musicTrackId === 'string' && data.musicTrackId) {
      gameState.musicTrackId = data.musicTrackId;
    } else if (typeof data.musicTrack === 'string' && data.musicTrack) {
      gameState.musicTrackId = data.musicTrack;
    } else {
      gameState.musicTrackId = null;
    }
    if (typeof data.musicEnabled === 'boolean') {
      gameState.musicEnabled = data.musicEnabled;
    } else if (gameState.musicTrackId) {
      gameState.musicEnabled = true;
    } else {
      gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
    }
    const fallbackVolume = parseStoredVolume(data.musicVolume);
    gameState.musicVolume = fallbackVolume != null ? fallbackVolume : DEFAULT_MUSIC_VOLUME;
  }
  if (gameState.musicEnabled === false) {
    gameState.musicTrackId = null;
  }
  evaluatePageUnlocks({ save: false, deferUI: true });
  getShopUnlockSet();
  invalidateFeatureUnlockCache({ resetArcadeState: true });
  applyTheme();
  recalcProduction();
  renderShop();
  updateBigBangVisibility();
  updateUI();
  updateFrenzyAutoCollectOptionVisibility();
  updateTicketStarAutoCollectOptionVisibility();
  updateTicketStarSpriteOptionVisibility();
  if (data.lastSave) {
    const diff = Math.max(0, (Date.now() - data.lastSave) / 1000);
    applyOfflineProgress(diff);
  }
}

function loadGame() {
  try {
    resetFrenzyState({ skipApply: true });
    resetTicketStarState({ reschedule: true });
    gameState.baseCrit = createDefaultCritState();
    gameState.crit = createDefaultCritState();
    gameState.lastCritical = null;
    let raw = null;
    const reloadSnapshot = consumeReloadSaveSnapshot();
    try {
      if (typeof localStorage !== 'undefined' && localStorage) {
        raw = localStorage.getItem(PRIMARY_SAVE_STORAGE_KEY);
      }
    } catch (error) {
      console.error('Erreur de lecture de la sauvegarde locale', error);
    }
    if (!raw) {
      const nativeRaw = readNativeSaveData();
      if (nativeRaw) {
        raw = nativeRaw;
        try {
          if (typeof localStorage !== 'undefined' && localStorage) {
            localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, nativeRaw);
          }
        } catch (syncError) {
          console.warn('Unable to sync native save with local storage', syncError);
        }
      }
    }
    if (!raw && reloadSnapshot) {
      raw = reloadSnapshot;
      try {
        if (typeof localStorage !== 'undefined' && localStorage) {
          localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, reloadSnapshot);
        }
      } catch (syncError) {
        console.warn('Unable to persist reload snapshot to local storage', syncError);
      }
    }
    if (!raw) {
      gameState.theme = DEFAULT_THEME_ID;
      gameState.stats = createInitialStats();
      gameState.shopUnlocks = new Set();
      invalidateFeatureUnlockCache({ resetArcadeState: true });
      applyTheme();
      recalcProduction();
      renderShop();
      updateUI();
      return;
    }
    const data = JSON.parse(raw);
    gameState.atoms = LayeredNumber.fromJSON(data.atoms);
    gameState.lifetime = LayeredNumber.fromJSON(data.lifetime);
    gameState.perClick = LayeredNumber.fromJSON(data.perClick);
    gameState.perSecond = LayeredNumber.fromJSON(data.perSecond);
    const tickets = Number(data.gachaTickets);
    const storedTickets = Number.isFinite(tickets) && tickets > 0 ? Math.floor(tickets) : 0;
    setGachaTicketsSilently(storedTickets);
    const bonusTickets = Number(data.bonusParticulesTickets ?? data.bonusTickets);
    gameState.bonusParticulesTickets = Number.isFinite(bonusTickets) && bonusTickets > 0
      ? Math.floor(bonusTickets)
      : 0;
    const storedFeatureFlags = data.featureUnlockFlags ?? data.featureFlags ?? null;
    gameState.featureUnlockFlags = normalizeFeatureUnlockFlags(storedFeatureFlags);
    const storedTicketStarUnlock = data.ticketStarUnlocked ?? data.ticketStarUnlock;
    if (storedTicketStarUnlock != null) {
      gameState.ticketStarUnlocked = storedTicketStarUnlock === true
        || storedTicketStarUnlock === 'true'
        || storedTicketStarUnlock === 1
        || storedTicketStarUnlock === '1';
    } else {
      gameState.ticketStarUnlocked = gameState.gachaTickets > 0;
    }
    gameState.arcadeProgress = normalizeArcadeProgress(data.arcadeProgress);
    syncMotocrossRecordsFromSave();
    applyDerivedFeatureUnlockFlags();
    const storedUpgrades = data.upgrades;
    if (storedUpgrades && typeof storedUpgrades === 'object') {
      const normalizedUpgrades = {};
      Object.entries(storedUpgrades).forEach(([id, value]) => {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
          return;
        }
        const sanitized = Math.max(0, Math.floor(numeric));
        if (sanitized > 0) {
          normalizedUpgrades[id] = sanitized;
        }
      });
      gameState.upgrades = normalizedUpgrades;
    } else {
      gameState.upgrades = {};
    }
    const storedShopUnlocks = data.shopUnlocks;
    if (Array.isArray(storedShopUnlocks)) {
      gameState.shopUnlocks = new Set(storedShopUnlocks);
    } else if (storedShopUnlocks && typeof storedShopUnlocks === 'object') {
      gameState.shopUnlocks = new Set(Object.keys(storedShopUnlocks));
    } else {
      gameState.shopUnlocks = new Set();
    }
    gameState.stats = parseStats(data.stats);
    gameState.trophies = normalizeUnlockedTrophies(data.trophies);
    const storedPageUnlocks = data.pageUnlocks;
    if (storedPageUnlocks && typeof storedPageUnlocks === 'object') {
      const baseUnlocks = createInitialPageUnlockState();
      Object.keys(baseUnlocks).forEach(key => {
        const rawValue = storedPageUnlocks[key];
        baseUnlocks[key] = rawValue === true || rawValue === 'true' || rawValue === 1;
      });
      gameState.pageUnlocks = baseUnlocks;
    } else {
      gameState.pageUnlocks = createInitialPageUnlockState();
    }
    const storedBigBangBonus = Number(
      data.bigBangLevelBonus
      ?? data.bigBangBonus
      ?? data.bigBangLevels
      ?? data.bigBangLevel
      ?? 0
    );
    setBigBangLevelBonus(storedBigBangBonus);
    const storedBigBangPreference =
      data.bigBangButtonVisible ?? data.showBigBangButton ?? data.bigBangVisible ?? null;
    const wantsBigBang =
      storedBigBangPreference === true
      || storedBigBangPreference === 'true'
      || storedBigBangPreference === 1
      || storedBigBangPreference === '1';
    const hasBigBangUnlock = isBigBangUnlocked();
    gameState.bigBangButtonVisible = wantsBigBang && hasBigBangUnlock;
    const storedOffline = Number(data.offlineGainMultiplier);
    if (Number.isFinite(storedOffline) && storedOffline > 0) {
      gameState.offlineGainMultiplier = Math.min(MYTHIQUE_OFFLINE_CAP, storedOffline);
    } else {
      gameState.offlineGainMultiplier = MYTHIQUE_OFFLINE_BASE;
    }
    const storedOfflineTickets = data.offlineTickets;
    if (storedOfflineTickets && typeof storedOfflineTickets === 'object') {
      const secondsPerTicket = Number(storedOfflineTickets.secondsPerTicket);
      const normalizedSecondsPerTicket = Number.isFinite(secondsPerTicket) && secondsPerTicket > 0
        ? secondsPerTicket
        : OFFLINE_TICKET_CONFIG.secondsPerTicket;
      const capSeconds = Number(storedOfflineTickets.capSeconds);
      const normalizedCapSeconds = Number.isFinite(capSeconds) && capSeconds > 0
        ? Math.max(capSeconds, normalizedSecondsPerTicket)
        : Math.max(OFFLINE_TICKET_CONFIG.capSeconds, normalizedSecondsPerTicket);
      const progressSeconds = Number(storedOfflineTickets.progressSeconds);
      const normalizedProgressSeconds = Number.isFinite(progressSeconds) && progressSeconds > 0
        ? Math.max(0, Math.min(progressSeconds, normalizedCapSeconds))
        : 0;
      gameState.offlineTickets = {
        secondsPerTicket: normalizedSecondsPerTicket,
        capSeconds: normalizedCapSeconds,
        progressSeconds: normalizedProgressSeconds
      };
    } else if (typeof storedOfflineTickets === 'number' && storedOfflineTickets > 0) {
      const interval = Number(storedOfflineTickets);
      const normalizedSecondsPerTicket = Number.isFinite(interval) && interval > 0
        ? interval
        : OFFLINE_TICKET_CONFIG.secondsPerTicket;
      const normalizedCapSeconds = Math.max(
        OFFLINE_TICKET_CONFIG.capSeconds,
        normalizedSecondsPerTicket
      );
      gameState.offlineTickets = {
        secondsPerTicket: normalizedSecondsPerTicket,
        capSeconds: normalizedCapSeconds,
        progressSeconds: 0
      };
    } else {
      gameState.offlineTickets = {
        secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
        capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
        progressSeconds: 0
      };
    }
    gameState.sudokuOfflineBonus = normalizeSudokuOfflineBonusState(data.sudokuOfflineBonus);
    const storedInterval = Number(data.ticketStarIntervalSeconds);
    if (Number.isFinite(storedInterval) && storedInterval > 0) {
      gameState.ticketStarAverageIntervalSeconds = storedInterval;
    } else {
      gameState.ticketStarAverageIntervalSeconds = DEFAULT_TICKET_STAR_INTERVAL_SECONDS;
    }
    const storedAutoCollect = data.ticketStarAutoCollect;
    if (storedAutoCollect && typeof storedAutoCollect === 'object') {
      const rawDelay = storedAutoCollect.delaySeconds
        ?? storedAutoCollect.delay
        ?? storedAutoCollect.seconds
        ?? storedAutoCollect.value;
      const delaySeconds = Number(rawDelay);
      gameState.ticketStarAutoCollect = Number.isFinite(delaySeconds) && delaySeconds >= 0
        ? { delaySeconds }
        : null;
    } else if (storedAutoCollect === true) {
      gameState.ticketStarAutoCollect = { delaySeconds: 0 };
    } else if (typeof storedAutoCollect === 'number' && Number.isFinite(storedAutoCollect) && storedAutoCollect >= 0) {
      gameState.ticketStarAutoCollect = { delaySeconds: storedAutoCollect };
    } else {
      gameState.ticketStarAutoCollect = null;
    }
    const storedAutoCollectEnabledRaw =
      data.ticketStarAutoCollectEnabled
      ?? data.ticketStarAutoCollectEnable
      ?? data.ticketStarAutoCollectOn;
    const storedAutoCollectEnabled = storedAutoCollectEnabledRaw === true
      || storedAutoCollectEnabledRaw === 'true'
      || storedAutoCollectEnabledRaw === 1
      || storedAutoCollectEnabledRaw === '1';
    applyTicketStarAutoCollectEnabled(
      storedAutoCollectEnabled || storedAutoCollectEnabledRaw === false
        ? storedAutoCollectEnabled
        : ticketStarAutoCollectPreference,
      {
        persist: storedAutoCollectEnabledRaw != null,
        updateControl: true
      }
    );
    updateTicketStarAutoCollectOptionVisibility();
    const storedTicketStarSprite = data.ticketStarSpriteId ?? data.ticketStarSprite;
    applyTicketStarSpritePreference(
      storedTicketStarSprite != null ? storedTicketStarSprite : getTicketStarSpritePreference(),
      {
        persist: storedTicketStarSprite != null,
        updateControl: true,
        refresh: true
      }
    );
    updateTicketStarSpriteOptionVisibility();
    gameState.ticketStarSpecialChance = clampTicketStarSpecialChance(
      data.ticketStarSpecialChance ?? data.ticketStarSpecialRate
    );
    gameState.ticketStarSpecialReward = normalizeTicketStarSpecialReward(
      data.ticketStarSpecialReward ?? data.ticketStarSpecialTickets
    );
    const storedFrenzyBonus = data.frenzySpawnBonus;
    if (storedFrenzyBonus && typeof storedFrenzyBonus === 'object') {
      const perClick = Number(storedFrenzyBonus.perClick);
      const perSecond = Number(storedFrenzyBonus.perSecond);
      const addPerClick = Number(storedFrenzyBonus.addPerClick ?? storedFrenzyBonus.perClickAdd);
      const addPerSecond = Number(storedFrenzyBonus.addPerSecond ?? storedFrenzyBonus.perSecondAdd);
      gameState.frenzySpawnBonus = {
        perClick: Number.isFinite(perClick) && perClick > 0 ? perClick : 1,
        perSecond: Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1,
        addPerClick: Number.isFinite(addPerClick) && addPerClick > 0 ? addPerClick : 0,
        addPerSecond: Number.isFinite(addPerSecond) && addPerSecond > 0 ? addPerSecond : 0
      };
    } else {
      gameState.frenzySpawnBonus = { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 };
    }
    applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
    gameState.apsCrit = normalizeApsCritState(data.apsCrit);
    const intervalChanged = setTicketStarAverageIntervalSeconds(gameState.ticketStarAverageIntervalSeconds);
    if (intervalChanged) {
      resetTicketStarState({ reschedule: true });
    }
    const baseCollection = createInitialElementCollection();
    if (data.elements && typeof data.elements === 'object') {
      Object.entries(data.elements).forEach(([id, saved]) => {
        if (!baseCollection[id]) return;
        const reference = baseCollection[id];
        const savedCount = Number(saved?.count);
        const normalizedCount = Number.isFinite(savedCount) && savedCount > 0
          ? Math.floor(savedCount)
          : 0;
        const savedLifetime = Number(saved?.lifetime);
        let normalizedLifetime = Number.isFinite(savedLifetime) && savedLifetime > 0
          ? Math.floor(savedLifetime)
          : 0;
        if (normalizedLifetime === 0 && (saved?.owned || normalizedCount > 0)) {
          normalizedLifetime = Math.max(normalizedCount, 1);
        }
        if (normalizedLifetime < normalizedCount) {
          normalizedLifetime = normalizedCount;
        }
        baseCollection[id] = {
          id,
          gachaId: reference.gachaId,
          owned: normalizedLifetime > 0,
          count: normalizedCount,
          lifetime: normalizedLifetime,
          rarity: reference.rarity ?? (typeof saved?.rarity === 'string' ? saved.rarity : null),
          effects: Array.isArray(reference?.effects) ? [...reference.effects] : [],
          bonuses: Array.isArray(reference?.bonuses) ? [...reference.bonuses] : []
        };
      });
    }
    gameState.elements = baseCollection;
    const baseCardCollection = createInitialGachaCardCollection();
    if (data.gachaCards && typeof data.gachaCards === 'object') {
      Object.entries(data.gachaCards).forEach(([cardId, stored]) => {
        if (!baseCardCollection[cardId]) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        baseCardCollection[cardId].count = Number.isFinite(rawCount) && rawCount > 0
          ? Math.floor(rawCount)
          : 0;
      });
    }
    gameState.gachaCards = baseCardCollection;
    const baseImageCollection = createInitialGachaImageCollection();
    let inferredImageAcquisitionCounter = 0;
    if (data.gachaImages && typeof data.gachaImages === 'object') {
      Object.entries(data.gachaImages).forEach(([imageId, stored]) => {
        const reference = baseImageCollection[imageId];
        if (!reference) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
          ? Math.floor(rawCount)
          : 0;
        reference.count = normalizedCount;
        if (normalizedCount > 0) {
          const storedOrder = Number(stored?.acquiredOrder);
          if (Number.isFinite(storedOrder) && storedOrder > 0) {
            reference.acquiredOrder = Math.floor(storedOrder);
            if (reference.acquiredOrder > inferredImageAcquisitionCounter) {
              inferredImageAcquisitionCounter = reference.acquiredOrder;
            }
          }
          const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
          if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
            reference.firstAcquiredAt = storedFirstAcquiredAt;
          }
        }
      });
    }
    gameState.gachaImages = baseImageCollection;
    const storedImageCounter = Number(data.gachaImageAcquisitionCounter);
    if (Number.isFinite(storedImageCounter) && storedImageCounter > inferredImageAcquisitionCounter) {
      gameState.gachaImageAcquisitionCounter = Math.floor(storedImageCounter);
    } else {
      gameState.gachaImageAcquisitionCounter = inferredImageAcquisitionCounter;
    }
    const baseVideoCollection = createInitialCollectionVideoCollection();
    if (data.collectionVideos && typeof data.collectionVideos === 'object') {
      Object.entries(data.collectionVideos).forEach(([videoId, stored]) => {
        const reference = baseVideoCollection[videoId];
        if (!reference) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
          ? 1
          : 0;
        reference.count = normalizedCount;
        if (normalizedCount > 0) {
          const storedOrder = Number(stored?.acquiredOrder);
          if (Number.isFinite(storedOrder) && storedOrder > 0) {
            reference.acquiredOrder = Math.floor(storedOrder);
          }
          const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
          if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
            reference.firstAcquiredAt = storedFirstAcquiredAt;
          }
        }
      });
    }
    mergeStoredCollectionVideoSnapshot(baseVideoCollection);
    gameState.collectionVideos = baseVideoCollection;
    persistCollectionVideoUnlockState(hasOwnedCollectionVideos());
    syncCollectionVideoStateSnapshot({ collection: baseVideoCollection });
    const baseBonusImageCollection = createInitialGachaBonusImageCollection();
    let inferredBonusImageAcquisitionCounter = 0;
    if (data.gachaBonusImages && typeof data.gachaBonusImages === 'object') {
      Object.entries(data.gachaBonusImages).forEach(([imageId, stored]) => {
        const reference = baseBonusImageCollection[imageId];
        if (!reference) {
          return;
        }
        const rawCount = Number(stored?.count ?? stored);
        const normalizedCount = Number.isFinite(rawCount) && rawCount > 0
          ? Math.floor(rawCount)
          : 0;
        reference.count = normalizedCount;
        if (normalizedCount > 0) {
          const storedOrder = Number(stored?.acquiredOrder);
          if (Number.isFinite(storedOrder) && storedOrder > 0) {
            reference.acquiredOrder = Math.floor(storedOrder);
            if (reference.acquiredOrder > inferredBonusImageAcquisitionCounter) {
              inferredBonusImageAcquisitionCounter = reference.acquiredOrder;
            }
          }
          const storedFirstAcquiredAt = Number(stored?.firstAcquiredAt);
          if (Number.isFinite(storedFirstAcquiredAt) && storedFirstAcquiredAt > 0) {
            reference.firstAcquiredAt = storedFirstAcquiredAt;
          }
        }
      });
    }
    gameState.gachaBonusImages = baseBonusImageCollection;
    const storedBonusCounter = Number(data.gachaBonusImageAcquisitionCounter);
    if (Number.isFinite(storedBonusCounter) && storedBonusCounter > inferredBonusImageAcquisitionCounter) {
      gameState.gachaBonusImageAcquisitionCounter = Math.floor(storedBonusCounter);
    } else {
      gameState.gachaBonusImageAcquisitionCounter = inferredBonusImageAcquisitionCounter;
    }
    const fusionState = createInitialFusionState();
    if (data.fusions && typeof data.fusions === 'object') {
      Object.keys(fusionState).forEach(id => {
        const stored = data.fusions[id];
        if (!stored || typeof stored !== 'object') {
          fusionState[id] = { attempts: 0, successes: 0 };
          return;
        }
        const attemptsRaw = Number(
          stored.attempts
            ?? stored.tries
            ?? stored.tentatives
            ?? stored.total
            ?? 0
        );
        const successesRaw = Number(
          stored.successes
            ?? stored.success
            ?? stored.victories
            ?? stored.wins
            ?? 0
        );
        fusionState[id] = {
          attempts: Number.isFinite(attemptsRaw) && attemptsRaw > 0 ? Math.floor(attemptsRaw) : 0,
          successes: Number.isFinite(successesRaw) && successesRaw > 0 ? Math.floor(successesRaw) : 0
        };
      });
    }
    gameState.fusions = fusionState;
    gameState.fusionBonuses = normalizeStoredFusionBonuses(data.fusionBonuses);
    gameState.theme = getThemeDefinition(data.theme) ? data.theme : DEFAULT_THEME_ID;
    const storedBrickSkin = data.arcadeBrickSkin
      ?? data.particulesBrickSkin
      ?? data.brickSkin
      ?? null;
    gameState.arcadeBrickSkin = normalizeBrickSkinSelection(storedBrickSkin);
    if (typeof setParticulesBrickSkinPreference === 'function') {
      setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
    }
    const parseStoredVolume = raw => {
      const numeric = Number(raw);
      if (!Number.isFinite(numeric)) {
        return null;
      }
      if (numeric > 1) {
        return clampMusicVolume(numeric / 100, DEFAULT_MUSIC_VOLUME);
      }
      return clampMusicVolume(numeric, DEFAULT_MUSIC_VOLUME);
    };

    const storedMusic = data.music;
    if (storedMusic && typeof storedMusic === 'object') {
      const selected = storedMusic.selectedTrack
        ?? storedMusic.track
        ?? storedMusic.id
        ?? storedMusic.filename;
      gameState.musicTrackId = typeof selected === 'string' && selected ? selected : null;
      const storedVolume = parseStoredVolume(storedMusic.volume);
      if (storedVolume != null) {
        gameState.musicVolume = storedVolume;
      } else if (typeof data.musicVolume === 'number') {
        const fallbackVolume = parseStoredVolume(data.musicVolume);
        gameState.musicVolume = fallbackVolume != null ? fallbackVolume : DEFAULT_MUSIC_VOLUME;
      } else {
        gameState.musicVolume = DEFAULT_MUSIC_VOLUME;
      }
      if (typeof storedMusic.enabled === 'boolean') {
        gameState.musicEnabled = storedMusic.enabled;
      } else if (typeof data.musicEnabled === 'boolean') {
        gameState.musicEnabled = data.musicEnabled;
      } else if (gameState.musicTrackId) {
        gameState.musicEnabled = true;
      } else {
        gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
      }
    } else {
      if (typeof data.musicTrackId === 'string' && data.musicTrackId) {
        gameState.musicTrackId = data.musicTrackId;
      } else if (typeof data.musicTrack === 'string' && data.musicTrack) {
        gameState.musicTrackId = data.musicTrack;
      } else {
        gameState.musicTrackId = null;
      }
      if (typeof data.musicEnabled === 'boolean') {
        gameState.musicEnabled = data.musicEnabled;
      } else if (gameState.musicTrackId) {
        gameState.musicEnabled = true;
      } else {
        gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
      }
      const fallbackVolume = parseStoredVolume(data.musicVolume);
      gameState.musicVolume = fallbackVolume != null ? fallbackVolume : DEFAULT_MUSIC_VOLUME;
    }
    if (gameState.musicEnabled === false) {
      gameState.musicTrackId = null;
    }
    evaluatePageUnlocks({ save: false, deferUI: true });
    getShopUnlockSet();
    invalidateFeatureUnlockCache({ resetArcadeState: true });
    applyTheme();
    recalcProduction();
    renderShop();
    updateBigBangVisibility();
    updateUI();
    updateFrenzyAutoCollectOptionVisibility();
    if (data.lastSave) {
      const diff = Math.max(0, (Date.now() - data.lastSave) / 1000);
      applyOfflineProgress(diff);
    }
  } catch (err) {
    console.error('Erreur de chargement', err);
    if (attemptRestoreFromBackup()) {
      return;
    }
    resetGame();
  }
}

function attemptRestoreFromBackup() {
  try {
    const entries = saveBackupManager.list();
    if (!Array.isArray(entries) || entries.length === 0) {
      return false;
    }
    for (let index = 0; index < entries.length; index += 1) {
      const entry = entries[index];
      if (!entry || typeof entry.id !== 'string' || !entry.id) {
        continue;
      }
      const serialized = saveBackupManager.load(entry.id);
      if (typeof serialized !== 'string' || !serialized) {
        continue;
      }
      try {
        applySerializedGameState(serialized);
        if (typeof localStorage !== 'undefined' && localStorage) {
          try {
            localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, serialized);
          } catch (storageError) {
            console.warn('Unable to persist restored backup to local storage', storageError);
          }
        }
        writeNativeSaveData(serialized);
        lastSerializedSave = serialized;
        storeReloadSaveSnapshot(serialized);
        const message = translateOrDefault(
          'scripts.app.saves.fallbackRestored',
          'Sauvegarde de secours restaurée.'
        );
        showToast(message);
        return true;
      } catch (error) {
        console.error('Unable to apply backup save', error);
      }
    }
    const failureMessage = translateOrDefault(
      'scripts.app.saves.fallbackFailed',
      'Aucune sauvegarde de secours valide n’a pu être chargée.'
    );
    showToast(failureMessage);
  } catch (error) {
    console.error('Unable to restore from backup', error);
  }
  return false;
}
