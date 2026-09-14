package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max

/** Original, deterministic pixel recipes. Coordinates are always on a 32 x 32 grid.
 * Palette and pattern are independent: changing a tint never changes the arrangement.
 * No per-frame work, filtering, external images, or world-save changes are needed.
 */
internal object MeadowTextures {
    const val SIZE = 32
    const val CLIMATE_COUNT = 5
    val itemTextureNames = listOf("Items/arrow.png", "Items/bucket.png", "Items/bucket_full.png",
        "Items/ore_iron.png", "Items/torch.png", "ward_stone.png")

    fun hasKnotVariant(name: String): Boolean = name.startsWith("cozy:material:7:")

    /** A stable candidate on roughly one log in twelve, never more than one face.
     * End-grain layers have no knot variant, including for horizontally placed logs.
     */
    fun rareKnotFace(x: Int, y: Int, z: Int): Int {
        var hash = x * 73856093 xor (y * 19349663) xor (z * 83492791)
        hash = (hash xor (hash ushr 16)) * 0x45d9f3b
        hash = hash xor (hash ushr 16)
        val positive = hash and Int.MAX_VALUE
        return if (positive % 12 == 0) (positive / 12) % 6 else -1
    }
    // Temperate, warm/dry, warm/humid, wetland, cold: muted, readable greens.
    private val climateColors = intArrayOf(0x779B62, 0xA6A16A, 0x689969, 0x708366, 0x8FA68D)
    fun climateColor(climate: Int, vivid: Boolean): Int {
        val color = 0xFF000000.toInt() or climateColors[climate]
        return if (vivid) CavePalette.vivid(color) else color
    }

    fun climateMask(name: String): Int {
        if (!isClimateTexture(name)) return 0
        return if (name.startsWith("cozy:cap:") || name == "cozy:material:1") 2 else 1
    }

    fun isClimateTexture(name: String): Boolean {
        val p = name.split(':')
        return when (p.getOrNull(1)) {
            "material" -> p[2] == "0" || p[2] == "1"
            "cap" -> p.getOrNull(4) == "0"
            "groundcover" -> p[2].toInt() in 0..2
            "leaf" -> p.getOrNull(3) in setOf("537B66", "619378", "698E80", "829D65")
            else -> false
        }
    }
    private val materials = intArrayOf(
        0x779B62, 0x94765C, 0x94765C, 0xA0ABB4,
        0xA6A29A, 0xDDC99F, 0xC9977B, 0xA28463,
        0xC3A579, 0xB69058, 0x789561, 0xD7CDB4,
        0xBC8171, 0x769F99, 0xC7C6AD, 0x8E9DA7
    )

    fun supports(name: String): Boolean {
        if (name in itemTextureNames) return true
        val p = name.split(':')
        return p.getOrNull(1) in setOf("material", "leaf", "ore", "cloth", "cap", "groundcover", "utility", "glass", "flora", "item", "nature")
    }

    fun texture(name: String, outputSize: Int, climate: Int = 0, vivid: Boolean = false): Bitmap {
        val pixels = pixels(name, climate, vivid)
        val bitmap = Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        if (outputSize == SIZE) return bitmap
        return Bitmap.createScaledBitmap(bitmap, outputSize, outputSize, false).also { bitmap.recycle() }
    }

