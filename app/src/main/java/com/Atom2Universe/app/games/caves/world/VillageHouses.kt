package com.Atom2Universe.app.games.caves.world

/** Six different floor plans, fitted inside the existing exhibition plots.
 * Local envelope, including eaves and balcony: x=-1..10, z=-2..11, y=0..18.
 */
internal object VillageHouses {
    fun generate(style: VillageArchitecture.Style, variant: Int, put: (Int, Int, Int, Short) -> Unit) {
        require(variant in 0..5)
        val cold = style == VillageArchitecture.Style.NORDIC
        val hot = style == VillageArchitecture.Style.OASIS
        fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, block: Short) {
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) put(x, y, z, block)
        }
        fun room(x0: Int, z0: Int, x1: Int, z1: Int, height: Int) {
            box(x0, 0, z0, x1, 0, z1, COBBLESTONE)
            box(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, if (hot) BRICK_TERRACOTTA else PLANK)
            box(x0, 1, z0, x1, height, z0, style.wall)
            box(x0, 1, z1, x1, height, z1, style.wall)
            box(x0, 1, z0, x0, height, z1, style.wall)
            box(x1, 1, z0, x1, height, z1, style.wall)
            for (x in listOf(x0, x1)) for (z in listOf(z0, z1))
                box(x, 1, z, x, height, z, style.frame)
            for (x in listOf(x0, x1)) box(x, 2, z0 + 2, x, 3, z0 + 3, GLASS)
            box(x0 + 2, 2, z1, x0 + 3, 3, z1, GLASS)
        }
        fun door(x: Int, z: Int, y: Int = 1) = box(x, y, z, x + 1, y + 2, z, AIR)
        fun roof(x0: Int, z0: Int, x1: Int, z1: Int, base: Int, acrossZ: Boolean = false) {
            if (hot) {
                box(x0, base, z0, x1, base, z1, style.roof)
                for (x in x0..x1) for (z in z0..z1)
                    if ((x == x0 || x == x1 || z == z0 || z == z1) && (x + z) % 2 == 0)
                        put(x, base + 1, z, style.frame)
                return
            }
            // Rotate the ridge on long buildings; fill both gables below the roof skin.
            for (x in x0 - 1..x1 + 1) for (z in z0 - 1..z1 + 1) {
                val rise = if (acrossZ) minOf(z - z0 + 1, z1 + 1 - z)
                    else minOf(x - x0 + 1, x1 + 1 - x)
                val y = base + rise
                val wall = x == x0 || x == x1 || z == z0 || z == z1
                if (wall && x in x0..x1 && z in z0..z1)
                    box(x, base, z, x, y, z, style.wall)
                put(x, y, z, style.roof)
                if (cold) put(x, y + 1, z, SNOW)
            }
        }
        fun hearth(x: Int, z: Int, top: Int) {
            val cap = if (hot) { if (top > 10) 12 else 7 } else top
            box(x - 1, 1, z, x + 1, 2, z + 1, BRICK_GREY)
            put(x, 1, z, FURNACE)
            box(x, 3, z + 1, x, cap - 1, z + 1, BRICK_GREY)
            box(x - 1, cap, z, x + 1, cap, z + 1, BRICK_GREY)
        }
        fun table(x: Int, z: Int) {
            put(x, 1, z, TABLE)
            put(x, 1, z + 2, PLANK)
            put(x, 2, z + 2, TORCH)
        }
        fun canopy(x0: Int, z0: Int, x1: Int, z1: Int) {
            for (x in listOf(x0, x1)) box(x, 1, z0, x, 3, z0, style.frame)
            box(x0, 4, z0, x1, 4, z1, if (hot) PLANK_JUNGLE else style.roof)
            if (cold) box(x0, 5, z0, x1, 5, z1, SNOW)
        }
        when (variant) {
            0 -> { // Compact cottage with an offset porch and a small front garden.
                room(1, 3, 8, 9, 4)
                door(4, 3)
                roof(1, 3, 8, 9, 5)
                canopy(3, 1, 7, 2)
                box(0, 0, 0, 2, 0, 2, DIRT)
                for (z in 0..2) put(0, 1, z, if (hot) CACTUS else TORCH)
                table(2, 5)
                hearth(6, 6, 10)
            }
            1 -> { // Long workshop: low transverse roof, open working porch and workbenches.
                room(0, 3, 9, 9, 4)
                door(4, 3)
                roof(0, 3, 9, 9, 5, acrossZ = true)
                canopy(0, 0, 9, 2)
                box(1, 1, 1, 2, 1, 1, PLANK)
                put(7, 1, 1, TABLE)
                box(1, 1, 6, 1, 1, 8, PLANK)
                put(1, 2, 7, TORCH)
                hearth(7, 6, 10)
            }
            2 -> { // L-shaped home wrapped around an open courtyard.
                room(0, 0, 4, 10, 4)
                room(4, 6, 9, 10, 4)
                // Shared wall opens into the rear wing; the courtyard entrance faces the avenue.
                box(4, 1, 7, 4, 3, 8, AIR)
                door(1, 0)
                door(6, 6)
                roof(0, 0, 4, 10, 5)
                roof(4, 6, 9, 10, 5, acrossZ = true)
                box(5, 0, 0, 9, 0, 5, BRICK_TERRACOTTA)
                box(8, 1, 1, 8, 1, 3, PLANK)
                put(8, 2, 2, TORCH)
                table(1, 4)
                hearth(7, 8, 10)
            }
            3, 5 -> { // A broad townhouse or a slender tower, with a usable upper floor.
                val right = if (variant == 3) 9 else 6
                room(0, 0, right, 10, 9)
                door(3, 0)
                box(1, 5, 1, right - 1, 5, 9, PLANK)
                box(1, 5, 1, 2, 5, 5, AIR)
                for (step in 1..5) box(1, 1, step, 2, step, step, PLANK)
                for (z in listOf(0, 10)) box(3, 7, z, 4, 8, z, GLASS)
                for (x in listOf(0, right)) box(x, 7, 7, x, 8, 8, GLASS)
                box(0, 5, 0, right, 5, 0, style.frame)
                if (variant == 3) {
                    roof(0, 0, right, 10, 10)
                    // Balcony above the entrance, reached directly from upstairs.
                    box(3, 5, -2, 7, 5, -1, PLANK)
                    box(3, 6, -2, 7, 6, -2, style.frame)
                    for (x in listOf(3, 7)) {
                        box(x, 1, -2, x, 4, -2, style.frame)
                        put(x, 6, -1, style.frame)
                    }
                    door(4, 0, 6)
                    hearth(7, 7, 16)
                } else {
                    // Hipped tower roof instead of another long gable.
                    for (x in -1..right + 1) for (z in -1..11) {
                        val y = 10 + minOf(x + 1, right + 1 - x, z + 1, 11 - z)
                        if ((x == 0 || x == right || z == 0 || z == 10) &&
                            x in 0..right && z in 0..10) box(x, 10, z, x, y, z, style.wall)
                        put(x, y, z, style.roof)
                        if (cold) put(x, y + 1, z, SNOW)
                    }
                    canopy(7, 4, 9, 8)
                    put(8, 1, 6, TABLE)
                }
                put(right - 1, 6, 8, TABLE)
                put(3, 6, 9, TORCH)
            }
            4 -> { // Shop set back behind a broad market counter and striped awning.
                room(0, 5, 9, 10, 4)
                door(4, 5)
                roof(0, 5, 9, 10, 5, acrossZ = true)
                canopy(0, 0, 9, 4)
                for (x in 0..9 step 2) box(x, 4, 0, x, 4, 4, style.frame)
                box(1, 1, 1, 3, 1, 1, PLANK)
                box(6, 1, 1, 8, 1, 1, PLANK)
                put(2, 2, 1, TABLE)
                put(7, 2, 1, TORCH)
                box(1, 1, 9, 3, 2, 9, PLANK)
                put(7, 1, 8, FURNACE)
            }
        }
    }
}
