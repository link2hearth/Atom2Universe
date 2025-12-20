(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/twins.json';
  const CARD_IMAGE_BASE_PATH = 'Assets/Cartes/bonus/';

  function resolveCardImageConfig(rawList, fallbackList) {
    const fallback = Array.isArray(fallbackList) ? fallbackList : [];
    const names = [];
    const seen = new Set();

    const addEntry = entry => {
      if (typeof entry !== 'string') {
        return;
      }
      const trimmed = entry.trim();
      if (!trimmed || seen.has(trimmed)) {
        return;
      }
      seen.add(trimmed);
      names.push(trimmed);
    };

    if (Array.isArray(rawList)) {
      rawList.forEach(addEntry);
    }

    if (names.length === 0 && fallback.length > 0) {
      fallback.forEach(addEntry);
    }

    const paths = names
      .map(name => {
        if (!name) {
          return null;
        }
        const isAbsolute = /^(?:https?:)?\/\//.test(name) || name.startsWith('/');
        const isRelativePath = name.startsWith('./') || name.startsWith('../');
        const alreadyPrefixed = name.startsWith(CARD_IMAGE_BASE_PATH);
        const base = isAbsolute || isRelativePath || alreadyPrefixed
          ? name
          : `${CARD_IMAGE_BASE_PATH}${name}`;
        return encodeURI(base);
      })
      .filter(path => typeof path === 'string' && path.length > 0);

    return {
      names: Object.freeze([...names]),
      paths: Object.freeze([...paths])
    };
  }

  const DEFAULT_CARD_IMAGE_NAMES = Object.freeze([
    'images (1).png',
    'images (2).png',
    'images (3).png',
    'images (4).png',
    'images (5).png',
    'images (6).png',
    'images (7).png',
    'images (8).png',
    'images (9).png',
    'images (10).png',
    'images (11).png',
    'images (12).png',
    'images (13).png',
    'images (14).png',
    'images (15).png',
    'images (16).png',
    'images (17).png',
    'images (18).png',
    'images (19).png',
    'images (20).png',
    'images (21).png',
    'images (22).png',
    'images (23).png',
    'images (24).png',
    'images (25).png',
    'images (26).png',
    'images (27).png',
    'images (28).png',
    'images (29).png',
    'images (30).png',
    'images (31).png',
    'images (32).png'
  ]);
  const DEFAULT_CARD_RESOURCES = resolveCardImageConfig(
    DEFAULT_CARD_IMAGE_NAMES,
    DEFAULT_CARD_IMAGE_NAMES
  );

  const READY_FALLBACK = 'Choisissez une difficulté puis appuyez sur « Commencer » pour lancer une partie.';
  const PROGRESS_FALLBACK = 'Paires restantes : {{remaining}}.';
  const VICTORY_FALLBACK = 'Bravo ! Toutes les paires sont trouvées en {{moves}} coups.';
  const START_FALLBACK = 'Commencer';
  const RESTART_FALLBACK = 'Rejouer';
  const START_ARIA_FALLBACK = 'Démarrer une partie de Twins';
  const RESTART_ARIA_FALLBACK = 'Relancer la partie de Twins';
  const DIFFICULTY_ARIA_FALLBACK = 'Choisir la difficulté du jeu Twins';

  const DEFAULT_CONFIG = Object.freeze({
    defaultDifficulty: 'easy',
    mismatchDelayMs: 900,
    cardAspectRatio: 0.72,
    cardImages: DEFAULT_CARD_IMAGE_NAMES,
    difficulties: Object.freeze({
      easy: Object.freeze({ pairs: 8, columns: 4, rows: 4, gachaTickets: 1 }),
      medium: Object.freeze({ pairs: 12, columns: 6, rows: 4, gachaTickets: 2 }),
      hard: Object.freeze({ pairs: 18, columns: 6, rows: 6, gachaTickets: 3 })
    })
  });

  const numberFormatter = typeof Intl !== 'undefined' && typeof Intl.NumberFormat === 'function'
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;

  const state = {
    elements: null,
    config: DEFAULT_CONFIG,
    difficulty: DEFAULT_CONFIG.defaultDifficulty,
    cards: [],
    cardImages: DEFAULT_CARD_RESOURCES.paths,
    cardElements: [],
    flipped: [],
    matches: 0,
    moves: 0,
    busy: false,
    started: false,
    mismatchTimeoutId: null,
    pendingMismatch: null,
    resizeFrame: null,
    status: {
      key: 'index.sections.twins.status.ready',
      fallback: READY_FALLBACK,
      params: null
    },
    languageHandlerAttached: false,
    languageHandler: null,
    initialized: false,
    configLoaded: false,
    rewardClaimed: false
  };

  function translate(key, fallback, params) {
    const hasKey = typeof key === 'string' && key.length > 0;
    let translator = null;
    if (window.i18n && typeof window.i18n.t === 'function') {
      translator = window.i18n.t.bind(window.i18n);
    } else if (typeof window.t === 'function') {
      translator = window.t.bind(window);
    }
    if (hasKey && translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string') {
          const trimmed = result.trim();
          if (trimmed && trimmed !== key) {
            return trimmed;
          }
        }
      } catch (error) {
        // Ignore translation errors and use fallback.
      }
    }
    if (typeof fallback !== 'string') {
      return hasKey ? key : '';
    }
    if (!params || typeof params !== 'object') {
      return fallback;
    }
    return fallback.replace(/\{\{(\w+)\}\}/g, (_, name) => {
      if (Object.prototype.hasOwnProperty.call(params, name)) {
        const value = params[name];
        return value == null ? '' : String(value);
      }
      return '';
    });
  }

  function formatNumber(value) {
    const numeric = Number.isFinite(value) ? value : Number.parseFloat(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    return numberFormatter ? numberFormatter.format(numeric) : String(Math.floor(numeric));
  }

  function clampInteger(value, fallback, min, max) {
    const numeric = typeof value === 'number' ? value : Number.parseInt(value, 10);
    const safeFallback = Number.isFinite(fallback) ? Math.floor(fallback) : min;
    if (!Number.isFinite(numeric)) {
      return Math.min(Math.max(safeFallback, min), max);
    }
    return Math.min(Math.max(Math.floor(numeric), min), max);
  }

  function clampNumber(value, fallback, min, max) {
    const numeric = typeof value === 'number' ? value : Number.parseFloat(value);
    const safeFallback = Number.isFinite(fallback) ? fallback : min;
    if (!Number.isFinite(numeric)) {
      return Math.min(Math.max(safeFallback, min), max);
    }
    return Math.min(Math.max(numeric, min), max);
  }

  function formatTicketCount(value) {
    if (typeof formatIntegerLocalized === 'function') {
      try {
        return formatIntegerLocalized(value);
      } catch (error) {
        // Ignore formatting errors and fallback below.
      }
    }
    if (Number.isFinite(value)) {
      try {
        return Number(value).toLocaleString();
      } catch (error) {
        // Ignore locale formatting errors.
      }
    }
    return String(value);
  }

  function getVictoryRewardTicketsForDifficulty(difficultyKey) {
    const difficultyConfig = state.config?.difficulties?.[difficultyKey]
      || state.config?.difficulties?.easy
      || DEFAULT_CONFIG.difficulties.easy;
    const amount = Number(difficultyConfig?.gachaTickets);
    if (!Number.isFinite(amount) || amount <= 0) {
      return 0;
    }
    return Math.max(0, Math.floor(amount));
  }

  function awardVictoryTickets() {
    if (state.rewardClaimed) {
      return;
    }
    const tickets = getVictoryRewardTicketsForDifficulty(state.difficulty);
    if (tickets <= 0) {
      state.rewardClaimed = true;
      return;
    }
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      state.rewardClaimed = true;
      return;
    }
    let granted = 0;
    try {
      granted = awardGacha(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Twins: unable to grant gacha tickets', error);
      granted = 0;
    }
    state.rewardClaimed = true;
    if (!Number.isFinite(granted) || granted <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = granted > 1 ? 's' : '';
      const formattedCount = formatTicketCount(granted);
      const message = translate(
        'scripts.arcade.twins.rewards.gachaVictory',
        'Twins : +{count} ticket{suffix} gacha !',
        { count: formattedCount, suffix }
      );
      showToast(message);
    }
  }

  function getCardImagePool() {
    if (Array.isArray(state.cardImages) && state.cardImages.length > 0) {
      return state.cardImages;
    }
    return DEFAULT_CARD_RESOURCES.paths;
  }

  function getMaxPairs() {
    const pool = getCardImagePool();
    return Math.max(1, pool.length || DEFAULT_CARD_RESOURCES.paths.length || 1);
  }

  function sanitizeDifficulty(raw, fallback, maxPairs) {
    const limit = Math.max(1, Number.isFinite(maxPairs) ? Math.floor(maxPairs) : getMaxPairs());
    const fallbackPairs = clampInteger(
      fallback?.pairs,
      DEFAULT_CONFIG.difficulties.easy.pairs,
      1,
      limit
    );
    let pairs = clampInteger(raw?.pairs, fallbackPairs, 1, limit);
    const fallbackColumns = clampInteger(fallback?.columns, DEFAULT_CONFIG.difficulties.easy.columns, 2, 12);
    let columns = clampInteger(raw?.columns, fallbackColumns, 2, 12);
    const fallbackRows = clampInteger(fallback?.rows, DEFAULT_CONFIG.difficulties.easy.rows, 2, 12);
    let rows = clampInteger(raw?.rows, fallbackRows, 2, 12);
    const capacity = Math.max(1, columns * rows);
    if (pairs * 2 > capacity) {
      columns = fallbackColumns;
      rows = fallbackRows;
    }
    const adjustedCapacity = Math.max(1, columns * rows);
    if (pairs * 2 > adjustedCapacity) {
      pairs = Math.min(pairs, Math.floor(adjustedCapacity / 2));
    }
    pairs = Math.max(1, pairs);
    columns = Math.max(2, columns);
    rows = Math.max(2, rows);
    const fallbackTickets = clampInteger(
      fallback?.gachaTickets,
      DEFAULT_CONFIG.difficulties.easy.gachaTickets,
      0,
      99
    );
    const gachaTickets = clampInteger(raw?.gachaTickets, fallbackTickets, 0, 99);
    return Object.freeze({ pairs, columns, rows, gachaTickets });
  }

  function applyConfig(rawConfig) {
    const raw = rawConfig && typeof rawConfig === 'object' ? rawConfig : {};
    const cardResources = resolveCardImageConfig(raw.cardImages, DEFAULT_CONFIG.cardImages);
    const cardImagePaths = cardResources.paths.length > 0
      ? cardResources.paths
      : DEFAULT_CARD_RESOURCES.paths;
    state.cardImages = cardImagePaths;
    const maxPairs = Math.max(1, cardImagePaths.length || DEFAULT_CARD_RESOURCES.paths.length || 1);
    const fallbackDifficulties = DEFAULT_CONFIG.difficulties;
    const resolvedDifficulties = {};
    Object.keys(fallbackDifficulties).forEach(key => {
      const fallback = fallbackDifficulties[key];
      resolvedDifficulties[key] = sanitizeDifficulty(raw?.difficulties?.[key], fallback, maxPairs);
    });
    const availableDifficulties = Object.keys(resolvedDifficulties);
    const defaultDifficulty = availableDifficulties.includes(raw.defaultDifficulty)
      ? raw.defaultDifficulty
      : DEFAULT_CONFIG.defaultDifficulty;
    const mismatchDelayMs = clampInteger(raw.mismatchDelayMs, DEFAULT_CONFIG.mismatchDelayMs, 150, 8000);
    const cardAspectRatio = clampNumber(raw.cardAspectRatio, DEFAULT_CONFIG.cardAspectRatio, 0.45, 1.05);
    state.config = Object.freeze({
      defaultDifficulty,
      mismatchDelayMs,
      cardAspectRatio,
      cardImages: cardResources.names,
      difficulties: Object.freeze(resolvedDifficulties)
    });
    if (!resolvedDifficulties[state.difficulty]) {
      state.difficulty = defaultDifficulty;
    }
    syncDifficultySelect();
    updateStats();
    updateMoves();
    if (!state.started) {
      setStatus('index.sections.twins.status.ready', READY_FALLBACK);
    } else {
      updateProgressStatus();
    }
    updateStartButtonLabel();
    scheduleLayoutUpdate();
  }

  function loadRemoteConfig() {
    if (state.configLoaded || typeof fetch !== 'function') {
      return;
    }
    state.configLoaded = true;
    fetch(CONFIG_PATH)
      .then(response => (response && response.ok ? response.json() : null))
      .then(config => {
        if (config && typeof config === 'object') {
          applyConfig(config);
        }
      })
      .catch(error => {
        console.warn('[Twins] Failed to load config', error);
      });
  }

  function getElements() {
    const section = document.getElementById('twins');
    if (!section) {
      return null;
    }
    const root = section.querySelector('[data-twins-root]');
    if (!root) {
      return null;
    }
    return {
      section,
      root,
      toolbar: root.querySelector('.twins__toolbar'),
      difficultySelect: document.getElementById('twinsDifficulty'),
      startButton: document.getElementById('twinsStartButton'),
      status: document.getElementById('twinsStatus'),
      matches: document.getElementById('twinsMatches'),
      moves: document.getElementById('twinsMoves'),
      grid: document.getElementById('twinsGrid'),
      playfield: root.querySelector('.twins__playfield')
    };
  }

  function syncDifficultySelect() {
    if (!state.elements?.difficultySelect) {
      return;
    }
    const select = state.elements.difficultySelect;
    if (select.value !== state.difficulty) {
      select.value = state.difficulty;
    }
  }

  function normalizeDifficulty(value) {
    if (typeof value !== 'string') {
      return null;
    }
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    return state.config?.difficulties?.[trimmed] ? trimmed : null;
  }

  function getDifficultyConfig(key) {
    const map = state.config?.difficulties || DEFAULT_CONFIG.difficulties;
    if (map[key]) {
      return map[key];
    }
    const fallbackKey = state.config?.defaultDifficulty || DEFAULT_CONFIG.defaultDifficulty;
    return map[fallbackKey] || DEFAULT_CONFIG.difficulties[fallbackKey];
  }

  function setStatus(key, fallback, params) {
    state.status = { key, fallback, params: params || null };
    updateStatus();
  }

  function updateStatus() {
    if (!state.elements?.status || !state.status) {
      return;
    }
    const { key, fallback, params } = state.status;
    const text = translate(key, fallback, params || undefined);
    state.elements.status.textContent = text;
  }

  function updateStats() {
    if (!state.elements?.matches) {
      return;
    }
    const config = getDifficultyConfig(state.difficulty);
    const totalPairs = Math.max(0, config?.pairs || 0);
    const text = translate('index.sections.twins.stats.matches', 'Paires trouvées : {{found}}/{{total}}', {
      found: formatNumber(Math.min(state.matches, totalPairs)),
      total: formatNumber(totalPairs)
    });
    state.elements.matches.textContent = text;
  }

  function updateMoves() {
    if (!state.elements?.moves) {
      return;
    }
    const text = translate('index.sections.twins.stats.moves', 'Coups : {{moves}}', {
      moves: formatNumber(state.moves)
    });
    state.elements.moves.textContent = text;
  }

  function updateStartButtonLabel() {
    if (!state.elements?.startButton) {
      return;
    }
    const button = state.elements.startButton;
    const key = state.started
      ? 'index.sections.twins.ui.restart'
      : 'index.sections.twins.ui.start';
    const fallback = state.started ? RESTART_FALLBACK : START_FALLBACK;
    button.dataset.i18n = key;
    const text = translate(key, fallback);
    button.textContent = text;
    const ariaKey = state.started
      ? 'index.sections.twins.ui.restartAria'
      : 'index.sections.twins.ui.startAria';
    const ariaFallback = state.started ? RESTART_ARIA_FALLBACK : START_ARIA_FALLBACK;
    const ariaLabel = translate(ariaKey, ariaFallback);
    button.setAttribute('aria-label', ariaLabel);
  }

  function updateDifficultyAriaLabel() {
    if (!state.elements?.difficultySelect) {
      return;
    }
    const ariaLabel = translate('index.sections.twins.ui.difficultyAria', DIFFICULTY_ARIA_FALLBACK);
    state.elements.difficultySelect.setAttribute('aria-label', ariaLabel);
  }

  function createCard(pairId, image, uid) {
    return {
      id: uid,
      pairId,
      image,
      faceUp: false,
      matched: false
    };
  }

  function shuffleArray(source) {
    const array = Array.isArray(source) ? [...source] : [];
    for (let i = array.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = array[i];
      array[i] = array[j];
      array[j] = temp;
    }
    return array;
  }

  function createDeck(pairCount) {
    const pool = getCardImagePool();
    const maxPairs = Math.max(1, pool.length || DEFAULT_CARD_RESOURCES.paths.length || 1);
    const safePairs = Math.max(1, Math.min(pairCount, maxPairs));
    const available = shuffleArray(pool);
    const selected = available.slice(0, safePairs);
    const cards = [];
    selected.forEach((image, index) => {
      const pairId = `pair-${index}`;
      cards.push(createCard(pairId, image, `${pairId}-a`));
      cards.push(createCard(pairId, image, `${pairId}-b`));
    });
    return shuffleArray(cards).map(card => ({ ...card }));
  }

  function setCardAriaLabel(element, card) {
    if (!element) {
      return;
    }
    let key = 'index.sections.twins.card.hidden';
    let fallback = 'Carte face cachée';
    if (card?.matched) {
      key = 'index.sections.twins.card.matched';
      fallback = 'Paire validée';
    } else if (card?.faceUp) {
      key = 'index.sections.twins.card.revealed';
      fallback = 'Carte face visible';
    }
    element.setAttribute('aria-label', translate(key, fallback));
  }

  function renderCards() {
    if (!state.elements?.grid) {
      return;
    }
    const grid = state.elements.grid;
    grid.innerHTML = '';
    state.cardElements = [];
    state.cards.forEach((card, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'twins-card';
      button.dataset.index = String(index);
      button.dataset.state = card.matched ? 'matched' : card.faceUp ? 'revealed' : 'hidden';
      if (card.matched || card.faceUp) {
        button.disabled = true;
      }

      const inner = document.createElement('div');
      inner.className = 'twins-card__inner';

      const back = document.createElement('div');
      back.className = 'twins-card__face twins-card__face--back';
      const symbol = document.createElement('span');
      symbol.className = 'twins-card__symbol';
      symbol.setAttribute('aria-hidden', 'true');
      symbol.textContent = '?';
      back.appendChild(symbol);

      const front = document.createElement('div');
      front.className = 'twins-card__face twins-card__face--front';
      const img = document.createElement('img');
      img.className = 'twins-card__image';
      img.src = card.image;
      img.alt = '';
      img.setAttribute('aria-hidden', 'true');
      front.appendChild(img);

      inner.appendChild(back);
      inner.appendChild(front);
      button.appendChild(inner);

      button.addEventListener('click', handleCardClick);
      setCardAriaLabel(button, card);

      grid.appendChild(button);
      state.cardElements[index] = button;
    });
  }

  function revealCard(index) {
    const card = state.cards[index];
    if (!card) {
      return;
    }
    card.faceUp = true;
    const element = state.cardElements[index];
    if (element) {
      element.dataset.state = card.matched ? 'matched' : 'revealed';
      if (!card.matched) {
        element.disabled = true;
      }
      setCardAriaLabel(element, card);
    }
  }

  function hideCard(index) {
    const card = state.cards[index];
    if (!card || card.matched) {
      return;
    }
    card.faceUp = false;
    const element = state.cardElements[index];
    if (element) {
      element.dataset.state = 'hidden';
      element.disabled = false;
      setCardAriaLabel(element, card);
    }
  }

  function markCardMatched(index) {
    const card = state.cards[index];
    if (!card) {
      return;
    }
    card.faceUp = true;
    card.matched = true;
    const element = state.cardElements[index];
    if (element) {
      element.dataset.state = 'matched';
      element.disabled = true;
      setCardAriaLabel(element, card);
    }
  }

  function finalizePendingMismatch() {
    if (state.mismatchTimeoutId != null && typeof window !== 'undefined') {
      window.clearTimeout(state.mismatchTimeoutId);
    }
    state.mismatchTimeoutId = null;
    const pending = Array.isArray(state.pendingMismatch)
      ? state.pendingMismatch.slice()
      : state.flipped.slice();
    if (pending.length) {
      pending.forEach(index => hideCard(index));
    }
    state.pendingMismatch = null;
    state.flipped = [];
    state.busy = false;
  }

  function updateProgressStatus() {
    const config = getDifficultyConfig(state.difficulty);
    const totalPairs = Math.max(0, config?.pairs || 0);
    const remaining = Math.max(0, totalPairs - state.matches);
    if (remaining === 0 && totalPairs > 0) {
      setStatus('index.sections.twins.status.victory', VICTORY_FALLBACK, {
        moves: formatNumber(state.moves)
      });
      awardVictoryTickets();
    } else {
      setStatus('index.sections.twins.status.inProgress', PROGRESS_FALLBACK, {
        remaining: formatNumber(remaining)
      });
    }
  }

  function handleCardClick(event) {
    if (state.busy) {
      return;
    }
    const target = event?.currentTarget;
    const rawIndex = target?.dataset?.index;
    const index = Number.parseInt(rawIndex, 10);
    if (!Number.isFinite(index) || index < 0) {
      return;
    }
    const card = state.cards[index];
    if (!card || card.matched || card.faceUp) {
      return;
    }
    revealCard(index);
    state.flipped.push(index);
    if (state.flipped.length < 2) {
      return;
    }
    state.busy = true;
    state.moves += 1;
    updateMoves();
    const [firstIndex, secondIndex] = state.flipped;
    const firstCard = state.cards[firstIndex];
    const secondCard = state.cards[secondIndex];
    if (firstCard && secondCard && firstCard.pairId === secondCard.pairId) {
      markCardMatched(firstIndex);
      markCardMatched(secondIndex);
      state.matches += 1;
      state.flipped = [];
      state.busy = false;
      updateStats();
      updateProgressStatus();
      updateStartButtonLabel();
      return;
    }
    const pending = state.flipped.slice();
    state.pendingMismatch = pending;
    const delay = Math.max(150, Math.min(5000, Number(state.config.mismatchDelayMs) || DEFAULT_CONFIG.mismatchDelayMs));
    state.mismatchTimeoutId = window.setTimeout(() => {
      pending.forEach(idx => hideCard(idx));
      state.mismatchTimeoutId = null;
      state.pendingMismatch = null;
      state.flipped = [];
      state.busy = false;
      updateProgressStatus();
    }, delay);
  }

  function resetBoardState() {
    finalizePendingMismatch();
    state.cards = [];
    state.cardElements = [];
    state.flipped = [];
    state.matches = 0;
    state.moves = 0;
    state.busy = false;
    state.rewardClaimed = false;
    if (state.elements?.grid) {
      state.elements.grid.innerHTML = '';
    }
  }

  function startGame() {
    const difficultyConfig = getDifficultyConfig(state.difficulty);
    const pairCount = Math.max(1, difficultyConfig?.pairs || DEFAULT_CONFIG.difficulties.easy.pairs);
    resetBoardState();
    state.cards = createDeck(pairCount);
    state.started = true;
    renderCards();
    updateStats();
    updateMoves();
    updateProgressStatus();
    updateStartButtonLabel();
    scheduleLayoutUpdate();
  }

  function handleStartButtonClick() {
    startGame();
  }

  function handleDifficultyChange(event) {
    const normalized = normalizeDifficulty(event?.target?.value)
      || state.config?.defaultDifficulty
      || DEFAULT_CONFIG.defaultDifficulty;
    if (state.difficulty === normalized && !state.started) {
      updateDifficultyAriaLabel();
      scheduleLayoutUpdate();
      return;
    }
    state.difficulty = normalized;
    syncDifficultySelect();
    resetBoardState();
    state.started = false;
    updateStats();
    updateMoves();
    setStatus('index.sections.twins.status.ready', READY_FALLBACK);
    updateStartButtonLabel();
    updateDifficultyAriaLabel();
    scheduleLayoutUpdate();
  }

  function parsePixelValue(value) {
    if (typeof value !== 'string') {
      return 0;
    }
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function updateLayout() {
    if (!state.elements?.grid || !state.elements?.playfield) {
      return;
    }
    const grid = state.elements.grid;
    const playfield = state.elements.playfield;
    const config = getDifficultyConfig(state.difficulty);
    const columns = Math.max(1, Number(config?.columns) || 1);
    const rows = Math.max(1, Number(config?.rows) || 1);
    grid.style.setProperty('--twins-columns', String(columns));
    grid.style.setProperty('--twins-rows', String(rows));
    const rect = playfield.getBoundingClientRect();
    if (!Number.isFinite(rect.width) || rect.width <= 0 || !Number.isFinite(rect.height) || rect.height <= 0) {
      return;
    }
    const computed = window.getComputedStyle(grid);
    const paddingX = parsePixelValue(computed.paddingLeft) + parsePixelValue(computed.paddingRight);
    const paddingY = parsePixelValue(computed.paddingTop) + parsePixelValue(computed.paddingBottom);
    const gapValue = parsePixelValue(computed.gap || computed.gridRowGap || computed.gridColumnGap);
    const contentWidth = rect.width - paddingX;
    const contentHeight = rect.height - paddingY;
    const totalGapX = gapValue * Math.max(0, columns - 1);
    const totalGapY = gapValue * Math.max(0, rows - 1);
    const availableWidth = contentWidth - totalGapX;
    const availableHeight = contentHeight - totalGapY;
    const maxWidthPerCard = availableWidth / columns;
    const maxHeightPerCard = availableHeight / rows;
    const aspect = Math.max(0.45, Number(state.config?.cardAspectRatio) || DEFAULT_CONFIG.cardAspectRatio);
    let widthFromHeight = maxHeightPerCard * aspect;
    if (!Number.isFinite(widthFromHeight) || widthFromHeight <= 0) {
      widthFromHeight = maxWidthPerCard;
    }
    let cardWidth = Math.min(maxWidthPerCard, widthFromHeight);
    if (!Number.isFinite(cardWidth) || cardWidth <= 0) {
      cardWidth = Math.max(40, availableWidth / columns);
    }
    cardWidth = Math.max(40, cardWidth);
    const cardHeight = cardWidth / aspect;
    grid.style.setProperty('--twins-card-max-width', `${cardWidth}px`);
    grid.style.setProperty('--twins-card-max-height', `${cardHeight}px`);
  }

  function scheduleLayoutUpdate() {
    if (!state.elements?.grid) {
      return;
    }
    if (typeof window === 'undefined') {
      updateLayout();
      return;
    }
    if (state.resizeFrame) {
      if (state.resizeFrame.type === 'raf' && typeof window.cancelAnimationFrame === 'function') {
        window.cancelAnimationFrame(state.resizeFrame.id);
      } else if (state.resizeFrame.type === 'timeout') {
        window.clearTimeout(state.resizeFrame.id);
      }
      state.resizeFrame = null;
    }
    const run = () => {
      state.resizeFrame = null;
      updateLayout();
    };
    if (typeof window.requestAnimationFrame === 'function') {
      const id = window.requestAnimationFrame(run);
      state.resizeFrame = { type: 'raf', id };
    } else {
      const id = window.setTimeout(run, 16);
      state.resizeFrame = { type: 'timeout', id };
    }
  }

  function handleWindowResize() {
    scheduleLayoutUpdate();
  }

  function initialize() {
    if (state.initialized) {
      return;
    }
    state.elements = getElements();
    if (!state.elements) {
      return;
    }
    state.initialized = true;
    syncDifficultySelect();
    updateStats();
    updateMoves();
    setStatus('index.sections.twins.status.ready', READY_FALLBACK);
    updateStartButtonLabel();
    updateDifficultyAriaLabel();
    state.elements.startButton?.addEventListener('click', handleStartButtonClick);
    state.elements.difficultySelect?.addEventListener('change', handleDifficultyChange);
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', handleWindowResize);
    }
    attachLanguageListener();
    loadRemoteConfig();
    scheduleLayoutUpdate();
  }

  function attachLanguageListener() {
    if (state.languageHandlerAttached || typeof window === 'undefined') {
      updateDifficultyAriaLabel();
      return;
    }
    const handler = () => {
      updateStats();
      updateMoves();
      updateStartButtonLabel();
      if (!state.started) {
        setStatus('index.sections.twins.status.ready', READY_FALLBACK);
      } else {
        updateProgressStatus();
      }
      updateDifficultyAriaLabel();
      state.cardElements.forEach((element, index) => {
        const card = state.cards[index];
        if (card && element) {
          setCardAriaLabel(element, card);
        }
      });
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandlerAttached = true;
    state.languageHandler = handler;
    updateDifficultyAriaLabel();
  }

  const api = {
    onEnter() {
      initialize();
      if (!state.initialized) {
        return;
      }
      if (!state.started) {
        setStatus('index.sections.twins.status.ready', READY_FALLBACK);
      } else {
        updateProgressStatus();
      }
      scheduleLayoutUpdate();
    },
    onLeave() {
      finalizePendingMismatch();
    }
  };

  window.twinsArcade = api;
})();
