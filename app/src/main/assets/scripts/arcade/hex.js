
(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const BOARD_SIZE = 14;
  const DIRECTIONS = [
    [-1, 0],
    [-1, 1],
    [0, -1],
    [0, 1],
    [1, -1],
    [1, 0]
  ];

  const PLAYERS = Object.freeze({
    BLUE: 1,
    RED: -1
  });

  const MODES = Object.freeze({
    SOLO: 'solo',
    DUO: 'duo'
  });

  const DIFFICULTIES = Object.freeze({
    easy: { depth: 1, maxCandidates: 6, thinkDelay: 350 },
    medium: { depth: 2, maxCandidates: 10, thinkDelay: 450 },
    hard: { depth: 3, maxCandidates: 14, thinkDelay: 550 }
  });

  const GACHA_REWARDS = Object.freeze({
    easy: 2,
    medium: 5,
    hard: 10
  });

  const AI_PLAYER = PLAYERS.RED;
  const WIN_SCORE = 100000;
  const AUTOSAVE_GAME_ID = 'hex';

  const state = {
    board: createEmptyBoard(),
    currentPlayer: PLAYERS.BLUE,
    mode: MODES.SOLO,
    difficulty: 'medium',
    gameOver: false,
    awaitingAI: false,
    rewardGranted: false,
    lastRewardCount: null,
    lastMove: null,
    elements: null,
    cells: [],
    aiTimeout: null,
    languageHandlerAttached: false,
    languageHandler: null
  };

  let resizeObserver = null;
  let pendingSizeFrame = null;
  let autosaveTimerId = null;

  onReady(() => {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    buildBoard();
    attachEvents();
    attachLanguageListener();
    initResizeObserver();
    scheduleBoardSizingUpdate();
    if (!restoreAutosave()) {
      resetGame({ skipAutosave: true });
    } else {
      renderBoard();
      updateModeButtons();
      updateDifficultyState();
      if (state.lastRewardCount) {
        updateRewardMessage(state.lastRewardCount);
      } else {
        updateRewardMessage(null);
      }
      if (!state.gameOver) {
        if (state.mode === MODES.SOLO && state.currentPlayer === AI_PLAYER) {
          scheduleAIMove();
        } else if (state.mode === MODES.SOLO) {
          setStatus('scripts.arcade.hex.status.readySolo', 'Hex : affrontez l’IA rouge.', null);
        } else {
          setStatus('scripts.arcade.hex.status.readyDuo', 'Mode 2 joueurs : les bleus commencent.', null);
        }
        updateTurnIndicator();
      }
    }
    window.addEventListener('resize', scheduleBoardSizingUpdate);
  });

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function getElements() {
    const page = document.getElementById('hex');
    const board = document.getElementById('hexBoard');
    const status = document.getElementById('hexStatus');
    const turn = document.getElementById('hexTurn');
    const reward = document.getElementById('hexReward');
    const resetButton = document.getElementById('hexResetButton');
    const difficultySelect = document.getElementById('hexDifficulty');
    const modeButtons = Array.from(
      document.querySelectorAll('[data-role="hexModeButton"]')
    );
    if (!page || !board || !status || !turn || !resetButton || !difficultySelect) {
      return null;
    }
    return {
      page,
      board,
      status,
      turn,
      reward,
      resetButton,
      difficultySelect,
      modeButtons
    };
  }

  function attachEvents() {
    if (!state.elements) {
      return;
    }
    state.elements.board.addEventListener('click', handleBoardClick);
    state.elements.modeButtons.forEach(button => {
      button.addEventListener('click', handleModeButtonClick);
    });
    state.elements.difficultySelect.addEventListener('change', event => {
      const select = event.target;
      const value = select instanceof HTMLSelectElement ? select.value : 'medium';
      setDifficulty(value);
    });
    state.elements.resetButton.addEventListener('click', () => {
      resetGame();
    });
  }

  function attachLanguageListener() {
    if (state.languageHandlerAttached) {
      return;
    }
    const handler = () => {
      renderBoard();
      updateTurnIndicator();
      if (state.gameOver) {
        announceWinnerStatus();
      } else if (state.mode === MODES.SOLO && state.awaitingAI) {
        setStatus('scripts.arcade.hex.status.aiThinking', 'L’IA réfléchit…', null);
      } else if (state.mode === MODES.SOLO) {
        setStatus('scripts.arcade.hex.status.readySolo', 'Hex : affrontez l’IA rouge.', null);
      } else {
        setStatus('scripts.arcade.hex.status.readyDuo', 'Mode 2 joueurs : les bleus commencent.', null);
      }
      updateRewardMessage(state.lastRewardCount);
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandlerAttached = true;
    state.languageHandler = handler;
  }

  function handleBoardClick(event) {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const cell = target.closest('button.hex__cell');
    if (!cell || typeof cell.dataset.row !== 'string' || typeof cell.dataset.col !== 'string') {
      return;
    }
    const row = Number.parseInt(cell.dataset.row, 10);
    const col = Number.parseInt(cell.dataset.col, 10);
    if (!Number.isFinite(row) || !Number.isFinite(col)) {
      return;
    }
    attemptHumanMove(row, col);
  }

  function handleModeButtonClick(event) {
    const button = event.currentTarget;
    if (!(button instanceof HTMLElement)) {
      return;
    }
    const mode = button.dataset.mode === MODES.DUO ? MODES.DUO : MODES.SOLO;
    setMode(mode);
  }

  function buildBoard() {
    if (!state.elements) {
      return;
    }
    const { board } = state.elements;
    board.innerHTML = '';
    const cells = new Array(BOARD_SIZE);
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      const rowElement = document.createElement('div');
      rowElement.className = 'hex__row';
      rowElement.dataset.row = String(row);
      rowElement.style.setProperty('--hex-row-index', String(row));
      const rowCells = new Array(BOARD_SIZE);
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'hex__cell';
        button.dataset.row = String(row);
        button.dataset.col = String(col);
        button.setAttribute('role', 'gridcell');
        button.setAttribute('aria-rowindex', String(row + 1));
        button.setAttribute('aria-colindex', String(col + 1));
        rowElement.appendChild(button);
        rowCells[col] = button;
      }
      board.appendChild(rowElement);
      cells[row] = rowCells;
    }
    state.cells = cells;
  }

  function renderBoard() {
    if (!state.cells.length) {
      return;
    }
    const disableForAITurn = state.mode === MODES.SOLO && state.awaitingAI;
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        updateCell(row, col, disableForAITurn);
      }
    }
  }

  function updateCell(row, col, disableForAITurn) {
    const cell = state.cells[row][col];
    if (!cell) {
      return;
    }
    const value = state.board[row][col];
    const lastMove = state.lastMove;
    const isLastMove = lastMove && lastMove.row === row && lastMove.col === col;
    if (value === PLAYERS.BLUE) {
      cell.dataset.owner = 'blue';
    } else if (value === PLAYERS.RED) {
      cell.dataset.owner = 'red';
    } else {
      delete cell.dataset.owner;
    }
    if (isLastMove) {
      cell.dataset.lastMove = 'true';
    } else {
      delete cell.dataset.lastMove;
    }
    const occupied = value !== 0;
    cell.disabled = state.gameOver || occupied || disableForAITurn;
    cell.setAttribute('aria-label', buildCellLabel(row, col, value));
  }

  function buildCellLabel(row, col, value) {
    const params = {
      row: row + 1,
      column: col + 1,
      content: getCellContentLabel(value)
    };
    return translate(
      'scripts.arcade.hex.cell',
      'Case {row}, colonne {column} : {content}',
      params
    );
  }

  function getCellContentLabel(value) {
    let key;
    if (value === PLAYERS.BLUE) {
      key = 'blue';
    } else if (value === PLAYERS.RED) {
      key = 'red';
    } else {
      key = 'empty';
    }
    return translate(
      `scripts.arcade.hex.cellStates.${key}`,
      key === 'blue' ? 'pion bleu' : key === 'red' ? 'pion rouge' : 'vide',
      null
    );
  }

  function attemptHumanMove(row, col) {
    if (state.gameOver || (state.mode === MODES.SOLO && state.awaitingAI)) {
      return;
    }
    if (state.mode === MODES.SOLO && state.currentPlayer === AI_PLAYER) {
      return;
    }
    if (!isInsideBoard(row, col) || state.board[row][col] !== 0) {
      setStatus(
        'scripts.arcade.hex.status.invalid',
        'Cette case est déjà occupée.',
        null
      );
      return;
    }
    applyMove(row, col, state.currentPlayer);
  }

  function applyMove(row, col, player) {
    state.board[row][col] = player;
    state.lastMove = { row, col };
    renderBoard();
    if (checkWinner(state.board, player)) {
      handleVictory(player);
      return;
    }
    if (isBoardFull()) {
      handleDraw();
      return;
    }
    state.currentPlayer = -player;
    updateTurnIndicator();
    if (state.mode === MODES.SOLO && state.currentPlayer === AI_PLAYER) {
      scheduleAIMove();
    } else {
      updateStatusForCurrentPlayer();
    }
    scheduleAutosave();
  }

  function handleVictory(player) {
    state.gameOver = true;
    clearAIMoveTimer();
    announceWinnerStatus(player);
    if (state.mode === MODES.SOLO && player !== AI_PLAYER && !state.rewardGranted) {
      state.rewardGranted = true;
      awardVictoryReward();
    } else if (player === AI_PLAYER) {
      updateRewardMessage(null);
    }
    scheduleAutosave();
  }

  function handleDraw() {
    state.gameOver = true;
    clearAIMoveTimer();
    setStatus(
      'scripts.arcade.hex.status.draw',
      'Plateau complet : égalité.',
      null
    );
    updateRewardMessage(null);
    scheduleAutosave();
  }

  function announceWinnerStatus(explicitWinner) {
    const winner = typeof explicitWinner === 'number' ? explicitWinner : detectWinner(state.board);
    if (winner === PLAYERS.BLUE) {
      if (state.mode === MODES.SOLO) {
        setStatus(
          'scripts.arcade.hex.status.playerWin',
          'Victoire ! Vous reliez les bords bleus.',
          null
        );
      } else {
        setStatus(
          'scripts.arcade.hex.status.duoWin',
          'Victoire des {player} !',
          { player: getPlayerLabel(PLAYERS.BLUE, { article: false }) }
        );
      }
    } else if (winner === PLAYERS.RED) {
      if (state.mode === MODES.SOLO) {
        setStatus(
          'scripts.arcade.hex.status.aiWin',
          'Défaite : l’IA relie les bords rouges.',
          null
        );
      } else {
        setStatus(
          'scripts.arcade.hex.status.duoWin',
          'Victoire des {player} !',
          { player: getPlayerLabel(PLAYERS.RED, { article: false }) }
        );
      }
    } else {
      setStatus(
        'scripts.arcade.hex.status.draw',
        'Plateau complet : égalité.',
        null
      );
    }
  }

  function awardVictoryReward() {
    const tickets = Number(GACHA_REWARDS[state.difficulty] || 0);
    if (tickets <= 0) {
      updateRewardMessage(null);
      return;
    }
    const awardFn = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardFn !== 'function') {
      updateRewardMessage(tickets);
      state.lastRewardCount = tickets;
      return;
    }
    let gained = 0;
    try {
      const result = awardFn(tickets, { unlockTicketStar: true });
      gained = Number.isFinite(result) && result > 0 ? Math.floor(result) : tickets;
    } catch (error) {
      console.warn('Hex: unable to grant gacha tickets', error);
      gained = tickets;
    }
    updateRewardMessage(gained);
    state.lastRewardCount = gained;
  }

  function updateRewardMessage(count) {
    if (!state.elements || !state.elements.reward) {
      return;
    }
    state.lastRewardCount = Number.isFinite(count) && count > 0 ? Math.floor(count) : null;
    if (!state.lastRewardCount) {
      state.elements.reward.hidden = true;
      state.elements.reward.textContent = translate(
        'index.sections.hex.reward.placeholder',
        'Tickets gacha gagnés : 0',
        null
      );
      return;
    }
    const text = translate(
      'scripts.arcade.hex.reward.gained',
      'Tickets gacha gagnés : {count}',
      { count: state.lastRewardCount }
    );
    state.elements.reward.textContent = text;
    state.elements.reward.hidden = false;
  }

  function updateStatusForCurrentPlayer() {
    if (state.gameOver) {
      return;
    }
    setStatus(
      'scripts.arcade.hex.status.turn',
      'Tour des {player}.',
      { player: getPlayerLabel(state.currentPlayer, { article: false }) }
    );
  }

  function updateTurnIndicator() {
    if (!state.elements || !state.elements.turn) {
      return;
    }
    if (state.gameOver) {
      state.elements.turn.textContent = '';
      return;
    }
    state.elements.turn.textContent = translate(
      'scripts.arcade.hex.turnIndicator',
      'Prochain joueur : {player}',
      { player: getPlayerLabel(state.currentPlayer, { article: true }) }
    );
  }

  function setMode(mode) {
    const normalized = mode === MODES.DUO ? MODES.DUO : MODES.SOLO;
    if (state.mode === normalized) {
      return;
    }
    state.mode = normalized;
    updateModeButtons();
    updateDifficultyState();
    resetGame();
    scheduleAutosave();
  }

  function setDifficulty(difficulty) {
    const normalized = DIFFICULTIES[difficulty] ? difficulty : 'medium';
    state.difficulty = normalized;
    if (state.elements && state.elements.difficultySelect.value !== normalized) {
      state.elements.difficultySelect.value = normalized;
    }
    scheduleAutosave();
  }

  function updateModeButtons() {
    if (!state.elements) {
      return;
    }
    state.elements.modeButtons.forEach(button => {
      const isActive = button.dataset.mode === state.mode;
      button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    });
  }

  function updateDifficultyState() {
    if (!state.elements) {
      return;
    }
    const disabled = state.mode === MODES.DUO;
    state.elements.difficultySelect.disabled = disabled;
  }

  function resetGame(options = {}) {
    clearAIMoveTimer();
    state.board = createEmptyBoard();
    state.currentPlayer = PLAYERS.BLUE;
    state.gameOver = false;
    state.awaitingAI = false;
    state.rewardGranted = false;
    state.lastRewardCount = null;
    state.lastMove = null;
    renderBoard();
    updateTurnIndicator();
    updateRewardMessage(null);
    if (state.mode === MODES.SOLO) {
      setStatus('scripts.arcade.hex.status.readySolo', 'Hex : affrontez l’IA rouge.', null);
    } else {
      setStatus('scripts.arcade.hex.status.readyDuo', 'Mode 2 joueurs : les bleus commencent.', null);
    }
    if (!options.skipAutosave) {
      scheduleAutosave();
    }
    scheduleBoardSizingUpdate();
  }

  function scheduleAIMove() {
    clearAIMoveTimer();
    state.awaitingAI = true;
    renderBoard();
    updateTurnIndicator();
    setStatus('scripts.arcade.hex.status.aiThinking', 'L’IA réfléchit…', null);
    const config = getDifficultyConfig();
    state.aiTimeout = window.setTimeout(() => {
      state.aiTimeout = null;
      performAIMove();
    }, config.thinkDelay);
  }

  function performAIMove() {
    state.awaitingAI = false;
    const move = chooseAIMove();
    if (!move) {
      handleDraw();
      return;
    }
    applyMove(move.row, move.col, AI_PLAYER);
  }

  function chooseAIMove() {
    const config = getDifficultyConfig();
    const candidates = getCandidateMoves(state.board, AI_PLAYER);
    if (!candidates.length) {
      return null;
    }
    let bestScore = Number.NEGATIVE_INFINITY;
    let bestMove = candidates[0];
    const limit = Math.min(candidates.length, Math.max(1, config.maxCandidates));
    for (let i = 0; i < limit; i += 1) {
      const candidate = candidates[i];
      state.board[candidate.row][candidate.col] = AI_PLAYER;
      const score = -negamax(
        state.board,
        PLAYERS.BLUE,
        config.depth - 1,
        Number.NEGATIVE_INFINITY,
        Number.POSITIVE_INFINITY,
        config,
        config.depth
      );
      state.board[candidate.row][candidate.col] = 0;
      if (score > bestScore) {
        bestScore = score;
        bestMove = candidate;
      }
    }
    return bestMove;
  }

  function negamax(board, player, depth, alpha, beta, config, maxDepth) {
    const winner = detectWinner(board);
    if (winner === player) {
      return WIN_SCORE - (maxDepth - depth);
    }
    if (winner === -player) {
      return -WIN_SCORE + (maxDepth - depth);
    }
    if (depth <= 0 || isBoardFull()) {
      return evaluateBoard(board, player);
    }
    const moves = getCandidateMoves(board, player);
    if (!moves.length) {
      return evaluateBoard(board, player);
    }
    const remainingDepth = maxDepth - depth;
    const dynamicLimit = Math.min(
      moves.length,
      Math.max(4, config.maxCandidates - remainingDepth * 2)
    );
    let value = Number.NEGATIVE_INFINITY;
    for (let i = 0; i < dynamicLimit; i += 1) {
      const move = moves[i];
      board[move.row][move.col] = player;
      const score = -negamax(board, -player, depth - 1, -beta, -alpha, config, maxDepth);
      board[move.row][move.col] = 0;
      if (score > value) {
        value = score;
      }
      if (value > alpha) {
        alpha = value;
      }
      if (alpha >= beta) {
        break;
      }
    }
    return value;
  }

  function evaluateBoard(board, player) {
    const winner = detectWinner(board);
    if (winner === player) {
      return WIN_SCORE;
    }
    if (winner === -player) {
      return -WIN_SCORE;
    }
    const myDistance = estimateConnectionDistance(board, player);
    const opponentDistance = estimateConnectionDistance(board, -player);
    return opponentDistance - myDistance;
  }

  function estimateConnectionDistance(board, player) {
    const visited = new Array(BOARD_SIZE * BOARD_SIZE).fill(false);
    const distances = new Array(BOARD_SIZE * BOARD_SIZE).fill(Number.POSITIVE_INFINITY);
    const queue = [];

    const pushNode = (index, cost) => {
      if (cost < distances[index]) {
        distances[index] = cost;
        queue.push({ index, cost });
      }
    };

    if (player === PLAYERS.BLUE) {
      for (let row = 0; row < BOARD_SIZE; row += 1) {
        const value = board[row][0];
        if (value === -player) {
          continue;
        }
        const index = toIndex(row, 0);
        const cost = value === player ? 0 : 1;
        pushNode(index, cost);
      }
    } else {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const value = board[0][col];
        if (value === -player) {
          continue;
        }
        const index = toIndex(0, col);
        const cost = value === player ? 0 : 1;
        pushNode(index, cost);
      }
    }

    let best = Number.POSITIVE_INFINITY;
    while (queue.length > 0) {
      queue.sort((a, b) => a.cost - b.cost);
      const current = queue.shift();
      if (!current) {
        break;
      }
      if (visited[current.index] || current.cost >= best) {
        continue;
      }
      visited[current.index] = true;
      const row = Math.floor(current.index / BOARD_SIZE);
      const col = current.index % BOARD_SIZE;
      if ((player === PLAYERS.BLUE && col === BOARD_SIZE - 1) || (player === PLAYERS.RED && row === BOARD_SIZE - 1)) {
        best = Math.min(best, current.cost);
        continue;
      }
      const neighbors = getNeighbors(row, col);
      for (let i = 0; i < neighbors.length; i += 1) {
        const [nr, nc] = neighbors[i];
        const value = board[nr][nc];
        if (value === -player) {
          continue;
        }
        const neighborIndex = toIndex(nr, nc);
        const additionalCost = value === player ? 0 : 1;
        const nextCost = current.cost + additionalCost;
        if (nextCost >= distances[neighborIndex]) {
          continue;
        }
        pushNode(neighborIndex, nextCost);
      }
    }
    if (!Number.isFinite(best)) {
      return BOARD_SIZE * 2;
    }
    return best;
  }

  function detectWinner(board) {
    if (checkWinner(board, PLAYERS.BLUE)) {
      return PLAYERS.BLUE;
    }
    if (checkWinner(board, PLAYERS.RED)) {
      return PLAYERS.RED;
    }
    return 0;
  }

  function checkWinner(board, player) {
    const visited = new Array(BOARD_SIZE * BOARD_SIZE).fill(false);
    const stack = [];
    if (player === PLAYERS.BLUE) {
      for (let row = 0; row < BOARD_SIZE; row += 1) {
        if (board[row][0] === player) {
          stack.push(toIndex(row, 0));
        }
      }
    } else {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        if (board[0][col] === player) {
          stack.push(toIndex(0, col));
        }
      }
    }
    while (stack.length > 0) {
      const index = stack.pop();
      if (visited[index]) {
        continue;
      }
      visited[index] = true;
      const row = Math.floor(index / BOARD_SIZE);
      const col = index % BOARD_SIZE;
      if (player === PLAYERS.BLUE && col === BOARD_SIZE - 1) {
        return true;
      }
      if (player === PLAYERS.RED && row === BOARD_SIZE - 1) {
        return true;
      }
      const neighbors = getNeighbors(row, col);
      for (let i = 0; i < neighbors.length; i += 1) {
        const [nr, nc] = neighbors[i];
        if (board[nr][nc] === player && !visited[toIndex(nr, nc)]) {
          stack.push(toIndex(nr, nc));
        }
      }
    }
    return false;
  }

  function getCandidateMoves(board, player) {
    const candidates = new Map();
    let hasStone = false;
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        if (board[row][col] === 0) {
          continue;
        }
        hasStone = true;
        const neighbors = getNeighbors(row, col);
        for (let i = 0; i < neighbors.length; i += 1) {
          const [nr, nc] = neighbors[i];
          if (board[nr][nc] !== 0) {
            continue;
          }
          const key = `${nr},${nc}`;
          if (!candidates.has(key)) {
            candidates.set(key, { row: nr, col: nc });
          }
        }
      }
    }
    if (!hasStone) {
      const mid = Math.floor(BOARD_SIZE / 2);
      return [
        { row: mid, col: mid },
        { row: mid - 1, col: mid },
        { row: mid, col: mid - 1 },
        { row: mid - 1, col: mid - 1 }
      ].filter(move => isInsideBoard(move.row, move.col));
    }
    const result = Array.from(candidates.values());
    for (let i = 0; i < result.length; i += 1) {
      const move = result[i];
      move.score = evaluatePlacementHeuristic(board, move.row, move.col, player);
    }
    result.sort((a, b) => {
      if (b.score !== a.score) {
        return b.score - a.score;
      }
      const center = (BOARD_SIZE - 1) / 2;
      const aDist = Math.abs(a.row - center) + Math.abs(a.col - center);
      const bDist = Math.abs(b.row - center) + Math.abs(b.col - center);
      return aDist - bDist;
    });
    return result;
  }

  function evaluatePlacementHeuristic(board, row, col, player) {
    board[row][col] = player;
    const myDistance = estimateConnectionDistance(board, player);
    const opponentDistance = estimateConnectionDistance(board, -player);
    board[row][col] = 0;
    return opponentDistance - myDistance;
  }

  function getNeighbors(row, col) {
    const result = [];
    for (let i = 0; i < DIRECTIONS.length; i += 1) {
      const [dr, dc] = DIRECTIONS[i];
      const nr = row + dr;
      const nc = col + dc;
      if (isInsideBoard(nr, nc)) {
        result.push([nr, nc]);
      }
    }
    return result;
  }

  function isInsideBoard(row, col) {
    return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
  }

  function isBoardFull() {
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        if (state.board[row][col] === 0) {
          return false;
        }
      }
    }
    return true;
  }

  function toIndex(row, col) {
    return row * BOARD_SIZE + col;
  }

  function getPlayerLabel(player, options = {}) {
    const useArticle = options.article === true;
    if (state.mode === MODES.SOLO) {
      if (player === AI_PLAYER) {
        return translate(
          'scripts.arcade.hex.players.ai',
          useArticle ? 'l’IA rouge' : 'IA rouge',
          null
        );
      }
      return translate(
        'scripts.arcade.hex.players.human',
        useArticle ? 'le joueur bleu' : 'joueur bleu',
        null
      );
    }
    const key = player === PLAYERS.BLUE ? 'blue' : 'red';
    const fallback = player === PLAYERS.BLUE
      ? useArticle ? 'les bleus' : 'bleus'
      : useArticle ? 'les rouges' : 'rouges';
    return translate(`scripts.arcade.hex.players.${key}`, fallback, null);
  }

  function setStatus(key, fallback, params) {
    if (!state.elements || !state.elements.status) {
      return;
    }
    state.elements.status.textContent = translate(key, fallback, params);
  }

  function clearAIMoveTimer() {
    if (state.aiTimeout != null) {
      window.clearTimeout(state.aiTimeout);
      state.aiTimeout = null;
    }
  }

  function getDifficultyConfig() {
    return DIFFICULTIES[state.difficulty] || DIFFICULTIES.medium;
  }

  function createEmptyBoard() {
    return Array.from({ length: BOARD_SIZE }, () => new Array(BOARD_SIZE).fill(0));
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
        console.warn('Hex translation error for', key, error);
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
    return template.replace(/\{\s*(\w+)\s*\}/g, (match, key) => {
      if (Object.prototype.hasOwnProperty.call(params, key)) {
        return params[key];
      }
      return match;
    });
  }

  function scheduleBoardSizingUpdate() {
    if (pendingSizeFrame != null) {
      return;
    }
    pendingSizeFrame = window.requestAnimationFrame(() => {
      pendingSizeFrame = null;
      updateBoardSizing();
    });
  }

  function updateBoardSizing() {
    if (!state.elements) {
      return;
    }
    const { page, board } = state.elements;
    if (!page || !board) {
      return;
    }
    const computed = window.getComputedStyle(board);
    const gap = Number.parseFloat(computed.getPropertyValue('--hex-cell-gap')) || 0;
    const width = board.clientWidth;
    if (!Number.isFinite(width) || width <= 0) {
      return;
    }
    const denominator = BOARD_SIZE + (BOARD_SIZE - 1) * 0.5;
    const gapContribution = (BOARD_SIZE - 1) * gap;
    const cellSize = Math.max(14, (width - gapContribution) / denominator);
    page.style.setProperty('--hex-cell-size', `${cellSize}px`);
  }

  function initResizeObserver() {
    if (typeof ResizeObserver !== 'function' || !state.elements) {
      return;
    }
    if (resizeObserver) {
      return;
    }
    resizeObserver = new ResizeObserver(() => {
      scheduleBoardSizingUpdate();
    });
    resizeObserver.observe(state.elements.board);
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
    if (autosaveTimerId != null) {
      window.clearTimeout(autosaveTimerId);
    }
    autosaveTimerId = window.setTimeout(() => {
      autosaveTimerId = null;
      persistAutosave();
    }, 150);
  }

  function persistAutosave() {
    const api = getAutosaveApi();
    if (!api || typeof api.set !== 'function') {
      return;
    }
    const payload = buildAutosavePayload();
    try {
      api.set(AUTOSAVE_GAME_ID, payload);
    } catch (error) {
      console.warn('Hex: unable to persist autosave', error);
    }
  }

  function buildAutosavePayload() {
    return {
      board: state.board.map(row => row.slice()),
      currentPlayer: state.currentPlayer,
      mode: state.mode,
      difficulty: state.difficulty,
      gameOver: state.gameOver,
      rewardGranted: state.rewardGranted,
      lastMove: state.lastMove ? { ...state.lastMove } : null,
      lastRewardCount: state.lastRewardCount,
      awaitingAI: state.awaitingAI
    };
  }

  function restoreAutosave() {
    const api = getAutosaveApi();
    if (!api || typeof api.get !== 'function') {
      return false;
    }
    const payload = api.get(AUTOSAVE_GAME_ID);
    if (!payload || typeof payload !== 'object') {
      return false;
    }
    const board = Array.isArray(payload.board) ? payload.board : null;
    if (!board || board.length !== BOARD_SIZE) {
      return false;
    }
    const restored = createEmptyBoard();
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      const sourceRow = Array.isArray(board[row]) ? board[row] : [];
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const value = Number(sourceRow[col] || 0);
        restored[row][col] = value === PLAYERS.BLUE ? PLAYERS.BLUE : value === PLAYERS.RED ? PLAYERS.RED : 0;
      }
    }
    state.board = restored;
    state.mode = payload.mode === MODES.DUO ? MODES.DUO : MODES.SOLO;
    state.difficulty = DIFFICULTIES[payload.difficulty] ? payload.difficulty : 'medium';
    state.currentPlayer = payload.currentPlayer === PLAYERS.RED ? PLAYERS.RED : PLAYERS.BLUE;
    state.gameOver = Boolean(payload.gameOver);
    state.rewardGranted = Boolean(payload.rewardGranted);
    state.lastRewardCount = Number.isFinite(payload.lastRewardCount) && payload.lastRewardCount > 0
      ? Math.floor(payload.lastRewardCount)
      : null;
    if (payload.lastMove && Number.isInteger(payload.lastMove.row) && Number.isInteger(payload.lastMove.col)) {
      state.lastMove = {
        row: Math.max(0, Math.min(BOARD_SIZE - 1, payload.lastMove.row)),
        col: Math.max(0, Math.min(BOARD_SIZE - 1, payload.lastMove.col))
      };
    } else {
      state.lastMove = null;
    }
    state.awaitingAI = false;
    if (state.elements) {
      state.elements.difficultySelect.value = state.difficulty;
    }
    renderBoard();
    updateModeButtons();
    updateDifficultyState();
    updateTurnIndicator();
    if (state.gameOver) {
      announceWinnerStatus();
    } else if (state.mode === MODES.SOLO && state.currentPlayer === AI_PLAYER) {
      scheduleAIMove();
    } else {
      updateStatusForCurrentPlayer();
    }
    scheduleBoardSizingUpdate();
    return true;
  }

  if (typeof window !== 'undefined') {
    const existingApi = typeof window.hexArcade === 'object' && window.hexArcade
      ? window.hexArcade
      : {};
    const previousEnter = typeof existingApi.onEnter === 'function' ? existingApi.onEnter : null;
    const previousLeave = typeof existingApi.onLeave === 'function' ? existingApi.onLeave : null;
    existingApi.onEnter = () => {
      if (typeof previousEnter === 'function') {
        try {
          previousEnter();
        } catch (error) {
          // Ignore previous handler errors.
        }
      }
      scheduleBoardSizingUpdate();
    };
    existingApi.onLeave = () => {
      if (typeof previousLeave === 'function') {
        try {
          previousLeave();
        } catch (error) {
          // Ignore previous handler errors.
        }
      }
      clearAIMoveTimer();
    };
    window.hexArcade = existingApi;
  }
})();
