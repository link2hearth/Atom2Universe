package com.Atom2Universe.app.studio

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
import com.google.android.material.button.MaterialButtonToggleGroup
import java.io.File
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioRegisterDeviceTest {
    @Test fun changingKeyAndReturningToNotesKeepTheOctaveChosenOnTheKeyboard() {
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
                onView(allOf(withText(R.string.studio_tab_notes), not(tab), isDisplayed())).perform(click())
                repeat(5) { onView(withContentDescription(R.string.studio_octave_up)).perform(click()) }
                checkRegister(84, 0)
                val names = context.resources.getStringArray(R.array.studio_key_names)
                onView(withText(context.getString(R.string.studio_key_label, names[0]))).perform(click())
                onView(withText(names[9])).perform(click())
                checkRegister(84, 9)
                onView(allOf(withText(R.string.studio_pads), tab, isDisplayed())).perform(click())
                notesTab.perform(click())
                checkRegister(84, 9)
            }
        } finally {
            library.delete(song.id)
        }
    }

    private fun checkRegister(expectedLow: Int, expectedKey: Int) {
        onView(isAssignableFrom(KeyboardView::class.java)).check { view, error ->
            if (error != null) throw error
            val keyboard = view as KeyboardView
            assertEquals(expectedLow, keyboard.low)
            assertEquals(expectedKey, keyboard.keyPc)
        }
        onView(isAssignableFrom(PianoRollView::class.java)).check { view, error ->
            if (error != null) throw error
            assertEquals(expectedLow, (view as PianoRollView).rows.first())
        }
    }
}
