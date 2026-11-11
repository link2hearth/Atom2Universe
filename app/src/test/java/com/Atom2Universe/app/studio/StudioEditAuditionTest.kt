package com.Atom2Universe.app.studio

import android.app.Application
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.descendants
import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.ui.KeyboardView
import com.Atom2Universe.app.studio.ui.PadsView
import com.Atom2Universe.app.studio.ui.PianoRollView
import com.Atom2Universe.app.studio.ui.StepGridView
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StudioEditAuditionTest {
    @Test fun gridEditsAndStepControlsAreSilentDuringPlaybackButTheKeyboardStillPlays() {
        withEditor { activity, root, engine ->
            val grid = root.descendants.filterIsInstance<StepGridView>().single().listener!!
            val roll = root.descendants.filterIsInstance<PianoRollView>().single().listener!!
            val play = root.descendants.filterIsInstance<Button>().first { it.text == activity.getString(R.string.studio_play) }
            play.performClick()
            // Le pas courant est vide ; ces notes ne doivent s'entendre qu'à la fin de la mesure.
            grid.onStepSet(0, 15, true)
            grid.onPreview(0, 15)
            grid.onStepSet(Inst.SYNTH, 14, true)
            grid.onPreview(Inst.SYNTH, 14)
            roll.onAddNote(12, 65, 2)
            roll.onPreview(65)
            roll.onEditNote(Note(12, 65, 2), 100f, 100f)
            val card = root.findViewWithTag<ViewGroup>(R.string.studio_step_force)
            val pitchRow = card.descendants.filterIsInstance<TextView>()
                .first { it.text == activity.getString(R.string.studio_drum_tune) }.parent as ViewGroup
            pitchRow.descendants.filterIsInstance<Button>().last().performClick()
            assertFalse("Editing added an unscheduled sound", audible(engine))
            // La note enregistrée au pas 12 doit ensuite sonner avec le transport.
            var heardStep = false
            repeat(300) {
                val sound = audible(engine)
                if (engine.currentStep == 12 && sound) heardStep = true
            }
            assertTrue("The transport must play the written note", heardStep)

            root.descendants.filterIsInstance<KeyboardView>().single().listener!!.onKeyDown(67, 1f)
            assertTrue("The performance keyboard must still play", audible(engine))
        }
    }

    @Test fun theStoppedGridAndThePlayingDrumPadCanBothBeHeard() {
        withEditor { _, root, engine ->
            val roll = root.descendants.filterIsInstance<PianoRollView>().single().listener!!
            roll.onPreview(65)
            assertTrue("Stopped notes should audition", audible(engine))
        }
        withEditor { activity, root, engine ->
            root.descendants.filterIsInstance<Button>().first { it.text == activity.getString(R.string.studio_play) }.performClick()
            root.descendants.filterIsInstance<PadsView>().single().onDown!!.invoke(Inst.KICK)
            assertTrue("The live drum pad must still play", audible(engine))
        }
    }

    private fun audible(engine: StudioEngine): Boolean {
        val left = FloatArray(512)
        engine.render(left, FloatArray(left.size), left.size)
        return left.any { abs(it) > 0.00001f }
    }

    private fun withEditor(test: (StudioActivity, ViewGroup, StudioEngine) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val library = StudioLibrary(File(context.filesDir, "studio/songs"))
        val song = library.create("Edit audition", StudioProject.blank())
        val intent = Intent(context, StudioActivity::class.java).putExtra(EXTRA_PROJECT_ID, song.id)
        // onCreate relie les vraies vues. Sans onResume, le test rend lui-même chaque bloc audio.
        val controller = Robolectric.buildActivity(StudioActivity::class.java, intent).create()
        try {
            val activity = controller.get()
            test(activity, activity.findViewById(android.R.id.content), ReflectionHelpers.getField(activity, "engine"))
        } finally {
            controller.destroy()
            library.delete(song.id)
        }
    }
}
