package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import kotlin.random.Random

/** Small, dry archaeological sites connected to existing caves. No new global cave carving. */
internal class UndergroundSites(private val seed: Long, private val terrain: NaturalTerrain) {
    enum class Kind(val mob: String, val population: Int, val firstStage: Int) {
        ABANDONED_MINE("skeleton", 5, 0),
        FUNGAL_MINE("spider", 4, 0),
        EMBER_FOUNDRY("imp", 4, 3),
        ZOMBIE_TEMPLE("zombie", 5, 1),
        GOBLIN_LABORATORY("goblin", 5, 1),
        CRYSTAL_SANCTUM("golem", 3, 3),
        DWARVEN_MINE("dwarf", 4, 2),
        OGRE_DEN("ogre", 3, 2),
        MUMMY_TOMB("mummy", 4, 2),
        TROLL_GROVE("troll", 3, 3),
        WRAITH_ARCHIVE("wraith", 3, 4),
        SLIME_CISTERN("slime", 5, 0)
    }

    data class Point(val x: Int, val y: Int, val z: Int)
    private data class Key(val x: Int, val y: Int, val z: Int)
    data class Cell(val id: Short, val meta: Byte = 0)
    data class Plan(val blocks: Map<Point, Cell>, val spawnPoints: List<Point>, val boss: Point)

