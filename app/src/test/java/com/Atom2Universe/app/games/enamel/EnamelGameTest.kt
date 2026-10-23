package com.Atom2Universe.app.games.enamel

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Garde-fous du lancer, des contacts et de la reprise, sans moteur Android. */
class EnamelGameTest {
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
        assertTrue(strong.launch(5f, 0f, -1f, .6f))
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

    @Test fun blockedLaunchEndsOnlyWhenEntireLineIsBlocked() {
        val rows = (0..10).joinToString(";") { "0,${.4f + it * .92f},14,0,0,0" }
        val game = scene(rows, current = 4)
        settle(game)
        assertTrue(game.over)
        assertNull(game.availableLaunchX(5f))
        val open = scene("4,5,14,0,0,0", current = 4)
        settle(open)
        assertFalse(open.over)
        val x = open.availableLaunchX(5f)
        assertNotNull(x)
        assertTrue(open.isLaunchClear(x!!))
        assertFalse(open.launch(5f, 0f, -1f, 1f))
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
