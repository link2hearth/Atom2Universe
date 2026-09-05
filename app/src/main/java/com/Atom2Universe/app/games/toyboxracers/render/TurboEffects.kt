package com.Atom2Universe.app.games.toyboxracers.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Ruban et poussière P2 : stockage borné et buffers réutilisés à chaque image. */
internal class TurboEffects {
    private val trailX = FloatArray(TRAIL_POINTS)
    private val trailY = FloatArray(TRAIL_POINTS)
    private val trailZ = FloatArray(TRAIL_POINTS)
    private val trailRightX = FloatArray(TRAIL_POINTS)
    private val trailRightZ = FloatArray(TRAIL_POINTS)
    private val trailAge = FloatArray(TRAIL_POINTS) { TRAIL_LIFETIME }
    private val trailLevel = IntArray(TRAIL_POINTS)
    private val trailBreak = BooleanArray(TRAIL_POINTS)
    private var trailHead = 0
    private var trailCount = 0
    private var trailTimer = 0f
    private var recordingDrift = false

    private val dustX = FloatArray(DUST_PARTICLES)
    private val dustY = FloatArray(DUST_PARTICLES)
    private val dustZ = FloatArray(DUST_PARTICLES)
    private val dustVx = FloatArray(DUST_PARTICLES)
    private val dustVz = FloatArray(DUST_PARTICLES)
    private val dustAge = FloatArray(DUST_PARTICLES) { DUST_LIFETIME }
    private var dustHead = 0
    private var dustTimer = 0f
    private var previousReleaseSerial = 0

    private val vertices = FloatArray(MAX_VERTICES * FLOATS_PER_VERTEX)
    private val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val mvp = FloatArray(16)
    private var vertexCount = 0
    private var vao = 0
    private var vbo = 0
    private val colorA = FloatArray(4)
    private val colorB = FloatArray(4)
    private val dustColor = FloatArray(4)

