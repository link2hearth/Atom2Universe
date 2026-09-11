package com.Atom2Universe.app.games.physics

import kotlin.math.hypot

/**
 * Une poulie : deux corps pendus à une même corde qui passe sur des réas fixes.
 *
 * C'est la seule liaison du moteur qui relie deux corps **sans les rapprocher**. Une
 * corde tient deux points à distance ; une poulie tient une **somme** :
 *
 *     longueurA + rapport × longueurB = constante
 *
 * Ce qui descend d'un côté monte de l'autre, et c'est toute la mécanique. Rien d'autre
 * dans le moteur ne sait transformer une chute en montée, et c'est exactement ce qu'on
 * demande à une machine infernale : la bille tombe dans un godet, et un contrepoids
 * s'élève à l'autre bout pour aller bousculer ce qui est en haut.
 *
 * ## Pourquoi les réas ne sont pas des corps
 *
 * On pourrait modéliser une vraie poulie : un disque en pivot, une corde qui s'enroule,
 * du frottement. Ce serait une chaîne de contacts entre un fil sans épaisseur et un
 * cercle, c'est-à-dire le pire cas du solveur, pour un résultat que personne ne verrait.
 * [groundAX]/[groundAY] et [groundBX]/[groundBY] sont donc de simples **points du
 * monde** : la corde y change de direction, un point c'est tout. Le dessin met une roue
 * par-dessus et l'illusion est parfaite.
 *
 * ## Corde, pas barre
 *
 * Comme [DistanceJoint] en mode corde, la contrainte ne fait que **retenir** : elle tire
 * les deux corps vers leurs réas, jamais elle ne les repousse. Un brin mou ne transmet
 * rien. Sans cela, un godet posé au sol se verrait « poussé » vers le bas par son propre
 * brin dès que l'autre côté remonte trop haut, ce qui n'existe pas.
 *
 * Le brin qui se raccourcit jusqu'à zéro est le seul cas dégénéré : la direction n'existe
 * plus, on ne résout rien ce pas-ci. En pratique les butées des glissières l'évitent.
 */
class PulleyJoint(a: PhysBody, b: PhysBody) : Joint(a, b) {

    /** Le réa du côté de [a], point fixe du monde. */
    var groundAX = 0f
    var groundAY = 0f

    /** Le réa du côté de [b]. */
    var groundBX = 0f
    var groundBY = 0f

    /** Point d'accroche de la corde sur [a], dans son repère. */
    var localAnchorAX = 0f
    var localAnchorAY = 0f

    /** Point d'accroche sur [b], dans son repère. */
    var localAnchorBX = 0f
    var localAnchorBY = 0f

    /**
     * Le rapport du palan : un mètre descendu du côté [a] en fait monter `1/rapport` du
     * côté [b]. À 1 c'est une poulie simple ; à 2, [b] monte deux fois moins vite mais
     * avec deux fois plus de force.
     */
    var ratio = 1f

    /** La somme à tenir : `longueurA + rapport × longueurB`, posée à la construction. */
    var constant = 0f

    /** Longueur du brin côté [a], en mètres. Utile au dessin. */
    var lengthA = 0f
        private set

    /** Longueur du brin côté [b]. */
    var lengthB = 0f
        private set

    /**
     * Impulsion accumulée, conservée d'un pas à l'autre. **Positive quand la corde tire**,
     * nulle quand elle est molle.
     */
    var impulse = 0f
        private set

    private var uax = 0f
    private var uay = 0f
    private var ubx = 0f
    private var uby = 0f
    private var rax = 0f
    private var ray = 0f
    private var rbx = 0f
    private var rby = 0f
    private var effectiveMass = 0f
    private var ecart = 0f
    private var posScale = 0f
    private var posImpulse = 0f
    private var solvable = false
    private var slack = false
    private val pointA = FloatArray(3)
    private val pointB = FloatArray(3)

