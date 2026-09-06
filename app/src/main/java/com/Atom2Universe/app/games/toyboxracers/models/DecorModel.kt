package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal enum class DecorRoom { KITCHEN, LIVING_ROOM, GARAGE, OFFICE, BATHROOM, OUTDOOR, BEDROOM }
internal enum class DecorShape { BOX, OVAL, CYLINDER_Y, CYLINDER_X, CONE_Y, GABLE_ROOF }

/** Dimensions complètes, origine au centre de la base, façade tournée vers +Z. */
internal data class DecorPart(
    val shape: DecorShape, val x: Float, val y: Float, val z: Float,
    val width: Float, val height: Float, val depth: Float,
    val color: Int, val solid: Boolean
)

internal data class DecorModel(val id: String, val room: DecorRoom, val parts: List<DecorPart>) {
    val bounds: RoomBox = run {
        require(parts.isNotEmpty())
        val left = parts.minOf { it.x - it.width / 2 }
        val right = parts.maxOf { it.x + it.width / 2 }
        val bottom = parts.minOf { it.y - it.height / 2 }
        val top = parts.maxOf { it.y + it.height / 2 }
        val back = parts.minOf { it.z - it.depth / 2 }
        val front = parts.maxOf { it.z + it.depth / 2 }
        RoomBox((left + right) / 2, (bottom + top) / 2, (back + front) / 2,
            right - left, top - bottom, front - back, 0)
    }
}

internal data class DecorPlacement(
    val model: DecorModel, val x: Float, val y: Float, val z: Float,
    val quarterTurns: Int = 0, val scale: Float = 1f, val yawDegrees: Float = quarterTurns * 90f
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite())
        require(scale.isFinite() && scale > 0f)
    }
    val normalizedYaw = ((yawDegrees % 360f) + 360f) % 360f
    val rotation = (((normalizedYaw / 90f).toInt() % 4) + 4) % 4
    private val yawRadians = normalizedYaw * kotlin.math.PI.toFloat() / 180f
    private val yawCos = cos(yawRadians)
    private val yawSin = sin(yawRadians)
    fun rotatedX(px: Float, pz: Float): Float = px * yawCos + pz * yawSin
    fun rotatedZ(px: Float, pz: Float): Float = -px * yawSin + pz * yawCos

    /** Un volume par pièce solide ; jamais une boîte globale bouchant dessous et passages. */
    val solids: List<RoomBox> by lazy {
        model.parts.filter { it.solid }.map { part ->
            RoomBox(x + rotatedX(part.x, part.z) * scale, y + part.y * scale,
                z + rotatedZ(part.x, part.z) * scale,
                (abs(yawCos) * part.width + abs(yawSin) * part.depth) * scale,
                part.height * scale,
                (abs(yawSin) * part.width + abs(yawCos) * part.depth) * scale, part.color)
        }
    }
}

/** Palette commune à toutes les pièces : bois miel, surfaces crème, accents pastel. */
internal object DecorPalette {
    const val WOOD = 0xDAB68B
    const val CREAM = 0xFFF0D1
    const val MINT = 0x91CDB4
    const val ROSE = 0xECA0AF
    const val BLUE = 0x91BEDD
    const val LILAC = 0xB9AAD8
    const val GOLD = 0xE8C36E
    const val DARK = 0x485065
    const val METAL = 0xB8C9CD
    const val GLASS = 0x688DA3
    const val LEAF = 0x72AD8E
}

internal class DecorBuilder {
    val parts = mutableListOf<DecorPart>()
    fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int, solid: Boolean = true) =
        add(DecorShape.BOX, x, y, z, w, h, d, color, solid)
    fun oval(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int, solid: Boolean = true) =
        add(DecorShape.OVAL, x, y, z, w, h, d, color, solid)
    fun cylinder(x: Float, y: Float, z: Float, radius: Float, h: Float, color: Int, solid: Boolean = true) =
        add(DecorShape.CYLINDER_Y, x, y, z, radius * 2, h, radius * 2, color, solid)
    fun wheel(x: Float, y: Float, z: Float, radius: Float, length: Float, color: Int) =
        add(DecorShape.CYLINDER_X, x, y, z, length, radius * 2, radius * 2, color, true)
    fun cone(x: Float, bottom: Float, z: Float, radius: Float, h: Float, color: Int) =
        add(DecorShape.CONE_Y, x, bottom + h / 2, z, radius * 2, h, radius * 2, color, true)
    fun roof(x: Float, bottom: Float, z: Float, w: Float, h: Float, d: Float, color: Int) =
        add(DecorShape.GABLE_ROOF, x, bottom + h / 2, z, w, h, d, color, true)
    private fun add(shape: DecorShape, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, c: Int, solid: Boolean) {
        require(listOf(x, y, z, w, h, d).all { it.isFinite() } && w > 0 && h > 0 && d > 0)
        parts += DecorPart(shape, x, y, z, w, h, d, c, solid)
    }
    fun legs(w: Float, d: Float, h: Float, thickness: Float = 0.8f, color: Int = DecorPalette.WOOD) {
        for (x in floatArrayOf(-w / 2, w / 2)) for (z in floatArrayOf(-d / 2, d / 2))
            box(x, h / 2, z, thickness, h, thickness, color)
    }
    fun handle(x: Float, y: Float, z: Float, width: Float = 1.6f) {
        box(x, y, z, width, 0.28f, 0.4f, DecorPalette.CREAM, false)
    }
}
