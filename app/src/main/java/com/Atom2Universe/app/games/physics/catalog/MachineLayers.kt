package com.Atom2Universe.app.games.physics.catalog

import kotlin.math.abs
import kotlin.math.hypot

data class LayerRange(val first: Int, val last: Int) {
    init { require(first <= last) }
    fun overlaps(other: LayerRange): Boolean = first <= other.last && other.first <= last
    fun gapTo(other: LayerRange): Int = when {
        overlaps(other) -> 0
        last < other.first -> other.first - last
        else -> first - other.last
    }
}

data class CatalogPlacement(
    val instanceId: String,
    val configuration: PartConfiguration,
    val x: Float,
    val y: Float,
    val angle: Float = 0f,
    val primaryLayer: Int = 0,
    val layerDepth: Int = configuration.definition.defaultLayerDepth
) {
    init {
        require(instanceId.isNotBlank())
        require(x.isFinite() && y.isFinite() && angle.isFinite())
        require(layerDepth >= 1)
    }

    val layers: LayerRange get() = LayerRange(primaryLayer, primaryLayer + layerDepth - 1)
}

data class PortEndpoint(val placement: CatalogPlacement, val portId: String) {
    val port: MachinePortDefinition
        get() = placement.configuration.definition.ports.firstOrNull { it.id == portId }
            ?: error("port absent : ${placement.configuration.definition.id}.$portId")
}

data class ConnectionVerdict(val compatible: Boolean, val reason: String)

/** Règles de profondeur communes à la future interface et au moteur. */
object MachineLayerSystem {
    fun canCollide(a: CatalogPlacement, b: CatalogPlacement): Boolean =
        a.configuration.definition.collidable && b.configuration.definition.collidable &&
            a.layers.overlaps(b.layers)

    fun canConnect(a: PortEndpoint, b: PortEndpoint): ConnectionVerdict {
        if (a.placement.instanceId == b.placement.instanceId && a.portId == b.portId) {
            return ConnectionVerdict(false, "un port ne peut pas se connecter à lui-même")
        }
        if (a.port.kind != b.port.kind) {
            return ConnectionVerdict(false, "médias incompatibles : ${a.port.kind} / ${b.port.kind}")
        }
        if (a.port.direction == PortDirection.INPUT && b.port.direction == PortDirection.INPUT) {
            return ConnectionVerdict(false, "deux entrées ne peuvent pas être reliées")
        }
        if (a.port.direction == PortDirection.OUTPUT && b.port.direction == PortDirection.OUTPUT) {
            return ConnectionVerdict(false, "deux sorties ne peuvent pas être reliées")
        }
        if (!acceptsLayer(a, b) || !acceptsLayer(b, a)) {
            return ConnectionVerdict(false, "couches incompatibles")
        }
        return ConnectionVerdict(true, "connexion compatible")
    }

    private fun acceptsLayer(own: PortEndpoint, other: PortEndpoint): Boolean = when (own.port.layerRule) {
        PortLayerRule.SAME_PRIMARY_LAYER ->
            own.placement.primaryLayer == other.placement.primaryLayer
        PortLayerRule.OVERLAPPING_LAYERS -> own.placement.layers.overlaps(other.placement.layers)
        PortLayerRule.ADJACENT_OR_OVERLAPPING -> own.placement.layers.gapTo(other.placement.layers) <= 1
        PortLayerRule.ANY_LAYER -> true
    }
}

enum class GearMeshType { EXTERNAL, INTERNAL }

data class CatalogGear(
    val placement: CatalogPlacement,
    val pitchRadius: Float,
    val teeth: Int,
    val pressureAngleDeg: Float = 20f
) {
    init {
        require(pitchRadius > 0f && teeth >= 3)
        require(pressureAngleDeg > 0f && pressureAngleDeg < 90f)
    }
    val module: Float get() = 2f * pitchRadius / teeth
}

data class GearMeshVerdict(
    val compatible: Boolean,
    val reason: String,
    val ratio: Float = 0f,
    val expectedCenterDistance: Float = 0f
)

object GearLayerMeshing {
    fun evaluate(
        a: CatalogGear,
        b: CatalogGear,
        type: GearMeshType = GearMeshType.EXTERNAL,
        moduleTolerance: Float = 0.01f,
        positionToleranceInModules: Float = 0.35f
    ): GearMeshVerdict {
        if (!a.placement.layers.overlaps(b.placement.layers)) {
            return GearMeshVerdict(false, "les dentures ne partagent aucune couche")
        }
        val moduleError = abs(a.module - b.module) / maxOf(a.module, b.module)
        if (moduleError > moduleTolerance) {
            return GearMeshVerdict(false, "modules incompatibles")
        }
        if (abs(a.pressureAngleDeg - b.pressureAngleDeg) > 0.1f) {
            return GearMeshVerdict(false, "angles de pression incompatibles")
        }
        val expected = when (type) {
            GearMeshType.EXTERNAL -> a.pitchRadius + b.pitchRadius
            GearMeshType.INTERNAL -> abs(a.pitchRadius - b.pitchRadius)
        }
        val actual = hypot(b.placement.x - a.placement.x, b.placement.y - a.placement.y)
        if (abs(actual - expected) > maxOf(a.module, b.module) * positionToleranceInModules) {
            return GearMeshVerdict(false, "entraxe incorrect", expectedCenterDistance = expected)
        }
        val signedRatio = a.teeth.toFloat() / b.teeth *
            if (type == GearMeshType.EXTERNAL) 1f else -1f
        return GearMeshVerdict(true, "engrenage compatible", signedRatio, expected)
    }
}

