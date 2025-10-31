const CONFIG = typeof window !== 'undefined' && window.GAME_CONFIG ? window.GAME_CONFIG : {};

const gameDataTranslate = (() => {
  const translator = typeof globalThis !== 'undefined' && typeof globalThis.t === 'function'
    ? globalThis.t.bind(globalThis)
    : null;
  if (translator) {
    return translator;
  }
  return (key, params) => {
    if (typeof key !== 'string' || !key) {
      return '';
    }
    if (!params || typeof params !== 'object') {
      return key;
    }
    return key.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
      const value = params[token];
      return value == null ? match : String(value);
    });
  };
})();

function translateGameDataKey(key, fallback = '', params) {
  const trimmedFallback = typeof fallback === 'string' ? fallback.trim() : '';
  if (typeof key === 'string') {
    const normalizedKey = key.trim();
    if (normalizedKey) {
      const translated = gameDataTranslate(normalizedKey, params);
      if (typeof translated === 'string') {
        const trimmedTranslation = translated.trim();
        if (trimmedTranslation && trimmedTranslation !== normalizedKey) {
          return trimmedTranslation;
        }
      }
    }
  }
  return trimmedFallback;
}

const DEFAULT_UPGRADE_MAX_LEVEL = (function resolveDefaultUpgradeMaxLevel() {
  const candidates = [
    CONFIG?.shop?.defaultMaxPurchase,
    CONFIG?.shop?.maxLevel,
    CONFIG?.shop?.maxPurchase
  ];
  for (const value of candidates) {
    const numeric = Number(value);
    if (Number.isFinite(numeric) && numeric > 0) {
      return Math.max(1, Math.floor(numeric));
    }
  }
  return 1000;
})();

const periodicElements = Array.isArray(globalThis.PERIODIC_ELEMENTS)
  ? globalThis.PERIODIC_ELEMENTS.map(def => ({
      ...def,
      position: def.position || { row: def.period ?? 0, column: def.group ?? 0 }
    }))
  : [];

const TOTAL_ELEMENT_COUNT = periodicElements.length;

const periodicElementIndex = new Map(
  periodicElements.map(def => [def.id, def])
);

const periodicElementByAtomicNumber = new Map(
  periodicElements
    .map(def => [Number(def.atomicNumber), def])
    .filter(([atomicNumber]) => Number.isFinite(atomicNumber))
);

const GACHA_SPECIAL_CARD_DEFINITIONS = [
  {
    id: 'hydrogene',
    assetPath: 'Assets/Cartes/Hydrogene.png',
    labelKey: 'scripts.gacha.cards.names.hydrogene',
    labelFallback: 'Carte Hydrogène'
  },
  {
    id: 'helium',
    assetPath: 'Assets/Cartes/Helium.png',
    labelKey: 'scripts.gacha.cards.names.helium',
    labelFallback: 'Carte Hélium'
  },
  {
    id: 'carbone',
    assetPath: 'Assets/Cartes/Carbone.png',
    labelKey: 'scripts.gacha.cards.names.carbone',
    labelFallback: 'Carte Carbone'
  },
  {
    id: 'azote',
    assetPath: 'Assets/Cartes/Azote.png',
    labelKey: 'scripts.gacha.cards.names.azote',
    labelFallback: 'Carte Azote'
  },
  {
    id: 'oxygene',
    assetPath: 'Assets/Cartes/Oxygene.png',
    labelKey: 'scripts.gacha.cards.names.oxygene',
    labelFallback: 'Carte Oxygène'
  },
  {
    id: 'fer',
    assetPath: 'Assets/Cartes/Fer.png',
    labelKey: 'scripts.gacha.cards.names.fer',
    labelFallback: 'Carte Fer'
  },
  {
    id: 'or',
    assetPath: 'Assets/Cartes/Or.png',
    labelKey: 'scripts.gacha.cards.names.or',
    labelFallback: 'Carte Or'
  },
  {
    id: 'argent',
    assetPath: 'Assets/Cartes/Argent.png',
    labelKey: 'scripts.gacha.cards.names.argent',
    labelFallback: 'Carte Argent'
  },
  {
    id: 'cuivre',
    assetPath: 'Assets/Cartes/Cuivre.png',
    labelKey: 'scripts.gacha.cards.names.cuivre',
    labelFallback: 'Carte Cuivre'
  },
  {
    id: 'plutonium',
    assetPath: 'Assets/Cartes/Plutonium.png',
    labelKey: 'scripts.gacha.cards.names.plutonium',
    labelFallback: 'Carte Plutonium'
  }
];

function normalizeBonusImageDefinition(entry, folder) {
  if (!entry || typeof entry !== 'object') {
    return null;
  }
  const id = typeof entry.id === 'string' ? entry.id.trim() : '';
  if (!id) {
    return null;
  }
  let assetPath = typeof entry.assetPath === 'string' ? entry.assetPath.trim() : '';
  if (!assetPath && typeof entry.path === 'string') {
    assetPath = entry.path.trim();
  }
  if (!assetPath && typeof entry.file === 'string') {
    assetPath = entry.file.trim();
  }
  if (!assetPath && typeof entry.fileName === 'string' && entry.fileName.trim()) {
    const safeFolder = typeof folder === 'string' && folder.trim()
      ? folder.trim().replace(/\/+$/, '')
      : '';
    const sanitized = entry.fileName.trim().replace(/^\/+/, '');
    assetPath = safeFolder ? `${safeFolder}/${sanitized}` : sanitized;
  }
  if (!assetPath && typeof folder === 'string' && folder.trim()) {
    const safeFolder = folder.trim().replace(/\/+$/, '');
    assetPath = safeFolder ? `${safeFolder}/${id}.png` : `${id}.png`;
  }
  const labelKey = typeof entry.labelKey === 'string' ? entry.labelKey.trim() : '';
  const labelValue = typeof entry.label === 'string' ? entry.label.trim() : '';
  const nameValue = typeof entry.name === 'string' ? entry.name.trim() : '';
  const names = {};
  const rawNames = entry.names && typeof entry.names === 'object' ? entry.names : null;
  if (rawNames) {
    Object.keys(rawNames).forEach(locale => {
      const value = rawNames[locale];
      if (typeof value === 'string' && value.trim()) {
        names[locale.trim()] = value.trim();
      }
    });
  }
  const fallbackFromNames = names.fr
    || names['fr-FR']
    || names['fr_fr']
    || names.en
    || names['en-US']
    || names['en_us']
    || '';
  const labelFallback = labelValue || nameValue || fallbackFromNames || `Image ${id}`;
  return {
    id,
    assetPath: assetPath || null,
    labelKey,
    labelFallback,
    names
  };
}

const RAW_BONUS_IMAGE_ENTRIES = (() => {
  const config = CONFIG?.gacha?.bonusImages;
  if (!config) {
    return [];
  }
  if (Array.isArray(config)) {
    return config;
  }
  if (Array.isArray(config.images)) {
    return config.images;
  }
  return [];
})();

const GACHA_BONUS_IMAGE_DEFINITIONS = RAW_BONUS_IMAGE_ENTRIES
  .map(entry => normalizeBonusImageDefinition(entry, CONFIG?.gacha?.bonusImages?.folder))
  .filter(def => def && def.id);

const configElements = Array.isArray(CONFIG.elements) ? CONFIG.elements : [];

const elementConfigByAtomicNumber = new Map();
configElements.forEach(entry => {
  if (!entry || typeof entry !== 'object') return;
  const atomicNumber = Number(entry.numero ?? entry.number ?? entry.atomicNumber);
  if (!Number.isFinite(atomicNumber)) return;
  elementConfigByAtomicNumber.set(atomicNumber, entry);
});

