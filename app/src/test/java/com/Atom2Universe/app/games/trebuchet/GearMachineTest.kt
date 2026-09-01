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
