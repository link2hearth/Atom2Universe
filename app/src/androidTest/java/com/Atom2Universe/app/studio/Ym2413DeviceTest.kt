package com.Atom2Universe.app.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.Ym2413Synth
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.export.StudioExporter
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Ym2413DeviceTest {
    private val rate = 48_000

    private fun note(program: Int, velocity: Float = 1f, brightness: Float = .5f, attack: Float = .5f): FloatArray {
        val synth = Ym2413Synth(rate)
        try {
            synth.select(program, brightness, attack)
            synth.noteOn(69, velocity)
            return FloatArray(rate / 4).also { audio -> synth.renderMono(audio, audio.size) }
        } finally {
            synth.close()
        }
    }

    @Test fun allRomPatchesAreAudibleAndDistinctAndThePadOnlyChangesTheUserPatch() {
        val rom = (1..15).map { note(it) }
        for ((i, a) in rom.withIndex()) {
            assertTrue("Timbre ${i + 1}", a.all { it.isFinite() } && a.any { abs(it) > .0001f })
            for (b in rom.take(i)) assertFalse(a.contentEquals(b))
        }
        assertArrayEquals(rom[2], note(3, brightness = 0f, attack = 1f), 0f)
        assertFalse(note(0, brightness = 0f).contentEquals(note(0, brightness = 1f)))
        assertFalse(note(0, attack = 0f).contentEquals(note(0, attack = 1f)))
    }

    @Test fun customBrightnessChangesAHeldNoteAndTheChipKeepsNineVoices() {
        Ym2413Synth(rate).use { moving ->
            Ym2413Synth(rate).use { fixed ->
                fun block(chip: Ym2413Synth) = FloatArray(2048).also { chip.renderMono(it, it.size) }
                for (chip in listOf(moving, fixed)) {
                    chip.select(0, .35f, 0f)
                    chip.noteOn(60, 1f)
                }
                assertArrayEquals(block(fixed), block(moving), 0f)
                moving.select(0, .95f, 0f)
                assertFalse(block(fixed).contentEquals(block(moving)))
                assertTrue(moving.holds(60))
                for (key in 61..70) moving.noteOn(key, .8f)
                assertEquals(9, moving.activeVoices)
                assertFalse(moving.holds(61))
                assertTrue((62..70).all { moving.holds(it) })
            }
        }
    }

    @Test fun carrierAttenuationMatchesMeasuredYamahaVolumeSteps() {
        // Source : andete/ym2413, analyze/volume/amplitudes.txt, mesures de la puce.
        val measured = listOf(255, 181, 127, 90, 63, 45, 31)
        fun rms(a: FloatArray) = sqrt(a.sumOf { it.toDouble() * it } / a.size)
        val full = rms(note(8))
        measured.forEachIndexed { step, amplitude ->
            val velocity = 10.0.pow(-3.0 * step / 20.0).toFloat()
            assertEquals("Atténuation $step", amplitude / 255.0, rms(note(8, velocity)) / full, .018)
        }
    }

    @Test fun savedCustomPadMatchesLivePlayingInWavExport() {
        val engine = StudioEngine(rate)
        try {
            val track = Inst.SYNTH
            val session = StudioSession(engine, StudioProject.blank())
            session.setSynthBank(track, SynthBank.FM)
            session.setFmProgram(track, 0)
            session.setFmPad(track, .81f, .29f)
            session.endFmPad()
            session.addNote(0, 64, 4, .9f, track)
            val project = StudioProjectCodec.decode(StudioProjectCodec.encode(session.project))!!
            assertEquals(session.project.track(track), project.track(track))
            val out = ByteArrayOutputStream()
            StudioExporter.renderWav(project, rate, out) { false }
            val wav = out.toByteArray()
            engine.trigger(track, 64, .9f)
            val left = FloatArray(1024)
            engine.render(left, FloatArray(left.size), left.size)
            assertTrue(left.any { abs(it) > .0001f })
            for (i in left.indices) {
                val pcm = (wav[45 + i * 4].toInt() shl 8) or (wav[44 + i * 4].toInt() and 255)
                assertEquals("Trame $i", pcm, (left[i].coerceIn(-1f, 1f) * 32767).roundToInt())
            }
        } finally {
            engine.close()
        }
    }
}
