package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.export.StudioExporter
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class StudioGxIntegrationTest {
    private val rate = 24_000
    private val track = Inst.SYNTH

    @Test fun firstChipSoundFitsTheTrackAndRemembersAnExplicitPianoAndBassSmoothing() {
        val session = StudioSession(StudioEngine(rate), StudioProject.blank())
        session.setPreset("dist_bass", track)
        session.setSynthBank(track, SynthBank.SCC)
        assertEquals(39, session.project.track(track).program)
        session.setChipProgram(track, 0)
        session.setChipSmoothBass(track, true)
        session.setSynthBank(track, SynthBank.STUDIO)
        val project = StudioProjectCodec.decode(StudioProjectCodec.encode(session.project))!!
        val restored = StudioSession(StudioEngine(rate), project)
        restored.setSynthBank(track, SynthBank.FAMICOM)
        assertEquals(0, restored.project.track(track).program)
        assertTrue(restored.project.track(track).chipSmoothBass)
        restored.setChipSmoothBass(track, false)
        restored.undo()
        assertTrue(restored.project.track(track).chipSmoothBass)
    }

    @Test fun keyboardAndAuditionPlayTheSameMiAtBothRegisters() {
        val outputRate = 48_000
        val block = 256
        for (bank in listOf(SynthBank.SCC, SynthBank.FAMICOM)) for (pitch in listOf(28, 64)) {
            fun start(live: Boolean): StudioEngine {
                val engine = StudioEngine(outputRate)
                val session = StudioSession(engine, StudioProject.blank().copy(space = 0f, echo = 0f))
                session.setSynthBank(track, bank)
                session.setChipProgram(track, 0)
                engine.trigger(track, pitch, 0.9f, recordHit = live)
                return engine
            }
            val keyboard = start(true)
            val audition = start(false)
            val keyL = FloatArray(block)
            val keyR = FloatArray(block)
            val listenL = FloatArray(block)
            val listenR = FloatArray(block)
            val crossings = mutableListOf<Int>()
            var previous = 0f
            repeat(120) { chunk ->
                if (chunk == 94) {
                    keyboard.release(track, pitch)
                    audition.release(track, pitch)
                }
                keyboard.render(keyL, keyR, block)
                audition.render(listenL, listenR, block)
                assertArrayEquals(keyL, listenL, 0f)
                assertArrayEquals(keyR, listenR, 0f)
                for (i in keyL.indices) {
                    if (chunk in 8 until 94 && previous <= 0f && keyL[i] > 0f) crossings += chunk * block + i
                    previous = keyL[i]
                }
            }
            assertTrue(crossings.size > 10)
            val frequency = (crossings.size - 1) * outputRate.toDouble() / (crossings.last() - crossings.first())
            assertEquals(if (pitch == 28) 40.87 else 326.97, frequency, 0.5)
        }
    }

    @Test fun emptyRhythmCellPreviewsTheSamePitchItAddsForBothBanks() {
        for (bank in listOf(SynthBank.SCC, SynthBank.FAMICOM)) {
            val session = StudioSession(StudioEngine(rate), StudioProject.blank())
            session.setKey(4)
            session.setSynthBank(track, bank)
            for ((program, pitch) in listOf(0 to 64, 32 to 40)) {
                session.setChipProgram(track, program)
                assertEquals(pitch, session.rhythmPreviewPitch(track, 0))
                session.setStep(track, 0, true)
                assertEquals(pitch, session.notesOf(track).single().pitch)
                session.setStep(track, 0, false)
            }
            session.addNote(0, 28, 1)
            session.addNote(1, 67, 1)
            session.addNote(2, 69, 2)
            assertEquals(69, session.rhythmPreviewPitch(track, 3))
            assertEquals(64, session.rhythmPreviewPitch(track, 4))
            session.setStep(track, 4, true)
            assertEquals(listOf(28, 67, 69, 64), session.notesOf(track).map { it.pitch })
        }
    }

    @Test fun selectedBankAndProgramSurviveSaveJournalAndUndo() {
        val empty = StudioProject.blank()
        val session = StudioSession(StudioEngine(rate), empty)
        assertEquals(SynthBank.STUDIO, StudioProjectCodec.decode(StudioProjectCodec.encode(empty))!!.track(track).synthBank)
        session.setSynthBank(track, SynthBank.SCC)
        session.setChipProgram(track, 24)
        session.setSynthBank(track, SynthBank.FAMICOM)
        val expected = session.project.track(track)
        val journal = StudioHistory.diff(empty, session.project, undoableOnly = false)
        assertEquals(expected, StudioHistory.apply(empty, journal, true).track(track))
        assertEquals(expected, StudioProjectCodec.decode(StudioProjectCodec.encode(session.project))!!.track(track))
        session.undo()
        assertEquals(SynthBank.SCC, session.project.track(track).synthBank)
        assertEquals(24, session.project.track(track).program)
        session.redo()
        session.setSynthBank(track, SynthBank.STUDIO)
        assertEquals(empty.track(track).synth, session.project.track(track).synth)
    }

    @Test fun bankChangesDuringPlaybackKeepTheClockAndReleaseHeldNotes() {
        val engine = StudioEngine(rate)
        val session = StudioSession(engine, StudioProject.blank())
        val left = FloatArray(512)
        val right = FloatArray(512)
        session.setSynthBank(track, SynthBank.SCC)
        session.setChipProgram(track, 80)
        engine.play()
        engine.trigger(track, 60)
        repeat(12) { engine.render(left, right, left.size) }
        val step = engine.currentStep
        assertTrue(step > 0)
        session.setSynthBank(track, SynthBank.FAMICOM)
        engine.trigger(track, 67)
        engine.render(left, right, left.size)
        session.setSynthBank(track, SynthBank.STUDIO)
        engine.trigger(track, 72)
        engine.glide(track, 60, 64)
        engine.render(left, right, left.size)
        assertEquals(step, engine.currentStep)
        assertEquals(3, engine.synth.activeVoices)
        engine.release(track, 64)
        engine.release(track, 67)
        engine.release(track, 72)
        repeat(100) { engine.render(left, right, left.size) }
        assertEquals(0, engine.synth.activeVoices)
    }

    @Test fun liveAudioMatchesExportAndMidiCarriesTheProgram() {
        val rendered = listOf(SynthBank.STUDIO, SynthBank.SCC, SynthBank.FAMICOM).map { bank ->
            val engine = StudioEngine(rate)
            val session = StudioSession(engine, StudioProject.blank())
            session.setSynthBank(track, bank)
            session.setChipProgram(track, 24)
            session.addNote(0, 60, 4, 0.8f, track)
            val bytes = ByteArrayOutputStream()
            // Le premier bloc suffit pour comparer l'attaque et le début de la note.
            StudioExporter.renderWav(session.project, rate, bytes) { false }
            val wav = bytes.toByteArray()
            val left = FloatArray(1024)
            val right = FloatArray(1024)
            engine.trigger(track, 60, 0.8f)
            engine.render(left, right, left.size)
            for (i in left.indices) {
                val pcm = (wav[45 + i * 4].toInt() shl 8) or (wav[44 + i * 4].toInt() and 255)
                assertEquals(pcm, (left[i] * 32767f).roundToInt())
            }
            assertTrue(left.any { it > 0.01f })
            val midi = StudioExporter.midiBytes(session.project)
            val program = if (bank == SynthBank.STUDIO) 81 else 24
            assertTrue((0 until midi.lastIndex).any { (midi[it].toInt() and 255) == 0xc0 && midi[it + 1].toInt() == program })
            left
        }
        for (a in rendered.indices) for (b in 0 until a) assertFalse(rendered[a].contentEquals(rendered[b]))
    }

    @Test fun anotherTrackBeingMutedPreservesTheChipChannelInMidi() {
        val session = StudioSession(StudioEngine(rate), StudioProject.blank())
        val second = session.addTrack(TrackKind.SYNTH)
        session.setSynthBank(second, SynthBank.SCC)
        session.setChipProgram(second, 73)
        session.setHidden(track, true)
        fun hasProgramChange(channel: Int): Boolean {
            val midi = StudioExporter.midiBytes(session.project)
            return (0 until midi.lastIndex).any {
                (midi[it].toInt() and 255) == (0xc0 or channel) && midi[it + 1].toInt() == 73
            }
        }
        assertTrue(hasProgramChange(1))
        session.moveTrack(second, 0)
        assertTrue(hasProgramChange(0))
    }
}
