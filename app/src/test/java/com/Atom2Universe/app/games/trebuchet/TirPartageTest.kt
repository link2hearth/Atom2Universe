package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * **Les deux jeux racontent leurs tirs de la même façon.**
 *
 * Le dossier `trebuchet` contient deux jeux — le trébuchet et l'atelier d'engrenages —
 * et le cycle du tir n'appartient à aucun des deux : une trajectoire, une traversée, un
 * fantôme, ça se raconte pareil qu'on ait lancé le boulet avec un contrepoids ou avec de
 * l'air comprimé. Ça n'a pourtant pas empêché les deux copies de diverger.
 *
 * Ce fichier ne teste pas *un* comportement : il teste que les **deux modes** ont le
 * même. C'est la seule forme de test qui attrape ce genre de défaut, parce qu'un test
 * écrit sur un seul mode est vert des deux côtés de la divergence.
 *
 * Mesuré le 04/09/2026, avant l'unification par [ShotTrail] et [ShotPierce] :
 *
 * ```
 *                      après clearGhosts()        fin du fantôme <-> le boulet
 * trébuchet    0 fantôme, 858 valeurs de trace     0,25 m   (tir coupé à 30 m/s)
 * atelier      0 fantôme,   0 valeur               0,00 m
 * ```
 */
class TirPartageTest {

    /** Charge l'atelier à fond et tire, comme la vue le fait image par image. */
    private fun atelierArme(): GearMachineGame {
        val game = GearMachineGame()
        game.startCharge(12f)
        var garde = 0
        while (game.charging && garde++ < 100_000) game.advanceCharge(1f / 240f, 2_000)
        assertTrue("l'atelier n'a pas tiré", game.launchProjectile())
        return game
    }

    /**
     * **« Effacer les tirs » efface aussi la trace vivante, dans les deux modes.**
     *
     * La vue dessine la trace du tir en cours **à chaque image**, en plus des fantômes.
     * Le trébuchet n'effaçait que la liste des fantômes : le bouton laissait donc la
     * dernière trajectoire à l'écran — quatre cent trente points — au-dessus de zéro
     * fantôme. L'atelier, lui, nettoyait tout. Même bouton, deux comportements.
     */
    @Test
    fun `effacer les tirs vide la trace dans les deux modes`() {
        val treb = TrebuchetGame()
        treb.simulateShot()
        assertTrue("le trébuchet n'a rien tracé", treb.trailCount > 0)
        treb.clearGhosts()
        assertEquals("le trébuchet garde des fantômes", 0, treb.ghosts.size)
        assertEquals(
            "le trébuchet laisse ${treb.trailCount / 2} points de trace à l'écran",
            0, treb.trailCount
        )

        val gear = atelierArme()
        var garde = 0
        while (gear.phase == GearMachineGame.Phase.FLIGHT && garde++ < 40_000) {
            gear.step(1f / 120f)
        }
        assertTrue("l'atelier n'a rien tracé", gear.trailCount > 0)
        gear.clearGhosts()
        assertEquals("l'atelier garde des fantômes", 0, gear.ghosts.size)
        assertEquals(
            "l'atelier laisse ${gear.trailCount / 2} points de trace à l'écran",
            0, gear.trailCount
        )
    }

    /**
     * **Un tir coupé en plein vol laisse un fantôme qui va jusqu'au boulet.**
     *
     * La trace n'échantillonne qu'un point tous les [ShotTrail.INTERVAL] secondes de vol.
     * Sans un dernier point posé à l'archivage, le fantôme s'arrête donc jusqu'à un
     * intervalle **avant** le boulet : 0,25 m à trente mètres par seconde, plus d'un
     * mètre sur une machine rapide. L'atelier posait ce point, le trébuchet non.
     *
     * On coupe le tir en plein vol — c'est le bouton du joueur, et c'est le cas où le
     * boulet va le plus vite au moment de l'archivage.
     */
    @Test
    fun `le fantome va jusqu au boulet dans les deux modes`() {
        val treb = TrebuchetGame()
        treb.release()
        repeat(400) { treb.step(1f / 120f) }
        val vT = hypot(treb.ball.vx, treb.ball.vy)
        val bxT = treb.ball.x
        val byT = treb.ball.y
        treb.stopShot()
        val fT = treb.ghosts.first()
        val ecartT = hypot(fT[fT.size - 2] - bxT, fT[fT.size - 1] - byT)
        println(
            "TRÉBUCHET tir coupé à ${"%.0f".format(vT)} m/s : fantôme arrêté à " +
                "${"%.2f".format(ecartT)} m du boulet"
        )
        assertTrue(
            "le fantôme du trébuchet s'arrête à ${"%.2f".format(ecartT)} m du boulet",
            ecartT < 0.01f
        )

        val gear = atelierArme()
        repeat(200) { gear.step(1f / 120f) }
        val corps = gear.projectile!!.body
        val vG = hypot(corps.vx, corps.vy)
        val bxG = corps.x
        val byG = corps.y
        gear.stopShot()
        val fG = gear.ghosts.first()
        val ecartG = hypot(fG[fG.size - 2] - bxG, fG[fG.size - 1] - byG)
        println(
            "ATELIER   tir coupé à ${"%.0f".format(vG)} m/s : fantôme arrêté à " +
                "${"%.2f".format(ecartG)} m du boulet"
        )
        assertTrue(
            "le fantôme de l'atelier s'arrête à ${"%.2f".format(ecartG)} m du boulet",
            ecartG < 0.01f
        )
    }

