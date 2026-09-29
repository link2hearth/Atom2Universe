package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap

/** A complete door in one tile; each world cell samples half its height. */
internal object DoorTextures {
    fun style(def: BlockDef) = def.textureSide.substringAfterLast(':').toInt()

    /** Shared with the frame geometry: the handle backplate also has solid thickness. */
    fun transparent(style: Int, x: Int, y: Int): Boolean =
        window(style, x, y) && !mullion(style, x, y) &&
            !(x in 24..28 && y in 32..39)

    /** Coordinates cover the entire 32 x 64 door, including its two world cells. */
    private fun window(style: Int, x: Int, y: Int): Boolean = when (style) {
        0 -> kotlin.math.abs(x * 2 - 31) + kotlin.math.abs(y * 2 - 31) <= 18 // Oak: diamond.
        1 -> x in 6..25 && y in 7..24 // Birch: four traditional panes.
        2 -> (x * 2 - 31) * (x * 2 - 31) + (y * 2 - 31) * (y * 2 - 31) <= 18 * 18 // Redwood: porthole.
        3 -> x in 11..20 && y in 6..27 // Dark wood: narrow vertical light.
        4 -> x in 6..25 && y in 7..27 && // Jungle: arched window.
            (y >= 16 || (x * 2 - 31) * (x * 2 - 31) + (y * 2 - 33) * (y * 2 - 33) <= 20 * 20)
        5 -> x in 5..26 && y in 5..58 // Blue: full-height glazing, perimeter frame only.
        6 -> x in 6..25 && (y in 6..19 || y in 24..37 || y in 42..57) // Purple: three lights.
        7 -> x in 5..26 && y in 5..58 // Yellow: full-height six-pane door.
        8 -> x in 6..25 && y in 7..27 && // Pink: clipped corners.
            kotlin.math.abs(x * 2 - 31) + kotlin.math.abs(y * 2 - 35) <= 30
        9 -> (x in 7..12 || x in 19..24) && y in 7..27 // Fir: twin narrow windows.
        else -> false
    }

    private fun mullion(style: Int, x: Int, y: Int): Boolean = when (style) {
        1 -> x in 15..16 || y in 15..16
        4 -> x in 15..16 || y in 19..20
        7 -> x in 15..16 || y in 21..24 || y in 39..42
        else -> false
    }

    fun register(def: BlockDef) {
        if (!def.door) return
        val style = style(def)
        BlockRegistry.registerGeneratedTexture(def.textureSide) { size ->
            val pixels = IntArray(32 * 64)
            fun shade(delta: Int): Int {
                fun channel(shift: Int) = (((def.color ushr shift) and 255) + delta).coerceIn(0, 255)
                return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
            for (y in 0..63) for (x in 0..31) {
                val frame = x < 3 || x > 28 || y < 3 || y > 60 || y in 30..33
                var color = shade(if (frame) -30 else if (x % 7 == 3) -16 else (x * 3 + y * 7) % 9 - 4)
                val opening = window(style, x, y)
                if (opening) {
                    // Alpha cutouts reveal the scenery from either side, also in item icons.
                    color = if (mullion(style, x, y)) shade(-25) else 0x00000000
                } else if (window(style, x - 2, y) || window(style, x + 2, y) ||
                    window(style, x, y - 2) || window(style, x, y + 2)) {
                    // Two-pixel edging keeps curved and polygonal frames legible after resizing.
                    color = shade(if (window(style, x + 2, y) || window(style, x, y + 2)) -32 else 15)
                }
                if (style == 2 && y in 50..52) color = 0xFF4E5356.toInt()
                // A solid backplate anchors the handle even on the fully glazed variants.
                if (x in 24..28 && y in 32..39) color = shade(-35)
                if (x in 25..27 && y in 33..37) color = 0xFFCFA65B.toInt()
                if (transparent(style, x, y)) color = 0x00000000
                pixels[x + y * 32] = color
            }
            val source = Bitmap.createBitmap(pixels, 32, 64, Bitmap.Config.ARGB_8888)
            Bitmap.createScaledBitmap(source, size, size, false).also { if (it !== source) source.recycle() }
        }
    }
}
