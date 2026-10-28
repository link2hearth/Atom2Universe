package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.toyboxracers.models.*
import kotlin.math.floor

/** One map object, independent of the voxel grid. Coordinates are local to the map. */
internal data class CaveDecor(val modelId: String, val x: Float, val y: Float, val z: Float,
    val scale: Float, val quarterTurns: Int = 0) {
    init {
        require(listOf(x, y, z, scale).all { it.isFinite() } && scale > 0f && scale <= 4f)
        require(quarterTurns in 0..3)
        require(modelId in CaveDecorModels.ids)
    }
    fun placement() = DecorPlacement(CaveDecorModels.get(modelId), x, y, z, quarterTurns, scale)
}

/** Reuse the original furniture geometry; only the plain desk is specific to Cave World. */
internal object CaveDecorModels {
    val ids = setOf("cave.desk", "office.computer", "office.pc_tower", "living.dining_chair",
        "living.sofa", "living.armchair", "living.coffee_table", "garage.storage_rack", "outdoor.family_car",
        "kitchen.fridge", "kitchen.oven", "kitchen.sink", "kitchen.counter", "kitchen.toaster", "kitchen.kettle",
        "living.dining_table", "living.tv_cabinet", "living.floor_lamp", "living.books", "living.plant",
        "garage.workbench", "garage.tool_chest", "garage.tires", "garage.toolbox", "garage.cone", "garage.crate",
        "outdoor.bench", "outdoor.planter", "outdoor.mailbox", "outdoor.hedge",
        "bedroom.double_bed", "bedroom.nightstand", "bedroom.table_lamp", "bedroom.wardrobe", "office.dresser",
        "bathroom.shower", "bathroom.bathtub", "bathroom.vanity", "bathroom.mirror", "bathroom.toilet",
        "bathroom.towel_rack", "bathroom.toilet_paper", "bathroom.washer", "bathroom.laundry_basket",
        "kitchen.microwave", "kitchen.fruit_bowl", "office.exercise_bike",
        "kawaii.robot", "kawaii.train", "kawaii.handheld_console", "kawaii.gamepad", "kawaii.arcade_cabinet")
    private val desk by lazy {
        val b = DecorBuilder().apply {
            legs(18f, 8f, 10f, .8f)
            box(0f, 10.5f, 0f, 20f, 1f, 10f, DecorPalette.WOOD)
            box(7f, 8f, 0f, 4f, 4f, 8f, DecorPalette.CREAM)
            handle(7f, 8f, 4.2f, 2f)
        }
        DecorModel("cave.desk", DecorRoom.OFFICE, b.parts.toList())
    }
    fun get(id: String): DecorModel {
        require(id in ids)
        if (id == "cave.desk") return desk
        val collection = when (id.substringBefore('.')) {
            "office" -> DecorExpansion.office
            "outdoor" -> DecorExpansion.outdoor
            "garage" -> DecorCatalog.garage
            "kitchen" -> DecorCatalog.kitchen + DecorExpansion.office
            "bathroom" -> DecorExpansion.bathroom
            "kawaii" -> DecorKawaiiCollection.toys + DecorKawaiiCollection.gaming +
                DecorPastelBatchThree.toys + DecorPastelBatchThree.gaming + DecorPastelBatchTwo.gaming
            "bedroom" -> DecorBedroom.all + DecorHouseFurniture.all
            else -> DecorCatalog.livingRoom
        }
        return collection.first { it.id == id }
    }
}

