package com.Atom2Universe.app.games.golf.classic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.golf.GolfUi
import kotlin.math.*

/**
 * The ball on its lie, in two small pictures: from the side (uphill or downhill towards the
 * target, marked by a flag) and from behind (higher on the right or on the left), each with its slope.
 */
internal class ClassicLieView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var along = 0f
    private var side = 0f
    private var alongLabel = ""
    private var sideLabel = ""

    /** Slopes as rise per metre; the picture and the percentages only change with the rounded value. */
    fun set(along: Float, side: Float) {
        val a = (along * 100f).roundToInt(); val s = (side * 100f).roundToInt()
        if (a == (this.along * 100f).roundToInt() && s == (this.side * 100f).roundToInt() && alongLabel.isNotEmpty()) return
        this.along = a / 100f; this.side = s / 100f
        alongLabel = context.getString(R.string.classic_slope_percent, a)
        sideLabel = context.getString(R.string.classic_slope_percent, s)
        contentDescription = context.getString(R.string.classic_lie_slopes, alongLabel, sideLabel)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val half = width / 2f
        p.style = Paint.Style.FILL
        p.color = GolfUi.SCENE_PLATE
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14f * d, 14f * d, p)
        panel(c, half * .5f, half, along, true)
        panel(c, half * 1.5f, half, side, false)
    }

    /** One picture centred on [cx]: a ground line tilted by [slope] (exaggerated to read), the ball on it and the percentage. */
    private fun panel(c: Canvas, cx: Float, room: Float, slope: Float, profile: Boolean) {
        val cy = height * .42f
        val degrees = Math.toDegrees(atan(slope).toDouble()).toFloat() * 2.5f
        val tilt = degrees.coerceIn(-40f, 40f)
        val colour = when { abs(slope) < .03f -> 0xFF9FD8A0.toInt(); abs(slope) < .10f -> 0xFFFFD778.toInt(); else -> 0xFFFF8A5C.toInt() }
        val reach = room * .38f
        c.save(); c.rotate(-tilt, cx, cy)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.strokeCap = Paint.Cap.ROUND
        p.color = colour
        c.drawLine(cx - reach, cy, cx + reach, cy, p)
        if (profile) {
            // The target stands at the high end of a rising line.
            p.strokeWidth = 1.6f * d
            c.drawLine(cx + reach, cy, cx + reach, cy - 11f * d, p)
            p.style = Paint.Style.FILL
            c.drawRect(cx + reach, cy - 11f * d, cx + reach + 6f * d, cy - 7f * d, p)
        }
        p.style = Paint.Style.FILL; p.color = 0xFFF6F3E8.toInt()
        c.drawCircle(cx, cy - 5f * d, 4.6f * d, p)
        c.restore()
        p.color = colour
        p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER; p.textSize = 12f * d
        c.drawText(if (profile) alongLabel else sideLabel, cx, height - 7f * d, p)
        p.typeface = Typeface.DEFAULT
    }
}
