package com.Atom2Universe.app.games.toyboxracers.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Traînées d'échappement suspendues et particules orientées vers la caméra.
 * Pools bornés : aucune allocation de maillage pendant la course.
 */
internal class TurboEffects {
    private val trailX = FloatArray(TRAIL_POINTS)
    private val trailY = FloatArray(TRAIL_POINTS)
    private val trailZ = FloatArray(TRAIL_POINTS)
    private val trailRightX = FloatArray(TRAIL_POINTS)
    private val trailRightZ = FloatArray(TRAIL_POINTS)
    private val trailRightY = FloatArray(TRAIL_POINTS)
    private val trailAge = FloatArray(TRAIL_POINTS) { TRAIL_LIFETIME }
    private val trailBreak = BooleanArray(TRAIL_POINTS)
    private val trailLevel = IntArray(TRAIL_POINTS)
    private var trailHead = 0
    private var trailCount = 0
    private var trailTimer = 0f
    private var recordingTrail = false
    private var pendingTrailBreak = true

    // Un seul tas de particules pour la poussière, les étincelles et les flammes :
    // elles ne diffèrent que par leur couleur, leur pesanteur et leur durée.
    private val particleX = FloatArray(PARTICLES)
    private val particleY = FloatArray(PARTICLES)
    private val particleZ = FloatArray(PARTICLES)
    private val particleVx = FloatArray(PARTICLES)
    private val particleVy = FloatArray(PARTICLES)
    private val particleVz = FloatArray(PARTICLES)
    private val particleAge = FloatArray(PARTICLES) { PARTICLE_LIFETIME }
    private val particleLife = FloatArray(PARTICLES) { PARTICLE_LIFETIME }
    private val particleKind = IntArray(PARTICLES)
    private val particleLevel = IntArray(PARTICLES)
    private var particleHead = 0
    private var dustTimer = 0f
    private var sparkTimer = 0f
    private var flameTimer = 0f
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
    private val particleTint = FloatArray(4)
    private val particleEdge = FloatArray(4)
    private var cameraRightX = 1f
    private var cameraRightY = 0f
    private var cameraRightZ = 0f
    private var cameraUpX = 0f
    private var cameraUpY = 1f
    private var cameraUpZ = 0f

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
        trailHead = 0
        trailCount = 0
        trailTimer = 0f
        recordingTrail = false
        pendingTrailBreak = true
        for (index in particleAge.indices) particleAge[index] = particleLife[index]
        particleHead = 0
        dustTimer = 0f
        sparkTimer = 0f
        flameTimer = 0f
        previousReleaseSerial = releaseSerial
    }

    fun update(dt: Float, car: ArcadeCar) {
        for (index in trailAge.indices) trailAge[index] += dt
        for (index in particleAge.indices) {
            if (particleAge[index] >= particleLife[index]) continue
            particleAge[index] += dt
            particleX[index] += particleVx[index] * dt
            particleY[index] += particleVy[index] * dt
            particleZ[index] += particleVz[index] * dt
            particleVy[index] -= when (particleKind[index]) {
                KIND_SPARK -> PARTICLE_GRAVITY
                KIND_DUST -> .35f
                else -> 0f
            } * dt
        }

        val boosting = car.turboBoostSeconds > 0f
        // Le ruban lit la charge en cours, même si une relance précédente tourne encore.
        val level = if (car.drifting) car.turboLevel.coerceAtLeast(1) else BOOST_LEVEL
        if (boosting || (car.drifting && !car.airborne)) {
            if (!recordingTrail) pendingTrailBreak = true
            recordingTrail = true
            trailTimer += dt
            if (trailTimer >= TRAIL_INTERVAL) {
                trailTimer -= TRAIL_INTERVAL
                addTrailPoint(car, level, pendingTrailBreak)
                pendingTrailBreak = false
            }
        } else {
            recordingTrail = false
            trailTimer = TRAIL_INTERVAL
        }

        if (car.drifting && !car.airborne) {
            dustTimer += dt
            if (dustTimer >= DUST_INTERVAL) {
                dustTimer -= DUST_INTERVAL
                addDust(car)
            }
            // Le premier niveau reste discret : les étincelles annoncent qu'un
            // vrai gain est en réserve, elles ne doivent pas sortir avant.
            if (car.turboLevel >= 2) {
                sparkTimer += dt
                val interval = if (car.turboLevel >= 3) SPARK_INTERVAL_HIGH else SPARK_INTERVAL
                if (sparkTimer >= interval) {
                    sparkTimer -= interval
                    addSpark(car, car.turboLevel)
                }
            } else sparkTimer = 0f
        } else {
            dustTimer = DUST_INTERVAL
            sparkTimer = 0f
        }

        if (boosting) {
            flameTimer += dt
            if (flameTimer >= FLAME_INTERVAL) {
                flameTimer -= FLAME_INTERVAL
                addFlame(car)
            }
        } else flameTimer = FLAME_INTERVAL

        if (car.turboReleaseSerial != previousReleaseSerial) {
            previousReleaseSerial = car.turboReleaseSerial
            repeat(12) { addSpark(car, 3) }
        }
    }

    private fun addTrailPoint(car: ArcadeCar, level: Int, startsNewRibbon: Boolean) {
        val index = trailHead
        val forwardX = sin(car.yawRadians)
        val forwardZ = cos(car.yawRadians)
        val pitch = if (car.airborne) 0f else car.pitchRadians.coerceIn(-.65f, .65f)
        val roll = if (car.airborne) 0f else car.rollRadians.coerceIn(-.34f, .34f)
        val rearDistance = EXHAUST_OFFSET * cos(pitch)
        trailX[index] = car.worldPosition.x - forwardX * rearDistance
        // Suit la pente du véhicule au niveau de l'échappement, jamais une projection au sol.
        trailY[index] = car.worldPosition.y - sin(pitch) * EXHAUST_OFFSET + .09f
        trailZ[index] = car.worldPosition.z - forwardZ * rearDistance
        trailRightX[index] = forwardZ * cos(roll)
        trailRightY[index] = sin(roll)
        trailRightZ[index] = -forwardX * cos(roll)
        trailAge[index] = 0f
        trailLevel[index] = level
        val previous = (index - 1 + TRAIL_POINTS) % TRAIL_POINTS
        val dx = trailX[index] - trailX[previous]
        val dy = trailY[index] - trailY[previous]
        val dz = trailZ[index] - trailZ[previous]
        trailBreak[index] = startsNewRibbon || dx * dx + dy * dy + dz * dz > 6.25f
        trailHead = (trailHead + 1) % TRAIL_POINTS
        trailCount = (trailCount + 1).coerceAtMost(TRAIL_POINTS)
    }

    private fun spawn(
        x: Float, y: Float, z: Float,
        vx: Float, vy: Float, vz: Float,
        kind: Int, level: Int, life: Float
    ) {
        val index = particleHead
        particleX[index] = x
        particleY[index] = y
        particleZ[index] = z
        particleVx[index] = vx
        particleVy[index] = vy
        particleVz[index] = vz
        particleAge[index] = 0f
        particleLife[index] = life
        particleKind[index] = kind
        particleLevel[index] = level
        particleHead = (particleHead + 1) % PARTICLES
    }

    private fun addDust(car: ArcadeCar) {
        val phase = particleHead * GOLDEN_ANGLE
        spawn(
            car.worldPosition.x + cos(phase) * 0.48f,
            car.worldPosition.y - .14f,
            car.worldPosition.z + sin(phase) * 0.48f,
            cos(phase) * 0.8f, 0f, sin(phase) * 0.8f,
            KIND_DUST, 0, DUST_LIFETIME
        )
    }

    /** Les étincelles partent des deux roues arrière, vers l'extérieur du virage. */
    private fun addSpark(car: ArcadeCar, level: Int) {
        val forwardX = sin(car.yawRadians)
        val forwardZ = cos(car.yawRadians)
        val rightX = forwardZ
        val rightZ = -forwardX
        val side = if (particleHead % 2 == 0) 1f else -1f
        val phase = particleHead * GOLDEN_ANGLE
        val spread = cos(phase) * 0.9f
        spawn(
            car.worldPosition.x - forwardX * REAR_AXLE_OFFSET + rightX * side * REAR_TRACK_HALF,
            car.worldPosition.y - .10f - sin(car.pitchRadians) * REAR_AXLE_OFFSET + sin(car.rollRadians) * side * REAR_TRACK_HALF,
            car.worldPosition.z - forwardZ * REAR_AXLE_OFFSET + rightZ * side * REAR_TRACK_HALF,
            -forwardX * 2.4f + rightX * (side * 1.6f + spread),
            2.9f + sin(phase) * 0.8f,
            -forwardZ * 2.4f + rightZ * (side * 1.6f + spread),
            KIND_SPARK, level, SPARK_LIFETIME
        )
    }

    private fun addFlame(car: ArcadeCar) {
        val forwardX = sin(car.yawRadians)
        val forwardZ = cos(car.yawRadians)
        val side = if (particleHead % 2 == 0) 1f else -1f
        val pitch = if (car.airborne) 0f else car.pitchRadians.coerceIn(-.65f, .65f)
        val rearDistance = EXHAUST_OFFSET * cos(pitch)
        spawn(
            car.worldPosition.x - forwardX * rearDistance + forwardZ * side * EXHAUST_HALF_TRACK,
            car.worldPosition.y - sin(pitch) * EXHAUST_OFFSET + .09f,
            car.worldPosition.z - forwardZ * rearDistance - forwardX * side * EXHAUST_HALF_TRACK,
            -forwardX * 1.6f, .4f, -forwardZ * 1.6f,
            KIND_FLAME, BOOST_LEVEL, FLAME_LIFETIME
        )
    }

    fun draw(shader: ToyboxShader, viewProjection: FloatArray, identity: FloatArray, view: FloatArray) {
        cameraRightX = view[0]; cameraRightY = view[4]; cameraRightZ = view[8]
        cameraUpX = view[1]; cameraUpY = view[5]; cameraUpZ = view[9]
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
        buildRibbons()
        buildParticles()
    }

    private fun buildRibbons() {
        if (trailCount < 2) return
        val oldest = (trailHead - trailCount + TRAIL_POINTS) % TRAIL_POINTS
        for (offset in 0 until trailCount - 1) {
            val a = (oldest + offset) % TRAIL_POINTS
            val b = (a + 1) % TRAIL_POINTS
            if (trailBreak[b]) continue
            if (trailAge[a] >= TRAIL_LIFETIME || trailAge[b] >= TRAIL_LIFETIME) continue
            val lifeA = 1f - trailAge[a] / TRAIL_LIFETIME
            val lifeB = 1f - trailAge[b] / TRAIL_LIFETIME
            // Deux rubans croisés par échappement : visibles de dessus et depuis la caméra.
            // Ils flottent derrière la caisse, sans projection sur la piste.
            for (core in 0..1) {
                val widthA = (.045f + .14f * lifeA) * if (core == 0) 1f else .38f
                val widthB = (.045f + .14f * lifeB) * if (core == 0) 1f else .38f
                ribbonColor(trailLevel[a], lifeA, colorA)
                ribbonColor(trailLevel[b], lifeB, colorB)
                if (core == 1) {
                    for (channel in 0..2) {
                        colorA[channel] = colorA[channel] * .4f + .6f
                        colorB[channel] = colorB[channel] * .4f + .6f
                    }
                } else { colorA[3] *= .75f; colorB[3] *= .75f }
                for (side in SIDES) {
                    val ax = trailX[a] + trailRightX[a] * side * EXHAUST_HALF_TRACK
                    val az = trailZ[a] + trailRightZ[a] * side * EXHAUST_HALF_TRACK
                    val ay = trailY[a] + trailRightY[a] * side * EXHAUST_HALF_TRACK
                    val bx = trailX[b] + trailRightX[b] * side * EXHAUST_HALF_TRACK
                    val bz = trailZ[b] + trailRightZ[b] * side * EXHAUST_HALF_TRACK
                    val by = trailY[b] + trailRightY[b] * side * EXHAUST_HALF_TRACK
                    // Face horizontale surélevée : la bande colorée reste bien lisible.
                    val arx = trailRightX[a] * widthA; val arz = trailRightZ[a] * widthA
                    val brx = trailRightX[b] * widthB; val brz = trailRightZ[b] * widthB
                    triangle(ax - arx, ay, az - arz, bx - brx, by, bz - brz, bx + brx, by, bz + brz,
                        colorA, colorB, colorB)
                    triangle(ax - arx, ay, az - arz, bx + brx, by, bz + brz, ax + arx, ay, az + arz,
                        colorA, colorB, colorA)
                    triangle(ax, ay - widthA, az, bx, by - widthB, bz, bx, by + widthB, bz,
                        colorA, colorB, colorB)
                    triangle(ax, ay - widthA, az, bx, by + widthB, bz, ax, ay + widthA, az,
                        colorA, colorB, colorA)
                }
            }
        }
    }

    private fun buildParticles() {
        for (index in particleAge.indices) {
            val lifetime = particleLife[index]
            if (particleAge[index] >= lifetime) continue
            val life = 1f - particleAge[index] / lifetime
            val size = when (particleKind[index]) {
                KIND_SPARK -> 0.055f + life * 0.055f
                KIND_FLAME -> 0.09f + (1f - life) * 0.26f
                else -> 0.10f + (1f - life) * 0.20f
            }
            particleColor(particleKind[index], particleLevel[index], life, particleTint)
            val x = particleX[index]
            val y = particleY[index]
            val z = particleZ[index]
            // Disque à bord transparent : pas de carrés opaques dans les flammes.
            particleEdge[0] = particleTint[0]; particleEdge[1] = particleTint[1]
            particleEdge[2] = particleTint[2]; particleEdge[3] = 0f
            for (corner in 0..7) {
                val a = corner * kotlin.math.PI.toFloat() / 4f
                val b = (corner + 1) * kotlin.math.PI.toFloat() / 4f
                val ax = (cameraRightX * cos(a) + cameraUpX * sin(a)) * size
                val ay = (cameraRightY * cos(a) + cameraUpY * sin(a)) * size
                val az = (cameraRightZ * cos(a) + cameraUpZ * sin(a)) * size
                val bx = (cameraRightX * cos(b) + cameraUpX * sin(b)) * size
                val by = (cameraRightY * cos(b) + cameraUpY * sin(b)) * size
                val bz = (cameraRightZ * cos(b) + cameraUpZ * sin(b)) * size
                triangle(x, y, z, x + ax, y + ay, z + az, x + bx, y + by, z + bz,
                    particleTint, particleEdge, particleEdge)
            }
        }
    }

    private fun ribbonColor(level: Int, alpha: Float, target: FloatArray) {
        when (level) {
            BOOST_LEVEL -> { target[0] = 1.00f; target[1] = 0.62f; target[2] = 0.30f; target[3] = alpha * 0.95f }
            3 -> { target[0] = 1.00f; target[1] = 0.88f; target[2] = 0.38f; target[3] = alpha * 0.92f }
            2 -> { target[0] = 0.70f; target[1] = 0.56f; target[2] = 1.00f; target[3] = alpha * 0.84f }
            else -> { target[0] = 0.42f; target[1] = 0.94f; target[2] = 0.84f; target[3] = alpha * 0.76f }
        }
    }

    private fun particleColor(kind: Int, level: Int, life: Float, target: FloatArray) {
        when (kind) {
            KIND_SPARK -> {
                ribbonColor(level, 1f, target)
                // Une étincelle est un point chaud : sa couleur est tirée vers le
                // blanc pour qu'elle se détache du ruban dont elle sort.
                target[0] = target[0] * 0.45f + 0.55f
                target[1] = target[1] * 0.45f + 0.55f
                target[2] = target[2] * 0.45f + 0.55f
                target[3] = life * 0.95f
            }
            KIND_FLAME -> {
                target[0] = 1.00f
                target[1] = 0.45f + life * 0.45f
                target[2] = 0.20f + life * 0.45f
                target[3] = life * 0.62f
            }
            else -> {
                target[0] = .76f; target[1] = .80f; target[2] = .86f; target[3] = life * .22f
            }
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
        private const val PARTICLES = 96
        private const val MAX_VERTICES = (TRAIL_POINTS - 1) * 48 + PARTICLES * 24
        private const val TRAIL_INTERVAL = 1f / 30f
        private const val TRAIL_LIFETIME = .78f
        private const val DUST_INTERVAL = 0.075f
        private const val DUST_LIFETIME = 0.72f
        private const val SPARK_INTERVAL = 0.045f
        private const val SPARK_INTERVAL_HIGH = 0.026f
        private const val SPARK_LIFETIME = 0.42f
        private const val FLAME_INTERVAL = 0.03f
        private const val FLAME_LIFETIME = 0.34f
        private const val PARTICLE_LIFETIME = 1f
        private const val PARTICLE_GRAVITY = 9f
        private const val REAR_AXLE_OFFSET = 0.46f
        private const val REAR_TRACK_HALF = 0.49f
        private const val EXHAUST_OFFSET = .82f
        private const val EXHAUST_HALF_TRACK = .245f
        private const val BOOST_LEVEL = 4
        private const val KIND_DUST = 0
        private const val KIND_SPARK = 1
        private const val KIND_FLAME = 2
        private const val GOLDEN_ANGLE = 2.39996f
        private val SIDES = floatArrayOf(-1f, 1f)
    }
}
