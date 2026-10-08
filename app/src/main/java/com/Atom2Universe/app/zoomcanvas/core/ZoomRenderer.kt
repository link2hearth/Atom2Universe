package com.Atom2Universe.app.zoomcanvas.core

import com.Atom2Universe.app.pixelart.core.ShapeKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Calcule l'image : de l'arrière vers l'avant, la couche d'en dessous la couche de travail (à sa
 * propre échelle), la couche de travail et son trait en cours, et enfin, juste après le seuil, la
 * couche du dessus qui s'efface (voir [RenderList.fadeStart]). Dans chaque couche, tout est tracé
 * dans l'ordre où ça a été posé : un coup de gomme creuse ce qui est avant lui, pas ce qui suit.
 *
 * Tout se calcule en `Double` relativement à la caméra (position de l'objet − position de la
 * caméra, dans le repère de sa couche) ; seul le petit résultat, déjà découpé au bord de l'écran,
 * passe en `Float`. Aucune valeur plus grande que l'écran (plus une marge) n'en sort, à aucun niveau.
 *
 * Une couche ne parcourt que ce qui touche l'écran (son index spatial, voir [Layer.query]) : le
 * coût d'une image suit ce qu'on voit, pas le nombre de traits du dessin.
 */
object ZoomRenderer {

    /** En dessous de cette taille à l'écran (pixels), une couche ou un trait n'est pas dessiné. */
    const val MIN_PX = 1.0
    /** Épaisseur minimale tracée : plus fin, le trait s'éclaircit au lieu de disparaître. */
    const val MIN_WIDTH_PX = 0.8
    /** Les couches vues depuis la couche de travail vers le bas : elle et celle d'en dessous. */
    const val MAX_LAYERS = 2
    /** Une police plus grande que ça à l'écran n'est pas dessinée (le moteur graphique ne suivrait pas). */
    const val MAX_TEXT_PX = 40000.0
    /**
     * Deux points d'un trait plus proches que ça à l'écran (pixels, sur les deux axes) ne se
     * distinguent pas : on saute le second. Un trait posé à un zoom n'est donc jamais plus fin à ce
     * zoom ou au-delà, et dézoomé il ne coûte plus que ce qu'on en voit.
     */
    const val MIN_STEP_PX = 0.5

    /** Ce que garde un tracé de couche : tout, tout sauf les coups de gomme, ou eux seuls (peints en couleur vive). */
    private const val ALL = 0
    private const val ITEMS_ONLY = 1
    private const val ERASERS_ONLY = 2

    /** Mode d'édition des gommes : tout est estompé à cette opacité, les coups de gomme ressortent. */
    const val GHOST_ALPHA = 0.3f
    /** La couleur à laquelle un coup de gomme se montre dans le mode d'édition des gommes. */
    const val ERASER_VIEW_COLOR = 0xD0FF1F8E.toInt()

    /**
     * Décide quelles couches se tracent depuis un cache raster : celles qui ont plus de [on] éléments
     * à l'écran, jusqu'à ce qu'elles retombent sous [off] (l'écart évite de basculer à chaque
     * image autour du seuil). Un trait coûte du temps de dessin, et dézoomé sur un grand tableau il
     * y en a des dizaines de milliers : on les cuit une fois dans une image qu'on déplace.
     */
    class CachePolicy(val on: Int = 600, val off: Int = 350) {
        private val active = java.util.WeakHashMap<Layer, Boolean>()

        fun useCache(layer: Layer, visibleCount: Int): Boolean {
            val now = if (visibleCount > on) true else if (visibleCount < off) false else active[layer] == true
            if (now) active[layer] = true else active.remove(layer)
            return now
        }

        fun clear() = active.clear()
    }

    private val shared = Builder()

    /**
     * Calcule l'image, sur le fil de l'écran. Avec [ghost] (mode d'édition des gommes), les couches
     * s'estompent à [GHOST_ALPHA], les coups de gomme de la couche de travail ne creusent plus rien :
     * ils se tracent par-dessus en [ERASER_VIEW_COLOR], pour qu'on les voie et les saisisse.
     */
    fun build(scene: ZoomScene, viewW: Double, viewH: Double, out: RenderList, ghost: Boolean = false, cache: CachePolicy? = null) =
        shared.build(scene, viewW, viewH, out, ghost, cache)

