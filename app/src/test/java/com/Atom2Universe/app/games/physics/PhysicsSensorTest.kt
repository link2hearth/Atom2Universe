package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Les zones de détection et les événements de contact.
 *
 * C'est la pièce qui manquait au moteur pour un jeu de mécanismes : savoir **quand**
 * deux choses se touchent, et pas seulement les empêcher de se traverser.
 */
class PhysicsSensorTest {

    /** Une zone de détection immobile, qui ne repousse rien. */
    private fun zone(x: Float, y: Float, halfW: Float, halfH: Float): PhysBody =
        PhysBody(halfW, halfH, 0f).apply {
            this.x = x
            this.y = y
            lockPosition = true
            lockRotation = true
            isSensor = true
            refreshMass()
        }

    /** Recopie les événements du pas, puisque le moteur réemploie ses objets. */
    private class Seen(val a: PhysBody?, val b: PhysBody?, val begin: Boolean, val sensor: Boolean)

    private fun PhysWorld.collect(seconds: Float, dt: Float = 1f / 120f): List<Seen> {
        val out = ArrayList<Seen>()
        repeat((seconds / dt).toInt()) {
            stepFrame(dt)
            for (e in contactEvents) out.add(Seen(e.a, e.b, e.begin, e.sensor))
        }
        return out
    }

    @Test
    fun `une zone de detection ne freine pas ce qui la traverse`() {
        // Deux mondes identiques, l'un avec une zone en travers de la chute, l'autre sans.
        // Une zone qui ralentirait sa cible, même d'un millimètre, en ferait un obstacle.
        fun fall(withZone: Boolean): Float {
            val w = PhysWorld()
            w.linearDamping = 0f
            val ball = disc(0.2f, 1f, 0f, 10f)
            w.add(ball)
            if (withZone) w.add(zone(0f, 5f, 1f, 0.5f))
            w.simulate(1.2f)
            return ball.y
        }

        val libre = fall(false)
        val traverse = fall(true)
        assertEquals("la zone a freiné la boule", libre, traverse, 1e-6f)
        assertTrue("la boule n'a pas traversé la zone", traverse < 4.5f)
    }

    @Test
    fun `une zone annonce l entree puis la sortie`() {
        val w = PhysWorld()
        w.linearDamping = 0f
        val ball = disc(0.2f, 1f, 0f, 10f)
        val z = zone(0f, 5f, 1f, 0.5f)
        w.add(ball)
        w.add(z)

        val seen = w.collect(1.5f).filter { it.sensor }
        assertEquals("il fallait une entrée et une sortie", 2, seen.size)
        assertTrue("le premier événement doit être une entrée", seen[0].begin)
        assertFalse("le second doit être une sortie", seen[1].begin)
        assertSame("la zone n'a pas reconnu la boule", ball, seen[0].a?.let {
            if (it === z) seen[0].b else it
        })
    }

    @Test
    fun `une boule trop rapide pour l image est quand meme vue`() {
        // Le cas qui justifie tout le reste. À 60 m/s et 60 Hz, la boule avance d'un
        // mètre par image ; la zone en fait vingt centimètres. Aucune image ne tombe à
        // l'intérieur, et interroger les positions ne verrait jamais rien. Le
        // découpage en sous-pas, lui, passe forcément dedans.
        val w = PhysWorld()
        w.gravity = 0f
        w.linearDamping = 0f
        val ball = disc(0.05f, 1f, -5f, 0f)
        ball.vx = 60f
        val z = zone(0f, 0f, 0.1f, 1f)
        w.add(ball)
        w.add(z)

        var entree = 0
        var sortie = 0
        repeat(20) {
            w.stepFrame(1f / 60f)
            for (e in w.contactEvents) if (e.sensor) if (e.begin) entree++ else sortie++
        }
        assertEquals("la zone a raté la boule rapide", 1, entree)
        assertEquals("la zone n'a pas vu la boule ressortir", 1, sortie)
    }

    @Test
    fun `une zone ne detecte que ce qu elle accepte de voir`() {
        // Le filtrage par catégorie marche sur les zones comme sur les corps : c'est
        // ainsi qu'une arrivée ne se déclenche que sur la boule et pas sur les débris.
        val w = PhysWorld()
        w.linearDamping = 0f
        val boule = disc(0.2f, 1f, -1f, 10f).apply { category = 2 }
        val debris = disc(0.2f, 1f, 1f, 10f).apply { category = 4 }
        val z = zone(0f, 5f, 3f, 0.5f).apply { collidesWith = 2 }
        w.add(boule)
        w.add(debris)
        w.add(z)

        val vus = w.collect(1.5f).filter { it.sensor && it.begin }
        assertEquals("la zone a vu autre chose que la boule", 1, vus.size)
        assertSame(boule, if (vus[0].a === z) vus[0].b else vus[0].a)
    }

