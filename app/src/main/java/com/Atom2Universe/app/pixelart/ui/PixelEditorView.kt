package com.Atom2Universe.app.pixelart.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.pixelart.core.Compositor
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.PixelRect
import com.Atom2Universe.app.pixelart.core.Raster
import com.Atom2Universe.app.pixelart.core.Tool
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class BackgroundStyle { CHECKER_LIGHT, CHECKER_DARK, WHITE, GRAY, BLACK }

/**
 * La zone de dessin : affiche l'image composée du [EditorSession] (plus proche voisin, jamais lissée),
 * gère le zoom / déplacement à deux doigts et convertit un doigt en pixels pour la session.
 *
 * La vue ne modifie jamais le document : elle répète à la session ce que fait le doigt, et l'activité
 * lui renvoie ce qui a changé ([refreshRegion], [refreshAll], [selectionChanged], [floatingChanged]).
 */
class PixelEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Host {
        fun onViewportChanged(scale: Float) {}
        /** Un doigt touche la zone pendant une lecture d'animation : la lecture doit s'arrêter. */
        fun onTouchWhilePlaying() {}
        /** Le déplacement de l'image de référence a changé. */
        fun onReferenceMoved(scale: Float, x: Float, y: Float) {}
    }

    var host: Host? = null
    private var session: EditorSession? = null

    // ---- Affichage -------------------------------------------------------------------------

    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var docW = 0
    private var docH = 0
    private var currentFrameId = -1

    var scale = 8f
        private set
    private var panX = 0f
    private var panY = 0f
    private var fitted = false
    private val density = resources.displayMetrics.density
    private val minScale get() = max(0.05f, fitScale() * 0.25f)
    private val maxScale get() = 96f * max(1f, density / 2.5f)

    var background: BackgroundStyle = BackgroundStyle.CHECKER_DARK
        set(v) { field = v; rebuildChecker(); invalidate() }
    var showPixelGrid = true
        set(v) { field = v; invalidate() }
    /** Une ligne plus marquée tous les N pixels (0 = aucune). */
    var tileGrid = 0
        set(v) { field = v; invalidate() }

    private val outsidePaint = Paint().apply { color = 0xFF0E1218.toInt() }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val checkerPaint = Paint()
    private val solidPaint = Paint()
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE; color = 0x66FFFFFF; strokeWidth = 1f
    }
    private val gridPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x33808080 }
    private val tileGridPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0x99FF4F8B.toInt() }
    private val axisPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0xCC29D3C8.toInt()
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val antsBlack = Paint().apply { style = Paint.Style.STROKE; color = Color.BLACK; isAntiAlias = false }
    private val antsWhite = Paint().apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = false }
    private val cursorPaint = Paint().apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 1.5f * density }
    private val cursorShadow = Paint().apply { style = Paint.Style.STROKE; color = 0xFF000000.toInt(); strokeWidth = 3f * density }
    private val onionPaint = Paint().apply { isFilterBitmap = false }
    private val refPaint = Paint().apply { isFilterBitmap = true }
    private val tmpRect = RectF()
    private val tmpPixRect = PixelRect.empty()

    private var checkerShader: BitmapShader? = null
    private var checkerTile: Bitmap? = null
    private val checkerMatrix = Matrix()

    private var gridLines = FloatArray(0)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isFocusable = true
        rebuildChecker()
    }

    fun bind(s: EditorSession) {
        session = s
        fitted = false
        rebuildBitmap()
        // La vue a peut-être déjà sa taille : onSizeChanged ne repassera pas pour cadrer le dessin.
        if (width > 0 && height > 0) fit()
        invalidate()
    }

    // ---- Bitmap composé ------------------------------------------------------------------------

    private fun rebuildBitmap() {
        val s = session ?: return
        val d = s.doc
        if (bitmap == null || docW != d.width || docH != d.height) {
            val firstTime = bitmap == null
            bitmap?.recycle()
            docW = d.width
            docH = d.height
            bitmap = Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888)
            pixels = IntArray(docW * docH)
            fitted = false
            // Une taille de toile différente (rotation, redimensionnement, annulation) : on recadre.
            if (!firstTime && width > 0 && height > 0) fit()
        }
        currentFrameId = playbackFrame ?: s.activeFrameId
        Compositor.composite(d, currentFrameId, pixels)
        bitmap!!.setPixels(pixels, 0, docW, 0, 0, docW, docH)
        selectionPathDirty = true
    }

    /** Une zone de l'image composée a changé (un trait en cours) : on ne recompose que celle-là. */
    fun refreshRegion(r: PixelRect) {
        val s = session ?: return
        val bmp = bitmap ?: return
        if (playbackFrame != null) return
        val l = r.left.coerceIn(0, docW); val t = r.top.coerceIn(0, docH)
        val rr = r.right.coerceIn(0, docW); val b = r.bottom.coerceIn(0, docH)
        if (l >= rr || t >= b) return
        Compositor.composite(s.doc, s.activeFrameId, pixels, l, t, rr, b)
        bmp.setPixels(pixels, t * docW + l, docW, l, t, rr - l, b - t)
        invalidate()
    }

    /** Tout est à recomposer (annuler, changement d'image ou de calque, taille…). */
    fun refreshAll() {
        rebuildBitmap()
        refreshOnion()
        invalidate()
    }

    fun selectionChanged() {
        selectionPathDirty = true
        invalidate()
    }

    fun overlayChanged() = invalidate()

    // ---- Lecture d'animation -----------------------------------------------------------------

    private var playbackFrame: Int? = null
    private var playbackCache: HashMap<Int, Bitmap>? = null

    /** Affiche l'image [frameId] sans toucher à la session (lecture) ; `null` revient à l'édition. */
    fun showFrame(frameId: Int?) {
        val s = session ?: return
        if (frameId == null) {
            playbackFrame = null
            playbackCache?.values?.forEach { it.recycle() }
            playbackCache = null
            rebuildBitmap()
            invalidate()
            return
        }
        playbackFrame = frameId
        val cache = playbackCache ?: HashMap<Int, Bitmap>().also { playbackCache = it }
        val bmp = cache.getOrPut(frameId) {
            val px = Compositor.compositeFrame(s.doc, frameId)
            Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, docW, 0, 0, docW, docH) }
        }
        playbackBitmap = bmp
        invalidate()
    }

    private var playbackBitmap: Bitmap? = null

    // ---- Pelure d'oignon ------------------------------------------------------------------------

    var onionEnabled = false
        set(v) { field = v; refreshOnion(); invalidate() }
    var onionOpacity = 0.4f
        set(v) { field = v; invalidate() }
    private var onionPrev: Bitmap? = null
    private var onionNext: Bitmap? = null
    private val onionPrevFilter = PorterDuffColorFilter(0xFFFF5A5A.toInt(), PorterDuff.Mode.SRC_ATOP)
    private val onionNextFilter = PorterDuffColorFilter(0xFF4FC3F7.toInt(), PorterDuff.Mode.SRC_ATOP)

    private fun refreshOnion() {
        onionPrev?.recycle(); onionPrev = null
        onionNext?.recycle(); onionNext = null
        val s = session ?: return
        if (!onionEnabled) return
        val i = s.frameIndex
        fun make(idx: Int): Bitmap? {
            if (idx !in s.doc.frames.indices) return null
            val px = Compositor.compositeFrame(s.doc, s.doc.frames[idx].id)
            return Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, docW, 0, 0, docW, docH) }
        }
        onionPrev = make(i - 1)
        onionNext = make(i + 1)
    }

    // ---- Image de référence --------------------------------------------------------------------

    private var reference: Bitmap? = null
    var referenceScale = 1f
        private set
    var referenceX = 0f
        private set
    var referenceY = 0f
        private set
    var referenceOpacity = 0.5f
        set(v) { field = v; invalidate() }
    var referenceVisible = true
        set(v) { field = v; invalidate() }

    /**
     * 0 = fond de la toile opaque, 1 = fond invisible. Ne joue que si une référence est affichée :
     * la référence est dessinée SOUS la toile, et c'est ce fond translucide qui la laisse voir à travers.
     */
    var canvasTransparency = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    /** Vrai : un doigt / un pincement déplacent la référence au lieu de la toile. */
    var referenceEditing = false

    fun setReference(bmp: Bitmap?, scale: Float = 1f, x: Float = 0f, y: Float = 0f) {
        reference?.recycle()
        reference = bmp
        referenceScale = scale
        referenceX = x
        referenceY = y
        invalidate()
    }

    val hasReference get() = reference != null

    /** Couleur de l'image de référence sous le pixel (x, y) du dessin, ou 0 (masquée, hors image, transparente). */
    fun referenceColorAt(x: Int, y: Int): Int {
        val r = reference ?: return 0
        if (!referenceVisible) return 0
        val rx = floor((x + 0.5f - referenceX) / referenceScale).toInt()
        val ry = floor((y + 0.5f - referenceY) / referenceScale).toInt()
        if (rx !in 0 until r.width || ry !in 0 until r.height) return 0
        return r.getPixel(rx, ry)
    }

    /** Cale la référence sur toute la toile (elle la couvre, centrée) — c'est le réglage de départ. */
    fun fitReference() {
        val r = reference ?: return
        val k = max(docW.toFloat() / r.width, docH.toFloat() / r.height)
        referenceScale = k
        referenceX = (docW - r.width * k) / 2f
        referenceY = (docH - r.height * k) / 2f
        host?.onReferenceMoved(referenceScale, referenceX, referenceY)
        invalidate()
    }

    // ---- Vue : zoom / position ------------------------------------------------------------------

    private fun fitScale(): Float {
        if (docW == 0 || width == 0) return 1f
        val margin = 24f * density
        return min((width - margin) / docW, (height - margin) / docH).coerceAtLeast(0.05f)
    }

    fun fit() {
        if (docW == 0 || width == 0 || height == 0) return
        val k = fitScale()
        // Un zoom entier est plus net : chaque pixel du dessin occupe un nombre entier de pixels d'écran.
        scale = if (k >= 1f) floor(k).coerceAtLeast(1f) else k
        panX = (width - docW * scale) / 2f
        panY = (height - docH * scale) / 2f
        fitted = true
        host?.onViewportChanged(scale)
        invalidate()
    }

    fun zoomBy(factor: Float, cx: Float = width / 2f, cy: Float = height / 2f) {
        val ns = (scale * factor).coerceIn(minScale, maxScale)
        val k = ns / scale
        panX = cx - (cx - panX) * k
        panY = cy - (cy - panY) * k
        scale = ns
        clampPan()
        host?.onViewportChanged(scale)
        invalidate()
    }

    /** Zoom à 100 % : un pixel du dessin = un pixel de l'écran multiplié par un entier lisible. */
    fun zoomTo(value: Float) {
        zoomBy(value / scale)
    }

    private fun clampPan() {
        // Garder au moins une bande du dessin visible pour ne jamais le perdre hors de l'écran.
        val keep = 48f * density
        val w = docW * scale
        val h = docH * scale
        panX = panX.coerceIn(keep - w, width - keep)
        panY = panY.coerceIn(keep - h, height - keep)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (!fitted || ow == 0 || oh == 0) fit() else {
            panX += (w - ow) / 2f
            panY += (h - oh) / 2f
        }
    }

    /** Le point du dessin (pixel fractionnaire) situé au centre de la vue — pour coller au milieu. */
    fun centerPixel(): Pair<Int, Int> = floor((width / 2f - panX) / scale).toInt() to floor((height / 2f - panY) / scale).toInt()

    // ---- Dessin --------------------------------------------------------------------------------

    private fun rebuildChecker() {
        val cell = (8f * density).toInt().coerceAtLeast(4)
        val (c1, c2) = when (background) {
            BackgroundStyle.CHECKER_LIGHT -> 0xFFFFFFFF.toInt() to 0xFFD8D8D8.toInt()
            BackgroundStyle.CHECKER_DARK -> 0xFF3A404C.toInt() to 0xFF2D323C.toInt()
            else -> 0 to 0
        }
        solidPaint.color = when (background) {
            BackgroundStyle.WHITE -> 0xFFFFFFFF.toInt()
            BackgroundStyle.GRAY -> 0xFF8A8F98.toInt()
            BackgroundStyle.BLACK -> 0xFF000000.toInt()
            else -> 0
        }
        if (background == BackgroundStyle.CHECKER_LIGHT || background == BackgroundStyle.CHECKER_DARK) {
            checkerTile?.recycle()
            val tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(tile)
            val p = Paint()
            p.color = c1; c.drawRect(0f, 0f, cell * 2f, cell * 2f, p)
            p.color = c2
            c.drawRect(cell.toFloat(), 0f, cell * 2f, cell.toFloat(), p)
            c.drawRect(0f, cell.toFloat(), cell.toFloat(), cell * 2f, p)
            checkerTile = tile
            checkerShader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            checkerPaint.shader = checkerShader
        }
    }

    private var selectionPath = Path()
    private var selectionPathDirty = true
    private val lassoPath = Path()

    private fun rebuildSelectionPath() {
        selectionPath.reset()
        val sel = session?.selection ?: return
        val seg = sel.outline()
        var i = 0
        while (i < seg.size) {
            selectionPath.moveTo(seg[i].toFloat(), seg[i + 1].toFloat())
            selectionPath.lineTo(seg[i + 2].toFloat(), seg[i + 3].toFloat())
            i += 4
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawPaint(outsidePaint)
        val s = session ?: return
        val bmp = playbackBitmap.takeIf { playbackFrame != null } ?: bitmap ?: return
        val left = panX
        val top = panY
        val right = panX + docW * scale
        val bottom = panY + docH * scale

        // L'image de référence est SOUS le fond de la toile ; le fond, rendu translucide, la laisse voir.
        drawReference(c)
        val backAlpha = if (reference != null && referenceVisible) ((1f - canvasTransparency) * 255).toInt() else 255
        checkerPaint.alpha = backAlpha
        solidPaint.alpha = backAlpha
        if (background == BackgroundStyle.CHECKER_LIGHT || background == BackgroundStyle.CHECKER_DARK) {
            checkerMatrix.setTranslate(left, top)
            checkerShader?.setLocalMatrix(checkerMatrix)
            c.drawRect(left, top, right, bottom, checkerPaint)
        } else {
            c.drawRect(left, top, right, bottom, solidPaint)
        }

        // Pelure d'oignon : silhouettes teintées de l'image d'avant (rouge) et d'après (bleu).
        if (onionEnabled && playbackFrame == null) {
            tmpRect.set(left, top, right, bottom)
            onionPaint.alpha = (onionOpacity * 255).toInt().coerceIn(0, 255)
            onionPrev?.let { onionPaint.colorFilter = onionPrevFilter; c.drawBitmap(it, null, tmpRect, onionPaint) }
            onionNext?.let { onionPaint.colorFilter = onionNextFilter; c.drawBitmap(it, null, tmpRect, onionPaint) }
            onionPaint.colorFilter = null
        }

        tmpRect.set(left, top, right, bottom)
        c.drawBitmap(bmp, null, tmpRect, bitmapPaint)

        if (playbackFrame == null) {
            drawFloating(c, s)
            drawGrid(c, left, top, right, bottom)
            drawSymmetry(c, s, left, top, right, bottom)
            drawSelection(c, s)
            drawCursor(c, s)
        }
        c.drawRect(left - 0.5f, top - 0.5f, right + 0.5f, bottom + 0.5f, borderPaint)
    }

    private fun drawReference(c: Canvas) {
        val r = reference ?: return
        if (!referenceVisible) return
        refPaint.alpha = (referenceOpacity * 255).toInt().coerceIn(0, 255)
        tmpRect.set(
            panX + referenceX * scale, panY + referenceY * scale,
            panX + (referenceX + r.width * referenceScale) * scale, panY + (referenceY + r.height * referenceScale) * scale,
        )
        c.drawBitmap(r, null, tmpRect, refPaint)
        if (referenceEditing) c.drawRect(tmpRect, cursorPaint)
    }

    private fun drawGrid(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val vx0 = max(left, 0f); val vx1 = min(right, width.toFloat())
        val vy0 = max(top, 0f); val vy1 = min(bottom, height.toFloat())
        if (vx0 >= vx1 || vy0 >= vy1) return
        if (showPixelGrid && scale >= 8f) {
            val x0 = floor((vx0 - panX) / scale).toInt().coerceAtLeast(0)
            val x1 = floor((vx1 - panX) / scale).toInt().coerceAtMost(docW)
            val y0 = floor((vy0 - panY) / scale).toInt().coerceAtLeast(0)
            val y1 = floor((vy1 - panY) / scale).toInt().coerceAtMost(docH)
            val n = (x1 - x0 + 1 + y1 - y0 + 1) * 4
            if (gridLines.size < n) gridLines = FloatArray(n + 64)
            var k = 0
            for (x in x0..x1) {
                val sx = panX + x * scale
                gridLines[k++] = sx; gridLines[k++] = vy0; gridLines[k++] = sx; gridLines[k++] = vy1
            }
            for (y in y0..y1) {
                val sy = panY + y * scale
                gridLines[k++] = vx0; gridLines[k++] = sy; gridLines[k++] = vx1; gridLines[k++] = sy
            }
            c.drawLines(gridLines, 0, k, gridPaint)
        }
        val step = tileGrid
        if (step > 1 && scale * step >= 6f) {
            var x = step
            while (x < docW) {
                val sx = panX + x * scale
                if (sx in vx0..vx1) c.drawLine(sx, vy0, sx, vy1, tileGridPaint)
                x += step
            }
            var y = step
            while (y < docH) {
                val sy = panY + y * scale
                if (sy in vy0..vy1) c.drawLine(vx0, sy, vx1, sy, tileGridPaint)
                y += step
            }
        }
    }

    private fun drawSymmetry(c: Canvas, s: EditorSession, left: Float, top: Float, right: Float, bottom: Float) {
        if (s.options.mirrorX) c.drawLine((left + right) / 2f, top, (left + right) / 2f, bottom, axisPaint)
        if (s.options.mirrorY) c.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, axisPaint)
    }

    // Contenu flottant : son bitmap est refait quand un nouveau contenu est détaché.
    private var floatBitmap: Bitmap? = null
    private var floatOwner: Any? = null

    fun floatingChanged() {
        val s = session ?: return
        val f = s.floating
        if (f == null) {
            floatBitmap?.recycle(); floatBitmap = null; floatOwner = null
        } else if (floatOwner !== f) {
            floatBitmap?.recycle()
            val px = IntArray(f.bw * f.bh) { if (f.mask[it].toInt() != 0) f.pixels[it] else 0 }
            floatBitmap = Bitmap.createBitmap(f.bw, f.bh, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, f.bw, 0, 0, f.bw, f.bh) }
            floatOwner = f
        }
        selectionPathDirty = true
        rebuildBitmap()
        invalidate()
    }

    private fun drawFloating(c: Canvas, s: EditorSession) {
        val f = s.floating ?: return
        val fb = floatBitmap ?: return
        tmpRect.set(panX + f.x * scale, panY + f.y * scale, panX + (f.x + f.bw) * scale, panY + (f.y + f.bh) * scale)
        c.drawBitmap(fb, null, tmpRect, bitmapPaint)
    }

    private fun drawSelection(c: Canvas, s: EditorSession) {
        val sel = s.selection
        val f = s.floating
        if (sel != null || f != null) {
            if (selectionPathDirty) { rebuildSelectionPath(); selectionPathDirty = false }
            c.save()
            c.translate(panX, panY)
            c.scale(scale, scale)
            if (f != null && sel != null) c.translate((f.x - f.originX).toFloat(), (f.y - f.originY).toFloat())
            val w = 1.5f * density / scale
            antsBlack.strokeWidth = w
            antsWhite.strokeWidth = w
            antsWhite.pathEffect = DashPathEffect(floatArrayOf(4f * density / scale, 4f * density / scale), 0f)
            if (sel != null) {
                c.drawPath(selectionPath, antsBlack)
                c.drawPath(selectionPath, antsWhite)
            } else if (f != null) {
                tmpRect.set(f.x.toFloat(), f.y.toFloat(), (f.x + f.bw).toFloat(), (f.y + f.bh).toFloat())
                c.drawRect(tmpRect, antsBlack)
                c.drawRect(tmpRect, antsWhite)
            }
            c.restore()
        }
        // Tracé en cours (cadre, lasso) : en coordonnées d'écran, l'épaisseur ne dépend pas du zoom.
        if (s.hasPreviewRect) {
            val r = s.previewRect
            tmpRect.set(panX + r.left * scale, panY + r.top * scale, panX + r.right * scale, panY + r.bottom * scale)
            antsBlack.strokeWidth = 1.5f * density
            antsWhite.strokeWidth = 1.5f * density
            antsWhite.pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
            c.drawRect(tmpRect, antsBlack)
            c.drawRect(tmpRect, antsWhite)
        }
        s.lassoPoints?.let { (xs, ys) ->
            lassoPath.reset()
            for (i in xs.indices) {
                val sx = panX + (xs[i] + 0.5f) * scale
                val sy = panY + (ys[i] + 0.5f) * scale
                if (i == 0) lassoPath.moveTo(sx, sy) else lassoPath.lineTo(sx, sy)
            }
            antsBlack.strokeWidth = 1.5f * density
            antsWhite.strokeWidth = 1.5f * density
            antsWhite.pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
            c.drawPath(lassoPath, antsBlack)
            c.drawPath(lassoPath, antsWhite)
        }
    }

    private var cursorX = 0
    private var cursorY = 0

    private fun drawCursor(c: Canvas, s: EditorSession) {
        if (mode != Mode.DRAW || !s.gestureActive) return
        val t = s.tool
        if (t != Tool.PENCIL && t != Tool.ERASER && t != Tool.BRUSH) return
        val n = s.options.size
        if (n <= 1) return
        val half = n / 2
        val l = panX + (cursorX - half) * scale
        val tp = panY + (cursorY - half) * scale
        tmpRect.set(l, tp, l + n * scale, tp + n * scale)
        c.drawRect(tmpRect, cursorShadow)
        c.drawRect(tmpRect, cursorPaint)
    }

    // ---- Doigts --------------------------------------------------------------------------------

    private enum class Mode { NONE, DRAW, PAN, GESTURE, IGNORE, REFERENCE }

    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var drawStart = 0L
    private var pinchDist = 0f
    private var pinchCx = 0f
    private var pinchCy = 0f

    private fun px(sx: Float) = floor((sx - panX) / scale).toInt()
    private fun py(sy: Float) = floor((sy - panY) / scale).toInt()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = session ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                if (playbackFrame != null) { host?.onTouchWhilePlaying(); mode = Mode.IGNORE; return true }
                when {
                    referenceEditing && reference != null -> mode = Mode.REFERENCE
                    s.tool == Tool.HAND -> mode = Mode.PAN
                    else -> {
                        mode = Mode.DRAW
                        drawStart = e.eventTime
                        cursorX = px(e.x); cursorY = py(e.y)
                        s.pointerDown(cursorX, cursorY)
                        invalidate()
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == Mode.DRAW) {
                    // Un doigt de plus tout de suite : c'était le début d'un pincement, pas un trait.
                    if (e.eventTime - drawStart < 350) s.pointerCancel() else s.pointerUp()
                }
                if (mode != Mode.IGNORE) {
                    mode = if (referenceEditing && reference != null) Mode.REFERENCE else Mode.GESTURE
                    startPinch(e)
                }
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.DRAW -> {
                    for (h in 0 until e.historySize) moveDraw(s, e.getHistoricalX(h), e.getHistoricalY(h))
                    moveDraw(s, e.x, e.y)
                }
                Mode.PAN -> {
                    panX += e.x - lastX; panY += e.y - lastY
                    lastX = e.x; lastY = e.y
                    clampPan(); invalidate()
                }
                Mode.GESTURE -> if (e.pointerCount >= 2) movePinch(e)
                Mode.REFERENCE -> moveReference(e)
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.GESTURE || mode == Mode.REFERENCE) {
                    if (e.pointerCount - 1 < 2) mode = Mode.IGNORE else startPinch(e, skip = e.actionIndex)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.DRAW) s.pointerUp()
                mode = Mode.NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.DRAW) s.pointerCancel()
                mode = Mode.NONE
                invalidate()
            }
        }
        return true
    }

    private fun moveDraw(s: EditorSession, x: Float, y: Float) {
        cursorX = px(x); cursorY = py(y)
        s.pointerMove(cursorX, cursorY)
        invalidate()
    }

    private fun startPinch(e: MotionEvent, skip: Int = -1) {
        var n = 0
        var sx = 0f; var sy = 0f
        val pts = ArrayList<Int>()
        for (i in 0 until e.pointerCount) if (i != skip) pts.add(i)
        for (i in pts) { sx += e.getX(i); sy += e.getY(i); n++ }
        pinchCx = sx / n; pinchCy = sy / n
        pinchDist = if (pts.size >= 2) hypot(e.getX(pts[0]) - e.getX(pts[1]), e.getY(pts[0]) - e.getY(pts[1])) else 0f
        pinchSkip = skip
    }

    private var pinchSkip = -1

    private fun pinchPoints(e: MotionEvent): Triple<Float, Float, Float>? {
        val pts = (0 until e.pointerCount).filter { it != pinchSkip }
        if (pts.size < 2) return null
        val cx = (e.getX(pts[0]) + e.getX(pts[1])) / 2f
        val cy = (e.getY(pts[0]) + e.getY(pts[1])) / 2f
        val d = hypot(e.getX(pts[0]) - e.getX(pts[1]), e.getY(pts[0]) - e.getY(pts[1]))
        return Triple(cx, cy, d)
    }

    private fun movePinch(e: MotionEvent) {
        val (cx, cy, d) = pinchPoints(e) ?: return
        if (pinchDist > 0f && d > 0f) {
            val ns = (scale * d / pinchDist).coerceIn(minScale, maxScale)
            val k = ns / scale
            panX = cx - (pinchCx - panX) * k + 0f
            panY = cy - (pinchCy - panY) * k + 0f
            scale = ns
        } else {
            panX += cx - pinchCx
            panY += cy - pinchCy
        }
        pinchCx = cx; pinchCy = cy; pinchDist = d
        clampPan()
        host?.onViewportChanged(scale)
        invalidate()
    }

    private fun moveReference(e: MotionEvent) {
        if (e.pointerCount >= 2) {
            val (cx, cy, d) = pinchPoints(e) ?: return
            if (pinchDist > 0f && d > 0f) {
                val k = d / pinchDist
                val newScale = (referenceScale * k).coerceIn(0.02f, 64f)
                // La référence grandit autour du doigt central.
                val fx = (cx - panX) / scale
                val fy = (cy - panY) / scale
                referenceX = fx - (fx - referenceX) * (newScale / referenceScale) + (cx - pinchCx) / scale
                referenceY = fy - (fy - referenceY) * (newScale / referenceScale) + (cy - pinchCy) / scale
                referenceScale = newScale
            }
            pinchCx = cx; pinchCy = cy; pinchDist = d
        } else {
            referenceX += (e.x - lastX) / scale
            referenceY += (e.y - lastY) / scale
            lastX = e.x; lastY = e.y
        }
        host?.onReferenceMoved(referenceScale, referenceX, referenceY)
        invalidate()
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) {
                zoomBy(if (v > 0) 1.15f else 1f / 1.15f, e.x, e.y)
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmap?.recycle(); bitmap = null
        onionPrev?.recycle(); onionNext?.recycle(); floatBitmap?.recycle()
        playbackCache?.values?.forEach { it.recycle() }
    }
}
