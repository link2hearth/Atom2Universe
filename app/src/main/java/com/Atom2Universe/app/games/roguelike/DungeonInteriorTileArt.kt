package com.Atom2Universe.app.games.roguelike

/** Connected blue-slate masonry, worn flagstones and quiet amber accents. */
internal class DungeonInteriorTileArt {
    companion object { const val SIZE = 48 }
    private var pixels = IntArray(SIZE * SIZE)
    private val ink = 0xFF202C38.toInt()
    private val mortar = 0xFF34404B.toInt()
    private val dark = 0xFF465564.toInt()
    private val stone = 0xFF657584.toInt()
    private val light = 0xFF8E9AA0.toInt()
    private val moss = 0xFF506B60.toInt()
    private val warm = 0xFFC7AA78.toInt()
    private fun hash(x: Int, y: Int, salt: Int = 0): Int {
        var n = x * 374761393 + y * 668265263 + salt * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and Int.MAX_VALUE
    }
    private fun dot(x: Int, y: Int, color: Int) {
        if (x in 0..47 && y in 0..47) pixels[y * SIZE + x] = color
    }
    private fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y.coerceAtLeast(0) until (y + h).coerceAtMost(SIZE))
            for (xx in x.coerceAtLeast(0) until (x + w).coerceAtMost(SIZE)) dot(xx, yy, color)
    }
    private fun line(x: Int, y: Int, dx: Int, dy: Int, length: Int, color: Int) {
        for (i in 0 until length) dot(x + dx * i, y + dy * i, color)
    }
    fun render(tx: Int, ty: Int, wall: Boolean, stairs: Boolean, neighbours: Int): IntArray {
        pixels = IntArray(SIZE * SIZE) { if (wall) mortar else ink }
        val seed = hash(tx, ty)
        if (wall) masonry(tx, ty, seed, neighbours) else floor(tx, ty, seed, neighbours)
        if (stairs) stairs()
        return pixels
    }
    private fun slab(x: Int, y: Int, w: Int, h: Int, seed: Int, wall: Boolean) {
        val base = if (wall) when (seed % 4) {
            0 -> 0xFF687988.toInt(); 1 -> 0xFF5E6E7E.toInt(); 2 -> 0xFF73818A.toInt(); else -> stone
        } else when (seed % 4) {
            0 -> 0xFF4A565F.toInt(); 1 -> 0xFF4E5E67.toInt(); 2 -> 0xFF56636B.toInt(); else -> 0xFF45515B.toInt()
        }
        rect(x, y, w, h, base)
        rect(x + 1, y, w - 2, 1, if (wall) light else stone)
        rect(x, y + 1, 1, h - 2, if (wall) stone else dark)
        rect(x + 1, y + h - 1, w - 1, 1, if (wall) dark else mortar)
        rect(x + w - 1, y + 1, 1, h - 1, if (wall) dark else mortar)
        if (seed % 4 == 0) {
            line(x + w / 2, y, -1, 1, 4, mortar)
            line(x + w / 2 - 3, y + 4, 1, 1, 3, mortar)
            dot(x + w / 2 - 1, y + 4, light)
        }
        if (seed % 5 == 0) { rect(x + 2, y + h - 3, 4, 2, moss); dot(x + 5, y + h - 4, moss) }
        dot(x + 4 + seed % (w - 6).coerceAtLeast(1), y + 3, if (wall) light else stone)
    }
    private fun masonry(tx: Int, ty: Int, seed: Int, mask: Int) {
        // Courses line up across adjacent blocking cells, including staggered vertical seams.
        for (row in 0..3) for (col in -1..2) {
            val x = col * 24 + row % 2 * 12
            slab(x + 1, row * 12 + 1, 23, 11, hash(tx * 2 + col, ty * 4 + row, 5), true)
        }
        // Exposed edges form one continuous rim, rather than a box around every tile.
        if (mask and 1 == 0) { rect(0, 0, 48, 3, light); rect(0, 3, 48, 2, stone) }
        if (mask and 8 == 0) { rect(0, 0, 3, 48, light); rect(3, 3, 2, 45, stone) }
        if (mask and 2 == 0) { rect(44, 0, 4, 48, ink); rect(42, 1, 2, 46, dark) }
        if (mask and 4 == 0) {
            rect(0, 34, 48, 14, ink)
            rect(1, 35, 46, 9, dark)
            rect(0, 34, 48, 2, light)
            rect(1, 44, 46, 2, mortar)
            for (x in 11..47 step 12) rect(x, 36, 1, 8, mortar)
        }
        when (seed % 10) {
            0 -> niche(false)
            1 -> niche(true)
            2 -> chains()
            3 -> {
                rect(14, 9, 20, 20, mortar); rect(16, 10, 16, 17, stone)
                rect(17, 10, 14, 1, light)
                line(24, 13, 1, 1, 6, light); line(29, 18, -1, 1, 6, light)
                line(24, 23, -1, -1, 6, dark); line(19, 18, 1, -1, 6, dark)
                dot(24, 18, warm)
            }
            4 -> {
                for (i in 0..6) { rect(9 + i * 3, 8 + i * 3, 3, 2, moss); dot(10 + i * 3, 7 + i * 3, stone) }
            }
            5 -> {
                line(9, 7, 1, 1, 8, ink); line(16, 15, -1, 1, 8, ink)
                line(12, 12, 1, 0, 9, dark)
            }
            else -> Unit
        }
        // End pillars only on exposed wall ends.
        if (mask == 1 || mask == 4 || mask == 0) {
            rect(3, 4, 5, 28, light); rect(7, 5, 3, 27, dark)
            rect(1, 3, 11, 3, light); rect(1, 31, 11, 3, stone)
        }
    }
    private fun niche(torch: Boolean) {
        rect(15, 10, 19, 22, dark); rect(17, 12, 15, 18, ink)
        rect(19, 8, 11, 4, stone); rect(20, 9, 9, 3, ink)
        rect(14, 31, 21, 2, light)
        if (torch) {
            rect(23, 21, 3, 10, 0xFF80674F.toInt())
            rect(21, 22, 7, 3, dark)
            rect(22, 14, 5, 8, 0xFFB8794F.toInt())
            rect(23, 12, 3, 8, 0xFFE4B76E.toInt())
            rect(24, 15, 2, 5, 0xFFF3DC9D.toInt())
            dot(25, 10, warm); dot(20, 15, 0xFFB8794F.toInt())
        } else {
            for (x in 20..30 step 4) { rect(x, 13, 1, 17, stone); dot(x, 13, light) }
            rect(18, 20, 13, 2, dark)
        }
    }
    private fun chains() {
        for (x in intArrayOf(18, 29)) {
            rect(x - 2, 8, 5, 3, ink); dot(x, 8, light)
            for (i in 0..4) {
                rect(x, 12 + i * 3, 3, 4, dark)
                dot(x, 12 + i * 3, light); dot(x + 2, 14 + i * 3, stone)
            }
            rect(x - 1, 27, 6, 4, ink); rect(x, 28, 4, 2, stone)
        }
    }
    private fun floor(tx: Int, ty: Int, seed: Int, mask: Int) {
        for (row in 0..2) for (col in -1..1) {
            val x = col * 32 + row % 2 * 16
            slab(x + 1, row * 16 + 1, 30, 14, hash(tx * 2 + col, ty * 3 + row), false)
        }
        // Low-contrast details belong to the ground, so they never resemble solid obstacles.
        when (seed % 11) {
            0 -> {
                rect(13, 19, 24, 10, mortar); rect(16, 17, 18, 13, mortar)
                rect(15, 20, 20, 7, 0xFF49646A.toInt())
                rect(18, 20, 9, 1, 0xFF758D8D.toInt()); rect(28, 25, 5, 1, stone)
            }
            1 -> {
                rect(13, 14, 22, 21, mortar); rect(14, 15, 20, 18, dark)
                for (x in 16..32 step 4) rect(x, 16, 2, 16, ink)
                rect(14, 23, 20, 2, mortar)
                dot(14, 15, stone); dot(33, 32, stone)
            }
            2 -> {
                line(11, 24, 1, 0, 20, dark)
                line(21, 14, 0, 1, 20, dark)
                line(12, 15, 1, 1, 18, dark)
                line(12, 32, 1, -1, 18, dark)
                dot(21, 24, stone)
            }
            3, 4 -> for (i in 0..5) {
                val x = 7 + hash(tx, ty, i) % 34; val y = 8 + hash(tx, ty, i + 9) % 30
                rect(x, y, 3 + i % 3, 2, stone); rect(x + 1, y + 2, 3, 1, mortar)
            }
            5 -> { line(12, 33, 1, -1, 20, mortar); line(23, 22, 1, 0, 9, mortar) }
            else -> Unit
        }
        if (mask and 1 != 0) { rect(0, 0, 48, 4, mortar); rect(0, 4, 48, 2, dark) }
        if (mask and 8 != 0) rect(0, 0, 3, 48, mortar)
        if (mask and 2 != 0) rect(46, 0, 2, 48, mortar)
        if (mask and 4 != 0) rect(0, 46, 48, 2, mortar)
        if (mask != 0 && seed % 3 == 0) {
            val x = if (mask and 8 != 0) 2 else 39
            for (i in 0..4) rect(x + i % 3, 7 + i * 3, 3, 2, moss)
        }
    }
    private fun stairs() {
        rect(5, 4, 38, 40, ink)
        for (i in 0..6) {
            val x = 8 + i * 2; val y = 8 + i * 5
            rect(x, y, 32 - i * 4, 4, if (i < 3) dark else mortar)
            rect(x, y, 32 - i * 4, 1, if (i < 4) light else stone)
        }
        rect(3, 4, 3, 40, stone); rect(42, 4, 3, 40, dark)
        line(19, 39, 1, 1, 5, warm); line(24, 43, 1, -1, 5, warm)
    }
}
