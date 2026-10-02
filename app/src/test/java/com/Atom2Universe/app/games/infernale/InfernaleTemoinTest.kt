package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La bille temoin et la chaine : ce qui change le verbe du jeu.
 *
 * Deux promesses, qu'un oeil ne verifie pas :
 *  - dans un tableau a temoin, **seule** la bille doree ouvre le portail, et la bille de
 *    depart n'y sert qu'a la liberer ;
 *  - la note compte les pieces que la machine a vraiment atteintes, ni plus, ni moins.
 */
class InfernaleTemoinTest {

    private val PAS = 1f / 120f

    /**
     * Une solution **faite a la main**, pour un tableau precis : la bille de depart roule sur
     * une planche, percute la bille doree, qui descend deux planches et file jusqu'au
     * bouton. Ce n'est pas un resolveur — c'est la preuve que la mecanique existe et se
     * joue, comme la ligne de dominos de `InfernaleChaineTest`.
     */
    private fun montage(boutonX: Float = 4.5f, avecPlanche: Boolean = true): Plateau {
        val p = Plateau()
        p.poserBouton(x = boutonX, bas = 0f)
        p.poserBille(x = -2.6f, y = 5f)
        p.poserTemoin(x = -1.2f, haut = 3.4f)
        if (avecPlanche) p.poser(Pieces.rampe(x = -2.2f, y = 3.7f, pente = 20f, longueur = 1.5f))
        p.poser(Pieces.rampe(x = -0.2f, y = 2.4f, pente = 20f, longueur = 1.5f))
        p.poser(Pieces.rampe(x = 1.5f, y = 1.2f, pente = 20f, longueur = 1.5f))
        return p
    }

    @Test
    fun `la bille doree liberee ouvre le portail`() {
        val p = montage()
        p.derouler(12f)
        assertTrue("la machine n'a pas gagne", p.gagne)
        assertSame("c'est la bille de depart qui a gagne", p.temoin, p.bouton!!.declencheur)
    }

    @Test
    fun `la bille de depart seule dans le bouton ne gagne pas`() {
        // Sans la premiere planche, la bille de depart ne touche jamais la doree. On la lache
        // pourtant juste au-dessus du bouton : elle y tombe, et rien ne s'ouvre.
        val p = Plateau()
        p.poserBouton(x = 0f, bas = 0f)
        p.poserBille(x = 0f, y = 1.5f)
        p.poserTemoin(x = -3f, haut = 3f)
        p.derouler(6f, arreterALaVictoire = false)
        assertFalse("la bille de depart a gagne a la place de la doree", p.gagne)
    }

    @Test
    fun `sans la planche, la bille doree reste sur son pilier`() {
        val p = montage(avecPlanche = false)
        p.derouler(6f, arreterALaVictoire = false)
        assertFalse(p.gagne)
        assertEquals("la doree a bouge sans qu'on l'ait touchee", -1.2f, p.temoin!!.x, 0.02f)
    }

    @Test
    fun `la bille de depart peut sortir du tableau sans que la machine soit perdue`() {
        // Une fois son travail fait, elle a le droit de partir : c'est la doree qu'on suit.
        val p = montage()
        p.derouler(1.5f, arreterALaVictoire = false)
        p.bille!!.x = 99f
        assertFalse("la machine est donnee perdue alors que la doree roule encore", p.billePerdue())
        p.temoin!!.x = 99f
        assertTrue("la doree est sortie et la machine n'est pas perdue", p.billePerdue())
    }

    // ── La chaine ────────────────────────────────────────────────────────────

    @Test
    fun `la chaine compte les pieces atteintes dans l'ordre ou elles le sont`() {
        val p = montage()
        p.derouler(12f)
        assertEquals("la machine pose 3 pieces", 3, p.comptees())
        assertEquals("elles servent toutes", 3, p.chaine())
    }

    @Test
    fun `une piece jamais touchee ne compte pas`() {
        val p = montage()
        // Loin de tout : ni la bille de depart ni la doree n'y passent.
        p.poser(Pieces.rampe(x = -6f, y = 1f, pente = 20f))
        p.poser(Pieces.plot(x = 6f, y = 3f))
        p.derouler(12f)
        assertEquals(5, p.comptees())
        assertEquals("les pieces inutiles ont ete comptees", 3, p.chaine())
    }

