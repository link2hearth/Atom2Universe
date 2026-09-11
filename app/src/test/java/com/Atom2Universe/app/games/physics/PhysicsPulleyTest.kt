package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La poulie : la seule liaison du moteur qui transforme une chute en montée.
 *
 * Trois promesses, et une seule d'entre elles est evidente :
 *
 *  1. ce qui descend d'un cote monte de l'autre, dans le rapport annonce ;
 *  2. la corde ne **pousse** jamais — un brin mou ne transmet rien ;
 *  3. une charge permanente ne la fait pas fluer.
 *
 * La troisieme est celle qui a coute le plus cher a obtenir, et elle n'a rien coute a la
 * poulie : mesure faite, la contrainte tient au micron sur six secondes. C'est la butee
 * de glissiere qu'on lui associait qui cedait, et ce test-ci est ce qui a permis de le
 * savoir — sans lui, on aurait passe l'apres-midi a chercher un signe dans le mauvais
 * fichier.
 */
class PhysicsPulleyTest {

    private val PAS = 1f / 120f

    /** Un bati immobile, deux masses pendues sous deux reas. */
    private class Montage(masseA: Float, masseB: Float, val reaY: Float = 2f) {
        val monde = PhysWorld()
        val a = PhysBody(0.1f, 0.1f, masseA).apply { x = -0.4f; y = 1f }
        val b = PhysBody(0.1f, 0.1f, masseB).apply { x = 0.4f; y = 1f }
        val corde: PulleyJoint

        init {
            monde.add(a)
            monde.add(b)
            corde = PulleyJoint.over(-0.4f, reaY, 0.4f, reaY, a, a.x, a.y, b, b.x, b.y)
            monde.addJoint(corde)
        }

        fun tourner(secondes: Float) {
            var t = 0f
            while (t < secondes) {
                monde.stepFrame(1f / 120f)
                t += 1f / 120f
            }
        }
    }

    @Test
    fun `la somme des deux brins ne change pas`() {
        // La definition meme d'une poulie. Tout le reste en decoule.
        val m = Montage(masseA = 1f, masseB = 3f)
        val somme = m.corde.constant
        m.tourner(1.2f)
        val apres = (m.reaY - m.a.y) + (m.reaY - m.b.y)
        assertEquals("la corde s'est allongee ou raccourcie", somme, apres, 0.01f)
    }

    @Test
    fun `le plus lourd descend et fait monter l autre d autant`() {
        val m = Montage(masseA = 1f, masseB = 3f)
        val departA = m.a.y
        val departB = m.b.y
        m.tourner(0.8f)
        val monteeA = m.a.y - departA
        val descenteB = departB - m.b.y
        assertTrue("le lourd n'est pas descendu : $descenteB m", descenteB > 0.2f)
        assertEquals("le leger n'est pas monte d'autant", descenteB, monteeA, 0.02f)
    }

    @Test
    fun `le rapport du palan divise la course`() {
        // A rapport 2, un metre descendu d'un cote n'en fait monter qu'un demi de
        // l'autre — et c'est ce qui permet de lever plus lourd que soi.
        val monde = PhysWorld()
        val a = PhysBody(0.1f, 0.1f, 1f).apply { x = -0.4f; y = 1f }
        val b = PhysBody(0.1f, 0.1f, 6f).apply { x = 0.4f; y = 1f }
        monde.add(a)
        monde.add(b)
        monde.addJoint(PulleyJoint.over(-0.4f, 2f, 0.4f, 2f, a, a.x, a.y, b, b.x, b.y, ratio = 2f))

        val departA = a.y
        val departB = b.y
        var t = 0f
        while (t < 0.8f) {
            monde.stepFrame(PAS)
            t += PAS
        }
        val monteeA = a.y - departA
        val descenteB = departB - b.y
        assertTrue("rien n'a bouge", descenteB > 0.05f)
        assertEquals("le rapport n'est pas respecte",
            2f * descenteB, monteeA, 0.03f + abs(monteeA) * 0.05f)
    }

    @Test
    fun `une corde molle ne transmet rien`() {
        // Les deux corps partent plus haut que la corde ne l'exige : elle pend, donc ils
        // doivent tomber librement jusqu'a ce qu'elle se tende. Une liaison qui pousserait
        // les freinerait des la premiere image.
        val monde = PhysWorld()
        val a = PhysBody(0.1f, 0.1f, 1f).apply { x = -0.4f; y = 1.9f }
        val b = PhysBody(0.1f, 0.1f, 1f).apply { x = 0.4f; y = 1.9f }
        monde.add(a)
        monde.add(b)
        val corde = PulleyJoint.over(-0.4f, 2f, 0.4f, 2f, a, a.x, a.y, b, b.x, b.y)
        corde.constant = 2f
        monde.addJoint(corde)

        val temoin = PhysWorld()
        val libre = PhysBody(0.1f, 0.1f, 1f).apply { x = -0.4f; y = 1.9f }
        temoin.add(libre)

        var t = 0f
        while (t < 0.25f) {
            monde.stepFrame(PAS)
            temoin.stepFrame(PAS)
            t += PAS
        }
        assertEquals("la corde molle a freine la chute", libre.y, a.y, 0.01f)
        assertEquals("la corde molle a exerce une tension", 0f, corde.tension, 1e-4f)
    }

    @Test
    fun `une charge permanente ne fait pas fluer la poulie`() {
        // Le pendant du test precedent : corde tendue, l'autre bout cloue, et une masse
        // qui pend. Elle doit rester exactement ou elle est, aussi longtemps qu'on
        // regarde. Un fluage de cinq millimetres par seconde, ca ne se voit pas en deux
        // secondes et ca vide un godet dans le sol en une minute.
        val monde = PhysWorld()
        val cloue = PhysBody(0.1f, 0.1f, 0f).apply {
            x = 0.4f; y = 1f; lockPosition = true; lockRotation = true; refreshMass()
        }
        val pendu = PhysBody(0.1f, 0.1f, 2f).apply { x = -0.4f; y = 1f }
        monde.add(cloue)
        monde.add(pendu)
        monde.addJoint(
            PulleyJoint.over(-0.4f, 2f, 0.4f, 2f, pendu, pendu.x, pendu.y, cloue, cloue.x, cloue.y)
        )

        var t = 0f
        while (t < 2f) {
            monde.stepFrame(PAS)
            t += PAS
        }
        val a2s = pendu.y
        while (t < 6f) {
            monde.stepFrame(PAS)
            t += PAS
        }
        assertEquals("la poulie flue sous une charge permanente", a2s, pendu.y, 1e-3f)
    }
}
