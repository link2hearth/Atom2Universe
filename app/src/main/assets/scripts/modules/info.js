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

function getCurrentLanguage() {
  const api = getI18nApi();
  if (api && typeof api.getCurrentLanguage === 'function') {
    const language = api.getCurrentLanguage();
    if (typeof language === 'string' && language.trim()) {
      return language.trim();
    }
  }
  return 'fr';
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

function toNonNegativeInteger(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 0;
  }
  return Math.max(0, Math.floor(numeric));
}

function translateOrDefault(key, fallback = '', params) {
  if (typeof key !== 'string' || !key.trim()) {
    return fallback;
  }
  const normalizedKey = key.trim();
  const api = getI18nApi();
  const translator = api && typeof api.t === 'function'
    ? api.t.bind(api)
    : (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function'
        ? globalThis.t
        : (typeof t === 'function' ? t : null));
  if (translator) {
    try {
      const translated = translator(normalizedKey, params);
      if (typeof translated === 'string') {
        const trimmed = translated.trim();
        if (!trimmed) {
          return fallback;
        }
        const stripped = trimmed.replace(/^!+/, '').replace(/!+$/, '');
        if (trimmed !== normalizedKey && stripped !== normalizedKey) {
          return translated;
        }
      } else if (translated != null) {
        return translated;
      }
    } catch (error) {
      console.warn('Unable to translate key', normalizedKey, error);
      return fallback;
    }
  }
  if (typeof fallback === 'string') {
    return fallback;
  }
  const strippedKey = normalizedKey;
  if (strippedKey) {
    return strippedKey;
  }
  return fallback;
}

function translateCollectionEffect(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.effects.${key}`, fallback, params);
}

function translateCollectionNote(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.notes.${key}`, fallback, params);
}

function translateCollectionLabel(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.labels.${key}`, fallback, params);
}

function translateCollectionThreshold(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.threshold.${key}`, fallback, params);
}

function translateCollectionOverview(key, fallback, params) {
  if (!key) {
    return fallback;
  }
  return translateOrDefault(`scripts.app.table.collection.overview.${key}`, fallback, params);
}

function translateCollectionUnit(unit, count, fallbackSingular, fallbackPlural) {
  const suffix = count === 1 ? 'singular' : 'plural';
  const fallback = count === 1 ? fallbackSingular : fallbackPlural;
  return translateOrDefault(`scripts.app.table.collection.units.${unit}.${suffix}`, fallback, { count });
}

function translateCollectionTarget(target) {
  if (target === 'production') {
    return translateOrDefault('scripts.app.table.collection.targets.production', 'production');
  }
  return target;
}

function resolveConfigLabel(label, fallback = null) {
  const normalized = typeof label === 'string' ? label.trim() : '';
  const fallbackValue = typeof fallback === 'string' ? fallback.trim() : '';
  if (!normalized) {
    return { text: fallbackValue || null, isFallback: true };
  }
  if (normalized.startsWith('scripts.')) {
    const translated = translateOrDefault(normalized, fallbackValue);
    const trimmed = typeof translated === 'string' ? translated.trim() : '';
    if (trimmed && trimmed !== normalized) {
      return { text: trimmed, isFallback: false };
    }
    if (fallbackValue) {
      return { text: fallbackValue, isFallback: true };
    }
    return { text: null, isFallback: true };
  }
  return { text: normalized, isFallback: false };
}

function getDefaultAddLabel(context, addConfig = {}) {
  if (context === 'perCopy') {
    return {
      text: translateCollectionLabel('perCopy', 'Par copie'),
      includeRequireAllUnique: true
    };
  }
  if (context === 'setBonus') {
    const requiresFullCollection = (
      addConfig.requireAllUnique
      && (!Number.isFinite(addConfig.minCopies) || addConfig.minCopies <= 1)
      && (!Number.isFinite(addConfig.minUnique) || addConfig.minUnique <= 0)
    );
    if (requiresFullCollection) {
      return {
        text: translateCollectionLabel('fullCollection', 'Collection complète'),
        includeRequireAllUnique: false
      };
    }
    return {
      text: translateCollectionLabel('collectionBonus', 'Bonus de collection'),
      includeRequireAllUnique: true
    };
  }
  return {
    text: translateCollectionLabel('generic', 'Bonus'),
    includeRequireAllUnique: true
  };
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
    const unit = translateCollectionUnit('copy', minCopies, 'copie', 'copies');
    const countText = formatIntegerLocalized(minCopies);
    notes.push(
      translateCollectionThreshold('minCopies', `dès ${countText} ${unit}`, { count: countText, unit })
    );
  }
  if (Number.isFinite(minUnique) && minUnique > 0) {
    const unit = translateCollectionUnit('unique', minUnique, 'unique', 'uniques');
    const countText = formatIntegerLocalized(minUnique);
    notes.push(
      translateCollectionThreshold('minUnique', `minimum ${countText} ${unit}`, { count: countText, unit })
    );
  }
  if (requireAllUnique && includeRequireAllUnique) {
    notes.push(translateCollectionThreshold('requireAll', 'collection complète requise'));
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
    parts.push(translateCollectionEffect('apcMultiplier', `APC ${clickText}`, { value: clickText }));
  }
  const autoText = formatElementMultiplierDisplay(entry.perSecond);
  if (autoText && autoText !== '×1') {
    parts.push(translateCollectionEffect('apsMultiplier', `APS ${autoText}`, { value: autoText }));
  }
  if (parts.length) {
    const effectsText = parts.join(' · ');
    notes.push(
      translateCollectionNote('amplify', `Amplifie ${targetLabel} : ${effectsText}`, {
        target: targetLabel,
        effects: effectsText
      })
    );
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

  const defaultLabelInfo = getDefaultAddLabel(context, addConfig);
  let includeRequireAllUnique = true;
  let baseLabel = null;

  const configLabelInfo = resolveConfigLabel(addConfig.label, defaultLabelInfo.text);
  if (configLabelInfo.text) {
    baseLabel = configLabelInfo.text;
    if (configLabelInfo.isFallback) {
      includeRequireAllUnique = defaultLabelInfo.includeRequireAllUnique;
    }
  }

  if (!baseLabel) {
    const overrideLabelInfo = resolveConfigLabel(overrideLabel, defaultLabelInfo.text);
    if (overrideLabelInfo.text) {
      baseLabel = overrideLabelInfo.text;
      if (overrideLabelInfo.isFallback) {
        includeRequireAllUnique = defaultLabelInfo.includeRequireAllUnique;
      }
    }
  }

  if (!baseLabel) {
    baseLabel = defaultLabelInfo.text;
    includeRequireAllUnique = defaultLabelInfo.includeRequireAllUnique;
  }

  const thresholdText = formatBonusThreshold(addConfig, context, { includeRequireAllUnique });
  const effects = [];

  if (Number.isFinite(clickAdd) && clickAdd !== 0) {
    const value = formatElementFlatBonus(clickAdd);
    if (value) {
      effects.push(translateCollectionEffect('apcFlat', `APC +${value}`, { value }));
    }
  }
  if (Number.isFinite(autoAdd) && autoAdd !== 0) {
    const value = formatElementFlatBonus(autoAdd);
    if (value) {
      effects.push(translateCollectionEffect('apsFlat', `APS +${value}`, { value }));
    }
  }

  if (Number.isFinite(uniqueClickAdd) && uniqueClickAdd !== 0) {
    const value = formatElementFlatBonus(uniqueClickAdd);
    if (value) {
      effects.push(
        translateCollectionEffect('apcFlatPerUnique', `APC +${value} par élément unique`, { value })
      );
    }
  }
  if (Number.isFinite(uniqueAutoAdd) && uniqueAutoAdd !== 0) {
    const value = formatElementFlatBonus(uniqueAutoAdd);
    if (value) {
      effects.push(
        translateCollectionEffect('apsFlatPerUnique', `APS +${value} par élément unique`, { value })
      );
    }
  }

  if (Number.isFinite(duplicateClickAdd) && duplicateClickAdd !== 0) {
    const value = formatElementFlatBonus(duplicateClickAdd);
    if (value) {
      effects.push(
        translateCollectionEffect('apcFlatPerDuplicate', `APC +${value} par doublon`, { value })
      );
    }
  }
  if (Number.isFinite(duplicateAutoAdd) && duplicateAutoAdd !== 0) {
    const value = formatElementFlatBonus(duplicateAutoAdd);
    if (value) {
      effects.push(
        translateCollectionEffect('apsFlatPerDuplicate', `APS +${value} par doublon`, { value })
      );
    }
  }

  const rarityNotes = formatRarityMultiplierNotes(rarityFlatMultipliers);
  if (rarityNotes.length) {
    effects.push(...rarityNotes);
  }

  if (!effects.length) {
    return [];
  }

  const effectsText = effects.join(' · ');
  const text = translateCollectionNote('effects', `${baseLabel} : ${effectsText}`, {
    label: baseLabel,
    effects: effectsText
  });
  return [thresholdText ? `${text}${thresholdText}` : text];
}

