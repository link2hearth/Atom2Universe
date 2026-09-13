package com.Atom2Universe.app.games.caves.ai

import kotlin.math.abs
import kotlin.math.floor

/**
 * « Est-ce que je vois ce point ? » sur une grille de blocs.
 *
 * On suit le segment bloc par bloc, sans en sauter ni en tester en trop (méthode d'Amanatides et
 * Woo) : à chaque pas, on passe dans le bloc voisin dont la frontière est la plus proche le long
 * du segment. Le premier bloc plein rencontré coupe la vue.
 */
internal object LineOfSight {

    /**
     * Vrai si le segment de (x0, y0, z0) à (x1, y1, z1) ne traverse aucun bloc plein. Le bloc de
     * départ n'est pas testé (l'œil est forcément dans un bloc libre) ; celui d'arrivée l'est.
     */
    fun isClear(
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        world: SolidGrid,
    ): Boolean {
        var x = floor(x0).toInt(); var y = floor(y0).toInt(); var z = floor(z0).toInt()
        val endX = floor(x1).toInt(); val endY = floor(y1).toInt(); val endZ = floor(z1).toInt()
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0

        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        val stepZ = if (dz > 0) 1 else -1

        // tDelta : fraction du segment pour traverser un bloc entier sur cet axe.
        // tMax : fraction du segment à laquelle on franchit la prochaine frontière sur cet axe.
        val tDeltaX = if (dx != 0.0) 1.0 / abs(dx) else Double.POSITIVE_INFINITY
        val tDeltaY = if (dy != 0.0) 1.0 / abs(dy) else Double.POSITIVE_INFINITY
        val tDeltaZ = if (dz != 0.0) 1.0 / abs(dz) else Double.POSITIVE_INFINITY
        var tMaxX = when { dx > 0 -> (x + 1 - x0) * tDeltaX; dx < 0 -> (x0 - x) * tDeltaX; else -> Double.POSITIVE_INFINITY }
        var tMaxY = when { dy > 0 -> (y + 1 - y0) * tDeltaY; dy < 0 -> (y0 - y) * tDeltaY; else -> Double.POSITIVE_INFINITY }
        var tMaxZ = when { dz > 0 -> (z + 1 - z0) * tDeltaZ; dz < 0 -> (z0 - z) * tDeltaZ; else -> Double.POSITIVE_INFINITY }

        // Exactement un pas par frontière franchie : on finit pile dans le bloc d'arrivée.
        var steps = abs(endX - x) + abs(endY - y) + abs(endZ - z)
        while (steps-- > 0) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { x += stepX; tMaxX += tDeltaX }
            else if (tMaxY < tMaxZ) { y += stepY; tMaxY += tDeltaY }
            else { z += stepZ; tMaxZ += tDeltaZ }
            if (world.isSolid(x, y, z)) return false
        }
        return true
    }
}
