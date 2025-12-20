function getHoldemBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.atom2universHoldem;
  if (!bridge || typeof bridge !== 'object') {
    return null;
  }
  return bridge;
}

function formatHoldemOptionValue(value) {
  let normalized = value;
  if (!(normalized instanceof LayeredNumber)) {
    const numeric = Number(normalized);
    normalized = Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0;
  }

  if (typeof formatLayeredLocalized === 'function') {
    const formatted = formatLayeredLocalized(normalized, {
      mantissaDigits: 2,
      numberFormatOptions: HOLDEM_NUMBER_FORMAT
    });
    if (typeof formatted === 'string' && formatted.length > 0) {
      return formatted;
    }
  }

  if (normalized instanceof LayeredNumber) {
    return normalized.toString();
  }

  return formatNumberLocalized(normalized, HOLDEM_NUMBER_FORMAT);
}

function updateHoldemBlindOption(blind) {
  if (!elements.holdemBlindValue) {
    return;
  }
  const resolveBlindValue = source => {
    if (typeof LayeredNumber === 'function') {
      try {
        const layered = source instanceof LayeredNumber
          ? (typeof source.clone === 'function' ? source.clone() : new LayeredNumber(source))
          : new LayeredNumber(source);
        if (layered && layered.sign > 0 && !layered.isZero()) {
          if (layered.layer === 0) {
            const numeric = layered.toNumber();
            if (Number.isFinite(numeric)) {
              return Math.max(1, Math.floor(numeric));
            }
          }
          return layered;
        }
      } catch (error) {
        // Ignore invalid layered values and fall back to numeric parsing.
      }
    }
    const numeric = Number(source);
    if (Number.isFinite(numeric) && numeric > 0) {
      return Math.max(1, Math.floor(numeric));
    }
    return null;
  };

  let resolved = resolveBlindValue(blind);
  if (!resolved) {
    const bridge = getHoldemBridge();
    if (bridge && typeof bridge.getBlind === 'function') {
      try {
        resolved = resolveBlindValue(bridge.getBlind());
      } catch (error) {
        resolved = null;
      }
    }
  }

  if (resolved) {
    elements.holdemBlindValue.textContent = formatHoldemOptionValue(resolved);
  } else {
    elements.holdemBlindValue.textContent = '—';
  }
}

function handleHoldemRestartRequest() {
  const bridge = getHoldemBridge();
  if (!bridge) {
    showToast(t('scripts.app.holdemOptions.restartFailure', 'Hold’em table unavailable.'));
    return;
  }
  const action = typeof bridge.restart === 'function'
    ? bridge.restart
    : typeof bridge.wipeOpponents === 'function'
      ? bridge.wipeOpponents
      : null;
  if (!action) {
    showToast(t('scripts.app.holdemOptions.restartFailure', 'Hold’em table unavailable.'));
    return;
  }
  try {
    const result = action();
    if (result && result.success) {
      updateHoldemBlindOption(result.blind);
      const stackLabel = formatHoldemOptionValue(result.stack);
      showToast(t('scripts.app.holdemOptions.restartSuccess', { stack: stackLabel }));
      return;
    }
  } catch (error) {
    console.error('Unable to restart Hold’em table', error);
  }
  showToast(t('scripts.app.holdemOptions.restartFailure', 'Hold’em table unavailable.'));
}

function handleHoldemBlindScaling(factor) {
  const bridge = getHoldemBridge();
  if (!bridge || typeof bridge.scaleBlind !== 'function') {
    showToast(t('scripts.app.holdemOptions.blindUnavailable', 'Unable to adjust the blind right now.'));
    return;
  }
  try {
    const result = bridge.scaleBlind(factor);
    if (result && result.success) {
      updateHoldemBlindOption(result.blind);
      const blindLabel = formatHoldemOptionValue(result.blind);
      showToast(t('scripts.app.holdemOptions.blindUpdated', { blind: blindLabel }));
      return;
    }
  } catch (error) {
    console.error('Unable to adjust Hold’em blind', error);
  }
  showToast(t('scripts.app.holdemOptions.blindUnavailable', 'Unable to adjust the blind right now.'));
}

function initializeHoldemOptionsUI() {
  updateHoldemBlindOption();
  if (!holdemBlindListenerAttached && typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('holdem:blindChange', event => {
      const detail = event && event.detail ? event.detail.blind : undefined;
      updateHoldemBlindOption(detail);
    });
    window.addEventListener('holdem:gameRestart', event => {
      const detail = event && event.detail ? event.detail.blind : undefined;
      updateHoldemBlindOption(detail);
    });
    holdemBlindListenerAttached = true;
  }
}

