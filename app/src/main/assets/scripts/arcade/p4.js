(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const SECTION = document.getElementById('p4');
  if (!SECTION) {
    return;
  }

  const CONFIG_PATH = 'config/arcade/p4.json';
  const PLAYER_ONE = 1;
  const PLAYER_TWO = 2;
  const OBSTACLE = 3;
  const EMPTY = 0;
  const WINDOW_LENGTH = 4;

  const DEFAULT_CONFIG = Object.freeze({
    rows: 7,
    columns: 9,
    obstacles: Object.freeze({
      enabledByDefault: true,
      count: 5,
      minRow: 1,
      maxRow: 3
    }),
    ai: Object.freeze({
      searchDepth: 4,
      centerWeight: 3,
      threeWeight: 120,
      twoWeight: 35,
      blockThreeWeight: 140,
      victoryScore: 100000,
      defeatScore: -100000
    }),
    rewards: Object.freeze({
      humanVictoryTickets: 2
    })
  });

  const STATUS_FALLBACKS = Object.freeze({
    ready: 'Choisissez un mode et lancez la partie.',
    turnPlayer1: 'Au tour de Joueur 1.',
    turnPlayer2: 'Au tour de Joueur 2.',
    turnAi: 'L’IA réfléchit…',
    columnFull: 'Cette colonne est pleine. Choisissez-en une autre.',
    winPlayer1: 'Victoire de Joueur 1 !',
    winPlayer2: 'Victoire de Joueur 2 !',
    winAi: 'Victoire de l’IA.',
    winHumanAi: 'Victoire ! Vous remportez {tickets} tickets gacha.',
    draw: 'Match nul : la grille est pleine.'
  });

  const TURN_FALLBACKS = Object.freeze({
    player1: 'Joueur 1 commence.',
    player2: 'Joueur 2 joue.',
    ai: 'Tour de l’IA.',
    victoryPlayer1: 'Victoire de Joueur 1.',
    victoryPlayer2: 'Victoire de Joueur 2.',
    victoryAi: 'Victoire de l’IA.',
    draw: 'Égalité.'
  });

  const REWARD_FALLBACKS = Object.freeze({
    info: 'Victoire contre l’IA : +{tickets} tickets gacha.',
    claimed: 'Victoire ! Vous remportez {tickets} tickets gacha.'
  });

  const COLUMN_LABEL_FALLBACK = 'Jouer dans la colonne {column}';
  const BOARD_ARIA_FALLBACK = 'Plateau de P4';
  const COLUMN_CONTROLS_ARIA_FALLBACK = 'Choisir une colonne pour lâcher un jeton';

  const numberFormatter = typeof Intl !== 'undefined' && typeof Intl.NumberFormat === 'function'
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;

  const state = {
    config: DEFAULT_CONFIG,
    aiSettings: DEFAULT_CONFIG.ai,
    rows: DEFAULT_CONFIG.rows,
    columns: DEFAULT_CONFIG.columns,
    board: [],
    cellElements: [],
    columnButtons: [],
    obstaclesEnabled: Boolean(DEFAULT_CONFIG.obstacles.enabledByDefault),
    obstacles: [],
    currentPlayer: PLAYER_ONE,
    mode: 'ai',
    moves: 0,
    maxMoves: DEFAULT_CONFIG.rows * DEFAULT_CONFIG.columns,
    lastMove: null,
    winningSequence: null,
    winner: null,
    gameOver: false,
    aiThinking: false,
    aiTimeoutId: null,
    rewardClaimed: false,
    claimedTickets: null,
    ticketReward: DEFAULT_CONFIG.rewards.humanVictoryTickets,
    status: {
      key: 'index.sections.p4.status.ready',
      fallback: STATUS_FALLBACKS.ready,
      params: null
    },
    root: SECTION.querySelector('[data-p4-root]'),
    elements: null,
    languageHandlerAttached: false
  };

  if (!state.root) {
    return;
  }

  state.elements = {
    board: state.root.querySelector('#p4Board'),
    status: state.root.querySelector('#p4Status'),
    modeSelect: state.root.querySelector('#p4ModeSelect'),
    obstacleSelect: state.root.querySelector('#p4ObstacleSelect'),
    newGameButton: state.root.querySelector('#p4NewGameButton'),
    turnIndicator: state.root.querySelector('#p4TurnIndicator'),
    ticketInfo: state.root.querySelector('#p4TicketInfo'),
    columnControls: state.root.querySelector('.p4__column-controls')
  };

  if (!state.elements.board || !state.elements.status || !state.elements.modeSelect || !state.elements.obstacleSelect
    || !state.elements.newGameButton || !state.elements.turnIndicator || !state.elements.ticketInfo) {
    return;
  }

  state.mode = state.elements.modeSelect.value === 'local' ? 'local' : 'ai';
  state.obstaclesEnabled = state.elements.obstacleSelect.value !== 'disabled';

  buildBoardElements();
  attachEventListeners();
  startNewGame(false);
  loadConfig();
  attachLanguageHandler();

  function translate(key, fallback, params) {
    const hasKey = typeof key === 'string' && key.length > 0;
    let translator = null;
    if (typeof window !== 'undefined') {
      if (window.i18n && typeof window.i18n.t === 'function') {
        translator = window.i18n.t.bind(window.i18n);
      } else if (typeof window.t === 'function') {
        translator = window.t.bind(window);
      }
    }

    if (hasKey && translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string') {
          const trimmed = result.trim();
          if (trimmed && trimmed !== key) {
            return trimmed;
          }
        }
      } catch (error) {
        console.warn('P4 translation error for key', key, error);
      }
    }

    if (typeof fallback !== 'string') {
      return hasKey ? key : '';
    }
    return applyParams(fallback, params);
  }

  function applyParams(template, params) {
    if (!template || !params) {
      return template;
    }
    return template.replace(/\{(\w+)\}/g, (match, paramKey) => {
      if (Object.prototype.hasOwnProperty.call(params, paramKey)) {
        const value = params[paramKey];
        return value !== undefined && value !== null ? String(value) : '';
      }
      return match;
    });
  }

  function formatNumber(value) {
    if (!Number.isFinite(value)) {
      return '0';
    }
    return numberFormatter ? numberFormatter.format(value) : String(Math.round(value));
  }

  function clampInteger(value, min, max, fallback) {
    const parsed = typeof value === 'number' ? value : Number.parseInt(value, 10);
    if (!Number.isFinite(parsed)) {
      return fallback;
    }
    const clamped = Math.min(Math.max(parsed, min), max);
    if (!Number.isFinite(clamped)) {
      return fallback;
    }
    return clamped;
  }

  function normalizeConfig(rawConfig, fallbackConfig) {
    const fallback = fallbackConfig || DEFAULT_CONFIG;
    const rows = clampInteger(rawConfig?.rows, 4, 12, fallback.rows);
    const columns = clampInteger(rawConfig?.columns, 4, 12, fallback.columns);

    const rawObstacles = rawConfig?.obstacles || {};
    const fallbackObstacles = fallback.obstacles || {};
    const maxObstacleCells = Math.max(0, rows * columns - 1);
    const obstacleCount = clampInteger(rawObstacles.count, 0, maxObstacleCells, fallbackObstacles.count || 0);
    const minRow = clampInteger(rawObstacles.minRow, 0, Math.max(0, rows - 2), fallbackObstacles.minRow || 1);
    const maxRow = clampInteger(
      rawObstacles.maxRow,
      minRow,
      Math.max(minRow, rows - 2),
      Math.max(minRow, fallbackObstacles.maxRow || minRow)
    );
    const enabledByDefault = typeof rawObstacles.enabledByDefault === 'boolean'
      ? rawObstacles.enabledByDefault
      : Boolean(fallbackObstacles.enabledByDefault);

    const rawAi = rawConfig?.ai || {};
    const fallbackAi = fallback.ai || {};
    const searchDepth = clampInteger(rawAi.searchDepth, 1, 6, fallbackAi.searchDepth || 4);
    const centerWeight = clampInteger(rawAi.centerWeight, 0, 50, fallbackAi.centerWeight || 3);
    const threeWeight = clampInteger(rawAi.threeWeight, 0, 2000, fallbackAi.threeWeight || 120);
    const twoWeight = clampInteger(rawAi.twoWeight, 0, 1000, fallbackAi.twoWeight || 35);
    const blockThreeWeight = clampInteger(rawAi.blockThreeWeight, 0, 2000, fallbackAi.blockThreeWeight || 140);
    const victoryScore = clampInteger(rawAi.victoryScore, 1, 1000000, fallbackAi.victoryScore || 100000);
    const defeatScoreRaw = typeof rawAi.defeatScore === 'number'
      ? rawAi.defeatScore
      : (fallbackAi.defeatScore ?? -100000);
    const defeatScore = Math.min(-1, Math.max(-1000000, defeatScoreRaw));

    const rawRewards = rawConfig?.rewards || {};
    const fallbackRewards = fallback.rewards || {};
    const humanVictoryTickets = clampInteger(
      rawRewards.humanVictoryTickets,
      0,
      999,
      fallbackRewards.humanVictoryTickets || 0
    );

    return {
      rows,
      columns,
      obstacles: {
        enabledByDefault,
        count: obstacleCount,
        minRow,
        maxRow
      },
      ai: {
        searchDepth,
        centerWeight,
        threeWeight,
        twoWeight,
        blockThreeWeight,
        victoryScore,
        defeatScore
      },
      rewards: {
        humanVictoryTickets
      }
    };
  }

  function loadConfig() {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          const normalized = normalizeConfig(data, state.config);
          applyConfig(normalized);
        }
      })
      .catch(error => {
        console.warn('P4: unable to load config', error);
      });
  }

  function applyConfig(config) {
    if (!config) {
      return;
    }
    state.config = config;
    state.aiSettings = config.ai;
    state.ticketReward = Math.max(0, Math.floor(Number(config.rewards.humanVictoryTickets) || 0));

    const previousRows = state.rows;
    const previousColumns = state.columns;

    state.rows = clampInteger(config.rows, 4, 12, DEFAULT_CONFIG.rows);
    state.columns = clampInteger(config.columns, 4, 12, DEFAULT_CONFIG.columns);

    const defaultObstaclesEnabled = Boolean(config.obstacles?.enabledByDefault);
    state.obstaclesEnabled = defaultObstaclesEnabled;

    if (state.elements.obstacleSelect) {
      state.elements.obstacleSelect.value = defaultObstaclesEnabled ? 'enabled' : 'disabled';
    }

    if (state.elements.modeSelect) {
      const currentMode = state.elements.modeSelect.value === 'local' ? 'local' : 'ai';
      state.mode = currentMode;
      state.elements.modeSelect.value = currentMode;
    }

    if (state.rows !== previousRows || state.columns !== previousColumns) {
      buildBoardElements();
    } else {
      updateBoardDimensions();
      updateBoardAppearance();
      updateColumnButtonLabels();
    }

    startNewGame(false);
  }

  function attachEventListeners() {
    if (state.elements.newGameButton) {
      state.elements.newGameButton.addEventListener('click', () => {
        startNewGame(true);
      });
    }

    if (state.elements.modeSelect) {
      state.elements.modeSelect.addEventListener('change', event => {
        const value = String(event.target.value);
        state.mode = value === 'local' ? 'local' : 'ai';
        startNewGame(true);
      });
    }

    if (state.elements.obstacleSelect) {
      state.elements.obstacleSelect.addEventListener('change', event => {
        const value = String(event.target.value);
        state.obstaclesEnabled = value !== 'disabled';
        startNewGame(true);
      });
    }

    if (state.elements.board) {
      state.elements.board.addEventListener('click', event => {
        const cell = event.target.closest('.p4__cell');
        if (!cell) {
          return;
        }
        const column = Number.parseInt(cell.dataset.column, 10);
        if (Number.isFinite(column)) {
          handleHumanMove(column);
        }
      });
    }
  }

  function attachLanguageHandler() {
    if (state.languageHandlerAttached) {
      return;
    }
    const handler = () => {
      refreshLocalizedTexts();
    };
    document.addEventListener('i18n:change', handler);
    state.languageHandlerAttached = true;
  }

  function buildBoardElements() {
    if (!state.elements.board) {
      return;
    }

    state.elements.board.innerHTML = '';
    state.cellElements = [];
    updateBoardDimensions();

    for (let row = 0; row < state.rows; row += 1) {
      const rowCells = [];
      for (let column = 0; column < state.columns; column += 1) {
        const cell = document.createElement('div');
        cell.className = 'p4__cell';
        cell.dataset.row = String(row);
        cell.dataset.column = String(column);
        cell.setAttribute('role', 'gridcell');
        cell.setAttribute('aria-selected', 'false');
        const token = document.createElement('span');
        token.className = 'p4__token';
        token.setAttribute('aria-hidden', 'true');
        cell.appendChild(token);
        state.elements.board.appendChild(cell);
        rowCells.push(cell);
      }
      state.cellElements.push(rowCells);
    }

    rebuildColumnButtons();
    state.board = createEmptyBoard();
    updateBoardAppearance();
    updateColumnButtonLabels();
  }

  function updateBoardDimensions() {
    if (!state.elements.board) {
      return;
    }
    state.elements.board.style.setProperty('--p4-columns', String(state.columns));
    state.elements.board.style.setProperty('--p4-rows', String(state.rows));
    state.elements.board.setAttribute('aria-rowcount', String(state.rows));
    state.elements.board.setAttribute('aria-colcount', String(state.columns));
  }

  function rebuildColumnButtons() {
    const container = state.elements.columnControls;
    if (!container) {
      state.columnButtons = [];
      return;
    }
    container.innerHTML = '';
    const fragment = document.createDocumentFragment();
    const buttons = [];
    for (let column = 0; column < state.columns; column += 1) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'p4__column-button';
      button.dataset.column = String(column);
      button.textContent = formatNumber(column + 1);
      button.addEventListener('click', () => {
        handleHumanMove(column);
      });
      fragment.appendChild(button);
      buttons.push(button);
    }
    container.appendChild(fragment);
    state.columnButtons = buttons;
  }

  function createEmptyBoard() {
    const board = [];
    for (let row = 0; row < state.rows; row += 1) {
      const rowCells = new Array(state.columns).fill(EMPTY);
      board.push(rowCells);
    }
    return board;
  }

  function startNewGame(triggeredByUser) {
    clearAiTimeout();
    state.board = createEmptyBoard();
    state.moves = 0;
    state.gameOver = false;
    state.winningSequence = null;
    state.winner = null;
    state.lastMove = null;
    state.rewardClaimed = false;
    state.claimedTickets = null;
    state.currentPlayer = PLAYER_ONE;
    state.mode = state.elements.modeSelect.value === 'local' ? 'local' : 'ai';
    state.obstaclesEnabled = state.elements.obstacleSelect.value !== 'disabled';

    if (state.obstaclesEnabled) {
      state.obstacles = generateObstacles();
      state.obstacles.forEach(position => {
        if (position && Number.isInteger(position.row) && Number.isInteger(position.column)) {
          if (position.row >= 0 && position.row < state.rows && position.column >= 0 && position.column < state.columns) {
            state.board[position.row][position.column] = OBSTACLE;
          }
        }
      });
    } else {
      state.obstacles = [];
    }

    state.maxMoves = state.rows * state.columns - state.obstacles.length;

    updateBoardAppearance();
    setStatus('index.sections.p4.status.ready', STATUS_FALLBACKS.ready);
    updateTurnIndicator();
    updateColumnButtons();
    updateTicketInfo();

    if (!triggeredByUser && state.mode === 'ai') {
      setStatus('index.sections.p4.status.ready', STATUS_FALLBACKS.ready);
    }
  }

  function clearAiTimeout() {
    if (state.aiTimeoutId !== null) {
      window.clearTimeout(state.aiTimeoutId);
      state.aiTimeoutId = null;
    }
    state.aiThinking = false;
  }

  function generateObstacles() {
    const obstacleConfig = state.config?.obstacles || {};
    const desiredCount = clampInteger(obstacleConfig.count, 0, state.rows * state.columns, 0);
    if (desiredCount <= 0) {
      return [];
    }
    const startRow = clampInteger(obstacleConfig.minRow, 0, Math.max(0, state.rows - 2), 1);
    const maxAllowedRow = Math.max(startRow, state.rows - 2);
    const endRow = clampInteger(obstacleConfig.maxRow, startRow, maxAllowedRow, startRow);

    const candidates = [];
    for (let row = startRow; row <= endRow; row += 1) {
      for (let column = 0; column < state.columns; column += 1) {
        candidates.push({ row, column });
      }
    }
    if (candidates.length === 0) {
      return [];
    }
    shuffle(candidates);
    return candidates.slice(0, Math.min(desiredCount, candidates.length));
  }

  function shuffle(list) {
    for (let index = list.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(Math.random() * (index + 1));
      const temp = list[index];
      list[index] = list[swapIndex];
      list[swapIndex] = temp;
    }
  }

  function setStatus(key, fallback, params) {
    state.status = { key, fallback, params };
    if (state.elements.status) {
      state.elements.status.textContent = translate(key, fallback, params);
    }
  }

  function updateTurnIndicator() {
    if (!state.elements.turnIndicator) {
      return;
    }
    let key;
    let fallback;

    if (state.gameOver) {
      if (state.winner === PLAYER_ONE) {
        key = state.mode === 'ai' ? 'index.sections.p4.turns.victoryPlayer1' : 'index.sections.p4.turns.victoryPlayer1';
        fallback = TURN_FALLBACKS.victoryPlayer1;
      } else if (state.winner === PLAYER_TWO) {
        if (state.mode === 'ai') {
          key = 'index.sections.p4.turns.victoryAi';
          fallback = TURN_FALLBACKS.victoryAi;
        } else {
          key = 'index.sections.p4.turns.victoryPlayer2';
          fallback = TURN_FALLBACKS.victoryPlayer2;
        }
      } else {
        key = 'index.sections.p4.turns.draw';
        fallback = TURN_FALLBACKS.draw;
      }
    } else if (state.mode === 'ai' && state.currentPlayer === PLAYER_TWO) {
      key = 'index.sections.p4.turns.ai';
      fallback = TURN_FALLBACKS.ai;
    } else if (state.currentPlayer === PLAYER_ONE) {
      key = 'index.sections.p4.turns.player1';
      fallback = TURN_FALLBACKS.player1;
    } else {
      key = 'index.sections.p4.turns.player2';
      fallback = TURN_FALLBACKS.player2;
    }

    state.elements.turnIndicator.textContent = translate(key, fallback);
  }

  function updateTicketInfo() {
    if (!state.elements.ticketInfo) {
      return;
    }
    if (Number.isFinite(state.claimedTickets) && state.claimedTickets > 0) {
      state.elements.ticketInfo.textContent = translate(
        'index.sections.p4.rewards.claimed',
        REWARD_FALLBACKS.claimed,
        { tickets: formatNumber(state.claimedTickets) }
      );
      return;
    }
    state.elements.ticketInfo.textContent = translate(
      'index.sections.p4.rewards.ai',
      REWARD_FALLBACKS.info,
      { tickets: formatNumber(state.ticketReward) }
    );
  }

  function updateColumnButtonLabels() {
    if (!state.columnButtons || state.columnButtons.length === 0) {
      return;
    }
    state.columnButtons.forEach((button, index) => {
      if (!button) {
        return;
      }
      const columnNumber = formatNumber(index + 1);
      const label = translate('index.sections.p4.controls.drop', COLUMN_LABEL_FALLBACK, { column: columnNumber });
      button.setAttribute('aria-label', label);
      button.setAttribute('title', label);
      button.dataset.column = String(index);
      button.textContent = columnNumber;
    });

    if (state.elements.columnControls) {
      const controlsLabel = translate(
        'index.sections.p4.controls.columns',
        COLUMN_CONTROLS_ARIA_FALLBACK
      );
      state.elements.columnControls.setAttribute('aria-label', controlsLabel);
    }

    if (state.elements.board) {
      const boardLabel = translate('index.sections.p4.board', BOARD_ARIA_FALLBACK);
      state.elements.board.setAttribute('aria-label', boardLabel);
    }
  }

  function updateColumnButtons() {
    const isHumanTurn = !state.gameOver
      && !state.aiThinking
      && (state.mode !== 'ai' || state.currentPlayer === PLAYER_ONE);

    state.columnButtons.forEach((button, index) => {
      if (!button) {
        return;
      }
      const columnFull = isColumnFull(index);
      button.disabled = !isHumanTurn || columnFull;
    });

    if (state.elements.board) {
      state.elements.board.classList.toggle('p4__board--blocked', !isHumanTurn);
    }
  }

  function refreshLocalizedTexts() {
    if (state.elements.status) {
      state.elements.status.textContent = translate(
        state.status.key,
        state.status.fallback,
        state.status.params
      );
    }
    updateTurnIndicator();
    updateTicketInfo();
    updateColumnButtonLabels();
    updateBoardAppearance();
  }

  function updateBoardAppearance() {
    for (let row = 0; row < state.rows; row += 1) {
      for (let column = 0; column < state.columns; column += 1) {
        const cell = state.cellElements[row] && state.cellElements[row][column];
        if (!cell) {
          continue;
        }
        cell.classList.remove('p4__cell--player1', 'p4__cell--player2', 'p4__cell--obstacle', 'p4__cell--last-move', 'p4__cell--win');
        const value = state.board[row][column];
        if (value === PLAYER_ONE) {
          cell.classList.add('p4__cell--player1');
        } else if (value === PLAYER_TWO) {
          cell.classList.add('p4__cell--player2');
        } else if (value === OBSTACLE) {
          cell.classList.add('p4__cell--obstacle');
        }
      }
    }

    if (state.lastMove && state.cellElements[state.lastMove.row]) {
      const cell = state.cellElements[state.lastMove.row][state.lastMove.column];
      if (cell) {
        cell.classList.add('p4__cell--last-move');
      }
    }

    if (Array.isArray(state.winningSequence)) {
      state.winningSequence.forEach(position => {
        if (!position) {
          return;
        }
        const cell = state.cellElements[position.row] && state.cellElements[position.row][position.column];
        if (cell) {
          cell.classList.add('p4__cell--win');
        }
      });
    }
  }

  function handleHumanMove(column) {
    if (state.gameOver || state.aiThinking) {
      return;
    }
    if (state.mode === 'ai' && state.currentPlayer !== PLAYER_ONE) {
      return;
    }
    if (!Number.isInteger(column) || column < 0 || column >= state.columns) {
      return;
    }
    const row = dropToken(state.board, column, state.currentPlayer);
    if (row < 0) {
      setStatus('index.sections.p4.status.columnFull', STATUS_FALLBACKS.columnFull);
      updateColumnButtons();
      return;
    }
    finalizeMove(row, column, state.currentPlayer, { isHuman: true });
  }

  function dropToken(board, column, player) {
    for (let row = state.rows - 1; row >= 0; row -= 1) {
      if (board[row][column] === EMPTY) {
        board[row][column] = player;
        return row;
      }
    }
    return -1;
  }

  function isColumnFull(column) {
    for (let row = 0; row < state.rows; row += 1) {
      if (state.board[row][column] === EMPTY) {
        return false;
      }
    }
    return true;
  }

  function finalizeMove(row, column, player, { isAi = false } = {}) {
    state.lastMove = { row, column };
    state.moves += 1;

    const result = findWinner(state.board);
    if (result) {
      state.gameOver = true;
      state.winningSequence = result.positions;
      state.winner = result.winner;
      updateBoardAppearance();
      handleVictory(result.winner);
      updateTurnIndicator();
      updateColumnButtons();
      return;
    }

    if (state.moves >= state.maxMoves) {
      state.gameOver = true;
      state.winner = null;
      state.winningSequence = null;
      setStatus('index.sections.p4.status.draw', STATUS_FALLBACKS.draw);
      updateBoardAppearance();
      updateTurnIndicator();
      updateColumnButtons();
      return;
    }

    state.currentPlayer = player === PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
    updateBoardAppearance();

    if (state.mode === 'ai' && state.currentPlayer === PLAYER_TWO) {
      state.aiThinking = true;
      setStatus('index.sections.p4.status.turnAi', STATUS_FALLBACKS.turnAi);
      updateTurnIndicator();
      updateColumnButtons();
      state.aiTimeoutId = window.setTimeout(() => {
        state.aiTimeoutId = null;
        performAiMove();
      }, 320);
      return;
    }

    const statusKey = state.currentPlayer === PLAYER_ONE
      ? 'index.sections.p4.status.turnPlayer1'
      : 'index.sections.p4.status.turnPlayer2';
    const fallback = state.currentPlayer === PLAYER_ONE
      ? STATUS_FALLBACKS.turnPlayer1
      : STATUS_FALLBACKS.turnPlayer2;
    setStatus(statusKey, fallback);
    updateTurnIndicator();
    updateColumnButtons();
  }

  function handleVictory(winner) {
    if (state.mode === 'ai') {
      if (winner === PLAYER_ONE) {
        const tickets = Math.max(0, Math.floor(Number(state.ticketReward) || 0));
        if (tickets > 0) {
          let gained = tickets;
          const awardGacha = typeof gainGachaTickets === 'function' ? gainGachaTickets : null;
          if (awardGacha) {
            try {
              const result = awardGacha(tickets, { unlockTicketStar: true });
              if (Number.isFinite(result) && result > 0) {
                gained = Math.floor(result);
              }
            } catch (error) {
              console.warn('P4: unable to grant gacha tickets', error);
            }
          }
          state.rewardClaimed = true;
          state.claimedTickets = gained;
          setStatus('index.sections.p4.status.winHumanAi', STATUS_FALLBACKS.winHumanAi, {
            tickets: formatNumber(gained)
          });
        } else {
          state.rewardClaimed = false;
          state.claimedTickets = null;
          setStatus('index.sections.p4.status.winPlayer1', STATUS_FALLBACKS.winPlayer1);
        }
      } else if (winner === PLAYER_TWO) {
        state.rewardClaimed = false;
        state.claimedTickets = null;
        setStatus('index.sections.p4.status.winAi', STATUS_FALLBACKS.winAi);
      }
    } else {
      if (winner === PLAYER_ONE) {
        setStatus('index.sections.p4.status.winPlayer1', STATUS_FALLBACKS.winPlayer1);
      } else if (winner === PLAYER_TWO) {
        setStatus('index.sections.p4.status.winPlayer2', STATUS_FALLBACKS.winPlayer2);
      }
    }
    updateTicketInfo();
  }

  function performAiMove() {
    if (state.gameOver) {
      state.aiThinking = false;
      updateColumnButtons();
      return;
    }

    const simulatedBoard = state.board.map(row => row.slice());
    const validColumns = getValidColumns(simulatedBoard);
    if (validColumns.length === 0) {
      state.aiThinking = false;
      updateColumnButtons();
      return;
    }

    const decision = minimax(simulatedBoard, state.aiSettings.searchDepth, Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY, true);
    let chosenColumn = decision && Number.isInteger(decision.column) ? decision.column : validColumns[0];
    if (!validColumns.includes(chosenColumn)) {
      chosenColumn = validColumns[0];
    }

    let row = dropToken(state.board, chosenColumn, PLAYER_TWO);
    if (row < 0) {
      for (let index = 0; index < validColumns.length; index += 1) {
        const fallbackColumn = validColumns[index];
        row = dropToken(state.board, fallbackColumn, PLAYER_TWO);
        if (row >= 0) {
          chosenColumn = fallbackColumn;
          break;
        }
      }
    }

    state.aiThinking = false;

    if (row >= 0) {
      finalizeMove(row, chosenColumn, PLAYER_TWO, { isAi: true });
    } else {
      state.currentPlayer = PLAYER_ONE;
      setStatus('index.sections.p4.status.turnPlayer1', STATUS_FALLBACKS.turnPlayer1);
      updateTurnIndicator();
      updateColumnButtons();
    }
  }

  function getValidColumns(board) {
    const columns = [];
    const center = Math.floor(state.columns / 2);
    for (let column = 0; column < state.columns; column += 1) {
      let hasSpace = false;
      for (let row = 0; row < state.rows; row += 1) {
        if (board[row][column] === EMPTY) {
          hasSpace = true;
          break;
        }
      }
      if (hasSpace) {
        columns.push(column);
      }
    }
    columns.sort((a, b) => Math.abs(center - a) - Math.abs(center - b));
    return columns;
  }

  function minimax(board, depth, alpha, beta, maximizing) {
    const result = findWinner(board);
    if (result) {
      if (result.winner === PLAYER_TWO) {
        return { column: null, score: state.aiSettings.victoryScore * (depth + 1) };
      }
      if (result.winner === PLAYER_ONE) {
        return { column: null, score: state.aiSettings.defeatScore * (depth + 1) };
      }
    }

    const validColumns = getValidColumns(board);
    if (depth === 0 || validColumns.length === 0) {
      return { column: null, score: evaluateBoard(board, PLAYER_TWO) };
    }

    let bestColumn = validColumns[0];

    if (maximizing) {
      let value = Number.NEGATIVE_INFINITY;
      for (let index = 0; index < validColumns.length; index += 1) {
        const column = validColumns[index];
        const row = applyMove(board, column, PLAYER_TWO);
        if (row < 0) {
          continue;
        }
        const evaluation = minimax(board, depth - 1, alpha, beta, false);
        undoMove(board, column, row);
        if (evaluation.score > value) {
          value = evaluation.score;
          bestColumn = column;
        }
        alpha = Math.max(alpha, value);
        if (alpha >= beta) {
          break;
        }
      }
      return { column: bestColumn, score: value };
    }

    let value = Number.POSITIVE_INFINITY;
    for (let index = 0; index < validColumns.length; index += 1) {
      const column = validColumns[index];
      const row = applyMove(board, column, PLAYER_ONE);
      if (row < 0) {
        continue;
      }
      const evaluation = minimax(board, depth - 1, alpha, beta, true);
      undoMove(board, column, row);
      if (evaluation.score < value) {
        value = evaluation.score;
        bestColumn = column;
      }
      beta = Math.min(beta, value);
      if (alpha >= beta) {
        break;
      }
    }
    return { column: bestColumn, score: value };
  }

  function applyMove(board, column, player) {
    for (let row = state.rows - 1; row >= 0; row -= 1) {
      if (board[row][column] === EMPTY) {
        board[row][column] = player;
        return row;
      }
    }
    return -1;
  }

  function undoMove(board, column, row) {
    if (row >= 0 && row < state.rows) {
      board[row][column] = EMPTY;
    }
  }

  function evaluateBoard(board, player) {
    const opponent = player === PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
    let score = 0;

    const centerColumn = Math.floor(state.columns / 2);
    let centerCount = 0;
    for (let row = 0; row < state.rows; row += 1) {
      if (board[row][centerColumn] === player) {
        centerCount += 1;
      }
    }
    score += centerCount * state.aiSettings.centerWeight;

    // Horizontal
    for (let row = 0; row < state.rows; row += 1) {
      for (let column = 0; column <= state.columns - WINDOW_LENGTH; column += 1) {
        const window = [];
        for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
          window.push(board[row][column + offset]);
        }
        score += evaluateWindow(window, player, opponent);
      }
    }

    // Vertical
    for (let column = 0; column < state.columns; column += 1) {
      for (let row = 0; row <= state.rows - WINDOW_LENGTH; row += 1) {
        const window = [];
        for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
          window.push(board[row + offset][column]);
        }
        score += evaluateWindow(window, player, opponent);
      }
    }

    // Diagonal down-right
    for (let row = 0; row <= state.rows - WINDOW_LENGTH; row += 1) {
      for (let column = 0; column <= state.columns - WINDOW_LENGTH; column += 1) {
        const window = [];
        for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
          window.push(board[row + offset][column + offset]);
        }
        score += evaluateWindow(window, player, opponent);
      }
    }

    // Diagonal up-right
    for (let row = WINDOW_LENGTH - 1; row < state.rows; row += 1) {
      for (let column = 0; column <= state.columns - WINDOW_LENGTH; column += 1) {
        const window = [];
        for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
          window.push(board[row - offset][column + offset]);
        }
        score += evaluateWindow(window, player, opponent);
      }
    }

    return score;
  }

  function evaluateWindow(window, player, opponent) {
    let playerCount = 0;
    let opponentCount = 0;
    let emptyCount = 0;
    let obstacleCount = 0;

    for (let index = 0; index < window.length; index += 1) {
      const value = window[index];
      if (value === player) {
        playerCount += 1;
      } else if (value === opponent) {
        opponentCount += 1;
      } else if (value === EMPTY) {
        emptyCount += 1;
      } else if (value === OBSTACLE) {
        obstacleCount += 1;
      }
    }

    if (playerCount === WINDOW_LENGTH) {
      return state.aiSettings.victoryScore;
    }
    if (opponentCount === WINDOW_LENGTH) {
      return state.aiSettings.defeatScore;
    }

    if (obstacleCount > 0) {
      return 0;
    }

    let score = 0;
    if (playerCount === 3 && emptyCount === 1) {
      score += state.aiSettings.threeWeight;
    } else if (playerCount === 2 && emptyCount === 2) {
      score += state.aiSettings.twoWeight;
    }

    if (opponentCount === 3 && emptyCount === 1) {
      score += state.aiSettings.blockThreeWeight;
    }

    return score;
  }

  function findWinner(board) {
    for (let row = 0; row < state.rows; row += 1) {
      for (let column = 0; column < state.columns; column += 1) {
        const value = board[row][column];
        if (value !== PLAYER_ONE && value !== PLAYER_TWO) {
          continue;
        }
        // Horizontal
        if (column <= state.columns - WINDOW_LENGTH) {
          let matches = true;
          const positions = [];
          for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
            if (board[row][column + offset] !== value) {
              matches = false;
              break;
            }
            positions.push({ row, column: column + offset });
          }
          if (matches) {
            return { winner: value, positions };
          }
        }
        // Vertical
        if (row <= state.rows - WINDOW_LENGTH) {
          let matches = true;
          const positions = [];
          for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
            if (board[row + offset][column] !== value) {
              matches = false;
              break;
            }
            positions.push({ row: row + offset, column });
          }
          if (matches) {
            return { winner: value, positions };
          }
        }
        // Diagonal down-right
        if (row <= state.rows - WINDOW_LENGTH && column <= state.columns - WINDOW_LENGTH) {
          let matches = true;
          const positions = [];
          for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
            if (board[row + offset][column + offset] !== value) {
              matches = false;
              break;
            }
            positions.push({ row: row + offset, column: column + offset });
          }
          if (matches) {
            return { winner: value, positions };
          }
        }
        // Diagonal up-right
        if (row >= WINDOW_LENGTH - 1 && column <= state.columns - WINDOW_LENGTH) {
          let matches = true;
          const positions = [];
          for (let offset = 0; offset < WINDOW_LENGTH; offset += 1) {
            if (board[row - offset][column + offset] !== value) {
              matches = false;
              break;
            }
            positions.push({ row: row - offset, column: column + offset });
          }
          if (matches) {
            return { winner: value, positions };
          }
        }
      }
    }
    return null;
  }

})();
