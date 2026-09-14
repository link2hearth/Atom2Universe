package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry

/** Placement constraints apply to player edits, not to biome generation or map imports. */
internal object BlockPlacement {
    fun supported(id: Short, x: Int, y: Int, z: Int, blockAt: (Int, Int, Int) -> Short): Boolean {
        val rule = BlockRegistry.get(id)?.placementRule ?: return false
        if (rule == "any") return true
        val below = blockAt(x, y - 1, z)
        val tags = BlockRegistry.get(below)?.tags.orEmpty()
        return when (rule) {
            "solid" -> below != AIR && !isDecoration(below) && !isWater(below) && below != LAVA
            "soil" -> "soil" in tags
            "sand" -> "sand" in tags
            "cactus" -> ("sand" in tags || below == CACTUS) &&
                listOf(x - 1 to z, x + 1 to z, x to z - 1, x to z + 1).all { (nx, nz) ->
                    val b = blockAt(nx, y, nz)
                    b == AIR || isDecoration(b)
                }
            "reeds" -> ("soil" in tags || "sand" in tags) &&
                listOf(x - 1 to z, x + 1 to z, x to z - 1, x to z + 1).any { (nx, nz) ->
                    isWater(blockAt(nx, y - 1, nz)) || isWater(blockAt(nx, y, nz))
                }
            else -> false
        }
    }
}
