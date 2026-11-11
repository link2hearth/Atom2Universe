package com.Atom2Universe.app.studio.dsp

import kotlin.math.*

/** Bandes de bruit au tiers d'octave. Chaque résonateur a un gain normalisé en puissance. */
internal class ResonantNoise(rate: Int) {
    private val source = Noise()
    private val a1 = DoubleArray(BANDS)
    private val a2 = DoubleArray(BANDS)
    private val inputGain = DoubleArray(BANDS)
    private val first = DoubleArray(BANDS)
    private val second = DoubleArray(BANDS)
    private val olderInput = DoubleArray(BANDS)
    private val lastInput = DoubleArray(BANDS)

    init {
        for (i in 0 until BANDS) {
            val hz = 35.0 * 2.0.pow(i / 3.0)
            if (hz >= 0.49 * rate) continue
            val angle = 2 * PI * hz / rate
            val alpha = sin(angle) / (2 * 5.5)
            a1[i] = 2 * cos(angle) / (1 + alpha)
            a2[i] = (1 - alpha) / (1 + alpha)
            inputGain[i] = sqrt(3 * alpha / (1 + alpha))
        }
    }

    fun reset() {
        first.fill(0.0)
        second.fill(0.0)
        olderInput.fill(0.0)
        lastInput.fill(0.0)
    }

    fun next(level: FloatArray, delta: FloatArray, from: Int): Float {
        var sum = 0f
        for (i in 0 until BANDS) {
            val index = from + i
            val white = source.next().toDouble()
            val value = inputGain[i] * (white - olderInput[i]) + a1[i] * first[i] - a2[i] * second[i]
            second[i] = first[i]
            first[i] = value
            olderInput[i] = lastInput[i]
            lastInput[i] = white
            sum += value.toFloat() * level[index]
            level[index] += delta[index]
        }
        return sum
    }

    companion object {
        const val BANDS = 28
    }
}
