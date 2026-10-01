package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * La toile du canvas infini. Un doigt dessine, gomme, manipule une image ou réaligne la couche du
 * dessous selon l'outil ; deux doigts déplacent et zooment (déplacent seulement quand le zoom est
 * bloqué, [zoomLocked]). Tout le calcul est fait par [ZoomScene]
 * et [ZoomRenderer] : la vue ne reçoit que de petites coordonnées d'écran, déjà découpées.
 *
 * La gomme dessine de la transparence : un coup de gomme est tracé en mode « destination out », qui
 * retire ce qui est déjà tracé dessous. Une couche qui en contient est donc composée dans un calque
 * à part : la gomme n'y creuse qu'elle, et la couche du dessous apparaît à travers le trou.
 *
 * Le pinceau s'affine aux bouts : il est tracé comme une forme pleine (un disque par point, un
 * trapèze par segment), remplie d'un coup pour que ses morceaux qui se chevauchent ne foncent pas.
 * Le feutre se multiplie avec ce qu'il recouvre.
 */
class ZoomCanvasView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Tool { PEN, BRUSH, MARKER, ERASER, SELECT, HAND, MOVE_LAYER }

    /** Noms volontairement distincts de ceux d'Activity (onContentChanged y est déjà pris). */
    interface Listener {
        fun onDrawingChanged()
        fun onViewMoved()
        fun onNothingToMove()
        fun onImageSelectionChanged()
    }

    var scene: ZoomScene? = null
        set(value) { field = value; selectedImage = null; invalidate() }
    var listener: Listener? = null
    var tool = Tool.PEN
        set(value) {
            field = value
            if (value != Tool.SELECT) selectImage(null)
            invalidate()
        }
    var color = Color.BLACK
    /**
     * Épaisseur de l'outil de dessin courant (crayon, pinceau, feutre ou gomme), en pixels d'écran
     * au moment du trait (le trait grossit ensuite avec le zoom).
     */
    var strokeSize = 6f
    /** Zoom bloqué : deux doigts ne font plus que déplacer la vue, la molette ne fait rien. */
    var zoomLocked = false
    var paperColor = 0xFFFAF8F3.toInt()

    /** Fournit le bitmap d'une image du projet (null tant qu'il se charge : un cadre gris en attendant). */
    var imageProvider: ((String) -> Bitmap?)? = null

    /** L'image sélectionnée avec l'outil de sélection (dans la couche de travail), ou null. */
    var selectedImage: Long? = null
        private set

    private val density = resources.displayMetrics.density
    private val handleRadius = 9f * density
    private val handleReach = 28f * density

    private val list = RenderList()
    private val path = Path()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val eraseDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY
    }
    private val markerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY
    }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = 0x22000000 }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x99000000.toInt()
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFF4C8DFF.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val srcRect = Rect()
    private val dstRect = RectF()

    fun selectImage(id: Long?) {
        if (selectedImage == id) return
        selectedImage = id
        invalidate()
        listener?.onImageSelectionChanged()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(paperColor)
        val s = scene ?: return
        draw(s, canvas, width.toDouble(), height.toDouble())
        if (mode == Mode.ERASE) canvas.drawCircle(lastX, lastY, strokeSize / 2, cursorPaint)
        drawSelection(s, canvas)
    }

    private fun draw(s: ZoomScene, canvas: Canvas, w: Double, h: Double) {
        ZoomRenderer.build(s, w, h, list)
        for (g in 0 until list.groupCount) {
            val from = list.groupStart[g]
            val to = list.groupEnd[g]
            if (!list.groupIsolated[g]) {
                drawRuns(canvas, from, to)
                continue
            }
            // Une couche à part : ses coups de gomme ne creusent qu'elle, et une couche qui s'efface
            // est posée d'un bloc avec son opacité (deux de ses traits qui se croisent ne foncent pas).
            val alpha = (list.groupAlpha[g] * 255).toInt().coerceIn(0, 255)
            val saved = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), alpha)
            drawRuns(canvas, from, to)
            canvas.restoreToCount(saved)
        }
    }

    private fun drawRuns(canvas: Canvas, from: Int, to: Int) {
        val c = list.coords
        for (r in from until to) {
            val img = list.runImage[r]
            if (img >= 0) {
                drawImage(canvas, img, list.runAlpha[r])
                continue
            }
            val erase = list.runErase[r]
            val kind = list.runKind[r].toInt()
            val base = if (erase) Color.BLACK else list.runColor[r]
            val a = ((base ushr 24) * list.runAlpha[r]).toInt().coerceIn(0, 255)
            if (a == 0) continue
            val argb = (a shl 24) or (base and 0xFFFFFF)
            val start = list.runStart[r]
            val n = list.runPoints[r]
            val width = list.runWidth[r]
            if (n == 1) {
                val dot = when {
                    erase -> eraseDotPaint
                    kind == Stroke.MARKER -> markerDotPaint
                    else -> dotPaint
                }
                dot.color = argb
                canvas.drawCircle(c[start], c[start + 1], width / 2, dot)
                continue
            }
            if (kind == Stroke.BRUSH) {
                drawBrush(canvas, start, n, argb)
                continue
            }
            val paint = when {
                erase -> erasePaint
                kind == Stroke.MARKER -> markerPaint
                else -> strokePaint
            }
            paint.color = argb
            paint.strokeWidth = width
            path.rewind()
            path.moveTo(c[start], c[start + 1])
            for (k in 1 until n) path.lineTo(c[start + 2 * k], c[start + 2 * k + 1])
            canvas.drawPath(path, paint)
        }
    }

    /**
     * Un trait de pinceau : un disque par point et un trapèze par segment, tous dans le même sens de
     * parcours, remplis d'un seul coup (leur union, sans double opacité là où ils se chevauchent).
     * Les points plus serrés qu'une demi-épaisseur sont sautés : la forme n'en change pas.
     */
    private fun drawBrush(canvas: Canvas, start: Int, n: Int, argb: Int) {
        val c = list.coords
        val pw = list.pointWidth
        val first = start / 2
        path.rewind()
        var px = c[start]
        var py = c[start + 1]
        var pr = pw[first] / 2
        path.addCircle(px, py, pr, Path.Direction.CCW)
        for (k in 1 until n) {
            val x = c[start + 2 * k]
            val y = c[start + 2 * k + 1]
            val r = pw[first + k] / 2
            val dx = x - px
            val dy = y - py
            val len = hypot(dx, dy)
            if (k < n - 1 && len < max(1f, min(pr, r))) continue
            if (len > 0f) {
                // Le trapèze tangent aux deux disques, parcouru dans le même sens qu'eux.
                val nx = -dy / len
                val ny = dx / len
                path.moveTo(px + nx * pr, py + ny * pr)
                path.lineTo(x + nx * r, y + ny * r)
                path.lineTo(x - nx * r, y - ny * r)
                path.lineTo(px - nx * pr, py - ny * pr)
                path.close()
            }
            path.addCircle(x, y, r, Path.Direction.CCW)
            px = x; py = y; pr = r
        }
        brushPaint.color = argb
        canvas.drawPath(path, brushPaint)
    }

    private fun drawImage(canvas: Canvas, i: Int, alpha: Float) {
        val d = list.imageDst
        dstRect.set(d[4 * i], d[4 * i + 1], d[4 * i + 2], d[4 * i + 3])
        val bmp = list.imageKeys[i]?.let { imageProvider?.invoke(it) }
        if (bmp == null) {
            canvas.drawRect(dstRect, placeholderPaint)
            return
        }
        // Le bitmap chargé peut être plus petit que l'image d'origine : on met le rectangle source à son échelle.
        val kx = bmp.width.toFloat() / list.imagePx[2 * i]
        val ky = bmp.height.toFloat() / list.imagePx[2 * i + 1]
        val sr = list.imageSrc
        srcRect.set(
            (sr[4 * i] * kx).toInt(), (sr[4 * i + 1] * ky).toInt(),
            kotlin.math.ceil(sr[4 * i + 2] * kx).toInt().coerceAtMost(bmp.width),
            kotlin.math.ceil(sr[4 * i + 3] * ky).toInt().coerceAtMost(bmp.height),
        )
        if (srcRect.width() <= 0 || srcRect.height() <= 0) return
        imagePaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, srcRect, dstRect, imagePaint)
    }

    /** Cadre et poignées de l'image sélectionnée, ramenés au bord de l'écran s'ils en sortent. */
    private fun drawSelection(s: ZoomScene, canvas: Canvas) {
        val id = selectedImage ?: return
        val item = s.image(id)
        if (item == null) { selectImage(null); return }
        val r = screenRect(s, id) ?: return
        val m = 4.0 * handleReach
        val x0 = r[0].coerceIn(-m, width + m).toFloat()
        val y0 = r[1].coerceIn(-m, height + m).toFloat()
        val x1 = r[2].coerceIn(-m, width + m).toFloat()
        val y1 = r[3].coerceIn(-m, height + m).toFloat()
        canvas.drawRect(x0, y0, x1, y1, framePaint)
        for ((hx, hy) in listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)) {
            canvas.drawCircle(hx, hy, handleRadius, handlePaint)
            canvas.drawCircle(hx, hy, handleRadius, framePaint)
        }
    }

    /** Rectangle de l'image [id] en pixels de la vue (Double, pas encore borné). */
    private fun screenRect(s: ZoomScene, id: Long): DoubleArray? {
        val item = s.image(id) ?: return null
        val r = s.imageScreenRect(item)
        return doubleArrayOf(r[0] + width / 2.0, r[1] + height / 2.0, r[2] + width / 2.0, r[3] + height / 2.0)
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

    private enum class Mode { NONE, DRAW, ERASE, PAN, MOVE_LAYER, IMAGE_MOVE, IMAGE_RESIZE, GESTURE, IGNORE }

    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var resizeCorner = 0
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
                    Tool.PEN -> { s.beginStroke(ox(e.x), oy(e.y), color, strokeSize.toDouble(), Stroke.PEN); Mode.DRAW }
                    Tool.BRUSH -> { s.beginStroke(ox(e.x), oy(e.y), color, strokeSize.toDouble(), Stroke.BRUSH); Mode.DRAW }
                    Tool.MARKER -> { s.beginStroke(ox(e.x), oy(e.y), color, strokeSize.toDouble(), Stroke.MARKER); Mode.DRAW }
                    Tool.ERASER -> { s.beginStroke(ox(e.x), oy(e.y), ZoomScene.ERASER, strokeSize.toDouble()); Mode.ERASE }
                    Tool.HAND -> Mode.PAN
                    Tool.MOVE_LAYER -> if (s.beginMoveLayer()) Mode.MOVE_LAYER else { listener?.onNothingToMove(); Mode.IGNORE }
                    Tool.SELECT -> startImageGesture(s, e.x, e.y)
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (mode) {
                    // Un deuxième doigt tout de suite : c'était un pincement, pas un trait.
                    Mode.DRAW, Mode.ERASE -> if (e.eventTime - downTime < 350) s.cancelStroke() else finishStroke(s)
                    Mode.MOVE_LAYER -> finishMove(s)
                    Mode.IMAGE_MOVE, Mode.IMAGE_RESIZE -> finishImage(s)
                    else -> Unit
                }
                mode = Mode.GESTURE
                startPinch(e)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.DRAW, Mode.ERASE -> {
                    for (h in 0 until e.historySize) s.extendStroke(ox(e.getHistoricalX(h)), oy(e.getHistoricalY(h)))
                    s.extendStroke(ox(e.x), oy(e.y))
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
                Mode.IMAGE_MOVE -> {
                    s.moveImageBy((e.x - lastX).toDouble(), (e.y - lastY).toDouble())
                    lastX = e.x; lastY = e.y
                    invalidate()
                }
                Mode.IMAGE_RESIZE -> {
                    s.resizeImageTo(resizeCorner, ox(e.x), oy(e.y))
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
                    Mode.DRAW, Mode.ERASE -> finishStroke(s)
                    Mode.MOVE_LAYER -> finishMove(s)
                    Mode.IMAGE_MOVE, Mode.IMAGE_RESIZE -> finishImage(s)
                    else -> Unit
                }
                mode = Mode.NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                when (mode) {
                    Mode.DRAW, Mode.ERASE -> s.cancelStroke()
                    Mode.MOVE_LAYER -> finishMove(s)
                    Mode.IMAGE_MOVE, Mode.IMAGE_RESIZE -> finishImage(s)
                    else -> Unit
                }
                mode = Mode.NONE
                invalidate()
            }
        }
        return true
    }

    /**
     * Outil de sélection : une poignée de l'image sélectionnée la redimensionne, l'intérieur d'une
     * image la sélectionne et la déplace, le vide désélectionne.
     */
    private fun startImageGesture(s: ZoomScene, x: Float, y: Float): Mode {
        val sel = selectedImage
        if (sel != null) {
            val r = screenRect(s, sel)
            if (r != null) {
                val corners = listOf(r[0] to r[1], r[2] to r[1], r[2] to r[3], r[0] to r[3])
                val i = corners.indices.minByOrNull { hypot(corners[it].first - x, corners[it].second - y) }!!
                if (hypot(corners[i].first - x, corners[i].second - y) <= handleReach && s.beginImageEdit(sel)) {
                    resizeCorner = i
                    return Mode.IMAGE_RESIZE
                }
            }
        }
        val hit = s.imageAt(ox(x), oy(y))
        selectImage(hit?.id)
        if (hit != null && s.beginImageEdit(hit.id)) return Mode.IMAGE_MOVE
        return Mode.IGNORE
    }

    /** Molette (souris, Chromebook) : zoome autour du curseur. */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val s = scene ?: return false
        if (e.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && e.actionMasked == MotionEvent.ACTION_SCROLL) {
            if (zoomLocked) return true
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) {
                s.zoomAt(1.2.pow(v.toDouble()), ox(e.x), oy(e.y))
                cameraMoved()
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    private fun finishStroke(s: ZoomScene) {
        if (s.endStroke() != null) listener?.onDrawingChanged()
    }

    private fun finishMove(s: ZoomScene) {
        s.endMoveLayer()
        listener?.onDrawingChanged()
    }

    private fun finishImage(s: ZoomScene) {
        s.endImageEdit()
        listener?.onDrawingChanged()
    }

    private var shownDepth = Long.MIN_VALUE

    private fun cameraMoved() {
        // L'image sélectionnée appartient à une couche : si on change de couche de travail, on la lâche.
        val s = scene
        if (s != null && s.depth != shownDepth) {
            shownDepth = s.depth
            if (selectedImage != null && s.image(selectedImage!!) == null) selectImage(null)
        }
        invalidate()
        listener?.onViewMoved()
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
        if (!zoomLocked && pinchDist > 0f && d > 0f) s.zoomAt((d / pinchDist).toDouble(), ox(cx), oy(cy))
        pinchCx = cx; pinchCy = cy; pinchDist = max(d, 1f)
        cameraMoved()
    }
}
