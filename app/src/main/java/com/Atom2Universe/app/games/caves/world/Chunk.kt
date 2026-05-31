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

// Nouveaux blocs solides
const val BRICK_GREY: Byte  = 31
const val CACTUS: Byte      = 32
const val GLASS: Byte       = 33
const val GRAVEL_DIRT: Byte = 34
const val TABLE: Byte       = 35

// Herbes folles (cross-sprites)
const val GRASS_WILD1: Byte = 36
const val GRASS_WILD2: Byte = 37
const val GRASS_WILD3: Byte = 38
const val GRASS_WILD4: Byte = 39
const val GRASS_BROWN: Byte = 40
const val GRASS_TAN: Byte   = 41

// Blé — 4 stades de croissance (cross-sprites)
const val WHEAT1: Byte = 42
const val WHEAT2: Byte = 43
const val WHEAT3: Byte = 44
const val WHEAT4: Byte = 45

// Biome désert rouge
const val REDSTONE: Byte = 46  // roche rouge (terracotta), base du biome

// Eau (transparent, non-solide, rendu séparé avec blending)
const val WATER:      Byte = 84  // source permanente (placée par joueur ou générée)
const val WATER_FLOW: Byte = 86  // eau qui coule (générée par simulation, temporaire)

// Planches de bouleau blanc (crafté depuis WOOD_WHITE)
const val WOOD_PLANK_WHITE: Byte = 85

// Bloc de garde (posé par le joueur après avoir tué un boss — crée une zone safe)
const val WARD_STONE: Byte = 87

// Items-outils (pas de rendu en monde, interaction spéciale)
const val BUCKET_EMPTY: Byte = 88  // seau vide — clic-placer sur WATER → BUCKET_FULL
const val BUCKET_FULL:  Byte = 89  // seau plein — clic-placer sur AIR  → WATER source + BUCKET_EMPTY

private val DECORATION_TABLE = BooleanArray(256).also { t ->
    for (b in byteArrayOf(ROCK, ROCK_MOSS, MUSHROOM_RED, MUSHROOM_BROWN, MUSHROOM_TAN, TORCH,
                          GRASS_WILD1, GRASS_WILD2, GRASS_WILD3, GRASS_WILD4,
                          GRASS_BROWN, GRASS_TAN, WHEAT1, WHEAT2, WHEAT3, WHEAT4))
        t[b.toInt() and 0xFF] = true
}

/** Blocs décoratifs : non-solides, pas de collision, rendu en croix. */
fun isDecoration(block: Byte) = DECORATION_TABLE[block.toInt() and 0xFF]

// Feuilles d'automne (arbustes, transparent)
const val LEAVES_ORANGE: Byte = 47

// Biome forêt de bouleaux
const val WOOD_WHITE:  Byte = 48  // tronc de bouleau
const val LEAVES_FALL: Byte = 49  // feuilles orange opaques

// Blocs de coton colorés (IDs 50–83, layers 60–93, formule layer = id + 10)
const val COTTON_AMBER:      Byte = 50
const val COTTON_BLACK:      Byte = 51
const val COTTON_BLUE:       Byte = 52
const val COTTON_BROWN:      Byte = 53
const val COTTON_CORAL:      Byte = 54
const val COTTON_CRIMSON:    Byte = 55
const val COTTON_CYAN:       Byte = 56
const val COTTON_DARK_GREEN: Byte = 57
const val COTTON_GOLD:       Byte = 58
const val COTTON_GREEN:      Byte = 59
const val COTTON_HOT_PINK:   Byte = 60
const val COTTON_INDIGO:     Byte = 61
const val COTTON_LAVENDER:   Byte = 62
const val COTTON_LIGHT_BLUE: Byte = 63
const val COTTON_LIME:       Byte = 64
const val COTTON_MAGENTA:    Byte = 65
const val COTTON_MINT:       Byte = 66
const val COTTON_NAVY:       Byte = 67
const val COTTON_OLIVE:      Byte = 68
const val COTTON_ORANGE:     Byte = 69
const val COTTON_PEACH:      Byte = 70
const val COTTON_PINK:       Byte = 71
const val COTTON_PURPLE:     Byte = 72
const val COTTON_RED:        Byte = 73
const val COTTON_ROSE:       Byte = 74
const val COTTON_SALMON:     Byte = 75
const val COTTON_SILVER:     Byte = 76
const val COTTON_SKY:        Byte = 77
const val COTTON_TAN:        Byte = 78
const val COTTON_TEAL:       Byte = 79
const val COTTON_TURQUOISE:  Byte = 80
const val COTTON_VIOLET:     Byte = 81
const val COTTON_WHITE:      Byte = 82
const val COTTON_YELLOW:     Byte = 83

private val TRANSPARENT_TABLE = BooleanArray(256).also { t ->
    t[GLASS.toInt() and 0xFF] = true
    t[LEAVES_ORANGE.toInt() and 0xFF] = true
}

/** Blocs transparents : solides mais laissent voir à travers (faces voisines restent visibles). */
fun isTransparent(block: Byte) = TRANSPARENT_TABLE[block.toInt() and 0xFF]

private val WATER_TABLE = BooleanArray(256).also { t ->
    t[WATER.toInt()      and 0xFF] = true
    t[WATER_FLOW.toInt() and 0xFF] = true
}

/** Eau : non-solide, non-décoratif, rendu séparé avec blending. */
fun isWater(block: Byte) = WATER_TABLE[block.toInt() and 0xFF]

private val FALLING_TABLE = BooleanArray(256).also { t ->
    for (b in byteArrayOf(SAND, REDSAND, GRAVEL, GRAVEL_DIRT))
        t[b.toInt() and 0xFF] = true
}

/** Blocs soumis à la gravité : tombent dans l'air sous eux. */
fun isFalling(block: Byte) = FALLING_TABLE[block.toInt() and 0xFF]

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
