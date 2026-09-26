package com.Atom2Universe.app.games.caves.world

/** Houses of the version 5 settlements: a finished shell, interior left empty.
 * Local frame: walls fill x 0 until width and z 0 until depth, the floor is y = 0, the main door
 * opens on the front (low z). Eaves and chimneys overhang by one block: x -1..width, z -1..depth.
 *
 * Roofs rise one block per block (never one per two: that leaves open steps). The roof of every
 * wing is merged into one height map, gables are walled up to it, and any step higher than one
 * block between neighbours is filled, so wings can cross without holes.
 */
internal object FrontierHouses {
    class Style(val foundation: Short, val floor: Short, val wall: Short, val stoneWall: Short,
                val frame: Short, val roof: Short, val flat: Boolean, val snow: Boolean)

    fun style(arid: Boolean, cold: Boolean) = when {
        arid -> Style(SANDSTONE, BRICK_TERRACOTTA, SANDSTONE, SANDSTONE, BRICK_TERRACOTTA, BRICK_SANDY, flat = true, snow = false)
        cold -> Style(STONE, PLANK_SAPIN, PLANK_SAPIN, COBBLESTONE, WOOD_DARK, PLANK_DARK, flat = false, snow = true)
        else -> Style(COBBLESTONE, PLANK, PLANK, BRICK_GREY, WOOD, BRICK_RED, flat = false, snow = false)
    }

    /** [doorX] is the left column of the two-wide main door, set in the wall z = [doorZ]. */
    class Model(val width: Int, val depth: Int, val doorX: Int, val doorZ: Int = 0)

    val models = listOf(
        Model(8, 7, 3),      // 0 cottage, gable facing the street, small porch
        Model(12, 7, 5),     // 1 longhouse, ridge along the street
        Model(12, 9, 2),     // 2 L-shaped house around a paved courtyard
        Model(8, 8, 3),      // 3 two-storey townhouse
        Model(6, 6, 2),      // 4 tower, hipped roof
        Model(10, 9, 4, 3))  // 5 house behind a covered porch

    private enum class Roof { RIDGE_X, RIDGE_Z, HIP }
    private class Wing(val x0: Int, val z0: Int, val x1: Int, val z1: Int, val height: Int, val roof: Roof) {
        fun contains(x: Int, z: Int) = x in x0..x1 && z in z0..z1
    }

