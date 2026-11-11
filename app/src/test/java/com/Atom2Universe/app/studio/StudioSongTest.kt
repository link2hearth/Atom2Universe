package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.LiveRecorder
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepCond
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.export.StudioExporter
import com.Atom2Universe.app.studio.music.Styles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Motifs A à D et morceau : édition indépendante, copie, enchaînement dans le moteur, sauvegarde. */
class StudioSongTest {

    private val rate = 48_000

    private fun session(): Pair<StudioEngine, StudioSession> {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.clear() // le motif A devient vide
        s.setSpace(0f)
        s.setEcho(0f)
        for (lane in 0 until Inst.COUNT) s.updateTrack(lane) { it.copy(reverbSend = 0f, delaySend = 0f) }
        return engine to s
    }

    private fun render(engine: StudioEngine, frames: Int): FloatArray {
        val out = FloatArray(frames)
        val l = FloatArray(256)
        val r = FloatArray(256)
        var done = 0
        while (done < frames) {
            val n = minOf(256, frames - done)
            engine.render(l, r, n)
            for (i in 0 until n) out[done + i] = l[i] + r[i]
            done += n
        }
        return out
    }

    private fun rms(a: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return sqrt(s / (to - from))
    }

    @Test
    fun patternsAreEditedIndependently() {
        val (_, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.selectPattern(1)
        assertFalse(s.isOn(Inst.KICK, 0))
        s.setStep(Inst.SNARE, 4, true)
        s.selectPattern(0)
        assertTrue(s.isOn(Inst.KICK, 0))
        assertFalse(s.isOn(Inst.SNARE, 4))
        assertFalse(s.isPatternEmpty(0))
        assertFalse(s.isPatternEmpty(1))
        assertTrue(s.isPatternEmpty(2))
    }

    @Test
    fun captureMixesExtraTracksIntoOneUndoableEditableAndExportableSlot() {
        val engine = StudioEngine(rate)
        val setup = StudioSession(engine, StudioProject.blank())
        val drum = setup.addTrack(TrackKind.DRUM, voice = Inst.TOM)
        val sf = setup.addTrack(TrackKind.SF2, bank = 0, program = 12)
        val empty = setup.project.patterns[0]
        val steps = empty.stepCount
        val base = empty.copy(
            lanes = empty.lanes.map { if (it.instrument == Inst.KICK) it.copy(velocity = FloatArray(steps) { 1f }) else it },
            notes = listOf(Note(1, 72, track = Inst.SYNTH), Note(2, 39, track = sf)),
            autoSpace = FloatArray(steps * StepPattern.AUTO_RES) { 0.3f },
        )
        val donor = empty.copy(
            lanes = empty.lanes.map {
                if (it.instrument != drum) it else Lane(drum, FloatArray(steps) { 0.67f }, IntArray(steps) { 71 },
                    gate = 0.75f, hold = FloatArray(steps) { 1.5f }, cond = IntArray(steps) { StepCond.EVERY_OTHER })
            },
            notes = listOf(Note(4, 55, 2, 0.6f, sf), Note(4, 63, 3, 0.7f, sf)),
            autoFilter = FloatArray(steps * StepPattern.AUTO_RES) { 1f },
        )
        val s = StudioSession(engine, setup.project.copy(patterns = listOf(base, donor, empty, base), current = 2))
        val clips = mapOf(Inst.KICK to 2, drum to 1, sf to 1)
        assertFalse(s.hasTrackClip(drum, 0))
        assertTrue(s.hasTrackClip(drum, 1))
        assertTrue(s.hasTrackClip(sf, 1))
        assertTrue(s.captureTrackClips(0, clips, 3))
        assertFalse(s.captureTrackClips(0, clips, 3))
        assertEquals(2, s.project.current)
        assertSame(base, s.project.patterns[0])
        assertSame(donor, s.project.patterns[1])
        val expected = base.copy(
            lanes = base.lanes.map { when (it.instrument) {
                Inst.KICK -> empty.lane(Inst.KICK)!!
                drum -> donor.lane(drum)!!
                else -> it
            } },
            notes = listOf(base.notes[0]) + donor.notes,
        )
        val encoded = StudioProjectCodec.encodePattern(expected).toString()
        assertEquals(encoded, StudioProjectCodec.encodePattern(s.project.patterns[3]).toString())
        s.undo()
        assertFalse(s.canUndo)
        assertEquals(StudioProjectCodec.encodePattern(base).toString(), StudioProjectCodec.encodePattern(s.project.patterns[3]).toString())
        s.redo()
        s.selectPattern(3)
        val loaded = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(encoded, StudioProjectCodec.encodePattern(loaded.pattern).toString())
        assertEquals(0.75f, loaded.pattern.lane(drum)!!.gate, 0f)
        assertTrue(StudioExporter.midiBytes(s.project.copy(pattern = expected)).contentEquals(StudioExporter.midiBytes(loaded)))
        assertTrue(s.captureTrackClips(0, clips, 1))
        assertEquals(encoded, StudioProjectCodec.encodePattern(s.project.patterns[1]).toString())
        s.undo()
        assertEquals(StudioProjectCodec.encodePattern(donor).toString(), StudioProjectCodec.encodePattern(s.project.patterns[1]).toString())
    }

    @Test
    fun captureAppendsSelectedDrumsMissingFromTheBaseInSourceOrder() {
        val project = StudioProject.blank()
        val empty = project.patterns.first()
        fun drum(id: Int, hit: Int) = Lane(id, FloatArray(empty.stepCount) { if (it == hit) 1f else 0f })
        val base = empty.copy(lanes = listOf(drum(Inst.TOM, 1), drum(Inst.KICK, 2)))
        val donor = empty.copy(lanes = listOf(drum(Inst.SNARE, 3), drum(Inst.KICK, 4), drum(Inst.CLAP, 5), drum(Inst.HAT, 6)))
        val s = StudioSession(StudioEngine(rate), project.copy(patterns = listOf(base, donor, empty, empty)))
        val clips = mapOf(Inst.CLAP to 1, Inst.KICK to 1, Inst.SNARE to 1)
        assertTrue(s.captureTrackClips(0, clips, 3))
        val captured = s.project.patterns[3]
        assertEquals(listOf(Inst.TOM, Inst.KICK, Inst.SNARE, Inst.CLAP), captured.lanes.map { it.instrument })
        assertSame(base.lane(Inst.TOM), captured.lane(Inst.TOM))
        for (id in clips.keys) assertSame(donor.lane(id), captured.lane(id))
    }

    @Test
    fun capturingCyclesShortClipsAndTruncatesLongClipsWhilePreservingBothSources() {
        val original = StudioProject.blank()
        val base = original.patterns[0]
        val short = StepPattern(6, listOf(Lane(Inst.KICK, floatArrayOf(0f, 1f, 0f, 0f, 0f, 0f))),
            listOf(Note(3, 52, 3)))
        val long = StepPattern(32, listOf(Lane(Inst.SNARE, FloatArray(32) { if (it == 8 || it == 28) 1f else 0f })))
        val s = StudioSession(StudioEngine(rate), original.copy(patterns = listOf(base, short, long, base)))
        assertTrue(s.captureTrackClips(0, mapOf(Inst.KICK to 1, Inst.SYNTH to 1, Inst.SNARE to 2), 3))
        val captured = s.project.patterns[3]
        assertEquals(16, captured.stepCount)
        assertEquals(listOf(1, 7, 13), captured.lane(Inst.KICK)!!.velocity.indices.filter { captured.lane(Inst.KICK)!!.velocity[it] > 0f })
        assertEquals(listOf(8), captured.lane(Inst.SNARE)!!.velocity.indices.filter { captured.lane(Inst.SNARE)!!.velocity[it] > 0f })
        assertEquals(listOf(3 to 3, 9 to 3, 15 to 1), captured.notes.map { it.step to it.length })
        assertSame(short, s.project.patterns[1])
        assertSame(long, s.project.patterns[2])
    }

    @Test
    fun liveNotesKeepTheirAttackSlotAndDrumHitsKeepTheirAudioSlot() {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.blank())
        s.setView(listOf(0, 1))
        engine.launchTrackClip(Inst.SYNTH, 2)
        val recorder = LiveRecorder(s, { true }, { 19.0 }).apply { armed = true }
        recorder.press(62, 17.4)
        engine.launchTrackClip(Inst.SYNTH, 3)
        recorder.release(62, 20.4)
        assertEquals(1 to 3, s.project.patterns[2].notes.single().let { it.step to it.length })
        assertTrue(s.project.patterns[3].notes.isEmpty())
        engine.launchTrackClip(Inst.KICK, 1)
        s.recordHit(Inst.KICK, 19.1, 0.7f)
        s.recordHit(Inst.KICK, 21.1, 0.8f, sourceSlot = 2)
        assertEquals(0.7f, s.project.patterns[1].lane(Inst.KICK)!!.velocity[3], 0f)
        assertEquals(0.8f, s.project.patterns[2].lane(Inst.KICK)!!.velocity[5], 0f)
        assertEquals(0f, s.project.patterns[1].lane(Inst.KICK)!!.velocity[5], 0f)
        assertTrue(s.project.patterns[0].notes.isEmpty())
        assertEquals(listOf(0, 1), s.project.viewSlots)
    }

