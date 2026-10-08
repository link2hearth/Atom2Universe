package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class PhysicsAdvancedMachineTest {

    private fun machineWorld() = PhysWorld().apply {
        gravity = 0f
        linearDamping = 0f
        angularDamping = 0f
        iterations = 18
    }

    @Test
    fun `deux engrenages respectent leur rapport sans creer d energie`() {
        val world = machineWorld()
        val postA = anchorPost(0f, 0f)
        val postB = anchorPost(2f, 0f)
        val a = PhysBody.circle(0.5f, 10f).apply { x = 0f; omega = 10f }
        val b = PhysBody.circle(1f, 20f).apply { x = 2f }
        world.add(postA); world.add(postB); world.add(a); world.add(b)
        world.addJoint(RevoluteJoint.pin(postA, a, 0f, 0f))
        world.addJoint(RevoluteJoint.pin(postB, b, 2f, 0f))
        val gear = GearJoint.external(a, 20, b, 40)
        world.addJoint(gear)
        val initialEnergy = 0.5f * a.inertia * a.omega * a.omega

        world.simulate(0.5f)

        assertTrue("le rapport de denture est faux", abs(b.omega + 0.5f * a.omega) < 0.02f)
        val finalEnergy = 0.5f * a.inertia * a.omega * a.omega +
            0.5f * b.inertia * b.omega * b.omega
        assertTrue("la mise en prise crée de l'énergie", finalEnergy <= initialEnergy * 1.001f)
    }

    @Test
    fun `un embrayage patine au couple regle`() {
        val world = machineWorld()
        val drive = PhysBody.circle(0.3f, 2f).apply { omega = 30f }
        val load = PhysBody.circle(1f, 100f)
        world.add(drive); world.add(load)
        val clutch = GearJoint(drive, load, 1f).apply { maxTorqueOnB = 4f }
        world.addJoint(clutch)

        world.stepFrame(0.1f)

        assertTrue("l'embrayage devrait patiner", clutch.slipping)
        assertTrue("le couple limite est dépassé", abs(clutch.reactionTorqueOnB) <= 4.01f)
    }

    @Test
    fun `une glissiere retient le piston et respecte sa course`() {
        val world = machineWorld()
        val frame = anchorPost(0f, 0f)
        val piston = PhysBody(0.15f, 0.1f, 5f).apply { x = 0.5f }
        world.add(frame); world.add(piston)
        val slide = PrismaticJoint(frame, piston).apply {
            setWorldAnchorsAndAxis(0f, 0f, 0.5f, 0f, 1f, 0f)
            limitsEnabled = true
            lowerTranslation = 0f
            upperTranslation = 2f
            motorEnabled = true
            motorSpeed = 4f
            maxMotorForce = 200f
        }
        world.addJoint(slide)

        world.simulate(2f)

        assertTrue("le piston sort de sa course : x=${piston.x}", piston.x <= 2.03f)
        assertTrue("le piston quitte son axe : y=${piston.y}", abs(piston.y) < 0.02f)
        assertTrue("le piston tourne dans le cylindre : angle=${piston.angle}", abs(piston.angle) < 0.02f)
    }

    @Test
    fun `une vanne pneumatique conserve masse et energie`() {
        val high = PneumaticChamber(0.02f, 700_000f)
        val low = PneumaticChamber(0.02f, 0f)
        val pipe = PneumaticPipe(high, low, area = 2e-5f)
        val massBefore = high.gasMass + low.gasMass
        val energyBefore = high.internalEnergy + low.internalEnergy
        val deltaBefore = high.absolutePressure - low.absolutePressure

        repeat(200) { pipe.step(0.001f) }

        assertEquals(massBefore, high.gasMass + low.gasMass, massBefore * 1e-4f)
        assertEquals(energyBefore, high.internalEnergy + low.internalEnergy, energyBefore * 1e-4f)
        assertTrue("la vanne n'égalise pas les pressions",
            abs(high.absolutePressure - low.absolutePressure) < deltaBefore)
    }

    @Test
    fun `un canon pneumatique accelere le projectile et recule`() {
        val world = machineWorld()
        val frame = PhysBody(0.5f, 0.2f, 20f).apply { collidesWith = 0 }
        val projectile = PhysBody.circle(0.04f, 0.5f).apply { x = 0.01f; collidesWith = 0 }
        val chamber = PneumaticChamber(0.002f, 600_000f)
        val launcher = PressureLauncher(
            frame, projectile, chamber,
            boreArea = PI.toFloat() * 0.04f * 0.04f,
            barrelLength = 1f,
            deadVolume = 0.002f
        )
        world.add(frame); world.add(projectile)

        repeat(2_000) {
            launcher.updateAndApply()
            world.step(0.0005f)
            if (launcher.released) return@repeat
        }

        assertTrue("le projectile ne sort pas du canon", projectile.vx > 10f)
        assertTrue("le bâti n'encaisse pas le recul", frame.vx < 0f)
        assertEquals("le lanceur crée de la quantité de mouvement", 0f,
            frame.mass * frame.vx + projectile.mass * projectile.vx, 0.05f)
    }

    @Test
    fun `une buse haute pression fournit un jet capable dattaquer lacier`() {
        val tank = PressurizedLiquidTank(
            initialLiquidVolume = 0.02f,
            initialGaugePressure = 600e6f,
            maxGaugePressure = 700e6f
        )
        val radius = 0.0002f
        val nozzle = LiquidJetNozzle(tank, PI.toFloat() * radius * radius)
        val jet = nozzle.fire(0.01f)
        val cut = JetCuttingModel.evaluate(
            jet, MachineMaterials.STEEL,
            kerfArea = 0.001f * 0.01f,
            abrasiveMultiplier = 1.5f
        )

        assertTrue("la buse ne produit pas de vitesse", jet.exitVelocity > 800f)
        assertTrue("la puissance cinétique dépasse l'énergie consommée",
            jet.kineticPower <= jet.consumedPower * 1.001f)
        assertTrue("le modèle ne reconnaît pas la découpe haute pression", cut.canCut)
        assertTrue("la coupe n'avance pas", cut.penetrationSpeed > 0f)
    }

    @Test
    fun `une roue dentee de cinq cents metres reste representable`() {
        val giant = GearSpec(
            pitchRadius = 250f,
            teeth = 2_000,
            faceWidth = 8f,
            boreRadius = 80f,
            material = MachineMaterials.STEEL
        )
        val body = MachinePartFactory.gear(giant)

        assertTrue("la géométrie géante déborde", body.radius.isFinite() && body.radius > 250f)
        assertTrue("la masse géante déborde", body.mass.isFinite() && body.mass > 1e9f)
        assertTrue("l'inertie géante déborde", body.inertia.isFinite() && body.inertia > 1e13f)
        assertEquals(0.25f, giant.module, 1e-5f)
    }

    @Test
    fun `une liaison desactivee ne supprime plus les collisions`() {
        val world = machineWorld()
        val a = PhysBody.circle(0.5f, 2f).apply { x = -0.2f }
        val b = PhysBody.circle(0.5f, 2f).apply { x = 0.2f }
        world.add(a); world.add(b)
        world.addJoint(DistanceJoint.rope(a, b, 1f).apply { enabled = false })

        world.simulate(0.5f)

        assertTrue("les anciennes pièces liées se traversent encore", b.x - a.x > 0.9f)
    }

    @Test
    fun `les couches physiques separent les collisions mais acceptent une piece epaisse`() {
        fun separation(layerB: Int, depthA: Int): Float {
            val world = machineWorld()
            val a = PhysBody.circle(0.5f, 2f).apply {
                x = -0.2f
                collisionLayer = 0
                collisionLayerDepth = depthA
            }
            val b = PhysBody.circle(0.5f, 2f).apply {
                x = 0.2f
                collisionLayer = layerB
            }
            world.add(a); world.add(b)
            world.simulate(0.5f)
            return b.x - a.x
        }

        assertTrue("deux couches séparées collisionnent", separation(layerB = 1, depthA = 1) < 0.5f)
        assertTrue("la profondeur multi-couche ne collisionne pas", separation(layerB = 1, depthA = 2) > 0.9f)
    }

    @Test
    fun `un engrenage ne transmet pas entre deux couches physiques`() {
        val world = machineWorld()
        val drive = PhysBody.circle(0.5f, 5f).apply { omega = 10f; collisionLayer = 0 }
        val driven = PhysBody.circle(0.5f, 5f).apply { collisionLayer = 1 }
        world.add(drive); world.add(driven)
        world.addJoint(GearJoint(drive, driven, 1f))

        world.simulate(0.2f)
        assertEquals("la denture traverse les couches", 0f, driven.omega, 1e-5f)

        driven.collisionLayer = 0
        world.simulate(0.2f)
        assertTrue("la denture ne reprend pas sur la même couche", driven.omega < -1f)
    }
}
