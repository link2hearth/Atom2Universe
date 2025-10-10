const ARCADE_GAME_IDS = Object.freeze([
  'particules',
  'metaux',
  'wave',
  'quantum2048',
  'math',
  'balance',
  'sudoku',
  'minesweeper',
  'solitaire',
  'blackjack',
  'echecs'
]);

function createInitialArcadeProgress() {
  const entries = {};
  ARCADE_GAME_IDS.forEach(id => {
    entries[id] = null;
  });
  return {
    version: 1,
    entries
  };
}

function createInitialApcFrenzyStats() {
  return {
    totalClicks: 0,
    best: {
      clicks: 0,
      frenziesUsed: 0
    },
    bestSingle: {
      clicks: 0
    }
  };
}

function normalizeApcFrenzyStats(raw) {
  const base = createInitialApcFrenzyStats();
  if (!raw || typeof raw !== 'object') {
    return base;
  }
  const total = Number(raw.totalClicks ?? raw.total ?? 0);
  base.totalClicks = Number.isFinite(total) ? Math.max(0, Math.floor(total)) : 0;
  const bestRaw = raw.best && typeof raw.best === 'object' ? raw.best : {};
  const bestClicks = Number(bestRaw.clicks ?? bestRaw.count ?? 0);
  const bestFrenzies = Number(bestRaw.frenziesUsed ?? bestRaw.frenzies ?? 0);
  base.best.clicks = Number.isFinite(bestClicks) ? Math.max(0, Math.floor(bestClicks)) : 0;
  base.best.frenziesUsed = Number.isFinite(bestFrenzies) ? Math.max(0, Math.floor(bestFrenzies)) : 0;
  const bestSingleRaw = raw.bestSingle && typeof raw.bestSingle === 'object' ? raw.bestSingle : {};
  const bestSingleClicks = Number(bestSingleRaw.clicks ?? bestSingleRaw.count ?? 0);
  base.bestSingle.clicks = Number.isFinite(bestSingleClicks)
    ? Math.max(0, Math.floor(bestSingleClicks))
    : 0;
  if ((!raw.bestSingle || typeof raw.bestSingle !== 'object') && base.best.frenziesUsed <= 1) {
    base.bestSingle.clicks = Math.max(base.bestSingle.clicks, base.best.clicks);
  }
  return base;
}

function ensureApcFrenzyStats(store) {
  if (!store || typeof store !== 'object') {
    return createInitialApcFrenzyStats();
  }
  if (!store.apcFrenzy || typeof store.apcFrenzy !== 'object') {
    store.apcFrenzy = createInitialApcFrenzyStats();
    return store.apcFrenzy;
  }
  store.apcFrenzy = normalizeApcFrenzyStats(store.apcFrenzy);
  return store.apcFrenzy;
}

function createInitialStats() {
  const now = Date.now();
  return {
    session: {
      atomsGained: LayeredNumber.zero(),
      apcAtoms: LayeredNumber.zero(),
      apsAtoms: LayeredNumber.zero(),
      offlineAtoms: LayeredNumber.zero(),
      manualClicks: 0,
      onlineTimeMs: 0,
      startedAt: now,
      frenzyTriggers: {
        perClick: 0,
        perSecond: 0,
        total: 0
      },
      apcFrenzy: createInitialApcFrenzyStats()
    },
    global: {
      apcAtoms: LayeredNumber.zero(),
      apsAtoms: LayeredNumber.zero(),
      offlineAtoms: LayeredNumber.zero(),
      manualClicks: 0,
      playTimeMs: 0,
      startedAt: null,
      frenzyTriggers: {
        perClick: 0,
        perSecond: 0,
        total: 0
      },
      apcFrenzy: createInitialApcFrenzyStats()
    }
  };
}

function getLayeredStat(store, key) {
  if (!store || typeof store !== 'object') {
    return LayeredNumber.zero();
  }
  const current = store[key];
  if (current instanceof LayeredNumber) {
    return current;
  }
  if (current && typeof current === 'object') {
    try {
      const normalized = LayeredNumber.fromJSON(current);
      store[key] = normalized;
      return normalized;
    } catch (error) {
      // Ignore malformed values and fall through to zero.
    }
  }
  if (current != null) {
    const numeric = Number(current);
    if (Number.isFinite(numeric) && numeric !== 0) {
      const normalized = new LayeredNumber(numeric);
      store[key] = normalized;
      return normalized;
    }
  }
  const zero = LayeredNumber.zero();
  store[key] = zero;
  return zero;
}

