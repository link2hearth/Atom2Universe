package com.Atom2Universe.app.games.golf

import android.content.Context
import android.graphics.Rect
import android.view.View
import com.Atom2Universe.app.games.golf.classic.ClassicResultView
import com.Atom2Universe.app.games.golf.classic.GolfIcon
import com.Atom2Universe.app.science.SciencePalette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ClassicResultLayoutTest.LayoutPalette::class],
    instrumentedPackages = ["com.Atom2Universe.app.science"])
class ClassicResultLayoutTest {
    // This project does not package app resources in JVM tests. Colours are irrelevant
    // to measurement: skip palette resolution while retaining the actual Android views.
    @Implements(SciencePalette::class)
    class LayoutPalette {
        @Implementation fun __constructor__(context: Context) = Unit
    }

    @Test fun nextHoleReplayAndMenuStayInsideTheScreen() = checkLayout(finished = false)

    @Test fun finalScorecardAndReplayStayInsideTheScreen() = checkLayout(finished = true)

    private fun checkLayout(finished: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        val ui = GolfUi(context)
        for ((width, height) in listOf(320 to 480, 360 to 640, 640 to 360)) {
            val clicked = mutableListOf<String>()
            val next = ui.primary(if (finished) "Scorecard" else "Next hole") {
                clicked += "continue"
            }
            // All GolfIcon glyphs share the same measurement; use an established glyph
            // so this layout regression does not depend on pending replay UI changes.
            val replay = GolfIcon(context, GolfIcon.Kind.CAMERA, "Replay") {
                clicked += "replay"
            }.apply { minimumHeight = ui.dp(48) }
            val buttons = mutableListOf<View>(next, replay)
            if (!finished) buttons += ui.secondary("Menu") { clicked += "menu" }
            val result = ClassicResultView(context, ui, ClassicResultView.Kind.PAR,
                "Par", buttons)
            result.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ui.dp(height), View.MeasureSpec.EXACTLY))
            result.layout(0, 0, result.measuredWidth, result.measuredHeight)
            var previousBottom = 0
            for (button in buttons) {
                val bounds = Rect(0, 0, button.width, button.height)
                result.offsetDescendantRectToMyCoords(button, bounds)
                assertTrue("Clipped action at ${width}x$height: $bounds",
                    bounds.left >= 0 && bounds.top >= 0 && bounds.right <= result.width && bounds.bottom <= result.height)
                assertTrue("Overlapping actions", bounds.top >= previousBottom)
                assertTrue("Accessible touch target", bounds.width() >= ui.dp(48) && bounds.height() >= ui.dp(48))
                previousBottom = bounds.bottom
                assertTrue(button.performClick())
            }
            assertTrue("Replay must not fill the result panel", replay.height <= ui.dp(56))
            assertEquals(if (finished) listOf("continue", "replay") else listOf("continue", "replay", "menu"), clicked)
        }
    }
}