    @Test
    fun `deux planches voisines ne font pas une chaine`() {
        // Une rampe scellee est atteinte par la bille, mais elle ne transmet rien : sinon la
        // planche qui en effleure une autre ferait croire a un enchainement.
        val p = Plateau()
        p.poserBouton(x = 7f, bas = 0f)
        p.poserBille(x = -1.5f, y = 3f)
        p.poser(Pieces.rampe(x = -1.5f, y = 1.5f, pente = 20f, longueur = 1.5f))
        // Elle touche la premiere par son bout haut, derriere la bille : jamais sur son chemin.
        p.poser(Pieces.rampe(x = -2.9f, y = 2.0f, pente = 20f, longueur = 1.5f))
        p.derouler(1.2f, arreterALaVictoire = false)
        assertEquals(2, p.comptees())
        assertEquals("une planche atteinte ne doit pas en atteindre une autre", 1, p.chaine())
    }

    @Test
    fun `les dominos qui tombent s'atteignent de proche en proche`() {
        val p = Plateau()
        p.poserBouton(x = 3f, bas = 0f)
        p.poserBille(x = -2.2f, y = 2.5f)
        p.poser(Pieces.rampe(x = -1.5f, y = 1.2f, pente = 14f, longueur = 1.5f))
        repeat(5) { p.poser(Pieces.domino(x = 0.1f + it * 0.3f, bas = 0f)) }
        p.derouler(8f)
        assertEquals("la rampe et les cinq dominos", 6, p.chaine())
    }

    @Test
    fun `une torche ne compte pas dans la note`() {
        val p = Plateau()
        p.poser(Pieces.torche(x = 2f, y = 1f))
        p.poser(Pieces.plot(x = 0f, y = 1f))
        assertEquals(1, p.comptees())
    }

    // ── Les etoiles ──────────────────────────────────────────────────────────

    @Test
    fun `le bareme recompense la chaine et l'absence de piece morte`() {
        assertEquals("portail ferme", 0, Notation.etoiles(gagne = false, atteintes = 9, comptees = 9))
        assertEquals("un simple toboggan", 1, Notation.etoiles(true, atteintes = 2, comptees = 2))
        assertEquals("une chaine honnete", 2, Notation.etoiles(true, atteintes = 3, comptees = 6))
        assertEquals("une belle machine", 3, Notation.etoiles(true, atteintes = 6, comptees = 6))
        assertEquals("une belle chaine avec une piece morte", 2,
            Notation.etoiles(true, atteintes = 6, comptees = 7))
        assertEquals("un chiffre rond ne suffit pas", 2,
            Notation.etoiles(true, atteintes = 4, comptees = 4))
    }

    // ── Le tableau ───────────────────────────────────────────────────────────

    @Test
    fun `un niveau sur trois a une bille doree entre le depart et le bouton`() {
        var vus = 0
        for (n in 1..30) {
            val t = Tableaux.pourNiveau(n)
            assertEquals("le niveau $n", n >= 3 && n % 3 == 0, t.avecTemoin)
            if (!t.avecTemoin) continue
            vus++
            val gauche = minOf(t.billeX, t.boutonX)
            val droite = maxOf(t.billeX, t.boutonX)
            assertTrue("niveau $n : le perchoir n'est pas entre la bille et le bouton",
                t.temoinX > gauche && t.temoinX < droite)
            assertTrue("niveau $n : la doree n'est pas plus basse que le depart",
                t.temoinHaut < t.billeY - 1f)
            assertTrue("niveau $n : le perchoir est trop bas pour qu'une planche passe dessous",
                t.temoinHaut >= 1.6f)
            assertEquals("niveau $n : un socle en plus du perchoir", 0f, t.socleHauteur, 0f)
        }
        assertEquals(10, vus)
    }

    @Test
    fun `le tableau a temoin vide ne se gagne pas tout seul`() {
        for (n in intArrayOf(3, 6, 9, 12, 15, 18)) {
            val m = Tableaux.monter(Tableaux.pourNiveau(n))
            assertNotNull(m.temoin)
            m.derouler(10f, arreterALaVictoire = false)
            assertFalse("le niveau $n se gagne sans rien poser", m.gagne)
        }
    }

    @Test
    fun `la bille posee n'existe pas dans un tableau a temoin`() {
        val avec = Partie(Tableaux.pourNiveau(3))
        val sans = Partie(Tableaux.pourNiveau(1))
        val bille = Pose(TypePiece.BILLE, x = 0f, y = 1.2f)
        assertEquals(Refus.INTERDIT, avec.verifier(bille))
        assertEquals(Refus.OK, sans.verifier(bille))
    }

    @Test
    fun `on ne pose rien sur la bille doree ni sur son pilier`() {
        val t = Tableaux.pourNiveau(3)
        val partie = Partie(t)
        val surLePilier = Pose(TypePiece.BLOC, x = t.temoinX, y = t.temoinHaut / 2f)
        assertEquals(Refus.OCCUPE, partie.verifier(surLePilier))
    }
}
