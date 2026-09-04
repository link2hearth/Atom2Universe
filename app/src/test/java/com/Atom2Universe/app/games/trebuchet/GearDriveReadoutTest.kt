package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
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
import kotlin.math.abs

/**
 * Le tableau de bord du train : ce que la machine promet avant qu'on la charge.
 *
 * Ces relevés existent à cause d'une question qui n'avait pas de réponse lisible à
 * l'écran : **pourquoi agrandir le moulin affaiblit-il le tir ?** La réponse est dans
 * la loi du rapport de vitesse périphérique — une aile plus longue tourne plus
 * lentement — et le tir ne lit que la vitesse de jante. Les tests ci-dessous fixent
 * les deux moitiés de ce troc pour qu'aucune ne bouge sans l'autre.
 */
class GearDriveReadoutTest {

    /**
     * Un moulin engrené sur le volant qui tire, avec la denture du rouet au choix.
     *
     * Le volant est planté sur son pas de tir : c'est autour de lui qu'on place le
     * moulin, jamais l'inverse.
     */
    private fun machine(
        motorTeeth: Int = 96,
        span: Float = GearMotorRules.defaultSpan(GearMotorKind.WINDMILL)
    ): GearMachineGame {
        val launcher = GearWheelConfig(
            2, GearMachineRules.LAUNCHER_X, 0f, GearMachineRules.FLYWHEEL_TEETH,
            kind = GearWheelKind.FLYWHEEL, material = GearWheelMaterial.STEEL
        )
        launcher.y = GearMachineRules.launcherY(launcher)
        val motorPitch = motorTeeth * GearMachineRules.MODULE / 2f
        return GearMachineGame(
            GearMachineConfig(
                mutableListOf(
                    GearWheelConfig(
                        1, launcher.x - launcher.pitchRadius - motorPitch, launcher.y,
                        motorTeeth, material = GearWheelMaterial.STEEL,
                        motor = GearMotorConfig(GearMotorKind.WINDMILL, 8, -1, span)
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
        while (game.charging && guard++ < 20_000) game.advanceCharge(1f / 120f, 2_000)
        assertFalse("la charge doit se terminer", game.charging)
    }

    /**
     * Le rapport annoncé est celui des dentures, et le plafond en découle.
     *
     * Un rouet de 96 dents sur un volant de 96 ne multiplie rien : le lanceur tourne
     * exactement à la vitesse libre du moulin. C'est le cas de référence, celui qui dit
     * que le calcul lit bien le train et pas autre chose.
     */
    @Test
    fun `un rouet de meme denture ne multiplie rien`() {
        val game = machine(motorTeeth = GearMachineRules.FLYWHEEL_TEETH)
        val drive = game.driveReadout()
        assertTrue("le moulin doit mener le volant", drive.driven)
        assertEquals(GearMotorKind.WINDMILL, drive.motorKind)
        assertEquals(1f, drive.ratio, 1e-3f)
        assertEquals(drive.motorFreeOmega, drive.launcherFreeOmega, 1e-3f)
        assertEquals(2, drive.trainSize)
    }

    /**
     * **Un gros rouet multiplie, un petit divise.** C'est la seule pièce du jeu qui
     * rattrape le couple gagné en agrandissant un moteur, donc c'est celle qui doit
     * être lisible sur le panneau.
     */
    @Test
    fun `la denture du rouet donne la multiplication`() {
        val gros = machine(motorTeeth = 192).driveReadout()
        assertEquals(192f / GearMachineRules.FLYWHEEL_TEETH, gros.ratio, 1e-3f)
        assertTrue(
            "un gros rouet doit faire tourner le volant plus vite que le moulin",
            gros.launcherFreeOmega > gros.motorFreeOmega * 1.5f
        )

        val petit = machine(motorTeeth = 24).driveReadout()
        assertEquals(24f / GearMachineRules.FLYWHEEL_TEETH, petit.ratio, 1e-3f)
        assertTrue(
            "un petit rouet doit au contraire ralentir le volant",
            petit.launcherFreeOmega < petit.motorFreeOmega
        )
    }

    /**
     * **Le troc de l'envergure, fixé noir sur blanc.**
     *
     * Agrandir un moulin multiplie sa puissance et son couple, et **baisse** son
     * régime — donc, à train inchangé, sa portée. Ce n'est pas une panne, c'est la loi
     * du rapport de vitesse périphérique ; mais c'est parfaitement contre-intuitif, et
     * c'est exactement ce que le panneau doit rendre visible. Si un jour quelqu'un
     * inverse ce sens, il devra le faire en connaissance de cause : ce test tombera.
     */
    @Test
    fun `agrandir le moulin monte le couple et baisse le regime`() {
        val petit = machine(span = 2f).driveReadout()
        val grand = machine(span = 10f).driveReadout()

        assertTrue(
            "la puissance doit suivre le carre de l'envergure",
            grand.motorPower > petit.motorPower * 20f
        )
        assertTrue(
            "le couple doit suivre le cube de l'envergure",
            grand.motorTorque > petit.motorTorque * 100f
        )
        assertTrue(
            "mais le regime doit baisser : c'est le rapport de vitesse peripherique",
            grand.launcherFreeOmega < petit.launcherFreeOmega
        )
        assertEquals(
            "et il baisse exactement comme l'inverse de l'envergure",
            5f, petit.launcherFreeOmega / grand.launcherFreeOmega, 0.05f
        )
    }

    /**
     * La portée au plafond suit le régime, pas la puissance.
     *
     * C'est le chiffre que le panneau affiche sous « Portée max », et la réponse
     * chiffrée à « est-ce que ma grosse machine tire plus loin ? ». Non — pas tant que
     * le train est le même.
     */
    @Test
    fun `la portee au plafond baisse quand le moulin grossit`() {
        val petit = machine(span = 2f)
        val grand = machine(span = 10f)
        assertTrue(
            "un petit moulin doit avoir une jante plus rapide au plafond",
            petit.ceilingRimSpeed() > grand.ceilingRimSpeed()
        )
        assertTrue(
            "portees ${petit.ceilingRange()} m contre ${grand.ceilingRange()} m",
            petit.ceilingRange() > grand.ceilingRange()
        )
    }

    /**
     * Le plafond annoncé est **celui que la machine atteint vraiment**.
     *
     * Sans ce test, le panneau pourrait promettre un régime que rien n'atteint : c'est
     * le genre de chiffre qui trompe plus qu'il n'aide. On charge longtemps — le
     * plafond est une limite asymptotique — et on vérifie qu'on s'en approche par en
     * dessous sans jamais le dépasser.
     */
    @Test
    fun `le plafond annonce est bien celui qu'on atteint`() {
        val game = machine(motorTeeth = GearMachineRules.FLYWHEEL_TEETH)
        val ceiling = game.driveReadout().launcherFreeOmega
        run(game, 1_200f)
        val reached = abs(game.gears.first { it.wheel.id == 2 }.body.omega)
        assertTrue(
            "regime atteint $reached rad/s pour un plafond annonce $ceiling",
            reached > ceiling * 0.9f
        )
        assertTrue(
            "et il ne doit jamais le depasser : un moteur pousse, il n'entraine pas au-dela",
            reached <= ceiling * 1.02f
        )
    }

    /**
     * Sans moteur, le panneau ne doit rien promettre.
     *
     * Un zéro se lirait comme une machine en panne alors qu'il n'y a simplement rien
     * d'attelé ; la vue affiche un tiret, et c'est [GearMachineGame.DriveReadout.driven]
     * qui le lui dit.
     */
    @Test
    fun `un train sans moteur ne promet rien`() {
        val game = machine()
        game.config.wheels.first { it.id == 1 }.motor = null
        game.rebuild()
        val drive = game.driveReadout()
        assertFalse(drive.driven)
        assertEquals(0f, drive.launcherFreeOmega, 0f)
        assertEquals(0f, game.ceilingRimSpeed(), 0f)
        assertEquals(0f, game.ceilingRange(), 0f)
    }
}