    companion object {
        /** Part de l'écart rattrapée à chaque pas par la passe de position. */
        private const val POSITION_RATE = 0.35f

        /** En deçà, un brin n'a plus de direction : on ne résout pas. */
        private const val MIN_BRIN = 1e-3f

        /**
         * Passe une corde sur deux réas, en prenant les longueurs actuelles comme
         * référence. Les ancrages sont donnés en coordonnées du monde.
         */
        fun over(
            groundAX: Float, groundAY: Float,
            groundBX: Float, groundBY: Float,
            a: PhysBody, ax: Float, ay: Float,
            b: PhysBody, bx: Float, by: Float,
            ratio: Float = 1f
        ): PulleyJoint {
            val j = PulleyJoint(a, b)
            j.groundAX = groundAX
            j.groundAY = groundAY
            j.groundBX = groundBX
            j.groundBY = groundBY
            j.setWorldAnchors(ax, ay, bx, by)
            j.ratio = ratio
            j.constant = hypot(ax - groundAX, ay - groundAY) +
                ratio * hypot(bx - groundBX, by - groundBY)
            return j
        }
    }

    /** Pose les deux accroches d'après des points du monde et la pose actuelle. */
    fun setWorldAnchors(ax: Float, ay: Float, bx: Float, by: Float) {
        val ca = kotlin.math.cos(a.angle)
        val sa = kotlin.math.sin(a.angle)
        val dxa = ax - a.x
        val dya = ay - a.y
        localAnchorAX = dxa * ca + dya * sa
        localAnchorAY = -dxa * sa + dya * ca

        val cb = kotlin.math.cos(b.angle)
        val sb = kotlin.math.sin(b.angle)
        val dxb = bx - b.x
        val dyb = by - b.y
        localAnchorBX = dxb * cb + dyb * sb
        localAnchorBY = -dxb * sb + dyb * cb
    }

    /** Position monde de l'accroche portée par [a], dans [out]. */
    fun anchorAWorld(out: FloatArray) = a.localToWorld(localAnchorAX, localAnchorAY, out)

    /** Position monde de l'accroche portée par [b], dans [out]. */
    fun anchorBWorld(out: FloatArray) = b.localToWorld(localAnchorBX, localAnchorBY, out)

    /** Vrai si la corde pend en ce moment : elle ne transmet alors rien. */
    val isSlack: Boolean get() = slack

    /** Tension de la corde, en kg·m/s. Nulle quand elle est molle. */
    val tension: Float get() = impulse

    override fun reset() {
        impulse = 0f
        posImpulse = 0f
    }

