package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.engine.PadPosition
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.export.StudioExporter
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Export : le WAV est bien formé, a la bonne durée et sonne ; le MIDI est lisible et contient les bonnes notes. */
class StudioExportTest {

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun project(): StudioProject {
        val engine = StudioEngine(48_000)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        return s.project
    }

    @Test
    fun heldPadSurvivesJournalAndOverridesPatternGesturesInTheAudioExport() {
        val original = project()
        val session = StudioSession(StudioEngine(48_000), original)
        session.beginGesture()
        session.recordGesture(0.0, 0.9f, 0.8f)
        session.endGesture()
        val automated = session.project
        val held = PadPosition(0.75f, 0.25f)
        session.setHeldPad(held)
        val restored = StudioHistory.apply(automated, StudioHistory.diff(automated, session.project, false), true)
        assertEquals(held, restored.heldPad)
        assertEquals(held, StudioProjectCodec.decode(StudioProjectCodec.encode(restored))!!.heldPad)

        fun wav(p: StudioProject) = ByteArrayOutputStream().also { StudioExporter.renderWav(p, 24000, it) }.toByteArray()
        val fixed = wav(restored)
        assertTrue(fixed.contentEquals(wav(original.copy(heldPad = held))))
        session.setHeldPad(null)
        assertFalse(fixed.contentEquals(wav(session.project)))
        assertTrue(session.project.pattern.hasAutomation)
    }

    @Test
    fun wavIsWellFormedAndAudible() {
        val p = project()
        val out = ByteArrayOutputStream()
        assertTrue(StudioExporter.renderWav(p, 48_000, out))
        val b = out.toByteArray()
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals("data", String(b, 36, 4))
        val frames = StudioExporter.frameCount(p, 48_000)
        assertEquals(frames * 4, le32(b, 40).toLong())
        assertEquals(44 + frames * 4, b.size.toLong())
        assertEquals(b.size - 8, le32(b, 4))
        // Du son au début, un peu de queue à la fin, jamais de dépassement (le limiteur y veille)
        var peak = 0
        for (i in 0 until 48_000 step 2) peak = maxOf(peak, abs(((b[44 + i * 2 + 1].toInt() shl 8) or (b[44 + i * 2].toInt() and 0xFF))))
        assertTrue("silence au début ($peak)", peak > 2000)
    }

    @Test
    fun wavLengthFollowsTheTempoAndTheSong() {
        val p = project()
        val loop = 48_000 * 60.0 / p.bpm / 4.0 * 16
        val single = StudioExporter.frameCount(p, 48_000)
        assertEquals(loop * StudioExporter.LOOPS_WITHOUT_SONG + 2.5 * 48_000, single.toDouble(), 2.0)
        val song = p.copy(chain = listOf(0, 1), songMode = true)
        assertEquals(loop * 2 + 2.5 * 48_000, StudioExporter.frameCount(song, 48_000).toDouble(), 2.0)
    }

    @Test
    fun wavRenderCanBeCancelled() {
        val out = ByteArrayOutputStream()
        var calls = 0
        val finished = StudioExporter.renderWav(project(), 48_000, out) { calls++ < 3 }
        assertFalse(finished)
        assertTrue(out.size() < 44 + 5 * 1024 * 4)
    }

    @Test
    fun wavDoesNotDependOnTheLiveOutput() {
        // Deux rendus du même projet donnent exactement les mêmes octets.
        val p = project()
        val a = ByteArrayOutputStream().also { StudioExporter.renderWav(p, 48_000, it) }.toByteArray()
        val b = ByteArrayOutputStream().also { StudioExporter.renderWav(p, 48_000, it) }.toByteArray()
        assertTrue(a.contentEquals(b))
    }

    /** Lecteur minimal : rend, par canal, les notes allumées, et le tempo. */
    private class Midi(val format: Int, val tracks: Int, val division: Int, val noteOns: Map<Int, Int>, val programs: Map<Int, Int>, val tempo: Int, val lastTick: Int)

