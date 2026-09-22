package com.Atom2Universe.app.games.roguelike

/** One 96px composition split across four solid cells, with no false doorway. */
internal object LargeSceneryArt {
    fun draw(p: SceneryPixels, kind: SceneryKind, variant: Int) = with(p) {
        val ink = 0xFF304047L
        val wood = 0xFF967750L
        val pale = 0xFFCAB18AL
        val stone = 0xFF82918DL
        val light = 0xFFBBC6B1L
        // Broad footing visually joins all four occupied quadrants.
        oval(4, 68, 88, 24, 0xFF34443FL)
        when (kind) {
            SceneryKind.CARGO -> {
                rect(6, 16, 84, 68, 0xFF5A493BL)
                for (row in 0..1) for (col in 0..1) {
                    val x = 8 + col * 41; val y = 18 + row * 33
                    rect(x, y, 38, 31, wood)
                    for (i in 1..3) rect(x + i * 9, y + 2, 1, 27, 0xFF70543FL)
                    rect(x, y, 38, 3, pale); rect(x, y + 28, 38, 3, ink)
                    line(x + 3, y + 4, x + 34, y + 26, pale)
                    line(x + 3, y + 26, x + 34, y + 4, pale)
                }
                for (x in listOf(17, 74)) { rect(x, 12, 4, 74, 0xFF54656AL); rect(x, 12, 1, 74, light) }
            }
            SceneryKind.REACTOR -> {
                rect(7, 9, 82, 75, ink); rect(10, 11, 76, 69, 0xFF607986L)
                rect(10, 11, 76, 3, light)
                for (x in listOf(14, 68)) {
                    rect(x, 19, 14, 52, ink)
                    for (y in 23..64 step 8) rect(x + 2, y, 10, 3, 0xFF8CBAC0L)
                }
                rect(33, 17, 29, 55, ink); rect(37, 20, 21, 49, 0xFF477D8BL)
                for (y in 24..64 step 10) { rect(38, y, 19, 4, 0xFF8ED6CFL); rect(41, y, 4, 4, 0xFFD2E7C6L) }
                rect(28, 73, 40, 6, ink)
                for (x in 32..61 step 7) rect(x, 75, 3, 2, 0xFFCCAF79L)
            }
            SceneryKind.BOOKCASES -> {
                rect(6, 7, 84, 78, 0xFF59463EL)
                for (row in 0..3) {
                    val y = 12 + row * 17
                    for (i in 0..13) {
                        val x = 11 + i * 5
                        val color = when ((i + row + variant) % 3) { 0 -> 0xFF9F6464L; 1 -> 0xFF78988AL; else -> 0xFFB59B68L }
                        rect(x, y + i % 3, 4, 13 - i % 3, color)
                        rect(x + 1, y + 5, 2, 1, pale)
                    }
                    rect(8, y + 13, 80, 3, wood); rect(8, y + 13, 80, 1, pale)
                }
                for (x in listOf(6, 46, 86)) { rect(x, 7, 4, 78, wood); rect(x, 7, 1, 78, pale) }
                rect(5, 5, 86, 4, pale)
            }
            SceneryKind.ORE_VEIN -> {
                polygon(0xFF536469, 6, 78, 9, 28, 29, 8, 68, 12, 88, 31, 90, 80, 68, 87, 23, 85)
                polygon(0xFF83928B, 10, 29, 29, 8, 68, 12, 79, 28, 43, 23, 23, 43)
                for (i in 0..5) {
                    val x = 18 + i * 11; val y = 38 + i % 3 * 12
                    polygon(0xFF70A6AC, x, y + 20, x - 3, y, x + 5, y - 10, x + 10, y + 3, x + 7, y + 21)
                    line(x + 4, y - 7, x + 4, y + 17, 0xFFB5D8CAL)
                }
                line(33, 29, 40, 40, ink); line(40, 40, 34, 54, ink)
            }
            SceneryKind.OAK -> {
                rect(39, 34, 20, 50, 0xFF715B48L)
                polygon(0xFF715B48, 17, 86, 40, 67, 58, 67, 80, 86, 51, 80, 36, 86)
                rect(42, 42, 4, 33, wood)
                oval(5, 9, 86, 62, 0xFF3E6751L)
                oval(10, 8, 52, 44, 0xFF6A9165L); oval(39, 13, 46, 42, 0xFF789F78L)
                oval(18, 7, 37, 21, 0xFF9BAE77L)
                for (i in 0..8) rect(16 + i * 8, 34 + i % 3 * 9, 6, 3, 0xFF527B59L)
            }
            SceneryKind.HAYSTACK -> {
                rect(8, 28, 80, 55, 0xFF9F824EL)
                for (row in 0..1) for (col in 0..2) {
                    val x = 9 + col * 26; val y = 29 + row * 27
                    rect(x, y, 25, 25, 0xFFC0A363L); rect(x, y, 25, 3, 0xFFDDC38AL)
                    for (dy in 7..22 step 5) rect(x + 2, y + dy, 21, 1, 0xFFA78B53L)
                    rect(x + 11, y, 3, 25, 0xFF75664CL)
                }
                rect(23, 12, 50, 16, 0xFFC0A363L); rect(24, 12, 48, 2, pale)
                for (x in listOf(35, 59)) rect(x, 12, 3, 16, 0xFF75664CL)
            }
            SceneryKind.BANQUET_TABLE -> {
                for (x in listOf(12, 77)) rect(x, 28, 7, 57, 0xFF604B3DL)
                rect(5, 13, 86, 64, 0xFF604B3DL); rect(7, 14, 82, 59, wood)
                for (y in 17..69 step 9) rect(8, y, 80, 1, pale)
                rect(37, 14, 23, 59, 0xFF976673L); rect(38, 14, 2, 59, pale)
                for (x in listOf(14, 67)) for (y in listOf(22, 48)) {
                    oval(x, y, 15, 12, light); oval(x + 3, y + 2, 9, 7, 0xFF88938AL)
                }
                rect(46, 31, 5, 17, pale); rect(47, 26, 3, 5, 0xFFE3BB7FL)
            }
            SceneryKind.MEMORIAL -> {
                rect(7, 66, 82, 19, ink); rect(9, 66, 78, 14, stone); rect(9, 66, 78, 3, light)
                rect(18, 50, 60, 15, stone); rect(18, 50, 60, 3, light)
                rect(28, 10, 40, 40, 0xFF657B80L); rect(28, 10, 40, 3, light)
                rect(31, 14, 4, 34, stone); rect(39, 18, 20, 24, ink)
                for (y in 22..36 step 5) rect(42, y, 14, 1, pale)
                rect(25, 6, 46, 4, light)
                for (x in listOf(12, 80)) { rect(x, 51, 4, 13, stone); rect(x, 49, 4, 2, pale) }
            }
            else -> Unit
        }
    }
}
