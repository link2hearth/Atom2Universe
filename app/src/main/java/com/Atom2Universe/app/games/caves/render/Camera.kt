package com.Atom2Universe.app.games.caves.render

import android.opengl.Matrix
import com.Atom2Universe.app.games.caves.world.CHUNK_SIZE
import kotlin.math.*

internal class Camera(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0) {

    // Position logique du joueur (mouvements, collision, sauvegarde)
    var playerX = x
    var playerY = y
    var playerZ = z

    // Position de l'œil de rendu (floating origin) — dérivée de playerX/Y/Z en TPS
    var x = x; var y = y; var z = z

    var yaw = 0f
    var pitch = 0f
    var thirdPerson = false

    val vpMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)

    val fwdX get() = sin(Math.toRadians(yaw.toDouble())).toFloat()
    val fwdZ get() = cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val rgtX get() = cos(Math.toRadians(yaw.toDouble())).toFloat()
    private val rgtZ get() = -sin(Math.toRadians(yaw.toDouble())).toFloat()

    // Direction FPS (yaw + pitch) — utilisée pour les mouvements et le laser
    val lookX get() = (cos(Math.toRadians(pitch.toDouble())) * sin(Math.toRadians(yaw.toDouble()))).toFloat()
    val lookY get() = (-sin(Math.toRadians(pitch.toDouble()))).toFloat()
    val lookZ get() = (cos(Math.toRadians(pitch.toDouble())) * cos(Math.toRadians(yaw.toDouble()))).toFloat()

    // Direction effective de visée depuis l'œil (identique en FPS, orbite en TPS)
    var aimX = 0f; private set
    var aimY = 0f; private set
    var aimZ = -1f; private set

    fun setProjection(fovDeg: Float, aspect: Float) {
        Matrix.perspectiveM(projMatrix, 0, fovDeg, aspect, 0.1f, 1500f)
    }

    fun update() {
        pitch = pitch.coerceIn(-89f, 89f)

        if (thirdPerson) {
            // La caméra orbite autour de la tête du joueur selon yaw+pitch.
            // Elle recule le long du vecteur look inversé à distance TPP_DIST.
            val pitchRad = Math.toRadians(pitch.toDouble())
            val yawRad   = Math.toRadians(yaw.toDouble())
            // Vecteur depuis la tête du joueur vers l'œil caméra (= opposé du regard)
            val backX = -(cos(pitchRad) * sin(yawRad))
            val backY =   sin(pitchRad)
            val backZ = -(cos(pitchRad) * cos(yawRad))
            // Point d'orbite : au-dessus du sommet de la boîte (playerY - 0.02),
            // la visée passe juste au-dessus de la tête sans toucher la boîte.
            val headY = playerY + TPP_ORBIT_DY
            x = playerX + backX * TPP_DIST
            y = headY   + backY * TPP_DIST
            z = playerZ + backZ * TPP_DIST
            // Direction de visée : de l'œil caméra vers la tête du joueur
            val ax = (playerX - x).toFloat()
            val ay = (headY   - y).toFloat()
            val az = (playerZ - z).toFloat()
            val len = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(0.001f)
            aimX = ax / len; aimY = ay / len; aimZ = az / len
        } else {
            x = playerX; y = playerY; z = playerZ
            aimX = lookX; aimY = lookY; aimZ = lookZ
        }

        // Floating Origin : la caméra est toujours à (0,0,0) en repère OpenGL.
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 0f,
            aimX, aimY, aimZ,
            0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
    }

    fun moveHorizontal(forward: Float, right: Float) {
        playerX += (fwdX * forward + rgtX * right).toDouble()
        playerZ += (fwdZ * forward + rgtZ * right).toDouble()
    }

    fun moveVertical(up: Float) { playerY += up.toDouble() }

    fun chunkX() = Math.floorDiv(Math.floor(playerX).toInt(), CHUNK_SIZE)
    fun chunkY() = Math.floorDiv(Math.floor(playerY).toInt(), CHUNK_SIZE)
    fun chunkZ() = Math.floorDiv(Math.floor(playerZ).toInt(), CHUNK_SIZE)

    fun posString() = "%.1f  %.1f  %.1f".format(playerX, playerY, playerZ)

    // Appelé après une collision caméra : repositionne l'œil et reconstruit les matrices.
    fun applyCollision(ex: Double, ey: Double, ez: Double) {
        x = ex; y = ey; z = ez
        val headY = playerY + TPP_ORBIT_DY
        val ax = (playerX - x).toFloat()
        val ay = (headY   - y).toFloat()
        val az = (playerZ - z).toFloat()
        val len = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(0.001f)
        aimX = ax / len; aimY = ay / len; aimZ = az / len
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, aimX, aimY, aimZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
    }

    companion object {
        const val TPP_DIST        = 4.5    // blocs d'orbite autour du point cible
        const val TPP_ORBIT_DY    = 0.28   // décalage Y du point d'orbite au-dessus de playerY
    }
}