function subscribeHoldemOptionsLanguageUpdates() {
  const handler = () => {
    updateHoldemBlindOption();
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

function formatLayeredLocalized(value, options = {}) {
  const numberOptions = options.numberFormatOptions || {
    maximumFractionDigits: 0,
    minimumFractionDigits: 0
  };
  const mantissaDigits = Number.isFinite(options.mantissaDigits)
    ? Math.min(4, Math.max(0, Math.floor(options.mantissaDigits)))
    : 1;

  const formatSmall = numeric => formatNumberLocalized(numeric, numberOptions);

  const toLayered = input => {
    if (input instanceof LayeredNumber) {
      return input;
    }
    if (typeof LayeredNumber === 'function') {
      try {
        return new LayeredNumber(input);
      } catch (error) {
        return null;
      }
    }
    return null;
  };

  const layered = toLayered(value);

  if (layered) {
    if (layered.sign === 0) {
      return formatSmall(0);
    }
    if (layered.layer === 0) {
      if (Math.abs(layered.exponent) < 6) {
        const numeric = layered.sign * layered.mantissa * Math.pow(10, layered.exponent);
        return formatSmall(numeric);
      }
      const mantissa = layered.sign * layered.mantissa;
      return `${mantissa.toFixed(mantissaDigits)}e${layered.exponent}`;
    }
    if (layered.layer === 1) {
      const exponent = Math.floor(layered.value);
      const fractional = layered.value - exponent;
      const mantissa = layered.sign * Math.pow(10, fractional);
      return `${mantissa.toFixed(mantissaDigits)}e${exponent}`;
    }
    return layered.toString();
  }

  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return formatSmall(0);
  }
  if (Math.abs(numeric) >= 1e6) {
    const exponent = Math.floor(Math.log10(Math.abs(numeric)));
    const mantissa = numeric / Math.pow(10, exponent);
    return `${mantissa.toFixed(mantissaDigits)}e${exponent}`;
  }
  return formatSmall(numeric);
}

function freezeFeatureUnlockDefinition(definition) {
  if (!definition || typeof definition !== 'object') {
    return Object.freeze({ type: 'always' });
  }
  const normalized = { ...definition };
  if (Array.isArray(normalized.requires)) {
    normalized.requires = Object.freeze([...normalized.requires]);
  }
  return Object.freeze(normalized);
}

function createDefaultFeatureUnlockDefinitions() {
  const defaults = new Map();
  [
    'arcade.hub',
    'arcade.particules',
    METAL_FEATURE_ID,
    'arcade.photon',
    'arcade.objectx',
    'arcade.balance',
    'arcade.math',
    'arcade.sudoku',
    'arcade.demineur',
    'arcade.solitaire',
    'arcade.holdem',
    'arcade.dice',
    'arcade.blackjack',
    'arcade.echecs',
    'arcade.starBridges',
    'arcade.pipeTap',
    'arcade.jumpingCat',
    'arcade.twins',
    'arcade.gameOfLife'
  ].forEach(id => {
    defaults.set(id, freezeFeatureUnlockDefinition({ type: 'always' }));
  });
  defaults.set('system.gacha', freezeFeatureUnlockDefinition({ type: 'page', pageId: 'gacha' }));
  defaults.set('system.tableau', freezeFeatureUnlockDefinition({ type: 'page', pageId: 'tableau' }));
  defaults.set('system.fusion', freezeFeatureUnlockDefinition({ type: 'page', pageId: 'fusion' }));
  defaults.set('system.musique', freezeFeatureUnlockDefinition({ type: 'always' }));
  return defaults;
}

function normalizeFeatureUnlockCondition(featureId, rawEntry) {
  if (rawEntry == null) {
    return null;
  }
  if (typeof rawEntry === 'number' || typeof rawEntry === 'string') {
    return freezeFeatureUnlockDefinition({
      type: 'lifetimeAtoms',
      amount: toLayeredNumber(rawEntry, 0)
    });
  }
  if (typeof rawEntry !== 'object') {
    return null;
  }
  const typeCandidate = rawEntry.type ?? rawEntry.kind ?? rawEntry.mode;
  let type = typeof typeCandidate === 'string' ? typeCandidate.trim().toLowerCase() : '';
  if (!type) {
    if (rawEntry.amount != null || rawEntry.value != null || rawEntry.threshold != null) {
      type = 'lifetimeatoms';
    } else if (rawEntry.trophyId || rawEntry.trophy) {
      type = 'trophy';
    } else if (rawEntry.pageId || rawEntry.page) {
      type = 'page';
    } else if (rawEntry.requires || rawEntry.require || rawEntry.dependencies || rawEntry.dependency) {
      type = 'feature';
    } else if (rawEntry === true) {
      type = 'always';
    }
  }
  switch (type) {
    case 'always':
    case 'true':
    case 'unlocked':
      return freezeFeatureUnlockDefinition({ type: 'always' });
    case 'lifetimeatoms':
    case 'atoms':
    case 'lifetime':
    case 'threshold': {
      const amountSource =
        rawEntry.amount ?? rawEntry.value ?? rawEntry.threshold ?? rawEntry.required ?? rawEntry.target ?? 0;
      return freezeFeatureUnlockDefinition({
        type: 'lifetimeAtoms',
        amount: toLayeredNumber(amountSource, 0)
      });
    }
    case 'feature':
    case 'requires':
    case 'dependency':
    case 'dependencies': {
      const requiresSource =
        rawEntry.requires ?? rawEntry.require ?? rawEntry.dependencies ?? rawEntry.dependency ?? [];
      const requiresList = Array.isArray(requiresSource) ? requiresSource : [requiresSource];
      const normalizedRequires = requiresList
        .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
        .filter(Boolean);
      return freezeFeatureUnlockDefinition({ type: 'feature', requires: normalizedRequires });
    }
    case 'trophy':
    case 'achievement': {
      const trophyIdCandidate = rawEntry.trophyId ?? rawEntry.trophy ?? rawEntry.id ?? rawEntry.target;
      const trophyId = typeof trophyIdCandidate === 'string' ? trophyIdCandidate.trim() : '';
      if (!trophyId) {
        return null;
      }
      return freezeFeatureUnlockDefinition({ type: 'trophy', trophyId });
    }
    case 'page':
    case 'pageunlock':
    case 'pagestate': {
      const pageCandidate = rawEntry.pageId ?? rawEntry.page ?? rawEntry.id ?? rawEntry.target;
      const pageId = typeof pageCandidate === 'string' ? pageCandidate.trim() : '';
      if (!pageId) {
        return null;
      }
      return freezeFeatureUnlockDefinition({ type: 'page', pageId });
    }
    case 'flag':
    case 'featureflag':
    case 'flagunlock':
    case 'flagged': {
      const flagCandidate = rawEntry.flagId ?? rawEntry.flag ?? rawEntry.id ?? rawEntry.target ?? featureId;
      const flagId = typeof flagCandidate === 'string' ? flagCandidate.trim() : '';
      if (!flagId) {
        return null;
      }
      return freezeFeatureUnlockDefinition({ type: 'flag', flagId });
    }
    default:
      return null;
  }
}

function buildFeatureUnlockDefinitions(rawConfig) {
  const defaults = createDefaultFeatureUnlockDefinitions();
  if (!rawConfig || typeof rawConfig !== 'object') {
    return defaults;
  }
  const result = new Map(defaults);
  const addEntry = (featureId, entry) => {
    const id = typeof featureId === 'string' ? featureId.trim() : '';
    if (!id) {
      return;
    }
    const normalized = normalizeFeatureUnlockCondition(id, entry);
    if (normalized) {
      result.set(id, normalized);
    }
  };
  const processGroup = (group, prefix) => {
    if (!group || typeof group !== 'object') {
      return;
    }
    Object.entries(group).forEach(([key, value]) => {
      const keyId = typeof key === 'string' ? key.trim() : '';
      if (!keyId) {
        return;
      }
      const featureId = prefix ? `${prefix}.${keyId}` : keyId;
      addEntry(featureId, value);
    });
  };
  if (Array.isArray(rawConfig.entries)) {
    rawConfig.entries.forEach(entry => {
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const id = typeof entry.id === 'string' ? entry.id.trim() : '';
      if (!id) {
        return;
      }
      addEntry(id, entry);
    });
  }
  processGroup(rawConfig.arcade, 'arcade');
  processGroup(rawConfig.systems, 'system');
  if (rawConfig.features) {
    processGroup(rawConfig.features, '');
  }
  return result;
}

const FEATURE_UNLOCK_DEFINITIONS = buildFeatureUnlockDefinitions(
  GLOBAL_CONFIG?.progression?.featureUnlocks
);

function getFeatureUnlockDefinition(featureId) {
  if (typeof featureId !== 'string' || !featureId.trim()) {
    return null;
  }
  return FEATURE_UNLOCK_DEFINITIONS.get(featureId.trim()) || null;
}

function normalizeFeatureUnlockFlags(raw) {
  const flags = new Set();
  if (raw == null) {
    return flags;
  }
  if (Array.isArray(raw)) {
    raw.forEach(entry => {
      const id = typeof entry === 'string' ? entry.trim() : '';
      if (id) {
        flags.add(id);
      }
    });
    return flags;
  }
  if (typeof raw === 'string') {
    const id = raw.trim();
    if (id) {
      flags.add(id);
    }
    return flags;
  }
  if (typeof raw === 'object') {
    Object.entries(raw).forEach(([key, value]) => {
      const id = typeof key === 'string' ? key.trim() : '';
      if (!id) {
        return;
      }
      if (
        value === true
        || value === 'true'
        || value === 1
        || (typeof value === 'string' && value.trim().toLowerCase() === 'true')
      ) {
        flags.add(id);
      }
    });
  }
  return flags;
}

function normalizeUnlockedTrophies(raw) {
  const trophies = new Set();
  if (raw == null) {
    return trophies;
  }
  const addTrophyId = (value) => {
    if (typeof value !== 'string') {
      return;
    }
    const id = value.trim();
    if (id) {
      trophies.add(id);
    }
  };

  if (raw instanceof Set) {
    raw.forEach(addTrophyId);
    return trophies;
  }

  if (Array.isArray(raw)) {
    raw.forEach(entry => {
      if (typeof entry === 'string') {
        addTrophyId(entry);
        return;
      }
      if (entry && typeof entry === 'object') {
        addTrophyId(entry.id ?? entry.trophyId);
      }
    });
    return trophies;
  }

  if (raw && typeof raw === 'object') {
    Object.keys(raw).forEach(addTrophyId);
  }

  return trophies;
}

function ensureFeatureUnlockFlagSet() {
  if (gameState.featureUnlockFlags instanceof Set) {
    return gameState.featureUnlockFlags;
  }
  const raw = gameState.featureUnlockFlags;
  const flags = normalizeFeatureUnlockFlags(raw);
  gameState.featureUnlockFlags = flags;
  return flags;
}

function hasFeatureUnlockFlag(flagId) {
  const id = typeof flagId === 'string' ? flagId.trim() : '';
  if (!id) {
    return false;
  }
  return ensureFeatureUnlockFlagSet().has(id);
}

function setFeatureUnlockFlag(flagId) {
  const id = typeof flagId === 'string' ? flagId.trim() : '';
  if (!id) {
    return false;
  }
  const flags = ensureFeatureUnlockFlagSet();
  if (flags.has(id)) {
    return false;
  }
  flags.add(id);
  return true;
}

function applyDerivedFeatureUnlockFlags() {
  const flags = ensureFeatureUnlockFlagSet();
  let changed = false;
  if (!flags.has(METAL_FEATURE_ID)) {
    const credits = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
    const hasMetauxProgress = !!(
      gameState.arcadeProgress
      && gameState.arcadeProgress.entries
      && gameState.arcadeProgress.entries.metaux
    );
    if (credits > 0 || hasMetauxProgress) {
      flags.add(METAL_FEATURE_ID);
      changed = true;
    }
  }
  return changed;
}

function evaluateFeatureUnlockCondition(definition, stack = new Set()) {
  if (!definition || typeof definition !== 'object') {
    return true;
  }
  switch (definition.type) {
    case 'always':
      return true;
    case 'lifetimeAtoms': {
      const target = definition.amount instanceof LayeredNumber
        ? definition.amount
        : toLayeredNumber(definition.amount ?? 0, 0);
      const lifetime = gameState.lifetime instanceof LayeredNumber
        ? gameState.lifetime
        : toLayeredNumber(gameState.lifetime ?? 0, 0);
      return lifetime.compare(target) >= 0;
    }
    case 'feature': {
      const requires = Array.isArray(definition.requires) ? definition.requires : [];
      if (!requires.length) {
        return true;
      }
      return requires.every(dep => isFeatureUnlocked(dep, stack));
    }
    case 'trophy':
      return !!definition.trophyId && getUnlockedTrophySet().has(definition.trophyId);
    case 'page': {
      const pageId = typeof definition.pageId === 'string' ? definition.pageId : '';
      if (!pageId) {
        return false;
      }
      const unlocks = getPageUnlockState();
      return unlocks?.[pageId] === true;
    }
    case 'flag': {
      const flagId = typeof definition.flagId === 'string' ? definition.flagId.trim() : '';
      if (!flagId) {
        return false;
      }
      return hasFeatureUnlockFlag(flagId);
    }
    default:
      return false;
  }
}

function isFeatureUnlocked(featureId, stack = new Set()) {
  const id = typeof featureId === 'string' ? featureId.trim() : '';
  if (!id) {
    return true;
  }
  if (featureUnlockCache.has(id)) {
    return featureUnlockCache.get(id);
  }
  if (stack.has(id)) {
    return false;
  }
  stack.add(id);
  const definition = getFeatureUnlockDefinition(id);
  const unlocked = definition ? evaluateFeatureUnlockCondition(definition, stack) : true;
  stack.delete(id);
  featureUnlockCache.set(id, unlocked);
  return unlocked;
}

function invalidateFeatureUnlockCache(options = {}) {
  featureUnlockCache.clear();
  if (options.resetArcadeState) {
    lastArcadeUnlockState = null;
  }
}

function isOptionsDetailUnlocked(detailId) {
  const featureId = OPTIONS_DETAIL_FEATURE_MAP[detailId];
  if (!featureId) {
    return true;
  }
  return isFeatureUnlocked(featureId);
}

function createDetailId(candidate, label, fallbackIndex = 0) {
  if (typeof candidate === 'string') {
    const trimmed = candidate.trim();
    if (trimmed) {
      return trimmed;
    }
  }
  if (typeof label === 'string') {
    const slug = label
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    if (slug) {
      return slug;
    }
  }
  return `detail-${fallbackIndex}`;
}

function getOptionsDetailMetadata(override = {}) {
  if (cachedOptionsDetailMetadata && !override.forceRefresh) {
    return cachedOptionsDetailMetadata;
  }
  const copy = override.copy ?? getOptionsWelcomeCardCopy();
  const fallbackCopy = override.fallback ?? CONFIG_OPTIONS_WELCOME_CARD;
  const localizedDetails = extractWelcomeDetails(copy);
  const fallbackDetails = extractWelcomeDetails(fallbackCopy);
  const detailMap = new Map();
  fallbackDetails.forEach(detail => {
    if (detail && detail.id) {
      detailMap.set(detail.id, detail);
    }
  });
  localizedDetails.forEach(detail => {
    if (detail && detail.id) {
      detailMap.set(detail.id, detail);
    }
  });
  const detailOrder = localizedDetails.length
    ? localizedDetails.map(detail => detail.id)
    : fallbackDetails.map(detail => detail.id);
  cachedOptionsDetailMetadata = { detailMap, detailOrder };
  return cachedOptionsDetailMetadata;
}

function computeRenderableOptionDetailIds(detailOrder, detailMap) {
  const order = Array.isArray(detailOrder) ? detailOrder : [];
  const map = detailMap instanceof Map ? detailMap : new Map();
  const unlocked = [];
  const seen = new Set();
  order.forEach(id => {
    if (!id || seen.has(id) || !map.has(id)) {
      return;
    }
    if (isOptionsDetailUnlocked(id)) {
      unlocked.push(id);
    }
    seen.add(id);
  });
  Object.keys(OPTIONS_DETAIL_FEATURE_MAP).forEach(id => {
    if (seen.has(id) || !map.has(id)) {
      return;
    }
    if (isOptionsDetailUnlocked(id)) {
      unlocked.push(id);
    }
    seen.add(id);
  });
  return unlocked;
}

function getTrophyDisplayName(trophyId) {
  if (typeof trophyId !== 'string' || !trophyId.trim()) {
    return '';
  }
  const def = TROPHY_MAP.get(trophyId.trim());
  if (!def) {
    return trophyId.trim();
  }
  const texts = getTrophyDisplayTexts(def);
  if (texts.name && typeof texts.name === 'string' && texts.name.trim()) {
    return texts.name.trim();
  }
  if (typeof def.name === 'string' && def.name.trim()) {
    return def.name.trim();
  }
  return def.id || trophyId.trim();
}

function getFeatureLockedReason(featureId, visited = new Set()) {
  const id = typeof featureId === 'string' ? featureId.trim() : '';
  if (!id || visited.has(id)) {
    return '';
  }
  visited.add(id);
  const definition = getFeatureUnlockDefinition(id);
  if (!definition) {
    return '';
  }
  if (definition.type === 'lifetimeAtoms') {
    const amount = definition.amount instanceof LayeredNumber
      ? definition.amount
      : toLayeredNumber(definition.amount ?? 0, 0);
    const amountText = formatLayeredLocalized(amount, { mantissaDigits: 1 });
    return translateOrDefault(
      'index.sections.arcadeHub.locked.requiresAtoms',
      `Collectez ${amountText} atomes pour débloquer l’arcade.`,
      { amount: amountText }
    );
  }
  if (definition.type === 'trophy') {
    const trophyName = getTrophyDisplayName(definition.trophyId);
    return translateOrDefault(
      'index.sections.arcadeHub.locked.requiresTrophy',
      `Débloquez le trophée « ${trophyName} » pour accéder à ce mini-jeu.`,
      { trophy: trophyName }
    );
  }
  if (definition.type === 'flag' && definition.flagId === METAL_FEATURE_ID) {
    return translateOrDefault(
      'index.sections.arcadeHub.locked.requiresMach3',
      'Obtenez un ticket Mach3 pour débloquer ce mini-jeu.'
    );
  }
  if (definition.type === 'feature') {
    const requires = Array.isArray(definition.requires) ? definition.requires : [];
    for (const dependency of requires) {
      if (!isFeatureUnlocked(dependency)) {
        const nested = getFeatureLockedReason(dependency, visited);
        if (nested) {
          return nested;
        }
      }
    }
  }
  return translateOrDefault(
    'index.sections.arcadeHub.locked.default',
    'Débloquez ce mini-jeu pour y accéder.'
  );
}

function readStoredArcadeHubCardStates() {
  try {
    const raw = globalThis.localStorage?.getItem(ARCADE_HUB_CARD_STATE_STORAGE_KEY);
    if (typeof raw === 'string' && raw.trim()) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') {
        const normalized = {};
        Object.keys(parsed).forEach(key => {
          if (typeof key === 'string' && key.trim()) {
            normalized[key.trim()] = !!parsed[key];
          }
        });
        return normalized;
      }
    }
  } catch (error) {
    console.warn('Unable to read arcade hub card states', error);
  }
  return {};
}

