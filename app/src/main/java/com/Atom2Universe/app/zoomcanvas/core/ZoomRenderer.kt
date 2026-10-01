package com.Atom2Universe.app.zoomcanvas.core

import kotlin.math.max
import kotlin.math.min

/**
 * Ce qu'il faut tracer, dans l'ordre, prêt pour le moteur graphique : des polylignes et des images
 * en coordonnées d'écran (`Float`), déjà découpées au bord de la vue. Réutilisée d'une image à
 * l'autre (aucune allocation une fois les tableaux à la bonne taille).
 *
 * Chaque élément est une « passe » : une polyligne ([runImage] = -1) ou une image ([runImage] =
 * indice dans [imageKeys], [imageSrc], [imageDst]). Les passes d'une même couche se suivent et
 * forment un groupe ([groupStart]..[groupEnd]) : une couche qui contient un coup de gomme, ou qui
 * s'efface, doit être composée à part ([groupIsolated]) pour que la gomme ne creuse qu'elle.
 */
class RenderList {
    /** Points (x, y) de toutes les polylignes, à la suite. */
    var coords = FloatArray(4096)
        private set
    var coordCount = 0
        private set

    var runCount = 0
        private set
    /** Pour chaque polyligne : indice de son premier nombre dans [coords], et son nombre de points. */
    var runStart = IntArray(256)
        private set
    var runPoints = IntArray(256)
        private set
    var runColor = IntArray(256)
        private set
    var runWidth = FloatArray(256)
        private set
    var runAlpha = FloatArray(256)
        private set
    /** -1 pour une polyligne, sinon l'indice de l'image. */
    var runImage = IntArray(256)
        private set
    /** Un coup de gomme : il efface, dans son groupe, ce qui a été tracé avant lui. */
    var runErase = BooleanArray(256)
        private set
    /** L'outil du trait ([Stroke.PEN], [Stroke.BRUSH], [Stroke.MARKER]). */
    var runKind = ByteArray(256)
        private set
    /**
     * L'épaisseur à l'écran en chaque point (un nombre par point, dans l'ordre de [coords]) : la
     * même partout sauf pour le pinceau, qui s'affine aux bouts.
     */
    var pointWidth = FloatArray(2048)
        private set

    var groupCount = 0
        private set
    /** Pour chaque couche tracée : ses passes [groupStart, groupEnd[, à la suite. */
    var groupStart = IntArray(4)
        private set
    var groupEnd = IntArray(4)
        private set
    /** Composer la couche à part (gomme ou fondu), puis la poser avec l'opacité [groupAlpha]. */
    var groupIsolated = BooleanArray(4)
        private set
    var groupAlpha = FloatArray(4)
        private set
    private var openGroup = false

    var imageCount = 0
        private set
    /** Fichier de l'image (clé), et sa taille en pixels. */
    var imageKeys = arrayOfNulls<String>(16)
        private set
    var imagePx = IntArray(32)
        private set
    /** Rectangle source, en pixels de l'image : gauche, haut, droite, bas. */
    var imageSrc = FloatArray(64)
        private set
    /** Rectangle de destination à l'écran : gauche, haut, droite, bas (toujours dans la vue). */
    var imageDst = FloatArray(64)
        private set

    /** Nombre de couches effectivement dessinées (pour l'indicateur et les tests). */
    var layersDrawn = 0
        internal set

    /**
     * Première passe de la couche du dessus en train de s'effacer, tracée en dernier ([runCount]
     * s'il n'y en a pas). Ses passes sont opaques : l'opacité [fadeAlpha] s'applique d'un bloc à
     * toute la couche, pour que deux traits qui se croisent ne foncent pas.
     */
    var fadeStart = 0
        internal set
    var fadeAlpha = 1f
        internal set

    fun clear() {
        coordCount = 0
        runCount = 0
        imageCount = 0
        layersDrawn = 0
        fadeStart = 0
        fadeAlpha = 1f
        groupCount = 0
        openGroup = false
    }

    internal fun beginGroup(isolated: Boolean, alpha: Float) {
        if (groupCount == groupStart.size) {
            val n = groupCount * 2
            groupStart = groupStart.copyOf(n); groupEnd = groupEnd.copyOf(n)
            groupIsolated = groupIsolated.copyOf(n); groupAlpha = groupAlpha.copyOf(n)
        }
        groupStart[groupCount] = runCount
        groupIsolated[groupCount] = isolated
        groupAlpha[groupCount] = alpha
        openGroup = true
    }