    fun upload() {
        val handles = IntArray(1)
        GLES30.glGenVertexArrays(1, handles, 0)
        vao = handles[0]
        GLES30.glGenBuffers(1, handles, 0)
        vbo = handles[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, null, GLES30.GL_DYNAMIC_DRAW)
        val stride = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * Float.SIZE_BYTES)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 6 * Float.SIZE_BYTES)
        GLES30.glBindVertexArray(0)
    }

    fun reset(releaseSerial: Int = 0) {
        trailAge.fill(TRAIL_LIFETIME)
        dustAge.fill(DUST_LIFETIME)
        trailHead = 0
        trailCount = 0
        trailTimer = 0f
        recordingDrift = false
        dustHead = 0
        dustTimer = 0f
        previousReleaseSerial = releaseSerial
    }

    fun update(dt: Float, car: ArcadeCar) {
        for (index in trailAge.indices) trailAge[index] += dt
        for (index in dustAge.indices) {
            dustAge[index] += dt
            if (dustAge[index] < DUST_LIFETIME) {
                dustX[index] += dustVx[index] * dt
                dustZ[index] += dustVz[index] * dt
            }
        }

        if (car.drifting) {
            val startsNewRibbon = !recordingDrift
            recordingDrift = true
            trailTimer += dt
            dustTimer += dt
            if (trailTimer >= TRAIL_INTERVAL) {
                trailTimer -= TRAIL_INTERVAL
                addTrailPoint(car, startsNewRibbon)
            }
            if (dustTimer >= DUST_INTERVAL) {
                dustTimer -= DUST_INTERVAL
                addDust(car, burst = false)
            }
        } else {
            recordingDrift = false
            trailTimer = TRAIL_INTERVAL
            dustTimer = DUST_INTERVAL
        }

        if (car.turboReleaseSerial != previousReleaseSerial) {
            previousReleaseSerial = car.turboReleaseSerial
            repeat(8) { addDust(car, burst = true) }
        }
    }

    private fun addTrailPoint(car: ArcadeCar, startsNewRibbon: Boolean) {
        val index = trailHead
        val forwardX = sin(car.yawRadians)
        val forwardZ = cos(car.yawRadians)
        trailX[index] = car.worldPosition.x - forwardX * 0.78f
        trailY[index] = car.worldPosition.y - 0.24f
        trailZ[index] = car.worldPosition.z - forwardZ * 0.78f
        trailRightX[index] = forwardZ
        trailRightZ[index] = -forwardX
        trailAge[index] = 0f
        trailLevel[index] = car.turboLevel.coerceAtLeast(1)
        trailBreak[index] = startsNewRibbon
        trailHead = (trailHead + 1) % TRAIL_POINTS
        trailCount = (trailCount + 1).coerceAtMost(TRAIL_POINTS)
    }

    private fun addDust(car: ArcadeCar, burst: Boolean) {
        val index = dustHead
        val phase = index * 2.39996f
        val radius = if (burst) 0.72f else 0.48f
        dustX[index] = car.worldPosition.x + cos(phase) * radius
        dustY[index] = car.worldPosition.y - 0.27f + 0.035f
        dustZ[index] = car.worldPosition.z + sin(phase) * radius
        val force = if (burst) 2.6f else 0.8f
        dustVx[index] = cos(phase) * force
        dustVz[index] = sin(phase) * force
        dustAge[index] = 0f
        dustHead = (dustHead + 1) % DUST_PARTICLES
    }

    fun draw(shader: ToyboxShader, viewProjection: FloatArray, identity: FloatArray) {
        buildVertices()
        if (vertexCount == 0) return
        vertexBuffer.clear()
        vertexBuffer.put(vertices, 0, vertexCount * FLOATS_PER_VERTEX)
        vertexBuffer.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertexBuffer.remaining() * Float.SIZE_BYTES, vertexBuffer)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, identity, 0)
        GLES30.glUniformMatrix4fv(shader.mvpLocation, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(shader.modelLocation, 1, false, identity, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glBindVertexArray(0)
    }

    private fun buildVertices() {
        vertexCount = 0
        if (trailCount >= 2) {
            val oldest = (trailHead - trailCount + TRAIL_POINTS) % TRAIL_POINTS
            for (offset in 0 until trailCount - 1) {
                val a = (oldest + offset) % TRAIL_POINTS
                val b = (a + 1) % TRAIL_POINTS
                if (trailBreak[b]) continue
                if (trailAge[a] >= TRAIL_LIFETIME || trailAge[b] >= TRAIL_LIFETIME) continue
                val widthA = ribbonWidth(trailLevel[a])
                val widthB = ribbonWidth(trailLevel[b])
                ribbonColor(trailLevel[a], 1f - trailAge[a] / TRAIL_LIFETIME, colorA)
                ribbonColor(trailLevel[b], 1f - trailAge[b] / TRAIL_LIFETIME, colorB)
                val alx = trailX[a] - trailRightX[a] * widthA
                val alz = trailZ[a] - trailRightZ[a] * widthA
                val arx = trailX[a] + trailRightX[a] * widthA
                val arz = trailZ[a] + trailRightZ[a] * widthA
                val blx = trailX[b] - trailRightX[b] * widthB
                val blz = trailZ[b] - trailRightZ[b] * widthB
                val brx = trailX[b] + trailRightX[b] * widthB
                val brz = trailZ[b] + trailRightZ[b] * widthB
                triangle(alx, trailY[a], alz, blx, trailY[b], blz, brx, trailY[b], brz, colorA, colorB, colorB)
                triangle(alx, trailY[a], alz, brx, trailY[b], brz, arx, trailY[a], arz, colorA, colorB, colorA)
            }
        }
        for (index in dustAge.indices) {
            if (dustAge[index] >= DUST_LIFETIME) continue
            val life = 1f - dustAge[index] / DUST_LIFETIME
            val size = 0.10f + (1f - life) * 0.20f
            dustColor[0] = 1f; dustColor[1] = 0.67f; dustColor[2] = 0.78f; dustColor[3] = life * 0.58f
            val y = dustY[index]
            triangle(dustX[index] - size, y, dustZ[index] - size, dustX[index] + size, y, dustZ[index] - size, dustX[index] + size, y, dustZ[index] + size, dustColor, dustColor, dustColor)
            triangle(dustX[index] - size, y, dustZ[index] - size, dustX[index] + size, y, dustZ[index] + size, dustX[index] - size, y, dustZ[index] + size, dustColor, dustColor, dustColor)
        }
    }

    private fun ribbonWidth(level: Int) = when (level) { 3 -> 0.42f; 2 -> 0.34f; else -> 0.26f }

    private fun ribbonColor(level: Int, alpha: Float, target: FloatArray) {
        when (level) {
            3 -> { target[0] = 1.00f; target[1] = 0.88f; target[2] = 0.38f; target[3] = alpha * 0.92f }
            2 -> { target[0] = 0.70f; target[1] = 0.56f; target[2] = 1.00f; target[3] = alpha * 0.84f }
            else -> { target[0] = 0.42f; target[1] = 0.94f; target[2] = 0.84f; target[3] = alpha * 0.76f }
        }
    }

    private fun triangle(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, colorA: FloatArray, colorB: FloatArray, colorC: FloatArray
    ) {
        vertex(ax, ay, az, colorA); vertex(bx, by, bz, colorB); vertex(cx, cy, cz, colorC)
    }

    private fun vertex(x: Float, y: Float, z: Float, color: FloatArray) {
        val base = vertexCount * FLOATS_PER_VERTEX
        vertices[base] = x; vertices[base + 1] = y; vertices[base + 2] = z
        vertices[base + 3] = 0f; vertices[base + 4] = 1f; vertices[base + 5] = 0f
        vertices[base + 6] = color[0]; vertices[base + 7] = color[1]
        vertices[base + 8] = color[2]; vertices[base + 9] = color[3]
        vertexCount++
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 10
        private const val TRAIL_POINTS = 72
        private const val DUST_PARTICLES = 40
        private const val MAX_VERTICES = (TRAIL_POINTS - 1) * 6 + DUST_PARTICLES * 6
        private const val TRAIL_INTERVAL = 1f / 30f
        private const val DUST_INTERVAL = 0.075f
        private const val TRAIL_LIFETIME = 1.35f
        private const val DUST_LIFETIME = 0.72f
    }
}