function writeStoredArcadeHubCardStates(map) {
  const normalized = {};
  if (map && typeof map === 'object') {
    Object.keys(map).forEach(key => {
      if (typeof key === 'string') {
        const trimmed = key.trim();
        if (trimmed) {
          normalized[trimmed] = !!map[key];
        }
      }
    });
  }
  arcadeHubCardStateCache = normalized;
  try {
    globalThis.localStorage?.setItem(ARCADE_HUB_CARD_STATE_STORAGE_KEY, JSON.stringify(normalized));
  } catch (error) {
    console.warn('Unable to persist arcade hub card states', error);
  }
}

function getArcadeHubCardStateMap() {
  if (!arcadeHubCardStateCache) {
    arcadeHubCardStateCache = readStoredArcadeHubCardStates();
  }
  return arcadeHubCardStateCache;
}

function getArcadeHubCardId(card) {
  if (!card || !card.dataset) {
    return '';
  }
  if (typeof card.dataset.cardId === 'string' && card.dataset.cardId.trim()) {
    return card.dataset.cardId.trim();
  }
  if (typeof card.dataset.pageTarget === 'string' && card.dataset.pageTarget.trim()) {
    return card.dataset.pageTarget.trim();
  }
  return '';
}

function updateArcadeHubCardToggleLabel(toggleButton, collapsed) {
  if (!toggleButton) {
    return;
  }
  const labelKey = collapsed ? ARCADE_HUB_CARD_EXPAND_LABEL_KEY : ARCADE_HUB_CARD_COLLAPSE_LABEL_KEY;
  const fallback = collapsed ? 'Afficher la description' : 'Masquer la description';
  const label = translateOrDefault(labelKey, fallback);
  toggleButton.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
  toggleButton.setAttribute('aria-label', label);
  toggleButton.setAttribute('title', label);
  toggleButton.dataset.i18n = `aria-label:${labelKey};title:${labelKey}`;
  const hiddenLabel = toggleButton.querySelector('[data-role="arcade-hub-card-toggle-label"]');
  if (hiddenLabel) {
    hiddenLabel.textContent = label;
    hiddenLabel.dataset.i18n = labelKey;
  }
}

