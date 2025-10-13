(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const DIFFICULTY_PRESETS = Object.freeze({
    facile: Object.freeze({ rows: 8, cols: 8, mines: 10 }),
    moyen: Object.freeze({ rows: 12, cols: 12, mines: 24 }),
    difficile: Object.freeze({ rows: 16, cols: 16, mines: 40 })
  });

  const DEFAULT_BOARD_SETTINGS = Object.freeze({
    targetCellSize: 48,
    minCellSize: 32,
    maxCellSize: 72,
    maxRows: 40,
    maxCols: 60
  });

  const BOARD_SETTINGS = (() => {
    if (typeof globalThis === 'undefined') {
      return DEFAULT_BOARD_SETTINGS;
    }
    const source = globalThis.MINESWEEPER_BOARD_SETTINGS;
    if (!source || typeof source !== 'object') {
      return DEFAULT_BOARD_SETTINGS;
    }

    const minSize = Math.max(16, coercePositiveNumber(source.minCellSize, DEFAULT_BOARD_SETTINGS.minCellSize));
    const maxSize = Math.max(minSize, coercePositiveNumber(source.maxCellSize, DEFAULT_BOARD_SETTINGS.maxCellSize));
    const targetSize = Math.min(
      maxSize,
      Math.max(minSize, coercePositiveNumber(source.targetCellSize, DEFAULT_BOARD_SETTINGS.targetCellSize))
    );

    const maxRows = Math.max(4, Math.floor(coercePositiveNumber(source.maxRows, DEFAULT_BOARD_SETTINGS.maxRows)));
    const maxCols = Math.max(4, Math.floor(coercePositiveNumber(source.maxCols, DEFAULT_BOARD_SETTINGS.maxCols)));

    return Object.freeze({
      targetCellSize: targetSize,
      minCellSize: minSize,
      maxCellSize: maxSize,
      maxRows,
      maxCols
    });
  })();

  const DEFAULT_TICKET_REWARD_RATIO = 0.5;

  function clamp(value, min, max) {
    if (!Number.isFinite(value)) {
      return min;
    }
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  function coercePositiveNumber(value, fallback) {
    const parsed = Number.parseFloat(value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return fallback;
    }
    return parsed;
  }

  function getBoardMetrics(boardElement) {
    const viewportWidth = Math.max(
      coercePositiveNumber(window.innerWidth, 1),
      coercePositiveNumber(document.documentElement?.clientWidth, 1),
      1
    );
    const viewportHeight = Math.max(
      coercePositiveNumber(window.innerHeight, 1),
      coercePositiveNumber(document.documentElement?.clientHeight, 1),
      1
    );

    let availableWidth = viewportWidth;
    let availableHeight = viewportHeight;

    const container = boardElement.closest('.minesweeper');
    if (container) {
      const containerRect = container.getBoundingClientRect();
      const containerStyles = window.getComputedStyle(container);
      const paddingX =
        coercePositiveNumber(containerStyles.paddingLeft, 0) +
        coercePositiveNumber(containerStyles.paddingRight, 0);
      const paddingY =
        coercePositiveNumber(containerStyles.paddingTop, 0) +
        coercePositiveNumber(containerStyles.paddingBottom, 0);
      const gap = coercePositiveNumber(containerStyles.gap || containerStyles.rowGap, 0);

      availableWidth = containerRect.width - paddingX;
      availableHeight = containerRect.height - paddingY - gap;

      const controls = container.querySelector('.minesweeper__controls');
      if (controls) {
        availableHeight -= controls.getBoundingClientRect().height;
      }
    }

    const boardRect = boardElement.getBoundingClientRect();
    if (!Number.isFinite(availableWidth) || availableWidth <= 0) {
      availableWidth = viewportWidth;
    }
    if (!Number.isFinite(availableHeight) || availableHeight <= 0) {
      availableHeight = viewportHeight - boardRect.top;
    }

    availableWidth = Math.max(availableWidth, 240);
    availableHeight = Math.max(availableHeight, viewportHeight - boardRect.top - 24, 240);

    return {
      width: availableWidth,
      height: availableHeight,
      ratio: availableWidth / availableHeight,
      orientation: availableWidth >= availableHeight ? 'landscape' : 'portrait'
    };
  }

  function computeGridDimensions(preset, metrics) {
    const baseRows = Math.max(4, Number(preset.rows) || 8);
    const baseCols = Math.max(4, Number(preset.cols) || 8);
    const baseArea = Math.max(16, baseRows * baseCols);
    const width = Math.max(coercePositiveNumber(metrics.width, 1), 1);
    const height = Math.max(coercePositiveNumber(metrics.height, 1), 1);
    const targetRatio = clamp(metrics.ratio || width / height, 0.35, 3.5);

    const minRows = Math.max(
      4,
      Math.min(
        BOARD_SETTINGS.maxRows,
        Math.floor(height / Math.max(BOARD_SETTINGS.maxCellSize, 1)) || 0
      )
    );
    const maxRows = Math.max(
      minRows,
      Math.min(
        BOARD_SETTINGS.maxRows,
        Math.floor(height / Math.max(BOARD_SETTINGS.minCellSize, 1)) || 0
      )
    );
    const minCols = Math.max(
      4,
      Math.min(
        BOARD_SETTINGS.maxCols,
        Math.floor(width / Math.max(BOARD_SETTINGS.maxCellSize, 1)) || 0
      )
    );
    const maxCols = Math.max(
      minCols,
      Math.min(
        BOARD_SETTINGS.maxCols,
        Math.floor(width / Math.max(BOARD_SETTINGS.minCellSize, 1)) || 0
      )
    );

    const candidateRows = new Set([baseRows]);
    for (let rows = minRows; rows <= maxRows; rows += 1) {
      candidateRows.add(rows);
    }

    const baseColsCandidate = clamp(baseCols, 4, BOARD_SETTINGS.maxCols);
    const candidateCols = new Set([baseColsCandidate]);
    for (let cols = minCols; cols <= maxCols; cols += 1) {
      candidateCols.add(cols);
    }

    let best = {
      rows: clamp(baseRows, 4, BOARD_SETTINGS.maxRows),
      cols: baseColsCandidate,
      score: Number.POSITIVE_INFINITY
    };

    candidateRows.forEach((rows) => {
      const normalizedRows = clamp(Math.round(rows), 4, BOARD_SETTINGS.maxRows);
      const idealCols = clamp(Math.round(normalizedRows * targetRatio), 4, BOARD_SETTINGS.maxCols);
      for (let delta = -2; delta <= 2; delta += 1) {
        candidateCols.add(clamp(idealCols + delta, 4, BOARD_SETTINGS.maxCols));
      }

      candidateCols.forEach((colsCandidate) => {
        const normalizedCols = clamp(Math.round(colsCandidate), 4, BOARD_SETTINGS.maxCols);
        if (normalizedCols < 4) {
          return;
        }

        const totalCells = normalizedRows * normalizedCols;
        const ratio = normalizedCols / normalizedRows;
        const cellWidth = width / normalizedCols;
        const cellHeight = height / normalizedRows;
        const effectiveSize = Math.min(cellWidth, cellHeight);

        const ratioScore = Math.abs(ratio - targetRatio);
        const areaScore = Math.abs(totalCells - baseArea) / baseArea;
        const sizeScore = Math.abs(effectiveSize - BOARD_SETTINGS.targetCellSize) / BOARD_SETTINGS.targetCellSize;
        const minSizePenalty = effectiveSize < BOARD_SETTINGS.minCellSize
          ? (BOARD_SETTINGS.minCellSize - effectiveSize) / BOARD_SETTINGS.minCellSize
          : 0;
        const maxSizePenalty = effectiveSize > BOARD_SETTINGS.maxCellSize
          ? (effectiveSize - BOARD_SETTINGS.maxCellSize) / BOARD_SETTINGS.maxCellSize
          : 0;

        const score = ratioScore * 4 + sizeScore * 3 + areaScore * 1.5 + (minSizePenalty + maxSizePenalty) * 10;
        if (score < best.score) {
          best = { rows: normalizedRows, cols: normalizedCols, score };
        }
      });
    });

    return { rows: best.rows, cols: best.cols };
  }

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
      armed: false,
      metrics: null
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

    function applyBoardMetrics() {
      if (!gameState.metrics) {
        return;
      }
      const { width, height } = gameState.metrics;
      boardElement.style.setProperty('--minesweeper-board-width', `${Math.round(width)}px`);
      boardElement.style.setProperty('--minesweeper-board-height', `${Math.round(height)}px`);
      boardElement.style.width = `${Math.round(width)}px`;
      boardElement.style.height = `${Math.round(height)}px`;
      boardElement.style.maxWidth = '100%';
      boardElement.style.maxHeight = '100%';
    }

    function renderBoard() {
      boardElement.innerHTML = '';
      boardElement.style.setProperty('--minesweeper-columns', gameState.cols);
      boardElement.style.setProperty('--minesweeper-rows', gameState.rows);
      applyBoardMetrics();
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
      const metrics = getBoardMetrics(boardElement);
      const { rows, cols } = computeGridDimensions(preset, metrics);
      const baseArea = Math.max(preset.rows * preset.cols, 1);
      const density = preset.mines / baseArea;
      const desiredMines = Math.round(density * rows * cols);
      const mines = clampMines(rows, cols, desiredMines);
      gameState.rows = rows;
      gameState.cols = cols;
      gameState.mineCount = mines;
      gameState.grid = createEmptyGrid(rows, cols);
      gameState.safeRemaining = rows * cols - mines;
      gameState.status = 'ready';
      gameState.armed = false;
      gameState.metrics = metrics;
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

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('i18n:languagechange', () => {
        refreshLabelCache();
      });
    }

    resetGame();
  });
})();
