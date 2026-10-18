package com.Atom2Universe.app.games.roulette

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** Étoile vectorielle : seule la palette tourne, la silhouette reste immobile. */
class RainbowStarDrawable : Drawable(), Animatable {
    private val star = Path().apply {
        repeat(10) { index ->
            val angle = Math.toRadians(-90.0 + index * 36.0)
            val radius = if (index % 2 == 0) 43f else 20f
            val x = 50f + cos(angle).toFloat() * radius
            val y = 52f + sin(angle).toFloat() * radius
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val rainbow = SweepGradient(50f, 52f, intArrayOf(
        0xFFFF4545.toInt(), 0xFFFFA52F.toInt(), 0xFFFFEB3B.toInt(),
        0xFF57EB65.toInt(), 0xFF32DFFF.toInt(), 0xFF5264FF.toInt(),
        0xFFCE4FFF.toInt(), 0xFFFF4545.toInt()
    ), null)
    private val rotation = Matrix()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rainbow }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = rainbow
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(25f, 10f, 70f, 92f,
            intArrayOf(0xAAFFFFFF.toInt(), 0x08FFFFFF, 0x33000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        strokeJoin = Paint.Join.ROUND
    }
    private var opacity = 255
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation.setRotate(it.animatedValue as Float, 50f, 52f)
            rainbow.setLocalMatrix(rotation)
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        val save = canvas.save()
        canvas.translate(bounds.exactCenterX() - size / 2f, bounds.exactCenterY() - size / 2f)
        canvas.scale(size / 100f, size / 100f)
        // Traits superposés : halo doux sans bitmap ni flou logiciel.
        for (layer in 3 downTo 1) {
            halo.strokeWidth = layer * 3f
            halo.alpha = opacity * (4 - layer) / 24
            canvas.drawPath(star, halo)
        }
        fill.alpha = opacity
        highlight.alpha = opacity
        rim.alpha = opacity / 2
        canvas.drawPath(star, fill)
        canvas.drawPath(star, highlight)
        canvas.drawPath(star, rim)
        canvas.restoreToCount(save)
    }

    override fun getIntrinsicWidth() = 128
    override fun getIntrinsicHeight() = 128
    override fun setAlpha(alpha: Int) {
        opacity = alpha.coerceIn(0, 255)
        invalidateSelf()
    }
    override fun getAlpha() = opacity
    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        halo.colorFilter = colorFilter
        highlight.colorFilter = colorFilter
        rim.colorFilter = colorFilter
        invalidateSelf()
    }
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun start() {
        if (!animator.isStarted && isVisible && ValueAnimator.areAnimatorsEnabled()) animator.start()
    }
    override fun stop() { animator.cancel() }
    override fun isRunning() = animator.isRunning
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) stop()
        return changed
    }
}
