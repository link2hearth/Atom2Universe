package com.Atom2Universe.app.games.caves.world

import kotlin.math.abs
import kotlin.random.Random

/** Larger silhouettes use connected wooden branches so their leaves obey normal decay. */
internal object GrandTrees {
    val types = setOf("giant_redwood", "giant_pine", "broad_oak", "baobab", "acacia", "willow")

    fun generate(type: String, rng: Random, put: (Int, Int, Int, Short, Boolean) -> Unit) {
        val conifer = type == "giant_redwood" || type == "giant_pine"
        val wood = when (type) {
            "giant_redwood" -> WOOD_RED
            "giant_pine" -> WOOD_SAPIN
            "willow" -> WOOD_DARK
            else -> WOOD
        }
        val leaf = if (conifer) LEAVES_SAPIN else if (type == "acacia" || type == "baobab") LEAVES_FALL else LEAVES
        fun log(x: Int, y: Int, z: Int) = put(x, y, z, wood, false)
        fun foliage(x: Int, y: Int, z: Int) = put(x, y, z, leaf, true)
        fun branch(x: Int, y: Int, z: Int) {
            log(x, y, z)
            foliage(x - 1, y, z); foliage(x + 1, y, z)
            foliage(x, y - 1, z); foliage(x, y + 1, z)
            foliage(x, y, z - 1); foliage(x, y, z + 1)
        }
        fun crown(x: Int, y: Int, z: Int, radius: Int = 2, depth: Int = 1) {
            for (dy in -depth..depth) for (dz in -radius..radius) for (dx in -radius..radius)
                if (dx * dx + dz * dz <= radius * radius + 1 - abs(dy)) foliage(x + dx, y + dy, z + dz)
        }
        if (conifer) {
            val h = if (type == "giant_redwood") 20 + rng.nextInt(6) else 15 + rng.nextInt(6)
            for (y in 1..h) for (z in 0..1) for (x in 0..1) log(x, y, z)
            for (y in h / 3..h step 2) {
                val r = ((h - y) / 5 + 1).coerceIn(1, 3)
                for (z in -r..r + 1) for (x in -r..r + 1)
                    if (abs(x * 2 - 1) + abs(z * 2 - 1) <= r * 2 + 2) {
                        foliage(x, y, z); foliage(x, y + 1, z)
                    }
            }
            crown(0, h + 1, 0, 1)
            return
        }
        val h = when (type) {
            "baobab" -> 7 + rng.nextInt(3)
            "broad_oak" -> 4 + rng.nextInt(2)
            else -> 6 + rng.nextInt(3)
        }
        if (type == "baobab") {
            // 5x5 shell, a 3x3 room and a two-block-high doorway facing the path.
            // Never carve AIR: the hollow recipe cannot erase terrain or neighboring features.
            for (y in 1..h) for (z in -2..2) for (x in -2..2) {
                if (abs(x) != 2 && abs(z) != 2) continue
                if (z == -2 && x == 0 && y <= 2) continue
                log(x, y, z)
            }
        } else {
            val width = if (type == "acacia") 0 else 1
            for (y in 1..h) for (z in 0..width) for (x in 0..width) log(x, y, z)
        }
        val tips = ArrayList<Triple<Int, Int, Int>>()
        val reach = if (type == "broad_oak" || type == "acacia") 4 else 3
        for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val base = h - 1
            for (step in 1..reach) {
                // Two vertical cells at each step keep the ascending limbs face-connected.
                val y = base + step / 3
                branch(dx * step, y, dz * step)
                branch(dx * step, y + 1, dz * step)
            }
            tips.add(Triple(dx * reach, base + reach / 3 + 1, dz * reach))
        }
        // Bridge the hollow baobab above head height to connect each limb to the shell.
        if (type == "baobab") for (s in -2..2) { log(s, h - 1, 0); log(0, h - 1, s) }
        crown(0, h + 1, 0, 2, 1)
        for ((x, y, z) in tips) {
            crown(x, y, z, 2, if (type == "willow") 2 else 1)
            if (type == "willow") {
                for ((dx, dz) in listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2)) {
                    val length = 2 + rng.nextInt(2)
                    for (drop in 1..length) foliage(x + dx, y - drop, z + dz)
                }
            }
        }
    }
}
