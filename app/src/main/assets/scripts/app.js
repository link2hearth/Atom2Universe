function setActiveShopPurchaseAmount(quantity) {
  if (!Array.isArray(SHOP_PURCHASE_AMOUNTS) || SHOP_PURCHASE_AMOUNTS.length === 0) {
    activeShopPurchaseAmount = 1;
    updateShopPurchaseControlState();
    updateShopAffordability();
    return;
  }
  activeShopPurchaseAmount = normalized;
  updateShopPurchaseControlState();
  updateShopAffordability();
  if (typeof updateGachaRollModeFromScale === 'function') {
    updateGachaRollModeFromScale();
  } else if (typeof updateGachaUI === 'function') {
    updateGachaUI();
  }
}
