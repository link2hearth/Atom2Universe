package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Original 32 px ruins materials, baked once into the normal block atlas. */
internal object UndergroundRuinTextures {
    private val names = listOf(
        "rail_top", "rail_crosswise_top", "rail_side", "beam_side", "beam_end", "funerary_bricks",
        "altar_top", "altar_side", "bench_top", "bench_side", "vat_top", "vat_side",
        "crate_top", "crate_side", "beacon_top", "beacon_side", "cobweb", "basalt_bricks",
        "vat_low_top", "vat_low_side", "vat_half_top", "vat_half_side", "vat_full_top", "vat_full_side")

    fun register() {
        for (name in names) BlockRegistry.registerGeneratedTexture("cave_ruins:$name") { size ->
            texture(name, size)
        }
    }

    private fun tint(rgb: Int, shade: Int = 0): Int = Color.rgb(
        (((rgb shr 16) and 255) + shade).coerceIn(0, 255),
        (((rgb shr 8) and 255) + shade).coerceIn(0, 255),
        ((rgb and 255) + shade).coerceIn(0, 255))

    private fun brick(x: Int, y: Int, grain: Int, basalt: Boolean): Int {
        val localX = Math.floorMod(x + (y / 8 % 2) * 8, 16)
        val localY = y % 8
        val shade = when {
            localX == 0 || localY == 0 -> -30
            localX == 1 || localY == 1 -> 18
            localX == 15 || localY == 7 -> -12
            else -> grain + ((x / 16 + y / 8) % 3 - 1) * 6
        }
        val base = if (basalt) 0x4A4548 else 0x78867E
        if (!basalt && localY in 3..5 && localX in 5..10 && (localX + localY) % 5 == 0)
            return tint(0x475A52, grain)
        return tint(base, shade)
    }

    private fun wood(x: Int, y: Int, grain: Int): Int {
        val stripe = x % 8
        val knot = abs(x - 18) + abs(y - 20) / 2
        return tint(0x927149, when {
            stripe == 0 -> -37
            stripe == 1 -> 12
            knot in 2..3 -> -25
            (x + y / 7) % 5 == 0 -> grain - 13
            else -> grain
        })
    }

