package com.Atom2Universe.app.games.colorstack

import org.json.JSONArray
import org.json.JSONObject
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
                // La trace trie les couleurs ; en mystère, dévoiler aussi les niveaux restés au fond.
                if (game.mystery) {
                    for (i in game.tubes.indices) {
                        if (game.tubes[i].any { !it.revealed }) {
                            val empty = game.tubes.indices.first { game.tubes[it].isEmpty() }
                            assertEquals(LabSortGame.CAPACITY, game.pour(i, empty))
                        }
                    }
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
    fun sortedMysteryBoardIsOnlyWonAfterEveryLayerIsRevealed() {
        val game = LabSortGame(Random(11))
        game.newGame(LabSortGame.Difficulty.EASY, mysteryMode = true)
        val cfg = LabSortGame.CONFIGS.getValue(game.difficulty)
        val sorted = JSONArray()
        repeat(cfg.colors) { color ->
            sorted.put(JSONArray().apply {
                repeat(LabSortGame.CAPACITY) { layer ->
                    put(color * 2 + if (layer == LabSortGame.CAPACITY - 1) 1 else 0)
                }
            })
        }
        repeat(cfg.empties) { sorted.put(JSONArray()) }
        val saved = JSONObject(game.serialize()).apply {
            put("tubes", sorted)
            put("initial", sorted)
        }

        assertTrue(game.deserialize(saved.toString()))
        assertFalse("couleurs triées mais encore cachées", game.solved)
        assertEquals(0, game.completedCount())

        // Chaque tube se dévoile en le versant dans l'un des tubes vides.
        repeat(cfg.colors) { source ->
            val empty = game.tubes.indices.first { game.tubes[it].isEmpty() }
            assertEquals(LabSortGame.CAPACITY, game.pour(source, empty))
            assertTrue(game.tubes[empty].all { it.revealed })
            assertEquals(source + 1, game.completedCount())
            assertEquals(source == cfg.colors - 1, game.solved)
        }
        val restored = LabSortGame()
        assertTrue(restored.deserialize(game.serialize()))
        assertTrue(restored.solved)
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
