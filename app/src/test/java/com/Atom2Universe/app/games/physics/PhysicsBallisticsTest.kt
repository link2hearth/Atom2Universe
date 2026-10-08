package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vérifie qu'un corps lancé en l'air se comporte comme la physique le dit, et
 * **pas comme le découpage du pas le décide**.
 *
 * C'est le garde-fou d'un bug qui a coûté cher : l'amortissement ambiant était un
 * facteur appliqué par pas (0,999), réglé du temps où une image de jeu valait un
 * pas de simulation. Depuis les sous-pas adaptatifs, une image rapide en vaut
 * jusqu'à trente-deux, et le même facteur freinait alors trente-deux fois plus. Un
 * boulet de trébuchet perdait 38 % de sa vitesse par seconde et tombait à la
 * moitié de sa portée — un frottement invisible, dont l'intensité dépendait de la
 * vitesse des corps voisins.
 */
class PhysicsBallisticsTest {

    /** Un disque lancé depuis l'origine, à [speed] m/s sous [deg] degrés. */
    private fun shot(world: PhysWorld, speed: Float, deg: Float, drag: Float = 0f): PhysBody {
        val a = Math.toRadians(deg.toDouble()).toFloat()
        return PhysBody.circle(0.1f, 5f).apply {
            x = 0f
            y = 0f
            vx = speed * cos(a)
            vy = speed * sin(a)
            dragFactor = drag
            world.add(this)
        }
    }

    /** Fait voler le corps jusqu'à ce qu'il repasse sous zéro, et rend son abscisse. */
    private fun range(world: PhysWorld, ball: PhysBody, dt: Float, frames: Boolean): Float {
        repeat(20_000) {
            if (frames) world.stepFrame(dt) else world.step(dt)
            if (ball.vy < 0f && ball.y <= 0f) return ball.x
        }
        return ball.x
    }

    @Test
    fun `sans amortissement ni air, la parabole est celle du manuel`() {
        val world = PhysWorld().apply {
            linearDamping = 0f
            angularDamping = 0f
        }
        val ball = shot(world, 30f, 45f)
        val d = range(world, ball, 1f / 480f, frames = false)

        // v² sin(2θ) / g, la formule du lycée.
        val expected = 30f * 30f / world.gravity
        assertTrue(
            "la parabole est fausse : %.1f m au lieu de %.1f m".format(d, expected),
            abs(d - expected) < expected * 0.02f
        )
    }

    @Test
    fun `la portee ne depend pas du decoupage du pas`() {
        // Le même tir, joué une fois en sous-pas automatiques et une fois en pas
        // minuscules. Les deux doivent tomber au même endroit : tout écart veut dire
        // qu'une perte se règle sur le nombre de pas et non sur le temps écoulé.
        val coarse = PhysWorld()
        val fine = PhysWorld()
        val a = shot(coarse, 45f, 40f)
        val b = shot(fine, 45f, 40f)

        val da = range(coarse, a, 1f / 60f, frames = true)
        val db = range(fine, b, 1f / 2000f, frames = false)

        println("sous-pas automatiques %.1f m contre pas fins %.1f m".format(da, db))
        assertTrue(
            "la portée dépend du découpage : %.1f m contre %.1f m".format(da, db),
            abs(da - db) < db * 0.03f
        )
    }

    @Test
    fun `la trainee ne fait que freiner`() {
        // Elle doit raccourcir le tir, jamais le rallonger, et jamais rendre de la
        // vitesse : une traînée qui change de signe créerait de l'énergie.
        val vacuum = PhysWorld().apply { linearDamping = 0f; angularDamping = 0f }
        val air = PhysWorld().apply { linearDamping = 0f; angularDamping = 0f }
        val free = shot(vacuum, 60f, 45f)
        val braked = shot(air, 60f, 45f, drag = 0.02f)

        val dFree = range(vacuum, free, 1f / 480f, frames = false)
        val dBraked = range(air, braked, 1f / 480f, frames = false)

        println("sans air %.0f m, avec air %.0f m".format(dFree, dBraked))
        assertTrue("l'air ne freine pas : %.0f m contre %.0f m".format(dFree, dBraked), dBraked < dFree)
        assertTrue("l'air freine trop : %.0f m".format(dBraked), dBraked > dFree * 0.4f)
    }

    @Test
    fun `un pas enorme ne renverse pas la trainee`() {
        // Avec un pas grossier, la décélération calculée peut dépasser la vitesse
        // elle-même. Sans borne, l'air repousserait le corps en arrière, en accélérant.
        val world = PhysWorld().apply { gravity = 0f; linearDamping = 0f }
        val ball = shot(world, 100f, 0f, drag = 5f)
        val start = sqrt(ball.speedSq)

        repeat(50) { world.step(0.2f) }

        assertTrue("le corps recule : vx=${ball.vx}", ball.vx >= 0f)
        assertTrue("le corps accélère dans l'air : ${sqrt(ball.speedSq)}", sqrt(ball.speedSq) <= start)
    }
}
