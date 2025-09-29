function getI18nApi() {
  return globalThis.i18n;
}

function getCurrentLocale() {
  const api = getI18nApi();
  if (api && typeof api.getCurrentLocale === 'function') {
    return api.getCurrentLocale();
  }
  return 'fr-FR';
}

function formatNumberLocalized(value, options) {
  const api = getI18nApi();
  if (api && typeof api.formatNumber === 'function') {
    const formatted = api.formatNumber(value, options);
    return formatted !== undefined ? formatted : '';
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '';
  }
  return numeric.toLocaleString(getCurrentLocale(), options);
}

function formatIntegerLocalized(value) {
  return formatNumberLocalized(value, { maximumFractionDigits: 0, minimumFractionDigits: 0 });
}

function translateOrDefault(key, fallback = '', params) {
  if (typeof key !== 'string' || !key.trim()) {
    return fallback;
  }
  const api = getI18nApi();
  const translator = api && typeof api.t === 'function'
    ? api.t.bind(api)
    : (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function'
        ? globalThis.t
        : (typeof t === 'function' ? t : null));
  if (translator) {
    try {
      const translated = translator(key, params);
      if (typeof translated === 'string' && translated && translated !== key) {
        return translated;
      }
    } catch (error) {
      console.warn('Unable to translate key', key, error);
    }
  }
  return fallback;
}

function compareTextLocalized(a, b, options) {
  const api = getI18nApi();
  if (api && typeof api.compareText === 'function') {
    return api.compareText(a, b, options);
  }
  const locale = getCurrentLocale();
  return String(a ?? '').localeCompare(String(b ?? ''), locale, options);
}

function toLocaleLower(value) {
  const api = getI18nApi();
  if (api && typeof api.toLocaleLowerCase === 'function') {
    return api.toLocaleLowerCase(value);
  }
  const locale = getCurrentLocale();
  return typeof value === 'string' ? value.toLocaleLowerCase(locale) : '';
}

function formatElementFlatBonus(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return null;
  }
  try {
    return new LayeredNumber(numeric).toString();
  } catch (err) {
    return formatNumberLocalized(numeric);
  }
}

function formatElementMultiplierBonus(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 1) {
    return null;
  }
  const delta = Math.abs(numeric - 1);
  if (delta < 1e-6) {
    return null;
  }
  const options = numeric >= 100
    ? { maximumFractionDigits: 0 }
    : numeric >= 10
      ? { maximumFractionDigits: 1 }
      : { maximumFractionDigits: 2 };
  return formatNumberLocalized(numeric, options);
}

function formatElementCritChanceBonus(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || Math.abs(numeric) < 1e-6) {
    return null;
  }
  const percent = numeric * 100;
  const abs = Math.abs(percent);
  const options = abs >= 100
    ? { maximumFractionDigits: 0 }
    : abs >= 10
      ? { maximumFractionDigits: 1 }
      : { maximumFractionDigits: 2 };
  return `${formatNumberLocalized(percent, options)} %`;
}

function formatElementCritMultiplierBonus(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || Math.abs(numeric) < 1e-6) {
    return null;
  }
  const abs = Math.abs(numeric);
  const options = abs >= 10
    ? { maximumFractionDigits: 0 }
    : abs >= 1
      ? { maximumFractionDigits: 1 }
      : { maximumFractionDigits: 2 };
  return formatNumberLocalized(numeric, options);
}

function formatElementMultiplierDisplay(value) {
  const formatted = formatElementMultiplierBonus(value);
  return formatted ? `×${formatted}` : null;
}

function formatElementTicketInterval(seconds) {
  const numeric = Number(seconds);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null;
  }
  const duration = formatDuration(numeric * 1000);
  if (!duration) {
    return null;
  }
  return translateOrDefault(
    'scripts.app.table.bonuses.ticketInterval',
    `Toutes les ${duration}`,
    { duration }
  );
}

const COLLECTION_BONUS_OVERVIEW_CACHE = new Map();

if (typeof window !== 'undefined') {
  window.addEventListener('i18n:languagechange', () => {
    COLLECTION_BONUS_OVERVIEW_CACHE.clear();
  });
}

function formatSignedBonus(value, { forcePlus = true } = {}) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return null;
  }
  const abs = Math.abs(numeric);
  const options = abs >= 100
    ? { maximumFractionDigits: 0 }
    : abs >= 10
      ? { maximumFractionDigits: 1 }
      : { maximumFractionDigits: 2 };
  const formatted = formatNumberLocalized(numeric, options);
  if (numeric > 0 && forcePlus) {
    return `+${formatted}`;
  }
  return formatted;
}

function formatBonusThreshold(addConfig, context, { includeRequireAllUnique = true } = {}) {
  if (!addConfig) return '';
  const notes = [];
  const { minCopies, minUnique, requireAllUnique } = addConfig;
  if (Number.isFinite(minCopies) && minCopies > 1) {
    const unit = minCopies === 1 ? 'copie' : 'copies';
    notes.push(`dès ${minCopies} ${unit}`);
  }
  if (Number.isFinite(minUnique) && minUnique > 0) {
    const unit = minUnique === 1 ? 'unique' : 'uniques';
    notes.push(`minimum ${minUnique} ${unit}`);
  }
  if (requireAllUnique && includeRequireAllUnique) {
    notes.push('collection complète requise');
  }
  return notes.length ? ` (${notes.join(' · ')})` : '';
}

function formatRarityMultiplierNotes(entries) {
  if (!Array.isArray(entries) || entries.length === 0) {
    return [];
  }
  const notes = [];
  entries.forEach(entry => {
    if (!entry || typeof entry !== 'object') return;
    const rarityId = typeof entry.rarityId === 'string' ? entry.rarityId.trim() : '';
    if (!rarityId) return;
    const targetLabel = RARITY_LABEL_MAP.get(rarityId) || rarityId;
    const parts = [];
    const clickText = formatElementMultiplierDisplay(entry.perClick);
    if (clickText && clickText !== '×1') {
      parts.push(`APC ${clickText}`);
    }
    const autoText = formatElementMultiplierDisplay(entry.perSecond);
    if (autoText && autoText !== '×1') {
      parts.push(`APS ${autoText}`);
    }
    if (parts.length) {
      notes.push(`Amplifie ${targetLabel} : ${parts.join(' · ')}`);
    }
  });
  return notes;
}

