package com.Atom2Universe.app.games.game2048

import com.Atom2Universe.app.games.game2048.Game2048Logic.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou du 2048 : le glissement/fusion est la regle entiere du jeu. Chaque coup valide
 * ajoute une tuile au hasard (2 ou 4) ; les tests l'ignorent donc en ne comparant que les
 * cases qui ne sont pas « la case nouvellement remplie ».
 */
class Game2048LogicTest {

    private fun game(vararg cells: Int, size: Int = 4, target: Int = 256): Game2048Logic {
        val g = Game2048Logic(size)
        assertTrue(g.deserialize(mapOf("size" to size, "target" to target, "board" to cells.toList())))
        return g
    }

    /** Le plateau vaut `expected`, a une case pres : celle qui etait vide et qui a recu un 2 ou un 4. */
    private fun assertBoardPlusSpawn(expected: IntArray, actual: IntArray) {
        val diff = expected.indices.filter { expected[it] != actual[it] }
        assertEquals("plateau ${actual.toList()} au lieu de ${expected.toList()} (+ une apparition)", 1, diff.size)
        assertEquals(0, expected[diff[0]])
        assertTrue(actual[diff[0]] == 2 || actual[diff[0]] == 4)
    }

    @Test fun uneFusionParPaireEtLeScore() {
        val g = game(2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(g.move(Direction.LEFT))
        assertEquals("[2,2,2,2] donne [4,4], pas [8]", 8, g.score)
        assertBoardPlusSpawn(intArrayOf(4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), g.board)
    }

    @Test fun troisTuilesEgalesFusionnentDabordCellesDuBordDeGlissement() {
        val left = game(2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        left.move(Direction.LEFT)
        assertEquals(listOf(4, 2), left.board.take(2))
        val right = game(2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        right.move(Direction.RIGHT)
        assertEquals(listOf(2, 4), right.board.slice(2..3))
    }

    @Test fun uneTuileNeFusionneQuUneFoisParCoup() {
        val g = game(4, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        g.move(Direction.LEFT)
        assertEquals("[4,2,2] donne [4,4], pas [8]", listOf(4, 4), g.board.take(2))
        assertEquals(4, g.score)
    }

    @Test fun lesQuatreDirectionsGlissentVersLeBonBord() {
        fun one(direction: Direction): IntArray {
            val g = game(0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0)   // un 2 en (ligne 2, colonne 1)
            g.move(direction)
            return g.board
        }
        assertEquals(2, one(Direction.LEFT)[2 * 4 + 0])
        assertEquals(2, one(Direction.RIGHT)[2 * 4 + 3])
        assertEquals(2, one(Direction.UP)[0 * 4 + 1])
        assertEquals(2, one(Direction.DOWN)[3 * 4 + 1])
    }

    @Test fun unCoupSansEffetNeFaitRienEtNeCompteRien() {
        val g = game(2, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val before = g.board.copyOf()
        assertFalse(g.move(Direction.LEFT))
        assertEquals(before.toList(), g.board.toList())
        assertEquals(0, g.moves)
        assertEquals(0, g.score)
    }

    @Test fun victoireALaTuileCible() {
        val g = game(128, 128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, target = 256)
        assertFalse(g.hasWon)
        g.move(Direction.LEFT)
        assertTrue(g.hasWon)
        assertEquals(256, g.bestTile)
    }

    @Test fun plateauPleinEtBloqueEstSansCoupEtUnePaireLeDebloque() {
        // Damier sans aucune paire egale adjacente, plateau plein : aucun coup ne change rien.
        val stuck = game(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        for (d in Direction.values()) assertFalse("$d sur un plateau bloque", stuck.move(d))
        // Plein, mais avec une paire possible : le coup est valide.
        val open = game(2, 2, 4, 8, 4, 8, 2, 4, 2, 4, 8, 2, 8, 2, 4, 8)
        assertTrue(open.move(Direction.LEFT))
    }

    @Test fun sauvegardeEtRelectureDonnentLeMemeEtat() {
        val g = game(2, 4, 8, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)
        g.move(Direction.UP)
        val copy = Game2048Logic()
        assertTrue(copy.deserialize(g.serialize()))
        assertEquals(g.board.toList(), copy.board.toList())
        assertEquals(g.score, copy.score)
        assertEquals(g.moves, copy.moves)
        assertFalse("une sauvegarde invalide est refusee", copy.deserialize(mapOf("size" to 4, "board" to listOf(1, 2, 3))))
    }
}
