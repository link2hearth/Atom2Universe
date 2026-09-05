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
import kotlin.math.hypot

class GearMachineTest {

    @Test
    fun `la machine par defaut a ses trois postes en ligne`() {
        val game = GearMachineGame()
        assertEquals(3, game.gears.size)
        assertEquals(2, game.meshes.size)
        val motor = game.config.motorWheels().single()
        val launcher = game.config.launcher()!!
        val idler = game.config.wheels.first { it.id != motor.id && it.id != launcher.id }

        // Le moteur a gauche, le renvoi au milieu, le pas de tir a droite, tous sur la
        // meme ligne d'axes : celle qu'impose le volant pose sur son socle.
        assertTrue("le moteur est a gauche", motor.x < idler.x)
        assertTrue("le lanceur est a droite", idler.x < launcher.x)
        assertEquals(launcher.y, idler.y, 1e-4f)
        assertEquals(launcher.y, motor.y, 1e-4f)

        game.spinGear(motor.id, 8f)
        repeat(120) { game.step(1f / 120f) }
        val driver = game.gears.first { it.wheel.id == motor.id }.body.omega
        val middle = game.gears.first { it.wheel.id == idler.id }.body.omega
        val fired = game.gears.first { it.wheel.id == launcher.id }.body.omega

        assertTrue("le renvoi ne tourne pas en sens inverse", middle * driver < 0f)
        assertTrue("le volant revient dans le sens du moteur", fired * driver > 0f)
        // **Un renvoi ne multiplie rien** : dans un train simple, seules la premiere et
        // la derniere roue comptent. Les deux ont la meme denture, donc le volant
        // tourne exactement a la vitesse du rouet moteur -- et c'est ce que le joueur
        // doit decouvrir pour aller plus loin.
        assertEquals(abs(driver), abs(fired), abs(driver) * 0.05f)
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

        game.setGlobalMaterial(GearWheelMaterial.ALUMINUM)
        assertEquals(initialEnergy, game.rotationalEnergy(setOf(1)), initialEnergy * 1e-5f)
    }