function applyArcadeHubCardCollapsedState(card, collapsed, options = {}) {
  if (!card) {
    return;
  }
  const { persist = true } = options;
  const normalized = !!collapsed;
  card.classList.toggle('arcade-hub-card--collapsed', normalized);
  card.setAttribute('aria-expanded', normalized ? 'false' : 'true');
  const description = card.querySelector('.arcade-hub-card__description');
  if (description) {
    description.hidden = normalized;
    description.setAttribute('aria-hidden', normalized ? 'true' : 'false');
  }
  const toggleButton = card.querySelector('.arcade-hub-card__toggle');
  if (toggleButton) {
    updateArcadeHubCardToggleLabel(toggleButton, normalized);
  }
  if (persist) {
    const cardId = getArcadeHubCardId(card);
    if (cardId) {
      const map = Object.assign({}, getArcadeHubCardStateMap());
      if (normalized) {
        map[cardId] = true;
      } else {
        delete map[cardId];
      }
      writeStoredArcadeHubCardStates(map);
    }
  }
}

function initializeArcadeHubCard(card) {
  if (!card) {
    return;
  }
  card.setAttribute('aria-expanded', 'true');
  const cardId = getArcadeHubCardId(card);
  const description = card.querySelector('.arcade-hub-card__description');
  if (description) {
    if (!description.id && cardId) {
      description.id = `arcadeCardDescription-${cardId}`;
    }
    if (description.id) {
      card.setAttribute('aria-describedby', description.id);
    }
    description.setAttribute('aria-hidden', 'false');
  }
  const toggleButton = card.querySelector('.arcade-hub-card__toggle');
  if (toggleButton) {
    toggleButton.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      const collapsed = card.classList.contains('arcade-hub-card--collapsed');
      applyArcadeHubCardCollapsedState(card, !collapsed);
    });
    toggleButton.addEventListener('keydown', event => {
      const key = event.key;
      if (key === ' ' || key === 'Enter') {
        event.preventDefault();
        event.stopPropagation();
        const collapsed = card.classList.contains('arcade-hub-card--collapsed');
        applyArcadeHubCardCollapsedState(card, !collapsed);
      }
    });
  }
  const stateMap = getArcadeHubCardStateMap();
  const initialCollapsed = cardId ? stateMap?.[cardId] === true : false;
  applyArcadeHubCardCollapsedState(card, initialCollapsed, { persist: false });
  card.addEventListener('pointerdown', event => {
    handleArcadeHubCardPointerDown(event, card);
  });
}

function isArcadeHubCardLocked(card) {
  if (!card) {
    return true;
  }
  return card.classList.contains('arcade-hub-card--locked');
}

function activateArcadeHubCard(card) {
  if (!card) {
    return;
  }
  if (isArcadeHubCardLocked(card)) {
    return;
  }
  const target = card.dataset?.pageTarget;
  if (!target || !isPageUnlocked(target)) {
    return;
  }
  if (target === 'wave') {
    ensureWaveGame();
  }
  if (target === 'quantum2048') {
    ensureQuantum2048Game();
  }
  if (target === 'sokoban') {
    ensureSokobanGame();
  }
  if (target === 'taquin') {
    ensureTaquinGame();
  }
  if (target === 'link') {
    ensureLinkGame();
  }
  if (target === 'lightsOut') {
    ensureLightsOutGame();
  }
  if (target === 'gameOfLife') {
    ensureGameOfLifeGame();
  }
  if (target === 'motocross') {
    ensureMotocrossGame();
  }
  if (target === 'twins') {
    ensureTwinsGame();
  }
  if (target === 'escape') {
    ensureEscapeGame();
  }
  showPage(target);
}

function getArcadeCardLockedMessage(pageId) {
  const featureId = PAGE_FEATURE_MAP[pageId];
  if (!featureId) {
    return translateOrDefault(
      'index.sections.arcadeHub.locked.default',
      'Débloquez ce mini-jeu pour y accéder.'
    );
  }
  if (isFeatureUnlocked(featureId)) {
    return '';
  }
  const reason = getFeatureLockedReason(featureId);
  return reason
    || translateOrDefault(
      'index.sections.arcadeHub.locked.default',
      'Débloquez ce mini-jeu pour y accéder.'
    );
}