    private fun parse(b: ByteArray): Midi {
        assertEquals("MThd", String(b, 0, 4))
        val format = ((b[8].toInt() and 0xFF) shl 8) or (b[9].toInt() and 0xFF)
        val tracks = ((b[10].toInt() and 0xFF) shl 8) or (b[11].toInt() and 0xFF)
        val division = ((b[12].toInt() and 0xFF) shl 8) or (b[13].toInt() and 0xFF)
        var pos = 14
        val ons = HashMap<Int, Int>()
        val programs = HashMap<Int, Int>()
        var tempo = 0
        var lastTick = 0
        repeat(tracks) {
            assertEquals("MTrk", String(b, pos, 4))
            val len = ((b[pos + 4].toInt() and 0xFF) shl 24) or ((b[pos + 5].toInt() and 0xFF) shl 16) or ((b[pos + 6].toInt() and 0xFF) shl 8) or (b[pos + 7].toInt() and 0xFF)
            var i = pos + 8
            val end = i + len
            var tick = 0
            while (i < end) {
                var delta = 0
                do {
                    val c = b[i++].toInt() and 0xFF
                    delta = (delta shl 7) or (c and 0x7F)
                } while (c and 0x80 != 0)
                tick += delta
                val status = b[i++].toInt() and 0xFF
                if (status == 0xFF) {
                    val type = b[i++].toInt() and 0xFF
                    val l = b[i++].toInt() and 0xFF
                    if (type == 0x51) tempo = ((b[i].toInt() and 0xFF) shl 16) or ((b[i + 1].toInt() and 0xFF) shl 8) or (b[i + 2].toInt() and 0xFF)
                    i += l
                } else {
                    val ch = status and 0x0F
                    val oneByte = status and 0xF0 == 0xC0 || status and 0xF0 == 0xD0 // changement de programme
                    val vel = if (oneByte) 0 else b[i + 1].toInt() and 0xFF
                    if (status and 0xF0 == 0xC0) programs[ch] = b[i].toInt() and 0x7F
                    if (status and 0xF0 == 0x90 && vel > 0) ons[ch] = (ons[ch] ?: 0) + 1
                    i += if (oneByte) 1 else 2
                    lastTick = maxOf(lastTick, tick)
                }
            }
            assertEquals("la piste se termine exactement", end, i)
            pos = end
        }
        assertEquals("rien après la dernière piste", b.size, pos)
        return Midi(format, tracks, division, ons, programs, tempo, lastTick)
    }

    @Test
    fun midiUsesTheGmEquivalentOfTheFmInstrument() {
        val project = project()
        val track = project.track(Inst.SYNTH)
        val channel = project.midiChannel(track.id)
        for ((fm, gm) in listOf(0 to 81, 1 to 40, 3 to 0, 12 to 11, 14 to 32, 15 to 27)) {
            val fmTrack = track.copy(synthBank = SynthBank.FM, fmProgram = fm, program = 75)
            val midi = parse(StudioExporter.midiBytes(project.withTrack(fmTrack)))
            assertEquals("Programme ROM $fm", gm, midi.programs[channel])
        }
    }

    @Test
    fun midiHasTheRightNotesAndTempo() {
        val p = project()
        val m = parse(StudioExporter.midiBytes(p))
        assertEquals(1, m.format)
        assertEquals(3, m.tracks)
        assertEquals(480, m.division)
        assertTrue(abs(m.tempo - 60_000_000 / p.bpm) <= 1)
        var drumSteps = 0
        for (lane in p.pattern.lanes) drumSteps += lane.velocity.count { it > 0f }
        assertEquals(drumSteps * StudioExporter.LOOPS_WITHOUT_SONG, m.noteOns[9] ?: 0)
        assertEquals(p.pattern.notes.size * StudioExporter.LOOPS_WITHOUT_SONG, m.noteOns[0] ?: 0)
        // Tout tient dans la durée de 4 tours de 16 pas (un pas = 120 ticks)
        assertTrue(m.lastTick <= 16 * 120 * 4 + 120 * 16)
    }

    @Test
    fun midiFollowsTheSong() {
        val engine = StudioEngine(48_000)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.chainAdd(0)
        s.chainAdd(1) // B est vide
        s.setSongMode(true)
        val m = parse(StudioExporter.midiBytes(s.project))
        var drumSteps = 0
        for (lane in s.project.patterns[0].lanes) drumSteps += lane.velocity.count { it > 0f }
        assertEquals(drumSteps, m.noteOns[9] ?: 0)
        assertEquals(s.project.patterns[0].notes.size, m.noteOns[0] ?: 0)
    }

    @Test
    fun emptyProjectStillExports() {
        val engine = StudioEngine(48_000)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.clear()
        val m = parse(StudioExporter.midiBytes(s.project))
        assertEquals(0, m.noteOns[9] ?: 0)
        val out = ByteArrayOutputStream()
        assertTrue(StudioExporter.renderWav(s.project, 48_000, out))
        assertTrue(out.size() > 44)
        assertEquals(Inst.COUNT, 8)
    }
}
