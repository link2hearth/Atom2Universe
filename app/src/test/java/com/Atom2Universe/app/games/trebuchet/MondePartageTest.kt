package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * **Un monde, un site, deux machines qui s'y succèdent.**
 *
 * Aboutissement des quatre tranches d'unification : les deux jeux ne partagent plus
 * seulement le *code* qui bâtit le monde, ils partagent le monde lui-même. Changer de
 * machine ne change plus de village, et n'efface plus ce qu'on lui a fait.
 *
 * Ce qui rendait ça impossible, et qui a été levé un par un :
 *
 *  - `world.clear()` : remonter une machine vidait le monde entier, donc effaçait
 *    l'autre. Remplacé par [com.Atom2Universe.app.games.physics.PhysWorld.removeOwned],
 *    qui ne retire que ce qui appartient à qui le demande.
 *  - **deux airs** : l'atelier a besoin de 0,015 / 0,0005 là où le trébuchet respire
 *    0,05 / 0,3, et l'amortissement n'existait que sur le monde. Il vit maintenant aussi
 *    sur les corps, chaque machine posant le sien sur les siens.
 *  - le sommeil du moteur : mesuré à 0,032 ms par image, soit 0,4 % d'une image à 120 par
 *    seconde. Ce n'était pas un obstacle, contrairement à ce que son commentaire laissait
 *    croire.
 */
class MondePartageTest {

    /** Le montage de l'activité : le trébuchet possède, l'atelier adopte. */
    private fun deuxMachines(seed: Long): Pair<TrebuchetGame, GearMachineGame> {
        val treb = TrebuchetGame()
        val gear = GearMachineGame(mondeInitial = treb.world, siteInitial = treb.site)
        gear.detach()
        treb.attach()
        treb.loadLevel(seed)
        return treb to gear
    }

    @Test
    fun `les deux machines partagent le meme monde et le meme site`() {
        val (treb, gear) = deuxMachines(7L)
        assertSame("deux mondes", treb.world, gear.world)
        assertSame("deux sites", treb.site, gear.site)
        assertSame("deux champs de cibles", treb.targets, gear.targets)
        assertSame("deux jeux d'effets", treb.effects, gear.effects)
        assertEquals("deux reliefs", treb.terrain.lowest, gear.terrain.lowest, 1e-6f)
    }

    /**
     * **Le village garde ses dégâts quand on change de machine.**
     *
     * C'est la question de départ : on abat un mur au trébuchet, on bascule, et on doit
     * retrouver le village tel qu'on l'a laissé — mêmes pierres, mêmes gravats, aux mêmes
     * endroits.
     */
    @Test
    fun `le village garde ses degats en changeant de machine`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            val (treb, gear) = deuxMachines(7L)
            val site = treb.targets

            // On laboure le village avec un gros boulet lancé à la main : la machine par
            // défaut ne porte pas jusque-là, et ce n'est pas elle qu'on teste.
            treb.release()
            var g = 0
            while (!treb.ballFree && g++ < 5_000) treb.step(1f / 120f)
            treb.ball.x = site.left - 10f
            treb.ball.y = treb.terrain.heightAt(site.left) + 5f
            treb.ball.vx = 160f
            treb.ball.vy = 0f
            site.wake()
            repeat(900) { treb.step(1f / 120f) }

            val cassees = site.pieceBroken
            val avant = site.pieces.map { it.body to floatArrayOf(it.body.x, it.body.y) }
            assertTrue("le boulet n'a rien cassé : le test ne prouve rien", cassees > 0)

            // Le basculement, exactement comme l'activité le fait.
            treb.detach()
            gear.attach()

            assertEquals("des pierres ont disparu", avant.size, site.pieces.size)
            assertEquals("les dégâts ont été oubliés", cassees, site.pieceBroken)
            var pire = 0f
            for ((corps, ou) in avant) {
                pire = maxOf(pire, abs(corps.x - ou[0]) + abs(corps.y - ou[1]))
            }
            println(
                "BASCULE $cassees pierres cassées gardées, ${site.pieces.size} pierres " +
                    "en place, la plus déplacée a bougé de ${"%.3f".format(pire)} m"
            )
            assertEquals("une pierre a bougé au changement de machine", 0f, pire, 1e-4f)

            // Et le monde tourne toujours : l'atelier est bien dedans, le trébuchet non.
            assertTrue("l'atelier n'est pas entré dans le monde", gear.gears.isNotEmpty())
            assertTrue(
                "le trébuchet est resté dans le monde",
                treb.world.bodies.none { it.owner === treb }
            )
            assertTrue(
                "l'atelier n'a pas de corps dans le monde",
                treb.world.bodies.any { it.owner === gear }
            )
        } finally {
            TargetRules.style = precedent
        }
    }

    /**
     * **Chaque machine garde son air.**
     *
     * L'atelier a besoin d'un amortissement angulaire de 0,0005 : à 0,3, celui du
     * trébuchet, un train de manège monté au soixante-quatrième plafonne à trente-cinq
     * fois l'allure de la bête au lieu des soixante-quatre que le rapport promet. Les deux
     * régimes cohabitent maintenant dans un même monde parce qu'ils sont posés sur les
     * corps.
     */
    @Test
    fun `chaque machine garde son air dans le monde commun`() {
        val (treb, gear) = deuxMachines(7L)
        gear.attach()

        // Les corps **mobiles** seulement : le sol appartient aussi à la machine qui l'a
        // posé, mais un corps immobile ne respire pas — l'intégrateur ne l'amortit jamais.
        val roues = treb.world.bodies.filter { it.owner === gear && !it.lockPosition }
        assertTrue("l'atelier n'a aucun corps", roues.isNotEmpty())
        for (b in roues) {
            assertEquals(
                "une roue respire l'air du trébuchet",
                GearMachineGame.AIR_ANGULAIRE, b.angularDamping, 1e-9f
            )
        }
        treb.attach()
        val poutres = treb.world.bodies.filter { it.owner === treb && !it.lockPosition }
        assertTrue("le trébuchet n'a aucun corps", poutres.isNotEmpty())
        for (b in poutres) {
            assertTrue(
                "le trébuchet s'est mis à respirer l'air de l'atelier",
                b.angularDamping < 0f
            )
        }
        println(
            "AIR atelier ${GearMachineGame.AIR_ANGULAIRE} sur ${roues.size} corps, " +
                "trébuchet celui du monde (${treb.world.angularDamping}) sur ${poutres.size}"
        )
    }

    /**
     * **Remonter une machine n'efface pas le site.**
     *
     * C'était `world.clear()` : vider le monde entier puis y remettre le site, ce qui a
     * déjà coûté un bug de pierres ajoutées deux fois. On règle maintenant une poutre
     * entre deux tirs sans que rien d'autre ne bouge.
     */
    @Test
    fun `regler la machine ne touche pas au site`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            val (treb, _) = deuxMachines(7L)
            val site = treb.targets
            val corps = site.pieces.map { it.body }
            val avantMonde = treb.world.bodies.count { it in corps }

            repeat(5) { treb.setBeamLength(10f + it) }

            assertEquals(
                "des pierres ont été ajoutées ou perdues en réglant la poutre",
                avantMonde, treb.world.bodies.count { it in corps }
            )
            assertEquals("le site a changé de taille", corps.size, site.pieces.size)
        } finally {
            TargetRules.style = precedent
        }
    }
}
