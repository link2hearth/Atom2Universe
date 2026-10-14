package com.Atom2Universe.app.games.hamlet

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * The game's feel: a button sinks under the finger, gold buttons catch the light, a changed number
 * swells, a page slides into place. All of it stops with the game's "effects" setting and with
 * Android's "remove animations".
 */
internal object HamletMotion {
    /** Follows the game's "effects" setting. */
    var enabled = true
    val on get() = enabled && ValueAnimator.areAnimatorsEnabled()
    private val spring = OvershootInterpolator(2.2f)
    private val settle = DecelerateInterpolator(1.6f)

    /** The view sinks a little while pressed and springs back; the click itself is untouched. */
    fun press(view: View) {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (on && v.isEnabled) v.animate().scaleX(.94f).scaleY(.94f).setStartDelay(0).setDuration(70).setInterpolator(settle).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    if (v.scaleX != 1f) v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(260).setInterpolator(spring).start()
            }
            false
        }
    }

    /** A quick swell: a number that just changed, a tab just chosen. */
    fun pop(view: View, from: Float = 1.16f) {
        if (!on || !view.isAttachedToWindow) return
        view.animate().cancel()
        view.scaleX = from; view.scaleY = from
        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(340).setInterpolator(spring).start()
    }

    /** A page or a card slides up into place. */
    fun enter(view: View, delay: Long = 0) {
        view.animate().cancel()
        if (!on) { view.alpha = 1f; view.translationY = 0f; return }
        view.alpha = 0f; view.translationY = HamletUi.dp(view.context, 14).toFloat()
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(220).setInterpolator(settle).start()
    }

    /** A popup grows out of the screen centre. */
    fun open(view: View) {
        if (!on) return
        view.alpha = 0f; view.scaleX = .9f; view.scaleY = .9f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(220).setInterpolator(spring).start()
    }

    /** Time since boot in seconds, for drawings that breathe. */
    fun seconds() = SystemClock.uptimeMillis() / 1000f

    /**
     * A band of light sweeps across a gold button every few seconds. Set as the button's foreground:
     * it only runs while the button is visible and enabled, and idles between sweeps.
     */
    class Shine(private val radius: Float) : Drawable(), Runnable {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()
        private val clip = Path()
        private val box = RectF()
        private var shader: Shader? = null
        private var active = true

        override fun onBoundsChange(bounds: Rect) {
            box.set(bounds); clip.reset(); clip.addRoundRect(box, radius, radius, Path.Direction.CW)
            val band = bounds.height() * .7f
            shader = LinearGradient(-band, 0f, band, 0f, intArrayOf(0x00FFFFFF, 0x80FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                .also { paint.shader = it }
        }
        override fun draw(canvas: Canvas) {
            val gradient = shader ?: return
            if (!on || !active || !isVisible || bounds.isEmpty) return
            val now = SystemClock.uptimeMillis()
            val phase = now % PERIOD
            unscheduleSelf(this)
            if (phase < SWEEP) {
                val t = phase / SWEEP.toFloat()
                val reach = bounds.width() + bounds.height() * 2f
                matrix.setRotate(-24f)
                matrix.postTranslate(bounds.left - bounds.height() + t * reach, bounds.exactCenterY())
                gradient.setLocalMatrix(matrix)
                canvas.save(); canvas.clipPath(clip); canvas.drawRect(bounds, paint); canvas.restore()
                scheduleSelf(this, now + 16)
            } else scheduleSelf(this, now - phase + PERIOD)
        }
        override fun run() = invalidateSelf()
        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val changed = super.setVisible(visible, restart)
            if (visible) invalidateSelf() else unscheduleSelf(this)
            return changed
        }
        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean {
            val enabled = android.R.attr.state_enabled in state
            if (enabled == active) return false
            active = enabled
            if (!enabled) unscheduleSelf(this)
            invalidateSelf(); return true
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

        private companion object {
            const val PERIOD = 3800L
            const val SWEEP = 760L
        }
    }
}
