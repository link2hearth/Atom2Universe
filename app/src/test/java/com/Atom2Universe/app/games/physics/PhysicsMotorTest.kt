package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Le moteur rotatif : la seule pièce du moteur physique qui ait le droit d'ajouter
 * de l'énergie. Tout l'enjeu de ces tests est qu'elle n'en ajoute **que** ce qu'un
 * moteur réel fournirait, et jamais dans le mauvais sens.
 */
class PhysicsMotorTest {

    private fun world(): PhysWorld = PhysWorld().apply {
        gravity = 0f
        linearDamping = 0f
        angularDamping = 0f
        sleepEnabled = false
        iterations = 18
    }

    /** Un volant sur son axe, entraîné par un moteur. */
    private fun rig(
        inertia: Float = 200f,
        torque: Float = 50f,
        freeOmega: Float = 6f
    ): Triple<PhysWorld, PhysBody, RotaryDriveJoint> {
        val w = world()
        val post = anchorPost(0f, 0f)
        val wheel = PhysBody.circle(1f, 2f * inertia).apply { refreshMass() }
        w.add(post)
        w.add(wheel)
        w.addJoint(RevoluteJoint.pin(post, wheel, 0f, 0f))
        val drive = RotaryDriveJoint(post, wheel).apply {
            maxTorque = torque
            targetOmega = freeOmega
        }
        w.addJoint(drive)
        return Triple(w, wheel, drive)
    }

    @Test
    fun `un moteur monte la roue jusqu a sa vitesse libre et s y tient`() {
        val (w, wheel, _) = rig()
        repeat(120 * 120) { w.stepFrame(1f / 120f) }
        assertEquals(6f, wheel.omega, 0.05f)
    }

    @Test
    fun `un moteur ne pousse jamais au dela de sa vitesse libre`() {
        val (w, wheel, _) = rig()
        // Lancée bien plus vite que le moteur : il doit se contenter de la laisser
        // filer, sans l'accélérer davantage ni la retenir.
        wheel.omega = 30f
        repeat(600) { w.stepFrame(1f / 120f) }
        assertEquals(30f, wheel.omega, 1e-3f)
    }

    @Test
    fun `un moteur ne freine pas une roue lancee a contresens`() {
        val (w, wheel, _) = rig()
        wheel.omega = -12f
        repeat(600) { w.stepFrame(1f / 120f) }
        // Il pousse dans son sens, donc il la ralentit puis la retourne — mais il ne
        // peut jamais faire mieux que son propre couple, et l'énergie ne saute pas.
        assertTrue("le moteur doit ramener la roue dans son sens", wheel.omega > -12f)
        assertTrue("et jamais au-dela de sa vitesse libre", wheel.omega <= 6.05f)
    }

    @Test
    fun `un moteur trop faible cale sans jamais faire reculer sa charge`() {
        // Une inertie énorme et un couple minuscule : la roue avance à peine.
        val (w, wheel, drive) = rig(inertia = 500_000f, torque = 5f, freeOmega = 6f)
        repeat(120) { w.stepFrame(1f / 120f) }
        assertTrue("il doit tirer a plein couple", drive.stalled)
        assertTrue("et avancer un peu, pas reculer", wheel.omega > 0f && wheel.omega < 0.02f)
    }

    /**
     * Le garde-fou du moteur physique dit que le solveur ne crée jamais d'énergie.
     * Un moteur en crée — c'est son métier — mais jamais plus que `couple × angle`.
     */
    @Test
    fun `l energie donnee ne depasse jamais le travail du couple`() {
        val (w, wheel, _) = rig(inertia = 200f, torque = 50f, freeOmega = 6f)
        val startAngle = wheel.angle
        repeat(240) { w.stepFrame(1f / 120f) }
        val energy = 0.5f * wheel.inertia * wheel.omega * wheel.omega
        val work = 50f * abs(wheel.angle - startAngle)
        assertTrue(
            "energie $energy J pour un travail maximal de $work J",
            energy <= work * 1.02f + 1e-3f
        )
    }

    @Test
    fun `la puissance ne depend pas de la frequence de simulation`() {
        val fast = rig()
        repeat(120 * 20) { fast.first.stepFrame(1f / 120f) }
        val slow = rig()
        repeat(30 * 20) { slow.first.stepFrame(1f / 30f) }
        assertEquals(fast.second.omega, slow.second.omega, 0.05f)
    }

    @Test
    fun `un moteur debraye ne fait rien du tout`() {
        val (w, wheel, drive) = rig()
        drive.maxTorque = 0f
        repeat(600) { w.stepFrame(1f / 120f) }
        assertEquals(0f, wheel.omega, 1e-4f)
    }
}
