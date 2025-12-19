(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const RAW_CONFIG =
    GLOBAL_CONFIG
    && GLOBAL_CONFIG.arcade
    && GLOBAL_CONFIG.arcade.gomoku
      ? GLOBAL_CONFIG.arcade.gomoku
      : {};

  const RULE_SET = Object.freeze({
    GOMOKU: 'gomoku',
    RENJU: 'renju'
  });

  const GAME_MODES = Object.freeze({
    AI: 'ai',
    LOCAL: 'local'
  });

  const PLAYERS = Object.freeze({
    BLACK: 1,
    WHITE: -1
  });

  const PLAYER_LABEL_KEY = Object.freeze({
    [PLAYERS.BLACK]: 'index.sections.gomoku.players.black',
    [PLAYERS.WHITE]: 'index.sections.gomoku.players.white'
  });

  const PLAYER_TOKEN = Object.freeze({
    [PLAYERS.BLACK]: 'B',
    [PLAYERS.WHITE]: 'W',
    0: '.'
  });

  const DIRECTIONS = Object.freeze([
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1]
  ]);

  const DEFAULT_WEIGHTS = Object.freeze({
    five: 1_000_000,
    openFour: 10_000,
    closedFour: 1_000,
    openThree: 500,
    closedThree: 80,
    two: 20,
    centerBonus: 15
  });

  const DEFAULT_SEARCH = Object.freeze({
    easy: { maxDepth: 2, maxTimeMs: 600, iterative: false, maxCandidates: 10 },
    medium: { maxDepth: 4, maxTimeMs: 1000, iterative: true, maxCandidates: 14 },
    hard: { maxDepth: 6, maxTimeMs: 2000, iterative: true, moveOrdering: true, maxCandidates: 20 }
  });

  const DEFAULT_BOARD_SETTINGS = Object.freeze({
    defaultSize: 15,
    availableSizes: [13, 15, 19],
    neighborRadius: 2,
    maxHistory: 256
  });

  const AI_VICTORY_REWARDS = Object.freeze({
    easy: 1,
    medium: 2,
    hard: 3
  });

  const MAX_BOARD_SIZE = 19;
  const EMPTY = 0;

  const AUTOSAVE_GAME_ID = 'gomoku';
  const SETTINGS_STORAGE_KEY = 'atom2univers.arcade.gomoku.settings';

  const CONFIG = normalizeConfig(RAW_CONFIG);

  const state = {
    ruleSet: RULE_SET.GOMOKU,
    mode: GAME_MODES.AI,
    difficulty: 'medium',
    boardSize: CONFIG.board.defaultSize,
    board: null,
    currentPlayer: PLAYERS.BLACK,
    elements: null,
    cells: [],
    gameOver: false,
    awaitingAI: false,
    aiTimeoutId: null,
    aiWorkerToken: 0,
    winner: null,
    winningLine: null,
    lastMove: null,
    autosaveTimerId: null,
    languageListenerAttached: false,
    languageHandler: null
  };

  const zobrist = createZobristTable(MAX_BOARD_SIZE);

  onReady(initialize);
  function normalizeConfig(raw) {
    const board = { ...DEFAULT_BOARD_SETTINGS };
    if (raw.board && typeof raw.board === 'object') {
      if (Array.isArray(raw.board.availableSizes) && raw.board.availableSizes.length > 0) {
        const sizes = raw.board.availableSizes
          .map(value => Number.parseInt(value, 10))
          .filter(value => Number.isInteger(value) && value >= 5 && value <= MAX_BOARD_SIZE);
        if (sizes.length > 0) {
          board.availableSizes = sizes;
        }
      }
      if (Number.isInteger(raw.board.defaultSize)) {
        board.defaultSize = clampBoardSize(raw.board.defaultSize, board.availableSizes[0]);
      }
      if (Number.isInteger(raw.board.neighborRadius) && raw.board.neighborRadius >= 1) {
        board.neighborRadius = raw.board.neighborRadius;
      }
      if (Number.isInteger(raw.board.maxHistory) && raw.board.maxHistory > 0) {
        board.maxHistory = raw.board.maxHistory;
      }
    }

    const renju = {
      exactFiveForWhite: raw.renju && raw.renju.exactFiveForWhite === false ? false : true
    };

    const evaluation = { ...DEFAULT_WEIGHTS };
    if (raw.evaluation && typeof raw.evaluation === 'object') {
      Object.keys(DEFAULT_WEIGHTS).forEach(key => {
        const value = Number(raw.evaluation[key]);
        if (Number.isFinite(value)) {
          evaluation[key] = value;
        }
      });
    }

    const search = {
      easy: { ...DEFAULT_SEARCH.easy },
      medium: { ...DEFAULT_SEARCH.medium },
      hard: { ...DEFAULT_SEARCH.hard }
    };
    if (raw.search && typeof raw.search === 'object') {
      Object.keys(search).forEach(key => {
        const entry = raw.search[key];
        if (!entry || typeof entry !== 'object') {
          return;
        }
        if (Number.isInteger(entry.maxDepth) && entry.maxDepth > 0) {
          search[key].maxDepth = entry.maxDepth;
        }
        if (Number.isFinite(entry.maxTimeMs) && entry.maxTimeMs > 0) {
          search[key].maxTimeMs = entry.maxTimeMs;
        }
        if (typeof entry.iterative === 'boolean') {
          search[key].iterative = entry.iterative;
        }
        if (typeof entry.moveOrdering === 'boolean') {
          search[key].moveOrdering = entry.moveOrdering;
        }
        if (Number.isInteger(entry.maxCandidates) && entry.maxCandidates > 0) {
          search[key].maxCandidates = entry.maxCandidates;
        }
      });
    }

    return Object.freeze({ board, renju, evaluation, search });
  }

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function initialize() {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    buildBoard();
    attachEvents();
    restoreSettings();
    if (!restoreAutosave()) {
      resetGame(true);
    } else {
      renderEverything();
    }
    attachLanguageListener();
  }

  function getElements() {
    const root = document.querySelector('[data-gomoku-root]');
    if (!root) {
      return null;
    }
    const board = document.getElementById('gomokuBoard');
    const ruleSelect = document.getElementById('gomokuRuleSelect');
    const modeSelect = document.getElementById('gomokuModeSelect');
    const sizeSelect = document.getElementById('gomokuSizeSelect');
    const difficultySelect = document.getElementById('gomokuDifficultySelect');
    const status = document.getElementById('gomokuStatus');
    const ruleTag = document.getElementById('gomokuRuleTag');
    const modeTag = document.getElementById('gomokuModeTag');
    const difficultyTag = document.getElementById('gomokuDifficultyTag');
    const undoButton = document.getElementById('gomokuUndoButton');
    const restartButton = document.getElementById('gomokuRestartButton');
    const toast = document.getElementById('gomokuToast');

    if (!board || !ruleSelect || !modeSelect || !sizeSelect || !difficultySelect) {
      return null;
    }

    return {
      root,
      board,
      ruleSelect,
      modeSelect,
      sizeSelect,
      difficultySelect,
      status,
      ruleTag,
      modeTag,
      difficultyTag,
      undoButton,
      restartButton,
      toast
    };
  }

  function attachLanguageListener() {
    if (state.languageListenerAttached) {
      return;
    }
    const handler = () => {
      updateControlLabels();
      updateTags();
      updateStatus();
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageListenerAttached = true;
    state.languageHandler = handler;
  }

  function attachEvents() {
    const { ruleSelect, modeSelect, sizeSelect, difficultySelect } = state.elements;
    const { undoButton, restartButton, board } = state.elements;

    ruleSelect.addEventListener('change', () => setRuleSet(ruleSelect.value));
    modeSelect.addEventListener('change', () => setMode(modeSelect.value));
    sizeSelect.addEventListener('change', () => {
      const value = Number.parseInt(sizeSelect.value, 10);
      if (Number.isInteger(value)) {
        setBoardSize(value);
      }
    });
    difficultySelect.addEventListener('change', () => setDifficulty(difficultySelect.value));

    if (undoButton) {
      undoButton.addEventListener('click', handleUndo);
    }
    if (restartButton) {
      restartButton.addEventListener('click', () => resetGame(false));
    }

    board.addEventListener('click', handleBoardClick);
  }
  function buildBoard() {
    if (!state.elements || !state.elements.board) {
      return;
    }
    const { board } = state.elements;
    const size = state.boardSize;
    board.innerHTML = '';
    board.style.gridTemplateColumns = `repeat(${size}, var(--gomoku-cell-size))`;
    board.style.gridAutoRows = 'var(--gomoku-cell-size)';
    board.setAttribute('aria-rowcount', String(size));
    board.setAttribute('aria-colcount', String(size));
    const cells = new Array(size);
    for (let row = 0; row < size; row += 1) {
      cells[row] = new Array(size);
      for (let col = 0; col < size; col += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'gomoku__cell';
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
  }

  function resetGame(fromInitialization) {
    cancelAiMove();
    clearToast();
    state.board = Board.empty(state.boardSize);
    state.currentPlayer = PLAYERS.BLACK;
    state.gameOver = false;
    state.awaitingAI = false;
    state.winner = null;
    state.winningLine = null;
    state.lastMove = null;
    renderBoard();
    updateTags();
    updateStatus();
    saveSettings();
    scheduleAutosave();
  }

  function renderEverything() {
    renderBoard();
    updateTags();
    updateStatus();
    updateControlsVisibility();
  }

  function renderBoard() {
    if (!state.cells || !state.board) {
      return;
    }
    const size = state.board.size;
    for (let row = 0; row < size; row += 1) {
      for (let col = 0; col < size; col += 1) {
        updateCell(row, col);
      }
    }
  }

  function updateCell(row, col) {
    const cellElement = state.cells[row][col];
    if (!cellElement) {
      return;
    }
    const value = state.board.getCell(row, col);
    cellElement.classList.remove('gomoku__cell--black', 'gomoku__cell--white', 'gomoku__cell--last', 'gomoku__cell--win', 'gomoku__cell--hint');
    if (value === PLAYERS.BLACK) {
      cellElement.classList.add('gomoku__cell--black');
    } else if (value === PLAYERS.WHITE) {
      cellElement.classList.add('gomoku__cell--white');
    }
    if (state.lastMove && state.lastMove.row === row && state.lastMove.col === col) {
      cellElement.classList.add('gomoku__cell--last');
    }
    if (state.winningLine && state.winningLine.some(pos => pos.row === row && pos.col === col)) {
      cellElement.classList.add('gomoku__cell--win');
    }
  }

  function updateTags() {
    if (!state.elements) {
      return;
    }
    const { ruleTag, modeTag, difficultyTag } = state.elements;
    if (ruleTag) {
      ruleTag.textContent = translate(getRuleLabelKey(state.ruleSet), 'Gomoku', null);
    }
    if (modeTag) {
      modeTag.textContent = translate(getModeLabelKey(state.mode), 'Mode', null);
    }
    if (difficultyTag) {
      const diffKey = `index.sections.gomoku.controls.difficulty.${state.difficulty}`;
      difficultyTag.textContent = translate(diffKey, formatDifficultyFallback(state.difficulty), null);
      difficultyTag.hidden = state.mode !== GAME_MODES.AI;
    }
  }

  function updateStatus(customMessage) {
    if (!state.elements || !state.elements.status) {
      return;
    }
    const { status } = state.elements;
    if (customMessage) {
      status.textContent = customMessage;
      return;
    }
    if (state.gameOver) {
      if (state.winner) {
        const key = state.winner === PLAYERS.BLACK
          ? 'index.sections.gomoku.status.winBlack'
          : 'index.sections.gomoku.status.winWhite';
        const fallback = state.winner === PLAYERS.BLACK ? 'Victoire des noirs !' : 'Victoire des blancs !';
        status.textContent = translate(key, fallback, null);
      } else {
        status.textContent = translate('index.sections.gomoku.status.draw', 'Égalité : plus aucune case disponible.', null);
      }
      return;
    }

    if (state.awaitingAI) {
      status.textContent = translate('index.sections.gomoku.status.aiThinking', 'L’IA réfléchit…', null);
      return;
    }

    const key = state.currentPlayer === PLAYERS.BLACK
      ? 'index.sections.gomoku.status.turnBlack'
      : 'index.sections.gomoku.status.turnWhite';
    const fallback = state.currentPlayer === PLAYERS.BLACK ? 'À vous de jouer (noirs).' : 'Au tour des blancs.';
    status.textContent = translate(key, fallback, null);
  }

  function updateControlsVisibility() {
    if (!state.elements) {
      return;
    }
    const { difficultySelect } = state.elements;
    if (difficultySelect) {
      difficultySelect.parentElement.style.display = state.mode === GAME_MODES.AI ? '' : 'none';
    }
    if (state.elements.difficultyTag) {
      state.elements.difficultyTag.hidden = state.mode !== GAME_MODES.AI;
    }
    if (state.elements.undoButton) {
      state.elements.undoButton.disabled = false;
    }
  }
  function handleBoardClick(event) {
    if (state.gameOver || !state.board) {
      return;
    }
    if (state.mode === GAME_MODES.AI && state.awaitingAI) {
      return;
    }
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const cell = target.closest('button.gomoku__cell');
    if (!cell) {
      return;
    }
    const row = Number.parseInt(cell.dataset.row || '', 10);
    const col = Number.parseInt(cell.dataset.col || '', 10);
    if (!Number.isInteger(row) || !Number.isInteger(col)) {
      return;
    }
    playHumanMove(row, col);
  }

  function playHumanMove(row, col) {
    if (!state.board || !state.board.isInside(row, col)) {
      return;
    }
    if (!state.board.isEmpty(row, col)) {
      showToast(translate('index.sections.gomoku.status.cellOccupied', 'Case déjà occupée.', null));
      return;
    }
    const player = state.currentPlayer;
    if (state.mode === GAME_MODES.AI && player !== PLAYERS.BLACK) {
      return;
    }
    const success = executeMove(row, col, player, { isAI: false });
    if (success && state.mode === GAME_MODES.AI && !state.gameOver) {
      scheduleAiMove();
    }
  }

  function executeMove(row, col, player, options = {}) {
    const isRenju = state.ruleSet === RULE_SET.RENJU;
    const move = new Move(row, col, player);
    const isAiMove = Boolean(options && options.isAI);
    const foul = isRenju && player === PLAYERS.BLACK ? checkRenjuFoul(state.board, move) : null;
    if (foul && foul.illegal) {
      showToast(translate('index.sections.gomoku.status.illegalRenju', 'Coup interdit pour Noir (Renju).', null));
      return false;
    }

    const nextBoard = state.board.applyMove(move);
    state.board = nextBoard;
    state.lastMove = move;
    state.currentPlayer = invertPlayer(player);
    state.winningLine = null;

    const winnerInfo = determineWinner(nextBoard, move);
    if (winnerInfo) {
      state.gameOver = true;
      state.winner = winnerInfo.player;
      state.winningLine = winnerInfo.line;
      if (!isAiMove && state.mode === GAME_MODES.AI && winnerInfo.player === PLAYERS.BLACK) {
        grantVsAiVictoryReward();
      }
    }

    if (!state.gameOver && nextBoard.isFull()) {
      state.gameOver = true;
      state.winner = null;
      state.winningLine = null;
    }

    renderBoard();
    updateStatus();
    scheduleAutosave();
    return true;
  }

  function grantVsAiVictoryReward() {
    const tickets = Number(AI_VICTORY_REWARDS[state.difficulty] || 0);
    if (!Number.isFinite(tickets) || tickets <= 0) {
      return;
    }

    const awardFn = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;

    let gained = tickets;
    if (typeof awardFn === 'function') {
      try {
        const result = awardFn(tickets, { unlockTicketStar: true });
        if (Number.isFinite(result) && result > 0) {
          gained = Math.floor(result);
        }
      } catch (error) {
        console.warn('Gomoku: unable to grant gacha tickets', error);
        gained = tickets;
      }
    }

    if (!Number.isFinite(gained) || gained <= 0) {
      return;
    }

    const suffix = gained > 1 ? 's' : '';
    const message = translate(
      'scripts.arcade.gomoku.rewards.vsAiVictory',
      'Victoire contre l’IA ! +{count} ticket{suffix} gacha.',
      { count: formatNumber(gained), suffix }
    );
    showToast(message);
  }

  function determineWinner(board, lastMove) {
    if (!lastMove) {
      return null;
    }
    const exactFiveForWhite = state.ruleSet === RULE_SET.RENJU && CONFIG.renju.exactFiveForWhite;
    for (let i = 0; i < DIRECTIONS.length; i += 1) {
      const dir = DIRECTIONS[i];
      const info = getRunInfo(board, lastMove.row, lastMove.col, lastMove.player, dir[0], dir[1]);
      if (!info) {
        continue;
      }
      if (state.ruleSet === RULE_SET.RENJU) {
        if (lastMove.player === PLAYERS.BLACK) {
          if (info.length === 5) {
            return { player: lastMove.player, line: info.positions };
          }
        } else {
          if (info.length > 5 && exactFiveForWhite) {
            continue;
          }
          if (info.length >= 5) {
            return { player: lastMove.player, line: info.positions };
          }
        }
      } else if (info.length >= 5) {
        return { player: lastMove.player, line: info.positions };
      }
    }
    return null;
  }

  function checkRenjuFoul(board, move) {
    const candidate = board.applyMove(move);
    const patterns = analyzeRenjuPatterns(candidate, move);
    return {
      illegal: patterns.overline || patterns.openThrees >= 2 || patterns.fours >= 2,
      details: patterns
    };
  }

  function isLegalMoveForRuleSet(board, move, ruleSet) {
    if (ruleSet === RULE_SET.RENJU && move.player === PLAYERS.BLACK) {
      const foul = checkRenjuFoul(board, move);
      return !(foul && foul.illegal);
    }
    return true;
  }

  function analyzeRenjuPatterns(board, move) {
    let overline = false;
    let fours = 0;
    let openThrees = 0;
    const visitedOpenThreeKeys = new Set();
    for (let i = 0; i < DIRECTIONS.length; i += 1) {
      const dir = DIRECTIONS[i];
      const info = getRunInfo(board, move.row, move.col, move.player, dir[0], dir[1]);
      if (!info) {
        continue;
      }
      if (info.length >= 6) {
        overline = true;
      }
      if (info.length === 4 && (info.openLeft || info.openRight)) {
        fours += 1;
      }
      const candidates = gatherOpenThreeCandidates(board, move, info, dir[0], dir[1]);
      candidates.forEach(key => {
        if (!visitedOpenThreeKeys.has(key)) {
          visitedOpenThreeKeys.add(key);
          openThrees += 1;
        }
      });
    }
    return { overline, fours, openThrees };
  }

  function gatherOpenThreeCandidates(board, move, runInfo, dRow, dCol) {
    const results = [];
    const line = extractLine(board, move.row, move.col, dRow, dCol);
    const { cells, coords, index } = line;
    if (!cells || cells.length === 0) {
      return results;
    }
    const emptySymbol = PLAYER_TOKEN[EMPTY];

    for (let offset = -4; offset <= 4; offset += 1) {
      const candidateIndex = index + offset;
      if (candidateIndex < 0 || candidateIndex >= cells.length) {
        continue;
      }
      if (cells[candidateIndex] !== emptySymbol) {
        continue;
      }
      const coord = coords[candidateIndex];
      const simulated = board.applyMove(new Move(coord.row, coord.col, move.player));
      const run = getRunInfo(simulated, coord.row, coord.col, move.player, dRow, dCol);
      if (!run) {
        continue;
      }
      if (run.length === 4 && run.openLeft && run.openRight) {
        const includesOriginal = run.positions.some(pos => pos.row === move.row && pos.col === move.col);
        if (includesOriginal) {
          results.push(`${coord.row}:${coord.col}:${dRow}:${dCol}`);
        }
      }
    }

    return results;
  }

  function getRunInfo(board, row, col, player, dRow, dCol) {
    if (!board || !board.isInside(row, col)) {
      return null;
    }
    if (board.getCell(row, col) !== player) {
      return null;
    }
    const positions = [{ row, col }];
    let length = 1;
    let r = row + dRow;
    let c = col + dCol;
    while (board.isInside(r, c) && board.getCell(r, c) === player) {
      positions.push({ row: r, col: c });
      length += 1;
      r += dRow;
      c += dCol;
    }
    const forwardEnd = { row: r, col: c };
    r = row - dRow;
    c = col - dCol;
    while (board.isInside(r, c) && board.getCell(r, c) === player) {
      positions.unshift({ row: r, col: c });
      length += 1;
      r -= dRow;
      c -= dCol;
    }
    const backwardEnd = { row: r, col: c };
    const openLeft = board.isInside(backwardEnd.row, backwardEnd.col)
      ? board.getCell(backwardEnd.row, backwardEnd.col) === EMPTY
      : false;
    const openRight = board.isInside(forwardEnd.row, forwardEnd.col)
      ? board.getCell(forwardEnd.row, forwardEnd.col) === EMPTY
      : false;
    return { length, positions, openLeft, openRight };
  }

  function extractLine(board, row, col, dRow, dCol) {
    const cells = [];
    const coords = [];
    let startRow = row;
    let startCol = col;
    while (board.isInside(startRow - dRow, startCol - dCol)) {
      startRow -= dRow;
      startCol -= dCol;
    }
    let index = 0;
    let currentRow = startRow;
    let currentCol = startCol;
    while (board.isInside(currentRow, currentCol)) {
      const value = board.getCell(currentRow, currentCol);
      cells.push(PLAYER_TOKEN[value]);
      coords.push({ row: currentRow, col: currentCol });
      if (currentRow === row && currentCol === col) {
        index = cells.length - 1;
      }
      currentRow += dRow;
      currentCol += dCol;
    }
    return { cells, coords, index };
  }
  function scheduleAiMove() {
    cancelAiMove();
    state.awaitingAI = true;
    updateStatus();
    const token = ++state.aiWorkerToken;
    state.aiTimeoutId = window.setTimeout(() => {
      computeAiMove(token);
    }, 120);
  }

  function cancelAiMove() {
    state.awaitingAI = false;
    if (state.aiTimeoutId != null) {
      window.clearTimeout(state.aiTimeoutId);
      state.aiTimeoutId = null;
    }
  }

  function computeAiMove(token) {
    if (!state.board || state.gameOver || token !== state.aiWorkerToken) {
      state.awaitingAI = false;
      updateStatus();
      return;
    }
    const settings = CONFIG.search[state.difficulty] || CONFIG.search.medium;
    const deadline = nowMs() + settings.maxTimeMs;
    const context = {
      ruleSet: state.ruleSet,
      evaluationWeights: CONFIG.evaluation,
      exactFiveWhite: CONFIG.renju.exactFiveForWhite,
      neighborRadius: CONFIG.board.neighborRadius,
      deadline,
      moveOrdering: Boolean(settings.moveOrdering),
      transposition: settings.iterative ? new Map() : null
    };

    let result;
    if (state.difficulty === 'easy') {
      result = Search.findEasyMove(state.board, state.currentPlayer, context);
    } else {
      result = Search.findBestMove(state.board, state.currentPlayer, settings, context);
    }

    state.awaitingAI = false;
    updateStatus();
    if (!result || !result.move) {
      return;
    }
    executeMove(result.move.row, result.move.col, state.currentPlayer, { isAI: true });
  }

  function handleUndo() {
    if (!state.board) {
      return;
    }
    if (state.board.moveHistory.length === 0) {
      return;
    }
    cancelAiMove();
    const previous = state.board.rewind();
    if (!previous) {
      return;
    }
    state.board = previous.board;
    state.currentPlayer = previous.currentPlayer;
    state.lastMove = previous.lastMove;
    state.gameOver = false;
    state.winner = null;
    state.winningLine = null;
    renderBoard();
    updateStatus();
    scheduleAutosave();
  }
  function setRuleSet(value) {
    if (!Object.values(RULE_SET).includes(value)) {
      value = RULE_SET.GOMOKU;
    }
    if (state.ruleSet === value) {
      return;
    }
    state.ruleSet = value;
    if (state.elements && state.elements.ruleSelect) {
      state.elements.ruleSelect.value = value;
    }
    updateTags();
    saveSettings();
    resetGame(false);
  }

  function setMode(value) {
    if (!Object.values(GAME_MODES).includes(value)) {
      value = GAME_MODES.AI;
    }
    if (state.mode === value) {
      return;
    }
    state.mode = value;
    if (state.elements && state.elements.modeSelect) {
      state.elements.modeSelect.value = value;
    }
    updateControlsVisibility();
    saveSettings();
    resetGame(false);
  }

  function setBoardSize(value) {
    const size = clampBoardSize(value, CONFIG.board.defaultSize);
    if (state.boardSize === size) {
      return;
    }
    state.boardSize = size;
    if (state.elements && state.elements.sizeSelect) {
      state.elements.sizeSelect.value = String(size);
    }
    buildBoard();
    resetGame(false);
    saveSettings();
  }

  function setDifficulty(value) {
    if (!CONFIG.search[value]) {
      value = 'medium';
    }
    state.difficulty = value;
    if (state.elements && state.elements.difficultySelect) {
      state.elements.difficultySelect.value = value;
    }
    updateTags();
    saveSettings();
  }

  function restoreSettings() {
    try {
      const storage = window.localStorage;
      if (!storage) {
        return;
      }
      const raw = storage.getItem(SETTINGS_STORAGE_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') {
        return;
      }
      if (parsed.ruleSet && Object.values(RULE_SET).includes(parsed.ruleSet)) {
        state.ruleSet = parsed.ruleSet;
      }
      if (parsed.mode && Object.values(GAME_MODES).includes(parsed.mode)) {
        state.mode = parsed.mode;
      }
      if (Number.isInteger(parsed.boardSize)) {
        state.boardSize = clampBoardSize(parsed.boardSize, CONFIG.board.defaultSize);
      }
      if (parsed.difficulty && CONFIG.search[parsed.difficulty]) {
        state.difficulty = parsed.difficulty;
      }
      if (state.elements) {
        state.elements.ruleSelect.value = state.ruleSet;
        state.elements.modeSelect.value = state.mode;
        state.elements.sizeSelect.value = String(state.boardSize);
        state.elements.difficultySelect.value = state.difficulty;
      }
      updateControlsVisibility();
    } catch (error) {
      console.warn('Gomoku: unable to restore settings', error);
    }
  }

  function saveSettings() {
    try {
      const storage = window.localStorage;
      if (!storage) {
        return;
      }
      const payload = {
        ruleSet: state.ruleSet,
        mode: state.mode,
        boardSize: state.boardSize,
        difficulty: state.difficulty
      };
      storage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(payload));
    } catch (error) {
      console.warn('Gomoku: unable to persist settings', error);
    }
  }
  function scheduleAutosave() {
    if (typeof window === 'undefined') {
      return;
    }
    if (state.autosaveTimerId != null) {
      window.clearTimeout(state.autosaveTimerId);
    }
    state.autosaveTimerId = window.setTimeout(() => {
      state.autosaveTimerId = null;
      persistAutosave();
    }, 150);
  }

  function getAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    return window.ArcadeAutosave || null;
  }

  function persistAutosave() {
    const api = getAutosaveApi();
    if (!api || typeof api.set !== 'function') {
      return;
    }
    try {
      api.set(AUTOSAVE_GAME_ID, buildAutosavePayload());
    } catch (error) {
      console.warn('Gomoku: unable to persist autosave', error);
    }
  }

  function buildAutosavePayload() {
    return {
      version: 1,
      boardSize: state.board ? state.board.size : state.boardSize,
      cells: state.board ? Array.from(state.board.cells) : [],
      currentPlayer: state.currentPlayer,
      ruleSet: state.ruleSet,
      mode: state.mode,
      difficulty: state.difficulty,
      moveHistory: state.board ? state.board.moveHistory.map(move => ({ row: move.row, col: move.col, player: move.player })) : []
    };
  }

  function restoreAutosave() {
    const api = getAutosaveApi();
    if (!api || typeof api.get !== 'function') {
      return false;
    }
    try {
      const payload = api.get(AUTOSAVE_GAME_ID);
      if (!payload || typeof payload !== 'object') {
        return false;
      }
      const size = clampBoardSize(payload.boardSize, CONFIG.board.defaultSize);
      const board = Board.fromData(size, payload.cells, payload.moveHistory);
      if (!board) {
        return false;
      }
      state.boardSize = size;
      state.board = board;
      state.currentPlayer = payload.currentPlayer === PLAYERS.WHITE ? PLAYERS.WHITE : PLAYERS.BLACK;
      state.ruleSet = Object.values(RULE_SET).includes(payload.ruleSet) ? payload.ruleSet : RULE_SET.GOMOKU;
      state.mode = Object.values(GAME_MODES).includes(payload.mode) ? payload.mode : GAME_MODES.AI;
      state.difficulty = CONFIG.search[payload.difficulty] ? payload.difficulty : 'medium';
      state.gameOver = false;
      state.winner = null;
      state.winningLine = null;
      state.lastMove = board.moveHistory.length > 0 ? board.moveHistory[board.moveHistory.length - 1] : null;
      if (state.elements) {
        state.elements.ruleSelect.value = state.ruleSet;
        state.elements.modeSelect.value = state.mode;
        state.elements.sizeSelect.value = String(state.boardSize);
        state.elements.difficultySelect.value = state.difficulty;
      }
      buildBoard();
      renderEverything();
      return true;
    } catch (error) {
      console.warn('Gomoku: unable to restore autosave', error);
      return false;
    }
  }

  function translate(key, fallback, params) {
    if (typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function') {
      try {
        return window.i18n.t(key, params || {});
      } catch (error) {
        return fallback || key;
      }
    }
    return fallback || key;
  }

  function updateControlLabels() {
    if (!state.elements) {
      return;
    }
    updateTags();
    updateStatus();
  }

  function showToast(message) {
    const toast = state.elements ? state.elements.toast : null;
    if (!toast) {
      return;
    }
    toast.textContent = message;
    toast.hidden = false;
    toast.classList.add('gomoku__toast--visible');
    window.clearTimeout(showToast.timeoutId);
    showToast.timeoutId = window.setTimeout(() => {
      clearToast();
    }, 2200);
  }

  function clearToast() {
    const toast = state.elements ? state.elements.toast : null;
    if (!toast) {
      return;
    }
    toast.classList.remove('gomoku__toast--visible');
    toast.hidden = true;
  }

  function formatNumber(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '';
    }
    const rounded = Math.floor(numeric);
    if (typeof Intl !== 'undefined' && typeof Intl.NumberFormat === 'function') {
      try {
        return new Intl.NumberFormat().format(rounded);
      } catch (error) {
        return String(rounded);
      }
    }
    return String(rounded);
  }

  function nowMs() {
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
      return performance.now();
    }
    return Date.now();
  }

  function invertPlayer(player) {
    return player === PLAYERS.BLACK ? PLAYERS.WHITE : PLAYERS.BLACK;
  }

  function getRuleLabelKey(rule) {
    if (rule === RULE_SET.RENJU) {
      return 'index.sections.gomoku.controls.rules.renju';
    }
    return 'index.sections.gomoku.controls.rules.gomoku';
  }

  function getModeLabelKey(mode) {
    if (mode === GAME_MODES.LOCAL) {
      return 'index.sections.gomoku.controls.mode.local';
    }
    return 'index.sections.gomoku.controls.mode.ai';
  }

  function formatDifficultyFallback(value) {
    switch (value) {
      case 'easy':
        return 'Facile';
      case 'hard':
        return 'Difficile';
      default:
        return 'Moyen';
    }
  }
  function clampBoardSize(size, fallback) {
    const normalized = Number.parseInt(size, 10);
    if (Number.isInteger(normalized) && normalized >= 5 && normalized <= MAX_BOARD_SIZE) {
      return normalized;
    }
    return Number.isInteger(fallback) ? fallback : DEFAULT_BOARD_SETTINGS.defaultSize;
  }

  function generateCandidateMoves(board, player, radius) {
    const size = board.size;
    const candidates = [];
    if (board.moveHistory.length === 0) {
      const center = Math.floor(size / 2);
      return [{ row: center, col: center }];
    }
    const bounds = board.getOccupiedBounds();
    const minRow = Math.max(0, bounds.minRow - radius);
    const maxRow = Math.min(size - 1, bounds.maxRow + radius);
    const minCol = Math.max(0, bounds.minCol - radius);
    const maxCol = Math.min(size - 1, bounds.maxCol + radius);
    for (let row = minRow; row <= maxRow; row += 1) {
      for (let col = minCol; col <= maxCol; col += 1) {
        if (!board.isEmpty(row, col)) {
          continue;
        }
        if (!hasNeighborWithin(board, row, col, radius)) {
          continue;
        }
        candidates.push({ row, col });
      }
    }
    if (candidates.length === 0) {
      for (let row = 0; row < size; row += 1) {
        for (let col = 0; col < size; col += 1) {
          if (board.isEmpty(row, col)) {
            candidates.push({ row, col });
          }
        }
      }
    }
    return candidates;
  }

  function hasNeighborWithin(board, row, col, radius) {
    for (let dr = -radius; dr <= radius; dr += 1) {
      for (let dc = -radius; dc <= radius; dc += 1) {
        if (dr === 0 && dc === 0) {
          continue;
        }
        const r = row + dr;
        const c = col + dc;
        if (!board.isInside(r, c)) {
          continue;
        }
        if (board.getCell(r, c) !== EMPTY) {
          return true;
        }
      }
    }
    return false;
  }

  function scoreMoveHeuristic(board, row, col, player, weights) {
    let score = 0;
    const opponent = invertPlayer(player);
    for (let i = 0; i < DIRECTIONS.length; i += 1) {
      const dir = DIRECTIONS[i];
      const run = estimatePotential(board, row, col, player, dir[0], dir[1]);
      score += run * 12;
      const block = estimatePotential(board, row, col, opponent, dir[0], dir[1]);
      score += block * 8;
    }
    const center = (board.size - 1) / 2;
    const distance = Math.abs(row - center) + Math.abs(col - center);
    score += Math.max(0, weights.centerBonus - distance);
    return score;
  }

  function scoreMoveTactics(board, row, col, player, weights) {
    const move = new Move(row, col, player);
    const nextBoard = board.applyMove(move);
    let score = 0;
    for (let i = 0; i < DIRECTIONS.length; i += 1) {
      const dir = DIRECTIONS[i];
      const info = getRunInfo(nextBoard, row, col, player, dir[0], dir[1]);
      if (!info) {
        continue;
      }
      if (info.length >= 5) {
        score += weights.five;
      } else if (info.length === 4) {
        score += info.openLeft && info.openRight ? weights.openFour : weights.closedFour;
      } else if (info.length === 3) {
        score += info.openLeft && info.openRight ? weights.openThree : weights.closedThree;
      } else if (info.length === 2) {
        score += weights.two;
      }
    }
    return score;
  }

  function estimatePotential(board, row, col, player, dRow, dCol) {
    let total = 1;
    let r = row + dRow;
    let c = col + dCol;
    while (board.isInside(r, c) && board.getCell(r, c) === player) {
      total += 1;
      r += dRow;
      c += dCol;
    }
    r = row - dRow;
    c = col - dCol;
    while (board.isInside(r, c) && board.getCell(r, c) === player) {
      total += 1;
      r -= dRow;
      c -= dCol;
    }
    return total;
  }
  class Move {
    constructor(row, col, player) {
      this.row = row;
      this.col = col;
      this.player = player;
    }
  }

  class Board {
    constructor(size, cells, moveHistory, zobristKey, bounds, occupied) {
      this.size = size;
      this.cells = cells;
      this.moveHistory = moveHistory;
      this.zobristKey = zobristKey;
      this.bounds = bounds;
      this.occupied = occupied;
    }

    static empty(size) {
      const cellCount = size * size;
      const cells = new Array(cellCount).fill(EMPTY);
      const bounds = {
        minRow: size,
        maxRow: -1,
        minCol: size,
        maxCol: -1
      };
      return new Board(size, cells, [], 0, bounds, 0);
    }

    static fromData(size, flatCells, history) {
      const cellCount = size * size;
      if (!Array.isArray(flatCells) || flatCells.length !== cellCount) {
        return null;
      }
      const cells = new Array(cellCount);
      let zobristKey = 0;
      let occupied = 0;
      for (let i = 0; i < cellCount; i += 1) {
        const value = Number(flatCells[i]);
        if (value === PLAYERS.BLACK || value === PLAYERS.WHITE) {
          cells[i] = value;
          zobristKey ^= zobrist.valueAt(Math.floor(i / size), i % size, value);
          occupied += 1;
        } else {
          cells[i] = EMPTY;
        }
      }
      const moveHistory = Array.isArray(history)
        ? history
          .map(item => new Move(item.row, item.col, item.player))
          .filter(move => Number.isInteger(move.row) && Number.isInteger(move.col) && (move.player === PLAYERS.BLACK || move.player === PLAYERS.WHITE))
        : [];
      const bounds = Board.computeBounds(size, cells);
      return new Board(size, cells, moveHistory, zobristKey, bounds, occupied);
    }

    static computeBounds(size, cells) {
      const bounds = {
        minRow: size,
        maxRow: -1,
        minCol: size,
        maxCol: -1
      };
      for (let row = 0; row < size; row += 1) {
        for (let col = 0; col < size; col += 1) {
          const value = cells[row * size + col];
          if (value !== EMPTY) {
            if (row < bounds.minRow) bounds.minRow = row;
            if (row > bounds.maxRow) bounds.maxRow = row;
            if (col < bounds.minCol) bounds.minCol = col;
            if (col > bounds.maxCol) bounds.maxCol = col;
          }
        }
      }
      if (bounds.maxRow === -1) {
        return { minRow: size, maxRow: -1, minCol: size, maxCol: -1 };
      }
      return bounds;
    }

    index(row, col) {
      return row * this.size + col;
    }

    isInside(row, col) {
      return row >= 0 && row < this.size && col >= 0 && col < this.size;
    }

    getCell(row, col) {
      if (!this.isInside(row, col)) {
        return EMPTY;
      }
      return this.cells[this.index(row, col)];
    }

    isEmpty(row, col) {
      return this.getCell(row, col) === EMPTY;
    }

    applyMove(move) {
      if (!this.isInside(move.row, move.col) || !this.isEmpty(move.row, move.col)) {
        return this;
      }
      const index = this.index(move.row, move.col);
      const nextCells = this.cells.slice();
      nextCells[index] = move.player;
      const nextHistory = this.moveHistory.concat(new Move(move.row, move.col, move.player));
      const nextZobrist = this.zobristKey ^ zobrist.valueAt(move.row, move.col, move.player);
      const nextBounds = {
        minRow: Math.min(this.bounds.minRow, move.row),
        maxRow: Math.max(this.bounds.maxRow, move.row),
        minCol: Math.min(this.bounds.minCol, move.col),
        maxCol: Math.max(this.bounds.maxCol, move.col)
      };
      return new Board(this.size, nextCells, nextHistory, nextZobrist, nextBounds, this.occupied + 1);
    }

    rewind() {
      if (this.moveHistory.length === 0) {
        return null;
      }
      const previousHistory = this.moveHistory.slice(0, -1);
      const lastMove = this.moveHistory[this.moveHistory.length - 1];
      const index = this.index(lastMove.row, lastMove.col);
      const nextCells = this.cells.slice();
      nextCells[index] = EMPTY;
      const nextZobrist = this.zobristKey ^ zobrist.valueAt(lastMove.row, lastMove.col, lastMove.player);
      const board = new Board(this.size, nextCells, previousHistory, nextZobrist, Board.computeBounds(this.size, nextCells), this.occupied - 1);
      return {
        board,
        currentPlayer: lastMove.player,
        lastMove: previousHistory.length > 0 ? previousHistory[previousHistory.length - 1] : null
      };
    }

    isFull() {
      return this.occupied >= this.size * this.size;
    }

    getOccupiedBounds() {
      if (this.bounds.maxRow === -1) {
        return {
          minRow: 0,
          maxRow: this.size - 1,
          minCol: 0,
          maxCol: this.size - 1
        };
      }
      return this.bounds;
    }
  }
  function createZobristTable(maxSize) {
    const table = new Array(maxSize);
    for (let row = 0; row < maxSize; row += 1) {
      table[row] = new Array(maxSize);
      for (let col = 0; col < maxSize; col += 1) {
        table[row][col] = {
          [PLAYERS.BLACK]: randomInt32(),
          [PLAYERS.WHITE]: randomInt32()
        };
      }
    }
    return {
      valueAt(row, col, player) {
        const safeRow = Math.max(0, Math.min(maxSize - 1, row));
        const safeCol = Math.max(0, Math.min(maxSize - 1, col));
        return table[safeRow][safeCol][player] || 0;
      }
    };
  }

  function randomInt32() {
    return Math.floor(Math.random() * 0xffffffff);
  }
  function evaluateBoard(board, player, context) {
    const winnerInfo = board.moveHistory.length > 0
      ? determineWinner(board, board.moveHistory[board.moveHistory.length - 1])
      : null;
    if (winnerInfo) {
      if (winnerInfo.player === player) {
        return Number.POSITIVE_INFINITY;
      }
      return Number.NEGATIVE_INFINITY;
    }
    if (board.isFull()) {
      return 0;
    }
    const opponent = invertPlayer(player);
    const weights = context.evaluationWeights || CONFIG.evaluation;
    const ownScore = evaluatePlayer(board, player, weights, context);
    const opponentScore = evaluatePlayer(board, opponent, weights, context);
    return ownScore - opponentScore;
  }

  function evaluatePlayer(board, player, weights, context) {
    let score = 0;
    const seen = new Set();
    const size = board.size;
    const center = (size - 1) / 2;
    for (let row = 0; row < size; row += 1) {
      for (let col = 0; col < size; col += 1) {
        const value = board.getCell(row, col);
        if (value !== player) {
          continue;
        }
        const centerDistance = Math.abs(row - center) + Math.abs(col - center);
        score += Math.max(0, weights.centerBonus - centerDistance);
        for (let i = 0; i < DIRECTIONS.length; i += 1) {
          const dir = DIRECTIONS[i];
          const key = `${row}:${col}:${dir[0]}:${dir[1]}`;
          const prevRow = row - dir[0];
          const prevCol = col - dir[1];
          if (board.isInside(prevRow, prevCol) && board.getCell(prevRow, prevCol) === player) {
            continue;
          }
          if (seen.has(key)) {
            continue;
          }
          seen.add(key);
          const info = getRunInfo(board, row, col, player, dir[0], dir[1]);
          if (!info) {
            continue;
          }
          if (info.length >= 5) {
            score += weights.five;
            continue;
          }
          if (info.length === 4) {
            score += info.openLeft && info.openRight ? weights.openFour : weights.closedFour;
            continue;
          }
          if (info.length === 3) {
            score += info.openLeft && info.openRight ? weights.openThree : weights.closedThree;
            continue;
          }
          if (info.length === 2) {
            score += weights.two;
          }
        }
      }
    }
    if (context.ruleSet === RULE_SET.RENJU && player === PLAYERS.BLACK) {
      const lastMove = board.moveHistory.length > 0 ? board.moveHistory[board.moveHistory.length - 1] : null;
      if (lastMove && lastMove.player === PLAYERS.BLACK) {
        const foul = checkRenjuFoul(board.rewind().board, lastMove);
        if (foul && foul.illegal) {
          score -= weights.five;
        }
      }
    }
    return score;
  }
  function findImmediateWinningMove(board, player, context, candidates) {
    const ruleSet = context.ruleSet || state.ruleSet;
    for (let i = 0; i < candidates.length; i += 1) {
      const pos = candidates[i];
      const move = new Move(pos.row, pos.col, player);
      if (!isLegalMoveForRuleSet(board, move, ruleSet)) {
        continue;
      }
      const nextBoard = board.applyMove(move);
      const winner = determineWinner(nextBoard, move);
      if (winner && winner.player === player) {
        return move;
      }
    }
    return null;
  }

  function findImmediateBlockMove(board, player, context) {
    const ruleSet = context.ruleSet || state.ruleSet;
    const opponent = invertPlayer(player);
    const neighborRadius = context.neighborRadius || CONFIG.board.neighborRadius;
    const opponentCandidates = generateCandidateMoves(board, opponent, neighborRadius);
    const weights = context.evaluationWeights || CONFIG.evaluation;
    let bestMove = null;
    let bestScore = Number.NEGATIVE_INFINITY;
    for (let i = 0; i < opponentCandidates.length; i += 1) {
      const pos = opponentCandidates[i];
      const opponentMove = new Move(pos.row, pos.col, opponent);
      if (!isLegalMoveForRuleSet(board, opponentMove, ruleSet)) {
        continue;
      }
      const nextBoard = board.applyMove(opponentMove);
      const winner = determineWinner(nextBoard, opponentMove);
      if (!winner || winner.player !== opponent) {
        continue;
      }
      const blockingMove = new Move(pos.row, pos.col, player);
      if (!isLegalMoveForRuleSet(board, blockingMove, ruleSet)) {
        continue;
      }
      const score = scoreMoveHeuristic(board, pos.row, pos.col, player, weights);
      if (!bestMove || score > bestScore) {
        bestMove = blockingMove;
        bestScore = score;
      }
    }
    return bestMove;
  }

  const Search = {
    findEasyMove(board, player, context) {
      const neighborRadius = context.neighborRadius || CONFIG.board.neighborRadius;
      const candidates = generateCandidateMoves(board, player, neighborRadius);
      const immediateWin = findImmediateWinningMove(board, player, context, candidates);
      if (immediateWin) {
        const resultingBoard = board.applyMove(immediateWin);
        return { move: immediateWin, value: evaluateBoard(resultingBoard, player, context) };
      }
      const urgentBlock = findImmediateBlockMove(board, player, context);
      if (urgentBlock) {
        const resultingBoard = board.applyMove(urgentBlock);
        return { move: urgentBlock, value: evaluateBoard(resultingBoard, player, context) };
      }
      const weights = context.evaluationWeights || CONFIG.evaluation;
      const maxCandidates = CONFIG.search.easy.maxCandidates;
      const ordering = orderCandidates(
        board,
        player,
        candidates,
        weights,
        maxCandidates,
        { includeTactics: true }
      );
      let bestMove = null;
      let bestScore = Number.NEGATIVE_INFINITY;
      for (let i = 0; i < ordering.length; i += 1) {
        const pos = ordering[i];
        const move = new Move(pos.row, pos.col, player);
        const foul = context.ruleSet === RULE_SET.RENJU && player === PLAYERS.BLACK
          ? checkRenjuFoul(board, move)
          : null;
        if (foul && foul.illegal) {
          continue;
        }
        const nextBoard = board.applyMove(move);
        const score = evaluateBoard(nextBoard, player, context);
        if (score > bestScore || !bestMove) {
          bestScore = score;
          bestMove = move;
        }
      }
      return { move: bestMove, value: bestScore };
    },

    findBestMove(board, player, settings, context) {
      const neighborRadius = context.neighborRadius || CONFIG.board.neighborRadius;
      const candidates = generateCandidateMoves(board, player, neighborRadius);
      if (candidates.length === 0) {
        return { move: null, value: 0 };
      }
      const immediateWin = findImmediateWinningMove(board, player, context, candidates);
      if (immediateWin) {
        return { move: immediateWin, value: Number.POSITIVE_INFINITY, depth: 1 };
      }
      const urgentBlock = findImmediateBlockMove(board, player, context);
      if (urgentBlock) {
        const resultingBoard = board.applyMove(urgentBlock);
        const value = evaluateBoard(resultingBoard, player, context);
        return { move: urgentBlock, value, depth: 1 };
      }
      let bestMove = null;
      let bestValue = Number.NEGATIVE_INFINITY;
      let completedDepth = 0;
      const iterative = settings.iterative !== false;
      const maxDepth = Math.max(1, settings.maxDepth || 2);
      const orderEnabled = context.moveOrdering !== false;
      const maxCandidates = Number.isInteger(settings.maxCandidates) && settings.maxCandidates > 0
        ? settings.maxCandidates
        : null;

      const ordering = orderEnabled
        ? orderCandidates(
          board,
          player,
          candidates,
          context.evaluationWeights || CONFIG.evaluation,
          maxCandidates,
          { includeTactics: true }
        )
        : applyCandidateLimit(candidates, maxCandidates);

      const ctx = {
        ...context,
        deadline: context.deadline,
        timedOut: false,
        transposition: context.transposition || new Map(),
        orderEnabled,
        maxCandidates
      };

      const startDepth = iterative ? 1 : maxDepth;
      for (let depth = startDepth; depth <= maxDepth; depth += 1) {
        const result = negamax(board, player, depth, Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY, ctx, ordering);
        if (ctx.timedOut && iterative) {
          break;
        }
        if (!ctx.timedOut && result.move) {
          bestMove = result.move;
          bestValue = result.value;
          completedDepth = depth;
        }
        if (!iterative) {
          break;
        }
      }

      if (!bestMove) {
        bestMove = ordering[0] ? new Move(ordering[0].row, ordering[0].col, player) : null;
      }
      return { move: bestMove, value: bestValue, depth: completedDepth };
    }
  };

  function negamax(board, player, depth, alpha, beta, context, orderedCandidates) {
    if (context.timedOut) {
      return { move: null, value: 0 };
    }
    if (context.deadline && nowMs() > context.deadline) {
      context.timedOut = true;
      return { move: null, value: 0 };
    }

    const lastMove = board.moveHistory.length > 0 ? board.moveHistory[board.moveHistory.length - 1] : null;
    const winnerInfo = lastMove ? determineWinner(board, lastMove) : null;
    if (winnerInfo) {
      if (winnerInfo.player === player) {
        return { move: null, value: Number.POSITIVE_INFINITY };
      }
      return { move: null, value: Number.NEGATIVE_INFINITY };
    }
    if (depth === 0 || board.isFull()) {
      return { move: null, value: evaluateBoard(board, player, context) };
    }

    const key = board.zobristKey ^ (player === PLAYERS.BLACK ? 0x9e3779b9 : 0x5bd1e995);
    if (context.transposition && context.transposition.has(key)) {
      const entry = context.transposition.get(key);
      if (entry.depth >= depth) {
        return { move: null, value: entry.value };
      }
    }

    const neighborRadius = context.neighborRadius || CONFIG.board.neighborRadius;
    const candidates = orderedCandidates
      || getCandidateMoves(board, player, neighborRadius, context, depth);
    let bestValue = Number.NEGATIVE_INFINITY;
    let bestMove = null;

    for (let i = 0; i < candidates.length; i += 1) {
      const pos = candidates[i];
      const move = new Move(pos.row, pos.col, player);
      if (context.ruleSet === RULE_SET.RENJU && player === PLAYERS.BLACK) {
        const foul = checkRenjuFoul(board, move);
        if (foul && foul.illegal) {
          continue;
        }
      }
      const nextBoard = board.applyMove(move);
      const result = negamax(nextBoard, invertPlayer(player), depth - 1, -beta, -alpha, context, null);
      const value = -result.value;
      if (context.timedOut) {
        return { move: bestMove, value: bestValue };
      }
      if (value > bestValue) {
        bestValue = value;
        bestMove = move;
      }
      alpha = Math.max(alpha, value);
      if (alpha >= beta) {
        break;
      }
    }

    if (context.transposition) {
      context.transposition.set(key, { value: bestValue, depth });
    }

    return { move: bestMove, value: bestValue };
  }

  function getCandidateMoves(board, player, neighborRadius, context, depth) {
    const candidates = generateCandidateMoves(board, player, neighborRadius);
    if (!context || !context.orderEnabled) {
      return applyCandidateLimit(candidates, context ? context.maxCandidates : null);
    }
    const weights = context.evaluationWeights || CONFIG.evaluation;
    const includeTactics = depth <= 2;
    return orderCandidates(board, player, candidates, weights, context.maxCandidates, { includeTactics });
  }

  function applyCandidateLimit(candidates, maxCandidates) {
    if (!Number.isInteger(maxCandidates) || maxCandidates <= 0 || candidates.length <= maxCandidates) {
      return candidates;
    }
    return candidates.slice(0, maxCandidates);
  }

  function orderCandidates(board, player, candidates, weights, maxCandidates, options = {}) {
    const includeTactics = options.includeTactics === true;
    const scored = candidates.map(pos => {
      const baseScore = scoreMoveHeuristic(board, pos.row, pos.col, player, weights);
      const tacticalScore = includeTactics ? scoreMoveTactics(board, pos.row, pos.col, player, weights) : 0;
      return {
        pos,
        score: baseScore + tacticalScore
      };
    });
    scored.sort((a, b) => b.score - a.score);
    const ordered = scored.map(item => item.pos);
    return applyCandidateLimit(ordered, maxCandidates);
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
      Board,
      Move,
      evaluateBoard,
      Search
    };
  }
})();
