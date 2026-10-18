package com.Atom2Universe.app.audio

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Décor dans l'espace réellement libre : ne participe ni au défilement ni aux interactions. */
class AudioHubAmbience(private val recycler: RecyclerView) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val density = recycler.resources.displayMetrics.density
    private val colors = intArrayOf(0xFFB59AFF.toInt(), 0xFF66D6EF.toInt(),
        0xFFEABD85.toInt(), 0xFF74D7BE.toInt(), 0xFFDD8FAB.toInt())
    private var active = false
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 24000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            recycler.invalidate()
        }
    }

    fun setActive(value: Boolean) {
        active = value
        if (!value) animator.cancel()
        recycler.invalidate()
    }

    fun dispose() {
        animator.cancel()
        animator.removeAllUpdateListeners()
        recycler.removeItemDecoration(this)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        // Le décor disparaît si la liste défile, ou si toutes les tuiles ne tiennent pas à l'écran.
        val count = parent.adapter?.itemCount ?: 0
        val last = parent.findViewHolderForAdapterPosition(count - 1)?.itemView
        if (count == 0 || last == null || parent.canScrollVertically(1) || parent.canScrollVertically(-1)) {
            animator.cancel()
            return
        }
        var bottom = 0f
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            bottom = maxOf(bottom, child.bottom + child.translationY)
        }
        val top = bottom + 24f * density
        val available = parent.height - parent.paddingBottom - top
        if (available < 140f * density) {
            animator.cancel()
            return
        }
        if (active && ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }

        canvas.save()
        canvas.clipRect(0f, top, parent.width.toFloat(), top + available)
        canvas.translate(0f, top)
        val w = parent.width.toFloat()
        val h = available
        val unit = min(w / 620f, h / 370f)

        // Deux portées souples traversent l'espace, comme des rubans de musique.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .8f * density
        for (band in 0..1) {
            paint.color = if (band == 0) 0x304FADC6 else 0x307C6DA9
            for (line in 0..4) {
                path.reset()
                val offset = (line - 2) * 8f * unit
                for (step in 0..80) {
                    val x = w * step / 80f
                    val y = staffY(x / w, h, band) + offset
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
        }
        paint.style = Paint.Style.FILL
        for (i in 0 until 22) {
            val band = i % 2
            val position = .07f + .86f * ((i * 7 % 22) / 21f)
            val x = position * w + sin(phase + i * 1.9f) * 9f * unit
            val bounce = sin(phase * 2f + i * 1.7f)
            val y = staffY(position, h, band) - (18f + 25f * bounce) * unit
            val size = (10f + (i % 4) * 2.2f) * unit
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(sin(phase + i) * 9f)
            paint.color = colors[i % colors.size]
            paint.alpha = 125 + (i % 4) * 25
            drawNote(canvas, size, i % 3 == 0)
            canvas.restore()
        }
        // Quelques points lumineux, discrets et sans allocation par image.
        for (i in 0 until 18) {
            paint.color = colors[i % colors.size]
            paint.alpha = (35 + 30 * (1f + sin(phase + i))).toInt()
            val x = w * (.06f + .88f * (i * 11 % 19) / 18f)
            val y = h * (.16f + .68f * (i * 7 % 17) / 16f) + cos(phase + i) * 8f * unit
            canvas.drawCircle(x, y, (1.1f + i % 2) * unit, paint)
        }
        paint.alpha = 255
        canvas.restore()
    }

    private fun staffY(x: Float, h: Float, band: Int): Float =
        h * (.38f + band * .29f) + sin(x * (2 * PI).toFloat() + band * 2f + phase) * h * .09f

    private fun drawNote(canvas: Canvas, size: Float, paired: Boolean) {
        canvas.save()
        canvas.rotate(-22f)
        canvas.drawOval(-size * .65f, -size * .35f, size * .65f, size * .35f, paint)
        canvas.restore()
        val stem = size * .5f
        canvas.drawRect(stem - size * .15f, -size * 2.6f, stem, 0f, paint)
        path.reset()
        path.moveTo(stem, -size * 2.6f)
        if (paired) {
            path.lineTo(stem + size * 1.6f, -size * 2.9f)
            path.lineTo(stem + size * 1.6f, -size * 2.5f)
            path.lineTo(stem, -size * 2.2f)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawRect(stem + size * 1.45f, -size * 2.9f, stem + size * 1.6f, -size * .3f, paint)
            canvas.drawOval(size * 1.0f, -size * .65f, size * 2.2f, size * .05f, paint)
        } else {
            path.cubicTo(size * 1.8f, -size * 1.9f, size * 1.6f, -size * 1.5f, size, -size)
            path.cubicTo(size * 1.3f, -size * 1.8f, size * .9f, -size * 1.7f, stem, -size * 1.9f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
