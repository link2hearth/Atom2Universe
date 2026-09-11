package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La partie : poser, reprendre, deplacer, lancer, recommencer.
 *
 * Le test qui compte est `poser la solution du generateur gagne vraiment` — il ferme la
 * boucle : le generateur promet qu'un tableau est soluble, et c'est en passant par les
 * memes gestes que le joueur qu'on verifie sa promesse.
 *
 * ## Pourquoi les regles de placement se testent sur un tableau ecrit a la main
 *
 * Elles se testaient sur un tableau tire au hasard, et elles ont casse le jour ou le
 * generateur a cesse de produire toujours la meme forme de machine : plus aucune graine
 * ne garantissait un domino dans l'inventaire, ni un metre de libre a l'endroit ou le
 * test voulait poser sa piece. Un test de regle n'a pas a dependre de ce que le hasard a
 * bien voulu donner — il se donne son propre tableau, et il dit exactement ce qu'il
 * mesure.
 */
class InfernalePartieTest {

    /** Un tableau ecrit a la main : cinq dominos, un bouton, de la place autour. */
    private fun tableauEssai(): Tableau = Tableau(
        graine = 0L,
        billeX = -3.5f,
        billeY = 3.5f,
        boutonX = 2.5f,
        boutonBas = 0f,
        solution = (0 until 5).map { Pose(TypePiece.DOMINO, x = -1f + it * 0.3f, y = 0f) }
    )

    private fun essai() = Partie(tableauEssai())

    private fun partie(graine: Long = 1L) = Partie(Tableaux.genererSurement(graine))

    @Test
    fun `une partie commence avec l inventaire du tableau et rien de pose`() {
        val p = partie()
        assertEquals("l'inventaire de depart n'est pas celui du tableau",
            p.tableau.inventaire, p.stock())
        assertTrue("des pieces sont deja posees", p.placees().isEmpty())
        assertFalse("la partie demarre lancee", p.lancee)
        assertFalse("la partie demarre gagnee", p.gagne)
    }

    @Test
    fun `poser decompte le stock et reprendre le rend`() {
        val p = essai()
        val pose = p.tableau.solution.first()
        val avant = p.stock(TypePiece.DOMINO)

        assertEquals(Refus.OK, p.poser(pose))
        assertEquals("le stock n'a pas baisse", avant - 1, p.stock(TypePiece.DOMINO))
        assertEquals(1, p.placees().size)

        assertTrue(p.reprendre())
        assertEquals("le stock n'est pas revenu", avant, p.stock(TypePiece.DOMINO))
        assertTrue("la piece reprise est restee posee", p.placees().isEmpty())
    }

    @Test
    fun `on ne pose pas plus que ce qu on a`() {
        val p = essai()
        val modele = p.tableau.solution.first()
        val stock = p.stock(TypePiece.DOMINO)
        // On les pose tous, bien ecartes pour qu'aucun ne gene l'autre.
        repeat(stock) {
            assertEquals("pose $it refusee", Refus.OK,
                p.poser(modele.copy(x = -4f + it * 0.6f)))
        }
        assertEquals("il devrait etre en rupture", 0, p.stock(TypePiece.DOMINO))
        assertEquals("on a pu poser un domino de trop", Refus.PLUS_EN_STOCK,
            p.poser(modele.copy(x = 1.5f)))
    }

    @Test
    fun `on ne pose pas une piece sur une autre`() {
        val p = essai()
        val modele = p.tableau.solution.first()
        assertEquals(Refus.OK, p.poser(modele.copy(x = -3f)))
        assertEquals("deux pieces au meme endroit ont ete acceptees", Refus.OCCUPE,
            p.poser(modele.copy(x = -3f)))
    }

    @Test
    fun `on ne pose pas une piece hors du cadre`() {
        val p = essai()
        val modele = p.tableau.solution.first()
        assertEquals("une piece a ete posee au-dela du bord droit", Refus.HORS_TABLEAU,
            p.poser(modele.copy(x = 50f)))
        assertEquals("une piece a ete posee sous le sol", Refus.HORS_TABLEAU,
            p.poser(modele.copy(x = 0f, y = -3f)))
    }

    @Test
    fun `on ne pose pas une piece mobile dans la zone du bouton`() {
        // **Le trou par lequel on gagnerait sans rien construire.** Un domino pose dans
        // la zone declencherait le capteur des le premier pas de simulation : le tableau
        // serait gagne avant d'avoir commence.
        val p = essai()
        val modele = p.tableau.solution.first()
        assertEquals("un domino pose dans la zone du bouton a ete accepte", Refus.OCCUPE,
            p.poser(modele.copy(x = p.tableau.boutonX)))
    }

    @Test
    fun `une piece scellee peut couvrir la zone du bouton`() {
        // Le pendant du test precedent. Une rampe scellee appartient au decor : le
        // capteur ne la voit pas, donc rien ne justifie de refuser le placement, et
        // l'interdire condamnerait tout un pan du tableau sans raison lisible.
        val p = Partie(
            Tableau(
                graine = 0L, billeX = -3f, billeY = 3f, boutonX = 1f, boutonBas = 0.5f,
                solution = listOf(Pose(TypePiece.RAMPE, x = 0f, y = 1.5f, reglage = 15f))
            )
        )
        assertEquals("une rampe scellee a ete refusee au-dessus du bouton", Refus.OK,
            p.poser(Pose(TypePiece.RAMPE, x = 1f, y = 0.55f, reglage = 0f)))
    }

