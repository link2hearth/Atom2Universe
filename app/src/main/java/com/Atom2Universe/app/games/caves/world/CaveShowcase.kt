package com.Atom2Universe.app.games.caves.world

import kotlin.math.abs

/** Six walk-in dioramas in the unused wing to the right of the crop garden. */
internal object CaveShowcase {
    fun contains(x: Int, z: Int) = x in 156..231 && z in 56..155

    fun biomeAt(x: Int, z: Int): Int? {
        if (!contains(x, z) || z < 62) return null
        val column = (x - 156) / 38
        val row = (z - 62) / 32
        return (row * 2 + column).takeIf { row in 0..2 && (x - 156) % 38 < 32 && (z - 62) % 32 < 26 }
    }

    fun build(put: (Int, Int, Int, Short) -> Unit) {
        val floor = ShowcaseMap.FLOOR
        for ((index, biome) in UndergroundDecor.Biome.entries.withIndex()) {
            val ox = 156 + index % 2 * 38
            val oz = 62 + index / 2 * 32
            fun block(x: Int, y: Int, z: Int, id: Short) = put(ox + x, floor + y, oz + z, id)
            fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, id: Short) {
                for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) block(x, y, z, id)
            }
            box(0, 0, 0, 31, 0, 25, biome.floor)
            // Open front; roof and side walls make the rear a real shaded cave sample.
            box(0, 1, 10, 0, 14, 25, biome.rock)
            box(31, 1, 10, 31, 14, 25, biome.rock)
            box(0, 1, 25, 31, 14, 25, biome.rock)
            box(0, 14, 10, 31, 14, 25, biome.rock)
            val samples = when (biome) {
                UndergroundDecor.Biome.CALCITE -> shortArrayOf(2201, 2613, 2600)
                UndergroundDecor.Biome.ROOTS -> shortArrayOf(STONE, MOSS, 2608, 2606, 2601)
                UndergroundDecor.Biome.FUNGAL -> shortArrayOf(2607, 2609, 2610, 2611, 2602)
                UndergroundDecor.Biome.CRYSTAL -> shortArrayOf(QUARTZ, 2614, 2603)
                UndergroundDecor.Biome.FROZEN -> shortArrayOf(2612, BLUE_ICE, 2604)
                UndergroundDecor.Biome.BASALT -> shortArrayOf(BASALT, 2307, 2605)
            }
            samples.forEachIndexed { i, id ->
                block(4 + i * 5, 1, 3, STONE)
                block(4 + i * 5, 2, 3, id)
            }
            fun spire(x: Int, z: Int, height: Int, id: Short, hanging: Boolean = false) {
                for (h in 0 until height) {
                    val radius = if (h < height / 2) 1 else 0
                    for (dz in -radius..radius) for (dx in -radius..radius)
                        if (abs(dx) + abs(dz) <= radius) block(x + dx, if (hanging) 13 - h else 1 + h, z + dz, id)
                }
            }
            when (biome) {
                UndergroundDecor.Biome.CALCITE -> {
                    spire(7, 17, 7, 2613); spire(23, 19, 5, 2613)
                    spire(15, 21, 6, 2613, true)
                }
                UndergroundDecor.Biome.ROOTS -> {
                    for (x in intArrayOf(7, 16, 24)) {
                        box(x, 7, 19, x, 13, 19, 2608)
                        box(x, 10, 19, x + 2, 10, 19, 2608)
                        block(x + 2, 9, 19, 2608)
                        block(x, 6, 19, 2601)
                    }
                    box(4, 0, 12, 10, 0, 16, CLAY)
                    box(5, 0, 13, 9, 0, 15, WATER)
                    box(20, 0, 13, 24, 0, 15, 2606)
                }
                UndergroundDecor.Biome.FUNGAL -> {
                    for ((x, z, height) in listOf(Triple(8, 17, 6), Triple(22, 20, 8))) {
                        box(x, 1, z, x, height, z, 2609)
                        for (dx in -2..2) for (dz in -2..2) if (dx * dx + dz * dz <= 5) {
                            block(x + dx, height, z + dz, 2610)
                            if (abs(dx) + abs(dz) <= 2) block(x + dx, height + 1, z + dz, 2610)
                        }
                        block(x + 1, height, z, 2602)
                    }
                    for (x in 12..18 step 3) block(x, 1, 13, 2611)
                }
                UndergroundDecor.Biome.CRYSTAL -> {
                    spire(8, 17, 8, 2614); spire(12, 19, 4, 2614)
                    spire(23, 19, 6, 2614); spire(18, 21, 5, 2614, true)
                }
                UndergroundDecor.Biome.FROZEN -> {
                    spire(8, 18, 6, BLUE_ICE); spire(23, 19, 8, BLUE_ICE)
                    spire(16, 18, 7, BLUE_ICE, true)
                    box(11, 0, 12, 19, 0, 16, BLUE_ICE)
                }
                UndergroundDecor.Biome.BASALT -> {
                    spire(7, 19, 8, BASALT); spire(24, 20, 6, BASALT)
                    box(12, 0, 13, 19, 0, 19, BASALT)
                    box(13, 0, 14, 18, 0, 18, LAVA)
                }
            }
            // Compare each engraving in daylight on its plinth and in shade on the back wall.
            block(15, 5, 24, if (index == 1) 2601 else biome.glow)
        }
    }
}
