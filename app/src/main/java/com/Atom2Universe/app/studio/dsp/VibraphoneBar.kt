package com.Atom2Universe.app.studio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Une lame de vibraphone. Chaque vibration a une fréquence, un niveau au coup de maillet et
 * un niveau résiduel. Les coefficients ont été mesurés sur douze notes du programme GM 11 de
 * Perso.sf2 (canaux gauche et droit réunis en puissance). Aucun échantillon à charger.
 */
internal class VibraphoneBar(private val sampleRate: Int) {
    private class Mode(val ratio: Double, val strike: Float, val ring: Float, val transientT60: Float)
    private class Register(val lastKey: Int, val ringT60: Float, vararg val modes: Mode)

    private class Resonance {
        var ring = 0f
        var transient = 0f
        var decay = 0f
        var phaseSin = 0.0
        var phaseCos = 1.0
        var stepSin = 0.0
        var stepCos = 1.0
        var bandGain = 0f

        fun strike(mode: Mode, rate: Int) {
            ring = mode.ring
            transient = mode.strike - ring
            decay = Fast.decayCoef(mode.transientT60, rate)
            phaseSin = 0.0
            phaseCos = 1.0
        }

        fun tune(cycles: Double) {
            // Éteindre le partiel avant Nyquist, y compris pendant un glissé.
            bandGain = ((0.5 - cycles) / 0.05).coerceIn(0.0, 1.0).toFloat()
            stepSin = sin(2.0 * PI * cycles)
            stepCos = cos(2.0 * PI * cycles)
        }

        fun next(): Float {
            val s = phaseSin * stepCos + phaseCos * stepSin
            phaseCos = phaseCos * stepCos - phaseSin * stepSin
            phaseSin = s
            val out = s.toFloat() * (ring + transient) * bandGain
            transient *= decay
            return out
        }
    }

    private val modes = Array(4) { Resonance() }
    private var register = registers[0]
    private var velocity = 0f
    private var previous = 0f
    private var carry = 0f
    private val carryDecay = Fast.decayCoef(0.012f, sampleRate)

    fun strike(note: Int, velocity: Float, retrigger: Boolean): Float {
        register = registers.first { note <= it.lastKey }
        // Mesures à vélocité MIDI 96 ; la voix applique déjà le premier facteur de vélocité.
        this.velocity = velocity / (REFERENCE_VELOCITY * REFERENCE_VELOCITY)
        // Préserver la dernière sortie lors d'une refrappe sur une lame encore vibrante.
        carry = if (retrigger) previous else 0f
        for (i in register.modes.indices) modes[i].strike(register.modes[i], sampleRate)
        return register.ringT60
    }

    fun tune(fundamental: Float, upper: Float) {
        modes[0].tune(fundamental.toDouble())
        // Le second oscillateur du patch règle l'accord des résonances supérieures (deux octaves par défaut).
        for (i in 1 until register.modes.size) modes[i].tune(upper * register.modes[i].ratio / 4.0)
    }

    fun next(): Float {
        var out = 0f
        for (i in register.modes.indices) out += modes[i].next()
        previous = out * velocity + carry
        carry *= carryDecay
        return previous
    }

    companion object {
        private const val REFERENCE_VELOCITY = 96f / 127f
        const val DEFAULT_DECAY = 4f

        // Dernière touche de la zone, T60 du son tenu en secondes, puis pour chaque mode :
        // fréquence / fondamentale, amplitude à la frappe, amplitude résiduelle, T60 du transitoire.
        private val registers = arrayOf(
            Register(54, 15.2f, Mode(1.0, 0f, 0.012f, 0.57f), Mode(4.01, 0.075f, 0.052f, 1.13f),
                Mode(11.09, 0.219f, 0.024f, 0.62f), Mode(25.75, 0.042f, 0f, 0.32f)),
            Register(56, 15.1f, Mode(1.0, 0f, 0.015f, 0.29f), Mode(4.02, 0.077f, 0.052f, 2.04f),
                Mode(10.91, 0.342f, 0.004f, 0.41f), Mode(23.78, 0.023f, 0.001f, 0.27f)),
            Register(58, 14.8f, Mode(1.0, 0f, 0.029f, 0.25f), Mode(4.0, 0.108f, 0.040f, 0.53f),
                Mode(10.74, 0.330f, 0.001f, 0.34f), Mode(22.67, 0.021f, 0f, 0.53f)),
            Register(61, 15f, Mode(1.0, 0f, 0.077f, 0.23f), Mode(4.01, 0.175f, 0.071f, 2.42f),
                Mode(10.54, 0.043f, 0f, 0.48f), Mode(17.44, 0.135f, 0.001f, 0.24f)),
            Register(64, 14.2f, Mode(1.0, 0.022f, 0.014f, 0.95f), Mode(4.01, 0.171f, 0.058f, 2.42f),
                Mode(10.13, 0.201f, 0f, 0.34f), Mode(16.20, 0.022f, 0f, 0.37f)),
            Register(68, 13.4f, Mode(1.0, 0.003f, 0.114f, 0.12f), Mode(4.01, 0.224f, 0.048f, 1.23f),
                Mode(14.92, 0.017f, 0f, 0.29f), Mode(21.11, 0.017f, 0f, 0.23f)),
            Register(72, 11.3f, Mode(1.0, 0f, 0.152f, 0.12f), Mode(4.00, 0.211f, 0.040f, 1.04f),
                Mode(8.94, 0.041f, 0f, 0.19f), Mode(19.81, 0.003f, 0f, 0.32f)),
            Register(77, 10.1f, Mode(1.0, 0.265f, 0.200f, 0.95f), Mode(4.00, 0.093f, 0.005f, 0.62f),
                Mode(12.55, 0.003f, 0f, 0.34f)),
            Register(82, 9.9f, Mode(1.0, 0.257f, 0.173f, 0.62f), Mode(4.00, 0.044f, 0.001f, 0.44f),
                Mode(7.50, 0.007f, 0f, 0.29f)),
            Register(85, 9.5f, Mode(1.0, 0.275f, 0.155f, 2.42f), Mode(3.92, 0.058f, 0f, 0.32f)),
            Register(87, 9.5f, Mode(1.0, 0.250f, 0.200f, 2.42f), Mode(3.76, 0.034f, 0f, 0.37f)),
            Register(127, 9.3f, Mode(1.0, 0.352f, 0.147f, 1.23f), Mode(3.77, 0.008f, 0f, 0.27f)),
        )
    }
}