function describeAddConfig(addConfig, context, options = {}) {
  if (!addConfig) return [];
  const {
    clickAdd = 0,
    autoAdd = 0,
    uniqueClickAdd = 0,
    uniqueAutoAdd = 0,
    duplicateClickAdd = 0,
    duplicateAutoAdd = 0,
    rarityFlatMultipliers
  } = addConfig;
  const overrideLabel = typeof options.overrideLabel === 'string'
    ? options.overrideLabel.trim()
    : null;

  let includeRequireAllUnique = true;
  let baseLabel = (() => {
    if (typeof addConfig.label === 'string' && addConfig.label.trim()) {
      return addConfig.label.trim();
    }
    if (overrideLabel) {
      return overrideLabel;
    }
    if (context === 'perCopy') {
      return 'Par copie';
    }
    if (context === 'setBonus') {
      if (
        addConfig.requireAllUnique
        && (!Number.isFinite(addConfig.minCopies) || addConfig.minCopies <= 1)
        && (!Number.isFinite(addConfig.minUnique) || addConfig.minUnique <= 0)
      ) {
        includeRequireAllUnique = false;
        return 'Collection complète';
      }
      return 'Bonus de collection';
    }
    return 'Bonus';
  })();

  const thresholdText = formatBonusThreshold(addConfig, context, { includeRequireAllUnique });
  const effects = [];

  if (Number.isFinite(clickAdd) && clickAdd !== 0) {
    const value = formatElementFlatBonus(clickAdd);
    if (value) {
      effects.push(`APC +${value}`);
    }
  }
  if (Number.isFinite(autoAdd) && autoAdd !== 0) {
    const value = formatElementFlatBonus(autoAdd);
    if (value) {
      effects.push(`APS +${value}`);
    }
  }

  if (Number.isFinite(uniqueClickAdd) && uniqueClickAdd !== 0) {
    const value = formatElementFlatBonus(uniqueClickAdd);
    if (value) {
      effects.push(`APC +${value} par élément unique`);
    }
  }
  if (Number.isFinite(uniqueAutoAdd) && uniqueAutoAdd !== 0) {
    const value = formatElementFlatBonus(uniqueAutoAdd);
    if (value) {
      effects.push(`APS +${value} par élément unique`);
    }
  }

  if (Number.isFinite(duplicateClickAdd) && duplicateClickAdd !== 0) {
    const value = formatElementFlatBonus(duplicateClickAdd);
    if (value) {
      effects.push(`APC +${value} par doublon`);
    }
  }
  if (Number.isFinite(duplicateAutoAdd) && duplicateAutoAdd !== 0) {
    const value = formatElementFlatBonus(duplicateAutoAdd);
    if (value) {
      effects.push(`APS +${value} par doublon`);
    }
  }

  const rarityNotes = formatRarityMultiplierNotes(rarityFlatMultipliers);
  if (rarityNotes.length) {
    effects.push(...rarityNotes);
  }

  if (!effects.length) {
    return [];
  }

  const text = `${baseLabel} : ${effects.join(' · ')}`;
  return [thresholdText ? `${text}${thresholdText}` : text];
}

function describeCritEffect(effect, scopeLabel) {
  if (!effect) return null;
  const parts = [];
  if (Number.isFinite(effect.chanceSet) && effect.chanceSet > 0) {
    const text = formatElementCritChanceBonus(effect.chanceSet);
    if (text) {
      parts.push(`Chance fixée à ${text}`);
    }
  }
  if (Number.isFinite(effect.chanceAdd) && effect.chanceAdd !== 0) {
    const text = formatElementCritChanceBonus(effect.chanceAdd);
    if (text) {
      parts.push(`Chance +${text}`);
    }
  }
  if (Number.isFinite(effect.chanceMult) && effect.chanceMult !== 1 && effect.chanceMult > 0) {
    const text = formatElementMultiplierDisplay(effect.chanceMult);
    if (text && text !== '×1') {
      parts.push(`Chance ${text}`);
    }
  }
  if (Number.isFinite(effect.multiplierSet) && effect.multiplierSet > 0) {
    const text = formatElementCritMultiplierBonus(effect.multiplierSet);
    if (text) {
      parts.push(`Multiplicateur fixé à ${text}×`);
    }
  }
  if (Number.isFinite(effect.multiplierAdd) && effect.multiplierAdd !== 0) {
    const text = formatElementCritMultiplierBonus(effect.multiplierAdd);
    if (text) {
      parts.push(`Multiplicateur +${text}×`);
    }
  }
  if (Number.isFinite(effect.multiplierMult) && effect.multiplierMult !== 1 && effect.multiplierMult > 0) {
    const text = formatElementMultiplierDisplay(effect.multiplierMult);
    if (text && text !== '×1') {
      parts.push(`Multiplicateur ${text}`);
    }
  }
  if (Number.isFinite(effect.maxMultiplierSet) && effect.maxMultiplierSet > 0) {
    const text = formatElementCritMultiplierBonus(effect.maxMultiplierSet);
    if (text) {
      parts.push(`Cap critique fixé à ${text}×`);
    }
  }
  if (Number.isFinite(effect.maxMultiplierAdd) && effect.maxMultiplierAdd !== 0) {
    const text = formatElementCritMultiplierBonus(effect.maxMultiplierAdd);
    if (text) {
      parts.push(`Cap critique +${text}×`);
    }
  }
  if (Number.isFinite(effect.maxMultiplierMult) && effect.maxMultiplierMult !== 1 && effect.maxMultiplierMult > 0) {
    const text = formatElementMultiplierDisplay(effect.maxMultiplierMult);
    if (text && text !== '×1') {
      parts.push(`Cap critique ${text}`);
    }
  }
  return parts.length ? `${scopeLabel} : ${parts.join(' · ')}` : null;
}

