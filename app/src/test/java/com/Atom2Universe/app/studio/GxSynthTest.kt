package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.dsp.GxBanks
import com.Atom2Universe.app.studio.dsp.GxSynth
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GxSynthTest {
    @Test fun programsAndWaveLevelsMatchTheReferenceBanks() {
        assertEquals(29, (0..127).map { GxBanks.wave(false, it) }.distinct().size)
        assertEquals(5, (0..127).map { GxBanks.wave(true, it) }.distinct().size)
        assertEquals(28, (0..127).map { GxBanks.envelope(it) }.distinct().size)
        // Deuxième point de la guitare nylon, amplitude signée × gain de la fiche.
        assertEquals(78 * 140 / 65536f, GxBanks.wave(false, 24).sample(1.0, 0.25), 0f)
        assertEquals(16 * 255 / 65536f, GxBanks.wave(true, 24).sample(1.0, 0.25), 0f)
    }

    @Test fun pulseAveragesTheCoveredStepsAcrossTheEdgeAndCycleBoundary() {
        val pulse = GxBanks.wave(false, 80)
        val high = 112 * 98 / 65536f
        val low = -128 * 98 / 65536f
        assertEquals((high + low) / 2, pulse.sample(15.5, 1.0), 1e-7f)
        assertEquals((low + high * 3) / 4, pulse.sample(31.5, 2.0), 1e-7f)
    }

    @Test fun keyboardReleaseAndNaturalDecayUseTheReferenceFrameCounts() {
        for (rate in listOf(22050, 44100, 48000)) {
            val synth = GxSynth(rate)
            synth.select(false, 0)
            synth.noteOn(69, 1f)
            val first = FloatArray(rate / 10)
            assertTrue(synth.renderMono(first, first.size))
            assertTrue(first.any { it != 0f })
            synth.noteOff(69)
            val tail = FloatArray(2205 + 2)
            synth.renderMono(tail, tail.size)
            assertEquals(0, synth.activeVoices)
            assertTrue(tail.takeLast(20).any { it != 0f })
            assertEquals(0f, tail.last(), 0f)

            synth.noteOn(60, 1f)
            val block = FloatArray(48000)
            repeat(4) { synth.renderMono(block, block.size) }
            assertEquals(0, synth.activeVoices)
            // Le lead carré conserve sa tenue indéfiniment.
            synth.select(true, 80)
            synth.noteOn(60, 0.7f)
            repeat(4) { synth.renderMono(block, block.size) }
            assertTrue(synth.holds(60))
        }
    }

    @Test fun effectsUseClockedNoiseAndAOneShotPitchDrop() {
        val synth = GxSynth(48000)
        val out = FloatArray(4800)
        for (program in listOf(122, 126)) {
            synth.select(false, program)
            synth.noteOn(69, 1f)
            synth.renderMono(out, out.size)
            val changes = (1 until out.size).count { out[it] != out[it - 1] }
            assertTrue(changes in 450..490)
            assertTrue(out.min() < -0.15f && out.max() < 0.067f)
            synth.clear()
        }
        synth.select(true, 118)
        synth.noteOn(69, 1f)
        synth.renderMono(out, out.size)
        assertTrue(out.take(1600).any { it != 0f })
        assertTrue(out.drop(1600).all { it == 0f })
        assertEquals(0, synth.activeVoices)
    }

    @Test fun changingBanksKeepsHeldNotesAndGlidesInTheSameVoice() {
        val synth = GxSynth(48000, 2)
        val original = GxSynth(48000, 2)
        val actual = FloatArray(513)
        val expected = FloatArray(513)
        for (s in listOf(synth, original)) {
            s.select(false, 24)
            s.noteOn(64, 1f)
            s.renderMono(actual, 127)
        }
        synth.select(true, 24)
        synth.midiChannel = 15
        synth.renderMono(actual, actual.size)
        original.renderMono(expected, expected.size)
        assertArrayEquals(expected, actual, 0f)
        synth.noteOn(67, 0.75f)
        synth.glide(67, 72, 0.75f)
        assertFalse(synth.holds(67))
        assertTrue(synth.holds(72))
        assertEquals(2, synth.activeVoices)
        synth.clear()
        assertFalse(synth.renderMono(actual, actual.size))
    }
}
