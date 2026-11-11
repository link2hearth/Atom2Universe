package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StudioPadLaunchTest {
    private val rate = 24_000
    private val stepFrames = rate / 8 // 120 BPM, une double-croche.
    private val engine = StudioEngine(rate).apply { bpm = 120f }

    private fun pattern(steps: Int, pitch: Int) = StepPattern(steps, emptyList(), listOf(Note(0, pitch)))

    private fun render(frames: Int) = engine.render(FloatArray(frames), FloatArray(frames), frames)

    @Test fun latestLaunchWaitsForTheWholeVisibleLoopAndStartsAtZero() {
        val old = StepPattern.concat(listOf(pattern(16, 53), pattern(16, 54)))
        val target = pattern(64, 67)
        val notes = mutableListOf<Pair<Int, Long>>()
        engine.onSynthNote = { note, frame -> notes.add(note to frame) }
        engine.pattern = old
        engine.play()
        render(3 * stepFrames)
        engine.queuePadLoop(1, pattern(16, 55))
        engine.queuePadLoop(2, target)
        render(29 * stepFrames)
        assertSame(old, engine.displayPattern())
        assertEquals(2, engine.pendingPadLoop)
        assertEquals(-1, engine.takeLaunchedPadLoop())
        render(1)
        assertEquals(listOf(53 to 0L, 54 to 16L * stepFrames, 67 to 32L * stepFrames), notes)
        assertEquals(0, engine.currentStep)
        assertSame(target, engine.displayPattern())
        assertEquals(-1, engine.pendingPadLoop)
        // Un choix fait avant que l'écran ait vu le lancement précédent ne perd pas cet acquittement.
        engine.queuePadLoop(3, pattern(16, 68))
        assertEquals(2, engine.takeLaunchedPadLoop())
        assertEquals(3, engine.pendingPadLoop)
        assertEquals(-1, engine.takeLaunchedPadLoop())
    }

    @Test fun launchLeavesSongChainOnItsDownbeatAndSurvivesStopBeforeUiPoll() {
        val target = pattern(16, 62)
        engine.song = arrayOf(pattern(16, 58), pattern(16, 59))
        engine.play()
        render(21 * stepFrames)
        assertEquals(1, engine.songPosition)
        engine.queuePadLoop(3, target)
        render(11 * stepFrames + 1)
        assertNull(engine.song)
        assertSame(target, engine.displayPattern())
        engine.stop()
        render(1)
        assertEquals(3, engine.takeLaunchedPadLoop())
        assertSame(target, engine.pattern)
        assertNull(engine.song)
    }

    @Test fun cancelledAndStoppedRequestsDoNotLaunchOnTheNextRun() {
        val initial = pattern(16, 53)
        engine.pattern = initial
        engine.play()
        render(1)
        engine.queuePadLoop(1, pattern(16, 60))
        engine.cancelPadLoop()
        render(16 * stepFrames)
        assertSame(initial, engine.displayPattern())
        engine.queuePadLoop(2, pattern(16, 61))
        engine.stop()
        engine.play()
        render(16 * stepFrames + 1)
        assertSame(initial, engine.displayPattern())
        assertEquals(-1, engine.takeLaunchedPadLoop())
    }

    @Test fun drumPreviewAndPlayedHitCanShareABlockWithoutRecordingThePreview() {
        engine.recordHits = true
        engine.trigger(Inst.SNARE, recordHit = false)
        engine.trigger(Inst.KICK)
        render(256)
        val hits = mutableListOf<Int>()
        engine.takeLiveHits { track, _, _ -> hits += track }
        assertEquals(listOf(Inst.KICK), hits)
    }
}
