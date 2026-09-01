package com.Atom2Universe.app.games.trebuchet.gears

import com.Atom2Universe.app.games.trebuchet.MachinePreset
import com.Atom2Universe.app.games.physics.MachineMaterial
import com.Atom2Universe.app.games.physics.MachineMaterials
import kotlin.math.hypot

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
    var angle: Float = 0f,
    /**
     * L'elevation du tir de ce volant, en degres.
     *
     * Un volant **porte toujours son bras de lancement** : c'est ce qui en fait autre
     * chose qu'une masse qui tourne. Le boulet part de la jante, tangentiellement,
     * et cet angle-la est la direction qu'il prend -- pas la position du bras, qui
     * s'en deduit et depend du sens de rotation.
     */
    var launchAngle: Float = 35f
) {
    val pitchRadius: Float get() = teeth * GearMachineRules.MODULE / 2f
    val outerRadius: Float get() = pitchRadius + GearMachineRules.MODULE

    /**
     * La gorge de lancement : le chemin creuse dans la jante ou le boulet est loge.
     *
     * Le boulet n'est pas au bout d'une perche, il est **dans** le mecanisme. La gorge
     * le retient contre la force centrifuge tout le temps que la roue prend de la
     * vitesse, et le lache par son encoche quand elle passe au point de largage.
     */
    val grooveWidth: Float get() = maxOf(0.30f, outerRadius * 0.16f)

    /** Le bord exterieur de la gorge, juste sous la denture. */
    val grooveOuterRadius: Float get() = outerRadius - GearMachineRules.MODULE * 0.5f

    /** Le rayon ou court le boulet : le milieu de la gorge. */
    val launchRadius: Float
        get() = (grooveOuterRadius - grooveWidth / 2f).coerceAtLeast(0.15f)

    fun copyWheel() =
        GearWheelConfig(id, x, y, teeth, layer, kind, material, angle, launchAngle)
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

    /** En dessous, la jante ne va pas assez vite pour lancer quoi que ce soit. */
    const val MIN_LAUNCH_OMEGA = 0.25f

    /** Le sens de rotation par defaut d'un volant a l'arret : horaire. */
    const val DEFAULT_SPIN = -1f
    const val LAUNCH_EFFICIENCY = 0.72f
    const val DEFAULT_PROJECTILE_MASS = 4f
    const val MIN_PROJECTILE_MASS = 0.1f
    const val MAX_PROJECTILE_MASS = 2_000f
    val SIZES = intArrayOf(12, 24, 48, 96)

    /** Les boulets proposés, du caillou au bloc de siège. */
    val PROJECTILE_MASSES = floatArrayOf(1f, 4f, 12f, 40f, 120f, 400f, 1_200f)

    /**
     * Le rayon d'un boulet de cette masse, en metres.
     *
     * Il sert au tir **et** au dessin du boulet en attente dans sa gorge : les deux
     * doivent donner exactement la meme bille, sinon celle qu'on voit charger n'est
     * pas celle qui part.
     */
    fun projectileRadius(mass: Float): Float =
        (0.075f * Math.cbrt(mass.toDouble())).toFloat().coerceIn(0.06f, 0.55f)

    /** Le cran de masse suivant, en tournant dans la liste. */
    fun nextProjectileMass(mass: Float, delta: Int): Float {
        val index = PROJECTILE_MASSES.indexOfFirst { kotlin.math.abs(it - mass) < 1e-3f }
        val from = if (index >= 0) index else PROJECTILE_MASSES.indexOfFirst { it >= mass }
            .let { if (it < 0) PROJECTILE_MASSES.size - 1 else it }
        return PROJECTILE_MASSES[(from + delta).coerceIn(0, PROJECTILE_MASSES.size - 1)]
    }

    /**
     * Qui touche quoi. Les roues ne se heurtent jamais — leurs dents sont logiques,
     * et deux roues d'un même train se recouvrent par construction. Le boulet, lui,
     * ne connaît que le sol : le laisser cogner la machine qui vient de le lancer
     * finirait toujours mal.
     */
    const val CATEGORY_WHEEL = 1
    const val CATEGORY_GROUND = 2
    const val CATEGORY_SHOT = 4

    /**
     * La dalle de sol : assez large pour porter le plus long des tirs, et **épaisse**,
     * parce que le moteur taille ses sous-pas sur l'épaisseur de ce qu'un corps rapide
     * peut atteindre. Une dalle mince hacherait chaque image en trente-deux sous-pas
     * dès qu'un boulet file au ras du sol.
     */
    const val GROUND_HALF_WIDTH = 20_000f
    const val GROUND_DEPTH = 30f

    /** Le mannequin planté devant le lanceur : le seul but de l'atelier, pour l'instant. */
    const val TARGET_DISTANCE = 60f
    const val TARGET_HEIGHT = 1.72f
    const val TARGET_RADIUS = 0.38f

    /** L'elevation d'un tir : de l'horizontale au tir en cloche. */
    const val MIN_LAUNCH_DEG = 0f
    const val MAX_LAUNCH_DEG = 85f

    /**
     * Le trajet d'une image passe-t-il dans le mannequin planté en [targetX] ?
     *
     * On mesure la distance du **segment** parcouru au centre de la cible, et non
     * celle du point d'arrivée : à trois cents mètres par seconde, un boulet saute
     * plus de deux mètres par sous-pas et traverserait une cible de quarante
     * centimètres sans jamais être relevé dedans.
     */
    fun segmentHitsTarget(
        targetX: Float, ballRadius: Float,
        x0: Float, y0: Float, x1: Float, y1: Float
    ): Boolean {
        val cy = TARGET_HEIGHT
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq <= 1e-12f) {
            0f
        } else {
            (((targetX - x0) * dx + (cy - y0) * dy) / lengthSq).coerceIn(0f, 1f)
        }
        val nearestX = x0 + dx * t
        val nearestY = y0 + dy * t
        return hypot(targetX - nearestX, cy - nearestY) <= TARGET_RADIUS + ballRadius
    }
}

