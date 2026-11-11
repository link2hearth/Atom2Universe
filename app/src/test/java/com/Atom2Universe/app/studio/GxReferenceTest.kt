package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.dsp.GxSynth
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun bothPianosKeepTheLowPitchAudibleAcrossTheEntireThirdOctave() {
        for (famicom in listOf(false, true)) for (soft in listOf(false, true)) {
            val synth = GxSynth(rate).also { it.smoothBass = soft; it.select(famicom, 0) }
            for (note in listOf(24, 33, 36, 48, 53, 55, 59)) {
                synth.clear()
                synth.noteOn(note, 1f)
                val audio = FloatArray(rate / 2).also { synth.renderMono(it, it.size) }
                val pitch = 261.63 * 2.0.pow((note - 60) / 12.0 - 14.0 / 1200.0)
                val fundamental = magnitude(audio, pitch)
                val label = "Famicom=$famicom, doux=$soft, MIDI=$note"
                assertTrue(label, fundamental > 100.0)
                for (h in listOf(31, 33)) {
                    assertTrue("$label, H$h", magnitude(audio, h * pitch) < fundamental * 0.001)
                }
                if (note <= 48) {
                    val octave = magnitude(audio, 2 * pitch) / fundamental
                    assertTrue("$label, octave=$octave", octave in 0.10..0.26)
                }
            }
        }
    }

    @Test fun keyboardPianosKeepTheirOwnOvertonesWithoutTheWhistleFromMiddleCUp() {
        for (outputRate in listOf(22_050, 44_100, 48_000)) for (famicom in listOf(false, true)) {
            val synth = GxSynth(outputRate).also { it.select(famicom, 0) }
            for (note in listOf(60, 62, 64, 72, 84, 96, 108)) {
                synth.clear()
                synth.noteOn(note, 0.9f)
                val audio = FloatArray(outputRate / 2).also { synth.renderMono(it, it.size) }
                val pitch = 261.63 * 2.0.pow((note - 60) / 12.0 - 14.0 / 1200.0)
                val fundamental = magnitude(audio, pitch, outputRate)
                val label = "Famicom=$famicom, MIDI=$note, Hz=$outputRate"
                assertTrue(label, fundamental > 50)
                // Les répliques H31/H33 et les partiels au-delà de Nyquist se replient à la lecture.
                val spurs = if (pitch * 3 > outputRate / 2) listOf(3, 31, 33) else listOf(31, 33)
                for (h in spurs) {
                    val image = magnitude(audio, pitch * h, outputRate)
                    assertTrue("$label, H$h = ${image / fundamental}", image < fundamental * 0.001)
                }
                if (note <= 72) {
                    val third = magnitude(audio, 3 * pitch, outputRate) / fundamental
                    assertEquals(label, if (famicom) 0.112 else 0.001, third, 0.012)
                }
            }
        }
    }

    @Test fun bassSofteningFadesOutOnAGlideIntoTheMiddleRegister() {
        val plain = GxSynth(rate)
        val soft = GxSynth(rate).also { it.smoothBass = true }
        val a = FloatArray(rate / 2)
        val b = FloatArray(a.size)
        plain.noteOn(33, 1f)
        soft.noteOn(33, 1f)
        plain.renderMono(a, a.size)
        soft.renderMono(b, b.size)
        for (note in listOf(60, 81)) {
            plain.glide(if (note == 60) 33 else 60, note, 1f)
            soft.glide(if (note == 60) 33 else 60, note, 1f)
            plain.renderMono(a, rate / 20)
            soft.renderMono(b, rate / 20)
            plain.renderMono(a, a.size)
            soft.renderMono(b, b.size)
            assertArrayEquals(a, b, 0f)
        }
    }

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
    private fun magnitude(audio: FloatArray, frequency: Double, sampleRate: Int = rate): Double {
        val start = sampleRate * 12 / 100
        val length = sampleRate / 5
        var real = 0.0
        var imag = 0.0
        for (i in 0 until length) {
            val sample = audio[start + i] * (0.5 - 0.5 * cos(2 * PI * i / (length - 1)))
            val phase = 2 * PI * frequency * i / sampleRate
            real += sample * cos(phase)
            imag += sample * sin(phase)
        }
        return sqrt(real * real + imag * imag)
    }
}
