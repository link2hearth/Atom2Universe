package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les effets : ça monte, ça éclate, ça retombe, et ça finit par s'éteindre.
 *
 * Un système de particules est la chose qu'on croit le plus facilement vérifier à
 * l'œil, et qu'on vérifie le plus mal : on voit une gerbe jolie et on ne voit ni la
 * particule qui ne meurt jamais, ni le bassin qui déborde, ni le bouquet qui tire dans
 * le vide parce qu'il manque de place. Ces trois-là se mesurent, et rien d'autre ne les
 * attrape.
 */
class TrebuchetEffectsTest {

    private fun vivantes(fx: TrebuchetEffects) = fx.sparks.count { it.alive }

    private fun avance(fx: TrebuchetEffects, secondes: Float, dt: Float = 1f / 60f) {
        repeat((secondes / dt).toInt()) { fx.update(dt) }
    }

    @Test
    fun `une fusee monte, eclate en haut, et ses etoiles retombent`() {
        val fx = TrebuchetEffects(7L)
        fx.rocket(50f)
        assertEquals("la fusée n'est pas partie", 1, vivantes(fx))

        var sommet = 0f
        var eclat = -1f
        var t = 0f
        val dt = 1f / 120f
        while (t < 8f) {
            fx.update(dt)
            t += dt
            val n = vivantes(fx)
            for (s in fx.sparks) if (s.alive) sommet = maxOf(sommet, s.y)
            // Le nombre de particules explose d'un coup : c'est l'éclatement.
            if (eclat < 0f && n > 20) eclat = sommet
        }
        println(
            "FUSÉE éclatée à ${"%.0f".format(eclat)} m, sommet ${"%.0f".format(sommet)} m, " +
                "il reste ${vivantes(fx)} étoiles après 8 s"
        )
        assertTrue("la fusée n'a pas éclaté", eclat > 0f)
        assertTrue("la fusée éclate trop bas : $eclat m", eclat > 20f)
        assertTrue("la fusée monte jusqu'à la lune : $sommet m", sommet < 200f)
        assertEquals("des étoiles brûlent encore huit secondes après", 0, vivantes(fx))
    }

    @Test
    fun `deux bouquets ne se ressemblent jamais`() {
        val fx = TrebuchetEffects(3L)
        val comptes = ArrayList<Int>()
        val teintes = ArrayList<Int>()
        repeat(6) {
            fx.clear()
            fx.rocket(50f)
            avance(fx, 7f)
            var max = 0
            val vues = HashSet<Int>()
            // On rejoue en comptant le maximum atteint : c'est la taille du bouquet.
            fx.clear()
            fx.rocket(50f)
            // Sept secondes, et pas quatre : une fusée qui vise soixante-dix mètres met
            // quatre secondes à y monter, comme une vraie. La fenêtre doit couvrir la
            // montée **et** le bouquet, sinon on mesure un ciel encore vide.
            repeat(420) {
                fx.update(1f / 60f)
                max = maxOf(max, vivantes(fx))
                for (s in fx.sparks) if (s.alive) vues.add(s.tint)
            }
            comptes.add(max)
            teintes.add(vues.size)
        }
        println("BOUQUETS tailles=$comptes teintes=$teintes")
        assertTrue("tous les bouquets ont la même taille : $comptes", comptes.toSet().size > 2)
        assertTrue("aucun bouquet n'a éclaté", comptes.all { it > 20 })
    }

    @Test
    fun `le bassin ne deborde jamais`() {
        val fx = TrebuchetEffects(11L)
        // Trente explosions au même endroit : de quoi demander bien plus que le bassin.
        repeat(30) { fx.explosion(50f, 10f, 12f) }
        avance(fx, 0.2f)
        val n = vivantes(fx)
        println("BASSIN $n particules pour un plafond de ${TrebuchetEffects.MAX_SPARKS}")
        assertTrue("le bassin a débordé : $n", n <= TrebuchetEffects.MAX_SPARKS)
        assertTrue("les explosions n'ont rien produit", n > 100)
        // Et tout finit par s'éteindre : une particule qui ne meurt pas est une fuite.
        avance(fx, 12f)
        assertEquals("des particules immortelles traînent", 0, vivantes(fx))
    }

