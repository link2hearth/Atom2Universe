package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Le morceau en blocs : un bloc par motif joué, dans l'ordre, qui passe à la ligne quand la place manque. On glisse un bloc
 * pour le ranger ailleurs ; on le tire hors de la rangée (vers le haut ou le bas) pour le retirer. Le bloc qui joue s'allume.
 */
class ChainView(context: Context) : View(context) {

    var chain: List<Int> = emptyList()
        set(v) {
            field = v
            requestLayout()
            invalidate()
        }

    /** Le nom de chaque emplacement (A à D) et sa couleur. */
    var names: List<String> = emptyList()
    var colors: IntArray = IntArray(0)

    /** Le rang du bloc qui joue en ce moment (-1 : aucun) ; lu à chaque image pendant la lecture. */
    var playingIndex: Int = -1
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var onMove: ((from: Int, to: Int) -> Unit)? = null
    var onRemove: ((index: Int) -> Unit)? = null

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val rect = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val block get() = dp(56f)
    private val gap get() = dp(8f)

    private fun perRow(): Int = maxOf(1, ((width + gap) / (block + gap)).toInt())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val per = maxOf(1, ((w + gap) / (block + gap)).toInt())
        val rows = maxOf(1, (chain.size + per - 1) / per)
        setMeasuredDimension(w, (rows * (block + gap) + gap).toInt())
    }

    private fun slotRect(i: Int, out: RectF) {
        val per = perRow()
        val x = (i % per) * (block + gap)
        val y = gap / 2 + (i / per) * (block + gap)
        out.set(x, y, x + block, y + block)
    }

    // Glissé en cours
    private var dragFrom = -1
    private var dragTo = -1
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f

    /** Retirer : le doigt est sorti de la zone des blocs d'au moins un demi-bloc. */
    private fun outside(): Boolean = curY < -block / 2 || curY > height + block / 2

    private fun indexAt(x: Float, y: Float): Int {
        val per = perRow()
        val c = (x / (block + gap)).toInt().coerceIn(0, per - 1)
        val r = ((y - gap / 2) / (block + gap)).toInt().coerceAtLeast(0)
        return (r * per + c).coerceIn(0, maxOf(0, chain.size - 1))
    }

    override fun onDraw(canvas: Canvas) {
        val now = playingIndex
        if (!dragging) {
            for (i in chain.indices) {
                slotRect(i, rect)
                drawBlock(canvas, i, if (i == now) 1f else 0.75f)
            }
            return
        }
        // Pendant le glissé, les autres blocs s'écartent pour montrer où le bloc tombera
        val order = chain.indices.toMutableList()
        if (dragging && dragFrom >= 0) {
            order.remove(dragFrom)
            if (!outside()) order.add(dragTo.coerceIn(0, order.size), dragFrom)
        }
        for ((place, i) in order.withIndex()) {
            if (dragging && i == dragFrom) continue
            slotRect(place, rect)
            drawBlock(canvas, i, if (i == now) 1f else 0.75f)
        }
        if (dragging && dragFrom >= 0) {
            rect.set(curX - block / 2, curY - block / 2, curX + block / 2, curY + block / 2)
            drawBlock(canvas, dragFrom, if (outside()) 0.3f else 1f)
        }
    }

    private fun drawBlock(canvas: Canvas, i: Int, light: Float) {
        val slot = chain[i]
        val color = colors.getOrElse(slot) { 0xFF7C8CFF.toInt() }
        val base = if (dark) 0xFF202027.toInt() else 0xFFE4E4EA.toInt()
        paint.color = blend(base, color, light)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)
        canvas.drawText(names.getOrElse(slot) { "" }, rect.centerX(), rect.centerY() + label.textSize / 3f, label)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (chain.isEmpty()) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                curX = event.x
                curY = event.y
                val i = indexAt(event.x, event.y)
                slotRect(i, rect)
                dragFrom = if (rect.contains(event.x, event.y)) i else -1
                dragTo = dragFrom
                dragging = false
                return dragFrom >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragFrom < 0) return true
                curX = event.x
                curY = event.y
                if (!dragging && (abs(curX - downX) > slop || abs(curY - downY) > slop)) {
                    dragging = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                if (dragging) {
                    dragTo = indexAt(curX.coerceIn(0f, width - 1f), curY.coerceIn(0f, height - 1f))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging && dragFrom >= 0) {
                    if (outside()) onRemove?.invoke(dragFrom)
                    else if (dragTo != dragFrom) onMove?.invoke(dragFrom, dragTo)
                }
                dragFrom = -1
                dragging = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragFrom = -1
                dragging = false
                invalidate()
            }
        }
        return true
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }
}
