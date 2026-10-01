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
 * Le zoom dit combien de pixels d'écran fait une unité de la couche de travail : à 1, la couche est à
 * sa taille normale. **L'épaisseur d'un trait se choisit à l'écran** : un crayon de 4 trace un trait
 * de 4 px tel qu'on le voit au moment où on dessine, quel que soit le zoom, et le trait est rangé en
 * unités de la couche (4 ÷ zoom). Il grossit ensuite avec le zoom, comme tout le reste du dessin :
 * dessiner zoomé, c'est dessiner plus fin. Les ancres sont des nombres entiers d'unités de la couche
 * du dessus.
 *
 * **Un seul seuil, suivi d'un fondu léger.** On voit la couche de travail (la seule qu'on édite) et
 * celle d'en dessous (derrière, [ratio] fois plus petite). Quand le zoom atteint [maxZoom], la
 * couche du dessous devient aussitôt la couche de travail (à [minZoom]) et celle d'encore en
 * dessous apparaît ; l'ancienne couche de travail reste tracée par-dessus et s'efface pendant un
 * zoom ×[FADE_ZOOM]. Dès qu'elle devient transparente, c'est donc en dessous qu'on dessine. En
 * dézoomant, c'est l'inverse exact : la couche du dessus réapparaît, et redevient la couche de
 * travail au seuil, entièrement opaque. L'opacité ne dépend que du zoom, jamais du chemin suivi :
 * aucune zone morte, zoomer et dézoomer autour du seuil fait basculer la couche de travail à chaque
 * passage, sans rien faire sauter à l'écran.
 *
 * **La distance entre deux couches est le rapport.** Toutes les couches zoomant ensemble, passer
 * d'une couche à la suivante demande exactement un zoom ×[ratio] : ×625, soit environ quatre
 * pincements. La plage d'une couche est centrée sur sa taille normale, de 1/√ratio à √ratio (de
 * ×1/25 à ×25), et un canvas s'ouvre au milieu : on peut dézoomer ×25 et zoomer ×25 avant de
 * changer de couche. Plus de pincements par couche, c'est un rapport plus grand, donc une couche
 * du dessous plus petite : il n'y a pas d'autre réglage.
 *
 * Les couches forment une suite contiguë [firstDepth .. lastDepth] qui couvre les couches dessinées
 * et celle de la caméra. Une couche vide n'y est gardée que pour son ancre, quand elle est coincée
 * entre deux couches utiles : c'est cette chaîne d'ancres qui porte la précision (situer une scène
 * dix niveaux plus bas demande dix ancres, aucun `Double` seul n'y suffirait). Les couches vides
 * au-delà n'existent que virtuellement : on les crée quand la caméra y entre, on les oublie quand
 * elle en sort sans y avoir dessiné.
 */
class ZoomScene(val ratio: Double = DEFAULT_RATIO, val maxZoom: Double = Math.sqrt(ratio), startZoom: Double = Double.NaN) {

    init {
        require(ratio > 1.0 && ratio.isFinite()) { "ratio must be > 1" }
        require(maxZoom > 0.0 && maxZoom.isFinite()) { "maxZoom must be > 0" }
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
    // Le seuil [maxZoom] (constructeur) : au-delà, la couche de dessous devient la couche de travail.
    /** En deçà, la couche du dessus redevient la couche de travail (exactement maxZoom / ratio). */
    val minZoom = maxZoom / ratio

    /**
     * Le milieu de la plage d'une couche (1, sa taille normale) : autant de marge pour dézoomer que
     * pour zoomer. C'est là qu'on ouvre un canvas ; tout en bas de la plage, le moindre dézoom
     * ferait passer à la couche du dessus.
     */
    val homeZoom = Math.sqrt(minZoom * maxZoom)

    /** Pixels d'écran par unité de la couche de travail, dans [minZoom, maxZoom[. On ouvre au milieu. */
    var zoom = if (startZoom.isNaN()) homeZoom else startZoom
        private set

    /** Prochain identifiant d'objet, trait ou image (unique dans le projet). */
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

    /** Les profondeurs qui contiennent quelque chose, de haut en bas. */
    fun nonEmptyDepths(): List<Long> = layers.filter { !it.isEmpty }.map { it.depth }

    /**
     * Position continue de la caméra dans la pile : une couche est à sa taille normale quand level()
     * vaut sa profondeur, et à ×ratio quand il vaut sa profondeur + 1.
     */
    fun level(): Double = depth + ln(zoom) / logRatio

    /** Avancée du zoom dans la plage de la couche de travail : 0 à son bord bas, 1 à son bord haut. */
    fun progress(): Double = (ln(zoom / minZoom) / ln(maxZoom / minZoom)).coerceIn(0.0, 1.0)

    /**
     * Opacité de la couche [d] : 1 pour la couche de travail et celle d'en dessous ; pour la couche
     * du dessus, le fondu de sortie ([upperAlpha]) ; 0 pour toutes les autres.
     */
    fun layerAlpha(d: Long): Double = when (d) {
        depth, depth + 1 -> 1.0
        depth - 1 -> upperAlpha()
        else -> 0.0
    }

    /**
     * Opacité de la couche du dessus, tracée par-dessus la couche de travail juste après le seuil :
     * 1 au seuil, 0 une fois le zoom multiplié par [FADE_ZOOM], en courbe douce entre les deux.
     */
    fun upperAlpha(): Double {
        val t = ln(zoom / minZoom) / ln(FADE_ZOOM)
        if (t >= 1.0) return 0.0
        val u = 1.0 - t.coerceIn(0.0, 1.0)
        return u * u * (3 - 2 * u)
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
     * en restant dans cette couche.
     */
    fun jumpTo(d: Long, viewW: Double, viewH: Double) {
        val l = layer(d) ?: return
        val b = l.bounds()
        if (b == null) {
            setCamera(d, 0.0, 0.0, homeZoom)
            return
        }
        val w = max(b[2] - b[0], 1e-9)
        val h = max(b[3] - b[1], 1e-9)
        val fit = min(viewW * 0.8 / w, viewH * 0.8 / h)
        setCamera(d, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, fit.coerceIn(minZoom, maxZoom * 0.99))
    }

    private fun normalize() {
        while (zoom >= maxZoom) descend()
        while (zoom < minZoom) ascend()
    }

    /** La couche de dessous devient la couche de travail. */
    private fun descend() {
        val child = layer(depth + 1) ?: Layer(depth + 1, Math.rint(cx), Math.rint(cy)).also { layers.add(it) }
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
            cur.ax = -Math.rint(cx / ratio)
            cur.ay = -Math.rint(cy / ratio)
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
    private var liveKind = Stroke.PEN

    /** Le trait en cours (ou null) : dessiné par-dessus tout le reste. */
    fun liveStroke(): Stroke? =
        if (liveCount == 0) null else Stroke(0L, liveOriginX, liveOriginY, livePts.copyOf(liveCount), liveColor, liveWidth, liveKind)

    val isDrawing: Boolean get() = liveCount > 0

    /**
     * Commence un trait à l'écart d'écran [sx], [sy]. [widthPx] est l'épaisseur à l'écran au moment
     * du trait ; elle est rangée en unités de la couche (÷ zoom), donc le trait grossit ensuite avec
     * le zoom comme le reste du dessin. Un trait de couleur transparente ([ERASER]) est un coup de
     * gomme : il dessine de la transparence, qui creuse ce que sa couche a reçu avant lui.
     * [kind] : crayon, pinceau ou feutre ([Stroke.PEN], [Stroke.BRUSH], [Stroke.MARKER]).
     */
    fun beginStroke(sx: Double, sy: Double, color: Int, widthPx: Double, kind: Int = Stroke.PEN) {
        anchorIfFresh(layer(depth)!!)
        val p = screenToLocal(sx, sy)
        liveOriginX = p[0]
        liveOriginY = p[1]
        liveCount = 0
        liveColor = color
        liveWidth = widthPx / zoom
        liveKind = if (color ushr 24 == 0) Stroke.PEN else kind
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

    /** Termine le trait et l'ajoute à la couche de travail. Un coup de gomme dans le vide ne laisse rien. */
    fun endStroke(): Stroke? {
        if (liveCount == 0) return null
        val l = layer(depth)!!
        val s = Stroke(nextStrokeId, liveOriginX, liveOriginY, livePts.copyOf(liveCount), liveColor, liveWidth, liveKind)
        liveCount = 0
        if (s.isEraser && !l.touches(s)) return null
        nextStrokeId++
        l.add(s)
        record(Edit.Add(depth, s))
        return s
    }

    /**
     * Première fois qu'on dessine dans une couche : on l'accroche là où est la caméra (son
     * origine passe sous le centre de l'écran). Invisible à l'écran : seul le repère change.
     */
    private fun anchorIfFresh(l: Layer) {
        if (l.used || !l.isEmpty) return
        // Un multiple du rapport : l'ancre de la couche reste un nombre entier de pixels du dessus.
        rebase(l, Math.rint(cx / ratio) * ratio, Math.rint(cy / ratio) * ratio)
    }

    /** Décale le repère de [l] de ([dx], [dy]) de ses propres unités, sans rien bouger à l'écran. */
    private fun rebase(l: Layer, dx: Double, dy: Double) {
        l.ax += dx / ratio
        l.ay += dy / ratio
        for (i in l.strokes.indices) {
            val s = l.strokes[i]
            l.strokes[i] = Stroke(s.id, s.x - dx, s.y - dy, s.pts, s.color, s.width, s.kind)
        }
        for (i in l.images.indices) l.images[i] = l.images[i].moved(-dx, -dy)
        l.invalidate()
        layer(l.depth + 1)?.let { it.ax -= dx; it.ay -= dy }
        if (l.depth == depth) { cx -= dx; cy -= dy }
    }

    // ---- Images ---------------------------------------------------------------------------

    /**
     * Pose une image de [pxW]×[pxH] pixels au centre de l'écran, dans la couche de travail, à une
     * taille qui tient dans [fitW]×[fitH] pixels d'écran.
     */
    fun addImage(key: String, pxW: Int, pxH: Int, fitW: Double, fitH: Double): ImageItem {
        val l = layer(depth)!!
        anchorIfFresh(l)
        val k = min(fitW / pxW, fitH / pxH) / zoom
        val item = ImageItem(nextStrokeId++, key, pxW, pxH, cx, cy, pxW * k, pxH * k)
        l.addImage(item)
        record(Edit.AddImage(depth, item))
        return item
    }

    /** L'image de la couche de travail sous l'écart d'écran [sx], [sy] (la dernière posée), ou null. */
    fun imageAt(sx: Double, sy: Double): ImageItem? {
        val l = layer(depth) ?: return null
        val p = screenToLocal(sx, sy)
        return l.images.filter { it.contains(p[0], p[1]) }.maxByOrNull { it.id }
    }

    fun image(id: Long): ImageItem? = layer(depth)?.images?.firstOrNull { it.id == id }

    /** Coins d'une image à l'écran (écarts depuis le centre) : x0, y0, x1, y1. */
    fun imageScreenRect(item: ImageItem): DoubleArray = doubleArrayOf(
        (item.x - item.w / 2 - cx) * zoom, (item.y - item.h / 2 - cy) * zoom,
        (item.x + item.w / 2 - cx) * zoom, (item.y + item.h / 2 - cy) * zoom,
    )

    private var imageBefore: ImageItem? = null

    /** Début d'un déplacement ou d'un redimensionnement de l'image [id] (un seul pas d'historique). */
    fun beginImageEdit(id: Long): Boolean {
        imageBefore = image(id)
        return imageBefore != null
    }

    /** Déplace l'image en cours d'édition de [dx], [dy] pixels d'écran. */
    fun moveImageBy(dx: Double, dy: Double) {
        val cur = imageBefore?.let { image(it.id) } ?: return
        replaceImage(cur.moved(dx / zoom, dy / zoom))
    }

    /**
     * Redimensionne l'image en cours d'édition par son coin [corner] (0 haut-gauche, 1 haut-droit,
     * 2 bas-droit, 3 bas-gauche), tiré jusqu'à l'écart d'écran [sx], [sy]. Le coin opposé ne bouge
     * pas et les proportions sont gardées.
     */
    fun resizeImageTo(corner: Int, sx: Double, sy: Double) {
        val start = imageBefore ?: return
        val signX = if (corner == 1 || corner == 2) 1.0 else -1.0
        val signY = if (corner >= 2) 1.0 else -1.0
        val ox = start.x - signX * start.w / 2
        val oy = start.y - signY * start.h / 2
        val p = screenToLocal(sx, sy)
        // On suit le doigt sur la diagonale : l'échelle est la projection du doigt sur celle-ci.
        val dx = signX * start.w
        val dy = signY * start.h
        val k = (((p[0] - ox) * dx + (p[1] - oy) * dy) / (dx * dx + dy * dy)).coerceAtLeast(MIN_IMAGE_PX / (zoom * max(start.w, start.h)))
        val w = start.w * k
        val h = start.h * k
        val cur = image(start.id) ?: return
        replaceImage(ImageItem(cur.id, cur.key, cur.pxW, cur.pxH, ox + signX * w / 2, oy + signY * h / 2, w, h))
    }

    fun endImageEdit() {
        val before = imageBefore ?: return
        imageBefore = null
        val after = image(before.id) ?: return
        if (after.x != before.x || after.y != before.y || after.w != before.w || after.h != before.h) {
            record(Edit.ChangeImage(depth, before, after))
        }
    }

    fun deleteImage(id: Long) {
        val l = layer(depth) ?: return
        val item = l.images.firstOrNull { it.id == id } ?: return
        l.removeImage(id)
        record(Edit.RemoveImage(depth, item))
    }

    /** Les images utilisées par le projet (clés de fichier), pour ranger les fichiers orphelins. */
    fun imageKeys(): Set<String> = layers.flatMap { l -> l.images.map { it.key } }.toSet()

    private fun replaceImage(item: ImageItem) {
        layer(depth)?.replaceImage(item)
        contentVersion++
    }

    // ---- Réalignement ---------------------------------------------------------------------

    private var moveDepth = Long.MIN_VALUE
    private var moveDx = 0.0
    private var moveDy = 0.0
    private var moveRawX = 0.0
    private var moveRawY = 0.0

    /** La couche qu'un glissé de réalignement déplacerait : celle du fond, juste sous la couche de travail. */
    fun movableLayer(): Layer? = layer(depth + 1)

    fun beginMoveLayer(): Boolean {
        val l = movableLayer() ?: return false
        moveDepth = l.depth
        moveDx = 0.0; moveDy = 0.0
        moveRawX = 0.0; moveRawY = 0.0
        return true
    }

    /**
     * Fait glisser la couche du fond (et tout ce qui est accroché dessous) de [dx], [dy] pixels par
     * rapport à la couche de travail : seule son ancre change.
     */
    fun moveLayerBy(dx: Double, dy: Double) {
        if (moveDepth == Long.MIN_VALUE) return
        val l = layer(moveDepth) ?: return
        moveRawX += dx / zoom
        moveRawY += dy / zoom
        // Par pixels entiers de la couche de travail : les grilles restent alignées.
        val tx = Math.rint(moveRawX)
        val ty = Math.rint(moveRawY)
        l.ax += tx - moveDx; l.ay += ty - moveDy
        moveDx = tx; moveDy = ty
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
        class Move(val depth: Long, val dx: Double, val dy: Double) : Edit()
        class AddImage(val depth: Long, val item: ImageItem) : Edit()
        class RemoveImage(val depth: Long, val item: ImageItem) : Edit()
        class ChangeImage(val depth: Long, val before: ImageItem, val after: ImageItem) : Edit()
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
            is Edit.Move -> {
                val l = layer(e.depth) ?: return
                val sign = if (reverse) -1.0 else 1.0
                l.ax += sign * e.dx
                l.ay += sign * e.dy
            }
            is Edit.AddImage -> {
                val l = layer(e.depth) ?: return
                if (reverse) l.removeImage(e.item.id) else l.addImage(e.item)
            }
            is Edit.RemoveImage -> {
                val l = layer(e.depth) ?: return
                if (reverse) l.addImage(e.item) else l.removeImage(e.item.id)
            }
            is Edit.ChangeImage -> layer(e.depth)?.replaceImage(if (reverse) e.before else e.after)
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
            for (l in layers) l.used = l.used || !l.isEmpty
        }
        var maxId = 0L
        for (l in layers) {
            for (s in l.strokes) maxId = max(maxId, s.id)
            for (i in l.images) maxId = max(maxId, i.id)
        }
        nextStrokeId = max(nextId, maxId + 1)
        undoStack.clear(); redoStack.clear()
        val z = if (camZoom.isFinite() && camZoom > 0) camZoom else homeZoom
        val x = if (camX.isFinite()) camX else 0.0
        val y = if (camY.isFinite()) camY else 0.0
        setCamera(camDepth, x, y, z)
    }

    class View(var x: Double = 0.0, var y: Double = 0.0, var zoom: Double = 1.0)

    companion object {
        /**
         * Chaque couche est 625 fois plus petite que celle du dessus (25 × 25) : environ quatre
         * pincements pour passer de l'une à l'autre, deux pour dézoomer et deux pour zoomer depuis
         * l'ouverture. Le même pour tous les nouveaux projets.
         */
        const val DEFAULT_RATIO = 625.0
        /** La couleur de la gomme : transparente. Un trait de cette couleur creuse sa couche. */
        const val ERASER = 0
        /**
         * Durée du fondu léger de la couche du dessus après le seuil : elle a disparu quand le zoom a
         * doublé (un petit bout de pincement).
         */
        const val FADE_ZOOM = 2.0
        /** Une image ne se réduit pas en dessous de cette taille à l'écran (pixels). */
        const val MIN_IMAGE_PX = 12.0
        const val MAX_HISTORY = 300
    }
}

/** Ce qu'une couche contient : un trait (gomme comprise) ou une image, chacun avec son identifiant. */
sealed interface LayerItem {
    val id: Long
}

/**
 * Une couche : son ancre dans la couche du dessus (en unités de celle-ci), ses traits et ses images,
 * en coordonnées locales. Tout se dessine dans l'ordre où ça a été posé ([drawOrder]) : un coup de
 * gomme creuse ce qui a été posé avant lui, jamais ce qui vient après.
 */
class Layer(val depth: Long, var ax: Double, var ay: Double) {
    val strokes = ArrayList<Stroke>()
    val images = ArrayList<ImageItem>()

    val isEmpty: Boolean get() = strokes.isEmpty() && images.isEmpty()
    /** A déjà porté quelque chose : on ne l'oublie plus (l'historique compte sur son repère). */
    var used = false

    private var cached: DoubleArray? = null
    private var boundsValid = false
    private var order: List<LayerItem>? = null
    private var eraser = false

    fun add(s: Stroke) {
        strokes.add(s)
        used = true
        invalidate()
    }

    fun remove(id: Long) {
        strokes.removeAll { it.id == id }
        invalidate()
    }

    fun addImage(i: ImageItem) {
        images.add(i)
        used = true
        invalidate()
    }

    fun removeImage(id: Long) {
        images.removeAll { it.id == id }
        invalidate()
    }

    fun replaceImage(i: ImageItem) {
        val k = images.indexOfFirst { it.id == i.id }
        if (k >= 0) images[k] = i
        invalidate()
    }

    fun invalidate() {
        boundsValid = false
        order = null
    }

    /** Traits, coups de gomme et images, dans l'ordre où ils ont été posés : l'ordre de dessin. */
    fun drawOrder(): List<LayerItem> =
        order ?: ArrayList<LayerItem>(strokes.size + images.size).apply {
            addAll(strokes)
            addAll(images)
            sortBy { it.id }
        }.also { order = it }

    /** Contient au moins un coup de gomme : il faut alors la composer à part pour qu'il ne creuse qu'elle. */
    val hasEraser: Boolean get() { bounds(); return eraser }

    /**
     * Rectangle englobant (minX, minY, maxX, maxY) de ce qu'on voit, épaisseur comprise, ou null si
     * rien. Les coups de gomme n'y comptent pas : ils ne font que creuser.
     */
    fun bounds(): DoubleArray? {
        if (!boundsValid) {
            var x0 = Double.POSITIVE_INFINITY; var y0 = Double.POSITIVE_INFINITY
            var x1 = Double.NEGATIVE_INFINITY; var y1 = Double.NEGATIVE_INFINITY
            eraser = false
            for (s in strokes) {
                if (s.isEraser) { eraser = true; continue }
                val h = s.width / 2
                x0 = min(x0, s.x + s.minX - h); y0 = min(y0, s.y + s.minY - h)
                x1 = max(x1, s.x + s.maxX + h); y1 = max(y1, s.y + s.maxY + h)
            }
            for (i in images) {
                x0 = min(x0, i.x - i.w / 2); y0 = min(y0, i.y - i.h / 2)
                x1 = max(x1, i.x + i.w / 2); y1 = max(y1, i.y + i.h / 2)
            }
            cached = if (x1 < x0) null else doubleArrayOf(x0, y0, x1, y1)
            boundsValid = true
        }
        return cached
    }

    /** Le trait [s] passe-t-il sur ce qu'on voit de la couche ? (Un coup de gomme dans le vide ne sert à rien.) */
    fun touches(s: Stroke): Boolean {
        val b = bounds() ?: return false
        val h = s.width / 2
        return s.x + s.maxX + h >= b[0] && s.x + s.minX - h <= b[2] && s.y + s.maxY + h >= b[1] && s.y + s.minY - h <= b[3]
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
 * Un trait de couleur transparente ([isEraser]) est un coup de gomme : il dessine de la
 * transparence, qui creuse ce que sa couche a reçu avant lui et laisse voir la couche du dessous.
 *
 * [kind] dit avec quoi il a été tracé : le crayon ([PEN]) garde la même épaisseur partout ; le
 * pinceau ([BRUSH]) s'affine aux deux bouts ; le feutre ([MARKER]) se multiplie avec ce qu'il
 * recouvre, comme une encre transparente (deux passages foncent).
 */
class Stroke(
    override val id: Long, val x: Double, val y: Double, val pts: DoubleArray, val color: Int, val width: Double,
    val kind: Int = PEN,
) : LayerItem {
    companion object {
        const val PEN = 0
        const val BRUSH = 1
        const val MARKER = 2

        /** Le pinceau s'affine sur une longueur de [BRUSH_TAPER] fois son épaisseur, à chaque bout. */
        const val BRUSH_TAPER = 3.0
        /** Épaisseur du pinceau tout au bout, en part de son épaisseur. */
        const val BRUSH_TIP = 0.15

        /** Part de l'épaisseur du pinceau à [d] (unités de la couche) du bout le plus proche. */
        fun brushFactor(d: Double, width: Double): Double {
            val t = d / (BRUSH_TAPER * width)
            return if (t >= 1.0) 1.0 else max(BRUSH_TIP, sqrt(max(t, 0.0)))
        }
    }

    val pointCount: Int get() = pts.size / 2

    val isEraser: Boolean get() = color ushr 24 == 0

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

    private val totalLength: Double by lazy {
        var sum = 0.0
        var i = 0
        while (i + 3 < pts.size) {
            val dx = pts[i + 2] - pts[i]
            val dy = pts[i + 3] - pts[i + 1]
            sum += sqrt(dx * dx + dy * dy)
            i += 2
        }
        sum
    }

    /** Longueur du trait (unités locales) : l'affinement du pinceau se règle dessus. */
    fun length(): Double = totalLength
}

/**
 * Une image posée sur une couche : son centre ([x], [y]) et sa taille ([w], [h]) en unités de la
 * couche. Les pixels vivent dans un fichier du projet ([key]) de [pxW]×[pxH] pixels.
 */
class ImageItem(override val id: Long, val key: String, val pxW: Int, val pxH: Int, val x: Double, val y: Double, val w: Double, val h: Double) : LayerItem {
    fun contains(px: Double, py: Double) = px >= x - w / 2 && px <= x + w / 2 && py >= y - h / 2 && py <= y + h / 2
    fun moved(dx: Double, dy: Double) = ImageItem(id, key, pxW, pxH, x + dx, y + dy, w, h)
}
