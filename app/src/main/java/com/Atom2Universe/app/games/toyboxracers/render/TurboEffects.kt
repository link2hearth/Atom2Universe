package com.Atom2Universe.app.games.toyboxracers.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Ruban Turbo et particules : stockage borné et buffers réutilisés à chaque image.
 *
 * Le ruban est double, une bande par roue arrière, comme les traces d'un kart.
 * Il enregistre pendant la charge — sa couleur dit alors le niveau atteint — et
 * pendant la relance, où il passe en traînée chaude. Les étincelles ne sortent
 * qu'à partir du deuxième niveau : c'est le seul signal qui dise au joueur que
 * relâcher le bouton vaut désormais quelque chose.
 */
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
    private var recordingTrail = false

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
            particleVy[index] -= PARTICLE_GRAVITY * dt
        }

        val boosting = car.turboBoostSeconds > 0f
        // On peut charger pendant une relance : c'est alors le NIVEAU DE CHARGE
        // qui doit se lire sur le ruban, pas la relance en cours. Le joueur a
        // besoin de savoir quand relâcher ; que le turbo tourne, les flammes le
        // disent déjà.
        val level = if (car.drifting) car.turboLevel.coerceAtLeast(1) else BOOST_LEVEL
        if (car.drifting || boosting) {
            val startsNewRibbon = !recordingTrail
            recordingTrail = true
            trailTimer += dt
            if (trailTimer >= TRAIL_INTERVAL) {
                trailTimer -= TRAIL_INTERVAL
                addTrailPoint(car, level, startsNewRibbon)
            }
        } else {
            recordingTrail = false
            trailTimer = TRAIL_INTERVAL
        }

        if (car.drifting) {
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
        trailX[index] = car.worldPosition.x - forwardX * REAR_AXLE_OFFSET
        trailY[index] = car.worldPosition.y - RIBBON_DROP
        trailZ[index] = car.worldPosition.z - forwardZ * REAR_AXLE_OFFSET
        trailRightX[index] = forwardZ
        trailRightZ[index] = -forwardX
        trailAge[index] = 0f
        trailLevel[index] = level
        trailBreak[index] = startsNewRibbon
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
            car.worldPosition.y - 0.235f,
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
            car.worldPosition.y - 0.16f,
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
        val phase = particleHead * GOLDEN_ANGLE
        spawn(
            car.worldPosition.x - forwardX * (REAR_AXLE_OFFSET + 0.1f) + cos(phase) * 0.22f,
            car.worldPosition.y - 0.10f,
            car.worldPosition.z - forwardZ * (REAR_AXLE_OFFSET + 0.1f) + sin(phase) * 0.22f,
            -forwardX * 3.2f, 1.1f, -forwardZ * 3.2f,
            KIND_FLAME, BOOST_LEVEL, FLAME_LIFETIME
        )
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
            // La bande s'affine en vieillissant : la trace paraît s'effacer au
            // lieu de disparaître d'un bloc quand son âge dépasse la durée.
            val widthA = ribbonWidth(trailLevel[a]) * (0.55f + 0.45f * lifeA)
            val widthB = ribbonWidth(trailLevel[b]) * (0.55f + 0.45f * lifeB)
            ribbonColor(trailLevel[a], lifeA, colorA)
            ribbonColor(trailLevel[b], lifeB, colorB)
            // Une bande par roue arrière : deux traces parallèles, pas un tapis.
            for (side in SIDES) {
                val cax = trailX[a] + trailRightX[a] * side * REAR_TRACK_HALF
                val caz = trailZ[a] + trailRightZ[a] * side * REAR_TRACK_HALF
                val cbx = trailX[b] + trailRightX[b] * side * REAR_TRACK_HALF
                val cbz = trailZ[b] + trailRightZ[b] * side * REAR_TRACK_HALF
                val alx = cax - trailRightX[a] * widthA
                val alz = caz - trailRightZ[a] * widthA
                val arx = cax + trailRightX[a] * widthA
                val arz = caz + trailRightZ[a] * widthA
                val blx = cbx - trailRightX[b] * widthB
                val blz = cbz - trailRightZ[b] * widthB
                val brx = cbx + trailRightX[b] * widthB
                val brz = cbz + trailRightZ[b] * widthB
                triangle(alx, trailY[a], alz, blx, trailY[b], blz, brx, trailY[b], brz, colorA, colorB, colorB)
                triangle(alx, trailY[a], alz, brx, trailY[b], brz, arx, trailY[a], arz, colorA, colorB, colorA)
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
            triangle(
                x - size, y, z - size, x + size, y, z - size, x + size, y, z + size,
                particleTint, particleTint, particleTint
            )
            triangle(
                x - size, y, z - size, x + size, y, z + size, x - size, y, z + size,
                particleTint, particleTint, particleTint
            )
        }
    }

    private fun ribbonWidth(level: Int) = when (level) {
        BOOST_LEVEL -> 0.30f
        3 -> 0.30f
        2 -> 0.25f
        else -> 0.20f
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
                target[0] = 1f; target[1] = 0.67f; target[2] = 0.78f; target[3] = life * 0.58f
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
        private const val MAX_VERTICES = (TRAIL_POINTS - 1) * 12 + PARTICLES * 6
        private const val TRAIL_INTERVAL = 1f / 30f
        private const val TRAIL_LIFETIME = 1.35f
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
        private const val RIBBON_DROP = 0.24f
        private const val BOOST_LEVEL = 4
        private const val KIND_DUST = 0
        private const val KIND_SPARK = 1
        private const val KIND_FLAME = 2
        private const val GOLDEN_ANGLE = 2.39996f
        private val SIDES = floatArrayOf(-1f, 1f)
    }
}
