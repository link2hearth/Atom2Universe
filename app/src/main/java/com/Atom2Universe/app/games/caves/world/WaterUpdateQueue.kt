package com.Atom2Universe.app.games.caves.world

/** Local edits must not wait behind thousands of shoreline updates from terrain streaming. */
internal class WaterUpdateQueue {
    private val local = LinkedHashMap<Long, IntArray>()
    private val terrain = LinkedHashMap<Long, IntArray>()

    @Synchronized fun offer(key: Long, x: Int, y: Int, z: Int, urgent: Boolean) {
        if (key in local) return
        if (urgent) {
            local[key] = terrain.remove(key) ?: intArrayOf(x, y, z)
        } else if (key !in terrain) terrain[key] = intArrayOf(x, y, z)
    }

    @Synchronized fun size(urgent: Boolean): Int = if (urgent) local.size else terrain.size

    @Synchronized fun poll(urgent: Boolean): IntArray? {
        val iterator = (if (urgent) local else terrain).entries.iterator()
        if (!iterator.hasNext()) return null
        val result = iterator.next().value
        iterator.remove()
        return result
    }
}
