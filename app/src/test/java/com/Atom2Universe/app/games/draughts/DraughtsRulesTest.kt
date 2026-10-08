package com.Atom2Universe.app.games.draughts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde-fou des règles internationales que le moteur faisait de travers. */
class DraughtsRulesTest {

    private fun empty(): DraughtsGame = DraughtsGame().apply {
        for (r in 0..9) for (c in 0..9) board[r][c] = null
    }

    private val whiteMan = DraughtsPiece(DraughtsPieceType.PAWN, DraughtsPieceColor.WHITE)
    private val whiteKing = DraughtsPiece(DraughtsPieceType.KING, DraughtsPieceColor.WHITE)
    private val blackMan = DraughtsPiece(DraughtsPieceType.PAWN, DraughtsPieceColor.BLACK)
    private val blackKing = DraughtsPiece(DraughtsPieceType.KING, DraughtsPieceColor.BLACK)

    /** Un pion qui touche la dernière rangée en pleine rafle continue, et n'est pas promu. */
    @Test fun unPionQuiTraverseLaDerniereRangeeContinueSansEtrePromu() {
        val game = empty()
        game.board[2][1] = whiteMan
        game.board[1][2] = blackMan
        game.board[1][4] = blackMan
        game.board[7][8] = blackMan
        val moves = game.getLegalMoves()
        assertEquals(1, moves.size)
        val move = moves.single()
        assertEquals(DraughtsPos(2, 5), move.to)
        assertEquals(2, move.captureCount)
        game.makeMove(move)
        assertEquals(whiteMan, game.board[2][5])
    }

    /** Les pièces prises restent sur le plateau jusqu'à la fin de la rafle (coup turc). */
    @Test fun uneDameNeRepassePasSurUnePiecePrise() {
        val game = empty()
        game.board[5][2] = whiteKing
        game.board[4][3] = blackMan
        game.board[6][1] = blackMan
        val moves = game.getLegalMoves()
        assertTrue(moves.isNotEmpty())
        assertTrue("aucune rafle ne repasse sur la pièce prise", moves.all { it.captureCount == 1 })
    }

    private fun play(game: DraughtsGame, from: DraughtsPos, to: DraughtsPos) {
        game.makeMove(game.getLegalMoves().first { it.from == from && it.to == to })
    }

    @Test fun lesDamesQuiTournentEnRondFontNulle() {
        val game = empty()
        game.board[9][0] = whiteKing
        game.board[0][1] = blackKing
        game.board[3][8] = blackMan
        val a = DraughtsPos(9, 0); val b = DraughtsPos(8, 1)
        val c = DraughtsPos(0, 1); val d = DraughtsPos(1, 0)
        repeat(2) {
            play(game, a, b); play(game, c, d)
            assertFalse(game.isGameOver)
            play(game, b, a); play(game, d, c)
        }
        assertFalse(game.isGameOver)
        play(game, a, b)
        assertTrue("troisième fois la même position", game.isGameOver)
        assertNull(game.winner)
        assertEquals(9, game.quietKingMoves)
        // Un coup de pion remet le compteur des coups de dames à zéro.
        val other = empty()
        other.board[9][0] = whiteKing
        other.board[6][3] = whiteMan
        other.board[0][1] = blackKing
        play(other, a, b)
        assertEquals(1, other.quietKingMoves)
        play(other, c, d)
        play(other, DraughtsPos(6, 3), DraughtsPos(5, 4))
        assertEquals(0, other.quietKingMoves)
    }
}
