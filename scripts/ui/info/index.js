const NO_OP = () => {};

function resolveUpdater(override) {
  if (typeof override === 'function') {
    return override;
  }
  if (typeof globalThis !== 'undefined' && typeof globalThis.updateInfoPanels === 'function') {
    return globalThis.updateInfoPanels.bind(globalThis);
  }
  return NO_OP;
}

export function initializeInfoUI(options) {
  if (!options || typeof options !== 'object') {
    throw new TypeError('initializeInfoUI requires an options object.');
  }

  const {
    elements,
    getPageUnlockState,
    getUnlockedTrophySet,
    achievementsTrophyId,
    updateInfoPanels: overrideUpdate
  } = options;

  if (!elements || typeof elements !== 'object') {
    throw new TypeError('initializeInfoUI requires a valid elements object.');
  }
  if (typeof getPageUnlockState !== 'function') {
    throw new TypeError('initializeInfoUI requires getPageUnlockState to be a function.');
  }
  if (typeof getUnlockedTrophySet !== 'function') {
    throw new TypeError('initializeInfoUI requires getUnlockedTrophySet to be a function.');
  }
  if (typeof achievementsTrophyId !== 'string' || !achievementsTrophyId) {
    throw new TypeError('initializeInfoUI requires achievementsTrophyId to be a non-empty string.');
  }

  const infoElements = {
    shopBonusCard: elements.infoShopBonusCard || null,
    elementBonusCard: elements.infoElementBonusCard || null,
    achievementsCard: elements.infoAchievementsCard || null,
    goalsEmpty: elements.goalsEmpty || null
  };

  const updatePanelsInternal = resolveUpdater(overrideUpdate);

  function areInfoBonusesUnlocked() {
    const unlocks = getPageUnlockState();
    return unlocks?.info === true;
  }

  function areAchievementsFeatureUnlocked() {
    const trophies = getUnlockedTrophySet();
    if (trophies instanceof Set) {
      return trophies.has(achievementsTrophyId);
    }
    if (trophies && typeof trophies.has === 'function') {
      try {
        return trophies.has(achievementsTrophyId);
      } catch (error) {
        return false;
      }
    }
    return false;
  }

  function updateBonusVisibility() {
    const visible = areInfoBonusesUnlocked();
    const ariaHidden = visible ? 'false' : 'true';

    if (infoElements.shopBonusCard) {
      infoElements.shopBonusCard.hidden = !visible;
      infoElements.shopBonusCard.setAttribute('aria-hidden', ariaHidden);
    }

    if (infoElements.elementBonusCard) {
      infoElements.elementBonusCard.hidden = !visible;
      infoElements.elementBonusCard.setAttribute('aria-hidden', ariaHidden);
    }
  }

  function updateAchievementsVisibility() {
    if (!infoElements.achievementsCard) {
      return;
    }

    const unlocked = areAchievementsFeatureUnlocked();
    const ariaHidden = unlocked ? 'false' : 'true';

    infoElements.achievementsCard.hidden = !unlocked;
    infoElements.achievementsCard.setAttribute('aria-hidden', ariaHidden);

    if (!unlocked && infoElements.goalsEmpty) {
      infoElements.goalsEmpty.hidden = true;
      infoElements.goalsEmpty.setAttribute('aria-hidden', 'true');
    }
  }

  function updatePanels() {
    updatePanelsInternal();
  }

  updateBonusVisibility();
  updateAchievementsVisibility();

  return {
    areInfoBonusesUnlocked,
    areAchievementsFeatureUnlocked,
    updateBonusVisibility,
    updateAchievementsVisibility,
    updatePanels
  };
}
