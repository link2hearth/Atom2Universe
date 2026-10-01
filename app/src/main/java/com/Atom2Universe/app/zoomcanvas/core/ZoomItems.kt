package com.Atom2Universe.app.zoomcanvas.core

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un objet posé sur une couche et qui tient dans une boîte : son centre ([x], [y]) et sa taille
 * ([w], [h]), en unités de la couche. Image, forme et texte se déplacent, se redimensionnent par un
 * coin et se sélectionnent de la même façon ; ce qui les distingue tient dans les quelques fonctions
 * ci-dessous. Tous sont immuables : modifier un objet, c'est en fabriquer un autre au même [id].
 */
sealed interface BoxItem : LayerItem {
    val x: Double
    val y: Double
    val w: Double
    val h: Double

    /** Les proportions sont gardées quand on tire un coin (image, texte) ; sinon la boîte est libre (formes). */
    val keepsRatio: Boolean

    fun moved(dx: Double, dy: Double): BoxItem

    /**
     * Le même objet dans une autre boîte. [crossX] / [crossY] disent que la boîte a été retournée
     * (le coin tiré a passé de l'autre côté du coin fixe) : seuls la ligne et la flèche, qui ont un
     * sens, en tiennent compte.
     */
    fun boxed(x: Double, y: Double, w: Double, h: Double, crossX: Boolean = false, crossY: Boolean = false): BoxItem

    /** Le point ([px], [py]) touche-t-il l'objet ? [slack] élargit la zone (un doigt n'est pas précis). */
    fun hit(px: Double, py: Double, slack: Double): Boolean =
        px >= x - w / 2 - slack && px <= x + w / 2 + slack && py >= y - h / 2 - slack && py <= y + h / 2 + slack
}

/**
 * Une forme vectorielle : l'une des formes du pixel art ([kind]), rangée dans sa boîte. [fill] dit si
 * on trace le contour ([strokeColor], épaisseur [width] en unités de la couche, choisie à l'écran au
 * moment du tracé comme celle d'un crayon), le remplissage ([fillColor]), ou les deux.
 *
 * La ligne et la flèche suivent le geste : elles vont d'un coin de la boîte au coin opposé, et
 * [flipX] / [flipY] disent de quel côté part leur début (la tête de la flèche est à la fin).
 */
data class ShapeItem(
    override val id: Long,
    val kind: ShapeKind,
    override val x: Double,
    override val y: Double,
    override val w: Double,
    override val h: Double,
    val flipX: Boolean,
    val flipY: Boolean,
    val strokeColor: Int,
    val fillColor: Int,
    val fill: ShapeFill,
    val width: Double,
) : BoxItem {
    override val keepsRatio: Boolean get() = false

    /** La ligne et la flèche ont un sens ; les autres formes sont toujours droites dans leur boîte. */
    val isDirected: Boolean get() = kind == ShapeKind.LINE || kind == ShapeKind.ARROW

    /** Y a-t-il un contour à tracer, un remplissage à poser ? (Une ligne n'a que son trait.) */
    val hasStroke: Boolean get() = (fill != ShapeFill.FILL || kind == ShapeKind.LINE) && strokeColor ushr 24 != 0
    val hasFill: Boolean get() = kind != ShapeKind.LINE && fill != ShapeFill.OUTLINE && fillColor ushr 24 != 0

    override fun moved(dx: Double, dy: Double) = copy(x = x + dx, y = y + dy)

    override fun boxed(x: Double, y: Double, w: Double, h: Double, crossX: Boolean, crossY: Boolean) = copy(
        x = x, y = y, w = w, h = h,
        flipX = if (isDirected && crossX) !flipX else flipX,
        flipY = if (isDirected && crossY) !flipY else flipY,
    )

    override fun hit(px: Double, py: Double, slack: Double): Boolean {
        if (!isDirected) return super.hit(px, py, slack)
        // Une ligne en diagonale ne remplit pas sa boîte : on ne la touche que près de son trait.
        val sx = if (flipX) 1.0 else -1.0
        val sy = if (flipY) 1.0 else -1.0
        val ax = x + sx * w / 2
        val ay = y + sy * h / 2
        val bx = x - sx * w / 2
        val by = y - sy * h / 2
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val reach = slack + width / 2 + if (kind == ShapeKind.ARROW) sqrt(len2) * ARROW_HEAD_HALF else 0.0
        return hypot(px - (ax + t * dx), py - (ay + t * dy)) <= reach
    }

    companion object {
        /** Demi-largeur de la tête de la flèche, en part de sa longueur. */
        const val ARROW_HEAD_HALF = 0.15
    }
}

