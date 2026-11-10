package com.Atom2Universe.app.studio

import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.Atom2Universe.app.studio.ui.StudioPanelHost
import com.Atom2Universe.app.studio.ui.KeyboardView
import com.Atom2Universe.app.studio.ui.PianoRollView
import com.Atom2Universe.app.studio.engine.Note
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StudioTouchTest {
    @Test fun keyDepthChangesAttackOnlyWhenDynamicTouchIsEnabled() {
        val keyboard = KeyboardView(RuntimeEnvironment.getApplication()).apply {
            low = 60
            high = 72
            locked = false
            layout(0, 0, 400, 200)
        }
        val attacks = mutableListOf<Float>()
        val releases = mutableListOf<Int>()
        keyboard.listener = object : KeyboardView.Listener {
            override fun onKeyDown(pitch: Int, velocity: Float) { attacks += velocity }
            override fun onKeyUp(pitch: Int) { releases += pitch }
        }
        fun tap(y: Float) {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 20, action, 12f, y, 0)
                keyboard.onTouchEvent(event)
                event.recycle()
            }
        }
        tap(24f)
        tap(180f)
        keyboard.dynamicTouch = true
        tap(24f)
        tap(180f)
        assertEquals(listOf(60, 60, 60, 60), releases)
        assertEquals(attacks[0], attacks[1], 0f)
        assertTrue(attacks[2] < 0.5f && attacks[3] > 0.9f)
    }

    @Test fun narrowingTheDeskKeepsSideBySidePanelsApart() {
        val ctx = RuntimeEnvironment.getApplication()
        val d = ctx.resources.displayMetrics.density
        val host = StudioPanelHost(ctx)
        val content = FrameLayout(ctx)
        host.addView(content)
        fun layout(widthDp: Int) {
            val w = (widthDp * d).toInt()
            val h = (700 * d).toInt()
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, w, h)
        }
        layout(1200)
        host.show(101, "Notes", TextView(ctx), 300, content)
        layout(1200)
        host.show(102, "Arp", TextView(ctx), 300, content)
        layout(1200)
        val a = host.getChildAt(1)
        val b = host.getChildAt(2)
        a.x = 500 * d
        b.x = 810 * d
        layout(720)
        assertTrue(a.x + a.width <= b.x)
        assertTrue(b.x + b.width <= host.width)
    }

    @Test fun rejectedDragCannotDeleteTheNoteAndRegisterScrollIsSilent() {
        val ctx = RuntimeEnvironment.getApplication()
        val roll = PianoRollView(ctx)
        val note = Note(2, 60)
        roll.rows = intArrayOf(59, 60, 61)
        roll.notes = { listOf(note) }
        roll.keyPc = 0
        var edits = 0
        var previews = 0
        var scrolls = 0
        roll.listener = object : PianoRollView.Listener {
            override fun onRemoveNote(note: Note) { edits++ }
            override fun onEditPosition(note: Note, step: Int, pitch: Int, length: Int) { edits++ }
            override fun onAddNote(step: Int, pitch: Int, length: Int) { edits++ }
            override fun onPreview(pitch: Int) { previews++ }
            override fun onScrollRegister(whiteKeys: Int) { scrolls++ }
            override fun onSelectionChanged() {}
            override fun onPasteAt(pitch: Int, step: Int) {}
            override fun onEditNote(note: Note, screenX: Float, screenY: Float) {}
            override fun onRevealNote(note: Note) {}
        }
        val d = ctx.resources.displayMetrics.density
        val w = (600 * d).toInt()
        val h = (192 * d).toInt()
        roll.layout(0, 0, w, h)
        fun touch(action: Int, x: Int, y: Int) {
            val e = MotionEvent.obtain(0, 20, action, x * d, y * d, 0)
            roll.onTouchEvent(e)
            e.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 149, 106)
        assertEquals(1, previews)
        touch(MotionEvent.ACTION_MOVE, 149, 50)
        touch(MotionEvent.ACTION_UP, 149, 50)
        assertEquals(0, edits)
        touch(MotionEvent.ACTION_DOWN, 25, 50)
        touch(MotionEvent.ACTION_MOVE, 25, 164)
        touch(MotionEvent.ACTION_UP, 25, 164)
        assertTrue(scrolls > 0)
        assertEquals(1, previews)
    }

    @Test fun movingPanelWithOneFingerLeavesSecondFingerOnInstrument() {
        val ctx = RuntimeEnvironment.getApplication()
        val host = StudioPanelHost(ctx)
        val instrument = FrameLayout(ctx)
        val actions = mutableListOf<Int>()
        instrument.setOnTouchListener { _, e -> actions += e.actionMasked; true }
        host.addView(instrument)
        fun layout() {
            val size = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
            host.measure(size, size)
            host.layout(0, 0, 1000, 1000)
        }
        layout()
        host.show(123, "Arp", TextView(ctx), 180, instrument, 160f to 50f)
        layout()
        val card = host.getChildAt(1)
        val start = card.x
        val y = card.y + 10
        fun send(action: Int, x: Float, two: Boolean) {
            val n = if (two) 2 else 1
            val ids = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(n) { i -> MotionEvent.PointerCoords().apply {
                this.x = if (i == 0) x else 950f
                this.y = if (i == 0) y else 925f
                pressure = 1f
            } }
            val e = MotionEvent.obtain(0, 20, action, n, ids, coords, 0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            host.dispatchTouchEvent(e)
            e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, start + 12, false)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), start + 12, true)
        send(MotionEvent.ACTION_MOVE, start + 92, true)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), start + 92, true)
        send(MotionEvent.ACTION_UP, start + 92, false)
        assertTrue(card.x > start + 40)
        assertTrue(MotionEvent.ACTION_DOWN in actions && MotionEvent.ACTION_UP in actions)
        assertEquals(2, host.childCount)
        host.closeTop()
        assertEquals(1, host.childCount)
    }
}
