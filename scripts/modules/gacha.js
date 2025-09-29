const DEFAULT_GACHA_TICKET_COST = 1;

const DEFAULT_GACHA_RARITIES = [
  {
    id: 'commun',
    label: 'Commun cosmique',
    description: 'Les briques fondamentales présentes dans la majorité des nébuleuses.',
    weight: 55,
    color: '#4f7ec2'
  },
  {
    id: 'essentiel',
    label: 'Essentiel planétaire',
    description: 'Éléments abondants dans les mondes rocheux et les atmosphères habitables.',
    weight: 20,
    color: '#4ba88c'
  },
  {
    id: 'stellaire',
    label: 'Forge stellaire',
    description: 'Alliages façonnés au coeur des étoiles et disséminés par les supernovæ.',
    weight: 12,
    color: '#8caf58'
  },
  {
    id: 'singulier',
    label: 'Singularité minérale',
    description: 'Cristaux recherchés, rarement concentrés au même endroit.',
    weight: 7,
    color: '#d08a54'
  },
  {
    id: 'mythique',
    label: 'Mythe quantique',
    description: 'Éléments légendaires aux propriétés extrêmes et insaisissables.',
    weight: 4,
    color: '#c46a9a'
  },
  {
    id: 'irreel',
    label: 'Irréel',
    description: 'Synthèses artificielles nées uniquement dans des accélérateurs.',
    weight: 2,
    color: '#7d6fc9'
  }
];

function sanitizeGachaRarities(rawRarities) {
  const base = Array.isArray(rawRarities) && rawRarities.length
    ? rawRarities
    : DEFAULT_GACHA_RARITIES;
  const seen = new Set();
  const sanitized = [];
  base.forEach(entry => {
    if (!entry || typeof entry !== 'object') return;
    const rawId = entry.id ?? entry.rarity ?? entry.key;
    if (!rawId) return;
    const id = String(rawId).trim();
    if (!id || seen.has(id)) return;
    const weightValue = Number(entry.weight ?? entry.rate ?? entry.dropRate ?? entry.chance ?? 0);
    sanitized.push({
      id,
      label: entry.label ? String(entry.label) : id,
      description: entry.description ? String(entry.description) : '',
      weight: Number.isFinite(weightValue) ? weightValue : 0,
      color: entry.color ? String(entry.color) : null
    });
    seen.add(id);
  });
  if (!sanitized.length) {
    return DEFAULT_GACHA_RARITIES.map(entry => ({ ...entry }));
  }
  return sanitized;
}

const rawGachaConfig = CONFIG.gacha && typeof CONFIG.gacha === 'object'
  ? CONFIG.gacha
  : {};

const GACHA_TICKET_COST = Math.max(
  1,
  Math.floor(
    Number(
      rawGachaConfig.ticketCost
        ?? rawGachaConfig.cost
        ?? DEFAULT_GACHA_TICKET_COST
    ) || DEFAULT_GACHA_TICKET_COST
  )
);

const BASE_GACHA_RARITIES = sanitizeGachaRarities(rawGachaConfig.rarities).map(entry => ({
  ...entry,
  weight: Math.max(0, Number(entry.weight) || 0)
}));

BASE_GACHA_RARITIES.forEach(entry => {
  configuredRarityIds.delete(entry.id);
});

configuredRarityIds.forEach(id => {
  const fallback = { id, label: id, description: '', weight: 0, color: null };
  BASE_GACHA_RARITIES.push(fallback);
});

const BASE_GACHA_RARITY_ID_SET = new Set(BASE_GACHA_RARITIES.map(entry => entry.id));

const WEEKDAY_KEYS = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];

const GACHA_FEATURED_LABELS_BY_DAY = Object.freeze({
  monday: '+Singularité Minérale',
  thursday: '+Singularité Minérale',
  tuesday: '+Mythe Quantique',
  friday: '+Mythe Quantique',
  wednesday: '+Iréel',
  saturday: '+Iréel'
});

function sanitizeWeeklyRarityWeights(rawWeights) {
  const sanitized = {};
  WEEKDAY_KEYS.forEach(day => {
    sanitized[day] = {};
  });
  if (!rawWeights || typeof rawWeights !== 'object') {
    return sanitized;
  }
  Object.entries(rawWeights).forEach(([rawDay, value]) => {
    if (!rawDay) return;
    const dayKey = WEEKDAY_KEYS.find(day => day === String(rawDay).toLowerCase());
    if (!dayKey) return;
    if (!value || typeof value !== 'object') return;
    Object.entries(value).forEach(([rawRarity, rawWeight]) => {
      if (!rawRarity) return;
      const rarityId = String(rawRarity).trim();
      if (!rarityId) return;
      if (BASE_GACHA_RARITY_ID_SET.size && !BASE_GACHA_RARITY_ID_SET.has(rarityId)) {
        return;
      }
      const numericWeight = Number(rawWeight);
      if (!Number.isFinite(numericWeight)) return;
      sanitized[dayKey][rarityId] = numericWeight;
    });
  });
  return sanitized;
}

const WEEKLY_RARITY_WEIGHTS = sanitizeWeeklyRarityWeights(
  rawGachaConfig.weeklyRarityWeights
    ?? rawGachaConfig.weeklyWeights
    ?? rawGachaConfig.dailyRarityWeights
    ?? {}
);

function getEffectiveGachaRaritiesForDate(date = new Date()) {
  const targetDate = date instanceof Date && !Number.isNaN(date.getTime())
    ? date
    : new Date();
  const dayKey = WEEKDAY_KEYS[targetDate.getDay()] ?? null;
  const overrides = dayKey ? WEEKLY_RARITY_WEIGHTS[dayKey] ?? {} : {};
  const effective = BASE_GACHA_RARITIES.map(entry => {
    const hasOverride = Object.prototype.hasOwnProperty.call(overrides, entry.id);
    const overrideValue = hasOverride ? Number(overrides[entry.id]) : null;
    const resolvedWeight = Number.isFinite(overrideValue) ? overrideValue : entry.weight;
    return {
      ...entry,
      weight: Math.max(0, Number(resolvedWeight) || 0)
    };
  });
  if (dayKey) {
    Object.entries(overrides).forEach(([rarityId, rawWeight]) => {
      if (BASE_GACHA_RARITY_ID_SET.has(rarityId)) {
        return;
      }
      const numericWeight = Number(rawWeight);
      if (!Number.isFinite(numericWeight)) {
        return;
      }
      effective.push({
        id: rarityId,
        label: rarityId,
        description: '',
        weight: Math.max(0, numericWeight),
        color: null
      });
    });
  }
  return effective;
}

let GACHA_RARITIES = [];
const GACHA_RARITY_MAP = new Map();
let activeGachaWeightDayKey = null;

function refreshGachaRarities(date = new Date(), { force = false } = {}) {
  const targetDate = date instanceof Date && !Number.isNaN(date.getTime())
    ? date
    : new Date();
  const dayKey = WEEKDAY_KEYS[targetDate.getDay()] ?? null;
  if (!force && dayKey && dayKey === activeGachaWeightDayKey) {
    return false;
  }
  const effective = getEffectiveGachaRaritiesForDate(targetDate);
  GACHA_RARITIES = effective;
  GACHA_RARITY_MAP.clear();
  GACHA_RARITIES.forEach(entry => {
    GACHA_RARITY_MAP.set(entry.id, entry);
  });
  activeGachaWeightDayKey = dayKey;
  updateGachaFeaturedInfo(dayKey);
  return true;
}

function getGachaFeaturedLabelForDayKey(dayKey) {
  if (!dayKey) {
    return null;
  }
  return GACHA_FEATURED_LABELS_BY_DAY[dayKey] ?? null;
}

function updateGachaFeaturedInfo(dayKey = WEEKDAY_KEYS[new Date().getDay()] ?? null) {
  if (typeof elements === 'undefined' || !elements) {
    return;
  }
  const featuredInfo = elements.gachaFeaturedInfo;
  if (!featuredInfo) {
    return;
  }
  const label = getGachaFeaturedLabelForDayKey(dayKey);
  if (label) {
    featuredInfo.textContent = label;
    featuredInfo.hidden = false;
  } else {
    featuredInfo.textContent = '';
    featuredInfo.hidden = true;
  }
}

function getCurrentGachaTotalWeight() {
  return GACHA_RARITIES.reduce((total, entry) => total + (entry.weight || 0), 0);
}

const RARITY_IDS = BASE_GACHA_RARITIES.map(entry => entry.id);
const GACHA_RARITY_ORDER = new Map(RARITY_IDS.map((id, index) => [id, index]));
const RARITY_LABEL_MAP = new Map(BASE_GACHA_RARITIES.map(entry => [entry.id, entry.label || entry.id]));
const INFO_BONUS_RARITIES = RARITY_IDS.length > 0
  ? [...RARITY_IDS]
  : ['commun', 'essentiel', 'stellaire', 'singulier', 'mythique', 'irreel'];
const INFO_BONUS_SUBTITLE = INFO_BONUS_RARITIES.length
  ? INFO_BONUS_RARITIES.map(id => RARITY_LABEL_MAP.get(id) || id).join(' · ')
  : 'Raretés indisponibles';

const rawTicketStarConfig = CONFIG.ticketStar && typeof CONFIG.ticketStar === 'object'
  ? CONFIG.ticketStar
  : {};

const TICKET_STAR_CONFIG = {
  averageSpawnIntervalMs: (() => {
    const raw = Number(
      rawTicketStarConfig.averageSpawnIntervalSeconds
        ?? rawTicketStarConfig.averageIntervalSeconds
        ?? 60
    );
    const seconds = Number.isFinite(raw) && raw > 0 ? raw : 60;
    return seconds * 1000;
  })(),
  speed: (() => {
    const raw = Number(
      rawTicketStarConfig.speedPixelsPerSecond
        ?? rawTicketStarConfig.speed
        ?? 90
    );
    return Number.isFinite(raw) && raw > 0 ? raw : 90;
  })(),
  size: (() => {
    const raw = Number(rawTicketStarConfig.size ?? rawTicketStarConfig.spriteSize ?? 72);
    return Number.isFinite(raw) && raw > 0 ? raw : 72;
  })(),
  rewardTickets: (() => {
    const raw = Number(rawTicketStarConfig.rewardTickets ?? rawTicketStarConfig.tickets ?? 1);
    return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1;
  })()
};

const DEFAULT_TICKET_STAR_INTERVAL_SECONDS = TICKET_STAR_CONFIG.averageSpawnIntervalMs / 1000;
const MYTHIQUE_OFFLINE_BASE = MYTHIQUE_SPECIAL_BONUS_CONFIG.offline.baseMultiplier;
const MYTHIQUE_OFFLINE_PER_DUPLICATE = MYTHIQUE_SPECIAL_BONUS_CONFIG.offline.perDuplicate;
const MYTHIQUE_OFFLINE_CAP = MYTHIQUE_SPECIAL_BONUS_CONFIG.offline.cap;
const MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS = MYTHIQUE_SPECIAL_BONUS_CONFIG.overflow.flatBonus;
const MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS = MYTHIQUE_SPECIAL_BONUS_CONFIG.ticket.minIntervalSeconds;
const MYTHIQUE_TICKET_UNIQUE_REDUCTION_SECONDS = MYTHIQUE_SPECIAL_BONUS_CONFIG.ticket.uniqueReductionSeconds;
const MYTHIQUE_FRENZY_SPAWN_BONUS_MULTIPLIER = MYTHIQUE_SPECIAL_BONUS_CONFIG.frenzy.multiplier;
const MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP = (() => {
  if (
    MYTHIQUE_OFFLINE_PER_DUPLICATE > 0
    && MYTHIQUE_OFFLINE_CAP > MYTHIQUE_OFFLINE_BASE
  ) {
    return Math.ceil((MYTHIQUE_OFFLINE_CAP - MYTHIQUE_OFFLINE_BASE) / MYTHIQUE_OFFLINE_PER_DUPLICATE);
  }
  return Number.POSITIVE_INFINITY;
})();

const PRODUCTION_STEP_DEFINITIONS = new Map();

function defineProductionStep(id, type, label, extra = {}) {
  if (!id || PRODUCTION_STEP_DEFINITIONS.has(id)) {
    return;
  }
  PRODUCTION_STEP_DEFINITIONS.set(id, { id, type, label, ...extra });
}

defineProductionStep('baseFlat', 'base', 'Base flat', { source: 'baseFlat' });
defineProductionStep('shopFlat', 'flat', 'Magasin', { source: 'shopFlat' });
defineProductionStep('elementFlat', 'flat', 'Éléments', { source: 'elementFlat' });
defineProductionStep('fusionFlat', 'flat', 'Fusions', { source: 'fusionFlat' });
defineProductionStep('shopBonus1', 'multiplier', 'Bonus shop 1', { source: 'shopBonus1' });
defineProductionStep('shopBonus2', 'multiplier', 'Bonus shop 2', { source: 'shopBonus2' });
defineProductionStep('frenzy', 'multiplier', 'Frénésie', { source: 'frenzy' });
defineProductionStep(
  'trophyMultiplier',
  'multiplier',
  'Multiplicateur trophées',
  { source: 'trophyMultiplier' }
);
defineProductionStep('total', 'total', '= Total');

