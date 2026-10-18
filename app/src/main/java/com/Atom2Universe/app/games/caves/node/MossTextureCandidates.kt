package com.Atom2Universe.app.games.caves.node

import kotlin.math.floor
import kotlin.math.max

/** Native 32px proposals. The preview calls this exact recipe; no image tracing or resampling.
 * Coordinates wrap before every noise/shape lookup, including shading across tile boundaries.
 * The moss block uses COTTON (cloud-like puffs); the others remain unassigned.
 */
internal object MossTextureCandidates {
    const val SIZE = 32
    enum class Variant { VELVET, CUSHIONS, CUSHIONS_CRISP, FUZZ, COTTON }

    private val velvetColors = intArrayOf(
        0x4D603B, 0x5A7042, 0x687E49, 0x778C53, 0x869B5F, 0x96AA70, 0xA6B67E
    )
    private val cushionColors = intArrayOf(
        0x465A35, 0x536A3C, 0x647C45, 0x789052, 0x8BA261, 0x9CB373, 0xAEC184
    )
    private val crispColors = intArrayOf(
        0x465A35, 0x536A3C, 0x647C45, 0x778D49, 0x879B50, 0x9DAF61, 0xB0BF78
    )
    // Irregularly spaced cushions straddle all four edges of the tile.
    private val cushions = arrayOf(
        floatArrayOf(2f, 3f, 6f, 5f), floatArrayOf(14f, 1f, 7f, 6f),
        floatArrayOf(25f, 6f, 7f, 6f), floatArrayOf(7f, 12f, 7f, 6f),
        floatArrayOf(18f, 13f, 6f, 5f), floatArrayOf(30f, 18f, 6f, 7f),
        floatArrayOf(11f, 23f, 8f, 7f), floatArrayOf(22f, 26f, 7f, 7f),
        floatArrayOf(1f, 29f, 6f, 6f)
    )

    private val fuzzColors = intArrayOf(0x405A1B, 0x4F6C22, 0x5F7E29, 0x709032, 0x84A43C)

    /** The fuzz is authored on 16 px and doubled, so its pixels stay large like the pack PNG. */
    private const val FUZZ_SIZE = 16

    private fun cell(x: Int, y: Int, salt: Int): Double {
        var hash = Math.floorMod(x, FUZZ_SIZE) * 374761393 xor (Math.floorMod(y, FUZZ_SIZE) * 668265263) xor
            (salt * 1274126177)
        hash = (hash xor (hash ushr 13)) * 1274126177
        return ((hash xor (hash ushr 16)) and 65535) / 65535.0
    }

    /** Uniform olive fuzz, no distinct shapes: fine grain, soft 2px patches, short vertical
     * blades (lighter or darker) and rare dark specks. Every lookup wraps, so it tiles. */
    private fun fuzzPixel(x: Int, y: Int): Int {
        var tone = .5 + (cell(x, y, 3) - .5) * .30 + (cell(x / 4, y / 4, 5) - .5) * .30 +
            (cell(x / 2, y / 2, 9) - .5) * .75
        for (k in 0..2) if (cell(x, y - k, 21) > .97) tone += if (cell(x, y - k, 22) > .5) -.30 else .26
        if (cell(x, y, 41) > .985) tone -= .35
        return 0xFF000000.toInt() or fuzzColors[(tone.coerceIn(0.0, .999) * fuzzColors.size).toInt()]
    }

    /** Muted olive tones, chosen so the game's vivid palette (CavePalette) lands on a soft moss
     * green: several of these sit near its anchors, so they look greyer than the final result. */
    private val cottonColors = intArrayOf(
        0x3C482D, 0x485634, 0x56683F, 0x63794A, 0x8D9375, 0x97A579, 0xA9B085
    )

    /** Same cushion layout as CUSHIONS, each one turned into a cotton puff: a crenellated
     * outline from two satellite lobes, a bright top and a darker rim. Painted back to front
     * (by row) so each rim separates a puff from the one behind it. x, y, rx, ry per puff. */
    private val cottonPuffs: List<FloatArray> = buildList {
        for ((i, c) in cushions.sortedBy { it[1] }.withIndex()) {
            for (k in 0..1) {
                val angle = (i * 2.4 + k * 3.1)
                add(floatArrayOf(
                    c[0] + (Math.cos(angle) * c[2] * .62).toFloat(),
                    c[1] + (Math.sin(angle) * c[3] * .5).toFloat(),
                    c[2] * .58f, c[3] * .55f))
            }
            add(c)
        }
    }