    /**
     * Le calculateur lui-même : il garde des tableaux de travail, donc un seul fil à la fois. Un fil
     * de fond qui trace pour son compte a le sien.
     */
    class Builder {
        private class Visible(val layer: Layer, val x: Double, val y: Double, val zoom: Double)

        private val cand = ArrayList<Entry>()
        private val src = DoubleArray(4)
        private val dst = DoubleArray(4)
        private var fillBuf = DoubleArray(256)
        private var fillTmp = DoubleArray(256)
        private var ringBuf = FloatArray(256)

        // La vue de la couche en cours de tracé : centre (dans son repère), zoom, taille, opacité.
        private var vx = 0.0
        private var vy = 0.0
        private var z = 1.0
        private var hw = 0.0
        private var hh = 0.0
        private var w = 0.0
        private var h = 0.0
        private var alpha = 1.0

        private var cachePolicy: CachePolicy? = null

        /**
         * Les passes d'un seul groupe : [entries] (déjà triées du fond vers l'avant) vues de la caméra
         * ([cx], [cy], [zoom]) dans une fenêtre de [w]×[h] pixels. Sert à cuire une couche, ou une partie, dans une image.
         */
        fun renderEntries(entries: List<Entry>, cx: Double, cy: Double, zoom: Double, w: Double, h: Double, out: RenderList) {
            out.clear()
            out.beginGroup(false, 1f)
            view(cx, cy, zoom, 1.0, w, h)
            emitEntries(entries, ALL, out)
            out.endGroup()
        }

        fun build(scene: ZoomScene, viewW: Double, viewH: Double, out: RenderList, ghost: Boolean = false, cache: CachePolicy? = null) {
            out.clear()
            // Pas de cache pour le mode d'édition des gommes : il redessine les gommes à part.
            cachePolicy = if (ghost) null else cache

            // Plus grande étendue de contenu à partir de chaque profondeur : on s'arrête dès que tout
            // ce qui reste en dessous ferait moins d'un pixel.
            val first = scene.depth
            val last = scene.lastDepth
            val count = (last - first + 1).toInt()
            val suffixExtent = DoubleArray(count + 1)
            for (i in count - 1 downTo 0) {
                suffixExtent[i] = max(suffixExtent[i + 1], scene.layer(first + i)?.extentApprox() ?: 0.0)
            }

            val visible = ArrayList<Visible>(4)
            var x = scene.cx
            var y = scene.cy
            var zz = scene.zoom
            for (i in 0 until count) {
                val l = scene.layer(first + i) ?: break
                if (i > 0) {
                    x = (x - l.ax) * scene.ratio
                    y = (y - l.ay) * scene.ratio
                    zz /= scene.ratio
                }
                if (!x.isFinite() || !y.isFinite() || zz <= 0.0) break
                if (suffixExtent[i] * zz < MIN_PX) break
                if (i >= MAX_LAYERS) break
                if (!l.isEmpty) visible.add(Visible(l, x, y, zz))
            }

            // Du fond vers l'avant : la couche d'en dessous…
            for (k in visible.indices.reversed()) {
                val v = visible[k]
                if (v.layer.depth == scene.depth) continue
                out.beginGroup(ghost || v.layer.hasEraser, if (ghost) GHOST_ALPHA else 1f)
                drawLayer(v, scene.layerAlpha(v.layer.depth), viewW, viewH, out, ALL)
                out.endGroup()
            }
            // … puis la couche de travail, avec le trait en cours (qui peut être un coup de gomme).
            val live = scene.live
            val drawing = live.count > 0
            val working = visible.firstOrNull { it.layer.depth == scene.depth }
            if (ghost) {
                // Les éléments d'un bloc, estompés (sans gomme : on voit tout ce qu'elle cache) ; les gommes au-dessus, en couleur.
                out.beginGroup(true, GHOST_ALPHA)
                if (working != null) drawLayer(working, 1.0, viewW, viewH, out, ITEMS_ONLY)
                out.endGroup()
                out.beginGroup(false, 1f)
                if (working != null) drawLayer(working, 1.0, viewW, viewH, out, ERASERS_ONLY)
                out.endGroup()
            } else {
                out.beginGroup(working?.layer?.hasEraser == true || (drawing && live.isEraser), 1f)
                if (working != null) drawLayer(working, 1.0, viewW, viewH, out, ALL)
                if (drawing) {
                    view(scene.cx, scene.cy, scene.zoom, 1.0, viewW, viewH)
                    emitPts(
                        live.originX, live.originY, live.pts, live.count, null, live.width, live.color, live.kind, live.isEraser,
                        if (live.kind == Stroke.BRUSH) pathLength(live.pts, live.count) else 0.0, out,
                    )
                }
                scene.liveShape()?.let {
                    view(scene.cx, scene.cy, scene.zoom, 1.0, viewW, viewH)
                    emitShape(it, out)
                }
                out.endGroup()
            }

            // Juste après le seuil, la couche du dessus reste par-dessus tout le reste et s'efface.
            out.fadeStart = out.runCount
            val upAlpha = scene.upperAlpha()
            val up = scene.layer(scene.depth - 1)
            if (up != null && !up.isEmpty && upAlpha > 0.0) {
                val v = scene.viewOf(up.depth)
                if (v.x.isFinite() && v.y.isFinite() && v.zoom > 0.0) {
                    val shown = if (ghost) upAlpha.toFloat() * GHOST_ALPHA else upAlpha.toFloat()
                    out.beginGroup(true, shown)
                    drawLayer(Visible(up, v.x, v.y, v.zoom), 1.0, viewW, viewH, out, ALL)
                    out.endGroup()
                    out.fadeAlpha = shown
                }
            }
        }

