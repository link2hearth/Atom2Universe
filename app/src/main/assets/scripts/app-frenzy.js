const APC_FRENZY_COUNTER_GRACE_MS = 3000;

const frenzyState = {
  perClick: {
    token: null,
    tokenExpire: 0,
    effectUntil: 0,
    currentMultiplier: 1,
    effects: [],
    currentStacks: 0,
    currentClickCount: 0,
    frenziesUsedInChain: 0,
    isActive: false,
    visibleUntil: 0,
    lastDisplayedCount: 0,
    autoCollectTimeout: null,
    autoCollectDeadline: 0
  },
  perSecond: {
    token: null,
    tokenExpire: 0,
    effectUntil: 0,
    currentMultiplier: 1,
    effects: [],
    currentStacks: 0,
    isActive: false,
    autoCollectTimeout: null,
    autoCollectDeadline: 0
  },
  spawnAccumulator: 0
};

function cancelFrenzyAutoCollect(type) {
  if (!FRENZY_TYPES.includes(type)) return;
  const entry = frenzyState[type];
  if (!entry) return;
  if (entry.autoCollectTimeout != null) {
    const clearFn = typeof globalThis !== 'undefined' && typeof globalThis.clearTimeout === 'function'
      ? globalThis.clearTimeout
      : typeof clearTimeout === 'function'
        ? clearTimeout
        : null;
    if (clearFn) {
      clearFn(entry.autoCollectTimeout);
    }
  }
  entry.autoCollectTimeout = null;
  entry.autoCollectDeadline = 0;
}

function scheduleFrenzyAutoCollect(type, now = (() => {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
})()) {
  if (!FRENZY_TYPES.includes(type)) return;
  if (!isFrenzyAutoCollectActive()) {
    cancelFrenzyAutoCollect(type);
    return;
  }
  const entry = frenzyState[type];
  if (!entry || !entry.token) {
    cancelFrenzyAutoCollect(type);
    return;
  }
  cancelFrenzyAutoCollect(type);
  const delay = Math.max(0, Math.floor(Number(FRENZY_AUTO_COLLECT_DELAY_MS) || 0));
  if (delay <= 0) {
    collectFrenzy(type, now);
    return;
  }
  const setFn = typeof globalThis !== 'undefined' && typeof globalThis.setTimeout === 'function'
    ? globalThis.setTimeout
    : typeof setTimeout === 'function'
      ? setTimeout
      : null;
  if (!setFn) {
    return;
  }
  entry.autoCollectDeadline = now + delay;
  entry.autoCollectTimeout = setFn(() => {
    entry.autoCollectTimeout = null;
    entry.autoCollectDeadline = 0;
    if (!isFrenzyAutoCollectActive()) {
      return;
    }
    if (!entry.token) {
      return;
    }
    collectFrenzy(type);
  }, delay);
}

function getClickerContexts() {
  const contexts = [
    {
      id: 'game',
      pageId: 'game',
      button: elements.atomButton,
      anchor: elements.atomButton,
      frenzyLayer: elements.frenzyLayer,
      ticketLayer: elements.ticketLayer,
      counter: {
        container: elements.apcFrenzyCounter,
        value: elements.apcFrenzyCounterValue,
        bestSingle: elements.apcFrenzyCounterBestSingle,
        bestMulti: elements.apcFrenzyCounterBestMulti
      }
    },
    {
      id: 'wave',
      pageId: 'wave',
      button: null,
      anchor: elements.waveStage || elements.waveCanvas,
      frenzyLayer: elements.waveFrenzyLayer,
      ticketLayer: elements.waveTicketLayer,
      counter: {
        container: elements.waveApcFrenzyCounter,
        value: elements.waveApcFrenzyCounterValue,
        bestSingle: elements.waveApcFrenzyCounterBestSingle,
        bestMulti: elements.waveApcFrenzyCounterBestMulti
      }
    }
  ];

  return contexts.filter(context => {
    const anchorConnected = context.anchor && context.anchor.isConnected;
    const layerConnected = context.frenzyLayer && context.frenzyLayer.isConnected;
    const counterConnected = context.counter?.container && context.counter.container.isConnected;
    const ticketConnected = context.ticketLayer && context.ticketLayer.isConnected;
    return anchorConnected || layerConnected || counterConnected || ticketConnected;
  });
}

