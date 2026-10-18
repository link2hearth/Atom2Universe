package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

/** Exact swept-corridor queries, indexed by longitudinal band rather than scanning the hole. */
internal class GolfFairwayIndex(private val samples: List<GolfRouteNode>, private val padding: Float) {
    private class Segment(val a: GolfRouteNode, val b: GolfRouteNode, padding: Float) {
        val radius = max(a.width, b.width) * .5f
        val low = a.z - radius - padding
        val high = b.z + radius + padding
        val dx = b.x - a.x
        val dz = b.z - a.z
        val lengthSquared = dx * dx + dz * dz
        val left = min(a.x, b.x)
        val right = max(a.x, b.x)
    }
    private val segments = samples.zipWithNext().map { (a, b) -> Segment(a, b, padding) }
        .filter { it.radius >= 2f }
    private val firstBand = segments.minOfOrNull { floor(it.low / BAND).toInt() } ?: 0
    private val buckets: Array<IntArray> = if (segments.isEmpty()) emptyArray() else {
        val lastBand = segments.maxOf { floor(it.high / BAND).toInt() }
        val rows = Array(lastBand - firstBand + 1) { ArrayList<Int>() }
        segments.forEachIndexed { i, s ->
            for (band in floor(s.low / BAND).toInt()..floor(s.high / BAND).toInt())
                rows[band - firstBand].add(i)
        }
        Array(rows.size) { rows[it].toIntArray() }
    }

    fun signedDistance(x: Float, z: Float): Float {
        val band = floor(z / BAND).toInt() - firstBand
        if (band !in buckets.indices) return Float.POSITIVE_INFINITY
        var best = Float.POSITIVE_INFINITY
        for (i in buckets[band]) {
            val s = segments[i]
            if (z < s.low || z > s.high) continue
            val a = s.a; val b = s.b
            if (max(max(s.left - x, x - s.right), max(a.z - z, z - b.z)) - s.radius > best + .0001f) continue
            // Keep the original arithmetic and segment order: only the search changes.
            val dx = s.dx; val dz = s.dz
            val t = (((x - a.x) * dx + (z - a.z) * dz) / s.lengthSquared).coerceIn(0f, 1f)
            val radius = (a.width + (b.width - a.width) * t) * .5f
            best = min(best, hypot(x - a.x - dx * t, z - a.z - dz * t) - radius)
        }
        return best
    }

    private companion object { const val BAND = 16f }
}
