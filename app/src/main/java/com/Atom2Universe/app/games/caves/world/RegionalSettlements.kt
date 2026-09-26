package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants
import com.Atom2Universe.app.games.caves.node.FarmSoil
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

/** Entire plans are deterministic and fit inside their cell, including entrance ramps and rotors. */
internal class RegionalSettlements(private val seed: Long, private val terrain: NaturalTerrain) {
    private data class Voxel(val x: Int, val y: Int, val z: Int, val cell: FrontierLandscape.Cell)
    private data class Site(val x: Int, val y: Int, val z: Int, val size: Int, val village: Boolean,
        val chunks: Map<Triple<Int, Int, Int>, List<Voxel>>, val mills: List<FrontierWorkshops.Pos>)
    private val sites = ConcurrentHashMap<Long, Site>()

    private fun site(cx: Int, cz: Int): Site = sites.getOrPut(columnCacheKey(cx, cz)) {
        if (sites.size > 96) sites.clear()
        val rng = Random(seed xor cx.toLong() * 341873128712L xor cz.toLong() * 132897987541L xor 915791L)
        val x = cx * CELL + 40 + rng.nextInt(64); val z = cz * CELL + 40 + rng.nextInt(64)
        val y = terrain.height(x + 44.0, z + 44.0).toInt()
        val empty = Site(x, y, z, 0, false, emptyMap(), emptyList())
        if (rng.nextFloat() > .68f) return@getOrPut empty
        val id = terrain.biomeIdAt(x + 44.0, z + 44.0)
        val parent = RegionalBiomes.parent(id)
        if (parent in setOf("ocean", "iceberg", "volcanic", "mountains")) return@getOrPut empty
        val village = rng.nextFloat() < .65f
        val size = if (village) 90 else 19
        val floor = if (village) y else terrain.height(x + 9.0, z + 9.0).toInt()
        // Survey the complete footprint, including approach ramps. Keep lakes and cave mouths open.
        for (dx in -8..size + 8 step 2) for (dz in -8..size + 8 step 2) {
            val wx = x + dx; val wz = z + dz
            val h = terrain.height(wx.toDouble(), wz.toDouble()).toInt()
            if (abs(h - floor) > 3 || h <= terrain.waterLevelAt(wx.toDouble(), wz.toDouble()) + 1 ||
                terrain.caveAt(wx, h, wz) || terrain.caveAt(wx, h - 3, wz)) return@getOrPut empty
        }
        val cold = terrain.temperature(x.toDouble(), z.toDouble()) < .30
        val arid = parent in setOf("desert", "red_desert", "savanna")
        val wet = parent == "wetlands" || parent == "jungle"
        val heightAt = { dx: Int, dz: Int -> terrain.height((x + dx).toDouble(), (z + dz).toDouble()).toInt() - floor }
        val plan = if (village) village(rng, arid, cold, wet, heightAt)
            else FrontierLandscape.blueprint(rng.nextInt(1, 5), arid, cold, rng, heightAt)
        val mills = plan.filterValues { it.id == F.WINDMILL }.keys.map { FrontierWorkshops.Pos(x + it.first, floor + it.second, z + it.third) }
        val voxels = plan.map { (p, cell) -> Voxel(x + p.first, floor + p.second, z + p.third, cell) }
        Site(x, floor, z, size, village, voxels.groupBy { Triple(Math.floorDiv(it.x, 16), Math.floorDiv(it.y, 16), Math.floorDiv(it.z, 16)) }, mills)
    }

    fun homes(x: Double, z: Double): List<FrontierLife.Place> = buildList {
        val cx = kotlin.math.floor(x / CELL).toInt(); val cz = kotlin.math.floor(z / CELL).toInt()
        for (dx in -1..1) for (dz in -1..1) {
            val s = site(cx + dx, cz + dz)
            if (s.village) add(FrontierLife.Place(s.x + 44, s.y, s.z + 44))
        }
    }
    fun reserves(x: Int, z: Int, margin: Int): Boolean {
        for (cx in Math.floorDiv(x - margin, CELL)..Math.floorDiv(x + margin, CELL))
            for (cz in Math.floorDiv(z - margin, CELL)..Math.floorDiv(z + margin, CELL)) {
                val s = site(cx, cz)
                if (s.size > 0 && x in s.x - 8 - margin..s.x + s.size + 8 + margin &&
                    z in s.z - 8 - margin..s.z + s.size + 8 + margin) return true
            }
        return false
    }
    fun windmill(p: FrontierWorkshops.Pos): Boolean = p in site(Math.floorDiv(p.x, CELL), Math.floorDiv(p.z, CELL)).mills
    fun decorate(chunk: Chunk) {
        val s = site(Math.floorDiv(chunk.worldX, CELL), Math.floorDiv(chunk.worldZ, CELL))
        for (v in s.chunks[Triple(chunk.cx, chunk.cy, chunk.cz)].orEmpty()) {
            chunk.setBlock(v.x - chunk.worldX, v.y - chunk.worldY, v.z - chunk.worldZ, v.cell.id)
            chunk.setMeta(v.x - chunk.worldX, v.y - chunk.worldY, v.z - chunk.worldZ, v.cell.meta)
        }
    }

