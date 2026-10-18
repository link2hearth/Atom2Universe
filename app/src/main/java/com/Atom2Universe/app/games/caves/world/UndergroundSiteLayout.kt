package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Cell
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Kind
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Plan
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Point
import kotlin.math.abs
import kotlin.random.Random

/** Two connected storeys within the site's surveyed horizontal footprint. */
internal object UndergroundSiteLayout {
    private data class Link(val a: Int, val b: Int)

    private fun neighbours(i: Int): List<Int> = buildList {
        if (i % 3 > 0) add(i - 1)
        if (i % 3 < 2) add(i + 1)
        if (i >= 3) add(i - 3)
        if (i < 6) add(i + 3)
    }

    private fun distance(links: List<Link>, start: Int, end: Int): Int {
        val depths = IntArray(9) { -1 }
        val queue = ArrayDeque<Int>()
        queue.add(start); depths[start] = 0
        while (queue.isNotEmpty()) {
            val here = queue.removeFirst()
            if (here == end) return depths[here]
            for (link in links) {
                val next = when (here) { link.a -> link.b; link.b -> link.a; else -> continue }
                if (depths[next] < 0) { depths[next] = depths[here] + 1; queue.add(next) }
            }
        }
        return -1
    }

    private fun maze(kind: Kind, rng: Random): List<Link> {
        // The vestibule opens left OR right. There is no entrance-to-boss firing corridor.
        val first = if (rng.nextBoolean()) 0 else 2
        var best = emptyList<Link>()
        repeat(24) {
            val visited = mutableSetOf(1)
            val links = ArrayList<Link>()
            fun visit(here: Int) {
                visited.add(here)
                for (next in neighbours(here).shuffled(rng)) {
                    if (next !in visited) { links += Link(here, next); visit(next) }
                }
            }
            links += Link(1, first)
            visit(first)
            if (distance(links, 1, 7) > distance(best, 1, 7)) best = links
        }
        val result = best.toMutableList()
        val loopCount = when (kind) {
            Kind.ABANDONED_MINE, Kind.DWARVEN_MINE, Kind.SLIME_CISTERN -> 2
            Kind.MUMMY_TOMB, Kind.WRAITH_ARCHIVE -> 0
            else -> 1
        }
        val candidates = (0..8).flatMap { a -> neighbours(a).filter { it > a }.map { Link(a, it) } }
            .filter { it.a != 1 && it.b != 1 }.shuffled(rng)
        var added = 0
        for (link in candidates) {
            if (added >= loopCount) break
            if (result.any { setOf(it.a, it.b) == setOf(link.a, link.b) }) continue
            val trial = result + link
            // Keep a detour and at least one side dead end, even in the loop-rich mines.
            val hasBranch = (0..8).any { i -> i != 1 && i != 7 && trial.count { it.a == i || it.b == i } == 1 }
            if (distance(trial, 1, 7) >= 4 && hasBranch) { result += link; added++ }
        }
        return result
    }

