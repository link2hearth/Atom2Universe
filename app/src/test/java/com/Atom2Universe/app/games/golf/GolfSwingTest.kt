package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test

class GolfSwingTest {
    @Test fun needleStartsFromAnEdgeSoAnInstantReleaseIsNeverClean() {
        val swing = GolfSwing()
        swing.start()
        assertTrue(swing.active)
        assertEquals(-1f, swing.position, 0f)
        assertEquals(-1f, swing.release(), 0f)
        assertFalse(swing.active)
        swing.start()
        assertEquals("Sides alternate", 1f, swing.position, 0f)
    }

    @Test fun needleSweepsAtConstantSpeedAndBouncesOnTheEdges() {
        val swing = GolfSwing()
        swing.start()
        swing.update(.25f, 2f)
        assertEquals(-.5f, swing.position, 1e-5f)
        swing.update(.25f, 2f)
        assertEquals("A quarter period reaches the centre", 0f, swing.position, 1e-5f)
        swing.update(.75f, 2f)
        assertEquals("Bounced off the right edge", .5f, swing.position, 1e-5f)
        repeat(1000) { swing.update(.016f, 1.4f); assertTrue(swing.position in -1f..1f) }
    }

    @Test fun deviationHasAPerfectBandThenGrowsToTheEdges() {
        assertEquals(0f, GolfSwing.deviation(0f), 0f)
        assertEquals(0f, GolfSwing.deviation(GolfSwing.PERFECT), 0f)
        assertEquals(0f, GolfSwing.deviation(-GolfSwing.PERFECT), 0f)
        assertTrue(GolfSwing.deviation(.5f) > 0f)
        assertTrue(GolfSwing.deviation(-.5f) < 0f)
        assertEquals(1f, GolfSwing.deviation(1f), 1e-6f)
        assertEquals(-1f, GolfSwing.deviation(-1f), 1e-6f)
        assertEquals(1f, GolfSwing.deviation(Float.NaN), 0f)
    }

    @Test fun longSwingsAndBadLiesSpeedUpTheNeedle() {
        val soft = GolfSwing.period(GolfClub.IRON7, .3f, GolfLie.FAIRWAY)
        val full = GolfSwing.period(GolfClub.IRON7, 1f, GolfLie.FAIRWAY)
        assertTrue(full < soft)
        assertTrue(GolfSwing.period(GolfClub.IRON7, 1f, GolfLie.ROUGH) < full)
        assertTrue(GolfSwing.period(GolfClub.SW, 1f, GolfLie.BUNKER) < GolfSwing.period(GolfClub.SW, 1f, GolfLie.ROUGH))
        assertTrue(GolfSwing.period(GolfClub.PUTTER, 1f, GolfLie.GREEN) > full)
    }

    @Test fun putterPullIsFinerAtShortRange() {
        assertEquals(.5f, GolfSwing.power(.5f, GolfClub.IRON7), 0f)
        assertTrue(GolfSwing.power(.3f, GolfClub.PUTTER) < .2f)
        assertEquals(1f, GolfSwing.power(1f, GolfClub.PUTTER), 1e-6f)
        assertEquals(0f, GolfSwing.power(-.4f, GolfClub.PUTTER), 0f)
        assertEquals(0f, GolfSwing.power(Float.NaN, GolfClub.DRIVER), 0f)
    }

    @Test fun invalidDeltaCannotMoveTheNeedle() {
        val swing = GolfSwing()
        swing.start()
        for (dt in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            swing.update(dt, 1.5f)
            assertEquals(-1f, swing.position, 0f)
        }
        swing.update(.1f, 0f)
        assertEquals(-1f, swing.position, 0f)
    }
}
