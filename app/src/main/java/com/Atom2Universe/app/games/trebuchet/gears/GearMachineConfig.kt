package com.Atom2Universe.app.games.trebuchet.gears

import com.Atom2Universe.app.games.trebuchet.MachinePreset
import com.Atom2Universe.app.games.physics.MachineMaterial
import com.Atom2Universe.app.games.physics.MachineMaterials

enum class GearWheelKind { GEAR, FLYWHEEL }

enum class GearLinkKind { BELT_OPEN, BELT_CROSSED, CHAIN_FREEWHEEL, SHAFT_CLUTCH }

data class GearLinkConfig(
    val firstId: Int,
    val secondId: Int,
    val kind: GearLinkKind,
    /** Sens de pédalage autorisé sur [firstId] : +1 antihoraire, -1 horaire. */
    val inputDirection: Int = 1
) {
    fun copyLink() = copy(inputDirection = if (inputDirection < 0) -1 else 1)
}

enum class GearWheelMaterial(val physics: MachineMaterial, val axleFriction: Float) {
    WOOD(MachineMaterials.WOOD, 0.020f),
    ALUMINUM(MachineMaterials.ALUMINUM, 0.012f),
    STEEL(MachineMaterials.STEEL, 0.016f),
    TITANIUM(MachineMaterials.TITANIUM, 0.010f);

    fun next(): GearWheelMaterial = entries[(ordinal + 1) % entries.size]
}

data class GearWheelConfig(
    val id: Int,
    var x: Float,
    var y: Float,
    var teeth: Int,
    var layer: Int = 0,
    var kind: GearWheelKind = GearWheelKind.GEAR,
    var material: GearWheelMaterial = GearWheelMaterial.STEEL,
    /** Phase visuelle de la denture, en radians. */
    var angle: Float = 0f
) {
    val pitchRadius: Float get() = teeth * GearMachineRules.MODULE / 2f
    val outerRadius: Float get() = pitchRadius + GearMachineRules.MODULE

    fun copyWheel() = GearWheelConfig(id, x, y, teeth, layer, kind, material, angle)
}

object GearMachineRules {
    const val MODULE = 0.12f
    const val MIN_TEETH = 8
    const val MAX_TEETH = 200
    const val MAX_GEARS = 64
    const val MAX_LINKS = 96
    const val MIN_COORD = -10_000f
    const val MAX_COORD = 10_000f
    const val MIN_LAYER = -32
    const val MAX_LAYER = 32
    /** Portée courte de l'aimant : assez pour corriger un lâcher, pas pour attirer de loin. */
    const val MAGNET_ENGAGE_MODULES = 1.05f
    /** Hystérésis : une roue déjà prise se décolle dès un mouvement volontaire. */
    const val MAGNET_RELEASE_MODULES = 2.2f
    const val MAX_MANUAL_SPEED = 120f
    const val FLICK_TRANSFER = 0.55f
    const val FLYWHEEL_TEETH = 48
    const val LAUNCH_EFFICIENCY = 0.72f
    const val DEFAULT_PROJECTILE_MASS = 4f
    const val MIN_PROJECTILE_MASS = 0.1f
    const val MAX_PROJECTILE_MASS = 2_000f
    val SIZES = intArrayOf(12, 24, 48, 96)
}

/** Configuration sauvegardable de l'atelier d'engrenages. */
class GearMachineConfig(
    val wheels: MutableList<GearWheelConfig> = defaultWheels().toMutableList(),
    var launcherWheelId: Int? = null,
    var launcherAngleDeg: Float = 35f,
    var projectileMass: Float = GearMachineRules.DEFAULT_PROJECTILE_MASS,
    val links: MutableList<GearLinkConfig> = mutableListOf()
) {
    var nextId: Int = (wheels.maxOfOrNull { it.id } ?: 0) + 1

    fun deepCopy(): GearMachineConfig = GearMachineConfig(
        wheels.map { it.copyWheel() }.toMutableList(), launcherWheelId,
        launcherAngleDeg, projectileMass, links.map { it.copyLink() }.toMutableList()
    ).also {
        it.nextId = nextId
    }

    fun clamp() {
        val seen = HashSet<Int>()
        val iterator = wheels.iterator()
        while (iterator.hasNext()) {
            val wheel = iterator.next()
            if (!wheel.x.isFinite() || !wheel.y.isFinite() || wheel.id < 0 || !seen.add(wheel.id)) {
                iterator.remove()
                continue
            }
            wheel.x = wheel.x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            wheel.y = wheel.y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            wheel.teeth = wheel.teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH)
            wheel.layer = wheel.layer.coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
            wheel.angle = wheel.angle.takeIf { it.isFinite() } ?: 0f
        }
        while (wheels.size > GearMachineRules.MAX_GEARS) wheels.removeLast()
        val ids = wheels.mapTo(HashSet()) { it.id }
        val wheelById = wheels.associateBy { it.id }
        val linkPairs = HashSet<String>()
        links.removeAll { link ->
            val key = "${minOf(link.firstId, link.secondId)}:${maxOf(link.firstId, link.secondId)}"
            val firstLayer = wheelById[link.firstId]?.layer
            val secondLayer = wheelById[link.secondId]?.layer
            val invalidLayers = if (link.kind == GearLinkKind.SHAFT_CLUTCH) {
                firstLayer != null && firstLayer == secondLayer
            } else {
                firstLayer != null && secondLayer != null && firstLayer != secondLayer
            }
            link.firstId == link.secondId || link.firstId !in ids || link.secondId !in ids ||
                invalidLayers || !linkPairs.add(key)
        }
        while (links.size > GearMachineRules.MAX_LINKS) links.removeLast()
        if (launcherWheelId != null && wheels.none { it.id == launcherWheelId }) launcherWheelId = null
        launcherAngleDeg = launcherAngleDeg.takeIf { it.isFinite() }?.coerceIn(0f, 85f) ?: 35f
        projectileMass = projectileMass.takeIf { it.isFinite() }
            ?.coerceIn(GearMachineRules.MIN_PROJECTILE_MASS, GearMachineRules.MAX_PROJECTILE_MASS)
            ?: GearMachineRules.DEFAULT_PROJECTILE_MASS
        nextId = maxOf(nextId, (wheels.maxOfOrNull { it.id } ?: 0) + 1)
    }

    companion object {
        fun defaultWheels(): List<GearWheelConfig> {
            // Une grande roue facile à saisir, puis une cascade immédiatement lisible.
            val large = GearWheelConfig(1, -3f, 3.2f, 48, 0)
            val medium = GearWheelConfig(
                2, large.x + large.pitchRadius + 24 * GearMachineRules.MODULE / 2f,
                3.2f, 24, 0
            )
            val small = GearWheelConfig(
                3, medium.x + medium.pitchRadius + 12 * GearMachineRules.MODULE / 2f,
                3.2f, 12, 0
            )
            return listOf(large, medium, small)
        }
    }
}

