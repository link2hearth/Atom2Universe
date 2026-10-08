package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Une liaison qui tient deux points à distance fixe — une barre, ou une **corde**.
 *
 * Les deux modes ne diffèrent que par un signe :
 *
 *  - **barre** ([rope] à `false`) : la distance est maintenue exactement, la
 *    liaison pousse autant qu'elle tire ;
 *  - **corde** ([rope] à `true`) : elle ne fait que **retenir**. En deçà de sa
 *    longueur elle est molle et ne dit rien ; au-delà, elle tire. C'est la seule
 *    façon honnête de représenter une corde, et c'est aussi ce qui la rend stable
 *    là où une barre rigide s'emballe : une corde molle ne transmet rien, donc
 *    n'a rien à faire exploser.
 *
 * Une corde est aussi bien plus sage qu'une tige qu'on aurait fabriquée avec un
 * corps allongé et deux pivots : elle n'a ni masse ni inertie propres, donc aucun
 * rapport de masse défavorable ne se glisse au milieu de la chaîne.
 */
class DistanceJoint(a: PhysBody, b: PhysBody) : Joint(a, b) {

    /** Point d'ancrage dans le repère de [a]. */
    var localAnchorAX = 0f
    var localAnchorAY = 0f

    /** Point d'ancrage dans le repère de [b]. */
    var localAnchorBX = 0f
    var localAnchorBY = 0f

    /** Longueur au repos, en mètres. */
    var length = 1f

    /** Corde (ne fait que retenir) plutôt que barre (retient et repousse). */
    var rope = false

    /** Impulsion accumulée le long de l'axe, conservée d'une image à l'autre. */
    var impulse = 0f
        private set

    private var nx = 0f
    private var ny = 0f
    private var rax = 0f
    private var ray = 0f
    private var rbx = 0f
    private var rby = 0f
    private var effectiveMass = 0f
    private var error = 0f
    private var posScale = 0f
    private var solvable = false
    private var slack = false

    companion object {
        /** Part de l'écart rattrapée à chaque pas par la passe de position. */
        private const val POSITION_RATE = 0.35f

        /**
         * Tend une liaison entre deux points du monde, en prenant la distance
         * actuelle comme longueur au repos.
         */
        fun between(
            a: PhysBody, ax: Float, ay: Float,
            b: PhysBody, bx: Float, by: Float,
            rope: Boolean = false
        ): DistanceJoint {
            val j = DistanceJoint(a, b)
            j.setWorldAnchors(ax, ay, bx, by)
            j.length = hypot(bx - ax, by - ay)
            j.rope = rope
            return j
        }

        /** Une corde entre deux corps, ancrée à leurs centres. */
        fun rope(a: PhysBody, b: PhysBody, length: Float): DistanceJoint =
            DistanceJoint(a, b).also {
                it.length = length
                it.rope = true
            }
    }

    /** Pose les deux ancrages d'après des points du monde et la pose actuelle. */
    fun setWorldAnchors(ax: Float, ay: Float, bx: Float, by: Float) {
        val ca = cos(a.angle)
        val sa = sin(a.angle)
        val dxa = ax - a.x
        val dya = ay - a.y
        localAnchorAX = dxa * ca + dya * sa
        localAnchorAY = -dxa * sa + dya * ca

        val cb = cos(b.angle)
        val sb = sin(b.angle)
        val dxb = bx - b.x
        val dyb = by - b.y
        localAnchorBX = dxb * cb + dyb * sb
        localAnchorBY = -dxb * sb + dyb * cb
    }

    /** Position monde de l'ancrage porté par [a], dans [out]. */
    fun anchorAWorld(out: FloatArray) = a.localToWorld(localAnchorAX, localAnchorAY, out)

    /** Position monde de l'ancrage porté par [b], dans [out]. */
    fun anchorBWorld(out: FloatArray) = b.localToWorld(localAnchorBX, localAnchorBY, out)

    /** Vrai si la corde est molle en ce moment : elle ne transmet alors rien. */
    val isSlack: Boolean get() = slack