    @Test
    fun `deplacer garde le rang de la piece`() {
        // Le rang compte : l'interface designe une piece par son indice, et la partie
        // tient sa liste de poses en parallele de celle du plateau. Une piece qui change
        // de rang en bougeant ferait deplacer la voisine au geste suivant.
        val p = essai()
        for (pose in p.tableau.solution) assertEquals(Refus.OK, p.poser(pose))

        val vise = p.placees()[1].copy(x = -2.5f)
        assertEquals(Refus.OK, p.deplacer(1, vise))
        assertEquals("la piece deplacee a change de rang", vise, p.placees()[1])
        assertEquals("le stock a bouge alors qu'on n'a fait que deplacer",
            0, p.stock(TypePiece.DOMINO))
        assertEquals("le plateau et la liste des poses ont divergé",
            -2.5f, p.plateau.pieces[1].principal.x, 1e-4f)
    }

    @Test
    fun `on ne pose plus rien une fois la machine lancee`() {
        val p = essai()
        val modele = p.tableau.solution.first()
        p.lancer()
        assertEquals(Refus.DEJA_LANCEE, p.poser(modele.copy(x = -3f)))
        assertFalse("on a pu reprendre une piece en pleine course", p.reprendre())
    }

    @Test
    fun `rien ne bouge tant qu on n a pas lance`() {
        // Le premier des deux temps : pendant la pose, le monde est fige. Sinon le
        // premier domino serait tombe avant qu'on ait pose le second.
        val p = essai()
        p.poser(p.tableau.solution.first())
        val bille = p.plateau.bille!!
        val depart = bille.x to bille.y
        repeat(600) { p.avancer(1f / 120f) }
        assertEquals("la bille est tombee avant le lancement", depart, bille.x to bille.y)
        assertEquals("le chrono tourne avant le lancement", 0f, p.chrono, 0f)
    }

    @Test
    fun `poser la solution du generateur gagne vraiment`() {
        // **Le test qui ferme la boucle.** Le generateur affirme qu'un tableau est
        // soluble ; on le verifie ici en passant par les memes gestes que le joueur —
        // meme inventaire, memes refus, meme lancement.
        for (graine in 1L..15L) {
            val p = partie(graine)
            for (pose in p.tableau.solution) {
                assertEquals("graine $graine : la solution a ete refusee ($pose)",
                    Refus.OK, p.poser(pose))
            }
            assertTrue("graine $graine : l'inventaire n'est pas vide apres la solution",
                p.toutPose)
            p.lancer()
            repeat(1500) { p.avancer(1f / 120f) }
            assertTrue("graine $graine : la solution verifiee ne gagne pas en partie",
                p.gagne)
        }
    }

    @Test
    fun `montrer la solution gagne aussi`() {
        val p = partie(4L)
        p.montrerSolution()
        assertEquals("la solution montree n'a pas tout pose",
            p.tableau.solution.size, p.placees().size)
        p.lancer()
        repeat(1500) { p.avancer(1f / 120f) }
        assertTrue("la solution montree ne gagne pas", p.gagne)
    }

    @Test
    fun `rejouer garde les pieces et remet la machine a zero`() {
        val p = partie()
        for (pose in p.tableau.solution) p.poser(pose)
        p.lancer()
        repeat(1500) { p.avancer(1f / 120f) }
        assertTrue(p.gagne)
        val billeApres = p.plateau.bille!!.x

        p.rejouer()

        assertFalse("rejouer n'a pas remis la victoire a zero", p.gagne)
        assertFalse("rejouer laisse la machine lancee", p.lancee)
        assertEquals("rejouer a perdu des pieces",
            p.tableau.solution.size, p.placees().size)
        assertNotEquals("la bille n'est pas revenue a son depart",
            billeApres, p.plateau.bille!!.x)
        assertEquals("le chrono n'est pas remis a zero", 0f, p.chrono, 0f)

        // Et ca regagne, a l'identique.
        p.lancer()
        repeat(1500) { p.avancer(1f / 120f) }
        assertTrue("le tableau rejoue ne gagne plus", p.gagne)
    }

    @Test
    fun `raser rend tout au stock`() {
        val p = partie()
        for (pose in p.tableau.solution) p.poser(pose)
        assertTrue(p.toutPose)
        p.tableauRase()
        assertTrue("le tableau rase garde des pieces", p.placees().isEmpty())
        assertEquals("le stock n'est pas revenu au complet",
            p.tableau.inventaire, p.stock())
    }

    @Test
    fun `verifier ne pose rien`() {
        // L'interface appellera `verifier` a chaque image pendant qu'un doigt traine une
        // piece : il ne doit strictement rien changer au monde.
        val p = partie()
        val pose = p.tableau.solution.first()
        val corps = p.plateau.monde.bodies.size
        repeat(50) { assertEquals(Refus.OK, p.verifier(pose)) }
        assertEquals("verifier a laisse des corps dans le monde",
            corps, p.plateau.monde.bodies.size)
        assertTrue("verifier a consomme du stock", p.placees().isEmpty())
    }
}
