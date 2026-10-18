package com.Atom2Universe.app.audioeditor.core

import kotlin.math.roundToInt

/** Une vignette en pixels ARGB opaques. */
class Thumb(val pixels: IntArray, val width: Int, val height: Int)

/**
 * La vignette d'un projet pour la galerie : la forme d'onde de son mixage.
 *
 * On ne mixe pas tout le projet (une heure d'audio serait longue à relire pour une image de
 * 320 pixels) : pour chaque colonne on mixe seulement une courte fenêtre au début de la tranche de
 * temps qu'elle représente. C'est un échantillonnage, pas une mesure exacte, mais une forme d'onde
 * de 320 colonnes n'a pas besoin de plus.
 */
object Thumbnail {

    const val WINDOW = 1024

    fun render(
        project: Project,
        provider: SampleProvider,
        width: Int = 320,
        height: Int = 120,
        background: Int = 0xFF1E2230.toInt(),
        wave: Int = 0xFF6FB6FF.toInt(),
    ): Thumb {
        val pixels = IntArray(width * height) { background }
        val mid = height / 2
        val total = project.length
        if (total <= 0) {
            for (x in 0 until width) pixels[mid * width + x] = wave
            return Thumb(pixels, width, height)
        }

        val mixer = Mixer(provider)
        val l = FloatArray(WINDOW)
        val r = FloatArray(WINDOW)
        val lo = FloatArray(width)
        val hi = FloatArray(width)
        var peak = 0f
        for (x in 0 until width) {
            val start = total * x / width
            val n = minOf(WINDOW.toLong(), maxOf(1L, total * (x + 1) / width - start)).toInt()
            mixer.render(project, start, n, l, r)
            var mn = 0f
            var mx = 0f
            for (i in 0 until n) {
                val a = minOf(l[i], r[i])
                val b = maxOf(l[i], r[i])
                if (a < mn) mn = a
                if (b > mx) mx = b
            }
            lo[x] = mn
            hi[x] = mx
            peak = maxOf(peak, -mn, mx)
        }
        // Un enregistrement faible reste lisible : on remplit la hauteur (sans jamais amplifier le bruit de fond au-delà de ×8).
        val scale = if (peak > 1e-4f) minOf(8f, 1f / peak) else 1f
        val half = (height / 2 - 1).coerceAtLeast(1)
        for (x in 0 until width) {
            val up = (hi[x] * scale).coerceIn(-1f, 1f)
            val down = (lo[x] * scale).coerceIn(-1f, 1f)
            val top = (mid - up * half).roundToInt().coerceIn(0, height - 1)
            val bottom = (mid - down * half).roundToInt().coerceIn(0, height - 1)
            val a = minOf(top, mid)
            val b = maxOf(bottom, mid)
            for (y in a..b) pixels[y * width + x] = wave
        }
        return Thumb(pixels, width, height)
    }
}
