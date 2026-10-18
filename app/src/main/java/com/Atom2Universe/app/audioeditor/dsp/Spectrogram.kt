package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Le spectrogramme d'une source, cuit **en coordonnées du monde** (une source, un temps), jamais en coordonnées d'écran :
 * l'audio est découpé en tuiles de [TILE_COLS] colonnes (une colonne = [HOP] trames), chaque tuile est calculée une fois
 * puis simplement dessinée, étirée selon le zoom. Faire défiler ou zoomer ne recalcule rien.
 *
 * Verticalement, [ROWS] lignes sur une échelle de mel (les graves ont plus de place que sur une échelle linéaire) ;
 * la ligne 0 de la tuile est la plus aiguë, prête à devenir la première rangée d'une image.
 */
object Spectrogram {

    const val FFT_SIZE = 2048
    const val HOP = 1024
    const val ROWS = 128
    const val TILE_COLS = 512

    /** Trames de source couvertes par une tuile. */
    const val TILE_FRAMES = TILE_COLS * HOP

    /** Plage de niveaux affichée : [MIN_DB] → noir, [MAX_DB] → blanc (pleine échelle d'un sinus = 0 dB). */
    const val MIN_DB = -90f
    const val MAX_DB = -10f

    private fun mel(f: Double) = 2595.0 * log10(1.0 + f / 700.0)
    private fun melInverse(m: Double) = 700.0 * (Math.pow(10.0, m / 2595.0) - 1.0)

    /** Quelles cases de la FFT alimentent chaque ligne : de [lo] (inclus) à [hi] (exclu). Dépend de la fréquence d'échantillonnage. */
    class Layout(val sampleRate: Int) {
        val lo = IntArray(ROWS)
        val hi = IntArray(ROWS)

        init {
            val bins = FFT_SIZE / 2
            val maxMel = mel(sampleRate / 2.0)
            for (r in 0 until ROWS) {
                val f0 = melInverse(maxMel * r / ROWS)
                val f1 = melInverse(maxMel * (r + 1) / ROWS)
                val b0 = (f0 * FFT_SIZE / sampleRate).toInt().coerceIn(0, bins - 1)
                val b1 = (f1 * FFT_SIZE / sampleRate).toInt().coerceIn(b0 + 1, bins)
                lo[r] = b0
                hi[r] = b1
            }
        }

        /** La fréquence centrale (Hz) de la ligne [row] (0 = la plus grave). */
        fun centerHz(row: Int): Double {
            val maxMel = mel(sampleRate / 2.0)
            return melInverse(maxMel * (row + 0.5) / ROWS)
        }
    }

    /**
     * Cuit une tuile. [mono] contient `TILE_FRAMES + FFT_SIZE` échantillons à partir de la trame `début de la tuile − FFT_SIZE / 2`
     * (la première fenêtre est donc centrée sur le début de la tuile). Rend `TILE_COLS × ROWS` niveaux de 0 à 255, rangée par
     * rangée, la rangée 0 étant la plus aiguë.
     */
    fun bake(mono: FloatArray, layout: Layout): ByteArray {
        val fft = Fft(FFT_SIZE)
        val hann = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / FFT_SIZE) }
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val mag = DoubleArray(FFT_SIZE / 2)
        val out = ByteArray(TILE_COLS * ROWS)
        val norm = FFT_SIZE / 4.0
        for (c in 0 until TILE_COLS) {
            val start = c * HOP + HOP / 2
            for (i in 0 until FFT_SIZE) {
                val k = start + i
                re[i] = if (k < mono.size) mono[k] * hann[i] else 0.0
                im[i] = 0.0
            }
            fft.transform(re, im)
            for (k in mag.indices) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) / norm
            for (r in 0 until ROWS) {
                var m = 0.0
                for (k in layout.lo[r] until layout.hi[r]) if (mag[k] > m) m = mag[k]
                val db = 20 * log10(m + 1e-9)
                val level = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)
                out[(ROWS - 1 - r) * TILE_COLS + c] = (level * 255).toInt().toByte()
            }
        }
        return out
    }

    /** Du niveau (0 à 255) à une couleur ARGB : noir, violet, rose, orange, jaune pâle. */
    val PALETTE: IntArray = run {
        val stops = intArrayOf(0x000004, 0x3B0F70, 0xB63679, 0xFB8861, 0xFCFDBF)
        IntArray(256) { i ->
            val t = i / 255.0 * (stops.size - 1)
            val k = t.toInt().coerceAtMost(stops.size - 2)
            val f = t - k
            fun ch(shift: Int): Int {
                val a = (stops[k] shr shift) and 0xFF
                val b = (stops[k + 1] shr shift) and 0xFF
                return (a + (b - a) * f).toInt()
            }
            (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    /** Les pixels ARGB d'une tuile cuite. */
    fun toPixels(levels: ByteArray): IntArray = IntArray(levels.size) { PALETTE[levels[it].toInt() and 0xFF] }

    /** Le numéro de la tuile qui contient la trame de source [frame]. */
    fun tileOf(frame: Long): Int = (max(0L, frame) / TILE_FRAMES).toInt()
}
