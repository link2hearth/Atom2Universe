package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FFT complexe en place, radix-2, double précision, pour une taille [size] (puissance de deux) fixée
 * à la construction : les tables sont calculées une fois. Sert à la réduction de bruit et à
 * l'étirement extrême ; indépendante d'Android et de `FFTProcessor` (qui, lui, produit des spectrogrammes).
 */
class Fft(val size: Int) {

    private val cosT = DoubleArray(size / 2)
    private val sinT = DoubleArray(size / 2)
    private val rev = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "La taille doit être une puissance de deux : $size" }
        for (i in 0 until size / 2) {
            cosT[i] = cos(2.0 * PI * i / size)
            sinT[i] = sin(2.0 * PI * i / size)
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Transformée directe (non normalisée) ou, avec [inverse], transformée inverse (divisée par [size]). */
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        for (i in 0 until size) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosT[k]
                    val wi = sign * sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr
                    im[j + half] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += len
            }
            len *= 2
        }
        if (inverse) {
            val s = 1.0 / size
            for (i in 0 until size) { re[i] *= s; im[i] *= s }
        }
    }
}
