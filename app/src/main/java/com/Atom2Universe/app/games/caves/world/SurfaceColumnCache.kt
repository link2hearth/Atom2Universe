package com.Atom2Universe.app.games.caves.world

/** Bijective mix: nearby X/Z columns must not all share Long.hashCode() buckets. */
internal fun columnCacheKey(x: Int, z: Int): Long {
    var key = (x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
    key = (key xor (key ushr 30)) * -4658895280553007687L
    key = (key xor (key ushr 27)) * -7723592293110705685L
    return key xor (key ushr 31)
}

/** Fixed-size, allocation-free cache. Each worker owns its instance. Collisions only evict. */
internal class SurfaceColumnCache {
    private val keys = LongArray(4096)
    private val heights = IntArray(4096) { Int.MIN_VALUE }
    private fun slot(key: Long) = (key xor (key ushr 32)).toInt() and 4095
    fun get(key: Long): Int {
        val i = slot(key)
        return if (keys[i] == key) heights[i] else Int.MIN_VALUE
    }
    fun put(key: Long, height: Int) {
        val i = slot(key)
        keys[i] = key
        heights[i] = height
    }
}
