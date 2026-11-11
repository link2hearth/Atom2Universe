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
        val rendered = SynthBank.entries.map { bank ->
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
