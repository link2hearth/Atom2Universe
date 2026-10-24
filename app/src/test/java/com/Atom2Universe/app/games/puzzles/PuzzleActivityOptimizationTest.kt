package com.Atom2Universe.app.games.puzzles

import android.app.Application
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Un vrai écran thémé, avec une recherche volontairement bloquée pour exercer les courses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PuzzleActivityOptimizationTest {
    @Test fun leChronoRepartPourUnePartieRestaureeEtSArreteEnPause() {
        val controller = Robolectric.buildActivity(SlowHintActivity::class.java).setup()
        val clock = PuzzleActivity::class.java.getDeclaredField("runningSince").apply { isAccessible = true }
        try {
            assertTrue(clock.getLong(controller.get()) > 0L)
            controller.pause()
            assertEquals(0L, clock.getLong(controller.get()))
            controller.resume()
            assertTrue(clock.getLong(controller.get()) > 0L)
        } finally { controller.pause().stop().destroy() }
    }

    @Before fun savedGame() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kit_optimization_test", 0).edit().clear()
            .putString("initial", "0").putString("current", "0").commit()
        NeutrinoRepository(context).setBalance(1000)
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun hint(activity: SlowHintActivity) {
        val text = activity.getString(R.string.kit_hint, NeutrinoRewards.HINT_COST)
        descendants(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.started.await(5, TimeUnit.SECONDS))
    }

    private fun drainUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue(condition())
    }

    @Test fun indiceCalculeHorsInterfaceEtPayeSeulementQuandIlEstApplique() {
        val controller = Robolectric.buildActivity(SlowHintActivity::class.java).setup()
        val activity = controller.get()
        try {
            hint(activity)
            assertFalse(activity.calculatedOnMain)
            assertFalse(activity.acceptsInput)
            assertEquals(1000, NeutrinoRepository(activity).getBalance())
            var interfaceResponded = false
            activity.lifecycleScope.launch { interfaceResponded = true }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(interfaceResponded)
            activity.release.countDown()
            drainUntil { activity.displayed == 1 }
            assertTrue(activity.acceptsInput)
            assertEquals(1000 - NeutrinoRewards.HINT_COST, NeutrinoRepository(activity).getBalance())
        } finally {
            activity.release.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test fun pauseAnnuleLaRechercheSansDebitNiCoupTardif() {
        val controller = Robolectric.buildActivity(SlowHintActivity::class.java).setup()
        val activity = controller.get()
        try {
            hint(activity)
            controller.pause()
            assertTrue(activity.finished.await(5, TimeUnit.SECONDS))
            activity.release.countDown()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, activity.displayed)
            assertEquals(1000, NeutrinoRepository(activity).getBalance())
        } finally {
            activity.release.countDown()
            controller.stop().destroy()
        }
    }

    @Test fun nouvellePartieAnnuleLIndiceDeLAncienneGrille() {
        val controller = Robolectric.buildActivity(SlowHintActivity::class.java).setup()
        val activity = controller.get()
        try {
            hint(activity)
            activity.newPuzzle()
            assertTrue(activity.finished.await(5, TimeUnit.SECONDS))
            activity.release.countDown()
            drainUntil { activity.displayed == 50 }
            assertTrue(activity.acceptsInput)
            assertEquals(1000, NeutrinoRepository(activity).getBalance())
        } finally {
            activity.release.countDown()
            controller.pause().stop().destroy()
        }
    }
}

class SlowHintActivity : PuzzleActivity<Int>() {
    override val gameKey = "optimization_test"
    override val titleRes = R.string.fifteen_title
    override val rulesRes = R.string.fifteen_rules
    override val presets = listOf(Preset(R.string.kit_size, 3, 3))
    override val timed = true
    override val hasHints = true
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val finished = CountDownLatch(1)
    @Volatile var calculatedOnMain = true
    val displayed get() = current
    val acceptsInput get() = board.inputEnabled
    fun newPuzzle() = newGame()
    override fun createBoard() = object : PuzzleView<Int>(this) {
        override fun drawBoard(canvas: Canvas, state: Int, area: RectF) = Unit
    }
    override fun generate(preset: Int, random: Random) = 50
    override fun encode(state: Int) = state.toString()
    override fun decode(text: String) = text.toIntOrNull()
    override fun isSolved(state: Int) = false
    override fun hint(state: Int): Int {
        calculatedOnMain = Looper.myLooper() == Looper.getMainLooper()
        started.countDown()
        try {
            check(release.await(5, TimeUnit.SECONDS))
            return state + 1
        } finally { finished.countDown() }
    }
}
