package com.Atom2Universe.app.games.glutton

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.GamesCatalog
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Scene
import com.Atom2Universe.app.hub.HubTileArtworks
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h820dp-port")
class GluttonActivityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() {
        LocaleHelper.setLanguage(context, "en")
        context.getSharedPreferences("glutton_save", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun views(activity: GluttonActivity) = descendants(activity.findViewById(android.R.id.content))
    private fun click(activity: GluttonActivity, text: Int) {
        assertTrue(views(activity).filterIsInstance<Button>().first { it.text == activity.getString(text) && it.visibility == View.VISIBLE }.performClick())
    }
    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        view.onTouchEvent(event); event.recycle()
    }
    private fun grow(view: GluttonView, target: Float) {
        val g = view.game
        repeat(400) {
            if (g.radius >= target) return
            val prop = GluttonCatalog.forScene(g.scene).filter { !it.moves && !it.goal && !it.treasure && it.radius <= g.radius * GluttonGame.EDIBLE }.maxBy { it.radius }
            g.items.removeAll { !it.moves }
            repeat(6) { g.items += GluttonGame.Item(9000 + it, prop, g.x, g.y, 0f) }
            g.update(1f / 60); view.onHudChanged?.invoke()
        }
        fail("Could not reach radius $target")
    }
    /** Grows to landmark size and parks the landmark in the mouth; the next frames swallow it, as in the app. */
    private fun completeScene(view: GluttonView) {
        grow(view, view.game.targetRadius)
        view.game.items.clear(); view.game.items += view.game.goal
        view.game.goal.x = view.game.x; view.game.goal.y = view.game.y
    }
    /** Drives the frame loop (with a ticking clock) through the swallow and the scene-change animation. */
    private fun settleTransition(view: GluttonView) = frames(view, 150)
    private fun frames(view: GluttonView, count: Int) {
        val runnable = view.javaClass.getDeclaredField("frame").apply { isAccessible = true }.get(view) as Runnable
        repeat(count) { ShadowSystemClock.advanceBy(Duration.ofMillis(16)); runnable.run() }
    }

