package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants
import kotlin.math.abs

/** A finite, hand-composed material garden. Never used by the survival generator. */
internal object ShowcaseMap {
    private const val ORIGINAL_WIDTH = 72
    const val WIDTH = 232
    fun villageAt(x: Int, z: Int): Int? =
        if (x in 76..227 && z in 4..51) ((x - 76) / 52).takeIf { (x - 76) % 52 < 48 } else null
    const val HEIGHT = 40
    const val FLOOR = 4
    const val TREE_Z = 50
    const val COLD_Z = 158
    const val GALLERY_Z = 212
    fun gardenContains(x: Int, z: Int) = x in 76..151 && z in 56..113
    fun gardenSample(x: Int, z: Int): Pair<Int, Int>? {
        if (!gardenContains(x, z)) return null
        val bank = if (x < 114) 0 else 1
        val row = ((z - 60) / 4).coerceIn(0, if (bank == 0) 9 else 8)
        val stage = ((x - (82 + bank * 38) + 2) / 5).coerceIn(0, 4)
        return (bank * 10 + row) to stage
    }
    fun coldAt(x: Int, z: Int): Int? {
        if (x !in 0 until ORIGINAL_WIDTH || z !in COLD_Z until GALLERY_Z - 2) return null
        return ((z - COLD_Z) / 18 * 3 + x / 24).takeIf { it in 0..7 }
    }
    val treeTypes = listOf("oak", "birch", "sapin", "darkwood", "jungle", "redwood",
        "apple", "pear", "pink", "purple", "blue", "yellow",
        "giant_redwood", "giant_pine", "broad_oak", "baobab", "acacia", "willow")

    fun treeAt(x: Int, z: Int): Int? {
        if (x !in 0 until ORIGINAL_WIDTH || z !in TREE_Z until COLD_Z - 2) return null
        val index = (z - TREE_Z) / 18 * 3 + x / 24
        return index.takeIf { it in treeTypes.indices }
    }
    const val COLUMNS = 16
    fun mobGalleryZ() = GALLERY_Z + ((galleryBlocks().size + COLUMNS - 1) / COLUMNS) * 4 + 8
    private fun mobBayCount() = com.Atom2Universe.app.games.caves.node.MobRegistry.all().size + 1 +
        com.Atom2Universe.app.games.caves.entity.PassiveAnimals.displays.size

    fun galleryBlocks(): List<Short> = BlockRegistry.all().filter { it.placeable }.map { it.id }.filter { it != AIR }.sorted()

    // Each vignette occupies 16 x 16 = 256 ground cells, with wide flat paths between them.
    fun zoneAt(x: Int, z: Int): Int {
        for (zone in 0..5) {
            val ox = 4 + zone % 3 * 22
            val oz = 4 + zone / 3 * 22
            if (x in ox until ox + 16 && z in oz until oz + 16) return zone
        }
        return if (treeAt(x, z) != null) 8 else if (z >= GALLERY_Z - 2) 6 else 7
    }

    fun climateAt(x: Int, z: Int): Int {
        villageAt(x, z)?.let { return when (it) { 0 -> 4; 1 -> 1; else -> 0 } }
        if (coldAt(x, z) != null) return 4
        // Adjacent grass samples on the avenue make the narrow color transition inspectable.
        if (z in 44..46 && x in 4..19) return if (x < 12) 0 else 3
        if (z in 44..46 && x in 48..63) return if (x < 56) 1 else 2
        return when (zoneAt(x, z)) {
        0 -> 4
        1, 5 -> 1
        4 -> 3
        else -> 0
        }
    }

