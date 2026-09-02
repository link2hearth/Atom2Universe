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

    /**
     * Lance le vrai boulet du jeu, puis le pose devant le site à la vitesse voulue.
     *
     * Le passage par [GearMachineGame.launchProjectile] n'est pas une coquetterie :
     * c'est **lui** qui déclare le boulet au site, sans quoi le site ne saurait pas qui
     * a cassé quoi et la traversée n'aurait rien à rembourser. Une bille ajoutée au
     * monde à la main n'est pas le projectile du jeu, et le premier essai de ce test
     * mesurait donc consciencieusement une mécanique qui ne tournait pas.
     *
     * Le téléport, lui, remplace une balistique dont on ne veut pas ici : la machine par
     * défaut n'envoie pas à cinq cents mètres, et ce n'est pas ce qu'on mesure.
     */
    private fun boulet(game: GearMachineGame, vitesse: Float): com.Atom2Universe.app.games.physics.PhysBody {
        game.startCharge(30f)
        var garde = 0
        while (game.charging && garde++ < 10_000) game.advanceCharge(1f / 120f, 2_000)
        assertTrue("le lanceur n'a pas tiré", game.launchProjectile())
        val site = game.targets
        val b = game.projectile!!.body
        b.x = site.left - 8f
        b.y = game.terrain.heightAt(site.left) + 3f
        b.vx = vitesse
        b.vy = 0f
        b.omega = 0f
        return b
    }

    /**
     * **En arcade, le boulet traverse ; en réaliste, il rebondit.**
     *
     * C'est l'autre moitié du tempérament, et elle manquait entièrement à l'atelier : la
     * traversée. Sans elle, la physique décide seule, et elle est impitoyable — un
     * boulet de vingt kilos qui percute une pierre de trois tonnes repart en arrière,
     * *même si la pierre se brise*. Exact, et tout ce qu'on ne veut pas voir en arcade.
     *
     * **L'expérience isole le seul curseur qui nous intéresse.** Le site est bâti en
     * arcade dans les deux cas, puis le tempérament bascule : les pierres gardent leur
     * taille et leur solidité — fixées à leur naissance, un autre test le montre — et
     * seule la règle de traversée change au moment du choc. Comparer deux sites
     * différents n'aurait rien prouvé : ils n'ont ni la même carrure ni le même nombre
     * de pierres.
     */
    @Test
    fun `la traversee ne s applique qu en arcade`() {
        // On mesure **la vitesse du boulet à l'image où il vient de casser une pierre**.
        // C'est le seul instant où les deux tempéraments se distinguent, et c'est là que
        // la différence se lit sans ambiguïté : en arcade il repart dans l'axe qu'il
        // avait, en réaliste la physique en fait ce qu'elle veut — c'est-à-dire le
        // renvoyer en arrière.
        //
        // Mesurer la pénétration dans le site ne marchait pas : les deux boulets
        // s'arrêtent au premier mur, et leur position finale ne dit que la force du
        // rebond.
        fun vitesseAuChoc(auChoc: TargetStyle): Float {
            // Le site, toujours bâti en arcade : c'est la traversée qu'on mesure, pas la
            // solidité des murs.
            val game = siteSous(TargetStyle.ARCADE)
            val site = game.targets
            var garde = 0
            while (!site.armed && garde++ < 2_000) game.step(1f / 120f)

            TargetRules.style = auChoc
            val b = boulet(game, vitesse = 140f)
            repeat(600) {
                game.step(1f / 120f)
                // La traversée a déjà joué quand `step` rend la main : elle passe après
                // les dégâts, dans la même image.
                if (site.pierceCost(b) > 0f) return b.vx
            }
            return Float.NaN
        }

        val arcade = vitesseAuChoc(TargetStyle.ARCADE)
        val realiste = vitesseAuChoc(TargetStyle.REALISTE)
        println(
            "TRAVERSÉE à l'image du bris : arcade ${"%.1f".format(arcade)} m/s, " +
                "réaliste ${"%.1f".format(realiste)} m/s"
        )
        assertTrue("aucune pierre cassée en arcade : rien à mesurer", !arcade.isNaN())
        assertTrue("aucune pierre cassée en réaliste : rien à mesurer", !realiste.isNaN())
        assertTrue(
            "le boulet d'arcade ne repart pas vers l'avant : ${"%.1f".format(arcade)} m/s",
            arcade > 0f
        )
        assertTrue(
            "l'arcade ne garde pas plus d'élan que le réaliste : " +
                "${"%.1f".format(arcade)} contre ${"%.1f".format(realiste)} m/s",
            arcade > realiste
        )
    }

    /**
     * **La traversée ne crée jamais d'énergie**, et c'est la règle d'or de ce moteur.
     *
     * Le boulet ne récupère que ce qu'il n'a pas dépensé : sa vitesse rendue correspond
     * à l'énergie d'avant le choc **moins** les points de vie qu'il vient d'emporter. Un
     * remboursement plus généreux ferait un boulet qui accélère en cassant des murs.
     */
    @Test
    fun `la traversee ne rend jamais plus que l elan d avant le choc`() {
        val game = siteSous(TargetStyle.ARCADE)
        val site = game.targets
        var garde = 0
        while (!site.armed && garde++ < 2_000) game.step(1f / 120f)

        val vitesse = 140f
        val bille = boulet(game, vitesse)
        val masse = bille.mass

        val depart0 = 0.5f * masse * vitesse * vitesse
        var pire = 0f
        repeat(600) {
            game.step(1f / 120f)
            val e = 0.5f * masse * (bille.vx * bille.vx + bille.vy * bille.vy)
            if (e > pire) pire = e
        }
        println(
            "TRAVERSÉE énergie : départ ${"%.0f".format(depart0 / 1000f)} kJ, " +
                "maximum atteint ${"%.0f".format(pire / 1000f)} kJ"
        )
        // Une marge de quelques pour cent : le boulet tombe aussi, et la gravité lui
        // ajoute honnêtement de l'énergie pendant sa course.
        assertTrue(
            "la traversée a créé de l'énergie : ${"%.0f".format(pire / 1000f)} kJ pour " +
                "${"%.0f".format(depart0 / 1000f)} kJ au départ",
            pire <= depart0 * 1.10f
        )
    }

    /**
     * **Toucher un mur en l'air n'est pas atterrir.**
     *
     * Le relevé de portée compte trois façons de constater le toucher, dont « le moteur
     * vient de compter un choc » — pour les boulets rapides, qui frappent la terre et
     * repartent en l'air dans la même image. C'était sans conséquence tant que l'atelier
     * tirait sur une dalle nue.
     *
     * Depuis qu'il y a des bâtiments, ce troisième relevé prenait **le premier mur** pour
     * le sol : la portée se lisait au pied du rempart, et le tir se terminait là — alors
     * qu'en arcade le boulet est justement censé le traverser et retomber plus loin.
     */
    @Test
    fun `toucher un mur en l air ne compte pas comme un atterrissage`() {
        val game = siteSous(TargetStyle.ARCADE)
        val site = game.targets
        var garde = 0
        while (!site.armed && garde++ < 2_000) game.step(1f / 120f)

        // Bien au-dessus du sol, en plein dans la silhouette du village.
        val sol = game.terrain.heightAt(site.left)
        val b = boulet(game, vitesse = 140f)
        b.y = sol + 9f
        val hauteur = b.y

        var pose = false
        repeat(30) {
            game.step(1f / 120f)
            if (game.projectile?.landed == true) pose = true
        }
        println(
            "PORTÉE choc à ${"%.1f".format(hauteur - sol)} m du sol : " +
                "compté comme atterrissage = $pose"
        )
        assertTrue(
            "un mur touché à neuf mètres du sol a été pris pour le sol",
            !pose
        )
    }
}
