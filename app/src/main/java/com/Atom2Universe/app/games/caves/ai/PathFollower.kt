package com.Atom2Universe.app.games.caves.ai

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Fait avancer un personnage le long d'un chemin de cases ([NavGrid]).
 *
 * Il vise le centre de chaque case l'une après l'autre, à vitesse constante. La grille garantit
 * que chaque liaison est praticable : pas besoin de collisions, on glisse d'un centre à l'autre et
 * la hauteur suit la case visée (marche, descente). Coordonnées locales à la carte, pieds.
 */
internal class PathFollower(private val grid: NavGrid) {

    /** Le chemin suivi, départ compris. */
    val path = IntList(64)
    private var next = 0

    var x = 0.0; private set
    var y = 0.0; private set
    var z = 0.0; private set
    /** Direction de marche en degrés, même convention que la caméra et les ennemis (0 = +Z). */
    var yawDeg = 0f; private set

    val arrived: Boolean get() = next >= path.size

    /** Index dans [path] de la prochaine case visée (les précédentes sont déjà atteintes). */
    val nextIndex: Int get() = next

    fun place(x: Double, y: Double, z: Double) {
        this.x = x; this.y = y; this.z = z
        stop()
    }

    /** Suit [newPath] à partir de sa 2e case : la 1re est celle où l'on se trouve déjà. */
    fun follow(newPath: IntList) {
        path.copyFrom(newPath)
        next = if (path.size > 1) 1 else path.size
    }

    fun stop() {
        path.clear()
        next = 0
    }

    /** Avance de [speed] blocs par seconde pendant [dt] secondes. Renvoie vrai s'il a bougé. */
    fun advance(dt: Float, speed: Float): Boolean {
        if (arrived) return false
        var budget = (speed * dt).toDouble()
        while (budget > 0.0 && next < path.size) {
            val n = path[next]
            val tx = grid.nodeX[n] + 0.5; val tz = grid.nodeZ[n] + 0.5
            val dx = tx - x; val dz = tz - z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist > 1e-6) yawDeg = Math.toDegrees(atan2(dx, dz)).toFloat()
            if (dist <= budget) {
                x = tx; z = tz
                budget -= dist
                next++
            } else {
                x += dx / dist * budget; z += dz / dist * budget
                budget = 0.0
            }
        }
        // La hauteur rejoint celle de la case visée (ou de la dernière atteinte), sans à-coup.
        val targetY = grid.nodeY[path[if (next < path.size) next else path.size - 1]].toDouble()
        val climb = (VERTICAL_SPEED * dt).toDouble()
        y = when {
            y < targetY -> minOf(targetY, y + climb)
            y > targetY -> maxOf(targetY, y - climb)
            else -> y
        }
        return true
    }

    private companion object {
        /** Blocs par seconde pour monter une marche ou descendre d'un rebord. */
        const val VERTICAL_SPEED = 6f
    }
}
