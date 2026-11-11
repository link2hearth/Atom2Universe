package com.Atom2Universe.app.studio

import android.app.Application
import android.view.MotionEvent
import com.Atom2Universe.app.studio.ui.PadsView
import com.Atom2Universe.app.studio.data.PadSize
import com.Atom2Universe.app.studio.data.PadPlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun melodic(mode: PadPlayMode) =
        PadsView.Tile(23, 0xFFE07744.toInt(), 0, intArrayOf(60, 64, 67), mode = mode)

    @Test fun modeButtonOpensOnReleaseAndDraggingOffItNeverPlays() {
        val pads = pads(melodic(PadPlayMode.DRIVE))
        val chosen = mutableListOf<Int>()
        val notes = mutableListOf<Int>()
        pads.onChooseMode = { track, _, _ -> chosen += track }
        pads.onNoteDown = { _, pitch -> notes += pitch }
        pads.touch(MotionEvent.ACTION_DOWN, 380, 9)
        pads.touch(MotionEvent.ACTION_MOVE, 350, 68)
        pads.touch(MotionEvent.ACTION_UP, 380, 9)
        assertTrue(chosen.isEmpty())
        pads.touch(MotionEvent.ACTION_DOWN, 380, 9)
        pads.touch(MotionEvent.ACTION_UP, 380, 9)
        assertEquals(listOf(23), chosen)
        assertTrue(notes.isEmpty())
    }

    @Test fun thereminGlidesWithoutRetriggerAndUsesAbsoluteHeightForExpression() {
        val pads = pads(melodic(PadPlayMode.THEREMIN))
        val events = mutableListOf<String>()
        val expression = mutableListOf<Float>()
        pads.onThereminExpression = { _, value -> events += "expression"; expression += value }
        pads.onNoteDown = { _, pitch -> events += "on $pitch" }
        pads.onGlide = { _, from, to -> events += "$from to $to" }
        pads.onNoteUp = { _, pitch -> events += "off $pitch" }
        pads.touch(MotionEvent.ACTION_DOWN, 28, 94)
        pads.touch(MotionEvent.ACTION_MOVE, 188, 62)
        pads.touch(MotionEvent.ACTION_MOVE, 188, 42)
        pads.touch(MotionEvent.ACTION_CANCEL, 190, 42)
        assertEquals(listOf("expression", "on 60", "expression", "60 to 64", "expression", "off 64"), events)
        assertTrue(expression.first() < 0.3f && expression.last() > 0.8f)
    }

    @Test fun changingHeldModeReleasesTheOldVoiceAndCleanIgnoresVerticalTravel() {
        val pads = pads(melodic(PadPlayMode.FILTER))
        val modulations = mutableListOf<Float>()
        val attacks = mutableListOf<Int>()
        val releases = mutableListOf<Int>()
        pads.onNoteEffect = { _, amount -> modulations += amount }
        pads.onNoteDown = { _, pitch -> attacks += pitch }
        pads.onNoteUp = { _, pitch -> releases += pitch }
        pads.touch(MotionEvent.ACTION_DOWN, 30, 85)
        pads.touch(MotionEvent.ACTION_MOVE, 30, 65)
        pads.tiles = listOf(melodic(PadPlayMode.CLEAN))
        pads.touch(MotionEvent.ACTION_MOVE, 190, 60)
        pads.touch(MotionEvent.ACTION_UP, 190, 60)
        pads.touch(MotionEvent.ACTION_DOWN, 190, 85)
        pads.touch(MotionEvent.ACTION_MOVE, 190, 55)
        pads.touch(MotionEvent.ACTION_UP, 190, 55)
        assertEquals(listOf(60, 64), attacks)
        assertEquals(attacks, releases)
        assertEquals(1, modulations.size)
    }

    @Test fun thereminKeepsItsFirstFingerWhileAnotherPlaysDrums() {
        val pads = pads(melodic(PadPlayMode.THEREMIN), drum(7))
        val notes = mutableListOf<Int>()
        val drums = mutableListOf<Int>()
        pads.onNoteDown = { _, pitch -> notes += pitch }
        pads.onNoteUp = { _, pitch -> notes += -pitch }
        pads.onDown = { drums += it }
        pads.onUp = { drums += -it }
        fun fingers(action: Int, index: Int, vararg xs: Int) {
            val props = Array(xs.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(xs.size) { i -> MotionEvent.PointerCoords().apply { x = xs[i].toFloat(); y = 70f } }
            val e = MotionEvent.obtain(0, 10, action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.size,
                props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            try { pads.onTouchEvent(e) } finally { e.recycle() }
        }
        fingers(MotionEvent.ACTION_DOWN, 0, 20)
        fingers(MotionEvent.ACTION_POINTER_DOWN, 1, 20, 170)
        fingers(MotionEvent.ACTION_POINTER_DOWN, 2, 20, 170, 350)
        fingers(MotionEvent.ACTION_MOVE, 0, 20, 100, 350)
        fingers(MotionEvent.ACTION_POINTER_UP, 2, 20, 100, 350)
        fingers(MotionEvent.ACTION_POINTER_UP, 1, 20, 100)
        fingers(MotionEvent.ACTION_UP, 0, 20)
        assertEquals(listOf(60, -60), notes)
        assertEquals(listOf(7, -7), drums)
    }
}
