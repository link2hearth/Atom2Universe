package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Les impulsions mémorisées d'un pas à l'autre (le démarrage à chaud) valent une force
 * multipliée par **la durée du pas où on les a trouvées**. Deux règles en découlent, et
 * ce banc garde les deux :
 *
 *  - une image se découpe en sous-pas **égaux**, jamais en « tranches pleines plus un
 *    reste » : le reste pouvait durer deux microsecondes ;
 *  - quand la durée du pas change, l'impulsion rendue est remise à l'échelle
 *    (le `dtRatio` de Box2D).
 *
 * Mesuré le 28/10/2026 avant correction : une pile de six caisses posées partait à
 * 11 cm/s après un seul pas de 2 µs, et une pile de dix sautait à 24 cm/s à chaque
 * fois qu'un objet rapide, ailleurs dans le monde, changeait le nombre de sous-pas.
 */
class PhysicsSubStepTest {

    private fun pile(world: PhysWorld, n: Int): List<PhysBody> =
        (0 until n).map { i -> box(0.2f, 0.1f, 8f, 0f, 0.1f + i * 0.205f).also { world.add(it) } }

    private fun fastest(bodies: List<PhysBody>): Float {
        var v = 0f
        for (b in bodies) v = maxOf(v, sqrt(b.speedSq))
        return v
    }

    @Test
    fun `un pas minuscule apres un long ne fait pas sauter une pile`() {
        val world = worldWithGround()
        val boxes = pile(world, 6)
        repeat(300) { world.step(1f / 60f) }

        world.step(2e-6f)
        val apresMinuscule = fastest(boxes)
        world.step(1f / 60f)
        val apresRetour = fastest(boxes)
        println("SOUS-PAS pile : %.4f m/s après 2 µs, %.4f m/s au pas suivant".format(apresMinuscule, apresRetour))

        assertTrue("la pile saute à %.3f m/s après un pas de 2 µs".format(apresMinuscule), apresMinuscule < 0.01f)
        assertTrue("la pile s'affaisse à %.3f m/s au pas suivant".format(apresRetour), apresRetour < 0.01f)
    }

    @Test
    fun `une pile ne sursaute pas quand un objet rapide change le decoupage`() {
        val world = PhysWorld()
        val sol = ground(200f)
        world.add(sol)
        val boxes = pile(world, 6)
        val dt = 1f / 60f
        repeat(180) { world.stepFrame(dt) }

        // Un obus qui file très loin de la pile : il ne touche rien, mais il impose
        // trois sous-pas et un petit reste par image, une demi-seconde sur deux.
        val obus = PhysBody.circle(0.05f, 1f).apply { x = -150f; y = 30f }
        world.add(obus)
        val vitesse = 3.02f * (0.05f + sol.smallestHalfExtent) * 0.25f / dt
        var pire = 0f
        for (f in 0 until 120) {
            obus.vx = if ((f / 30) % 2 == 0) vitesse else 0f
            obus.vy = 0f; obus.y = 30f
            world.stepFrame(dt)
            pire = maxOf(pire, fastest(boxes))
        }
        println("SOUS-PAS obus : la pile bouge au plus à %.4f m/s".format(pire))
        assertTrue("la pile sursaute à %.3f m/s".format(pire), pire < 0.05f)
    }
}
