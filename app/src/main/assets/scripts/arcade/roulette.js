(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const CONFIG_PATH = 'config/arcade/roulette.json';
  const DEFAULT_BET_AMOUNTS = Object.freeze([10, 20, 50]);
  const DEFAULT_MAX_APC_BET_MULTIPLIER = 100;
  const DEFAULT_PAYOUTS = Object.freeze({
    suitLine: 5,
    suitDiagonal: 5,
    colorLine: 2,
    colorDiagonal: 2,
    jokerRowTopBottom: 10,
    jokerRowMiddle: 25
  });
  const DEFAULT_ANIMATION = Object.freeze({ initialMs: 2500, columnDelayMs: 1000, shuffleIntervalMs: 100 });

  const SYMBOLS = Object.freeze([
    Object.freeze({ id: 'hearts', label: '♥', color: 'red', translationKey: 'scripts.arcade.roulette.symbols.hearts' }),
    Object.freeze({ id: 'diamonds', label: '♦', color: 'red', translationKey: 'scripts.arcade.roulette.symbols.diamonds' }),
    Object.freeze({ id: 'clubs', label: '♣', color: 'black', translationKey: 'scripts.arcade.roulette.symbols.clubs' }),
    Object.freeze({ id: 'spades', label: '♠', color: 'black', translationKey: 'scripts.arcade.roulette.symbols.spades' }),
    Object.freeze({ id: 'joker', label: '🃏', color: 'joker', translationKey: 'scripts.arcade.roulette.symbols.joker' }),
    Object.freeze({ id: 'void', label: '●', color: 'void', translationKey: 'scripts.arcade.roulette.symbols.void' })
  ]);

  const SYMBOL_BY_ID = SYMBOLS.reduce((map, symbol) => {
    map.set(symbol.id, symbol);
    return map;
  }, new Map());

  const DEFAULT_SYMBOL_WEIGHTS = Object.freeze({
    hearts: 1,
    diamonds: 1,
    clubs: 1,
    spades: 1,
    joker: 0.5,
    void: 2
  });

  const DEFAULT_CONFIG = Object.freeze({
    betOptions: DEFAULT_BET_AMOUNTS,
    maxBetApcMultiplier: DEFAULT_MAX_APC_BET_MULTIPLIER,
    payouts: DEFAULT_PAYOUTS,
    animation: DEFAULT_ANIMATION,
    symbolWeights: DEFAULT_SYMBOL_WEIGHTS
  });

  const state = {
    config: resolveInitialConfig()
  };

  let currentSymbolPicker = null;

  function normalizeSymbolWeights(weights, fallbackWeights) {
    const fallback = fallbackWeights && typeof fallbackWeights === 'object'
      ? fallbackWeights
      : DEFAULT_CONFIG.symbolWeights;
    const normalized = {};
    let total = 0;
    const source = weights && typeof weights === 'object' ? weights : null;
    for (let i = 0; i < SYMBOLS.length; i += 1) {
      const symbol = SYMBOLS[i];
      const fallbackValue = Number(fallback[symbol.id]);
      const raw = source != null ? Number(source[symbol.id]) : NaN;
      const value = Number.isFinite(raw) && raw > 0
        ? raw
        : Number.isFinite(fallbackValue) && fallbackValue > 0
          ? fallbackValue
          : 0;
      normalized[symbol.id] = value;
      total += value;
    }
    if (total <= 0) {
      for (let i = 0; i < SYMBOLS.length; i += 1) {
        const symbol = SYMBOLS[i];
        const fallbackValue = Number(fallback[symbol.id]);
        normalized[symbol.id] = Number.isFinite(fallbackValue) && fallbackValue > 0 ? fallbackValue : 1;
      }
    }
    return normalized;
  }

  function createSymbolPicker(weights) {
    const normalized = normalizeSymbolWeights(weights);
    const entries = [];
    let cumulative = 0;
    for (let i = 0; i < SYMBOLS.length; i += 1) {
      const symbol = SYMBOLS[i];
      const weight = Number(normalized[symbol.id]);
      if (Number.isFinite(weight) && weight > 0) {
        cumulative += weight;
        entries.push({ symbol, cumulative });
      }
    }
    if (!entries.length) {
      return () => SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)];
    }
    if (entries.length === 1) {
      const onlySymbol = entries[0].symbol;
      return () => onlySymbol;
    }
    const totalWeight = entries[entries.length - 1].cumulative;
    return () => {
      const roll = Math.random() * totalWeight;
      for (let i = 0; i < entries.length; i += 1) {
        if (roll < entries[i].cumulative) {
          return entries[i].symbol;
        }
      }
      return entries[entries.length - 1].symbol;
    };
  }

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
        // Silently fallback to provided text.
      }
    }

    return fallback;
  }

  function normalizeBetOptions(options, fallbackOptions) {
    const fallback = Array.isArray(fallbackOptions) && fallbackOptions.length
      ? fallbackOptions
      : DEFAULT_CONFIG.betOptions;
    const source = Array.isArray(options) && options.length ? options : fallback;
    const normalized = [];
    const seen = new Set();
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
      return fallback.slice();
    }
    normalized.sort((a, b) => a - b);
    return normalized;
  }

  function normalizePayouts(payouts, fallbackPayouts) {
    const fallback = fallbackPayouts && typeof fallbackPayouts === 'object'
      ? fallbackPayouts
      : DEFAULT_CONFIG.payouts;
    const source = payouts && typeof payouts === 'object' ? payouts : {};
    const sanitize = (value, fallback) => {
      const numeric = Number(value);
      return Number.isFinite(numeric) && numeric > 0 ? numeric : fallback;
    };
    const suitLine = sanitize(source.suitLine, fallback.suitLine);
    const suitDiagonal = sanitize(
      source.suitDiagonal != null ? source.suitDiagonal : source.suitLine,
      fallback.suitDiagonal
    );
    const colorLine = sanitize(source.colorLine, fallback.colorLine);
    const colorDiagonal = sanitize(
      source.colorDiagonal != null ? source.colorDiagonal : source.diagonalColor,
      fallback.colorDiagonal
    );
    const jokerRowMiddle = sanitize(
      source.jokerRowMiddle != null ? source.jokerRowMiddle : source.jokerRow,
      fallback.jokerRowMiddle
    );
    const jokerRowTopBottom = sanitize(
      source.jokerRowTopBottom != null ? source.jokerRowTopBottom : source.jokerRow,
      fallback.jokerRowTopBottom
    );
    return {
      suitLine,
      suitDiagonal,
      colorLine,
      colorDiagonal,
      jokerRowTopBottom,
      jokerRowMiddle
    };
  }

  function normalizeAnimation(animation, fallbackAnimation) {
    const fallback = fallbackAnimation && typeof fallbackAnimation === 'object'
      ? fallbackAnimation
      : DEFAULT_CONFIG.animation;
    const source = animation && typeof animation === 'object' ? animation : {};
    const initial = Number(source.initialMs);
    const columnDelay = Number(source.columnDelayMs);
    const shuffle = Number(source.shuffleIntervalMs);
    return {
      initialMs: Number.isFinite(initial) && initial > 0
        ? initial
        : Number.isFinite(fallback.initialMs) && fallback.initialMs > 0
          ? fallback.initialMs
          : DEFAULT_ANIMATION.initialMs,
      columnDelayMs: Number.isFinite(columnDelay) && columnDelay >= 0
        ? columnDelay
        : Number.isFinite(fallback.columnDelayMs) && fallback.columnDelayMs >= 0
          ? fallback.columnDelayMs
          : DEFAULT_ANIMATION.columnDelayMs,
      shuffleIntervalMs: Number.isFinite(shuffle) && shuffle > 0
        ? shuffle
        : Number.isFinite(fallback.shuffleIntervalMs) && fallback.shuffleIntervalMs > 0
          ? fallback.shuffleIntervalMs
          : DEFAULT_ANIMATION.shuffleIntervalMs
    };
  }

  function normalizeConfig(rawConfig, fallbackConfig) {
    const fallback = fallbackConfig && typeof fallbackConfig === 'object' ? fallbackConfig : DEFAULT_CONFIG;
    const betOptions = normalizeBetOptions(rawConfig?.betOptions, fallback.betOptions);
    const rawMultiplier = Number(rawConfig?.maxBetApcMultiplier);
    const fallbackMultiplier = Number(fallback.maxBetApcMultiplier);
    const maxBetApcMultiplier = Number.isFinite(rawMultiplier) && rawMultiplier > 0
      ? rawMultiplier
      : Number.isFinite(fallbackMultiplier) && fallbackMultiplier > 0
        ? fallbackMultiplier
        : DEFAULT_MAX_APC_BET_MULTIPLIER;
    const payouts = normalizePayouts(rawConfig?.payouts, fallback.payouts);
    const animation = normalizeAnimation(rawConfig?.animation, fallback.animation);
    const symbolWeights = normalizeSymbolWeights(rawConfig?.symbolWeights, fallback.symbolWeights);
    return Object.freeze({
      betOptions: Object.freeze(betOptions.slice()),
      maxBetApcMultiplier,
      payouts: Object.freeze({ ...payouts }),
      animation: Object.freeze({ ...animation }),
      symbolWeights: Object.freeze({ ...symbolWeights })
    });
  }

  function resolveInitialConfig() {
    const globalConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.roulette
      ? GLOBAL_CONFIG.arcade.roulette
      : null;
    return normalizeConfig(globalConfig, DEFAULT_CONFIG);
  }

  function setConfig(nextConfig) {
    state.config = normalizeConfig(nextConfig, state.config || DEFAULT_CONFIG);
    return state.config;
  }

  function loadRemoteConfig(onLoaded) {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    window.fetch(CONFIG_PATH, { cache: 'no-store' })
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          const updated = setConfig(data);
          if (typeof onLoaded === 'function') {
            onLoaded(updated);
          }
        }
      })
      .catch(error => {
        console.warn('Roulette config load error', error);
      });
  }

  function getGameAtoms() {
    if (typeof gameState === 'undefined' || !gameState) {
      return null;
    }
    const atoms = gameState.atoms;
    if (atoms instanceof LayeredNumber) {
      return atoms;
    }
    if (typeof LayeredNumber === 'function') {
      try {
        const layered = new LayeredNumber(atoms ?? 0);
        gameState.atoms = layered;
        return layered;
      } catch (error) {
        return null;
      }
    }
    return null;
  }

  function getGameApc() {
    if (typeof gameState === 'undefined' || !gameState) {
      return null;
    }
    const apc = gameState.perClick;
    if (apc instanceof LayeredNumber) {
      return apc;
    }
    if (typeof LayeredNumber === 'function') {
      try {
        const layered = new LayeredNumber(apc ?? 0);
        gameState.perClick = layered;
        return layered;
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
        // Fallback to locale formatting.
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
        // Ignore and fallback to default.
      }
    }
    if (typeof numeric.toLocaleString === 'function') {
      return numeric.toLocaleString();
    }
    return `${numeric}`;
  }

  function randomSymbol() {
    if (!currentSymbolPicker) {
      currentSymbolPicker = createSymbolPicker();
    }
    return currentSymbolPicker();
  }

  function translateSymbolName(symbol) {
    if (!symbol) {
      return '';
    }
    const fallbackMap = {
      hearts: 'Cœur',
      diamonds: 'Carreau',
      clubs: 'Trèfle',
      spades: 'Pique',
      joker: 'Joker',
      void: 'Trou noir'
    };
    return translate(symbol.translationKey, fallbackMap[symbol.id] || symbol.id);
  }

  function translateColor(color) {
    switch (color) {
      case 'red':
        return translate('scripts.arcade.roulette.colors.red', 'Rouge');
      case 'black':
        return translate('scripts.arcade.roulette.colors.black', 'Noir');
      default:
        return color;
    }
  }

  function translateJokerRowPosition(rowOrKey) {
    let key = rowOrKey;
    if (typeof rowOrKey === 'number') {
      if (rowOrKey === 0) {
        key = 'top';
      } else if (rowOrKey === 1) {
        key = 'middle';
      } else {
        key = 'bottom';
      }
    }
    let fallback;
    switch (key) {
      case 'top':
        fallback = 'supérieure';
        break;
      case 'middle':
        fallback = 'centrale';
        break;
      default:
        key = 'bottom';
        fallback = 'inférieure';
        break;
    }
    return translate(`scripts.arcade.roulette.rows.${key}`, fallback);
  }

  function createResultGrid() {
    const grid = [];
    for (let row = 0; row < 3; row += 1) {
      const rowSymbols = [];
      for (let col = 0; col < 3; col += 1) {
        rowSymbols.push(randomSymbol());
      }
      grid.push(rowSymbols);
    }
    return grid;
  }

  function evaluateGrid(grid, payouts) {
    const wins = [];

    function getUniformSuit(cells) {
      if (!cells || !cells.length) {
        return null;
      }
      let baseSuit = null;
      for (let i = 0; i < cells.length; i += 1) {
        const cell = cells[i];
        if (!cell || cell.id === 'void') {
          return null;
        }
        if (cell.id === 'joker') {
          continue;
        }
        if (baseSuit == null) {
          baseSuit = cell.id;
        } else if (cell.id !== baseSuit) {
          return null;
        }
      }
      return baseSuit;
    }

    function getUniformColor(cells) {
      if (!cells || !cells.length) {
        return null;
      }
      let baseColor = null;
      for (let i = 0; i < cells.length; i += 1) {
        const cell = cells[i];
        if (!cell || cell.color === 'void') {
          return null;
        }
        if (cell.id === 'joker') {
          continue;
        }
        if (cell.color !== 'red' && cell.color !== 'black') {
          return null;
        }
        if (baseColor == null) {
          baseColor = cell.color;
        } else if (cell.color !== baseColor) {
          return null;
        }
      }
      return baseColor;
    }

    const diagonals = [
      {
        cells: [grid[0][0], grid[1][1], grid[2][2]],
        coords: [
          { row: 0, col: 0 },
          { row: 1, col: 1 },
          { row: 2, col: 2 }
        ]
      },
      {
        cells: [grid[0][2], grid[1][1], grid[2][0]],
        coords: [
          { row: 0, col: 2 },
          { row: 1, col: 1 },
          { row: 2, col: 0 }
        ]
      }
    ];

    for (let i = 0; i < diagonals.length; i += 1) {
      const diagonal = diagonals[i];
      if (!diagonal) {
        continue;
      }
      const uniformSuit = getUniformSuit(diagonal.cells);
      if (uniformSuit) {
        wins.push({
          type: 'suitDiagonal',
          suit: uniformSuit,
          multiplier: payouts.suitDiagonal,
          cells: diagonal.coords
        });
      }
      const uniformColor = getUniformColor(diagonal.cells);
      if (uniformColor) {
        wins.push({
          type: 'colorDiagonal',
          color: uniformColor,
          multiplier: payouts.colorDiagonal,
          cells: diagonal.coords
        });
      }
    }

    for (let row = 0; row < 3; row += 1) {
      const rowCells = [grid[row][0], grid[row][1], grid[row][2]];
      const allJokers = rowCells.every(cell => cell && cell.id === 'joker');
      if (allJokers) {
        const multiplier = row === 1 ? payouts.jokerRowMiddle : payouts.jokerRowTopBottom;
        wins.push({
          type: 'jokerRow',
          row,
          multiplier,
          cells: [
            { row, col: 0 },
            { row, col: 1 },
            { row, col: 2 }
          ]
        });
      }

      const rowSuit = getUniformSuit(rowCells);
      if (rowSuit) {
        wins.push({
          type: 'suitLine',
          orientation: 'row',
          row,
          suit: rowSuit,
          multiplier: payouts.suitLine,
          cells: [
            { row, col: 0 },
            { row, col: 1 },
            { row, col: 2 }
          ]
        });
      }

      const rowColor = getUniformColor(rowCells);
      if (rowColor) {
        wins.push({
          type: 'colorLine',
          orientation: 'row',
          row,
          color: rowColor,
          multiplier: payouts.colorLine,
          cells: [
            { row, col: 0 },
            { row, col: 1 },
            { row, col: 2 }
          ]
        });
      }
    }

    for (let col = 0; col < 3; col += 1) {
      const columnCells = [grid[0][col], grid[1][col], grid[2][col]];
      const columnSuit = getUniformSuit(columnCells);
      if (columnSuit) {
        wins.push({
          type: 'suitLine',
          orientation: 'column',
          column: col,
          suit: columnSuit,
          multiplier: payouts.suitLine,
          cells: [
            { row: 0, col },
            { row: 1, col },
            { row: 2, col }
          ]
        });
      }

      const columnColor = getUniformColor(columnCells);
      if (columnColor) {
        wins.push({
          type: 'colorLine',
          orientation: 'column',
          column: col,
          color: columnColor,
          multiplier: payouts.colorLine,
          cells: [
            { row: 0, col },
            { row: 1, col },
            { row: 2, col }
          ]
        });
      }
    }

    const totalMultiplier = wins.reduce((sum, win) => sum + (Number(win.multiplier) || 0), 0);

    return { wins, totalMultiplier };
  }

  onReady(() => {
    const page = document.getElementById('roulette');
    if (!page) {
      return;
    }

    const columnElements = Array.from(page.querySelectorAll('[data-roulette-column]'));
    const cellElements = Array.from(page.querySelectorAll('[data-roulette-cell]'));
    if (columnElements.length !== 3 || cellElements.length !== 9) {
      return;
    }

    const startButton = document.getElementById('rouletteSpinButton');
    const statusElement = document.getElementById('rouletteStatus');
    const currentBetElement = document.getElementById('rouletteCurrentBet');
    const balanceElement = document.getElementById('rouletteAtomsBalance');
    const multiplierElement = document.getElementById('rouletteLastMultiplier');
    const betOptionsElement = document.getElementById('rouletteBetOptions');
    const betSummaryElement = document.getElementById('rouletteBetSummary');
    const betMultiplierElement = document.getElementById('rouletteBetMultiplier');
    const lastWinElement = document.getElementById('rouletteLastWin');
    const betContainer = document.getElementById('rouletteBet');

    if (!startButton || !statusElement || !betOptionsElement || !betSummaryElement || !betMultiplierElement || !lastWinElement) {
      return;
    }

    const cellMatrix = [
      [null, null, null],
      [null, null, null],
      [null, null, null]
    ];

    cellElements.forEach(cell => {
      const row = Number(cell.dataset.row);
      const col = Number(cell.dataset.col);
      if (Number.isInteger(row) && Number.isInteger(col) && row >= 0 && row < 3 && col >= 0 && col < 3) {
        cellMatrix[row][col] = cell;
      }
    });

    let currentConfig = state.config;
    let baseBetOptions = currentConfig.betOptions.slice();
    let payouts = currentConfig.payouts;
    let animation = currentConfig.animation;
    currentSymbolPicker = createSymbolPicker(currentConfig.symbolWeights);

    let betMultiplier = 1;
    let betOptions = baseBetOptions.map(value => value);
    let selectedBet = null;
    let selectedBaseBet = null;
    let spinActive = false;
    let activeBet = null;
    let balanceIntervalId = null;
    let finalGrid = null;
    const spinIntervals = [null, null, null];
    const spinTimeouts = [null, null, null];
    const betButtons = [];
    let multiplyButton = null;
    let divideButton = null;

    function getBetLimitMultiplier() {
      const multiplier = Number(currentConfig?.maxBetApcMultiplier);
      return Number.isFinite(multiplier) && multiplier > 0
        ? multiplier
        : DEFAULT_MAX_APC_BET_MULTIPLIER;
    }

    function getMaxAllowedBet() {
      const apc = getGameApc();
      const multiplier = getBetLimitMultiplier();
      if (!apc || typeof apc.multiplyNumber !== 'function') {
        return null;
      }
      try {
        return apc.multiplyNumber(multiplier);
      } catch (error) {
        return null;
      }
    }

    function isBetWithinLimit(amount) {
      const maxBet = getMaxAllowedBet();
      if (!maxBet) {
        return true;
      }
      const bet = createLayeredBet(amount);
      if (!bet || typeof bet.compare !== 'function') {
        return true;
      }
      try {
        return bet.compare(maxBet) <= 0;
      } catch (error) {
        return true;
      }
    }

    function canPlayBet(amount) {
      return canAffordBet(amount) && isBetWithinLimit(amount);
    }

    function hasBetWithinLimitForMultiplier(multiplierValue) {
      if (!Number.isFinite(multiplierValue) || multiplierValue <= 0) {
        return false;
      }
      if (!getMaxAllowedBet()) {
        return true;
      }
      for (let i = 0; i < baseBetOptions.length; i += 1) {
        const candidate = baseBetOptions[i] * multiplierValue;
        if (isBetWithinLimit(candidate)) {
          return true;
        }
      }
      return false;
    }

    function showBetLimitWarning() {
      const limit = getMaxAllowedBet();
      const multiplier = getBetLimitMultiplier();
      const formattedLimit = formatBetAmount(limit || 0);
      const formattedMultiplier = formatBetAmount(multiplier);
      setStatus(
        'betLimit',
        `Mise limitée à ${formattedLimit} atomes (max ${formattedMultiplier}× APC).`,
        { limit: formattedLimit, multiplier: formattedMultiplier }
      );
    }

    function applyConfig(nextConfig) {
      const effective = nextConfig || state.config;
      currentConfig = effective;
      baseBetOptions = effective.betOptions.slice();
      payouts = effective.payouts;
      animation = effective.animation;
      currentSymbolPicker = createSymbolPicker(effective.symbolWeights);
      const previousBase = selectedBaseBet;
      const previousBet = selectedBet;
      betMultiplier = Math.max(1, Math.floor(betMultiplier) || 1);
      initializeBetOptions();
      if (previousBase != null && baseBetOptions.includes(previousBase)) {
        setSelectedBet(previousBase * betMultiplier, previousBase);
      } else if (previousBet != null) {
        setSelectedBet(previousBet);
      }
      ensureSelectedBetAffordable();
      updateBetButtons();
      updateBalanceDisplay();
    }

    function applySymbolToCell(cell, symbol) {
      if (!cell || !symbol) {
        return;
      }
      cell.textContent = symbol.label;
      cell.dataset.symbol = symbol.id;
      cell.dataset.color = symbol.color;
      cell.setAttribute('aria-label', translateSymbolName(symbol));
    }

    function clearHighlights() {
      for (let row = 0; row < 3; row += 1) {
        for (let col = 0; col < 3; col += 1) {
          const cell = cellMatrix[row][col];
          if (cell) {
            cell.classList.remove('roulette-cell--highlight');
          }
        }
      }
    }

    function highlightWins(wins) {
      clearHighlights();
      if (!Array.isArray(wins) || !wins.length) {
        return;
      }
      for (let i = 0; i < wins.length; i += 1) {
        const win = wins[i];
        if (!win || !Array.isArray(win.cells)) {
          continue;
        }
        for (let j = 0; j < win.cells.length; j += 1) {
          const info = win.cells[j];
          if (!info) {
            continue;
          }
          const cell = cellMatrix[info.row]?.[info.col];
          if (cell) {
            cell.classList.add('roulette-cell--highlight');
          }
        }
      }
    }

    function setStatus(key, fallback, params) {
      if (!statusElement) {
        return;
      }
      const message = translate(`scripts.arcade.roulette.status.${key}`, fallback, params);
      statusElement.textContent = message;
    }

    function updateLastWinDisplay(betAmount, multiplier, payoutAmount) {
      if (!lastWinElement) {
        return;
      }
      const betValue = betAmount != null ? betAmount : 0;
      const numericMultiplier = Number(multiplier);
      const multiplierValue = Number.isFinite(numericMultiplier) && numericMultiplier > 0 ? numericMultiplier : 0;
      const betDisplay = formatBetAmount(betValue);
      const multiplierDisplay = formatBetAmount(multiplierValue);
      let totalDisplay = formatBetAmount(0);

      if (payoutAmount != null) {
        totalDisplay = formatBetAmount(payoutAmount);
      } else if (betAmount != null && multiplierValue > 0) {
        try {
          if (typeof betAmount.multiplyNumber === 'function') {
            totalDisplay = formatBetAmount(betAmount.multiplyNumber(multiplierValue));
          } else {
            const numericBet = Number(betAmount);
            if (Number.isFinite(numericBet)) {
              totalDisplay = formatBetAmount(numericBet * multiplierValue);
            }
          }
        } catch (error) {
          const numericBet = Number(betAmount);
          if (Number.isFinite(numericBet)) {
            totalDisplay = formatBetAmount(numericBet * multiplierValue);
          }
        }
      }

      const fallback = `Dernier gain : ${betDisplay} × ${multiplierDisplay} = ${totalDisplay}`;
      const message = translate(
        'scripts.arcade.roulette.lastWin',
        fallback,
        { bet: betDisplay, multiplier: multiplierDisplay, total: totalDisplay }
      );
      lastWinElement.textContent = message;
    }

    function updateMultiplierDisplay(multiplier) {
      if (!multiplierElement) {
        return;
      }
      if (multiplier == null) {
        multiplierElement.textContent = '×1';
        return;
      }
      const clamped = multiplier > 0 ? multiplier : 0;
      multiplierElement.textContent = `×${formatBetAmount(clamped)}`;
    }

    function updateBalanceDisplay() {
      if (!balanceElement) {
        return;
      }
      const atoms = getGameAtoms();
      balanceElement.textContent = atoms ? atoms.toString() : '0';
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
        if (!spinActive) {
          ensureSelectedBetAffordable();
          updateBetButtons();
        }
      }, 1500);
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
            const baseNumeric = Number(baseAmount);
            if (Number.isFinite(baseNumeric) && baseNumeric > 0) {
              selectedBaseBet = Math.floor(baseNumeric);
            } else if (betMultiplier > 0) {
              selectedBaseBet = Math.floor(selectedBet / betMultiplier);
            }
          } else if (betMultiplier > 0) {
            selectedBaseBet = Math.floor(selectedBet / betMultiplier);
          }
        }
      }
      updateBetSummary();
      updateCurrentBetDisplay();
    }

    function updateCurrentBetDisplay() {
      const display = selectedBet != null ? formatBetAmount(selectedBet) : '0';
      if (currentBetElement) {
        currentBetElement.textContent = display;
      }
    }

    function updateBetSummary() {
      if (betSummaryElement) {
        const label = selectedBet != null
          ? translate('index.sections.roulette.bet.summary', `{amount} atomes`, { amount: formatBetAmount(selectedBet) })
          : translate('index.sections.roulette.bet.none', 'Aucune');
        betSummaryElement.textContent = label;
      }
      if (betMultiplierElement) {
        const formattedMultiplier = formatBetAmount(betMultiplier);
        betMultiplierElement.textContent = `×${formattedMultiplier}`;
      }
    }

    function updateScaleButtons() {
      const formattedMultiplier = formatBetAmount(betMultiplier);
      if (multiplyButton) {
        multiplyButton.textContent = translate(
          'index.sections.roulette.bet.scale.multiplyLabel',
          '×10'
        );
        multiplyButton.setAttribute(
          'aria-label',
          translate(
            'index.sections.roulette.bet.scale.multiplyAria',
            `Multiplier les mises par 10 (multiplicateur actuel : ×${formattedMultiplier})`,
            { multiplier: formattedMultiplier }
          )
        );
        const nextMultiplier = betMultiplier * 10;
        const limitReached = !hasBetWithinLimitForMultiplier(nextMultiplier);
        const disabled = spinActive || limitReached;
        multiplyButton.disabled = disabled;
        multiplyButton.classList.toggle('roulette-bet__option--unavailable', disabled);
      }
      if (divideButton) {
        divideButton.textContent = translate(
          'index.sections.roulette.bet.scale.divideLabel',
          '÷10'
        );
        divideButton.setAttribute(
          'aria-label',
          translate(
            'index.sections.roulette.bet.scale.divideAria',
            `Diviser les mises par 10 (multiplicateur actuel : ×${formattedMultiplier})`,
            { multiplier: formattedMultiplier }
          )
        );
        const disabled = spinActive || betMultiplier <= 1;
        divideButton.disabled = disabled;
        divideButton.classList.toggle('roulette-bet__option--unavailable', disabled);
      }
    }

    function updateBetOptionValues() {
      betOptions = baseBetOptions.map(amount => amount * betMultiplier);
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const baseAmount = baseBetOptions[i];
        const scaledAmount = betOptions[i];
        if (!button) {
          continue;
        }
        button.dataset.baseBet = `${baseAmount}`;
        button.dataset.bet = `${scaledAmount}`;
        button.textContent = translate(
          'index.sections.roulette.bet.option',
          `${formatBetAmount(scaledAmount)} atomes`,
          { amount: formatBetAmount(scaledAmount) }
        );
      }
      updateBetSummary();
      updateScaleButtons();
      ensureSelectedBetAffordable();
    }

    function updateBetButtons() {
      for (let i = 0; i < betButtons.length; i += 1) {
        const button = betButtons[i];
        const amount = Number(button.dataset.bet);
        const isSelected = selectedBet != null && Number.isFinite(amount) && amount === selectedBet;
        button.classList.toggle('roulette-bet__option--selected', isSelected);
        const playable = canPlayBet(amount);
        const disableClass = !spinActive && !playable;
        button.classList.toggle('roulette-bet__option--unavailable', disableClass);
        button.disabled = spinActive;
      }
      updateScaleButtons();
    }

    function setBetMultiplier(value) {
      const numeric = Number(value);
      if (!Number.isFinite(numeric) || numeric <= 0) {
        return;
      }
      const normalized = Math.max(1, Math.floor(numeric));
      if (normalized === betMultiplier) {
        return;
      }
      betMultiplier = normalized;
      updateBetOptionValues();
      if (selectedBaseBet != null) {
        setSelectedBet(selectedBaseBet * betMultiplier, selectedBaseBet);
      }
      updateBetButtons();
      updateBetSummary();
    }

    function canAffordBet(amount) {
      const atoms = getGameAtoms();
      const bet = createLayeredBet(amount);
      if (!atoms || !bet) {
        return false;
      }
      return atoms.compare(bet) >= 0;
    }

    function ensureSelectedBetAffordable() {
      if (spinActive) {
        return;
      }
      if (selectedBet != null && canPlayBet(selectedBet)) {
        return;
      }
      for (let i = betOptions.length - 1; i >= 0; i -= 1) {
        const candidate = betOptions[i];
        if (canPlayBet(candidate)) {
          const base = baseBetOptions[i];
          setSelectedBet(candidate, base);
          updateBetButtons();
          return;
        }
      }
      setSelectedBet(null);
      updateBetButtons();
    }

    function clearSpinTimers() {
      for (let i = 0; i < spinIntervals.length; i += 1) {
        if (spinIntervals[i] != null && typeof window !== 'undefined') {
          window.clearInterval(spinIntervals[i]);
        }
        spinIntervals[i] = null;
      }
      for (let i = 0; i < spinTimeouts.length; i += 1) {
        if (spinTimeouts[i] != null && typeof window !== 'undefined') {
          window.clearTimeout(spinTimeouts[i]);
        }
        spinTimeouts[i] = null;
      }
    }

    function beginColumnShuffle(columnIndex) {
      if (typeof window === 'undefined') {
        return;
      }
      const columnElement = columnElements[columnIndex];
      if (!columnElement) {
        return;
      }
      columnElement.classList.add('roulette-column--spinning');
      const intervalId = window.setInterval(() => {
        for (let row = 0; row < 3; row += 1) {
          const cell = cellMatrix[row][columnIndex];
          if (cell) {
            applySymbolToCell(cell, randomSymbol());
          }
        }
      }, Math.max(45, animation.shuffleIntervalMs));
      spinIntervals[columnIndex] = intervalId;
    }

    function stopColumnShuffle(columnIndex, grid) {
      if (columnIndex < 0 || columnIndex >= columnElements.length) {
        return;
      }
      if (spinIntervals[columnIndex] != null && typeof window !== 'undefined') {
        window.clearInterval(spinIntervals[columnIndex]);
      }
      spinIntervals[columnIndex] = null;
      const columnElement = columnElements[columnIndex];
      if (columnElement) {
        columnElement.classList.remove('roulette-column--spinning');
      }
      for (let row = 0; row < 3; row += 1) {
        const cell = cellMatrix[row][columnIndex];
        if (cell) {
          applySymbolToCell(cell, grid[row][columnIndex]);
        }
      }
      if (columnIndex === columnElements.length - 1) {
        finalizeSpin();
      }
    }

    function setControlsDisabled(disabled) {
      startButton.disabled = disabled;
      updateBetButtons();
    }

    function finalizeSpin() {
      if (!finalGrid) {
        return;
      }
      clearSpinTimers();
      const { wins, totalMultiplier } = evaluateGrid(finalGrid, payouts);
      highlightWins(wins);
      updateMultiplierDisplay(totalMultiplier);

      const betForResult = activeBet;
      let payoutAmount = null;

      if (totalMultiplier > 0 && betForResult && typeof gameState !== 'undefined') {
        try {
          payoutAmount = betForResult.multiplyNumber(totalMultiplier);
          gameState.atoms = gameState.atoms.add(payoutAmount);
          if (typeof updateUI === 'function') {
            updateUI();
          }
          if (typeof saveGame === 'function') {
            saveGame();
          }
        } catch (error) {
          // Ignore payout errors but continue gracefully.
        }
      }

      updateBalanceDisplay();
      ensureSelectedBetAffordable();
      updateLastWinDisplay(betForResult, totalMultiplier, payoutAmount);

      if (!wins.length) {
        setStatus('lose', 'Pas de gain cette fois.');
      } else if (wins.length === 1) {
        const win = wins[0];
        const multiplierText = formatBetAmount(win.multiplier);
        if (win.type === 'suitLine') {
          const suitSymbol = SYMBOL_BY_ID.get(win.suit);
          const suit = translateSymbolName(suitSymbol);
          const orientation = win.orientation === 'column' ? 'column' : 'row';
          const translationKey = orientation === 'column' ? 'winSuitColumn' : 'winSuitLine';
          const fallback =
            orientation === 'column'
              ? `Colonne de ${suit} ! Gain ×${multiplierText}.`
              : `Ligne de ${suit} ! Gain ×${multiplierText}.`;
          setStatus(translationKey, fallback, { suit, multiplier: multiplierText });
        } else if (win.type === 'suitDiagonal') {
          const suitSymbol = SYMBOL_BY_ID.get(win.suit);
          const suit = translateSymbolName(suitSymbol);
          setStatus(
            'winSuitDiagonal',
            `Diagonale de ${suit} ! Gain ×${multiplierText}.`,
            { suit, multiplier: multiplierText }
          );
        } else if (win.type === 'colorLine') {
          const color = translateColor(win.color);
          const orientation = win.orientation === 'column' ? 'column' : 'row';
          const translationKey = orientation === 'column' ? 'winColorColumn' : 'winColorLine';
          const fallback =
            orientation === 'column'
              ? `Colonne ${color} ! Bonus couleur ×${multiplierText}.`
              : `Ligne ${color} ! Bonus couleur ×${multiplierText}.`;
          setStatus(translationKey, fallback, { color, multiplier: multiplierText });
        } else if (win.type === 'colorDiagonal') {
          const color = translateColor(win.color);
          setStatus(
            'winColorDiagonal',
            `Diagonale ${color} ! Bonus couleur ×${multiplierText}.`,
            { color, multiplier: multiplierText }
          );
        } else if (win.type === 'jokerRow') {
          const positionKey = win.row === 0 ? 'top' : win.row === 1 ? 'middle' : 'bottom';
          const position = translateJokerRowPosition(positionKey);
          setStatus(
            'winJokerRowPosition',
            `Ligne ${position} de Jokers ! Gain ×${multiplierText}.`,
            { position, multiplier: multiplierText }
          );
        } else {
          setStatus('win', `Gain ×${formatBetAmount(totalMultiplier)}.`);
        }
      } else {
        setStatus(
          'winMultiple',
          `Combinaisons multiples ! Gain total ×${formatBetAmount(totalMultiplier)}.`,
          {
            multiplier: formatBetAmount(totalMultiplier),
            count: wins.length
          }
        );
      }

      activeBet = null;
      finalGrid = null;
      spinActive = false;
      setControlsDisabled(false);
    }

    function startSpin() {
      if (spinActive) {
        return;
      }
      if (selectedBet == null || selectedBet <= 0) {
        setStatus('selectBet', 'Sélectionnez une mise avant de lancer la roulette.');
        return;
      }
      if (!isBetWithinLimit(selectedBet)) {
        showBetLimitWarning();
        ensureSelectedBetAffordable();
        updateBetButtons();
        return;
      }
      const layeredBet = createLayeredBet(selectedBet);
      const atoms = getGameAtoms();
      if (!layeredBet || !atoms || atoms.compare(layeredBet) < 0) {
        setStatus('insufficientAtoms', 'Solde insuffisant pour cette mise.');
        updateBalanceDisplay();
        ensureSelectedBetAffordable();
        return;
      }

      try {
        gameState.atoms = atoms.subtract(layeredBet);
        activeBet = layeredBet;
        if (typeof updateUI === 'function') {
          updateUI();
        }
        if (typeof saveGame === 'function') {
          saveGame();
        }
      } catch (error) {
        setStatus('insufficientAtoms', 'Solde insuffisant pour cette mise.');
        return;
      }

      updateBalanceDisplay();
      spinActive = true;
      clearSpinTimers();
      clearHighlights();
      finalGrid = createResultGrid();
      setStatus('spinning', 'La roulette tourne…');
      setControlsDisabled(true);
      updateMultiplierDisplay(null);

      for (let columnIndex = 0; columnIndex < columnElements.length; columnIndex += 1) {
        beginColumnShuffle(columnIndex);
        if (typeof window !== 'undefined') {
          const delay = animation.initialMs + animation.columnDelayMs * columnIndex;
          spinTimeouts[columnIndex] = window.setTimeout(() => {
            stopColumnShuffle(columnIndex, finalGrid);
          }, delay);
        }
      }
    }

    function initializeBetOptions() {
      betOptionsElement.innerHTML = '';
      betButtons.length = 0;

      const scaleGroup = document.createElement('div');
      scaleGroup.className = 'roulette-bet__scale';

      multiplyButton = document.createElement('button');
      multiplyButton.type = 'button';
      multiplyButton.className = 'roulette-bet__option roulette-bet__scale-button';
      multiplyButton.addEventListener('click', () => {
        if (spinActive) {
          return;
        }
        setBetMultiplier(betMultiplier * 10);
      });

      divideButton = document.createElement('button');
      divideButton.type = 'button';
      divideButton.className = 'roulette-bet__option roulette-bet__scale-button';
      divideButton.addEventListener('click', () => {
        if (spinActive || betMultiplier <= 1) {
          return;
        }
        setBetMultiplier(betMultiplier / 10);
      });

      scaleGroup.appendChild(divideButton);
      scaleGroup.appendChild(multiplyButton);
      betOptionsElement.appendChild(scaleGroup);

      for (let i = 0; i < baseBetOptions.length; i += 1) {
        const baseAmount = baseBetOptions[i];
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'roulette-bet__option';
        button.dataset.baseBet = `${baseAmount}`;
        button.addEventListener('click', () => {
          if (spinActive) {
            return;
          }
          const amount = Number(button.dataset.bet);
          if (!isBetWithinLimit(amount)) {
            showBetLimitWarning();
            return;
          }
          if (!canAffordBet(amount)) {
            setStatus('insufficientAtoms', 'Solde insuffisant pour cette mise.');
            return;
          }
          setSelectedBet(amount, baseAmount);
          updateBetButtons();
        });
        betOptionsElement.appendChild(button);
        betButtons.push(button);
      }

      if (startButton) {
        betOptionsElement.appendChild(startButton);
      }

      updateBetOptionValues();
      if (baseBetOptions.length) {
        setSelectedBet(baseBetOptions[0] * betMultiplier, baseBetOptions[0]);
        updateBetButtons();
      }
    }

    function initializeGrid() {
      for (let row = 0; row < 3; row += 1) {
        for (let col = 0; col < 3; col += 1) {
          const cell = cellMatrix[row][col];
          if (cell) {
            const symbol = SYMBOL_BY_ID.get(cell.dataset.symbol) || randomSymbol();
            applySymbolToCell(cell, symbol);
          }
        }
      }
    }

    initializeGrid();
    initializeBetOptions();
    updateBetSummary();
    updateBalanceDisplay();
    updateMultiplierDisplay(null);
    updateLastWinDisplay(0, 0, 0);
    startBalanceUpdates();

    loadRemoteConfig(applyConfig);

    startButton.addEventListener('click', startSpin);

    if (betContainer) {
      betContainer.addEventListener('focusin', () => {
        ensureSelectedBetAffordable();
        updateBetButtons();
      });
    }
  });
})();
