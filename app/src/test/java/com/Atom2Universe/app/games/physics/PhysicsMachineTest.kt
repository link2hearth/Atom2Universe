package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Garde-fous des primitives destinées aux machines du bac à sable. */
class PhysicsMachineTest {

    @Test
    fun `une force ne depend pas du nombre de sous pas`() {
        fun driven(withFastNeighbour: Boolean): Pair<PhysBody, Int> {
            val world = PhysWorld().apply {
                gravity = 0f
                linearDamping = 0f
                angularDamping = 0f
            }
            val body = PhysBody(0.2f, 0.2f, 2f).apply {
                y = 20f
                category = 2
                collidesWith = 2
            }
            world.add(body)
            if (withFastNeighbour) {
                val wall = PhysBody(0.03f, 2f, 0f).apply {
                    x = 5f; lockPosition = true; lockRotation = true; refreshMass()
                }
                val bullet = PhysBody.circle(0.05f, 1f).apply { vx = 100f }
                world.add(wall)
                world.add(bullet)
            }
            body.applyForce(10f, 0f)
            val subSteps = world.subStepsFor(0.1f)
            world.stepFrame(0.1f)
            return body to subSteps
        }

        val (single, oneStep) = driven(false)
        val (split, manySteps) = driven(true)

        assertEquals(1, oneStep)
        assertTrue("le scénario rapide ne déclenche pas les sous-pas", manySteps > 1)
        assertEquals("la poussée change avec le découpage", single.vx, split.vx, 1e-4f)
        assertEquals("F·dt/m n'est pas respecté", 0.5f, split.vx, 1e-4f)
        assertEquals("la force n'est pas consommée après l'image", 0f, split.forceX, 0f)
    }

    @Test
    fun `une impulsion en bout de barre produit le bon elan angulaire`() {
        val bar = PhysBody(1f, 0.05f, 6f)
        val impulse = 3f
        bar.applyImpulseAtWorldPoint(0f, impulse, 1f, 0f)

        val expectedOmega = impulse / bar.inertia
        assertEquals(expectedOmega, bar.omega, 1e-5f)
        assertEquals(impulse / bar.mass, bar.vy, 1e-5f)
    }

    @Test
    fun `un moteur daxe est limite en couple puis atteint sa vitesse`() {
        val world = PhysWorld().apply {
            gravity = 0f
            linearDamping = 0f
            angularDamping = 0f
        }
        val post = anchorPost(0f, 0f)
        val wheel = PhysBody.circle(0.5f, 8f)
        world.add(post)
        world.add(wheel)
        val axle = RevoluteJoint.pin(post, wheel, 0f, 0f).apply {
            motorEnabled = true
            motorSpeed = 12f
            maxMotorTorque = 6f
        }
        world.addJoint(axle)

        world.stepFrame(0.25f)
        val expectedFirst = 6f * 0.25f / wheel.inertia
        assertEquals("le couple maximal n'est pas respecté", expectedFirst, wheel.omega, 0.05f)

        world.simulate(3f)
        assertTrue("le moteur dépasse sa consigne : ${wheel.omega}", wheel.omega <= 12.05f)
        assertTrue("le moteur n'atteint pas sa consigne : ${wheel.omega}", wheel.omega >= 11.9f)

        // La même primitive devient un frein de palier à vitesse cible nulle.
        axle.motorSpeed = 0f
        axle.maxMotorTorque = 3f
        world.simulate(5f)
        assertTrue("le frein d'axe ne stoppe pas la roue : ${wheel.omega}", abs(wheel.omega) < 0.05f)
    }

    @Test
    fun `un reservoir se detend et une pompe lui rend de l energie`() {
        val chamber = PneumaticChamber(
            initialVolume = 0.01f,
            initialGaugePressure = 500_000f
        )
        val pressureBefore = chamber.gaugePressure
        val energyBefore = chamber.availableEnergy

        chamber.setVolume(0.02f)
        assertTrue("la pression ne baisse pas pendant la détente", chamber.gaugePressure < pressureBefore)
        assertTrue("le gaz crée de l'énergie pendant la détente", chamber.availableEnergy < energyBefore)

        val beforePump = chamber.internalEnergy
        chamber.pump(powerWatts = 1_000f, dt = 2f, efficiency = 0.75f)
        assertEquals("la puissance de pompe n'est pas convertie en énergie", 1_500f,
            chamber.internalEnergy - beforePump, 0.5f)
    }

    @Test
    fun `un verin pousse deux corps en sens opposes`() {
        val world = PhysWorld().apply { gravity = 0f; linearDamping = 0f; angularDamping = 0f }
        val left = PhysBody(0.1f, 0.1f, 2f).apply { x = 0f; collidesWith = 0 }
        val right = PhysBody(0.1f, 0.1f, 2f).apply { x = 0.5f; collidesWith = 0 }
        world.add(left)
        world.add(right)
        val chamber = PneumaticChamber(0.0015f, initialGaugePressure = 200_000f)
        val actuator = PneumaticActuator(
            left, right, chamber,
            pistonArea = 0.001f,
            deadVolume = 0.001f,
            stroke = 1f
        ).apply { maxForce = 500f }

        actuator.updateAndApply()
        world.stepFrame(0.02f)

        assertTrue("le piston ne pousse pas le corps gauche", left.vx < 0f)
        assertTrue("le piston ne pousse pas le corps droit", right.vx > 0f)
        assertEquals("le vérin ne conserve pas la quantité de mouvement", 0f,
            left.mass * left.vx + right.mass * right.vx, 1e-4f)
    }
}
