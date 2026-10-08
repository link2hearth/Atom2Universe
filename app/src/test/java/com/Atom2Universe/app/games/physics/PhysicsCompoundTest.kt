package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie les corps composés de plusieurs formes.
 *
 * C'est ce qui permet à une planche et à ses butées de ne faire qu'une seule
 * pièce rigide. Tant qu'elles étaient des corps distincts tenus par une soudure
 * approchée, un boulet coincé entre elles se faisait éjecter à 250 m/s : l'effort
 * devait traverser une liaison molle avant d'atteindre la planche.
 */
class PhysicsCompoundTest {

    @Test
    fun `les formes sont recentrees sur le centre de masse`() {
        // Deux carrés identiques, l'un en -1, l'autre en +1 : le centre est au milieu.
        val symmetric = PhysBody.compound(10f) {
            box(0.25f, 0.25f, -1f, 0f)
            box(0.25f, 0.25f, 1f, 0f)
        }
        assertEquals(-1f, symmetric.localOffsetX(0), 1e-4f)
        assertEquals(1f, symmetric.localOffsetX(1), 1e-4f)

        // Un grand carré à l'origine et un petit décalé : le centre glisse vers le
        // petit, proportionnellement à son aire — un quart ici.
        val lopsided = PhysBody.compound(10f) {
            box(0.5f, 0.5f, 0f, 0f)     // aire 1
            box(0.25f, 0.25f, 2f, 0f)   // aire 0,25
        }
        val expected = (0f * 1f + 2f * 0.25f) / 1.25f
        assertEquals(-expected, lopsided.localOffsetX(0), 1e-4f)
        assertEquals(2f - expected, lopsided.localOffsetX(1), 1e-4f)
    }

    @Test
    fun `une masse excentree est plus dure a faire tourner`() {
        // Théorème de Huygens : une forme éloignée du centre apporte son inertie
        // propre plus sa masse fois le carré de la distance.
        val compact = PhysBody.compound(8f) {
            box(0.25f, 0.25f, 0f, 0f)
            box(0.25f, 0.25f, 0.5f, 0f)
        }
        val spread = PhysBody.compound(8f) {
            box(0.25f, 0.25f, 0f, 0f)
            box(0.25f, 0.25f, 3f, 0f)
        }
        assertTrue(
            "l'écartement ne change rien à l'inertie : ${compact.inertia} contre ${spread.inertia}",
            spread.inertia > compact.inertia * 4f
        )
    }

    /** Un berceau d'un seul tenant : un fond et deux parois. */
    private fun cradle(mass: Float, x: Float, y: Float): PhysBody =
        PhysBody.compound(mass) {
            box(0.6f, 0.06f, 0f, 0f)        // le fond
            box(0.05f, 0.2f, -0.35f, 0.26f) // paroi gauche
            box(0.05f, 0.2f, 0.35f, 0.26f)  // paroi droite
        }.apply {
            this.x = x
            this.y = y
            friction = 0.8f
        }

    @Test
    fun `un boulet pose dans un berceau dun seul tenant y reste`() {
        val world = worldWithGround()
        val c = cradle(30f, 0f, 0.4f).apply {
            lockPosition = true
            lockRotation = true
            refreshMass()
        }
        world.add(c)
        val ball = disc(0.22f, 14f, 0f, 0.75f)
        world.add(ball)

        world.simulate(4f)

        assertTrue("le boulet traverse le fond du berceau : y=${ball.y}", ball.y > 0.5f)
        assertTrue("le boulet est éjecté du berceau : x=${ball.x}", abs(ball.x) < 0.2f)
        assertTrue("le boulet ne s'immobilise pas : v=${ball.speedSq}", ball.speedSq < 0.01f)
    }

    @Test
    fun `un berceau lance ne peut pas ejecter son boulet`() {
        // La situation qui faisait tout exploser : le boulet serré dans son berceau
        // pendant que celui-ci fouette au bout d'un bras chargé.
        val world = worldWithGround(halfWidth = 12f)
        val post = anchorPost(0f, 3f)
        world.add(post)

        val arm = cradle(40f, -2f, 3f)
        world.add(arm)
        world.addJoint(RevoluteJoint.pin(arm, post, 0f, 3f))

        val ball = disc(0.22f, 14f, -2f, 3f + 0.06f + 0.22f)
        world.add(ball)

        var peak = 0f
        val dt = 1f / 120f
        repeat((3f / dt).toInt()) {
            world.stepFrame(dt)
            val v = kotlin.math.sqrt(ball.speedSq)
            if (v > peak) peak = v
        }

        // Le berceau tourne comme un pendule de 2 m et le boulet finit par retomber
        // de trois mètres : une dizaine de mètres par seconde tout au plus. La barre
        // est haute exprès — ce qu'il s'agit d'exclure, ce sont les 250 m/s que
        // produisaient les pièces soudées, pas de mesurer finement une chute.
        assertTrue("le boulet est éjecté par le solveur : $peak m/s", peak < 20f)
    }
}
