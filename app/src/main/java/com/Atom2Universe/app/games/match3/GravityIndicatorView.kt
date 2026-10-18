package com.Atom2Universe.app.games.match3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.Atom2Universe.app.R

/** Shared gravity cue: 0 down, 1 left, 2 right, 3 up. */
class GravityIndicatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var direction = 0
        set(value) {
            field = value
            contentDescription = resources.getStringArray(R.array.forge_gravity_directions)[value]
            invalidate()
        }
    init { direction = 0; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = 0xffffc46b.toInt(); paint.strokeWidth = resources.displayMetrics.density * 3f
        paint.strokeCap = Paint.Cap.ROUND; paint.style = Paint.Style.STROKE
        val radius = minOf(width, height) * .3f
        canvas.save(); canvas.translate(width / 2f, height / 2f)
        canvas.rotate(when (direction) { 1 -> 90f; 2 -> -90f; 3 -> 180f; else -> 0f })
        canvas.drawLine(0f, -radius, 0f, radius, paint)
        canvas.drawLine(-radius * .65f, radius * .35f, 0f, radius, paint)
        canvas.drawLine(radius * .65f, radius * .35f, 0f, radius, paint)
        canvas.restore()
    }
}

internal class GravityFrame {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, direction: Int, density: Float) {
        val gap = 9f * density
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        val x1 = if (direction == 2) right + gap else left - gap
        val y1 = if (direction == 0) bottom + gap else top - gap
        val x2 = if (direction == 0 || direction == 3) right + gap else x1
        val y2 = if (direction == 1 || direction == 2) bottom + gap else y1
        paint.color = 0x55ffb44f; paint.strokeWidth = 9f * density
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.color = 0xffffc46b.toInt(); paint.strokeWidth = 3f * density
        canvas.drawLine(x1, y1, x2, y2, paint)
        // Three outward-facing chevrons distinguish gravity from green delivery exits.
        repeat(3) { n ->
            val t = (n + 1) / 4f
            val x = x1 + (x2 - x1) * t; val y = y1 + (y2 - y1) * t
            canvas.save(); canvas.translate(x, y)
            canvas.rotate(when (direction) { 1 -> 90f; 2 -> -90f; 3 -> 180f; else -> 0f })
            canvas.drawLine(-4 * density, -3 * density, 0f, 2 * density, paint)
            canvas.drawLine(4 * density, -3 * density, 0f, 2 * density, paint)
            canvas.restore()
        }
    }
}
