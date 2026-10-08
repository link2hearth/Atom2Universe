package com.Atom2Universe.app.games.bigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Accrétion sur le moteur partagé : le bocal doit tenir (rien ne s'en échappe, le tas se
 * calme), les jumeaux doivent fusionner, et une partie doit bien finir par déborder.
 */
class BiggerGameTest {

    private val frame = 1f / 60f

    private fun run(game: BiggerGame, seconds: Float) {
        repeat((seconds / frame).toInt()) { game.step(frame); game.events.clear() }
    }

    private fun assertInsideJar(game: BiggerGame) {
        for (o in game.orbs) {
            val b = o.body
            assertFalse("position invalide", b.x.isNaN() || b.y.isNaN())
            assertTrue("sorti par la gauche : ${b.x}", b.x >= o.radius - 0.1f)
            assertTrue("sorti par la droite : ${b.x}", b.x <= Accretion.WIDTH - o.radius + 0.1f)
            assertTrue("passé sous le fond : ${b.y}", b.y >= o.radius - 0.1f)
        }
    }

    @Test
    fun twinsMerge() {
        val game = BiggerGame(Random(1))
        // Deux poussières lâchées au même endroit : elles doivent fusionner en caillou.
        game.restoreState("1|0|1|0|-1|0|0|0,5.0,0.34,0,0,0,0;0,5.0,1.2,0,0,0,0")
        run(game, 1f)
        assertEquals(1, game.orbs.size)
        assertEquals(1, game.orbs[0].tier)
        assertEquals(Accretion.points(1), game.score)
    }

    @Test
    fun blackHolesAnnihilate() {
        val game = BiggerGame(Random(1))
        game.restoreState("1|0|1|0|10|0|0|11,3.0,1.15,0,0,0,0;11,5.3,1.15,0,0,0,0;0,8.0,0.34,0,0,0,0")
        run(game, 1f)
        assertTrue("les trous noirs ont disparu", game.orbs.none { it.tier == Accretion.BLACK_HOLE })
        assertTrue(game.score >= Accretion.ANNIHILATION_POINTS)
    }

    @Test
    fun jarHoldsAndSettles() {
        val rnd = Random(7)
        val game = BiggerGame(Random(3))
        var maxOrbs = 0
        var drops = 0
        while (!game.over && drops < 400) {
            if (game.canDrop) { game.drop(rnd.nextFloat() * Accretion.WIDTH); drops++ }
            run(game, 0.6f)
            assertInsideJar(game)
            maxOrbs = maxOf(maxOrbs, game.orbs.size)
        }
        println("Partie : $drops lâchers, ${game.merges} fusions, score ${game.score}, plus gros ${game.bestTier}, " +
            "jusqu'à $maxOrbs astres, fin=${game.over}")
        assertTrue("des fusions ont eu lieu", game.merges > 20)
        assertTrue("la partie finit par déborder", game.over)
    }

    /**
     * Le tas finit par s'immobiliser. On ne demande pas qu'il le reste à chaque instant :
     * une fusion tardive au fond le réorganise, c'est le jeu. On demande qu'il existe des
     * moments de calme complet — un tremblement permanent n'en laisserait aucun.
     */
    @Test
    fun pileComesToRest() {
        val rnd = Random(11)
        val game = BiggerGame(Random(5))
        repeat(25) {
            if (game.canDrop) game.drop(rnd.nextFloat() * Accretion.WIDTH)
            run(game, 0.6f)
        }
        // Le déplacement, pas la vitesse : un contact posé reçoit la pesanteur puis la
        // rend à chaque pas, et garde un bruit de vitesse que l'œil ne voit jamais.
        var calmest = Float.MAX_VALUE
        repeat(8) {
            run(game, 0.75f)
            val before = game.orbs.associateWith { it.body.x to it.body.y }
            run(game, 0.25f)
            var moved = 0f
            for ((o, p) in before) if (o in game.orbs) {
                val dx = o.body.x - p.first; val dy = o.body.y - p.second
                moved = maxOf(moved, sqrt(dx * dx + dy * dy))
            }
            calmest = minOf(calmest, moved)
        }
        println("Tas de ${game.orbs.size} astres, déplacement max en 0,25 s au moment le plus calme : $calmest")
        assertTrue("le tas tremble : $calmest", calmest < 0.01f)
    }
}