    fun build(kind: Kind, salt: Long, stage: Int): Plan {
        val rng = Random(salt xor (kind.ordinal.toLong() * 11939L))
        val palette = UndergroundSiteDressing.palette(kind)
        val blocks = linkedMapOf<Point, Cell>()
        fun put(x: Int, y: Int, z: Int, id: Short, meta: Int = 0) {
            blocks[Point(x, y, z)] = Cell(id, meta.toByte())
        }
        fun box(x0: Int, x1: Int, y0: Int, y1: Int, z0: Int, z1: Int, id: Short) {
            for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) put(x, y, z, id)
        }
        val rooms = (0..8).map { i ->
            val height = when (kind) {
                Kind.OGRE_DEN, Kind.TROLL_GROVE, Kind.CRYSTAL_SANCTUM -> 7 + (i % 2)
                Kind.ABANDONED_MINE, Kind.FUNGAL_MINE -> if (i % 3 == 0) 6 else 5
                else -> 5 + rng.nextInt(3)
            }
            UndergroundSiteDressing.Room((i % 3 - 1) * 16, 5 + (i / 3) * 12, i,
                if (i == 5) maxOf(7, height) else height)
        }
        val stairRoom = if (rng.nextBoolean()) 0 else 2
        val stairRooms = setOf(stairRoom, 4)
        val lowerLinks = maze(kind, rng).toMutableList()
        val lowerIndices = (0..8).toMutableSet()
        // Trim only leaves: each smaller basement remains connected to the stairwell.
        repeat(rng.nextInt(4)) {
            val leaves = lowerIndices.filter { i -> i !in stairRooms && lowerLinks.count { it.a == i || it.b == i } == 1 }
            if (leaves.isNotEmpty()) {
                val removed = leaves.random(rng)
                lowerIndices.remove(removed)
                lowerLinks.removeAll { it.a == removed || it.b == removed }
            }
        }
        val lowerRooms = lowerIndices.sorted().map { i -> rooms[i].copy(index = 10 + i,
            height = 6, floorY = -10, stairwell = i in stairRooms) }
        val furnishedRooms = rooms.map { it.copy(stairwell = it.index in stairRooms) } + lowerRooms
        fun shell(x0: Int, x1: Int, z0: Int, z1: Int, height: Int, floorY: Int = 0) {
            box(x0, x1, floorY - 1, floorY, z0, z1, palette.floor)
            box(x0, x1, floorY + 1, floorY + height, z0, z1, AIR)
            box(x0, x1, floorY + height + 1, floorY + height + 1, z0, z1, palette.wall)
            box(x0, x0, floorY + 1, floorY + height, z0, z1, palette.wall)
            box(x1, x1, floorY + 1, floorY + height, z0, z1, palette.wall)
            box(x0, x1, floorY + 1, floorY + height, z0, z0, palette.wall)
            box(x0, x1, floorY + 1, floorY + height, z1, z1, palette.wall)
        }
        for (room in furnishedRooms) shell(room.x - 7, room.x + 7, room.z - 5, room.z + 5, room.height, room.floorY)
        shell(-8, 8, 34, 45, 10)

        // Only socket-to-socket tunnels are cut; disconnected neighbours retain solid walls.
        fun passage(x0: Int, z0: Int, x1: Int, z1: Int, floorY: Int = 0) {
            val alongX = x0 != x1
            val from = if (alongX) minOf(x0, x1) else minOf(z0, z1)
            val to = if (alongX) maxOf(x0, x1) else maxOf(z0, z1)
            for (t in from..to) for (u in -2..2) {
                val x = if (alongX) t else x0 + u
                val z = if (alongX) z0 + u else t
                put(x, floorY - 1, z, palette.floor); put(x, floorY, z, palette.floor)
                for (y in 1..4) put(x, floorY + y, z, if (abs(u) == 2) palette.trim else AIR)
                val beam = palette.trim == UndergroundSites.BEAM || palette.trim == UndergroundSites.BEAM_OAK
                put(x, floorY + 5, z, palette.trim, if (beam) { if (alongX) 2 else 1 } else 0)
                // Upside-down steps round the door shoulders above four blocks of clearance.
                if (abs(u) == 1) put(x, floorY + 5, z, palette.stairs,
                    4 or if (alongX) { if (u < 0) 2 else 0 } else { if (u < 0) 3 else 1 })
            }
        }
        val links = maze(kind, rng)
        fun connect(edges: List<Link>, floorY: Int) {
            for ((a, b) in edges) {
                val r = rooms[a]; val s = rooms[b]
                if (r.x == s.x) {
                    val sign = if (s.z > r.z) 1 else -1
                    passage(r.x, r.z + sign * 5, s.x, s.z - sign * 5, floorY)
                } else {
                    val sign = if (s.x > r.x) 1 else -1
                    passage(r.x + sign * 7, r.z, s.x - sign * 7, s.z, floorY)
                }
            }
        }
        connect(links, 0)
        connect(lowerLinks, -10)
        passage(0, -4, 0, 0)
        passage(0, 34, 0, 35)
        UndergroundSiteDressing.dress(kind, salt, stage, blocks,
            furnishedRooms + UndergroundSiteDressing.Room(0, 39, 9, 10))

