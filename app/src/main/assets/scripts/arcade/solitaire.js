(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const DEFAULT_SOLITAIRE_REWARDS = Object.freeze({
    completion: Object.freeze({
      gachaTickets: 0,
      bonusTicketAmount: 0
    })
  });

  const SUITS = Object.freeze([
    Object.freeze({ key: 'hearts', symbol: '♥', color: 'red' }),
    Object.freeze({ key: 'diamonds', symbol: '♦', color: 'red' }),
    Object.freeze({ key: 'clubs', symbol: '♣', color: 'black' }),
    Object.freeze({ key: 'spades', symbol: '♠', color: 'black' })
  ]);
  const RANKS = Object.freeze([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]);
  const RANK_LABELS = Object.freeze({ 1: 'A', 11: 'J', 12: 'Q', 13: 'K' });
  const TABLEAU_FACE_DOWN_OFFSET = 24;
  const TABLEAU_FACE_UP_OFFSET = 38;
  const WASTE_HORIZONTAL_OFFSET = 18;

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key.trim()) {
      return typeof fallback === 'string' ? fallback : '';
    }
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined') {
      if (typeof window.translateOrDefault === 'function') {
        return window.translateOrDefault(key, fallback, params);
      }
      const api = window.i18n && typeof window.i18n.t === 'function' ? window.i18n.t : null;
      if (api) {
        try {
          const result = api(key, params);
          if (typeof result === 'string' && result.trim() && result !== key) {
            return result;
          }
        } catch (error) {
          console.warn('Solitaire translation error for', key, error);
        }
      }
    }
    if (typeof fallback === 'string') {
      if (!params) {
        return fallback;
      }
      return fallback.replace(/\{(\w+)\}/g, (match, name) => {
        if (Object.prototype.hasOwnProperty.call(params, name)) {
          return params[name];
        }
        return match;
      });
    }
    return key;
  }

  function formatIntegerLocalized(value) {
    const numeric = Number.isFinite(Number(value)) ? Math.floor(Number(value)) : 0;
    const safe = numeric >= 0 ? numeric : 0;
    try {
      return safe.toLocaleString();
    } catch (error) {
      return String(safe);
    }
  }

  function getSolitaireRewardConfig() {
    const arcadeConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade ? GLOBAL_CONFIG.arcade : null;
    const solitaireConfig = arcadeConfig && typeof arcadeConfig.solitaire === 'object' ? arcadeConfig.solitaire : null;
    const rewardsSource = solitaireConfig && typeof solitaireConfig.rewards === 'object'
      ? solitaireConfig.rewards
      : null;
    const completionSource = rewardsSource && typeof rewardsSource.completion === 'object'
      ? rewardsSource.completion
      : {};

    const gachaCandidates = [
      completionSource.gachaTickets,
      completionSource.tickets,
      completionSource.amount,
      DEFAULT_SOLITAIRE_REWARDS.completion.gachaTickets
    ];
    let gachaTickets = 0;
    for (let index = 0; index < gachaCandidates.length; index += 1) {
      const candidate = Number(gachaCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        gachaTickets = Math.floor(candidate);
        break;
      }
    }

    const bonusCandidates = [
      completionSource.bonusTicketAmount,
      completionSource.mach3Tickets,
      completionSource.bonusTickets,
      DEFAULT_SOLITAIRE_REWARDS.completion.bonusTicketAmount
    ];
    let bonusTickets = 0;
    for (let index = 0; index < bonusCandidates.length; index += 1) {
      const candidate = Number(bonusCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        bonusTickets = Math.floor(candidate);
        break;
      }
    }

    return {
      completion: {
        gachaTickets: Math.max(0, gachaTickets),
        bonusTicketAmount: Math.max(0, bonusTickets)
      }
    };
  }

  function shuffleInPlace(array) {
    for (let index = array.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(Math.random() * (index + 1));
      const temporary = array[index];
      array[index] = array[swapIndex];
      array[swapIndex] = temporary;
    }
  }

  function formatRank(rank) {
    if (Object.prototype.hasOwnProperty.call(RANK_LABELS, rank)) {
      return RANK_LABELS[rank];
    }
    return String(rank);
  }

  function createDeck() {
    const deck = [];
    for (let suitIndex = 0; suitIndex < SUITS.length; suitIndex += 1) {
      const suit = SUITS[suitIndex];
      for (let rankIndex = 0; rankIndex < RANKS.length; rankIndex += 1) {
        const rank = RANKS[rankIndex];
        deck.push({
          id: `${suit.key}-${rank}`,
          suit: suit.key,
          symbol: suit.symbol,
          rank,
          color: suit.color,
          faceUp: false,
          pile: 'stock',
          pileIndex: -1,
          element: null,
          parts: null
        });
      }
    }
    return deck;
  }

  onReady(() => {
    const boardElement = document.getElementById('solitaireBoard');
    const resetButton = document.getElementById('solitaireReset');

    if (!boardElement || !resetButton) {
      return;
    }

    const rootElement = boardElement.closest('.solitaire');

    const piles = setupBoard(boardElement);
    const rewardConfig = getSolitaireRewardConfig();
    const state = {
      stock: [],
      waste: [],
      foundations: Array.from({ length: 4 }, () => []),
      tableau: Array.from({ length: 7 }, () => []),
      selected: null,
      rewardClaimed: false
    };

    function resetGame() {
      clearSelection();
      const deck = createDeck();
      shuffleInPlace(deck);
      state.stock = [];
      state.waste = [];
      state.foundations = Array.from({ length: 4 }, () => []);
      state.tableau = Array.from({ length: 7 }, () => []);
      state.rewardClaimed = false;
      dealInitialLayout(deck);
      renderAll();
    }

    function dealInitialLayout(deck) {
      for (let columnIndex = 0; columnIndex < state.tableau.length; columnIndex += 1) {
        const column = state.tableau[columnIndex];
        for (let depth = 0; depth <= columnIndex; depth += 1) {
          const card = deck.pop();
          if (!card) {
            break;
          }
          card.faceUp = depth === columnIndex;
          card.pile = 'tableau';
          card.pileIndex = columnIndex;
          card.element = null;
          card.parts = null;
          column.push(card);
        }
      }

      deck.forEach((card) => {
        card.faceUp = false;
        card.pile = 'stock';
        card.pileIndex = -1;
        card.element = null;
        card.parts = null;
      });

      state.stock = deck;
    }

    function renderAll() {
      renderStock();
      renderWaste();
      renderFoundations();
      renderTableau();
      updateDropTargets();
      updateCompletionState();
    }

    function renderStock() {
      const { stockElement } = piles;
      stockElement.replaceChildren();
      if (state.stock.length > 0) {
        const card = state.stock[state.stock.length - 1];
        card.faceUp = false;
        const cardElement = ensureCardElement(card);
        updateCardElement(card);
        cardElement.style.setProperty('--solitaire-offset-x', '0px');
        cardElement.style.setProperty('--solitaire-offset-y', '0px');
        cardElement.style.zIndex = '1';
        stockElement.appendChild(cardElement);
      }
      stockElement.classList.toggle('is-empty', state.stock.length === 0);
    }

    function renderWaste() {
      const { wasteElement } = piles;
      wasteElement.replaceChildren();
      const visibleCount = Math.min(state.waste.length, 3);
      const startIndex = state.waste.length - visibleCount;
      for (let index = startIndex; index < state.waste.length; index += 1) {
        const card = state.waste[index];
        const cardElement = ensureCardElement(card);
        updateCardElement(card);
        const offset = (index - startIndex) * WASTE_HORIZONTAL_OFFSET;
        cardElement.style.setProperty('--solitaire-offset-x', `${offset}px`);
        cardElement.style.setProperty('--solitaire-offset-y', '0px');
        cardElement.style.zIndex = String(10 + index);
        wasteElement.appendChild(cardElement);
      }
      wasteElement.classList.toggle('is-empty', state.waste.length === 0);
    }

    function renderFoundations() {
      state.foundations.forEach((foundation, index) => {
        const element = piles.foundationElements[index];
        element.replaceChildren();
        if (foundation.length > 0) {
          const card = foundation[foundation.length - 1];
          const cardElement = ensureCardElement(card);
          updateCardElement(card);
          cardElement.style.setProperty('--solitaire-offset-x', '0px');
          cardElement.style.setProperty('--solitaire-offset-y', '0px');
          cardElement.style.zIndex = '1';
          element.appendChild(cardElement);
        }
        element.classList.toggle('is-empty', foundation.length === 0);
      });
    }

    function renderTableau() {
      for (let columnIndex = 0; columnIndex < state.tableau.length; columnIndex += 1) {
        renderTableauPile(columnIndex);
      }
    }

    function renderTableauPile(columnIndex) {
      const column = state.tableau[columnIndex];
      const element = piles.tableauElements[columnIndex];
      element.replaceChildren();

      let offset = 0;
      for (let index = 0; index < column.length; index += 1) {
        const card = column[index];
        const cardElement = ensureCardElement(card);
        updateCardElement(card);
        cardElement.style.setProperty('--solitaire-offset-x', '0px');
        cardElement.style.setProperty('--solitaire-offset-y', `${offset}px`);
        cardElement.style.zIndex = String(10 + index);
        element.appendChild(cardElement);
        offset += card.faceUp ? TABLEAU_FACE_UP_OFFSET : TABLEAU_FACE_DOWN_OFFSET;
      }

      const stackOffset = column.length > 0
        ? offset - (column[column.length - 1].faceUp ? TABLEAU_FACE_UP_OFFSET : TABLEAU_FACE_DOWN_OFFSET)
        : 0;
      if (stackOffset > 0) {
        element.style.setProperty('--solitaire-stack-offset', `${stackOffset}px`);
      } else {
        element.style.removeProperty('--solitaire-stack-offset');
      }

      element.classList.toggle('is-empty', column.length === 0);
    }

    function ensureCardElement(card) {
      if (card.element && card.element instanceof HTMLElement) {
        return card.element;
      }
      const element = document.createElement('button');
      element.type = 'button';
      element.className = 'solitaire-card';
      element.dataset.color = card.color;
      element.dataset.rank = formatRank(card.rank);
      element.dataset.suit = card.suit;

      const topCorner = document.createElement('span');
      topCorner.className = 'solitaire-card__corner solitaire-card__corner--top';

      const topCornerSymbol = document.createElement('span');
      topCornerSymbol.className = 'solitaire-card__corner-symbol';

      const topCornerRank = document.createElement('span');
      topCornerRank.className = 'solitaire-card__corner-rank';

      const value = document.createElement('span');
      value.className = 'solitaire-card__value';

      topCorner.append(topCornerSymbol, topCornerRank);
      element.append(topCorner, value);

      element.addEventListener('click', (event) => {
        handleCardClick(card, event);
      });

      element.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          handleCardClick(card, { detail: 1 });
          return;
        }
        if (event.key && event.key.toLowerCase() === 'f') {
          event.preventDefault();
          tryAutoFoundation(card);
        }
      });

      card.element = element;
      card.parts = { topCornerSymbol, topCornerRank, value };
      return element;
    }

    function updateCardElement(card) {
      const element = ensureCardElement(card);
      if (!card.parts) {
        return;
      }
      const rankLabel = formatRank(card.rank);
      card.parts.topCornerSymbol.textContent = card.symbol;
      card.parts.topCornerRank.textContent = rankLabel;
      card.parts.value.textContent = rankLabel;
      element.dataset.color = card.color;
      element.dataset.rank = rankLabel;
      element.dataset.suit = card.suit;
      element.classList.toggle('is-face-down', !card.faceUp);
    }

    function updateDropTargets() {
      const hasSelection = Boolean(state.selected);
      piles.tableauElements.forEach((element, index) => {
        const droppable = hasSelection && canMoveSelectionToTableau(index);
        element.classList.toggle('is-droppable', droppable);
      });
      piles.foundationElements.forEach((element, index) => {
        const droppable = hasSelection
          && state.selected.cards.length === 1
          && !(state.selected.type === 'foundation' && state.selected.index === index)
          && canPlaceOnFoundation(state.selected.cards[0], state.foundations[index]);
        element.classList.toggle('is-droppable', droppable);
      });
    }

    function maybeAwardCompletionReward() {
      if (state.rewardClaimed) {
        return;
      }
      const reward = rewardConfig && rewardConfig.completion ? rewardConfig.completion : null;
      if (!reward) {
        state.rewardClaimed = true;
        return;
      }
      const gachaAmount = Math.max(0, Math.floor(Number(reward.gachaTickets) || 0));
      const bonusAmount = Math.max(0, Math.floor(Number(reward.bonusTicketAmount) || 0));
      if (gachaAmount <= 0 && bonusAmount <= 0) {
        state.rewardClaimed = true;
        return;
      }
      let gachaGained = 0;
      if (gachaAmount > 0) {
        const awardGacha = typeof gainGachaTickets === 'function'
          ? gainGachaTickets
          : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
            ? window.gainGachaTickets
            : null;
        if (typeof awardGacha === 'function') {
          try {
            gachaGained = awardGacha(gachaAmount, { unlockTicketStar: true });
          } catch (error) {
            console.warn('Solitaire: unable to grant gacha tickets', error);
            gachaGained = 0;
          }
        }
      }
      let bonusGained = 0;
      if (bonusAmount > 0 && typeof gainBonusParticulesTickets === 'function') {
        try {
          bonusGained = gainBonusParticulesTickets(bonusAmount);
        } catch (error) {
          console.warn('Solitaire: unable to grant Mach3 tickets', error);
          bonusGained = 0;
        }
      }
      if (!Number.isFinite(gachaGained) || gachaGained <= 0) {
        gachaGained = 0;
      }
      if (!Number.isFinite(bonusGained) || bonusGained <= 0) {
        bonusGained = 0;
      }
      if (gachaGained <= 0 && bonusGained <= 0) {
        state.rewardClaimed = true;
        return;
      }
      state.rewardClaimed = true;
      if (typeof showToast === 'function') {
        const parts = [];
        if (gachaGained > 0) {
          const suffix = gachaGained > 1 ? 's' : '';
          parts.push(translate(
            'scripts.arcade.solitaire.rewards.gacha',
            '{count} ticket{suffix} gacha',
            { count: formatIntegerLocalized(gachaGained), suffix }
          ));
        }
        if (bonusGained > 0) {
          const suffix = bonusGained > 1 ? 's' : '';
          parts.push(translate(
            'scripts.arcade.solitaire.rewards.mach3',
            '{count} ticket{suffix} Mach3',
            { count: formatIntegerLocalized(bonusGained), suffix }
          ));
        }
        if (parts.length) {
          const rewardsText = parts.join(' · ');
          const message = translate(
            'scripts.arcade.solitaire.toast.completion',
            'Solitaire terminé ! Récompenses : {rewards}.',
            { rewards: rewardsText }
          );
          showToast(message);
        }
      }
    }

    function updateCompletionState() {
      const complete = state.foundations.every((pile) => pile.length === 13);
      if (rootElement) {
        rootElement.classList.toggle('solitaire--complete', complete);
      }
      if (complete) {
        maybeAwardCompletionReward();
      }
    }

    function handleStockClick() {
      clearSelection();
      if (state.stock.length > 0) {
        const card = state.stock.pop();
        card.faceUp = true;
        card.pile = 'waste';
        card.pileIndex = null;
        state.waste.push(card);
      } else if (state.waste.length > 0) {
        while (state.waste.length > 0) {
          const card = state.waste.pop();
          card.faceUp = false;
          card.pile = 'stock';
          card.pileIndex = -1;
          state.stock.push(card);
        }
      }
      renderAll();
    }

    function handleCardClick(card, event) {
      if (event && typeof event.preventDefault === 'function') {
        event.preventDefault();
      }

      if (card.pile === 'stock') {
        return;
      }

      if (!card.faceUp) {
        if (card.pile === 'tableau' && isTopCard(card)) {
          card.faceUp = true;
          renderTableauPile(card.pileIndex);
          updateDropTargets();
        }
        return;
      }

      if (event && typeof event.detail === 'number' && event.detail > 1) {
        if (tryAutoFoundation(card)) {
          return;
        }
      }

      if (state.selected && !state.selected.cards.includes(card)) {
        if (card.pile === 'tableau' && isTopCard(card)) {
          if (moveSelectionToTableau(card.pileIndex)) {
            return;
          }
        }
        if (card.pile === 'foundation' && isTopCard(card)) {
          if (moveSelectionToFoundation(card.pileIndex)) {
            return;
          }
        }
      }

      if (state.selected && state.selected.cards.includes(card)) {
        clearSelection();
        return;
      }

      if (card.pile === 'waste') {
        setSelection('waste', null, state.waste.length - 1);
        return;
      }

      if (card.pile === 'foundation') {
        const foundation = state.foundations[card.pileIndex];
        const index = foundation.indexOf(card);
        if (index !== -1) {
          setSelection('foundation', card.pileIndex, index);
        }
        return;
      }

      if (card.pile === 'tableau') {
        const column = state.tableau[card.pileIndex];
        const index = column.indexOf(card);
        if (index !== -1) {
          setSelection('tableau', card.pileIndex, index);
        }
      }
    }

    function handlePileClickForTableau(columnIndex, event) {
      if (event.target !== piles.tableauElements[columnIndex]) {
        return;
      }
      if (state.selected) {
        moveSelectionToTableau(columnIndex);
      } else {
        clearSelection();
      }
    }

    function handlePileClickForFoundation(foundationIndex, event) {
      if (event.target !== piles.foundationElements[foundationIndex]) {
        return;
      }
      if (state.selected) {
        moveSelectionToFoundation(foundationIndex);
      } else {
        clearSelection();
      }
    }

    function canMoveSelectionToTableau(columnIndex) {
      if (!state.selected) {
        return false;
      }
      if (state.selected.type === 'tableau' && state.selected.index === columnIndex) {
        return false;
      }
      return canPlaceSequenceOnTableau(state.selected.cards, state.tableau[columnIndex]);
    }

    function canPlaceSequenceOnTableau(cards, destination) {
      if (!cards || cards.length === 0) {
        return false;
      }
      const firstCard = cards[0];
      if (destination.length === 0) {
        return firstCard.rank === 13;
      }
      const target = destination[destination.length - 1];
      if (!target.faceUp) {
        return false;
      }
      return target.color !== firstCard.color && target.rank === firstCard.rank + 1;
    }

    function canPlaceOnFoundation(card, foundation) {
      if (!card) {
        return false;
      }
      if (foundation.length === 0) {
        return card.rank === 1;
      }
      const topCard = foundation[foundation.length - 1];
      return topCard.suit === card.suit && card.rank === topCard.rank + 1;
    }

    function moveSelectionToTableau(columnIndex) {
      if (!state.selected) {
        return false;
      }
      if (state.selected.type === 'tableau' && state.selected.index === columnIndex) {
        return false;
      }
      const destination = state.tableau[columnIndex];
      if (!canPlaceSequenceOnTableau(state.selected.cards, destination)) {
        return false;
      }
      const source = getPileArray(state.selected.type, state.selected.index);
      if (!source) {
        return false;
      }
      const moving = source.splice(state.selected.startIndex, state.selected.cards.length);
      if (moving.length === 0) {
        return false;
      }
      moving.forEach((card) => {
        card.pile = 'tableau';
        card.pileIndex = columnIndex;
      });
      destination.push(...moving);
      if (state.selected.type === 'tableau') {
        revealTopCard(state.selected.index);
      }
      clearSelection();
      renderAll();
      return true;
    }

    function moveSelectionToFoundation(foundationIndex) {
      if (!state.selected) {
        return false;
      }
      if (state.selected.type === 'foundation' && state.selected.index === foundationIndex) {
        return false;
      }
      if (state.selected.cards.length !== 1) {
        return false;
      }
      const card = state.selected.cards[0];
      const destination = state.foundations[foundationIndex];
      if (!canPlaceOnFoundation(card, destination)) {
        return false;
      }
      const source = getPileArray(state.selected.type, state.selected.index);
      if (!source) {
        return false;
      }
      const removed = source.splice(state.selected.startIndex, 1);
      if (removed.length === 0) {
        return false;
      }
      const movedCard = removed[0];
      movedCard.pile = 'foundation';
      movedCard.pileIndex = foundationIndex;
      destination.push(movedCard);
      if (state.selected.type === 'tableau') {
        revealTopCard(state.selected.index);
      }
      clearSelection();
      renderAll();
      return true;
    }

    function revealTopCard(columnIndex) {
      const column = state.tableau[columnIndex];
      if (!column || column.length === 0) {
        return;
      }
      const topCard = column[column.length - 1];
      if (!topCard.faceUp) {
        topCard.faceUp = true;
      }
    }

    function tryAutoFoundation(card) {
      if (!card.faceUp || !isTopCard(card)) {
        return false;
      }
      const location = getCardLocation(card);
      if (!location) {
        return false;
      }
      for (let foundationIndex = 0; foundationIndex < state.foundations.length; foundationIndex += 1) {
        if (location.type === 'foundation' && location.index === foundationIndex) {
          continue;
        }
        if (canPlaceOnFoundation(card, state.foundations[foundationIndex])) {
          moveCardDirectlyToFoundation(card, location, foundationIndex);
          return true;
        }
      }
      return false;
    }

    function moveCardDirectlyToFoundation(card, location, foundationIndex) {
      const source = getPileArray(location.type, location.index);
      if (!source) {
        return;
      }
      const index = source.indexOf(card);
      if (index !== source.length - 1) {
        return;
      }
      if (!canPlaceOnFoundation(card, state.foundations[foundationIndex])) {
        return;
      }
      source.pop();
      card.pile = 'foundation';
      card.pileIndex = foundationIndex;
      state.foundations[foundationIndex].push(card);
      if (location.type === 'tableau') {
        revealTopCard(location.index);
      }
      clearSelection();
      renderAll();
    }

    function isTopCard(card) {
      if (card.pile === 'tableau') {
        const column = state.tableau[card.pileIndex];
        return Boolean(column && column[column.length - 1] === card);
      }
      if (card.pile === 'waste') {
        return state.waste[state.waste.length - 1] === card;
      }
      if (card.pile === 'foundation') {
        const foundation = state.foundations[card.pileIndex];
        return Boolean(foundation && foundation[foundation.length - 1] === card);
      }
      return false;
    }

    function getCardLocation(card) {
      if (card.pile === 'tableau') {
        const column = state.tableau[card.pileIndex];
        if (!column) {
          return null;
        }
        const index = column.indexOf(card);
        return index === -1 ? null : { type: 'tableau', index: card.pileIndex, position: index };
      }
      if (card.pile === 'waste') {
        const index = state.waste.indexOf(card);
        return index === -1 ? null : { type: 'waste', index: null, position: index };
      }
      if (card.pile === 'foundation') {
        const foundation = state.foundations[card.pileIndex];
        if (!foundation) {
          return null;
        }
        const index = foundation.indexOf(card);
        return index === -1 ? null : { type: 'foundation', index: card.pileIndex, position: index };
      }
      return null;
    }

    function getPileArray(type, index) {
      switch (type) {
        case 'stock':
          return state.stock;
        case 'waste':
          return state.waste;
        case 'foundation':
          return typeof index === 'number' ? state.foundations[index] : null;
        case 'tableau':
          return typeof index === 'number' ? state.tableau[index] : null;
        default:
          return null;
      }
    }

    function setSelection(type, index, startIndex) {
      const source = getPileArray(type, index);
      if (!source) {
        clearSelection();
        return;
      }
      const normalizedStart = Math.max(0, Math.min(startIndex, source.length - 1));
      const cards = source.slice(normalizedStart);
      if (cards.length === 0) {
        clearSelection();
        return;
      }
      if (type === 'tableau') {
        if (!cards[0].faceUp) {
          return;
        }
        const allFaceUp = cards.every((card) => card.faceUp);
        if (!allFaceUp) {
          return;
        }
      }
      clearSelection();
      state.selected = {
        type,
        index,
        startIndex: normalizedStart,
        cards
      };
      cards.forEach((card) => {
        if (card.element) {
          card.element.classList.add('is-selected');
        }
      });
      updateDropTargets();
    }

    function clearSelection() {
      if (state.selected && state.selected.cards) {
        state.selected.cards.forEach((card) => {
          if (card.element) {
            card.element.classList.remove('is-selected');
          }
        });
      }
      state.selected = null;
      updateDropTargets();
    }

    resetButton.addEventListener('click', () => {
      resetGame();
    });

    piles.stockElement.addEventListener('click', (event) => {
      event.preventDefault();
      handleStockClick();
    });

    piles.wasteElement.addEventListener('click', (event) => {
      if (event.target === piles.wasteElement) {
        if (state.waste.length > 0) {
          setSelection('waste', null, state.waste.length - 1);
        } else {
          clearSelection();
        }
      }
    });

    piles.foundationElements.forEach((element, index) => {
      element.addEventListener('click', (event) => {
        handlePileClickForFoundation(index, event);
      });
    });

    piles.tableauElements.forEach((element, index) => {
      element.addEventListener('click', (event) => {
        handlePileClickForTableau(index, event);
      });
    });

    boardElement.addEventListener('click', (event) => {
      if (event.target === boardElement) {
        clearSelection();
      }
    });

    resetGame();
  });

  function setupBoard(boardElement) {
    boardElement.textContent = '';

    const topRow = document.createElement('div');
    topRow.className = 'solitaire__row solitaire__row--top';

    const stockGroup = document.createElement('div');
    stockGroup.className = 'solitaire__group solitaire__group--stock';
    const stockElement = createPileElement('stock');
    const wasteElement = createPileElement('waste');
    stockGroup.append(stockElement, wasteElement);

    const foundationsGroup = document.createElement('div');
    foundationsGroup.className = 'solitaire__group solitaire__group--foundations';
    const foundationElements = [];
    for (let index = 0; index < 4; index += 1) {
      const foundation = createPileElement('foundation');
      foundation.dataset.foundation = String(index);
      foundationsGroup.appendChild(foundation);
      foundationElements.push(foundation);
    }

    topRow.append(stockGroup, foundationsGroup);

    const tableauRow = document.createElement('div');
    tableauRow.className = 'solitaire__row solitaire__row--tableau';
    const tableauElements = [];
    for (let index = 0; index < 7; index += 1) {
      const column = createPileElement('tableau');
      column.dataset.column = String(index);
      tableauRow.appendChild(column);
      tableauElements.push(column);
    }

    boardElement.append(topRow, tableauRow);

    return {
      stockElement,
      wasteElement,
      foundationElements,
      tableauElements
    };
  }

  function createPileElement(type) {
    const element = document.createElement('div');
    element.className = `solitaire-pile solitaire-pile--${type}`;
    element.dataset.pileType = type;
    return element;
  }
})();