function describeCritConfig(critConfig) {
  if (!critConfig) return [];
  const results = [];
  if (critConfig.perUnique) {
    const text = describeCritEffect(critConfig.perUnique, 'Critique par unique');
    if (text) {
      results.push(text);
    }
  }
  if (critConfig.perDuplicate) {
    const text = describeCritEffect(critConfig.perDuplicate, 'Critique par doublon');
    if (text) {
      results.push(text);
    }
  }
  return results;
}

function describeMultiplierConfig(multiplierConfig, labelOverride = null) {
  if (!multiplierConfig) return null;
  const targets = [];
  if (multiplierConfig.targets?.has('perClick')) {
    targets.push('APC');
  }
  if (multiplierConfig.targets?.has('perSecond')) {
    targets.push('APS');
  }
  const targetLabel = targets.length === 2
    ? 'APC/APS'
    : targets.length === 1
      ? targets[0]
      : 'production';
  const parts = [];
  if (
    Number.isFinite(multiplierConfig.base)
    && Math.abs(multiplierConfig.base - 1) > 1e-9
  ) {
    const baseText = formatMultiplier(multiplierConfig.base);
    if (baseText && baseText !== '×—') {
      const prefix = targetLabel === 'production' ? '' : `${targetLabel} `;
      parts.push(`${prefix}base ${baseText}`.trim());
    }
  }
  if (
    Number.isFinite(multiplierConfig.increment)
    && multiplierConfig.increment !== 0
    && Number.isFinite(multiplierConfig.every)
    && multiplierConfig.every > 0
  ) {
    const incrementText = formatSignedBonus(multiplierConfig.increment);
    if (incrementText) {
      const unit = multiplierConfig.every === 1 ? 'copie' : 'copies';
      const prefix = targetLabel === 'production' ? '' : `${targetLabel} `;
      parts.push(`${prefix}${incrementText} toutes les ${multiplierConfig.every} ${unit}`.trim());
    }
  }
  if (
    Number.isFinite(multiplierConfig.cap)
    && multiplierConfig.cap > 0
    && multiplierConfig.cap !== Number.POSITIVE_INFINITY
  ) {
    const capText = formatMultiplier(multiplierConfig.cap);
    if (capText && capText !== '×—') {
      const prefix = targetLabel === 'production' ? '' : `${targetLabel} `;
      parts.push(`${prefix}max ${capText}`.trim());
    }
  }
  if (!parts.length) {
    return null;
  }
  const prefix = labelOverride && labelOverride.trim()
    ? labelOverride.trim()
    : `Multiplicateur ${targetLabel}`;
  return `${prefix} : ${parts.join(' · ')}`;
}

function describeRarityMultiplierBonus(bonusConfig, labelOverride = null) {
  if (!bonusConfig) return null;
  const targets = [];
  if (bonusConfig.targets?.has('perClick')) {
    targets.push('APC');
  }
  if (bonusConfig.targets?.has('perSecond')) {
    targets.push('APS');
  }
  const targetLabel = targets.length === 2
    ? 'APC/APS'
    : targets.length === 1
      ? targets[0]
      : 'production';
  const amountText = formatSignedBonus(bonusConfig.amount);
  if (!amountText) {
    return null;
  }
  const notes = [];
  if (Number.isFinite(bonusConfig.uniqueThreshold) && bonusConfig.uniqueThreshold > 0) {
    const unit = bonusConfig.uniqueThreshold === 1 ? 'unique' : 'uniques';
    notes.push(`minimum ${bonusConfig.uniqueThreshold} ${unit}`);
  }
  if (Number.isFinite(bonusConfig.copyThreshold) && bonusConfig.copyThreshold > 0) {
    const unit = bonusConfig.copyThreshold === 1 ? 'copie' : 'copies';
    notes.push(`dès ${bonusConfig.copyThreshold} ${unit}`);
  }
  const suffix = notes.length ? ` (${notes.join(' · ')})` : '';
  const detail = targetLabel === 'production'
    ? `${amountText}${suffix}`
    : `${targetLabel} ${amountText}${suffix}`;
  if (labelOverride && labelOverride.trim()) {
    return `${labelOverride.trim()} : ${detail}`;
  }
  return `Multiplicateur de rareté ${detail}`;
}

