package com.Atom2Universe.app.games.mochi

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.Atom2Universe.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Intégration des vrais contrôles : choix du mode, annulation tactile et pause. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h820dp-port")
class MochiActivityTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("mochi_save", Context.MODE_PRIVATE)

    @Before fun clearSave() { prefs.edit().clear().commit() }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun views(activity: MochiActivity) = descendants(activity.findViewById(android.R.id.content))

    private fun click(activity: MochiActivity, label: Int) {
        val button = views(activity).filterIsInstance<Button>().first {
            it.visibility == View.VISIBLE && it.text.toString() == activity.getString(label)
        }
        assertTrue(button.performClick())
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    @Test fun normalIsDefaultAndEasyChoicePersistsWithoutChangingTheOldRecord() {
        prefs.edit().putInt("best", 250).commit()
        val first = Robolectric.buildActivity(MochiActivity::class.java).setup()
        try {
            val activity = first.get()
            val toggle = views(activity).filterIsInstance<SwitchCompat>().single()
            assertFalse(toggle.isChecked)
            toggle.isChecked = true
            click(activity, R.string.mochi_play)
            assertTrue(views(activity).filterIsInstance<MochiView>().single().game.easyMode)
            assertTrue(prefs.getBoolean("easy_mode", false))
        } finally { first.pause().stop().destroy() }
        assertEquals(250, prefs.getInt("best_easy", 0))
        assertEquals(0, prefs.getInt("best_normal", 0))
        val next = Robolectric.buildActivity(MochiActivity::class.java).setup()
        try {
            assertTrue(views(next.get()).filterIsInstance<SwitchCompat>().single().isChecked)
        } finally { next.pause().stop().destroy() }
    }

    @Test fun cancelledGestureAndPauseDoNotSpendTheThreeGuidedLaunches() {
        val controller = Robolectric.buildActivity(MochiActivity::class.java).setup()
        try {
            val activity = controller.get()
            click(activity, R.string.mochi_play)
            val view = views(activity).filterIsInstance<MochiView>().single()
            view.layout(0, 0, 420, 700)
            touch(view, MotionEvent.ACTION_DOWN, 210f, 300f)
            touch(view, MotionEvent.ACTION_MOVE, 190f, 410f)
            touch(view, MotionEvent.ACTION_CANCEL, 190f, 410f)
            assertEquals(0, view.game.shots)
            touch(view, MotionEvent.ACTION_DOWN, 210f, 300f)
            touch(view, MotionEvent.ACTION_MOVE, 190f, 410f)
            click(activity, R.string.mochi_pause)
            touch(view, MotionEvent.ACTION_UP, 190f, 410f)
            assertEquals(0, view.game.shots)
            click(activity, R.string.mochi_resume)
            view.layout(0, 0, 420, 700)
            touch(view, MotionEvent.ACTION_DOWN, 210f, 300f)
            touch(view, MotionEvent.ACTION_UP, 190f, 410f)
            assertEquals(1, view.game.shots)
            assertEquals(MochiGame.Phase.FLYING, view.game.phase)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun climbingJournalKeepsProgressSeparateBetweenModes() {
        prefs.edit().putInt("mastery_normal_chain3", 3).putInt("mastery_normal_rest1", 1).commit()
        val controller = Robolectric.buildActivity(MochiActivity::class.java).setup()
        try {
            val activity = controller.get()
            fun displayedJournal(completed: Int, easy: Boolean, challenge: String) = activity.getString(
                R.string.mochi_mastery, completed, 6,
                activity.getString(if (easy) R.string.mochi_mode_easy else R.string.mochi_mode_normal), challenge)
            val normal = displayedJournal(2, false, activity.getString(R.string.mochi_mastery_next,
                activity.getString(R.string.mochi_goal_detours, 2), 0, 2))
            assertTrue(views(activity).filterIsInstance<TextView>().any { it.text.toString() == normal })
            views(activity).filterIsInstance<SwitchCompat>().single().isChecked = true
            val easy = displayedJournal(0, true, activity.getString(R.string.mochi_mastery_next,
                activity.getString(R.string.mochi_goal_chain, 3), 0, 3))
            assertTrue(views(activity).filterIsInstance<TextView>().any { it.text.toString() == easy })
            assertEquals(3, prefs.getInt("mastery_normal_chain3", 0))
        } finally { controller.pause().stop().destroy() }
    }
}