function resolveElementRarity(definition) {
  if (!definition) return null;
  const atomicNumber = Number(definition.atomicNumber);
  const configEntry = Number.isFinite(atomicNumber)
    ? elementConfigByAtomicNumber.get(atomicNumber)
    : null;
  if (configEntry) {
    if (configEntry.rarete) return configEntry.rarete;
    if (configEntry.rarity) return configEntry.rarity;
  }
  if (definition.tags && definition.tags.rarity) {
    return definition.tags.rarity;
  }
  return null;
}

const elementRarityIndex = new Map();
periodicElements.forEach(def => {
  const rarity = resolveElementRarity(def);
  if (rarity) {
    elementRarityIndex.set(def.id, rarity);
  }
});

const configuredRarityIds = new Set(elementRarityIndex.values());

function normalizeFusionElementList(source) {
  const normalized = [];
  if (source == null) {
    return normalized;
  }
  const list = Array.isArray(source) ? source : [source];
  list.forEach(rawEntry => {
    if (rawEntry == null) {
      return;
    }
    let entry = rawEntry;
    if (typeof rawEntry === 'string') {
      entry = { elementId: rawEntry };
    } else if (typeof rawEntry === 'number') {
      entry = { atomicNumber: rawEntry };
    }
    if (!entry || typeof entry !== 'object') {
      return;
    }
    let elementDef = null;
    let elementId = typeof entry.elementId === 'string' ? entry.elementId.trim() : '';
    const atomicNumber = Number(entry.atomicNumber ?? entry.numero ?? entry.number);
    const symbol = typeof entry.symbol === 'string' ? entry.symbol.trim() : '';
    if (elementId && periodicElementIndex.has(elementId)) {
      elementDef = periodicElementIndex.get(elementId);
    } else if (Number.isFinite(atomicNumber) && periodicElementByAtomicNumber.has(atomicNumber)) {
      elementDef = periodicElementByAtomicNumber.get(atomicNumber);
    } else if (symbol) {
      elementDef = periodicElements.find(def => {
        if (typeof def.symbol !== 'string') {
          return false;
        }
        return def.symbol.trim().toLowerCase() === symbol.toLowerCase();
      }) || null;
    }
    if (!elementDef) {
      return;
    }
    elementId = elementDef.id;
    const rawCount = Number(entry.count ?? entry.quantity ?? entry.amount ?? 1);
    const count = Number.isFinite(rawCount) && rawCount > 0 ? Math.floor(rawCount) : 1;
    normalized.push({ elementId, elementDef, count });
  });
  return normalized;
}

function normalizeFusionDefinition(entry, index = 0) {
  if (!entry || typeof entry !== 'object') {
    return null;
  }
  const rawId = entry.id ?? entry.key ?? entry.identifier ?? null;
  let id = typeof rawId === 'string' ? rawId.trim() : '';
  if (!id) {
    id = `fusion-${index + 1}`;
  }
  const translationBaseKey = typeof entry.translationKey === 'string'
    ? entry.translationKey.trim()
    : '';
  const explicitNameKey = typeof entry.nameKey === 'string' ? entry.nameKey.trim() : '';
  const explicitDescriptionKey = typeof entry.descriptionKey === 'string'
    ? entry.descriptionKey.trim()
    : '';
  const nameFallback = typeof entry.name === 'string' && entry.name.trim()
    ? entry.name.trim()
    : gameDataTranslate('scripts.gameData.fusions.defaultName', { number: index + 1 });
  const descriptionFallback = typeof entry.description === 'string' ? entry.description.trim() : '';
  const nameKey = explicitNameKey || (translationBaseKey ? `${translationBaseKey}.name` : '');
  const descriptionKey = explicitDescriptionKey || (translationBaseKey ? `${translationBaseKey}.description` : '');
  const nameParams = { number: index + 1 };
  const name = translateGameDataKey(nameKey, nameFallback, nameParams);
  const description = translateGameDataKey(descriptionKey, descriptionFallback);
  const inputSource = entry.inputs ?? entry.ingredients ?? [];
  const inputs = normalizeFusionElementList(inputSource);
  if (!inputs.length) {
    return null;
  }
  let successChance = Number(entry.successChance ?? entry.chance ?? entry.probability);
  if (!Number.isFinite(successChance)) {
    successChance = 0;
  } else if (successChance > 1) {
    successChance = Math.max(0, Math.min(successChance, 100)) / 100;
  } else if (successChance < 0) {
    successChance = 0;
  }
  successChance = Math.max(0, Math.min(successChance, 1));
  const rewardsRaw = entry.rewards ?? entry.reward ?? {};
  const rawApc = Number(rewardsRaw.apcFlat ?? rewardsRaw.apc ?? rewardsRaw.perClick ?? rewardsRaw.click ?? 0);
  const rawAps = Number(rewardsRaw.apsFlat ?? rewardsRaw.aps ?? rewardsRaw.perSecond ?? rewardsRaw.auto ?? 0);
  const apcFlat = Number.isFinite(rawApc) ? rawApc : 0;
  const apsFlat = Number.isFinite(rawAps) ? rawAps : 0;
  const rewardElementSource = rewardsRaw.elements ?? rewardsRaw.items ?? rewardsRaw.element ?? [];
  const elementRewards = normalizeFusionElementList(rewardElementSource);
  return {
    id,
    name,
    description,
    inputs,
    successChance,
    rewards: {
      apcFlat,
      apsFlat,
      elements: elementRewards
    },
    localization: {
      nameKey,
      nameFallback,
      nameParams,
      descriptionKey,
      descriptionFallback
    }
  };
}

const rawFusionList = Array.isArray(CONFIG.fusions) ? CONFIG.fusions : [];
const FUSION_DEFS = rawFusionList
  .map((entry, index) => normalizeFusionDefinition(entry, index))
  .filter(Boolean);
const FUSION_DEFINITION_MAP = new Map(FUSION_DEFS.map(def => [def.id, def]));

function refreshFusionLocalization() {
  FUSION_DEFS.forEach(def => {
    if (!def || typeof def !== 'object') {
      return;
    }
    const localization = def.localization;
    if (!localization || typeof localization !== 'object') {
      return;
    }
    const {
      nameKey,
      nameFallback,
      nameParams,
      descriptionKey,
      descriptionFallback
    } = localization;
    def.name = translateGameDataKey(nameKey, nameFallback, nameParams);
    def.description = translateGameDataKey(descriptionKey, descriptionFallback);
  });
}

refreshFusionLocalization();

if (typeof window !== 'undefined') {
  window.refreshFusionLocalization = refreshFusionLocalization;
  if (typeof window.addEventListener === 'function') {
    window.addEventListener('i18n:languagechange', refreshFusionLocalization);
  }
}

function readNumberProperty(source, candidates) {
  if (!source || typeof source !== 'object') {
    return undefined;
  }
  for (const key of candidates) {
    if (!(key in source)) continue;
    const rawValue = source[key];
    const numeric = typeof rawValue === 'number'
      ? rawValue
      : (typeof rawValue === 'string' ? Number(rawValue) : Number.NaN);
    if (Number.isFinite(numeric)) {
      return numeric;
    }
  }
  return undefined;
}

function coerceFiniteNumber(raw, options = {}) {
  if (raw == null) {
    return null;
  }
  const { allowZero = true, positiveOnly = false } = options;
  const numeric = typeof raw === 'number'
    ? raw
    : (typeof raw === 'string' ? Number(raw) : Number.NaN);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  if (!allowZero && numeric === 0) {
    return null;
  }
  if (positiveOnly && numeric <= 0) {
    return null;
  }
  return numeric;
}

function readBooleanProperty(source, candidates) {
  if (!source || typeof source !== 'object') {
    return undefined;
  }
  for (const key of candidates) {
    if (!(key in source)) continue;
    const rawValue = source[key];
    if (typeof rawValue === 'boolean') {
      return rawValue;
    }
    if (typeof rawValue === 'number') {
      return rawValue !== 0;
    }
    if (typeof rawValue === 'string') {
      const normalized = rawValue.trim().toLowerCase();
      if (!normalized) continue;
      if (['true', '1', 'yes', 'on'].includes(normalized)) {
        return true;
      }
      if (['false', '0', 'no', 'off'].includes(normalized)) {
        return false;
      }
    }
  }
  return undefined;
}

