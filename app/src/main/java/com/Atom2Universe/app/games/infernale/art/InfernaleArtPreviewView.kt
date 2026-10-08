package com.Atom2Universe.app.games.infernale.art

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.sin

/** Planche animée de direction artistique, à placer dans un ScrollView pour les petits écrans. */
class InfernaleArtPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val art = InfernalePartArt()
    private val bounds = RectF()
    private val clip = android.graphics.Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var elapsed = 0f
    private var lastFrame = 0L
    private val examples = infernalePartLabels.toList()
    private val labels = examples.map { context.getString(it.second) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (360 * density).toInt()
        val w = resolveSize(desiredWidth, widthMeasureSpec)
        val columns = if (w >= 500 * density) 4 else 3
        val h = (80 * density + ((examples.size + columns - 1) / columns) * (w / columns + 30 * density)).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = android.os.SystemClock.uptimeMillis()
        if (lastFrame != 0L) elapsed += ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
        lastFrame = now
        canvas.drawColor(InfernalePartArt.BACKGROUND)
        paint.color = 0xFF59637D.toInt(); paint.textAlign = Paint.Align.CENTER
        paint.textSize = 24 * density
        canvas.drawText(context.getString(R.string.infernale_art_title), width / 2f, 35 * density, paint)
        val columns = if (width >= 500 * density) 4 else 3
        val cell = width.toFloat() / columns
        val wave = (sin(elapsed * 2f) + 1f) / 2f
        val state = InfernalePartArt.State(angle = elapsed, travel = wave, level = wave, active = wave > 0.5f, phase = elapsed)
        canvas.getClipBounds(clip)
        for ((index, entry) in examples.withIndex()) {
            val left = index % columns * cell
            val top = 65 * density + index / columns * (cell + 30 * density)
            if (top > clip.bottom || top + cell + 30 * density < clip.top) continue
            // Taille entière du pixel : les détails ne changent pas d'épaisseur.
            val spriteSize = (((cell - 12 * density) / 64).toInt().coerceAtLeast(1) * 64).toFloat()
            bounds.set(left + (cell - spriteSize) / 2, top, left + (cell + spriteSize) / 2, top + spriteSize)
            art.draw(canvas, entry.first, bounds, state)
            paint.textSize = 12 * density
            paint.textSize = minOf(paint.textSize, paint.textSize * (cell - 8 * density) / paint.measureText(labels[index]).coerceAtLeast(1f))
            canvas.drawText(labels[index], left + cell / 2, top + cell + 8 * density, paint)
        }
        if (isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        lastFrame = 0L
        if (isVisible) invalidate()
    }

    override fun onDetachedFromWindow() {
        lastFrame = 0L
        super.onDetachedFromWindow()
    }
}