/**
 * Un texte (une ou plusieurs lignes) : sa boîte, la taille de la police [fontSize] en unités de la
 * couche, sa couleur, son style ([BOLD], [ITALIC]) et sa police ([font] : nom d'affichage de la
 * police, vide pour celle par défaut). La largeur [w] est mesurée par l'écran, qui connaît les
 * polices ; la hauteur [h] ne dépend que du nombre de lignes. Tirer un coin change la taille de la
 * police, jamais la forme du texte.
 */
data class TextItem(
    override val id: Long,
    val text: String,
    override val x: Double,
    override val y: Double,
    override val w: Double,
    override val h: Double,
    val fontSize: Double,
    val color: Int,
    val style: Int,
    val font: String,
) : BoxItem {
    override val keepsRatio: Boolean get() = true

    val bold: Boolean get() = style and BOLD != 0
    val italic: Boolean get() = style and ITALIC != 0
    val lines: List<String> get() = text.split('\n')

    override fun moved(dx: Double, dy: Double) = copy(x = x + dx, y = y + dy)

    override fun boxed(x: Double, y: Double, w: Double, h: Double, crossX: Boolean, crossY: Boolean): TextItem {
        val k = if (this.w > 0.0) w / this.w else 1.0
        return copy(x = x, y = y, w = w, h = h, fontSize = fontSize * k)
    }

    /**
     * Le même texte avec un autre contenu ou un autre style, la largeur [unitWidth] étant celle de sa
     * ligne la plus longue pour une police de taille 1. Le coin haut-gauche ne bouge pas.
     */
    fun restyled(text: String = this.text, style: Int = this.style, font: String = this.font, color: Int = this.color, unitWidth: Double): TextItem {
        val nw = unitWidth * fontSize
        val nh = heightOf(text, fontSize)
        return copy(text = text, style = style, font = font, color = color, w = nw, h = nh, x = x - w / 2 + nw / 2, y = y - h / 2 + nh / 2)
    }

    companion object {
        const val BOLD = 1
        const val ITALIC = 2
        /** Hauteur d'une ligne, en part de la taille de la police. */
        const val LINE_HEIGHT = 1.25
        /** Distance du haut d'une ligne à sa ligne de base, en part de la taille de la police. */
        const val BASELINE = 0.98
        /** Plus long texte gardé (le fichier écrit chaque texte d'un bloc). */
        const val MAX_LENGTH = 4000

        fun heightOf(text: String, fontSize: Double): Double = (text.count { it == '\n' } + 1) * LINE_HEIGHT * fontSize
    }
}

/** Un déplacement dans la pile des éléments d'une couche : d'un cran vers l'avant / l'arrière, ou tout devant / tout derrière. */
enum class OrderMove { BACKWARD, FORWARD, TO_BACK, TO_FRONT }

/**
 * Un trait de dessin vu comme un objet à boîte, pour qu'on puisse le sélectionner, le déplacer, le
 * redimensionner (la forme et l'épaisseur grandissent ensemble) et le monter ou le descendre dans la
 * pile comme une image ou une forme. Le trait lui-même ([stroke]) reste ce qu'il est : seul son
 * origine et l'échelle de ses points changent, jamais une grande coordonnée. On le touche près de
 * son tracé, pas dans le vide de sa boîte.
 */
class StrokeBox(val stroke: Stroke) : BoxItem {
    override val id: Long get() = stroke.id
    override val x: Double = stroke.x + (stroke.minX + stroke.maxX) / 2
    override val y: Double = stroke.y + (stroke.minY + stroke.maxY) / 2
    override val w: Double = stroke.maxX - stroke.minX
    override val h: Double = stroke.maxY - stroke.minY
    override val keepsRatio: Boolean get() = true

    override fun moved(dx: Double, dy: Double) =
        StrokeBox(Stroke(stroke.id, stroke.x + dx, stroke.y + dy, stroke.pts, stroke.color, stroke.width, stroke.kind))