    class Site(val kind: Kind, val entrance: Point, val turn: Int, val salt: Long,
               val spawnPoints: List<Point>, val cachePositions: Set<Point>, val bossPoint: Point,
               private val chunks: Map<Point, IntArray>, private val habitat: Map<Point, LongArray>) {
        val id = "${entrance.x}:${entrance.y}:${entrance.z}"
        val stage = MineralProgression.stage(entrance.y.toDouble())
        val level = stage + 1
        // Geometry and encounters keep varying below the final equipment tier as well.
        val depthBand = Math.floorDiv(-entrance.y.toLong(), MineralProgression.LAYER_HEIGHT.toLong()).coerceAtLeast(0)
        fun contains(x: Int, y: Int, z: Int): Boolean {
            val mask = habitat[Point(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16))] ?: return false
            val i = Math.floorMod(x,16) + Math.floorMod(y,16)*16 + Math.floorMod(z,16)*256
            return mask[i ushr 6] and (1L shl (i and 63)) != 0L
        }
        fun apply(chunk: Chunk) {
            val cells = chunks[Point(chunk.cx, chunk.cy, chunk.cz)] ?: return
            for (packed in cells) {
                val index = packed and 4095
                chunk.blocks[index] = (packed ushr 16).toShort()
                chunk.meta[index] = ((packed ushr 12) and 15).toByte()
            }
            chunk.undergroundSite = this
        }
    }

    // Cache misses are serialized; completed sites are immutable and can be shared by chunks.
    // Both misses and hits are cached, and only generation threads perform terrain surveys.
    private val sites = object : LinkedHashMap<Key, Site?>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Site?>?) = size > 64
    }
    @Synchronized private fun site(key: Key): Site? {
        if (sites.containsKey(key)) return sites[key]
        return survey(key).also { sites[key] = it }
    }

    fun decorate(chunk: Chunk) {
        val key = Key(Math.floorDiv(chunk.worldX, CELL), Math.floorDiv(chunk.worldY, HEIGHT), Math.floorDiv(chunk.worldZ, CELL))
        site(key)?.apply(chunk)
    }

    private fun survey(key: Key): Site? {
        val salt = seed xor (key.x.toLong() * 341873128712L) xor
            (key.y.toLong() * 42317861L) xor (key.z.toLong() * 132897987541L) xor 792137L
        val rng = Random(salt)
        if (rng.nextFloat() > .58f) return null
        // Plusieurs colonnes et orientations : une paroi mal orientée ne vide plus toute la cellule.
        repeat(6) {
            val firstTurn = rng.nextInt(4)
            val x = key.x * CELL + 48 + rng.nextInt(32)
            val z = key.z * CELL + 48 + rng.nextInt(32)
            findEntrance(key, x, z, firstTurn, salt, rng)?.let { return it }
        }
        return null
    }

    private fun findEntrance(key: Key, x: Int, z: Int, firstTurn: Int, salt: Long, rng: Random): Site? {
        // The full rotated blueprint fits inside this cell, including its entrance apron.
        val h = terrain.height(x.toDouble(), z.toDouble()).toInt()
        val water = terrain.waterLevelAt(x.toDouble(), z.toDouble())
        val top = minOf(key.y * HEIGHT + 80, h - 20)
        val bottom = key.y * HEIGHT + 16
        if (top < bottom) return null
        // Search for a real dry cave floor; a site can never be buried without an entrance.
        for (y in top downTo bottom) {
            if (terrain.isFlooded(x, y + 1, z, h, water) ||
                !terrain.caveAt(x, y + 1, z) || !terrain.caveAt(x, y + 2, z) || terrain.caveAt(x, y, z)) continue
            for (orientation in 0..3) {
                val turn = (firstTurn + orientation) % 4
                val approach = rotate(0, -4, turn)
                val ax = x + approach.first; val az = z + approach.second
                if (terrain.groundAt(ax, y + 1, az) != AIR || terrain.groundAt(ax, y + 2, az) != AIR ||
                    terrain.groundAt(ax, y, az) == WATER) continue
                if (terrain.groundAt(ax, y, az) == AIR && terrain.groundAt(ax, y - 1, az) in shortArrayOf(AIR, WATER)) continue
                // Reject shallow roofs and aquifers over the footprint before digging any room.
                var fits = true
                footprint@ for (dx in -24..24 step 8) for (dz in -8..44 step 4) {
                    val r = rotate(dx, dz, turn)
                    val wx = x + r.first; val wz = z + r.second
                    val roof = terrain.height(wx.toDouble(), wz.toDouble()).toInt()
                    if (roof - y < 16 || terrain.isFlooded(wx, y - 11, wz, roof,
                            terrain.waterLevelAt(wx.toDouble(), wz.toDouble()))) {
                        fits = false
                        break@footprint
                    }
                }
                if (!fits) continue
                val stage = MineralProgression.stage(y.toDouble())
                val choices = Kind.entries.filter { stage >= it.firstStage }
                return materialize(choices[rng.nextInt(choices.size)], Point(x, y, z), turn, salt)
            }
        }
        return null
    }

    private fun materialize(kind: Kind, anchor: Point, turn: Int, salt: Long): Site {
        val plan = blueprint(kind, salt, MineralProgression.stage(anchor.y.toDouble()))
        fun world(p: Point): Point {
            val r = rotate(p.x, p.z, turn)
            return Point(anchor.x + r.first, anchor.y + p.y, anchor.z + r.second)
        }
        val chunks = HashMap<Point, MutableList<Int>>()
        val habitat = HashMap<Point, LongArray>()
        val caches = HashSet<Point>()
        for ((local, value) in plan.blocks) {
            val p = world(local)
            val key = Point(Math.floorDiv(p.x, 16), Math.floorDiv(p.y, 16), Math.floorDiv(p.z, 16))
            val index = Math.floorMod(p.x, 16) + Math.floorMod(p.y, 16) * 16 + Math.floorMod(p.z, 16) * 256
            val id = if (turn % 2 != 0) when (value.id) {
                RAIL -> RAIL_CROSSWISE
                RAIL_CROSSWISE -> RAIL
                else -> value.id
            } else value.id
            val meta = rotatedMeta(id, value.meta, turn).toInt()
            val cells = chunks.getOrPut(key) { ArrayList() }
            cells.add(index or ((meta and 15) shl 12) or ((id.toInt() and 65535) shl 16))
            if (value.id == F.CACHE) caches += p
            if (local.y in -9..10 && (id == AIR || id == COBWEB || id == RAIL || id == RAIL_CROSSWISE || id in 2620.toShort()..2626.toShort())) {
                val mask = habitat.getOrPut(key) { LongArray(64) }
                mask[index ushr 6] = mask[index ushr 6] or (1L shl (index and 63))
            }
        }
        return Site(kind, anchor, turn, salt, plan.spawnPoints.map(::world), caches, world(plan.boss),
            chunks.mapValues { (_, v) -> v.toIntArray() }, habitat)
    }

    companion object {
        private const val CELL = 128
        private const val HEIGHT = 96
        const val RAIL: Short = 2660
        const val BEAM: Short = 2661
        const val TOMB_BRICKS: Short = 2662
        const val ALTAR: Short = 2663
        const val BOOKSHELF: Short = 2664
        const val VAT: Short = 2665
        const val CRATE: Short = 2666
        const val COBWEB: Short = 2668
        const val BASALT_BRICKS: Short = 2669
        const val RAIL_CROSSWISE: Short = 2670
        const val BEAM_OAK: Short = 2671
        const val CRATE_OAK: Short = 2672
        const val CRATE_FIR: Short = 2673
        const val TOMB_SMOOTH: Short = 2674
        const val TOMB_WEATHERED: Short = 2675
        const val SKULL_ALTAR: Short = 2676
        const val RITUAL_ALTAR: Short = 2677
        const val BASALT_SMOOTH: Short = 2678
        const val BASALT_CRACKED: Short = 2679
        const val VAT_LOW: Short = 2680
        const val VAT_HALF: Short = 2681
        const val VAT_FULL: Short = 2682
        const val BOOKSHELF_FIR: Short = 2683
        const val BARREL_OAK: Short = 2684
        const val BARREL_FIR: Short = 2685

        private fun rotate(x: Int, z: Int, turn: Int): Pair<Int, Int> = when (turn) {
            1 -> -z to x; 2 -> -x to -z; 3 -> z to -x; else -> x to z
        }

        /** Generation also runs without a loaded GL registry. Keep the authored orientation list here. */
        internal fun rotatedMeta(id: Short, meta: Byte, turn: Int): Byte {
            val raw = meta.toInt() and 15
            // Stair directions ascend +Z, +X, -Z, -X, opposite the plan's quarter turns.
            if (id in 2400.toShort()..2411.toShort())
                return ((raw and 12) or ((raw - turn) and 3)).toByte()
            val facing = id == BOOKSHELF || id == BOOKSHELF_FIR || id == F.CACHE || id == E.FORGE || id == FURNACE
            if (facing) {
                val direction = when (raw and 3) { 0 -> 0 to -1; 1 -> 0 to 1; 2 -> 1 to 0; else -> -1 to 0 }
                val rotated = rotate(direction.first, direction.second, turn)
                val face = when { rotated.second < 0 -> 0; rotated.second > 0 -> 1; rotated.first > 0 -> 2; else -> 3 }
                return ((raw and 12) or face).toByte()
            }
            val axial = id == BEAM || id == BEAM_OAK || id == F.SHAFT || id == F.COGWHEEL ||
                id == F.LARGE_COGWHEEL || id == F.GEARBOX || id == F.WATERWHEEL || id == F.LARGE_WATERWHEEL
            if (axial && turn % 2 != 0) {
                val axis = when (raw and 3) { 1 -> 2; 2 -> 1; else -> raw and 3 }
                return ((raw and 12) or axis).toByte()
            }
            return meta
        }

        /** Seeded socket-connected rooms, furnished only after their routes are established. */
        internal fun blueprint(kind: Kind, salt: Long, stage: Int = 0): Plan =
            UndergroundSiteLayout.build(kind, salt, stage)
    }
}
