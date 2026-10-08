package com.Atom2Universe.app.games.caves.render

import kotlin.math.abs

/** Teste le quad entier, pas seulement son centre, avant la division perspective.
 * Les coordonnées sont relatives à la caméra, comme celles de ProjectileRenderer. */
internal object ProjectileVisibility {
    fun inFrontOfNearPlane(
        x: Float, y: Float, z: Float, rightX: Float, rightZ: Float,
        halfSize: Float, vp: FloatArray
    ): Boolean {
        // Plan proche OpenGL : clip.z + clip.w > 0.
        val nx = vp[2] + vp[3]
        val ny = vp[6] + vp[7]
        val nz = vp[10] + vp[11]
        val center = nx*x + ny*y + nz*z + vp[14] + vp[15]
        val extent = halfSize * (abs(nx*rightX + nz*rightZ) + abs(ny))
        return center - extent > 0.001f
    }
}
