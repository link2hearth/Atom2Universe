package com.Atom2Universe.app.notes.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorPickerHueView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var onHueChanged: ((Float) -> Unit)? = null
    private var hue = 0f
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            buildHueColors(), null, Shader.TileMode.CLAMP
        )
        huePaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), huePaint)
        val x = hue / 360f * width
        canvas.drawCircle(x, height / 2f, height / 2f - 2, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            hue = (event.x / width * 360f).coerceIn(0f, 360f)
            onHueChanged?.invoke(hue)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun setHue(h: Float) {
        hue = h
        invalidate()
    }

    private fun buildHueColors(): IntArray {
        return IntArray(361) { i -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)) }
    }
}
