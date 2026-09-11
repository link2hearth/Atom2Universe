package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La partie : poser, reprendre, deplacer, lancer, recommencer.
 *
 * ## Pourquoi les regles de placement se testent sur un tableau ecrit a la main
 *
 * Un test de regle n'a pas a dependre de ce que le hasard a bien voulu donner : il se donne
 * son propre tableau, et il dit exactement ce qu'il mesure.
 */
class InfernalePartieTest {

    /** Un tableau ecrit a la main : une bille a gauche, un bouton a droite, de la place. */
    private fun tableauEssai(): Tableau = Tableau(
        graine = 0L,
        billeX = -3.5f,
        billeY = 4.2f,
        boutonX = 2.5f,
        boutonBas = 0f
    )

    private fun essai() = Partie(tableauEssai())

    /** Un domino, la piece a tout faire des tests de placement. */
    private fun domino(x: Float = -1f) = Pose(TypePiece.DOMINO, x = x, y = 0f)

    private fun partie(niveau: Int = 1) = Partie(Tableaux.pourNiveau(niveau))

    @Test
    fun `une partie commence avec toute la panoplie et rien de pose`() {
        val p = partie()
        assertEquals("l'inventaire de depart n'est pas la panoplie complete",
            Panoplie.COMPLET, p.stock())
        assertEquals("des pieces sont deja posees", 0, p.posees)
        assertFalse("la partie demarre lancee", p.lancee)
        assertFalse("la partie demarre gagnee", p.gagne)
    }

    @Test
    fun `poser decompte le stock et reprendre le rend`() {
        val p = essai()
        val avant = p.stock(TypePiece.DOMINO)

        assertEquals(Refus.OK, p.poser(domino()))
        assertEquals("le stock n'a pas baisse", avant - 1, p.stock(TypePiece.DOMINO))
        assertEquals(1, p.posees)

        assertTrue(p.reprendre())
        assertEquals("le stock n'est pas revenu", avant, p.stock(TypePiece.DOMINO))
        assertEquals("la piece reprise est restee posee", 0, p.posees)
    }

    @Test
    fun `on ne pose pas plus que ce qu on a`() {
        val p = essai()
        val stock = p.stock(TypePiece.DOMINO)
        // On les pose tous, bien ecartes pour qu'aucun ne gene l'autre.
        repeat(stock) {
            assertEquals("pose $it refusee", Refus.OK, p.poser(domino(x = -4f + it * 0.5f)))
        }
        assertEquals("il devrait etre en rupture", 0, p.stock(TypePiece.DOMINO))
        assertEquals("on a pu poser un domino de trop", Refus.PLUS_EN_STOCK,
            p.poser(domino(x = 1.5f)))
    }

    @Test
    fun `on ne pose pas une piece sur une autre`() {
        val p = essai()
        assertEquals(Refus.OK, p.poser(domino(x = -3f)))
        assertEquals("deux pieces au meme endroit ont ete acceptees", Refus.OCCUPE,
            p.poser(domino(x = -3f)))
    }

    @Test
    fun `on ne pose pas une piece hors du cadre`() {
        val p = essai()
        assertEquals("une piece a ete posee au-dela du bord droit", Refus.HORS_TABLEAU,
            p.poser(domino(x = 50f)))
        assertEquals("une piece a ete posee sous le sol", Refus.HORS_TABLEAU,
            p.poser(domino(x = 0f).copy(y = -3f)))
    }

    @Test
    fun `on ne pose pas une piece mobile dans la zone du bouton`() {
        // **Le trou par lequel on gagnerait sans rien construire.** Un domino pose dans la
        // zone declencherait le capteur des le premier pas de simulation : le tableau serait
        // gagne avant d'avoir commence.
        val p = essai()
        assertEquals("un domino pose dans la zone du bouton a ete accepte", Refus.OCCUPE,
            p.poser(domino(x = p.tableau.boutonX)))
    }

    @Test
    fun `une piece scellee peut couvrir la zone du bouton`() {
        // Le pendant du test precedent. Une rampe scellee appartient au decor : le capteur
        // ne la voit pas, donc rien ne justifie de refuser le placement, et l'interdire
        // condamnerait tout un pan du tableau sans raison lisible.
        val p = Partie(
            Tableau(graine = 0L, billeX = -3f, billeY = 4f, boutonX = 1f, boutonBas = 0.5f)
        )
        assertEquals("une rampe scellee a ete refusee au-dessus du bouton", Refus.OK,
            p.poser(Pose(TypePiece.RAMPE, x = 1f, y = 0.55f, reglage = 0f)))
    }

