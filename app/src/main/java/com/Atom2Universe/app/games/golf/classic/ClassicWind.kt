package com.Atom2Universe.app.games.golf.classic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.golf.GolfUi
import java.util.Random
import kotlin.math.*

/**
 * Wind compass in the top-left corner. The top of the rose is the direction of the shot: the arrow
 * shows where the wind pushes the ball, so an arrow pointing up is a tailwind, down a headwind.
 */
internal class ClassicWindRose(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    /** Clockwise from the top of the screen, radians. */
    private var angle = 0f
    private var speed = 0f
    private var label = ""

    /** [aim] is the heading of the shot; the wind blows towards ([windX], [windZ]). */
    fun set(aim: Float, windX: Float, windZ: Float) {
        val s = hypot(windX, windZ)
        val a = (aim - atan2(windX, windZ)).let { if (it.isFinite()) it else 0f }
        if (abs(a - angle) < .01f && abs(s - speed) < .005f) return
        angle = a; speed = s
        label = context.getString(R.string.classic_wind_speed, s)
        contentDescription = label
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val r = width / 2f - 2f * d
        val cy = width / 2f
        p.style = Paint.Style.FILL
        p.color = GolfUi.SCENE_PLATE
        c.drawCircle(cx, cy, r + 2f * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d
        p.color = 0x66FFFFFF
        c.drawCircle(cx, cy, r, p)
        // The rose: four long points, four short ones; the top one is the line of the shot.
        p.style = Paint.Style.FILL
        for (k in 0 until 8) {
            val long = k % 2 == 0
            val tip = r * if (long) .92f else .62f
            val half = r * if (long) .13f else .09f
            c.save(); c.rotate(k * 45f, cx, cy)
            path.reset()
            path.moveTo(cx, cy - tip); path.lineTo(cx + half, cy); path.lineTo(cx - half, cy); path.close()
            p.color = if (k == 0) 0xFFFFD25C.toInt() else 0x66FFFFFF
            c.drawPath(path, p)
            c.restore()
        }
        // Wind arrow.
        val colour = when { speed < 1.6f -> 0xFFBFE3F2.toInt(); speed < 2.8f -> 0xFFFFD778.toInt(); else -> 0xFFFF8A5C.toInt() }
        c.save(); c.rotate(Math.toDegrees(angle.toDouble()).toFloat(), cx, cy)
        path.reset()
        path.moveTo(cx, cy - r * .74f)
        path.lineTo(cx + r * .27f, cy - r * .22f); path.lineTo(cx + r * .09f, cy - r * .22f)
        path.lineTo(cx + r * .09f, cy + r * .46f); path.lineTo(cx - r * .09f, cy + r * .46f)
        path.lineTo(cx - r * .09f, cy - r * .22f); path.lineTo(cx - r * .27f, cy - r * .22f)
        path.close()
        p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = 2.5f * d; p.strokeJoin = Paint.Join.ROUND
        p.color = 0xCC101814.toInt()
        c.drawPath(path, p)
        p.style = Paint.Style.FILL; p.color = colour
        c.drawPath(path, p)
        c.restore()
        // Speed under the rose.
        p.color = colour
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = 12f * d
        p.setShadowLayer(3f * d, 0f, d, 0xAA000000.toInt())
        c.drawText(label, cx, height - 4f * d, p)
        p.clearShadowLayer()
        p.typeface = android.graphics.Typeface.DEFAULT
    }
}

/**
 * Thin streaks of air drifting over the scene in the direction the wind pushes: more and faster
 * with a stronger wind. It never takes touches.
 */
internal class ClassicWindStreaks(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }
    private val random = Random(7)
    private val xs = FloatArray(COUNT) { random.nextFloat() }
    private val ys = FloatArray(COUNT) { random.nextFloat() }
    private val pace = FloatArray(COUNT) { .7f + .6f * random.nextFloat() }
    private var dirX = 0f
    private var dirY = -1f
    private var strength = 0f
    private var waited = 0f

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** Advances the streaks; redraws at about 30 images a second, which is plenty for thin lines. */
    fun update(dt: Float, aim: Float, windX: Float, windZ: Float) {
        waited += dt
        if (waited < .033f || width == 0) return
        val step = waited.coerceAtMost(.1f); waited = 0f
        strength = hypot(windX, windZ)
        val a = (aim - atan2(windX, windZ)).let { if (it.isFinite()) it else 0f }
        dirX = sin(a); dirY = -cos(a)
        val shown = visibleCount()
        for (i in 0 until shown) {
            val v = (50f + 45f * strength) * d * pace[i] * step
            xs[i] += dirX * v / width; ys[i] += dirY * v / height
            if (xs[i] < -.1f || xs[i] > 1.1f || ys[i] < -.1f || ys[i] > 1.1f) respawn(i)
        }
        invalidate()
    }

    private fun visibleCount() = (4 + strength * 5f).toInt().coerceIn(4, COUNT)

    /** A streak that left the screen comes back upstream, on the edge the wind comes from. */
    private fun respawn(i: Int) {
        val ax = abs(dirX); val ay = abs(dirY)
        if (random.nextFloat() * (ax + ay) < ax) {
            xs[i] = if (dirX > 0f) -.05f else 1.05f; ys[i] = random.nextFloat()
        } else {
            ys[i] = if (dirY > 0f) -.05f else 1.05f; xs[i] = random.nextFloat()
        }
    }

    override fun onDraw(c: Canvas) {
        val length = (16f + 11f * strength) * d
        p.strokeWidth = 1.6f * d
        for (i in 0 until visibleCount()) {
            val x = xs[i] * width; val y = ys[i] * height
            p.alpha = 40 + (pace[i] * 40f).toInt()
            c.drawLine(x, y, x - dirX * length * pace[i], y - dirY * length * pace[i], p)
        }
    }

    private companion object { const val COUNT = 26 }
}
