package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Le jeu du mobile : tableaux tirés, prise et dépôt, victoire.
 *
 * Pour gagner un tableau, les tests rejouent son **tirage** (la répartition qui a servi à le
 * construire) : ils ne cherchent jamais de solution.
 */
class MobileJeuTest {

    private fun MobileGame.jouer(secondes: Float) {
        repeat((secondes * 120f).toInt()) { step(1f / 120f) }
    }

    /** Accroche le tirage directement, sans passer par les gestes. */
    private fun MobileGame.poserLeTirage() {
        for ((c, o) in tirage) {
            etagere.removeAll { it === o }
            monde.accrocher(c, o)
        }
    }

    @Test
    fun `chaque tableau est bien forme`() {
        for (diff in MobileGame.Difficulty.entries) {
            repeat(30) { graine ->
                val jeu = MobileGame(Random(graine))
                jeu.newLevel(diff)
                val racine = jeu.monde.racine
                val crochets = racine.crochets()
                // Les deux premiers tableaux faciles n'ont que deux objets, et pas de leurre.
                val initiation = diff == MobileGame.Difficulty.EASY && crochets.size == 2
                assertTrue(crochets.size == diff.objets || initiation)
                assertEquals(crochets.size + if (initiation) 0 else diff.leurres, jeu.etagere.size)
                val masses = jeu.tirage.map { it.second.masse }
                assertTrue("trop de jumeaux : $masses", masses.size - masses.distinct().size <= diff.jumeaux)
                assertTrue("triplés : $masses", masses.groupingBy { it }.eachCount().values.all { it <= 2 })
                assertTrue("rien n'est accroché au départ", crochets.all { it.objet == null })
                for (t in racine.tiges()) {
                    assertTrue(t.brasGauche >= 1 && t.brasDroit >= 1)
                    assertTrue("tige de ${t.brasGauche + t.brasDroit} crans", t.brasGauche + t.brasDroit <= MobileGame.CRANS_MAX)
                }
                assertTrue("aucune tige décentrée", racine.tiges().any { it.brasGauche != it.brasDroit })
                assertFalse(jeu.gagne)
            }
        }
    }

    @Test
    fun `le tirage gagne a toutes les difficultes`() {
        for (diff in MobileGame.Difficulty.entries) {
            repeat(12) { graine ->
                val jeu = MobileGame(Random(100 + graine))
                jeu.newLevel(diff)
                jeu.poserLeTirage()
                assertTrue(jeu.monde.racine.equilibree())
                jeu.jouer(8f)
                val pentes = jeu.monde.racine.tiges().joinToString { "%.1f°".format(jeu.monde.penteDeg(it)) }
                assertTrue("$diff / graine ${100 + graine} pas gagné : $pentes", jeu.gagne)
            }
        }
    }

    @Test
    fun `la victoire n'attend pas la fin du balancement`() {
        for (diff in MobileGame.Difficulty.entries) {
            val jeu = MobileGame(Random(150))
            jeu.newLevel(diff, 5)
            jeu.poserLeTirage()
            // Les leurres restent sur l'étagère : ils ne comptent pas.
            assertEquals(diff.leurres, jeu.etagere.size)
            jeu.jouer(0.6f)
            assertTrue("$diff : toujours pas gagné", jeu.gagne)
        }
    }

    @Test
    fun `l'anneau vise le crochet le plus proche, jamais un objet voisin`() {
        val jeu = MobileGame(Random(8))
        jeu.newLevel(MobileGame.Difficulty.HARD, 5)
        // Tout le tirage accroché sauf le premier objet, qu'on prend sur l'étagère : son crochet
        // est vide, entouré d'objets garnis.
        val (vide, objet) = jeu.tirage.first()
        for ((c, o) in jeu.tirage.drop(1)) {
            jeu.etagere.removeAll { it === o }
            jeu.monde.accrocher(c, o)
        }
        jeu.jouer(2f)
        val case = FloatArray(2)
        jeu.caseDe(objet, case)
        assertTrue(jeu.prendre(case[0], case[1], 0.01f))
        assertTrue(jeu.enMain === objet)
        val p = FloatArray(2)
        jeu.monde.crochet(vide, p)
        // L'anneau à deux centimètres du crochet vide : c'est lui, pas un voisin garni.
        jeu.deplacer(p[0] + 0.02f, p[1], 0.15f)
        assertTrue(jeu.cible === vide)
        // L'anneau posé au milieu d'un objet garni : c'est l'échange avec celui-là.
        val (autre, _) = jeu.tirage.last()
        val corpsAutre = jeu.monde.corps(autre)!!
        jeu.deplacer(corpsAutre.x, corpsAutre.y, 0.15f)
        assertTrue(jeu.cible === autre)
    }

    @Test
    fun `une repartition fausse ne gagne pas`() {
        repeat(20) { graine ->
            val jeu = MobileGame(Random(300 + graine))
            jeu.newLevel(MobileGame.Difficulty.MEDIUM)
            // Le tirage, décalé d'un crochet : presque toujours faux.
            val crochets = jeu.tirage.map { it.first }
            val objets = jeu.tirage.map { it.second }
            for ((i, c) in crochets.withIndex()) {
                val o = objets[(i + 1) % objets.size]
                jeu.etagere.removeAll { it === o }
                jeu.monde.accrocher(c, o)
            }
            if (jeu.monde.racine.equilibree()) return@repeat
            jeu.jouer(6f)
            assertFalse("graine ${300 + graine} : un mobile faux a gagné", jeu.gagne)
        }
    }