/** Configuration sauvegardable de l'atelier d'engrenages. */
class GearMachineConfig(
    val wheels: MutableList<GearWheelConfig> = defaultWheels().toMutableList(),
    var launcherWheelId: Int? = null,
    var projectileMass: Float = GearMachineRules.DEFAULT_PROJECTILE_MASS,
    val links: MutableList<GearLinkConfig> = mutableListOf()
) {
    var nextId: Int = (wheels.maxOfOrNull { it.id } ?: 0) + 1

    fun deepCopy(): GearMachineConfig = GearMachineConfig(
        wheels.map { it.copyWheel() }.toMutableList(), launcherWheelId,
        projectileMass, links.map { it.copyLink() }.toMutableList()
    ).also {
        it.nextId = nextId
    }

    /** Le volant qui tire, s'il y en a un. */
    fun launcher(): GearWheelConfig? = wheels.firstOrNull { it.id == launcherWheelId }

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
            wheel.launchAngle = wheel.launchAngle.takeIf { it.isFinite() }
                ?.coerceIn(GearMachineRules.MIN_LAUNCH_DEG, GearMachineRules.MAX_LAUNCH_DEG) ?: 35f
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
        // Seul un volant peut tirer, et le premier venu s'en charge : le bras de
        // lancement fait partie du volant, il n'a pas a etre monte a part.
        if (wheels.none { it.id == launcherWheelId && it.kind == GearWheelKind.FLYWHEEL }) {
            launcherWheelId = wheels.firstOrNull { it.kind == GearWheelKind.FLYWHEEL }?.id
        }
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
    private const val VERSION = "G5"
    private const val VERSION_G4 = "G4"
    private const val VERSION_G3 = "G3"
    private const val VERSION_G2 = "G2"
    private const val VERSION_G1 = "G1"

    fun encode(list: List<GearMachinePreset>): String = buildString {
        for (preset in list.take(MAX_PRESETS)) {
            append(VERSION).append('\t').append(preset.name)
            // L'angle garde sa place dans l'entete pour que les versions anterieures
            // relisent quelque chose de sense : c'est celui du volant qui tire.
            append('\t').append("L,").append(preset.config.launcherWheelId ?: -1).append(',')
                .append(preset.config.launcher()?.launchAngle ?: 35f).append(',')
                .append(preset.config.projectileMass)
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
                    .append(wheel.angle).append(',').append(wheel.launchAngle)
            }
            append('\n')
        }
    }

    fun decode(text: String): List<GearMachinePreset> {
        val out = ArrayList<GearMachinePreset>()
        for (line in text.lineSequence()) {
            if (out.size >= MAX_PRESETS) break
            val fields = line.split('\t')
            val version = fields.getOrNull(0) ?: continue
            if (fields.size < 2 ||
                version !in setOf(VERSION, VERSION_G4, VERSION_G3, VERSION_G2, VERSION_G1)) continue
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
                if (value.size !in 5..9 || value.size == 6) continue
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
                // Avant G5, l'angle de tir etait unique et vivait dans l'entete : il
                // devient celui de chaque volant relu.
                val launch = value.getOrNull(8)?.toFloatOrNull() ?: launcherAngle
                wheels += GearWheelConfig(id, x, y, teeth, layer, kind, material, angle, launch)
            }
            val config = GearMachineConfig(wheels, launcherId, projectileMass, links)
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