    @Test
    fun copyingOneClipReplacesOnlyTheDestinationInstrumentAndCanBeUndone() {
        val engine = StudioEngine(rate)
        val setup = StudioSession(engine, StudioProject.blank())
        val sf = setup.addTrack(TrackKind.SF2, bank = 0, program = 12)
        val empty = setup.project.patterns[0]
        val source = empty.copy(notes = listOf(Note(2, 61, 4, 0.8f, sf), Note(8, 75)))
        val destination = empty.copy(
            lanes = empty.lanes.map { if (it.instrument == Inst.CLAP) it.copy(velocity = FloatArray(empty.stepCount) { 1f }) else it },
            notes = listOf(Note(1, 32, track = sf), Note(11, 68)),
            autoFilter = FloatArray(empty.stepCount * StepPattern.AUTO_RES) { 0.4f },
        )
        val s = StudioSession(engine, setup.project.copy(patterns = listOf(source, destination, empty, empty)))
        assertTrue(s.copyTrackClip(sf, 0, 1))
        val result = s.project.patterns[1]
        assertSame(source, s.project.patterns[0])
        assertSame(destination.autoFilter, result.autoFilter)
        assertSame(destination.lane(Inst.CLAP), result.lane(Inst.CLAP))
        assertEquals(listOf(68, 61), result.notes.map { it.pitch })
        assertSame(source.notes[0], result.notes.last())
        assertFalse(s.copyTrackClip(sf, 0, 1))
        s.undo()
        assertEquals(StudioProjectCodec.encodePattern(destination).toString(), StudioProjectCodec.encodePattern(s.project.patterns[1]).toString())
        assertFalse(s.canUndo)
    }