        private fun view(vx: Double, vy: Double, z: Double, alpha: Double, w: Double, h: Double) {
            this.vx = vx; this.vy = vy; this.z = z
            this.w = w; this.h = h; hw = w / 2; hh = h / 2
            this.alpha = alpha
        }

        private fun drawLayer(v: Visible, alpha: Double, w: Double, h: Double, out: RenderList, keep: Int) {
            if (alpha <= 0.0) return
            // L'encombrement d'une couche ignore les coups de gomme : on ne s'y fie pas pour les montrer.
            if (keep == ERASERS_ONLY) { if (!v.layer.hasEraser) return }
            else if (!layerOnScreen(v, w, h)) return
            out.layersDrawn++
            view(v.x, v.y, v.zoom, alpha, w, h)
            // Ce qui touche l'écran, plus de quoi ne pas couper l'épaisseur d'un trait fin ou le tour d'un pixel.
            val reach = (MIN_WIDTH_PX / 2 + 2.0) / v.zoom
            val x0 = v.x - hw / v.zoom - reach
            val y0 = v.y - hh / v.zoom - reach
            val x1 = v.x + hw / v.zoom + reach
            val y1 = v.y + hh / v.zoom + reach
            val policy = cachePolicy
            // Trop d'éléments pour les tracer un à un à chaque image : la vue posera le cache raster de la couche.
            if (policy != null && keep == ALL && policy.useCache(v.layer, v.layer.countIn(x0, y0, x1, y1))) {
                out.setGroupCache(v.layer, v.x, v.y, v.zoom)
                return
            }
            v.layer.query(x0, y0, x1, y1, cand)
            emitEntries(cand, keep, out)
            cand.clear()
        }

