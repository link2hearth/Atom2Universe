package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.graphics.Typeface
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.PointSink
import com.Atom2Universe.app.pixelart.core.Raster
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.zoomcanvas.core.BoxItem
import com.Atom2Universe.app.zoomcanvas.core.Layer
import com.Atom2Universe.app.zoomcanvas.core.PixelLayer
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * La toile du canvas infini. Un doigt dessine, gomme, trace une forme, pose un texte, manipule un
 * objet (image, forme, texte) ou réaligne la couche du dessous selon l'outil ; deux doigts déplacent et zooment (déplacent seulement quand le zoom est
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

    /**
     * [ERASER_EDIT] : le mode d'édition des gommes (appui long sur la gomme). Tout est estompé, les
     * coups de gomme ressortent en couleur, et on les sélectionne, déplace, redimensionne,
     * monte / descend dans la pile ou supprime comme n'importe quel élément.
     */
    enum class Tool {
        PEN, BRUSH, MARKER, ERASER, SHAPE, TEXT, SELECT, HAND, MOVE_LAYER, ERASER_EDIT,
        /** Les outils de la couche de pixels (le « Canvas ») : pinceau, gomme, formes, pot de peinture, pipette. */
        PIXEL_PEN, PIXEL_ERASER, PIXEL_SHAPE, PIXEL_FILL, PIXEL_PICK,
    }

    /** Noms volontairement distincts de ceux d'Activity (onContentChanged y est déjà pris). */
    interface Listener {
        fun onDrawingChanged()
        fun onViewMoved()
        fun onNothingToMove()
        fun onSelectionChanged()
        /** Un appui avec l'outil texte : sur le texte [id] pour le modifier, ou dans le vide (null) pour en poser un à cet écart d'écran. */
        fun onTextRequested(id: Long?, sx: Double, sy: Double)
        /** La pipette a pris la couleur d'une case : à mettre en couleur principale. */
        fun onColorPicked(color: Int) {}
    }

    var scene: ZoomScene? = null
        set(value) { field = value; selectedItem = null; caches.clear(); cachePolicy.clear(); pixelPainter.clear(); invalidate() }
    var listener: Listener? = null
    var tool = Tool.PEN
        set(value) {
            val changed = field != value
            field = value
            // La sélection d'un outil ne vaut pas pour l'autre (la sélection ne voit pas les gommes, ni l'inverse).
            if (changed) selectItem(null)
            invalidate()
        }
    var color = Color.BLACK
    /**
     * Épaisseur de l'outil de dessin courant (crayon, pinceau, feutre ou gomme), en pixels d'écran
     * au moment du trait (le trait grossit ensuite avec le zoom).
     */
    var strokeSize = 6f
    /** Couleur de remplissage des formes à tracer, et leurs réglages (forme, contour / remplissage, forme régulière). */
    var fillColor = Color.WHITE
    var shapeKind = ShapeKind.RECT
    var shapeFill = ShapeFill.OUTLINE
    var shapeSquare = false
    /** Taille de la police du prochain texte (pixels d'écran), son style ([TextItem.BOLD], [TextItem.ITALIC]) et sa police. */
    var textStyle = 0
    var textFont = ""
    /** Zoom bloqué : deux doigts ne font plus que déplacer la vue, la molette ne fait rien. */
    var zoomLocked = false
    var paperColor = 0xFFFAF8F3.toInt()
    /**
     * L'éditeur est en mode pixels : la grille des cases se montre (quand elles sont assez grandes) et les
     * formes en cours se prévisualisent. Les outils de pixels eux-mêmes se choisissent par [tool].
     */
    var pixelMode = false
        set(value) { field = value; invalidate() }
    var showPixelGrid = true
        set(value) { field = value; invalidate() }
    /** Pinceau rond (sinon carré) et « pixel parfait » : retire les coins d'un trait d'une case. */
    var pixelRound = true
    var pixelPerfect = true
    /** Une grille discrète sous le dessin, qui suit le zoom (le « Canvas » à une seule couche). */
    var showGrid = false
        set(value) { field = value; invalidate() }

    /** Fournit le bitmap d'une image du projet (null tant qu'il se charge : un cadre gris en attendant). */
    var imageProvider: ((String) -> Bitmap?)? = null
        set(value) { field = value; painter.imageProvider = value; caches.invalidateAll() }

    /** Donne la police d'un texte (nom d'affichage, style) : la vue ne connaît pas les ressources. */
    var typefaceProvider: ((String, Int) -> Typeface)? = null
        set(value) { field = value; painter.typefaceProvider = value; caches.invalidateAll() }

    /** L'objet (image, forme, texte) sélectionné avec l'outil de sélection, dans la couche de travail, ou null. */
    var selectedItem: Long? = null
        private set

    private val density = resources.displayMetrics.density
    private val handleRadius = 9f * density
    private val handleReach = 28f * density
    private val tapSlop = 12f * density

    private val list = RenderList()
    private val painter = RunPainter()
    /** Quelles couches se tracent depuis un cache raster, et les caches eux-mêmes. */
    private val cachePolicy = ZoomRenderer.CachePolicy()
    private val pixelPainter = PixelPainter()
    private val caches = LayerCacheManager(object : LayerCache.Host {
        override val imageProvider get() = this@ZoomCanvasView.imageProvider
        override val typefaceProvider get() = this@ZoomCanvasView.typefaceProvider
        override fun cacheReady() = postInvalidateOnAnimation()
        override fun runOnMain(r: Runnable) { post(r) }
    })
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
    private val gridPaint = Paint().apply {
        strokeWidth = 1f
        color = 0x22000000
    }
    private var gridLines = FloatArray(0)

    fun selectItem(id: Long?) {
        if (selectedItem == id) return
        selectedItem = id
        invalidate()
        listener?.onSelectionChanged()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(paperColor)
        val s = scene ?: return
        if (showGrid) drawGrid(s, canvas)
        draw(s, canvas, width.toDouble(), height.toDouble(), tool == Tool.ERASER_EDIT)
        if (pixelMode) {
            if (showPixelGrid && s.zoom >= PIXEL_GRID_MIN_ZOOM) drawGrid(s, canvas, 1.0)
            drawPixelPreview(s, canvas)
        }
        if (mode == Mode.ERASE) canvas.drawCircle(lastX, lastY, strokeSize / 2, cursorPaint)
        drawSelection(s, canvas)
    }

    private fun draw(s: ZoomScene, canvas: Canvas, w: Double, h: Double, ghost: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!s.pixelsAbove) pixelPainter.draw(canvas, s.pixels, s.cx, s.cy, s.zoom, w, h)
        ZoomRenderer.build(s, w, h, list, ghost, cachePolicy)
        for (g in 0 until list.groupCount) {
            val from = list.groupStart[g]
            val to = list.groupEnd[g]
            val layer = list.groupLayer[g]
            if (!list.groupIsolated[g]) {
                if (layer != null) drawCached(canvas, g, layer, w, h, now, (list.groupAlpha[g] * 255).toInt().coerceIn(0, 255))
                painter.drawRuns(canvas, list, from, to)
                continue
            }
            // Une couche à part : ses coups de gomme ne creusent qu'elle, et une couche qui s'efface
            // est posée d'un bloc avec son opacité (deux de ses traits qui se croisent ne foncent pas).
            val alpha = (list.groupAlpha[g] * 255).toInt().coerceIn(0, 255)
            val saved = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), alpha)
            if (layer != null) drawCached(canvas, g, layer, w, h, now, 255)
            painter.drawRuns(canvas, list, from, to)
            canvas.restoreToCount(saved)
        }
        if (s.pixelsAbove) pixelPainter.draw(canvas, s.pixels, s.cx, s.cy, s.zoom, w, h)
        caches.trim(now)
    }

    /** La couche du groupe [g] est trop dense pour être tracée trait par trait : on pose son cache raster. */
    private fun drawCached(canvas: Canvas, g: Int, layer: Layer, w: Double, h: Double, now: Long, alpha: Int) {
        caches.cacheFor(layer, now).draw(canvas, list.groupViewX[g], list.groupViewY[g], list.groupViewZoom[g], w, h, alpha)
    }

    /**
     * Le quadrillage : un pas rond (1, 2 ou 5 × une puissance de dix, en unités de la couche) qui
     * garde des cases de 48 à 120 pixels à l'écran, quel que soit le zoom. Les lignes sont placées
     * depuis la caméra (jamais depuis l'origine) : exact même très loin du centre.
     */
    private fun drawGrid(s: ZoomScene, canvas: Canvas, forcedStep: Double = 0.0) {
        val raw = 48.0 / s.zoom
        val e = Math.floor(Math.log10(raw))
        val base = Math.pow(10.0, e)
        val m = raw / base
        val step = if (forcedStep > 0.0) forcedStep else base * (if (m <= 1.0) 1.0 else if (m <= 2.0) 2.0 else if (m <= 5.0) 5.0 else 10.0)
        val w = width.toDouble()
        val h = height.toDouble()
        val left = s.cx - w / 2 / s.zoom
        val top = s.cy - h / 2 / s.zoom
        val first = Math.ceil(left / step)
        val firstY = Math.ceil(top / step)
        val cols = (w / (step * s.zoom)).toInt() + 2
        val rows = (h / (step * s.zoom)).toInt() + 2
        if (gridLines.size < (cols + rows) * 4) gridLines = FloatArray((cols + rows) * 4)
        var n = 0
        for (i in 0 until cols) {
            val x = ((first + i) * step - s.cx) * s.zoom + w / 2
            if (x < 0 || x > w) continue
            gridLines[n++] = x.toFloat(); gridLines[n++] = 0f; gridLines[n++] = x.toFloat(); gridLines[n++] = h.toFloat()
        }
        for (j in 0 until rows) {
            val y = ((firstY + j) * step - s.cy) * s.zoom + h / 2
            if (y < 0 || y > h) continue
            gridLines[n++] = 0f; gridLines[n++] = y.toFloat(); gridLines[n++] = w.toFloat(); gridLines[n++] = y.toFloat()
        }
        canvas.drawLines(gridLines, 0, n, gridPaint)
    }

    /** Ce qu'on voit, en taille réelle, sur le papier (l'export de la vue). La grille n'en fait pas partie. */
    fun snapshot(): Bitmap? {
        val s = scene ?: return null
        if (width == 0 || height == 0) return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(paperColor)
        draw(s, canvas, width.toDouble(), height.toDouble())
        return bmp
    }

    /** Une image du projet vient de se charger, ou une police : les caches qui ont cuit un cadre gris sont à refaire. */
    fun invalidateCaches() {
        caches.invalidateAll()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        caches.clear()
        pixelPainter.clear()
        super.onDetachedFromWindow()
    }

    /** Cadre et poignées de l'objet sélectionné, ramenés au bord de l'écran s'ils en sortent. */
    private fun drawSelection(s: ZoomScene, canvas: Canvas) {
        val id = selectedItem ?: return
        val item = s.box(id)
        if (item == null) { selectItem(null); return }
        val r = screenRect(s, id) ?: return
        val m = 4.0 * handleReach
        val x0 = r[0].coerceIn(-m, width + m).toFloat()
        val y0 = r[1].coerceIn(-m, height + m).toFloat()
        val x1 = r[2].coerceIn(-m, width + m).toFloat()
        val y1 = r[3].coerceIn(-m, height + m).toFloat()
        canvas.drawRect(x0, y0, x1, y1, framePaint)
        for (k in 0 until 4) {
            val hx = if (k == 0 || k == 3) x0 else x1
            val hy = if (k < 2) y0 else y1
            canvas.drawCircle(hx, hy, handleRadius, handlePaint)
            canvas.drawCircle(hx, hy, handleRadius, framePaint)
        }
    }

    /** Rectangle de l'objet [id] en pixels de la vue (Double, pas encore borné). */
    private fun screenRect(s: ZoomScene, id: Long): DoubleArray? {
        val item = s.box(id) ?: return null
        val r = s.boxScreenRect(item)
        // Un peu de marge : le cadre d'un trait droit ou d'un point n'est pas réduit à une ligne.
        val pad = 4.0 * density
        return doubleArrayOf(r[0] + width / 2.0 - pad, r[1] + height / 2.0 - pad, r[2] + width / 2.0 + pad, r[3] + height / 2.0 + pad)
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

    private enum class Mode { NONE, DRAW, ERASE, SHAPE, TEXT_TAP, PAN, MOVE_LAYER, ITEM_MOVE, ITEM_RESIZE, GESTURE, IGNORE, PIXEL_DRAW, PIXEL_SHAPE, PIXEL_TAP }

    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    /** Le glissé d'un élément a commencé (le doigt a quitté la zone d'appui). */
    private var dragging = false
    /** L'élément sélectionné qu'un simple appui remplacera par celui d'en dessous, ou null. */
    private var cycleFrom: Long? = null
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
                    Tool.SHAPE -> {
                        s.beginShape(shapeKind, shapeFill, color, fillColor, strokeSize.toDouble(), ox(e.x), oy(e.y), shapeSquare)
                        Mode.SHAPE
                    }
                    Tool.TEXT -> Mode.TEXT_TAP
                    Tool.HAND -> Mode.PAN
                    Tool.MOVE_LAYER -> if (s.beginMoveLayer()) Mode.MOVE_LAYER else { listener?.onNothingToMove(); Mode.IGNORE }
                    Tool.SELECT, Tool.ERASER_EDIT -> startItemGesture(s, e.x, e.y)
                    Tool.PIXEL_PEN, Tool.PIXEL_ERASER -> { startPixelStroke(s, e.x, e.y); Mode.PIXEL_DRAW }
                    Tool.PIXEL_SHAPE -> { startPixelShape(s, e.x, e.y); Mode.PIXEL_SHAPE }
                    Tool.PIXEL_FILL, Tool.PIXEL_PICK -> { downX = e.x; downY = e.y; Mode.PIXEL_TAP }
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (mode) {
                    // Un deuxième doigt tout de suite : c'était un pincement, pas un trait.
                    Mode.DRAW, Mode.ERASE -> if (e.eventTime - downTime < 350) s.cancelStroke() else finishStroke(s)
                    Mode.SHAPE -> if (e.eventTime - downTime < 350) s.cancelShape() else finishShape(s)
                    Mode.PIXEL_DRAW -> if (e.eventTime - downTime < 350) s.cancelPixelEdit() else finishPixelStroke(s)
                    Mode.PIXEL_SHAPE -> if (e.eventTime - downTime < 350) clearPixelPreview() else finishPixelShape(s)
                    Mode.MOVE_LAYER -> finishMove(s)
                    Mode.ITEM_MOVE, Mode.ITEM_RESIZE -> finishItem(s)
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
                Mode.SHAPE -> {
                    for (h in 0 until e.historySize) s.updateShape(ox(e.getHistoricalX(h)), oy(e.getHistoricalY(h)))
                    s.updateShape(ox(e.x), oy(e.y))
                    lastX = e.x; lastY = e.y
                    invalidate()
                }
                Mode.PIXEL_DRAW -> {
                    for (h in 0 until e.historySize) movePixelTo(s, s.cellX(ox(e.getHistoricalX(h))), s.cellY(oy(e.getHistoricalY(h))))
                    movePixelTo(s, s.cellX(ox(e.x)), s.cellY(oy(e.y)))
                    invalidate()
                }
                Mode.PIXEL_SHAPE -> {
                    pixelCurX = s.cellX(ox(e.x)); pixelCurY = s.cellY(oy(e.y))
                    buildPixelPreview()
                    invalidate()
                }
                Mode.PIXEL_TAP -> if (hypot(e.x - downX, e.y - downY) > tapSlop) mode = Mode.IGNORE
                // Un appui qui glisse n'est plus un appui : le texte ne se pose pas.
                Mode.TEXT_TAP -> if (hypot(e.x - lastX, e.y - lastY) > tapSlop) mode = Mode.IGNORE
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
                Mode.ITEM_MOVE -> {
                    // Un doigt qui bouge à peine ne déplace rien : c'est un appui (qui peut changer d'élément).
                    if (dragging || hypot(e.x - downX, e.y - downY) > tapSlop) {
                        dragging = true
                        s.moveBoxBy((e.x - lastX).toDouble(), (e.y - lastY).toDouble())
                        lastX = e.x; lastY = e.y
                        invalidate()
                    }
                }
                Mode.ITEM_RESIZE -> {
                    s.resizeBoxTo(resizeCorner, ox(e.x), oy(e.y))
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
                    Mode.SHAPE -> finishShape(s)
                    Mode.PIXEL_DRAW -> finishPixelStroke(s)
                    Mode.PIXEL_SHAPE -> finishPixelShape(s)
                    Mode.PIXEL_TAP -> if (e.eventTime - downTime < TAP_MS) pixelTap(s, e.x, e.y)
                    Mode.TEXT_TAP -> if (e.eventTime - downTime < TAP_MS) {
                        val hit = s.boxAt(ox(e.x), oy(e.y), textOnly = true)
                        listener?.onTextRequested((hit as? TextItem)?.id, ox(e.x), oy(e.y))
                    }
                    Mode.MOVE_LAYER -> finishMove(s)
                    Mode.ITEM_MOVE -> {
                        val from = cycleFrom
                        if (!dragging && from != null && e.eventTime - downTime < TAP_MS) {
                            // Un appui sur l'élément déjà sélectionné : on descend à celui qui est dessous, au même endroit.
                            s.endBoxEdit()
                            s.boxAt(ox(e.x), oy(e.y), below = from, erasers = tool == Tool.ERASER_EDIT)?.let { selectItem(it.id) }
                        } else finishItem(s)
                    }
                    Mode.ITEM_RESIZE -> finishItem(s)
                    else -> Unit
                }
                mode = Mode.NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                when (mode) {
                    Mode.DRAW, Mode.ERASE -> s.cancelStroke()
                    Mode.SHAPE -> s.cancelShape()
                    Mode.PIXEL_DRAW -> s.cancelPixelEdit()
                    Mode.PIXEL_SHAPE -> clearPixelPreview()
                    Mode.MOVE_LAYER -> finishMove(s)
                    Mode.ITEM_MOVE, Mode.ITEM_RESIZE -> finishItem(s)
                    else -> Unit
                }
                mode = Mode.NONE
                invalidate()
            }
        }
        return true
    }

    // ---- Pixels ---------------------------------------------------------------------------

    private var pixelLastX = 0
    private var pixelLastY = 0
    /** Les cases du trait en cours, dans l'ordre : le « pixel parfait » y cherche les coins. */
    private var pixelPath = LongArray(64)
    private var pixelPathN = 0
    private var pixelAnchorX = 0
    private var pixelAnchorY = 0
    private var pixelCurX = 0
    private var pixelCurY = 0

    /** Cases d'un aperçu de forme (paires x, y) : le plein et le contour. */
    private class CellList {
        var a = IntArray(256)
        var n = 0
        val count: Int get() = n / 2
        fun add(x: Int, y: Int) {
            if (n + 2 > a.size) a = a.copyOf(a.size * 2)
            a[n++] = x
            a[n++] = y
        }
        fun clear() { n = 0 }
    }

    private val previewFill = CellList()
    private val previewLine = CellList()
    private val previewPaint = Paint().apply { isAntiAlias = false }

    private fun pixelSize() = strokeSize.toInt().coerceAtLeast(1)

    private fun startPixelStroke(s: ZoomScene, x: Float, y: Float) {
        s.beginPixelEdit()
        pixelPathN = 0
        val cx = s.cellX(ox(x))
        val cy = s.cellY(oy(y))
        pixelLastX = cx
        pixelLastY = cy
        stampPixel(s, cx, cy)
        pathAdd(s, cx, cy)
    }

    /** Pose le pinceau (ou la gomme) sur la case ([x], [y]). */
    private fun stampPixel(s: ZoomScene, x: Int, y: Int) {
        val c = if (tool == Tool.PIXEL_ERASER) 0 else color
        Raster.stamp(x, y, pixelSize(), pixelRound) { px, py -> s.paintCell(px, py, c) }
    }

    private fun movePixelTo(s: ZoomScene, cx: Int, cy: Int) {
        if (cx == pixelLastX && cy == pixelLastY) return
        val fx = pixelLastX
        val fy = pixelLastY
        Raster.line(fx, fy, cx, cy) { x, y ->
            if (x != fx || y != fy) {
                stampPixel(s, x, y)
                pathAdd(s, x, y)
            }
        }
        pixelLastX = cx
        pixelLastY = cy
    }

    /**
     * Note une case du trait. « Pixel parfait » (pinceau d'une case) : si les trois dernières forment un coin en L (la
     * première et la troisième en diagonale), la case du coin est de trop : on la retire.
     */
    private fun pathAdd(s: ZoomScene, x: Int, y: Int) {
        if (pixelPathN == pixelPath.size) pixelPath = pixelPath.copyOf(pixelPath.size * 2)
        pixelPath[pixelPathN++] = PixelLayer.cellKey(x, y)
        if (!pixelPerfect || tool != Tool.PIXEL_PEN || pixelSize() != 1 || pixelPathN < 3) return
        val n = pixelPathN
        val ax = PixelLayer.cellX(pixelPath[n - 3])
        val ay = PixelLayer.cellY(pixelPath[n - 3])
        val bx = PixelLayer.cellX(pixelPath[n - 2])
        val by = PixelLayer.cellY(pixelPath[n - 2])
        val corner = ax != x && ay != y && Math.abs(ax - x) <= 1 && Math.abs(ay - y) <= 1 &&
            (ax == bx || ay == by) && (bx == x || by == y)
        if (!corner) return
        // La case du coin ne sert qu'une fois dans le trait : sinon on la garde.
        var uses = 0
        for (i in 0 until n) if (pixelPath[i] == pixelPath[n - 2]) uses++
        if (uses != 1) return
        s.unpaintCell(bx, by)
        pixelPath[n - 2] = pixelPath[n - 1]
        pixelPathN = n - 1
    }

    private fun finishPixelStroke(s: ZoomScene) {
        if (s.endPixelEdit()) listener?.onDrawingChanged()
    }

    private fun startPixelShape(s: ZoomScene, x: Float, y: Float) {
        pixelAnchorX = s.cellX(ox(x))
        pixelAnchorY = s.cellY(oy(y))
        pixelCurX = pixelAnchorX
        pixelCurY = pixelAnchorY
        buildPixelPreview()
    }

    /** La couleur du plein d'une forme : la principale pour « plein », la secondaire pour « les deux ». */
    private fun shapeFillColor() = if (shapeFill == ShapeFill.FILL) color else fillColor

    /** Envoie les cases de la forme en cours : le plein à [plotFill], le contour (épaissi par le pinceau) à [plotLine]. */
    private fun shapeCells(plotFill: PointSink, plotLine: PointSink) {
        val ax = pixelAnchorX
        val ay = pixelAnchorY
        var bx = pixelCurX
        var by = pixelCurY
        val directed = shapeKind == ShapeKind.LINE || shapeKind == ShapeKind.ARROW
        if (shapeSquare && !directed) {
            val dx = bx - ax
            val dy = by - ay
            val m = Math.max(Math.abs(dx), Math.abs(dy))
            bx = ax + if (dx < 0) -m else m
            by = ay + if (dy < 0) -m else m
        }
        val size = pixelSize()
        val line = PointSink { x, y -> Raster.stamp(x, y, size, pixelRound, plotLine) }
        when {
            shapeKind == ShapeKind.LINE || shapeFill == ShapeFill.OUTLINE -> Raster.shape(shapeKind, ax, ay, bx, by, false, line)
            shapeFill == ShapeFill.FILL -> Raster.shape(shapeKind, ax, ay, bx, by, true, plotFill)
            else -> {
                Raster.shape(shapeKind, ax, ay, bx, by, true, plotFill)
                Raster.shape(shapeKind, ax, ay, bx, by, false, line)
            }
        }
    }

    private fun buildPixelPreview() {
        previewFill.clear()
        previewLine.clear()
        shapeCells(
            { x, y -> if (previewFill.count < PREVIEW_MAX) previewFill.add(x, y) },
            { x, y -> if (previewLine.count < PREVIEW_MAX) previewLine.add(x, y) },
        )
    }

    private fun clearPixelPreview() {
        previewFill.clear()
        previewLine.clear()
    }

    private fun finishPixelShape(s: ZoomScene) {
        clearPixelPreview()
        s.beginPixelEdit()
        val inside = shapeFillColor()
        shapeCells({ x, y -> s.paintCell(x, y, inside) }, { x, y -> s.paintCell(x, y, color) })
        if (s.endPixelEdit()) listener?.onDrawingChanged()
    }

    /** L'aperçu de la forme en cours, case par case. */
    private fun drawPixelPreview(s: ZoomScene, canvas: Canvas) {
        if (previewFill.count == 0 && previewLine.count == 0) return
        val w = width.toDouble()
        val h = height.toDouble()
        fun paintCells(list: CellList, c: Int) {
            previewPaint.color = c
            var i = 0
            while (i < list.n) {
                val l = ((list.a[i] - s.cx) * s.zoom + w / 2).roundToInt().toFloat()
                val t = ((list.a[i + 1] - s.cy) * s.zoom + h / 2).roundToInt().toFloat()
                val r = ((list.a[i] + 1 - s.cx) * s.zoom + w / 2).roundToInt().toFloat()
                val b = ((list.a[i + 1] + 1 - s.cy) * s.zoom + h / 2).roundToInt().toFloat()
                if (r >= 0 && b >= 0 && l <= w && t <= h) canvas.drawRect(l, t, r, b, previewPaint)
                i += 2
            }
        }
        paintCells(previewFill, shapeFillColor())
        paintCells(previewLine, color)
    }

    /** Un appui avec le pot de peinture ou la pipette. */
    private fun pixelTap(s: ZoomScene, x: Float, y: Float) {
        val cx = s.cellX(ox(x))
        val cy = s.cellY(oy(y))
        if (tool == Tool.PIXEL_PICK) {
            val c = s.pixels.get(cx, cy)
            if (c ushr 24 != 0) listener?.onColorPicked(c)
            return
        }
        // Dans le vide, le remplissage n'a pas de bord : il s'arrête à l'écran (et à une fenêtre qui reste raisonnable).
        val x0 = maxOf(s.cellX(ox(0f)), cx - FILL_REACH)
        val x1 = minOf(s.cellX(ox(width.toFloat())), cx + FILL_REACH)
        val y0 = maxOf(s.cellY(oy(0f)), cy - FILL_REACH)
        val y1 = minOf(s.cellY(oy(height.toFloat())), cy + FILL_REACH)
        if (s.fillPixels(cx, cy, color, x0, y0, x1, y1)) listener?.onDrawingChanged()
    }

    /**
     * Outil de sélection : une poignée de l'objet sélectionné le redimensionne, l'intérieur d'un
     * objet (image, forme, texte) le sélectionne et le déplace, le vide désélectionne.
     */
    private fun startItemGesture(s: ZoomScene, x: Float, y: Float): Mode {
        val sel = selectedItem
        if (sel != null) {
            val r = screenRect(s, sel)
            if (r != null) {
                val corners = listOf(r[0] to r[1], r[2] to r[1], r[2] to r[3], r[0] to r[3])
                val i = corners.indices.minByOrNull { hypot(corners[it].first - x, corners[it].second - y) }!!
                if (hypot(corners[i].first - x, corners[i].second - y) <= handleReach && s.beginBoxEdit(sel)) {
                    resizeCorner = i
                    return Mode.ITEM_RESIZE
                }
            }
        }
        val hit: BoxItem? = s.boxAt(ox(x), oy(y), erasers = tool == Tool.ERASER_EDIT)
        // Toucher l'élément déjà sélectionné : un simple appui descendra dans la pile, un glissé le déplace.
        cycleFrom = if (hit != null && hit.id == sel) hit.id else null
        dragging = false
        downX = x; downY = y
        selectItem(hit?.id)
        if (hit != null && s.beginBoxEdit(hit.id)) return Mode.ITEM_MOVE
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

    private fun finishItem(s: ZoomScene) {
        s.endBoxEdit()
        listener?.onDrawingChanged()
    }

    private fun finishShape(s: ZoomScene) {
        if (s.endShape() != null) listener?.onDrawingChanged()
    }

    private var shownDepth = Long.MIN_VALUE

    private companion object {
        /** Un appui plus long que ça n'est plus un appui. */
        const val TAP_MS = 500L
        /** La grille des cases se montre quand une case fait au moins 8 pixels d'écran. */
        const val PIXEL_GRID_MIN_ZOOM = 8.0
        /** Cases d'un aperçu de forme au plus (au-delà, l'aperçu est tronqué ; la forme, elle, est posée en entier). */
        const val PREVIEW_MAX = 60_000
        /** Le pot de peinture cherche à au plus ces cases de l'appui, de chaque côté. */
        const val FILL_REACH = 900
    }

    private fun cameraMoved() {
        // L'objet sélectionné appartient à une couche : si on change de couche de travail, on le lâche.
        val s = scene
        if (s != null && s.depth != shownDepth) {
            shownDepth = s.depth
            if (selectedItem != null && s.box(selectedItem!!) == null) selectItem(null)
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
