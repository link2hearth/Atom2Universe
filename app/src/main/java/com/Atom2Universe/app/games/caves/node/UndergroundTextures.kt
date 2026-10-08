package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/** Original pixel textures, generated once with the existing atlas (no external assets). */
internal object UndergroundTextures {
    private val mushroomHues = linkedMapOf(
        "amber" to 0xEBC47A, "green" to 0x80CAA0, "violet" to 0xBB91E8,
        "cyan" to 0x85D9E4, "blue" to 0x9BAFEA, "ember" to 0xE99A65,
        "rose" to 0xF2A5C6)
    private val caveDetails = mapOf(
        "cave_fern" to 0x5FAD88, "cave_crystal_cluster" to 0x85D9E4,
        "cave_ice_needles" to 0xB8DEEE, "cave_calcite_spires" to 0xD7C7A8,
        "columnar_basalt" to 0x50515B, "cave_travertine" to 0xDACAAD)
    private val colors = mapOf(
        "amber" to 0xEBC47A, "green" to 0x80CAA0, "violet" to 0xBB91E8,
        "cyan" to 0x85D9E4, "blue" to 0x9BAFEA, "ember" to 0xE99A65,
        "moss" to 0x83BC89, "mycelium" to 0x96849F, "root" to 0x98745B,
        "stem" to 0xC9B5AD, "cap" to 0x86638F, "gills" to 0xB9A3BC,
        "fungus" to 0xC6A2EB, "frost" to 0xA8C1CF, "calcite" to 0xCDBF9E,
        "crystal" to 0xA5C9D9)

    fun register() {
        UndergroundRuinTextures.register()
        UndergroundRelicTextures.register()
        for (name in colors.keys) BlockRegistry.registerGeneratedTexture("underground:$name") { size ->
            texture(name, size)
        }
        for ((index, entry) in mushroomHues.entries.withIndex()) {
            val (name, hue) = entry
            BlockRegistry.registerGeneratedTexture("underground:mushroom_$name") { size ->
                mushroom(hue, index, size)
            }
            for (gills in listOf(false, true)) {
                val part = if (gills) "gills" else "cap"
                BlockRegistry.registerGeneratedTexture("underground:${part}_$name") { size ->
                    mushroomBlock(hue, gills, size)
                }
            }
        }
        for ((name, hue) in caveDetails) BlockRegistry.registerGeneratedTexture("underground:$name") { size ->
            caveDetail(name, hue, size)
        }
    }

    private fun shaded(rgb: Int, shade: Int = 0): Int = Color.rgb(
        (((rgb shr 16) and 255) + shade).coerceIn(0, 255),
        (((rgb shr 8) and 255) + shade).coerceIn(0, 255),
        ((rgb and 255) + shade).coerceIn(0, 255))

    private fun bitmap(pixels: IntArray, size: Int): Bitmap {
        val source = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        if (size == 32) return source
        return Bitmap.createScaledBitmap(source, size, size, false).also { source.recycle() }
    }

    /** Seven silhouettes: squat caps, clusters, tall parasols, bells and branching fans.
     * Every stem reaches the ground row; the transparent background has no luminous square.
     */
    private fun mushroom(hue: Int, variant: Int, size: Int): Bitmap {
        val pixels = IntArray(32 * 32)
        fun specimen(cx: Int, top: Int, radius: Int, capHeight: Int, stemWidth: Int, bell: Boolean) {
            val bottom = top + capHeight
            for (y in bottom..31) for (x in cx - stemWidth..cx + stemWidth) {
                if (x in 0..31) pixels[y * 32 + x] = shaded(0xC3CECA,
                    if (x == cx - stemWidth) -38 else if (x == cx + stemWidth) -15 else 12)
            }
            for (y in top..bottom) for (x in 0..31) {
                val down = y - top
                val width = if (bell) minOf(radius, 2 + down * radius / maxOf(1, capHeight))
                    else minOf(radius, 3 + down * 3)
                if (abs(x - cx) > width) continue
                val grain = (x * 19 + y * 31 + variant * 13) % 13 - 6
                val shade = when {
                    y == bottom -> -46 + if ((x - cx) % 3 == 0) 26 else 0
                    abs(x - cx) == width -> -28
                    y <= top + 2 -> 34
                    (x * 7 + y * 13 + variant * 11) % 29 < 4 -> 64
                    else -> grain - maxOf(0, x - cx) * 2
                }
                pixels[y * 32 + x] = shaded(hue, shade)
            }
        }
        when (variant) {
            0 -> { specimen(17, 9, 12, 9, 2, false); specimen(6, 22, 5, 4, 1, false) }
            1 -> { specimen(21, 13, 7, 8, 1, true); specimen(10, 8, 7, 9, 1, true); specimen(6, 23, 4, 4, 1, true) }
            2 -> { specimen(16, 3, 13, 8, 1, false); specimen(26, 22, 4, 4, 1, false) }
            3 -> { specimen(9, 13, 7, 8, 1, true); specimen(21, 6, 8, 12, 1, true) }
            4 -> { specimen(16, 8, 10, 11, 2, true); specimen(5, 24, 4, 4, 1, true) }
            5 -> { specimen(9, 20, 7, 5, 1, false); specimen(23, 17, 7, 5, 1, false); specimen(16, 8, 9, 6, 1, false) }
            else -> { specimen(23, 18, 7, 6, 1, false); specimen(10, 14, 8, 6, 1, false); specimen(16, 4, 10, 6, 1, false) }
        }
        return bitmap(pixels, size)
    }

