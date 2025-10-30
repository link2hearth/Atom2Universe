(function () {
  if (typeof document === 'undefined') {
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

  const DICE_SECTION = document.getElementById('dice');
  if (!DICE_SECTION) {
    return;
  }

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

  const resolvedConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.dice
    ? GLOBAL_CONFIG.arcade.dice
    : null;

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

  const settings = {
    diceCount: resolveNumericSetting(resolvedConfig?.diceCount, DEFAULT_SETTINGS.diceCount, {
      min: 1,
      max: 10
    }),
    faces: resolveNumericSetting(resolvedConfig?.faces, DEFAULT_SETTINGS.faces, {
      min: 4,
      max: 12
    }),
    rollsPerTurn: resolveNumericSetting(resolvedConfig?.rollsPerTurn, DEFAULT_SETTINGS.rollsPerTurn, {
      min: 1,
      max: 6
    }),
    fullHouseScore: resolveNumericSetting(resolvedConfig?.fullHouseScore, DEFAULT_SETTINGS.fullHouseScore, {
      min: 0,
      max: 200
    }),
    smallStraightScore: resolveNumericSetting(
      resolvedConfig?.smallStraightScore,
      DEFAULT_SETTINGS.smallStraightScore,
      { min: 0, max: 200 }
    ),
    largeStraightScore: resolveNumericSetting(
      resolvedConfig?.largeStraightScore,
      DEFAULT_SETTINGS.largeStraightScore,
      { min: 0, max: 200 }
    ),
    yahtzeeScore: resolveNumericSetting(resolvedConfig?.yahtzeeScore, DEFAULT_SETTINGS.yahtzeeScore, {
      min: 0,
      max: 200
    }),
    bonusThreshold: resolveNumericSetting(
      resolvedConfig?.bonusThreshold,
      DEFAULT_SETTINGS.bonusThreshold,
      { min: 0, max: 200 }
    ),
    bonusValue: resolveNumericSetting(resolvedConfig?.bonusValue, DEFAULT_SETTINGS.bonusValue, {
      min: 0,
      max: 200
    })
  };

  const diceButtons = Array.from(DICE_SECTION.querySelectorAll('[data-dice-index]'));
  const activeDiceCount = Math.max(1, Math.min(settings.diceCount, diceButtons.length || settings.diceCount));
  const facesPerDie = settings.faces;
  const maxRollsPerTurn = settings.rollsPerTurn;

  const numberFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;

  function formatScore(value) {
    if (!Number.isFinite(value)) {
      return '0';
    }
    return numberFormatter ? numberFormatter.format(value) : String(Math.round(value));
  }

  const CATEGORY_DEFINITIONS = Object.freeze([
    { id: 'ones', face: 1, labelKey: 'index.sections.dice.scorecard.categories.ones', fallback: 'Ones', section: 'upper' },
    { id: 'twos', face: 2, labelKey: 'index.sections.dice.scorecard.categories.twos', fallback: 'Twos', section: 'upper' },
    { id: 'threes', face: 3, labelKey: 'index.sections.dice.scorecard.categories.threes', fallback: 'Threes', section: 'upper' },
    { id: 'fours', face: 4, labelKey: 'index.sections.dice.scorecard.categories.fours', fallback: 'Fours', section: 'upper' },
    { id: 'fives', face: 5, labelKey: 'index.sections.dice.scorecard.categories.fives', fallback: 'Fives', section: 'upper' },
    { id: 'sixes', face: 6, labelKey: 'index.sections.dice.scorecard.categories.sixes', fallback: 'Sixes', section: 'upper' },
    { id: 'threeKind', labelKey: 'index.sections.dice.scorecard.categories.threeKind', fallback: 'Three of a kind', section: 'lower' },
    { id: 'fourKind', labelKey: 'index.sections.dice.scorecard.categories.fourKind', fallback: 'Four of a kind', section: 'lower' },
    { id: 'fullHouse', labelKey: 'index.sections.dice.scorecard.categories.fullHouse', fallback: 'Full house', section: 'lower' },
    { id: 'smallStraight', labelKey: 'index.sections.dice.scorecard.categories.smallStraight', fallback: 'Small straight', section: 'lower' },
    { id: 'largeStraight', labelKey: 'index.sections.dice.scorecard.categories.largeStraight', fallback: 'Large straight', section: 'lower' },
    { id: 'yahtzee', labelKey: 'index.sections.dice.scorecard.categories.yahtzee', fallback: 'Yahtzee', section: 'lower' },
    { id: 'chance', labelKey: 'index.sections.dice.scorecard.categories.chance', fallback: 'Chance', section: 'lower' }
  ]);

  const CATEGORY_MAP = new Map(CATEGORY_DEFINITIONS.map(definition => [definition.id, definition]));

  const elements = {
    rollButton: DICE_SECTION.querySelector('#diceRollButton'),
    clearButton: DICE_SECTION.querySelector('#diceClearHoldsButton'),
    newGameButton: DICE_SECTION.querySelector('#diceNewGameButton'),
    rollsValue: DICE_SECTION.querySelector('#diceRollsValue'),
    status: DICE_SECTION.querySelector('#diceStatus'),
    lastScore: DICE_SECTION.querySelector('#diceLastScore')
  };

  const diceValues = Array.from({ length: activeDiceCount }, (_, index) => ((index % facesPerDie) + 1));
  const heldDice = Array.from({ length: activeDiceCount }, () => false);
  let rollsLeft = maxRollsPerTurn;
  let hasRolledThisTurn = false;
  let gameComplete = false;
  const assignedScores = new Map();
  let lastGameScore = 0;

  function computeCounts(values) {
    const counts = new Array(facesPerDie + 1).fill(0);
    for (let i = 0; i < values.length; i += 1) {
      const face = values[i];
      if (counts[face] != null) {
        counts[face] += 1;
      }
    }
    return counts;
  }

  function computeSum(values) {
    let total = 0;
    for (let i = 0; i < values.length; i += 1) {
      total += values[i];
    }
    return total;
  }

  function getCategoryLabel(definition) {
    return translate(definition.labelKey, definition.fallback);
  }

  function computeCategoryScore(definition, counts, sum, uniqueValues) {
    if (!definition || !counts) {
      return 0;
    }
    switch (definition.id) {
      case 'ones':
      case 'twos':
      case 'threes':
      case 'fours':
      case 'fives':
      case 'sixes': {
        const face = definition.face ?? 0;
        const count = counts[face] ?? 0;
        return face * count;
      }
      case 'threeKind': {
        for (let i = 1; i < counts.length; i += 1) {
          if (counts[i] >= 3) {
            return sum;
          }
        }
        return 0;
      }
      case 'fourKind': {
        for (let i = 1; i < counts.length; i += 1) {
          if (counts[i] >= 4) {
            return sum;
          }
        }
        return 0;
      }
      case 'fullHouse': {
        let hasThree = false;
        let hasPair = false;
        for (let i = 1; i < counts.length; i += 1) {
          if (counts[i] === 5) {
            // Yahtzee counts as a full house in Yahtzee scoring variants.
            return settings.fullHouseScore;
          }
          if (counts[i] === 3) {
            hasThree = true;
          } else if (counts[i] === 2) {
            hasPair = true;
          }
        }
        return hasThree && hasPair ? settings.fullHouseScore : 0;
      }
      case 'smallStraight': {
        if (!uniqueValues) {
          return 0;
        }
        const sequences = [
          [1, 2, 3, 4],
          [2, 3, 4, 5],
          [3, 4, 5, 6]
        ];
        for (let i = 0; i < sequences.length; i += 1) {
          const sequence = sequences[i];
          let valid = true;
          for (let j = 0; j < sequence.length; j += 1) {
            if (!uniqueValues.has(sequence[j])) {
              valid = false;
              break;
            }
          }
          if (valid) {
            return settings.smallStraightScore;
          }
        }
        return 0;
      }
      case 'largeStraight': {
        if (!uniqueValues) {
          return 0;
        }
        const largeSequences = [
          [1, 2, 3, 4, 5],
          [2, 3, 4, 5, 6]
        ];
        for (let i = 0; i < largeSequences.length; i += 1) {
          const sequence = largeSequences[i];
          let valid = true;
          for (let j = 0; j < sequence.length; j += 1) {
            if (!uniqueValues.has(sequence[j])) {
              valid = false;
              break;
            }
          }
          if (valid) {
            return settings.largeStraightScore;
          }
        }
        return 0;
      }
      case 'yahtzee': {
        for (let i = 1; i < counts.length; i += 1) {
          if (counts[i] === activeDiceCount) {
            return settings.yahtzeeScore;
          }
        }
        return 0;
      }
      case 'chance':
        return sum;
      default:
        return 0;
    }
  }

  function computeTotals() {
    let upperSubtotal = 0;
    let lowerTotal = 0;
    assignedScores.forEach((score, categoryId) => {
      const def = CATEGORY_MAP.get(categoryId);
      if (!def) {
        return;
      }
      if (def.section === 'upper') {
        upperSubtotal += score;
      } else {
        lowerTotal += score;
      }
    });
    const bonus = upperSubtotal >= settings.bonusThreshold ? settings.bonusValue : 0;
    const upperTotal = upperSubtotal + bonus;
    const grandTotal = upperTotal + lowerTotal;
    return { upperSubtotal, bonus, upperTotal, lowerTotal, grandTotal };
  }

  function updateLastScoreDisplay() {
    if (!elements.lastScore) {
      return;
    }
    elements.lastScore.textContent = translate(
      'index.sections.dice.controls.lastScore',
      'Last score: {score}',
      { score: formatScore(lastGameScore) }
    );
  }

  function setStatus(key, fallback, params) {
    if (!elements.status) {
      return;
    }
    elements.status.textContent = translate(key, fallback, params);
  }

  function updateRollInfo() {
    if (elements.rollsValue) {
      elements.rollsValue.textContent = formatScore(rollsLeft);
    }
  }

  function updateDiceDisplay() {
    for (let i = 0; i < diceButtons.length; i += 1) {
      const button = diceButtons[i];
      if (!button) {
        continue;
      }
      if (i >= activeDiceCount) {
        button.setAttribute('hidden', '');
        continue;
      }
      button.removeAttribute('hidden');
      const value = diceValues[i] ?? 1;
      const held = heldDice[i] ?? false;
      button.dataset.value = String(value);
      button.dataset.held = held ? 'true' : 'false';
      button.setAttribute('aria-pressed', held ? 'true' : 'false');
      const valueElement = button.querySelector('.dice-die__value');
      if (valueElement) {
        valueElement.textContent = String(value);
      }
      updateDieAriaLabel(i);
    }
  }

  function updateDieAriaLabel(index) {
    const button = diceButtons[index];
    if (!button || index >= activeDiceCount) {
      return;
    }
    const dieValue = diceValues[index] ?? 1;
    const held = heldDice[index];
    const params = {
      index: formatScore(index + 1),
      value: formatScore(dieValue)
    };
    const key = held ? 'scripts.arcade.dice.die.held' : 'scripts.arcade.dice.die.available';
    const fallback = held
      ? `Die ${index + 1} showing ${dieValue}, held.`
      : `Die ${index + 1} showing ${dieValue}.`;
    button.setAttribute('aria-label', translate(key, fallback, params));
  }

  function selectBestCategory(counts, sum, uniqueValues) {
    let bestDefinition = null;
    let bestScore = -1;
    for (let i = 0; i < CATEGORY_DEFINITIONS.length; i += 1) {
      const definition = CATEGORY_DEFINITIONS[i];
      if (assignedScores.has(definition.id)) {
        continue;
      }
      const score = Math.max(0, computeCategoryScore(definition, counts, sum, uniqueValues));
      if (score > bestScore || (score === bestScore && bestDefinition === null)) {
        bestScore = score;
        bestDefinition = definition;
      }
    }
    return bestDefinition ? { definition: bestDefinition, score: bestScore } : null;
  }

  function autoAssignBestCategory(precomputed) {
    if (gameComplete || !hasRolledThisTurn) {
      return;
    }
    const counts = precomputed?.counts ?? computeCounts(diceValues);
    const sum = precomputed?.sum ?? computeSum(diceValues);
    const uniqueValues = precomputed?.uniqueValues ?? new Set(diceValues);
    const best = selectBestCategory(counts, sum, uniqueValues);
    if (!best) {
      const totals = computeTotals();
      gameComplete = true;
      lastGameScore = totals.grandTotal;
      updateRollInfo();
      updateControls();
      updateLastScoreDisplay();
      setStatus(
        'scripts.arcade.dice.status.gameComplete',
        'Game complete! Final score: {score}.',
        { score: formatScore(totals.grandTotal) }
      );
      return;
    }

    assignedScores.set(best.definition.id, best.score);
    const totals = computeTotals();
    heldDice.fill(false);
    hasRolledThisTurn = false;
    rollsLeft = maxRollsPerTurn;
    updateDiceDisplay();
    updateRollInfo();

    const completed = assignedScores.size >= CATEGORY_DEFINITIONS.length;
    if (completed) {
      gameComplete = true;
      lastGameScore = totals.grandTotal;
      updateControls();
      updateLastScoreDisplay();
      setStatus(
        'scripts.arcade.dice.status.gameComplete',
        'Game complete! Final score: {score}.',
        { score: formatScore(totals.grandTotal) }
      );
      return;
    }

    updateControls();
    setStatus(
      'scripts.arcade.dice.status.autoScored',
      'Picked {category} for {score} points. Current total: {total}.',
      {
        category: getCategoryLabel(best.definition),
        score: formatScore(best.score),
        total: formatScore(totals.grandTotal)
      }
    );
  }

  function updateControls() {
    if (elements.rollButton) {
      elements.rollButton.disabled = gameComplete;
    }
    if (elements.clearButton) {
      const hasHeld = heldDice.some(Boolean);
      elements.clearButton.disabled = !hasHeld || !hasRolledThisTurn || gameComplete;
    }
    if (elements.newGameButton) {
      elements.newGameButton.disabled = false;
    }
  }

  function rollDice() {
    if (gameComplete) {
      return;
    }
    if (rollsLeft <= 0) {
      autoAssignBestCategory();
      return;
    }
    for (let i = 0; i < activeDiceCount; i += 1) {
      if (heldDice[i]) {
        continue;
      }
      const newValue = Math.floor(Math.random() * facesPerDie) + 1;
      diceValues[i] = newValue;
      const button = diceButtons[i];
      if (button) {
        const dx = (Math.random() * 160) - 80;
        const dy = (Math.random() * 120) - 60;
        const rot = (Math.random() * 720) - 360;
        const delay = Math.random() * 0.18;
        button.style.setProperty('--roll-dx', `${dx.toFixed(2)}px`);
        button.style.setProperty('--roll-dy', `${dy.toFixed(2)}px`);
        button.style.setProperty('--roll-rot', `${rot.toFixed(2)}deg`);
        button.style.setProperty('--roll-delay', `${delay.toFixed(3)}s`);
        button.classList.remove('dice-die--rolling');
        // Force reflow to restart the animation
        void button.offsetWidth;
        button.classList.add('dice-die--rolling');
      }
    }
    rollsLeft -= 1;
    hasRolledThisTurn = true;
    updateRollInfo();
    updateDiceDisplay();
    const counts = computeCounts(diceValues);
    const sum = computeSum(diceValues);
    const uniqueValues = new Set(diceValues);
    updateControls();
    if (rollsLeft > 0) {
      setStatus(
        'scripts.arcade.dice.status.rollsLeft',
        'Rolls left: {rolls}. Select dice to hold or roll again.',
        { rolls: formatScore(rollsLeft) }
      );
      return;
    }
    autoAssignBestCategory({ counts, sum, uniqueValues });
  }

  function toggleHold(index) {
    if (!hasRolledThisTurn || gameComplete) {
      return;
    }
    heldDice[index] = !heldDice[index];
    updateDiceDisplay();
    updateControls();
    const key = heldDice[index]
      ? 'scripts.arcade.dice.status.dieHeld'
      : 'scripts.arcade.dice.status.dieReleased';
    const fallback = heldDice[index]
      ? `Die ${index + 1} held.`
      : `Die ${index + 1} released.`;
    setStatus(key, fallback, { index: formatScore(index + 1) });
  }

  function clearHolds() {
    if (!hasRolledThisTurn || gameComplete) {
      return;
    }
    let changed = false;
    for (let i = 0; i < heldDice.length; i += 1) {
      if (heldDice[i]) {
        heldDice[i] = false;
        changed = true;
      }
    }
    if (!changed) {
      return;
    }
    updateDiceDisplay();
    updateControls();
    setStatus('scripts.arcade.dice.status.holdsCleared', 'All dice released.');
  }

  function resetGame() {
    assignedScores.clear();
    hasRolledThisTurn = false;
    gameComplete = false;
    rollsLeft = maxRollsPerTurn;
    for (let i = 0; i < diceValues.length; i += 1) {
      diceValues[i] = ((i % facesPerDie) + 1);
      heldDice[i] = false;
    }
    updateDiceDisplay();
    updateRollInfo();
    updateControls();
    updateLastScoreDisplay();
    setStatus(
      'scripts.arcade.dice.status.newGame',
      'New game started. Roll to begin.'
    );
  }

  diceButtons.forEach((button, index) => {
    if (!button) {
      return;
    }
    if (index >= activeDiceCount) {
      button.setAttribute('hidden', '');
      return;
    }
    button.dataset.diceIndex = String(index);
    button.addEventListener('click', event => {
      event.preventDefault();
      toggleHold(index);
    });
    button.addEventListener('animationend', () => {
      button.classList.remove('dice-die--rolling');
    });
  });

  if (elements.rollButton) {
    elements.rollButton.addEventListener('click', event => {
      event.preventDefault();
      rollDice();
    });
  }

  if (elements.clearButton) {
    elements.clearButton.addEventListener('click', event => {
      event.preventDefault();
      clearHolds();
    });
  }

  if (elements.newGameButton) {
    elements.newGameButton.addEventListener('click', event => {
      event.preventDefault();
      resetGame();
    });
  }

  updateDiceDisplay();
  updateRollInfo();
  updateLastScoreDisplay();
  updateControls();
  setStatus(
    'scripts.arcade.dice.status.start',
    'Roll the dice to begin.'
  );
})();
