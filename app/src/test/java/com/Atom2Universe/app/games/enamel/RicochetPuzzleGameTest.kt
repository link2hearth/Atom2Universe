package com.Atom2Universe.app.games.enamel

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class RicochetPuzzleGameTest {
    private fun settle(g: RicochetPuzzleGame) {
        repeat(1300) { if (!g.moving) return; g.step(1f / 120) }
        fail("Un tour doit toujours finir, même avec le mur mobile")
    }

    @Test fun seedsGiveSixSeparatedPairsAndReplayExactly() {
        for (seed in listOf(0, 1, 42, 50321, RicochetPuzzleRules.MAX_SEED)) {
            val g = RicochetPuzzleGame(seed, true)
            val initial = g.saveState()
            assertEquals(6, g.pairsLeft)
            assertTrue((0..5).all { color -> g.pucks.count { it.color == color } == 2 })
            for (a in g.pucks) for (b in g.pucks) if (a !== b) assertTrue(hypot(a.x - b.x, a.y - b.y) > a.radius + b.radius + .1f)
            assertTrue(g.pucks.none { g.touchesWall(it.x, it.y, it.radius) })
            repeat(300) { g.step(1f / 120) }
            assertTrue(g.launch(5f, 12.5f, .2f, -1f, .7f))
            settle(g)
            g.reset()
            assertEquals(initial, g.saveState())
            val easy = RicochetPuzzleGame(seed)
            assertEquals(g.pucks.map { Triple(it.color, it.x, it.y) }, easy.pucks.map { Triple(it.color, it.x, it.y) })
        }
        assertNotEquals(RicochetPuzzleGame(42).saveState(), RicochetPuzzleGame(43).saveState())
    }

    @Test fun matchingPairsDisappearWithoutGrowingAndProjectileIsNotAMatch() {
        val g = RicochetPuzzleGame()
        g.pucks.clear()
        g.pucks.addAll(listOf(PuzzlePuck(0, 2f, 2f), PuzzlePuck(0, 3.09f, 2f),
            PuzzlePuck(1, 6f, 3f), PuzzlePuck(1, 8f, 3f)))
        assertTrue(g.launch(5f, 12.5f, 0f, -1f, .03f))
        g.step(1f / 120)
        assertEquals(1, g.pairsLeft)
        assertTrue(g.pucks.none { it.color == 0 })
        assertEquals(1, g.events.size)
        assertTrue(g.pucks.any { it.projectile })
        settle(g)
        assertTrue(g.pucks.none { it.projectile })
        assertEquals(2, g.pucks.size)
        assertTrue(g.canLaunch)
    }

    @Test fun steelProjectilePushesWhitePuckWithoutRemovingIt() {
        val g = RicochetPuzzleGame()
        g.pucks.clear()
        g.pucks.addAll(listOf(PuzzlePuck(0, 5f, 9f), PuzzlePuck(0, 8f, 2f)))
        assertTrue(g.launch(5f, 12.5f, 0f, -1f, .4f))
        settle(g)
        assertEquals(2, g.pucks.size)
        assertTrue(g.pucks.first().y < 8f)
        assertEquals(1, g.shots)
    }

    @Test fun lastPairCompletesOnceAndRefusesExtraShots() {
        val g = RicochetPuzzleGame()
        g.pucks.clear()
        g.pucks.addAll(listOf(PuzzlePuck(3, 3f, 3f), PuzzlePuck(3, 4.09f, 3f)))
        assertTrue(g.launch(5f, 12.5f, 0f, -1f, .5f))
        settle(g)
        assertTrue(g.complete)
        assertTrue(g.pucks.isEmpty())
        assertEquals(1, g.shots)
        assertFalse(g.launch(5f, 12.5f, 0f, -1f, .5f))
    }

    @Test fun movingWallPatrolsButNeverPlaysWhileIdle() {
        val g = RicochetPuzzleGame(42, true)
        val positions = g.pucks.map { it.x to it.y }
        val startX = g.wallX
        repeat(60) { g.step(1f / 120) }
        assertNotEquals(startX, g.wallX)
        repeat(1500) { g.step(1f / 120) }
        assertEquals(positions, g.pucks.map { it.x to it.y })
        assertEquals(0, g.shots)
        assertTrue(g.events.isEmpty())
        val p = g.pucks.first()
        p.x = g.wallX + (if (g.wallVelocity > 0) 1 else -1) * (RicochetPuzzleRules.WALL_HALF_WIDTH + p.radius + .03f)
        p.y = RicochetPuzzleRules.WALL_Y
        val stationary = p.x to p.y
        repeat(300) { g.step(1f / 120) }
        assertEquals(stationary, p.x to p.y)
        assertFalse(g.touchesWall(p.x, p.y, p.radius - .001f))
    }

    @Test fun wallBlocksAProjectileAndTurnsAlwaysEnd() {
        val g = RicochetPuzzleGame(4, true)
        g.pucks.clear()
        g.pucks.addAll(listOf(PuzzlePuck(0, 1f, 2f), PuzzlePuck(0, 9f, 2f)))
        assertTrue(g.launch(5f, 12.5f, 0f, -1f, 1f))
        val projectile = g.pucks.last()
        projectile.x = g.wallX; projectile.y = RicochetPuzzleRules.WALL_Y + 1f; projectile.vy = -19f
        var reflected = false
        repeat(20) { g.step(1f / 120); if (projectile.vy > 0) reflected = true }
        assertTrue(reflected)
        settle(g)
        assertTrue(g.pucks.none { it.projectile })
        assertTrue(g.canLaunch)
    }

    @Test fun savesPreserveShotAndMovingWallPhaseAtomically() {
        val g = RicochetPuzzleGame(654321, true)
        repeat(61) { g.step(1f / 120) }
        assertTrue(g.launch(3f, 12f, .4f, -1f, .7f))
        repeat(35) { g.step(1f / 120) }
        val state = g.saveState()
        val restored = RicochetPuzzleGame()
        assertTrue(restored.restoreState(state))
        assertEquals(state, restored.saveState())
        repeat(1300) { g.step(1f / 120); restored.step(1f / 120) }
        assertEquals(g.saveState(), restored.saveState())
        val before = restored.saveState()
        assertFalse(restored.restoreState(state.replace("|654321|", "|NaN|")))
        assertEquals(before, restored.saveState())
    }

    @Test fun neutralShotsAreCountedOnlyWhenAccepted() {
        val g = RicochetPuzzleGame()
        assertFalse(g.launch(Float.NaN, 12f, 0f, -1f, .5f))
        assertFalse(g.launch(5f, 5f, 0f, -1f, .5f))
        assertFalse(g.launch(5f, 12f, 0f, 1f, .5f))
        assertFalse(g.launch(5f, 12f, 0f, -1f, 0f))
        assertEquals(0, g.shots)
        assertTrue(g.launch(3f, 13f, 0f, -1f, .2f))
        assertEquals(13f, g.pucks.last().y, 0f)
        assertFalse(g.launch(5f, 12f, 0f, -1f, .5f))
        assertEquals(1, g.shots)
    }
}
