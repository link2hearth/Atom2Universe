package com.Atom2Universe.app.games.caves.world

/** Null means unloaded: never infer decay from missing chunks. Six face-connected steps. */
internal object LeafSupport {
    const val PERSISTENT: Byte = 4
    fun supported(x: Int, y: Int, z: Int, read: (Int, Int, Int) -> Short?): Boolean =
        state(x,y,z,read) != false

    /** True: a known log; false: no log; null: unknown chunks might still provide support. */
    fun state(x: Int, y: Int, z: Int, read: (Int, Int, Int) -> Short?): Boolean? {
        val origin = Triple(x, y, z)
        val seen = hashSetOf(origin)
        var frontier = listOf(origin)
        var unknown = false
        repeat(6) {
            val next = ArrayList<Triple<Int, Int, Int>>()
            for ((a, b, c) in frontier) {
                for (p in arrayOf(Triple(a + 1, b, c), Triple(a - 1, b, c),
                    Triple(a, b + 1, c), Triple(a, b - 1, c), Triple(a, b, c + 1), Triple(a, b, c - 1))) {
                    if (!seen.add(p)) continue
                    val id = read(p.first, p.second, p.third)
                    if (id == null) { unknown = true; continue }
                    if (isWood(id)) return true
                    if (isLeaf(id)) next.add(p)
                }
            }
            frontier = next
        }
        return if (unknown) null else false
    }
}
