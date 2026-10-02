package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Les verbes du jeu autres que « amener la bille au bouton » : franchir des anneaux dans
 * l'ordre, tenir dans une cuvette, et finir avant la limite de temps.
 *
 * Chacun pose une question que les rampes ne posaient pas, et chacun a un piege que ce
 * fichier garde : un anneau qui compterait dans le desordre, une tenue qui se cumulerait
 * au lieu de repartir de zero, une machine « arretee » que le jeu declarerait perdue
 * alors qu'elle est en train de tenir.
 */
class InfernaleVerbesTest {

    private val PAS = 1f / 120f

    private fun avancer(p: Plateau, secondes: Float) {
        var t = 0f
        while (t < secondes) { p.avancer(PAS); t += PAS }
    }

    // ── Les anneaux ──────────────────────────────────────────────────────────

    @Test
    fun `une bille qui tombe dans un anneau le franchit`() {
        val p = Plateau()
        p.poserBouton(x = 6f, bas = 0f)
        p.poserBille(x = 0f, y = 3.5f)
        val a = p.poserAnneau(x = 0f, y = 2f)
        avancer(p, 1f)
        assertTrue("l'anneau n'a pas vu passer la bille", a.franchi)
    }

    @Test
    fun `le deuxieme anneau ne compte pas avant le premier`() {
        val p = Plateau()
        p.poserBouton(x = 6f, bas = 0f)
        p.poserBille(x = 0f, y = 3.5f)
        val premier = p.poserAnneau(x = -3f, y = 2f)
        val second = p.poserAnneau(x = 0f, y = 2f)
        avancer(p, 1f)
        assertFalse("le second anneau a compte dans le desordre", second.franchi)
        assertFalse(premier.franchi)
        assertFalse(p.anneauxFranchis)
    }

    @Test
    fun `les anneaux se franchissent dans l'ordre`() {
        val p = Plateau()
        p.poserBouton(x = 6f, bas = 0f)
        p.poserBille(x = 0f, y = 4.5f)
        val haut = p.poserAnneau(x = 0f, y = 3.4f)
        val bas = p.poserAnneau(x = 0f, y = 1.6f)
        avancer(p, 1.2f)
        assertTrue(haut.franchi)
        assertTrue(bas.franchi)
        assertTrue(p.anneauxFranchis)
    }

    @Test
    fun `un domino dans l'anneau ne le franchit pas`() {
        val p = Plateau()
        p.poserBouton(x = 6f, bas = 0f)
        p.poserBille(x = -4f, y = 1f)
        val a = p.poserAnneau(x = 0f, y = 0.4f)
        p.poser(Pieces.domino(x = 0f, bas = 0f))
        avancer(p, 1f)
        assertFalse("un domino a franchi l'anneau", a.franchi)
    }

    @Test
    fun `le bouton ne compte qu'une fois les anneaux franchis`() {
        // La bille tombe droit dans le bouton sans jamais passer par l'anneau, qui est ailleurs.
        val p = Plateau()
        p.poserBouton(x = 0f, bas = 0f)
        p.poserBille(x = 0f, y = 2f)
        p.poserAnneau(x = -4f, y = 2f)
        avancer(p, 2f)
        assertFalse("le bouton a gagne sans les anneaux", p.gagne)
    }

    @Test
    fun `l'anneau franchi, le bouton gagne`() {
        val p = Plateau()
        p.poserBouton(x = 0f, bas = 0f)
        p.poserBille(x = 0f, y = 3f)
        p.poserAnneau(x = 0f, y = 1.6f)
        avancer(p, 2f)
        assertTrue(p.gagne)
    }

    @Test
    fun `la bille doree est la seule a franchir les anneaux d'un tableau a temoin`() {
        val p = Plateau()
        p.poserBouton(x = 6f, bas = 0f)
        p.poserBille(x = 0f, y = 3.5f)
        p.poserTemoin(x = -4f, haut = 2f)
        val a = p.poserAnneau(x = 0f, y = 2f)
        avancer(p, 1f)
        assertFalse("la bille de depart a franchi un anneau reserve a la doree", a.franchi)
    }

