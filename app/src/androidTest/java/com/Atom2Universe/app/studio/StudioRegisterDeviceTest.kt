package com.Atom2Universe.app.studio

import android.content.Intent
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.data.PadPlayMode
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.ui.KeyboardView
import com.Atom2Universe.app.studio.ui.PadsView
import com.Atom2Universe.app.studio.ui.PianoRollView
import com.Atom2Universe.app.studio.ui.StudioTonicKeyboard
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.io.File
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
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
            session.setPadPlayMode(Inst.SYNTH, PadPlayMode.THEREMIN)
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
                checkThereminRange(63, 75)
                onView(withText(context.getString(R.string.studio_track_octave, names[3] + "4"))).perform(click())
                val lowerSharp = names[3] + "1"
                onView(withText(lowerSharp)).perform(click())
                checkThereminRange(27, 39)
                onView(withText(context.getString(R.string.studio_track_octave, lowerSharp))).check(matches(isDisplayed()))
                notesTab.perform(click())
                assertTrue("The indicated rhythm key must now be visible", 27 in checkRegister(3, 27))
            }
        } finally {
            library.delete(song.id)
        }
    }

    @Test fun padHeadersOpenIndependentPanelsAndNotesShowsTheEditedTrack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = StudioLibrary(File(context.filesDir, "studio/songs"))
        val project = StudioEngine(48_000).use { engine ->
            val session = StudioSession(engine, StudioProject.blank().copy(keyPc = 0))
            session.setSynthBank(Inst.SYNTH, SynthBank.SCC)
            val secondId = session.addTrack(TrackKind.SYNTH)
            session.setSynthBank(secondId, SynthBank.FAMICOM)
            session.project.copy(tracks = session.project.tracks.filter { it.melodic })
        }
        val (first, second) = project.tracks
        val song = library.create("Pad panel test", project)
        val intent = Intent(context, StudioActivity::class.java).putExtra(EXTRA_PROJECT_ID, song.id)
        val notes = context.getString(R.string.studio_tab_notes)
        fun name(engine: Int, n: Int) = context.getString(R.string.studio_track_numbered, context.getString(engine), n)
        val names = listOf(name(R.string.studio_bank_scc, 1), name(R.string.studio_bank_famicom, 2))
        val keys = context.resources.getStringArray(R.array.studio_key_names)
        fun title(n: Int) = context.getString(R.string.studio_panel_track, notes, names[n - 1])
        fun octave(k: Int, n: Int) = context.getString(R.string.studio_track_octave, keys[k] + n)
        fun panel(n: Int) = isDescendantOfA(withChild(withChild(withText(title(n)))))
        val tab = withParent(isAssignableFrom(MaterialButtonToggleGroup::class.java))
        try {
            ActivityScenario.launch<StudioActivity>(intent).use {
                onView(allOf(withText(R.string.studio_pads), tab)).perform(click())
                val pads = onView(isAssignableFrom(PadsView::class.java))
                fun open(track: Int) = pads.perform(GeneralClickAction(Tap.SINGLE, CoordinatesProvider { view ->
                    val local = Rect()
                    requireNotNull(view.accessibilityNodeProvider.createAccessibilityNodeInfo(2 * track)).getBoundsInParent(local)
                    val origin = IntArray(2).also(view::getLocationOnScreen)
                    floatArrayOf(origin[0] + local.left + 5f, origin[1] + local.exactCenterY())
                }, Press.FINGER, 0, 0))
                open(first.id)
                open(second.id)
                onView(allOf(withText(octave(0, 4)), panel(2))).perform(click())
                onView(withText(keys[0] + "6")).perform(click())
                onView(allOf(withText(keys[5]), panel(2))).perform(click())
                onView(allOf(withText(octave(5, 4)), panel(1))).check(matches(isDisplayed()))
                onView(allOf(withText(octave(5, 6)), panel(2))).check(matches(isDisplayed()))
                pads.check { view, error ->
                    if (error != null) throw error
                    val tiles = (view as PadsView).tiles
                    assertEquals(65, tiles.single { it.track == first.id }.levels!!.first())
                    assertEquals(89, tiles.single { it.track == second.id }.levels!!.first())
                }
                onView(withContentDescription(context.getString(R.string.studio_close_panel, title(2)))).perform(click())
                onView(withContentDescription(context.getString(R.string.studio_close_panel, title(1)))).perform(click())
                onView(allOf(withText(notes), tab)).perform(click())
                val firstChip = onView(allOf(withText(names[0]), isDisplayed()))
                val secondChip = onView(allOf(withText(names[1]), isDisplayed()))
                firstChip.check(matches(isSelected()))
                secondChip.perform(click()).check(matches(isSelected()))
                firstChip.check(matches(not(isSelected())))
                checkRegister(5, 89)

                val soundLabel = context.getString(R.string.studio_sound_label, "")
                onView(allOf(withContentDescription(startsWith(soundLabel)), isDisplayed())).perform(click())
                onView(allOf(withText(R.string.studio_title), isDisplayed())).perform(click())
                val renamed = name(R.string.studio_title, 2)
                val soundTitle = context.getString(R.string.studio_panel_track, context.getString(R.string.studio_sound_caption), renamed)
                onView(withContentDescription(context.getString(R.string.studio_close_panel, soundTitle))).perform(click())
                onView(allOf(withText(renamed), isDisplayed())).check(matches(isSelected()))
                onView(allOf(withText(R.string.studio_pads), tab)).perform(click())
                pads.check { view, error ->
                    if (error != null) throw error
                    assertEquals(listOf(names[0], renamed), (view as PadsView).tiles.map { it.name })
                }
            }
        } finally {
            library.delete(song.id)
        }
    }

    private fun checkThereminRange(low: Int, high: Int) {
        onView(allOf(isAssignableFrom(PadsView::class.java), isDisplayed())).check { view, error ->
            if (error != null) throw error
            val tile = (view as PadsView).tiles.single { it.track == Inst.SYNTH }
            assertEquals(PadPlayMode.THEREMIN, tile.mode)
            assertEquals(low, tile.levels!!.first())
            assertEquals(high, tile.levels.last())
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
