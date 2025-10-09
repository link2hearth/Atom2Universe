(function () {
  'use strict';

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

  function updateSquareAccessibility(button, piece) {
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

  function renderBoard(state, ui) {
    if (ui.boardElement) {
      const hideCoordinates = state.preferences && state.preferences.showCoordinates === false;
      ui.boardElement.classList.toggle('chess-board--hide-coordinates', hideCoordinates);
    }
    for (let row = 0; row < BOARD_SIZE; row += 1) {
      for (let col = 0; col < BOARD_SIZE; col += 1) {
        const button = ui.squares[row][col];
        const piece = state.board[row][col];
        button.textContent = getPieceSymbol(piece);
        button.classList.toggle('has-piece', Boolean(piece));
        updateSquareAccessibility(button, piece);
        if (!state.selection || state.selection.row !== row || state.selection.col !== col) {
          button.classList.remove('is-selected');
        }
        const isDragTarget = state.dragContext
          && state.dragContext.hoverRow === row
          && state.dragContext.hoverCol === col;
        button.classList.toggle('is-drag-target', Boolean(isDragTarget));
        if (state.selection) {
          const key = state.selection.row + ',' + state.selection.col;
          const moves = state.legalMovesByFrom.get(key) || [];
          let isTarget = false;
          for (let i = 0; i < moves.length; i += 1) {
            const move = moves[i];
            if (move.toRow === row && move.toCol === col) {
              isTarget = true;
              button.classList.toggle('is-legal-capture', move.isCapture);
              button.classList.toggle('is-legal-move', !move.isCapture);
              break;
            }
          }
          if (!isTarget) {
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

  function setStatusText(element, key, fallback, params) {
    if (!element) {
      return;
    }
    const text = translate(key, fallback, params);
    element.textContent = text;
  }

  function updateStatus(state, ui) {
    if (!ui.statusElement) {
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
      preferences
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

    const key = getPositionKey(state);
    state.positionKey = key;
    if (!state.positionCounts.has(key)) {
      state.positionCounts.set(key, 1);
    }

    updateLegalMoves(state);
    evaluateGameState(state);
    return true;
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
      }
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

  function makeMove(state, move, ui) {
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
    clearSelection(state, ui);
    renderBoard(state, ui);
    renderHistory(state, ui);
    updateStatus(state, ui);
    clearHelperMessage(state);
    updateHelper(state, ui);
    applyBoardPreferences(state, ui);
    saveProgress(state);
  }

  function updateBoardTranslations(section, ui, state) {
    const boardElement = section.querySelector(BOARD_SELECTOR);
    if (boardElement) {
      const ariaLabel = translate('index.sections.echecs.board', 'Standard chessboard');
      boardElement.setAttribute('aria-label', ariaLabel);
    }
    renderBoard(state, ui);
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
      suppressClick: false
    };
    const key = getPositionKey(state);
    state.positionKey = key;
    state.positionCounts.set(key, 1);
    updateLegalMoves(state);
    return state;
  }

  onReady(function () {
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
      historyTitle
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

    const storedProgress = readStoredProgress();
    const hasStored = applyStoredProgress(state, storedProgress);

    applyBoardPreferences(state, ui);
    updateBoardTranslations(section, ui, state);
    if (!hasStored) {
      saveProgress(state);
    }
    markSectionReady(section);

    window.addEventListener('i18n:languagechange', function () {
      updateBoardTranslations(section, ui, state);
    });
  });
})();
