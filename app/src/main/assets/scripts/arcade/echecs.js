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
  const HELP_BUTTON_SELECTOR = '[data-chess-help]';
  const PROMOTION_SELECTOR = '[data-chess-promotion]';
  const PROMOTION_OPTIONS_SELECTOR = '[data-chess-promotion-options]';
  const COORDINATES_TOGGLE_SELECTOR = '[data-chess-toggle-coordinates]';
  const HISTORY_TOGGLE_SELECTOR = '[data-chess-toggle-history]';
  const NOTATION_TOGGLE_SELECTOR = '[data-chess-notation-toggle]';
  const HISTORY_CONTAINER_SELECTOR = '[data-chess-history]';
  const HISTORY_LIST_SELECTOR = '[data-chess-history-list]';
  const HISTORY_EMPTY_SELECTOR = '[data-chess-history-empty]';
  const RESET_BUTTON_SELECTOR = '[data-chess-reset]';
  const ANALYZE_BUTTON_SELECTOR = '[data-chess-analyze]';
  const ANALYSIS_CONTAINER_SELECTOR = '[data-chess-analysis]';
  const ANALYSIS_TEXT_SELECTOR = '[data-chess-analysis-text]';
  const DIFFICULTY_SELECT_SELECTOR = '[data-chess-difficulty]';
  const DIFFICULTY_DESCRIPTION_SELECTOR = '[data-chess-difficulty-description]';
  const SAVE_BUTTON_SELECTOR = '[data-chess-save-game]';
  const SAVE_DIALOG_SELECTOR = '[data-chess-save-dialog]';
  const SAVE_INPUT_SELECTOR = '[data-chess-save-name]';
  const SAVE_CONFIRM_SELECTOR = '[data-chess-save-confirm]';
  const SAVE_CANCEL_SELECTOR = '[data-chess-save-cancel]';
  const ARCHIVE_SELECT_SELECTOR = '[data-chess-archive]';
  const ARCHIVE_EMPTY_SELECTOR = '[data-chess-archive-empty]';
  const ARCHIVE_RESTORE_SELECTOR = '[data-chess-archive-restore]';
  const ARCHIVE_NAVIGATION_SELECTOR = '[data-chess-archive-navigation]';
  const ARCHIVE_PREVIOUS_SELECTOR = '[data-chess-archive-prev]';
  const ARCHIVE_NEXT_SELECTOR = '[data-chess-archive-next]';

  const LOCAL_STORAGE_KEY = 'atom2univers.arcade.echecs';
  const STORAGE_VERSION = 3;
  const POINTER_DRAG_THRESHOLD = 6;
  const ARCHIVE_LIMIT = 25;
  const PGN_EVENT_NAME = 'Atom2Univers Chess';

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

  const NOTATION_STYLES = Object.freeze({
    SHORT: 'san',
    LONG: 'lan'
  });

  const PGN_RESULTS = Object.freeze({
    WHITE: '1-0',
    BLACK: '0-1',
    DRAW: '1/2-1/2',
    UNKNOWN: '*'
  });

  const DEFAULT_AI_SETTINGS = Object.freeze({
    depth: 3,
    timeLimitMs: 1200,
    moveDelayMs: 150,
    transpositionSize: 4000,
    creativity: Object.freeze({
      enabled: true,
      thresholdCentipawns: 80,
      variabilityCentipawns: 30,
      candidateCount: 3,
      excitementBonus: 0.35
    }),
    extensions: Object.freeze({
      captureDepthBonus: 1,
      checkDepthBonus: 1,
      tacticalMoveThreshold: 2,
      maxDepth: 5,
      timeBonusMs: 450,
      branchingThreshold: 26
    })
  });

  const DEFAULT_DIFFICULTY_MODES = Object.freeze([
    Object.freeze({
      id: 'training',
      labelKey: 'index.sections.echecs.difficulty.training',
      fallbackLabel: 'Mode entraînement',
      descriptionKey: 'index.sections.echecs.difficulty.trainingDescription',
      fallbackDescription: 'Recherche plus courte et coups rapides pour tester librement le plateau.',
      ai: Object.freeze({ depth: 2, timeLimitMs: 600, moveDelayMs: 120 }),
      reward: null
    }),
    Object.freeze({
      id: 'standard',
      labelKey: 'index.sections.echecs.difficulty.standard',
      fallbackLabel: 'Mode standard',
      descriptionKey: 'index.sections.echecs.difficulty.standardDescription',
      fallbackDescription: 'Configuration équilibrée : IA réactive et coups stables.',
      ai: Object.freeze({ depth: 3, timeLimitMs: 1200, moveDelayMs: 150 }),
      reward: Object.freeze({
        gachaTickets: 100,
        crit: Object.freeze({ multiplier: 1000, durationSeconds: 300 })
      })
    }),
    Object.freeze({
      id: 'expert',
      labelKey: 'index.sections.echecs.difficulty.expert',
      fallbackLabel: 'Mode expert',
      descriptionKey: 'index.sections.echecs.difficulty.expertDescription',
      fallbackDescription: 'Profondeur accrue et réflexion prolongée pour un défi soutenu.',
      ai: Object.freeze({ depth: 4, timeLimitMs: 1800, moveDelayMs: 180 }),
      reward: Object.freeze({
        gachaTickets: 100,
        crit: Object.freeze({ multiplier: 1000, durationSeconds: 300 })
      })
    }),
    Object.freeze({
      id: 'twoPlayer',
      labelKey: 'index.sections.echecs.difficulty.twoPlayers',
      fallbackLabel: 'Mode deux joueurs',
      descriptionKey: 'index.sections.echecs.difficulty.twoPlayersDescription',
      fallbackDescription: 'Affrontez un autre joueur sur le même appareil. L’IA reste désactivée.',
      twoPlayer: true,
      ai: null,
      reward: null
    })
  ]);

  const DEFAULT_DIFFICULTY_CONFIG = Object.freeze({
    defaultMode: 'standard',
    modes: DEFAULT_DIFFICULTY_MODES
  });

  const TRAINING_MODE_ID = 'training';
  const HELP_MODE_CANDIDATE_IDS = Object.freeze([
    'veryHard',
    'very-hard',
    'tresDifficile',
    'tresdifficile',
    'tres-difficile',
    'extreme',
    'hard',
    'hardcore',
    'expert'
  ]);

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

  const PIECE_SPRITES = Object.freeze({
    w: Object.freeze({
      p: 'Assets/sprites/pawn.png',
      n: 'Assets/sprites/knight.png',
      b: 'Assets/sprites/bishop.png',
      r: 'Assets/sprites/rook.png',
      q: 'Assets/sprites/queen.png',
      k: 'Assets/sprites/king.png'
    }),
    b: Object.freeze({
      p: 'Assets/sprites/pawn1.png',
      n: 'Assets/sprites/knight1.png',
      b: 'Assets/sprites/bishop1.png',
      r: 'Assets/sprites/rook1.png',
      q: 'Assets/sprites/queen1.png',
      k: 'Assets/sprites/king1.png'
    })
  });

  const SPRITE_STATUS = new Map();
  let latestRenderContext = null;

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

  function formatInteger(value) {
    const numeric = Number.isFinite(Number(value)) ? Math.floor(Number(value)) : 0;
    const safe = numeric >= 0 ? numeric : 0;
    try {
      return safe.toLocaleString();
    } catch (error) {
      return String(safe);
    }
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
    const source = raw && typeof raw === 'object' ? raw : {};

    const gachaCandidates = [
      source.gachaTickets,
      source.tickets,
      source.amount,
      base.gachaTickets
    ];
    let gachaTickets = 0;
    for (let index = 0; index < gachaCandidates.length; index += 1) {
      const candidate = Number(gachaCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        gachaTickets = Math.floor(candidate);
        break;
      }
    }

    const critSource = source.crit && typeof source.crit === 'object'
      ? source.crit
      : source.critical && typeof source.critical === 'object'
        ? source.critical
        : {};
    const fallbackCrit = base && typeof base.crit === 'object' ? base.crit : {};

    const multiplierCandidates = [
      critSource.multiplier,
      critSource.value,
      fallbackCrit.multiplier
    ];
    let multiplier = 0;
    for (let index = 0; index < multiplierCandidates.length; index += 1) {
      const candidate = Number(multiplierCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 1) {
        multiplier = candidate;
        break;
      }
    }

    const durationCandidates = [
      critSource.durationSeconds,
      critSource.seconds,
      critSource.duration,
      critSource.time,
      fallbackCrit.durationSeconds
    ];
    let durationSeconds = 0;
    for (let index = 0; index < durationCandidates.length; index += 1) {
      const candidate = Number(durationCandidates[index]);
      if (Number.isFinite(candidate) && candidate > 0) {
        durationSeconds = Math.floor(candidate);
        break;
      }
    }

    const crit = multiplier > 1 && durationSeconds > 0
      ? { multiplier, durationSeconds }
      : null;

    const normalizedTickets = Math.max(0, gachaTickets);
    if (!normalizedTickets && !crit) {
      return null;
    }

    return {
      gachaTickets: normalizedTickets,
      crit
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

    const baseTwoPlayer = Boolean(base.twoPlayer);
    const twoPlayer = source.twoPlayer === true
      ? true
      : source.twoPlayer === false
        ? false
        : baseTwoPlayer;

    let ai = null;
    let reward = null;

    if (!twoPlayer) {
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

      ai = { depth, timeLimitMs, moveDelayMs };
      reward = normalizeRewardConfig(source.reward, base.reward);
    }

    return {
      id,
      labelKey,
      fallbackLabel,
      descriptionKey,
      fallbackDescription,
      twoPlayer,
      ai,
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
    const ticketCount = Number.isFinite(Number(mode.reward.gachaTickets))
      ? Math.max(0, Math.floor(Number(mode.reward.gachaTickets)))
      : 0;
    const crit = mode.reward.crit && typeof mode.reward.crit === 'object' ? mode.reward.crit : null;
    const multiplierValue = crit && Number.isFinite(Number(crit.multiplier)) ? Number(crit.multiplier) : 0;
    const durationValue = crit && Number.isFinite(Number(crit.durationSeconds)) ? Number(crit.durationSeconds) : 0;
    const durationText = durationValue > 0 ? formatRewardDuration(durationValue) : '';
    const multiplierText = multiplierValue > 1 ? formatMultiplierText(multiplierValue) : '';
    const hasTickets = ticketCount > 0;
    const hasCrit = multiplierValue > 1 && Boolean(durationText);
    if (!hasTickets && !hasCrit) {
      return '';
    }
    const parts = [];
    if (hasTickets) {
      const suffix = ticketCount > 1 ? 's' : '';
      const ticketText = translate(
        'scripts.arcade.chess.reward.tickets',
        '{count} ticket{suffix} gacha',
        { count: formatInteger(ticketCount), suffix }
      );
      if (ticketText) {
        parts.push(ticketText);
      }
    }
    if (hasCrit) {
      const multiplierNumeric = multiplierText.replace(/^×/, '') || formatInteger(multiplierValue);
      const critText = translate(
        'scripts.arcade.chess.reward.crit',
        'Critique ×{multiplier} pendant {duration}',
        {
          multiplier: multiplierNumeric,
          multiplierText,
          duration: durationText
        }
      );
      if (critText) {
        parts.push(critText);
      }
    }
    if (!parts.length) {
      return '';
    }
    const rewardsText = parts.join(' · ');
    return translate(
      'index.sections.echecs.difficulty.reward',
      'Récompense : {rewards}',
      { rewards: rewardsText }
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

  function normalizeAiCreativitySettings(raw, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : DEFAULT_AI_SETTINGS.creativity;
    const result = {
      enabled: Boolean(base && base.enabled),
      thresholdCentipawns: Math.max(0, Number(base && base.thresholdCentipawns) || 0),
      variabilityCentipawns: Math.max(0, Number(base && base.variabilityCentipawns) || 0),
      candidateCount: Math.max(1, toPositiveInteger(base && base.candidateCount, DEFAULT_AI_SETTINGS.creativity.candidateCount)),
      excitementBonus: Math.max(0, Number(base && base.excitementBonus) || 0)
    };

    if (!raw || typeof raw !== 'object') {
      return result;
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'enabled')) {
      result.enabled = Boolean(raw.enabled);
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'thresholdCentipawns')) {
      const threshold = Number(raw.thresholdCentipawns);
      if (Number.isFinite(threshold) && threshold >= 0) {
        result.thresholdCentipawns = threshold;
      }
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'variabilityCentipawns')) {
      const variability = Number(raw.variabilityCentipawns);
      if (Number.isFinite(variability) && variability >= 0) {
        result.variabilityCentipawns = variability;
      }
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'candidateCount')) {
      const candidates = toPositiveInteger(raw.candidateCount, result.candidateCount);
      result.candidateCount = Math.max(1, candidates);
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'excitementBonus')) {
      const excitement = Number(raw.excitementBonus);
      if (Number.isFinite(excitement) && excitement >= 0) {
        result.excitementBonus = excitement;
      }
    }

    return result;
  }

  function normalizeAiExtensionSettings(raw, fallback) {
    const base = fallback && typeof fallback === 'object' ? fallback : DEFAULT_AI_SETTINGS.extensions;
    const result = {
      captureDepthBonus: Math.max(0, Number(base && base.captureDepthBonus) || 0),
      checkDepthBonus: Math.max(0, Number(base && base.checkDepthBonus) || 0),
      tacticalMoveThreshold: Math.max(1, toPositiveInteger(base && base.tacticalMoveThreshold, DEFAULT_AI_SETTINGS.extensions.tacticalMoveThreshold)),
      maxDepth: clampDepth((base && base.maxDepth) != null ? base.maxDepth : DEFAULT_AI_SETTINGS.extensions.maxDepth),
      timeBonusMs: Math.max(0, Number(base && base.timeBonusMs) || 0),
      branchingThreshold: Math.max(0, Number(base && base.branchingThreshold) || 0)
    };

    if (!raw || typeof raw !== 'object') {
      return result;
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'captureDepthBonus')) {
      const captureBonus = Number(raw.captureDepthBonus);
      if (Number.isFinite(captureBonus) && captureBonus >= 0) {
        result.captureDepthBonus = captureBonus;
      }
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'checkDepthBonus')) {
      const checkBonus = Number(raw.checkDepthBonus);
      if (Number.isFinite(checkBonus) && checkBonus >= 0) {
        result.checkDepthBonus = checkBonus;
      }
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'tacticalMoveThreshold')) {
      const tactical = toPositiveInteger(raw.tacticalMoveThreshold, result.tacticalMoveThreshold);
      result.tacticalMoveThreshold = Math.max(1, tactical);
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'maxDepth')) {
      result.maxDepth = clampDepth(raw.maxDepth);
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'timeBonusMs')) {
      const bonus = Number(raw.timeBonusMs);
      if (Number.isFinite(bonus) && bonus >= 0) {
        result.timeBonusMs = bonus;
      }
    }

    if (Object.prototype.hasOwnProperty.call(raw, 'branchingThreshold')) {
      const branching = Number(raw.branchingThreshold);
      if (Number.isFinite(branching) && branching >= 0) {
        result.branchingThreshold = branching;
      }
    }

    return result;
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
      if (overrides.transpositionSize != null) {
        transpositionSize = clampTranspositionSize(overrides.transpositionSize);
      }
    }

    let creativity = normalizeAiCreativitySettings(aiConfig && (aiConfig.creativity || aiConfig.personality), DEFAULT_AI_SETTINGS.creativity);
    let extensions = normalizeAiExtensionSettings(aiConfig && (aiConfig.searchExtensions || aiConfig.extensions), DEFAULT_AI_SETTINGS.extensions);

    if (difficultyMode && difficultyMode.ai) {
      const overrides = difficultyMode.ai;
      if (overrides.creativity) {
        creativity = normalizeAiCreativitySettings(overrides.creativity, creativity);
      }
      if (overrides.searchExtensions || overrides.extensions) {
        const rawExtensions = overrides.searchExtensions || overrides.extensions;
        extensions = normalizeAiExtensionSettings(rawExtensions, extensions);
      }
    }

    return {
      depth,
      timeLimitMs,
      moveDelayMs,
      transpositionSize,
      creativity: Object.freeze(creativity),
      extensions: Object.freeze(extensions)
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

  function replaceChildrenSafe(element) {
    if (!element) {
      return;
    }
    const children = Array.prototype.slice.call(arguments, 1);
    if (typeof element.replaceChildren === 'function') {
      element.replaceChildren.apply(element, children);
      return;
    }
    const doc = element.ownerDocument || (typeof document !== 'undefined' ? document : null);

    function appendNormalized(target, value) {
      if (value == null) {
        return;
      }
      const isNode = typeof Node === 'function'
        ? value instanceof Node
        : value && typeof value === 'object' && typeof value.nodeType === 'number';
      if (isNode) {
        target.appendChild(value);
        return;
      }
      if (Array.isArray(value)) {
        for (let index = 0; index < value.length; index += 1) {
          appendNormalized(target, value[index]);
        }
        return;
      }
      if (value && typeof value === 'object') {
        const iteratorSymbol = typeof Symbol !== 'undefined' ? Symbol.iterator : null;
        if (iteratorSymbol && typeof value[iteratorSymbol] === 'function') {
          const iterator = value[iteratorSymbol]();
          let step = iterator.next();
          while (!step.done) {
            appendNormalized(target, step.value);
            step = iterator.next();
          }
          return;
        }
      }
      if (doc) {
        target.appendChild(doc.createTextNode(String(value)));
      }
    }

    while (element.firstChild) {
      element.removeChild(element.firstChild);
    }
    for (let i = 0; i < children.length; i += 1) {
      appendNormalized(element, children[i]);
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
  const MAX_QUIESCENCE_PLY = 18;
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

  function doesMoveDeliverCheck(state, move) {
    if (!state || !move) {
      return false;
    }
    const nextState = applyMove(state, move);
    return isKingInCheck(nextState, nextState.activeColor);
  }

  function analyzeSearchPressure(state) {
    const moves = generateLegalMoves(state);
    let captureCount = 0;
    let checkingCount = 0;
    let promotionCount = 0;
    for (let i = 0; i < moves.length; i += 1) {
      const move = moves[i];
      if (!move) {
        continue;
      }
      if (move.isCapture) {
        captureCount += 1;
      }
      if (move.promotion) {
        promotionCount += 1;
      }
      if (doesMoveDeliverCheck(state, move)) {
        checkingCount += 1;
      }
    }
    return {
      totalMoves: moves.length,
      captureCount,
      checkingCount,
      promotionCount
    };
  }

  function computeExtendedSearchDepth(baseDepth, pressure, extensionSettings) {
    if (!pressure || !extensionSettings) {
      return baseDepth;
    }
    const maxDepthSetting = extensionSettings.maxDepth != null ? extensionSettings.maxDepth : DEFAULT_AI_SETTINGS.extensions.maxDepth;
    const maxDepth = clampDepth(maxDepthSetting);
    let targetDepth = Math.min(maxDepth, clampDepth(baseDepth));
    const tacticalThreshold = Math.max(1, Number(extensionSettings.tacticalMoveThreshold) || DEFAULT_AI_SETTINGS.extensions.tacticalMoveThreshold);
    const hasTacticalBurst = pressure.captureCount + pressure.promotionCount >= tacticalThreshold;
    if (hasTacticalBurst) {
      targetDepth = Math.min(maxDepth, targetDepth + Math.max(0, Number(extensionSettings.captureDepthBonus) || 0));
    }
    if (pressure.checkingCount > 0) {
      targetDepth = Math.min(maxDepth, targetDepth + Math.max(0, Number(extensionSettings.checkDepthBonus) || 0));
    }
    return targetDepth;
  }

  function computeAdditionalThinkTimeMs(pressure, extensionSettings) {
    if (!pressure || !extensionSettings) {
      return 0;
    }
    const bonus = Math.max(0, Number(extensionSettings.timeBonusMs) || 0);
    if (bonus <= 0) {
      return 0;
    }
    const branchingThreshold = Math.max(0, Number(extensionSettings.branchingThreshold) || 0);
    const hasBranching = branchingThreshold > 0 && pressure.totalMoves >= branchingThreshold;
    const hasChecks = pressure.checkingCount > 0;
    const hasCaptures = pressure.captureCount + pressure.promotionCount > 0;
    return hasBranching || hasChecks || hasCaptures ? bonus : 0;
  }

  function evaluateMoveExcitement(state, move, creativitySettings) {
    const excitementBonus = Math.max(0, Number(creativitySettings && creativitySettings.excitementBonus) || 0);
    let weight = 1;
    let givesCheck = false;
    if (move.isCapture) {
      weight += excitementBonus;
    }
    if (move.promotion) {
      weight += excitementBonus;
    }
    if (move.isCastle) {
      weight += excitementBonus * 0.5;
    }
    if (state) {
      givesCheck = doesMoveDeliverCheck(state, move);
      if (givesCheck) {
        weight += excitementBonus;
      }
    }
    return { weight, givesCheck };
  }

  function selectCreativeRootMove(state, result, creativitySettings) {
    if (!creativitySettings || !creativitySettings.enabled || !result || !Array.isArray(result.rootScores)) {
      return null;
    }
    if (result.rootScores.length < 2) {
      return null;
    }
    if (Math.abs(result.score) >= MATE_SCORE - 2000) {
      return null;
    }

    const threshold = Math.max(0, Number(creativitySettings.thresholdCentipawns) || 0);
    const variability = Math.max(0, Number(creativitySettings.variabilityCentipawns) || 0);
    const candidateLimit = Math.max(1, Math.min(result.rootScores.length, toPositiveInteger(creativitySettings.candidateCount, DEFAULT_AI_SETTINGS.creativity.candidateCount)));

    const sorted = result.rootScores
      .filter(function (entry) {
        return entry && entry.move && Number.isFinite(entry.score) && !entry.aborted;
      })
      .sort(function (a, b) {
        return b.score - a.score;
      });

    if (sorted.length < 2) {
      return null;
    }

    const bestScore = sorted[0].score;
    const candidates = [];
    for (let index = 0; index < sorted.length && candidates.length < candidateLimit; index += 1) {
      const entry = sorted[index];
      const diff = bestScore - entry.score;
      if (diff > threshold) {
        continue;
      }
      const excitement = evaluateMoveExcitement(state, entry.move, creativitySettings);
      const noise = variability > 0 ? (Math.random() * variability) : 0;
      const baseWeight = Math.max(0.001, threshold - diff + noise);
      const totalWeight = baseWeight * Math.max(0.25, excitement.weight);
      candidates.push({
        move: entry.move,
        score: entry.score,
        weight: totalWeight
      });
    }

    if (candidates.length <= 1) {
      return null;
    }

    let totalWeight = 0;
    for (let i = 0; i < candidates.length; i += 1) {
      totalWeight += candidates[i].weight;
    }

    if (totalWeight <= 0) {
      return null;
    }

    const pick = Math.random() * totalWeight;
    let accum = 0;
    for (let i = 0; i < candidates.length; i += 1) {
      const candidate = candidates[i];
      accum += candidate.weight;
      if (accum >= pick) {
        return candidate;
      }
    }

    return candidates[candidates.length - 1];
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

  function quiescenceSearch(state, alpha, beta, colorSign, context, ply) {
    const evaluation = colorSign * evaluateStaticPosition(state);
    if (context && context.deadline && getCurrentTimeMs() >= context.deadline) {
      return { score: evaluation, move: null, aborted: true };
    }
    if (ply >= MAX_QUIESCENCE_PLY) {
      return { score: evaluation, move: null, aborted: false };
    }

    if (evaluation >= beta) {
      return { score: evaluation, move: null, aborted: false };
    }
    let localAlpha = Math.max(alpha, evaluation);
    let bestScore = evaluation;
    let aborted = false;

    const tacticalMoves = generateQuiescenceMoves(state);
    if (!tacticalMoves.length) {
      return { score: evaluation, move: null, aborted: false };
    }

    const orderedMoves = orderMovesForSearch(state, tacticalMoves, null, context, ply);
    for (let i = 0; i < orderedMoves.length; i += 1) {
      const move = orderedMoves[i];
      const nextState = applyMove(state, move);
      const child = quiescenceSearch(nextState, -beta, -localAlpha, -colorSign, context, ply + 1);
      if (child.aborted) {
        aborted = true;
      }
      const score = -child.score;
      if (score > bestScore) {
        bestScore = score;
      }
      if (score > localAlpha) {
        localAlpha = score;
      }
      if (localAlpha >= beta || aborted) {
        break;
      }
    }

    return { score: bestScore, move: null, aborted };
  }

  function negamax(state, depth, alpha, beta, colorSign, context, ply) {
    const alphaOriginal = alpha;
    const isRoot = ply === 1;
    if (isRoot && Array.isArray(context.currentRootScores)) {
      context.currentRootScores.length = 0;
    }
    if (context.deadline && getCurrentTimeMs() >= context.deadline) {
      return { score: colorSign * evaluateStaticPosition(state), move: null, aborted: true };
    }

    if (depth === 0) {
      return quiescenceSearch(state, alpha, beta, colorSign, context, ply);
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
      if (isRoot && Array.isArray(context.currentRootScores)) {
        context.currentRootScores.push({ move, score, aborted: child.aborted });
      }
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
    const baseDepth = clampDepth(settings.depth);
    const transpositionLimit = clampTranspositionSize(settings.transpositionSize);
    const searchState = createSearchStateFromGameState(gameState);
    const extensions = settings.extensions || DEFAULT_AI_SETTINGS.extensions;
    const creativity = settings.creativity || DEFAULT_AI_SETTINGS.creativity;
    const pressure = analyzeSearchPressure(searchState);
    const targetDepth = computeExtendedSearchDepth(baseDepth, pressure, extensions);
    const baseDeadline = settings.timeLimitMs > 0 ? getCurrentTimeMs() + settings.timeLimitMs : 0;
    const deadline = baseDeadline > 0 ? baseDeadline + computeAdditionalThinkTimeMs(pressure, extensions) : 0;
    const context = {
      table: aiContext && aiContext.table instanceof Map ? aiContext.table : new Map(),
      transpositionLimit,
      deadline,
      killers: [],
      rootHint: aiContext && aiContext.lastBestMove ? cloneMoveDescriptor(aiContext.lastBestMove) : null,
      currentRootScores: []
    };
    if (aiContext && !(aiContext.table instanceof Map)) {
      aiContext.table = context.table;
    }
    const colorSign = searchState.activeColor === WHITE ? 1 : -1;
    let bestResult = { score: Number.NEGATIVE_INFINITY, move: null, aborted: false, depth: 0, rootScores: [] };
    let bestDescriptor = context.rootHint;
    for (let currentDepth = 1; currentDepth <= targetDepth; currentDepth += 1) {
      context.currentRootScores = [];
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
        const rootScores = Array.isArray(context.currentRootScores) ? context.currentRootScores.slice() : [];
        bestResult = { ...iteration, depth: currentDepth, rootScores };
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
    if (bestResult.move) {
      const creativeChoice = selectCreativeRootMove(searchState, bestResult, creativity);
      if (creativeChoice && creativeChoice.move) {
        bestResult.move = creativeChoice.move;
        bestResult.score = creativeChoice.score;
        bestDescriptor = cloneMoveDescriptor(creativeChoice.move);
      }
    }
    if (aiContext && bestDescriptor) {
      aiContext.lastBestMove = bestDescriptor;
    }
    if (bestResult.rootScores) {
      delete bestResult.rootScores;
    }
    return bestResult;
  }

  function scheduleAIMove(state, ui) {
    if (!state || state.isGameOver || state.pendingPromotion || state.activeColor !== BLACK) {
      return;
    }
    if (isTwoPlayerMode(state)) {
      return;
    }
    ensureAiContext(state);
    if (state.aiThinking) {
      return;
    }

    state.aiThinking = true;
    updateTrainingHelpState(state, ui);
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
      updateTrainingHelpState(state, ui);
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

  function generateQuiescenceMoves(state) {
    const moves = generateLegalMoves(state);
    const tacticalMoves = [];
    for (let i = 0; i < moves.length; i += 1) {
      const move = moves[i];
      if (!move) {
        continue;
      }
      if (move.isCapture || move.promotion) {
        tacticalMoves.push(move);
      }
    }
    return tacticalMoves;
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
    if (!state.isGameOver) {
      state.lastSavedOutcomeSignature = null;
    }
  }

  function createBoardSquares(boardElement, handleSquareClick) {
    const squares = [];
    replaceChildrenSafe(boardElement);

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

  function getPieceSprite(piece) {
    if (!piece) {
      return '';
    }
    const color = getPieceColor(piece);
    const type = getPieceType(piece);
    const sprites = PIECE_SPRITES[color];
    return sprites ? sprites[type] || '' : '';
  }

  function rememberRenderContext(state, ui, options) {
    latestRenderContext = {
      state: state,
      ui: ui,
      options: options || null
    };
  }

  function rerenderBoardFromSprites() {
    if (!latestRenderContext) {
      return;
    }
    renderBoard(latestRenderContext.state, latestRenderContext.ui, latestRenderContext.options);
  }

  function scheduleBoardRerender() {
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(rerenderBoardFromSprites);
    } else {
      setTimeout(rerenderBoardFromSprites, 0);
    }
  }

  function canUseSprite(spriteUrl) {
    if (!spriteUrl) {
      return false;
    }
    if (typeof Image !== 'function') {
      return false;
    }
    const status = SPRITE_STATUS.get(spriteUrl);
    if (status === 'loaded') {
      return true;
    }
    if (status === 'failed') {
      return false;
    }
    if (status !== 'pending') {
      SPRITE_STATUS.set(spriteUrl, 'pending');
      const image = new Image();
      image.addEventListener('load', function () {
        SPRITE_STATUS.set(spriteUrl, 'loaded');
        scheduleBoardRerender();
      });
      image.addEventListener('error', function () {
        SPRITE_STATUS.set(spriteUrl, 'failed');
        scheduleBoardRerender();
      });
      image.src = spriteUrl;
    }
    return false;
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
    rememberRenderContext(state, ui, options);
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
        const pieceColor = piece ? getPieceColor(piece) : null;
        const pieceKey = piece || '';
        const pieceChanged = button.dataset.piece !== pieceKey;
        if (pieceChanged) {
          button.dataset.piece = pieceKey;
        }
        if (pieceColor) {
          button.dataset.pieceColor = pieceColor;
        } else {
          delete button.dataset.pieceColor;
        }
        const existingSprite = button.querySelector('.chess-square__piece');
        const existingGlyph = button.querySelector('.chess-square__glyph');
        const spriteUrl = getPieceSprite(piece);
        const shouldUseSprite = Boolean(piece) && canUseSprite(spriteUrl);
        const usesSprite = button.classList.contains('chess-square--with-sprite');
        if (pieceChanged || usesSprite !== shouldUseSprite) {
          if (shouldUseSprite) {
            if (!existingSprite) {
              const spriteElement = document.createElement('span');
              spriteElement.className = 'chess-square__piece';
              spriteElement.setAttribute('aria-hidden', 'true');
              button.textContent = '';
              button.appendChild(spriteElement);
              spriteElement.style.backgroundImage = 'url(' + spriteUrl + ')';
            } else {
              existingSprite.style.backgroundImage = 'url(' + spriteUrl + ')';
              if (button.textContent !== '') {
                button.textContent = '';
              }
            }
            if (existingGlyph) {
              existingGlyph.remove();
            }
            button.classList.add('chess-square--with-sprite');
          } else {
            if (existingSprite) {
              existingSprite.remove();
            }
            button.classList.remove('chess-square--with-sprite');
            if (piece) {
              let glyph = existingGlyph;
              if (!glyph) {
                glyph = document.createElement('span');
                glyph.className = 'chess-square__glyph';
                glyph.setAttribute('aria-hidden', 'true');
                button.textContent = '';
                button.appendChild(glyph);
              }
              glyph.textContent = getPieceSymbol(piece);
            } else if (existingGlyph) {
              existingGlyph.remove();
              if (button.textContent !== '') {
                button.textContent = '';
              }
            } else if (button.textContent !== '') {
              button.textContent = '';
            }
          }
        } else if (!shouldUseSprite && piece && existingGlyph) {
          const symbol = getPieceSymbol(piece);
          if (existingGlyph.textContent !== symbol) {
            existingGlyph.textContent = symbol;
          }
        }
        if (piece) {
          button.classList.add('has-piece');
        } else {
          button.classList.remove('has-piece');
        }
        const isDragSource = state.dragContext
          && state.dragContext.moved
          && state.dragContext.fromRow === row
          && state.dragContext.fromCol === col;
        button.classList.toggle('is-drag-source', Boolean(isDragSource));
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
        const hint = state.trainingHint;
        const isHintSource = hint && hint.fromRow === row && hint.fromCol === col;
        const isHintTarget = hint && hint.toRow === row && hint.toCol === col;
        button.classList.toggle('is-hint-source', Boolean(isHintSource));
        button.classList.toggle('is-hint-target', Boolean(isHintTarget));
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
    replaceChildrenSafe(select, fragment);
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
    if (ui && ui.difficultySelect) {
      ui.difficultySelect.disabled = false;
      ui.difficultySelect.setAttribute('aria-disabled', 'false');
      const current = state.difficulty ? state.difficulty.id : state.difficultyId;
      if (current) {
        ui.difficultySelect.value = current;
      }
    }
    updateDifficultyDescription(state, ui);
    updateTrainingHelpState(state, ui);
  }

  function isTrainingDifficultyMode(mode) {
    return Boolean(mode && mode.id === TRAINING_MODE_ID);
  }

  function isTrainingMode(state) {
    if (!state) {
      return false;
    }
    const difficulty = state.difficulty || resolveDifficultyMode(state.difficultyId);
    return isTrainingDifficultyMode(difficulty) && !isTwoPlayerMode(state);
  }

  function resolveTrainingHelpDifficultyMode() {
    const modes = Array.isArray(DIFFICULTY_CONFIG.modes) && DIFFICULTY_CONFIG.modes.length
      ? DIFFICULTY_CONFIG.modes
      : DEFAULT_DIFFICULTY_MODES;

    for (let index = 0; index < HELP_MODE_CANDIDATE_IDS.length; index += 1) {
      const candidateId = HELP_MODE_CANDIDATE_IDS[index];
      if (typeof candidateId !== 'string' || !candidateId) {
        continue;
      }
      const candidate = findDifficultyMode(DIFFICULTY_CONFIG, candidateId);
      if (candidate && candidate.ai && !candidate.twoPlayer) {
        return candidate;
      }
    }

    let bestMode = null;
    let bestScore = Number.NEGATIVE_INFINITY;
    for (let i = 0; i < modes.length; i += 1) {
      const mode = modes[i];
      if (!mode || mode.twoPlayer || !mode.ai) {
        continue;
      }
      const settings = getConfiguredChessAiSettings(mode);
      const depth = clampDepth(settings.depth);
      const time = toNonNegativeNumber(settings.timeLimitMs, 0);
      const transposition = clampTranspositionSize(settings.transpositionSize);
      const score = (depth * 1e9) + (time * 1e3) + transposition;
      if (score > bestScore || !bestMode) {
        bestMode = mode;
        bestScore = score;
      }
    }
    return bestMode || resolveDifficultyMode('expert');
  }

  function canRequestTrainingHint(state) {
    if (!state) {
      return false;
    }
    if (!isTrainingMode(state)) {
      return false;
    }
    if (state.trainingHintPending || state.aiThinking) {
      return false;
    }
    if (state.pendingPromotion || state.isGameOver) {
      return false;
    }
    return state.activeColor === WHITE;
  }

  function updateTrainingHelpState(state, ui) {
    if (!ui || !ui.helpButton) {
      return;
    }
    const button = ui.helpButton;
    if (!isTrainingMode(state)) {
      button.hidden = true;
      button.setAttribute('aria-hidden', 'true');
      button.disabled = true;
      button.setAttribute('aria-disabled', 'true');
      button.textContent = translate('index.sections.echecs.controls.help', 'Aide');
      button.removeAttribute('aria-busy');
      button.removeAttribute('title');
      return;
    }

    button.hidden = false;
    button.setAttribute('aria-hidden', 'false');

    const workingLabel = translate('index.sections.echecs.controls.helpWorking', 'Calcul en cours…');
    const idleLabel = translate('index.sections.echecs.controls.help', 'Aide');
    const opponentTurnMessage = translate(
      'index.sections.echecs.controls.helpOpponentTurn',
      'Patientez, c’est au tour de l’IA.'
    );
    const gameOverMessage = translate(
      'index.sections.echecs.controls.helpGameOver',
      'La partie est terminée.'
    );
    const promotionMessage = translate(
      'index.sections.echecs.controls.helpPromotionPending',
      'Choisissez une promotion pour continuer.'
    );

    const opponentTurn = state.activeColor !== WHITE;
    const canRequest = canRequestTrainingHint(state);
    const isPending = state.trainingHintPending;
    const isGameOver = state.isGameOver;
    const awaitingPromotion = state.pendingPromotion;

    button.disabled = !canRequest;
    button.setAttribute('aria-disabled', button.disabled ? 'true' : 'false');
    button.textContent = isPending ? workingLabel : idleLabel;
    if (isPending) {
      button.setAttribute('aria-busy', 'true');
      button.title = workingLabel;
    } else {
      button.removeAttribute('aria-busy');
      if (isGameOver) {
        button.title = gameOverMessage;
      } else if (awaitingPromotion) {
        button.title = promotionMessage;
      } else if (opponentTurn || state.aiThinking) {
        button.title = opponentTurnMessage;
      } else {
        button.removeAttribute('title');
      }
    }
  }

  function clearTrainingHint(state) {
    if (!state) {
      return;
    }
    state.trainingHint = null;
  }

  function requestTrainingHint(state, ui) {
    if (!state || !ui || !ui.helpButton) {
      return;
    }
    if (!canRequestTrainingHint(state)) {
      updateTrainingHelpState(state, ui);
      return;
    }

    const helpMode = resolveTrainingHelpDifficultyMode();
    const effectiveMode = helpMode && helpMode.ai ? helpMode : resolveDifficultyMode('expert');
    const difficultyLabel = getDifficultyDisplayName(effectiveMode);

    clearTrainingHint(state);
    state.trainingHintPending = true;
    state.trainingHintRequestId = Number.isInteger(state.trainingHintRequestId)
      ? state.trainingHintRequestId + 1
      : 1;
    const requestId = state.trainingHintRequestId;
    const initialKey = state.positionKey;

    updateTrainingHelpState(state, ui);
    renderBoard(state, ui);
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.helperTrainingHintPending',
      'Recherche du meilleur coup ({difficulty})…',
      { difficulty: difficultyLabel },
      { duration: 0 }
    );

    const scheduler = typeof window !== 'undefined' && typeof window.setTimeout === 'function'
      ? window.setTimeout.bind(window)
      : typeof setTimeout === 'function'
        ? setTimeout
        : null;

    const execute = function () {
      let result = null;
      try {
        const settings = getConfiguredChessAiSettings(effectiveMode);
        const context = {
          settings: { ...settings, moveDelayMs: 0 },
          table: new Map(),
          searchId: 0,
          lastBestMove: null
        };
        result = findBestAIMove(state, context);
      } catch (error) {
        console.warn('Training hint search failed', error);
        if (requestId !== state.trainingHintRequestId) {
          return;
        }
        state.trainingHintPending = false;
        clearTrainingHint(state);
        renderBoard(state, ui);
        showInteractionMessage(
          state,
          ui,
          'index.sections.echecs.helperTrainingHintUnavailable',
          'Aucun conseil disponible ({difficulty}).',
          { difficulty: difficultyLabel },
          { duration: 5500 }
        );
        updateTrainingHelpState(state, ui);
        return;
      }

      if (requestId !== state.trainingHintRequestId) {
        return;
      }

      state.trainingHintPending = false;
      if (state.positionKey !== initialKey) {
        updateTrainingHelpState(state, ui);
        return;
      }

      if (result && result.move) {
        const preview = applyMove(state, result.move);
        const notation = buildAlgebraicNotation(state, result.move, preview);
        const from = toSquareNotation(result.move.fromRow, result.move.fromCol);
        const to = toSquareNotation(result.move.toRow, result.move.toCol);
        state.trainingHint = {
          fromRow: result.move.fromRow,
          fromCol: result.move.fromCol,
          toRow: result.move.toRow,
          toCol: result.move.toCol,
          san: notation.san,
          fromSquare: from,
          toSquare: to,
          difficulty: difficultyLabel
        };
        renderBoard(state, ui, { forceAccessibility: true });
        showInteractionMessage(
          state,
          ui,
          'index.sections.echecs.helperTrainingHint',
          'Suggestion ({difficulty}) : {move} ({from}→{to})',
          {
            difficulty: difficultyLabel,
            move: notation.san || (from + '→' + to),
            from,
            to
          },
          { duration: 6500 }
        );
      } else {
        clearTrainingHint(state);
        renderBoard(state, ui);
        showInteractionMessage(
          state,
          ui,
          'index.sections.echecs.helperTrainingHintUnavailable',
          'Aucun conseil disponible ({difficulty}).',
          { difficulty: difficultyLabel },
          { duration: 5500 }
        );
      }
      updateTrainingHelpState(state, ui);
    };

    if (scheduler) {
      scheduler(execute, 0);
    } else {
      execute();
    }
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
    const twoPlayer = isTwoPlayerMode(state);
    if (ui && ui.analyzeButton) {
      if (twoPlayer) {
        ui.analyzeButton.disabled = true;
        ui.analyzeButton.textContent = translate(
          'index.sections.echecs.controls.analyzeDisabled',
          'AI analysis unavailable in two-player mode.'
        );
      } else {
        const hasAnalysis = Boolean(state.lastAiAnalysis);
        ui.analyzeButton.disabled = !hasAnalysis;
        ui.analyzeButton.textContent = translate(
          'index.sections.echecs.controls.analyze',
          'Analyse last AI move'
        );
      }
    }
    if (!ui || !ui.analysisElement || !ui.analysisText) {
      return;
    }
    if (twoPlayer) {
      ui.analysisElement.hidden = true;
      delete ui.analysisElement.dataset.visible;
      ui.analysisText.textContent = translate(
        'index.sections.echecs.analysis.disabled',
        'AI analysis is unavailable in two-player mode.'
      );
      return;
    }
    const analysis = state.lastAiAnalysis ? normalizeAiAnalysis(state.lastAiAnalysis) : null;
    if (analysis) {
      state.lastAiAnalysis = analysis;
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
    if (isTwoPlayerMode(state)) {
      updateAnalysisState(state, ui);
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
    if (state.isArchivePreview) {
      state.isArchivePreview = false;
      state.preventAutosave = false;
      state.reviewingArchiveId = null;
      state.previewReturnSnapshot = null;
      state.previewReturnSlotId = null;
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
    saveProgress(state);

    clearTrainingHint(state);
    state.trainingHintPending = false;
    state.trainingHintRequestId = Number.isInteger(state.trainingHintRequestId)
      ? state.trainingHintRequestId + 1
      : 0;

    if (!(state.savedSlots instanceof Map)) {
      state.savedSlots = new Map();
    }

    let slotState = state.savedSlots.get(mode.id);
    if (!slotState) {
      slotState = createInitialSlotSnapshot(mode, state.preferences);
      state.savedSlots.set(mode.id, slotState);
    }

    applyNormalizedState(state, ui, slotState, { mode, skipSave: true });
    updateDifficultyUI(state, ui);
    updateAnalysisState(state, ui);
    updateStatus(state, ui);
    updateHelper(state, ui);
    updateSaveControls(state, ui);
    updateArchiveControls(state, ui);
    saveProgress(state);
    if (!state.isGameOver && state.activeColor === BLACK && !isTwoPlayerMode(state)) {
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
      ui.statusElement.textContent = '';
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
    ui.helperElement.textContent = '';
  }

  function determineGameResult(outcome) {
    if (!outcome || typeof outcome !== 'object') {
      return PGN_RESULTS.UNKNOWN;
    }
    if (outcome.type === 'checkmate') {
      if (outcome.winner === WHITE) {
        return PGN_RESULTS.WHITE;
      }
      if (outcome.winner === BLACK) {
        return PGN_RESULTS.BLACK;
      }
      return PGN_RESULTS.UNKNOWN;
    }
    if (outcome.type === 'stalemate' || outcome.type === 'draw') {
      return PGN_RESULTS.DRAW;
    }
    return PGN_RESULTS.UNKNOWN;
  }

  function getOutcomeSignature(state) {
    if (!state || !state.gameOutcome) {
      return null;
    }
    const result = determineGameResult(state.gameOutcome);
    const moveCount = Array.isArray(state.history) ? state.history.length : 0;
    return result + ':' + moveCount;
  }

  function updateSaveControls(state, ui) {
    if (!ui || !ui.saveButton) {
      return;
    }
    if (!state || state.isArchivePreview) {
      ui.saveButton.disabled = true;
      return;
    }
    const ready = Boolean(state.gameOutcome && state.isGameOver);
    const signature = getOutcomeSignature(state);
    const alreadySaved = Boolean(signature && state.lastSavedOutcomeSignature === signature);
    ui.saveButton.disabled = !ready || alreadySaved;
  }

  function updateArchiveControls(state, ui) {
    if (!ui || !ui.archiveSelect) {
      return;
    }
    const select = ui.archiveSelect;
    const entries = Array.isArray(state && state.archivedGames) ? state.archivedGames : [];
    const fragment = document.createDocumentFragment();
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = translate(
      'index.sections.echecs.controls.archives.placeholder',
      'Select a saved game'
    );
    fragment.appendChild(placeholder);
    for (let index = 0; index < entries.length; index += 1) {
      const entry = entries[index];
      if (!entry || typeof entry !== 'object' || typeof entry.id !== 'string') {
        continue;
      }
      const option = document.createElement('option');
      option.value = entry.id;
      const label = typeof entry.name === 'string' && entry.name.trim()
        ? entry.name.trim()
        : translate(
            'index.sections.echecs.controls.archives.unnamed',
            'Saved game {index}',
            { index: index + 1 }
          );
      option.textContent = label;
      fragment.appendChild(option);
    }
    replaceChildrenSafe(select, fragment);
    if (state && state.isArchivePreview && state.reviewingArchiveId) {
      select.value = state.reviewingArchiveId;
      if (!select.value) {
        select.selectedIndex = 0;
      }
    } else {
      select.value = '';
    }
    select.disabled = entries.length === 0;
    if (ui.archiveEmpty) {
      ui.archiveEmpty.hidden = entries.length > 0;
    }
    if (ui.archiveRestore) {
      ui.archiveRestore.hidden = !(state && state.isArchivePreview);
    }
    updateArchiveNavigation(state, ui);
  }

  function updateArchiveNavigation(state, ui) {
    if (!ui) {
      return;
    }
    const navigation = ui.archiveNavigation;
    const previous = ui.archivePrevious;
    const next = ui.archiveNext;
    const isPreview = Boolean(state && state.isArchivePreview);
    const history = Array.isArray(state && state.archivePreviewHistory)
      ? state.archivePreviewHistory
      : [];
    const maxIndex = history.length;
    const currentIndex = Number.isInteger(state && state.archivePreviewIndex)
      ? state.archivePreviewIndex
      : maxIndex;

    if (navigation) {
      navigation.hidden = !isPreview;
    }
    if (previous) {
      previous.disabled = !isPreview || currentIndex <= 0;
    }
    if (next) {
      next.disabled = !isPreview || currentIndex >= maxIndex;
    }
  }

  function formatArchiveTimestamp(timestamp) {
    const numeric = Number(timestamp);
    const date = Number.isFinite(numeric) ? new Date(numeric) : new Date();
    const pad = function (value) {
      return String(Math.floor(Math.abs(value))).padStart(2, '0');
    };
    return (
      date.getFullYear()
      + '-' + pad(date.getMonth() + 1)
      + '-' + pad(date.getDate())
      + ' · ' + pad(date.getHours())
      + ':' + pad(date.getMinutes())
    );
  }

  function getDifficultyDisplayName(mode) {
    const target = mode || getDefaultDifficultyMode();
    return translate(target.labelKey, target.fallbackLabel);
  }

  function generateDefaultArchiveName(state) {
    const difficulty = state
      ? state.difficulty || resolveDifficultyMode(state.difficultyId)
      : null;
    const label = getDifficultyDisplayName(difficulty || getDefaultDifficultyMode());
    return label + ' / ' + formatArchiveTimestamp(Date.now());
  }

  function openSaveDialog(state, ui) {
    if (!state || !ui || !ui.saveDialog || state.isArchivePreview) {
      return;
    }
    if (!state.gameOutcome || !state.isGameOver) {
      return;
    }
    const defaultName = generateDefaultArchiveName(state);
    ui.saveDialog.hidden = false;
    ui.saveDialog.setAttribute('aria-hidden', 'false');
    if (ui.saveInput) {
      ui.saveInput.value = defaultName;
      requestAnimationFrame(function () {
        try {
          ui.saveInput.focus();
          ui.saveInput.setSelectionRange(0, defaultName.length);
        } catch (error) {
          // Ignore focus issues
        }
      });
    }
  }

  function closeSaveDialog(ui) {
    if (!ui || !ui.saveDialog) {
      return;
    }
    ui.saveDialog.hidden = true;
    ui.saveDialog.setAttribute('aria-hidden', 'true');
    if (ui.saveInput) {
      ui.saveInput.blur();
    }
    if (ui.saveButton) {
      ui.saveButton.focus();
    }
  }

  function saveCompletedGame(state, ui, name) {
    if (!state || !state.gameOutcome || !state.isGameOver) {
      return false;
    }
    const signature = getOutcomeSignature(state);
    if (signature && state.lastSavedOutcomeSignature === signature) {
      return false;
    }
    const entry = createArchiveEntry(state, name);
    if (!entry) {
      return false;
    }
    if (!Array.isArray(state.archivedGames)) {
      state.archivedGames = [];
    }
    state.archivedGames.unshift(entry);
    if (state.archivedGames.length > ARCHIVE_LIMIT) {
      state.archivedGames.length = ARCHIVE_LIMIT;
    }
    state.lastSavedOutcomeSignature = signature || getOutcomeSignature(state);
    updateArchiveControls(state, ui);
    updateSaveControls(state, ui);
    saveProgress(state, { force: true });
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.helperSaved',
      'Game saved to archives!',
      { name: entry.name },
      { duration: 4000 }
    );
    return true;
  }

  function findArchiveEntry(state, id) {
    if (!state || !Array.isArray(state.archivedGames) || !id) {
      return null;
    }
    for (let index = 0; index < state.archivedGames.length; index += 1) {
      const entry = state.archivedGames[index];
      if (entry && entry.id === id) {
        return entry;
      }
    }
    return null;
  }

  function enterArchivePreview(state, ui, entry) {
    if (!state || !ui || !entry || !entry.normalizedState) {
      return false;
    }
    if (state.isArchivePreview && state.reviewingArchiveId === entry.id) {
      return true;
    }
    if (state.isArchivePreview) {
      restoreArchivePreview(state, ui);
    }
    const previousSnapshot = normalizeStoredChessState(createSerializableState(state));
    if (!previousSnapshot) {
      return false;
    }
    state.previewReturnSnapshot = previousSnapshot;
    state.previewReturnSlotId = state.activeSlotId
      || (state.difficulty && state.difficulty.id)
      || getDefaultDifficultyMode().id;
    state.isArchivePreview = true;
    state.preventAutosave = true;
    state.reviewingArchiveId = entry.id;
    const baseNormalized = entry.normalizedState;
    const history = Array.isArray(baseNormalized.history) ? baseNormalized.history.slice() : [];
    const mode = baseNormalized.difficulty || resolveDifficultyMode(baseNormalized.difficultyId);
    const initialSnapshot = createInitialSlotSnapshot(mode, baseNormalized.preferences);
    if (initialSnapshot) {
      initialSnapshot.isTwoPlayer = baseNormalized.isTwoPlayer === true;
    }
    state.archivePreviewBaseState = baseNormalized;
    state.archivePreviewHistory = history;
    state.archivePreviewInitialState = initialSnapshot || null;
    state.archivePreviewIndex = history.length;
    applyNormalizedState(state, ui, baseNormalized, { skipSave: true });
    state.activeSlotId = state.previewReturnSlotId;
    updateArchiveControls(state, ui);
    updateSaveControls(state, ui);
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.helperArchivePreview',
      'Viewing saved game “{name}”.',
      { name: entry.name },
      { duration: 5000 }
    );
    return true;
  }

  function restoreArchivePreview(state, ui) {
    if (!state || !state.isArchivePreview) {
      return false;
    }
    const snapshot = state.previewReturnSnapshot;
    const slotId = state.previewReturnSlotId;
    state.isArchivePreview = false;
    state.preventAutosave = false;
    state.reviewingArchiveId = null;
    state.previewReturnSnapshot = null;
    state.previewReturnSlotId = null;
    state.archivePreviewBaseState = null;
    state.archivePreviewHistory = null;
    state.archivePreviewInitialState = null;
    state.archivePreviewIndex = 0;
    if (snapshot) {
      applyNormalizedState(state, ui, snapshot, { skipSave: true });
    } else if (slotId && state.savedSlots instanceof Map && state.savedSlots.has(slotId)) {
      const slotState = state.savedSlots.get(slotId);
      if (slotState) {
        applyNormalizedState(state, ui, slotState, { skipSave: true });
      }
    } else {
      resetGameState(state, ui);
      return true;
    }
    updateArchiveControls(state, ui);
    updateSaveControls(state, ui);
    saveProgress(state, { force: true });
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.helperArchiveRestored',
      'Back to your current game.',
      null,
      { duration: 3500 }
    );
    return true;
  }

  function clearHelperMessage(state) {
    if (state.helperTimeoutId) {
      clearTimeout(state.helperTimeoutId);
      state.helperTimeoutId = null;
    }
    state.helperMessage = null;
  }

  function isTwoPlayerMode(state) {
    if (!state) {
      return false;
    }
    if (state.isTwoPlayer) {
      return true;
    }
    const difficulty = state.difficulty || resolveDifficultyMode(state.difficultyId);
    if (difficulty && difficulty.twoPlayer) {
      return true;
    }
    return state.difficultyId === 'twoPlayer';
  }

  function setTwoPlayerMode(state, ui, enabled, options) {
    if (!state) {
      return false;
    }
    const next = Boolean(enabled);
    const previous = isTwoPlayerMode(state);
    state.isTwoPlayer = next;
    if (ui && ui.boardElement) {
      ui.boardElement.classList.toggle('chess-board--two-player', next);
    }
    if (next) {
      if (state.ai && Number.isInteger(state.ai.searchId)) {
        state.ai.searchId += 1;
      }
      state.ai = null;
      state.aiThinking = false;
      state.lastAiAnalysis = null;
      clearHelperMessage(state);
    } else {
      ensureAiContext(state);
    }
    updateDifficultyUI(state, ui);
    updateAnalysisState(state, ui);
    updateStatus(state, ui);
    updateHelper(state, ui);
    updateSaveControls(state, ui);
    updateArchiveControls(state, ui);
    if (!(options && options.skipSave)) {
      saveProgress(state);
    }
    if (!next && !(options && options.skipSchedule) && !state.isGameOver && state.activeColor === BLACK) {
      scheduleAIMove(state, ui);
    }
    return next !== previous;
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
    const promotionMoves = Array.isArray(moves)
      ? moves.filter(function (move) {
        return move && move.promotion;
      })
      : [];
    if (!promotionMoves.length) {
      hidePromotionDialog(ui);
      state.pendingPromotion = null;
      return;
    }
    replaceChildrenSafe(ui.promotionOptionsElement);
    for (let i = 0; i < promotionMoves.length; i += 1) {
      const move = promotionMoves[i];
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
    state.pendingPromotion = promotionMoves;
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
    replaceChildrenSafe(ui.promotionOptionsElement);
    ui.promotionElement.hidden = true;
  }

  function createHistoryCaptureElement(piece) {
    if (!piece) {
      return null;
    }

    const label = getPieceLabel(piece);
    const spriteUrl = getPieceSprite(piece);
    if (spriteUrl) {
      const image = document.createElement('img');
      image.className = 'chess-history__capture';
      image.src = spriteUrl;
      image.alt = label;
      image.loading = 'lazy';
      image.decoding = 'async';
      return image;
    }

    const symbol = getPieceSymbol(piece);
    if (!symbol && !label) {
      return null;
    }

    const fallback = document.createElement('span');
    fallback.className = 'chess-history__capture chess-history__capture--fallback';
    fallback.textContent = symbol || '×';
    if (label) {
      fallback.setAttribute('role', 'img');
      fallback.setAttribute('aria-label', label);
    }
    return fallback;
  }

  function getNotationPreference(state) {
    if (!state || !state.preferences || state.preferences.notation !== NOTATION_STYLES.LONG) {
      return NOTATION_STYLES.SHORT;
    }
    return NOTATION_STYLES.LONG;
  }

  function getEntryNotation(entry, style) {
    if (!entry || typeof entry.san !== 'string') {
      return '';
    }
    if (style === NOTATION_STYLES.LONG) {
      const lan = typeof entry.lan === 'string' ? entry.lan.trim() : '';
      if (lan) {
        return lan;
      }
    }
    return entry.san;
  }

  function updateNotationToggle(state, ui) {
    if (!ui || !ui.notationToggle) {
      return;
    }
    const style = getNotationPreference(state);
    const isLong = style === NOTATION_STYLES.LONG;
    ui.notationToggle.setAttribute('aria-pressed', isLong ? 'true' : 'false');
    const key = isLong
      ? 'index.sections.echecs.controls.notation.long'
      : 'index.sections.echecs.controls.notation.short';
    const fallback = isLong ? 'Notation longue' : 'Notation courte';
    ui.notationToggle.dataset.i18n = key;
    ui.notationToggle.textContent = translate(key, fallback);
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
      const san = typeof entry.san === 'string' ? entry.san.trim() : '';
      const lan = typeof entry.lan === 'string' ? entry.lan.trim() : '';
      const capturedPiece = sanitizePiece(entry.captured);
      if (!Number.isFinite(moveNumber) || moveNumber <= 0 || !color || !san) {
        continue;
      }
      const key = Math.floor(moveNumber);
      if (!entriesByMove.has(key)) {
        entriesByMove.set(key, { number: key, white: null, black: null });
      }
      const record = entriesByMove.get(key);
      const slot = color === WHITE ? 'white' : 'black';
      record[slot] = {
        san,
        lan: lan || null,
        captured: capturedPiece || null
      };
    }

    const moveNumbers = Array.from(entriesByMove.keys()).sort((a, b) => a - b);
    replaceChildrenSafe(ui.historyList);

    const notationStyle = getNotationPreference(state);

    function renderMoveCell(cell, moveData) {
      replaceChildrenSafe(cell);
      cell.classList.remove('chess-history__move--empty');
      if (!moveData || !moveData.san) {
        cell.textContent = '…';
        cell.classList.add('chess-history__move--empty');
        return;
      }
      const notationSpan = document.createElement('span');
      notationSpan.className = 'chess-history__notation';
      const text = getEntryNotation(moveData, notationStyle);
      notationSpan.textContent = text || moveData.san;
      cell.appendChild(notationSpan);
      if (moveData.captured) {
        const captureElement = createHistoryCaptureElement(moveData.captured);
        if (captureElement) {
          cell.appendChild(captureElement);
        }
      }
    }

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
      renderMoveCell(whiteSpan, record.white);
      const blackSpan = document.createElement('span');
      blackSpan.className = 'chess-history__move chess-history__move--black';
      renderMoveCell(blackSpan, record.black);
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
    const notationStyle = getNotationPreference(state);
    state.preferences = Object.assign({}, preferences, { notation: notationStyle });
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
    updateNotationToggle(state, ui);
  }

  function getFenString(state) {
    const base = getPositionKey(state);
    return base + ' ' + state.halfmoveClock + ' ' + state.fullmove;
  }

  function parseFenString(fen) {
    if (typeof fen !== 'string') {
      return null;
    }
    const parts = fen.trim().split(/\s+/);
    if (parts.length < 4) {
      return null;
    }
    const placement = parts[0];
    const activePart = parts[1];
    const castlingPart = parts[2];
    const enPassantPart = parts[3];
    const halfmovePart = parts[4];
    const fullmovePart = parts[5];

    const rows = placement.split('/');
    if (rows.length !== BOARD_SIZE) {
      return null;
    }

    const board = [];
    for (let rowIndex = 0; rowIndex < BOARD_SIZE; rowIndex += 1) {
      const rowString = rows[rowIndex];
      const cells = [];
      let colIndex = 0;
      for (let i = 0; i < rowString.length && colIndex < BOARD_SIZE; i += 1) {
        const char = rowString[i];
        if (char >= '1' && char <= '8') {
          const emptyCount = Number(char);
          for (let empty = 0; empty < emptyCount && colIndex < BOARD_SIZE; empty += 1) {
            cells.push('');
            colIndex += 1;
          }
        } else {
          const lower = char.toLowerCase();
          const pieceMap = { p: 'P', n: 'N', b: 'B', r: 'R', q: 'Q', k: 'K' };
          const type = pieceMap[lower];
          if (!type) {
            return null;
          }
          const color = char === char.toUpperCase() ? WHITE : BLACK;
          cells.push(color + type);
          colIndex += 1;
        }
      }
      if (cells.length !== BOARD_SIZE) {
        return null;
      }
      board.push(cells);
    }

    const activeColor = activePart === BLACK ? BLACK : WHITE;
    const castling = {
      w: { king: false, queen: false },
      b: { king: false, queen: false }
    };
    if (typeof castlingPart === 'string' && castlingPart !== '-') {
      for (let index = 0; index < castlingPart.length; index += 1) {
        const token = castlingPart[index];
        if (token === 'K') {
          castling.w.king = true;
        } else if (token === 'Q') {
          castling.w.queen = true;
        } else if (token === 'k') {
          castling.b.king = true;
        } else if (token === 'q') {
          castling.b.queen = true;
        }
      }
    }

    let enPassant = null;
    if (typeof enPassantPart === 'string' && enPassantPart !== '-') {
      const file = enPassantPart[0];
      const rank = enPassantPart[1];
      const col = FILES.indexOf(file);
      const row = RANKS.indexOf(rank);
      if (col >= 0 && row >= 0) {
        enPassant = { row, col };
      }
    }

    const halfmoveClock = Number.isFinite(Number(halfmovePart)) && Number(halfmovePart) >= 0
      ? Math.floor(Number(halfmovePart))
      : 0;
    const fullmove = Number.isFinite(Number(fullmovePart)) && Number(fullmovePart) > 0
      ? Math.floor(Number(fullmovePart))
      : 1;

    return {
      board,
      activeColor,
      castling,
      enPassant,
      halfmoveClock,
      fullmove
    };
  }

  function parseLanMoveCoordinates(lan) {
    if (typeof lan !== 'string') {
      return null;
    }
    let trimmed = lan.trim();
    if (!trimmed) {
      return null;
    }
    while (trimmed.endsWith('+') || trimmed.endsWith('#')) {
      trimmed = trimmed.slice(0, -1);
    }
    let promotion = null;
    const promotionIndex = trimmed.indexOf('=');
    if (promotionIndex !== -1) {
      promotion = trimmed.slice(promotionIndex + 1).toLowerCase();
      trimmed = trimmed.slice(0, promotionIndex);
    }
    const match = trimmed.match(/^([KQRBNP])([a-h][1-8])([x-])([a-h][1-8])$/);
    if (!match) {
      return null;
    }
    const fromSquare = match[2];
    const toSquare = match[4];
    const fromCol = FILES.indexOf(fromSquare[0]);
    const fromRow = RANKS.indexOf(fromSquare[1]);
    const toCol = FILES.indexOf(toSquare[0]);
    const toRow = RANKS.indexOf(toSquare[1]);
    if (fromCol < 0 || fromRow < 0 || toCol < 0 || toRow < 0) {
      return null;
    }
    return {
      fromRow,
      fromCol,
      toRow,
      toCol,
      isCapture: match[3] === 'x',
      promotion,
      pieceLetter: match[1]
    };
  }

  function cloneHistorySlice(history, count) {
    const result = [];
    if (!Array.isArray(history) || count <= 0) {
      return result;
    }
    const limit = Math.min(history.length, Math.max(0, count));
    for (let index = 0; index < limit; index += 1) {
      const entry = history[index];
      if (!entry || typeof entry !== 'object') {
        continue;
      }
      result.push({
        moveNumber: entry.moveNumber,
        color: entry.color,
        san: entry.san,
        lan: typeof entry.lan === 'string' ? entry.lan : null,
        fen: typeof entry.fen === 'string' ? entry.fen : null,
        captured: entry.captured ? sanitizePiece(entry.captured) || null : null
      });
    }
    return result;
  }

  function createPreviewLastMove(entry) {
    if (!entry || typeof entry !== 'object') {
      return null;
    }
    const parsed = parseLanMoveCoordinates(entry.lan);
    if (!parsed) {
      return null;
    }
    return {
      fromRow: parsed.fromRow,
      fromCol: parsed.fromCol,
      toRow: parsed.toRow,
      toCol: parsed.toCol,
      piece: null,
      placedPiece: null,
      captured: sanitizePiece(entry.captured) || null,
      promotion: parsed.promotion || null,
      isCastle: parsed.pieceLetter === 'K' && Math.abs(parsed.toCol - parsed.fromCol) === 2,
      isEnPassant: false
    };
  }

  function buildArchivePreviewSnapshot(baseNormalized, history, moveCount, initialSnapshot) {
    if (!baseNormalized) {
      return null;
    }
    const totalMoves = Array.isArray(history) ? history.length : 0;
    const clamped = Math.max(0, Math.min(moveCount, totalMoves));
    if (clamped === totalMoves) {
      return baseNormalized;
    }
    if (clamped === 0) {
      if (initialSnapshot) {
        return initialSnapshot;
      }
      const mode = baseNormalized.difficulty || resolveDifficultyMode(baseNormalized.difficultyId);
      const snapshot = createInitialSlotSnapshot(mode, baseNormalized.preferences);
      if (snapshot) {
        snapshot.isTwoPlayer = baseNormalized.isTwoPlayer === true;
      }
      return snapshot;
    }
    const entry = history[clamped - 1];
    const fenData = entry ? parseFenString(entry.fen) : null;
    if (!fenData) {
      return null;
    }
    const preferencesSource = baseNormalized.preferences || {};
    const preferences = {
      showCoordinates: preferencesSource.showCoordinates !== false,
      showHistory: preferencesSource.showHistory !== false,
      notation: preferencesSource.notation === NOTATION_STYLES.LONG
        ? NOTATION_STYLES.LONG
        : NOTATION_STYLES.SHORT
    };
    const positionCounts = new Map();
    const snapshot = {
      board: fenData.board,
      activeColor: fenData.activeColor,
      castling: cloneCastlingRights(fenData.castling),
      enPassant: fenData.enPassant ? { ...fenData.enPassant } : null,
      halfmoveClock: fenData.halfmoveClock,
      fullmove: fenData.fullmove,
      lastMove: createPreviewLastMove(entry),
      history: cloneHistorySlice(history, clamped),
      positionCounts,
      gameOutcome: null,
      isGameOver: false,
      preferences,
      difficulty: baseNormalized.difficulty,
      difficultyId: baseNormalized.difficultyId,
      lastAiAnalysis: null,
      isTwoPlayer: baseNormalized.isTwoPlayer === true
    };
    const key = getPositionKey(snapshot);
    positionCounts.set(key, 1);
    return snapshot;
  }

  function setArchivePreviewIndex(state, ui, index) {
    if (!state || !state.isArchivePreview) {
      return false;
    }
    const history = Array.isArray(state.archivePreviewHistory) ? state.archivePreviewHistory : [];
    const baseNormalized = state.archivePreviewBaseState;
    if (!baseNormalized) {
      return false;
    }
    const maxIndex = history.length;
    const numericIndex = Number.isFinite(Number(index)) ? Number(index) : maxIndex;
    const clamped = Math.max(0, Math.min(Math.floor(numericIndex), maxIndex));
    let snapshot = null;
    if (clamped === maxIndex) {
      snapshot = baseNormalized;
    } else if (clamped === 0) {
      snapshot = state.archivePreviewInitialState
        || buildArchivePreviewSnapshot(baseNormalized, history, 0, null);
      if (snapshot && !state.archivePreviewInitialState) {
        state.archivePreviewInitialState = snapshot;
      }
    } else {
      snapshot = buildArchivePreviewSnapshot(
        baseNormalized,
        history,
        clamped,
        state.archivePreviewInitialState
      );
    }
    if (!snapshot) {
      return false;
    }
    applyNormalizedState(state, ui, snapshot, { skipSave: true });
    state.isArchivePreview = true;
    state.preventAutosave = true;
    state.archivePreviewHistory = history;
    state.archivePreviewBaseState = baseNormalized;
    state.archivePreviewIndex = clamped;
    if (state.previewReturnSlotId) {
      state.activeSlotId = state.previewReturnSlotId;
    }
    updateArchiveControls(state, ui);
    updateSaveControls(state, ui);
    return true;
  }

  function stepArchivePreview(state, ui, delta) {
    if (!state || !state.isArchivePreview) {
      return;
    }
    const history = Array.isArray(state.archivePreviewHistory) ? state.archivePreviewHistory : [];
    const currentIndex = Number.isInteger(state.archivePreviewIndex)
      ? state.archivePreviewIndex
      : history.length;
    setArchivePreviewIndex(state, ui, currentIndex + delta);
  }

  function buildAlgebraicNotation(state, move, nextState) {
    const pieceType = getPieceType(move.piece);
    const color = getPieceColor(move.piece);
    const toSquare = toSquareNotation(move.toRow, move.toCol);
    const hasFromCoordinates = Number.isInteger(move.fromRow)
      && Number.isInteger(move.fromCol)
      && move.fromRow >= 0
      && move.fromRow < BOARD_SIZE
      && move.fromCol >= 0
      && move.fromCol < BOARD_SIZE;
    const fromSquare = hasFromCoordinates ? toSquareNotation(move.fromRow, move.fromCol) : '';
    const fen = getFenString(nextState);

    if (!pieceType || !color) {
      const basic = toSquare;
      const lanFallback = fromSquare ? fromSquare + '-' + toSquare : basic;
      return { san: basic, lan: lanFallback, fen };
    }

    if (move.isCastle === 'king' || move.isCastle === 'queen') {
      const opponentMoves = generateLegalMoves(nextState);
      const inCheck = isKingInCheck(nextState, nextState.activeColor);
      const suffix = inCheck ? (opponentMoves.length === 0 ? '#' : '+') : '';
      const san = (move.isCastle === 'king' ? 'O-O' : 'O-O-O') + suffix;
      const lanBase = 'K' + (fromSquare ? fromSquare + '-' + toSquare : toSquare);
      const lan = lanBase + suffix;
      return { san, lan, fen };
    }

    const pieceLetterMap = {
      [PIECE_TYPES.PAWN]: '',
      [PIECE_TYPES.KNIGHT]: 'N',
      [PIECE_TYPES.BISHOP]: 'B',
      [PIECE_TYPES.ROOK]: 'R',
      [PIECE_TYPES.QUEEN]: 'Q',
      [PIECE_TYPES.KING]: 'K'
    };

    const longPieceLetterMap = {
      [PIECE_TYPES.PAWN]: 'P',
      [PIECE_TYPES.KNIGHT]: 'N',
      [PIECE_TYPES.BISHOP]: 'B',
      [PIECE_TYPES.ROOK]: 'R',
      [PIECE_TYPES.QUEEN]: 'Q',
      [PIECE_TYPES.KING]: 'K'
    };

    let sanNotation = pieceLetterMap[pieceType] || '';

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
          sanNotation += FILES[move.fromCol];
        } else if (!sameRank) {
          sanNotation += RANKS[move.fromRow];
        } else {
          sanNotation += FILES[move.fromCol] + RANKS[move.fromRow];
        }
      }
    } else if (move.isCapture) {
      sanNotation += FILES[move.fromCol];
    }

    if (move.isCapture) {
      sanNotation += 'x';
    }

    sanNotation += toSquare;

    const longPieceLetter = longPieceLetterMap[pieceType] || '';
    let lanNotation = longPieceLetter + (fromSquare || '');
    if (!lanNotation) {
      lanNotation = fromSquare || longPieceLetter;
    }
    if (fromSquare) {
      lanNotation += move.isCapture ? 'x' : '-';
    } else {
      lanNotation += move.isCapture ? 'x' : '-';
    }
    lanNotation += toSquare;

    if (move.promotion) {
      const promotion = '=' + String(move.promotion).toUpperCase();
      sanNotation += promotion;
      lanNotation += promotion;
    }

    const opponentMoves = generateLegalMoves(nextState);
    const inCheck = isKingInCheck(nextState, nextState.activeColor);
    if (inCheck) {
      const suffix = opponentMoves.length === 0 ? '#' : '+';
      sanNotation += suffix;
      lanNotation += suffix;
    }

    return { san: sanNotation, lan: lanNotation, fen };
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
    const promotionCandidates = candidates.filter(function (move) {
      return Boolean(move && move.promotion);
    });
    if (!promotionCandidates.length) {
      makeMove(state, candidates[0], ui);
    } else if (promotionCandidates.length === 1) {
      makeMove(state, promotionCandidates[0], ui);
    } else {
      showPromotionDialog(state, ui, promotionCandidates);
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
      'Tap or drag a piece to highlight its legal moves.'
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

  function removeDragGhost(context) {
    if (context && context.ghostElement && context.ghostElement.parentNode) {
      context.ghostElement.parentNode.removeChild(context.ghostElement);
    }
    if (context) {
      context.ghostElement = null;
    }
  }

  function updateDragGhostPosition(context, clientX, clientY) {
    if (!context || !context.ghostElement || !context.boardElement) {
      return;
    }
    const rect = context.boardElement.getBoundingClientRect();
    const width = context.squareRect ? context.squareRect.width : context.ghostElement.offsetWidth;
    const height = context.squareRect ? context.squareRect.height : context.ghostElement.offsetHeight;
    if (width > 0) {
      context.ghostElement.style.inlineSize = width + 'px';
    }
    if (height > 0) {
      context.ghostElement.style.blockSize = height + 'px';
    }
    const offsetX = typeof context.offsetX === 'number' ? context.offsetX : width / 2;
    const offsetY = typeof context.offsetY === 'number' ? context.offsetY : height / 2;
    const x = clientX - rect.left - offsetX;
    const y = clientY - rect.top - offsetY;
    const translation = 'translate3d(' + x + 'px, ' + y + 'px, 0)';
    const rotation = context.rotateForTwoPlayer ? ' rotate(180deg)' : '';
    context.ghostElement.style.transform = translation + rotation;
  }

  function ensureDragGhost(state, ui, context) {
    if (!context || context.ghostElement || !ui || !ui.boardElement) {
      return;
    }
    const piece = state.board[context.fromRow] && state.board[context.fromRow][context.fromCol];
    if (!piece) {
      return;
    }
    const ghost = document.createElement('div');
    ghost.className = 'chess-drag-ghost';
    const spriteUrl = getPieceSprite(piece);
    if (spriteUrl && canUseSprite(spriteUrl)) {
      ghost.classList.add('chess-drag-ghost--sprite');
      ghost.style.backgroundImage = 'url(' + spriteUrl + ')';
    } else {
      ghost.textContent = getPieceSymbol(piece);
      const color = getPieceColor(piece);
      ghost.style.color = color === WHITE ? 'var(--chess-square-light-color)' : 'var(--chess-square-dark-color)';
    }
    const squareButton = ui.squares[context.fromRow] && ui.squares[context.fromRow][context.fromCol];
    if (squareButton) {
      const rect = squareButton.getBoundingClientRect();
      context.squareRect = rect;
      if (typeof context.offsetX !== 'number') {
        context.offsetX = context.startX - rect.left;
      }
      if (typeof context.offsetY !== 'number') {
        context.offsetY = context.startY - rect.top;
      }
      ghost.style.inlineSize = rect.width + 'px';
      ghost.style.blockSize = rect.height + 'px';
    }
    if (context.pieceColor) {
      ghost.dataset.color = context.pieceColor;
    }
    if (context.rotateForTwoPlayer) {
      ghost.dataset.rotated = 'true';
    }
    context.boardElement = ui.boardElement;
    context.ghostElement = ghost;
    updateDragGhostPosition(context, context.startX, context.startY);
    ui.boardElement.appendChild(ghost);
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
    removeDragGhost(context);
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
      hoverCol: null,
      offsetX: 0,
      offsetY: 0,
      boardElement: ui.boardElement || null,
      ghostElement: null,
      squareRect: null
    };
    const piece = state.board[row] && state.board[row][col];
    const pieceColor = piece ? getPieceColor(piece) : null;
    context.pieceColor = pieceColor;
    context.rotateForTwoPlayer = Boolean(piece && pieceColor === BLACK && isTwoPlayerMode(state));
    const squareButton = ui.squares[row] && ui.squares[row][col];
    if (squareButton) {
      const rect = squareButton.getBoundingClientRect();
      context.squareRect = rect;
      context.offsetX = event.clientX - rect.left;
      context.offsetY = event.clientY - rect.top;
    }
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
        ensureDragGhost(state, ui, context);
        renderBoard(state, ui);
      }
    }
    if (!context.moved) {
      return;
    }
    ensureDragGhost(state, ui, context);
    updateDragGhostPosition(context, event.clientX, event.clientY);
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

  function normalizeStoredChessState(raw) {
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
        const lan = typeof entry.lan === 'string' ? entry.lan.trim() : '';
        const captured = sanitizePiece(entry.captured);
        if (!Number.isFinite(moveNumber) || moveNumber <= 0 || !color || !san) {
          continue;
        }
        history.push({
          moveNumber: Math.floor(moveNumber),
          color,
          san,
          lan: lan || null,
          fen: fen || null,
          captured: captured || null
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

    const rawNotationPreference = raw.preferences && typeof raw.preferences.notation === 'string'
      ? raw.preferences.notation.toLowerCase()
      : null;
    const preferences = {
      showCoordinates: raw.preferences && raw.preferences.showCoordinates === false ? false : true,
      showHistory: raw.preferences && raw.preferences.showHistory === false ? false : true,
      notation: rawNotationPreference === NOTATION_STYLES.LONG ? NOTATION_STYLES.LONG : NOTATION_STYLES.SHORT
    };

    const difficultyIdRaw = typeof raw.difficultyId === 'string' && raw.difficultyId.trim()
      ? raw.difficultyId.trim()
      : typeof raw.difficulty === 'string' && raw.difficulty.trim()
        ? raw.difficulty.trim()
        : null;
    const difficulty = resolveDifficultyMode(difficultyIdRaw);
    const lastAiAnalysis = normalizeAiAnalysis(raw.lastAiAnalysis);
    const isTwoPlayer = raw.isTwoPlayer === true
      || raw.mode === 'twoPlayer'
      || raw.twoPlayer === true;

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
      lastAiAnalysis,
      isTwoPlayer
    };
  }

  function normalizeStoredArchiveEntry(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const id = typeof raw.id === 'string' && raw.id.trim() ? raw.id.trim() : null;
    if (!id) {
      return null;
    }
    const stateSnapshot = raw.state && typeof raw.state === 'object' ? raw.state : null;
    const normalizedState = stateSnapshot ? normalizeStoredChessState(stateSnapshot) : null;
    if (!normalizedState) {
      return null;
    }
    const createdAtRaw = Number(raw.createdAt);
    const createdAt = Number.isFinite(createdAtRaw) ? createdAtRaw : Date.now();
    const difficultyIdRaw = typeof raw.difficultyId === 'string' && raw.difficultyId.trim()
      ? raw.difficultyId.trim()
      : normalizedState.difficultyId;
    const difficulty = resolveDifficultyMode(difficultyIdRaw);
    let name = typeof raw.name === 'string' && raw.name.trim() ? raw.name.trim() : '';
    const result = typeof raw.result === 'string' && raw.result.trim()
      ? raw.result.trim()
      : determineGameResult(normalizedState.gameOutcome);
    const entry = {
      id,
      name,
      createdAt,
      difficultyId: difficulty ? difficulty.id : normalizedState.difficultyId,
      difficultyLabelKey: typeof raw.difficultyLabelKey === 'string' ? raw.difficultyLabelKey : (difficulty ? difficulty.labelKey : null),
      fallbackLabel: typeof raw.fallbackLabel === 'string' ? raw.fallbackLabel : (difficulty ? difficulty.fallbackLabel : ''),
      result,
      pgn: typeof raw.pgn === 'string' ? raw.pgn : '',
      state: stateSnapshot,
      normalizedState,
      twoPlayer: raw.twoPlayer === true || normalizedState.isTwoPlayer === true
    };
    if (!entry.name) {
      const label = getDifficultyDisplayName(difficulty || resolveDifficultyMode(entry.difficultyId));
      entry.name = label + ' / ' + formatArchiveTimestamp(entry.createdAt);
    }
    if (!entry.pgn) {
      entry.pgn = buildArchivePGN(normalizedState, {
        difficulty,
        createdAt: entry.createdAt,
        twoPlayer: entry.twoPlayer,
        result: entry.result,
        name: entry.name
      });
    }
    return entry;
  }

  function normalizeStoredArchives(raw) {
    if (!raw) {
      return [];
    }
    const entries = [];
    const pushEntry = function (candidate) {
      const normalized = normalizeStoredArchiveEntry(candidate);
      if (normalized) {
        entries.push(normalized);
      }
    };
    if (Array.isArray(raw)) {
      raw.forEach(pushEntry);
    } else if (raw && typeof raw === 'object') {
      if (Array.isArray(raw.entries)) {
        raw.entries.forEach(pushEntry);
      } else if (raw.entries && typeof raw.entries === 'object') {
        Object.keys(raw.entries).forEach(function (key) {
          pushEntry(raw.entries[key]);
        });
      } else {
        Object.keys(raw).forEach(function (key) {
          pushEntry(raw[key]);
        });
      }
    }
    entries.sort(function (a, b) {
      return (b.createdAt || 0) - (a.createdAt || 0);
    });
    if (entries.length > ARCHIVE_LIMIT) {
      return entries.slice(0, ARCHIVE_LIMIT);
    }
    return entries;
  }

  function normalizeStoredChessProgress(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }

    const archives = normalizeStoredArchives(raw.archives);

    if (raw.slots && typeof raw.slots === 'object') {
      const slots = new Map();
      const slotEntries = raw.slots;
      Object.keys(slotEntries).forEach(function (slotKey) {
        if (typeof slotKey !== 'string') {
          return;
        }
        const normalized = normalizeStoredChessState(slotEntries[slotKey]);
        if (!normalized) {
          return;
        }
        const id = normalized.difficultyId
          || (normalized.difficulty && normalized.difficulty.id)
          || slotKey;
        if (!id || slots.has(id)) {
          return;
        }
        slots.set(id, normalized);
      });
      if (!slots.size) {
        return null;
      }
      const activeCandidate = typeof raw.activeSlot === 'string' && raw.activeSlot.trim()
        ? raw.activeSlot.trim()
        : null;
      const activeSlot = activeCandidate && slots.has(activeCandidate)
        ? activeCandidate
        : null;
      return { slots, activeSlot, archives };
    }

    const normalized = normalizeStoredChessState(raw);
    if (!normalized) {
      return null;
    }
    const id = normalized.difficultyId
      || (normalized.difficulty && normalized.difficulty.id)
      || getDefaultDifficultyMode().id;
    const slots = new Map();
    slots.set(id, normalized);
    return { slots, activeSlot: id, archives };
  }

  function readStoredProgress() {
    if (typeof window !== 'undefined' && window.ArcadeAutosave && typeof window.ArcadeAutosave.get === 'function') {
      try {
        const autosaved = window.ArcadeAutosave.get('echecs');
        const normalizedAutosaved = normalizeStoredChessProgress(autosaved);
        if (normalizedAutosaved) {
          return normalizedAutosaved;
        }
      } catch (error) {
        // Ignore autosave errors
      }
    }

    const globalState = getGlobalGameState();
    if (globalState && globalState.arcadeProgress && typeof globalState.arcadeProgress === 'object') {
      const entries = globalState.arcadeProgress.entries && typeof globalState.arcadeProgress.entries === 'object'
        ? globalState.arcadeProgress.entries
        : globalState.arcadeProgress;
      const rawEntry = entries.echecs;
      const payload = rawEntry && typeof rawEntry === 'object' && rawEntry.state ? rawEntry.state : rawEntry;
      const normalized = normalizeStoredChessProgress(payload);
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

  function applyStoredProgress(state, stored, ui) {
    if (!stored || !(stored.slots instanceof Map) || stored.slots.size === 0) {
      return false;
    }

    const slots = new Map();
    stored.slots.forEach(function (snapshot, slotId) {
      if (!snapshot || typeof slotId !== 'string') {
        return;
      }
      slots.set(slotId, snapshot);
    });

    if (!slots.size) {
      return false;
    }

    state.savedSlots = slots;
    state.archivedGames = Array.isArray(stored.archives)
      ? stored.archives.slice(0, ARCHIVE_LIMIT)
      : [];
    state.reviewingArchiveId = null;
    state.isArchivePreview = false;
    state.preventAutosave = false;
    state.previewReturnSnapshot = null;
    state.previewReturnSlotId = null;
    state.lastSavedOutcomeSignature = null;

    const defaultMode = getDefaultDifficultyMode();
    let targetId = stored.activeSlot && slots.has(stored.activeSlot) ? stored.activeSlot : null;
    if (!targetId && slots.has(defaultMode.id)) {
      targetId = defaultMode.id;
    }
    if (!targetId) {
      const iterator = slots.keys();
      const first = iterator.next();
      targetId = first && !first.done ? first.value : defaultMode.id;
    }

    const snapshot = slots.get(targetId);
    if (!snapshot) {
      return false;
    }

    const mode = snapshot.difficulty || resolveDifficultyMode(targetId);
    applyNormalizedState(state, ui, snapshot, { mode, skipSave: true });
    return true;
  }

  function resetGameState(state, ui) {
    if (!state) {
      return;
    }
    const twoPlayerEnabled = isTwoPlayerMode(state);
    const difficulty = state.difficulty || getDefaultDifficultyMode();
    const preferences = {
      showCoordinates: state.preferences && state.preferences.showCoordinates !== false,
      showHistory: state.preferences && state.preferences.showHistory !== false,
      notation: getNotationPreference(state)
    };
    const base = createInitialState(difficulty);
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
    state.trainingHint = null;
    state.trainingHintPending = false;
    state.trainingHintRequestId = 0;
    state.suppressClick = false;
    state.difficulty = difficulty;
    state.difficultyId = difficulty.id;
    state.difficultyOptions = DIFFICULTY_CONFIG.modes;
    state.lastAiAnalysis = null;
    state.isTwoPlayer = twoPlayerEnabled;
    state.activeSlotId = difficulty.id;
    state.isArchivePreview = false;
    state.preventAutosave = false;
    state.reviewingArchiveId = null;
    state.previewReturnSnapshot = null;
    state.previewReturnSlotId = null;
    state.lastSavedOutcomeSignature = null;
    state.archivePreviewBaseState = null;
    state.archivePreviewHistory = null;
    state.archivePreviewInitialState = null;
    state.archivePreviewIndex = 0;
    state.ai = twoPlayerEnabled ? null : createAiContext(difficulty);
    state.aiThinking = false;
    updateLegalMoves(state);
    evaluateGameState(state);
    clearSelection(state, ui);
    hidePromotionDialog(ui);
    renderBoard(state, ui);
    renderHistory(state, ui);
    updateStatus(state, ui);
    clearHelperMessage(state);
    updateHelper(state, ui);
    updateTrainingHelpState(state, ui);
    updateSaveControls(state, ui);
    updateArchiveControls(state, ui);
    applyBoardPreferences(state, ui);
    setTwoPlayerMode(state, ui, twoPlayerEnabled, { skipSave: true, skipSchedule: true });
    saveProgress(state);
    showInteractionMessage(
      state,
      ui,
      'index.sections.echecs.feedback.reset',
      'Partie réinitialisée.',
      null,
      { duration: 2500 }
    );
    if (!state.isGameOver && state.activeColor === BLACK && !isTwoPlayerMode(state)) {
      scheduleAIMove(state, ui);
    }
  }

  function createSerializableState(state) {
    if (!state) {
      return null;
    }
    const preferences = state.preferences || {};
    const board = Array.isArray(state.board)
      ? state.board.map(function (row) {
          return Array.isArray(row)
            ? row.map(function (cell) {
                return sanitizePiece(cell);
              })
            : new Array(BOARD_SIZE).fill('');
        })
      : createInitialBoard();
    const castlingState = state.castling && typeof state.castling === 'object' ? state.castling : {};
    const whiteCastling = castlingState.w && typeof castlingState.w === 'object' ? castlingState.w : {};
    const blackCastling = castlingState.b && typeof castlingState.b === 'object' ? castlingState.b : {};
    const history = Array.isArray(state.history)
      ? state.history.map(function (entry) {
          if (!entry || typeof entry !== 'object') {
            return null;
          }
          return {
            moveNumber: Math.max(1, Number(entry.moveNumber) || 1),
            color: entry.color === BLACK ? BLACK : WHITE,
            san: typeof entry.san === 'string' ? entry.san : '',
            lan: typeof entry.lan === 'string' ? entry.lan : null,
            fen: typeof entry.fen === 'string' ? entry.fen : null,
            captured: sanitizePiece(entry.captured) || null
          };
        }).filter(Boolean)
      : [];

    return {
      board,
      activeColor: state.activeColor === BLACK ? BLACK : WHITE,
      castling: {
        w: { king: Boolean(whiteCastling.king), queen: Boolean(whiteCastling.queen) },
        b: { king: Boolean(blackCastling.king), queen: Boolean(blackCastling.queen) }
      },
      enPassant: state.enPassant
        && Number.isInteger(state.enPassant.row)
        && Number.isInteger(state.enPassant.col)
        ? { row: state.enPassant.row, col: state.enPassant.col }
        : null,
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
      history,
      positionCounts: state.positionCounts instanceof Map
        ? Array.from(state.positionCounts.entries())
        : [],
      gameOutcome: state.gameOutcome ? { ...state.gameOutcome } : null,
      isGameOver: Boolean(state.isGameOver),
      isTwoPlayer: isTwoPlayerMode(state),
      preferences: {
        showCoordinates: preferences.showCoordinates !== false,
        showHistory: preferences.showHistory !== false,
        notation: preferences.notation === NOTATION_STYLES.LONG
          ? NOTATION_STYLES.LONG
          : NOTATION_STYLES.SHORT
      },
      difficultyId: state.difficultyId || (state.difficulty && state.difficulty.id) || getDefaultDifficultyMode().id,
      lastAiAnalysis: serializeAiAnalysis(state.lastAiAnalysis)
    };
  }

  function generateArchiveId(seed) {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      try {
        return crypto.randomUUID();
      } catch (error) {
        // Ignore UUID errors and fallback to manual id
      }
    }
    const base = Number.isFinite(seed) ? seed : Date.now();
    const random = Math.floor(Math.random() * 1e9);
    return 'archive-' + base.toString(36) + '-' + random.toString(36);
  }

  function formatPgnDate(date) {
    const pad = function (value) {
      return String(Math.floor(Math.abs(value))).padStart(2, '0');
    };
    return (
      date.getFullYear()
      + '.' + pad(date.getMonth() + 1)
      + '.' + pad(date.getDate())
    );
  }

  function getTerminationDescription(outcome) {
    if (!outcome || typeof outcome !== 'object') {
      return translate(
        'index.sections.echecs.archives.termination.unknown',
        'Game archived from an intermediate position.'
      );
    }
    if (outcome.type === 'checkmate') {
      if (outcome.winner === WHITE) {
        return translate(
          'index.sections.echecs.archives.termination.checkmateWhite',
          'Checkmate — White wins.'
        );
      }
      if (outcome.winner === BLACK) {
        return translate(
          'index.sections.echecs.archives.termination.checkmateBlack',
          'Checkmate — Black wins.'
        );
      }
      return translate(
        'index.sections.echecs.archives.termination.checkmateGeneric',
        'Checkmate.'
      );
    }
    if (outcome.type === 'stalemate') {
      return translate(
        'index.sections.echecs.archives.termination.stalemate',
        'Stalemate.'
      );
    }
    if (outcome.type === 'draw') {
      const reason = outcome.reason;
      if (reason === 'fiftyMove') {
        return translate(
          'index.sections.echecs.archives.termination.draw.fiftyMove',
          'Draw by fifty-move rule.'
        );
      }
      if (reason === 'repetition') {
        return translate(
          'index.sections.echecs.archives.termination.draw.repetition',
          'Draw by repetition.'
        );
      }
      if (reason === 'insufficient') {
        return translate(
          'index.sections.echecs.archives.termination.draw.insufficient',
          'Draw by insufficient material.'
        );
      }
      if (reason === 'moveLimit') {
        return translate(
          'index.sections.echecs.archives.termination.draw.moveLimit',
          'Draw by move limit.'
        );
      }
      return translate(
        'index.sections.echecs.archives.termination.draw.generic',
        'Draw.'
      );
    }
    return translate(
      'index.sections.echecs.archives.termination.generic',
      'Game concluded.'
    );
  }

  function buildArchivePGN(snapshot, options) {
    if (!snapshot || typeof snapshot !== 'object') {
      return '';
    }
    const createdAt = options && Number.isFinite(options.createdAt)
      ? new Date(options.createdAt)
      : new Date();
    const difficulty = options && options.difficulty
      ? options.difficulty
      : resolveDifficultyMode(snapshot.difficultyId);
    const difficultyLabel = getDifficultyDisplayName(difficulty || getDefaultDifficultyMode());
    const twoPlayer = Boolean(options && options.twoPlayer);
    const result = options && typeof options.result === 'string'
      ? options.result
      : determineGameResult(snapshot.gameOutcome);
    const whiteLabel = translate(
      'index.sections.echecs.archives.pgnWhite',
      'White player'
    );
    const blackLabel = twoPlayer
      ? translate(
          'index.sections.echecs.archives.pgnBlackTwoPlayer',
          'Black player'
        )
      : translate(
          'index.sections.echecs.archives.pgnBlackAi',
          'AI ({difficulty})',
          { difficulty: difficultyLabel }
        );
    const headers = [
      `[Event "${PGN_EVENT_NAME}"]`,
      '[Site "Atom2Univers"]',
      `[Date "${formatPgnDate(createdAt)}"]`,
      '[Round "-"]',
      `[White "${whiteLabel}"]`,
      `[Black "${blackLabel}"]`,
      `[Result "${result || PGN_RESULTS.UNKNOWN}"]`,
      `[Difficulty "${difficultyLabel}"]`,
      `[Termination "${getTerminationDescription(snapshot.gameOutcome)}"]`
    ];

    const moves = Array.isArray(snapshot.history) ? snapshot.history : [];
    const grouped = new Map();
    for (let index = 0; index < moves.length; index += 1) {
      const entry = moves[index];
      if (!entry || typeof entry !== 'object') {
        continue;
      }
      const moveNumber = Number(entry.moveNumber);
      if (!Number.isFinite(moveNumber)) {
        continue;
      }
      const color = entry.color === BLACK ? BLACK : WHITE;
      const san = typeof entry.san === 'string' ? entry.san.trim() : '';
      if (!san) {
        continue;
      }
      let pair = grouped.get(moveNumber);
      if (!pair) {
        pair = {};
        grouped.set(moveNumber, pair);
      }
      if (color === WHITE) {
        pair.white = san;
      } else {
        pair.black = san;
      }
    }

    const tokens = [];
    const numbers = Array.from(grouped.keys()).sort(function (a, b) {
      return a - b;
    });
    for (let i = 0; i < numbers.length; i += 1) {
      const moveNumber = numbers[i];
      const pair = grouped.get(moveNumber);
      if (!pair) {
        continue;
      }
      if (pair.white) {
        tokens.push(moveNumber + '. ' + pair.white);
        if (pair.black) {
          tokens.push(pair.black);
        }
      } else if (pair.black) {
        tokens.push(moveNumber + '... ' + pair.black);
      }
    }

    const lines = [];
    let currentLine = '';
    for (let i = 0; i < tokens.length; i += 1) {
      const token = tokens[i];
      if ((currentLine + token).length > 78) {
        if (currentLine.trim().length) {
          lines.push(currentLine.trim());
        }
        currentLine = '';
      }
      currentLine += token + ' ';
    }
    if (currentLine.trim().length) {
      lines.push(currentLine.trim());
    }

    const movesSection = lines.length ? lines.join('\n') + ' ' : '';
    const finalResult = result || PGN_RESULTS.UNKNOWN;
    return headers.join('\n') + '\n\n' + movesSection + finalResult;
  }

  function createArchiveEntry(state, name) {
    if (!state || !state.gameOutcome) {
      return null;
    }
    const serializable = createSerializableState(state);
    const normalized = normalizeStoredChessState(serializable);
    if (!normalized) {
      return null;
    }
    const now = Date.now();
    const cleanName = typeof name === 'string' && name.trim()
      ? name.trim()
      : generateDefaultArchiveName(state);
    const difficulty = state.difficulty || resolveDifficultyMode(state.difficultyId);
    const result = determineGameResult(normalized.gameOutcome);
    const twoPlayer = Boolean(
      normalized.isTwoPlayer
      || (difficulty && difficulty.twoPlayer)
      || isTwoPlayerMode(state)
    );
    return {
      id: generateArchiveId(now),
      name: cleanName,
      createdAt: now,
      difficultyId: difficulty ? difficulty.id : normalized.difficultyId,
      difficultyLabelKey: difficulty ? difficulty.labelKey : null,
      fallbackLabel: difficulty ? difficulty.fallbackLabel : '',
      result,
      pgn: buildArchivePGN(normalized, {
        difficulty,
        createdAt: now,
        twoPlayer,
        result,
        name: cleanName
      }),
      state: serializable,
      normalizedState: normalized,
      twoPlayer
    };
  }

  function serializeArchivedGames(state) {
    if (!state || !Array.isArray(state.archivedGames)) {
      return [];
    }
    const serialized = [];
    for (let index = 0; index < state.archivedGames.length && index < ARCHIVE_LIMIT; index += 1) {
      const entry = state.archivedGames[index];
      if (!entry || typeof entry !== 'object' || typeof entry.id !== 'string') {
        continue;
      }
      const createdAt = Number(entry.createdAt) || Date.now();
      const baseState = entry.state && typeof entry.state === 'object'
        ? entry.state
        : entry.normalizedState
          ? createSerializableState(entry.normalizedState)
          : null;
      if (!baseState) {
        continue;
      }
      const difficultyId = typeof entry.difficultyId === 'string'
        ? entry.difficultyId
        : null;
      const difficulty = resolveDifficultyMode(difficultyId);
      const result = typeof entry.result === 'string' && entry.result.trim()
        ? entry.result
        : determineGameResult(entry.normalizedState && entry.normalizedState.gameOutcome);
      const pgn = typeof entry.pgn === 'string' && entry.pgn.trim()
        ? entry.pgn
        : buildArchivePGN(entry.normalizedState || normalizeStoredChessState(baseState), {
            difficulty,
            createdAt,
            twoPlayer: entry.twoPlayer === true,
            result,
            name: entry.name
          });
      serialized.push({
        id: entry.id,
        name: typeof entry.name === 'string' ? entry.name : '',
        createdAt,
        difficultyId,
        difficultyLabelKey: typeof entry.difficultyLabelKey === 'string' ? entry.difficultyLabelKey : null,
        fallbackLabel: typeof entry.fallbackLabel === 'string' ? entry.fallbackLabel : '',
        result,
        pgn,
        state: baseState,
        twoPlayer: entry.twoPlayer === true
      });
    }
    return serialized;
  }

  function createInitialSlotSnapshot(mode, referencePreferences) {
    const base = createInitialState(mode);
    if (referencePreferences && typeof referencePreferences === 'object') {
      base.preferences = {
        showCoordinates: referencePreferences.showCoordinates !== false,
        showHistory: referencePreferences.showHistory !== false,
        notation: referencePreferences.notation === NOTATION_STYLES.LONG
          ? NOTATION_STYLES.LONG
          : NOTATION_STYLES.SHORT
      };
    }
    return normalizeStoredChessState(createSerializableState(base));
  }

  function applyNormalizedState(state, ui, normalized, options) {
    if (!state || !normalized) {
      return false;
    }

    const mode = options && options.mode
      ? options.mode
      : normalized.difficulty || resolveDifficultyMode(normalized.difficultyId);
    const difficulty = mode || getDefaultDifficultyMode();
    const board = Array.isArray(normalized.board) ? normalized.board : createInitialBoard();
    state.board = cloneBoard(board);
    state.activeColor = normalized.activeColor === BLACK ? BLACK : WHITE;
    state.castling = cloneCastlingRights(normalized.castling);
    state.enPassant = normalized.enPassant
      && Number.isInteger(normalized.enPassant.row)
      && Number.isInteger(normalized.enPassant.col)
      ? { row: normalized.enPassant.row, col: normalized.enPassant.col }
      : null;
    state.halfmoveClock = Math.max(0, Number(normalized.halfmoveClock) || 0);
    state.fullmove = Math.max(1, Number(normalized.fullmove) || 1);
    state.lastMove = normalized.lastMove
      ? {
          fromRow: normalized.lastMove.fromRow,
          fromCol: normalized.lastMove.fromCol,
          toRow: normalized.lastMove.toRow,
          toCol: normalized.lastMove.toCol,
          piece: sanitizePiece(normalized.lastMove.piece),
          placedPiece: sanitizePiece(normalized.lastMove.placedPiece),
          captured: sanitizePiece(normalized.lastMove.captured),
          promotion: normalized.lastMove.promotion || null,
          isCastle: Boolean(normalized.lastMove.isCastle),
          isEnPassant: Boolean(normalized.lastMove.isEnPassant)
        }
      : null;
    state.history = Array.isArray(normalized.history)
      ? normalized.history.map(function (entry) {
          if (!entry || typeof entry !== 'object') {
            return null;
          }
          return {
            moveNumber: Math.max(1, Number(entry.moveNumber) || 1),
            color: entry.color === BLACK ? BLACK : WHITE,
            san: typeof entry.san === 'string' ? entry.san : '',
            lan: typeof entry.lan === 'string' ? entry.lan : null,
            fen: typeof entry.fen === 'string' ? entry.fen : null,
            captured: sanitizePiece(entry.captured) || null
          };
        }).filter(Boolean)
      : [];
    state.positionCounts = normalized.positionCounts instanceof Map
      ? new Map(normalized.positionCounts)
      : new Map();
    state.gameOutcome = normalized.gameOutcome ? { ...normalized.gameOutcome } : null;
    state.isGameOver = Boolean(normalized.isGameOver);
    state.preferences = normalized.preferences
      ? {
          showCoordinates: normalized.preferences.showCoordinates !== false,
          showHistory: normalized.preferences.showHistory !== false,
          notation: normalized.preferences.notation === NOTATION_STYLES.LONG
            ? NOTATION_STYLES.LONG
            : NOTATION_STYLES.SHORT
        }
      : { showCoordinates: true, showHistory: true, notation: NOTATION_STYLES.SHORT };
    state.pendingPromotion = null;
    state.selection = null;
    state.dragContext = null;
    state.helperMessage = null;
    state.helperTimeoutId = null;
    state.suppressClick = false;
    state.difficulty = difficulty;
    state.difficultyId = difficulty.id;
    state.difficultyOptions = DIFFICULTY_CONFIG.modes;
    state.activeSlotId = difficulty.id;
    state.lastAiAnalysis = normalized.lastAiAnalysis && !difficulty.twoPlayer
      ? normalizeAiAnalysis(normalized.lastAiAnalysis)
      : null;
    state.aiThinking = false;

    const twoPlayer = Boolean(normalized.isTwoPlayer || (difficulty && difficulty.twoPlayer));
    setTwoPlayerMode(state, ui, twoPlayer, { skipSave: true, skipSchedule: true });

    const key = getPositionKey(state);
    state.positionKey = key;
    if (!state.positionCounts.has(key)) {
      state.positionCounts.set(key, 1);
    }

    updateLegalMoves(state);
    evaluateGameState(state);
    state.lastSavedOutcomeSignature = null;

    if (ui) {
      hidePromotionDialog(ui);
      renderBoard(state, ui);
      renderHistory(state, ui);
      updateStatus(state, ui);
      updateDifficultyUI(state, ui);
      updateAnalysisState(state, ui);
      updateHelper(state, ui);
      updateSaveControls(state, ui);
      updateArchiveControls(state, ui);
      applyBoardPreferences(state, ui);
    }

    return true;
  }

  function saveProgress(state, options) {
    if (!state) {
      return;
    }

    const force = options && options.force === true;
    const skipSlotUpdate = state.preventAutosave && !force;

    if (!(state.savedSlots instanceof Map)) {
      state.savedSlots = new Map();
    }

    let slotId = state.activeSlotId
      || (state.difficulty && state.difficulty.id)
      || getDefaultDifficultyMode().id;

    let snapshot = null;
    let normalized = null;

    if (!skipSlotUpdate) {
      snapshot = createSerializableState(state);
      normalized = normalizeStoredChessState(snapshot);
      if (!normalized) {
        return;
      }
      slotId = normalized.difficultyId
        || (normalized.difficulty && normalized.difficulty.id)
        || getDefaultDifficultyMode().id;
      state.savedSlots.set(slotId, normalized);
      state.activeSlotId = slotId;
    } else if (state.previewReturnSlotId) {
      slotId = state.previewReturnSlotId;
    }

    const container = {
      version: STORAGE_VERSION,
      activeSlot: slotId,
      slots: {},
      archives: serializeArchivedGames(state)
    };

    state.savedSlots.forEach(function (value, key) {
      if (!value || typeof key !== 'string') {
        return;
      }
      container.slots[key] = createSerializableState(value);
    });

    if (!skipSlotUpdate && snapshot && !container.slots[slotId]) {
      container.slots[slotId] = snapshot;
    } else if (skipSlotUpdate && !container.slots[slotId]) {
      const existing = state.savedSlots.get(slotId);
      if (existing) {
        container.slots[slotId] = createSerializableState(existing);
      }
    }

    if (typeof window !== 'undefined' && window.ArcadeAutosave && typeof window.ArcadeAutosave.set === 'function') {
      try {
        window.ArcadeAutosave.set('echecs', container);
      } catch (error) {
        // Ignore autosave errors
      }
    } else {
      const globalState = getGlobalGameState();
      if (globalState && typeof globalState === 'object') {
        if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
          globalState.arcadeProgress = { version: 1, entries: {} };
        }
        if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
          globalState.arcadeProgress.entries = {};
        }
        globalState.arcadeProgress.entries.echecs = {
          state: container,
          updatedAt: Date.now()
        };
      }
    }

    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(container));
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
    clearTrainingHint(state);
    state.trainingHintPending = false;
    state.trainingHintRequestId = Number.isInteger(state.trainingHintRequestId)
      ? state.trainingHintRequestId + 1
      : 0;
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
      lan: notation.lan,
      fen: notation.fen,
      captured: nextState.lastMove ? sanitizePiece(nextState.lastMove.captured) || null : null
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
    if (!state.gameOutcome) {
      state.lastSavedOutcomeSignature = null;
    }
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
    updateTrainingHelpState(state, ui);
    applyBoardPreferences(state, ui);
    if (metadata && metadata.analysis && metadata.analysis.type === 'ai') {
      state.lastAiAnalysis = createAiMoveAnalysis(state, move, metadata.analysis, notation);
    }
    updateAnalysisState(state, ui);
    updateSaveControls(state, ui);
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
    updateSaveControls(state, ui);
    updateArchiveControls(state, ui);
    applyBoardPreferences(state, ui);
  }

  function markSectionReady(section) {
    section.dataset.arcadeReady = 'true';
  }

  function createInitialState(initialMode) {
    const board = createInitialBoard();
    const castling = {
      w: { king: true, queen: true },
      b: { king: true, queen: true }
    };
    const difficulty = initialMode || getDefaultDifficultyMode();
    const twoPlayer = Boolean(difficulty && difficulty.twoPlayer);
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
      preferences: { showCoordinates: true, showHistory: true, notation: NOTATION_STYLES.SHORT },
      dragContext: null,
      helperMessage: null,
      helperTimeoutId: null,
      trainingHint: null,
      trainingHintPending: false,
      trainingHintRequestId: 0,
      suppressClick: false,
      isTwoPlayer: twoPlayer,
      ai: twoPlayer ? null : createAiContext(difficulty),
      aiThinking: false,
      difficulty,
      difficultyId: difficulty.id,
      difficultyOptions: DIFFICULTY_CONFIG.modes,
      lastAiAnalysis: null,
      savedSlots: new Map(),
      activeSlotId: difficulty.id,
      archivedGames: [],
      reviewingArchiveId: null,
      previewReturnSnapshot: null,
      previewReturnSlotId: null,
      isArchivePreview: false,
      preventAutosave: false,
      lastSavedOutcomeSignature: null,
      archivePreviewBaseState: null,
      archivePreviewHistory: null,
      archivePreviewInitialState: null,
      archivePreviewIndex: 0
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
    const helpButton = section.querySelector(HELP_BUTTON_SELECTOR);
    const promotionElement = section.querySelector(PROMOTION_SELECTOR);
    const promotionOptionsElement = section.querySelector(PROMOTION_OPTIONS_SELECTOR);
    const coordinatesToggle = section.querySelector(COORDINATES_TOGGLE_SELECTOR);
    const historyToggle = section.querySelector(HISTORY_TOGGLE_SELECTOR);
    const notationToggle = section.querySelector(NOTATION_TOGGLE_SELECTOR);
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
    const saveButton = section.querySelector(SAVE_BUTTON_SELECTOR);
    const saveDialog = section.querySelector(SAVE_DIALOG_SELECTOR);
    const saveInput = section.querySelector(SAVE_INPUT_SELECTOR);
    const saveConfirm = section.querySelector(SAVE_CONFIRM_SELECTOR);
    const saveCancel = section.querySelector(SAVE_CANCEL_SELECTOR);
    const archiveSelect = section.querySelector(ARCHIVE_SELECT_SELECTOR);
    const archiveEmpty = section.querySelector(ARCHIVE_EMPTY_SELECTOR);
    const archiveRestore = section.querySelector(ARCHIVE_RESTORE_SELECTOR);
    const archiveNavigation = section.querySelector(ARCHIVE_NAVIGATION_SELECTOR);
    const archivePrevious = section.querySelector(ARCHIVE_PREVIOUS_SELECTOR);
    const archiveNext = section.querySelector(ARCHIVE_NEXT_SELECTOR);

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
      helpButton,
      promotionElement,
      promotionOptionsElement,
      coordinatesToggle,
      historyToggle,
      notationToggle,
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
      difficultyDescription,
      saveButton,
      saveDialog,
      saveInput,
      saveConfirm,
      saveCancel,
      archiveSelect,
      archiveEmpty,
      archiveRestore,
      archiveNavigation,
      archivePrevious,
      archiveNext
    };

    if (ui.boardElement) {
      ui.boardElement.classList.toggle('chess-board--two-player', isTwoPlayerMode(state));
    }

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
    if (notationToggle) {
      notationToggle.addEventListener('click', function () {
        state.preferences = state.preferences || {};
        const current = getNotationPreference(state);
        const next = current === NOTATION_STYLES.LONG ? NOTATION_STYLES.SHORT : NOTATION_STYLES.LONG;
        state.preferences.notation = next;
        updateNotationToggle(state, ui);
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
    if (helpButton) {
      helpButton.addEventListener('click', function () {
        requestTrainingHint(state, ui);
      });
    }
    if (saveButton) {
      saveButton.addEventListener('click', function () {
        if (!saveButton.disabled) {
          openSaveDialog(state, ui);
        }
      });
    }
    if (saveCancel) {
      saveCancel.addEventListener('click', function () {
        closeSaveDialog(ui);
      });
    }
    if (saveDialog) {
      saveDialog.addEventListener('click', function (event) {
        if (event.target === saveDialog) {
          closeSaveDialog(ui);
        }
      });
      saveDialog.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
          event.preventDefault();
          closeSaveDialog(ui);
        }
      });
    }
    if (saveConfirm) {
      saveConfirm.addEventListener('click', function () {
        const value = ui.saveInput ? ui.saveInput.value : '';
        if (saveCompletedGame(state, ui, value)) {
          closeSaveDialog(ui);
        }
      });
    }
    if (saveInput) {
      saveInput.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') {
          event.preventDefault();
          const value = saveInput.value;
          if (saveCompletedGame(state, ui, value)) {
            closeSaveDialog(ui);
          }
        }
      });
    }
    if (archiveSelect) {
      archiveSelect.addEventListener('change', function () {
        const archiveId = archiveSelect.value;
        if (!archiveId) {
          if (state.isArchivePreview) {
            restoreArchivePreview(state, ui);
          }
          return;
        }
        const entry = findArchiveEntry(state, archiveId);
        if (entry) {
          enterArchivePreview(state, ui, entry);
        } else {
          archiveSelect.value = '';
        }
      });
    }
    if (archiveRestore) {
      archiveRestore.addEventListener('click', function () {
        if (restoreArchivePreview(state, ui) && archiveSelect) {
          archiveSelect.value = '';
        }
      });
    }
    if (archivePrevious) {
      archivePrevious.addEventListener('click', function () {
        if (!archivePrevious.disabled) {
          stepArchivePreview(state, ui, -1);
        }
      });
    }
    if (archiveNext) {
      archiveNext.addEventListener('click', function () {
        if (!archiveNext.disabled) {
          stepArchivePreview(state, ui, 1);
        }
      });
    }

    const storedProgress = readStoredProgress();
    const hasStored = applyStoredProgress(state, storedProgress, ui);

    applyBoardPreferences(state, ui);
    updateTrainingHelpState(state, ui);
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
