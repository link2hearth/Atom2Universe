package com.Atom2Universe.app.graphics

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** Version fixe du plasma de NativeSunDrawable, calculable sans GPU dès Android 8.
 * Chaque appel retourne un bitmap indépendant que son propriétaire peut recycler.
 */
internal object SunArtwork {
    fun createBitmap(size: Int = 256): Bitmap {
        require(size in 32..1024)
        val pixels = IntArray(size * size)
        // Cadrage resserré pour une icône fixe sans grandes éruptions.
        val scale = size * 0.43f
        val aa = 1.5f / scale
        for (y in 0 until size) for (x in 0 until size) {
            val u = (x + 0.5f - size * 0.5f) / scale
            val v = (y + 0.5f - size * 0.5f) / scale
            val radius = sqrt(u * u + v * v)
            if (radius > 1.16f) continue
            val disk = 1f - smooth(1f - aa, 1f + aa, radius)
            val outside = (radius - 1f).coerceAtLeast(0f)
            val halo = exp(-outside * 25f) * 0.12f * (1f - smooth(1.06f, 1.16f, radius))
            var red = 0f
            var green = 0f
            var blue = 0f
            if (disk > 0f) {
                // Même projection sphérique, bruit, déformation et palette que le shader à t=0.
                val z = sqrt((1f - u * u - v * v).coerceAtLeast(0f))
                val px = u * 5.5f
                val py = v * 5.5f
                val pz = z * 5.5f
                val wx = fbm(px + 1.2f, py, pz)
                val wy = fbm(px + 11.7f, py - 1.6f + 11.7f, pz + 11.7f)
                val wz = fbm(px + 27.3f, py + 28.5f, pz + 27.3f)
                val fx = px + (wx - 0.5f) * 2.8f
                val fy = py + (wy - 0.5f) * 2.8f + 1.04f
                val fz = pz + (wz - 0.5f) * 2.8f
                val broad = fbm(fx * 1.8f, fy * 1.8f, fz * 1.8f)
                val grain = noise(fx * 23f + 3.6f, fy * 23f, fz * 23f)
                val filament = (1f - kotlin.math.abs(2f * noise(fx * 7f, fy * 7f - 3.2f, fz * 7f) - 1f)).pow(9)
                val heat = (broad * 0.9f + grain * 0.13f + filament * 0.23f).coerceIn(0f, 1f)
                val warm = smooth(0.24f, 0.52f, heat)
                val gold = smooth(0.48f, 0.73f, heat)
                val white = smooth(0.72f, 0.9f, heat) * 0.7f
                val light = 0.68f + 0.32f * z.pow(0.4f)
                val rim = exp(-kotlin.math.abs(radius - 1f) * 65f)
                red = mix(mix(mix(0.67f, 1f, warm), 1f, gold), 1f, white) * light + 0.22f * rim
                green = mix(mix(mix(0.055f, 0.37f, warm), 0.86f, gold), 0.97f, white) * light + 0.07f * rim
                blue = mix(mix(mix(0.002f, 0.008f, warm), 0.23f, gold), 0.68f, white) * light + 0.002f * rim
            }
            val alpha = disk + halo * (1f - disk)
            if (alpha <= 0f) continue
            // setPixels attend des couleurs non prémultipliées.
            red = (red * disk + halo * (1f - disk)) / alpha
            green = (green * disk + 0.13f * halo * (1f - disk)) / alpha
            blue = (blue * disk + 0.008f * halo * (1f - disk)) / alpha
            pixels[y * size + x] = Color.argb(channel(alpha), channel(red), channel(green), channel(blue))
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smooth(a: Float, b: Float, value: Float): Float {
        val t = ((value - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private fun fract(x: Float) = x - floor(x)
    private fun hash(x: Float, y: Float, z: Float): Float {
        var a = fract(x * 0.1031f)
        var b = fract(y * 0.1031f)
        var c = fract(z * 0.1031f)
        val d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f)
        a += d; b += d; c += d
        return fract((a + b) * c)
    }
    private fun noise(x: Float, y: Float, z: Float): Float {
        val ix = floor(x); val iy = floor(y); val iz = floor(z)
        val fx = smooth(0f, 1f, x - ix)
        val fy = smooth(0f, 1f, y - iy)
        val fz = smooth(0f, 1f, z - iz)
        return mix(
            mix(mix(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx),
                mix(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy),
            mix(mix(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx),
                mix(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy), fz)
    }
    private fun fbm(x: Float, y: Float, z: Float): Float {
        var px = x; var py = y; var pz = z
        var amplitude = 0.53f
        var result = 0f
        repeat(4) {
            result += amplitude * noise(px, py, pz)
            px = px * 2.03f + 17.1f
            py = py * 2.03f + 9.2f
            pz = pz * 2.03f + 13.7f
            amplitude *= 0.48f
        }
        return result
    }
}
