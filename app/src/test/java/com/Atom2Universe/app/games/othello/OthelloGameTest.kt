package com.Atom2Universe.app.games.othello

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou d'Othello : les coups legaux, les retournements, le passage de tour et la fin
 * de partie. Les parties jouees par l'IA servent de test d'invariants : a chaque coup, le
 * nombre de pions augmente de un, et une partie finit toujours.
 */
class OthelloGameTest {

    @Test fun positionInitialeQuatreCoupsPourBlanc() {
        val g = OthelloGame()
        assertEquals(OthelloGame.WHITE, g.currentPlayer)
        assertEquals(setOf(2 to 4, 3 to 5, 4 to 2, 5 to 3), g.validMoves.keys)
        assertEquals(2 to 2, g.getScore())
    }

    @Test fun unCoupRetourneLesPionsEncadresEtPasseLaMain() {
        val g = OthelloGame()
        assertTrue(g.placeDisc(2, 4))          // blanc joue en (2,4) : retourne le noir en (3,4)
        assertEquals(OthelloGame.WHITE, g.board[2][4])
        assertEquals(OthelloGame.WHITE, g.board[3][4])
        assertEquals(1 to 4, g.getScore())
        assertEquals(OthelloGame.BLACK, g.currentPlayer)
    }

    @Test fun uneCaseIllegaleOuOccupeeEstRefusee() {
        val g = OthelloGame()
        assertFalse(g.placeDisc(0, 0))
        assertFalse(g.placeDisc(3, 3))         // case occupee
        assertEquals(OthelloGame.WHITE, g.currentPlayer)
        assertEquals(2 to 2, g.getScore())
    }

    private fun position(player: Int, vararg discs: Triple<Int, Int, Int>): OthelloGame {
        val flat = MutableList(OthelloGame.BOARD_SIZE * OthelloGame.BOARD_SIZE) { OthelloGame.EMPTY }
        for ((r, c, v) in discs) flat[r * OthelloGame.BOARD_SIZE + c] = v
        val g = OthelloGame()
        assertTrue(g.deserialize(mapOf("board" to flat, "player" to player, "over" to false)))
        return g
    }

    @Test fun uneLigneSansPionAEncadrerNeDonneAucunCoup() {
        val B = OthelloGame.BLACK
        val W = OthelloGame.WHITE
        // Deux noirs contre le bord et aucun blanc : rien a encadrer.
        val g = position(W, Triple(0, 0, B), Triple(0, 1, B))
        assertTrue(g.validMoves.isEmpty())
        // Noir noir blanc : blanc joue en (0,0) et retourne les deux.
        val h = position(W, Triple(0, 1, B), Triple(0, 2, B), Triple(0, 3, W))
        assertEquals(mapOf((0 to 0) to listOf(0 to 1, 0 to 2)), h.validMoves)
    }

    @Test fun plusAucunCoupPourPersonneTermineLaPartie() {
        val B = OthelloGame.BLACK
        val W = OthelloGame.WHITE
        // Blanc joue en (0,2), retourne l'unique noir : il ne reste que des blancs.
        val g = position(W, Triple(0, 0, W), Triple(0, 1, B))
        assertTrue(g.placeDisc(0, 2))
        assertTrue(g.gameOver)
        assertTrue(g.validMoves.isEmpty())
        assertEquals(0 to 3, g.getScore())
        assertEquals(W, g.getWinner())
    }

    @Test fun uneParticleCompleteGardeSesInvariantsEtSeTermine() {
        val g = OthelloGame()
        var moves = 0
        while (!g.gameOver) {
            val move = g.chooseAIMove()
            assertNotNull("pas de coup alors que la partie n'est pas finie", move)
            val (black, white) = g.getScore()
            assertTrue(g.placeDisc(move!!.first, move.second))
            moves++
            val (b2, w2) = g.getScore()
            assertEquals("un coup pose exactement un pion", black + white + 1, b2 + w2)
            assertTrue("un coup retourne au moins un pion", (b2 - black).let { it != 0 } || (w2 - white).let { it != 0 })
            assertTrue("partie trop longue", moves <= 60)
        }
        val (b, w) = g.getScore()
        assertEquals("une partie finie a 4 + (coups) pions", 4 + moves, b + w)
        assertTrue(g.validMoves.isEmpty())
    }

    @Test fun sauvegardeEtRelecture() {
        val g = OthelloGame()
        repeat(6) { g.chooseAIMove()?.let { g.placeDisc(it.first, it.second) } }
        val copy = OthelloGame()
        assertTrue(copy.deserialize(g.serialize()))
        assertEquals(g.currentPlayer, copy.currentPlayer)
        assertEquals(g.getScore(), copy.getScore())
        assertEquals(g.validMoves.keys, copy.validMoves.keys)
        assertFalse(copy.deserialize(mapOf("board" to listOf(1, 2), "player" to 1)))
    }
}