    /** Ferme le groupe ouvert ; un groupe sans passe n'est pas gardé. */
    internal fun endGroup() {
        if (!openGroup) return
        openGroup = false
        if (runCount == groupStart[groupCount]) return
        groupEnd[groupCount] = runCount
        groupCount++
    }

    internal fun beginRun(color: Int, width: Float, alpha: Float, erase: Boolean = false, kind: Int = Stroke.PEN) {
        if (runCount == runStart.size) {
            val n = runCount * 2
            runStart = runStart.copyOf(n); runPoints = runPoints.copyOf(n)
            runColor = runColor.copyOf(n); runWidth = runWidth.copyOf(n); runAlpha = runAlpha.copyOf(n)
            runImage = runImage.copyOf(n); runErase = runErase.copyOf(n); runKind = runKind.copyOf(n)
        }
        runStart[runCount] = coordCount
        runPoints[runCount] = 0
        runColor[runCount] = color
        runWidth[runCount] = width
        runAlpha[runCount] = alpha
        runImage[runCount] = -1
        runErase[runCount] = erase
        runKind[runCount] = kind.toByte()
        runCount++
    }

    internal fun addImage(item: ImageItem, src: DoubleArray, dst: DoubleArray, alpha: Float) {
        if (imageCount == imageKeys.size) {
            val n = imageCount * 2
            imageKeys = imageKeys.copyOf(n); imagePx = imagePx.copyOf(2 * n)
            imageSrc = imageSrc.copyOf(4 * n); imageDst = imageDst.copyOf(4 * n)
        }
        val i = imageCount++
        imageKeys[i] = item.key
        imagePx[2 * i] = item.pxW
        imagePx[2 * i + 1] = item.pxH
        for (k in 0 until 4) {
            imageSrc[4 * i + k] = src[k].toFloat()
            imageDst[4 * i + k] = dst[k].toFloat()
        }
        beginRun(0, 0f, alpha)
        runImage[runCount - 1] = i
    }

    internal fun addPoint(x: Double, y: Double, width: Double = runWidth[runCount - 1].toDouble()) {
        if (coordCount + 2 > coords.size) coords = coords.copyOf(coords.size * 2)
        if (coordCount / 2 >= pointWidth.size) pointWidth = pointWidth.copyOf(pointWidth.size * 2)
        pointWidth[coordCount / 2] = width.toFloat()
        coords[coordCount++] = x.toFloat()
        coords[coordCount++] = y.toFloat()
        runPoints[runCount - 1]++
    }

}

/**
 * Calcule l'image : de l'arrière vers l'avant, la couche d'en dessous la couche de travail (à sa
 * propre échelle), la couche de travail et son trait en cours, et enfin, juste après le seuil, la
 * couche du dessus qui s'efface (voir [RenderList.fadeStart]). Dans chaque couche, tout est tracé
 * dans l'ordre où ça a été posé : un coup de gomme creuse ce qui est avant lui, pas ce qui suit.
 *
 * Tout se calcule en `Double` relativement à la caméra (position de l'objet − position de la
 * caméra, dans le repère de sa couche) ; seul le petit résultat, déjà découpé au bord de l'écran,
 * passe en `Float`. Aucune valeur plus grande que l'écran (plus une marge) n'en sort, à aucun niveau.
 */
object ZoomRenderer {

    /** En dessous de cette taille à l'écran (pixels), une couche ou un trait n'est pas dessiné. */
    const val MIN_PX = 1.0
    /** Épaisseur minimale tracée : plus fin, le trait s'éclaircit au lieu de disparaître. */
    const val MIN_WIDTH_PX = 0.8
    /** Les couches vues depuis la couche de travail vers le bas : elle et celle d'en dessous. */
    const val MAX_LAYERS = 2

    private class Visible(val layer: Layer, val x: Double, val y: Double, val zoom: Double)

