package com.Atom2Universe.app.games.glutton

import kotlin.math.floor

/**
 * Buckets over one ring's square ([-half, half]²): every entry is filed in each bucket its bounding box
 * touches, so a query only looks at the buckets of its own box. A query may meet an entry twice; callers
 * that care stamp what they have already seen.
 */
internal class GluttonGrid<T>(val half: Double, val count: Int = 64) {
    @PublishedApi internal val size = 2.0 * half / count
    @PublishedApi internal val buckets = arrayOfNulls<ArrayList<T>>(count * count)

    @PublishedApi internal fun column(v: Double) = floor((v + half) / size).toInt().coerceIn(0, count - 1)

    fun add(entry: T, x: Double, y: Double, radius: Double) {
        val c0 = column(x - radius); val c1 = column(x + radius)
        val r0 = column(y - radius); val r1 = column(y + radius)
        for (row in r0..r1) for (col in c0..c1) {
            val i = col + row * count
            (buckets[i] ?: ArrayList<T>(4).also { buckets[i] = it }).add(entry)
        }
    }

    fun clear() { buckets.fill(null) }

    inline fun query(x0: Double, y0: Double, x1: Double, y1: Double, visit: (T) -> Unit) {
        if (x1 < -half || y1 < -half || x0 > half || y0 > half) return
        val c0 = column(x0); val c1 = column(x1); val r0 = column(y0); val r1 = column(y1)
        for (row in r0..r1) for (col in c0..c1) {
            val bucket = buckets[col + row * count] ?: continue
            for (i in bucket.indices) visit(bucket[i])
        }
    }
}
