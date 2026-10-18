package com.Atom2Universe.app.games.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Accumulateur de liquide sous pression.
 *
 * [storedEnergy] représente le travail encore disponible derrière le liquide. Le
 * modèle est celui d'un accumulateur à pression quasi constante : retirer `dV`
 * consomme `P·dV`. C'est adapté à une pompe hydraulique, une presse et une réserve
 * de découpeuse à eau sans prétendre simuler chaque onde dans le tuyau.
 */
class PressurizedLiquidTank(
    initialLiquidVolume: Float,
    initialGaugePressure: Float = 0f,
    val density: Float = PhysicsConstants.WATER_DENSITY,
    val maxGaugePressure: Float = Float.POSITIVE_INFINITY
) {
    init {
        require(initialLiquidVolume > 0f) { "le réservoir doit contenir du liquide" }
        require(initialGaugePressure >= 0f) { "la pression liquide doit être positive" }
        require(density > 0f) { "la masse volumique doit être positive" }
        require(maxGaugePressure > 0f) { "la pression maximale doit être positive" }
        require(initialGaugePressure <= maxGaugePressure) { "la pression initiale dépasse la cuve" }
    }

    var liquidVolume = initialLiquidVolume
        private set
    var storedEnergy = initialGaugePressure * initialLiquidVolume
        private set

    val gaugePressure: Float
        get() = if (liquidVolume > 1e-12f) storedEnergy / liquidVolume else 0f

    val liquidMass: Float get() = liquidVolume * density

    /** Ajoute du travail de pompe, avec soupape à [maxGaugePressure]. */
    fun pump(powerWatts: Float, dt: Float, efficiency: Float = 1f): Float {
        if (powerWatts <= 0f || dt <= 0f) return 0f
        val requested = powerWatts * dt * efficiency.coerceIn(0f, 1f)
        val capacity = maxGaugePressure * liquidVolume
        val accepted = minOf(requested, (capacity - storedEnergy).coerceAtLeast(0f))
        storedEnergy += accepted
        return accepted
    }

    /** Ajoute du liquide déjà pressurisé ; volume et énergie sont conservés. */
    fun fill(volume: Float, gaugePressure: Float = 0f) {
        if (volume <= 0f) return
        liquidVolume += volume
        storedEnergy += gaugePressure.coerceAtLeast(0f) * volume
        val cap = maxGaugePressure * liquidVolume
        if (storedEnergy > cap) storedEnergy = cap
    }

    /** Retire du liquide et l'énergie de pression qui l'accompagne. */
    internal fun withdraw(requestedVolume: Float): Pair<Float, Float> {
        if (requestedVolume <= 0f || liquidVolume <= 1e-12f) return 0f to 0f
        val moved = minOf(requestedVolume, liquidVolume * 0.5f)
        val pressure = gaugePressure
        val energy = minOf(storedEnergy, pressure * moved)
        liquidVolume -= moved
        storedEnergy -= energy
        return moved to energy
    }
}

/** Conduite hydraulique entre deux accumulateurs, avec ouverture et clapet optionnel. */
class LiquidPipe(
    val a: PressurizedLiquidTank,
    val b: PressurizedLiquidTank,
    val area: Float,
    val dischargeCoefficient: Float = 0.72f
) {
    init {
        require(area > 0f) { "la conduite doit avoir une section positive" }
        require(dischargeCoefficient > 0f) { "le coefficient de débit doit être positif" }
    }

    var opening = 1f
    var checkValveAToB = false
    var volumeFlow = 0f
        private set
    var powerLoss = 0f
        private set

    fun step(dt: Float) {
        volumeFlow = 0f
        powerLoss = 0f
        if (dt <= 0f || opening <= 0f) return
        val aToB = a.gaugePressure >= b.gaugePressure
        if (!aToB && checkValveAToB) return
        val upstream = if (aToB) a else b
        val downstream = if (aToB) b else a
        val deltaP = upstream.gaugePressure - downstream.gaugePressure
        if (deltaP <= 0f) return

        val q = dischargeCoefficient * area * opening.coerceIn(0f, 1f) *
            sqrt(2f * deltaP / upstream.density)
        val (moved, movedEnergy) = upstream.withdraw(q * dt)
        val downstreamEnergy = downstream.gaugePressure * moved
        downstream.fill(moved, downstream.gaugePressure)
        // La différence de pression est dissipée dans la restriction.
        powerLoss = ((movedEnergy - downstreamEnergy) / dt).coerceAtLeast(0f)
        volumeFlow = (if (aToB) 1f else -1f) * moved / dt
    }
}

