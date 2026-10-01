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
 *
 * **Une seule couche ([single]).** Le « Canvas » ordinaire est la même toile sans la pile : une seule
 * couche, jamais de seuil. Le zoom y est simplement borné à [minZoom]..[maxZoom] (de ×1/100 à ×100, voir
 * [SINGLE_RATIO]) et la caméra ne quitte jamais la couche 0. Tout le reste (traits, formes, textes,
 * images, gomme, pile d'éléments, enregistrement) est le même code.
 */
class ZoomScene(
    val ratio: Double = DEFAULT_RATIO,
    val maxZoom: Double = Math.sqrt(ratio),
    startZoom: Double = Double.NaN,
    val single: Boolean = false,
) {

    init {
        require(ratio > 1.0 && ratio.isFinite()) { "ratio must be > 1" }
        require(maxZoom > 0.0 && maxZoom.isFinite()) { "maxZoom must be > 0" }
    }

    private val logRatio = ln(ratio)

    /** Ce qui a changé depuis la dernière écriture : les couches y notent chaque pose, retrait ou remplacement. */
    private val log = ChangeLog()

    private fun newLayer(depth: Long, ax: Double, ay: Double): Layer = Layer(depth, ax, ay).also { it.journal = log }

    /** Les couches, de la plus haute (la plus grande) à la plus profonde. Jamais vide. */
    private val layers = ArrayList<Layer>().apply { add(newLayer(0L, 0.0, 0.0)) }
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
    fun zoomAt(factor0: Double, fx: Double, fy: Double) {
        if (!(factor0 > 0.0) || !factor0.isFinite()) return
        // Une seule couche : le zoom butte sur ses bornes (le point sous le doigt reste en place, comme ailleurs).
        val factor = if (single) (zoom * factor0).coerceIn(minZoom, maxZoom) / zoom else factor0
        // Le point sous le doigt, dans la couche de travail : il doit rester sous le doigt.
        val px = cx + fx / zoom
        val py = cy + fy / zoom
        zoom *= factor
        cx = px - fx / zoom
        cy = py - fy / zoom
        normalize()
        cameraVersion++
        residencyCheck()
    }

    /** Place la caméra directement (reprise d'un projet, saut vers une couche). */
    fun setCamera(d: Long, x: Double, y: Double, z: Double) {
        ensureRangeIncludes(d)
        depth = d; cx = x; cy = y; zoom = z
        normalize()
        trim()
        cameraVersion++
        residencyCheck()
    }

    /**
     * Saute vers la couche [d] et cadre tout son contenu dans une vue de [viewW]×[viewH] pixels,
     * en restant dans cette couche.
     */
    fun jumpTo(d: Long, viewW: Double, viewH: Double, maxFit: Double = maxZoom * 0.99) {
        val l = layer(d) ?: return
        var b = l.bounds()
        // Une seule couche : la couche de pixels compte aussi dans ce qu'on cadre.
        if (single) pixels.bounds()?.let { pb ->
            val r = doubleArrayOf(pb[0].toDouble(), pb[1].toDouble(), pb[2] + 1.0, pb[3] + 1.0)
            b = if (b == null) r else doubleArrayOf(min(b[0], r[0]), min(b[1], r[1]), max(b[2], r[2]), max(b[3], r[3]))
        }
        if (b == null) {
            setCamera(d, 0.0, 0.0, homeZoom)
            return
        }
        val w = max(b[2] - b[0], 1e-9)
        val h = max(b[3] - b[1], 1e-9)
        val fit = min(viewW * 0.8 / w, viewH * 0.8 / h)
        setCamera(d, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, fit.coerceIn(minZoom, maxFit))
    }

    private fun normalize() {
        if (single) { zoom = zoom.coerceIn(minZoom, maxZoom); return }
        while (zoom >= maxZoom) descend()
        while (zoom < minZoom) ascend()
    }

    /** La couche de dessous devient la couche de travail. */
    private fun descend() {
        val child = layer(depth + 1) ?: newLayer(depth + 1, Math.rint(cx), Math.rint(cy)).also { layers.add(it) }
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
            layers.add(0, newLayer(depth - 1, 0.0, 0.0))
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
        while (d < firstDepth) { layers.add(0, newLayer(firstDepth - 1, 0.0, 0.0)); firstDepth-- }
        while (d > lastDepth) layers.add(newLayer(lastDepth + 1, 0.0, 0.0))
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

    /**
     * Le trait en cours de tracé. Un seul objet, réutilisé : le rendu le lit tel quel à chaque image
     * (sans copier ses points, qui grossissent avec le geste), sur le même fil que celui qui l'écrit.
     */
    class LiveStroke {
        var originX = 0.0
        var originY = 0.0
        /** Les points (x, y) relatifs à l'origine ; [count] nombres sont valides. */
        var pts = FloatArray(64)
        var count = 0
        var color = 0
        var width = 1.0
        var kind = Stroke.PEN
        val isEraser: Boolean get() = color ushr 24 == 0
    }

    internal val live = LiveStroke()

    /** Le trait en cours (ou null), copié : pour les tests ; le rendu lit [live]. */
    fun liveStroke(): Stroke? =
        if (live.count == 0) null else Stroke(0L, live.originX, live.originY, live.pts.copyOf(live.count), live.color, live.width, live.kind)

    val isDrawing: Boolean get() = live.count > 0

    /**
     * Commence un trait à l'écart d'écran [sx], [sy]. [widthPx] est l'épaisseur à l'écran au moment
     * du trait ; elle est rangée en unités de la couche (÷ zoom), donc le trait grossit ensuite avec
     * le zoom comme le reste du dessin. Un trait de couleur transparente ([ERASER]) est un coup de
     * gomme : il dessine de la transparence, qui creuse ce que sa couche a reçu avant lui.
     * [kind] : crayon, pinceau ou feutre ([Stroke.PEN], [Stroke.BRUSH], [Stroke.MARKER]).
     */
    fun beginStroke(sx: Double, sy: Double, color: Int, widthPx: Double, kind: Int = Stroke.PEN) {
        anchorIfFresh(working())
        val p = screenToLocal(sx, sy)
        live.originX = p[0]
        live.originY = p[1]
        live.count = 0
        live.color = color
        live.width = widthPx / zoom
        live.kind = if (color ushr 24 == 0) Stroke.PEN else kind
        appendLive(0.0, 0.0)
    }

    fun extendStroke(sx: Double, sy: Double) {
        if (live.count == 0) return
        // Un seul geste continu a une fin : au-delà, les points en plus sont ignorés (la ligne du fichier doit rester lisible).
        if (live.count >= 2 * MAX_STROKE_POINTS) return
        // Forme en coordonnées locales autour du premier point : petite, quelle que soit la position.
        val rx = (cx - live.originX) + sx / zoom
        val ry = (cy - live.originY) + sy / zoom
        val lx = live.pts[live.count - 2]
        val ly = live.pts[live.count - 1]
        // Pas de points plus serrés qu'un demi-pixel : inutile et coûteux.
        val minStep = 0.5 / zoom
        if (abs(rx - lx) < minStep && abs(ry - ly) < minStep) return
        appendLive(rx, ry)
    }

    private fun appendLive(x: Double, y: Double) {
        if (live.count + 2 > live.pts.size) live.pts = live.pts.copyOf(live.pts.size * 2)
        live.pts[live.count++] = x.toFloat()
        live.pts[live.count++] = y.toFloat()
    }

    fun cancelStroke() {
        live.count = 0
    }

    /** Termine le trait et l'ajoute à la couche de travail. Un coup de gomme dans le vide ne laisse rien. */
    fun endStroke(): Stroke? {
        if (live.count == 0) return null
        val l = working()
        val s = Stroke(nextStrokeId, live.originX, live.originY, live.pts.copyOf(live.count), live.color, live.width, live.kind)
        live.count = 0
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
        // Une seule couche : pas d'ancre dans une couche du dessus, le repère ne bouge jamais.
        if (single || l.used || !l.isEmpty) return
        // Un multiple du rapport : l'ancre de la couche reste un nombre entier de pixels du dessus.
        rebase(l, Math.rint(cx / ratio) * ratio, Math.rint(cy / ratio) * ratio)
    }

    /**
     * Décale le repère de [l] de ([dx], [dy]) de ses propres unités, sans rien bouger à l'écran.
     * Réservé à une couche vide (voir [anchorIfFresh]) : il n'y a donc aucun élément à décaler dedans.
     */
    private fun rebase(l: Layer, dx: Double, dy: Double) {
        l.ax += dx / ratio
        l.ay += dy / ratio
        layer(l.depth + 1)?.let { it.ax -= dx; it.ay -= dy }
        if (l.depth == depth) { cx -= dx; cy -= dy }
    }

    // ---- Objets à boîte : images, formes, textes --------------------------------------------

    /**
     * Pose une image de [pxW]×[pxH] pixels au centre de l'écran, dans la couche de travail, à une
     * taille qui tient dans [fitW]×[fitH] pixels d'écran.
     */
    fun addImage(key: String, pxW: Int, pxH: Int, fitW: Double, fitH: Double): ImageItem {
        val l = working()
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
        val l = working()
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
    fun imageKeys(): Set<String> = HashSet<String>().also { keys -> for (l in layers) l.imageKeys(keys) }

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
        anchorIfFresh(working())
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
        working().addBox(item)
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

    // ---- Couche de pixels (le « Canvas ») ---------------------------------------------------

    /** La grille de pixels, une couche à part du dessin vectoriel : vide dans un canvas infini à couches. */
    val pixels = PixelLayer()

    /** La couche de pixels est tracée devant le dessin (sinon derrière). Se choisit dans l'éditeur. */
    var pixelsAbove = false
        set(value) {
            if (field == value) return
            field = value
            pixelSettingsDirty = true
            contentVersion++
        }
    private var pixelSettingsDirty = false

    /** Ce que la case ([x], [y]) était avant le geste en cours : de quoi défaire d'un seul pas d'historique. */
    private var pixelBefore: LinkedHashMap<Long, Int>? = null

    val isEditingPixels: Boolean get() = pixelBefore != null

    /** La case sous l'écart d'écran [sx] (pixels depuis le centre de la vue). */
    fun cellX(sx: Double): Int = Math.floor(cx + sx / zoom).coerceIn(-PixelLayer.LIMIT.toDouble(), PixelLayer.LIMIT.toDouble()).toInt()
    fun cellY(sy: Double): Int = Math.floor(cy + sy / zoom).coerceIn(-PixelLayer.LIMIT.toDouble(), PixelLayer.LIMIT.toDouble()).toInt()

    /** Commence un geste de pixels : tout ce qu'on peint jusqu'à [endPixelEdit] est un seul pas d'historique. */
    fun beginPixelEdit() {
        pixelBefore = LinkedHashMap()
    }

    /** Peint la case ([x], [y]) pendant un geste ([beginPixelEdit]). Faux si rien n'a changé. */
    fun paintCell(x: Int, y: Int, color: Int): Boolean {
        val before = pixelBefore ?: return false
        val old = pixels.get(x, y)
        if (!pixels.set(x, y, color)) return false
        before.putIfAbsent(PixelLayer.cellKey(x, y), old)
        return true
    }

    /** Remet la case ([x], [y]) comme elle était avant le geste (le « pixel parfait » retire ainsi un coin). */
    fun unpaintCell(x: Int, y: Int) {
        val before = pixelBefore ?: return
        val old = before[PixelLayer.cellKey(x, y)] ?: return
        pixels.set(x, y, old)
    }

    /** Termine le geste : ce qui a vraiment changé entre dans l'historique. Faux s'il n'a rien changé. */
    fun endPixelEdit(): Boolean {
        val before = pixelBefore ?: return false
        pixelBefore = null
        val keys = ArrayList<Long>()
        val was = ArrayList<Int>()
        val now = ArrayList<Int>()
        for ((k, b) in before) {
            val a = pixels.get(PixelLayer.cellX(k), PixelLayer.cellY(k))
            if (a != b) { keys.add(k); was.add(b); now.add(a) }
        }
        if (keys.isEmpty()) return false
        record(Edit.Pixels(keys.toLongArray(), was.toIntArray(), now.toIntArray()))
        return true
    }

    /** Abandonne le geste (un pincement, un geste annulé) : les cases touchées redeviennent ce qu'elles étaient. */
    fun cancelPixelEdit() {
        val before = pixelBefore ?: return
        pixelBefore = null
        for ((k, b) in before) pixels.set(PixelLayer.cellX(k), PixelLayer.cellY(k), b)
        contentVersion++
    }

    /**
     * Remplit, avec [color], la zone de cases de la même couleur que ([x], [y]) qui touche cette case, sans sortir de la
     * boîte ([x0], [y0])–([x1], [y1]) (inclus). Dans le vide, la zone n'a pas de bord : on la borne à l'écran. Rien si elle
     * dépasse [MAX_FILL_CELLS] cases. Un seul pas d'historique. Vrai si quelque chose a été rempli.
     */
    fun fillPixels(x: Int, y: Int, color: Int, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        if (x < x0 || x > x1 || y < y0 || y > y1) return false
        val c = if (color ushr 24 == 0) 0 else color
        val target = pixels.get(x, y)
        if (target == c) return false
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        if (w.toLong() * h > MAX_FILL_AREA) return false
        val seen = BooleanArray(w * h)
        val region = ArrayList<Int>()
        var stack = IntArray(256)
        var n = 0
        fun push(px: Int, py: Int) {
            if (px < x0 || px > x1 || py < y0 || py > y1) return
            val i = (py - y0) * w + (px - x0)
            if (seen[i] || pixels.get(px, py) != target) return
            seen[i] = true
            if (n + 1 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[n++] = i
        }
        push(x, y)
        while (n > 0) {
            val i = stack[--n]
            region.add(i)
            if (region.size > MAX_FILL_CELLS) return false
            val px = x0 + i % w
            val py = y0 + i / w
            push(px - 1, py); push(px + 1, py); push(px, py - 1); push(px, py + 1)
        }
        beginPixelEdit()
        for (i in region) paintCell(x0 + i % w, y0 + i / w, c)
        return endPixelEdit()
    }

    /**
     * Cale la caméra sur la grille : les bords des cases tombent sur des pixels d'écran entiers (des cases nettes,
     * sans cases plus larges que leurs voisines si le zoom est entier). Un demi-pixel de glissement au plus.
     */
    fun snapCameraToPixels(viewW: Double, viewH: Double) {
        cx = (Math.rint(cx * zoom - viewW / 2) + viewW / 2) / zoom
        cy = (Math.rint(cy * zoom - viewH / 2) + viewH / 2) / zoom
        cameraVersion++
    }

    /** Lecture d'un projet : les tuiles (déjà écrites) et l'ordre de la couche de pixels. */
    fun restorePixels(tiles: List<Pair<Long, IntArray>>, above: Boolean) {
        pixels.load(tiles)
        pixelsAbove = above
        pixelSettingsDirty = false
    }

    // ---- Historique -----------------------------------------------------------------------

    sealed class Edit {
        /** La couche que la modification touche : tant qu'elle est dans l'historique, on ne la décharge pas. */
        abstract val depth: Long

        class Add(override val depth: Long, val stroke: Stroke) : Edit()
        class Move(override val depth: Long, val dx: Double, val dy: Double) : Edit()
        /** [index] : rang de dessin de l'objet (-1 : tout devant). */
        class AddBox(override val depth: Long, val item: BoxItem, val index: Int = -1) : Edit()
        class RemoveBox(override val depth: Long, val item: BoxItem, val index: Int) : Edit()
        /** L'élément [id] passe du rang [from] au rang [to] de la pile de sa couche. */
        class Reorder(override val depth: Long, val id: Long, val from: Int, val to: Int) : Edit()
        class ChangeBox(override val depth: Long, val before: BoxItem, val after: BoxItem) : Edit()
        /** Un geste de pixels : les cases [keys] ([PixelLayer.cellKey]) passent de [before] à [after]. */
        class Pixels(val keys: LongArray, val before: IntArray, val after: IntArray) : Edit() {
            override val depth: Long get() = 0L
        }
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
            is Edit.Pixels -> for (i in e.keys.indices) pixels.set(PixelLayer.cellX(e.keys[i]), PixelLayer.cellY(e.keys[i]), if (reverse) e.before[i] else e.after[i])
        }
    }

    // ---- Enregistrement et chargement --------------------------------------------------------

    /** Les couches telles que la base les connaît (profondeur → ancre) : on ne réécrit que celles qui ont changé. */
    private var persisted = HashMap<Long, DoubleArray>()

    /** Y a-t-il des objets posés, retirés ou remplacés qu'on n'a pas encore écrits ? */
    val hasUnsavedItems: Boolean get() = !log.isEmpty || pixels.hasUnsaved || pixelSettingsDirty

    /**
     * Prend ce qui a changé depuis le dernier appel, pour l'écrire : les objets touchés (leur dernier
     * état), ceux qui sont partis, les couches dont la ligne change, et la caméra. Rapide : son coût
     * suit le nombre de changements, pas la taille du dessin. Les objets sont immuables, le lot peut
     * donc être écrit sur un autre fil. Si l'écriture échoue, [requeueChanges] le remet.
     *
     * Les couches gardées vont de la plus haute couche dessinée (ou de la caméra) à la plus profonde.
     * Les couches vides coincées entre les deux ne gardent que leur ancre.
     */
    fun drainChanges(): SceneDelta {
        var lo = depth
        var hi = depth
        for (l in layers) if (!l.isEmpty) { lo = min(lo, l.depth); hi = max(hi, l.depth) }
        val rows = ArrayList<LayerRow>()
        val kept = HashMap<Long, DoubleArray>()
        for (d in lo..hi) {
            val l = layer(d)!!
            val known = persisted[d]
            if (known == null || known[0] != l.ax || known[1] != l.ay || d in log.touchedLayers) {
                val sum = l.summary()
                rows.add(LayerRow(d, l.ax, l.ay, sum.count, sum.erasers, sum.bounds))
            }
            kept[d] = doubleArrayOf(l.ax, l.ay)
        }
        persisted = kept
        val versions = HashMap<Long, Long>()
        for (d in log.touchedLayers) layer(d)?.let { versions[d] = it.version }
        val delta = SceneDelta(
            ArrayList(log.upserts.values), log.deletes.toLongArray(), rows, lo, hi,
            depth, cx, cy, zoom, nextStrokeId, contentVersion, cameraVersion, versions,
            pixels.drain(), pixelsAbove, pixelSettingsDirty,
        )
        pixelSettingsDirty = false
        log.clear()
        return delta
    }

    /** L'écriture de [delta] a échoué : ses changements repartent dans le journal (ce qui s'est passé depuis l'emporte). */
    fun requeueChanges(delta: SceneDelta) {
        log.requeue(delta)
        pixels.requeue(delta.pixels)
        if (delta.pixelSettingsChanged) pixelSettingsDirty = true
        persisted.clear()
    }

    /**
     * Remplace tout le contenu (lecture d'un projet). [loaded] est une suite contiguë de couches
     * commençant à [first]. Ce qu'on vient de lire est déjà écrit : le journal repart vide.
     */
    fun restore(first: Long, loaded: List<Layer>, camDepth: Long, camX: Double, camY: Double, camZoom: Double, nextId: Long) {
        layers.clear()
        if (loaded.isEmpty()) {
            layers.add(newLayer(camDepth, 0.0, 0.0))
            firstDepth = camDepth
        } else {
            for (l in loaded) { l.journal = log; layers.add(l) }
            firstDepth = first
            for (l in layers) l.used = l.used || !l.isEmpty
        }
        var maxId = 0L
        for (l in layers) for (e in l.entries()) maxId = max(maxId, e.id)
        nextStrokeId = max(nextId, maxId + 1)
        undoStack.clear(); redoStack.clear()
        persisted = HashMap<Long, DoubleArray>().also { m -> for (l in layers) m[l.depth] = doubleArrayOf(l.ax, l.ay) }
        val z = if (camZoom.isFinite() && camZoom > 0) camZoom else homeZoom
        val x = if (camX.isFinite()) camX else 0.0
        val y = if (camY.isFinite()) camY else 0.0
        setCamera(camDepth, x, y, z)
        log.clear()
        refreshResidency()
    }

    // ---- Couches en mémoire --------------------------------------------------------------------

    /**
     * Ce qui lit les éléments d'une couche dans la base : la scène ne connaît pas la base, elle dit
     * seulement quelles couches elle voudra bientôt ([wantLoad], sans attendre : le résultat revient
     * par [installLayer]) ou tout de suite ([loadNow], qui bloque : seulement si on va dessiner dans
     * une couche pas encore arrivée).
     */
    interface LayerPager {
        fun wantLoad(depth: Long)
        fun loadNow(depth: Long): List<Pair<LayerItem, Long>>?
    }

    /** Sans pager (tests, projet neuf), toutes les couches restent en mémoire. */
    var pager: LayerPager? = null

    private var residencyDepth = Long.MIN_VALUE

    /** À appeler après avoir posé le [pager] : charge et décharge selon la couche de travail actuelle. */
    fun refreshResidency() {
        residencyDepth = Long.MIN_VALUE
        residencyCheck()
    }

    private fun residencyCheck() {
        if (depth != residencyDepth) {
            residencyDepth = depth
            updateResidency()
        }
    }

    /**
     * Garde en mémoire la couche de travail, celle du dessus (pour quand on dézoome : elle est prête)
     * et les deux d'en dessous (la deuxième s'affiche dès qu'on zoome assez pour changer de couche) ;
     * demande celles qui manquent et décharge celles qui sont loin. Un peu plus de marge pour
     * décharger que pour charger : autour d'un seuil, on ne recharge pas à chaque passage. On ne
     * décharge jamais une couche pas encore écrite, ni une couche que l'historique d'annulation cite.
     */
    private fun updateResidency() {
        val p = pager ?: return
        for (d in depth - LOAD_ABOVE..depth + LOAD_BELOW) {
            val l = layer(d) ?: continue
            if (!l.isLoaded) p.wantLoad(d)
        }
        var pinned: HashSet<Long>? = null
        for (l in layers) {
            if (!l.isLoaded || l.depth in depth - KEEP_ABOVE..depth + KEEP_BELOW || l.dirty) continue
            if (pinned == null) {
                pinned = HashSet()
                for (e in undoStack) pinned.add(e.depth)
                for (e in redoStack) pinned.add(e.depth)
            }
            if (l.depth !in pinned) l.unload()
        }
    }

    /** Les éléments de la couche [depth] viennent d'être lus : on les installe. Faux si elle est déjà là ou n'est plus voulue. */
    fun installLayer(depth: Long, items: List<Pair<LayerItem, Long>>): Boolean {
        val l = layer(depth) ?: return false
        if (l.isLoaded || depth !in this.depth - KEEP_ABOVE..this.depth + KEEP_BELOW) return false
        l.load(items)
        return true
    }

    /** Le lot [delta] est écrit : les couches qu'il touchait (et qui n'ont pas bougé depuis) peuvent de nouveau être déchargées. */
    fun markSaved(delta: SceneDelta) {
        for ((d, v) in delta.layerVersions) layer(d)?.markSaved(v)
        updateResidency()
    }

    /** La couche de travail, chargée : si elle n'est pas encore arrivée (on a zoomé très vite), on l'attend. */
    private fun working(): Layer {
        val l = layer(depth)!!
        if (!l.isLoaded) pager?.loadNow(depth)?.let { l.load(it) }
        return l
    }

    class View(var x: Double = 0.0, var y: Double = 0.0, var zoom: Double = 1.0)

    companion object {
        /**
         * Chaque couche est 625 fois plus petite que celle du dessus (25 × 25) : environ quatre
         * pincements pour passer de l'une à l'autre, deux pour dézoomer et deux pour zoomer depuis
         * l'ouverture. Le même pour tous les nouveaux projets.
         */
        const val DEFAULT_RATIO = 625.0
        /**
         * Une scène à une seule couche ([ZoomScene.single]) : sa plage de zoom va de 1/100 à 100
         * (l'ancien canvas allait de 1/10 à 10). Le « rapport » n'y sert qu'à fixer cette étendue :
         * [SINGLE_MAX_ZOOM] ÷ [SINGLE_RATIO] = 0,01.
         */
        const val SINGLE_RATIO = 10_000.0
        const val SINGLE_MAX_ZOOM = 100.0
        /** Ajuster au contenu ne zoome pas au-delà : un petit dessin ne remplit pas l'écran de ses pixels. */
        const val SINGLE_FIT_MAX_ZOOM = 4.0
        /** En mode pixels, un petit dessin se cadre plus gros : on veut voir et viser les cases. */
        const val PIXEL_FIT_MAX_ZOOM = 64.0

        /** Une scène neuve : à couches (le [ratio] du projet) ou à une seule couche. */
        fun create(single: Boolean, ratio: Double = DEFAULT_RATIO): ZoomScene =
            if (single) ZoomScene(SINGLE_RATIO, SINGLE_MAX_ZOOM, single = true) else ZoomScene(ratio)
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
        /** Le pot de peinture ne remplit pas plus de cases que ça (l'historique garde chaque case : 16 octets). */
        const val MAX_FILL_CELLS = 262_144
        /** Aire de la boîte dans laquelle il cherche (cases) : un tableau de marques de cette taille. */
        const val MAX_FILL_AREA = 4_000_000L
        /** Points d'un seul trait (un geste sans lever le doigt, sur des dizaines d'écrans) : 100 000 points = 800 Ko. */
        const val MAX_STROKE_POINTS = 100_000
        /** Couches gardées en mémoire autour de la couche de travail : une au-dessus, deux en dessous (voir [updateResidency]). */
        const val LOAD_ABOVE = 1L
        const val LOAD_BELOW = 2L
        /** On ne décharge qu'au-delà de celles-ci. */
        const val KEEP_ABOVE = 2L
        const val KEEP_BELOW = 3L
    }
}
