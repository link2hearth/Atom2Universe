package com.Atom2Universe.app.games.caves.render

import android.opengl.Matrix
import com.Atom2Universe.app.games.caves.world.CHUNK_SIZE
import kotlin.math.*

internal class Camera(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    var yaw = 0f    // degrees, horizontal
    var pitch = 0f  // degrees, vertical, clamped to ±89

    val vpMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)

    // Horizontal-only forward (ignores pitch) for WASD-style movement.
    private val fwdX get() = sin(Math.toRadians(yaw.toDouble())).toFloat()
    private val fwdZ get() = cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val rgtX get() = cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val rgtZ get() = -sin(Math.toRadians(yaw.toDouble())).toFloat()

    // Full look direction (including pitch) — exposé pour le raycasting laser.
    val lookX get() = cos(Math.toRadians(pitch.toDouble())).toFloat() * sin(Math.toRadians(yaw.toDouble())).toFloat()
    val lookY get() = -sin(Math.toRadians(pitch.toDouble())).toFloat()
    val lookZ get() = cos(Math.toRadians(pitch.toDouble())).toFloat() * cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val lookDX get() = lookX
    private val lookDY get() = lookY
    private val lookDZ get() = lookZ

    fun setProjection(fovDeg: Float, aspect: Float) {
        Matrix.perspectiveM(projMatrix, 0, fovDeg, aspect, 0.1f, 90f)
    }

    fun update() {
        pitch = pitch.coerceIn(-89f, 89f)
        Matrix.setLookAtM(viewMatrix, 0,
            x, y, z,
            x + lookDX, y + lookDY, z + lookDZ,
            0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
    }

    fun moveHorizontal(forward: Float, right: Float) {
        x += fwdX * forward + rgtX * right
        z += fwdZ * forward + rgtZ * right
    }

    fun moveVertical(up: Float) { y += up }

    fun chunkX() = Math.floorDiv(x.toInt(), CHUNK_SIZE)
    fun chunkY() = Math.floorDiv(y.toInt(), CHUNK_SIZE)
    fun chunkZ() = Math.floorDiv(z.toInt(), CHUNK_SIZE)

    fun posString() = "%.1f  %.1f  %.1f".format(x, y, z)
}
