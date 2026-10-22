package com.Atom2Universe.app.games.colorstack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LabSortGameTest {

    /** Rejoue la trace du mélange à l'envers avec les vraies règles : le niveau doit se terminer résolu. */
    @Test
    fun everyGeneratedLevelIsSolvableByItsOwnTrail() {
        for (diff in LabSortGame.Difficulty.entries) {
            val cfg = LabSortGame.CONFIGS.getValue(diff)
            var totalMs = 0L
            repeat(60) { seed ->
                val game = LabSortGame(Random(seed * 31 + diff.ordinal))
                val t0 = System.nanoTime()
                game.newGame(diff, mysteryMode = seed % 2 == 0)
                totalMs += (System.nanoTime() - t0) / 1_000_000

                assertEquals(cfg.colors + cfg.empties, game.tubeCount)
                assertEquals(cfg.empties, game.tubes.count { it.isEmpty() })
                assertFalse("niveau déjà résolu au départ", game.solved)
                assertTrue("un tube déjà trié au départ", game.tubes.none { game.isComplete(it) })

                for (m in game.generationTrail.reversed()) {
                    assertTrue("coup interdit ${m.toList()}", game.canPour(m[1], m[0]))
                    assertEquals(m[2], game.pour(m[1], m[0]))
                }
                assertTrue("diff=$diff seed=$seed non résolu", game.solved)
            }
            println("LabSort $diff : ${totalMs / 60} ms par niveau en moyenne")
        }
    }

    @Test
    fun mysteryHidesEverythingButTheTops() {
        val game = LabSortGame(Random(7))
        game.newGame(LabSortGame.Difficulty.EASY, mysteryMode = true)
        for (t in game.tubes) {
            t.forEachIndexed { i, c -> assertEquals(i == t.lastIndex, c.revealed) }
        }
        // Verser découvre le nouveau dessus du tube de départ.
        val from = game.tubes.indices.first { i ->
            game.tubes.indices.any { game.canPour(i, it) && game.tubes[i].size > game.topGroupSize(i) }
        }
        val to = game.tubes.indices.first { game.canPour(from, it) }
        game.pour(from, to)
        assertTrue(game.tubes[from].last().revealed)
    }

    @Test
    fun pipetteAndUndoFollowTheirLimits() {
        val game = LabSortGame(Random(3))
        game.newGame(LabSortGame.Difficulty.EASY, mysteryMode = false)
        val full = game.tubes.indices.first { game.tubes[it].isNotEmpty() }
        val empty = game.tubes.indices.first { game.tubes[it].isEmpty() }

        assertTrue(game.pipette(full, empty))
        assertEquals(1, game.tubes[empty].size)
        assertEquals(LabSortGame.PIPETTE_START - 1, game.pipetteCharges)
        assertFalse("plus de pipette", game.canPipette(full, empty))

        assertTrue(game.undo())
        assertEquals(LabSortGame.PIPETTE_START, game.pipetteCharges)
        assertEquals(LabSortGame.UNDO_LIMIT - 1, game.undoLeft)

        game.restart()
        assertEquals(LabSortGame.UNDO_LIMIT, game.undoLeft)
    }

    @Test
    fun saveAndLoadKeepsTheGame() {
        val game = LabSortGame(Random(11))
        game.newGame(LabSortGame.Difficulty.MEDIUM, mysteryMode = true)
        val copy = LabSortGame()
        assertTrue(copy.deserialize(game.serialize()))
        assertEquals(game.serialize(), copy.serialize())
    }
}