    /**
     * **Le vent de la graine arrive dans les deux mondes.**
     *
     * [TargetGenerator] tire un vent pour chaque niveau — jusqu'à 8,5 m/s — et il se pose
     * à deux endroits : la traînée du monde, qui décide de la trajectoire, et les effets,
     * qui emportent la fumée et couchent les feux d'artifice. Le trébuchet le faisait
     * depuis toujours ; l'atelier **jetait purement et simplement** celui de sa propre
     * graine. Mesuré le 04/09/2026, mêmes graines des deux côtés :
     *
     * ```
     * graine 3 : vent -2,15 m/s -> trébuchet -2,15, atelier 0,00
     * graine 6 : vent  8,49 m/s -> trébuchet  8,49, atelier 0,00
     * ```
     *
     * Sa vue en était réduite à inventer une brise sinusoïdale pour faire bouger les
     * arbres, pendant que le vrai vent dormait dans un objet que personne ne lisait.
     */
    @Test
    fun `le vent de la graine arrive dans les deux mondes`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            var souffles = 0
            for (seed in 1L..12L) {
                val lvl = TargetGenerator.generate(seed)
                if (abs(lvl.wind.vx) < 0.5f) continue
                souffles++

                val treb = TrebuchetGame()
                treb.applyLevel(lvl)
                val gear = GearMachineGame()
                gear.loadSite(seed)

                assertEquals(
                    "graine $seed : le trébuchet n'a pas posé le vent de sa graine",
                    lvl.wind.vx, treb.world.windX, 1e-4f
                )
                assertEquals(
                    "graine $seed : l'atelier jette le vent de sa graine " +
                        "(${"%.2f".format(lvl.wind.vx)} m/s perdus)",
                    lvl.wind.vx, gear.world.windX, 1e-4f
                )
                assertEquals(
                    "graine $seed : l'atelier ne souffle pas sur ses effets",
                    lvl.wind.vx, gear.wind.vx, 1e-4f
                )
            }
            println("VENT : $souffles graines sur 12 soufflent à plus de 0,5 m/s")
            assertTrue("aucune graine ventée dans l'échantillon", souffles > 0)
        } finally {
            TargetRules.style = precedent
        }
    }

    /**
     * **Changer de site remet les compteurs à zéro, dans les deux modes.**
     *
     * Le compteur de tirs de l'atelier traversait les sites : on arrivait sur un village
     * neuf avec les tirs du précédent au compteur. Mesuré : trébuchet 1 → 0, atelier
     * 1 → 1.
     */
    @Test
    fun `changer de site remet les compteurs a zero`() {
        val treb = TrebuchetGame()
        treb.loadLevel(1L)
        treb.simulateShot()
        assertTrue("le trébuchet n'a pas compté son tir", treb.shotCount > 0)
        treb.loadLevel(2L)
        assertEquals("le trébuchet garde les tirs du niveau précédent", 0, treb.shotCount)

        val gear = atelierArme()
        var garde = 0
        while (gear.phase == GearMachineGame.Phase.FLIGHT && garde++ < 40_000) {
            gear.step(1f / 120f)
        }
        assertTrue("l'atelier n'a pas compté son tir", gear.shotCount > 0)
        gear.loadSite(2L)
        assertEquals("l'atelier garde les tirs du site précédent", 0, gear.shotCount)
    }

    /**
     * **La pile des fantômes se comporte pareil des deux côtés.**
     *
     * Elle empile le plus récent en tête, elle se taille sur-le-champ quand le joueur
     * baisse la limite dans le menu, et la limite est bornée aux choix que le menu
     * propose. Trois règles, deux implémentations autrefois — c'est exactement le genre
     * de chose qui part à la dérive sans que personne ne s'en aperçoive.
     */
    @Test
    fun `la pile des fantomes obeit aux memes regles`() {
        val trail = ShotTrail()
        for (i in 1..4) {
            trail.begin(i.toFloat(), 0f)
            trail.archive()
        }
        assertEquals("le plus récent n'est pas en tête", 4f, trail.ghosts.first()[0], 1e-4f)
        assertEquals(4, trail.ghosts.size)

        val avant = trail.stamp
        trail.limit = 2
        assertEquals("baisser la limite n'a pas taillé la pile", 2, trail.ghosts.size)
        assertEquals("le plus récent a été jeté", 4f, trail.ghosts.first()[0], 1e-4f)
        assertTrue("la vue n'a pas été prévenue", trail.stamp > avant)

        trail.limit = 0
        assertEquals("une limite nulle laisserait un menu sans effet", 1, trail.limit)
        trail.limit = 9_999
        assertEquals(
            "la limite dépasse les choix du menu",
            TrebuchetRules.GHOST_CHOICES.last(), trail.limit
        )
    }

    /**
     * **La trace ne déborde pas, et son plafond est le même pour les deux.**
     *
     * Trois mille points, soit une minute de vol. Au-delà on cesse d'écrire plutôt que de
     * laisser grossir un tableau qu'on recopie en entier à chaque fantôme.
     */
    @Test
    fun `la trace s arrete a son plafond`() {
        val trail = ShotTrail()
        trail.begin()
        // Deux fois le plafond, à un point par appel.
        repeat(2 * ShotTrail.MAX_FLOATS) { trail.sample(ShotTrail.INTERVAL * 2f, 1f, 2f) }
        assertTrue(
            "la trace a dépassé son plafond : ${trail.count} valeurs",
            trail.count <= ShotTrail.MAX_FLOATS + 2
        )
    }
}
