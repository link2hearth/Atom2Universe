package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.PhysicsNode
import com.Atom2Universe.app.games.caves.world.AIR
import org.junit.Assert.*
import org.junit.Test

class WaterCurrentPhysicsTest {
    private fun drift(fps: Int, wall: Boolean = false, flow: Double = 1.25): Double {
        val physics = PhysicsNode { _, _, _ -> AIR }
        physics.waterContainsPoint = { _, _, _ -> true }
        physics.sampleWaterCurrent = { _, _, _, out ->
            out.fill(0.0); out[0] = flow; out[3] = 1.0
        }
        physics.dynamicCollision = { x, feet, _, _ -> feet < 0.0 || wall && x + .29 > 1.0 }
        var p = Triple(0.0, 1.62, 0.0)
        repeat(fps * 2) {
            p = physics.updateWalk(1f / fps, p.first, p.second, p.third,
                1f, 0f, 0f, 1f, 0f, 0f, false)
        }
        return p.first
    }

    @Test fun currentMovesAnIdlePlayerButStillWaterDoesNot() {
        assertTrue(drift(60) > 1.5)
        assertEquals(0.0, drift(60, flow = 0.0), 0.00001)
    }

    @Test fun currentCannotPushThroughAWall() {
        val x = drift(60, wall = true)
        assertTrue(x > .5 && x <= .71)
    }

    @Test fun driftIsSimilarAtThirtyAndOneHundredTwentyFramesPerSecond() {
        assertEquals(drift(30), drift(120), .03)
    }
}
