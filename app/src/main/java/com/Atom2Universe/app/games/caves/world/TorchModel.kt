package com.Atom2Universe.app.games.caves.world

/** Shared geometry for rendering, picking and the flame's light origin.
 * Metadata: 0 = floor (including old saves), 1..4 = outward +X, -X, +Z, -Z.
 */
internal object TorchModel {
    data class Point(val x: Float, val y: Float, val z: Float)
    data class Part(val half: Float, val bottom: Float, val top: Float, val material: Int)
    val parts = arrayOf(
        Part(.055f, 0f, .53f, 0),
        Part(.075f, .43f, .56f, 1),
        Part(.082f, .56f, .66f, 2),
        Part(.058f, .66f, .75f, 3),
        Part(.026f, .75f, .82f, 3),
    )
    val faces = arrayOf(
        intArrayOf(2, 3, 7, 6), intArrayOf(4, 5, 1, 0),
        intArrayOf(5, 7, 3, 1), intArrayOf(0, 2, 6, 4),
        intArrayOf(4, 6, 7, 5), intArrayOf(1, 3, 2, 0),
    )

    fun orientation(nx: Int, ny: Int, nz: Int): Byte = when {
        ny < 0 -> -1 // No ceiling mount.
        nx > 0 -> 1
        nx < 0 -> 2
        nz > 0 -> 3
        nz < 0 -> 4
        else -> 0
    }

    fun normal(meta: Byte): Pair<Int, Int> = when (meta.toInt()) {
        1 -> 1 to 0
        2 -> -1 to 0
        3 -> 0 to 1
        4 -> 0 to -1
        else -> 0 to 0
    }

    fun point(meta: Byte, x: Float, y: Float, z: Float): Point {
        val (nx, nz) = normal(meta)
        if (nx == 0 && nz == 0) return Point(.5f + x, y, .5f + z)
        // Rotate 25 degrees away from the wall, with the foot touching its face.
        val along = nx * x + nz * z
        val shift = along * (.906308f - 1f) + y * .422618f
        return Point(.5f - nx * .44f + x + nx * shift,
            .20f + y * .906308f - along * .422618f,
            .5f - nz * .44f + z + nz * shift)
    }

    fun flame(meta: Byte) = point(meta, 0f, .67f, 0f)

    fun vertices(meta: Byte, part: Part, padding: Float = 0f): Array<Point> = Array(8) { i ->
        point(meta, if (i and 1 == 0) -part.half - padding else part.half + padding,
            if (i and 2 == 0) part.bottom - padding else part.top + padding,
            if (i and 4 == 0) -part.half - padding else part.half + padding)
    }

    fun intersects(meta: Byte, x: Double, y: Double, z: Double,
        dx: Double, dy: Double, dz: Double, entry: Double, exit: Double): Boolean {
        val (nx, nz) = normal(meta)
        val wall = nx != 0 || nz != 0
        val px = x - (.5 - nx * .44); val py = y - if (wall) .20 else 0.0
        val pz = z - (.5 - nz * .44)
        fun inverse(a: Double, b: Double, c: Double): DoubleArray {
            if (!wall) return doubleArrayOf(a, b, c)
            val along = nx * a + nz * c
            val shift = along * (.906308 - 1.0) - b * .422618
            return doubleArrayOf(a + nx * shift, b * .906308 + along * .422618, c + nz * shift)
        }
        val origin = inverse(px, py, pz); val direction = inverse(dx, dy, dz)
        return parts.any { part ->
            var near = entry; var far = exit
            for (axis in 0..2) {
                val lo = if (axis == 1) part.bottom.toDouble() else -part.half.toDouble()
                val hi = if (axis == 1) part.top.toDouble() else part.half.toDouble()
                val d = direction[axis]; val o = origin[axis]
                if (kotlin.math.abs(d) < 1e-9) {
                    if (o < lo || o > hi) return@any false
                } else {
                    val a = (lo - o) / d; val b = (hi - o) / d
                    near = maxOf(near, minOf(a, b)); far = minOf(far, maxOf(a, b))
                    if (near > far) return@any false
                }
            }
            near <= far
        }
    }

    fun highlight(meta: Byte, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float): FloatArray {
        val out = ArrayList<Float>()
        for (part in parts) {
            val vertices = vertices(meta, part, .004f)
            for (face in faces) for (corner in intArrayOf(0, 1, 2, 0, 2, 3)) {
                val p = vertices[face[corner]]
                out.addAll(listOf(x + p.x, y + p.y, z + p.z, r, g, b))
            }
        }
        return out.toFloatArray()
    }
}
