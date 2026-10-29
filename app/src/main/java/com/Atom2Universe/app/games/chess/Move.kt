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
    val isEnPassant: Boolean = false
) {
    /**
     * Notation longue (ex: "e2e4", "e7e8q")
     */
    fun toLongNotation(): String {
        val promotion = when (promotionType) {
            PieceType.QUEEN -> "q"
            PieceType.ROOK -> "r"
            PieceType.BISHOP -> "b"
            PieceType.KNIGHT -> "n"
            else -> ""
        }
        return "${from.toAlgebraic()}${to.toAlgebraic()}$promotion"
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
