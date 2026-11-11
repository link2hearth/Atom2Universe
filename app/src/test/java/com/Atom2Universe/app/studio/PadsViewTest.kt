package com.Atom2Universe.app.studio

import android.app.Application
import android.view.MotionEvent
import com.Atom2Universe.app.studio.ui.PadsView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "mdpi")
class PadsViewTest {
    private fun drum(track: Int, wide: Boolean = false) = PadsView.Tile(track, 0xFF446699.toInt(), 0, wide = wide)

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

    @Test fun widePadSharesTwoColumnsEvenWhenPlacedAfterAnotherPad() {
        val pads = pads(drum(12), drum(13, wide = true), drum(14))
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