function describeMythiqueSpecials(groupConfig) {
  const results = [];

  const formatSmallNumber = value => {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return null;
    }
    const abs = Math.abs(numeric);
    const options = abs >= 100
      ? { maximumFractionDigits: 0 }
      : abs >= 10
        ? { maximumFractionDigits: 1 }
        : { maximumFractionDigits: 2 };
    return formatNumberLocalized(numeric, options);
  };

  if (
    Number.isFinite(MYTHIQUE_TICKET_UNIQUE_REDUCTION_SECONDS)
    && MYTHIQUE_TICKET_UNIQUE_REDUCTION_SECONDS > 0
  ) {
    const reductionText = formatSmallNumber(MYTHIQUE_TICKET_UNIQUE_REDUCTION_SECONDS);
    const parts = [];
    if (reductionText) {
      parts.push(`Réduit l’intervalle des étoiles à tickets de ${reductionText}s par élément unique`);
    }
    if (
      Number.isFinite(MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS)
      && MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS > 0
    ) {
      const minText = formatSmallNumber(MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS);
      if (minText) {
        parts.push(`minimum ${minText}s`);
      }
    }
    if (parts.length) {
      results.push(parts.join(' · '));
    }
  }

  if (Number.isFinite(MYTHIQUE_OFFLINE_PER_DUPLICATE) && MYTHIQUE_OFFLINE_PER_DUPLICATE > 0) {
    const parts = [];
    if (Number.isFinite(MYTHIQUE_OFFLINE_BASE) && Math.abs(MYTHIQUE_OFFLINE_BASE - 1) > 1e-9) {
      const baseText = formatMultiplier(MYTHIQUE_OFFLINE_BASE);
      if (baseText && baseText !== '×—') {
        parts.push(`base ${baseText}`);
      }
    }
    const incrementText = formatSignedBonus(MYTHIQUE_OFFLINE_PER_DUPLICATE);
    if (incrementText) {
      parts.push(`${incrementText} par doublon`);
    }
    if (
      Number.isFinite(MYTHIQUE_OFFLINE_CAP)
      && MYTHIQUE_OFFLINE_CAP > 0
      && MYTHIQUE_OFFLINE_CAP !== Number.POSITIVE_INFINITY
    ) {
      const capText = formatMultiplier(MYTHIQUE_OFFLINE_CAP);
      if (capText && capText !== '×—') {
        parts.push(`max ${capText}`);
      }
    }
    if (parts.length) {
      results.push(`Collecte hors ligne ${parts.join(' · ')}`);
    }
  }

  if (
    Number.isFinite(MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS)
    && MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS !== 0
    && Number.isFinite(MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP)
    && MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP !== Number.POSITIVE_INFINITY
  ) {
    const overflowValue = formatElementFlatBonus(MYTHIQUE_DUPLICATE_OVERFLOW_FLAT_BONUS);
    if (overflowValue) {
      const threshold = Math.max(0, Math.floor(MYTHIQUE_DUPLICATES_FOR_OFFLINE_CAP));
      const thresholdText = formatSmallNumber(threshold);
      const unit = threshold <= 1 ? 'doublon' : 'doublons';
      const parts = [`APC/APS +${overflowValue} par doublon`];
      if (Number.isFinite(threshold) && threshold > 0 && thresholdText) {
        parts.push(`au-delà de ${thresholdText} ${unit}`);
      }
      results.push(parts.join(' · '));
    }
  }

  if (
    Number.isFinite(MYTHIQUE_FRENZY_SPAWN_BONUS_MULTIPLIER)
    && Math.abs(MYTHIQUE_FRENZY_SPAWN_BONUS_MULTIPLIER - 1) > 1e-9
  ) {
    const frenzyText = formatMultiplier(MYTHIQUE_FRENZY_SPAWN_BONUS_MULTIPLIER);
    if (frenzyText && frenzyText !== '×—') {
      results.push(`Chance de frénésie ${frenzyText} (collection complète requise)`);
    }
  }

  return results;
}

function getCollectionBonusOverview(rarityId) {
  if (!rarityId) return [];
  if (COLLECTION_BONUS_OVERVIEW_CACHE.has(rarityId)) {
    return COLLECTION_BONUS_OVERVIEW_CACHE.get(rarityId);
  }
  const config = ELEMENT_GROUP_BONUS_CONFIG.get(rarityId);
  if (!config) {
    COLLECTION_BONUS_OVERVIEW_CACHE.set(rarityId, []);
    return [];
  }
  const overview = [];
  const overviewSet = new Set();
  const pushOverview = text => {
    const normalized = normalizeCollectionDetailText(text);
    if (!normalized) {
      return;
    }
    if (overviewSet.has(normalized)) {
      return;
    }
    overviewSet.add(normalized);
    overview.push(text);
  };
  const labelOverrides = config.labels && typeof config.labels === 'object' ? config.labels : {};

  describeAddConfig(config.perCopy, 'perCopy', { overrideLabel: labelOverrides.perCopy })
    .forEach(text => pushOverview(text));

  if (Array.isArray(config.setBonuses) && config.setBonuses.length) {
    config.setBonuses.forEach(entry => {
      if (!entry) return;
      const overrideLabel = (typeof entry.label === 'string' && entry.label.trim())
        ? null
        : labelOverrides.setBonus;
      describeAddConfig(entry, 'setBonus', { overrideLabel })
        .forEach(text => pushOverview(text));
    });
  } else if (config.setBonus) {
    const overrideLabel = (typeof config.setBonus.label === 'string' && config.setBonus.label.trim())
      ? null
      : labelOverrides.setBonus;
    describeAddConfig(config.setBonus, 'setBonus', { overrideLabel })
      .forEach(text => pushOverview(text));
  }

  const multiplierLabel = config.multiplier?.label || labelOverrides.multiplier || null;
  const multiplierText = describeMultiplierConfig(config.multiplier, multiplierLabel);
  if (multiplierText) {
    pushOverview(multiplierText);
  }

  describeCritConfig(config.crit).forEach(text => pushOverview(text));

  const rarityMultiplierLabel = labelOverrides.rarityMultiplier || null;
  const rarityMultiplierText = describeRarityMultiplierBonus(config.rarityMultiplierBonus, rarityMultiplierLabel);
  if (rarityMultiplierText) {
    pushOverview(rarityMultiplierText);
  }

  if (rarityId === MYTHIQUE_RARITY_ID) {
    describeMythiqueSpecials(config).forEach(text => pushOverview(text));
  }

  if (rarityId === 'stellaire') {
    pushOverview('Synergie Singularité : Bonus ×2 si la collection Singularité minérale est complète');
  } else if (rarityId === 'singulier') {
    pushOverview('Synergie Forge stellaire : Compléter la collection double tous les bonus Forge stellaire');
  }

  let finalOverview = overview;
  if (COMPACT_COLLECTION_RARITIES.has(rarityId)) {
    const rarityLabel = RARITY_LABEL_MAP.get(rarityId) || '';
    const transformed = [];
    const transformedSet = new Set();
    overview.forEach(text => {
      if (!text) return;
      let trimmed = String(text).trim();
      if (!trimmed) return;
      if (rarityLabel) {
        const stripped = stripBonusLabelPrefix(trimmed, rarityLabel);
        if (stripped && stripped.trim()) {
          trimmed = stripped.trim();
        }
      }
      trimmed = trimmed.replace(/^·\s*/, '');
      const colonIndex = trimmed.indexOf(':');
      if (colonIndex !== -1) {
        trimmed = trimmed.slice(colonIndex + 1).trim();
      }
      trimmed = trimmed.replace(/^·\s*/, '');
      if (!trimmed) return;
      const normalized = normalizeCollectionDetailText(trimmed);
      if (!normalized || transformedSet.has(normalized)) {
        return;
      }
      transformedSet.add(normalized);
      transformed.push(trimmed);
    });
    finalOverview = transformed;
  }

  COLLECTION_BONUS_OVERVIEW_CACHE.set(rarityId, finalOverview);
  return finalOverview;
}

