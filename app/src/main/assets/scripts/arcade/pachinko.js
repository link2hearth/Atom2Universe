(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const DEFAULT_BET_OPTIONS = Object.freeze([10, 20, 50]);
  const DEFAULT_SLOT_LAYOUT = Object.freeze([
    Object.freeze({
      id: 'void-left',
      className: 'pachinko-slot--void',
      labelKey: 'index.sections.pachinko.slots.void',
      fallback: 'Void Rift',
      multiplier: 0
    }),
    Object.freeze({
      id: 'stardust-left',
      className: 'pachinko-slot--stardust',
      labelKey: 'index.sections.pachinko.slots.stardust',
      fallback: 'Stardust Trail',
      multiplier: 0.5
    }),
    Object.freeze({
      id: 'aurora-left',
      className: 'pachinko-slot--aurora',
      labelKey: 'index.sections.pachinko.slots.aurora',
      fallback: 'Aurora Gate',
      multiplier: 1.5
    }),
    Object.freeze({
      id: 'core',
      className: 'pachinko-slot--core',
      labelKey: 'index.sections.pachinko.slots.core',
      fallback: 'Supernova Core',
      multiplier: 5
    }),
    Object.freeze({
      id: 'aurora-right',
      className: 'pachinko-slot--aurora',
      labelKey: 'index.sections.pachinko.slots.aurora',
      fallback: 'Aurora Gate',
      multiplier: 1.5
    }),
    Object.freeze({
      id: 'stardust-right',
      className: 'pachinko-slot--stardust',
      labelKey: 'index.sections.pachinko.slots.stardust',
      fallback: 'Stardust Trail',
      multiplier: 0.5
    }),
    Object.freeze({
      id: 'void-right',
      className: 'pachinko-slot--void',
      labelKey: 'index.sections.pachinko.slots.void',
      fallback: 'Void Rift',
      multiplier: 0
    })
  ]);
  const DEFAULT_ROW_COUNT = 8;
  const DEFAULT_STEP_MS = 280;
  const HISTORY_LIMIT = 8;

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
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
      console.warn('Pachinko translation error for key', key, error);
    }
    return fallback;
  }

  function sanitizeBetOptions(source) {
    if (!Array.isArray(source)) {
      return [...DEFAULT_BET_OPTIONS];
    }
    const unique = new Set();
    const cleaned = [];
    for (let i = 0; i < source.length; i += 1) {
      const value = Number(source[i]);
      if (!Number.isFinite(value)) {
        continue;
      }
      const normalized = Math.floor(value);
      if (normalized <= 0 || unique.has(normalized)) {
        continue;
      }
      unique.add(normalized);
      cleaned.push(normalized);
    }
    if (!cleaned.length) {
      return [...DEFAULT_BET_OPTIONS];
    }
    cleaned.sort((a, b) => a - b);
    return cleaned;
  }

  function sanitizeSlotMultipliers(layout, source) {
    if (!Array.isArray(source) || !source.length) {
      return layout.map(slot => slot.multiplier);
    }
    const normalized = [];
    for (let i = 0; i < layout.length; i += 1) {
      const fallback = layout[i].multiplier;
      const value = Number(source[i]);
      if (Number.isFinite(value) && value >= 0) {
        normalized.push(value);
      } else {
        normalized.push(fallback);
      }
    }
    return normalized;
  }

  function clampRowCount(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return DEFAULT_ROW_COUNT;
    }
    return Math.max(4, Math.min(12, Math.floor(numeric)));
  }

  function clampStepMs(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return DEFAULT_STEP_MS;
    }
    return Math.max(140, Math.min(600, Math.floor(numeric)));
  }

  onReady(() => {
    const section = document.getElementById('pachinko');
    if (!section) {
      return;
    }

    const boardElement = section.querySelector('.pachinko-board__inner');
    const pegLayerElement = section.querySelector('#pachinkoPegLayer');
    const slotsContainer = section.querySelector('#pachinkoSlots');
    const ballElement = section.querySelector('#pachinkoBall');
    const statusElement = section.querySelector('#pachinkoStatus');
    const dropButton = section.querySelector('#pachinkoDropButton');
    const betContainer = section.querySelector('#pachinkoBet');
    const betOptionsElement = section.querySelector('#pachinkoBetOptions');
    const betCurrentElement = section.querySelector('#pachinkoCurrentBet');
    const multiplyButton = section.querySelector('#pachinkoMultiplyBets');
    const divideButton = section.querySelector('#pachinkoDivideBets');
    const balanceElement = section.querySelector('#pachinkoAtomsBalance');
    const statsDropsElement = section.querySelector('#pachinkoTotalDrops');
    const statsWinsElement = section.querySelector('#pachinkoTotalWins');
    const statsBestElement = section.querySelector('#pachinkoBestMultiplier');
    const statsStreakElement = section.querySelector('#pachinkoStreakValue');
    const historyListElement = section.querySelector('#pachinkoHistory');

    if (!boardElement || !slotsContainer || !dropButton || !betContainer || !betOptionsElement) {
      return;
    }

    const resolvedConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.pachinko
      ? GLOBAL_CONFIG.arcade.pachinko
      : null;

    const baseBetOptions = sanitizeBetOptions(resolvedConfig ? resolvedConfig.betOptions : null);
    const slotLayoutMultipliers = sanitizeSlotMultipliers(DEFAULT_SLOT_LAYOUT, resolvedConfig ? resolvedConfig.slotMultipliers : null);
    const rowCount = clampRowCount(resolvedConfig && resolvedConfig.board ? resolvedConfig.board.rows : null);
    const dropStepMs = clampStepMs(resolvedConfig && resolvedConfig.board ? resolvedConfig.board.stepMs : null);
    const slotCount = DEFAULT_SLOT_LAYOUT.length;

    const slotDefinitions = DEFAULT_SLOT_LAYOUT.map((slot, index) => ({
      id: slot.id,
      className: slot.className,
      labelKey: slot.labelKey,
      fallback: slot.fallback,
      multiplier: slotLayoutMultipliers[index] ?? slot.multiplier
    }));

    const numberFormatter = typeof Intl !== 'undefined' && typeof Intl.NumberFormat === 'function'
      ? () => new Intl.NumberFormat(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 0 })
      : () => null;

    let currentMultiplierFormatter = numberFormatter();

    function refreshMultiplierFormatter() {
      currentMultiplierFormatter = numberFormatter();
    }

    function formatMultiplier(value) {
      if (!Number.isFinite(value)) {
        return '×0';
      }
      const numeric = Math.max(0, value);
      const formatter = currentMultiplierFormatter;
      if (formatter) {
        try {
          const formatted = formatter.format(numeric);
          if (formatted) {
            return `×${formatted}`;
          }
        } catch (error) {
          // ignore format error
        }
      }
      const rounded = Math.round(numeric * 100) / 100;
      return `×${rounded}`;
    }

    function formatLayeredNumber(value) {
      if (value instanceof LayeredNumber) {
        return value.toString();
      }
      const numeric = Number(value);
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
          // ignore formatting error
        }
      }
      if (typeof numeric.toLocaleString === 'function') {
        return numeric.toLocaleString();
      }
      return `${numeric}`;
    }

    function formatSigned(value) {
      if (!(value instanceof LayeredNumber)) {
        return `${value}`;
      }
      const text = value.toString();
      if (value.sign > 0 && text[0] !== '+') {
        return `+${text}`;
      }
      return text;
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

    function createLayeredAmount(amount) {
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
      const bet = createLayeredAmount(amount);
      if (!atoms || !bet) {
        return false;
      }
      return atoms.compare(bet) >= 0;
    }

    function cloneLayered(value) {
      if (value instanceof LayeredNumber) {
        return value.clone();
      }
      return createLayeredAmount(value) || LayeredNumber.zero();
    }

    let betMultiplier = 1;
    let betOptions = baseBetOptions.map(amount => amount * betMultiplier);
    let betButtons = [];
    let selectedBet = null;
    let selectedBaseBet = null;
    let activeBet = null;
    let dropping = false;
    let currentTimeouts = [];
    let balanceIntervalId = null;

    const stats = {
      drops: 0,
      wins: 0,
      streak: 0,
      bestMultiplier: 0
    };

    const historyEntries = [];

    function clearPendingTimeouts() {
      for (let i = 0; i < currentTimeouts.length; i += 1) {
        window.clearTimeout(currentTimeouts[i]);
      }
      currentTimeouts = [];
    }

    function scheduleTimeout(callback, delay) {
      const id = window.setTimeout(callback, delay);
      currentTimeouts.push(id);
    }

    function formatBetAmount(amount) {
      if (!Number.isFinite(amount)) {
        return '0';
      }
      const numeric = Math.floor(amount);
      if (typeof formatNumberLocalized === 'function') {
        try {
          const formatted = formatNumberLocalized(numeric, { maximumFractionDigits: 0 });
          if (formatted) {
            return formatted;
          }
        } catch (error) {
          // ignore formatting error
        }
      }
      if (typeof numeric.toLocaleString === 'function') {
        return numeric.toLocaleString();
      }
      return `${numeric}`;
    }

    function translateBetOptionLabel(amountLabel) {
      return translate(
        'index.sections.pachinko.bet.option',
        `${amountLabel} atoms`,
        { amount: amountLabel }
      );
    }

    function updateBalanceDisplay() {
      if (!balanceElement) {
        return;
      }
      const atoms = getGameAtoms();
      balanceElement.textContent = atoms ? atoms.toString() : '0';
    }

    function startBalanceUpdates() {
      if (balanceIntervalId != null) {
        return;
      }
      balanceIntervalId = window.setInterval(() => {
        if (typeof document !== 'undefined' && document.hidden) {
          return;
        }
        updateBalanceDisplay();
      }, 1200);
    }

    function setStatus(key, fallback, params) {
      if (!statusElement) {
        return;
      }
      const message = key
        ? translate(`scripts.arcade.pachinko.status.${key}`, fallback, params)
        : fallback;
      statusElement.textContent = message;
    }

    function updateDropButtonState() {
      if (!dropButton) {
        return;
      }
      const disabled = dropping || selectedBet == null || !canAffordBet(selectedBet);
      dropButton.disabled = disabled;
    }

    function updateBetButtons() {
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const amount = Number(button.dataset.bet);
        const disable = dropping || !canAffordBet(amount);
        button.disabled = disable;
        button.classList.toggle('pachinko-bet__option--unavailable', !dropping && disable);
        const isSelected = selectedBet === amount;
        button.classList.toggle('pachinko-bet__option--selected', isSelected);
        button.setAttribute('aria-pressed', isSelected ? 'true' : 'false');
      }
      updateDropButtonState();
    }

    function updateMultiplierControls() {
      if (multiplyButton) {
        const multiplierText = formatBetAmount(betMultiplier);
        const aria = translate(
          'index.sections.pachinko.bet.scale.multiplyAria',
          `Multiply bet options by 10 (current multiplier: ×${multiplierText})`,
          { multiplier: multiplierText }
        );
        multiplyButton.disabled = dropping;
        multiplyButton.setAttribute('aria-label', aria);
        multiplyButton.title = aria;
      }
      if (divideButton) {
        const multiplierText = formatBetAmount(betMultiplier);
        const aria = translate(
          'index.sections.pachinko.bet.scale.divideAria',
          `Divide bet options by 10 (current multiplier: ×${multiplierText})`,
          { multiplier: multiplierText }
        );
        const disabled = dropping || betMultiplier <= 1;
        divideButton.disabled = disabled;
        divideButton.setAttribute('aria-label', aria);
        divideButton.title = aria;
      }
    }

    function ensureSelectedBetAffordable() {
      if (selectedBet != null && !canAffordBet(selectedBet)) {
        setSelectedBet(null);
      }
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
            selectedBaseBet = Number.isFinite(numericBase) && numericBase > 0
              ? Math.floor(numericBase)
              : null;
          } else if (betMultiplier > 0) {
            selectedBaseBet = Math.floor(selectedBet / betMultiplier);
          }
        } else {
          selectedBet = null;
          selectedBaseBet = null;
        }
      }

      if (betCurrentElement) {
        betCurrentElement.removeAttribute('data-i18n');
        betCurrentElement.textContent = selectedBet != null
          ? formatBetAmount(selectedBet)
          : translate('index.sections.pachinko.bet.none', 'None');
      }
      updateBetButtons();
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
      if (selectedBaseBet != null) {
        const scaledSelection = selectedBaseBet * betMultiplier;
        setSelectedBet(scaledSelection, selectedBaseBet);
      } else if (selectedBet != null) {
        setSelectedBet(selectedBet);
      }
      ensureSelectedBetAffordable();
      updateBetButtons();
      updateMultiplierControls();
    }

    function setBetMultiplier(value) {
      const numeric = Number(value);
      const normalized = Number.isFinite(numeric) && numeric > 0 ? Math.max(1, Math.floor(numeric)) : betMultiplier;
      if (normalized === betMultiplier) {
        updateMultiplierControls();
        return;
      }
      betMultiplier = normalized;
      updateBetOptionValues();
    }

    function renderBetButtons() {
      betButtons = [];
      betOptionsElement.innerHTML = '';
      for (let i = 0; i < baseBetOptions.length; i += 1) {
        const baseAmount = baseBetOptions[i];
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'pachinko-bet__option';
        button.dataset.baseBet = `${baseAmount}`;
        button.addEventListener('click', () => {
          if (dropping) {
            return;
          }
          const scaled = baseAmount * betMultiplier;
          setSelectedBet(scaled, baseAmount);
        });
        betButtons.push(button);
        betOptionsElement.appendChild(button);
      }
      updateBetOptionValues();
    }

    function renderPegLayer() {
      if (!pegLayerElement) {
        return;
      }
      pegLayerElement.innerHTML = '';
      for (let row = 0; row < rowCount; row += 1) {
        const rowElement = document.createElement('div');
        rowElement.className = 'pachinko-pegs__row';
        if (row % 2 === 1) {
          rowElement.classList.add('pachinko-pegs__row--offset');
        }
        for (let col = 0; col < slotCount; col += 1) {
          const peg = document.createElement('span');
          peg.className = 'pachinko-pegs__peg';
          rowElement.appendChild(peg);
        }
        pegLayerElement.appendChild(rowElement);
      }
    }

    const slotElements = Array.from(slotsContainer.querySelectorAll('.pachinko-slot')).slice(0, slotCount);

    function renderSlotDetails() {
      for (let i = 0; i < slotElements.length; i += 1) {
        const slotElement = slotElements[i];
        const slotDefinition = slotDefinitions[i];
        if (!slotElement || !slotDefinition) {
          continue;
        }
        slotElement.dataset.multiplier = `${slotDefinition.multiplier}`;
        slotElement.dataset.slotId = slotDefinition.id;
        slotElement.classList.add(slotDefinition.className);
        const multiplierElement = slotElement.querySelector('.pachinko-slot__multiplier');
        if (multiplierElement) {
          multiplierElement.textContent = formatMultiplier(slotDefinition.multiplier);
        }
      }
    }

    function renderHistory() {
      if (!historyListElement) {
        return;
      }
      historyListElement.innerHTML = '';
      if (!historyEntries.length) {
        const empty = document.createElement('li');
        empty.className = 'pachinko-history__empty';
        empty.setAttribute('data-i18n', 'index.sections.pachinko.history.empty');
        empty.textContent = translate('index.sections.pachinko.history.empty', 'No drops yet.');
        historyListElement.appendChild(empty);
        return;
      }
      for (let i = 0; i < historyEntries.length; i += 1) {
        const entry = historyEntries[i];
        const listItem = document.createElement('li');
        listItem.className = `pachinko-history__item pachinko-history__item--${entry.outcome}`;
        const multiplierSpan = document.createElement('span');
        multiplierSpan.className = 'pachinko-history__multiplier';
        multiplierSpan.textContent = formatMultiplier(entry.multiplier);
        const nameSpan = document.createElement('span');
        nameSpan.className = 'pachinko-history__label';
        nameSpan.textContent = translate(entry.labelKey, entry.fallback);
        const deltaSpan = document.createElement('span');
        deltaSpan.className = 'pachinko-history__delta';
        deltaSpan.textContent = formatSigned(entry.net);
        listItem.appendChild(multiplierSpan);
        listItem.appendChild(nameSpan);
        listItem.appendChild(deltaSpan);
        historyListElement.appendChild(listItem);
      }
    }

    function pushHistory(slotDefinition, multiplier, net) {
      const entry = {
        labelKey: slotDefinition.labelKey,
        fallback: slotDefinition.fallback,
        multiplier,
        net: cloneLayered(net),
        outcome: net.sign > 0 ? 'win' : net.sign < 0 ? 'loss' : 'neutral'
      };
      historyEntries.unshift(entry);
      if (historyEntries.length > HISTORY_LIMIT) {
        historyEntries.length = HISTORY_LIMIT;
      }
      renderHistory();
    }

    function highlightSlot(index) {
      for (let i = 0; i < slotElements.length; i += 1) {
        slotElements[i].classList.toggle('pachinko-slot--active', i === index);
      }
      scheduleTimeout(() => {
        for (let i = 0; i < slotElements.length; i += 1) {
          slotElements[i].classList.remove('pachinko-slot--active');
        }
      }, 1500);
    }

    function resetBallPosition() {
      if (!ballElement) {
        return;
      }
      ballElement.classList.remove('pachinko-board__ball--active');
      ballElement.style.top = '0%';
      ballElement.style.left = '50%';
      ballElement.style.opacity = '0';
      ballElement.style.transform = 'translate(-50%, -100%)';
    }

    function animateBall(path) {
      return new Promise(resolve => {
        if (!ballElement || !Array.isArray(path) || !path.length) {
          resolve();
          return;
        }
        ballElement.classList.add('pachinko-board__ball--active');
        ballElement.style.opacity = '1';
        ballElement.style.transform = 'translate(-50%, -50%)';
        let stepIndex = 0;
        const totalSteps = path.length;

        function step() {
          if (!ballElement) {
            resolve();
            return;
          }
          const { row, column } = path[stepIndex];
          const verticalPercent = Math.min(100, (row / (rowCount + 0.6)) * 100);
          const horizontalPercent = slotCount <= 1 ? 50 : (column / (slotCount - 1)) * 100;
          ballElement.style.top = `${verticalPercent}%`;
          ballElement.style.left = `${horizontalPercent}%`;
          stepIndex += 1;
          if (stepIndex < totalSteps) {
            scheduleTimeout(step, dropStepMs);
          } else {
            scheduleTimeout(resolve, dropStepMs + 40);
          }
        }

        step();
      });
    }

    function generatePath() {
      const path = [];
      let column = Math.floor(slotCount / 2);
      const lastIndex = slotCount - 1;
      for (let row = 0; row <= rowCount; row += 1) {
        path.push({ row, column });
        if (row === rowCount) {
          break;
        }
        const options = [0];
        if (column > 0) {
          options.push(-1);
        }
        if (column < lastIndex) {
          options.push(1);
        }
        const choice = options[Math.floor(Math.random() * options.length)];
        column = Math.max(0, Math.min(lastIndex, column + choice));
      }
      return path;
    }

    function updateStatsDisplay() {
      if (statsDropsElement) {
        statsDropsElement.textContent = `${stats.drops}`;
      }
      if (statsWinsElement) {
        statsWinsElement.textContent = `${stats.wins}`;
      }
      if (statsBestElement) {
        statsBestElement.textContent = formatMultiplier(stats.bestMultiplier);
      }
      if (statsStreakElement) {
        statsStreakElement.textContent = `${stats.streak}`;
      }
    }

    function finishDrop(slotIndex, slotDefinition, layeredBet) {
      highlightSlot(slotIndex);
      stats.drops += 1;

      let payout = null;
      const multiplier = Number(slotDefinition.multiplier);
      if (Number.isFinite(multiplier) && multiplier > 0) {
        payout = layeredBet.multiplyNumber(multiplier);
      }

      let net = payout ? payout.subtract(layeredBet) : layeredBet.negate();
      if (!(net instanceof LayeredNumber)) {
        net = LayeredNumber.zero();
      }

      if (payout && typeof gameState !== 'undefined') {
        gameState.atoms = gameState.atoms.add(payout);
        if (typeof updateUI === 'function') {
          updateUI();
        }
        if (typeof saveGame === 'function') {
          saveGame();
        }
      }

      if (net.sign > 0) {
        stats.wins += 1;
        stats.streak += 1;
      } else if (net.sign < 0) {
        stats.streak = 0;
      }
      if (Number.isFinite(multiplier) && multiplier > stats.bestMultiplier) {
        stats.bestMultiplier = multiplier;
      }

      pushHistory(slotDefinition, multiplier, net);
      updateStatsDisplay();
      updateBalanceDisplay();

      const slotName = translate(slotDefinition.labelKey, slotDefinition.fallback);
      const params = {
        multiplier: formatMultiplier(multiplier),
        slot: slotName,
        net: formatLayeredNumber(net)
      };

      if (net.sign > 0) {
        setStatus('win', 'You win!', params);
      } else if (net.sign === 0) {
        setStatus('even', 'Break even.', params);
      } else if (multiplier > 0) {
        setStatus('softLoss', 'Partial loss.', params);
      } else {
        setStatus('loss', 'You lose.', params);
      }

      if (typeof showToast === 'function') {
        const toastKey = net.sign > 0
          ? 'scripts.arcade.pachinko.toast.win'
          : net.sign === 0
            ? 'scripts.arcade.pachinko.toast.even'
            : multiplier > 0
              ? 'scripts.arcade.pachinko.toast.softLoss'
              : 'scripts.arcade.pachinko.toast.loss';
        showToast(translate(toastKey, statusElement ? statusElement.textContent : ''));
      }

      activeBet = null;
      dropping = false;
      updateBetButtons();
      updateMultiplierControls();
      updateDropButtonState();
      scheduleTimeout(resetBallPosition, 300);
      ensureSelectedBetAffordable();
    }

    function beginDrop() {
      if (dropping) {
        return;
      }
      if (selectedBet == null) {
        setStatus('selectBet', 'Select a bet first.');
        if (typeof showToast === 'function') {
          showToast(translate('scripts.arcade.pachinko.status.selectBet', 'Select a bet first.'));
        }
        return;
      }
      if (!canAffordBet(selectedBet)) {
        setStatus('insufficientAtoms', 'Not enough atoms for this bet.');
        if (typeof showToast === 'function') {
          showToast(translate('scripts.arcade.pachinko.status.insufficientAtoms', 'Not enough atoms for this bet.'));
        }
        ensureSelectedBetAffordable();
        updateBalanceDisplay();
        return;
      }
      const layeredBet = createLayeredAmount(selectedBet);
      if (!layeredBet) {
        return;
      }
      activeBet = layeredBet;
      dropping = true;
      clearPendingTimeouts();
      setStatus('dropping', 'Dropping...');
      resetBallPosition();
      updateBetButtons();
      updateMultiplierControls();
      updateDropButtonState();

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

      const path = generatePath();
      animateBall(path).then(() => {
        const lastStep = path[path.length - 1] || { column: 0 };
        const index = Math.max(0, Math.min(slotDefinitions.length - 1, lastStep.column));
        const slotDefinition = slotDefinitions[index];
        finishDrop(index, slotDefinition, layeredBet);
      });
    }

    if (dropButton) {
      dropButton.addEventListener('click', () => {
        beginDrop();
      });
    }

    if (multiplyButton) {
      multiplyButton.addEventListener('click', () => {
        if (dropping) {
          return;
        }
        setBetMultiplier(betMultiplier * 10);
      });
    }

    if (divideButton) {
      divideButton.addEventListener('click', () => {
        if (dropping || betMultiplier <= 1) {
          return;
        }
        setBetMultiplier(Math.max(1, Math.floor(betMultiplier / 10)));
      });
    }

    renderPegLayer();
    renderBetButtons();
    renderSlotDetails();
    renderHistory();
    setSelectedBet(null);
    updateStatsDisplay();
    updateBalanceDisplay();
    startBalanceUpdates();
    setStatus('ready', 'Pick a bet and drop a quantum orb.');

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('i18n:languagechange', () => {
        refreshMultiplierFormatter();
        renderSlotDetails();
        renderHistory();
        updateMultiplierControls();
        if (betCurrentElement && selectedBet == null) {
          betCurrentElement.textContent = translate('index.sections.pachinko.bet.none', 'None');
        }
        if (!dropping) {
          setStatus('ready', 'Pick a bet and drop a quantum orb.');
        }
      });
    }
  });
})();
