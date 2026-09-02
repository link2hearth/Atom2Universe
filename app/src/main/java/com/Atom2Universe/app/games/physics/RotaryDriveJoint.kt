package com.Atom2Universe.app.games.physics

/**
 * Une source de couple montée sur un arbre : un moteur.
 *
 * Un moteur réel n'est pas une vitesse imposée, c'est **deux nombres** : le couple
 * qu'il peut arracher ([maxTorque]) et la vitesse au-delà de laquelle il ne pousse
 * plus ([targetOmega]). Un cheval attelé tire fort mais marche à un mètre par
 * seconde, quoi qu'on lui accroche derrière ; une aile de moulin tourne vite mais
 * ne développe presque rien. C'est de cet écart que naît tout l'intérêt d'un train
 * d'engrenages : il échange l'un contre l'autre.
 *
 * **Il pousse, il ne retient jamais.** L'impulsion accumulée est bornée du côté de
 * [targetOmega] seulement : une roue qui va déjà plus vite que le moteur (parce
 * qu'un volant la porte, ou parce qu'on l'a lancée à la main) le laisse simplement
 * en roue libre. Sans cette borne, un moteur lent deviendrait un frein dès que la
 * machine dépasse sa vitesse, et surtout il **prendrait** de l'énergie à la machine
 * pour la rendre au décor, ce qu'aucun moteur ne fait.
 *
 * Ce qu'il ajoute par pas est borné par `couple × vitesse × dt` : le garde-fou du
 * moteur physique — le solveur ne crée jamais d'énergie — reste vrai partout
 * ailleurs, et ici l'énergie ajoutée est celle qu'un moteur fournit vraiment.
 */
class RotaryDriveJoint(a: PhysBody, b: PhysBody) : Joint(a, b) {

    /**
     * Vitesse libre, en rad/s, signée : le sens de rotation entraîné. À zéro le
     * moteur est débrayé.
     */
    var targetOmega = 0f

    /** Couple maximal, en N·m. À zéro le moteur est débrayé. */
    var maxTorque = 0f

    /** Couple réellement délivré au dernier pas, en N·m. */
    var torque = 0f
        private set

    /** Vrai quand le moteur tire à plein couple : la machine est trop lourde pour lui. */
    var stalled = false
        private set

    private var effectiveMass = 0f
    private var impulse = 0f
    private var maxImpulse = 0f
    private var lastInvDt = 0f
    private var driving = false

    override fun reset() {
        impulse = 0f
        torque = 0f
        stalled = false
    }

    override fun preStep(invDt: Float) {
        driving = false
        torque = 0f
        stalled = false
        if (!enabled || !a.inWorld || !b.inWorld) return
        if (maxTorque <= 0f || targetOmega == 0f ||
            !maxTorque.isFinite() || !targetOmega.isFinite()
        ) {
            impulse = 0f
            return
        }
        val k = a.invI + b.invI
        if (k <= 1e-12f) return
        effectiveMass = 1f / k
        lastInvDt = invDt
        // Couple × durée : changer la fréquence de simulation ne change ni la force
        // du moteur ni sa puissance.
        maxImpulse = maxTorque / invDt
        impulse = clampToDriveSide(impulse)
        a.omega -= a.invI * impulse
        b.omega += b.invI * impulse
        driving = true
    }

    override fun applyImpulse() {
        if (!driving) return
        val error = targetOmega - (b.omega - a.omega)
        val old = impulse
        impulse = clampToDriveSide(old + effectiveMass * error)
        val delta = impulse - old
        a.omega -= a.invI * delta
        b.omega += b.invI * delta
        torque = impulse * lastInvDt
        stalled = kotlin.math.abs(impulse) >= maxImpulse * 0.999f
    }

    /** Le moteur ne tire que d'un côté : de zéro jusqu'à son couple, dans son sens. */
    private fun clampToDriveSide(value: Float): Float =
        if (targetOmega > 0f) value.coerceIn(0f, maxImpulse) else value.coerceIn(-maxImpulse, 0f)

    override fun applyPositionImpulse() = Unit
}
