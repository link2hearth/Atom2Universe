package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Réservoir de gaz idéal comprimé ou mis sous vide.
 *
 * Les pressions sont absolues en interne et toutes les unités sont SI. Entre deux
 * apports d'énergie, `P × V^γ` reste constant : une détente refroidit et perd de la
 * pression au lieu de fournir une réserve infinie. Ce modèle adiabatique simple est
 * adapté aux tirs et coups de vérin rapides ; une future simulation thermique pourra
 * faire lentement revenir γ vers 1 pour les évolutions lentes.
 */
class PneumaticChamber(
    initialVolume: Float,
    initialGaugePressure: Float = 0f,
    val ambientPressure: Float = PhysicsConstants.ATMOSPHERIC_PRESSURE,
    val heatCapacityRatio: Float = PhysicsConstants.AIR_HEAT_CAPACITY_RATIO,
    val gasConstant: Float = PhysicsConstants.AIR_GAS_CONSTANT,
    initialTemperature: Float = PhysicsConstants.ROOM_TEMPERATURE
) {
    init {
        require(initialVolume > 0f) { "le volume pneumatique doit être positif" }
        require(ambientPressure > 0f) { "la pression ambiante doit être positive" }
        require(heatCapacityRatio > 1f) { "gamma doit être supérieur à 1" }
        require(gasConstant > 0f && initialTemperature > 0f) { "les propriétés du gaz doivent être positives" }
        require(initialGaugePressure > -ambientPressure) { "la pression absolue doit rester positive" }
    }

    var volume = initialVolume
        private set

    private var gasEnergy =
        (ambientPressure + initialGaugePressure) * initialVolume / (heatCapacityRatio - 1f)

    var gasMass =
        (ambientPressure + initialGaugePressure) * initialVolume / (gasConstant * initialTemperature)
        private set

    val absolutePressure: Float
        get() = (heatCapacityRatio - 1f) * gasEnergy / volume

    val gaugePressure: Float
        get() = absolutePressure - ambientPressure

    /** Énergie interne du gaz, utile pour les bilans du sandbox. */
    val internalEnergy: Float get() = gasEnergy

    val density: Float get() = gasMass / volume

    val temperature: Float
        get() = if (gasMass > 1e-12f) {
            gasEnergy * (heatCapacityRatio - 1f) / (gasMass * gasConstant)
        } else 0f

    /**
     * Énergie mécanique maximale récupérable avant équilibre avec l'atmosphère.
     * Elle vaut zéro à pression ambiante, aussi bien pour un gros que pour un petit réservoir.
     */
    val availableEnergy: Float
        get() {
            val invariant = absolutePressure * volume.pow(heatCapacityRatio)
            val equilibriumVolume =
                (invariant / ambientPressure).pow(1f / heatCapacityRatio)
            val equilibriumInternal =
                ambientPressure * equilibriumVolume / (heatCapacityRatio - 1f)
            return (internalEnergy - equilibriumInternal +
                ambientPressure * (volume - equilibriumVolume)).coerceAtLeast(0f)
        }

    /** Change le volume sans ajouter de chaleur : compression et détente sont adiabatiques. */
    fun setVolume(value: Float) {
        require(value > 0f && value.isFinite()) { "le volume pneumatique doit être fini et positif" }
        // Travail adiabatique d'un gaz parfait : U₂/U₁ = (V₁/V₂)^(γ-1).
        gasEnergy *= (volume / value).pow(heatCapacityRatio - 1f)
        volume = value
    }

    /** Recharge instantanément le réservoir à la pression manométrique demandée. */
    fun setGaugePressure(value: Float) {
        require(value.isFinite() && value > -ambientPressure) {
            "la pression absolue doit rester finie et positive"
        }
        val oldTemperature = temperature.coerceAtLeast(1f)
        gasEnergy = (ambientPressure + value) * volume / (heatCapacityRatio - 1f)
        gasMass = (ambientPressure + value) * volume / (gasConstant * oldTemperature)
    }

    /**
     * Ajoute l'énergie fournie par une pompe au volume courant.
     * La hausse de pression respecte `U = P·V/(γ-1)` et [efficiency] absorbe les pertes.
     */
    fun pump(powerWatts: Float, dt: Float, efficiency: Float = 1f) {
        if (powerWatts <= 0f || dt <= 0f) return
        val energy = powerWatts * dt * efficiency.coerceIn(0f, 1f)
        gasEnergy += energy
    }

    /** Retire une masse de gaz bien mélangée et rend l'énergie qu'elle emporte. */
    internal fun extractGas(requestedMass: Float): Pair<Float, Float> {
        if (requestedMass <= 0f || gasMass <= 1e-12f) return 0f to 0f
        val movedMass = minOf(requestedMass, gasMass * 0.5f)
        val fraction = movedMass / gasMass
        val movedEnergy = gasEnergy * fraction
        gasMass -= movedMass
        gasEnergy -= movedEnergy
        return movedMass to movedEnergy
    }

    /** Reçoit du gaz et son énergie ; le mélange conserve masse et énergie. */
    internal fun acceptGas(mass: Float, energy: Float) {
        if (mass <= 0f || energy < 0f) return
        gasMass += mass
        gasEnergy += energy
    }
}