    /** Exposed internally so preview tooling can exercise the exact production recipes. */
    fun pixels(name: String, climate: Int = 0, vivid: Boolean = false): IntArray {
        val item = itemTextureNames.indexOf(name)
        val p = (if (item >= 0) "cozy:item:$item" else name).split(':')
        val family = p[1]
        val tile = p[2].toInt()
        val base = p.getOrNull(3)?.let { Color.parseColor("#$it") }
            ?: (0xFF000000.toInt() or materials[tile.coerceIn(0, 15)])
        val canvas = Tile()
        when (family) {
            "nature" -> canvas.nature(tile)
            "item" -> canvas.item(tile)
            "utility" -> canvas.utility(tile, p.getOrNull(3)?.let { Color.parseColor("#$it") })
            "glass" -> canvas.glass()
            "flora" -> canvas.flora(tile)
            "groundcover" -> canvas.groundcover(tile)
            "leaf" -> canvas.leaves(base)
            "cloth" -> {
                val soft = mix(base, 0xFFD8CDBE.toInt(), .20f)
                canvas.fill(soft)
                for (y in 0..31 step 4) for (x in 0..31 step 4) {
                    canvas.rect(x, y, 2, 1, shade(soft, 9))
                    canvas.rect(x + 2, y + 2, 1, 2, shade(soft, -9))
                }
            }
            else -> {
                canvas.material(tile, base)
                if (tile == 7 && p.getOrNull(4) == "knot") canvas.knot(base)
                if (family == "ore") canvas.ore(Color.parseColor("#${p[4]}"))
                if (family == "cap") {
                    val cap = p[4].toInt()
                    val top = if (p.getOrNull(5) == "utility") 0xFFE0E9E5.toInt()
                        else 0xFF000000.toInt() or materials[cap]
                    canvas.cap(top)
                }
            }
        }
        if (climate != 0 && isClimateTexture(name)) {
            val target = 0xFF000000.toInt() or climateColors[climate]
            val side = family == "cap" || (family == "material" && tile == 1)
            for (y in 0..31) for (x in 0..31) {
                // Tint only the grass fringe, including its shadow; the soil stays unchanged.
                if (side && y > capDepth(x)) continue
                val c = canvas.data[y * SIZE + x]
                if (c == Color.TRANSPARENT) continue
                canvas.data[y * SIZE + x] = Color.rgb(
                    (Color.red(c) * Color.red(target) / 119).coerceIn(0, 255),
                    (Color.green(c) * Color.green(target) / 155).coerceIn(0, 255),
                    (Color.blue(c) * Color.blue(target) / 98).coerceIn(0, 255))
            }
        }
        if (vivid) for (i in canvas.data.indices) {
            if (canvas.data[i] != Color.TRANSPARENT) canvas.data[i] = CavePalette.vivid(canvas.data[i])
        }
        return canvas.data
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt())

    private fun shade(c: Int, amount: Int) = Color.rgb(
        (Color.red(c) + amount).coerceIn(0, 255),
        (Color.green(c) + amount).coerceIn(0, 255),
        (Color.blue(c) + amount).coerceIn(0, 255))

    private fun capDepth(x: Int): Int =
        6 + intArrayOf(0, 0, 2, 2, 0, 2, 4, 4)[minOf(x, 31 - x) / 2]

    private class Tile {
        val data = IntArray(SIZE * SIZE)
        fun fill(c: Int) = data.fill(c)
        fun rect(x: Int, y: Int, w: Int, h: Int, c: Int) {
            for (py in max(0, y) until (y + h).coerceAtMost(SIZE))
                for (px in max(0, x) until (x + w).coerceAtMost(SIZE)) data[py * SIZE + px] = c
        }
        // Authored, separated marks on a coarse grid. No overlapping random noise.
        // Staggered spacing avoids both accidental clusters and an obvious checkerboard.
        private fun marks(base: Int, contrast: Int, dense: Boolean = false, small: Boolean = false) {
            val positions = arrayOf(2 to 2, 14 to 0, 24 to 4, 8 to 8, 18 to 10,
                28 to 14, 0 to 16, 10 to 18, 22 to 22, 4 to 26, 16 to 28, 30 to 28)
            for ((i, point) in positions.withIndex()) {
                val w = if (!small && i % 3 == 0) 4 else 2
                val h = if (!small && i % 4 == 1) 4 else 2
                val c = shade(base, intArrayOf(-contrast, contrast, -contrast, contrast / 2)[i % 4])
                wrappedRect(point.first, point.second, w, h, c)
            }
            if (dense) {
                for ((x, y) in arrayOf(20 to 2, 2 to 10, 24 to 16, 14 to 24))
                    rect(x, y, 2, 2, shade(base, contrast / 2))
            }
        }

        private fun wrappedRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            for (dy in 0 until h) for (dx in 0 until w)
                data[((y + dy) % SIZE) * SIZE + (x + dx) % SIZE] = color
        }