RARITY_IDS.forEach(rarityId => {
  const rarityLabel = RARITY_LABEL_MAP.get(rarityId) || rarityId;
  defineProductionStep(
    `rarityMultiplier:${rarityId}`,
    'multiplier',
    `Rareté ${rarityLabel}`,
    { source: 'rarityMultiplier', rarityId }
  );
});

const DEFAULT_PRODUCTION_STEP_IDS = [
  'baseFlat',
  'shopFlat',
  'elementFlat',
  'fusionFlat',
  'shopBonus1',
  'shopBonus2',
  'frenzy',
  ...RARITY_IDS.map(id => `rarityMultiplier:${id}`),
  'trophyMultiplier',
  'total'
];

function resolveProductionStepOrder(configOrder) {
  const seen = new Set();
  const resolved = [];

  const pushStep = (id, labelOverride = null) => {
    if (!id) return;
    let normalizedId = id;
    if (id === 'shopMultiplier') {
      normalizedId = 'shopBonus1';
    } else if (id === 'shopMultiplier2' || id === 'shopMultiplierSecondary') {
      normalizedId = 'shopBonus2';
    }
    if (seen.has(normalizedId)) return;
    const base = PRODUCTION_STEP_DEFINITIONS.get(normalizedId);
    if (!base) return;
    const entry = { ...base };
    if (labelOverride && typeof labelOverride === 'string') {
      entry.label = labelOverride;
    }
    resolved.push(entry);
    seen.add(normalizedId);
  };

  if (Array.isArray(configOrder)) {
    configOrder.forEach(item => {
      if (!item) return;
      if (typeof item === 'string') {
        pushStep(item.trim());
        return;
      }
      if (typeof item === 'object') {
        const type = item.type ?? item.kind;
        if ((type === 'rarity' || type === 'rarityMultiplier') && item.rarity) {
          const rarityId = String(item.rarity).trim();
          const label = typeof item.label === 'string' ? item.label.trim() : null;
          pushStep(`rarityMultiplier:${rarityId}`, label);
          return;
        }
        const rawId = item.id ?? item.key ?? item.step;
        if (!rawId) return;
        const id = String(rawId).trim();
        const label = typeof item.label === 'string' ? item.label.trim() : null;
        pushStep(id, label);
      }
    });
  }

  DEFAULT_PRODUCTION_STEP_IDS.forEach(id => {
    if (!seen.has(id)) {
      pushStep(id);
    }
  });

  return resolved;
}

const PRODUCTION_STEP_ORDER = resolveProductionStepOrder(CONFIG.infoPanels?.productionOrder);

const gachaPools = new Map();
const gachaRarityRows = new Map();

function rebuildGachaPools() {
  gachaPools.clear();
  GACHA_RARITIES.forEach(entry => {
    gachaPools.set(entry.id, []);
  });
  periodicElements.forEach(def => {
    const rarity = elementRarityIndex.get(def.id);
    if (!rarity) return;
    if (!gachaPools.has(rarity)) {
      gachaPools.set(rarity, []);
    }
    gachaPools.get(rarity).push(def.id);
  });
}

refreshGachaRarities(new Date(), { force: true });
rebuildGachaPools();

function getRarityPoolSize(rarityId) {
  const pool = gachaPools.get(rarityId);
  return Array.isArray(pool) ? pool.length : 0;
}

LayeredNumber.LAYER1_THRESHOLD = CONFIG.numbers?.layer1Threshold ?? 1e6;
LayeredNumber.LAYER1_DOWN = CONFIG.numbers?.layer1Downshift ?? 5;
LayeredNumber.LOG_DIFF_LIMIT = CONFIG.numbers?.logDifferenceLimit ?? 15;
LayeredNumber.EPSILON = CONFIG.numbers?.epsilon ?? 1e-12;

const BASE_PER_CLICK = toLayeredNumber(CONFIG.progression?.basePerClick, 1);
const BASE_PER_SECOND = toLayeredNumber(CONFIG.progression?.basePerSecond, 0);
const DEFAULT_THEME = CONFIG.progression?.defaultTheme ?? 'dark';
const DEFAULT_MUSIC_VOLUME = 0.5;
const DEFAULT_MUSIC_ENABLED = true;

function clampMusicVolume(value, fallback = DEFAULT_MUSIC_VOLUME) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  if (numeric <= 0) {
    return 0;
  }
  if (numeric >= 1) {
    return 1;
  }
  return numeric;
}
const OFFLINE_TICKET_CONFIG = normalizeOfflineTicketConfig(CONFIG.progression?.offlineTickets);
const OFFLINE_GAIN_CAP = CONFIG.progression?.offlineCapSeconds ?? 60 * 60 * 12;

function clampCritChance(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  if (numeric >= 1) {
    return 1;
  }
  return numeric;
}

function normalizeCritConfig(raw) {
  const defaults = {
    baseChance: 0.05,
    baseMultiplier: 2,
    maxMultiplier: 100
  };
  if (!raw || typeof raw !== 'object') {
    return { ...defaults };
  }
  const parseNumber = (value, fallback) => {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : fallback;
  };
  const baseChance = clampCritChance(parseNumber(raw.baseChance ?? raw.chance ?? raw.defaultChance, defaults.baseChance));
  const baseMultiplierRaw = parseNumber(
    raw.baseMultiplier ?? raw.multiplier ?? raw.defaultMultiplier,
    defaults.baseMultiplier
  );
  const baseMultiplier = baseMultiplierRaw > 0 ? baseMultiplierRaw : defaults.baseMultiplier;
  const maxMultiplierRaw = parseNumber(raw.maxMultiplier ?? raw.cap ?? raw.maximum, defaults.maxMultiplier);
  const maxMultiplier = Math.max(1, maxMultiplierRaw);
  return {
    baseChance,
    baseMultiplier,
    maxMultiplier: Math.max(baseMultiplier, maxMultiplier)
  };
}

const CRIT_DEFAULTS = normalizeCritConfig(CONFIG.progression?.crit);

function createDefaultCritState() {
  const multiplier = Math.max(1, Math.min(CRIT_DEFAULTS.baseMultiplier, CRIT_DEFAULTS.maxMultiplier));
  return {
    chance: clampCritChance(CRIT_DEFAULTS.baseChance),
    multiplier,
    rawMultiplier: Math.max(1, CRIT_DEFAULTS.baseMultiplier),
    maxMultiplier: Math.max(1, CRIT_DEFAULTS.maxMultiplier)
  };
}

function cloneCritState(state) {
  if (!state || typeof state !== 'object') {
    return createDefaultCritState();
  }
  const baseChance = Number(state.chance ?? state.baseChance);
  const chance = clampCritChance(Number.isFinite(baseChance) ? baseChance : CRIT_DEFAULTS.baseChance);
  const rawMultiplierValue = Number(state.rawMultiplier ?? state.multiplier);
  const rawMultiplier = Number.isFinite(rawMultiplierValue) && rawMultiplierValue > 0
    ? rawMultiplierValue
    : CRIT_DEFAULTS.baseMultiplier;
  const maxMultiplierValue = Number(state.maxMultiplier);
  const maxMultiplier = Number.isFinite(maxMultiplierValue) && maxMultiplierValue > 0
    ? maxMultiplierValue
    : CRIT_DEFAULTS.maxMultiplier;
  const effectiveMultiplier = Math.max(1, Math.min(Number(state.multiplier ?? rawMultiplier) || rawMultiplier, maxMultiplier));
  return {
    chance,
    multiplier: effectiveMultiplier,
    rawMultiplier: Math.max(1, rawMultiplier),
    maxMultiplier: Math.max(1, maxMultiplier)
  };
}

function createCritAccumulator() {
  return {
    chanceAdd: 0,
    chanceMult: 1,
    chanceSet: null,
    multiplierAdd: 0,
    multiplierMult: 1,
    multiplierSet: null,
    maxMultiplierAdd: 0,
    maxMultiplierMult: 1,
    maxMultiplierSet: null
  };
}

function applyCritModifiersFromEffect(accumulator, effect) {
  if (!accumulator || !effect || typeof effect !== 'object') {
    return;
  }
  const readNumber = value => {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
  };
  const nested = effect.crit && typeof effect.crit === 'object' ? effect.crit : null;

  const chanceSetDirect = readNumber(effect.critChanceSet ?? effect.critChance ?? nested?.chanceSet ?? nested?.chance);
  if (chanceSetDirect != null) {
    accumulator.chanceSet = Math.max(0, chanceSetDirect);
  }
  const chanceAdd = readNumber(effect.critChanceAdd ?? nested?.chanceAdd);
  if (chanceAdd != null) {
    accumulator.chanceAdd += chanceAdd;
  }
  const chanceMult = readNumber(effect.critChanceMult ?? nested?.chanceMult);
  if (chanceMult != null) {
    accumulator.chanceMult *= Math.max(0, chanceMult);
  }

  const multiplierSet = readNumber(effect.critMultiplierSet ?? effect.critMultiplier ?? nested?.multiplierSet ?? nested?.multiplier);
  if (multiplierSet != null) {
    accumulator.multiplierSet = Math.max(0, multiplierSet);
  }
  const multiplierAdd = readNumber(effect.critMultiplierAdd ?? nested?.multiplierAdd);
  if (multiplierAdd != null) {
    accumulator.multiplierAdd += multiplierAdd;
  }
  const multiplierMult = readNumber(effect.critMultiplierMult ?? nested?.multiplierMult);
  if (multiplierMult != null) {
    accumulator.multiplierMult *= Math.max(0, multiplierMult);
  }

  const maxMultiplierSet = readNumber(
    effect.critMaxMultiplierSet ?? effect.critMaxMultiplier ?? nested?.maxMultiplierSet ?? nested?.maxMultiplier ?? nested?.cap
  );
  if (maxMultiplierSet != null) {
    accumulator.maxMultiplierSet = Math.max(0, maxMultiplierSet);
  }
  const maxMultiplierAdd = readNumber(effect.critMaxMultiplierAdd ?? nested?.maxMultiplierAdd ?? nested?.capAdd);
  if (maxMultiplierAdd != null) {
    accumulator.maxMultiplierAdd += maxMultiplierAdd;
  }
  const maxMultiplierMult = readNumber(effect.critMaxMultiplierMult ?? nested?.maxMultiplierMult ?? nested?.capMult);
  if (maxMultiplierMult != null) {
    accumulator.maxMultiplierMult *= Math.max(0, maxMultiplierMult);
  }
}

function applyRepeatedCritEffect(accumulator, effectConfig, repetitions) {
  if (!accumulator || !effectConfig) {
    return;
  }
  const times = Math.max(0, Math.floor(Number(repetitions) || 0));
  if (times <= 0) {
    return;
  }
  const effect = {};
  if (effectConfig.chanceSet != null) {
    effect.critChanceSet = Math.max(0, effectConfig.chanceSet);
  }
  if (effectConfig.chanceAdd) {
    effect.critChanceAdd = effectConfig.chanceAdd * times;
  }
  if (effectConfig.chanceMult != null) {
    const base = effectConfig.chanceMult;
    if (base > 0 && base !== 1) {
      effect.critChanceMult = Math.pow(base, times);
    }
  }
  if (effectConfig.multiplierSet != null) {
    effect.critMultiplierSet = Math.max(0, effectConfig.multiplierSet);
  }
  if (effectConfig.multiplierAdd) {
    effect.critMultiplierAdd = effectConfig.multiplierAdd * times;
  }
  if (effectConfig.multiplierMult != null) {
    const base = effectConfig.multiplierMult;
    if (base > 0 && base !== 1) {
      effect.critMultiplierMult = Math.pow(base, times);
    }
  }
  if (effectConfig.maxMultiplierSet != null) {
    effect.critMaxMultiplierSet = Math.max(0, effectConfig.maxMultiplierSet);
  }
  if (effectConfig.maxMultiplierAdd) {
    effect.critMaxMultiplierAdd = effectConfig.maxMultiplierAdd * times;
  }
  if (effectConfig.maxMultiplierMult != null) {
    const base = effectConfig.maxMultiplierMult;
    if (base > 0 && base !== 1) {
      effect.critMaxMultiplierMult = Math.pow(base, times);
    }
  }
  applyCritModifiersFromEffect(accumulator, effect);
}