function incrementLayeredStat(store, key, amount) {
  if (!store || typeof store !== 'object') {
    return;
  }
  const current = getLayeredStat(store, key);
  store[key] = current.add(amount);
}

function normalizeFrenzyStats(raw) {
  if (!raw || typeof raw !== 'object') {
    return { perClick: 0, perSecond: 0, total: 0 };
  }
  const perClick = Number(raw.perClick ?? raw.click ?? 0);
  const perSecond = Number(raw.perSecond ?? raw.auto ?? raw.aps ?? 0);
  const totalRaw = raw.total != null ? Number(raw.total) : perClick + perSecond;
  return {
    perClick: Number.isFinite(perClick) && perClick > 0 ? Math.floor(perClick) : 0,
    perSecond: Number.isFinite(perSecond) && perSecond > 0 ? Math.floor(perSecond) : 0,
    total: Number.isFinite(totalRaw) && totalRaw > 0 ? Math.floor(totalRaw) : 0
  };
}

function createDefaultApsCritState() {
  return { effects: [] };
}

function normalizeApsCritEffect(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const multiplierAdd = Number(raw.multiplierAdd ?? raw.add ?? raw.multiplier ?? raw.value ?? 0);
  const remainingSeconds = Number(
    raw.remainingSeconds ?? raw.seconds ?? raw.time ?? raw.duration ?? raw.chrono ?? 0
  );
  if (!Number.isFinite(multiplierAdd) || !Number.isFinite(remainingSeconds)) {
    return null;
  }
  if (multiplierAdd <= 0 || remainingSeconds <= 0) {
    return null;
  }
  return {
    multiplierAdd: Math.max(0, multiplierAdd),
    remainingSeconds: Math.max(0, remainingSeconds)
  };
}

function normalizeApsCritState(raw) {
  const state = createDefaultApsCritState();
  if (!raw || typeof raw !== 'object') {
    return state;
  }
  if (Array.isArray(raw.effects)) {
    state.effects = raw.effects
      .map(entry => normalizeApsCritEffect(entry))
      .filter(effect => effect != null);
    if (state.effects.length) {
      return state;
    }
  }
  const chronoValue = Number(
    raw.chronoSeconds ?? raw.chrono ?? raw.time ?? raw.seconds ?? raw.chronoSecs ?? 0
  );
  const multiplierValue = Number(raw.multiplier ?? raw.multi ?? raw.factor ?? 0);
  if (Number.isFinite(chronoValue) && chronoValue > 0 && Number.isFinite(multiplierValue) && multiplierValue > 1) {
    state.effects = [{
      multiplierAdd: Math.max(0, multiplierValue - 1),
      remainingSeconds: Math.max(0, chronoValue)
    }];
  }
  return state;
}

function createEmptyProductionEntry() {
  const rarityMultipliers = new Map();
  RARITY_IDS.forEach(id => {
    rarityMultipliers.set(id, 1);
  });
  return {
    base: LayeredNumber.zero(),
    totalAddition: LayeredNumber.zero(),
    totalMultiplier: LayeredNumber.one(),
    additions: [],
    multipliers: [],
    total: LayeredNumber.zero(),
    sources: {
      flats: {
        baseFlat: LayeredNumber.zero(),
        shopFlat: LayeredNumber.zero(),
        elementFlat: LayeredNumber.zero(),
        fusionFlat: LayeredNumber.zero(),
        devkitFlat: LayeredNumber.zero()
      },
      multipliers: {
        trophyMultiplier: LayeredNumber.one(),
        frenzy: LayeredNumber.one(),
        apsCrit: LayeredNumber.one(),
        collectionMultiplier: LayeredNumber.one(),
        rarityMultipliers
      }
    }
  };
}

function createEmptyProductionBreakdown() {
  return {
    perClick: createEmptyProductionEntry(),
    perSecond: createEmptyProductionEntry()
  };
}

