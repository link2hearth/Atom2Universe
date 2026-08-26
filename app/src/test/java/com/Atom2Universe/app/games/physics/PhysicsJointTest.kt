package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Vérifie la liaison pivot, la pièce ajoutée au moteur pour le trébuchet : elle
 * doit tenir son point d'ancrage, multiplier les vitesses selon le rapport de
 * bras, projeter un poids, et lâcher prise quand on le lui demande.
 */
class PhysicsJointTest {

    /** Longueur du bras, du pivot vers chaque extrémité, pour un bras posé à plat. */
    private class Lever(val beam: PhysBody, val post: PhysBody, val joint: RevoluteJoint) {
        /** Vitesse réelle du point du bras situé à [dist] du pivot, côté [side]. */
        fun tipSpeed(dist: Float, side: Float): Float {
            val out = FloatArray(2)
            joint.anchorWorld(out)
            // Position du point visé dans le repère monde, puis sa vitesse :
            // v = v_centre + oméga × r
            val rx = (out[0] + side * dist * kotlin.math.cos(beam.angle)) - beam.x
            val ry = (out[1] + side * dist * kotlin.math.sin(beam.angle)) - beam.y
            return hypot(beam.vx - beam.omega * ry, beam.vy + beam.omega * rx)
        }
    }

    /**
     * Un bras de 4 m dont le pivot est décalé : 3 m d'un côté, 1 m de l'autre.
     * C'est exactement la géométrie que le jeu d'équilibre ne savait pas faire.
     */
    private fun lever(world: PhysWorld, pivotOffset: Float = 1f, beamMass: Float = 8f): Lever {
        val beam = PhysBody(2f, 0.06f, beamMass).apply {
            x = 0f
            y = 2f
            friction = 0.7f
        }
        val post = anchorPost(pivotOffset, 2f)
        world.add(post)
        world.add(beam)
        val joint = RevoluteJoint.pin(beam, post, pivotOffset, 2f)
        world.addJoint(joint)
        return Lever(beam, post, joint)
    }

    @Test
    fun `la liaison tient son point dancrage pendant que le bras tourne`() {
        val world = PhysWorld()
        val lv = lever(world)

        world.simulate(0.8f)

        val out = FloatArray(2)
        lv.joint.anchorWorld(out)
        val drift = hypot(out[0] - 1f, out[1] - 2f)
        assertTrue("l'axe a dérivé de $drift m", drift < 0.02f)
        // Le centre de gravité du bras est à gauche du pivot, donc ce côté descend.
        // Avec l'axe Y vers le haut, un point à gauche qui descend, c'est une
        // rotation dans le sens trigonométrique : l'angle augmente.
        assertTrue("le bras ne tourne pas : angle=${lv.beam.angle}", lv.beam.angle > 0.3f)
    }

    @Test
    fun `la liaison resiste a une charge lourde sans lacher`() {
        val world = PhysWorld()
        val lv = lever(world)
        // 120 kg posés sur le bras long : vingt fois le poids du bras lui-même.
        val load = box(0.25f, 0.25f, 120f, -1.6f, 2.35f)
        world.add(load)

        world.simulate(1.5f)

        val out = FloatArray(2)
        lv.joint.anchorWorld(out)
        val drift = hypot(out[0] - 1f, out[1] - 2f)
        assertTrue("l'axe cède sous la charge : dérive de $drift m", drift < 0.05f)
        assertTrue("la liaison n'encaisse rien : ${lv.joint.reactionImpulse}", lv.joint.reactionImpulse > 0f)
    }

    @Test
    fun `le bras long va trois fois plus vite que le bras court`() {
        val world = PhysWorld()
        val lv = lever(world)

        // Assez de temps pour que le bras ait pris de la vitesse, pas assez pour
        // qu'il soit vertical (au-delà, les longueurs projetées changent).
        world.simulate(0.35f)

        val longSide = lv.tipSpeed(3f, -1f)   // 3 m à gauche du pivot
        val shortSide = lv.tipSpeed(1f, 1f)   // 1 m à droite
        val ratio = longSide / shortSide
        assertTrue(
            "le rapport de bras ne se retrouve pas dans les vitesses : $ratio (attendu 3)",
            abs(ratio - 3f) < 0.15f
        )
        assertTrue("le bras ne bouge pas : ${lv.beam.omega}", abs(lv.beam.omega) > 0.2f)
    }