function finalizeCritEffect(accumulator) {
  if (!accumulator) {
    return null;
  }
  const effect = {};
  if (accumulator.chanceSet != null) {
    effect.critChanceSet = accumulator.chanceSet;
  }
  if (accumulator.chanceAdd !== 0) {
    effect.critChanceAdd = accumulator.chanceAdd;
  }
  if (accumulator.chanceMult !== 1) {
    effect.critChanceMult = accumulator.chanceMult;
  }
  if (accumulator.multiplierSet != null) {
    effect.critMultiplierSet = accumulator.multiplierSet;
  }
  if (accumulator.multiplierAdd !== 0) {
    effect.critMultiplierAdd = accumulator.multiplierAdd;
  }
  if (accumulator.multiplierMult !== 1) {
    effect.critMultiplierMult = accumulator.multiplierMult;
  }
  if (accumulator.maxMultiplierSet != null) {
    effect.critMaxMultiplierSet = accumulator.maxMultiplierSet;
  }
  if (accumulator.maxMultiplierAdd !== 0) {
    effect.critMaxMultiplierAdd = accumulator.maxMultiplierAdd;
  }
  if (accumulator.maxMultiplierMult !== 1) {
    effect.critMaxMultiplierMult = accumulator.maxMultiplierMult;
  }
  return Object.keys(effect).length ? effect : null;
}

function resolveCritState(accumulator) {
  const acc = accumulator || createCritAccumulator();
  const baseChance = acc.chanceSet != null ? acc.chanceSet : CRIT_DEFAULTS.baseChance;
  const chance = clampCritChance((baseChance + acc.chanceAdd) * (acc.chanceMult != null ? acc.chanceMult : 1));

  const baseMultiplier = acc.multiplierSet != null ? acc.multiplierSet : CRIT_DEFAULTS.baseMultiplier;
  let rawMultiplier = baseMultiplier + acc.multiplierAdd;
  rawMultiplier = Math.max(0, rawMultiplier);
  rawMultiplier *= acc.multiplierMult != null ? acc.multiplierMult : 1;
  rawMultiplier = Math.max(1, rawMultiplier);

  const baseMaxMultiplier = acc.maxMultiplierSet != null ? acc.maxMultiplierSet : CRIT_DEFAULTS.maxMultiplier;
  let maxMultiplier = baseMaxMultiplier + acc.maxMultiplierAdd;
  maxMultiplier = Math.max(0, maxMultiplier);
  maxMultiplier *= acc.maxMultiplierMult != null ? acc.maxMultiplierMult : 1;
  maxMultiplier = Math.max(1, maxMultiplier);

  const effectiveMultiplier = Math.max(1, Math.min(rawMultiplier, maxMultiplier));
  return {
    chance,
    multiplier: effectiveMultiplier,
    rawMultiplier,
    maxMultiplier
  };
}

const FRENZY_DEFAULTS = {
  displayDurationMs: 5000,
  effectDurationMs: 30000,
  multiplier: 2,
  baseMaxStacks: 1,
  spawnChancePerSecond: {
    perClick: 0.01,
    perSecond: 0.01
  }
};

function normalizeFrenzySpawnChance(raw) {
  const clamp = value => {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric < 0) {
      return 0;
    }
    if (numeric > 1) {
      return 1;
    }
    return numeric;
  };
  const defaults = FRENZY_DEFAULTS.spawnChancePerSecond;
  if (typeof raw === 'number') {
    const value = clamp(raw);
    return { perClick: value, perSecond: value };
  }
  if (raw && typeof raw === 'object') {
    const perClickRaw = raw.perClick ?? raw.click ?? raw.apc;
    const perSecondRaw = raw.perSecond ?? raw.auto ?? raw.aps;
    return {
      perClick: perClickRaw != null ? clamp(perClickRaw) : defaults.perClick,
      perSecond: perSecondRaw != null ? clamp(perSecondRaw) : defaults.perSecond
    };
  }
  return { ...defaults };
}

const FRENZY_CONFIG = {
  displayDurationMs: Math.max(0, Number(CONFIG.frenzies?.displayDurationMs ?? FRENZY_DEFAULTS.displayDurationMs) || 0),
  effectDurationMs: Math.max(0, Number(CONFIG.frenzies?.effectDurationMs ?? FRENZY_DEFAULTS.effectDurationMs) || 0),
  multiplier: Math.max(1, Number(CONFIG.frenzies?.multiplier ?? FRENZY_DEFAULTS.multiplier) || FRENZY_DEFAULTS.multiplier),
  baseMaxStacks: Math.max(1, Number(CONFIG.frenzies?.baseMaxStacks ?? FRENZY_DEFAULTS.baseMaxStacks) || FRENZY_DEFAULTS.baseMaxStacks),
  spawnChancePerSecond: normalizeFrenzySpawnChance(CONFIG.frenzies?.spawnChancePerSecond)
};

const FRENZY_TYPE_INFO = {
  perClick: {
    id: 'perClick',
    label: 'Frénésie APC',
    shortLabel: 'APC',
    asset: 'Assets/Image/frenesieAPC.png'
  },
  perSecond: {
    id: 'perSecond',
    label: 'Frénésie APS',
    shortLabel: 'APS',
    asset: 'Assets/Image/frenesieAPS.png'
  }
};

const FRENZY_TYPES = ['perClick', 'perSecond'];

const frenzySpawnChanceBonus = { perClick: 1, perSecond: 1 };

function applyFrenzySpawnChanceBonus(bonus) {
  const perClick = Number(bonus?.perClick);
  const perSecond = Number(bonus?.perSecond);
  frenzySpawnChanceBonus.perClick = Number.isFinite(perClick) && perClick > 0 ? perClick : 1;
  frenzySpawnChanceBonus.perSecond = Number.isFinite(perSecond) && perSecond > 0 ? perSecond : 1;
}

function getEffectiveFrenzySpawnChance(type) {
  const base = FRENZY_CONFIG.spawnChancePerSecond[type] ?? 0;
  if (!Number.isFinite(base) || base <= 0) {
    return 0;
  }
  const modifier = type === 'perClick'
    ? frenzySpawnChanceBonus.perClick
    : (type === 'perSecond' ? frenzySpawnChanceBonus.perSecond : 1);
  const total = base * modifier;
  if (!Number.isFinite(total) || total <= 0) {
    return 0;
  }
  return Math.min(1, total);
}

const FALLBACK_UPGRADES = (function createFallbackUpgrades() {
  if (typeof createShopBuildingDefinitions === 'function') {
    return createShopBuildingDefinitions();
  }

  const getLevel = (context, id) => {
    if (!context || typeof context !== 'object') {
      return 0;
    }
    const value = Number(context[id] ?? 0);
    return Number.isFinite(value) && value > 0 ? value : 0;
  };

  const getTotal = context => {
    if (!context || typeof context !== 'object') {
      return 0;
    }
    return Object.values(context).reduce((acc, value) => {
      const numeric = Number(value);
      return acc + (Number.isFinite(numeric) && numeric > 0 ? numeric : 0);
    }, 0);
  };

  const withDefaults = def => ({ maxLevel: DEFAULT_UPGRADE_MAX_LEVEL, ...def });

  return [
    {
      id: 'freeElectrons',
      name: 'Électrons libres',
      description: 'Libérez des électrons pour une production de base stable.',
      effectSummary:
        'Production passive : minimum +1 APS par niveau. À 100 exemplaires : chaque électron ajoute +1 APC (valeur arrondie).',
      category: 'auto',
      baseCost: 15,
      costScale: 1.15,
      effect: (level = 0) => {
        const rawAutoAdd = level;
        const autoAdd = level > 0 ? Math.max(level, Math.round(rawAutoAdd)) : 0;
        const clickAdd = level >= 100 ? level : 0;
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        return result;
      }
    },
    {
      id: 'physicsLab',
      name: 'Laboratoire de Physique',
      description: 'Des équipes de chercheurs boostent votre production atomique.',
      effectSummary:
        'Production passive : +1 APS par niveau. Chaque 10 labos accordent +5 % d’APC global. Accélérateur ≥200 : Labos +20 % APS.',
      category: 'auto',
      baseCost: 100,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const acceleratorLevel = getLevel(context, 'particleAccelerator');
        let productionMultiplier = 1;
        if (acceleratorLevel >= 200) {
          productionMultiplier *= 1.2;
        }
        const rawAutoAdd = level * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(level, Math.round(rawAutoAdd)) : 0;
        const clickBonus = Math.pow(1.05, Math.floor(level / 10));
        return {
          autoAdd,
          clickMult: clickBonus
        };
      }
    },
    {
      id: 'nuclearReactor',
      name: 'Réacteur nucléaire',
      description: 'Des réacteurs contrôlés libèrent une énergie colossale.',
      effectSummary:
        'Production passive : +10 APS par niveau (+1 % par 50 Électrons, +20 % si Labos ≥200). Palier 150 : APC global ×2.',
      category: 'auto',
      baseCost: 1000,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const electronLevel = getLevel(context, 'freeElectrons');
        const labLevel = getLevel(context, 'physicsLab');
        let productionMultiplier = 1;
        if (electronLevel > 0) {
          productionMultiplier *= 1 + 0.01 * Math.floor(electronLevel / 50);
        }
        if (labLevel >= 200) {
          productionMultiplier *= 1.2;
        }
        const baseAmount = 10 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = level >= 150 ? 2 : 1;
        return clickMult > 1
          ? { autoAdd, clickMult }
          : { autoAdd };
      }
    },
    {
      id: 'particleAccelerator',
      name: 'Accélérateur de particules',
      description: 'Boostez vos particules pour décupler l’APC.',
      effectSummary:
        'Production passive : +50 APS par niveau (bonus si ≥100 Supercalculateurs). Chaque niveau octroie +2 % d’APC. Palier 200 : +20 % production des Labos.',
      category: 'hybrid',
      baseCost: 12_000,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const supercomputerLevel = getLevel(context, 'supercomputer');
        let productionMultiplier = 1;
        if (supercomputerLevel >= 100) {
          productionMultiplier *= 1.5;
        }
        const baseAmount = 50 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = Math.pow(1.02, level);
        return { autoAdd, clickMult };
      }
    },
    {
      id: 'supercomputer',
      name: 'Supercalculateurs',
      description: 'Des centres de calcul quantique optimisent vos gains.',
      effectSummary:
        'Production passive : +500 APS par niveau (doublée par Stations ≥300). Chaque 25 unités offrent +1 % APS global.',
      category: 'auto',
      baseCost: 200_000,
      costScale: 1.15,
      effect: (level = 0, context = {}) => {
        const stationLevel = getLevel(context, 'spaceStation');
        let productionMultiplier = 1;
        if (stationLevel >= 300) {
          productionMultiplier *= 2;
        }
        const baseAmount = 500 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const autoMult = Math.pow(1.01, Math.floor(level / 25));
        return autoMult > 1
          ? { autoAdd, autoMult }
          : { autoAdd };
      }
    },
    {
      id: 'interstellarProbe',
      name: 'Sonde interstellaire',
      description: 'Explorez la galaxie pour récolter toujours plus.',
      effectSummary:
        'Production passive : +5 000 APS par niveau (boostée par Réacteurs). À 150 exemplaires : chaque sonde ajoute +10 APC.',
      category: 'hybrid',
      baseCost: 5e6,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const reactorLevel = getLevel(context, 'nuclearReactor');
        let productionMultiplier = 1;
        if (reactorLevel > 0) {
          productionMultiplier *= 1 + 0.001 * reactorLevel;
        }
        const baseAmount = 5000 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickAdd = level >= 150 ? level * 10 : 0;
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        return result;
      }
    },
    {
      id: 'spaceStation',
      name: 'Station spatiale',
      description: 'Des bases orbitales coordonnent votre expansion.',
      effectSummary:
        'Production passive : +50 000 APS par niveau. Chaque Station accorde +5 % d’APC. Palier 300 : Supercalculateurs +100 %.',
      category: 'hybrid',
      baseCost: 1e8,
      costScale: 1.2,
      effect: (level = 0) => {
        const baseAmount = 50_000 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = Math.pow(1.05, level);
        return { autoAdd, clickMult };
      }
    },
    {
      id: 'starForge',
      name: 'Forgeron d’étoiles',
      description: 'Façonnez des étoiles et dopez votre APC.',
      effectSummary:
        'Production passive : +500 000 APS par niveau (+2 % APS par Station). Palier 150 : +25 % APC global.',
      category: 'hybrid',
      baseCost: 5e10,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const stationLevel = getLevel(context, 'spaceStation');
        let productionMultiplier = 1;
        if (stationLevel > 0) {
          productionMultiplier *= 1 + 0.02 * stationLevel;
        }
        const baseAmount = 500_000 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = level >= 150 ? 1.25 : 1;
        return clickMult > 1
          ? { autoAdd, clickMult }
          : { autoAdd };
      }
    },
    {
      id: 'artificialGalaxy',
      name: 'Galaxie artificielle',
      description: 'Ingénierie galactique pour une expansion sans fin.',
      effectSummary:
        'Production passive : +5 000 000 APS par niveau (doublée par Bibliothèque ≥300). Chaque niveau augmente l’APS de 10 %. Palier 100 : +50 % APC global.',
      category: 'auto',
      baseCost: 1e13,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const libraryLevel = getLevel(context, 'omniverseLibrary');
        let productionMultiplier = 1;
        if (libraryLevel >= 300) {
          productionMultiplier *= 2;
        }
        const baseAmount = 5e6 * level;
        const rawAutoAdd = baseAmount * productionMultiplier;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const autoMult = Math.pow(1.1, level);
        const clickMult = level >= 100 ? 1.5 : 1;
        const result = { autoAdd };
        if (autoMult > 1) {
          result.autoMult = autoMult;
        }
        if (clickMult > 1) {
          result.clickMult = clickMult;
        }
        return result;
      }
    },
    {
      id: 'multiverseSimulator',
      name: 'Simulateur de Multivers',
      description: 'Simulez l’infini pour optimiser chaque seconde.',
      effectSummary:
        'Production passive : +500 000 000 APS par niveau. Synergie : +0,5 % APS global par bâtiment possédé. Palier 200 : coûts des bâtiments −5 %.',
      category: 'auto',
      baseCost: 1e16,
      costScale: 1.2,
      effect: (level = 0, context = {}) => {
        const baseAmount = 5e8 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const totalBuildings = getTotal(context);
        const autoMult = totalBuildings > 0 ? Math.pow(1.005, totalBuildings) : 1;
        return autoMult > 1
          ? { autoAdd, autoMult }
          : { autoAdd };
      }
    },
    {
      id: 'realityWeaver',
      name: 'Tisseur de Réalité',
      description: 'Tissez les lois physiques à votre avantage.',
      effectSummary:
        'Production passive : +10 000 000 000 APS par niveau. Bonus clic arrondi : +0,1 × bâtiments × niveau. Palier 300 : production totale ×2.',
      category: 'hybrid',
      baseCost: 1e20,
      costScale: 1.25,
      effect: (level = 0, context = {}) => {
        const totalBuildings = getTotal(context);
        const baseAmount = 1e10 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const rawClickAdd = totalBuildings > 0 ? 0.1 * totalBuildings * level : 0;
        const clickAdd = rawClickAdd > 0 ? Math.max(1, Math.round(rawClickAdd)) : 0;
        const globalMult = level >= 300 ? 2 : 1;
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        if (globalMult > 1) {
          result.autoMult = globalMult;
          result.clickMult = globalMult;
        }
        return result;
      }
    },
    {
      id: 'cosmicArchitect',
      name: 'Architecte Cosmique',
      description: 'Réécrivez les plans du cosmos pour réduire les coûts.',
      effectSummary:
        'Production passive : +1 000 000 000 000 APS par niveau. Réduction de 1 % du coût futur par Architecte. Palier 150 : +20 % APC global.',
      category: 'hybrid',
      baseCost: 1e25,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e12 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const clickMult = level >= 150 ? 1.2 : 1;
        return clickMult > 1
          ? { autoAdd, clickMult }
          : { autoAdd };
      }
    },
    {
      id: 'parallelUniverse',
      name: 'Univers parallèle',
      description: 'Expérimentez des réalités alternatives à haut rendement.',
      effectSummary:
        'Production passive : +100 000 000 000 000 APS par niveau.',
      category: 'auto',
      baseCost: 1e30,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e14 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        return { autoAdd };
      }
    },
    {
      id: 'omniverseLibrary',
      name: 'Bibliothèque de l’Omnivers',
      description: 'Compilez le savoir infini pour booster toute production.',
      effectSummary:
        'Production passive : +10 000 000 000 000 000 APS par niveau. +2 % boost global par Univers parallèle. Palier 300 : Galaxies artificielles ×2.',
      category: 'hybrid',
      baseCost: 1e36,
      costScale: 1.25,
      effect: (level = 0, context = {}) => {
        const baseAmount = 1e16 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const parallelLevel = getLevel(context, 'parallelUniverse');
        const globalBoost = parallelLevel > 0 ? Math.pow(1.02, parallelLevel) : 1;
        if (globalBoost > 1) {
          return {
            autoAdd,
            autoMult: globalBoost,
            clickMult: globalBoost
          };
        }
        return { autoAdd };
      }
    },
    {
      id: 'quantumOverseer',
      name: 'Grand Ordonnateur Quantique',
      description: 'Ordonnez le multivers et atteignez la singularité.',
      effectSummary:
        'Production passive : +1 000 000 000 000 000 000 APS par niveau. Palier 100 : double définitivement tous les gains.',
      category: 'hybrid',
      baseCost: 1e42,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e18 * level;
        const rawAutoAdd = baseAmount;
        const autoAdd = level > 0 ? Math.max(baseAmount, Math.round(rawAutoAdd)) : 0;
        const globalMult = level >= 100 ? 2 : 1;
        return globalMult > 1
          ? { autoAdd, autoMult: globalMult, clickMult: globalMult }
          : { autoAdd };
      }
    }
  ].map(withDefaults);
})();