        private fun mirrorRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            rect(x, y, w, h, color)
            rect(SIZE - x - w, y, w, h, color)
        }
        fun material(tile: Int, base: Int) {
            fill(base)
            when (tile) {
                0 -> marks(base, contrast = 21)
                1 -> { fill(0xFF94765C.toInt()); marks(0xFF94765C.toInt(), contrast = 16, dense = true); cap(0xFF779B62.toInt()) }
                2 -> marks(base, contrast = 16, dense = true)
                3, 14 -> {
                    // Broad mineral facets: each shape has its own breathing room.
                    val facets = arrayOf(
                        intArrayOf(2, 2, 6, 4), intArrayOf(16, 0, 8, 2),
                        intArrayOf(12, 8, 6, 4), intArrayOf(26, 6, 4, 4),
                        intArrayOf(0, 14, 6, 4), intArrayOf(22, 16, 8, 4),
                        intArrayOf(8, 22, 8, 4), intArrayOf(22, 28, 6, 2))
                    for ((i, f) in facets.withIndex()) {
                        val c = shade(base, if (i % 2 == 0) -15 else 13)
                        rect(f[0], f[1], f[2], f[3], c)
                    }
                }
                4 -> {
                    for (row in 0..3) for (col in 0..3) {
                        val x = col * 8 + if (row % 2 == 0) 0 else 4
                        val y = row * 8 + 1
                        wrappedRect(x, y, 6, 5, shade(base, -17))
                        wrappedRect(x, y, 6, 3, shade(base, if ((row + col) % 3 == 0) 17 else 7))
                    }
                }
                5, 6 -> marks(base, contrast = 10, dense = true, small = true)
                7 -> {
                    // Straight grain is the default on every ordinary bark face.
                    mirrorRect(2, 0, 2, 32, shade(base, -20))
                    mirrorRect(4, 0, 2, 32, shade(base, 10))
                    mirrorRect(8, 0, 2, 32, shade(base, -15))
                    mirrorRect(10, 0, 2, 32, shade(base, 7))
                    rect(15, 0, 2, 32, shade(base, -10))
                }
                11 -> {
                    val white = 0xFFE7E8DF.toInt()
                    val charcoal = 0xFF62665E.toInt()
                    fill(white)
                    mirrorRect(0, 0, 2, 32, shade(white, -12))
                    // Unequal, staggered lenticels: no mirrored marks or central face.
                    rect(0, 4, 7, 2, charcoal)
                    rect(2, 6, 3, 1, shade(charcoal, 15))
                    rect(21, 8, 9, 2, charcoal)
                    rect(24, 10, 4, 1, shade(charcoal, 15))
                    rect(10, 15, 6, 2, charcoal)
                    rect(0, 22, 4, 2, charcoal)
                    rect(18, 25, 8, 2, charcoal)
                    rect(20, 27, 3, 1, shade(charcoal, 15))
                    rect(7, 30, 4, 1, shade(white, -26))
                }
                8 -> {
                    for (y in 0..31) for (x in 0..31) {
                        // Center lies between pixels 15 and 16 on BOTH axes.
                        val ring = max(abs(2 * x - 31), abs(2 * y - 31)) / 2
                        data[y * 32 + x] = shade(base, when {
                            ring >= 14 -> -32
                            ring % 4 == 2 -> -19
                            ring % 4 == 3 -> 9
                            else -> 0
                        })
                    }
                    rect(14, 14, 4, 4, shade(base, -22))
                }
                9 -> {
                    for (y in 0..31 step 8) {
                        rect(0, y, 32, 1, shade(base, -28)); rect(0, y + 1, 32, 1, shade(base, 15))
                        if (y % 16 == 0) rect(15, y, 2, 8, shade(base, -23))
                        else mirrorRect(0, y, 1, 8, shade(base, -23))
                        mirrorRect(4, y + 4, 7, 1, shade(base, -10))
                    }
                }
                10 -> leaves(base)
                12, 13, 15 -> {
                    val rowHeight = if (tile == 15) 16 else 8
                    for (y in 0..31 step rowHeight) for (x in -16..31 step 16) {
                        val offset = if ((y / rowHeight) % 2 == 0) 0 else 8
                        val c = shade(base, if ((x / 16 + y / rowHeight) % 2 == 0) 4 else -4)
                        rect(x + offset, y, 16, rowHeight, shade(base, -26))
                        rect(x + offset + 1, y + 1, 14, rowHeight - 2, c)
                        rect(x + offset + 1, y + 1, 14, 1, shade(c, 12))
                    }
                }
            }
        }
        fun knot(base: Int) {
            // Same outer ribs as the normal texture, with one small centered knot.
            rect(10, 8, 12, 16, base)
            mirrorRect(10, 10, 2, 12, shade(base, -15))
            mirrorRect(12, 12, 2, 8, shade(base, -23))
            rect(14, 10, 4, 2, shade(base, -23))
            rect(14, 20, 4, 2, shade(base, -23))
            rect(15, 14, 2, 4, shade(base, 12))
        }
        fun nature(tile: Int) {
            val colors = intArrayOf(0x929DA4, 0x929DA4, 0xCBB68D, 0x827565,
                0xB7A69B, 0x88735D, 0x81986B, 0x626D7C)
            val base = 0xFF000000.toInt() or colors[tile]
            fill(base)
            when (tile) {
                0, 1 -> {
                    fill(shade(base, -25))
                    for (row in 0..3) for (col in 0..3) {
                        val x = col * 8 + if (row % 2 == 0) 0 else 4
                        val y = row * 8
                        wrappedRect(x + 1, y + 1, 6, 6, shade(base, if ((row + col) % 3 == 0) 8 else -2))
                        wrappedRect(x + 2, y, 4, 1, base)
                        wrappedRect(x + 2, y + 1, 4, 1, shade(base, 17))
                        if (tile == 1 && (row + col) % 3 != 0) {
                            wrappedRect(x + 1, y + 1, 4, 2, 0xFF87966B.toInt())
                            wrappedRect(x + 1, y + 3, 2, 2, 0xFF75875F.toInt())
                        }
                    }
                }
                2 -> {
                    for (y in intArrayOf(6, 16, 26)) {
                        rect(0, y, 32, 2, shade(base, -12))
                        rect(0, y + 2, 32, 1, shade(base, 12))
                    }
                    rect(5, 3, 6, 1, shade(base, 15)); rect(20, 22, 8, 1, shade(base, -8))
                }
                3 -> {
                    for ((x, y) in arrayOf(2 to 4, 18 to 12, 6 to 24)) {
                        rect(x, y, 10, 3, shade(base, -12))
                        rect(x + 2, y, 6, 1, shade(base, 9))
                    }
                }
                4 -> {
                    rect(0, 8, 32, 2, shade(base, -8)); rect(0, 22, 32, 2, shade(base, 8))
                    marks(base, 8, small = true)
                }
                5 -> {
                    marks(base, 10)
                    for ((i, p) in arrayOf(3 to 4, 19 to 2, 11 to 14, 25 to 22, 2 to 26).withIndex()) {
                        val leaf = if (i % 2 == 0) 0xFFB29965.toInt() else 0xFF98855A.toInt()
                        rect(p.first, p.second + 1, 5, 2, leaf)
                        rect(p.first + 1, p.second, 3, 4, leaf)
                        rect(p.first + 2, p.second + 1, 1, 2, shade(leaf, -18))
                    }
                }
                6 -> {
                    marks(base, 12, dense = true)
                    for ((x, y) in arrayOf(8 to 6, 22 to 17, 4 to 24))
                        rect(x, y, 4, 2, shade(base, 17))
                }
                7 -> {
                    for (x in intArrayOf(4, 14, 26)) {
                        rect(x, 0, 2, 32, shade(base, -16))
                        rect(x + 2, 0, 1, 32, shade(base, 8))
                    }
                    rect(6, 10, 8, 1, shade(base, -13)); rect(16, 23, 10, 1, shade(base, -13))
                }
            }
        }

        fun glass() {
            val edge = 0xFFAACBCB.toInt()
            rect(0, 0, 32, 1, edge); rect(0, 31, 32, 1, edge)
            rect(0, 0, 1, 32, edge); rect(31, 0, 1, 32, edge)
            for (i in 0..5) {
                rect(5 + i * 2, 15 - i * 2, 2, 2, 0xFFDDEBE6.toInt())
                rect(17 + i * 2, 27 - i * 2, 2, 2, edge)
            }
        }

        fun utility(tile: Int, tint: Int?) {
            when (tile) {
                12, 13 -> {
                    val stone = 0xFF929DA5.toInt()
                    fill(shade(stone, -22))
                    rect(2, 2, 28, 28, stone)
                    rect(3, 3, 26, 1, shade(stone, 14))
                    rect(3, 28, 26, 1, shade(stone, -12))
                    if (tile == 12) {
                        for (y in intArrayOf(10, 16, 22)) {
                            rect(8, y, 16, 2, shade(stone, -35))
                            rect(8, y + 2, 16, 1, shade(stone, 9))
                        }
                    } else {
                        rect(7, 7, 18, 18, shade(stone, -13))
                        rect(9, 9, 14, 14, shade(stone, 4))
                        rect(12, 12, 8, 8, shade(stone, -24))
                    }
                }
                14, 15 -> {
                    val wood = 0xFFB99A71.toInt()
                    fill(shade(wood, -12))
                    rect(3, 3, 26, 26, wood)
                    mirrorRect(0, 0, 3, 32, shade(wood, -30))
                    rect(0, 0, 32, 4, shade(wood, 10))
                    rect(0, 27, 32, 3, shade(wood, -27))
                    if (tile == 14) {
                        // Framed drawer above a lower shelf, readable on all four sides.
                        rect(5, 7, 22, 9, shade(wood, -22))
                        rect(6, 8, 20, 7, shade(wood, 6))
                        rect(13, 10, 6, 2, 0xFF74848B.toInt())
                        rect(5, 20, 22, 2, shade(wood, -22))
                    } else {
                        for (x in intArrayOf(10, 20)) rect(x, 4, 1, 23, shade(wood, -25))
                    }
                }
                0 -> {
                    val stone = 0xFF9CA7AC.toInt()
                    fill(stone)
                    rect(3, 3, 26, 3, shade(stone, 13))
                    rect(5, 9, 22, 18, shade(stone, -30))
                    rect(8, 11, 16, 12, 0xFF465258.toInt())
                    rect(10, 10, 12, 1, 0xFF465258.toInt())
                    rect(9, 23, 14, 2, 0xFF726B61.toInt())
                    rect(4, 28, 24, 2, shade(stone, 12))
                    for (x in 9..23 step 6) rect(x, 24, 2, 1, 0xFFBB9371.toInt())
                }
                1 -> {
                    val wood = 0xFFB99A71.toInt()
                    fill(wood)
                    for (i in intArrayOf(0, 30)) {
                        rect(i, 0, 2, 32, shade(wood, -26))
                        rect(0, i, 32, 2, shade(wood, -26))
                    }
                    for (i in intArrayOf(11, 20)) {
                        rect(i, 4, 1, 24, shade(wood, -19))
                        rect(4, i, 24, 1, shade(wood, -19))
                    }
                    for (x in intArrayOf(3, 27)) for (y in intArrayOf(3, 27))
                        rect(x, y, 2, 2, 0xFF818B8C.toInt())
                }
                2 -> {
                    val green = 0xFF8FA474.toInt()
                    fill(green)
                    for (x in 2..31 step 8) {
                        rect(x, 0, 2, 32, shade(green, -21))
                        rect(x + 2, 0, 2, 32, shade(green, 13))
                    }
                    for (y in 4..31 step 12) for (x in 6..31 step 8) {
                        rect(x, y, 2, 2, 0xFFDFD8B4.toInt())
                    }
                }
                3, 4 -> {
                    val green = if (tile == 3) 0xFFAABB89.toInt() else 0xFF8B9970.toInt()
                    fill(shade(green, -22))
                    rect(2, 2, 28, 28, green)
                    rect(5, 5, 22, 22, shade(green, 9))
                    rect(14, 8, 4, 16, shade(green, -13))
                    rect(8, 14, 16, 4, shade(green, -13))
                }
                5 -> glass()
                6, 7 -> {
                    val water = 0xFF80ACBB.toInt()
                    fill(water)
                    for (y in 2..31 step 8) {
                        val x = if (y % 16 == 2) 2 else 16
                        if (tile == 6) {
                            wrappedRect(x, y, 10, 2, shade(water, 17))
                            wrappedRect(x + 4, y + 3, 12, 1, shade(water, -12))
                        } else {
                            wrappedRect(y, x, 2, 10, shade(water, 17))
                            wrappedRect(y + 3, x + 4, 1, 12, shade(water, -12))
                        }
                    }
                }
                8 -> {
                    val lava = 0xFFC88662.toInt()
                    fill(lava)
                    for (row in 0..3) for (col in 0..3) {
                        val x = col * 8 + if (row % 2 == 0) 0 else 4
                        wrappedRect(x, row * 8, 6, 6, 0xFFE6B17A.toInt())
                        wrappedRect(x + 1, row * 8 + 1, 4, 2, 0xFFF0CA91.toInt())
                    }
                }
                10 -> {
                    val snow = 0xFFE0E9E5.toInt()
                    fill(snow)
                    for ((x, y) in arrayOf(2 to 4, 18 to 2, 10 to 14, 26 to 18, 2 to 26, 20 to 28))
                        rect(x, y, 4, 2, shade(snow, -10))
                    rect(20, 10, 6, 2, shade(snow, 9)); rect(6, 20, 4, 2, shade(snow, 9))
                }
                11 -> {
                    val ice = if (tint == null) 0xFF82BCE5.toInt()
                        else mix(tint, 0xFF388ACF.toInt(), .72f)
                    fill(ice)
                    // Broad blue frozen facets, with short glints instead of marble-like veins.
                    for (y in 0..31) for (x in 0..31) {
                        val amount = when {
                            x / 2 + y / 2 < 8 -> 11
                            x / 2 + y / 2 > 23 -> -10
                            else -> 0
                        }
                        data[y * 32 + x] = shade(ice, amount)
                    }
                    for (i in 0..3) rect(4 + i * 2, 10 - i * 2, 2, 2, shade(ice, 31))
                    for (i in 0..2) rect(21 + i * 2, 26 - i * 2, 2, 2, shade(ice, 23))
                }
                else -> error("Unknown procedural utility tile: $tile")
            }
        }

        fun item(tile: Int) {
            val metal = 0xFFA9B9BE.toInt()
            when (tile) {
                0 -> {
                    rect(15, 9, 2, 23, 0xFFAA8964.toInt())
                    rect(15, 1, 2, 2, shade(metal, 15))
                    rect(13, 3, 6, 3, metal); rect(11, 6, 10, 3, metal)
                    rect(15, 3, 2, 5, shade(metal, 23))
                    mirrorRect(11, 23, 3, 7, 0xFFDCD9C6.toInt())
                    mirrorRect(13, 26, 2, 5, 0xFFC2C7B5.toInt())
                }
                1, 2 -> {
                    rect(10, 3, 12, 2, shade(metal, -27))
                    mirrorRect(7, 5, 3, 10, shade(metal, -27))
                    rect(5, 12, 22, 5, shade(metal, -31))
                    rect(7, 17, 18, 9, metal)
                    rect(9, 26, 14, 5, metal)
                    rect(7, 13, 18, 3, if (tile == 2) 0xFF6CABD7.toInt() else 0xFF697C83.toInt())
                    rect(9, 18, 3, 10, shade(metal, 23))
                    rect(21, 17, 3, 9, shade(metal, -18))
                    rect(10, 29, 12, 2, shade(metal, -18))
                    if (tile == 2) rect(9, 13, 7, 1, 0xFFA5D6EA.toInt())
                }
                3 -> {
                    rect(13, 3, 6, 3, metal); rect(11, 6, 10, 9, metal)
                    rect(11, 15, 10, 14, 0xFFC5AC7C.toInt())
                    rect(13, 16, 2, 11, 0xFFE0C69A.toInt())
                    rect(10, 28, 12, 3, 0xFFA68D61.toInt())
                    rect(13, 6, 2, 7, shade(metal, 22))
                }
                4 -> {
                    rect(13, 15, 6, 17, 0xFFA17F59.toInt())
                    rect(13, 17, 2, 15, 0xFFC09B6B.toInt())
                    rect(10, 10, 12, 7, 0xFFCE9061.toInt())
                    rect(12, 5, 8, 10, 0xFFE5B673.toInt())
                    rect(14, 1, 4, 13, 0xFFE5B673.toInt())
                    rect(14, 8, 4, 7, 0xFFF3DCA4.toInt())
                }
                5 -> {
                    val stone = 0xFF6D718C.toInt()
                    fill(stone)
                    mirrorRect(2, 4, 6, 2, shade(stone, 12))
                    mirrorRect(4, 25, 4, 2, shade(stone, -14))
                    for (y in 5..24) {
                        val half = (9 - abs(y - 14)).coerceAtLeast(1) / 2 + 1
                        rect(16 - half, y, half * 2, 1, 0xFFB5A0D1.toInt())
                        rect(16 - half, y, half, 1, 0xFFD7C6E8.toInt())
                    }
                    rect(10, 28, 12, 1, shade(stone, -15))
                }
            }
        }

        fun flora(tile: Int) {
            val stem = 0xFF708F62.toInt()
            when (tile) {
                6 -> {
                    rect(15, 3, 2, 29, stem)
                    for (row in 0..4) {
                        val y = 7 + row * 4
                        val width = if (row < 3) 4 + row * 2 else 8 - (row - 3) * 2
                        mirrorRect(15 - width, y, width, 2, shade(stem, 12))
                        mirrorRect(15 - width, y - 2, 2, 2, shade(stem, 12))
                    }
                }
                10, 11 -> {
                    val cap = if (tile == 10) 0xFFC78079.toInt() else 0xFFB9A07C.toInt()
                    rect(13, 15, 6, 17, 0xFFD8C9AB.toInt())
                    rect(13, 17, 2, 15, 0xFFC4B696.toInt())
                    rect(5, 12, 22, 5, shade(cap, -15))
                    rect(7, 8, 18, 6, cap)
                    rect(11, 5, 10, 5, cap)
                    rect(13, 4, 6, 2, shade(cap, 12))
                    for ((x, y) in arrayOf(10 to 9, 19 to 9, 14 to 6, 14 to 13))
                        rect(x, y, 3, 2, 0xFFE9DBBB.toInt())
                }
                12 -> {
                    for (x in intArrayOf(5, 15, 25)) {
                        val y = if (x == 15) 2 else 8
                        rect(x, y, 2, 32 - y, stem)
                        rect(x - 1, y + 1, 4, 9, 0xFF9F805F.toInt())
                        rect(x, y + 2, 2, 6, 0xFFB0946D.toInt())
                    }
                    mirrorRect(9, 23, 2, 9, shade(stem, 13))
                    mirrorRect(7, 20, 2, 5, shade(stem, 13))
                }
                13 -> {
                    rect(13, 12, 6, 20, 0xFF8EA78D.toInt())
                    rect(15, 10, 2, 10, 0xFFA5BA9C.toInt())
                    mirrorRect(5, 18, 4, 8, 0xFF7D9B83.toInt())
                    mirrorRect(9, 24, 4, 8, 0xFF7D9B83.toInt())
                    mirrorRect(3, 16, 4, 2, 0xFFA5BA9C.toInt())
                }
                15 -> {
                    rect(13, 20, 6, 12, shade(stem, -17))
                    rect(4, 16, 24, 12, stem)
                    rect(7, 10, 18, 12, shade(stem, 8))
                    rect(11, 7, 10, 6, shade(stem, 15))
                    for ((x, y) in arrayOf(7 to 18, 20 to 18, 14 to 11, 14 to 24)) {
                        rect(x, y, 3, 3, 0xFFB87582.toInt())
                        rect(x, y, 2, 1, 0xFFD3A0A7.toInt())
                    }
                }
                1 -> {
                    for (x in intArrayOf(7, 15, 23)) {
                        val tip = if (x == 15) 2 else 8
                        rect(x, tip, 2, 32 - tip, stem)
                        for (y in tip..tip + 10 step 4) {
                            rect(x - 2, y + 1, 6, 2, 0xFFAC9AC2.toInt())
                            rect(x, y, 2, 2, 0xFFC7B8D6.toInt())
                        }
                    }
                }
                else -> {
                    val petal = when (tile) {
                        0 -> 0xFFF0EDDC.toInt()
                        2 -> 0xFFD4A2B5.toInt()
                        3 -> 0xFF91AECF.toInt()
                        4 -> 0xFFE5CE83.toInt()
                        5 -> 0xFFCA8B7D.toInt()
                        14 -> 0xFFD0A1AC.toInt()
                        else -> error("Unknown procedural flower tile: $tile")
                    }
                    rect(15, 14, 2, 18, stem)
                    mirrorRect(9, 23, 6, 2, shade(stem, 10))
                    mirrorRect(7, 21, 4, 2, shade(stem, 10))
                    rect(12, 3, 8, 6, petal); rect(12, 13, 8, 5, shade(petal, -7))
                    rect(7, 8, 7, 6, petal); rect(18, 8, 7, 6, petal)
                    rect(13, 8, 6, 6, 0xFFCCB36C.toInt())
                    rect(14, 9, 4, 2, 0xFFE6CD86.toInt())
                    if (tile == 14) {
                        rect(12, 8, 8, 6, shade(petal, -16))
                        rect(14, 9, 4, 3, shade(petal, 12))
                    }
                }
            }
        }

        fun cap(color: Int) {
            for (x in 0..31) {
                val depth = capDepth(x)
                rect(x, 0, 1, depth, color)
                rect(x, depth, 1, 1, shade(color, -20))
            }
            for (x in 2..31 step 8) rect(x, 2, 4, 2, shade(color, 13))
        }
        fun leaves(base: Int) {
            fill(base)
            for (row in 0..3) for (col in 0..3) {
                val x = col * 8 + if (row % 2 == 0) 0 else 4
                val y = row * 8
                wrappedRect(x, y + 2, 6, 4, shade(base, -16))
                wrappedRect(x + 2, y, 4, 4, shade(base, if ((row + col) % 2 == 0) 18 else 10))
                wrappedRect(x + 6, y + 6, 2, 2, Color.TRANSPARENT)
            }
        }
        fun ore(mineral: Int) {
            val c = mix(mineral, 0xFFDAD2C3.toInt(), .16f)
            for ((x, y) in arrayOf(4 to 6, 19 to 3, 25 to 17, 10 to 22, 2 to 28, 16 to 13)) {
                rect(x - 1, y, 5, 4, shade(c, -32)); rect(x, y, 3, 3, c)
                rect(x, y, 2, 1, shade(c, 29))
            }
        }
        fun groundcover(tile: Int) {
            val green = if (tile == 12) 0xFFBAAA72.toInt() else 0xFF779B62.toInt()
            if (tile == 3 || tile == 4) {
                for (y in 20..31) {
                    val inset = if (y < 24) 9 else 5
                    rect(inset, y, 32 - inset * 2, 1, shade(0xFFA0ABB4.toInt(), (31 - y) * 2 - 12))
                }
                if (tile == 4) rect(9, 20, 14, 3, green)
                return
            }
            if (tile == 15) {
                for ((x, y) in arrayOf(6 to 23, 15 to 18, 24 to 23)) {
                    rect(x, y, 2, 32 - y, shade(green, -16))
                    rect(x - 3, y - 2, 3, 3, shade(green, 12))
                    rect(x + 2, y - 2, 3, 3, shade(green, 12))
                    rect(x - 1, y - 5, 4, 3, shade(green, 20))
                }
                return
            }
            val wheat = tile in 8..11
            if (wheat) {
                val height = intArrayOf(10, 17, 24, 30)[tile - 8]
                val stalk = if (tile >= 10) 0xFFAA9B62.toInt() else green
                val grain = if (tile >= 10) 0xFFCEBC80.toInt() else shade(green, 15)
                // Three individually symmetric ears, arranged symmetrically around x=15.5.
                for (x in intArrayOf(5, 15, 25)) {
                    val tip = 32 - height + if (x == 15) 0 else 4
                    rect(x, tip, 2, 32 - tip, stalk)
                    if (tile >= 9) {
                        rect(x, tip, 2, 2, shade(grain, 15))
                        val pairs = if (tile >= 10) 3 else 2
                        for (row in 0 until pairs) {
                            val y = tip + 3 + row * 3
                            rect(x - 2, y, 2, 2, grain)
                            rect(x + 2, y, 2, 2, grain)
                        }
                    }
                    val leafY = maxOf(tip + 9, 26)
                    rect(x - 2, leafY, 2, 2, stalk)
                    rect(x + 2, leafY, 2, 2, stalk)
                    rect(x - 4, leafY - 2, 2, 3, shade(stalk, 9))
                    rect(x + 4, leafY - 2, 2, 3, shade(stalk, 9))
                }
                return
            }
            val height = when (tile) { 0 -> 12; 1 -> 22; else -> 28 }
            // A deliberate fan silhouette, with equal paired blades and open space at the tips.
            rect(15, 32 - height, 2, height, shade(green, 12))
            for (i in 0..2) {
                val x = 3 + i * 4
                val tip = (32 - height + (3 - i) * 4).coerceAtMost(28)
                val c = shade(green, if (i % 2 == 0) -13 else 8)
                mirrorRect(x, tip, 2, 6, c)
                mirrorRect(x + 2, tip + 4, 2, 32 - tip - 4, c)
            }
        }
    }
}
