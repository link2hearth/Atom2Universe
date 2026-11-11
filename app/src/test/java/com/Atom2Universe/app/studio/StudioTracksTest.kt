package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.Action
import com.Atom2Universe.app.studio.data.PadSize
import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.ExternalSynth
import com.Atom2Universe.app.studio.engine.Arp
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.export.StudioExporter
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sin

/** Les pistes libres : ajouter, cacher, retirer, plusieurs mélodies, instrument de soundfont (faux synthé), sauvegarde, export. */
class StudioTracksTest {

    private val rate = 48_000

    /** Un faux soundfont : une sinusoïde tant qu'une note est tenue ; il note quand et quoi on lui a demandé. */
    private class FakeSynth : ExternalSynth {
        val ons = ArrayList<Pair<Int, Int>>() // (note, nombre de trames déjà rendues)
        val offs = ArrayList<Int>()
        var program = -1
        var bank = -1
        var rendered = 0
        private val held = HashSet<Int>()
        private var phase = 0.0

        override fun noteOn(note: Int, velocity: Int) {
            ons += note to rendered
            held += note
        }

        override fun noteOff(note: Int) {
            offs += note
            held -= note
        }

        override fun allOff() = held.clear()

        override fun setProgram(bank: Int, program: Int) {
            this.bank = bank
            this.program = program
        }

        override fun render(left: FloatArray, right: FloatArray, count: Int) {
            for (i in 0 until count) {
                val v = if (held.isEmpty()) 0f else (sin(phase) * 0.4).toFloat()
                phase += 2 * Math.PI * 440.0 / 48_000
                left[i] = v
                right[i] = v
            }
            rendered += count
        }

        override val activeVoices: Int get() = held.size
    }

    private fun quiet(): StudioEngine = StudioEngine(rate).also {
        it.spaceAmount = 0f
        it.echoAmount = 0f
        for (inst in it.allInstruments) {
            inst.reverbSend = 0f
            inst.delaySend = 0f
        }
    }

