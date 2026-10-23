package com.Atom2Universe.app.games.enamel

import android.app.Activity
import android.app.Application
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
@Config(sdk = [34], manifest = Config.NONE, application = Application::class,
    shadows = [RicochetTestPalette::class], instrumentedPackages = ["com.Atom2Universe.app.science"])
class RicochetPuzzleViewTest {
    private fun context(activity: Activity): ContextWrapper {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings_ricochet_puzzle.xml")).getElementsByTagName("string")
        val strings = (0 until nodes.length).associate {
            val n = nodes.item(it)
            R.string::class.java.getField(n.attributes.getNamedItem("name").nodeValue).getInt(null) to n.textContent
        }
        val base = activity.resources
        val resources = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int) = strings[id] ?: super.getString(id)
        }
        return object : ContextWrapper(activity) { override fun getResources() = resources }
    }

    private fun metric(v: RicochetPuzzleView, name: String) = RicochetPuzzleView::class.java.getDeclaredField(name).run {
        isAccessible = true; getFloat(v)
    }

    private fun touch(v: RicochetPuzzleView, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(0, 0, action, metric(v, "left") + x * metric(v, "scale"),
            metric(v, "top") + y * metric(v, "scale"), 0).also { v.onTouchEvent(it); it.recycle() }
    }

    @Test fun placementUsesDepthAndCancelOrPauseDoesNotCount() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val v = RicochetPuzzleView(context(controller.get()), RicochetPuzzleGame(42))
        v.layout(0, 0, 600, 900); v.resume()
        touch(v, MotionEvent.ACTION_DOWN, 3f, 12f)
        touch(v, MotionEvent.ACTION_MOVE, 3f, 13f)
        touch(v, MotionEvent.ACTION_CANCEL, 3f, 13f)
        assertEquals(0, v.game.shots)
        for (y in floatArrayOf(11.6f, 13.3f)) {
            v.game.reset(); v.refresh(); v.resume()
            touch(v, MotionEvent.ACTION_DOWN, 3f, y)
            touch(v, MotionEvent.ACTION_UP, 3f, y + .3f)
            assertEquals(1, v.game.shots)
            assertEquals(y, v.game.pucks.last().y, .001f)
            assertTrue(v.game.pucks.last().projectile)
        }
        v.game.reset(); v.refresh()
        touch(v, MotionEvent.ACTION_DOWN, 3f, 12f)
        v.pause()
        touch(v, MotionEvent.ACTION_UP, 3f, 13f)
        assertEquals(0, v.game.shots)
        controller.pause().stop().destroy()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun resizingKeepsSeedGeometryAndBothDifficultiesRender() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val v = RicochetPuzzleView(context(controller.get()), RicochetPuzzleGame(42, true))
        val state = v.game.saveState()
        val output = File("build/outputs/ricochet-puzzle").apply { mkdirs() }
        for ((w, h) in listOf(600 to 900, 900 to 500)) {
            v.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
            assertEquals(state, v.game.saveState())
            File(output, "hard-$w.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        v.game.reset(42, false); v.refresh(); v.layout(0, 0, 600, 900)
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bitmap))
        File(output, "easy.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        controller.pause().stop().destroy()
    }
}