/** Static compound collision boxes, indexed by voxel. Empty space under furniture stays empty. */
internal class CaveDecorScene(objects: List<CaveDecor>) {
    val placements = objects.map { it.placement() }
    private data class Box(val x0: Double, val y0: Double, val z0: Double,
        val x1: Double, val y1: Double, val z1: Double) {
        fun overlaps(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double) =
            bx > x0 && ax < x1 && by > y0 && ay < y1 && bz > z0 && az < z1

        fun hits(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double): Boolean {
            // Same slab test per axis as before, written inline: a local function sharing lo/hi
            // allocated two boxed doubles on every call (lines of sight test thousands of voxels).
            var lo = 0.0; var hi = 1.0
            if (kotlin.math.abs(dx) < 1e-10) { if (!(x >= x0 && x <= x1)) return false }
            else {
                val t0 = (x0 - x) / dx; val t1 = (x1 - x) / dx
                lo = maxOf(lo, minOf(t0, t1)); hi = minOf(hi, maxOf(t0, t1))
                if (!(lo <= hi)) return false
            }
            if (kotlin.math.abs(dy) < 1e-10) { if (!(y >= y0 && y <= y1)) return false }
            else {
                val t0 = (y0 - y) / dy; val t1 = (y1 - y) / dy
                lo = maxOf(lo, minOf(t0, t1)); hi = minOf(hi, maxOf(t0, t1))
                if (!(lo <= hi)) return false
            }
            if (kotlin.math.abs(dz) < 1e-10) return z >= z0 && z <= z1
            val t0 = (z0 - z) / dz; val t1 = (z1 - z) / dz
            lo = maxOf(lo, minOf(t0, t1)); hi = minOf(hi, maxOf(t0, t1))
            return lo <= hi
        }
    }
    // Occupied voxels, sorted by packed key: a lookup is a binary search, with no MapPoint
    // allocated per tested voxel (sight lines, collisions and the NavGrid test thousands).
    private val cellKeys: LongArray
    private val cellBoxes: Array<Array<Box>>
    init {
        val cells = HashMap<Long, MutableList<Box>>()
        for (p in placements) for (s in p.solids) {
            val b = Box((s.x - s.width / 2).toDouble(), (s.y - s.height / 2).toDouble(),
                (s.z - s.depth / 2).toDouble(), (s.x + s.width / 2).toDouble(),
                (s.y + s.height / 2).toDouble(), (s.z + s.depth / 2).toDouble())
            for (x in cell(b.x0)..cell(b.x1 - 1e-6))
                for (y in cell(b.y0)..cell(b.y1 - 1e-6))
                    for (z in cell(b.z0)..cell(b.z1 - 1e-6))
                        key(x, y, z).takeIf { it >= 0 }?.let { cells.getOrPut(it) { ArrayList() }.add(b) }
        }
        cellKeys = cells.keys.toLongArray().also { it.sort() }
        cellBoxes = Array(cellKeys.size) { cells.getValue(cellKeys[it]).toTypedArray() }
    }
    private fun boxesAt(x: Int, y: Int, z: Int): Array<Box>? {
        val index = cellKeys.binarySearch(key(x, y, z))
        return if (index >= 0) cellBoxes[index] else null
    }
    fun occupied(x: Int, y: Int, z: Int) = cellKeys.binarySearch(key(x, y, z)) >= 0
    fun blocksSight(cx: Int, cy: Int, cz: Int, x: Double, y: Double, z: Double,
        dx: Double, dy: Double, dz: Double) = boxesAt(cx, cy, cz)?.any { it.hits(x,y,z,dx,dy,dz) } == true

    fun collides(x: Double, feet: Double, z: Double, height: Double, radius: Double): Boolean {
        if (cellKeys.isEmpty()) return false
        for (cx in cell(x-radius)..cell(x+radius)) for (cy in cell(feet)..cell(feet+height))
            for (cz in cell(z-radius)..cell(z+radius)) {
                if (boxesAt(cx,cy,cz)?.any {
                    it.overlaps(x-radius,feet,z-radius,x+radius,feet+height,z+radius)
                } == true) return true
            }
        return false
    }

    /** Used on the existing short projectile steps, so thin tabletops cannot be skipped. */
    fun hitsSegment(x: Double, y: Double, z: Double, endX: Double, endY: Double, endZ: Double): Boolean {
        if (cellKeys.isEmpty()) return false
        for (cx in cell(minOf(x,endX))..cell(maxOf(x,endX)))
            for (cy in cell(minOf(y,endY))..cell(maxOf(y,endY)))
                for (cz in cell(minOf(z,endZ))..cell(maxOf(z,endZ)))
                    if (blocksSight(cx,cy,cz,x,y,z,endX-x,endY-y,endZ-z)) return true
        return false
    }
    private fun cell(v: Double) = floor(v).toInt()
    /** Voxel packed on 3 × 21 bits; -1 (never stored) outside the representable range. */
    private fun key(x: Int, y: Int, z: Int): Long {
        if (x < -KEY_HALF || x >= KEY_HALF || y < -KEY_HALF || y >= KEY_HALF || z < -KEY_HALF || z >= KEY_HALF) return -1L
        return ((x + KEY_HALF).toLong() shl 42) or ((y + KEY_HALF).toLong() shl 21) or (z + KEY_HALF).toLong()
    }
    private companion object { const val KEY_HALF = 1 shl 20 }
}
