package com.Atom2Universe.app.graphics

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi

/** Soleil procédural : convection continue du plasma, sans séquence à remettre à zéro. */
@RequiresApi(33)
internal class NativeSunDrawable : Drawable(), Animatable {
    private val shader = RuntimeShader(SunPlasmaShader.agsl)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@NativeSunDrawable.shader }
    private var animationRequested = false
    private val animation = SunAnimationState()
    private var lastFrame = 0L
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val now = SystemClock.uptimeMillis()
            // Le plasma bouge lentement : 30 images/s suffisent, même sur un écran 120 Hz.
            // L'horloge réelle avance d'autant à chaque image retenue, la vitesse ne change pas.
            if (now - lastFrame < MIN_FRAME_MS) return@addUpdateListener
            animation.advance(now - lastFrame)
            lastFrame = now
            shader.setFloatUniform("phase", animation.phase)
            shader.setFloatUniform("eruptionAge", animation.eruptionAge)
            shader.setFloatUniform("eruptionShape", animation.shape)
            shader.setFloatUniform("eruptionDetail", animation.detail)
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        shader.setFloatUniform("size", bounds.width().toFloat(), bounds.height().toFloat())
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), paint)
        canvas.restoreToCount(saved)
    }

    override fun start() {
        animationRequested = true
        updateAnimation()
    }

    override fun stop() {
        animationRequested = false
        animator.cancel()
    }

    private fun updateAnimation() {
        if (animationRequested && isVisible && ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted) {
                lastFrame = SystemClock.uptimeMillis()
                animator.start()
            }
        } else {
            animator.cancel()
        }
    }

    override fun isRunning() = animator.isRunning
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        // ImageView peut changer la visibilité après onResume, lors de son attachement.
        // Conserver la demande d'animation pour redémarrer quand il redevient visible.
        updateAnimation()
        return changed
    }
    override fun getIntrinsicWidth() = 512
    override fun getIntrinsicHeight() = 512
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun getAlpha() = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        // Un peu sous 33 ms : quatre images à 120 Hz, trois à 90 Hz ou deux à 60 Hz passent toujours.
        const val MIN_FRAME_MS = 30L
    }
}