function renderGachaRarityList() {
  if (!elements.gachaRarityList) return;
  const weightsUpdated = refreshGachaRarities(new Date());
  if (weightsUpdated) {
    rebuildGachaPools();
  }
  elements.gachaRarityList.innerHTML = '';
  gachaRarityRows.clear();
  const totalWeight = getCurrentGachaTotalWeight();
  GACHA_RARITIES.forEach(def => {
    const item = document.createElement('li');
    item.className = 'gacha-rarity';
    item.dataset.rarityId = def.id;
    if (def.color) {
      item.style.setProperty('--rarity-color', def.color);
    }

    const header = document.createElement('div');
    header.className = 'gacha-rarity__header';

    const label = document.createElement('span');
    label.className = 'gacha-rarity__label';
    label.textContent = def.label || def.id;

    const chance = document.createElement('span');
    chance.className = 'gacha-rarity__chance';
    if (def.weight > 0 && totalWeight > 0) {
      const ratio = (def.weight / totalWeight) * 100;
      const digits = ratio >= 10 ? 1 : 2;
      chance.textContent = `${ratio.toFixed(digits).replace('.', ',')}\u00a0%`;
    } else {
      chance.textContent = '—';
    }

    header.append(label, chance);
    item.appendChild(header);

    if (def.description) {
      const description = document.createElement('p');
      description.className = 'gacha-rarity__description';
      description.textContent = def.description;
      item.appendChild(description);
    }

    const progress = document.createElement('div');
    progress.className = 'gacha-rarity__progress';
    progress.setAttribute('role', 'progressbar');
    progress.setAttribute('aria-valuemin', '0');
    progress.setAttribute('aria-valuemax', '0');
    progress.setAttribute('aria-valuenow', '0');

    const bar = document.createElement('div');
    bar.className = 'gacha-rarity__bar';
    progress.appendChild(bar);

    const summary = document.createElement('p');
    summary.className = 'gacha-rarity__summary';
    summary.textContent = 'Aucun élément';

    item.append(progress, summary);
    elements.gachaRarityList.appendChild(item);
    gachaRarityRows.set(def.id, { item, progress, bar, summary });
  });

  updateGachaRarityProgress();
}

function updateGachaRarityProgress() {
  if (!gachaRarityRows.size) return;
  const totals = new Map();
  gachaPools.forEach((ids, rarity) => {
    totals.set(rarity, { total: ids.length, owned: 0 });
  });

  const entries = Object.values(gameState.elements || {});
  entries.forEach(entry => {
    if (!entry) return;
    const rarityId = entry.rarity || elementRarityIndex.get(entry.id);
    if (!rarityId || !totals.has(rarityId)) return;
    const bucket = totals.get(rarityId);
    if (hasElementLifetime(entry)) {
      bucket.owned += 1;
    }
  });

  gachaRarityRows.forEach((row, rarityId) => {
    const data = totals.get(rarityId) || { total: 0, owned: 0 };
    const total = data.total;
    const owned = data.owned;
    row.progress.setAttribute('aria-valuemax', String(total));
    row.progress.setAttribute('aria-valuenow', String(owned));
    const percent = total > 0 ? (owned / total) * 100 : 0;
    row.bar.style.width = `${percent}%`;
    row.summary.textContent = total > 0
      ? `${owned} / ${total} éléments`
      : 'Aucun élément';
  });
}

const fusionCards = new Map();

function formatFusionChance(chance) {
  const ratio = Math.max(0, Math.min(1, Number(chance) || 0));
  const percent = ratio * 100;
  const digits = percent < 10 ? 1 : 0;
  return `${percent.toFixed(digits).replace('.', ',')} %`;
}

function getFusionStateById(fusionId) {
  if (!gameState.fusions || typeof gameState.fusions !== 'object') {
    gameState.fusions = createInitialFusionState();
  }
  if (!gameState.fusions[fusionId]) {
    gameState.fusions[fusionId] = { attempts: 0, successes: 0 };
  }
  const state = gameState.fusions[fusionId];
  const attempts = Number(state.attempts);
  const successes = Number(state.successes);
  state.attempts = Number.isFinite(attempts) && attempts > 0 ? Math.floor(attempts) : 0;
  state.successes = Number.isFinite(successes) && successes > 0 ? Math.floor(successes) : 0;
  return state;
}

function getFusionBonusState() {
  if (!gameState.fusionBonuses || typeof gameState.fusionBonuses !== 'object') {
    gameState.fusionBonuses = createInitialFusionBonuses();
  }
  const bonuses = gameState.fusionBonuses;
  const apc = Number(bonuses.apcFlat);
  const aps = Number(bonuses.apsFlat);
  bonuses.apcFlat = Number.isFinite(apc) ? apc : 0;
  bonuses.apsFlat = Number.isFinite(aps) ? aps : 0;
  return bonuses;
}

function setFusionLog(message, status = null) {
  if (!elements.fusionLog) return;
  elements.fusionLog.textContent = message;
  elements.fusionLog.classList.remove('fusion-log--success', 'fusion-log--failure');
  if (status === 'success') {
    elements.fusionLog.classList.add('fusion-log--success');
  } else if (status === 'failure') {
    elements.fusionLog.classList.add('fusion-log--failure');
  }
}

function canAttemptFusion(definition) {
  if (!definition) return false;
  if (!Array.isArray(definition.inputs) || !definition.inputs.length) {
    return false;
  }
  return definition.inputs.every(input => {
    const entry = gameState.elements?.[input.elementId];
    return getElementCurrentCount(entry) >= input.count;
  });
}

function applyFusionRewards(rewards) {
  if (!rewards || typeof rewards !== 'object') {
    return [];
  }
  const bonuses = getFusionBonusState();
  const summaries = [];
  const apcIncrement = Number(rewards.apcFlat);
  if (Number.isFinite(apcIncrement) && apcIncrement !== 0) {
    bonuses.apcFlat += apcIncrement;
    const formatted = apcIncrement.toLocaleString('fr-FR');
    summaries.push(`+${formatted} APC`);
  }
  const apsIncrement = Number(rewards.apsFlat);
  if (Number.isFinite(apsIncrement) && apsIncrement !== 0) {
    bonuses.apsFlat += apsIncrement;
    const formatted = apsIncrement.toLocaleString('fr-FR');
    summaries.push(`+${formatted} APS`);
  }
  return summaries;
}

