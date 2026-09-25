package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FarmItems
import com.Atom2Universe.app.games.caves.node.FarmShowcasePlants
import com.Atom2Universe.app.games.caves.node.FarmSoil
import org.json.JSONArray
import org.json.JSONObject

/** Loaded crops grow in world time, with light, irrigation and persistent accumulated progress. */
internal class Farming(private val world: World, private val rebuild: (Int, Int, Int) -> Unit,
                       private val light: (Int, Int, Int) -> Int = { _, _, _ -> 15 }) {
    data class Position(val x: Int, val y: Int, val z: Int)
    data class Plant(val position: Position, val crop: Int, var plantedAt: Long,
                     var growthMs: Long = 0L, var checkedAt: Long = plantedAt)
    private val plants = LinkedHashMap<Position, Plant>()
    private var cursor: MutableIterator<MutableMap.MutableEntry<Position, Plant>>? = null
    private var clockMs = 0L
    private var tickMs = 0L
    var initialized = false
        private set

    @Synchronized fun initialize(): Boolean {
        if (initialized) return false
        initialized = true
        return true
    }
    @Synchronized fun restore(json: String) {
        plants.clear(); cursor = null; tickMs = 0L
        val root = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        clockMs = root.optLong("clock", 0L).coerceAtLeast(0L)
        initialized = root.optBoolean("initialized", false)
        val rows = root.optJSONArray("plants") ?: return
        for (i in 0 until rows.length()) runCatching {
            val r = rows.getJSONArray(i)
            val p = Position(r.getInt(0), r.getInt(1), r.getInt(2))
            val crop = r.getInt(3)
            if (crop in FarmItems.crops.indices) {
                val planted=r.getLong(4).coerceIn(0L,clockMs)
                plants[p] = Plant(p,crop,planted,r.optLong(5,clockMs-planted).coerceIn(0L,FarmItems.durationMs(crop)),
                    r.optLong(6,clockMs).coerceIn(0L,clockMs))
            }
        }
    }
    @Synchronized fun snapshot(): String {
        val rows = JSONArray()
        for (plant in plants.values) rows.put(JSONArray().put(plant.position.x).put(plant.position.y)
            .put(plant.position.z).put(plant.crop).put(plant.plantedAt).put(plant.growthMs).put(plant.checkedAt))
        return JSONObject().put("clock",clockMs).put("initialized",initialized).put("plants",rows).toString()
    }
    private fun loaded(p: Position) = world.getChunk(Math.floorDiv(p.x,CHUNK_SIZE),
        Math.floorDiv(p.y,CHUNK_SIZE),Math.floorDiv(p.z,CHUNK_SIZE))?.generated == true
    private fun stage(plant: Plant) = (plant.growthMs * 4 /
        FarmItems.durationMs(plant.crop)).toInt().coerceIn(0,4)
    private fun irrigated(p: Position): Boolean {
        for (dx in -4..4) for (dz in -4..4) for (dy in -1..0)
            if (isWater(world.blockAt(p.x+dx,p.y-1+dy,p.z+dz))) return true
        return false
    }
    private fun grow(plant: Plant) {
        val p=plant.position
        // Catch-up is bounded to the current visit. Unloaded fields never grow in unknown conditions.
        val elapsed=(clockMs-plant.checkedAt).coerceIn(0L,10_000L)
        plant.checkedAt=clockMs
        if (plant.growthMs>=FarmItems.durationMs(plant.crop)) return
        if (light(p.x,p.y,p.z)<8) return
        plant.growthMs=(plant.growthMs + (elapsed * if(irrigated(p)) 1.0 else .4).toLong())
            .coerceAtMost(FarmItems.durationMs(plant.crop))
    }
    @Synchronized fun advance(ms: Long) {
        clockMs += ms.coerceIn(0L,1000L); tickMs += ms.coerceIn(0L,1000L)
        if (tickMs < 1000L) return
        tickMs %= 1000L
        val iterator = cursor?.takeIf { it.hasNext() } ?: plants.entries.iterator().also { cursor = it }
        var budget = 128
        while (budget-- > 0 && iterator.hasNext()) {
            val plant = iterator.next().value; val p = plant.position
            if (!loaded(p) || world.getChunk(Math.floorDiv(p.x,CHUNK_SIZE),
                Math.floorDiv(p.y-1,CHUNK_SIZE),Math.floorDiv(p.z,CHUNK_SIZE))?.generated != true) continue
            val current = world.blockAt(p.x,p.y,p.z)
            if (FarmShowcasePlants.sample(current)?.first != plant.crop) { iterator.remove(); continue }
            // Unsupported plants are normally removed by the edit path. This also repairs old diffs.
            if (world.blockAt(p.x,p.y-1,p.z) != FarmSoil.FARMLAND) {
                world.setBlock(p.x,p.y,p.z,AIR); rebuild(p.x,p.y,p.z); iterator.remove(); continue
            }
            grow(plant)
            val next = FarmShowcasePlants.id(plant.crop,stage(plant))
            if (current != next) { world.setBlock(p.x,p.y,p.z,next); rebuild(p.x,p.y,p.z) }
        }
    }
    @Synchronized fun plant(x: Int, y: Int, z: Int, crop: Int): Boolean {
        if (crop !in FarmItems.crops.indices || world.blockAt(x,y,z) != AIR || world.blockAt(x,y-1,z) != FarmSoil.FARMLAND) return false
        // Tall plants need headroom, but adjacent crops may share their spreading leaves.
        if (world.blockAt(x,y+1,z) != AIR || world.blockAt(x,y+2,z) != AIR) return false
        val p = Position(x,y,z)
        plants[p] = Plant(p,crop,clockMs); cursor = null
        world.setBlock(x,y,z,FarmShowcasePlants.id(crop,0)); rebuild(x,y,z)
        return true
    }
    @Synchronized fun nearby(x: Double, y: Double, z: Double, reach: Double): List<Plant> =
        plants.values.filter { val p=it.position
            kotlin.math.abs(p.x+.5-x) <= reach+1 && kotlin.math.abs(p.z+.5-z) <= reach+1 &&
                p.y <= y+reach && p.y+3 >= y-reach && loaded(p)
        }
    @Synchronized fun harvest(x: Int, y: Int, z: Int, uproot: Boolean): List<Pair<Short,Int>>? {
        val p=Position(x,y,z)
        // Mature crops in generated gardens become ordinary tracked plants on first harvest.
        val plant=plants[p] ?: run {
            val sample=FarmShowcasePlants.sample(world.blockAt(x,y,z)) ?: return null
            if(sample.second != 4 || world.blockAt(x,y-1,z) != FarmSoil.FARMLAND) return null
            Plant(p,sample.first,clockMs,FarmItems.durationMs(sample.first)).also { plants[p]=it; cursor=null }
        }
        grow(plant)
        if (FarmShowcasePlants.sample(world.blockAt(x,y,z))?.first != plant.crop) {
            plants.remove(p); cursor=null; return null
        }
        val mature = stage(plant) == 4
        if (!mature && !uproot) return emptyList()
        if (mature && !uproot && FarmItems.regrows(plant.crop)) {
            plant.growthMs=FarmItems.durationMs(plant.crop)/2
            plant.checkedAt=clockMs
            world.setBlock(x,y,z,FarmShowcasePlants.id(plant.crop,2))
        } else {
            plants.remove(p); cursor=null; world.setBlock(x,y,z,AIR)
        }
        rebuild(x,y,z)
        return if (mature) listOf(FarmItems.produce(plant.crop) to 2, FarmItems.seed(plant.crop) to 1)
            else listOf(FarmItems.seed(plant.crop) to 1)
    }

    @Synchronized fun fertilize(x: Int,y: Int,z: Int): Boolean {
        val plant=plants[Position(x,y,z)] ?: return false
        if (stage(plant)>=4 || !loaded(plant.position)) return false
        if (FarmShowcasePlants.sample(world.blockAt(x,y,z))?.first != plant.crop) return false
        plant.growthMs=(plant.growthMs+FarmItems.durationMs(plant.crop)/4).coerceAtMost(FarmItems.durationMs(plant.crop))
        world.setBlock(x,y,z,FarmShowcasePlants.id(plant.crop,stage(plant)))
        rebuild(x,y,z)
        return true
    }
}