function getClickerContextById(id) {
  if (!id) {
    return null;
  }
  const normalized = String(id).trim().toLowerCase();
  if (!normalized) {
    return null;
  }
  return getClickerContexts().find(context => context.id === normalized || context.pageId === normalized) || null;
}

function getActiveClickerContext() {
  const contexts = getClickerContexts();
  const activePage = document.body?.dataset?.activePage;
  if (activePage) {
    const active = contexts.find(context => context.pageId === activePage && context.anchor?.isConnected);
    if (active) {
      return active;
    }
  }
  const fallback = contexts.find(context => context.pageId === 'game' && context.anchor?.isConnected);
  if (fallback) {
    return fallback;
  }
  return contexts.find(context => context.anchor?.isConnected) || null;
}

function getActiveFrenzyContext() {
  const contexts = getClickerContexts();
  const activePage = document.body?.dataset?.activePage;
  if (activePage) {
    const active = contexts.find(context => context.pageId === activePage
      && context.frenzyLayer?.isConnected
      && context.anchor?.isConnected);
    if (active) {
      return active;
    }
  }
  const fallback = contexts.find(context => context.pageId === 'game'
    && context.frenzyLayer?.isConnected
    && context.anchor?.isConnected);
  if (fallback) {
    return fallback;
  }
  return contexts.find(context => context.frenzyLayer?.isConnected && context.anchor?.isConnected) || null;
}

function getCounterContexts() {
  return getClickerContexts().filter(context => context.counter?.container);
}

function getActiveTicketLayerElement() {
  const contexts = getClickerContexts();
  const activePage = document.body?.dataset?.activePage;
  if (activePage) {
    const active = contexts.find(context => context.pageId === activePage && context.ticketLayer?.isConnected);
    if (active) {
      return active.ticketLayer;
    }
  }
  const fallback = contexts.find(context => context.pageId === 'game' && context.ticketLayer?.isConnected);
  if (fallback) {
    return fallback.ticketLayer;
  }
  const any = contexts.find(context => context.ticketLayer?.isConnected);
  return any ? any.ticketLayer : null;
}

if (typeof globalThis !== 'undefined') {
  globalThis.getActiveTicketLayerElement = getActiveTicketLayerElement;
}

function resolveManualClickContext(contextId = null) {
  const explicit = getClickerContextById(contextId);
  if (explicit && explicit.anchor?.isConnected) {
    return explicit;
  }
  const active = getActiveClickerContext();
  if (active) {
    return active;
  }
  return getClickerContextById('game');
}

function isManualClickContextActive() {
  const activePage = document.body?.dataset?.activePage;
  return activePage === 'game' || activePage === 'wave';
}

function getFrenzyMultiplier(type, now = performance.now()) {
  if (!FRENZY_TYPES.includes(type)) {
    return 1;
  }
  const entry = frenzyState[type];
  if (!entry) {
    return 1;
  }
  if (!Array.isArray(entry.effects) || entry.effects.length === 0) {
    return entry.effectUntil > now ? FRENZY_CONFIG.multiplier : 1;
  }
  const activeStacks = entry.effects.filter(expire => expire > now).length;
  if (activeStacks <= 0) {
    return 1;
  }
  return Math.pow(FRENZY_CONFIG.multiplier, activeStacks);
}

function getFrenzyStackCount(type, now = performance.now()) {
  if (!FRENZY_TYPES.includes(type)) return 0;
  const entry = frenzyState[type];
  if (!entry || !Array.isArray(entry.effects)) return entry && entry.effectUntil > now ? 1 : 0;
  return entry.effects.filter(expire => expire > now).length;
}

function isApcFrenzyActive(now = performance.now()) {
  return getFrenzyStackCount('perClick', now) > 0;
}

const apcFrenzyValuePulseTimeoutIds = new Map();

