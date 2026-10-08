package com.Atom2Universe.app.games.physics

import kotlin.math.hypot

/**
 * Une corde faite de plusieurs brins, tendue entre deux corps.
 *
 * Une seule [DistanceJoint] en mode corde suffit déjà à retenir deux objets l'un
 * à l'autre, et c'est ce qu'il faut employer quand seule la contrainte compte —
 * une fronde, par exemple. Cette corde-ci sert quand on veut la **voir** : elle
 * pend, elle ondule, elle s'enroule sur les obstacles, parce qu'elle est faite de
 * maillons qui ont chacun leur poids.
 *
 * Les maillons ne se touchent ni entre eux ni avec les deux corps reliés : une
 * corde qui se marche dessus se met à trembler pour rien.
 */
class Rope private constructor(
    /** Les maillons, du premier ancrage vers le second. */
    val links: List<PhysBody>,
    /** Les contraintes, une de plus que les maillons. */
    val joints: List<DistanceJoint>
) {

    /** Longueur totale au repos, en mètres. */
    val length: Float get() = joints.sumOf { it.length.toDouble() }.toFloat()

    /** Tension la plus forte du moment, en kg·m/s : de quoi faire casser une corde. */
    fun maxTension(): Float {
        var t = 0f
        for (j in joints) if (j.tension > t) t = j.tension
        return t
    }

    /** Retire la corde du monde : les maillons et les contraintes. */
    fun removeFrom(world: PhysWorld) {
        for (j in joints) world.removeJoint(j)
        for (l in links) world.remove(l)
    }

    /** Décroche la corde de son second ancrage. */
    fun release() {
        joints.last().enabled = false
    }

    companion object {

        /** Catégorie réservée aux maillons, pour qu'ils s'ignorent entre eux. */
        const val LINK_CATEGORY = 1 shl 30

        /**
         * Tend une corde de [segments] maillons entre un point de [a] et un point
         * de [b], en suivant la ligne droite qui les sépare.
         *
         * [slack] allonge la corde par rapport à cette distance : à 0 elle est
         * tendue d'emblée, à 0,2 elle a vingt pour cent de mou et pend.
         */
        fun between(
            world: PhysWorld,
            a: PhysBody, ax: Float, ay: Float,
            b: PhysBody, bx: Float, by: Float,
            segments: Int = 6,
            totalMass: Float = 1f,
            slack: Float = 0f,
            linkRadius: Float = 0.04f
        ): Rope {
            require(segments >= 1) { "une corde a au moins un maillon" }

            val span = hypot(bx - ax, by - ay)
            val segLength = span * (1f + slack) / (segments + 1)
            val linkMass = maxOf(totalMass / segments, 1e-3f)

            val links = ArrayList<PhysBody>(segments)
            for (i in 1..segments) {
                val t = i.toFloat() / (segments + 1)
                val link = PhysBody.circle(linkRadius, linkMass).apply {
                    x = ax + (bx - ax) * t
                    y = ay + (by - ay) * t
                    friction = 0.4f
                    category = LINK_CATEGORY
                    // Ni entre eux, ni avec ce qu'ils relient : une corde qui se
                    // marche dessus tremble sans fin.
                    collidesWith = LINK_CATEGORY.inv() and
                        a.category.inv() and b.category.inv()
                }
                world.add(link)
                links += link
            }

            val joints = ArrayList<DistanceJoint>(segments + 1)
            var prev = a
            var prevX = ax
            var prevY = ay
            for (link in links) {
                joints += link(world, prev, prevX, prevY, link, link.x, link.y, segLength)
                prev = link
                prevX = link.x
                prevY = link.y
            }
            joints += link(world, prev, prevX, prevY, b, bx, by, segLength)

            return Rope(links, joints)
        }

        private fun link(
            world: PhysWorld,
            a: PhysBody, ax: Float, ay: Float,
            b: PhysBody, bx: Float, by: Float,
            length: Float
        ): DistanceJoint {
            val j = DistanceJoint(a, b)
            j.setWorldAnchors(ax, ay, bx, by)
            j.length = length
            j.rope = true
            world.addJoint(j)
            return j
        }
    }
}