function describeCritEffect(effect, scopeLabel) {
  if (!effect) return null;
  const parts = [];
  if (Number.isFinite(effect.chanceSet) && effect.chanceSet > 0) {
    const text = formatElementCritChanceBonus(effect.chanceSet);
    if (text) {
      parts.push(translateCollectionEffect('critChanceSet', `Chance fixée à ${text}`, { value: text }));
    }
  }
  if (Number.isFinite(effect.chanceAdd) && effect.chanceAdd !== 0) {
    const text = formatElementCritChanceBonus(effect.chanceAdd);
    if (text) {
      parts.push(translateCollectionEffect('critChance', `Chance +${text}`, { value: text }));
    }
  }
  if (Number.isFinite(effect.chanceMult) && effect.chanceMult !== 1 && effect.chanceMult > 0) {
    const text = formatElementMultiplierDisplay(effect.chanceMult);
    if (text && text !== '×1') {
      parts.push(translateCollectionEffect('critChanceMultiplier', `Chance ${text}`, { value: text }));
    }
  }
  if (Number.isFinite(effect.multiplierSet) && effect.multiplierSet > 0) {
    const text = formatElementCritMultiplierBonus(effect.multiplierSet);
    if (text) {
      parts.push(
        translateCollectionEffect('critMultiplierSet', `Multiplicateur fixé à ${text}×`, { value: text })
      );
    }
  }
  if (Number.isFinite(effect.multiplierAdd) && effect.multiplierAdd !== 0) {
    const text = formatElementCritMultiplierBonus(effect.multiplierAdd);
    if (text) {
      parts.push(translateCollectionEffect('critMultiplier', `Multiplicateur +${text}×`, { value: text }));
    }
  }
  if (Number.isFinite(effect.multiplierMult) && effect.multiplierMult !== 1 && effect.multiplierMult > 0) {
    const text = formatElementMultiplierDisplay(effect.multiplierMult);
    if (text && text !== '×1') {
      parts.push(
        translateCollectionEffect('critMultiplierMultiplier', `Multiplicateur ${text}`, { value: text })
      );
    }
  }
  if (Number.isFinite(effect.maxMultiplierSet) && effect.maxMultiplierSet > 0) {
    const text = formatElementCritMultiplierBonus(effect.maxMultiplierSet);
    if (text) {
      parts.push(
        translateCollectionEffect('critCapSet', `Cap critique fixé à ${text}×`, { value: text })
      );
    }
  }
  if (Number.isFinite(effect.maxMultiplierAdd) && effect.maxMultiplierAdd !== 0) {
    const text = formatElementCritMultiplierBonus(effect.maxMultiplierAdd);
    if (text) {
      parts.push(translateCollectionEffect('critCapAdd', `Cap critique +${text}×`, { value: text }));
    }
  }
  if (Number.isFinite(effect.maxMultiplierMult) && effect.maxMultiplierMult !== 1 && effect.maxMultiplierMult > 0) {
    const text = formatElementMultiplierDisplay(effect.maxMultiplierMult);
    if (text && text !== '×1') {
      parts.push(
        translateCollectionEffect('critCapMultiplier', `Cap critique ${text}`, { value: text })
      );
    }
  }
  return parts.length ? `${scopeLabel} : ${parts.join(' · ')}` : null;
}

