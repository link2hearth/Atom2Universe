package com.Atom2Universe.app.games.papattes

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import com.Atom2Universe.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Les vrais écrans : lancement, marche au doigt, pause, Labo et sauvegarde. Aucune image produite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h820dp-port")
class PapattesActivityTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("papattes_save", Context.MODE_PRIVATE)

    @Before fun clearSave() { prefs.edit().clear().commit() }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun views(activity: PapattesActivity) = descendants(activity.findViewById(android.R.id.content))

    private fun button(activity: PapattesActivity, label: String) = views(activity).filterIsInstance<Button>().first {
        it.visibility == View.VISIBLE && it.text.toString() == label
    }

    private fun click(activity: PapattesActivity, label: Int) = assertTrue(button(activity, activity.getString(label)).performClick())

    private fun touch(view: View, action: Int) {
        val event = MotionEvent.obtain(0, 0, action, 200f, 300f, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    @Test fun walkPauseAndDrawWithoutCrash() {
        val controller = Robolectric.buildActivity(PapattesActivity::class.java).setup()
        try {
            val activity = controller.get()
            click(activity, R.string.papattes_play)
            val view = views(activity).filterIsInstance<PapattesView>().single()
            view.layout(0, 0, 412, 820)
            touch(view, MotionEvent.ACTION_DOWN)
            assertTrue(view.game!!.walker.pressed)
            touch(view, MotionEvent.ACTION_UP)
            // Toutes les couches du dessin passent, prairie comprise.
            view.draw(Canvas(Bitmap.createBitmap(412, 820, Bitmap.Config.ARGB_8888)))
            click(activity, R.string.papattes_pause)
            touch(view, MotionEvent.ACTION_DOWN)
            assertTrue("en pause, le doigt ne fait rien", !view.game!!.walker.pressed)
            click(activity, R.string.papattes_menu)
            click(activity, R.string.papattes_lab)
            // Le Labo et son carnet se construisent avec les vrais sprites.
            assertTrue(views(activity).filterIsInstance<PapattesPortraitView>().count() >= 2)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun backLeavesLabThenGame() {
        val controller = Robolectric.buildActivity(PapattesActivity::class.java).setup()
        try {
            val activity = controller.get()
            click(activity, R.string.papattes_lab)
            // La flèche du haut est le dernier enfant de la racine : rien ne la recouvre.
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
            val chrome = root.getChildAt(root.childCount - 1) as ViewGroup
            val arrow = descendants(chrome).filterIsInstance<ImageButton>().single { it.contentDescription == activity.getString(R.string.papattes_back) }
            assertTrue(arrow.performClick())
            assertTrue("retour au menu", button(activity, activity.getString(R.string.papattes_play)).isShown)
            click(activity, R.string.papattes_back)
            assertTrue(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun afterAFinishTheMenuOffersTheNextStage() {
        val controller = Robolectric.buildActivity(PapattesActivity::class.java).setup()
        try {
            val activity = controller.get()
            click(activity, R.string.papattes_play)
            val view = views(activity).filterIsInstance<PapattesView>().single()
            view.layout(0, 0, 412, 820)
            assertTrue(PapattesRobot(view.game!!).run(120f))
            view.onFinished?.invoke()
            // Détour par le Labo, retour au menu : « C'est parti » lance l'étape 2, pas la 1.
            click(activity, R.string.papattes_lab)
            activity.onBackPressedDispatcher.onBackPressed()
            click(activity, R.string.papattes_play)
            assertEquals(2, view.game!!.course.stage)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun bookFiltersThePenAndTheEggHatches() {
        val p = PapattesProgress()
        for (s in 1..3) p.clearStage(s, PapattesGame.STAR_FINISH)
        p.buyEgg(kotlin.random.Random(4))
        p.clearStage(4, PapattesGame.STAR_FINISH)
        assertTrue(p.eggReady)
        prefs.edit().putString("progress", p.encode()).commit()
        val controller = Robolectric.buildActivity(PapattesActivity::class.java).setup()
        try {
            val activity = controller.get()
            click(activity, R.string.papattes_lab)
            fun portraits() = views(activity).filter { it is PapattesPortraitView && it.isShown }.count()
            assertEquals(1, portraits())
            // La première tête du carnet (le chat de départ) ne garde que les bestioles à tête de chat.
            val thumbs = views(activity).filter { it is android.widget.ImageView && it.parent?.parent is android.widget.HorizontalScrollView }.toList()
            assertTrue(thumbs.first().performClick())
            assertEquals(1, portraits())
            click(activity, R.string.papattes_hatch)
            val hatch = views(activity).filterIsInstance<PapattesHatchView>().single()
            assertTrue(hatch.isShown)
            hatch.layout(0, 0, 412, 700)
            hatch.measure(0, 0)
            val method = View::class.java.getDeclaredMethod("onSizeChanged", Int::class.java, Int::class.java, Int::class.java, Int::class.java)
            method.isAccessible = true
            method.invoke(hatch, 412, 700, 0, 0)
            // On frotte l'œuf jusqu'à ce qu'il craque.
            fun send(action: Int, x: Float, y: Float) {
                val e = MotionEvent.obtain(0, 0, action, x, y, 0); hatch.onTouchEvent(e); e.recycle()
            }
            send(MotionEvent.ACTION_DOWN, 180f, 320f)
            var i = 0
            while (hatch.phase == PapattesHatchView.Phase.RUB && i < 2000) {
                send(MotionEvent.ACTION_MOVE, if (i % 2 == 0) 240f else 170f, 320f + (i % 3) * 10f)
                i++
            }
            send(MotionEvent.ACTION_UP, 200f, 320f)
            assertEquals(PapattesHatchView.Phase.PEEL, hatch.phase)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun everyBiomeDraws() {
        val controller = Robolectric.buildActivity(PapattesActivity::class.java).setup()
        try {
            val view = views(controller.get()).filterIsInstance<PapattesView>().single()
            view.layout(0, 0, 412, 820)
            // Une étape de chaque sorte d'obstacle, avec des pièces des nouvelles planches.
            val wanted = listOf<(PapattesCourse) -> Boolean>({ it.seesaws.isNotEmpty() }, { it.mushrooms.isNotEmpty() },
                { it.winds.isNotEmpty() }, { c -> c.segments.any { it.floater == Floater.PLANK } }, { c -> c.segments.any { it.floater == Floater.LOG } })
            val stages = wanted.map { test -> (1..60).first { test(PapattesCourse(it)) } }
            for ((i, stage) in stages.withIndex()) {
                view.prepare(stage, Critter(Head.entries[(9 + i * 4) % Head.entries.size], Body.entries[9 + i], Legs.entries[5 + i]))
                val game = view.game!!
                // On amène la caméra sur l'obstacle et on fait vivre la physique un instant.
                repeat(30) { game.update(1f / 60f) }
                view.draw(Canvas(Bitmap.createBitmap(412, 820, Bitmap.Config.ARGB_8888)))
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun progressIsSavedAndRestored() {
        val p = PapattesProgress()
        for (s in 1..3) p.clearStage(s, PapattesGame.STAR_FINISH)
        p.buyEgg(kotlin.random.Random(4))
        p.clearStage(4, PapattesGame.STAR_FINISH)
        p.hatch()
        prefs.edit().putString("progress", p.encode()).commit()
        val controller = Robolectric.buildActivity(PapattesActivity::class.java).setup()
        try {
            val activity = controller.get()
            click(activity, R.string.papattes_lab)
            val cards = views(activity).filterIsInstance<PapattesPortraitView>().count()
            // Deux cartes d'enclos (le chat de départ et la bestiole sauvage), plus le portrait du menu.
            assertEquals(3, cards)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
