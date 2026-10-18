package com.Atom2Universe.app.games.caves.render

/**
 * Cotton-like pixel clouds seen from below: overlapping round puffs with a bright core and a
 * blue-grey rim, painted back to front so each puff edge separates it from the one behind.
 * Built once as flat rectangles (row runs of the same tone), so the batch needs no texture.
 */
internal object CloudShapes {
    const val CELL = 3.2f
    private const val COLS = 30
    private const val ROWS = 18
    const val TONES = 4

    /** Whites to blue-greys; the renderer greys them further as the sky closes. */
    val tones = intArrayOf(0xFFFFFF, 0xEDF3F9, 0xD2DFEB, 0xB0C4D8)

    // cx, cz, rx, rz in cells; later puffs sit in front of earlier ones.
    private val puffs = arrayOf(
        floatArrayOf(15f,12f,9f,5f, 8f,10f,7f,5f, 16f,7f,8f,6f, 23f,11f,6f,4.5f, 27f,10f,3f,2.5f),
        floatArrayOf(15f,13f,10f,4f, 6f,9f,5f,4f, 13f,8f,7f,6f, 21f,9f,7f,5.5f, 26f,11f,4f,3f),
        floatArrayOf(5f,13f,4f,3f, 14f,5f,5f,4f, 19f,10f,9f,6f, 9f,9f,8f,6f, 25f,12f,4f,3.5f)
    )

    /** Per variant: x0, x1, z0, z1 (world units, centred) then tone, five floats per rectangle. */
    val variants: Array<FloatArray> = Array(puffs.size) { build(it) }
    val maxRects: Int = variants.maxOf { it.size / 5 }

    private fun hash(x: Int, y: Int, salt: Int): Int {
        var h = x * 374761393 xor (y * 668265263) xor (salt * 1274126177)
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7fffffff
    }

    private fun build(variant: Int): FloatArray {
        val grid = IntArray(COLS * ROWS) { -1 }
        val list = puffs[variant]
        for (p in 0 until list.size / 4) {
            val cx = list[p * 4]; val cz = list[p * 4 + 1]; val rx = list[p * 4 + 2]; val rz = list[p * 4 + 3]
            for (z in 0 until ROWS) for (x in 0 until COLS) {
                val dx = (x + .5f - cx) / rx; val dz = (z + .5f - cz) / rz
                val jitter = (hash(x / 2, z / 2, variant * 7 + p) % 5 - 2) * .07f
                val d = dx * dx + dz * dz + jitter
                if (d >= 1f) continue
                // The underside of a puff is shadowed: shift the tone toward blue on the near side.
                val shaded = d + if (dz > 0f) .14f else 0f
                grid[z * COLS + x] = when {
                    shaded < .25f -> 0
                    shaded < .55f -> 1
                    shaded < .82f -> 2
                    else -> 3
                }
            }
        }
        val out = ArrayList<Float>()
        for (z in 0 until ROWS) {
            var x = 0
            while (x < COLS) {
                val tone = grid[z * COLS + x]
                if (tone < 0) { x++; continue }
                val start = x
                while (x < COLS && grid[z * COLS + x] == tone) x++
                out.add((start - COLS / 2f) * CELL); out.add((x - COLS / 2f) * CELL)
                out.add((z - ROWS / 2f) * CELL); out.add((z + 1 - ROWS / 2f) * CELL)
                out.add(tone.toFloat())
            }
        }
        return out.toFloatArray()
    }
}
