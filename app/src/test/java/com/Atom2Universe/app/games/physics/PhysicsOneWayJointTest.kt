package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsOneWayJointTest {
    private fun wheel(x: Float): PhysBody = PhysBody.circle(1f, 10f).apply {
        this.x = x
        lockPosition = true
        refreshMass()
    }

    @Test
    fun `la roue libre pousse mais ne freine jamais la sortie`() {
        val input = wheel(0f)
        val output = wheel(3f)
        val world = PhysWorld().apply {
            gravity = 0f
            linearDamping = 0f
            angularDamping = 0f
            sleepEnabled = false
            add(input); add(output)
        }
        val clutch = OneWayRotaryJoint(input, output, ratio = 1f, inputDirection = 1)
        world.addJoint(clutch)
        input.omega = 10f
        world.stepFrame(1f / 120f)
        assertTrue(output.omega > 0f)
        assertTrue(clutch.engaged)

        input.omega = 0f
        val coasting = output.omega
        world.stepFrame(1f / 120f)
        assertFalse(clutch.engaged)
        assertTrue(output.omega >= coasting - 1e-5f)
        assertTrue(kotlin.math.abs(input.omega) < 1e-5f)
    }

    @Test
    fun `le mauvais sens de pedalage reste libre`() {
        val input = wheel(0f)
        val output = wheel(3f)
        val world = PhysWorld().apply {
            gravity = 0f
            angularDamping = 0f
            sleepEnabled = false
            add(input); add(output)
        }
        val clutch = OneWayRotaryJoint(input, output, ratio = 1f, inputDirection = 1)
        world.addJoint(clutch)
        input.omega = -10f
        world.stepFrame(1f / 60f)

        assertFalse(clutch.engaged)
        assertTrue(kotlin.math.abs(output.omega) < 1e-5f)
    }

    @Test
    fun `une roue libre inversee entraine dans le sens horaire`() {
        val input = wheel(0f)
        val output = wheel(3f)
        val world = PhysWorld().apply {
            gravity = 0f
            angularDamping = 0f
            sleepEnabled = false
            add(input); add(output)
        }
        val clutch = OneWayRotaryJoint(input, output, ratio = 1f, inputDirection = -1)
        world.addJoint(clutch)
        input.omega = -10f
        world.stepFrame(1f / 60f)

        assertTrue(clutch.engaged)
        assertTrue(output.omega < 0f)
    }
}
