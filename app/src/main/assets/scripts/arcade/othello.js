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

  const AI_PLAYER = PLAYERS.BLACK;
  const HUMAN_PLAYER = PLAYERS.WHITE;
  const AI_MOVE_DELAY = 500;

  const state = {
    board: createEmptyBoard(),
    currentPlayer: PLAYERS.BLACK,
    validMoves: new Map(),
    passes: 0,
    elements: null,
    cells: [],
    languageHandlerAttached: false,
    languageHandler: null,
    lastStatus: null,
    aiTimeout: null
  };

  onReady(() => {
    const elements = getElements();
    if (!elements) {
      return;
    }
    state.elements = elements;
    buildBoard();
    attachEvents();
    resetGame();
    attachLanguageListener();
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
    if (!board || !resetButton || !status || !score) {
      return null;
    }
    return { board, resetButton, status, score };
  }

  function attachEvents() {
    state.elements.board.addEventListener('click', handleBoardClick);
    state.elements.resetButton.addEventListener('click', resetGame);
  }

  function attachLanguageListener() {
    if (state.languageHandlerAttached) {
      return;
    }
    const handler = () => {
      renderBoard();
      updateScore();
      refreshStatus();
    };
    window.addEventListener('i18n:languagechange', handler);
    state.languageHandlerAttached = true;
    state.languageHandler = handler;
  }

  function buildBoard() {
    const cells = new Array(BOARD_SIZE);
    state.elements.board.innerHTML = '';
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
        state.elements.board.appendChild(button);
        cells[row][col] = button;
      }
    }
    state.cells = cells;
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
    if (state.currentPlayer !== HUMAN_PLAYER) {
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
    state.currentPlayer = PLAYERS.BLACK;
    state.passes = 0;
    prepareTurn();
  }

  function prepareTurn() {
    clearAIMoveTimer();
    state.validMoves = computeValidMoves(state.currentPlayer);
    renderBoard();
    if (state.validMoves.size > 0) {
      beginTurn();
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
  }

  function endGame() {
    clearAIMoveTimer();
    const score = getScore();
    const resultKey = score.black > score.white
      ? 'black'
      : score.white > score.black
        ? 'white'
        : 'draw';
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
    state.validMoves = new Map();
    renderBoard();
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
    const isHumanTurn = state.currentPlayer === HUMAN_PLAYER && state.validMoves.size > 0;
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

  function beginTurn() {
    if (state.currentPlayer === HUMAN_PLAYER) {
      setStatus(
        'scripts.arcade.othello.status.humanTurn',
        'À vous de jouer (blancs).',
        null
      );
      return;
    }
    setStatus(
      'scripts.arcade.othello.status.aiTurn',
      'L’IA (noirs) réfléchit…',
      null
    );
    scheduleAIMove();
  }

  function scheduleAIMove() {
    if (typeof window === 'undefined') {
      return;
    }
    state.aiTimeout = window.setTimeout(() => {
      state.aiTimeout = null;
      if (state.currentPlayer !== AI_PLAYER) {
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

  function translate(key, fallback, params) {
    const translator = typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
      ? window.i18n.t.bind(window.i18n)
      : typeof window !== 'undefined' && typeof window.t === 'function'
        ? window.t
        : null;
    if (translator) {
      try {
        const result = translator(key, params || undefined);
        if (typeof result === 'string' && result.trim()) {
          return formatString(result, params);
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
})();
