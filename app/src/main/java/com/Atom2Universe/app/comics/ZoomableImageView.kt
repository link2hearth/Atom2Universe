package com.Atom2Universe.app.comics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ViewLayout { FULL, HALF_TOP, HALF_LEFT, HALF_RIGHT }

    private var bitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var currentScale = 1f
    private val maxScale = 8f
    private var translateX = 0f
    private var translateY = 0f

    private var pendingLayout = ViewLayout.FULL

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private var isScaling = false
    private var lastPanX = 0f
    private var lastPanY = 0f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale = (currentScale * detector.scaleFactor).coerceIn(0.5f, maxScale)
                clampTranslation()
                invalidate()
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.1f) {
                    // Retour au cadrage du layout courant
                    applyLayoutFraming(pendingLayout)
                } else {
                    currentScale = 2.5f
                    clampTranslation()
                }
                invalidate()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                // Swipe page seulement si pas zoomé au-delà du cadrage initial
                if (currentScale <= 1.1f && e1 != null) {
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (abs(dx) > 80 && abs(dx) > abs(dy) && abs(vx) > 200f) {
                        if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                        return true
                    }
                }
                return false
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTap?.invoke()
                return true
            }
        })

    // ── API publique ──────────────────────────────────────────────────────────

    fun setBitmap(bmp: Bitmap?, layout: ViewLayout = ViewLayout.FULL) {
        bitmap = bmp
        pendingLayout = layout
        currentScale = 1f
        translateX = 0f
        translateY = 0f
        if (width > 0 && height > 0) applyLayoutFraming(layout)
        invalidate()
    }

    fun setLayout(layout: ViewLayout) {
        pendingLayout = layout
        currentScale = 1f
        translateX = 0f
        translateY = 0f
        if (width > 0 && height > 0) applyLayoutFraming(layout)
        invalidate()
    }

    // ── Cadrage initial selon le layout ──────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && bitmap != null) applyLayoutFraming(pendingLayout)
    }

    private fun applyLayoutFraming(layout: ViewLayout) {
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return

        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val baseScale = minOf(vw / bw, vh / bh)

        when (layout) {
            ViewLayout.FULL -> {
                currentScale = 1f
                translateX = 0f
                translateY = 0f
            }
            ViewLayout.HALF_LEFT -> {
                // Zoom pour que la moitié gauche remplisse la largeur vue
                currentScale = (2f * vw / (bw * baseScale)).coerceIn(1f, maxScale)
                clampTranslation()
                // Cadrer sur le bord gauche
                val maxTx = maxOf(0f, (bw * baseScale * currentScale - vw) / 2f)
                translateX = maxTx
            }
            ViewLayout.HALF_RIGHT -> {
                currentScale = (2f * vw / (bw * baseScale)).coerceIn(1f, maxScale)
                clampTranslation()
                val maxTx = maxOf(0f, (bw * baseScale * currentScale - vw) / 2f)
                translateX = -maxTx
            }
            ViewLayout.HALF_TOP -> {
                // Zoom pour que la moitié haute remplisse la hauteur vue
                currentScale = (2f * vh / (bh * baseScale)).coerceIn(1f, maxScale)
                clampTranslation()
                val maxTy = maxOf(0f, (bh * baseScale * currentScale - vh) / 2f)
                translateY = maxTy
            }
        }
        invalidate()
    }

    // ── Dessin ────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return

        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val baseScale = minOf(vw / bw, vh / bh)
        val scaledW = bw * baseScale * currentScale
        val scaledH = bh * baseScale * currentScale
        val dx = (vw - scaledW) / 2f + translateX
        val dy = (vh - scaledH) / 2f + translateY

        drawMatrix.reset()
        drawMatrix.postScale(baseScale * currentScale, baseScale * currentScale)
        drawMatrix.postTranslate(dx, dy)
        canvas.drawBitmap(bmp, drawMatrix, paint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPanX = event.x
                lastPanY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling) {
                    translateX += event.x - lastPanX
                    translateY += event.y - lastPanY
                    clampTranslation()
                    invalidate()
                }
                lastPanX = event.x
                lastPanY = event.y
            }
        }
        return true
    }

    private fun clampTranslation() {
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return
        val baseScale = minOf(vw / bmp.width, vh / bmp.height)
        val scaledW = bmp.width * baseScale * currentScale
        val scaledH = bmp.height * baseScale * currentScale
        val maxTx = maxOf(0f, (scaledW - vw) / 2f)
        val maxTy = maxOf(0f, (scaledH - vh) / 2f)
        translateX = translateX.coerceIn(-maxTx, maxTx)
        translateY = translateY.coerceIn(-maxTy, maxTy)
    }
}
