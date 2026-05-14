package com.Atom2Universe.app.notes.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorPickerSatValView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var onColorChanged: ((Int) -> Unit)? = null
    private var hue = 0f
    private var sat = 1f
    private var value = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        val satShader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            Color.WHITE, Color.HSVToColor(floatArrayOf(hue, 1f, 1f)),
            Shader.TileMode.CLAMP
        )
        val valShader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP
        )
        paint.shader = ComposeShader(valShader, satShader, PorterDuff.Mode.MULTIPLY)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val cx = sat * width
        val cy = (1f - value) * height
        canvas.drawCircle(cx, cy, 10f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            sat = (event.x / width).coerceIn(0f, 1f)
            value = 1f - (event.y / height).coerceIn(0f, 1f)
            onColorChanged?.invoke(Color.HSVToColor(floatArrayOf(hue, sat, value)))
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun setHsv(h: Float, s: Float, v: Float) {
        hue = h; sat = s; value = v
        invalidate()
    }

    fun setHue(h: Float) {
        hue = h
        onColorChanged?.invoke(Color.HSVToColor(floatArrayOf(hue, sat, value)))
        invalidate()
    }

    fun getCurrentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))
}
