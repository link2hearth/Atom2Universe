package com.Atom2Universe.app.games.caves.world

const val CHUNK_SIZE = 16

const val AIR: Byte    = 0
const val STONE: Byte  = 1
const val GRANITE: Byte = 2
const val QUARTZ: Byte = 3
const val COAL: Byte    = 4
const val GOLD: Byte    = 5
const val CRYSTAL: Byte = 6
const val DIRT: Byte    = 7
const val GRAVEL: Byte  = 8
const val IRON: Byte    = 9
const val SILVER: Byte  = 10
const val RUBY: Byte    = 11
const val LAVA: Byte    = 12
const val FURNACE: Byte = 13
const val EMERALD: Byte = 14
const val COPPER: Byte  = 15
const val GRASS: Byte   = 16
const val WOOD: Byte    = 17
const val LEAVES: Byte  = 18
const val SAND: Byte    = 19
const val REDSAND: Byte = 20
// Biome blocks
const val ICE: Byte        = 21
const val SNOW: Byte       = 22
const val BRICK_RED: Byte  = 23
// Decorations (non-solid, cross-sprite rendering)
const val ROCK: Byte          = 24
const val ROCK_MOSS: Byte     = 25
const val MUSHROOM_RED: Byte  = 26
const val MUSHROOM_BROWN: Byte = 27
const val MUSHROOM_TAN: Byte  = 28
const val TORCH: Byte         = 29
const val PLANK: Byte         = 30

/** Blocs décoratifs : non-solides, pas de collision, rendu en croix. */
fun isDecoration(block: Byte) =
    block == ROCK || block == ROCK_MOSS ||
    block == MUSHROOM_RED || block == MUSHROOM_BROWN || block == MUSHROOM_TAN ||
    block == TORCH

data class StructureHint(val lx: Int, val ly: Int, val lz: Int, val type: Int)

class Chunk(val cx: Int, val cy: Int, val cz: Int) {
    val blocks = ByteArray(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE)

    @Volatile var generated = false
    @Volatile var meshDirty = false
    @Volatile var pendingVertices: FloatArray? = null
    @Volatile var version = 0   // incrémenté à chaque setBlock → invalide les builds en cours
    var structureHints: MutableList<StructureHint>? = null

    val worldX get() = cx * CHUNK_SIZE
    val worldY get() = cy * CHUNK_SIZE
    val worldZ get() = cz * CHUNK_SIZE

    fun blockAt(lx: Int, ly: Int, lz: Int): Byte {
        if (lx !in 0 until CHUNK_SIZE || ly !in 0 until CHUNK_SIZE || lz !in 0 until CHUNK_SIZE) return AIR
        return blocks[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE]
    }

    fun setBlock(lx: Int, ly: Int, lz: Int, type: Byte) {
        blocks[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE] = type
    }
}
