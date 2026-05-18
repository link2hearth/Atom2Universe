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

    private var lastPanX = 0f
    private var lastPanY = 0f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale = (currentScale * detector.scaleFactor).coerceIn(0.5f, maxScale)
                clampTranslation()
                invalidate()
                return true
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
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) <= 140 || abs(dx) <= 3f * abs(dy) || abs(vx) <= 600f) return false
                // Autoriser le changement de page si on est au bord de l'image dans la direction du swipe
                val bmp = bitmap ?: return false
                val scaledW = bmp.width * minOf(width / bmp.width.toFloat(), height / bmp.height.toFloat()) * currentScale
                val maxTx = maxOf(0f, (scaledW - width) / 2f)
                val atRightEdge = translateX <= -maxTx + 2f
                val atLeftEdge  = translateX >= maxTx - 2f
                return when {
                    dx < 0 && atRightEdge -> { onSwipeLeft?.invoke(); true }
                    dx > 0 && atLeftEdge  -> { onSwipeRight?.invoke(); true }
                    else -> false
                }
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
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastPanX = centroidX(event)
                lastPanY = centroidY(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val cx = centroidX(event)
                val cy = centroidY(event)
                translateX += cx - lastPanX
                translateY += cy - lastPanY
                clampTranslation()
                invalidate()
                lastPanX = cx
                lastPanY = cy
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Recalculer sans le doigt qui se lève pour éviter un saut
                val lifted = event.actionIndex
                var sx = 0f; var sy = 0f; var n = 0
                for (i in 0 until event.pointerCount) {
                    if (i != lifted) { sx += event.getX(i); sy += event.getY(i); n++ }
                }
                if (n > 0) { lastPanX = sx / n; lastPanY = sy / n }
            }
        }
        return true
    }

    private fun centroidX(event: MotionEvent): Float {
        var s = 0f; repeat(event.pointerCount) { s += event.getX(it) }; return s / event.pointerCount
    }

    private fun centroidY(event: MotionEvent): Float {
        var s = 0f; repeat(event.pointerCount) { s += event.getY(it) }; return s / event.pointerCount
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