    @Test fun cancelledInputAndBackgroundingStopMovementUntilExplicitResume() {
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            view.layout(0, 0, 412, 820)
            touch(view, MotionEvent.ACTION_DOWN, 150f, 400f)
            touch(view, MotionEvent.ACTION_MOVE, 200f, 400f)
            assertTrue(view.hasInput)
            touch(view, MotionEvent.ACTION_CANCEL, 200f, 400f)
            assertFalse(view.hasInput)
            touch(view, MotionEvent.ACTION_DOWN, 150f, 400f)
            click(activity, R.string.glutton_pause)
            assertFalse(view.isRunning); assertFalse(view.hasInput)
            click(activity, R.string.glutton_resume); assertTrue(view.isRunning)
            controller.pause()
            assertFalse(view.isRunning); assertFalse(view.hasInput)
            controller.resume()
            assertFalse(view.isRunning)
            click(activity, R.string.glutton_resume); assertTrue(view.isRunning)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun replayAndRepeatedPausesDoNotPayTheSamePointsTwice() {
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            grow(view, 80f)
            val expected = NeutrinoRewards.glutton(view.game.score)
            assertTrue(expected > 0)
            controller.pause().resume(); click(activity, R.string.glutton_resume)
            controller.pause().resume()
            assertEquals(expected, context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).getInt("balance", 0))
            click(activity, R.string.glutton_again)
            assertEquals(expected, context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).getInt("balance", 0))
            assertEquals(0, view.game.score)
            assertTrue(context.getSharedPreferences("glutton_save", Context.MODE_PRIVATE).getInt("best", 0) > 0)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun finishingDuringBackgroundTransitionShowsResultsInsteadOfAnUnresumablePause() {
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            var guard = 0
            while (!view.game.finished && guard++ < 4000) view.game.update(.1f)
            assertTrue(view.game.finished)
            activity.onWindowFocusChanged(false)
            assertTrue(views(activity).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.glutton_time_up) })
            click(activity, R.string.glutton_again)
            assertFalse(view.game.finished); assertTrue(view.isRunning)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun tileHasArtworkAndLivesInTheGamesPane() {
        val tile = GamesCatalog.tiles.single { it.id == "glutton" }
        assertEquals(GluttonActivity::class.java, tile.activityClass)
        assertEquals(GluttonHubTileDrawable::class, HubTileArtworks.forActivity(GluttonActivity::class.java.name))
        assertTrue(tile.id in GamesCatalog.bigGameIds)
    }

    @Test fun everyCatalogueEntryHasANameInBothLanguages() {
        for (language in listOf("en", "fr")) {
            LocaleHelper.setLanguage(context, language)
            val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
            try {
                val activity = controller.get()
                for (prop in GluttonCatalog.props) {
                    val id = activity.resources.getIdentifier("glutton_item_${prop.id}", "string", activity.packageName)
                    assertTrue("$language: missing glutton_item_${prop.id}", id != 0)
                    assertTrue(activity.name(prop).isNotBlank())
                }
                assertTrue(GluttonActivity.STREAKS.keys.containsAll(GluttonCatalog.props.map { it.family }.filter { it != GluttonCatalog.Family.TREASURE }))
            } finally { controller.pause().stop().destroy() }
        }
    }

    @Test fun landmarksChainScenesWithoutAMenuAndUnlockThem() {
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            view.layout(0, 0, 412, 820)
            completeScene(view)
            settleTransition(view)
            val prefs = context.getSharedPreferences("glutton_save", Context.MODE_PRIVATE)
            assertEquals(1, prefs.getInt("unlocked", 0)); assertTrue(prefs.getInt("stars_0", 0) > 0)
            assertTrue(view.isRunning); assertEquals(1, view.game.sceneIndex); assertFalse(view.inTransition)
            completeScene(view); settleTransition(view)
            assertEquals(2, prefs.getInt("unlocked", 0)); assertEquals(2, view.game.sceneIndex)
            completeScene(view); settleTransition(view)
            assertTrue(view.game.won)
            assertTrue(views(activity).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.glutton_victory) })
            val balance = context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).getInt("balance", 0)
            assertTrue(balance > 0)
            controller.pause().resume()
            assertEquals(balance, context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).getInt("balance", 0))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun anUnlockedAreaCanBeSelectedDirectlyWithoutResettingEarnedStars() {
        val prefs = context.getSharedPreferences("glutton_save", Context.MODE_PRIVATE)
        prefs.edit().putInt("unlocked", 2).putInt("stars_0", 3).commit()
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_zone_city); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            assertEquals(2, view.game.sceneIndex); assertEquals(Scene.CITY, view.game.scene)
            assertEquals(Scene.CITY.startMetres, view.game.diameterMetres, .01f)
            assertEquals(3, prefs.getInt("stars_0", 0))
        } finally { controller.pause().stop().destroy() }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "fr-rFR-w412dp-h820dp-port")
    fun menusAndGameplayRenderWithRoomForTheControls() {
        LocaleHelper.setLanguage(context, "fr")
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val out = File("build/outputs/glutton").apply { mkdirs() }
            fun render(name: String, width: Int, height: Int) {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val colors = HashSet<Int>()
                for (x in 1..12) for (y in 1..16) colors += bitmap.getPixel(x * width / 13, y * height / 17)
                assertTrue("Canvas should contain diverse artwork", colors.size >= 8)
                bitmap.recycle()
            }
            render("menu", 412, 820)
            click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            root.layout(0, 0, 412, 820); frames(view, 30)
            render("start", 412, 820)
            grow(view, 70f); frames(view, 90); render("grown", 412, 820)
            grow(view, view.game.targetRadius * .95f); frames(view, 90); render("room_big", 412, 820)
            completeScene(view); settleTransition(view); render("garden", 412, 820)
            grow(view, 90f); frames(view, 90); render("garden_grown", 412, 820)
            completeScene(view); settleTransition(view); render("city", 412, 820)
            grow(view, view.game.targetRadius * .95f); frames(view, 90); render("city_giant", 412, 820)
            render("compact", 320, 568)
            render("landscape", 820, 412)
        } finally { controller.pause().stop().destroy() }
    }
}
