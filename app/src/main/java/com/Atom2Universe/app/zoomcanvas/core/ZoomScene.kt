package com.Atom2Universe.app.zoomcanvas.core

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
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
        l.mapBoxes { it.moved(-dx, -dy) }
        layer(l.depth + 1)?.let { it.ax -= dx; it.ay -= dy }
        if (l.depth == depth) { cx -= dx; cy -= dy }
    }

    // ---- Objets à boîte : images, formes, textes --------------------------------------------

    /**
     * Pose une image de [pxW]×[pxH] pixels au centre de l'écran, dans la couche de travail, à une
     * taille qui tient dans [fitW]×[fitH] pixels d'écran.
     */
    fun addImage(key: String, pxW: Int, pxH: Int, fitW: Double, fitH: Double): ImageItem {
        val l = layer(depth)!!
        anchorIfFresh(l)
        val k = min(fitW / pxW, fitH / pxH) / zoom
        val item = ImageItem(nextStrokeId++, key, pxW, pxH, cx, cy, pxW * k, pxH * k)
        l.addBox(item)
        record(Edit.AddBox(depth, item))
        return item
    }

    /**
     * Pose un texte de police [fontPx] pixels d'écran (rangée en unités de la couche, ÷ zoom, comme
     * l'épaisseur d'un trait) dont le centre est à l'écart d'écran [sx], [sy]. [unitWidth] est la
     * largeur de sa ligne la plus longue pour une police de taille 1 : l'écran la mesure.
     */
    fun addText(text: String, sx: Double, sy: Double, fontPx: Double, color: Int, style: Int, font: String, unitWidth: Double): TextItem? {
        val t = text.take(TextItem.MAX_LENGTH)
        if (t.isBlank()) return null
        val l = layer(depth)!!
        anchorIfFresh(l)
        val p = screenToLocal(sx, sy)
        val size = fontPx / zoom
        val item = TextItem(nextStrokeId++, t, p[0], p[1], unitWidth * size, TextItem.heightOf(t, size), size, color, style, font)
        l.addBox(item)
        record(Edit.AddBox(depth, item))
        return item
    }

    /**
     * L'élément de la couche de travail sous l'écart d'écran [sx], [sy] (le plus en avant), ou null.
     * Avec [below], celui qui est juste sous cet élément à cet endroit. [textOnly] ne cherche que les
     * textes, [erasers] que les coups de gomme (le mode d'édition des gommes).
     */
    fun boxAt(sx: Double, sy: Double, below: Long? = null, textOnly: Boolean = false, erasers: Boolean = false): BoxItem? {
        val l = layer(depth) ?: return null
        val p = screenToLocal(sx, sy)
        return l.boxAt(p[0], p[1], HIT_SLACK_PX / zoom, below, textOnly, erasers)
    }

    fun box(id: Long): BoxItem? = layer(depth)?.box(id)

    /** Coins d'un objet à l'écran (écarts depuis le centre) : x0, y0, x1, y1. */
    fun boxScreenRect(item: BoxItem): DoubleArray = doubleArrayOf(
        (item.x - item.w / 2 - cx) * zoom, (item.y - item.h / 2 - cy) * zoom,
        (item.x + item.w / 2 - cx) * zoom, (item.y + item.h / 2 - cy) * zoom,
    )

    private var boxBefore: BoxItem? = null

    /** Début d'un déplacement ou d'un redimensionnement de l'objet [id] (un seul pas d'historique). */
    fun beginBoxEdit(id: Long): Boolean {
        boxBefore = box(id)
        return boxBefore != null
    }

    /** Déplace l'objet en cours d'édition de [dx], [dy] pixels d'écran. */
    fun moveBoxBy(dx: Double, dy: Double) {
        val cur = boxBefore?.let { box(it.id) } ?: return
        replaceBox(cur.moved(dx / zoom, dy / zoom))
    }

    /**
     * Redimensionne l'objet en cours d'édition par son coin [corner] (0 haut-gauche, 1 haut-droit,
     * 2 bas-droit, 3 bas-gauche), tiré jusqu'à l'écart d'écran [sx], [sy]. Le coin opposé ne bouge
     * pas. Une image et un texte gardent leurs proportions ; une forme se déforme librement, et sa
     * boîte se retourne si on tire le coin de l'autre côté du coin fixe.
     */
    fun resizeBoxTo(corner: Int, sx: Double, sy: Double) {
        val start = boxBefore ?: return
        val signX = if (corner == 1 || corner == 2) 1.0 else -1.0
        val signY = if (corner >= 2) 1.0 else -1.0
        val ox = start.x - signX * start.w / 2
        val oy = start.y - signY * start.h / 2
        val p = screenToLocal(sx, sy)
        if (start.keepsRatio) {
            if (start.w <= 0.0 && start.h <= 0.0) return
            // On suit le doigt sur la diagonale : l'échelle est la projection du doigt sur celle-ci.
            val dx = signX * start.w
            val dy = signY * start.h
            val k = (((p[0] - ox) * dx + (p[1] - oy) * dy) / (dx * dx + dy * dy)).coerceAtLeast(MIN_IMAGE_PX / (zoom * max(start.w, start.h)))
            val w = start.w * k
            val h = start.h * k
            replaceBox(start.boxed(ox + signX * w / 2, oy + signY * h / 2, w, h))
            return
        }
        // Forme libre : la boîte va du coin fixe au doigt, retournée si le doigt passe de l'autre côté.
        var w = abs(p[0] - ox)
        var h = abs(p[1] - oy)
        val floor = MIN_IMAGE_PX / zoom
        if (w < floor && h < floor) {
            // Jamais réduite à un point : une forme qu'on ne voit plus ne se rattrape pas.
            if (w >= h) w = floor else h = floor
        }
        val crossX = (p[0] - ox) * signX < 0
        val crossY = (p[1] - oy) * signY < 0
        val x = ox + (if (crossX) -signX else signX) * w / 2
        val y = oy + (if (crossY) -signY else signY) * h / 2
        replaceBox(start.boxed(x, y, w, h, crossX, crossY))
    }

    fun endBoxEdit() {
        val before = boxBefore ?: return
        boxBefore = null
        val after = box(before.id) ?: return
        if (!sameBox(after, before)) record(Edit.ChangeBox(depth, before, after))
    }

    /** Deux objets identiques ? (Une image n'a pas d'égalité de valeur : on compare sa boîte.) */
    private fun sameBox(a: BoxItem, b: BoxItem): Boolean = when {
        a is ImageItem && b is ImageItem -> a.x == b.x && a.y == b.y && a.w == b.w && a.h == b.h
        a is StrokeBox && b is StrokeBox ->
            a.x == b.x && a.y == b.y && a.w == b.w && a.h == b.h && a.stroke.color == b.stroke.color && a.stroke.width == b.stroke.width
        else -> a == b
    }

    /**
     * Remplace l'objet de la couche de travail qui a l'identifiant de [item] (couleur, style,
     * texte, remplissage…) en un seul pas d'historique.
     */
    fun changeBox(item: BoxItem) {
        val before = box(item.id) ?: return
        if (sameBox(before, item)) return
        replaceBox(item)
        record(Edit.ChangeBox(depth, before, item))
    }

    /** Pose une copie de l'objet [id], décalée de [offsetPx] pixels d'écran, devant tout le reste. Rend la copie. */
    fun duplicateBox(id: Long, offsetPx: Double): BoxItem? {
        val src = box(id) ?: return null
        val d = offsetPx / zoom
        val copy = when (val m = src.moved(d, d)) {
            is ImageItem -> ImageItem(nextStrokeId, m.key, m.pxW, m.pxH, m.x, m.y, m.w, m.h)
            is ShapeItem -> m.copy(id = nextStrokeId)
            is TextItem -> m.copy(id = nextStrokeId)
            is StrokeBox -> StrokeBox(Stroke(nextStrokeId, m.stroke.x, m.stroke.y, m.stroke.pts, m.stroke.color, m.stroke.width, m.stroke.kind))
        }
        nextStrokeId++
        val l = layer(depth)!!
        val at = l.orderIndex(id) + 1
        l.addBox(copy, at)
        record(Edit.AddBox(depth, copy, at))
        return copy
    }

    fun deleteBox(id: Long) {
        val l = layer(depth) ?: return
        val item = l.box(id) ?: return
        val at = l.removeBox(id)
        record(Edit.RemoveBox(depth, item, at))
    }

    /** Les images utilisées par le projet (clés de fichier), pour ranger les fichiers orphelins. */
    fun imageKeys(): Set<String> = layers.flatMap { l -> l.images.map { it.key } }.toSet()

    private fun replaceBox(item: BoxItem) {
        layer(depth)?.replaceBox(item)
        contentVersion++
    }

    // ---- Pile des éléments de la couche de travail -----------------------------------------

    /** Où en est [id] dans la pile, et ce qu'on peut encore faire : rang visible, nombre, et les quatre déplacements. */
    class OrderInfo(val rank: Int, val count: Int, val canBackward: Boolean, val canForward: Boolean, val canToBack: Boolean, val canToFront: Boolean)

    fun orderInfo(id: Long): OrderInfo? {
        val l = layer(depth) ?: return null
        val r = l.visibleRank(id) ?: return null
        return OrderInfo(
            r[0], r[1],
            l.moveTarget(id, OrderMove.BACKWARD) != null, l.moveTarget(id, OrderMove.FORWARD) != null,
            l.moveTarget(id, OrderMove.TO_BACK) != null, l.moveTarget(id, OrderMove.TO_FRONT) != null,
        )
    }

    /** Monte ou descend l'élément [id] dans la pile de la couche de travail (un pas d'historique). Faux s'il n'y a rien à faire. */
    fun reorder(id: Long, move: OrderMove): Boolean {
        val l = layer(depth) ?: return false
        val to = l.moveTarget(id, move) ?: return false
        val from = l.orderIndex(id)
        l.moveInOrder(id, to)
        record(Edit.Reorder(depth, id, from, to))
        return true
    }

    // ---- Formes (tracées au doigt, d'un coin à l'autre) ------------------------------------

    private var shapeAx = 0.0
    private var shapeAy = 0.0
    private var shapeBx = 0.0
    private var shapeBy = 0.0
    private var shapeActive = false
    private var shapeKind = ShapeKind.RECT
    private var shapeFill = ShapeFill.OUTLINE
    private var shapeStroke = 0
    private var shapeFillColor = 0
    private var shapeWidth = 1.0
    private var shapeSquare = false

    val isDrawingShape: Boolean get() = shapeActive

    /**
     * Commence une forme dont un coin est à l'écart d'écran [sx], [sy]. [widthPx] est l'épaisseur du
     * contour à l'écran au moment du tracé : rangée en unités de la couche (÷ zoom), comme un crayon.
     * [square] force une forme carrée / ronde / équilatérale (sans effet sur la ligne et la flèche).
     */
    fun beginShape(kind: ShapeKind, fill: ShapeFill, strokeColor: Int, fillColor: Int, widthPx: Double, sx: Double, sy: Double, square: Boolean) {
        anchorIfFresh(layer(depth)!!)
        val p = screenToLocal(sx, sy)
        shapeAx = p[0]; shapeAy = p[1]; shapeBx = p[0]; shapeBy = p[1]
        shapeKind = kind; shapeFill = fill; shapeStroke = strokeColor; shapeFillColor = fillColor
        shapeWidth = widthPx / zoom
        shapeSquare = square
        shapeActive = true
    }

    /** Tire le coin opposé jusqu'à l'écart d'écran [sx], [sy]. */
    fun updateShape(sx: Double, sy: Double) {
        if (!shapeActive) return
        val p = screenToLocal(sx, sy)
        shapeBx = p[0]; shapeBy = p[1]
    }

    /** La forme en cours (id 0), tracée par-dessus tout le reste, ou null. */
    fun liveShape(): ShapeItem? = if (shapeActive) buildShape(0L) else null

    private fun buildShape(id: Long): ShapeItem {
        var dx = shapeBx - shapeAx
        var dy = shapeBy - shapeAy
        val directed = shapeKind == ShapeKind.LINE || shapeKind == ShapeKind.ARROW
        if (shapeSquare && !directed) {
            val m = max(abs(dx), abs(dy))
            dx = if (dx < 0) -m else m
            dy = if (dy < 0) -m else m
        }
        return ShapeItem(
            id, shapeKind, shapeAx + dx / 2, shapeAy + dy / 2, abs(dx), abs(dy),
            directed && dx < 0, directed && dy < 0, shapeStroke, shapeFillColor, shapeFill, shapeWidth,
        )
    }

    fun cancelShape() {
        shapeActive = false
    }

    /** Termine la forme et la pose dans la couche de travail. Un simple appui, trop petit, ne laisse rien. */
    fun endShape(): ShapeItem? {
        if (!shapeActive) return null
        shapeActive = false
        val probe = buildShape(0L)
        if (max(probe.w, probe.h) * zoom < MIN_SHAPE_PX) return null
        val item = probe.copy(id = nextStrokeId++)
        layer(depth)!!.addBox(item)
        record(Edit.AddBox(depth, item))
        return item
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
        /** [index] : rang de dessin de l'objet (-1 : tout devant). */
        class AddBox(val depth: Long, val item: BoxItem, val index: Int = -1) : Edit()
        class RemoveBox(val depth: Long, val item: BoxItem, val index: Int) : Edit()
        /** L'élément [id] passe du rang [from] au rang [to] de la pile de sa couche. */
        class Reorder(val depth: Long, val id: Long, val from: Int, val to: Int) : Edit()
        class ChangeBox(val depth: Long, val before: BoxItem, val after: BoxItem) : Edit()
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
            is Edit.AddBox -> {
                val l = layer(e.depth) ?: return
                if (reverse) l.removeBox(e.item.id) else l.addBox(e.item, e.index)
            }
            is Edit.RemoveBox -> {
                val l = layer(e.depth) ?: return
                if (reverse) l.addBox(e.item, e.index) else l.removeBox(e.item.id)
            }
            is Edit.Reorder -> layer(e.depth)?.moveInOrder(e.id, if (reverse) e.from else e.to)
            is Edit.ChangeBox -> layer(e.depth)?.replaceBox(if (reverse) e.before else e.after)
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
            for (i in l.shapes) maxId = max(maxId, i.id)
            for (i in l.texts) maxId = max(maxId, i.id)
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
        /** Un objet ne se réduit pas en dessous de cette taille à l'écran (pixels). */
        const val MIN_IMAGE_PX = 12.0
        /** Une forme tracée plus petite que ça (un simple appui) n'est pas gardée. */
        const val MIN_SHAPE_PX = 6.0
        /** De combien un doigt déborde d'un objet pour le toucher (pixels d'écran). */
        const val HIT_SLACK_PX = 8.0
        const val MAX_HISTORY = 300
    }
}