function buildFusionCard(definition) {
  const card = document.createElement('article');
  card.className = 'fusion-card';
  card.dataset.fusionId = definition.id;
  card.setAttribute('role', 'listitem');

  const header = document.createElement('div');
  header.className = 'fusion-card__header';

  const title = document.createElement('h3');
  title.className = 'fusion-card__title';
  title.textContent = definition.name;

  const chance = document.createElement('p');
  chance.className = 'fusion-card__chance';
  chance.textContent = `Chance de réussite : ${formatFusionChance(definition.successChance)}`;

  header.append(title, chance);

  const bodyFragment = document.createDocumentFragment();
  bodyFragment.appendChild(header);

  if (definition.description) {
    const description = document.createElement('p');
    description.className = 'fusion-card__description';
    description.textContent = definition.description;
    bodyFragment.appendChild(description);
  }

  const requirementList = document.createElement('ul');
  requirementList.className = 'fusion-card__requirements';

  const requirements = definition.inputs.map(input => {
    const item = document.createElement('li');
    item.className = 'fusion-requirement';

    const symbol = document.createElement('span');
    symbol.className = 'fusion-requirement__symbol';
    symbol.textContent = input.elementDef?.symbol ?? input.elementId;

    const name = document.createElement('span');
    name.className = 'fusion-requirement__name';
    name.textContent = input.elementDef?.name ?? input.elementId;

    const count = document.createElement('span');
    count.className = 'fusion-requirement__count';
    count.textContent = `×${input.count}`;

    const availability = document.createElement('span');
    availability.className = 'fusion-requirement__availability';
    availability.textContent = 'Disponible : 0';

    item.append(symbol, name, count, availability);
    requirementList.appendChild(item);
    return {
      elementId: input.elementId,
      requiredCount: input.count,
      availabilityLabel: availability
    };
  });

  bodyFragment.appendChild(requirementList);

  const actions = document.createElement('div');
  actions.className = 'fusion-card__actions';

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'fusion-card__button';
  button.textContent = 'Tenter la fusion';
  button.setAttribute('aria-label', `Tenter la fusion ${definition.name}`);
  button.addEventListener('click', () => {
    handleFusionAttempt(definition.id);
  });

  const status = document.createElement('span');
  status.className = 'fusion-card__feedback fusion-card__status';
  status.textContent = 'Vérification des ingrédients…';

  actions.append(button, status);

  const stats = document.createElement('p');
  stats.className = 'fusion-card__stats';
  stats.textContent = 'Tentatives : 0 · Succès : 0';

  const bonusParts = [];
  if (definition.rewards.apcFlat) {
    bonusParts.push(`+${definition.rewards.apcFlat.toLocaleString('fr-FR')} APC`);
  }
  if (definition.rewards.apsFlat) {
    bonusParts.push(`+${definition.rewards.apsFlat.toLocaleString('fr-FR')} APS`);
  }
  const bonus = document.createElement('p');
  bonus.className = 'fusion-card__bonus';
  bonus.textContent = bonusParts.length
    ? `Bonus par réussite : ${bonusParts.join(' · ')}`
    : 'Aucun bonus défini';

  const totalBonus = document.createElement('p');
  totalBonus.className = 'fusion-card__feedback fusion-card__total';
  totalBonus.textContent = 'Bonus cumulé : —';

  card.append(bodyFragment, actions, stats, bonus, totalBonus);

  return {
    root: card,
    button,
    requirements,
    stats,
    status,
    bonus,
    totalBonus
  };
}

function renderFusionList() {
  if (!elements.fusionList) return;
  fusionCards.clear();
  elements.fusionList.innerHTML = '';
  if (!FUSION_DEFS.length) {
    const empty = document.createElement('p');
    empty.className = 'fusion-empty';
    empty.textContent = 'Aucune fusion disponible pour le moment.';
    empty.setAttribute('role', 'listitem');
    elements.fusionList.appendChild(empty);
    return;
  }
  const fragment = document.createDocumentFragment();
  FUSION_DEFS.forEach(def => {
    const card = buildFusionCard(def);
    fragment.appendChild(card.root);
    fusionCards.set(def.id, card);
  });
  elements.fusionList.appendChild(fragment);
  if (elements.fusionLog && !elements.fusionLog.textContent.trim()) {
    setFusionLog('Sélectionnez une recette pour tenter votre première fusion.');
  }
  updateFusionUI();
}

function updateFusionUI() {
  if (!elements.fusionList || !FUSION_DEFS.length) {
    return;
  }
  FUSION_DEFS.forEach(def => {
    const card = fusionCards.get(def.id);
    if (!card) {
      return;
    }
    const state = getFusionStateById(def.id);
    card.stats.textContent = `Tentatives : ${state.attempts} · Succès : ${state.successes}`;
    let canAttempt = true;
    card.requirements.forEach(requirement => {
      const entry = gameState.elements?.[requirement.elementId];
      const available = getElementCurrentCount(entry);
      const availableText = available.toLocaleString('fr-FR');
      const requiredText = requirement.requiredCount.toLocaleString('fr-FR');
      requirement.availabilityLabel.textContent = `Disponible : ${availableText} / ${requiredText}`;
      if (available < requirement.requiredCount) {
        canAttempt = false;
      }
    });
    card.button.disabled = !canAttempt;
    card.button.setAttribute('aria-disabled', canAttempt ? 'false' : 'true');
    card.status.textContent = canAttempt
      ? 'Ingrédients disponibles'
      : 'Ressources insuffisantes';
    const totalParts = [];
    if (def.rewards.apcFlat) {
      const totalApc = def.rewards.apcFlat * state.successes;
      totalParts.push(`+${totalApc.toLocaleString('fr-FR')} APC cumulés`);
    }
    if (def.rewards.apsFlat) {
      const totalAps = def.rewards.apsFlat * state.successes;
      totalParts.push(`+${totalAps.toLocaleString('fr-FR')} APS cumulés`);
    }
    card.totalBonus.textContent = `Bonus cumulé : ${totalParts.length ? totalParts.join(' · ') : '—'}`;
  });
}

function handleFusionAttempt(fusionId) {
  const definition = FUSION_DEFINITION_MAP.get(fusionId);
  if (!definition) {
    return;
  }
  if (!canAttemptFusion(definition)) {
    setFusionLog('Vous n’avez pas assez de ressources pour cette fusion.', 'failure');
    showToast('Ressources insuffisantes pour cette fusion.');
    return;
  }

  definition.inputs.forEach(input => {
    const entry = gameState.elements?.[input.elementId];
    if (!entry) {
      return;
    }
    const current = getElementCurrentCount(entry);
    const nextCount = Math.max(0, current - input.count);
    entry.count = nextCount;
  });

  const state = getFusionStateById(definition.id);
  state.attempts += 1;

  const success = Math.random() < definition.successChance;
  let rewardSummary = [];
  if (success) {
    state.successes += 1;
    rewardSummary = applyFusionRewards(definition.rewards);
  }

  recalcProduction();
  updateUI();
  evaluateTrophies();
  saveGame();

  if (success) {
    const rewardText = rewardSummary.length ? rewardSummary.join(' · ') : 'Aucun bonus.';
    setFusionLog(`Fusion ${definition.name} réussie ! Bonus obtenu : ${rewardText}`, 'success');
    showToast('Fusion réussie !');
  } else {
    setFusionLog(`La fusion ${definition.name} a échoué. Les éléments ont été consommés.`, 'failure');
    showToast('Fusion échouée.');
  }
}

function pickGachaRarity() {
  const available = GACHA_RARITIES.filter(def => {
    const pool = gachaPools.get(def.id);
    return Array.isArray(pool) && pool.length > 0;
  });
  if (!available.length) {
    return null;
  }
  const totalWeight = available.reduce((sum, def) => sum + (def.weight || 0), 0);
  if (totalWeight <= 0) {
    const index = Math.floor(Math.random() * available.length);
    return available[index];
  }
  let roll = Math.random() * totalWeight;
  for (const def of available) {
    const weight = Math.max(0, def.weight || 0);
    if (weight <= 0) {
      continue;
    }
    if (roll < weight) {
      return def;
    }
    roll -= weight;
  }
  return available[available.length - 1];
}

function pickRandomElementFromRarity(rarityId) {
  const pool = gachaPools.get(rarityId);
  if (!pool || !pool.length) {
    return null;
  }
  const index = Math.floor(Math.random() * pool.length);
  const elementId = pool[index];
  return periodicElementIndex.get(elementId) || null;
}

function renderGachaResult(outcome) {
  if (!elements.gachaResult) return;
  const container = elements.gachaResult;
  container.innerHTML = '';
  container.style.removeProperty('--rarity-color');

  if (!outcome || !Array.isArray(outcome.focus) || !outcome.focus.length) {
    container.textContent = 'Synthèse indisponible pour le moment.';
    return;
  }

  const grid = document.createElement('div');
  grid.className = 'gacha-result__grid';
  grid.setAttribute('role', 'list');

  const newCount = Number(outcome.newCount) || 0;

  outcome.focus.forEach(entry => {
    if (!entry?.elementDef) return;
    const card = document.createElement('article');
    card.className = 'gacha-result-card';
    card.setAttribute('role', 'listitem');
    if (entry.isNew) {
      card.classList.add('is-new');
    } else {
      card.classList.add('is-duplicate');
    }
    if (entry.rarity?.color) {
      card.style.setProperty('--rarity-color', entry.rarity.color);
    }

    const rarity = document.createElement('span');
    rarity.className = 'gacha-result-card__rarity';
    rarity.textContent = entry.rarity?.label || entry.rarity?.id || 'Rareté inconnue';

    const name = document.createElement('span');
    name.className = 'gacha-result-card__name';
    const baseName = entry.elementDef.name || entry.elementDef.symbol || 'Élément inconnu';
    const symbol = entry.elementDef.symbol && entry.elementDef.name ? ` (${entry.elementDef.symbol})` : '';
    name.textContent = `${baseName}${symbol}`;

    const status = document.createElement('span');
    status.className = 'gacha-result-card__status';
    status.textContent = entry.isNew ? 'NOUVEAU !' : 'Déjà possédé';

    if (entry.count > 1) {
      const count = document.createElement('span');
      count.className = 'gacha-result-card__count';
      count.textContent = `x${entry.count}`;
      card.appendChild(count);
    }

    card.appendChild(rarity);
    card.appendChild(name);
    card.appendChild(status);

    grid.appendChild(card);
  });

  if (newCount > 8 && grid.children.length >= 9) {
    grid.classList.add('gacha-result__grid--wide');
  }

  if (!grid.children.length) {
    container.textContent = 'Synthèse indisponible pour le moment.';
    return;
  }

  container.appendChild(grid);

  const drawCount = Math.max(1, Math.floor(Number(outcome.drawCount) || 0));
  const summary = document.createElement('p');
  summary.className = 'gacha-result__summary';
  const drawLabel = drawCount > 1 ? `Tirage x${drawCount}` : 'Tirage x1';
  const duplicateCount = Number(outcome.duplicateCount) || 0;
  const summaryParts = [drawLabel];
  if (newCount > 0) {
    const newLabel = newCount === 1
      ? '1 nouvel élément'
      : `${newCount} nouveaux éléments`;
    summaryParts.push(newLabel);
  }
  if (duplicateCount > 0) {
    const duplicateLabel = duplicateCount === 1 ? '1 doublon' : `${duplicateCount} doublons`;
    summaryParts.push(duplicateLabel);
  }
  summary.textContent = summaryParts.join(' · ');
  container.appendChild(summary);
}

function formatTicketLabel(count) {
  const numeric = Math.max(0, Math.floor(Number(count) || 0));
  const formatted = numeric.toLocaleString('fr-FR');
  const unit = numeric === 1 ? 'ticket' : 'tickets';
  return `${formatted} ${unit}`;
}

function formatBonusTicketLabel(count) {
  const numeric = Math.max(0, Math.floor(Number(count) || 0));
  const formatted = numeric.toLocaleString('fr-FR');
  const unit = numeric === 1 ? 'ticket Mach3' : 'tickets Mach3';
  return `${formatted} ${unit}`;
}

function getGachaRarityRank(rarity) {
  if (!rarity) return -1;
  const id = typeof rarity === 'string' ? rarity : rarity.id;
  if (!id) return -1;
  const rank = GACHA_RARITY_ORDER.has(id) ? GACHA_RARITY_ORDER.get(id) : -1;
  return Number.isFinite(rank) ? rank : -1;
}

function compareGachaRaritiesDesc(a, b) {
  const rankA = getGachaRarityRank(a);
  const rankB = getGachaRarityRank(b);
  if (rankA === rankB) return 0;
  return rankB - rankA;
}

function compareGachaEntries(a, b) {
  const rarityDiff = compareGachaRaritiesDesc(a?.rarity, b?.rarity);
  if (rarityDiff !== 0) {
    return rarityDiff;
  }
  const nameA = a?.elementDef?.name || '';
  const nameB = b?.elementDef?.name || '';
  if (nameA && nameB) {
    return nameA.localeCompare(nameB, 'fr', { sensitivity: 'base' });
  }
  const idA = a?.elementDef?.id || '';
  const idB = b?.elementDef?.id || '';
  return idA.localeCompare(idB, 'fr', { sensitivity: 'base' });
}

function buildGachaDisplayData(results) {
  const aggregatedMap = new Map();
  const rarityColorMap = new Map();

  results.forEach(result => {
    if (!result) return;
    const { elementDef, rarity, isNew } = result;
    if (rarity?.id && !rarityColorMap.has(rarity.id)) {
      rarityColorMap.set(rarity.id, rarity.color || null);
    }
    if (!elementDef?.id) {
      return;
    }
    let entry = aggregatedMap.get(elementDef.id);
    if (!entry) {
      entry = {
        elementDef,
        rarity,
        count: 0,
        isNew: false
      };
      aggregatedMap.set(elementDef.id, entry);
    }
    entry.count += 1;
    if (isNew) {
      entry.isNew = true;
    }
    if (!entry.rarity && rarity) {
      entry.rarity = rarity;
    }
  });

  const aggregated = Array.from(aggregatedMap.values()).sort(compareGachaEntries);
  const newEntries = aggregated.filter(entry => entry.isNew);
  const duplicateEntries = aggregated.filter(entry => !entry.isNew);

  let focus = newEntries.slice();
  if (!focus.length && duplicateEntries.length) {
    focus = duplicateEntries.slice(0, Math.min(3, duplicateEntries.length));
  }
  if (!focus.length && aggregated.length) {
    focus = aggregated.slice(0, Math.min(3, aggregated.length));
  }

  return { aggregated, focus, newEntries, rarityColorMap };
}

