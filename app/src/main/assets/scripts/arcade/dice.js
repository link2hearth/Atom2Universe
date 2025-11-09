(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const DICE_SECTION = document.getElementById('dice');
  if (!DICE_SECTION) {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;

  const DEFAULT_SETTINGS = Object.freeze({
    diceCount: 5,
    faces: 6,
    rollsPerTurn: 3,
    fullHouseScore: 25,
    smallStraightScore: 30,
    largeStraightScore: 40,
    yahtzeeScore: 50,
    bonusThreshold: 63,
    bonusValue: 35
  });

  function translate(key, fallback, params) {
    if (!key || typeof key !== 'string') {
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

    if (!translator) {
      return fallback;
    }

    try {
      const result = translator(key, params);
      if (typeof result === 'string') {
        const trimmed = result.trim();
        if (trimmed && trimmed !== key) {
          return trimmed;
        }
      }
    } catch (error) {
      console.warn('Dice translation error for key', key, error);
    }
    return fallback;
  }

  function resolveNumericSetting(value, fallback, { min = Number.NEGATIVE_INFINITY, max = Number.POSITIVE_INFINITY } = {}) {
    const parsed = typeof value === 'number' ? value : Number.parseInt(value, 10);
    if (!Number.isFinite(parsed)) {
      return fallback;
    }
    const clamped = Math.min(Math.max(parsed, min), max);
    if (!Number.isFinite(clamped)) {
      return fallback;
    }
    return clamped;
  }

  const resolvedConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.dice
    ? GLOBAL_CONFIG.arcade.dice
    : null;

  const settings = {
    diceCount: resolveNumericSetting(resolvedConfig?.diceCount, DEFAULT_SETTINGS.diceCount, { min: 1, max: 10 }),
    faces: resolveNumericSetting(resolvedConfig?.faces, DEFAULT_SETTINGS.faces, { min: 4, max: 12 }),
    rollsPerTurn: resolveNumericSetting(resolvedConfig?.rollsPerTurn, DEFAULT_SETTINGS.rollsPerTurn, { min: 1, max: 6 }),
    fullHouseScore: resolveNumericSetting(resolvedConfig?.fullHouseScore, DEFAULT_SETTINGS.fullHouseScore, { min: 0, max: 100 }),
    smallStraightScore: resolveNumericSetting(resolvedConfig?.smallStraightScore, DEFAULT_SETTINGS.smallStraightScore, { min: 0, max: 100 }),
    largeStraightScore: resolveNumericSetting(resolvedConfig?.largeStraightScore, DEFAULT_SETTINGS.largeStraightScore, { min: 0, max: 120 }),
    yahtzeeScore: resolveNumericSetting(resolvedConfig?.yahtzeeScore, DEFAULT_SETTINGS.yahtzeeScore, { min: 0, max: 150 }),
    bonusThreshold: resolveNumericSetting(resolvedConfig?.bonusThreshold, DEFAULT_SETTINGS.bonusThreshold, { min: 0, max: 200 }),
    bonusValue: resolveNumericSetting(resolvedConfig?.bonusValue, DEFAULT_SETTINGS.bonusValue, { min: 0, max: 200 })
  };

  const diceButtons = Array.from(DICE_SECTION.querySelectorAll('.dice-die'));
  const activeDiceCount = Math.max(1, Math.min(settings.diceCount, diceButtons.length || settings.diceCount));

  const elements = {
    rollButton: DICE_SECTION.querySelector('#diceRollButton'),
    clearButton: DICE_SECTION.querySelector('#diceClearHoldsButton'),
    newGameButton: DICE_SECTION.querySelector('#diceNewGameButton'),
    rollsValue: DICE_SECTION.querySelector('#diceRollsValue'),
    status: DICE_SECTION.querySelector('#diceStatus'),
    modeSelect: DICE_SECTION.querySelector('#diceModeSelect'),
    scoreHeader: DICE_SECTION.querySelector('#diceScoreHeader'),
    scoreBody: DICE_SECTION.querySelector('#diceScoreBody'),
    instructions: DICE_SECTION.querySelector('#diceModeHint'),
    scorecard: DICE_SECTION.querySelector('#diceScorecard')
  };

  if (!elements.rollButton || !elements.clearButton || !elements.newGameButton || !elements.rollsValue || !elements.status || !elements.scoreHeader || !elements.scoreBody) {
    return;
  }

  const numberFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;

  function formatNumber(value) {
    if (!Number.isFinite(value)) {
      return '0';
    }
    return numberFormatter ? numberFormatter.format(value) : String(Math.round(value));
  }

  const placeholderEmpty = translate('index.sections.dice.scorecard.placeholders.empty', '—');

  const PLAYER_MODE_PRESETS = [
    { value: 'solo', humans: 1, ai: 1, labelKey: 'index.sections.dice.scorecard.modes.solo', fallback: '1 joueur + IA' },
    { value: '2', humans: 2, ai: 0, labelKey: 'index.sections.dice.scorecard.modes.twoPlayers', fallback: '2 joueurs' },
    { value: '3', humans: 3, ai: 0, labelKey: 'index.sections.dice.scorecard.modes.threePlayers', fallback: '3 joueurs' },
    { value: '4', humans: 4, ai: 0, labelKey: 'index.sections.dice.scorecard.modes.fourPlayers', fallback: '4 joueurs' }
  ];

  const SCORECARD_LAYOUT = [
    { type: 'section', id: 'upper', labelKey: 'index.sections.dice.scorecard.sections.upper', fallback: 'Section supérieure' },
    { type: 'category', id: 'ones', face: 1, section: 'upper', labelKey: 'index.sections.dice.scorecard.categories.ones', fallback: '1', hintKey: 'index.sections.dice.scorecard.hints.ones', hintFallback: '[total de 1]' },
    { type: 'category', id: 'twos', face: 2, section: 'upper', labelKey: 'index.sections.dice.scorecard.categories.twos', fallback: '2', hintKey: 'index.sections.dice.scorecard.hints.twos', hintFallback: '[total de 2]' },
    { type: 'category', id: 'threes', face: 3, section: 'upper', labelKey: 'index.sections.dice.scorecard.categories.threes', fallback: '3', hintKey: 'index.sections.dice.scorecard.hints.threes', hintFallback: '[total de 3]' },
    { type: 'category', id: 'fours', face: 4, section: 'upper', labelKey: 'index.sections.dice.scorecard.categories.fours', fallback: '4', hintKey: 'index.sections.dice.scorecard.hints.fours', hintFallback: '[total de 4]' },
    { type: 'category', id: 'fives', face: 5, section: 'upper', labelKey: 'index.sections.dice.scorecard.categories.fives', fallback: '5', hintKey: 'index.sections.dice.scorecard.hints.fives', hintFallback: '[total de 5]' },
    { type: 'category', id: 'sixes', face: 6, section: 'upper', labelKey: 'index.sections.dice.scorecard.categories.sixes', fallback: '6', hintKey: 'index.sections.dice.scorecard.hints.sixes', hintFallback: '[total de 6]' },
    { type: 'computed', id: 'upperBonus', section: 'upper', labelKey: 'index.sections.dice.scorecard.computed.upperBonus', fallback: 'Bonus si ≥63 (35)' },
    { type: 'computed', id: 'upperTotal', section: 'upper', labelKey: 'index.sections.dice.scorecard.computed.upperTotal', fallback: 'Total supérieur' },
    { type: 'section', id: 'lower', labelKey: 'index.sections.dice.scorecard.sections.lower', fallback: 'Section inférieure' },
    { type: 'category', id: 'threeKind', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.threeKind', fallback: 'Brelan', hintKey: 'index.sections.dice.scorecard.hints.threeKind', hintFallback: '[3 dés identiques]' },
    { type: 'category', id: 'fourKind', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.fourKind', fallback: 'Carré', hintKey: 'index.sections.dice.scorecard.hints.fourKind', hintFallback: '[4 dés identiques]' },
    { type: 'category', id: 'fullHouse', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.fullHouse', fallback: 'Full House', hintKey: 'index.sections.dice.scorecard.hints.fullHouse', hintFallback: '[25 points]' },
    { type: 'category', id: 'smallStraight', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.smallStraight', fallback: 'Petite suite', hintKey: 'index.sections.dice.scorecard.hints.smallStraight', hintFallback: '[4 dés qui se suivent]' },
    { type: 'category', id: 'largeStraight', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.largeStraight', fallback: 'Grande suite', hintKey: 'index.sections.dice.scorecard.hints.largeStraight', hintFallback: '[5 dés qui se suivent]' },
    { type: 'category', id: 'yahtzee', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.yahtzee', fallback: 'Yams', hintKey: 'index.sections.dice.scorecard.hints.yahtzee', hintFallback: '[5 dés identiques]' },
    { type: 'category', id: 'chance', section: 'lower', labelKey: 'index.sections.dice.scorecard.categories.chance', fallback: 'Chance', hintKey: 'index.sections.dice.scorecard.hints.chance', hintFallback: '[total des dés]' },
    { type: 'computed', id: 'lowerTotal', section: 'lower', labelKey: 'index.sections.dice.scorecard.computed.lowerTotal', fallback: 'Total inférieur' },
    { type: 'computed', id: 'grandTotal', section: 'lower', labelKey: 'index.sections.dice.scorecard.computed.grandTotal', fallback: 'Total' }
  ];

  const CATEGORY_IDS = SCORECARD_LAYOUT.filter(row => row.type === 'category').map(row => row.id);
  const CATEGORY_COUNT = CATEGORY_IDS.length;
  const UPPER_CATEGORY_IDS = SCORECARD_LAYOUT.filter(row => row.type === 'category' && row.section === 'upper').map(row => row.id);
  const LOWER_CATEGORY_IDS = SCORECARD_LAYOUT.filter(row => row.type === 'category' && row.section === 'lower').map(row => row.id);

  const AI_CATEGORY_ORDER = [
    'yahtzee',
    'largeStraight',
    'smallStraight',
    'fourKind',
    'fullHouse',
    'threeKind',
    'chance',
    'sixes',
    'fives',
    'fours',
    'threes',
    'twos',
    'ones'
  ];

  const AI_CATEGORY_PRIORITY = new Map(AI_CATEGORY_ORDER.map((id, index) => [id, index]));
  const CATEGORY_FACE_MAP = {
    ones: 1,
    twos: 2,
    threes: 3,
    fours: 4,
    fives: 5,
    sixes: 6
  };

  const AI_REWARD_TICKETS = 5;

  const state = {
    mode: 'solo',
    players: [],
    aiIndex: -1,
    assignments: [],
    totals: [],
    diceValues: Array.from({ length: activeDiceCount }, () => 1),
    heldDice: Array.from({ length: activeDiceCount }, () => false),
    rollsLeft: settings.rollsPerTurn,
    hasRolled: false,
    currentPlayerIndex: 0,
    gameOver: false,
    isAiTurn: false,
    completedCount: 0,
    currentScores: Object.create(null)
  };

  const scoreCells = {
    categories: new Map(),
    computed: new Map(),
    headerCells: []
  };

  const categoryLabels = new Map();

  diceButtons.forEach((button, index) => {
    if (!button) {
      return;
    }
    if (index >= activeDiceCount) {
      button.hidden = true;
      button.setAttribute('aria-hidden', 'true');
      button.disabled = true;
    }
  });

  function setInstructions(text) {
    if (elements.instructions) {
      elements.instructions.textContent = text;
    }
  }

  function setStatus(key, fallback, params) {
    if (!elements.status) {
      return;
    }
    const message = translate(key, fallback, params);
    elements.status.textContent = message;
  }

  function buildScorecardTable() {
    scoreCells.categories.clear();
    scoreCells.computed.clear();
    scoreCells.headerCells = [];
    categoryLabels.clear();

    if (elements.scoreHeader) {
      elements.scoreHeader.innerHTML = '';
      const comboHeader = document.createElement('th');
      comboHeader.scope = 'col';
      comboHeader.textContent = translate('index.sections.dice.scorecard.headers.category', 'Combinaison');
      elements.scoreHeader.appendChild(comboHeader);
      state.players.forEach((player, index) => {
        const th = document.createElement('th');
        th.scope = 'col';
        th.dataset.playerIndex = String(index);
        th.textContent = player.name;
        elements.scoreHeader.appendChild(th);
        scoreCells.headerCells.push(th);
      });
    }

    if (!elements.scoreBody) {
      return;
    }

    elements.scoreBody.innerHTML = '';

    SCORECARD_LAYOUT.forEach(row => {
      if (row.type === 'section') {
        const tr = document.createElement('tr');
        tr.classList.add('dice-scorecard__section-row');
        const th = document.createElement('th');
        th.colSpan = state.players.length + 1;
        th.textContent = translate(row.labelKey, row.fallback);
        tr.appendChild(th);
        elements.scoreBody.appendChild(tr);
        return;
      }

      if (row.type === 'category') {
        const tr = document.createElement('tr');
        tr.dataset.categoryId = row.id;
        const th = document.createElement('th');
        th.scope = 'row';
        const labelSpan = document.createElement('span');
        labelSpan.textContent = translate(row.labelKey, row.fallback);
        labelSpan.className = 'dice-scorecard__label';
        categoryLabels.set(row.id, labelSpan.textContent);
        th.appendChild(labelSpan);
        if (row.hintKey || row.hintFallback) {
          const hintSpan = document.createElement('span');
          hintSpan.className = 'dice-scorecard__hint';
          hintSpan.textContent = translate(row.hintKey, row.hintFallback, { face: row.face ? formatNumber(row.face) : undefined });
          th.appendChild(hintSpan);
        }
        tr.appendChild(th);

        const cellList = [];
        state.players.forEach((player, playerIndex) => {
          const td = document.createElement('td');
          td.dataset.playerIndex = String(playerIndex);
          const container = document.createElement('div');
          container.className = 'dice-scorecard__cell';
          container.dataset.state = 'empty';
          const valueSpan = document.createElement('span');
          valueSpan.className = 'dice-scorecard__cell-value';
          valueSpan.textContent = placeholderEmpty;
          const previewSpan = document.createElement('span');
          previewSpan.className = 'dice-scorecard__preview';
          previewSpan.textContent = '';
          container.appendChild(valueSpan);
          container.appendChild(previewSpan);

          const actionButton = document.createElement('button');
          actionButton.type = 'button';
          actionButton.className = 'dice-scorecard__action';
          actionButton.textContent = '✓';
          actionButton.disabled = true;
          actionButton.hidden = player.type !== 'human';
          if (player.type === 'human') {
            actionButton.addEventListener('click', () => handleCategorySelection(row.id, playerIndex));
          }
          container.appendChild(actionButton);

          td.appendChild(container);
          tr.appendChild(td);
          cellList.push({ td, container, value: valueSpan, preview: previewSpan, action: actionButton });
        });

        scoreCells.categories.set(row.id, cellList);
        elements.scoreBody.appendChild(tr);
        return;
      }

      if (row.type === 'computed') {
        const tr = document.createElement('tr');
        tr.dataset.computedId = row.id;
        const th = document.createElement('th');
        th.scope = 'row';
        th.textContent = translate(row.labelKey, row.fallback);
        tr.appendChild(th);
        const cellList = [];
        state.players.forEach((player, playerIndex) => {
          const td = document.createElement('td');
          td.dataset.playerIndex = String(playerIndex);
          const valueSpan = document.createElement('span');
          valueSpan.className = 'dice-scorecard__cell-value';
          valueSpan.textContent = '0';
          td.appendChild(valueSpan);
          tr.appendChild(td);
          cellList.push({ td, value: valueSpan });
        });
        scoreCells.computed.set(row.id, cellList);
        elements.scoreBody.appendChild(tr);
      }
    });

    updateCategoryButtons();
  }

  function updateRollsDisplay(forcedValue) {
    const value = Number.isFinite(forcedValue) ? forcedValue : state.rollsLeft;
    elements.rollsValue.textContent = formatNumber(Math.max(0, value));
  }

  function updateDiceDisplay(animatedIndices) {
    const animatedSet = Array.isArray(animatedIndices) ? new Set(animatedIndices) : null;
    diceButtons.forEach((button, index) => {
      if (!button) {
        return;
      }
      if (index >= activeDiceCount) {
        button.hidden = true;
        button.disabled = true;
        return;
      }
      button.hidden = false;
      const value = Number(state.diceValues[index]) || 1;
      button.dataset.value = String(value);
      button.setAttribute('data-value', String(value));
      button.setAttribute('aria-pressed', state.heldDice[index] ? 'true' : 'false');
      const ariaKey = state.heldDice[index] ? 'index.sections.dice.die.held' : 'index.sections.dice.die.available';
      const fallback = state.heldDice[index]
        ? `Dé ${index + 1} affichant ${value}, mis de côté.`
        : `Dé ${index + 1} affichant ${value}.`;
      button.setAttribute('aria-label', translate(ariaKey, fallback, { index: String(index + 1), value: formatNumber(value) }));
      button.disabled = state.gameOver || state.isAiTurn || !state.hasRolled;
      if (animatedSet && animatedSet.has(index)) {
        button.classList.add('dice-die--rolling');
        setTimeout(() => {
          button.classList.remove('dice-die--rolling');
        }, 520);
      }
    });
  }

  function updateButtonsState() {
    const canRoll = !state.gameOver && !state.isAiTurn && state.rollsLeft > 0;
    elements.rollButton.disabled = !canRoll;
    const hasHold = state.heldDice.some(Boolean);
    elements.clearButton.disabled = state.gameOver || state.isAiTurn || !hasHold;
    diceButtons.forEach((button, index) => {
      if (!button || index >= activeDiceCount) {
        return;
      }
      button.disabled = state.gameOver || state.isAiTurn || !state.hasRolled;
    });
  }

  function setActivePlayerColumn(playerIndex) {
    scoreCells.headerCells.forEach((cell, index) => {
      if (!cell) {
        return;
      }
      if (index === playerIndex && !state.gameOver) {
        cell.dataset.active = 'true';
      } else {
        delete cell.dataset.active;
      }
    });

    scoreCells.categories.forEach(cellList => {
      cellList.forEach((cell, index) => {
        if (!cell || !cell.td) {
          return;
        }
        if (index === playerIndex && !state.gameOver) {
          cell.td.dataset.active = 'true';
        } else {
          delete cell.td.dataset.active;
        }
      });
    });

    scoreCells.computed.forEach(cellList => {
      cellList.forEach((cell, index) => {
        if (!cell || !cell.td) {
          return;
        }
        if (index === playerIndex && !state.gameOver) {
          cell.td.dataset.active = 'true';
        } else if (!cell.td.dataset.winner) {
          delete cell.td.dataset.active;
        }
      });
    });
  }

  function clearWinnerHighlights() {
    scoreCells.computed.forEach(cellList => {
      cellList.forEach(cell => {
        if (cell && cell.td) {
          delete cell.td.dataset.winner;
        }
      });
    });
  }

  function updateCategoryButtons() {
    scoreCells.categories.forEach((cellList, categoryId) => {
      const label = categoryLabels.get(categoryId) || categoryId;
      cellList.forEach((cell, playerIndex) => {
        if (!cell) {
          return;
        }
        const assignedScore = state.assignments[playerIndex]?.[categoryId];
        const player = state.players[playerIndex];
        if (Number.isFinite(assignedScore)) {
          cell.container.dataset.state = 'assigned';
          cell.value.textContent = formatNumber(assignedScore);
          cell.preview.textContent = '';
          if (cell.action) {
            cell.action.disabled = true;
            cell.action.setAttribute(
              'aria-label',
              translate('index.sections.dice.scorecard.actions.locked', '{category} verrouillée avec {score} points.', {
                category: label,
                score: formatNumber(assignedScore)
              })
            );
          }
          return;
        }

        if (playerIndex === state.currentPlayerIndex && player?.type === 'human' && state.hasRolled) {
          const previewScore = Number.isFinite(state.currentScores[categoryId]) ? state.currentScores[categoryId] : 0;
          cell.container.dataset.state = 'preview';
          cell.value.textContent = placeholderEmpty;
          cell.preview.textContent = formatNumber(previewScore);
          if (cell.action) {
            cell.action.disabled = false;
            cell.action.hidden = false;
            cell.action.setAttribute(
              'aria-label',
              translate('index.sections.dice.scorecard.actions.select', 'Valider {score} points dans {category}.', {
                score: formatNumber(previewScore),
                category: label
              })
            );
          }
        } else {
          cell.container.dataset.state = 'empty';
          cell.value.textContent = placeholderEmpty;
          cell.preview.textContent = '';
          if (cell.action) {
            cell.action.disabled = true;
            if (player?.type !== 'human') {
              cell.action.hidden = true;
            } else {
              cell.action.hidden = false;
              cell.action.setAttribute(
                'aria-label',
                translate('index.sections.dice.scorecard.actions.disabled', 'Lancez avant de choisir une combinaison.')
              );
            }
          }
        }
      });
    });
  }

  function computeAllCategoryScores(values) {
    const result = Object.create(null);
    const counts = Array.from({ length: settings.faces + 1 }, () => 0);
    let total = 0;
    values.forEach(value => {
      const face = Number(value) || 0;
      if (counts[face] != null) {
        counts[face] += 1;
      }
      total += face;
    });
    const uniqueFaces = [];
    for (let face = 1; face < counts.length; face += 1) {
      if (counts[face] > 0) {
        uniqueFaces.push(face);
      }
    }

    CATEGORY_IDS.forEach(categoryId => {
      result[categoryId] = computeCategoryScore(categoryId, values, counts, uniqueFaces, total);
    });
    return result;
  }

  function computeCategoryScore(categoryId, values, counts, uniqueFaces, total) {
    if (CATEGORY_FACE_MAP[categoryId]) {
      const face = CATEGORY_FACE_MAP[categoryId];
      return (counts?.[face] || 0) * face;
    }

    switch (categoryId) {
      case 'threeKind': {
        const hasThree = counts.some(count => count >= 3);
        return hasThree ? total : 0;
      }
      case 'fourKind': {
        const hasFour = counts.some(count => count >= 4);
        return hasFour ? total : 0;
      }
      case 'fullHouse': {
        let hasThree = false;
        let hasTwo = false;
        for (let face = 1; face < counts.length; face += 1) {
          const count = counts[face];
          if (count === 3) {
            hasThree = true;
          } else if (count === 2) {
            hasTwo = true;
          }
        }
        return hasThree && hasTwo ? settings.fullHouseScore : 0;
      }
      case 'smallStraight': {
        return hasStraight(uniqueFaces, 4) ? settings.smallStraightScore : 0;
      }
      case 'largeStraight': {
        return hasStraight(uniqueFaces, Math.min(activeDiceCount, 5)) ? settings.largeStraightScore : 0;
      }
      case 'yahtzee': {
        const hasAll = counts.some(count => count === activeDiceCount);
        return hasAll ? settings.yahtzeeScore : 0;
      }
      case 'chance': {
        return total;
      }
      default:
        return 0;
    }
  }

  function hasStraight(uniqueFaces, length) {
    if (!Array.isArray(uniqueFaces) || uniqueFaces.length < length) {
      return false;
    }
    let streak = 1;
    for (let index = 1; index < uniqueFaces.length; index += 1) {
      if (uniqueFaces[index] === uniqueFaces[index - 1] + 1) {
        streak += 1;
        if (streak >= length) {
          return true;
        }
      } else {
        streak = 1;
      }
    }
    return false;
  }

  function handleRoll() {
    if (state.gameOver || state.isAiTurn || state.rollsLeft <= 0) {
      return;
    }
    const rolledIndices = [];
    for (let index = 0; index < activeDiceCount; index += 1) {
      if (state.heldDice[index]) {
        continue;
      }
      const value = Math.floor(Math.random() * settings.faces) + 1;
      state.diceValues[index] = value;
      rolledIndices.push(index);
    }
    state.hasRolled = true;
    state.rollsLeft = Math.max(0, state.rollsLeft - 1);
    updateDiceDisplay(rolledIndices);
    updateRollsDisplay();
    state.currentScores = computeAllCategoryScores(state.diceValues);
    updateCategoryButtons();
    updateButtonsState();
    const player = state.players[state.currentPlayerIndex];
    if (!player) {
      return;
    }
    if (state.rollsLeft > 0) {
      setStatus(
        'index.sections.dice.status.afterRoll',
        'Choisissez une combinaison pour {player} ou relancez ({rolls} lancers restants).',
        { player: player.name, rolls: formatNumber(state.rollsLeft) }
      );
    } else {
      setStatus(
        'index.sections.dice.status.lastRoll',
        'Plus de lancers restants. Choisissez une combinaison pour {player}.',
        { player: player.name }
      );
    }
  }

  function handleClearHolds() {
    if (state.gameOver || state.isAiTurn) {
      return;
    }
    if (!state.heldDice.some(Boolean)) {
      return;
    }
    state.heldDice.fill(false);
    updateDiceDisplay();
    updateButtonsState();
    setStatus('index.sections.dice.status.holdsCleared', 'Tous les dés ont été libérés.');
  }

  function handleDieToggle(index) {
    if (state.gameOver || state.isAiTurn) {
      return;
    }
    if (!state.hasRolled) {
      setStatus('index.sections.dice.status.needRoll', 'Lancez au moins une fois avant de marquer.');
      return;
    }
    if (!state.heldDice[index]) {
      state.heldDice[index] = true;
      setStatus('index.sections.dice.status.dieHeld', 'Dé {index} mis de côté.', { index: String(index + 1) });
    } else {
      state.heldDice[index] = false;
      setStatus('index.sections.dice.status.dieReleased', 'Dé {index} relâché.', { index: String(index + 1) });
    }
    updateDiceDisplay();
    updateButtonsState();
  }

  function handleCategorySelection(categoryId, playerIndex) {
    if (state.gameOver || playerIndex !== state.currentPlayerIndex) {
      return;
    }
    const player = state.players[playerIndex];
    if (!player || player.type !== 'human') {
      return;
    }
    if (!state.hasRolled) {
      setStatus('index.sections.dice.status.needRoll', 'Lancez au moins une fois avant de marquer.');
      return;
    }
    if (state.assignments[playerIndex]?.[categoryId] != null) {
      return;
    }
    const score = Number.isFinite(state.currentScores[categoryId])
      ? state.currentScores[categoryId]
      : (computeAllCategoryScores(state.diceValues)[categoryId] || 0);
    applyCategoryScore(playerIndex, categoryId, score);
    setStatus(
      'index.sections.dice.status.categoryChosen',
      '{player} inscrit {score} points dans {category}.',
      { player: player.name, score: formatNumber(score), category: getCategoryText(categoryId) }
    );
    advanceTurn();
  }

  function applyCategoryScore(playerIndex, categoryId, score) {
    if (!state.assignments[playerIndex]) {
      state.assignments[playerIndex] = Object.create(null);
    }
    state.assignments[playerIndex][categoryId] = Number(score) || 0;
    state.completedCount += 1;
    const cellList = scoreCells.categories.get(categoryId);
    if (cellList && cellList[playerIndex]) {
      const cell = cellList[playerIndex];
      cell.container.dataset.state = 'assigned';
      cell.value.textContent = formatNumber(state.assignments[playerIndex][categoryId]);
      cell.preview.textContent = '';
      if (cell.action) {
        cell.action.disabled = true;
      }
    }
    updateTotalsForPlayer(playerIndex);
    state.hasRolled = false;
    state.rollsLeft = 0;
    state.currentScores = Object.create(null);
    updateRollsDisplay();
    updateButtonsState();
    updateCategoryButtons();
  }

  function updateTotalsForPlayer(playerIndex) {
    const assignments = state.assignments[playerIndex] || {};
    let upperSum = 0;
    UPPER_CATEGORY_IDS.forEach(id => {
      upperSum += Number(assignments[id]) || 0;
    });
    const bonus = upperSum >= settings.bonusThreshold ? settings.bonusValue : 0;
    let lowerSum = 0;
    LOWER_CATEGORY_IDS.forEach(id => {
      lowerSum += Number(assignments[id]) || 0;
    });
    const grandTotal = upperSum + bonus + lowerSum;
    state.totals[playerIndex] = grandTotal;
    updateComputedCell('upperBonus', playerIndex, bonus);
    updateComputedCell('upperTotal', playerIndex, upperSum + bonus);
    updateComputedCell('lowerTotal', playerIndex, lowerSum);
    updateComputedCell('grandTotal', playerIndex, grandTotal);
  }

  function updateComputedCell(id, playerIndex, value) {
    const row = scoreCells.computed.get(id);
    if (!row || !row[playerIndex]) {
      return;
    }
    row[playerIndex].value.textContent = formatNumber(value);
    if (!state.gameOver && row[playerIndex].td) {
      delete row[playerIndex].td.dataset.winner;
    }
  }

  function chooseBestCategoryForPlayer(playerIndex, scores) {
    const assignments = state.assignments[playerIndex] || {};
    let best = null;
    CATEGORY_IDS.forEach(categoryId => {
      if (assignments[categoryId] != null) {
        return;
      }
      const score = Number(scores?.[categoryId]) || 0;
      if (!best || score > best.score) {
        best = { categoryId, score };
      } else if (best && score === best.score) {
        if (compareCategoryPriority(categoryId, best.categoryId) < 0) {
          best = { categoryId, score };
        }
      }
    });
    return best;
  }

  function compareCategoryPriority(a, b) {
    const rankA = AI_CATEGORY_PRIORITY.has(a) ? AI_CATEGORY_PRIORITY.get(a) : CATEGORY_IDS.indexOf(a);
    const rankB = AI_CATEGORY_PRIORITY.has(b) ? AI_CATEGORY_PRIORITY.get(b) : CATEGORY_IDS.indexOf(b);
    return (rankA ?? Number.MAX_SAFE_INTEGER) - (rankB ?? Number.MAX_SAFE_INTEGER);
  }

  function applyDiceValues(values) {
    for (let index = 0; index < activeDiceCount; index += 1) {
      const value = Number(values[index]);
      state.diceValues[index] = Number.isFinite(value) && value >= 1 && value <= settings.faces ? value : 1;
      state.heldDice[index] = false;
    }
    updateDiceDisplay();
    updateButtonsState();
  }

  function wait(ms) {
    return new Promise(resolve => {
      setTimeout(resolve, ms);
    });
  }

  async function playAiTurn() {
    const playerIndex = state.currentPlayerIndex;
    const player = state.players[playerIndex];
    if (!player || player.type !== 'ai') {
      return;
    }

    const attempts = [];
    const attemptCount = Math.max(1, settings.rollsPerTurn);
    for (let attempt = 0; attempt < attemptCount; attempt += 1) {
      const hand = Array.from({ length: activeDiceCount }, () => Math.floor(Math.random() * settings.faces) + 1);
      const scores = computeAllCategoryScores(hand);
      const choice = chooseBestCategoryForPlayer(playerIndex, scores);
      attempts.push({ hand, scores, choice });
    }

    let chosen = attempts.find(entry => entry.choice) || attempts[0];
    attempts.forEach(entry => {
      if (!entry.choice) {
        return;
      }
      if (!chosen.choice || entry.choice.score > chosen.choice.score) {
        chosen = entry;
      } else if (chosen.choice && entry.choice.score === chosen.choice.score) {
        if (compareCategoryPriority(entry.choice.categoryId, chosen.choice.categoryId) < 0) {
          chosen = entry;
        }
      }
    });

    const rollAnimationDelay = Math.min(600, 260 + 120 * activeDiceCount);
    for (let attempt = 0; attempt < attempts.length; attempt += 1) {
      applyDiceValues(attempts[attempt].hand);
      updateDiceDisplay(Array.from({ length: activeDiceCount }, (_, index) => index));
      updateRollsDisplay(settings.rollsPerTurn - attempt - 1);
      await wait(rollAnimationDelay);
    }

    const finalAttempt = chosen || attempts[attempts.length - 1];
    applyDiceValues(finalAttempt.hand);
    state.hasRolled = true;
    state.rollsLeft = 0;
    state.currentScores = finalAttempt.scores || computeAllCategoryScores(finalAttempt.hand);
    updateDiceDisplay();
    updateRollsDisplay();
    updateCategoryButtons();
    await wait(rollAnimationDelay);

    const choice = finalAttempt.choice || chooseBestCategoryForPlayer(playerIndex, state.currentScores);
    if (choice) {
      applyCategoryScore(playerIndex, choice.categoryId, choice.score);
      setStatus(
        'index.sections.dice.status.aiChoice',
        '{player} valide {category} pour {score} points.',
        { player: player.name, category: getCategoryText(choice.categoryId), score: formatNumber(choice.score) }
      );
      await wait(320);
    }
    advanceTurn();
  }

  function getCategoryText(categoryId) {
    return categoryLabels.get(categoryId) || categoryId;
  }

  function formatWinnerList(names) {
    if (!Array.isArray(names) || names.length === 0) {
      return '';
    }
    if (names.length === 1) {
      return names[0];
    }
    const conjunction = translate('index.sections.dice.status.and', 'et');
    if (names.length === 2) {
      return `${names[0]} ${conjunction} ${names[1]}`;
    }
    return `${names.slice(0, -1).join(', ')} ${conjunction} ${names[names.length - 1]}`;
  }

  function advanceTurn() {
    if (isGameComplete()) {
      endGame();
      return;
    }
    const nextIndex = (state.currentPlayerIndex + 1) % state.players.length;
    startTurn(nextIndex);
  }

  function isGameComplete() {
    return state.completedCount >= CATEGORY_COUNT * state.players.length;
  }

  function endGame() {
    state.gameOver = true;
    state.isAiTurn = false;
    updateButtonsState();
    diceButtons.forEach((button, index) => {
      if (!button || index >= activeDiceCount) {
        return;
      }
      button.disabled = true;
    });
    setActivePlayerColumn(-1);

    if (!state.totals.length) {
      return;
    }
    const highest = state.totals.reduce((max, value) => Math.max(max, Number(value) || 0), 0);
    const winners = [];
    state.totals.forEach((total, index) => {
      if (Number(total) === highest) {
        winners.push(index);
      }
    });

    const grandTotalCells = scoreCells.computed.get('grandTotal');
    if (grandTotalCells) {
      grandTotalCells.forEach((cell, index) => {
        if (!cell || !cell.td) {
          return;
        }
        if (winners.includes(index)) {
          cell.td.dataset.winner = 'true';
        } else {
          delete cell.td.dataset.winner;
        }
      });
    }

    const winnerNames = winners.map(index => state.players[index]?.name || '');
    const winnersLabel = formatWinnerList(winnerNames);
    if (winners.length > 1) {
      setStatus(
        'index.sections.dice.status.gameOverTie',
        'Partie terminée ! Égalité entre {players} ({score} points).',
        { players: winnersLabel, score: formatNumber(highest) }
      );
    } else {
      setStatus(
        'index.sections.dice.status.gameOverSingle',
        'Partie terminée ! {winner} l’emporte avec {score} points.',
        { winner: winnersLabel, score: formatNumber(highest) }
      );
    }

    if (state.mode === 'solo' && state.aiIndex >= 0) {
      const humanIndex = state.players.findIndex(player => player.type === 'human');
      if (humanIndex >= 0 && (state.totals[humanIndex] || 0) > (state.totals[state.aiIndex] || 0)) {
        grantAiVictoryReward();
      }
    }
  }

  function grantAiVictoryReward() {
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      return;
    }
    let gained = 0;
    try {
      gained = awardGacha(AI_REWARD_TICKETS, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Dice: unable to grant gacha tickets', error);
      gained = 0;
    }
    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }
    const suffix = gained > 1 ? 's' : '';
    const message = translate(
      'scripts.arcade.dice.rewards.vsAiVictory',
      'Victoire contre l’IA ! +{count} ticket{suffix} gacha.',
      { count: formatNumber(gained), suffix }
    );
    if (typeof showToast === 'function') {
      showToast(message);
    } else if (typeof window !== 'undefined' && typeof window.showToast === 'function') {
      window.showToast(message);
    }
  }

  function startTurn(playerIndex) {
    const player = state.players[playerIndex];
    if (!player) {
      return;
    }
    state.currentPlayerIndex = playerIndex;
    state.rollsLeft = settings.rollsPerTurn;
    state.hasRolled = false;
    state.isAiTurn = player.type === 'ai';
    state.currentScores = Object.create(null);
    state.heldDice.fill(false);
    updateDiceDisplay();
    updateRollsDisplay();
    updateCategoryButtons();
    updateButtonsState();
    setActivePlayerColumn(playerIndex);
    if (player.type === 'ai') {
      setStatus('index.sections.dice.status.waitAi', '{player} (IA) réfléchit…', { player: player.name });
      playAiTurn();
    } else {
      setStatus(
        'index.sections.dice.status.turnStart',
        'Au tour de {player}. {rolls} lancers disponibles.',
        { player: player.name, rolls: formatNumber(state.rollsLeft) }
      );
    }
  }

  function startNewGame(modeValue) {
    const preset = PLAYER_MODE_PRESETS.find(entry => entry.value === modeValue) || PLAYER_MODE_PRESETS[0];
    state.mode = preset.value;
    if (elements.modeSelect && elements.modeSelect.value !== preset.value) {
      elements.modeSelect.value = preset.value;
    }

    const players = [];
    for (let index = 0; index < preset.humans; index += 1) {
      const name = translate('index.sections.dice.scorecard.players.human', 'Joueur {number}', { number: formatNumber(index + 1) });
      players.push({ name, type: 'human' });
    }
    for (let index = 0; index < preset.ai; index += 1) {
      const name = translate('index.sections.dice.scorecard.players.ai', 'IA');
      players.push({ name, type: 'ai' });
    }

    state.players = players;
    state.aiIndex = players.findIndex(player => player.type === 'ai');
    state.assignments = players.map(() => Object.create(null));
    state.totals = players.map(() => 0);
    state.diceValues = Array.from({ length: activeDiceCount }, () => 1);
    state.heldDice = Array.from({ length: activeDiceCount }, () => false);
    state.currentScores = Object.create(null);
    state.completedCount = 0;
    state.gameOver = false;
    state.hasRolled = false;
    state.isAiTurn = false;

    clearWinnerHighlights();
    buildScorecardTable();
    updateDiceDisplay();
    updateRollsDisplay();
    updateButtonsState();

    const modeLabel = translate(preset.labelKey, preset.fallback);
    setInstructions(translate('index.sections.dice.scorecard.mode.summary', 'Mode sélectionné : {label}', { label: modeLabel }));

    if (players.length > 0) {
      setStatus(
        'index.sections.dice.status.modeReady',
        'Mode {mode} prêt. {player} commence.',
        { mode: modeLabel, player: players[0].name }
      );
      startTurn(0);
    } else {
      setStatus('index.sections.dice.status.start', 'Choisissez un mode et lancez les dés pour commencer.');
    }
  }

  if (elements.rollButton) {
    elements.rollButton.addEventListener('click', handleRoll);
  }
  if (elements.clearButton) {
    elements.clearButton.addEventListener('click', handleClearHolds);
  }
  if (elements.newGameButton) {
    elements.newGameButton.addEventListener('click', () => {
      startNewGame(state.mode);
    });
  }
  if (elements.modeSelect) {
    elements.modeSelect.addEventListener('change', event => {
      startNewGame(event.target?.value || 'solo');
    });
  }
  diceButtons.forEach((button, index) => {
    if (!button || index >= activeDiceCount) {
      return;
    }
    button.addEventListener('click', () => handleDieToggle(index));
  });

  startNewGame(elements.modeSelect ? elements.modeSelect.value || 'solo' : 'solo');
})();