    override fun boxed(x: Double, y: Double, w: Double, h: Double, crossX: Boolean, crossY: Boolean): StrokeBox {
        val s = stroke
        val k = if (this.w > 0.0) w / this.w else if (this.h > 0.0) h / this.h else 1.0
        val pts = DoubleArray(s.pts.size) { s.pts[it] * k }
        // Les points sont relatifs à l'origine : on l'installe pour que le centre de la boîte tombe en (x, y).
        val ox = x - k * (s.minX + s.maxX) / 2
        val oy = y - k * (s.minY + s.maxY) / 2
        return StrokeBox(Stroke(s.id, ox, oy, pts, s.color, s.width * k, s.kind))
    }

    /** Le même trait d'une autre couleur. */
    fun recolored(color: Int) = StrokeBox(Stroke(stroke.id, stroke.x, stroke.y, stroke.pts, color, stroke.width, stroke.kind))

    override fun hit(px: Double, py: Double, slack: Double): Boolean {
        val s = stroke
        val reach = slack + s.width / 2
        val lx = px - s.x
        val ly = py - s.y
        val p = s.pts
        if (p.size < 4) return hypot(lx - p[0], ly - p[1]) <= reach
        var i = 0
        while (i + 3 < p.size) {
            val ax = p[i]; val ay = p[i + 1]
            val dx = p[i + 2] - ax; val dy = p[i + 3] - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0.0) 0.0 else (((lx - ax) * dx + (ly - ay) * dy) / len2).coerceIn(0.0, 1.0)
            if (hypot(lx - (ax + t * dx), ly - (ay + t * dy)) <= reach) return true
            i += 2
        }
        return false
    }
}

/**
 * Les points d'une forme, relatifs à son centre, en unités de la couche. Les formes fermées rendent
 * l'anneau sans répéter le premier point.
 */
object ShapeGeometry {

    /** Plus d'arêtes qu'ici ne se voient pas : on ne trace jamais plus fin. */
    private const val MAX_STEPS = 2048
    private const val MIN_STEPS = 24

