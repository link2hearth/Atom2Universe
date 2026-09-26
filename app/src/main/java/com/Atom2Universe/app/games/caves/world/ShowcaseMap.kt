package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants
import kotlin.math.abs

/** A finite, hand-composed material garden. Never used by the survival generator. */
internal object ShowcaseMap {
    private const val ORIGINAL_WIDTH = 72
    const val WIDTH = 204
    const val HEIGHT = 40
    const val FLOOR = 4
    const val TREE_Z = 50
    const val COLD_Z = 158
    const val GALLERY_Z = 212

    // Version 5 landmarks, as the survival generator builds them, on flat ground.
    // One row per climate (temperate, cold, arid): the hamlet on the left, the five small sites on the right.
    private const val FRONTIER_Z = 116
    private const val FRONTIER_END_Z = 355
    private const val FRONTIER_AISLE_X = 124
    fun frontierContains(x: Int, z: Int) = x in 76 until WIDTH && z in FRONTIER_Z..FRONTIER_END_Z
    // Mechanics wing beside the crop garden, entered from the promenade. Every source turns here.
    fun mechanicsContains(x: Int, z: Int) = x in 153 until WIDTH && z in 56..114
    private val mechanicsExhibits = listOf(
        intArrayOf(155, 57, 167, 63), intArrayOf(169, 57, 175, 63), intArrayOf(176, 56, 187, 63),
        intArrayOf(188, 56, 201, 64), intArrayOf(155, 65, 167, 75), intArrayOf(169, 65, 179, 75),
        intArrayOf(180, 65, 201, 75), intArrayOf(155, 77, 167, 87), intArrayOf(169, 77, 179, 87))
    /** Index of the mechanical exhibit under (x, z), in the order of cave_showcase_mechanics_names. */
    fun mechanicsAt(x: Int, z: Int): Int? = mechanicsExhibits.indexOfFirst { (x0, z0, x1, z1) -> x in x0..x1 && z in z0..z1 }
        .takeIf { it >= 0 && mechanicsContains(x, z) }
    // First the six house models, one short row per climate, then the settlements.
    private const val HOUSES_Z = FRONTIER_Z + 4
    private const val HOUSE_ROW = 18
    private const val SETTLEMENTS_Z = HOUSES_Z + 3 * HOUSE_ROW + 2
    private val houseX = intArrayOf(79, 94, 109, 130, 145, 160)
    private fun houseOrigin(climate: Int, model: Int) = houseX[model] to HOUSES_Z + 3 + climate * HOUSE_ROW
    private fun frontierRowZ(climate: Int) = SETTLEMENTS_Z + climate * 60
    private fun frontierClimate(z: Int) =
        (if (z < SETTLEMENTS_Z) (z - HOUSES_Z) / HOUSE_ROW else (z - SETTLEMENTS_Z) / 60).coerceIn(0, 2)

    /** Index climate * models + model of the house under (x, z), eaves and porch included. */
    fun houseAt(x: Int, z: Int): Int? {
        if (!frontierContains(x, z) || z >= SETTLEMENTS_Z) return null
        for (climate in 0..2) for ((model, m) in FrontierHouses.models.withIndex()) {
            val (ox, oz) = houseOrigin(climate, model)
            if (x in ox - 1..ox + m.width && z in oz - 1..oz + m.depth) return climate * FrontierHouses.models.size + model
        }
        return null
    }
    private fun frontierOrigin(climate: Int, kind: Int): Pair<Int, Int> =
        if (kind == 0) 80 to frontierRowZ(climate) + 6
        else (129 + (kind - 1) % 3 * 25) to frontierRowZ(climate) + 6 + (kind - 1) / 3 * 30

    /** Index climate * KINDS + kind of the landmark under (x, z), entrance stair included. */
    fun frontierAt(x: Int, z: Int): Int? {
        if (!frontierContains(x, z)) return null
        for (climate in 0..2) for (kind in 0 until FrontierLandscape.KINDS) {
            val (ox, oz) = frontierOrigin(climate, kind)
            val size = FrontierLandscape.size(kind)
            if (x in ox - 1..ox + size && z in oz - 5..oz + size) return climate * FrontierLandscape.KINDS + kind
        }
        return null
    }

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
        if (frontierContains(x, z)) return when (frontierClimate(z)) { 1 -> 4; 2 -> 1; else -> 0 }
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

