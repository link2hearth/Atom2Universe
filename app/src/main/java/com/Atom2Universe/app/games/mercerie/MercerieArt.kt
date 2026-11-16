package com.Atom2Universe.app.games.mercerie

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * La matière du jeu : de la feutrine cousue. Tout ce qui se dessine (pièces, boutons, panneaux)
 * passe par [shape] pour avoir la même épaisseur, les mêmes fibres et le même point de couture.
 */
object Felt {
    private val LIGHT_COLORS = intArrayOf(
        0xFFE8596A.toInt(), 0xFF5AA9E6.toInt(), 0xFFF7C948.toInt(),
        0xFF6CC08B.toInt(), 0xFFA982D9.toInt(), 0xFFF5935A.toInt()
    )
    /** Les mêmes, en tons profonds, d'après les images sombres. */
    private val DARK_COLORS = intArrayOf(
        0xFFB3202E.toInt(), 0xFF2F58C0.toInt(), 0xFFE0A81E.toInt(),
        0xFF1F8A45.toInt(), 0xFF7B3FB0.toInt(), 0xFFC9561C.toInt()
    )

    /**
     * Mode sombre : feutrines profondes, pièces sans visage, images prises dans `assets/mercerie/dark/`
     * (une image absente de ce dossier vient du dossier clair). [theme] change toute la palette d'un coup.
     */
    var dark = false
        private set

    /** Rouge cœur, bleu bouton, jaune étoile, vert feuille, violet pelote, orange fleur. */
    var COLORS = LIGHT_COLORS
        private set
    /** Texte posé sur le fond ou sur un panneau ([PAPER]). */
    var INK = 0xFF5B4034.toInt(); private set
    /** Fil clair : couture, texte sur un bouton de couleur, étincelles. */
    var CREAM = 0xFFFFF6E6.toInt(); private set
    /** Feutrine des panneaux, des fiches et des boutons ronds. */
    var PAPER = 0xFFFFF6E6.toInt(); private set
    /** Texte secondaire sur un panneau (meilleur score). */
    var SOFT = 0xFF987E64.toInt(); private set
    /** Voile posé sous une fiche. */
    var VEIL = 0x885B4034.toInt(); private set
    var LINEN = 0xFFEBD3B8.toInt(); private set
    var KRAFT = 0xFFD9B48F.toInt(); private set
    var PINK = 0xFFEF6F8A.toInt(); private set
    var MINT = 0xFF8FD3B6.toInt(); private set
    var SKY = 0xFFBFE3F2.toInt(); private set
    var PEACH = 0xFFFDE3D0.toInt(); private set
    var GOLD = 0xFFF7C948.toInt(); private set
    var GREY = 0xFFCFC6BC.toInt(); private set
    /** Cases du plateau, paire et impaire. */
    var TILES = intArrayOf(0xFFFFF5E4.toInt(), 0xFFFBEBD5.toInt()); private set

    fun theme(dark: Boolean) {
        this.dark = dark
        if (dark) {
            COLORS = DARK_COLORS
            INK = 0xFFF1E6D6.toInt(); CREAM = 0xFFEDE3D3.toInt(); PAPER = 0xFF2F3242.toInt()
            SOFT = 0xFFC7B7A2.toInt(); VEIL = 0xAA0E0F16.toInt()
            LINEN = 0xFF4A4E60.toInt(); KRAFT = 0xFF5E5568.toInt()
            PINK = 0xFFC9506C.toInt(); MINT = 0xFF3E8B70.toInt(); SKY = 0xFF34507A.toInt()
            PEACH = 0xFF4A3F55.toInt(); GOLD = 0xFFE9B83A.toInt(); GREY = 0xFF5C6070.toInt()
            TILES = intArrayOf(0xFF555B6E.toInt(), 0xFF4C5163.toInt())
        } else {
            COLORS = LIGHT_COLORS
            INK = 0xFF5B4034.toInt(); CREAM = 0xFFFFF6E6.toInt(); PAPER = 0xFFFFF6E6.toInt()
            SOFT = 0xFF987E64.toInt(); VEIL = 0x885B4034.toInt()
            LINEN = 0xFFEBD3B8.toInt(); KRAFT = 0xFFD9B48F.toInt()
            PINK = 0xFFEF6F8A.toInt(); MINT = 0xFF8FD3B6.toInt(); SKY = 0xFFBFE3F2.toInt()
            PEACH = 0xFFFDE3D0.toInt(); GOLD = 0xFFF7C948.toInt(); GREY = 0xFFCFC6BC.toInt()
            TILES = intArrayOf(0xFFFFF5E4.toInt(), 0xFFFBEBD5.toInt())
        }
    }

