package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class MahjongIceSolverTest {
    private fun fixture(): MahjongGame = MahjongGame(MahjongMode.SLIDE, 1, initializeBoard = false).apply {
        // Pair 0 looks playable, but removing it first loses the stopper at (0,3).
        // Pair 1 must use that stopper before pair 0 is removed.
        tiles += MahjongTile(0, 0, 3, 0, 0)
        tiles += MahjongTile(1, 5, 5, 0, 0)
        tiles += MahjongTile(2, 1, 1, 0, 1)
        tiles += MahjongTile(3, 3, 3, 0, 1)
    }

    private fun play(game: MahjongGame, plan: List<MahjongSolutionStep>): List<List<Int>> {
        val matches = mutableListOf<List<Int>>()
        for (step in plan) {
            step.slide?.let { assertEquals(MahjongGame.Result.MOVED, game.slide(it.id, it.x, it.y)) }
            for (id in step.ids) {
                val result = game.tap(id)
                assertTrue(result == MahjongGame.Result.SELECTED || result == MahjongGame.Result.MATCHED)
                if (result == MahjongGame.Result.MATCHED) matches += game.lastMatched
            }
        }
        assertTrue(game.complete)
        return matches
    }

    @Test fun solverBacktracksInsteadOfRemovingANeededStopper() {
        val game = fixture()
        val before = game.snapshot()
        val plan = MahjongIceSolver.solve(game)
        assertNotNull(plan)
        assertEquals(before, game.snapshot())
        assertTrue(game.history.isEmpty())
        assertEquals(plan, MahjongIceSolver.solve(game))
        val matches = play(game, plan!!)
        assertEquals(setOf(2, 3), matches.first().toSet())
    }

    @Test fun solverRejectsTheDeadEndAfterPrematureStopperRemoval() {
        val game = fixture()
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0, 0, 1))
        assertEquals(MahjongGame.Result.MATCHED, game.tap(1))
        assertFalse(game.complete)
        // Two interior diagonal tiles cannot be aligned by moving only one on ice.
        assertNull(MahjongIceSolver.solve(game))
    }

    @Test fun solverCanAbandonAnUnfinishedLosingAttemptWithoutMovingOtherTiles() {
        val game = fixture()
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0, 0, 1))
        val before = game.snapshot()
        val plan = MahjongIceSolver.solve(game)
        assertNotNull(plan)
        assertEquals(before, game.snapshot())
        val matches = play(game, plan!!)
        assertEquals(setOf(2, 3), matches.first().toSet())
    }

    @Test fun solverHandlesAnAlreadyCompletedBoard() {
        val game = fixture()
        game.tiles.forEach { it.removed = true }
        assertEquals(emptyList<MahjongSolutionStep>(), MahjongIceSolver.solve(game))
    }

    @Test fun solverHandlesAllSixteenPairsIncludingTheHighestTileBit() {
        val game = MahjongGame(MahjongMode.SLIDE, 1, stage = 4, initializeBoard = false)
        for (pair in 0 until 16) {
            val x = if (pair % 2 == 0) 0 else 4
            game.tiles += MahjongTile(pair * 2, x, pair / 2, 0, pair)
            game.tiles += MahjongTile(pair * 2 + 1, x + 1, pair / 2, 0, pair)
        }
        val plan = MahjongIceSolver.solve(game)
        assertNotNull(plan)
        assertEquals(16, play(game, plan!!).size)
    }

    @Test fun solverStillSupportsMoreThanTwoTilesWithTheSameFace() {
        val game = fixture()
        game.tiles.forEach { it.face = 0 }
        val plan = MahjongIceSolver.solve(game)
        assertNotNull(plan)
        assertEquals(2, play(game, plan!!).size)
    }

    @Test fun interruptedSearchStopsWithoutChangingTheGameOrClearingInterruption() {
        val game = fixture()
        val before = game.snapshot()
        Thread.currentThread().interrupt()
        try {
            MahjongIceSolver.solve(game)
            fail("An interrupted solver must cancel instead of reporting an unsolvable board")
        } catch (_: CancellationException) {
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(before, game.snapshot())
            assertTrue(game.history.isEmpty())
        } finally {
            Thread.interrupted()
        }
        assertNotNull(MahjongIceSolver.solve(game))
    }

    @Test fun hintsFollowACompleteSolutionIncludingRollbackOfTheActiveAttempt() {
        val game = fixture()
        game.slideDirection(0, 0, 1)
        var firstPair: Set<Int>? = null
        var guard = 0
        while (!game.complete && guard++ < 40) {
            val hint = game.hint()
            assertTrue("A winning next action must be available", hint.isNotEmpty())
            val slide = game.slideHint()
            if (slide != null) assertEquals(MahjongGame.Result.MOVED, game.slide(slide.id, slide.x, slide.y))
            else for (id in hint) {
                val result = game.tap(id)
                if (result == MahjongGame.Result.MATCHED && firstPair == null) firstPair = game.lastMatched.toSet()
            }
        }
        assertTrue("Hints must not cycle between tentative positions", game.complete)
        assertEquals(setOf(2, 3), firstPair)
    }
}