function normalizeLabel(value) {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function translateElementLabel(value) {
  const normalized = normalizeLabel(value);
  if (!normalized) {
    return normalized;
  }
  if (normalized.startsWith('scripts.')) {
    const translated = gameDataTranslate(normalized);
    if (
      translated
      && typeof translated === 'string'
      && translated.trim()
      && translated !== normalized
    ) {
      return translated.trim();
    }
  }
  return normalized;
}

function createElementGroupAddConfig(values, options = {}) {
  const {
    defaultMinCopies = 0,
    defaultMinUnique = 0,
    defaultRequireAllUnique = false
  } = options;
  return {
    clickAdd: values.clickAdd ?? 0,
    autoAdd: values.autoAdd ?? 0,
    uniqueClickAdd: values.uniqueClickAdd ?? 0,
    uniqueAutoAdd: values.uniqueAutoAdd ?? 0,
    duplicateClickAdd: values.duplicateClickAdd ?? 0,
    duplicateAutoAdd: values.duplicateAutoAdd ?? 0,
    rarityFlatMultipliers: values.rarityFlatMultipliers ?? null,
    minCopies: values.minCopies ?? defaultMinCopies,
    minUnique: values.minUnique ?? defaultMinUnique,
    requireAllUnique: values.requireAllUnique ?? defaultRequireAllUnique,
    label: values.label ?? null
  };
}

function createFlatAddConfig(value, options = {}) {
  return createElementGroupAddConfig({ clickAdd: value }, options);
}

function normalizeElementGroupAddConfig(raw, options = {}) {
  if (raw == null) {
    return null;
  }
  const numericValue = coerceFiniteNumber(raw, { allowZero: false });
  if (numericValue != null) {
    return createFlatAddConfig(numericValue, options);
  }
  if (typeof raw !== 'object') {
    return null;
  }
  const clickAdd = coerceFiniteNumber(readNumberProperty(raw, ['clickAdd', 'apc', 'perClick', 'manual', 'click'])) ?? 0;
  const autoAdd = coerceFiniteNumber(readNumberProperty(raw, ['autoAdd', 'aps', 'perSecond', 'auto', 'automatic'])) ?? 0;
  const uniqueClickAdd = coerceFiniteNumber(readNumberProperty(raw, [
    'uniqueClickAdd',
    'clickAddUnique',
    'perUniqueClick',
    'uniquePerClick',
    'uniqueApc'
  ])) ?? 0;
  const uniqueAutoAdd = coerceFiniteNumber(readNumberProperty(raw, [
    'uniqueAutoAdd',
    'autoAddUnique',
    'perUniqueAuto',
    'uniquePerSecond',
    'uniqueAps'
  ])) ?? 0;
  const duplicateClickAdd = coerceFiniteNumber(readNumberProperty(raw, [
    'duplicateClickAdd',
    'clickAddDuplicate',
    'perDuplicateClick',
    'duplicatePerClick',
    'duplicateApc'
  ])) ?? 0;
  const duplicateAutoAdd = coerceFiniteNumber(readNumberProperty(raw, [
    'duplicateAutoAdd',
    'autoAddDuplicate',
    'perDuplicateAuto',
    'duplicatePerSecond',
    'duplicateAps'
  ])) ?? 0;
  const rarityFlatMultipliers = normalizeRarityFlatMultipliers(
    raw.rarityFlatMultipliers
      ?? raw.targetRarityFlatMultipliers
      ?? raw.rarityFlatMultiplier
      ?? raw.targetRarityFlatMultiplier
      ?? raw.flatMultipliers
      ?? raw.applyToRarities
  );
  const hasFlatEffect = [
    clickAdd,
    autoAdd,
    uniqueClickAdd,
    uniqueAutoAdd,
    duplicateClickAdd,
    duplicateAutoAdd
  ].some(value => value !== 0);
  const hasRarityFlatMultiplier = Array.isArray(rarityFlatMultipliers) && rarityFlatMultipliers.length > 0;
  if (!hasFlatEffect && !hasRarityFlatMultiplier) {
    return null;
  }
  const minCopiesCandidate = readNumberProperty(raw, [
    'minCopies',
    'minimumCopies',
    'requireCopies',
    'requiredCopies',
    'requiresCopies'
  ]);
  const minUniqueCandidate = readNumberProperty(raw, [
    'minUnique',
    'minimumUnique',
    'requireUnique',
    'requiredUnique',
    'requiresUnique',
    'minOwned',
    'minimumOwned',
    'requireOwned',
    'requiredOwned',
    'requiresOwned'
  ]);
  const requireAllUniqueCandidate = readBooleanProperty(raw, [
    'requireAllUnique',
    'requireAll',
    'requireFullSet',
    'requiresFullSet',
    'fullSet',
    'completeSet'
  ]);
  const minCopiesValue = coerceFiniteNumber(minCopiesCandidate, { positiveOnly: true });
  const minUniqueValue = coerceFiniteNumber(minUniqueCandidate, { positiveOnly: true });
  const minCopies = minCopiesValue != null ? Math.floor(minCopiesValue) : undefined;
  const minUnique = minUniqueValue != null ? Math.floor(minUniqueValue) : undefined;
  const requireAllUnique = requireAllUniqueCandidate != null
    ? requireAllUniqueCandidate
    : undefined;
  const label = translateElementLabel(raw.label);
  return createElementGroupAddConfig({
    clickAdd,
    autoAdd,
    uniqueClickAdd,
    uniqueAutoAdd,
    duplicateClickAdd,
    duplicateAutoAdd,
    rarityFlatMultipliers,
    minCopies,
    minUnique,
    requireAllUnique,
    label
  }, options);
}

function normalizeElementGroupAddConfigList(raw, options = {}) {
  if (raw == null) {
    return null;
  }
  if (Array.isArray(raw)) {
    const normalized = raw
      .map(entry => normalizeElementGroupAddConfig(entry, options))
      .filter(entry => entry);
    return normalized.length ? normalized : null;
  }
  const single = normalizeElementGroupAddConfig(raw, options);
  return single ? [single] : null;
}

function normalizeRarityFlatMultipliers(raw) {
  if (raw == null) {
    return null;
  }

  const result = [];
  const registerEntry = (rarityId, config) => {
    if (!rarityId || typeof rarityId !== 'string') {
      return;
    }
    const normalizedId = rarityId.trim();
    if (!normalizedId) {
      return;
    }

    let perClickMultiplier;
    let perSecondMultiplier;

    if (typeof config === 'number') {
      if (Number.isFinite(config)) {
        perClickMultiplier = config;
      }
    } else if (typeof config === 'string') {
      const numeric = Number(config);
      if (Number.isFinite(numeric)) {
        perClickMultiplier = numeric;
      }
    } else if (config && typeof config === 'object') {
      const unifiedCandidate = readNumberProperty(config, ['multiplier', 'value', 'factor']);
      const clickCandidate = readNumberProperty(config, [
        'perClick',
        'click',
        'apc',
        'manual',
        'perClickMultiplier',
        'clickMultiplier',
        'clickFlatMultiplier'
      ]);
      const autoCandidate = readNumberProperty(config, [
        'perSecond',
        'auto',
        'aps',
        'automatic',
        'perSecondMultiplier',
        'autoMultiplier',
        'autoFlatMultiplier'
      ]);
      if (Number.isFinite(clickCandidate)) {
        perClickMultiplier = clickCandidate;
      }
      if (Number.isFinite(autoCandidate)) {
        perSecondMultiplier = autoCandidate;
      }
      if (!Number.isFinite(clickCandidate) && Number.isFinite(unifiedCandidate)) {
        perClickMultiplier = unifiedCandidate;
      }
      if (!Number.isFinite(autoCandidate) && Number.isFinite(unifiedCandidate)) {
        perSecondMultiplier = unifiedCandidate;
      }
    }

    const validClick = Number.isFinite(perClickMultiplier) && perClickMultiplier > 0 ? perClickMultiplier : 1;
    const validSecond = Number.isFinite(perSecondMultiplier) && perSecondMultiplier > 0 ? perSecondMultiplier : 1;
    if (validClick === 1 && validSecond === 1) {
      return;
    }
    result.push({
      rarityId: normalizedId,
      perClick: validClick,
      perSecond: validSecond
    });
  };

  if (Array.isArray(raw)) {
    raw.forEach(entry => {
      if (!entry) return;
      if (typeof entry === 'string') {
        registerEntry(entry, 2);
        return;
      }
      if (typeof entry !== 'object') {
        return;
      }
      const rarityId = entry.rarity ?? entry.rarityId ?? entry.target ?? entry.targetRarity;
      if (!rarityId) return;
      registerEntry(String(rarityId), entry);
    });
  } else if (typeof raw === 'object') {
    Object.entries(raw).forEach(([rarityId, config]) => {
      registerEntry(String(rarityId), config);
    });
  } else if (typeof raw === 'string') {
    registerEntry(raw, 2);
  } else if (typeof raw === 'number') {
    registerEntry('commun', raw);
  }

  return result.length ? result : null;
}

function normalizeElementGroupMultiplier(raw) {
  if (raw == null) {
    return null;
  }
  const baseValue = coerceFiniteNumber(raw, { positiveOnly: true });
  if (baseValue != null) {
    const base = baseValue;
    return {
      base,
      every: 0,
      increment: 0,
      cap: base,
      targets: new Set(['perClick', 'perSecond']),
      label: null
    };
  }
  if (typeof raw !== 'object') {
    return null;
  }
  const baseCandidate = readNumberProperty(raw, ['base', 'start', 'initial', 'value']);
  const base = coerceFiniteNumber(baseCandidate, { positiveOnly: true }) ?? 1;
  const everyCandidate = readNumberProperty(raw, ['every', 'each', 'per', 'step', 'threshold', 'interval']);
  const everyValue = coerceFiniteNumber(everyCandidate, { positiveOnly: true });
  const every = everyValue != null ? Math.floor(everyValue) : 0;
  const incrementCandidate = readNumberProperty(raw, ['increment', 'gain', 'stepValue', 'bonus', 'amount', 'increase']);
  const increment = coerceFiniteNumber(incrementCandidate) ?? 0;
  const capCandidate = readNumberProperty(raw, ['cap', 'max', 'maximum', 'limit']);
  let cap = coerceFiniteNumber(capCandidate, { positiveOnly: true }) ?? Number.POSITIVE_INFINITY;
  if (Number.isFinite(cap)) {
    cap = Math.max(cap, base);
  }
  const rawTargets = raw.targets ?? raw.appliesTo ?? raw.affects ?? raw.types ?? raw.scope;
  const targets = new Set();
  const registerTarget = target => {
    if (typeof target !== 'string') return;
    const normalized = target.trim().toLowerCase();
    if (!normalized) return;
    if (['perclick', 'click', 'manual', 'apc', 'manualclick'].includes(normalized)) {
      targets.add('perClick');
    }
    if (['persecond', 'auto', 'automatic', 'aps', 'persec'].includes(normalized)) {
      targets.add('perSecond');
    }
  };
  if (Array.isArray(rawTargets)) {
    rawTargets.forEach(registerTarget);
  } else if (typeof rawTargets === 'string') {
    registerTarget(rawTargets);
  }
  if (targets.size === 0) {
    targets.add('perClick');
    targets.add('perSecond');
  }
  const label = translateElementLabel(raw.label);
  if (increment === 0 && every === 0 && base === 1) {
    return null;
  }
  return {
    base,
    every,
    increment,
    cap,
    targets,
    label
  };
}

function normalizeCritBonusEffect(raw) {
  if (raw == null) {
    return null;
  }
  const numericValue = coerceFiniteNumber(raw, { allowZero: false });
  if (numericValue != null) {
    return { chanceAdd: numericValue };
  }
  if (typeof raw !== 'object') {
    return null;
  }
  const effectSource = raw.effect && typeof raw.effect === 'object' ? raw.effect : raw;
  const effect = {};

  const chanceSetValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'chanceSet',
    'chanceBase',
    'setChance',
    'critChanceSet',
    'critChance'
  ]));
  if (chanceSetValue != null) {
    effect.chanceSet = Math.max(0, chanceSetValue);
  }

  const chanceAddValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'chanceAdd',
    'chanceBonus',
    'critChanceAdd',
    'bonusChance',
    'addChance'
  ]));
  if (chanceAddValue != null && chanceAddValue !== 0) {
    effect.chanceAdd = chanceAddValue;
  }

  const chanceMultValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'chanceMult',
    'chanceMultiplier',
    'critChanceMult',
    'multChance'
  ]), { positiveOnly: true });
  if (chanceMultValue != null && chanceMultValue !== 1) {
    effect.chanceMult = chanceMultValue;
  }

  const multiplierSetValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'multiplierSet',
    'setMultiplier',
    'critMultiplierSet',
    'critMultiplier'
  ]));
  if (multiplierSetValue != null) {
    effect.multiplierSet = Math.max(0, multiplierSetValue);
  }

  const multiplierAddValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'multiplierAdd',
    'damageAdd',
    'critMultiplierAdd',
    'bonusMultiplier',
    'bonusDamage'
  ]));
  if (multiplierAddValue != null && multiplierAddValue !== 0) {
    effect.multiplierAdd = multiplierAddValue;
  }

  const multiplierMultValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'multiplierMult',
    'multiplierMultiplier',
    'critMultiplierMult',
    'damageMult'
  ]), { positiveOnly: true });
  if (multiplierMultValue != null && multiplierMultValue !== 1) {
    effect.multiplierMult = multiplierMultValue;
  }

  const maxMultiplierSetValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'maxMultiplierSet',
    'capSet',
    'critMaxMultiplierSet',
    'maxMultiplier'
  ]));
  if (maxMultiplierSetValue != null) {
    effect.maxMultiplierSet = Math.max(0, maxMultiplierSetValue);
  }

  const maxMultiplierAddValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'maxMultiplierAdd',
    'capAdd',
    'critMaxMultiplierAdd'
  ]));
  if (maxMultiplierAddValue != null && maxMultiplierAddValue !== 0) {
    effect.maxMultiplierAdd = maxMultiplierAddValue;
  }

  const maxMultiplierMultValue = coerceFiniteNumber(readNumberProperty(effectSource, [
    'maxMultiplierMult',
    'capMult',
    'critMaxMultiplierMult'
  ]), { positiveOnly: true });
  if (maxMultiplierMultValue != null && maxMultiplierMultValue !== 1) {
    effect.maxMultiplierMult = maxMultiplierMultValue;
  }

  return Object.keys(effect).length ? effect : null;
}