    /** Une image du jeu, prise d'abord dans le dossier sombre quand le mode sombre est choisi. */
    fun image(assets: AssetManager?, name: String): Bitmap? {
        if (assets == null) return null
        val paths = if (dark) listOf("mercerie/dark/$name.webp", "mercerie/$name.webp") else listOf("mercerie/$name.webp")
        for (p in paths) try {
            return assets.open(p).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {}
        return null
    }

    fun darker(color: Int, f: Float = .78f) = Color.argb(
        Color.alpha(color), (Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt()
    )

    fun lighter(color: Int, t: Float = .45f) = Color.argb(
        Color.alpha(color),
        (Color.red(color) + (255 - Color.red(color)) * t).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * t).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * t).toInt()
    )

    fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    private val fiber: BitmapShader by lazy {
        val size = 128
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val r = java.util.Random(42)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.3f; strokeCap = Paint.Cap.ROUND }
        repeat(1100) {
            val x = r.nextFloat() * size
            val y = r.nextFloat() * size
            val a = r.nextFloat() * PI.toFloat() * 2
            val l = 3 + r.nextFloat() * 8
            p.color = if (r.nextBoolean()) 0x1CFFFFFF else 0x12000000
            c.drawLine(x, y, x + cos(a) * l, y + sin(a) * l, p)
        }
        BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    /** Le bombé de la feutrine, dessiné une fois à l'unité puis posé par une matrice. */
    private val bulge = RadialGradient(0f, 0f, 1f, intArrayOf(0x38FFFFFF, 0x00FFFFFF, 0x26000000), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
    private val bulgeMatrix = Matrix()

    /** Pointillés et étoiles déjà fabriqués, par taille : la carte en redessine des dizaines à chaque image. */
    private val dashes = object : LinkedHashMap<Float, DashPathEffect>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Float, DashPathEffect>?) = size > 24
    }
    private val stars = object : LinkedHashMap<Float, Path>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Float, Path>?) = size > 24
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fibers = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bounds = RectF()
    private val inner = Path()
    private val matrix = Matrix()

    /**
     * Un morceau de feutrine. [size] est la taille de référence (le plus petit côté), d'où viennent
     * l'épaisseur de l'ombre et la longueur des points. [stitchPath] remplace le tracé de la couture
     * quand la forme n'est pas ramassée (un bouton large, un panneau).
     */
    @Synchronized
    fun shape(
        canvas: Canvas, path: Path, color: Int, size: Float,
        stitch: Boolean = true, shadow: Boolean = true, plush: Boolean = true,
        threadColor: Int = lighter(color, .6f), stitchPath: Path? = null
    ) {
        if (shadow) {
            fill.color = 0x2E3A2418
            canvas.save()
            canvas.translate(0f, size * .045f)
            canvas.drawPath(path, fill)
            canvas.restore()
        }
        fill.color = color
        canvas.drawPath(path, fill)
        path.computeBounds(bounds, true)
        if (plush) {
            val cx = bounds.centerX() - bounds.width() * .12f
            val cy = bounds.centerY() - bounds.height() * .18f
            val reach = maxOf(bounds.width(), bounds.height()) * .75f
            bulgeMatrix.setScale(reach, reach)
            bulgeMatrix.postTranslate(cx, cy)
            bulge.setLocalMatrix(bulgeMatrix)
            shade.shader = bulge
            canvas.drawPath(path, shade)
        }
        fibers.shader = fiber
        canvas.drawPath(path, fibers)
        edge.color = darker(color, .86f)
        edge.strokeWidth = size * .028f
        canvas.drawPath(path, edge)
        if (stitch) {
            val line = stitchPath ?: inner.also {
                matrix.setScale(.8f, .8f, bounds.centerX(), bounds.centerY())
                path.transform(matrix, it)
            }
            stitches(canvas, line, threadColor, size)
        }
    }

    /** Un point de couture le long de [path]. */
    @Synchronized
    fun stitches(canvas: Canvas, path: Path, color: Int, size: Float, width: Float = .034f) {
        thread.color = color
        thread.strokeWidth = size * width
        thread.pathEffect = dashes.getOrPut(size) { DashPathEffect(floatArrayOf(size * .085f, size * .075f), 0f) }
        canvas.drawPath(path, thread)
        thread.pathEffect = null
    }

    private val rect = RectF()
    private val panelPath = Path()
    private val panelStitch = Path()

    /** Un panneau ou un bouton : rectangle arrondi cousu à [inset] du bord. */
    @Synchronized
    fun panel(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, unit: Float,
              stitch: Boolean = true, shadow: Boolean = true, threadColor: Int = lighter(color, .6f)) {
        panelPath.rewind()
        rect.set(l, t, r, b)
        panelPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        panelStitch.rewind()
        val inset = unit * .16f
        rect.inset(inset, inset)
        val ir = (radius - inset * .7f).coerceAtLeast(0f)
        panelStitch.addRoundRect(rect, ir, ir, Path.Direction.CW)
        shape(canvas, panelPath, color, unit, stitch, shadow, plush = false, threadColor = threadColor, stitchPath = panelStitch)
    }

    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.ROUND }

    /** Une lettre découpée dans la feutrine : le tracé est épaissi, puis cousu le long de son contour. */
    @Synchronized
    fun letter(canvas: Canvas, path: Path, color: Int, textSize: Float) {
        glyph.strokeWidth = textSize * .16f
        glyph.shader = null
        glyph.color = 0x383A2418
        canvas.save()
        canvas.translate(0f, textSize * .05f)
        canvas.drawPath(path, glyph)
        canvas.restore()
        glyph.color = color
        canvas.drawPath(path, glyph)
        glyph.shader = fiber
        canvas.drawPath(path, glyph)
        glyph.shader = null
        stitches(canvas, path, lighter(color, .65f), textSize * .55f, .04f)
    }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smile = Path()

    /** Le petit visage brodé. [s] : la demi-largeur de la pièce. */
    @Synchronized
    fun face(canvas: Canvas, cx: Float, cy: Float, s: Float, eyes: Boolean = true) {
        ink.style = Paint.Style.FILL
        ink.color = 0x70FF8FA3
        canvas.drawCircle(cx - .46f * s, cy + .13f * s, .11f * s, ink)
        canvas.drawCircle(cx + .46f * s, cy + .13f * s, .11f * s, ink)
        if (eyes) {
            ink.color = INK
            canvas.drawCircle(cx - .26f * s, cy - .04f * s, .085f * s, ink)
            canvas.drawCircle(cx + .26f * s, cy - .04f * s, .085f * s, ink)
            ink.color = Color.WHITE
            canvas.drawCircle(cx - .285f * s, cy - .07f * s, .03f * s, ink)
            canvas.drawCircle(cx + .235f * s, cy - .07f * s, .03f * s, ink)
        }
        ink.style = Paint.Style.STROKE
        ink.strokeCap = Paint.Cap.ROUND
        ink.strokeWidth = .05f * s
        ink.color = INK
        smile.rewind()
        smile.moveTo(cx - .11f * s, cy + .12f * s)
        smile.quadTo(cx, cy + .24f * s, cx + .11f * s, cy + .12f * s)
        canvas.drawPath(smile, ink)
        ink.style = Paint.Style.FILL
    }

    /** Une étoile de récompense, pleine ou vide. */
    @Synchronized
    fun star(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
        val p = stars.getOrPut(r) { starPath(r) }
        canvas.save()
        canvas.translate(cx, cy)
        shape(canvas, p, if (on) GOLD else GREY, r * 2, stitch = on, shadow = on, plush = on, threadColor = 0xFFFFF3C4.toInt())
        canvas.restore()
    }

    fun starPath(r: Float): Path {
        val pts = FloatArray(20)
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) r * 1.13f else r * .58f
            val a = (-90 + 36 * k) * PI.toFloat() / 180f
            pts[k * 2] = cos(a) * rad
            pts[k * 2 + 1] = sin(a) * rad
        }
        return rounded(pts)
    }

    /** Polygone aux coins arrondis : la courbe passe par le milieu de chaque côté. */
    fun rounded(pts: FloatArray): Path {
        val n = pts.size / 2
        val path = Path()
        fun mx(i: Int) = (pts[(i % n) * 2] + pts[((i + 1) % n) * 2]) / 2
        fun my(i: Int) = (pts[(i % n) * 2 + 1] + pts[((i + 1) % n) * 2 + 1]) / 2
        path.moveTo(mx(0), my(0))
        for (i in 1..n) path.quadTo(pts[(i % n) * 2], pts[(i % n) * 2 + 1], mx(i), my(i))
        path.close()
        return path
    }
}

