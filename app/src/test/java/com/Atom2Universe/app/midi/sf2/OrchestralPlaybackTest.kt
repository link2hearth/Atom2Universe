package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.studio.dsp.PolySynth
import com.Atom2Universe.app.studio.engine.SynthBank
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestralPlaybackTest {
    private val rate = 48000
    private val bank = OrchestralSoundBank()

    private fun newSynth() = Sf2Synthesizer(bank.file, rate, 8).apply {
        globalGain = 0.2f
        setReverbPreset(-1)
        setChorusPreset(-1)
        setForceSequentialRendering(true)
    }

    private fun record(synth: Sf2Synthesizer, blocks: Int): FloatArray {
        val signal = FloatArray(blocks * 512)
        repeat(blocks) { block ->
            val left = FloatArray(512)
            synth.render(left, FloatArray(512), left.size)
            left.copyInto(signal, block * 512)
        }
        return signal
    }

    @Test fun eachProgramPlaysItsNativeVoiceThroughSf2() {
        val synth = newSynth()
        try {
            for (program in 0..127) {
                bank.preparePrograms(setOf(program))
                val profile = bank.file.getPreset(0, program)!!.regions.single().orchestralProgram!!.profile()
                val key = if (profile == null) 64 else (profile.firstKey + profile.lastKey) / 2
                synth.reset()
                synth.programChange(0, program)
                synth.noteOn(0, key, 100)
                val out = record(synth, 16)
                assertTrue("GM $program", out.all(Float::isFinite) && out.maxOf { abs(it) } > 1e-5f)
            }
        } finally { synth.release() }
    }

    @Test fun pianoVibraphoneAndViolinMatchStudioAcrossBothKindsOfVoice() {
        for (program in listOf(0, 11, 40)) {
            val synth = newSynth()
            val studio = PolySynth(rate, 1)
            try {
                bank.preparePrograms(setOf(program))
                synth.programChange(0, program)
                synth.noteOn(0, 64, 110)
                val rendered = record(synth, 16)
                studio.selectBank(SynthBank.ORCHESTRAL, program, 0)
                studio.noteOn(64, 110 / 127f)
                val reference = FloatArray(rendered.size)
                val scratch = FloatArray(rendered.size)
                studio.render(reference, scratch, scratch, scratch, 0, reference.size)
                // Comparer la forme d'onde après l'attaque, indépendamment du gain et du panoramique.
                var xy = 0.0
                var xx = 0.0
                var yy = 0.0
                for (i in 512 until reference.size) {
                    xy += reference[i] * rendered[i]
                    xx += reference[i] * reference[i]
                    yy += rendered[i] * rendered[i]
                }
                assertTrue("GM $program", xy / sqrt(xx * yy) > 0.999)
            } finally { studio.close(); synth.release() }
        }
    }

    @Test fun aProgramChangeKeepsSoundingNotesAndOtherChannelsIntact() {
        bank.preparePrograms(setOf(0, 11, 40))
        val audio = (0..2).map { arrangement ->
            val synth = newSynth()
            try {
                if (arrangement != 1) synth.noteOn(0, 64, 92)
                if (arrangement != 0) {
                    synth.programChange(1, 40)
                    synth.noteOn(1, 67, 80)
                }
                record(synth, 6)
                if (arrangement != 1) {
                    // Référence : la nouvelle note de vibraphone est sur un canal libre.
                    // Dans le mélange elle change le programme du canal qui tient déjà le piano.
                    val channel = if (arrangement == 0) 2 else 0
                    synth.programChange(channel, 11)
                    synth.noteOn(channel, 72, 90)
                }
                record(synth, 12)
            } finally { synth.release() }
        }
        assertTrue(audio[0].maxOf { abs(it) } > 0.001f)
        assertTrue(audio[1].maxOf { abs(it) } > 0.001f)
        assertTrue(audio[2].indices.all { i -> abs(audio[2][i] - audio[0][i] - audio[1][i]) < 1e-4f })
    }

    @Test fun thePedalKeepsTheNativePianoRingingUntilRelease() {
        val synth = newSynth()
        try {
            synth.controlChange(0, 64, 127)
            synth.noteOn(0, 64, 100)
            record(synth, 8)
            synth.noteOff(0, 64)
            assertTrue(record(synth, 96).takeLast(512).any { abs(it) > 1e-4f })
            synth.controlChange(0, 64, 0)
            assertTrue(record(synth, 96).takeLast(512).all { it == 0f })
        } finally { synth.release() }
    }

    @Test fun percussionUsesTheBuiltInStudioKit() {
        val synth = newSynth()
        try {
            val sounds = listOf(36, 38, 42, 46).map { key ->
                synth.reset()
                synth.programChange(9, 8) // Room kit falls back to the built-in kit.
                synth.noteOn(9, key, 120)
                record(synth, 4).also { assertTrue(it.maxOf { x -> abs(x) } > 0.001f) }
            }
            assertEquals(4, sounds.map { it.contentHashCode() }.distinct().size)
        } finally { synth.release() }
    }
}
