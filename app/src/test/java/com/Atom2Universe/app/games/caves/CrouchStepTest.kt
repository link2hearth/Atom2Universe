package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.PhysicsNode
import com.Atom2Universe.app.games.caves.world.AIR
import org.junit.Assert.*
import org.junit.Test

class CrouchStepTest {
    /** Simulated collision volumes avoid loading the Android block/texture registry. */
    private fun walk(stepHeight: Double, ceiling: Double = 10.0, secondStep: Boolean = false): Triple<Double, Double, Double> {
        val physics = PhysicsNode { _, _, _ -> AIR }
        physics.dynamicCollision = { x, feet, _, height ->
            feet < -1e-6 || feet + height > ceiling ||
                (x + .29 > 1.0 && feet < stepHeight - 1e-6) ||
                (secondStep && x + .29 > 2.0 && feet < 1.0 - 1e-6)
        }
        var position = Triple(0.0, 1.62, 0.0)
        physics.onGround = true
        repeat(240) {
            physics.updateCrouch(true, position.first, position.second, position.third)
            position = physics.updateWalk(1f / 60f, position.first, position.second, position.third,
                1f, 0f, 0f, 1f, 1f, 0f, false)
            assertTrue(physics.isCrouching)
        }
        return position
    }

    @Test fun crouchingClimbsConsecutiveHalfStepsWithoutCancellingTheRiseEveryFrame() {
        val position = walk(.5, secondStep = true)
        assertTrue(position.first > 2.5)
        assertEquals(2.62, position.second, .02)
    }

    @Test fun crouchingStillCannotClimbAFullBlock() {
        val position = walk(1.0)
        assertTrue(position.first < 1.0)
        assertEquals(1.62, position.second, .02)
    }

    @Test fun halfStepCannotPushTheCrouchedHeadThroughACeiling() {
        val position = walk(.5, ceiling = 1.65)
        assertTrue(position.first < 1.0)
        assertEquals(1.62, position.second, .02)
    }
}