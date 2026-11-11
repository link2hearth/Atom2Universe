package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.dsp.ExternalSynth
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.engine.TrackSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lancement par piste : vérifie le son produit en plus de l'état affiché dans le lanceur. */
class StudioTrackClipTest {
    private val rate = 24_000
    private val stepFrames = rate / 8
    private val engine = StudioEngine(rate).apply { bpm = 120f }

    private fun lane(track: Int, vararg hits: Int) = Lane(track, FloatArray(16) { if (it in hits) 1f else 0f })
    private fun render(frames: Int, source: StudioEngine = engine): FloatArray {
        val left = FloatArray(frames)
        source.render(left, FloatArray(frames), frames)
        return left
    }

    @Test fun twoClipsReplaceOnlyTheirInstrumentsAndProduceTheExpectedMix() {
        val a = StepPattern(16, listOf(lane(Inst.KICK, 0), lane(Inst.CLAP, 4)), listOf(Note(1, 50, 3)))
        val b = StepPattern(16, listOf(lane(Inst.KICK, 3, 8), lane(Inst.CLAP, 12)), listOf(Note(9, 57)))
        val c = StepPattern(16, listOf(lane(Inst.KICK, 5)), listOf(Note(2, 67, 3)))
        engine.pattern = a
        engine.trackClipPatterns = listOf(a, b, c)
        engine.launchTrackClip(Inst.KICK, 1)
        engine.launchTrackClip(Inst.SYNTH, 2)
        assertEquals(mapOf(Inst.KICK to 1, Inst.SYNTH to 2), engine.activeTrackClips())
        assertNull(engine.pendingTrackClip(Inst.KICK))
        val expected = StudioEngine(rate).apply {
            bpm = 120f
            pattern = StepPattern(16, listOf(b.lanes.first(), a.lanes.last()), c.notes)
            play()
        }
        engine.play()
        assertArrayEquals(render(32 * stepFrames, expected), render(32 * stepFrames), 0f)
    }

    @Test fun independentQueuesAndReturnToSceneCommitTogetherAtTheEndOfTheView() {
        val a = StepPattern(16, emptyList(), listOf(Note(0, 50)))
        val b = StepPattern(16, emptyList(), listOf(Note(0, 60)))
        engine.trackClipPatterns = listOf(a, b)
        engine.pattern = StepPattern.concat(listOf(a, b))
        engine.play()
        render(1)
        engine.launchTrackClip(Inst.KICK, 1)
        engine.launchTrackClip(Inst.SYNTH, 0)
        engine.launchTrackClip(Inst.SYNTH, 0)
        assertNull(engine.pendingTrackClip(Inst.SYNTH))
        assertEquals(1, engine.pendingTrackClip(Inst.KICK))
        engine.launchTrackClip(Inst.SYNTH, 1)
        val queued = engine.trackClipRevision
        render(32 * stepFrames - 1)
        assertTrue(engine.activeTrackClips().isEmpty())
        render(1)
        assertTrue(engine.trackClipRevision > queued)
        assertEquals(mapOf(Inst.KICK to 1, Inst.SYNTH to 1), engine.activeTrackClips())
        engine.launchTrackClip(Inst.KICK, 0)
        engine.followSceneClips()
        assertEquals(-1, engine.pendingTrackClip(Inst.KICK))
        assertEquals(-1, engine.pendingTrackClip(Inst.SYNTH))
        render(32 * stepFrames)
        assertTrue(engine.activeTrackClips().isEmpty())
        assertNull(engine.pendingTrackClip(Inst.SYNTH))
    }

    @Test fun playingThenSelectingBeforeTheFirstBlockStartsTheClipOnTheFirstBeat() {
        val a = StepPattern(16, emptyList(), listOf(Note(0, 48)))
        val b = StepPattern(16, emptyList(), listOf(Note(0, 69)))
        engine.pattern = a
        engine.trackClipPatterns = listOf(a, b)
        val played = mutableListOf<Pair<Int, Long>>()
        engine.onSynthNote = { note, frame -> played.add(note to frame) }
        engine.play()
        engine.launchTrackClip(Inst.SYNTH, 1)
        render(1)
        assertEquals(listOf(69 to 0L), played)
        engine.launchTrackClip(Inst.SYNTH, -1)
        engine.stop()
        assertEquals(1, engine.activeTrackClip(Inst.SYNTH))
        assertNull(engine.pendingTrackClip(Inst.SYNTH))
        engine.followSceneClips()
        assertEquals(-1, engine.activeTrackClip(Inst.SYNTH))
    }

    @Test fun sceneSwitchLeavesAMelodicClipRunningAndClipSwitchReleasesOnlyItsOwnNotes() {
        val held = mutableSetOf<Int>()
        engine.sf2Factory = { _, _, _ -> object : ExternalSynth {
            override fun noteOn(note: Int, velocity: Int) { held += note }
            override fun noteOff(note: Int) { held -= note }
            override fun allOff() { held.clear() }
            override fun setProgram(bank: Int, program: Int) {}
            override fun render(left: FloatArray, right: FloatArray, count: Int) {}
            override val activeVoices get() = held.size
        } }
        engine.setTracks(listOf(TrackSpec(Inst.SYNTH, TrackKind.SF2)))
        val scene = StepPattern(16, emptyList())
        // Le clip de 64 pas tient sa note sur plusieurs tours de la scène de 16.
        val clip = StepPattern(64, emptyList(), listOf(Note(0, 64, 60), Note(20, 67, 35)))
        engine.pattern = scene
        engine.song = arrayOf(scene, scene)
        engine.trackClipPatterns = listOf(scene, clip)
        engine.launchTrackClip(Inst.SYNTH, 1)
        engine.play()
        render(1)
        engine.queuePadLoop(0, scene)
        render(16 * stepFrames)
        assertTrue(64 in held)
        render(4 * stepFrames)
        assertTrue(67 in held)
        engine.trigger(Inst.SYNTH, 64)
        engine.launchTrackClip(Inst.SYNTH, -1)
        render(12 * stepFrames)
        assertFalse(67 in held)
        assertTrue(64 in held)
        engine.release(Inst.SYNTH, 64)
        render(1)
        assertTrue(held.isEmpty())
    }

    @Test fun hitsReadAfterAClipChangeKeepTheirOriginalRecordingDestination() {
        val empty = StepPattern(16, emptyList())
        engine.pattern = empty
        engine.trackClipPatterns = listOf(empty, empty)
        engine.recordHits = true
        engine.launchTrackClip(Inst.KICK, 0)
        engine.play()
        render(1)
        engine.trigger(Inst.KICK)
        engine.launchTrackClip(Inst.KICK, 1)
        render(16 * stepFrames)
        engine.trigger(Inst.KICK)
        render(1)
        val slots = mutableListOf<Int>()
        engine.takeLiveHitsWithSlot { _, _, _, slot -> slots += slot }
        assertEquals(listOf(0, 1), slots)
        engine.setTracks(emptyList())
        engine.setTracks(TrackSpec.defaults())
        assertEquals(-1, engine.activeTrackClip(Inst.KICK))
    }
}