const GACHA_ANIMATION_CONFETTI_COUNT = 150;
const GACHA_ANIMATION_REVEAL_DELAY = 2500;
const GACHA_CONFETTI_BASE_RARITY_ID = 'commun';
const DEFAULT_GACHA_CONFETTI_COLOR = '#4f7ec2';
const DEFAULT_GACHA_CONFETTI_RGB = { r: 79, g: 126, b: 194 };
let gachaAnimationInProgress = false;
let gachaRollMode = 1;

function updateGachaUI() {
  updateGachaFeaturedInfo();
  const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  if (elements.gachaTicketValue) {
    elements.gachaTicketValue.textContent = formatTicketLabel(available);
  } else if (elements.gachaTicketCounter) {
    elements.gachaTicketCounter.textContent = formatTicketLabel(available);
  }
  updateArcadeTicketDisplay();
  if (elements.gachaTicketModeLabel) {
    elements.gachaTicketModeLabel.textContent = `Tirage x${gachaRollMode}`;
  }
  if (elements.gachaTicketModeButton) {
    const modeLabel = `Tirage x${gachaRollMode}`;
    elements.gachaTicketModeButton.setAttribute('aria-label', `Basculer le mode de tirage (actuel\u00a0: ${modeLabel})`);
    elements.gachaTicketModeButton.title = `Mode actuel\u00a0: ${modeLabel}`;
  }
  if (elements.gachaSunButton) {
    const gachaFree = isDevKitGachaFree();
    const totalCost = gachaRollMode * GACHA_TICKET_COST;
    const affordable = gachaFree || available >= totalCost;
    const busy = gachaAnimationInProgress;
    const costLabel = gachaFree ? 'Gratuit' : formatTicketLabel(totalCost);
    const drawLabel = gachaRollMode > 1 ? `tirage cosmique x${gachaRollMode}` : 'tirage cosmique';
    let label;
    if (busy) {
      label = 'Tirage cosmique en cours';
    } else if (gachaFree) {
      label = `Déclencher un ${drawLabel} (gratuit)`;
    } else if (affordable) {
      label = `Déclencher un ${drawLabel}`;
    } else {
      label = `Tickets insuffisants pour un ${drawLabel}`;
    }
    elements.gachaSunButton.classList.toggle('is-locked', !affordable || busy);
    elements.gachaSunButton.setAttribute('aria-disabled', !affordable || busy ? 'true' : 'false');
    elements.gachaSunButton.setAttribute('aria-label', label);
    elements.gachaSunButton.title = gachaFree ? label : `${label} (${costLabel})`;
    if (busy) {
      elements.gachaSunButton.disabled = true;
    } else if (elements.gachaSunButton.disabled) {
      elements.gachaSunButton.disabled = false;
    }
  }
}


let particulesBrickSkinPreference = null;
let particulesGame = null;

function normalizeParticulesBrickSkin(value) {
  if (value == null) {
    return null;
  }
  const normalized = String(value).trim().toLowerCase();
  if (!normalized || normalized === 'original' || normalized === 'default') {
    return null;
  }
  if (normalized === 'metallic' || normalized === 'neon') {
    return normalized;
  }
  return null;
}

function setParticulesBrickSkinPreference(value) {
  particulesBrickSkinPreference = normalizeParticulesBrickSkin(value);
  if (particulesGame && typeof particulesGame.setBrickSkin === 'function') {
    particulesGame.setBrickSkin(particulesBrickSkinPreference);
  }
}

function initParticulesGame() {
  if (particulesGame || !elements.arcadeCanvas || typeof ParticulesGame !== 'function') {
    return;
  }
  particulesGame = new ParticulesGame({
    canvas: elements.arcadeCanvas,
    particleLayer: elements.arcadeParticleLayer,
    overlay: elements.arcadeOverlay,
    overlayButton: elements.arcadeOverlayButton,
    overlayMessage: elements.arcadeOverlayMessage,
    levelLabel: elements.arcadeLevelValue,
    livesLabel: elements.arcadeLivesValue,
    scoreLabel: elements.arcadeScoreValue,
    comboLabel: elements.arcadeComboMessage,
    brickSkin: particulesBrickSkinPreference,
    formatTicketLabel,
    formatBonusTicketLabel,
    onTicketsEarned: (count = 0) => {
      const reward = Math.max(0, Math.floor(Number(count) || 0));
      if (reward <= 0) {
        return;
      }
      const gained = gainGachaTickets(reward, { unlockTicketStar: true });
      saveGame();
      const rewardLabel = formatTicketLabel(gained);
      showToast(`+${rewardLabel} grâce à Particules !`);
    },
    onSpecialTicket: (count = 0) => {
      const reward = Math.max(0, Math.floor(Number(count) || 0));
      if (reward <= 0) {
        return;
      }
      const gained = gainBonusParticulesTickets(reward);
      saveGame();
      const label = formatBonusTicketLabel(gained);
      showToast(`+${label} !`);
    }
  });
  updateArcadeTicketDisplay();
}

function performGachaRoll(count = 1) {
  const weightsUpdated = refreshGachaRarities(new Date());
  if (weightsUpdated) {
    rebuildGachaPools();
    if (elements.gachaRarityList) {
      renderGachaRarityList();
    }
  }
  const drawCount = Math.max(1, Math.floor(Number(count) || 1));
  const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  const gachaFree = isDevKitGachaFree();
  const totalCost = drawCount * GACHA_TICKET_COST;

  if (!gachaFree && available < totalCost) {
    showToast(`Pas assez de tickets de tirage (nécessaire\u00a0: ${formatTicketLabel(totalCost)}).`);
    return null;
  }

  const hasAvailableElements = GACHA_RARITIES.some(def => {
    const pool = gachaPools.get(def.id);
    return Array.isArray(pool) && pool.length > 0;
  });
  if (!hasAvailableElements) {
    showToast('Aucun élément disponible dans les chambres de synthèse.');
    return null;
  }

  if (!gachaFree) {
    gameState.gachaTickets = available - totalCost;
  }

  const results = [];
  for (let rollIndex = 0; rollIndex < drawCount; rollIndex += 1) {
    const rarity = pickGachaRarity();
    if (!rarity) {
      showToast('Aucun élément disponible dans les chambres de synthèse.');
      break;
    }

    const elementDef = pickRandomElementFromRarity(rarity.id);
    if (!elementDef) {
      showToast('Flux instable, impossible de matérialiser un élément.');
      continue;
    }

    let entry = gameState.elements[elementDef.id];
    if (!entry) {
      entry = {
        id: elementDef.id,
        gachaId: elementDef.gachaId ?? elementDef.id,
        owned: false,
        count: 0,
        lifetime: 0,
        rarity: rarity.id,
        effects: [],
        bonuses: []
      };
      gameState.elements[elementDef.id] = entry;
    }

    const previousCount = getElementCurrentCount(entry);
    const previousLifetime = getElementLifetimeCount(entry);
    entry.count = previousCount + 1;
    entry.lifetime = previousLifetime + 1;

    if (!entry.rarity) {
      entry.rarity = rarity.id;
    }
    entry.owned = entry.lifetime > 0;

    const isNew = previousLifetime === 0;
    results.push({ rarity, elementDef, isNew });
  }

  if (!results.length) {
    if (!gachaFree) {
      gameState.gachaTickets = available;
      updateGachaUI();
    }
    return null;
  }

  recalcProduction();
  evaluatePageUnlocks({ save: false });
  updateUI();
  saveGame();
  evaluateTrophies();

  const { aggregated, focus, newEntries, rarityColorMap } = buildGachaDisplayData(results);
  const confettiColors = [];
  rarityColorMap.forEach(color => {
    if (color != null) {
      confettiColors.push(color);
    }
  });
  const newCount = results.reduce((sum, result) => sum + (result.isNew ? 1 : 0), 0);
  const duplicateCount = Math.max(0, results.length - newCount);

  return {
    drawCount: results.length,
    results,
    aggregated,
    focus,
    newEntries,
    confettiColors,
    newCount,
    duplicateCount
  };
}

function displayGachaResult(outcome) {
  if (!outcome) return;
  renderGachaResult(outcome);
}

function getGachaToastMessage(outcome) {
  if (!outcome) return '';
  const results = Array.isArray(outcome.results) ? outcome.results : [];
  if (!results.length) {
    return '';
  }

  if (results.length === 1) {
    const single = results[0];
    if (!single?.elementDef) return '';
    return single.isNew
      ? `Nouvel élément obtenu : ${single.elementDef.name} !`
      : `${single.elementDef.name} rejoint à nouveau votre collection.`;
  }

  const newCount = Number.isFinite(Number(outcome.newCount))
    ? Number(outcome.newCount)
    : results.reduce((sum, result) => sum + (result.isNew ? 1 : 0), 0);

  if (newCount > 1) {
    return `${newCount} nouveaux éléments découverts !`;
  }

  if (newCount === 1) {
    const focusNew = Array.isArray(outcome.focus)
      ? outcome.focus.find(entry => entry?.isNew && entry.elementDef)
      : null;
    const rawNew = results.find(result => result.isNew && result.elementDef);
    const target = focusNew || rawNew;
    if (target?.elementDef) {
      return `Nouvel élément obtenu : ${target.elementDef.name} !`;
    }
  }

  const focusEntry = Array.isArray(outcome.focus) && outcome.focus.length
    ? outcome.focus[0]
    : null;
  if (focusEntry?.elementDef) {
    const rarityLabel = focusEntry.rarity?.label || focusEntry.rarity?.id;
    if (rarityLabel) {
      return `${focusEntry.elementDef.name} (${rarityLabel}) rejoint à nouveau votre collection.`;
    }
    return `${focusEntry.elementDef.name} rejoint à nouveau votre collection.`;
  }

  const fallback = results[0];
  if (fallback?.elementDef) {
    return `${fallback.elementDef.name} rejoint à nouveau votre collection.`;
  }

  return '';
}

function wait(duration) {
  return new Promise(resolve => {
    setTimeout(resolve, Math.max(0, Number(duration) || 0));
  });
}

const gachaConfettiState = {
  canvas: null,
  ctx: null,
  particles: [],
  animationFrameId: null,
  handleResize: null,
  resizeObserver: null,
  dpr: 1,
  width: 0,
  height: 0,
  centerX: 0,
  centerY: 0,
  baseColorRgb: DEFAULT_GACHA_CONFETTI_RGB
};

function normalizeHexColor(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  const match = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(trimmed);
  if (!match) return null;
  let hex = match[1];
  if (hex.length === 3) {
    hex = hex.split('').map(part => part + part).join('');
  }
  return `#${hex.toLowerCase()}`;
}

function parseHexColorToRgb(input) {
  const normalized = normalizeHexColor(input);
  if (!normalized) return null;
  const value = normalized.slice(1);
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) {
    return null;
  }
  return { r, g, b };
}

function mixRgb(color, target, amount) {
  const mixAmount = Math.max(0, Math.min(1, Number(amount) || 0));
  const mixChannel = (base, goal) => {
    const value = base + (goal - base) * mixAmount;
    return Math.max(0, Math.min(255, Math.round(value)));
  };
  return {
    r: mixChannel(color.r, target.r),
    g: mixChannel(color.g, target.g),
    b: mixChannel(color.b, target.b)
  };
}

function lightenRgb(color, amount) {
  return mixRgb(color, { r: 255, g: 255, b: 255 }, amount);
}

function darkenRgb(color, amount) {
  return mixRgb(color, { r: 0, g: 0, b: 0 }, amount);
}

function rgbToHex(color) {
  if (!color || typeof color.r !== 'number' || typeof color.g !== 'number' || typeof color.b !== 'number') {
    return null;
  }
  const clampChannel = value => {
    return Math.max(0, Math.min(255, Math.round(value)));
  };
  const toHex = value => clampChannel(value).toString(16).padStart(2, '0');
  return `#${toHex(color.r)}${toHex(color.g)}${toHex(color.b)}`;
}

function getPeriodicCellCollectionColor(baseColor, isOwned) {
  const normalized = normalizeHexColor(baseColor);
  if (!normalized) {
    return null;
  }
  if (isOwned) {
    return normalized;
  }
  const rgb = parseHexColorToRgb(normalized);
  if (!rgb) {
    return normalized;
  }
  const darkened = darkenRgb(rgb, 0.4);
  return rgbToHex(darkened) || normalized;
}

function applyPeriodicCellCollectionColor(cell, isOwned) {
  if (!cell) return;
  const baseColor = cell.dataset.rarityColor;
  const resolved = getPeriodicCellCollectionColor(baseColor, isOwned);
  if (resolved) {
    cell.style.setProperty('--rarity-color', resolved);
  } else {
    cell.style.removeProperty('--rarity-color');
  }
}