function pulseApcFrenzyValue(targetContext = null) {
  const contexts = targetContext ? [targetContext] : getCounterContexts();
  contexts.forEach(context => {
    const valueElement = context?.counter?.value;
    if (!valueElement) {
      return;
    }
    valueElement.classList.remove('apc-frenzy-counter__value--pulse');
    void valueElement.offsetWidth;
    valueElement.classList.add('apc-frenzy-counter__value--pulse');
    const key = context?.id || context?.pageId || valueElement.id;
    if (apcFrenzyValuePulseTimeoutIds.has(key)) {
      clearTimeout(apcFrenzyValuePulseTimeoutIds.get(key));
    }
    const timeoutId = setTimeout(() => {
      valueElement.classList.remove('apc-frenzy-counter__value--pulse');
      apcFrenzyValuePulseTimeoutIds.delete(key);
    }, 260);
    apcFrenzyValuePulseTimeoutIds.set(key, timeoutId);
  });
}

function formatApcFrenzySingleRecordText(bestClicks) {
  const clicks = Math.max(0, Math.floor(bestClicks || 0));
  if (clicks <= 0) {
    return translateOrDefault(
      'index.sections.game.apcFrenzyCounter.bestEmpty',
      'Record : —'
    );
  }
  const clicksText = formatIntegerLocalized(clicks);
  return translateOrDefault(
    'index.sections.game.apcFrenzyCounter.bestSingleRecord',
    `Record (1 frénésie) : ${clicksText} clics`,
    { count: clicksText }
  );
}

function formatApcFrenzyMultiRecordText(bestClicks, frenziesUsed) {
  const clicks = Math.max(0, Math.floor(bestClicks || 0));
  if (clicks <= 0) {
    return translateOrDefault(
      'index.sections.game.apcFrenzyCounter.bestEmpty',
      'Record : —'
    );
  }
  const frenzies = Math.max(1, Math.floor(frenziesUsed || 0));
  const clicksText = formatIntegerLocalized(clicks);
  if (frenzies <= 1) {
    return translateOrDefault(
      'index.sections.game.apcFrenzyCounter.bestMultiRecordSingle',
      `Record (multi frénésie) : ${clicksText} clics (1 frénésie)`,
      { count: clicksText }
    );
  }
  const frenzyCountText = formatIntegerLocalized(frenzies);
  return translateOrDefault(
    'index.sections.game.apcFrenzyCounter.bestMultiRecord',
    `Record (multi frénésie) : ${clicksText} clics (${frenzyCountText} frén.)`,
    { count: clicksText, frenzies: frenzyCountText }
  );
}

function updateApcFrenzyCounterDisplay(now = performance.now()) {
  const entry = frenzyState.perClick;
  const active = isApcFrenzyActive(now);
  let currentCount = entry ? Math.max(0, Math.floor(entry.currentClickCount || 0)) : 0;
  let bestSingleClicks = 0;
  let bestChainClicks = 0;
  let bestChainFrenzies = 0;
  if (gameState.stats) {
    const sessionStats = ensureApcFrenzyStats(gameState.stats.session);
    const globalStats = ensureApcFrenzyStats(gameState.stats.global);
    bestSingleClicks = Math.max(0, Math.floor(globalStats.bestSingle?.clicks || 0));
    bestChainClicks = Math.max(0, Math.floor(globalStats.best?.clicks || 0));
    bestChainFrenzies = Math.max(0, Math.floor(globalStats.best?.frenziesUsed || 0));
    // Ensure session stats reference stays normalized for future updates.
    gameState.stats.session.apcFrenzy = sessionStats;
    gameState.stats.global.apcFrenzy = globalStats;
  }
  const contexts = getCounterContexts();
  contexts.forEach(context => {
    const { container, value, bestSingle, bestMulti } = context?.counter || {};
    if (bestSingle) {
      bestSingle.textContent = formatApcFrenzySingleRecordText(bestSingleClicks);
    }
    if (bestMulti) {
      bestMulti.textContent = formatApcFrenzyMultiRecordText(bestChainClicks, bestChainFrenzies);
    }
    if (!container) {
      return;
    }
    const contextActive = document.body?.dataset?.activePage === context.pageId;
    let displayCount = currentCount;
    let shouldDisplay = active;
    if (entry) {
      if (active) {
        entry.visibleUntil = Math.max(entry.visibleUntil || 0, now + APC_FRENZY_COUNTER_GRACE_MS);
        entry.lastDisplayedCount = currentCount;
      } else if (Number.isFinite(entry.visibleUntil) && entry.visibleUntil > now) {
        shouldDisplay = true;
        displayCount = Math.max(0, Math.floor(entry.lastDisplayedCount || 0));
      } else {
        entry.visibleUntil = 0;
        entry.lastDisplayedCount = Math.max(0, Math.floor(entry.lastDisplayedCount || 0));
      }
    }
    const isVisible = shouldDisplay && contextActive;
    container.hidden = !isVisible;
    container.style.display = isVisible ? '' : 'none';
    container.setAttribute('aria-hidden', String(!isVisible));
    container.classList.toggle('is-active', active && contextActive);
    container.classList.toggle('is-idle', !(active && contextActive));
    if (value) {
      value.textContent = formatIntegerLocalized(isVisible ? displayCount : currentCount);
    }
  });
}

