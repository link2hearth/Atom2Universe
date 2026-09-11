package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La partie : poser, reprendre, lancer, recommencer.
 *
 * Le test qui compte est le dernier — **poser la solution du generateur gagne
 * vraiment**. Il ferme la boucle : le generateur promet qu'un tableau est soluble, et
 * c'est en passant par les memes gestes que le joueur qu'on verifie sa promesse.
 */
class InfernalePartieTest {

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
        val p = partie()
        val pose = p.tableau.solution.first { it.type == TypePiece.DOMINO }
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
        val p = partie()
        val modele = p.tableau.solution.first { it.type == TypePiece.DOMINO }
        val stock = p.stock(TypePiece.DOMINO)
        // On les pose tous, bien ecartes pour qu'aucun ne gene l'autre.
        repeat(stock) {
            assertEquals("pose $it refusee", Refus.OK,
                p.poser(modele.copy(x = -4f + it * 0.6f)))
        }
        assertEquals("il devrait etre en rupture", 0, p.stock(TypePiece.DOMINO))
        assertEquals("on a pu poser un domino de trop", Refus.PLUS_EN_STOCK,
            p.poser(modele.copy(x = 3f)))
    }

    @Test
    fun `on ne pose pas une piece sur une autre`() {
        val p = partie()
        val modele = p.tableau.solution.first { it.type == TypePiece.DOMINO }
        assertEquals(Refus.OK, p.poser(modele.copy(x = -3f)))
        assertEquals("deux pieces au meme endroit ont ete acceptees", Refus.OCCUPE,
            p.poser(modele.copy(x = -3f)))
    }

    @Test
    fun `on ne pose pas une piece hors du tableau`() {
        val p = partie()
        val modele = p.tableau.solution.first { it.type == TypePiece.DOMINO }
        assertEquals("une piece a ete posee au-dela du bord droit", Refus.HORS_TABLEAU,
            p.poser(modele.copy(x = 50f)))
        assertEquals("une piece a ete posee sous le sol", Refus.HORS_TABLEAU,
            p.poser(modele.copy(x = 0f, y = -3f)))
    }

    @Test
    fun `on ne pose plus rien une fois la machine lancee`() {
        val p = partie()
        val modele = p.tableau.solution.first { it.type == TypePiece.DOMINO }
        p.lancer()
        assertEquals(Refus.DEJA_LANCEE, p.poser(modele.copy(x = -3f)))
        assertFalse("on a pu reprendre une piece en pleine course", p.reprendre())
    }

    @Test
    fun `rien ne bouge tant qu on n a pas lance`() {
        // Le premier des deux temps : pendant la pose, le monde est fige. Sinon le
        // premier domino serait tombe avant qu'on ait pose le second.
        val p = partie()
        val pose = p.tableau.solution.first { it.type == TypePiece.DOMINO }
        p.poser(pose)
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
