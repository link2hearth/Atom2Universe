package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La partie : poser, reprendre, deplacer, lancer, arreter.
 *
 * Le tableau est un bac a sable : il demarre vide, sans bille, sans bouton, sans objectif.
 */
class InfernalePartieTest {

    /** Un domino, la piece a tout faire des tests de placement. */
    private fun domino(x: Float = -1f) = Pose(TypePiece.DOMINO, x = x, y = 0f)

    @Test
    fun `un tableau neuf est vide`() {
        val p = Partie()
        assertEquals("des pieces sont deja posees", 0, p.posees)
        assertTrue("des pieces trainent dans le monde", p.plateau.pieces.isEmpty())
        assertFalse("la partie demarre lancee", p.lancee)
    }

    @Test
    fun `poser compte la piece et la retirer la decompte`() {
        val p = Partie()
        assertEquals(Refus.OK, p.poser(domino()))
        assertEquals(1, p.posees)

        assertTrue(p.reprendre())
        assertEquals("la piece retiree est restee posee", 0, p.posees)
    }

    @Test
    fun `on pose autant de pieces qu on veut`() {
        val p = Partie()
        repeat(40) {
            assertEquals("pose $it refusee", Refus.OK, p.poser(domino(x = -4f + it * 0.12f)))
        }
        assertEquals(40, p.posees)
    }

    @Test
    fun `on ne pose pas une piece sur une autre`() {
        val p = Partie()
        assertEquals(Refus.OK, p.poser(domino(x = -3f)))
        assertEquals("deux pieces au meme endroit ont ete acceptees", Refus.OCCUPE,
            p.poser(domino(x = -3f)))
    }

    @Test
    fun `on ne pose pas une piece hors du cadre`() {
        val p = Partie()
        assertEquals("une piece a ete posee au-dela du bord droit", Refus.HORS_TABLEAU,
            p.poser(domino(x = 50f)))
        assertEquals("une piece a ete posee sous le sol", Refus.HORS_TABLEAU,
            p.poser(domino(x = 0f).copy(y = -3f)))
    }

    @Test
    fun `deplacer garde le rang de la piece`() {
        // Le rang compte : l'interface designe une piece par son indice, et la partie tient
        // sa liste de poses en parallele de celle du plateau. Une piece qui change de rang
        // en bougeant ferait deplacer la voisine au geste suivant.
        val p = Partie()
        repeat(5) { assertEquals(Refus.OK, p.poser(domino(x = -1f + it * 0.35f))) }

        val vise = p.placees()[1].copy(x = -2.5f)
        assertEquals(Refus.OK, p.deplacer(1, vise))
        assertEquals("la piece deplacee a change de rang", vise, p.placees()[1])
        assertEquals("deplacer a change le nombre de pieces posees", 5, p.posees)
        assertEquals("le plateau et la liste des poses ont diverge",
            -2.5f, p.plateau.pieces[1].principal.x, 1e-4f)
    }

    @Test
    fun `on ne pose plus rien une fois la machine lancee`() {
        val p = Partie()
        p.poser(domino(x = -1f))
        p.lancer()
        assertEquals(Refus.DEJA_LANCEE, p.poser(domino(x = -3f)))
        assertFalse("on a pu reprendre une piece en pleine course", p.reprendre())
    }

    @Test
    fun `rien ne bouge tant qu on n a pas lance`() {
        // Le premier des deux temps : pendant la pose, le monde est fige. Sinon la bille
        // serait tombee avant qu'on ait pose la rampe.
        val p = Partie()
        p.poser(Pose(TypePiece.BILLE, x = 0f, y = 3f))
        val bille = p.plateau.pieces.single().principal
        val depart = bille.x to bille.y
        repeat(600) { p.avancer(1f / 120f) }
        assertEquals("la bille est tombee avant le lancement", depart, bille.x to bille.y)
        assertEquals("le chrono tourne avant le lancement", 0f, p.chrono, 0f)
    }

    @Test
    fun `une bille posee tombe quand on lance`() {
        val p = Partie()
        p.poser(Pose(TypePiece.BILLE, x = 0f, y = 3f))
        val bille = p.plateau.pieces.single().principal
        val haut = bille.y
        p.lancer()
        repeat(120) { p.avancer(1f / 120f) }
        assertTrue("la bille n'est pas tombee", bille.y < haut - 1f)
    }

