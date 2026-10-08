package com.Atom2Universe.app.games.physics

import kotlin.math.abs

/**
 * Transmission idéale entre deux arbres : `ωb + ratio × ωa = 0`.
 *
 * Un [ratio] positif représente deux engrenages extérieurs, qui tournent en sens
 * opposés. Un rapport négatif donne le même sens (courroie non croisée ou engrenage
 * intérieur). Le rapport peut venir des nombres de dents ou des rayons primitifs ;
 * aucune taille maximale n'est imposée.
 *
 * [maxTorqueOnB] transforme la liaison rigide en embrayage/limiteur de couple. Une
 * valeur finie autorise le glissement et désactive logiquement le rappel de phase :
 * un embrayage qui a patiné ne doit pas récupérer sa dent perdue en créant de l'énergie.
 */
class GearJoint(a: PhysBody, b: PhysBody, ratio: Float) : Joint(a, b) {
    var ratio = ratio
        set(value) {
            require(value.isFinite() && abs(value) > 1e-6f) { "le rapport doit être fini et non nul" }
            field = value
            capturePhase()
            reset()
        }

    /** Couple transmissible sur l'arbre B, en N·m. Infini = dents rigides. */
    var maxTorqueOnB = Float.POSITIVE_INFINITY

    /** Une denture réelle ne transmet que si les roues partagent une couche. */
    var requiresLayerOverlap = true

    /** Souplesse numérique de la transmission ; zéro convient dans la majorité des cas. */
    var softness = 0f

    var impulse = 0f
        private set
    var reactionTorqueOnB = 0f
        private set
    var slipping = false
        private set

    private var phase = 0f
    private var effectiveMass = 0f
    private var maxImpulse = Float.POSITIVE_INFINITY
    private var posImpulse = 0f
    private var posScale = 0f
    private var solvable = false
    private var lastInvDt = 0f

    init {
        require(ratio.isFinite() && abs(ratio) > 1e-6f) { "le rapport doit être fini et non nul" }
        capturePhase()
    }

    /** Reprend les angles actuels comme position de denture sans donner d'à-coup. */
    fun capturePhase() {
        phase = b.angle + ratio * a.angle
    }

    override fun reset() {
        impulse = 0f
        posImpulse = 0f
        reactionTorqueOnB = 0f
        slipping = false
    }

    override fun preStep(invDt: Float) {
        solvable = false
        posImpulse = 0f
        reactionTorqueOnB = 0f
        slipping = false
        if (!enabled || !a.inWorld || !b.inWorld) return
        if (requiresLayerOverlap && !a.sharesCollisionLayer(b)) {
            impulse = 0f
            return
        }

        val k = b.invI + ratio * ratio * a.invI + softness
        if (k <= 1e-12f) return
        effectiveMass = 1f / k
        lastInvDt = invDt
        maxImpulse = if (maxTorqueOnB.isFinite()) {
            maxTorqueOnB.coerceAtLeast(0f) / invDt
        } else {
            Float.POSITIVE_INFINITY
        }
        posScale = 0.35f * invDt

        impulse = impulse.coerceIn(-maxImpulse, maxImpulse)
        a.omega += a.invI * ratio * impulse
        b.omega += b.invI * impulse
        solvable = true
    }

    override fun applyImpulse() {
        if (!solvable) return
        val cDot = b.omega + ratio * a.omega
        var dP = -effectiveMass * (cDot + softness * impulse)
        val old = impulse
        impulse = (old + dP).coerceIn(-maxImpulse, maxImpulse)
        dP = impulse - old
        a.omega += a.invI * ratio * dP
        b.omega += b.invI * dP
        reactionTorqueOnB = impulse * lastInvDt
        slipping = maxImpulse.isFinite() && abs(impulse) >= maxImpulse * 0.999f && abs(cDot) > 1e-3f
    }

    override fun applyPositionImpulse() {
        if (!solvable || maxImpulse.isFinite()) return
        val error = (b.angle + ratio * a.angle) - phase
        val cDot = b.pomega + ratio * a.pomega
        val dP = -effectiveMass * (error * posScale + cDot)
        posImpulse += dP
        a.pomega += a.invI * ratio * dP
        b.pomega += b.invI * dP
    }

    companion object {
        /** Deux roues dentées extérieures à partir de leurs nombres de dents. */
        fun external(a: PhysBody, teethA: Int, b: PhysBody, teethB: Int): GearJoint {
            require(teethA > 0 && teethB > 0) { "une roue dentée doit avoir des dents" }
            return GearJoint(a, b, teethA.toFloat() / teethB.toFloat())
        }

        /** Engrenage intérieur : les deux roues tournent dans le même sens. */
        fun internal(a: PhysBody, teethA: Int, b: PhysBody, teethB: Int): GearJoint {
            require(teethA > 0 && teethB > 0) { "une roue dentée doit avoir des dents" }
            return GearJoint(a, b, -teethA.toFloat() / teethB.toFloat())
        }

        /** Courroie ; [crossed] inverse le sens de la roue menée. */
        fun belt(a: PhysBody, radiusA: Float, b: PhysBody, radiusB: Float, crossed: Boolean = false): GearJoint {
            require(radiusA > 0f && radiusB > 0f) { "les poulies doivent avoir un rayon positif" }
            val ratio = radiusA / radiusB * if (crossed) 1f else -1f
            return GearJoint(a, b, ratio)
        }
    }
}