    @Test
    fun `une explosion melange feu, eclats et fumee`() {
        val fx = TrebuchetEffects(5L)
        fx.explosion(0f, 5f, 10f)
        val sortes = fx.sparks.filter { it.alive }.map { it.kind }.toSet()
        println("EXPLOSION ${vivantes(fx)} particules, sortes=$sortes")
        assertTrue("l'explosion n'a pas de feu", sortes.contains(Puff.FIRE))
        assertTrue("l'explosion n'a pas d'éclats", sortes.contains(Puff.SPARK))
        assertTrue("l'explosion n'a pas de fumée", sortes.contains(Puff.SMOKE))

        // La fumée survit à tout le reste : c'est ce qu'on retient d'une explosion.
        avance(fx, 1.6f)
        val restant = fx.sparks.filter { it.alive }.map { it.kind }.toSet()
        println("EXPLOSION après 1,6 s : $restant")
        assertTrue("la fumée est partie la première", restant.contains(Puff.SMOKE))
        assertTrue("le feu brûle encore", !restant.contains(Puff.FIRE))
    }

    @Test
    fun `le feu d artifice tire ses fusees les unes apres les autres`() {
        val fx = TrebuchetEffects(2L)
        fx.skyTop = 130f
        fx.celebrate(30f, 120f, shots = 10)
        assertTrue("le spectacle n'est pas armé", fx.busy)
        assertEquals("des fusées sont parties trop tôt", 0, vivantes(fx))

        var maxi = 0
        var t = 0f
        val xs = ArrayList<Float>()
        var fusees = 0
        while (t < 20f) {
            val avant = fx.sparks.count { it.alive && it.kind == Puff.SHELL }
            fx.update(1f / 60f)
            t += 1f / 60f
            maxi = maxOf(maxi, vivantes(fx))
            // On relève l'abscisse **au départ** et pas en vol : une fusée penche, et
            // c'est voulu — celle qui part du bord du couloir en sort forcément un peu
            // en montant, comme n'importe quelle fusée un jour de vent.
            val apres = fx.sparks.filter { it.alive && it.kind == Puff.SHELL }
            if (apres.size > avant) {
                fusees++
                xs.add(apres.last().x)
            }
        }
        println(
            "SPECTACLE pic à $maxi particules, $fusees fusées tirées entre " +
                "${"%.0f".format(xs.min())} et ${"%.0f".format(xs.max())} m"
        )
        assertTrue("le spectacle n'a rien tiré", maxi > 100)
        assertTrue("les fusées sortent de leur couloir", xs.min() >= 30f && xs.max() <= 120f)
        assertTrue("le spectacle ne finit jamais", !fx.busy)
    }

    @Test
    fun `une fontaine monte depuis le sol puis s eteint`() {
        val fx = TrebuchetEffects(42L)
        fx.fountain(35f, ground = 6f)
        val depart = fx.sparks.filter { it.alive }
        assertTrue("la fontaine n'a pas produit d'étincelles", depart.size >= 50)
        assertTrue("la fontaine ne part pas vers le haut", depart.count { it.vy > 0f } > 45)
        assertTrue("la fontaine ne part pas du sol", depart.all { it.y == 6f })

        avance(fx, 6f)
        assertEquals("la fontaine laisse des particules immortelles", 0, vivantes(fx))
    }

    /**
     * Bout en bout : un site rasé déclenche son feu d'artifice, une fois et pas deux.
     *
     * C'est le seul test qui relie la règle du niveau au spectacle. Les deux vivent
     * très loin l'un de l'autre — l'un compte des pierres, l'autre fait voler des
     * points — et rien d'autre ne dirait qu'on a oublié de brancher le fil.
     */
    @Test
    fun `raser un site tire le feu d artifice`() {
        TargetRules.style = TargetStyle.ARCADE
        val g = TrebuchetGame()
        g.loadLevel(1L)
        assertTrue("le spectacle part avant la victoire", !g.effects.busy)

        // On rase tout à l'explosif, puis on laisse le tir se terminer normalement :
        // c'est la fin du tir qui décide, pas la dernière pierre.
        g.release()
        repeat(120) { g.step(1f / 60f) }
        repeat(6) { i ->
            g.targets.blast(g.targets.left + i * 9f, 3f, 2_000_000f, 60f)
            repeat(30) { g.step(1f / 60f) }
        }
        var t = 0f
        while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 30f) {
            g.step(1f / 60f)
            t += 1f / 60f
        }
        println(
            "VICTOIRE rasé=${g.targets.cleared} " +
                "(${g.targets.pieceBroken}/${g.targets.pieceTotal} pierres), " +
                "spectacle=${g.effects.busy}"
        )
        assertTrue("le site n'a pas été rasé, le test ne prouve rien", g.targets.cleared)
        assertTrue("aucun feu d'artifice à la victoire", g.effects.busy)