        private fun emitEntries(entries: List<Entry>, keep: Int, out: RenderList) {
            for (k in 0 until entries.size) {
                when (val item = entries[k].item) {
                    is ImageItem -> if (keep != ERASERS_ONLY) emitImage(item, out)
                    is Stroke -> when (keep) {
                        ALL -> emitStroke(item, item.color, out)
                        ITEMS_ONLY -> if (!item.isEraser) emitStroke(item, item.color, out)
                        // Un coup de gomme montré tel quel : un trait de couleur vive qui ne creuse rien.
                        else -> if (item.isEraser) emitStroke(item, ERASER_VIEW_COLOR, out)
                    }
                    is ShapeItem -> if (keep != ERASERS_ONLY) emitShape(item, out)
                    is TextItem -> if (keep != ERASERS_ONLY) emitText(item, out)
                    is StrokeBox -> Unit
                }
            }
        }

        private fun layerOnScreen(v: Visible, w: Double, h: Double): Boolean {
            val b = v.layer.boundsApprox() ?: return false
            val x0 = (b[0] - v.x) * v.zoom + w / 2
            val y0 = (b[1] - v.y) * v.zoom + h / 2
            val x1 = (b[2] - v.x) * v.zoom + w / 2
            val y1 = (b[3] - v.y) * v.zoom + h / 2
            if (x1 < 0 || y1 < 0 || x0 > w || y0 > h) return false
            return x1 - x0 >= MIN_PX || y1 - y0 >= MIN_PX
        }

