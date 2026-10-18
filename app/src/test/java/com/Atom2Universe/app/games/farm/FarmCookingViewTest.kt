package com.Atom2Universe.app.games.farm

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FarmCookingViewTest {
    /** This project runs Robolectric without packaged app resources: load the actual EN strings. */
    private fun kitchenContext(activity: Activity): Context {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.File("src/main/res/values/strings_farm.xml"))
        val nodes = document.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            com.Atom2Universe.app.R.string::class.java.getField(node.attributes.getNamedItem("name").nodeValue).getInt(null) to node.textContent
        }
        val base = activity.resources
        val loaded = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int): String = strings[id] ?: super.getString(id)
            override fun getString(id: Int, vararg formatArgs: Any?): String = String.format(getString(id), *formatArgs)
        }
        return object : ContextWrapper(activity) { override fun getResources(): Resources = loaded }
    }
    private fun touch(view: FarmCookingView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x * view.width, y * view.height, 0)
        view.dispatchTouchEvent(event); event.recycle()
    }
    @Test fun `swipes sur planche et alternative accessible progressent sans double decoupe`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val session = FarmCookingSession(FarmRecipe.SOUP)
        val view = FarmCookingView(kitchenContext(activity), session)
        val parent = FrameLayout(activity); parent.addView(view); activity.setContentView(parent)
        view.layout(0, 0, 400, 200)
        touch(view, MotionEvent.ACTION_DOWN, .2f, .2f)
        touch(view, MotionEvent.ACTION_UP, .2f, .8f)
        assertEquals(.25f, session.progress, .0001f)
        touch(view, MotionEvent.ACTION_DOWN, .2f, .2f)
        touch(view, MotionEvent.ACTION_CANCEL, .2f, .8f)
        assertEquals(.25f, session.progress, .0001f)
        repeat(3) { view.performClick() }
        assertEquals(FarmCookingStep.STIR, session.step)
        repeat(8) { view.performClick() }
        assertTrue(session.complete)
        assertTrue(view.contentDescription.isNotBlank())
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); bitmap.recycle()
        controller.pause().stop().destroy()
    }
    @Test fun `drag sandwich suit les couches et rend la main apres fin`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val session = FarmCookingSession(FarmRecipe.CHEESE_SANDWICH)
        val view = FarmCookingView(kitchenContext(activity), session)
        val parent = FrameLayout(activity); parent.addView(view); activity.setContentView(parent)
        view.layout(0, 0, 400, 200)
        for (i in 0..3) {
            touch(view, MotionEvent.ACTION_DOWN, .14f + i * .24f, .24f)
            touch(view, MotionEvent.ACTION_MOVE, .5f, .73f)
            touch(view, MotionEvent.ACTION_UP, .5f, .73f)
        }
        assertTrue(session.complete)
        view.performClick(); assertTrue(session.complete)
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); bitmap.recycle()
        controller.pause().stop().destroy()
    }
}
