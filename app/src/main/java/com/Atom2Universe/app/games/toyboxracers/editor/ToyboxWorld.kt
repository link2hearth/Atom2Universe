package com.Atom2Universe.app.games.toyboxracers.editor

import org.json.JSONArray
import org.json.JSONObject

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
    val color: Int = kind.color
) {
    val left get() = x - width * 0.5f
    val right get() = x + width * 0.5f
    val back get() = z - depth * 0.5f
    val front get() = z + depth * 0.5f

    fun moveTo(nx: Float, nz: Float) = copy(x = nx, z = nz)
    fun resize(dw: Float, dd: Float) = copy(
        width = (width + dw).coerceAtLeast(1f),
        depth = (depth + dd).coerceAtLeast(1f)
    )
    fun lift(dy: Float) = copy(y = (y + dy).coerceAtLeast(0f))
    fun taller(dh: Float) = copy(height = (height + dh).coerceAtLeast(0.25f))
    fun toggleSolid() = copy(solid = !solid)

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

    companion object {
        fun fromJson(json: JSONObject): ToyboxVolume {
            val kind = ToyboxVolumeKind.entries.find { it.name == json.optString("kind") }
                ?: ToyboxVolumeKind.FURNITURE
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
                color = json.optInt("color", kind.color)
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
    val checkpoints: List<ToyboxCheckpoint> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("name", name)
        .put("volumes", JSONArray().apply { volumes.forEach { put(it.toJson()) } })
        .put("checkpoints", JSONArray().apply { checkpoints.forEach { put(it.toJson()) } })

    companion object {
        const val VERSION = 1

        fun fromJson(json: JSONObject): ToyboxWorld {
            val volumesJson = json.optJSONArray("volumes") ?: JSONArray()
            val checkpointJson = json.optJSONArray("checkpoints") ?: JSONArray()
            return ToyboxWorld(
                version = json.optInt("version", VERSION),
                name = json.optString("name", "Maison tablette"),
                volumes = List(volumesJson.length()) { ToyboxVolume.fromJson(volumesJson.getJSONObject(it)) },
                checkpoints = List(checkpointJson.length()) { ToyboxCheckpoint.fromJson(checkpointJson.getJSONObject(it)) }
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
    }
}