    fun build(scene: ZoomScene, viewW: Double, viewH: Double, out: RenderList) {
        out.clear()
        val hw = viewW / 2
        val hh = viewH / 2

        // Plus grande étendue de contenu à partir de chaque profondeur : on s'arrête dès que tout
        // ce qui reste en dessous ferait moins d'un pixel.
        val first = scene.depth
        val last = scene.lastDepth
        val count = (last - first + 1).toInt()
        val suffixExtent = DoubleArray(count + 1)
        for (i in count - 1 downTo 0) {
            suffixExtent[i] = max(suffixExtent[i + 1], scene.layer(first + i)?.extent() ?: 0.0)
        }

        val visible = ArrayList<Visible>(4)
        var x = scene.cx
        var y = scene.cy
        var z = scene.zoom
        for (i in 0 until count) {
            val l = scene.layer(first + i) ?: break
            if (i > 0) {
                x = (x - l.ax) * scene.ratio
                y = (y - l.ay) * scene.ratio
                z /= scene.ratio
            }
            if (!x.isFinite() || !y.isFinite() || z <= 0.0) break
            if (suffixExtent[i] * z < MIN_PX) break
            if (i >= MAX_LAYERS) break
            if (!l.isEmpty) visible.add(Visible(l, x, y, z))
        }

        // Du fond vers l'avant : la couche d'en dessous…
        for (k in visible.indices.reversed()) {
            val v = visible[k]
            if (v.layer.depth == scene.depth) continue
            out.beginGroup(v.layer.hasEraser, 1f)
            drawLayer(v, scene.layerAlpha(v.layer.depth), hw, hh, viewW, viewH, out)
            out.endGroup()
        }
        // … puis la couche de travail, avec le trait en cours (qui peut être un coup de gomme).
        val live = scene.liveStroke()
        val working = visible.firstOrNull { it.layer.depth == scene.depth }
        out.beginGroup(working?.layer?.hasEraser == true || live?.isEraser == true, 1f)
        if (working != null) drawLayer(working, 1.0, hw, hh, viewW, viewH, out)
        live?.let { emit(it, scene.cx, scene.cy, scene.zoom, 1.0, hw, hh, viewW, viewH, out) }
        out.endGroup()

        // Juste après le seuil, la couche du dessus reste par-dessus tout le reste et s'efface.
        out.fadeStart = out.runCount
        val upAlpha = scene.upperAlpha()
        val up = scene.layer(scene.depth - 1)
        if (up != null && !up.isEmpty && upAlpha > 0.0) {
            val v = scene.viewOf(up.depth)
            if (v.x.isFinite() && v.y.isFinite() && v.zoom > 0.0) {
                out.beginGroup(true, upAlpha.toFloat())
                drawLayer(Visible(up, v.x, v.y, v.zoom), 1.0, hw, hh, viewW, viewH, out)
                out.endGroup()
                out.fadeAlpha = upAlpha.toFloat()
            }
        }
    }

    private fun drawLayer(v: Visible, alpha: Double, hw: Double, hh: Double, w: Double, h: Double, out: RenderList) {
        if (alpha <= 0.0) return
        if (!layerOnScreen(v, hw, hh, w, h)) return
        out.layersDrawn++
        for (item in v.layer.drawOrder()) when (item) {
            is ImageItem -> emitImage(item, v.x, v.y, v.zoom, alpha, hw, hh, w, h, out)
            is Stroke -> emit(item, v.x, v.y, v.zoom, alpha, hw, hh, w, h, out)
        }
    }

    private val src = DoubleArray(4)
    private val dst = DoubleArray(4)

    /**
     * Projette une image et ne garde que sa partie visible : le rectangle de destination est
     * découpé au bord de l'écran, et le rectangle source (dans les pixels de l'image) suit.
     */
    private fun emitImage(item: ImageItem, vx: Double, vy: Double, z: Double, alpha: Double, hw: Double, hh: Double, w: Double, h: Double, out: RenderList) {
        val x0 = (item.x - item.w / 2 - vx) * z + hw
        val y0 = (item.y - item.h / 2 - vy) * z + hh
        val sw = item.w * z
        val sh = item.h * z
        if (sw < MIN_PX && sh < MIN_PX) return
        val cx0 = max(x0, -1.0)
        val cy0 = max(y0, -1.0)
        val cx1 = min(x0 + sw, w + 1)
        val cy1 = min(y0 + sh, h + 1)
        if (cx1 <= cx0 || cy1 <= cy0) return
        dst[0] = cx0; dst[1] = cy0; dst[2] = cx1; dst[3] = cy1
        src[0] = (cx0 - x0) / sw * item.pxW
        src[1] = (cy0 - y0) / sh * item.pxH
        src[2] = (cx1 - x0) / sw * item.pxW
        src[3] = (cy1 - y0) / sh * item.pxH
        out.addImage(item, src, dst, alpha.toFloat())
    }

    private fun layerOnScreen(v: Visible, hw: Double, hh: Double, w: Double, h: Double): Boolean {
        val b = v.layer.bounds() ?: return false
        val x0 = (b[0] - v.x) * v.zoom + hw
        val y0 = (b[1] - v.y) * v.zoom + hh
        val x1 = (b[2] - v.x) * v.zoom + hw
        val y1 = (b[3] - v.y) * v.zoom + hh
        if (x1 < 0 || y1 < 0 || x0 > w || y0 > h) return false
        return x1 - x0 >= MIN_PX || y1 - y0 >= MIN_PX
    }

