package com.Atom2Universe.app.games.physics

import kotlin.math.abs

/**
 * Roue libre entre deux arbres.
 *
 * Quand l'arbre [a] tourne dans [inputDirection], il peut pousser [b] jusqu'à la
 * vitesse `ratio × ωa`. Si [b] va déjà plus vite, ou si [a] s'arrête/revient en
 * arrière, la liaison s'ouvre instantanément : aucune énergie ne repart vers les
 * pédales. C'est le comportement du pignon arrière d'un vélo.
 */
class OneWayRotaryJoint(
    a: PhysBody,
    b: PhysBody,
    ratio: Float,
    inputDirection: Int = 1
) : Joint(a, b) {

    var ratio = ratio
        set(value) {
            require(value.isFinite() && abs(value) > 1e-6f)
            field = value
            reset()
        }

    var inputDirection = if (inputDirection < 0) -1 else 1
    var maxTorqueOnB = Float.POSITIVE_INFINITY
    var requiresLayerOverlap = true

    var engaged = false
        private set
    var slipping = false
        private set
    var reactionTorqueOnB = 0f
        private set

    private var effectiveMass = 0f
    private var accumulatedImpulse = 0f
    private var maxImpulse = Float.POSITIVE_INFINITY
    private var lastInvDt = 0f

    init {
        require(ratio.isFinite() && abs(ratio) > 1e-6f)
    }

    override fun reset() {
        accumulatedImpulse = 0f
        reactionTorqueOnB = 0f
        engaged = false
        slipping = false
    }

    override fun preStep(invDt: Float) {
        engaged = false
        slipping = false
        reactionTorqueOnB = 0f
        if (!enabled || !a.inWorld || !b.inWorld) return
        if (requiresLayerOverlap && !a.sharesCollisionLayer(b)) {
            accumulatedImpulse = 0f
            return
        }

        val inputForward = a.omega * inputDirection > 1e-4f
        val speedError = (b.omega - ratio * a.omega) * inputDirection
        // Une roue menée plus rapide est en roue libre. On oublie aussi l'impulsion
        // précédente pour qu'aucun warm-start ne lui donne un coup de frein.
        if (!inputForward || speedError >= -1e-4f) {
            accumulatedImpulse = 0f
            return
        }

        val k = b.invI + ratio * ratio * a.invI
        if (k <= 1e-12f) return
        effectiveMass = 1f / k
        lastInvDt = invDt
        maxImpulse = if (maxTorqueOnB.isFinite()) {
            maxTorqueOnB.coerceAtLeast(0f) / invDt
        } else Float.POSITIVE_INFINITY
        accumulatedImpulse = accumulatedImpulse.coerceIn(0f, maxImpulse)
        val warmImpulse = accumulatedImpulse * inputDirection
        a.omega -= a.invI * ratio * warmImpulse
        b.omega += b.invI * warmImpulse
        engaged = true
    }

    override fun applyImpulse() {
        if (!engaged) return
        val speedError = (b.omega - ratio * a.omega) * inputDirection
        var impulse = -effectiveMass * speedError
        val old = accumulatedImpulse
        accumulatedImpulse = (old + impulse).coerceIn(0f, maxImpulse)
        impulse = accumulatedImpulse - old
        val signedImpulse = impulse * inputDirection
        a.omega -= a.invI * ratio * signedImpulse
        b.omega += b.invI * signedImpulse
        reactionTorqueOnB = accumulatedImpulse * lastInvDt * inputDirection
        slipping = maxImpulse.isFinite() && accumulatedImpulse >= maxImpulse * 0.999f && speedError < -1e-3f
    }

    override fun applyPositionImpulse() = Unit
}