    companion object {
        private const val CELL = 256
        /** Same five-block pinwheel as the finished Assault mechanics exhibit. */
        fun sails(): List<IntArray> = buildList {
            fun sail(x: Int, y: Int) { add(intArrayOf(x, y, -1, F.SAIL.toInt())) }
            sail(0, 0)
            for (i in 1..5) { sail(i, 0); sail(0, i); sail(-i, 0); sail(0, -i) }
            for (i in 2..5) { sail(i, 1); sail(-1, i); sail(-i, -1); sail(1, -i) }
        }

        internal fun village(rng: Random, arid: Boolean, cold: Boolean, wet: Boolean,
            heightAt: (Int, Int) -> Int): Map<Triple<Int, Int, Int>, FrontierLandscape.Cell> {
            val data = linkedMapOf<Triple<Int, Int, Int>, FrontierLandscape.Cell>()
            val style = if (wet && !cold && !arid) FrontierHouses.Style(MOSSY_COBBLESTONE, PLANK_JUNGLE,
                PLANK_JUNGLE, MOSSY_COBBLESTONE, WOOD_JUNGLE, PLANK_DARK, flat = false, snow = false)
                else FrontierHouses.style(arid, cold)
            val path = if (wet) PLANK_JUNGLE else if (arid) SANDSTONE else GRAVEL
            fun put(x: Int, y: Int, z: Int, id: Short, meta: Byte = 0) { data[Triple(x, y, z)] = FrontierLandscape.Cell(id, meta) }
            fun column(x: Int, z: Int, level: Int = 0, top: Short = path, clearance: Int = 4) {
                val ground = heightAt(x, z)
                for (y in minOf(ground - 1, level - 1)..level) put(x, y, z, if (y == level) top else style.foundation)
                for (y in level + 1..maxOf(ground, level + clearance)) put(x, y, z, AIR)
            }
            fun pad(x: Int, z: Int, w: Int, d: Int, top: Short) {
                for (dx in x until x + w) for (dz in z until z + d) column(dx, dz, top = top, clearance = 18)
            }
            val bend = rng.nextInt(-3, 4)
            fun street(x: Int) = 44 + ((x - 44) * bend / 38)
            for (x in 0..88) for (w in -1..1) column(x, street(x) + w)
            // Both ends descend to the natural ground, at most one block per step.
            for (side in listOf(0, 88)) for (step in 1..6) for (w in -1..1) {
                val x = side + if (side == 0) -step else step
                val ground = heightAt(x, street(side) + w)
                column(x, street(side) + w, ground.coerceIn(-step, step))
            }
            fun connect(x: Int, z: Int) {
                for (dz in minOf(z, street(x))..maxOf(z, street(x))) for (w in 0..1) column(x + w, dz)
            }
            // Six independent lots, shuffled roles and house models, fronts always facing the street.
            val roles = (listOf(0, 0, 0, 1, 2) + if (rng.nextBoolean()) 0 else 1).shuffled(rng)
            val models = FrontierHouses.models.indices.shuffled(rng)
            for (slot in 0..5) {
                val ox = 5 + (slot % 3) * 28 + rng.nextInt(3)
                val north = slot < 3
                val oz = if (north) 15 + rng.nextInt(4) else 59 + rng.nextInt(4)
                when (roles[slot]) {
                    0 -> {
                        val model = models[slot]; val m = FrontierHouses.models[model]
                        pad(ox - 1, oz - 1, m.width + 2, m.depth + 2, style.foundation)
                        fun p(dx: Int, dy: Int, dz: Int, block: Short) {
                            put(ox + if (north) m.width - 1 - dx else dx, dy,
                                oz + if (north) m.depth - 1 - dz else dz, block)
                        }
                        FrontierHouses.build(model, style, ::p)
                        p(1, 1, m.depth - 2, F.CACHE); p(m.width - 2, 1, m.depth - 2, F.COOKER)
                        p(1, 2, m.depth - 2, TORCH)
                        val doorX = ox + if (north) m.width - 2 - m.doorX else m.doorX
                        connect(doorX, if (north) oz + m.depth else oz - 1)
                    }
                    1 -> {
                        pad(ox, oz, 16, 12, DIRT)
                        for (dx in 1..14) for (dz in 1..10) {
                            put(ox + dx, 0, oz + dz, if (dx % 5 == 0) WATER else FarmSoil.FARMLAND)
                            if (dx % 5 != 0) put(ox + dx, 1, oz + dz,
                                FarmShowcasePlants.id(if (arid) 9 else if (cold) 4 else (slot + dz / 3) % 6, 4))
                        }
                        put(ox + 16, 1, oz + 6, F.COMPOSTER)
                        connect(ox + 7, if (north) oz + 12 else oz - 1)
                    }
                    else -> {
                        // Rotor is drawn as one animated piece. No stationary sail blocks underneath it.
                        pad(ox, oz, 20, 20, style.foundation)
                        for (dx in listOf(9, 11)) for (dz in listOf(6, 8)) for (y in 1..9) put(ox + dx, y, oz + dz, style.frame)
                        for (dx in 8..12) for (dz in 5..9) put(ox + dx, 10, oz + dz, style.roof)
                        put(ox + 10, 9, oz + 5, F.WINDMILL, 2)
                        put(ox + 10, 9, oz + 6, F.SHAFT, 2); put(ox + 10, 9, oz + 7, F.GEARBOX, 1)
                        for (y in 2..8) put(ox + 10, y, oz + 7, F.SHAFT)
                        put(ox + 10, 1, oz + 7, F.MILL); put(ox + 13, 1, oz + 7, F.CACHE)
                        connect(ox + 3, if (north) oz + 20 else oz - 1)
                    }
                }
            }
            // A dry, walkable gathering square: fountain sits beside, never across, the main route.
            pad(39, 37, 10, 14, path)
            for (x in 40..46) for (z in 37..40) put(x, 0, z,
                if (x in 41..45 && z in 38..39) WATER else style.foundation)
            put(48, 1, 49, F.MARKET_BELL); put(40, 1, 49, F.CACHE)
            for (x in listOf(18, 44, 74)) {
                val z = street(x) + 3
                for (y in 1..3) put(x, y, z, style.frame)
                put(x, 4, z, TORCH)
            }
            return data
        }
    }
}