function registerApcFrenzyClick(now = performance.now(), context = null) {
  const entry = frenzyState.perClick;
  if (!entry || !isApcFrenzyActive(now)) {
    return;
  }
  entry.currentClickCount = Math.max(0, Math.floor(entry.currentClickCount || 0)) + 1;
  entry.lastDisplayedCount = entry.currentClickCount;
  pulseApcFrenzyValue(context);
  updateApcFrenzyCounterDisplay(now);
}

function handleApcFrenzyActivated(wasActive, now = performance.now()) {
  const entry = frenzyState.perClick;
  if (!entry) {
    return;
  }
  if (!wasActive) {
    entry.currentClickCount = 0;
    entry.frenziesUsedInChain = 0;
    entry.lastDisplayedCount = 0;
  }
  entry.frenziesUsedInChain = Math.max(0, Math.floor(entry.frenziesUsedInChain || 0)) + 1;
  entry.isActive = true;
  updateApcFrenzyCounterDisplay(now);
}

function applyApcFrenzyRunToStats(runClicks, frenziesUsed) {
  if (!gameState.stats) {
    return;
  }
  const sanitizedClicks = Math.max(0, Math.floor(runClicks || 0));
  if (sanitizedClicks <= 0) {
    return;
  }
  const sanitizedFrenzies = Math.max(1, Math.floor(frenziesUsed || 0));
  const applyToStore = store => {
    if (!store || typeof store !== 'object') {
      return;
    }
    const statsEntry = ensureApcFrenzyStats(store);
    statsEntry.totalClicks = Math.max(0, Math.floor(statsEntry.totalClicks || 0)) + sanitizedClicks;
    const currentBestClicks = Math.max(0, Math.floor(statsEntry.best?.clicks || 0));
    const currentBestFrenzies = Math.max(0, Math.floor(statsEntry.best?.frenziesUsed || 0)) || 0;
    const currentBestSingle = Math.max(0, Math.floor(statsEntry.bestSingle?.clicks || 0));
    if (
      sanitizedClicks > currentBestClicks
      || (
        sanitizedClicks === currentBestClicks
        && sanitizedFrenzies < Math.max(1, currentBestFrenzies || Infinity)
      )
    ) {
      statsEntry.best = { clicks: sanitizedClicks, frenziesUsed: sanitizedFrenzies };
    }
    if (sanitizedFrenzies <= 1 && sanitizedClicks > currentBestSingle) {
      statsEntry.bestSingle = { clicks: sanitizedClicks };
    }
  };
  applyToStore(gameState.stats.session);
  applyToStore(gameState.stats.global);
}

function finalizeApcFrenzyRun(now = performance.now()) {
  const entry = frenzyState.perClick;
  if (!entry) {
    return;
  }
  const runClicks = Math.max(0, Math.floor(entry.currentClickCount || 0));
  const frenziesUsed = Math.max(0, Math.floor(entry.frenziesUsedInChain || 0));
  if (runClicks > 0) {
    applyApcFrenzyRunToStats(runClicks, frenziesUsed);
    saveGame();
  }
  entry.lastDisplayedCount = runClicks;
  entry.visibleUntil = Math.max(entry.visibleUntil || 0, now + APC_FRENZY_COUNTER_GRACE_MS);
  entry.currentClickCount = 0;
  entry.frenziesUsedInChain = 0;
  entry.isActive = false;
  updateApcFrenzyCounterDisplay(now);
}

