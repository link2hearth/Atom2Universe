package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Vérifie les cordes.
 *
 * Une corde ne fait qu'une chose, mais elle doit la faire exactement : **retenir
 * sans jamais pousser**. Molle, elle ne transmet rien du tout ; tendue, elle tire.
 * C'est cette asymétrie qui la distingue d'une barre, et c'est aussi ce qui la
 * rend stable là où une tige rigide s'emballe.
 */
class PhysicsRopeTest {

    @Test
    fun `une corde retient un poids a sa longueur`() {
        val world = PhysWorld()
        val hook = anchorPost(0f, 6f)
        world.add(hook)

        val weight = PhysBody(0.2f, 0.2f, 40f).apply { x = 0f; y = 5.9f }
        world.add(weight)
        world.addJoint(DistanceJoint.rope(weight, hook, 2f))

        world.simulate(5f)

        val d = hypot(weight.x - hook.x, weight.y - hook.y)
        assertTrue("la corde s'allonge : %.3f m pour 2 m".format(d), d < 2.02f)
        assertTrue("le poids ne pend pas sous son crochet : y=${weight.y}", weight.y < 4.2f)
        assertTrue("le poids ne s'immobilise pas : v=${weight.speedSq}", weight.speedSq < 0.01f)
    }

    @Test
    fun `une corde molle ne pousse pas`() {
        // Le poids démarre bien plus près que la longueur de corde : elle doit le
        // laisser tomber librement, comme si elle n'existait pas.
        val world = PhysWorld()
        val hook = anchorPost(0f, 10f)
        world.add(hook)

        val weight = PhysBody(0.15f, 0.15f, 5f).apply { x = 0f; y = 9.5f }
        world.add(weight)
        val rope = DistanceJoint.rope(weight, hook, 4f)
        world.addJoint(rope)

        // Un dixième de seconde de chute : la corde a encore trois mètres de mou.
        val dt = 1f / 240f
        repeat(24) { world.stepFrame(dt) }

        val freeFall = 0.5f * world.gravity * 0.1f * 0.1f
        assertTrue("la corde molle freine la chute", 9.5f - weight.y > freeFall * 0.85f)
        assertTrue("la corde molle transmet une tension : ${rope.tension}", rope.tension == 0f)
        assertTrue("la corde molle ne se signale pas comme molle", rope.isSlack)
    }

    @Test
    fun `une barre pousse la ou une corde ne fait que tirer`() {
        fun settle(asRope: Boolean): PhysBody {
            val world = PhysWorld()
            val hook = anchorPost(0f, 10f)
            world.add(hook)
            val weight = PhysBody(0.15f, 0.15f, 5f).apply { x = 0f; y = 9.5f }
            world.add(weight)
            world.addJoint(
                DistanceJoint(weight, hook).apply {
                    length = 4f
                    rope = asRope
                }
            )
            world.simulate(4f)
            return weight
        }

        val onRope = settle(true)
        val onBar = settle(false)

        // Les deux finissent à quatre mètres sous le crochet : la corde parce
        // qu'elle a retenu la chute, la barre parce qu'elle a aussi dû pousser le
        // poids pour l'y amener. Ce qui les sépare, c'est le chemin, pas la fin.
        assertTrue("la corde ne tient pas : y=${onRope.y}", abs(onRope.y - 6f) < 0.05f)
        assertTrue("la barre ne tient pas : y=${onBar.y}", abs(onBar.y - 6f) < 0.05f)
    }

    @Test
    fun `une corde a plusieurs maillons pend et ne sallonge pas`() {
        val world = PhysWorld()
        val left = anchorPost(-3f, 8f)
        val right = anchorPost(3f, 8f)
        world.add(left)
        world.add(right)

        // Six mètres d'écart, mais huit mètres de corde : elle doit pendre.
        val rope = Rope.between(
            world, left, -3f, 8f, right, 3f, 8f,
            segments = 8, totalMass = 4f, slack = 0.33f
        )

        world.simulate(6f)

        val lowest = rope.links.minOf { it.y }
        assertTrue("la corde ne pend pas : point bas à $lowest", lowest < 7f)
        assertTrue("la corde tombe au sol : point bas à $lowest", lowest > 4.5f)

        // Aucun maillon ne doit s'être détaché.
        var prevX = -3f
        var prevY = 8f
        for (l in rope.links) {
            val d = hypot(l.x - prevX, l.y - prevY)
            assertTrue("un maillon s'est détaché : $d m", d < rope.length / 8f + 0.1f)
            prevX = l.x
            prevY = l.y
        }
    }

    @Test
    fun `une corde chargee ne fait pas exploser la simulation`() {
        // Le cas qui compte : une masse lourde au bout d'une corde, tirée par un
        // bras qui fouette. C'est exactement la fronde d'un trébuchet.
        val world = worldWithGround(halfWidth = 40f)
        val post = anchorPost(0f, 6f)
        world.add(post)

        val arm = PhysBody(3f, 0.07f, 12f).apply { x = -3f; y = 6f }
        world.add(arm)
        world.addJoint(RevoluteJoint.pin(arm, post, 0f, 6f))

        val weight = PhysBody(0.35f, 0.35f, 250f).apply { x = 0.9f; y = 6.4f }
        world.add(weight)
        val (w1, w2) = RevoluteJoint.weld(weight, arm, 0.55f, 6.4f, 1.25f, 6.4f)
        world.addJoint(w1)
        world.addJoint(w2)

        val ball = PhysBody.circle(0.16f, 6f).apply { x = -7.5f; y = 0.16f }
        world.add(ball)
        world.addJoint(DistanceJoint.between(arm, -6f, 6f, ball, ball.x, ball.y, rope = true))

        var fastest = 0f
        val dt = 1f / 120f
        repeat((5f / dt).toInt()) {
            world.stepFrame(dt)
            val v = kotlin.math.sqrt(ball.speedSq)
            if (v > fastest) fastest = v
        }

        // 250 kg qui descendent d'un mètre, c'est deux mille cinq cents joules ;
        // un boulet de 6 kg ne peut pas dépasser la trentaine de mètres par seconde.
        assertTrue("le boulet sort à $fastest m/s : la corde a explosé", fastest < 35f)
        assertTrue("la corde n'a rien lancé du tout : $fastest m/s", fastest > 4f)
    }
}
