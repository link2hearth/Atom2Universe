package com.Atom2Universe.app.games.physics

import kotlin.math.PI
import kotlin.math.pow

/** Propriétés de jeu d'un matériau de construction, toutes en unités SI. */
data class MachineMaterial(
    val name: String,
    val density: Float,
    val yieldStrength: Float,
    val friction: Float,
    val restitution: Float,
    /** Énergie volumique approximative nécessaire à la découpe, en J/m³. */
    val specificCuttingEnergy: Float
)

/** Presets d'ingénierie destinés au gameplay, pas tables de certification industrielle. */
object MachineMaterials {
    val WOOD = MachineMaterial("wood", 650f, 40e6f, 0.55f, 0.15f, 1.5e9f)
    val ALUMINUM = MachineMaterial("aluminum", 2_700f, 275e6f, 0.45f, 0.2f, 12e9f)
    val STEEL = MachineMaterial("steel", 7_850f, 250e6f, 0.6f, 0.1f, 30e9f)
    val TITANIUM = MachineMaterial("titanium", 4_500f, 830e6f, 0.5f, 0.12f, 45e9f)
    val RUBBER = MachineMaterial("rubber", 1_100f, 12e6f, 1.1f, 0.65f, 4e9f)
    val ROCK = MachineMaterial("rock", 2_600f, 80e6f, 0.75f, 0.05f, 18e9f)
}

data class GearSpec(
    val pitchRadius: Float,
    val teeth: Int,
    val faceWidth: Float,
    val boreRadius: Float = 0f,
    val material: MachineMaterial = MachineMaterials.STEEL
) {
    init {
        require(pitchRadius > 0f && pitchRadius.isFinite()) { "le rayon primitif doit être positif" }
        require(teeth >= 3) { "un engrenage doit avoir au moins trois dents" }
        require(faceWidth > 0f) { "la largeur de denture doit être positive" }
        require(boreRadius >= 0f && boreRadius < pitchRadius) { "l'alésage doit rester dans la roue" }
    }

    /** Module métrique, en mètres par dent. */
    val module: Float get() = 2f * pitchRadius / teeth
    val outerRadius: Float get() = pitchRadius + module
    val rootRadius: Float get() = (pitchRadius - 1.25f * module).coerceAtLeast(boreRadius)
    val mass: Float
        get() = PI.toFloat() * (outerRadius * outerRadius - boreRadius * boreRadius) *
            faceWidth * material.density
}

data class FlywheelSpec(
    val outerRadius: Float,
    val innerRadius: Float,
    val width: Float,
    val material: MachineMaterial = MachineMaterials.STEEL
) {
    init {
        require(outerRadius > 0f && outerRadius.isFinite()) { "le volant doit avoir un rayon positif" }
        require(innerRadius >= 0f && innerRadius < outerRadius) { "le rayon intérieur est invalide" }
        require(width > 0f) { "le volant doit avoir une largeur positive" }
    }

    val mass: Float
        get() = PI.toFloat() * (outerRadius * outerRadius - innerRadius * innerRadius) *
            width * material.density
    val inertia: Float
        get() = 0.5f * mass * (outerRadius * outerRadius + innerRadius * innerRadius)
    fun energyAt(omega: Float): Float = 0.5f * inertia * omega * omega
}

/** Dimensionnement simplifié d'une cuve cylindrique fermée. */
data class PressureVesselSpec(
    val innerRadius: Float,
    val innerLength: Float,
    val workingPressure: Float,
    val safetyFactor: Float = 2.5f,
    val material: MachineMaterial = MachineMaterials.STEEL
) {
    init {
        require(innerRadius > 0f && innerLength > 0f) { "les dimensions de cuve doivent être positives" }
        require(workingPressure >= 0f) { "la pression doit être positive" }
        require(safetyFactor >= 1f) { "le coefficient de sécurité doit être au moins égal à 1" }
    }

    /** Épaisseur minimale par contrainte circonférentielle, modèle de gameplay. */
    val wallThickness: Float
        get() = workingPressure * innerRadius * safetyFactor / material.yieldStrength
    val outerRadius: Float get() = innerRadius + wallThickness
    val internalVolume: Float get() = PI.toFloat() * innerRadius * innerRadius * innerLength
    val shellMass: Float
        get() {
            val side = PI.toFloat() * (outerRadius * outerRadius - innerRadius * innerRadius) * innerLength
            val caps = 2f * PI.toFloat() * outerRadius * outerRadius * wallThickness
            return (side + caps) * material.density
        }
}

/** Fabriques de corps rigides correspondant aux pièces mécaniques configurables. */
object MachinePartFactory {
    fun gear(spec: GearSpec): PhysBody = PhysBody.circle(spec.outerRadius, spec.mass).apply {
        friction = spec.material.friction
        restitution = spec.material.restitution
        // Approximation annulaire lorsque la roue possède un alésage important.
        inertiaScale = (spec.outerRadius * spec.outerRadius + spec.boreRadius * spec.boreRadius) /
            (spec.outerRadius * spec.outerRadius)
        tag = spec
    }

    fun flywheel(spec: FlywheelSpec): PhysBody = PhysBody.circle(spec.outerRadius, spec.mass).apply {
        friction = spec.material.friction
        restitution = spec.material.restitution
        inertiaScale = (spec.outerRadius * spec.outerRadius + spec.innerRadius * spec.innerRadius) /
            (spec.outerRadius * spec.outerRadius)
        tag = spec
    }

    fun pressureVessel(spec: PressureVesselSpec): PhysBody =
        PhysBody(spec.innerLength / 2f + spec.wallThickness, spec.outerRadius, spec.shellMass).apply {
            friction = spec.material.friction
            restitution = spec.material.restitution
            tag = spec
        }
}

/** Estimation de l'action d'un jet sur un matériau. */
object JetCuttingModel {
    data class CutResult(
        val canCut: Boolean,
        val dynamicPressure: Float,
        val penetrationSpeed: Float,
        val removedVolumePerSecond: Float
    )

    /**
     * [kerfArea] est la section de matière retirée. [abrasiveMultiplier] permet de
     * représenter un jet chargé en grenat sans coder chaque grain.
     */
    fun evaluate(
        jet: JetOutput,
        material: MachineMaterial,
        kerfArea: Float,
        efficiency: Float = 0.35f,
        abrasiveMultiplier: Float = 1f,
        fluidDensity: Float = PhysicsConstants.WATER_DENSITY
    ): CutResult {
        require(kerfArea > 0f) { "la section de coupe doit être positive" }
        val dynamicPressure = 0.5f * fluidDensity * jet.exitVelocity.pow(2)
        val canCut = dynamicPressure * abrasiveMultiplier >= material.yieldStrength
        if (!canCut || jet.kineticPower <= 0f) return CutResult(false, dynamicPressure, 0f, 0f)
        val usefulPower = jet.kineticPower * efficiency.coerceIn(0f, 1f) *
            abrasiveMultiplier.coerceAtLeast(0f)
        val removed = usefulPower / material.specificCuttingEnergy
        return CutResult(true, dynamicPressure, removed / kerfArea, removed)
    }
}