function pruneFrenzyEffects(entry, now = performance.now()) {
  if (!entry) return false;
  if (!Array.isArray(entry.effects)) {
    entry.effects = [];
  }
  const before = entry.effects.length;
  entry.effects = entry.effects.filter(expire => expire > now);
  entry.effectUntil = entry.effects.length ? Math.max(...entry.effects) : 0;
  entry.currentStacks = entry.effects.length;
  return before !== entry.effects.length;
}

function applyFrenzyEffects(now = performance.now()) {
  if (typeof flushPendingAutoGain === 'function') {
    flushPendingAutoGain(now, { force: true });
  }
  const basePerClick = gameState.basePerClick instanceof LayeredNumber
    ? gameState.basePerClick.clone()
    : BASE_PER_CLICK.clone();
  const basePerSecond = gameState.basePerSecond instanceof LayeredNumber
    ? gameState.basePerSecond.clone()
    : BASE_PER_SECOND.clone();

  const clickMultiplier = getFrenzyMultiplier('perClick', now);
  const autoMultiplier = getFrenzyMultiplier('perSecond', now);

  let perClickResult = basePerClick.multiplyNumber(clickMultiplier);
  let perSecondResult = basePerSecond.multiplyNumber(autoMultiplier);

  perClickResult = normalizeProductionUnit(perClickResult);
  perSecondResult = normalizeProductionUnit(perSecondResult);

  gameState.perClick = perClickResult.clone();
  gameState.perSecond = perSecondResult.clone();

  const baseProduction = gameState.productionBase || createEmptyProductionBreakdown();
  const clickEntry = cloneProductionEntry(baseProduction.perClick);
  const autoEntry = cloneProductionEntry(baseProduction.perSecond);

  const clickMultiplierLayered = toMultiplierLayered(clickMultiplier);
  const autoMultiplierLayered = toMultiplierLayered(autoMultiplier);

  if (clickEntry) {
    clickEntry.sources.multipliers.frenzy = clickMultiplierLayered.clone();
    if (!isLayeredOne(clickMultiplierLayered)) {
      clickEntry.multipliers.push({
        id: 'frenzy',
        label: 'Frénésie',
        value: clickMultiplierLayered.clone(),
        source: 'frenzy'
      });
    }
    clickEntry.totalMultiplier = clickEntry.totalMultiplier.multiply(clickMultiplierLayered);
    clickEntry.total = perClickResult.clone();
  }

  if (autoEntry) {
    autoEntry.sources.multipliers.frenzy = autoMultiplierLayered.clone();
    if (!isLayeredOne(autoMultiplierLayered)) {
      autoEntry.multipliers.push({
        id: 'frenzy',
        label: 'Frénésie',
        value: autoMultiplierLayered.clone(),
        source: 'frenzy'
      });
    }
    autoEntry.totalMultiplier = autoEntry.totalMultiplier.multiply(autoMultiplierLayered);
    autoEntry.total = perSecondResult.clone();
  }

  gameState.production = {
    perClick: clickEntry,
    perSecond: autoEntry
  };

  gameState.crit = cloneCritState(gameState.baseCrit);

  frenzyState.perClick.currentMultiplier = clickMultiplier;
  frenzyState.perSecond.currentMultiplier = autoMultiplier;
  frenzyState.perClick.currentStacks = getFrenzyStackCount('perClick', now);
  frenzyState.perSecond.currentStacks = getFrenzyStackCount('perSecond', now);
}

function clearFrenzyToken(type, immediate = false) {
  if (!FRENZY_TYPES.includes(type)) return;
  const entry = frenzyState[type];
  if (!entry || !entry.token) return;
  cancelFrenzyAutoCollect(type);
  const token = entry.token;
  entry.token = null;
  entry.tokenExpire = 0;
  token.disabled = true;
  token.style.pointerEvents = 'none';
  token.classList.add('is-expiring');
  const remove = () => {
    if (token && token.isConnected) {
      token.remove();
    }
  };
  if (immediate) {
    remove();
  } else {
    setTimeout(remove, 180);
  }
}