    // ── Tenir ────────────────────────────────────────────────────────────────

    private fun cuvette(duree: Float = 2f): Plateau {
        val p = Plateau()
        val b = p.poserBouton(x = 0f, bas = 0f, largeur = 0.5f)
        b.duree = duree
        p.poserCuvette(0f)
        return p
    }

    @Test
    fun `une bille posee dans la cuvette gagne apres la duree, pas avant`() {
        val p = cuvette(2f)
        p.poserBille(x = 0f, y = 1.2f)
        avancer(p, 1.2f)
        assertFalse("elle a gagne en moins de la duree demandee", p.gagne)
        assertTrue("la jauge n'avance pas", p.bouton!!.progression > 0f)
        avancer(p, 1.5f)
        assertTrue("elle est restee assez longtemps mais n'a pas gagne", p.gagne)
    }

    @Test
    fun `une bille qui traverse a toute vitesse ne cumule rien`() {
        // Lancee de haut, a plat, sur le sol : elle passe sous la zone en une fraction de seconde.
        val p = Plateau()
        val b = p.poserBouton(x = 0f, bas = 0f, largeur = 0.5f)
        b.duree = 2f
        p.poserBille(x = -4f, y = 0.11f).apply { vx = 6f }
        avancer(p, 3f)
        assertFalse(p.gagne)
        assertEquals("la tenue n'est pas retombee a zero", 0f, b.tenue, 1e-4f)
    }

    @Test
    fun `la tenue repart de zero quand la bille sort`() {
        val p = cuvette(5f)
        val bille = p.poserBille(x = 0f, y = 0.5f)
        avancer(p, 1f)
        assertTrue(p.bouton!!.tenue > 0.3f)
        // On la sort de la zone d'un coup : elle ne doit pas garder son avance.
        bille.x = 3f
        bille.y = 0.11f
        bille.wake()
        avancer(p, 0.2f)
        assertEquals(0f, p.bouton!!.tenue, 1e-4f)
    }

    @Test
    fun `une machine qui tient n'est pas donnee pour perdue`() {
        // La bille dort dans la cuvette : le monde est « immobile », et c'est la que le jeu
        // aurait crie a l'echec alors que la jauge se remplit.
        val tableau = Tableau(1L, billeX = 0f, billeY = 1.4f, boutonX = 0f, boutonBas = 0f, tenir = 3f)
        val partie = Partie(tableau)
        partie.lancer()
        var t = 0f
        var echoue = false
        while (t < 2.8f) {
            partie.avancer(PAS); t += PAS
            if (partie.echoue) echoue = true
        }
        assertFalse("la machine a ete declaree perdue pendant qu'elle tenait", echoue)
        assertTrue("la jauge s'est arretee", partie.plateau.bouton!!.progression > 0.5f)
        while (t < 4f) { partie.avancer(PAS); t += PAS }
        assertTrue(partie.gagne)
    }

    @Test
    fun `une machine qui rate la cuvette finit bien par etre perdue`() {
        val tableau = Tableau(1L, billeX = 3f, billeY = 1.4f, boutonX = 0f, boutonBas = 0f, tenir = 3f)
        val partie = Partie(tableau)
        partie.lancer()
        var t = 0f
        while (t < 6f && !partie.echoue) { partie.avancer(PAS); t += PAS }
        assertTrue("une bille loin de la zone n'a jamais ete donnee perdue", partie.echoue)
    }

    // ── Le temps ─────────────────────────────────────────────────────────────

    @Test
    fun `le temps accorde peut s'ecouler pendant que la machine roule encore`() {
        val tableau = Tableau(1L, billeX = 0f, billeY = 9.5f, boutonX = 6f, boutonBas = 0f, limite = 1f)
        val partie = Partie(tableau)
        assertFalse(partie.tempsEcoule)
        assertEquals(1f, partie.tempsRestant!!, 1e-4f)
        partie.lancer()
        var t = 0f
        while (t < 1.05f) { partie.avancer(PAS); t += PAS }
        assertTrue("la limite est passee et la machine n'est pas perdue", partie.echoue)
        assertTrue(partie.tempsEcoule)
        assertEquals(0f, partie.tempsRestant!!, 1e-4f)
    }