    override fun preStep(invDt: Float) {
        solvable = false
        slack = false
        posImpulse = 0f
        if (!enabled || !a.inWorld || !b.inWorld) return

        a.localToWorld(localAnchorAX, localAnchorAY, pointA)
        b.localToWorld(localAnchorBX, localAnchorBY, pointB)

        rax = pointA[0] - a.x
        ray = pointA[1] - a.y
        rbx = pointB[0] - b.x
        rby = pointB[1] - b.y

        var dx = pointA[0] - groundAX
        var dy = pointA[1] - groundAY
        lengthA = hypot(dx, dy)
        if (lengthA < MIN_BRIN) return
        uax = dx / lengthA
        uay = dy / lengthA

        dx = pointB[0] - groundBX
        dy = pointB[1] - groundBY
        lengthB = hypot(dx, dy)
        if (lengthB < MIN_BRIN) return
        ubx = dx / lengthB
        uby = dy / lengthB

        // **Le mou restant** : positif quand la somme des brins est plus courte que la
        // corde, donc que la corde pend ; négatif quand elle est tendue et doit tirer.
        //
        // Ce signe-là et pas l'inverse, parce qu'il doit être celui de sa propre dérivée :
        // `cdot` plus bas mesure la vitesse à laquelle ce mou change, et la passe de
        // position additionne les deux. Les avoir pris en sens contraires est exactement
        // l'erreur qui a fait **pousser** la corde au lieu de tirer — le godet s'enfonçait
        // dans le sol et le contrepoids montait au plafond tout seul.
        ecart = constant - lengthA - ratio * lengthB

        // Une corde plus longue que la somme des deux brins pend : elle ne transmet
        // rien, et l'impulsion memorisee doit etre oubliee pour qu'elle reparte molle.
        //
        // Le serrage de l'impulsion ne suffit pas a l'obtenir, et c'est ce qui a ete
        // mesure : deux corps qui tombent ensemble raccourcissent la corde tous les deux,
        // donc la contrainte de **vitesse** voulait les retenir alors que la corde pendait
        // a un metre quatre-vingts de mou. Un serrage ne connait que le signe de l'effort,
        // pas l'etat de la corde ; seul l'ecart le connait.
        if (ecart > 0f) {
            slack = true
            impulse = 0f
            return
        }

        val ruA = rax * uay - ray * uax
        val ruB = rbx * uby - rby * ubx
        val mA = a.invMass + a.invI * ruA * ruA
        val mB = b.invMass + b.invI * ruB * ruB
        val k = mA + ratio * ratio * mB
        if (k <= 1e-9f) return
        effectiveMass = 1f / k
        posScale = POSITION_RATE * invDt

        // Réapplication de l'impulsion mémorisée : c'est ce qui donne à la liaison sa
        // raideur sans avoir à multiplier les itérations.
        appliquer(impulse, position = false)
        solvable = true
    }

    override fun applyImpulse() {
        if (!solvable) return
        val vpa = (a.vx - a.omega * ray) * uax + (a.vy + a.omega * rax) * uay
        val vpb = (b.vx - b.omega * rby) * ubx + (b.vy + b.omega * rbx) * uby
        val cdot = -vpa - ratio * vpb

        val old = impulse
        // La corde ne pousse jamais : l'impulsion cumulée reste positive, ce qui tire les
        // deux corps vers leurs réas.
        impulse = maxOf(old - effectiveMass * cdot, 0f)
        appliquer(impulse - old, position = false)
    }

    override fun applyPositionImpulse() {
        if (!solvable) return
        val vpa = (a.pvx - a.pomega * ray) * uax + (a.pvy + a.pomega * rax) * uay
        val vpb = (b.pvx - b.pomega * rby) * ubx + (b.pvy + b.pomega * rbx) * uby
        val cdot = -vpa - ratio * vpb

        val old = posImpulse
        posImpulse = maxOf(old - effectiveMass * (ecart * posScale + cdot), 0f)
        appliquer(posImpulse - old, position = true)
    }

    /**
     * Verse une impulsion sur les deux corps, soit sur les vraies vitesses, soit sur les
     * vitesses fantômes de la passe de position.
     *
     * Les deux passes appliquent exactement la même chose à un nom de champ près : les
     * écrire deux fois, c'est se garantir qu'un jour l'une des deux aura un signe de
     * moins que l'autre.
     */
    private fun appliquer(dP: Float, position: Boolean) {
        if (dP == 0f) return
        val pax = -dP * uax
        val pay = -dP * uay
        val pbx = -ratio * dP * ubx
        val pby = -ratio * dP * uby
        if (position) {
            a.pvx += a.invMass * pax
            a.pvy += a.invMass * pay
            a.pomega += a.invI * (rax * pay - ray * pax)
            b.pvx += b.invMass * pbx
            b.pvy += b.invMass * pby
            b.pomega += b.invI * (rbx * pby - rby * pbx)
        } else {
            a.vx += a.invMass * pax
            a.vy += a.invMass * pay
            a.omega += a.invI * (rax * pay - ray * pax)
            b.vx += b.invMass * pbx
            b.vy += b.invMass * pby
            b.omega += b.invI * (rbx * pby - rby * pbx)
        }
    }
}
