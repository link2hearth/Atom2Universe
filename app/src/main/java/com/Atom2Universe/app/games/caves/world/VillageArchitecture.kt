package com.Atom2Universe.app.games.caves.world

import kotlin.random.Random

/** Reusable flat-ground village recipes. Coordinates include the ground layer (y = 0).
 * Kept separate from survival placement until terrain fitting has been implemented.
 */
internal object VillageArchitecture {
    const val SIZE = 48
    enum class Style(val ground: Short, val wall: Short, val frame: Short, val roof: Short) {
        NORDIC(DIRT_SNOW, PLANK_SAPIN, WOOD_DARK, PLANK_DARK),
        OASIS(SAND, SANDSTONE, BRICK_TERRACOTTA, BRICK_SANDY),
        STONE_TOWN(GRASS, BRICK_GREY, WOOD, BRICK_RED)
    }

    /** Stable seed controls paving; each plot receives a distinct house recipe. */
    fun generate(style: Style, seed: Int, homes: Int, put: (Int, Int, Int, Short) -> Unit) {
        require(homes in 3..6)
        val random = Random(seed)
        fun fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, id: Short) {
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) {
                require(x in 0 until SIZE && z in 0 until SIZE && y in 0..30)
                put(x, y, z, id)
            }
        }
        fill(0, 0, 0, 47, 0, 47, style.ground)
        // A continuous public avenue and a generous square around the landmark.
        fill(0, 0, 1, 47, 0, 3, COBBLESTONE)
        fill(22, 0, 2, 25, 0, 45, COBBLESTONE)
        for (z in 13..35) for (x in 13..34)
            put(x, 0, z, if (random.nextInt(7) == 0) style.frame else style.roof)

        val plots = listOf(2 to 6, 36 to 6, 2 to 32, 36 to 32, 2 to 19, 36 to 19)
        for ((index, plot) in plots.take(homes).withIndex()) {
            val (ox, oz) = plot
            VillageHouses.generate(style, index) { x, y, z, block ->
                fill(ox + x, y, oz + z, ox + x, y, oz + z, block)
            }
            // Branch paths reach the front door without crossing another plot.
            val lane = if (index % 2 == 0) 13 else 34
            fill(minOf(ox + 4, lane), 0, oz - 2, maxOf(ox + 5, lane), 0, oz - 1, COBBLESTONE)
        }
        when (style) {
            Style.OASIS -> {
                // Nine stepped terraces, a summit shrine and a walk-in burial chamber.
                for (step in 0..8)
                    fill(14 + step, 1 + step, 14 + step, 33 - step, 1 + step, 33 - step,
                        if (step % 3 == 0) BRICK_TERRACOTTA else SANDSTONE)
                fill(22, 1, 14, 25, 3, 27, AIR)
                fill(19, 1, 20, 28, 4, 27, AIR)
                fill(22, 1, 25, 25, 1, 26, BRICK_COBALT)
                for (x in listOf(22, 25)) for (z in listOf(22, 25))
                    fill(x, 10, z, x, 13, z, BRICK_COBALT)
                fill(21, 14, 21, 26, 14, 26, BRICK_SANDY)
                put(20, 1, 21, TORCH); put(27, 1, 21, TORCH)
            }
            Style.STONE_TOWN -> {
                // Monumental open colonnade: checker floor, capitals, entablature, pediment.
                for (x in 15..32) for (z in 15..33)
                    put(x, 0, z, if ((x + z) % 2 == 0) QUARTZ else BRICK_GREY)
                for (x in listOf(15, 19, 28, 32)) for (z in listOf(16, 24, 32)) {
                    fill(x, 1, z, x, 10, z, QUARTZ)
                    fill(x - 1, 11, z - 1, x + 1, 11, z + 1, BRICK_SANDY)
                }
                fill(14, 12, 14, 33, 12, 34, QUARTZ)
                for (step in 0..9) {
                    fill(14 + step, 13 + step / 2, 14, 14 + step, 13 + step / 2, 34, BRICK_RED)
                    fill(33 - step, 13 + step / 2, 14, 33 - step, 13 + step / 2, 34, BRICK_RED)
                }
                fill(22, 1, 29, 25, 2, 31, QUARTZ)
                put(22, 3, 30, TORCH); put(25, 3, 30, TORCH)
            }
            Style.NORDIC -> {
                // Guardian statue holding a raised crystal staff, on a patterned gathering place.
                fill(20, 1, 21, 27, 1, 28, COBBLESTONE)
                fill(21, 2, 22, 26, 2, 27, BRICK_GREY)
                for (x in listOf(22, 25)) fill(x, 3, 24, x, 6, 25, STONE)
                fill(22, 7, 24, 25, 11, 26, STONE)
                fill(23, 12, 24, 24, 14, 25, QUARTZ)
                fill(20, 10, 24, 27, 11, 25, STONE)
                fill(28, 3, 24, 28, 15, 24, WOOD_DARK)
                fill(27, 16, 23, 29, 17, 25, BLUE_ICE)
                for (x in listOf(17, 30)) {
                    fill(x, 1, 30, x, 1, 33, PLANK_SAPIN)
                    put(x, 1, 18, COBBLESTONE); put(x, 2, 18, TORCH)
                }
            }
        }
    }
}
