package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Les tableaux sauvegardes : ce qu'on range est exactement ce qu'on retrouve. */
class InfernaleSauvegardeTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private fun sauvegardes() = Sauvegardes(dossier.newFolder("infernale"))

    @Test
    fun `une pose fait l aller-retour en texte`() {
        for (type in TypePiece.entries) {
            val pose = Pose(type, x = -1.25f, y = 2.5f, reglage = 17.5f, taille = 1.8f, taille2 = 0.6f, miroir = true, force = 22f)
            assertEquals("le type $type ne revient pas intact", pose, PoseTexte.lire(PoseTexte.ecrire(pose)))
        }
    }

    @Test
    fun `une ligne abimee est ignoree`() {
        assertNull(PoseTexte.lire(""))
        assertNull(PoseTexte.lire("RAMPE 1 2"))
        assertNull(PoseTexte.lire("INCONNUE 1 2 3 4 5 0"))
        assertNull(PoseTexte.lire("RAMPE un 2 3 4 5 0"))
        assertNull(PoseTexte.lire("RAMPE NaN 2 3 4 5 0"))
    }

    @Test
    fun `un tableau neuf est vide et se retrouve`() {
        val s = sauvegardes()
        val t = s.creer("Mon bac", maintenant = 1000L)
        val relu = s.charger(t.id)!!
        assertEquals("Mon bac", relu.nom)
        assertTrue(relu.poses.isEmpty())
        assertEquals(t.graine, relu.graine)
    }

    @Test
    fun `le montage enregistre revient dans le meme ordre`() {
        val s = sauvegardes()
        val t = s.creer("Chaine", maintenant = 1000L)
        val poses = listOf(
            Pose(TypePiece.RAMPE, 0f, 3f, reglage = 20f, taille = 2f),
            Pose(TypePiece.BILLE, -0.3f, 4f),
            Pose(TypePiece.TAPIS, 2f, 1f, miroir = true)
        )
        s.enregistrer(TableauSauve(t.id, t.nom, t.graine, poses, 2000L))
        assertEquals(poses, s.charger(t.id)!!.poses)
    }

    @Test
    fun `une partie rouverte retrouve ses pieces`() {
        val s = sauvegardes()
        val t = s.creer("Test", maintenant = 1000L)
        val partie = Partie(t.graine)
        partie.poser(Pose(TypePiece.RAMPE, 0f, 3f, reglage = 20f))
        partie.poser(Pose(TypePiece.DOMINO, 2f, 0f))
        s.enregistrer(TableauSauve(t.id, t.nom, t.graine, partie.placees(), 2000L))

        val rouverte = Partie(t.graine).also { it.poserTout(s.charger(t.id)!!.poses) }
        assertEquals(partie.placees(), rouverte.placees())
    }

    @Test
    fun `lister met le plus recent en premier`() {
        val s = sauvegardes()
        val vieux = s.creer("Vieux", maintenant = 1000L)
        val recent = s.creer("Recent", maintenant = 5000L)
        assertEquals(listOf(recent.id, vieux.id), s.lister().map { it.id })
    }

    @Test
    fun `deux creations au meme instant ne s ecrasent pas`() {
        val s = sauvegardes()
        val a = s.creer("A", maintenant = 1000L)
        val b = s.creer("B", maintenant = 1000L)
        assertNotEquals(a.id, b.id)
        assertEquals(2, s.lister().size)
    }

    @Test
    fun `renommer garde le montage`() {
        val s = sauvegardes()
        val t = s.creer("Ancien", maintenant = 1000L)
        val poses = listOf(Pose(TypePiece.PLOT, 1f, 1f))
        s.enregistrer(TableauSauve(t.id, t.nom, t.graine, poses, 1500L))
        s.renommer(t.id, "Nouveau\nnom")
        val relu = s.charger(t.id)!!
        assertEquals("Nouveau nom", relu.nom)
        assertEquals(poses, relu.poses)
    }

    @Test
    fun `supprimer retire le tableau`() {
        val s = sauvegardes()
        val t = s.creer("A jeter", maintenant = 1000L)
        s.supprimer(t.id)
        assertNull(s.charger(t.id))
        assertTrue(s.lister().isEmpty())
    }

    @Test
    fun `les liens font l aller-retour`() {
        val s = sauvegardes()
        val t = s.creer("Liens", maintenant = 1000L)
        val poses = listOf(Pose(TypePiece.PLAQUE, 0f, 0.03f), Pose(TypePiece.CANON, 2f, 1f))
        s.enregistrer(TableauSauve(t.id, t.nom, t.graine, poses, 2000L, listOf(Lien(0, 1))))
        assertEquals(listOf(Lien(0, 1)), s.charger(t.id)!!.liens)
        s.renommer(t.id, "Autre")
        assertEquals("renommer a perdu les liens", listOf(Lien(0, 1)), s.charger(t.id)!!.liens)
    }
}
