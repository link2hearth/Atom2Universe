(function () {
  'use strict';

  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;

  if (typeof document === 'undefined') {
    return;
  }

  const SECTION_ID = 'echecs';
  const BOARD_SELECTOR = '[data-chess-board]';
  const STATUS_SELECTOR = '[data-chess-status]';
  const OUTCOME_SELECTOR = '[data-chess-outcome]';
  const HELPER_SELECTOR = '[data-chess-helper]';
  const PROMOTION_SELECTOR = '[data-chess-promotion]';
  const PROMOTION_OPTIONS_SELECTOR = '[data-chess-promotion-options]';
  const COORDINATES_TOGGLE_SELECTOR = '[data-chess-toggle-coordinates]';
  const HISTORY_TOGGLE_SELECTOR = '[data-chess-toggle-history]';
  const HISTORY_CONTAINER_SELECTOR = '[data-chess-history]';
  const HISTORY_LIST_SELECTOR = '[data-chess-history-list]';
  const HISTORY_EMPTY_SELECTOR = '[data-chess-history-empty]';
  const RESET_BUTTON_SELECTOR = '[data-chess-reset]';
  const ANALYZE_BUTTON_SELECTOR = '[data-chess-analyze]';
  const ANALYSIS_CONTAINER_SELECTOR = '[data-chess-analysis]';
  const ANALYSIS_TEXT_SELECTOR = '[data-chess-analysis-text]';
  const DIFFICULTY_SELECT_SELECTOR = '[data-chess-difficulty]';
  const DIFFICULTY_DESCRIPTION_SELECTOR = '[data-chess-difficulty-description]';

  const LOCAL_STORAGE_KEY = 'atom2univers.arcade.echecs';
  const POINTER_DRAG_THRESHOLD = 6;

  const BOARD_SIZE = 8;
  const WHITE = 'w';
  const BLACK = 'b';

  const FILES = Object.freeze(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']);
  const RANKS = Object.freeze(['8', '7', '6', '5', '4', '3', '2', '1']);
  const PROMOTION_CHOICES = Object.freeze(['q', 'r', 'b', 'n']);
  const PROMOTION_OPTION_KEYS = Object.freeze({
    q: 'queen',
    r: 'rook',
    b: 'bishop',
    n: 'knight'
  });

  const DEFAULT_AI_SETTINGS = Object.freeze({
    depth: 3,
    timeLimitMs: 1200,
    moveDelayMs: 150,
    transpositionSize: 4000
  });

  const DEFAULT_DIFFICULTY_MODES = Object.freeze([
    Object.freeze({
      id: 'training',
      labelKey: 'index.sections.echecs.difficulty.training',
      fallbackLabel: 'Mode entraînement',
      descriptionKey: 'index.sections.echecs.difficulty.trainingDescription',
      fallbackDescription: 'Recherche plus courte et coups rapides pour tester librement le plateau.',
      ai: Object.freeze({ depth: 2, timeLimitMs: 600, moveDelayMs: 120 }),
      reward: Object.freeze({ offlineSeconds: 600, offlineMultiplier: 1 })
    }),
    Object.freeze({
      id: 'standard',
      labelKey: 'index.sections.echecs.difficulty.standard',
      fallbackLabel: 'Mode standard',
      descriptionKey: 'index.sections.echecs.difficulty.standardDescription',
      fallbackDescription: 'Configuration équilibrée : IA réactive et coups stables.',
      ai: Object.freeze({ depth: 3, timeLimitMs: 1200, moveDelayMs: 150 }),
      reward: Object.freeze({ offlineSeconds: 1200, offlineMultiplier: 1.5 })
    }),
    Object.freeze({
      id: 'expert',
      labelKey: 'index.sections.echecs.difficulty.expert',
      fallbackLabel: 'Mode expert',
      descriptionKey: 'index.sections.echecs.difficulty.expertDescription',
      fallbackDescription: 'Profondeur accrue et réflexion prolongée pour un défi soutenu.',
      ai: Object.freeze({ depth: 4, timeLimitMs: 1800, moveDelayMs: 180 }),
      reward: Object.freeze({ offlineSeconds: 1800, offlineMultiplier: 2 })
    })
  ]);

  const DEFAULT_DIFFICULTY_CONFIG = Object.freeze({
    defaultMode: 'standard',
    modes: DEFAULT_DIFFICULTY_MODES
  });

  const DEFAULT_MATCH_LIMIT = 80;

  const AI_TEST_POSITIONS_URL = 'resources/chess/ai-test-positions.json';
  const FALLBACK_AI_TEST_POSITIONS = Object.freeze([
    Object.freeze({
      id: 'king_centralization',
      description: 'Finale roi et pions : le roi noir doit rejoindre le centre pour bloquer le pion blanc.',
      fen: '8/6p1/3k3p/8/8/3K3P/6P1/8 b - - 0 1',
      expectedMoves: Object.freeze(['Kd5']),
      tags: Object.freeze(['endgame', 'king'])
    }),
    Object.freeze({
      id: 'capture_passed_pawn',
      description: 'Les noirs peuvent capturer immédiatement le pion passé blanc.',
      fen: '8/6p1/8/3k3p/3P4/8/6PP/5K2 b - - 0 1',
      expectedMoves: Object.freeze(['Kxd4']),
      tags: Object.freeze(['tactics', 'passed-pawn'])
    }),
    Object.freeze({
      id: 'push_outside_passed_pawn',
      description: 'Pousser le pion h crée une menace de promotion rapide.',
      fen: '8/8/6k1/6P1/7p/6K1/8/8 b - - 0 1',
      expectedMoves: Object.freeze(['h3']),
      tags: Object.freeze(['endgame', 'passed-pawn'])
    })
  ]);

  const MIN_AI_DEPTH = 1;
  const MAX_AI_DEPTH = 5;

  const PIECE_TYPES = Object.freeze({
    PAWN: 'p',
    KNIGHT: 'n',
    BISHOP: 'b',
    ROOK: 'r',
    QUEEN: 'q',
    KING: 'k'
  });

  const PIECE_SYMBOLS = Object.freeze({
    w: Object.freeze({
      p: '♙',
      n: '♘',
      b: '♗',
      r: '♖',
      q: '♕',
      k: '♔'
    }),
    b: Object.freeze({
      p: '♟',
      n: '♞',
      b: '♝',
      r: '♜',
      q: '♛',
      k: '♚'
    })
  });

  const PIECE_FALLBACK_LABELS = Object.freeze({
    w: Object.freeze({
      p: 'White pawn',
      n: 'White knight',
      b: 'White bishop',
      r: 'White rook',
      q: 'White queen',
      k: 'White king'
    }),
    b: Object.freeze({
      p: 'Black pawn',
      n: 'Black knight',
      b: 'Black bishop',
      r: 'Black rook',
      q: 'Black queen',
      k: 'Black king'
    })
  });

  const INITIAL_BOARD = Object.freeze([
    Object.freeze(['bR', 'bN', 'bB', 'bQ', 'bK', 'bB', 'bN', 'bR']),
    Object.freeze(['bP', 'bP', 'bP', 'bP', 'bP', 'bP', 'bP', 'bP']),
    Object.freeze(['', '', '', '', '', '', '', '']),
    Object.freeze(['', '', '', '', '', '', '', '']),
    Object.freeze(['', '', '', '', '', '', '', '']),
    Object.freeze(['', '', '', '', '', '', '', '']),
    Object.freeze(['wP', 'wP', 'wP', 'wP', 'wP', 'wP', 'wP', 'wP']),
    Object.freeze(['wR', 'wN', 'wB', 'wQ', 'wK', 'wB', 'wN', 'wR'])
  ]);

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function toPositiveInteger(value, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return fallback;
    }
    return Math.floor(numeric);
  }

  function toNonNegativeNumber(value, fallback) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric < 0) {
      return fallback;
    }
    return numeric;
  }

  function clampDepth(value) {
    const normalized = toPositiveInteger(value, DEFAULT_AI_SETTINGS.depth);
    return Math.max(MIN_AI_DEPTH, Math.min(MAX_AI_DEPTH, normalized));
  }

  function clampTranspositionSize(value) {
    const normalized = toPositiveInteger(value, DEFAULT_AI_SETTINGS.transpositionSize);
    return Math.max(500, Math.min(20000, normalized));
  }

  function normalizeRewardConfig(raw, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : {};
    const secondsCandidate = raw && typeof raw === 'object'
      ? raw.offlineSeconds ?? raw.seconds ?? raw.durationSeconds
      : null;
    const multiplierCandidate = raw && typeof raw === 'object'
      ? raw.offlineMultiplier ?? raw.multiplier ?? raw.value
      : null;

    const offlineSeconds = Math.max(
      0,
      Number.isFinite(Number(secondsCandidate))
        ? Number(secondsCandidate)
        : Number.isFinite(Number(base.offlineSeconds))
          ? Number(base.offlineSeconds)
          : 0
    );

    const offlineMultiplier = Math.max(
      0,
      Number.isFinite(Number(multiplierCandidate))
        ? Number(multiplierCandidate)
        : Number.isFinite(Number(base.offlineMultiplier))
          ? Number(base.offlineMultiplier)
          : 1
    );

    return {
      offlineSeconds,
      offlineMultiplier
    };
  }

  function normalizeDifficultyMode(rawMode, fallbackMode) {
    const base = fallbackMode && typeof fallbackMode === 'object'
      ? fallbackMode
      : DEFAULT_DIFFICULTY_MODES[0];
    const source = rawMode && typeof rawMode === 'object' ? rawMode : {};

    const id = typeof source.id === 'string' && source.id.trim()
      ? source.id.trim()
      : base.id;

    const labelKey = typeof source.labelKey === 'string' && source.labelKey.trim()
      ? source.labelKey.trim()
      : base.labelKey;

    const fallbackLabel = typeof source.label === 'string' && source.label.trim()
      ? source.label.trim()
      : base.fallbackLabel;

    const descriptionKey = typeof source.descriptionKey === 'string' && source.descriptionKey.trim()
      ? source.descriptionKey.trim()
      : base.descriptionKey;

    const fallbackDescription = typeof source.description === 'string' && source.description.trim()
      ? source.description.trim()
      : base.fallbackDescription;

    const aiSource = source.ai && typeof source.ai === 'object' ? source.ai : {};
    const baseAi = base.ai && typeof base.ai === 'object' ? base.ai : DEFAULT_AI_SETTINGS;
    const depthCandidates = [aiSource.depth, aiSource.searchDepth, aiSource.maxDepth, baseAi.depth, DEFAULT_AI_SETTINGS.depth];
    let depth = DEFAULT_AI_SETTINGS.depth;
    for (let i = 0; i < depthCandidates.length; i += 1) {
      const candidate = depthCandidates[i];
      if (candidate != null) {
        depth = clampDepth(candidate);
        break;
      }
    }

    const timeCandidates = [
      aiSource.timeLimitMs,
      aiSource.timeMs,
      aiSource.maxTimeMs,
      baseAi.timeLimitMs,
      DEFAULT_AI_SETTINGS.timeLimitMs
    ];
    let timeLimitMs = DEFAULT_AI_SETTINGS.timeLimitMs;
    for (let i = 0; i < timeCandidates.length; i += 1) {
      const candidate = timeCandidates[i];
      if (candidate != null) {
        timeLimitMs = toNonNegativeNumber(candidate, DEFAULT_AI_SETTINGS.timeLimitMs);
        break;
      }
    }

    const delayCandidates = [
      aiSource.moveDelayMs,
      aiSource.delayMs,
      baseAi.moveDelayMs,
      DEFAULT_AI_SETTINGS.moveDelayMs
    ];
    let moveDelayMs = DEFAULT_AI_SETTINGS.moveDelayMs;
    for (let i = 0; i < delayCandidates.length; i += 1) {
      const candidate = delayCandidates[i];
      if (candidate != null) {
        moveDelayMs = toNonNegativeNumber(candidate, DEFAULT_AI_SETTINGS.moveDelayMs);
        break;
      }
    }

    const reward = normalizeRewardConfig(source.reward, base.reward);

    return {
      id,
      labelKey,
      fallbackLabel,
      descriptionKey,
      fallbackDescription,
      ai: { depth, timeLimitMs, moveDelayMs },
      reward
    };
  }

  function normalizeDifficultyConfig(rawConfig) {
    const fallback = DEFAULT_DIFFICULTY_CONFIG;
    const source = rawConfig && typeof rawConfig === 'object' ? rawConfig : {};
    const normalized = { defaultMode: fallback.defaultMode, modes: [] };
    const rawModes = Array.isArray(source.modes) && source.modes.length ? source.modes : fallback.modes;
    const seen = new Set();
    for (let i = 0; i < rawModes.length; i += 1) {
      const rawMode = rawModes[i];
      const fallbackMode = fallback.modes[Math.min(i, fallback.modes.length - 1)] || fallback.modes[0];
      const mode = normalizeDifficultyMode(rawMode, fallbackMode);
      if (!mode || seen.has(mode.id)) {
        continue;
      }
      seen.add(mode.id);
      normalized.modes.push(Object.freeze(mode));
    }
    if (!normalized.modes.length) {
      normalized.modes = fallback.modes;
      normalized.defaultMode = fallback.defaultMode;
      return Object.freeze(normalized);
    }
    const defaultCandidate = typeof source.defaultMode === 'string' && source.defaultMode.trim()
      ? source.defaultMode.trim()
      : null;
    if (defaultCandidate && seen.has(defaultCandidate)) {
      normalized.defaultMode = defaultCandidate;
    } else if (!seen.has(normalized.defaultMode)) {
      normalized.defaultMode = normalized.modes[0].id;
    }
    return Object.freeze(normalized);
  }

  function findDifficultyMode(config, id) {
    if (!config || !Array.isArray(config.modes)) {
      return null;
    }
    for (let i = 0; i < config.modes.length; i += 1) {
      const mode = config.modes[i];
      if (mode && mode.id === id) {
        return mode;
      }
    }
    return null;
  }

  function getDefaultDifficultyMode() {
    return findDifficultyMode(DIFFICULTY_CONFIG, DIFFICULTY_CONFIG.defaultMode)
      || (DIFFICULTY_CONFIG.modes && DIFFICULTY_CONFIG.modes[0])
      || DEFAULT_DIFFICULTY_MODES[0];
  }

  function resolveDifficultyMode(modeId) {
    if (typeof modeId === 'string' && modeId.trim()) {
      const found = findDifficultyMode(DIFFICULTY_CONFIG, modeId.trim());
      if (found) {
        return found;
      }
    }
    return getDefaultDifficultyMode();
  }

  function normalizeMatchConfig(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const candidate = Number(source.moveLimit ?? source.fullmoveLimit ?? source.limit);
    let moveLimit = DEFAULT_MATCH_LIMIT;
    if (Number.isFinite(candidate) && candidate >= 20) {
      moveLimit = Math.floor(candidate);
    }
    return { moveLimit };
  }

  const DIFFICULTY_CONFIG = normalizeDifficultyConfig(
    GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.echecs
      ? GLOBAL_CONFIG.arcade.echecs.difficulty
      : null
  );

  const MATCH_CONFIG = normalizeMatchConfig(
    GLOBAL_CONFIG && GLOBAL_CONFIG.arcade && GLOBAL_CONFIG.arcade.echecs
      ? GLOBAL_CONFIG.arcade.echecs.match
      : null
  );

  function normalizeAiAnalysis(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const moveSan = typeof raw.moveSan === 'string' && raw.moveSan.trim() ? raw.moveSan.trim() : '';
    const before = Number(raw.evaluationBefore);
    const after = Number(raw.evaluationAfter);
    const delta = Number(raw.evaluationDelta);
    const depth = Number(raw.depth);
    const score = Number(raw.searchScore);
    const timestamp = Number(raw.timestamp);
    const normalized = {
      moveSan,
      evaluationBefore: Number.isFinite(before) ? before : null,
      evaluationAfter: Number.isFinite(after) ? after : null,
      evaluationDelta: Number.isFinite(delta)
        ? delta
        : Number.isFinite(before) && Number.isFinite(after)
          ? after - before
          : null,
      depth: Number.isFinite(depth) && depth >= 0 ? Math.floor(depth) : null,
      searchScore: Number.isFinite(score) ? score : null,
      timestamp: Number.isFinite(timestamp) && timestamp > 0 ? timestamp : Date.now()
    };
    if (!normalized.moveSan && normalized.evaluationAfter == null && normalized.evaluationBefore == null) {
      return null;
    }
    return normalized;
  }

  function serializeAiAnalysis(analysis) {
    if (!analysis || typeof analysis !== 'object') {
      return null;
    }
    const normalized = normalizeAiAnalysis(analysis);
    if (!normalized) {
      return null;
    }
    return {
      moveSan: normalized.moveSan,
      evaluationBefore: normalized.evaluationBefore,
      evaluationAfter: normalized.evaluationAfter,
      evaluationDelta: normalized.evaluationDelta,
      depth: normalized.depth,
      searchScore: normalized.searchScore,
      timestamp: normalized.timestamp
    };
  }

  function normalizeEvaluationForAnalysis(score, activeColor) {
    const numeric = Number(score);
    if (!Number.isFinite(numeric)) {
      return null;
    }
    return numeric - getTempoBonusForColor(activeColor);
  }

  function createAiMoveAnalysis(state, move, info, notation) {
    if (!info || typeof info !== 'object') {
      return null;
    }
    const movingColor = info.activeColorBefore === WHITE || info.activeColorBefore === BLACK
      ? info.activeColorBefore
      : getPieceColor(move && move.piece ? move.piece : null) || BLACK;
    const before = normalizeEvaluationForAnalysis(info.evaluationBefore, movingColor);
    let after = normalizeEvaluationForAnalysis(info.evaluationAfter, state.activeColor);
    if (after == null) {
      after = normalizeEvaluationForAnalysis(evaluateStaticPosition(state), state.activeColor);
    }
    const depth = Number.isFinite(Number(info.depth)) ? Math.max(0, Math.floor(Number(info.depth))) : null;
    const searchScore = Number.isFinite(Number(info.searchScore)) ? Number(info.searchScore) : null;
    const durationMs = Number.isFinite(Number(info.durationMs)) && Number(info.durationMs) >= 0
      ? Number(info.durationMs)
      : null;
    const delta = after != null && before != null ? after - before : null;
    return {
      moveSan: notation && notation.san ? notation.san : '',
      evaluationBefore: before,
      evaluationAfter: after,
      evaluationDelta: delta,
      depth,
      searchScore,
      durationMs,
      timestamp: Date.now()
    };
  }

  function formatMultiplierText(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return '';
    }
    if (Math.abs(numeric - Math.round(numeric)) < 1e-6) {
      return '×' + Math.round(numeric);
    }
    if (Math.abs(numeric) >= 10) {
      return '×' + numeric.toFixed(1).replace(/\.0$/, '');
    }
    return '×' + numeric.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
  }

  function formatRewardDuration(seconds) {
    const numeric = Number(seconds);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return '';
    }
    const totalSeconds = Math.floor(numeric);
    const minutes = Math.floor(totalSeconds / 60);
    if (minutes >= 120) {
      const hours = Math.floor(minutes / 60);
      const remainingMinutes = minutes % 60;
      if (remainingMinutes === 0) {
        return hours + ' h';
      }
      return hours + ' h ' + remainingMinutes + ' min';
    }
    if (minutes >= 60) {
      const hours = Math.floor(minutes / 60);
      const remainingMinutes = minutes % 60;
      return hours + ' h ' + remainingMinutes + ' min';
    }
    if (minutes > 0) {
      return minutes + ' min';
    }
    return Math.max(1, totalSeconds) + ' s';
  }

  function formatDifficultyRewardText(mode) {
    if (!mode || !mode.reward) {
      return '';
    }
    const seconds = Number(mode.reward.offlineSeconds);
    const multiplierText = formatMultiplierText(mode.reward.offlineMultiplier);
    const durationText = formatRewardDuration(seconds);
    if (!durationText && !multiplierText) {
      return '';
    }
    const fallback = durationText && multiplierText
      ? 'Bonus hors ligne : ' + durationText + ' à ' + multiplierText
      : durationText
        ? 'Bonus hors ligne : ' + durationText
        : 'Bonus hors ligne : ' + multiplierText;
    return translate(
      'index.sections.echecs.difficulty.reward',
      fallback,
      { duration: durationText, multiplier: multiplierText }
    );
  }

  function formatEvaluationScore(score) {
    const numeric = Number(score);
    if (!Number.isFinite(numeric)) {
      return '—';
    }
    const pawns = numeric / 100;
    const abs = Math.abs(pawns);
    const precision = abs >= 10 ? 1 : 2;
    const text = pawns.toFixed(precision).replace(/0+$/, '').replace(/\.$/, '');
    const sign = pawns > 0 ? '+' : pawns < 0 ? '−' : '';
    return sign + text.replace(/^(\-)/, '−').replace(/^\+−/, '−');
  }

  function formatDeltaScore(delta) {
    const numeric = Number(delta);
    if (!Number.isFinite(numeric) || Math.abs(numeric) < 1e-3) {
      return translate('index.sections.echecs.analysis.delta.equal', 'aucun changement');
    }
    const pawns = numeric / 100;
    const abs = Math.abs(pawns);
    const precision = abs >= 10 ? 1 : 2;
    const text = pawns.toFixed(precision).replace(/0+$/, '').replace(/\.$/, '');
    const sign = pawns > 0 ? '+' : '−';
    const fallback = pawns > 0
      ? 'avantage noir ' + sign + text
      : 'avantage blanc ' + sign + text;
    const key = pawns > 0
      ? 'index.sections.echecs.analysis.delta.positive'
      : 'index.sections.echecs.analysis.delta.negative';
    return translate(key, fallback, { value: sign + text });
  }

  function formatDepthText(depth) {
    const numeric = Number(depth);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return '';
    }
    const value = Math.floor(numeric);
    return translate(
      'index.sections.echecs.analysis.depth',
      ' · profondeur ' + value,
      { value }
    );
  }

  function getConfiguredChessAiSettings(difficultyMode) {
    const arcadeConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.arcade ? GLOBAL_CONFIG.arcade : null;
    const chessConfig = arcadeConfig && arcadeConfig.echecs ? arcadeConfig.echecs : null;
    const aiConfig = chessConfig && chessConfig.ai ? chessConfig.ai : null;

    let depth = DEFAULT_AI_SETTINGS.depth;
    if (aiConfig) {
      const depthCandidates = [aiConfig.searchDepth, aiConfig.depth, aiConfig.maxDepth];
      for (let i = 0; i < depthCandidates.length; i += 1) {
        const candidate = depthCandidates[i];
        if (candidate != null) {
          depth = clampDepth(candidate);
          break;
        }
      }
    }

    let timeLimitMs = DEFAULT_AI_SETTINGS.timeLimitMs;
    if (aiConfig) {
      const timeCandidates = [aiConfig.timeLimitMs, aiConfig.timeMs, aiConfig.maxTimeMs];
      for (let i = 0; i < timeCandidates.length; i += 1) {
        const candidate = timeCandidates[i];
        if (candidate != null) {
          timeLimitMs = toNonNegativeNumber(candidate, DEFAULT_AI_SETTINGS.timeLimitMs);
          break;
        }
      }
    }

    let moveDelayMs = DEFAULT_AI_SETTINGS.moveDelayMs;
    if (aiConfig) {
      const delayCandidates = [aiConfig.moveDelayMs, aiConfig.delayMs];
      for (let i = 0; i < delayCandidates.length; i += 1) {
        const candidate = delayCandidates[i];
        if (candidate != null) {
          moveDelayMs = toNonNegativeNumber(candidate, DEFAULT_AI_SETTINGS.moveDelayMs);
          break;
        }
      }
    }

    let transpositionSize = DEFAULT_AI_SETTINGS.transpositionSize;
    if (aiConfig) {
      const transpositionCandidates = [aiConfig.transpositionSize, aiConfig.ttSize, aiConfig.hashSize];
      for (let i = 0; i < transpositionCandidates.length; i += 1) {
        const candidate = transpositionCandidates[i];
        if (candidate != null) {
          transpositionSize = clampTranspositionSize(candidate);
          break;
        }
      }
    }

    if (difficultyMode && difficultyMode.ai) {
      const overrides = difficultyMode.ai;
      if (overrides.depth != null) {
        depth = clampDepth(overrides.depth);
      }
      if (overrides.timeLimitMs != null) {
        timeLimitMs = toNonNegativeNumber(overrides.timeLimitMs, timeLimitMs);
      }
      if (overrides.moveDelayMs != null) {
        moveDelayMs = toNonNegativeNumber(overrides.moveDelayMs, moveDelayMs);
      }
    }

    return {
      depth,
      timeLimitMs,
      moveDelayMs,
      transpositionSize
    };
  }

  function createAiContext(difficultyMode) {
    return {
      settings: getConfiguredChessAiSettings(difficultyMode),
      table: new Map(),
      searchId: 0,
      lastBestMove: null
    };
  }

  function assignAiTestPositions(positions) {
    if (typeof window === 'undefined' || !Array.isArray(positions)) {
      return;
    }
    window.atom2universChessTests = positions;
  }

  function loadAiTestPositions() {
    if (typeof window === 'undefined') {
      return;
    }
    if (Array.isArray(window.atom2universChessTests) && window.atom2universChessTests.length > 0) {
      return;
    }
    assignAiTestPositions(FALLBACK_AI_TEST_POSITIONS);

    const fetcher = typeof window.fetch === 'function'
      ? window.fetch.bind(window)
      : typeof fetch === 'function'
        ? fetch
        : null;

    if (!fetcher) {
      return;
    }

    fetcher(AI_TEST_POSITIONS_URL)
      .then(function (response) {
        if (!response || !response.ok) {
          return null;
        }
        return response.json();
      })
      .then(function (data) {
        if (!Array.isArray(data) || data.length === 0) {
          return;
        }
        const sanitized = data.map(function (entry) {
          const normalized = {
            id: typeof entry.id === 'string' ? entry.id : '',
            description: typeof entry.description === 'string' ? entry.description : '',
            fen: typeof entry.fen === 'string' ? entry.fen : '',
            expectedMoves: Array.isArray(entry.expectedMoves)
              ? entry.expectedMoves.filter(function (move) { return typeof move === 'string' && move.trim(); })
              : [],
            tags: Array.isArray(entry.tags)
              ? entry.tags.filter(function (tag) { return typeof tag === 'string' && tag.trim(); })
              : []
          };
          return Object.freeze(normalized);
        });
        assignAiTestPositions(Object.freeze(sanitized));
      })
      .catch(function () {
        // Ignored: fallback dataset already assigned.
      });
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key) {
      return fallback;
    }

    const translator = typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
      ? window.i18n.t
      : typeof window !== 'undefined' && typeof window.t === 'function'
        ? window.t
        : null;

    if (translator) {
      try {
        const translated = translator(key, params);
        if (typeof translated === 'string') {
          const trimmed = translated.trim();
          if (trimmed && trimmed !== key) {
            return trimmed;
          }
        }
      } catch (error) {
        console.warn('Chess translation error for', key, error);
      }
    }

    if (typeof fallback === 'string') {
      return fallback.replace(/\{\{(.*?)\}\}/g, function (match, token) {
        const trimmedToken = typeof token === 'string' ? token.trim() : '';
        if (!trimmedToken) {
          return '';
        }
        if (params && Object.prototype.hasOwnProperty.call(params, trimmedToken)) {
          return String(params[trimmedToken]);
        }
        return '';
      });
    }

    if (typeof fallback === 'function') {
      try {
        return fallback(params);
      } catch (error) {
        return '';
      }
    }

    return fallback !== undefined ? fallback : key;
  }

  function getGlobalGameState() {
    if (typeof window === 'undefined') {
      return null;
    }
    if (window.atom2universGameState && typeof window.atom2universGameState === 'object') {
      return window.atom2universGameState;
    }
    if (window.gameState && typeof window.gameState === 'object') {
      return window.gameState;
    }
    return null;
  }

  function requestSave() {
    if (typeof window === 'undefined') {
      return;
    }
    if (typeof window.atom2universSaveGame === 'function') {
      window.atom2universSaveGame();
      return;
    }
    if (typeof window.saveGame === 'function') {
      window.saveGame();
    }
  }

  function createPiece(color, type) {
    return color + type.toUpperCase();
  }

  function getPieceColor(piece) {
    if (!piece || typeof piece !== 'string' || piece.length < 2) {
      return null;
    }
    return piece[0];
  }

  function getPieceType(piece) {
    if (!piece || typeof piece !== 'string' || piece.length < 2) {
      return null;
    }
    return piece[1].toLowerCase();
  }

  const PIECE_BASE_VALUES = Object.freeze({
    [PIECE_TYPES.PAWN]: 100,
    [PIECE_TYPES.KNIGHT]: 320,
    [PIECE_TYPES.BISHOP]: 330,
    [PIECE_TYPES.ROOK]: 500,
    [PIECE_TYPES.QUEEN]: 900,
    [PIECE_TYPES.KING]: 20000
  });

  const MATE_SCORE = 100000;
  const TRANSPOSITION_FLAG_EXACT = 0;
  const TRANSPOSITION_FLAG_LOWER = 1;
  const TRANSPOSITION_FLAG_UPPER = 2;

  function analyzeBoardForEvaluation(board) {
    const analysis = {
      board,
      score: 0,
      whitePawnFiles: new Array(BOARD_SIZE).fill(0),
      blackPawnFiles: new Array(BOARD_SIZE).fill(0),
      whitePawnRowsByFile: Array.from({ length: BOARD_SIZE }, function () { return []; }),
      blackPawnRowsByFile: Array.from({ length: BOARD_SIZE }, function () { return []; }),
      whitePawns: [],
      blackPawns: [],
      whiteRooks: [],
      blackRooks: [],
      whiteKing: null,
      blackKing: null,
      whiteMinorHome: 0,
      blackMinorHome: 0,
      whiteNonPawnMaterial: 0,
      blackNonPawnMaterial: 0,
      whiteBishopCount: 0,
      blackBishopCount: 0
    };

    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const piece = board[row][col];
        if (!piece) {
          continue;
        }
        const color = getPieceColor(piece);
        const type = getPieceType(piece);
        if (!color || !type) {
          continue;
        }
        const sign = color === WHITE ? 1 : -1;
        const baseValue = PIECE_BASE_VALUES[type] || 0;
        analysis.score += sign * baseValue;

        if (type !== PIECE_TYPES.PAWN && type !== PIECE_TYPES.KING) {
          if (color === WHITE) {
            analysis.whiteNonPawnMaterial += baseValue;
          } else {
            analysis.blackNonPawnMaterial += baseValue;
          }
        }

        if (type === PIECE_TYPES.PAWN) {
          if (color === WHITE) {
            analysis.whitePawnFiles[col] += 1;
            analysis.whitePawnRowsByFile[col].push(row);
            analysis.whitePawns.push({ row, col });
            const advancement = Math.max(0, 6 - row);
            analysis.score += sign * advancement * 4;
          } else {
            analysis.blackPawnFiles[col] += 1;
            analysis.blackPawnRowsByFile[col].push(row);
            analysis.blackPawns.push({ row, col });
            const advancement = Math.max(0, row - 1);
            analysis.score += sign * advancement * 4;
          }
          const centerDistance = Math.abs(col - 3.5);
          const centerBonus = Math.max(0, 2 - centerDistance) * 2;
          analysis.score += sign * centerBonus;
        } else if (type === PIECE_TYPES.KNIGHT) {
          const distance = Math.max(Math.abs(row - 3.5), Math.abs(col - 3.5));
          const centrality = Math.max(0, 3.5 - distance);
          analysis.score += sign * Math.round(centrality * 12);
          if (color === WHITE && row === 7 && (col === 1 || col === 6)) {
            analysis.whiteMinorHome += 1;
          } else if (color === BLACK && row === 0 && (col === 1 || col === 6)) {
            analysis.blackMinorHome += 1;
          }
        } else if (type === PIECE_TYPES.BISHOP) {
          const distance = Math.max(Math.abs(row - 3.5), Math.abs(col - 3.5));
          const centrality = Math.max(0, 3 - distance);
          analysis.score += sign * Math.round(centrality * 10);
          if (color === WHITE && row === 7 && (col === 2 || col === 5)) {
            analysis.whiteMinorHome += 1;
          } else if (color === BLACK && row === 0 && (col === 2 || col === 5)) {
            analysis.blackMinorHome += 1;
          }
          if (color === WHITE) {
            analysis.whiteBishopCount += 1;
          } else {
            analysis.blackBishopCount += 1;
          }
        } else if (type === PIECE_TYPES.ROOK) {
          const rookInfo = { row, col };
          if (color === WHITE) {
            analysis.whiteRooks.push(rookInfo);
          } else {
            analysis.blackRooks.push(rookInfo);
          }
          const fileDistance = Math.abs(col - 3.5);
          analysis.score += sign * Math.max(0, 2 - fileDistance) * 4;
        } else if (type === PIECE_TYPES.QUEEN) {
          const distance = Math.max(Math.abs(row - 3.5), Math.abs(col - 3.5));
          const centrality = Math.max(0, 3 - distance);
          analysis.score += sign * Math.round(centrality * 6);
        } else if (type === PIECE_TYPES.KING) {
          if (color === WHITE) {
            analysis.whiteKing = { row, col };
          } else {
            analysis.blackKing = { row, col };
          }
        }
      }
    }

    return analysis;
  }

  function evaluatePawnStructureScore(analysis) {
    let score = 0;
    for (let file = 0; file < BOARD_SIZE; file += 1) {
      const whiteCount = analysis.whitePawnFiles[file];
      const blackCount = analysis.blackPawnFiles[file];
      if (whiteCount > 1) {
        score -= (whiteCount - 1) * 18;
      }
      if (blackCount > 1) {
        score += (blackCount - 1) * 18;
      }

      const whiteIsolated = whiteCount > 0
        && (file === 0 || analysis.whitePawnFiles[file - 1] === 0)
        && (file === BOARD_SIZE - 1 || analysis.whitePawnFiles[file + 1] === 0);
      if (whiteIsolated) {
        score -= 12;
      }

      const blackIsolated = blackCount > 0
        && (file === 0 || analysis.blackPawnFiles[file - 1] === 0)
        && (file === BOARD_SIZE - 1 || analysis.blackPawnFiles[file + 1] === 0);
      if (blackIsolated) {
        score += 12;
      }
    }

    for (let i = 0; i < analysis.whitePawns.length; i += 1) {
      const pawn = analysis.whitePawns[i];
      if (isWhitePassedPawn(analysis, pawn)) {
        const advancement = Math.max(0, 6 - pawn.row);
        score += 20 + advancement * 4;
      }
    }

    for (let i = 0; i < analysis.blackPawns.length; i += 1) {
      const pawn = analysis.blackPawns[i];
      if (isBlackPassedPawn(analysis, pawn)) {
        const advancement = Math.max(0, pawn.row - 1);
        score -= 20 + advancement * 4;
      }
    }

    return score;
  }

  function isWhitePassedPawn(analysis, pawn) {
    for (let file = pawn.col - 1; file <= pawn.col + 1; file += 1) {
      if (file < 0 || file >= BOARD_SIZE) {
        continue;
      }
      const opponentRows = analysis.blackPawnRowsByFile[file];
      for (let index = 0; index < opponentRows.length; index += 1) {
        if (opponentRows[index] < pawn.row) {
          return false;
        }
      }
    }
    return true;
  }

  function isBlackPassedPawn(analysis, pawn) {
    for (let file = pawn.col - 1; file <= pawn.col + 1; file += 1) {
      if (file < 0 || file >= BOARD_SIZE) {
        continue;
      }
      const opponentRows = analysis.whitePawnRowsByFile[file];
      for (let index = 0; index < opponentRows.length; index += 1) {
        if (opponentRows[index] > pawn.row) {
          return false;
        }
      }
    }
    return true;
  }

  function evaluateDevelopmentScore(analysis) {
    const penalty = 18;
    return (-analysis.whiteMinorHome * penalty) + (analysis.blackMinorHome * penalty);
  }

  function evaluateRookPlacementScore(analysis) {
    let score = 0;
    for (let i = 0; i < analysis.whiteRooks.length; i += 1) {
      const rook = analysis.whiteRooks[i];
      const file = rook.col;
      const whiteFilePawns = analysis.whitePawnFiles[file];
      const blackFilePawns = analysis.blackPawnFiles[file];
      if (whiteFilePawns === 0) {
        score += 12;
        if (blackFilePawns === 0) {
          score += 6;
        }
      }
      if (rook.row <= 1) {
        score += 10;
      }
    }

    for (let i = 0; i < analysis.blackRooks.length; i += 1) {
      const rook = analysis.blackRooks[i];
      const file = rook.col;
      const blackFilePawns = analysis.blackPawnFiles[file];
      const whiteFilePawns = analysis.whitePawnFiles[file];
      if (blackFilePawns === 0) {
        score -= 12;
        if (whiteFilePawns === 0) {
          score -= 6;
        }
      }
      if (rook.row >= BOARD_SIZE - 2) {
        score -= 10;
      }
    }

    return score;
  }

  function evaluateSingleKingSafety(analysis, state, kingPosition, color, isEndgame) {
    if (!kingPosition) {
      return 0;
    }

    let score = 0;
    const board = analysis.board;
    const homeRow = color === WHITE ? BOARD_SIZE - 1 : 0;
    const direction = color === WHITE ? -1 : 1;
    const kingFile = kingPosition.col;
    const kingRow = kingPosition.row;

    if (isEndgame) {
      const distance = Math.max(Math.abs(kingRow - 3.5), Math.abs(kingFile - 3.5));
      const centrality = Math.max(0, 3.5 - distance);
      score += Math.round(centrality * 14);
      return score;
    }

    const distanceFromCenter = Math.abs(kingFile - 3.5);
    if (distanceFromCenter > 1.5) {
      score += 20;
    } else if (distanceFromCenter < 1) {
      score -= 18;
    }

    if (Math.abs(kingRow - homeRow) > 1) {
      score -= 14;
    }

    const castlingRights = state.castling && state.castling[color];
    if (castlingRights && !castlingRights.king && !castlingRights.queen && kingFile === 4) {
      score -= 8;
    }

    let shieldScore = 0;
    for (let offset = -1; offset <= 1; offset += 1) {
      const file = kingFile + offset;
      const frontRow = kingRow + direction;
      if (!isOnBoard(frontRow, file)) {
        shieldScore -= 6;
        continue;
      }
      const frontPiece = board[frontRow][file];
      if (frontPiece && getPieceColor(frontPiece) === color && getPieceType(frontPiece) === PIECE_TYPES.PAWN) {
        shieldScore += 12;
        continue;
      }
      const secondRow = kingRow + direction * 2;
      if (isOnBoard(secondRow, file)) {
        const secondPiece = board[secondRow][file];
        if (secondPiece && getPieceColor(secondPiece) === color && getPieceType(secondPiece) === PIECE_TYPES.PAWN) {
          shieldScore += 6;
        } else {
          shieldScore -= 8;
        }
      } else {
        shieldScore -= 8;
      }
    }

    score += shieldScore;
    return score;
  }

  function evaluateKingSafetyScore(analysis, state) {
    const totalNonPawnMaterial = analysis.whiteNonPawnMaterial + analysis.blackNonPawnMaterial;
    const isEndgame = totalNonPawnMaterial < 1600;
    let score = 0;
    score += evaluateSingleKingSafety(analysis, state, analysis.whiteKing, WHITE, isEndgame);
    score -= evaluateSingleKingSafety(analysis, state, analysis.blackKing, BLACK, isEndgame);
    return score;
  }

  function evaluateEndgameFeatures(analysis) {
    const totalNonPawnMaterial = analysis.whiteNonPawnMaterial + analysis.blackNonPawnMaterial;
    if (totalNonPawnMaterial > 1600) {
      return 0;
    }

    let score = 0;
    const passedPawnBase = totalNonPawnMaterial < 800 ? 18 : 12;
    const kingCentralBase = totalNonPawnMaterial < 800 ? 16 : 10;

    const evaluateKingCentrality = function (kingInfo) {
      if (!kingInfo) {
        return 0;
      }
      const distance = Math.abs(kingInfo.row - 3.5) + Math.abs(kingInfo.col - 3.5);
      const centrality = Math.max(0, 4 - distance);
      return Math.round(centrality * kingCentralBase);
    };

    score += evaluateKingCentrality(analysis.whiteKing);
    score -= evaluateKingCentrality(analysis.blackKing);

    for (let i = 0; i < analysis.whitePawns.length; i += 1) {
      const pawn = analysis.whitePawns[i];
      if (isWhitePassedPawn(analysis, pawn)) {
        const advancement = Math.max(0, 6 - pawn.row);
        score += passedPawnBase + advancement * 6;
      }
    }

    for (let i = 0; i < analysis.blackPawns.length; i += 1) {
      const pawn = analysis.blackPawns[i];
      if (isBlackPassedPawn(analysis, pawn)) {
        const advancement = Math.max(0, pawn.row - 1);
        score -= passedPawnBase + advancement * 6;
      }
    }

    return score;
  }

  function evaluateStaticPosition(state) {
    if (!state || !state.board) {
      return 0;
    }
    if (state.halfmoveClock >= 100 || isInsufficientMaterial(state.board)) {
      return 0;
    }

    const analysis = analyzeBoardForEvaluation(state.board);
    let score = analysis.score;

    if (analysis.whiteBishopCount >= 2) {
      score += 30;
    }
    if (analysis.blackBishopCount >= 2) {
      score -= 30;
    }

    score += evaluatePawnStructureScore(analysis);
    score += evaluateDevelopmentScore(analysis);
    score += evaluateRookPlacementScore(analysis);
    score += evaluateKingSafetyScore(analysis, state);
    score += evaluateEndgameFeatures(analysis);

    score += state.activeColor === WHITE ? 10 : -10;

    return score;
  }

  function getTempoBonusForColor(color) {
    return color === WHITE ? 10 : -10;
  }

  function cloneCastlingRights(castling) {
    const source = castling && typeof castling === 'object' ? castling : {};
    const white = source.w && typeof source.w === 'object' ? source.w : {};
    const black = source.b && typeof source.b === 'object' ? source.b : {};
    return {
      w: { king: Boolean(white.king), queen: Boolean(white.queen) },
      b: { king: Boolean(black.king), queen: Boolean(black.queen) }
    };
  }

  function createSearchStateFromGameState(state) {
    return {
      board: cloneBoard(state.board),
      activeColor: state.activeColor,
      castling: cloneCastlingRights(state.castling),
      enPassant: state.enPassant ? { row: state.enPassant.row, col: state.enPassant.col } : null,
      halfmoveClock: Number.isFinite(state.halfmoveClock) ? state.halfmoveClock : 0,
      fullmove: Number.isFinite(state.fullmove) && state.fullmove > 0 ? state.fullmove : 1
    };
  }

  function ensureAiContext(state) {
    if (!state.ai || typeof state.ai !== 'object') {
      state.ai = createAiContext(state.difficulty);
      return;
    }
    state.ai.settings = getConfiguredChessAiSettings(state.difficulty);
    if (!(state.ai.table instanceof Map)) {
      state.ai.table = new Map();
    }
    if (!Number.isInteger(state.ai.searchId)) {
      state.ai.searchId = 0;
    }
    if (!Object.prototype.hasOwnProperty.call(state.ai, 'lastBestMove')) {
      state.ai.lastBestMove = null;
    }
  }

  function getCurrentTimeMs() {
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
      return performance.now();
    }
    return Date.now();
  }

  function moveMatchesDescriptor(move, descriptor) {
    if (!move || !descriptor) {
      return false;
    }
    if (move.fromRow !== descriptor.fromRow || move.fromCol !== descriptor.fromCol) {
      return false;
    }
    if (move.toRow !== descriptor.toRow || move.toCol !== descriptor.toCol) {
      return false;
    }
    const promotion = move.promotion || null;
    const descriptorPromotion = descriptor.promotion || null;
    return promotion === descriptorPromotion;
  }

  function cloneMoveDescriptor(move) {
    if (!move) {
      return null;
    }
    return {
      fromRow: move.fromRow,
      fromCol: move.fromCol,
      toRow: move.toRow,
      toCol: move.toCol,
      promotion: move.promotion || null
    };
  }

  function recordKillerMove(context, ply, move) {
    if (!context || !context.killers || !move || move.isCapture) {
      return;
    }
    if (!Number.isInteger(ply) || ply <= 0) {
      return;
    }
    const descriptor = cloneMoveDescriptor(move);
    if (!descriptor) {
      return;
    }
    const killers = Array.isArray(context.killers[ply]) ? context.killers[ply] : [];
    for (let i = 0; i < killers.length; i += 1) {
      if (moveMatchesDescriptor(move, killers[i])) {
        return;
      }
    }
    killers.unshift(descriptor);
    if (killers.length > 2) {
      killers.length = 2;
    }
    context.killers[ply] = killers;
  }

  function getKillerMoves(context, ply) {
    if (!context || !context.killers || !Number.isInteger(ply) || ply <= 0) {
      return null;
    }
    const killers = context.killers[ply];
    return Array.isArray(killers) && killers.length ? killers : null;
  }

  function findMoveFromDescriptor(moves, descriptor) {
    if (!descriptor || !Array.isArray(moves)) {
      return null;
    }
    for (let i = 0; i < moves.length; i += 1) {
      const move = moves[i];
      if (moveMatchesDescriptor(move, descriptor)) {
        return move;
      }
    }
    return null;
  }

  function getCapturedPieceValue(state, move) {
    if (!move || !move.isCapture) {
      return 0;
    }
    const color = getPieceColor(move.piece);
    if (move.isEnPassant) {
      const captureRow = move.toRow + (color === WHITE ? 1 : -1);
      if (captureRow < 0 || captureRow >= BOARD_SIZE) {
        return 0;
      }
      const captured = state.board[captureRow][move.toCol];
      const type = getPieceType(captured);
      return PIECE_BASE_VALUES[type] || 0;
    }
    const captured = state.board[move.toRow][move.toCol];
    const type = getPieceType(captured);
    return PIECE_BASE_VALUES[type] || 0;
  }

  function getMoveOrderingScore(state, move, preferredDescriptor, context, ply) {
    let score = 0;
    if (preferredDescriptor && moveMatchesDescriptor(move, preferredDescriptor)) {
      score += 5000;
    }
    if (move.isCapture) {
      const capturedValue = getCapturedPieceValue(state, move);
      const moverValue = PIECE_BASE_VALUES[getPieceType(move.piece)] || 0;
      const mvvLva = capturedValue * 12 - moverValue;
      score += 4000 + mvvLva;
    } else {
      const killerMoves = getKillerMoves(context, ply);
      if (killerMoves) {
        for (let index = 0; index < killerMoves.length; index += 1) {
          if (moveMatchesDescriptor(move, killerMoves[index])) {
            score += 3200 - index * 200;
            break;
          }
        }
      }
    }
    if (move.promotion) {
      score += 1500;
    }
    if (move.isCastle) {
      score += 400;
    }
    return score;
  }

  function orderMovesForSearch(state, moves, preferredDescriptor, context, ply) {
    const scored = moves.map(function (move) {
      return { move, score: getMoveOrderingScore(state, move, preferredDescriptor, context, ply) };
    });
    scored.sort(function (a, b) {
      return b.score - a.score;
    });
    return scored.map(function (entry) {
      return entry.move;
    });
  }

  function storeTranspositionEntry(context, key, depth, score, flag, move) {
    if (!context || !(context.table instanceof Map)) {
      return;
    }
    const limit = Number.isFinite(context.transpositionLimit) ? context.transpositionLimit : DEFAULT_AI_SETTINGS.transpositionSize;
    if (context.table.size >= limit) {
      const iterator = context.table.keys();
      const first = iterator.next();
      if (!first.done) {
        context.table.delete(first.value);
      }
    }
    context.table.set(key, {
      depth,
      score,
      flag,
      bestMove: cloneMoveDescriptor(move)
    });
  }

  function negamax(state, depth, alpha, beta, colorSign, context, ply) {
    const alphaOriginal = alpha;
    if (context.deadline && getCurrentTimeMs() >= context.deadline) {
      return { score: colorSign * evaluateStaticPosition(state), move: null, aborted: true };
    }

    if (depth === 0) {
      return { score: colorSign * evaluateStaticPosition(state), move: null, aborted: false };
    }

    if (state.halfmoveClock >= 100 || isInsufficientMaterial(state.board)) {
      return { score: 0, move: null, aborted: false };
    }

    const key = getPositionKey(state);
    const tableEntry = context.table instanceof Map ? context.table.get(key) : null;

    const legalMoves = generateLegalMoves(state);

    if (legalMoves.length === 0) {
      if (isKingInCheck(state, state.activeColor)) {
        return { score: -MATE_SCORE + ply, move: null, aborted: false };
      }
      return { score: 0, move: null, aborted: false };
    }

    let preferredDescriptor = null;
    if (tableEntry) {
      preferredDescriptor = tableEntry.bestMove;
      if (tableEntry.depth >= depth) {
        if (tableEntry.flag === TRANSPOSITION_FLAG_EXACT) {
          const matched = findMoveFromDescriptor(legalMoves, tableEntry.bestMove);
          return { score: tableEntry.score, move: matched, aborted: false };
        }
        if (tableEntry.flag === TRANSPOSITION_FLAG_LOWER) {
          alpha = Math.max(alpha, tableEntry.score);
        } else if (tableEntry.flag === TRANSPOSITION_FLAG_UPPER) {
          beta = Math.min(beta, tableEntry.score);
        }
        if (alpha >= beta) {
          const matched = findMoveFromDescriptor(legalMoves, tableEntry.bestMove);
          return { score: tableEntry.score, move: matched, aborted: false };
        }
      }
    }

    if (!preferredDescriptor && ply === 1 && context.rootHint) {
      preferredDescriptor = context.rootHint;
    }

    const orderedMoves = orderMovesForSearch(state, legalMoves, preferredDescriptor, context, ply);

    let bestScore = Number.NEGATIVE_INFINITY;
    let bestMove = null;
    let aborted = false;
    let localAlpha = alpha;

    for (let i = 0; i < orderedMoves.length; i += 1) {
      const move = orderedMoves[i];
      const nextState = applyMove(state, move);
      const child = negamax(nextState, depth - 1, -beta, -localAlpha, -colorSign, context, ply + 1);
      if (child.aborted) {
        aborted = true;
      }
      const score = -child.score;
      if (score > bestScore || bestMove == null) {
        bestScore = score;
        bestMove = move;
      }
      if (score > localAlpha) {
        localAlpha = score;
      }
      if (localAlpha >= beta) {
        recordKillerMove(context, ply, move);
      }
      if (localAlpha >= beta || aborted) {
        break;
      }
    }

    if (!aborted) {
      let flag = TRANSPOSITION_FLAG_EXACT;
      if (bestScore <= alphaOriginal) {
        flag = TRANSPOSITION_FLAG_UPPER;
      } else if (bestScore >= beta) {
        flag = TRANSPOSITION_FLAG_LOWER;
      }
      storeTranspositionEntry(context, key, depth, bestScore, flag, bestMove);
    }

    return { score: bestScore, move: bestMove, aborted };
  }

  function findBestAIMove(gameState, aiContext) {
    const settings = aiContext && aiContext.settings ? aiContext.settings : DEFAULT_AI_SETTINGS;
    const depth = clampDepth(settings.depth);
    const transpositionLimit = clampTranspositionSize(settings.transpositionSize);
    const searchState = createSearchStateFromGameState(gameState);
    const deadline = settings.timeLimitMs > 0 ? getCurrentTimeMs() + settings.timeLimitMs : 0;
    const context = {
      table: aiContext && aiContext.table instanceof Map ? aiContext.table : new Map(),
      transpositionLimit,
      deadline,
      killers: [],
      rootHint: aiContext && aiContext.lastBestMove ? cloneMoveDescriptor(aiContext.lastBestMove) : null
    };
    if (aiContext && !(aiContext.table instanceof Map)) {
      aiContext.table = context.table;
    }
    const colorSign = searchState.activeColor === WHITE ? 1 : -1;
    let bestResult = { score: Number.NEGATIVE_INFINITY, move: null, aborted: false, depth: 0 };
    let bestDescriptor = context.rootHint;
    for (let currentDepth = 1; currentDepth <= depth; currentDepth += 1) {
      const iteration = negamax(
        searchState,
        currentDepth,
        Number.NEGATIVE_INFINITY,
        Number.POSITIVE_INFINITY,
        colorSign,
        context,
        1
      );
      if (iteration.aborted) {
        break;
      }
      if (iteration.move) {
        bestResult = { ...iteration, depth: currentDepth };
        bestDescriptor = cloneMoveDescriptor(iteration.move);
        context.rootHint = bestDescriptor;
        if (aiContext) {
          aiContext.lastBestMove = bestDescriptor;
        }
      }
      if (context.deadline && context.deadline > 0 && getCurrentTimeMs() >= context.deadline) {
        break;
      }
    }

    if (!bestResult.move) {
      const fallbackMoves = generateLegalMoves(searchState);
      bestResult.move = fallbackMoves.length ? fallbackMoves[0] : null;
      bestResult.score = bestResult.move ? evaluateStaticPosition(applyMove(searchState, bestResult.move)) : 0;
      bestResult.depth = bestResult.move ? 1 : 0;
    }
    if (!bestDescriptor && bestResult.move) {
      bestDescriptor = cloneMoveDescriptor(bestResult.move);
    }
    if (aiContext && bestDescriptor) {
      aiContext.lastBestMove = bestDescriptor;
    }
    return bestResult;
  }

  function scheduleAIMove(state, ui) {
    if (!state || state.isGameOver || state.pendingPromotion || state.activeColor !== BLACK) {
      return;
    }
    ensureAiContext(state);
    if (state.aiThinking) {
      return;
    }

    state.aiThinking = true;
    state.ai.searchId += 1;
    const searchId = state.ai.searchId;
    const settings = state.ai.settings || DEFAULT_AI_SETTINGS;
    const delay = Number.isFinite(settings.moveDelayMs) && settings.moveDelayMs > 0 ? settings.moveDelayMs : 0;

    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.helperAiThinking',
      'The AI is thinking…',
      null,
      { duration: 0 }
    );
    updateStatus(state, ui);

    const scheduler = typeof window !== 'undefined' && typeof window.setTimeout === 'function'
      ? window.setTimeout.bind(window)
      : typeof setTimeout === 'function'
        ? setTimeout
        : null;

    const executeSearch = function () {
      const activeColorBefore = state.activeColor;
      const evaluationBefore = evaluateStaticPosition(state);
      const startTime = getCurrentTimeMs();
      const result = findBestAIMove(state, state.ai);
      const searchDuration = Math.max(0, getCurrentTimeMs() - startTime);
      if (searchId !== state.ai.searchId) {
        return;
      }
      state.aiThinking = false;
      clearHelperMessage(state);
      updateHelper(state, ui);
      if (!result.move) {
        updateStatus(state, ui);
        return;
      }
      makeMove(state, result.move, ui, {
        analysis: {
          type: 'ai',
          evaluationBefore,
          activeColorBefore,
          searchScore: result.score,
          depth: result.depth,
          durationMs: searchDuration
        }
      });
    };

    if (scheduler) {
      scheduler(executeSearch, delay);
    } else {
      executeSearch();
    }
  }

  function createInitialBoard() {
    const board = [];
    for (let row = 0; row < INITIAL_BOARD.length; row += 1) {
      board[row] = [];
      for (let col = 0; col < INITIAL_BOARD[row].length; col += 1) {
        board[row][col] = INITIAL_BOARD[row][col];
      }
    }
    return board;
  }

  function cloneBoard(board) {
    const clone = [];
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      clone[row] = [];
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        clone[row][col] = board[row][col];
      }
    }
    return clone;
  }

  function getOppositeColor(color) {
    return color === WHITE ? BLACK : WHITE;
  }

  function isOnBoard(row, col) {
    return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
  }

  function findKingPosition(board, color) {
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const piece = board[row][col];
        if (piece && getPieceColor(piece) === color && getPieceType(piece) === PIECE_TYPES.KING) {
          return { row, col };
        }
      }
    }
    return null;
  }

  function isSquareAttacked(board, targetRow, targetCol, attackerColor) {
    const opponent = attackerColor;
    const direction = opponent === WHITE ? -1 : 1;
    const pawnRow = targetRow - direction;

    for (let offset = -1; offset <= 1; offset += 2) {
      const pawnCol = targetCol + offset;
      if (!isOnBoard(pawnRow, pawnCol)) {
        continue;
      }
      const piece = board[pawnRow][pawnCol];
      if (piece && getPieceColor(piece) === opponent && getPieceType(piece) === PIECE_TYPES.PAWN) {
        return true;
      }
    }

    const knightMoves = [
      [-2, -1], [-2, 1],
      [-1, -2], [-1, 2],
      [1, -2], [1, 2],
      [2, -1], [2, 1]
    ];

    for (let i = 0; i < knightMoves.length; i += 1) {
      const move = knightMoves[i];
      const row = targetRow + move[0];
      const col = targetCol + move[1];
      if (!isOnBoard(row, col)) {
        continue;
      }
      const piece = board[row][col];
      if (piece && getPieceColor(piece) === opponent && getPieceType(piece) === PIECE_TYPES.KNIGHT) {
        return true;
      }
    }

    const directions = [
      [-1, 0],
      [1, 0],
      [0, -1],
      [0, 1],
      [-1, -1],
      [-1, 1],
      [1, -1],
      [1, 1]
    ];

    for (let i = 0; i < directions.length; i += 1) {
      const [dRow, dCol] = directions[i];
      let row = targetRow + dRow;
      let col = targetCol + dCol;
      let steps = 1;

      while (isOnBoard(row, col)) {
        const piece = board[row][col];
        if (piece) {
          const pieceColor = getPieceColor(piece);
          const pieceType = getPieceType(piece);
          if (pieceColor === opponent) {
            if (steps === 1 && pieceType === PIECE_TYPES.KING) {
              return true;
            }
            const isDiagonal = Math.abs(dRow) === Math.abs(dCol);
            const isStraight = dRow === 0 || dCol === 0;
            if ((isDiagonal && (pieceType === PIECE_TYPES.BISHOP || pieceType === PIECE_TYPES.QUEEN)) ||
              (isStraight && (pieceType === PIECE_TYPES.ROOK || pieceType === PIECE_TYPES.QUEEN))) {
              return true;
            }
          }
          break;
        }
        row += dRow;
        col += dCol;
        steps += 1;
      }
    }

    return false;
  }

  function generatePseudoMoves(state, row, col) {
    const moves = [];
    const piece = state.board[row][col];
    if (!piece) {
      return moves;
    }
    const color = getPieceColor(piece);
    const type = getPieceType(piece);

    if (type === PIECE_TYPES.PAWN) {
      generatePawnMoves(state, row, col, color, moves);
    } else if (type === PIECE_TYPES.KNIGHT) {
      generateKnightMoves(state.board, row, col, color, moves);
    } else if (type === PIECE_TYPES.BISHOP) {
      generateSlidingMoves(state.board, row, col, color, moves, [
        [-1, -1], [-1, 1], [1, -1], [1, 1]
      ]);
    } else if (type === PIECE_TYPES.ROOK) {
      generateSlidingMoves(state.board, row, col, color, moves, [
        [-1, 0], [1, 0], [0, -1], [0, 1]
      ]);
    } else if (type === PIECE_TYPES.QUEEN) {
      generateSlidingMoves(state.board, row, col, color, moves, [
        [-1, -1], [-1, 1], [1, -1], [1, 1],
        [-1, 0], [1, 0], [0, -1], [0, 1]
      ]);
    } else if (type === PIECE_TYPES.KING) {
      generateKingMoves(state, row, col, color, moves);
    }

    return moves;
  }

  function generatePawnMoves(state, row, col, color, moves) {
    const direction = color === WHITE ? -1 : 1;
    const startRow = color === WHITE ? 6 : 1;
    const promotionRow = color === WHITE ? 0 : 7;
    const board = state.board;

    const forwardRow = row + direction;
    if (isOnBoard(forwardRow, col) && !board[forwardRow][col]) {
      addPawnMove(row, col, forwardRow, col, color, false, false, promotionRow, moves);
      if (row === startRow) {
        const doubleRow = row + direction * 2;
        if (isOnBoard(doubleRow, col) && !board[doubleRow][col]) {
          moves.push({
            fromRow: row,
            fromCol: col,
            toRow: doubleRow,
            toCol: col,
            piece: board[row][col],
            isCapture: false,
            isEnPassant: false,
            isCastle: false,
            promotion: null,
            isDoubleStep: true
          });
        }
      }
    }

    for (let offset = -1; offset <= 1; offset += 2) {
      const captureCol = col + offset;
      const targetRow = row + direction;
      if (!isOnBoard(targetRow, captureCol)) {
        continue;
      }
      const targetPiece = board[targetRow][captureCol];
      if (targetPiece && getPieceColor(targetPiece) !== color) {
        addPawnMove(row, col, targetRow, captureCol, color, true, false, promotionRow, moves);
      }

      if (state.enPassant && state.enPassant.row === targetRow && state.enPassant.col === captureCol) {
        addPawnMove(row, col, targetRow, captureCol, color, true, true, promotionRow, moves);
      }
    }
  }

  function addPawnMove(fromRow, fromCol, toRow, toCol, color, isCapture, isEnPassant, promotionRow, moves) {
    const piece = color === WHITE ? 'wP' : 'bP';
    if (toRow === promotionRow) {
      for (let i = 0; i < PROMOTION_CHOICES.length; i += 1) {
        const promotion = PROMOTION_CHOICES[i];
        moves.push({
          fromRow,
          fromCol,
          toRow,
          toCol,
          piece,
          isCapture,
          isEnPassant,
          isCastle: false,
          promotion,
          isDoubleStep: false
        });
      }
    } else {
      moves.push({
        fromRow,
        fromCol,
        toRow,
        toCol,
        piece,
        isCapture,
        isEnPassant,
        isCastle: false,
        promotion: null,
        isDoubleStep: false
      });
    }
  }

  function generateKnightMoves(board, row, col, color, moves) {
    const knightMoves = [
      [-2, -1], [-2, 1],
      [-1, -2], [-1, 2],
      [1, -2], [1, 2],
      [2, -1], [2, 1]
    ];

    for (let i = 0; i < knightMoves.length; i += 1) {
      const move = knightMoves[i];
      const nextRow = row + move[0];
      const nextCol = col + move[1];
      if (!isOnBoard(nextRow, nextCol)) {
        continue;
      }
      const targetPiece = board[nextRow][nextCol];
      if (!targetPiece || getPieceColor(targetPiece) !== color) {
        moves.push({
          fromRow: row,
          fromCol: col,
          toRow: nextRow,
          toCol: nextCol,
          piece: board[row][col],
          isCapture: Boolean(targetPiece),
          isEnPassant: false,
          isCastle: false,
          promotion: null,
          isDoubleStep: false
        });
      }
    }
  }

  function generateSlidingMoves(board, row, col, color, moves, directions) {
    for (let i = 0; i < directions.length; i += 1) {
      const direction = directions[i];
      let nextRow = row + direction[0];
      let nextCol = col + direction[1];

      while (isOnBoard(nextRow, nextCol)) {
        const targetPiece = board[nextRow][nextCol];
        if (!targetPiece) {
          moves.push({
            fromRow: row,
            fromCol: col,
            toRow: nextRow,
            toCol: nextCol,
            piece: board[row][col],
            isCapture: false,
            isEnPassant: false,
            isCastle: false,
            promotion: null,
            isDoubleStep: false
          });
        } else {
          if (getPieceColor(targetPiece) !== color) {
            moves.push({
              fromRow: row,
              fromCol: col,
              toRow: nextRow,
              toCol: nextCol,
              piece: board[row][col],
              isCapture: true,
              isEnPassant: false,
              isCastle: false,
              promotion: null,
              isDoubleStep: false
            });
          }
          break;
        }
        nextRow += direction[0];
        nextCol += direction[1];
      }
    }
  }

  function generateKingMoves(state, row, col, color, moves) {
    const deltas = [
      [-1, -1], [-1, 0], [-1, 1],
      [0, -1], /* king stays */ [0, 1],
      [1, -1], [1, 0], [1, 1]
    ];

    for (let i = 0; i < deltas.length; i += 1) {
      const [dRow, dCol] = deltas[i];
      const nextRow = row + dRow;
      const nextCol = col + dCol;
      if (!isOnBoard(nextRow, nextCol)) {
        continue;
      }
      const targetPiece = state.board[nextRow][nextCol];
      if (!targetPiece || getPieceColor(targetPiece) !== color) {
        moves.push({
          fromRow: row,
          fromCol: col,
          toRow: nextRow,
          toCol: nextCol,
          piece: state.board[row][col],
          isCapture: Boolean(targetPiece),
          isEnPassant: false,
          isCastle: false,
          promotion: null,
          isDoubleStep: false
        });
      }
    }

    const opponent = getOppositeColor(color);
    const rights = color === WHITE ? state.castling.w : state.castling.b;
    const rowIndex = color === WHITE ? 7 : 0;

    if (rights.king) {
      const squares = [
        { row: rowIndex, col: 5 },
        { row: rowIndex, col: 6 }
      ];
      const rookSquare = { row: rowIndex, col: 7 };
      const pathClear = squares.every(square => !state.board[square.row][square.col]);
      const rook = state.board[rookSquare.row][rookSquare.col];
      const rookReady = rook && getPieceColor(rook) === color && getPieceType(rook) === PIECE_TYPES.ROOK;
      if (pathClear && rookReady) {
        const inCheck = isSquareAttacked(state.board, rowIndex, 4, opponent) ||
          squares.some(square => isSquareAttacked(state.board, square.row, square.col, opponent));
        if (!inCheck) {
          moves.push({
            fromRow: row,
            fromCol: col,
            toRow: rowIndex,
            toCol: 6,
            piece: state.board[row][col],
            isCapture: false,
            isEnPassant: false,
            isCastle: 'king',
            promotion: null,
            isDoubleStep: false
          });
        }
      }
    }

    if (rights.queen) {
      const squares = [
        { row: rowIndex, col: 1 },
        { row: rowIndex, col: 2 },
        { row: rowIndex, col: 3 }
      ];
      const rookSquare = { row: rowIndex, col: 0 };
      const pathClear = squares.every(square => !state.board[square.row][square.col]);
      const rook = state.board[rookSquare.row][rookSquare.col];
      const rookReady = rook && getPieceColor(rook) === color && getPieceType(rook) === PIECE_TYPES.ROOK;
      if (pathClear && rookReady) {
        const passingSquares = [
          { row: rowIndex, col: 4 },
          { row: rowIndex, col: 3 },
          { row: rowIndex, col: 2 }
        ];
        const inCheck = passingSquares.some(square => isSquareAttacked(state.board, square.row, square.col, opponent));
        if (!inCheck) {
          moves.push({
            fromRow: row,
            fromCol: col,
            toRow: rowIndex,
            toCol: 2,
            piece: state.board[row][col],
            isCapture: false,
            isEnPassant: false,
            isCastle: 'queen',
            promotion: null,
            isDoubleStep: false
          });
        }
      }
    }
  }

  function generateLegalMoves(state) {
    const pseudoMoves = [];
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const piece = state.board[row][col];
        if (!piece || getPieceColor(piece) !== state.activeColor) {
          continue;
        }
        const moves = generatePseudoMoves(state, row, col);
        for (let i = 0; i < moves.length; i += 1) {
          pseudoMoves.push(moves[i]);
        }
      }
    }

    const legalMoves = [];
    for (let i = 0; i < pseudoMoves.length; i += 1) {
      const move = pseudoMoves[i];
      const nextState = applyMove(state, move);
      if (!isKingInCheck(nextState, state.activeColor)) {
        legalMoves.push(move);
      }
    }
    return legalMoves;
  }

  function applyMove(state, move) {
    const board = cloneBoard(state.board);
    const piece = board[move.fromRow][move.fromCol];
    const color = getPieceColor(piece);
    const opponent = getOppositeColor(color);

    let captured = null;

    board[move.fromRow][move.fromCol] = '';

    if (move.isEnPassant) {
      const captureRow = move.toRow + (color === WHITE ? 1 : -1);
      captured = board[captureRow][move.toCol];
      board[captureRow][move.toCol] = '';
    } else {
      captured = board[move.toRow][move.toCol] || null;
    }

    let placedPiece = piece;
    if (move.promotion) {
      placedPiece = createPiece(color, move.promotion);
    }
    board[move.toRow][move.toCol] = placedPiece;

    const nextCastling = {
      w: { king: state.castling.w.king, queen: state.castling.w.queen },
      b: { king: state.castling.b.king, queen: state.castling.b.queen }
    };

    if (getPieceType(piece) === PIECE_TYPES.KING) {
      nextCastling[color].king = false;
      nextCastling[color].queen = false;
      if (move.isCastle === 'king') {
        const rookFromCol = 7;
        const rookToCol = 5;
        board[move.toRow][rookToCol] = board[move.toRow][rookFromCol];
        board[move.toRow][rookFromCol] = '';
      } else if (move.isCastle === 'queen') {
        const rookFromCol = 0;
        const rookToCol = 3;
        board[move.toRow][rookToCol] = board[move.toRow][rookFromCol];
        board[move.toRow][rookFromCol] = '';
      }
    }

    if (getPieceType(piece) === PIECE_TYPES.ROOK) {
      if (color === WHITE && move.fromRow === 7 && move.fromCol === 0) {
        nextCastling.w.queen = false;
      } else if (color === WHITE && move.fromRow === 7 && move.fromCol === 7) {
        nextCastling.w.king = false;
      } else if (color === BLACK && move.fromRow === 0 && move.fromCol === 0) {
        nextCastling.b.queen = false;
      } else if (color === BLACK && move.fromRow === 0 && move.fromCol === 7) {
        nextCastling.b.king = false;
      }
    }

    if (captured && getPieceType(captured) === PIECE_TYPES.ROOK) {
      if (opponent === WHITE && move.toRow === 7 && move.toCol === 0) {
        nextCastling.w.queen = false;
      } else if (opponent === WHITE && move.toRow === 7 && move.toCol === 7) {
        nextCastling.w.king = false;
      } else if (opponent === BLACK && move.toRow === 0 && move.toCol === 0) {
        nextCastling.b.queen = false;
      } else if (opponent === BLACK && move.toRow === 0 && move.toCol === 7) {
        nextCastling.b.king = false;
      }
    }

    const nextEnPassant = move.isDoubleStep
      ? { row: move.fromRow + (color === WHITE ? -1 : 1), col: move.fromCol }
      : null;

    const nextHalfmoveClock = getPieceType(piece) === PIECE_TYPES.PAWN || captured ? 0 : state.halfmoveClock + 1;

    const nextFullmove = color === BLACK ? state.fullmove + 1 : state.fullmove;

    return {
      board,
      activeColor: opponent,
      castling: nextCastling,
      enPassant: nextEnPassant,
      halfmoveClock: nextHalfmoveClock,
      fullmove: nextFullmove,
      lastMove: {
        fromRow: move.fromRow,
        fromCol: move.fromCol,
        toRow: move.toRow,
        toCol: move.toCol,
        piece,
        placedPiece,
        captured,
        promotion: move.promotion || null,
        isCastle: move.isCastle,
        isEnPassant: move.isEnPassant
      }
    };
  }

  function isKingInCheck(state, color) {
    const kingPosition = findKingPosition(state.board, color);
    if (!kingPosition) {
      return false;
    }
    return isSquareAttacked(state.board, kingPosition.row, kingPosition.col, getOppositeColor(color));
  }

  function updateLegalMoves(state) {
    const moves = generateLegalMoves(state);
    const map = new Map();
    for (let i = 0; i < moves.length; i += 1) {
      const move = moves[i];
      const key = move.fromRow + ',' + move.fromCol;
      if (!map.has(key)) {
        map.set(key, []);
      }
      map.get(key).push(move);
    }
    state.legalMoves = moves;
    state.legalMovesByFrom = map;
    return moves;
  }

  function getSquareColorIndex(row, col) {
    return (row + col) % 2;
  }

  function isInsufficientMaterial(board) {
    const pieces = [];
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const piece = board[row][col];
        if (!piece) {
          continue;
        }
        const type = getPieceType(piece);
        if (type === PIECE_TYPES.KING) {
          continue;
        }
        const color = getPieceColor(piece);
        const squareColor = getSquareColorIndex(row, col);
        pieces.push({ type, color, squareColor });
      }
    }

    if (!pieces.length) {
      return true;
    }

    if (pieces.length === 1) {
      const type = pieces[0].type;
      return type === PIECE_TYPES.BISHOP || type === PIECE_TYPES.KNIGHT;
    }

    if (pieces.length === 2) {
      const first = pieces[0];
      const second = pieces[1];
      if (first.type === PIECE_TYPES.BISHOP && second.type === PIECE_TYPES.BISHOP && first.squareColor === second.squareColor) {
        return true;
      }
    }

    return false;
  }

  function getPositionKey(state) {
    let boardKey = '';
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      let empty = 0;
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const piece = state.board[row][col];
        if (!piece) {
          empty += 1;
        } else {
          if (empty > 0) {
            boardKey += String(empty);
            empty = 0;
          }
          const color = getPieceColor(piece);
          const type = getPieceType(piece);
          boardKey += color === WHITE ? type.toUpperCase() : type;
        }
      }
      if (empty > 0) {
        boardKey += String(empty);
      }
      if (row < BOARD_SIZE - 1) {
        boardKey += '/';
      }
    }

    const active = state.activeColor;
    const castling = [
      state.castling.w.king ? 'K' : '',
      state.castling.w.queen ? 'Q' : '',
      state.castling.b.king ? 'k' : '',
      state.castling.b.queen ? 'q' : ''
    ].join('') || '-';

    const enPassant = state.enPassant
      ? FILES[state.enPassant.col] + RANKS[state.enPassant.row]
      : '-';

    return [boardKey, active, castling, enPassant].join(' ');
  }

  function evaluateGameState(state) {
    const legalMoves = state.legalMoves;
    const inCheck = isKingInCheck(state, state.activeColor);
    state.gameOutcome = null;
    state.isGameOver = false;

    if (legalMoves.length === 0) {
      if (inCheck) {
        state.gameOutcome = {
          type: 'checkmate',
          winner: getOppositeColor(state.activeColor)
        };
      } else {
        state.gameOutcome = { type: 'stalemate' };
      }
    } else {
      if (state.halfmoveClock >= 100) {
        state.gameOutcome = { type: 'draw', reason: 'fiftyMove' };
      } else if (state.positionCounts.get(state.positionKey) >= 3) {
        state.gameOutcome = { type: 'draw', reason: 'repetition' };
      } else if (isInsufficientMaterial(state.board)) {
        state.gameOutcome = { type: 'draw', reason: 'insufficient' };
      } else if (MATCH_CONFIG && Number.isFinite(MATCH_CONFIG.moveLimit) && MATCH_CONFIG.moveLimit > 0
        && state.fullmove > MATCH_CONFIG.moveLimit) {
        state.gameOutcome = { type: 'draw', reason: 'moveLimit' };
      }
    }

    state.isGameOver = Boolean(state.gameOutcome);
  }

  function createBoardSquares(boardElement, handleSquareClick) {
    const squares = [];
    boardElement.replaceChildren();

    for (let row = 0; row < BOARD_SIZE; row += 1) {
      squares[row] = [];
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'chess-square' + (getSquareColorIndex(row, col) === 1 ? ' chess-square--dark' : '');
        button.dataset.row = String(row);
        button.dataset.col = String(col);
        button.dataset.file = FILES[col];
        button.dataset.rank = RANKS[row];
        if (col === 0) {
          button.dataset.rankEdge = 'true';
        }
        if (row === BOARD_SIZE - 1) {
          button.dataset.fileEdge = 'true';
        }
        button.dataset.coordinate = FILES[col] + RANKS[row];
        button.setAttribute('role', 'gridcell');
        button.addEventListener('click', function () {
          handleSquareClick(row, col);
        });
        squares[row][col] = button;
        boardElement.appendChild(button);
      }
    }

    return squares;
  }

  function getPieceSymbol(piece) {
    if (!piece) {
      return '';
    }
    const color = getPieceColor(piece);
    const type = getPieceType(piece);
    const symbols = PIECE_SYMBOLS[color];
    return symbols ? symbols[type] || '' : '';
  }

  function getPieceLabel(piece) {
    if (!piece) {
      return '';
    }
    const color = getPieceColor(piece);
    const type = getPieceType(piece);
    const key = 'index.sections.echecs.pieces.' + color + '.' + type;
    const fallback = PIECE_FALLBACK_LABELS[color] ? PIECE_FALLBACK_LABELS[color][type] : '';
    return translate(key, fallback);
  }

  function toSquareNotation(row, col) {
    if (!Number.isInteger(row) || !Number.isInteger(col)) {
      return '';
    }
    const file = FILES[col] || '';
    const rank = RANKS[row] || '';
    return file + rank;
  }

  function sanitizePiece(value) {
    if (typeof value !== 'string') {
      return '';
    }
    const trimmed = value.trim();
    if (trimmed.length < 2) {
      return '';
    }
    const color = trimmed[0];
    const typeChar = trimmed[1].toLowerCase();
    if (color !== WHITE && color !== BLACK) {
      return '';
    }
    const allowed = Object.values(PIECE_TYPES);
    if (!allowed.includes(typeChar)) {
      return '';
    }
    return color + typeChar.toUpperCase();
  }

  function updateSquareAccessibility(button, piece, shouldUpdate) {
    if (!shouldUpdate) {
      return;
    }
    const coordinate = button.dataset.coordinate;
    if (piece) {
      const label = getPieceLabel(piece);
      const text = translate(
        'index.sections.echecs.accessibility.pieceOnSquare',
        '{{piece}} on {{square}}',
        { piece: label, square: coordinate }
      );
      button.setAttribute('aria-label', text);
    } else {
      const text = translate(
        'index.sections.echecs.accessibility.emptySquare',
        'Empty square {{square}}',
        { square: coordinate }
      );
      button.setAttribute('aria-label', text);
    }
  }

  function clearSquareState(button) {
    button.classList.remove(
      'is-selected',
      'is-legal-move',
      'is-legal-capture',
      'is-last-move',
      'is-check'
    );
  }

  function renderBoard(state, ui, options) {
    if (ui.boardElement) {
      const hideCoordinates = state.preferences && state.preferences.showCoordinates === false;
      ui.boardElement.classList.toggle('chess-board--hide-coordinates', hideCoordinates);
    }
    const forceAccessibility = Boolean(options && options.forceAccessibility);
    const selection = state.selection;
    const selectionIndex = selection ? (selection.row * BOARD_SIZE) + selection.col : -1;
    const selectionKey = selection ? selection.row + ',' + selection.col : null;
    const selectionMoves = selectionKey ? state.legalMovesByFrom.get(selectionKey) || [] : null;
    let selectionTargets = null;
    if (selectionMoves && selectionMoves.length > 0) {
      selectionTargets = new Map();
      for (let i = 0; i < selectionMoves.length; i += 1) {
        const move = selectionMoves[i];
        selectionTargets.set((move.toRow * BOARD_SIZE) + move.toCol, move);
      }
    }
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const button = ui.squares[row][col];
        const piece = state.board[row][col];
        const pieceKey = piece || '';
        const pieceChanged = button.dataset.piece !== pieceKey;
        if (pieceChanged) {
          button.dataset.piece = pieceKey;
          const symbol = getPieceSymbol(piece);
          if (button.textContent !== symbol) {
            button.textContent = symbol;
          }
          if (piece) {
            button.classList.add('has-piece');
          } else {
            button.classList.remove('has-piece');
          }
        }
        updateSquareAccessibility(button, piece, pieceChanged || forceAccessibility);
        const squareIndex = (row * BOARD_SIZE) + col;
        if (squareIndex === selectionIndex) {
          button.classList.add('is-selected');
        } else {
          button.classList.remove('is-selected');
        }
        const isDragTarget = state.dragContext
          && state.dragContext.hoverRow === row
          && state.dragContext.hoverCol === col;
        button.classList.toggle('is-drag-target', Boolean(isDragTarget));
        if (selection) {
          if (selectionTargets && selectionTargets.size > 0) {
            const move = selectionTargets.get(squareIndex);
            if (move) {
              if (move.isCapture) {
                button.classList.add('is-legal-capture');
                button.classList.remove('is-legal-move');
              } else {
                button.classList.add('is-legal-move');
                button.classList.remove('is-legal-capture');
              }
            } else {
              button.classList.remove('is-legal-move', 'is-legal-capture');
            }
          } else {
            button.classList.remove('is-legal-move', 'is-legal-capture');
          }
        } else {
          button.classList.remove('is-legal-move', 'is-legal-capture');
        }
        button.classList.remove('is-last-move', 'is-check');
      }
    }

    if (state.lastMove) {
      const fromButton = ui.squares[state.lastMove.fromRow][state.lastMove.fromCol];
      const toButton = ui.squares[state.lastMove.toRow][state.lastMove.toCol];
      if (fromButton) {
        fromButton.classList.add('is-last-move');
      }
      if (toButton) {
        toButton.classList.add('is-last-move');
      }
    }

    let checkColor = null;
    if (state.gameOutcome && state.gameOutcome.type === 'checkmate') {
      checkColor = getOppositeColor(state.gameOutcome.winner);
    } else if (isKingInCheck(state, state.activeColor)) {
      checkColor = state.activeColor;
    }

    if (checkColor) {
      const kingPosition = findKingPosition(state.board, checkColor);
      if (kingPosition) {
        const button = ui.squares[kingPosition.row][kingPosition.col];
        button.classList.add('is-check');
      }
    }
  }

  function triggerSquareAnimation(button, className) {
    if (!button || !className) {
      return;
    }
    button.classList.remove(className);
    // Force reflow to restart the animation when the class is re-added.
    void button.offsetWidth;
    button.classList.add(className);
    const remover = typeof window !== 'undefined' && typeof window.setTimeout === 'function'
      ? window.setTimeout.bind(window)
      : typeof setTimeout === 'function'
        ? setTimeout
        : null;
    if (remover) {
      remover(function () {
        button.classList.remove(className);
      }, 360);
    }
  }

  function animateMoveSquares(ui, move) {
    if (!ui || !ui.squares || !move) {
      return;
    }
    const fromRow = move.fromRow;
    const fromCol = move.fromCol;
    const toRow = move.toRow;
    const toCol = move.toCol;
    if (Number.isInteger(fromRow) && Number.isInteger(fromCol)) {
      const fromButton = ui.squares[fromRow] && ui.squares[fromRow][fromCol];
      triggerSquareAnimation(fromButton, 'chess-square--animate-move');
    }
    if (Number.isInteger(toRow) && Number.isInteger(toCol)) {
      const toButton = ui.squares[toRow] && ui.squares[toRow][toCol];
      const className = move.isCapture ? 'chess-square--animate-capture' : 'chess-square--animate-move';
      triggerSquareAnimation(toButton, className);
    }
  }

  function setStatusText(element, key, fallback, params) {
    if (!element) {
      return;
    }
    const text = translate(key, fallback, params);
    element.textContent = text;
  }

  function renderDifficultyOptions(state, ui) {
    if (!ui || !ui.difficultySelect) {
      return;
    }
    const select = ui.difficultySelect;
    const modes = Array.isArray(state.difficultyOptions) && state.difficultyOptions.length
      ? state.difficultyOptions
      : DIFFICULTY_CONFIG.modes;
    const fragment = document.createDocumentFragment();
    const current = state.difficulty ? state.difficulty.id : state.difficultyId;
    const seen = new Set();
    for (let i = 0; i < modes.length; i += 1) {
      const mode = modes[i];
      if (!mode || !mode.id || seen.has(mode.id)) {
        continue;
      }
      seen.add(mode.id);
      const option = document.createElement('option');
      option.value = mode.id;
      option.textContent = translate(mode.labelKey, mode.fallbackLabel);
      fragment.appendChild(option);
    }
    select.replaceChildren(fragment);
    if (current && seen.has(current)) {
      select.value = current;
    }
  }

  function updateDifficultyDescription(state, ui) {
    if (!ui || !ui.difficultyDescription) {
      return;
    }
    const mode = state.difficulty || resolveDifficultyMode(state.difficultyId);
    if (!mode) {
      ui.difficultyDescription.textContent = '';
      return;
    }
    const description = translate(mode.descriptionKey, mode.fallbackDescription);
    const reward = formatDifficultyRewardText(mode);
    ui.difficultyDescription.textContent = reward ? description + ' · ' + reward : description;
  }

  function updateDifficultyUI(state, ui) {
    renderDifficultyOptions(state, ui);
    updateDifficultyDescription(state, ui);
  }

  function applyAnalysisSummary(state, ui, analysis) {
    if (!ui || !ui.analysisElement || !ui.analysisText || !analysis) {
      return;
    }
    const normalized = normalizeAiAnalysis(analysis);
    if (!normalized) {
      ui.analysisElement.hidden = true;
      delete ui.analysisElement.dataset.visible;
      ui.analysisText.textContent = translate('index.sections.echecs.analysis.empty', 'No AI move to analyse yet.');
      return;
    }
    state.lastAiAnalysis = normalized;
    const scoreText = formatEvaluationScore(normalized.evaluationAfter);
    const deltaText = formatDeltaScore(normalized.evaluationDelta);
    const depthText = formatDepthText(normalized.depth);
    const advantageKey = normalized.evaluationAfter > 25
      ? 'white'
      : normalized.evaluationAfter < -25
        ? 'black'
        : 'balanced';
    const advantage = translate(
      'index.sections.echecs.analysis.advantage.' + advantageKey,
      advantageKey === 'white'
        ? 'avantage blanc'
        : advantageKey === 'black'
          ? 'avantage noir'
          : 'équilibre'
    );
    const fallback = normalized.moveSan
      ? 'Coup ' + normalized.moveSan + ' · ' + scoreText + ' (' + advantage + ')' + (deltaText ? ' · ' + deltaText : '') + (depthText ? ' · ' + depthText : '')
      : scoreText + ' (' + advantage + ')' + (deltaText ? ' · ' + deltaText : '') + (depthText ? ' · ' + depthText : '');
    const summary = translate(
      'index.sections.echecs.analysis.summary',
      fallback,
      {
        move: normalized.moveSan || '—',
        score: scoreText,
        advantage,
        delta: deltaText,
        depth: depthText
      }
    );
    ui.analysisText.textContent = summary;
    ui.analysisElement.hidden = false;
    ui.analysisElement.dataset.visible = 'true';
  }

  function updateAnalysisState(state, ui) {
    const analysis = state.lastAiAnalysis ? normalizeAiAnalysis(state.lastAiAnalysis) : null;
    if (analysis) {
      state.lastAiAnalysis = analysis;
    }
    if (ui && ui.analyzeButton) {
      ui.analyzeButton.disabled = !analysis;
      ui.analyzeButton.textContent = translate(
        'index.sections.echecs.controls.analyze',
        'Analyse last AI move'
      );
    }
    if (!ui || !ui.analysisElement || !ui.analysisText) {
      return;
    }
    if (!analysis) {
      ui.analysisElement.hidden = true;
      delete ui.analysisElement.dataset.visible;
      ui.analysisText.textContent = translate('index.sections.echecs.analysis.empty', 'No AI move to analyse yet.');
      return;
    }
    if (ui.analysisElement.dataset.visible === 'true') {
      applyAnalysisSummary(state, ui, analysis);
    } else {
      ui.analysisElement.hidden = true;
      ui.analysisText.textContent = translate(
        'index.sections.echecs.analysis.ready',
        'Cliquez sur « Analyser » pour détailler le dernier coup.'
      );
    }
  }

  function revealAnalysis(state, ui) {
    if (!state || !ui) {
      return;
    }
    const analysis = state.lastAiAnalysis ? normalizeAiAnalysis(state.lastAiAnalysis) : null;
    if (!analysis) {
      updateAnalysisState(state, ui);
      return;
    }
    applyAnalysisSummary(state, ui, analysis);
  }

  function applyDifficulty(state, ui, modeId) {
    if (!state) {
      return false;
    }
    const mode = resolveDifficultyMode(modeId);
    if (!mode) {
      return false;
    }
    const currentId = state.difficulty ? state.difficulty.id : state.difficultyId;
    if (currentId === mode.id) {
      updateDifficultyUI(state, ui);
      return false;
    }
    state.difficulty = mode;
    state.difficultyId = mode.id;
    state.ai = createAiContext(mode);
    state.aiThinking = false;
    state.lastAiAnalysis = null;
    if (ui && ui.analysisElement) {
      ui.analysisElement.hidden = true;
      delete ui.analysisElement.dataset.visible;
    }
    updateDifficultyUI(state, ui);
    updateAnalysisState(state, ui);
    saveProgress(state);
    if (!state.isGameOver && state.activeColor === BLACK) {
      scheduleAIMove(state, ui);
    }
    return true;
  }

  function updateStatus(state, ui) {
    if (!ui.statusElement) {
      return;
    }

    if (state.aiThinking) {
      setStatusText(
        ui.statusElement,
        'index.sections.echecs.status.aiThinking',
        'Black is thinking…'
      );
      if (ui.outcomeElement) {
        ui.outcomeElement.textContent = '';
      }
      return;
    }

    if (state.pendingPromotion) {
      setStatusText(
        ui.statusElement,
        'index.sections.echecs.status.promotion',
        'Choose a promotion piece.'
      );
      if (ui.outcomeElement) {
        ui.outcomeElement.textContent = '';
      }
      return;
    }

    if (!state.lastMove && state.halfmoveClock === 0) {
      setStatusText(
        ui.statusElement,
        'index.sections.echecs.status.ready',
        'Engine ready. White moves first.'
      );
      if (ui.outcomeElement) {
        ui.outcomeElement.textContent = '';
      }
      return;
    }

    if (state.gameOutcome) {
      if (state.gameOutcome.type === 'checkmate') {
        const winner = state.gameOutcome.winner === WHITE ? 'white' : 'black';
        setStatusText(
          ui.statusElement,
          'index.sections.echecs.outcome.checkmate.' + winner,
          winner === 'white' ? 'Checkmate! White wins.' : 'Checkmate! Black wins.'
        );
      } else if (state.gameOutcome.type === 'stalemate') {
        setStatusText(
          ui.statusElement,
          'index.sections.echecs.outcome.stalemate',
          'Stalemate. The game is drawn.'
        );
      } else if (state.gameOutcome.type === 'draw') {
        const reason = state.gameOutcome.reason;
        let key = 'index.sections.echecs.outcome.draw.generic';
        let fallback = 'Draw.';
        if (reason === 'fiftyMove') {
          key = 'index.sections.echecs.outcome.draw.fiftyMove';
          fallback = 'Draw by fifty-move rule.';
        } else if (reason === 'repetition') {
          key = 'index.sections.echecs.outcome.draw.repetition';
          fallback = 'Draw by threefold repetition.';
        } else if (reason === 'insufficient') {
          key = 'index.sections.echecs.outcome.draw.insufficient';
          fallback = 'Draw by insufficient material.';
        }
        setStatusText(ui.statusElement, key, fallback);
      }
    } else {
      const color = state.activeColor === WHITE ? 'white' : 'black';
      const keyBase = isKingInCheck(state, state.activeColor)
        ? 'index.sections.echecs.status.check.'
        : 'index.sections.echecs.status.turn.';
      const fallback = state.activeColor === WHITE
        ? (keyBase.indexOf('.check.') > -1 ? 'Check on White.' : 'White to move.')
        : (keyBase.indexOf('.check.') > -1 ? 'Check on Black.' : 'Black to move.');
      setStatusText(ui.statusElement, keyBase + color, fallback);
    }

    if (ui.outcomeElement) {
      if (state.gameOutcome && state.gameOutcome.type === 'draw') {
        const reason = state.gameOutcome.reason;
        const key = 'index.sections.echecs.outcome.draw.reason.' + reason;
        const fallbackMap = {
          fiftyMove: 'No capture or pawn move for fifty moves.',
          repetition: 'Position repeated three times.',
          insufficient: 'Not enough material to checkmate.'
        };
        setStatusText(ui.outcomeElement, key, fallbackMap[reason] || '');
      } else {
        ui.outcomeElement.textContent = '';
      }
    }
  }

  function updateHelper(state, ui) {
    if (!ui.helperElement) {
      return;
    }
    const message = state.helperMessage;
    if (message) {
      ui.helperElement.textContent = translate(message.key, message.fallback, message.params);
      return;
    }
    if (state.aiThinking) {
      ui.helperElement.textContent = translate(
        'index.sections.echecs.helperAiThinking',
        'The AI is thinking…'
      );
      return;
    }
    ui.helperElement.textContent = translate(
      'index.sections.echecs.helper',
      'Tap or drag a white piece to highlight legal moves.'
    );
  }

  function clearHelperMessage(state) {
    if (state.helperTimeoutId) {
      clearTimeout(state.helperTimeoutId);
      state.helperTimeoutId = null;
    }
    state.helperMessage = null;
  }

  function showInteractionMessage(state, ui, key, fallback, params, options) {
    clearHelperMessage(state);
    state.helperMessage = { key, fallback, params };
    updateHelper(state, ui);
    const duration = options && Number.isFinite(Number(options.duration)) && Number(options.duration) >= 0
      ? Number(options.duration)
      : 4000;
    if (duration > 0) {
      state.helperTimeoutId = setTimeout(function () {
        state.helperTimeoutId = null;
        state.helperMessage = null;
        updateHelper(state, ui);
      }, duration);
    }
  }

  function showPromotionDialog(state, ui, moves) {
    if (!ui.promotionElement || !ui.promotionOptionsElement) {
      return;
    }
    ui.promotionOptionsElement.replaceChildren();
    for (let i = 0; i < moves.length; i += 1) {
      const move = moves[i];
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'chess-promotion__option';
      const optionKey = PROMOTION_OPTION_KEYS[move.promotion] || move.promotion;
      const key = 'index.sections.echecs.promotion.options.' + optionKey;
      const fallback = getPieceLabel(createPiece(state.activeColor, move.promotion));
      button.textContent = translate(key, fallback);
      button.addEventListener('click', function () {
        hidePromotionDialog(ui);
        makeMove(state, move, ui);
      });
      ui.promotionOptionsElement.appendChild(button);
    }
    ui.promotionElement.hidden = false;
    state.pendingPromotion = moves;
    setStatusText(
      ui.statusElement,
      'index.sections.echecs.status.promotion',
      'Choose a promotion piece.'
    );
  }

  function hidePromotionDialog(ui) {
    if (!ui.promotionElement || !ui.promotionOptionsElement) {
      return;
    }
    ui.promotionOptionsElement.replaceChildren();
    ui.promotionElement.hidden = true;
  }

  function renderHistory(state, ui) {
    if (!ui.historyList) {
      return;
    }
    const entriesByMove = new Map();
    const history = Array.isArray(state.history) ? state.history : [];
    for (let index = 0; index < history.length; index += 1) {
      const entry = history[index];
      if (!entry || typeof entry !== 'object') {
        continue;
      }
      const moveNumber = Number(entry.moveNumber);
      const color = entry.color === BLACK ? BLACK : entry.color === WHITE ? WHITE : null;
      const san = typeof entry.san === 'string' ? entry.san : '';
      if (!Number.isFinite(moveNumber) || moveNumber <= 0 || !color || !san) {
        continue;
      }
      const key = Math.floor(moveNumber);
      if (!entriesByMove.has(key)) {
        entriesByMove.set(key, { number: key, white: '', black: '' });
      }
      const record = entriesByMove.get(key);
      if (color === WHITE) {
        record.white = san;
      } else {
        record.black = san;
      }
    }

    const moveNumbers = Array.from(entriesByMove.keys()).sort((a, b) => a - b);
    ui.historyList.replaceChildren();
    for (let i = 0; i < moveNumbers.length; i += 1) {
      const moveNumber = moveNumbers[i];
      const record = entriesByMove.get(moveNumber);
      const item = document.createElement('li');
      item.className = 'chess-history__item';
      const numberSpan = document.createElement('span');
      numberSpan.className = 'chess-history__move-number';
      numberSpan.textContent = moveNumber + '.';
      const whiteSpan = document.createElement('span');
      whiteSpan.className = 'chess-history__move chess-history__move--white';
      whiteSpan.textContent = record.white || '…';
      if (!record.white) {
        whiteSpan.classList.add('chess-history__move--empty');
      }
      const blackSpan = document.createElement('span');
      blackSpan.className = 'chess-history__move chess-history__move--black';
      blackSpan.textContent = record.black || '…';
      if (!record.black) {
        blackSpan.classList.add('chess-history__move--empty');
      }
      item.append(numberSpan, whiteSpan, blackSpan);
      ui.historyList.appendChild(item);
    }

    if (ui.historyEmpty) {
      ui.historyEmpty.hidden = moveNumbers.length > 0;
    }
  }

  function applyBoardPreferences(state, ui) {
    const preferences = state.preferences || {};
    const showCoordinates = preferences.showCoordinates !== false;
    const showHistory = preferences.showHistory !== false;
    if (ui.coordinatesToggle) {
      ui.coordinatesToggle.checked = showCoordinates;
    }
    if (ui.historyToggle) {
      ui.historyToggle.checked = showHistory;
    }
    if (ui.historyContainer) {
      ui.historyContainer.hidden = !showHistory;
    }
    if (ui.boardElement) {
      ui.boardElement.classList.toggle('chess-board--hide-coordinates', !showCoordinates);
    }
  }

  function getFenString(state) {
    const base = getPositionKey(state);
    return base + ' ' + state.halfmoveClock + ' ' + state.fullmove;
  }

  function buildAlgebraicNotation(state, move, nextState) {
    const pieceType = getPieceType(move.piece);
    const color = getPieceColor(move.piece);
    if (!pieceType || !color) {
      return { san: toSquareNotation(move.toRow, move.toCol), fen: getFenString(nextState) };
    }

    if (move.isCastle === 'king') {
      const opponentMoves = generateLegalMoves(nextState);
      const inCheck = isKingInCheck(nextState, nextState.activeColor);
      const suffix = inCheck ? (opponentMoves.length === 0 ? '#' : '+') : '';
      return { san: 'O-O' + suffix, fen: getFenString(nextState) };
    }
    if (move.isCastle === 'queen') {
      const opponentMoves = generateLegalMoves(nextState);
      const inCheck = isKingInCheck(nextState, nextState.activeColor);
      const suffix = inCheck ? (opponentMoves.length === 0 ? '#' : '+') : '';
      return { san: 'O-O-O' + suffix, fen: getFenString(nextState) };
    }

    const pieceLetterMap = {
      [PIECE_TYPES.PAWN]: '',
      [PIECE_TYPES.KNIGHT]: 'N',
      [PIECE_TYPES.BISHOP]: 'B',
      [PIECE_TYPES.ROOK]: 'R',
      [PIECE_TYPES.QUEEN]: 'Q',
      [PIECE_TYPES.KING]: 'K'
    };

    let notation = pieceLetterMap[pieceType] || '';

    if (pieceType !== PIECE_TYPES.PAWN) {
      const disambiguationCandidates = [];
      const legalMoves = Array.isArray(state.legalMoves) ? state.legalMoves : [];
      for (let index = 0; index < legalMoves.length; index += 1) {
        const candidate = legalMoves[index];
        if (!candidate || candidate === move) {
          continue;
        }
        if (candidate.toRow !== move.toRow || candidate.toCol !== move.toCol) {
          continue;
        }
        const candidatePiece = state.board[candidate.fromRow][candidate.fromCol];
        if (!candidatePiece) {
          continue;
        }
        if (getPieceColor(candidatePiece) !== color) {
          continue;
        }
        if (getPieceType(candidatePiece) !== pieceType) {
          continue;
        }
        disambiguationCandidates.push(candidate);
      }

      if (disambiguationCandidates.length) {
        const sameFile = disambiguationCandidates.some(candidate => candidate.fromCol === move.fromCol);
        const sameRank = disambiguationCandidates.some(candidate => candidate.fromRow === move.fromRow);
        if (!sameFile) {
          notation += FILES[move.fromCol];
        } else if (!sameRank) {
          notation += RANKS[move.fromRow];
        } else {
          notation += FILES[move.fromCol] + RANKS[move.fromRow];
        }
      }
    } else if (move.isCapture) {
      notation += FILES[move.fromCol];
    }

    if (move.isCapture) {
      notation += 'x';
    }

    notation += toSquareNotation(move.toRow, move.toCol);

    if (move.promotion) {
      notation += '=' + String(move.promotion).toUpperCase();
    }

    const opponentMoves = generateLegalMoves(nextState);
    const inCheck = isKingInCheck(nextState, nextState.activeColor);
    if (inCheck) {
      notation += opponentMoves.length === 0 ? '#' : '+';
    }

    return { san: notation, fen: getFenString(nextState) };
  }

  function clearSelection(state, ui) {
    state.selection = null;
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        clearSquareState(ui.squares[row][col]);
      }
    }
  }

  function selectSquare(state, ui, row, col) {
    state.selection = { row, col };
    renderBoard(state, ui);
    const button = ui.squares[row][col];
    if (button) {
      button.classList.add('is-selected');
    }
  }

  function attemptMove(state, ui, fromRow, fromCol, toRow, toCol) {
    const selectionKey = fromRow + ',' + fromCol;
    const moves = state.legalMovesByFrom.get(selectionKey) || [];
    const candidates = [];
    for (let i = 0; i < moves.length; i += 1) {
      const move = moves[i];
      if (move.toRow === toRow && move.toCol === toCol) {
        candidates.push(move);
      }
    }
    if (!candidates.length) {
      return false;
    }
    if (candidates.length === 1 && !candidates[0].promotion) {
      makeMove(state, candidates[0], ui);
    } else {
      showPromotionDialog(state, ui, candidates);
    }
    return true;
  }

  function handleSquareClick(state, ui, row, col) {
    if (state.isGameOver || state.pendingPromotion) {
      return;
    }

    if (state.aiThinking && state.activeColor === BLACK) {
      return;
    }

    if (state.suppressClick) {
      state.suppressClick = false;
      return;
    }

    const piece = state.board[row][col];
    const color = getPieceColor(piece);

    if (state.selection && state.selection.row === row && state.selection.col === col) {
      clearSelection(state, ui);
      renderBoard(state, ui);
      updateStatus(state, ui);
      updateHelper(state, ui);
      return;
    }

    if (piece && color === state.activeColor) {
      selectSquare(state, ui, row, col);
      return;
    }

    if (state.selection) {
      const moved = attemptMove(state, ui, state.selection.row, state.selection.col, row, col);
      if (!moved) {
        showInteractionMessage(
          state,
          ui,
          'index.sections.echecs.feedback.invalidMove',
          'That move is not legal.'
        );
      }
      return;
    }

    if (piece && color && color !== state.activeColor) {
      showInteractionMessage(
        state,
        ui,
        'index.sections.echecs.feedback.notYourTurn',
        'It is not that side to move.'
      );
      return;
    }

    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.helper',
      'Tap or drag a white piece to highlight its legal moves.'
    );
  }

  function getSquareFromPoint(x, y) {
    if (typeof document === 'undefined') {
      return null;
    }
    const element = document.elementFromPoint(x, y);
    if (!element) {
      return null;
    }
    const button = element.closest('[data-row][data-col]');
    if (!button || !button.dataset) {
      return null;
    }
    const row = Number.parseInt(button.dataset.row, 10);
    const col = Number.parseInt(button.dataset.col, 10);
    if (!Number.isInteger(row) || !Number.isInteger(col)) {
      return null;
    }
    if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
      return null;
    }
    return { row, col };
  }

  function cleanupDrag(state, ui) {
    const context = state.dragContext;
    if (!context) {
      return;
    }
    if (context.moveHandler) {
      document.removeEventListener('pointermove', context.moveHandler);
    }
    if (context.upHandler) {
      document.removeEventListener('pointerup', context.upHandler);
    }
    if (context.cancelHandler) {
      document.removeEventListener('pointercancel', context.cancelHandler);
    }
    state.dragContext = null;
    renderBoard(state, ui);
  }

  function beginDrag(state, ui, event, row, col) {
    cleanupDrag(state, ui);
    const context = {
      pointerId: event.pointerId,
      fromRow: row,
      fromCol: col,
      startX: event.clientX,
      startY: event.clientY,
      moved: false,
      hoverRow: null,
      hoverCol: null
    };
    context.moveHandler = function (moveEvent) {
      handlePointerMove(state, ui, moveEvent);
    };
    context.upHandler = function (upEvent) {
      handlePointerUp(state, ui, upEvent);
    };
    context.cancelHandler = function (cancelEvent) {
      handlePointerCancel(state, ui, cancelEvent);
    };
    state.dragContext = context;
    document.addEventListener('pointermove', context.moveHandler);
    document.addEventListener('pointerup', context.upHandler);
    document.addEventListener('pointercancel', context.cancelHandler);
  }

  function handlePointerDown(state, ui, event, row, col) {
    if (state.isGameOver || state.pendingPromotion) {
      return;
    }
    if (state.aiThinking && state.activeColor === BLACK) {
      return;
    }
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    const piece = state.board[row][col];
    if (!piece) {
      return;
    }
    const color = getPieceColor(piece);
    if (color !== state.activeColor) {
      if (!state.selection) {
        showInteractionMessage(
          state,
          ui,
          'index.sections.echecs.feedback.notYourTurn',
          'It is not that side to move.'
        );
      }
      return;
    }
    selectSquare(state, ui, row, col);
    beginDrag(state, ui, event, row, col);
    event.preventDefault();
  }

  function handlePointerMove(state, ui, event) {
    const context = state.dragContext;
    if (!context || event.pointerId !== context.pointerId) {
      return;
    }
    const dx = event.clientX - context.startX;
    const dy = event.clientY - context.startY;
    if (!context.moved) {
      const distance = Math.hypot(dx, dy);
      if (distance >= POINTER_DRAG_THRESHOLD) {
        context.moved = true;
      }
    }
    if (!context.moved) {
      return;
    }
    const square = getSquareFromPoint(event.clientX, event.clientY);
    const hoverRow = square ? square.row : null;
    const hoverCol = square ? square.col : null;
    if (hoverRow !== context.hoverRow || hoverCol !== context.hoverCol) {
      context.hoverRow = hoverRow;
      context.hoverCol = hoverCol;
      renderBoard(state, ui);
    }
    event.preventDefault();
  }

  function handlePointerUp(state, ui, event) {
    const context = state.dragContext;
    if (!context || event.pointerId !== context.pointerId) {
      return;
    }
    const wasMoved = context.moved;
    const square = getSquareFromPoint(event.clientX, event.clientY);
    cleanupDrag(state, ui);
    if (!wasMoved) {
      return;
    }
    state.suppressClick = true;
    if (!square) {
      showInteractionMessage(
        state,
        ui,
        'index.sections.echecs.feedback.dragCancelled',
        'Drag cancelled.'
      );
      return;
    }
    if (square.row === context.fromRow && square.col === context.fromCol) {
      showInteractionMessage(
        state,
        ui,
        'index.sections.echecs.feedback.dragCancelled',
        'Drag cancelled.'
      );
      return;
    }
    const moved = attemptMove(state, ui, context.fromRow, context.fromCol, square.row, square.col);
    if (!moved) {
      showInteractionMessage(
        state,
        ui,
        'index.sections.echecs.feedback.invalidMove',
        'That move is not legal.'
      );
      renderBoard(state, ui);
      updateStatus(state, ui);
      updateHelper(state, ui);
    }
    event.preventDefault();
  }

  function handlePointerCancel(state, ui, event) {
    const context = state.dragContext;
    if (!context || event.pointerId !== context.pointerId) {
      return;
    }
    cleanupDrag(state, ui);
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.feedback.dragCancelled',
      'Drag cancelled.'
    );
  }

  function attachPointerHandlers(state, ui) {
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const button = ui.squares[row][col];
        button.addEventListener('pointerdown', function (event) {
          handlePointerDown(state, ui, event, row, col);
        });
      }
    }
  }

  function normalizeStoredChessProgress(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }

    const board = [];
    const sourceBoard = Array.isArray(raw.board) ? raw.board : [];
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      const sourceRow = Array.isArray(sourceBoard[row]) ? sourceBoard[row] : [];
      const normalizedRow = [];
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        normalizedRow[col] = sanitizePiece(sourceRow[col]);
      }
      board.push(normalizedRow);
    }

    const activeColor = raw.activeColor === BLACK ? BLACK : WHITE;
    const castlingSource = raw.castling && typeof raw.castling === 'object' ? raw.castling : {};
    const castling = {
      w: {
        king: Boolean(castlingSource.w && castlingSource.w.king),
        queen: Boolean(castlingSource.w && castlingSource.w.queen)
      },
      b: {
        king: Boolean(castlingSource.b && castlingSource.b.king),
        queen: Boolean(castlingSource.b && castlingSource.b.queen)
      }
    };

    const enPassant = raw.enPassant
      && Number.isInteger(raw.enPassant.row)
      && Number.isInteger(raw.enPassant.col)
      && raw.enPassant.row >= 0
      && raw.enPassant.row < BOARD_SIZE
      && raw.enPassant.col >= 0
      && raw.enPassant.col < BOARD_SIZE
      ? { row: raw.enPassant.row, col: raw.enPassant.col }
      : null;

    const halfmoveClock = Number.isFinite(Number(raw.halfmoveClock)) && Number(raw.halfmoveClock) >= 0
      ? Math.floor(Number(raw.halfmoveClock))
      : 0;
    const fullmove = Number.isFinite(Number(raw.fullmove)) && Number(raw.fullmove) > 0
      ? Math.floor(Number(raw.fullmove))
      : 1;

    let lastMove = null;
    if (raw.lastMove && typeof raw.lastMove === 'object') {
      const fromRow = Number(raw.lastMove.fromRow);
      const fromCol = Number(raw.lastMove.fromCol);
      const toRow = Number(raw.lastMove.toRow);
      const toCol = Number(raw.lastMove.toCol);
      if (
        Number.isInteger(fromRow) && Number.isInteger(fromCol)
        && Number.isInteger(toRow) && Number.isInteger(toCol)
        && fromRow >= 0 && fromRow < BOARD_SIZE
        && fromCol >= 0 && fromCol < BOARD_SIZE
        && toRow >= 0 && toRow < BOARD_SIZE
        && toCol >= 0 && toCol < BOARD_SIZE
      ) {
        lastMove = {
          fromRow,
          fromCol,
          toRow,
          toCol,
          piece: sanitizePiece(raw.lastMove.piece),
          placedPiece: sanitizePiece(raw.lastMove.placedPiece),
          captured: sanitizePiece(raw.lastMove.captured || raw.lastMove.capturedPiece || ''),
          promotion: raw.lastMove.promotion || null,
          isCastle: raw.lastMove.isCastle || false,
          isEnPassant: raw.lastMove.isEnPassant || false
        };
      }
    }

    const history = [];
    if (Array.isArray(raw.history)) {
      for (let index = 0; index < raw.history.length; index += 1) {
        const entry = raw.history[index];
        if (!entry || typeof entry !== 'object') {
          continue;
        }
        const moveNumber = Number(entry.moveNumber);
        const color = entry.color === BLACK ? BLACK : entry.color === WHITE ? WHITE : null;
        const san = typeof entry.san === 'string' ? entry.san.trim() : '';
        const fen = typeof entry.fen === 'string' ? entry.fen : null;
        if (!Number.isFinite(moveNumber) || moveNumber <= 0 || !color || !san) {
          continue;
        }
        history.push({
          moveNumber: Math.floor(moveNumber),
          color,
          san,
          fen: fen || null
        });
      }
      history.sort(function (a, b) {
        if (a.moveNumber === b.moveNumber) {
          if (a.color === b.color) {
            return 0;
          }
          return a.color === WHITE ? -1 : 1;
        }
        return a.moveNumber - b.moveNumber;
      });
    }

    const positionCounts = new Map();
    if (Array.isArray(raw.positionCounts)) {
      raw.positionCounts.forEach(function (entry) {
        if (Array.isArray(entry) && entry.length >= 2) {
          const key = typeof entry[0] === 'string' ? entry[0] : null;
          const value = Number(entry[1]);
          if (key && Number.isFinite(value) && value > 0) {
            positionCounts.set(key, Math.floor(value));
          }
        } else if (entry && typeof entry === 'object' && typeof entry.key === 'string') {
          const value = Number(entry.value);
          if (Number.isFinite(value) && value > 0) {
            positionCounts.set(entry.key, Math.floor(value));
          }
        }
      });
    } else if (raw.positionCounts && typeof raw.positionCounts === 'object') {
      Object.keys(raw.positionCounts).forEach(function (key) {
        const value = Number(raw.positionCounts[key]);
        if (typeof key === 'string' && Number.isFinite(value) && value > 0) {
          positionCounts.set(key, Math.floor(value));
        }
      });
    }

    let gameOutcome = null;
    if (raw.gameOutcome && typeof raw.gameOutcome === 'object') {
      const type = typeof raw.gameOutcome.type === 'string' ? raw.gameOutcome.type : null;
      if (type === 'checkmate') {
        const winner = raw.gameOutcome.winner === BLACK ? BLACK : raw.gameOutcome.winner === WHITE ? WHITE : null;
        if (winner) {
          gameOutcome = { type: 'checkmate', winner };
        }
      } else if (type === 'stalemate') {
        gameOutcome = { type: 'stalemate' };
      } else if (type === 'draw') {
        const reason = typeof raw.gameOutcome.reason === 'string' ? raw.gameOutcome.reason : null;
        gameOutcome = reason ? { type: 'draw', reason } : { type: 'draw' };
      }
    }

    const preferences = {
      showCoordinates: raw.preferences && raw.preferences.showCoordinates === false ? false : true,
      showHistory: raw.preferences && raw.preferences.showHistory === false ? false : true
    };

    const difficultyIdRaw = typeof raw.difficultyId === 'string' && raw.difficultyId.trim()
      ? raw.difficultyId.trim()
      : typeof raw.difficulty === 'string' && raw.difficulty.trim()
        ? raw.difficulty.trim()
        : null;
    const difficulty = resolveDifficultyMode(difficultyIdRaw);
    const lastAiAnalysis = normalizeAiAnalysis(raw.lastAiAnalysis);

    const isGameOver = raw.isGameOver === true || (gameOutcome != null);

    return {
      board,
      activeColor,
      castling,
      enPassant,
      halfmoveClock,
      fullmove,
      lastMove,
      history,
      positionCounts,
      gameOutcome,
      isGameOver,
      preferences,
      difficulty,
      difficultyId: difficulty.id,
      lastAiAnalysis
    };
  }

  function readStoredProgress() {
    const globalState = getGlobalGameState();
    if (globalState && globalState.arcadeProgress && typeof globalState.arcadeProgress === 'object') {
      const normalized = normalizeStoredChessProgress(globalState.arcadeProgress.echecs);
      if (normalized) {
        return normalized;
      }
    }
    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          const normalized = normalizeStoredChessProgress(parsed);
          if (normalized) {
            return normalized;
          }
        }
      } catch (error) {
        // Ignore storage errors
      }
    }
    return null;
  }

  function applyStoredProgress(state, stored) {
    if (!stored) {
      return false;
    }
    state.board = stored.board;
    state.activeColor = stored.activeColor;
    state.castling = stored.castling;
    state.enPassant = stored.enPassant;
    state.halfmoveClock = stored.halfmoveClock;
    state.fullmove = stored.fullmove;
    state.lastMove = stored.lastMove;
    state.history = stored.history;
    state.positionCounts = stored.positionCounts;
    state.gameOutcome = stored.gameOutcome;
    state.isGameOver = stored.isGameOver;
    state.preferences = stored.preferences;
    state.pendingPromotion = null;
    state.selection = null;
    state.dragContext = null;
    state.helperMessage = null;
    state.helperTimeoutId = null;
    state.suppressClick = false;
    state.difficulty = stored.difficulty || resolveDifficultyMode(stored.difficultyId);
    state.difficultyId = state.difficulty ? state.difficulty.id : getDefaultDifficultyMode().id;
    state.difficultyOptions = DIFFICULTY_CONFIG.modes;
    state.lastAiAnalysis = stored.lastAiAnalysis ? normalizeAiAnalysis(stored.lastAiAnalysis) : null;
    ensureAiContext(state);
    state.aiThinking = false;

    if (state.ai) {
      state.ai.searchId = 0;
    }

    const key = getPositionKey(state);
    state.positionKey = key;
    if (!state.positionCounts.has(key)) {
      state.positionCounts.set(key, 1);
    }

    updateLegalMoves(state);
    evaluateGameState(state);
    return true;
  }

  function resetGameState(state, ui) {
    if (!state) {
      return;
    }
    const difficulty = state.difficulty || getDefaultDifficultyMode();
    const preferences = {
      showCoordinates: state.preferences && state.preferences.showCoordinates !== false,
      showHistory: state.preferences && state.preferences.showHistory !== false
    };
    const base = createInitialState();
    state.board = base.board;
    state.activeColor = base.activeColor;
    state.castling = base.castling;
    state.enPassant = base.enPassant;
    state.halfmoveClock = base.halfmoveClock;
    state.fullmove = base.fullmove;
    state.positionCounts = base.positionCounts;
    state.positionKey = base.positionKey;
    state.legalMoves = base.legalMoves;
    state.legalMovesByFrom = base.legalMovesByFrom;
    state.selection = null;
    state.lastMove = null;
    state.pendingPromotion = null;
    state.gameOutcome = null;
    state.isGameOver = false;
    state.history = [];
    state.preferences = preferences;
    state.dragContext = null;
    state.helperMessage = null;
    state.helperTimeoutId = null;
    state.suppressClick = false;
    state.difficulty = difficulty;
    state.difficultyId = difficulty.id;
    state.difficultyOptions = DIFFICULTY_CONFIG.modes;
    state.lastAiAnalysis = null;
    state.ai = createAiContext(difficulty);
    state.aiThinking = false;
    updateLegalMoves(state);
    evaluateGameState(state);
    clearSelection(state, ui);
    renderBoard(state, ui);
    renderHistory(state, ui);
    updateStatus(state, ui);
    clearHelperMessage(state);
    updateHelper(state, ui);
    applyBoardPreferences(state, ui);
    updateDifficultyUI(state, ui);
    updateAnalysisState(state, ui);
    saveProgress(state);
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.feedback.reset',
      'Partie réinitialisée.',
      null,
      { duration: 2500 }
    );
    if (!state.isGameOver && state.activeColor === BLACK) {
      scheduleAIMove(state, ui);
    }
  }

  function saveProgress(state) {
    const preferences = state.preferences || {};
    const payload = {
      board: state.board.map(function (row) {
        return row.map(function (cell) {
          return sanitizePiece(cell);
        });
      }),
      activeColor: state.activeColor,
      castling: (function () {
        const castlingState = state.castling && typeof state.castling === 'object' ? state.castling : {};
        const white = castlingState.w && typeof castlingState.w === 'object' ? castlingState.w : {};
        const black = castlingState.b && typeof castlingState.b === 'object' ? castlingState.b : {};
        return {
          w: { king: Boolean(white.king), queen: Boolean(white.queen) },
          b: { king: Boolean(black.king), queen: Boolean(black.queen) }
        };
      }()),
      enPassant: state.enPassant ? { row: state.enPassant.row, col: state.enPassant.col } : null,
      halfmoveClock: Math.max(0, Number(state.halfmoveClock) || 0),
      fullmove: Math.max(1, Number(state.fullmove) || 1),
      lastMove: state.lastMove
        ? {
            fromRow: state.lastMove.fromRow,
            fromCol: state.lastMove.fromCol,
            toRow: state.lastMove.toRow,
            toCol: state.lastMove.toCol,
            piece: sanitizePiece(state.lastMove.piece),
            placedPiece: sanitizePiece(state.lastMove.placedPiece),
            captured: sanitizePiece(state.lastMove.captured),
            promotion: state.lastMove.promotion || null,
            isCastle: Boolean(state.lastMove.isCastle),
            isEnPassant: Boolean(state.lastMove.isEnPassant)
          }
        : null,
      history: Array.isArray(state.history)
        ? state.history.map(function (entry) {
            return {
              moveNumber: entry.moveNumber,
              color: entry.color,
              san: entry.san,
              fen: entry.fen || null
            };
          })
        : [],
      positionCounts: Array.from(
        state.positionCounts instanceof Map ? state.positionCounts.entries() : []
      ),
      gameOutcome: state.gameOutcome ? { ...state.gameOutcome } : null,
      isGameOver: Boolean(state.isGameOver),
      preferences: {
        showCoordinates: preferences.showCoordinates !== false,
        showHistory: preferences.showHistory !== false
      },
      difficultyId: state.difficultyId || (state.difficulty && state.difficulty.id) || getDefaultDifficultyMode().id,
      lastAiAnalysis: serializeAiAnalysis(state.lastAiAnalysis)
    };

    const globalState = getGlobalGameState();
    if (globalState && typeof globalState === 'object') {
      if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
        globalState.arcadeProgress = {};
      }
      globalState.arcadeProgress.echecs = payload;
    }

    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(payload));
      } catch (error) {
        // Ignore storage errors
      }
    }

    requestSave();
  }

  function makeMove(state, move, ui, metadata) {
    const hadGameOutcome = Boolean(state.gameOutcome);
    const moveNumber = state.fullmove;
    const movingColor = state.activeColor;
    const nextState = applyMove(state, move);
    const notation = buildAlgebraicNotation(state, move, nextState);
    state.board = nextState.board;
    state.activeColor = nextState.activeColor;
    state.castling = nextState.castling;
    state.enPassant = nextState.enPassant;
    state.halfmoveClock = nextState.halfmoveClock;
    state.fullmove = nextState.fullmove;
    state.lastMove = nextState.lastMove;
    state.pendingPromotion = null;

    if (!Array.isArray(state.history)) {
      state.history = [];
    }
    state.history = state.history.concat({
      moveNumber,
      color: movingColor,
      san: notation.san,
      fen: notation.fen
    });

    if (!(state.positionCounts instanceof Map)) {
      state.positionCounts = new Map();
    }

    const key = getPositionKey(state);
    state.positionKey = key;
    const currentCount = state.positionCounts.get(key) || 0;
    state.positionCounts.set(key, currentCount + 1);

    updateLegalMoves(state);
    evaluateGameState(state);
    if (!hadGameOutcome
      && state.gameOutcome
      && state.gameOutcome.type === 'checkmate'
      && state.gameOutcome.winner === WHITE) {
      registerVictoryReward(state);
    }
    clearSelection(state, ui);
    renderBoard(state, ui);
    animateMoveSquares(ui, move);
    renderHistory(state, ui);
    updateStatus(state, ui);
    clearHelperMessage(state);
    updateHelper(state, ui);
    applyBoardPreferences(state, ui);
    if (metadata && metadata.analysis && metadata.analysis.type === 'ai') {
      state.lastAiAnalysis = createAiMoveAnalysis(state, move, metadata.analysis, notation);
    }
    updateAnalysisState(state, ui);
    saveProgress(state);
    if (!state.isGameOver && state.activeColor === BLACK) {
      scheduleAIMove(state, ui);
    }
  }

  function registerVictoryReward(state) {
    if (!state || !state.gameOutcome || state.gameOutcome.type !== 'checkmate' || state.gameOutcome.winner !== WHITE) {
      return;
    }
    const registrar = typeof window !== 'undefined' && typeof window.registerChessVictoryReward === 'function'
      ? window.registerChessVictoryReward
      : null;
    if (!registrar) {
      return;
    }
    const difficulty = state.difficulty || resolveDifficultyMode(state.difficultyId);
    if (!difficulty || !difficulty.reward) {
      return;
    }
    try {
      registrar({
        reward: difficulty.reward,
        difficultyId: difficulty.id,
        labelKey: difficulty.labelKey,
        fallbackLabel: difficulty.fallbackLabel
      });
    } catch (error) {
      console.warn('Chess reward registration failed', error);
    }
  }

  function updateBoardTranslations(section, ui, state) {
    const boardElement = section.querySelector(BOARD_SELECTOR);
    if (boardElement) {
      const ariaLabel = translate('index.sections.echecs.board', 'Standard chessboard');
      boardElement.setAttribute('aria-label', ariaLabel);
    }
    renderBoard(state, ui, { forceAccessibility: true });
    if (state.pendingPromotion && ui.promotionElement && !ui.promotionElement.hidden) {
      showPromotionDialog(state, ui, state.pendingPromotion);
    } else {
      updateStatus(state, ui);
    }
    if (ui.coordinatesLabel) {
      ui.coordinatesLabel.textContent = translate(
        'index.sections.echecs.controls.coordinates',
        'Show coordinates'
      );
    }
    if (ui.historyLabel) {
      ui.historyLabel.textContent = translate(
        'index.sections.echecs.controls.history',
        'Show move list'
      );
    }
    if (ui.historyTitle) {
      ui.historyTitle.textContent = translate(
        'index.sections.echecs.history.title',
        'Move list'
      );
    }
    if (ui.historyEmpty) {
      ui.historyEmpty.textContent = translate(
        'index.sections.echecs.history.empty',
        'No moves yet.'
      );
    }
    updateDifficultyUI(state, ui);
    updateAnalysisState(state, ui);
    updateHelper(state, ui);
    renderHistory(state, ui);
    applyBoardPreferences(state, ui);
  }

  function markSectionReady(section) {
    section.dataset.arcadeReady = 'true';
  }

  function createInitialState() {
    const board = createInitialBoard();
    const castling = {
      w: { king: true, queen: true },
      b: { king: true, queen: true }
    };
    const difficulty = getDefaultDifficultyMode();
    const state = {
      board,
      activeColor: WHITE,
      castling,
      enPassant: null,
      halfmoveClock: 0,
      fullmove: 1,
      positionCounts: new Map(),
      positionKey: '',
      legalMoves: [],
      legalMovesByFrom: new Map(),
      selection: null,
      lastMove: null,
      pendingPromotion: null,
      gameOutcome: null,
      isGameOver: false,
      history: [],
      preferences: { showCoordinates: true, showHistory: true },
      dragContext: null,
      helperMessage: null,
      helperTimeoutId: null,
      suppressClick: false,
      ai: createAiContext(difficulty),
      aiThinking: false,
      difficulty,
      difficultyId: difficulty.id,
      difficultyOptions: DIFFICULTY_CONFIG.modes,
      lastAiAnalysis: null
    };
    const key = getPositionKey(state);
    state.positionKey = key;
    state.positionCounts.set(key, 1);
    updateLegalMoves(state);
    return state;
  }

  onReady(function () {
    loadAiTestPositions();

    const section = document.getElementById(SECTION_ID);
    if (!section) {
      return;
    }

    const boardElement = section.querySelector(BOARD_SELECTOR);
    const statusElement = section.querySelector(STATUS_SELECTOR);
    const outcomeElement = section.querySelector(OUTCOME_SELECTOR);
    const helperElement = section.querySelector(HELPER_SELECTOR);
    const promotionElement = section.querySelector(PROMOTION_SELECTOR);
    const promotionOptionsElement = section.querySelector(PROMOTION_OPTIONS_SELECTOR);
    const coordinatesToggle = section.querySelector(COORDINATES_TOGGLE_SELECTOR);
    const historyToggle = section.querySelector(HISTORY_TOGGLE_SELECTOR);
    const historyContainer = section.querySelector(HISTORY_CONTAINER_SELECTOR);
    const historyList = section.querySelector(HISTORY_LIST_SELECTOR);
    const historyEmpty = section.querySelector(HISTORY_EMPTY_SELECTOR);
    const coordinatesLabel = section.querySelector('[data-i18n="index.sections.echecs.controls.coordinates"]');
    const historyLabel = section.querySelector('[data-i18n="index.sections.echecs.controls.history"]');
    const historyTitle = section.querySelector('[data-i18n="index.sections.echecs.history.title"]');
    const resetButton = section.querySelector(RESET_BUTTON_SELECTOR);
    const analyzeButton = section.querySelector(ANALYZE_BUTTON_SELECTOR);
    const analysisElement = section.querySelector(ANALYSIS_CONTAINER_SELECTOR);
    const analysisText = section.querySelector(ANALYSIS_TEXT_SELECTOR);
    const difficultySelect = section.querySelector(DIFFICULTY_SELECT_SELECTOR);
    const difficultyDescription = section.querySelector(DIFFICULTY_DESCRIPTION_SELECTOR);

    if (!boardElement || !statusElement) {
      return;
    }

    boardElement.setAttribute('role', 'grid');

    const state = createInitialState();
    const ui = {
      boardElement,
      squares: createBoardSquares(boardElement, function (row, col) {
        handleSquareClick(state, ui, row, col);
      }),
      statusElement,
      outcomeElement,
      helperElement,
      promotionElement,
      promotionOptionsElement,
      coordinatesToggle,
      historyToggle,
      historyContainer,
      historyList,
      historyEmpty,
      coordinatesLabel,
      historyLabel,
      historyTitle,
      resetButton,
      analyzeButton,
      analysisElement,
      analysisText,
      difficultySelect,
      difficultyDescription
    };

    attachPointerHandlers(state, ui);

    if (coordinatesToggle) {
      coordinatesToggle.addEventListener('change', function () {
        state.preferences = state.preferences || {};
        state.preferences.showCoordinates = coordinatesToggle.checked;
        applyBoardPreferences(state, ui);
        renderBoard(state, ui);
        saveProgress(state);
      });
    }
    if (historyToggle) {
      historyToggle.addEventListener('change', function () {
        state.preferences = state.preferences || {};
        state.preferences.showHistory = historyToggle.checked;
        applyBoardPreferences(state, ui);
        renderHistory(state, ui);
        saveProgress(state);
      });
    }
    if (difficultySelect) {
      difficultySelect.addEventListener('change', function () {
        applyDifficulty(state, ui, difficultySelect.value);
      });
    }
    if (resetButton) {
      resetButton.addEventListener('click', function () {
        resetGameState(state, ui);
      });
    }
    if (analyzeButton) {
      analyzeButton.addEventListener('click', function () {
        revealAnalysis(state, ui);
      });
    }

    const storedProgress = readStoredProgress();
    const hasStored = applyStoredProgress(state, storedProgress);

    applyBoardPreferences(state, ui);
    updateBoardTranslations(section, ui, state);
    if (!hasStored) {
      saveProgress(state);
    }
    markSectionReady(section);

    if (!state.isGameOver && state.activeColor === BLACK) {
      scheduleAIMove(state, ui);
    }

    window.addEventListener('i18n:languagechange', function () {
      updateBoardTranslations(section, ui, state);
    });
  });
})();
