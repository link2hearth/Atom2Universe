package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Le tapis roulant : une surface qui défile sans que le corps bouge.
 *
 * Tout se joue dans le solveur de frottement, en décalant la vitesse qu'il cherche à
 * atteindre. Ce qui en découle — l'adhérence qui borne l'entraînement, le patinage
 * quand elle ne suffit pas, le freinage d'une charge trop rapide — n'est donc pas codé,
 * c'est hérité.
 *
 * **La bande est très longue dans tous ces essais, et ce n'est pas de la coquetterie.**
 * Le premier banc mesurait sur une bande de dix mètres : la caisse en sortait par le
 * bout au bout de trois secondes, et la chute libre qui suivait se lisait comme un
 * emballement du tapis. Une mesure qui ne tient pas jusqu'à la fin du trajet ne mesure
 * pas ce qu'on croit.
 */
class PhysicsConveyorTest {

    /** Un tapis horizontal immobile, dont la surface défile à [speed] m/s. */
    private fun belt(speed: Float, halfWidth: Float = 200f): PhysBody =
        PhysBody(halfWidth, 0.5f, 0f).apply {
            x = 0f
            y = -0.5f
            lockPosition = true
            lockRotation = true
            friction = 0.8f
            surfaceSpeed = speed
            refreshMass()
        }

    /**
     * Pose une caisse sur un tapis à l'arrêt, la laisse se stabiliser, puis met la
     * bande en marche. C'est le seul protocole qui isole l'entraînement : une caisse
     * lâchée sur une bande déjà lancée mêle le rebond de la chute à ce qu'on mesure.
     */
    private fun started(speed: Float): Pair<PhysWorld, PhysBody> {
        val w = PhysWorld()
        val tapis = belt(0f)
        w.add(tapis)
        val caisse = box(0.3f, 0.3f, 10f, 0f, 0.31f)
        w.add(caisse)
        w.simulate(1.5f)
        tapis.surfaceSpeed = speed
        w.wakeAll()
        return w to caisse
    }

    @Test
    fun `une surface immobile se comporte exactement comme avant`() {
        // Non-régression : `surfaceSpeed` vaut zéro partout dans le jeu existant, et le
        // moteur doit rendre les mêmes nombres au bit près.
        fun pose(avecTapis: Boolean): Float {
            val w = PhysWorld()
            w.add(if (avecTapis) belt(0f, halfWidth = 5f) else ground())
            val caisse = box(0.3f, 0.3f, 10f, 0f, 2f)
            w.add(caisse)
            w.simulate(3f)
            return caisse.x
        }
        assertEquals("un tapis à l'arrêt a bougé la caisse", pose(false), pose(true), 0f)
    }

    @Test
    fun `un tapis entraine sa charge a la vitesse de sa bande`() {
        val (w, caisse) = started(2f)
        w.simulate(2f)

        assertEquals("la caisse ne suit pas la bande", 2f, caisse.vx, 0.01f)
        assertTrue("la caisse s'est mise à tourner sur le tapis", abs(caisse.omega) < 0.01f)
        assertTrue("la caisse n'a pas avancé", caisse.x > 3f)
    }

    @Test
    fun `un tapis ne lance jamais sa charge plus vite que sa bande`() {
        // La borne qui compte, et elle est gratuite : l'entraînement reste borné par
        // l'adhérence, donc la bande cesse de pousser dès qu'elle a rattrapé sa charge.
        val (w, caisse) = started(3f)
        repeat(1200) {
            w.stepFrame(1f / 120f)
            assertTrue("la caisse a dépassé la bande : vx = ${caisse.vx}",
                caisse.vx <= 3.001f)
        }
        assertEquals("la caisse n'a pas fini par suivre la bande", 3f, caisse.vx, 0.01f)
    }

    @Test
    fun `un tapis freine aussi ce qui va trop vite`() {
        // Une bande lente sous une caisse lancée doit la **retenir**, pas la pousser :
        // c'est le même frottement, dans l'autre sens.
        val w = PhysWorld()
        w.add(belt(1f))
        val caisse = box(0.3f, 0.3f, 10f, 0f, 0.31f)
        caisse.vx = 6f
        w.add(caisse)
        w.simulate(4f)
        assertEquals("la caisse n'a pas été ramenée à la vitesse de la bande",
            1f, caisse.vx, 0.02f)
    }

    @Test
    fun `la bande defile dans le sens du corps`() {
        // La vitesse est portée par l'axe local +X : un tapis retourné entraîne dans
        // l'autre sens sans qu'on ait à changer le signe à la main.
        val w = PhysWorld()
        w.add(belt(2f).apply { angle = Math.PI.toFloat() })
        val caisse = box(0.3f, 0.3f, 10f, 0f, 0.31f)
        w.add(caisse)
        w.simulate(3f)
        assertEquals("le tapis retourné n'entraîne pas vers la gauche",
            -2f, caisse.vx, 0.02f)
        assertTrue("la caisse n'est pas partie vers la gauche", caisse.x < -2f)
    }

    @Test
    fun `une bande sans adherence n entraine rien`() {
        val w = PhysWorld()
        w.add(belt(3f).apply { friction = 0f })
        val caisse = box(0.3f, 0.3f, 10f, 0f, 0.31f).apply { friction = 0f }
        w.add(caisse)
        w.simulate(3f)
        assertTrue("une bande sans frottement a quand même entraîné : vx = ${caisse.vx}",
            abs(caisse.vx) < 0.05f)
    }

    @Test
    fun `une bande trop rapide patine sous sa charge`() {
        // L'entraînement est borné par `μ × poids`, donc l'accélération d'une caisse sur
        // un tapis ne dépasse jamais `μ·g` quelle que soit la vitesse de la bande. À
        // μ = 0,7 c'est 6,9 m/s² : la caisse met une seconde pleine à atteindre 7 m/s,
        // et une bande à 50 m/s ne l'arrache pas pour autant.
        val w = PhysWorld()
        w.add(belt(50f))
        val caisse = box(0.3f, 0.3f, 10f, 0f, 0.31f)
        w.add(caisse)
        w.simulate(1f)

        val mu = kotlin.math.sqrt(0.8f * caisse.friction)
        val plafond = mu * PhysicsConstants.STANDARD_GRAVITY
        assertTrue("la bande a arraché sa charge : vx = ${caisse.vx} pour un plafond de $plafond",
            caisse.vx <= plafond * 1.05f)
        assertTrue("la bande n'a rien entraîné du tout", caisse.vx > plafond * 0.8f)
    }
}
