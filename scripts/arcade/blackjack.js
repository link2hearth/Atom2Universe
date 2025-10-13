(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const DEFAULT_DECK_COUNT = 8;
  const DEFAULT_DEALER_HIT_SOFT_17 = false;
  const DEFAULT_BET_AMOUNTS = Object.freeze([10, 20, 50, 100]);

  const RANKS = [
    { id: 'A', value: 11 },
    { id: '2', value: 2 },
    { id: '3', value: 3 },
    { id: '4', value: 4 },
    { id: '5', value: 5 },
    { id: '6', value: 6 },
    { id: '7', value: 7 },
    { id: '8', value: 8 },
    { id: '9', value: 9 },
    { id: '10', value: 10 },
    { id: 'J', value: 10 },
    { id: 'Q', value: 10 },
    { id: 'K', value: 10 }
  ];

  const SUITS = [
    { id: 'spades', symbol: '♠', color: 'black' },
    { id: 'hearts', symbol: '♥', color: 'red' },
    { id: 'diamonds', symbol: '♦', color: 'red' },
    { id: 'clubs', symbol: '♣', color: 'black' }
  ];

  const CARDS = SUITS.flatMap(suit =>
    RANKS.map(rank => ({
      id: `${rank.id}-${suit.id}`,
      rank: rank.id,
      suit: suit.id,
      symbol: suit.symbol,
      color: suit.color,
      baseValue: rank.value,
      isAce: rank.id === 'A'
    }))
  );

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key.trim()) {
      return fallback;
    }
    const translator = typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
      ? window.i18n.t
      : typeof window !== 'undefined' && typeof window.t === 'function'
        ? window.t
        : null;
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
        console.warn('Blackjack translation error for', key, error);
      }
    }
    return fallback;
  }

  function getDeckCount() {
    const configValue = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.blackjack
      ? GLOBAL_CONFIG.arcade.blackjack.decks
      : null;
    const parsed = Number.parseInt(configValue, 10);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
    return DEFAULT_DECK_COUNT;
  }

  function shouldDealerHitSoft17() {
    const configValue = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.blackjack
      ? GLOBAL_CONFIG.arcade.blackjack.dealerHitSoft17
      : null;
    return Boolean(configValue ?? DEFAULT_DEALER_HIT_SOFT_17);
  }

  function getBetOptions() {
    const config = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.blackjack
      ? GLOBAL_CONFIG.arcade.blackjack.betOptions
      : null;
    const source = Array.isArray(config) ? config : DEFAULT_BET_AMOUNTS;
    const seen = new Set();
    const normalized = [];
    for (let i = 0; i < source.length; i += 1) {
      const numeric = Number(source[i]);
      if (!Number.isFinite(numeric) || numeric <= 0) {
        continue;
      }
      const value = Math.floor(numeric);
      if (value <= 0 || seen.has(value)) {
        continue;
      }
      seen.add(value);
      normalized.push(value);
    }
    if (!normalized.length) {
      return [...DEFAULT_BET_AMOUNTS];
    }
    normalized.sort((a, b) => a - b);
    return normalized;
  }

  function createFreshShoe(deckCount) {
    const counts = new Map();
    for (let i = 0; i < CARDS.length; i += 1) {
      counts.set(CARDS[i].id, deckCount);
    }
    return { counts, deckCount };
  }

  function hasCompleteSet(shoe) {
    if (!shoe || !shoe.counts) {
      return false;
    }
    for (let i = 0; i < CARDS.length; i += 1) {
      if ((shoe.counts.get(CARDS[i].id) ?? 0) <= 0) {
        return false;
      }
    }
    return true;
  }

  function countRemainingCards(shoe) {
    if (!shoe || !shoe.counts) {
      return 0;
    }
    let total = 0;
    for (let i = 0; i < CARDS.length; i += 1) {
      total += shoe.counts.get(CARDS[i].id) ?? 0;
    }
    return total;
  }

  function evaluateHand(cards) {
    let totals = [0];
    let hasAce = false;
    let hasTenValue = false;

    for (let i = 0; i < cards.length; i += 1) {
      const card = cards[i];
      if (!card) {
        continue;
      }
      if (card.isAce) {
        hasAce = true;
        const nextTotals = [];
        for (let j = 0; j < totals.length; j += 1) {
          const base = totals[j];
          nextTotals.push(base + 1);
          nextTotals.push(base + 11);
        }
        totals = nextTotals;
      } else {
        if (card.baseValue === 10) {
          hasTenValue = true;
        }
        for (let j = 0; j < totals.length; j += 1) {
          totals[j] += card.baseValue;
        }
      }
    }

    const uniqueTotals = Array.from(new Set(totals)).sort((a, b) => a - b);
    const hardTotal = uniqueTotals[0] ?? 0;
    const validTotals = uniqueTotals.filter(total => total <= 21);
    const bestTotal = validTotals.length
      ? validTotals[validTotals.length - 1]
      : uniqueTotals[0] ?? 0;
    const isBust = bestTotal > 21;
    const isSoft = !isBust && validTotals.length > 1;
    if (!hasTenValue) {
      hasTenValue = cards.some(card => card && !card.isAce && card.baseValue === 10);
    }
    const isBlackjack =
      cards.length === 2 && hasAce && hasTenValue && !isBust && bestTotal === 21;

    return {
      total: bestTotal,
      hardTotal,
      isSoft,
      isBlackjack,
      isBust,
      cardCount: cards.length
    };
  }

  function formatHandTotal(info, options = {}) {
    if (!info) {
      return options.empty ?? '0';
    }
    if (info.isBust) {
      return translate(
        'scripts.arcade.blackjack.totals.bust',
        `Bust (${info.total})`,
        { total: info.total }
      );
    }
    if (info.isBlackjack && info.cardCount === 2) {
      return translate('scripts.arcade.blackjack.totals.blackjack', 'Blackjack');
    }
    if (info.isSoft) {
      return translate(
        'scripts.arcade.blackjack.totals.soft',
        `${info.hardTotal} / ${info.total}`,
        { hard: info.hardTotal, soft: info.total }
      );
    }
    return `${info.total}`;
  }

  function formatCardLabel(card) {
    const rankLabel = translate(`scripts.arcade.blackjack.ranks.${card.rank}`, card.rank);
    const suitLabel = translate(`scripts.arcade.blackjack.suits.${card.suit}`, card.suit);
    return translate(
      'scripts.arcade.blackjack.cardLabel',
      `${rankLabel} of ${suitLabel}`,
      { rank: rankLabel, suit: suitLabel }
    );
  }

  function createCardElement(card) {
    const element = document.createElement('li');
    element.className = `blackjack-card${card.color === 'red' ? ' blackjack-card--red' : ''}`;
    element.setAttribute('aria-label', formatCardLabel(card));
    const valueSpan = document.createElement('span');
    valueSpan.className = 'blackjack-card__value';
    valueSpan.textContent = card.rank;
    const suitSpan = document.createElement('span');
    suitSpan.className = 'blackjack-card__suit';
    suitSpan.textContent = card.symbol;
    element.appendChild(valueSpan);
    element.appendChild(suitSpan);
    return element;
  }

  function createHiddenCardElement(hiddenLabel) {
    const element = document.createElement('li');
    element.className = 'blackjack-card blackjack-card--hidden';
    element.textContent = hiddenLabel;
    element.setAttribute('aria-label', hiddenLabel);
    return element;
  }

  onReady(() => {
    const dealerCardsElement = document.getElementById('blackjackDealerCards');
    const playerCardsElement = document.getElementById('blackjackPlayerCards');
    const dealerHandElement = dealerCardsElement
      ? dealerCardsElement.closest('.blackjack-hand')
      : null;
    const playerHandElement = playerCardsElement
      ? playerCardsElement.closest('.blackjack-hand')
      : null;
    const dealerTotalElement = document.getElementById('blackjackDealerTotalValue');
    const playerTotalElement = document.getElementById('blackjackPlayerTotalValue');
    const statusElement = document.getElementById('blackjackStatus');
    const betOptionsElement = document.getElementById('blackjackBetOptions');
    const betCurrentElement = document.getElementById('blackjackCurrentBet');
    const betBalanceElement = document.getElementById('blackjackAtomsBalance');
    const newRoundButton = document.getElementById('blackjackNewRound');
    const hitButton = document.getElementById('blackjackHit');
    const standButton = document.getElementById('blackjackStand');
    const deckInfoElement = document.getElementById('blackjackDeckInfo');
    const shoeStateElement = document.getElementById('blackjackShoeState');
    const shoeRemainingElement = document.getElementById('blackjackShoeRemaining');
    const winsElement = document.getElementById('blackjackWins');
    const lossesElement = document.getElementById('blackjackLosses');
    const pushesElement = document.getElementById('blackjackPushes');

    if (
      !dealerCardsElement ||
      !playerCardsElement ||
      !dealerTotalElement ||
      !playerTotalElement ||
      !betOptionsElement ||
      !newRoundButton ||
      !hitButton ||
      !standButton
    ) {
      return;
    }

    let shoe = null;
    let dealerReveal = false;
    let roundActive = false;
    let playerTurn = false;
    let playerCards = [];
    let dealerCards = [];
    const stats = { wins: 0, losses: 0, pushes: 0 };
    const hiddenCardLabel = translate('index.sections.blackjack.hiddenCard', 'Hidden card');
    const configuredBetOptions = getBetOptions();
    const baseBetOptions = configuredBetOptions.length > 1
      ? configuredBetOptions.slice(1)
      : configuredBetOptions.slice();
    const betButtons = [];
    let betOptions = baseBetOptions.map(value => value);
    let betMultiplier = 1;
    let multiplyButton = null;
    let divideButton = null;
    let selectedBaseBet = null;
    let selectedBet = null;
    let activeBet = null;
    let balanceIntervalId = null;
    let currentStatusKey = 'intro';

    function formatBetAmount(amount) {
      if (typeof formatLayeredLocalized === 'function') {
        try {
          const formatted = formatLayeredLocalized(amount, {
            numberFormatOptions: { maximumFractionDigits: 0, minimumFractionDigits: 0 },
            mantissaDigits: 1
          });
          if (formatted) {
            return formatted;
          }
        } catch (error) {
          // Ignore formatting errors and fallback to locale string.
        }
      }
      const numeric = Number(amount);
      if (!Number.isFinite(numeric)) {
        return '0';
      }
      if (typeof formatNumberLocalized === 'function') {
        try {
          const formatted = formatNumberLocalized(numeric, { maximumFractionDigits: 0 });
          if (formatted) {
            return formatted;
          }
        } catch (error) {
          // Ignore formatting errors and fallback to locale string.
        }
      }
      if (typeof numeric.toLocaleString === 'function') {
        return numeric.toLocaleString();
      }
      return `${numeric}`;
    }

    function translateBetOptionLabel(amountLabel) {
      return translate(
        'index.sections.blackjack.bet.option',
        `${amountLabel} atoms`,
        { amount: amountLabel }
      );
    }

    function getGameAtoms() {
      if (typeof gameState === 'undefined') {
        return null;
      }
      const atoms = gameState.atoms;
      if (atoms instanceof LayeredNumber) {
        return atoms;
      }
      if (typeof LayeredNumber === 'function') {
        try {
          return new LayeredNumber(atoms);
        } catch (error) {
          return null;
        }
      }
      return null;
    }

    function createLayeredBet(amount) {
      if (typeof LayeredNumber !== 'function') {
        return null;
      }
      const numeric = Number(amount);
      if (!Number.isFinite(numeric) || numeric <= 0) {
        return null;
      }
      try {
        return new LayeredNumber(numeric);
      } catch (error) {
        return null;
      }
    }

    function canAffordBet(amount) {
      const atoms = getGameAtoms();
      const bet = createLayeredBet(amount);
      if (!atoms || !bet) {
        return false;
      }
      return atoms.compare(bet) >= 0;
    }

    function setSelectedBet(amount, baseAmount) {
      if (amount == null) {
        selectedBet = null;
        selectedBaseBet = null;
      } else {
        const numeric = Number(amount);
        if (Number.isFinite(numeric) && numeric > 0) {
          selectedBet = Math.floor(numeric);
          if (baseAmount != null) {
            const numericBase = Number(baseAmount);
            if (Number.isFinite(numericBase) && numericBase > 0) {
              selectedBaseBet = Math.floor(numericBase);
            } else if (betMultiplier > 0) {
              selectedBaseBet = Math.floor(selectedBet / betMultiplier);
            } else {
              selectedBaseBet = null;
            }
          } else if (betMultiplier > 0) {
            selectedBaseBet = Math.floor(selectedBet / betMultiplier);
          } else {
            selectedBaseBet = null;
          }
        } else {
          selectedBet = null;
          selectedBaseBet = null;
        }
      }
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const buttonAmount = Number(button.dataset.bet);
        const isSelected = selectedBet === buttonAmount;
        button.classList.toggle('blackjack-bet__option--selected', isSelected);
        button.setAttribute('aria-pressed', isSelected ? 'true' : 'false');
      }
      if (betCurrentElement) {
        betCurrentElement.textContent = selectedBet != null
          ? formatBetAmount(selectedBet)
          : translate('index.sections.blackjack.bet.none', 'None');
      }
    }

    function updateBetOptionValues() {
      betOptions = baseBetOptions.map(amount => amount * betMultiplier);
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const amount = betOptions[i];
        button.dataset.bet = `${amount}`;
        const label = formatBetAmount(amount);
        button.textContent = label;
        button.setAttribute('aria-label', translateBetOptionLabel(label));
      }
    }

    function updateMultiplierLabels() {
      const formattedMultiplier = formatBetAmount(betMultiplier);
      if (multiplyButton) {
        const aria = translate(
          'index.sections.blackjack.bet.scale.multiplyAria',
          `Multiply bet options by 10 (current multiplier: ×${formattedMultiplier})`,
          { multiplier: formattedMultiplier }
        );
        multiplyButton.disabled = roundActive;
        multiplyButton.setAttribute('aria-label', aria);
        multiplyButton.title = aria;
      }
      if (divideButton) {
        const disabled = roundActive || betMultiplier <= 1;
        const aria = translate(
          'index.sections.blackjack.bet.scale.divideAria',
          `Divide bet options by 10 (current multiplier: ×${formattedMultiplier})`,
          { multiplier: formattedMultiplier }
        );
        divideButton.disabled = disabled;
        divideButton.setAttribute('aria-label', aria);
        divideButton.title = aria;
      }
    }

    function setBetMultiplier(newMultiplier) {
      const numeric = Number(newMultiplier);
      const normalized = Number.isFinite(numeric) && numeric > 0 ? Math.max(1, Math.floor(numeric)) : betMultiplier;
      if (normalized === betMultiplier) {
        updateMultiplierLabels();
        return;
      }
      betMultiplier = normalized;
      updateBetOptionValues();
      if (selectedBaseBet != null) {
        const scaledSelection = selectedBaseBet * betMultiplier;
        setSelectedBet(scaledSelection, selectedBaseBet);
      } else if (selectedBet != null) {
        setSelectedBet(selectedBet);
      }
      ensureSelectedBetAffordable();
      updateBetButtons();
      updateMultiplierLabels();
    }

    function updateBetButtons() {
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const amount = Number(button.dataset.bet);
        const disable = roundActive || !canAffordBet(amount);
        button.disabled = disable;
        button.classList.toggle('blackjack-bet__option--unavailable', !roundActive && disable);
      }
      updateMultiplierLabels();
    }

    function ensureSelectedBetAffordable() {
      if (roundActive) {
        return;
      }
      if (selectedBet != null && canAffordBet(selectedBet)) {
        return;
      }
      for (let i = 0; i < betOptions.length; i += 1) {
        const option = betOptions[i];
        if (canAffordBet(option)) {
          setSelectedBet(option, baseBetOptions[i] ?? option);
          return;
        }
      }
      if (selectedBet != null) {
        setSelectedBet(null);
      }
    }

    function updateBalanceDisplay() {
      const atoms = getGameAtoms();
      if (betBalanceElement) {
        betBalanceElement.textContent = atoms ? atoms.toString() : '0';
      }
      if (!roundActive) {
        ensureSelectedBetAffordable();
        let hasAffordable = false;
        for (let i = 0; i < betOptions.length; i += 1) {
          if (canAffordBet(betOptions[i])) {
            hasAffordable = true;
            break;
          }
        }
        if (currentStatusKey === 'intro' && !hasAffordable) {
          setStatus('insufficientAtoms', 'Not enough atoms for this bet.');
        } else if (currentStatusKey === 'insufficientAtoms' && hasAffordable) {
          setStatus('intro', 'Start a new round to challenge the dealer.');
        }
      }
      updateBetButtons();
    }

    function initializeBetOptions() {
      const previousBaseSelection = selectedBaseBet;
      betButtons.length = 0;
      betOptionsElement.innerHTML = '';
      const optionsAria = translate(
        'index.sections.blackjack.bet.optionsAria',
        'Available bet amounts'
      );
      if (optionsAria) {
        betOptionsElement.setAttribute('aria-label', optionsAria);
      }

      const scaleGroup = document.createElement('div');
      scaleGroup.className = 'blackjack-bet__scale';
      scaleGroup.setAttribute('role', 'group');
      const scaleAria = translate(
        'index.sections.blackjack.bet.scale.groupAria',
        'Adjust bet multiplier'
      );
      if (scaleAria) {
        scaleGroup.setAttribute('aria-label', scaleAria);
      }

      multiplyButton = document.createElement('button');
      multiplyButton.type = 'button';
      multiplyButton.className = 'blackjack-bet__option blackjack-bet__scale-button';
      multiplyButton.textContent = translate(
        'index.sections.blackjack.bet.scale.multiplyLabel',
        '×10'
      );
      multiplyButton.setAttribute('aria-pressed', 'false');
      multiplyButton.addEventListener('click', () => {
        if (roundActive) {
          return;
        }
        setBetMultiplier(betMultiplier * 10);
      });
      scaleGroup.appendChild(multiplyButton);

      divideButton = document.createElement('button');
      divideButton.type = 'button';
      divideButton.className = 'blackjack-bet__option blackjack-bet__scale-button';
      divideButton.textContent = translate(
        'index.sections.blackjack.bet.scale.divideLabel',
        '÷10'
      );
      divideButton.setAttribute('aria-pressed', 'false');
      divideButton.addEventListener('click', () => {
        if (roundActive || betMultiplier <= 1) {
          return;
        }
        setBetMultiplier(betMultiplier / 10);
      });
      scaleGroup.appendChild(divideButton);

      betOptionsElement.appendChild(scaleGroup);

      for (let i = 0; i < baseBetOptions.length; i += 1) {
        const baseAmount = baseBetOptions[i];
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'blackjack-bet__option';
        button.dataset.baseBet = `${baseAmount}`;
        button.setAttribute('aria-pressed', 'false');
        button.addEventListener('click', () => {
          if (roundActive) {
            return;
          }
          const scaledAmount = baseAmount * betMultiplier;
          if (!canAffordBet(scaledAmount)) {
            setStatus('insufficientAtoms', 'Not enough atoms for this bet.');
            if (typeof showToast === 'function') {
              showToast(translate(
                'scripts.arcade.blackjack.status.insufficientAtoms',
                'Not enough atoms for this bet.'
              ));
            }
            return;
          }
          setSelectedBet(scaledAmount, baseAmount);
          updateBetButtons();
        });
        betOptionsElement.appendChild(button);
        betButtons.push(button);
      }

      updateBetOptionValues();

      if (previousBaseSelection != null) {
        const restored = previousBaseSelection * betMultiplier;
        setSelectedBet(restored, previousBaseSelection);
      } else if (selectedBet != null) {
        setSelectedBet(selectedBet);
      } else {
        setSelectedBet(null);
      }

      ensureSelectedBetAffordable();
      updateBetButtons();
    }

    function startBalanceUpdates() {
      if (balanceIntervalId != null || typeof window === 'undefined') {
        return;
      }
      balanceIntervalId = window.setInterval(() => {
        if (typeof document !== 'undefined' && document.hidden) {
          return;
        }
        updateBalanceDisplay();
      }, 1000);
    }

    function settleBet(outcomeKey) {
      if (!activeBet) {
        activeBet = null;
        updateBalanceDisplay();
        return;
      }
      let multiplier = 0;
      switch (outcomeKey) {
        case 'playerWins':
        case 'dealerBust':
        case 'win':
          multiplier = 2;
          break;
        case 'blackjack':
          multiplier = 2;
          break;
        case 'push':
        case 'pushBlackjack':
          multiplier = 1;
          break;
        default:
          multiplier = 0;
          break;
      }
      if (multiplier > 0 && typeof gameState !== 'undefined') {
        const payout = activeBet.multiplyNumber(multiplier);
        gameState.atoms = gameState.atoms.add(payout);
        if (typeof updateUI === 'function') {
          updateUI();
        }
        if (typeof saveGame === 'function') {
          saveGame();
        }
      }
      activeBet = null;
      updateBalanceDisplay();
    }

    initializeBetOptions();
    updateBalanceDisplay();
    startBalanceUpdates();

    function ensureShoeReady() {
      const deckCount = getDeckCount();
      if (!shoe || shoe.deckCount !== deckCount || !hasCompleteSet(shoe)) {
        shoe = createFreshShoe(deckCount);
        updateShoeDisplay();
      }
    }

    function updateShoeDisplay() {
      const deckCount = getDeckCount();
      if (deckInfoElement) {
        deckInfoElement.textContent = translate(
          'scripts.arcade.blackjack.shoe.decks',
          `${deckCount} decks`,
          { count: deckCount }
        );
      }
      const remaining = shoe ? countRemainingCards(shoe) : deckCount * CARDS.length;
      if (shoeRemainingElement) {
        shoeRemainingElement.textContent = translate(
          'scripts.arcade.blackjack.shoe.remaining',
          `${remaining} cards remaining`,
          { count: remaining }
        );
      }
      const stateKey = shoe && !hasCompleteSet(shoe) ? 'reshuffle' : 'fresh';
      if (shoeStateElement) {
        shoeStateElement.textContent = translate(
          `scripts.arcade.blackjack.shoe.status.${stateKey}`,
          stateKey === 'fresh' ? 'Shoe ready' : 'Reshuffle on next draw'
        );
      }
    }

    function drawCard() {
      ensureShoeReady();
      if (!shoe) {
        return null;
      }
      let candidate = null;
      let attempts = 0;
      while (!candidate) {
        const counts = shoe.counts;
        const randomIndex = Math.floor(Math.random() * CARDS.length);
        const card = CARDS[randomIndex];
        const remaining = counts.get(card.id) ?? 0;
        if (remaining > 0) {
          counts.set(card.id, remaining - 1);
          candidate = card;
          break;
        }
        attempts += 1;
        if (attempts > CARDS.length * 4) {
          shoe = createFreshShoe(getDeckCount());
          attempts = 0;
        }
      }
      updateShoeDisplay();
      return candidate;
    }

    function renderHands() {
      dealerCardsElement.innerHTML = '';
      playerCardsElement.innerHTML = '';
      for (let i = 0; i < dealerCards.length; i += 1) {
        if (!dealerReveal && i === 1) {
          dealerCardsElement.appendChild(createHiddenCardElement(hiddenCardLabel));
        } else {
          dealerCardsElement.appendChild(createCardElement(dealerCards[i]));
        }
      }
      for (let i = 0; i < playerCards.length; i += 1) {
        playerCardsElement.appendChild(createCardElement(playerCards[i]));
      }
    }

    function updateTotals() {
      const playerInfo = evaluateHand(playerCards);
      const dealerInfo = evaluateHand(dealerCards);
      playerTotalElement.textContent = formatHandTotal(playerInfo, { empty: '0' });
      if (dealerReveal) {
        dealerTotalElement.textContent = formatHandTotal(dealerInfo, { empty: '0' });
      } else {
        dealerTotalElement.textContent = translate('scripts.arcade.blackjack.totals.hidden', 'Hidden');
      }
    }

    function updateStatsDisplay() {
      if (winsElement) {
        winsElement.textContent = `${stats.wins}`;
      }
      if (lossesElement) {
        lossesElement.textContent = `${stats.losses}`;
      }
      if (pushesElement) {
        pushesElement.textContent = `${stats.pushes}`;
      }
    }

    const HAND_OUTCOME_CLASSES = [
      'blackjack-hand--win',
      'blackjack-hand--lose',
      'blackjack-hand--push'
    ];

    const HAND_OUTCOME_CLASS_MAP = {
      win: 'blackjack-hand--win',
      lose: 'blackjack-hand--lose',
      push: 'blackjack-hand--push'
    };

    function applyHandOutcome(element, outcome) {
      if (!element) {
        return;
      }
      for (let i = 0; i < HAND_OUTCOME_CLASSES.length; i += 1) {
        element.classList.remove(HAND_OUTCOME_CLASSES[i]);
      }
      if (outcome && HAND_OUTCOME_CLASS_MAP[outcome]) {
        element.classList.add(HAND_OUTCOME_CLASS_MAP[outcome]);
      }
    }

    function updateHandOutcomes(playerOutcome, dealerOutcome) {
      applyHandOutcome(playerHandElement, playerOutcome);
      applyHandOutcome(dealerHandElement, dealerOutcome);
    }

    function updateButtons() {
      newRoundButton.disabled = roundActive;
      hitButton.disabled = !roundActive || !playerTurn;
      standButton.disabled = !roundActive || !playerTurn;
      updateBetButtons();
    }

    function setStatus(key, fallback, params) {
      if (typeof key === 'string' && key) {
        currentStatusKey = key;
      } else {
        currentStatusKey = '';
      }
      if (statusElement) {
        statusElement.textContent = translate(
          `scripts.arcade.blackjack.status.${key}`,
          fallback,
          params
        );
      }
    }

    function finishRound(resultKey, fallback, params, payoutKey) {
      roundActive = false;
      playerTurn = false;
      dealerReveal = true;
      renderHands();
      updateTotals();
      if (resultKey) {
        setStatus(resultKey, fallback, params);
      }
      applyRoundOutcome(resultKey);
      settleBet(payoutKey);
      updateButtons();
      updateStatsDisplay();
    }

    function applyRoundOutcome(resultKey) {
      switch (resultKey) {
        case 'playerBlackjack':
        case 'dealerBust':
        case 'playerWins':
          updateHandOutcomes('win', 'lose');
          break;
        case 'dealerBlackjack':
        case 'playerBust':
        case 'dealerWins':
          updateHandOutcomes('lose', 'win');
          break;
        case 'pushBlackjack':
        case 'push':
          updateHandOutcomes('push', 'push');
          break;
        default:
          updateHandOutcomes(null, null);
          break;
      }
    }

    function resolveInitialBlackjack() {
      const playerInfo = evaluateHand(playerCards);
      const dealerInfo = evaluateHand(dealerCards);
      if (playerInfo.isBlackjack || dealerInfo.isBlackjack) {
        if (playerInfo.isBlackjack && dealerInfo.isBlackjack) {
          stats.pushes += 1;
          finishRound('pushBlackjack', 'Push: both reveal blackjack.', {}, 'pushBlackjack');
        } else if (playerInfo.isBlackjack) {
          stats.wins += 1;
          finishRound('playerBlackjack', 'Blackjack! You win.', {}, 'blackjack');
        } else {
          stats.losses += 1;
          finishRound('dealerBlackjack', 'Dealer blackjack. You lose.', {}, 'lose');
        }
        return true;
      }
      return false;
    }

    function concludeRound() {
      const playerInfo = evaluateHand(playerCards);
      const dealerInfo = evaluateHand(dealerCards);
      const playerTotal = formatHandTotal(playerInfo, { empty: '0' });
      const dealerTotal = formatHandTotal(dealerInfo, { empty: '0' });
      if (playerInfo.isBust) {
        stats.losses += 1;
        finishRound('playerBust', 'Bust! Dealer wins with {dealer}.', { dealer: dealerTotal }, 'lose');
      } else if (dealerInfo.isBust) {
        stats.wins += 1;
        finishRound('dealerBust', 'Dealer busts with {dealer}. You win!', { dealer: dealerTotal, player: playerTotal }, 'dealerBust');
      } else if (playerInfo.total > dealerInfo.total) {
        stats.wins += 1;
        finishRound('playerWins', 'You win {player} vs {dealer}.', { player: playerTotal, dealer: dealerTotal }, 'playerWins');
      } else if (dealerInfo.total > playerInfo.total) {
        stats.losses += 1;
        finishRound('dealerWins', 'Dealer wins {dealer} vs {player}.', { player: playerTotal, dealer: dealerTotal }, 'lose');
      } else {
        stats.pushes += 1;
        finishRound('push', 'Push at {total}.', { total: playerTotal }, 'push');
      }
    }

    function dealerTurn() {
      dealerReveal = true;
      renderHands();
      updateTotals();
      setStatus('dealerTurn', 'Dealer plays…');
      const hitSoft17 = shouldDealerHitSoft17();
      let dealerInfo = evaluateHand(dealerCards);
      while (
        !dealerInfo.isBust &&
        (dealerInfo.total < 17 || (dealerInfo.total === 17 && dealerInfo.isSoft && hitSoft17))
      ) {
        dealerCards.push(drawCard());
        renderHands();
        dealerInfo = evaluateHand(dealerCards);
      }
      concludeRound();
    }

    function startNewRound() {
      ensureShoeReady();
      if (selectedBet == null) {
        setStatus('selectBet', 'Select a bet before starting a round.');
        if (typeof showToast === 'function') {
          showToast(translate(
            'scripts.arcade.blackjack.status.selectBet',
            'Select a bet before starting a round.'
          ));
        }
        return;
      }
      if (!canAffordBet(selectedBet)) {
        setStatus('insufficientAtoms', 'Not enough atoms for this bet.');
        if (typeof showToast === 'function') {
          showToast(translate(
            'scripts.arcade.blackjack.status.insufficientAtoms',
            'Not enough atoms for this bet.'
          ));
        }
        updateBalanceDisplay();
        return;
      }
      const layeredBet = createLayeredBet(selectedBet);
      if (!layeredBet) {
        setStatus('insufficientAtoms', 'Not enough atoms for this bet.');
        return;
      }
      activeBet = layeredBet;
      if (typeof gameState !== 'undefined') {
        gameState.atoms = gameState.atoms.subtract(layeredBet);
        if (typeof updateUI === 'function') {
          updateUI();
        }
        if (typeof saveGame === 'function') {
          saveGame();
        }
      }
      updateBalanceDisplay();
      roundActive = true;
      playerTurn = true;
      updateButtons();
      dealerReveal = false;
      playerCards = [];
      dealerCards = [];
      updateHandOutcomes(null, null);
      playerCards.push(drawCard());
      dealerCards.push(drawCard());
      playerCards.push(drawCard());
      dealerCards.push(drawCard());
      renderHands();
      updateTotals();
      if (!resolveInitialBlackjack()) {
        setStatus('playerTurn', 'Hit to draw or Stand to hold.');
        updateButtons();
      }
    }

    function handleHit() {
      if (!roundActive || !playerTurn) {
        return;
      }
      playerCards.push(drawCard());
      renderHands();
      updateTotals();
      const info = evaluateHand(playerCards);
      if (info.isBust) {
        concludeRound();
      }
    }

    function handleStand() {
      if (!roundActive || !playerTurn) {
        return;
      }
      playerTurn = false;
      updateButtons();
      dealerTurn();
    }

    newRoundButton.addEventListener('click', () => {
      if (roundActive) {
        return;
      }
      startNewRound();
    });

    hitButton.addEventListener('click', handleHit);
    standButton.addEventListener('click', handleStand);

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('i18n:languagechange', () => {
        initializeBetOptions();
        updateBalanceDisplay();
      });
    }

    updateShoeDisplay();
    updateTotals();
    updateStatsDisplay();
    updateButtons();
    setStatus('intro', 'Start a new round to challenge the dealer.');
    updateBalanceDisplay();
  });
})();
