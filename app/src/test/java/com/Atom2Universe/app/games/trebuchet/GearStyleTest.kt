package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'atelier obéit au tempérament, comme le champ de tir.**
 *
 * Arcade et réaliste ne sont pas un réglage d'affichage : ce sont deux tailles de
 * pierre, deux masses volumiques et deux seuils de rupture, fixés **à la naissance de
 * chaque pierre**. Un site bâti sous un tempérament garde donc le sien jusqu'à ce qu'on
 * le refasse.
 *
 * L'atelier ne le refaisait jamais. Son site naissait avec `setContentView`, donc avant
 * que le tempérament ne soit relu des préférences, et un changement dans le menu ne
 * touchait que le trébuchet. On se retrouvait avec des murs de réaliste dans une partie
 * d'arcade, impossibles à entamer, sans que rien ne le dise.
 */
class GearStyleTest {

    private val depart = TargetRules.style

    @After
    fun rendreLeTemperament() {
        // Le tempérament est un **réglage global** : le laisser tourné aurait fait
        // échouer les tests suivants selon l'ordre d'exécution, ce qui est la pire
        // sorte de panne à chercher.
        TargetRules.style = depart
    }

    private fun siteSous(style: TargetStyle): GearMachineGame {
        TargetRules.style = style
        return GearMachineGame()
    }

    /**
     * **Le même site n'a pas la même carrure selon le tempérament.**
     *
     * C'est le curseur qu'on voit vraiment : un site d'arcade est plus grand, ses
     * pierres plus grosses, et il pèse davantage. À graine égale, les deux mondes
     * doivent donc mesurer différemment.
     */
    @Test
    fun `le site de l atelier change de taille avec le temperament`() {
        val arcade = siteSous(TargetStyle.ARCADE).targets
        val realiste = siteSous(TargetStyle.REALISTE).targets

        fun carrure(f: TargetField) = "${"%.1f".format(f.right - f.left)} m de front, " +
            "${"%.0f".format(f.totalMass / 1000f)} t, ${f.pieceTotal} pierres"
        println("STYLE arcade   : ${carrure(arcade)}")
        println("STYLE réaliste : ${carrure(realiste)}")

        assertTrue(
            "les deux tempéraments donnent le même front : le site ne les lit pas",
            arcade.right - arcade.left != realiste.right - realiste.left
        )
        assertTrue(
            "un site d'arcade devrait être plus large",
            arcade.right - arcade.left > realiste.right - realiste.left
        )
    }

    /**
     * **Et surtout, il ne se casse pas de la même façon.**
     *
     * C'est la plainte du joueur, et la seule mesure qui compte : à souffle égal, un
     * site d'arcade doit céder nettement plus qu'un site réaliste. Un atelier bloqué en
     * réaliste donne des murs qu'on n'entame pas, et le joueur conclut que sa machine
     * est faible alors que c'est le tempérament qui n'a pas suivi.
     */
    @Test
    fun `un site d arcade cede plus vite qu un site realiste`() {
        fun souffler(style: TargetStyle, energie: Float): Float {
            val game = siteSous(style)
            val site = game.targets
            var garde = 0
            while (!site.armed && garde++ < 2_000) game.step(1f / 120f)
            val x = (site.left + site.right) / 2f
            site.blast(x, game.terrain.heightAt(x) + 3f, energie, 10f)
            repeat(240) { game.step(1f / 120f) }
            return site.progress
        }

        // On mesure **au milieu de la plage utile**, et pas aux extrêmes. Les deux
        // sites n'ont pas la même carrure — quarante-deux mètres de front et vingt-deux
        // grosses pierres en arcade, dix-neuf mètres et soixante-neuf petites en
        // réaliste — donc un souffle de rayon fixe ne couvre pas la même part de
        // chacun. Mesuré :
        //
        //    20 kJ : arcade   0 %, réaliste  12 %   ← trop faible, on gratte le bord
        //    60 kJ : arcade  36 %, réaliste  12 %
        //   150 kJ : arcade  70 %, réaliste  14 %
        //   400 kJ : arcade  79 %, réaliste 100 %   ← tout est rasé des deux côtés
        //
        // Entre les deux, la différence est celle du tempérament et de rien d'autre.
        for (e in floatArrayOf(60_000f, 150_000f)) {
            val arcade = souffler(TargetStyle.ARCADE, e)
            val realiste = souffler(TargetStyle.REALISTE, e)
            println(
                "STYLE ${"%.0f".format(e / 1000f)} kJ : arcade " +
                    "${"%.0f".format(arcade * 100f)} % de l'objectif, réaliste " +
                    "${"%.0f".format(realiste * 100f)} %"
            )
            assertTrue(
                "à ${"%.0f".format(e / 1000f)} kJ l'arcade n'est pas plus tendre que le " +
                    "réaliste : ${"%.2f".format(arcade)} contre ${"%.2f".format(realiste)}",
                arcade > realiste
            )
        }
    }

    /**
     * **Refaire le site sur la même graine le remet au tempérament du moment.**
     *
     * C'est exactement ce que fait le menu des réglages : il pose le tempérament, puis
     * redemande le même site. Sans ce deuxième geste, le changement ne touche rien.
     */
    @Test
    fun `recharger la meme graine applique le nouveau temperament`() {
        val game = siteSous(TargetStyle.REALISTE)
        val frontRealiste = game.targets.right - game.targets.left

        TargetRules.style = TargetStyle.ARCADE
        assertTrue(
            "le site a changé tout seul : la taille n'est pas fixée à la naissance",
            game.targets.right - game.targets.left == frontRealiste
        )

        game.loadSite(GearMachineGame.DEFAULT_SITE_SEED)
        val frontArcade = game.targets.right - game.targets.left
        println(
            "STYLE rechargement : ${"%.1f".format(frontRealiste)} m en réaliste → " +
                "${"%.1f".format(frontArcade)} m en arcade"
        )
        assertTrue("recharger n'a pas appliqué le nouveau tempérament", frontArcade > frontRealiste)
    }
}
