package com.Atom2Universe.app.games.chess

/**
 * Représente un coup aux échecs
 */
data class Move(
    val from: Square,
    val to: Square,
    val piece: Piece,
    val capturedPiece: Piece? = null,
    val promotionType: PieceType? = null,
    val isCastling: Boolean = false,
    val isEnPassant: Boolean = false,
    val isCheck: Boolean = false,
    val isCheckmate: Boolean = false
) {
    /**
     * Notation algébrique du coup (ex: "e2e4", "Nf3", "O-O")
     */
    fun toAlgebraic(): String {
        // Roque
        if (isCastling) {
            return if (to.col > from.col) "O-O" else "O-O-O"
        }

        val pieceSymbol = when (piece.type) {
            PieceType.PAWN -> ""
            PieceType.KNIGHT -> "N"
            PieceType.BISHOP -> "B"
            PieceType.ROOK -> "R"
            PieceType.QUEEN -> "Q"
            PieceType.KING -> "K"
        }

        val capture = if (capturedPiece != null) "x" else ""
        val promotion = if (promotionType != null) {
            "=" + when (promotionType) {
                PieceType.QUEEN -> "Q"
                PieceType.ROOK -> "R"
                PieceType.BISHOP -> "B"
                PieceType.KNIGHT -> "N"
                else -> ""
            }
        } else ""

        val checkSymbol = when {
            isCheckmate -> "#"
            isCheck -> "+"
            else -> ""
        }

        return "$pieceSymbol${from.toAlgebraic()}$capture${to.toAlgebraic()}$promotion$checkSymbol"
    }

    /**
     * Notation longue (ex: "e2e4")
     */
    fun toLongNotation(): String {
        val promotion = if (promotionType != null) {
            when (promotionType) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> ""
            }
        } else ""
        return "${from.toAlgebraic()}${to.toAlgebraic()}$promotion"
    }

    /**
     * Notation courte pour affichage (ex: "e4", "Nf3")
     */
    fun toShortNotation(): String {
        if (isCastling) {
            return if (to.col > from.col) "O-O" else "O-O-O"
        }

        val pieceSymbol = when (piece.type) {
            PieceType.PAWN -> if (capturedPiece != null) from.toAlgebraic()[0].toString() else ""
            PieceType.KNIGHT -> "N"
            PieceType.BISHOP -> "B"
            PieceType.ROOK -> "R"
            PieceType.QUEEN -> "Q"
            PieceType.KING -> "K"
        }

        val capture = if (capturedPiece != null) "x" else ""
        val destination = to.toAlgebraic()

        val promotion = if (promotionType != null) {
            "=" + when (promotionType) {
                PieceType.QUEEN -> "Q"
                PieceType.ROOK -> "R"
                PieceType.BISHOP -> "B"
                PieceType.KNIGHT -> "N"
                else -> ""
            }
        } else ""

        val checkSymbol = when {
            isCheckmate -> "#"
            isCheck -> "+"
            else -> ""
        }

        return "$pieceSymbol$capture$destination$promotion$checkSymbol"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Move) return false

        // Deux coups sont égaux s'ils ont la même origine, destination et type de promotion
        return from == other.from &&
               to == other.to &&
               promotionType == other.promotionType
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + (promotionType?.hashCode() ?: 0)
        return result
    }
}
