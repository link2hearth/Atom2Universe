package com.Atom2Universe.app.games.toyboxracers.editor

import com.Atom2Universe.app.games.toyboxracers.track.HouseGeometry
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

internal enum class ToyboxVolumeKind(val label: String, val color: Int, val solidByDefault: Boolean) {
    FLOOR("Sol", 0xFFD7E6E4.toInt(), true),
    WALL("Mur", 0xFFD2DDED.toInt(), true),
    DOOR("Porte", 0xFFA8D1B7.toInt(), false),
    WINDOW("Fenetre", 0xFF9FD2E3.toInt(), false),
    RAIL("Rambarde", 0xFFF7E7C8.toInt(), true),
    RAMP("Rampe", 0xFFC08A55.toInt(), true),
    STAIR("Marche", 0xFFF3E9D7.toInt(), true),
    DUCT("Conduit", 0xFF8DA7B3.toInt(), true),
    FURNITURE("Meuble", 0xFFDDB4C3.toInt(), true),
    DECOR("Decor", 0xFFE8C36E.toInt(), false)
}

internal data class ToyboxVolume(
    val id: Long,
    val kind: ToyboxVolumeKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    val solid: Boolean = kind.solidByDefault,
    val color: Int = kind.color,
    val quarterTurns: Int = 0,
    val yawDegrees: Float = quarterTurns * 90f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f
) {
    val normalizedYaw get() = ((yawDegrees % 360f) + 360f) % 360f
    val rotation get() = (((normalizedYaw / 90f).toInt() % 4) + 4) % 4
    val yawRadians get() = normalizedYaw * kotlin.math.PI.toFloat() / 180f
    val pitchRadians get() = pitchDegrees * kotlin.math.PI.toFloat() / 180f
    val rollRadians get() = rollDegrees * kotlin.math.PI.toFloat() / 180f
    val yawCos get() = cos(yawRadians)
    val yawSin get() = sin(yawRadians)
    val pitchCos get() = cos(pitchRadians)
    val pitchSin get() = sin(pitchRadians)
    val rollCos get() = cos(rollRadians)
    val rollSin get() = sin(rollRadians)
    private val corners get() = buildList {
        for (lx in floatArrayOf(-width * 0.5f, width * 0.5f)) {
            for (ly in floatArrayOf(-height * 0.5f, height * 0.5f)) {
                for (lz in floatArrayOf(-depth * 0.5f, depth * 0.5f)) add(worldPoint(lx, ly, lz))
            }
        }
    }
    val worldWidth get() = right - left
    val worldDepth get() = front - back
    val left get() = corners.minOf { it.x }
    val right get() = corners.maxOf { it.x }
    val back get() = corners.minOf { it.z }
    val front get() = corners.maxOf { it.z }

    fun moveTo(nx: Float, nz: Float) = copy(x = nx, z = nz)
    fun resize(dw: Float, dd: Float) = copy(
        width = (width + dw).coerceAtLeast(1f),
        depth = (depth + dd).coerceAtLeast(1f)
    )
    fun lift(dy: Float) = copy(y = (y + dy).coerceAtLeast(0f))
    fun taller(dh: Float) = copy(height = (height + dh).coerceAtLeast(0.25f))
    fun toggleSolid() = copy(solid = !solid)
    fun rotateQuarter(delta: Int = 1) = rotateYaw(delta * 90f)
    fun rotateYaw(deltaDegrees: Float) = withYaw(yawDegrees + deltaDegrees)
    fun rotatePitch(deltaDegrees: Float) = copy(pitchDegrees = pitchDegrees + deltaDegrees)
    fun rotateRoll(deltaDegrees: Float) = copy(rollDegrees = rollDegrees + deltaDegrees)
    fun withYaw(degrees: Float): ToyboxVolume {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val snappedQuarter = ((normalized / 90f).toInt() % 4 + 4) % 4
        return copy(yawDegrees = normalized, quarterTurns = snappedQuarter)
    }

    fun localX(worldX: Float, worldZ: Float): Float {
        val dx = worldX - x
        val dz = worldZ - z
        return dx * yawCos - dz * yawSin
    }

    fun localZ(worldX: Float, worldZ: Float): Float {
        val dx = worldX - x
        val dz = worldZ - z
        return dx * yawSin + dz * yawCos
    }

    fun worldX(localX: Float, localZ: Float): Float = x + localX * yawCos + localZ * yawSin
    fun worldZ(localX: Float, localZ: Float): Float = z - localX * yawSin + localZ * yawCos

    fun worldPoint(localX: Float, localY: Float, localZ: Float): VolumePoint {
        val pitchedY = localY + localZ * pitchSin
        val pitchedZ = localZ * pitchCos - localY * pitchSin
        val rolledX = localX * rollCos - pitchedY * rollSin
        val rolledY = localX * rollSin + pitchedY * rollCos
        return VolumePoint(
            x = x + rolledX * yawCos + pitchedZ * yawSin,
            y = y + rolledY,
            z = z - rolledX * yawSin + pitchedZ * yawCos
        )
    }

    fun localPoint(worldX: Float, worldY: Float, worldZ: Float): VolumePoint {
        val dx = worldX - x
        val dy = worldY - y
        val dz = worldZ - z
        val yawedX = dx * yawCos - dz * yawSin
        val yawedZ = dx * yawSin + dz * yawCos
        val unrolledX = yawedX * rollCos + dy * rollSin
        val unrolledY = -yawedX * rollSin + dy * rollCos
        val unpitchedY = unrolledY * pitchCos - yawedZ * pitchSin
        val unpitchedZ = unrolledY * pitchSin + yawedZ * pitchCos
        return VolumePoint(unrolledX, unpitchedY, unpitchedZ)
    }

    fun containsXZ(worldX: Float, worldZ: Float): Boolean =
        worldX in left..right && worldZ in back..front

    fun toJson() = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("width", width.toDouble())
        .put("height", height.toDouble())
        .put("depth", depth.toDouble())
        .put("solid", solid)
        .put("color", color)
        .put("quarterTurns", quarterTurns)
        .put("yawDegrees", yawDegrees.toDouble())
        .put("pitchDegrees", pitchDegrees.toDouble())
        .put("rollDegrees", rollDegrees.toDouble())

    companion object {
        fun fromJson(json: JSONObject): ToyboxVolume {
            val kind = ToyboxVolumeKind.entries.find { it.name == json.optString("kind") }
                ?: ToyboxVolumeKind.FURNITURE
            val quarterTurns = json.optInt("quarterTurns", 0)
            return ToyboxVolume(
                id = json.optLong("id", System.nanoTime()),
                kind = kind,
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                z = json.optDouble("z", 0.0).toFloat(),
                width = json.optDouble("width", 12.0).toFloat().coerceAtLeast(1f),
                height = json.optDouble("height", 4.0).toFloat().coerceAtLeast(0.25f),
                depth = json.optDouble("depth", 12.0).toFloat().coerceAtLeast(1f),
                solid = json.optBoolean("solid", true),
                color = json.optInt("color", kind.color),
                quarterTurns = quarterTurns,
                yawDegrees = json.optDouble("yawDegrees", quarterTurns * 90.0).toFloat(),
                pitchDegrees = json.optDouble("pitchDegrees", 0.0).toFloat(),
                rollDegrees = json.optDouble("rollDegrees", 0.0).toFloat()
            )
        }
    }
}

