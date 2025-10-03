(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const DEFAULT_DECK_COUNT = 8;
  const DEFAULT_DEALER_HIT_SOFT_17 = false;

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
        if (typeof result === 'string' && result.trim()) {
          return result;
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
    let hardTotal = 0;
    let aceCount = 0;
    for (let i = 0; i < cards.length; i += 1) {
      if (cards[i].isAce) {
        hardTotal += 1;
        aceCount += 1;
      } else {
        hardTotal += cards[i].baseValue;
      }
    }
    let bestTotal = hardTotal;
    let softAces = aceCount;
    while (softAces > 0 && bestTotal + 10 <= 21) {
      bestTotal += 10;
      softAces -= 1;
    }
    const finalTotal = bestTotal;
    const bust = finalTotal > 21;
    const usedSoftAce = !bust && aceCount > softAces;
    const hasAce = aceCount > 0;
    const hasTenValue = cards.some(card => !card.isAce && card.baseValue === 10);
    const isBlackjack = cards.length === 2 && hasAce && hasTenValue && !bust && finalTotal === 21;
    return {
      total: bust ? hardTotal : finalTotal,
      hardTotal,
      isSoft: usedSoftAce,
      isBlackjack,
      isBust: bust,
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
    const dealerTotalElement = document.getElementById('blackjackDealerTotalValue');
    const playerTotalElement = document.getElementById('blackjackPlayerTotalValue');
    const statusElement = document.getElementById('blackjackStatus');
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
      !statusElement ||
      !newRoundButton ||
      !hitButton ||
      !standButton ||
      !deckInfoElement ||
      !shoeStateElement ||
      !shoeRemainingElement ||
      !winsElement ||
      !lossesElement ||
      !pushesElement
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

    function ensureShoeReady() {
      const deckCount = getDeckCount();
      if (!shoe || shoe.deckCount !== deckCount || !hasCompleteSet(shoe)) {
        shoe = createFreshShoe(deckCount);
        updateShoeDisplay();
      }
    }

    function updateShoeDisplay() {
      const deckCount = getDeckCount();
      deckInfoElement.textContent = translate(
        'scripts.arcade.blackjack.shoe.decks',
        `${deckCount} decks`,
        { count: deckCount }
      );
      const remaining = shoe ? countRemainingCards(shoe) : deckCount * CARDS.length;
      shoeRemainingElement.textContent = translate(
        'scripts.arcade.blackjack.shoe.remaining',
        `${remaining} cards remaining`,
        { count: remaining }
      );
      const stateKey = shoe && !hasCompleteSet(shoe) ? 'reshuffle' : 'fresh';
      shoeStateElement.textContent = translate(
        `scripts.arcade.blackjack.shoe.status.${stateKey}`,
        stateKey === 'fresh' ? 'Shoe ready' : 'Reshuffle on next draw'
      );
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
      winsElement.textContent = `${stats.wins}`;
      lossesElement.textContent = `${stats.losses}`;
      pushesElement.textContent = `${stats.pushes}`;
    }

    function updateButtons() {
      newRoundButton.disabled = roundActive;
      hitButton.disabled = !roundActive || !playerTurn;
      standButton.disabled = !roundActive || !playerTurn;
    }

    function setStatus(key, fallback, params) {
      statusElement.textContent = translate(
        `scripts.arcade.blackjack.status.${key}`,
        fallback,
        params
      );
    }

    function finishRound(resultKey, fallback, params) {
      roundActive = false;
      playerTurn = false;
      dealerReveal = true;
      renderHands();
      updateTotals();
      if (resultKey) {
        setStatus(resultKey, fallback, params);
      }
      updateButtons();
      updateStatsDisplay();
    }

    function resolveInitialBlackjack() {
      const playerInfo = evaluateHand(playerCards);
      const dealerInfo = evaluateHand(dealerCards);
      if (playerInfo.isBlackjack || dealerInfo.isBlackjack) {
        if (playerInfo.isBlackjack && dealerInfo.isBlackjack) {
          stats.pushes += 1;
          finishRound('pushBlackjack', 'Push: both reveal blackjack.', {});
        } else if (playerInfo.isBlackjack) {
          stats.wins += 1;
          finishRound('playerBlackjack', 'Blackjack! You win.', {});
        } else {
          stats.losses += 1;
          finishRound('dealerBlackjack', 'Dealer blackjack. You lose.', {});
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
        finishRound('playerBust', 'Bust! Dealer wins with {dealer}.', { dealer: dealerTotal });
      } else if (dealerInfo.isBust) {
        stats.wins += 1;
        finishRound('dealerBust', 'Dealer busts with {dealer}. You win!', { dealer: dealerTotal, player: playerTotal });
      } else if (playerInfo.total > dealerInfo.total) {
        stats.wins += 1;
        finishRound('playerWins', 'You win {player} vs {dealer}.', { player: playerTotal, dealer: dealerTotal });
      } else if (dealerInfo.total > playerInfo.total) {
        stats.losses += 1;
        finishRound('dealerWins', 'Dealer wins {dealer} vs {player}.', { player: playerTotal, dealer: dealerTotal });
      } else {
        stats.pushes += 1;
        finishRound('push', 'Push at {total}.', { total: playerTotal });
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
      roundActive = true;
      playerTurn = true;
      updateButtons();
      dealerReveal = false;
      playerCards = [];
      dealerCards = [];
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

    updateShoeDisplay();
    updateTotals();
    updateStatsDisplay();
    updateButtons();
    setStatus('intro', 'Start a new round to challenge the dealer.');
  });
})();
