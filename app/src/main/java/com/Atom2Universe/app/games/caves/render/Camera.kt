package com.Atom2Universe.app.games.caves.render

import android.opengl.Matrix
import com.Atom2Universe.app.games.caves.world.CHUNK_SIZE
import kotlin.math.*

internal class Camera(var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0) {
    var yaw = 0f
    var pitch = 0f

    val vpMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)

    private val fwdX get() = sin(Math.toRadians(yaw.toDouble())).toFloat()
    private val fwdZ get() = cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val rgtX get() = cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val rgtZ get() = -sin(Math.toRadians(yaw.toDouble())).toFloat()

    val lookX get() = (cos(Math.toRadians(pitch.toDouble())) * sin(Math.toRadians(yaw.toDouble()))).toFloat()
    val lookY get() = (-sin(Math.toRadians(pitch.toDouble()))).toFloat()
    val lookZ get() = (cos(Math.toRadians(pitch.toDouble())) * cos(Math.toRadians(yaw.toDouble()))).toFloat()

    fun setProjection(fovDeg: Float, aspect: Float) {
        Matrix.perspectiveM(projMatrix, 0, fovDeg, aspect, 0.1f, 90f)
    }

    fun update() {
        pitch = pitch.coerceIn(-89f, 89f)
        // Floating Origin: camera is always at (0,0,0) in render space.
        // Chunk vertices are in local space (0–CHUNK_SIZE); the offset to camera-relative
        // world space is applied per draw call via u_chunk_offset in the vertex shader.
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 0f,
            lookX, lookY, lookZ,
            0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
    }

    fun moveHorizontal(forward: Float, right: Float) {
        x += (fwdX * forward + rgtX * right).toDouble()
        z += (fwdZ * forward + rgtZ * right).toDouble()
    }

    fun moveVertical(up: Float) { y += up.toDouble() }

    fun chunkX() = Math.floorDiv(Math.floor(x).toInt(), CHUNK_SIZE)
    fun chunkY() = Math.floorDiv(Math.floor(y).toInt(), CHUNK_SIZE)
    fun chunkZ() = Math.floorDiv(Math.floor(z).toInt(), CHUNK_SIZE)

    fun posString() = "%.1f  %.1f  %.1f".format(x, y, z)
}