        /**
         * Projette une image et ne garde que sa partie visible : le rectangle de destination est
         * découpé au bord de l'écran, et le rectangle source (dans les pixels de l'image) suit.
         */
        private fun emitImage(item: ImageItem, out: RenderList) {
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

        /**
         * Une forme : son remplissage (polygone découpé au bord de l'écran) puis son contour (une
         * polyligne, comme un trait de crayon, donc découpée de la même façon).
         */
        private fun emitShape(s: ShapeItem, out: RenderList) {
            val ox = (s.x - vx) * z + hw
            val oy = (s.y - vy) * z + hh
            val widthPx = if (s.hasStroke) s.width * z else 0.0
            val reach = max(widthPx, MIN_WIDTH_PX) / 2 + 2.0 + if (s.kind == ShapeKind.ARROW) hypot(s.w, s.h) * z * ShapeItem.ARROW_HEAD_HALF else 0.0
            if (ox + s.w * z / 2 + reach < 0 || ox - s.w * z / 2 - reach > w || oy + s.h * z / 2 + reach < 0 || oy - s.h * z / 2 - reach > h) return
            if (s.w * z < MIN_PX && s.h * z < MIN_PX && widthPx < MIN_PX) return
            val pts = ShapeGeometry.outline(s, z)
            if (s.hasFill) emitFill(pts, ox, oy, s.fillColor, out)
            if (s.hasStroke) {
                val closed = s.kind != ShapeKind.LINE
                val n = if (closed) pts.size + 2 else pts.size
                if (ringBuf.size < n) ringBuf = FloatArray(n)
                for (i in pts.indices) ringBuf[i] = pts[i].toFloat()
                if (closed) { ringBuf[pts.size] = pts[0].toFloat(); ringBuf[pts.size + 1] = pts[1].toFloat() }
                emitPts(s.x, s.y, ringBuf, n, null, s.width, s.strokeColor, Stroke.PEN, false, 0.0, out)
            }
        }

        /** Un texte : on ne garde que ce qui touche l'écran, et on dit à la vue où en poser le coin haut-gauche. */
        private fun emitText(t: TextItem, out: RenderList) {
            val sizePx = t.fontSize * z
            val x0 = (t.x - t.w / 2 - vx) * z + hw
            val y0 = (t.y - t.h / 2 - vy) * z + hh
            if (x0 + t.w * z < 0 || y0 + t.h * z < 0 || x0 > w || y0 > h) return
            if (t.w * z < MIN_PX && t.h * z < MIN_PX) return
            if (sizePx > MAX_TEXT_PX) return
            out.addText(t, x0, y0, sizePx, alpha.toFloat())
        }

        /**
         * Remplit le polygone [pts] (relatif à son centre, en unités de la couche) dont le centre est à
         * ([ox], [oy]) à l'écran. Découpé au bord de l'écran par Sutherland-Hodgman, en `Double` : ce qui
         * part vers le moteur graphique reste de la taille de l'écran, même pour une forme géante.
         */
        private fun emitFill(pts: DoubleArray, ox: Double, oy: Double, color: Int, out: RenderList) {
            val n = pts.size / 2
            if (n < 3) return
            var count = n
            if (fillBuf.size < 2 * n) fillBuf = DoubleArray(2 * n)
            for (i in 0 until n) { fillBuf[2 * i] = ox + pts[2 * i] * z; fillBuf[2 * i + 1] = oy + pts[2 * i + 1] * z }
            val m = 2.0
            // Quatre bords : gauche, droit, haut, bas.
            for (edge in 0 until 4) {
                if (count == 0) return
                if (fillTmp.size < 2 * (2 * count + 2)) fillTmp = DoubleArray(2 * (2 * count + 2))
                var k = 0
                var px = fillBuf[2 * (count - 1)]
                var py = fillBuf[2 * (count - 1) + 1]
                var pin = inside(edge, px, py, m)
                for (i in 0 until count) {
                    val cx = fillBuf[2 * i]
                    val cy = fillBuf[2 * i + 1]
                    val cin = inside(edge, cx, cy, m)
                    if (cin != pin) {
                        // Le segment coupe le bord : on garde le point d'intersection.
                        val t = when (edge) {
                            0 -> (-m - px) / (cx - px)
                            1 -> (w + m - px) / (cx - px)
                            2 -> (-m - py) / (cy - py)
                            else -> (h + m - py) / (cy - py)
                        }
                        var ix = px + t * (cx - px)
                        var iy = py + t * (cy - py)
                        when (edge) { 0 -> ix = -m; 1 -> ix = w + m; 2 -> iy = -m; else -> iy = h + m }
                        fillTmp[k++] = ix; fillTmp[k++] = iy
                    }
                    if (cin) { fillTmp[k++] = cx; fillTmp[k++] = cy }
                    px = cx; py = cy; pin = cin
                }
                count = k / 2
                val swap = fillBuf; fillBuf = fillTmp; fillTmp = swap
            }
            if (count < 3) return
            out.beginRun(color, 0f, alpha.toFloat(), false, RenderList.KIND_FILL)
            for (i in 0 until count) out.addPoint(fillBuf[2 * i], fillBuf[2 * i + 1])
        }

        private fun inside(edge: Int, x: Double, y: Double, m: Double): Boolean = when (edge) {
            0 -> x >= -m
            1 -> x <= w + m
            2 -> y >= -m
            else -> y <= h + m
        }

        private fun emitStroke(s: Stroke, color: Int, out: RenderList) {
            emitPts(
                s.x, s.y, s.pts, s.pts.size, s, s.width, color, s.kind, color ushr 24 == 0,
                if (s.kind == Stroke.BRUSH) s.length() else 0.0, out,
            )
        }

        private fun pathLength(p: FloatArray, n: Int): Double {
            var sum = 0.0
            var i = 0
            while (i + 3 < n) { sum += hypot((p[i + 2] - p[i]).toDouble(), (p[i + 3] - p[i + 1]).toDouble()); i += 2 }
            return sum
        }

        /**
         * Projette un trait (origine ([x], [y]) dans le repère de la couche, [n] nombres dans [p],
         * relatifs à l'origine) vu de la caméra courante et le découpe à l'écran. [brushLen] : sa
         * longueur, pour l'affinement du pinceau. [stroke] : le trait lui-même quand il est rangé
         * dans une couche (sa boîte est alors toute faite) ; null pour un tracé qu'il faut mesurer
         * (trait en cours, contour d'une forme).
         */
        private fun emitPts(
            x: Double, y: Double, p: FloatArray, n: Int, stroke: Stroke?,
            width: Double, color: Int, kind: Int, erase: Boolean, brushLen: Double, out: RenderList,
        ) {
            val widthPx = width * z
            val half = max(widthPx, MIN_WIDTH_PX) / 2 + 1.0
            val ox = (x - vx) * z + hw
            val oy = (y - vy) * z + hh
            var minX: Double; var minY: Double; var maxX: Double; var maxY: Double
            if (stroke != null) {
                minX = stroke.minX; minY = stroke.minY; maxX = stroke.maxX; maxY = stroke.maxY
            } else {
                minX = Double.POSITIVE_INFINITY; minY = Double.POSITIVE_INFINITY
                maxX = Double.NEGATIVE_INFINITY; maxY = Double.NEGATIVE_INFINITY
                var i = 0
                while (i < n) {
                    val px = p[i].toDouble(); val py = p[i + 1].toDouble()
                    if (px < minX) minX = px; if (px > maxX) maxX = px
                    if (py < minY) minY = py; if (py > maxY) maxY = py
                    i += 2
                }
                if (n == 0) { minX = 0.0; minY = 0.0; maxX = 0.0; maxY = 0.0 }
            }
            val bx0 = ox + minX * z - half
            val by0 = oy + minY * z - half
            val bx1 = ox + maxX * z + half
            val by1 = oy + maxY * z + half
            if (!(bx1 >= 0 && by1 >= 0 && bx0 <= w && by0 <= h)) return
            if ((maxX - minX) * z < MIN_PX && (maxY - minY) * z < MIN_PX && widthPx < MIN_PX) return

            val drawnWidth = min(max(widthPx, MIN_WIDTH_PX), 4 * max(w, h))
            val a = (alpha * if (widthPx < MIN_WIDTH_PX) max(widthPx / MIN_WIDTH_PX, 0.3) else 1.0).toFloat()
            // Zone de découpe : l'écran plus de quoi ne pas couper l'épaisseur du trait.
            val m = drawnWidth / 2 + 2.0
            val cx0 = -m
            val cy0 = -m
            val cx1 = w + m
            val cy1 = h + m
            val np = n / 2
            if (np == 0) return

            if (np == 1) {
                if (ox in cx0..cx1 && oy in cy0..cy1) {
                    out.beginRun(color, drawnWidth.toFloat(), a, erase, kind)
                    out.addPoint(ox, oy)
                }
                return
            }

            // Le pinceau : l'épaisseur de chaque point dépend de sa distance (le long du trait) au bout
            // le plus proche. [alongA] est la distance parcourue jusqu'au point A, en unités de la couche.
            val brush = kind == Stroke.BRUSH
            var alongA = 0.0
            var alongB = 0.0
            var open = false
            var ax = ox + p[0] * z
            var ay = oy + p[1] * z
            for (i in 1 until np) {
                val bx = ox + p[2 * i] * z
                val by = oy + p[2 * i + 1] * z
                if (brush) alongB += hypot((p[2 * i] - p[2 * i - 2]).toDouble(), (p[2 * i + 1] - p[2 * i - 1]).toDouble())
                // Un point qui ne se distingue pas du précédent n'ajoute rien : on le saute (sauf le dernier).
                if (i < np - 1 && abs(bx - ax) < MIN_STEP_PX && abs(by - ay) < MIN_STEP_PX) continue
                if (!clip(ax, ay, bx, by, cx0, cy0, cx1, cy1)) {
                    open = false
                } else {
                    val dx = bx - ax
                    val dy = by - ay
                    val seg = alongB - alongA
                    if (!open) {
                        out.beginRun(color, drawnWidth.toFloat(), a, erase, kind)
                        out.addPoint(ax + t0 * dx, ay + t0 * dy, if (brush) brushWidth(width, alongA + t0 * seg, brushLen, drawnWidth) else drawnWidth)
                    }
                    out.addPoint(ax + t1 * dx, ay + t1 * dy, if (brush) brushWidth(width, alongA + t1 * seg, brushLen, drawnWidth) else drawnWidth)
                    open = t1 >= 1.0
                }
                alongA = alongB
                ax = bx
                ay = by
            }
        }

        /** Épaisseur à l'écran du pinceau à [d] du début d'un trait long de [total]. */
        private fun brushWidth(width: Double, d: Double, total: Double, full: Double): Double =
            full * Stroke.brushFactor(min(d, total - d), width)

        // Résultat de [clip].
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
}
