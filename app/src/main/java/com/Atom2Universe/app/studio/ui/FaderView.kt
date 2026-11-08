package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Un curseur dessiné, vertical (volume) ou horizontal (panoramique). La valeur va de 0 à 1.
 * [defaultValue] dessine un petit repère et, si [snapToDefault], aimante le curseur dessus (le centre du panoramique).
 * Le remplissage part du bas, de la gauche, ou du repère quand [fillFromDefault] est vrai.
 */
class FaderView(context: Context, private val vertical: Boolean) : View(context) {

    var value: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var defaultValue: Float = -1f
    var snapToDefault = false
    var fillFromDefault = false
    var accent: Int = 0xFF7C8CFF.toInt()

    /** Appelé à chaque changement venu du doigt. */
    var onChange: ((Float) -> Unit)? = null

    /** Appelé quand le doigt se lève. */
    var onRelease: (() -> Unit)? = null

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val grooveColor = if (dark) 0xFF3B3B42.toInt() else 0xFFCFCFD6.toInt()
    private val tickColor = if (dark) 0xFF9A9AA4.toInt() else 0xFF6A6A74.toInt()
    private val thumbColor = if (dark) 0xFFE8E8F0.toInt() else 0xFF2A2A30.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val thick = dp(8f)
        val margin = dp(16f)
        // Le rail
        if (vertical) rect.set(width / 2f - thick / 2, margin, width / 2f + thick / 2, height - margin)
        else rect.set(margin, height / 2f - thick / 2, width - margin, height / 2f + thick / 2)
        paint.color = grooveColor
        canvas.drawRoundRect(rect, thick, thick, paint)

        val start = if (fillFromDefault && defaultValue >= 0f) defaultValue else 0f
        val a = pos(minOf(start, value))
        val b = pos(maxOf(start, value))
        paint.color = accent
        if (vertical) rect.set(width / 2f - thick / 2, b, width / 2f + thick / 2, a)
        else rect.set(a, height / 2f - thick / 2, b, height / 2f + thick / 2)
        canvas.drawRoundRect(rect, thick, thick, paint)

        // Le repère de la valeur d'origine
        if (defaultValue >= 0f) {
            paint.color = tickColor
            val p = pos(defaultValue)
            if (vertical) canvas.drawRect(width / 2f - dp(10f), p - dp(1f), width / 2f + dp(10f), p + dp(1f), paint)
            else canvas.drawRect(p - dp(1f), height / 2f - dp(10f), p + dp(1f), height / 2f + dp(10f), paint)
        }

        // La poignée
        paint.color = thumbColor
        val p = pos(value)
        if (vertical) rect.set(width / 2f - dp(18f), p - dp(8f), width / 2f + dp(18f), p + dp(8f))
        else rect.set(p - dp(8f), height / 2f - dp(16f), p + dp(8f), height / 2f + dp(16f))
        canvas.drawRoundRect(rect, dp(6f), dp(6f), paint)
    }

    /** Position (en pixels, le long du rail) d'une valeur. */
    private fun pos(v: Float): Float {
        val margin = dp(16f)
        return if (vertical) height - margin - v * (height - 2 * margin) else margin + v * (width - 2 * margin)
    }

    private fun valueAt(x: Float, y: Float): Float {
        val margin = dp(16f)
        val v = if (vertical) (height - margin - y) / (height - 2 * margin) else (x - margin) / (width - 2 * margin)
        return v.coerceIn(0f, 1f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                var v = valueAt(event.x, event.y)
                if (snapToDefault && defaultValue >= 0f && abs(v - defaultValue) < 0.04f) v = defaultValue
                if (v != value) {
                    value = v
                    onChange?.invoke(v)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onRelease?.invoke()
        }
        return true
    }
}
