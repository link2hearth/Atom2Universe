package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le generateur de tableaux.
 *
 * Une seule promesse compte, et elle est dure a tenir : **tout tableau propose a un
 * joueur doit avoir une solution**. Rien n'est plus decourageant qu'un casse-tete
 * insoluble, et rien n'est plus difficile a prouver apres coup. La fabrique s'y prend
 * donc a l'envers — elle construit la solution, la fait tourner, et ne garde que ce
 * qui a gagne.
 */
class InfernaleTableauTest {

    @Test
    fun `cinquante graines donnent cinquante tableaux solubles`() {
        // Le test qui porte tout. Il monte et fait tourner cinquante machines completes,
        // et il tourne en quelques secondes : c'est le genre de garantie qu'on ne peut
        // se payer que parce que le jeu se simule sans ecran.
        var trouves = 0
        for (graine in 1L..50L) {
            val tableau = Tableaux.generer(graine) ?: continue
            trouves++
            val monde = Tableaux.monter(tableau, avecSolution = true)
            monde.derouler(12f)
            assertTrue("la graine $graine a produit un tableau que sa propre solution " +
                "ne gagne pas", monde.gagne)
        }
        assertTrue("seulement $trouves tableaux sur 50 : le generateur echoue trop souvent",
            trouves >= 45)
    }

    @Test
    fun `la meme graine rend exactement le meme tableau`() {
        // Un tableau se partage par son numero : deux joueurs qui tapent la meme graine
        // doivent avoir le meme casse-tete, sinon le numero ne veut rien dire.
        for (graine in listOf(1L, 7L, 42L, 1234L)) {
            val a = Tableaux.genererSurement(graine)
            val b = Tableaux.genererSurement(graine)
            assertEquals("la graine $graine ne donne pas deux fois la meme bille",
                a.billeX to a.billeY, b.billeX to b.billeY)
            assertEquals("la graine $graine ne donne pas deux fois le meme bouton",
                a.boutonX, b.boutonX, 0f)
            assertEquals("la graine $graine ne donne pas deux fois la meme solution",
                a.solution, b.solution)
        }
    }

    @Test
    fun `deux graines differentes donnent des tableaux differents`() {
        val tableaux = (1L..20L).map { Tableaux.genererSurement(it) }
        val distincts = tableaux.map { it.solution }.toSet()
        assertTrue("le generateur se repete : ${distincts.size} tableaux distincts sur 20",
            distincts.size >= 18)
    }

    @Test
    fun `un tableau vide ne se gagne pas tout seul`() {
        // Le pendant du test precedent, et il est indispensable. Si le decor seul
        // suffisait a declencher le bouton, l'inventaire ne servirait a rien et le
        // « generateur » ne genererait qu'une animation.
        for (graine in 1L..20L) {
            val tableau = Tableaux.genererSurement(graine)
            val monde = Tableaux.monter(tableau, avecSolution = false)
            monde.derouler(12f)
            assertFalse("la graine $graine se gagne sans poser une seule piece",
                monde.gagne)
        }
    }

    @Test
    fun `l inventaire correspond exactement a la solution`() {
        val tableau = Tableaux.genererSurement(3L)
        assertEquals("l'inventaire ne compte pas les memes pieces que la solution",
            tableau.pieces, tableau.inventaire.values.sum())
        assertTrue("un tableau sans rampe", TypePiece.RAMPE in tableau.inventaire)
        assertTrue("un tableau sans domino", TypePiece.DOMINO in tableau.inventaire)
    }

    @Test
    fun `les tableaux restent de taille raisonnable`() {
        // La contrainte de cout : peu de dominos et gros. Une ligne de trente dominos
        // fins couterait plus cher qu'un chateau au moteur, pour le meme effet.
        for (graine in 1L..30L) {
            val tableau = Tableaux.genererSurement(graine)
            val dominos = tableau.inventaire[TypePiece.DOMINO] ?: 0
            assertTrue("la graine $graine demande $dominos dominos", dominos in 4..8)
            assertTrue("la graine $graine demande ${tableau.pieces} pieces en tout",
                tableau.pieces <= 10)
        }
    }

    @Test
    fun `une pose se recree a l identique`() {
        // Une pose est un souvenir : la recreer deux fois doit donner deux pieces
        // identiques, sinon rien ne se sauvegarde ni ne se rejoue.
        val pose = Pose(TypePiece.RAMPE, x = 1.5f, y = 0.8f, reglage = 24f)
        val a = pose.creer()
        val b = pose.creer()
        assertEquals(a.type, b.type)
        assertEquals(a.principal.x, b.principal.x, 0f)
        assertEquals(a.principal.y, b.principal.y, 0f)
        assertEquals(a.principal.angle, b.principal.angle, 0f)
        assertEquals("une rampe doit etre scellee", Ancrage.SCELLE, a.ancrage)
    }

    @Test
    fun `chaque type de piece sait se recreer depuis une pose`() {
        // Garde-fou contre l'oubli : ajouter un type de piece sans l'ajouter a `creer`
        // ne compilerait meme pas, mais rien ne garantit qu'il produise quelque chose.
        for (type in TypePiece.entries) {
            val piece = Pose(type, x = 0f, y = 0.5f, reglage = 20f).creer()
            assertNotNull("le type $type ne produit aucune piece", piece)
            assertTrue("le type $type ne produit aucun corps", piece.corps.isNotEmpty())
            assertEquals("le type $type ne se souvient pas de son ancrage",
                type.ancrage, piece.ancrage)
        }
    }
}
