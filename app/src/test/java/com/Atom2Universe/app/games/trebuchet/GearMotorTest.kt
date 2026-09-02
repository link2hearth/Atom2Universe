package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearLinkKind
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineLibrary
import com.Atom2Universe.app.games.trebuchet.gears.GearMachinePreset
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineRules
import com.Atom2Universe.app.games.trebuchet.gears.GearMotorConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearMotorKind
import com.Atom2Universe.app.games.trebuchet.gears.GearMotorRules
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelKind
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Les moteurs de l'atelier, et le dimensionnement des roues qui les rend possibles.
 *
 * Le test le plus important du lot est celui de la masse : une roue redevenue disque
 * plein pèserait cinquante fois trop, et **aucun** des trois moteurs ne pourrait plus
 * rien faire tourner. Tout le reste en découle.
 */
class GearMotorTest {

    /**
     * Un moulin engrene sur le volant qui tire.
     *
     * Le volant est **plante** sur son pas de tir : c'est donc autour de lui qu'on
     * place le reste, et jamais l'inverse.
     */
    private fun windmillMachine(
        motorTeeth: Int = 96,
        flywheelTeeth: Int = 96,
        units: Int = 8,
        material: GearWheelMaterial = GearWheelMaterial.STEEL
    ): GearMachineGame {
        val launcher = GearWheelConfig(
            2, GearMachineRules.LAUNCHER_X, 0f, flywheelTeeth,
            kind = GearWheelKind.FLYWHEEL, material = material
        )
        launcher.y = GearMachineRules.launcherY(launcher)
        val motorPitch = motorTeeth * GearMachineRules.MODULE / 2f
        return GearMachineGame(
            GearMachineConfig(
                mutableListOf(
                    GearWheelConfig(
                        1, launcher.x - launcher.pitchRadius - motorPitch, launcher.y,
                        motorTeeth, material = material,
                        motor = GearMotorConfig(
                            GearMotorKind.WINDMILL, units, -1,
                            GearMotorRules.defaultSpan(GearMotorKind.WINDMILL)
                        )
                    ),
                    launcher
                )
            )
        )
    }

    /** Fait tourner la machine [seconds] secondes de son temps. */
    private fun run(game: GearMachineGame, seconds: Float) {
        assertTrue("la charge doit demarrer", game.startCharge(seconds))
        var guard = 0
        while (game.charging && guard++ < 10_000) game.advanceCharge(1f / 120f, 2_000)
        assertFalse("la charge doit se terminer", game.charging)
    }

    /**
     * La machine de depart doit **tirer**. C'est la premiere chose qu'un joueur fait,
     * et une machine livree qui ne lance rien ne s'explique pas toute seule.
     */
    @Test
    fun `la machine de depart tire pour de bon`() {
        val game = GearMachineGame()
        run(game, 120f)
        // Deux minutes de moulin, et la jante file a dix-huit metres par seconde pour
        // une portee d'environ quarante-cinq metres. Le mannequin est a soixante :
        // **la machine livree n'y arrive pas**, et c'est voulu. Le renvoi du milieu ne
        // multiplie rien, et decouvrir qu'il faut un etage compose est tout le jeu.
        assertTrue(
            "portee ${game.estimatedRange()} m a ${game.launchSpeedNow()} m/s",
            game.estimatedRange() > 30f
        )
        assertTrue(
            "elle ne doit pas non plus tout resoudre toute seule",
            game.estimatedRange() < GearMachineRules.TARGET_DISTANCE
        )
        assertTrue(game.launchProjectile())
    }

