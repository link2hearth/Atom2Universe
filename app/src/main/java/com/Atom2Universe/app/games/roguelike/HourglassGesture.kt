package com.Atom2Universe.app.games.roguelike

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Tourner le grand sablier autour de son centre ; le sable commence en bas et finit en haut. */
internal class HourglassGesture(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    private val limitMs: Float,
) : TouchGesture {
    override var result: Timing? = null
        private set
    private var pointer = -1
    private var lastAngle: Float? = null
    var rotation = 0f
        private set
    private var flipAt = -1f
    private var flipFrom = 0f
    val flipped get() = flipAt >= 0f

    private fun angle(x: Float, y: Float): Float? =
        if (hypot(x - centerX, y - centerY) < radius * .2f) null
        else Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()

    /** Après le retournement, un court temps laisse voir le sable en haut et les premiers grains tomber. */
    fun displayedRotation(t: Float): Float {
        if (!flipped) return rotation
        val end = if (flipFrom >= 0f) 180f else -180f
        return flipFrom + (end - flipFrom) * ((t - flipAt) / 180f).coerceIn(0f, 1f)
    }
    fun sandTime(t: Float) = if (flipped) (t - flipAt - 180f).coerceAtLeast(0f) else 0f

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || flipped || pointer >= 0) return
        pointer = id
        lastAngle = angle(x, y)
    }
    override fun move(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || flipped || id != pointer) return
        val next = angle(x, y)
        val previous = lastAngle
        if (next != null && previous != null) {
            val delta = ((next - previous + 540f) % 360f) - 180f
            rotation = (rotation + delta).coerceIn(-180f, 180f)
            if (abs(rotation) >= 150f) {
                flipFrom = rotation
                flipAt = t
            }
        }
        lastAngle = next
    }
    override fun up(id: Int, x: Float, y: Float, t: Float) {
        if (id != pointer) return
        move(id, x, y, t)
        pointer = -1
        lastAngle = null
    }
    override fun update(t: Float) {
        if (result != null) return
        if (flipped) {
            if (t >= flipAt + 750f) result = Timing.PERFECT
        } else if (t >= limitMs) result = Timing.MISS
    }
}
