package com.Atom2Universe.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vue personnalisée pour sélectionner la teinte (hue) dans un gradient horizontal
 */
class ColorPickerHueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var hue: Float = 0f
    private var onHueChanged: ((Float) -> Unit)? = null

    // Cached objects to avoid allocations in onDraw
    private var cachedGradient: LinearGradient? = null
    private var cachedWidth: Int = 0
    private val hueColors = IntArray(7)
    private val hsv = floatArrayOf(0f, 1f, 1f)

    init {
        // Pre-compute hue colors (these never change)
        for (i in 0..6) {
            hsv[0] = i * 60f
            hueColors[i] = Color.HSVToColor(hsv)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            cachedGradient = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                hueColors,
                null,
                Shader.TileMode.CLAMP
            )
            cachedWidth = w
        }
    }

    fun setHue(h: Float) {
        hue = h.coerceIn(0f, 360f)
        invalidate()
    }

    fun getHue(): Float = hue

    fun setOnHueChangedListener(listener: (Float) -> Unit) {
        onHueChanged = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = h / 2

        // Use cached gradient (created in onSizeChanged)
        paint.shader = cachedGradient

        // Dessiner le rectangle arrondi
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, paint)

        // Dessiner le sélecteur
        val selectorX = (hue / 360f) * w
        val selectorRadius = h / 2 - 2

        // Cercle externe blanc
        selectorPaint.color = Color.WHITE
        selectorPaint.style = Paint.Style.FILL
        canvas.drawCircle(selectorX, h / 2, selectorRadius, selectorPaint)

        // Bordure noire
        selectorPaint.color = Color.BLACK
        selectorPaint.style = Paint.Style.STROKE
        selectorPaint.strokeWidth = 2f
        canvas.drawCircle(selectorX, h / 2, selectorRadius, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val x = event.x.coerceIn(0f, width.toFloat())
                hue = (x / width) * 360f
                onHueChanged?.invoke(hue)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