function describeCritConfig(critConfig) {
  if (!critConfig) return [];
  const results = [];
  if (critConfig.perUnique) {
    const text = describeCritEffect(
      critConfig.perUnique,
      translateCollectionLabel('critPerUnique', 'Critique par unique')
    );
    if (text) {
      results.push(text);
    }
  }
  if (critConfig.perDuplicate) {
    const text = describeCritEffect(
      critConfig.perDuplicate,
      translateCollectionLabel('critPerDuplicate', 'Critique par doublon')
    );
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
  const localizedTarget = translateCollectionTarget(targetLabel);
  const parts = [];
  if (
    Number.isFinite(multiplierConfig.base)
    && Math.abs(multiplierConfig.base - 1) > 1e-9
  ) {
    const baseText = formatMultiplier(multiplierConfig.base);
    if (baseText && baseText !== '×—') {
      const prefix = targetLabel === 'production' ? '' : `${targetLabel} `;
      const fallback = `${prefix}base ${baseText}`.trim();
      parts.push(
        translateCollectionNote('multiplierBase', fallback, {
          prefix,
          value: baseText,
          target: localizedTarget
        })
      );
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
      const everyValue = Number(multiplierConfig.every);
      const countText = formatNumberLocalized(everyValue);
      const unit = translateCollectionUnit('copy', everyValue, 'copie', 'copies');
      const prefix = targetLabel === 'production' ? '' : `${targetLabel} `;
      const fallback = `${prefix}${incrementText} toutes les ${countText} ${unit}`.trim();
      parts.push(
        translateCollectionNote('multiplierIncrement', fallback, {
          prefix,
          value: incrementText,
          count: countText,
          unit,
          target: localizedTarget
        })
      );
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
      const fallback = `${prefix}max ${capText}`.trim();
      parts.push(
        translateCollectionNote('multiplierCap', fallback, {
          prefix,
          value: capText,
          target: localizedTarget
        })
      );
    }
  }
  if (!parts.length) {
    return null;
  }
  const defaultLabel = translateCollectionLabel('multiplier', `Multiplicateur ${localizedTarget}`, { target: localizedTarget });
  const resolvedLabel = resolveConfigLabel(labelOverride, defaultLabel);
  const labelPrefix = resolvedLabel.text || defaultLabel;
  return `${labelPrefix} : ${parts.join(' · ')}`;
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
  const localizedTarget = translateCollectionTarget(targetLabel);
  const amountText = formatSignedBonus(bonusConfig.amount);
  if (!amountText) {
    return null;
  }
  const notes = [];
  if (Number.isFinite(bonusConfig.uniqueThreshold) && bonusConfig.uniqueThreshold > 0) {
    const threshold = Math.floor(bonusConfig.uniqueThreshold);
    const countText = formatIntegerLocalized(threshold);
    const unit = translateCollectionUnit('unique', threshold, 'unique', 'uniques');
    notes.push(
      translateCollectionThreshold('minUnique', `minimum ${countText} ${unit}`, { count: countText, unit })
    );
  }
  if (Number.isFinite(bonusConfig.copyThreshold) && bonusConfig.copyThreshold > 0) {
    const threshold = Math.floor(bonusConfig.copyThreshold);
    const countText = formatIntegerLocalized(threshold);
    const unit = translateCollectionUnit('copy', threshold, 'copie', 'copies');
    notes.push(
      translateCollectionThreshold('minCopies', `dès ${countText} ${unit}`, { count: countText, unit })
    );
  }
  const suffix = notes.length ? ` (${notes.join(' · ')})` : '';
  const prefixText = targetLabel === 'production' ? '' : `${localizedTarget} `;
  const detailFallback = targetLabel === 'production'
    ? `${amountText}${suffix}`
    : `${targetLabel} ${amountText}${suffix}`;
  const detail = translateCollectionNote('rarityMultiplierDetail', detailFallback, {
    prefix: prefixText,
    target: localizedTarget,
    value: amountText,
    suffix
  });
  const defaultPrefix = translateCollectionLabel('rarityMultiplier', 'Multiplicateur de rareté', {
    target: localizedTarget
  });
  const resolvedLabel = resolveConfigLabel(labelOverride, defaultPrefix);
  if (resolvedLabel.text && !resolvedLabel.isFallback) {
    return `${resolvedLabel.text} : ${detail}`;
  }
  const effectivePrefix = resolvedLabel.text || defaultPrefix;
  return `${effectivePrefix} ${detail}`.trim();
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
      parts.push(
        translateCollectionOverview(
          'ticketReduction',
          `Réduit l’intervalle des étoiles à tickets de ${reductionText}s par élément unique`,
          { value: reductionText }
        )
      );
    }
    if (
      Number.isFinite(MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS)
      && MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS > 0
    ) {
      const minText = formatSmallNumber(MYTHIQUE_TICKET_MIN_INTERVAL_SECONDS);
      if (minText) {
        parts.push(
          translateCollectionOverview('ticketMinimum', `minimum ${minText}s`, { value: minText })
        );
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
        parts.push(
          translateCollectionOverview('offlineBase', `base ${baseText}`, { value: baseText })
        );
      }
    }
    const incrementText = formatSignedBonus(MYTHIQUE_OFFLINE_PER_DUPLICATE);
    if (incrementText) {
      parts.push(
        translateCollectionOverview('offlineIncrement', `${incrementText} par doublon`, { value: incrementText })
      );
    }
    if (
      Number.isFinite(MYTHIQUE_OFFLINE_CAP)
      && MYTHIQUE_OFFLINE_CAP > 0
      && MYTHIQUE_OFFLINE_CAP !== Number.POSITIVE_INFINITY
    ) {
      const capText = formatMultiplier(MYTHIQUE_OFFLINE_CAP);
      if (capText && capText !== '×—') {
        parts.push(
          translateCollectionOverview('offlineCap', `max ${capText}`, { value: capText })
        );
      }
    }
    if (parts.length) {
      const detail = parts.join(' · ');
      results.push(
        translateCollectionOverview('offlineSummary', `Collecte hors ligne ${detail}`, { detail })
      );
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
      const parts = [
        translateCollectionOverview('overflowFlat', `APC/APS +${overflowValue} par doublon`, { value: overflowValue })
      ];
      if (Number.isFinite(threshold) && threshold > 0 && thresholdText) {
        const translatedUnit = translateCollectionUnit('duplicate', threshold, 'doublon', 'doublons');
        parts.push(
          translateCollectionOverview(
            'overflowThreshold',
            `au-delà de ${thresholdText} ${unit}`,
            { count: thresholdText, unit: translatedUnit }
          )
        );
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
      results.push(
        translateCollectionOverview(
          'frenzyComplete',
          `Chance de frénésie ${frenzyText} (collection complète requise)`,
          { value: frenzyText }
        )
      );
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
    pushOverview(
      translateCollectionOverview(
        'singularitySynergy',
        'Synergie Supernovae : Bonus ×2 si la collection Supernovae est complète'
      )
    );
  } else if (rarityId === 'singulier') {
    pushOverview(
      translateCollectionOverview(
        'stellaireSynergy',
        'Synergie Étoiles simples : Compléter la collection double tous les bonus Étoiles simples'
      )
    );
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

const specialCardOverlayState = {
  queue: [],
  active: null,
  lastFocus: null,
  hideTimer: null,
  initialized: false,
  navigation: null,
  swipe: {
    active: false,
    startX: 0,
    startY: 0,
    lastX: 0,
    lastY: 0,
    hasDirection: false,
    isHorizontal: false
  }
};

const SPECIAL_CARD_SWIPE_THRESHOLD = 60;
const SPECIAL_CARD_OVERLAY_TOUCH_OPTIONS = (() => {
  if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
    return false;
  }
  let supportsPassive = false;
  const testListener = () => {};
  try {
    const options = Object.defineProperty({}, 'passive', {
      get() {
        supportsPassive = true;
        return false;
      }
    });
    window.addEventListener('test-passive', testListener, options);
    window.removeEventListener('test-passive', testListener, options);
  } catch (error) {
    supportsPassive = false;
  }
  return supportsPassive ? { passive: false } : false;
})();

function resetSpecialCardOverlaySwipeState() {
  if (!specialCardOverlayState.swipe) {
    specialCardOverlayState.swipe = {
      active: false,
      startX: 0,
      startY: 0,
      lastX: 0,
      lastY: 0,
      hasDirection: false,
      isHorizontal: false
    };
    return;
  }
  specialCardOverlayState.swipe.active = false;
  specialCardOverlayState.swipe.startX = 0;
  specialCardOverlayState.swipe.startY = 0;
  specialCardOverlayState.swipe.lastX = 0;
  specialCardOverlayState.swipe.lastY = 0;
  specialCardOverlayState.swipe.hasDirection = false;
  specialCardOverlayState.swipe.isHorizontal = false;
}

function resetSpecialCardOverlayNavigation() {
  specialCardOverlayState.navigation = null;
}

function getCollectionDefinitionsForNavigation(collectionType) {
  if (collectionType === 'permanent') {
    return getPermanentBonusImageDefinitions();
  }
  if (collectionType === 'permanent2') {
    return getSecondaryPermanentBonusImageDefinitions();
  }
  return getBonusImageDefinitions();
}

function getCollectionStorageForNavigation(collectionType) {
  if (collectionType === 'permanent' || collectionType === 'permanent2') {
    return getPermanentBonusImageCollection();
  }
  return getBonusImageCollection();
}

function updateSpecialCardOverlayNavigation(card) {
  if (!card || card.type !== 'image') {
    resetSpecialCardOverlayNavigation();
    return;
  }

  const rawCollectionType = typeof card.collectionType === 'string'
    ? card.collectionType.toLowerCase()
    : '';
  const collectionType = rawCollectionType === 'permanent'
    ? 'permanent'
    : (rawCollectionType === 'permanent2' ? 'permanent2' : 'optional');
  const definitions = getCollectionDefinitionsForNavigation(collectionType);
  const collection = getCollectionStorageForNavigation(collectionType);
  const owned = buildOwnedCollectionEntries(definitions, collection, 'image');

  if (!Array.isArray(owned) || owned.length < 2) {
    resetSpecialCardOverlayNavigation();
    return;
  }

  owned.sort(compareCollectionImagesByAcquisition);
  const ids = owned.map(entry => entry.id).filter(Boolean);
  const targetId = card.cardId || (card.definition && card.definition.id) || '';
  const currentIndex = ids.indexOf(targetId);

  if (currentIndex === -1 || ids.length < 2) {
    resetSpecialCardOverlayNavigation();
    return;
  }

  specialCardOverlayState.navigation = {
    ids,
    index: currentIndex,
    collectionType
  };
}

function isSpecialCardOverlayNavigationAvailable() {
  const nav = specialCardOverlayState.navigation;
  return !!(nav && Array.isArray(nav.ids) && nav.ids.length >= 2);
}

function navigateSpecialCardOverlay(step) {
  if (!isSpecialCardOverlayNavigationAvailable()) {
    return;
  }

  const nav = specialCardOverlayState.navigation;
  const total = nav.ids.length;
  const nextIndex = ((nav.index ?? 0) + step + total) % total;
  const nextId = nav.ids[nextIndex];
  nav.index = nextIndex;
  if (nextId) {
    showSpecialCardFromCollection(nextId, 'image');
  }
}

function handleSpecialCardOverlayTouchStart(event) {
  if (!isSpecialCardOverlayNavigationAvailable()) {
    return;
  }
  if (event.target && event.target.closest && event.target.closest('.gacha-card-overlay__close')) {
    return;
  }
  if (!event.changedTouches || !event.changedTouches.length) {
    return;
  }
  const touch = event.changedTouches[0];
  const swipe = specialCardOverlayState.swipe;
  swipe.active = true;
  swipe.startX = touch.clientX;
  swipe.startY = touch.clientY;
  swipe.lastX = touch.clientX;
  swipe.lastY = touch.clientY;
  swipe.hasDirection = false;
  swipe.isHorizontal = false;
}

function handleSpecialCardOverlayTouchMove(event) {
  const swipe = specialCardOverlayState.swipe;
  if (!swipe || !swipe.active) {
    return;
  }
  if (!event.changedTouches || !event.changedTouches.length) {
    return;
  }
  const touch = event.changedTouches[0];
  swipe.lastX = touch.clientX;
  swipe.lastY = touch.clientY;
  const deltaX = swipe.lastX - swipe.startX;
  const deltaY = swipe.lastY - swipe.startY;
  if (!swipe.hasDirection) {
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);
    if (absX > 8 || absY > 8) {
      swipe.hasDirection = true;
      swipe.isHorizontal = absX >= absY;
    }
  }
  if (swipe.isHorizontal) {
    event.preventDefault();
  }
}

function handleSpecialCardOverlayTouchEnd(event) {
  const swipe = specialCardOverlayState.swipe;
  if (!swipe || !swipe.active) {
    return;
  }
  if (!event.changedTouches || !event.changedTouches.length) {
    resetSpecialCardOverlaySwipeState();
    return;
  }
  const touch = event.changedTouches[0];
  const deltaX = touch.clientX - swipe.startX;
  const deltaY = touch.clientY - swipe.startY;
  const absX = Math.abs(deltaX);
  const absY = Math.abs(deltaY);
  const isHorizontal = swipe.hasDirection ? swipe.isHorizontal : absX >= absY;
  resetSpecialCardOverlaySwipeState();
  if (!isHorizontal || absX < SPECIAL_CARD_SWIPE_THRESHOLD) {
    return;
  }
  if (!isSpecialCardOverlayNavigationAvailable()) {
    return;
  }
  if (deltaX < 0) {
    navigateSpecialCardOverlay(1);
  } else {
    navigateSpecialCardOverlay(-1);
  }
}

function handleSpecialCardOverlayTouchCancel() {
  resetSpecialCardOverlaySwipeState();
}

function getSpecialCardDefinitions() {
  return Array.isArray(GACHA_SPECIAL_CARD_DEFINITIONS)
    ? GACHA_SPECIAL_CARD_DEFINITIONS
    : [];
}

function getSpecialCardCollection() {
  if (gameState.gachaCards && typeof gameState.gachaCards === 'object') {
    return gameState.gachaCards;
  }
  return {};
}

function getBonusImageDefinitions() {
  return Array.isArray(GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS)
    ? GACHA_OPTIONAL_BONUS_IMAGE_DEFINITIONS
    : [];
}

function getBonusImageDefinition(imageId) {
  if (!imageId) {
    return null;
  }
  return getBonusImageDefinitions().find(def => def.id === imageId)
    || (Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)
      ? GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS.find(def => def.id === imageId)
      : null)
    || (Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)
      ? GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS.find(def => def.id === imageId)
      : null)
    || null;
}

function getBonusImageCollection() {
  if (gameState.gachaImages && typeof gameState.gachaImages === 'object') {
    return gameState.gachaImages;
  }
  return {};
}