internal data class VolumePoint(val x: Float, val y: Float, val z: Float)

internal enum class ToyboxRotationAxis(val label: String) {
    YAW("Plan"),
    PITCH("Incl. av/ar"),
    ROLL("Incl. g/d");

    fun next() = when (this) {
        YAW -> PITCH
        PITCH -> ROLL
        ROLL -> YAW
    }
}

internal data class ToyboxDecor(
    val id: Long,
    val modelId: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val quarterTurns: Int = 0,
    val scale: Float = 1f,
    val yawDegrees: Float = quarterTurns * 90f
) {
    fun placement(): DecorPlacement? = runCatching {
        DecorPlacement(DecorCatalog[modelId], x, y, z, quarterTurns, scale, yawDegrees)
    }.getOrNull()

    fun moveTo(nx: Float, nz: Float) = copy(x = nx, z = nz)
    fun lift(dy: Float) = copy(y = (y + dy).coerceAtLeast(0f))
    fun resize(ds: Float) = copy(scale = (scale + ds).coerceAtLeast(0.05f))
    fun rotateQuarter(delta: Int = 1) = rotateYaw(delta * 90f)
    fun rotateYaw(deltaDegrees: Float) = withYaw(yawDegrees + deltaDegrees)
    fun withYaw(degrees: Float): ToyboxDecor {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val snappedQuarter = ((normalized / 90f).toInt() % 4 + 4) % 4
        return copy(yawDegrees = normalized, quarterTurns = snappedQuarter)
    }

    fun toJson() = JSONObject()
        .put("id", id)
        .put("modelId", modelId)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("quarterTurns", quarterTurns)
        .put("scale", scale.toDouble())
        .put("yawDegrees", yawDegrees.toDouble())

    companion object {
        fun fromJson(json: JSONObject): ToyboxDecor {
            val quarterTurns = json.optInt("quarterTurns", 0)
            return ToyboxDecor(
                id = json.optLong("id", System.nanoTime()),
                modelId = json.optString("modelId"),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                z = json.optDouble("z", 0.0).toFloat(),
                quarterTurns = quarterTurns,
                scale = json.optDouble("scale", 1.0).toFloat().coerceAtLeast(0.05f),
                yawDegrees = json.optDouble("yawDegrees", quarterTurns * 90.0).toFloat()
            )
        }
    }
}

