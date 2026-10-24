package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class MahjongIceQualityTest {
    private fun trap() = MahjongGame(MahjongMode.SLIDE, 1, initializeBoard = false).apply {
        tiles += MahjongTile(0, 0, 3, 0, 0)
        tiles += MahjongTile(1, 5, 5, 0, 0)
        tiles += MahjongTile(2, 1, 1, 0, 1)
        tiles += MahjongTile(3, 3, 3, 0, 1)
    }

    private fun play(game: MahjongGame, plan: List<MahjongSolutionStep>) {
        for (step in plan) {
            step.slide?.let { assertEquals(MahjongGame.Result.MOVED, game.slide(it.id, it.x, it.y)) }
            for (id in step.ids) assertTrue(game.tap(id) in listOf(MahjongGame.Result.SELECTED, MahjongGame.Result.MATCHED))
        }
    }

    @Test fun certifiesAnOrderTrapAndAnUnavoidableExternalStopper() {
        val game = trap()
        val before = game.snapshot()
        val quality = MahjongIceSolver.analyze(game)!!
        assertEquals(MahjongIceSolver.Outcome.SOLVABLE, quality.outcome)
        assertEquals(1, quality.stopperDependentPairs)
        assertEquals(1, quality.winningChoices)
        assertEquals(1, quality.losingChoices)
        assertEquals(MahjongIceSolver.Outcome.UNSOLVABLE, quality.opening.single { it.ids == listOf(0, 1) }.outcome)
        assertEquals(MahjongIceSolver.Outcome.SOLVABLE, quality.opening.single { it.ids == listOf(2, 3) }.outcome)
        assertEquals(before, game.snapshot())
        assertTrue(game.history.isEmpty())
        play(game, quality.solution!!)
        assertTrue(game.complete)
    }

    @Test fun obviousIndependentPairsHaveNoPuzzleCertificate() {
        val game = MahjongGame(MahjongMode.SLIDE, 1, initializeBoard = false).apply {
            tiles += MahjongTile(0, 0, 0, 0, 0)
            tiles += MahjongTile(1, 1, 0, 0, 0)
            tiles += MahjongTile(2, 0, 2, 0, 1)
            tiles += MahjongTile(3, 1, 2, 0, 1)
        }
        val quality = MahjongIceSolver.analyze(game)!!
        assertEquals(MahjongIceSolver.Outcome.SOLVABLE, quality.outcome)
        assertEquals(2, quality.immediatePairs)
        assertEquals(2, quality.winningChoices)
        assertEquals(0, quality.losingChoices)
        assertEquals(0, quality.stopperDependentPairs)
    }

    @Test fun isolatedInteriorDiagonalPairIsAProvenDeadEnd() {
        val game = trap()
        game.tiles[0].removed = true; game.tiles[1].removed = true
        val quality = MahjongIceSolver.analyze(game)!!
        assertEquals(MahjongIceSolver.Outcome.UNSOLVABLE, quality.outcome)
        assertEquals(1, quality.stopperDependentPairs)
        assertTrue(quality.opening.isEmpty())
        assertNull(quality.solution)
    }

    @Test fun exhaustedWorkBudgetIsNeverCountedAsAProvenTrap() {
        val game = trap()
        val quality = MahjongIceSolver.analyze(game, maxStates = 0)!!
        assertEquals(MahjongIceSolver.Outcome.UNKNOWN, quality.outcome)
        assertEquals(0, quality.losingChoices)
        assertTrue(quality.opening.all { it.outcome == MahjongIceSolver.Outcome.UNKNOWN })
        assertNull(quality.solution)
    }

    @Test fun pairPlanFindsShortestOrientationAndPreservesTheOtherTiles() {
        val game = trap()
        val before = game.snapshot()
        val plan = MahjongIceSolver.pairPlan(game, 0, 1)!!
        assertEquals(1, plan.count { it.slide != null })
        assertEquals(before, game.snapshot())
        play(game, plan)
        assertEquals(setOf(2, 3), game.board.map { it.id }.toSet())
        assertEquals(before.tiles[2], game.tiles[2])
        assertEquals(before.tiles[3], game.tiles[3])
        assertNull(MahjongIceSolver.pairPlan(game, 2, 3))
    }

    @Test fun analysisRejectsActiveTrialsAndDuplicateFamiliesRatherThanCertifyingThem() {
        val game = trap()
        game.tap(0)
        assertNull(MahjongIceSolver.analyze(game))
        assertNull(MahjongIceSolver.pairPlan(game, 0, 1))
        val duplicate = trap()
        duplicate.tiles.forEach { it.face = 0 }
        assertNull(MahjongIceSolver.analyze(duplicate))
    }

    @Test fun analysisCanBeCancelledWithoutTouchingTheBoard() {
        val game = trap()
        val before = game.snapshot()
        Thread.currentThread().interrupt()
        try {
            MahjongIceSolver.analyze(game)
            fail("An interrupted quality analysis must cancel")
        } catch (_: CancellationException) {
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(before, game.snapshot())
        } finally {
            Thread.interrupted()
        }
    }
}