function normalizeElementGroupCritConfig(raw) {
  if (raw == null) {
    return null;
  }
  if (typeof raw === 'number' || typeof raw === 'string') {
    const effect = normalizeCritBonusEffect(raw);
    return effect ? { perUnique: effect, perDuplicate: null, labels: null } : null;
  }
  if (typeof raw !== 'object') {
    return null;
  }
  const perUnique = normalizeCritBonusEffect(
    raw.perUnique
      ?? raw.perUniqueCopy
      ?? raw.unique
      ?? raw.first
      ?? raw.perUniqueElement
      ?? raw.perNew
  );
  const perDuplicate = normalizeCritBonusEffect(
    raw.perDuplicate
      ?? raw.perCopyBeyondFirst
      ?? raw.duplicate
      ?? raw.extra
      ?? raw.dupe
      ?? raw.perAdditional
  );
  const labels = {};
  if (raw.labels && typeof raw.labels === 'object') {
    ['perUnique', 'perDuplicate'].forEach(key => {
      const normalized = translateElementLabel(raw.labels[key]);
      if (normalized) {
        labels[key] = normalized;
      }
    });
  }
  const hasLabels = Object.keys(labels).length > 0;
  if (!perUnique && !perDuplicate) {
    return null;
  }
  return {
    perUnique,
    perDuplicate,
    labels: hasLabels ? labels : null
  };
}