function positionFrenzyToken(context, type, token) {
  if (!context?.frenzyLayer || !context.anchor) return false;
  const containerRect = context.frenzyLayer.getBoundingClientRect();
  const anchorRect = context.anchor.getBoundingClientRect();
  if (!containerRect.width || !containerRect.height || !anchorRect.width || !anchorRect.height) {
    return false;
  }

  const centerX = anchorRect.left + anchorRect.width / 2;
  const centerY = anchorRect.top + anchorRect.height / 2;
  const baseSize = Math.max(anchorRect.width, anchorRect.height);
  const minRadius = baseSize * 0.45;
  const maxRadius = baseSize * 1.25;
  const radiusRange = Math.max(maxRadius - minRadius, minRadius);
  const radius = minRadius + Math.random() * radiusRange;
  const angle = Math.random() * Math.PI * 2;

  let targetX = centerX + Math.cos(angle) * radius;
  let targetY = centerY + Math.sin(angle) * radius;

  const margin = Math.max(40, baseSize * 0.25);
  targetX = Math.min(containerRect.right - margin, Math.max(containerRect.left + margin, targetX));
  targetY = Math.min(containerRect.bottom - margin, Math.max(containerRect.top + margin, targetY));

  token.style.left = `${targetX - containerRect.left}px`;
  token.style.top = `${targetY - containerRect.top}px`;
  return true;
}

function pickFrenzyAsset(info) {
  if (!info) {
    return '';
  }
  const assets = Array.isArray(info.assets) ? info.assets.filter(asset => typeof asset === 'string' && asset.trim()) : [];
  if (assets.length) {
    const randomIndex = Math.floor(Math.random() * assets.length);
    return assets[randomIndex];
  }
  if (typeof info.asset === 'string' && info.asset.trim()) {
    return info.asset;
  }
  return '';
}

function spawnFrenzyToken(type, now = performance.now()) {
  const info = FRENZY_TYPE_INFO[type];
  if (!info) return;
  const context = getActiveFrenzyContext();
  if (!context?.frenzyLayer || !context.anchor) return;
  if (FRENZY_CONFIG.displayDurationMs <= 0) return;

  const token = document.createElement('button');
  token.type = 'button';
  token.className = `frenzy-token frenzy-token--${type}`;
  token.dataset.frenzyType = type;
  const multiplierText = `×${FRENZY_CONFIG.multiplier}`;
  token.setAttribute('aria-label', `Activer la ${info.label} (${multiplierText})`);
  token.title = `Activer la ${info.label} (${multiplierText})`;

  const img = document.createElement('img');
  const assetSource = pickFrenzyAsset(info);
  if (assetSource) {
    img.src = assetSource;
  }
  img.alt = '';
  img.setAttribute('aria-hidden', 'true');
  token.appendChild(img);

  token.addEventListener('click', event => {
    event.preventDefault();
    event.stopPropagation();
    collectFrenzy(type);
  });

  if (!positionFrenzyToken(context, type, token)) {
    return;
  }

  context.frenzyLayer.appendChild(token);
  frenzyState[type].token = token;
  frenzyState[type].tokenExpire = now + FRENZY_CONFIG.displayDurationMs;
  scheduleFrenzyAutoCollect(type, now);
}

function attemptFrenzySpawn(type, now = performance.now()) {
  if (!FRENZY_TYPES.includes(type)) return;
  if (!isManualClickContextActive()) return;
  if (typeof document !== 'undefined' && document.hidden) return;
  const context = getActiveFrenzyContext();
  if (!context?.frenzyLayer || !context.anchor) return;
  const entry = frenzyState[type];
  if (entry.token) return;
  const chance = getEffectiveFrenzySpawnChance(type);
  if (chance <= 0) return;
  if (Math.random() >= chance) return;
  spawnFrenzyToken(type, now);
}

function getFusionFrenzyDurationSeconds(type) {
  if (!FUSION_DEFS.length) {
    return 0;
  }
  const bonuses = getFusionBonusState();
  if (!bonuses) {
    return 0;
  }
  if (type === 'perClick') {
    const seconds = Number(bonuses.apcFrenzyDurationSeconds);
    return Number.isFinite(seconds) ? Math.max(0, seconds) : 0;
  }
  if (type === 'perSecond') {
    const seconds = Number(bonuses.apsFrenzyDurationSeconds);
    return Number.isFinite(seconds) ? Math.max(0, seconds) : 0;
  }
  return 0;
}

