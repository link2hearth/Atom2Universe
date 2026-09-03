package com.Atom2Universe.app.games.trebuchet

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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Le canon a pression, le second lanceur possible de l'atelier.
 *
 * Le test qui compte le plus est celui du plateau : la pression ne doit jamais
 * diverger sous une charge soutenue, elle doit s'approcher d'un plafond que la seule
 * mecanique du train decide -- exactement comme la vitesse d'un volant plafonne a la
 * vitesse libre de son moteur (voir `GearMotorTest`). Le mecanisme est dans
 * `GearMachineGame` : la resistance du reservoir se pose sur le meme moteur d'axe que
 * le frottement sec, et cale la manivelle des que le train n'a plus le couple pour
 * pousser la pression plus haut.
 */
class GearPumpTest {

    /** Un moulin engrene sur un canon plante au pas de tir, meme disposition qu'un volant. */
    private fun windmillCannon(
        motorTeeth: Int = 96,
        cannonTeeth: Int = 96,
        units: Int = 8
    ): GearMachineGame {
        val launcher = GearWheelConfig(
            2, GearMachineRules.LAUNCHER_X, 0f, cannonTeeth,
            kind = GearWheelKind.PUMP, material = GearWheelMaterial.STEEL
        )
        launcher.y = GearMachineRules.launcherY(launcher)
        val motorPitch = motorTeeth * GearMachineRules.MODULE / 2f
        return GearMachineGame(
            GearMachineConfig(
                mutableListOf(
                    GearWheelConfig(
                        1, launcher.x - launcher.pitchRadius - motorPitch, launcher.y,
                        motorTeeth, material = GearWheelMaterial.STEEL,
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

    /** Fait tourner la machine [seconds] secondes de son temps -- au plus [GearMachineRules.MAX_CHARGE] a la fois. */
    private fun run(game: GearMachineGame, seconds: Float) {
        assertTrue("la charge doit demarrer", game.startCharge(seconds))
        var guard = 0
        while (game.charging && guard++ < 10_000) game.advanceCharge(1f / 120f, 2_000)
        assertFalse("la charge doit se terminer", game.charging)
    }

    /** Enchaine [times] charges au plafond de duree, pour depasser [GearMachineRules.MAX_CHARGE]. */
    private fun runLong(game: GearMachineGame, times: Int) {
        repeat(times) { run(game, GearMachineRules.MAX_CHARGE) }
    }

    @Test
    fun `la pression ne diverge jamais, elle plafonne`() {
        val game = windmillCannon()
        run(game, 1_200f)
        val first = game.launcherPressure()
        assertTrue(
            "la pression doit vraiment monter au-dessus de l'air libre : $first Pa",
            first > GearMachineRules.ATMOSPHERIC_PRESSURE * 1.05f
        )
        run(game, 1_200f)
        val second = game.launcherPressure()
        assertTrue("la pression doit se stabiliser : $first puis $second", second < first * 1.05f)
        assertTrue("et rester acquise", second > first * 0.9f)
    }

    @Test
    fun `sans charge le canon ne tire pas`() {
        val game = windmillCannon()
        assertEquals(GearMachineRules.ATMOSPHERIC_PRESSURE, game.launcherPressure(), 1f)
        assertFalse(game.launchProjectile())
    }

    @Test
    fun `un coup de canon vide le reservoir d un coup`() {
        val game = windmillCannon()
        run(game, 1_200f)
        assertTrue(game.launcherPressure() > GearMachineRules.ATMOSPHERIC_PRESSURE)
        assertTrue(game.launchProjectile())
        assertEquals(
            "la vanne s'ouvre en grand : plus rien au-dessus de l'air libre",
            GearMachineRules.ATMOSPHERIC_PRESSURE, game.launcherPressure(), 1f
        )
    }

    @Test
    fun `le canon tire droit dans l axe vise, pas tangentiellement`() {
        val game = windmillCannon()
        game.setLaunchAngle(2, 30f)
        run(game, 1_200f)
        assertTrue(game.launchProjectile())
        val shot = game.projectile!!.body
        val speed = hypot(shot.vx, shot.vy)
        val expectedAngle = Math.toRadians(30.0)
        assertEquals(cos(expectedAngle).toFloat(), shot.vx / speed, 0.01f)
        assertEquals(sin(expectedAngle).toFloat(), shot.vy / speed, 0.01f)
    }

    @Test
    fun `basculer volant canon garde position denture et matiere`() {
        val game = windmillCannon(cannonTeeth = 64)
        val before = game.config.launcher()!!
        assertEquals(GearWheelKind.PUMP, before.kind)
        assertEquals(GearWheelKind.FLYWHEEL, game.cycleLauncherKind(2))
        val flywheel = game.config.launcher()!!
        assertEquals(64, flywheel.teeth)
        assertEquals(GearWheelMaterial.STEEL, flywheel.material)
        assertEquals(GearWheelKind.PUMP, game.cycleLauncherKind(2))
    }

    /**
     * La piece par defaut, quand il en manque une, reste un volant : le canon ne se
     * choisit qu'a la main, en basculant depuis la bulle.
     */
    @Test
    fun `une machine completee par defaut recoit un volant, jamais un canon`() {
        val game = GearMachineGame()
        game.loadConfig(GearMachineConfig(mutableListOf(GearWheelConfig(1, 0f, 6f, 24))))
        assertEquals(GearWheelKind.FLYWHEEL, game.config.launcher()?.kind)
    }

    @Test
    fun `une sauvegarde avec un canon ne recoit pas un second lanceur`() {
        val game = windmillCannon()
        val text = GearMachineLibrary.encode(listOf(GearMachinePreset("canon", game.config)))
        val back = GearMachineLibrary.decode(text).single().config
        assertEquals(GearWheelKind.PUMP, back.launcher()?.kind)
        back.ensureCorePieces()
        assertEquals(1, back.wheels.count { it.kind in GearMachineRules.LAUNCHER_KINDS })
    }

    /**
     * Le second levier de puissance : la denture decide du plafond de pression, le
     * reservoir decide de l'energie qu'on trouve a ce plafond. Un plus gros reservoir
     * ne doit donc pas tirer plus fort a lui seul -- juste plus longtemps, et
     * finalement plus loin une fois plein.
     */
    @Test
    fun `un plus grand reservoir plafonne a la meme pression mais stocke plus d energie`() {
        val small = windmillCannon()
        val big = windmillCannon()
        assertEquals(
            4 * GearMachineRules.DEFAULT_RESERVOIR_VOLUME,
            big.setReservoirVolume(2, 4 * GearMachineRules.DEFAULT_RESERVOIR_VOLUME) ?: 0f, 1e-4f
        )
        // Un reservoir quatre fois plus grand met, en gros, quatre fois plus longtemps
        // a s'approcher du meme plafond -- comme un volant quatre fois plus lourd met
        // plus longtemps a atteindre la meme vitesse limite.
        runLong(small, 3)
        runLong(big, 12)
        val pSmall = small.launcherPressure()
        val pBig = big.launcherPressure()
        assertTrue(
            "le plafond ne doit pas suivre le reservoir : $pSmall contre $pBig",
            pBig < pSmall * 1.25f && pBig > pSmall * 0.75f
        )
        // ... mais un reservoir quatre fois plus grand, plein a la meme pression,
        // rend un coup plus energique -- donc un boulet plus rapide.
        assertTrue(
            "le grand reservoir doit lancer plus vite : ${small.launchSpeedNow()} contre ${big.launchSpeedNow()}",
            big.launchSpeedNow() > small.launchSpeedNow() * 1.2f
        )
    }

    @Test
    fun `le reservoir ne se regle que sur un canon, et reste dans ses bornes`() {
        val game = windmillCannon()
        assertEquals(
            GearMachineRules.MAX_RESERVOIR_VOLUME,
            game.setReservoirVolume(2, 999f) ?: 0f, 1e-4f
        )
        assertEquals(
            GearMachineRules.MIN_RESERVOIR_VOLUME,
            game.setReservoirVolume(2, -5f) ?: 0f, 1e-4f
        )
        // La roue motrice n'a pas de reservoir a regler.
        assertEquals(null, game.setReservoirVolume(1, 1f))
        game.cycleLauncherKind(2)
        assertEquals(null, game.setReservoirVolume(2, 1f))
    }

    @Test
    fun `une sauvegarde conserve la taille du reservoir`() {
        val game = windmillCannon()
        game.setReservoirVolume(2, 0.5f)
        val text = GearMachineLibrary.encode(listOf(GearMachinePreset("canon", game.config)))
        val back = GearMachineLibrary.decode(text).single().config
        assertEquals(0.5f, back.launcher()?.reservoirVolume ?: 0f, 1e-4f)
    }

    @Test
    fun `une machine G6 sans reservoir se relit a la taille par defaut`() {
        val old = "G6\tancienne\tL,2,40.0,4.0,120.0" +
            "\t1,0.0,8.0,96,0,GEAR,STEEL,0.0,35.0" +
            "\t2,8.64,8.0,96,0,PUMP,STEEL,0.0,40.0"
        val back = GearMachineLibrary.decode(old).single().config
        assertEquals(
            GearMachineRules.DEFAULT_RESERVOIR_VOLUME, back.launcher()?.reservoirVolume ?: 0f, 1e-4f
        )
    }
}
