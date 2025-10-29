(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const AUTOSAVE_ID = 'holdem';

  const DEFAULT_CONFIG = Object.freeze({
    blinds: { small: 20, big: 40 },
    dealerSpeedMs: 650,
    minRaise: 40,
    startingStack: 1000,
    opponentCount: { min: 4, max: 5 },
    aiProfiles: {
      patient: { aggression: 0.35, caution: 0.6, bluff: 0.08 },
      daring: { aggression: 0.55, caution: 0.4, bluff: 0.18 }
    },
    defaultAiProfile: 'patient'
  });

  const DEFAULT_AI_PROFILE = Object.freeze({ aggression: 0.4, caution: 0.5, bluff: 0.12 });

  const SUITS = Object.freeze([
    { id: 'spades', symbol: '♠', color: 'black' },
    { id: 'hearts', symbol: '♥', color: 'red' },
    { id: 'diamonds', symbol: '♦', color: 'red' },
    { id: 'clubs', symbol: '♣', color: 'black' }
  ]);

  const RANKS = Object.freeze([
    { id: '2', value: 2 },
    { id: '3', value: 3 },
    { id: '4', value: 4 },
    { id: '5', value: 5 },
    { id: '6', value: 6 },
    { id: '7', value: 7 },
    { id: '8', value: 8 },
    { id: '9', value: 9 },
    { id: '10', value: 10 },
    { id: 'J', value: 11 },
    { id: 'Q', value: 12 },
    { id: 'K', value: 13 },
    { id: 'A', value: 14 }
  ]);

  const AI_NAME_POOL = Object.freeze(['Lyra', 'Mira', 'Orion', 'Nova', 'Vega', 'Altair', 'Sora', 'Cassiopeia']);
  const HAND_LABEL_KEYS = Object.freeze([
    'index.sections.holdem.hands.highCard',
    'index.sections.holdem.hands.pair',
    'index.sections.holdem.hands.twoPair',
    'index.sections.holdem.hands.threeKind',
    'index.sections.holdem.hands.straight',
    'index.sections.holdem.hands.flush',
    'index.sections.holdem.hands.fullHouse',
    'index.sections.holdem.hands.fourKind',
    'index.sections.holdem.hands.straightFlush'
  ]);
  const MAX_HAND_SCORE = 9 * 1e10;

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function clamp(value, min, max) {
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return fallback;
    }
    let translator = null;
    if (typeof window !== 'undefined') {
      if (window.i18n && typeof window.i18n.t === 'function') {
        translator = window.i18n.t.bind(window.i18n);
      } else if (typeof window.t === 'function') {
        translator = window.t.bind(window);
      }
    } else if (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function') {
      translator = globalThis.t.bind(globalThis);
    }
    if (translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string') {
          const trimmed = result.trim();
          if (trimmed && trimmed !== key) {
            return trimmed;
          }
        }
      } catch (error) {
        console.warn('Holdem translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return fallback;
    }
    return key;
  }

  function toPositiveInteger(value, fallback) {
    const numeric = Number(value);
    if (Number.isFinite(numeric) && numeric > 0) {
      return Math.floor(numeric);
    }
    return fallback;
  }

  function normalizeAiProfile(rawProfile) {
    const source = rawProfile && typeof rawProfile === 'object' ? rawProfile : {};
    return {
      aggression: clamp(Number(source.aggression) || DEFAULT_AI_PROFILE.aggression, 0, 1),
      caution: clamp(Number(source.caution) || DEFAULT_AI_PROFILE.caution, 0, 1),
      bluff: clamp(Number(source.bluff) || DEFAULT_AI_PROFILE.bluff, 0, 1)
    };
  }

  function getHoldemConfig() {
    const raw =
      GLOBAL_CONFIG
      && GLOBAL_CONFIG.arcade
      && GLOBAL_CONFIG.arcade.holdem
        ? GLOBAL_CONFIG.arcade.holdem
        : null;

    const blindsSource = raw && raw.blinds ? raw.blinds : DEFAULT_CONFIG.blinds;
    const smallBlind = toPositiveInteger(blindsSource.small, DEFAULT_CONFIG.blinds.small);
    const bigBlind = toPositiveInteger(blindsSource.big, DEFAULT_CONFIG.blinds.big);
    const minRaise = toPositiveInteger(raw && raw.minRaise, DEFAULT_CONFIG.minRaise);
    const startingStack = toPositiveInteger(raw && raw.startingStack, DEFAULT_CONFIG.startingStack);

    let opponentMin = DEFAULT_CONFIG.opponentCount.min;
    let opponentMax = DEFAULT_CONFIG.opponentCount.max;
    if (raw && raw.opponentCount && typeof raw.opponentCount === 'object') {
      opponentMin = toPositiveInteger(raw.opponentCount.min, opponentMin);
      opponentMax = toPositiveInteger(raw.opponentCount.max, opponentMax);
    }
    if (opponentMax < opponentMin) {
      opponentMax = opponentMin;
    }

    const dealerSpeedMs = toPositiveInteger(raw && raw.dealerSpeedMs, DEFAULT_CONFIG.dealerSpeedMs);

    const aiProfiles = {};
    const sourceProfiles = raw && raw.aiProfiles && typeof raw.aiProfiles === 'object'
      ? raw.aiProfiles
      : DEFAULT_CONFIG.aiProfiles;
    Object.keys(sourceProfiles).forEach(id => {
      aiProfiles[id] = normalizeAiProfile(sourceProfiles[id]);
    });
    if (!Object.keys(aiProfiles).length) {
      aiProfiles.default = normalizeAiProfile(DEFAULT_AI_PROFILE);
    }

    let defaultProfileId = typeof raw?.defaultAiProfile === 'string' && aiProfiles[raw.defaultAiProfile]
      ? raw.defaultAiProfile
      : DEFAULT_CONFIG.defaultAiProfile;
    if (!aiProfiles[defaultProfileId]) {
      defaultProfileId = Object.keys(aiProfiles)[0];
    }

    return {
      blinds: { small: smallBlind, big: Math.max(bigBlind, smallBlind * 2) },
      dealerSpeedMs,
      minRaise: Math.max(minRaise, Math.max(bigBlind, smallBlind * 2)),
      startingStack,
      opponentCount: { min: clamp(opponentMin, 2, 7), max: clamp(opponentMax, 2, 7) },
      aiProfiles,
      defaultAiProfile: defaultProfileId
    };
  }

  const CONFIG = getHoldemConfig();

  const elements = {
    status: document.getElementById('holdemStatus'),
    community: document.getElementById('holdemCommunityCards'),
    opponents: document.getElementById('holdemPlayers'),
    potValue: document.getElementById('holdemPotValue'),
    potFill: document.getElementById('holdemPotFill'),
    playerCards: document.getElementById('holdemPlayerCards'),
    newHand: document.getElementById('holdemNewHand'),
    fold: document.getElementById('holdemFold'),
    checkCall: document.getElementById('holdemCheckCall'),
    raise: document.getElementById('holdemRaise'),
    raiseAmount: document.getElementById('holdemRaiseAmount')
  };

  const state = {
    config: CONFIG,
    deck: [],
    communityCards: [],
    players: [],
    pot: 0,
    phase: 'idle',
    currentBet: 0,
    awaitingPlayer: false,
    dealerPosition: 0,
    statusKey: 'index.sections.holdem.status.intro',
    statusParams: {},
    pendingTimeouts: []
  };
  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const api = window.ArcadeAutosave;
    if (!api || typeof api.set !== 'function' || typeof api.get !== 'function') {
      return null;
    }
    return api;
  }

  function formatAmount(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    return Math.max(0, Math.round(numeric)).toLocaleString();
  }

  function sanitizeName(name, fallback) {
    if (typeof name === 'string') {
      const trimmed = name.trim();
      if (trimmed) {
        return trimmed;
      }
    }
    return fallback;
  }

  function sanitizeStack(value, fallback) {
    const numeric = Number(value);
    if (Number.isFinite(numeric) && numeric >= 0) {
      return Math.floor(numeric);
    }
    return fallback;
  }

  function createHero(stack) {
    return {
      id: 'hero',
      type: 'human',
      name: translate('index.sections.holdem.labels.you', 'Vous'),
      stack: sanitizeStack(stack, state.config.startingStack),
      bet: 0,
      cards: [],
      folded: false,
      allIn: false,
      profileId: null,
      profile: null,
      statusKey: 'index.sections.holdem.playerStatus.waiting',
      statusParams: null
    };
  }

  function pickAiName(usedNames) {
    for (let i = 0; i < AI_NAME_POOL.length; i += 1) {
      const candidate = AI_NAME_POOL[i];
      if (!usedNames.has(candidate)) {
        usedNames.add(candidate);
        return candidate;
      }
    }
    const fallback = `IA ${usedNames.size + 1}`;
    usedNames.add(fallback);
    return fallback;
  }

  function createAiPlayer(index, options = {}) {
    const usedNames = new Set(state.players.map(player => player.name));
    const profileId = options.profileId && state.config.aiProfiles[options.profileId]
      ? options.profileId
      : state.config.defaultAiProfile;
    const name = sanitizeName(options.name, pickAiName(usedNames));
    return {
      id: `ai-${index}`,
      type: 'ai',
      name,
      stack: sanitizeStack(options.stack, state.config.startingStack),
      bet: 0,
      cards: [],
      folded: false,
      allIn: false,
      profileId,
      profile: state.config.aiProfiles[profileId] || normalizeAiProfile(null),
      statusKey: 'index.sections.holdem.playerStatus.waiting',
      statusParams: null
    };
  }

  function loadAutosave() {
    const api = getAutosaveApi();
    if (!api) {
      return;
    }
    let data = null;
    try {
      data = api.get(AUTOSAVE_ID);
    } catch (error) {
      data = null;
    }
    if (!data || typeof data !== 'object') {
      return;
    }

    if (Number.isFinite(data.dealerPosition)) {
      state.dealerPosition = data.dealerPosition;
    }

    const heroEntry = Array.isArray(data.players)
      ? data.players.find(player => player && (player.type === 'human' || player.id === 'hero'))
      : null;
    const hero = createHero(heroEntry ? heroEntry.stack : state.config.startingStack);

    const targetOpponentCount = clamp(state.config.opponentCount.min, 2, state.config.opponentCount.max);
    const aiEntries = Array.isArray(data.players)
      ? data.players.filter(player => player && player.type !== 'human')
      : [];

    const aiPlayers = [];
    const usedNames = new Set();
    usedNames.add(hero.name);
    for (let i = 0; i < aiEntries.length && aiPlayers.length < targetOpponentCount; i += 1) {
      const entry = aiEntries[i];
      const profileId = typeof entry.profileId === 'string' && state.config.aiProfiles[entry.profileId]
        ? entry.profileId
        : state.config.defaultAiProfile;
      const name = sanitizeName(entry.name, pickAiName(usedNames));
      usedNames.add(name);
      aiPlayers.push({
        id: `ai-${i}`,
        type: 'ai',
        name,
        stack: sanitizeStack(entry.stack, state.config.startingStack),
        bet: 0,
        cards: [],
        folded: false,
        allIn: false,
        profileId,
        profile: state.config.aiProfiles[profileId] || normalizeAiProfile(null),
        statusKey: 'index.sections.holdem.playerStatus.waiting',
        statusParams: null
      });
    }

    while (aiPlayers.length < targetOpponentCount) {
      const player = createAiPlayer(aiPlayers.length, {});
      usedNames.add(player.name);
      aiPlayers.push(player);
    }

    state.players = [hero, ...aiPlayers];
  }

  function commitAutosave() {
    const api = getAutosaveApi();
    if (!api) {
      return;
    }
    const payload = {
      version: 1,
      dealerPosition: state.dealerPosition,
      players: state.players.map(player => ({
        id: player.id,
        type: player.type,
        name: player.name,
        stack: player.stack,
        profileId: player.profileId
      }))
    };
    try {
      api.set(AUTOSAVE_ID, payload);
    } catch (error) {
      // Ignore autosave failures (quota, private browsing, etc.)
    }
  }

  function ensurePlayers() {
    if (state.players.length) {
      return;
    }
    const hero = createHero(state.config.startingStack);
    const count = clamp(state.config.opponentCount.min, 2, state.config.opponentCount.max);
    const players = [hero];
    for (let i = 0; i < count; i += 1) {
      players.push(createAiPlayer(i, {}));
    }
    state.players = players;
  }
  function clearPendingTimeouts() {
    while (state.pendingTimeouts.length) {
      clearTimeout(state.pendingTimeouts.pop());
    }
  }

  function createDeck() {
    const deck = [];
    for (let suitIndex = 0; suitIndex < SUITS.length; suitIndex += 1) {
      const suit = SUITS[suitIndex];
      for (let rankIndex = 0; rankIndex < RANKS.length; rankIndex += 1) {
        const rank = RANKS[rankIndex];
        deck.push({
          id: `${rank.id}-${suit.id}`,
          rank: rank.id,
          value: rank.value,
          suit: suit.id,
          symbol: suit.symbol,
          color: suit.color
        });
      }
    }
    return deck;
  }

  function shuffleDeck(deck) {
    for (let i = deck.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = deck[i];
      deck[i] = deck[j];
      deck[j] = temp;
    }
  }

  function drawCard() {
    if (!state.deck.length) {
      return null;
    }
    return state.deck.pop();
  }

  function resetPlayerForHand(player) {
    player.bet = 0;
    player.cards = [];
    player.folded = player.stack <= 0;
    player.allIn = player.stack <= 0;
    player.statusKey = player.folded
      ? 'index.sections.holdem.playerStatus.folded'
      : 'index.sections.holdem.playerStatus.waiting';
    player.statusParams = null;
  }

  function resetTableState() {
    state.deck = createDeck();
    shuffleDeck(state.deck);
    state.communityCards = [];
    state.pot = 0;
    state.currentBet = 0;
    state.phase = 'preflop';
    state.awaitingPlayer = false;
    clearPendingTimeouts();
    for (let i = 0; i < state.players.length; i += 1) {
      resetPlayerForHand(state.players[i]);
    }
  }

  function updateStatus(key, fallback, params) {
    state.statusKey = key;
    state.statusParams = params || null;
    if (!elements.status) {
      return;
    }
    elements.status.dataset.i18n = key;
    elements.status.textContent = translate(key, fallback, params);
  }

  function renderPot() {
    if (elements.potValue) {
      elements.potValue.textContent = formatAmount(state.pot);
    }
    if (elements.potFill) {
      const totalStacks = state.players.reduce((sum, player) => sum + Math.max(0, player.stack), 0);
      const denominator = state.pot + totalStacks;
      const ratio = denominator > 0 ? (state.pot / denominator) * 100 : 0;
      elements.potFill.style.width = `${clamp(ratio, 0, 100)}%`;
    }
  }

  function createCardElement(card, hidden) {
    const item = document.createElement('li');
    item.className = 'holdem-card';
    if (hidden) {
      item.classList.add('holdem-card--hidden');
      item.setAttribute('aria-hidden', 'true');
      item.textContent = '◆';
      return item;
    }
    item.classList.add(card.color === 'red' ? 'holdem-card--red' : 'holdem-card--black');
    const rankSpan = document.createElement('span');
    rankSpan.className = 'holdem-card__rank';
    rankSpan.textContent = card.rank;
    const suitSpan = document.createElement('span');
    suitSpan.className = 'holdem-card__suit';
    suitSpan.textContent = card.symbol;
    item.appendChild(rankSpan);
    item.appendChild(suitSpan);
    item.setAttribute('aria-label', translate('index.sections.holdem.hand.cardLabel', `${card.rank}${card.symbol}`, {
      rank: card.rank,
      suit: card.symbol
    }));
    return item;
  }

  function renderCardList(container, cards, options = {}) {
    if (!container) {
      return;
    }
    container.innerHTML = '';
    if (!cards || !cards.length) {
      const placeholder = document.createElement('li');
      placeholder.className = 'holdem-cards__placeholder';
      if (options.placeholderKey) {
        placeholder.dataset.i18n = options.placeholderKey;
        placeholder.textContent = translate(options.placeholderKey, options.placeholderFallback || '');
      } else {
        placeholder.textContent = options.placeholderFallback || '';
      }
      container.appendChild(placeholder);
      return;
    }
    const fragment = document.createDocumentFragment();
    for (let i = 0; i < cards.length; i += 1) {
      fragment.appendChild(createCardElement(cards[i], Boolean(options.hidden)));
    }
    container.appendChild(fragment);
  }

  function renderCommunity() {
    renderCardList(elements.community, state.communityCards, {
      placeholderKey: 'index.sections.holdem.communityPlaceholder',
      placeholderFallback: 'Les cartes communes apparaîtront ici.'
    });
  }

  function renderPlayerHand() {
    const hero = state.players[0];
    renderCardList(elements.playerCards, hero ? hero.cards : [], {
      placeholderKey: 'index.sections.holdem.hand.placeholder',
      placeholderFallback: 'Aucune carte distribuée pour le moment.'
    });
  }

  function getPlayerStatusFallback(key) {
    switch (key) {
      case 'index.sections.holdem.playerStatus.folded':
        return 'Couché';
      case 'index.sections.holdem.playerStatus.allIn':
        return 'All-in';
      case 'index.sections.holdem.playerStatus.raise':
        return 'Relance';
      case 'index.sections.holdem.playerStatus.call':
        return 'Suit';
      case 'index.sections.holdem.playerStatus.check':
        return 'Check';
      case 'index.sections.holdem.playerStatus.blind':
        return 'Blind';
      case 'index.sections.holdem.playerStatus.waiting':
        return 'En attente';
      default:
        return 'En jeu';
    }
  }

  function renderOpponents() {
    if (!elements.opponents) {
      return;
    }
    elements.opponents.innerHTML = '';
    const fragment = document.createDocumentFragment();
    for (let i = 1; i < state.players.length; i += 1) {
      const player = state.players[i];
      const article = document.createElement('article');
      article.className = 'holdem-player';
      if (player.folded) {
        article.classList.add('holdem-player--folded');
      }
      if (player.allIn) {
        article.classList.add('holdem-player--all-in');
      }
      const name = document.createElement('h4');
      name.className = 'holdem-player__name';
      name.textContent = player.name;
      article.appendChild(name);

      const stack = document.createElement('p');
      stack.className = 'holdem-player__stack';
      const label = document.createElement('span');
      label.className = 'holdem-player__stack-label';
      label.dataset.i18n = 'index.sections.holdem.labels.stack';
      label.textContent = translate('index.sections.holdem.labels.stack', 'Stack');
      const value = document.createElement('span');
      value.className = 'holdem-player__stack-value';
      value.textContent = formatAmount(player.stack);
      stack.appendChild(label);
      stack.appendChild(value);
      article.appendChild(stack);

      const status = document.createElement('p');
      status.className = 'holdem-player__status';
      const statusKey = player.statusKey || 'index.sections.holdem.playerStatus.waiting';
      status.dataset.i18n = statusKey;
      status.textContent = translate(statusKey, getPlayerStatusFallback(statusKey), player.statusParams);
      article.appendChild(status);

      const cardList = document.createElement('ul');
      cardList.className = 'holdem-cards holdem-cards--opponent';
      const reveal = state.phase === 'showdown' || state.phase === 'complete';
      renderCardList(cardList, player.cards, { hidden: !reveal });
      article.appendChild(cardList);

      fragment.appendChild(article);
    }
    elements.opponents.appendChild(fragment);
  }

  function renderControls() {
    if (!elements.checkCall || !elements.fold || !elements.raise || !elements.raiseAmount) {
      return;
    }
    const hero = state.players[0];
    const activeHand = state.phase !== 'idle' && state.phase !== 'complete' && state.phase !== 'showdown';
    const canAct = activeHand && state.awaitingPlayer && hero && !hero.folded && !hero.allIn;
    elements.fold.disabled = !canAct;
    elements.raise.disabled = !canAct;
    elements.raiseAmount.disabled = !canAct;
    elements.checkCall.disabled = !canAct;

    if (!hero) {
      return;
    }
    const requiredToCall = Math.max(0, state.currentBet - hero.bet);
    if (requiredToCall > 0) {
      elements.checkCall.dataset.i18n = 'index.sections.holdem.controls.callWithAmount';
      elements.checkCall.textContent = translate(
        'index.sections.holdem.controls.callWithAmount',
        `Suivre (${formatAmount(requiredToCall)})`,
        { amount: formatAmount(requiredToCall) }
      );
    } else {
      elements.checkCall.dataset.i18n = 'index.sections.holdem.controls.check';
      elements.checkCall.textContent = translate('index.sections.holdem.controls.check', 'Checker');
    }
  }

  function render() {
    renderPot();
    renderCommunity();
    renderPlayerHand();
    renderOpponents();
    renderControls();
  }
  function evaluateFiveCardHand(cards) {
    const values = cards.map(card => card.value).sort((a, b) => b - a);
    const suits = cards.map(card => card.suit);
    const counts = new Map();
    for (let i = 0; i < values.length; i += 1) {
      const value = values[i];
      counts.set(value, (counts.get(value) || 0) + 1);
    }
    const countEntries = Array.from(counts.entries()).sort((a, b) => {
      if (b[1] === a[1]) {
        return b[0] - a[0];
      }
      return b[1] - a[1];
    });

    const isFlush = suits.every(suit => suit === suits[0]);

    let uniqueValues = Array.from(new Set(values));
    uniqueValues.sort((a, b) => b - a);

    let isStraight = false;
    let straightHigh = uniqueValues[0];
    if (uniqueValues.length >= 5) {
      let consecutive = 1;
      for (let i = 1; i < uniqueValues.length; i += 1) {
        if (uniqueValues[i] === uniqueValues[i - 1] - 1) {
          consecutive += 1;
          if (consecutive >= 5) {
            isStraight = true;
            straightHigh = uniqueValues[i - 4];
            break;
          }
        } else {
          consecutive = 1;
        }
      }
      if (!isStraight && uniqueValues.includes(14)) {
        const wheel = [5, 4, 3, 2];
        const hasWheel = wheel.every(value => uniqueValues.includes(value));
        if (hasWheel) {
          isStraight = true;
          straightHigh = 5;
        }
      }
    }

    let rank = 0;
    let valuesForScore = [];

    if (isStraight && isFlush) {
      rank = 8;
      valuesForScore = [straightHigh];
    } else if (countEntries[0][1] === 4) {
      rank = 7;
      valuesForScore = [countEntries[0][0], countEntries[1][0]];
    } else if (countEntries[0][1] === 3 && countEntries[1] && countEntries[1][1] === 2) {
      rank = 6;
      valuesForScore = [countEntries[0][0], countEntries[1][0]];
    } else if (isFlush) {
      rank = 5;
      valuesForScore = values;
    } else if (isStraight) {
      rank = 4;
      valuesForScore = [straightHigh, ...values.filter(value => value !== straightHigh).slice(0, 4)];
    } else if (countEntries[0][1] === 3) {
      rank = 3;
      const kickers = uniqueValues.filter(value => value !== countEntries[0][0]).slice(0, 2);
      valuesForScore = [countEntries[0][0], ...kickers];
    } else if (countEntries[0][1] === 2 && countEntries[1] && countEntries[1][1] === 2) {
      rank = 2;
      const pairValues = [countEntries[0][0], countEntries[1][0]].sort((a, b) => b - a);
      const kicker = uniqueValues.find(value => value !== pairValues[0] && value !== pairValues[1]) || 0;
      valuesForScore = [...pairValues, kicker];
    } else if (countEntries[0][1] === 2) {
      rank = 1;
      const kickers = uniqueValues.filter(value => value !== countEntries[0][0]).slice(0, 3);
      valuesForScore = [countEntries[0][0], ...kickers];
    } else {
      rank = 0;
      valuesForScore = values;
    }

    let score = rank * 1e10;
    for (let i = 0; i < valuesForScore.length; i += 1) {
      score += valuesForScore[i] * Math.pow(10, 8 - i * 2);
    }
    return { rank, score, values: valuesForScore, isStraight, isFlush, straightHigh };
  }

  function evaluateBestHand(cards) {
    if (!cards || cards.length < 5) {
      return { score: 0, rank: 0, labelKey: HAND_LABEL_KEYS[0], values: [] };
    }
    let best = null;
    for (let a = 0; a < cards.length - 4; a += 1) {
      for (let b = a + 1; b < cards.length - 3; b += 1) {
        for (let c = b + 1; c < cards.length - 2; c += 1) {
          for (let d = c + 1; d < cards.length - 1; d += 1) {
            for (let e = d + 1; e < cards.length; e += 1) {
              const combination = [cards[a], cards[b], cards[c], cards[d], cards[e]];
              const evaluated = evaluateFiveCardHand(combination);
              if (!best || evaluated.score > best.score) {
                let labelKey = HAND_LABEL_KEYS[evaluated.rank] || HAND_LABEL_KEYS[0];
                if (evaluated.rank === 8 && evaluated.straightHigh === 14) {
                  labelKey = 'index.sections.holdem.hands.royalFlush';
                }
                best = {
                  score: evaluated.score,
                  rank: evaluated.rank,
                  labelKey,
                  values: evaluated.values,
                  cards: combination
                };
              }
            }
          }
        }
      }
    }
    return best || { score: 0, rank: 0, labelKey: HAND_LABEL_KEYS[0], values: [] };
  }

  function describeHand(result) {
    if (!result) {
      return translate(HAND_LABEL_KEYS[0], 'Hauteur');
    }
    const fallback = {
      0: 'Hauteur',
      1: 'Paire',
      2: 'Double paire',
      3: 'Brelan',
      4: 'Quinte',
      5: 'Couleur',
      6: 'Full',
      7: 'Carré',
      8: result.rank === 8 && result.values[0] === 14 ? 'Quinte flush royale' : 'Quinte flush'
    };
    return translate(result.labelKey, fallback[result.rank] || 'Hauteur');
  }

  function estimatePreflopStrength(cards) {
    if (!cards || cards.length < 2) {
      return 0.1;
    }
    const [cardA, cardB] = cards;
    let strength = 0.2;
    if (cardA.value === cardB.value) {
      strength = 0.6 + (cardA.value / 20);
    } else if (cardA.suit === cardB.suit) {
      strength += 0.1;
    }
    const gap = Math.abs(cardA.value - cardB.value);
    if (gap <= 1) {
      strength += 0.1;
    }
    strength += (cardA.value + cardB.value) / 30;
    return clamp(strength, 0, 0.95);
  }

  function estimateStrength(player) {
    const cards = [...player.cards, ...state.communityCards];
    if (cards.length < 5) {
      return estimatePreflopStrength(player.cards);
    }
    const best = evaluateBestHand(cards);
    return clamp(best.score / MAX_HAND_SCORE, 0, 1);
  }

  function setPlayerStatus(player, key, params) {
    player.statusKey = key;
    player.statusParams = params || null;
  }

  function applyBet(player, targetBet) {
    if (!player || player.folded || player.allIn) {
      return;
    }
    const sanitizedTarget = Math.max(player.bet, Math.floor(targetBet));
    const available = player.bet + player.stack;
    const actualTarget = Math.min(sanitizedTarget, available);
    const increase = actualTarget - player.bet;
    if (increase <= 0) {
      return;
    }
    player.bet += increase;
    player.stack -= increase;
    state.pot += increase;
    if (player.stack <= 0) {
      player.stack = 0;
      player.allIn = true;
      setPlayerStatus(player, 'index.sections.holdem.playerStatus.allIn');
    }
  }

  function getActivePlayers() {
    return state.players.filter(player => !player.folded && (player.stack > 0 || player.bet > 0 || player.allIn));
  }

  function resolveIfOnlyOne() {
    const active = getActivePlayers();
    if (active.length === 1) {
      const winner = active[0];
      const potAmount = state.pot;
      distributePot([winner]);
      if (winner.id === 'hero') {
        updateStatus('index.sections.holdem.status.playerWins', 'Vous remportez le pot ({amount}).', {
          amount: formatAmount(potAmount)
        });
      } else {
        updateStatus('index.sections.holdem.status.playerFold', `${winner.name} remporte le pot ({amount}).`, {
          winner: winner.name,
          amount: formatAmount(potAmount)
        });
      }
      return true;
    }
    return false;
  }

  function resetBetsForNextRound() {
    for (let i = 0; i < state.players.length; i += 1) {
      const player = state.players[i];
      player.bet = 0;
      if (!player.folded && !player.allIn) {
        setPlayerStatus(player, 'index.sections.holdem.playerStatus.waiting');
      }
    }
    state.currentBet = 0;
  }

  function postBlinds() {
    const smallBlindIndex = state.players.length > 1 ? 1 : 0;
    const bigBlindIndex = state.players.length > 2 ? 2 : smallBlindIndex;
    const smallBlindPlayer = state.players[smallBlindIndex];
    const bigBlindPlayer = state.players[bigBlindIndex];

    if (smallBlindPlayer) {
      const amount = Math.min(state.config.blinds.small, smallBlindPlayer.stack + smallBlindPlayer.bet);
      applyBet(smallBlindPlayer, smallBlindPlayer.bet + amount);
      setPlayerStatus(smallBlindPlayer, 'index.sections.holdem.playerStatus.blind', {
        amount: formatAmount(amount)
      });
    }
    if (bigBlindPlayer) {
      const amount = Math.min(state.config.blinds.big, bigBlindPlayer.stack + bigBlindPlayer.bet);
      applyBet(bigBlindPlayer, bigBlindPlayer.bet + amount);
      setPlayerStatus(bigBlindPlayer, 'index.sections.holdem.playerStatus.blind', {
        amount: formatAmount(amount)
      });
      state.currentBet = Math.max(state.currentBet, bigBlindPlayer.bet);
    }
  }

  function executeAiRound({ allowRaise = true } = {}) {
    for (let i = 1; i < state.players.length; i += 1) {
      const player = state.players[i];
      if (player.folded || player.allIn) {
        continue;
      }
      const requiredToCall = Math.max(0, state.currentBet - player.bet);
      const strength = estimateStrength(player);
      const profile = player.profile || state.config.aiProfiles[player.profileId] || DEFAULT_AI_PROFILE;
      let action = 'check';
      let targetBet = player.bet;

      if (requiredToCall > 0) {
        const foldThreshold = 0.25 + (0.4 * (1 - profile.caution));
        if (strength < foldThreshold && Math.random() > profile.bluff) {
          player.folded = true;
          setPlayerStatus(player, 'index.sections.holdem.playerStatus.folded');
          continue;
        }
        if (allowRaise && strength > 0.65 && Math.random() < profile.aggression) {
          const raiseTarget = state.currentBet + Math.max(state.config.minRaise, Math.round(state.config.minRaise * (0.8 + strength)));
          targetBet = Math.min(player.bet + player.stack, raiseTarget);
          action = targetBet > player.bet ? 'raise' : 'call';
        } else {
          targetBet = player.bet + Math.min(player.stack, requiredToCall);
          action = requiredToCall ? 'call' : 'check';
        }
      } else {
        if (allowRaise && strength > 0.55 && Math.random() < profile.aggression) {
          const raiseTarget = Math.max(state.currentBet + state.config.minRaise, player.bet + Math.round(state.config.minRaise * (0.8 + strength)));
          targetBet = Math.min(player.bet + player.stack, raiseTarget);
          action = targetBet > player.bet ? 'raise' : 'check';
        } else {
          action = 'check';
        }
      }

      applyBet(player, targetBet);
      if (player.bet > state.currentBet) {
        state.currentBet = player.bet;
      }
      setPlayerStatus(player, `index.sections.holdem.playerStatus.${action}`, {
        amount: formatAmount(player.bet)
      });
    }
  }

  function executeAiResponsesAfterPlayerAction() {
    for (let i = 1; i < state.players.length; i += 1) {
      const player = state.players[i];
      if (player.folded || player.allIn) {
        continue;
      }
      const requiredToCall = Math.max(0, state.currentBet - player.bet);
      if (requiredToCall <= 0) {
        continue;
      }
      const strength = estimateStrength(player);
      const profile = player.profile || state.config.aiProfiles[player.profileId] || DEFAULT_AI_PROFILE;
      const callThreshold = 0.22 + (0.5 * profile.aggression);
      if (strength + profile.bluff < callThreshold) {
        player.folded = true;
        setPlayerStatus(player, 'index.sections.holdem.playerStatus.folded');
        continue;
      }
      applyBet(player, player.bet + Math.min(player.stack, requiredToCall));
      if (player.bet > state.currentBet) {
        state.currentBet = player.bet;
      }
      setPlayerStatus(player, 'index.sections.holdem.playerStatus.call', {
        amount: formatAmount(player.bet)
      });
    }
  }
  function revealCommunityCards(count) {
    for (let i = 0; i < count; i += 1) {
      const card = drawCard();
      if (card) {
        state.communityCards.push(card);
      }
    }
  }

  function schedulePhaseAdvance(delay = state.config.dealerSpeedMs) {
    state.awaitingPlayer = false;
    const timer = setTimeout(() => {
      advancePhase();
    }, Math.max(150, delay));
    state.pendingTimeouts.push(timer);
  }

  function advancePhase() {
    if (resolveIfOnlyOne()) {
      state.phase = 'complete';
      state.awaitingPlayer = false;
      render();
      commitAutosave();
      return;
    }

    if (state.phase === 'preflop') {
      revealCommunityCards(3);
      state.phase = 'flop';
    } else if (state.phase === 'flop') {
      revealCommunityCards(1);
      state.phase = 'turn';
    } else if (state.phase === 'turn') {
      revealCommunityCards(1);
      state.phase = 'river';
    } else if (state.phase === 'river') {
      goToShowdown();
      return;
    }

    resetBetsForNextRound();
    executeAiRound({ allowRaise: true });
    state.awaitingPlayer = true;
    const phaseLabel = translate(`index.sections.holdem.phase.${state.phase}`, state.phase);
    updateStatus('index.sections.holdem.status.phasePrompt', `Phase ${phaseLabel} — à vous de jouer.`, {
      phase: phaseLabel
    });
    render();
  }

  function distributePot(winners) {
    if (!Array.isArray(winners) || !winners.length) {
      state.pot = 0;
      return;
    }
    const share = Math.floor(state.pot / winners.length);
    let remainder = state.pot % winners.length;
    for (let i = 0; i < winners.length; i += 1) {
      const player = winners[i];
      const bonus = remainder > 0 ? 1 : 0;
      player.stack += share + bonus;
      if (remainder > 0) {
        remainder -= 1;
      }
    }
    state.pot = 0;
  }

  function goToShowdown() {
    const contenders = state.players.filter(player => !player.folded);
    const results = contenders.map(player => ({
      player,
      best: evaluateBestHand([...player.cards, ...state.communityCards])
    }));
    results.forEach(result => {
      setPlayerStatus(result.player, 'index.sections.holdem.playerStatus.revealed', {
        hand: describeHand(result.best)
      });
    });
    const bestScore = results.reduce((max, current) => Math.max(max, current.best.score), 0);
    const winningResults = results.filter(result => result.best.score === bestScore);
    const winners = winningResults.map(result => result.player);
    const winnerNames = winners.map(winner => winner.name).join(', ');
    const heroResult = results.find(result => result.player.id === 'hero');

    if (winners.length === 1 && winners[0].id === 'hero') {
      updateStatus('index.sections.holdem.status.playerShowdownWin', 'Vous gagnez {amount} avec {hand}.', {
        amount: formatAmount(state.pot),
        hand: describeHand(heroResult?.best)
      });
    } else if (winners.some(player => player.id === 'hero')) {
      updateStatus('index.sections.holdem.status.playerSplitPot', 'Partage du pot ({amount}) avec {names}.', {
        amount: formatAmount(state.pot),
        names: winnerNames,
        hand: describeHand(heroResult?.best)
      });
    } else {
      const primaryWinner = winners[0];
      const winnerResult = winningResults.find(result => result.player === primaryWinner);
      updateStatus('index.sections.holdem.status.playerShowdownLose', 'Défaite face à {winner} ({hand}).', {
        winner: primaryWinner.name,
        hand: describeHand(winnerResult?.best),
        amount: formatAmount(state.pot)
      });
    }

    distributePot(winners);
    state.phase = 'complete';
    state.awaitingPlayer = false;
    render();
    commitAutosave();
  }

  function refreshStacksForNewHand() {
    for (let i = 0; i < state.players.length; i += 1) {
      const player = state.players[i];
      if (player.stack <= 0) {
        player.stack = state.config.startingStack;
      }
    }
  }

  function dealInitialCards() {
    for (let round = 0; round < 2; round += 1) {
      for (let i = 0; i < state.players.length; i += 1) {
        const card = drawCard();
        if (card) {
          state.players[i].cards.push(card);
        }
      }
    }
  }

  function startNewHand() {
    ensurePlayers();
    refreshStacksForNewHand();
    resetTableState();
    dealInitialCards();
    render();

    postBlinds();
    executeAiRound({ allowRaise: true });
    state.awaitingPlayer = true;

    const small = formatAmount(state.config.blinds.small);
    const big = formatAmount(state.config.blinds.big);
    updateStatus('index.sections.holdem.status.newHand', 'Nouvelle donne — blinds {small}/{big}.', {
      small,
      big
    });
    if (elements.raiseAmount) {
      elements.raiseAmount.value = '';
    }
    render();
    commitAutosave();
  }

  function handlePlayerFold() {
    const hero = state.players[0];
    if (!state.awaitingPlayer || !hero || hero.folded) {
      return;
    }
    hero.folded = true;
    setPlayerStatus(hero, 'index.sections.holdem.playerStatus.folded');
    const winners = state.players.slice(1).filter(player => !player.folded);
    if (!winners.length) {
      winners.push(hero);
    }
    const potAmount = state.pot;
    distributePot(winners);
    const primaryWinner = winners[0];
    if (primaryWinner.id === 'hero') {
      updateStatus('index.sections.holdem.status.playerWins', 'Vous remportez le pot ({amount}).', {
        amount: formatAmount(potAmount)
      });
    } else {
      updateStatus('index.sections.holdem.status.playerFold', `${primaryWinner.name} remporte le pot ({amount}).`, {
        winner: primaryWinner.name,
        amount: formatAmount(potAmount)
      });
    }
    state.phase = 'complete';
    state.awaitingPlayer = false;
    render();
    commitAutosave();
  }

  function handlePlayerCheckOrCall() {
    const hero = state.players[0];
    if (!state.awaitingPlayer || !hero || hero.folded || hero.allIn) {
      return;
    }
    const requiredToCall = Math.max(0, state.currentBet - hero.bet);
    if (requiredToCall > hero.stack) {
      updateStatus('index.sections.holdem.status.insufficientStack', 'Pas assez de crédits pour suivre ({amount}).', {
        amount: formatAmount(requiredToCall)
      });
      return;
    }
    applyBet(hero, hero.bet + requiredToCall);
    const actionKey = requiredToCall > 0
      ? 'index.sections.holdem.playerStatus.call'
      : 'index.sections.holdem.playerStatus.check';
    setPlayerStatus(hero, actionKey, { amount: formatAmount(hero.bet) });

    executeAiResponsesAfterPlayerAction();
    if (resolveIfOnlyOne()) {
      state.phase = 'complete';
      state.awaitingPlayer = false;
      render();
      commitAutosave();
      return;
    }

    resetBetsForNextRound();
    render();
    schedulePhaseAdvance(state.config.dealerSpeedMs);
  }

  function handlePlayerRaise() {
    const hero = state.players[0];
    if (!state.awaitingPlayer || !hero || hero.folded || hero.allIn) {
      return;
    }
    const rawValue = elements.raiseAmount ? Number(elements.raiseAmount.value) : NaN;
    if (!Number.isFinite(rawValue)) {
      updateStatus('index.sections.holdem.status.invalidRaise', 'Entrez un montant valide.');
      return;
    }
    const desired = Math.floor(rawValue);
    const minimum = Math.max(state.currentBet + state.config.minRaise, hero.bet + state.config.minRaise);
    const target = Math.max(desired, minimum);
    if (target <= hero.bet) {
      updateStatus('index.sections.holdem.status.raiseTooSmall', 'Relance insuffisante (minimum {amount}).', {
        amount: formatAmount(minimum)
      });
      return;
    }
    const needed = target - hero.bet;
    if (needed > hero.stack) {
      updateStatus('index.sections.holdem.status.insufficientStack', 'Pas assez de crédits pour suivre ({amount}).', {
        amount: formatAmount(needed)
      });
      return;
    }

    applyBet(hero, target);
    state.currentBet = Math.max(state.currentBet, hero.bet);
    setPlayerStatus(hero, 'index.sections.holdem.playerStatus.raise', { amount: formatAmount(hero.bet) });

    executeAiResponsesAfterPlayerAction();
    if (resolveIfOnlyOne()) {
      state.phase = 'complete';
      state.awaitingPlayer = false;
      render();
      commitAutosave();
      return;
    }

    resetBetsForNextRound();
    render();
    schedulePhaseAdvance(state.config.dealerSpeedMs);
  }

  function init() {
    loadAutosave();
    ensurePlayers();
    render();
    updateStatus('index.sections.holdem.status.intro', 'Lancez une nouvelle donne pour jouer.');

    if (elements.newHand) {
      elements.newHand.addEventListener('click', () => {
        startNewHand();
      });
    }
    if (elements.fold) {
      elements.fold.addEventListener('click', handlePlayerFold);
    }
    if (elements.checkCall) {
      elements.checkCall.addEventListener('click', handlePlayerCheckOrCall);
    }
    if (elements.raise) {
      elements.raise.addEventListener('click', handlePlayerRaise);
    }
  }

  onReady(init);
})();
