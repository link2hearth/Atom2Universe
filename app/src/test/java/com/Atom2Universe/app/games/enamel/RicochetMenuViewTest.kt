package com.Atom2Universe.app.games.enamel

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.widget.LinearLayout
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
@Config(sdk = [34], manifest = Config.NONE, application = Application::class,
    shadows = [RicochetTestPalette::class], instrumentedPackages = ["com.Atom2Universe.app.science"])
class RicochetMenuViewTest {
    private fun context(activity: Activity): ContextWrapper {
        val strings = mutableMapOf<Int, String>()
        for (file in listOf("strings_enamel.xml", "strings_ricochet_puzzle.xml")) {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/values-fr/$file")).getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                strings[R.string::class.java.getField(node.attributes.getNamedItem("name").nodeValue).getInt(null)] = node.textContent
            }
        }
        val base = activity.resources
        val resources = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int) = strings[id] ?: super.getString(id)
        }
        return object : ContextWrapper(activity) { override fun getResources() = resources }
    }

    @Test fun bothModesFitOnTabletAndStayReachableOnCompactScreens() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = context(controller.get())
        val dp = context.resources.displayMetrics.density
        for ((w, h) in listOf(600 to 900, 360 to 640, 320 to 400, 900 to 360)) {
            val clicks = mutableListOf<String>()
            val menu = RicochetMenuView(context, { clicks += "classic" }, { clicks += "puzzle" })
            val width = (w * dp).toInt()
            val height = (h * dp).toInt()
            menu.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            menu.layout(0, 0, width, height)
            val cards = menu.getChildAt(0) as LinearLayout
            assertEquals(2, cards.childCount)
            val classic = cards.getChildAt(0)
            val puzzle = cards.getChildAt(1)
            assertTrue("Le décor du classique ne doit pas avaler le menu ($w x $h)", classic.height <= 300 * dp)
            assertTrue("Le défi doit rester dans le contenu défilable ($w x $h)", puzzle.bottom <= cards.height - cards.paddingBottom)
            assertTrue(puzzle.top >= classic.bottom)
            if (h >= 640) assertTrue("Les deux modes doivent tenir ensemble ($w x $h)", puzzle.bottom <= menu.height)
            menu.scrollTo(0, cards.height)
            assertTrue("Le défi doit être entièrement accessible ($w x $h)", puzzle.bottom - menu.scrollY <= menu.height)
            assertTrue(classic.performClick())
            assertTrue(puzzle.performClick())
            assertEquals(listOf("classic", "puzzle"), clicks)
        }
        controller.pause().stop().destroy()
    }
}
