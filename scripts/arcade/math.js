(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;

  const DEFAULT_CONFIG = Object.freeze({
    optionsCount: 6,
    termCountRange: Object.freeze({ min: 4, max: 5 }),
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
    roundTimerSeconds: 30
  });

  const PLACEHOLDER_SYMBOL = '?';
  const MAX_GENERATION_ATTEMPTS = 120;
  const DISPLAY_OPERATOR_MAP = Object.freeze({
    '*': 'x'
  });

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

  function getGameConfig() {
    const arcadeConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade ? GLOBAL_CONFIG.arcade : null;
    const mathConfig = arcadeConfig && typeof arcadeConfig.math === 'object' ? arcadeConfig.math : null;
    if (!mathConfig) {
      return DEFAULT_CONFIG;
    }
    const termCountRange = normalizeRange(mathConfig.termCountRange, DEFAULT_CONFIG.termCountRange, 3);
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
    return {
      optionsCount,
      termCountRange,
      baseRange,
      advancedRange,
      expertRange,
      thresholds,
      resultLimits,
      roundTimerSeconds
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

  function determineStage(correctCount, config) {
    const operations = ['+', '-'];
    let range = config.baseRange;
    let maxResult = config.resultLimits.base;

    if (correctCount >= config.thresholds.multiply) {
      operations.push('*');
      range = config.advancedRange;
      maxResult = config.resultLimits.advanced;
    }
    if (correctCount >= config.thresholds.divide) {
      if (!operations.includes('*')) {
        operations.push('*');
      }
      operations.push('/');
      range = config.advancedRange;
      maxResult = config.resultLimits.advanced;
    }
    if (correctCount >= config.thresholds.expert) {
      range = config.expertRange;
      maxResult = config.resultLimits.expert;
    }

    const minTerms = Math.max(4, config.termCountRange.min);
    const maxTerms = Math.max(minTerms, config.termCountRange.max);

    return {
      operations,
      range,
      termRange: { min: minTerms, max: maxTerms },
      maxResult
    };
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

  function generateRoundForStage(stage, config) {
    if (!stage.operations.length) {
      stage = Object.assign({}, stage, { operations: ['+'] });
    }
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
      const evaluation = evaluateExpression(numbers, operations);
      if (!evaluation.valid) {
        continue;
      }
      if (!Number.isFinite(evaluation.value) || Math.abs(evaluation.value) > stage.maxResult) {
        continue;
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
        options
      };
    }
    return null;
  }

  function createStaticFallback(config) {
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
      options
    };
  }

  function formatOperator(operator) {
    if (typeof operator !== 'string') {
      return '';
    }
    return DISPLAY_OPERATOR_MAP[operator] || operator;
  }

  function formatExpression(numbers, operations, result, hiddenIndex, reveal) {
    const sequence = [];
    for (let i = 0; i < numbers.length; i += 1) {
      const display = i === hiddenIndex && !reveal ? PLACEHOLDER_SYMBOL : numbers[i];
      sequence.push(String(display));
      if (i < operations.length) {
        sequence.push(formatOperator(operations[i]));
      }
    }
    sequence.push('=');
    sequence.push(String(result));
    return sequence.join(' ');
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
      timer: document.getElementById('mathTimerValue')
    };

    if (!elements.expression || !elements.options || !elements.nextButton) {
      return;
    }

    const config = getGameConfig();

    const state = {
      correctAnswers: 0,
      streak: 0,
      solved: false,
      currentRound: null,
      config,
      timer: {
        id: null,
        deadline: null,
        remaining: Math.max(0, Math.floor(config.roundTimerSeconds || 0))
      }
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

    function stopTimer() {
      if (state.timer.id != null) {
        window.clearInterval(state.timer.id);
      }
      state.timer.id = null;
      state.timer.deadline = null;
    }

    function startTimer() {
      stopTimer();
      const duration = Math.max(1, Math.floor(state.config.roundTimerSeconds));
      setTimerRemaining(duration);
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

    function handleTimeout() {
      if (state.solved) {
        return;
      }
      setTimerRemaining(0);
      const round = state.currentRound;
      state.solved = true;
      state.streak = 0;
      if (round) {
        const fullEquation = formatExpression(
          round.numbers,
          round.operations,
          round.result,
          round.hiddenIndex,
          true
        );
        elements.expression.textContent = fullEquation;
      }
      Array.from(elements.options.querySelectorAll('button')).forEach(option => {
        option.disabled = true;
        if (round) {
          const optionValue = Number.parseInt(option.dataset.value, 10);
          if (Number.isFinite(optionValue) && optionValue === round.hiddenValue) {
            option.classList.add('math-game__option--correct');
          }
        }
      });
      if (elements.feedback) {
        elements.feedback.textContent = translate(
          'scripts.arcade.math.status.timeout',
          'Temps écoulé ! La série est réinitialisée.'
        );
      }
      elements.nextButton.disabled = false;
      updateCounters();
    }

    function renderRound(round) {
      const promptMessage = translate(
        'scripts.arcade.math.status.prompt',
        'Choisissez la valeur manquante pour compléter le calcul.'
      );
      elements.expression.textContent = formatExpression(
        round.numbers,
        round.operations,
        round.result,
        round.hiddenIndex,
        false
      );
      elements.options.innerHTML = '';
      round.options.forEach(value => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'math-game__option';
        button.dataset.value = String(value);
        button.textContent = String(value);
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
      const stage = determineStage(state.correctAnswers, state.config);
      let round = generateRoundForStage(stage, state.config);
      if (!round && stage.operations.includes('/')) {
        const withoutDivision = Object.assign({}, stage, {
          operations: stage.operations.filter(operator => operator !== '/'),
          maxResult: Math.max(stage.maxResult, state.config.resultLimits.advanced)
        });
        round = generateRoundForStage(withoutDivision, state.config);
      }
      if (!round && stage.operations.includes('*')) {
        const simpleStage = Object.assign({}, stage, {
          operations: ['+', '-'],
          range: state.config.baseRange,
          maxResult: state.config.resultLimits.base
        });
        round = generateRoundForStage(simpleStage, state.config);
      }
      if (!round) {
        round = createStaticFallback(state.config);
      }
      state.currentRound = round;
      renderRound(round);
      startTimer();
    }

    function handleCorrectAnswer(button) {
      stopTimer();
      const round = state.currentRound;
      state.correctAnswers += 1;
      state.streak += 1;
      state.solved = true;
      const fullEquation = formatExpression(
        round.numbers,
        round.operations,
        round.result,
        round.hiddenIndex,
        true
      );
      button.classList.add('math-game__option--correct');
      Array.from(elements.options.querySelectorAll('button')).forEach(option => {
        option.disabled = true;
      });
      elements.expression.textContent = fullEquation;
      if (elements.feedback) {
        elements.feedback.textContent = translate(
          'scripts.arcade.math.status.correct',
          'Bravo ! {equation}',
          { equation: fullEquation }
        );
      }
      elements.nextButton.disabled = false;
      updateCounters();
    }

    function handleWrongAnswer(button, value) {
      state.streak = 0;
      button.classList.add('math-game__option--wrong');
      button.disabled = true;
      if (elements.feedback) {
        elements.feedback.textContent = translate(
          'scripts.arcade.math.status.incorrect',
          "Ce n'est pas {value}. Réessayez !",
          { value }
        );
      }
      updateCounters();
    }

    elements.options.addEventListener('click', event => {
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (!target || target.tagName !== 'BUTTON') {
        return;
      }
      if (target.disabled || state.solved || !state.currentRound) {
        return;
      }
      const value = Number.parseInt(target.dataset.value, 10);
      if (!Number.isFinite(value)) {
        return;
      }
      if (value === state.currentRound.hiddenValue) {
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

    prepareRound();
  });
})();