    @Test
    fun copyPatternDuplicatesIt() {
        val (_, s) = session()
        s.setStep(Inst.CLAP, 8, true)
        s.copyPattern(0, 3)
        s.selectPattern(3)
        assertTrue(s.isOn(Inst.CLAP, 8))
        s.setStep(Inst.CLAP, 8, false)
        s.selectPattern(0)
        assertTrue("la copie ne partage rien avec l'original", s.isOn(Inst.CLAP, 8))
    }

    @Test
    fun songPlaysPatternsOneAfterAnother() {
        val (engine, s) = session()
        s.setStep(Inst.KICK, 0, true) // A : une grosse caisse au début ; B reste vide
        s.chainAdd(0)
        s.chainAdd(1)
        s.chainAdd(0)
        s.setSongMode(true)
        engine.bpm = 120f
        engine.play()
        val loop = 16 * 6_000
        val out = render(engine, loop * 4)
        val win = 20_000
        val first = rms(out, 0, win)
        val second = rms(out, loop, loop + win)
        val third = rms(out, loop * 2, loop * 2 + win)
        val fourth = rms(out, loop * 3, loop * 3 + win) // le morceau A-B-A recommence : A de nouveau
        assertTrue("A sonne ($first)", first > 0.02)
        assertTrue("B est vide ($second)", second < first * 0.1)
        assertTrue("A revient ($third)", third > first * 0.5)
        assertTrue("le morceau recommence par A ($fourth)", fourth > first * 0.5)
        assertEquals("le morceau a fait deux fois le tour de ses trois rangs : retour au rang 0 au 4e tour", 0, engine.songPosition)
    }