        var pic = 0
        repeat(1200) {
            g.stepEffects(1f / 60f)
            pic = maxOf(pic, g.effects.aliveCount)
        }
        println("VICTOIRE pic à $pic particules, puis le ciel se vide")
        assertTrue("le feu d'artifice est chiche : $pic particules", pic > 150)
        assertTrue("le ciel ne se vide jamais", !g.effects.busy)

        // Et un site suivant repart d'un ciel noir.
        g.loadLevel(2L)
        assertEquals("le nouveau site hérite des étoiles du précédent", 0, vivantes(g.effects))
    }

    /**
     * Le feu d'artifice occupe la hauteur qu'on lui donne.
     *
     * C'est le défaut qu'un écran debout a révélé : des fusées réglées pour un écran
     * couché éclatent à quatre-vingts mètres, ce qui remplit joliment une image large
     * et laisse les trois quarts d'une image haute au ciel étoilé. La hauteur visée
     * suit donc ce que le joueur voit — et le nombre de fusées avec, parce qu'un grand
     * ciel vide reste un ciel vide, même bien visé.
     */
    @Test
    fun `le feu d artifice remplit le ciel qu on lui donne`() {
        fun sommetEtFusees(ciel: Float): Pair<Float, Int> {
            val fx = TrebuchetEffects(21L)
            fx.skyTop = ciel
            fx.celebrate(30f, 120f)
            var haut = 0f
            var fusees = 0
            var vues = 0
            var t = 0f
            while (t < 30f && fx.busy) {
                val avant = fx.sparks.count { it.alive && it.kind == Puff.SHELL }
                fx.update(1f / 60f)
                t += 1f / 60f
                val apres = fx.sparks.count { it.alive && it.kind == Puff.SHELL }
                if (apres > avant) fusees += apres - avant
                for (s in fx.sparks) if (s.alive) haut = maxOf(haut, s.y)
                vues++
            }
            return haut to fusees
        }

        val (basPlafond, peu) = sommetEtFusees(120f)
        val (hautPlafond, beaucoup) = sommetEtFusees(700f)
        println(
            "CIEL 120 m → sommet ${"%.0f".format(basPlafond)} m, $peu fusées ; " +
                "700 m → sommet ${"%.0f".format(hautPlafond)} m, $beaucoup fusées"
        )
        assertTrue(
            "les fusées ignorent la hauteur de l'écran : $basPlafond puis $hautPlafond",
            hautPlafond > 3f * basPlafond
        )
        assertTrue("le grand ciel n'a pas reçu ses fusées", beaucoup >= peu)
        // Et elles restent dans l'image : un bouquet au-dessus de l'écran est perdu.
        assertTrue("les fusées dépassent le haut de l'écran", hautPlafond < 700f * 1.2f)
        assertTrue("les fusées se tassent en bas : $hautPlafond", hautPlafond > 700f * 0.75f)
    }

    @Test
    fun `tout est dans le fond sauf les explosions`() {
        val fx = TrebuchetEffects(9L)
        fx.rocket(50f)
        avance(fx, 3f)
        assertTrue(
            "un feu d'artifice se dessine au premier plan",
            fx.sparks.filter { it.alive }.all { it.background }
        )
        fx.clear()
        fx.explosion(10f, 4f, 8f)
        assertTrue(
            "une explosion se dessine à l'arrière-plan",
            fx.sparks.filter { it.alive }.none { it.background }
        )
    }
}
