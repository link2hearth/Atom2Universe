package com.Atom2Universe.app.games.caves.world

import kotlin.math.abs

import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.MineralItems as M
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
        val bottom = key.y * HEIGHT + 8
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
                    if (roof - y < 16 || terrain.isFlooded(wx, y - 2, wz, roof,
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
            val id = if (value.id == RAIL && turn % 2 != 0) RAIL_CROSSWISE else value.id
            val meta = rotatedMeta(id, value.meta, turn).toInt()
            val cells = chunks.getOrPut(key) { ArrayList() }
            cells.add(index or ((meta and 15) shl 12) or ((id.toInt() and 65535) shl 16))
            if (value.id == F.CACHE) caches += p
            if (local.y in 1..10 && (id == AIR || id == COBWEB || id == RAIL || id == RAIL_CROSSWISE || id in 2620.toShort()..2626.toShort())) {
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

        /** Authored routes first, furnishing second. Every type has an optional loop and two caches. */
        internal fun blueprint(kind: Kind, salt: Long, stage: Int = 0): Plan {
            val rng = Random(salt)
            val blocks = linkedMapOf<Point, Cell>()
            val pads = ArrayList<Point>()
            val mine = kind in setOf(Kind.ABANDONED_MINE, Kind.FUNGAL_MINE, Kind.EMBER_FOUNDRY, Kind.DWARVEN_MINE)
            // A coherent material family per site, with local supply variants in its side rooms.
            val style = ((salt ushr 27) % 3L).toInt()
            val fir = kind in setOf(Kind.FUNGAL_MINE, Kind.EMBER_FOUNDRY, Kind.WRAITH_ARCHIVE, Kind.TROLL_GROVE)
            val beam = if (fir) BEAM else BEAM_OAK
            val bookshelf = if (fir) BOOKSHELF_FIR else BOOKSHELF
            val barrel = if (fir) BARREL_FIR else BARREL_OAK
            val tombBrick = shortArrayOf(TOMB_BRICKS,TOMB_SMOOTH,TOMB_WEATHERED)[style]
            val basaltBrick = shortArrayOf(BASALT_BRICKS,BASALT_SMOOTH,BASALT_CRACKED)[style]
            val altar = when (kind) {
                Kind.ZOMBIE_TEMPLE -> SKULL_ALTAR
                Kind.WRAITH_ARCHIVE -> RITUAL_ALTAR
                else -> ALTAR
            }
            val wall: Short = when (kind) {
                Kind.ZOMBIE_TEMPLE -> tombBrick
                Kind.MUMMY_TOMB -> BRICK_SANDY
                Kind.WRAITH_ARCHIVE -> BRICK_OBSIDIAN
                Kind.TROLL_GROVE, Kind.SLIME_CISTERN -> BRICK_MOSS
                Kind.GOBLIN_LABORATORY, Kind.DWARVEN_MINE -> BRICK_GREY
                Kind.CRYSTAL_SANCTUM -> 2613
                Kind.EMBER_FOUNDRY -> basaltBrick
                else -> COBBLESTONE
            }
            val floor: Short = when (kind) {
                Kind.FUNGAL_MINE -> 2607
                Kind.ABANDONED_MINE -> PLANK
                Kind.CRYSTAL_SANCTUM -> QUARTZ
                else -> wall
            }
            fun put(x: Int, y: Int, z: Int, id: Short, meta: Byte = 0) {
                val material = when (id) {
                    BEAM -> beam
                    BOOKSHELF -> bookshelf
                    CRATE -> shortArrayOf(CRATE,CRATE_OAK,CRATE_FIR)[Math.floorMod(x + z + style,3)]
                    ALTAR -> altar
                    BASALT_BRICKS -> basaltBrick
                    VAT -> shortArrayOf(VAT,VAT_LOW,VAT_HALF,VAT_FULL)[Math.floorMod(x + z / 4 + style,4)]
                    else -> id
                }
                blocks[Point(x,y,z)] = Cell(material,meta)
            }
            fun box(x0: Int, x1: Int, y0: Int, y1: Int, z0: Int, z1: Int, id: Short, meta: Byte = 0) {
                for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) put(x,y,z,id,meta)
            }
            fun room(x0: Int, x1: Int, z0: Int, z1: Int, high: Int = 5) {
                box(x0,x1,-1,0,z0,z1,floor)
                box(x0,x1,1,high,z0,z1,AIR)
                box(x0,x1,high+1,high+1,z0,z1,wall)
                box(x0,x0,1,high,z0,z1,wall); box(x1,x1,1,high,z0,z1,wall)
                box(x0,x1,1,high,z0,z0,wall); box(x0,x1,1,high,z1,z1,wall)
            }
            fun route(x0: Int, x1: Int, z0: Int, z1: Int) {
                box(x0,x1,-1,0,z0,z1,floor)
                box(x0,x1,1,3,z0,z1,AIR)
            }
            fun pillar(x: Int, z: Int, high: Int, id: Short = wall) = box(x,x,1,high,z,z,id)
            fun cache(x: Int, z: Int) {
                // Open toward the site's central galleries, then rotate with the whole plan.
                val facing = if (abs(x) > abs(z - 20)) { if (x < 0) 2 else 3 }
                    else if (z > 20) 0 else 1
                put(x,1,z,F.CACHE, facing.toByte())
            }
            fun pad(x: Int, z: Int) { pads += Point(x,1,z) }

            fun hydraulicWorkshop(wheelX: Int, large: Boolean, machine: Short) {
                // Replace the old display furniture, never stack moving parts through its blocks.
                // The dry gallery and encounter pads lie outside this service bay.
                box(wheelX-4,wheelX+1,1,5,18,24,AIR)
                box(wheelX-4,wheelX+1,-1,0,18,24,floor)
                val reach = if (large) 2 else 1
                // An EMPTY recessed basin receives flow, with a full floor and solid rim.
                // Prefilling it with sources would make the falling water spill at floor height.
                box(wheelX-1,wheelX+1,0,0,21-reach,21+reach,AIR)
                // A single enclosed spring above the paddle on +Z. Normal water activation on
                // chunk load creates the falling column; no saved flow levels or fake rotation.
                box(wheelX-1,wheelX+1,6,7,20+reach,22+reach,wall)
                put(wheelX,6,21+reach,WATER)
                put(wheelX,3,21,if (large) F.LARGE_WATERWHEEL else F.WATERWHEEL,1)
                put(wheelX-1,3,21,F.SHAFT,1)
                put(wheelX-2,3,21,F.COGWHEEL,1)
                put(wheelX-2,2,21,F.COGWHEEL,1)
                // Gearbox axis Z exposes X/Y sockets: it feeds the machine immediately below.
                put(wheelX-3,2,21,F.GEARBOX,2)
                put(wheelX-3,1,21,machine)
                // Bearing below the overhead gear train, away from the room's entrance.
                put(wheelX-2,1,21,wall)
                put(wheelX-3,1,24,barrel)
            }

            if (mine) {
                // Two parallel maintenance galleries make a real circuit around the railway.
                room(-3,3,-3,38,4)
                room(-19,19,9,15,4); room(-19,19,27,33,4)
                room(-19,-13,9,33,4); room(13,19,9,33,4)
                room(-23,-13,17,25,5) // store / mushroom nursery
                room(11,23,17,25,5)  // workshop
                room(-8,8,33,45,10)  // terminal and boss arena
                route(-1,1,-4,40)
                route(-17,17,11,13); route(-17,17,29,31)
                route(-17,-15,12,31); route(15,17,12,31)
                route(-18,-14,20,22); route(14,18,20,22)
                for (z in 1..35) put(0,1,z,RAIL)
                // Five-block frames, all outside the clear central three-block passage.
                for (z in 1..36 step 5) {
                    pillar(-2,z,3,BEAM); pillar(2,z,3,BEAM)
                    box(-2,2,4,4,z,z,BEAM,1)
                }
                for (x in listOf(-16,16)) for (z in listOf(12,30)) {
                    put(x-1,1,z+1,CRATE); pad(x+1,z)
                }
                put(20,1,20,if (kind == Kind.EMBER_FOUNDRY) E.FORGE else F.COOKER)
                put(21,1,22,CRATE)
                for (y in 1..2) put(20,y,24,BOOKSHELF)
                put(-21,1,24,barrel); put(22,1,18,barrel)
                pad(-20,19); pad(20,24); pad(-4,38); pad(4,36)
                when (kind) {
                    Kind.FUNGAL_MINE -> {
                        for (z in 5..35 step 6) put(if (z % 2 == 0) -1 else 1,1,z,(2621 + rng.nextInt(4)).toShort())
                        for (x in -21..-19) for (z in 18..24 step 3) put(x,1,z,2626)
                        pillar(-6,39,3,2609); box(-7,-5,4,4,37,41,2646)
                    }
                    Kind.EMBER_FOUNDRY -> {
                        for (x in listOf(-4,4)) { pillar(x,37,4,BASALT_BRICKS); put(x,5,37,2625) }
                        box(17,21,1,1,18,18,BASALT_BRICKS)
                        put(19,2,18,VAT); put(-21,1,18,2625)
                    }
                    Kind.DWARVEN_MINE -> {
                        put(20,1,20,E.FORGE); put(20,1,22,E.ANVIL)
                        for (x in listOf(-5,5)) { pillar(x,37,4,BEAM); put(x,5,37,2631) }
                        put(-21,1,18,CRATE)
                    }
                    else -> {
                        for (z in listOf(7,22,34)) put(1,3,z,COBWEB)
                        put(-21,1,18,CRATE); put(-20,2,18,COBWEB)
                        // One collapsed service niche; it never seals the circuit.
                        box(21,22,1,2,18,19,GRAVEL)
                    }
                }
            } else if (kind == Kind.ZOMBIE_TEMPLE || kind == Kind.MUMMY_TOMB || kind == Kind.WRAITH_ARCHIVE) {
                room(-4,4,-3,10,5); room(-10,10,8,33,7)
                room(-22,-9,12,28,4); room(9,22,12,28,4)
                room(-8,8,32,45,10)
                route(-1,1,-4,40)
                for (z in listOf(14,26)) route(-19,19,z,z+2)
                for (x in listOf(-6,6)) for (z in listOf(12,20,28)) pillar(x,z,6)
                // A low dais and six lateral sarcophagi leave a broad central nave.
                box(-3,3,1,1,29,31,wall); put(0,2,30,ALTAR)
                for (side in listOf(-1,1)) for (z in listOf(18,22)) {
                    box(minOf(side*14,side*19),maxOf(side*14,side*19),1,1,z,z+1,wall)
                    put(side*20,2,z,COBWEB)
                }
                cache(-5,40); cache(20,25)
                for ((x,z) in listOf(-3 to 12,3 to 21,-12 to 15,12 to 27,4 to 37)) pad(x,z)
                put(-8,1,10,2621); put(8,1,31,2621)
                if (kind == Kind.MUMMY_TOMB) {
                    // Terracotta mosaics and stepped burial monuments distinguish the dry tomb.
                    for (z in 10..28 step 3) box(-1,1,0,0,z,z,BRICK_TERRACOTTA)
                    for (x in listOf(-16,16)) {
                        box(x-2,x+2,1,1,18,22,BRICK_TERRACOTTA)
                        box(x-1,x+1,2,2,19,21,BRICK_SANDY); put(x,3,20,ALTAR)
                    }
                    put(-8,1,10,2620); put(8,1,31,2620)
                } else if (kind == Kind.WRAITH_ARCHIVE) {
                    // Real recessed shelves face the archive aisles, with a violet fungal index.
                    for (side in listOf(-1,1)) for (z in 17..23 step 3)
                        for (y in 1..3) put(side*20,y,z,BOOKSHELF,if(side<0) 2 else 3)
                    put(0,2,30,ALTAR); put(-8,1,10,2622); put(8,1,31,2622)
                }
            } else if (kind == Kind.GOBLIN_LABORATORY) {
                room(-4,4,-3,10,4); room(-9,9,8,32,6)
                room(-22,-8,12,29,5); room(8,22,12,29,5)
                room(-8,8,31,45,10)
                route(-1,1,-4,40)
                for (z in listOf(14,26)) route(-19,19,z,z+2)
                // Alchemy vessels, specimen plinths and shelves of goblin research notes.
                for (z in listOf(18,22,26)) {
                    put(-19,1,z,VAT)
                    for (y in 1..2) put(-21,y,z,BOOKSHELF,2)
                    box(17,20,1,1,z,z,wall)
                    put(18,2,z,2622); put(19,2,z,2631)
                }
                for (x in listOf(-8,8)) for (z in 18..22) for (y in 1..2)
                    put(x,y,z,BOOKSHELF,if(x<0) 2 else 3)
                put(-5,1,20,VAT); put(5,1,20,2631)
                put(20,1,28,barrel); put(-20,1,28,barrel)
                put(5,1,39,F.COOKER); put(4,1,40,CRATE)
                cache(-5,39); cache(20,15)
                for ((x,z) in listOf(-3 to 11,3 to 27,-12 to 19,12 to 23,2 to 38)) pad(x,z)
            } else if (kind == Kind.CRYSTAL_SANCTUM) {
                room(-3,3,-3,10,5); room(-11,11,8,33,7)
                room(-21,-10,13,28,5); room(10,21,13,28,5)
                room(-8,8,32,45,10)
                route(-1,1,-4,40)
                for (z in listOf(15,25)) route(-18,18,z,z+2)
                for (x in listOf(-7,7)) for (z in listOf(12,20,29)) {
                    pillar(x,z,4,2614); put(x,5,z,2631)
                }
                // Crystal lens with an open aisle on both sides, surrounded by observation alcoves.
                box(-2,2,1,1,28,30,2613); put(0,2,29,2631)
                for (x in listOf(-19,19)) for (z in listOf(18,22)) put(x,1,z,2631)
                cache(-4,40); cache(-19,25)
                for ((x,z) in listOf(-4 to 17,4 to 24,13 to 20,-13 to 20,2 to 37)) pad(x,z)
            } else {
                // A broad inhabited hall with two side walks around its thematic centre.
                room(-4,4,-3,10,5); room(-12,12,8,33,8)
                room(-23,-11,13,28,5); room(11,23,13,28,5)
                room(-8,8,32,45,10)
                route(-1,1,-4,40)
                for(z in listOf(15,25)) route(-20,20,z,z+2)
                when(kind) {
                    Kind.OGRE_DEN -> {
                        // Two long banquet tables, store alcoves and a rough stone throne.
                        for(x in listOf(-6,6)) {
                            box(x-1,x+1,1,1,18,23,PLANK_DARK)
                            put(x,2,20,CRATE); put(x,2,22,F.COOKER)
                        }
                        box(-2,2,1,2,43,44,COBBLESTONE); box(-2,2,3,5,44,44,COBBLESTONE)
                        for(z in 18..23 step 2) { put(-21,1,z,CRATE); put(21,1,z,barrel) }
                    }
                    Kind.TROLL_GROVE -> {
                        // Massive rooted piers, fern beds and bioluminescent nursery alcoves.
                        for(x in listOf(-8,8)) for(z in listOf(13,29)) {
                            pillar(x,z,7,2608); box(x-1,x+1,7,7,z-1,z+1,MOSS)
                        }
                        for(side in listOf(-1,1)) for(z in 18..23) {
                            put(side*20,0,z,MOSS); put(side*20,1,z,if(z%2==0) 2630 else 2621)
                        }
                    }
                    Kind.SLIME_CISTERN -> {
                        // Recessed, sealed water channels with dry three-block aisles around them.
                        for(x in listOf(-6,6)) {
                            box(x-2,x+2,-2,-2,18,23,wall)
                            box(x-2,x+2,-1,0,18,23,wall)
                            box(x-1,x+1,0,0,19,22,WATER)
                        }
                        for(x in listOf(-10,10)) for(z in listOf(11,30)) {
                            pillar(x,z,5,wall); put(x,6,z,2621)
                        }
                        put(-21,1,20,VAT); put(21,1,20,VAT)
                        put(-21,1,22,barrel); put(21,1,22,barrel)
                    }
                    else -> error("Unhandled underground site $kind")
                }
                cache(-21,25); cache(6,42)
                for((x,z) in listOf(-3 to 12,3 to 28,-16 to 19,16 to 22,2 to 37)) pad(x,z)
            }
            // Working scenery uses the existing water/rotation systems. No mob owns a machine,
            // no inventory is supplied, and no crafting or fuel is simulated for the inhabitants.
            when (kind) {
                Kind.DWARVEN_MINE -> {
                    hydraulicWorkshop(21, true, if (style == 1) F.PRESS else F.CRUSHER)
                    put(18,1,18,E.FORGE,1); put(19,1,18,E.ANVIL)
                    put(18,1,23,FURNACE,2)
                    // Store materials beside the encounter pad at (20, 1, 24).
                    put(19,1,24,CRATE); put(22,1,18,barrel)
                }
                Kind.GOBLIN_LABORATORY -> {
                    hydraulicWorkshop(20, false, if (style == 2) F.MILL else F.PRESS)
                    put(17,1,18,F.COOKER); put(18,1,18,VAT)
                    put(17,1,23,VAT); put(18,1,24,barrel)
                    for (y in 1..2) put(20,y,24,BOOKSHELF,0)
                }
                Kind.EMBER_FOUNDRY -> {
                    hydraulicWorkshop(21, true, F.BELLOWS)
                    put(18,1,22,E.FORGE,3); put(18,2,22,F.CRUCIBLE)
                    put(19,1,22,wall); put(19,2,22,F.CAST_MOLD)
                    put(18,1,18,FURNACE,1); put(19,1,18,E.ANVIL)
                    put(22,1,18,barrel)
                }
                Kind.SLIME_CISTERN -> {
                    hydraulicWorkshop(21, false, F.MILL)
                    put(18,1,18,F.COOKER); put(19,1,18,CRATE)
                    put(19,1,24,VAT); put(21,1,24,barrel)
                    put(18,2,24,2621)
                }
                else -> Unit
            }
            // An identifiable portal, with no door or light barrier stopping cave access.
            // Furnishings and fungal beds must never overwrite a persistent loot anchor.
            if (mine) { cache(-21,21); cache(5,40) }
            for (x in listOf(-2,2)) pillar(x,-2,3,if(mine) BEAM else wall)
            box(-2,2,4,4,-2,-2,if(mine) BEAM else wall,if(mine) 1 else 0)
            // Arena centre is deliberately unobstructed, even for the largest current boss model.
            box(-3,3,1,9,36,42,AIR)
            // Side reliefs vary per site without moving its route, boss or persistent caches.
            val accent: Short = (2620 + rng.nextInt(7)).toShort()
            for(x in listOf(-7,7)) { put(x,1,35,wall); put(x,2,35,accent) }
            // A small exposed seam visually identifies the metal of this equipment layer.
            for(x in listOf(-7,7)) for(y in 1..2) put(x,y,43,M.id(stage,M.Form.ORE))
            // Pads remain plain floor + air; dressing cannot silently bury a planned encounter.
            val validPads = pads.filter { p -> blocks[p]?.id == AIR && blocks[p.copy(y=p.y+1)]?.id == AIR }
            return Plan(blocks, validPads, Point(0,1,39))
        }
    }
}