    @Test
    fun `le point du bras situe sur laxe reste immobile`() {
        val world = PhysWorld()
        val lv = lever(world)

        world.simulate(0.4f)

        // Vitesse du point du bras qui coïncide avec l'axe : elle doit être nulle,
        // c'est la définition même d'un pivot.
        assertTrue("l'axe se déplace : ${lv.tipSpeed(0f, 1f)} m/s", lv.tipSpeed(0f, 1f) < 0.05f)
    }

    @Test
    fun `decrocher la liaison libere le bras`() {
        val world = PhysWorld()
        val lv = lever(world)

        world.simulate(0.4f)
        val yBefore = lv.beam.y

        lv.joint.enabled = false
        world.simulate(0.5f)

        assertTrue(
            "le bras décroché reste accroché : y=${lv.beam.y} (avant ${yBefore})",
            lv.beam.y < yBefore - 0.8f
        )
    }

    @Test
    fun `un contrepoids lourd projette le boulet loin devant`() {
        // Épreuve d'ensemble : la machine du jeu, en entier, doit lancer.
        // C'est le test précédent qui prouve le rapport de bras ; celui-ci vérifie
        // qu'un contrepoids posé sur le bras court envoie vraiment le boulet.
        val world = worldWithGround(halfWidth = 20f)

        // Bras de 3 m, pivot à 1 m de l'extrémité droite : 1,75 m contre 0,75 m.
        val beam = PhysBody(1.5f, 0.06f, 6f).apply {
            x = 0f
            y = 2f
            friction = 0.7f
        }
        val post = anchorPost(0.5f, 2f)
        world.add(post)
        world.add(beam)
        world.addJoint(RevoluteJoint.pin(beam, post, 0.5f, 2f))

        // Contrepoids posé sur le bras court, boulet posé sur le bras long.
        val counterweight = box(0.22f, 0.22f, 60f, 1.25f, 2.29f)
        val payload = box(0.09f, 0.09f, 2f, -1.25f, 2.16f)
        world.add(counterweight)
        world.add(payload)

        val startY = payload.y
        var peakSpeed = 0f
        var peakHeight = startY
        val dt = 1f / 240f
        repeat((2.5f / dt).toInt()) {
            world.stepFrame(dt)
            peakSpeed = maxOf(peakSpeed, hypot(payload.vx, payload.vy))
            peakHeight = maxOf(peakHeight, payload.y)
        }

        assertTrue("le boulet n'est pas parti : $peakSpeed m/s", peakSpeed > 5f)
        assertTrue(
            "le boulet n'a pas décollé : il a culminé à $peakHeight m, parti de $startY m",
            peakHeight > startY + 0.6f
        )
        // Le boulet part vers la droite alors qu'il était posé à gauche : le bras
        // long balaie par-dessus le pivot et le lâche de l'autre côté. C'est le
        // comportement d'un vrai trébuchet, et ça décide de l'orientation de la
        // machine dans le jeu : on tire par-dessus soi, du côté du contrepoids.
        assertTrue("le boulet retombe sur la machine : x=${payload.x}", payload.x > 3f)
    }

    @Test
    fun `deux corps relies par une liaison ne se repoussent pas`() {
        // L'axe est planté au milieu du bras : les deux formes se chevauchent, et
        // sans le filtre de collision la machine se mettrait à vibrer.
        val world = PhysWorld()
        world.gravity = 0f
        val lv = lever(world, pivotOffset = 0f)

        world.simulate(1f)

        assertTrue("le bras est éjecté par son propre axe : x=${lv.beam.x}", abs(lv.beam.x) < 0.01f)
        assertTrue("le bras se met à tourner tout seul : ${lv.beam.omega}", abs(lv.beam.omega) < 0.01f)
    }
}