    /** Working displays of every rule of the rotation network (see KineticNetwork). */
    private fun buildMechanics(put: (Int, Int, Int, Short, Byte) -> Unit) {
        val y = FLOOR + 1
        for (z in 56..114) put(153, y, z, COBBLESTONE, 0)
        for (x in 153 until WIDTH) put(x, y, 114, COBBLESTONE, 0)
        for (z in 52..58) for (x in 174..179) put(x, FLOOR, z, COBBLESTONE, 0)
        fun part(x: Int, h: Int, z: Int, id: Short, axis: Char) =
            put(x, y + h, z, id, ((when (axis) { 'x' -> 1; 'z' -> 2; else -> 0 }) or 8).toByte())
        val F = com.Atom2Universe.app.games.caves.node.FrontierItems
        // 0. Wheel, shafts and mill.
        part(157, 0, 60, F.WATERWHEEL, 'y'); for (x in 158..164) part(x, 0, 60, F.SHAFT, 'x'); part(165, 0, 60, F.MILL, 'y')
        // 1. Crank and vertical shaft.
        part(172, 0, 60, F.CRANK, 'y'); for (h in 1..4) part(172, h, 60, F.SHAFT, 'y')
        // 2. Cogwheels side by side, turning in turn.
        part(178, 1, 58, F.CRANK, 'z'); for (x in 178..184) part(x, 1, 59, F.COGWHEEL, 'z')
        // 3. Large and small cogwheels: the small ones turn twice as fast.
        part(193, 2, 58, F.CRANK, 'z'); part(193, 2, 59, F.LARGE_COGWHEEL, 'z')
        part(194, 3, 59, F.COGWHEEL, 'z'); part(192, 1, 59, F.COGWHEEL, 'z')
        for (z in 60..62) part(194, 3, z, F.SHAFT, 'z')
        // 4. Gearbox: a quarter turn each side, reversed straight through.
        part(157, 0, 70, F.WATERWHEEL, 'y'); for (x in 158..160) part(x, 0, 70, F.SHAFT, 'x')
        part(161, 0, 70, F.GEARBOX, 'y')
        for (z in 71..73) part(161, 0, z, F.SHAFT, 'z'); for (z in 67..69) part(161, 0, z, F.SHAFT, 'z')
        for (x in 162..165) part(x, 0, 70, F.SHAFT, 'x')
        // 5. From horizontal to vertical, as in a windmill: the gearbox sends the rotation down to the mill.
        part(174, 3, 66, F.CRANK, 'z'); for (z in 67..68) part(174, 3, z, F.SHAFT, 'z')
        part(174, 3, 69, F.GEARBOX, 'x'); for (h in 1..2) part(174, h, 69, F.SHAFT, 'y'); part(174, 0, 69, F.MILL, 'y')
        // 6. Four machines at work: one wheel for crusher, mill and loom (8 of 8), another for the press.
        part(182, 0, 70, F.WATERWHEEL, 'y'); for (x in 183..185) part(x, 0, 70, F.SHAFT, 'x')
        part(186, 0, 70, F.GEARBOX, 'y'); part(187, 0, 70, F.CRUSHER, 'y')
        part(186, 0, 71, F.MILL, 'y'); part(186, 0, 69, F.LOOM, 'y')
        part(192, 0, 70, F.WATERWHEEL, 'y'); part(193, 0, 70, F.SHAFT, 'x'); part(194, 0, 70, F.PRESS, 'y')
        // 7. Too much for one wheel: press, crusher and mill need 10, the wheel gives 8. Everything stops.
        part(157, 0, 82, F.WATERWHEEL, 'y'); for (x in 158..160) part(x, 0, 82, F.SHAFT, 'x')
        part(161, 0, 82, F.GEARBOX, 'y'); part(161, 0, 83, F.PRESS, 'y'); part(162, 0, 82, F.CRUSHER, 'y')
        part(161, 0, 81, F.MILL, 'y')
        // 8. Gears that cannot agree: round the loop the speed comes back doubled. Everything stops.
        part(172, 0, 82, F.CRANK, 'y'); part(172, 1, 82, F.COGWHEEL, 'y'); part(173, 1, 82, F.COGWHEEL, 'y')
        part(172, 2, 82, F.SHAFT, 'y'); part(172, 3, 82, F.LARGE_COGWHEEL, 'y')
        part(173, 3, 83, F.COGWHEEL, 'y'); part(173, 2, 83, F.SHAFT, 'y'); part(173, 1, 83, F.COGWHEEL, 'y')
    }

