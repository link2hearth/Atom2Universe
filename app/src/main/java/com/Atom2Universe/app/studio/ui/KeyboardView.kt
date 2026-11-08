package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.studio.music.Scale
import com.Atom2Universe.app.studio.music.Scales

/**
 * Le clavier tactile du synthé : une touche par ligne du piano roll (mêmes hauteurs, même gamme, même octave), donc
 * « verrouillé sur la gamme » il n'a que des notes justes. Plusieurs doigts jouent en même temps ; glisser le doigt
 * d'une touche à l'autre enchaîne les notes (glissando).
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyDown(pitch: Int)
        fun onKeyUp(pitch: Int)
    }

    var listener: Listener? = null

    /** Les hauteurs MIDI des touches, de la plus grave à la plus aiguë. */
    var rows: IntArray = IntArray(0)
        set(value) {
            field = value
            invalidate()
        }
    var keyPc: Int = 9
    var scale: Scale = Scales.MINOR_PENT
    var accent: Int = 0xFF1ABC9C.toInt()
    var noteNames: List<String> = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val keyIn = if (dark) 0xFF4A4A52.toInt() else 0xFFE4E4EA.toInt()
    private val keyRoot = if (dark) 0xFF63636E.toInt() else 0xFFC4C4D0.toInt()
    private val keyOut = if (dark) 0xFF2A2A2F.toInt() else 0xFF9A9AA6.toInt()
    private val labelColor = if (dark) 0xFFD0D0DA.toInt() else 0xFF303038.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    /** Doigt (identifiant) -> hauteur de la touche qu'il tient. */
    private val down = HashMap<Int, Int>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (rows.isEmpty() || width == 0) return
        val keyW = width.toFloat() / rows.size
        val gap = dp(1.5f)
        val showAll = keyW > dp(30f)
        val pressed = down.values
        for (i in rows.indices) {
            val pitch = rows[i]
            val inScale = Scales.contains(scale, keyPc, pitch)
            val isRoot = ((pitch - keyPc) % 12 + 12) % 12 == 0
            rect.set(i * keyW + gap, gap, (i + 1) * keyW - gap, height - gap)
            fill.color = when {
                pitch in pressed -> accent
                isRoot -> keyRoot
                inScale -> keyIn
                else -> keyOut
            }
            canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
            if (showAll || isRoot) {
                label.color = if (pitch in pressed) 0xFFFFFFFF.toInt() else labelColor
                label.isFakeBoldText = isRoot
                canvas.drawText(noteNames[((pitch % 12) + 12) % 12], rect.centerX(), height - dp(10f), label)
            }
        }
    }

    private fun pitchAt(x: Float): Int {
        if (rows.isEmpty() || width == 0) return -1
        val i = (x / width * rows.size).toInt().coerceIn(0, rows.size - 1)
        return rows[i]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val pitch = pitchAt(event.getX(idx))
                if (pitch >= 0 && pitch !in down.values) {
                    down[id] = pitch
                    listener?.onKeyDown(pitch)
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (k in 0 until event.pointerCount) {
                    val id = event.getPointerId(k)
                    val old = down[id] ?: continue
                    val pitch = pitchAt(event.getX(k))
                    if (pitch >= 0 && pitch != old && pitch !in down.values) {
                        down[id] = pitch
                        listener?.onKeyUp(old)
                        listener?.onKeyDown(pitch)
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    releaseAll()
                } else {
                    val pitch = down.remove(event.getPointerId(idx))
                    if (pitch != null) listener?.onKeyUp(pitch)
                    invalidate()
                }
            }
        }
        return true
    }

    /** Relâche tout (écran quitté, annulation du geste). */
    fun releaseAll() {
        val held = down.values.toList()
        down.clear()
        for (p in held) listener?.onKeyUp(p)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }
}
