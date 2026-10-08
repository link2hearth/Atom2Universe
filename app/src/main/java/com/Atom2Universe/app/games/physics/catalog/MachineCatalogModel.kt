package com.Atom2Universe.app.games.physics.catalog

/** Famille fonctionnelle affichable plus tard dans un catalogue de construction. */
enum class PartCategory {
    STRUCTURE, FASTENER, ROTARY, TRANSMISSION, LINEAR,
    PNEUMATIC, HYDRAULIC, PROJECTILE, ENERGY, CONTROL, SENSOR, SAFETY
}

/** Ce que le moteur sait déjà faire de la définition. */
enum class SimulationSupport {
    /** Une classe dédiée existe déjà dans le moteur. */
    NATIVE,
    /** Réalisable en assemblant corps, joints, forces et circuits existants. */
    COMPOSABLE,
    /** Décrit et validé dans le catalogue, comportement spécialisé à coder plus tard. */
    CATALOG_ONLY
}

enum class ParameterUnit {
    NONE, BOOLEAN, COUNT,
    METER, SQUARE_METER, CUBIC_METER,
    KILOGRAM, KILOGRAM_PER_CUBIC_METER,
    SECOND, RADIAN, RADIAN_PER_SECOND,
    NEWTON, NEWTON_METER, PASCAL, WATT, JOULE,
    VOLT, AMPERE, OHM, RATIO, PERCENT
}

data class PartParameterDefinition(
    val key: String,
    val unit: ParameterUnit,
    val defaultValue: Float,
    val minValue: Float,
    val maxValue: Float,
    val integer: Boolean = false,
    val description: String = ""
) {
    init {
        require(key.matches(Regex("[a-z][a-z0-9_]*"))) { "clé de paramètre invalide : $key" }
        require(minValue.isFinite() && maxValue.isFinite() && minValue <= maxValue)
        require(defaultValue in minValue..maxValue) { "valeur par défaut hors limites : $key" }
        if (integer) require(defaultValue % 1f == 0f) { "la valeur par défaut de $key doit être entière" }
    }

    fun accepts(value: Float): Boolean = value.isFinite() && value in minValue..maxValue &&
        (!integer || value % 1f == 0f)
}

enum class PortKind {
    STRUCTURAL,
    ROTARY_SHAFT,
    GEAR_TEETH,
    LINEAR_MECHANICAL,
    FLEXIBLE_MECHANICAL,
    PNEUMATIC_GAS,
    HYDRAULIC_LIQUID,
    ELECTRICAL_POWER,
    CONTROL_SIGNAL,
    PROJECTILE_PATH,
    FLUID_JET,
    EXHAUST
}

enum class PortDirection { INPUT, OUTPUT, BIDIRECTIONAL }

/** Règle pseudo-3D d'un port dans le monde physique 2D. */
enum class PortLayerRule {
    /** Les couches principales doivent être identiques. */
    SAME_PRIMARY_LAYER,
    /** Les épaisseurs de couche doivent avoir au moins une couche commune. */
    OVERLAPPING_LAYERS,
    /** Autorise aussi la couche immédiatement voisine. */
    ADJACENT_OR_OVERLAPPING,
    /** Flexible ou abstrait : la distance en profondeur n'est pas bloquante. */
    ANY_LAYER
}

data class MachinePortDefinition(
    val id: String,
    val kind: PortKind,
    val direction: PortDirection = PortDirection.BIDIRECTIONAL,
    val layerRule: PortLayerRule = PortLayerRule.OVERLAPPING_LAYERS,
    val capacityParameter: String? = null,
    val description: String = ""
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) { "identifiant de port invalide : $id" }
    }
}

data class MachinePartDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: PartCategory,
    val support: SimulationSupport,
    val parameters: List<PartParameterDefinition> = emptyList(),
    val ports: List<MachinePortDefinition> = emptyList(),
    val tags: Set<String> = emptySet(),
    /** Nombre de couches physiques occupées par défaut. */
    val defaultLayerDepth: Int = 1,
    val collidable: Boolean = true
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_.-]*"))) { "identifiant de pièce invalide : $id" }
        require(name.isNotBlank() && description.isNotBlank())
        require(defaultLayerDepth >= 1)
        require(parameters.map { it.key }.toSet().size == parameters.size) { "paramètres dupliqués dans $id" }
        require(ports.map { it.id }.toSet().size == ports.size) { "ports dupliqués dans $id" }
        val keys = parameters.map { it.key }.toSet()
        for (port in ports) require(port.capacityParameter == null || port.capacityParameter in keys) {
            "le port ${port.id} référence un paramètre absent dans $id"
        }
    }
}

/** Valeurs validées d'une pièce, indépendantes de toute vue Android. */
class PartConfiguration(
    val definition: MachinePartDefinition,
    values: Map<String, Float> = emptyMap()
) {
    private val resolved: Map<String, Float>

    init {
        val known = definition.parameters.associateBy { it.key }
        require(values.keys.all { it in known }) { "paramètre inconnu pour ${definition.id}" }
        resolved = buildMap {
            for (parameter in definition.parameters) {
                val value = values[parameter.key] ?: parameter.defaultValue
                require(parameter.accepts(value)) { "valeur invalide pour ${definition.id}.${parameter.key}: $value" }
                put(parameter.key, value)
            }
        }
    }

    operator fun get(key: String): Float =
        resolved[key] ?: error("paramètre absent : ${definition.id}.$key")

    fun values(): Map<String, Float> = resolved.toMap()
}