/** Résultat instantané d'une buse liquide. */
data class JetOutput(
    val pressure: Float,
    val exitVelocity: Float,
    val volumeFlow: Float,
    val massFlow: Float,
    val thrust: Float,
    val kineticPower: Float,
    val consumedPower: Float
)

/**
 * Buse propulsive ou de découpe alimentée par un [PressurizedLiquidTank].
 *
 * La vitesse suit Bernoulli, `v = Cd·sqrt(2P/ρ)`. La réaction est appliquée au corps
 * porteur en sens opposé au jet. Une buse minuscule à 600 MPa et un canon à eau de
 * plusieurs mètres diffèrent uniquement par leurs paramètres.
 */
class LiquidJetNozzle(
    val source: PressurizedLiquidTank,
    val area: Float,
    val dischargeCoefficient: Float = 0.92f
) {
    init {
        require(area > 0f) { "la buse doit avoir une section positive" }
        require(dischargeCoefficient > 0f) { "le coefficient de buse doit être positif" }
    }

    var carrier: PhysBody? = null
    var localX = 0f
    var localY = 0f
    var localDirectionX = 1f
    var localDirectionY = 0f
    var opening = 1f

    fun fire(dt: Float): JetOutput {
        val pressure = source.gaugePressure
        if (dt <= 0f || opening <= 0f || pressure <= 0f) return ZERO_JET
        val idealVelocity = sqrt(2f * pressure / source.density)
        val velocity = dischargeCoefficient * idealVelocity
        val requestedFlow = area * opening.coerceIn(0f, 1f) * velocity
        val (volume, energy) = source.withdraw(requestedFlow * dt)
        if (volume <= 0f) return ZERO_JET
        val q = volume / dt
        val massFlow = q * source.density
        val thrust = massFlow * velocity
        val kineticPower = 0.5f * massFlow * velocity * velocity
        val consumedPower = energy / dt

        carrier?.let { body ->
            val c = cos(body.angle); val s = sin(body.angle)
            val length = kotlin.math.hypot(localDirectionX, localDirectionY).coerceAtLeast(1e-6f)
            val dx = (localDirectionX * c - localDirectionY * s) / length
            val dy = (localDirectionX * s + localDirectionY * c) / length
            val point = FloatArray(2)
            body.localToWorld(localX, localY, point)
            body.applyForceAtWorldPoint(-thrust * dx, -thrust * dy, point[0], point[1])
        }

        return JetOutput(pressure, velocity, q, massFlow, thrust, kineticPower, consumedPower)
    }

    companion object {
        val ZERO_JET = JetOutput(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/** Pompe volumétrique entraînée par un arbre physique. */
class ShaftDrivenLiquidPump(
    val shaft: PhysBody,
    val tank: PressurizedLiquidTank,
    /** Volume déplacé par tour d'arbre, en m³/tr. */
    val displacementPerRevolution: Float
) {
    init {
        require(displacementPerRevolution > 0f) { "la cylindrée doit être positive" }
    }

    var efficiency = 0.85f
    var maxTorque = Float.POSITIVE_INFINITY
    /** Couple minimal d'amorçage/compression fourni par l'entraînement, en N·m. */
    var primingTorque = 0f
    var targetPressure = Float.POSITIVE_INFINITY
    var mechanicalPower = 0f
        private set
    var hydraulicPower = 0f
        private set

    /** À appeler avant le pas physique ; la pompe charge le réservoir et freine l'arbre. */
    fun update(dt: Float) {
        mechanicalPower = 0f
        hydraulicPower = 0f
        val omega = shaft.omega
        if (dt <= 0f || abs(omega) < 1e-4f || tank.gaugePressure >= targetPressure) return
        val theoreticalTorque = tank.gaugePressure * displacementPerRevolution / (2f * PI.toFloat())
        val torque = minOf(maxOf(theoreticalTorque, primingTorque.coerceAtLeast(0f)), abs(maxTorque))
        mechanicalPower = torque * abs(omega)
        val acceptedEnergy = tank.pump(mechanicalPower, dt, efficiency)
        hydraulicPower = acceptedEnergy / dt
        if (acceptedEnergy > 0f) shaft.applyTorque(-sign(omega) * torque)
    }
}