function resolveGachaConfettiColor(input, fallback = DEFAULT_GACHA_CONFETTI_COLOR) {
  return normalizeHexColor(input) || normalizeHexColor(fallback) || DEFAULT_GACHA_CONFETTI_COLOR;
}

function clamp01(value) {
  if (!Number.isFinite(value)) return 0;
  if (value <= 0) return 0;
  if (value >= 1) return 1;
  return value;
}

function lerpRgbColor(from, to, t) {
  const amount = clamp01(t);
  const mixChannel = (start, end) => {
    const value = start + (end - start) * amount;
    return Math.max(0, Math.min(255, Math.round(value)));
  };
  return {
    r: mixChannel(from.r, to.r),
    g: mixChannel(from.g, to.g),
    b: mixChannel(from.b, to.b)
  };
}

function getNow() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

function ensureGachaConfettiCanvas() {
  const canvas = elements.gachaAnimationConfetti;
  if (!canvas || typeof canvas.getContext !== 'function') {
    return false;
  }
  const context = canvas.getContext('2d');
  if (!context) {
    return false;
  }
  if (gachaConfettiState.canvas !== canvas) {
    gachaConfettiState.canvas = canvas;
    gachaConfettiState.ctx = context;
  }
  if (typeof window !== 'undefined') {
    if (!gachaConfettiState.handleResize) {
      gachaConfettiState.handleResize = () => updateGachaConfettiCanvasSize();
      window.addEventListener('resize', gachaConfettiState.handleResize);
    }
    if (!gachaConfettiState.resizeObserver && typeof ResizeObserver !== 'undefined') {
      gachaConfettiState.resizeObserver = new ResizeObserver(() => {
        updateGachaConfettiCanvasSize();
      });
      gachaConfettiState.resizeObserver.observe(canvas);
    }
  }
  updateGachaConfettiCanvasSize();
  return true;
}

function updateGachaConfettiCanvasSize() {
  const { canvas, ctx } = gachaConfettiState;
  if (!canvas || !ctx) {
    return;
  }
  const rect = canvas.getBoundingClientRect();
  const width = Math.max(1, rect.width || canvas.offsetWidth || 1);
  const height = Math.max(1, rect.height || canvas.offsetHeight || 1);
  const dpr = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
    ? window.devicePixelRatio
    : 1;
  canvas.width = Math.max(1, Math.round(width * dpr));
  canvas.height = Math.max(1, Math.round(height * dpr));
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  gachaConfettiState.dpr = dpr;
  gachaConfettiState.width = width;
  gachaConfettiState.height = height;
  gachaConfettiState.centerX = width / 2;
  gachaConfettiState.centerY = height / 2;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
}

function createGachaConfettiParticle(finalColorRgb, birthTime) {
  const now = Number.isFinite(birthTime) ? birthTime : getNow();
  return {
    birth: now,
    life: 2600 + Math.random() * 1400,
    angle: Math.random() * Math.PI * 2,
    speed: 1.5 + Math.random() * 3.5,
    size: 3 + Math.random() * 4,
    spiralFactor: 0.45 + Math.random() * 0.55,
    wobbleAmplitude: 8 + Math.random() * 24,
    wobbleFrequency: 1.5 + Math.random() * 2.5,
    spinPhase: Math.random() * Math.PI * 2,
    drift: (Math.random() - 0.5) * 0.3,
    finalColor: finalColorRgb
  };
}

function easeOutCubic(t) {
  const value = 1 - clamp01(t);
  return 1 - value * value * value;
}

function drawGachaConfettiParticle(particle, now) {
  const { ctx, centerX, centerY, baseColorRgb } = gachaConfettiState;
  if (!ctx) {
    return false;
  }
  const age = now - particle.birth;
  if (age <= 0) {
    return true;
  }
  if (age >= particle.life) {
    return false;
  }
  const progress = clamp01(age / particle.life);
  const eased = easeOutCubic(progress);
  const radius = particle.speed * age * 0.03;
  const angle = particle.angle + eased * Math.PI * 6 * particle.spiralFactor;
  const baseX = centerX + Math.cos(angle) * (radius + 6);
  const baseY = centerY + Math.sin(angle) * (radius + 6) + age * particle.drift * 0.08;
  const wobble = Math.sin(progress * Math.PI * particle.wobbleFrequency + particle.spinPhase) * particle.wobbleAmplitude;
  const x = baseX + Math.cos(angle) * wobble * 0.2;
  const y = baseY + wobble;
  const color = lerpRgbColor(baseColorRgb, particle.finalColor, eased);
  const alpha = Math.pow(1 - progress, 1.25);
  ctx.beginPath();
  ctx.fillStyle = `rgba(${color.r}, ${color.g}, ${color.b}, ${(alpha * 0.9).toFixed(3)})`;
  ctx.arc(x, y, particle.size, 0, Math.PI * 2);
  ctx.fill();
  return true;
}

function renderGachaConfettiFrame(now) {
  const state = gachaConfettiState;
  if (!state.canvas || !state.ctx) {
    state.animationFrameId = null;
    state.particles = [];
    return;
  }
  const timestamp = Number.isFinite(now) ? now : getNow();
  const { ctx, canvas, dpr } = state;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.globalCompositeOperation = 'lighter';
  state.particles = state.particles.filter(particle => drawGachaConfettiParticle(particle, timestamp));
  ctx.globalCompositeOperation = 'source-over';
  if (state.particles.length && typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    state.animationFrameId = window.requestAnimationFrame(renderGachaConfettiFrame);
  } else {
    state.animationFrameId = null;
  }
}

function stopGachaConfettiAnimation() {
  const state = gachaConfettiState;
  if (typeof window !== 'undefined' && typeof window.cancelAnimationFrame === 'function' && state.animationFrameId != null) {
    window.cancelAnimationFrame(state.animationFrameId);
  }
  state.animationFrameId = null;
  state.particles = [];
  const { ctx, canvas, dpr } = state;
  if (ctx && canvas) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.setTransform(dpr || 1, 0, 0, dpr || 1, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
  }
}

function startGachaConfettiAnimation(outcome) {
  if (!ensureGachaConfettiCanvas()) {
    stopGachaConfettiAnimation();
    return;
  }
  stopGachaConfettiAnimation();

  const baseRarityColor = resolveGachaConfettiColor(
    GACHA_RARITY_MAP.get(GACHA_CONFETTI_BASE_RARITY_ID)?.color,
    DEFAULT_GACHA_CONFETTI_COLOR
  );
  const baseColor = baseRarityColor || DEFAULT_GACHA_CONFETTI_COLOR;
  const baseColorRgb = parseHexColorToRgb(baseColor) || DEFAULT_GACHA_CONFETTI_RGB;

  const paletteSet = new Set();
  if (Array.isArray(outcome?.confettiColors)) {
    outcome.confettiColors.forEach(color => {
      const normalized = resolveGachaConfettiColor(color, baseColor);
      if (normalized) {
        paletteSet.add(normalized);
      }
    });
  }

  if (!paletteSet.size && Array.isArray(outcome?.focus)) {
    outcome.focus.forEach(entry => {
      if (!entry?.rarity?.color) return;
      const normalized = resolveGachaConfettiColor(entry.rarity.color, baseColor);
      if (normalized) {
        paletteSet.add(normalized);
      }
    });
  }

  if (!paletteSet.size) {
    paletteSet.add(baseColor);
  }

  const paletteRgb = Array.from(paletteSet)
    .map(color => parseHexColorToRgb(color) || baseColorRgb)
    .filter(Boolean);
  if (!paletteRgb.length) {
    paletteRgb.push(baseColorRgb);
  }

  gachaConfettiState.baseColorRgb = baseColorRgb;

  let measureAttempts = 0;
  const maxMeasureAttempts = 4;

  const beginAnimation = () => {
    updateGachaConfettiCanvasSize();
    const { width, height } = gachaConfettiState;
    const hasValidSize = width > 2 && height > 2;
    if (!hasValidSize && measureAttempts < maxMeasureAttempts
      && typeof window !== 'undefined'
      && typeof window.requestAnimationFrame === 'function') {
      measureAttempts += 1;
      window.requestAnimationFrame(beginAnimation);
      return;
    }

    const now = getNow();
    const particles = [];
    const paletteLength = paletteRgb.length;
    for (let i = 0; i < GACHA_ANIMATION_CONFETTI_COUNT; i += 1) {
      const color = paletteRgb[paletteLength > 0 ? i % paletteLength : 0] || baseColorRgb;
      particles.push(createGachaConfettiParticle(color, now));
    }
    gachaConfettiState.particles = particles;

    if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
      gachaConfettiState.animationFrameId = window.requestAnimationFrame(renderGachaConfettiFrame);
    } else {
      renderGachaConfettiFrame(now);
    }
  };

  beginAnimation();
}

function waitForGachaAnimationDismiss(layer, options = {}) {
  const { ignoreClicksUntil = 0 } = options;
  return new Promise(resolve => {
    let inputGuardTime = Number(ignoreClicksUntil) || 0;
    const cleanup = () => {
      layer.removeEventListener('pointerdown', handlePointerDown);
      layer.removeEventListener('click', handleClick, true);
      layer.removeEventListener('keydown', handleKeyDown);
      resolve();
    };
    const handlePointerDown = event => {
      const timeStamp = typeof event.timeStamp === 'number' ? event.timeStamp : 0;
      if (inputGuardTime && timeStamp <= inputGuardTime) {
        inputGuardTime = 0;
        return;
      }
      if ('isPrimary' in event && event.isPrimary === false) return;
      if (typeof event.button === 'number' && event.button > 0) return;
      event.preventDefault();
      event.stopPropagation();
      cleanup();
    };
    const handleClick = event => {
      const timeStamp = typeof event.timeStamp === 'number' ? event.timeStamp : 0;
      if (inputGuardTime && timeStamp <= inputGuardTime) {
        inputGuardTime = 0;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      cleanup();
    };
    const handleKeyDown = event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        cleanup();
      } else if (event.key === 'Escape') {
        cleanup();
      }
    };
    requestAnimationFrame(() => {
      try {
        layer.focus({ preventScroll: true });
      } catch (err) {
        layer.focus();
      }
    });
    layer.addEventListener('pointerdown', handlePointerDown);
    layer.addEventListener('click', handleClick, true);
    layer.addEventListener('keydown', handleKeyDown);
  });
}

function waitForGachaReveal(layer, delay) {
  const revealDelay = Math.max(0, Number(delay) || 0);
  return new Promise(resolve => {
    let resolved = false;
    let timeoutId = window.setTimeout(() => finish(false, 0), revealDelay);
    const cleanup = () => {
      if (timeoutId !== null) {
        clearTimeout(timeoutId);
        timeoutId = null;
      }
      layer.removeEventListener('pointerup', handlePointerUp);
      layer.removeEventListener('click', handleClick, true);
      layer.removeEventListener('keydown', handleKeyDown);
    };
    const finish = (skipped, guardTime) => {
      if (resolved) return;
      resolved = true;
      cleanup();
      resolve({ skipped, guardTime });
    };
    const resolveWithGuardTime = event => {
      const timeStamp = typeof event.timeStamp === 'number' ? event.timeStamp + 16 : 0;
      finish(true, timeStamp);
    };
    const handlePointerUp = event => {
      if (resolved) return;
      if ('isPrimary' in event && event.isPrimary === false) return;
      if (typeof event.button === 'number' && event.button > 0) return;
      event.preventDefault();
      event.stopPropagation();
      resolveWithGuardTime(event);
    };
    const handleClick = event => {
      if (resolved) return;
      event.preventDefault();
      event.stopPropagation();
      resolveWithGuardTime(event);
    };
    const handleKeyDown = event => {
      if (resolved) return;
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        finish(true, 0);
      } else if (event.key === 'Escape') {
        finish(true, 0);
      }
    };
    layer.addEventListener('pointerup', handlePointerUp);
    layer.addEventListener('click', handleClick, true);
    layer.addEventListener('keydown', handleKeyDown);
  });
}

async function playGachaAnimation(outcome) {
  if (!outcome) return;
  if (!elements.gachaAnimation) {
    displayGachaResult(outcome);
    return;
  }

  const layer = elements.gachaAnimation;

  layer.hidden = false;
  layer.setAttribute('aria-hidden', 'false');
  layer.classList.remove('show-result');
  layer.classList.add('is-active');
  if (elements.gachaResult) {
    elements.gachaResult.innerHTML = '';
    elements.gachaResult.style.removeProperty('--rarity-color');
  }

  startGachaConfettiAnimation(outcome);

  let guardTime = 0;
  try {
    const revealResult = await waitForGachaReveal(layer, GACHA_ANIMATION_REVEAL_DELAY);

    displayGachaResult(outcome);
    layer.classList.add('show-result');

    guardTime = revealResult && typeof revealResult.guardTime === 'number'
      ? revealResult.guardTime
      : 0;

    await waitForGachaAnimationDismiss(layer, { ignoreClicksUntil: guardTime });
  } finally {
    stopGachaConfettiAnimation();
  }

  layer.classList.remove('show-result');
  layer.classList.remove('is-active');
  layer.setAttribute('aria-hidden', 'true');
  layer.hidden = true;
  if (elements.gachaResult) {
    elements.gachaResult.innerHTML = '';
    elements.gachaResult.style.removeProperty('--rarity-color');
  }
}