function updateArcadeHubLocks() {
  if (!elements || !elements.arcadeHubCards?.length) {
    return;
  }
  elements.arcadeHubCards.forEach(card => {
    if (!card) {
      return;
    }
    const target = card.dataset?.pageTarget;
    if (!target) {
      return;
    }
    const featureId = PAGE_FEATURE_MAP[target];
    const unlocked = featureId ? isFeatureUnlocked(featureId) : true;
    const originalLabel = card.dataset.originalAriaLabel || card.getAttribute('aria-label') || '';
    if (!card.dataset.originalAriaLabel && originalLabel) {
      card.dataset.originalAriaLabel = originalLabel;
    }
    if (unlocked) {
      delete card.dataset.arcadeLocked;
      card.classList.remove('arcade-hub-card--locked');
      card.setAttribute('aria-disabled', 'false');
      card.setAttribute('tabindex', '0');
      card.removeAttribute('title');
      if (originalLabel) {
        card.setAttribute('aria-label', originalLabel);
      }
      return;
    }
    const hint = getArcadeCardLockedMessage(target);
    card.dataset.arcadeLocked = 'true';
    card.classList.add('arcade-hub-card--locked');
    card.setAttribute('aria-disabled', 'true');
    card.setAttribute('tabindex', '-1');
    if (hint) {
      card.title = hint;
      const combined = originalLabel ? `${originalLabel} — ${hint}` : hint;
      card.setAttribute('aria-label', combined);
    } else if (originalLabel) {
      card.removeAttribute('title');
      card.setAttribute('aria-label', originalLabel);
    } else {
      card.removeAttribute('title');
      card.removeAttribute('aria-label');
    }
  });
}

function formatDurationLocalized(value, options) {
  const api = getI18nApi();
  if (api && typeof api.formatDuration === 'function') {
    const formatted = api.formatDuration(value, options);
    if (formatted) {
      return formatted;
    }
  }
  return formatNumberLocalized(value, Object.assign({ style: 'unit', unit: 'second', unitDisplay: 'short' }, options));
}

function readPositiveNumber(candidates, transform) {
  if (!Array.isArray(candidates)) {
    return null;
  }
  for (let i = 0; i < candidates.length; i += 1) {
    const numeric = Number(candidates[i]);
    if (Number.isFinite(numeric) && numeric > 0) {
      return typeof transform === 'function' ? transform(numeric) : numeric;
    }
  }
  return null;
}

function cloneDefaultSudokuRewardLevels() {
  const defaultLevels = DEFAULT_SUDOKU_COMPLETION_REWARD.levels || {};
  return Object.keys(defaultLevels).reduce((acc, levelId) => {
    const level = defaultLevels[levelId] || {};
    acc[levelId] = {
      bonusSeconds: Number(level.bonusSeconds) || 0,
      multiplier: Number(level.multiplier) || 0,
      validSeconds: Number(level.validSeconds || level.bonusSeconds) || 0
    };
    return acc;
  }, {});
}

function normalizeSudokuRewardLevel(source, fallback) {
  const base = {
    bonusSeconds: Number(fallback?.bonusSeconds) || 0,
    multiplier: Number(fallback?.multiplier) || 0,
    validSeconds: Number(fallback?.validSeconds || fallback?.bonusSeconds) || 0
  };
  const data = source && typeof source === 'object' ? source : {};

  const bonusSeconds = readPositiveNumber([
    data.offlineBonusSeconds,
    data.bonusSeconds,
    data.durationSeconds,
    data.duration,
    data.secondsBonus
  ]);
  if (bonusSeconds) {
    base.bonusSeconds = bonusSeconds;
  } else {
    const bonusMinutes = readPositiveNumber([
      data.offlineBonusMinutes,
      data.bonusMinutes,
      data.durationMinutes
    ], value => value * 60);
    if (bonusMinutes) {
      base.bonusSeconds = bonusMinutes;
    } else {
      const bonusHours = readPositiveNumber([
        data.offlineBonusHours,
        data.bonusHours,
        data.durationHours
      ], value => value * 60 * 60);
      if (bonusHours) {
        base.bonusSeconds = bonusHours;
      }
    }
  }

  const multiplier = readPositiveNumber([
    data.offlineMultiplier,
    data.multiplier,
    data.value
  ]);
  if (multiplier) {
    base.multiplier = multiplier;
  }

  const validSeconds = readPositiveNumber([
    data.validSeconds,
    data.validDurationSeconds,
    data.validitySeconds,
    data.validSecondsRemaining
  ]);
  if (validSeconds) {
    base.validSeconds = validSeconds;
  } else {
    const validMinutes = readPositiveNumber([
      data.validMinutes,
      data.validDurationMinutes,
      data.validityMinutes
    ], value => value * 60);
    if (validMinutes) {
      base.validSeconds = validMinutes;
    } else {
      const validHours = readPositiveNumber([
        data.validHours,
        data.validDurationHours,
        data.validityHours
      ], value => value * 60 * 60);
      if (validHours) {
        base.validSeconds = validHours;
      }
    }
  }

  if (!Number.isFinite(base.validSeconds) || base.validSeconds <= 0) {
    base.validSeconds = base.bonusSeconds;
  }

  return base;
}

function normalizeSudokuRewardSettings(raw) {
  const config = {
    enabled: DEFAULT_SUDOKU_COMPLETION_REWARD.enabled !== false,
    levels: cloneDefaultSudokuRewardLevels()
  };

  if (raw === false) {
    config.enabled = false;
    return config;
  }

  const source = raw && typeof raw === 'object' ? raw : {};
  if (source.enabled === false) {
    config.enabled = false;
  }

  const levelSource = (() => {
    if (source.levels && typeof source.levels === 'object') {
      return source.levels;
    }
    if (source.offlineBonus && typeof source.offlineBonus === 'object') {
      return source.offlineBonus;
    }
    if (
      source.speedCompletion
      && typeof source.speedCompletion === 'object'
    ) {
      return source.speedCompletion;
    }
    if (Object.keys(source).some(key => typeof source[key] === 'object')) {
      return source;
    }
    return null;
  })();

  const fallbackLevel = config.levels.moyen
    || Object.values(config.levels)[0]
    || { bonusSeconds: 0, multiplier: 0, validSeconds: 0 };

  if (levelSource && typeof levelSource === 'object') {
    Object.entries(levelSource).forEach(([levelId, levelConfig]) => {
      const normalized = normalizeSudokuRewardLevel(levelConfig, config.levels[levelId] || fallbackLevel);
      if (normalized.bonusSeconds > 0 && normalized.multiplier > 0) {
        config.levels[levelId] = normalized;
      }
    });
  }

  const hasValidLevel = Object.values(config.levels).some(level => (
    Number.isFinite(level.bonusSeconds)
      && level.bonusSeconds > 0
      && Number.isFinite(level.multiplier)
      && level.multiplier > 0
      && Number.isFinite(level.validSeconds)
      && level.validSeconds > 0
  ));

  if (!hasValidLevel) {
    config.enabled = false;
  }

  return config;
}