    @Test
    fun `une roue est une jante et non un disque plein`() {
        val game = GearMachineGame(
            GearMachineConfig(
                mutableListOf(
                    GearWheelConfig(
                        1, 0f, 4f, 48, kind = GearWheelKind.FLYWHEEL,
                        material = GearWheelMaterial.STEEL
                    ),
                    GearWheelConfig(
                        2, 20f, 4f, 48, kind = GearWheelKind.GEAR,
                        material = GearWheelMaterial.STEEL
                    )
                )
            )
        )
        val flywheel = game.gears.first { it.wheel.id == 1 }.body
        val gear = game.gears.first { it.wheel.id == 2 }.body
        // Le disque plein de trente-cinq centimetres pesait cinquante-six tonnes ; la
        // jante en pese une et demie, et c'est ce qui met les moteurs a portee.
        assertTrue(
            "un volant de trois metres pese ${flywheel.mass} kg",
            flywheel.mass in 800f..2_500f
        )
        assertTrue("un engrenage est plus leger encore", gear.mass < flywheel.mass)
        // Le volant garde sa raison d'etre : a diametre egal il emmagasine davantage.
        assertTrue(flywheel.inertia > gear.inertia * 2f)
    }

    @Test
    fun `les trois moteurs occupent trois places distinctes en couple et en vitesse`() {
        // Chacun a son gabarit normal : c'est comme ca qu'on les pose, et c'est donc
        // sur ces trois machines-la qu'ils doivent se distinguer.
        val carousel = motor(GearMotorKind.CAROUSEL)
        val windmill = motor(GearMotorKind.WINDMILL)
        val water = motor(GearMotorKind.WATERWHEEL)

        // Le moulin est le rapide et le faible, la roue a eau la forte et la lente.
        assertTrue(GearMotorRules.freeOmega(windmill) > GearMotorRules.freeOmega(carousel) * 5f)
        assertTrue(GearMotorRules.freeOmega(windmill) > GearMotorRules.freeOmega(water) * 5f)
        assertTrue(GearMotorRules.maxTorque(water) > GearMotorRules.maxTorque(carousel))
        assertTrue(GearMotorRules.maxTorque(carousel) > GearMotorRules.maxTorque(windmill))
        // Quatre betes attelees valent quatre chevaux-vapeur, par definition.
        assertEquals(2_800f, GearMotorRules.power(carousel), 1f)
    }

    /**
     * Le gabarit est **le** levier d'un moteur, et la denture du rouet n'y est pour
     * rien : c'est tout le sens de la machine posee a cote de son engrenage.
     */
    @Test
    fun `la puissance suit le gabarit de la machine et non la denture du rouet`() {
        val small = motor(GearMotorKind.WINDMILL, span = 4f)
        val big = motor(GearMotorKind.WINDMILL, span = 8f)
        // Deux fois l'envergure, quatre fois la surface balayee, quatre fois la puissance.
        assertEquals(4f, GearMotorRules.power(big) / GearMotorRules.power(small), 0.02f)
        // ... et deux fois moins vite : une grande aile tourne lentement.
        assertEquals(
            0.5f,
            GearMotorRules.freeOmega(big) / GearMotorRules.freeOmega(small), 0.01f
        )

        val fine = windmillMachine(motorTeeth = 24)
        val coarse = windmillMachine(motorTeeth = 96)
        assertEquals(
            GearMotorRules.power(fine.config.wheels.first().motor!!),
            GearMotorRules.power(coarse.config.wheels.first().motor!!),
            1f
        )
    }

    private fun motor(
        kind: GearMotorKind,
        units: Int = 4,
        span: Float = GearMotorRules.defaultSpan(kind)
    ) = GearMotorConfig(kind, units, -1, span)

    @Test
    fun `un moulin lance le volant jusqu a une vitesse de jante utile`() {
        val game = windmillMachine()
        // Un quart d'heure : le moulin de cinq metres donne moins de six kilowatts, et
        // le volant d'acier de douze metres de diametre pese pres de trois tonnes.
        run(game, 900f)
        // Rouet et volant ont la meme denture : le volant tourne donc **exactement** a
        // l'allure des ailes. La vitesse de jante vient alors de son seul rayon, ce qui
        // est bien tout ce qu'un train simple sait faire.
        val motorOmega = GearMotorRules.freeOmega(game.config.wheels.first().motor!!)
        val flywheel = game.gears.first { it.wheel.id == 2 }.body
        assertEquals(motorOmega, abs(flywheel.omega), motorOmega * 0.1f)
        assertTrue("la jante file a ${game.rimSpeed()} m/s", game.rimSpeed() > 8f)
        assertTrue("et le tir part", game.launchProjectile())
    }