class GearMachinePreset(name: String, config: GearMachineConfig) {
    val name: String = MachinePreset.clean(name)
    val config: GearMachineConfig = config.deepCopy()
}

/** Format tolérant et versionné des machines à engrenages. */
object GearMachineLibrary {
    const val MAX_PRESETS = 30
    private const val VERSION = "G4"
    private const val VERSION_G3 = "G3"
    private const val VERSION_G2 = "G2"
    private const val VERSION_G1 = "G1"

    fun encode(list: List<GearMachinePreset>): String = buildString {
        for (preset in list.take(MAX_PRESETS)) {
            append(VERSION).append('\t').append(preset.name)
            append('\t').append("L,").append(preset.config.launcherWheelId ?: -1).append(',')
                .append(preset.config.launcherAngleDeg).append(',').append(preset.config.projectileMass)
            for (link in preset.config.links) {
                append('\t').append("T,").append(link.kind.name).append(',')
                    .append(link.firstId).append(',').append(link.secondId).append(',')
                    .append(if (link.inputDirection < 0) -1 else 1)
            }
            for (wheel in preset.config.wheels) {
                append('\t').append(wheel.id).append(',')
                    .append(wheel.x).append(',').append(wheel.y).append(',')
                    .append(wheel.teeth).append(',').append(wheel.layer).append(',')
                    .append(wheel.kind.name).append(',').append(wheel.material.name).append(',')
                    .append(wheel.angle)
            }
            append('\n')
        }
    }

    fun decode(text: String): List<GearMachinePreset> {
        val out = ArrayList<GearMachinePreset>()
        for (line in text.lineSequence()) {
            if (out.size >= MAX_PRESETS) break
            val fields = line.split('\t')
            if (fields.size < 2 || fields[0] !in setOf(VERSION, VERSION_G3, VERSION_G2, VERSION_G1)) continue
            val name = MachinePreset.clean(fields[1])
            if (name.isEmpty()) continue
            val wheels = ArrayList<GearWheelConfig>()
            var launcherId: Int? = null
            var launcherAngle = 35f
            var projectileMass = GearMachineRules.DEFAULT_PROJECTILE_MASS
            val links = ArrayList<GearLinkConfig>()
            for (field in fields.drop(2)) {
                val value = field.split(',')
                if (value.size == 4 && value[0] == "L") {
                    launcherId = value[1].toIntOrNull()?.takeIf { it >= 0 }
                    launcherAngle = value[2].toFloatOrNull() ?: launcherAngle
                    projectileMass = value[3].toFloatOrNull() ?: projectileMass
                    continue
                }
                if (value.size == 5 && value[0] == "T") {
                    val kind = runCatching { GearLinkKind.valueOf(value[1]) }.getOrNull() ?: continue
                    val first = value[2].toIntOrNull() ?: continue
                    val second = value[3].toIntOrNull() ?: continue
                    val direction = value[4].toIntOrNull() ?: 1
                    links += GearLinkConfig(first, second, kind, direction)
                    continue
                }
                if (value.size != 5 && value.size != 7 && value.size != 8) continue
                val id = value[0].toIntOrNull() ?: continue
                val x = value[1].toFloatOrNull() ?: continue
                val y = value[2].toFloatOrNull() ?: continue
                val teeth = value[3].toIntOrNull() ?: continue
                val layer = value[4].toIntOrNull() ?: continue
                val kind = value.getOrNull(5)?.let { runCatching { GearWheelKind.valueOf(it) }.getOrNull() }
                    ?: GearWheelKind.GEAR
                val material = value.getOrNull(6)?.let { runCatching { GearWheelMaterial.valueOf(it) }.getOrNull() }
                    ?: GearWheelMaterial.STEEL
                val angle = value.getOrNull(7)?.toFloatOrNull() ?: 0f
                wheels += GearWheelConfig(id, x, y, teeth, layer, kind, material, angle)
            }
            val config = GearMachineConfig(wheels, launcherId, launcherAngle, projectileMass, links)
            config.clamp()
            if (config.wheels.isNotEmpty()) out += GearMachinePreset(name, config)
        }
        return out
    }

    fun put(list: List<GearMachinePreset>, preset: GearMachinePreset): List<GearMachinePreset> =
        buildList {
            add(preset)
            for (old in list) if (!old.name.equals(preset.name, ignoreCase = true)) add(old)
        }.take(MAX_PRESETS)

    fun remove(list: List<GearMachinePreset>, name: String): List<GearMachinePreset> =
        list.filterNot { it.name.equals(name, ignoreCase = true) }
}
