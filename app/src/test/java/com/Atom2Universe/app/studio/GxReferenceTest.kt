package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.dsp.GxSynth
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Mesures sur deux WAV exportés par GXSCC 236E : canal 1, vélocité 100, 48 kHz, detune 14 cents, filtre coupé. */
class GxReferenceTest {
    private val rate = 48000
    private val programs = listOf(0, 24, 32, 40, 80) // Piano, guitare nylon, basse, violon, lead carré.

    @Test fun timbresEtNiveauxRelatifsDuTemoinScc() = checkBank(
        false,
        listOf(
            listOf(0.001, 0.001, 0.001), listOf(0.001, 0.216, 0.096), listOf(0.733, 0.375, 0.079),
            listOf(0.403, 0.291, 0.171), listOf(0.0, 0.332, 0.198),
        ),
        listOf(1.029, 0.636, 1.489, 0.889, 1.0),
    )

    @Test fun timbresEtNiveauxRelatifsDuTemoinFamicom() = checkBank(
        true,
        List(3) { listOf(0.001, 0.112, 0.041) } + List(2) { listOf(0.0, 0.332, 0.198) },
        listOf(0.938, 0.784, 1.565, 1.0, 1.0),
    )

    private fun checkBank(famicom: Boolean, expectedHarmonics: List<List<Double>>, expectedLevels: List<Double>) {
        val levels = programs.mapIndexed { index, program ->
            val synth = GxSynth(rate)
            val note = if (program == 32) 57 else 69
            synth.select(famicom, program)
            synth.noteOn(note, 100f / 127f)
            val audio = FloatArray(rate * 4 / 10)
            synth.renderMono(audio, audio.size)

            val frequency = 440.0 * 2.0.pow((note - 69) / 12.0 - 14.0 / 1200.0)
            val fundamental = magnitude(audio, frequency)
            listOf(2, 3, 5).forEachIndexed { i, harmonic ->
                assertEquals("Programme $program, harmonique $harmonic", expectedHarmonics[index][i],
                    magnitude(audio, frequency * harmonic) / fundamental, 0.012)
            }
            val start = rate / 10
            sqrt((start until audio.size).sumOf { audio[it].toDouble() * audio[it] } / (audio.size - start))
        }
        // Studio applique son propre gain maître. Le lead commun aux deux banques sert de référence.
        levels.forEachIndexed { i, level ->
            assertEquals("Niveau du programme ${programs[i]}", expectedLevels[i], level / levels.last(), 0.025)
        }
    }

    /** Projection sinusoïdale sur une fenêtre de Hann, de 120 à 320 ms après l'attaque. */
    private fun magnitude(audio: FloatArray, frequency: Double): Double {
        val start = rate * 12 / 100
        val length = rate / 5
        var real = 0.0
        var imag = 0.0
        for (i in 0 until length) {
            val sample = audio[start + i] * (0.5 - 0.5 * cos(2 * PI * i / (length - 1)))
            val phase = 2 * PI * frequency * i / rate
            real += sample * cos(phase)
            imag += sample * sin(phase)
        }
        return sqrt(real * real + imag * imag)
    }
}
