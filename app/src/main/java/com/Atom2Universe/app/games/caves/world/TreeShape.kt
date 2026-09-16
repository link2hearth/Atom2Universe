package com.Atom2Universe.app.games.caves.world

import kotlin.math.abs
import kotlin.random.Random

/** Coordinate-independent recipe: replay the whole tree for each intersecting chunk. */
internal object TreeShape {
    const val REACH = 7
    const val HEIGHT = 28
    const val BLOSSOM: Short = 1032
    const val APPLE_LEAF: Short = 1033
    const val PEAR_LEAF: Short = 1034
    const val CHERRY_LEAF: Short = 1035

    fun generate(type: String, rng: Random, rawPut: (Int, Int, Int, Short, Boolean) -> Unit) {
        // A crown centred low on a short tree dips under the trunk base: y = 0 is the ground the
        // tree stands on, so those blocks are dropped instead of overwriting the terrain. Clipping
        // here rather than inside foliage() keeps the rng draws — and so the rest of the tree —
        // identical.
        val put = { x: Int, y: Int, z: Int, id: Short, onlyAir: Boolean ->
            if (y >= 1) rawPut(x, y, z, id, onlyAir)
        }
        if (type in GrandTrees.types) {
            GrandTrees.generate(type, rng, put)
            return
        }
        val fruit = when {
            type == "apple" -> APPLE_LEAF
            type == "pear" -> PEAR_LEAF
            type == "pink" -> CHERRY_LEAF
            type in listOf("oak", "normal", "default") && rng.nextInt(4) == 0 ->
                if (rng.nextBoolean()) APPLE_LEAF else PEAR_LEAF
            else -> 0.toShort()
        }
        val pine = type in listOf("sapin", "redwood")
        val birch = type == "birch"
        val jungle = type == "jungle"
        val h = when {
            fruit != 0.toShort() -> 4 + rng.nextInt(3)
            pine -> 8 + rng.nextInt(6)
            jungle -> 9 + rng.nextInt(5)
            birch -> 6 + rng.nextInt(4)
            else -> 4 + rng.nextInt(6)
        }
        val wood = when (type) {
            "birch" -> WOOD_WHITE
            "sapin" -> WOOD_SAPIN
            "jungle", "jungle_small" -> WOOD_JUNGLE
            "redwood" -> WOOD_RED
            "darkwood", "pink", "purple", "blue" -> WOOD_DARK
            else -> WOOD
        }
        val leaf = when (type) {
            "sapin", "redwood" -> LEAVES_SAPIN
            "darkwood" -> LEAVES_DARK
            "jungle", "jungle_small" -> LEAVES_JUNGLE
            "purple" -> LEAVES_PURPLE
            "blue" -> LEAVES_BLUE
            "yellow" -> LEAVES_YELLOW
            else -> LEAVES
        }
        fun foliage(x: Int, y: Int, z: Int) {
            val roll = rng.nextFloat()
            val block = when {
                fruit != 0.toShort() && roll < .11f -> fruit
                fruit != 0.toShort() && roll < .18f -> BLOSSOM
                !pine && roll < .015f -> BLOSSOM
                else -> leaf
            }
            put(x, y, z, block, true)
        }
        fun crown(cx: Int, cy: Int, cz: Int, radius: Int, vertical: Int) {
            for (dy in -vertical..vertical) for (dz in -radius..radius) for (dx in -radius..radius) {
                val d = (dx * dx + dz * dz).toDouble() / (radius * radius + .5) +
                    dy * dy.toDouble() / (vertical * vertical + .5)
                if (d <= 1.0 && !(d > .83 && rng.nextInt(5) == 0)) foliage(cx + dx, cy + dy, cz + dz)
            }
        }
        for (y in 1..h) put(0, y, 0, wood, false)
        if (pine) {
            for (y in 3..h + 1) {
                val r = if (y >= h) 1 else ((h - y) / 3 + if (y % 2 == 0) 1 else 0).coerceIn(1, 3)
                for (z in -r..r) for (x in -r..r)
                    if (abs(x) + abs(z) <= r + 1 && (x != 0 || z != 0)) foliage(x, y, z)
            }
            foliage(0, h + 1, 0); foliage(0, h + 2, 0)
        } else if (birch) {
            crown(0, h - 1, 0, 2, 3)
        } else {
            // Connected stepped branches support separate lobes, breaking the spherical silhouette.
            val reach = if (h >= 8) 2 else 1
            val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).shuffled(rng)
            val tips = directions.take(3).map { (dx, dz) ->
                val branchY = h - 2 - rng.nextInt(2)
                for (step in 1..reach) {
                    put(dx * step, branchY, dz * step, wood, false)
                    put(dx * step, branchY + 1, dz * step, wood, false)
                }
                Triple(dx * reach, branchY + 1, dz * reach)
            }
            crown(0, h, 0, if (jungle) 3 else 2, 2)
            for ((x, y, z) in tips) crown(x, y, z, 2, if (jungle) 1 else 2)
        }
    }
}
