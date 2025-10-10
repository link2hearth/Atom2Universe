const PERIODIC_ELEMENT_I18N_BASE = 'scripts.periodic.elements';

const FAMILY_DESCRIPTION_KEYS = Object.freeze({
  'alkali-metal': 'scripts.app.table.family.descriptions.alkaliMetal',
  'alkaline-earth-metal': 'scripts.app.table.family.descriptions.alkalineEarthMetal',
  'transition-metal': 'scripts.app.table.family.descriptions.transitionMetal',
  'post-transition-metal': 'scripts.app.table.family.descriptions.postTransitionMetal',
  metalloid: 'scripts.app.table.family.descriptions.metalloid',
  nonmetal: 'scripts.app.table.family.descriptions.nonmetal',
  halogen: 'scripts.app.table.family.descriptions.halogen',
  'noble-gas': 'scripts.app.table.family.descriptions.nobleGas',
  lanthanide: 'scripts.app.table.family.descriptions.lanthanide',
  actinide: 'scripts.app.table.family.descriptions.actinide'
});

export function initializeCollectionsUI(options) {
  if (!options || typeof options !== 'object') {
    throw new TypeError('initializeCollectionsUI requires an options object.');
  }

  const {
    elements,
    gameState,
    translateOrDefault,
    t,
    formatNumberLocalized,
    formatIntegerLocalized,
    getI18nApi,
    periodicElements: periodicElementsOption,
    periodicElementIndex: periodicElementIndexOption,
    elementRarityIndex: elementRarityIndexOption,
    gachaRarityMap: gachaRarityMapOption,
    categoryLabels: categoryLabelsOption,
    totalElementCount: totalElementCountOption,
    getCollectionBonusOverview: getCollectionBonusOverviewOption,
    normalizeCollectionDetailText: normalizeCollectionDetailTextOption,
    getElementCurrentCount: getElementCurrentCountOption,
    getElementLifetimeCount: getElementLifetimeCountOption,
    hasElementLifetime: hasElementLifetimeOption,
    applyPeriodicCellCollectionColor: applyPeriodicCellCollectionColorOption,
    updateGachaRarityProgress: updateGachaRarityProgressOption
  } = options;

  if (!elements || typeof elements !== 'object') {
    throw new TypeError('initializeCollectionsUI requires an elements object.');
  }
  if (!gameState || typeof gameState !== 'object') {
    throw new TypeError('initializeCollectionsUI requires a gameState reference.');
  }
  if (typeof translateOrDefault !== 'function') {
    throw new TypeError('initializeCollectionsUI requires translateOrDefault to be a function.');
  }
  if (typeof t !== 'function') {
    throw new TypeError('initializeCollectionsUI requires t to be a function.');
  }
  if (typeof formatNumberLocalized !== 'function') {
    throw new TypeError('initializeCollectionsUI requires formatNumberLocalized to be a function.');
  }
  if (typeof formatIntegerLocalized !== 'function') {
    throw new TypeError('initializeCollectionsUI requires formatIntegerLocalized to be a function.');
  }

  const periodicElements = Array.isArray(periodicElementsOption)
    ? periodicElementsOption
    : (Array.isArray(globalThis.periodicElements) ? globalThis.periodicElements : []);
  const periodicElementIndex = periodicElementIndexOption instanceof Map
    ? periodicElementIndexOption
    : (globalThis.periodicElementIndex instanceof Map ? globalThis.periodicElementIndex : new Map());
  const elementRarityIndex = elementRarityIndexOption instanceof Map
    ? elementRarityIndexOption
    : (globalThis.elementRarityIndex instanceof Map ? globalThis.elementRarityIndex : new Map());
  const gachaRarityMap = gachaRarityMapOption instanceof Map
    ? gachaRarityMapOption
    : (globalThis.GACHA_RARITY_MAP instanceof Map ? globalThis.GACHA_RARITY_MAP : new Map());
  const categoryLabels = categoryLabelsOption && typeof categoryLabelsOption === 'object'
    ? categoryLabelsOption
    : (typeof globalThis.CATEGORY_LABELS === 'object' && globalThis.CATEGORY_LABELS)
      ? globalThis.CATEGORY_LABELS
      : {};
  const totalElementCount = Number.isFinite(totalElementCountOption)
    ? totalElementCountOption
    : (Number.isFinite(globalThis.TOTAL_ELEMENT_COUNT) ? globalThis.TOTAL_ELEMENT_COUNT : periodicElements.length);

  const getCollectionBonusOverview = typeof getCollectionBonusOverviewOption === 'function'
    ? getCollectionBonusOverviewOption
    : (typeof globalThis.getCollectionBonusOverview === 'function' ? globalThis.getCollectionBonusOverview : null);
  const normalizeCollectionDetailText = typeof normalizeCollectionDetailTextOption === 'function'
    ? normalizeCollectionDetailTextOption
    : (typeof globalThis.normalizeCollectionDetailText === 'function' ? globalThis.normalizeCollectionDetailText : null);
  const getElementCurrentCount = typeof getElementCurrentCountOption === 'function'
    ? getElementCurrentCountOption
    : (typeof globalThis.getElementCurrentCount === 'function' ? globalThis.getElementCurrentCount : null);
  const getElementLifetimeCount = typeof getElementLifetimeCountOption === 'function'
    ? getElementLifetimeCountOption
    : (typeof globalThis.getElementLifetimeCount === 'function' ? globalThis.getElementLifetimeCount : null);
  const hasElementLifetime = typeof hasElementLifetimeOption === 'function'
    ? hasElementLifetimeOption
    : (typeof globalThis.hasElementLifetime === 'function' ? globalThis.hasElementLifetime : null);

  if (typeof getCollectionBonusOverview !== 'function') {
    throw new TypeError('initializeCollectionsUI requires getCollectionBonusOverview to be a function.');
  }
  if (typeof normalizeCollectionDetailText !== 'function') {
    throw new TypeError('initializeCollectionsUI requires normalizeCollectionDetailText to be a function.');
  }
  if (typeof getElementCurrentCount !== 'function' || typeof getElementLifetimeCount !== 'function') {
    throw new TypeError('initializeCollectionsUI requires element count helpers.');
  }
  if (typeof hasElementLifetime !== 'function') {
    throw new TypeError('initializeCollectionsUI requires hasElementLifetime to be a function.');
  }

  const applyPeriodicCellCollectionColor = typeof applyPeriodicCellCollectionColorOption === 'function'
    ? applyPeriodicCellCollectionColorOption
    : (typeof globalThis.applyPeriodicCellCollectionColor === 'function'
      ? globalThis.applyPeriodicCellCollectionColor
      : (() => {}));
  const updateGachaRarityProgress = typeof updateGachaRarityProgressOption === 'function'
    ? updateGachaRarityProgressOption
    : (typeof globalThis.updateGachaRarityProgress === 'function'
      ? globalThis.updateGachaRarityProgress
      : (() => {}));

  function formatAtomicMass(value) {
    if (value == null) return '';
    if (typeof value === 'number') {
      if (!Number.isFinite(value)) return '—';
      const text = value.toString();
      if (!text.includes('.')) {
        return text;
      }
      const [integer, fraction] = text.split('.');
      return `${integer},${fraction}`;
    }
    return String(value);
  }

  const periodicCells = new Map();
  let selectedElementId = null;
  let elementDetailsLastTrigger = null;
  let elementFamilyLastTrigger = null;

  function getI18n() {
    if (typeof getI18nApi === 'function') {
      try {
        return getI18nApi();
      } catch (error) {
        console.warn('collections: unable to resolve i18n API', error);
      }
    }
    return null;
  }

  function getPeriodicElementTranslationBase(definition) {
    const id = typeof definition?.id === 'string' ? definition.id.trim() : '';
    if (!id) {
      return '';
    }
    return `${PERIODIC_ELEMENT_I18N_BASE}.${id}`;
  }

  function translatePeriodicElementField(definition, field, fallback) {
    if (!field) {
      return fallback ?? '';
    }
    const base = getPeriodicElementTranslationBase(definition);
    if (!base) {
      return fallback ?? '';
    }
    const translated = translateOrDefault(`${base}.${field}`, fallback ?? '');
    if (typeof translated === 'string' && translated.trim()) {
      return translated;
    }
    return fallback ?? '';
  }

  function getPeriodicElementDisplay(definition) {
    if (!definition || typeof definition !== 'object') {
      return { symbol: '', name: '' };
    }
    const fallbackSymbol = typeof definition.symbol === 'string' ? definition.symbol : '';
    const fallbackName = typeof definition.name === 'string' ? definition.name : '';
    const symbol = translatePeriodicElementField(definition, 'symbol', fallbackSymbol);
    const name = translatePeriodicElementField(definition, 'name', fallbackName);
    return { symbol, name };
  }

  function getPeriodicElementDetails(definition) {
    if (!definition || typeof definition !== 'object') {
      return null;
    }
    const base = getPeriodicElementTranslationBase(definition);
    if (!base) {
      return null;
    }
    const api = getI18n();
    if (api && typeof api.getResource === 'function') {
      try {
        const resource = api.getResource(`${base}.details`);
        if (resource && typeof resource === 'object') {
          return resource;
        }
      } catch (error) {
        console.warn('collections: unable to read periodic element details', error);
      }
    }
    return null;
  }

  function isElementDetailsModalOpen() {
    return Boolean(elements.elementDetailsOverlay && !elements.elementDetailsOverlay.hasAttribute('hidden'));
  }

  function updateElementDetailsModalContent(definition) {
    if (!definition || !elements.elementDetailsOverlay) {
      return;
    }
    const { symbol, name } = getPeriodicElementDisplay(definition);
    const displaySymbol = symbol || '';
    const displayName = name || '';
    const atomicNumber = definition.atomicNumber != null ? definition.atomicNumber : '';
    if (elements.elementDetailsTitle) {
      let titleKey = 'index.sections.table.modal.titleFallback';
      let fallbackTitle = atomicNumber ? `Element ${atomicNumber}` : 'Element details';
      if (displayName && displaySymbol) {
        titleKey = 'index.sections.table.modal.title';
        fallbackTitle = `${displayName} (${displaySymbol})`;
      } else if (displayName) {
        titleKey = 'index.sections.table.modal.titleNameOnly';
        fallbackTitle = displayName;
      } else if (displaySymbol) {
        titleKey = 'index.sections.table.modal.titleSymbolOnly';
        fallbackTitle = displaySymbol;
      }
      const titleText = translateOrDefault(titleKey, fallbackTitle, {
        name: displayName,
        symbol: displaySymbol,
        number: atomicNumber || definition.id || ''
      });
      elements.elementDetailsTitle.textContent = titleText;
    }
    if (elements.elementDetailsBody) {
      const container = elements.elementDetailsBody;
      const fallbackText = t('index.sections.table.modal.comingSoon');
      container.innerHTML = '';
      container.classList.remove('element-details-overlay__content--placeholder');
      const details = getPeriodicElementDetails(definition);

      const summaryText = typeof details?.summary === 'string' ? details.summary.trim() : '';
      const bodyText = typeof details?.body === 'string' ? details.body.trim() : '';
      const paragraphTexts = Array.isArray(details?.paragraphs)
        ? details.paragraphs
            .map(paragraph => (typeof paragraph === 'string' ? paragraph.trim() : ''))
            .filter(text => text.length)
        : [];
      const sources = Array.isArray(details?.sources)
        ? details.sources
            .map(source => (typeof source === 'string' ? source.trim() : ''))
            .filter(text => text.length)
        : [];

      let hasContent = false;
      const fragment = document.createDocumentFragment();

      if (summaryText) {
        const summary = document.createElement('p');
        summary.className = 'element-details-overlay__summary';
        summary.textContent = summaryText;
        fragment.appendChild(summary);
        hasContent = true;
      }

      const effectiveParagraphs = paragraphTexts.length ? paragraphTexts : (bodyText ? [bodyText] : []);
      effectiveParagraphs.forEach(text => {
        const paragraph = document.createElement('p');
        paragraph.className = 'element-details-overlay__paragraph';
        paragraph.textContent = text;
        fragment.appendChild(paragraph);
        hasContent = true;
      });

      if (sources.length) {
        const sourcesWrapper = document.createElement('div');
        sourcesWrapper.className = 'element-details-overlay__sources';

        const label = document.createElement('p');
        label.className = 'element-details-overlay__sources-label';
        label.textContent = t('index.sections.table.modal.sourcesLabel');
        sourcesWrapper.appendChild(label);

        const list = document.createElement('ul');
        list.className = 'element-details-overlay__sources-list';
        sources.forEach(text => {
          const item = document.createElement('li');
          item.className = 'element-details-overlay__sources-item';
          item.textContent = text;
          list.appendChild(item);
        });
        sourcesWrapper.appendChild(list);
        fragment.appendChild(sourcesWrapper);
        hasContent = true;
      }

      if (hasContent) {
        container.appendChild(fragment);
      } else {
        const placeholder = document.createElement('p');
        placeholder.className = 'element-details-overlay__placeholder';
        placeholder.textContent = fallbackText;
        container.appendChild(placeholder);
        container.classList.add('element-details-overlay__content--placeholder');
      }
    }
    elements.elementDetailsOverlay.dataset.elementId = definition.id;
    if (elements.elementDetailsDialog) {
      elements.elementDetailsDialog.dataset.elementId = definition.id;
    }
  }

  function openElementDetailsModal(elementId, { trigger = null } = {}) {
    if (!elements.elementDetailsOverlay || !periodicElementIndex.has(elementId)) {
      return;
    }
    const definition = periodicElementIndex.get(elementId);
    updateElementDetailsModalContent(definition);
    elements.elementDetailsOverlay.hidden = false;
    elements.elementDetailsOverlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('element-details-modal-open');
    elementDetailsLastTrigger = trigger || document.activeElement || elements.elementInfoSymbol;
    if (elements.elementInfoSymbol) {
      elements.elementInfoSymbol.setAttribute('aria-expanded', 'true');
    }
    if (elements.elementDetailsDialog) {
      elements.elementDetailsDialog.focus({ preventScroll: true });
    }
    if (elements.elementDetailsCloseButton) {
      const closeLabel = t('index.sections.table.modal.close');
      if (closeLabel) {
        elements.elementDetailsCloseButton.setAttribute('aria-label', closeLabel);
      }
    }
    document.addEventListener('keydown', handleElementDetailsKeydown, true);
  }

  function closeElementDetailsModal({ restoreFocus = true } = {}) {
    if (!isElementDetailsModalOpen()) {
      return;
    }
    elements.elementDetailsOverlay.hidden = true;
    elements.elementDetailsOverlay.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('element-details-modal-open');
    if (elements.elementInfoSymbol) {
      elements.elementInfoSymbol.setAttribute('aria-expanded', 'false');
    }
    if (elements.elementDetailsOverlay.dataset.elementId) {
      delete elements.elementDetailsOverlay.dataset.elementId;
    }
    if (elements.elementDetailsDialog?.dataset?.elementId) {
      delete elements.elementDetailsDialog.dataset.elementId;
    }
    document.removeEventListener('keydown', handleElementDetailsKeydown, true);
    const lastTrigger = elementDetailsLastTrigger;
    elementDetailsLastTrigger = null;
    if (restoreFocus && lastTrigger && typeof lastTrigger.focus === 'function') {
      lastTrigger.focus({ preventScroll: true });
    }
  }

  function handleElementDetailsKeydown(event) {
    if (!isElementDetailsModalOpen()) {
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      closeElementDetailsModal();
      return;
    }
    if (event.key === 'Tab' && elements.elementDetailsDialog) {
      const focusableSelectors = [
        'button:not([disabled])',
        '[href]',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])'
      ];
      const focusable = Array.from(
        elements.elementDetailsDialog.querySelectorAll(focusableSelectors.join(','))
      ).filter(el => !(el.hasAttribute('disabled') || el.getAttribute('aria-hidden') === 'true'));
      if (!focusable.length) {
        event.preventDefault();
        elements.elementDetailsDialog.focus({ preventScroll: true });
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey) {
        if (document.activeElement === first || document.activeElement === elements.elementDetailsDialog) {
          event.preventDefault();
          last.focus({ preventScroll: true });
        }
      } else if (document.activeElement === last) {
        event.preventDefault();
        first.focus({ preventScroll: true });
      }
    }
  }

  function isElementFamilyModalOpen() {
    return Boolean(elements.elementFamilyOverlay && !elements.elementFamilyOverlay.hasAttribute('hidden'));
  }

  function getFamilyDescription(familyId, familyLabel) {
    if (!familyId) {
      return translateOrDefault(
        'scripts.app.table.family.descriptions.placeholder',
        `Description de la famille ${familyLabel} à venir.`,
        { family: familyLabel }
      );
    }
    const messageKey = FAMILY_DESCRIPTION_KEYS[familyId];
    if (messageKey) {
      const translated = t(messageKey);
      if (translated && translated !== messageKey) {
        return translated;
      }
    }
    return translateOrDefault(
      'scripts.app.table.family.descriptions.placeholder',
      `Description de la famille ${familyLabel} à venir.`,
      { family: familyLabel }
    );
  }

  function updateElementFamilyModalContent(familyId) {
    if (!familyId || !elements.elementFamilyOverlay) {
      return;
    }
    const familyLabel = categoryLabels[familyId] || familyId;
    if (elements.elementFamilyTitle) {
      const titleText = translateOrDefault(
        'index.sections.table.family.modal.title',
        `Famille · ${familyLabel}`,
        { family: familyLabel }
      );
      elements.elementFamilyTitle.textContent = titleText;
    }
    if (elements.elementFamilyBody) {
      const description = getFamilyDescription(familyId, familyLabel);
      elements.elementFamilyBody.textContent = description;
    }
    elements.elementFamilyOverlay.dataset.familyId = familyId;
    if (elements.elementFamilyDialog) {
      elements.elementFamilyDialog.dataset.familyId = familyId;
    }
  }

  function openElementFamilyModal(familyId, { trigger = null } = {}) {
    if (!familyId || !elements.elementFamilyOverlay) {
      return;
    }
    updateElementFamilyModalContent(familyId);
    elements.elementFamilyOverlay.hidden = false;
    elements.elementFamilyOverlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('element-family-modal-open');
    elementFamilyLastTrigger = trigger || document.activeElement || elements.elementInfoCategoryButton;
    if (elements.elementInfoCategoryButton) {
      elements.elementInfoCategoryButton.setAttribute('aria-expanded', 'true');
    }
    if (elements.elementFamilyDialog) {
      elements.elementFamilyDialog.focus({ preventScroll: true });
    }
    if (elements.elementFamilyCloseButton) {
      const closeLabel = t('index.sections.table.modal.close');
      if (closeLabel) {
        elements.elementFamilyCloseButton.setAttribute('aria-label', closeLabel);
      }
    }
    document.addEventListener('keydown', handleElementFamilyKeydown, true);
  }

  function closeElementFamilyModal({ restoreFocus = true } = {}) {
    if (!isElementFamilyModalOpen()) {
      return;
    }
    elements.elementFamilyOverlay.hidden = true;
    elements.elementFamilyOverlay.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('element-family-modal-open');
    if (elements.elementInfoCategoryButton) {
      elements.elementInfoCategoryButton.setAttribute('aria-expanded', 'false');
    }
    if (elements.elementFamilyOverlay.dataset.familyId) {
      delete elements.elementFamilyOverlay.dataset.familyId;
    }
    if (elements.elementFamilyDialog?.dataset?.familyId) {
      delete elements.elementFamilyDialog.dataset.familyId;
    }
    document.removeEventListener('keydown', handleElementFamilyKeydown, true);
    const lastTrigger = elementFamilyLastTrigger;
    elementFamilyLastTrigger = null;
    if (restoreFocus && lastTrigger && typeof lastTrigger.focus === 'function') {
      lastTrigger.focus({ preventScroll: true });
    }
  }

  function handleElementFamilyKeydown(event) {
    if (!isElementFamilyModalOpen()) {
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      closeElementFamilyModal();
      return;
    }
    if (event.key === 'Tab' && elements.elementFamilyDialog) {
      const focusableSelectors = [
        'button:not([disabled])',
        '[href]',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])'
      ];
      const focusable = Array.from(
        elements.elementFamilyDialog.querySelectorAll(focusableSelectors.join(','))
      ).filter(el => !(el.hasAttribute('disabled') || el.getAttribute('aria-hidden') === 'true'));
      if (!focusable.length) {
        event.preventDefault();
        elements.elementFamilyDialog.focus({ preventScroll: true });
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey) {
        if (document.activeElement === first || document.activeElement === elements.elementFamilyDialog) {
          event.preventDefault();
          last.focus({ preventScroll: true });
        }
      } else if (document.activeElement === last) {
        event.preventDefault();
        first.focus({ preventScroll: true });
      }
    }
  }

  function updateElementInfoPanel(definition) {
    const panel = elements.elementInfoPanel;
    const placeholder = elements.elementInfoPlaceholder;
    const content = elements.elementInfoContent;
    if (!panel) return;

    if (!definition) {
      if (elements.elementInfoSymbol) {
        elements.elementInfoSymbol.textContent = '';
        elements.elementInfoSymbol.disabled = true;
        elements.elementInfoSymbol.setAttribute('aria-expanded', 'false');
        elements.elementInfoSymbol.removeAttribute('aria-label');
        elements.elementInfoSymbol.removeAttribute('title');
        if (elements.elementInfoSymbol.dataset.elementId) {
          delete elements.elementInfoSymbol.dataset.elementId;
        }
      }
      if (elements.elementInfoCategoryButton) {
        elements.elementInfoCategoryButton.textContent = '';
        elements.elementInfoCategoryButton.disabled = true;
        elements.elementInfoCategoryButton.setAttribute('aria-expanded', 'false');
        elements.elementInfoCategoryButton.removeAttribute('aria-label');
        elements.elementInfoCategoryButton.removeAttribute('title');
        if (elements.elementInfoCategoryButton.dataset.familyId) {
          delete elements.elementInfoCategoryButton.dataset.familyId;
        }
      }
      if (isElementDetailsModalOpen()) {
        closeElementDetailsModal({ restoreFocus: false });
      }
      if (isElementFamilyModalOpen()) {
        closeElementFamilyModal({ restoreFocus: false });
      }
      if (panel.dataset.category) {
        delete panel.dataset.category;
      }
      if (content) {
        content.hidden = true;
      }
      if (placeholder) {
        placeholder.hidden = false;
      }
      return;
    }

    if (definition.category) {
      panel.dataset.category = definition.category;
    } else if (panel.dataset.category) {
      delete panel.dataset.category;
    }

    if (placeholder) {
      placeholder.hidden = true;
    }
    if (content) {
      content.hidden = false;
    }

    if (elements.elementInfoNumber) {
      elements.elementInfoNumber.textContent =
        definition.atomicNumber != null ? definition.atomicNumber : '—';
    }
    const { symbol, name } = getPeriodicElementDisplay(definition);
    if (elements.elementInfoSymbol) {
      const symbolButton = elements.elementInfoSymbol;
      const displaySymbol = symbol ?? '';
      symbolButton.textContent = displaySymbol;
      const hasSymbol = Boolean(displaySymbol);
      const hasName = Boolean(name);
      symbolButton.disabled = !(hasSymbol || hasName);
      if (!symbolButton.disabled) {
        const openLabel = hasName
          ? translateOrDefault(
              'index.sections.table.modal.open',
              `Open detailed sheet for ${name}`,
              { name, symbol: displaySymbol }
            )
          : translateOrDefault(
              'index.sections.table.modal.openSymbol',
              `Open detailed sheet for ${displaySymbol}`,
              { symbol: displaySymbol }
            );
        symbolButton.setAttribute('aria-label', openLabel);
        symbolButton.setAttribute('title', openLabel);
        symbolButton.dataset.elementId = definition.id;
      } else {
        symbolButton.removeAttribute('aria-label');
        symbolButton.removeAttribute('title');
        if (symbolButton.dataset.elementId) {
          delete symbolButton.dataset.elementId;
        }
      }
      const openElementId = elements.elementDetailsOverlay?.dataset?.elementId || null;
      symbolButton.setAttribute('aria-expanded', openElementId === definition.id ? 'true' : 'false');
    }
    if (elements.elementInfoName) {
      elements.elementInfoName.textContent = name ?? '';
    }
    if (elements.elementInfoCategoryButton) {
      const categoryButton = elements.elementInfoCategoryButton;
      const hasCategory = Boolean(definition.category);
      const label = hasCategory
        ? categoryLabels[definition.category] || definition.category
        : '—';
      categoryButton.textContent = label;
      categoryButton.disabled = !hasCategory;
      const familyLabelText = translateOrDefault(
        'index.sections.table.details.family',
        'Famille'
      );
      const openFamilyId = elements.elementFamilyOverlay?.dataset?.familyId || null;
      if (hasCategory) {
        categoryButton.dataset.familyId = definition.category;
        const openLabel = translateOrDefault(
          'index.sections.table.family.open',
          `${familyLabelText ? `${familyLabelText} : ` : ''}${label}. Ouvrir la fiche famille.`,
          { family: label, label: familyLabelText || '' }
        );
        if (openLabel) {
          categoryButton.setAttribute('aria-label', openLabel);
          categoryButton.setAttribute('title', openLabel);
        } else {
          categoryButton.removeAttribute('aria-label');
          categoryButton.removeAttribute('title');
        }
        categoryButton.setAttribute('aria-expanded', openFamilyId === definition.category ? 'true' : 'false');
        if (isElementFamilyModalOpen() && openFamilyId === definition.category) {
          updateElementFamilyModalContent(definition.category);
        }
      } else {
        if (categoryButton.dataset.familyId) {
          delete categoryButton.dataset.familyId;
        }
        categoryButton.setAttribute('aria-expanded', 'false');
        categoryButton.removeAttribute('aria-label');
        categoryButton.removeAttribute('title');
        if (isElementFamilyModalOpen()) {
          closeElementFamilyModal({ restoreFocus: false });
        }
      }
    }
    const entry = gameState.elements?.[definition.id];
    const count = getElementCurrentCount(entry);
    const lifetimeCount = getElementLifetimeCount(entry);
    if (elements.elementInfoOwnedCount) {
      const displayCount = formatIntegerLocalized(count);
      const lifetimeDisplay = formatIntegerLocalized(lifetimeCount);
      elements.elementInfoOwnedCount.textContent = displayCount;
      const ownedAria = translateOrDefault(
        'scripts.app.table.info.ownedAria',
        `Copies actives\u00a0: ${displayCount}. Collectées au total\u00a0: ${lifetimeDisplay}`,
        { active: displayCount, lifetime: lifetimeDisplay }
      );
      elements.elementInfoOwnedCount.setAttribute('aria-label', ownedAria);
      const ownedTitle = translateOrDefault(
        'scripts.app.table.info.ownedTitle',
        `Copies actives\u00a0: ${displayCount}\nCollectées au total\u00a0: ${lifetimeDisplay}`,
        { active: displayCount, lifetime: lifetimeDisplay }
      );
      elements.elementInfoOwnedCount.setAttribute('title', ownedTitle);
    }
    if (elements.elementInfoCollection) {
      const rarityId = entry?.rarity || elementRarityIndex.get(definition.id);
      const rarityDef = rarityId ? gachaRarityMap.get(rarityId) : null;
      const rarityLabel = rarityDef?.label || rarityId || '—';
      const hasRarityLabel = Boolean(rarityLabel && rarityLabel !== '—');
      const bonusDetails = [];
      const seenDetailTexts = new Set();
      const addDetail = detail => {
        const normalized = normalizeCollectionDetailText(detail);
        if (!normalized) {
          return;
        }
        if (seenDetailTexts.has(normalized)) {
          return;
        }
        seenDetailTexts.add(normalized);
        bonusDetails.push(detail.trim());
      };
      if (rarityId) {
        const overview = getCollectionBonusOverview(rarityId);
        overview.forEach(addDetail);
      }

      if (!bonusDetails.length && rarityDef?.description) {
        addDetail(rarityDef.description);
      }

      if (bonusDetails.length) {
        elements.elementInfoCollection.textContent = hasRarityLabel
          ? `${rarityLabel} · ${bonusDetails.join(' · ')}`
          : bonusDetails.join(' · ');
      } else {
        elements.elementInfoCollection.textContent = rarityLabel;
      }
    }

    if (isElementDetailsModalOpen()) {
      const openId = elements.elementDetailsOverlay?.dataset?.elementId;
      if (openId === definition.id) {
        updateElementDetailsModalContent(definition);
      }
    }
  }

  function selectPeriodicElement(id, { focus = false } = {}) {
    if (!id || !periodicElementIndex.has(id)) {
      selectedElementId = null;
      periodicCells.forEach(cell => {
        cell.classList.remove('is-selected');
        cell.setAttribute('aria-pressed', 'false');
      });
      updateElementInfoPanel(null);
      return;
    }

    selectedElementId = id;
    periodicCells.forEach((cell, elementId) => {
      const isSelected = elementId === id;
      cell.classList.toggle('is-selected', isSelected);
      cell.setAttribute('aria-pressed', isSelected ? 'true' : 'false');
    });

    const definition = periodicElementIndex.get(id);
    updateElementInfoPanel(definition);

    if (focus) {
      const target = periodicCells.get(id);
      if (target) {
        target.focus();
      }
    }
  }

  function renderPeriodicTable() {
    if (!elements.periodicTable) return;
    const infoPanel = elements.elementInfoPanel;
    const summaryTile = elements.collectionSummaryTile;
    if (infoPanel) {
      infoPanel.remove();
    }
    if (summaryTile) {
      summaryTile.remove();
    }

    elements.periodicTable.innerHTML = '';
    periodicCells.clear();

    if (infoPanel) {
      elements.periodicTable.appendChild(infoPanel);
    }
    if (summaryTile) {
      elements.periodicTable.appendChild(summaryTile);
    }

    if (!periodicElements.length) {
      const placeholder = document.createElement('p');
      placeholder.className = 'periodic-placeholder';
      placeholder.textContent = t('scripts.app.table.placeholder');
      elements.periodicTable.appendChild(placeholder);
      if (elements.collectionProgress) {
        elements.collectionProgress.textContent = t('scripts.app.collection.pending');
      }
      selectPeriodicElement(null);
      return;
    }

    const fragment = document.createDocumentFragment();
    periodicElements.forEach(def => {
      const cell = document.createElement('button');
      cell.type = 'button';
      cell.className = 'periodic-element';
      cell.dataset.elementId = def.id;
      cell.dataset.category = def.category ?? 'unknown';
      if (def.atomicNumber != null) {
        cell.dataset.atomicNumber = String(def.atomicNumber);
      }
      if (def.period != null) {
        cell.dataset.period = String(def.period);
      }
      if (def.group != null) {
        cell.dataset.group = String(def.group);
      }
      if (def.gachaId) {
        cell.dataset.gachaId = def.gachaId;
      }
      const rarityId = elementRarityIndex.get(def.id);
      if (rarityId) {
        cell.dataset.rarity = rarityId;
        const rarityDef = gachaRarityMap.get(rarityId);
        if (rarityDef?.color) {
          cell.dataset.rarityColor = rarityDef.color;
        } else {
          delete cell.dataset.rarityColor;
        }
      } else {
        delete cell.dataset.rarityColor;
        cell.style.removeProperty('--rarity-color');
      }
      const { row, column } = def.position || {};
      if (column) {
        cell.style.gridColumn = String(column);
      }
      if (row) {
        cell.style.gridRow = String(row);
      }
      const { symbol, name } = getPeriodicElementDisplay(def);
      const displaySymbol = symbol || '';
      const displayName = name || '';
      const massText = formatAtomicMass(def.atomicMass);
      const labelParts = [];
      if (displayName) {
        if (displaySymbol) {
          labelParts.push(
            translateOrDefault(
              'scripts.app.table.aria.nameWithSymbol',
              `${displayName} (${displaySymbol})`,
              { name: displayName, symbol: displaySymbol }
            )
          );
        } else {
          labelParts.push(
            translateOrDefault(
              'scripts.app.table.aria.name',
              displayName,
              { name: displayName }
            )
          );
        }
      } else if (displaySymbol) {
        labelParts.push(
          translateOrDefault(
            'scripts.app.table.aria.symbol',
            displaySymbol,
            { symbol: displaySymbol }
          )
        );
      }
      if (def.atomicNumber != null) {
        labelParts.push(
          translateOrDefault(
            'scripts.app.table.aria.atomicNumber',
            `numéro atomique ${def.atomicNumber}`,
            { number: def.atomicNumber }
          )
        );
      }
      if (massText) {
        labelParts.push(
          translateOrDefault(
            'scripts.app.table.aria.atomicMass',
            `masse atomique ${massText}`,
            { mass: massText }
          )
        );
      }
      if (def.category) {
        const categoryLabel = categoryLabels[def.category] || def.category;
        labelParts.push(
          translateOrDefault(
            'scripts.app.table.aria.family',
            `famille ${categoryLabel}`,
            { family: categoryLabel }
          )
        );
      }
      cell.setAttribute('aria-label', labelParts.join(', '));

      cell.innerHTML = `
        <span class="periodic-element__symbol">${displaySymbol}</span>
        <span class="periodic-element__number">${def.atomicNumber}</span>
      `;
      cell.setAttribute('aria-pressed', 'false');
      cell.addEventListener('click', () => selectPeriodicElement(def.id));
      cell.addEventListener('focus', () => selectPeriodicElement(def.id));

      const state = gameState.elements[def.id];
      const isOwned = hasElementLifetime(state);
      applyPeriodicCellCollectionColor(cell, isOwned);
      if (isOwned) {
        cell.classList.add('is-owned');
      }

      periodicCells.set(def.id, cell);
      fragment.appendChild(cell);
    });

    elements.periodicTable.appendChild(fragment);
    if (selectedElementId && periodicCells.has(selectedElementId)) {
      selectPeriodicElement(selectedElementId);
    } else if (periodicElements.length) {
      selectPeriodicElement(periodicElements[0].id);
    } else {
      selectPeriodicElement(null);
    }
    updateCollectionDisplay();
  }

  function updateCollectionDisplay() {
    const elementEntries = Object.values(gameState.elements || {});
    const ownedCount = elementEntries.reduce((total, entry) => {
      return total + (hasElementLifetime(entry) ? 1 : 0);
    }, 0);
    const total = totalElementCount || elementEntries.length;
    const lifetimeTotal = elementEntries.reduce((sum, entry) => {
      return sum + getElementLifetimeCount(entry);
    }, 0);
    const currentTotal = elementEntries.reduce((sum, entry) => {
      return sum + getElementCurrentCount(entry);
    }, 0);

    if (elements.collectionProgress) {
      if (total > 0) {
        const ownedDisplay = formatIntegerLocalized(ownedCount);
        const totalDisplay = formatIntegerLocalized(total);
        elements.collectionProgress.textContent = translateOrDefault(
          'scripts.app.table.collection.progress',
          `Collection\u00a0: ${ownedDisplay} / ${totalDisplay} éléments`,
          { owned: ownedDisplay, total: totalDisplay }
        );
      } else {
        elements.collectionProgress.textContent = t('scripts.app.collection.pending');
      }
    }

    if (elements.gachaOwnedSummary) {
      if (total > 0) {
        const ownedDisplay = formatIntegerLocalized(ownedCount);
        const totalDisplay = formatIntegerLocalized(total);
        const ratio = (ownedCount / total) * 100;
        let ratioValue = ratio;
        let ratioOptions = { maximumFractionDigits: 2 };
        if (!Number.isFinite(ratioValue) || ratioValue < 0) {
          ratioValue = 0;
        }
        if (ratioValue >= 99.95) {
          ratioValue = 100;
          ratioOptions = { maximumFractionDigits: 0 };
        } else if (ratioValue >= 10) {
          ratioOptions = { maximumFractionDigits: 1 };
        }
        const ratioDisplay = formatNumberLocalized(ratioValue, ratioOptions);
        elements.gachaOwnedSummary.textContent = translateOrDefault(
          'scripts.app.table.collection.summary',
          `Collection\u00a0: ${ownedDisplay} / ${totalDisplay} éléments (${ratioDisplay}\u00a0%)`,
          { owned: ownedDisplay, total: totalDisplay, ratio: ratioDisplay }
        );
      } else {
        elements.gachaOwnedSummary.textContent = t('scripts.app.collection.pending');
      }
    }

    const currentDisplay = formatIntegerLocalized(currentTotal);
    const lifetimeDisplay = formatIntegerLocalized(lifetimeTotal);

    if (elements.collectionSummaryCurrent) {
      elements.collectionSummaryCurrent.textContent = currentDisplay;
    }

    if (elements.collectionSummaryLifetime) {
      elements.collectionSummaryLifetime.textContent = lifetimeDisplay;
    }

    if (elements.collectionSummaryTile) {
      const summaryLabel = translateOrDefault(
        'scripts.app.table.summaryTile.aria',
        `Total actuel\u00a0: ${currentDisplay} · Total historique\u00a0: ${lifetimeDisplay}`,
        { current: currentDisplay, lifetime: lifetimeDisplay }
      );
      elements.collectionSummaryTile.setAttribute('aria-label', summaryLabel);
      elements.collectionSummaryTile.setAttribute('title', summaryLabel);
    }

    periodicCells.forEach((cell, id) => {
      const entry = gameState.elements?.[id];
      const isOwned = hasElementLifetime(entry);
      cell.classList.toggle('is-owned', isOwned);
      applyPeriodicCellCollectionColor(cell, isOwned);
    });

    if (selectedElementId && periodicElementIndex.has(selectedElementId)) {
      updateElementInfoPanel(periodicElementIndex.get(selectedElementId));
    }

    updateGachaRarityProgress();
  }

  return {
    renderPeriodicTable,
    updateCollectionDisplay,
    openElementDetailsModal,
    closeElementDetailsModal,
    openElementFamilyModal,
    closeElementFamilyModal,
    getSelectedElementId: () => selectedElementId,
    selectElementById: (id, { focus = false } = {}) => selectPeriodicElement(id, { focus })
  };
}

export default initializeCollectionsUI;