function createRarityMultiplierBonusConfig(amount, details = {}) {
  const {
    uniqueThreshold = 0,
    copyThreshold = 0,
    targets,
    label = null
  } = details;
  return {
    amount,
    uniqueThreshold,
    copyThreshold,
    targets: targets ?? new Set(['perClick', 'perSecond']),
    label
  };
}

function normalizeRarityMultiplierBonus(raw) {
  if (raw == null) {
    return null;
  }
  const numericValue = coerceFiniteNumber(raw, { allowZero: false });
  if (numericValue != null) {
    return createRarityMultiplierBonusConfig(numericValue);
  }
  if (typeof raw !== 'object') {
    return null;
  }
  const amountValue = coerceFiniteNumber(readNumberProperty(raw, ['amount', 'value', 'add', 'increase', 'bonus']), {
    allowZero: false
  });
  if (amountValue == null) {
    return null;
  }
  const uniqueThresholdCandidate = readNumberProperty(raw, [
    'uniqueThreshold',
    'minUnique',
    'minimumUnique',
    'requiredUnique',
    'requiresUnique',
    'unique',
    'threshold',
    'count'
  ]);
  const copyThresholdCandidate = readNumberProperty(raw, [
    'copyThreshold',
    'minCopies',
    'minimumCopies',
    'requiredCopies',
    'requiresCopies',
    'copies'
  ]);
  const uniqueThresholdValue = coerceFiniteNumber(uniqueThresholdCandidate, { positiveOnly: true });
  const copyThresholdValue = coerceFiniteNumber(copyThresholdCandidate, { positiveOnly: true });
  const uniqueThreshold = uniqueThresholdValue != null ? Math.floor(uniqueThresholdValue) : 0;
  const copyThreshold = copyThresholdValue != null ? Math.floor(copyThresholdValue) : 0;
  const rawTargets = raw.targets ?? raw.appliesTo ?? raw.scope ?? raw.types;
  const targets = new Set();
  const registerTarget = target => {
    if (typeof target !== 'string') return;
    const normalized = target.trim().toLowerCase();
    if (!normalized) return;
    if (['perclick', 'click', 'manual', 'apc', 'manualclick'].includes(normalized)) {
      targets.add('perClick');
    }
    if (['persecond', 'auto', 'automatic', 'aps', 'persec'].includes(normalized)) {
      targets.add('perSecond');
    }
  };
  if (Array.isArray(rawTargets)) {
    rawTargets.forEach(registerTarget);
  } else if (typeof rawTargets === 'string') {
    registerTarget(rawTargets);
  }
  if (targets.size === 0) {
    targets.add('perClick');
    targets.add('perSecond');
  }
  const label = translateElementLabel(raw.label);
  return createRarityMultiplierBonusConfig(amountValue, {
    uniqueThreshold,
    copyThreshold,
    targets,
    label
  });
}

function normalizeElementGroupBonus(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const perCopy = normalizeElementGroupAddConfig(
    raw.perCopy ?? raw.perElement ?? raw.perCopyBonus ?? raw.perElementBonus ?? raw.perCollect,
    { defaultMinCopies: 1 }
  );
  const setBonuses = normalizeElementGroupAddConfigList(
    raw.setBonus ?? raw.groupBonus ?? raw.set ?? raw.group ?? raw.setReward ?? raw.bonusDeGroupe,
    { defaultRequireAllUnique: true }
  );
  const multiplier = normalizeElementGroupMultiplier(
    raw.multiplier ?? raw.groupMultiplier ?? raw.multiplicateur ?? raw.multiplierConfig
  );
  const crit = normalizeElementGroupCritConfig(
    raw.crit ?? raw.critBonus ?? raw.critBonuses ?? raw.critics ?? raw.critical
  );
  const rarityMultiplierBonus = normalizeRarityMultiplierBonus(
    raw.rarityMultiplierBonus
      ?? raw.rarityBonus
      ?? raw.multiplierBonus
      ?? raw.rarityMultiplierBoost
      ?? raw.rarityBoost
  );
  const labels = {};
  if (raw.labels && typeof raw.labels === 'object') {
    ['perCopy', 'setBonus', 'multiplier', 'rarityMultiplier'].forEach(key => {
      const normalized = translateElementLabel(raw.labels[key]);
      if (normalized) {
        labels[key] = normalized;
      }
    });
  }
  const hasLabels = Object.keys(labels).length > 0;
  if (!perCopy && (!setBonuses || setBonuses.length === 0) && !multiplier && !crit && !rarityMultiplierBonus) {
    return null;
  }
  return {
    perCopy,
    setBonus: setBonuses ? setBonuses[0] : null,
    setBonuses,
    multiplier,
    crit,
    rarityMultiplierBonus,
    labels: hasLabels ? labels : null
  };
}

const RAW_ELEMENT_GROUP_BONUS_GROUPS = (() => {
  const rawConfig = CONFIG.elementBonuses ?? CONFIG.elementBonus ?? null;
  if (!rawConfig || typeof rawConfig !== 'object') {
    return {};
  }
  const rawGroups = rawConfig.groups
    ?? rawConfig.byRarity
    ?? rawConfig.rarity
    ?? rawConfig.groupsByRarity
    ?? rawConfig;
  if (!rawGroups || typeof rawGroups !== 'object') {
    return {};
  }
  return rawGroups;
})();

const CATEGORY_LABEL_KEYS = {
  'alkali-metal': 'scripts.gameData.categories.alkaliMetal',
  'alkaline-earth-metal': 'scripts.gameData.categories.alkalineEarthMetal',
  'transition-metal': 'scripts.gameData.categories.transitionMetal',
  'post-transition-metal': 'scripts.gameData.categories.postTransitionMetal',
  metalloid: 'scripts.gameData.categories.metalloid',
  nonmetal: 'scripts.gameData.categories.nonmetal',
  halogen: 'scripts.gameData.categories.halogen',
  'noble-gas': 'scripts.gameData.categories.nobleGas',
  lanthanide: 'scripts.gameData.categories.lanthanide',
  actinide: 'scripts.gameData.categories.actinide'
};

function formatCategoryFallbackLabel(id) {
  if (!id) {
    return '';
  }
  return id
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ');
}

const CATEGORY_LABELS = {};
Object.entries(CATEGORY_LABEL_KEYS).forEach(([key, messageKey]) => {
  Object.defineProperty(CATEGORY_LABELS, key, {
    enumerable: true,
    configurable: true,
    get() {
      const translated = gameDataTranslate(messageKey);
      if (typeof translated === 'string') {
        const trimmed = translated.trim();
        if (trimmed && trimmed !== messageKey) {
          return trimmed;
        }
      }
      return formatCategoryFallbackLabel(key);
    }
  });
});

const ELEMENT_GROUP_BONUS_CONFIG = (() => {
  const result = new Map();
  Object.entries(RAW_ELEMENT_GROUP_BONUS_GROUPS).forEach(([rarityId, rawValue]) => {
    if (!rarityId) return;
    const normalizedRarityId = String(rarityId).trim();
    if (!normalizedRarityId) return;
    const normalizedConfig = normalizeElementGroupBonus(rawValue);
    if (!normalizedConfig) return;
    result.set(normalizedRarityId, normalizedConfig);
  });
  return result;
})();

const MYTHIQUE_RARITY_ID = 'mythique';
const COMPACT_COLLECTION_RARITIES = new Set([
  'essentiel',
  'stellaire',
  'singulier',
  MYTHIQUE_RARITY_ID
]);
const RAW_MYTHIQUE_GROUP_CONFIG = (() => {
  const raw = RAW_ELEMENT_GROUP_BONUS_GROUPS[MYTHIQUE_RARITY_ID];
  return raw && typeof raw === 'object' ? raw : null;
})();

