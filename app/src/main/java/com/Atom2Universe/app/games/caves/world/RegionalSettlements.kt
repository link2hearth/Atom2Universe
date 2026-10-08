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
        val empty = Site(cx * CELL, 0, cz * CELL, 0, false, emptyMap(), emptyList())
        if (rng.nextFloat() > .80f) return@getOrPut empty
        val wantsVillage = rng.nextFloat() < .70f
        // Plusieurs implantations dans la même cellule ; une mauvaise pente ne condamne plus la région.
        for (village in if (wantsVillage) listOf(true, false) else listOf(false)) {
            repeat(6) {
                val x = cx * CELL + 24 + rng.nextInt(112)
                val z = cz * CELL + 24 + rng.nextInt(112)
                buildSite(x, z, village, rng)?.let { return@getOrPut it }
            }
        }
        empty
    }

    private fun buildSite(x: Int, z: Int, village: Boolean, rng: Random): Site? {
        val center = if (village) 44 else 9
        val id = terrain.biomeIdAt(x + center.toDouble(), z + center.toDouble())
        val parent = RegionalBiomes.parent(id)
        if (parent in setOf("ocean", "iceberg", "volcanic")) return null
        val size = if (village) 90 else 19
        val floor = terrain.height(x + center.toDouble(), z + center.toDouble()).toInt()
        // Les maisons auront leurs propres terrasses : plus besoin de 107 m de terrain plat.
        // On garde une limite de terrassement et évite les lacs profonds, sans rejeter une petite mare.
        var wetSamples = 0
        var samples = 0
        val heights = HashMap<Pair<Int, Int>, Int>()
        fun height(dx: Int, dz: Int) = heights.getOrPut(dx to dz) {
            terrain.height((x + dx).toDouble(), (z + dz).toDouble()).toInt()
        }
        for (dx in -8..size + 8 step 4) for (dz in -8..size + 8 step 4) {
            val wx = x + dx; val wz = z + dz
            val h = height(dx, dz)
            val water = terrain.waterLevelAt(wx.toDouble(), wz.toDouble())
            if (abs(h - floor) > (if (village) 24 else 8) || water - h > 3) return null
            samples++
            if (h <= water) wetSamples++
        }
        if (wetSamples * 5 > samples || floor <= terrain.waterLevelAt(x + center.toDouble(), z + center.toDouble())) return null
        val cold = terrain.temperature(x.toDouble(), z.toDouble()) < .30
        val arid = parent in setOf("desert", "red_desert", "savanna")
        val wet = parent == "wetlands" || parent == "jungle"
        val heightAt = { dx: Int, dz: Int -> height(dx, dz) - floor }
        val plan = if (village) village(rng, arid, cold, wet, heightAt)
            else FrontierLandscape.blueprint(rng.nextInt(1, 5), arid, cold, rng, heightAt)
        // Descendre les fondations des seules colonnes construites jusqu'à un appui réel.
        // Les petites ouvertures de grotte n'annulent plus un village entier.
        val supportedPlan = plan.toMutableMap()
        if (!village) {
            // Entrée des petits camps/ruines : escalier extérieur entre la terrasse et le sol.
            var level = 0
            for (step in 0..8) {
                level = heightAt(8, -step).coerceIn(level - 1, level + 1)
                    .let { if (step == 0) 0 else it }
                for (dx in 7..9) {
                    val ground = heightAt(dx, -step)
                    for (y in minOf(ground - 1, level - 1)..level)
                        supportedPlan[Triple(dx, y, -step)] = FrontierLandscape.Cell(if (y == level) GRAVEL else COBBLESTONE)
                    for (y in level + 1..maxOf(ground, level + 3))
                        supportedPlan[Triple(dx, y, -step)] = FrontierLandscape.Cell(AIR)
                }
            }
        }
        for ((column, cells) in supportedPlan.entries.groupBy { it.key.first to it.key.third }) {
            val bottom = cells.minBy { it.key.second }
            if (bottom.value.id == AIR || bottom.value.id == WATER) continue
            val wx = x + column.first; val wz = z + column.second
            val baseY = floor + bottom.key.second
            var support = baseY - 1
            while (support >= baseY - 16 && terrain.groundAt(wx, support, wz) in shortArrayOf(AIR, WATER)) support--
            if (support < baseY - 16) return null
            for (y in support + 1 until baseY)
                supportedPlan[Triple(column.first, y - floor, column.second)] = FrontierLandscape.Cell(COBBLESTONE)
        }
        val mills = plan.filterValues { it.id == F.WINDMILL }.keys.map { FrontierWorkshops.Pos(x + it.first, floor + it.second, z + it.third) }
        val voxels = supportedPlan.map { (p, cell) -> Voxel(x + p.first, floor + p.second, z + p.third, cell) }
        return Site(x, floor, z, size, village, voxels.groupBy { Triple(Math.floorDiv(it.x, 16), Math.floorDiv(it.y, 16), Math.floorDiv(it.z, 16)) }, mills)
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
            fun pad(x: Int, z: Int, w: Int, d: Int, top: Short, level: Int = 0) {
                for (dx in x until x + w) for (dz in z until z + d) column(dx, dz, level, top, clearance = 18)
            }
            val bend = rng.nextInt(-3, 4)
            fun street(x: Int) = 44 + ((x - 44) * bend / 38)
            // Place centrale plane, puis rue en gradins suivant le relief (un bloc maximum par pas).
            val streetLevels = IntArray(105) // x = -8..96, entièrement dans la cellule du village.
            fun streetLevel(x: Int) = streetLevels[x + 8]
            for (x in 38 downTo -8) streetLevels[x + 8] = heightAt(x, street(x))
                .coerceIn(streetLevel(x + 1) - 1, streetLevel(x + 1) + 1)
            for (x in 49..96) streetLevels[x + 8] = heightAt(x, street(x))
                .coerceIn(streetLevel(x - 1) - 1, streetLevel(x - 1) + 1)
            for (x in -8..96) for (w in -1..1) column(x, street(x) + w, streetLevel(x))
            fun accessEnd(x: Int, z: Int) = if (x in 39..48)
                (if (z < street(x)) 37 else 50) else street(x)
            fun connect(x: Int, z: Int, level: Int) {
                val end = accessEnd(x, z)
                val distance = abs(end - z)
                val direction = if (end > z) 1 else -1
                for (step in 0 until distance) {
                    // Palier devant la porte, raccord exact au bord de la rue de chaque côté.
                    val remaining = (distance - step - 1).coerceAtLeast(0)
                    for (w in 0..1) {
                        val target = if (x in 39..48) 0 else streetLevel(x + w)
                        val y = level.coerceIn(target - remaining, target + remaining)
                        column(x + w, z + step * direction, y)
                    }
                }
            }
            // Six independent lots, shuffled roles and house models, fronts always facing the street.
            val roles = (listOf(0, 0, 0, 1, 2) + if (rng.nextBoolean()) 0 else 1).shuffled(rng)
            val models = FrontierHouses.models.indices.shuffled(rng)
            for (slot in 0..5) {
                val ox = 5 + (slot % 3) * 28 + rng.nextInt(3)
                val north = slot < 3
                val oz = if (north) 15 + rng.nextInt(4) else 59 + rng.nextInt(4)
                // Chaque lot choisit sa hauteur locale, bornée par la longueur de son accès.
                val frontage = if (north) oz + (if (roles[slot] == 2) 20 else if (roles[slot] == 1) 12 else FrontierHouses.models[models[slot]].depth) else oz - 1
                val accessX = ox + when (roles[slot]) { 1 -> 7; 2 -> 3; else -> {
                    val m = FrontierHouses.models[models[slot]]
                    if (north) m.width - 2 - m.doorX else m.doorX
                } }
                val reach = (abs(accessEnd(accessX, frontage) - frontage) - 3).coerceAtLeast(0)
                val base = streetLevel(accessX)
                val level = heightAt(ox + 8, oz + 6).coerceIn(base - reach, base + reach)
                fun b(dx: Int, dy: Int, dz: Int, block: Short, meta: Byte = 0) = put(dx, dy + level, dz, block, meta)
                when (roles[slot]) {
                    0 -> {
                        val model = models[slot]; val m = FrontierHouses.models[model]
                        pad(ox - 1, oz - 1, m.width + 2, m.depth + 2, style.foundation, level)
                        fun p(dx: Int, dy: Int, dz: Int, block: Short, meta: Byte = 0) {
                            // North-side houses rotate 180 degrees, including each leaf's facing.
                            // Keep hinge, opening and upper-half bits intact.
                            val rotated = if (north && (block == style.door || block == style.window))
                                ((meta.toInt() and 3.inv()) or ((meta.toInt() + 2) and 3)).toByte() else meta
                            b(ox + if (north) m.width - 1 - dx else dx, dy,
                                oz + if (north) m.depth - 1 - dz else dz, block, rotated)
                        }
                        FrontierHouses.build(model, style, ::p)
                        p(1, 1, m.depth - 2, F.CACHE); p(m.width - 2, 1, m.depth - 2, F.COOKER)
                        p(1, 2, m.depth - 2, TORCH)
                        val doorX = ox + if (north) m.width - 2 - m.doorX else m.doorX
                        connect(doorX, if (north) oz + m.depth else oz - 1, level)
                    }
                    1 -> {
                        pad(ox, oz, 16, 12, DIRT, level)
                        for (dx in 1..14) for (dz in 1..10) {
                            b(ox + dx, 0, oz + dz, if (dx % 5 == 0) WATER else FarmSoil.FARMLAND)
                            if (dx % 5 != 0) b(ox + dx, 1, oz + dz,
                                FarmShowcasePlants.id(if (arid) 9 else if (cold) 4 else (slot + dz / 3) % 6, 4))
                        }
                        column(ox + 16, oz + 6, level)
                        b(ox + 16, 1, oz + 6, F.COMPOSTER)
                        connect(ox + 7, if (north) oz + 12 else oz - 1, level)
                    }
                    else -> {
                        // Rotor is drawn as one animated piece. No stationary sail blocks underneath it.
                        pad(ox, oz, 20, 20, style.foundation, level)
                        for (dx in listOf(9, 11)) for (dz in listOf(6, 8)) for (y in 1..9) b(ox + dx, y, oz + dz, style.frame)
                        for (dx in 8..12) for (dz in 5..9) b(ox + dx, 10, oz + dz, style.roof)
                        b(ox + 10, 9, oz + 5, F.WINDMILL, 2)
                        b(ox + 10, 9, oz + 6, F.SHAFT, 2); b(ox + 10, 9, oz + 7, F.GEARBOX, 1)
                        for (y in 2..8) b(ox + 10, y, oz + 7, F.SHAFT)
                        b(ox + 10, 1, oz + 7, F.MILL); b(ox + 13, 1, oz + 7, F.CACHE)
                        connect(ox + 3, if (north) oz + 20 else oz - 1, level)
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
                val level = streetLevel(x)
                column(x, z, level)
                for (y in 1..3) put(x, level + y, z, style.frame)
                put(x, level + 4, z, TORCH)
            }
            return data
        }
    }
}
