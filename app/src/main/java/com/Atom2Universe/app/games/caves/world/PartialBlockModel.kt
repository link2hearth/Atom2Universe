package com.Atom2Universe.app.games.caves.world

/** Stairs: bits 0..1 ascend +Z, +X, -Z, -X; bit 2 selects the upper half for both shapes. */
internal object PartialBlockModel {
    data class Box(val x: Float, val y: Float, val z: Float,
                   val width: Float = .5f, val height: Float = .5f, val depth: Float = .5f)
    data class Face(val direction: Int, val vertices: Array<FloatArray>)
    data class Hit(val distance: Double, val nx: Int, val ny: Int, val nz: Int)
    private val normals = arrayOf(intArrayOf(0,1,0), intArrayOf(0,-1,0),
        intArrayOf(1,0,0), intArrayOf(-1,0,0), intArrayOf(0,0,1), intArrayOf(0,0,-1))
    // Four horizontal quarter cells, shared by straight, inner and outer stairs.
    private fun straightMask(meta: Byte): Int = when (meta.toInt() and 3) {
        0 -> 12; 1 -> 10; 2 -> 3; else -> 5
    }
    private val dx = intArrayOf(0, 1, 0, -1)
    private val dz = intArrayOf(1, 0, -1, 0)

    /** Neighbor-driven corners, never persisted: removing a neighbor restores the straight step. */
    fun connectedMask(meta: Byte, neighbor: (Int, Int) -> Byte?): Int {
        val direction = meta.toInt() and 3
        val half = meta.toInt() and 4
        val own = straightMask(meta)
        fun compatible(value: Byte?) = value != null && value.toInt() and 4 == half
        fun sameAt(x: Int, z: Int): Boolean {
            val value = neighbor(x, z)
            return compatible(value) && value!!.toInt() and 3 == direction
        }
        val front = neighbor(dx[direction], dz[direction])
        if (compatible(front)) {
            val other = front!!.toInt() and 3
            if ((other and 1) != (direction and 1) && !sameAt(-dx[other], -dz[other]))
                return own and straightMask(front)
        }
        val back = neighbor(-dx[direction], -dz[direction])
        if (compatible(back)) {
            val other = back!!.toInt() and 3
            if ((other and 1) != (direction and 1) && !sameAt(dx[other], dz[other]))
                return own or straightMask(back)
        }
        return own
    }

    private val boxes = Array(34) { shape ->
        buildList {
            val half = if (shape >= 32) shape - 32 else shape and 1
            val mask = if (shape >= 32) 0 else shape shr 1
            for (x in 0..1) for (y in 0..1) for (z in 0..1)
                if (y == half || mask and (1 shl (x + 2 * z)) != 0)
                    add(Box(x * .5f, y * .5f, z * .5f))
        }
    }
    private fun index(meta: Byte, slab: Boolean, mask: Int) =
        if (slab) 32 + ((meta.toInt() shr 2) and 1)
        else 2 * (if (mask < 0) straightMask(meta) else mask) + ((meta.toInt() shr 2) and 1)
    private val lowBoxes = java.util.concurrent.ConcurrentHashMap<Float, List<Box>>()
    fun boxes(meta: Byte, slab: Boolean = false, height: Float = 1f, mask: Int = -1) =
        if (height < 1f) lowBoxes.getOrPut(height) { listOf(Box(0f, 0f, 0f, 1f, height, 1f)) }
        else boxes[index(meta, slab, mask)]
    private val surfaces = Array(34) { meta ->
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
    private val lowSurfaces = java.util.concurrent.ConcurrentHashMap<Float, List<Face>>()
    fun faces(meta: Byte, slab: Boolean = false, height: Float = 1f, mask: Int = -1): List<Face> =
        if (height >= 1f) surfaces[index(meta, slab, mask)] else lowSurfaces.getOrPut(height) {
            val corners = Array(8) { i -> floatArrayOf(if (i and 1 == 0) 0f else 1f,
                if (i and 2 == 0) 0f else height, if (i and 4 == 0) 0f else 1f) }
            normals.indices.map { f -> Face(f, TorchModel.faces[f].map { corners[it] }.toTypedArray()) }
        }

    /** Does a boundary expose any empty area? Also works with recessed soil heights. */
    fun hasOpenBoundary(cells: List<Box>, face: Int): Boolean {
        val covered = cells.sumOf { b ->
            when (face) {
                0 -> if (b.y + b.height == 1f) (b.width * b.depth).toDouble() else 0.0
                1 -> if (b.y == 0f) (b.width * b.depth).toDouble() else 0.0
                2 -> if (b.x + b.width == 1f) (b.height * b.depth).toDouble() else 0.0
                3 -> if (b.x == 0f) (b.height * b.depth).toDouble() else 0.0
                4 -> if (b.z + b.depth == 1f) (b.width * b.height).toDouble() else 0.0
                else -> if (b.z == 0f) (b.width * b.height).toDouble() else 0.0
            }
        }
        return covered < .99999
    }

    fun intersect(meta: Byte, x: Double, y: Double, z: Double,
        dx: Double, dy: Double, dz: Double, reach: Double, slab: Boolean = false, height: Float = 1f, mask: Int = -1): Hit? {
        var best: Hit? = null
        val origin = doubleArrayOf(x,y,z); val dir = doubleArrayOf(dx,dy,dz)
        for (b in boxes(meta, slab, height, mask)) {
            val low = floatArrayOf(b.x,b.y,b.z)
            val size = floatArrayOf(b.width, b.height, b.depth)
            var near = Double.NEGATIVE_INFINITY; var far = reach; var axis = 0; var sign = 0
            for (a in 0..2) {
                if (kotlin.math.abs(dir[a]) < 1e-12) {
                    if (origin[a] < low[a] || origin[a] > low[a]+size[a]) far = Double.NEGATIVE_INFINITY
                } else {
                    val t0 = (low[a]-origin[a])/dir[a]; val t1 = (low[a]+size[a]-origin[a])/dir[a]
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
