package com.Atom2Universe.app.pixelart.core

/**
 * Arithmétique de couleurs ARGB « non prémultipliées » (le format de `Bitmap.setPixels`).
 * Volontairement sans aucune dépendance Android : tout le cœur du pixel art se teste en JVM.
 */
object PixelColor {

    const val TRANSPARENT = 0

    fun alpha(c: Int) = c ushr 24
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** `src` posé sur `dst` (composition « over » classique, alpha droit). */
    fun over(dst: Int, src: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        if (da == 0) return src
        val t = da * (255 - sa)
        val denom = sa * 255 + t          // = alpha de sortie × 255
        val outA = (denom + 127) / 255
        val r = (red(src) * sa * 255 + red(dst) * t + denom / 2) / denom
        val g = (green(src) * sa * 255 + green(dst) * t + denom / 2) / denom
        val b = (blue(src) * sa * 255 + blue(dst) * t + denom / 2) / denom
        return argb(outA, r, g, b)
    }

    /** Pose `src` sur `dst` avec un mode de fusion et une opacité de calque (0..255). */
    fun blend(dst: Int, src: Int, mode: BlendMode, opacity: Int): Int {
        var sa = src ushr 24
        if (opacity < 255) sa = (sa * opacity + 127) / 255
        if (sa == 0) return dst
        var s = withAlpha(src, sa)
        if (mode != BlendMode.NORMAL) {
            val da = dst ushr 24
            if (da > 0) {
                // La couleur source est mélangée au résultat du mode, proportionnellement à
                // l'opacité du fond : sur un fond transparent, le mode ne change rien.
                val r = mix(red(src), channelBlend(mode, red(dst), red(src)), da)
                val g = mix(green(src), channelBlend(mode, green(dst), green(src)), da)
                val b = mix(blue(src), channelBlend(mode, blue(dst), blue(src)), da)
                s = argb(sa, r, g, b)
            }
        }
        return over(dst, s)
    }

    private fun mix(a: Int, b: Int, weightB255: Int): Int = (a * (255 - weightB255) + b * weightB255 + 127) / 255

    private fun channelBlend(mode: BlendMode, b: Int, s: Int): Int = when (mode) {
        BlendMode.NORMAL -> s
        BlendMode.MULTIPLY -> (b * s + 127) / 255
        BlendMode.SCREEN -> 255 - ((255 - b) * (255 - s) + 127) / 255
        BlendMode.ADD -> minOf(255, b + s)
    }

    /** Écart maximal entre deux couleurs sur les quatre canaux (deux transparents sont égaux). */
    fun distance(a: Int, b: Int): Int {
        val aa = a ushr 24
        val ba = b ushr 24
        if (aa == 0 && ba == 0) return 0
        var d = kotlin.math.abs(aa - ba)
        d = maxOf(d, kotlin.math.abs(red(a) - red(b)))
        d = maxOf(d, kotlin.math.abs(green(a) - green(b)))
        d = maxOf(d, kotlin.math.abs(blue(a) - blue(b)))
        return d
    }

    // ---- TSV -----------------------------------------------------------------------------

    /** Retourne `[teinte 0..360, saturation 0..1, valeur 0..1]`. */
    fun toHsv(c: Int, out: FloatArray = FloatArray(3)): FloatArray {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (max == 0f) 0f else d / max
        out[2] = max
        return out
    }

    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(
            alpha.coerceIn(0, 255),
            ((r + m) * 255f + 0.5f).toInt().coerceIn(0, 255),
            ((g + m) * 255f + 0.5f).toInt().coerceIn(0, 255),
            ((b + m) * 255f + 0.5f).toInt().coerceIn(0, 255),
        )
    }

    // ---- Texte ---------------------------------------------------------------------------

    /** `#RRGGBB` ou `#AARRGGBB` (l'alpha n'est écrit que s'il n'est pas opaque). */
    fun toHex(c: Int): String {
        val rgb = String.format("%02X%02X%02X", red(c), green(c), blue(c))
        return if (alpha(c) == 255) "#$rgb" else String.format("#%02X", alpha(c)) + rgb
    }

    /** Accepte `RGB`, `RRGGBB`, `AARRGGBB`, avec ou sans `#`. Retourne null si illisible. */
    fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        return when (t.length) {
            3 -> {
                val r = t[0].digitToIntOrNull(16) ?: return null
                val g = t[1].digitToIntOrNull(16) ?: return null
                val b = t[2].digitToIntOrNull(16) ?: return null
                argb(255, r * 17, g * 17, b * 17)
            }
            6 -> t.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> t.toLongOrNull(16)?.toInt()
            else -> null
        }
    }
}