function handleGachaRoll() {
  const outcome = performGachaRoll(gachaRollMode);
  if (!outcome) return;
  displayGachaResult(outcome);
  const message = getGachaToastMessage(outcome);
  if (message) {
    showToast(message);
  }
}

function toggleGachaRollMode() {
  gachaRollMode = gachaRollMode === 1 ? 10 : 1;
  updateGachaUI();
}

async function handleGachaSunClick() {
  if (gachaAnimationInProgress) return;
  const outcome = performGachaRoll(gachaRollMode);
  if (!outcome) {
    return;
  }

  gachaAnimationInProgress = true;
  updateGachaUI();

  try {
    await playGachaAnimation(outcome);
    const message = getGachaToastMessage(outcome);
    if (message) {
      showToast(message);
    }
  } finally {
    gachaAnimationInProgress = false;
    if (elements.gachaSunButton) {
      elements.gachaSunButton.disabled = false;
    }
    updateGachaUI();
  }
}

function gainGachaTickets(amount = 1, options = {}) {
  const gain = Math.max(1, Math.floor(Number(amount) || 0));
  const current = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
  gameState.gachaTickets = current + gain;
  if (options && options.unlockTicketStar && gain > 0 && gameState.ticketStarUnlocked !== true) {
    gameState.ticketStarUnlocked = true;
    resetTicketStarState({ reschedule: true });
  }
  evaluatePageUnlocks();
  updateGachaUI();
  return gain;
}

function gainBonusParticulesTickets(amount = 1) {
  const gain = Math.max(1, Math.floor(Number(amount) || 0));
  const current = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
  gameState.bonusParticulesTickets = current + gain;
  updateArcadeTicketDisplay();
  if (typeof window !== 'undefined' && typeof window.updateMetauxCreditsUI === 'function') {
    window.updateMetauxCreditsUI();
  }
  return gain;
}

let ticketStarAverageIntervalMsOverride = TICKET_STAR_CONFIG.averageSpawnIntervalMs;

function getTicketStarAverageIntervalMs() {
  const value = Number.isFinite(ticketStarAverageIntervalMsOverride) && ticketStarAverageIntervalMsOverride > 0
    ? ticketStarAverageIntervalMsOverride
    : TICKET_STAR_CONFIG.averageSpawnIntervalMs;
  return Math.max(1000, value);
}

function setTicketStarAverageIntervalSeconds(seconds) {
  const baseSeconds = DEFAULT_TICKET_STAR_INTERVAL_SECONDS;
  const numericSeconds = Number(seconds);
  const resolvedSeconds = Number.isFinite(numericSeconds) && numericSeconds > 0
    ? numericSeconds
    : baseSeconds;
  const normalizedMs = Math.max(1000, resolvedSeconds * 1000);
  if (Math.abs(ticketStarAverageIntervalMsOverride - normalizedMs) < 1) {
    gameState.ticketStarAverageIntervalSeconds = normalizedMs / 1000;
    return false;
  }
  ticketStarAverageIntervalMsOverride = normalizedMs;
  gameState.ticketStarAverageIntervalSeconds = normalizedMs / 1000;
  return true;
}

function computeTicketStarDelay() {
  const average = getTicketStarAverageIntervalMs();
  const jitter = 0.5 + Math.random();
  return average * jitter;
}

function isTicketStarFeatureUnlocked() {
  return isPageUnlocked('gacha') && gameState.ticketStarUnlocked === true;
}

function getTicketStarAutoCollectDelayMs() {
  const config = gameState.ticketStarAutoCollect;
  if (!config) {
    return null;
  }
  const rawDelay = config.delaySeconds ?? config.delay ?? config.seconds ?? config.value;
  const delaySeconds = Number(rawDelay);
  if (!Number.isFinite(delaySeconds) || delaySeconds < 0) {
    return 0;
  }
  return delaySeconds * 1000;
}

function shouldAutoCollectTicketStar(now = performance.now()) {
  const delayMs = getTicketStarAutoCollectDelayMs();
  if (delayMs == null) {
    return false;
  }
  if (!ticketStarState.active) {
    return false;
  }
  if (!isGamePageActive()) {
    return false;
  }
  if (typeof document !== 'undefined' && document.hidden) {
    return false;
  }
  if (gamePageVisibleSince == null || now - gamePageVisibleSince < delayMs) {
    return false;
  }
  const spawnTime = Number(ticketStarState.spawnTime) || 0;
  if (spawnTime <= 0 || now - spawnTime < delayMs) {
    return false;
  }
  return true;
}

const ticketStarState = {
  element: null,
  active: false,
  position: { x: 0, y: 0 },
  velocity: { x: 0, y: 0 },
  width: 0,
  height: 0,
  nextSpawnTime: performance.now() + computeTicketStarDelay(),
  spawnTime: 0,
  lastSpawnEdge: null
};

function resetTicketStarState(options = {}) {
  if (ticketStarState.element && ticketStarState.element.parentNode) {
    ticketStarState.element.remove();
  }
  ticketStarState.element = null;
  ticketStarState.active = false;
  ticketStarState.position.x = 0;
  ticketStarState.position.y = 0;
  ticketStarState.velocity.x = 0;
  ticketStarState.velocity.y = 0;
  ticketStarState.width = 0;
  ticketStarState.height = 0;
  ticketStarState.spawnTime = 0;
  ticketStarState.lastSpawnEdge = null;
  const now = performance.now();
  if (!isTicketStarFeatureUnlocked()) {
    ticketStarState.nextSpawnTime = Number.POSITIVE_INFINITY;
    return;
  }
  if (options.reschedule) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
  } else if (!Number.isFinite(ticketStarState.nextSpawnTime) || ticketStarState.nextSpawnTime <= now) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
  }
}

function collectTicketStar(event) {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }
  if (!ticketStarState.active) {
    return;
  }
  if (!isTicketStarFeatureUnlocked()) {
    return;
  }
  const gained = gainGachaTickets(TICKET_STAR_CONFIG.rewardTickets);
  showToast(gained === 1 ? 'Ticket de tirage obtenu !' : `+${gained} tickets de tirage !`);
  if (ticketStarState.element && ticketStarState.element.parentNode) {
    ticketStarState.element.remove();
  }
  ticketStarState.element = null;
  ticketStarState.active = false;
  ticketStarState.spawnTime = 0;
  ticketStarState.width = 0;
  ticketStarState.height = 0;
  ticketStarState.velocity.x = 0;
  ticketStarState.velocity.y = 0;
  ticketStarState.position.x = 0;
  ticketStarState.position.y = 0;
  ticketStarState.lastSpawnEdge = null;
  ticketStarState.nextSpawnTime = performance.now() + computeTicketStarDelay();
  saveGame();
}

function spawnTicketStar(now = performance.now()) {
  if (!isTicketStarFeatureUnlocked()) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
    return;
  }
  if (!elements.ticketLayer) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
    return;
  }
  const layer = elements.ticketLayer;
  const layerWidth = layer.clientWidth;
  const layerHeight = layer.clientHeight;
  if (layerWidth <= 0 || layerHeight <= 0) {
    ticketStarState.nextSpawnTime = now + 2000;
    return;
  }

  if (ticketStarState.element && ticketStarState.element.parentNode) {
    ticketStarState.element.remove();
  }

  const star = document.createElement('button');
  star.type = 'button';
  star.className = 'ticket-star';
  star.setAttribute('aria-label', 'Collecter un ticket de tirage');
  star.style.setProperty('--ticket-star-size', `${TICKET_STAR_CONFIG.size}px`);
  star.innerHTML = '<img src="Assets/Image/star.png" alt="Étoile bonus" draggable="false" />';
  star.addEventListener('click', collectTicketStar);
  star.addEventListener('dragstart', event => event.preventDefault());

  layer.appendChild(star);

  const starWidth = star.offsetWidth || TICKET_STAR_CONFIG.size;
  const starHeight = star.offsetHeight || TICKET_STAR_CONFIG.size;
  const maxX = Math.max(0, layerWidth - starWidth);
  const maxY = Math.max(0, layerHeight - starHeight);
  let startX = Math.random() * maxX;
  let startY = Math.random() * maxY;
  const edges = ['top', 'right', 'bottom', 'left'];
  let edgePool = edges;
  if (ticketStarState.lastSpawnEdge && edges.length > 1) {
    const filtered = edges.filter(entry => entry !== ticketStarState.lastSpawnEdge);
    if (filtered.length) {
      edgePool = filtered;
    }
  }
  const edge = edgePool[Math.floor(Math.random() * edgePool.length)] ?? 'top';
  ticketStarState.lastSpawnEdge = edge;
  let angle;
  switch (edge) {
    case 'top':
      startY = 0;
      angle = Math.PI / 4 + Math.random() * (Math.PI / 2);
      break;
    case 'bottom':
      startY = maxY;
      angle = (Math.PI * 5) / 4 + Math.random() * (Math.PI / 2);
      break;
    case 'left':
      startX = 0;
      angle = -Math.PI / 4 + Math.random() * (Math.PI / 2);
      break;
    default:
      startX = maxX;
      angle = (3 * Math.PI) / 4 + Math.random() * (Math.PI / 2);
      break;
  }

  ticketStarState.element = star;
  ticketStarState.active = true;
  ticketStarState.position.x = startX;
  ticketStarState.position.y = startY;
  ticketStarState.width = starWidth;
  ticketStarState.height = starHeight;
  const speed = TICKET_STAR_CONFIG.speed;
  ticketStarState.velocity.x = Math.cos(angle) * speed;
  ticketStarState.velocity.y = Math.sin(angle) * speed;
  ticketStarState.nextSpawnTime = Number.POSITIVE_INFINITY;
  ticketStarState.spawnTime = now;

  star.style.transform = `translate(${startX}px, ${startY}px)`;
}

function updateTicketStar(deltaSeconds, now = performance.now()) {
  if (!elements.ticketLayer) {
    return;
  }
  if (!isTicketStarFeatureUnlocked()) {
    if (ticketStarState.active) {
      if (ticketStarState.element && ticketStarState.element.parentNode) {
        ticketStarState.element.remove();
      }
      ticketStarState.element = null;
      ticketStarState.active = false;
      ticketStarState.spawnTime = 0;
      ticketStarState.lastSpawnEdge = null;
    }
    ticketStarState.nextSpawnTime = Number.POSITIVE_INFINITY;
    return;
  }
  if (!ticketStarState.active && !Number.isFinite(ticketStarState.nextSpawnTime)) {
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
  }
  if (!ticketStarState.active) {
    if (now >= ticketStarState.nextSpawnTime) {
      spawnTicketStar(now);
    }
    return;
  }
  const star = ticketStarState.element;
  if (!star) {
    ticketStarState.active = false;
    ticketStarState.nextSpawnTime = now + computeTicketStarDelay();
    ticketStarState.spawnTime = 0;
    ticketStarState.lastSpawnEdge = null;
    return;
  }
  const layer = elements.ticketLayer;
  const width = layer.clientWidth;
  const height = layer.clientHeight;
  if (width <= 0 || height <= 0) {
    return;
  }
  if (shouldAutoCollectTicketStar(now)) {
    collectTicketStar();
    return;
  }
  const starWidth = star.offsetWidth || ticketStarState.width || TICKET_STAR_CONFIG.size;
  const starHeight = star.offsetHeight || ticketStarState.height || TICKET_STAR_CONFIG.size;
  const maxX = Math.max(0, width - starWidth);
  const maxY = Math.max(0, height - starHeight);
  let nextX = ticketStarState.position.x + ticketStarState.velocity.x * deltaSeconds;
  let nextY = ticketStarState.position.y + ticketStarState.velocity.y * deltaSeconds;

  if (nextX <= 0) {
    nextX = 0;
    ticketStarState.velocity.x = Math.abs(ticketStarState.velocity.x);
  } else if (nextX >= maxX) {
    nextX = maxX;
    ticketStarState.velocity.x = -Math.abs(ticketStarState.velocity.x);
  }

  if (nextY <= 0) {
    nextY = 0;
    ticketStarState.velocity.y = Math.abs(ticketStarState.velocity.y);
  } else if (nextY >= maxY) {
    nextY = maxY;
    ticketStarState.velocity.y = -Math.abs(ticketStarState.velocity.y);
  }

  ticketStarState.position.x = nextX;
  ticketStarState.position.y = nextY;
  ticketStarState.width = starWidth;
  ticketStarState.height = starHeight;
  star.style.transform = `translate(${nextX}px, ${nextY}px)`;
}