    /**
     * Le manège marche au pas : c'est le seul moteur qui **oblige** à construire une
     * cascade à plusieurs étages, et c'est tout l'intérêt qu'il a dans l'atelier.
     *
     * Le train monté ici multiplie par soixante-quatre : deux étages `96 → 12`, chacun
     * relayé au suivant par un arbre coaxial, puis le volant sur le dernier arbre.
     */
    @Test
    fun `un manege multiplie par un train compose jusqu au volant`() {
        val big = 96 * GearMachineRules.MODULE / 2f
        val small = 12 * GearMachineRules.MODULE / 2f
        // Le volant est plante ; on remonte la cascade depuis lui, vers la gauche.
        val launcher = GearWheelConfig(
            5, GearMachineRules.LAUNCHER_X, 0f, 48, layer = 2, kind = GearWheelKind.FLYWHEEL
        )
        launcher.y = GearMachineRules.launcherY(launcher)
        val game = GearMachineGame(
            GearMachineConfig(
                mutableListOf(
                    GearWheelConfig(
                        1, launcher.x - 2f * (big + small), launcher.y, 96, layer = 0,
                        motor = GearMotorConfig(
                            GearMotorKind.CAROUSEL, 8, -1,
                            GearMotorRules.defaultSpan(GearMotorKind.CAROUSEL)
                        )
                    ),
                    GearWheelConfig(2, launcher.x - (big + small), launcher.y, 12, layer = 0),
                    GearWheelConfig(3, launcher.x - (big + small), launcher.y, 96, layer = 1),
                    GearWheelConfig(4, launcher.x, launcher.y, 12, layer = 1),
                    launcher
                )
            )
        )
        assertTrue(game.addLink(2, 3, GearLinkKind.SHAFT_CLUTCH))
        assertTrue(game.addLink(4, 5, GearLinkKind.SHAFT_CLUTCH))
        assertEquals(5, game.config.launcherWheelId)

        val motorOmega = GearMotorRules.freeOmega(game.config.wheels.first().motor!!)
        // Vingt minutes de manege : une bete attelee donne sept cent cinquante watts,
        // et remplir un volant ne se fait pas en une minute.
        run(game, 1_200f)
        val flywheel = game.gears.first { it.wheel.id == 5 }.body
        // Soixante-quatre fois l'allure de la bete : c'est le train qui fait tout.
        assertTrue(
            "le volant tourne a ${abs(flywheel.omega)} rad/s pour un pas de $motorOmega",
            abs(flywheel.omega) > motorOmega * 30f
        )
        assertTrue("la jante file a ${game.rimSpeed()} m/s", game.rimSpeed() > 25f)
    }

    @Test
    fun `un moteur ne fait jamais tourner sa roue plus vite que sa vitesse libre`() {
        val game = windmillMachine()
        run(game, 600f)
        val motor = game.gears.first { it.wheel.id == 1 }
        val free = GearMotorRules.freeOmega(motor.wheel.motor!!)
        assertTrue(
            "la roue motrice tourne a ${abs(motor.body.omega)} pour une allure libre de $free",
            abs(motor.body.omega) <= free * 1.02f
        )
    }

    @Test
    fun `une machine laissee tourner finit sur un plateau`() {
        val game = windmillMachine()
        run(game, 1_200f)
        val first = game.rotationalEnergy()
        run(game, 1_200f)
        val second = game.rotationalEnergy()
        assertTrue("l'energie doit se stabiliser : $first puis $second", second < first * 1.05f)
        assertTrue("et rester acquise", second > first * 0.9f)
    }