if (!ELEMENT_GROUP_BONUS_CONFIG.has(MYTHIQUE_RARITY_ID)) {
  const fallback = {};
  if (RAW_MYTHIQUE_GROUP_CONFIG?.labels && typeof RAW_MYTHIQUE_GROUP_CONFIG.labels === 'object') {
    fallback.labels = {};
    Object.entries(RAW_MYTHIQUE_GROUP_CONFIG.labels).forEach(([key, value]) => {
      if (typeof value === 'string' && value.trim()) {
        fallback.labels[key] = value.trim();
      }
    });
  }
  ELEMENT_GROUP_BONUS_CONFIG.set(MYTHIQUE_RARITY_ID, fallback);
} else if (RAW_MYTHIQUE_GROUP_CONFIG?.labels && typeof RAW_MYTHIQUE_GROUP_CONFIG.labels === 'object') {
  const entry = ELEMENT_GROUP_BONUS_CONFIG.get(MYTHIQUE_RARITY_ID);
  if (entry) {
    entry.labels = { ...(entry.labels || {}) };
    Object.entries(RAW_MYTHIQUE_GROUP_CONFIG.labels).forEach(([key, value]) => {
      if (typeof value === 'string' && value.trim()) {
        entry.labels[key] = value.trim();
      }
    });
  }
}

function normalizeElementFamilyBonusEntry(raw, { familyId, defaultLabel, index }) {
  if (raw == null) {
    return null;
  }
  let entry = raw;
  if (typeof raw === 'string') {
    const note = raw.trim();
    if (!note) {
      return null;
    }
    entry = { notes: [note] };
  }
  if (typeof entry !== 'object') {
    return null;
  }

  const label = normalizeLabel(entry.label ?? entry.name ?? defaultLabel) || defaultLabel;
  const idBase = typeof entry.id === 'string' && entry.id.trim()
    ? entry.id.trim()
    : `${familyId}:bonus:${index + 1}`;

  const effectSource = entry.effects && typeof entry.effects === 'object'
    ? entry.effects
    : entry;

  const effects = {};
  const clickAdd = coerceFiniteNumber(
    readNumberProperty(effectSource, ['clickAdd', 'apc', 'perClick', 'manual', 'click'])
  , { allowZero: false });
  if (clickAdd != null) {
    effects.clickAdd = clickAdd;
  }
  const autoAdd = coerceFiniteNumber(
    readNumberProperty(effectSource, ['autoAdd', 'aps', 'perSecond', 'auto', 'automatic'])
  , { allowZero: false });
  if (autoAdd != null) {
    effects.autoAdd = autoAdd;
  }
  const critSource = effectSource.crit && typeof effectSource.crit === 'object' ? effectSource.crit : null;
  const readCrit = (source, keys, target, options = {}) => {
    if (effects[target] != null) {
      return;
    }
    const value = coerceFiniteNumber(readNumberProperty(source, keys), options);
    if (value != null) {
      effects[target] = value;
    }
  };

  readCrit(effectSource, ['critChanceAdd', 'critChancePlus', 'critChance'], 'critChanceAdd', { allowZero: false });
  readCrit(effectSource, ['critChanceMult', 'critChanceMultiplier'], 'critChanceMult', { allowZero: false, positiveOnly: true });
  readCrit(effectSource, ['critChanceSet', 'critChanceFixed'], 'critChanceSet', { allowZero: false, positiveOnly: true });
  if (critSource) {
    readCrit(critSource, ['chanceAdd', 'add', 'bonus'], 'critChanceAdd', { allowZero: false });
    readCrit(critSource, ['chanceMult', 'multiplier'], 'critChanceMult', { allowZero: false, positiveOnly: true });
    readCrit(critSource, ['chanceSet', 'set'], 'critChanceSet', { allowZero: false, positiveOnly: true });
  }

  readCrit(effectSource, ['critMultiplierAdd', 'critPowerAdd'], 'critMultiplierAdd', { allowZero: false });
  readCrit(effectSource, ['critMultiplierMult', 'critPowerMult'], 'critMultiplierMult', { allowZero: false, positiveOnly: true });
  readCrit(effectSource, ['critMultiplierSet', 'critPowerSet'], 'critMultiplierSet', { allowZero: false, positiveOnly: true });
  if (critSource) {
    readCrit(critSource, ['multiplierAdd', 'powerAdd'], 'critMultiplierAdd', { allowZero: false });
    readCrit(critSource, ['multiplierMult', 'powerMult'], 'critMultiplierMult', { allowZero: false, positiveOnly: true });
    readCrit(critSource, ['multiplierSet', 'powerSet'], 'critMultiplierSet', { allowZero: false, positiveOnly: true });
  }

  readCrit(effectSource, ['critMaxMultiplierAdd', 'critCapAdd'], 'critMaxMultiplierAdd', { allowZero: false });
  readCrit(effectSource, ['critMaxMultiplierMult', 'critCapMult'], 'critMaxMultiplierMult', { allowZero: false, positiveOnly: true });
  readCrit(effectSource, ['critMaxMultiplierSet', 'critCapSet'], 'critMaxMultiplierSet', { allowZero: false, positiveOnly: true });
  if (critSource) {
    readCrit(critSource, ['maxMultiplierAdd', 'capAdd'], 'critMaxMultiplierAdd', { allowZero: false });
    readCrit(critSource, ['maxMultiplierMult', 'capMult'], 'critMaxMultiplierMult', { allowZero: false, positiveOnly: true });
    readCrit(critSource, ['maxMultiplierSet', 'capSet'], 'critMaxMultiplierSet', { allowZero: false, positiveOnly: true });
  }

  if (critSource && !effects.crit) {
    const crit = {};
    const pushCritValue = (target, keys, options) => {
      if (crit[target] != null) {
        return;
      }
      const value = coerceFiniteNumber(readNumberProperty(critSource, keys), options);
      if (value != null) {
        crit[target] = value;
      }
    };
    pushCritValue('chanceAdd', ['chanceAdd', 'add', 'bonus'], { allowZero: false });
    pushCritValue('chanceMult', ['chanceMult', 'multiplier'], { allowZero: false, positiveOnly: true });
    pushCritValue('chanceSet', ['chanceSet', 'set'], { allowZero: false, positiveOnly: true });
    pushCritValue('multiplierAdd', ['multiplierAdd', 'powerAdd'], { allowZero: false });
    pushCritValue('multiplierMult', ['multiplierMult', 'powerMult'], { allowZero: false, positiveOnly: true });
    pushCritValue('multiplierSet', ['multiplierSet', 'powerSet'], { allowZero: false, positiveOnly: true });
    pushCritValue('maxMultiplierAdd', ['maxMultiplierAdd', 'capAdd'], { allowZero: false });
    pushCritValue('maxMultiplierMult', ['maxMultiplierMult', 'capMult'], { allowZero: false, positiveOnly: true });
    pushCritValue('maxMultiplierSet', ['maxMultiplierSet', 'capSet'], { allowZero: false, positiveOnly: true });
    if (Object.keys(crit).length > 0) {
      effects.crit = crit;
    }
  }

  if (Object.keys(effects).length === 0) {
    return null;
  }

  const notes = [];
  const appendNote = value => {
    if (typeof value !== 'string') {
      return;
    }
    const trimmed = value.trim();
    if (!trimmed) {
      return;
    }
    if (!notes.includes(trimmed)) {
      notes.push(trimmed);
    }
  };
  if (Array.isArray(entry.notes)) {
    entry.notes.forEach(appendNote);
  }
  if (typeof entry.note === 'string') {
    appendNote(entry.note);
  }

  const description = typeof entry.description === 'string' && entry.description.trim()
    ? entry.description.trim()
    : null;

  return {
    id: idBase,
    label,
    description,
    notes,
    effects
  };
}

