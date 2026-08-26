package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie le mini moteur physique du jeu d'équilibre : une brique doit se poser
 * et rester au repos, une pile centrée doit tenir, et une pile trop décalée doit
 * basculer (c'est tout l'intérêt du jeu).
 */
class BalancePhysicsTest {

    private fun worldWithGround(): PhysWorld {
        val w = PhysWorld()
        val ground = PhysBody(5f, 0.5f, 0f).apply {
            x = 0f
            y = -0.5f
            lockPosition = true
            lockRotation = true
            friction = 0.8f
            refreshMass()
        }
        w.add(ground)
        return w
    }

    private fun PhysWorld.simulate(seconds: Float) {
        val dt = 1f / 120f
        repeat((seconds / dt).toInt()) { step(dt) }
    }

    private fun box(halfW: Float, halfH: Float, mass: Float, x: Float, y: Float) =
        PhysBody(halfW, halfH, mass).apply {
            this.x = x
            this.y = y
            friction = 0.62f
        }

    @Test
    fun `une brique lachee se pose sur le sol et sy immobilise`() {
        val world = worldWithGround()
        val b = box(0.2f, 0.15f, 10f, 0f, 1.2f)
        world.add(b)

        world.simulate(3f)

        assertTrue("la brique traverse le sol : y=${b.y}", b.y > 0.10f)
        assertTrue("la brique flotte : y=${b.y}", b.y < 0.20f)
        assertTrue("la brique ne s'immobilise pas : v=${b.vy}", abs(b.vy) < 0.05f)
        assertTrue("la brique pivote sans raison : ${b.angle}", abs(b.angle) < 0.08f)
    }

    @Test
    fun `une grosse brique bien centree sur une petite tient debout`() {
        val world = worldWithGround()
        // Petite brique étroite de 2 kg, puis 25 kg large posés pile au centre.
        val small = box(0.075f, 0.12f, 2f, 0f, 0.12f)
        val heavy = box(0.25f, 0.11f, 25f, 0f, 0.35f)
        world.add(small)
        world.add(heavy)

        world.simulate(4f)

        assertTrue("la pile centrée bascule : ${heavy.angle}", abs(heavy.angle) < 0.25f)
        assertTrue("la grosse brique est tombée : y=${heavy.y}", heavy.y > 0.25f)
    }

    @Test
    fun `une grosse brique decalee sur une petite bascule`() {
        val world = worldWithGround()
        // Même duo, mais le centre de gravité des 25 kg tombe hors de l'appui.
        val small = box(0.075f, 0.12f, 2f, 0f, 0.12f)
        val heavy = box(0.25f, 0.11f, 25f, 0.22f, 0.36f)
        world.add(small)
        world.add(heavy)

        world.simulate(4f)

        assertTrue(
            "la pile déséquilibrée reste en l'air : y=${heavy.y}, angle=${heavy.angle}",
            heavy.y < 0.25f || abs(heavy.angle) > 0.3f
        )
    }

    @Test
    fun `une pile de briques reste stable`() {
        val world = worldWithGround()
        val boxes = (0 until 4).map { i ->
            box(0.18f, 0.09f, 6f, 0f, 0.09f + i * 0.185f).also { world.add(it) }
        }

        world.simulate(4f)

        for ((i, b) in boxes.withIndex()) {
            assertTrue("brique $i a glissé : x=${b.x}", abs(b.x) < 0.06f)
            assertTrue("brique $i a pivoté : ${b.angle}", abs(b.angle) < 0.2f)
        }
    }
}
