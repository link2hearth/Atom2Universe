package com.Atom2Universe.app.games.caves.node

import java.util.concurrent.ConcurrentHashMap

/** Authored material anchors; nearby shades keep their original pixel contrast. */
internal object CavePalette {
    private val anchors = arrayOf(
        0x779B62 to 0x50963D, 0x708366 to 0x486B40, 0xA6A16A to 0xABA04B,
        0x689969 to 0x33834D, 0x8FA68D to 0x77A07D,
        0x94765C to 0x91643F, 0xA28463 to 0x9C733E, 0xB69058 to 0xBC8A3D,
        0xDDC99F to 0xE3C684, 0xC9977B to 0xC78659, 0xA0ABB4 to 0x8B9DAE,
        0x626D7C to 0x46576F, 0xB1D3DC to 0x79B7DC, 0x82BCE5 to 0x59AFE5,
        0xDDB0BE to 0xE292B0, 0xA899C5 to 0x9774C4, 0xBC8171 to 0xB96751,
        0x769F99 to 0x448D82, 0xC5B77D to 0xD0B65A, 0xE0E9E5 to 0xE0EBEB,
        0xE7E8DF to 0xE7E8DF
    )
    private val cache = ConcurrentHashMap<Int, Int>()
    fun vivid(color: Int): Int = cache.getOrPut(color) {
        val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
        var nearest = -1; var distance = 32 * 32
        for (i in anchors.indices) {
            val a = anchors[i].first
            val dr = r - (a ushr 16 and 255); val dg = g - (a ushr 8 and 255); val db = b - (a and 255)
            val d = dr * dr + dg * dg + db * db
            if (d < distance) { distance = d; nearest = i }
        }
        val mean = (r * 3 + g * 6 + b) / 10f
        fun channel(value: Int, shift: Int): Int = if (nearest >= 0) {
            val (from, to) = anchors[nearest]
            (value + (to ushr shift and 255) - (from ushr shift and 255)).coerceIn(0, 255)
        } else (mean + (value - mean) * 1.18f).toInt().coerceIn(0, 255)
        (color and -0x1000000) or (channel(r, 16) shl 16) or (channel(g, 8) shl 8) or channel(b, 0)
    }
}