function stripBonusLabelPrefix(label, rarityLabel) {
  if (!label) return null;
  const trimmed = String(label).trim();
  if (!rarityLabel) {
    return trimmed;
  }
  const prefix = `${rarityLabel} · `;
  if (trimmed.startsWith(prefix)) {
    return trimmed.slice(prefix.length);
  }
  return trimmed;
}

function normalizeCollectionDetailText(text) {
  if (typeof text !== 'string') {
    return null;
  }
  const trimmed = text.trim();
  if (!trimmed) {
    return null;
  }
  const collapsed = trimmed.replace(/\s+/g, ' ');
  const lowered = toLocaleLower(collapsed);
  return lowered || collapsed.toLowerCase();
}

function renderElementBonuses() {
  const container = elements.infoElementBonuses;
  if (!container) return;
  if (elements.infoBonusSubtitle) {
    elements.infoBonusSubtitle.textContent = INFO_BONUS_SUBTITLE;
  }
  container.innerHTML = '';

  const summaryStore = gameState.elementBonusSummary || {};
  const rarityEntries = INFO_BONUS_RARITIES.map(rarityId => {
    const existing = summaryStore[rarityId];
    if (existing) {
      return { ...existing, type: existing.type || 'rarity' };
    }
    const label = RARITY_LABEL_MAP.get(rarityId) || rarityId;
    return {
      type: 'rarity',
      rarityId,
      label,
      copies: 0,
      uniques: 0,
      duplicates: 0,
      totalUnique: getRarityPoolSize(rarityId),
      isComplete: false,
      clickFlatTotal: 0,
      autoFlatTotal: 0,
      multiplierPerClick: 1,
      multiplierPerSecond: 1,
      critChanceAdd: 0,
      critMultiplierAdd: 0,
      activeLabels: [],
      ticketIntervalSeconds: null,
      offlineMultiplier: 1,
      overflowDuplicates: 0,
      frenzyChanceMultiplier: 1
    };
  });

  const familyEntries = summaryStore.families && typeof summaryStore.families === 'object'
    ? Object.values(summaryStore.families).map(entry => ({ ...entry, type: entry.type || 'family' }))
    : [];
  familyEntries.sort((a, b) => {
    const labelA = typeof a.label === 'string' ? a.label : '';
    const labelB = typeof b.label === 'string' ? b.label : '';
    return compareTextLocalized(labelA, labelB);
  });

  const entries = [...rarityEntries, ...familyEntries];

  entries.forEach(summary => {
    const card = document.createElement('article');
    card.className = 'element-bonus-card';
    card.setAttribute('role', 'listitem');
    const summaryType = summary.type || (summary.familyId ? 'family' : 'rarity');
    if (summaryType === 'family') {
      card.dataset.familyId = summary.familyId || '';
      card.classList.add('element-bonus-card--family');
    } else if (summary.rarityId) {
      card.dataset.rarityId = summary.rarityId;
    }
    if (summary.isComplete) {
      card.classList.add('element-bonus-card--complete');
    }

    const meta = document.createElement('div');
    meta.className = 'element-bonus-card__meta';

    const header = document.createElement('header');
    header.className = 'element-bonus-card__header';

    const title = document.createElement('h4');
    title.textContent = summary.label;
    header.appendChild(title);

    if (summaryType === 'family') {
      const status = document.createElement('span');
      status.className = 'element-bonus-card__status';
      status.textContent = summary.isComplete
        ? t('scripts.info.collections.complete')
        : t('scripts.info.collections.inProgress');
      header.appendChild(status);
    }

    meta.appendChild(header);

    const counts = document.createElement('dl');
    counts.className = 'element-bonus-counts';

    const addCountRow = (labelText, valueText, options = {}) => {
      if (!labelText || valueText == null) return;
      const row = document.createElement('div');
      row.className = 'element-bonus-count';
      if (options.highlight) {
        row.classList.add('element-bonus-count--highlight');
      }
      const labelEl = document.createElement('dt');
      labelEl.className = 'element-bonus-count__label';
      labelEl.textContent = labelText;
      const valueEl = document.createElement('dd');
      valueEl.className = 'element-bonus-count__value';
      valueEl.textContent = valueText;
      row.append(labelEl, valueEl);
      counts.appendChild(row);
    };

    const copiesCount = Number(summary.copies || 0);
    const uniqueCount = Number(summary.uniques || 0);
    const totalUnique = Number(summary.totalUnique || 0);
    const uniqueDisplay = totalUnique > 0
      ? `${formatIntegerLocalized(uniqueCount)} / ${formatIntegerLocalized(totalUnique)}`
      : formatIntegerLocalized(uniqueCount);

    addCountRow('Éléments uniques', uniqueDisplay);
    addCountRow('Total', formatIntegerLocalized(copiesCount));

    if (counts.children.length) {
      meta.appendChild(counts);
    }

    card.appendChild(meta);

    const details = document.createElement('div');
    details.className = 'element-bonus-card__details';

    const productionEntries = [];
    const appendProduction = (labelText, valueText) => {
      if (!valueText) return;
      productionEntries.push({ label: labelText, value: valueText });
    };

    appendProduction('APC +', formatElementFlatBonus(summary.clickFlatTotal));
    appendProduction('APS +', formatElementFlatBonus(summary.autoFlatTotal));
    appendProduction('APC ×', formatElementMultiplierBonus(summary.multiplierPerClick));
    appendProduction('APS ×', formatElementMultiplierBonus(summary.multiplierPerSecond));

    const critParts = [];
    const critChanceText = formatElementCritChanceBonus(summary.critChanceAdd);
    if (critChanceText) {
      critParts.push(`+${critChanceText}`);
    }
    const critMultiplierText = formatElementCritMultiplierBonus(summary.critMultiplierAdd);
    if (critMultiplierText) {
      critParts.push(`+${critMultiplierText}×`);
    }
    if (critParts.length) {
      appendProduction('Critiques', critParts.join(' · '));
    }

    const productionEffectSet = new Set();
    productionEntries.forEach(entry => {
      if (!entry) return;
      const label = typeof entry.label === 'string' ? entry.label.trim() : '';
      const value = entry.value != null ? String(entry.value).trim() : '';
      if (!value) return;
      const combos = [
        `${label}${value}`,
        label ? `${label} ${value}` : null,
        value
      ];
      combos.forEach(text => {
        if (!text) return;
        const normalized = normalizeCollectionDetailText(text);
        if (normalized) {
          productionEffectSet.add(normalized);
        }
      });
    });

    const specialEntries = [];

    const ticketIntervalText = formatElementTicketInterval(summary.ticketIntervalSeconds);
    if (ticketIntervalText) {
      specialEntries.push({ label: 'Tickets quantiques', value: ticketIntervalText });
    }

    const offlineText = formatElementMultiplierDisplay(summary.offlineMultiplier);
    if (offlineText) {
      specialEntries.push({ label: 'Collecte hors ligne', value: offlineText });
    }

    const frenzyText = formatElementMultiplierDisplay(summary.frenzyChanceMultiplier);
    if (frenzyText) {
      specialEntries.push({ label: 'Chance de frénésie', value: frenzyText });
    }

    const overflowCount = Number(summary.overflowDuplicates || 0);
    if (overflowCount > 0) {
      specialEntries.push({
        label: 'Surcharge fractale',
        value: `${formatIntegerLocalized(overflowCount)} doublons`
      });
    }

    const specialEffectSet = new Set();
    specialEntries.forEach(entry => {
      if (!entry) return;
      const label = typeof entry.label === 'string' ? entry.label.trim() : '';
      const value = entry.value != null ? String(entry.value).trim() : '';
      if (!value) return;
      const combos = [
        `${label}${value}`,
        label ? `${label} ${value}` : null,
        value
      ];
      combos.forEach(text => {
        if (!text) return;
        const normalized = normalizeCollectionDetailText(text);
        if (normalized) {
          specialEffectSet.add(normalized);
        }
      });
    });

    const rawLabels = Array.isArray(summary.activeLabels) ? summary.activeLabels : [];
    const highlightLabels = rawLabels
      .map(raw => {
        if (raw == null) {
          return null;
        }
        if (typeof raw === 'string') {
          const labelText = stripBonusLabelPrefix(raw, summary.label);
          if (!labelText || !labelText.trim()) {
            return null;
          }
          return { label: labelText.trim(), description: null };
        }
        if (typeof raw === 'object') {
          const rawLabel = typeof raw.label === 'string' ? raw.label : '';
          const labelText = stripBonusLabelPrefix(rawLabel, summary.label);
          if (!labelText || !labelText.trim()) {
            return null;
          }
          const descriptionParts = [];
          const seenDescriptions = new Set();
          const addPart = text => {
            const normalized = normalizeCollectionDetailText(text);
            if (!normalized || seenDescriptions.has(normalized)) {
              return;
            }
            seenDescriptions.add(normalized);
            descriptionParts.push(text.trim());
          };
          if (typeof raw.description === 'string' && raw.description.trim()) {
            addPart(raw.description.trim());
          } else {
            if (Array.isArray(raw.effects)) {
              raw.effects.forEach(effect => {
                if (typeof effect !== 'string') return;
                const trimmed = effect.trim();
                if (!trimmed) return;
                const normalized = normalizeCollectionDetailText(trimmed);
                if (normalized && (productionEffectSet.has(normalized) || specialEffectSet.has(normalized))) {
                  return;
                }
                addPart(trimmed);
              });
            }
            if (Array.isArray(raw.notes)) {
              raw.notes.forEach(note => {
                if (typeof note === 'string' && note.trim()) {
                  addPart(note.trim());
                }
              });
            }
          }
          if (!descriptionParts.length) {
            return null;
          }
          return { label: labelText.trim(), description: descriptionParts.join(' · ') };
        }
        return null;
      })
      .filter(entry => entry && entry.label);

    if (highlightLabels.length) {
      const section = document.createElement('section');
      section.className = 'element-bonus-section';

      const title = document.createElement('h5');
      title.className = 'element-bonus-section__title';
      title.textContent = t('scripts.info.sections.activeBoosts');
      section.appendChild(title);

      const tags = document.createElement('div');
      tags.className = 'element-bonus-tags';

      highlightLabels.forEach(entry => {
        const tag = document.createElement('span');
        tag.className = 'element-bonus-tag';
        tag.textContent = entry.label;
        if (entry.description) {
          tag.dataset.tooltip = entry.description;
          tag.setAttribute('tabindex', '0');
          tag.setAttribute('aria-label', `${entry.label} : ${entry.description}`);
        }
        tags.appendChild(tag);
      });

      section.appendChild(tags);
      details.appendChild(section);
    }
    if (specialEntries.length && summary.rarityId !== 'mythique') {
      const section = document.createElement('section');
      section.className = 'element-bonus-section';

      const title = document.createElement('h5');
      title.className = 'element-bonus-section__title';
      title.textContent = t('scripts.info.sections.specialEffects');
      section.appendChild(title);

      const list = document.createElement('ul');
      list.className = 'element-bonus-specials';

      specialEntries.forEach(entry => {
        const item = document.createElement('li');
        item.className = 'element-bonus-specials__item';

        const labelEl = document.createElement('span');
        labelEl.className = 'element-bonus-specials__label';
        labelEl.textContent = entry.label;

        const valueEl = document.createElement('span');
        valueEl.className = 'element-bonus-specials__value';
        valueEl.textContent = entry.value;

        item.append(labelEl, valueEl);
        list.appendChild(item);
      });

      section.appendChild(list);
      details.appendChild(section);
    }

    if (!details.children.length) {
      const empty = document.createElement('p');
      empty.className = 'element-bonus-empty';
      empty.textContent = t('scripts.info.sections.inactiveBonuses');
      details.appendChild(empty);
    }

    card.appendChild(details);
    container.appendChild(card);
  });
}