/**
 * Conduite ou vanne entre deux chambres pneumatiques.
 *
 * Le débit suit l'approximation d'orifice `Q = Cd·A·sqrt(2ΔP/ρ)`. La masse et
 * l'énergie transportées sont retirées d'un côté et ajoutées à l'autre : ouvrir une
 * vanne égalise les pressions sans inventer de gaz ni de joules. [opening] va de 0 à 1.
 */
class PneumaticPipe(
    val a: PneumaticChamber,
    val b: PneumaticChamber,
    val area: Float,
    val dischargeCoefficient: Float = 0.72f
) {
    init {
        require(area > 0f) { "la conduite doit avoir une section positive" }
        require(dischargeCoefficient > 0f) { "le coefficient de débit doit être positif" }
    }

    var opening = 1f
    var checkValveAToB = false
    var massFlow = 0f
        private set
    var energyFlow = 0f
        private set

    fun step(dt: Float) {
        massFlow = 0f
        energyFlow = 0f
        if (dt <= 0f || opening <= 0f) return
        val aToB = a.absolutePressure >= b.absolutePressure
        if (!aToB && checkValveAToB) return
        val upstream = if (aToB) a else b
        val downstream = if (aToB) b else a
        val deltaP = upstream.absolutePressure - downstream.absolutePressure
        if (deltaP <= 0f || upstream.density <= 1e-9f) return

        val flowArea = area * opening.coerceIn(0f, 1f)
        val volumeFlow = dischargeCoefficient * flowArea *
            kotlin.math.sqrt(2f * deltaP / upstream.density)
        val requestedMass = upstream.density * volumeFlow * dt
        val (movedMass, movedEnergy) = upstream.extractGas(requestedMass)
        downstream.acceptGas(movedMass, movedEnergy)
        val sign = if (aToB) 1f else -1f
        massFlow = sign * movedMass / dt
        energyFlow = sign * movedEnergy / dt
    }
}

/**
 * Vérin pneumatique simple effet entre deux corps.
 *
 * La chambre grandit avec l'écartement des ancrages. Sa pression pousse le piston
 * vers l'extérieur ; une dépression le tire vers l'intérieur. [updateAndApply]
 * s'appelle une fois avant `world.stepFrame(dt)` : les forces resteront valables
 * pendant tous les sous-pas de cette image.
 */
