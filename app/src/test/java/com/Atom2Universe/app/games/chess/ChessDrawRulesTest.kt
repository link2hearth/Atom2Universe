package com.Atom2Universe.app.games.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou des nulles d'office : sans elles, deux rois seuls ou deux cavaliers qui font des
 * allers-retours tournaient sans fin.
 */
class ChessDrawRulesTest {

    private fun play(game: ChessGame, from: String, to: String) {
        val move = game.getLegalMoves(Square.fromAlgebraic(from)!!)
            .first { it.to == Square.fromAlgebraic(to) }
        assertTrue("$from-$to", game.makeMove(move))
    }

    @Test fun lesAllersRetoursDeCavaliersFontNulleALaTroisiemeFois() {
        val game = ChessGame()
        repeat(2) {
            play(game, "g1", "f3"); play(game, "g8", "f6")
            play(game, "f3", "g1"); play(game, "f6", "g8")
        }
        assertEquals(ChessGame.DrawReason.REPETITION, game.drawReason)
        assertTrue(game.isGameOver())
    }

    @Test fun deuxRoisSeulsFontNulle() {
        val game = ChessGame()
        game.fromFEN("8/8/8/8/8/3k4/1q6/K7 w - - 0 1")
        assertNull(game.drawReason)
        assertFalse(game.isGameOver())
        // Le roi blanc prend la dame : il ne reste que les deux rois.
        play(game, "a1", "b2")
        assertEquals(ChessGame.DrawReason.MATERIAL, game.drawReason)
    }

    @Test fun cinquanteCoupsSansPriseNiPionFontNulle() {
        val game = ChessGame()
        game.fromFEN("4k3/8/8/8/8/8/R7/4K3 w - - 99 80")
        play(game, "a2", "a3")
        assertEquals(ChessGame.DrawReason.FIFTY_MOVES, game.drawReason)
    }
}
