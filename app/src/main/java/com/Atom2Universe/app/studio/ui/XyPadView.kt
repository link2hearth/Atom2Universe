package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R

/**
 * Un pad à deux axes pour jouer des effets du doigt. [onMove] reçoit x (0 à gauche, 1 à droite) et y (0 en bas, 1 en
 * haut) tant que le doigt est posé. Le mixeur décide de la position au relâchement.
 */
open class XyPadView(
    context: Context,
    private val horizontalLabel: String = "← " + context.getString(R.string.studio_fx_filter),
    private val verticalLabel: String = "↑ " + context.getString(R.string.studio_space),
) : View(context) {

    fun interface MoveListener { fun moved(x: Float, y: Float) }
    var onMove: MoveListener? = null
    var onRelease: ((completed: Boolean) -> Unit)? = null
    var accent: Int = 0xFF7C8CFF.toInt()

    var padX = 1f
        private set
    var padY = 0f
        private set
    private var touching = false

    /** Vrai tant qu'un doigt est posé. */
    val isTouching: Boolean get() = touching

    /** Appelé quand le doigt se pose, avant le premier [onMove]. */
    var onPress: (() -> Unit)? = null

    /** Rouge discret quand le geste est en cours d'enregistrement. */
    var recording = false
        set(v) {
            field = v
            invalidate()
        }

    /** Montre où en est le geste enregistré (le pad le joue tout seul) : sans doigt, juste un repère qui bouge. */
    fun showAt(fx: Float, fy: Float) {
        if (touching) return
        val nx = fx.coerceIn(0f, 1f)
        val ny = fy.coerceIn(0f, 1f)
        // Appelé à chaque image pendant la lecture : on ne redessine que si le repère bouge (sans geste enregistré, il ne bouge pas)
        if (nx == padX && ny == padY) return
        padX = nx
        padY = ny
        invalidate()
    }

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val bg = if (dark) 0xFF2A2A31.toInt() else 0xFFDADAE2.toInt()
    private val lineColor = if (dark) 0xFF4A4A54.toInt() else 0xFFB8B8C4.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val axisWidth = maxOf(axisPaint.measureText(horizontalLabel), axisPaint.measureText(verticalLabel))

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = if (recording) 0xFF4A2A2E.toInt() else bg
        canvas.drawRoundRect(rect, dp(14f), dp(14f), paint)

        val px = padX * width
        val py = (1f - padY) * height
        paint.color = lineColor
        paint.strokeWidth = dp(1.5f)
        canvas.drawLine(px, 0f, px, height.toFloat(), paint)
        canvas.drawLine(0f, py, width.toFloat(), py, paint)

        val margin = dp(12f)
        if (height > 2f * (margin + axisPaint.textSize) && width > margin * 2 + axisWidth) {
            axisPaint.color = if (dark || recording) 0xFFB9B9C5.toInt() else 0xFF51515F.toInt()
            canvas.drawText(verticalLabel, margin, margin - axisPaint.ascent(), axisPaint)
            canvas.drawText(horizontalLabel, margin, height - margin, axisPaint)
        }

        paint.color = accent
        paint.alpha = if (touching) 255 else 140
        val inset = minOf(dp(14f), width / 2f, height / 2f)
        canvas.drawCircle(px.coerceIn(inset, width - inset), py.coerceIn(inset, height - inset), if (touching) dp(18f) else dp(12f), paint)
        paint.alpha = 255
    }

    fun finishTouch(completed: Boolean) {
        if (!touching) return
        touching = false
        onRelease?.invoke(completed)
        invalidate()
    }

    private fun moveTo(event: MotionEvent) {
        if (width <= 0 || height <= 0) return
        padX = (event.x / width).coerceIn(0f, 1f)
        padY = (1f - event.y / height).coerceIn(0f, 1f)
        onMove?.moved(padX, padY)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!touching) onPress?.invoke()
                touching = true
                moveTo(event)
            }
            MotionEvent.ACTION_MOVE -> if (touching) moveTo(event)
            MotionEvent.ACTION_UP -> if (touching) {
                moveTo(event)
                finishTouch(true)
            }
            MotionEvent.ACTION_CANCEL -> finishTouch(false)
        }
        return true
    }
}