function normalizeSudokuOfflineBonusState(raw) {
  if (!raw || typeof raw !== 'object') {
    return null;
  }

  const maxSeconds = readPositiveNumber([
    raw.maxSeconds,
    raw.seconds,
    raw.durationSeconds,
    raw.limitSeconds,
    raw.capSeconds
  ]);
  const multiplier = readPositiveNumber([
    raw.multiplier,
    raw.offlineMultiplier,
    raw.value
  ]);
  if (!maxSeconds || !multiplier) {
    return null;
  }

  const grantedAtCandidate = Number(raw.grantedAt ?? raw.timestamp ?? raw.granted_at);
  const grantedAt = Number.isFinite(grantedAtCandidate) && grantedAtCandidate > 0
    ? grantedAtCandidate
    : Date.now();

  const remainingSecondsCandidate = readPositiveNumber([
    raw.remainingSeconds,
    raw.remaining,
    raw.remainingDurationSeconds
  ]);
  const remainingSeconds = remainingSecondsCandidate
    ? Math.min(remainingSecondsCandidate, maxSeconds)
    : maxSeconds;

  const validSecondsCandidate = readPositiveNumber([
    raw.validSeconds,
    raw.validDurationSeconds,
    raw.validitySeconds
  ]);
  const validSeconds = validSecondsCandidate && validSecondsCandidate > 0
    ? validSecondsCandidate
    : maxSeconds;

  const expiresAtCandidate = Number(raw.expiresAt ?? raw.expiration ?? raw.expires_at);
  const expiresAt = Number.isFinite(expiresAtCandidate) && expiresAtCandidate > grantedAt
    ? expiresAtCandidate
    : grantedAt + validSeconds * 1000;

  if (remainingSeconds <= 0 || !Number.isFinite(expiresAt) || expiresAt <= grantedAt) {
    return null;
  }

  const level = typeof raw.level === 'string' && raw.level.trim()
    ? raw.level.trim()
    : typeof raw.difficulty === 'string' && raw.difficulty.trim()
      ? raw.difficulty.trim()
      : null;

  return {
    multiplier,
    maxSeconds,
    remainingSeconds,
    grantedAt,
    expiresAt,
    level
  };
}

const SUDOKU_COMPLETION_REWARD_CONFIG = normalizeSudokuRewardSettings(
  GLOBAL_CONFIG?.arcade?.sudoku?.rewards ?? null
);

function formatTrophyBonusValue(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '0';
  }
  const rounded = Math.round(numeric);
  if (Math.abs(numeric - rounded) <= 1e-9) {
    return formatIntegerLocalized(rounded);
  }
  return formatNumberLocalized(numeric, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function compareTextLocalized(a, b, options) {
  const api = getI18nApi();
  if (api && typeof api.compareText === 'function') {
    return api.compareText(a, b, options);
  }
  const locale = getCurrentLocale();
  return String(a ?? '').localeCompare(String(b ?? ''), locale, options);
}

function matchAvailableLanguage(raw) {
  const normalized = normalizeLanguageCode(raw);
  if (!normalized) {
    return null;
  }
  const directMatch = AVAILABLE_LANGUAGE_CODES.find(code => code.toLowerCase() === normalized);
  if (directMatch) {
    return directMatch;
  }
  const [base] = normalized.split('-');
  if (base) {
    const baseMatch = AVAILABLE_LANGUAGE_CODES.find(code => code.toLowerCase() === base);
    if (baseMatch) {
      return baseMatch;
    }
  }
  return null;
}

function resolveLanguageCode(raw) {
  return matchAvailableLanguage(raw) ?? DEFAULT_LANGUAGE_CODE;
}

function getConfigDefaultLanguage() {
  const configLanguage =
    GLOBAL_CONFIG?.language
    ?? APP_DATA?.DEFAULT_LANGUAGE
    ?? document?.documentElement?.lang;
  if (configLanguage) {
    return matchAvailableLanguage(configLanguage) ?? DEFAULT_LANGUAGE_CODE;
  }
  return DEFAULT_LANGUAGE_CODE;
}

function readStoredLanguagePreference() {
  try {
    const stored = globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY);
    if (stored) {
      const matched = matchAvailableLanguage(stored);
      if (matched) {
        return matched;
      }
    }
  } catch (error) {
    console.warn('Unable to read stored language preference', error);
  }
  return null;
}

function writeStoredLanguagePreference(lang) {
  try {
    const normalized = resolveLanguageCode(lang);
    globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, normalized);
  } catch (error) {
    console.warn('Unable to persist language preference', error);
  }
}

function readStoredAtomIconVisibility() {
  try {
    const stored = globalThis.localStorage?.getItem(ATOM_ICON_VISIBILITY_STORAGE_KEY);
    if (stored === '1' || stored === 'true') {
      return true;
    }
    if (stored === '0' || stored === 'false') {
      return false;
    }
  } catch (error) {
    console.warn('Unable to read atom icon preference', error);
  }
  return null;
}

function writeStoredAtomIconVisibility(visible) {
  try {
    const value = visible ? '1' : '0';
    globalThis.localStorage?.setItem(ATOM_ICON_VISIBILITY_STORAGE_KEY, value);
  } catch (error) {
    console.warn('Unable to persist atom icon preference', error);
  }
}

function readStoredMusicPagePreference() {
  try {
    const stored = globalThis.localStorage?.getItem(MUSIC_PAGE_STORAGE_KEY);
    if (stored === 'radio' || stored === 'midi') {
      return stored;
    }
  } catch (error) {
    console.warn('Unable to read stored music page preference', error);
  }
  return null;
}

function writeStoredMusicPagePreference(pageId) {
  try {
    if (pageId === 'radio' || pageId === 'midi') {
      globalThis.localStorage?.setItem(MUSIC_PAGE_STORAGE_KEY, pageId);
    }
  } catch (error) {
    console.warn('Unable to persist music page preference', error);
  }
}

function detectNavigatorLanguage() {
  const nav = typeof navigator !== 'undefined' ? navigator : null;
  if (!nav) {
    return null;
  }
  const candidates = [];
  if (Array.isArray(nav.languages)) {
    candidates.push(...nav.languages);
  }
  if (typeof nav.language === 'string') {
    candidates.push(nav.language);
  }
  for (let index = 0; index < candidates.length; index += 1) {
    const match = matchAvailableLanguage(candidates[index]);
    if (match) {
      return match;
    }
  }
  return null;
}

function getInitialLanguagePreference() {
  const stored = readStoredLanguagePreference();
  if (stored) {
    return stored;
  }
  const navigatorLanguage = detectNavigatorLanguage();
  if (navigatorLanguage) {
    return navigatorLanguage;
  }
  return getConfigDefaultLanguage();
}

