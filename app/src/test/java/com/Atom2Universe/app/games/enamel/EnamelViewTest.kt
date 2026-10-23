package com.Atom2Universe.app.games.enamel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import com.Atom2Universe.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, shadows = [RicochetTestPalette::class],
    instrumentedPackages = ["com.Atom2Universe.app.science"])
class EnamelViewTest {
    // Le projet exécute Robolectric avec des ressources factices : charge les textes réels.
    private fun context(activity: Activity): Context {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings_enamel.xml"))
        val nodes = xml.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            R.string::class.java.getField(node.attributes.getNamedItem("name").nodeValue).getInt(null) to node.textContent
        }
        val base = activity.resources
        val loaded = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int): String = strings[id] ?: super.getString(id)
            override fun getString(id: Int, vararg formatArgs: Any?): String = String.format(getString(id), *formatArgs)
        }
        return object : ContextWrapper(activity) { override fun getResources() = loaded }
    }

    private fun touch(view: EnamelView, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(0, 0, action, x, y, 0).also { view.onTouchEvent(it); it.recycle() }
    }

    private fun metric(view: EnamelView, name: String) = EnamelView::class.java.getDeclaredField(name).run {
        isAccessible = true; getFloat(view)
    }

    private fun touchBoard(view: EnamelView, action: Int, x: Float, y: Float) = touch(view, action,
        metric(view, "left") + x * metric(view, "scale"), metric(view, "top") + y * metric(view, "scale"))

    @Test fun pullLaunchesOnceWhileCancelAndPauseDoNotLaunch() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = EnamelView(context(controller.get()))
        view.layout(0, 0, 400, 900)
        view.resume()
        val y = view.game.launchY
        touchBoard(view, MotionEvent.ACTION_DOWN, 5f, y)
        touchBoard(view, MotionEvent.ACTION_MOVE, 5f, y + 1f)
        touchBoard(view, MotionEvent.ACTION_CANCEL, 5f, y + 1f)
        assertEquals(0, view.game.shots)
        touchBoard(view, MotionEvent.ACTION_DOWN, 5f, y)
        touchBoard(view, MotionEvent.ACTION_MOVE, 5.2f, y + 1f)
        touchBoard(view, MotionEvent.ACTION_UP, 5.2f, y + 1f)
        assertEquals(1, view.game.shots)
        assertTrue(view.game.pucks.single().vy < 0)
        assertTrue(view.game.pucks.single().vx < 0)
        touchBoard(view, MotionEvent.ACTION_DOWN, 5f, y)
        touchBoard(view, MotionEvent.ACTION_MOVE, 5f, y + 1f)
        touchBoard(view, MotionEvent.ACTION_UP, 5f, y + 1f)
        assertEquals(1, view.game.shots)
        view.reset()
        touchBoard(view, MotionEvent.ACTION_DOWN, 5f, y)
        touchBoard(view, MotionEvent.ACTION_MOVE, 5f, y + 1f)
        view.pause()
        touchBoard(view, MotionEvent.ACTION_UP, 5f, y + 1f)
        assertEquals(0, view.game.shots)
        controller.pause().stop().destroy()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun boardDrawsAndLaunchesAfterLandscapeResize() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = EnamelView(context(controller.get()))
        view.game.pucks.addAll(listOf(EnamelPuck(6, 2f, 2.2f), EnamelPuck(2, 5.2f, 1.2f),
            EnamelPuck(3, 7.7f, 2.8f), EnamelPuck(4, 3.8f, 5.5f)))
        view.discoveredTier = 6
        view.layout(0, 0, 600, 900)
        val portrait = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(portrait))
        val output = File("build/outputs/ricochet").apply { mkdirs() }
        File(output, "portrait.png").outputStream().use { portrait.compress(Bitmap.CompressFormat.PNG, 100, it) }
        portrait.recycle()
        view.layout(0, 0, 900, 400)
        view.resume()
        val y = view.game.launchY
        touchBoard(view, MotionEvent.ACTION_DOWN, 5f, y)
        touchBoard(view, MotionEvent.ACTION_MOVE, 5f, y + 1f)
        touchBoard(view, MotionEvent.ACTION_UP, 5f, y + 1f)
        assertEquals(1, view.game.shots)
        val landscape = Bitmap.createBitmap(900, 400, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(landscape))
        File(output, "landscape.png").outputStream().use { landscape.compress(Bitmap.CompressFormat.PNG, 100, it) }
        landscape.recycle()
        view.pause()
        controller.pause().stop().destroy()
    }

    @Test fun portraitUsesTheWidthAndTouchSelectsDepth() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = EnamelView(context(controller.get()))
        view.layout(0, 0, 600, 900)
        assertTrue("Le plateau doit occuper au moins 92 % de la largeur", metric(view, "scale") * 10 > 600 * .92f)
        val ys = mutableListOf<Float>()
        for (depth in floatArrayOf(.9f, 2.1f)) {
            view.reset(); view.resume()
            val y = view.game.baseTop + depth
            touchBoard(view, MotionEvent.ACTION_DOWN, 3f, y)
            touchBoard(view, MotionEvent.ACTION_MOVE, 3f, y + .5f)
            touchBoard(view, MotionEvent.ACTION_UP, 3f, y + .5f)
            assertEquals(1, view.game.shots)
            ys.add(view.game.pucks.single().y)
            assertEquals(y, ys.last(), .001f)
        }
        assertEquals(1.2f, ys[1] - ys[0], .001f)
        view.pause()
        controller.pause().stop().destroy()
    }
}