const DEVKIT_STATE = {
  isOpen: false,
  lastFocusedElement: null,
  cheats: {
    freeShop: false,
    freeGacha: false
  },
  bonuses: {
    autoFlat: LayeredNumber.zero()
  }
};

function isDevKitShopFree() {
  return DEVKIT_STATE.cheats.freeShop === true;
}

function isDevKitGachaFree() {
  return DEVKIT_STATE.cheats.freeGacha === true;
}

function getDevKitAutoFlatBonus() {
  return DEVKIT_STATE.bonuses.autoFlat instanceof LayeredNumber
    ? DEVKIT_STATE.bonuses.autoFlat
    : LayeredNumber.zero();
}

function setDevKitAutoFlatBonus(value) {
  if (value instanceof LayeredNumber) {
    DEVKIT_STATE.bonuses.autoFlat = value.clone();
    return;
  }
  DEVKIT_STATE.bonuses.autoFlat = new LayeredNumber(value ?? 0);
}

let coreState = null;

function cloneLayeredNumber(source, fallback = 0) {
  if (source instanceof LayeredNumber) {
    return source.clone();
  }
  return new LayeredNumber(source ?? fallback);
}

function resolveOfflineTicketConfig(config) {
  const fallbackConfig = typeof OFFLINE_TICKET_CONFIG === 'object' && OFFLINE_TICKET_CONFIG
    ? OFFLINE_TICKET_CONFIG
    : { secondsPerTicket: 600, capSeconds: 3600 };
  const seconds = Number(config?.secondsPerTicket);
  const cap = Number(config?.capSeconds);
  if (Number.isFinite(seconds) && seconds > 0 && Number.isFinite(cap) && cap >= seconds) {
    return {
      secondsPerTicket: seconds,
      capSeconds: cap,
      progressSeconds: 0
    };
  }
  const fallbackSeconds = Number.isFinite(seconds) && seconds > 0
    ? seconds
    : fallbackConfig.secondsPerTicket;
  const fallbackCap = Number.isFinite(cap) && cap >= fallbackSeconds
    ? cap
    : Math.max(fallbackConfig.capSeconds, fallbackSeconds);
  return {
    secondsPerTicket: fallbackSeconds,
    capSeconds: fallbackCap,
    progressSeconds: 0
  };
}