    @Test
    fun `une zone ne tient pas eveille ce qu elle observe`() {
        // Une zone posée sur le sol ne doit pas empêcher ce qui s'y arrête de dormir :
        // sinon la moindre arrivée coûterait, à elle seule, le prix d'un décor entier.
        val w = worldWithGround()
        w.sleepEnabled = true
        val caisse = box(0.3f, 0.3f, 5f, 0f, 0.31f)
        w.add(caisse)
        w.add(zone(0f, 0.3f, 1f, 1f))
        w.simulate(4f)
        assertTrue("la zone a tenu la caisse éveillée", caisse.sleeping)
    }

    @Test
    fun `un dormeur reste vu par la zone qui le contient`() {
        // Le piège du sommeil : un corps endormi n'est plus revu par la recherche de
        // paires, donc son couple expirerait et le jeu croirait qu'il est ressorti —
        // alors qu'il est toujours là, simplement immobile.
        val w = worldWithGround()
        w.sleepEnabled = true
        val caisse = box(0.3f, 0.3f, 5f, 0f, 0.31f)
        w.add(caisse)
        w.add(zone(0f, 0.3f, 1f, 1f))

        var dedans = false
        repeat(600) {
            w.stepFrame(1f / 120f)
            for (e in w.contactEvents) if (e.sensor) dedans = e.begin
        }
        assertTrue("la caisse est endormie mais la zone ne la voit plus", dedans)
        assertTrue("le test ne prouve rien si rien ne dort", caisse.sleeping)
    }

    @Test
    fun `un corps compose ne declenche qu un seul evenement`() {
        // Un contact du solveur est un couple de formes ; un événement de jeu est un
        // couple de corps. Trois boîtes posées sur le sol, c'est une arrivée, pas trois.
        val w = worldWithGround()
        w.linearDamping = 0f
        val traineau = PhysBody.compound(12f) {
            box(0.4f, 0.1f, -0.5f, 0f)
            box(0.4f, 0.1f, 0.5f, 0f)
            box(0.2f, 0.3f, 0f, 0.3f)
        }.apply { x = 0f; y = 1.5f }
        w.add(traineau)

        val arrivees = w.collect(2f).count { it.begin && !it.sensor }
        assertEquals("le corps composé a déclenché plusieurs arrivées", 1, arrivees)
    }

    @Test
    fun `retirer un corps n annonce pas sa separation`() {
        // Un corps retiré par le jeu ne doit pas revenir dans les événements : il n'y
        // apprendrait rien, et l'événement lui rendrait un objet qu'il a déjà jeté.
        val w = worldWithGround()
        val caisse = box(0.3f, 0.3f, 5f, 0f, 0.31f)
        w.add(caisse)
        w.simulate(1f)

        w.remove(caisse)
        w.stepFrame(1f / 120f)
        for (e in w.contactEvents) {
            assertNull("la caisse retirée est revenue dans les événements",
                e.other(caisse)?.let { caisse })
        }
    }

    @Test
    fun `les evenements d une image sont oublies a la suivante`() {
        val w = worldWithGround()
        w.add(box(0.3f, 0.3f, 5f, 0f, 3f))
        var vuUnePremiereFois = false
        repeat(240) {
            w.stepFrame(1f / 120f)
            if (w.contactEvents.isNotEmpty()) vuUnePremiereFois = true
        }
        assertTrue("aucun contact n'a jamais été annoncé", vuUnePremiereFois)
        // Une fois la caisse posée et stabilisée, plus rien ne se passe.
        w.simulate(1f)
        assertTrue("des événements traînent alors que rien ne bouge",
            w.contactEvents.isEmpty())
    }

    @Test
    fun `une zone ne change rien au monde quand il n y en a pas`() {
        // Garde-fou de non-régression : le moteur doit donner exactement les mêmes
        // nombres qu'avant sur une scène sans capteur.
        fun chute(): Float {
            val w = worldWithGround()
            val b = box(0.25f, 0.25f, 8f, 0.1f, 4f)
            w.add(b)
            w.simulate(3f)
            return b.y
        }
        assertEquals(chute(), chute(), 0f)
        assertTrue("la caisse n'est pas retombée sur le sol", abs(chute() - 0.25f) < 0.05f)
    }
}
