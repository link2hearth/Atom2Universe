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
import com.Atom2Universe.app.games.glutton.GluttonGame.Mode
import com.Atom2Universe.app.hub.HubTileArtworks
import java.io.File
import java.time.Duration
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
        val wanted = activity.getString(text)
        assertTrue(views(activity).filterIsInstance<Button>().first { it.text.startsWith(wanted) && it.visibility == View.VISIBLE }.performClick())
    }
    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        view.onTouchEvent(event); event.recycle()
    }
    private fun frames(view: GluttonView, count: Int) {
        val runnable = view.javaClass.getDeclaredField("frame").apply { isAccessible = true }.get(view) as Runnable
        repeat(count) { ShadowSystemClock.advanceBy(Duration.ofMillis(16)); runnable.run() }
    }
    private fun teleport(game: GluttonGame, x: Double, y: Double, radius: Double) {
        game.forceRadius(radius); game.teleport(x, y)
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

    @Test fun replayAndRepeatedPausesDoNotPayTheSameSizeTwice() {
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            view.game.forceRadius(2.0)
            val expected = NeutrinoRewards.glutton(view.game.diameterMetres)
            assertEquals(13, expected)
            controller.pause().resume(); click(activity, R.string.glutton_resume)
            controller.pause().resume()
            assertEquals(expected, context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).getInt("balance", 0))
            click(activity, R.string.glutton_again)
            assertEquals(expected, context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE).getInt("balance", 0))
            assertEquals(GluttonGame.START_RADIUS, view.game.radius, 1e-9)
            assertEquals(4f, context.getSharedPreferences("glutton_save", Context.MODE_PRIVATE).getFloat("best_voyage", 0f), .001f)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun finishingDuringBackgroundTransitionShowsResultsInsteadOfAnUnresumablePause() {
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get(); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            view.game.forceRadius(view.game.world.rings.last().to * 1.01)
            var guard = 0
            while (!view.game.finished && guard++ < 4000) view.game.update(.1f)
            assertTrue(view.game.finished)
            activity.onWindowFocusChanged(false)
            val headings = views(activity).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertTrue(headings.any { it == activity.getString(R.string.glutton_end) || it == activity.getString(R.string.glutton_new_record) || it == activity.getString(R.string.glutton_cleared) })
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
            } finally { controller.pause().stop().destroy() }
        }
    }

    @Test fun theHungryClockModeIsRememberedAndKeepsItsOwnRecord() {
        val prefs = context.getSharedPreferences("glutton_save", Context.MODE_PRIVATE)
        prefs.edit().putFloat("best_voyage", 12.5f).commit()
        val controller = Robolectric.buildActivity(GluttonActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(views(activity).filterIsInstance<Button>().any { it.text.contains("12.5") || it.text.contains("12,5") })
            click(activity, R.string.glutton_mode_fringale); click(activity, R.string.glutton_play)
            val view = views(activity).filterIsInstance<GluttonView>().single()
            assertEquals(Mode.FRINGALE, view.game.mode); assertEquals(1, prefs.getInt("mode", 0))
            assertTrue(view.game.items.any { it.prop.bonus })
            view.game.forceRadius(.5)
            activity.onWindowFocusChanged(false)
            assertEquals(1f, prefs.getFloat("best_fringale", 0f), .001f)
            assertEquals(12.5f, prefs.getFloat("best_voyage", 0f), .001f)
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
            // The pages and textures decode on their own thread.
            Thread.sleep(2500)
            frames(view, 30); render("start_textured", 412, 820)
            teleport(view.game, 1.5, -1.2, .1); frames(view, 90); render("house_grown", 412, 820)
            teleport(view.game, 0.0, -5.0, .4); frames(view, 90); render("house_big", 412, 820)
            teleport(view.game, 30.0, 22.0, 1.0); frames(view, 90); render("suburb", 412, 820)
            teleport(view.game, -45.0, 40.0, 4.0); frames(view, 90); render("suburb_big", 412, 820)
            teleport(view.game, 300.0, 250.0, 15.0); frames(view, 90); Thread.sleep(1500); render("city", 412, 820)
            teleport(view.game, -700.0, 500.0, 90.0); frames(view, 90); render("city_giant", 412, 820)
            teleport(view.game, 4000.0, 2500.0, 300.0); frames(view, 90); Thread.sleep(1500); render("country", 412, 820)
            teleport(view.game, 60e3, -40e3, 5000.0); frames(view, 90); Thread.sleep(1500); render("mountains", 412, 820)
            teleport(view.game, 1.2e6, 8e5, 1.5e5); frames(view, 90); Thread.sleep(1500); render("earth", 412, 820)
            teleport(view.game, 1.5e7, 0.0, 2e6); frames(view, 90); Thread.sleep(1500); render("planets", 412, 820)
            teleport(view.game, -1.6e9, 4e8, 6e7); frames(view, 90); render("sun", 412, 820)
            teleport(view.game, 5e10, 3e10, 2e9); frames(view, 90); Thread.sleep(1500); render("stars", 412, 820)
            teleport(view.game, 3e14, 1e14, 1e13); frames(view, 90); render("nebulae", 412, 820)
            teleport(view.game, 1e20, 4e19, 4e18); frames(view, 90); Thread.sleep(1500); render("galaxy", 412, 820)
            teleport(view.game, 5e23, 2e23, 3e22); frames(view, 90); Thread.sleep(1500); render("cosmos", 412, 820)
            render("compact", 320, 568)
            render("landscape", 820, 412)
        } finally { controller.pause().stop().destroy() }
    }
}
