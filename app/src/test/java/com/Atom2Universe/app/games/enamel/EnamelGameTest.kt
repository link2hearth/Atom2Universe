package com.Atom2Universe.app.games.enamel

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Garde-fous du lancer, des contacts et de la reprise, sans moteur Android. */
class EnamelGameTest {
    private fun EnamelGame.launch(x: Float, dx: Float, dy: Float, power: Float) = launch(x, launchY, dx, dy, power)
    private fun scene(pucks: String, current: Int = 0) = EnamelGame(Random(1)).apply {
        assertTrue(restoreState("1|0|1|-1|$current|0|0|1|$pucks"))
    }

    private fun settle(game: EnamelGame) {
        repeat(1500) {
            if (!game.moving) return
            game.step(1f / 120)
        }
        fail("Les palets doivent finir par s'arrêter")
    }

    @Test fun powerChangesTravelAndShotWaitsForRest() {
        val gentle = EnamelGame(Random(7))
        val strong = EnamelGame(Random(7))
        assertTrue(gentle.launch(5f, 0f, -1f, .12f))
        assertTrue(strong.launch(5f, 0f, -1f, .4f))
        assertFalse(strong.launch(5f, 0f, -1f, 1f))
        settle(gentle); settle(strong)
        assertTrue(strong.pucks.single().y < gentle.pucks.single().y - 5f)
        assertTrue(gentle.canLaunch)
    }

    @Test fun matchingRanksMergeAndPreserveSafeMotion() {
        val game = scene("0,4.65,5,6,0,0;0,5.32,5,0,0,0")
        game.step(1f / 120)
        assertEquals(1, game.pucks.size)
        assertEquals(1, game.pucks.single().tier)
        assertEquals(EnamelRules.points(1), game.score)
        assertTrue(game.pucks.single().vx in 0f..3f)
        settle(game)
    }

    @Test fun impactsPushOtherRanksWithoutCreatingEnergyOrTunneling() {
        val game = scene("0,5,8,0,-19,0;1,5,5,0,0,0")
        fun energy() = game.pucks.sumOf { (.5f * it.mass * (it.vx * it.vx + it.vy * it.vy)).toDouble() }
        var previous = energy()
        var pushed = false
        repeat(600) {
            game.step(1f / 60)
            val now = energy()
            assertTrue("Énergie créée : $previous -> $now", now <= previous + .002)
            previous = now
            if (game.pucks[1].y < 4.9f) pushed = true
            game.pucks.forEach { p ->
                assertTrue(p.x.isFinite() && p.y.isFinite())
                assertTrue(p.x >= p.radius - .001f && p.x <= 10 - p.radius + .001f)
                assertTrue(p.y >= p.radius - .001f && p.y <= 16 - p.radius + .001f)
            }
        }
        assertTrue("Le petit palet a transmis son impulsion au grand", pushed)
    }

    @Test fun chainsAndFinaleFreeSpace() {
        val chain = scene("0,2,3,0,0,0;0,2.67,3,0,0,0;1,3.2,3,0,0,0")
        settle(chain)
        assertEquals(2, chain.chain)
        assertEquals(EnamelRules.points(1) + 2 * EnamelRules.points(2), chain.score)
        assertEquals(2, chain.pucks.single().tier)
        val finale = scene("11,3,5,0,0,0;11,5.49,5,0,0,0")
        settle(finale)
        assertTrue(finale.pucks.isEmpty())
        assertEquals(250, finale.score)
        assertTrue(finale.events.single().finale)
    }

    @Test fun launchUsesBothCoordinatesAndGentleShotsClearTheBase() {
        for (tier in EnamelRules.WEIGHTS.indices) for (depth in floatArrayOf(.85f, 1.5f, 2.15f)) {
            val game = scene("", current = tier)
            settle(game)
            val y = game.baseTop + depth
            assertTrue(game.launch(3f, y, 0f, -1f, .03f))
            assertEquals(y, game.pucks.single().y, .0001f)
            settle(game)
            assertFalse(game.over)
            assertTrue(game.pucks.single().y + game.pucks.single().radius < game.baseTop)
        }
        val game = EnamelGame(Random(2))
        assertFalse(game.launch(5f, game.baseTop - .5f, 0f, -1f, .5f))
        assertFalse(game.launch(5f, game.height, 0f, -1f, .5f))
        assertFalse(game.launch(5f, Float.NaN, 0f, -1f, .5f))
    }