    private fun mushroomBlock(hue: Int, gills: Boolean, size: Int): Bitmap {
        val pixels = IntArray(32 * 32)
        for (y in 0..31) for (x in 0..31) {
            val grain = (x * 19 + y * 37) % 13 - 6
            val fleckX = Math.floorMod(x + (y / 8) * 3, 11)
            val fleckY = Math.floorMod(y, 9)
            val shade = if (gills) {
                if (Math.floorMod(x + y / 4, 5) < 2) -48 else 14 + grain
            } else {
                if (fleckX in 2..4 && fleckY in 3..5) 45 + grain else -18 + grain
            }
            pixels[y * 32 + x] = shaded(hue, shade)
        }
        return bitmap(pixels, size)
    }

    private fun caveDetail(name: String, hue: Int, size: Int): Bitmap {
        val pixels = IntArray(32 * 32)
        if (name == "columnar_basalt" || name == "cave_travertine") {
            for (y in 0..31) for (x in 0..31) {
                val grain = (x * 37 + y * 19) % 13 - 6
                val shade = if (name == "columnar_basalt") {
                    val stripe = Math.floorMod(x + (y / 14), 11)
                    when (stripe) { 0, 1 -> -27; 2 -> 18; else -> grain }
                } else {
                    val band = Math.floorMod(y + x / 9, 10)
                    when (band) { 0, 1 -> -30; 2 -> 22; 6 -> -11; else -> grain }
                }
                pixels[y * 32 + x] = shaded(hue, shade)
            }
            return bitmap(pixels, size)
        }
        if (name == "cave_fern") {
            fun dot(x: Int, y: Int, tint: Int) {
                if (x in 0..31 && y in 0..31) pixels[y * 32 + x] = shaded(hue, tint)
            }
            // Curved fronds, with alternating paired leaflets rather than a solid triangle.
            for (side in listOf(-1, 1)) for (frond in 0..2) {
                val reach = 5 + frond * 4
                val height = 23 - frond * 5
                for (step in 0..height) {
                    val y = 31 - step
                    val x = 16 + side * (step * reach / height)
                    dot(x, y, -18)
                    if (step > 3 && step % 3 == 0) for (leaf in 1..3) {
                        dot(x + side * leaf, y + leaf / 2, 12 + frond * 5)
                        dot(x - side * leaf, y - 1, -5)
                    }
                }
            }
        } else {
            fun spike(cx: Int, top: Int, width: Int, slant: Int) {
                for (y in top..31) {
                    val center = cx + slant * (31 - y) / maxOf(1, 31 - top)
                    val radius = minOf(width, (y - top) / 3)
                    for (x in center - radius..center + radius) if (x in 0..31) {
                        val shade = if (name == "cave_calcite_spires") {
                            if (y % 6 == 0) -21 else if (x < center) 18 else -15
                        } else {
                            if (x == center) 57 else if (x < center) 20 else -43
                        }
                        pixels[y * 32 + x] = shaded(hue, shade)
                    }
                }
            }
            when (name) {
                "cave_crystal_cluster" -> { spike(8, 15, 4, -4); spike(23, 11, 4, 4); spike(16, 3, 5, 0) }
                "cave_ice_needles" -> { spike(6, 18, 2, -2); spike(24, 8, 2, 3); spike(12, 2, 2, -1); spike(19, 12, 2, 1) }
                else -> { spike(7, 19, 4, 0); spike(25, 14, 4, 0); spike(15, 5, 5, 0) }
            }
        }
        return bitmap(pixels, size)
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
