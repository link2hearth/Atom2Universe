package com.Atom2Universe.app.games.caves.world

/** Stable IDs; leaf metadata keeps bit 2 for persistent decorative leaves. */
internal object TreeSpecies {
    val types = listOf("oak", "birch", "sapin", "darkwood", "jungle", "redwood",
        "apple", "pear", "pink", "purple", "blue", "yellow",
        "giant_redwood", "giant_pine", "broad_oak", "baobab", "acacia", "willow",
        "jungle_small", "autumn")
    const val DROP_CHANCE = .05f
    const val DROP_REACH = 32.0
    fun sapling(species: Int): Short = (9500 + species).toShort()
    fun species(id: Short): Int? = (id.toInt() - 9500).takeIf { it in types.indices }
    fun duration(species: Int): Long = if (types[species] in GrandTrees.types) 600_000L else 300_000L
    fun leafMeta(type: String, persistent: Boolean = false): Byte {
        val species = types.indexOf(type)
        require(species >= 0) { "Unknown tree species $type" }
        return (((species + 1) shl 3) or if (persistent) LeafSupport.PERSISTENT.toInt() else 0).toByte()
    }
    fun fromLeaf(meta: Byte): Int? = (((meta.toInt() and 255) ushr 3) - 1).takeIf { it in types.indices }
    fun persistent(meta: Byte) = meta.toInt() and LeafSupport.PERSISTENT.toInt() != 0
    fun drop(meta: Byte, roll: Float): Short? =
        if (roll < DROP_CHANCE) fromLeaf(meta)?.let(::sapling) else null
}
