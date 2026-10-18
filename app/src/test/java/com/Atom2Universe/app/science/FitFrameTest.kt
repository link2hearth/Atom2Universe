package com.Atom2Universe.app.science

import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

/** Le dessin d'une fiche reste entier : il suit la largeur, ou s'inscrit dans la hauteur qu'on lui impose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FitFrameTest {
    private fun build(): Pair<FitFrame, View> {
        val context = RuntimeEnvironment.getApplication()
        val child = View(context)
        val frame = FitFrame(context, 1.1f, 480).apply { addView(child, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }
        return frame to child
    }

    @Test fun `sans hauteur imposee le dessin suit la largeur et ne depasse pas le plafond`() {
        val (frame, child) = build()
        frame.measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        assertEquals(480, child.measuredWidth)
        assertEquals(528, child.measuredHeight)
        assertEquals(1000, frame.measuredWidth)
        assertEquals(528, frame.measuredHeight)

        frame.measure(MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        assertEquals(300, child.measuredWidth)
        assertEquals(330, child.measuredHeight)
    }

    @Test fun `avec une hauteur imposee le dessin s'inscrit entier et se centre`() {
        val (frame, child) = build()
        frame.measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY))
        assertEquals(400, child.measuredHeight)
        assertTrue(child.measuredWidth <= 480)
        assertEquals(400 / 1.1f, child.measuredWidth.toFloat(), 1f)
        assertEquals(600, frame.measuredWidth)
        assertEquals(400, frame.measuredHeight)
        frame.layout(0, 0, 600, 400)
        assertTrue("centré à l'horizontale", abs(child.left - (600 - child.measuredWidth) / 2) <= 1)
    }

    @Test fun `une hauteur seulement plafonnee suffit a faire tenir le dessin`() {
        val (frame, child) = build()
        frame.measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(400, MeasureSpec.AT_MOST))
        assertEquals(400, child.measuredHeight)
        assertEquals(400, frame.measuredHeight)
    }

    @Test fun `une place plus haute que le dessin ne l'etire pas`() {
        val (frame, child) = build()
        frame.measure(MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(900, MeasureSpec.EXACTLY))
        assertEquals(300, child.measuredWidth)
        assertEquals(330, child.measuredHeight)
        assertEquals(900, frame.measuredHeight)
    }
}
