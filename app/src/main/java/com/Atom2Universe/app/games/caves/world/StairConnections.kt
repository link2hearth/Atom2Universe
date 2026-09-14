package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry

/** Single shape resolver for meshes, player collision, selection, projectiles and bot sight. */
internal object StairConnections {
    fun maskAt(x: Int, y: Int, z: Int, blockAt: (Int, Int, Int) -> Short,
               metaAt: (Int, Int, Int) -> Byte): Int {
        if (BlockRegistry.get(blockAt(x, y, z))?.stairs != true) return -1
        return PartialBlockModel.connectedMask(metaAt(x, y, z)) { dx, dz ->
            if (BlockRegistry.get(blockAt(x + dx, y, z + dz))?.stairs == true)
                metaAt(x + dx, y, z + dz) else null
        }
    }
}