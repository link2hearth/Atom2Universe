(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const AUTOSAVE_ID = 'holdem';

  const DEFAULT_RAISE_GUIDANCE = Object.freeze({
    potRatio: 0.25,
    stackRatio: 0.12,
    openRaiseMultiplier: 2
  });

  const DEFAULT_STAKE_GROWTH = Object.freeze({
    usageFactor: 1.1,
    cap: 99e68
  });

  const DEFAULT_CONFIG = Object.freeze({
    blind: 10,
    dealerSpeedMs: 650,
    minRaise: 10,
    startingStack: 600,
    aiStack: 1e11,
    aiThinkingDelayMs: { min: 900, max: 2500 },
    opponentCount: { min: 4, max: 4 },
    aiProfiles: {
      patient: { aggression: 0.5, caution: 0.45, bluff: 0.15 },
      daring: { aggression: 0.72, caution: 0.33, bluff: 0.28 }
    },
    defaultAiProfile: 'patient',
    raiseGuidance: DEFAULT_RAISE_GUIDANCE,
    growth: DEFAULT_STAKE_GROWTH
  });

  const DEFAULT_AI_PROFILE = Object.freeze({ aggression: 0.55, caution: 0.4, bluff: 0.2 });

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

  const AI_NAME_POOL = Object.freeze(['Jack', 'Morgan', 'Alex', 'Riley', 'Quinn']);
  const AI_SEAT_NAMES = Object.freeze(['Peter', 'Wendy', 'Zelda', 'Link']);
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

  function getGrowthConfig() {
    return state.config && state.config.growth ? state.config.growth : DEFAULT_STAKE_GROWTH;
  }

  function getStakeCap() {
    const growth = getGrowthConfig();
    const capCandidate = growth && Number(growth.cap);
    if (Number.isFinite(capCandidate) && capCandidate > 0) {
      return capCandidate;
    }
    return DEFAULT_STAKE_GROWTH.cap;
  }

  function clampStakeValue(value) {
    const cap = getStakeCap();
    if (!Number.isFinite(value)) {
      return cap;
    }
    const sanitized = Math.max(0, Math.ceil(value - 1e-9));
    return Math.min(cap, sanitized);
  }

  function setStakeValue(value) {
    const baseline = Number.isFinite(value) ? value : CONFIG.blind;
    const clamped = clampStakeValue(Math.max(1, baseline));
    state.blind = clamped;
    state.minRaise = clamped;
    state.lastRaiseAmount = clamped;
  }

  function syncMinRaiseBaseline() {
    if (!Number.isFinite(state.blind) || state.blind <= 0) {
      setStakeValue(CONFIG.blind || DEFAULT_CONFIG.blind);
      return;
    }
    setStakeValue(state.blind);
  }

  function applyStakeGrowth() {
    const growth = getGrowthConfig();
    const factorCandidate = Number(growth && growth.usageFactor);
    const factor = Number.isFinite(factorCandidate) && factorCandidate > 1
      ? factorCandidate
      : DEFAULT_STAKE_GROWTH.usageFactor;
    if (!(factor > 1)) {
      syncMinRaiseBaseline();
      return;
    }
    setStakeValue(state.blind * factor);
  }

  function registerRaise(raiseDiff) {
    const sanitizedDiff = clampStakeValue(raiseDiff);
    if (sanitizedDiff > state.blind) {
      setStakeValue(sanitizedDiff);
    } else {
      syncMinRaiseBaseline();
    }
    applyStakeGrowth();
  }

  function formatTemplateValue(value) {
    if (value == null) {
      return '';
    }
    if (Array.isArray(value)) {
      return value
        .map(item => (item == null ? '' : String(item)))
        .filter(item => item.length > 0)
        .join(', ');
    }
    if (typeof value === 'object') {
      if (typeof value.toString === 'function' && value.toString !== Object.prototype.toString) {
        return value.toString();
      }
      try {
        return JSON.stringify(value);
      } catch (error) {
        return '';
      }
    }
    return String(value);
  }

  function applyParams(template, params) {
    if (typeof template !== 'string' || !template) {
      return template;
    }
    if (!params || typeof params !== 'object') {
      return template;
    }
    return template.replace(/\{([^{}]+)\}/g, (match, rawKey) => {
      const key = rawKey.trim();
      if (!Object.prototype.hasOwnProperty.call(params, key)) {
        return match;
      }
      const formatted = formatTemplateValue(params[key]);
      return formatted;
    });
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return applyParams(typeof fallback === 'string' ? fallback : key, params);
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
            return applyParams(trimmed, params);
          }
        }
      } catch (error) {
        console.warn('Holdem translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return applyParams(fallback, params);
    }
    return applyParams(key, params);
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

  function normalizeRaiseGuidance(rawGuidance) {
    const source = rawGuidance && typeof rawGuidance === 'object' ? rawGuidance : {};
    const defaults = DEFAULT_CONFIG.raiseGuidance || DEFAULT_RAISE_GUIDANCE;
    const potRatio = clamp(Number(source.potRatio) || defaults.potRatio || 0, 0, 1);
    const stackRatio = clamp(Number(source.stackRatio) || defaults.stackRatio || 0, 0, 1);
    const openRaiseMultiplier = Math.max(1, Number(source.openRaiseMultiplier) || defaults.openRaiseMultiplier || 1);
    return { potRatio, stackRatio, openRaiseMultiplier };
  }

  function getHoldemConfig() {
    const raw =
      GLOBAL_CONFIG
      && GLOBAL_CONFIG.arcade
      && GLOBAL_CONFIG.arcade.holdem
        ? GLOBAL_CONFIG.arcade.holdem
        : null;

    const growthDefaults = DEFAULT_CONFIG.growth || DEFAULT_STAKE_GROWTH;
    const growthSource = raw && raw.growth ? raw.growth : growthDefaults;
    const usageFactorCandidate = Number(growthSource.usageFactor);
    const usageFactor = usageFactorCandidate > 1 ? usageFactorCandidate : growthDefaults.usageFactor;
    const capCandidate = Number(growthSource.cap);
    const stakeCap = capCandidate > 0 ? capCandidate : growthDefaults.cap;

    const blindCandidate = raw && raw.blind != null ? raw.blind : DEFAULT_CONFIG.blind;
    const blindRaw = toPositiveInteger(blindCandidate, DEFAULT_CONFIG.blind);
    const minRaiseRaw = toPositiveInteger(raw && raw.minRaise, DEFAULT_CONFIG.minRaise);
    const startingStackRaw = toPositiveInteger(raw && raw.startingStack, DEFAULT_CONFIG.startingStack);
    const aiStackRaw = toPositiveInteger(raw && raw.aiStack, DEFAULT_CONFIG.aiStack);
    const raiseGuidance = normalizeRaiseGuidance(raw && raw.raiseGuidance);

    const blind = Math.max(1, Math.min(stakeCap, blindRaw));
    const normalizedMinRaise = Math.min(stakeCap, Math.max(minRaiseRaw, blind));
    const startingStack = Math.min(stakeCap, startingStackRaw);
    const aiStack = Math.min(stakeCap, aiStackRaw);

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

    let thinkingMin = DEFAULT_CONFIG.aiThinkingDelayMs.min;
    let thinkingMax = DEFAULT_CONFIG.aiThinkingDelayMs.max;
    if (raw && raw.aiThinkingDelayMs && typeof raw.aiThinkingDelayMs === 'object') {
      thinkingMin = toPositiveInteger(raw.aiThinkingDelayMs.min, thinkingMin);
      thinkingMax = toPositiveInteger(raw.aiThinkingDelayMs.max, thinkingMax);
    }
    if (thinkingMax < thinkingMin) {
      thinkingMax = thinkingMin;
    }

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
      blind,
      dealerSpeedMs,
      minRaise: normalizedMinRaise,
      startingStack,
      aiStack,
      raiseGuidance,
      growth: { usageFactor, cap: stakeCap },
      aiThinkingDelayMs: { min: thinkingMin, max: thinkingMax },
      opponentCount: { min: clamp(opponentMin, 2, 7), max: clamp(opponentMax, 2, 7) },
      aiProfiles,
      defaultAiProfile: defaultProfileId
    };
  }

  const CONFIG = getHoldemConfig();
  const BASE_AI_STACK = Number.isFinite(CONFIG.aiStack)
    ? CONFIG.aiStack
    : Number.isFinite(DEFAULT_CONFIG.aiStack)
      ? DEFAULT_CONFIG.aiStack
      : 0;

  const elements = {
    status: document.getElementById('holdemStatus'),
    community: document.getElementById('holdemCommunityCards'),
    opponentsLeft: document.getElementById('holdemPlayersLeft'),
    opponentsRight: document.getElementById('holdemPlayersRight'),
    potValue: document.getElementById('holdemPotValue'),
    playerCards: document.getElementById('holdemPlayerCards'),
    playerStatus: document.getElementById('holdemPlayerStatus'),
    playerBet: document.getElementById('holdemPlayerBet'),
    playerPanel: document.getElementById('holdemPlayerPanel'),
    newHand: document.getElementById('holdemNewHand'),
    fold: document.getElementById('holdemFold'),
    checkCall: document.getElementById('holdemCheckCall'),
    raise: document.getElementById('holdemRaise'),
    allIn: document.getElementById('holdemAllIn')
  };

  const state = {
    config: CONFIG,
    baseAiStack: BASE_AI_STACK,
    blind: CONFIG.blind,
    minRaise: Math.max(CONFIG.minRaise, CONFIG.blind),
    deck: [],
    communityCards: [],
    players: [],
    pot: 0,
    phase: 'idle',
    currentBet: 0,
    awaitingPlayer: false,
    dealerPosition: 0,
    heroBank: null,
    activePlayerId: null,
    lastRaiseAmount: Math.max(CONFIG.minRaise, CONFIG.blind),
    statusKey: 'index.sections.holdem.status.intro',
    statusParams: {},
    pendingTimeouts: [],
    handCount: 0
  };

  syncMinRaiseBaseline();

  function getGameState() {
    if (typeof window === 'undefined') {
      return null;
    }
    const gameState = window.atom2universGameState;
    if (gameState && typeof gameState === 'object') {
      return gameState;
    }
    return null;
  }

  function toLayeredNumber(value) {
    if (typeof LayeredNumber !== 'function') {
      return null;
    }
    try {
      return new LayeredNumber(value);
    } catch (error) {
      return null;
    }
  }

  function getHeroAtomSnapshot() {
    const fallbackNumeric = state.config.startingStack;
    const fallbackLayered = toLayeredNumber(fallbackNumeric);
    const gameState = getGameState();
    if (!gameState) {
      return { numeric: fallbackNumeric, layered: fallbackLayered };
    }
    let layered = null;
    const atoms = gameState.atoms;
    if (atoms instanceof LayeredNumber) {
      layered = atoms.clone();
    } else {
      layered = toLayeredNumber(atoms);
    }
    if (!layered) {
      return { numeric: fallbackNumeric, layered: fallbackLayered };
    }
    const approx = layered.toNumber();
    const numeric = Number.isFinite(approx)
      ? Math.max(0, Math.min(Math.floor(approx), Number.MAX_SAFE_INTEGER))
      : Number.MAX_SAFE_INTEGER;
    return { numeric, layered };
  }

  function syncHeroStackWithGameState() {
    const hero = state.players[0];
    if (!hero) {
      return;
    }
    const snapshot = getHeroAtomSnapshot();
    hero.stack = sanitizeStack(snapshot.numeric, hero.stack);
    hero.bet = 0;
    hero.folded = false;
    hero.allIn = false;
    state.heroBank = {
      numeric: hero.stack,
      layered: snapshot.layered ? snapshot.layered.clone() : null
    };
  }

  function commitHeroStackChange(newStack) {
    if (!state.players.length) {
      return;
    }
    const sanitized = sanitizeStack(newStack, 0);
    if (!state.heroBank) {
      state.heroBank = { numeric: sanitized, layered: null };
    }
    const previous = Number.isFinite(state.heroBank.numeric) ? state.heroBank.numeric : 0;
    if (sanitized === previous) {
      return;
    }
    state.heroBank.numeric = sanitized;
    const gameState = getGameState();
    if (!gameState) {
      return;
    }
    if (typeof LayeredNumber !== 'function') {
      gameState.atoms = sanitized;
      if (typeof window !== 'undefined' && typeof window.atom2universSaveGame === 'function') {
        window.atom2universSaveGame();
      }
      return;
    }
    let base = state.heroBank.layered;
    if (!(base instanceof LayeredNumber)) {
      base = toLayeredNumber(previous);
      if (!(base instanceof LayeredNumber)) {
        base = new LayeredNumber(previous);
      }
    }
    let delta;
    try {
      delta = new LayeredNumber(sanitized - previous);
    } catch (error) {
      delta = toLayeredNumber(sanitized - previous) || LayeredNumber.zero();
    }
    state.heroBank.layered = base.add(delta);
    gameState.atoms = state.heroBank.layered.clone();
    if (typeof window !== 'undefined' && typeof window.atom2universSaveGame === 'function') {
      window.atom2universSaveGame();
    }
  }

  function getAiDelay() {
    const delayConfig = state.config.aiThinkingDelayMs || DEFAULT_CONFIG.aiThinkingDelayMs;
    const min = clamp(Number(delayConfig.min) || DEFAULT_CONFIG.aiThinkingDelayMs.min, 300, 3000);
    const maxCandidate = Number(delayConfig.max);
    let max = Number.isFinite(maxCandidate)
      ? clamp(maxCandidate, min, 3000)
      : clamp(DEFAULT_CONFIG.aiThinkingDelayMs.max, min, 3000);
    const span = Math.max(0, max - min);
    return Math.floor(min + Math.random() * (span || 1));
  }

  function computeRaisePlan(player) {
    if (!player || player.folded || player.allIn) {
      const fallbackBet = player ? player.bet : 0;
      return {
        canRaise: false,
        target: fallbackBet,
        callTarget: fallbackBet,
        requiredToCall: 0
      };
    }
    const stakeUnit = Math.max(1, Math.floor(state.blind));
    const currentBet = Math.max(0, player.bet);
    const stack = Math.max(0, player.stack);
    const requiredToCall = Math.max(0, state.currentBet - currentBet);
    const available = currentBet + stack;
    const callTarget = currentBet + Math.min(stack, requiredToCall);

    if (available <= currentBet) {
      return {
        canRaise: false,
        target: currentBet,
        callTarget,
        requiredToCall
      };
    }

    if (requiredToCall > 0) {
      const desired = currentBet + requiredToCall + stakeUnit;
      if (available >= desired) {
        const target = Math.min(available, desired);
        return {
          canRaise: true,
          target,
          callTarget,
          requiredToCall
        };
      }
      return {
        canRaise: false,
        target: callTarget,
        callTarget,
        requiredToCall
      };
    }

    const baseline = Math.max(state.currentBet, currentBet);
    const desired = baseline + stakeUnit;
    if (available >= desired) {
      const target = Math.min(available, desired);
      return {
        canRaise: true,
        target,
        callTarget,
        requiredToCall
      };
    }
    return {
      canRaise: false,
      target: currentBet,
      callTarget,
      requiredToCall
    };
  }

  function getPlayerCount() {
    return state.players.length;
  }

  function normalizeSeatIndex(index) {
    const count = getPlayerCount();
    if (!count) {
      return -1;
    }
    const normalized = ((index % count) + count) % count;
    return normalized;
  }

  function getDealerIndex() {
    return normalizeSeatIndex(state.dealerPosition);
  }

  function getSmallBlindIndex() {
    const count = getPlayerCount();
    if (!count) {
      return -1;
    }
    if (count === 1) {
      return getDealerIndex();
    }
    return normalizeSeatIndex(getDealerIndex() + 1);
  }

  function getBigBlindIndex() {
    return getSmallBlindIndex();
  }

  function getBettingOrder(options = {}) {
    const includeHero = options.includeHero !== false;
    const count = getPlayerCount();
    if (!count) {
      return [];
    }
    const dealerIndex = getDealerIndex();
    if (dealerIndex < 0) {
      return [];
    }
    const startIndex = state.phase === 'preflop'
      ? normalizeSeatIndex(getBigBlindIndex() + 1)
      : normalizeSeatIndex(dealerIndex + 1);
    const order = [];
    for (let offset = 0; offset < count; offset += 1) {
      const seatIndex = normalizeSeatIndex(startIndex + offset);
      const player = state.players[seatIndex];
      if (!player) {
        continue;
      }
      if (player.folded || player.allIn) {
        continue;
      }
      if (!includeHero && player.id === 'hero') {
        continue;
      }
      order.push(player);
    }
    return order;
  }

  function getResponseOrder() {
    const order = getBettingOrder({ includeHero: true });
    if (!order.length) {
      return [];
    }
    const heroIndex = order.findIndex(player => player.id === 'hero');
    if (heroIndex === -1) {
      return order.filter(player => player.id !== 'hero');
    }
    const result = [];
    for (let step = 1; step < order.length; step += 1) {
      const player = order[(heroIndex + step) % order.length];
      if (player && player.id !== 'hero') {
        result.push(player);
      }
    }
    return result;
  }
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
    if (typeof formatLayeredLocalized === 'function') {
      let layeredValue = null;
      if (value instanceof LayeredNumber) {
        layeredValue = value;
      } else if (typeof LayeredNumber === 'function') {
        layeredValue = toLayeredNumber(value);
      }
      if (layeredValue) {
        const formatted = formatLayeredLocalized(layeredValue, {
          mantissaDigits: 2,
          numberFormatOptions: {
            maximumFractionDigits: 0,
            minimumFractionDigits: 0
          }
        });
        if (formatted) {
          return formatted;
        }
      }
    }
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    const rounded = Math.max(0, Math.round(numeric));
    try {
      return rounded.toLocaleString();
    } catch (error) {
      return String(rounded);
    }
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

  function getHero() {
    return state.players.length ? state.players[0] : null;
  }

  function heroIsActiveForDecision() {
    const hero = getHero();
    if (!hero) {
      return false;
    }
    if (hero.folded || hero.allIn) {
      return false;
    }
    return hero.stack > 0;
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

  function resolveAiName(index, usedNames, rawName) {
    const candidates = [];

    function pushCandidate(name) {
      const sanitized = sanitizeName(name, null);
      if (!sanitized) {
        return;
      }
      if (!candidates.includes(sanitized)) {
        candidates.push(sanitized);
      }
    }

    pushCandidate(AI_SEAT_NAMES[index]);
    if (typeof rawName !== 'undefined') {
      pushCandidate(rawName);
    }
    for (let i = 0; i < AI_SEAT_NAMES.length; i += 1) {
      pushCandidate(AI_SEAT_NAMES[i]);
    }
    for (let i = 0; i < AI_NAME_POOL.length; i += 1) {
      pushCandidate(AI_NAME_POOL[i]);
    }

    for (let i = 0; i < candidates.length; i += 1) {
      const candidate = candidates[i];
      if (!usedNames.has(candidate)) {
        usedNames.add(candidate);
        return candidate;
      }
    }

    const fallback = `IA ${usedNames.size + 1}`;
    usedNames.add(fallback);
    return fallback;
  }

  function enforceBaseAiStack() {
    const defaultStack = Number.isFinite(DEFAULT_CONFIG.aiStack) && DEFAULT_CONFIG.aiStack > 0
      ? DEFAULT_CONFIG.aiStack
      : 0;
    const baseline = Number.isFinite(state.baseAiStack) && state.baseAiStack > 0
      ? state.baseAiStack
      : defaultStack;
    const current = Number.isFinite(state.config && state.config.aiStack)
      ? state.config.aiStack
      : baseline;
    if (!Number.isFinite(state.config.aiStack) || state.config.aiStack < baseline) {
      state.config.aiStack = baseline;
    }
    return Math.max(baseline, current, defaultStack);
  }

  function createAiPlayer(index, options = {}, context = {}) {
    const usedNames = context && context.usedNames instanceof Set
      ? context.usedNames
      : new Set(state.players.map(player => player.name));
    const profileId = options.profileId && state.config.aiProfiles[options.profileId]
      ? options.profileId
      : state.config.defaultAiProfile;
    const name = resolveAiName(index, usedNames, options.name);
    const fallbackStack = enforceBaseAiStack();
    return {
      id: `ai-${index}`,
      type: 'ai',
      name,
      stack: sanitizeStack(options.stack, fallbackStack),
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

    enforceBaseAiStack();

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
      const name = resolveAiName(aiPlayers.length, usedNames, entry.name);
      aiPlayers.push({
        id: `ai-${i}`,
        type: 'ai',
        name,
        stack: sanitizeStack(entry.stack, enforceBaseAiStack()),
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
      const player = createAiPlayer(aiPlayers.length, {}, { usedNames });
      aiPlayers.push(player);
    }

    state.players = [hero, ...aiPlayers];
    syncHeroStackWithGameState();
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
    const baseline = enforceBaseAiStack();
    const count = clamp(state.config.opponentCount.min, 2, state.config.opponentCount.max);
    const players = [hero];
    const usedNames = new Set([hero.name]);
    state.players = players;
    for (let i = 0; i < count; i += 1) {
      players.push(createAiPlayer(i, { stack: baseline }, { usedNames }));
    }
    syncHeroStackWithGameState();
  }
  function clearPendingTimeouts() {
    while (state.pendingTimeouts.length) {
      clearTimeout(state.pendingTimeouts.pop());
    }
    state.activePlayerId = null;
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
    state.activePlayerId = null;
    state.lastRaiseAmount = state.minRaise;
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
    if (elements.playerBet) {
      elements.playerBet.textContent = formatAmount(hero ? hero.bet : 0);
    }
    if (elements.playerStatus) {
      const statusKey = hero && hero.statusKey
        ? hero.statusKey
        : 'index.sections.holdem.playerStatus.waiting';
      elements.playerStatus.dataset.i18n = statusKey;
      elements.playerStatus.textContent = translate(
        statusKey,
        getPlayerStatusFallback(statusKey),
        hero ? hero.statusParams : null
      );
    }
    if (elements.playerPanel) {
      const activeHand = state.phase !== 'idle' && state.phase !== 'complete' && state.phase !== 'showdown';
      const highlight = activeHand && state.awaitingPlayer && hero && !hero.folded && !hero.allIn;
      if (highlight) {
        elements.playerPanel.classList.add('holdem-hand--active');
      } else {
        elements.playerPanel.classList.remove('holdem-hand--active');
      }
    }
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
    const leftColumn = elements.opponentsLeft;
    const rightColumn = elements.opponentsRight;
    if (!leftColumn || !rightColumn) {
      return;
    }
    leftColumn.innerHTML = '';
    rightColumn.innerHTML = '';
    const opponents = state.players.slice(1);
    if (!opponents.length) {
      return;
    }
    const splitIndex = Math.ceil(opponents.length / 2);
    const leftFragment = document.createDocumentFragment();
    const rightFragment = document.createDocumentFragment();
    for (let i = 0; i < opponents.length; i += 1) {
      const player = opponents[i];
      const article = document.createElement('article');
      article.className = 'holdem-player';
      if (player.folded) {
        article.classList.add('holdem-player--folded');
      }
      if (player.allIn) {
        article.classList.add('holdem-player--all-in');
      }
      if (state.activePlayerId === player.id) {
        article.classList.add('holdem-player--active');
      }
      article.setAttribute('role', 'listitem');

      const header = document.createElement('div');
      header.className = 'holdem-player__header';

      const name = document.createElement('h4');
      name.className = 'holdem-player__name';
      name.textContent = player.name;
      header.appendChild(name);

      const meta = document.createElement('div');
      meta.className = 'holdem-player__meta';

      const stack = document.createElement('span');
      stack.className = 'holdem-player__stack';
      const label = document.createElement('span');
      label.className = 'holdem-player__stack-label';
      label.dataset.i18n = 'index.sections.holdem.labels.stack';
      label.textContent = translate('index.sections.holdem.labels.stack', 'Atom reserve');
      const value = document.createElement('span');
      value.className = 'holdem-player__stack-value';
      value.textContent = formatAmount(player.stack);
      stack.appendChild(label);
      stack.appendChild(value);
      meta.appendChild(stack);

      const status = document.createElement('span');
      status.className = 'holdem-player__status';
      const statusKey = player.statusKey || 'index.sections.holdem.playerStatus.waiting';
      status.dataset.i18n = statusKey;
      status.textContent = translate(statusKey, getPlayerStatusFallback(statusKey), player.statusParams);
      meta.appendChild(status);

      header.appendChild(meta);
      article.appendChild(header);

      const cardList = document.createElement('ul');
      cardList.className = 'holdem-cards holdem-cards--opponent';
      const reveal = state.phase === 'showdown' || state.phase === 'complete';
      renderCardList(cardList, player.cards, { hidden: !reveal });
      article.appendChild(cardList);

      const bet = document.createElement('p');
      bet.className = 'holdem-player__bet';
      const betLabel = document.createElement('span');
      betLabel.className = 'holdem-player__stack-label';
      betLabel.dataset.i18n = 'index.sections.holdem.labels.currentBet';
      betLabel.textContent = translate('index.sections.holdem.labels.currentBet', 'Current bet');
      const betValue = document.createElement('span');
      betValue.className = 'holdem-player__bet-value';
      betValue.textContent = formatAmount(player.bet);
      bet.appendChild(betLabel);
      bet.appendChild(betValue);
      article.appendChild(bet);

      if (i < splitIndex) {
        leftFragment.appendChild(article);
      } else {
        rightFragment.appendChild(article);
      }
    }
    leftColumn.appendChild(leftFragment);
    rightColumn.appendChild(rightFragment);
  }

  function renderControls() {
    if (!elements.checkCall || !elements.fold || !elements.raise || !elements.allIn) {
      return;
    }
    const hero = state.players[0];
    const activeHand = state.phase !== 'idle' && state.phase !== 'complete' && state.phase !== 'showdown';
    const canAct = activeHand && state.awaitingPlayer && hero && !hero.folded && !hero.allIn;
    elements.fold.disabled = !canAct;
    elements.checkCall.disabled = !canAct;

    const allInAmount = hero ? hero.bet + hero.stack : 0;
    const allInAmountLabel = formatAmount(allInAmount);

    if (!hero) {
      if (elements.raise) {
        elements.raise.disabled = true;
        elements.raise.dataset.i18n = 'index.sections.holdem.controls.raise';
        elements.raise.textContent = translate('index.sections.holdem.controls.raise', 'Raise');
      }
      elements.allIn.disabled = true;
      elements.allIn.dataset.i18n = 'index.sections.holdem.controls.allIn';
      elements.allIn.textContent = translate('index.sections.holdem.controls.allIn', 'All-in', {
        amount: allInAmountLabel
      });
      return;
    }

    const raisePlan = computeRaisePlan(hero);
    const nextRaise = raisePlan ? Math.floor(raisePlan.target) : hero.bet;
    const canRaise = canAct && raisePlan && raisePlan.canRaise && nextRaise > hero.bet;
    if (elements.raise) {
      elements.raise.disabled = !canRaise;
      if (canRaise) {
        const isAllIn = hero.bet + hero.stack <= nextRaise;
        const labelKey = isAllIn
          ? 'index.sections.holdem.controls.raiseAllIn'
          : 'index.sections.holdem.controls.raiseTo';
        const amountLabel = formatAmount(nextRaise);
        const fallback = isAllIn
          ? `All-in (${amountLabel})`
          : `Raise to ${amountLabel}`;
        elements.raise.dataset.i18n = labelKey;
        elements.raise.textContent = translate(labelKey, fallback, { amount: amountLabel });
      } else {
        elements.raise.dataset.i18n = 'index.sections.holdem.controls.raise';
        elements.raise.textContent = translate('index.sections.holdem.controls.raise', 'Raise');
      }
    }

    const canAllIn = canAct && hero.stack > 0;
    elements.allIn.disabled = !canAllIn;
    const allInFallback = `All-in (${allInAmountLabel})`;
    elements.allIn.dataset.i18n = 'index.sections.holdem.controls.allIn';
    elements.allIn.textContent = translate(
      'index.sections.holdem.controls.allIn',
      allInFallback,
      { amount: allInAmountLabel }
    );

    const requiredToCall = Math.max(0, state.currentBet - hero.bet);
    if (requiredToCall > 0) {
      const requiredLabel = formatAmount(requiredToCall);
      elements.checkCall.dataset.i18n = 'index.sections.holdem.controls.callWithAmount';
      elements.checkCall.textContent = translate(
        'index.sections.holdem.controls.callWithAmount',
        `Suivre (${requiredLabel})`,
        { amount: requiredLabel }
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
      return 0.15;
    }
    const [cardA, cardB] = cards;
    const highCardBonus = (cardA.value + cardB.value) / 26;
    const suitedBonus = cardA.suit === cardB.suit ? 0.12 : 0;
    const gap = Math.abs(cardA.value - cardB.value);
    let connectorBonus = 0;
    if (gap <= 1) {
      connectorBonus = 0.14;
    } else if (gap === 2) {
      connectorBonus = 0.07;
    }
    if (cardA.value === cardB.value) {
      const pairStrength = 0.68 + (cardA.value / 18);
      return clamp(pairStrength + suitedBonus, 0, 0.98);
    }
    const aceBonus = cardA.value === 14 || cardB.value === 14 ? 0.08 : 0;
    const kingBonus = cardA.value === 13 || cardB.value === 13 ? 0.04 : 0;
    const base = 0.28 + highCardBonus + suitedBonus + connectorBonus + aceBonus + kingBonus;
    return clamp(base, 0, 0.92);
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
    if (player.id === 'hero') {
      commitHeroStackChange(player.stack);
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
    state.lastRaiseAmount = state.minRaise;
  }

  function postBlinds() {
    const playerCount = state.players.length;
    if (!playerCount) {
      return;
    }
    const blindPlayer = state.players[getBigBlindIndex()];

    if (blindPlayer) {
      const amount = Math.min(state.blind, blindPlayer.stack + blindPlayer.bet);
      applyBet(blindPlayer, blindPlayer.bet + amount);
      setPlayerStatus(blindPlayer, 'index.sections.holdem.playerStatus.blind', {
        amount: formatAmount(amount)
      });
      state.currentBet = Math.max(state.currentBet, blindPlayer.bet);
    } else {
      state.currentBet = 0;
    }
    state.lastRaiseAmount = state.minRaise;
  }

  function decideAiRoundAction(player, allowRaise) {
    const plan = computeRaisePlan(player);
    const requiredToCall = plan.requiredToCall;
    const strength = estimateStrength(player);
    const profile = player.profile || state.config.aiProfiles[player.profileId] || DEFAULT_AI_PROFILE;
    const riskBoost = (1 - profile.caution) * 0.35;
    const aggressionFactor = profile.aggression + riskBoost;
    const bluffFactor = profile.bluff;

    if (requiredToCall > 0) {
      const bluffRoll = Math.random();
      const disciplinedFoldThreshold = 0.2 + (0.22 * (1 - profile.caution));
      if (strength + bluffFactor * 0.35 < disciplinedFoldThreshold && bluffRoll > bluffFactor * 0.55) {
        return { action: 'fold', target: player.bet };
      }

      if (allowRaise && plan.canRaise && (strength > 0.52 || bluffRoll < bluffFactor) && Math.random() < aggressionFactor) {
        return { action: 'raise', target: plan.target };
      }

      if (allowRaise && plan.canRaise && strength > 0.35 && Math.random() < bluffFactor * 0.6) {
        return { action: 'raise', target: plan.target };
      }

      const callTarget = Math.max(player.bet, plan.callTarget);
      return { action: requiredToCall ? 'call' : 'check', target: callTarget };
    }

    if (allowRaise && plan.canRaise && (strength > 0.45 || Math.random() < bluffFactor * 0.75) && Math.random() < aggressionFactor) {
      return { action: 'raise', target: plan.target };
    }

    if (allowRaise && plan.canRaise && Math.random() < bluffFactor * 0.35 && player.stack > 0) {
      return { action: 'raise', target: plan.target };
    }
    return { action: 'check', target: player.bet };
  }

  function decideAiResponseAction(player) {
    const plan = computeRaisePlan(player);
    const requiredToCall = plan.requiredToCall;
    if (requiredToCall <= 0) {
      return { action: 'check', target: player.bet };
    }
    const strength = estimateStrength(player);
    const profile = player.profile || state.config.aiProfiles[player.profileId] || DEFAULT_AI_PROFILE;
    const aggressionFactor = profile.aggression + (1 - profile.caution) * 0.3;
    const bluffRoll = Math.random();
    const reluctantThreshold = 0.18 + (0.3 * (1 - profile.caution));

    const potAfterCall = state.pot + requiredToCall;
    const normalizedPotOdds = clamp(potAfterCall > 0 ? requiredToCall / potAfterCall : 1, 0, 1);
    const normalizedPressure = clamp(requiredToCall / Math.max(1, player.stack + player.bet), 0, 1);
    const potReward = (1 - normalizedPotOdds) * (0.35 + (1 - profile.caution) * 0.2);
    const pressureTax = normalizedPressure * (0.25 + profile.caution * 0.2);
    const dynamicReluctance = clamp(reluctantThreshold + pressureTax - potReward, 0.05, 0.85);
    const confidentStrength = 0.42 + (1 - profile.caution) * 0.18;

    if (strength < confidentStrength && strength + profile.bluff * 0.4 < dynamicReluctance && bluffRoll > profile.bluff * 0.45) {
      return { action: 'fold', target: player.bet };
    }

    if (plan.canRaise && Math.random() < aggressionFactor * 0.55 && (strength > 0.55 || bluffRoll < profile.bluff)) {
      return { action: 'raise', target: plan.target };
    }

    const callTarget = Math.max(player.bet, plan.callTarget);
    return { action: 'call', target: callTarget };
  }

  function applyAiDecision(player, decision) {
    if (!decision || !player) {
      return { handResolved: false };
    }
    const action = decision.action || 'check';
    if (action === 'fold') {
      player.folded = true;
      setPlayerStatus(player, 'index.sections.holdem.playerStatus.folded');
      updateStatus(
        'index.sections.holdem.status.aiActionFold',
        `${player.name} folds.`,
        { name: player.name }
      );
      render();
      if (resolveIfOnlyOne()) {
        return { handResolved: true };
      }
      return { handResolved: false };
    }

    const previousBet = player.bet;
    const previousCurrent = state.currentBet;
    const target = Number.isFinite(decision.target) ? Math.max(player.bet, Math.floor(decision.target)) : player.bet;
    applyBet(player, target);
    if (player.bet > previousCurrent) {
      const raiseDiff = player.bet - previousCurrent;
      if (raiseDiff > 0) {
        registerRaise(raiseDiff);
      }
      state.currentBet = player.bet;
    } else {
      state.currentBet = Math.max(state.currentBet, player.bet);
    }

    let statusKey = 'index.sections.holdem.playerStatus.check';
    if (player.allIn) {
      statusKey = 'index.sections.holdem.playerStatus.allIn';
    } else if (action === 'raise') {
      statusKey = 'index.sections.holdem.playerStatus.raise';
    } else if (action === 'call') {
      statusKey = 'index.sections.holdem.playerStatus.call';
    }

    const amountLabel = formatAmount(player.bet);
    setPlayerStatus(player, statusKey, { amount: amountLabel });

    let announcementKey = 'index.sections.holdem.status.aiActionCheck';
    if (player.allIn || (action === 'raise' && player.stack === 0)) {
      announcementKey = 'index.sections.holdem.status.aiActionAllIn';
    } else if (action === 'raise') {
      announcementKey = 'index.sections.holdem.status.aiActionRaise';
    } else if (action === 'call') {
      announcementKey = 'index.sections.holdem.status.aiActionCall';
    }
    let fallbackStatus = `${player.name} checks.`;
    if (announcementKey === 'index.sections.holdem.status.aiActionCall') {
      fallbackStatus = `${player.name} calls ${amountLabel}.`;
    } else if (announcementKey === 'index.sections.holdem.status.aiActionRaise') {
      fallbackStatus = `${player.name} raises to ${amountLabel}.`;
    } else if (announcementKey === 'index.sections.holdem.status.aiActionAllIn') {
      fallbackStatus = `${player.name} goes all-in (${amountLabel}).`;
    }
    updateStatus(
      announcementKey,
      fallbackStatus,
      { name: player.name, amount: amountLabel }
    );
    render();

    if (resolveIfOnlyOne()) {
      return { handResolved: true };
    }
    return { handResolved: false };
  }

  function executeAiRound({ allowRaise = true, expectHeroAction, onComplete } = {}) {
    const order = getBettingOrder({ includeHero: true });
    const heroIndex = order.findIndex(player => player.id === 'hero');
    const heroEligible = heroIsActiveForDecision() && heroIndex !== -1;
    const queue = [];
    if (heroEligible) {
      for (let i = 0; i < heroIndex; i += 1) {
        const player = order[i];
        if (!player || player.id === 'hero') {
          continue;
        }
        queue.push(player);
      }
    } else {
      for (let i = 0; i < order.length; i += 1) {
        const player = order[i];
        if (!player || player.id === 'hero') {
          continue;
        }
        queue.push(player);
      }
    }

    const shouldPromptHero = typeof expectHeroAction === 'boolean'
      ? expectHeroAction && heroEligible
      : heroEligible;

    if (!queue.length) {
      if (state.phase !== 'complete') {
        state.awaitingPlayer = shouldPromptHero;
        if (shouldPromptHero) {
          const phaseLabel = translate(`index.sections.holdem.phase.${state.phase}`, state.phase);
          updateStatus(
            'index.sections.holdem.status.phasePrompt',
            `Phase ${phaseLabel} — à vous de jouer.`,
            { phase: phaseLabel }
          );
        } else {
          updateStatus(
            'index.sections.holdem.status.aiAutoProgress',
            'Les adversaires poursuivent la manche.'
          );
        }
      } else {
        state.awaitingPlayer = false;
      }
      render();
      commitAutosave();
      if (typeof onComplete === 'function') {
        onComplete(false);
      }
      return;
    }

    state.awaitingPlayer = false;
    let finished = false;

    const finish = handResolved => {
      if (finished) {
        return;
      }
      finished = true;
      state.activePlayerId = null;
      if (!handResolved && state.phase !== 'complete') {
        if (shouldPromptHero) {
          state.awaitingPlayer = true;
          const phaseLabel = translate(`index.sections.holdem.phase.${state.phase}`, state.phase);
          updateStatus(
            'index.sections.holdem.status.phasePrompt',
            `Phase ${phaseLabel} — à vous de jouer.`,
            { phase: phaseLabel }
          );
        } else {
          state.awaitingPlayer = false;
          updateStatus(
            'index.sections.holdem.status.aiAutoProgress',
            'Les adversaires poursuivent la manche.'
          );
        }
      } else {
        state.awaitingPlayer = false;
      }
      render();
      if (typeof onComplete === 'function') {
        onComplete(Boolean(handResolved));
      }
      commitAutosave();
    };

    let index = 0;
    const processNext = () => {
      if (finished) {
        return;
      }
      if (index >= queue.length) {
        finish(false);
        return;
      }
      const player = queue[index];
      index += 1;
      if (!player || player.folded || player.allIn) {
        processNext();
        return;
      }
      state.activePlayerId = player.id;
      updateStatus(
        'index.sections.holdem.status.aiThinking',
        `${player.name} is thinking…`,
        { name: player.name }
      );
      render();
      const timer = setTimeout(() => {
        const decision = decideAiRoundAction(player, allowRaise);
        const result = applyAiDecision(player, decision);
        if (result.handResolved) {
          finish(true);
          return;
        }
        processNext();
      }, getAiDelay());
      state.pendingTimeouts.push(timer);
    };

    processNext();
  }

  function executeAiResponsesAfterPlayerAction(onComplete) {
    const queue = [];
    const responseOrder = getResponseOrder();
    for (let i = 0; i < responseOrder.length; i += 1) {
      const player = responseOrder[i];
      if (!player || player.folded || player.allIn) {
        continue;
      }
      const requiredToCall = Math.max(0, state.currentBet - player.bet);
      if (requiredToCall <= 0) {
        continue;
      }
      queue.push(player);
    }

    if (!queue.length) {
      state.activePlayerId = null;
      if (typeof onComplete === 'function') {
        onComplete(false);
      }
      return;
    }

    state.awaitingPlayer = false;
    let finished = false;

    const finish = handResolved => {
      if (finished) {
        return;
      }
      finished = true;
      state.activePlayerId = null;
      render();
      if (typeof onComplete === 'function') {
        onComplete(Boolean(handResolved));
      }
      commitAutosave();
    };

    let index = 0;
    const processNext = () => {
      if (finished) {
        return;
      }
      if (index >= queue.length) {
        finish(false);
        return;
      }
      const player = queue[index];
      index += 1;
      if (!player || player.folded || player.allIn) {
        processNext();
        return;
      }
      state.activePlayerId = player.id;
      updateStatus(
        'index.sections.holdem.status.aiThinking',
        `${player.name} is thinking…`,
        { name: player.name }
      );
      render();
      const timer = setTimeout(() => {
        const decision = decideAiResponseAction(player);
        const result = applyAiDecision(player, decision);
        if (result.handResolved) {
          finish(true);
          return;
        }
        processNext();
      }, getAiDelay());
      state.pendingTimeouts.push(timer);
    };

    processNext();
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
    state.activePlayerId = null;
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
    render();
    executeAiRound({
      allowRaise: true,
      onComplete(handResolved) {
        if (handResolved || state.phase === 'complete') {
          return;
        }
        if (!heroIsActiveForDecision()) {
          resetBetsForNextRound();
          render();
          schedulePhaseAdvance(state.config.dealerSpeedMs);
        }
      }
    });
  }

  function continueHandWithoutHero() {
    if (state.phase === 'complete') {
      state.awaitingPlayer = false;
      render();
      commitAutosave();
      return;
    }

    const finishIfResolved = () => {
      if (resolveIfOnlyOne()) {
        state.phase = 'complete';
        state.awaitingPlayer = false;
        render();
        commitAutosave();
        return true;
      }
      return false;
    };

    if (finishIfResolved()) {
      return;
    }

    const proceed = () => {
      if (state.phase === 'complete') {
        state.awaitingPlayer = false;
        render();
        commitAutosave();
        return;
      }

      if (finishIfResolved()) {
        return;
      }

      if (state.phase === 'river') {
        goToShowdown();
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
      }

      resetBetsForNextRound();
      render();

      executeAiRound({
        allowRaise: true,
        expectHeroAction: false,
        onComplete(handResolved) {
          if (handResolved) {
            state.phase = 'complete';
            state.awaitingPlayer = false;
            render();
            commitAutosave();
            return;
          }
          resetBetsForNextRound();
          render();
          proceed();
        }
      });
    };

    proceed();
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
      if (player.id === 'hero') {
        commitHeroStackChange(player.stack);
      }
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
    const hero = getHero();
    const heroStack = hero ? sanitizeStack(hero.stack, state.config.startingStack) : enforceBaseAiStack();
    let respawnStack = enforceBaseAiStack();
    let aiCount = 0;
    let bustedAiCount = 0;

    for (let i = 0; i < state.players.length; i += 1) {
      const player = state.players[i];
      if (player.id === 'hero') {
        continue;
      }
      aiCount += 1;
      if (player.stack <= 0) {
        bustedAiCount += 1;
      }
    }

    if (aiCount > 0 && bustedAiCount === aiCount) {
      let computedStack = heroStack * 100;
      if (!Number.isFinite(computedStack) || computedStack <= 0) {
        computedStack = state.config.aiStack;
      } else if (computedStack > Number.MAX_SAFE_INTEGER) {
        computedStack = Number.MAX_SAFE_INTEGER;
      }
      respawnStack = sanitizeStack(computedStack, state.config.aiStack);
      if (respawnStack <= 0) {
        respawnStack = state.config.aiStack;
      }
      respawnStack = Math.max(enforceBaseAiStack(), respawnStack);
      state.config.aiStack = respawnStack;
    }

    for (let i = 0; i < state.players.length; i += 1) {
      const player = state.players[i];
      if (player.id === 'hero') {
        continue;
      }
      if (player.stack <= 0) {
        player.stack = respawnStack;
        player.folded = false;
        player.allIn = false;
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

  function advanceDealerPosition() {
    const playerCount = state.players.length;
    if (!playerCount) {
      return;
    }
    let nextPosition = (state.dealerPosition + 1) % playerCount;
    let checked = 0;
    while (checked < playerCount) {
      const candidate = state.players[nextPosition];
      if (candidate && candidate.stack > 0) {
        break;
      }
      nextPosition = (nextPosition + 1) % playerCount;
      checked += 1;
    }
    state.dealerPosition = nextPosition;
  }

  function startNewHand() {
    enforceBaseAiStack();
    ensurePlayers();
    if (!state.players.length) {
      return;
    }
    if (state.handCount > 0) {
      applyStakeGrowth();
    } else {
      syncMinRaiseBaseline();
    }
    advanceDealerPosition();
    syncHeroStackWithGameState();
    refreshStacksForNewHand();
    resetTableState();
    dealInitialCards();
    render();

    postBlinds();
    render();

    const blind = formatAmount(state.blind);
    updateStatus('index.sections.holdem.status.newHand', 'Nouvelle donne — blinde {blind} atomes.', {
      blind
    });
    executeAiRound({ allowRaise: true });
    state.handCount += 1;
  }

  function handlePlayerFold() {
    const hero = getHero();
    if (!state.awaitingPlayer || !hero || hero.folded) {
      return;
    }
    hero.folded = true;
    setPlayerStatus(hero, 'index.sections.holdem.playerStatus.folded');
    updateStatus(
      'index.sections.holdem.status.heroFolded',
      'Vous vous couchez. Les adversaires poursuivent la manche.'
    );
    render();
    state.awaitingPlayer = false;
    resetBetsForNextRound();
    render();
    continueHandWithoutHero();
  }

  function handlePlayerCheckOrCall() {
    const hero = state.players[0];
    if (!state.awaitingPlayer || !hero || hero.folded || hero.allIn) {
      return;
    }
    const requiredToCall = Math.max(0, state.currentBet - hero.bet);
    if (requiredToCall > hero.stack) {
      updateStatus('index.sections.holdem.status.insufficientStack', 'Pas assez d\'atomes pour suivre ({amount}).', {
        amount: formatAmount(requiredToCall)
      });
      return;
    }
    applyBet(hero, hero.bet + requiredToCall);
    const actionKey = requiredToCall > 0
      ? 'index.sections.holdem.playerStatus.call'
      : 'index.sections.holdem.playerStatus.check';
    setPlayerStatus(hero, actionKey, { amount: formatAmount(hero.bet) });
    render();

    state.awaitingPlayer = false;
    executeAiResponsesAfterPlayerAction(handResolved => {
      if (handResolved) {
        state.phase = 'complete';
        state.awaitingPlayer = false;
        render();
        return;
      }
      resetBetsForNextRound();
      render();
      schedulePhaseAdvance(state.config.dealerSpeedMs);
    });
  }

  function handlePlayerRaise() {
    const hero = state.players[0];
    if (!state.awaitingPlayer || !hero || hero.folded || hero.allIn) {
      return;
    }
    const plan = computeRaisePlan(hero);
    const target = plan && plan.canRaise ? Math.floor(plan.target) : hero.bet;
    if (!plan || !plan.canRaise || !Number.isFinite(target) || target <= hero.bet) {
      updateStatus('index.sections.holdem.status.raiseUnavailable', 'Impossible de relancer avec votre réserve d\'atomes actuelle.');
      return;
    }
    const needed = target - hero.bet;
    if (needed > hero.stack) {
      updateStatus('index.sections.holdem.status.insufficientStack', 'Pas assez d\'atomes pour suivre ({amount}).', {
        amount: formatAmount(needed)
      });
      return;
    }

    const previousCurrent = state.currentBet;
    applyBet(hero, target);
    if (hero.bet > previousCurrent) {
      const raiseDiff = hero.bet - previousCurrent;
      if (raiseDiff > 0) {
        registerRaise(raiseDiff);
      }
      state.currentBet = hero.bet;
    } else {
      state.currentBet = Math.max(state.currentBet, hero.bet);
    }

    if (hero.allIn || hero.stack === 0) {
      setPlayerStatus(hero, 'index.sections.holdem.playerStatus.allIn');
    } else {
      setPlayerStatus(hero, 'index.sections.holdem.playerStatus.raise', { amount: formatAmount(hero.bet) });
    }
    render();

    state.awaitingPlayer = false;
    executeAiResponsesAfterPlayerAction(handResolved => {
      if (handResolved) {
        state.phase = 'complete';
        state.awaitingPlayer = false;
        render();
        return;
      }
      resetBetsForNextRound();
      render();
      schedulePhaseAdvance(state.config.dealerSpeedMs);
    });
  }

  function handlePlayerAllIn() {
    const hero = state.players[0];
    if (!state.awaitingPlayer || !hero || hero.folded || hero.allIn || hero.stack <= 0) {
      return;
    }
    const target = hero.bet + hero.stack;
    if (target <= hero.bet) {
      return;
    }
    const previousCurrent = state.currentBet;
    applyBet(hero, target);
    if (hero.bet > previousCurrent) {
      const raiseDiff = hero.bet - previousCurrent;
      if (raiseDiff > 0) {
        registerRaise(raiseDiff);
      }
      state.currentBet = hero.bet;
    } else {
      state.currentBet = Math.max(state.currentBet, hero.bet);
    }

    setPlayerStatus(hero, 'index.sections.holdem.playerStatus.allIn');
    render();

    state.awaitingPlayer = false;
    executeAiResponsesAfterPlayerAction(handResolved => {
      if (handResolved) {
        state.phase = 'complete';
        state.awaitingPlayer = false;
        render();
        return;
      }
      resetBetsForNextRound();
      render();
      schedulePhaseAdvance(state.config.dealerSpeedMs);
    });
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
    if (elements.allIn) {
      elements.allIn.addEventListener('click', handlePlayerAllIn);
    }
  }

  onReady(init);
})();
