package com.Atom2Universe.app.studio

import android.app.Application
import android.view.MotionEvent
import com.Atom2Universe.app.studio.ui.PadsView
import com.Atom2Universe.app.studio.data.PadSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "mdpi")
class PadsViewTest {
    private fun drum(track: Int, size: PadSize = PadSize.NORMAL) = PadsView.Tile(track, 0xFF446699.toInt(), 0, padSize = size)

    private fun pads(vararg items: PadsView.Tile) = PadsView(RuntimeEnvironment.getApplication()).apply {
        tiles = items.toList()
        layout(0, 0, 400, 100)
    }

    private fun PadsView.touch(action: Int, x: Int, y: Int) {
        val event = MotionEvent.obtain(0, 10, action, x.toFloat(), y.toFloat(), 0)
        try {
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    @Test fun resizeOnlyFromTheHandleAndReorderOnlyOnDropWithoutPlaying() {
        val pads = pads(drum(8), drum(7), drum(3), drum(9))
        val moves = mutableListOf<Pair<Int, Int>>()
        val resizes = mutableListOf<Int>()
        val hits = mutableListOf<Int>()
        pads.onDown = { hits += it }
        pads.onUp = { hits += it }
        pads.onReorder = { from, to -> moves += from to to }
        pads.onResize = { resizes += it }
        pads.editing = true
        pads.touch(MotionEvent.ACTION_DOWN, 50, 62)
        pads.touch(MotionEvent.ACTION_UP, 50, 62)
        pads.touch(MotionEvent.ACTION_DOWN, 92, 16)
        pads.touch(MotionEvent.ACTION_UP, 93, 17)
        assertEquals(listOf(8), resizes)

        pads.touch(MotionEvent.ACTION_DOWN, 50, 62)
        pads.touch(MotionEvent.ACTION_MOVE, 250, 62)
        assertEquals(emptyList<Pair<Int, Int>>(), moves)
        pads.touch(MotionEvent.ACTION_CANCEL, 250, 62)
        pads.touch(MotionEvent.ACTION_DOWN, 50, 62)
        pads.touch(MotionEvent.ACTION_MOVE, 250, 62)
        pads.touch(MotionEvent.ACTION_UP, 410, 62)
        assertEquals(emptyList<Pair<Int, Int>>(), moves)

        pads.touch(MotionEvent.ACTION_DOWN, 50, 62)
        pads.touch(MotionEvent.ACTION_MOVE, 250, 62)
        pads.tiles = listOf(drum(8), drum(7), drum(3), drum(9))
        pads.touch(MotionEvent.ACTION_UP, 250, 62)
        assertEquals(listOf(8 to 3), moves)
        assertEquals(listOf(8), resizes)
        assertEquals(emptyList<Int>(), hits)
    }

    @Test fun compactDrumsShareTheTopRowAndLeaveTheWholeKeyboardPlayableAfterRotation() {
        val drums = (0..6).map { drum(it, PadSize.HALF) }
        val keyboard = PadsView.Tile(7, 0xFF446699.toInt(), 0, intArrayOf(60, 64, 67))
        val pads = pads(*(drums + keyboard).toTypedArray())
        val hits = mutableListOf<Int>()
        val notes = mutableListOf<Int>()
        pads.onDown = { hits += it }
        pads.onNoteDown = { _, pitch -> notes += pitch }
        for ((w, h) in listOf(800 to 600, 600 to 800)) {
            pads.layout(0, 0, w, h)
            hits.clear()
            notes.clear()
            for (i in 0..6) {
                val x = (i * 2 + 1) * w / 14
                pads.touch(MotionEvent.ACTION_DOWN, x, 20)
                pads.touch(MotionEvent.ACTION_UP, x, 20)
            }
            for (x in listOf(10, w / 2, w - 10)) {
                pads.touch(MotionEvent.ACTION_DOWN, x, h / 4)
                pads.touch(MotionEvent.ACTION_UP, x, h / 4)
            }
            assertEquals((0..6).toList(), hits)
            assertEquals(listOf(60, 64, 67), notes)
        }
    }

    @Test fun smallHandleIsSilentAndIndependentOfTheWideHandleOnAHalfTile() {
        val pads = pads(drum(8, PadSize.HALF), drum(7), drum(3), drum(9))
        val sizes = mutableListOf<Pair<Int, PadSize>>()
        val hits = mutableListOf<Int>()
        pads.onDown = { hits += it }
        pads.onToggleHalf = { sizes += it to PadSize.HALF }
        pads.onResize = { sizes += it to PadSize.WIDE }
        pads.editing = true
        for (x in listOf(8, 43)) {
            pads.touch(MotionEvent.ACTION_DOWN, x, 8)
            pads.touch(MotionEvent.ACTION_UP, x, 8)
        }
        assertEquals(listOf(8 to PadSize.HALF, 8 to PadSize.WIDE), sizes)
        assertEquals(emptyList<Int>(), hits)
    }

    @Test fun widePadSharesTwoColumnsEvenWhenPlacedAfterAnotherPad() {
        val pads = pads(drum(12), drum(13, size = PadSize.WIDE), drum(14))
        val hits = mutableListOf<Int>()
        pads.onDown = { hits += it }
        for (x in listOf(50, 150, 250, 350)) {
            pads.touch(MotionEvent.ACTION_DOWN, x, 50)
            pads.touch(MotionEvent.ACTION_UP, x, 50)
        }
        assertEquals(listOf(12, 13, 13, 14), hits)
    }

    @Test fun droppingInTheUnusedEndOfTheLastRowMovesToTheLastPosition() {
        val pads = pads(drum(2), drum(5), drum(8))
        pads.layout(0, 0, 200, 200)
        pads.editing = true
        val moves = mutableListOf<Pair<Int, Int>>()
        pads.onReorder = { from, to -> moves += from to to }
        pads.touch(MotionEvent.ACTION_DOWN, 50, 60)
        pads.touch(MotionEvent.ACTION_MOVE, 155, 155)
        pads.touch(MotionEvent.ACTION_UP, 155, 155)
        assertEquals(listOf(2 to 8), moves)
    }

    @Test fun heldNoteSurvivesEquivalentRefreshAndStopsWhenSwitchingToEdit() {
        fun keys(vararg pitches: Int) = PadsView.Tile(23, 0xFFE07744.toInt(), 0, pitches)
        val pads = pads(keys(60, 64, 67))
        val attacks = mutableListOf<Int>()
        val releases = mutableListOf<Int>()
        pads.onNoteDown = { _, pitch -> attacks += pitch }
        pads.onNoteUp = { _, pitch -> releases += pitch }
        pads.touch(MotionEvent.ACTION_DOWN, 40, 70)
        pads.tiles = listOf(keys(60, 64, 67))
        assertEquals(emptyList<Int>(), releases)
        pads.touch(MotionEvent.ACTION_MOVE, 155, 70)
        assertEquals(listOf(60, 64), attacks)
        assertEquals(listOf(60), releases)
        pads.editing = true
        pads.editing = false
        pads.touch(MotionEvent.ACTION_MOVE, 350, 70)
        pads.touch(MotionEvent.ACTION_UP, 350, 70)
        assertEquals(listOf(60, 64), attacks)
        assertEquals(listOf(60, 64), releases)
    }
}
