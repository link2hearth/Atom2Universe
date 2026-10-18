package com.Atom2Universe.app.science.bestiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.Atom2Universe.app.R
import java.io.Closeable

/** A lazy offline illustration, safe when RecyclerView rebinds a thumbnail during decoding. */
class AnimalPlateView(context: Context, variant: String = "lion") : View(context) {
    var variant: String = variant
        set(value) { if (field != value) { field = value; reload() } }
    private val store = AnimalImageStore.get(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var image: Bitmap? = null
    private var request: Closeable? = null
    private var generation = 0
    private var failed = false

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    private fun reload() {
        request?.close(); request = null
        val token = ++generation
        val id = variant
        image = store.peek(id); failed = false; invalidate()
        if (!isAttachedToWindow || image != null) return
        request = store.request(id) { bitmap ->
            if (generation == token && variant == id && isAttachedToWindow) {
                image = bitmap; failed = bitmap == null; request = null; invalidate()
            }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); reload() }
    override fun onDetachedFromWindow() {
        generation++; request?.close(); request = null; image = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.WHITE)
        image?.let { AnimalImageStore.drawFit(canvas, it, width.toFloat(), height.toFloat(), paint); return }
        paint.color = 0xff777565.toInt(); paint.textSize = 12 * resources.displayMetrics.scaledDensity
        paint.textAlign = Paint.Align.CENTER
        val label = context.getString(if (failed) R.string.best_plate_unavailable else R.string.best_plate_loading)
        val available = (width - 16 * resources.displayMetrics.density).coerceAtLeast(1f)
        val measured = paint.measureText(label)
        if (measured > available) paint.textSize *= available / measured
        canvas.drawText(label, width / 2f, (height - paint.ascent() - paint.descent()) / 2f, paint)
    }
}
