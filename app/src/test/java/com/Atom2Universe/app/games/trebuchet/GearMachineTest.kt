package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineLibrary
import com.Atom2Universe.app.games.trebuchet.gears.GearMachinePreset
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineRules
import com.Atom2Universe.app.games.trebuchet.gears.GearLinkConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearLinkKind
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelKind
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelMaterial
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GearMachineTest {

    @Test
    fun `la machine par defaut forme une cascade de trois engrenages`() {
        val game = GearMachineGame()
        assertEquals(3, game.gears.size)
        assertEquals(2, game.meshes.size)

        game.spinGear(1, 8f)
        repeat(120) { game.step(1f / 120f) }

        val large = game.gears.first { it.wheel.id == 1 }.body.omega
        val medium = game.gears.first { it.wheel.id == 2 }.body.omega
        val small = game.gears.first { it.wheel.id == 3 }.body.omega
        assertTrue("la roue moyenne ne tourne pas en sens inverse", medium * large < 0f)
        assertTrue("la petite ne revient pas dans le sens de la grande", small * large > 0f)
        assertTrue(
            "la cascade ne multiplie pas la vitesse : grande=$large petite=$small",
            abs(small) > abs(large) * 3f
        )
    }

    @Test
    fun `deplacer une roue pres dune autre la clipse sur les dents`() {
        val config = GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 2f, 24, 0)))
        val game = GearMachineGame(config)
        val id = game.addGear(24, 3.0f, 2f, 0)!!
        val added = game.config.wheels.first { it.id == id }
        val expected = 2f * added.pitchRadius

        assertEquals(expected, added.x, 1e-4f)
        assertEquals(1, game.meshes.size)
    }

    @Test
    fun `laimant aligne la distance et la phase puis se libere sans arracher la roue`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0)
        )))
        val id = game.addGear(24, 6f, 2f, 0)!!

        val target = game.moveGearMagnetic(id, 2.96f, 2f, null)
        val moved = game.config.wheels.first { it.id == id }
        assertEquals(1, target)
        assertEquals(2.88f, moved.x, 1e-4f)
        assertTrue("la phase de denture doit être ajustée", abs(moved.angle) > 1e-4f)

        val detached = game.moveGearMagnetic(id, 3.4f, 2f, target)
        assertEquals(null, detached)
        assertEquals(3.4f, game.config.wheels.first { it.id == id }.x, 1e-4f)
    }

    @Test
    fun `redimensionner la roue centrale conserve toute la chaine`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, -2.88f, 2f, 24, 0),
            GearWheelConfig(2, 0f, 2f, 24, 0),
            GearWheelConfig(3, 2.88f, 2f, 24, 0)
        )))

        val result = game.resizeGearAndReflow(2, 48)!!
        assertTrue(result.success)
        assertEquals(0f, game.config.wheels.first { it.id == 2 }.x, 1e-5f)
        assertEquals(-4.32f, game.config.wheels.first { it.id == 1 }.x, 1e-4f)
        assertEquals(4.32f, game.config.wheels.first { it.id == 3 }.x, 1e-4f)
        assertEquals(2, game.meshes.size)
    }

    @Test
    fun `le reamenagement traverse les axes entre couches`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0),
            GearWheelConfig(2, 2.88f, 2f, 24, 0),
            GearWheelConfig(3, 0f, 2f, 24, 1),
            GearWheelConfig(4, 2.88f, 2f, 24, 1)
        )))
        assertTrue(game.addLink(1, 3, GearLinkKind.SHAFT_CLUTCH))

        val result = game.resizeGearAndReflow(2, 48)!!
        assertTrue(result.success)
        assertEquals(-1.44f, game.config.wheels.first { it.id == 1 }.x, 1e-4f)
        assertEquals(-1.44f, game.config.wheels.first { it.id == 3 }.x, 1e-4f)
        assertEquals(1.44f, game.config.wheels.first { it.id == 4 }.x, 1e-4f)
        assertEquals(2, game.meshes.size)
    }

    @Test
    fun `une collision annule entierement le changement de taille`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0),
            GearWheelConfig(2, 2.88f, 2f, 24, 0),
            GearWheelConfig(3, 7f, 2f, 24, 0)
        )))

        val result = game.resizeGearAndReflow(1, 48)!!
        assertFalse(result.success)
        assertTrue(result.conflicts.containsAll(setOf(1, 2, 3)))
        assertEquals(24, game.config.wheels.first { it.id == 1 }.teeth)
        assertEquals(2.88f, game.config.wheels.first { it.id == 2 }.x, 1e-5f)
        assertEquals(1, game.meshes.size)
    }

    @Test
    fun `redimensionner une roue isolee ignore les conflits exterieurs`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, -10f, 2f, 24, 0),
            // Zone indépendante déjà trop serrée : elle n'est pas concernée par
            // l'édition de la roue 1 et ne doit donc pas la bloquer.
            GearWheelConfig(2, 8f, 2f, 24, 0),
            GearWheelConfig(3, 8.5f, 2f, 24, 0)
        )))

        val result = game.resizeGearAndReflow(1, 48)!!

        assertTrue(result.success)
        assertEquals(48, game.config.wheels.first { it.id == 1 }.teeth)
    }

    @Test
    fun `une copie reste libre et peut etre redimensionnee`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0),
            GearWheelConfig(2, -2.88f, 2f, 24, 0)
        )))

        val copyId = game.duplicateGear(1)!!

        assertTrue(game.meshes.none { copyId == it.firstId || copyId == it.secondId })
        val result = game.resizeGearAndReflow(copyId, 48)!!
        assertTrue(result.success)
        assertEquals(48, game.config.wheels.first { it.id == copyId }.teeth)
    }

    @Test
    fun `deplacer un bati conserve les distances de son assemblage`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0),
            GearWheelConfig(2, 2.88f, 2f, 24, 0),
            GearWheelConfig(3, 5.76f, 2f, 24, 0)
        )))
        val before = game.config.wheels.associate { it.id to floatArrayOf(it.x, it.y) }

        game.moveAssembly(2, 6f, 5f)

        assertEquals(setOf(1, 2, 3), game.assemblyIds(2))
        for (wheel in game.config.wheels) {
            assertEquals(6f - before[2]!![0], wheel.x - before[wheel.id]!![0], 1e-4f)
            assertEquals(5f - before[2]!![1], wheel.y - before[wheel.id]!![1], 1e-4f)
        }
    }

    @Test
    fun `les editions a chaud conservent le mouvement des roues existantes`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0)
        )))
        game.spinGear(1, 30f)
        val initialEnergy = game.rotationalEnergy()

        game.addGear(12, 12f, 2f, 0)
        assertEquals(initialEnergy, game.rotationalEnergy(setOf(1)), initialEnergy * 1e-5f)
        assertTrue(game.gears.first { it.wheel.id == 1 }.body.omega > 0f)

        val resized = game.resizeGearAndReflow(1, 36)!!
        assertTrue(resized.success)
        assertEquals(initialEnergy, game.rotationalEnergy(setOf(1)), initialEnergy * 1e-5f)
        assertTrue(game.gears.first { it.wheel.id == 1 }.body.omega > 0f)

        game.changeMaterial(1, 1)
        assertEquals(initialEnergy, game.rotationalEnergy(setOf(1)), initialEnergy * 1e-5f)
    }

    @Test
    fun `une edition de machine ne supprime pas le projectile en vol`() {
        val game = GearMachineGame()
        game.spinGear(1, 8f)
        assertTrue(game.attachLauncher(3))
        assertTrue(game.launchProjectile())
        val shot = game.projectile!!
        val vx = shot.body.vx
        val vy = shot.body.vy

        game.addGear(12, 20f, 3f, 2)

        assertSame(shot, game.projectile)
        assertEquals(vx, game.projectile!!.body.vx, 1e-6f)
        assertEquals(vy, game.projectile!!.body.vy, 1e-6f)
    }

    @Test
    fun `deux roues de couches differentes ne sengrenent pas`() {
        val config = GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, 0),
            GearWheelConfig(2, 2.88f, 2f, 24, 1)
        ))
        val game = GearMachineGame(config)
        assertEquals(0, game.meshes.size)

        game.changeLayer(2, -1)
        assertEquals(1, game.meshes.size)
    }

    @Test
    fun `les supports automatiques ne collisionnent avec rien`() {
        val game = GearMachineGame()
        assertTrue(game.gears.all { it.support.collidesWith == 0 })
        assertTrue(game.gears.all { it.body.collidesWith == 0 })
    }

    @Test
    fun `une sauvegarde engrenages fait un aller retour profond`() {
        val config = GearMachineConfig(mutableListOf(
            GearWheelConfig(7, -12.5f, 3.25f, 96, -2),
            GearWheelConfig(8, 1.5f, 9f, 12, 4)
        ))
        val encoded = GearMachineLibrary.encode(listOf(GearMachinePreset("Mon moulin", config)))
        config.wheels[0].x = 999f
        val decoded = GearMachineLibrary.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals("Mon moulin", decoded[0].name)
        assertEquals(2, decoded[0].config.wheels.size)
        assertEquals(-12.5f, decoded[0].config.wheels[0].x, 1e-5f)
        assertEquals(-2, decoded[0].config.wheels[0].layer)
    }

    @Test
    fun `la sauvegarde conserve la phase des dents et lit encore G3`() {
        val config = GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24, angle = 0.37f)
        ))
        val decoded = GearMachineLibrary.decode(
            GearMachineLibrary.encode(listOf(GearMachinePreset("Phase", config)))
        ).single().config
        assertEquals(0.37f, decoded.wheels.single().angle, 1e-5f)

        val old = GearMachineLibrary.decode(
            "G3\tAncienne\t1,0.0,2.0,24,0,GEAR,STEEL\n"
        ).single().config
        assertEquals(0f, old.wheels.single().angle, 1e-5f)
    }

    @Test
    fun `la bibliotheque ignore les roues corrompues et borne les autres`() {
        val dirty = "G1\tValide\t1,0.0,2.0,9999,999\tmal,ecrite\t2,NaN,0.0,24,0\n"
        val decoded = GearMachineLibrary.decode(dirty)

        assertEquals(1, decoded.size)
        assertEquals(1, decoded[0].config.wheels.size)
        assertEquals(GearMachineRules.MAX_TEETH, decoded[0].config.wheels[0].teeth)
        assertEquals(GearMachineRules.MAX_LAYER, decoded[0].config.wheels[0].layer)
    }

    @Test
    fun `supprimer la derniere roue laisse un atelier valide`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 2f, 24))))
        assertTrue(game.deleteGear(1))
        assertTrue(game.gears.isEmpty())
        assertFalse(game.deleteGear(1))
        assertEquals(4, game.bounds().size)
    }

    @Test
    fun `un volant acier stocke plus quun engrenage de meme diametre`() {
        val gear = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 48, kind = GearWheelKind.GEAR)
        )))
        val flywheel = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 48, kind = GearWheelKind.FLYWHEEL)
        )))
        gear.spinGear(1, 10f)
        flywheel.spinGear(1, 10f)

        assertTrue(flywheel.rotationalEnergy() > gear.rotationalEnergy())
        assertEquals(GearWheelMaterial.STEEL, flywheel.config.wheels.single().material)
    }

    @Test
    fun `le frottement des paliers dissipe sans inverser la roue`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24)
        )))
        game.spinGear(1, 6f)
        val before = game.rotationalEnergy()
        repeat(240) { game.step(1f / 120f) }

        assertTrue(game.rotationalEnergy() < before)
        assertTrue(game.gears.single().body.omega >= -1e-4f)
    }

    @Test
    fun `le lanceur ne donne jamais plus denergie que les roues nen perdent`() {
        val game = GearMachineGame()
        game.spinGear(1, 8f)
        assertTrue(game.attachLauncher(3))
        val before = game.rotationalEnergy()

        assertTrue(game.launchProjectile())
        val shot = game.projectile!!
        val after = game.rotationalEnergy()
        val actualShotEnergy = 0.5f * shot.body.mass *
            (shot.body.vx * shot.body.vx + shot.body.vy * shot.body.vy)

        assertTrue(after + actualShotEnergy <= before + before * 1e-4f)
        assertEquals(game.lastLaunchEnergy, actualShotEnergy, game.lastLaunchEnergy * 1e-4f)
        assertEquals(GearMachineRules.LAUNCH_EFFICIENCY, game.lastLaunchEfficiency, 1e-4f)
    }

    @Test
    fun `la sauvegarde conserve volant materiau et lanceur`() {
        val config = GearMachineConfig(mutableListOf(
            GearWheelConfig(9, 4f, 5f, 48, 3, GearWheelKind.FLYWHEEL, GearWheelMaterial.TITANIUM)
        ), launcherWheelId = 9, launcherAngleDeg = 55f, projectileMass = 12f)
        val decoded = GearMachineLibrary.decode(
            GearMachineLibrary.encode(listOf(GearMachinePreset("Canon inertiel", config)))
        ).single().config

        assertEquals(GearWheelKind.FLYWHEEL, decoded.wheels.single().kind)
        assertEquals(GearWheelMaterial.TITANIUM, decoded.wheels.single().material)
        assertEquals(9, decoded.launcherWheelId)
        assertEquals(55f, decoded.launcherAngleDeg, 1e-5f)
        assertEquals(12f, decoded.projectileMass, 1e-5f)
    }

    @Test
    fun `deux coups de doigt rapides additionnent leur elan`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24)
        )))
        game.flickGear(1, 10f)
        val afterFirst = game.gears.single().body.omega
        game.flickGear(1, 10f)
        val afterSecond = game.gears.single().body.omega

        assertTrue(afterFirst > 0f)
        assertTrue(afterSecond > afterFirst * 1.9f)
    }

    @Test
    fun `courroie ouverte et croisee choisissent le sens`() {
        fun run(kind: GearLinkKind): Pair<Float, Float> {
            val game = GearMachineGame(GearMachineConfig(mutableListOf(
                GearWheelConfig(1, 0f, 2f, 24),
                GearWheelConfig(2, 8f, 2f, 24)
            )))
            assertTrue(game.addLink(1, 2, kind))
            game.spinGear(1, 8f)
            repeat(30) { game.step(1f / 120f) }
            return game.gears[0].body.omega to game.gears[1].body.omega
        }

        val open = run(GearLinkKind.BELT_OPEN)
        val crossed = run(GearLinkKind.BELT_CROSSED)
        assertTrue(open.first * open.second > 0f)
        assertTrue(crossed.first * crossed.second < 0f)
    }

    @Test
    fun `lembrayage coaxial centre les couches et transmet un choc progressivement`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 12, 0),
            GearWheelConfig(2, 8f, 6f, 96, 1)
        )))
        assertTrue(game.addLink(1, 2, GearLinkKind.SHAFT_CLUTCH))
        assertEquals(0f, game.config.wheels[1].x, 1e-5f)
        assertEquals(2f, game.config.wheels[1].y, 1e-5f)

        game.spinGear(1, 80f)
        val energyBefore = game.rotationalEnergy()
        game.step(1f / 120f)
        val firstFrame = game.gears.first { it.wheel.id == 2 }.body.omega
        val smallAfterShock = game.gears.first { it.wheel.id == 1 }.body.omega
        assertTrue("la grande roue doit commencer à prendre le couple", firstFrame > 0f)
        assertTrue("elle ne doit pas prendre instantanément la vitesse du pignon", firstFrame < smallAfterShock)

        repeat(120) { game.step(1f / 120f) }
        val afterOneSecond = game.gears.first { it.wheel.id == 2 }.body.omega
        assertTrue("la prise doit continuer progressivement", afterOneSecond > firstFrame)
        assertTrue("l'embrayage ne doit pas créer d'énergie", game.rotationalEnergy() <= energyBefore)

        game.moveGear(1, 3f, 4f, snap = false)
        assertEquals(3f, game.config.wheels.first { it.id == 2 }.x, 1e-5f)
        assertEquals(4f, game.config.wheels.first { it.id == 2 }.y, 1e-5f)
    }

    @Test
    fun `la roue libre entraine puis laisse la sortie continuer seule`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24),
            GearWheelConfig(2, 8f, 2f, 24)
        )))
        assertTrue(game.addLink(1, 2, GearLinkKind.CHAIN_FREEWHEEL, 1))
        game.spinGear(1, 10f)
        repeat(30) { game.step(1f / 120f) }
        val driven = game.gears[1].body.omega
        assertTrue(driven > 0f)

        game.spinGear(1, 0f)
        repeat(10) { game.step(1f / 120f) }
        assertTrue(game.gears[1].body.omega > 0f)
        assertTrue(abs(game.gears[0].body.omega) < game.gears[1].body.omega * 0.2f)
    }

    @Test
    fun `la sauvegarde conserve les transmissions et leur sens`() {
        val config = GearMachineConfig(
            mutableListOf(GearWheelConfig(1, 0f, 2f, 24), GearWheelConfig(2, 8f, 2f, 48)),
            links = mutableListOf(GearLinkConfig(1, 2, GearLinkKind.CHAIN_FREEWHEEL, -1))
        )
        val decoded = GearMachineLibrary.decode(
            GearMachineLibrary.encode(listOf(GearMachinePreset("Velo", config)))
        ).single().config

        assertEquals(1, decoded.links.size)
        assertEquals(GearLinkKind.CHAIN_FREEWHEEL, decoded.links.single().kind)
        assertEquals(-1, decoded.links.single().inputDirection)
    }

    @Test
    fun `les sauvegardes G2 restent lisibles apres ajout des transmissions`() {
        val decoded = GearMachineLibrary.decode(
            "G2\tAncienne\tL,1,40.0,4.0\t1,0.0,2.0,24,0,GEAR,STEEL\n"
        ).single().config

        assertEquals(1, decoded.wheels.size)
        assertEquals(1, decoded.launcherWheelId)
        assertTrue(decoded.links.isEmpty())
    }
}