    @Test fun returningSmallPuckBouncesWithoutLosingOrGainingEnergy() {
        val game = scene("0,5,12.5,0,8,0")
        var bounced = false
        var speed = 8f
        repeat(500) {
            game.step(1f / 120)
            val p = game.pucks.single()
            if (p.vy < 0) bounced = true
            val nextSpeed = kotlin.math.hypot(p.vx, p.vy)
            assertTrue(nextSpeed <= speed + .0001f)
            speed = nextSpeed
            assertTrue(p.y + p.radius <= game.baseTop + .001f)
            assertFalse(game.over)
            assertEquals(0f, game.danger, 0f)
        }
        assertTrue(bounced)
        assertTrue(game.canLaunch)
    }

    @Test fun shotDeflectedBeforeLeavingTheBaseIsAlsoProtected() {
        val game = scene("7,5,11.55,0,0,0")
        settle(game)
        assertTrue(game.launch(5f, game.baseTop + .4f, 0f, -1f, .5f))
        settle(game)
        assertFalse("Un seul grand palet ne doit pas faire perdre sur un rebond", game.over)
        assertTrue(game.pucks.all { it.y + it.radius <= game.baseTop + .06f })
    }

    @Test fun gentleDiagonalShotsLeaveTheBaseAfterSideBounces() {
        for (x in floatArrayOf(.9f, 5f, 9.1f)) for (dx in floatArrayOf(-1f, 1f)) {
            val game = EnamelGame(Random(2))
            assertTrue(game.launch(x, game.height - .85f, dx, -1f, .03f))
            settle(game)
            assertFalse(game.over)
            assertTrue(game.pucks.single().entered)
        }
    }

    @Test fun packedBoardLosesOnlyAfterSustainedPressure() {
        val game = scene( (0..3).flatMap { row -> (0..2).map { col ->
            "${8 + (row + col) % 2},${1.8f + col * 3.2f},${1.8f + row * 3.3f},0,0,0"
        } }.joinToString(";"))
        repeat(30) { game.step(1f / 120) }
        assertFalse("Le tas doit avoir un délai pour se dégager", game.over)
        settle(game)
        assertTrue("Une planche saturée doit déborder malgré la bande de renvoi", game.over)
        assertEquals(1f, game.danger, .001f)
    }

    @Test fun clearingPressureDuringGracePeriodSavesTheBoard() {
        val game = scene("0,5,6,0,0,0")
        val p = game.pucks.single()
        p.entered = false; p.y = 14.5f; p.age = EnamelRules.LAUNCH_GRACE
        repeat(60) { game.step(1f / 120) }
        assertTrue(game.danger > 0f)
        assertFalse(game.over)
        p.y = 8f
        settle(game)
        assertFalse(game.over)
        assertTrue(game.canLaunch)
    }

    @Test fun resizeAndLegacyRestorePreserveThePlayableBoard() {
        val game = scene("0,2,14,0,0,0;4,7,5,0,0,0")
        settle(game)
        assertFalse(game.over)
        game.resize(10f)
        assertTrue(game.pucks.all { it.y >= it.radius && it.y + it.radius <= game.baseTop })
        game.resize(24f)
        val restored = EnamelGame(Random(3))
        assertTrue(restored.restoreState(game.saveState()!!))
        assertEquals(24f, restored.height, 0f)
        settle(restored)
        assertFalse(restored.over)
    }

    @Test fun savedMovingBoardResumesWithSameQueueAndScore() {
        val game = EnamelGame(Random(4))
        assertTrue(game.launch(3f, .3f, -1f, .8f))
        repeat(15) { game.step(1f / 120) }
        val state = game.saveState()!!
        val restored = EnamelGame(Random(9))
        assertTrue(restored.restoreState(state))
        assertEquals(game.current, restored.current)
        assertEquals(game.next, restored.next)
        assertEquals(game.score, restored.score)
        assertEquals(state, restored.saveState())
        settle(game); settle(restored)
        assertEquals(game.pucks.single().x, restored.pucks.single().x, .001f)
        assertEquals(game.pucks.single().y, restored.pucks.single().y, .001f)
        val before = restored.saveState()
        assertFalse(restored.restoreState("1|0|1|-1|0|0|0|1|0,NaN,5,0,0,0"))
        assertEquals(before, restored.saveState())
    }

    @Test fun invalidAndCancelledShotsDoNotConsumePuck() {
        val game = EnamelGame(Random(2))
        assertFalse(game.launch(5f, 0f, -1f, 0f))
        assertFalse(game.launch(5f, 0f, 1f, 1f))
        assertFalse(game.launch(Float.NaN, 0f, -1f, 1f))
        assertFalse(game.launch(5f, Float.NaN, -1f, 1f))
        assertEquals(0, game.shots)
        assertTrue(game.pucks.isEmpty())
    }
}