function getFrenzyEffectDurationMs(type) {
  const base = FRENZY_CONFIG.effectDurationMs;
  const bonusSeconds = getFusionFrenzyDurationSeconds(type);
  const bonusMs = Number.isFinite(bonusSeconds) ? Math.max(0, bonusSeconds) * 1000 : 0;
  return Math.max(0, base + bonusMs);
}

function collectFrenzy(type, now = performance.now()) {
  const info = FRENZY_TYPE_INFO[type];
  if (!info) return;
  if (!FRENZY_TYPES.includes(type)) return;
  const entry = frenzyState[type];
  if (!entry) return;

  clearFrenzyToken(type);
  pruneFrenzyEffects(entry, now);
  if (!Array.isArray(entry.effects)) {
    entry.effects = [];
  }
  const wasActive = entry.effects.length > 0;
  const duration = getFrenzyEffectDurationMs(type);
  const expireAt = now + duration;
  const maxStacks = getTrophyFrenzyCap();
  if (entry.effects.length >= maxStacks && entry.effects.length > 0) {
    entry.effects.sort((a, b) => a - b);
    entry.effects[0] = expireAt;
  } else {
    entry.effects.push(expireAt);
  }
  entry.effectUntil = entry.effects.length ? Math.max(...entry.effects) : expireAt;
  entry.currentStacks = getFrenzyStackCount(type, now);

  applyFrenzyEffects(now);

  registerFrenzyTrigger(type);
  evaluateTrophies();
  updateUI();

  if (type === 'perClick') {
    handleApcFrenzyActivated(wasActive, now);
  }

  const rawSeconds = duration / 1000;
  let durationText;
  if (rawSeconds >= 1) {
    if (Number.isInteger(rawSeconds)) {
      durationText = `${rawSeconds.toFixed(0)}s`;
    } else {
      const precision = rawSeconds < 10 ? 1 : 0;
      durationText = `${rawSeconds.toFixed(precision)}s`;
    }
  } else {
    durationText = `${rawSeconds.toFixed(1)}s`;
  }
  showToast(t('scripts.app.frenzyToast', {
    label: info.label,
    multiplier: FRENZY_CONFIG.multiplier,
    duration: durationText
  }));
}

function updateFrenzies(delta, now = performance.now()) {
  if (!Number.isFinite(delta) || delta < 0) {
    delta = 0;
  }

  frenzyState.spawnAccumulator += delta;
  const attempts = Math.floor(frenzyState.spawnAccumulator);
  if (attempts > 0) {
    for (let i = 0; i < attempts; i += 1) {
      FRENZY_TYPES.forEach(type => attemptFrenzySpawn(type, now));
    }
    frenzyState.spawnAccumulator -= attempts;
  }

  let needsUpdate = false;
  FRENZY_TYPES.forEach(type => {
    const entry = frenzyState[type];
    if (!entry) return;
    const wasActive = entry.isActive === true || getFrenzyStackCount(type, now) > 0;
    if (entry.token && now >= entry.tokenExpire) {
      clearFrenzyToken(type);
    }
    const removed = pruneFrenzyEffects(entry, now);
    const isActive = getFrenzyStackCount(type, now) > 0;
    entry.isActive = isActive;
    if (type === 'perClick' && wasActive && !isActive) {
      finalizeApcFrenzyRun(now);
    }
    if (removed || wasActive !== isActive) {
      needsUpdate = true;
    }
  });

  if (needsUpdate) {
    applyFrenzyEffects(now);
    updateUI();
  }
}

function resetFrenzyState(options = {}) {
  const { skipApply = false } = options;
  FRENZY_TYPES.forEach(type => {
    clearFrenzyToken(type, true);
    const entry = frenzyState[type];
    if (!entry) return;
    entry.effectUntil = 0;
    entry.currentMultiplier = 1;
    entry.effects = [];
    entry.currentStacks = 0;
    entry.currentClickCount = 0;
    entry.frenziesUsedInChain = 0;
    entry.isActive = false;
    entry.visibleUntil = 0;
    entry.lastDisplayedCount = 0;
  });
  frenzyState.spawnAccumulator = 0;
  if (!skipApply) {
    applyFrenzyEffects();
    updateUI();
  }
}
