package com.Atom2Universe.app.crypto.fusion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FusionSplatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var scale = 0f
    private var targetRadius = 0f
    private val blobPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 16 bras pour une forme organique bien fournie
    private val numArms = 16
    private val armAngles = FloatArray(numArms)
    private val armRadii = FloatArray(numArms)

    private var currentAnimator: ValueAnimator? = null

    /**
     * Lance l'animation d'écrasement.
     * [color]        couleur du blob
     * [targetRadius] rayon de base cible (les bras vont de 0.55× à 1.45×)
     * [seed]         graine pour la forme distordue (reproductible)
     * [onDone]       appelé quand l'animation est terminée
     */
    fun splat(color: Int, targetRadius: Float, seed: Int, onDone: () -> Unit) {
        this.targetRadius = targetRadius
        paint.color = color

        val rng = Random(seed)
        for (i in 0 until numArms) {
            // Angle légèrement irrégulier pour l'aspect organique
            armAngles[i] = i * (2.0 * Math.PI / numArms).toFloat() + (rng.nextFloat() - 0.5f) * 0.5f
            // Rayon variable : entre 0.55 et 1.45 du rayon de base
            armRadii[i] = 0.55f + rng.nextFloat() * 0.90f
        }

        scale = 0f
        visibility = VISIBLE
        invalidate()

        currentAnimator?.cancel()
        // 0 → 1.18 (overshoot d'écrasement) → 1.0 (stabilisation)
        currentAnimator = ValueAnimator.ofFloat(0f, 1.18f, 0.97f, 1.0f).apply {
            duration = 380
            interpolator = DecelerateInterpolator(2.0f)
            addUpdateListener {
                scale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onDone()
            })
            start()
        }
    }

    fun hide() {
        currentAnimator?.cancel()
        scale = 0f
        visibility = GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (scale <= 0f || width == 0 || height == 0) return
        buildPath(width / 2f, height / 2f, targetRadius * scale)
        canvas.drawPath(blobPath, paint)
    }

    private fun buildPath(cx: Float, cy: Float, baseRadius: Float) {
        blobPath.reset()

        // Points des bras
        val px = FloatArray(numArms)
        val py = FloatArray(numArms)
        for (i in 0 until numArms) {
            val r = baseRadius * armRadii[i]
            px[i] = cx + r * cos(armAngles[i].toDouble()).toFloat()
            py[i] = cy + r * sin(armAngles[i].toDouble()).toFloat()
        }

        // Tangentes lisses : direction = (next − prev) × facteur
        val cpDx = FloatArray(numArms)
        val cpDy = FloatArray(numArms)
        for (i in 0 until numArms) {
            val prev = (i - 1 + numArms) % numArms
            val next = (i + 1) % numArms
            cpDx[i] = (px[next] - px[prev]) * 0.20f
            cpDy[i] = (py[next] - py[prev]) * 0.20f
        }

        // Tracé cubique fermé
        blobPath.moveTo(px[0], py[0])
        for (i in 0 until numArms) {
            val ni = (i + 1) % numArms
            blobPath.cubicTo(
                px[i] + cpDx[i], py[i] + cpDy[i],
                px[ni] - cpDx[ni], py[ni] - cpDy[ni],
                px[ni], py[ni]
            )
        }
        blobPath.close()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentAnimator?.cancel()
    }
}
