(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const DEFAULT_LEVEL_CLUE_RANGES = Object.freeze({
    facile: Object.freeze({ min: 30, max: 34 }),
    moyen: Object.freeze({ min: 24, max: 28 }),
    difficile: Object.freeze({ min: 18, max: 22 })
  });

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function getConfiguredClueRange(level) {
    const fallback = DEFAULT_LEVEL_CLUE_RANGES[level] || DEFAULT_LEVEL_CLUE_RANGES.moyen;
    if (!GLOBAL_CONFIG || !GLOBAL_CONFIG.arcade || !GLOBAL_CONFIG.arcade.sudoku) {
      return fallback;
    }
    const ranges = GLOBAL_CONFIG.arcade.sudoku.levelClues;
    if (!ranges || typeof ranges !== 'object') {
      return fallback;
    }
    const entry = ranges[level] || null;
    const min = entry && Number.isFinite(entry.min) ? entry.min : fallback.min;
    const max = entry && Number.isFinite(entry.max) ? entry.max : fallback.max;
    const normalizedMin = Math.max(17, Math.min(min, max));
    const normalizedMax = Math.max(normalizedMin, max);
    return { min: normalizedMin, max: normalizedMax };
  }

  function pickClueCount(level) {
    const range = getConfiguredClueRange(level);
    if (range.min === range.max) {
      return range.min;
    }
    const span = range.max - range.min + 1;
    return range.min + Math.floor(Math.random() * span);
  }

  function createEmptyBoard() {
    return Array.from({ length: 9 }, () => Array(9).fill(0));
  }

  function isValid(board, row, col, value) {
    for (let j = 0; j < 9; j += 1) {
      if (board[row][j] === value) {
        return false;
      }
    }
    for (let i = 0; i < 9; i += 1) {
      if (board[i][col] === value) {
        return false;
      }
    }
    const boxRow = Math.floor(row / 3) * 3;
    const boxCol = Math.floor(col / 3) * 3;
    for (let i = 0; i < 3; i += 1) {
      for (let j = 0; j < 3; j += 1) {
        if (board[boxRow + i][boxCol + j] === value) {
          return false;
        }
      }
    }
    return true;
  }

  function getCandidates(board, row, col) {
    const candidates = [];
    for (let value = 1; value <= 9; value += 1) {
      if (isValid(board, row, col, value)) {
        candidates.push(value);
      }
    }
    return candidates;
  }

  function selectNextCell(board) {
    let bestRow = -1;
    let bestCol = -1;
    let bestCandidates = null;
    for (let row = 0; row < 9; row += 1) {
      for (let col = 0; col < 9; col += 1) {
        if (board[row][col] !== 0) {
          continue;
        }
        const candidates = getCandidates(board, row, col);
        if (candidates.length === 0) {
          return { row, col, candidates };
        }
        if (!bestCandidates || candidates.length < bestCandidates.length) {
          bestRow = row;
          bestCol = col;
          bestCandidates = candidates;
          if (candidates.length === 1) {
            return { row, col, candidates };
          }
        }
      }
    }
    if (!bestCandidates) {
      return null;
    }
    return { row: bestRow, col: bestCol, candidates: bestCandidates };
  }

  function solveBoard(board) {
    const nextCell = selectNextCell(board);
    if (!nextCell) {
      return true;
    }
    const { row, col, candidates } = nextCell;
    if (!candidates.length) {
      return false;
    }
    const options = shuffled(candidates);
    for (let i = 0; i < options.length; i += 1) {
      board[row][col] = options[i];
      if (solveBoard(board)) {
        return true;
      }
      board[row][col] = 0;
    }
    return false;
  }

  function countSolutions(board, limit = 2) {
    const nextCell = selectNextCell(board);
    if (!nextCell) {
      return 1;
    }
    const { row, col, candidates } = nextCell;
    if (!candidates.length) {
      return 0;
    }
    let count = 0;
    const options = shuffled(candidates);
    for (let i = 0; i < options.length; i += 1) {
      board[row][col] = options[i];
      count += countSolutions(board, limit);
      if (count >= limit) {
        board[row][col] = 0;
        return count;
      }
    }
    board[row][col] = 0;
    return count;
  }

  function shuffled(values) {
    const copy = values.slice();
    for (let i = copy.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = copy[i];
      copy[i] = copy[j];
      copy[j] = tmp;
    }
    return copy;
  }

  function generateFullSolution() {
    const board = createEmptyBoard();
    const digits = [1, 2, 3, 4, 5, 6, 7, 8, 9];

    function fillCell(index) {
      if (index === 81) {
        return true;
      }
      const row = Math.floor(index / 9);
      const col = index % 9;
      const options = shuffled(digits);
      for (let i = 0; i < options.length; i += 1) {
        const value = options[i];
        if (isValid(board, row, col, value)) {
          board[row][col] = value;
          if (fillCell(index + 1)) {
            return true;
          }
          board[row][col] = 0;
        }
      }
      return false;
    }

    fillCell(0);
    return board;
  }

  function cloneBoard(board) {
    return board.map(row => row.slice());
  }

  function makePuzzleFromSolution(solution, targetClues) {
    const puzzle = cloneBoard(solution);
    const positions = shuffled(Array.from({ length: 81 }, (_, index) => index));
    let removals = 81 - targetClues;
    for (let i = 0; i < positions.length && removals > 0; i += 1) {
      const index = positions[i];
      const row = Math.floor(index / 9);
      const col = index % 9;
      const backup = puzzle[row][col];
      if (backup === 0) {
        continue;
      }
      puzzle[row][col] = 0;
      const solutionCount = countSolutions(puzzle, 2);
      if (solutionCount !== 1) {
        puzzle[row][col] = backup;
      } else {
        removals -= 1;
      }
    }
    return puzzle;
  }

  function generateRandomPuzzle(level) {
    const solution = generateFullSolution();
    const clues = pickClueCount(level);
    return makePuzzleFromSolution(solution, clues);
  }

  function parseGridToBoard(container) {
    const inputs = container.querySelectorAll('input');
    const board = createEmptyBoard();
    inputs.forEach((input, index) => {
      const row = Math.floor(index / 9);
      const col = index % 9;
      const value = Number.parseInt(input.value, 10);
      board[row][col] = Number.isInteger(value) && value >= 1 && value <= 9 ? value : 0;
    });
    return board;
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key.trim()) {
      return fallback;
    }
    const translator = typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
      ? window.i18n.t
      : typeof window !== 'undefined' && typeof window.t === 'function'
        ? window.t
        : null;
    if (translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string' && result.trim()) {
          return result;
        }
      } catch (error) {
        console.warn('Sudoku translation error for', key, error);
      }
    }
    return fallback;
  }

  function formatStatus(key, fallback, params) {
    return translate(`scripts.arcade.sudoku.status.${key}`, fallback, params);
  }

  onReady(() => {
    const gridElement = document.getElementById('sudokuGrid');
    if (!gridElement) {
      return;
    }

    const statusElement = document.getElementById('sudokuStatus');
    const levelSelect = document.getElementById('sudokuLevel');
    const generateButton = document.getElementById('sudokuGenerate');
    const padValidateButton = document.getElementById('sudokuPadValidate');
    const padElement = document.getElementById('sudokuPad');

    if (!statusElement || !levelSelect || !generateButton || !padValidateButton || !padElement) {
      return;
    }

    const padButtons = Array.from(padElement.querySelectorAll('.sudoku-pad__button[data-value]'));

    let activeInput = null;
    let selectedPadValue = null;

    let currentFixedMask = createEmptyBoard().map(row => row.map(() => false));

    function setStatus(message, kind = 'info') {
      if (!statusElement) {
        return;
      }
      if (typeof message === 'string' && message.trim()) {
        statusElement.textContent = message;
        statusElement.hidden = false;
        statusElement.classList.toggle('sudoku-status--ok', kind === 'ok');
        statusElement.classList.toggle('sudoku-status--error', kind === 'error');
      } else {
        statusElement.textContent = '';
        statusElement.hidden = true;
        statusElement.classList.remove('sudoku-status--ok', 'sudoku-status--error');
      }
    }

    function clearHighlights() {
      gridElement.querySelectorAll('.sudoku-cell').forEach(cell => {
        cell.classList.remove('error', 'ok');
      });
    }

    function loadBoardToGrid(board, fixedMask) {
      gridElement.innerHTML = '';
      activeInput = null;
      currentFixedMask = fixedMask
        ? fixedMask.map(row => row.slice())
        : createEmptyBoard().map(row => row.map(() => false));

      for (let row = 0; row < 9; row += 1) {
        for (let col = 0; col < 9; col += 1) {
          const cell = document.createElement('div');
          cell.className = 'sudoku-cell';
          cell.dataset.r = String(row);
          cell.dataset.c = String(col);
          cell.setAttribute('role', 'gridcell');
          const input = document.createElement('input');
          input.inputMode = 'numeric';
          input.maxLength = 1;
          input.pattern = '[1-9]';
          input.setAttribute('aria-label', translate('index.sections.sudoku.cellLabel', 'Case de Sudoku'));
          const value = board[row][col];
          if (value) {
            input.value = String(value);
          }
          if (currentFixedMask[row][col]) {
            cell.classList.add('fixed');
            input.readOnly = true;
            input.tabIndex = -1;
          }
          input.addEventListener('input', event => {
            const sanitized = event.target.value.replace(/[^1-9]/g, '');
            event.target.value = sanitized.slice(-1);
          });
          input.addEventListener('focus', () => {
            cell.classList.remove('error', 'ok');
          });
          cell.appendChild(input);
          gridElement.appendChild(cell);
        }
      }
    }

    function updatePadSelection() {
      padButtons.forEach(button => {
        button.classList.toggle('is-active', button.dataset.value === selectedPadValue);
      });
    }

    function applySelectionToInput(input, selection) {
      if (!input || input.readOnly || selection === null) {
        return;
      }
      const finalValue = selection === 'clear' ? '' : selection;
      input.value = finalValue;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function validateBoard(board) {
      const errors = [];
      for (let i = 0; i < 9; i += 1) {
        const seenRow = new Map();
        const seenCol = new Map();
        for (let j = 0; j < 9; j += 1) {
          const rowValue = board[i][j];
          if (rowValue) {
            if (seenRow.has(rowValue)) {
              errors.push({ row: i, col: j });
              const prevCol = seenRow.get(rowValue);
              errors.push({ row: i, col: prevCol });
            } else {
              seenRow.set(rowValue, j);
            }
          }
          const colValue = board[j][i];
          if (colValue) {
            if (seenCol.has(colValue)) {
              errors.push({ row: j, col: i });
              const prevRow = seenCol.get(colValue);
              errors.push({ row: prevRow, col: i });
            } else {
              seenCol.set(colValue, j);
            }
          }
        }
      }
      for (let baseRow = 0; baseRow < 9; baseRow += 3) {
        for (let baseCol = 0; baseCol < 9; baseCol += 3) {
          const seen = new Map();
          for (let i = 0; i < 3; i += 1) {
            for (let j = 0; j < 3; j += 1) {
              const row = baseRow + i;
              const col = baseCol + j;
              const value = board[row][col];
              if (!value) {
                continue;
              }
              if (seen.has(value)) {
                errors.push({ row, col });
                const previous = seen.get(value);
                errors.push(previous);
              } else {
                seen.set(value, { row, col });
              }
            }
          }
        }
      }
      return errors;
    }

    function highlightErrors(errors) {
      const cells = gridElement.querySelectorAll('.sudoku-cell');
      errors.forEach(({ row, col }) => {
        const index = row * 9 + col;
        const cell = cells[index];
        if (cell) {
          cell.classList.add('error');
        }
      });
    }

    function onValidate() {
      clearHighlights();
      const board = parseGridToBoard(gridElement);
      const errors = validateBoard(board);
      if (errors.length) {
        highlightErrors(errors);
        setStatus(formatStatus('errors', 'Erreurs détectées : {count}.', { count: errors.length }), 'error');
      } else {
        setStatus(formatStatus('noError', "Aucune erreur pour l'instant."), 'ok');
      }
    }

    function onGenerate() {
      clearHighlights();
      const level = levelSelect.value || 'moyen';
      const levelLabel = levelSelect.options[levelSelect.selectedIndex]
        ? levelSelect.options[levelSelect.selectedIndex].textContent.trim()
        : level;
      setStatus(formatStatus('generating', 'Génération en cours…'));
      const puzzle = generateRandomPuzzle(level);
      const fixedMask = puzzle.map(row => row.map(value => value !== 0));
      loadBoardToGrid(puzzle, fixedMask);
      const clues = puzzle.flat().filter(value => value !== 0).length;
      setStatus(
        formatStatus('generated', 'Grille {level} générée (indices : {clues}).', {
          level: levelLabel,
          clues
        }),
        'ok'
      );
    }

    padButtons.forEach(button => {
      button.addEventListener('click', () => {
        const value = button.dataset.value ?? null;
        const isSameSelection = selectedPadValue === value;
        selectedPadValue = isSameSelection ? null : value;
        updatePadSelection();
        const appliedValue = isSameSelection ? value : selectedPadValue;
        if (activeInput) {
          activeInput.focus();
          applySelectionToInput(activeInput, appliedValue);
        }
      });
    });

    gridElement.addEventListener('focusin', event => {
      if (event.target instanceof HTMLInputElement) {
        activeInput = event.target;
      }
    });

    gridElement.addEventListener('focusout', event => {
      if (activeInput === event.target) {
        activeInput = null;
      }
    });

    gridElement.addEventListener('click', event => {
      const cell = event.target.closest('.sudoku-cell');
      if (!cell) {
        return;
      }
      const input = cell.querySelector('input');
      if (!input || input.readOnly) {
        return;
      }
      input.focus();
      activeInput = input;
      applySelectionToInput(input, selectedPadValue);
    });

    generateButton.addEventListener('click', onGenerate);
    padValidateButton.addEventListener('click', onValidate);

    loadBoardToGrid(createEmptyBoard());
    setStatus('');
  });
})();