function normalizeElementFamilyConfig(raw, familyId) {
  if (raw == null) {
    return null;
  }
  const defaultLabel = CATEGORY_LABELS[familyId] || familyId;
  let label = translateElementLabel(raw.label ?? raw.name) || defaultLabel;
  let bonusEntries;
  if (Array.isArray(raw)) {
    bonusEntries = raw;
  } else if (Array.isArray(raw.bonuses)) {
    bonusEntries = raw.bonuses;
  } else if (raw.bonus != null) {
    bonusEntries = [raw.bonus];
  } else if (Array.isArray(raw.effects)) {
    bonusEntries = raw.effects;
  } else {
    bonusEntries = [];
  }

  const bonuses = [];
  bonusEntries.forEach((entry, index) => {
    const normalized = normalizeElementFamilyBonusEntry(entry, { familyId, defaultLabel: label, index });
    if (normalized) {
      bonuses.push(normalized);
    }
  });

  if (bonuses.length === 0) {
    return null;
  }

  return {
    familyId,
    label,
    bonuses
  };
}

const RAW_ELEMENT_FAMILY_CONFIG = (() => {
  const raw = CONFIG.elementFamilies
    ?? CONFIG.elementFamilyBonuses
    ?? CONFIG.families;
  return raw && typeof raw === 'object' ? raw : null;
})();

const ELEMENT_FAMILY_CONFIG = (() => {
  const result = new Map();
  if (!RAW_ELEMENT_FAMILY_CONFIG) {
    return result;
  }
  Object.entries(RAW_ELEMENT_FAMILY_CONFIG).forEach(([familyId, rawValue]) => {
    if (!familyId) {
      return;
    }
    const normalizedId = String(familyId).trim();
    if (!normalizedId) {
      return;
    }
    const normalized = normalizeElementFamilyConfig(rawValue, normalizedId);
    if (!normalized) {
      return;
    }
    result.set(normalizedId, normalized);
  });
  return result;
})();

function pickDefined(...candidates) {
  for (const candidate of candidates) {
    if (candidate !== undefined && candidate !== null) {
      return candidate;
    }
  }
  return undefined;
}

function clampNumber(value, fallback, { min = -Infinity, max = Infinity } = {}) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  let result = numeric;
  if (Number.isFinite(min) && result < min) {
    result = min;
  }
  if (Number.isFinite(max) && result > max) {
    result = max;
  }
  return result;
}

const OFFLINE_TICKET_DEFAULTS = {
  secondsPerTicket: 60 * 15,
  capSeconds: 60 * 60 * 24
};

function normalizeOfflineTicketConfig(rawConfig) {
  const config = { ...OFFLINE_TICKET_DEFAULTS };
  if (rawConfig == null) {
    return config;
  }

  if (typeof rawConfig === 'number') {
    const interval = Number(rawConfig);
    if (Number.isFinite(interval) && interval > 0) {
      config.secondsPerTicket = interval;
    }
    return config;
  }

  if (typeof rawConfig === 'object') {
    const intervalCandidate = pickDefined(
      rawConfig.secondsPerTicket,
      rawConfig.intervalSeconds,
      rawConfig.ticketIntervalSeconds,
      rawConfig.perTicketSeconds,
      rawConfig.interval,
      rawConfig.seconds,
      rawConfig.value
    );
    const interval = Number(intervalCandidate);
    if (Number.isFinite(interval) && interval > 0) {
      config.secondsPerTicket = interval;
    }

    const capCandidate = pickDefined(
      rawConfig.capSeconds,
      rawConfig.maximumSeconds,
      rawConfig.maxSeconds,
      rawConfig.cap,
      rawConfig.maximum,
      rawConfig.max
    );
    const cap = Number(capCandidate);
    if (Number.isFinite(cap) && cap > 0) {
      config.capSeconds = cap;
    }
  }

  if (config.capSeconds <= 0) {
    config.capSeconds = config.secondsPerTicket;
  } else if (config.capSeconds < config.secondsPerTicket) {
    config.capSeconds = config.secondsPerTicket;
  }

  return config;
}

const MYTHIQUE_BONUS_DEFAULTS = {
  ticket: {
    uniqueReductionSeconds: 1,
    minIntervalSeconds: 5
  },
  offline: {
    baseMultiplier: 0.01,
    perDuplicate: 0.01,
    cap: 1
  },
  overflow: {
    flatBonus: 50
  },
  frenzy: {
    multiplier: 1.5
  }
};

function normalizeMythiqueBonusConfig(rawConfig) {
  const config = {
    ticket: { ...MYTHIQUE_BONUS_DEFAULTS.ticket },
    offline: { ...MYTHIQUE_BONUS_DEFAULTS.offline },
    overflow: { ...MYTHIQUE_BONUS_DEFAULTS.overflow },
    frenzy: { ...MYTHIQUE_BONUS_DEFAULTS.frenzy }
  };
  if (!rawConfig || typeof rawConfig !== 'object') {
    return config;
  }

  const ticketCandidate = pickDefined(
    rawConfig.ticketBonus,
    rawConfig.ticket,
    rawConfig.specialBonuses?.ticketBonus,
    rawConfig.specialBonuses?.ticket,
    rawConfig.specials?.ticketBonus,
    rawConfig.specials?.ticket,
    rawConfig.bonusConfig?.ticketBonus,
    rawConfig.bonusConfig?.ticket
  );
  if (ticketCandidate !== undefined) {
    if (ticketCandidate && typeof ticketCandidate === 'object') {
      const reduction = clampNumber(
        ticketCandidate.uniqueReductionSeconds
          ?? ticketCandidate.reductionPerUnique
          ?? ticketCandidate.perUnique
          ?? ticketCandidate.reduction,
        config.ticket.uniqueReductionSeconds,
        { min: 0 }
      );
      const minInterval = clampNumber(
        ticketCandidate.minIntervalSeconds
          ?? ticketCandidate.minSeconds
          ?? ticketCandidate.minimum
          ?? ticketCandidate.minimumSeconds,
        config.ticket.minIntervalSeconds,
        { min: 1 }
      );
      config.ticket.uniqueReductionSeconds = reduction;
      config.ticket.minIntervalSeconds = minInterval;
    } else {
      const minInterval = clampNumber(ticketCandidate, config.ticket.minIntervalSeconds, { min: 1 });
      config.ticket.minIntervalSeconds = minInterval;
    }
  }

  const offlineCandidate = pickDefined(
    rawConfig.offlineBonus,
    rawConfig.offline,
    rawConfig.specialBonuses?.offlineBonus,
    rawConfig.specialBonuses?.offline,
    rawConfig.specials?.offlineBonus,
    rawConfig.specials?.offline,
    rawConfig.bonusConfig?.offlineBonus,
    rawConfig.bonusConfig?.offline
  );
  if (offlineCandidate !== undefined && offlineCandidate !== null) {
    if (typeof offlineCandidate === 'object') {
      const baseMultiplier = clampNumber(
        offlineCandidate.baseMultiplier
          ?? offlineCandidate.base
          ?? offlineCandidate.minimum
          ?? offlineCandidate.min,
        config.offline.baseMultiplier,
        { min: 0 }
      );
      const perDuplicate = clampNumber(
        offlineCandidate.perDuplicate
          ?? offlineCandidate.increment
          ?? offlineCandidate.step
          ?? offlineCandidate.perCopy,
        config.offline.perDuplicate,
        { min: 0 }
      );
      const cap = clampNumber(
        offlineCandidate.cap
          ?? offlineCandidate.maximum
          ?? offlineCandidate.max,
        config.offline.cap,
        { min: baseMultiplier }
      );
      config.offline.baseMultiplier = Math.min(baseMultiplier, cap);
      config.offline.perDuplicate = perDuplicate;
      config.offline.cap = Math.max(cap, config.offline.baseMultiplier);
    } else {
      const perDuplicate = clampNumber(offlineCandidate, config.offline.perDuplicate, { min: 0 });
      config.offline.perDuplicate = perDuplicate;
    }
  }

  const overflowCandidate = pickDefined(
    rawConfig.duplicateOverflow,
    rawConfig.overflow,
    rawConfig.specialBonuses?.duplicateOverflow,
    rawConfig.specialBonuses?.overflow,
    rawConfig.specials?.duplicateOverflow,
    rawConfig.specials?.overflow,
    rawConfig.bonusConfig?.duplicateOverflow,
    rawConfig.bonusConfig?.overflow
  );
  if (overflowCandidate !== undefined && overflowCandidate !== null) {
    if (typeof overflowCandidate === 'object') {
      const flatBonus = clampNumber(
        overflowCandidate.flatBonus
          ?? overflowCandidate.flat
          ?? overflowCandidate.amount,
        config.overflow.flatBonus,
        { min: 0 }
      );
      config.overflow.flatBonus = flatBonus;
    } else {
      const flatBonus = clampNumber(overflowCandidate, config.overflow.flatBonus, { min: 0 });
      config.overflow.flatBonus = flatBonus;
    }
  }

  const frenzyCandidate = pickDefined(
    rawConfig.frenzyBonus,
    rawConfig.frenzy,
    rawConfig.specialBonuses?.frenzyBonus,
    rawConfig.specialBonuses?.frenzy,
    rawConfig.specials?.frenzyBonus,
    rawConfig.specials?.frenzy,
    rawConfig.bonusConfig?.frenzyBonus,
    rawConfig.bonusConfig?.frenzy
  );
  if (frenzyCandidate !== undefined && frenzyCandidate !== null) {
    if (typeof frenzyCandidate === 'object') {
      const multiplier = clampNumber(
        frenzyCandidate.multiplier
          ?? frenzyCandidate.value
          ?? frenzyCandidate.amount,
        config.frenzy.multiplier,
        { min: 1 }
      );
      config.frenzy.multiplier = multiplier;
    } else {
      const multiplier = clampNumber(frenzyCandidate, config.frenzy.multiplier, { min: 1 });
      config.frenzy.multiplier = multiplier;
    }
  }

  return config;
}

