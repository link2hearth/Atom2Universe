package com.Atom2Universe.app.games.chess.ai

import com.Atom2Universe.app.games.chess.*

/**
 * Évaluateur de position d'échecs
 * Retourne un score en centipawns (100 = valeur d'un pion)
 * Score positif = avantage blanc, score négatif = avantage noir
 */
object BoardEvaluator {

    // Valeurs des pièces en centipawns
    private val PIECE_VALUES = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 20000
    )

    // Tables pièce-case pour bonus positionnel (perspective des blancs)
    // Les noirs utilisent les mêmes tables inversées (row 7 - row)

    private val PAWN_TABLE = arrayOf(
        intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf( 50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf( 10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(  5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf(  0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf(  5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf(  5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0)
    )

    private val KNIGHT_TABLE = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
        intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
        intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    private val BISHOP_TABLE = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
        intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
        intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
        intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )

    private val ROOK_TABLE = arrayOf(
        intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(  5, 10, 10, 10, 10, 10, 10,  5),
        intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( -5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(  0,  0,  0,  5,  5,  0,  0,  0)
    )

    private val QUEEN_TABLE = arrayOf(
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5,  5,  5,  5,  0,-10),
        intArrayOf( -5,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(  0,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(-10,  5,  5,  5,  5,  5,  0,-10),
        intArrayOf(-10,  0,  5,  0,  0,  0,  0,-10),
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20)
    )

    private val KING_MIDDLEGAME_TABLE = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf( 20, 20,  0,  0,  0,  0, 20, 20),
        intArrayOf( 20, 30, 10,  0,  0, 10, 30, 20)
    )

    /**
     * Évalue une position
     * @param game État du jeu à évaluer
     * @return Score en centipawns (positif = avantage blanc)
     */
    fun evaluate(game: ChessGame): Int {
        var score = 0
        var whiteKnightCount = 0
        var whiteBishopCount = 0
        var blackKnightCount = 0
        var blackBishopCount = 0

        // Évaluation matérielle + positionnelle
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = game.getPieceAt(row, col)
                if (piece != null) {
                    val pieceValue = PIECE_VALUES[piece.type] ?: 0
                    val positionalBonus = getPositionalBonus(piece, row, col)
                    val totalValue = pieceValue + positionalBonus

                    if (piece.color == PieceColor.WHITE) {
                        score += totalValue
                        if (piece.type == PieceType.KNIGHT) whiteKnightCount++
                        if (piece.type == PieceType.BISHOP) whiteBishopCount++
                    } else {
                        score -= totalValue
                        if (piece.type == PieceType.KNIGHT) blackKnightCount++
                        if (piece.type == PieceType.BISHOP) blackBishopCount++
                    }
                }
            }
        }

        // Bonus de mobilité : pénalité pour roi en échec (plus simple que compter tous les coups)
        if (game.isInCheck) {
            score += if (game.currentTurn == PieceColor.WHITE) -50 else 50
        }

        // Bonus pour la paire de fous
        if (whiteBishopCount >= 2) score += 30
        if (blackBishopCount >= 2) score -= 30

        // Bonus pour les pièces développées (cavaliers et fous pas sur leur rangée de départ)
        // C'est déjà géré par les piece-square tables, pas besoin de doubler

        return score
    }

    /**
     * Retourne le bonus positionnel pour une pièce
     */
    private fun getPositionalBonus(piece: Piece, row: Int, col: Int): Int {
        val table = when (piece.type) {
            PieceType.PAWN -> PAWN_TABLE
            PieceType.KNIGHT -> KNIGHT_TABLE
            PieceType.BISHOP -> BISHOP_TABLE
            PieceType.ROOK -> ROOK_TABLE
            PieceType.QUEEN -> QUEEN_TABLE
            PieceType.KING -> KING_MIDDLEGAME_TABLE
        }

        // Pour les noirs, inverser la table (miroir vertical)
        val tableRow = if (piece.color == PieceColor.WHITE) row else 7 - row
        return table[tableRow][col]
    }
}
