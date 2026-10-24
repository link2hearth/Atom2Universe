package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Bounded gameplay certificates, not a timing or device-performance benchmark. */
class MahjongIceGeneratorTest {
    private fun fromCells(cells: IntArray, stage: Int, count: Int = cells.size) =
        MahjongGame(MahjongMode.SLIDE, 1, stage, initializeBoard = false).apply {
            repeat(count) { index ->
                tiles += MahjongTile(index, cells[index] % columns, cells[index] / columns, 0, index / 2)
            }
        }

    private fun play(game: MahjongGame, plan: List<MahjongSolutionStep>) {
        for (step in plan) {
            step.slide?.let { assertEquals(MahjongGame.Result.MOVED, game.slide(it.id, it.x, it.y)) }
            for (id in step.ids) {
                val result = game.tap(id)
                assertTrue("Illegal action $result for tile $id", result == MahjongGame.Result.SELECTED || result == MahjongGame.Result.MATCHED)
            }
        }
    }

    private fun certify(game: MahjongGame): MahjongIceSolver.Quality {
        val quality = MahjongIceSolver.analyze(game)!!
        val name = "stage=${game.stage} seed=${game.seed}"
        assertEquals(name, MahjongIceSolver.Outcome.SOLVABLE, quality.outcome)
        assertEquals("An obvious opening pair bypasses the ice puzzle: $name", 0, quality.immediatePairs)
        assertTrue("Most pairs must actually need another family's stop: $name",
            quality.stopperDependentPairs >= game.remaining / 2 - 1)
        assertTrue("The first fusion must require choosing an order: $name", quality.winningChoices in 1..3)
        assertTrue("A legal wrong first fusion must really lose: $name", quality.losingChoices >= if (game.stage < 4) 1 else 2)
        assertTrue(name, quality.opening.none { it.outcome == MahjongIceSolver.Outcome.UNKNOWN })
        assertTrue("Every winning first fusion must require several slides: $name",
            quality.opening.filter { it.outcome == MahjongIceSolver.Outcome.SOLVABLE }.all { it.minSlides >= 2 })
        return quality
    }

    @Test fun everyNormalAndHardLayoutHasCertifiedOrderAndStopperDependencies() {
        for ((stage, layouts) in listOf(1 to MahjongIceLayouts.normal, 4 to MahjongIceLayouts.hard)) {
            assertEquals(8, layouts.size)
            for (cells in layouts) {
                val game = fromCells(cells, stage)
                assertEquals(if (stage < 4) 16 else 20, game.remaining)
                assertEquals(cells.size, cells.toSet().size)
                assertTrue(cells.all { it in 0 until game.columns * game.rows })
                val quality = certify(game)
                play(game, quality.solution!!)
                assertTrue(game.complete)
            }
        }
    }

    @Test fun everyEvenCatalogPrefixIncludingLegacyBoardsHasALegalFinish() {
        val catalogs = listOf(1 to MahjongIceLayouts.normal, 4 to MahjongIceLayouts.hard,
            1 to MahjongIceLayouts.legacyNormal, 4 to MahjongIceLayouts.legacyHard)
        for ((stage, layouts) in catalogs) for (cells in layouts) {
            assertEquals(cells.size, cells.toSet().size)
            for (count in 2..cells.size step 2) {
                val game = fromCells(cells, stage, count)
                val plan = MahjongIceSolver.solve(game)
                assertNotNull("stage=$stage prefix=$count layout=${cells.toList()}", plan)
                play(game, plan!!)
                assertTrue(game.complete)
            }
        }
    }

    @Test fun actualGeneratedPuzzlesKeepTheCertificatesAndLosingChoicesReallyLose() {
        for (stage in listOf(1, 4)) {
            val shapes = mutableSetOf<String>()
            for (seed in 0 until 8) {
                val game = MahjongGame(MahjongMode.SLIDE, seed, stage)
                val before = game.snapshot()
                assertEquals(before, MahjongGame(MahjongMode.SLIDE, seed, stage).snapshot())
                val quality = certify(game)
                shapes += geometry(game)
                val wrong = quality.opening.first { it.outcome == MahjongIceSolver.Outcome.UNSOLVABLE }
                val wrongPlan = MahjongIceSolver.pairPlan(game, wrong.ids[0], wrong.ids[1])
                assertNotNull(wrongPlan)
                play(game, wrongPlan!!)
                assertEquals(before.tiles.size - 2, game.remaining)
                assertNull("The certified losing opening must not have a rescue without undo/shuffle", MahjongIceSolver.solve(game))
                game.restore(before)
                play(game, quality.solution!!)
                assertTrue(game.complete)
            }
            assertTrue("Different seeds must change the puzzle beyond faces, IDs or reflections", shapes.size >= 3)
        }
    }

    @Test fun legacyRedealsPreserveSparseIdsPointsAndUndoForEveryRemainingSize() {
        for ((stage, cells) in listOf(1 to MahjongIceLayouts.legacyNormal.first(), 4 to MahjongIceLayouts.legacyHard.first())) {
            for (remaining in 2..cells.size step 2) {
                val game = fromCells(cells, stage)
                val liveIds = cells.indices.shuffled(Random(remaining + stage)).take(remaining).toSet()
                game.tiles.forEach { it.removed = it.id !in liveIds }
                game.restore(game.snapshot().copy(score = 620, combo = 3, peakCombo = 5, moves = 9, assists = 1))
                val before = game.snapshot()
                game.shuffle()
                assertEquals(cells.size, game.tiles.size)
                assertEquals(liveIds, game.board.map { it.id }.toSet())
                assertEquals(before.tiles.filter { it.removed }, game.tiles.filter { it.removed })
                assertEquals(620, game.score)
                assertEquals(5, game.peakCombo)
                assertEquals(2, game.assists)
                val after = game.snapshot()
                val plan = game.solutionSteps.toList()
                assertTrue(game.undo())
                assertEquals(before.tiles, game.tiles)
                assertEquals(before.score, game.score)
                game.restore(after)
                play(game, plan)
                assertTrue("Legacy stage=$stage remaining=$remaining", game.complete)
            }
        }
    }

    /** Remove face/ID labels and all board symmetries before comparing layouts. */
    private fun geometry(game: MahjongGame): String {
        val variants = mutableListOf<String>()
        val transposes = if (game.columns == game.rows) listOf(false, true) else listOf(false)
        for (transpose in transposes) for (mirrorX in listOf(false, true)) for (mirrorY in listOf(false, true)) {
            variants += game.board.groupBy { it.face }.values.map { pair ->
                pair.map { tile ->
                    var x = if (transpose) tile.y else tile.x
                    var y = if (transpose) tile.x else tile.y
                    if (mirrorX) x = game.columns - 1 - x
                    if (mirrorY) y = game.rows - 1 - y
                    y * game.columns + x
                }.sorted().joinToString(",")
            }.sorted().joinToString(";")
        }
        return variants.minOrNull()!!
    }
}