    @Test
    fun `deplacer garde le rang de la piece`() {
        // Le rang compte : l'interface designe une piece par son indice, et la partie tient
        // sa liste de poses en parallele de celle du plateau. Une piece qui change de rang
        // en bougeant ferait deplacer la voisine au geste suivant.
        val p = essai()
        val stock = p.stock(TypePiece.DOMINO)
        repeat(5) { assertEquals(Refus.OK, p.poser(domino(x = -1f + it * 0.35f))) }

        val vise = p.placees()[1].copy(x = -2.5f)
        assertEquals(Refus.OK, p.deplacer(1, vise))
        assertEquals("la piece deplacee a change de rang", vise, p.placees()[1])
        assertEquals("le stock a bouge alors qu'on n'a fait que deplacer",
            stock - 5, p.stock(TypePiece.DOMINO))
        assertEquals("le plateau et la liste des poses ont diverge",
            -2.5f, p.plateau.pieces[1].principal.x, 1e-4f)
    }

    @Test
    fun `on ne pose plus rien une fois la machine lancee`() {
        val p = essai()
        p.lancer()
        assertEquals(Refus.DEJA_LANCEE, p.poser(domino(x = -3f)))
        assertFalse("on a pu reprendre une piece en pleine course", p.reprendre())
    }

    @Test
    fun `rien ne bouge tant qu on n a pas lance`() {
        // Le premier des deux temps : pendant la pose, le monde est fige. Sinon le premier
        // domino serait tombe avant qu'on ait pose le second.
        val p = essai()
        p.poser(domino())
        val bille = p.plateau.bille!!
        val depart = bille.x to bille.y
        repeat(600) { p.avancer(1f / 120f) }
        assertEquals("la bille est tombee avant le lancement", depart, bille.x to bille.y)
        assertEquals("le chrono tourne avant le lancement", 0f, p.chrono, 0f)
    }

    /** Trois rampes en escalier sous la bille : de quoi faire tourner une machine. */
    private fun batirUnPeu(p: Partie): Int {
        val sens = if (p.tableau.boutonX >= p.tableau.billeX) 1f else -1f
        repeat(3) { i ->
            p.poser(
                Pose(
                    TypePiece.RAMPE,
                    x = p.tableau.billeX + sens * (0.7f + i * 1.6f),
                    y = p.tableau.billeY - 0.8f - i * 0.7f,
                    reglage = sens * 22f,
                    taille = 1.5f
                )
            )
        }
        return p.posees
    }

    @Test
    fun `rejouer garde les pieces et remet la machine a zero`() {
        val p = partie()
        val posees = batirUnPeu(p)
        assertTrue("rien n'a pu etre pose", posees > 0)
        p.lancer()
        repeat(1200) { p.avancer(1f / 120f) }
        val billeApres = p.plateau.bille!!.x

        p.rejouer()

        assertFalse("rejouer laisse la machine lancee", p.lancee)
        assertEquals("rejouer a perdu des pieces", posees, p.posees)
        assertNotEquals("la bille n'est pas revenue a son depart",
            billeApres, p.plateau.bille!!.x)
        assertEquals("le chrono n'est pas remis a zero", 0f, p.chrono, 0f)
    }

    @Test
    fun `raser rend tout au stock`() {
        val p = partie()
        assertTrue("rien n'a pu etre pose", batirUnPeu(p) > 0)
        p.tableauRase()
        assertEquals("le tableau rase garde des pieces", 0, p.posees)
        assertEquals("le stock n'est pas revenu au complet", Panoplie.COMPLET, p.stock())
    }

    @Test
    fun `verifier ne pose rien`() {
        // L'interface appelle `verifier` a chaque image pendant qu'un doigt traine une
        // piece : il ne doit strictement rien changer au monde.
        val p = partie()
        val pose = Pose(
            TypePiece.RAMPE,
            x = p.tableau.billeX,
            y = p.tableau.billeY - 1f,
            reglage = 20f
        )
        val corps = p.plateau.monde.bodies.size
        repeat(50) { assertEquals(Refus.OK, p.verifier(pose)) }
        assertEquals("verifier a laisse des corps dans le monde",
            corps, p.plateau.monde.bodies.size)
        assertEquals("verifier a consomme du stock", 0, p.posees)
    }
}
