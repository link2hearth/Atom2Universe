package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le test le plus important du moteur : **il ne doit jamais créer d'énergie**.
 *
 * Toutes les explosions rencontrées jusqu'ici — un boulet à 250 m/s sorti d'une
 * machine qui n'en stocke que dix mille joules — venaient de là. Un solveur qui
 * corrige les positions en poussant sur les vraies vitesses invente de l'élan à
 * chaque image ; sur une chaîne de liaisons un peu raide, cet élan s'emballe.
 *
 * Depuis que les corrections de position passent par des vitesses fantômes, la
 * quantité mesurée ici ne peut plus que décroître.
 */
class PhysicsEnergyTest {

    /** Énergie mécanique totale du monde : cinétique plus pesanteur. */
    private fun PhysWorld.mechanicalEnergy(): Float {
        var e = 0f
        for (bd in bodies) {
            if (!bd.inWorld || bd.invMass == 0f) continue
            e += 0.5f * bd.mass * bd.speedSq
            e += 0.5f * bd.inertia * bd.omega * bd.omega
            e += bd.mass * gravity * bd.y
        }
        return e
    }

    @Test
    fun `un pendule accroche a un axe ne prend jamais de vitesse`() {
        val world = PhysWorld()
        val post = anchorPost(0f, 5f)
        world.add(post)

        // Un bras horizontal accroché par un bout : il va balancer.
        val arm = PhysBody(1.5f, 0.08f, 20f).apply { x = -1.5f; y = 5f }
        world.add(arm)
        world.addJoint(RevoluteJoint.pin(arm, post, 0f, 5f))

        val start = world.mechanicalEnergy()
        var worst = start
        val dt = 1f / 120f
        repeat((12f / dt).toInt()) {
            world.stepFrame(dt)
            val e = world.mechanicalEnergy()
            if (e > worst) worst = e
        }

        assertTrue(
            "le pendule gagne de l'énergie : %.1f J au départ, %.1f J au pire".format(start, worst),
            worst < start + kotlin.math.abs(start) * 0.02f + 1f
        )
    }

    @Test
    fun `une chaine de liaisons chargee ne semballe pas`() {
        // La configuration qui faisait exploser le trébuchet : un bras lourd
        // articulé, une corde au bout, et une masse au bout de la corde.
        val world = worldWithGround(halfWidth = 40f)
        val post = anchorPost(0f, 6f)
        world.add(post)

        val arm = PhysBody(3f, 0.07f, 15f).apply { x = -3f; y = 6f; friction = 0.6f }
        world.add(arm)
        world.addJoint(RevoluteJoint.pin(arm, post, 0f, 6f))

        // Trois cents kilos soudés près de l'axe, côté opposé : le moteur.
        val weight = PhysBody(0.4f, 0.4f, 300f).apply { x = 0.8f; y = 6.5f }
        world.add(weight)
        val (w1, w2) = RevoluteJoint.weld(weight, arm, 0.4f, 6.5f, 1.2f, 6.5f)
        world.addJoint(w1)
        world.addJoint(w2)

        val ball = PhysBody.circle(0.16f, 6f).apply { x = -8f; y = 0.16f }
        world.add(ball)
        world.addJoint(
            DistanceJoint.between(arm, -6f, 6f, ball, ball.x, ball.y, rope = true)
        )

        val start = world.mechanicalEnergy()
        var worst = start
        var fastest = 0f
        val dt = 1f / 120f
        repeat((6f / dt).toInt()) {
            world.stepFrame(dt)
            val e = world.mechanicalEnergy()
            if (e > worst) worst = e
            val v = kotlin.math.sqrt(ball.speedSq)
            if (v > fastest) fastest = v
        }

        assertTrue(
            "la chaîne gagne de l'énergie : %.0f J au départ, %.0f J au pire".format(start, worst),
            worst < start + kotlin.math.abs(start) * 0.05f + 10f
        )
        // Garde-fou lisible : l'énergie disponible ne peut pas donner plus de ça.
        val ceiling = kotlin.math.sqrt(2f * kotlin.math.abs(start) / ball.mass)
        assertTrue(
            "le boulet sort à %.0f m/s, au-delà des %.0f m/s que l'énergie autorise"
                .format(fastest, ceiling),
            fastest < ceiling
        )
    }

    @Test
    fun `une pile de caisses ne se met pas a vibrer`() {
        // Les corrections de position ne doivent pas non plus donner d'élan à une
        // pile au repos : c'était le vieux défaut du biais de Baumgarte.
        val world = worldWithGround()
        val boxes = (0 until 6).map { i ->
            box(0.2f, 0.1f, 8f, 0f, 0.1f + i * 0.205f).also { world.add(it) }
        }

        world.simulate(5f)

        for ((i, b) in boxes.withIndex()) {
            assertTrue("la caisse $i vibre : v=${kotlin.math.sqrt(b.speedSq)}", b.speedSq < 0.002f)
            assertTrue("la caisse $i a glissé : x=${b.x}", kotlin.math.abs(b.x) < 0.08f)
        }
        assertTrue("la pile ne se met pas au repos", world.isAtRest())
    }
}