    /** Tension actuelle, en kg·m/s. Nulle sur une corde molle. */
    val tension: Float get() = abs(impulse)

    override fun reset() {
        impulse = 0f
        posImpulse = 0f
    }

    private var posImpulse = 0f

    override fun preStep(invDt: Float) {
        solvable = false
        slack = false
        posImpulse = 0f
        if (!enabled || !a.inWorld || !b.inWorld) return

        val ca = cos(a.angle)
        val sa = sin(a.angle)
        rax = localAnchorAX * ca - localAnchorAY * sa
        ray = localAnchorAX * sa + localAnchorAY * ca

        val cb = cos(b.angle)
        val sb = sin(b.angle)
        rbx = localAnchorBX * cb - localAnchorBY * sb
        rby = localAnchorBX * sb + localAnchorBY * cb

        val dx = (b.x + rbx) - (a.x + rax)
        val dy = (b.y + rby) - (a.y + ray)
        val dist = hypot(dx, dy)
        // Deux ancrages confondus : plus aucune direction, rien à faire.
        if (dist < 1e-5f) return
        nx = dx / dist
        ny = dy / dist
        error = dist - length
        // L'écart est une longueur : il faut le lire comme une vitesse pour le
        // résorber, donc le diviser par la durée du pas.
        posScale = POSITION_RATE * invDt

        // Une corde plus courte que sa longueur pend : elle ne transmet rien, et
        // l'impulsion mémorisée doit être oubliée pour qu'elle reparte molle.
        if (rope && error < 0f) {
            slack = true
            impulse = 0f
            return
        }

        val crossA = rax * ny - ray * nx
        val crossB = rbx * ny - rby * nx
        val k = a.invMass + b.invMass + a.invI * crossA * crossA + b.invI * crossB * crossB
        if (k <= 1e-9f) return
        effectiveMass = 1f / k

        // Réapplication de l'impulsion de l'image précédente
        val px = impulse * nx
        val py = impulse * ny
        a.vx -= a.invMass * px
        a.vy -= a.invMass * py
        a.omega -= a.invI * (rax * py - ray * px)
        b.vx += b.invMass * px
        b.vy += b.invMass * py
        b.omega += b.invI * (rbx * py - rby * px)

        solvable = true
    }

    override fun applyImpulse() {
        if (!solvable) return

        val dvx = (b.vx - b.omega * rby) - (a.vx - a.omega * ray)
        val dvy = (b.vy + b.omega * rbx) - (a.vy + a.omega * rax)
        val vn = dvx * nx + dvy * ny

        var dP = -effectiveMass * vn
        if (rope) {
            // Une corde ne pousse jamais : l'impulsion cumulée ne peut que tirer.
            val old = impulse
            impulse = minOf(old + dP, 0f)
            dP = impulse - old
        } else {
            impulse += dP
        }

        val px = dP * nx
        val py = dP * ny
        a.vx -= a.invMass * px
        a.vy -= a.invMass * py
        a.omega -= a.invI * (rax * py - ray * px)
        b.vx += b.invMass * px
        b.vy += b.invMass * py
        b.omega += b.invI * (rbx * py - rby * px)
    }

    override fun applyPositionImpulse() {
        if (!solvable) return

        val dvx = (b.pvx - b.pomega * rby) - (a.pvx - a.pomega * ray)
        val dvy = (b.pvy + b.pomega * rbx) - (a.pvy + a.pomega * rax)
        val vn = dvx * nx + dvy * ny

        var dP = -effectiveMass * (error * posScale + vn)
        if (rope) {
            val old = posImpulse
            posImpulse = minOf(old + dP, 0f)
            dP = posImpulse - old
        } else {
            posImpulse += dP
        }

        val px = dP * nx
        val py = dP * ny
        a.pvx -= a.invMass * px
        a.pvy -= a.invMass * py
        a.pomega -= a.invI * (rax * py - ray * px)
        b.pvx += b.invMass * px
        b.pvy += b.invMass * py
        b.pomega += b.invI * (rbx * py - rby * px)
    }
}