function initializeCoreState(options = {}) {
  if (coreState) {
    return coreState;
  }

  const fallbackBasePerClick = typeof BASE_PER_CLICK !== 'undefined'
    ? BASE_PER_CLICK
    : new LayeredNumber(1);
  const fallbackBasePerSecond = typeof BASE_PER_SECOND !== 'undefined'
    ? BASE_PER_SECOND
    : new LayeredNumber(1);
  const fallbackTicketInterval = typeof DEFAULT_TICKET_STAR_INTERVAL_SECONDS === 'number'
    ? DEFAULT_TICKET_STAR_INTERVAL_SECONDS
    : 600;
  const fallbackMusicVolume = typeof DEFAULT_MUSIC_VOLUME === 'number'
    ? DEFAULT_MUSIC_VOLUME
    : 0.5;
  const fallbackMusicEnabled = typeof DEFAULT_MUSIC_ENABLED === 'boolean'
    ? DEFAULT_MUSIC_ENABLED
    : true;
  const fallbackOfflineConfig = typeof OFFLINE_TICKET_CONFIG === 'object'
    ? OFFLINE_TICKET_CONFIG
    : { secondsPerTicket: 600, capSeconds: 3600 };

  const {
    basePerClick = fallbackBasePerClick,
    basePerSecond = fallbackBasePerSecond,
    defaultThemeId,
    offlineTicketConfig = fallbackOfflineConfig,
    defaultTicketStarIntervalSeconds = fallbackTicketInterval,
    defaultMusicVolume = fallbackMusicVolume,
    defaultMusicEnabled = fallbackMusicEnabled
  } = options;

  const resolvedOfflineConfig = resolveOfflineTicketConfig(offlineTicketConfig);
  const resolvedTheme = typeof defaultThemeId === 'string' && defaultThemeId
    ? defaultThemeId
    : 'dark';
  const resolvedOfflineMultiplier = typeof MYTHIQUE_OFFLINE_BASE === 'number'
    ? MYTHIQUE_OFFLINE_BASE
    : 1;

  coreState = {
    atoms: LayeredNumber.zero(),
    lifetime: LayeredNumber.zero(),
    perClick: cloneLayeredNumber(basePerClick, 1),
    perSecond: cloneLayeredNumber(basePerSecond, 1),
    basePerClick: cloneLayeredNumber(basePerClick, 1),
    basePerSecond: cloneLayeredNumber(basePerSecond, 1),
    gachaTickets: 0,
    bonusParticulesTickets: 0,
    upgrades: {},
    shopUnlocks: new Set(),
    elements: typeof createInitialElementCollection === 'function'
      ? createInitialElementCollection()
      : {},
    fusions: typeof createInitialFusionState === 'function'
      ? createInitialFusionState()
      : {},
    fusionBonuses: typeof createInitialFusionBonuses === 'function'
      ? createInitialFusionBonuses()
      : { apcFlat: 0, apsFlat: 0 },
    pageUnlocks: typeof createInitialPageUnlockState === 'function'
      ? createInitialPageUnlockState()
      : {},
    theme: resolvedTheme,
    arcadeBrickSkin: 'original',
    stats: createInitialStats(),
    production: createEmptyProductionBreakdown(),
    productionBase: createEmptyProductionBreakdown(),
    crit: typeof createDefaultCritState === 'function' ? createDefaultCritState() : null,
    baseCrit: typeof createDefaultCritState === 'function' ? createDefaultCritState() : null,
    lastCritical: null,
    elementBonusSummary: {},
    trophies: new Set(),
    offlineGainMultiplier: resolvedOfflineMultiplier,
    offlineTickets: resolvedOfflineConfig,
    sudokuOfflineBonus: null,
    ticketStarAutoCollect: null,
    ticketStarAverageIntervalSeconds: Number.isFinite(defaultTicketStarIntervalSeconds)
      ? Math.max(1, defaultTicketStarIntervalSeconds)
      : fallbackTicketInterval,
    ticketStarUnlocked: false,
    frenzySpawnBonus: { perClick: 1, perSecond: 1 },
    musicTrackId: null,
    musicVolume: Number.isFinite(defaultMusicVolume)
      ? Math.max(0, Math.min(1, defaultMusicVolume))
      : fallbackMusicVolume,
    musicEnabled: defaultMusicEnabled !== false,
    bigBangButtonVisible: false,
    apsCrit: createDefaultApsCritState(),
    arcadeProgress: createInitialArcadeProgress()
  };

  if (typeof globalThis !== 'undefined') {
    globalThis.atom2universGameState = coreState;
  }

  if (typeof applyFrenzySpawnChanceBonus === 'function') {
    applyFrenzySpawnChanceBonus(coreState.frenzySpawnBonus);
  }

  if (typeof setParticulesBrickSkinPreference === 'function') {
    setParticulesBrickSkinPreference(coreState.arcadeBrickSkin);
  }

  return coreState;
}

function getGameState() {
  if (!coreState) {
    return initializeCoreState();
  }
  return coreState;
}

function ensureApsCritState() {
  const state = getGameState();
  if (!state.apsCrit || typeof state.apsCrit !== 'object') {
    state.apsCrit = createDefaultApsCritState();
    return state.apsCrit;
  }
  const current = state.apsCrit;
  if (!Array.isArray(current.effects)) {
    current.effects = [];
  }
  current.effects = current.effects
    .map(entry => normalizeApsCritEffect(entry))
    .filter(effect => effect != null);
  return current;
}

function getApsCritRemainingSeconds(state = ensureApsCritState()) {
  if (!state || !Array.isArray(state.effects) || !state.effects.length) {
    return 0;
  }
  return state.effects.reduce(
    (max, effect) => Math.max(max, Number(effect?.remainingSeconds) || 0),
    0
  );
}

