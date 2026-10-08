package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mise en sommeil : ce qui ne bouge plus ne doit plus rien coûter, et rien
 * d'autre ne doit changer.
 *
 * C'est l'optimisation qui rend une cible empilée abordable, et c'est aussi celle
 * qui peut faire les dégâts les plus discrets : un corps endormi n'est plus
 * intégré, donc s'il s'endort au mauvais moment il reste **suspendu en l'air**, et
 * s'il reçoit de la vitesse pendant son sommeil il repart comme un ressort le jour
 * où on le réveille. Les quatre tests ci-dessous couvrent exactement ces cas.
 */
class PhysicsSleepTest {

    private fun sleepyWorld(halfWidth: Float = 20f): PhysWorld =
        worldWithGround(halfWidth).apply { sleepEnabled = true }

    @Test
    fun `une pile posee finit par s endormir`() {
        val world = sleepyWorld()
        for (i in 0 until 5) world.add(box(0.5f, 0.25f, 100f, 0f, 0.25f + i * 0.51f))

        world.simulate(3f)

        val awake = world.bodies.count { !it.sleeping && !it.immovable }
        assertEquals("des caisses posées tournent encore dans le solveur", 0, awake)
    }

    @Test
    fun `un boulet reveille ce qu il touche, et de proche en proche`() {
        val world = sleepyWorld()
        val stack = ArrayList<PhysBody>()
        for (i in 0 until 5) {
            val b = box(0.5f, 0.25f, 100f, 0f, 0.25f + i * 0.51f)
            stack += b
            world.add(b)
        }
        world.simulate(3f)
        assertTrue("la pile ne s'est pas endormie", stack.all { it.sleeping })

        // Un boulet lancé dans l'assise du bas, à la vitesse d'un vrai tir.
        val ball = disc(0.16f, 12f, -6f, 0.3f).apply { vx = 90f }
        world.add(ball)
        world.simulate(0.5f)

        assertTrue("l'assise touchée dort encore", !stack[0].sleeping)
        assertTrue(
            "le réveil ne s'est pas propagé dans la pile",
            stack.count { !it.sleeping } >= 3
        )
        // Et la pile a réellement bougé, elle n'a pas seulement changé d'état.
        assertTrue("rien n'a bougé sous le choc", stack[0].x > 0.05f)
    }

    @Test
    fun `retirer un corps reveille ce qui s appuyait dessus`() {
        val world = sleepyWorld()
        val bas = box(0.5f, 0.25f, 100f, 0f, 0.25f)
        val haut = box(0.5f, 0.25f, 100f, 0f, 0.76f)
        world.add(bas)
        world.add(haut)
        world.simulate(3f)
        assertTrue("la pile ne s'est pas endormie", bas.sleeping && haut.sleeping)

        // C'est ce que fait une pierre qui éclate : elle disparaît du monde.
        world.remove(bas)
        assertFalse("la caisse du haut n'a pas été réveillée", haut.sleeping)

        // Et elle retombe pour de bon — puis se rendort au sol, ce qui est très bien.
        world.simulate(1f)
        assertTrue("elle est restée suspendue en l'air : y=${haut.y}", haut.y < 0.5f)
    }

    @Test
    fun `une liaison ne pousse pas sur un corps endormi`() {
        // Le piège mesuré : une liaison résolue contre un corps endormi lui verse de
        // la vitesse qu'il n'intègre jamais. Elle s'accumule, et il part comme un
        // ressort au réveil. Un boulet de trébuchet sortait ainsi à deux fois et
        // demie l'énergie que la machine contient.
        val world = sleepyWorld()
        world.sleepDelay = 0.3f
        val post = anchorPost(0f, 5f)
        world.add(post)
        val arm = PhysBody(1.5f, 0.08f, 20f).apply { x = -1.5f; y = 5f }
        world.add(arm)
        world.addJoint(RevoluteJoint.pin(arm, post, 0f, 5f))
        val poids = box(0.3f, 0.3f, 200f, -3f, 5f)
        world.add(poids)
        world.addJoint(DistanceJoint.between(arm, -3f, 5f, poids, -3f, 5f, rope = true))

        // On le laisse balancer, puis on l'endort de force, comme le jeu endort une
        // cible qui attend : la liaison, elle, est toujours là et tire toujours.
        world.simulate(1.5f)
        arm.sleep()
        poids.sleep()

        val armAngle = arm.angle
        val poidsY = poids.y
        world.simulate(2f)

        assertTrue("le pendule s'est réveillé tout seul", arm.sleeping && poids.sleeping)
        assertEquals("la liaison a versé de la vitesse au dormeur", 0f, poids.speedSq, 0f)
        assertEquals("la liaison a versé de la rotation au dormeur", 0f, arm.omega, 0f)
        assertEquals("le dormeur a dérivé", armAngle, arm.angle, 0f)
        assertEquals("le dormeur a dérivé", poidsY, poids.y, 0f)

        // Et au réveil, il repart de ce qu'il valait, sans réserve cachée.
        arm.wake()
        poids.wake()
        world.stepFrame(1f / 120f)
        assertTrue(
            "le poids est reparti comme un ressort à %.2f m/s".format(kotlin.math.sqrt(poids.speedSq)),
            poids.speedSq < 0.25f
        )
    }

    /**
     * **Un corps endormi avant son premier pas reste touchable.**
     *
     * Le balayage ne recalcule pas la boîte englobante d'un dormeur : il n'a pas bougé,
     * donc la sienne est encore bonne. Sauf s'il n'en a **jamais** eu. Un corps posé
     * dans le monde puis endormi tout de suite gardait alors la boîte nulle de sa
     * construction, se retrouvait à l'origine pour la détection, et **plus rien ne le
     * touchait jamais**.
     *
     * Ça s'est vu en vrai : un village de vingt-deux pierres chargé puis endormi avant
     * sa première image laissait les boulets le traverser de part en part. Rien ne le
     * signale — pas d'exception, pas de forme manquante, un village bien peint — et on
     * voit simplement un boulet passer au travers d'un mur.
     *
     * [PhysBody.sleep] rafraîchit donc la boîte avant d'endormir.
     */
    @Test
    fun `un corps endormi des sa pose garde sa vraie boite`() {
        val world = sleepyWorld()
        val mur = PhysBody(1f, 3f, 5_000f).apply {
            x = 8f
            y = 3f
            refreshMass()
        }
        world.add(mur)
        // Endormi **avant** le moindre pas de simulation : le cas exact du village.
        mur.sleep()
        assertTrue("la boîte du dormeur est restée à l'origine", mur.aabbMaxX > 6f)

        val bille = PhysBody.circle(0.3f, 40f).apply {
            x = 0f
            y = 3f
            vx = 25f
        }
        world.add(bille)
        var reveille = false
        repeat(240) {
            world.stepFrame(1f / 120f)
            if (!mur.sleeping) reveille = true
        }

        println(
            "SOMMEIL bille arrêtée à x=${"%.2f".format(bille.x)}, " +
                "mur poussé à ${"%.4f".format(mur.x)}, réveillé=$reveille"
        )
        assertTrue(
            "la bille a traversé un mur endormi : x=${"%.2f".format(bille.x)}",
            bille.x < mur.x
        )
        // Le mur se rendort ensuite, et c'est très bien : cinq tonnes encaissent une
        // bille de quarante kilos sans broncher. Ce qu'on vérifie, c'est qu'il s'est
        // réveillé **au choc** — un dormeur qu'on traverse ne se réveille jamais.
        assertTrue("le choc n'a jamais réveillé le mur", reveille)
    }
}
