package com.Atom2Universe.app.games.caves.node

/** Stable world IDs: lower material first, upper material second. Never reorder the slab IDs. */
internal object DoubleSlabs {
    private const val BASE = 2800
    private const val FIRST_SLAB = 2500
    private const val COUNT = 12

    fun combine(lower: Short, upper: Short): Short? {
        val lo = lower.toInt() - FIRST_SLAB
        val hi = upper.toInt() - FIRST_SLAB
        if (lo !in 0 until COUNT || hi !in 0 until COUNT || lo == hi) return null
        return (BASE + lo * COUNT + hi).toShort()
    }

    fun materials(id: Short): Pair<Short, Short>? {
        val index = id.toInt() - BASE
        if (index !in 0 until COUNT * COUNT || index / COUNT == index % COUNT) return null
        return (FIRST_SLAB + index / COUNT).toShort() to (FIRST_SLAB + index % COUNT).toShort()
    }

    fun definitions(defs: Map<Short, BlockDef>): List<BlockDef> = buildList {
        for (lo in FIRST_SLAB until FIRST_SLAB + COUNT) for (hi in FIRST_SLAB until FIRST_SLAB + COUNT) {
            val id = combine(lo.toShort(), hi.toShort()) ?: continue
            val lower = defs.getValue(lo.toShort())
            val upper = defs.getValue(hi.toShort())
            require(lower.slab && upper.slab)
            add(lower.copy(id = id, name = "double_slab_${lo}_$hi", slab = false,
                textureTop = upper.textureTop, hardness = maxOf(lower.hardness, upper.hardness),
                drop = "", creativeTab = "technical", tags = emptySet()))
        }
    }
}
