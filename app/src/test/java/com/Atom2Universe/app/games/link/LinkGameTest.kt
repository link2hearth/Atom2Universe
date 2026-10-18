package com.Atom2Universe.app.games.link

import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LinkGameTest {

    private fun generated(floor: Int, seed: Int) = LinkGame().apply { this.floor = floor; generate(Random(seed)) }

    /** Une grille faite à la main, en format de sauvegarde : aucun étage fini. */
    private fun handmade(size: Int, energies: String, pairs: String, hand: String) =
        LinkGame().apply {
            val scores = List(LinkGame.FLOORS) { -1 }.joinToString(",")
            assertTrue(deserialize("2;1;$size;$energies;$pairs;$hand;;0;0;0;$scores;${"0".repeat(LinkGame.FLOORS)}"))
        }

    /** Les poses du générateur, rejouées dans n'importe quel ordre, gagnent toujours. */
    @Test
    fun generatorSolutionWins() {
        for (floor in 1..LinkGame.FLOORS) repeat(15) { i ->
            val g = generated(floor, floor * 100 + i)
            val p = LinkGame.paramsFor(floor)
            assertEquals("étage $floor : paires", p.pairs, g.pairCount)
            assertEquals(g.hand.size, g.solution.size)
            for (cells in g.solution.shuffled(Random(i))) {
                assertEquals("étage $floor, pose $cells", LinkGame.Result.PLACED, g.place(cells))
            }
            assertTrue("étage $floor non résolu", g.isVictory)
        }
    }

    /** Le boss a chaque forme dans sa main et ses sept paires. */
    @Test
    fun bossHasEveryShape() {
        repeat(20) { i ->
            val g = generated(LinkGame.FLOORS, 900 + i)
            assertTrue(g.isBoss)
            assertEquals(LinkGame.SHAPES.indices.toSet(), g.hand.toSet())
            assertEquals(7, g.pairCount)
        }
    }

    /**
     * Mesure : ce que demande chaque étage, et ce que vaut une partie. Une partie parfaite
     * sert de plafond pour caler les neutrinos.
     */
    @Test
    fun runMeasurements() {
        var perfectRun = 0
        for (floor in 1..LinkGame.FLOORS) {
            var pieces = 0; var zeros = 0
            val runs = 20
            repeat(runs) { i ->
                val g = generated(floor, 7 * floor + i)
                pieces += g.hand.size
                zeros += g.initial.count { it == 0 }
            }
            val p = LinkGame.paramsFor(floor)
            val perfect = LinkGame.floorScore(pieces / runs, floor, 0, 0)
            perfectRun += perfect
            println("étage $floor : ${p.size}×${p.size}, ${pieces / runs} pièces, ${p.pairs} paires, " +
                "${"%.1f".format(zeros / runs.toFloat())} au repos, $perfect points parfait")
        }
        val withUndos = (1..LinkGame.FLOORS).sumOf { f ->
            LinkGame.floorScore(LinkGame.paramsFor(f).pieces, f, undos = 2, restarts = 0)
        }
        println("partie parfaite : $perfectRun points → ${NeutrinoRewards.link(perfectRun)} neutrinos ; " +
            "deux annulations par étage : $withUndos → ${NeutrinoRewards.link(withUndos)}")
    }

    @Test
    fun scoreRewardsCleanPlay() {
        val base = 10 * 10 * 5
        assertEquals(base + base / 4, LinkGame.floorScore(10, 5, 0, 0))
        assertEquals((base * 0.8f).toInt(), LinkGame.floorScore(10, 5, 2, 0))
        assertEquals((base * 0.2f).toInt(), LinkGame.floorScore(10, 5, 20, 3))
    }

    @Test
    fun shapeMatchesAnyOrientation() {
        val g = handmade(4, "1".repeat(16), "", "5")
        // Le L couché : (0,0) (1,0) (2,0) (0,1).
        assertEquals(0, g.matchingPiece(listOf(0, 1, 2, 4)))
        // Une ligne de 4 n'est pas un L.
        assertEquals(-1, g.matchingPiece(listOf(0, 1, 2, 3)))
    }

    /** Toucher un atome intriqué fait descendre son jumeau ; au repos, la pose est refusée. */
    @Test
    fun entangledPartnerDropsToo() {
        // Grille 4 × 4 : cases 0 et 15 intriquées. Main : deux dominos.
        val energies = CharArray(16) { '0' }.also { it[0] = '1'; it[1] = '1'; it[15] = '1' }
        val g = handmade(4, String(energies), "0,15", "0,0")
        assertEquals(LinkGame.Result.PLACED, g.place(listOf(0, 1)))
        assertEquals(0, g.energy[15])
        assertTrue(g.energy.all { it == 0 })
        assertFalse("il reste un domino en main", g.isVictory)
        assertEquals(LinkGame.Result.EXHAUSTED, g.place(listOf(4, 5)))
        g.undo()
        assertEquals(1, g.energy[15])
        assertEquals(1, g.undos)
    }

    /** Une partie complète : les étages s'enchaînent, le boss clôt la partie. */
    @Test
    fun fullRun() {
        val g = LinkGame()
        g.newRun(Random(5))
        for (floor in 1..LinkGame.FLOORS) {
            assertEquals(floor, g.floor)
            for (cells in g.solution) g.place(cells)
            assertTrue(g.scoreFloor() > 0)
            assertEquals(-1, g.scoreFloor())
            assertEquals(floor == LinkGame.FLOORS, g.isRunOver)
            g.nextFloor(Random(floor))
        }
        assertEquals(LinkGame.FLOORS, g.perfectFloors.count { it })
        assertEquals(g.floorScores.sum(), g.totalScore)
    }

    @Test
    fun saveRoundTrip() {
        val g = LinkGame()
        g.newRun(Random(3))
        for (cells in g.solution) g.place(cells)
        g.scoreFloor()
        g.nextFloor(Random(4))
        for (cells in g.solution.take(2)) g.place(cells)
        g.undo()
        val back = LinkGame()
        assertTrue(back.deserialize(g.serialize()))
        assertEquals(2, back.floor)
        assertTrue(g.initial.contentEquals(back.initial))
        assertTrue(g.energy.contentEquals(back.energy))
        assertTrue(g.hand.contentEquals(back.hand))
        assertEquals(1, back.placements.size)
        assertEquals(1, back.undos)
        assertTrue(g.floorScores.contentEquals(back.floorScores))
        assertTrue(back.perfectFloors[0])
    }
}
