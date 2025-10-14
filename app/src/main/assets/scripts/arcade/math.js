(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;

  const DEFAULT_CONFIG = Object.freeze({
    optionsCount: 6,
    termCountRange: Object.freeze({ min: 2, max: 5 }),
    baseRange: Object.freeze({ min: 1, max: 9 }),
    advancedRange: Object.freeze({ min: 2, max: 12 }),
    expertRange: Object.freeze({ min: 2, max: 15 }),
    thresholds: Object.freeze({
      multiply: 3,
      divide: 7,
      expert: 12
    }),
    resultLimits: Object.freeze({
      base: 40,
      advanced: 80,
      expert: 120
    }),
    maxMistakes: 3,
    roundTimerSeconds: 30,
    symbolMode: Object.freeze({
      enabled: true,
      fullSymbolUnlockLevel: 10,
      earlySymbols: Object.freeze(['+', '-']),
      fullSymbols: Object.freeze(['+', '-', '*', '/'])
    }),
    rewards: Object.freeze({
      gachaTicket: Object.freeze({
        amount: 0,
        chance: 0
      })
    })
  });

  const PLACEHOLDER_SYMBOL = '?';
  const MAX_GENERATION_ATTEMPTS = 120;
  const DISPLAY_OPERATOR_MAP = Object.freeze({
    '*': 'x',
    '/': '÷'
  });
  const ALLOWED_OPERATORS = Object.freeze(['+', '-', '*', '/']);

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
        console.warn('Math game translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return fallback;
    }
    return typeof key === 'string' ? key : '';
  }

  function normalizeRange(range, fallback, minValue) {
    const safeFallback = fallback || { min: 1, max: 1 };
    const source = range && typeof range === 'object' ? range : {};
    const minimum = Number.isFinite(source.min) ? Math.floor(source.min) : safeFallback.min;
    const maximum = Number.isFinite(source.max) ? Math.floor(source.max) : safeFallback.max;
    const normalizedMin = Math.max(minValue, minimum);
    const normalizedMax = Math.max(normalizedMin, maximum);
    return { min: normalizedMin, max: normalizedMax };
  }

  function normalizeThresholds(thresholds, fallback) {
    const safeFallback = fallback || {};
    const source = thresholds && typeof thresholds === 'object' ? thresholds : {};
    const multiply = Number.isFinite(source.multiply) ? Math.max(0, Math.floor(source.multiply)) : safeFallback.multiply;
    const divide = Number.isFinite(source.divide) ? Math.max(multiply, Math.floor(source.divide)) : safeFallback.divide;
    const expert = Number.isFinite(source.expert) ? Math.max(divide, Math.floor(source.expert)) : safeFallback.expert;
    return { multiply, divide, expert };
  }

  function normalizeResultLimits(limits, fallback) {
    const safeFallback = fallback || {};
    const source = limits && typeof limits === 'object' ? limits : {};
    const base = Number.isFinite(source.base) ? Math.max(10, Math.floor(source.base)) : safeFallback.base;
    const advanced = Number.isFinite(source.advanced) ? Math.max(base, Math.floor(source.advanced)) : safeFallback.advanced;
    const expert = Number.isFinite(source.expert) ? Math.max(advanced, Math.floor(source.expert)) : safeFallback.expert;
    return { base, advanced, expert };
  }

  function normalizeSymbolList(list, fallback) {
    const fallbackList = Array.isArray(fallback) ? fallback : [];
    const source = Array.isArray(list) ? list : [];
    const sanitized = [];
    source.forEach(value => {
      if (typeof value !== 'string') {
        return;
      }
      const trimmed = value.trim();
      if (!trimmed) {
        return;
      }
      if (!ALLOWED_OPERATORS.includes(trimmed)) {
        return;
      }
      if (!sanitized.includes(trimmed)) {
        sanitized.push(trimmed);
      }
    });
    if (sanitized.length > 0) {
      return sanitized;
    }
    const fallbackSanitized = [];
    fallbackList.forEach(value => {
      if (typeof value !== 'string') {
        return;
      }
      const trimmed = value.trim();
      if (ALLOWED_OPERATORS.includes(trimmed) && !fallbackSanitized.includes(trimmed)) {
        fallbackSanitized.push(trimmed);
      }
    });
    if (fallbackSanitized.length > 0) {
      return fallbackSanitized;
    }
    return ['+', '-'];
  }

  function normalizeSymbolMode(symbolMode, fallback) {
    const safeFallback = fallback || DEFAULT_CONFIG.symbolMode;
    const source = symbolMode && typeof symbolMode === 'object' ? symbolMode : {};
    const enabled = source.enabled !== false;
    const unlockLevel = Number.isFinite(source.fullSymbolUnlockLevel)
      ? Math.max(1, Math.floor(source.fullSymbolUnlockLevel))
      : safeFallback.fullSymbolUnlockLevel;
    const fullSymbols = normalizeSymbolList(source.fullSymbols, safeFallback.fullSymbols);
    const earlySymbolsSource = normalizeSymbolList(source.earlySymbols, safeFallback.earlySymbols);
    const earlySymbols = earlySymbolsSource.filter(symbol => fullSymbols.includes(symbol));
    if (earlySymbols.length < 2) {
      fullSymbols.forEach(symbol => {
        if (!earlySymbols.includes(symbol) && earlySymbols.length < 2) {
          earlySymbols.push(symbol);
        }
      });
    }
    const optionCount = Math.max(2, Math.min(fullSymbols.length, 8));
    const normalizedEarly = Object.freeze(earlySymbols.slice(0, optionCount));
    const normalizedFull = Object.freeze(fullSymbols.slice(0, optionCount));
    return {
      enabled,
      fullSymbolUnlockLevel: unlockLevel,
      earlySymbols: normalizedEarly,
      fullSymbols: normalizedFull
    };
  }

  function normalizeRewards(rewards, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : DEFAULT_CONFIG.rewards;
    const source = rewards && typeof rewards === 'object' ? rewards : {};
    const gachaSource = source.gachaTicket && typeof source.gachaTicket === 'object'
      ? source.gachaTicket
      : source.gacha && typeof source.gacha === 'object'
        ? source.gacha
        : {};
    const fallbackGacha = base && typeof base.gachaTicket === 'object' ? base.gachaTicket : {};

    const amountCandidates = [
      gachaSource.amount,
      gachaSource.value,
      gachaSource.tickets,
      fallbackGacha.amount,
      DEFAULT_CONFIG.rewards.gachaTicket.amount
    ];
    let amount = 0;
    for (let index = 0; index < amountCandidates.length; index += 1) {
      const candidate = Number(amountCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        amount = Math.floor(candidate);
        break;
      }
    }

    const chanceCandidates = [
      gachaSource.chance,
      gachaSource.probability,
      gachaSource.rate,
      fallbackGacha.chance,
      DEFAULT_CONFIG.rewards.gachaTicket.chance
    ];
    let chance = 0;
    for (let index = 0; index < chanceCandidates.length; index += 1) {
      const candidate = Number(chanceCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        chance = candidate;
        break;
      }
    }

    return {
      gachaTicket: {
        amount: Math.max(0, amount),
        chance: Math.max(0, Math.min(1, chance))
      }
    };
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

  function getGameConfig() {
    const arcadeConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade ? GLOBAL_CONFIG.arcade : null;
    const mathConfig = arcadeConfig && typeof arcadeConfig.math === 'object' ? arcadeConfig.math : null;
    if (!mathConfig) {
      return DEFAULT_CONFIG;
    }
    const termCountRange = normalizeRange(mathConfig.termCountRange, DEFAULT_CONFIG.termCountRange, 2);
    const baseRange = normalizeRange(mathConfig.baseRange, DEFAULT_CONFIG.baseRange, 1);
    const advancedRange = normalizeRange(mathConfig.advancedRange, DEFAULT_CONFIG.advancedRange, 1);
    const expertRange = normalizeRange(mathConfig.expertRange, DEFAULT_CONFIG.expertRange, 1);
    const thresholds = normalizeThresholds(mathConfig.thresholds, DEFAULT_CONFIG.thresholds);
    const resultLimits = normalizeResultLimits(mathConfig.resultLimits, DEFAULT_CONFIG.resultLimits);
    const optionsCount = Number.isFinite(mathConfig.optionsCount)
      ? Math.max(3, Math.floor(mathConfig.optionsCount))
      : DEFAULT_CONFIG.optionsCount;
    const roundTimerSeconds = Number.isFinite(mathConfig.roundTimerSeconds)
      ? Math.max(1, Math.floor(mathConfig.roundTimerSeconds))
      : DEFAULT_CONFIG.roundTimerSeconds;
    const maxMistakes = Number.isFinite(mathConfig.maxMistakes)
      ? Math.max(1, Math.floor(mathConfig.maxMistakes))
      : DEFAULT_CONFIG.maxMistakes;
    const symbolMode = normalizeSymbolMode(mathConfig.symbolMode, DEFAULT_CONFIG.symbolMode);
    const rewards = normalizeRewards(mathConfig.rewards, DEFAULT_CONFIG.rewards);
    return {
      optionsCount,
      termCountRange,
      baseRange,
      advancedRange,
      expertRange,
      thresholds,
      resultLimits,
      maxMistakes,
      roundTimerSeconds,
      symbolMode,
      rewards
    };
  }

  function randomInt(min, max) {
    const lower = Math.ceil(min);
    const upper = Math.floor(max);
    if (upper <= lower) {
      return lower;
    }
    return Math.floor(Math.random() * (upper - lower + 1)) + lower;
  }

  function shuffle(array) {
    const copy = Array.isArray(array) ? array.slice() : [];
    for (let i = copy.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = copy[i];
      copy[i] = copy[j];
      copy[j] = temp;
    }
    return copy;
  }

  function evaluateExpression(numbers, operations) {
    if (!Array.isArray(numbers) || numbers.length === 0) {
      return { valid: false, value: 0 };
    }
    const workingNumbers = numbers.slice();
    const workingOps = Array.isArray(operations) ? operations.slice() : [];

    for (let index = 0; index < workingOps.length;) {
      const operator = workingOps[index];
      if (operator === '*' || operator === '/') {
        const left = workingNumbers[index];
        const right = workingNumbers[index + 1];
        if (!Number.isFinite(left) || !Number.isFinite(right)) {
          return { valid: false, value: 0 };
        }
        if (operator === '/') {
          if (right === 0 || !Number.isInteger(left) || left % right !== 0) {
            return { valid: false, value: 0 };
          }
          const result = left / right;
          if (!Number.isInteger(result)) {
            return { valid: false, value: 0 };
          }
          workingNumbers.splice(index, 2, result);
          workingOps.splice(index, 1);
          continue;
        }
        const product = left * right;
        if (!Number.isFinite(product)) {
          return { valid: false, value: 0 };
        }
        workingNumbers.splice(index, 2, product);
        workingOps.splice(index, 1);
      } else {
        index += 1;
      }
    }

    let total = workingNumbers[0];
    if (!Number.isFinite(total)) {
      return { valid: false, value: 0 };
    }

    for (let i = 0; i < workingOps.length; i += 1) {
      const operator = workingOps[i];
      const value = workingNumbers[i + 1];
      if (!Number.isFinite(value)) {
        return { valid: false, value: 0 };
      }
      if (operator === '+') {
        total += value;
      } else if (operator === '-') {
        total -= value;
      } else {
        return { valid: false, value: 0 };
      }
      if (!Number.isFinite(total)) {
        return { valid: false, value: 0 };
      }
    }

    if (!Number.isInteger(total)) {
      return { valid: false, value: 0 };
    }

    return { valid: true, value: total };
  }

  function determineStage(correctCount, config, mode) {
    const level = Math.max(1, correctCount + 1);
    if (mode === 'symbols') {
      const symbolConfig = config.symbolMode || DEFAULT_CONFIG.symbolMode;
      const unlockLevel = Math.max(1, symbolConfig.fullSymbolUnlockLevel || DEFAULT_CONFIG.symbolMode.fullSymbolUnlockLevel);
      const usingFullSet = level >= unlockLevel;
      const operations = usingFullSet
        ? Array.from(symbolConfig.fullSymbols || DEFAULT_CONFIG.symbolMode.fullSymbols)
        : Array.from(symbolConfig.earlySymbols || DEFAULT_CONFIG.symbolMode.earlySymbols);
      if (!operations.length) {
        operations.push('+', '-');
      }
      let range = config.baseRange;
      let maxResult = config.resultLimits.base;
      let termRange = level <= 5 ? { min: 2, max: 2 } : { min: 3, max: 3 };
      if (usingFullSet) {
        range = config.advancedRange;
        maxResult = config.resultLimits.advanced;
        termRange = { min: 3, max: 3 };
        if (level > config.thresholds.divide) {
          termRange = { min: 3, max: 4 };
        }
        if (level > config.thresholds.expert) {
          range = config.expertRange;
          maxResult = config.resultLimits.expert;
          termRange = { min: 3, max: 5 };
        }
      }
      const stage = {
        operations,
        range,
        termRange,
        maxResult,
        hideType: 'operator',
        optionValues: operations.slice(),
        optionCount: Math.max(2, operations.length)
      };
      if (usingFullSet) {
        stage.requireMultiplicative = true;
      }
      return stage;
    }

    let operations = ['+', '-'];
    let range = config.baseRange;
    let maxResult = config.resultLimits.base;
    let termRange = { min: 2, max: 2 };
    let requireMultiplicative = false;

    if (level <= 5) {
      termRange = { min: 2, max: 2 };
      operations = ['+', '-'];
      range = config.baseRange;
      maxResult = config.resultLimits.base;
    } else if (level <= 10) {
      termRange = { min: 3, max: 3 };
      operations = ['+', '-'];
      range = config.baseRange;
      maxResult = config.resultLimits.base;
    } else if (level <= 20) {
      termRange = { min: 3, max: 3 };
      operations = ['+', '-', '*', '/'];
      range = config.advancedRange;
      maxResult = config.resultLimits.advanced;
      requireMultiplicative = true;
    } else {
      termRange = { min: 3, max: 5 };
      operations = ['+', '-', '*', '/'];
      range = config.expertRange;
      maxResult = config.resultLimits.expert;
    }

    const stage = {
      operations,
      range,
      termRange,
      maxResult,
      hideType: 'number'
    };
    if (requireMultiplicative) {
      stage.requireMultiplicative = true;
    }
    return stage;
  }

  function buildOptions(correctValue, stage, config) {
    const targetCount = Math.max(3, config.optionsCount);
    const values = new Set([correctValue]);
    const spanMin = Math.max(1, Math.min(stage.range.min, correctValue - 6));
    const spanMax = Math.max(spanMin, Math.max(stage.range.max, correctValue + 6));
    let attempts = 0;

    while (values.size < targetCount && attempts < 200) {
      attempts += 1;
      const candidate = randomInt(spanMin, spanMax);
      if (candidate <= 0 || values.has(candidate)) {
        continue;
      }
      values.add(candidate);
    }

    while (values.size < targetCount && attempts < 400) {
      attempts += 1;
      const candidate = randomInt(stage.range.min, stage.range.max);
      if (candidate <= 0 || values.has(candidate)) {
        continue;
      }
      values.add(candidate);
    }

    let filler = 1;
    while (values.size < targetCount && filler < 20) {
      const candidate = correctValue + filler;
      if (!values.has(candidate) && candidate > 0) {
        values.add(candidate);
      }
      filler += 1;
    }

    const shuffled = shuffle(Array.from(values));
    if (shuffled.length > targetCount) {
      return shuffled.slice(0, targetCount);
    }
    return shuffled;
  }

  function buildSymbolOptions(hiddenValue, stage, symbolModeConfig) {
    const config = symbolModeConfig || DEFAULT_CONFIG.symbolMode;
    const baseValues = Array.isArray(stage.optionValues) && stage.optionValues.length
      ? Array.from(stage.optionValues)
      : Array.isArray(config.fullSymbols)
        ? Array.from(config.fullSymbols)
        : Array.from(DEFAULT_CONFIG.symbolMode.fullSymbols);
    const targetCount = Math.max(2, stage.optionCount || baseValues.length || 2);
    const poolSet = new Set();
    baseValues.forEach(symbol => {
      if (typeof symbol === 'string' && ALLOWED_OPERATORS.includes(symbol)) {
        poolSet.add(symbol);
      }
    });
    if (typeof hiddenValue === 'string' && ALLOWED_OPERATORS.includes(hiddenValue)) {
      poolSet.add(hiddenValue);
    }
    if (poolSet.size < targetCount) {
      const fallbackFull = Array.isArray(config.fullSymbols) && config.fullSymbols.length
        ? config.fullSymbols
        : DEFAULT_CONFIG.symbolMode.fullSymbols;
      fallbackFull.forEach(symbol => {
        if (typeof symbol === 'string' && ALLOWED_OPERATORS.includes(symbol)) {
          poolSet.add(symbol);
        }
      });
    }
    if (poolSet.size < targetCount) {
      const fallbackEarly = Array.isArray(config.earlySymbols) && config.earlySymbols.length
        ? config.earlySymbols
        : DEFAULT_CONFIG.symbolMode.earlySymbols;
      fallbackEarly.forEach(symbol => {
        if (typeof symbol === 'string' && ALLOWED_OPERATORS.includes(symbol)) {
          poolSet.add(symbol);
        }
      });
    }
    const pool = Array.from(poolSet).filter(symbol => ALLOWED_OPERATORS.includes(symbol));
    if (typeof hiddenValue === 'string' && !pool.includes(hiddenValue)) {
      pool.unshift(hiddenValue);
    }
    if (pool.length < targetCount) {
      return null;
    }
    const others = shuffle(pool.filter(symbol => symbol !== hiddenValue));
    const result = [hiddenValue];
    for (let index = 0; index < others.length && result.length < targetCount; index += 1) {
      result.push(others[index]);
    }
    if (result.length < targetCount) {
      return null;
    }
    return shuffle(result);
  }

  function generateRoundForStage(stage, config) {
    if (!stage.operations.length) {
      stage = Object.assign({}, stage, { operations: ['+'] });
    }
    const hideType = stage.hideType === 'operator' ? 'operator' : 'number';
    for (let attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt += 1) {
      const termCount = randomInt(stage.termRange.min, stage.termRange.max);
      const numbers = [];
      for (let i = 0; i < termCount; i += 1) {
        numbers.push(randomInt(stage.range.min, stage.range.max));
      }
      const operations = [];
      for (let i = 0; i < termCount - 1; i += 1) {
        const opIndex = randomInt(0, stage.operations.length - 1);
        operations.push(stage.operations[opIndex]);
      }
      if (stage.requireMultiplicative) {
        const hasMultiplicative = operations.some(operator => operator === '*' || operator === '/');
        if (!hasMultiplicative) {
          continue;
        }
      }
      const evaluation = evaluateExpression(numbers, operations);
      if (!evaluation.valid) {
        continue;
      }
      if (!Number.isFinite(evaluation.value) || Math.abs(evaluation.value) > stage.maxResult) {
        continue;
      }
      if (hideType === 'operator') {
        if (!operations.length) {
          continue;
        }
        const hiddenIndex = randomInt(0, operations.length - 1);
        const hiddenValue = operations[hiddenIndex];
        const options = buildSymbolOptions(hiddenValue, stage, config.symbolMode);
        const required = Math.max(2, stage.optionCount || 2);
        if (!Array.isArray(options) || options.length < required) {
          continue;
        }
        return {
          numbers,
          operations,
          result: evaluation.value,
          hiddenIndex,
          hiddenValue,
          options,
          hiddenType: 'operator'
        };
      }
      const hiddenIndex = randomInt(0, numbers.length - 1);
      const hiddenValue = numbers[hiddenIndex];
      if (!Number.isInteger(hiddenValue) || hiddenValue <= 0) {
        continue;
      }
      const options = buildOptions(hiddenValue, stage, config);
      if (!Array.isArray(options) || options.length < Math.max(3, config.optionsCount)) {
        continue;
      }
      return {
        numbers,
        operations,
        result: evaluation.value,
        hiddenIndex,
        hiddenValue,
        options,
        hiddenType: 'number'
      };
    }
    return null;
  }

  function createFallbackRound(config, mode) {
    if (mode === 'symbols') {
      const numbers = [3, 4];
      const operations = ['+'];
      const evaluation = evaluateExpression(numbers, operations);
      const symbolStage = {
        optionValues: (config.symbolMode && config.symbolMode.earlySymbols) || DEFAULT_CONFIG.symbolMode.earlySymbols,
        optionCount: Math.max(2, (config.symbolMode && config.symbolMode.earlySymbols && config.symbolMode.earlySymbols.length) || 2)
      };
      const options = buildSymbolOptions('+', symbolStage, config.symbolMode);
      return {
        numbers,
        operations,
        result: evaluation.valid ? evaluation.value : 7,
        hiddenIndex: 0,
        hiddenValue: '+',
        options: options || ['+', '-'],
        hiddenType: 'operator'
      };
    }
    const stage = {
      operations: ['+', '-'],
      range: config.baseRange,
      termRange: { min: 4, max: 4 },
      maxResult: config.resultLimits.base
    };
    const numbers = [3, 5, 4, 1];
    const operations = ['+', '+', '-'];
    const evaluation = evaluateExpression(numbers, operations);
    const hiddenIndex = 2;
    const hiddenValue = numbers[hiddenIndex];
    const options = buildOptions(hiddenValue, stage, config);
    return {
      numbers,
      operations,
      result: evaluation.valid ? evaluation.value : 11,
      hiddenIndex,
      hiddenValue,
      options,
      hiddenType: 'number'
    };
  }

  function formatOperator(operator) {
    if (typeof operator !== 'string') {
      return '';
    }
    return DISPLAY_OPERATOR_MAP[operator] || operator;
  }

  function formatExpression(numbers, operations, result, hiddenIndex, reveal, hiddenType) {
    const type = hiddenType === 'operator' ? 'operator' : 'number';
    const displayNumbers = numbers.map((value, index) => {
      if (type === 'number' && index === hiddenIndex && !reveal) {
        return PLACEHOLDER_SYMBOL;
      }
      return String(value);
    });
    const formattedOps = operations.map((operator, index) => {
      if (type === 'operator' && index === hiddenIndex && !reveal) {
        return PLACEHOLDER_SYMBOL;
      }
      return formatOperator(operator);
    });
    const tokens = [];
    let index = 0;

    while (index < displayNumbers.length) {
      const nextOperator = index < operations.length ? operations[index] : null;
      if (nextOperator === '*' || nextOperator === '/') {
        let groupStart = index;
        let groupEnd = index;
        while (groupEnd < operations.length && (operations[groupEnd] === '*' || operations[groupEnd] === '/')) {
          groupEnd += 1;
        }
        const parts = [displayNumbers[groupStart]];
        for (let opIndex = groupStart; opIndex < groupEnd; opIndex += 1) {
          parts.push(formattedOps[opIndex]);
          parts.push(displayNumbers[opIndex + 1]);
        }
        let segment = parts.join(' ');
        const wrapLeft = groupStart > 0 && (operations[groupStart - 1] === '+' || operations[groupStart - 1] === '-');
        const wrapRight = groupEnd < operations.length && (operations[groupEnd] === '+' || operations[groupEnd] === '-');
        if (wrapLeft || wrapRight) {
          segment = `(${segment})`;
        }
        tokens.push(segment);
        if (groupEnd < operations.length) {
          tokens.push(formattedOps[groupEnd]);
        }
        index = groupEnd + 1;
      } else {
        tokens.push(displayNumbers[index]);
        if (index < operations.length) {
          tokens.push(formattedOps[index]);
        }
        index += 1;
      }
    }
    tokens.push('=');
    tokens.push(String(result));
    return tokens.join(' ');
  }

  onReady(() => {
    const gameElement = document.getElementById('mathGame');
    if (!gameElement) {
      return;
    }

    const elements = {
      game: gameElement,
      expression: document.getElementById('mathExpression'),
      options: document.getElementById('mathOptions'),
      feedback: document.getElementById('mathFeedback'),
      nextButton: document.getElementById('mathNextButton'),
      score: document.getElementById('mathScoreValue'),
      streak: document.getElementById('mathStreakValue'),
      timer: document.getElementById('mathTimerValue'),
      modeSwitch: document.getElementById('mathModeSwitch')
    };
    elements.modeButtons = elements.modeSwitch
      ? Array.from(elements.modeSwitch.querySelectorAll('[data-math-mode]'))
      : Array.from(document.querySelectorAll('[data-math-mode]'));

    if (!elements.expression || !elements.options || !elements.nextButton) {
      return;
    }

    const config = getGameConfig();

    const state = {
      correctAnswers: 0,
      streak: 0,
      mistakes: 0,
      solved: false,
      currentRound: null,
      mode: 'numbers',
      config,
      timer: {
        id: null,
        deadline: null,
        remaining: Math.max(0, Math.floor(config.roundTimerSeconds || 0))
      },
      disabledOptions: new Set()
    };

    function updateCounters() {
      if (elements.score) {
        elements.score.textContent = String(state.correctAnswers);
      }
      if (elements.streak) {
        elements.streak.textContent = String(state.streak);
      }
    }

    function updateTimerDisplay() {
      if (elements.timer) {
        elements.timer.textContent = String(state.timer.remaining);
      }
    }

    function setTimerRemaining(seconds) {
      const normalized = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
      state.timer.remaining = normalized;
      updateTimerDisplay();
    }

    function isSymbolModeAvailable() {
      return Boolean(state.config.symbolMode && state.config.symbolMode.enabled);
    }

    function updateModeButtons() {
      if (!Array.isArray(elements.modeButtons)) {
        return;
      }
      elements.modeButtons.forEach(button => {
        if (!(button instanceof HTMLElement)) {
          return;
        }
        const modeValue = button.dataset.mathMode || 'numbers';
        const isActive = modeValue === state.mode;
        button.classList.toggle('math-game__mode-button--active', isActive);
        button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        if (modeValue === 'symbols') {
          if (!isSymbolModeAvailable()) {
            button.disabled = true;
            button.setAttribute('aria-disabled', 'true');
          } else {
            button.disabled = false;
            button.removeAttribute('aria-disabled');
          }
        }
      });
    }

    function resetForModeChange() {
      state.correctAnswers = 0;
      state.streak = 0;
      state.mistakes = 0;
      state.solved = false;
      state.currentRound = null;
      updateCounters();
      if (elements.feedback) {
        elements.feedback.textContent = '';
      }
    }

    function switchMode(nextMode) {
      const normalized = nextMode === 'symbols' && isSymbolModeAvailable() ? 'symbols' : 'numbers';
      if (normalized === state.mode) {
        return;
      }
      stopTimer();
      state.mode = normalized;
      resetForModeChange();
      updateModeButtons();
      prepareRound();
    }

    function stopTimer() {
      if (state.timer.id != null) {
        window.clearInterval(state.timer.id);
      }
      state.timer.id = null;
      state.timer.deadline = null;
    }

    function startTimer(overrideSeconds) {
      stopTimer();
      const baseDuration = Math.max(1, Math.floor(state.config.roundTimerSeconds));
      const duration = Number.isFinite(overrideSeconds)
        ? Math.max(0, Math.floor(overrideSeconds))
        : baseDuration;
      setTimerRemaining(duration);
      if (duration <= 0) {
        state.timer.deadline = null;
        return;
      }
      const deadline = Date.now() + duration * 1000;
      state.timer.deadline = deadline;
      state.timer.id = window.setInterval(() => {
        const remaining = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
        if (remaining !== state.timer.remaining) {
          state.timer.remaining = remaining;
          updateTimerDisplay();
        }
        if (remaining <= 0) {
          stopTimer();
          handleTimeout();
        }
      }, 200);
    }

    function revealRoundAnswer(round) {
      if (!round) {
        Array.from(elements.options.querySelectorAll('button')).forEach(option => {
          option.disabled = true;
        });
        return '';
      }
      const fullEquation = formatExpression(
        round.numbers,
        round.operations,
        round.result,
        round.hiddenIndex,
        true,
        round.hiddenType
      );
      elements.expression.textContent = fullEquation;
      Array.from(elements.options.querySelectorAll('button')).forEach(option => {
        option.disabled = true;
        const optionValue = option.dataset.answer || '';
        if (optionValue === String(round.hiddenValue)) {
          option.classList.add('math-game__option--correct');
        }
      });
      return fullEquation;
    }

    function handleGameOver(reason) {
      if (state.solved) {
        return;
      }
      stopTimer();
      const round = state.currentRound;
      revealRoundAnswer(round);
      state.solved = true;
      state.correctAnswers = 0;
      state.streak = 0;
      state.mistakes = 0;
      if (elements.feedback) {
        if (reason === 'mistakes') {
          const mistakeLimit = Math.max(1, state.config.maxMistakes || DEFAULT_CONFIG.maxMistakes);
          elements.feedback.textContent = translate(
            'scripts.arcade.math.status.gameOverMistakes',
            'Vous avez atteint {limit} erreurs : game over ! Le niveau repart à zéro.',
            { limit: mistakeLimit }
          );
        } else {
          elements.feedback.textContent = translate(
            'scripts.arcade.math.status.gameOverTimeout',
            'Temps écoulé : game over ! Le niveau repart à zéro.'
          );
        }
      }
      elements.nextButton.disabled = false;
      updateCounters();
      scheduleSave();
    }

    function handleTimeout() {
      if (state.solved) {
        return;
      }
      setTimerRemaining(0);
      handleGameOver('timeout');
    }

    function renderRound(round) {
      const promptKey = round.hiddenType === 'operator'
        ? 'scripts.arcade.math.status.symbolPrompt'
        : 'scripts.arcade.math.status.prompt';
      const promptFallback = round.hiddenType === 'operator'
        ? 'Pick the missing operator to complete the equation.'
        : 'Pick the missing value to complete the equation.';
      const promptMessage = translate(promptKey, promptFallback);
      elements.expression.textContent = formatExpression(
        round.numbers,
        round.operations,
        round.result,
        round.hiddenIndex,
        false,
        round.hiddenType
      );
      elements.options.innerHTML = '';
      state.disabledOptions = new Set();
      const ariaKey = round.hiddenType === 'operator'
        ? 'index.sections.math.symbolOptionsAria'
        : 'index.sections.math.optionsAria';
      const ariaFallback = round.hiddenType === 'operator'
        ? 'Pick the missing operator to complete the equation.'
        : 'Pick the answer that completes the equation.';
      elements.options.setAttribute('aria-label', translate(ariaKey, ariaFallback));
      round.options.forEach(value => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'math-game__option';
        const answerValue = String(value);
        button.dataset.answer = answerValue;
        button.textContent = round.hiddenType === 'operator' ? formatOperator(value) : answerValue;
        elements.options.appendChild(button);
      });
      if (elements.feedback) {
        elements.feedback.textContent = promptMessage;
      }
      elements.nextButton.disabled = true;
      state.solved = false;
      updateCounters();
    }

    function prepareRound() {
      stopTimer();
      setTimerRemaining(state.config.roundTimerSeconds);
      const stage = determineStage(state.correctAnswers, state.config, state.mode);
      let round = generateRoundForStage(stage, state.config);
      if (!round && stage.operations.includes('/')) {
        const filteredOperations = stage.operations.filter(operator => operator !== '/');
        const withoutDivision = Object.assign({}, stage, {
          operations: filteredOperations,
          optionValues: Array.isArray(stage.optionValues)
            ? stage.optionValues.filter(operator => operator !== '/')
            : filteredOperations,
          optionCount: Math.max(2, Array.isArray(stage.optionValues)
            ? stage.optionValues.filter(operator => operator !== '/').length
            : filteredOperations.length),
          requireMultiplicative: false,
          maxResult: Math.max(stage.maxResult, state.config.resultLimits.advanced)
        });
        round = generateRoundForStage(withoutDivision, state.config);
      }
      if (!round && stage.operations.includes('*')) {
        const simpleStage = Object.assign({}, stage, {
          operations: ['+', '-'],
          range: state.config.baseRange,
          optionValues: ['+', '-'],
          optionCount: 2,
          requireMultiplicative: false,
          maxResult: state.config.resultLimits.base
        });
        round = generateRoundForStage(simpleStage, state.config);
      }
      if (!round) {
        round = createFallbackRound(state.config, state.mode);
      }
      state.currentRound = round;
      renderRound(round);
      startTimer();
      scheduleSave();
    }

    function serializeRound(round) {
      if (!round || typeof round !== 'object') {
        return null;
      }
      const numbers = Array.isArray(round.numbers) ? round.numbers.map(value => Number(value)) : null;
      const operations = Array.isArray(round.operations) ? round.operations.map(value => String(value)) : null;
      const options = Array.isArray(round.options) ? round.options.map(value => String(value)) : null;
      if (!numbers || numbers.length === 0 || !operations) {
        return null;
      }
      const hiddenType = round.hiddenType === 'operator' ? 'operator' : 'number';
      const hiddenIndex = Number.isFinite(round.hiddenIndex) ? Math.max(0, Math.floor(round.hiddenIndex)) : 0;
      const hiddenValue = hiddenType === 'operator' ? String(round.hiddenValue) : Number(round.hiddenValue);
      return {
        numbers,
        operations,
        result: Number(round.result) || 0,
        hiddenIndex,
        hiddenValue,
        hiddenType,
        options
      };
    }

    function sanitizeRound(data) {
      if (!data || typeof data !== 'object') {
        return null;
      }
      const numbers = Array.isArray(data.numbers)
        ? data.numbers.map(value => Number(value)).filter(value => Number.isFinite(value))
        : null;
      const operations = Array.isArray(data.operations)
        ? data.operations.map(value => String(value))
        : null;
      if (!numbers || numbers.length === 0 || !operations) {
        return null;
      }
      const hiddenType = data.hiddenType === 'operator' ? 'operator' : 'number';
      const hiddenIndex = Number.isFinite(data.hiddenIndex) ? Math.max(0, Math.floor(data.hiddenIndex)) : 0;
      const result = Number(data.result);
      if (!Number.isFinite(result)) {
        return null;
      }
      if (hiddenType === 'number' && hiddenIndex >= numbers.length) {
        return null;
      }
      if (hiddenType === 'operator' && hiddenIndex >= operations.length) {
        return null;
      }
      const options = Array.isArray(data.options)
        ? data.options.map(value => String(value)).filter(value => value != null)
        : [];
      const hiddenValue = hiddenType === 'operator'
        ? String(data.hiddenValue || '')
        : Number(data.hiddenValue);
      if (hiddenType === 'number' && !Number.isFinite(hiddenValue)) {
        return null;
      }
      return {
        numbers,
        operations,
        result,
        hiddenIndex,
        hiddenValue,
        hiddenType,
        options
      };
    }

    let saveTimeoutId = null;

    function persistState() {
      if (typeof window === 'undefined' || !window.ArcadeAutosave || typeof window.ArcadeAutosave.set !== 'function') {
        return;
      }
      const payload = {
        mode: state.mode,
        correctAnswers: Math.max(0, Number(state.correctAnswers) || 0),
        streak: Math.max(0, Number(state.streak) || 0),
        mistakes: Math.max(0, Number(state.mistakes) || 0),
        solved: state.solved === true,
        timer: {
          remaining: Math.max(0, Number(state.timer?.remaining) || 0)
        },
        round: serializeRound(state.currentRound),
        disabledOptions: Array.from(state.disabledOptions || []).map(value => String(value)),
        feedback: elements.feedback ? elements.feedback.textContent || '' : '',
        updatedAt: Date.now()
      };
      try {
        window.ArcadeAutosave.set('math', payload);
      } catch (error) {
        // Ignore autosave errors to avoid disrupting gameplay
      }
    }

    function scheduleSave() {
      if (saveTimeoutId != null) {
        window.clearTimeout(saveTimeoutId);
      }
      saveTimeoutId = window.setTimeout(() => {
        saveTimeoutId = null;
        persistState();
      }, 60);
    }

    function applySavedState(saved) {
      const round = sanitizeRound(saved && saved.round);
      if (!saved || !round) {
        return false;
      }
      const savedMode = saved.mode === 'symbols' && isSymbolModeAvailable() ? 'symbols' : 'numbers';
      state.mode = savedMode;
      state.correctAnswers = Math.max(0, Number(saved.correctAnswers) || 0);
      state.streak = Math.max(0, Number(saved.streak) || 0);
      state.mistakes = Math.max(0, Number(saved.mistakes) || 0);
      state.solved = saved.solved === true;
      state.currentRound = round;
      const remaining = saved.timer && Number(saved.timer.remaining);
      const normalizedRemaining = Number.isFinite(remaining) ? Math.max(0, Math.floor(remaining)) : state.config.roundTimerSeconds;
      setTimerRemaining(normalizedRemaining);
      updateCounters();
      updateModeButtons();
      renderRound(round);
      const disabledList = Array.isArray(saved.disabledOptions)
        ? saved.disabledOptions.map(value => String(value))
        : [];
      state.disabledOptions = new Set();
      if (disabledList.length) {
        const optionButtons = Array.from(elements.options.querySelectorAll('button'));
        disabledList.forEach(value => {
          state.disabledOptions.add(value);
          optionButtons.forEach(button => {
            if (String(button.dataset.answer) === value) {
              button.disabled = true;
              button.classList.add('math-game__option--wrong');
            }
          });
        });
      }
      if (elements.feedback) {
        if (state.solved && saved.feedback && typeof saved.feedback === 'string') {
          elements.feedback.textContent = saved.feedback;
        } else if (!state.solved) {
          // Keep the prompt rendered by renderRound
        }
      }
      if (state.solved) {
        revealRoundAnswer(round);
        elements.nextButton.disabled = false;
        stopTimer();
      } else {
        elements.nextButton.disabled = true;
        if (normalizedRemaining > 0) {
          startTimer(normalizedRemaining);
        } else {
          stopTimer();
        }
      }
      return true;
    }

    function handleCorrectAnswer(button) {
      stopTimer();
      const round = state.currentRound;
      state.correctAnswers += 1;
      state.streak += 1;
      state.solved = true;
      const fullEquation = revealRoundAnswer(round);
      button.classList.add('math-game__option--correct');
      if (elements.feedback) {
        elements.feedback.textContent = translate(
          'scripts.arcade.math.status.correct',
          'Bravo ! {equation}',
          { equation: fullEquation }
        );
      }
      elements.nextButton.disabled = false;
      updateCounters();
      maybeAwardGachaTicket();
      scheduleSave();
    }

    function maybeAwardGachaTicket() {
      const rewards = state.config && state.config.rewards ? state.config.rewards : null;
      const gacha = rewards && rewards.gachaTicket ? rewards.gachaTicket : null;
      if (!gacha) {
        return;
      }
      const amount = Math.max(0, Math.floor(Number(gacha.amount) || 0));
      if (amount <= 0) {
        return;
      }
      const chanceValue = Number(gacha.chance);
      const chance = Number.isFinite(chanceValue) ? Math.max(0, Math.min(1, chanceValue)) : 0;
      if (chance <= 0) {
        return;
      }
      if (chance < 1 && Math.random() >= chance) {
        return;
      }
      const award = typeof gainGachaTickets === 'function'
        ? gainGachaTickets
        : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
          ? window.gainGachaTickets
          : null;
      if (typeof award !== 'function') {
        return;
      }
      let gained = 0;
      try {
        gained = award(amount, { unlockTicketStar: true });
      } catch (error) {
        console.warn('Math game: unable to grant gacha tickets', error);
        return;
      }
      if (!Number.isFinite(gained) || gained <= 0) {
        return;
      }
      if (typeof showToast === 'function') {
        const countText = formatIntegerLocalized(gained);
        const message = translate(
          'scripts.arcade.math.toast.gachaTicket',
          'Ticket de tirage gagné ! (+{count})',
          { count: countText }
        );
        showToast(message);
      }
    }

    function handleWrongAnswer(button, value) {
      state.streak = 0;
      button.classList.add('math-game__option--wrong');
      button.disabled = true;
      state.mistakes += 1;
      if (value != null) {
        state.disabledOptions.add(String(value));
      }
      const mistakeLimit = Math.max(1, state.config.maxMistakes || DEFAULT_CONFIG.maxMistakes);
      if (state.mistakes >= mistakeLimit) {
        handleGameOver('mistakes');
        return;
      }
      if (elements.feedback) {
        const remaining = Math.max(0, mistakeLimit - state.mistakes);
        const displayValue = state.currentRound && state.currentRound.hiddenType === 'operator'
          ? formatOperator(value)
          : value;
        elements.feedback.textContent = translate(
          'scripts.arcade.math.status.incorrect',
          "Ce n'est pas {value}. Réessayez ! Il vous reste {remaining} erreur(s).",
          { value: displayValue, remaining }
        );
      }
      updateCounters();
      scheduleSave();
    }

    if (Array.isArray(elements.modeButtons) && elements.modeButtons.length) {
      elements.modeButtons.forEach(button => {
        if (!(button instanceof HTMLElement)) {
          return;
        }
        const modeValue = button.dataset.mathMode || 'numbers';
        button.addEventListener('click', () => {
          if (modeValue === 'symbols' && !isSymbolModeAvailable()) {
            return;
          }
          switchMode(modeValue);
        });
      });
    }

    elements.options.addEventListener('click', event => {
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (!target || target.tagName !== 'BUTTON') {
        return;
      }
      if (target.disabled || state.solved || !state.currentRound) {
        return;
      }
      const value = target.dataset.answer;
      if (typeof value !== 'string') {
        return;
      }
      if (value === String(state.currentRound.hiddenValue)) {
        handleCorrectAnswer(target);
      } else {
        handleWrongAnswer(target, value);
      }
    });

    elements.nextButton.addEventListener('click', () => {
      if (!state.solved) {
        return;
      }
      prepareRound();
    });

    let restored = false;
    if (typeof window !== 'undefined' && window.ArcadeAutosave && typeof window.ArcadeAutosave.get === 'function') {
      try {
        const savedState = window.ArcadeAutosave.get('math');
        restored = applySavedState(savedState);
      } catch (error) {
        restored = false;
      }
    }

    if (!restored) {
      updateModeButtons();
      prepareRound();
    } else {
      scheduleSave();
    }
  });
})();
