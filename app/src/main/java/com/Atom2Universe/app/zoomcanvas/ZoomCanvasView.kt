package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import com.Atom2Universe.app.zoomcanvas.core.CellMap
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
        override fun runOnMainDelayed(r: Runnable, delayMs: Long) { postDelayed(r, delayMs) }
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

    /**
     * Trace la scène. [policy] dit quelles couches denses se posent depuis leur cache raster ; null : tout
     * est tracé trait par trait (l'export en taille réelle, qui doit être net).
     */
    private fun draw(s: ZoomScene, canvas: Canvas, w: Double, h: Double, ghost: Boolean = false, policy: ZoomRenderer.CachePolicy? = cachePolicy) {
        val now = SystemClock.uptimeMillis()
        if (!s.pixelsAbove) pixelPainter.draw(canvas, s.pixels, s.cx, s.cy, s.zoom, w, h)
        ZoomRenderer.build(s, w, h, list, ghost, policy)
        for (g in 0 until list.groupCount) {
            val from = list.groupStart[g]
            val to = list.groupEnd[g]
            val layer = list.groupLayer[g]
            val alpha = (list.groupAlpha[g] * 255).toInt().coerceIn(0, 255)
            if (!list.groupIsolated[g]) {
                if (layer != null) drawCached(canvas, g, layer, w, h, now, alpha)
                painter.drawRuns(canvas, list, from, to)
                continue
            }
            // Posée depuis son cache, sans trait en cours : ses gommes y sont déjà appliquées, il suffit
            // de poser l'image avec son opacité (pas de calque à part).
            if (layer != null && from == to) {
                drawCached(canvas, g, layer, w, h, now, alpha)
                continue
            }
            // Une couche à part : ses coups de gomme ne creusent qu'elle, et une couche qui s'efface
            // est posée d'un bloc avec son opacité (deux de ses traits qui se croisent ne foncent pas).
            // Le calque ne couvre que ce que la couche trace à l'écran (tout l'écran s'il y a un texte).
            groupX0 = Float.POSITIVE_INFINITY; groupY0 = Float.POSITIVE_INFINITY
            groupX1 = Float.NEGATIVE_INFINITY; groupY1 = Float.NEGATIVE_INFINITY
            val bounded = addRunBounds(from, to)
            if (bounded && layer != null && caches.cacheFor(layer, now).drawnRect(list.groupViewX[g], list.groupViewY[g], list.groupViewZoom[g], w, h, cacheRect)) {
                addBounds(cacheRect.left, cacheRect.top, cacheRect.right, cacheRect.bottom)
            }
            if (!bounded) { groupX0 = 0f; groupY0 = 0f; groupX1 = w.toFloat(); groupY1 = h.toFloat() }
            var l = max(groupX0, 0f)
            var t = max(groupY0, 0f)
            var r = min(groupX1, w.toFloat())
            var b = min(groupY1, h.toFloat())
            // Rien à l'écran : un calque vide (le cache, lui, doit quand même voir passer l'image pour cuire).
            if (!(r > l && b > t)) { l = 0f; t = 0f; r = 0f; b = 0f }
            val saved = canvas.saveLayerAlpha(l, t, r, b, alpha)
            if (layer != null) drawCached(canvas, g, layer, w, h, now, 255)
            painter.drawRuns(canvas, list, from, to)
            canvas.restoreToCount(saved)
        }
        if (s.pixelsAbove) pixelPainter.draw(canvas, s.pixels, s.cx, s.cy, s.zoom, w, h)
        caches.trim(now)
    }

    // Le rectangle d'écran que trace un groupe (voir [addRunBounds]).
    private var groupX0 = 0f
    private var groupY0 = 0f
    private var groupX1 = 0f
    private var groupY1 = 0f
    private val cacheRect = RectF()

    private fun addBounds(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (x0 < groupX0) groupX0 = x0
        if (y0 < groupY0) groupY0 = y0
        if (x1 > groupX1) groupX1 = x1
        if (y1 > groupY1) groupY1 = y1
    }

    /**
     * Ajoute au rectangle du groupe ce que tracent les passes [from]..[to] : chaque point d'un trait avec
     * sa demi-épaisseur (et le lissage du bord), le polygone d'un remplissage, le rectangle d'une image.
     * Faux si une passe n'a pas de boîte connue d'avance (un texte) : il faut alors tout l'écran.
     */
    private fun addRunBounds(from: Int, to: Int): Boolean {
        val c = list.coords
        for (r in from until to) {
            if (list.runText[r] != null) return false
            val img = list.runImage[r]
            if (img >= 0) {
                val d = list.imageDst
                addBounds(d[4 * img], d[4 * img + 1], d[4 * img + 2], d[4 * img + 3])
                continue
            }
            // L'épaisseur de la passe borne celle de chacun de ses points (le pinceau s'affine, sans grossir).
            val pad = list.runWidth[r] / 2 + 2f
            val start = list.runStart[r]
            for (k in 0 until list.runPoints[r]) {
                val x = c[start + 2 * k]
                val y = c[start + 2 * k + 1]
                addBounds(x - pad, y - pad, x + pad, y + pad)
            }
        }
        return true
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

    /**
     * Ce qu'on voit, en taille réelle, sur le papier (l'export de la vue). La grille n'en fait pas partie.
     * Tout est tracé trait par trait, sans les caches raster (flous, ou pas encore cuits) : l'export est net.
     */
    fun snapshot(): Bitmap? {
        val s = scene ?: return null
        if (width == 0 || height == 0) return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(paperColor)
        draw(s, canvas, width.toDouble(), height.toDouble(), policy = null)
        return bmp
    }

    /** L'image [key] du projet vient de se charger : seuls les caches des couches qui la portent (cuits avec un cadre gris) sont à refaire. */
    fun imageLoaded(key: String) {
        caches.invalidateImage(key)
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
        val r = screenRect(s, item)
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

    /** Le rectangle de l'objet sélectionné (voir [screenRect]) : un seul tableau, réutilisé à chaque image. */
    private val selRect = DoubleArray(4)

    /** Rectangle de l'objet [item] en pixels de la vue (Double, pas encore borné), rangé dans [selRect]. */
    private fun screenRect(s: ZoomScene, item: BoxItem): DoubleArray {
        val r = s.boxScreenRect(item, selRect)
        // Un peu de marge : le cadre d'un trait droit ou d'un point n'est pas réduit à une ligne.
        val pad = 4.0 * density
        r[0] += width / 2.0 - pad
        r[1] += height / 2.0 - pad
        r[2] += width / 2.0 + pad
        r[3] += height / 2.0 + pad
        return r
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
                    val cx = s.cellX(ox(e.x))
                    val cy = s.cellY(oy(e.y))
                    // L'aperçu ne se refait que si le doigt change de case.
                    if (cx != pixelCurX || cy != pixelCurY) {
                        pixelCurX = cx; pixelCurY = cy
                        buildPixelPreview()
                        invalidate()
                    }
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

    /**
     * Cases d'un aperçu de forme, par bouts de ligne (x0, x1, y) : le plein et le contour. Les cases d'une
     * forme arrivent ligne par ligne, de gauche à droite : un rectangle plein n'y fait qu'un bout par ligne.
     */
    private class CellRuns {
        var a = IntArray(384)
        var n = 0
        /** Nombre de bouts. */
        val count: Int get() = n / 3
        fun add(x: Int, y: Int) {
            // La case prolonge le dernier bout (même ligne, juste à sa droite) : on l'allonge.
            if (n > 0 && a[n - 1] == y && a[n - 2] == x - 1) { a[n - 2] = x; return }
            if (n + 3 > a.size) a = a.copyOf(a.size * 2)
            a[n++] = x
            a[n++] = x
            a[n++] = y
        }
        fun clear() { n = 0 }
    }

    private val previewFill = CellRuns()
    private val previewLine = CellRuns()
    /** Les cases du contour déjà dans l'aperçu : les empreintes du pinceau se chevauchent. */
    private val previewSeen = CellMap(1024)
    private val previewPaint = Paint().apply { isAntiAlias = false }

    private fun pixelSize() = strokeSize.toInt().coerceAtLeast(1)

    /** Le pinceau (taille, rond) pour lequel [brushEdges] est calculé. */
    private var edgesSize = -1
    private var edgesRound = false
    /**
     * Pour chacune des 8 directions d'un pas d'une case (rangée en (dy + 1) × 3 + (dx + 1)) : les cases de
     * l'empreinte du pinceau qui n'étaient pas dans l'empreinte précédente (paires dx, dy autour de la
     * nouvelle case). Le long d'un trait, seul ce bord est neuf : le reste vient d'être peint.
     */
    private var brushEdges = arrayOfNulls<IntArray>(9)

    private fun brushEdges(size: Int, round: Boolean): Array<IntArray?> {
        if (size == edgesSize && round == edgesRound) return brushEdges
        val f = Raster.footprint(size, round)
        // L'empreinte dans une grille, avec une case de marge tout autour (un pas sort d'une case au plus).
        var lo = 0
        var hi = 0
        var i = 0
        while (i < f.size) { lo = min(lo, min(f[i], f[i + 1])); hi = max(hi, max(f[i], f[i + 1])); i += 2 }
        val side = hi - lo + 3
        val grid = BooleanArray(side * side)
        i = 0
        while (i < f.size) { grid[(f[i + 1] - lo + 1) * side + (f[i] - lo + 1)] = true; i += 2 }
        val edges = arrayOfNulls<IntArray>(9)
        val tmp = IntArray(f.size)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            var n = 0
            i = 0
            while (i < f.size) {
                // La case (centre + f) était déjà dans l'empreinte d'avant (centre − pas) si f + pas y est.
                if (!grid[(f[i + 1] + dy - lo + 1) * side + (f[i] + dx - lo + 1)]) { tmp[n++] = f[i]; tmp[n++] = f[i + 1] }
                i += 2
            }
            edges[(dy + 1) * 3 + (dx + 1)] = tmp.copyOf(n)
        }
        brushEdges = edges
        edgesSize = size
        edgesRound = round
        return edges
    }

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

    /**
     * Le pinceau avance d'un pas d'une case ([dx], [dy] dans −1..1) jusqu'à ([x], [y]) : seule la partie de
     * son empreinte qui n'était pas sous lui au pas d'avant est neuve, on ne pose qu'elle (un pinceau de 64
     * cases ne repeint pas ses 3 000 cases à chaque case du trait).
     */
    private fun stampPixelStep(s: ZoomScene, x: Int, y: Int, dx: Int, dy: Int) {
        val edge = brushEdges(pixelSize(), pixelRound)[(dy + 1) * 3 + (dx + 1)]
        if (edge == null) { stampPixel(s, x, y); return }
        val c = if (tool == Tool.PIXEL_ERASER) 0 else color
        var i = 0
        while (i < edge.size) {
            s.paintCell(x + edge[i], y + edge[i + 1], c)
            i += 2
        }
    }

    private fun movePixelTo(s: ZoomScene, cx: Int, cy: Int) {
        if (cx == pixelLastX && cy == pixelLastY) return
        val fx = pixelLastX
        val fy = pixelLastY
        // La ligne avance case par case (un pas de −1 à 1 sur chaque axe) depuis la case déjà peinte.
        var px = fx
        var py = fy
        Raster.line(fx, fy, cx, cy) { x, y ->
            if (x != fx || y != fy) {
                stampPixelStep(s, x, y, x - px, y - py)
                pathAdd(s, x, y)
                px = x
                py = y
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

    /** Arrête l'énumération d'une forme qui dépasse le plafond (sans pile : rien n'est fabriqué à chaque fois). */
    private object ShapeTooBig : RuntimeException() {
        private fun readResolve(): Any = ShapeTooBig
        override fun fillInStackTrace(): Throwable = this
    }

    /** Cases envoyées par la forme en cours (empreintes du pinceau comprises, avec leurs chevauchements). */
    private var shapePlots = 0

    private fun countPlot() {
        if (++shapePlots > MAX_SHAPE_PLOTS) throw ShapeTooBig
    }

    /**
     * Envoie les cases de la forme en cours : le plein à [plotFill], le contour (épaissi par le pinceau) à
     * [plotLine]. Faux, sans rien envoyer ou en s'arrêtant en route, si la forme dépasse le plafond de
     * cases ([ZoomScene.MAX_FILL_CELLS], comme le pot de peinture) : dézoomé, un rectangle plein couvrirait
     * des centaines de millions de cases.
     */
    private fun shapeCells(plotFill: PointSink, plotLine: PointSink): Boolean {
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
        val outlineOnly = shapeKind == ShapeKind.LINE || shapeFill == ShapeFill.OUTLINE
        // La boîte suffit à écarter une forme bien trop grande, sans parcourir ses cases : le plein couvre
        // au plus la boîte, le contour au plus son tour épaissi par le pinceau.
        val bw = Math.abs(bx.toLong() - ax) + 1
        val bh = Math.abs(by.toLong() - ay) + 1
        var estimate = 0L
        if (outlineOnly || shapeFill == ShapeFill.BOTH) estimate += 2 * (bw + bh) * size
        if (!outlineOnly) estimate += bw * bh
        if (estimate > ZoomScene.MAX_FILL_CELLS) return false
        shapePlots = 0
        val fill = PointSink { x, y -> countPlot(); plotFill.plot(x, y) }
        val stamped = PointSink { x, y -> countPlot(); plotLine.plot(x, y) }
        val line = PointSink { x, y -> Raster.stamp(x, y, size, pixelRound, stamped) }
        try {
            when {
                outlineOnly -> Raster.shape(shapeKind, ax, ay, bx, by, false, line)
                shapeFill == ShapeFill.FILL -> Raster.shape(shapeKind, ax, ay, bx, by, true, fill)
                else -> {
                    Raster.shape(shapeKind, ax, ay, bx, by, true, fill)
                    Raster.shape(shapeKind, ax, ay, bx, by, false, line)
                }
            }
        } catch (e: ShapeTooBig) {
            return false
        }
        return true
    }

    private fun buildPixelPreview() {
        clearPixelPreview()
        val whole = shapeCells(
            { x, y -> if (previewFill.count < PREVIEW_MAX) previewFill.add(x, y) },
            { x, y -> if (previewLine.count < PREVIEW_MAX && previewSeen.putIfAbsent(PixelLayer.cellKey(x, y), 0)) previewLine.add(x, y) },
        )
        // Trop grande : la forme ne fera rien, l'aperçu ne montre rien.
        if (!whole) clearPixelPreview()
    }

    private fun clearPixelPreview() {
        previewFill.clear()
        previewLine.clear()
        previewSeen.clear()
    }

    private fun finishPixelShape(s: ZoomScene) {
        clearPixelPreview()
        s.beginPixelEdit()
        val inside = shapeFillColor()
        val whole = shapeCells({ x, y -> s.paintCell(x, y, inside) }, { x, y -> s.paintCell(x, y, color) })
        // Trop grande : la forme ne fait rien (ce qu'elle avait déjà peint est remis).
        if (!whole || s.pixelEditCells > ZoomScene.MAX_FILL_CELLS) {
            s.cancelPixelEdit()
            return
        }
        if (s.endPixelEdit()) listener?.onDrawingChanged()
    }

    /** L'aperçu de la forme en cours, bout de ligne par bout de ligne. */
    private fun drawPixelPreview(s: ZoomScene, canvas: Canvas) {
        if (previewFill.count == 0 && previewLine.count == 0) return
        paintPreview(s, canvas, previewFill, shapeFillColor())
        paintPreview(s, canvas, previewLine, color)
    }

    private fun paintPreview(s: ZoomScene, canvas: Canvas, runs: CellRuns, c: Int) {
        val w = width.toDouble()
        val h = height.toDouble()
        previewPaint.color = c
        var i = 0
        while (i < runs.n) {
            val l = ((runs.a[i] - s.cx) * s.zoom + w / 2).roundToInt().toFloat()
            val r = ((runs.a[i + 1] + 1 - s.cx) * s.zoom + w / 2).roundToInt().toFloat()
            val t = ((runs.a[i + 2] - s.cy) * s.zoom + h / 2).roundToInt().toFloat()
            val b = ((runs.a[i + 2] + 1 - s.cy) * s.zoom + h / 2).roundToInt().toFloat()
            // Un long bout est rogné au bord de l'écran (le moteur graphique n'aime pas les grands nombres).
            if (r >= 0 && b >= 0 && l <= w && t <= h) canvas.drawRect(max(l, -1f), t, min(r, w.toFloat() + 1f), b, previewPaint)
            i += 3
        }
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
        val selBox = sel?.let { s.box(it) }
        if (sel != null && selBox != null) {
            val r = screenRect(s, selBox)
            // Le coin le plus proche du doigt (0 haut-gauche, 1 haut-droit, 2 bas-droit, 3 bas-gauche).
            var i = 0
            var best = Double.POSITIVE_INFINITY
            for (k in 0 until 4) {
                val d = hypot((if (k == 0 || k == 3) r[0] else r[2]) - x, (if (k < 2) r[1] else r[3]) - y)
                if (d < best) { best = d; i = k }
            }
            if (best <= handleReach && s.beginBoxEdit(sel)) {
                resizeCorner = i
                return Mode.ITEM_RESIZE
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
        /** Bouts de ligne d'un aperçu de forme au plus (au-delà, l'aperçu est tronqué ; la forme, elle, est posée en entier). */
        const val PREVIEW_MAX = 60_000
        /**
         * Cases qu'une forme de pixels peut envoyer, empreintes du pinceau comprises (elles se chevauchent :
         * un contour au pinceau de 16 envoie chaque case plusieurs fois). Le garde-fou de [shapeCells].
         */
        const val MAX_SHAPE_PLOTS = ZoomScene.MAX_FILL_CELLS * 16
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

    // Sans liste ni tableau : un pincement arrive à chaque image.
    private fun startPinch(e: MotionEvent, skip: Int = -1) {
        pinchSkip = skip
        var n = 0
        var sx = 0.0
        var sy = 0.0
        var a = -1
        var b = -1
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i); sy += e.getY(i); n++
            if (a < 0) a = i else if (b < 0) b = i
        }
        if (n == 0) return
        pinchCx = (sx / n).toFloat()
        pinchCy = (sy / n).toFloat()
        pinchDist = if (b >= 0) hypot(e.getX(a) - e.getX(b), e.getY(a) - e.getY(b)) else 0f
    }

    private fun movePinch(s: ZoomScene, e: MotionEvent) {
        // Les deux premiers doigts (hors celui qui vient de se lever).
        var a = -1
        var b = -1
        for (i in 0 until e.pointerCount) {
            if (i == pinchSkip) continue
            if (a < 0) a = i else { b = i; break }
        }
        if (b < 0) return
        val cx = (e.getX(a) + e.getX(b)) / 2f
        val cy = (e.getY(a) + e.getY(b)) / 2f
        val d = hypot(e.getX(a) - e.getX(b), e.getY(a) - e.getY(b))
        s.pan((cx - pinchCx).toDouble(), (cy - pinchCy).toDouble())
        if (!zoomLocked && pinchDist > 0f && d > 0f) s.zoomAt((d / pinchDist).toDouble(), ox(cx), oy(cy))
        pinchCx = cx; pinchCy = cy; pinchDist = max(d, 1f)
        cameraMoved()
    }
}