    private fun texture(name: String, size: Int): Bitmap {
        if (name.startsWith("vat_") && name !in setOf("vat_top", "vat_side"))
            return vatVariant(name, size)
        val pixels = IntArray(32 * 32)
        for (y in 0..31) for (x in 0..31) {
            val grain = ((x * 73 xor y * 151) ushr 2) % 15 - 7
            val edge = min(min(x, 31 - x), min(y, 31 - y))
            val dx = abs(2 * x - 31)
            val dy = abs(2 * y - 31)
            val railX = if (name == "rail_crosswise_top") y else x
            val railY = if (name == "rail_crosswise_top") x else y
            pixels[y * 32 + x] = when (name) {
                "rail_top", "rail_crosswise_top" -> when {
                    railX in 6..9 || railX in 22..25 -> tint(0x8B969A,
                        if (railX == 7 || railX == 23) 62 else if (railX == 9 || railX == 25) -35 else grain)
                    railY % 10 in 2..5 && railX in 2..29 -> tint(0x73543A,
                        if (railY % 10 == 2) 19 else grain - 8)
                    else -> tint(0x45494B, grain * 2)
                }
                "rail_side" -> tint(0x45494B, grain * 2)
                "beam_side" -> when {
                    y in 3..7 || y in 24..28 -> {
                        val rivet = (x == 5 || x == 26) && (y == 5 || y == 26)
                        tint(if (rivet) 0xD0BBB0 else 0x505860,
                            if (y == 3 || y == 24) 25 else grain / 2)
                    }
                    else -> wood(x, y, grain)
                }
                "beam_end" -> when {
                    edge < 2 -> tint(0x505860, if (edge == 0) -18 else 18)
                    max(dx, dy) % 8 < 2 -> tint(0x6C5135, grain)
                    else -> tint(0xA38358, grain)
                }
                "funerary_bricks" -> brick(x, y, grain, false)
                "basalt_bricks" -> brick(x, y, grain, true)
                "altar_top" -> {
                    val ring = max(dx, dy) in 21..23 && dx + dy < 39
                    val diamond = dx + dy in 12..14
                    val corners = min(dx, dy) in 15..17 && max(dx, dy) in 17..21
                    when {
                        edge < 2 -> tint(0x344845, grain)
                        ring || diamond || corners -> tint(0xA9C5A4, grain)
                        dx <= 1 && dy <= 3 -> tint(0xE0D5AD)
                        else -> tint(0x4B605A, grain)
                    }
                }
                "altar_side" -> when {
                    y < 3 || y > 27 -> tint(0x88968C, grain - if (y == 2 || y == 28) 28 else 0)
                    y in 11..21 && x % 8 in 3..4 -> tint(0x293C37, grain)
                    y == 9 || y == 24 -> tint(0xAAB79D, grain - 15)
                    else -> brick(x, y, grain, false)
                }
                "bench_top" -> when {
                    edge < 2 -> tint(0x544941, grain)
                    x in 4..11 && y in 4..12 -> tint(0xD0BC94, grain)
                    x in 5..10 && y in 6..11 && (x + y) % 3 == 0 -> tint(0x7A6C56)
                    (x - 24) * (x - 24) + (y - 9) * (y - 9) <= 16 -> tint(0x73B79C, grain)
                    x in 6..8 && y in 19..28 || y in 22..24 && x in 5..17 -> tint(0x9CA3A2, grain)
                    x in 22..27 && y in 21..26 -> tint(0xAC77B3, grain)
                    x % 10 == 0 || y % 10 == 0 -> tint(0x647975, grain)
                    else -> tint(0x9FADA0, grain)
                }
                "bench_side" -> when {
                    y < 4 -> tint(0x98A59A, grain - if (y == 3) 31 else 0)
                    x in 2..3 || x in 28..29 -> tint(0x414B4C, grain)
                    x in 6..25 && y in 7..24 -> when {
                        y == 7 || y == 15 || y == 24 || x == 6 || x == 25 -> tint(0x453D32)
                        x in 13..18 && (y == 11 || y == 20) -> tint(0xC9AA72)
                        else -> wood(x, y, grain)
                    }
                    else -> tint(0x5B554A, grain)
                }
                "vat_top" -> {
                    val distance = dx * dx + dy * dy
                    when {
                        distance > 850 -> tint(0x424F51, grain)
                        distance > 560 -> tint(0x91A5A0, grain + if (x < 15) 17 else -20)
                        distance > 430 -> tint(0x263E3A)
                        (x - 12) * (x - 12) + (y - 13) * (y - 13) in 5..10 -> tint(0xC7E997)
                        (x - 20) * (x - 20) + (y - 20) * (y - 20) in 2..5 -> tint(0xA0DC7F)
                        else -> tint(0x57985D, grain + if ((x + y) % 11 == 0) 12 else 0)
                    }
                }
                "vat_side" -> when {
                    y in 0..3 || y in 27..31 -> tint(0x91A5A0, grain - if (y == 3 || y == 31) 35 else 0)
                    x in 9..22 && y in 7..24 -> when {
                        x == 9 || x == 22 || y == 7 || y == 24 -> tint(0xBCB699)
                        y < 12 -> tint(0x263D37)
                        x == 12 || (x == 18 && y in 15..17) -> tint(0xADD98F)
                        else -> tint(0x598D60, grain)
                    }
                    x % 8 == 0 -> tint(0x25393A)
                    else -> tint(0x4A6360, grain)
                }
                "crate_top", "crate_side" -> when {
                    edge < 3 -> tint(0x624B34, grain + if (edge == 1) 22 else 0)
                    (x in 3..4 || x in 27..28) && (y == 4 || y == 27) -> tint(0xB9B3A0)
                    name == "crate_side" && abs(x - y) <= 2 -> tint(0xB28C59, grain)
                    name == "crate_side" && x in 18..25 && y in 7..14 ->
                        tint(if (x in 20..23 && y in 9..12) 0x6E7566 else 0xD9CBA5, grain)
                    name == "crate_top" && y in 6..9 || name == "crate_top" && y in 22..25 -> tint(0xB28C59, grain)
                    else -> wood(x, y, grain)
                }
                "beacon_top" -> when {
                    edge < 3 -> tint(0x526579, grain + if (edge == 1) 20 else 0)
                    dx + dy < 21 -> tint(0x99DCE1, if (x < 16) 24 else -29)
                    dx <= 1 || dy <= 1 -> tint(0x798CA1, grain)
                    else -> tint(0x344B64, grain)
                }
                "beacon_side" -> when {
                    y > 25 || y < 3 -> tint(0x8295A4, grain - if (y == 26 || y == 2) 30 else 0)
                    y in 5..24 && dx <= min((y - 4) * 2, (26 - y) * 3) ->
                        tint(0x8CCEDC, if (x == 15) 65 else if (x < 15) 16 else -32)
                    x < 3 || x > 28 -> tint(0x526579, grain)
                    else -> tint(0x2F435B, grain)
                }
                "cobweb" -> {
                    // Slender radial threads and sagging octagonal rings; clear between them.
                    val major = max(dx, dy)
                    val minor = min(dx, dy)
                    val spoke = dx <= 1 || dy <= 1 || abs(dx - dy) <= 1
                    val ring = major + minor / 2
                    val thread = spoke || ring == 9 || ring == 19 || ring == 30 || ring == 41
                    if (thread) tint(0xC5D0CD, grain + if (spoke) 12 else -18) else 0
                }
                else -> error("Unknown ruin texture: $name")
            }
        }
        val source = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        if (size == 32) return source
        return Bitmap.createScaledBitmap(source, size, size, false).also { source.recycle() }
    }