function formatShopFlatBonus(value) {
  if (value == null) return null;
  const layered = value instanceof LayeredNumber ? value : toLayeredValue(value, 0);
  if (layered.isZero() || layered.sign <= 0) {
    return null;
  }
  const normalized = normalizeProductionUnit(layered);
  if (normalized.isZero() || normalized.sign <= 0) {
    return null;
  }
  return normalized.toString();
}

function formatShopMultiplierBonus(value) {
  if (value == null) return null;
  const layered = value instanceof LayeredNumber ? value : toMultiplierLayered(value);
  return isLayeredOne(layered) ? null : formatMultiplier(layered);
}

function getShopBuildingTexts(def) {
  if (!def || typeof def !== 'object') {
    return { name: '', description: '' };
  }
  const id = typeof def.id === 'string' ? def.id.trim() : '';
  const baseKey = id ? `config.shop.buildings.${id}` : '';
  const fallbackName = typeof def.name === 'string' && def.name.trim() ? def.name.trim() : id;
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
}

function collectShopBonusSummaries() {
  const productionBase = gameState.productionBase || createEmptyProductionBreakdown();
  const clickEntry = productionBase.perClick || createEmptyProductionEntry();
  const autoEntry = productionBase.perSecond || createEmptyProductionEntry();

  const summaries = new Map();
  UPGRADE_DEFS.forEach(def => {
    const texts = getShopBuildingTexts(def);
    summaries.set(def.id, {
      id: def.id,
      name: texts.name || def.name || def.id,
      effectSummary: texts.description || def.effectSummary || def.description || '',
      level: getUpgradeLevel(gameState.upgrades, def.id),
      clickAdd: LayeredNumber.zero(),
      autoAdd: LayeredNumber.zero(),
      clickMult: LayeredNumber.one(),
      autoMult: LayeredNumber.one()
    });
  });

  const accumulateAddition = (list, key) => {
    if (!Array.isArray(list)) return;
    list.forEach(entry => {
      if (!entry || entry.source !== 'shop') return;
      const summary = summaries.get(entry.id);
      if (!summary) return;
      const value = entry.value instanceof LayeredNumber
        ? entry.value
        : toLayeredValue(entry.value, 0);
      if (value.isZero() || value.sign <= 0) return;
      summary[key] = summary[key].add(value);
    });
  };

  const accumulateMultiplier = (list, key) => {
    if (!Array.isArray(list)) return;
    list.forEach(entry => {
      if (!entry || entry.source !== 'shop') return;
      const summary = summaries.get(entry.id);
      if (!summary) return;
      const value = entry.value instanceof LayeredNumber
        ? entry.value
        : toMultiplierLayered(entry.value);
      summary[key] = summary[key].multiply(value);
    });
  };

  accumulateAddition(clickEntry.additions, 'clickAdd');
  accumulateAddition(autoEntry.additions, 'autoAdd');
  accumulateMultiplier(clickEntry.multipliers, 'clickMult');
  accumulateMultiplier(autoEntry.multipliers, 'autoMult');

  return UPGRADE_DEFS.map(def => summaries.get(def.id)).filter(Boolean);
}

