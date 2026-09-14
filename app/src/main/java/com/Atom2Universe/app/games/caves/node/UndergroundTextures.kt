package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/** Original pixel textures, generated once with the existing atlas (no external assets). */
internal object UndergroundTextures {
    private val colors = mapOf(
        "amber" to 0xEBC47A, "green" to 0x80CAA0, "violet" to 0xBB91E8,
        "cyan" to 0x85D9E4, "blue" to 0x9BAFEA, "ember" to 0xE99A65,
        "moss" to 0x83BC89, "mycelium" to 0x96849F, "root" to 0x98745B,
        "stem" to 0xC9B5AD, "cap" to 0x86638F, "gills" to 0xB9A3BC,
        "fungus" to 0xC6A2EB, "frost" to 0xA8C1CF, "calcite" to 0xCDBF9E,
        "crystal" to 0xA5C9D9)

    fun register() {
        for (name in colors.keys) BlockRegistry.registerGeneratedTexture("underground:$name") { size ->
            texture(name, size)
        }
    }

    private fun texture(name: String, size: Int): Bitmap {
        val base = colors.getValue(name)
        if (name in luminousStones) return runeStone(name, base, size)
        val pixels = IntArray(32 * 32)
        for (y in 0..31) for (x in 0..31) {
            val hash = ((x * 73428767 xor (y * 912931)) ushr 3) and 255
            var tint = (hash % 23) - 11
            var color = base
            var alpha = 255
            when (name) {
                "moss" -> { if (hash < 30) { color = 0xC0E8AD; tint = 0 }; if (hash > 190) tint -= 25 }
                "mycelium" -> { if (Math.floorMod(x * 3 + y + x / 5, 17) < 2) tint += 34 }
                "root", "stem" -> { tint += if ((x + y / 9) % 7 < 2) -25 else 5 }
                "cap" -> { if ((x / 4 + y / 4) % 5 == 0) { color = 0xD7BCCF; tint /= 2 } }
                "gills" -> { tint += if ((x + y) % 5 < 2) -26 else 10 }
                "frost" -> { if ((x + y * 2) % 11 < 2) tint += 30 }
                "calcite" -> { tint += if ((y + x / 8) % 8 < 2) -16 else 6 }
                "crystal" -> { tint += if (x < 12 + y / 4) 20 else -30; if (abs(x - 12 - y / 4) < 2) tint += 35 }
                "fungus" -> {
                    val cap = y in 7..17 && abs(x - 16) <= (if (y < 11) (y - 6) * 3 else 12)
                    val stem = y in 17..29 && x in 14..17
                    alpha = if (cap || stem) 255 else 0
                    if (stem) color = 0xBDB1C9
                    if (cap && hash < 60) color = 0xF0D8F4
                }
            }
            pixels[y * 32 + x] = Color.argb(alpha,
                (((color shr 16) and 255) + tint).coerceIn(0, 255),
                (((color shr 8) and 255) + tint).coerceIn(0, 255),
                ((color and 255) + tint).coerceIn(0, 255))
        }
        val bitmap = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        if (size == 32) return bitmap
        return Bitmap.createScaledBitmap(bitmap, size, size, false).also { bitmap.recycle() }
    }

    private val luminousStones = setOf("amber", "green", "violet", "cyan", "blue", "ember")

    /** Six invented runes, sharing a carved stone finish and mirrored strokes.
     * Doubled coordinates keep every stroke exactly mirrored around the four centre pixels.
     * The halo is baked into the atlas; no extra light source or render pass is needed.
     */
    private fun runeStone(name: String, hue: Int, size: Int): Bitmap {
        val mask = BooleanArray(32 * 32)
        for (y in 0..31) for (x in 0..31) {
            val dx = abs(2 * x - 31)
            val dy = abs(2 * y - 31)
            val sum = dx + dy
            mask[y * 32 + x] = when (name) {
                "amber" -> { // Sun seal: octagonal ring and eight separated rays.
                    val ring = maxOf(dx, dy) in 11..13 && sum <= 22 ||
                        sum in 20..22 && maxOf(dx, dy) <= 13
                    val rays = dx <= 1 && dy in 19..25 || dy <= 1 && dx in 19..25 ||
                        dx == dy && dx in 15..21
                    ring || rays || (dx <= 3 && dy <= 3)
                }
                "green" -> { // Paired fronds growing from a central stem.
                    val stem = dx <= 1 && dy <= 25
                    val branches = dx in 3..13 && (sum == 14 || sum == 26)
                    val leaves = dx in 9..15 && dy in 5..11 && sum in 18..20
                    stem || branches || leaves || (dx == 21 && dy <= 5)
                }
                "violet" -> { // Watchful eye, diamond pupil and detached brow marks.
                    val eye = dx + 2 * dy in 24..28 && dx <= 23 && dy <= 13
                    val pupil = sum in 6..8
                    val brows = sum in 28..30 && dx <= 11 && dy >= 17
                    eye || pupil || brows
                }
                "cyan" -> { // Four pointed compass star with corner brackets.
                    val star = 3 * minOf(dx, dy) + maxOf(dx, dy) in 24..28
                    val corners = dx in 17..23 && dy in 17..23 && (dx == 23 || dy == 23)
                    star || corners || (dx <= 1 && dy <= 1)
                }
                "blue" -> { // Ice totem: crossed axes with branching tips.
                    val axes = dx <= 1 && dy <= 25 || dy <= 1 && dx <= 25
                    val forks = sum == 24 && minOf(dx, dy) in 3..9
                    val diagonals = dx == dy && dx in 5..15
                    axes || forks || diagonals
                }
                else -> { // Ember: concentric angular lozenges and a glowing core.
                    val shell = 2 * dx + dy in 28..32 && dy <= 25
                    val inner = 2 * dx + dy in 12..16 && dy <= 13
                    val guards = dx == 23 && dy <= 9 || dx == 21 && dy == 11
                    shell || inner || guards || (dx <= 1 && dy <= 3)
                }
            }
        }
        fun mix(a: Int, b: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val av = (a shr shift) and 255
                val bv = (b shr shift) and 255
                return (av + (bv - av) * amount).toInt()
            }
            return Color.rgb(channel(16), channel(8), channel(0))
        }
        val pixels = IntArray(32 * 32)
        for (y in 0..31) for (x in 0..31) {
            val mx = minOf(x, 31 - x)
            val my = minOf(y, 31 - y)
            val edge = minOf(mx, my)
            val grain = ((minOf(mx, my) * 37 + maxOf(mx, my) * 61) % 9) / 100f
            var stone = mix(0x252A33, 0x555C68, 0.22f + grain)
            // A restrained bevel frames the engraving without a bright tile border.
            if (edge == 0) stone = 0xFF20242B.toInt()
            if (edge == 1) stone = 0xFF454C58.toInt()
            var distance = 3
            for (oy in -2..2) for (ox in -2..2) {
                val nx = x + ox; val ny = y + oy
                if (nx in 0..31 && ny in 0..31 && mask[ny * 32 + nx])
                    distance = minOf(distance, maxOf(abs(ox), abs(oy)))
            }
            pixels[y * 32 + x] = when (distance) {
                0 -> mix(hue, 0xFFF4DF, if (abs(x * 2 - 31) + abs(y * 2 - 31) <= 16) 0.42f else 0.16f)
                1 -> mix(0x151920, hue, 0.30f) // recessed, coloured edge
                2 -> mix(stone, hue, 0.10f)
                else -> stone
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        if (size == 32) return bitmap
        return Bitmap.createScaledBitmap(bitmap, size, size, false).also { bitmap.recycle() }
    }
}