function getApsCritMultiplier(state = ensureApsCritState()) {
  if (!state || !Array.isArray(state.effects) || !state.effects.length) {
    return 1;
  }
  const totalAdd = state.effects.reduce((sum, effect) => {
    const timeLeft = Number(effect?.remainingSeconds) || 0;
    if (timeLeft <= 0) {
      return sum;
    }
    const value = Number(effect?.multiplierAdd) || 0;
    if (value <= 0) {
      return sum;
    }
    return sum + value;
  }, 0);
  return totalAdd > 0 ? 1 + totalAdd : 1;
}

function normalizeArcadeProgress(raw) {
  const base = createInitialArcadeProgress();
  if (!raw || typeof raw !== 'object') {
    return base;
  }
  const result = { version: 1, entries: { ...base.entries } };
  const sourceEntries = raw.entries && typeof raw.entries === 'object' ? raw.entries : raw;

  ARCADE_GAME_IDS.forEach(id => {
    const entry = sourceEntries[id];
    if (!entry) {
      result.entries[id] = null;
      return;
    }
    const payload = entry && typeof entry === 'object' ? (entry.state ?? entry) : null;
    if (!payload || typeof payload !== 'object') {
      result.entries[id] = null;
      return;
    }
    try {
      result.entries[id] = {
        state: JSON.parse(JSON.stringify(payload)),
        updatedAt: Number.isFinite(entry.updatedAt) ? entry.updatedAt : Date.now()
      };
    } catch (error) {
      result.entries[id] = null;
    }
  });

  return result;
}

function parseStats(saved) {
  const stats = createInitialStats();
  if (!saved || typeof saved !== 'object') {
    return stats;
  }

  let legacySessionStart = null;
  if (saved.session && typeof saved.session.startedAt === 'number') {
    const candidate = Number(saved.session.startedAt);
    if (Number.isFinite(candidate) && candidate > 0) {
      legacySessionStart = candidate;
    }
  }

  if (saved.global) {
    stats.global.manualClicks = Number(saved.global.manualClicks) || 0;
    stats.global.playTimeMs = Number(saved.global.playTimeMs) || 0;
    stats.global.frenzyTriggers = normalizeFrenzyStats(saved.global.frenzyTriggers);
    stats.global.apcAtoms = LayeredNumber.fromJSON(saved.global.apcAtoms);
    stats.global.apsAtoms = LayeredNumber.fromJSON(saved.global.apsAtoms);
    stats.global.offlineAtoms = LayeredNumber.fromJSON(saved.global.offlineAtoms);
    stats.global.apcFrenzy = normalizeApcFrenzyStats(saved.global.apcFrenzy);
    const globalStart = typeof saved.global.startedAt === 'number'
      ? Number(saved.global.startedAt)
      : null;
    if (Number.isFinite(globalStart) && globalStart > 0) {
      stats.global.startedAt = globalStart;
    } else if (legacySessionStart != null) {
      stats.global.startedAt = legacySessionStart;
    }
  } else if (legacySessionStart != null) {
    stats.global.startedAt = legacySessionStart;
  }

  stats.session = {
    atomsGained: LayeredNumber.zero(),
    apcAtoms: LayeredNumber.zero(),
    apsAtoms: LayeredNumber.zero(),
    offlineAtoms: LayeredNumber.zero(),
    manualClicks: 0,
    onlineTimeMs: 0,
    startedAt: Date.now(),
    frenzyTriggers: { perClick: 0, perSecond: 0, total: 0 },
    apcFrenzy: createInitialApcFrenzyStats()
  };

  return stats;
}

export {
  ARCADE_GAME_IDS,
  DEVKIT_STATE,
  createDefaultApsCritState,
  createEmptyProductionEntry,
  createEmptyProductionBreakdown,
  createInitialApcFrenzyStats,
  createInitialArcadeProgress,
  createInitialStats,
  ensureApcFrenzyStats,
  ensureApsCritState,
  getApsCritMultiplier,
  getApsCritRemainingSeconds,
  getDevKitAutoFlatBonus,
  getGameState,
  getLayeredStat,
  incrementLayeredStat,
  initializeCoreState,
  isDevKitGachaFree,
  isDevKitShopFree,
  normalizeArcadeProgress,
  normalizeApcFrenzyStats,
  normalizeApsCritEffect,
  normalizeApsCritState,
  normalizeFrenzyStats,
  parseStats,
  setDevKitAutoFlatBonus
};
