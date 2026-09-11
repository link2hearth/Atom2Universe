package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La machine complète : une bille, une rampe, une ligne de dominos, un bouton.
 *
 * C'est le test qui dit si le jeu existe. Tout le reste — l'ancrage, le ressort, les
 * événements de contact — n'est que de la plomberie tant que cette chaîne-là ne part
 * pas toute seule du début à la fin.
 *
 * Et il tourne **sans APK et sans écran**, en une seconde : c'est ce qui permet
 * d'essayer une idée de tableau tout de suite au lieu d'attendre une installation.
 */
class InfernaleChaineTest {

    /** Un domino est tombé quand il a dépassé le point de non-retour. */
    private fun tombe(p: Piece) = abs(p.principal.angle) > 0.6f

    /**
     * Le tableau de référence. [dominos] permet d'en retirer pour casser la chaîne
     * exprès — c'est le contrôle négatif.
     */
    private fun montage(dominos: Int = 6, trou: Int = -1): Pair<Plateau, List<Piece>> {
        val p = Plateau()
        p.poser(Pieces.rampe(x = -3f, y = 0.7f, pente = 22f, longueur = 2f))
        val ligne = (0 until dominos)
            .filter { it != trou }
            .map { p.poser(Pieces.domino(x = -1.4f + it * 0.30f, bas = 0f)) }
        p.poserBouton(x = 0.58f, bas = 0f)
        p.poserBille(x = -3.7f, y = 1.6f)
        return p to ligne
    }

    @Test
    fun `la machine part toute seule et gagne`() {
        val (p, ligne) = montage()
        val duree = p.derouler(12f)

        assertTrue("la machine n'a pas gagné", p.gagne)
        assertTrue("elle a mis trop longtemps : $duree s", duree < 6f)
        assertEquals("tous les dominos auraient dû tomber",
            ligne.size, ligne.count { tombe(it) })
    }

    @Test
    fun `un domino manquant casse la chaine`() {
        // **Le test le plus important du lot.** Si on peut retirer une pièce au milieu
        // et gagner quand même, ce n'est pas un casse-tête, c'est une animation : le
        // joueur n'aurait aucune raison de réfléchir à son placement.
        val (p, ligne) = montage(trou = 3)
        p.derouler(12f)

        assertFalse("la chaîne a gagné avec un trou au milieu : ce n'est pas un puzzle",
            p.gagne)
        // Le début de la ligne doit quand même être tombé : c'est bien le trou qui
        // arrête, pas la bille qui aurait raté son coup depuis le départ.
        assertTrue("la bille n'a même pas atteint les premiers dominos",
            ligne.take(3).all { tombe(it) })
        assertTrue("les dominos d'après le trou n'auraient pas dû tomber",
            ligne.drop(3).none { tombe(it) })
    }

    @Test
    fun `sans bouton il ne se passe rien de special`() {
        // Garde-fou : la victoire vient du bouton, pas d'un effet de bord du décor.
        val p = Plateau()
        p.poser(Pieces.rampe(x = -3f, y = 0.7f, pente = 22f, longueur = 2f))
        repeat(6) { p.poser(Pieces.domino(x = -1.4f + it * 0.30f, bas = 0f)) }
        p.poserBille(x = -3.7f, y = 1.6f)
        p.derouler(8f)
        assertFalse("on gagne sans bouton", p.gagne)
    }

    @Test
    fun `le meme montage donne deux fois le meme resultat`() {
        // La reproductibilité n'est pas un luxe : un tableau se partage par sa graine, et
        // une solution qui marche une fois sur deux est une solution qu'on ne peut pas
        // valider. Le moteur range ses contacts dans l'ordre de découverte exprès
        // pour ça — on vérifie que la promesse tient jusqu'ici.
        fun rejouer(): Triple<Float, Float, Float> {
            val (p, _) = montage()
            val t = p.derouler(12f)
            val b = p.bille!!
            return Triple(t, b.x, b.y)
        }
        assertEquals("deux parties identiques divergent", rejouer(), rejouer())
    }

    @Test
    fun `la machine se rejoue apres rearmement`() {
        val (p, _) = montage()
        p.derouler(12f)
        assertTrue(p.gagne)

        p.bouton!!.rearmer()
        assertFalse("le réarmement n'a pas remis le bouton à zéro", p.gagne)
    }

    @Test
    fun `un tableau au repos ne coute presque rien`() {
        // Le sommeil doit rattraper tout ce qui attend son tour. Sans lui, un tableau
        // posé ferait tourner le solveur sur trente corps immobiles à chaque image.
        val (p, ligne) = montage()
        // **Sans s'arreter a la victoire** : le bouton tombe alors que la moitie de la
        // ligne est encore en l'air, et une scene coupee en plein effondrement n'a
        // evidemment rien d'endormi.
        p.derouler(15f, arreterALaVictoire = false)
        val eveilles = p.monde.bodies.count { !it.sleeping && !it.immovable }
        assertTrue("$eveilles corps mobiles ne dorment toujours pas", eveilles <= 2)
        assertEquals("le montage a perdu des dominos en route", 6, ligne.size)
    }
}
