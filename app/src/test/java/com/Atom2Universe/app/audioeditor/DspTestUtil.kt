package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.dsp.Effect
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
}
