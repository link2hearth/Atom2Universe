package com.Atom2Universe.app.zoomcanvas.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Le canvas infini à couches : la même toile répétée à l'infini, chaque couche [ratio] fois plus
 * petite que celle du dessus, et une seule caméra pour toutes.
 *
 * **Règle d'or : jamais de grandes coordonnées (« origine flottante »).** Rien n'est exprimé depuis
 * un point de départ commun. Chaque couche a son propre repère, et ne connaît que son point
 * d'accroche dans la couche du dessus :
 *
 *     point dans la couche d-1  =  ancre(d)  +  point dans la couche d / ratio
 *
 * La caméra vit toujours dans la couche de travail ([depth]) : son centre y reste de la taille d'un
 * écran. Quand on zoome assez pour changer de couche, on la convertit dans la nouvelle couche et les
 * nombres redeviennent petits. La profondeur n'est qu'un entier.
 *
 * Une couche est à sa taille normale au zoom 1 (un trait de 14 px fait 14 px à l'écran). Elle vit sur
 * ×100 de zoom : elle apparaît au loin à 1/ratio de sa taille normale (fond, derrière la couche de
 * devant), grandit de ×ratio jusqu'à sa taille normale, puis encore de ×ratio jusqu'à disparaître.
 * Le fondu de sortie n'occupe que la toute fin ([FADE_START], en échelle logarithmique) : de ×ratio^0,9
 * à ×ratio. Les couches existent dans les deux sens (au-dessus et en dessous), à l'infini.
 *
 * La couche de travail — la seule qu'on édite — est celle qu'on voit devant : on passe à la suivante
 * au milieu du fondu, quand la couche de devant est à moitié effacée. Le zoom de la caméra reste donc
 * dans [minZoom, maxZoom[ (un rapport d'échelle de large, décalé d'un demi-fondu autour de 1) ; la
 * couche du dessus, en fin de fondu, est dessinée par-dessus jusqu'à s'effacer complètement.
 *
 * Les couches forment une suite contiguë [firstDepth .. lastDepth] qui couvre les couches dessinées
 * et celle de la caméra. Une couche vide n'y est gardée que pour son ancre, quand elle est coincée
 * entre deux couches utiles : c'est cette chaîne d'ancres qui porte la précision (situer une scène
 * dix niveaux plus bas demande dix ancres, aucun `Double` seul n'y suffirait). Les couches vides
 * au-delà n'existent que virtuellement : on les crée quand la caméra y entre, on les oublie quand
 * elle en sort sans y avoir dessiné.
 */
class ZoomScene(val ratio: Double = DEFAULT_RATIO) {

    init {
        require(ratio > 1.0 && ratio.isFinite()) { "ratio must be > 1" }
    }

    private val logRatio = ln(ratio)

    /** Les couches, de la plus haute (la plus grande) à la plus profonde. Jamais vide. */
    private val layers = ArrayList<Layer>().apply { add(Layer(0L, 0.0, 0.0)) }
    var firstDepth = 0L
        private set
    val lastDepth: Long get() = firstDepth + layers.size - 1

    // ---- Caméra (toujours exprimée dans la couche de travail) -------------------------------

    /** Couche de travail : celle qu'on voit devant et où l'on dessine. */
    var depth = 0L
        private set
    var cx = 0.0
        private set
    var cy = 0.0
        private set
    /** Au-delà, la couche de dessous devient la couche de travail : milieu du fondu de sortie. */
    val maxZoom = Math.pow(ratio, (FADE_START + 1.0) / 2)
    /** En deçà, la couche du dessus redevient la couche de travail (exactement maxZoom / ratio). */
    val minZoom = maxZoom / ratio

    /** Pixels d'écran par unité de la couche de travail, dans [minZoom, maxZoom[ ; 1 = taille normale. */
    var zoom = START_ZOOM
        private set

    /** Prochain identifiant de trait (unique dans le projet). */
    var nextStrokeId = 1L
        private set

    /** Incrémenté à chaque changement du contenu (pas de la caméra) : l'enregistrement s'y fie. */
    var contentVersion = 0L
        private set
    /** Incrémenté à chaque mouvement de caméra. */
    var cameraVersion = 0L
        private set

    // ---- Couches ------------------------------------------------------------------------

    fun layer(d: Long): Layer? {
        val i = d - firstDepth
        return if (i < 0 || i >= layers.size) null else layers[i.toInt()]
    }

    /** Toutes les couches connues (y compris les vides gardées pour leur ancre), de haut en bas. */
    fun allLayers(): List<Layer> = layers

    /** Les profondeurs qui contiennent au moins un trait, de haut en bas. */
    fun nonEmptyDepths(): List<Long> = layers.filter { it.strokes.isNotEmpty() }.map { it.depth }

    /**
     * Position continue de la caméra dans la pile : la couche de travail est à sa taille normale
     * quand level() vaut sa profondeur, et s'efface complètement à profondeur + 1.
     */
    fun level(): Double = depth + ln(zoom) / logRatio

    /** Avancée du zoom dans la couche de travail : 0 juste après y être entré, 1 au moment d'en sortir. */
    fun progress(): Double = (ln(zoom / minZoom) / logRatio).coerceIn(0.0, 1.0)

    /**
     * Opacité de la couche [d] : pleine tant qu'elle n'a pas grossi de plus de ×ratio^FADE_START
     * depuis sa taille normale, puis fondu jusqu'à ×ratio où elle a disparu.
     */
    fun layerAlpha(d: Long): Double = alphaAt(level() - d)

    private fun alphaAt(r: Double): Double {
        if (r <= FADE_START) return 1.0
        if (r >= 1.0) return 0.0
        val t = (r - FADE_START) / (1.0 - FADE_START)
        return 1.0 - t * t * (3.0 - 2.0 * t)
    }

    // ---- Projection -----------------------------------------------------------------------

    /**
     * La caméra vue depuis la couche [d] : son centre dans le repère de [d] et son zoom (pixels
     * par unité de [d]). Les couches entre la caméra et [d] doivent exister. Calculé en `Double`,
     * en enchaînant les ancres depuis la couche de travail, jamais depuis une origine lointaine.
     */
    fun viewOf(d: Long, out: View = View()): View {
        var x = cx
        var y = cy
        var z = zoom
        if (d >= depth) {
            var k = depth
            while (k < d) {
                val child = layer(k + 1) ?: error("layer ${k + 1} missing")
                x = (x - child.ax) * ratio
                y = (y - child.ay) * ratio
                z /= ratio
                k++
            }
        } else {
            var k = depth
            while (k > d) {
                val cur = layer(k) ?: error("layer $k missing")
                x = cur.ax + x / ratio
                y = cur.ay + y / ratio
                z *= ratio
                k--
            }
        }
        out.x = x; out.y = y; out.zoom = z
        return out
    }

    /** Écart à l'écran (en pixels, depuis le centre de la vue) d'un point de la couche [d]. */
    fun toScreen(d: Long, x: Double, y: Double): DoubleArray {
        val v = viewOf(d)
        return doubleArrayOf((x - v.x) * v.zoom, (y - v.y) * v.zoom)
    }

    /**
     * Écart à l'écran du point [i] du trait [s] de la couche [d], calculé relativement à la caméra
     * (position du trait − caméra, puis forme locale) : exact même loin de l'origine de la couche.
     */
    fun toScreen(d: Long, s: Stroke, i: Int): DoubleArray {
        val v = viewOf(d)
        return doubleArrayOf(((s.x - v.x) + s.pts[2 * i]) * v.zoom, ((s.y - v.y) + s.pts[2 * i + 1]) * v.zoom)
    }

    /** Point de la couche de travail sous un écart d'écran [sx], [sy] (pixels depuis le centre). */
    fun screenToLocal(sx: Double, sy: Double): DoubleArray = doubleArrayOf(cx + sx / zoom, cy + sy / zoom)

    // ---- Caméra ---------------------------------------------------------------------------

    /** Fait glisser la toile de [dx], [dy] pixels (le contenu suit le doigt). */
    fun pan(dx: Double, dy: Double) {
        cx -= dx / zoom
        cy -= dy / zoom
        cameraVersion++
    }

    /**
     * Zoome d'un facteur [factor] autour d'un point de l'écran ([fx], [fy] : écart en pixels depuis
     * le centre de la vue), qui reste immobile. Change de couche autant de fois que nécessaire.
     */
    fun zoomAt(factor: Double, fx: Double, fy: Double) {
        if (!(factor > 0.0) || !factor.isFinite()) return
        // Le point sous le doigt, dans la couche de travail : il doit rester sous le doigt.
        val px = cx + fx / zoom
        val py = cy + fy / zoom
        zoom *= factor
        cx = px - fx / zoom
        cy = py - fy / zoom
        normalize()
        cameraVersion++
    }

    /** Place la caméra directement (reprise d'un projet, saut vers une couche). */
    fun setCamera(d: Long, x: Double, y: Double, z: Double) {
        ensureRangeIncludes(d)
        depth = d; cx = x; cy = y; zoom = z
        normalize()
        trim()
        cameraVersion++
    }

    /**
     * Saute vers la couche [d] et cadre tout son contenu dans une vue de [viewW]×[viewH] pixels,
     * sans entrer dans son fondu de sortie.
     */
    fun jumpTo(d: Long, viewW: Double, viewH: Double) {
        val l = layer(d) ?: return
        val b = l.bounds()
        if (b == null) {
            setCamera(d, 0.0, 0.0, START_ZOOM)
            return
        }
        val w = max(b[2] - b[0], 1e-9)
        val h = max(b[3] - b[1], 1e-9)
        val fit = min(viewW * 0.8 / w, viewH * 0.8 / h)
        val noFade = Math.pow(ratio, FADE_START) * 0.99
        setCamera(d, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, fit.coerceIn(minZoom, noFade))
    }

    private fun normalize() {
        while (zoom >= maxZoom) descend()
        while (zoom < minZoom) ascend()
    }

    /** La couche de dessous devient la couche de travail. */
    private fun descend() {
        val child = layer(depth + 1) ?: Layer(depth + 1, cx, cy).also { layers.add(it) }
        cx = (cx - child.ax) * ratio
        cy = (cy - child.ay) * ratio
        zoom /= ratio
        depth++
        trim()
    }

    /** La couche du dessus redevient la couche de travail. */
    private fun ascend() {
        val cur = layer(depth)!!
        if (layer(depth - 1) == null) {
            // Rien au-dessus : on crée la couche parente centrée sur la caméra. La couche courante
            // étant tout en haut, son ancre n'engageait rien, on peut la choisir.
            cur.ax = -cx / ratio
            cur.ay = -cy / ratio
            layers.add(0, Layer(depth - 1, 0.0, 0.0))
            firstDepth--
        }
        cx = cur.ax + cx / ratio
        cy = cur.ay + cy / ratio
        zoom *= ratio
        depth--
        trim()
    }

    /** Crée les couches (vides) qui manquent pour que [d] soit dans la suite. */
    private fun ensureRangeIncludes(d: Long) {
        while (d < firstDepth) { layers.add(0, Layer(firstDepth - 1, 0.0, 0.0)); firstDepth-- }
        while (d > lastDepth) layers.add(Layer(lastDepth + 1, 0.0, 0.0))
    }

    /** Oublie les couches vides des extrémités qui ne servent ni à la caméra ni à une couche dessinée. */
    private fun trim() {
        while (layers.size > 1) {
            val top = layers[0]
            if (top.depth == depth || top.used) break
            layers.removeAt(0)
            firstDepth++
        }
        while (layers.size > 1) {
            val bottom = layers[layers.size - 1]
            if (bottom.depth == depth || bottom.used) break
            layers.removeAt(layers.size - 1)
        }
    }

    // ---- Dessin ---------------------------------------------------------------------------

    private var liveOriginX = 0.0
    private var liveOriginY = 0.0
    private var livePts = DoubleArray(64)
    private var liveCount = 0
    private var liveColor = 0
    private var liveWidth = 1.0

    /** Le trait en cours (ou null) : dessiné par-dessus, pleinement opaque. */
    fun liveStroke(): Stroke? =
        if (liveCount == 0) null else Stroke(0L, liveOriginX, liveOriginY, livePts.copyOf(liveCount), liveColor, liveWidth)

    val isDrawing: Boolean get() = liveCount > 0

    /** Commence un trait à l'écart d'écran [sx], [sy], d'épaisseur [widthPx] pixels à l'écran. */
    fun beginStroke(sx: Double, sy: Double, color: Int, widthPx: Double) {
        anchorIfFresh(layer(depth)!!)
        val p = screenToLocal(sx, sy)
        liveOriginX = p[0]
        liveOriginY = p[1]
        liveCount = 0
        liveColor = color
        liveWidth = widthPx / zoom
        appendLive(0.0, 0.0)
    }

    fun extendStroke(sx: Double, sy: Double) {
        if (liveCount == 0) return
        // Forme en coordonnées locales autour du premier point : petite, quelle que soit la position.
        val rx = (cx - liveOriginX) + sx / zoom
        val ry = (cy - liveOriginY) + sy / zoom
        val lx = livePts[liveCount - 2]
        val ly = livePts[liveCount - 1]
        // Pas de points plus serrés qu'un demi-pixel : inutile et coûteux.
        val minStep = 0.5 / zoom
        if (abs(rx - lx) < minStep && abs(ry - ly) < minStep) return
        appendLive(rx, ry)
    }

    private fun appendLive(x: Double, y: Double) {
        if (liveCount + 2 > livePts.size) livePts = livePts.copyOf(livePts.size * 2)
        livePts[liveCount++] = x
        livePts[liveCount++] = y
    }

    fun cancelStroke() {
        liveCount = 0
    }

    /** Termine le trait et l'ajoute à la couche de travail. */
    fun endStroke(): Stroke? {
        if (liveCount == 0) return null
        val l = layer(depth)!!
        val s = Stroke(nextStrokeId++, liveOriginX, liveOriginY, livePts.copyOf(liveCount), liveColor, liveWidth)
        liveCount = 0
        l.add(s)
        record(Edit.Add(depth, s))
        return s
    }

    /**
     * Première fois qu'on dessine dans une couche : on l'accroche là où est la caméra (son
     * origine passe sous le centre de l'écran). Invisible à l'écran : seul le repère change.
     */
    private fun anchorIfFresh(l: Layer) {
        if (l.used || l.strokes.isNotEmpty()) return
        rebase(l, cx, cy)
    }

    /** Décale le repère de [l] de ([dx], [dy]) de ses propres unités, sans rien bouger à l'écran. */
    private fun rebase(l: Layer, dx: Double, dy: Double) {
        l.ax += dx / ratio
        l.ay += dy / ratio
        for (i in l.strokes.indices) {
            val s = l.strokes[i]
            l.strokes[i] = Stroke(s.id, s.x - dx, s.y - dy, s.pts, s.color, s.width)
        }
        l.invalidate()
        layer(l.depth + 1)?.let { it.ax -= dx; it.ay -= dy }
        if (l.depth == depth) { cx -= dx; cy -= dy }
    }

    // ---- Gomme ----------------------------------------------------------------------------

    private var eraseBatch: ArrayList<Stroke>? = null

    fun beginErase() {
        eraseBatch = ArrayList()
    }

    /** Efface les traits de la couche de travail touchés par un disque de [radiusPx] pixels. */
    fun eraseAt(sx: Double, sy: Double, radiusPx: Double): Boolean {
        val l = layer(depth) ?: return false
        val p = screenToLocal(sx, sy)
        val r = radiusPx / zoom
        val hit = l.strokes.filter { it.hits(p[0], p[1], r) }
        if (hit.isEmpty()) return false
        for (s in hit) l.remove(s.id)
        val batch = eraseBatch
        if (batch != null) batch.addAll(hit) else record(Edit.Remove(depth, hit))
        return true
    }

    fun endErase() {
        val batch = eraseBatch ?: return
        eraseBatch = null
        if (batch.isNotEmpty()) record(Edit.Remove(depth, batch))
    }

    // ---- Réalignement ---------------------------------------------------------------------

    private var moveDepth = Long.MIN_VALUE
    private var moveDx = 0.0
    private var moveDy = 0.0

    /** La couche qu'un glissé de réalignement déplacerait : celle du fond, juste sous la couche de travail. */
    fun movableLayer(): Layer? = layer(depth + 1)

    fun beginMoveLayer(): Boolean {
        val l = movableLayer() ?: return false
        moveDepth = l.depth
        moveDx = 0.0; moveDy = 0.0
        return true
    }

    /**
     * Fait glisser la couche du fond (et tout ce qui est accroché dessous) de [dx], [dy] pixels par
     * rapport à la couche de travail : seule son ancre change.
     */
    fun moveLayerBy(dx: Double, dy: Double) {
        if (moveDepth == Long.MIN_VALUE) return
        val l = layer(moveDepth) ?: return
        val ux = dx / zoom
        val uy = dy / zoom
        l.ax += ux; l.ay += uy
        moveDx += ux; moveDy += uy
        l.used = true
        contentVersion++
    }

    fun endMoveLayer() {
        if (moveDepth == Long.MIN_VALUE) return
        if (moveDx != 0.0 || moveDy != 0.0) record(Edit.Move(moveDepth, moveDx, moveDy))
        moveDepth = Long.MIN_VALUE
    }

    // ---- Historique -----------------------------------------------------------------------

    sealed class Edit {
        class Add(val depth: Long, val stroke: Stroke) : Edit()
        class Remove(val depth: Long, val strokes: List<Stroke>) : Edit()
        class Move(val depth: Long, val dx: Double, val dy: Double) : Edit()
    }

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun record(e: Edit) {
        undoStack.addLast(e)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        contentVersion++
    }

    fun undo(): Boolean {
        val e = undoStack.removeLastOrNull() ?: return false
        apply(e, reverse = true)
        redoStack.addLast(e)
        contentVersion++
        return true
    }

    fun redo(): Boolean {
        val e = redoStack.removeLastOrNull() ?: return false
        apply(e, reverse = false)
        undoStack.addLast(e)
        contentVersion++
        return true
    }

    /**
     * Rejoue une modification. Les couches qui ont porté un trait pendant la session ne sont
     * jamais oubliées ([Layer.used]) : leur repère est donc toujours celui de l'historique.
     */
    private fun apply(e: Edit, reverse: Boolean) {
        when (e) {
            is Edit.Add -> {
                val l = layer(e.depth) ?: return
                if (reverse) l.remove(e.stroke.id) else l.add(e.stroke)
            }
            is Edit.Remove -> {
                val l = layer(e.depth) ?: return
                if (reverse) e.strokes.forEach { l.add(it) } else e.strokes.forEach { l.remove(it.id) }
            }
            is Edit.Move -> {
                val l = layer(e.depth) ?: return
                val sign = if (reverse) -1.0 else 1.0
                l.ax += sign * e.dx
                l.ay += sign * e.dy
            }
        }
    }

    // ---- Chargement -----------------------------------------------------------------------

    /**
     * Remplace tout le contenu (lecture d'un projet). [loaded] est une suite contiguë de couches
     * commençant à [first].
     */
    fun restore(first: Long, loaded: List<Layer>, camDepth: Long, camX: Double, camY: Double, camZoom: Double, nextId: Long) {
        layers.clear()
        if (loaded.isEmpty()) {
            layers.add(Layer(camDepth, 0.0, 0.0))
            firstDepth = camDepth
        } else {
            layers.addAll(loaded)
            firstDepth = first
            for (l in layers) l.used = l.used || l.strokes.isNotEmpty()
        }
        var maxId = 0L
        for (l in layers) for (s in l.strokes) maxId = max(maxId, s.id)
        nextStrokeId = max(nextId, maxId + 1)
        undoStack.clear(); redoStack.clear()
        val z = if (camZoom.isFinite() && camZoom > 0) camZoom else START_ZOOM
        val x = if (camX.isFinite()) camX else 0.0
        val y = if (camY.isFinite()) camY else 0.0
        setCamera(camDepth, x, y, z)
    }

    class View(var x: Double = 0.0, var y: Double = 0.0, var zoom: Double = 1.0)

    companion object {
        const val DEFAULT_RATIO = 10.0
        /** Zoom d'ouverture : la couche 0 à sa taille normale. */
        const val START_ZOOM = 1.0
        /**
         * Début du fondu de sortie, en part du rapport d'échelle (échelle logarithmique) : avec ×10,
         * la couche commence à s'effacer à ×10^0,9 ≈ ×7,9 de sa taille normale et a disparu à ×10.
         */
        const val FADE_START = 0.9
        const val MAX_HISTORY = 300
        val RATIOS = doubleArrayOf(5.0, 10.0, 20.0)
    }
}

/**
 * Une couche : son ancre dans la couche du dessus (en unités de celle-ci) et ses traits, en
 * coordonnées locales.
 */
class Layer(val depth: Long, var ax: Double, var ay: Double) {
    val strokes = ArrayList<Stroke>()
    /** A déjà porté quelque chose : on ne l'oublie plus (l'historique compte sur son repère). */
    var used = false

    private var cached: DoubleArray? = null
    private var boundsValid = false

    fun add(s: Stroke) {
        strokes.add(s)
        used = true
        invalidate()
    }

    fun remove(id: Long) {
        strokes.removeAll { it.id == id }
        invalidate()
    }

    fun invalidate() {
        boundsValid = false
    }

    /** Rectangle englobant (minX, minY, maxX, maxY) du contenu, épaisseur comprise, ou null si vide. */
    fun bounds(): DoubleArray? {
        if (!boundsValid) {
            cached = if (strokes.isEmpty()) null else {
                var x0 = Double.POSITIVE_INFINITY; var y0 = Double.POSITIVE_INFINITY
                var x1 = Double.NEGATIVE_INFINITY; var y1 = Double.NEGATIVE_INFINITY
                for (s in strokes) {
                    val h = s.width / 2
                    x0 = min(x0, s.x + s.minX - h); y0 = min(y0, s.y + s.minY - h)
                    x1 = max(x1, s.x + s.maxX + h); y1 = max(y1, s.y + s.maxY + h)
                }
                doubleArrayOf(x0, y0, x1, y1)
            }
            boundsValid = true
        }
        return cached
    }

    /** Plus grande dimension du contenu, en unités locales (0 si vide). */
    fun extent(): Double {
        val b = bounds() ?: return 0.0
        return max(b[2] - b[0], b[3] - b[1])
    }
}

/**
 * Un trait de dessin libre : sa position ([x], [y], en `Double`) et sa forme [pts] (paires x, y)
 * relative à cette position — le premier point vaut (0, 0). [width] est en unités de la couche.
 */
class Stroke(val id: Long, val x: Double, val y: Double, val pts: DoubleArray, val color: Int, val width: Double) {
    val pointCount: Int get() = pts.size / 2

    val minX: Double
    val minY: Double
    val maxX: Double
    val maxY: Double

    init {
        var a = Double.POSITIVE_INFINITY; var b = Double.POSITIVE_INFINITY
        var c = Double.NEGATIVE_INFINITY; var d = Double.NEGATIVE_INFINITY
        var i = 0
        while (i < pts.size) {
            a = min(a, pts[i]); c = max(c, pts[i])
            b = min(b, pts[i + 1]); d = max(d, pts[i + 1])
            i += 2
        }
        if (pts.isEmpty()) { a = 0.0; b = 0.0; c = 0.0; d = 0.0 }
        minX = a; minY = b; maxX = c; maxY = d
    }

    /** Le disque ([px], [py], [r]) touche-t-il le trait (épaisseur comprise) ? */
    fun hits(px: Double, py: Double, r: Double): Boolean {
        val lx = px - x
        val ly = py - y
        val reach = r + width / 2
        if (lx < minX - reach || lx > maxX + reach || ly < minY - reach || ly > maxY + reach) return false
        if (pts.size == 2) return hypot2(lx - pts[0], ly - pts[1]) <= reach * reach
        var i = 0
        while (i + 3 < pts.size) {
            if (segDist2(lx, ly, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]) <= reach * reach) return true
            i += 2
        }
        return false
    }

    private fun hypot2(dx: Double, dy: Double) = dx * dx + dy * dy

    private fun segDist2(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0.0, 1.0)
        return hypot2(px - ax - t * vx, py - ay - t * vy)
    }

    /** Longueur du trait (unités locales), pour les tests et les statistiques. */
    fun length(): Double {
        var sum = 0.0
        var i = 0
        while (i + 3 < pts.size) {
            sum += sqrt(hypot2(pts[i + 2] - pts[i], pts[i + 3] - pts[i + 1]))
            i += 2
        }
        return sum
    }
}