    fun create(): A2Map {
        val exhibits = galleryBlocks()
        val depth = maxOf(mobGalleryZ() + ((mobBayCount()+2)/3)*10 + 4, FRONTIER_END_Z + 2)
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
            if (x >= ORIGINAL_WIDTH && z > 55 && !gardenContains(x, z) && !frontierContains(x, z) && !mechanicsContains(x, z)) continue
            fill(x, 0, z, x, FLOOR - 1, z, STONE)
            put(x, FLOOR, z, if (z < GALLERY_Z - 3) SANDSTONE else 2202)
            if (x == 0 || x == WIDTH - 1 || z == 0 || z == depth - 1 ||
                (x == ORIGINAL_WIDTH - 1 && z > 55) ||
                (x >= ORIGINAL_WIDTH && z == 55 && x !in 78..149 && x !in 174..179))
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
        buildMechanics(::put)
        // Frontier landmarks behind the crop garden, reached through its central gate.
        fill(111, FLOOR + 1, 113, 116, FLOOR + 1, 113, AIR)
        fill(111, FLOOR, 113, 116, FLOOR, 118, COBBLESTONE)
        fill(111, FLOOR, 116, FRONTIER_AISLE_X + 3, FLOOR, 118, COBBLESTONE)
        for (x in 76 until WIDTH) {
            if (x !in 111..116) put(x, FLOOR + 1, FRONTIER_Z, COBBLESTONE)
            put(x, FLOOR + 1, FRONTIER_END_Z, COBBLESTONE)
        }
        for (z in FRONTIER_Z..FRONTIER_END_Z) put(76, FLOOR + 1, z, COBBLESTONE)
        for (climate in 0..2) {
            fill(77, FLOOR, HOUSES_Z + climate * HOUSE_ROW, WIDTH - 2, FLOOR, HOUSES_Z + climate * HOUSE_ROW + HOUSE_ROW - 1,
                when (climate) { 1 -> DIRT_SNOW; 2 -> SAND; else -> GRASS })
            val z0 = frontierRowZ(climate) - 2
            fill(77, FLOOR, z0, WIDTH - 2, FLOOR, minOf(frontierRowZ(climate) + 57, FRONTIER_END_Z - 1),
                when (climate) { 1 -> DIRT_SNOW; 2 -> SAND; else -> GRASS })
        }
        fill(FRONTIER_AISLE_X, FLOOR, FRONTIER_Z + 1, FRONTIER_AISLE_X + 3, FLOOR, FRONTIER_END_Z - 1, COBBLESTONE)
        for (climate in 0..2) for (model in FrontierHouses.models.indices) {
            val (ox, oz) = houseOrigin(climate, model)
            FrontierHouses.build(model, FrontierHouses.style(arid = climate == 2, cold = climate == 1)) { x, y, z, id ->
                put(ox + x, FLOOR + y, oz + z, id)
            }
        }
        for (climate in 0..2) for (kind in 0 until FrontierLandscape.KINDS) {
            val (ox, oz) = frontierOrigin(climate, kind)
            val blueprint = FrontierLandscape.blueprint(kind, arid = climate == 2, cold = climate == 1,
                kotlin.random.Random(7300 + kind)) { _, _ -> 0 }
            for ((pos, id) in blueprint) put(ox + pos.first, FLOOR + pos.second, oz + pos.third, id)
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
            listOf(MapPoint(FRONTIER_AISLE_X + 2, FLOOR + 1, FRONTIER_Z + 2)), emptyList(), emptyList())
    }
}
