package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Le pad thérémine : on glisse le doigt (ou la souris, bouton enfoncé) pour jouer. De gauche à droite, les notes de la gamme
 * ([pitches], de la plus grave à la plus aiguë) ; de bas en haut, le jeu passe de doux et sourd à fort et brillant.
 * [onStart] quand le doigt se pose, [onPitch] quand il change de note, [onExpression] quand il monte ou descend, [onEnd]
 * quand il se lève. Un seul doigt joue à la fois.
 */
class ThereminView(context: Context) : View(context) {

    /** Les notes jouables, de gauche à droite. */
    var pitches: IntArray = IntArray(0)
        set(v) {
            field = v
            invalidate()
        }

    /** La tonique (0 = do) : ses colonnes sont marquées plus fort, avec le nom de la note. */
    var keyPc: Int = 0
    var noteNames: List<String> = emptyList()
    var accent: Int = 0xFF7C8CFF.toInt()

    var onStart: ((pitch: Int, expression: Float) -> Unit)? = null
    var onPitch: ((from: Int, to: Int) -> Unit)? = null
    var onExpression: ((expression: Float) -> Unit)? = null
    var onEnd: ((pitch: Int) -> Unit)? = null

    private var pointer = -1
    private var current = -1
    private var fx = 0f
    private var fy = 0f

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        color = if (dark) 0xFFB8B8C4.toInt() else 0xFF55555F.toInt()
    }
    private val rect = RectF()
    private var shadeFor = 0

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun columnWidth(): Float = if (pitches.isEmpty()) 0f else width / pitches.size.toFloat()

    private fun pitchAt(x: Float): Int {
        if (pitches.isEmpty()) return -1
        val i = (x / columnWidth()).toInt().coerceIn(0, pitches.size - 1)
        return pitches[i]
    }

    /** 0 en bas (doux, sourd) à 1 en haut (fort, brillant), avec un plancher pour qu'on entende toujours quelque chose. */
    private fun expressionAt(y: Float): Float = (0.15f + 0.85f * (1f - y / height.coerceAtLeast(1))).coerceIn(0.15f, 1f)

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        // Fond : sombre en bas, plus clair en haut (le son s'éclaircit en montant)
        if (shadeFor != height) {
            shadeFor = height
            val top = if (dark) 0xFF4A4A70.toInt() else 0xFFF4F4FA.toInt()
            val bottom = if (dark) 0xFF111116.toInt() else 0xFFB4B4C4.toInt()
            paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        val shader = paint.shader
        paint.shader = null

        val cw = columnWidth()
        for (i in pitches.indices) {
            val p = pitches[i]
            val tonic = ((p % 12) + 12) % 12 == keyPc
            val x = i * cw
            if (i > 0) {
                paint.color = if (tonic) Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent))
                else if (dark) 0x33FFFFFF else 0x33000000
                paint.strokeWidth = if (tonic) dp(2f) else dp(1f)
                canvas.drawLine(x, dp(10f), x, height - dp(10f), paint)
            }
            if (tonic && noteNames.size == 12) {
                canvas.drawText(noteNames[((p % 12) + 12) % 12] + (p / 12 - 1), x + cw / 2f, height - dp(14f), text)
            }
            if (p == current) {
                paint.color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
                rect.set(x, 0f, x + cw, height.toFloat())
                canvas.drawRect(rect, paint)
            }
        }
        if (pointer >= 0) {
            paint.color = accent
            canvas.drawCircle(fx, fy, dp(22f), paint)
            paint.color = Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(fx, fy, dp(40f), paint)
        }
        paint.shader = shader
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = event.getPointerId(0)
                fx = event.x
                fy = event.y
                current = pitchAt(fx)
                if (current >= 0) onStart?.invoke(current, expressionAt(fy))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val k = event.findPointerIndex(pointer)
                if (k < 0) return true
                fx = event.getX(k).coerceIn(0f, width.toFloat())
                fy = event.getY(k).coerceIn(0f, height.toFloat())
                val p = pitchAt(fx)
                if (p >= 0 && p != current && current >= 0) {
                    onPitch?.invoke(current, p)
                    current = p
                }
                onExpression?.invoke(expressionAt(fy))
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointer) finish()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finish()
        }
        return true
    }

    /** Le doigt se lève (ou la page part) : la note se relâche. */
    fun finish() {
        if (pointer < 0) return
        pointer = -1
        val p = current
        current = -1
        if (p >= 0) onEnd?.invoke(p)
        invalidate()
    }
}