const MYTHIQUE_SPECIAL_BONUS_CONFIG = normalizeMythiqueBonusConfig(RAW_MYTHIQUE_GROUP_CONFIG);

const ELEMENT_FAMILY_POOLS = (() => {
  const pools = new Map();
  periodicElements.forEach(def => {
    const familyId = typeof def.category === 'string' ? def.category.trim() : '';
    if (!familyId) {
      return;
    }
    if (!pools.has(familyId)) {
      pools.set(familyId, {
        elementIds: [],
        get label() {
          return CATEGORY_LABELS[familyId] || formatCategoryFallbackLabel(familyId);
        }
      });
    }
    pools.get(familyId).elementIds.push(def.id);
  });
  return pools;
})();

function getFamilyPoolSize(familyId) {
  if (!familyId) {
    return 0;
  }
  const pool = ELEMENT_FAMILY_POOLS.get(familyId);
  if (!pool) {
    return 0;
  }
  return Array.isArray(pool.elementIds) ? pool.elementIds.length : 0;
}

function createInitialElementCollection() {
  const collection = {};
  periodicElements.forEach(def => {
    const gachaId = def.gachaId ?? def.id;
    const atomicNumber = Number(def.atomicNumber);
    const configEntry = Number.isFinite(atomicNumber)
      ? elementConfigByAtomicNumber.get(atomicNumber)
      : null;
    const rarity = elementRarityIndex.get(def.id) || null;
    const effects = Array.isArray(configEntry?.effects)
      ? [...configEntry.effects]
      : [];
    const bonuses = [];
    const bonusValue = configEntry?.bonus;
    if (Array.isArray(bonusValue)) {
      bonusValue.forEach(item => {
        if (typeof item === 'string' && item.trim()) {
          bonuses.push(item.trim());
        }
      });
    } else if (typeof bonusValue === 'string' && bonusValue.trim()) {
      bonuses.push(bonusValue.trim());
    }
    collection[def.id] = {
      id: def.id,
      gachaId,
      owned: false,
      count: 0,
      lifetime: 0,
      rarity,
      effects,
      bonuses
    };
  });
  return collection;
}

function createInitialGachaCardCollection() {
  const collection = {};
  GACHA_SPECIAL_CARD_DEFINITIONS.forEach(def => {
    if (!def || !def.id) {
      return;
    }
    collection[def.id] = { id: def.id, count: 0 };
  });
  return collection;
}

function createInitialGachaImageCollection() {
  const collection = {};
  GACHA_BONUS_IMAGE_DEFINITIONS.forEach(def => {
    if (!def || !def.id) {
      return;
    }
    collection[def.id] = { id: def.id, count: 0 };
  });
  return collection;
}

function createInitialFusionState() {
  const state = {};
  FUSION_DEFS.forEach(def => {
    state[def.id] = { attempts: 0, successes: 0 };
  });
  return state;
}

function createInitialFusionBonuses() {
  return { apcFlat: 0, apsFlat: 0 };
}

function createInitialPageUnlockState() {
  return {
    gacha: false,
    tableau: false,
    fusion: false,
    info: false,
    collection: false
  };
}

function getFusionSuccessCount(fusionId) {
  if (!fusionId) {
    return 0;
  }
  const entry = gameState.fusions && typeof gameState.fusions === 'object'
    ? gameState.fusions[fusionId]
    : null;
  const successes = Number(entry?.successes);
  if (Number.isFinite(successes) && successes > 0) {
    return Math.floor(successes);
  }
  return 0;
}

function getElementCurrentCount(entry) {
  if (!entry) return 0;
  const rawCount = Number(entry.count);
  if (Number.isFinite(rawCount) && rawCount > 0) {
    return Math.floor(rawCount);
  }
  return 0;
}

function getElementLifetimeCount(entry) {
  if (!entry) return 0;
  const rawLifetime = Number(entry.lifetime);
  if (Number.isFinite(rawLifetime) && rawLifetime > 0) {
    return Math.floor(rawLifetime);
  }
  const fallbackCount = getElementCurrentCount(entry);
  if (fallbackCount > 0) {
    return fallbackCount;
  }
  return entry.owned ? 1 : 0;
}

function hasElementLifetime(entry) {
  return getElementLifetimeCount(entry) > 0;
}

function toLayeredNumber(value, fallback = 0) {
  if (value instanceof LayeredNumber) return value.clone();
  if (value == null) return new LayeredNumber(fallback);
  if (typeof value === 'number') return new LayeredNumber(value);
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) {
      return new LayeredNumber(fallback);
    }
    const numeric = Number(trimmed);
    if (Number.isFinite(numeric)) {
      return new LayeredNumber(numeric);
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (parsed === value) {
        return new LayeredNumber(fallback);
      }
      return toLayeredNumber(parsed, fallback);
    } catch (error) {
      return new LayeredNumber(fallback);
    }
  }
  if (typeof value === 'object') {
    if (value.type === 'number') return new LayeredNumber(value.value ?? fallback);
    if (value.type === 'layer0') {
      const mantissa = value.mantissa ?? value.value ?? fallback;
      const exponent = value.exponent ?? 0;
      const sign = value.sign ?? 1;
      return LayeredNumber.fromLayer0(mantissa, exponent, sign);
    }
    if (value.type === 'layer1') {
      const val = value.value ?? fallback;
      const sign = value.sign ?? 1;
      return LayeredNumber.fromLayer1(val, sign);
    }
    if (value.type === 'json' && value.value) {
      return LayeredNumber.fromJSON(value.value);
    }
    if ('layer' in value || 'mantissa' in value || 'exponent' in value || 'value' in value || 'sign' in value) {
      return LayeredNumber.fromJSON(value);
    }
  }
  return new LayeredNumber(fallback);
}

