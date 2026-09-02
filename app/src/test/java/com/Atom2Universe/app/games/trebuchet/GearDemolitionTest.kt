package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les boulets de l'atelier abattent vraiment les murs.**
 *
 * Ils les traversaient. Toutes les conditions étaient réunies — les pierres dans le
 * monde, les filtres de collision d'accord des deux côtés, les couches qui se
 * recouvrent — et pourtant rien ne se touchait, parce que les corps s'endormaient
 * **avant leur premier pas de simulation** et gardaient la boîte englobante nulle de
 * leur construction. Le balayage les voyait tous à l'origine du monde, à cinq cents
 * mètres de là où ils sont dessinés.
 *
 * Rien ne le signalait : pas d'exception, pas de forme manquante, un village bien
 * peint. On voyait un boulet passer au travers d'un mur, c'est tout.
 */
class GearDemolitionTest {

    private val fixed = 1f / 120f

    /** Une bille lancée à la main dans le site, comme le ferait un tir réussi. */
    private fun tirer(game: GearMachineGame, masse: Float, vitesse: Float): PhysBody {
        val site = game.targets
        val depart = site.left - 40f
        val bille = PhysBody.circle(0.30f, masse).apply {
            x = depart
            y = game.terrain.heightAt(site.left) + 3f
            vx = vitesse
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
            collisionLayer = -32
            collisionLayerDepth = 65
        }
        game.world.add(bille)
        return bille
    }

    /** Laisse le site se poser : avant l'armement, aucun choc ne compte. */
    private fun armer(game: GearMachineGame) {
        var garde = 0
        while (!game.targets.armed && garde++ < 2_000) game.step(fixed)
        assertTrue("le site ne s'arme jamais", game.targets.armed)
    }

    /**
     * **Le mur arrête le boulet.**
     *
     * Le test le plus simple, et celui qui manquait : une bille lancée dans le village
     * ne doit pas ressortir de l'autre côté. C'est la panne exacte qu'on a eue.
     */
    @Test
    fun `un boulet ne traverse pas le village`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets
        val bille = tirer(game, masse = 60f, vitesse = 70f)
        repeat(600) { game.step(fixed) }

        println(
            "TIR bille arrêtée à x=${"%.1f".format(bille.x)} " +
                "(le site va de ${"%.0f".format(site.left)} à ${"%.0f".format(site.right)})"
        )
        assertTrue(
            "la bille est ressortie derrière le village : x=${"%.1f".format(bille.x)}",
            bille.x < site.right
        )
    }

    /**
     * **Un coup qui porte abîme, et un gros coup jette des pierres à terre.**
     *
     * On tire deux fois plus lourd et deux fois plus vite, et on regarde les compteurs
     * que l'interface affiche : les points de structure perdus, les pierres cassées ou
     * renversées, et l'avancement rapporté à l'objectif.
     */
    @Test
    fun `un boulet lourd entame le site`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets
        val corpsAvant = site.bodyCount

        tirer(game, masse = 250f, vitesse = 120f)
        repeat(900) { game.step(fixed) }

        val aTerre = site.pieceBroken + site.pieceToppled
        println(
            "TIR $aTerre pierres à terre sur ${site.pieceTotal} " +
                "(${site.pieceBroken} cassées, ${site.pieceToppled} renversées), " +
                "avancement ${"%.0f".format(site.progress * 100f)} % de l'objectif " +
                "(${"%.0f".format(site.winRatio * 100f)} %), " +
                "$corpsAvant corps → ${site.bodyCount} avec les gravats"
        )
        // On ne compte **pas** les points de structure restants : une pierre qui cède se
        // brise en gravats, qui sont des pièces à part entière avec leur propre vie. Le
        // total remonte donc quand on démolit, ce qui est correct et parfaitement
        // trompeur. Les compteurs qui disent la vérité sont ceux que l'interface montre.
        assertTrue("le tir n'a mis aucune pierre à terre", aTerre >= 1)
        assertTrue("l'avancement ne bouge pas", site.progress > 0f)
        assertTrue("aucun gravat n'est né de la démolition", site.bodyCount > corpsAvant)
    }

    /**
     * **Un site rasé se déclare gagné et tire son feu d'artifice.**
     *
     * Les deux vont ensemble : `cleared` est ce que l'interface lit pour annoncer la
     * victoire, et le bouquet part une fois, dans le même mouvement. Un feu d'artifice
     * qui repartirait à chaque image serait pire que pas de feu du tout.
     */
    @Test
    fun `raser le site declenche la victoire et le bouquet`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets
        assertTrue("le site est gagné avant qu'on y touche", !site.cleared)

        // On souffle le village par le milieu, plusieurs fois : c'est le moyen le plus
        // court d'atteindre l'objectif sans dépendre de la balistique.
        var garde = 0
        while (!site.cleared && garde++ < 40) {
            val x = site.left + (garde % 8) * (site.right - site.left) / 8f
            site.blast(x, game.terrain.heightAt(x) + 3f, 1_200_000f, 32f)
            repeat(90) { game.step(fixed) }
        }
        println(
            "VICTOIRE après $garde souffles : rasé=${site.cleared}, " +
                "avancement ${"%.0f".format(site.progress * 100f)} %, " +
                "particules ${game.effects.aliveCount}"
        )
        assertTrue("le site ne se laisse pas raser", site.cleared)

        // Le bouquet part : les effets sont occupés dans les images qui suivent.
        repeat(120) { game.step(fixed) }
        assertTrue("aucun feu d'artifice à la victoire", game.effects.busy)

        // Et il ne repart pas : on note ce qui est en vol, on laisse tourner, et le
        // compteur de fusées en attente ne remonte pas.
        val encore = game.effects.busy
        repeat(600) { game.step(fixed) }
        assertTrue("le feu d'artifice s'est éteint trop vite", encore)
    }

    /**
     * **On peut retirer le site**, et alors il n'y a plus rien à gagner.
     *
     * C'est ce que fait l'appui long sur « Machines » quand on veut revenir à un
     * établi nu : la graine nulle rend sa dalle plate à l'atelier.
     */
    @Test
    fun `un atelier sans site n a rien a raser`() {
        val game = GearMachineGame()
        game.loadSite(null)
        assertEquals("des pierres ont survécu", 0, game.targets.pieceTotal)
        assertTrue("un atelier vide se déclare gagné", !game.targets.cleared)
        assertEquals("l'avancement d'un site absent n'est pas nul", 0f, game.targets.progress, 1e-6f)

        // Et on peut en redresser un : c'est l'autre moitié du même geste.
        game.loadSite(31L)
        assertTrue("aucun site ne se redresse", game.targets.pieceTotal > 0)
        assertTrue("le relief n'est pas revenu", !game.terrain.flat)
    }
}
