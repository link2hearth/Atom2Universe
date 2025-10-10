const SHOP_PURCHASE_AMOUNTS = Object.freeze([1, 10, 100]);

function createShopBuildingTextsGetter({ translateOrDefault }) {
  return function getShopBuildingTexts(def) {
    if (!def || typeof def !== 'object') {
      return { name: '', description: '' };
    }
    const id = typeof def.id === 'string' ? def.id.trim() : '';
    const baseKey = id ? `config.shop.buildings.${id}` : '';
    const fallbackName = typeof def.name === 'string' ? def.name : id;
    let name = fallbackName;
    if (baseKey) {
      const translatedName = translateOrDefault(`${baseKey}.name`, fallbackName);
      if (translatedName) {
        name = translatedName;
      }
    }
    const fallbackDescription = typeof def.effectSummary === 'string' && def.effectSummary.trim()
      ? def.effectSummary.trim()
      : (typeof def.description === 'string' ? def.description.trim() : '');
    let description = '';
    if (baseKey) {
      description =
        translateOrDefault(`${baseKey}.effectSummary`, '')
        || translateOrDefault(`${baseKey}.effect`, '')
        || translateOrDefault(`${baseKey}.description`, '');
    }
    if (!description) {
      description = fallbackDescription;
    }
    return { name, description };
  };
}