    @Test
    fun `arreter remet les pieces ou elles etaient posees`() {
        val p = Partie()
        p.poser(Pose(TypePiece.RAMPE, x = -1f, y = 2f, reglage = 20f, taille = 1.5f))
        p.poser(Pose(TypePiece.BILLE, x = -1.4f, y = 3f))
        val avant = p.placees()
        p.lancer()
        repeat(600) { p.avancer(1f / 120f) }
        val billeApres = p.plateau.pieces[1].principal.y

        p.arreter()

        assertFalse("arreter laisse la machine lancee", p.lancee)
        assertEquals("arreter a change le montage", avant, p.placees())
        assertTrue("la bille n'est pas revenue a son depart",
            p.plateau.pieces[1].principal.y > billeApres + 0.5f)
        assertEquals("le chrono n'est pas remis a zero", 0f, p.chrono, 0f)
        assertEquals("on ne peut plus poser apres l'arret", Refus.OK, p.poser(domino(x = 3f)))
    }

    @Test
    fun `vider efface tout`() {
        val p = Partie()
        repeat(3) { p.poser(domino(x = -3f + it)) }
        p.vider()
        assertEquals("le tableau vide garde des pieces", 0, p.posees)
        assertTrue(p.plateau.pieces.isEmpty())
    }

    @Test
    fun `poserTout rejoue une sauvegarde et saute ce qui ne passe plus`() {
        val p = Partie()
        p.poserTout(listOf(domino(x = -1f), domino(x = -1f), domino(x = 99f), domino(x = 2f)))
        assertEquals("seules les deux poses valides devaient rester", 2, p.posees)
    }

    @Test
    fun `verifier ne pose rien`() {
        // L'interface appelle `verifier` a chaque image pendant qu'un doigt traine une
        // piece : il ne doit strictement rien changer au monde.
        val p = Partie()
        val pose = Pose(TypePiece.RAMPE, x = 0f, y = 3f, reglage = 20f)
        val corps = p.plateau.monde.bodies.size
        repeat(50) { assertEquals(Refus.OK, p.verifier(pose)) }
        assertEquals("verifier a laisse des corps dans le monde",
            corps, p.plateau.monde.bodies.size)
        assertEquals("verifier a pose une piece", 0, p.posees)
    }

    private fun plaque(x: Float) = Pose(TypePiece.PLAQUE, x = x, y = 0.03f)
    private fun canon(x: Float) = Pose(TypePiece.CANON, x = x, y = 1f)

    @Test
    fun `on ne relie qu une plaque et un canon`() {
        val p = Partie()
        p.poser(plaque(-3f)); p.poser(canon(0f)); p.poser(domino(x = 3f))
        assertTrue(p.lier(0, 1))
        assertEquals(listOf(Lien(0, 1)), p.liens())
        assertFalse("on a relie une plaque a un domino", p.lier(0, 2))
        assertFalse("on a relie un canon a lui-meme", p.lier(1, 1))
        // Dans l'autre sens, c'est le meme lien : il se defait.
        assertTrue(p.lier(1, 0))
        assertTrue(p.liens().isEmpty())
    }

    @Test
    fun `retirer une piece renumerote les liens`() {
        val p = Partie()
        p.poser(domino(x = 3f)); p.poser(plaque(-3f)); p.poser(canon(0f))
        p.lier(1, 2)
        p.reprendre(0)
        assertEquals("les rangs n'ont pas recule", listOf(Lien(0, 1)), p.liens())
        p.reprendre(1)
        assertTrue("le lien du canon retire est reste", p.liens().isEmpty())
    }

    @Test
    fun `arreter et recharger gardent les liens`() {
        val p = Partie()
        p.poser(plaque(-3f)); p.poser(canon(0f)); p.lier(0, 1)
        p.lancer()
        p.arreter()
        assertEquals(listOf(Lien(0, 1)), p.liens())

        val q = Partie()
        q.charger(listOf(domino(x = 99f), plaque(-3f), canon(0f)), listOf(Lien(1, 2)))
        assertEquals("la pose refusee decale les rangs", listOf(Lien(0, 1)), q.liens())
    }

    @Test
    fun `une plaque se relie aussi a un ventilateur`() {
        val p = Partie()
        p.poser(plaque(-3f)); p.poser(Pose(TypePiece.VENTILATEUR, x = 2f, y = 0.4f))
        assertTrue(p.lier(0, 1))
        assertEquals(listOf(Lien(0, 1)), p.liens())
    }
}