    @Test
    fun `une fois juste, les voisins ne se chevauchent pas`() {
        for (diff in MobileGame.Difficulty.entries) {
            repeat(12) { graine ->
                val jeu = MobileGame(Random(700 + graine))
                jeu.newLevel(diff)
                jeu.poserLeTirage()
                jeu.jouer(6f)
                val corps = jeu.tirage.map { (c, o) -> jeu.monde.corps(c)!! to o }
                for (i in corps.indices) for (j in i + 1 until corps.size) {
                    val (a, oa) = corps[i]
                    val (b, ob) = corps[j]
                    val d = hypot(a.x - b.x, a.y - b.y)
                    assertTrue(
                        "$diff / graine ${700 + graine} : objets ${oa.masse} et ${ob.masse} se chevauchent",
                        d >= oa.rayon + ob.rayon - 0.01f
                    )
                }
            }
        }
    }

    @Test
    fun `prendre sur l'etagere et poser sur un crochet`() {
        val jeu = MobileGame(Random(1))
        val o = jeu.casesObjets[0]
        val case = FloatArray(2)
        jeu.caseDe(o, case)
        assertTrue(jeu.prendre(case[0], case[1], 0.05f))
        assertTrue(jeu.enMain === o)
        assertFalse(jeu.etagere.any { it === o })

        val c = jeu.monde.racine.crochets()[0]
        val p = FloatArray(2)
        jeu.monde.crochet(c, p)
        // L'anneau de l'objet tenu sur le crochet.
        jeu.deplacer(p[0], p[1], 0.3f)
        assertTrue(jeu.cible === c)
        jeu.lacher()
        assertTrue(c.objet === o)
        assertNull(jeu.enMain)
    }

    @Test
    fun `lacher loin de tout rend l'objet a l'etagere`() {
        val jeu = MobileGame(Random(2))
        val o = jeu.casesObjets[0]
        val case = FloatArray(2)
        jeu.caseDe(o, case)
        jeu.prendre(case[0], case[1], 0.05f)
        jeu.deplacer(case[0] + 50f, case[1], 0.3f)
        assertNull(jeu.cible)
        jeu.lacher()
        assertTrue(jeu.etagere.any { it === o })
    }

    @Test
    fun `poser sur un crochet garni echange les deux objets`() {
        val jeu = MobileGame(Random(3))
        val (c1, c2) = jeu.monde.racine.crochets()
        val a = jeu.casesObjets[0]
        val b = jeu.casesObjets[1]
        jeu.etagere.removeAll { it === a || it === b }
        jeu.monde.accrocher(c1, a)
        jeu.monde.accrocher(c2, b)
        // À peine le temps de bouger : si ce hasard était juste, il ne doit pas avoir déjà gagné.
        jeu.jouer(0.1f)
        assertFalse(jeu.gagne)

        // On prend a au mobile et on le lâche sur b : b doit partir là où était a.
        val corpsA = jeu.monde.corps(c1)!!
        assertTrue(jeu.prendre(corpsA.x, corpsA.y, 0.05f))
        val corpsB = jeu.monde.corps(c2)!!
        jeu.deplacer(corpsB.x, corpsB.y, 0.3f)
        assertTrue(jeu.cible === c2)
        jeu.lacher()
        assertTrue(c2.objet === a)
        assertTrue(c1.objet === b)
    }

    @Test
    fun `recommencer remet tout sur l'etagere`() {
        val jeu = MobileGame(Random(4))
        jeu.newLevel(MobileGame.Difficulty.HARD)
        val crans = jeu.monde.racine.tiges().map { it.unite }
        jeu.poserLeTirage()
        jeu.recommencer()
        assertEquals(jeu.casesObjets.size, jeu.etagere.size)
        assertTrue(jeu.monde.racine.crochets().all { it.objet == null })
        assertEquals("la mise en page ne bouge pas", crans, jeu.monde.racine.tiges().map { it.unite })
    }

    @Test
    fun `apres une victoire, on ne prend plus rien`() {
        val jeu = MobileGame(Random(5))
        jeu.poserLeTirage()
        jeu.jouer(8f)
        assertTrue(jeu.gagne)
        val c = jeu.monde.racine.crochets()[0]
        val corps = jeu.monde.corps(c)!!
        assertFalse(jeu.prendre(corps.x, corps.y, 0.05f))
    }

    @Test
    fun `le niveau monte apres une victoire, pas apres un abandon`() {
        val jeu = MobileGame(Random(6))
        jeu.newLevel()
        assertEquals(1, jeu.level)
        jeu.poserLeTirage()
        jeu.jouer(8f)
        jeu.newLevel()
        assertEquals(2, jeu.level)
        jeu.newLevel()
        assertEquals(2, jeu.level)
    }
}
