package com.Atom2Universe.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vue personnalisée pour sélectionner la saturation et la valeur (luminosité)
 * dans un carré avec gradient horizontal (saturation) et vertical (valeur)
 */
class ColorPickerSatValView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var hue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f

    private var onColorChanged: ((Float, Float) -> Unit)? = null

    // Cached objects to avoid allocations in onDraw
    private var cachedShader: ComposeShader? = null
    private var cachedValShader: LinearGradient? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0
    private var cachedHue: Float = Float.NaN
    private val hsvArray = floatArrayOf(0f, 1f, 1f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            cachedWidth = w
            cachedHeight = h
            // Value shader (vertical: transparent to black) doesn't depend on hue
            cachedValShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.TRANSPARENT, Color.BLACK,
                Shader.TileMode.CLAMP
            )
            // Force shader recreation on next draw
            cachedHue = Float.NaN
        }
    }

    private fun updateShaderIfNeeded() {
        if (cachedWidth <= 0 || cachedHeight <= 0) return
        if (cachedHue == hue && cachedShader != null) return

        // Compute hue color
        hsvArray[0] = hue
        hsvArray[1] = 1f
        hsvArray[2] = 1f
        val hueColor = Color.HSVToColor(hsvArray)

        // Saturation shader (horizontal: white to hue color)
        val satShader = LinearGradient(
            0f, 0f, cachedWidth.toFloat(), 0f,
            Color.WHITE, hueColor,
            Shader.TileMode.CLAMP
        )

        // Combine shaders
        cachedValShader?.let { valShader ->
            cachedShader = ComposeShader(satShader, valShader, PorterDuff.Mode.DARKEN)
        }
        cachedHue = hue
    }

    fun setHue(h: Float) {
        hue = h.coerceIn(0f, 360f)
        invalidate()
    }

    fun setSaturation(s: Float) {
        saturation = s.coerceIn(0f, 1f)
        invalidate()
    }

    fun setValue(v: Float) {
        value = v.coerceIn(0f, 1f)
        invalidate()
    }

    fun getSaturation(): Float = saturation
    fun getValue(): Float = value

    fun setOnColorChangedListener(listener: (Float, Float) -> Unit) {
        onColorChanged = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = 8f

        // Update cached shader if hue changed
        updateShaderIfNeeded()
        paint.shader = cachedShader

        // Dessiner le rectangle
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, paint)

        // Dessiner le sélecteur (cercle)
        val selectorX = saturation * w
        val selectorY = (1f - value) * h
        val selectorRadius = 12f

        // Cercle externe blanc
        selectorPaint.color = Color.WHITE
        selectorPaint.style = Paint.Style.STROKE
        selectorPaint.strokeWidth = 4f
        canvas.drawCircle(selectorX, selectorY, selectorRadius, selectorPaint)

        // Cercle interne noir
        selectorPaint.color = Color.BLACK
        selectorPaint.strokeWidth = 2f
        canvas.drawCircle(selectorX, selectorY, selectorRadius - 3f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val x = event.x.coerceIn(0f, width.toFloat())
                val y = event.y.coerceIn(0f, height.toFloat())

                saturation = x / width
                value = 1f - (y / height)

                onColorChanged?.invoke(saturation, value)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
