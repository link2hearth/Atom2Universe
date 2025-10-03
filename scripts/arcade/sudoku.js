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
    const puzzle = makePuzzleFromSolution(solution, clues);
    return {
      puzzle,
      solution
    };
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
    const checkButton = document.getElementById('sudokuCheck');
    const padValidateButton = document.getElementById('sudokuPadValidate');
    const padElement = document.getElementById('sudokuPad');

    if (
      !statusElement ||
      !levelSelect ||
      !generateButton ||
      !padValidateButton ||
      !padElement ||
      !checkButton
    ) {
      return;
    }

    const padButtons = Array.from(padElement.querySelectorAll('.sudoku-pad__button[data-value]'));
    const conflictToggleButton = document.createElement('button');
    conflictToggleButton.type = 'button';
    conflictToggleButton.className = 'sudoku-status__action sudoku-status__action--conflicts';

    let activeInput = null;
    let selectedPadValue = null;

    let currentFixedMask = createEmptyBoard().map(row => row.map(() => false));
    let solutionBoard = createEmptyBoard();
    let currentLevel = levelSelect.value || 'moyen';
    let showMistakes = currentLevel === 'facile';
    let showConflicts = currentLevel === 'facile';
    let allowMistakeHints = currentLevel === 'facile';
    let manualMistakeReveal = false;
    let lastMistakeSet = new Set();
    let lastMistakeCount = 0;
    let lastConflictSet = new Set();
    let lastConflictCount = 0;
    let lastStatusMessage = '';
    let lastStatusKind = 'info';
    let lastStatusOptionsBuilder = null;
    let lastStatusMessageBuilder = null;
    let lastStatusMeta = {};
    let lastStatusIsMistakeMessage = false;

    function updateConflictButtonState() {
      const isEasyLevel = currentLevel === 'facile';
      conflictToggleButton.hidden = !isEasyLevel;
      if (!isEasyLevel) {
        return;
      }
      const labelKey = showConflicts ? 'hideConflicts' : 'showConflicts';
      const fallback = showConflicts ? 'Masquer les conflits' : 'Afficher les conflits';
      const label = formatStatus(labelKey, fallback);
      conflictToggleButton.textContent = label;
      conflictToggleButton.setAttribute('aria-label', label);
      conflictToggleButton.setAttribute('aria-pressed', String(showConflicts));
      const shouldDisable = lastConflictCount === 0 && !showConflicts;
      conflictToggleButton.disabled = shouldDisable;
      conflictToggleButton.classList.toggle('is-disabled', shouldDisable);
    }

    conflictToggleButton.addEventListener('click', () => {
      if (currentLevel !== 'facile') {
        return;
      }
      if (lastConflictCount === 0 && !showConflicts) {
        return;
      }
      showConflicts = !showConflicts;
      updateConflictButtonState();
      refreshConflictHighlights();
      refreshStatus();
    });

    function refreshStatus() {
      if (lastStatusMeta.isConflictMessage && lastConflictCount === 0) {
        if (lastMistakeCount > 0) {
          const messageBuilder = () =>
            formatStatus('mistakes', 'Cases incorrectes : {count}.', { count: lastMistakeCount });
          const severity = currentLevel === 'facile' ? 'error' : 'info';
          setStatus(messageBuilder, severity, buildMistakeStatusOptions, {
            isMistakeMessage: true,
            isConflictMessage: false
          });
        } else {
          setStatus(formatStatus('noError', "Aucune erreur pour l'instant."), 'ok');
        }
        return;
      }
      if (lastStatusIsMistakeMessage && lastMistakeCount === 0) {
        setStatus(formatStatus('noError', "Aucune erreur pour l'instant."), 'ok');
        return;
      }
      if (typeof lastStatusMessageBuilder === 'function') {
        setStatus(lastStatusMessageBuilder, lastStatusKind, lastStatusOptionsBuilder, lastStatusMeta);
        return;
      }
      if (typeof lastStatusMessage !== 'string' || !lastStatusMessage.trim()) {
        setStatus('');
        return;
      }
      setStatus(lastStatusMessage, lastStatusKind, lastStatusOptionsBuilder, lastStatusMeta);
    }

    function setStatus(message, kind = 'info', optionsBuilder = null, meta = null) {
      if (!statusElement) {
        return;
      }
      const resolvedMessage = typeof message === 'function' ? message() : message;
      lastStatusMessageBuilder = typeof message === 'function' ? message : null;
      lastStatusMessage = typeof resolvedMessage === 'string' ? resolvedMessage : '';
      lastStatusKind = kind;
      lastStatusOptionsBuilder = optionsBuilder;
      lastStatusMeta = meta && typeof meta === 'object' ? { ...meta } : {};
      lastStatusIsMistakeMessage = Boolean(lastStatusMeta.isMistakeMessage);

      if (currentLevel !== 'facile') {
        statusElement.hidden = true;
        statusElement.classList.remove('sudoku-status--ok', 'sudoku-status--error');
        statusElement.replaceChildren();
        return;
      }

      const hasMessage = typeof resolvedMessage === 'string' && resolvedMessage.trim().length > 0;
      statusElement.hidden = false;
      statusElement.classList.toggle('sudoku-status--ok', hasMessage && kind === 'ok');
      statusElement.classList.toggle('sudoku-status--error', hasMessage && kind === 'error');
      if (!hasMessage) {
        statusElement.classList.remove('sudoku-status--ok', 'sudoku-status--error');
      }
      statusElement.replaceChildren();

      if (hasMessage) {
        const messageSpan = document.createElement('span');
        messageSpan.className = 'sudoku-status__message';
        messageSpan.textContent = resolvedMessage;
        statusElement.appendChild(messageSpan);
      }

      const options = typeof optionsBuilder === 'function' ? optionsBuilder() : optionsBuilder;
      if (options && options.button) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'sudoku-status__action';
        button.textContent = options.button.label;
        if (options.button.ariaLabel) {
          button.setAttribute('aria-label', options.button.ariaLabel);
        }
        if (typeof options.button.pressed === 'boolean') {
          button.setAttribute('aria-pressed', String(options.button.pressed));
        }
        if (typeof options.button.disabled === 'boolean') {
          button.disabled = options.button.disabled;
          button.classList.toggle('is-disabled', options.button.disabled);
        }
        if (typeof options.button.onClick === 'function') {
          button.addEventListener('click', event => {
            options.button.onClick(event);
          });
        }
        statusElement.appendChild(button);
      }

      updateConflictButtonState();
      if (!conflictToggleButton.hidden) {
        statusElement.appendChild(conflictToggleButton);
      }
    }

    function updateCheckButtonVisibility() {
      const isEasyLevel = currentLevel === 'facile';
      checkButton.hidden = isEasyLevel;
      checkButton.disabled = isEasyLevel;
    }

    function clearHighlights() {
      gridElement.querySelectorAll('.sudoku-cell').forEach(cell => {
        cell.classList.remove('error', 'ok');
      });
      lastConflictSet = new Set();
      lastConflictCount = 0;
    }

    function refreshMistakeVisibility() {
      const cells = gridElement.querySelectorAll('.sudoku-cell');
      cells.forEach((cell, index) => {
        const isMistake = lastMistakeSet.has(index);
        cell.classList.toggle('mistake', showMistakes && isMistake);
      });
    }

    function refreshConflictHighlights() {
      const cells = gridElement.querySelectorAll('.sudoku-cell');
      cells.forEach((cell, index) => {
        const isConflict = lastConflictSet.has(index);
        cell.classList.toggle('error', showConflicts && isConflict);
      });
    }

    function getMistakePositions(board) {
      if (!Array.isArray(board) || board.length !== 9) {
        return [];
      }
      if (!Array.isArray(solutionBoard) || solutionBoard.length !== 9) {
        return [];
      }
      const mistakes = [];
      for (let row = 0; row < 9; row += 1) {
        for (let col = 0; col < 9; col += 1) {
          if (currentFixedMask[row][col]) {
            continue;
          }
          const value = board[row][col];
          if (!value) {
            continue;
          }
          const expected = solutionBoard[row] ? solutionBoard[row][col] : 0;
          if (expected && expected !== value) {
            mistakes.push({ row, col });
          }
        }
      }
      return mistakes;
    }

    function updateMistakeHighlights(board) {
      const workingBoard = board || parseGridToBoard(gridElement);
      const mistakes = getMistakePositions(workingBoard);
      lastMistakeCount = mistakes.length;
      lastMistakeSet = new Set(mistakes.map(({ row, col }) => row * 9 + col));
      refreshMistakeVisibility();
      return { workingBoard, mistakes };
    }

    function updateConflictState(board, conflicts) {
      const workingBoard = board || parseGridToBoard(gridElement);
      const entries = Array.isArray(conflicts) ? conflicts : validateBoard(workingBoard);
      const indices = new Set();
      entries.forEach(({ row, col }) => {
        if (Number.isInteger(row) && Number.isInteger(col)) {
          indices.add(row * 9 + col);
        }
      });
      lastConflictSet = indices;
      lastConflictCount = indices.size;
      refreshConflictHighlights();
      updateConflictButtonState();
      return entries;
    }

    function buildMistakeStatusOptions() {
      if (!allowMistakeHints) {
        return null;
      }
      if (!lastMistakeCount) {
        return null;
      }
      return {
        button: {
          label: formatStatus(
            showMistakes ? 'hideMistakes' : 'showMistakes',
            showMistakes ? 'Masquer les cases incorrectes' : 'Afficher les cases incorrectes'
          ),
          pressed: showMistakes,
          onClick: () => {
            showMistakes = !showMistakes;
            refreshMistakeVisibility();
            refreshStatus();
          }
        }
      };
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
            const { workingBoard } = updateMistakeHighlights();
            if (currentLevel !== 'facile' && manualMistakeReveal) {
              manualMistakeReveal = false;
              showMistakes = false;
              refreshMistakeVisibility();
            }
            updateConflictState(workingBoard);
            refreshStatus();
          });
          input.addEventListener('focus', () => {
            cell.classList.remove('error', 'ok');
          });
          cell.appendChild(input);
          gridElement.appendChild(cell);
        }
      }

      const { workingBoard } = updateMistakeHighlights(board);
      updateConflictState(workingBoard);
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

    function onValidate() {
      clearHighlights();
      const board = parseGridToBoard(gridElement);
      const { mistakes } = updateMistakeHighlights(board);
      const conflicts = updateConflictState(board, validateBoard(board));

      if (conflicts.length || mistakes.length) {
        const hasConflicts = conflicts.length > 0;
        const messageBuilder = () =>
          formatStatus(
            hasConflicts ? 'errors' : 'mistakes',
            hasConflicts ? 'Erreurs détectées : {count}.' : 'Cases incorrectes : {count}.',
            { count: hasConflicts ? lastConflictCount : lastMistakeCount }
          );
        const severity = currentLevel === 'facile' ? 'error' : 'info';
        const optionsBuilder = hasConflicts ? null : buildMistakeStatusOptions;
        setStatus(messageBuilder, severity, optionsBuilder, {
          isMistakeMessage: !hasConflicts,
          isConflictMessage: hasConflicts
        });
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
      currentLevel = level;
      showMistakes = level === 'facile';
      allowMistakeHints = level === 'facile';
      showConflicts = level === 'facile';
      manualMistakeReveal = false;
      lastMistakeSet = new Set();
      lastMistakeCount = 0;
      lastConflictSet = new Set();
      lastConflictCount = 0;
      updateConflictButtonState();
      updateCheckButtonVisibility();
      setStatus(formatStatus('generating', 'Génération en cours…'));
      const { puzzle, solution } = generateRandomPuzzle(level);
      solutionBoard = cloneBoard(solution);
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
      refreshMistakeVisibility();
      refreshConflictHighlights();
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

    checkButton.addEventListener('click', () => {
      if (currentLevel === 'facile') {
        return;
      }
      const board = parseGridToBoard(gridElement);
      const { mistakes } = updateMistakeHighlights(board);
      manualMistakeReveal = mistakes.length > 0;
      showMistakes = manualMistakeReveal;
      refreshMistakeVisibility();
      updateConflictState(board, validateBoard(board));
      refreshStatus();
    });

    generateButton.addEventListener('click', onGenerate);
    padValidateButton.addEventListener('click', onValidate);

    loadBoardToGrid(createEmptyBoard());
    updateConflictButtonState();
    updateCheckButtonVisibility();
    setStatus('');
  });
})();