function renderShopBonuses() {
  const container = elements.infoShopBonuses;
  if (!container) return;
  container.innerHTML = '';

  const summaries = collectShopBonusSummaries();
  const summaryById = new Map();
  summaries.forEach(summary => {
    summaryById.set(summary.id, summary);
  });

  const unlocks = getShopUnlockSet();
  const visibleDefs = UPGRADE_DEFS.filter(def => unlocks.has(def.id));
  const visibleSummaries = visibleDefs
    .map(def => summaryById.get(def.id))
    .filter(Boolean);

  if (!visibleSummaries.length) {
    const empty = document.createElement('p');
    empty.className = 'element-bonus-empty shop-bonus-empty';
    empty.textContent = t('scripts.info.shop.noneAvailable');
    container.appendChild(empty);
    lastVisibleShopBonusIds = new Set();
    return;
  }

  const previouslyVisibleIds = lastVisibleShopBonusIds;
  const nextVisibleIds = new Set();

  visibleSummaries.forEach(summary => {
    nextVisibleIds.add(summary.id);
    const card = document.createElement('article');
    card.className = 'element-bonus-card shop-bonus-card';
    card.setAttribute('role', 'listitem');
    card.dataset.upgradeId = summary.id;
    if (!summary.level) {
      card.classList.add('shop-bonus-card--inactive');
    }

    if (!previouslyVisibleIds.has(summary.id)) {
      card.classList.add('shop-bonus-card--revealed');
      card.addEventListener('animationend', () => {
        card.classList.remove('shop-bonus-card--revealed');
      }, { once: true });
    }

    const header = document.createElement('header');
    header.className = 'element-bonus-card__header shop-bonus-card__header';

    const title = document.createElement('h4');
    title.textContent = summary.name;
    header.appendChild(title);

    const status = document.createElement('span');
    status.className = 'element-bonus-card__status shop-bonus-card__status';
    if (summary.level > 0) {
      status.textContent = t('scripts.info.shop.level', {
        level: formatIntegerLocalized(summary.level)
      });
    } else {
      status.textContent = t('scripts.info.shop.notPurchased');
    }
    header.appendChild(status);

    card.appendChild(header);

    if (summary.effectSummary) {
      const desc = document.createElement('p');
      desc.className = 'shop-bonus-card__summary';
      desc.textContent = summary.effectSummary;
      card.appendChild(desc);
    }

    if (summary.level > 0) {
      const effects = document.createElement('ul');
      effects.className = 'element-bonus-effects shop-bonus-effects';
      let hasEffect = false;

      const appendEffect = (labelText, valueText) => {
        if (!valueText) return;
        const item = document.createElement('li');
        item.className = 'element-bonus-effects__item';
        const labelEl = document.createElement('span');
        labelEl.className = 'element-bonus-effects__label';
        labelEl.textContent = labelText;
        const valueEl = document.createElement('span');
        valueEl.className = 'element-bonus-effects__value';
        valueEl.textContent = valueText;
        item.append(labelEl, valueEl);
        effects.appendChild(item);
        hasEffect = true;
      };

      appendEffect(t('scripts.info.shop.bonus.apcFlat'), formatShopFlatBonus(summary.clickAdd));
      appendEffect(t('scripts.info.shop.bonus.apsFlat'), formatShopFlatBonus(summary.autoAdd));
      appendEffect(t('scripts.info.shop.bonus.apcMult'), formatShopMultiplierBonus(summary.clickMult));
      appendEffect(t('scripts.info.shop.bonus.apsMult'), formatShopMultiplierBonus(summary.autoMult));

      if (hasEffect) {
        card.appendChild(effects);
      } else {
        const empty = document.createElement('p');
        empty.className = 'element-bonus-empty shop-bonus-empty';
        empty.textContent = t('scripts.info.shop.pendingThreshold');
        card.appendChild(empty);
      }
    } else {
      const locked = document.createElement('p');
      locked.className = 'element-bonus-empty shop-bonus-empty';
      locked.textContent = t('scripts.info.shop.locked');
      card.appendChild(locked);
    }

    container.appendChild(card);
  });

  lastVisibleShopBonusIds = nextVisibleIds;
}

