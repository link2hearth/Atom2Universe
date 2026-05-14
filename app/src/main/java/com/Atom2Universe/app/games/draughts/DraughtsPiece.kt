package com.Atom2Universe.app.games.draughts

enum class DraughtsPieceType { PAWN, KING }

enum class DraughtsPieceColor {
    WHITE, BLACK;
    fun opposite() = if (this == WHITE) BLACK else WHITE
}

data class DraughtsPiece(val type: DraughtsPieceType, val color: DraughtsPieceColor) {
    fun isKing() = type == DraughtsPieceType.KING
    fun promoted() = copy(type = DraughtsPieceType.KING)
}

data class DraughtsPos(val row: Int, val col: Int) {
    fun isValid() = row in 0..9 && col in 0..9 && (row + col) % 2 == 1
    fun offset(dr: Int, dc: Int) = DraughtsPos(row + dr, col + dc)
    fun isInBounds() = row in 0..9 && col in 0..9
}
