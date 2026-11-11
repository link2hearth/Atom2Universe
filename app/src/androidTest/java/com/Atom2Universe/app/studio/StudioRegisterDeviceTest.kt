package com.Atom2Universe.app.studio

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.ui.KeyboardView
import com.Atom2Universe.app.studio.ui.PianoRollView
import com.Atom2Universe.app.studio.ui.StudioTonicKeyboard
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.io.File
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioRegisterDeviceTest {
    @Test fun middleCOpensByDefaultAndRhythmCanBeLocatedAfterChangingKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val project = StudioEngine(48_000).use { engine ->
            val session = StudioSession(engine, StudioProject.blank().copy(keyPc = 0))
            session.addNote(0, 24, 1)
            session.project
        }
        val library = StudioLibrary(File(context.filesDir, "studio/songs"))
        val song = library.create("Keyboard register test", project)
        try {
            val intent = Intent(context, StudioActivity::class.java).putExtra(EXTRA_PROJECT_ID, song.id)
            ActivityScenario.launch<StudioActivity>(intent).use {
                val tab = withParent(isAssignableFrom(MaterialButtonToggleGroup::class.java))
                val notesTab = onView(allOf(withText(R.string.studio_tab_notes), tab, isDisplayed()))
                notesTab.perform(click())
                assertEquals(60, checkRegister(0, 60).first)
                onView(isAssignableFrom(PianoRollView::class.java)).perform(
                    GeneralSwipeAction(Swipe.SLOW, sideKeyPosition(.30f), sideKeyPosition(.85f), Press.FINGER)
                )
                val shifted = checkRegister(0, 60)
                assertTrue("Dragging the side keyboard must scroll to higher pitches", shifted.first > 60)

                val settings = onView(allOf(withText(R.string.studio_tab_notes),
                    isAssignableFrom(MaterialButton::class.java), not(tab), isDisplayed()))
                settings.perform(click())
                val names = context.resources.getStringArray(R.array.studio_key_names)
                val tonic = withParent(isAssignableFrom(StudioTonicKeyboard::class.java))
                onView(allOf(withText(names[0]), tonic)).check(matches(isSelected()))
                val sharp = onView(allOf(withText(names[3]), tonic))
                sharp.perform(click())
                sharp.check(matches(isSelected()))
                assertEquals(shifted, checkRegister(3, 63))
                onView(allOf(withText(R.string.studio_pads), tab, isDisplayed())).perform(click())
                notesTab.perform(click())
                assertEquals(shifted, checkRegister(3, 63))

                settings.perform(click())
                onView(withText(context.getString(R.string.studio_rhythm_pitch, names[3] + "4"))).perform(click())
                val lowerSharp = names[3] + "1"
                onView(withText(lowerSharp)).perform(click())
                assertTrue("The indicated rhythm key must now be visible", 27 in checkRegister(3, 27))
                onView(withText(context.getString(R.string.studio_rhythm_pitch, lowerSharp))).check(matches(isDisplayed()))
            }
        } finally {
            library.delete(song.id)
        }
    }

    private fun sideKeyPosition(heightFraction: Float) = CoordinatesProvider { view ->
        val position = IntArray(2).also(view::getLocationOnScreen)
        floatArrayOf(position[0] + 24f * view.resources.displayMetrics.density,
            position[1] + heightFraction * view.height)
    }

    private fun checkRegister(expectedKey: Int, expectedRhythm: Int): IntRange {
        var range = IntRange.EMPTY
        onView(isAssignableFrom(KeyboardView::class.java)).check { view, error ->
            if (error != null) throw error
            val keyboard = view as KeyboardView
            range = keyboard.low..keyboard.high
            assertEquals(expectedKey, keyboard.keyPc)
            assertEquals(expectedRhythm, keyboard.rhythmPitch)
        }
        onView(isAssignableFrom(PianoRollView::class.java)).check { view, error ->
            if (error != null) throw error
            val roll = view as PianoRollView
            assertEquals(range, roll.rows.first()..roll.rows.last())
            assertEquals(expectedKey, roll.keyPc)
            assertEquals(expectedRhythm, roll.rhythmPitch)
        }
        return range
    }
}