function computeRarityMultiplierProduct(store) {
  if (!store) return LayeredNumber.one();
  if (store instanceof Map) {
    let product = LayeredNumber.one();
    store.forEach(raw => {
      const numeric = Number(raw);
      if (Number.isFinite(numeric) && numeric > 0) {
        product = product.multiplyNumber(numeric);
      }
    });
    return product;
  }
  if (typeof store === 'object' && store !== null) {
    return Object.values(store).reduce((product, raw) => {
      const numeric = Number(raw);
      if (Number.isFinite(numeric) && numeric > 0) {
        return product.multiplyNumber(numeric);
      }
      return product;
    }, LayeredNumber.one());
  }
  return LayeredNumber.one();
}

function updateSessionStats() {
  const session = gameState.stats?.session;
  if (!session) return;

  if (elements.infoSessionAtoms) {
    const atoms = session.atomsGained instanceof LayeredNumber
      ? session.atomsGained
      : LayeredNumber.fromJSON(session.atomsGained);
    elements.infoSessionAtoms.textContent = atoms.toString();
  }
  if (elements.infoSessionClicks) {
    elements.infoSessionClicks.textContent = formatIntegerLocalized(Number(session.manualClicks || 0));
  }
  if (elements.infoSessionApcAtoms) {
    const apc = getLayeredStat(session, 'apcAtoms');
    elements.infoSessionApcAtoms.textContent = apc.toString();
  }
  if (elements.infoSessionApsAtoms) {
    const aps = getLayeredStat(session, 'apsAtoms');
    elements.infoSessionApsAtoms.textContent = aps.toString();
  }
  if (elements.infoSessionOfflineAtoms) {
    const offline = getLayeredStat(session, 'offlineAtoms');
    elements.infoSessionOfflineAtoms.textContent = offline.toString();
  }
  if (elements.infoSessionDuration) {
    elements.infoSessionDuration.textContent = formatDuration(session.onlineTimeMs);
  }
}

function updateGlobalStats() {
  const global = gameState.stats?.global;
  if (!global) return;

  if (elements.infoGlobalAtoms) {
    elements.infoGlobalAtoms.textContent = gameState.lifetime.toString();
  }
  if (elements.infoGlobalClicks) {
    elements.infoGlobalClicks.textContent = formatIntegerLocalized(Number(global.manualClicks || 0));
  }
  if (elements.infoGlobalApcAtoms) {
    const apc = getLayeredStat(global, 'apcAtoms');
    elements.infoGlobalApcAtoms.textContent = apc.toString();
  }
  if (elements.infoGlobalApsAtoms) {
    const aps = getLayeredStat(global, 'apsAtoms');
    elements.infoGlobalApsAtoms.textContent = aps.toString();
  }
  if (elements.infoGlobalOfflineAtoms) {
    const offline = getLayeredStat(global, 'offlineAtoms');
    elements.infoGlobalOfflineAtoms.textContent = offline.toString();
  }
  if (elements.infoGlobalDuration) {
    elements.infoGlobalDuration.textContent = formatDuration(global.playTimeMs);
  }
}

function updateInfoPanels() {
  const production = gameState.production;
  if (production) {
    renderProductionBreakdown(elements.infoApsBreakdown, production.perSecond, 'perSecond');
    renderProductionBreakdown(elements.infoApcBreakdown, production.perClick, 'perClick');
  } else {
    renderProductionBreakdown(elements.infoApsBreakdown, null, 'perSecond');
    renderProductionBreakdown(elements.infoApcBreakdown, null, 'perClick');
  }

  updateSessionStats();
  updateGlobalStats();
  renderElementBonuses();
  renderShopBonuses();
}