function normalizeBrickSkinSelection(rawValue) {
  const value = typeof rawValue === 'string' ? rawValue.trim().toLowerCase() : '';
  if (value === 'pastels1' || value === 'pastels2') {
    return 'pastels';
  }
  if (BRICK_SKIN_CHOICES.includes(value)) {
    return value;
  }
  return 'original';
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
    } catch (err) {
      // Ignore malformed values and fall through to zero
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
    bigBangCount: 0,
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
        bigBang: LayeredNumber.one(),
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

function cloneRarityMultipliers(store) {
  if (store instanceof Map) {
    return new Map(store);
  }
  if (store && typeof store === 'object') {
    return { ...store };
  }
  return new Map();
}

function cloneProductionEntry(entry) {
  if (!entry) {
    return createEmptyProductionEntry();
  }
  const clone = {
    bigBangCount: Number.isFinite(entry.bigBangCount) ? entry.bigBangCount : 0,
    base: entry.base instanceof LayeredNumber ? entry.base.clone() : toLayeredValue(entry.base, 0),
    totalAddition: entry.totalAddition instanceof LayeredNumber
      ? entry.totalAddition.clone()
      : toLayeredValue(entry.totalAddition, 0),
    totalMultiplier: entry.totalMultiplier instanceof LayeredNumber
      ? entry.totalMultiplier.clone()
      : toMultiplierLayered(entry.totalMultiplier),
    additions: Array.isArray(entry.additions)
      ? entry.additions.map(add => ({
        ...add,
        value: add.value instanceof LayeredNumber ? add.value.clone() : toLayeredValue(add.value, 0)
      }))
      : [],
    multipliers: Array.isArray(entry.multipliers)
      ? entry.multipliers.map(mult => ({
        ...mult,
        value: mult.value instanceof LayeredNumber ? mult.value.clone() : toMultiplierLayered(mult.value)
      }))
      : [],
    total: entry.total instanceof LayeredNumber ? entry.total.clone() : toLayeredValue(entry.total, 0),
    sources: {
      flats: {
        baseFlat: entry.sources?.flats?.baseFlat instanceof LayeredNumber
          ? entry.sources.flats.baseFlat.clone()
          : toLayeredValue(entry.sources?.flats?.baseFlat, 0),
        shopFlat: entry.sources?.flats?.shopFlat instanceof LayeredNumber
          ? entry.sources.flats.shopFlat.clone()
          : toLayeredValue(entry.sources?.flats?.shopFlat, 0),
        elementFlat: entry.sources?.flats?.elementFlat instanceof LayeredNumber
          ? entry.sources.flats.elementFlat.clone()
          : toLayeredValue(entry.sources?.flats?.elementFlat, 0),
        fusionFlat: entry.sources?.flats?.fusionFlat instanceof LayeredNumber
          ? entry.sources.flats.fusionFlat.clone()
          : toLayeredValue(entry.sources?.flats?.fusionFlat, 0),
        devkitFlat: entry.sources?.flats?.devkitFlat instanceof LayeredNumber
          ? entry.sources.flats.devkitFlat.clone()
          : toLayeredValue(entry.sources?.flats?.devkitFlat, 0)
      },
      multipliers: {
        bigBang: entry.sources?.multipliers?.bigBang instanceof LayeredNumber
          ? entry.sources.multipliers.bigBang.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.bigBang ?? 1),
        trophyMultiplier: entry.sources?.multipliers?.trophyMultiplier instanceof LayeredNumber
          ? entry.sources.multipliers.trophyMultiplier.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.trophyMultiplier ?? 1),
        frenzy: entry.sources?.multipliers?.frenzy instanceof LayeredNumber
          ? entry.sources.multipliers.frenzy.clone()
          : LayeredNumber.one(),
        apsCrit: entry.sources?.multipliers?.apsCrit instanceof LayeredNumber
          ? entry.sources.multipliers.apsCrit.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.apsCrit ?? 1),
        collectionMultiplier: entry.sources?.multipliers?.collectionMultiplier instanceof LayeredNumber
          ? entry.sources.multipliers.collectionMultiplier.clone()
          : toMultiplierLayered(entry.sources?.multipliers?.collectionMultiplier ?? 1),
        rarityMultipliers: cloneRarityMultipliers(entry.sources?.multipliers?.rarityMultipliers)
      }
    }
  };
  return clone;
}

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

  // Always start a fresh session when the game is (re)loaded.
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

// Game state management
const DEFAULT_STATE = {
  atoms: LayeredNumber.zero(),
  lifetime: LayeredNumber.zero(),
  perClick: BASE_PER_CLICK.clone(),
  perSecond: BASE_PER_SECOND.clone(),
  basePerClick: BASE_PER_CLICK.clone(),
  basePerSecond: BASE_PER_SECOND.clone(),
  gachaTickets: 0,
  bonusParticulesTickets: 0,
  upgrades: {},
  shopUnlocks: [],
  elements: createInitialElementCollection(),
  fusions: createInitialFusionState(),
  fusionBonuses: createInitialFusionBonuses(),
  gachaImages: createInitialGachaImageCollection(),
  gachaImageAcquisitionCounter: 0,
  collectionVideos: createInitialCollectionVideoCollection(),
  gachaBonusImages: createInitialGachaBonusImageCollection(),
  gachaBonusImageAcquisitionCounter: 0,
  pageUnlocks: createInitialPageUnlockState(),
  lastSave: Date.now(),
  theme: DEFAULT_THEME_ID,
  arcadeBrickSkin: 'original',
  stats: createInitialStats(),
  production: createEmptyProductionBreakdown(),
  productionBase: createEmptyProductionBreakdown(),
  crit: createDefaultCritState(),
  baseCrit: createDefaultCritState(),
  lastCritical: null,
  elementBonusSummary: {},
  trophies: [],
  offlineGainMultiplier: MYTHIQUE_OFFLINE_BASE,
  bigBangLevelBonus: 0,
  offlineTickets: {
    secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
    capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
    progressSeconds: 0
  },
  sudokuOfflineBonus: null,
  ticketStarAutoCollect: null,
  ticketStarAutoCollectEnabled: false,
  ticketStarAverageIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
  ticketStarUnlocked: false,
  frenzySpawnBonus: { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 },
  musicTrackId: null,
  musicVolume: DEFAULT_MUSIC_VOLUME,
  musicEnabled: DEFAULT_MUSIC_ENABLED,
  bigBangButtonVisible: false,
  apsCrit: createDefaultApsCritState(),
  featureUnlockFlags: []
};

const ARCADE_GAME_IDS = Object.freeze([
  'particules',
  'metaux',
  'wave',
  'starsWar',
  'jumpingCat',
  'reflex',
  'quantum2048',
  'bigger',
  'math',
  'theLine',
  'lightsOut',
  'link',
  'starBridges',
  'pipeTap',
  'colorStack',
  'motocross',
  'hex',
  'twins',
  'sokoban',
  'taquin',
  'balance',
  'sudoku',
  'minesweeper',
  'solitaire',
  'holdem',
  'blackjack',
  'echecs'
]);

const DEFAULT_TICKET_STAR_SPRITE_ID = (() => {
  const raw = CONFIG?.ticketStar?.sprite?.defaultSprite
    ?? CONFIG?.ticketStar?.sprite?.defaultSpriteId
    ?? CONFIG?.ticketStar?.defaultSprite;
  if (typeof raw === 'string') {
    const normalized = raw.trim().toLowerCase();
    if (normalized === 'animated' || normalized === 'gif' || normalized === 'anim') {
      return 'animated';
    }
  }
  return 'static';
})();

const DEFAULT_TICKET_STAR_SPECIAL_REWARD = (() => {
  const raw = Number(CONFIG?.ticketStar?.specialStar?.rewardTickets ?? CONFIG?.ticketStar?.specialStar?.tickets ?? 10);
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 10;
})();

function clampTicketStarSpecialChance(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 0;
  }
  if (numeric <= 0) {
    return 0;
  }
  if (numeric > 1) {
    return Math.min(1, numeric / 100);
  }
  return Math.min(1, numeric);
}

function normalizeTicketStarSpecialReward(value, fallback = DEFAULT_TICKET_STAR_SPECIAL_REWARD) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.floor(numeric);
  }
  const fallbackNumeric = Number(fallback);
  if (Number.isFinite(fallbackNumeric) && fallbackNumeric > 0) {
    return Math.floor(fallbackNumeric);
  }
  return 1;
}

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

