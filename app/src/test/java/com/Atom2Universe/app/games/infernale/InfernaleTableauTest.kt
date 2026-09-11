package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Le generateur de tableaux, dans sa version simple.
 *
 * ## Ce qu'il promet maintenant, et ce qu'il ne promet plus
 *
 * Il construisait la machine a la place du joueur, la faisait tourner, elaguait ce qui ne
 * servait a rien, et ne distribuait que l'inventaire de ce qui restait. Il ne fait plus
 * rien de tout cela : il pose la bille, il pose le bouton, et le joueur recoit toute la
 * panoplie. Trois cents lignes en moins, et surtout la bonne question posee au joueur.
 *
 * La solubilite, elle, a cesse d'etre une question. Avec toute la panoplie dans les mains
 * du joueur — huit rampes scellees qui tiennent en l'air ou l'on veut — on descend de
 * n'importe ou a n'importe ou. Il n'y a donc rien a prouver ici, et surtout pas en
 * ecrivant un resolveur : ce qui reste a verifier, ce sont les regles de dessin du
 * tableau, et le seul accident qu'elles ne couvrent pas.
 */
class InfernaleTableauTest {

    @Test
    fun `un tableau vide ne se gagne pas tout seul`() {
        // Le seul garde-fou que la geometrie ne couvre pas, et le seul que le generateur
        // mesure encore. Si le decor suffisait, l'inventaire ne servirait a rien et le
        // tableau ne serait qu'une animation.
        for (niveau in 1..30) {
            val monde = Tableaux.monter(Tableaux.pourNiveau(niveau))
            monde.derouler(10f)
            assertFalse("le niveau $niveau se gagne sans poser une seule piece", monde.gagne)
        }
    }

    @Test
    fun `le bouton est toujours plus bas que la bille`() {
        // La regle de dessin qui porte tout le reste. Un bouton plus haut que la bille
        // demanderait de la faire monter, ce que la panoplie ne sait faire que sur
        // soixante-dix centimetres avec la poulie : le tableau serait injouable sans que
        // rien a l'ecran ne le dise.
        for (niveau in 1..40) {
            val t = Tableaux.pourNiveau(niveau)
            assertTrue(
                "le niveau $niveau place le bouton a ${t.boutonBas} pour une bille a ${t.billeY}",
                t.boutonBas <= t.billeY - 1.2f
            )
            assertTrue(
                "le niveau $niveau colle la bille au bouton (${abs(t.boutonX - t.billeX)} m)",
                abs(t.boutonX - t.billeX) >= 2.4f
            )
        }
    }

    @Test
    fun `tout tient dans le cadre`() {
        // Le cadre est a la fois ce qu'on voit et ce ou l'on peut batir. La bille ou le
        // bouton qui en sortirait serait invisible, et le tableau paraitrait casse.
        for (niveau in 1..40) {
            val t = Tableaux.pourNiveau(niveau)
            assertTrue("niveau $niveau : la bille est hors cadre",
                t.billeX > t.cadreMinX && t.billeX < t.cadreMaxX)
            assertTrue("niveau $niveau : le bouton est hors cadre",
                t.boutonX > t.cadreMinX && t.boutonX < t.cadreMaxX)
            assertTrue("niveau $niveau : la bille depasse le plafond",
                t.billeY < t.cadreMaxY)
            assertTrue("niveau $niveau : cadre trop etroit (${t.cadreMaxX - t.cadreMinX} m)",
                t.cadreMaxX - t.cadreMinX >= 6f)
        }
    }

    @Test
    fun `la meme graine rend exactement le meme tableau`() {
        // Un tableau se partage par son numero : deux joueurs qui tapent le meme doivent
        // avoir le meme casse-tete, sinon le numero ne veut rien dire.
        for (niveau in listOf(1, 7, 12, 33)) {
            val a = Tableaux.pourNiveau(niveau)
            val b = Tableaux.pourNiveau(niveau)
            assertEquals("le niveau $niveau ne rend pas deux fois la meme bille",
                a.billeX to a.billeY, b.billeX to b.billeY)
            assertEquals("le niveau $niveau ne rend pas deux fois le meme bouton",
                a.boutonX to a.boutonBas, b.boutonX to b.boutonBas)
            assertEquals("le niveau $niveau ne rend pas deux fois le meme socle",
                a.socleHauteur, b.socleHauteur, 0f)
        }
    }

    @Test
    fun `deux niveaux differents donnent des tableaux differents`() {
        val vus = (1..30).map { n ->
            val t = Tableaux.pourNiveau(n)
            Triple(t.billeX, t.boutonX, t.boutonBas)
        }.toSet()
        assertTrue("le generateur se repete : ${vus.size} tableaux distincts sur 30",
            vus.size >= 28)
    }

    @Test
    fun `la bille part des deux cotes`() {
        // Sans ce tirage, tous les tableaux se lisent de gauche a droite et se ressemblent
        // au premier coup d'oeil, quelles que soient les cotes.
        val versLaDroite = (1..30).count { n ->
            val t = Tableaux.pourNiveau(n)
            t.boutonX > t.billeX
        }
        assertTrue("le sens ne varie pas : $versLaDroite tableaux sur 30 vont a droite",
            versLaDroite in 8..22)
    }

    @Test
    fun `le socle apparait sans jamais depasser la bille`() {
        // Un socle est ce qui rend un bouton en hauteur interessant : il faut poser la
        // bille **sur** quelque chose. Mais un socle plus haut que la bille demanderait de
        // la faire monter, donc il est borne a la construction.
        var perches = 0
        for (niveau in 1..30) {
            val t = Tableaux.pourNiveau(niveau)
            if (t.socleHauteur > 0f) {
                perches++
                assertTrue("le niveau $niveau perche le bouton a ${t.socleHauteur} m " +
                    "pour une bille a ${t.billeY} m", t.socleHauteur <= t.billeY - 1.2f)
                assertEquals("le socle et le bouton ne sont pas a la meme hauteur",
                    t.socleHauteur, t.boutonBas, 1e-4f)
            }
        }
        assertTrue("aucun tableau ne perche son bouton", perches >= 5)
    }

    @Test
    fun `la panoplie est la meme partout et contient les neuf pieces`() {
        // Le joueur a tout, tout le temps : c'est le coeur du reglage de difficulte, qui
        // porte desormais sur la geometrie du probleme et pas sur ce qu'on retire.
        for (type in TypePiece.entries) {
            assertTrue("la panoplie ne contient pas de $type",
                (Panoplie.COMPLET[type] ?: 0) > 0)
        }
        for (niveau in listOf(1, 5, 20)) {
            assertEquals("le niveau $niveau n'a pas la panoplie complete",
                Panoplie.COMPLET, Tableaux.pourNiveau(niveau).inventaire)
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
        // Garde-fou contre l'oubli : ajouter un type de piece sans l'ajouter a `creer` ne
        // compilerait meme pas, mais rien ne garantit qu'il produise quelque chose.
        for (type in TypePiece.entries) {
            val piece = Pose(type, x = 0f, y = 0.5f, reglage = 20f).creer()
            assertNotNull("le type $type ne produit aucune piece", piece)
            assertTrue("le type $type ne produit aucun corps", piece.corps.isNotEmpty())
            assertEquals("le type $type ne se souvient pas de son ancrage",
                type.ancrage, piece.ancrage)
        }
    }
}
