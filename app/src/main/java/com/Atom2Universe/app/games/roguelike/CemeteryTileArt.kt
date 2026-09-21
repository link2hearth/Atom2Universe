package com.Atom2Universe.app.games.roguelike

import kotlin.math.abs

/** Cemetery pixel art, independent of Android so the actual tiles can be rendered for visual QA.
 * All detail is rasterized at 48 px before enlargement. N/E/S/W bits describe blocking neighbours.
 */
internal class CemeteryTileArt {
    companion object { const val SIZE = 48 }
    private var side = SIZE
    private var pixels = IntArray(SIZE * SIZE)
    private val ink = 0xFF263638.toInt()
    private val shadow = 0xFF304440.toInt()
    private val soil = 0xFF647064.toInt()
    private val earth = 0xFF737A69.toInt()
    private val grass = 0xFF4B6756.toInt()
    private val moss = 0xFF6D8361.toInt()
    private val paleMoss = 0xFF8A9871.toInt()
    private val stone = 0xFF899A96.toInt()
    private val stoneDark = 0xFF596E72.toInt()
    private val light = 0xFFB8BEB0.toInt()
    private val warm = 0xFFC6AE79.toInt()
    private val iron = 0xFF344A50.toInt()

    private fun hash(x: Int, y: Int, salt: Int = 0): Int {
        var n = x * 374761393 + y * 668265263 + salt * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and Int.MAX_VALUE
    }
    private fun dot(x: Int, y: Int, color: Int) {
        if (x in 0 until side && y in 0 until side) pixels[y * side + x] = color
    }
    private fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y.coerceAtLeast(0) until (y + h).coerceAtMost(side))
            for (xx in x.coerceAtLeast(0) until (x + w).coerceAtMost(side)) dot(xx, yy, color)
    }
    private fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var error = dx + dy
        while (true) {
            dot(x, y, color)
            if (x == x1 && y == y1) break
            val twice = 2 * error
            if (twice >= dy) { error += dy; x += sx }
            if (twice <= dx) { error += dx; y += sy }
        }
    }
    private fun oval(x: Int, y: Int, rx: Int, ry: Int, color: Int) {
        for (yy in -ry..ry) for (xx in -rx..rx)
            if (xx * xx * ry * ry + yy * yy * rx * rx <= rx * rx * ry * ry) dot(x + xx, y + yy, color)
    }
    private fun grassTuft(x: Int, y: Int, seed: Int) {
        line(x, y, x - 2, y - 3, grass)
        line(x + 1, y, x + 1, y - 4 - seed % 3, moss)
        line(x + 2, y, x + 4, y - 2, paleMoss)
    }

    fun render(tx: Int, ty: Int, wall: Boolean, stairs: Boolean, neighbours: Int): IntArray {
        side = SIZE
        pixels = IntArray(SIZE * SIZE)
        val seed = hash(tx, ty)
        ground(tx, ty, wall, neighbours)
        if (stairs) cryptEntrance() else if (wall) {
            // Three gravestone silhouettes, family plots, sculpture and vegetation.
            when (seed % 12) {
                0 -> grave(seed, 0)
                1 -> grave(seed, 3)
                2 -> grave(seed, 1)
                3 -> grave(seed, 2)
                4 -> sarcophagus(seed)
                5 -> grave(seed, 2)
                6 -> deadTree(seed)
                7 -> cypress(seed)
                8 -> familyGraves(seed)
                9 -> angel(seed)
                10 -> ironEnclosure(seed)
                else -> brokenGrave(seed)
            }
        }
        return pixels
    }

    private fun ground(tx: Int, ty: Int, wall: Boolean, neighbours: Int) {
        val seed = hash(tx, ty)
        val paved = !wall && hash(tx / 3, ty / 3, 3) % 4 == 0
        val gravel = !wall && !paved && seed % 5 == 0
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = hash(tx * SIZE + x, ty * SIZE + y, 1)
            val edge = (neighbours and 1 != 0 && y < 3 + hash(tx * SIZE + x, ty, 2) % 4) ||
                (neighbours and 2 != 0 && x > 43 - hash(tx, ty * SIZE + y, 2) % 4) ||
                (neighbours and 4 != 0 && y > 43 - hash(tx * SIZE + x, ty, 2) % 4) ||
                (neighbours and 8 != 0 && x < 3 + hash(tx, ty * SIZE + y, 2) % 4)
            dot(x, y, if (wall || edge) {
                when (n % 53) { 0 -> moss; 1, 2 -> shadow; else -> grass }
            } else when (n % 29) { 0 -> earth; 1 -> 0xFF58665D.toInt(); else -> if (gravel) 0xFF6E7569.toInt() else soil })
        }
        if (paved) {
            for (row in 0..3) for (col in -1..2) {
                val x = col * 20 + (row % 2) * 10 + 2
                val y = row * 13 + 1
                rect(x, y, 17, 10, 0xFF76817A.toInt())
                rect(x + 1, y, 15, 1, 0xFF929A89.toInt())
                rect(x + 1, y + 9, 16, 1, 0xFF4E615C.toInt())
                if (hash(tx + col, ty + row) % 3 == 0) {
                    line(x + 7, y, x + 9, y + 4, soil)
                    line(x + 9, y + 4, x + 6, y + 8, soil)
                    rect(x + 1, y + 7, 3, 2, moss)
                }
            }
        } else if (!wall && seed % 9 == 0) {
            oval(24, 28, 13, 6, shadow)
            oval(24, 27, 11, 4, 0xFF607C7D.toInt())
            line(15, 25, 23, 25, 0xFF96A9A0.toInt())
            line(24, 29, 31, 29, 0xFF7E9892.toInt())
            dot(32, 26, light)
        }
        repeat(if (wall) 6 else if (gravel) 28 else 5) { i ->
            val x = hash(tx, ty, i + 10) % 44 + 2
            val y = hash(tx, ty, i + 40) % 44 + 2
            if (wall) grassTuft(x, y, i) else {
                rect(x, y, 2, 1, earth)
                dot(x + 1, y + 1, shadow)
                if (seed % 4 == 0) dot(x - 1, y, 0xFFAD9570.toInt())
            }
        }
        if (!wall && !paved && seed % 7 == 0) {
            line(5, 36, 15, 32, 0xFF596053.toInt())
            line(10, 34, 10, 30, 0xFF596053.toInt())
            dot(16, 34, warm)
        }
    }

    private fun plinth(x: Int, y: Int, w: Int) {
        oval(x + w / 2 + 2, y + 3, w / 2 + 3, 4, shadow)
        rect(x, y, w, 4, stoneDark)
        rect(x, y, w, 1, light)
        rect(x + 2, y - 2, w - 4, 2, stone)
    }
    private fun inscription(x: Int, y: Int, width: Int) {
        rect(x, y, width, 1, stoneDark)
        rect(x + 2, y + 3, width - 4, 1, stoneDark)
        dot(x, y + 3, stoneDark)
    }
    private fun flowers(x: Int, y: Int, seed: Int) {
        line(x, y, x + 2, y - 4, moss)
        line(x, y, x - 3, y - 3, grass)
        rect(x, y - 5, 3, 2, if (seed % 2 == 0) 0xFFB78D86.toInt() else warm)
        dot(x + 1, y - 5, light)
        rect(x - 4, y - 4, 2, 2, 0xFF94798B.toInt())
    }
    private fun grave(seed: Int, shape: Int) {
        oval(25, 34, 16, 8, shadow)
        rect(12, 27, 23, 11, 0xFF536552.toInt())
        rect(14, 28, 18, 8, 0xFF71816A.toInt())
        plinth(10, 31, 27)
        if (shape == 3) {
            rect(18, 10, 14, 21, ink)
            for (i in 0..6) rect(24 - i, 3 + i, i * 2 + 1, 1, stone)
            rect(19, 10, 11, 20, stone)
            rect(19, 10, 2, 19, light)
            rect(28, 10, 2, 20, stoneDark)
            inscription(22, 15, 6)
            line(28, 22, 24, 29, grass)
            rect(26, 21, 4, 2, moss)
            rect(24, 25, 4, 2, paleMoss)
        } else if (shape == 1) {
            rect(21, 5, 7, 26, ink)
            rect(14, 12, 21, 7, ink)
            rect(22, 6, 4, 25, stone)
            rect(15, 13, 18, 4, stone)
            line(22, 6, 22, 28, light)
            line(15, 13, 32, 13, light)
            dot(26, 18, stoneDark)
        } else {
            rect(14, 12, 21, 20, ink)
            if (shape == 0) {
                oval(24, 13, 10, 7, ink)
                oval(23, 13, 8, 5, light)
            } else {
                rect(17, 8, 15, 6, ink)
                rect(19, 6, 11, 3, stoneDark)
            }
            rect(15, 13, 18, 17, stone)
            rect(15, 13, 2, 16, light)
            rect(31, 13, 2, 17, stoneDark)
            rect(19, 16, 10, 10, stoneDark)
            rect(20, 17, 8, 8, 0xFF9EAAA0.toInt())
            inscription(21, 19, 6)
            line(28, 10, 26, 14, stoneDark)
            line(26, 14, 28, 16, stoneDark)
        }
        rect(17, 29, 6, 2, moss)
        dot(18, 28, paleMoss)
        grassTuft(9, 38, seed)
        if (seed % 3 != 0) flowers(29, 39, seed)
    }
    private fun sarcophagus(seed: Int) {
        oval(25, 36, 19, 6, shadow)
        rect(6, 16, 35, 21, ink)
        rect(7, 18, 33, 17, stoneDark)
        rect(8, 13, 29, 16, stone)
        rect(8, 13, 29, 2, light)
        rect(9, 16, 2, 10, light)
        rect(11, 17, 22, 9, stoneDark)
        rect(12, 18, 20, 7, 0xFF9AA69C.toInt())
        rect(21, 18, 2, 7, stoneDark)
        rect(18, 20, 8, 2, stoneDark)
        for (x in 11..35 step 8) { rect(x, 30, 3, 4, ink); dot(x, 30, light) }
        rect(9, 26, 6, 3, moss)
        line(30, 14, 28, 18, stoneDark)
        flowers(35, 39, seed)
    }
    /** One continuous building, sampled into four map cells by the map renderer. */
    fun renderMausoleum(): IntArray {
        side = SIZE * 4
        pixels = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            val n = hash(x, y, 81)
            dot(x, y, when (n % 67) { 0 -> moss; 1 -> shadow; else -> grass })
        }
        oval(99, 166, 88, 21, shadow)
        // Stone foundations and three worn steps.
        for (i in 0..2) {
            rect(10 + i * 5, 179 - i * 6, 172 - i * 10, 6, stoneDark)
            rect(10 + i * 5, 179 - i * 6, 172 - i * 10, 2, light)
        }
        rect(22, 69, 148, 96, ink)
        rect(25, 70, 141, 91, stone)
        rect(151, 72, 15, 89, stoneDark)
        // Mortar joins continue behind columns, with varied aged blocks.
        for (row in 0..6) for (col in -1..6) {
            val x = 27 + col * 23 + row % 2 * 11
            val y = 72 + row * 13
            val left = x.coerceAtLeast(25); val right = (x + 21).coerceAtMost(165)
            if (right > left) {
                rect(left, y, right - left, 11, if ((row + col) % 3 == 0) 0xFF94A19A.toInt() else stone)
                rect(left, y, right - left, 1, light)
                rect(left, y + 11, right - left, 1, stoneDark)
            }
        }
        // Pediment, nested mouldings and carved circular relief.
        for (y in 20..64) {
            val half = (y - 20) * 2
            rect(96 - half, y, half * 2 + 1, 1, stoneDark)
        }
        line(8, 64, 96, 20, light); line(96, 20, 184, 64, light)
        line(12, 64, 96, 23, light); line(96, 23, 180, 64, stone)
        line(27, 59, 96, 28, ink); line(96, 28, 165, 59, ink)
        line(34, 59, 96, 31, stone); line(96, 31, 158, 59, stone)
        oval(96, 47, 10, 10, ink); oval(96, 46, 8, 8, stone)
        rect(94, 40, 3, 13, light); rect(90, 44, 11, 3, light)
        rect(7, 64, 178, 5, light); rect(11, 69, 170, 4, stoneDark)
        for (x in 17..175 step 9) rect(x, 69, 4, 5, stone)
        // Recessed arched door, wrought iron bars and central lock.
        oval(96, 105, 27, 28, stoneDark)
        rect(69, 105, 55, 56, stoneDark)
        oval(96, 106, 21, 22, ink); rect(75, 106, 43, 55, ink)
        for (x in 78..115 step 6) {
            val top = 91 + abs(x - 96) / 2
            rect(x, top, 2, 68 - (top - 91), iron)
            rect(x, top, 1, 152 - top, stoneDark)
            dot(x, top - 1, light)
        }
        rect(76, 117, 41, 3, stoneDark); rect(76, 144, 41, 3, stoneDark)
        rect(94, 119, 6, 10, warm); dot(97, 122, ink)
        // Paired fluted columns with capitals and chipped bases.
        for (x in intArrayOf(35, 138)) {
            rect(x, 81, 17, 72, stoneDark)
            rect(x, 81, 13, 72, light)
            for (xx in x + 3..x + 12 step 4) rect(xx, 85, 1, 65, stone)
            rect(x - 4, 76, 25, 5, light); rect(x - 2, 81, 21, 3, stone)
            rect(x - 3, 152, 23, 6, stone); rect(x - 5, 158, 27, 5, light)
            dot(x + 1, 155, stoneDark)
        }
        // Ivy climbs the shaded side in separate leaves, never a flat green stripe.
        line(161, 159, 155, 88, grass)
        for (i in 0..13) {
            val x = 156 + i % 3 * 3; val y = 88 + i * 5
            rect(x, y, 4, 3, if (i % 3 == 0) paleMoss else moss)
            dot(x - 2, y + 2, grass)
        }
        line(29, 113, 33, 121, stoneDark); line(33, 121, 29, 131, stoneDark)
        for (i in 0..12) { rect(18 + i * 5, 174 + i % 3, 3, 2, moss) }
        lantern(57, 115); lantern(128, 115)
        line(59, 110, 59, 115, iron); line(130, 110, 130, 115, iron)
        flowers(57, 169, 2); flowers(126, 169, 3)
        // A raven on the ridge, and stone finials on either corner.
        rect(95, 14, 9, 4, ink); rect(102, 10, 5, 6, ink)
        line(95, 16, 90, 20, ink); dot(107, 12, warm)
        for (x in intArrayOf(15, 174)) { rect(x, 54, 4, 10, stone); oval(x + 2, 52, 4, 4, light) }
        return pixels
    }
    private fun lantern(x: Int, y: Int) {
        rect(x, y, 5, 8, iron); rect(x + 1, y + 2, 3, 4, warm)
        rect(x + 2, y + 2, 1, 3, 0xFFF0D6A0.toInt()); dot(x + 2, y - 1, iron)
    }
    private fun deadTree(seed: Int) {
        oval(24, 39, 18, 6, shadow)
        for (i in 0..3) {
            line(23 + i, 37, 21 + i, 15, 0xFF766D59.toInt())
            line(22 + i, 24, 10 + i, 15, 0xFF675F50.toInt())
            line(23 + i, 19, 34 + i, 11, 0xFF675F50.toInt())
        }
        line(21, 17, 18, 7, warm); line(18, 7, 22, 3, stoneDark)
        line(11, 16, 8, 6, stoneDark); line(11, 16, 3, 13, stoneDark)
        line(34, 13, 38, 4, stoneDark); line(34, 13, 44, 11, stoneDark)
        line(23, 25, 23, 36, warm)
        for (i in 0..4) line(23, 33, 9 + i * 7, 40 + i % 2, 0xFF746E56.toInt())
        rect(21, 27, 2, 4, ink)
        grassTuft(30, 40, seed)
        lantern(9, 20)
        line(11, 16, 11, 20, iron)
    }
    private fun cypress(seed: Int) {
        oval(25, 39, 17, 6, shadow)
        rect(22, 29, 5, 12, 0xFF756B55.toInt())
        for (y in 3..35) {
            val radius = 2 + (y - 3) / 3
            val edge = if (y % 6 < 2) 2 else 0
            rect(24 - radius + edge, y, radius * 2 - edge, 1, 0xFF365447.toInt())
            rect(24 - radius + edge, y, radius - edge, 1, 0xFF587660.toInt())
            if (y % 5 == 0) rect(24 - radius + 2, y, radius - 2, 1, moss)
        }
        grassTuft(15, 42, seed)
        rect(34, 36, 4, 3, 0xFF9E8C78.toInt()); dot(35, 35, warm)
    }
    private fun familyGraves(seed: Int) {
        oval(24, 35, 20, 7, shadow)
        for (i in 0..1) {
            val x = 7 + i * 22; val y = 11 + i * 7
            rect(x, y, 13, 19, ink); oval(x + 6, y, 6, 4, ink)
            rect(x + 1, y, 10, 18, stone)
            oval(x + 6, y, 4, 2, light)
            rect(x + 1, y + 1, 1, 15, light)
            inscription(x + 3, y + 5, 6)
            plinth(x - 2, y + 19, 17)
            rect(x + 7, y + 17, 4, 2, moss)
        }
        flowers(23, 40, seed)
    }
    private fun angel(seed: Int) {
        plinth(11, 39, 28)
        rect(16, 30, 17, 9, stoneDark); rect(17, 30, 14, 7, stone)
        inscription(21, 32, 7)
        for (i in 0..7) {
            line(22, 21 + i / 2, 8 + i, 8 + i * 2, stone)
            line(27, 21 + i / 2, 41 - i, 8 + i * 2, stoneDark)
        }
        line(8, 8, 9, 19, light); line(41, 8, 40, 19, stone)
        oval(24, 10, 4, 4, stone); rect(21, 15, 7, 13, stone)
        rect(20, 25, 10, 5, stone); line(22, 16, 21, 27, light)
        line(26, 19, 28, 27, stoneDark); dot(22, 9, light)
        grassTuft(11, 41, seed)
    }
    private fun ironEnclosure(seed: Int) {
        // A complete fenced grave plot: always a blocker, never a misleading open gate.
        rect(8, 13, 31, 26, shadow)
        rect(13, 18, 21, 18, moss)
        rect(17, 13, 13, 13, stoneDark); rect(18, 12, 10, 12, stone)
        inscription(20, 16, 7)
        for (y in intArrayOf(10, 36)) {
            rect(5, y + 2, 38, 2, iron)
            for (x in 6..42 step 6) {
                rect(x, y - 3, 2, 10, iron); dot(x, y - 4, light)
                line(x - 1, y - 1, x, y - 3, iron)
            }
        }
        for (x in intArrayOf(5, 42)) {
            rect(x, 8, 2, 34, iron); rect(x, 8, 1, 30, stoneDark)
        }
        flowers(24, 31, seed)
    }
    private fun brokenGrave(seed: Int) {
        plinth(8, 35, 30)
        rect(13, 22, 19, 13, stoneDark); rect(14, 22, 16, 11, stone)
        line(14, 22, 19, 25, light); line(19, 25, 25, 21, light)
        for (i in 0..10) rect(23 + i / 2, 9 + i, 10, 1, stone)
        line(24, 9, 29, 18, light)
        inscription(28, 13, 6)
        rect(9, 29, 4, 4, stone); dot(10, 29, light)
        rect(34, 31, 4, 3, stoneDark)
        grassTuft(18, 36, seed); grassTuft(34, 40, seed)
    }
    private fun cryptEntrance() {
        oval(24, 39, 22, 7, shadow)
        rect(4, 6, 40, 38, ink)
        rect(5, 6, 38, 3, stone)
        rect(5, 7, 2, 33, light); rect(41, 8, 2, 33, stoneDark)
        for (i in 0..5) {
            val x = 9 + i * 2; val y = 11 + i * 5
            rect(x, y, 30 - i * 4, 3, if (i < 3) stoneDark else iron)
            rect(x, y, 30 - i * 4, 1, if (i < 3) light else stoneDark)
        }
        rect(2, 40, 44, 4, stoneDark); rect(2, 40, 44, 1, light)
        lantern(1, 17); lantern(42, 17)
        rect(4, 8, 3, 4, moss); rect(39, 35, 3, 5, moss)
        // Small gold chevrons identify the usable descent, unlike decorative mausoleums.
        line(19, 39, 24, 42, warm); line(24, 42, 29, 39, warm)
    }
}
