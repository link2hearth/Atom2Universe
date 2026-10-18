package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.ui.BilliardStrokeGesture
import com.Atom2Universe.app.games.billiards.ui.billiardTimingPower
import org.junit.Assert.*
import org.junit.Test

class BilliardShotInputTest {
    @Test fun equalDistanceProducesMorePowerWhenTheStrokeIsFaster() {
        val slow=BilliardStrokeGesture().apply { begin(150f,0); move(100f,250) }
        val fast=BilliardStrokeGesture().apply { begin(150f,0); move(100f,50) }
        val slowPower=checkNotNull(slow.release(100f,250))
        val fastPower=checkNotNull(fast.release(100f,50))
        assertTrue(fastPower>slowPower*4)
    }
    @Test fun tapsBackwardStrokesCancellationAndPausesDoNotFire() {
        val stroke=BilliardStrokeGesture()
        stroke.begin(100f,0); assertNull(stroke.release(100f,20))
        stroke.begin(100f,0); assertNull(stroke.release(150f,50))
        stroke.begin(150f,0); stroke.move(100f,40); stroke.cancel()
        assertNull(stroke.release(100f,50))
        stroke.begin(150f,0); stroke.move(100f,40)
        assertNull(stroke.release(100f,400))
    }
    @Test fun holdingBeforeTheStrokeAndSmallReleaseJitterDoNotCancelIt() {
        val stroke=BilliardStrokeGesture()
        stroke.begin(150f,0)
        stroke.move(140f,1010); stroke.move(120f,1030); stroke.move(100f,1050)
        assertTrue(checkNotNull(stroke.release(101f,1060))>.4f)
    }
    @Test fun timingMeterGoesUpDownAndRepeats() {
        assertEquals(0f,billiardTimingPower(0),0f)
        assertEquals(.5f,billiardTimingPower(450),0f)
        assertEquals(1f,billiardTimingPower(900),0f)
        assertEquals(.5f,billiardTimingPower(1350),0f)
        assertEquals(0f,billiardTimingPower(1800),0f)
        assertEquals(.5f,billiardTimingPower(2250),0f)
    }
}