/**
 * Les images des pièces, préparées une fois à la taille d'une case.
 *
 * Elles viennent de `assets/mercerie/` (ou de `dark/` en mode sombre : [Felt.image]) (`piece_0` à `piece_5`, `rainbow`, `thimble`, `pin`, `knot`,
 * `patch`, `pompom`, en `.webp`) ; une image absente est remplacée par un dessin en code. Les rubans des
 * bobines et la pelote des pompons se composent ici, par-dessus l'image de la pièce.
 */
class MercerieSprites(private val assets: AssetManager?, val cell: Int) {
    private val r = cell * .43f
    private val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun blank() = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)

    private fun asset(name: String): Bitmap? = Felt.image(assets, name)

    /** Pose [src] dans une case : [scale] de sa largeur, décalée de [dy] cases, penchée de [tilt] degrés. */
    private fun fitted(src: Bitmap, scale: Float, filter: ColorFilter? = null, dy: Float = 0f, tilt: Float = 0f): Bitmap {
        val out = blank()
        val c = Canvas(out)
        val k = min(cell.toFloat() / src.width, cell.toFloat() / src.height) * scale
        val w = src.width * k
        val h = src.height * k
        val cy = cell / 2f + dy * cell
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = filter
        c.rotate(tilt, cell / 2f, cy)
        c.drawBitmap(src, null, RectF((cell - w) / 2, cy - h / 2, (cell + w) / 2, cy + h / 2), paint)
        return out
    }

    /**
     * Cerne une image d'un trait brun : l'épingle argentée, seule, se perdait sur les pièces
     * claires. Le cerne est la même image, teinte en brun, tamponnée tout autour.
     */
    private fun outlined(src: Bitmap, color: Int = Felt.INK, width: Float = .02f): Bitmap {
        val out = blank()
        val c = Canvas(out)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        ink.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        val reach = cell * width
        for (k in 0 until 16) {
            val a = k * 2 * PI.toFloat() / 16
            c.drawBitmap(src, cos(a) * reach, sin(a) * reach + reach * .5f, ink)
        }
        c.drawBitmap(src, 0f, 0f, smooth)
        src.recycle()
        return out
    }

    /** Passe une image en gris, puis la teinte : [gain] rattrape la clarté perdue. */
    private fun tint(color: Int, gain: Float): ColorFilter {
        val m = ColorMatrix().apply { setSaturation(0f) }
        m.postConcat(ColorMatrix().apply {
            setScale(gain * Color.red(color) / 255f, gain * Color.green(color) / 255f, gain * Color.blue(color) / 255f, 1f)
        })
        return ColorMatrixColorFilter(m)
    }

    private fun drawn(name: String, scale: Float, draw: (Canvas, Float, Float) -> Unit): Bitmap =
        asset(name)?.let { src -> fitted(src, scale).also { src.recycle() } }
            ?: blank().also { draw(Canvas(it), cell / 2f, cell / 2f) }

    /** Les pièces laissent voir le fond de leur case tout autour. */
    private val base = Array(MercerieBoard.COLORS) { color -> drawn("piece_$color", .78f) { c, cx, cy -> drawBase(c, color, cx, cy, r * .87f) } }
    private val arrowH = arrow(true)
    private val arrowV = arrow(false)
    private val lineH = Array(MercerieBoard.COLORS) { withArrow(base[it], arrowH) }
    private val lineV = Array(MercerieBoard.COLORS) { withArrow(base[it], arrowV) }
    private val bomb: Array<Bitmap> = asset("pompom").let { wool ->
        Array(MercerieBoard.COLORS) { pompom(it, wool) }.also { wool?.recycle() }
    }
    val rainbow = drawn("rainbow", .84f) { c, cx, cy -> drawRainbow(c, cx, cy, r) }
    val thimble = drawn("thimble", .8f) { c, cx, cy -> drawThimble(c, cx, cy, r) }
    /** Le nœud de laine : la même pelote grise, plus foncée à chaque couche. */
    val knot: Array<Bitmap> = asset("knot")?.let { src ->
        Array(3) { layers -> fitted(src, .86f, tint(KNOT_COLORS[layers], 1.5f)) }.also { src.recycle() }
    } ?: Array(3) { layers -> blank().also { drawKnot(Canvas(it), cell / 2f, cell / 2f, r, layers + 1) } }
    val pin: Bitmap = asset("pin")?.let { src -> outlined(fitted(src, .94f, dy = .27f, tilt = -14f)).also { src.recycle() } }
        ?: blank().also { drawPin(Canvas(it), cell / 2f, cell / 2f, r) }
    val patch: Array<Bitmap> = asset("patch")?.let { src ->
        Array(2) { layers ->
            // Double écusson : plus rose en clair, plus vif en sombre.
            val second = if (Felt.dark) LightingColorFilter(0xFFFFFFFF.toInt(), 0x00502638) else LightingColorFilter(0xFFE2A0B4.toInt(), 0)
            fitted(src, .9f, if (layers == 1) second else null)
        }.also { src.recycle() }
    } ?: Array(2) { layers -> blank().also { drawPatch(Canvas(it), cell.toFloat(), layers + 1) } }
    val tile = Array(2) { odd -> blank().also { drawTile(Canvas(it), cell.toFloat(), odd == 1) } }

    fun of(piece: Piece): Bitmap = of(piece.kind, piece.color)

    /** Le plateau dessine la bobine en deux temps : la pièce seule, puis ses ciseaux qui battent. */
    fun plain(color: Int): Bitmap = base[color.coerceIn(0, 5)]

    fun arrowOf(kind: Kind): Bitmap? = when (kind) {
        Kind.LINE_H -> arrowH
        Kind.LINE_V -> arrowV
        else -> null
    }

    fun of(kind: Kind, color: Int): Bitmap = when (kind) {
        Kind.NORMAL -> base[color.coerceIn(0, 5)]
        Kind.LINE_H -> lineH[color.coerceIn(0, 5)]
        Kind.LINE_V -> lineV[color.coerceIn(0, 5)]
        Kind.BOMB -> bomb[color.coerceIn(0, 5)]
        Kind.RAINBOW -> rainbow
        Kind.THIMBLE -> thimble
    }

    /** Bobine pour l'aide et les objectifs : la pièce avec sa flèche, immobile. */
    private fun withArrow(piece: Bitmap, arrow: Bitmap): Bitmap {
        val out = piece.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawBitmap(arrow, 0f, 0f, smooth)
        return out
    }

    /**
     * Le signe des bobines : une paire de ciseaux ouverte dans le sens de la coupe (`scissors.webp`,
     * lames vers la droite, tournée d'un quart pour une colonne), cernée comme l'épingle. Sans image,
     * une flèche à deux pointes en feutrine crème cernée de brun.
     */
    private fun arrow(horizontal: Boolean): Bitmap {
        asset("scissors")?.let { src ->
            // Un cerne fin et toujours foncé : en clair il se fond dans la pièce, en sombre il ne fait pas de halo blanc.
            val rim = if (Felt.dark) 0xFF1C1826.toInt() else Felt.INK
            return outlined(fitted(src, .44f, tilt = if (horizontal) 0f else 90f), rim, .012f).also { src.recycle() }
        }
        val out = blank()
        val c = Canvas(out)
        val half = cell * .34f
        val shaft = cell * .06f
        val head = cell * .17f
        val wing = cell * .16f
        val p = Path()
        p.moveTo(-half, 0f)
        p.lineTo(-half + head, -wing); p.lineTo(-half + head, -shaft)
        p.lineTo(half - head, -shaft); p.lineTo(half - head, -wing)
        p.lineTo(half, 0f)
        p.lineTo(half - head, wing); p.lineTo(half - head, shaft)
        p.lineTo(-half + head, shaft); p.lineTo(-half + head, wing)
        p.close()
        c.translate(cell / 2f, cell / 2f)
        if (!horizontal) c.rotate(90f)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = cell * .075f
            color = 0xFF4A3328.toInt()
        }
        c.drawPath(p, rim)
        Felt.shape(c, p, 0xFFFFF6E6.toInt(), cell * .5f, stitch = false)
        return out
    }

    /**
     * Pompon : la pièce posée sur un pompon à sa couleur. Le pompon est une image blanche ([wool]),
     * teintée ici ; sans image, une boule frisée dessinée en code.
     */
    private fun pompom(color: Int, wool: Bitmap?): Bitmap {
        val m = cell / 2f
        val out: Bitmap
        if (wool != null) {
            out = fitted(wool, .9f, tint(Felt.lighter(Felt.COLORS[color], .25f), 1.1f))
        } else {
            val wool = Felt.lighter(Felt.COLORS[color], .5f)
            out = blank()
            val c = Canvas(out)
            val big = cell * .47f
            val ring = Path()
            val tufts = 14
            for (k in 0 until tufts) {
                val a = k * 2 * PI.toFloat() / tufts
                ring.addCircle(m + cos(a) * big * .78f, m + sin(a) * big * .78f, big * .22f, Path.Direction.CW)
            }
            ring.addCircle(m, m, big * .8f, Path.Direction.CW)
            val whole = Path()
            whole.op(ring, ring, Path.Op.UNION)
            Felt.shape(c, whole, wool, cell.toFloat(), stitch = false)
        }
        val s = cell * .6f
        Canvas(out).drawBitmap(base[color], null, RectF(m - s / 2, m - s / 2, m + s / 2, m + s / 2), smooth)
        return out
    }

    companion object {
        /** Nœud à une, deux ou trois couches : écru, rose, framboise. */
        private val KNOT_COLORS = intArrayOf(0xFFF2E2CB.toInt(), 0xFFEFA3B2.toInt(), 0xFFBE5F7E.toInt())

        fun drawBase(canvas: Canvas, color: Int, cx: Float, cy: Float, r: Float) {
            val tint = Felt.COLORS[color]
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val matrix = Matrix()
            canvas.save()
            canvas.translate(cx, cy)
            when (color) {
                0 -> {
                    val p = Path()
                    p.moveTo(0f, .9f * r)
                    p.cubicTo(-1.32f * r, -.02f * r, -.6f * r, -1.08f * r, 0f, -.42f * r)
                    p.cubicTo(.6f * r, -1.08f * r, 1.32f * r, -.02f * r, 0f, .9f * r)
                    p.close()
                    Felt.shape(canvas, p, tint, r * 2)
                    Felt.face(canvas, 0f, -.08f * r, r * .9f)
                }
                1 -> {
                    val p = Path().apply { addCircle(0f, 0f, .93f * r, Path.Direction.CW) }
                    Felt.shape(canvas, p, tint, r * 2, stitch = false)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = r * .09f
                    paint.color = Felt.darker(tint, .84f)
                    canvas.drawCircle(0f, 0f, .68f * r, paint)
                    paint.style = Paint.Style.FILL
                    // Les deux trous du bouton lui servent d'yeux.
                    paint.color = Felt.darker(tint, .42f)
                    canvas.drawCircle(-.24f * r, -.08f * r, .105f * r, paint)
                    canvas.drawCircle(.24f * r, -.08f * r, .105f * r, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = r * .05f
                    paint.color = Felt.CREAM
                    canvas.drawLine(-.24f * r, -.08f * r, .24f * r, -.08f * r, paint)
                    paint.style = Paint.Style.FILL
                    Felt.face(canvas, 0f, .04f * r, r * .86f, eyes = false)
                }
                2 -> {
                    Felt.shape(canvas, Felt.starPath(r * .98f), tint, r * 2, threadColor = 0xFFFFF3C4.toInt())
                    Felt.face(canvas, 0f, .06f * r, r * .78f)
                }
                3 -> {
                    val p = Path()
                    p.moveTo(0f, -1.02f * r)
                    p.cubicTo(1.02f * r, -.62f * r, .9f * r, .62f * r, 0f, 1.02f * r)
                    p.cubicTo(-.9f * r, .62f * r, -1.02f * r, -.62f * r, 0f, -1.02f * r)
                    p.close()
                    matrix.setRotate(38f)
                    p.transform(matrix)
                    Felt.shape(canvas, p, tint, r * 2)
                    val vein = Path()
                    vein.moveTo(.5f * r, -.62f * r)
                    vein.lineTo(.2f * r, -.26f * r)
                    Felt.stitches(canvas, vein, Felt.lighter(tint, .6f), r * 2)
                    Felt.face(canvas, -.04f * r, .08f * r, r * .82f)
                }
                4 -> {
                    val p = Path().apply { addCircle(0f, 0f, .92f * r, Path.Direction.CW) }
                    Felt.shape(canvas, p, tint, r * 2, stitch = false)
                    canvas.save()
                    canvas.clipPath(p)
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = r * .06f
                    paint.color = Felt.lighter(tint, .42f)
                    for (k in 0 until 4) canvas.drawCircle(-1.5f * r + k * .1f * r, -1.3f * r, (1.35f + k * .3f) * r, paint)
                    paint.color = Felt.darker(tint, .84f)
                    for (k in 0 until 3) canvas.drawCircle(1.55f * r, -1.2f * r + k * .08f * r, (1.5f + k * .3f) * r, paint)
                    paint.style = Paint.Style.FILL
                    canvas.restore()
                    Felt.face(canvas, 0f, .1f * r, r * .86f)
                }
                else -> {
                    val petals = Path()
                    for (k in 0 until 6) {
                        val a = (k * 60 - 90) * PI.toFloat() / 180f
                        val one = Path().apply { addCircle(cos(a) * .58f * r, sin(a) * .58f * r, .42f * r, Path.Direction.CW) }
                        petals.op(one, Path.Op.UNION)
                    }
                    Felt.shape(canvas, petals, tint, r * 2, stitch = false)
                    val heart = Path().apply { addCircle(0f, 0f, .5f * r, Path.Direction.CW) }
                    Felt.shape(canvas, heart, 0xFFFFE7A8.toInt(), r * 1.2f, shadow = false, threadColor = Felt.darker(tint, .9f))
                    Felt.face(canvas, 0f, 0f, r * .6f)
                }
            }
            canvas.restore()
        }

        fun drawRainbow(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.save()
            canvas.translate(cx, cy)
            val disc = Path().apply { addCircle(0f, 0f, .96f * r, Path.Direction.CW) }
            Felt.shape(canvas, disc, Felt.CREAM, r * 2, stitch = false)
            val outer = RectF(-.8f * r, -.8f * r, .8f * r, .8f * r)
            val inner = RectF(-.36f * r, -.36f * r, .36f * r, .36f * r)
            for (k in 0 until 6) {
                val a = k * 60f - 90f + 3f
                val wedge = Path()
                wedge.arcTo(outer, a, 54f, true)
                wedge.arcTo(inner, a + 54f, -54f)
                wedge.close()
                Felt.shape(canvas, wedge, Felt.COLORS[k], r * 2, stitch = false, shadow = false, plush = false)
            }
            val rim = Path().apply { addCircle(0f, 0f, .88f * r, Path.Direction.CW) }
            Felt.stitches(canvas, rim, Felt.PINK, r * 2, .03f)
            paint.style = Paint.Style.FILL
            paint.color = Felt.darker(Felt.KRAFT, .7f)
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) canvas.drawCircle(sx * .13f * r, sy * .13f * r, .065f * r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = r * .045f
            paint.color = Felt.PINK
            canvas.drawLine(-.13f * r, -.13f * r, .13f * r, .13f * r, paint)
            canvas.drawLine(.13f * r, -.13f * r, -.13f * r, .13f * r, paint)
            paint.style = Paint.Style.FILL
            canvas.restore()
        }

        fun drawThimble(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.save()
            canvas.translate(cx, cy)
            val silver = 0xFFBCC7DB.toInt()
            val body = Path()
            body.moveTo(-.6f * r, .6f * r)
            body.lineTo(-.46f * r, -.4f * r)
            body.quadTo(-.44f * r, -.95f * r, 0f, -.95f * r)
            body.quadTo(.44f * r, -.95f * r, .46f * r, -.4f * r)
            body.lineTo(.6f * r, .6f * r)
            body.close()
            Felt.shape(canvas, body, silver, r * 2, stitch = false)
            paint.style = Paint.Style.FILL
            paint.color = Felt.darker(silver, .86f)
            for (row in 0 until 2) for (k in -2..2) canvas.drawCircle(k * .17f * r + (row % 2) * .085f * r, (-.72f + row * .16f) * r, .04f * r, paint)
            val band = Path().apply { addRoundRect(RectF(-.74f * r, .48f * r, .74f * r, .86f * r), .16f * r, .16f * r, Path.Direction.CW) }
            Felt.shape(canvas, band, 0xFF93A1BC.toInt(), r * 2, stitch = false, shadow = false)
            val seam = Path().apply { moveTo(-.6f * r, .67f * r); lineTo(.6f * r, .67f * r) }
            Felt.stitches(canvas, seam, Felt.CREAM, r * 2, .03f)
            Felt.face(canvas, 0f, -.08f * r, r * .74f)
            canvas.restore()
        }

        fun drawKnot(canvas: Canvas, cx: Float, cy: Float, r: Float, layers: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.save()
            canvas.translate(cx, cy)
            val wool = 0xFFD8C4AE.toInt()
            val ball = Path().apply { addCircle(0f, 0f, .9f * r, Path.Direction.CW) }
            Felt.shape(canvas, ball, wool, r * 2, stitch = false)
            canvas.save()
            canvas.clipPath(ball)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = r * .07f
            paint.color = Felt.darker(wool, .86f)
            for (k in 0 until 5) canvas.drawCircle(-1.4f * r, -1.2f * r + k * .05f * r, (1.2f + k * .28f) * r, paint)
            paint.color = Felt.lighter(wool, .5f)
            for (k in 0 until 4) canvas.drawCircle(1.5f * r, -1.1f * r, (1.35f + k * .3f) * r, paint)
            // Chaque couche en plus est un tour de laine rose bien serré.
            paint.strokeWidth = r * .2f
            if (layers >= 2) { paint.color = 0xFFD9808F.toInt(); canvas.drawLine(-r, .5f * r, r, -.5f * r, paint) }
            if (layers >= 3) { paint.color = 0xFFB9657A.toInt(); canvas.drawLine(-r, -.5f * r, r, .5f * r, paint) }
            paint.style = Paint.Style.FILL
            canvas.restore()
            if (layers >= 2) {
                val s = Path().apply { moveTo(-.7f * r, .35f * r); lineTo(.7f * r, -.35f * r) }
                Felt.stitches(canvas, s, Felt.CREAM, r * 2, .022f)
            }
            if (layers >= 3) {
                val s = Path().apply { moveTo(-.7f * r, -.35f * r); lineTo(.7f * r, .35f * r) }
                Felt.stitches(canvas, s, Felt.CREAM, r * 2, .022f)
            }
            canvas.restore()
        }

        /** Épingle à nourrice posée en travers du bas de la pièce. */
        fun drawPin(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.save()
            canvas.translate(cx, cy + .52f * r)
            canvas.rotate(-12f)
            val steel = 0xFF8D97AD.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = r * .13f
            paint.color = 0x553A2418
            canvas.drawLine(-.7f * r, .1f * r, .6f * r, .1f * r, paint)
            paint.strokeWidth = r * .09f
            paint.color = steel
            canvas.drawLine(-.7f * r, -.12f * r, .62f * r, -.12f * r, paint)
            canvas.drawLine(-.7f * r, .12f * r, .62f * r, .12f * r, paint)
            canvas.drawCircle(-.78f * r, 0f, .14f * r, paint)
            paint.style = Paint.Style.FILL
            val head = Path().apply { addRoundRect(RectF(.5f * r, -.24f * r, .92f * r, .24f * r), .14f * r, .14f * r, Path.Direction.CW) }
            Felt.shape(canvas, head, Felt.PINK, r * 1.2f, stitch = false, shadow = false)
            canvas.restore()
        }

        /** Écusson vichy cousu au fond de la case. */
        fun drawPatch(canvas: Canvas, cell: Float, layers: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val color = if (layers >= 2) 0xFFEE8FA6.toInt() else 0xFFF8BECB.toInt()
            val m = cell * .07f
            val rect = RectF(m, m, cell - m, cell - m)
            val path = Path().apply { addRoundRect(rect, cell * .14f, cell * .14f, Path.Direction.CW) }
            Felt.shape(canvas, path, color, cell, stitch = false, shadow = false, plush = false)
            canvas.save()
            canvas.clipPath(path)
            paint.style = Paint.Style.FILL
            paint.color = 0x55FFFFFF
            val step = (cell - 2 * m) / 4
            for (k in 0 until 4) if (k % 2 == 0) {
                canvas.drawRect(m + k * step, m, m + (k + 1) * step, cell - m, paint)
                canvas.drawRect(m, m + k * step, cell - m, m + (k + 1) * step, paint)
            }
            canvas.restore()
            val inset = cell * .15f
            val seam = Path().apply { addRoundRect(RectF(inset, inset, cell - inset, cell - inset), cell * .09f, cell * .09f, Path.Direction.CW) }
            Felt.stitches(canvas, seam, if (layers >= 2) Felt.CREAM else 0xFFE0708A.toInt(), cell, .03f)
            if (layers >= 2) {
                val second = cell * .23f
                val seam2 = Path().apply { addRoundRect(RectF(second, second, cell - second, cell - second), cell * .06f, cell * .06f, Path.Direction.CW) }
                Felt.stitches(canvas, seam2, Felt.CREAM, cell, .026f)
            }
        }

        fun drawTile(canvas: Canvas, cell: Float, odd: Boolean) {
            val m = cell * .035f
            val path = Path().apply { addRoundRect(RectF(m, m, cell - m, cell - m), cell * .16f, cell * .16f, Path.Direction.CW) }
            Felt.shape(canvas, path, Felt.TILES[if (odd) 1 else 0], cell, stitch = false, shadow = false, plush = false)
        }
    }
}
