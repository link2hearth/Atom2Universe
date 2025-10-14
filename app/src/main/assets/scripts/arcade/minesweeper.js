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

  function parseCssNumber(value, fallback = 0) {
    if (typeof value !== 'string') {
      return fallback;
    }
    const parsed = Number.parseFloat(value);
    if (!Number.isFinite(parsed)) {
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

    const boardRect = boardElement.getBoundingClientRect();
    const container = boardElement.closest('.minesweeper');

    let availableWidth = viewportWidth;
    let availableHeight = Math.max(viewportHeight - Math.max(boardRect.top, 0) - 24, 120);
    let paddingLeft = 0;
    let paddingRight = 0;
    let paddingTop = 0;
    let paddingBottom = 0;
    let rowGap = 0;
    let columnGap = 0;

    if (container) {
      const containerRect = container.getBoundingClientRect();
      const containerStyles = window.getComputedStyle(container);
      const containerPaddingLeft = parseCssNumber(containerStyles.paddingLeft, 0);
      const containerPaddingRight = parseCssNumber(containerStyles.paddingRight, 0);
      const containerPaddingTop = parseCssNumber(containerStyles.paddingTop, 0);
      const containerPaddingBottom = parseCssNumber(containerStyles.paddingBottom, 0);
      const containerGap = parseCssNumber(containerStyles.rowGap || containerStyles.gap, 0);

      availableWidth = Math.min(
        viewportWidth - 32,
        containerRect.width - containerPaddingLeft - containerPaddingRight
      );

      const controls = container.querySelector('.minesweeper__controls');
      const controlsHeight = controls ? controls.getBoundingClientRect().height : 0;
      const topOffset = Math.max(containerRect.top, 0);
      availableHeight = Math.min(
        Math.max(
          viewportHeight - topOffset - controlsHeight - containerPaddingTop - containerPaddingBottom - 32,
          0
        ),
        containerRect.height - containerPaddingTop - containerPaddingBottom - controlsHeight - containerGap
      );
      availableHeight = Math.max(availableHeight, 0);
    } else {
      availableWidth = Math.max(Math.min(viewportWidth - 32, boardRect.width || viewportWidth), 0);
    }

    if (!Number.isFinite(availableWidth) || availableWidth <= 0) {
      availableWidth = Math.max(viewportWidth - 32, 160);
    }
    if (!Number.isFinite(availableHeight) || availableHeight <= 0) {
      availableHeight = Math.max(viewportHeight - Math.max(boardRect.top, 0) - 32, 120);
    }

    if (typeof window !== 'undefined' && typeof window.getComputedStyle === 'function') {
      const boardStyles = window.getComputedStyle(boardElement);
      paddingLeft = parseCssNumber(boardStyles.paddingLeft, 0);
      paddingRight = parseCssNumber(boardStyles.paddingRight, 0);
      paddingTop = parseCssNumber(boardStyles.paddingTop, 0);
      paddingBottom = parseCssNumber(boardStyles.paddingBottom, 0);
      rowGap = parseCssNumber(boardStyles.rowGap, parseCssNumber(boardStyles.gap, 0));
      columnGap = parseCssNumber(boardStyles.columnGap, parseCssNumber(boardStyles.gap, 0));
    }

    const innerWidth = Math.max(availableWidth - paddingLeft - paddingRight, 1);
    const innerHeight = Math.max(availableHeight - paddingTop - paddingBottom, 1);

    return {
      width: availableWidth,
      height: availableHeight,
      innerWidth,
      innerHeight,
      ratio: innerWidth / innerHeight,
      orientation: availableWidth >= availableHeight ? 'landscape' : 'portrait',
      paddingLeft,
      paddingRight,
      paddingTop,
      paddingBottom,
      rowGap,
      columnGap
    };
  }

  function computeGridDimensions(preset, metrics) {
    const baseRows = clamp(Math.round(Number(preset.rows) || 8), 4, BOARD_SETTINGS.maxRows);
    const baseCols = clamp(Math.round(Number(preset.cols) || 8), 4, BOARD_SETTINGS.maxCols);
    const ratio = baseCols / baseRows || 1;
    const width = Math.max(coercePositiveNumber(metrics.innerWidth || metrics.width, 1), 1);
    const height = Math.max(coercePositiveNumber(metrics.innerHeight || metrics.height, 1), 1);
    const columnGap = Math.max(0, metrics.columnGap || 0);
    const rowGap = Math.max(0, metrics.rowGap || 0);

    const denominatorCols = BOARD_SETTINGS.minCellSize + columnGap;
    const denominatorRows = BOARD_SETTINGS.minCellSize + rowGap;
    const maxColsBySpace = denominatorCols > 0 ? Math.floor((width + columnGap) / denominatorCols) : BOARD_SETTINGS.maxCols;
    const maxRowsBySpace = denominatorRows > 0 ? Math.floor((height + rowGap) / denominatorRows) : BOARD_SETTINGS.maxRows;
    const maxCols = clamp(maxColsBySpace || 0, 4, BOARD_SETTINGS.maxCols);
    const maxRows = clamp(maxRowsBySpace || 0, 4, BOARD_SETTINGS.maxRows);

    const maxCellDenominatorCols = BOARD_SETTINGS.maxCellSize + columnGap;
    const maxCellDenominatorRows = BOARD_SETTINGS.maxCellSize + rowGap;
    const minColsBySpace = maxCellDenominatorCols > 0 ? Math.floor((width + columnGap) / maxCellDenominatorCols) : 0;
    const minRowsBySpace = maxCellDenominatorRows > 0 ? Math.floor((height + rowGap) / maxCellDenominatorRows) : 0;
    const minCols = clamp(minColsBySpace || 0, 4, maxCols);
    const minRows = clamp(minRowsBySpace || 0, 4, maxRows);

    const baseWidth = baseCols * BOARD_SETTINGS.targetCellSize;
    const baseHeight = baseRows * BOARD_SETTINGS.targetCellSize;
    const widthScale = baseWidth > 0 ? width / baseWidth : 1;
    const heightScale = baseHeight > 0 ? height / baseHeight : 1;
    const approxScale = clamp(Math.min(widthScale, heightScale), 0.25, 4);

    let rows = clamp(Math.round(baseRows * approxScale), minRows, maxRows);
    rows = clamp(rows, minRows, maxRows);
    let cols = clamp(Math.round(rows * ratio), minCols, maxCols);

    const evaluateLayout = (candidateRows, candidateCols) => {
      const normalizedRows = clamp(Math.round(candidateRows), minRows, maxRows);
      const normalizedCols = clamp(Math.round(candidateCols), minCols, maxCols);
      const layout = computeBoardLayout(normalizedRows, normalizedCols, metrics);
      return { rows: normalizedRows, cols: normalizedCols, layout };
    };

    let evaluation = evaluateLayout(rows, cols);

    while (
      (evaluation.layout.width - metrics.width > 1 ||
        evaluation.layout.height - metrics.height > 1 ||
        evaluation.layout.cellSize + 0.5 < BOARD_SETTINGS.minCellSize) &&
      evaluation.rows > 4 &&
      evaluation.cols > 4
    ) {
      rows = clamp(evaluation.rows - 1, minRows, maxRows);
      cols = clamp(Math.round(rows * ratio), minCols, maxCols);
      evaluation = evaluateLayout(rows, cols);
    }

    let expanded = true;
    while (expanded) {
      expanded = false;
      const nextRows = evaluation.rows + 1;
      const nextCols = Math.round(nextRows * ratio);
      if (nextRows > maxRows || nextCols > maxCols) {
        break;
      }
      const nextEvaluation = evaluateLayout(nextRows, nextCols);
      if (
        nextEvaluation.layout.cellSize + 0.5 >= BOARD_SETTINGS.minCellSize &&
        nextEvaluation.layout.width <= metrics.width + 1 &&
        nextEvaluation.layout.height <= metrics.height + 1
      ) {
        evaluation = nextEvaluation;
        expanded = true;
      }
    }

    return evaluation;
  }

  function computeBoardLayout(rows, cols, metrics) {
    const safeRows = Math.max(1, Math.floor(rows));
    const safeCols = Math.max(1, Math.floor(cols));
    const availableWidth = Math.max(coercePositiveNumber(metrics?.width, 1), 1);
    const availableHeight = Math.max(coercePositiveNumber(metrics?.height, 1), 1);
    const paddingLeft = Math.max(0, metrics?.paddingLeft || 0);
    const paddingRight = Math.max(0, metrics?.paddingRight || 0);
    const paddingTop = Math.max(0, metrics?.paddingTop || 0);
    const paddingBottom = Math.max(0, metrics?.paddingBottom || 0);
    const rowGap = Math.max(0, metrics?.rowGap || 0);
    const columnGap = Math.max(0, metrics?.columnGap || 0);

    const horizontalPadding = paddingLeft + paddingRight;
    const verticalPadding = paddingTop + paddingBottom;
    const horizontalGaps = columnGap * Math.max(safeCols - 1, 0);
    const verticalGaps = rowGap * Math.max(safeRows - 1, 0);

    const usableWidth = Math.max(availableWidth - horizontalPadding - horizontalGaps, 0);
    const usableHeight = Math.max(availableHeight - verticalPadding - verticalGaps, 0);

    let cellSize = Math.min(
      usableWidth / safeCols || 0,
      usableHeight / safeRows || 0,
      BOARD_SETTINGS.maxCellSize
    );
    if (!Number.isFinite(cellSize) || cellSize <= 0) {
      const fallbackWidth = Math.max(availableWidth - horizontalPadding - horizontalGaps, 0);
      const fallbackHeight = Math.max(availableHeight - verticalPadding - verticalGaps, 0);
      cellSize = Math.min(
        fallbackWidth / safeCols || 0,
        fallbackHeight / safeRows || 0,
        BOARD_SETTINGS.targetCellSize
      );
    }
    if (!Number.isFinite(cellSize) || cellSize <= 0) {
      const widthBound = availableWidth / safeCols || 0;
      const heightBound = availableHeight / safeRows || 0;
      cellSize = Math.max(
        0,
        Math.min(BOARD_SETTINGS.minCellSize, BOARD_SETTINGS.maxCellSize, widthBound, heightBound)
      );
    }

    let boardWidth = horizontalPadding + horizontalGaps + cellSize * safeCols;
    let boardHeight = verticalPadding + verticalGaps + cellSize * safeRows;

    if (boardWidth > availableWidth || boardHeight > availableHeight) {
      const widthLimit = Math.max(availableWidth - horizontalPadding - horizontalGaps, 0) / safeCols;
      const heightLimit = Math.max(availableHeight - verticalPadding - verticalGaps, 0) / safeRows;
      const limitedCellSize = Math.min(widthLimit || 0, heightLimit || 0);
      if (Number.isFinite(limitedCellSize) && limitedCellSize > 0) {
        cellSize = Math.min(cellSize, limitedCellSize);
        boardWidth = horizontalPadding + horizontalGaps + cellSize * safeCols;
        boardHeight = verticalPadding + verticalGaps + cellSize * safeRows;
      } else {
        boardWidth = Math.max(availableWidth, 0);
        boardHeight = Math.max(availableHeight, 0);
      }
    }

    return {
      width: boardWidth,
      height: boardHeight,
      cellSize
    };
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
      metrics: null,
      difficulty: 'moyen'
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
      const layout = gameState.metrics.layout || {};
      const width = Number.isFinite(layout.width) ? layout.width : gameState.metrics.width;
      const height = Number.isFinite(layout.height) ? layout.height : gameState.metrics.height;
      const resolvedWidth = Number.isFinite(width) ? Math.max(0, Math.round(width)) : null;
      const resolvedHeight = Number.isFinite(height) ? Math.max(0, Math.round(height)) : null;
      const cellSize = Number.isFinite(layout.cellSize) ? Math.max(0, layout.cellSize) : null;

      if (resolvedWidth != null) {
        boardElement.style.setProperty('--minesweeper-board-width', `${resolvedWidth}px`);
        boardElement.style.width = `${resolvedWidth}px`;
      } else {
        boardElement.style.removeProperty('--minesweeper-board-width');
        boardElement.style.removeProperty('width');
      }

      if (resolvedHeight != null) {
        boardElement.style.setProperty('--minesweeper-board-height', `${resolvedHeight}px`);
        boardElement.style.height = `${resolvedHeight}px`;
      } else {
        boardElement.style.removeProperty('--minesweeper-board-height');
        boardElement.style.removeProperty('height');
      }

      if (cellSize != null) {
        boardElement.style.setProperty('--minesweeper-cell-size', `${cellSize}px`);
      } else {
        boardElement.style.removeProperty('--minesweeper-cell-size');
      }

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
      const { rows, cols, layout } = computeGridDimensions(preset, metrics);
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
      gameState.metrics = { ...metrics, layout };
      gameState.difficulty = presetKey;
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

    function recalculateBoardMetrics() {
      if (!gameState.rows || !gameState.cols) {
        return;
      }
      const metrics = getBoardMetrics(boardElement);
      const preset = DIFFICULTY_PRESETS[gameState.difficulty];
      if (preset) {
        const evaluation = computeGridDimensions(preset, metrics);
        if (evaluation.rows !== gameState.rows || evaluation.cols !== gameState.cols) {
          initializeGrid(gameState.difficulty);
          return;
        }
        gameState.metrics = { ...metrics, layout: evaluation.layout };
        applyBoardMetrics();
        return;
      }
      const layout = computeBoardLayout(gameState.rows, gameState.cols, metrics);
      gameState.metrics = { ...metrics, layout };
      applyBoardMetrics();
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
      window.addEventListener('resize', () => {
        recalculateBoardMetrics();
      });
    }

    if (typeof ResizeObserver !== 'undefined') {
      const observerTarget = boardElement.closest('.minesweeper') || boardElement;
      try {
        const observer = new ResizeObserver(() => {
          recalculateBoardMetrics();
        });
        observer.observe(observerTarget);
      } catch (error) {
        /* noop */
      }
    }

    resetGame();
  });
})();
