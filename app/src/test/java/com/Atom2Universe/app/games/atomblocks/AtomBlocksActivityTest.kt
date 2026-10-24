package com.Atom2Universe.app.games.atomblocks

import android.app.Application
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.Atom2Universe.app.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Small lifecycle regressions: real themed controls, no rendering and no native audio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h820dp-port")
class AtomBlocksActivityTest {
    @Before fun muteBeforeCreatingTheActivity() {
        assertTrue(AtomBlocksStore(RuntimeEnvironment.getApplication()).prefs.edit().clear()
            .putBoolean("sound", false).putBoolean("music", false)
            .putBoolean("effects", false).putBoolean("haptics", false).commit())
    }

    @Test fun classicOptionsMenuAndRotationKeepTheCurrentGame() {
        val savedState = Bundle()
        lateinit var checkpoint: AtomBlocksEngine.Frame
        val portrait = Robolectric.buildActivity(AtomBlocksActivity::class.java).setup()
        try {
            val activity = portrait.get()
            assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
            clickButton(activity, R.string.ab_classic)
            val board = board(activity)
            assertEquals(AtomBlocksEngine.Mode.CLASSIC, board.game!!.mode)
            val move = board.game!!.hint()!!
            board.onPlace!!.invoke(move.slot, move.row, move.col)
            checkpoint = board.game!!.snapshot().state
            assertTrue(checkpoint.score > 0)

            clickButton(activity, R.string.ab_more)
            val actions = latestDialog()
            assertEquals(3, actions.listView.adapter.count)
            actions.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            // AppCompat posts dismissal after dispatching its button listener.
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(actions.isShowing)

            descendants(content(activity)).first {
                it.contentDescription?.toString() == activity.getString(R.string.ab_settings)
            }.performClick()
            val settings = latestDialog()
            val switches = descendants(settings.window!!.decorView).filterIsInstance<SwitchCompat>().toList()
            assertEquals(4, switches.size)
            assertFalse(switches.first { it.text.toString() == activity.getString(R.string.ab_sound) }.isChecked)
            assertFalse(switches.first { it.text.toString() == activity.getString(R.string.ab_music) }.isChecked)
            settings.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(settings.isShowing)

            activity.onBackPressedDispatcher.onBackPressed()
            assertFalse(descendants(content(activity)).any { it is AtomBlocksBoardView })
            clickButton(activity, activity.getString(R.string.ab_resume, activity.getString(R.string.ab_classic)))
            assertFrameEquals(checkpoint, board(activity).game!!.snapshot().state)
        } finally {
            portrait.pause().saveInstanceState(savedState).stop().destroy()
        }

        // Simulate Android recreating an activity that does not consume orientation changes.
        RuntimeEnvironment.setQualifiers("w820dp-h412dp-land")
        val landscape = Robolectric.buildActivity(AtomBlocksActivity::class.java)
            .create(savedState).start().resume().visible()
        try {
            val activity = landscape.get()
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
            assertFrameEquals(checkpoint, board(activity).game!!.snapshot().state)
            assertTrue(board(activity).game!!.canUndo)
            assertNotNull(button(activity, activity.getString(R.string.ab_more)).parent)
            assertNotNull(button(activity, activity.getString(R.string.ab_hint)).parent)
            clickButton(activity, R.string.ab_undo)
            assertEquals(0, board(activity).game!!.moves)
        } finally {
            landscape.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w820dp-h412dp-land")
    fun openingClassicDirectlyInLandscapeAttachesEachActionOnce() {
        val controller = Robolectric.buildActivity(AtomBlocksActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
            clickButton(activity, R.string.ab_classic)
            assertEquals(AtomBlocksEngine.Mode.CLASSIC, board(activity).game!!.mode)
            for (title in listOf(R.string.ab_undo, R.string.ab_hint, R.string.ab_more)) {
                assertEquals(1, descendants(content(activity)).filterIsInstance<Button>()
                    .count { it.text.toString() == activity.getString(title) })
            }
            clickButton(activity, R.string.ab_hint)
            activity.onBackPressedDispatcher.onBackPressed()
            clickButton(activity, activity.getString(R.string.ab_resume, activity.getString(R.string.ab_classic)))
            assertNotNull(board(activity))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun content(activity: AtomBlocksActivity): View = activity.findViewById(android.R.id.content)

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun board(activity: AtomBlocksActivity) =
        descendants(content(activity)).filterIsInstance<AtomBlocksBoardView>().single()

    private fun button(activity: AtomBlocksActivity, text: String) =
        descendants(content(activity)).filterIsInstance<Button>().single { it.text.toString() == text }

    private fun clickButton(activity: AtomBlocksActivity, title: Int) = clickButton(activity, activity.getString(title))
    private fun clickButton(activity: AtomBlocksActivity, text: String) { assertTrue(button(activity, text).performClick()) }

    private fun latestDialog(): AlertDialog = (ShadowDialog.getLatestDialog() as AlertDialog).also { assertTrue(it.isShowing) }

    private fun assertFrameEquals(expected: AtomBlocksEngine.Frame, actual: AtomBlocksEngine.Frame) {
        assertArrayEquals(expected.board, actual.board)
        assertArrayEquals(expected.gems, actual.gems)
        assertEquals(expected.copy(board = actual.board, gems = actual.gems), actual)
    }
}
