(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const DIFFICULTY_PRESETS = Object.freeze({
    facile: Object.freeze({ rows: 8, cols: 8, mines: 10 }),
    moyen: Object.freeze({ rows: 12, cols: 12, mines: 24 }),
    difficile: Object.freeze({ rows: 16, cols: 16, mines: 40 })
  });

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
      armed: false
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

    function renderBoard() {
      boardElement.innerHTML = '';
      boardElement.style.setProperty('--minesweeper-columns', gameState.cols);
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
        }
      }
    }

    function initializeGrid(difficultyKey) {
      const presetKey = normalizeDifficultyKey(difficultyKey);
      const preset = DIFFICULTY_PRESETS[presetKey];
      const rows = Math.max(4, Number(preset.rows) || 8);
      const cols = Math.max(4, Number(preset.cols) || 8);
      const mines = clampMines(rows, cols, preset.mines);
      gameState.rows = rows;
      gameState.cols = cols;
      gameState.mineCount = mines;
      gameState.grid = createEmptyGrid(rows, cols);
      gameState.safeRemaining = rows * cols - mines;
      gameState.status = 'ready';
      gameState.armed = false;
      renderBoard();
      for (let row = 0; row < gameState.rows; row += 1) {
        for (let col = 0; col < gameState.cols; col += 1) {
          resetCellVisual(gameState.grid[row][col]);
        }
      }
      updateBoardState('ready');
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

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('i18n:languagechange', () => {
        refreshLabelCache();
      });
    }

    resetGame();
  });
})();
