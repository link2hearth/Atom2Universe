package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

class TurtleSpriteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val FRAME_W = 48
        private const val FRAME_H = 16
        private const val FRAME_COUNT = 6
        private const val FRAME_DURATION_MS = 500L
    }

    private val paint = Paint().apply { isAntiAlias = false }

    private var frameIndex = 0
    private var lastFrameAt = 0L

    private val choreographer = Choreographer.getInstance()
    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = System.currentTimeMillis()
            if (lastFrameAt == 0L) lastFrameAt = now
            while (now - lastFrameAt >= FRAME_DURATION_MS) {
                frameIndex = (frameIndex + 1) % FRAME_COUNT
                lastFrameAt += FRAME_DURATION_MS
            }
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        choreographer.postFrameCallback(tick)
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        ClickerAnimalArt.turtle.draw(canvas, paint, frameIndex, width, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(
            (FRAME_H * resources.displayMetrics.density * 2).toInt()
        )
        val w = h * FRAME_W / FRAME_H
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }
}
