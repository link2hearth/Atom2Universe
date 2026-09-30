package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/**
 * La toile du canvas infini. Un doigt dessine (ou gomme, ou réaligne la couche du dessous selon
 * l'outil) ; deux doigts déplacent et zooment. Tout le calcul est fait par [ZoomScene] et
 * [ZoomRenderer] : la vue ne reçoit que de petites coordonnées d'écran, déjà découpées.
 */
class ZoomCanvasView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Tool { PEN, ERASER, HAND, MOVE_LAYER }

    interface Listener {
        fun onContentChanged()
        fun onCameraChanged()
        fun onNothingToMove()
    }

    var scene: ZoomScene? = null
        set(value) { field = value; invalidate() }
    var listener: Listener? = null
    var tool = Tool.PEN
    var color = Color.BLACK
    /** Épaisseur du crayon, en pixels à l'écran au moment du trait. */
    var penWidthPx = 6f
    var paperColor = 0xFFFAF8F3.toInt()

    private val density = resources.displayMetrics.density
    private val eraserRadiusPx = 14f * density

    private val list = RenderList()
    private val path = Path()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x99000000.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(paperColor)
        val s = scene ?: return
        draw(s, canvas, width.toDouble(), height.toDouble())
        if (mode == Mode.ERASE) canvas.drawCircle(lastX, lastY, eraserRadiusPx, cursorPaint)
    }

    private fun draw(s: ZoomScene, canvas: Canvas, w: Double, h: Double) {
        ZoomRenderer.build(s, w, h, list)
        val c = list.coords
        for (r in 0 until list.runCount) {
            val base = list.runColor[r]
            val a = ((base ushr 24) * list.runAlpha[r]).toInt().coerceIn(0, 255)
            if (a == 0) continue
            val argb = (a shl 24) or (base and 0xFFFFFF)
            val start = list.runStart[r]
            val n = list.runPoints[r]
            val width = list.runWidth[r]
            if (n == 1) {
                dotPaint.color = argb
                canvas.drawCircle(c[start], c[start + 1], width / 2, dotPaint)
                continue
            }
            strokePaint.color = argb
            strokePaint.strokeWidth = width
            path.rewind()
            path.moveTo(c[start], c[start + 1])
            for (k in 1 until n) path.lineTo(c[start + 2 * k], c[start + 2 * k + 1])
            canvas.drawPath(path, strokePaint)
        }
    }

    /** Une vignette carrée de ce qu'on voit (pour la galerie). */
    fun thumbnail(size: Int = 320): Bitmap? {
        val s = scene ?: return null
        if (width == 0 || height == 0) return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(paperColor)
        val side = minOf(width, height).toFloat()
        val k = size / side
        canvas.scale(k, k)
        canvas.translate(-(width - side) / 2f, -(height - side) / 2f)
        draw(s, canvas, width.toDouble(), height.toDouble())
        return bmp
    }

    // ---- Gestes --------------------------------------------------------------------------

    private enum class Mode { NONE, DRAW, ERASE, PAN, MOVE_LAYER, GESTURE, IGNORE }

    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var pinchCx = 0f
    private var pinchCy = 0f
    private var pinchDist = 0f
    private var pinchSkip = -1

    private fun ox(x: Float) = (x - width / 2f).toDouble()
    private fun oy(y: Float) = (y - height / 2f).toDouble()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = scene ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                downTime = e.eventTime
                parent?.requestDisallowInterceptTouchEvent(true)
                mode = when (tool) {
                    Tool.PEN -> { s.beginStroke(ox(e.x), oy(e.y), color, penWidthPx.toDouble()); Mode.DRAW }
                    Tool.ERASER -> { s.beginErase(); eraseAt(s, e.x, e.y); Mode.ERASE }
                    Tool.HAND -> Mode.PAN
                    Tool.MOVE_LAYER -> if (s.beginMoveLayer()) Mode.MOVE_LAYER else { listener?.onNothingToMove(); Mode.IGNORE }
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (mode) {
                    // Un deuxième doigt tout de suite : c'était un pincement, pas un trait.
                    Mode.DRAW -> if (e.eventTime - downTime < 350) s.cancelStroke() else finishStroke(s)
                    Mode.ERASE -> finishErase(s)
                    Mode.MOVE_LAYER -> finishMove(s)
                    else -> Unit
                }
                if (mode != Mode.IGNORE || tool == Tool.MOVE_LAYER) {
                    mode = Mode.GESTURE
                    startPinch(e)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.DRAW -> {
                    for (h in 0 until e.historySize) s.extendStroke(ox(e.getHistoricalX(h)), oy(e.getHistoricalY(h)))
                    s.extendStroke(ox(e.x), oy(e.y))
                    invalidate()
                }
                Mode.ERASE -> {
                    for (h in 0 until e.historySize) eraseAt(s, e.getHistoricalX(h), e.getHistoricalY(h))
                    eraseAt(s, e.x, e.y)
                    lastX = e.x; lastY = e.y
                    invalidate()
                }
                Mode.PAN -> {
                    s.pan((e.x - lastX).toDouble(), (e.y - lastY).toDouble())
                    lastX = e.x; lastY = e.y
                    cameraMoved()
                }
                Mode.MOVE_LAYER -> {
                    s.moveLayerBy((e.x - lastX).toDouble(), (e.y - lastY).toDouble())
                    lastX = e.x; lastY = e.y
                    invalidate()
                }
                Mode.GESTURE -> movePinch(s, e)
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.GESTURE) {
                    if (e.pointerCount - 1 < 2) mode = Mode.IGNORE else startPinch(e, skip = e.actionIndex)
                }
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.DRAW -> finishStroke(s)
                    Mode.ERASE -> finishErase(s)
                    Mode.MOVE_LAYER -> finishMove(s)
                    else -> Unit
                }
                mode = Mode.NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                when (mode) {
                    Mode.DRAW -> s.cancelStroke()
                    Mode.ERASE -> finishErase(s)
                    Mode.MOVE_LAYER -> finishMove(s)
                    else -> Unit
                }
                mode = Mode.NONE
                invalidate()
            }
        }
        return true
    }

    /** Molette (souris, Chromebook) : zoome autour du curseur. */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val s = scene ?: return false
        if (e.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && e.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) {
                s.zoomAt(1.2.pow(v.toDouble()), ox(e.x), oy(e.y))
                cameraMoved()
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    private fun eraseAt(s: ZoomScene, x: Float, y: Float) {
        s.eraseAt(ox(x), oy(y), eraserRadiusPx.toDouble())
        lastX = x; lastY = y
    }

    private fun finishStroke(s: ZoomScene) {
        if (s.endStroke() != null) listener?.onContentChanged()
    }

    private fun finishErase(s: ZoomScene) {
        val before = s.contentVersion
        s.endErase()
        if (s.contentVersion != before) listener?.onContentChanged()
    }

    private fun finishMove(s: ZoomScene) {
        s.endMoveLayer()
        listener?.onContentChanged()
    }

    private fun cameraMoved() {
        invalidate()
        listener?.onCameraChanged()
    }

    private fun startPinch(e: MotionEvent, skip: Int = -1) {
        val pts = (0 until e.pointerCount).filter { it != skip }
        pinchSkip = skip
        if (pts.isEmpty()) return
        pinchCx = pts.map { e.getX(it) }.average().toFloat()
        pinchCy = pts.map { e.getY(it) }.average().toFloat()
        pinchDist = if (pts.size >= 2) hypot(e.getX(pts[0]) - e.getX(pts[1]), e.getY(pts[0]) - e.getY(pts[1])) else 0f
    }

    private fun movePinch(s: ZoomScene, e: MotionEvent) {
        val pts = (0 until e.pointerCount).filter { it != pinchSkip }
        if (pts.size < 2) return
        val cx = (e.getX(pts[0]) + e.getX(pts[1])) / 2f
        val cy = (e.getY(pts[0]) + e.getY(pts[1])) / 2f
        val d = hypot(e.getX(pts[0]) - e.getX(pts[1]), e.getY(pts[0]) - e.getY(pts[1]))
        s.pan((cx - pinchCx).toDouble(), (cy - pinchCy).toDouble())
        if (pinchDist > 0f && d > 0f) s.zoomAt((d / pinchDist).toDouble(), ox(cx), oy(cy))
        pinchCx = cx; pinchCy = cy; pinchDist = max(d, 1f)
        cameraMoved()
    }
}