    fun build(index: Int, s: Style, put: (Int, Int, Int, Short) -> Unit) {
        val m = models[index]
        val wings = when (index) {
            0 -> listOf(Wing(0, 0, 7, 6, 4, Roof.RIDGE_Z))
            1 -> listOf(Wing(0, 0, 11, 6, 4, Roof.RIDGE_X))
            2 -> listOf(Wing(0, 0, 7, 8, 4, Roof.RIDGE_Z), Wing(7, 4, 11, 8, 4, Roof.RIDGE_X))
            3 -> listOf(Wing(0, 0, 7, 7, 8, Roof.RIDGE_Z))
            4 -> listOf(Wing(0, 0, 5, 5, 10, Roof.HIP))
            else -> listOf(Wing(0, 3, 9, 8, 4, Roof.RIDGE_X))
        }
        val wall = if (index == 3 || index == 4) s.stoneWall else s.wall
        fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, id: Short) {
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) put(x, y, z, id)
        }
        fun inside(x: Int, z: Int) = wings.any { it.contains(x, z) }
        fun height(x: Int, z: Int) = wings.filter { it.contains(x, z) }.maxOf { it.height }
        // Eight neighbours: an inner corner of an L touches the outside only diagonally.
        fun perimeter(x: Int, z: Int) = (-1..1).any { dx -> (-1..1).any { dz -> !inside(x + dx, z + dz) } }
        fun corner(x: Int, z: Int): Boolean {
            val outX = !inside(x - 1, z) || !inside(x + 1, z)
            val outZ = !inside(x, z - 1) || !inside(x, z + 1)
            return (outX && outZ) || (!outX && !outZ && perimeter(x, z))
        }

        // Roof height map, eaves included; -1 where there is no roof.
        val gx = m.width + 4; val gz = m.depth + 4
        val roof = IntArray(gx * gz) { -1 }
        fun roofAt(x: Int, z: Int) = if (x + 2 in 0 until gx && z + 2 in 0 until gz) roof[x + 2 + (z + 2) * gx] else -1
        if (!s.flat) for (w in wings) for (x in w.x0 - 1..w.x1 + 1) for (z in w.z0 - 1..w.z1 + 1) {
            val alongZ = minOf(z - w.z0 + 1, w.z1 + 1 - z)
            val alongX = minOf(x - w.x0 + 1, w.x1 + 1 - x)
            val rise = when (w.roof) { Roof.RIDGE_X -> alongZ; Roof.RIDGE_Z -> alongX; Roof.HIP -> minOf(alongX, alongZ) }
            val i = x + 2 + (z + 2) * gx
            roof[i] = maxOf(roof[i], w.height + 1 + rise)
        }

        for (x in 0 until m.width) for (z in 0 until m.depth) {
            if (!inside(x, z)) continue
            val edge = perimeter(x, z)
            put(x, 0, z, if (edge) s.foundation else s.floor)
            if (!edge) continue
            val h = height(x, z)
            val top = if (s.flat) h else roofAt(x, z) - 1
            val pillar = corner(x, z)
            for (y in 1..top) put(x, y, z, if (pillar || (!s.flat && y == h)) s.frame else wall)
        }

        fun window(x0: Int, z0: Int, x1: Int, z1: Int, y0: Int = 2, y1: Int = 3) = box(x0, y0, z0, x1, y1, z1, GLASS)
        // Gable windows sit above the wall top: a flat roof has no gable to hold them.
        fun gable(x0: Int, z0: Int, x1: Int, z1: Int, y0: Int, y1: Int = y0) { if (!s.flat) window(x0, z0, x1, z1, y0, y1) }
        fun door(x: Int, z: Int) = box(x, 1, z, x + 1, 3, z, AIR)
        var chimney: Pair<Int, Int>? = null
        when (index) {
            0 -> {
                window(1, 0, 1, 0); window(6, 0, 6, 0); gable(3, 0, 4, 0, 6)
                window(0, 2, 0, 4); window(7, 2, 7, 4); window(3, 6, 4, 6)
                // Porch roof over the door.
                box(2, 1, -1, 2, 3, -1, s.frame); box(5, 1, -1, 5, 3, -1, s.frame)
                box(2, 4, -1, 5, 4, -1, if (s.flat) s.frame else s.roof)
                if (s.snow) box(2, 5, -1, 5, 5, -1, SNOW)
            }
            1 -> {
                for (z in listOf(0, 6)) { window(2, z, 3, z); window(8, z, 9, z) }
                for (x in listOf(0, 11)) { window(x, 3, x, 3); gable(x, 3, x, 3, 6) }
                chimney = -1 to 3
            }
            2 -> {
                window(5, 0, 6, 0); window(0, 2, 0, 3); window(0, 6, 0, 7); window(2, 8, 3, 8)
                window(7, 1, 7, 2); window(9, 8, 10, 8); window(11, 6, 11, 6); gable(11, 6, 11, 6, 6)
                door(9, 4)
                box(8, 0, 0, 11, 0, 3, s.foundation)
                chimney = 12 to 6
            }
            3 -> {
                window(1, 0, 1, 0); window(6, 0, 6, 0); window(3, 7, 4, 7)
                for (z in listOf(0, 7)) { window(1, z, 2, z, 6, 7); window(5, z, 6, z, 6, 7) }
                for (x in listOf(0, 7)) { window(x, 3, x, 4); window(x, 3, x, 4, 6, 7) }
                gable(3, 0, 4, 0, 10, 11)
                // Floor band between the two storeys.
                for (x in 0..7) for (z in 0..7) if (perimeter(x, z)) put(x, 5, z, s.frame)
            }
            4 -> {
                window(2, 5, 3, 5); window(2, 0, 3, 0, 6, 7); window(2, 5, 3, 5, 6, 7)
                for (x in listOf(0, 5)) { window(x, 2, x, 3); window(x, 2, x, 3, 7, 8) }
            }
            else -> {
                window(1, 3, 2, 3); window(7, 3, 8, 3); window(2, 8, 3, 8); window(6, 8, 7, 8)
                for (x in listOf(0, 9)) { window(x, 5, x, 6); gable(x, 5, x, 6, 6) }
                // Covered porch along the whole front.
                box(0, 0, 0, 9, 0, 2, s.floor)
                for (x in listOf(0, 3, 6, 9)) box(x, 1, 0, x, 3, 0, s.frame)
                box(0, 4, 0, 9, 4, 0, s.frame)
                box(0, 4, 1, 9, 4, 2, if (s.flat) s.frame else s.roof)
                if (s.snow) box(0, 5, 0, 9, 5, 2, SNOW)
                chimney = 10 to 6
            }
        }
        door(m.doorX, m.doorZ)

        if (s.flat) {
            // Roof terrace behind a crenellated parapet.
            for (x in 0 until m.width) for (z in 0 until m.depth) {
                if (!inside(x, z)) continue
                val h = height(x, z)
                put(x, h + 1, z, s.roof)
                if (perimeter(x, z) && ((x + z) % 2 == 0 || corner(x, z))) put(x, h + 2, z, s.frame)
            }
            return
        }
        for (x in -1..m.width) for (z in -1..m.depth) {
            val y = roofAt(x, z)
            if (y < 0) continue
            // Close any step of more than one block towards a lower neighbour.
            val low = listOf(roofAt(x - 1, z), roofAt(x + 1, z), roofAt(x, z - 1), roofAt(x, z + 1))
                .filter { it >= 0 }.minOrNull() ?: y
            for (fill in low + 1 until y) put(x, fill, z, s.roof)
            put(x, y, z, s.roof)
            if (s.snow) put(x, y + 1, z, SNOW)
        }
        chimney?.let { (x, z) ->
            val peak = (0 until m.width).maxOf { cx -> (0 until m.depth).maxOf { cz -> roofAt(cx, cz) } }
            box(x, 1, z, x, peak + 2, z, s.foundation)
        }
    }
}