    /** Decorative vessel fill levels. The original vat remains pixel-for-pixel unchanged. */
    private fun vatVariant(name: String, size: Int): Bitmap {
        val level = when { "low" in name -> 0; "half" in name -> 1; else -> 2 }
        val top = name.endsWith("_top")
        val pixels = IntArray(32 * 32)
        val bubbles = when (level) {
            0 -> arrayOf(intArrayOf(13,19,2), intArrayOf(21,11,1))
            1 -> arrayOf(intArrayOf(10,12,2), intArrayOf(20,19,3), intArrayOf(18,9,1))
            else -> arrayOf(intArrayOf(11,11,3), intArrayOf(20,17,3), intArrayOf(12,23,2), intArrayOf(23,9,1))
        }
        val surface = intArrayOf(21,17,10)[level]
        for (y in 0..31) for (x in 0..31) {
            val grain = ((x * 73 xor y * 151) ushr 2) % 15 - 7
            val dx = abs(2 * x - 31); val dy = abs(2 * y - 31)
            pixels[y * 32 + x] = if (top) {
                val distance = dx * dx + dy * dy
                when {
                    distance > 850 -> tint(0x424F51, grain)
                    distance > 560 -> tint(0x91A5A0, grain + if (x < 15) 17 else -20)
                    distance > 430 -> tint(0x263E3A)
                    // More of the dark inner wall is visible in the low vessel.
                    level == 0 && y < 10 -> tint(0x314C40, grain)
                    bubbles.any { (bx,by,r) -> (x-bx)*(x-bx)+(y-by)*(y-by) in max(1,r*r-2)..r*r+1 } -> tint(0xC7E997)
                    bubbles.any { (bx,by,r) -> (x-bx)*(x-bx)+(y-by)*(y-by) < max(1,r*r-2) } -> tint(0x3E774E, grain)
                    else -> tint(0x57985D, grain + if ((x + y + level * 3) % 11 == 0) 12 else 0)
                }
            } else when {
                y in 0..3 || y in 27..31 -> tint(0x91A5A0, grain - if (y == 3 || y == 31) 35 else 0)
                x in 9..22 && y in 7..24 -> when {
                    x == 9 || x == 22 || y == 7 || y == 24 -> tint(0xBCB699)
                    y < surface -> tint(0x263D37)
                    y == surface -> tint(0xADD98F)
                    x == 12 || (x == 18 && y in surface+2..surface+3) -> tint(0xADD98F)
                    else -> tint(0x598D60, grain)
                }
                x % 8 == 0 -> tint(0x25393A)
                else -> tint(0x4A6360, grain)
            }
        }
        val source = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        if (size == 32) return source
        return Bitmap.createScaledBitmap(source, size, size, false).also { source.recycle() }
    }
}