    @Test
    fun `une edition de machine ne supprime pas le projectile en vol`() {
        val game = flywheelGame()
        game.spinGear(1, 8f)
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
    fun `un volant stocke plus quun engrenage de meme diametre`() {
        val gear = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 48, kind = GearWheelKind.GEAR)
        )))
        val flywheel = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 48, kind = GearWheelKind.FLYWHEEL)
        )))
        gear.spinGear(1, 10f)
        flywheel.spinGear(1, 10f)

        assertTrue(flywheel.rotationalEnergy() > gear.rotationalEnergy())
        // Le bois est la matiere par defaut : c'est celle d'une machine qu'on batit
        // avant d'avoir de quoi la forger, et elle rend les moteurs efficaces.
        assertEquals(GearWheelMaterial.WOOD, flywheel.config.wheels.single().material)
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
        val game = flywheelGame()
        game.spinGear(1, 8f)
        val before = game.rotationalEnergy()

        assertTrue(game.launchProjectile())
        val shot = game.projectile!!
        val after = game.rotationalEnergy()
        val actualShotEnergy = 0.5f * shot.body.mass *
            (shot.body.vx * shot.body.vx + shot.body.vy * shot.body.vy)

        assertTrue(after + actualShotEnergy <= before + before * 1e-4f)
        assertEquals(game.lastLaunchEnergy, actualShotEnergy, game.lastLaunchEnergy * 1e-4f)
        // Le rendement se mesure sur ce que les roues ont **réellement** perdu : le
        // reste part en chaleur, et rien n'apparaît de nulle part.
        assertEquals(
            GearMachineRules.LAUNCH_EFFICIENCY, actualShotEnergy / (before - after), 1e-3f
        )
    }

    @Test
    fun `le boulet retombe sur le sol au lieu de le traverser`() {
        val game = flywheelGame()
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())

        // Assez long pour que n'importe quel tir de l'atelier ait touché terre.
        repeat(120 * 60) { game.step(1f / 120f) }

        val shot = game.projectile
        assertTrue("le boulet ne doit pas disparaître après son vol", shot != null)
        assertTrue("le tir doit être relevé comme posé", shot!!.landed)
        assertTrue("la portée doit être positive : ${shot.distance}", shot.distance > 0f)
        assertTrue("le boulet ne doit pas passer sous le sol", shot.body.y > -0.05f)
        assertEquals(shot.distance, game.lastShotDistance, 1e-4f)
        assertTrue(game.lastShotHeight > 0f)
    }

    @Test
    fun `la portee est celle du premier contact et ne bouge plus`() {
        val game = flywheelGame()
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())

        var atLanding = 0f
        repeat(120 * 60) {
            game.step(1f / 120f)
            val shot = game.projectile ?: return@repeat
            if (shot.landed && atLanding == 0f) atLanding = shot.distance
        }

        assertTrue(atLanding > 0f)
        // Le boulet roule et rebondit après avoir touché : la portée, elle, est figée.
        assertEquals(atLanding, game.projectile!!.distance, 1e-5f)
        assertEquals(atLanding, game.lastShotDistance, 1e-5f)
    }

    @Test
    fun `un tir efface le precedent et repart sans resultat`() {
        val game = flywheelGame()
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())
        repeat(120 * 60) { game.step(1f / 120f) }
        val first = game.projectile!!.body

        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())

        assertEquals(0f, game.lastShotDistance, 0f)
        assertFalse(game.lastShotHitTarget)
        assertFalse(game.projectile!!.landed)
        assertTrue("l'ancien boulet doit être retiré", game.projectile!!.body !== first)
        assertTrue(game.world.bodies.none { it === first })
    }

    /**
     * Un atelier d'un seul volant. Il porte son bras de lancement, donc il tire —
     * et [GearMachineConfig.clamp] le designe tout seul comme lanceur.
     */
    private fun flywheelGame(teeth: Int = 48): GearMachineGame = GearMachineGame(
        GearMachineConfig(mutableListOf(
            // En acier, et volontairement : ces essais mesurent la loi du lancer, et
            // un volant de bois -- douze fois plus leger -- n'a pas toujours l'energie
            // de payer la vitesse que sa jante offre. Le plafond serait alors celui de
            // la matiere, pas celui qu'on veut lire.
            GearWheelConfig(
                1, 0f, 4f, teeth, kind = GearWheelKind.FLYWHEEL,
                material = GearWheelMaterial.STEEL
            )
        ))
    )

    /** Tire, puis laisse le temps au tir de se conclure de lui-même. */
    private fun fireAndSettle(game: GearMachineGame, spin: Float = 8f) {
        game.spinGear(1, spin)
        assertTrue(game.launchProjectile())
        repeat(120 * 40) {
            if (game.phase == GearMachineGame.Phase.RESULT) return
            game.step(1f / 120f)
        }
    }

    @Test
    fun `un tir finit tout seul et devient un fantome`() {
        val game = flywheelGame()
        assertEquals(1, game.config.launcherWheelId)
        assertEquals(GearMachineGame.Phase.BUILD, game.phase)

        fireAndSettle(game)

        assertEquals(GearMachineGame.Phase.RESULT, game.phase)
        assertEquals(1, game.shotCount)
        assertEquals(1, game.ghosts.size)
        assertTrue("le fantôme doit contenir la course", game.ghosts[0].size >= 6)
        val shot = game.projectile!!
        assertTrue(shot.landed)
        // Le boulet posé quitte la simulation : il n'a plus rien à y faire.
        assertTrue(game.world.bodies.none { it === shot.body })
    }

    @Test
    fun `le boulet ne roule plus une fois le tir termine`() {
        val game = flywheelGame()
        fireAndSettle(game)
        val restingX = game.projectile!!.body.x

        repeat(120 * 5) { game.step(1f / 120f) }

        assertEquals(restingX, game.projectile!!.body.x, 1e-5f)
    }

    @Test
    fun `terminer un tir en plein vol le range quand meme`() {
        val game = flywheelGame()
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())
        repeat(60) { game.step(1f / 120f) }
        assertEquals(GearMachineGame.Phase.FLIGHT, game.phase)

        game.stopShot()

        assertEquals(GearMachineGame.Phase.RESULT, game.phase)
        assertEquals(1, game.ghosts.size)
        assertFalse("le boulet n'avait pas touché", game.projectile!!.landed)
    }

    @Test
    fun `rebander efface le boulet et garde le fantome`() {
        val game = flywheelGame()
        fireAndSettle(game)

        game.newShot()

        assertEquals(GearMachineGame.Phase.BUILD, game.phase)
        assertEquals(null, game.projectile)
        assertEquals(1, game.ghosts.size)
    }

    @Test
    fun `la memoire des tirs est bornee et le plus recent vient en tete`() {
        val game = flywheelGame()
        game.ghostLimit = 2

        repeat(3) {
            game.newShot()
            game.spinGear(1, 8f)
            assertTrue(game.launchProjectile())
            repeat(60) { game.step(1f / 120f) }
            game.stopShot()
        }

        assertEquals(2, game.ghosts.size)
        assertEquals(3, game.shotCount)
        game.clearGhosts()
        assertTrue(game.ghosts.isEmpty())
    }

    @Test
    fun `le boulet part de la jante et non du moyeu`() {
        val game = flywheelGame()
        val wheel = game.config.wheels.single()
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())

        val shot = game.projectile!!
        val armX = shot.startX - wheel.x
        val armY = shot.startY - wheel.y
        assertEquals(
            "le boulet doit naître dans la gorge",
            wheel.launchRadius, hypot(armX, armY), 1e-3f
        )
        // La gorge est creusée **dans** la jante : le boulet court dedans, il n'est
        // pas tenu au bout d'une perche qui dépasse.
        assertTrue("la gorge doit rester sous la denture", wheel.launchRadius < wheel.outerRadius)
        assertTrue("et loin du moyeu", wheel.launchRadius > wheel.outerRadius * 0.5f)
    }

    @Test
    fun `la gorge est creusee dans la jante et le boulet y tient`() {
        for (teeth in GearMachineRules.SIZES) {
            val wheel = flywheelGame(teeth).config.wheels.single()
            val inner = wheel.launchRadius - wheel.grooveWidth / 2f

            assertTrue("la gorge sort de la denture ($teeth)", wheel.grooveOuterRadius < wheel.outerRadius)
            assertTrue("la gorge mord sur le moyeu ($teeth)", inner > 0f)
            // Le boulet ordinaire doit tenir entre les deux lèvres, sinon celui qu'on
            // dessine en attente déborderait de la piste qui le retient.
            val ball = GearMachineRules.projectileRadius(GearMachineRules.DEFAULT_PROJECTILE_MASS)
            assertTrue("le boulet ne tient pas dans la gorge ($teeth)", wheel.grooveWidth >= ball * 2f)
        }
    }

    @Test
    fun `le boulet en attente est exactement celui qui part`() {
        val game = flywheelGame()
        game.setProjectileMass(40f)
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())

        assertEquals(
            GearMachineRules.projectileRadius(40f),
            game.projectile!!.body.radius,
            1e-6f
        )
        assertEquals(40f, game.projectile!!.body.mass, 1e-4f)
    }

    @Test
    fun `a regime egal tous les boulets sortent a la vitesse de la jante`() {
        fun fire(mass: Float): GearMachineGame {
            val game = flywheelGame()
            game.setProjectileMass(mass)
            game.spinGear(1, 20f)
            assertTrue(game.launchProjectile())
            return game
        }

        val light = fire(4f)
        val heavy = fire(1_200f)

        // La gorge donne sa vitesse, pas son energie : une fronde lance le caillou et
        // le bloc a la meme allure. C'est l'energie emportee qui change.
        assertEquals(light.lastLaunchSpeed, heavy.lastLaunchSpeed, 1e-3f)
        assertTrue(heavy.lastLaunchEnergy > light.lastLaunchEnergy * 100f)
    }

    @Test
    fun `un boulet lourd va plus loin parce que lair le freine moins`() {
        fun range(mass: Float): Float {
            val game = flywheelGame()
            game.setProjectileMass(mass)
            game.spinGear(1, 20f)
            assertTrue(game.launchProjectile())
            repeat(120 * 40) {
                if (game.phase == GearMachineGame.Phase.RESULT) return game.lastShotDistance
                game.step(1f / 120f)
            }
            return game.lastShotDistance
        }

        val light = range(1f)
        val heavy = range(1_200f)

        assertTrue("le caillou doit retomber avant le bloc : $light vs $heavy", heavy > light)
    }

    @Test
    fun `le boulet part sur la tranche et non dans laxe du bras`() {
        val game = flywheelGame()
        val wheel = game.config.wheels.single()
        game.spinGear(1, 8f)
        assertTrue(game.launchProjectile())

        val shot = game.projectile!!
        val armX = shot.startX - wheel.x
        val armY = shot.startY - wheel.y
        val speed = hypot(shot.body.vx, shot.body.vy)
        // Tangentiel : la vitesse est perpendiculaire au bras.
        val alignment = (armX * shot.body.vx + armY * shot.body.vy) /
            (wheel.launchRadius * speed)
        assertEquals("le tir doit être tangent au bras", 0f, alignment, 1e-3f)
    }

    @Test
    fun `la vitesse du boulet est celle de la jante`() {
        val game = flywheelGame()
        val wheel = game.config.wheels.single()
        val omega = 8f
        game.spinGear(1, omega)
        assertTrue(game.launchProjectile())

        val expected = omega * wheel.launchRadius
        assertEquals(expected, game.lastLaunchSpeed, expected * 1e-3f)
        assertEquals(
            expected, hypot(game.projectile!!.body.vx, game.projectile!!.body.vy),
            expected * 1e-3f
        )
    }

    @Test
    fun `tourner deux fois plus vite double la vitesse de sortie`() {
        val slow = flywheelGame()
        slow.spinGear(1, 5f)
        assertTrue(slow.launchProjectile())

        val fast = flywheelGame()
        fast.spinGear(1, 10f)
        assertTrue(fast.launchProjectile())

        assertEquals(2f, fast.lastLaunchSpeed / slow.lastLaunchSpeed, 1e-3f)
    }

    @Test
    fun `un volant plus large tire plus loin a vitesse egale`() {
        val small = flywheelGame(24)
        small.spinGear(1, 8f)
        assertTrue(small.launchProjectile())

        val large = flywheelGame(96)
        large.spinGear(1, 8f)
        assertTrue(large.launchProjectile())

        assertTrue(
            "le bras plus long doit sortir plus vite",
            large.lastLaunchSpeed > small.lastLaunchSpeed
        )
    }

    @Test
    fun `une roue immobile ou dentee ne lance rien`() {
        val still = flywheelGame()
        assertFalse("une jante à l'arrêt ne lance rien", still.launchProjectile())

        // Un engrenage ne porte pas de bras : il ne peut pas être désigné lanceur.
        val geared = GearMachineGame(
            GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 4f, 24)))
        )
        assertFalse(geared.attachLauncher(1))
        assertEquals(null, geared.config.launcherWheelId)
        geared.spinGear(1, 8f)
        assertFalse(geared.launchProjectile())
    }

    @Test
    fun `le sens de rotation decide du cote ou le bras lache`() {
        val game = flywheelGame()
        val wheel = game.config.wheels.single()
        game.setLaunchAngle(1, 0f)

        // À l'arrêt, le volant emprunte le sens du croquis : horaire.
        assertEquals(GearMachineRules.DEFAULT_SPIN, game.launchSpin(1), 0f)
        val clockwiseArm = game.launchPointAngle(wheel)

        game.spinGear(1, 8f)
        assertEquals(1f, game.launchSpin(1), 0f)
        val counterArm = game.launchPointAngle(wheel)

        // Un demi-tour sépare les deux : la roue lâche toujours du côté d'où elle vient.
        val gap = abs(clockwiseArm - counterArm)
        assertEquals(Math.PI.toFloat(), gap, 1e-4f)
    }

    @Test
    fun `la machine de depart a son moteur et son pas de tir`() {
        val game = GearMachineGame()
        val launcher = game.config.launcher()
        assertTrue("il faut un lanceur", launcher != null)
        assertEquals(GearWheelKind.FLYWHEEL, launcher!!.kind)
        // Plante sur son pas de tir, et pose sur son socle : sa hauteur suit sa taille.
        assertEquals(GearMachineRules.LAUNCHER_X, launcher.x, 1e-4f)
        assertEquals(GearMachineRules.launcherY(launcher), launcher.y, 1e-4f)
        assertEquals(1, game.config.wheels.count { it.kind == GearWheelKind.FLYWHEEL })
        assertEquals(1, game.config.motorWheels().size)
    }

    @Test
    fun `une machine relue sans moteur ni volant en recoit`() {
        val game = GearMachineGame()
        game.loadConfig(
            GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 4f, 24)))
        )
        assertEquals(1, game.config.wheels.count { it.kind == GearWheelKind.FLYWHEEL })
        assertEquals(1, game.config.motorWheels().size)
        assertTrue("la roue d'origine reste", game.config.wheels.any { it.id == 1 })
    }

    @Test
    fun `le mannequin est releve meme traverse a grande vitesse`() {
        val target = 60f
        val radius = 0.12f
        // Un bond d'une image entière à trois cents mètres par seconde : le point
        // d'arrivée est loin derrière la cible, mais le trajet passe dedans.
        assertTrue(
            GearMachineRules.segmentHitsTarget(
                target, radius,
                target - 2.5f, GearMachineRules.TARGET_HEIGHT,
                target + 2.5f, GearMachineRules.TARGET_HEIGHT
            )
        )
        // Même trajet, mais un mètre trop haut : rien n'est touché.
        assertFalse(
            GearMachineRules.segmentHitsTarget(
                target, radius,
                target - 2.5f, GearMachineRules.TARGET_HEIGHT + 1f,
                target + 2.5f, GearMachineRules.TARGET_HEIGHT + 1f
            )
        )
        // Un tir qui s'arrête avant la cible ne la touche pas non plus.
        assertFalse(
            GearMachineRules.segmentHitsTarget(
                target, radius,
                target - 8f, GearMachineRules.TARGET_HEIGHT,
                target - 4f, GearMachineRules.TARGET_HEIGHT
            )
        )
    }

    @Test
    fun `le mannequin se plante devant le lanceur`() {
        val game = flywheelGame()
        val launcherX = game.gears.first { it.wheel.id == 1 }.body.x

        assertEquals(launcherX + GearMachineRules.TARGET_DISTANCE, game.targetX(), 1e-4f)
    }

    @Test
    fun `les roues ne touchent jamais le sol`() {
        val game = GearMachineGame()
        assertTrue(game.gears.none { it.body.collidesWith(game.ground) })
        assertTrue(game.gears.none { it.support.collidesWith(game.ground) })
    }

    @Test
    fun `la sauvegarde conserve volant materiau et lanceur`() {
        val config = GearMachineConfig(mutableListOf(
            GearWheelConfig(
                9, 4f, 5f, 48, 3, GearWheelKind.FLYWHEEL, GearWheelMaterial.TITANIUM,
                launchAngle = 55f
            )
        ), launcherWheelId = 9, projectileMass = 12f)
        val decoded = GearMachineLibrary.decode(
            GearMachineLibrary.encode(listOf(GearMachinePreset("Canon inertiel", config)))
        ).single().config

        assertEquals(GearWheelKind.FLYWHEEL, decoded.wheels.single().kind)
        assertEquals(GearWheelMaterial.TITANIUM, decoded.wheels.single().material)
        assertEquals(9, decoded.launcherWheelId)
        assertEquals(55f, decoded.wheels.single().launchAngle, 1e-5f)
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
    fun `la main entraine la roue sans jamais la freiner`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24)
        )))

        // Le doigt tourne : la roue le suit immédiatement, sans attendre le lâcher.
        game.driveGear(1, 12f)
        assertEquals(12f, game.gears.single().body.omega, 1e-4f)

        // Un passage plus lent ne reprend rien de l'élan déjà donné…
        game.driveGear(1, 5f)
        assertEquals(12f, game.gears.single().body.omega, 1e-4f)
        // …et un passage à contresens ne l'inverse pas non plus : la main patine.
        game.driveGear(1, -30f)
        assertEquals(12f, game.gears.single().body.omega, 1e-4f)
        // Mais un passage plus rapide, lui, porte la roue plus haut.
        game.driveGear(1, 20f)
        assertEquals(20f, game.gears.single().body.omega, 1e-4f)

        // La vitesse à la main reste bornée.
        game.driveGear(1, GearMachineRules.MAX_MANUAL_SPEED * 10f)
        assertEquals(GearMachineRules.MAX_MANUAL_SPEED, game.gears.single().body.omega, 1e-3f)
    }

    @Test
    fun `une roue a larret peut etre lancee dans les deux sens`() {
        val game = GearMachineGame(GearMachineConfig(mutableListOf(
            GearWheelConfig(1, 0f, 2f, 24)
        )))

        game.driveGear(1, -9f)
        assertEquals(-9f, game.gears.single().body.omega, 1e-4f)
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
        // Un engrenage ne porte pas de bras : il ne peut plus etre le lanceur.
        assertEquals(null, decoded.launcherWheelId)
        assertTrue(decoded.links.isEmpty())
    }

    @Test
    fun `un ancien angle de tir global migre sur le volant`() {
        val decoded = GearMachineLibrary.decode(
            "G4\tAncienne\tL,1,40.0,4.0\t1,0.0,4.0,48,0,FLYWHEEL,STEEL,0.0\n"
        ).single().config

        assertEquals(1, decoded.launcherWheelId)
        assertEquals(40f, decoded.wheels.single().launchAngle, 1e-5f)
    }
}