    @Test
    fun `le compte a rebours s'arrete a la victoire`() {
        val tableau = Tableau(1L, billeX = 0f, billeY = 1f, boutonX = 0f, boutonBas = 0f, limite = 5f)
        val partie = Partie(tableau)
        partie.lancer()
        var t = 0f
        while (t < 1f) { partie.avancer(PAS); t += PAS }
        assertTrue("la bille aurait du tomber dans le bouton", partie.gagne)
        val reste = partie.tempsRestant!!
        while (t < 6f) { partie.avancer(PAS); t += PAS }
        assertEquals("le temps a continue apres la victoire", reste, partie.tempsRestant!!, 1e-4f)
        assertFalse("une machine gagnee ne peut pas etre en retard", partie.tempsEcoule)
    }

    @Test
    fun `sans limite, il n'y a pas de compte a rebours`() {
        val partie = Partie(Tableaux.pourNiveau(1))
        assertNull(partie.tempsRestant)
    }

    // ── Le cycle des niveaux ─────────────────────────────────────────────────

    @Test
    fun `chaque verbe arrive seul, a son rang`() {
        val attendu = mapOf(
            3 to "temoin", 4 to "anneau", 5 to "socle", 6 to "temoin+anneau",
            7 to "tenir", 8 to "chrono", 9 to "temoin"
        )
        for (n in 1..2) {
            val v = Tableaux.verbes(n)
            assertFalse("le niveau $n est le premier : rien de special",
                v.temoin || v.anneaux > 0 || v.socle || v.tenir || v.chrono)
        }
        for ((n, nom) in attendu) {
            val v = Tableaux.verbes(n)
            val lu = buildList {
                if (v.temoin) add("temoin")
                if (v.anneaux > 0) add("anneau")
                if (v.socle) add("socle")
                if (v.tenir) add("tenir")
                if (v.chrono) add("chrono")
            }.joinToString("+")
            assertEquals("niveau $n", nom, lu)
        }
    }

    @Test
    fun `les tableaux generes respectent la regle de dessin pour chaque verbe`() {
        for (n in 1..40) {
            val v = Tableaux.verbes(n)
            val t = Tableaux.pourNiveau(n)
            assertEquals("niveau $n : nombre d'anneaux", v.anneaux, t.anneaux.size)
            assertEquals("niveau $n : tenir", v.tenir, t.tenir > 0f)
            assertEquals("niveau $n : limite", v.chrono, t.limite > 0f)
            val departY = if (v.temoin) t.temoinHaut else t.billeY
            var precedent = departY
            for (a in t.anneaux) {
                assertTrue("niveau $n : un anneau plus haut que ce qui le precede", a.y < precedent)
                assertTrue("niveau $n : anneau trop bas pour qu'une planche passe dessous", a.y >= 1.3f)
                assertTrue("niveau $n : anneau hors du plateau", abs(a.x) < Plateau.LARGEUR / 2f)
                precedent = a.y
            }
            if (v.chrono) assertTrue("niveau $n : limite ${t.limite}", t.limite in 4f..10f)
            if (v.tenir) assertTrue("niveau $n : tenue ${t.tenir}", t.tenir in 2.5f..3.5f)
        }
    }

    @Test
    fun `aucun tableau vide ne se gagne tout seul, quel que soit le verbe`() {
        for (n in 1..24) {
            val m = Tableaux.monter(Tableaux.pourNiveau(n))
            m.derouler(10f, arreterALaVictoire = false)
            assertFalse("le niveau $n se gagne sans rien poser", m.gagne)
        }
    }

    @Test
    fun `un tableau a tenir monte sa cuvette et un tableau a anneaux ses anneaux`() {
        val tenir = Tableaux.monter(Tableaux.pourNiveau(7))
        assertEquals(2, tenir.cuvette.size)
        assertTrue(tenir.bouton!!.duree > 0f)
        val anneaux = Tableaux.monter(Tableaux.pourNiveau(4))
        assertEquals(1, anneaux.anneaux.size)
        assertNotNull(anneaux.anneaux.first().zone)
    }
}