    private fun cottonPixel(x: Int, y: Int): Int {
        var index = if (noise(x, y, 2, 47) > .6) 1 else 0
        val grain = noise(x, y, 2, 47) - .5
        val edge = (noise(x, y, 2, 83) - .5) * .34
        for (p in cottonPuffs) {
            val dx = wrappedDistance(x - p[0].toDouble())
            val dy = wrappedDistance(y - p[1].toDouble())
            val d = dx * dx / (p[2] * p[2]) + dy * dy / (p[3] * p[3]) + edge
            if (d >= 1.0) continue
            val ny = dy / p[3]
            val tone = .95 - d * .85 - ny * .2 + grain * .14
            var shade = (tone * cottonColors.size).toInt().coerceIn(1, cottonColors.size - 1)
            // Broken tufts on the lit part, and a rim that stays dark on the underside.
            if (shade >= 4 && noise(x + 1, y + 2, 4, 157) > .62) shade++
            if (d > .74) shade = minOf(shade, if (ny < 0) 3 else 2)
            index = shade.coerceAtMost(cottonColors.size - 1)
        }
        return 0xFF000000.toInt() or cottonColors[index]
    }

    fun pixels(variant: Variant): IntArray =
        IntArray(SIZE * SIZE) { pixel(variant, it % SIZE, it / SIZE) }

    /** Also used by the preview to render a continuous 96px patch across nine tiles. */
    fun pixel(variant: Variant, worldX: Int, worldY: Int): Int {
        val x = Math.floorMod(worldX, SIZE)
        val y = Math.floorMod(worldY, SIZE)
        if (variant == Variant.COTTON) return cottonPixel(x, y)
        if (variant == Variant.FUZZ) return fuzzPixel(x / 2, y / 2)
        val grain = noise(x, y, 2, 47) - .5
        val colors: IntArray
        val shade: Double
        when (variant) {
            Variant.VELVET -> {
                colors = velvetColors
                val broad = noise(x, y, 8, 11)
                val lobes = noise(x + 3, y + 1, 4, 29)
                shade = (.50 + (broad - .5) * 1.05 + (lobes - .5) * .55 + grain * .16)
            }
            Variant.CUSHIONS, Variant.CUSHIONS_CRISP, Variant.FUZZ, Variant.COTTON -> {
                colors = cushionColors
                val warpX = (noise(x, y, 4, 71) - .5) * 2.0
                val warpY = (noise(x + 1, y + 2, 4, 93) - .5) * 2.0
                var height = 0.0
                for (cushion in cushions) {
                    val dx = wrappedDistance(x - cushion[0].toDouble()) + warpX
                    val dy = wrappedDistance(y - cushion[1].toDouble()) + warpY
                    val radius = dx * dx / (cushion[2] * cushion[2]) +
                        dy * dy / (cushion[3] * cushion[3])
                    if (radius < 1.0) {
                        val dome = 1.0 - radius
                        height = max(height, dome * (.81 - dx * .020 - dy * .030))
                    }
                }
                shade = .13 + height * .90 + grain * .15
                if (variant == Variant.CUSHIONS_CRISP) {
                    val original = (shade.coerceIn(0.0, .999) * cushionColors.size).toInt()
                    // Preserve B's exact silhouette and recessed pixels. Only repaint the face:
                    // flat moss plates and clustered tips replace its smooth radial lighting.
                    val color = if (original < 3) cushionColors[original] else {
                        val plates = noise(x + 1, y + 3, 4, 113)
                        val tips = noise(x + 2, y + 1, 2, 157)
                        val index = when {
                            tips > .70 && plates > .48 -> 6
                            tips > .57 && plates > .42 -> 5
                            plates > .43 -> 4
                            else -> 3
                        }
                        crispColors[index]
                    }
                    return 0xFF000000.toInt() or color
                }
            }
        }
        val index = (shade.coerceIn(0.0, .999) * colors.size).toInt()
        return 0xFF000000.toInt() or colors[index]
    }

    private fun wrappedDistance(value: Double): Double =
        value - floor((value + SIZE / 2) / SIZE) * SIZE

    private fun noise(x: Int, y: Int, cellSize: Int, salt: Int): Double {
        val px = Math.floorMod(x, SIZE); val py = Math.floorMod(y, SIZE)
        val gx = px / cellSize; val gy = py / cellSize
        val period = SIZE / cellSize
        fun corner(cx: Int, cy: Int): Double {
            var hash = Math.floorMod(cx, period) * 374761393 xor
                (Math.floorMod(cy, period) * 668265263) xor (salt * 1274126177)
            hash = (hash xor (hash ushr 13)) * 1274126177
            return ((hash xor (hash ushr 16)) and 65535) / 65535.0
        }
        fun smooth(v: Double) = v * v * (3 - 2 * v)
        val tx = smooth((px % cellSize).toDouble() / cellSize)
        val ty = smooth((py % cellSize).toDouble() / cellSize)
        val top = corner(gx, gy) * (1 - tx) + corner(gx + 1, gy) * tx
        val bottom = corner(gx, gy + 1) * (1 - tx) + corner(gx + 1, gy + 1) * tx
        return top * (1 - ty) + bottom * ty
    }
}
