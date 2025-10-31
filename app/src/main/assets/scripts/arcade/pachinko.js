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
  const DEFAULT_ADVANTAGE_BONUS = 0.3;
  const HISTORY_LIMIT = 8;
  const MOVING_PEG_TRAVEL_MARGIN = 0.04;

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

  function clampValue(value, min, max) {
    if (!Number.isFinite(value)) {
      return min;
    }
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  function randomOffset(scale) {
    return (Math.random() - 0.5) * scale;
  }

  onReady(() => {
    const section = document.getElementById('pachinko');
    if (!section) {
      return;
    }

    const boardElement = section.querySelector('.pachinko-board__inner');
    const pegLayerElement = section.querySelector('#pachinkoPegLayer');
    const slotsContainer = section.querySelector('#pachinkoSlots');
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
    const advantageBonusMultiplier = resolvedConfig && typeof resolvedConfig.advantageBonus === 'number'
      ? Math.max(0, resolvedConfig.advantageBonus)
      : DEFAULT_ADVANTAGE_BONUS;

    const boardField = {
      left: 0.08,
      right: 0.92,
      top: 0.06,
      bottom: 0.86
    };
    boardField.width = boardField.right - boardField.left;
    boardField.height = boardField.bottom - boardField.top;

    const physicsPegs = [];
    const movingPegStates = [];
    let movingPegAnimationHandle = null;
    let lastDropSpawnRatio = 0.5;
    const slotTargets = slotLayoutMultipliers.map((_, index) => {
      if (slotCount <= 1) {
        return boardField.left + boardField.width / 2;
      }
      const ratio = index / (slotCount - 1);
      return boardField.left + ratio * boardField.width;
    });
    const BALL_RADIUS = 0.014;
    const GRAVITY_FORCE = 0.00118;
    const MAX_SIMULATION_STEPS = 900;
    const PATH_SAMPLE_INTERVAL = 2;

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
      if (typeof LayeredNumber === 'function') {
        try {
          const layered = new LayeredNumber(value);
          if (layered instanceof LayeredNumber) {
            return layered.toString();
          }
        } catch (error) {
          // ignore conversion error and fall back to numeric formatting
        }
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
    let activeDropCount = 0;
    let balanceIntervalId = null;

    const stats = {
      drops: 0,
      wins: 0,
      streak: 0,
      bestMultiplier: 0
    };

    const historyEntries = [];

    function formatBetAmount(amount) {
      const layered = createLayeredAmount(amount);
      if (layered) {
        return formatLayeredNumber(layered);
      }
      if (!Number.isFinite(amount)) {
        return '0';
      }
      const numeric = Math.floor(amount);
      return formatLayeredNumber(numeric);
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
      const disabled = selectedBet == null || !canAffordBet(selectedBet);
      dropButton.disabled = disabled;
    }

    function updateBetButtons() {
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const amount = Number(button.dataset.bet);
        const disable = !canAffordBet(amount);
        button.disabled = disable;
        button.classList.toggle('pachinko-bet__option--unavailable', disable);
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
        multiplyButton.disabled = false;
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
        const disabled = betMultiplier <= 1;
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
          const scaled = baseAmount * betMultiplier;
          setSelectedBet(scaled, baseAmount);
        });
        betButtons.push(button);
        betOptionsElement.appendChild(button);
      }
      updateBetOptionValues();
    }

    function nowSeconds() {
      if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
        return performance.now() / 1000;
      }
      return Date.now() / 1000;
    }

    function buildPegField() {
      const pegs = [];
      if (slotCount <= 0) {
        return pegs;
      }
      const usableRows = Math.max(3, rowCount);
      const verticalSpan = boardField.height * 0.76;
      const verticalSpacing = verticalSpan / (usableRows + 1);
      const startY = boardField.top + verticalSpacing;
      const centerX = boardField.left + boardField.width / 2;
      let hasCentralPeg = false;

      for (let row = 0; row < usableRows; row += 1) {
        const baseY = startY + row * verticalSpacing;
        const rowPegCount = Math.max(3, Math.round(slotCount * (0.45 + Math.random() * 0.3)));
        const segmentWidth = boardField.width / (rowPegCount + 1);
        for (let index = 0; index < rowPegCount; index += 1) {
          const baseX = boardField.left + segmentWidth * (index + 1);
          const jitterX = randomOffset(segmentWidth * 0.4);
          const jitterY = randomOffset(verticalSpacing * 0.28);
          const pegX = clampValue(baseX + jitterX, boardField.left + 0.035, boardField.right - 0.035);
          const pegY = clampValue(baseY + jitterY, boardField.top + 0.08, boardField.bottom - 0.2);
          if (Math.abs(pegX - centerX) <= boardField.width * 0.08) {
            hasCentralPeg = true;
          }
          pegs.push({
            x: pegX,
            y: pegY,
            radius: 0.026,
            bounce: 0.66,
            spin: 0.045,
            type: 'peg'
          });
        }
      }

      if (!hasCentralPeg) {
        const extraY = clampValue(
          boardField.top + boardField.height * (0.25 + Math.random() * 0.4),
          boardField.top + 0.12,
          boardField.bottom - 0.28
        );
        const extraX = clampValue(
          centerX + randomOffset(boardField.width * 0.12),
          boardField.left + 0.04,
          boardField.right - 0.04
        );
        pegs.push({
          x: extraX,
          y: extraY,
          radius: 0.028,
          bounce: 0.68,
          spin: 0.05,
          type: 'peg'
        });
      }

      const movingConfigs = [
        { yRatio: 0.28, radius: 0.032, travelSeconds: 2, startRatio: 1 },
        { yRatio: 0.33, radius: 0.03, travelSeconds: 4, startRatio: 0.25 },
        { yRatio: 0.52, radius: 0.034, travelSeconds: 6, startRatio: 0.65 },
        { yRatio: 0.6, radius: 0.031, travelSeconds: 10, startRatio: 0 }
      ];
      for (let i = 0; i < movingConfigs.length; i += 1) {
        const config = movingConfigs[i];
        const radius = clampValue(config.radius || 0.032, 0.02, 0.05);
        const travelMin = boardField.left + radius + MOVING_PEG_TRAVEL_MARGIN;
        const travelMax = boardField.right - radius - MOVING_PEG_TRAVEL_MARGIN;
        const travelWidth = Math.max(0, travelMax - travelMin);
        const amplitude = travelWidth / 2;
        const baseX = travelMin + amplitude;
        const normalizedStart = clampValue(
          typeof config.startRatio === 'number' ? config.startRatio : Math.random(),
          0,
          1
        );
        const startOffset = clampValue(normalizedStart * 2 - 1, -1, 1);
        const phase = Math.asin(startOffset);
        const travelSeconds = Math.max(0.5, Number(config.travelSeconds) || 2);
        const speed = Math.PI / travelSeconds;
        const initialX = amplitude > 0 ? baseX + amplitude * Math.sin(phase) : baseX;
        pegs.push({
          x: initialX,
          baseX,
          y: clampValue(
            boardField.top + boardField.height * config.yRatio,
            boardField.top + 0.12,
            boardField.bottom - 0.2
          ),
          radius,
          bounce: 0.78,
          spin: 0.1,
          type: 'moving',
          amplitude,
          speed,
          phase
        });
      }

      return pegs;
    }

    function updateMovingPegPhysics(timeSeconds) {
      if (!movingPegStates.length) {
        return;
      }
      for (let i = 0; i < movingPegStates.length; i += 1) {
        const state = movingPegStates[i];
        const { physicsPeg } = state;
        const amplitude = physicsPeg.amplitude || 0;
        const speed = physicsPeg.speed || 0.6;
        const phase = physicsPeg.phase || 0;
        const offset = Math.sin(timeSeconds * speed + phase) * amplitude;
        const minX = boardField.left + physicsPeg.radius + MOVING_PEG_TRAVEL_MARGIN;
        const maxX = boardField.right - physicsPeg.radius - MOVING_PEG_TRAVEL_MARGIN;
        physicsPeg.x = clampValue(physicsPeg.baseX + offset, minX, maxX);
      }
    }

    function updateMovingPegDisplays() {
      if (!movingPegStates.length) {
        return;
      }
      for (let i = 0; i < movingPegStates.length; i += 1) {
        const state = movingPegStates[i];
        const { element, physicsPeg } = state;
        const leftPercent = clampValue((physicsPeg.x - physicsPeg.radius) * 100, 0, 100);
        element.style.left = `${leftPercent}%`;
      }
    }

    function animateMovingPegs() {
      updateMovingPegPhysics(nowSeconds());
      updateMovingPegDisplays();
      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        movingPegAnimationHandle = window.requestAnimationFrame(animateMovingPegs);
      }
    }

    function startMovingPegAnimation() {
      if (typeof window === 'undefined' || typeof window.requestAnimationFrame !== 'function') {
        return;
      }
      if (movingPegAnimationHandle != null && typeof window.cancelAnimationFrame === 'function') {
        window.cancelAnimationFrame(movingPegAnimationHandle);
      }
      if (!movingPegStates.length) {
        movingPegAnimationHandle = null;
        return;
      }
      movingPegAnimationHandle = window.requestAnimationFrame(animateMovingPegs);
    }

    function renderPegLayer() {
      if (!pegLayerElement) {
        return;
      }
      pegLayerElement.innerHTML = '';
      physicsPegs.length = 0;
      movingPegStates.length = 0;
      const layout = buildPegField();
      for (let i = 0; i < layout.length; i += 1) {
        const peg = layout[i];
        const element = document.createElement('span');
        element.className = 'pachinko-peg';
        const diameterPercent = clampValue(peg.radius * 200, 1.8, 18);
        element.style.width = `${diameterPercent}%`;
        element.style.height = `${diameterPercent}%`;
        const leftPercent = clampValue((peg.x - peg.radius) * 100, 0, 100);
        const topPercent = clampValue((peg.y - peg.radius) * 100, 0, 100);
        element.style.left = `${leftPercent}%`;
        element.style.top = `${topPercent}%`;
        pegLayerElement.appendChild(element);
        const physicsPeg = {
          x: peg.x,
          y: peg.y,
          radius: peg.radius,
          bounce: peg.bounce,
          spin: peg.spin,
          type: peg.type
        };
        if (peg.type === 'moving') {
          element.classList.add('pachinko-peg--moving');
          physicsPeg.baseX = typeof peg.baseX === 'number' ? peg.baseX : peg.x;
          physicsPeg.amplitude = peg.amplitude || 0;
          physicsPeg.speed = Math.max(0.2, peg.speed || 0.6);
          physicsPeg.phase = peg.phase || 0;
          movingPegStates.push({ element, physicsPeg });
        }
        physicsPegs.push(physicsPeg);
      }
      startMovingPegAnimation();
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
        slotElement.setAttribute('data-i18n-aria-label', slotDefinition.labelKey);
        slotElement.setAttribute('aria-label', translate(slotDefinition.labelKey, slotDefinition.fallback));
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
      window.setTimeout(() => {
        for (let i = 0; i < slotElements.length; i += 1) {
          slotElements[i].classList.remove('pachinko-slot--active');
        }
      }, 1500);
    }

    function animateBall(points) {
      return new Promise(resolve => {
        if (!boardElement || !Array.isArray(points) || !points.length) {
          resolve();
          return;
        }
        const ball = document.createElement('div');
        ball.className = 'pachinko-board__ball pachinko-board__ball--active';
        ball.setAttribute('aria-hidden', 'true');
        ball.style.top = '-8%';
        ball.style.left = '50%';
        ball.style.opacity = '1';
        ball.style.transform = 'translate(-50%, -50%)';
        boardElement.appendChild(ball);
        let stepIndex = 0;
        const totalSteps = points.length;
        const frameDelay = Math.max(14, Math.min(36, Math.floor(dropStepMs / 7)));

        function step() {
          if (!ball.isConnected) {
            resolve();
            return;
          }
          const { x, y } = points[stepIndex];
          const verticalPercent = clampValue(y * 100, -10, 100);
          const horizontalPercent = clampValue(x * 100, 0, 100);
          ball.style.top = `${verticalPercent}%`;
          ball.style.left = `${horizontalPercent}%`;
          stepIndex += 1;
          if (stepIndex < totalSteps) {
            window.setTimeout(step, frameDelay);
          } else {
            window.setTimeout(() => {
              ball.classList.remove('pachinko-board__ball--active');
              ball.style.opacity = '0';
              window.setTimeout(() => {
                if (ball.parentElement) {
                  ball.parentElement.removeChild(ball);
                }
                resolve();
              }, 160);
            }, frameDelay + 80);
          }
        }

        step();
      });
    }

    function createFallbackPath() {
      const fallbackPoints = [];
      const totalSteps = rowCount + 4;
      const centerX = boardField.left + boardField.width / 2;
      for (let step = 0; step <= totalSteps; step += 1) {
        const progress = step / totalSteps;
        const y = boardField.top + progress * (boardField.height + 0.1);
        fallbackPoints.push({ x: centerX, y });
      }
      return {
        points: fallbackPoints,
        slotIndex: Math.floor(slotCount / 2)
      };
    }

    function generatePath() {
      if (!physicsPegs.length) {
        return createFallbackPath();
      }

      const points = [];
      const simulationStart = nowSeconds();
      updateMovingPegPhysics(simulationStart);
      const dropMargin = 0.12;
      let spawnRatio = lastDropSpawnRatio;
      for (let attempt = 0; attempt < 4; attempt += 1) {
        const candidate = clampValue(
          dropMargin + Math.random() * (1 - dropMargin * 2),
          dropMargin,
          1 - dropMargin
        );
        if (Math.abs(candidate - lastDropSpawnRatio) >= 0.12 || attempt === 3) {
          spawnRatio = candidate;
          break;
        }
      }
      lastDropSpawnRatio = spawnRatio;

      let x = clampValue(
        boardField.left + boardField.width * spawnRatio + randomOffset(boardField.width * 0.02),
        boardField.left + BALL_RADIUS,
        boardField.right - BALL_RADIUS
      );
      let y = boardField.top - 0.12;
      let vx = randomOffset(0.022);
      let vy = 0.0025;

      for (let step = 0; step < MAX_SIMULATION_STEPS; step += 1) {
        vy += GRAVITY_FORCE;
        vx *= 0.998;
        vy *= 0.999;

        x += vx;
        y += vy;

        if (x < boardField.left + BALL_RADIUS) {
          x = boardField.left + BALL_RADIUS;
          vx = Math.abs(vx) * 0.62;
        } else if (x > boardField.right - BALL_RADIUS) {
          x = boardField.right - BALL_RADIUS;
          vx = -Math.abs(vx) * 0.62;
        }

        if (y < boardField.top - 0.05) {
          y = boardField.top - 0.05;
          vy = Math.abs(vy) * 0.6;
        }

        const stepTime = simulationStart + step * 0.016;
        updateMovingPegPhysics(stepTime);
        for (let i = 0; i < physicsPegs.length; i += 1) {
          const peg = physicsPegs[i];
          const minimumDistance = peg.radius + BALL_RADIUS;
          const dx = x - peg.x;
          const dy = y - peg.y;
          const distanceSq = dx * dx + dy * dy;
          if (distanceSq >= minimumDistance * minimumDistance || distanceSq === 0) {
            continue;
          }
          const distance = Math.sqrt(distanceSq) || minimumDistance;
          const nx = dx / (distance || 1);
          const ny = dy / (distance || 1);
          const overlap = minimumDistance - distance + 0.0006;
          x += nx * overlap;
          y += ny * overlap;
          const velocityDot = vx * nx + vy * ny;
          if (velocityDot < 0) {
            vx -= (1 + peg.bounce) * velocityDot * nx;
            vy -= (1 + peg.bounce) * velocityDot * ny;
          }
          vx *= peg.bounce;
          vy *= peg.bounce;
          const tangentX = -ny;
          const tangentY = nx;
          const tangentForce = randomOffset(peg.spin * 1.6);
          vx += tangentX * tangentForce;
          vy += tangentY * tangentForce * 0.6;
          vx += randomOffset(peg.spin * 0.8);
          vy += randomOffset(peg.spin * 0.35);
          vx *= 0.94;
          vy *= 0.9;
          if (vy < GRAVITY_FORCE * 6) {
            vy = GRAVITY_FORCE * 6;
          }
          if (peg.type === 'moving') {
            vx *= 1.02;
          }
        }

        if (step % PATH_SAMPLE_INTERVAL === 0) {
          points.push({ x, y });
        }

        if (y >= boardField.bottom) {
          y = boardField.bottom;
          points.push({ x, y });
          break;
        }
      }

      if (!points.length) {
        return createFallbackPath();
      }

      const normalizedX = clampValue((x - boardField.left) / boardField.width, 0, 1);
      const slotIndex = Math.max(0, Math.min(slotCount - 1, Math.round(normalizedX * (slotCount - 1))));
      const slotCenterX = clampValue(
        slotTargets[slotIndex] != null ? slotTargets[slotIndex] : boardField.left + boardField.width / 2,
        boardField.left + BALL_RADIUS,
        boardField.right - BALL_RADIUS
      );
      const settleY = Math.min(1, boardField.bottom + 0.08);
      points.push({ x: slotCenterX, y: settleY });
      points.push({ x: slotCenterX, y: Math.min(1, settleY + 0.05) });

      return { points, slotIndex };
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

      let appliedAdvantage = null;
      if (net.sign <= 0 && advantageBonusMultiplier > 0) {
        const bonus = layeredBet.multiplyNumber(advantageBonusMultiplier);
        if (bonus instanceof LayeredNumber) {
          appliedAdvantage = bonus;
          payout = payout ? payout.add(bonus) : bonus;
          net = net.add(bonus);
        }
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
        net: formatLayeredNumber(net),
        bonus: appliedAdvantage ? formatMultiplier(advantageBonusMultiplier) : null
      };

      if (net.sign > 0) {
        if (appliedAdvantage && multiplier <= 0) {
          setStatus('advantage', 'Advantage bonus awarded!', params);
        } else {
          setStatus('win', 'You win!', params);
        }
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

      updateDropButtonState();
    }

    function beginDrop() {
      if (selectedBet == null) {
        setStatus('selectBet', 'Select a bet first.');
        if (typeof showToast === 'function') {
          showToast(translate('scripts.arcade.pachinko.status.selectBet', 'Select a bet first.'));
        }
        return false;
      }
      if (!canAffordBet(selectedBet)) {
        setStatus('insufficientAtoms', 'Not enough atoms for this bet.');
        if (typeof showToast === 'function') {
          showToast(translate('scripts.arcade.pachinko.status.insufficientAtoms', 'Not enough atoms for this bet.'));
        }
        ensureSelectedBetAffordable();
        updateBalanceDisplay();
        return false;
      }
      const layeredBet = createLayeredAmount(selectedBet);
      if (!layeredBet) {
        return false;
      }
      activeDropCount += 1;
      if (activeDropCount > 1) {
        setStatus('droppingMultiple', 'Multiple orbs descending...', { count: `${activeDropCount}` });
      } else {
        setStatus('dropping', 'Dropping...');
      }

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
      updateBetButtons();
      updateMultiplierControls();
      updateDropButtonState();

      const pathResult = generatePath();
      const pathPoints = pathResult && Array.isArray(pathResult.points) ? pathResult.points : [];
      const landingIndex = pathResult && Number.isInteger(pathResult.slotIndex)
        ? pathResult.slotIndex
        : Math.floor(slotCount / 2);
      animateBall(pathPoints).then(() => {
        const index = Math.max(0, Math.min(slotDefinitions.length - 1, landingIndex));
        const slotDefinition = slotDefinitions[index] || slotDefinitions[Math.floor(slotDefinitions.length / 2)];
        finishDrop(index, slotDefinition, layeredBet);
        activeDropCount = Math.max(0, activeDropCount - 1);
        ensureSelectedBetAffordable();
        updateBetButtons();
        updateMultiplierControls();
        updateDropButtonState();
      });
      return true;
    }

    if (dropButton) {
      dropButton.addEventListener('click', () => {
        beginDrop();
      });
    }

    if (multiplyButton) {
      multiplyButton.addEventListener('click', () => {
        setBetMultiplier(betMultiplier * 10);
      });
    }

    if (divideButton) {
      divideButton.addEventListener('click', () => {
        if (betMultiplier <= 1) {
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
        if (activeDropCount === 0) {
          setStatus('ready', 'Pick a bet and drop a quantum orb.');
        }
      });
    }
  });
})();
