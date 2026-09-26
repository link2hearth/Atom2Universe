package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.PhysicsNode
import com.Atom2Universe.app.games.caves.world.AIR
import org.junit.Assert.*
import org.junit.Test

class CrouchLedgeTest {
    private class Walker(val floor: Double = 0.0, val corner: Boolean = false) {
        val physics = PhysicsNode { _, _, _ -> AIR }
        var position = Triple(.5, floor + 1.62, .5)

        init {
            // Plateforme de x=0 à x=1 ; soit longue en Z, soit limitée à un bloc.
            physics.decorCollision = { x, feet, z, _ ->
                feet < floor && x + .29 > 0.0 && x - .30 < 1.0 &&
                    (!corner || (z + .29 > 0.0 && z - .30 < 1.0))
            }
            physics.onGround = true
        }

        fun step(dx: Float, dz: Float = 0f, crouch: Boolean = true, jump: Boolean = false) {
            val (x, y, z) = position
            physics.updateCrouch(crouch, x, y, z)
            position = physics.updateWalk(1f / 60f, x, y, z,
                1f, 0f, 0f, -1f, dx, dz, jump)
        }
    }

    @Test fun stopsAtBothEdgesOfABlock() {
        for (direction in listOf(-1f, 1f)) {
            val w = Walker()
            repeat(180) { w.step(direction) }
            assertEquals(1.62, w.position.second, .002)
            assertTrue(w.physics.onGround)
            val expected = if (direction > 0) 1.30 else -.29
            assertEquals(expected, w.position.first, .002)
        }
    }

    @Test fun diagonalMovementCannotLeaveACorner() {
        val w = Walker(corner = true)
        repeat(180) { w.step(1f, 1f) }
        assertEquals(1.30, w.position.first, .002)
        assertEquals(1.30, w.position.third, .002)
        assertEquals(1.62, w.position.second, .002)
        assertTrue(w.physics.onGround)
    }

    @Test fun canSlideAlongTheEdgeAndWalkBack() {
        val w = Walker()
        repeat(90) { w.step(1f) }
        val edge = w.position.first
        repeat(60) { w.step(1f, 1f) }
        assertEquals(edge, w.position.first, .002)
        assertTrue(w.position.third > 1.5)
        repeat(30) { w.step(-1f) }
        assertTrue(w.position.first < 1.0)
        assertEquals(1.62, w.position.second, .002)
    }

    @Test fun slabHeightAlsoProvidesSupport() {
        val w = Walker(floor = .5)
        repeat(180) { w.step(1f) }
        assertEquals(2.12, w.position.second, .002)
        assertEquals(1.30, w.position.first, .002)
    }

    @Test fun releasingCrouchAllowsWalkingOff() {
        val w = Walker()
        repeat(90) { w.step(1f) }
        repeat(30) { w.step(1f, crouch = false) }
        assertTrue(w.position.first > 1.5)
        assertTrue(w.position.second < 1.0)
        assertFalse(w.physics.onGround)
    }

    @Test fun canJumpOffWhileCrouching() {
        val w = Walker()
        repeat(90) { w.step(1f) }
        w.step(1f, jump = true)
        assertTrue(w.position.first > 1.30)
        assertTrue(w.position.second > 1.62)
        assertFalse(w.physics.onGround)
    }

    @Test fun knockbackCanStillPushPlayerOff() {
        val w = Walker()
        repeat(90) { w.step(1f) }
        w.physics.applyKnockback(4.0, 0.0)
        repeat(30) { w.step(0f) }
        assertTrue(w.position.first > 1.30)
        assertTrue(w.position.second < 1.0)
    }

    @Test fun crouchingInMidAirDoesNotStopAFall() {
        val w = Walker()
        w.position = Triple(1.5, 3.0, .5)
        w.physics.onGround = false
        repeat(30) { w.step(1f) }
        assertTrue(w.position.first > 1.8)
        assertTrue(w.position.second < 1.0)
    }
}