function getPermanentBonusImageDefinitions() {
  return Array.isArray(GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS)
    ? GACHA_PERMANENT_BONUS_IMAGE_DEFINITIONS
    : [];
}

function getSecondaryPermanentBonusImageDefinitions() {
  return Array.isArray(GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS)
    ? GACHA_SECONDARY_PERMANENT_BONUS_IMAGE_DEFINITIONS
    : [];
}

function getPermanentBonusImageCollection() {
  if (gameState.gachaBonusImages && typeof gameState.gachaBonusImages === 'object') {
    return gameState.gachaBonusImages;
  }
  return {};
}

function resolveSpecialCardLabel(cardId) {
  if (typeof resolveSpecialGachaCardLabel === 'function') {
    const resolved = resolveSpecialGachaCardLabel(cardId);
    if (resolved) {
      return resolved;
    }
  }
  const definition = getSpecialCardDefinitions().find(def => def.id === cardId);
  if (definition) {
    const fallback = definition.labelFallback || `Carte ${cardId}`;
    return translateOrDefault(definition.labelKey, fallback);
  }
  return translateOrDefault(`scripts.gacha.cards.names.${cardId}`, `Carte ${cardId}`);
}

function resolveBonusImageLabel(imageId) {
  const definition = getBonusImageDefinition(imageId);
  if (!definition) {
    return translateOrDefault(`scripts.gacha.images.names.${imageId}`, `Image ${imageId}`);
  }
  const language = getCurrentLanguage();
  if (definition.names && typeof definition.names === 'object') {
    const direct = definition.names[language];
    if (typeof direct === 'string' && direct.trim()) {
      return direct.trim();
    }
    const [base] = language.split('-');
    if (base && typeof definition.names[base] === 'string' && definition.names[base].trim()) {
      return definition.names[base].trim();
    }
  }
  if (definition.labelKey) {
    const translated = translateOrDefault(definition.labelKey, definition.labelFallback);
    if (translated) {
      return translated;
    }
  }
  if (definition.labelFallback) {
    return definition.labelFallback;
  }
  return translateOrDefault(`scripts.gacha.images.names.${imageId}`, `Image ${imageId}`);
}

function normalizeSpecialCardReward(reward) {
  if (!reward) {
    return null;
  }
  let cardId = typeof reward.cardId === 'string' ? reward.cardId.trim() : '';
  if (!cardId && typeof reward === 'string') {
    cardId = reward.trim();
  }
  if (!cardId && typeof reward.id === 'string') {
    cardId = reward.id.trim();
  }
  if (!cardId) {
    return null;
  }
  const rewardType = reward.type === 'image' ? 'image' : 'card';
  const definition = rewardType === 'image'
    ? getBonusImageDefinition(cardId)
    : (typeof getSpecialGachaCardDefinition === 'function'
        ? getSpecialGachaCardDefinition(cardId)
        : getSpecialCardDefinitions().find(def => def.id === cardId));
  const collectionType = rewardType === 'image'
    ? (typeof reward.collectionType === 'string'
      ? reward.collectionType.toLowerCase()
      : (definition?.collectionType || 'optional'))
    : null;
  const label = reward.label
    ? reward.label
    : (rewardType === 'image'
        ? resolveBonusImageLabel(cardId)
        : resolveSpecialCardLabel(cardId));
  const collection = rewardType === 'image'
    ? ((collectionType === 'permanent' || collectionType === 'permanent2')
        ? getPermanentBonusImageCollection()
        : getBonusImageCollection())
    : getSpecialCardCollection();
  const stored = collection[cardId];
  const storedCount = Number.isFinite(Number(stored?.count ?? stored))
    ? Math.max(0, Math.floor(Number(stored?.count ?? stored)))
    : 0;
  const rewardCount = Number.isFinite(Number(reward.count))
    ? Math.max(0, Math.floor(Number(reward.count)))
    : null;
  const count = rewardCount != null ? rewardCount : storedCount;
  const assetPath = typeof reward.assetPath === 'string' && reward.assetPath.trim()
    ? reward.assetPath.trim()
    : (definition?.assetPath || null);
  return {
    cardId,
    label,
    count,
    assetPath,
    isNew: reward.isNew === true,
    definition: definition || null,
    type: rewardType,
    collectionType: collectionType || null
  };
}

function formatSpecialCardCount(count, type = 'card') {
  const normalized = Math.max(0, Math.floor(Number(count) || 0));
  const formatted = formatIntegerLocalized(normalized);
  const fallback = `×${formatted}`;
  const key = type === 'image'
    ? 'scripts.gacha.images.overlay.count'
    : 'scripts.gacha.cards.overlay.count';
  return translateOrDefault(key, fallback, { count: formatted });
}

function applySpecialCardOverlayContent(card) {
  if (!card) {
    return;
  }
  const rawDisplayMode = typeof card.displayMode === 'string' ? card.displayMode.toLowerCase() : '';
  const useFullscreenLayout = card.type === 'image'
    || rawDisplayMode === 'image'
    || rawDisplayMode === 'fullscreen';
  const overlayType = useFullscreenLayout ? 'image' : 'card';
  const isActualImage = card.type === 'image';
  const isImageLayout = overlayType === 'image';
  if (elements.gachaCardOverlay) {
    elements.gachaCardOverlay.dataset.overlayType = overlayType;
  }
  if (elements.gachaCardOverlayDialog) {
    if (overlayType === 'image') {
      elements.gachaCardOverlayDialog.setAttribute('aria-label', card.label);
      elements.gachaCardOverlayDialog.removeAttribute('aria-labelledby');
      elements.gachaCardOverlayDialog.removeAttribute('aria-describedby');
    } else {
      elements.gachaCardOverlayDialog.setAttribute('aria-labelledby', 'gachaCardOverlayTitle');
      elements.gachaCardOverlayDialog.setAttribute('aria-describedby', 'gachaCardOverlayHint');
      elements.gachaCardOverlayDialog.removeAttribute('aria-label');
    }
  }
  if (elements.gachaCardOverlayLabel) {
    elements.gachaCardOverlayLabel.textContent = card.label;
    const hidden = overlayType === 'image';
    elements.gachaCardOverlayLabel.hidden = hidden;
    elements.gachaCardOverlayLabel.setAttribute('aria-hidden', hidden ? 'true' : 'false');
  }
  if (elements.gachaCardOverlayCount) {
    elements.gachaCardOverlayCount.textContent = formatSpecialCardCount(card.count, card.type);
    const hidden = overlayType === 'image';
    elements.gachaCardOverlayCount.hidden = hidden;
    elements.gachaCardOverlayCount.setAttribute('aria-hidden', hidden ? 'true' : 'false');
  }
  if (elements.gachaCardOverlayImage) {
    if (card.assetPath) {
      elements.gachaCardOverlayImage.src = card.assetPath;
      elements.gachaCardOverlayImage.hidden = false;
    } else {
      elements.gachaCardOverlayImage.removeAttribute('src');
      elements.gachaCardOverlayImage.hidden = true;
    }
    elements.gachaCardOverlayImage.alt = card.label;
  }
  if (elements.gachaCardOverlayTitle) {
    const titleKey = card.isNew
      ? (isImageLayout
        ? (isActualImage ? 'scripts.gacha.images.overlay.newTitle' : 'scripts.gacha.cards.overlay.newTitle')
        : 'scripts.gacha.cards.overlay.newTitle')
      : (isImageLayout
        ? (isActualImage ? 'scripts.gacha.images.overlay.duplicateTitle' : 'scripts.gacha.cards.overlay.duplicateTitle')
        : 'scripts.gacha.cards.overlay.duplicateTitle');
    const fallback = card.isNew
      ? (isImageLayout
        ? (isActualImage ? 'Image bonus obtenue !' : 'Carte spéciale obtenue !')
        : 'Carte spéciale obtenue !')
      : (isImageLayout
        ? (isActualImage ? 'Image bonus retrouvée !' : 'Carte spéciale retrouvée !')
        : 'Carte spéciale retrouvée !');
    elements.gachaCardOverlayTitle.textContent = translateOrDefault(titleKey, fallback, { card: card.label });
    elements.gachaCardOverlayTitle.hidden = isImageLayout;
    elements.gachaCardOverlayTitle.setAttribute('aria-hidden', isImageLayout ? 'true' : 'false');
  }
  if (elements.gachaCardOverlayHint) {
    const hintKey = isImageLayout
      ? (isActualImage ? 'scripts.gacha.images.overlay.hint' : 'scripts.gacha.cards.overlay.hint')
      : 'scripts.gacha.cards.overlay.hint';
    const hintFallback = isImageLayout
      ? (isActualImage ? 'Touchez la croix pour refermer l’image.' : 'Touchez la croix pour revenir au jeu.')
      : 'Touchez la croix pour revenir au jeu.';
    elements.gachaCardOverlayHint.textContent = translateOrDefault(hintKey, hintFallback);
    elements.gachaCardOverlayHint.hidden = isImageLayout;
    elements.gachaCardOverlayHint.setAttribute('aria-hidden', isImageLayout ? 'true' : 'false');
  }
  if (elements.gachaCardOverlayClose) {
    const closeKey = isImageLayout
      ? (isActualImage ? 'scripts.gacha.images.overlay.close' : 'scripts.gacha.cards.overlay.close')
      : 'scripts.gacha.cards.overlay.close';
    const closeFallback = isImageLayout
      ? (isActualImage ? 'Fermer l’image' : 'Fermer la carte')
      : 'Fermer la carte';
    const closeLabel = translateOrDefault(closeKey, closeFallback);
    elements.gachaCardOverlayClose.setAttribute('aria-label', closeLabel);
    elements.gachaCardOverlayClose.setAttribute('title', closeLabel);
  }
}