    private fun render(e: StudioEngine, frames: Int, block: Int = 256): FloatArray {
        val out = FloatArray(frames)
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            e.render(l, r, n)
            for (i in 0 until n) out[done + i] = l[i] + r[i]
            done += n
        }
        return out
    }

    private fun session(): StudioSession = StudioSession(quiet(), StudioProject.fromStyle(Styles.HOUSE)).apply { coalesceMs = 0 }

    @Test
    fun newProjectHasTheEightStartingTracks() {
        val p = StudioProject.fromStyle(Styles.HOUSE)
        assertEquals((0 until Inst.COUNT).toList(), p.tracks.map { it.id })
        assertEquals(List(Inst.DRUM_COUNT) { TrackKind.DRUM } + TrackKind.SYNTH, p.tracks.map { it.kind })
        assertEquals(Inst.DRUM_COUNT, p.pattern.lanes.size)
    }

    @Test
    fun addingADrumTrackGivesEveryPatternAnEmptyLane() {
        val s = session()
        val id = s.addTrack(TrackKind.DRUM, voice = Inst.KICK)
        assertEquals(Inst.COUNT, id)
        for (p in s.project.patterns) {
            val lane = p.lane(id)
            assertNotNull(lane)
            assertTrue(lane!!.velocity.all { it == 0f })
        }
        // la nouvelle ligne se joue comme les autres
        s.setStep(id, 0, true)
        assertTrue(s.isOn(id, 0))
        assertFalse(s.isOn(id, 1))
    }

    @Test
    fun twoSynthTracksPlayTheirOwnNotes() {
        val engine = quiet()
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.clear()
        val second = s.addTrack(TrackKind.SYNTH)
        s.addNote(0, 60, 4) // piste du synthé d'origine
        s.addNote(8, 64, 4, track = second)
        val notes = s.project.pattern.notes
        assertEquals(setOf(Inst.SYNTH, second), notes.map { it.track }.toSet())
        val heard = ArrayList<Pair<Int, Long>>()
        engine.onSynthNote = { p, f -> heard += p to f }
        engine.play()
        render(engine, rate * 3 / 2) // un peu moins qu'une mesure
        assertEquals(listOf(60, 64), heard.map { it.first })
        // chaque piste ne joue que ses notes
        assertTrue(engine.synthOf(Inst.SYNTH)!!.activeVoices >= 0)
        assertTrue(engine.synthOf(second) !== engine.synthOf(Inst.SYNTH))
    }

    @Test
    fun removingATrackRemovesItsStepsAndNotes() {
        val s = session()
        val second = s.addTrack(TrackKind.SYNTH)
        s.addNote(2, 62, 2, track = second)
        s.removeTrack(second)
        assertNull(s.project.trackOrNull(second))
        assertTrue(s.project.pattern.notes.none { it.track == second })
        s.removeTrack(Inst.KICK)
        for (p in s.project.patterns) assertNull(p.lane(Inst.KICK))
        // un pas en arrière rend la piste retirée en dernier
        s.undo()
        assertNotNull(s.project.trackOrNull(Inst.KICK))
        assertTrue(s.project.pattern.lane(Inst.KICK)!!.velocity[0] > 0f)
    }

    @Test
    fun aHiddenTrackIsSilentButKeepsItsContent() {
        val engine = quiet()
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        val before = s.project.pattern.lane(Inst.KICK)!!.velocity.copyOf()
        s.setHidden(Inst.KICK, true)
        assertTrue(engine.instrument(Inst.KICK)!!.muted)
        assertTrue(s.isHidden(Inst.KICK))
        assertTrue(before.contentEquals(s.project.pattern.lane(Inst.KICK)!!.velocity))
        s.setHidden(Inst.KICK, false)
        assertFalse(engine.instrument(Inst.KICK)!!.muted)
    }

    @Test
    fun hidingASoloTrackClearsItsSolo() {
        val s = session()
        s.toggleSolo(Inst.SNARE)
        s.setHidden(Inst.SNARE, true)
        assertFalse(s.isSolo(Inst.SNARE))
    }

    @Test
    fun tracksCanBeReordered() {
        val s = session()
        s.moveTrack(Inst.SYNTH, 0)
        assertEquals(Inst.SYNTH, s.project.tracks.first().id)
        assertEquals(Inst.COUNT, s.project.tracks.size)
    }

    @Test
    fun soundfontTrackPlaysNotesAtTheExactSample() {
        val fake = FakeSynth()
        val engine = quiet()
        engine.sf2Factory = { _, _, _ -> fake }
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.clear()
        val id = s.addTrack(TrackKind.SF2, bank = 0, program = 24)
        assertEquals(24, fake.program)
        s.addNote(4, 67, 2, track = id)
        val heard = ArrayList<Pair<Int, Long>>()
        engine.onSynthNote = { p, f -> heard += p to f }
        engine.play()
        val out = render(engine, rate * 3 / 2, block = 333)
        assertEquals(listOf(67), fake.ons.map { it.first })
        assertEquals(listOf(67), heard.map { it.first })
        val stepFrames = rate * 60.0 / s.project.bpm / 4
        assertTrue("note à ${heard[0].second}", abs(heard[0].second - 4 * stepFrames) <= 1.0)
        assertEquals(listOf(67), fake.offs)
        assertTrue(out.any { abs(it) > 0.05f })
    }

    @Test
    fun soundfontTrackFollowsTheMixer() {
        val fake = FakeSynth()
        val engine = quiet()
        engine.sf2Factory = { _, _, _ -> fake }
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        val id = s.addTrack(TrackKind.SF2, 0, 0)
        s.updateTrack(id) { it.copy(level = 1f, reverbSend = 0f, delaySend = 0f) }
        engine.trigger(id, 60, 1f)
        val loud = render(engine, 4_000).maxOf { abs(it) }
        s.setMuted(id, true)
        val muted = render(engine, 4_000).maxOf { abs(it) }
        assertTrue("sonne ($loud)", loud > 0.1f)
        assertEquals(0f, muted, 1e-6f)
    }

    @Test
    fun changingTheProgramKeepsTheSameSynth() {
        val made = ArrayList<FakeSynth>()
        val engine = quiet()
        engine.sf2Factory = { _, _, _ -> FakeSynth().also { made += it } }
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        val id = s.addTrack(TrackKind.SF2, 0, 0)
        s.setSf2Program(id, 8, 40)
        assertEquals(1, made.size)
        assertEquals(40, made[0].program)
        assertEquals(8, made[0].bank)
    }

    @Test
    fun withoutASoundfontTheTrackIsJustSilent() {
        val engine = quiet() // pas de fabrique
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        val id = s.addTrack(TrackKind.SF2, 0, 0)
        engine.trigger(id, 60, 1f)
        val out = render(engine, 2_000)
        assertEquals(0f, out.maxOf { abs(it) }, 1e-6f)
    }

    @Test
    fun eachMelodyTrackHasItsOwnArpeggio() {
        val s = session()
        val second = s.addTrack(TrackKind.SYNTH)
        s.setArpMode(Arp.UP_DOWN, second)
        s.setArpOctaves(2, second)
        assertEquals(Arp.OFF, s.project.track(Inst.SYNTH).arpMode)
        assertEquals(Arp.UP_DOWN, s.project.track(second).arpMode)
        assertEquals(1, s.project.track(Inst.SYNTH).arpOctaves)
    }

    @Test
    fun padOrderAndWidthAreLocalToTheSongAndIndependentOfTheMixer() {
        val session = StudioSession(StudioEngine(rate), StudioProject.blank())
        val trackIds = session.project.tracks.map { it.id }
        val first = trackIds.first()
        val last = trackIds.last()
        session.movePad(last, first)
        session.resizePad(last)
        assertEquals(last, session.padTracks().first().id)
        assertEquals(trackIds, session.project.tracks.map { it.id })
        session.undo()
        assertEquals(PadSize.NORMAL, session.padTracks().first().padSize)
        session.redo()
        val restored = StudioProjectCodec.decode(StudioProjectCodec.encode(session.project))!!
        assertEquals(PadSize.WIDE, restored.track(last).padSize)
        assertEquals(last, restored.tracks.minBy { it.padOrder }.id)
        session.setHidden(last, true)
        session.setHidden(last, false)
        assertEquals(last, session.padTracks().first().id)
    }

    @Test
    fun halfPadSwitchesFromWideAndSurvivesTheProjectAndJournal() {
        val session = session()
        val kick = Inst.KICK
        session.resizePad(kick)
        val wide = session.project
        session.togglePadHalf(kick)
        val half = session.project
        assertEquals(PadSize.HALF, StudioProjectCodec.decode(StudioProjectCodec.encode(half))!!.track(kick).padSize)

        val change = Action(StudioHistory.diff(wide, half, undoableOnly = true))
        val journal = listOf(StudioHistory.line(change, undoable = true))
        assertEquals(PadSize.HALF, StudioHistory.replay(wide, journal).project.track(kick).padSize)
        session.undo()
        assertEquals(PadSize.WIDE, session.project.track(kick).padSize)
        session.redo()
        session.togglePadHalf(kick)
        assertEquals(PadSize.NORMAL, session.project.track(kick).padSize)
        session.togglePadHalf(kick)
        session.resizePad(kick)
        assertEquals(PadSize.WIDE, session.project.track(kick).padSize)
    }

    @Test
    fun tracksSurviveTheCodec() {
        val s = session()
        val second = s.addTrack(TrackKind.SYNTH)
        val sf = s.addTrack(TrackKind.SF2, bank = 1, program = 33)
        val drum = s.addTrack(TrackKind.DRUM, voice = Inst.TOM)
        s.addNote(3, 60, 2, track = second)
        s.addNote(5, 48, 1, track = sf)
        s.setStep(drum, 6, true)
        s.setHidden(Inst.OPEN_HAT, true)
        s.setPreset("pad", second)
        s.setArpMode(Arp.RANDOM, sf)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(s.project.tracks, back.tracks)
        assertEquals(33, back.track(sf).program)
        assertEquals(1, back.track(sf).bank)
        assertEquals("pad", back.track(second).synth.id)
        assertTrue(back.track(Inst.OPEN_HAT).hidden)
        val notes = back.pattern.notes.map { Triple(it.track, it.step, it.pitch) }.toSet()
        assertTrue(Triple(second, 3, 60) in notes)
        assertTrue(Triple(sf, 5, 48) in notes)
        assertTrue(back.pattern.lane(drum)!!.velocity[6] > 0f)
    }

    @Test
    fun noteOfAnUnknownTrackIsDroppedWhenReading() {
        val s = session()
        val second = s.addTrack(TrackKind.SYNTH)
        s.addNote(1, 50, 1, track = second)
        s.removeTrack(second)
        val text = StudioProjectCodec.encode(s.project).replace("\"notes\":[", "\"notes\":[[0,60,1,1.0,20],")
        val back = StudioProjectCodec.decode(text)
        assertNotNull(back)
        assertTrue(back!!.pattern.notes.none { it.track == 20 })
    }

    @Test
    fun styleAndRandomOnlyTouchExistingTracks() {
        val s = session()
        val second = s.addTrack(TrackKind.SYNTH)
        s.addNote(1, 50, 1, track = second)
        s.removeTrack(Inst.SNARE)
        s.applyStyle(Styles.TECHNO)
        assertTrue(s.project.pattern.lane(Inst.SNARE) == null)
        assertTrue(s.project.pattern.notes.any { it.track == second }) // l'autre mélodie reste
        assertTrue(s.project.pattern.notes.any { it.track == Inst.SYNTH }) // le style écrit dans la première
        s.randomize(5L)
        assertTrue(s.project.pattern.notes.any { it.track == second })
        assertEquals(s.project.tracks.count { !it.melodic }, s.project.pattern.lanes.size)
        s.clear()
        assertTrue(s.project.pattern.notes.isEmpty())
    }

    @Test
    fun stepCountResizeKeepsTheTrackOfEachNote() {
        val s = session()
        val second = s.addTrack(TrackKind.SYNTH)
        s.addNote(1, 50, 1, track = second)
        s.setStepCount(32)
        val tracks = s.project.pattern.notes.filter { it.step == 1 || it.step == 17 }.map { it.track }
        assertTrue(second in tracks)
        assertEquals(2, s.project.pattern.notes.count { it.track == second })
    }

    @Test
    fun theTrackLimitIsRespected() {
        val s = session()
        var last = 0
        repeat(StudioEngine.MAX_TRACKS) { last = s.addTrack(TrackKind.SYNTH) }
        assertEquals(-1, last)
        assertEquals(StudioEngine.MAX_TRACKS, s.project.tracks.size)
    }

    @Test
    fun midiGivesEachMelodyItsOwnChannelAndProgram() {
        val s = session()
        s.clear()
        val sf = s.addTrack(TrackKind.SF2, bank = 0, program = 40)
        s.addNote(0, 60, 2, track = sf)
        s.addNote(0, 50, 2) // synthé d'origine
        val bytes = StudioExporter.midiBytes(s.project)
        // 3 + une piste mélodique de plus : conducteur, batterie, synthé, soundfont
        val tracks = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        assertEquals(4, tracks)
        // un changement de programme 40 sur le canal 2 (index 1)
        var found = false
        for (i in 0 until bytes.size - 1) if ((bytes[i].toInt() and 0xFF) == 0xC1 && bytes[i + 1].toInt() == 40) found = true
        assertTrue(found)
    }

    @Test
    fun hiddenTracksAreNotExported() {
        val s = session()
        s.clear()
        val sf = s.addTrack(TrackKind.SF2, 0, 0)
        s.addNote(0, 60, 2, track = sf)
        s.setHidden(sf, true)
        val bytes = StudioExporter.midiBytes(s.project)
        val tracks = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        assertEquals(3, tracks) // conducteur, batterie, synthé d'origine
    }

    @Test
    fun wavExportIncludesTheSoundfontTrack() {
        val s = session()
        s.clear()
        val sf = s.addTrack(TrackKind.SF2, 0, 0)
        s.addNote(0, 60, 8, track = sf)
        val withSf = ByteArrayOutputStream()
        StudioExporter.renderWav(s.project, rate, withSf, { _, _, _ -> FakeSynth() })
        val without = ByteArrayOutputStream()
        StudioExporter.renderWav(s.project, rate, without)
        fun peak(b: ByteArray): Int {
            var m = 0
            var i = 44
            while (i + 1 < b.size) {
                val v = (b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)
                m = maxOf(m, abs(v.toShort().toInt()))
                i += 2
            }
            return m
        }
        assertTrue("avec le soundfont ${peak(withSf.toByteArray())}", peak(withSf.toByteArray()) > 2_000)
        assertEquals(0, peak(without.toByteArray()))
    }

    @Test
    fun melodyNotesStillUseThePatternNotesOfTheirTrack() {
        // une note sans piste précisée appartient à la piste du synthé d'origine
        val n = Note(0, 60)
        assertEquals(Inst.SYNTH, n.track)
        val p = StepPattern(16, emptyList(), listOf(n))
        assertEquals(1, p.notes.size)
    }
}