class PneumaticActuator(
    val a: PhysBody,
    val b: PhysBody,
    val chamber: PneumaticChamber,
    val pistonArea: Float,
    val deadVolume: Float,
    val stroke: Float
) {
    init {
        require(pistonArea > 0f) { "la surface du piston doit être positive" }
        require(deadVolume > 0f) { "le volume mort doit être positif" }
        require(stroke > 0f) { "la course doit être positive" }
    }

    var localAnchorAX = 0f
    var localAnchorAY = 0f
    var localAnchorBX = 0f
    var localAnchorBY = 0f

    /** Amortisseur visqueux du piston, en N·s/m. */
    var damping = 0f

    /** Soupape/limiteur mécanique de sécurité, en newtons. */
    var maxForce = Float.POSITIVE_INFINITY

    var lastForce = 0f
        private set
    var lastPower = 0f
        private set
    var extension = 0f
        private set

    private val pointA = FloatArray(2)
    private val pointB = FloatArray(2)

    fun updateAndApply() {
        a.localToWorld(localAnchorAX, localAnchorAY, pointA)
        b.localToWorld(localAnchorBX, localAnchorBY, pointB)
        val dx = pointB[0] - pointA[0]
        val dy = pointB[1] - pointA[1]
        val distance = hypot(dx, dy)
        if (distance < 1e-6f) {
            lastForce = 0f
            lastPower = 0f
            return
        }

        val nx = dx / distance
        val ny = dy / distance
        extension = distance.coerceIn(0f, stroke)
        chamber.setVolume(deadVolume + pistonArea * extension)

        val vaX = a.vx - a.omega * (pointA[1] - a.y)
        val vaY = a.vy + a.omega * (pointA[0] - a.x)
        val vbX = b.vx - b.omega * (pointB[1] - b.y)
        val vbY = b.vy + b.omega * (pointB[0] - b.x)
        val axialSpeed = (vbX - vaX) * nx + (vbY - vaY) * ny

        val pressureForce = chamber.gaugePressure * pistonArea
        lastForce = (pressureForce - damping.coerceAtLeast(0f) * axialSpeed)
            .coerceIn(-abs(maxForce), abs(maxForce))
        lastPower = lastForce * axialSpeed

        val fx = lastForce * nx
        val fy = lastForce * ny
        a.applyForceAtWorldPoint(-fx, -fy, pointA[0], pointA[1])
        b.applyForceAtWorldPoint(fx, fy, pointB[0], pointB[1])
    }

    /** Place les deux ancrages à partir de points monde dans la pose courante. */
    fun setWorldAnchors(ax: Float, ay: Float, bx: Float, by: Float) {
        val ca = cos(a.angle); val sa = sin(a.angle)
        val daX = ax - a.x; val daY = ay - a.y
        localAnchorAX = daX * ca + daY * sa
        localAnchorAY = -daX * sa + daY * ca

        val cb = cos(b.angle); val sb = sin(b.angle)
        val dbX = bx - b.x; val dbY = by - b.y
        localAnchorBX = dbX * cb + dbY * sb
        localAnchorBY = -dbX * sb + dbY * cb
    }
}

/**
 * Canon pneumatique générique : potato gun, lanceur d'essai ou canon industriel.
 *
 * Le projectile doit être guidé par une [PrismaticJoint] si le jeu veut représenter
 * le contact avec le tube. Cette classe ne fait que la thermodynamique : la chambre
 * se détend avec la course, pousse le projectile et applique le recul au bâti.
 */
class PressureLauncher(
    val frame: PhysBody,
    val projectile: PhysBody,
    val chamber: PneumaticChamber,
    val boreArea: Float,
    val barrelLength: Float,
    val deadVolume: Float
) {
    init {
        require(boreArea > 0f) { "la section du canon doit être positive" }
        require(barrelLength > 0f) { "le canon doit avoir une longueur positive" }
        require(deadVolume > 0f) { "le volume de chambre doit être positif" }
    }

    var localBreechX = 0f
    var localBreechY = 0f
    var localAxisX = 1f
    var localAxisY = 0f
    /** Frottement sec du joint et du tube, en newtons. */
    var sealFriction = 0f
    var maxForce = Float.POSITIVE_INFINITY

    var travel = 0f
        private set
    var released = false
        private set
    var lastForce = 0f
        private set
    var lastPower = 0f
        private set

    private val breech = FloatArray(2)

    fun updateAndApply() {
        frame.localToWorld(localBreechX, localBreechY, breech)
        val c = cos(frame.angle); val s = sin(frame.angle)
        val axisLength = hypot(localAxisX, localAxisY).coerceAtLeast(1e-6f)
        val nx = (localAxisX * c - localAxisY * s) / axisLength
        val ny = (localAxisX * s + localAxisY * c) / axisLength
        travel = ((projectile.x - breech[0]) * nx + (projectile.y - breech[1]) * ny)
            .coerceAtLeast(0f)
        if (travel >= barrelLength) {
            released = true
            lastForce = 0f
            lastPower = 0f
            return
        }

        chamber.setVolume(deadVolume + boreArea * travel)
        val projectileAxialSpeed = projectile.vx * nx + projectile.vy * ny
        val framePointVx = frame.vx - frame.omega * (breech[1] - frame.y)
        val framePointVy = frame.vy + frame.omega * (breech[0] - frame.x)
        val relativeSpeed = projectileAxialSpeed - (framePointVx * nx + framePointVy * ny)
        val pressureForce = chamber.gaugePressure * boreArea
        val friction = if (relativeSpeed > 0f) sealFriction.coerceAtLeast(0f) else 0f
        lastForce = (pressureForce - friction).coerceIn(0f, abs(maxForce))
        lastPower = lastForce * relativeSpeed.coerceAtLeast(0f)

        val fx = lastForce * nx
        val fy = lastForce * ny
        projectile.applyForce(fx, fy)
        frame.applyForceAtWorldPoint(-fx, -fy, breech[0], breech[1])
    }
}
