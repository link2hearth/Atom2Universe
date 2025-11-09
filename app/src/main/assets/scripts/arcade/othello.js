(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const BOARD_SIZE = 8;
  const DIRECTIONS = [
    [-1, -1],
    [-1, 0],
    [-1, 1],
    [0, -1],
    [0, 1],
    [1, -1],
    [1, 0],
    [1, 1]
  ];

  const PLAYERS = Object.freeze({
    BLACK: 1,
    WHITE: -1
  });

  const GAME_MODES = Object.freeze({
    SOLO: 'solo',
    DUO: 'duo'
  });

  const AI_PLAYER = PLAYERS.BLACK;
  const AI_MOVE_DELAY = 500;
  const SOLO_VICTORY_REWARD_TICKETS = 10;

  const state = {
    board: createEmptyBoard(),
    currentPlayer: PLAYERS.WHITE,
    validMoves: new Map(),
    passes: 0,
    elements: null,
    cells: [],
    languageHandlerAttached: false,
    languageHandler: null,
    lastStatus: null,
    aiTimeout: null,
    mode: GAME_MODES.SOLO,
    gameOver: false,
    rewardGranted: false
  };

  const AUTOSAVE_GAME_ID = 'othello';
  let autosaveTimerId = null;
  let boardResizeObserver = null;
  let pendingBoardSizingFrame = null;
  let boardViewportListenersAttached = false;

  onReady(() => {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    buildBoard();
    initBoardViewportListeners();
    initBoardResizeObserver();
    scheduleBoardSizingUpdate();
    attachEvents();
    attachLanguageListener();
    if (!restoreAutosave()) {
      updateModeToggle();
      resetGame();
    }
  });

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function getElements() {
    const board = document.getElementById('othelloBoard');
    const resetButton = document.getElementById('othelloResetButton');
    const status = document.getElementById('othelloStatus');
    const score = document.getElementById('othelloScore');
    const modeToggle = document.getElementById('othelloModeToggle');
    const result = document.getElementById('othelloResult');
    if (!board || !resetButton || !status || !score) {
      return null;
    }
    return { board, resetButton, status, score, modeToggle, result: result || null };
  }

  function attachEvents() {
    state.elements.board.addEventListener('click', handleBoardClick);
    state.elements.resetButton.addEventListener('click', () => resetGame());
    if (state.elements.modeToggle) {
      state.elements.modeToggle.addEventListener('click', toggleMode);
    }
  }

  function attachLanguageListener() {
    if (state.languageHandlerAttached) {
      return;
    }
    const handler = () => {
      renderBoard();
      updateScore();
      refreshStatus();
      updateModeToggle();
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandlerAttached = true;
    state.languageHandler = handler;
  }

  function toggleMode() {
    state.mode = state.mode === GAME_MODES.SOLO ? GAME_MODES.DUO : GAME_MODES.SOLO;
    updateModeToggle();
    resetGame();
  }

  function updateModeToggle() {
    if (!state.elements || !state.elements.modeToggle) {
      return;
    }
    const isDuo = state.mode === GAME_MODES.DUO;
    state.elements.modeToggle.setAttribute('aria-pressed', isDuo ? 'true' : 'false');
    state.elements.modeToggle.dataset.mode = state.mode;
    const key = isDuo
      ? 'scripts.arcade.othello.mode.duo'
      : 'scripts.arcade.othello.mode.solo';
    const fallback = isDuo ? 'Mode : 2 joueurs' : 'Mode : Solo vs IA';
    state.elements.modeToggle.textContent = translate(key, fallback, null);
  }

  function buildBoard() {
    if (!state.elements || !state.elements.board) {
      return;
    }
    const cells = new Array(BOARD_SIZE);
    const { board } = state.elements;
    board.innerHTML = '';
    board.setAttribute('role', 'grid');
    board.setAttribute('aria-rowcount', String(BOARD_SIZE));
    board.setAttribute('aria-colcount', String(BOARD_SIZE));
    board.style.gridTemplateColumns = `repeat(${BOARD_SIZE}, var(--othello-cell-size))`;
    board.style.gridAutoRows = 'var(--othello-cell-size)';
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      cells[row] = new Array(BOARD_SIZE);
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'othello__cell';
        button.dataset.row = String(row);
        button.dataset.col = String(col);
        button.setAttribute('role', 'gridcell');
        button.setAttribute('aria-rowindex', String(row + 1));
        button.setAttribute('aria-colindex', String(col + 1));
        board.appendChild(button);
        cells[row][col] = button;
      }
    }
    state.cells = cells;
    scheduleBoardSizingUpdate();
  }

  function handleBoardClick(event) {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const cell = target.closest('button.othello__cell');
    if (!cell) {
      return;
    }
    if (!isHumanControlled(state.currentPlayer)) {
      return;
    }
    const row = Number.parseInt(cell.dataset.row || '', 10);
    const col = Number.parseInt(cell.dataset.col || '', 10);
    if (Number.isNaN(row) || Number.isNaN(col)) {
      return;
    }
    const key = getMoveKey(row, col);
    const flips = state.validMoves.get(key);
    if (!flips || state.board[row][col] !== 0) {
      setStatus(
        'scripts.arcade.othello.status.invalid',
        'Coup invalide. Choisissez une case surlignée.',
        null
      );
      return;
    }
    placeDisc(row, col, flips);
  }

  function placeDisc(row, col, flips) {
    state.board[row][col] = state.currentPlayer;
    flips.forEach(([r, c]) => {
      state.board[r][c] = state.currentPlayer;
    });
    state.passes = 0;
    switchPlayer();
  }

  function switchPlayer() {
    state.currentPlayer = -state.currentPlayer;
    prepareTurn();
  }

  function resetGame() {
    clearAIMoveTimer();
    state.board = createEmptyBoard();
    const mid = BOARD_SIZE / 2;
    state.board[mid - 1][mid - 1] = PLAYERS.WHITE;
    state.board[mid][mid] = PLAYERS.WHITE;
    state.board[mid - 1][mid] = PLAYERS.BLACK;
    state.board[mid][mid - 1] = PLAYERS.BLACK;
    state.currentPlayer = PLAYERS.WHITE;
    state.passes = 0;
    state.gameOver = false;
    state.rewardGranted = false;
    updateResultIndicator(null, null);
    prepareTurn();
  }

  function prepareTurn() {
    clearAIMoveTimer();
    state.validMoves = computeValidMoves(state.currentPlayer);
    renderBoard();
    if (state.validMoves.size > 0) {
      beginTurn();
      scheduleAutosave();
      return;
    }

    state.passes += 1;
    const playerName = getPlayerName(state.currentPlayer);
    if (state.passes >= 2) {
      endGame();
      return;
    }
    setStatus(
      'scripts.arcade.othello.status.pass',
      'Aucun coup disponible pour les {player}. Tour passé.',
      { player: playerName }
    );
    state.currentPlayer = -state.currentPlayer;
    state.validMoves = computeValidMoves(state.currentPlayer);
    renderBoard();
    if (state.validMoves.size === 0) {
      endGame();
      return;
    }
    beginTurn();
    scheduleAutosave();
  }

  function endGame() {
    clearAIMoveTimer();
    const score = getScore();
    const resultKey = score.black > score.white
      ? 'black'
      : score.white > score.black
        ? 'white'
        : 'draw';
    state.gameOver = true;
    const resultText = translate(
      `scripts.arcade.othello.status.result.${resultKey}`,
      resultKey === 'black'
        ? 'Victoire des noirs !'
        : resultKey === 'white'
          ? 'Victoire des blancs !'
          : 'Match nul.',
      null
    );
    setStatus(
      'scripts.arcade.othello.status.gameOver',
      'Partie terminée. Noir {black} – Blanc {white}. {result}',
      {
        black: String(score.black),
        white: String(score.white),
        result: resultText
      }
    );
    updateResultIndicator(resultKey, score);
    state.validMoves = new Map();
    renderBoard();
    maybeGrantVictoryReward(resultKey);
    scheduleAutosave();
  }

  function maybeGrantVictoryReward(resultKey) {
    if (
      state.mode !== GAME_MODES.SOLO ||
      resultKey !== 'white' ||
      state.rewardGranted
    ) {
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
    let granted = 0;
    try {
      granted = award(SOLO_VICTORY_REWARD_TICKETS, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Othello: unable to grant gacha tickets', error);
      granted = 0;
    }
    if (!Number.isFinite(granted) || granted <= 0) {
      return;
    }
    state.rewardGranted = true;
    if (typeof showToast === 'function') {
      const suffix = granted > 1 ? 's' : '';
      const message = translate(
        'scripts.arcade.othello.rewards.gachaWin',
        'Victory! {count} gacha ticket{suffix} earned.',
        {
          count: formatRewardTicketCount(granted),
          suffix
        }
      );
      showToast(message);
    }
    if (typeof updateArcadeTicketDisplay === 'function') {
      updateArcadeTicketDisplay();
    }
  }

  function formatRewardTicketCount(value) {
    if (typeof formatIntegerLocalized === 'function') {
      return formatIntegerLocalized(value);
    }
    if (typeof formatInteger === 'function') {
      return formatInteger(value);
    }
    const numeric = Number(value);
    if (Number.isFinite(numeric)) {
      return String(Math.floor(numeric));
    }
    return '0';
  }

  function computeValidMoves(player) {
    const moves = new Map();
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        if (state.board[row][col] !== 0) {
          continue;
        }
        const captured = collectFlips(row, col, player);
        if (captured.length > 0) {
          moves.set(getMoveKey(row, col), captured);
        }
      }
    }
    return moves;
  }

  function collectFlips(row, col, player) {
    const captured = [];
    for (let i = 0; i < DIRECTIONS.length; i += 1) {
      const [dr, dc] = DIRECTIONS[i];
      const line = collectLine(row, col, dr, dc, player);
      if (line.length > 0) {
        captured.push(...line);
      }
    }
    return captured;
  }

  function collectLine(row, col, dr, dc, player) {
    const line = [];
    let r = row + dr;
    let c = col + dc;
    while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE) {
      const value = state.board[r][c];
      if (value === 0) {
        return [];
      }
      if (value === player) {
        return line.length > 0 ? line : [];
      }
      line.push([r, c]);
      r += dr;
      c += dc;
    }
    return [];
  }

  function renderBoard() {
    const isHumanTurn = isHumanControlled(state.currentPlayer) && state.validMoves.size > 0;
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const cell = state.cells[row][col];
        const value = state.board[row][col];
        const key = getMoveKey(row, col);
        const isValidMove = isHumanTurn && state.validMoves.has(key);
        cell.classList.toggle('othello__cell--black', value === PLAYERS.BLACK);
        cell.classList.toggle('othello__cell--white', value === PLAYERS.WHITE);
        cell.classList.toggle('othello__cell--valid', isValidMove);
        cell.disabled = value !== 0 || !isHumanTurn;
        const contentKey = value === PLAYERS.BLACK
          ? 'black'
          : value === PLAYERS.WHITE
            ? 'white'
            : 'empty';
        const contentLabel = getCellContentLabel(contentKey);
        const baseLabel = translate(
          'scripts.arcade.othello.cell',
          'Case {row}, colonne {column} : {content}',
          {
            row: String(row + 1),
            column: String(col + 1),
            content: contentLabel
          }
        );
        const formattedLabel = formatString(baseLabel, {
          row: String(row + 1),
          column: String(col + 1),
          content: contentLabel
        });
        if (isValidMove) {
          const moveLabel = translate(
            'scripts.arcade.othello.cellValid',
            'Coup disponible',
            null
          );
          cell.setAttribute('aria-label', `${formattedLabel} — ${moveLabel}`.trim());
        } else {
          cell.setAttribute('aria-label', formattedLabel);
        }
      }
    }
    updateScore();
    scheduleBoardSizingUpdate();
  }

  function parsePixelValue(value) {
    if (typeof value !== 'string') {
      return 0;
    }
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function updateBoardSizing() {
    if (!state.elements || !state.elements.board) {
      return;
    }
    if (typeof window === 'undefined') {
      return;
    }
    const board = state.elements.board;
    const container = board.parentElement instanceof HTMLElement ? board.parentElement : board;
    const rect = container.getBoundingClientRect();
    const containerWidth = rect.width;
    if (!Number.isFinite(containerWidth) || containerWidth <= 0) {
      return;
    }
    const targetWidth = containerWidth * 0.95;
    if (!Number.isFinite(targetWidth) || targetWidth <= 0) {
      return;
    }
    const computedStyle = window.getComputedStyle(board);
    const paddingLeft = parsePixelValue(computedStyle.paddingLeft);
    const paddingRight = parsePixelValue(computedStyle.paddingRight);
    const borderLeft = parsePixelValue(computedStyle.borderLeftWidth);
    const borderRight = parsePixelValue(computedStyle.borderRightWidth);
    const gapValue = parsePixelValue(
      computedStyle.columnGap || computedStyle.gridColumnGap || computedStyle.gap
    );
    const outerSpacing = paddingLeft + paddingRight + borderLeft + borderRight;
    const gapTotal = gapValue * Math.max(0, BOARD_SIZE - 1);
    const maxCellSize = Math.max((targetWidth - outerSpacing - gapTotal) / BOARD_SIZE, 0);
    let cellSize = maxCellSize;
    if (!Number.isFinite(cellSize) || cellSize <= 0) {
      const fallback = parsePixelValue(computedStyle.getPropertyValue('--othello-cell-size'));
      cellSize = fallback > 0 ? Math.min(fallback, maxCellSize) : maxCellSize;
    }
    if (!Number.isFinite(cellSize) || cellSize <= 0) {
      return;
    }
    const contentWidth = cellSize * BOARD_SIZE;
    const boardWidth = outerSpacing + gapTotal + contentWidth;
    const boundedWidth = Math.min(boardWidth, targetWidth);
    board.style.setProperty('--othello-cell-size', `${cellSize}px`);
    board.style.setProperty('--othello-board-max-width', `${Math.max(boundedWidth, 0)}px`);
    board.style.gridTemplateColumns = `repeat(${BOARD_SIZE}, var(--othello-cell-size))`;
    board.style.gridAutoRows = 'var(--othello-cell-size)';
    board.style.maxWidth = `${Math.max(boundedWidth, 0)}px`;
  }

  function scheduleBoardSizingUpdate() {
    if (typeof window === 'undefined') {
      updateBoardSizing();
      return;
    }
    if (pendingBoardSizingFrame != null) {
      return;
    }
    const run = () => {
      pendingBoardSizingFrame = null;
      updateBoardSizing();
    };
    if (typeof window.requestAnimationFrame === 'function') {
      pendingBoardSizingFrame = window.requestAnimationFrame(run);
    } else {
      pendingBoardSizingFrame = window.setTimeout(run, 16);
    }
  }

  function initBoardResizeObserver() {
    if (!state.elements || !state.elements.board || typeof window === 'undefined') {
      return;
    }
    if (typeof window.ResizeObserver !== 'function') {
      return;
    }
    const board = state.elements.board;
    const target = board.parentElement instanceof HTMLElement ? board.parentElement : board;
    if (!target) {
      return;
    }
    if (boardResizeObserver) {
      try {
        boardResizeObserver.disconnect();
      } catch (error) {
        // Ignore disconnect errors.
      }
    }
    boardResizeObserver = new window.ResizeObserver(() => {
      scheduleBoardSizingUpdate();
    });
    boardResizeObserver.observe(target);
  }

  function initBoardViewportListeners() {
    if (typeof window === 'undefined' || boardViewportListenersAttached) {
      return;
    }
    const handleViewportResize = () => {
      scheduleBoardSizingUpdate();
    };
    window.addEventListener('resize', handleViewportResize, { passive: true });
    window.addEventListener('orientationchange', handleViewportResize);
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', handleViewportResize);
      window.visualViewport.addEventListener('scroll', handleViewportResize);
    }
    boardViewportListenersAttached = true;
  }

  function updateScore() {
    const score = getScore();
    const params = { black: String(score.black), white: String(score.white) };
    const text = translate(
      'scripts.arcade.othello.score',
      'Noirs : {black} · Blancs : {white}',
      params
    );
    state.elements.score.textContent = formatString(text, params);
  }

  function refreshStatus() {
    if (!state.lastStatus) {
      return;
    }
    const { key, fallback, params } = state.lastStatus;
    const text = translate(key, fallback, params);
    state.elements.status.textContent = formatString(text, params);
  }

  function setStatus(key, fallback, params) {
    state.lastStatus = { key, fallback, params: params || null };
    refreshStatus();
  }

  function updateResultIndicator(resultKey, score) {
    if (!state.elements || !state.elements.result) {
      return;
    }
    const element = state.elements.result;
    element.classList.remove('othello__result--black', 'othello__result--white', 'othello__result--draw');
    if (!resultKey) {
      const placeholder = translate(
        'index.sections.othello.result.placeholder',
        'Le résultat de la partie s’affichera ici.',
        null
      );
      element.textContent = placeholder;
      element.hidden = true;
      return;
    }
    const params = {
      black: String(score?.black ?? ''),
      white: String(score?.white ?? '')
    };
    const fallback = resultKey === 'black'
      ? 'Victoire des noirs ! Noir {black} – Blanc {white}.'
      : resultKey === 'white'
        ? 'Victoire des blancs ! Noir {black} – Blanc {white}.'
        : 'Égalité parfaite ! Noir {black} – Blanc {white}.';
    const text = translate(
      `scripts.arcade.othello.resultBanner.${resultKey}`,
      fallback,
      params
    );
    element.textContent = formatString(text, params);
    element.hidden = false;
    element.classList.add(`othello__result--${resultKey}`);
  }

  function beginTurn() {
    if (!isHumanControlled(state.currentPlayer)) {
      setStatus(
        'scripts.arcade.othello.status.aiTurn',
        'L’IA (noirs) réfléchit…',
        null
      );
      scheduleAIMove();
      return;
    }
    const playerKey = state.currentPlayer === PLAYERS.BLACK ? 'black' : 'white';
    setStatus(
      `scripts.arcade.othello.status.humanTurn.${playerKey}`,
      playerKey === 'black' ? 'Aux noirs de jouer.' : 'À vous de jouer (blancs).',
      null
    );
  }

  function scheduleAIMove() {
    if (typeof window === 'undefined' || state.mode !== GAME_MODES.SOLO) {
      return;
    }
    state.aiTimeout = window.setTimeout(() => {
      state.aiTimeout = null;
      if (state.currentPlayer !== AI_PLAYER || state.mode !== GAME_MODES.SOLO) {
        return;
      }
      const move = chooseAIMove();
      if (!move) {
        prepareTurn();
        return;
      }
      const flips = state.validMoves.get(getMoveKey(move.row, move.col));
      if (!flips) {
        prepareTurn();
        return;
      }
      placeDisc(move.row, move.col, flips);
    }, AI_MOVE_DELAY);
  }

  function chooseAIMove() {
    if (state.validMoves.size === 0) {
      return null;
    }
    const weights = [
      [120, -20, 20, 5, 5, 20, -20, 120],
      [-20, -40, -5, -5, -5, -5, -40, -20],
      [20, -5, 15, 3, 3, 15, -5, 20],
      [5, -5, 3, 3, 3, 3, -5, 5],
      [5, -5, 3, 3, 3, 3, -5, 5],
      [20, -5, 15, 3, 3, 15, -5, 20],
      [-20, -40, -5, -5, -5, -5, -40, -20],
      [120, -20, 20, 5, 5, 20, -20, 120]
    ];
    let bestMove = null;
    let bestScore = -Infinity;
    state.validMoves.forEach((flips, key) => {
      const [rowStr, colStr] = key.split(',');
      const row = Number.parseInt(rowStr, 10);
      const col = Number.parseInt(colStr, 10);
      if (Number.isNaN(row) || Number.isNaN(col)) {
        return;
      }
      const positional = weights[row][col] || 0;
      const score = positional + flips.length * 10;
      if (!bestMove || score > bestScore) {
        bestMove = { row, col };
        bestScore = score;
      }
    });
    return bestMove;
  }

  function clearAIMoveTimer() {
    if (state.aiTimeout !== null && typeof window !== 'undefined') {
      window.clearTimeout(state.aiTimeout);
      state.aiTimeout = null;
    }
  }

  function getScore() {
    let black = 0;
    let white = 0;
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        if (state.board[row][col] === PLAYERS.BLACK) {
          black += 1;
        } else if (state.board[row][col] === PLAYERS.WHITE) {
          white += 1;
        }
      }
    }
    return { black, white };
  }

  function getCellContentLabel(contentKey) {
    return translate(
      `scripts.arcade.othello.cellStates.${contentKey}`,
      contentKey === 'black'
        ? 'pion noir'
        : contentKey === 'white'
          ? 'pion blanc'
          : 'vide',
      null
    );
  }

  function getPlayerName(player) {
    return translate(
      player === PLAYERS.BLACK
        ? 'scripts.arcade.othello.players.black'
        : 'scripts.arcade.othello.players.white',
      player === PLAYERS.BLACK ? 'noirs' : 'blancs',
      null
    );
  }

  function isHumanControlled(player) {
    if (state.mode === GAME_MODES.DUO) {
      return true;
    }
    return player !== AI_PLAYER;
  }

  function translate(key, fallback, params) {
    const translator = typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
      ? window.i18n.t.bind(window.i18n)
      : typeof window !== 'undefined' && typeof window.t === 'function'
        ? window.t
        : null;
    if (translator) {
      try {
        const result = translator(key, params || undefined);
        if (typeof result === 'string') {
          const trimmed = result.trim();
          if (trimmed && trimmed !== key) {
            return formatString(trimmed, params);
          }
        }
      } catch (error) {
        console.warn('Othello translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return formatString(fallback, params);
    }
    return key;
  }

  function formatString(template, params) {
    if (typeof template !== 'string' || !params) {
      return template;
    }
    return template.replace(/{{\s*(\w+)\s*}}|{\s*(\w+)\s*}/g, (match, keyA, keyB) => {
      const key = keyA || keyB;
      if (!key || !(key in params)) {
        return match;
      }
      return params[key];
    });
  }

  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    return window.ArcadeAutosave || null;
  }

  function scheduleAutosave() {
    if (typeof window === 'undefined') {
      return;
    }
    const api = getAutosaveApi();
    if (!api || typeof api.set !== 'function') {
      return;
    }
    if (autosaveTimerId != null) {
      window.clearTimeout(autosaveTimerId);
    }
    autosaveTimerId = window.setTimeout(() => {
      autosaveTimerId = null;
      persistAutosave();
    }, 120);
  }

  function persistAutosave() {
    const api = getAutosaveApi();
    if (!api || typeof api.set !== 'function') {
      return;
    }
    const payload = buildAutosavePayload();
    if (!payload) {
      return;
    }
    try {
      api.set(AUTOSAVE_GAME_ID, payload);
    } catch (error) {
      // Ignore autosave persistence errors to avoid disrupting gameplay
    }
  }

  function buildAutosavePayload() {
    if (!Array.isArray(state.board) || state.board.length !== BOARD_SIZE) {
      return null;
    }
    const board = new Array(BOARD_SIZE);
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      const sourceRow = Array.isArray(state.board[row]) ? state.board[row] : [];
      board[row] = new Array(BOARD_SIZE);
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const value = Number(sourceRow[col]);
        if (value === PLAYERS.BLACK) {
          board[row][col] = PLAYERS.BLACK;
        } else if (value === PLAYERS.WHITE) {
          board[row][col] = PLAYERS.WHITE;
        } else {
          board[row][col] = 0;
        }
      }
    }
    const currentPlayer = state.currentPlayer === PLAYERS.BLACK ? PLAYERS.BLACK : PLAYERS.WHITE;
    const rawPasses = Number(state.passes);
    const passes = Number.isFinite(rawPasses) ? Math.max(0, Math.min(2, Math.floor(rawPasses))) : 0;
    const mode = state.mode === GAME_MODES.DUO ? GAME_MODES.DUO : GAME_MODES.SOLO;
    const blackMoves = computeValidMoves(PLAYERS.BLACK);
    const whiteMoves = computeValidMoves(PLAYERS.WHITE);
    const gameOver = state.gameOver || passes >= 2 || (blackMoves.size === 0 && whiteMoves.size === 0);
    return {
      board,
      currentPlayer,
      mode,
      passes,
      gameOver,
      rewardGranted: state.rewardGranted === true,
      updatedAt: Date.now()
    };
  }

  function restoreAutosave() {
    const api = getAutosaveApi();
    if (!api || typeof api.get !== 'function') {
      return false;
    }
    let saved;
    try {
      saved = api.get(AUTOSAVE_GAME_ID);
    } catch (error) {
      return false;
    }
    const normalized = sanitizeSavedState(saved);
    if (!normalized) {
      return false;
    }
    state.mode = normalized.mode;
    state.board = normalized.board;
    state.currentPlayer = normalized.currentPlayer;
    state.passes = normalized.passes;
    state.rewardGranted = normalized.rewardGranted;
    state.gameOver = normalized.gameOver;
    if (!state.gameOver) {
      updateResultIndicator(null, null);
    }
    updateModeToggle();
    clearAIMoveTimer();
    const blackMoves = computeValidMoves(PLAYERS.BLACK);
    const whiteMoves = computeValidMoves(PLAYERS.WHITE);
    const gameOver = normalized.gameOver || normalized.passes >= 2 || (blackMoves.size === 0 && whiteMoves.size === 0);
    if (gameOver) {
      endGame();
      return true;
    }
    state.validMoves = computeValidMoves(state.currentPlayer);
    renderBoard();
    if (state.validMoves.size === 0) {
      prepareTurn();
      return true;
    }
    beginTurn();
    scheduleAutosave();
    return true;
  }

  function sanitizeSavedState(saved) {
    if (!saved || typeof saved !== 'object') {
      return null;
    }
    const board = sanitizeSavedBoard(saved.board);
    if (!board) {
      return null;
    }
    const playerValue = Number(saved.currentPlayer);
    const currentPlayer = playerValue === PLAYERS.BLACK
      ? PLAYERS.BLACK
      : playerValue === PLAYERS.WHITE
        ? PLAYERS.WHITE
        : null;
    if (currentPlayer == null) {
      return null;
    }
    const mode = saved.mode === GAME_MODES.DUO ? GAME_MODES.DUO : GAME_MODES.SOLO;
    const rawPasses = Number(saved.passes);
    const passes = Number.isFinite(rawPasses) ? Math.max(0, Math.min(2, Math.floor(rawPasses))) : 0;
    const gameOver = saved.gameOver === true;
    const rewardGranted = saved.rewardGranted === true;
    return {
      board,
      currentPlayer,
      mode,
      passes,
      gameOver,
      rewardGranted
    };
  }

  function sanitizeSavedBoard(data) {
    if (!Array.isArray(data) || data.length !== BOARD_SIZE) {
      return null;
    }
    const board = new Array(BOARD_SIZE);
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      const sourceRow = data[row];
      if (!Array.isArray(sourceRow) || sourceRow.length !== BOARD_SIZE) {
        return null;
      }
      board[row] = new Array(BOARD_SIZE);
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const value = Number(sourceRow[col]);
        if (value === PLAYERS.BLACK) {
          board[row][col] = PLAYERS.BLACK;
        } else if (value === PLAYERS.WHITE) {
          board[row][col] = PLAYERS.WHITE;
        } else {
          board[row][col] = 0;
        }
      }
    }
    return board;
  }

  function getMoveKey(row, col) {
    return `${row},${col}`;
  }

  function createEmptyBoard() {
    const board = new Array(BOARD_SIZE);
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      board[row] = new Array(BOARD_SIZE).fill(0);
    }
    return board;
  }

  if (typeof window !== 'undefined') {
    const existingApi = typeof window.othelloArcade === 'object' && window.othelloArcade
      ? window.othelloArcade
      : {};
    const previousOnEnter = typeof existingApi.onEnter === 'function' ? existingApi.onEnter : null;
    existingApi.onEnter = () => {
      if (typeof previousOnEnter === 'function') {
        try {
          previousOnEnter();
        } catch (error) {
          // Ignore errors from previous onEnter handlers to preserve new sizing logic.
        }
      }
      scheduleBoardSizingUpdate();
    };
    window.othelloArcade = existingApi;
  }
})();
