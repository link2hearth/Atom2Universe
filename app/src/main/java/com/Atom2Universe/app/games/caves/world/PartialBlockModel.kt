package com.Atom2Universe.app.games.caves.world

/** Stairs: bits 0..1 ascend +Z, +X, -Z, -X; bit 2 selects the upper half for both shapes. */
internal object PartialBlockModel {
    data class Box(val x: Float, val y: Float, val z: Float)
    data class Face(val direction: Int, val vertices: Array<FloatArray>)
    data class Hit(val distance: Double, val nx: Int, val ny: Int, val nz: Int)
    private val normals = arrayOf(intArrayOf(0,1,0), intArrayOf(0,-1,0),
        intArrayOf(1,0,0), intArrayOf(-1,0,0), intArrayOf(0,0,1), intArrayOf(0,0,-1))
    private val boxes = Array(10) { meta ->
        buildList {
            for (x in 0..1) for (y in 0..1) for (z in 0..1) {
                val base = y == if (meta >= 8) meta - 8 else if (meta and 4 == 0) 0 else 1
                val high = when (meta and 3) { 0 -> z == 1; 1 -> x == 1; 2 -> z == 0; else -> x == 0 }
                if (base || meta < 8 && high) add(Box(x*.5f, y*.5f, z*.5f))
            }
        }
    }
    private fun index(meta: Byte, slab: Boolean) = if (slab) 8 + ((meta.toInt() shr 2) and 1) else meta.toInt() and 7
    fun boxes(meta: Byte, slab: Boolean = false) = boxes[index(meta, slab)]
    private val surfaces = Array(10) { meta ->
        buildList {
            val cells = boxes[meta]
            for (b in cells) for ((f,n) in normals.withIndex()) {
                if (cells.any { it.x == b.x+n[0]*.5f && it.y == b.y+n[1]*.5f && it.z == b.z+n[2]*.5f }) continue
                val corners = Array(8) { i -> floatArrayOf(b.x+if(i and 1 == 0) 0f else .5f,
                    b.y+if(i and 2 == 0) 0f else .5f, b.z+if(i and 4 == 0) 0f else .5f) }
                add(Face(f, TorchModel.faces[f].map { corners[it] }.toTypedArray()))
            }
        }
    }
    fun faces(meta: Byte, slab: Boolean = false) = surfaces[index(meta, slab)]

    fun intersect(meta: Byte, x: Double, y: Double, z: Double,
        dx: Double, dy: Double, dz: Double, reach: Double, slab: Boolean = false): Hit? {
        var best: Hit? = null
        val origin = doubleArrayOf(x,y,z); val dir = doubleArrayOf(dx,dy,dz)
        for (b in boxes(meta, slab)) {
            val low = floatArrayOf(b.x,b.y,b.z)
            var near = Double.NEGATIVE_INFINITY; var far = reach; var axis = 0; var sign = 0
            for (a in 0..2) {
                if (kotlin.math.abs(dir[a]) < 1e-12) {
                    if (origin[a] < low[a] || origin[a] > low[a]+.5) far = Double.NEGATIVE_INFINITY
                } else {
                    val t0 = (low[a]-origin[a])/dir[a]; val t1 = (low[a]+.5-origin[a])/dir[a]
                    val entry = minOf(t0,t1)
                    if (entry > near) { near = entry; axis = a; sign = if(dir[a]>0) -1 else 1 }
                    far = minOf(far,maxOf(t0,t1))
                }
            }
            if (near <= far && far >= 0 && near <= reach) {
                val distance = maxOf(0.0,near)
                if (best == null || distance < best.distance) best = Hit(distance,
                    if(axis==0) sign else 0, if(axis==1) sign else 0, if(axis==2) sign else 0)
            }
        }
        return best
    }
}