        // Two broad flights around a landing, with an upper bridge crossing above them.
        // Each half-step is a real stair collision shape, not a decorative upside-down block.
        for (stair in rooms.filter { it.index in stairRooms }) {
            fun stairPut(x: Int, y: Int, z: Int, id: Short, meta: Int = 0) =
                put(stair.x + x, y, stair.z + z, id, meta)
            // Open the inter-storey ceiling inside the chamber, keeping its outer retaining walls.
            for (x in -6..6) for (z in -4..4) for (y in -9..-1) stairPut(x, y, z, AIR)
            for (x in -5..-3) for (z in -3..3) stairPut(x, 0, z, AIR)
            for (x in 3..5) for (z in -3..3) stairPut(x, 0, z, AIR)
            for (x in -5..5) for (z in 2..3) stairPut(x, -5, z, palette.floor)
            for (step in 0..4) {
                for (x in -5..-3) {
                    for (y in -9 until -step) stairPut(x, y, -3 + step, palette.wall)
                    stairPut(x, -step, -3 + step, palette.stairs, 2)
                }
                for (x in 3..5) {
                    for (y in -9 until -5 - step) stairPut(x, y, 1 - step, palette.wall)
                    stairPut(x, -5 - step, 1 - step, palette.stairs, 0)
                }
            }
            // The main upper crossing has a continuous three-block-wide bridge over the flights.
            for (x in -6..6) for (z in 1..3) stairPut(x, 0, z, palette.floor)
            // Keep the approach overhead clear even during movement between adjacent half-steps.
            for (x in listOf(-6, 6)) for (z in -3..-2) stairPut(x, 1, z, palette.slab)
            for (y in -9..4) for (x in listOf(-6, 6)) stairPut(x, y, 4, palette.trim)
            for (y in listOf(-8, -3, 2)) for (x in listOf(-6, 6)) stairPut(x, y, 3, F.LAMP)
        }

        // Stable loot anchors preserve existing per-coordinate opened-cache persistence.
        val caches = when (kind) {
            Kind.ABANDONED_MINE, Kind.FUNGAL_MINE, Kind.EMBER_FOUNDRY, Kind.DWARVEN_MINE ->
                listOf(-21 to 21, 5 to 40)
            Kind.ZOMBIE_TEMPLE, Kind.MUMMY_TOMB, Kind.WRAITH_ARCHIVE -> listOf(-5 to 40, 20 to 25)
            Kind.GOBLIN_LABORATORY -> listOf(-5 to 39, 20 to 15)
            Kind.CRYSTAL_SANCTUM -> listOf(-4 to 40, -19 to 25)
            else -> listOf(-21 to 25, 6 to 42)
        }
        for ((x, z) in caches) {
            // A dry approach connects the reward to the room's reserved cross-shaped aisle.
            val room = if (z >= 36) UndergroundSiteDressing.Room(0, 39, 9, 10)
                else rooms.minBy { abs(it.x - x) + abs(it.z - z) }
            for (az in minOf(z, room.z)..maxOf(z, room.z)) {
                put(x, 0, az, palette.floor)
                for (y in 1..3) put(x, y, az, AIR)
            }
            val facing = if (z > room.z) 0 else 1
            put(x, 1, z, F.CACHE, facing)
        }
        box(-3, 3, 1, 9, 36, 42, AIR)
        for (x in listOf(-7, 7)) for (y in 1..2) put(x, y, 43, M.id(stage, M.Form.ORE))
        // One safe encounter point per room, clear for both the player and the site's enemies.
        val pads = furnishedRooms.filter { it.index != 1 }.map { Point(it.x, it.floorY + 1, it.z) }
        return Plan(blocks, pads, Point(0, 1, 39))
    }
}