/** Ce qu'une couche contient : un trait (gomme comprise), une image, une forme ou un texte, chacun avec son identifiant. */
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
    val shapes = ArrayList<ShapeItem>()
    val texts = ArrayList<TextItem>()

    val isEmpty: Boolean get() = strokes.isEmpty() && images.isEmpty() && shapes.isEmpty() && texts.isEmpty()

    /** Ce qu'on voit dans la couche (les coups de gomme ne comptent pas, ils ne font que creuser). */
    val itemCount: Int get() = strokes.count { !it.isEraser } + images.size + shapes.size + texts.size
    /** A déjà porté quelque chose : on ne l'oublie plus (l'historique compte sur son repère). */
    var used = false

    private var cached: DoubleArray? = null
    private var boundsValid = false
    private var order: List<LayerItem>? = null
    private var eraser = false

    /**
     * Les identifiants dans l'ordre de dessin : du fond (premier) à l'avant (dernier). Par défaut,
     * l'ordre où les éléments ont été posés ; la sélection peut les monter ou les descendre.
     */
    private val zOrder = ArrayList<Long>()

    fun add(s: Stroke) {
        strokes.add(s)
        zOrder.add(s.id)
        used = true
        invalidate()
    }

    fun remove(id: Long) {
        strokes.removeAll { it.id == id }
        zOrder.remove(id)
        invalidate()
    }

    fun addImage(i: ImageItem) = addBox(i)

    /** Pose une image, une forme, un texte ou un trait, à la fin (devant tout) ou au rang [index] de l'ordre de dessin. */
    fun addBox(b: BoxItem, index: Int = -1) {
        when (b) {
            is ImageItem -> images.add(b)
            is ShapeItem -> shapes.add(b)
            is TextItem -> texts.add(b)
            is StrokeBox -> strokes.add(b.stroke)
        }
        if (index in 0..zOrder.size) zOrder.add(index, b.id) else zOrder.add(b.id)
        used = true
        invalidate()
    }

    /** Retire l'élément [id] et rend le rang qu'il avait dans l'ordre de dessin (-1 s'il n'y était pas). */
    fun removeBox(id: Long): Int {
        val at = zOrder.indexOf(id)
        images.removeAll { it.id == id }
        shapes.removeAll { it.id == id }
        texts.removeAll { it.id == id }
        strokes.removeAll { it.id == id }
        if (at >= 0) zOrder.removeAt(at)
        invalidate()
        return at
    }

    /** Remplace l'élément de même identifiant : son rang de dessin ne change pas. */
    fun replaceBox(b: BoxItem) {
        when (b) {
            is ImageItem -> { val k = images.indexOfFirst { it.id == b.id }; if (k >= 0) images[k] = b }
            is ShapeItem -> { val k = shapes.indexOfFirst { it.id == b.id }; if (k >= 0) shapes[k] = b }
            is TextItem -> { val k = texts.indexOfFirst { it.id == b.id }; if (k >= 0) texts[k] = b }
            is StrokeBox -> { val k = strokes.indexOfFirst { it.id == b.id }; if (k >= 0) strokes[k] = b.stroke }
        }
        invalidate()
    }

    /** L'élément [id] (un coup de gomme compris : on l'édite dans le mode d'édition des gommes), ou null. */
    fun box(id: Long): BoxItem? =
        images.firstOrNull { it.id == id } ?: shapes.firstOrNull { it.id == id } ?: texts.firstOrNull { it.id == id }
            ?: strokes.firstOrNull { it.id == id }?.let { StrokeBox(it) }

    /**
     * L'élément que touche le point, le plus en avant d'abord. Avec [below], celui qui est juste
     * sous cet élément à cet endroit (et, s'il n'y en a plus, on repart du dessus) : un appui répété
     * descend ainsi dans la pile. [textOnly] ne regarde que les textes ; [erasers] ne regarde que les
     * coups de gomme (et eux seuls : sinon ils sont invisibles pour la sélection).
     */
    fun boxAt(px: Double, py: Double, slack: Double, below: Long? = null, textOnly: Boolean = false, erasers: Boolean = false): BoxItem? {
        val hits = ArrayList<BoxItem>(2)
        val items = drawOrder()
        for (k in items.indices.reversed()) {
            val item = items[k]
            val box: BoxItem = when (item) {
                is BoxItem -> if (erasers) continue else item
                is Stroke -> if (item.isEraser != erasers) continue else StrokeBox(item)
            }
            if (textOnly && box !is TextItem) continue
            if (box.hit(px, py, slack)) hits.add(box)
        }
        if (hits.isEmpty()) return null
        if (below == null) return hits[0]
        val at = hits.indexOfFirst { it.id == below }
        return hits[if (at < 0) 0 else (at + 1) % hits.size]
    }

    /** Remplace tous les objets à boîte (hors traits) par [f] appliquée à chacun (recentrage de la couche). */
    fun mapBoxes(f: (BoxItem) -> BoxItem) {
        for (i in images.indices) images[i] = f(images[i]) as ImageItem
        for (i in shapes.indices) shapes[i] = f(shapes[i]) as ShapeItem
        for (i in texts.indices) texts[i] = f(texts[i]) as TextItem
        invalidate()
    }

    fun invalidate() {
        boundsValid = false
        order = null
    }

    /** Traits, coups de gomme, images, formes et textes dans l'ordre de dessin : du fond vers l'avant. */
    fun drawOrder(): List<LayerItem> =
        order ?: run {
            val byId = HashMap<Long, LayerItem>(zOrder.size * 2 + 1)
            for (s in strokes) byId[s.id] = s
            for (i in images) byId[i.id] = i
            for (i in shapes) byId[i.id] = i
            for (i in texts) byId[i.id] = i
            zOrder.mapNotNull { byId[it] }
        }.also { order = it }

    // ---- Ordre de dessin ------------------------------------------------------------------

    /** Remet l'ordre par défaut : celui des identifiants, donc de la pose. */
    fun resetOrderById() {
        zOrder.clear()
        zOrder.addAll((strokes.map { it.id } + images.map { it.id } + shapes.map { it.id } + texts.map { it.id }).sorted())
        invalidate()
    }

    /** Impose l'ordre [ids] (du fond vers l'avant) ; ce qui n'y figure pas est posé devant, dans l'ordre des identifiants. */
    fun setOrder(ids: List<Long>) {
        val known = HashSet<Long>(zOrder)
        val wanted = ids.filter { it in known }.distinct()
        val wantedSet = wanted.toSet()
        val missing = zOrder.filter { it !in wantedSet }.sorted()
        zOrder.clear()
        zOrder.addAll(wanted)
        zOrder.addAll(missing)
        invalidate()
    }

    /** L'ordre n'est plus celui de la pose : il faut le ranger dans le fichier. */
    val hasCustomOrder: Boolean get() = zOrder != zOrder.sorted()
    val orderIds: List<Long> get() = zOrder

    fun orderIndex(id: Long): Int = zOrder.indexOf(id)

    private fun visibleAt(items: List<LayerItem>, k: Int): Boolean = !(items[k] is Stroke && (items[k] as Stroke).isEraser)

    /**
     * Le rang où irait [id] pour un déplacement [move], ou null s'il n'y a rien à faire. Les coups
     * de gomme se traversent : « d'un cran » veut dire juste après le prochain élément visible.
     * Passer devant un coup de gomme, c'est ne plus être creusé par lui ; passer derrière, l'être.
     */
    fun moveTarget(id: Long, move: OrderMove): Int? {
        val items = drawOrder()
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return null
        return when (move) {
            OrderMove.FORWARD -> (i + 1 until items.size).firstOrNull { visibleAt(items, it) }
            OrderMove.BACKWARD -> (i - 1 downTo 0).firstOrNull { visibleAt(items, it) }
            OrderMove.TO_FRONT -> if (i < items.size - 1) items.size - 1 else null
            OrderMove.TO_BACK -> if ((0 until i).any { visibleAt(items, it) }) 0 else null
        }
    }

    /** Range [id] au rang [to] (compté dans l'ordre d'avant le déplacement). */
    fun moveInOrder(id: Long, to: Int) {
        val from = zOrder.indexOf(id)
        if (from < 0) return
        zOrder.removeAt(from)
        zOrder.add(to.coerceIn(0, zOrder.size), id)
        invalidate()
    }

    /** Rang de [id] parmi les éléments visibles (1 = le plus au fond) et leur nombre, ou null. */
    fun visibleRank(id: Long): IntArray? {
        val items = drawOrder()
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return null
        // Pour un coup de gomme : le nombre d'éléments visibles qu'il y a sous lui, donc ceux qu'il creuse.
        var rank = 0
        var count = 0
        for (k in items.indices) if (visibleAt(items, k)) { count++; if (k <= i) rank++ }
        return intArrayOf(rank, count)
    }

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
            for (i in shapes) {
                val b = ShapeGeometry.bounds(i)
                x0 = min(x0, b[0]); y0 = min(y0, b[1]); x1 = max(x1, b[2]); y1 = max(y1, b[3])
            }
            for (i in texts) {
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
class ImageItem(override val id: Long, val key: String, val pxW: Int, val pxH: Int, override val x: Double, override val y: Double, override val w: Double, override val h: Double) : BoxItem {
    override val keepsRatio: Boolean get() = true
    fun contains(px: Double, py: Double) = hit(px, py, 0.0)
    override fun moved(dx: Double, dy: Double) = ImageItem(id, key, pxW, pxH, x + dx, y + dy, w, h)
    override fun boxed(x: Double, y: Double, w: Double, h: Double, crossX: Boolean, crossY: Boolean) = ImageItem(id, key, pxW, pxH, x, y, w, h)
}
