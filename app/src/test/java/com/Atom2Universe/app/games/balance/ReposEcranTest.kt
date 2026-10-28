package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * La veille des vues du levier et du mobile se juge sur ce que l'écran montre ([ReposEcran]).
 * On monte un vrai mobile et une vraie pile, on les laisse vivre dix secondes à soixante
 * images par seconde comme la vue, et le repos doit finir par être déclaré : sinon le fil ne
 * dort jamais et l'écran chauffe devant une image figée.
 *
 * Les échelles sont celles de la tablette de test (environ 1800 px de large) : le demi-pixel
 * y est le plus petit, donc c'est là que le repos est le plus dur à déclarer.
 */
class ReposEcranTest {

    private val image = 1f / 60f
    private val pas = 1f / 120f

    // ── Le mobile ─────────────────────────────────────────────────────────────

    private fun crochet(masse: Int) = BoutCrochet().apply { objet = MobileObjet(masse) }

    private fun monter(racine: MobileTige): MobileMonde {
        MobileMiseEnPage.etendue(racine, racine.crochets().maxOf { it.objet?.rayon ?: 0.2f })
        return MobileMonde(racine, 6f)
    }

    /** Fait vivre le mobile [secondes] ; rend l'instant où le repos est déclaré, ou -1. */
    private fun reposDuMobile(m: MobileMonde, secondes: Float): Float {
        val repos = ReposEcran()
        // Un mobile cadré sur la largeur de la tablette : de l'ordre de mille pixels par mètre.
        val echelle = 1000f
        var t = 0f
        var declare = -1f
        while (t < secondes) {
            m.step(pas)
            m.step(pas)
            t += image
            repos.debut()
            repos.monde(m.world, 900f, 30f + 6f * echelle, echelle)
            val fige = repos.fin(image, true)
            if (fige && declare < 0f) declare = t
            if (!fige) declare = -1f
        }
        return declare
    }

    @Test
    fun `un mobile juste finit par etre declare au repos`() {
        // La chaîne à la Calder de MobileStabiliteTest : 1 au cran 2 contre 2 au cran 1, etc.
        val bas = MobileTige(2, 1, crochet(1), crochet(2))
        val milieu = MobileTige(1, 1, crochet(3), BoutTige(bas))
        val m = monter(MobileTige(3, 2, crochet(4), BoutTige(milieu)))
        val t = reposDuMobile(m, 10f)
        assertTrue("le mobile juste n'est jamais déclaré au repos", t >= 0f)
    }

    @Test
    fun `un mobile de travers posé sur sa butée finit aussi par dormir`() {
        // Le cas courant pendant la recherche : une tige penche franchement contre sa butée
        // douce. C'est là que l'ancienne veille, jugée sur la vitesse, battait sur le seuil.
        val bas = MobileTige(2, 1, crochet(2), crochet(1))
        val milieu = MobileTige(1, 1, crochet(3), BoutTige(bas))
        val m = monter(MobileTige(3, 2, crochet(4), BoutTige(milieu)))
        val t = reposDuMobile(m, 10f)
        assertTrue("le mobile de travers n'est jamais déclaré au repos", t >= 0f)
    }

    // ── Le levier ─────────────────────────────────────────────────────────────

    private fun BalanceGame.put(w: BalanceWeight, x: Float) {
        beginDrag(w, w.body.x, w.body.y)
        dragTo(x, BalanceRules.PLANK_TOP + 0.45f)
        endDrag()
    }

    @Test
    fun `une pile de briques sur la planche finit par etre declaree au repos`() {
        val game = BalanceGame(Random(507L))
        game.setViewport(8f)
        game.newLevel(BalanceGame.Difficulty.EASY)
        // Deux briques l'une sur l'autre à gauche, la troisième seule à droite.
        val (a, b, c) = game.weights
        game.put(a, -0.9f)
        game.put(b, -0.9f)
        game.put(c, 1.0f)
        assertTrue("la pile n'a pas été posée", game.weights.all { it.placed })

        // La vue de la tablette : 5 m sur 1800 px.
        val echelle = 1800f / BalanceRules.WORLD_WIDTH
        val repos = ReposEcran()
        var t = 0f
        var declare = -1f
        while (t < 10f) {
            game.step(pas)
            game.step(pas)
            t += image
            repos.debut()
            repos.monde(game.world, 900f, 2880f, echelle)
            val fige = repos.fin(image, game.phase != BalanceGame.Phase.TESTING)
            if (fige && declare < 0f) declare = t
            if (!fige) declare = -1f
        }
        assertTrue("la pile n'est jamais déclarée au repos", declare >= 0f)
    }

    // ── La règle elle-même ────────────────────────────────────────────────────

    @Test
    fun `une derive lente finit par rouvrir la fenetre`() {
        val repos = ReposEcran()
        var x = 0f
        var vu = false
        // Un dixième de pixel par image : rien d'un coup, cinq pixels en une seconde.
        repeat(60) {
            x += 0.1f
            repos.debut()
            repos.point(x, 0f)
            if (repos.fin(image, true)) vu = true
        }
        assertFalse("une dérive de six pixels par seconde a été prise pour du repos", vu)
    }

    @Test
    fun `un fremissement plus petit qu un demi pixel est du repos`() {
        val repos = ReposEcran()
        var fige = false
        repeat(60) { i ->
            repos.debut()
            repos.point(100f + if (i % 2 == 0) 0.2f else -0.2f, 50f)
            fige = repos.fin(image, true)
        }
        assertTrue("un frémissement invisible empêche le repos", fige)
    }
}