    @Test
    fun withoutSongModeItLoopsOnTheCurrentPattern() {
        val (engine, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.chainAdd(0)
        s.chainAdd(1)
        // morceau écrit mais pas activé
        engine.bpm = 120f
        engine.play()
        val loop = 16 * 6_000
        val out = render(engine, loop * 2)
        assertTrue(rms(out, loop, loop + 20_000) > 0.02)
    }

    @Test
    fun chainEditing() {
        val (_, s) = session()
        s.chainAdd(0)
        s.chainAdd(2)
        assertEquals(listOf(0, 2), s.project.chain)
        s.setSongMode(true)
        assertTrue(s.project.songMode)
        s.chainRemoveLast()
        assertEquals(listOf(0), s.project.chain)
        s.chainRemoveLast()
        assertTrue("sans morceau, le mode s'éteint", !s.project.songMode)
        for (k in 0 until 40) s.chainAdd(1)
        assertEquals(StudioProject.MAX_CHAIN, s.project.chain.size)
        s.chainClear()
        assertTrue(s.project.chain.isEmpty())
        s.setSongMode(true)
        assertFalse("pas de morceau à jouer", s.project.songMode)
    }

    @Test
    fun stepCountChangesEveryPattern() {
        val (_, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.copyPattern(0, 2)
        s.setStepCount(32)
        for (slot in 0 until StudioProject.SLOTS) assertEquals(32, s.project.patterns[slot].stepCount)
        s.selectPattern(2)
        assertTrue(s.isOn(Inst.KICK, 16))
        s.setStepCount(16)
        for (slot in 0 until StudioProject.SLOTS) assertEquals(16, s.project.patterns[slot].stepCount)
    }

    @Test
    fun styleOnlyReplacesTheCurrentPattern() {
        val (_, s) = session()
        s.setStep(Inst.CLAP, 8, true)
        s.selectPattern(2)
        s.setStep(Inst.TOM, 3, true)
        s.chainAdd(0)
        s.chainAdd(2)
        s.applyStyle(Styles.TECHNO)
        assertEquals(2, s.project.current)
        assertTrue("l'autre motif est intact", !s.isPatternEmpty(0))
        s.selectPattern(0)
        assertTrue(s.isOn(Inst.CLAP, 8))
        assertEquals(listOf(0, 2), s.project.chain)
    }

    @Test
    fun songSurvivesTheCodec() {
        val (_, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.copyPattern(0, 1)
        s.selectPattern(1)
        s.chainAdd(0)
        s.chainAdd(1)
        s.setSongMode(true)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(1, back.current)
        assertEquals(listOf(0, 1), back.chain)
        assertTrue(back.songMode)
        assertEquals(StudioProject.SLOTS, back.patterns.size)
        assertTrue(back.patterns[0].lanes[Inst.KICK].velocity[0] > 0f)
        assertTrue(back.patterns[2].notes.isEmpty())
    }
}
