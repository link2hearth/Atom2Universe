package com.Atom2Universe.app.games.caves.node

import kotlin.math.floor
import kotlin.math.max

/** Native 32px proposals. The preview calls this exact recipe; no image tracing or resampling.
 * Coordinates wrap before every noise/shape lookup, including shading across tile boundaries.
 * The moss block uses CUSHIONS (original B); the other proposals remain unassigned.
 */
internal object MossTextureCandidates {
    const val SIZE = 32
    enum class Variant { VELVET, CUSHIONS, CUSHIONS_CRISP }

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

    fun pixels(variant: Variant): IntArray =
        IntArray(SIZE * SIZE) { pixel(variant, it % SIZE, it / SIZE) }

    /** Also used by the preview to render a continuous 96px patch across nine tiles. */
    fun pixel(variant: Variant, worldX: Int, worldY: Int): Int {
        val x = Math.floorMod(worldX, SIZE)
        val y = Math.floorMod(worldY, SIZE)
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
            Variant.CUSHIONS, Variant.CUSHIONS_CRISP -> {
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
