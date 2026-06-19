package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry

const val CHUNK_SIZE = 16

// Plafond (en chunks) de la bande de surface. Partagé entre World (génération), LodBuilder
// (scan du heightmap LOD) et CaveRenderer (déclenchement LOD + frustum). Permet aux reliefs
// hauts (montagnes) d'être générés ET capturés/affichés par le LOD jusqu'à Y ≈ 1023.
const val SURFACE_CY_MAX = 63

// ── Spécial ──────────────────────────────────────────────────────────────────
const val AIR: Short = 0

// ── Famille 100 : Terrain de base ────────────────────────────────────────────
const val DIRT:        Short = 100
const val GRASS:       Short = 101
const val GRAVEL:      Short = 102
const val GRAVEL_DIRT: Short = 103
const val DIRT_SAND:   Short = 104
const val DIRT_SNOW:   Short = 105

// ── Famille 1000 : Bois ───────────────────────────────────────────────────────
const val WOOD:            Short = 1000
const val WOOD_WHITE:      Short = 1001
const val WOOD_RED:        Short = 1002
const val WOOD_DARK:       Short = 1003
const val WOOD_JUNGLE:     Short = 1004
const val WOOD_BLUE:       Short = 1005
const val WOOD_PURPLE:     Short = 1006
const val WOOD_YELLOW:     Short = 1007
const val WOOD_PINK:       Short = 1008
const val WOOD_SAPIN:      Short = 1009
const val PLANK:           Short = 1010
const val WOOD_PLANK_WHITE:Short = 1011
const val PLANK_RED:       Short = 1012
const val PLANK_DARK:      Short = 1013
const val PLANK_JUNGLE:    Short = 1014
const val PLANK_BLUE:      Short = 1015
const val PLANK_PURPLE:    Short = 1016
const val PLANK_YELLOW:    Short = 1017
const val PLANK_PINK:      Short = 1018
const val PLANK_SAPIN:     Short = 1019
const val LEAVES:          Short = 1020
const val LEAVES_ORANGE:   Short = 1021
const val LEAVES_FALL:     Short = 1022
const val LEAVES_RED:      Short = 1023
const val LEAVES_DARK:     Short = 1024
const val LEAVES_JUNGLE:   Short = 1025
const val LEAVES_BLUE:     Short = 1026
const val LEAVES_PURPLE:   Short = 1027
const val LEAVES_YELLOW:   Short = 1028
const val LEAVES_PINK:     Short = 1029
const val LEAVES_SAPIN:    Short = 1031

// ── Famille 2000 : Pierre ─────────────────────────────────────────────────────
const val STONE:               Short = 2000
const val GRANITE:             Short = 2001
const val QUARTZ:              Short = 2002
const val BRICK_RED:           Short = 2010
const val BRICK_GREY:          Short = 2011
const val BRICK_TERRACOTTA:    Short = 2012
const val BRICK_SANDY:         Short = 2013
const val BRICK_COBALT:        Short = 2014
const val BRICK_OBSIDIAN:      Short = 2015
const val BRICK_MOSS:          Short = 2016
const val BRICK_ROSE:          Short = 2017
const val ROCK:                Short = 2020
const val ROCK_MOSS:           Short = 2021
const val STONE_GRASS:         Short = 2030
const val STONE_DIRT:          Short = 2031
const val STONE_SAND:          Short = 2032
const val STONE_SNOW:          Short = 2033
const val GREYSAND:            Short = 2050
const val GREYSTONE:           Short = 2051
const val GREYSTONE_GREYSAND:  Short = 2052

// ── Famille 3000 : Minerais ───────────────────────────────────────────────────
const val COAL:     Short = 3000
const val IRON:     Short = 3001
const val SILVER:   Short = 3002
const val GOLD:     Short = 3003
const val COPPER:   Short = 3004
const val RUBY:     Short = 3005
const val EMERALD:  Short = 3006
const val CRYSTAL:  Short = 3007
const val REDSTONE: Short = 3008

// ── Famille 4000 : Désert / Sable ─────────────────────────────────────────────
const val SAND:              Short = 4000
const val REDSAND:           Short = 4001
const val REDSTONE_REDSAND:  Short = 4002
const val CACTUS:            Short = 4010

// ── Famille 5000 : Glace / Neige ──────────────────────────────────────────────
const val ICE:      Short = 5000
const val SNOW:     Short = 5001
const val BLUE_ICE: Short = 5002

// ── Famille 6000 : Liquides ───────────────────────────────────────────────────
const val LAVA:       Short = 6000
const val WATER:      Short = 6001
const val WATER_FLOW: Short = 6002

// ── Famille 7000 : Végétation décorative ──────────────────────────────────────
const val GRASS_WILD1:  Short = 7000
const val GRASS_WILD2:  Short = 7001
const val GRASS_WILD3:  Short = 7002
const val GRASS_WILD4:  Short = 7003
const val GRASS_BROWN:  Short = 7004
const val GRASS_TAN:    Short = 7005
const val MUSHROOM_RED:   Short = 7010
const val MUSHROOM_BROWN: Short = 7011
const val MUSHROOM_TAN:   Short = 7012
const val WHEAT1: Short = 7020
const val WHEAT2: Short = 7021
const val WHEAT3: Short = 7022
const val WHEAT4: Short = 7023

// ── Famille 8000 : Fonctionnel ────────────────────────────────────────────────
const val FURNACE:    Short = 8000
const val TABLE:      Short = 8001
const val TORCH:      Short = 8002
const val GLASS:      Short = 8003
const val WARD_STONE: Short = 8004

