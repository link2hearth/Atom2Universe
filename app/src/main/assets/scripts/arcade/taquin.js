(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-taquin-root]');
  if (!root) {
    return;
  }

  const CONFIG_PATH = 'config/arcade/taquin.json';
  const DEFAULT_CONFIG = Object.freeze({
    imageCount: 100,
    difficulties: Object.freeze({
      small: Object.freeze({ size: 4, shuffle: Object.freeze({ min: 32, max: 64 }), gachaTickets: 1 }),
      medium: Object.freeze({ size: 5, shuffle: Object.freeze({ min: 48, max: 96 }), gachaTickets: 2 }),
      large: Object.freeze({ size: 6, shuffle: Object.freeze({ min: 64, max: 128 }), gachaTickets: 3 })
    })
  });

  const elements = {
    board: document.getElementById('taquinBoard'),
    movesValue: document.getElementById('taquinMovesValue'),
    timeValue: document.getElementById('taquinTimeValue'),
    newButton: document.getElementById('taquinNewButton'),
    restartButton: document.getElementById('taquinRestartButton'),
    modeButtons: Array.from(document.querySelectorAll('[data-taquin-size]')),
    message: document.getElementById('taquinMessage')
  };

  if (!elements.board) {
    return;
  }

  const state = {
    config: DEFAULT_CONFIG,
    difficulty: 'small',
    size: DEFAULT_CONFIG.difficulties.small.size,
    tiles: [],
    initialTiles: [],
    moves: 0,
    elapsedMs: 0,
    startTimestamp: null,
    timerId: null,
    timerRunning: false,
    resumeOnEnter: false,
    solved: false,
    rewardClaimed: false,
    imageIndex: 1,
    availableImages: null,
    imageDetectionPromise: null,
    pendingNewGame: false,
    active: false
  };

  function translate(key, fallback, params) {
    const i18n = typeof window !== 'undefined' ? window.i18n : null;
    if (i18n && typeof i18n.translate === 'function') {
      try {
        const result = i18n.translate(key, params);
        if (typeof result === 'string' && result.trim().length > 0) {
          return result;
        }
      } catch (error) {
        // Ignore translation errors and fallback to default text.
      }
    }
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof fallback !== 'string') {
      return '';
    }
    if (!params || typeof params !== 'object') {
      return fallback;
    }
    return fallback.replace(/\{(\w+)\}/g, (match, token) => {
      if (Object.prototype.hasOwnProperty.call(params, token)) {
        const value = params[token];
        return value != null ? String(value) : '';
      }
      return match;
    });
  }

  function clampInteger(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
      return fallback;
    }
    const integer = Math.trunc(number);
    if (Number.isFinite(min) && integer < min) {
      return min;
    }
    if (Number.isFinite(max) && integer > max) {
      return max;
    }
    return integer;
  }

  function normalizeDifficultyConfig(rawDifficulty, fallback) {
    const base = fallback || DEFAULT_CONFIG.difficulties.small;
    if (!rawDifficulty || typeof rawDifficulty !== 'object') {
      return { ...base };
    }
    const size = clampInteger(rawDifficulty.size, 3, 8, base.size);
    const shuffleMin = clampInteger(rawDifficulty.shuffle?.min, 1, 400, base.shuffle.min);
    const shuffleMax = clampInteger(
      rawDifficulty.shuffle?.max,
      shuffleMin,
      800,
      Math.max(base.shuffle.max, shuffleMin)
    );
    const gachaTickets = clampInteger(rawDifficulty.gachaTickets, 0, 99, base.gachaTickets);
    return {
      size,
      shuffle: { min: shuffleMin, max: shuffleMax },
      gachaTickets
    };
  }

  function normalizeConfig(rawConfig) {
    if (!rawConfig || typeof rawConfig !== 'object') {
      return DEFAULT_CONFIG;
    }
    const imageCount = clampInteger(rawConfig.imageCount, 1, 500, DEFAULT_CONFIG.imageCount);
    const baseDiffs = DEFAULT_CONFIG.difficulties;
    const difficulties = {
      small: normalizeDifficultyConfig(rawConfig.difficulties?.small, baseDiffs.small),
      medium: normalizeDifficultyConfig(rawConfig.difficulties?.medium, baseDiffs.medium),
      large: normalizeDifficultyConfig(rawConfig.difficulties?.large, baseDiffs.large)
    };
    return { imageCount, difficulties };
  }

  function formatInteger(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
      return '0';
    }
    try {
      return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(number);
    } catch (error) {
      return String(Math.trunc(number));
    }
  }

  function formatElapsedTime(ms) {
    const totalMs = Math.max(0, Number(ms) || 0);
    const totalSeconds = Math.floor(totalMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    const paddedSeconds = seconds < 10 ? `0${seconds}` : String(seconds);
    return `${minutes}:${paddedSeconds}`;
  }

  function getNow() {
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
      return performance.now();
    }
    return Date.now();
  }

  function updateTimerDisplay(ms) {
    if (!elements.timeValue) {
      return;
    }
    elements.timeValue.textContent = formatElapsedTime(ms);
  }

  function updateStats() {
    if (elements.movesValue) {
      elements.movesValue.textContent = formatInteger(state.moves);
    }
    updateTimerDisplay(state.timerRunning && state.startTimestamp != null
      ? state.elapsedMs + (getNow() - state.startTimestamp)
      : state.elapsedMs
    );
    updateControlsState();
  }

  function updateControlsState() {
    if (elements.restartButton) {
      const disabled = state.tiles.length === 0;
      elements.restartButton.disabled = disabled;
      elements.restartButton.setAttribute('aria-disabled', disabled ? 'true' : 'false');
    }
  }

  function isSolvedSequence(sequence) {
    if (!Array.isArray(sequence)) {
      return false;
    }
    for (let index = 0; index < sequence.length - 1; index += 1) {
      if (sequence[index] !== index + 1) {
        return false;
      }
    }
    return sequence[sequence.length - 1] === 0;
  }

  function getSolvedPosition(tileValue, size) {
    if (!Number.isFinite(tileValue) || tileValue <= 0) {
      return { row: size - 1, col: size - 1 };
    }
    const normalized = tileValue - 1;
    return {
      row: Math.floor(normalized / size),
      col: normalized % size
    };
  }

  function getNeighborIndices(index, size) {
    const neighbors = [];
    const row = Math.floor(index / size);
    const col = index % size;
    if (row > 0) {
      neighbors.push(index - size);
    }
    if (row < size - 1) {
      neighbors.push(index + size);
    }
    if (col > 0) {
      neighbors.push(index - 1);
    }
    if (col < size - 1) {
      neighbors.push(index + 1);
    }
    return neighbors;
  }

  function randomInt(min, max) {
    const lower = Math.ceil(Number(min) || 0);
    const upper = Math.floor(Number(max) || 0);
    if (upper <= lower) {
      return lower;
    }
    const span = upper - lower + 1;
    return lower + Math.floor(Math.random() * span);
  }

  function getShuffleCountForDifficulty(difficultyId) {
    const definition = state.config.difficulties[difficultyId] || DEFAULT_CONFIG.difficulties.small;
    const min = Number(definition.shuffle?.min) || 16;
    const max = Number(definition.shuffle?.max) || min;
    if (max <= min) {
      return Math.max(min, state.size * state.size * 8);
    }
    return randomInt(min, max);
  }

  function generatePuzzle(size, shuffleMoves) {
    const total = size * size;
    const tiles = Array.from({ length: total }, (_, index) => index);
    let emptyIndex = total - 1;
    const moves = Math.max(shuffleMoves, size * size * 8);
    for (let step = 0; step < moves; step += 1) {
      const neighbors = getNeighborIndices(emptyIndex, size);
      if (!neighbors.length) {
        break;
      }
      const target = neighbors[Math.floor(Math.random() * neighbors.length)];
      const temp = tiles[emptyIndex];
      tiles[emptyIndex] = tiles[target];
      tiles[target] = temp;
      emptyIndex = target;
    }
    if (isSolvedSequence(tiles)) {
      const neighbors = getNeighborIndices(emptyIndex, size);
      if (neighbors.length) {
        const target = neighbors[0];
        const temp = tiles[emptyIndex];
        tiles[emptyIndex] = tiles[target];
        tiles[target] = temp;
      }
    }
    return tiles;
  }

  function getImageUrl(index) {
    const safeIndex = clampInteger(index, 1, 500, 1);
    return encodeURI(`Assets/Image/Taquin/taquin (${safeIndex}).png`);
  }

  function detectAvailableImages(maxCount) {
    if (!Number.isFinite(maxCount) || maxCount <= 0) {
      return Promise.resolve([]);
    }
    if (typeof Image !== 'function') {
      return Promise.resolve(Array.from({ length: maxCount }, (_, i) => i + 1));
    }
    const indices = [];
    const checks = [];
    for (let index = 1; index <= maxCount; index += 1) {
      checks.push(
        new Promise(resolve => {
          const image = new Image();
          const finalize = () => {
            image.onload = null;
            image.onerror = null;
            resolve();
          };
          image.onload = () => {
            indices.push(index);
            finalize();
          };
          image.onerror = finalize;
          image.src = getImageUrl(index);
        })
      );
    }
    return Promise.all(checks).then(() => indices.sort((a, b) => a - b));
  }

  function prepareImagePool() {
    if (state.imageDetectionPromise) {
      return state.imageDetectionPromise;
    }
    const count = Math.max(1, clampInteger(state.config.imageCount, 1, 500, DEFAULT_CONFIG.imageCount));
    const detection = detectAvailableImages(count)
      .then(indices => {
        state.availableImages = indices;
        if (!indices.length) {
          console.warn('Taquin: no available images found in Assets/Image/Taquin.');
        }
        return indices;
      })
      .catch(error => {
        console.warn('Taquin: unable to verify available images.', error);
        state.availableImages = [];
        return state.availableImages;
      })
      .finally(() => {
        state.imageDetectionPromise = null;
      });
    state.imageDetectionPromise = detection;
    return detection;
  }

  function chooseRandomImageIndex() {
    if (Array.isArray(state.availableImages)) {
      if (state.availableImages.length === 0) {
        const fallbackCount = Math.max(
          1,
          clampInteger(state.config.imageCount, 1, 500, DEFAULT_CONFIG.imageCount)
        );
        return randomInt(1, fallbackCount);
      }
      const position = Math.floor(Math.random() * state.availableImages.length);
      return state.availableImages[position];
    }
    const count = Math.max(1, clampInteger(state.config.imageCount, 1, 500, DEFAULT_CONFIG.imageCount));
    return randomInt(1, count);
  }

  function updateBoardBackground() {
    if (!elements.board) {
      return;
    }
    const index = Number(state.imageIndex) || 0;
    if (!Number.isFinite(index) || index <= 0) {
      elements.board.style.removeProperty('--taquin-image');
      return;
    }
    const url = getImageUrl(index);
    elements.board.style.setProperty('--taquin-image', `url("${url}")`);
  }

  function renderBoard() {
    if (!elements.board) {
      return;
    }
    const { tiles, size, solved } = state;
    const imageUrl = Number(state.imageIndex) > 0 ? getImageUrl(state.imageIndex) : null;
    elements.board.innerHTML = '';
    elements.board.style.setProperty('--taquin-size', String(size));
    elements.board.dataset.taquinSolved = solved ? 'true' : 'false';
    elements.board.setAttribute('aria-rowcount', String(size));
    elements.board.setAttribute('aria-colcount', String(size));

    for (let index = 0; index < tiles.length; index += 1) {
      const value = tiles[index];
      if (value === 0) {
        const cell = document.createElement('div');
        cell.className = 'taquin__tile taquin__tile--empty';
        cell.dataset.index = String(index);
        cell.setAttribute('role', 'gridcell');
        cell.setAttribute('aria-hidden', 'true');
        elements.board.appendChild(cell);
        continue;
      }
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'taquin__tile';
      button.dataset.index = String(index);
      button.dataset.value = String(value);
      button.setAttribute('role', 'gridcell');
      button.setAttribute(
        'aria-label',
        translate('scripts.arcade.taquin.tileLabel', 'Tuile {value}', { value })
      );
      const label = document.createElement('span');
      label.className = 'taquin__tile-label';
      label.textContent = String(value);
      button.appendChild(label);

      if (imageUrl) {
        button.classList.add('taquin__tile--with-image');
        const backgroundSize = `${size * 100}% ${size * 100}%`;
        button.style.backgroundImage = `url("${imageUrl}")`;
        button.style.backgroundSize = backgroundSize;
        const position = getSolvedPosition(value, size);
        const denominator = Math.max(1, size - 1);
        const x = (position.col / denominator) * 100;
        const y = (position.row / denominator) * 100;
        button.style.backgroundPosition = `${x}% ${y}%`;
      } else {
        button.classList.remove('taquin__tile--with-image');
        button.style.removeProperty('backgroundImage');
        button.style.removeProperty('backgroundSize');
        button.style.removeProperty('backgroundPosition');
      }
      button.addEventListener('click', handleTilePointer);
      elements.board.appendChild(button);
    }
  }

  function setMessage(text, hidden = false) {
    if (!elements.message) {
      return;
    }
    elements.message.hidden = hidden;
    elements.message.setAttribute('aria-hidden', hidden ? 'true' : 'false');
    if (!hidden) {
      elements.message.textContent = text;
    } else {
      elements.message.textContent = '';
    }
  }

  function hideMessage() {
    setMessage('', true);
  }

  function showSolvedMessage() {
    const movesText = formatInteger(state.moves);
    const timeText = formatElapsedTime(state.elapsedMs);
    const message = translate(
      'scripts.arcade.taquin.message.solved',
      'Puzzle résolu en {moves} coups ({time}).',
      { moves: movesText, time: timeText }
    );
    setMessage(message, false);
  }

  function awardVictoryTickets() {
    if (state.rewardClaimed) {
      return;
    }
    const definition = state.config.difficulties[state.difficulty];
    const tickets = Number.isFinite(Number(definition?.gachaTickets))
      ? Math.max(0, Math.floor(Number(definition.gachaTickets)))
      : 0;
    state.rewardClaimed = true;
    if (tickets <= 0) {
      return;
    }
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
      gained = awardGacha(tickets, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Taquin: unable to grant gacha tickets', error);
      gained = 0;
    }
    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = gained > 1 ? 's' : '';
      const formatted = formatInteger(gained);
      const toast = translate(
        'scripts.arcade.taquin.rewards.gacha',
        'Taquin réussi ! +{count} ticket{suffix} gacha.',
        { count: formatted, suffix }
      );
      showToast(toast);
    }
  }

  function startTimer() {
    if (state.timerRunning) {
      return;
    }
    state.timerRunning = true;
    state.resumeOnEnter = false;
    state.startTimestamp = getNow();
    if (state.timerId != null) {
      clearInterval(state.timerId);
    }
    state.timerId = window.setInterval(() => {
      if (!state.timerRunning || state.startTimestamp == null) {
        return;
      }
      const current = getNow();
      const total = state.elapsedMs + (current - state.startTimestamp);
      updateTimerDisplay(total);
    }, 250);
  }

  function pauseTimer(options = {}) {
    const preserveResume = options.preserveResume === true;
    if (!state.timerRunning || state.startTimestamp == null) {
      state.resumeOnEnter = preserveResume && state.moves > 0 && !state.solved;
      if (state.timerId != null) {
        clearInterval(state.timerId);
        state.timerId = null;
      }
      return;
    }
    const now = getNow();
    state.elapsedMs += now - state.startTimestamp;
    state.startTimestamp = null;
    state.timerRunning = false;
    if (state.timerId != null) {
      clearInterval(state.timerId);
      state.timerId = null;
    }
    updateTimerDisplay(state.elapsedMs);
    state.resumeOnEnter = preserveResume && state.moves > 0 && !state.solved;
  }

  function resumeTimerIfNeeded() {
    if (!state.resumeOnEnter || state.solved || state.timerRunning) {
      return;
    }
    state.resumeOnEnter = false;
    state.startTimestamp = getNow();
    state.timerRunning = true;
    if (state.timerId != null) {
      clearInterval(state.timerId);
    }
    state.timerId = window.setInterval(() => {
      if (!state.timerRunning || state.startTimestamp == null) {
        return;
      }
      const current = getNow();
      const total = state.elapsedMs + (current - state.startTimestamp);
      updateTimerDisplay(total);
    }, 250);
  }

  function resetTimer() {
    if (state.timerId != null) {
      clearInterval(state.timerId);
      state.timerId = null;
    }
    state.timerRunning = false;
    state.resumeOnEnter = false;
    state.startTimestamp = null;
    state.elapsedMs = 0;
    updateTimerDisplay(0);
  }

  function handleVictory() {
    state.solved = true;
    pauseTimer({ preserveResume: false });
    showSolvedMessage();
    renderBoard();
    awardVictoryTickets();
    updateStats();
  }

  function tryMoveTile(index) {
    if (!Array.isArray(state.tiles) || state.solved) {
      return false;
    }
    const size = state.size;
    const emptyIndex = state.tiles.indexOf(0);
    if (emptyIndex === -1 || index === emptyIndex) {
      return false;
    }
    const neighbors = getNeighborIndices(emptyIndex, size);
    if (!neighbors.includes(index)) {
      return false;
    }
    const temp = state.tiles[index];
    state.tiles[index] = state.tiles[emptyIndex];
    state.tiles[emptyIndex] = temp;
    state.moves += 1;
    if (!state.timerRunning && !state.solved) {
      startTimer();
    }
    renderBoard();
    if (isSolvedSequence(state.tiles)) {
      handleVictory();
    } else {
      updateStats();
    }
    return true;
  }

  function handleTilePointer(event) {
    if (!event || typeof event.currentTarget?.dataset?.index === 'undefined') {
      return;
    }
    const index = Number(event.currentTarget.dataset.index);
    if (!Number.isInteger(index)) {
      return;
    }
    if (tryMoveTile(index) && typeof elements.board?.focus === 'function') {
      elements.board.focus({ preventScroll: true });
    }
  }

  function moveTileInDirection(direction) {
    if (!Array.isArray(state.tiles) || state.solved) {
      return false;
    }
    const size = state.size;
    const emptyIndex = state.tiles.indexOf(0);
    if (emptyIndex === -1) {
      return false;
    }
    let targetIndex = emptyIndex;
    switch (direction) {
      case 'up':
        if (emptyIndex + size < state.tiles.length) {
          targetIndex = emptyIndex + size;
        } else {
          return false;
        }
        break;
      case 'down':
        if (emptyIndex - size >= 0) {
          targetIndex = emptyIndex - size;
        } else {
          return false;
        }
        break;
      case 'left':
        if (emptyIndex % size < size - 1) {
          targetIndex = emptyIndex + 1;
        } else {
          return false;
        }
        break;
      case 'right':
        if (emptyIndex % size > 0) {
          targetIndex = emptyIndex - 1;
        } else {
          return false;
        }
        break;
      default:
        return false;
    }
    return tryMoveTile(targetIndex);
  }

  function handleBoardKey(event) {
    if (!event) {
      return;
    }
    const key = event.key;
    const directionMap = {
      ArrowUp: 'up',
      ArrowDown: 'down',
      ArrowLeft: 'left',
      ArrowRight: 'right'
    };
    const direction = directionMap[key];
    if (!direction) {
      return;
    }
    if (!moveTileInDirection(direction)) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    if (typeof elements.board?.focus === 'function') {
      elements.board.focus({ preventScroll: true });
    }
  }

  function isInteractiveElement(element) {
    if (!element) {
      return false;
    }
    const tagName = element.tagName;
    if (!tagName) {
      return Boolean(element.isContentEditable);
    }
    const upperTag = tagName.toUpperCase();
    return (
      element.isContentEditable === true ||
      upperTag === 'INPUT' ||
      upperTag === 'TEXTAREA' ||
      upperTag === 'SELECT' ||
      upperTag === 'BUTTON'
    );
  }

  function handleGlobalKey(event) {
    if (!event || !state.active) {
      return;
    }
    if (event.defaultPrevented) {
      return;
    }
    if (isInteractiveElement(event.target)) {
      return;
    }
    handleBoardKey(event);
  }

  const swipeState = {
    pointerId: null,
    active: false,
    startX: 0,
    startY: 0,
    handled: false
  };

  function resetSwipeTracking(handled = false) {
    swipeState.pointerId = null;
    swipeState.active = false;
    swipeState.startX = 0;
    swipeState.startY = 0;
    swipeState.handled = handled;
  }

  function handleBoardPointerDown(event) {
    if (!event || (event.pointerType === 'mouse' && event.button !== 0)) {
      return;
    }
    swipeState.pointerId = event.pointerId;
    swipeState.active = true;
    swipeState.startX = event.clientX;
    swipeState.startY = event.clientY;
    swipeState.handled = false;
    if (event.currentTarget && typeof event.currentTarget.setPointerCapture === 'function') {
      try {
        event.currentTarget.setPointerCapture(event.pointerId);
      } catch (error) {
        // Ignore capture errors (e.g., unsupported browsers).
      }
    }
  }

  function resolveSwipeGesture(event) {
    if (!event || !swipeState.active || event.pointerId !== swipeState.pointerId) {
      return;
    }
    if (event.currentTarget && typeof event.currentTarget.releasePointerCapture === 'function') {
      try {
        event.currentTarget.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore release errors.
      }
    }
    const deltaX = event.clientX - swipeState.startX;
    const deltaY = event.clientY - swipeState.startY;
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);
    const baseThreshold = 24;
    const dimension = elements.board
      ? Math.min(elements.board.clientWidth || 0, elements.board.clientHeight || 0)
      : 0;
    const adaptiveThreshold = dimension > 0 ? Math.max(baseThreshold, dimension * 0.08) : baseThreshold;
    if (absX < adaptiveThreshold && absY < adaptiveThreshold) {
      resetSwipeTracking(false);
      return;
    }
    const direction = absX > absY ? (deltaX > 0 ? 'right' : 'left') : deltaY > 0 ? 'down' : 'up';
    const moved = moveTileInDirection(direction);
    resetSwipeTracking(moved);
    if (moved && typeof elements.board?.focus === 'function') {
      elements.board.focus({ preventScroll: true });
    }
    if (moved && typeof event.preventDefault === 'function') {
      event.preventDefault();
    }
  }

  function handleBoardPointerUp(event) {
    resolveSwipeGesture(event);
  }

  function handleBoardPointerCancel(event) {
    if (!event || event.pointerId !== swipeState.pointerId) {
      return;
    }
    if (event.currentTarget && typeof event.currentTarget.releasePointerCapture === 'function') {
      try {
        event.currentTarget.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore release errors.
      }
    }
    resetSwipeTracking(false);
  }

  function handleBoardClickCapture(event) {
    if (swipeState.handled) {
      swipeState.handled = false;
      event.preventDefault();
      event.stopPropagation();
    }
  }

  function updateDifficultyButtons() {
    if (!Array.isArray(elements.modeButtons)) {
      return;
    }
    elements.modeButtons.forEach(button => {
      if (!(button instanceof HTMLElement)) {
        return;
      }
      const id = button.dataset.taquinSize;
      const isActive = id === state.difficulty;
      button.classList.toggle('taquin__toggle--active', isActive);
      button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    });
  }

  function applyDifficulty(difficultyId, options = {}) {
    const normalizedId = ['small', 'medium', 'large'].includes(difficultyId)
      ? difficultyId
      : 'small';
    state.difficulty = normalizedId;
    const definition = state.config.difficulties[normalizedId] || DEFAULT_CONFIG.difficulties.small;
    state.size = definition.size;
    updateDifficultyButtons();
    if (options.skipNewGame) {
      return;
    }
    startNewGame();
  }

  function startNewGame(options = {}) {
    const skipImagePreparation = Boolean(options.skipImagePreparation);
    if (!skipImagePreparation && !Array.isArray(state.availableImages)) {
      if (!state.pendingNewGame) {
        state.pendingNewGame = true;
        prepareImagePool()
          .catch(() => {
            // Errors are already logged inside prepareImagePool.
          })
          .finally(() => {
            state.pendingNewGame = false;
            startNewGame({ skipImagePreparation: true });
          });
      }
      return;
    }
    const shuffleCount = getShuffleCountForDifficulty(state.difficulty);
    state.tiles = generatePuzzle(state.size, shuffleCount);
    state.initialTiles = state.tiles.slice();
    state.moves = 0;
    state.solved = false;
    state.rewardClaimed = false;
    state.resumeOnEnter = false;
    resetTimer();
    state.imageIndex = chooseRandomImageIndex();
    updateBoardBackground();
    hideMessage();
    renderBoard();
    updateStats();
    if (typeof elements.board?.focus === 'function') {
      elements.board.focus({ preventScroll: true });
    }
  }

  function restartPuzzle() {
    if (!state.initialTiles.length) {
      startNewGame();
      return;
    }
    state.tiles = state.initialTiles.slice();
    state.moves = 0;
    state.solved = false;
    state.rewardClaimed = false;
    state.resumeOnEnter = false;
    resetTimer();
    hideMessage();
    renderBoard();
    updateStats();
    if (typeof elements.board?.focus === 'function') {
      elements.board.focus({ preventScroll: true });
    }
  }

  function bindEvents() {
    if (elements.board) {
      elements.board.addEventListener('keydown', handleBoardKey);
      elements.board.addEventListener('pointerdown', handleBoardPointerDown);
      elements.board.addEventListener('pointerup', handleBoardPointerUp);
      elements.board.addEventListener('pointercancel', handleBoardPointerCancel);
      elements.board.addEventListener('click', handleBoardClickCapture, true);
    }
    if (Array.isArray(elements.modeButtons)) {
      elements.modeButtons.forEach(button => {
        if (!(button instanceof HTMLElement)) {
          return;
        }
        button.addEventListener('click', () => {
          applyDifficulty(button.dataset.taquinSize || 'small');
        });
      });
    }
    if (elements.newButton) {
      elements.newButton.addEventListener('click', () => {
        startNewGame();
      });
    }
    if (elements.restartButton) {
      elements.restartButton.addEventListener('click', () => {
        restartPuzzle();
      });
    }
    if (typeof document !== 'undefined') {
      try {
        document.addEventListener('keydown', handleGlobalKey, { capture: true });
      } catch (error) {
        document.addEventListener('keydown', handleGlobalKey, true);
      }
    }
  }

  function loadConfig() {
    if (typeof fetch !== 'function') {
      applyDifficulty(state.difficulty, { skipNewGame: true });
      startNewGame();
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response && response.ok ? response.json() : null))
      .then(data => {
        state.config = normalizeConfig(data);
      })
      .catch(error => {
        console.warn('Taquin: unable to load configuration, using defaults.', error);
        state.config = DEFAULT_CONFIG;
      })
      .finally(() => {
        const definition = state.config.difficulties[state.difficulty];
        state.size = definition?.size || DEFAULT_CONFIG.difficulties.small.size;
        updateDifficultyButtons();
        startNewGame();
      });
  }

  function onEnter() {
    state.active = true;
    resumeTimerIfNeeded();
  }

  function onLeave() {
    state.active = false;
    pauseTimer({ preserveResume: true });
  }

  bindEvents();
  loadConfig();

  const api = {
    newGame: startNewGame,
    restart: restartPuzzle,
    setDifficulty: applyDifficulty,
    onEnter,
    onLeave
  };

  window.taquinArcade = api;
})();
