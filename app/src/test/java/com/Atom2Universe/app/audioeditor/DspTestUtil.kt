package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.dsp.Effect
import com.Atom2Universe.app.audioeditor.dsp.Fft
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Outils partagés par les tests d'effets : signaux de test et mesures. */
object DspTest {
    const val RATE = 44100

    fun sine(n: Int, freq: Double, amp: Float = 0.25f, rate: Int = RATE) =
        FloatArray(n) { (amp * sin(2 * PI * freq * it / rate)).toFloat() }

    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var s = 0.0
        for (i in from until to) s += x[i].toDouble() * x[i]
        return sqrt(s / (to - from))
    }

    fun peak(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        var p = 0f
        for (i in from until to) p = maxOf(p, abs(x[i]))
        return p
    }

    fun mean(x: FloatArray): Double = x.fold(0.0) { a, v -> a + v } / x.size

    fun db(ratio: Double): Double = 20 * log10(ratio)

    /**
     * Gain en dB d'un effet à la fréquence [freq] : un sinus d'une seconde entre dans l'effet, on compare
     * le RMS de la seconde moitié (le régime est établi) à celui de l'entrée.
     */
    fun gainDb(effect: Effect, freq: Double, amp: Float = 0.25f, channels: Int = 1, rate: Int = RATE): Double {
        val n = rate
        val x = sine(n, freq, amp, rate)
        val out = effect.processInMemory(Array(channels) { x.copyOf() }, rate)[0]
        return db(rms(out, n / 2, n) / rms(x, n / 2, n))
    }

    /** Fréquence estimée par les passages à zéro montants (signaux quasi sinusoïdaux), sur le tronçon central. */
    fun zeroCrossFreq(x: FloatArray, rate: Int = RATE): Double {
        val a = x.size / 10
        val b = x.size - a
        var crossings = 0
        for (i in a + 1 until b) if (x[i - 1] < 0f && x[i] >= 0f) crossings++
        return crossings / ((b - a).toDouble() / rate)
    }

    /** Fréquence dominante (en Hz) d'un tronçon de [size] échantillons commençant en [from], par FFT et fenêtre de Hann. */
    fun dominantFreq(x: FloatArray, from: Int, size: Int = 16384, rate: Int = RATE): Double {
        val fft = Fft(size)
        val re = DoubleArray(size) { x[from + it] * (0.5 - 0.5 * Math.cos(2 * Math.PI * it / size)) }
        val im = DoubleArray(size)
        fft.transform(re, im)
        var best = 1
        for (b in 1 until size / 2) if (re[b] * re[b] + im[b] * im[b] > re[best] * re[best] + im[best] * im[best]) best = b
        return best * rate.toDouble() / size
    }
}
