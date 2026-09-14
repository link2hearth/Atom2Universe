package com.Atom2Universe.app.games.caves.ai

import kotlin.math.floor

internal fun interface BodyClearance {
    fun isFree(x: Double, y: Double, z: Double): Boolean
}

/** Volume debout, coordonnées locales aux pieds ; les contacts exacts sont autorisés. */
internal object SoldierCollision {
    const val RADIUS = .30
    const val HEIGHT = 1.8
    fun clearsWorld(world: SolidGrid, x: Double, y: Double, z: Double): Boolean {
        for (bx in floor(x - RADIUS + .0001).toInt()..floor(x + RADIUS - .0001).toInt())
            for (by in floor(y + .0001).toInt()..floor(y + HEIGHT - .0001).toInt())
                for (bz in floor(z - RADIUS + .0001).toInt()..floor(z + RADIUS - .0001).toInt())
                    if (world.isSolid(bx, by, bz)) return false
        return true
    }

    fun overlaps(x: Double, y: Double, z: Double, otherX: Double, otherY: Double,
                 otherZ: Double, otherHeight: Double = HEIGHT): Boolean =
        y < otherY + otherHeight - .0001 && y + HEIGHT > otherY + .0001 &&
            (x - otherX) * (x - otherX) + (z - otherZ) * (z - otherZ) < 4 * RADIUS * RADIUS - .0001
}
