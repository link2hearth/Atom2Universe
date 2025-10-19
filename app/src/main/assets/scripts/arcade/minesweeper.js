(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const DIFFICULTY_PRESETS = Object.freeze({
    facile: Object.freeze({ rows: 9, cols: 12, mines: 18 }),
    moyen: Object.freeze({ rows: 11, cols: 15, mines: 28 }),
    difficile: Object.freeze({ rows: 12, cols: 20, mines: 38 })
  });

  const FIXED_CELL_SIZE = 42;

  const ORIENTATION_MODE = Object.freeze({
    LANDSCAPE: 'landscape',
    PORTRAIT: 'portrait'
  });

  const DEFAULT_TICKET_REWARD_RATIO = 0.5;

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function clampMines(rows, cols, mines) {
    const maxMines = rows * cols - 1;
    if (!Number.isFinite(mines) || mines <= 0) {
      return Math.max(1, Math.floor((rows * cols) / 7));
    }
    return Math.min(maxMines, Math.floor(mines));
  }

  function createEmptyGrid(rows, cols) {
    return Array.from({ length: rows }, (_, row) =>
      Array.from({ length: cols }, (_, col) => ({
        row,
        col,
        mine: false,
        adjacent: 0,
        revealed: false,
        flagged: false,
        element: null
      }))
    );
  }

  function getNeighborCoordinates(row, col, rows, cols) {
    const neighbors = [];
    for (let deltaRow = -1; deltaRow <= 1; deltaRow += 1) {
      for (let deltaCol = -1; deltaCol <= 1; deltaCol += 1) {
        if (deltaRow === 0 && deltaCol === 0) {
          continue;
        }
        const neighborRow = row + deltaRow;
        const neighborCol = col + deltaCol;
        if (neighborRow < 0 || neighborRow >= rows) {
          continue;
        }
        if (neighborCol < 0 || neighborCol >= cols) {
          continue;
        }
        neighbors.push({ row: neighborRow, col: neighborCol });
      }
    }
    return neighbors;
  }

  function shuffleInPlace(array) {
    for (let index = array.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(Math.random() * (index + 1));
      const temporary = array[index];
      array[index] = array[swapIndex];
      array[swapIndex] = temporary;
    }
  }

  onReady(() => {
    const boardElement = document.getElementById('minesweeperBoard');
    const difficultySelect = document.getElementById('minesweeperDifficulty');
    const resetButton = document.getElementById('minesweeperReset');
    const containerElement = boardElement ? boardElement.closest('.minesweeper') : null;

    if (!boardElement || !difficultySelect || !resetButton) {
      return;
    }

    const labelCache = {
      hidden: boardElement.getAttribute('data-hidden-label') || 'Hidden cell',
      flagged: boardElement.getAttribute('data-flagged-label') || 'Flagged cell',
      revealed: boardElement.getAttribute('data-revealed-label') || 'Revealed cell',
      mine: boardElement.getAttribute('data-mine-label') || 'Mine'
    };

    const gameState = {
      rows: 0,
      cols: 0,
      mineCount: 0,
      grid: [],
      safeRemaining: 0,
      status: 'ready',
      armed: false,
      cellSize: FIXED_CELL_SIZE,
      orientation: ORIENTATION_MODE.LANDSCAPE,
      difficultyKey: 'moyen'
    };

    const orientationState = {
      listenersAttached: false,
      mediaQuery: null
    };

    const boundOrientationHandler = () => {
      handleOrientationChange();
    };

    function refreshLabelCache() {
      labelCache.hidden = boardElement.getAttribute('data-hidden-label') || labelCache.hidden;
      labelCache.flagged = boardElement.getAttribute('data-flagged-label') || labelCache.flagged;
      labelCache.revealed = boardElement.getAttribute('data-revealed-label') || labelCache.revealed;
      labelCache.mine = boardElement.getAttribute('data-mine-label') || labelCache.mine;
      updateAllCellLabels();
    }

    function updateBoardState(status) {
      gameState.status = status;
      boardElement.setAttribute('data-state', status);
    }

    function normalizeDifficultyKey(rawKey) {
      if (rawKey && Object.prototype.hasOwnProperty.call(DIFFICULTY_PRESETS, rawKey)) {
        return rawKey;
      }
      return 'moyen';
    }

    function applyCellClasses(cell, classes) {
      const { element } = cell;
      if (!element) {
        return;
      }
      element.className = `minesweeper-cell${classes ? ` ${classes}` : ''}`;
    }

    function setCellContent(cell, text) {
      if (!cell.element) {
        return;
      }
      cell.element.textContent = text == null ? '' : String(text);
    }

    function setCellAriaLabel(cell, label) {
      if (!cell.element) {
        return;
      }
      cell.element.setAttribute('aria-label', label);
    }

    function getCell(row, col) {
      if (row < 0 || row >= gameState.rows) {
        return null;
      }
      if (col < 0 || col >= gameState.cols) {
        return null;
      }
      return gameState.grid[row][col];
    }

    function resetCellVisual(cell) {
      if (!cell.element) {
        return;
      }
      cell.element.disabled = false;
      cell.element.tabIndex = 0;
      cell.element.dataset.row = String(cell.row);
      cell.element.dataset.col = String(cell.col);
      cell.element.className = 'minesweeper-cell';
      cell.element.textContent = '';
      setCellAriaLabel(cell, labelCache.hidden);
    }

    function getLayoutDimensions() {
      if (gameState.orientation === ORIENTATION_MODE.PORTRAIT) {
        return {
          columns: gameState.rows,
          rows: gameState.cols
        };
      }
      return {
        columns: gameState.cols,
        rows: gameState.rows
      };
    }

    function updateBoardLayoutMetrics() {
      if (!boardElement) {
        return;
      }
      const layout = getLayoutDimensions();
      boardElement.style.setProperty('--minesweeper-columns', layout.columns);
      boardElement.style.setProperty('--minesweeper-rows', layout.rows);
    }

    function positionCellElement(cell) {
      if (!cell || !cell.element) {
        return;
      }
      let columnStart;
      let rowStart;
      if (gameState.orientation === ORIENTATION_MODE.PORTRAIT) {
        columnStart = cell.row + 1;
        rowStart = cell.col + 1;
      } else {
        columnStart = cell.col + 1;
        rowStart = cell.row + 1;
      }
      cell.element.style.gridColumnStart = String(columnStart);
      cell.element.style.gridRowStart = String(rowStart);
    }

    function applyOrientationLayout() {
      if (!boardElement || !gameState.grid.length) {
        return;
      }
      updateBoardLayoutMetrics();
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          positionCellElement(gameState.grid[row][col]);
        }
      }
    }

    function renderBoard() {
      boardElement.innerHTML = '';
      boardElement.style.setProperty('--minesweeper-cell-size', `${gameState.cellSize}px`);
      boardElement.style.removeProperty('--minesweeper-board-width');
      boardElement.style.removeProperty('--minesweeper-board-height');
      boardElement.style.removeProperty('width');
      boardElement.style.removeProperty('height');
      updateBoardLayoutMetrics();
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          const cell = gameState.grid[row][col];
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'minesweeper-cell';
          button.dataset.row = String(row);
          button.dataset.col = String(col);
          button.setAttribute('aria-label', labelCache.hidden);
          button.addEventListener('click', handleCellPrimary, { passive: true });
          button.addEventListener('contextmenu', handleCellSecondary);
          button.addEventListener('keydown', handleCellKeydown);
          cell.element = button;
          boardElement.appendChild(button);
          positionCellElement(cell);
        }
      }
      applyOrientationClass();
      applyOrientationLayout();
    }

    function initializeGrid(difficultyKey) {
      const presetKey = normalizeDifficultyKey(difficultyKey);
      const preset = DIFFICULTY_PRESETS[presetKey];
      const presetRows = Math.max(4, Number(preset.rows) || 8);
      const presetCols = Math.max(4, Number(preset.cols) || 8);
      const rows = Math.min(presetRows, presetCols);
      const cols = Math.max(presetRows, presetCols);
      const mines = clampMines(rows, cols, Number(preset.mines));
      gameState.rows = rows;
      gameState.cols = cols;
      gameState.mineCount = mines;
      gameState.grid = createEmptyGrid(rows, cols);
      gameState.safeRemaining = rows * cols - mines;
      gameState.status = 'ready';
      gameState.armed = false;
      gameState.cellSize = FIXED_CELL_SIZE;
      gameState.difficultyKey = presetKey;
      renderBoard();
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          resetCellVisual(gameState.grid[row][col]);
        }
      }
      updateBoardState('ready');
    }

    function computeOrientationMode() {
      if (typeof window === 'undefined') {
        return ORIENTATION_MODE.LANDSCAPE;
      }
      const width = window.innerWidth || 0;
      const height = window.innerHeight || 0;
      if (height > width && width > 0) {
        return ORIENTATION_MODE.PORTRAIT;
      }
      if (width === 0 && height > 0) {
        return ORIENTATION_MODE.PORTRAIT;
      }
      return ORIENTATION_MODE.LANDSCAPE;
    }

    function applyOrientationClass() {
      if (!containerElement) {
        return;
      }
      containerElement.classList.toggle('minesweeper--portrait', gameState.orientation === ORIENTATION_MODE.PORTRAIT);
      containerElement.classList.toggle('minesweeper--landscape', gameState.orientation === ORIENTATION_MODE.LANDSCAPE);
    }

    function handleOrientationChange({ force = false } = {}) {
      const mode = computeOrientationMode();
      if (!force && mode === gameState.orientation) {
        return;
      }
      gameState.orientation = mode;
      applyOrientationClass();
      applyOrientationLayout();
    }

    function attachOrientationListeners() {
      if (!containerElement) {
        return;
      }
      if (orientationState.listenersAttached || typeof window === 'undefined') {
        handleOrientationChange({ force: true });
        return;
      }
      window.addEventListener('resize', boundOrientationHandler);
      window.addEventListener('orientationchange', boundOrientationHandler);
      if (typeof window.matchMedia === 'function') {
        try {
          orientationState.mediaQuery = window.matchMedia('(orientation: portrait)');
          const media = orientationState.mediaQuery;
          if (media) {
            if (typeof media.addEventListener === 'function') {
              media.addEventListener('change', boundOrientationHandler);
            } else if (typeof media.addListener === 'function') {
              media.addListener(boundOrientationHandler);
            }
          }
        } catch (error) {
          console.warn('Unable to attach minesweeper orientation listener', error);
        }
      }
      orientationState.listenersAttached = true;
      handleOrientationChange({ force: true });
    }

    function getSafeZone(cell) {
      const safe = new Set();
      safe.add(`${cell.row}:${cell.col}`);
      const neighbors = getNeighborCoordinates(cell.row, cell.col, gameState.rows, gameState.cols);
      neighbors.forEach(({ row, col }) => {
        safe.add(`${row}:${col}`);
      });
      return safe;
    }

    function armBoard(initialCell) {
      const exclusion = getSafeZone(initialCell);
      const candidates = [];
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          const key = `${row}:${col}`;
          if (exclusion.has(key)) {
            continue;
          }
          candidates.push(key);
        }
      }
      shuffleInPlace(candidates);
      const minesToPlace = Math.min(gameState.mineCount, candidates.length);
      for (let index = 0; index < minesToPlace; index += 1) {
        const [rowStr, colStr] = candidates[index].split(':');
        const row = Number.parseInt(rowStr, 10);
        const col = Number.parseInt(colStr, 10);
        const cell = getCell(row, col);
        if (cell) {
          cell.mine = true;
        }
      }

      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          const cell = getCell(row, col);
          if (!cell || cell.mine) {
            continue;
          }
          const neighbors = getNeighborCoordinates(row, col, gameState.rows, gameState.cols);
          let adjacentMines = 0;
          for (let i = 0; i < neighbors.length; i += 1) {
            const neighbor = getCell(neighbors[i].row, neighbors[i].col);
            if (neighbor && neighbor.mine) {
              adjacentMines += 1;
            }
          }
          cell.adjacent = adjacentMines;
        }
      }
      gameState.armed = true;
    }

    function ensureBoardIsArmed(initialCell) {
      if (gameState.armed) {
        return;
      }
      armBoard(initialCell);
      updateBoardState('playing');
    }

    function revealMine(cell, detonated) {
      if (!cell || cell.revealed) {
        return;
      }
      cell.revealed = true;
      if (cell.element) {
        cell.element.disabled = true;
      }
      applyCellClasses(cell, `minesweeper-cell--revealed minesweeper-cell--mine${detonated ? ' minesweeper-cell--detonated' : ''}`);
      setCellContent(cell, '');
      setCellAriaLabel(cell, labelCache.mine);
    }

    function revealEmpty(cell) {
      if (!cell || cell.revealed) {
        return;
      }
      cell.revealed = true;
      if (cell.element) {
        cell.element.disabled = true;
      }
      applyCellClasses(cell, 'minesweeper-cell--revealed minesweeper-cell--empty');
      setCellContent(cell, '');
      setCellAriaLabel(cell, labelCache.revealed);
    }

    function revealNumber(cell, number) {
      if (!cell || cell.revealed) {
        return;
      }
      cell.revealed = true;
      if (cell.element) {
        cell.element.disabled = true;
      }
      const classes = ['minesweeper-cell--revealed'];
      if (number > 0) {
        classes.push(`minesweeper-cell--adjacent-${Math.min(number, 8)}`);
      }
      applyCellClasses(cell, classes.join(' '));
      setCellContent(cell, number > 0 ? number : '');
      setCellAriaLabel(cell, labelCache.revealed);
    }

    function updateAllCellLabels() {
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          const cell = getCell(row, col);
          if (!cell || !cell.element) {
            continue;
          }
          if (cell.revealed) {
            if (cell.mine) {
              setCellAriaLabel(cell, labelCache.mine);
            } else {
              setCellAriaLabel(cell, labelCache.revealed);
            }
          } else if (cell.flagged) {
            setCellAriaLabel(cell, labelCache.flagged);
          } else {
            setCellAriaLabel(cell, labelCache.hidden);
          }
        }
      }
    }

    function setFlag(cell, flagged) {
      if (!cell || !cell.element) {
        return;
      }
      if (cell.revealed) {
        return;
      }
      cell.flagged = flagged;
      if (flagged) {
        cell.element.classList.add('minesweeper-cell--flagged');
        setCellAriaLabel(cell, labelCache.flagged);
      } else {
        cell.element.classList.remove('minesweeper-cell--flagged');
        cell.element.classList.remove('minesweeper-cell--misflag');
        setCellAriaLabel(cell, labelCache.hidden);
      }
    }

    function toggleFlag(cell) {
      if (!cell || gameState.status === 'lost' || gameState.status === 'won') {
        return;
      }
      setFlag(cell, !cell.flagged);
    }

    function cascadeReveal(startCell) {
      const queue = [startCell];
      while (queue.length) {
        const cell = queue.shift();
        if (!cell || cell.revealed || cell.flagged) {
          continue;
        }
        if (cell.mine) {
          continue;
        }
        const adjacent = Number(cell.adjacent) || 0;
        if (adjacent <= 0) {
          revealEmpty(cell);
        } else {
          revealNumber(cell, adjacent);
        }
        gameState.safeRemaining = Math.max(0, gameState.safeRemaining - 1);
        if (adjacent === 0) {
          const neighbors = getNeighborCoordinates(cell.row, cell.col, gameState.rows, gameState.cols);
          for (let i = 0; i < neighbors.length; i += 1) {
            const neighbor = getCell(neighbors[i].row, neighbors[i].col);
            if (neighbor && !neighbor.revealed && !neighbor.flagged) {
              queue.push(neighbor);
            }
          }
        }
      }
    }

    function revealCell(cell) {
      if (!cell || cell.revealed || cell.flagged) {
        return;
      }
      ensureBoardIsArmed(cell);
      if (cell.mine) {
        revealMine(cell, true);
        updateBoardState('lost');
        exposeBoardAfterLoss(cell);
        return;
      }
      cascadeReveal(cell);
      if (gameState.safeRemaining <= 0 && gameState.status !== 'lost') {
        updateBoardState('won');
        exposeMinesAfterWin();
        grantVictoryTickets();
      }
    }

    function exposeBoardAfterLoss(triggerCell) {
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          const cell = getCell(row, col);
          if (!cell) {
            continue;
          }
          if (cell.mine) {
            const detonated = triggerCell && cell.row === triggerCell.row && cell.col === triggerCell.col;
            revealMine(cell, detonated);
          } else if (cell.flagged) {
            cell.flagged = false;
            if (cell.element) {
              cell.element.disabled = true;
              cell.element.classList.remove('minesweeper-cell--flagged');
              cell.element.classList.add('minesweeper-cell--revealed', 'minesweeper-cell--misflag');
              setCellAriaLabel(cell, labelCache.revealed);
            }
          } else if (!cell.revealed) {
            const adjacent = Number(cell.adjacent) || 0;
            if (adjacent > 0) {
              revealNumber(cell, adjacent);
            } else {
              revealEmpty(cell);
            }
          }
        }
      }
    }

    function exposeMinesAfterWin() {
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          const cell = getCell(row, col);
          if (!cell) {
            continue;
          }
          if (cell.mine && !cell.flagged) {
            cell.flagged = true;
            if (cell.element) {
              cell.element.classList.add('minesweeper-cell--flagged');
              cell.element.disabled = true;
              setCellAriaLabel(cell, labelCache.flagged);
            }
          } else if (!cell.mine && !cell.revealed) {
            const adjacent = Number(cell.adjacent) || 0;
            if (adjacent > 0) {
              revealNumber(cell, adjacent);
            } else {
              revealEmpty(cell);
            }
          } else if (cell.element) {
            cell.element.disabled = true;
          }
        }
      }
    }

    function resolveTicketRewardRatio() {
      if (typeof GLOBAL_CONFIG === 'undefined' || !GLOBAL_CONFIG) {
        return DEFAULT_TICKET_REWARD_RATIO;
      }
      const arcadeConfig = GLOBAL_CONFIG.arcade;
      if (!arcadeConfig || typeof arcadeConfig !== 'object') {
        return DEFAULT_TICKET_REWARD_RATIO;
      }
      const demineurConfig = arcadeConfig.demineur;
      if (!demineurConfig || typeof demineurConfig !== 'object') {
        return DEFAULT_TICKET_REWARD_RATIO;
      }
      const rewardConfig = demineurConfig.rewards;
      if (!rewardConfig || typeof rewardConfig !== 'object') {
        return DEFAULT_TICKET_REWARD_RATIO;
      }
      const ticketsConfig = rewardConfig.gachaTickets;
      if (!ticketsConfig || typeof ticketsConfig !== 'object') {
        return DEFAULT_TICKET_REWARD_RATIO;
      }
      const ratioCandidate =
        ticketsConfig.perMineRatio ?? ticketsConfig.mineRatio ?? ticketsConfig.perMine ?? ticketsConfig.ratio;
      const ratio = Number.parseFloat(ratioCandidate);
      if (!Number.isFinite(ratio)) {
        return DEFAULT_TICKET_REWARD_RATIO;
      }
      return Math.max(0, ratio);
    }

    function computeVictoryTicketReward(totalMines) {
      if (!Number.isFinite(totalMines) || totalMines <= 0) {
        return 0;
      }
      const ratio = resolveTicketRewardRatio();
      if (!Number.isFinite(ratio) || ratio <= 0) {
        return 0;
      }
      return Math.floor(totalMines * ratio);
    }

    function grantVictoryTickets() {
      const totalMines = Math.max(0, Math.floor(Number(gameState.mineCount) || 0));
      const reward = computeVictoryTicketReward(totalMines);
      if (!Number.isFinite(reward) || reward <= 0) {
        return;
      }

      let granted = reward;
      if (typeof gainGachaTickets === 'function') {
        granted = gainGachaTickets(reward, { unlockTicketStar: true });
      }

      if (!Number.isFinite(granted) || granted <= 0) {
        return;
      }

      let fallbackLabel = `${granted} tickets`;
      if (typeof formatInteger === 'function') {
        fallbackLabel = `${formatInteger(granted)} tickets`;
      }
      const ticketLabel = typeof formatTicketLabel === 'function'
        ? formatTicketLabel(granted)
        : fallbackLabel;
      const message = typeof translate === 'function'
        ? translate(
            'scripts.arcade.minesweeper.rewardToast',
            'Minesweeper reward: {tickets}',
            { tickets: ticketLabel }
          )
        : `Minesweeper reward: ${ticketLabel}`;

      if (message && typeof showToast === 'function') {
        showToast(message);
      }
      if (typeof updateArcadeTicketDisplay === 'function') {
        updateArcadeTicketDisplay();
      }
    }

    function handleCellPrimary(event) {
      const target = event.currentTarget || event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      const row = Number.parseInt(target.dataset.row || '', 10);
      const col = Number.parseInt(target.dataset.col || '', 10);
      if (!Number.isInteger(row) || !Number.isInteger(col)) {
        return;
      }
      const cell = getCell(row, col);
      if (!cell || gameState.status === 'lost' || gameState.status === 'won') {
        return;
      }
      revealCell(cell);
    }

    function handleCellSecondary(event) {
      event.preventDefault();
      const target = event.currentTarget || event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      const row = Number.parseInt(target.dataset.row || '', 10);
      const col = Number.parseInt(target.dataset.col || '', 10);
      if (!Number.isInteger(row) || !Number.isInteger(col)) {
        return;
      }
      const cell = getCell(row, col);
      if (!cell || gameState.status === 'lost' || gameState.status === 'won') {
        return;
      }
      toggleFlag(cell);
    }

    function handleCellKeydown(event) {
      if (event.defaultPrevented) {
        return;
      }
      const target = event.currentTarget || event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      const row = Number.parseInt(target.dataset.row || '', 10);
      const col = Number.parseInt(target.dataset.col || '', 10);
      if (!Number.isInteger(row) || !Number.isInteger(col)) {
        return;
      }
      const cell = getCell(row, col);
      if (!cell) {
        return;
      }
      if (event.key === 'f' || event.key === 'F') {
        event.preventDefault();
        toggleFlag(cell);
        return;
      }
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
        event.preventDefault();
        if (gameState.status !== 'lost' && gameState.status !== 'won') {
          revealCell(cell);
        }
      }
    }

    function resetGame() {
      const key = normalizeDifficultyKey(difficultySelect.value);
      if (key !== difficultySelect.value) {
        difficultySelect.value = key;
      }
      initializeGrid(key);
    }

    difficultySelect.addEventListener('change', () => {
      resetGame();
    });

    resetButton.addEventListener('click', () => {
      resetGame();
    });

    attachOrientationListeners();

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('i18n:languagechange', () => {
        refreshLabelCache();
      });
    }

    resetGame();
  });
})();