    fun create(): A2Map {
        val exhibits = galleryBlocks()
        val depth = mobGalleryZ() + ((mobBayCount()+2)/3)*10 + 4
        require(WIDTH.toLong() * HEIGHT * depth <= A2Map.MAX_VOLUME)
        val blocks = ShortArray(WIDTH * HEIGHT * depth)
        val meta = ByteArray(blocks.size)
        fun put(x: Int, y: Int, z: Int, block: Short, rotation: Byte = 0) {
            require(x in 0 until WIDTH && y in 0 until HEIGHT && z in 0 until depth)
            val i = x + WIDTH * (z + depth * y)
            blocks[i] = block; meta[i] = rotation
        }
        fun fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, block: Short) {
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) put(x, y, z, block)
        }
        for (z in 0 until depth) for (x in 0 until WIDTH) {
            // L-shaped extension: keep the long existing exhibition and widen only its entrance.
            if (x >= ORIGINAL_WIDTH && z > 55 && !gardenContains(x, z)) continue
            fill(x, 0, z, x, FLOOR - 1, z, STONE)
            put(x, FLOOR, z, if (z < GALLERY_Z - 3) SANDSTONE else 2202)
            if (x == 0 || x == WIDTH - 1 || z == 0 || z == depth - 1 ||
                (x == ORIGINAL_WIDTH - 1 && z > 55) ||
                (x >= ORIGINAL_WIDTH && z == 55 && x !in 78..149))
                put(x, FLOOR + 1, z, COBBLESTONE)
        }
        // Two banks of ten/nine species. Five fixed growth snapshots along each bed.
        // Open paths let the player compare front, side and overhead views of crossed sprites.
        for (crop in FarmShowcasePlants.crops.indices) {
            val x0 = 82 + crop / 10 * 38
            val z0 = 62 + crop % 10 * 4
            fill(x0 - 2, FLOOR, z0 - 1, x0 + 22, FLOOR, z0 + 1,
                com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND)
            for (stage in FarmShowcasePlants.growth.indices) {
                put(x0 + stage * 5, FLOOR + 1, z0, FarmShowcasePlants.id(crop, stage))
            }
            // A dense mature patch after the five samples: one plant per adjacent soil block.
            fill(x0 + 25, FLOOR, z0 - 1, x0 + 27, FLOOR, z0 + 1,
                com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND)
            fill(x0 + 25, FLOOR + 1, z0 - 1, x0 + 27, FLOOR + 1, z0 + 1,
                FarmShowcasePlants.id(crop, FarmShowcasePlants.growth.lastIndex))
        }
        // Low border and a contrasting entrance path behind the villages.
        for (z in 56..113) {
            put(76, FLOOR + 1, z, COBBLESTONE)
            put(151, FLOOR + 1, z, COBBLESTONE)
        }
        fill(76, FLOOR + 1, 113, 151, FLOOR + 1, 113, COBBLESTONE)
        fill(111, FLOOR, 52, 116, FLOOR, 112, COBBLESTONE)
        fill(98, FLOOR, 52, 116, FLOOR, 54, COBBLESTONE)
        for ((index, style) in VillageArchitecture.Style.entries.withIndex()) {
            VillageArchitecture.generate(style, 260914 + index, listOf(3, 5, 6)[index]) { x, y, z, id ->
                put(76 + index * 52 + x, FLOOR + y, 4 + z, id)
            }
        }
        fill(65, FLOOR, 5, WIDTH - 2, FLOOR, 7, COBBLESTONE)
        val tops = IntArray(WIDTH * depth) { FLOOR }
        for (zone in 0..5) {
            val ox = 4 + zone % 3 * 22
            val oz = 4 + zone / 3 * 22
            for (z in 0..15) for (x in 0..15) {
                val wx = ox + x; val wz = oz + z
                val ridge = (9 - maxOf(abs(x - 8), abs(z - 8))).coerceAtLeast(0)
                val top = when (zone) {
                    0 -> FLOOR + ridge
                    1 -> FLOOR + ((8 - abs(x - 8) - abs(z - 9)).coerceAtLeast(0) / 3)
                    2 -> if (x > 7) FLOOR - 2 else FLOOR
                    4 -> if (x in 5..11 && z in 4..11) FLOOR - 1 else FLOOR
                    5 -> FLOOR + ridge / 2
                    else -> FLOOR
                }
                val surface: Short = when (zone) {
                    0 -> if (ridge > 6) SNOW else if ((x + z) % 7 == 0) COBBLESTONE else STONE
                    1 -> if (x < 4) SANDSTONE else if (z > 11) REDSAND else SAND
                    2 -> if (x < 5) SAND else if (z % 5 == 0) GRAVEL else CLAY
                    3 -> if ((x + z) % 6 == 0) MOSS else FOREST_FLOOR
                    4 -> if (x < 4) CLAY else if (z > 11) MOSS else MUD
                    else -> BASALT
                }
                val under = when (zone) { 1 -> SANDSTONE; 3, 4 -> DIRT; 5 -> BASALT; else -> STONE }
                for (y in 1..top) put(wx, y, wz, if (y == top) surface else under)
                for (y in top + 1..FLOOR) put(wx, y, wz, WATER)
                tops[wx + wz * WIDTH] = maxOf(top, FLOOR)
                if (zone == 3 && (x * 3 + z) % 17 == 0) put(wx, top + 1, wz, 7046)
                if (zone == 4 && x == 4 && z % 4 == 0) put(wx, FLOOR + 1, wz, 7052)
            }
            when (zone) {
                0 -> {
                    fill(ox + 2, 5, oz + 3, ox + 4, 6, oz + 5, COBBLESTONE)
                    fill(ox + 11, 5, oz + 2, ox + 13, 6, oz + 4, ICE)
                    put(ox + 12, 7, oz + 3, BLUE_ICE)
                    // Open mineral cut in the mountain, accessible directly from the path.
                    for (i in 0..8) put(ox + 3 + i, 5, oz, (3000 + i).toShort())
                }
                1 -> {
                    fill(ox + 12, 5, oz + 3, ox + 12, 7, oz + 3, CACTUS)
                    put(ox + 5, tops[ox + 5 + (oz + 12) * WIDTH] + 1, oz + 12, 7053)
                }
                3 -> {
                    for ((tx, tz, wood, leaves) in listOf(
                        intArrayOf(4, 5, WOOD.toInt(), LEAVES.toInt()),
                        intArrayOf(11, 11, WOOD_WHITE.toInt(), LEAVES.toInt()))) {
                        fill(ox + tx, 5, oz + tz, ox + tx, 9, oz + tz, wood.toShort())
                        for (dy in 0..2) for (dz in -2..2) for (dx in -2..2) {
                            if (abs(dx) + abs(dz) <= 3 - dy / 2)
                                put(ox + tx + dx, 9 + dy, oz + tz + dz, leaves.toShort())
                        }
                    }
                    put(ox + 8, 5, oz + 3, MOSSY_COBBLESTONE)
                    put(ox + 8, 6, oz + 3, 7011)
                    put(ox + 2, 5, oz + 12, 7055)
                }
                4 -> fill(ox + 12, 5, oz + 3, ox + 13, 6, oz + 4, MOSSY_COBBLESTONE)
                5 -> {
                    // Recessed lava pool: never placed above an open side.
                    for (z in 6..10) for (x in 6..10)
                        if (x == 6 || x == 10 || z == 6 || z == 10) put(ox + x, 8, oz + z, BASALT)
                    fill(ox + 7, 8, oz + 7, ox + 9, 8, oz + 9, LAVA)
                    fill(ox + 7, 9, oz + 7, ox + 9, 10, oz + 9, AIR)
                }
            }
        }
        // Workshop beside the main avenue: fronts, backs and tops can all be inspected.
        put(26, 5, 45, FURNACE)
        put(30, 5, 45, TABLE)
        put(34, 5, 45, FURNACE, 2)
        put(38, 5, 45, TABLE, 2)
        for (z in 44..46) for (x in (4..19) + (48..63)) put(x, FLOOR, z, GRASS)
        for (x in intArrayOf(6, 10, 13, 17, 50, 54, 57, 61)) put(x, FLOOR + 1, 45, 7056)
        // Full production trees, separated by walking aisles; fruit species are explicit.
        for ((index, type) in treeTypes.withIndex()) {
            val tx = 12 + index % 3 * 24
            val tz = TREE_Z + 8 + index / 3 * 18
            fill(tx - 7, FLOOR, tz - 7, tx + 7, FLOOR, tz + 7, if (type == "baobab" || type == "acacia") SAND else GRASS)
            TreeShape.generate(type, kotlin.random.Random(8100 + index)) { dx, dy, dz, block, onlyAir ->
                val x = tx + dx; val y = FLOOR + dy; val z = tz + dz
                if (!onlyAir || blocks[x + WIDTH * (z + depth * y)] == AIR)
                    put(x, y, z, block, if (isLeaf(block)) LeafSupport.PERSISTENT else 0)
            }
        }
        for (kind in 0..7) {
            val tx = 12 + kind % 3 * 24
            val tz = COLD_Z + 8 + kind / 3 * 18
            fill(tx - 7, FLOOR, tz - 7, tx + 7, FLOOR, tz + 7, DIRT_SNOW)
            ColdLandscape.generate(kind, kotlin.random.Random(9140 + kind), { _, _ -> FLOOR }) { x, y, z, id, rotation ->
                put(tx + x, y, tz + z, id, rotation)
            }
        }
        // Every registered block, including markers and liquids. No capture(): it would strip markers.
        for ((i, block) in exhibits.withIndex()) {
            val x = 4 + i % COLUMNS * 4
            val z = GALLERY_Z + i / COLUMNS * 4
            put(x, FLOOR + 1, z, block)
            if (isWater(block) || block == LAVA) {
                put(x - 1, FLOOR + 1, z, GLASS); put(x + 1, FLOOR + 1, z, GLASS)
                put(x, FLOOR + 1, z - 1, GLASS); put(x, FLOOR + 1, z + 1, GLASS)
            }
        }
        // Flush ground bays, three display bodies per species, with open walking aisles.
        for (row in 0 until (mobBayCount()+2)/3) for (column in 0..2) {
            if (row * 3 + column >= mobBayCount()) continue
            val centerX = 12 + column * 24
            val centerZ = mobGalleryZ() + 5 + row * 10
            fill(centerX - 8, FLOOR, centerZ - 2, centerX + 8, FLOOR, centerZ + 2, SANDSTONE)
        }
        return A2Map("biome_showcase", WIDTH, HEIGHT, depth, blocks, meta,
            listOf(MapPoint(35, 5, 2)), emptyList())
    }
}