function finishHidingSpecialCardOverlay() {
  const overlay = elements.gachaCardOverlay;
  if (!overlay) {
    return;
  }
  if (overlay.classList.contains('is-visible')) {
    specialCardOverlayState.hideTimer = null;
    return;
  }
  delete overlay.dataset.overlayType;
  overlay.hidden = true;
  overlay.classList.remove('is-visible');
  if (elements.gachaCardOverlayImage) {
    elements.gachaCardOverlayImage.removeAttribute('src');
  }
  if (elements.gachaCardOverlayDialog) {
    elements.gachaCardOverlayDialog.removeAttribute('aria-label');
    elements.gachaCardOverlayDialog.setAttribute('aria-labelledby', 'gachaCardOverlayTitle');
    elements.gachaCardOverlayDialog.setAttribute('aria-describedby', 'gachaCardOverlayHint');
  }
  if (document && document.body) {
    document.body.classList.remove('has-gacha-card-overlay');
  }
  if (specialCardOverlayState.lastFocus && typeof specialCardOverlayState.lastFocus.focus === 'function') {
    try {
      specialCardOverlayState.lastFocus.focus();
    } catch (error) {
      // Ignore focus errors.
    }
  }
  specialCardOverlayState.lastFocus = null;
  specialCardOverlayState.hideTimer = null;
  specialCardOverlayState.active = null;
  resetSpecialCardOverlayNavigation();
  resetSpecialCardOverlaySwipeState();
  processSpecialCardOverlayQueue();
}

function openSpecialCardOverlay(card) {
  const overlay = elements.gachaCardOverlay;
  if (!overlay) {
    return;
  }
  if (specialCardOverlayState.hideTimer != null) {
    clearTimeout(specialCardOverlayState.hideTimer);
    specialCardOverlayState.hideTimer = null;
  }
  const alreadyVisible = !overlay.hidden && overlay.classList.contains('is-visible');
  specialCardOverlayState.active = card;
  applySpecialCardOverlayContent(card);
  updateSpecialCardOverlayNavigation(card);
  resetSpecialCardOverlaySwipeState();
  if (!alreadyVisible) {
    overlay.hidden = false;
    overlay.setAttribute('aria-hidden', 'false');
    if (document && document.body) {
      document.body.classList.add('has-gacha-card-overlay');
    }
    requestAnimationFrame(() => {
      overlay.classList.add('is-visible');
    });
    specialCardOverlayState.lastFocus = typeof document !== 'undefined' ? document.activeElement : null;
    document.addEventListener('keydown', handleSpecialCardOverlayKeydown);
  }
  const focusTarget = elements.gachaCardOverlayClose || elements.gachaCardOverlayDialog;
  if (focusTarget && typeof focusTarget.focus === 'function') {
    try {
      focusTarget.focus({ preventScroll: true });
    } catch (error) {
      focusTarget.focus();
    }
  }
}

function closeSpecialCardOverlay() {
  const overlay = elements.gachaCardOverlay;
  if (!overlay || overlay.hidden) {
    specialCardOverlayState.active = null;
    processSpecialCardOverlayQueue();
    return;
  }
  overlay.setAttribute('aria-hidden', 'true');
  overlay.classList.remove('is-visible');
  document.removeEventListener('keydown', handleSpecialCardOverlayKeydown);
  resetSpecialCardOverlayNavigation();
  resetSpecialCardOverlaySwipeState();
  if (specialCardOverlayState.hideTimer != null) {
    clearTimeout(specialCardOverlayState.hideTimer);
  }
  overlay.addEventListener('transitionend', finishHidingSpecialCardOverlay, { once: true });
  specialCardOverlayState.hideTimer = setTimeout(finishHidingSpecialCardOverlay, 260);
}

function processSpecialCardOverlayQueue() {
  if (specialCardOverlayState.active || !specialCardOverlayState.queue.length) {
    return;
  }
  const next = specialCardOverlayState.queue.shift();
  if (!next) {
    processSpecialCardOverlayQueue();
    return;
  }
  openSpecialCardOverlay(next);
}

function enqueueSpecialCardReveal(rewards) {
  const list = Array.isArray(rewards) ? rewards : [rewards];
  list.forEach(entry => {
    const normalized = normalizeSpecialCardReward(entry);
    if (normalized) {
      specialCardOverlayState.queue.push(normalized);
    }
  });
  processSpecialCardOverlayQueue();
}

function showSpecialCardFromCollection(cardId, type = 'card') {
  const normalized = normalizeSpecialCardReward({ cardId, type });
  if (!normalized || normalized.count <= 0) {
    return;
  }
  normalized.isNew = false;
  normalized.displayMode = 'fullscreen';
  specialCardOverlayState.queue = [];
  openSpecialCardOverlay(normalized);
}

function handleCollectionListClick(event) {
  const button = event.target.closest('[data-card-id]');
  if (!button) {
    return;
  }
  event.preventDefault();
  const cardId = button.getAttribute('data-card-id');
  if (!cardId) {
    return;
  }
  const rawType = button.getAttribute('data-card-type') || button.dataset.cardType || '';
  const normalizedType = rawType === 'card' ? 'card' : 'image';
  showSpecialCardFromCollection(cardId, normalizedType);
}

function handleSpecialCardOverlayKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeSpecialCardOverlay();
  }
}

function initSpecialCardOverlay() {
  if (specialCardOverlayState.initialized) {
    return;
  }
  specialCardOverlayState.initialized = true;
  if (elements.infoCardsList) {
    elements.infoCardsList.addEventListener('click', handleCollectionListClick);
  }
  if (elements.collectionImagesList) {
    elements.collectionImagesList.addEventListener('click', handleCollectionListClick);
  }
  if (elements.collectionBonusImagesList) {
    elements.collectionBonusImagesList.addEventListener('click', handleCollectionListClick);
  }
  if (elements.collectionBonus2ImagesList) {
    elements.collectionBonus2ImagesList.addEventListener('click', handleCollectionListClick);
  }
  if (elements.gachaCardOverlayClose) {
    elements.gachaCardOverlayClose.addEventListener('click', event => {
      event.preventDefault();
      closeSpecialCardOverlay();
    });
  }
  if (elements.gachaCardOverlayDialog) {
    elements.gachaCardOverlayDialog.addEventListener('click', event => {
      event.stopPropagation();
    });
    elements.gachaCardOverlayDialog.addEventListener('touchstart', handleSpecialCardOverlayTouchStart, SPECIAL_CARD_OVERLAY_TOUCH_OPTIONS);
    elements.gachaCardOverlayDialog.addEventListener('touchmove', handleSpecialCardOverlayTouchMove, SPECIAL_CARD_OVERLAY_TOUCH_OPTIONS);
    elements.gachaCardOverlayDialog.addEventListener('touchend', handleSpecialCardOverlayTouchEnd, SPECIAL_CARD_OVERLAY_TOUCH_OPTIONS);
    elements.gachaCardOverlayDialog.addEventListener('touchcancel', handleSpecialCardOverlayTouchCancel, SPECIAL_CARD_OVERLAY_TOUCH_OPTIONS);
  }
  if (elements.gachaCardOverlay) {
    elements.gachaCardOverlay.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
    });
  }
  renderSpecialCardCollection();
}

function resolveCollectionEntryLabel(id, type) {
  return type === 'image' ? resolveBonusImageLabel(id) : resolveSpecialCardLabel(id);
}

function buildOwnedCollectionEntries(definitions, collection, type) {
  if (!Array.isArray(definitions)) {
    return [];
  }
  const sourceCollection = collection && typeof collection === 'object' ? collection : {};
  return definitions
    .map(def => {
      if (!def || !def.id) {
        return null;
      }
      const stored = sourceCollection[def.id];
      const rawCount = Number.isFinite(Number(stored?.count ?? stored))
        ? Math.max(0, Math.floor(Number(stored?.count ?? stored)))
        : 0;
      const label = resolveCollectionEntryLabel(def.id, type);
      const assetPath = typeof def.assetPath === 'string' && def.assetPath.trim()
        ? def.assetPath.trim()
        : null;
      const rawAcquiredOrder = Number(stored?.acquiredOrder);
      const acquiredOrder = Number.isFinite(rawAcquiredOrder) && rawAcquiredOrder > 0
        ? Math.floor(rawAcquiredOrder)
        : null;
      const rawFirstAcquiredAt = Number(stored?.firstAcquiredAt);
      const firstAcquiredAt = Number.isFinite(rawFirstAcquiredAt) && rawFirstAcquiredAt > 0
        ? rawFirstAcquiredAt
        : null;
      return {
        id: def.id,
        count: rawCount,
        label,
        assetPath,
        acquiredOrder,
        firstAcquiredAt
      };
    })
    .filter(entry => entry && entry.count > 0);
}

