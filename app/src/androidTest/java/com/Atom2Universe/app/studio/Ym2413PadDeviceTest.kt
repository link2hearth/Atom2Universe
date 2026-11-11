package com.Atom2Universe.app.studio

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.SynthBank
import java.io.File
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Ym2413PadDeviceTest {
    @Test fun chooseFmAndEditThePatchFromTheNotesKeyboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = StudioLibrary(File(context.filesDir, "studio/songs"))
        val song = library.create("YM2413 UI test", StudioProject.blank())
        try {
            val intent = Intent(context, StudioActivity::class.java).putExtra(EXTRA_PROJECT_ID, song.id)
            ActivityScenario.launch<StudioActivity>(intent).use {
                onView(allOf(withText(R.string.studio_tab_notes), isDisplayed())).perform(click())
                val prefix = context.getString(R.string.studio_sound_label, "")
                onView(allOf(withContentDescription(startsWith(prefix)), isDisplayed())).perform(click())
                onView(allOf(withText(R.string.studio_bank_fm), isDisplayed())).perform(click())
                repeat(3) { onView(withContentDescription(R.string.studio_program_previous)).perform(click()) }
                onView(withContentDescription(R.string.studio_fm_pad)).perform(swipeUp())
            }
            val updated = library.load(song.id)!!.project.track(Inst.SYNTH)
            assertEquals(SynthBank.FM, updated.synthBank)
            assertEquals(0, updated.fmProgram)
            assertTrue(updated.fmAttack > .75f)
        } finally {
            library.delete(song.id)
        }
    }
}