export function createShopController(options) {
  if (!options || typeof options !== 'object') {
    throw new TypeError('createShopController requires an options object.');
  }

  const {
    elements,
    upgradeDefs,
    upgradeIndexMap,
    gameState,
    translateOrDefault,
    translate,
    formatIntegerLocalized,
    formatShopCost,
    getUpgradeLevel,
    resolveUpgradeMaxLevel,
    getRemainingUpgradeCapacity,
    computeUpgradeCost,
    getShopUnlockSet,
    isDevKitShopFree,
    recalcProduction,
    updateUI,
    showToast,
    toLayeredValue
  } = options;

  if (!elements || typeof elements !== 'object') {
    throw new TypeError('createShopController requires a valid elements object.');
  }
  if (!Array.isArray(upgradeDefs)) {
    throw new TypeError('createShopController requires upgradeDefs to be an array.');
  }
  if (!(upgradeIndexMap instanceof Map)) {
    throw new TypeError('createShopController requires upgradeIndexMap to be a Map.');
  }
  if (!gameState || typeof gameState !== 'object') {
    throw new TypeError('createShopController requires a gameState reference.');
  }
  if (typeof translateOrDefault !== 'function') {
    throw new TypeError('createShopController requires translateOrDefault to be a function.');
  }
  if (typeof translate !== 'function') {
    throw new TypeError('createShopController requires translate to be a function.');
  }
  if (typeof formatIntegerLocalized !== 'function') {
    throw new TypeError('createShopController requires a number formatter.');
  }
  if (typeof formatShopCost !== 'function') {
    throw new TypeError('createShopController requires formatShopCost to be a function.');
  }
  if (typeof getUpgradeLevel !== 'function' || typeof resolveUpgradeMaxLevel !== 'function') {
    throw new TypeError('createShopController requires upgrade helpers.');
  }
  if (typeof getRemainingUpgradeCapacity !== 'function' || typeof computeUpgradeCost !== 'function') {
    throw new TypeError('createShopController requires upgrade calculators.');
  }
  if (typeof getShopUnlockSet !== 'function') {
    throw new TypeError('createShopController requires getShopUnlockSet.');
  }
  if (typeof isDevKitShopFree !== 'function') {
    throw new TypeError('createShopController requires isDevKitShopFree.');
  }
  if (typeof recalcProduction !== 'function' || typeof updateUI !== 'function') {
    throw new TypeError('createShopController requires production update callbacks.');
  }
  if (typeof showToast !== 'function') {
    throw new TypeError('createShopController requires showToast.');
  }
  if (typeof toLayeredValue !== 'function') {
    throw new TypeError('createShopController requires toLayeredValue.');
  }

  const getShopBuildingTexts = createShopBuildingTextsGetter({ translateOrDefault });
  const shopRows = new Map();
  let lastVisibleShopIndex = -1;

  function getShopActionLabel(level) {
    const isUpgrade = Number.isFinite(level) && Number(level) > 0;
    const key = isUpgrade ? 'scripts.app.shop.actionUpgrade' : 'scripts.app.shop.actionBuy';
    const fallback = isUpgrade ? 'Améliorer' : 'Acheter';
    return translateOrDefault(key, fallback);
  }

  function formatShopLevelLabel(level, maxLevel, capReached) {
    const resolvedLevel = Number.isFinite(level) ? Math.max(0, Math.floor(level)) : 0;
    const params = { level: formatIntegerLocalized(resolvedLevel) };
    let label;
    if (Number.isFinite(maxLevel)) {
      params.max = formatIntegerLocalized(Math.max(0, Math.floor(maxLevel)));
      label = translateOrDefault(
        'scripts.app.shop.levelLabelWithMax',
        `Niveau ${params.level} / ${params.max}`,
        params
      );
      if (capReached) {
        const suffix = translateOrDefault('scripts.app.shop.levelMaxSuffix', ' (max)');
        label += suffix;
      }
    } else {
      label = translateOrDefault('scripts.app.shop.levelLabel', `Niveau ${params.level}`, params);
    }
    return label;
  }

  function getShopLimitSuffix(limited) {
    if (!limited) {
      return '';
    }
    return translateOrDefault('scripts.app.shop.limitSuffix', ' (limité aux niveaux restants)');
  }

  function formatShopPriceText({ isFree, limitedQuantity, quantity, priceText }) {
    if (isFree) {
      return translateOrDefault('scripts.app.shop.free', 'Gratuit');
    }
    if (limitedQuantity) {
      return translateOrDefault(
        'scripts.app.shop.priceLimited',
        `Limité à x${quantity} — ${priceText}`,
        { quantity: formatIntegerLocalized(quantity), price: priceText }
      );
    }
    return priceText;
  }

  function formatShopAriaLabel({ state, action, name, quantity, limitNote, costValue }) {
    const params = {
      action: action || '',
      name: name || '',
      quantity: formatIntegerLocalized(Number.isFinite(quantity) ? quantity : Number(quantity) || 0),
      limitNote: limitNote || ''
    };
    const fallbackQuantity = `×${params.quantity}${params.limitNote}`;
    if (state === 'free') {
      return translateOrDefault(
        'scripts.app.shop.ariaActionFree',
        `${params.action} ${params.name} ${fallbackQuantity} (gratuit)`,
        params
      );
    }
    params.cost = costValue || '';
    if (state === 'cost') {
      return translateOrDefault(
        'scripts.app.shop.ariaActionCost',
        `${params.action} ${params.name} ${fallbackQuantity} (coût ${params.cost} atomes)`,
        params
      );
    }
    return translateOrDefault(
      'scripts.app.shop.ariaActionInsufficient',
      `${params.action} ${params.name} ${fallbackQuantity} (atomes insuffisants)`,
      params
    );
  }

  function renderShopPurchaseHeader() {
    if (!elements.shopActionsHeader) return;
    const header = elements.shopActionsHeader;
    header.innerHTML = '';
    if (!Array.isArray(SHOP_PURCHASE_AMOUNTS) || SHOP_PURCHASE_AMOUNTS.length === 0) {
      header.hidden = true;
      return;
    }
    header.hidden = false;
    const fragment = document.createDocumentFragment();
    const spacer = document.createElement('div');
    spacer.className = 'shop-actions-header__spacer';
    fragment.appendChild(spacer);
    const labels = document.createElement('div');
    labels.className = 'shop-actions-header__labels';
    SHOP_PURCHASE_AMOUNTS.forEach(quantity => {
      const label = document.createElement('span');
      label.className = 'shop-actions-header__label';
      label.textContent = `x${quantity}`;
      labels.appendChild(label);
    });
    fragment.appendChild(labels);
    header.appendChild(fragment);
  }

  function getLocalizedUpgradeName(def) {
    return getShopBuildingTexts(def).name;
  }

  function buildShopItem(def) {
    const item = document.createElement('article');
    item.className = 'shop-item';
    item.dataset.upgradeId = def.id;
    item.setAttribute('role', 'listitem');

    const header = document.createElement('header');
    header.className = 'shop-item__header';

    const title = document.createElement('h3');
    const texts = getShopBuildingTexts(def);
    title.textContent = texts.name;

    const level = document.createElement('span');
    level.className = 'shop-item__level';

    header.append(title, level);

    const desc = document.createElement('p');
    desc.className = 'shop-item__description';
    desc.textContent = texts.description;

    const actions = document.createElement('div');
    actions.className = 'shop-item__actions';
    const buttonMap = new Map();

    SHOP_PURCHASE_AMOUNTS.forEach(quantity => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'shop-item__action';

      const priceLabel = document.createElement('span');
      priceLabel.className = 'shop-item__action-price';
      priceLabel.textContent = '—';

      button.append(priceLabel);
      button.addEventListener('click', () => {
        attemptPurchase(def, quantity);
      });

      actions.appendChild(button);
      buttonMap.set(quantity, {
        button,
        price: priceLabel,
        baseQuantity: quantity
      });
    });

    item.append(header, desc, actions);

    return { root: item, title, level, description: desc, buttons: buttonMap };
  }

  function updateShopUnlockHint() {
    const button = elements.navShopButton;
    if (!button) {
      return;
    }

    let shouldVibrate = false;
    if (!button.hidden && !button.disabled && lastVisibleShopIndex >= 0 && lastVisibleShopIndex < upgradeDefs.length) {
      const def = upgradeDefs[lastVisibleShopIndex];
      if (def) {
        const level = getUpgradeLevel(gameState.upgrades, def.id);
        if (!Number.isFinite(level) || level <= 0) {
          const remainingLevels = getRemainingUpgradeCapacity(def);
          if (!Number.isFinite(remainingLevels) || remainingLevels > 0) {
            const cost = computeUpgradeCost(def, 1);
            const atoms = toLayeredValue(gameState.atoms, 0);
            shouldVibrate = atoms.compare(cost) >= 0;
          }
        }
      }
    }

    button.classList.toggle('nav-button--vibrate', shouldVibrate);
  }

  function updateShopVisibility() {
    if (!shopRows.size) {
      lastVisibleShopIndex = -1;
      updateShopUnlockHint();
      return;
    }
    const unlocks = getShopUnlockSet();

    let visibleLimit = -1;
    unlocks.forEach(id => {
      const unlockIndex = upgradeIndexMap.get(id);
      if (unlockIndex != null && unlockIndex > visibleLimit) {
        visibleLimit = unlockIndex;
      }
    });
    if (visibleLimit < 0 && upgradeDefs.length > 0) {
      visibleLimit = 0;
    }
    if (visibleLimit >= upgradeDefs.length) {
      visibleLimit = upgradeDefs.length - 1;
    }

    upgradeDefs.forEach((def, index) => {
      const row = shopRows.get(def.id);
      if (!row) return;

      const shouldReveal = index <= visibleLimit;
      row.root.hidden = !shouldReveal;
      row.root.classList.toggle('shop-item--locked', !shouldReveal);
      if (shouldReveal) {
        unlocks.add(def.id);
      }
    });

    lastVisibleShopIndex = visibleLimit;
    updateShopUnlockHint();
  }

  function updateShopAffordability() {
    if (!shopRows.size) return;
    upgradeDefs.forEach(def => {
      const row = shopRows.get(def.id);
      if (!row) return;
      const texts = getShopBuildingTexts(def);
      if (row.title) {
        row.title.textContent = texts.name;
      }
      if (row.description) {
        row.description.textContent = texts.description;
      }
      const level = getUpgradeLevel(gameState.upgrades, def.id);
      const maxLevel = resolveUpgradeMaxLevel(def);
      const remainingLevels = getRemainingUpgradeCapacity(def);
      const hasFiniteCap = Number.isFinite(maxLevel);
      const capReached = Number.isFinite(remainingLevels) && remainingLevels <= 0;
      row.level.textContent = formatShopLevelLabel(level, maxLevel, capReached);
      let anyAffordable = false;
      const actionLabel = getShopActionLabel(level);
      const displayName = texts.name;
      const shopFree = isDevKitShopFree();

      SHOP_PURCHASE_AMOUNTS.forEach(quantity => {
        const entry = row.buttons.get(quantity);
        if (!entry) return;
        const baseQuantity = entry.baseQuantity ?? quantity;

        if (capReached) {
        entry.price.textContent = translate('scripts.app.shop.limitReached');
          entry.button.disabled = true;
          entry.button.classList.remove('is-ready');
          const ariaLabel = translateOrDefault(
            'scripts.app.shop.ariaMaxLevel',
            `${displayName} a atteint son niveau maximum`,
            { name: displayName }
          );
          entry.button.setAttribute('aria-label', ariaLabel);
          entry.button.title = ariaLabel;
          return;
        }

        const effectiveQuantity = Number.isFinite(remainingLevels)
          ? Math.min(baseQuantity, remainingLevels)
          : baseQuantity;
        const limited = Number.isFinite(remainingLevels) && effectiveQuantity !== baseQuantity;

        const cost = computeUpgradeCost(def, effectiveQuantity);
        const affordable = shopFree || gameState.atoms.compare(cost) >= 0;
        const costDisplay = formatShopCost(cost);
        entry.price.textContent = formatShopPriceText({
          isFree: shopFree,
          limitedQuantity: limited,
          quantity: limited ? effectiveQuantity : baseQuantity,
          priceText: costDisplay
        });
        const enabled = affordable && effectiveQuantity > 0;
        entry.button.disabled = !enabled;
        entry.button.classList.toggle('is-ready', enabled);
        if (enabled) {
          anyAffordable = true;
        }
        const displayQuantity = limited ? effectiveQuantity : baseQuantity;
        const limitNote = getShopLimitSuffix(limited);
        const ariaLabel = formatShopAriaLabel({
          state: enabled ? (shopFree ? 'free' : 'cost') : 'insufficient',
          action: actionLabel,
          name: displayName,
          quantity: displayQuantity,
          limitNote,
          costValue: cost.toString()
        });
        entry.button.setAttribute('aria-label', ariaLabel);
        entry.button.title = ariaLabel;
      });

      row.root.classList.toggle('shop-item--ready', anyAffordable);
    });
    updateShopVisibility();
  }

  function renderShop() {
    if (!elements.shopList) return;
    renderShopPurchaseHeader();
    shopRows.clear();
    elements.shopList.innerHTML = '';
    const fragment = document.createDocumentFragment();
    upgradeDefs.forEach(def => {
      const row = buildShopItem(def);
      fragment.appendChild(row.root);
      shopRows.set(def.id, row);
    });
    elements.shopList.appendChild(fragment);
    updateShopAffordability();
  }

  function attemptPurchase(def, quantity = 1) {
    const buyAmount = Math.max(1, Math.floor(Number(quantity) || 0));
    const remainingLevels = getRemainingUpgradeCapacity(def);
    const cappedOut = Number.isFinite(remainingLevels) && remainingLevels <= 0;
    if (cappedOut) {
      showToast(translate('scripts.app.shop.maxLevel'));
      return;
    }
    const finalAmount = Number.isFinite(remainingLevels)
      ? Math.min(buyAmount, remainingLevels)
      : buyAmount;
    if (!Number.isFinite(finalAmount) || finalAmount <= 0) {
      showToast(translate('scripts.app.shop.maxLevel'));
      return;
    }
    const cost = computeUpgradeCost(def, finalAmount);
    const shopFree = isDevKitShopFree();
    if (!shopFree && gameState.atoms.compare(cost) < 0) {
      showToast(translate('scripts.app.shop.notEnoughAtoms'));
      return;
    }
    if (!shopFree) {
      gameState.atoms = gameState.atoms.subtract(cost);
    }
    const currentLevel = Number(gameState.upgrades[def.id]);
    const normalizedLevel = Number.isFinite(currentLevel) && currentLevel > 0
      ? Math.floor(currentLevel)
      : 0;
    gameState.upgrades[def.id] = normalizedLevel + finalAmount;
    recalcProduction();
    updateUI();
    const limitSuffix = finalAmount < buyAmount ? getShopLimitSuffix(true) : '';
    const displayName = getLocalizedUpgradeName(def);
    showToast(shopFree
      ? translate('scripts.app.shop.devkitFreePurchase', {
        name: displayName,
        quantity: finalAmount,
        suffix: limitSuffix
      })
      : translate('scripts.app.shop.purchase', {
        name: displayName,
        quantity: finalAmount,
        suffix: limitSuffix
      }));
  }

  return {
    render: renderShop,
    updateAffordability: updateShopAffordability,
    updateUnlockHint: updateShopUnlockHint,
    attemptPurchase
  };
}

export function initializeShopUI(options) {
  return createShopController(options);
}

export { SHOP_PURCHASE_AMOUNTS };
