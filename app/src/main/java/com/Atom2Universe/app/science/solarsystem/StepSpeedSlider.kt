package com.Atom2Universe.app.science.solarsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * Slider à étapes bidirectionnel pour la vitesse de simulation.
 *
 * Contrairement à un [android.widget.SeekBar] standard qui remplit sa piste depuis
 * le bord gauche, le remplissage part ici du centre (= pause) pour matérialiser le
 * sens du temps : ambre vers la gauche = marche arrière, bleu vers la droite = avant.
 * Chaque étape est une graduation ; le pouce s'aimante sur la graduation la plus proche.
 */
class StepSpeedSlider(context: Context) : View(context) {

    var stepCount = 15
    var centerStep = 7

    var step = 11
        private set

    /** Appelé uniquement quand l'utilisateur change d'étape (pas en programmatique). */
    var onStepChanged: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val thumbRadius = 9f * density
    private val trackHeight = 3f * density
    private val tickRadius = 1.6f * density
    private val centerTickRadius = 3.5f * density

    private val fwdColor = 0xFF42A5F5.toInt()   // bleu = avant
    private val revColor = 0xFFFFB300.toInt()    // ambre = arrière

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF555566.toInt() }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF888899.toInt() }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * density
    }

    private val trackLeft get() = paddingLeft + thumbRadius
    private val trackRight get() = width - paddingRight - thumbRadius
    private val centerY get() = height / 2f

    private fun stepX(i: Int): Float {
        val t = i.toFloat() / (stepCount - 1)
        return trackLeft + (trackRight - trackLeft) * t
    }

    /** Positionne le pouce. [notify] = true déclenche [onStepChanged] si la valeur change. */
    fun setStep(newStep: Int, notify: Boolean = false) {
        val clamped = newStep.coerceIn(0, stepCount - 1)
        if (clamped == step) return
        step = clamped
        invalidate()
        if (notify) onStepChanged?.invoke(step)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (thumbRadius * 2 + 4 * density).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = centerY
        val left = trackLeft; val right = trackRight
        val cx = stepX(centerStep)
        val thumbCx = stepX(step)
        val top = cy - trackHeight / 2; val bot = cy + trackHeight / 2

        canvas.drawRoundRect(left, top, right, bot, trackHeight / 2, trackHeight / 2, trackPaint)

        // Remplissage ancré au centre, coloré selon le sens
        when {
            step > centerStep -> {
                fillPaint.color = fwdColor
                canvas.drawRoundRect(cx, top, thumbCx, bot, trackHeight / 2, trackHeight / 2, fillPaint)
            }
            step < centerStep -> {
                fillPaint.color = revColor
                canvas.drawRoundRect(thumbCx, top, cx, bot, trackHeight / 2, trackHeight / 2, fillPaint)
            }
        }

        // Graduations
        for (i in 0 until stepCount) {
            if (i == centerStep) continue
            canvas.drawCircle(stepX(i), cy, tickRadius, tickPaint)
        }
        // Repère central (pause) plus marqué
        canvas.drawCircle(cx, cy, centerTickRadius, centerPaint)

        // Pouce
        thumbPaint.color = when {
            step > centerStep -> fwdColor
            step < centerStep -> revColor
            else -> 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(thumbCx, cy, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbCx, cy, thumbRadius, thumbStroke)
    }

    private fun xToStep(x: Float): Int {
        val span = (trackRight - trackLeft).coerceAtLeast(1f)
        val t = ((x - trackLeft) / span).coerceIn(0f, 1f)
        return (t * (stepCount - 1)).roundToInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setStep(xToStep(event.x), notify = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                setStep(xToStep(event.x), notify = true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