function compareCollectionImagesByAcquisition(a, b) {
  if (!a || !b) {
    return 0;
  }
  const rawOrderA = Number(a.acquiredOrder);
  const orderA = Number.isFinite(rawOrderA) && rawOrderA > 0 ? rawOrderA : null;
  const rawOrderB = Number(b.acquiredOrder);
  const orderB = Number.isFinite(rawOrderB) && rawOrderB > 0 ? rawOrderB : null;
  const hasOrderA = orderA != null;
  const hasOrderB = orderB != null;
  if (hasOrderA || hasOrderB) {
    if (!hasOrderA && hasOrderB) {
      return -1;
    }
    if (hasOrderA && !hasOrderB) {
      return 1;
    }
    if (orderA !== orderB) {
      return orderA - orderB;
    }
  }
  const rawTimeA = Number(a.firstAcquiredAt);
  const timeA = Number.isFinite(rawTimeA) && rawTimeA > 0 ? rawTimeA : null;
  const rawTimeB = Number(b.firstAcquiredAt);
  const timeB = Number.isFinite(rawTimeB) && rawTimeB > 0 ? rawTimeB : null;
  const hasTimeA = timeA != null;
  const hasTimeB = timeB != null;
  if (hasTimeA || hasTimeB) {
    if (!hasTimeA && hasTimeB) {
      return -1;
    }
    if (hasTimeA && !hasTimeB) {
      return 1;
    }
    if (timeA !== timeB) {
      return timeA - timeB;
    }
  }
  return compareTextLocalized(a.label, b.label);
}

function renderBonusImageCollectionList(options) {
  const {
    definitions,
    collection,
    container,
    emptyElement,
    viewKey,
    collectionType = 'image'
  } = options;

  if (!container || !emptyElement) {
    return;
  }

  container.classList.add('info-card-list--images');
  container.dataset.collectionType = collectionType || 'image';
  container.innerHTML = '';

  const owned = buildOwnedCollectionEntries(definitions, collection, 'image');

  if (!owned.length) {
    container.hidden = true;
    container.setAttribute('aria-hidden', 'true');
    emptyElement.hidden = false;
    emptyElement.setAttribute('aria-hidden', 'false');
    return;
  }

  owned.sort(compareCollectionImagesByAcquisition);

  owned.forEach(entry => {
    const item = document.createElement('li');
    item.className = 'info-card-list__item info-card-list__item--image';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'collection-image-button';
    button.dataset.cardId = entry.id;
    button.dataset.cardType = 'image';
    const viewLabel = translateOrDefault(
      viewKey,
      `Afficher ${entry.label}`,
      { card: entry.label }
    );
    button.setAttribute('aria-label', viewLabel);
    button.title = viewLabel;

    const figure = document.createElement('span');
    figure.className = 'collection-image-button__figure';

    const img = document.createElement('img');
    img.className = 'collection-image-button__image';
    img.alt = entry.label;
    img.loading = 'lazy';
    img.decoding = 'async';
    img.draggable = false;
    if (entry.assetPath) {
      img.src = entry.assetPath;
    }

    figure.appendChild(img);

    const srLabel = document.createElement('span');
    srLabel.className = 'visually-hidden';
    srLabel.textContent = entry.label;

    button.append(figure, srLabel);
    item.appendChild(button);
    container.appendChild(item);
  });

  container.hidden = false;
  container.setAttribute('aria-hidden', 'false');
  emptyElement.hidden = true;
  emptyElement.setAttribute('aria-hidden', 'true');
}

function renderCollectionList(options) {
  const {
    definitions,
    collection,
    container,
    emptyElement,
    type,
    viewKey,
    countKey
  } = options;

  if (type === 'image' || type === 'bonusImage' || type === 'bonusImage2') {
    renderBonusImageCollectionList({
      definitions,
      collection,
      container,
      emptyElement,
      viewKey,
      collectionType: type === 'bonusImage'
        ? 'bonusImage'
        : (type === 'bonusImage2' ? 'bonusImage2' : 'image')
    });
    return;
  }

  if (!container || !emptyElement) {
    return;
  }

  container.classList.remove('info-card-list--images');
  container.dataset.collectionType = type || '';
  container.innerHTML = '';

  const owned = buildOwnedCollectionEntries(definitions, collection, type);

  if (!owned.length) {
    container.hidden = true;
    container.setAttribute('aria-hidden', 'true');
    emptyElement.hidden = false;
    emptyElement.setAttribute('aria-hidden', 'false');
    return;
  }

  owned.sort((a, b) => compareTextLocalized(a.label, b.label));

  owned.forEach(entry => {
    const item = document.createElement('li');
    item.className = 'info-card-list__item';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'info-card-list__button';
    button.dataset.cardId = entry.id;
    button.dataset.cardType = type;
    const viewLabel = translateOrDefault(
      viewKey,
      `Afficher ${entry.label}`,
      { card: entry.label }
    );
    button.setAttribute('aria-label', viewLabel);
    button.title = viewLabel;

    const name = document.createElement('span');
    name.className = 'info-card-list__name';
    name.textContent = entry.label;

    const count = document.createElement('span');
    count.className = 'info-card-list__count';
    const formattedCount = formatIntegerLocalized(entry.count);
    count.textContent = translateOrDefault(
      countKey,
      `×${formattedCount}`,
      { count: formattedCount }
    );

    button.appendChild(name);
    button.appendChild(count);
    item.appendChild(button);
    container.appendChild(item);
  });

  container.hidden = false;
  container.setAttribute('aria-hidden', 'false');
  emptyElement.hidden = true;
  emptyElement.setAttribute('aria-hidden', 'true');
}

function renderSpecialCardCollection() {
  renderCollectionList({
    definitions: getSpecialCardDefinitions(),
    collection: getSpecialCardCollection(),
    container: elements.infoCardsList,
    emptyElement: elements.infoCardsEmpty,
    type: 'card',
    viewKey: 'index.sections.collection.cards.view',
    countKey: 'index.sections.collection.cards.count'
  });

  renderCollectionList({
    definitions: getBonusImageDefinitions(),
    collection: getBonusImageCollection(),
    container: elements.collectionImagesList,
    emptyElement: elements.collectionImagesEmpty,
    type: 'image',
    viewKey: 'index.sections.collection.images.view',
    countKey: 'index.sections.collection.images.count'
  });

  renderCollectionList({
    definitions: getPermanentBonusImageDefinitions(),
    collection: getPermanentBonusImageCollection(),
    container: elements.collectionBonusImagesList,
    emptyElement: elements.collectionBonusImagesEmpty,
    type: 'bonusImage',
    viewKey: 'index.sections.collection.bonusImages.view',
    countKey: 'index.sections.collection.bonusImages.count'
  });

  renderCollectionList({
    definitions: getSecondaryPermanentBonusImageDefinitions(),
    collection: getPermanentBonusImageCollection(),
    container: elements.collectionBonus2ImagesList,
    emptyElement: elements.collectionBonus2ImagesEmpty,
    type: 'bonusImage2',
    viewKey: 'index.sections.collection.bonus2Images.view',
    countKey: 'index.sections.collection.bonus2Images.count'
  });
}

