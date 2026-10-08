package com.Atom2Universe.app.midi.sf2

import kotlin.math.*

/**
 * Filtre biquad RBJ dédié à l'EQ 10 bandes MIDI.
 *
 * Implémente 3 modes selon le cookbook RBJ "Audio EQ Cookbook" :
 * - LOW_SHELF  : étagère basse (bande 0, 32 Hz)
 * - PEAK       : pic paramétrique (bandes 1-8, 64 Hz – 8 kHz)
 * - HIGH_SHELF : étagère haute (bande 9, 16 kHz)
 *
 * Forme directe I (stable pour EQ à gain faible).
 * Anti-dénormal intégré pour éviter les pics CPU.
 */
class MidiEqBiquadFilter {

    enum class Type { LOW_SHELF, PEAK, HIGH_SHELF }

    // Coefficients biquad (normalisés par a0)
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    // État interne Direct Form I
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    private var bypassed = true

    /**
     * Configure le filtre.
     *
     * @param sampleRate Fréquence d'échantillonnage (Hz)
     * @param freqHz     Fréquence centrale (Hz)
     * @param gainDb     Gain en dB (négatif = coupure, positif = amplification)
     * @param q          Facteur Q (1.41 pour un EQ graphique)
     * @param type       Type de filtre (LOW_SHELF, PEAK, HIGH_SHELF)
     */
    fun configure(sampleRate: Int, freqHz: Float, gainDb: Float, q: Float, type: Type) {
        if (gainDb == 0f) {
            bypassed = true
            return
        }
        bypassed = false

        val A = 10f.pow(gainDb / 40f)          // sqrt(10^(dB/20))
        val w0 = 2f * PI.toFloat() * freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)

        val rawB0: Float
        val rawB1: Float
        val rawB2: Float
        val rawA0: Float
        val rawA1: Float
        val rawA2: Float

        when (type) {
            Type.PEAK -> {
                rawB0 = 1f + alpha * A
                rawB1 = -2f * cosW0
                rawB2 = 1f - alpha * A
                rawA0 = 1f + alpha / A
                rawA1 = -2f * cosW0
                rawA2 = 1f - alpha / A
            }
            Type.LOW_SHELF -> {
                val sqrtA = sqrt(A)
                val alphaS = sinW0 / 2f * sqrt((A + 1f / A) * (1f / q - 1f) + 2f)
                rawB0 = A * ((A + 1f) - (A - 1f) * cosW0 + 2f * sqrtA * alphaS)
                rawB1 = 2f * A * ((A - 1f) - (A + 1f) * cosW0)
                rawB2 = A * ((A + 1f) - (A - 1f) * cosW0 - 2f * sqrtA * alphaS)
                rawA0 = (A + 1f) + (A - 1f) * cosW0 + 2f * sqrtA * alphaS
                rawA1 = -2f * ((A - 1f) + (A + 1f) * cosW0)
                rawA2 = (A + 1f) + (A - 1f) * cosW0 - 2f * sqrtA * alphaS
            }
            Type.HIGH_SHELF -> {
                val sqrtA = sqrt(A)
                val alphaS = sinW0 / 2f * sqrt((A + 1f / A) * (1f / q - 1f) + 2f)
                rawB0 = A * ((A + 1f) + (A - 1f) * cosW0 + 2f * sqrtA * alphaS)
                rawB1 = -2f * A * ((A - 1f) + (A + 1f) * cosW0)
                rawB2 = A * ((A + 1f) + (A - 1f) * cosW0 - 2f * sqrtA * alphaS)
                rawA0 = (A + 1f) - (A - 1f) * cosW0 + 2f * sqrtA * alphaS
                rawA1 = 2f * ((A - 1f) - (A + 1f) * cosW0)
                rawA2 = (A + 1f) - (A - 1f) * cosW0 - 2f * sqrtA * alphaS
            }
        }

        // Normaliser par a0
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    /**
     * Traite un échantillon unique. Direct Form I.
     */
    fun process(input: Float): Float {
        if (bypassed) return input

        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2

        x2 = x1
        x1 = input
        y2 = y1
        y1 = output

        // Anti-dénormal
        if (y1 > -1e-18f && y1 < 1e-18f) y1 = 0f
        if (y2 > -1e-18f && y2 < 1e-18f) y2 = 0f

        return output
    }

    /**
     * Vide l'état interne (à appeler lors d'un reset).
     */
    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
}
