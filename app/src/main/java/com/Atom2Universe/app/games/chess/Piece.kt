package com.Atom2Universe.app.games.chess

/**
 * Type de pièce d'échecs avec notation et valeur matérielle
 */
enum class PieceType(val notation: String, val value: Int) {
    PAWN("p", 100),
    KNIGHT("n", 320),
    BISHOP("b", 330),
    ROOK("r", 500),
    QUEEN("q", 900),
    KING("k", 20000)
}

/**
 * Couleur des pièces
 */
enum class PieceColor {
    WHITE, BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

/**
 * Représente une pièce d'échecs
 */
data class Piece(
    val type: PieceType,
    val color: PieceColor
) {
    /**
     * Notation de la pièce (majuscule pour blanc, minuscule pour noir)
     */
    val notation: String = if (color == PieceColor.WHITE) {
        type.notation.uppercase()
    } else {
        type.notation.lowercase()
    }

    /**
     * Chemin du sprite pour cette pièce
     */
    fun getSpritePath(): String {
        val pieceName = when (type) {
            PieceType.PAWN -> "pawn"
            PieceType.KNIGHT -> "knight"
            PieceType.BISHOP -> "bishop"
            PieceType.ROOK -> "rook"
            PieceType.QUEEN -> "queen"
            PieceType.KING -> "king"
        }
        // Pièces noires ont "1" à la fin (pawn1.png, knight1.png, etc.)
        val suffix = if (color == PieceColor.BLACK) "1" else ""
        return "Assets/sprites/$pieceName$suffix.png"
    }
}

/**
 * Représente une case sur l'échiquier (0,0 = a8, 7,7 = h1)
 */
data class Square(val row: Int, val col: Int) {

    /**
     * Convertit en notation algébrique (ex: "e4")
     */
    fun toAlgebraic(): String {
        if (!isValid()) return "??"
        val file = ('a' + col).toString()
        val rank = (8 - row).toString()
        return "$file$rank"
    }

    /**
     * Vérifie si la case est valide (dans les limites du plateau)
     */
    fun isValid(): Boolean = row in 0..7 && col in 0..7

    companion object {
        /**
         * Crée une case depuis la notation algébrique (ex: "e4")
         */
        fun fromAlgebraic(notation: String): Square? {
            if (notation.length != 2) return null
            val col = notation[0] - 'a'
            val row = 8 - (notation[1] - '0')
            val square = Square(row, col)
            return if (square.isValid()) square else null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Square) return false
        return row == other.row && col == other.col
    }

    override fun hashCode(): Int {
        return row * 8 + col
    }
}