internal data class ToyboxCheckpoint(
    val id: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float = 8f
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("radius", radius.toDouble())

    companion object {
        fun fromJson(json: JSONObject) = ToyboxCheckpoint(
            id = json.optLong("id", System.nanoTime()),
            x = json.optDouble("x", 0.0).toFloat(),
            y = json.optDouble("y", 0.0).toFloat(),
            z = json.optDouble("z", 0.0).toFloat(),
            radius = json.optDouble("radius", 8.0).toFloat().coerceAtLeast(1f)
        )
    }
}

internal data class ToyboxWorld(
    val version: Int = VERSION,
    val name: String = "Maison tablette",
    val volumes: List<ToyboxVolume> = starterVolumes(),
    val checkpoints: List<ToyboxCheckpoint> = emptyList(),
    val decorations: List<ToyboxDecor> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("name", name)
        .put("volumes", JSONArray().apply { volumes.forEach { put(it.toJson()) } })
        .put("checkpoints", JSONArray().apply { checkpoints.forEach { put(it.toJson()) } })
        .put("decorations", JSONArray().apply { decorations.forEach { put(it.toJson()) } })

    companion object {
        const val VERSION = 1

        fun fromJson(json: JSONObject): ToyboxWorld {
            val volumesJson = json.optJSONArray("volumes") ?: JSONArray()
            val checkpointJson = json.optJSONArray("checkpoints") ?: JSONArray()
            val decorationsJson = json.optJSONArray("decorations") ?: JSONArray()
            return ToyboxWorld(
                version = json.optInt("version", VERSION),
                name = json.optString("name", "Maison tablette"),
                volumes = List(volumesJson.length()) { ToyboxVolume.fromJson(volumesJson.getJSONObject(it)) },
                checkpoints = List(checkpointJson.length()) { ToyboxCheckpoint.fromJson(checkpointJson.getJSONObject(it)) },
                decorations = List(decorationsJson.length()) { ToyboxDecor.fromJson(decorationsJson.getJSONObject(it)) }
            )
        }

        fun starterVolumes(): List<ToyboxVolume> = listOf(
            ToyboxVolume(1, ToyboxVolumeKind.FLOOR, 0f, -0.4f, 0f, 180f, 0.8f, 120f),
            ToyboxVolume(2, ToyboxVolumeKind.WALL, 0f, 5f, -61f, 180f, 10f, 2f),
            ToyboxVolume(3, ToyboxVolumeKind.WALL, 0f, 5f, 61f, 180f, 10f, 2f),
            ToyboxVolume(4, ToyboxVolumeKind.WALL, -91f, 5f, 0f, 2f, 10f, 120f),
            ToyboxVolume(5, ToyboxVolumeKind.WALL, 91f, 5f, 0f, 2f, 10f, 120f),
            ToyboxVolume(6, ToyboxVolumeKind.DOOR, 0f, 5f, 61.5f, 18f, 10f, 1f, solid = false),
            ToyboxVolume(7, ToyboxVolumeKind.RAMP, -35f, 2.4f, 20f, 42f, 1f, 14f),
            ToyboxVolume(8, ToyboxVolumeKind.DUCT, 35f, 4f, -20f, 48f, 8f, 16f),
            ToyboxVolume(9, ToyboxVolumeKind.RAIL, 0f, 3f, 0f, 48f, 5f, 1.2f)
        )

        fun builtInWorlds(): List<ToyboxWorld> = listOf(
            ToyboxWorld(
                name = "Maison complete 3 etages",
                volumes = completeHouseVolumes(),
                decorations = completeHouseDecorations()
            ),
            ToyboxWorld(name = "Maison tablette", volumes = starterVolumes())
        )

        private fun completeHouseVolumes(): List<ToyboxVolume> {
            var nextId = 10_000L
            fun RoomBox.toVolume(kind: ToyboxVolumeKind) = ToyboxVolume(
                id = nextId++,
                kind = kind,
                x = x,
                y = y,
                z = z,
                width = width,
                height = height,
                depth = depth,
                solid = kind.solidByDefault,
                color = 0xFF000000.toInt() or (color and 0x00FFFFFF)
            )
            val floors = HouseGeometry.floorBoxes().map { it.toVolume(ToyboxVolumeKind.FLOOR) }
            val walls = HouseGeometry.wallBoxes().map { box ->
                box.toVolume(if (box.height <= 5f) ToyboxVolumeKind.RAIL else ToyboxVolumeKind.WALL)
            }
            val furniture = HouseGeometry.furnitureBoxes().map { box ->
                box.toVolume(classifyHouseBox(box))
            }
            return floors + walls + furniture
        }

        private fun classifyHouseBox(box: RoomBox): ToyboxVolumeKind {
            val color = box.color and 0x00FFFFFF
            val thin = box.width <= 1.2f || box.depth <= 1.2f
            return when {
                color == 0x9FD2E3 -> ToyboxVolumeKind.WINDOW
                color == 0xA8D1B7 && thin && box.height >= 12f -> ToyboxVolumeKind.DOOR
                color == 0x8DA7B3 || color == 0xE6EEF2 -> ToyboxVolumeKind.DUCT
                color == 0xC08A55 || color == 0xD6A46E || color == 0xA7B9C6 -> ToyboxVolumeKind.RAMP
                color == 0xF3E9D7 && box.height <= 1.2f && box.depth <= 4f -> ToyboxVolumeKind.STAIR
                color == 0xF7E7C8 || color == 0xC79A5A || color == 0xB57F4A -> ToyboxVolumeKind.RAIL
                !box.isSolidDecor() -> ToyboxVolumeKind.DECOR
                else -> ToyboxVolumeKind.FURNITURE
            }
        }

        private fun RoomBox.isSolidDecor(): Boolean =
            width >= 1f && height >= 0.5f && depth >= 1f

        private fun completeHouseDecorations(): List<ToyboxDecor> =
            HouseGeometry.furnitureDecorations().mapIndexed { index, placement ->
                ToyboxDecor(
                    id = 20_000L + index,
                    modelId = placement.model.id,
                    x = placement.x,
                    y = placement.y,
                    z = placement.z,
                    quarterTurns = placement.quarterTurns,
                    scale = placement.scale,
                    yawDegrees = placement.yawDegrees
                )
            }
    }
}
