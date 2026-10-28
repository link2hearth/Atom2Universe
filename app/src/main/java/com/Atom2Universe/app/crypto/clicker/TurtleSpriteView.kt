package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.Atom2Universe.app.crypto.VisibleWindowGate

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

    // Un redessin par image du sprite, pas à chaque rafraîchissement d'écran.
    private val advance = object : Runnable {
        override fun run() {
            frameIndex = (frameIndex + 1) % FRAME_COUNT
            invalidate()
            postDelayed(this, FRAME_DURATION_MS)
        }
    }

    private val gate = VisibleWindowGate(this) { active ->
        removeCallbacks(advance)
        if (active) postDelayed(advance, FRAME_DURATION_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        gate.update()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) gate.update()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) gate.update()
    }

    override fun onDetachedFromWindow() {
        gate.detach()
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