const gameState = {
  atoms: LayeredNumber.zero(),
  lifetime: LayeredNumber.zero(),
  perClick: BASE_PER_CLICK.clone(),
  perSecond: BASE_PER_SECOND.clone(),
  basePerClick: BASE_PER_CLICK.clone(),
  basePerSecond: BASE_PER_SECOND.clone(),
  gachaTickets: 0,
  bonusParticulesTickets: 0,
  upgrades: {},
  shopUnlocks: new Set(),
  elements: createInitialElementCollection(),
  gachaCards: createInitialGachaCardCollection(),
  gachaImages: createInitialGachaImageCollection(),
  gachaImageAcquisitionCounter: 0,
  fusions: createInitialFusionState(),
  fusionBonuses: createInitialFusionBonuses(),
  pageUnlocks: createInitialPageUnlockState(),
  theme: DEFAULT_THEME_ID,
  arcadeBrickSkin: 'original',
  stats: createInitialStats(),
  production: createEmptyProductionBreakdown(),
  productionBase: createEmptyProductionBreakdown(),
  crit: createDefaultCritState(),
  baseCrit: createDefaultCritState(),
  lastCritical: null,
  elementBonusSummary: {},
  trophies: new Set(),
  offlineGainMultiplier: MYTHIQUE_OFFLINE_BASE,
  bigBangLevelBonus: 0,
  offlineTickets: {
    secondsPerTicket: OFFLINE_TICKET_CONFIG.secondsPerTicket,
    capSeconds: OFFLINE_TICKET_CONFIG.capSeconds,
    progressSeconds: 0
  },
  sudokuOfflineBonus: null,
  ticketStarAutoCollect: null,
  ticketStarAverageIntervalSeconds: DEFAULT_TICKET_STAR_INTERVAL_SECONDS,
  ticketStarUnlocked: false,
  ticketStarSpriteId: DEFAULT_TICKET_STAR_SPRITE_ID,
  ticketStarSpecialChance: 0,
  ticketStarSpecialReward: DEFAULT_TICKET_STAR_SPECIAL_REWARD,
  frenzySpawnBonus: { perClick: 1, perSecond: 1, addPerClick: 0, addPerSecond: 0 },
  musicTrackId: null,
  musicVolume: DEFAULT_MUSIC_VOLUME,
  musicEnabled: DEFAULT_MUSIC_ENABLED,
  bigBangButtonVisible: false,
  apsCrit: createDefaultApsCritState(),
  arcadeProgress: createInitialArcadeProgress(),
  featureUnlockFlags: new Set()
};

if (typeof window !== 'undefined') {
  window.atom2universGameState = gameState;
}

const gachaTicketIncreaseListeners = new Set();
let gachaTicketObserverPauseCount = 0;
let internalGachaTicketValue = sanitizeGachaTicketValue(gameState.gachaTickets);

Object.defineProperty(gameState, 'gachaTickets', {
  configurable: true,
  enumerable: true,
  get() {
    return internalGachaTicketValue;
  },
  set(value) {
    const nextValue = sanitizeGachaTicketValue(value);
    const previousValue = internalGachaTicketValue;
    internalGachaTicketValue = nextValue;
    if (nextValue > previousValue && gachaTicketObserverPauseCount <= 0) {
      notifyGachaTicketIncrease(previousValue, nextValue);
    }
  }
});

function sanitizeGachaTicketValue(rawValue) {
  const numeric = Number(rawValue);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  return Math.max(0, Math.floor(numeric));
}

function notifyGachaTicketIncrease(previousValue, currentValue) {
  if (currentValue <= previousValue) {
    return;
  }
  const delta = currentValue - previousValue;
  gachaTicketIncreaseListeners.forEach(listener => {
    try {
      listener(delta, { previous: previousValue, current: currentValue });
    } catch (error) {
      console.error('Gacha ticket increase listener failed', error);
    }
  });
}

function pauseGachaTicketObservers() {
  gachaTicketObserverPauseCount += 1;
}

function resumeGachaTicketObservers() {
  gachaTicketObserverPauseCount = Math.max(0, gachaTicketObserverPauseCount - 1);
}

function setGachaTicketsSilently(value) {
  pauseGachaTicketObservers();
  try {
    gameState.gachaTickets = value;
    return gameState.gachaTickets;
  } finally {
    resumeGachaTicketObservers();
  }
}

function onGachaTicketsIncrease(listener) {
  if (typeof listener !== 'function') {
    return () => {};
  }
  gachaTicketIncreaseListeners.add(listener);
  return () => {
    gachaTicketIncreaseListeners.delete(listener);
  };
}

applyFrenzySpawnChanceBonus(gameState.frenzySpawnBonus);
if (typeof setParticulesBrickSkinPreference === 'function') {
  setParticulesBrickSkinPreference(gameState.arcadeBrickSkin);
}

const initialPerformanceModeSettings = getPerformanceModeSettings(PERFORMANCE_MODE_DEFAULT_ID);

const performanceModeState = {
  id: PERFORMANCE_MODE_DEFAULT_ID,
  settings: initialPerformanceModeSettings,
  atomAnimation: normalizeAtomAnimationSettings(initialPerformanceModeSettings?.atomAnimation),
  pendingManualGain: null,
  pendingAutoGain: null,
  autoAccumulatedMs: 0,
  lastManualFlush: typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now(),
  lastAutoFlush: typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now()
};

function getAtomAnimationSettings() {
  return performanceModeState?.atomAnimation || DEFAULT_ATOM_ANIMATION_SETTINGS;
}

const gameLoopControl = {
  handle: null,
  type: null,
  isActive: false
};

function getLoopTimestamp() {
  return typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now();
}

function clearScheduledGameLoop() {
  if (gameLoopControl.handle == null) {
    return;
  }
  if (gameLoopControl.type === 'timeout') {
    const globalTarget = typeof globalThis !== 'undefined'
      ? globalThis
      : (typeof window !== 'undefined' ? window : null);
    if (globalTarget && typeof globalTarget.clearTimeout === 'function') {
      globalTarget.clearTimeout(gameLoopControl.handle);
    } else if (typeof clearTimeout === 'function') {
      clearTimeout(gameLoopControl.handle);
    }
  } else if (gameLoopControl.type === 'raf') {
    if (typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(gameLoopControl.handle);
    } else if (typeof globalThis !== 'undefined'
      && typeof globalThis.cancelAnimationFrame === 'function') {
      globalThis.cancelAnimationFrame(gameLoopControl.handle);
    }
  }
  gameLoopControl.handle = null;
  gameLoopControl.type = null;
}

function scheduleGameLoop(options = {}) {
  const config = Object.assign({ immediate: false, force: false }, options);
  if (!gameLoopControl.isActive && !config.force) {
    return;
  }
  clearScheduledGameLoop();
  const settings = performanceModeState.settings || {};
  const intervalRaw = Number(settings?.frameIntervalMs);
  const frameInterval = Number.isFinite(intervalRaw) && intervalRaw > 0 ? intervalRaw : 0;
  const nowProvider = getLoopTimestamp;
  const hasWindow = typeof window !== 'undefined';
  const hasRaf = hasWindow && typeof window.requestAnimationFrame === 'function';
  const useTimeout = frameInterval > 0 || !hasRaf;
  const delay = useTimeout && !config.immediate
    ? Math.max(frameInterval > 0 ? frameInterval : GAME_LOOP_MIN_TIMEOUT_MS, GAME_LOOP_MIN_TIMEOUT_MS)
    : 0;
  if (useTimeout) {
    const timeoutTarget = typeof globalThis !== 'undefined'
      ? globalThis
      : (hasWindow ? window : null);
    if (timeoutTarget && typeof timeoutTarget.setTimeout === 'function') {
      gameLoopControl.type = 'timeout';
      gameLoopControl.handle = timeoutTarget.setTimeout(() => {
        loop(nowProvider());
      }, delay);
      return;
    }
    gameLoopControl.type = 'timeout';
    gameLoopControl.handle = setTimeout(() => {
      loop(nowProvider());
    }, delay);
    return;
  }
  gameLoopControl.type = 'raf';
  gameLoopControl.handle = window.requestAnimationFrame(loop);
}

function startGameLoop(options = {}) {
  if (gameLoopControl.isActive) {
    restartGameLoop(options);
    return;
  }
  gameLoopControl.isActive = true;
  const now = getLoopTimestamp();
  lastUpdate = now;
  lastSaveTime = now;
  lastUIUpdate = now;
  scheduleGameLoop(Object.assign({ immediate: true, force: true }, options));
}

function restartGameLoop(options = {}) {
  if (!gameLoopControl.isActive) {
    return;
  }
  const now = getLoopTimestamp();
  lastUpdate = now;
  lastSaveTime = now;
  lastUIUpdate = now;
  clearScheduledGameLoop();
  scheduleGameLoop(Object.assign({ immediate: true, force: true }, options));
}