    private val TRIANGLE = arrayOf(0.5 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
    private val DIAMOND = arrayOf(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5)
    private val PENTAGON = regularPolygon(5, -90.0)
    private val HEXAGON = regularPolygon(6, 0.0)
    private val STAR = star(5, 0.4)

    /** Les sommets d'une forme à coins (triangle, losange, polygone, étoile), dans la boîte 0..1. */
    private fun unit(kind: ShapeKind): Array<Pair<Double, Double>>? = when (kind) {
        ShapeKind.TRIANGLE -> TRIANGLE
        ShapeKind.DIAMOND -> DIAMOND
        ShapeKind.PENTAGON -> PENTAGON
        ShapeKind.HEXAGON -> HEXAGON
        ShapeKind.STAR -> STAR
        else -> null
    }

    private fun regularPolygon(sides: Int, startDeg: Double): Array<Pair<Double, Double>> =
        normalized(Array(sides) { k ->
            val t = (startDeg + 360.0 * k / sides) * PI / 180.0
            cos(t) to sin(t)
        })

    private fun star(points: Int, inner: Double): Array<Pair<Double, Double>> =
        normalized(Array(points * 2) { k ->
            val t = (-90.0 + 180.0 * k / points) * PI / 180.0
            val r = if (k % 2 == 0) 1.0 else inner
            cos(t) * r to sin(t) * r
        })

    /** Remplit exactement la boîte 0..1 × 0..1 (un pentagone n'est pas aussi haut que large avant ça). */
    private fun normalized(p: Array<Pair<Double, Double>>): Array<Pair<Double, Double>> {
        val x0 = p.minOf { it.first }
        val x1 = p.maxOf { it.first }
        val y0 = p.minOf { it.second }
        val y1 = p.maxOf { it.second }
        val w = max(x1 - x0, 1e-9)
        val h = max(y1 - y0, 1e-9)
        return Array(p.size) { (p[it].first - x0) / w to (p[it].second - y0) / h }
    }

    /**
     * Le contour de [s] comme une suite de points (x, y, x, y…) relatifs à son centre. [zoom] (pixels
     * d'écran par unité de la couche) règle la finesse des courbes : assez d'arêtes pour qu'aucune ne
     * s'écarte de plus d'un dixième de pixel de la vraie courbe, et pas une de plus.
     */
    fun outline(s: ShapeItem, zoom: Double): DoubleArray {
        val w = s.w
        val h = s.h
        return when (s.kind) {
            ShapeKind.LINE -> {
                val sx = if (s.flipX) 1.0 else -1.0
                val sy = if (s.flipY) 1.0 else -1.0
                doubleArrayOf(sx * w / 2, sy * h / 2, -sx * w / 2, -sy * h / 2)
            }
            ShapeKind.RECT -> doubleArrayOf(-w / 2, -h / 2, w / 2, -h / 2, w / 2, h / 2, -w / 2, h / 2)
            ShapeKind.ELLIPSE -> {
                val n = steps(max(w, h) * zoom / 2, 0.15, MIN_STEPS)
                DoubleArray(2 * n) { i ->
                    val t = 2 * PI * (i / 2) / n
                    if (i % 2 == 0) cos(t) * w / 2 else sin(t) * h / 2
                }
            }
            ShapeKind.ROUNDED_RECT -> roundedRect(w, h, zoom)
            ShapeKind.ARROW -> arrow(s)
            else -> {
                val u = unit(s.kind)!!
                DoubleArray(2 * u.size) { i -> if (i % 2 == 0) (u[i / 2].first - 0.5) * w else (u[i / 2].second - 0.5) * h }
            }
        }
    }

    /** Nombre d'arêtes pour un arc de rayon [rPx] pixels qui ne s'écarte pas de plus de [tol] pixels. */
    private fun steps(rPx: Double, tol: Double, min: Int): Int {
        if (!rPx.isFinite()) return MAX_STEPS
        val n = ceil(PI * sqrt(max(rPx, 0.0) / (2 * tol))).toInt()
        return n.coerceIn(min, MAX_STEPS)
    }

    private fun roundedRect(w: Double, h: Double, zoom: Double): DoubleArray {
        val r = min(w, h) / 4
        // Un quart de cercle : le quart du nombre d'arêtes d'un cercle entier.
        val arc = max(2, steps(r * zoom, 0.15, 8) / 4)
        val out = DoubleArray(2 * 4 * (arc + 1))
        // Quatre coins dans le sens horaire depuis en haut à gauche : centre de l'arc, angle de départ.
        val cx = doubleArrayOf(-w / 2 + r, w / 2 - r, w / 2 - r, -w / 2 + r)
        val cy = doubleArrayOf(-h / 2 + r, -h / 2 + r, h / 2 - r, h / 2 - r)
        val from = doubleArrayOf(180.0, 270.0, 0.0, 90.0)
        var i = 0
        for (c in 0 until 4) for (k in 0..arc) {
            val t = (from[c] + 90.0 * k / arc) * PI / 180.0
            out[i++] = cx[c] + cos(t) * r
            out[i++] = cy[c] + sin(t) * r
        }
        return out
    }

    /** Une flèche pleine du début à la fin de la ligne : sept sommets, la pointe au bout. */
    private fun arrow(s: ShapeItem): DoubleArray {
        val sx = if (s.flipX) 1.0 else -1.0
        val sy = if (s.flipY) 1.0 else -1.0
        val ax = sx * s.w / 2
        val ay = sy * s.h / 2
        val bx = -ax
        val by = -ay
        val dx = bx - ax
        val dy = by - ay
        val len = hypot(dx, dy)
        if (len <= 0.0) return doubleArrayOf(ax, ay)
        val ux = dx / len
        val uy = dy / len
        val nx = -uy
        val ny = ux
        val shaft = len * 0.06
        val head = min(len * 0.55, len * 0.3)
        val headHalf = len * ShapeItem.ARROW_HEAD_HALF
        val hx = bx - ux * head
        val hy = by - uy * head
        return doubleArrayOf(
            ax + nx * shaft, ay + ny * shaft,
            hx + nx * shaft, hy + ny * shaft,
            hx + nx * headHalf, hy + ny * headHalf,
            bx, by,
            hx - nx * headHalf, hy - ny * headHalf,
            hx - nx * shaft, hy - ny * shaft,
            ax - nx * shaft, ay - ny * shaft,
        )
    }

    /** Épaisseur du trait comprise, la boîte englobante d'une forme (minX, minY, maxX, maxY). */
    fun bounds(s: ShapeItem): DoubleArray {
        val half = if (s.hasStroke) s.width / 2 else 0.0
        // La tête d'une flèche dépasse un peu de la boîte quand elle est en travers.
        val extra = if (s.kind == ShapeKind.ARROW) hypot(s.w, s.h) * ShapeItem.ARROW_HEAD_HALF else 0.0
        val m = half + extra
        return doubleArrayOf(s.x - s.w / 2 - m, s.y - s.h / 2 - m, s.x + s.w / 2 + m, s.y + s.h / 2 + m)
    }
}
