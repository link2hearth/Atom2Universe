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
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EnamelViewTest {
    // Le projet exécute Robolectric avec des ressources factices : charge les textes réels.
    private fun context(activity: Activity): Context {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings_enamel.xml"))
        val nodes = xml.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            R.string::class.java.getField(node.attributes.getNamedItem("name").nodeValue).getInt(null) to node.textContent
        }
        val marks = xml.getElementsByTagName("item").let { items -> Array(items.length) { items.item(it).textContent } }
        val base = activity.resources
        val loaded = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int): String = strings[id] ?: super.getString(id)
            override fun getString(id: Int, vararg formatArgs: Any?): String = String.format(getString(id), *formatArgs)
            override fun getStringArray(id: Int): Array<String> = if (id == R.array.enamel_marks) marks else super.getStringArray(id)
        }
        return object : ContextWrapper(activity) { override fun getResources() = loaded }
    }

    private fun touch(view: EnamelView, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(0, 0, action, x, y, 0).also { view.onTouchEvent(it); it.recycle() }
    }

    @Test fun pullLaunchesOnceWhileCancelAndPauseDoNotLaunch() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = EnamelView(context(controller.get()))
        view.layout(0, 0, 400, 900)
        view.resume()
        touch(view, MotionEvent.ACTION_DOWN, 200f, 630f)
        touch(view, MotionEvent.ACTION_MOVE, 200f, 705f)
        touch(view, MotionEvent.ACTION_CANCEL, 200f, 705f)
        assertEquals(0, view.game.shots)
        touch(view, MotionEvent.ACTION_DOWN, 200f, 630f)
        touch(view, MotionEvent.ACTION_MOVE, 210f, 705f)
        touch(view, MotionEvent.ACTION_UP, 210f, 705f)
        assertEquals(1, view.game.shots)
        assertTrue(view.game.pucks.single().vy < 0)
        assertTrue(view.game.pucks.single().vx < 0)
        touch(view, MotionEvent.ACTION_DOWN, 200f, 630f)
        touch(view, MotionEvent.ACTION_MOVE, 200f, 705f)
        touch(view, MotionEvent.ACTION_UP, 200f, 705f)
        assertEquals(1, view.game.shots)
        view.reset()
        touch(view, MotionEvent.ACTION_DOWN, 200f, 630f)
        touch(view, MotionEvent.ACTION_MOVE, 200f, 705f)
        view.pause()
        touch(view, MotionEvent.ACTION_UP, 200f, 705f)
        assertEquals(0, view.game.shots)
        controller.pause().stop().destroy()
    }

    @Test fun boardDrawsAndLaunchesAfterLandscapeResize() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = EnamelView(context(controller.get()))
        view.layout(0, 0, 400, 900)
        val portrait = Bitmap.createBitmap(400, 900, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(portrait)); portrait.recycle()
        view.layout(0, 0, 900, 400)
        view.resume()
        touch(view, MotionEvent.ACTION_DOWN, 450f, 270f)
        touch(view, MotionEvent.ACTION_MOVE, 450f, 320f)
        touch(view, MotionEvent.ACTION_UP, 450f, 320f)
        assertEquals(1, view.game.shots)
        val landscape = Bitmap.createBitmap(900, 400, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(landscape)); landscape.recycle()
        view.pause()
        controller.pause().stop().destroy()
    }
}