    @Test
    fun `la charge joue exactement la duree demandee`() {
        val game = windmillMachine()
        assertTrue(game.startCharge(60f))
        assertEquals(60f, game.chargeTotal, 1e-3f)
        var played = 0f
        var guard = 0
        while (game.charging && guard++ < 10_000) played += game.advanceCharge(1f / 120f, 500)
        assertEquals(60f, played, 0.05f)
        assertEquals(0f, game.chargeRemaining, 1e-4f)
    }

    @Test
    fun `charger deux fois de suite vaut charger une fois plus longtemps`() {
        val once = windmillMachine()
        run(once, 120f)
        val twice = windmillMachine()
        run(twice, 60f)
        run(twice, 60f)
        assertEquals(once.rotationalEnergy(), twice.rotationalEnergy(), once.rotationalEnergy() * 0.05f)
    }

    @Test
    fun `sans moteur il n y a rien a charger`() {
        val game = GearMachineGame(
            GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 4f, 24)))
        )
        assertFalse(game.startCharge(60f))
        assertFalse(game.charging)
    }

    @Test
    fun `le frein arrete la roue sans jamais l inverser`() {
        val game = windmillMachine()
        run(game, 120f)
        val flywheel = game.gears.first { it.wheel.id == 2 }
        val sign = if (flywheel.body.omega < 0f) -1f else 1f
        game.setBrake(2)
        repeat(120 * 20) {
            game.step(1f / 120f)
            assertTrue(
                "le frein ne doit jamais retourner la roue",
                flywheel.body.omega * sign >= -1e-3f
            )
        }
        assertTrue("la roue doit s'arreter", abs(flywheel.body.omega) < 0.2f)
    }

    @Test
    fun `un stop vide tout le train d un coup`() {
        val game = windmillMachine()
        run(game, 120f)
        assertTrue(game.rotationalEnergy() > 0f)
        game.stopConnected(2)
        assertEquals(0f, game.rotationalEnergy(), 1e-4f)
    }

    @Test
    fun `un volant ne peut pas etre attele`() {
        val game = GearMachineGame(
            GearMachineConfig(
                mutableListOf(
                    GearWheelConfig(
                        1, 0f, 4f, 48, kind = GearWheelKind.FLYWHEEL,
                        motor = GearMotorConfig(GearMotorKind.CAROUSEL, 4, -1)
                    )
                )
            )
        )
        assertNull("un volant garde l'elan, il ne le produit pas", game.config.wheels.single().motor)
        assertNull("et rien ne l'attelle apres coup", game.cycleMotorKind(1))
    }

    @Test
    fun `le sens de l attelage se prend au bout du meme reglage`() {
        val game = windmillMachine()
        assertEquals(-1, game.config.wheels.first().motor?.direction)
        // Depuis « ↻ 8 » les crans remontent jusqu'a « ↻ 1 », puis le suivant traverse
        // le zero et repart a « ↺ 1 » : huit crans, pas neuf.
        repeat(8) { game.changeMotorUnits(1, +1) }
        val motor = game.config.wheels.first().motor
        assertEquals(1, motor?.direction)
        assertEquals(1, motor?.units)
    }

    /**
     * Une machine motrice deborde largement a gauche de son rouet : si le cadrage ne la
     * voyait pas, un moulin de seize metres sortirait de l'image des qu'on le pose.
     */
    @Test
    fun `le cadrage voit la carcasse de la machine`() {
        val game = windmillMachine()
        val motor = game.config.wheels.first().motor!!
        val gear = game.config.wheels.first()
        val expected = gear.x - GearMotorRules.offsetX(motor, gear.outerRadius) - motor.span
        assertEquals(expected, game.bounds()[0], 0.01f)
    }

    /**
     * Le defaut qui a rendu le premier moulin ridicule : son axe suivait la roue
     * dentee, donc un rouet pose a quatre metres donnait une tour de quatre metres
     * portant des ailes de huit -- deux fois plus longues que le batiment, et balayant
     * quatre metres sous terre.
     */
    @Test
    fun `une machine ne plante jamais sa roue sous terre`() {
        for (kind in GearMotorKind.entries) {
            if (kind == GearMotorKind.NONE) continue
            for (span in floatArrayOf(1f, 3f, 5f, 8f, 12f)) {
                for (gearY in floatArrayOf(-2f, 0f, 0.5f, 4f, 30f)) {
                    val motor = motor(kind, span = span)
                    val hub = GearMotorRules.hubHeight(motor, gearY)
                    assertTrue(
                        "$kind d'envergure $span : bas de roue a ${hub - span} m",
                        hub - span > 0f
                    )
                    // Et la descente reste toujours une descente : un rouet pose haut
                    // fait une machine haute, jamais un arbre qui remonte.
                    assertTrue("$kind : l'axe passe sous le rouet", hub >= gearY)
                }
            }
        }
    }

    @Test
    fun `une tour de moulin est au moins aussi haute que ses ailes sont longues`() {
        val motor = motor(GearMotorKind.WINDMILL, span = 8f)
        val hub = GearMotorRules.hubHeight(motor, 0.5f)
        assertTrue("la tour ne mesure que $hub m pour 8 m d'aile", hub >= 8f)
        // Le cadrage doit voir la machine dressee, pas seulement sa roue dentee.
        val game = windmillMachine()
        val gear = game.config.wheels.first()
        val top = GearMotorRules.hubHeight(gear.motor!!, gear.y) + gear.motor!!.span
        assertTrue("le haut du cadre est a ${game.bounds()[3]} pour $top", game.bounds()[3] >= top)
        assertEquals("le cadre part du sol", 0f, game.bounds()[2], 1e-3f)
    }

    /**
     * Une machine a qui il manque un moteur en recoit un a l'ouverture, et il tourne
     * pour de bon : ce n'est pas une piece decorative posee pour faire nombre.
     */
    @Test
    fun `une machine completee recoit un moulin qui tourne`() {
        val game = GearMachineGame()
        game.loadConfig(GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 6f, 24))))
        val motor = game.config.motorWheels().single()
        assertEquals(GearMotorKind.WINDMILL, motor.motor?.kind)
        run(game, 30f)
        assertTrue(
            "la roue motrice doit tourner",
            abs(game.gears.first { it.wheel.id == motor.id }.body.omega) > 0.05f
        )
        // Et le bouton de la bulle fait bien defiler les trois machines, sans jamais
        // passer par « aucun » : une machine sans moteur ne se rattraperait plus.
        assertEquals(GearMotorKind.WATERWHEEL, game.cycleMotorKind(motor.id))
        assertEquals(GearMotorKind.CAROUSEL, game.cycleMotorKind(motor.id))
        assertEquals(GearMotorKind.WINDMILL, game.cycleMotorKind(motor.id))
    }

    /**
     * Le gabarit et le genre sont **deux** reglages, et changer l'un ne touche pas
     * l'autre : une roue de cage de seize metres existe pour de bon, et repartir de la
     * taille normale a chaque changement effacerait un reglage qu'on venait de trouver.
     */
    @Test
    fun `changer de machine garde son gabarit`() {
        val game = windmillMachine()
        assertEquals(11f, game.setMotorSpan(1, 11f) ?: 0f, 1e-3f)
        for (expected in listOf(
            GearMotorKind.WATERWHEEL, GearMotorKind.CAROUSEL, GearMotorKind.WINDMILL
        )) {
            assertEquals(expected, game.cycleMotorKind(1))
            assertEquals(11f, game.config.wheels.first().motor?.span ?: 0f, 1e-3f)
        }
        // Et le gabarit conserve pese vraiment : une bete au bout d'un bras de onze
        // metres arrache bien plus qu'au bout de trois.
        game.cycleMotorKind(1)
        game.cycleMotorKind(1)
        val carousel = game.config.wheels.first().motor!!
        assertEquals(GearMotorKind.CAROUSEL, carousel.kind)
        assertTrue(
            GearMotorRules.maxTorque(carousel) >
                GearMotorRules.maxTorque(motor(GearMotorKind.CAROUSEL, units = carousel.units))
        )
    }

    /**
     * Les roulettes de la bulle rendent une valeur, pas un sens de rotation : les
     * reglages doivent donc s'ecrire en absolu, sinon chaque chiffre tourne aurait a
     * etre traduit en un nombre de crans.
     */
    @Test
    fun `les reglages s ecrivent en absolu comme les roulettes les donnent`() {
        val game = windmillMachine()
        assertEquals(-3, game.setMotorUnits(1, -3))
        assertEquals(3, game.config.wheels.first().motor?.units)
        assertEquals(-1, game.config.wheels.first().motor?.direction)
        assertEquals(5, game.setMotorUnits(1, 5))
        assertEquals(1, game.config.wheels.first().motor?.direction)
        // Zero n'est pas un attelage : il se rabat sur la plus petite bete attelee.
        assertEquals(1, game.setMotorUnits(1, 0))

        assertEquals(-2, game.setLayer(1, -2))
        assertEquals(-2, game.config.wheels.first().layer)
        // Au-dela des bornes, la valeur se rabat au lieu d'etre refusee.
        assertEquals(GearMachineRules.MAX_LAYER, game.setLayer(1, 999))

        assertEquals(6f, game.setMotorSpan(1, 6f) ?: 0f, 1e-3f)
        assertEquals(GearMotorRules.MAX_SPAN, game.setMotorSpan(1, 99f) ?: 0f, 1e-3f)
        assertEquals(GearMotorRules.MIN_SPAN, game.setMotorSpan(1, 0f) ?: 0f, 1e-3f)

        assertEquals(GearMachineRules.MAX_CHARGE, game.setChargeSeconds(99_999f), 1e-3f)
        assertEquals(GearMachineRules.MIN_CHARGE, game.setChargeSeconds(0f), 1e-3f)
    }

    @Test
    fun `une sauvegarde conserve les moteurs et la duree de charge`() {
        val game = windmillMachine()
        game.setChargeSeconds(300f)
        val text = GearMachineLibrary.encode(listOf(GearMachinePreset("moulin", game.config)))
        val back = GearMachineLibrary.decode(text).single().config
        assertEquals(300f, back.chargeSeconds, 1e-3f)
        val motor = back.wheels.first { it.id == 1 }.motor
        assertEquals(GearMotorKind.WINDMILL, motor?.kind)
        assertEquals(8, motor?.units)
        assertEquals(-1, motor?.direction)
        assertEquals(GearMotorRules.defaultSpan(GearMotorKind.WINDMILL), motor?.span ?: 0f, 1e-3f)
        assertNull(back.wheels.first { it.id == 2 }.motor)
    }

    @Test
    fun `une machine G5 se relit sans moteur et avec la duree par defaut`() {
        val old = "G5\tancienne\tL,2,40.0,4.0\t1,0.0,8.0,96,0,GEAR,STEEL,0.0,35.0" +
            "\t2,8.64,8.0,48,0,FLYWHEEL,STEEL,0.0,40.0"
        val back = GearMachineLibrary.decode(old).single().config
        assertEquals(2, back.wheels.size)
        assertNull(back.wheels.first().motor)
        assertEquals(GearMachineRules.DEFAULT_CHARGE, back.chargeSeconds, 1e-3f)
        assertEquals(40f, back.launcher()?.launchAngle ?: 0f, 1e-3f)
    }
}