// ── Famille 9000 : Cotton (34 couleurs) ──────────────────────────────────────
const val COTTON_AMBER:      Short = 9000
const val COTTON_BLACK:      Short = 9001
const val COTTON_BLUE:       Short = 9002
const val COTTON_BROWN:      Short = 9003
const val COTTON_CORAL:      Short = 9004
const val COTTON_CRIMSON:    Short = 9005
const val COTTON_CYAN:       Short = 9006
const val COTTON_DARK_GREEN: Short = 9007
const val COTTON_GOLD:       Short = 9008
const val COTTON_GREEN:      Short = 9009
const val COTTON_HOT_PINK:   Short = 9010
const val COTTON_INDIGO:     Short = 9011
const val COTTON_LAVENDER:   Short = 9012
const val COTTON_LIGHT_BLUE: Short = 9013
const val COTTON_LIME:       Short = 9014
const val COTTON_MAGENTA:    Short = 9015
const val COTTON_MINT:       Short = 9016
const val COTTON_NAVY:       Short = 9017
const val COTTON_OLIVE:      Short = 9018
const val COTTON_ORANGE:     Short = 9019
const val COTTON_PEACH:      Short = 9020
const val COTTON_PINK:       Short = 9021
const val COTTON_PURPLE:     Short = 9022
const val COTTON_RED:        Short = 9023
const val COTTON_ROSE:       Short = 9024
const val COTTON_SALMON:     Short = 9025
const val COTTON_SILVER:     Short = 9026
const val COTTON_SKY:        Short = 9027
const val COTTON_TAN:        Short = 9028
const val COTTON_TEAL:       Short = 9029
const val COTTON_TURQUOISE:  Short = 9030
const val COTTON_VIOLET:     Short = 9031
const val COTTON_WHITE:      Short = 9032
const val COTTON_YELLOW:     Short = 9033

// ── Famille 10000 : Items ─────────────────────────────────────────────────────
const val BUCKET_EMPTY: Short = 10000
const val BUCKET_FULL:  Short = 10001

// ── Propriétés de bloc — délèguent à BlockRegistry après load() ───────────────
fun isDecoration(block: Short)  = BlockRegistry.isDecoration(block)
fun isWaterlogged(block: Short) = BlockRegistry.isWaterlogged(block)
fun isWood(block: Short)        = BlockRegistry.isWood(block)
fun isLeaf(block: Short)        = BlockRegistry.isLeaf(block)
fun isTransparent(block: Short) = BlockRegistry.isTransparent(block)
fun isWater(block: Short)       = BlockRegistry.isWater(block)
fun isFalling(block: Short)     = BlockRegistry.isFalling(block)

data class StructureHint(val lx: Int, val ly: Int, val lz: Int, val type: Int)

class Chunk(val cx: Int, val cy: Int, val cz: Int) {
    val blocks = ShortArray(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE)
    val meta   = ByteArray(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE)
    // Lumière du ciel par voxel : niveau 0..15 dans le quartet bas (le quartet haut est réservé
    // à une éventuelle lumière de bloc). Rempli par LightEngine, lu par MeshBuilder.
    val light  = ByteArray(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE)

    @Volatile var generated = false
    @Volatile var meshDirty = false
    // Mis à true quand la skylight change ; déclenche un unique rebuild du mesh une fois stabilisée
    // (lu/écrit seulement par le passage de lumière, mono-thread de fond).
    var lightMeshDirty = false
    // Masque des faces de bord (0..5) dont la skylight a changé depuis le dernier mesh : à la
    // stabilisation, on en déduit quels voisins re-mesher (leurs faces de bord nous échantillonnent).
    var lightBoundaryDirty = 0
    @Volatile var waterMeshDirty = false
    @Volatile var pendingVertices: FloatArray? = null
    @Volatile var version = 0
    @Volatile var waterVersion = 0
    var structureHints: MutableList<StructureHint>? = null

    val worldX get() = cx * CHUNK_SIZE
    val worldY get() = cy * CHUNK_SIZE
    val worldZ get() = cz * CHUNK_SIZE

    fun blockAt(lx: Int, ly: Int, lz: Int): Short {
        if (lx !in 0 until CHUNK_SIZE || ly !in 0 until CHUNK_SIZE || lz !in 0 until CHUNK_SIZE) return AIR
        return blocks[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE]
    }

    fun setBlock(lx: Int, ly: Int, lz: Int, type: Short) {
        blocks[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE] = type
    }

    fun metaAt(lx: Int, ly: Int, lz: Int): Byte {
        if (lx !in 0 until CHUNK_SIZE || ly !in 0 until CHUNK_SIZE || lz !in 0 until CHUNK_SIZE) return 0
        return meta[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE]
    }

    fun setMeta(lx: Int, ly: Int, lz: Int, value: Byte) {
        meta[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE] = value
    }

    fun skyAt(lx: Int, ly: Int, lz: Int): Int {
        if (lx !in 0 until CHUNK_SIZE || ly !in 0 until CHUNK_SIZE || lz !in 0 until CHUNK_SIZE) return 0
        return light[lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE].toInt() and 0x0F
    }

    fun setSky(lx: Int, ly: Int, lz: Int, value: Int) {
        val i = lx + ly * CHUNK_SIZE + lz * CHUNK_SIZE * CHUNK_SIZE
        light[i] = ((light[i].toInt() and 0xF0) or (value and 0x0F)).toByte()
    }
}
