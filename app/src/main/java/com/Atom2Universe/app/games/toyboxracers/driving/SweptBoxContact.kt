package com.Atom2Universe.app.games.toyboxracers.driving

import kotlin.math.abs

internal data class SweptBoxContact(val normalX: Float, val normalZ: Float)

/** Face touchée par le déplacement, même si son extrémité a déjà traversé la boîte. */
internal fun sweepBoxEntry(
    fromX: Float, fromZ: Float, toX: Float, toZ: Float,
    left: Float, right: Float, back: Float, front: Float
): SweptBoxContact? {
    // Un objet déjà en pénétration relève de la séparation statique.
    if (fromX > left && fromX < right && fromZ > back && fromZ < front) return null
    if (maxOf(fromX, toX) < left || minOf(fromX, toX) > right ||
        maxOf(fromZ, toZ) < back || minOf(fromZ, toZ) > front) return null
    var enter = 0f
    var leave = 1f
    var normalX = 0f
    var normalZ = 0f
    for (axis in 0..1) {
        val xAxis = axis == 0
        val start = if (xAxis) fromX else fromZ
        val delta = if (xAxis) toX - fromX else toZ - fromZ
        val lower = if (xAxis) left else back
        val upper = if (xAxis) right else front
        if (abs(delta) < .00001f) {
            if (start <= lower || start >= upper) return null
            continue
        }
        val near = (lower - start) / delta
        val far = (upper - start) / delta
        val entry = minOf(near, far)
        if (entry >= enter) {
            val normal = if (delta > 0f) -1f else 1f
            normalX = if (xAxis) normal else 0f
            normalZ = if (xAxis) 0f else normal
            enter = entry
        }
        leave = minOf(leave, maxOf(near, far))
        if (enter > leave) return null
    }
    if (enter !in 0f..1f || leave <= 0f || (normalX == 0f && normalZ == 0f)) return null
    return SweptBoxContact(normalX, normalZ)
}