function subscribeSpecialCardOverlayLanguageUpdates() {
  const handler = () => {
    renderSpecialCardCollection();
    if (specialCardOverlayState.active) {
      applySpecialCardOverlayContent(specialCardOverlayState.active);
    }
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

  const elementEntries = summaryStore.elements && typeof summaryStore.elements === 'object'
    ? Object.values(summaryStore.elements).map(entry => ({ ...entry, type: entry.type || 'element' }))
    : [];
  elementEntries.sort((a, b) => {
    const labelA = typeof a.label === 'string' ? a.label : '';
    const labelB = typeof b.label === 'string' ? b.label : '';
    return compareTextLocalized(labelA, labelB);
  });

  const entries = [...rarityEntries, ...familyEntries, ...elementEntries];

  entries.forEach(summary => {
    const card = document.createElement('article');
    card.className = 'element-bonus-card';
    card.setAttribute('role', 'listitem');
    const summaryType = summary.type || (summary.familyId ? 'family' : 'rarity');
    if (summaryType === 'family') {
      card.dataset.familyId = summary.familyId || '';
      card.classList.add('element-bonus-card--family');
    } else if (summaryType === 'element') {
      if (summary.elementId) {
        card.dataset.elementId = summary.elementId;
      }
      if (summary.rarityId) {
        card.dataset.rarityId = summary.rarityId;
      }
      card.classList.add('element-bonus-card--element');
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


function computeRarityMultiplierProduct(store) {
  if (!store) return LayeredNumber.one();
  const accumulate = raw => {
    const numeric = Number(raw);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return 0;
    }
    return numeric - 1;
  };
  let bonusTotal = 0;
  if (store instanceof Map) {
    store.forEach(raw => {
      bonusTotal += accumulate(raw);
    });
  } else if (typeof store === 'object' && store !== null) {
    Object.values(store).forEach(raw => {
      bonusTotal += accumulate(raw);
    });
  }
  const total = 1 + bonusTotal;
  if (!Number.isFinite(total) || total <= 0) {
    return LayeredNumber.one();
  }
  return new LayeredNumber(total);
}

function createFallbackApcFrenzyStats() {
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

function getNormalizedApcFrenzyStats(store) {
  if (typeof ensureApcFrenzyStats === 'function') {
    const normalized = ensureApcFrenzyStats(store);
    if (normalized && typeof normalized === 'object') {
      return normalized;
    }
  }

  const fallback = createFallbackApcFrenzyStats();
  if (!store || typeof store !== 'object') {
    return fallback;
  }

  const rawStats = store.apcFrenzy && typeof store.apcFrenzy === 'object'
    ? store.apcFrenzy
    : {};
  const bestRaw = rawStats.best && typeof rawStats.best === 'object' ? rawStats.best : {};
  const bestSingleRaw = rawStats.bestSingle && typeof rawStats.bestSingle === 'object' ? rawStats.bestSingle : {};

  fallback.totalClicks = toNonNegativeInteger(rawStats.totalClicks ?? rawStats.total ?? 0);
  fallback.best.clicks = toNonNegativeInteger(bestRaw.clicks ?? bestRaw.count ?? 0);
  fallback.best.frenziesUsed = toNonNegativeInteger(bestRaw.frenziesUsed ?? bestRaw.frenzies ?? 0);
  fallback.bestSingle.clicks = toNonNegativeInteger(bestSingleRaw.clicks ?? bestSingleRaw.count ?? 0);

  if (fallback.bestSingle.clicks <= 0 && fallback.best.frenziesUsed <= 1) {
    fallback.bestSingle.clicks = Math.max(fallback.bestSingle.clicks, fallback.best.clicks);
  }

  store.apcFrenzy = fallback;
  return fallback;
}

function formatFrenzySingleRecordValue(rawClicks) {
  const clicks = toNonNegativeInteger(rawClicks);
  if (clicks <= 0) {
    return translateOrDefault('scripts.info.progress.frenzy.empty', '—');
  }
  const clicksText = formatIntegerLocalized(clicks);
  return translateOrDefault(
    'scripts.info.progress.frenzy.singleValue',
    `${clicksText} clicks`,
    { count: clicksText }
  );
}

function formatFrenzyMultiRecordValue(rawClicks, rawFrenzies) {
  const clicks = toNonNegativeInteger(rawClicks);
  if (clicks <= 0) {
    return translateOrDefault('scripts.info.progress.frenzy.empty', '—');
  }
  const frenzies = Math.max(1, toNonNegativeInteger(rawFrenzies));
  const clicksText = formatIntegerLocalized(clicks);
  const frenziesText = formatIntegerLocalized(frenzies);
  const key = frenzies === 1
    ? 'scripts.info.progress.frenzy.multiValueSingle'
    : 'scripts.info.progress.frenzy.multiValue';
  const fallback = frenzies === 1
    ? `${clicksText} clicks · ${frenziesText} frenzy`
    : `${clicksText} clicks · ${frenziesText} frenzies`;
  return translateOrDefault(key, fallback, { count: clicksText, frenzies: frenziesText });
}

function getPhotonProgressStats() {
  const progress = gameState.arcadeProgress;
  if (!progress || typeof progress !== 'object') {
    return null;
  }
  const entries = progress.entries && typeof progress.entries === 'object' ? progress.entries : {};
  const entry = entries.wave || entries.photon || null;
  if (!entry || typeof entry !== 'object') {
    return null;
  }
  const state = entry.state && typeof entry.state === 'object' ? entry.state : entry;
  if (!state || typeof state !== 'object') {
    return null;
  }
  const toNumber = value => {
    const numeric = Number(value);
    return Number.isFinite(numeric) && numeric > 0 ? numeric : 0;
  };
  const bestDistance = toNumber(
    state.bestDistance
      ?? state.bestDistanceMeters
      ?? state.maxDistance
      ?? state.distance
  );
  const totalDistance = toNumber(
    state.totalDistance
      ?? state.distanceTotal
      ?? state.accumulatedDistance
      ?? state.distanceAccumulated
  );
  const bestSpeed = toNumber(state.bestSpeed ?? state.maxSpeed ?? state.speed);
  const maxAltitude = toNumber(state.maxAltitude ?? state.bestAltitude ?? state.altitude);
  return {
    totalDistance: Math.max(totalDistance, bestDistance),
    bestSpeed,
    maxAltitude
  };
}

function formatPhotonSpeed(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return null;
  }
  const digits = value >= 100 ? 0 : 1;
  return formatNumberLocalized(value, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  });
}

function formatPhotonAltitude(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return null;
  }
  const digits = value >= 10 ? 0 : 1;
  return formatNumberLocalized(value, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  });
}

function updatePhotonStats() {
  const stats = getPhotonProgressStats();
  const emptyValue = translateOrDefault('scripts.info.progress.photon.empty', '—');

  if (elements.infoPhotonDistanceValue) {
    if (stats && stats.totalDistance > 0) {
      const rawDistance = stats.totalDistance;
      let layered;
      if (rawDistance instanceof LayeredNumber) {
        layered = rawDistance.clone();
      } else if (rawDistance && typeof rawDistance === 'object') {
        try {
          layered = LayeredNumber.fromJSON(rawDistance);
        } catch (error) {
          layered = new LayeredNumber(rawDistance);
        }
      } else {
        layered = new LayeredNumber(rawDistance);
      }
      elements.infoPhotonDistanceValue.textContent = layered.isZero()
        ? emptyValue
        : layered.toString();
    } else {
      elements.infoPhotonDistanceValue.textContent = emptyValue;
    }
  }

  if (elements.infoPhotonSpeedValue) {
    const formatted = stats ? formatPhotonSpeed(stats.bestSpeed) : null;
    elements.infoPhotonSpeedValue.textContent = formatted ?? emptyValue;
  }

  if (elements.infoPhotonAltitudeValue) {
    const formatted = stats ? formatPhotonAltitude(stats.maxAltitude) : null;
    elements.infoPhotonAltitudeValue.textContent = formatted ?? emptyValue;
  }
}

const STARS_WAR_DIFFICULTY_MODES = Object.freeze({ hard: 'hard', easy: 'easy' });
const STARS_WAR_AUTOSAVE_KEY = 'starsWar';

function normalizeStarsWarMode(mode) {
  if (typeof mode === 'string' && mode.toLowerCase() === STARS_WAR_DIFFICULTY_MODES.easy) {
    return STARS_WAR_DIFFICULTY_MODES.easy;
  }
  return STARS_WAR_DIFFICULTY_MODES.hard;
}

function normalizeStarsWarRecord(raw) {
  const result = { score: 0, time: 0, waves: 0 };
  if (!raw || typeof raw !== 'object') {
    return result;
  }
  const toNumber = value => {
    const numeric = Number(value);
    return Number.isFinite(numeric) && numeric >= 0 ? numeric : 0;
  };
  result.score = Math.max(0, Math.floor(toNumber(raw.bestScore ?? raw.score ?? raw.highScore ?? raw.points)));
  result.time = Math.max(0, Math.floor(toNumber(raw.bestTime ?? raw.bestTimeSeconds ?? raw.time ?? raw.duration)));
  result.waves = Math.max(0, Math.floor(toNumber(raw.bestWave ?? raw.wave ?? raw.maxWave ?? raw.waves)));
  return result;
}

function normalizeStarsWarSyncRecord(record) {
  if (!record || typeof record !== 'object') {
    return { score: 0, time: 0, waves: 0 };
  }
  return {
    score: toNonNegativeInteger(record.score),
    time: toNonNegativeInteger(record.time),
    waves: toNonNegativeInteger(record.waves)
  };
}

function starsWarRecordEquals(left, right) {
  if (!left || !right) {
    return false;
  }
  return left.score === right.score && left.time === right.time && left.waves === right.waves;
}

function pickBetterStarsWarRecord(existing, candidate) {
  const current = normalizeStarsWarSyncRecord(existing);
  const next = normalizeStarsWarSyncRecord(candidate);
  if (next.time > current.time) {
    return next;
  }
  if (next.time === current.time) {
    if (next.score > current.score) {
      return next;
    }
    if (next.score === current.score && next.waves > current.waves) {
      return next;
    }
  }
  return current;
}

function readStarsWarProgressEntry(rawEntry) {
  if (!rawEntry || typeof rawEntry !== 'object') {
    return null;
  }
  const state = rawEntry.state && typeof rawEntry.state === 'object' ? rawEntry.state : rawEntry;
  if (!state || typeof state !== 'object') {
    return null;
  }
  const modesSource = state.modes && typeof state.modes === 'object' ? state.modes : null;
  const result = {};
  Object.keys(STARS_WAR_DIFFICULTY_MODES).forEach(key => {
    const modeKey = STARS_WAR_DIFFICULTY_MODES[key];
    const rawMode = modesSource && typeof modesSource[modeKey] === 'object' ? modesSource[modeKey] : null;
    result[modeKey] = normalizeStarsWarRecord(rawMode);
  });
  if (!modesSource) {
    const fallback = normalizeStarsWarRecord(state);
    if (fallback.score > 0 || fallback.time > 0 || fallback.waves > 0) {
      result[STARS_WAR_DIFFICULTY_MODES.hard] = fallback;
    }
  }
  return result;
}

function hasStarsWarProgressData(stats) {
  if (!stats || typeof stats !== 'object') {
    return false;
  }
  return Object.keys(STARS_WAR_DIFFICULTY_MODES).some(key => {
    const modeKey = STARS_WAR_DIFFICULTY_MODES[key];
    const record = stats[modeKey];
    return record && (record.time > 0 || record.score > 0 || record.waves > 0);
  });
}

function syncStarsWarProgressEntry(stats) {
  if (!hasStarsWarProgressData(stats)) {
    return;
  }
  if (!gameState || typeof gameState !== 'object') {
    return;
  }
  if (!gameState.arcadeProgress || typeof gameState.arcadeProgress !== 'object') {
    gameState.arcadeProgress = { version: 1, entries: {} };
  }
  const progress = gameState.arcadeProgress;
  if (!progress.entries || typeof progress.entries !== 'object') {
    progress.entries = {};
  }
  progress.version = Number.isFinite(progress.version) && progress.version > 0
    ? Math.floor(progress.version)
    : 1;

  const currentEntry = progress.entries.starsWar || progress.entries.starswar || null;
  const currentState = currentEntry && typeof currentEntry === 'object' && typeof currentEntry.state === 'object'
    ? currentEntry.state
    : null;
  const existing = readStarsWarProgressEntry(currentEntry) || {};
  const merged = {};
  let changed = false;

  Object.keys(STARS_WAR_DIFFICULTY_MODES).forEach(key => {
    const modeKey = STARS_WAR_DIFFICULTY_MODES[key];
    const bestRecord = pickBetterStarsWarRecord(existing[modeKey], stats[modeKey]);
    const normalizedExisting = normalizeStarsWarSyncRecord(existing[modeKey]);
    merged[modeKey] = bestRecord;
    if (!starsWarRecordEquals(bestRecord, normalizedExisting)) {
      changed = true;
    }
  });

  if (!changed && hasStarsWarProgressData(existing)) {
    return;
  }

  const payload = {
    version: Number.isFinite(currentState?.version) && currentState.version > 0
      ? Math.floor(currentState.version)
      : 3,
    bestDifficulty: Number.isFinite(currentState?.bestDifficulty) && currentState.bestDifficulty > 0
      ? Math.floor(currentState.bestDifficulty)
      : 1,
    modes: {}
  };

  Object.keys(STARS_WAR_DIFFICULTY_MODES).forEach(key => {
    const modeKey = STARS_WAR_DIFFICULTY_MODES[key];
    const rawMode = currentState && currentState.modes && typeof currentState.modes === 'object'
      ? currentState.modes[modeKey]
      : null;
    const topRuns = rawMode && Array.isArray(rawMode.topRuns) ? rawMode.topRuns : [];
    payload.modes[modeKey] = {
      bestScore: merged[modeKey].score,
      bestTime: merged[modeKey].time,
      bestWave: merged[modeKey].waves,
      topRuns
    };
  });

  const entry = {
    state: payload,
    updatedAt: Date.now()
  };

  progress.entries.starsWar = entry;
  progress.entries.starswar = entry;
}

function getStarsWarAutosaveStats() {
  if (typeof window === 'undefined') {
    return null;
  }
  const api = window.ArcadeAutosave;
  if (!api || typeof api.get !== 'function') {
    return null;
  }
  let raw = null;
  try {
    raw = api.get(STARS_WAR_AUTOSAVE_KEY);
  } catch (error) {
    return null;
  }
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  return readStarsWarProgressEntry(raw);
}

function getStarsWarProgressStats() {
  const progress = gameState.arcadeProgress;
  const entries = progress && typeof progress === 'object' && progress.entries && typeof progress.entries === 'object'
    ? progress.entries
    : {};
  const entry = entries.starsWar || entries.starswar || null;
  const fromProgress = readStarsWarProgressEntry(entry);
  if (hasStarsWarProgressData(fromProgress)) {
    return fromProgress;
  }
  const fromAutosave = getStarsWarAutosaveStats();
  if (hasStarsWarProgressData(fromAutosave)) {
    syncStarsWarProgressEntry(fromAutosave);
    return fromAutosave;
  }
  return fromProgress || fromAutosave;
}

function formatStarsWarPoints(value) {
  const count = toNonNegativeInteger(value);
  const countText = formatIntegerLocalized(count);
  const key = count === 1
    ? 'scripts.info.progress.starsWar.points.one'
    : 'scripts.info.progress.starsWar.points.other';
  const fallback = count === 1 ? `${countText} point` : `${countText} points`;
  return translateOrDefault(key, fallback, { count: countText });
}

function formatStarsWarWaves(value) {
  const count = toNonNegativeInteger(value);
  const countText = formatIntegerLocalized(count);
  const key = count === 1
    ? 'scripts.info.progress.starsWar.waves.one'
    : 'scripts.info.progress.starsWar.waves.other';
  const fallback = count === 1 ? `${countText} vague` : `${countText} vagues`;
  return translateOrDefault(key, fallback, { count: countText });
}

function formatStarsWarEntry(record) {
  if (!record || record.time <= 0) {
    return translateOrDefault('scripts.info.progress.starsWar.empty', '—');
  }
  const pointsText = formatStarsWarPoints(record.score);
  const wavesText = formatStarsWarWaves(record.waves);
  const durationText = formatDuration(record.time * 1000);
  const fallback = `${pointsText} · ${wavesText} · ${durationText}`;
  return translateOrDefault('scripts.info.progress.starsWar.entry', fallback, {
    points: pointsText,
    waves: wavesText,
    duration: durationText
  });
}

function updateStarsWarStats(override) {
  const stats = getStarsWarProgressStats() || {};
  const overrides = {};
  if (override && typeof override === 'object') {
    const mode = normalizeStarsWarMode(override.mode);
    const time = toNonNegativeInteger(override.time);
    if (time > 0) {
      overrides[mode] = {
        score: toNonNegativeInteger(override.score),
        time,
        waves: toNonNegativeInteger(override.waves)
      };
    }
  }
  const emptyValue = translateOrDefault('scripts.info.progress.starsWar.empty', '—');
  const combined = {};

  const resolveRecordForMode = modeKey => {
    const base = stats[modeKey] || { score: 0, time: 0, waves: 0 };
    const overrideRecord = overrides[modeKey];
    const chosen = overrideRecord && overrideRecord.time > base.time ? overrideRecord : base;
    const normalized = normalizeStarsWarSyncRecord(chosen);
    combined[modeKey] = normalized;
    return normalized;
  };

  const applyValue = (element, modeKey) => {
    const record = resolveRecordForMode(modeKey);
    if (element) {
      element.textContent = record.time > 0 ? formatStarsWarEntry(record) : emptyValue;
    }
  };

  applyValue(elements.infoStarsWarHardValue, STARS_WAR_DIFFICULTY_MODES.hard);
  applyValue(elements.infoStarsWarEasyValue, STARS_WAR_DIFFICULTY_MODES.easy);

  syncStarsWarProgressEntry(combined);
}

function updateSessionStats() {
  const session = gameState.stats?.session;
  if (!session) return;

  const frenzyStats = getNormalizedApcFrenzyStats(session);

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
  if (elements.infoSessionFrenzySingle) {
    elements.infoSessionFrenzySingle.textContent = formatFrenzySingleRecordValue(frenzyStats.bestSingle?.clicks);
  }
  if (elements.infoSessionFrenzyMulti) {
    elements.infoSessionFrenzyMulti.textContent = formatFrenzyMultiRecordValue(
      frenzyStats.best?.clicks,
      frenzyStats.best?.frenziesUsed
    );
  }
}

function updateGlobalStats() {
  const global = gameState.stats?.global;
  if (!global) return;

  const frenzyStats = getNormalizedApcFrenzyStats(global);

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
  if (elements.infoGlobalFrenzySingle) {
    elements.infoGlobalFrenzySingle.textContent = formatFrenzySingleRecordValue(frenzyStats.bestSingle?.clicks);
  }
  if (elements.infoGlobalFrenzyMulti) {
    elements.infoGlobalFrenzyMulti.textContent = formatFrenzyMultiRecordValue(
      frenzyStats.best?.clicks,
      frenzyStats.best?.frenziesUsed
    );
  }

  updatePhotonStats();
  updateStarsWarStats();
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

  renderSpecialCardCollection();
  updateSessionStats();
  updateGlobalStats();
  renderElementBonuses();
}

if (typeof window !== 'undefined') {
  window.enqueueSpecialCardReveal = enqueueSpecialCardReveal;
  window.initSpecialCardOverlay = initSpecialCardOverlay;
  window.subscribeSpecialCardOverlayLanguageUpdates = subscribeSpecialCardOverlayLanguageUpdates;
  window.refreshStarsWarInfoStats = updateStarsWarStats;
}