    /** Projette un trait (repère de sa couche, vu d'une caméra [vx], [vy], [z]) et le découpe à l'écran. */
    private fun emit(s: Stroke, vx: Double, vy: Double, z: Double, alpha: Double, hw: Double, hh: Double, w: Double, h: Double, out: RenderList) {
        val widthPx = s.width * z
        val half = max(widthPx, MIN_WIDTH_PX) / 2 + 1.0
        val ox = (s.x - vx) * z + hw
        val oy = (s.y - vy) * z + hh
        val bx0 = ox + s.minX * z - half
        val by0 = oy + s.minY * z - half
        val bx1 = ox + s.maxX * z + half
        val by1 = oy + s.maxY * z + half
        if (!(bx1 >= 0 && by1 >= 0 && bx0 <= w && by0 <= h)) return
        if ((s.maxX - s.minX) * z < MIN_PX && (s.maxY - s.minY) * z < MIN_PX && widthPx < MIN_PX) return

        val drawnWidth = min(max(widthPx, MIN_WIDTH_PX), 4 * max(w, h))
        val a = (alpha * if (widthPx < MIN_WIDTH_PX) max(widthPx / MIN_WIDTH_PX, 0.3) else 1.0).toFloat()
        // Zone de découpe : l'écran plus de quoi ne pas couper l'épaisseur du trait.
        val m = drawnWidth / 2 + 2.0
        val cx0 = -m
        val cy0 = -m
        val cx1 = w + m
        val cy1 = h + m
        val p = s.pts
        val n = p.size / 2

        if (n == 1) {
            if (ox in cx0..cx1 && oy in cy0..cy1) {
                out.beginRun(s.color, drawnWidth.toFloat(), a, s.isEraser, s.kind)
                out.addPoint(ox, oy)
            }
            return
        }

        // Le pinceau : l'épaisseur de chaque point dépend de sa distance (le long du trait) au bout
        // le plus proche. [along] est la distance parcourue jusqu'au point A, en unités de la couche.
        val brush = s.kind == Stroke.BRUSH
        val total = if (brush) s.length() else 0.0
        var along = 0.0
        var open = false
        var ax = ox + p[0] * z
        var ay = oy + p[1] * z
        for (i in 1 until n) {
            val bx = ox + p[2 * i] * z
            val by = oy + p[2 * i + 1] * z
            val seg = if (brush) Math.hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1]) else 0.0
            if (!clip(ax, ay, bx, by, cx0, cy0, cx1, cy1)) {
                open = false
            } else {
                val dx = bx - ax
                val dy = by - ay
                if (!open) {
                    out.beginRun(s.color, drawnWidth.toFloat(), a, s.isEraser, s.kind)
                    out.addPoint(ax + t0 * dx, ay + t0 * dy, if (brush) brushWidth(s, along + t0 * seg, total, drawnWidth) else drawnWidth)
                }
                out.addPoint(ax + t1 * dx, ay + t1 * dy, if (brush) brushWidth(s, along + t1 * seg, total, drawnWidth) else drawnWidth)
                open = t1 >= 1.0
            }
            along += seg
            ax = bx
            ay = by
        }
    }

    /** Épaisseur à l'écran du pinceau à [d] du début d'un trait long de [total]. */
    private fun brushWidth(s: Stroke, d: Double, total: Double, full: Double): Double =
        full * Stroke.brushFactor(min(d, total - d), s.width)

    // Résultat de [clip] (le rendu se fait sur un seul fil).
    private var t0 = 0.0
    private var t1 = 1.0

    /**
     * Découpe de Liang-Barsky : garde dans [t0, t1] la part du segment A→B qui reste dans le
     * rectangle ; faux s'il en sort entièrement.
     */
    private fun clip(ax: Double, ay: Double, bx: Double, by: Double, x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        t0 = 0.0
        t1 = 1.0
        val dx = bx - ax
        val dy = by - ay
        return edge(-dx, ax - x0) && edge(dx, x1 - ax) && edge(-dy, ay - y0) && edge(dy, y1 - ay)
    }

    private fun edge(p: Double, q: Double): Boolean {
        if (p == 0.0) return q >= 0
        val r = q / p
        if (p < 0) {
            if (r > t1) return false
            if (r > t0) t0 = r
        } else {
            if (r < t0) return false
            if (r < t1) t1 = r
        }
        return true
    }
}
